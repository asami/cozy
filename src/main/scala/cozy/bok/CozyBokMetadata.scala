package cozy.bok

import org.goldenport.RAISE
import org.goldenport.cli.{Request => CliRequest}
import org.goldenport.cli.spec
import cozy.bok.scenario.ScenarioMetadata
import cozy.bok.BibliographyEntry._
import cozy.config.CozyProjectYamlConfig
import cozy.publication.{CozyArticleMediaBuildContext, CozyArticleMediaInfographicCommand, CozyArticleMediaInfographicEvidence, CozyArticleMediaVideoCommand}
import cozy.video.{CozyVideo, CozyVideoPublisher}
import org.smartdox.{Body, Document, Dox}
import org.smartdox.parser.Dox2Parser
import org.smartdox.transformers.Dox2HtmlTransformer
import org.smartdox.generator.{Context => SmartDoxContext}
import org.smartdox.metadata.DocumentMetaData
import org.goldenport.i18n.I18NContext
import java.net.URLEncoder
import java.time.{Instant, LocalDate, LocalDateTime, YearMonth, ZoneOffset}
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.regex.Pattern
import java.nio.charset.StandardCharsets
import java.nio.file.{Files, LinkOption, Path, Paths, StandardCopyOption}
import java.util.zip.{ZipEntry, ZipFile, ZipInputStream, ZipOutputStream}
import scala.collection.JavaConverters._
import scala.util.matching.Regex
import scala.util.control.NonFatal
import scala.sys.process._
import io.circe.{Decoder, HCursor, Json}
import io.circe.parser
import io.circe.syntax._

/*
 * @since   Aug. 14, 2026
 * @version Aug. 14, 2026
 * @author  ASAMI, Tomoharu
 */

