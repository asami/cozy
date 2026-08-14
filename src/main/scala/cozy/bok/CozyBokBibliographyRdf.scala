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

private[cozy] trait CozyBokBibliographyRdf {
  self: CozyBokImplementation.type =>
  private[bok] def _sync_effective_bibliography_fragments(config: BuildConfig): Unit =
    _bibliography_index(config).foreach { index =>
      val entries = index.entries.filterNot(_.needsresolution)
      val path = config.doxsitePath.resolve("metadata/documents/fragments.json")
      if (entries.nonEmpty && Files.isRegularFile(path))
        parser.parse(Files.readString(path, StandardCharsets.UTF_8)).toOption.foreach { json =>
          _write_text(path, _rewrite_bibliography_fragment_json(json, entries).spaces2 + "\n")
        }
    }

  private def _rewrite_bibliography_fragment_json(json: Json, entries: Vector[BibliographyEntry]): Json =
    json.arrayOrObject(
      json,
      values => Json.fromValues(values.map(_rewrite_bibliography_fragment_json(_, entries))),
      obj => {
        val fields = obj.toVector
        val sourcepath = fields.find(_._1 == "source_path").flatMap(_._2.asString)
        val locale = fields.find(_._1 == "locale").flatMap(_._2.asString).getOrElse("en")
        Json.obj(fields.map {
          case ("body_html", value) =>
            "body_html" -> value.asString.
              map(body => Json.fromString(_rewrite_bibliography_fragment_body(sourcepath, locale, body, entries))).
              getOrElse(_rewrite_bibliography_fragment_json(value, entries))
          case (key, value) =>
            key -> _rewrite_bibliography_fragment_json(value, entries)
        }: _*)
      }
    )

  private def _rewrite_bibliography_fragment_body(sourcepath: Option[String], locale: String, body: String, entries: Vector[BibliographyEntry]): String = {
    val normalized = _normalize_bibliography_fragment_heading(locale, body)
    sourcepath.map { source =>
      val references = entries.flatMap { entry =>
        entry.sourcerefs.filter(_.sourcepath == source).map(_ -> entry)
      }
      references.foldLeft(normalized) {
        case (html, (ref, entry)) => _rewrite_bibliography_reference_item(html, ref, entry)
      }
    }.getOrElse(normalized)
  }

  private def _normalize_bibliography_fragment_heading(locale: String, body: String): String = {
    val title = _html_escape(_ui(locale, "bibliography.title"))
    body.
      replace("<h2>参考文献</h2>", s"<h2>${title}</h2>").
      replace("<h2>参考情報</h2>", s"<h2>${title}</h2>").
      replace("<h2>参照情報</h2>", s"<h2>${title}</h2>")
  }

  private def _rewrite_bibliography_reference_item(html: String, ref: BibliographySourceRef, entry: BibliographyEntry): String = {
    val key = Pattern.quote(_html_escape(ref.citationkey))
    val pattern = Pattern.compile(
      s"""(?is)<li>(\\s*\\[\\d+\\]\\s*)<a class="bibliography-reference" href="[^"]*">${key}</a>\\.\\s*Unresolved bibliography reference[^<]*</li>"""
    )
    val matcher = pattern.matcher(html)
    val buffer = new StringBuffer
    while (matcher.find()) {
      val replacement =
        s"""<li>${matcher.group(1)}<a class="bibliography-reference" href="${_html_escape(_relative_href(ref.publicpath, entry.publicpath))}">${_html_escape(_bibliography_citation_label(entry))}</a>.</li>"""
      matcher.appendReplacement(buffer, java.util.regex.Matcher.quoteReplacement(replacement))
    }
    matcher.appendTail(buffer)
    buffer.toString
  }

  private[bok] def _relative_href(frompublicpath: String, targetpublicpath: String): String = {
    val depth = frompublicpath.split('/').dropRight(1).length
    ("../" * depth) + targetpublicpath
  }

  private def _bibliography_citation_label(entry: BibliographyEntry): String =
    entry.citation.
      filter(_.trim.nonEmpty).
      orElse(entry.key.map(key => s"${entry.title} [${key}]")).
      getOrElse(entry.title)

  private[bok] def _bibliography_rdf_aliases(index: BibliographyIndex): Vector[BibliographyRdfAlias] =
    index.entries.flatMap { entry =>
      _bibliography_alias_refs(entry).map { alias =>
        BibliographyRdfAlias(
          s"bibliography/${entry.categorySlug}/${_safe_file_name(alias)}",
          _html_path_without_suffix(entry.publicpath),
          entry.title
        )
      }
    }

  private def _bibliography_alias_refs(entry: BibliographyEntry): Vector[String] =
    _distinct_preserving_order(entry.refs ++ entry.key.toVector).
      filter(ref => ref != entry.id && _is_bare_bibliography_citation_key(ref))

  private def _html_path_without_suffix(path: String): String =
    path.stripSuffix(".html")

  private[bok] def _sync_effective_bibliography_turtle(path: Path, aliases: Vector[BibliographyRdfAlias]): Unit =
    if (Files.isRegularFile(path)) {
      val text = Files.readString(path, StandardCharsets.UTF_8)
      val replacements = _bibliography_turtle_replacements(text, aliases)
      if (replacements.nonEmpty || aliases.nonEmpty) {
        val source = if (replacements.nonEmpty) _remove_turtle_subject_blocks(text, replacements.keySet) else text
        val replaced = replacements.foldLeft(source) {
          case (acc, (from, to)) => acc.replace(s"<${from}>", s"<${to}>")
        }
        val rewritten = _deduplicate_turtle_subject_blocks(replaced)
        val cleaned = _replace_turtle_bibliography_placeholders(rewritten, aliases)
        _write_text(path, cleaned)
      }
    }

  private def _bibliography_turtle_replacements(text: String, aliases: Vector[BibliographyRdfAlias]): Map[String, String] =
    aliases.flatMap { alias =>
      _find_rdf_iri(text, alias.aliastail).flatMap { aliasiri =>
        _find_rdf_iri(text, alias.targettail).map { targetiri =>
          val resource = aliasiri -> targetiri
          val pages = _find_rdf_iris(text, s"${alias.aliastail}.html").map { pageiri =>
            pageiri -> pageiri.replace(alias.aliastail, alias.targettail)
          }
          resource +: pages
        }
      }.getOrElse(Vector.empty)
    }.toMap

  private def _find_rdf_iri(text: String, tail: String): Option[String] =
    _find_rdf_iris(text, tail).headOption

  private def _find_rdf_iris(text: String, tail: String): Vector[String] = {
    val pattern = ("(?i)<([^>]*" + Pattern.quote(tail) + ")>").r
    pattern.findAllMatchIn(text).map(_.group(1)).toVector.distinct
  }

  private def _remove_turtle_subject_blocks(text: String, subjects: Set[String]): String = {
    val lines = text.split("(?<=\\n)", -1).toVector
    val builder = new StringBuilder
    var i = 0
    while (i < lines.length) {
      _turtle_subject_iri(lines(i)) match {
        case Some(iri) if subjects.contains(iri) =>
          i += 1
          while (i < lines.length && _turtle_subject_iri(lines(i)).isEmpty)
            i += 1
        case _ =>
          builder.append(lines(i))
          i += 1
      }
    }
    builder.toString
  }

  private def _turtle_subject_iri(line: String): Option[String] = {
    val subject = "^<([^>]+)>\\s+.*".r
    line.trim match {
      case subject(iri) => Some(iri)
      case _ => None
    }
  }

  private def _deduplicate_turtle_subject_blocks(text: String): String = {
    val blocks = _turtle_blocks(text)
    val grouped = blocks.collect { case (Some(iri), block) => iri -> block }.groupBy(_._1).map {
      case (iri, values) => iri -> values.map(_._2)
    }
    blocks.foldLeft((Set.empty[String], Vector.empty[String])) {
      case ((seen, acc), (None, block)) => seen -> (acc :+ block)
      case ((seen, acc), (Some(iri), _)) if seen.contains(iri) => seen -> acc
      case ((seen, acc), (Some(iri), _)) =>
        val selected = _preferred_turtle_subject_block(grouped.getOrElse(iri, Vector.empty))
        (seen + iri) -> (acc :+ selected)
    }._2.mkString
  }

  private def _preferred_turtle_subject_block(blocks: Vector[String]): String =
    blocks.find(_is_resolved_turtle_subject_block).orElse(blocks.lastOption).getOrElse("")

  private def _is_resolved_turtle_subject_block(block: String): Boolean =
    block.contains("http://purl.org/dc/terms/source") ||
      block.contains("dcterms:source") ||
      !block.contains("Unresolved bibliography reference")

  private def _replace_turtle_bibliography_placeholders(text: String, aliases: Vector[BibliographyRdfAlias]): String =
    _turtle_blocks(text).map {
      case (Some(iri), block) =>
        aliases.find(alias => _ends_with_ignore_case(iri, alias.targettail)).
          map(alias => _replace_bibliography_placeholder_text(block, alias.title)).
          getOrElse(block)
      case (_, block) =>
        block
    }.mkString

  private def _replace_bibliography_placeholder_text(text: String, title: String): String =
    "Unresolved bibliography reference[^\"]*".r.replaceAllIn(text, java.util.regex.Matcher.quoteReplacement(_escape_rdf_string(title)))

  private def _escape_rdf_string(value: String): String =
    value.replace("\\", "\\\\").replace("\"", "\\\"")

  private def _turtle_blocks(text: String): Vector[(Option[String], String)] = {
    val lines = text.split("(?<=\\n)", -1).toVector
    var blocks = Vector.empty[(Option[String], String)]
    var currentiri: Option[String] = None
    val current = new StringBuilder
    lines.foreach { line =>
      _turtle_subject_iri(line) match {
        case Some(iri) =>
          if (current.nonEmpty)
            blocks :+= currentiri -> current.toString
          current.clear()
          currentiri = Some(iri)
          current.append(line)
        case None =>
          current.append(line)
      }
    }
    if (current.nonEmpty)
      blocks :+= currentiri -> current.toString
    blocks
  }

  private[bok] def _sync_effective_bibliography_jsonld(path: Path, aliases: Vector[BibliographyRdfAlias]): Unit =
    if (Files.isRegularFile(path)) {
      val text = Files.readString(path, StandardCharsets.UTF_8)
      parser.parse(text).toOption.foreach { json =>
        val replacements = _bibliography_jsonld_replacements(json, aliases)
        if (replacements.nonEmpty || aliases.nonEmpty) {
          val rewritten = _rewrite_jsonld_ids(json, replacements)
          val cleaned = _replace_jsonld_bibliography_placeholders(rewritten, aliases)
          _write_text(path, cleaned.spaces2 + "\n")
        }
      }
    }

  private def _bibliography_jsonld_replacements(json: Json, aliases: Vector[BibliographyRdfAlias]): Map[String, String] = {
    val ids = _jsonld_ids(json)
    aliases.flatMap { alias =>
      ids.find(_ends_with_ignore_case(_, alias.aliastail)).flatMap { aliasid =>
        ids.find(_ends_with_ignore_case(_, alias.targettail)).map { targetid =>
          val resource = aliasid -> targetid
          val pages = ids.filter(_ends_with_ignore_case(_, s"${alias.aliastail}.html")).map { pageid =>
            pageid -> pageid.replace(alias.aliastail, alias.targettail)
          }
          resource +: pages
        }
      }.getOrElse(Vector.empty)
    }.toMap
  }

  private def _ends_with_ignore_case(value: String, suffix: String): Boolean =
    value.toLowerCase(Locale.ROOT).endsWith(suffix.toLowerCase(Locale.ROOT))

  private def _jsonld_ids(json: Json): Vector[String] =
    json.arrayOrObject(
      json.asString.toVector,
      _.flatMap(_jsonld_ids).toVector,
      obj => obj.toVector.flatMap {
        case ("@id", value) => value.asString.toVector ++ _jsonld_ids(value)
        case (_, value) => _jsonld_ids(value)
      }.toVector
    ).distinct

  private def _rewrite_jsonld_ids(json: Json, replacements: Map[String, String]): Json =
    json.arrayOrObject(
      json,
      values => Json.fromValues(values.map(_rewrite_jsonld_ids(_, replacements))),
      obj => Json.obj(obj.toVector.flatMap {
        case ("@graph", value) =>
          Some("@graph" -> _rewrite_jsonld_graph(value, replacements))
        case ("@id", value) =>
          Some("@id" -> value.asString.flatMap(replacements.get).map(Json.fromString).getOrElse(_rewrite_jsonld_ids(value, replacements)))
        case (key, value) =>
          Some(key -> _rewrite_jsonld_ids(value, replacements))
      }: _*)
    )

  private def _rewrite_jsonld_graph(json: Json, replacements: Map[String, String]): Json =
    json.asArray.map { values =>
      val rewritten = values.flatMap { value =>
        val id = value.hcursor.downField("@id").as[String].toOption
        if (id.exists(replacements.contains))
          None
        else
          Some(_rewrite_jsonld_ids(value, replacements))
      }
      Json.fromValues(_deduplicate_jsonld_graph_nodes(rewritten.toVector))
    }.getOrElse(_rewrite_jsonld_ids(json, replacements))

  private def _replace_jsonld_bibliography_placeholders(json: Json, aliases: Vector[BibliographyRdfAlias]): Json =
    _replace_jsonld_bibliography_placeholders(json, aliases, None)

  private def _replace_jsonld_bibliography_placeholders(json: Json, aliases: Vector[BibliographyRdfAlias], title: Option[String]): Json =
    json.arrayOrObject(
      json.asString.map(value => Json.fromString(if (title.exists(_ => _is_unresolved_bibliography_placeholder(value))) title.get else value)).getOrElse(json),
      values => Json.fromValues(values.map(_replace_jsonld_bibliography_placeholders(_, aliases, title))),
      obj => {
        val id = obj("@id").flatMap(_.asString)
        val nexttitle = id.flatMap(value => aliases.find(alias => _ends_with_ignore_case(value, alias.targettail)).map(_.title)).orElse(title)
        Json.obj(obj.toVector.map {
          case (key, value) => key -> _replace_jsonld_bibliography_placeholders(value, aliases, nexttitle)
        }: _*)
      }
    )

  private def _deduplicate_jsonld_graph_nodes(values: Vector[Json]): Vector[Json] = {
    val grouped = values.flatMap { value =>
      value.hcursor.downField("@id").as[String].toOption.map(_ -> value)
    }.groupBy(_._1).map {
      case (id, xs) => id -> xs.map(_._2)
    }
    values.foldLeft((Set.empty[String], Vector.empty[Json])) { (state, value) =>
      val (seen, acc) = state
      value.hcursor.downField("@id").as[String].toOption match {
        case Some(id) if seen.contains(id) =>
          seen -> acc
        case Some(id) =>
          val selected = _preferred_jsonld_graph_node(grouped.getOrElse(id, Vector.empty))
          (seen + id) -> (acc :+ selected)
        case None =>
          seen -> (acc :+ value)
      }
    }._2
  }

  private def _preferred_jsonld_graph_node(values: Vector[Json]): Json =
    values.find(_is_resolved_jsonld_graph_node).orElse(values.lastOption).getOrElse(Json.Null)

  private def _is_resolved_jsonld_graph_node(value: Json): Boolean = {
    val text = value.noSpaces
    text.contains("dcterms:source") ||
      text.contains("http://purl.org/dc/terms/source") ||
      !text.contains("Unresolved bibliography reference")
  }

  private[bok] def _ensure_bibliography_cache(config: BuildConfig, entry: BibliographyEntry, fetcher: BibliographyBibtexFetcher): Option[String] = {
    val path = config.project.resolve("target/cozy-bok/bibliography/cache").resolve(s"${_safe_file_name(entry.id)}.bib")
    if (_bibliography_source_needs_local_bib(entry)) {
      fetcher.fetchEntry(entry).foreach(_write_text(path, _))
      None
    } else if (!entry.needsresolution && entry.bibtex.raw.isEmpty && entry.bibtex.sourceurl.isEmpty)
      None
    else if (Files.isRegularFile(path))
      None
    else if (entry.bibtex.raw.nonEmpty) {
      _write_text(path, entry.bibtex.raw.get)
      None
    } else if (!config.bibliographyService)
      Some(entry.id)
    else {
      val body =
        fetcher.fetchEntry(entry)
      body match {
        case Some(value) =>
          _write_text(path, value)
          None
        case None => Some(entry.id)
      }
    }
  }

  private[bok] def _ensure_bibliography_metadata_handoff(config: BuildConfig): Unit =
    if (_source_declares_bibliography(config) && _bibliography_index(config).forall(_.entries.isEmpty))
      _raise_missing_bibliography_handoff()

  private def _raise_missing_bibliography_handoff(): Unit =
      RAISE.invalidArgumentFault(
        "SmartDox bibliography metadata was not generated even though BoK source declares bibliography references or BibTeX sources. " +
          "Update the dox/SmartDox runtime used by cozy bok build, or run through a launcher that provides SmartDox bibliography support."
      )

  private def _source_declares_bibliography(config: BuildConfig): Boolean = {
    val root = config.project.resolve("src/main/doxsite")
    if (!Files.isDirectory(root))
      false
    else {
      val stream = Files.walk(root)
      try {
        stream.iterator.asScala.exists { path =>
          Files.isRegularFile(path) && _source_declares_bibliography(path, root)
        }
      } finally {
        stream.close()
      }
    }
  }

  private def _source_declares_bibliography(path: Path, root: Path): Boolean = {
    val relative = root.relativize(path).toString.replace(java.io.File.separatorChar, '/')
    val name = path.getFileName.toString.toLowerCase(java.util.Locale.ROOT)
    if (relative.startsWith("bibliography/") && (name.endsWith(".dox") || name.endsWith(".md") || name.endsWith(".markdown") || name.endsWith(".bib")))
      true
    else if (name.endsWith(".dox") || name.endsWith(".md") || name.endsWith(".markdown")) {
      val text = Files.readString(path, StandardCharsets.UTF_8)
      text.split("\\r?\\n").exists { line =>
        val trimmed = line.trim
        trimmed.startsWith("bibliography.refs") ||
          trimmed.startsWith("references.bibliography") ||
          trimmed == "bibliography:" ||
          trimmed == "references:" ||
          trimmed.contains("bib:[") ||
          trimmed.startsWith("bibid") ||
          trimmed.startsWith("bibids")
      }
    } else {
      false
    }
  }

  private[bok] def _resolve_cached_bibliography_entry(config: BuildConfig, entry: BibliographyEntry): BibliographyEntry = {
    val path = config.project.resolve("target/cozy-bok/bibliography/cache").resolve(s"${_safe_file_name(entry.id)}.bib")
    if (!Files.isRegularFile(path))
      entry
    else {
      val raw = Files.readString(path, StandardCharsets.UTF_8)
      BibliographyBibtexParser.parse(raw).map { fields =>
        val authors = fields.get("author").map(BibliographyBibtexParser.authors).getOrElse(entry.authors)
        val title =
          if (_is_curated_bibliography_source(entry))
            entry.title
          else
            fields.get("title").getOrElse(entry.title)
        entry.copy(
          entrytype = fields.getOrElse("type", entry.entrytype),
          title = title,
          summary = _resolved_bibliography_summary(entry),
          authors = if (authors.nonEmpty) authors else entry.authors,
          publishedat = entry.publishedat.orElse(fields.get("year")),
          publisher = entry.publisher.orElse(fields.get("publisher")),
          sourceurl = entry.sourceurl.orElse(fields.get("url")),
          citation = entry.citation.orElse(BibliographyBibtexParser.citation(fields)),
          identifiers = entry.identifiers.copy(doi = entry.identifiers.doi.orElse(fields.get("doi")), isbn = entry.identifiers.isbn.orElse(fields.get("isbn")), url = entry.identifiers.url.orElse(fields.get("url"))),
          key = entry.key.orElse(fields.get("id")),
          bibtex = entry.bibtex.copy(entrytype = entry.bibtex.entrytype.orElse(fields.get("type")), raw = Some(raw)),
          sourcekind = if (entry.sourcekind == "external-ref") "external-cache" else entry.sourcekind,
          needsresolution = false,
          quality = entry.quality.copy(missingcitation = false, missingsource = false, missingnarrative = entry.bodyhtml.trim.isEmpty, needscuration = entry.bodyhtml.trim.isEmpty)
        )
      }.getOrElse(entry)
    }
  }

  private def _resolved_bibliography_summary(entry: BibliographyEntry): Option[String] =
    entry.summary.filterNot(_is_unresolved_bibliography_placeholder)

  private def _is_unresolved_bibliography_placeholder(value: String): Boolean =
    value.trim.toLowerCase(Locale.ROOT).startsWith("unresolved bibliography reference")

  private def _bibliography_source_needs_local_bib(entry: BibliographyEntry): Boolean =
    _is_curated_bibliography_source(entry) &&
      entry.key.isEmpty &&
      entry.bibtex.raw.isEmpty &&
      entry.bibtex.sourceurl.isEmpty

  private def _is_curated_bibliography_source(entry: BibliographyEntry): Boolean = {
    val source = entry.sourcepath.toLowerCase(Locale.ROOT)
    source.endsWith(".bib.dox") || source.endsWith(".bib.md") || source.endsWith(".bib.markdown")
  }

}
