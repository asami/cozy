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

private[cozy] trait CozyBokScaffold {
  self: CozyBokImplementation.type =>
  private[bok] def _inspect_bok(input: Path): BokInspection = {
    val root = _find_bok_root(input)
    val markers = root.map(_bok_markers).getOrElse(Vector.empty)
    val issues = root.map(_bok_issues).getOrElse(Vector(s"BoK root was not found from ${input.toAbsolutePath.normalize}"))
    val fixes = root.map(_bok_fixes).getOrElse(Vector.empty)
    BokInspection(input.toAbsolutePath.normalize, root, markers, issues, fixes)
  }

  private def _bok_markers(root: Path): Vector[String] =
    Vector(
      root.resolve(".cozy/config.yaml"),
      root.resolve(".cozy/config.yml"),
      root.resolve(".cozy/config.json"),
      root.resolve(".cozy/config.conf"),
      root.resolve("conf/cozy/config.yaml"),
      root.resolve("conf/cozy/config.yml"),
      root.resolve("conf/cozy/config.json"),
      root.resolve("conf/cozy/config.conf"),
      root.resolve("src/main/doxsite"),
      root.resolve("src/main/doxsite/site.conf"),
      root.resolve("src/main/publication")
    ).filter(p => Files.exists(p)).map(p => root.relativize(p).toString)

  private def _bok_issues(root: Path): Vector[String] = {
    val configfiles = CozyProjectYamlConfig.operationDefaultFiles(root).filter(x => Files.isRegularFile(x))
    val config = _load_config(root)
    val missingconfig =
      if (configfiles.isEmpty) Vector("Missing BoK config: .cozy/config.yaml or conf/cozy/config.yaml") else Vector.empty
    val olddocker =
      if (configfiles.exists(p => _read_text(p).contains("simplemodeling/cozy-toolchain:latest")))
        Vector("Legacy Docker image reference found: simplemodeling/cozy-toolchain:latest")
      else
        Vector.empty
    val missingsource =
      if (!Files.isDirectory(root.resolve("src/main/doxsite")))
        Vector("Missing BoK source directory: src/main/doxsite")
      else
        Vector.empty
    val missingsite =
      if (!Files.isRegularFile(root.resolve("src/main/doxsite/site.conf")))
        Vector("Missing BoK site config: src/main/doxsite/site.conf")
      else
        Vector.empty
    val missinggitignore = _missing_gitignore_entries(root)
    val gitignoreissue =
      if (missinggitignore.nonEmpty)
        Vector(s"Generated/work directories are not fully ignored: ${missinggitignore.mkString(", ")}")
      else
        Vector.empty
    val missingupload =
      if (config.value("bok.workflow.upload.command").isEmpty)
        Vector("Missing upload workflow command: bok.workflow.upload.command")
      else
        Vector.empty
    val doxissues = _dox_metadata_section_issues(root).map { issue =>
      s"SmartDox Dox metadata section heading must be followed by a blank line: ${root.relativize(issue.path)}:${issue.line} ${issue.heading}"
    }
    val markdownissues = _markdown_metadata_issues(root).map { issue =>
      s"Markdown front matter metadata issue: ${root.relativize(issue.path)}: ${issue.message}"
    }
    missingconfig ++ olddocker ++ missingsource ++ missingsite ++ gitignoreissue ++ missingupload ++ doxissues ++ markdownissues
  }

  private def _bok_fixes(root: Path): Vector[BokFix] = {
    val configfiles = CozyProjectYamlConfig.operationDefaultFiles(root).filter(x => Files.isRegularFile(x))
    val createconfig =
      if (configfiles.isEmpty)
        Vector(BokFix("Create .cozy/config.yaml with current BoK defaults", () => _write_text(root.resolve(".cozy/config.yaml"), _cozy_config())))
      else
        Vector.empty
    val updatedocker =
      configfiles.filter(p => _read_text(p).contains("simplemodeling/cozy-toolchain:latest")).map { path =>
        BokFix(s"Replace legacy Docker image in ${root.relativize(path)}", () => {
          val current = _read_text(path)
          _write_text(path, current.replace("simplemodeling/cozy-toolchain:latest", _default_docker_image))
        })
      }.toVector
    val gitignoreentries = _missing_gitignore_entries(root)
    val gitignorefix =
      if (gitignoreentries.nonEmpty)
        Vector(BokFix("Append generated/work directory ignores to .gitignore", () => _append_gitignore_entries(root.resolve(".gitignore"), gitignoreentries)))
      else
        Vector.empty
    val doxfixes = _dox_metadata_section_issues(root).groupBy(_.path).toVector.sortBy(_._1.toString).map {
      case (path, issues) =>
        val description =
          if (issues.size == 1)
            s"Insert blank line after SmartDox metadata section heading in ${root.relativize(path)}:${issues.head.line}"
          else
            s"Insert blank lines after ${issues.size} SmartDox metadata section headings in ${root.relativize(path)}"
        BokFix(description, () => _fix_dox_metadata_section_spacing(path))
    }
    createconfig ++ updatedocker ++ gitignorefix ++ doxfixes
  }

  private def _dox_metadata_section_issues(root: Path): Vector[DoxMetadataSectionIssue] = {
    val source = root.resolve("src/main/doxsite")
    if (!Files.isDirectory(source))
      Vector.empty
    else {
      val stream = Files.walk(source)
      try {
        stream.iterator.asScala.toVector.
          filter(path => Files.isRegularFile(path) && path.getFileName.toString.endsWith(".dox")).
          flatMap(_dox_metadata_section_issues_in_file)
      } finally {
        stream.close()
      }
    }
  }

  private def _dox_metadata_section_issues_in_file(path: Path): Vector[DoxMetadataSectionIssue] = {
    val lines = Files.readAllLines(path, StandardCharsets.UTF_8).asScala.toVector
    lines.zipWithIndex.flatMap {
      case (line, index) =>
        _dox_metadata_section_heading(line) match {
          case Some(heading) if index + 1 >= lines.length || lines(index + 1).trim.nonEmpty =>
            Some(DoxMetadataSectionIssue(path, index + 1, heading))
          case _ =>
            None
        }
    }
  }

  private def _dox_metadata_section_heading(line: String): Option[String] = {
    val trimmed = line.trim
    val markerlength = trimmed.takeWhile(c => c == '#' || c == '*').length
    if (markerlength > 0 && trimmed.length > markerlength && trimmed.charAt(markerlength) == ' ') {
      val name = trimmed.drop(markerlength + 1).trim
      if (_dox_metadata_section_names.contains(name))
        Some(trimmed)
      else
        None
    } else
      None
  }

  private def _markdown_metadata_issues(root: Path): Vector[MarkdownMetadataIssue] = {
    val source = root.resolve("src/main/doxsite")
    if (!Files.isDirectory(source))
      Vector.empty
    else {
      val stream = Files.walk(source)
      try {
        stream.iterator.asScala.toVector.
          filter(path => Files.isRegularFile(path) && _is_markdown_source_document(path)).
          flatMap(_markdown_metadata_issues_in_file)
      } finally {
        stream.close()
      }
    }
  }

  private def _markdown_metadata_issues_in_file(path: Path): Vector[MarkdownMetadataIssue] = {
    val lines = Files.readAllLines(path, StandardCharsets.UTF_8).asScala.toVector
    if (lines.headOption.exists(_.trim == "---")) {
      val end = lines.zipWithIndex.drop(1).find(_._1.trim == "---").map(_._2)
      end match {
        case Some(n) =>
          val keys = lines.slice(1, n).flatMap { line =>
            val trimmed = line.trim
            if (trimmed.startsWith("#") || !trimmed.contains(":"))
              None
            else
              Some(trimmed.takeWhile(_ != ':').trim.toLowerCase(java.util.Locale.ROOT))
          }.toSet
          val title = keys.contains("title") || keys.contains("headline")
          val brief = keys.contains("brief") || keys.contains("summary") || keys.contains("description")
          Vector(
            if (title) None else Some(MarkdownMetadataIssue(path, "front matter should include title or headline")),
            if (brief) None else Some(MarkdownMetadataIssue(path, "front matter should include brief, summary, or description"))
          ).flatten
        case None =>
          Vector(MarkdownMetadataIssue(path, "front matter starts with --- but has no closing ---"))
      }
    } else {
      Vector.empty
    }
  }

  private def _fix_dox_metadata_section_spacing(path: Path): Unit = {
    val original = Files.readAllLines(path, StandardCharsets.UTF_8).asScala.toVector
    val fixed = original.zipWithIndex.flatMap {
      case (line, index) =>
        val needsblank = _dox_metadata_section_heading(line).isDefined &&
          (index + 1 >= original.length || original(index + 1).trim.nonEmpty)
        if (needsblank)
          Vector(line, "")
        else
          Vector(line)
    }
    _write_text(path, fixed.mkString("\n") + "\n")
  }

  private[bok] val _bok_guide_scenarios: Vector[(String, String, Vector[String])] = Vector(
    (
      "create-bok",
      "Create a new BoK project",
      Vector(
        "1. cozy bok create --save <project-dir> --name <name> --url <site-url> --language ja",
        "2. cd <project-dir>",
        "3. edit src/main/doxsite/site.conf and category sources",
        "4. define BoK Vision, Goals, and Subgoals in src/main/doxsite/site.conf",
        "5. define category Vision, Goals, and Subgoals in category.yaml when needed",
        "6. cozy bok doctor",
        "7. cozy bok build"
      )
    ),
    (
      "daily-build",
      "Inspect and build an existing BoK",
      Vector(
        "1. cd <project-dir>",
        "2. cozy bok doctor",
        "3. cozy bok fix --dry-run",
        "4. update site.conf/category.yaml Vision, Goals, and Subgoals if operational intent changed",
        "5. cozy bok build --strategy preview",
        "6. use cozy bok build --strategy preview --no-bib-service for offline/cache-only bibliography builds",
        s"7. cozy bok preview --port ${_default_preview_port}",
        s"8. open http://127.0.0.1:${_default_preview_port}/ in a browser; use the local Web server instead of opening website.d directly"
      )
    ),
    (
      "publish-dry-run",
      "Verify publication without side effects",
      Vector(
        "1. cd <project-dir>",
        "2. configure bok.workflow.upload.command in .cozy/config.yaml or conf/cozy/config.yaml",
        "3. cozy bok publish . --dry-run",
        "4. inspect target/cozy-bok/publish/latest/manifest.json",
        "5. run cozy bok publish . only after the plan is correct"
      )
    ),
    (
      "video-publication",
      "Publish .video packages into the BoK registry",
      Vector(
        "1. create src/main/doxsite/<category>/<slug>.video/index.dox",
        "2. create src/main/doxsite/<category>/<slug>.video/video.yaml",
        "3. keep generated mp4/rdf/captions outside the .video source package",
        "4. cozy bok publish-video .",
        "5. cozy bok build --strategy preview"
      )
    ),
    (
      "stage-upload",
      "Connect project-owned staging and upload scripts",
      Vector(
        "1. copy etc/website-stage.sh.proto to etc/website-stage.sh when staging is needed",
        "2. copy etc/website-upload.sh.proto to etc/website-upload.sh and edit the target",
        "3. configure bok.workflow.stage.command and bok.workflow.upload.command",
        "4. cozy bok stage .",
        "5. cozy bok upload ."
      )
    )
  )

  private[bok] def _print_bok_guide_scenario(name: String, title: String, lines: Vector[String]): Unit = {
    println(s"Cozy BoK guide: ${name}")
    println(title)
    lines.foreach(line => println(s"  ${line}"))
  }

  private def _missing_gitignore_entries(root: Path): Vector[String] = {
    val path = root.resolve(".gitignore")
    val existing =
      if (Files.isRegularFile(path))
        Files.readAllLines(path, StandardCharsets.UTF_8).asScala.map(_.trim).filter(_.nonEmpty).toSet
      else
        Set.empty[String]
    _generated_gitignore_entries.filterNot(existing.contains)
  }

  private def _append_gitignore_entries(path: Path, entries: Vector[String]): Unit = {
    val current = if (Files.isRegularFile(path)) _read_text(path) else ""
    val separator = if (current.isEmpty || current.endsWith("\n")) "" else "\n"
    _write_text(path, current + separator + entries.mkString("\n") + "\n")
  }

  private[bok] def _print_bok_inspection(inspection: BokInspection, config: DoctorConfig): Unit = {
    println("bok doctor")
    println(s"input: ${inspection.input}")
    println(s"status: ${inspection.status}")
    inspection.root match {
      case Some(root) => println(s"root: ${root}")
      case None => println("root: <not found>")
    }
    _print_list("bok root markers", inspection.markers)
    _print_list("issues", inspection.issues)
    _print_list(if (config.fix && config.dryRun) "planned fixes" else "fixes", inspection.fixes.map(_.description))
    _print_list("next steps", _bok_next_steps(inspection))
  }

  private def _bok_next_steps(inspection: BokInspection): Vector[String] =
    inspection.root.toVector.flatMap { root =>
      val port = _bok_preview_port(root)
      Vector(
        "Build generated site from the BoK root: cozy bok build --strategy preview",
        "Build generated site from another directory: cozy bok build <bok-root> --strategy preview",
        s"Serve website.d from the BoK root: cozy bok preview --port ${port}",
        s"Serve website.d from another directory: cozy bok preview <bok-root> --port ${port}",
        s"Open http://127.0.0.1:${port}/ in a browser; use the local Web server instead of opening generated HTML directly",
        "Strategy states: draft -> wip (work-in-progress) -> preview -> production/publish",
        "Strategy meaning: draft/wip are authoring states, preview is local/site verification, production is publish/upload readiness"
      )
    }

  private def _bok_preview_port(root: Path): String =
    _load_config(root).value("bok.preview.port").getOrElse(_default_preview_port)

  private def _print_list(label: String, values: Vector[String]): Unit = {
    println(s"${label}:")
    if (values.isEmpty)
      println("  - none")
    else
      values.foreach(x => println(s"  - ${x}"))
  }

  private[bok] def _apply_bok_fixes(inspection: BokInspection, config: DoctorConfig): Unit =
    inspection.root match {
      case None =>
        RAISE.invalidArgumentFault(s"Cannot fix because BoK root was not found from ${inspection.input}")
      case Some(_) if config.dryRun =>
        println("fix mode: dry-run")
      case Some(_) =>
        inspection.fixes.foreach(_.apply())
        println(s"applied fixes: ${inspection.fixes.length}")
    }

  private def _split_command(value: String): Vector[String] =
    value.trim.split("\\s+").toVector.filter(_.nonEmpty)

  private[bok] def _languages(config: CozyProjectYamlConfig.Config): Vector[String] =
    config.list("site.metadata.in_language") match {
      case xs if xs.nonEmpty => xs
      case _ =>
        config.value("site.metadata.in_language").map { x =>
          x.stripPrefix("[").stripSuffix("]").split(",").toVector.map(_.trim.stripPrefix("\"").stripSuffix("\"")).filter(_.nonEmpty)
        }.getOrElse(Vector("ja", "en"))
    }

  private[bok] def _languages(config: CozyProjectYamlConfig.Config, site: SiteConfig): Vector[String] =
    _languages(config) match {
      case xs if xs != Vector("ja", "en") => xs
      case _ =>
        site.list("site.metadata.in_language") match {
          case xs if xs.nonEmpty => xs
          case _ => site.value("site.metadata.in_language").map(_parse_inline_list).filter(_.nonEmpty).getOrElse(Vector("ja", "en"))
        }
    }

  private[bok] def _locale_mode(config: CozyProjectYamlConfig.Config, site: SiteConfig): LocaleMode =
    LocaleMode.create(
      config.value("site.output.locale_mode").
        orElse(config.value("bok.output.locale_mode")).
        orElse(site.value("site.output.locale_mode")).
        getOrElse {
          if (site.boolean("simplemodelingorg").getOrElse(false))
            "multi_locale_subdirs"
          else
            "single_locale_root"
        }
    )

  private[bok] def _default_locale(
    config: CozyProjectYamlConfig.Config,
    site: SiteConfig,
    languages: Vector[String]
  ): String =
    config.value("site.output.default_locale").
      orElse(config.value("bok.output.default_locale")).
      orElse(site.value("site.output.default_locale")).
      getOrElse(languages.headOption.getOrElse("ja"))

  private[bok] def _direct_assets(project: Path, config: CozyProjectYamlConfig.Config): DirectAssetsConfig = {
    val enabled = _boolean(config, "bok.direct-assets.enabled", false)
    val pairs = config.mapUnder("bok.direct-assets.items").toVector.sortBy(_._1)
    val grouped = pairs.groupBy(_._1.takeWhile(_ != '.')).toVector.sortBy(_._1).flatMap {
      case (_, kvs) =>
        val m = kvs.map { case (k, v) => k.dropWhile(_ != '.').drop(1) -> v }.toMap
        for {
          source <- m.get("source")
          dest <- m.get("destination")
        } yield DirectAsset(source, dest)
    }
    DirectAssetsConfig(enabled, if (grouped.nonEmpty) grouped else _direct_asset_list_items(project))
  }

  private def _direct_asset_list_items(project: Path): Vector[DirectAsset] = {
    CozyProjectYamlConfig.operationDefaultFiles(project).flatMap(_direct_asset_list_items_in_file)
  }

  private def _direct_asset_list_items_in_file(file: Path): Vector[DirectAsset] = {
    if (!Files.isRegularFile(file))
      Vector.empty
    else {
      var initems = false
      var itemsindent = -1
      var current = Map.empty[String, String]
      var items = Vector.empty[DirectAsset]
      var stack = Vector.empty[(Int, String)]

      def _flush_(): Unit = {
        for {
          source <- current.get("source")
          dest <- current.get("destination")
        } items = items :+ DirectAsset(source, dest)
        current = Map.empty
      }

      Files.readAllLines(file, StandardCharsets.UTF_8).asScala.foreach { raw =>
        val line = raw.takeWhile(_ != '#')
        val indent = line.takeWhile(_ == ' ').length
        val trimmed = line.trim
        if (trimmed.nonEmpty && !trimmed.startsWith("-")) {
          val n = trimmed.indexOf(':')
          if (n >= 0) {
            val key = trimmed.substring(0, n).trim
            stack = stack.dropRight(stack.reverse.takeWhile(_._1 >= indent).length) :+ (indent -> key)
          }
        }
        val path = stack.map(_._2).mkString(".")
        if (trimmed == "items:" && path == "bok.direct-assets.items") {
          initems = true
          itemsindent = indent
        } else if (initems && indent <= itemsindent && trimmed.nonEmpty && !trimmed.startsWith("-")) {
          _flush_()
          initems = false
        } else if (initems && trimmed.startsWith("- ")) {
          _flush_()
          _parse_key_value(trimmed.substring(2)).foreach { case (k, v) => current = current.updated(k, v) }
        } else if (initems) {
          _parse_key_value(trimmed).foreach { case (k, v) => current = current.updated(k, v) }
        }
      }
      _flush_()
      items
    }
  }

  private[bok] def _parse_key_value(value: String): Option[(String, String)] = {
    val n = value.indexOf(':')
    if (n < 0)
      None
    else {
      val key = value.substring(0, n).trim
      val v = _unquote(value.substring(n + 1).trim)
      if (key.isEmpty || v.isEmpty) None else Some(key -> v)
    }
  }

  private val _assignment: Regex = """^\s*([A-Za-z0-9_.-]+)\s*[:=]\s*(.+?)\s*$""".r

  private[bok] def _parse_site_values(lines: Vector[String]): Map[String, String] = {
    var stack = Vector.empty[(Int, String)]
    var values = Map.empty[String, String]
    lines.foreach { raw =>
      val line = raw.takeWhile(_ != '#')
      val trimmed = line.trim
      if (trimmed.nonEmpty && trimmed != "}" && !trimmed.startsWith("[") && !trimmed.startsWith("-")) {
        val indent = line.takeWhile(_.isWhitespace).length
        if (trimmed.endsWith("{")) {
          val key = trimmed.dropRight(1).trim
          stack = stack.dropRight(stack.reverse.takeWhile(_._1 >= indent).length) :+ (indent -> key)
        } else trimmed match {
          case _assignment(key, value) =>
            stack = stack.dropRight(stack.reverse.takeWhile(_._1 >= indent).length)
            val path = (stack.map(_._2) :+ key).mkString(".")
            values = values.updated(path, _unquote(value.stripSuffix(",")))
          case _ =>
        }
      }
    }
    values
  }

  private[bok] def _parse_site_lists(lines: Vector[String]): Map[String, Vector[String]] =
    _parse_site_values(lines).collect {
      case (key, value) if value.startsWith("[") && value.endsWith("]") =>
        key -> _parse_inline_list(value)
    } ++ _parse_site_block_lists(lines)

  private[bok] def _parse_site_goal_trees(lines: Vector[String]): Map[String, Vector[BokGoal]] = {
    val candidates = Set("site.metadata.goals", "site.metadata.goal_tree")
    var stack = Vector.empty[(Int, String)]
    var result = Map.empty[String, Vector[BokGoal]]
    var collecting: Option[(Int, String, Vector[BokGoal], Option[BokGoal], Boolean, Vector[String])] = None

    def _flush_current_(path: String, goals: Vector[BokGoal], current: Option[BokGoal]): Vector[BokGoal] =
      current.filterNot(_.isEmpty).map(goals :+ _).getOrElse(goals)

    lines.foreach { raw =>
      val line = raw.takeWhile(_ != '#')
      val trimmed = line.trim.stripSuffix(",")
      val indent = line.takeWhile(_.isWhitespace).length
      collecting match {
        case Some((baseindent, path, goals, current, subcollecting, subitems)) =>
          if (subcollecting) {
            if (trimmed == "]") {
              val updated = current.map(g => g.copy(subgoals = g.subgoals ++ subitems))
              collecting = Some((baseindent, path, goals, updated, false, Vector.empty))
            } else if (trimmed.startsWith("\"") || trimmed.startsWith("'")) {
              collecting = Some((baseindent, path, goals, current, true, subitems :+ _unquote(trimmed)))
            }
          } else if (trimmed == "]" && indent <= baseindent) {
            result = result.updated(path, _flush_current_(path, goals, current))
            collecting = None
          } else if (trimmed == "{" || trimmed == "{") {
            collecting = Some((baseindent, path, _flush_current_(path, goals, current), Some(BokGoal("", Vector.empty)), false, Vector.empty))
          } else if (trimmed == "}" || trimmed == "},") {
            collecting = Some((baseindent, path, _flush_current_(path, goals, current), None, false, Vector.empty))
          } else trimmed match {
            case _assignment(key, value) if key == "title" || key == "goal" || key == "name" =>
              val updated = current.map(_.copy(title = _unquote(value.stripSuffix(",")))).orElse(Some(BokGoal(_unquote(value.stripSuffix(",")), Vector.empty)))
              collecting = Some((baseindent, path, goals, updated, false, Vector.empty))
            case _assignment(key, value) if key == "subgoals" && value.startsWith("[") && value.endsWith("]") =>
              val updated = current.map(g => g.copy(subgoals = g.subgoals ++ _parse_inline_list(value)))
              collecting = Some((baseindent, path, goals, updated, false, Vector.empty))
            case _assignment(key, value) if key == "subgoals" && value == "[" =>
              collecting = Some((baseindent, path, goals, current, true, Vector.empty))
            case _ =>
          }
        case None =>
          if (trimmed.nonEmpty && trimmed != "}") {
            if (trimmed.endsWith("{")) {
              val key = trimmed.dropRight(1).trim
              stack = stack.dropRight(stack.reverse.takeWhile(_._1 >= indent).length) :+ (indent -> key)
            } else trimmed match {
              case _assignment(key, value) if value == "[" =>
                stack = stack.dropRight(stack.reverse.takeWhile(_._1 >= indent).length)
                val path = (stack.map(_._2) :+ key).mkString(".")
                if (candidates.contains(path))
                  collecting = Some((indent, path, Vector.empty, None, false, Vector.empty))
              case _ =>
            }
          }
      }
    }
    collecting.foreach {
      case (_, path, goals, current, _, _) =>
        result = result.updated(path, _flush_current_(path, goals, current))
    }
    result.map { case (k, v) => k -> v.filterNot(_.isEmpty) }.filter(_._2.nonEmpty)
  }

  private def _parse_site_block_lists(lines: Vector[String]): Map[String, Vector[String]] = {
    var stack = Vector.empty[(Int, String)]
    var lists = Map.empty[String, Vector[String]]
    var collecting: Option[(Int, String, Vector[String])] = None
    lines.foreach { raw =>
      val line = raw.takeWhile(_ != '#')
      val trimmed = line.trim
      collecting match {
        case Some((baseindent, path, items)) =>
          if (trimmed == "]") {
            lists = lists.updated(path, items)
            collecting = None
          } else if (trimmed.startsWith("\"") || trimmed.startsWith("'") || trimmed.endsWith(",")) {
            val item = _unquote(trimmed.stripSuffix(","))
            if (item.nonEmpty)
              collecting = Some((baseindent, path, items :+ item))
          } else if (trimmed.nonEmpty && line.takeWhile(_.isWhitespace).length <= baseindent) {
            lists = lists.updated(path, items)
            collecting = None
          }
        case None =>
          if (trimmed.nonEmpty && trimmed != "}") {
            val indent = line.takeWhile(_.isWhitespace).length
            if (trimmed.endsWith("{")) {
              val key = trimmed.dropRight(1).trim
              stack = stack.dropRight(stack.reverse.takeWhile(_._1 >= indent).length) :+ (indent -> key)
            } else trimmed match {
              case _assignment(key, value) if value == "[" =>
                stack = stack.dropRight(stack.reverse.takeWhile(_._1 >= indent).length)
                val path = (stack.map(_._2) :+ key).mkString(".")
                collecting = Some((indent, path, Vector.empty))
              case _ =>
            }
          }
      }
    }
    collecting.foreach { case (_, path, items) =>
      lists = lists.updated(path, items)
    }
    lists
  }

  private[bok] def _parse_inline_list(value: String): Vector[String] =
    value.stripPrefix("[").stripSuffix("]").split(",").toVector.map(x => _unquote(x.trim)).filter(_.nonEmpty)

  private[bok] def _unquote(value: String): String = {
    val s = value.trim
    if (s.length >= 2 && ((s.head == '"' && s.last == '"') || (s.head == '\'' && s.last == '\'')))
      s.substring(1, s.length - 1)
    else
      s
  }

}