private[cozy] trait CozyBokMetadata {
  self: CozyBokImplementation.type =>
  private[bok] final case class CategorySummary(slug: String, title: String, description: String, purpose: BokPurpose)

  private[bok] def _regular_category_summaries(source: Path): Vector[CategorySummary] =
    _category_summaries(source).filterNot(x => _is_special_category(x.slug))

  private def _is_special_category(slug: String): Boolean =
    slug == "glossary" || slug == "history"

  private def _category_summaries(source: Path): Vector[CategorySummary] =
    if (!Files.isDirectory(source))
      Vector.empty
    else {
      val stream = Files.list(source)
      try {
        stream.iterator.asScala.toVector.filter(Files.isDirectory(_)).flatMap { dir =>
          val category = dir.resolve("category.yaml")
          if (Files.isRegularFile(category))
            Some(CategorySummary(
              source.relativize(dir).toString,
              _yaml_value(category, "title").getOrElse(dir.getFileName.toString),
              _yaml_description(category).getOrElse(""),
              _yaml_purpose(category)
            ))
          else
            None
        }.sortBy(_.slug)
      } finally {
        stream.close()
      }
    }

  private[bok] def _category_contents(source: Path): Vector[CategoryContent] =
    _regular_category_summaries(source).map { summary =>
      val dir = source.resolve(summary.slug)
      CategoryContent(
        summary.slug,
        summary.title,
        summary.description,
        summary.purpose,
        _article_page_items(dir),
        _glossary_page_items(source.resolve("glossary").resolve(summary.slug), summary.slug)
      )
    }

  private[bok] def _source_article_count(config: BuildConfig): Int =
    _category_contents(config.sourcepath).map(_.articles.size).sum

  private def _article_page_items(dir: Path): Vector[CategoryPageItem] =
    if (!Files.isDirectory(dir))
      Vector.empty
    else {
      val stream = Files.walk(dir)
      try {
        stream.iterator.asScala.toVector.
          filter(Files.isRegularFile(_)).
          filter(_is_source_document).
          filterNot(_is_index_source_document).
          filterNot(x => dir.relativize(x).toString.replace(java.io.File.separatorChar, '/').startsWith("glossary/")).
          map { file =>
            val rel = dir.relativize(file).toString.replace(java.io.File.separatorChar, '/')
            CategoryPageItem(
              _source_document_html_href(rel),
              _dox_title(file),
              _dox_brief(file),
              _modified_at_millis(file)
            )
          }.sortBy(_.href)
      } finally {
        stream.close()
      }
    }

  private def _glossary_page_items(dir: Path, category: String): Vector[CategoryPageItem] =
    if (!Files.isDirectory(dir))
      Vector.empty
    else {
      val stream = Files.walk(dir)
      try {
        stream.iterator.asScala.toVector.
          filter(Files.isRegularFile(_)).
          filter(_is_source_document).
          filterNot(_is_index_source_document).
          map { file =>
            val rel = dir.relativize(file).toString.replace(java.io.File.separatorChar, '/')
            val href = s"../glossary/${category}/${_source_document_html_href(rel)}"
            val content = Files.readString(file, StandardCharsets.UTF_8)
            CategoryPageItem(
              href,
              _dox_title(file),
              _dox_brief(file),
              _modified_at_millis(file),
              _dox_reading(file)
            )
          }.sortBy(_.href)
      } finally {
        stream.close()
      }
    }

  private[bok] def _dox_title(file: Path): String = {
    val content = Files.readString(file, StandardCharsets.UTF_8)
    val metadata = _dox_metadata(file)
    _effective_headline(metadata, "en").
      orElse(metadata.flatMap(_.getTitleStringDefault).filter(_.nonEmpty)).
      getOrElse(content.linesIterator.map(_.trim).find(_.nonEmpty).getOrElse(_titleize(_source_document_stem(file.getFileName.toString))))
  }

  private[bok] def _dox_brief(file: Path): String = {
    val metadata = _dox_metadata(file)
    _dox_metadata_property_string(metadata, Vector("brief", "summary", "description")).
      orElse(_markdown_front_matter_value(file, Vector("brief", "summary", "description"))).
      orElse(_effective_brief(metadata, "en")).
      getOrElse(_dox_brief_fallback(Files.readString(file, StandardCharsets.UTF_8)))
  }

  private def _dox_brief_fallback(content: String): String = {
    val lines = content.linesIterator.toVector
    val sectionnames = Set("## BRIEF", "## SUMMARY", "## DESCRIPTION")
    lines.zipWithIndex.collectFirst {
      case (line, i) if sectionnames.contains(line.trim) =>
        lines.drop(i + 1).map(_.trim).find(_.nonEmpty).getOrElse("")
    }.filter(_.nonEmpty).getOrElse("Category entry.")
  }

  private def _dox_metadata(file: Option[Path]): Option[DocumentMetaData] =
    file.flatMap(_dox_metadata)

  private def _dox_metadata(file: Path): Option[DocumentMetaData] =
    if (Files.isRegularFile(file)) {
      val content = Files.readString(file, StandardCharsets.UTF_8)
      val filename = file.getFileName.toString
      val suffix = filename.lastIndexOf('.') match {
        case n if n >= 0 => filename.substring(n + 1).toLowerCase(Locale.ROOT)
        case _ => ""
      }
      val config = suffix match {
        case "md" | "markdown" => Dox2Parser.Config.markdown
        case _ => Dox2Parser.Config.default
      }
      val dox = Dox2Parser.parseWithFilename(config, file.toString, content)
      _dox_metadata_from_dox(dox)
    } else {
      None
    }

  private def _dox_metadata_from_dox(dox: Dox): Option[DocumentMetaData] =
    Dox.getMetadata(dox).flatMap(_.toOption).orElse {
      dox.elements.toStream.flatMap(_dox_metadata_from_dox).headOption
    }

  private def _effective_headline(metadata: Option[DocumentMetaData], locale: String): Option[String] =
    metadata.flatMap(_.getEffectiveHeadlineString(_to_locale(locale))).filter(_.nonEmpty)

  private def _effective_brief(metadata: Option[DocumentMetaData], locale: String): Option[String] =
    metadata.flatMap(_.getEffectiveBriefString(_to_locale(locale))).filter(_.nonEmpty)

  private def _dox_reading(file: Path): Option[String] = {
    val content = Files.readString(file, StandardCharsets.UTF_8)
    _dox_metadata_property_string(_dox_metadata(file), Vector("reading", "yomi", "読み")).
      orElse(_markdown_front_matter_value(file, Vector("reading", "yomi", "読み"))).
      orElse(_dox_head_value(content, Vector("reading", "yomi", "読み")))
  }

  private def _markdown_front_matter_value(file: Path, keys: Vector[String]): Option[String] =
    if (!_is_markdown_source_document(file))
      None
    else {
      val lines = Files.readAllLines(file, StandardCharsets.UTF_8).asScala.toVector
      if (!lines.headOption.exists(_.trim == "---"))
        None
      else {
        val keyset = keys.map(_.toLowerCase(Locale.ROOT)).toSet
        lines.zipWithIndex.drop(1).find(_._1.trim == "---").flatMap { case (_, end) =>
          lines.slice(1, end).collectFirst {
            case line if line.contains(":") && keyset.contains(line.takeWhile(_ != ':').trim.toLowerCase(Locale.ROOT)) =>
              _unquote(line.dropWhile(_ != ':').drop(1).trim)
          }.filter(_.nonEmpty)
        }
      }
    }

  private def _dox_metadata_property_string(metadata: Option[DocumentMetaData], keys: Vector[String]): Option[String] =
    metadata.flatMap(_.properties).flatMap { hocon =>
      keys.toStream.flatMap { key =>
        try {
          if (hocon.hasPath(key))
            Some(hocon.getString(key)).filter(_.nonEmpty)
          else
            None
        } catch {
          case NonFatal(_) => None
        }
      }.headOption
    }

  private def _dox_head_value(content: String, keys: Vector[String]): Option[String] = {
    val keyset = keys.toSet
    val lines = content.linesIterator.toVector
    val headindex = lines.indexWhere(_.trim == "# HEAD")
    if (headindex < 0)
      None
    else {
      val headlines = lines.drop(headindex + 1).takeWhile { line =>
        val s = line.trim
        !s.startsWith("# ") || s.startsWith("## ")
      }
      headlines.collectFirst {
        case line if _head_property_key(line).exists(keyset.contains) =>
          _head_property_value(line)
      }.flatten
    }
  }

  private def _head_property_key(line: String): Option[String] =
    line.trim.indexOf('=') match {
      case n if n > 0 => Some(line.trim.substring(0, n).trim)
      case _ => None
    }

  private def _head_property_value(line: String): Option[String] =
    line.trim.indexOf('=') match {
      case n if n >= 0 => Some(line.trim.substring(n + 1).trim).filter(_.nonEmpty)
      case _ => None
    }

  private[bok] def _modified_at_millis(file: Path): Long =
    Files.getLastModifiedTime(file).toMillis

  private def _yaml_value(file: Path, key: String): Option[String] =
    Files.readAllLines(file, StandardCharsets.UTF_8).asScala.collectFirst {
      case line if line.trim.startsWith(s"${key}:") =>
        _unquote(line.trim.substring(key.length + 1).trim)
    }.filter(_.nonEmpty)

  private def _yaml_description(file: Path): Option[String] = {
    val lines = Files.readAllLines(file, StandardCharsets.UTF_8).asScala.toVector
    _yaml_value(file, "description").orElse {
      lines.sliding(2).collectFirst {
        case Vector(a, b) if a.trim == "description:" && b.trim.startsWith("ja:") =>
          _unquote(b.trim.substring(3).trim)
      }
    }.orElse {
      lines.sliding(3).collectFirst {
        case Vector(a, _, c) if a.trim == "description:" && c.trim.startsWith("ja:") =>
          _unquote(c.trim.substring(3).trim)
      }
    }
  }

  private def _yaml_purpose(file: Path): BokPurpose =
    _purpose_from_parts(
      _yaml_value(file, "vision"),
      _first_non_empty(_yaml_goal_tree(file, "goals"), _yaml_goal_tree(file, "goal_tree")),
      _yaml_list(file, "goals"),
      _yaml_list(file, "subgoals")
    )

  private def _yaml_goal_tree(file: Path, key: String): Vector[BokGoal] = {
    val lines = Files.readAllLines(file, StandardCharsets.UTF_8).asScala.toVector
    lines.zipWithIndex.collectFirst {
      case (line, i) if line.trim == s"${key}:" =>
        val baseindent = line.takeWhile(_.isWhitespace).length
        val block = lines.drop(i + 1).takeWhile { raw =>
          raw.trim.isEmpty || raw.takeWhile(_.isWhitespace).length > baseindent
        }
        _parse_yaml_goal_block(block)
    }.getOrElse(Vector.empty)
  }

  private def _parse_yaml_goal_block(lines: Vector[String]): Vector[BokGoal] = {
    var goals = Vector.empty[BokGoal]
    var current: Option[BokGoal] = None
    var subcollecting = false
    var subindent = 0

    def _flush_(): Unit = {
      current.filterNot(_.isEmpty).foreach(x => goals = goals :+ x)
      current = None
      subcollecting = false
    }

    lines.foreach { raw =>
      val trimmed = raw.trim
      val indent = raw.takeWhile(_.isWhitespace).length
      if (trimmed.nonEmpty) {
        if (subcollecting && indent <= subindent && !trimmed.startsWith("-")) {
          subcollecting = false
        }
        if (trimmed.startsWith("- ")) {
          val rest = trimmed.drop(2).trim
          if (subcollecting) {
            val value = _unquote(rest)
            current = current.map(g => g.copy(subgoals = g.subgoals :+ value))
          } else {
            _flush_()
            _parse_key_value(rest) match {
              case Some((k, v)) if k == "title" || k == "goal" || k == "name" =>
                current = Some(BokGoal(v, Vector.empty))
              case _ =>
                current = Some(BokGoal(_unquote(rest), Vector.empty))
            }
          }
        } else if (trimmed == "subgoals:") {
          subcollecting = true
          subindent = indent
        } else _parse_key_value(trimmed) match {
          case Some((k, v)) if k == "title" || k == "goal" || k == "name" =>
            current = Some(current.getOrElse(BokGoal("", Vector.empty)).copy(title = v))
          case Some((k, v)) if k == "subgoals" =>
            current = current.map(g => g.copy(subgoals = g.subgoals ++ _parse_inline_list(v)))
          case _ =>
        }
      }
    }
    _flush_()
    goals.filterNot(_.isEmpty)
  }

  private def _yaml_list(file: Path, key: String): Vector[String] = {
    val lines = Files.readAllLines(file, StandardCharsets.UTF_8).asScala.toVector
    _yaml_value(file, key).map(_parse_inline_list).filter(_.nonEmpty).getOrElse {
      lines.zipWithIndex.collectFirst {
        case (line, i) if line.trim == s"${key}:" =>
          val baseindent = line.takeWhile(_.isWhitespace).length
          lines.drop(i + 1).takeWhile { raw =>
            val trimmed = raw.trim
            trimmed.isEmpty || raw.takeWhile(_.isWhitespace).length > baseindent
          }.map(_.trim).collect {
            case item if item.startsWith("-") => _unquote(item.drop(1).trim)
          }.filter(_.nonEmpty)
      }.getOrElse(Vector.empty)
    }
  }

}
