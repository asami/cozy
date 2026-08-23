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
 * @version Aug. 23, 2026
 * @author  ASAMI, Tomoharu
 */

private[cozy] trait CozyBokCommand {
  self: CozyBokImplementation.type =>
  def execute(args: List[String]): Boolean =
    args match {
      case "bok" :: "create" :: rest =>
        create(CreateConfig.create(rest))
        true
      case "bok" :: "create-category" :: rest =>
        createCategory(CategoryConfig.create(rest))
        true
      case "bok" :: "build" :: rest =>
        build(BuildConfig.create(rest), ProcessRunner)
        true
      case "bok" :: "finalize-metadata" :: rest if _help_requested(rest) =>
        _print_finalize_metadata_usage()
        true
      case "bok" :: "finalize-metadata" :: rest =>
        finalizeMetadata(BuildConfig.create(rest))
        true
      case "bok" :: "update" :: rest =>
        build(BuildConfig.create(rest), ProcessRunner)
        true
      case "bok" :: "doctor" :: rest =>
        doctor(DoctorConfig.create(rest, fix = false))
        true
      case "bok" :: "fix" :: rest =>
        doctor(DoctorConfig.create(rest, fix = true))
        true
      case "bok" :: "guide" :: rest =>
        guide(rest)
        true
      case "bok" :: "tutorial" :: rest =>
        guide(rest)
        true
      case "bok" :: "search-bibliography" :: rest if _help_requested(rest) =>
        _print_bibliography_search_usage()
        true
      case "bok" :: "search-bibliography" :: rest =>
        println(searchBibliography(_search_bibliography_config(rest), BibliographySearchRegistry.default))
        true
      case "bok" :: "update-bibliography" :: rest if _help_requested(rest) =>
        _print_bibliography_update_usage()
        true
      case "bok" :: "update-bibliography" :: rest =>
        val config = _update_bibliography_config(rest)
        println(updateBibliography(config, _bibliography_fetcher(config.project, BibliographyHttpBibtexFetcher)))
        true
      case "bok" :: "publish-video" :: rest if _help_requested(rest) =>
        _print_publication_usage("publish-video")
        true
      case "bok" :: "publish-video" :: rest =>
        publishVideo(PublicationConfig.create("publish-video", rest), CozyVideo.VoicevoxClient.default, CozyVideo.VideoProcessRunner.default)
        true
      case "bok" :: "publish-media" :: rest if _help_requested(rest) =>
        _print_publication_usage("publish-media")
        true
      case "bok" :: "publish-media" :: rest =>
        publishMedia(PublicationConfig.create("publish-media", rest))
        true
      case "bok" :: "publish-projects" :: rest if _help_requested(rest) =>
        _print_publication_usage("publish-projects")
        true
      case "bok" :: "publish-projects" :: rest =>
        publishProjects(PublicationConfig.create("publish-projects", rest))
        true
      case "bok" :: "update-publication" :: rest if _help_requested(rest) =>
        _print_publication_usage("update-publication")
        true
      case "bok" :: "update-publication" :: rest =>
        updatePublication(PublicationConfig.create("update-publication", rest), CozyVideo.VoicevoxClient.default, CozyVideo.VideoProcessRunner.default)
        true
      case "bok" :: "publish" :: rest if _help_requested(rest) =>
        _print_publication_usage("publish")
        true
      case "bok" :: "publish" :: rest =>
        publish(PublicationConfig.create("publish", rest), ProcessRunner, CozyVideo.VoicevoxClient.default, CozyVideo.VideoProcessRunner.default)
        true
      case "bok" :: "preview" :: rest =>
        preview(rest, ProcessRunner)
        true
      case "bok" :: "stage" :: rest if _help_requested(rest) =>
        _print_workflow_usage("stage")
        true
      case "bok" :: "stage" :: rest =>
        runWorkflow(WorkflowConfig.create("stage", rest), ProcessRunner)
        true
      case "bok" :: "upload" :: rest if _help_requested(rest) =>
        _print_workflow_usage("upload")
        true
      case "bok" :: "upload" :: rest =>
        runWorkflow(WorkflowConfig.create("upload", rest), ProcessRunner)
        true
      case "bok" :: other :: _ =>
        RAISE.invalidArgumentFault(s"Unsupported bok command: ${other}")
      case _ =>
        false
    }

  private def _help_requested(args: List[String]): Boolean =
    args.exists(x => x == "--help" || x == "-h")

  private def _print_publication_usage(name: String): Unit = {
    name match {
      case "publish" =>
        println("Usage: cozy bok publish <project-dir> [--publication <dir>] [--repository <dir>] [--warehouse <dir>] [--version <version>] [--strategy production] [--force] [--dry-run]")
        println("Run update-publication, build, optional stage, and configured upload workflow. Use --dry-run to print the plan without publication, repository, site, or upload side effects.")
      case "publish-video" =>
        println("Usage: cozy bok publish-video <project-dir> [--publication <dir>] [--repository <dir>] [--warehouse <dir>] [--version <version>] [--force]")
        println("Publish .video packages into the BoK publication registry and artifact repository.")
      case "publish-media" =>
        println("Usage: cozy bok publish-media <project-dir> [--publication <dir>] [--repository <dir>] [--version <version>] [--force]")
        println("Publish explicit detailed-infographic media packages into the BoK article-media registry and artifact repository.")
      case "publish-projects" =>
        println("Usage: cozy bok publish-projects <project-dir> [--publication <dir>] [--repository <dir>] [--warehouse <dir>] [--version <version>] [--force]")
        println("Register src/main/doxsite/projects/<category>/<slug> project knowledge packages into the BoK publication registry. CAR artifact publishing remains the responsibility of cozy publish-car.")
      case "update-publication" =>
        println("Usage: cozy bok update-publication <project-dir> [--publication <dir>] [--repository <dir>] [--warehouse <dir>] [--version <version>] [--force]")
        println("Update the BoK publication registry for .video, detailed-infographic media, and project knowledge packages.")
      case other =>
        RAISE.invalidArgumentFault(s"Unknown publication command: ${other}")
    }
  }

  private def _print_workflow_usage(name: String): Unit = {
    println(s"Usage: cozy bok ${name} [<project-dir>]")
    println(s"Run the external command registered at bok.workflow.${name}.command.")
    if (name == "upload")
      println("When bok.backup.enabled is true, website.d is backed up before upload. Defaults: enabled=false, dir=website.backup, compressed=true.")
  }

  private def _print_bibliography_search_usage(): Unit = {
    println("Usage: cozy bok search-bibliography <query> [--provider crossref|openlibrary|dblp|all] [--limit <n>] [--format text|json]")
    println("Search external bibliography/reference providers. This command does not modify the BoK source tree.")
  }

  private def _print_bibliography_update_usage(): Unit = {
    println("Usage: cozy bok update-bibliography [<project-dir>] [--force] [--report-only|--no-fetch]")
    println("Fetch explicit BibTeX/cache sources registered in bibliography metadata into target/cozy-bok/bibliography/cache.")
    println("Local .bib files under repository/bibliography, repository/catalog/bibliography, or src/main/doxsite/bibliography are used before external providers.")
    println("With --report-only or --no-fetch, report missing bibliography cache entries without external fetches.")
  }

  private def _print_finalize_metadata_usage(): Unit = {
    println("Usage: cozy bok finalize-metadata [<project-dir>] [--strategy wip|draft|preview|production]")
    println("Finalize generated BoK machine metadata into the configured website without running SmartDox, Antora, Arcadia, media, publication, upload, deployment, or project workflows.")
    println("Requires existing configured doxsite.d and website.d roots; updates only the RDF, metadata, component-reference, SIE, and KnowledgeSource allowlist.")
  }

  private def _search_bibliography_config(args: List[String]): BibliographySearchConfig = {
    val (options, values) = _parse_bibliography_options(args)
    val query = values.mkString(" ").trim
    if (query.isEmpty)
      RAISE.invalidArgumentFault("Usage: cozy bok search-bibliography <query> [--provider crossref|openlibrary|dblp|all] [--limit <n>] [--format text|json]")
    val provider = options.getOrElse("provider", "all").toLowerCase(java.util.Locale.ROOT)
    if (!Set("crossref", "openlibrary", "dblp", "all").contains(provider))
      RAISE.invalidArgumentFault(s"Unsupported bibliography provider: ${provider}")
    val limit = options.get("limit").map(x => try x.toInt catch { case NonFatal(_) => RAISE.invalidArgumentFault(s"Invalid --limit <n>: ${x}") }).getOrElse(10)
    val format = options.getOrElse("format", "text").toLowerCase(java.util.Locale.ROOT)
    BibliographySearchConfig(query, provider, limit, format)
  }

  private def _update_bibliography_config(args: List[String]): BibliographyUpdateConfig = {
    val normalized = args match {
      case x :: xs if !x.startsWith("--") => "--project" :: x :: xs
      case _ => args
    }
    val (options, values) = _parse_bibliography_options(normalized)
    if (values.nonEmpty)
      RAISE.invalidArgumentFault(s"Unknown bibliography update argument: ${values.head}")
    BibliographyUpdateConfig(
      options.get("project").map(Paths.get(_)).getOrElse(Paths.get(".")),
      options.contains("force"),
      options.contains("report-only") || options.contains("no-fetch")
    )
  }

  private def _parse_bibliography_options(args: List[String]): (Map[String, String], List[String]) = {
    def _loop_(rest: List[String], options: Map[String, String], values: List[String]): (Map[String, String], List[String]) =
      rest match {
        case Nil => (options, values.reverse)
        case arg :: tail if arg.startsWith("--") && arg.contains("=") =>
          val Array(key, value) = arg.drop(2).split("=", 2)
          _loop_(tail, options + (key -> value), values)
        case "--force" :: tail => _loop_(tail, options + ("force" -> "true"), values)
        case arg :: value :: tail if arg.startsWith("--") =>
          _loop_(tail, options + (arg.drop(2) -> value), values)
        case arg :: _ if arg.startsWith("--") =>
          RAISE.invalidArgumentFault(s"Missing value for ${arg}")
        case value :: tail => _loop_(tail, options, value :: values)
      }
    _loop_(args, Map.empty, Nil)
  }

  def create(config: CreateConfig): Unit = {
    val sitedir = config.save.resolve("src/main/doxsite")
    _write(config.save.resolve("conf/cozy/config.yaml"), _cozy_config(Some(config)), config.policy)
    _write(config.save.resolve("README.md"), _readme(config), config.policy)
    _write(config.save.resolve("STRUCTURE.md"), _structure(config), config.policy)
    _write(sitedir.resolve("site.conf"), _site_conf(config), config.policy)
    _write(sitedir.resolve("index.dox"), _site_index(config), config.policy)
    _write(sitedir.resolve("glossary/category.yaml"), _category("Glossary", "用語集", "BoK全体で共有する用語集。"), config.policy)
    _write(sitedir.resolve("history/category.yaml"), _category("History", "History", "BoK運用と更新履歴。"), config.policy)
    _write(sitedir.resolve("history/index.dox"), _history_index(), config.policy)
    _write(sitedir.resolve("manual/local-rules.dox"), _manual_local_rules(config), config.policy)
    _write(sitedir.resolve("rdf/site.ttl"), _site_ttl(config), config.policy)
    _write(sitedir.resolve("rdf/site.jsonld"), _site_jsonld(config), config.policy)
    _write(sitedir.resolve("rdf/schema/knowledgehub.ttl"), _schema_ttl(config), config.policy)
    _write(sitedir.resolve("rdf/schema/knowledgehub.jsonld"), _schema_jsonld(), config.policy)
    _write(sitedir.resolve("rdf/ontology/knowledgehub.ttl"), _ontology_ttl(config), config.policy)
    _write(sitedir.resolve("rdf/ontology/knowledgehub.jsonld"), _ontology_jsonld(), config.policy)
    _write(sitedir.resolve("assets/css/knowledgehub.css"), _css(), config.policy)
    _write(config.save.resolve("etc/website-stage.sh.proto"), _website_stage_script(config), config.policy)
    _write(config.save.resolve("etc/website-upload.sh.proto"), _website_upload_script(config), config.policy)
    _write_default_ui_bundle(config.save.resolve("src/main/antora-ui/build/ui-bundle.zip"), config.policy)
  }

  def doctor(config: DoctorConfig): Unit = {
    val inspection = _inspect_bok(config.input)
    _print_bok_inspection(inspection, config)
    if (config.fix)
      _apply_bok_fixes(inspection, config)
  }

  def guide(args: List[String]): Unit = {
    val scenario = args match {
      case Nil => "overview"
      case name :: Nil => name
      case _ => RAISE.invalidArgumentFault(s"Usage: cozy bok guide [scenario]")
    }
    val normalized = scenario.toLowerCase(java.util.Locale.ROOT)
    normalized match {
      case "overview" | "list" =>
        println("Cozy BoK guide")
        println("Available scenarios:")
        _bok_guide_scenarios.foreach { case (name, title, _) =>
          println(s"  - ${name}: ${title}")
        }
        println()
        println("Run: cozy bok guide <scenario>")
      case "all" =>
        _bok_guide_scenarios.foreach { case (name, title, lines) =>
          _print_bok_guide_scenario(name, title, lines)
          println()
        }
      case name =>
        _bok_guide_scenarios.find(_._1 == name) match {
          case Some((n, title, lines)) => _print_bok_guide_scenario(n, title, lines)
          case None => RAISE.invalidArgumentFault(s"Unknown BoK guide scenario: ${scenario}")
        }
    }
  }

  def searchBibliography(config: BibliographySearchConfig, registry: BibliographySearchRegistry): String = {
    val results = registry.search(config)
    config.format match {
      case "json" => _bibliography_search_json(results)
      case "text" =>
        if (results.isEmpty)
          s"No bibliography candidates found for: ${config.query}"
        else
          results.map(_.text).mkString("\n\n")
      case other => RAISE.invalidArgumentFault(s"Unsupported bibliography search format: ${other}")
    }
  }

  def updateBibliography(config: BibliographyUpdateConfig, fetcher: BibliographyBibtexFetcher): String = {
    val metadata = config.project.resolve("doxsite.d/metadata/bibliography/bibliography.json")
    if (!Files.isRegularFile(metadata))
      s"No bibliography metadata found: ${metadata}. Run cozy bok build first."
    else {
      val index = parser.parse(Files.readString(metadata, StandardCharsets.UTF_8)).toOption.flatMap(_.as[BibliographyIndex].toOption).getOrElse(BibliographyIndex(Vector.empty))
      val cache = config.project.resolve("target/cozy-bok/bibliography/cache")
      var written = Vector.empty[Path]
      var missing = Vector.empty[String]
      index.entries.foreach { entry =>
        val path = cache.resolve(s"${_safe_file_name(entry.id)}.bib")
        if (config.force || !Files.exists(path)) {
          entry.bibtex.raw match {
            case Some(raw) =>
              if (!config.reportonly) {
                _write_text(path, raw)
                written :+= path
              }
            case None if config.reportonly =>
              if (entry.bibtex.sourceurl.nonEmpty || entry.needsresolution)
                missing :+= entry.id
            case None =>
              val body = fetcher.fetchEntry(entry)
              body match {
                case Some(value) =>
                  _write_text(path, value)
                  written :+= path
                case None =>
                  if (entry.bibtex.sourceurl.nonEmpty || entry.needsresolution)
                    missing :+= entry.id
              }
          }
        }
      }
      if (config.reportonly && missing.nonEmpty)
        missing.distinct.map(x => s"bibliography cache missing: ${x}").mkString("\n")
      else if (config.reportonly)
        s"bibliography cache: complete (${cache})"
      else if (missing.nonEmpty)
        missing.distinct.map(x => s"bibliography cache unresolved: ${x}").mkString("\n")
      else if (written.isEmpty)
        s"bibliography cache: no updates (${cache})"
      else
        written.map(path => s"bibliography cache: ${config.project.toAbsolutePath.normalize.relativize(path.toAbsolutePath.normalize)}").mkString("\n")
    }
  }


}
