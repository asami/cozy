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

private[cozy] trait CozyBokBuildConfig {
  self: CozyBokImplementation.type =>
  final case class BuildConfig(
    project: Path,
    source: String,
    website: String,
    antora: String,
    doxsite: String,
    arcadiaSite: String,
    uiBundle: String,
    strategy: String,
    dockerImage: String,
    siteOutputScopePolicy: String,
    siteTitle: String,
    siteId: String,
    siteUrl: Option[String],
    localeMode: LocaleMode,
    defaultLocale: String,
    languages: Vector[String],
    arcadia: ArcadiaConfig,
    directAssets: DirectAssetsConfig,
    publication: PublicationSettings,
    dashboardColorGroup: String,
    bibliographyService: Boolean
  ) {
    def sourcepath: Path = project.resolve(source)
    def sourcePath: Path = sourcepath
    def websitePath: Path = project.resolve(website)
    def antoraPath: Path = project.resolve(antora)
    def doxsitePath: Path = project.resolve(doxsite)
    def arcadiaSitePath: Path = project.resolve(arcadiaSite)
    def uiBundlePath: Path = project.resolve(uiBundle)
  }

  final case class ArcadiaConfig(enabled: Boolean, source: String)
  final case class DirectAssetsConfig(enabled: Boolean, items: Vector[DirectAsset])
  final case class DirectAsset(source: String, destination: String)
  final case class PublicationSettings(
    path: String,
    warehouse: Option[String],
    repository: String,
    mergeRdf: Boolean,
    missingRdfPolicy: String
  ) {
    def publicationPath(project: Path): Path = project.resolve(path).toAbsolutePath.normalize()
    def warehousePath(project: Path): Option[Path] = warehouse.map(project.resolve(_).toAbsolutePath.normalize())
    def repositoryPath(project: Path): Path = project.resolve(repository).toAbsolutePath.normalize()
    def artifactBasePath(project: Path): Path = repositoryPath(project)
    def publicationRepositoryBasePath(project: Path): Path = repositoryPath(project)
  }
  final case class PublicationConfig(
    project: Path,
    source: String,
    publication: String,
    warehouse: Option[String],
    repository: String,
    version: Option[String],
    force: Boolean,
    videoEnabled: Boolean,
    dryRun: Boolean,
    strategy: String,
    mediaForce: Boolean
  ) {
    def sourcepath: Path = project.resolve(source).toAbsolutePath.normalize()
    def publicationPath: Path = project.resolve(publication).toAbsolutePath.normalize()
    def warehousePath: Option[Path] = warehouse.map(project.resolve(_).toAbsolutePath.normalize())
    def repositoryPath: Path = project.resolve(repository).toAbsolutePath.normalize()
    def artifactBasePath: Path = repositoryPath
    def manifestPath: Path = project.resolve("target/cozy-bok/publish/latest/manifest.json").toAbsolutePath.normalize()
  }
  final case class WorkflowConfig(
    project: Path,
    name: String,
    command: Vector[String],
    env: Map[String, String],
    backup: Option[WebsiteBackupConfig] = None
  )
  final case class WebsiteBackupConfig(source: Path, root: Path, compressed: Boolean)
  private[bok] final case class PublishStep(name: String, status: String, message: String)
  private[bok] final case class WebsiteBackupSnapshot(path: Path, yearmonth: YearMonth, order: String)
  private[bok] final case class BibliographyRdfAlias(aliastail: String, targettail: String, title: String)
  private[bok] final case class KnowledgeSourceResource(kind: String, href: String, mediatype: String) {
    def toJson: Json = Json.obj(
      "kind" -> Json.fromString(kind),
      "href" -> Json.fromString(href),
      "mediaType" -> Json.fromString(mediatype)
    )
  }
  private[bok] final case class PublishPreflight(
    stage: Option[WorkflowConfig],
    upload: WorkflowConfig,
    build: BuildConfig,
    videopackages: Vector[Path],
    projectpackages: Vector[Path],
    infographicpackages: Vector[CozyArticleMediaInfographicCommand.PlannedCandidate],
    videopreflight: CozyArticleMediaVideoCommand.Preflight,
    infographicplan: CozyArticleMediaInfographicCommand.Plan
  ) {
    def packageCount: Int = videopackages.size + infographicpackages.size + projectpackages.size
  }
  private[bok] final case class ParsedArgs(request: CliRequest) {
    def argument(name: String): Option[String] =
      request.arguments.find(_.name == name).map(_.asString).map(_.trim).filter(_.nonEmpty)

    def property(name: String): Option[String] =
      request.properties.find(_.name == name).map(_.asString).map(_.trim).filter(_.nonEmpty)

    def properties(name: String): Vector[String] =
      request.properties.filter(_.name == name).map(_.asString).map(_.trim).filter(_.nonEmpty).toVector

    def pathProperty(name: String): Option[Path] =
      request.properties.find(_.name == name).map(x => _to_path(x.value))

    def requiredPath(name: String, usage: String): Path =
      pathProperty(name).getOrElse(RAISE.invalidArgumentFault(s"Missing --${name} ${usage}"))

    def optionalInt(name: String): Option[Int] =
      request.properties.find(_.name == name).map { value =>
        value.value match {
          case m: Int => m
          case other => RAISE.invalidArgumentFault(s"Invalid --${name} <number>: ${other}")
        }
      }

    def validateNoUnrecognized(): Unit = {
      request.switches.filter(_.spec.isEmpty).foreach { x =>
        RAISE.invalidArgumentFault(s"Unknown option: --${x.name}")
      }
      request.arguments.filter(_.spec.isEmpty).foreach { x =>
        RAISE.invalidArgumentFault(s"Unknown argument: ${x.asString}")
      }
    }
  }

  private[bok] object BokArgs {
    private val _p_save = spec.Parameter.propertyFileOption("save")
    private val _p_project = spec.Parameter.argumentFile("project")
    private val _p_project_property = spec.Parameter.propertyFileOption("project")
    private val _p_project_dir_property = spec.Parameter.propertyFileOption("project-dir")

    private val _p_no_project_files = spec.Parameter("no-project-files", spec.Parameter.SwitchKind)
    private val _p_no_scaffold_files = spec.Parameter("no-scaffold-files", spec.Parameter.SwitchKind)
    private val _p_overwrite_project_files = spec.Parameter("overwrite-project-files", spec.Parameter.SwitchKind)
    private val _p_force_project_files = spec.Parameter("force-project-files", spec.Parameter.SwitchKind)

    private val _p_article = spec.Parameter(
      "article",
      spec.Parameter.PropertyKind,
      spec.XString,
      spec.Multiplicity.ZeroMore
    )
    private val _p_term = spec.Parameter(
      "term",
      spec.Parameter.PropertyKind,
      spec.XString,
      spec.Multiplicity.ZeroMore
    )
    private val _p_goal = spec.Parameter(
      "goal",
      spec.Parameter.PropertyKind,
      spec.XString,
      spec.Multiplicity.ZeroMore
    )
    private val _p_subgoal = spec.Parameter(
      "subgoal",
      spec.Parameter.PropertyKind,
      spec.XString,
      spec.Multiplicity.ZeroMore
    )

    private val _create_request = spec.Request(
      _p_save,
      spec.Parameter.property("name"),
      spec.Parameter.property("url"),
      spec.Parameter.property("language"),
      _p_no_project_files,
      _p_no_scaffold_files,
      _p_overwrite_project_files,
      _p_force_project_files
    )

    private val _category_request = spec.Request(
      spec.Parameter.argument("name"),
      _p_project_property,
      _p_project_dir_property,
      spec.Parameter.property("title"),
      spec.Parameter.property("description"),
      spec.Parameter.property("vision"),
      _p_goal,
      _p_subgoal,
      spec.Parameter.property("kind"),
      spec.Parameter.propertyInt("order"),
      _p_article,
      _p_term,
      _p_no_project_files,
      _p_no_scaffold_files,
      _p_overwrite_project_files,
      _p_force_project_files
    )

    private val _build_request = spec.Request(
      _p_project_property,
      _p_project_dir_property,
      spec.Parameter.property("strategy"),
      spec.Parameter.property("docker-image"),
      spec.Parameter.property("dashboard-color-group"),
      spec.Parameter.propertyFileOption("warehouse"),
      spec.Parameter.propertyFileOption("repository"),
      spec.Parameter.propertyFileOption("publication"),
      spec.Parameter.property("rdf-missing-artifact-policy"),
      spec.Parameter("no-bib-service", spec.Parameter.SwitchKind)
    )

    private val _publication_request = spec.Request(
      _p_project,
      spec.Parameter.propertyFileOption("warehouse"),
      spec.Parameter.propertyFileOption("repository"),
      spec.Parameter.propertyFileOption("publication"),
      spec.Parameter.property("version"),
      spec.Parameter.property("strategy"),
      spec.Parameter("force", spec.Parameter.SwitchKind),
      spec.Parameter("dry-run", spec.Parameter.SwitchKind)
    )

    private val _workflow_request = spec.Request(_p_project)
    private val _doctor_request = spec.Request(
      spec.Parameter("fix", spec.Parameter.SwitchKind),
      spec.Parameter("dry-run", spec.Parameter.SwitchKind)
    )

    def create(args: List[String]): ParsedArgs = _parse("bok-create", _create_request, args)
    def category(args: List[String]): ParsedArgs = _parse("bok-create-category", _category_request, args)
    def build(args: List[String]): ParsedArgs = _parse("bok-build", _build_request, _normalize_optional_project_argument(args))
    def publication(name: String, args: List[String]): ParsedArgs = _parse(s"bok-${name}", _publication_request, args)
    def workflow(name: String, args: List[String]): ParsedArgs = _parse(s"bok-${name}", _workflow_request, args)
    def doctor(args: List[String]): ParsedArgs = _parse("bok-doctor", _doctor_request, args)

    private def _parse(name: String, request: spec.Request, args: List[String]): ParsedArgs =
      ParsedArgs(request.build(CliRequest(name), args))

    private def _normalize_optional_project_argument(args: List[String]): List[String] =
      args match {
        case x :: xs if !x.startsWith("--") => "--project" :: x :: xs
        case _ => args
      }
  }
  final case class SiteConfig(
    values: Map[String, String],
    lists: Map[String, Vector[String]],
    goalTrees: Map[String, Vector[BokGoal]] = Map.empty
  ) {
    def value(path: String): Option[String] = values.get(path).map(_.trim).filter(_.nonEmpty)
    def boolean(path: String): Option[Boolean] =
      value(path).map(_.toLowerCase(java.util.Locale.ROOT)).collect {
        case "true" | "yes" | "on" => true
        case "false" | "no" | "off" => false
      }
    def list(path: String): Vector[String] = lists.getOrElse(path, Vector.empty)
    def goalTree(path: String): Vector[BokGoal] = goalTrees.getOrElse(path, Vector.empty)
  }
  object SiteConfig {
    lazy val empty: SiteConfig = new SiteConfig(Map.empty, Map.empty, Map.empty)
  }

  private[bok] val _default_preview_port = "8980"

  trait Runner {
    def run(command: Vector[String], cwd: Path): Unit
    def run(command: Vector[String], cwd: Path, env: Map[String, String]): Unit =
      run(command, cwd)
  }

  object ProcessRunner extends Runner {
    def run(command: Vector[String], cwd: Path): Unit = {
      val exit = Process(command, cwd.toFile).!
      if (exit != 0)
        RAISE.invalidArgumentFault(s"Command failed with exit code ${exit}: ${command.mkString(" ")}")
    }
    override def run(command: Vector[String], cwd: Path, env: Map[String, String]): Unit = {
      val exit = Process(command, cwd.toFile, env.toSeq: _*).!
      if (exit != 0)
        RAISE.invalidArgumentFault(s"Command failed with exit code ${exit}: ${command.mkString(" ")}")
    }
  }

  object BuildConfig {
    def create(args: List[String]): BuildConfig = {
      val parsed = BokArgs.build(args)
      parsed.validateNoUnrecognized()
      val strategy = _strategy(parsed)
      val project = _project(parsed)
      val config = _load_config(project)
      val source = config.value("bok.source").getOrElse("src/main/doxsite")
      val projectroot = _finalization_project_root(project)
      val admittedsource = _admit_build_config_source(project.resolve(source), projectroot)
      val admittedrelative = projectroot.relativize(admittedsource).toString
      val site = _load_site_config(admittedsource)
      val languages = _languages(config, site)
      val sitetitle = site.value("site.metadata.name").getOrElse("KnowledgeHub BoK")
      val siteid = site.value("site.metadata.id").
        orElse(site.value("site.metadata.key")).
        map(_site_identifier).
        filter(_.nonEmpty).
        getOrElse(_site_identifier(sitetitle))
      val siteurl = site.value("site.metadata.url").flatMap(_site_base_uri)
      val dockerimage =
        parsed.property("docker-image").
          orElse(config.value("bok.docker-image")).
          orElse(config.value("cozy.docker-image")).
          orElse(config.value("pdf.docker-image")).
          orElse(config.value("smartdox.pdf.docker-image")).
          orElse(config.value("cozy.pdf.docker-image")).
          getOrElse(_default_docker_image)
      BuildConfig(
        project,
        admittedrelative,
        config.value("bok.website").getOrElse("website.d"),
        config.value("bok.antora").getOrElse("antora.d"),
        config.value("bok.doxsite").getOrElse("doxsite.d"),
        config.value("bok.arcadia-site").getOrElse("arcadiasite.d"),
        config.value("bok.ui-bundle").getOrElse("src/main/antora-ui/build/ui-bundle.zip"),
        strategy,
        dockerimage,
        site.value("output.scope.policy").orElse(config.value("bok.output.scope.policy")).getOrElse("home_only"),
        sitetitle,
        siteid,
        siteurl,
        _locale_mode(config, site),
        _default_locale(config, site, languages),
        languages,
        ArcadiaConfig(_boolean(config, "bok.arcadia.enabled", false), config.value("bok.arcadia.source").getOrElse("src/main/arcadiasite")),
        _direct_assets(project, config),
        _publication_settings(parsed, config, strategy),
        _dashboard_color_group(parsed, config, site),
        !parsed.request.switches.exists(_.name == "no-bib-service")
      )
    }
  }

  private def _admit_build_config_source(source: Path, projectroot: Path): Path = {
    val normalized = source.toAbsolutePath.normalize()
    if (!normalized.startsWith(projectroot))
      RAISE.invalidArgumentFault(s"BoK configured source root must be inside the project root: $source")
    if (normalized != projectroot)
      _validate_build_config_source_parent(
        projectroot,
        Option(normalized.getParent).getOrElse(projectroot)
      )
    if (Files.exists(normalized, LinkOption.NOFOLLOW_LINKS) &&
        (Files.isSymbolicLink(normalized) || !Files.isDirectory(normalized, LinkOption.NOFOLLOW_LINKS)))
      RAISE.invalidArgumentFault(
        s"BoK configured source root must be an existing non-symbolic-link directory inside the project root: $source"
      )
    val canonicalprojectroot = projectroot.toRealPath()
    val canonical = _nearest_existing_build_config_path(normalized).toRealPath()
    if (!canonical.startsWith(canonicalprojectroot))
      RAISE.invalidArgumentFault(s"BoK configured source root must resolve below the project root: $source")
    normalized
  }

  private def _validate_build_config_source_parent(root: Path, parent: Path): Unit = {
    if (!parent.startsWith(root))
      RAISE.invalidArgumentFault(s"BoK configured source root must be inside the project root: $parent")
    var current = root
    root.relativize(parent).iterator.asScala.foreach { segment =>
      val next = current.resolve(segment.toString)
      if (Files.exists(next, LinkOption.NOFOLLOW_LINKS) &&
          (Files.isSymbolicLink(next) || !Files.isDirectory(next, LinkOption.NOFOLLOW_LINKS)))
        RAISE.invalidArgumentFault(s"BoK configured source root is unsafe: $next")
      current = next
    }
  }

  private def _nearest_existing_build_config_path(path: Path): Path = {
    var current = path
    while (!Files.exists(current, LinkOption.NOFOLLOW_LINKS))
      current = Option(current.getParent).getOrElse(current)
    current
  }

  private[bok] def _site_identifier(value: String): String =
    value.trim.toLowerCase(Locale.ROOT).
      replaceAll("[^\\p{L}\\p{N}]+", "-").
      stripPrefix("-").
      stripSuffix("-") match {
        case "" => "bok-site"
        case x => x
      }

  private def _site_base_uri(value: String): Option[String] =
    Option(value).map(_.trim).filter(_.nonEmpty).map { x =>
      if (x.endsWith("/")) x else x + "/"
    }

  object PublicationConfig {
    def apply(
      project: Path,
      source: String,
      publication: String,
      warehouse: Option[String],
      repository: String,
      version: Option[String],
      force: Boolean,
      videoEnabled: Boolean,
      dryRun: Boolean,
      strategy: String
    ): PublicationConfig =
      new PublicationConfig(project, source, publication, warehouse, repository, version, force, videoEnabled, dryRun, strategy, force)

    def create(name: String, args: List[String]): PublicationConfig = {
      val parsed = BokArgs.publication(name, args)
      val project = _project(parsed)
      parsed.validateNoUnrecognized()
      if (name == "publish-media" && parsed.pathProperty("warehouse").nonEmpty)
        RAISE.invalidArgumentFault("--warehouse is not supported by bok publish-media")
      val config = _load_config(project)
      val strategy = _strategy(parsed, config.value("bok.strategy").getOrElse("production"))
      val publication = parsed.pathProperty("publication").
        map(_.toString).
        orElse(config.value("bok.publication")).
        getOrElse("src/main/publication")
      val (warehouse, repository) = _publication_artifact_roots(parsed, config)
      val explicitforce = parsed.request.switches.exists(_.name == "force")
      val force = explicitforce ||
        (name != "publish-media" && _boolean(config, "bok.video.force", false))
      val videoenabled = _boolean(config, "bok.video.enabled", true)
      val dryrun = parsed.request.switches.exists(_.name == "dry-run")
      if (dryrun && name != "publish")
        RAISE.invalidArgumentFault(s"--dry-run is only supported by bok publish: ${name}")
      PublicationConfig(
        project,
        config.value("bok.source").getOrElse("src/main/doxsite"),
        publication,
        warehouse,
        repository,
        parsed.property("version"),
        force,
        videoenabled,
        dryrun,
        strategy,
        explicitforce
      )
    }
  }

  private def _workflow_command(config: CozyProjectYamlConfig.Config, name: String): Vector[String] =
    config.value(s"bok.workflow.${name}.command").map(x => Vector("sh", "-c", x)).getOrElse(Vector.empty)

  private def _workflow_env(config: CozyProjectYamlConfig.Config, name: String): Map[String, String] = {
    val defaults = Map(
      "WEBSITE_SOURCE_DIR" -> config.value("bok.website").getOrElse("website.d"),
      "REPOSITORY_SOURCE_DIR" -> config.value("bok.repository").
        orElse(config.value("bok.warehouse").map(_repository_under_warehouse)).
        getOrElse("repository")
    ) ++ (if (name == "stage") config.value("bok.website-staging").map("WEBSITE_STAGING_DIR" -> _).toMap else Map.empty)
    defaults ++ config.mapUnder(s"bok.workflow.${name}.env")
  }

  object WorkflowConfig {
    def create(name: String, args: List[String]): WorkflowConfig = {
      val parsed = BokArgs.workflow(name, args)
      val project = _project(parsed)
      parsed.validateNoUnrecognized()
      val config = _load_config(project)
      val env = _workflow_env(config, name)
      WorkflowConfig(
        project,
        name,
        _workflow_command(config, name),
        env,
        _workflow_backup_config(project, config, name, env)
      )
    }
  }

  private def _workflow_backup_config(
    project: Path,
    config: CozyProjectYamlConfig.Config,
    name: String,
    env: Map[String, String]
  ): Option[WebsiteBackupConfig] =
    if (name == "upload" && _boolean(config, "bok.backup.enabled", false)) {
      val source = project.resolve(env.getOrElse("WEBSITE_SOURCE_DIR", config.value("bok.website").getOrElse("website.d"))).toAbsolutePath.normalize()
      val root = project.resolve(
        config.value("bok.backup.dir").
          orElse(config.value("bok.backup.path")).
          getOrElse("website.backup")
      ).toAbsolutePath.normalize()
      Some(WebsiteBackupConfig(source, root, _boolean(config, "bok.backup.compressed", true)))
    } else {
      None
    }
}
