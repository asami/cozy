package cozy.bok

import org.goldenport.RAISE
import org.goldenport.cli.{Request => CliRequest}
import org.goldenport.cli.spec
import cozy.config.CozyProjectYamlConfig
import cozy.video.{CozyVideo, CozyVideoPublisher}
import org.smartdox.Dox
import org.smartdox.parser.Dox2Parser
import org.smartdox.transformers.Dox2HtmlTransformer
import org.smartdox.transformers.LanguageFilterTransformer
import org.smartdox.generator.{Context => SmartDoxContext}
import org.goldenport.i18n.I18NContext
import java.net.URLEncoder
import java.time.LocalDate
import java.util.Locale
import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path, Paths, StandardCopyOption}
import java.util.zip.{ZipEntry, ZipInputStream, ZipOutputStream}
import scala.collection.JavaConverters._
import scala.util.matching.Regex
import scala.sys.process._
import io.circe.{Decoder, HCursor, Json}
import io.circe.parser

/*
 * @since   Jun.  3, 2026
 * @version Jun. 22, 2026
 * @author  ASAMI, Tomoharu
 */
private[cozy] object CozyBok {
  private val _default_docker_image = "ghcr.io/asami/cozy-toolchain:latest"
  private val _ui_resource_base = "cozy.bok.BokUi"
  private val _ui_resource_config = I18NContext.ResourceBundleConfig.englishFallback

  sealed trait LocaleMode
  object LocaleMode {
    case object MultiLocaleSubdirs extends LocaleMode
    case object SingleLocaleRoot extends LocaleMode

    def create(value: String): LocaleMode =
      value match {
        case "multi_locale_subdirs" | "multi-locale-subdirs" => MultiLocaleSubdirs
        case "single_locale_root" | "single-locale-root" => SingleLocaleRoot
        case other => RAISE.invalidArgumentFault(s"Unsupported bok locale mode: ${other}")
      }
  }

  final case class CreateConfig(
    save: Path,
    name: String,
    url: String,
    language: String,
    policy: ProjectFilePolicy
  )
  final case class CategoryConfig(
    project: Path,
    name: String,
    title: String,
    description: String,
    purpose: BokPurpose,
    articles: Vector[CategoryArticle],
    terms: Vector[CategoryTerm],
    policy: ProjectFilePolicy
  )
  final case class BokGoal(title: String, subgoals: Vector[String]) {
    def isEmpty: Boolean = title.trim.isEmpty && subgoals.isEmpty
  }
  final case class BokPurpose(
    vision: Option[String],
    goals: Vector[BokGoal],
    flatGoals: Vector[String] = Vector.empty,
    flatSubgoals: Vector[String] = Vector.empty
  ) {
    def isEmpty: Boolean = vision.isEmpty && goals.isEmpty && flatGoals.isEmpty && flatSubgoals.isEmpty
  }
  object BokPurpose {
    val empty: BokPurpose = BokPurpose(None, Vector.empty)
  }
  final case class CategoryArticle(slug: String, title: String, purpose: String) {
    def fileName: String = s"${slug}.dox"
    def htmlName: String = s"${slug}.html"
  }
  final case class CategoryTerm(path: String, title: String, definition: String, reading: Option[String] = None) {
    def termPath: String = path.stripPrefix("glossary/").stripPrefix("/")
    def fileName: String = s"${termPath}.dox"
    def htmlName(category: String): String = s"../glossary/${category}/${termPath}.html"
  }
  final case class DoctorConfig(input: Path, fix: Boolean, dryRun: Boolean)
  final case class PreviewConfig(input: Path, port: Option[Int])
  private final case class BokInspection(
    input: Path,
    root: Option[Path],
    markers: Vector[String],
    issues: Vector[String],
    fixes: Vector[BokFix]
  ) {
    def status: String =
      if (root.isEmpty)
        "not-found"
      else if (issues.isEmpty)
        "ok"
      else
        "needs-fix"
  }
  private final case class BokFix(description: String, apply: () => Unit)
  private final case class CategoryContent(
    slug: String,
    title: String,
    description: String,
    purpose: BokPurpose,
    articles: Vector[CategoryPageItem],
    terms: Vector[CategoryPageItem]
  )
  private final case class CategoryPageItem(
    href: String,
    title: String,
    brief: String,
    modifiedAtMillis: Long,
    reading: Option[String] = None
  )
  private final case class DashboardCounts(
    categoryCount: Int,
    articleCount: Int,
    glossaryTermCount: Int,
    totalItemCount: Int
  )
  private final case class DashboardBucket(
    label: String,
    startDate: String,
    endDate: String,
    count: Int,
    articleCount: Int,
    glossaryTermCount: Int,
    hasBreakdown: Boolean
  )
  private final case class DashboardRdfSummary(
    resourceCount: Int,
    tripleCount: Int,
    subjectCount: Int,
    predicateCount: Int
  )
  private final case class DashboardIncrements(
    scale: String,
    buckets: Vector[DashboardBucket]
  )
  private final case class DashboardCategory(
    name: String,
    title: String,
    counts: DashboardCounts,
    increments: DashboardIncrements,
    rdf: Option[DashboardRdfSummary]
  )
  private final case class BokDashboard(
    counts: DashboardCounts,
    rdf: DashboardRdfSummary,
    increments: DashboardIncrements,
    categories: Vector[DashboardCategory]
  )

  private final case class TermIndex(terms: Vector[TermEntry])
  private final case class TermEntry(
    id: String,
    slug: String,
    title: String,
    reading: Option[String],
    category: Option[String],
    sourcePath: String,
    publicPath: String,
    definitionHtml: String,
    summary: Option[String],
    aliases: Vector[String],
    articleRefs: Vector[TermReference],
    termRefs: Vector[TermReference],
    rdfRefs: Vector[TermRdfReference],
    videoRefs: Vector[TermReference],
    quality: TermQuality
  ) {
    def categorySlug: String = category.getOrElse("glossary")
    def glossaryHref: String = publicPath.stripPrefix("glossary/")
    def termHubHrefFromHome: String = publicPath
    def termHubHrefFromCategory: String = "../" + publicPath
    def rdfHrefFromHome: String = s"rdf/index.html?term=${_url_query_escape(id)}"
    def rdfHrefFromGlossary: String = s"../rdf/index.html?term=${_url_query_escape(id)}"
    def rdfHrefFromCategory: String = s"../rdf/index.html?term=${_url_query_escape(id)}"
    def rdfHrefFromTerm: String = s"../../rdf/index.html?term=${_url_query_escape(id)}"
  }
  private final case class TermReference(title: String, path: String, relation: String)
  private final case class TermRdfReference(resource: String, label: String, predicate: Option[String], direction: String)
  private final case class TermQuality(isolated: Boolean, unreferenced: Boolean, weaklyconnected: Boolean)

  private implicit val _dashboard_counts_decoder: Decoder[DashboardCounts] = (c: HCursor) =>
    for {
      categorycount <- c.downField("category_count").as[Int]
      articlecount <- c.downField("article_count").as[Int]
      glossarytermcount <- c.downField("glossary_term_count").as[Int]
      totalitemcount <- c.downField("total_item_count").as[Int]
    } yield DashboardCounts(categorycount, articlecount, glossarytermcount, totalitemcount)

  private implicit val _dashboard_bucket_decoder: Decoder[DashboardBucket] = (c: HCursor) =>
    for {
      label <- c.downField("label").as[String]
      startdate <- c.downField("start_date").as[String]
      enddate <- c.downField("end_date").as[String]
      count <- c.downField("count").as[Int]
      articlecount <- c.downField("article_count").as[Option[Int]]
      glossarytermcount <- c.downField("glossary_term_count").as[Option[Int]]
    } yield DashboardBucket(
      label,
      startdate,
      enddate,
      count,
      articlecount.getOrElse(0),
      glossarytermcount.getOrElse(0),
      articlecount.isDefined || glossarytermcount.isDefined
    )

  private implicit val _dashboard_rdf_summary_decoder: Decoder[DashboardRdfSummary] = (c: HCursor) =>
    for {
      resourcecount <- c.downField("resource_count").as[Int]
      triplecount <- c.downField("triple_count").as[Int]
      subjectcount <- c.downField("subject_count").as[Int]
      predicatecount <- c.downField("predicate_count").as[Int]
    } yield DashboardRdfSummary(resourcecount, triplecount, subjectcount, predicatecount)

  private implicit val _dashboard_increments_decoder: Decoder[DashboardIncrements] = (c: HCursor) =>
    for {
      scale <- c.downField("scale").as[String]
      buckets <- c.downField("buckets").as[Vector[DashboardBucket]]
    } yield DashboardIncrements(scale, buckets)

  private implicit val _dashboard_category_decoder: Decoder[DashboardCategory] = (c: HCursor) =>
    for {
      name <- c.downField("name").as[String]
      title <- c.downField("title").as[String]
      counts <- c.downField("counts").as[DashboardCounts]
      increments <- c.downField("increments").as[DashboardIncrements]
      rdf <- c.downField("rdf").as[Option[DashboardRdfSummary]]
    } yield DashboardCategory(name, title, counts, increments, rdf)

  private implicit val _bok_dashboard_decoder: Decoder[BokDashboard] = (c: HCursor) =>
    for {
      counts <- c.downField("counts").as[DashboardCounts]
      rdf <- c.downField("rdf").as[DashboardRdfSummary]
      increments <- c.downField("increments").as[DashboardIncrements]
      categories <- c.downField("categories").as[Vector[DashboardCategory]]
    } yield BokDashboard(counts, rdf, increments, categories)

  private implicit val _term_reference_decoder: Decoder[TermReference] = (c: HCursor) =>
    for {
      title <- c.downField("title").as[String]
      path <- c.downField("path").as[String]
      relation <- c.downField("relation").as[Option[String]]
    } yield TermReference(title, path, relation.getOrElse("related"))

  private implicit val _term_rdf_reference_decoder: Decoder[TermRdfReference] = (c: HCursor) =>
    for {
      resource <- c.downField("resource").as[String]
      label <- c.downField("label").as[String]
      predicate <- c.downField("predicate").as[Option[String]]
      direction <- c.downField("direction").as[Option[String]]
    } yield TermRdfReference(resource, label, predicate, direction.getOrElse("node"))

  private implicit val _term_quality_decoder: Decoder[TermQuality] = (c: HCursor) =>
    for {
      isolated <- c.downField("isolated").as[Option[Boolean]]
      unreferenced <- c.downField("unreferenced").as[Option[Boolean]]
      weaklyconnected <- c.downField("weakly_connected").as[Option[Boolean]]
    } yield TermQuality(isolated.getOrElse(false), unreferenced.getOrElse(false), weaklyconnected.getOrElse(false))

  private implicit val _term_entry_decoder: Decoder[TermEntry] = (c: HCursor) =>
    for {
      id <- c.downField("id").as[String]
      slug <- c.downField("slug").as[String]
      title <- c.downField("title").as[String]
      reading <- c.downField("reading").as[Option[String]]
      category <- c.downField("category").as[Option[String]]
      sourcepath <- c.downField("source_path").as[String]
      publicpath <- c.downField("public_path").as[String]
      definitionhtml <- c.downField("definition_html").as[String]
      summary <- c.downField("summary").as[Option[String]]
      aliases <- c.downField("aliases").as[Option[Vector[String]]]
      articlerefs <- c.downField("article_refs").as[Option[Vector[TermReference]]]
      termrefs <- c.downField("term_refs").as[Option[Vector[TermReference]]]
      rdfrefs <- c.downField("rdf_refs").as[Option[Vector[TermRdfReference]]]
      videorefs <- c.downField("video_refs").as[Option[Vector[TermReference]]]
      quality <- c.downField("quality").as[Option[TermQuality]]
    } yield TermEntry(id, slug, title, reading, category, sourcepath, publicpath, definitionhtml, summary, aliases.getOrElse(Vector.empty), articlerefs.getOrElse(Vector.empty), termrefs.getOrElse(Vector.empty), rdfrefs.getOrElse(Vector.empty), videorefs.getOrElse(Vector.empty), quality.getOrElse(TermQuality(false, false, false)))

  private implicit val _term_index_decoder: Decoder[TermIndex] = (c: HCursor) =>
    for {
      terms <- c.downField("terms").as[Option[Vector[TermEntry]]]
    } yield TermIndex(terms.getOrElse(Vector.empty))

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
    localeMode: LocaleMode,
    defaultLocale: String,
    languages: Vector[String],
    arcadia: ArcadiaConfig,
    directAssets: DirectAssetsConfig,
    publication: PublicationSettings,
    dashboardColorGroup: String
  ) {
    def sourcePath: Path = project.resolve(source)
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
    warehouse: String,
    mergeRdf: Boolean,
    missingRdfPolicy: String
  ) {
    def publicationPath(project: Path): Path = project.resolve(path).toAbsolutePath.normalize()
    def warehousePath(project: Path): Path = project.resolve(warehouse).toAbsolutePath.normalize()
  }
  final case class PublicationConfig(
    project: Path,
    source: String,
    publication: String,
    warehouse: String,
    version: Option[String],
    force: Boolean,
    videoEnabled: Boolean,
    dryRun: Boolean,
    strategy: String
  ) {
    def sourcePath: Path = project.resolve(source).toAbsolutePath.normalize()
    def publicationPath: Path = project.resolve(publication).toAbsolutePath.normalize()
    def warehousePath: Path = project.resolve(warehouse).toAbsolutePath.normalize()
    def manifestPath: Path = project.resolve("target/cozy-bok/publish/latest/manifest.json").toAbsolutePath.normalize()
  }
  final case class WorkflowConfig(project: Path, name: String, command: Vector[String], env: Map[String, String])
  private final case class PublishStep(name: String, status: String, message: String)
  private final case class PublishPreflight(
    stage: Option[WorkflowConfig],
    upload: WorkflowConfig,
    build: BuildConfig,
    packages: Vector[Path]
  )
  private final case class ParsedArgs(request: CliRequest) {
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

  private object BokArgs {
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
      spec.Parameter.propertyFileOption("publication"),
      spec.Parameter.property("rdf-missing-artifact-policy")
    )

    private val _publication_request = spec.Request(
      _p_project,
      spec.Parameter.propertyFileOption("warehouse"),
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
    val empty: SiteConfig = SiteConfig(Map.empty, Map.empty, Map.empty)
  }

  private val _default_preview_port = "8980"

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
      case "bok" :: "publish-video" :: rest if _help_requested(rest) =>
        _print_publication_usage("publish-video")
        true
      case "bok" :: "publish-video" :: rest =>
        publishVideo(PublicationConfig.create("publish-video", rest), CozyVideo.VoicevoxClient.default, CozyVideo.VideoProcessRunner.default)
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
        println("Usage: cozy bok publish <project-dir> [--publication <dir>] [--warehouse <dir>] [--version <version>] [--strategy production] [--force] [--dry-run]")
        println("Run update-publication, build, optional stage, and configured upload workflow. Use --dry-run to print the plan without publication, warehouse, site, or upload side effects.")
      case "publish-video" =>
        println("Usage: cozy bok publish-video <project-dir> [--publication <dir>] [--warehouse <dir>] [--version <version>] [--force]")
        println("Publish .video packages into the BoK publication registry and video warehouse.")
      case "update-publication" =>
        println("Usage: cozy bok update-publication <project-dir> [--publication <dir>] [--warehouse <dir>] [--version <version>] [--force]")
        println("Update the BoK publication registry. V1 delegates to video publication.")
      case other =>
        RAISE.invalidArgumentFault(s"Unknown publication command: ${other}")
    }
  }

  private def _print_workflow_usage(name: String): Unit = {
    println(s"Usage: cozy bok ${name} [<project-dir>]")
    println(s"Run the external command registered at bok.workflow.${name}.command.")
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
    _write(sitedir.resolve("manual/index.dox"), _manual_index(), config.policy)
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

  def createCategory(config: CategoryConfig): Unit = {
    val dir = config.project.resolve("src/main/doxsite").resolve(config.name)
    _write(dir.resolve("category.yaml"), _category(_category_name(config.name), config.title, config.description, config.purpose), config.policy)
    _write(dir.resolve("index.dox"), _category_index(config.name, config.title, config.description, config.articles, config.terms), config.policy)
    config.articles.foreach { article =>
      _write(dir.resolve(article.fileName), _article(article.title, article.purpose), config.policy)
    }
    val glossarydir = config.project.resolve("src/main/doxsite/glossary").resolve(config.name)
    config.terms.foreach { term =>
      _write(glossarydir.resolve(term.fileName), _glossary(term.title, term.definition, term.reading), config.policy)
    }
  }

  def build(config: BuildConfig, runner: Runner): Unit = {
    _delete_directory(config.project.resolve("target"))
    _delete_directory(config.project.resolve(s"doxsite-cache-${config.strategy}.d"))
    _delete_directory(config.doxsitePath)
    _delete_directory(config.antoraPath)
    _delete_directory(config.websitePath)
    if (config.arcadia.enabled)
      _delete_directory(config.arcadiaSitePath)
    runner.run(_dox_antora_command(config), config.project, _smartdox_toolchain_env(config))
    _run_antora(config, runner)
    runner.run(_dox_site_command(config), config.project, _smartdox_toolchain_env(config))
    _normalize_doxsite_output(config)
    _delete_directory(config.project.resolve(s"doxsite-cache-${config.strategy}.d"))
    if (config.arcadia.enabled) {
      runner.run(Vector("arcadia", "site", config.arcadia.source, config.arcadiaSite), config.project)
      _copy_directory(config.arcadiaSitePath, config.websitePath)
    }
    _write_bok_pages(config)
    if (config.strategy == "production") {
      runner.run(Vector("dox", "site-mark", "-strategy", "production", "-output.scope.policy", "all", config.source), config.project)
      if (config.directAssets.enabled)
        config.directAssets.items.foreach { item =>
          _copy_directory(config.project.resolve(item.source), config.project.resolve(item.destination))
        }
    }
  }

  def preview(args: List[String], runner: Runner): Unit = {
    if (args.exists(x => x == "--help" || x == "-h")) {
      println("Usage: cozy bok preview [<project-dir>] [--port <port>]")
      println("Serve generated website.d through a local Web server for browser preview.")
      return
    }
    val previewconfig = PreviewConfig.create(args)
    val project = _resolve_bok_project(previewconfig.input)
    val config = _load_config(project)
    val port = previewconfig.port.map(_.toString).
      orElse(config.value("bok.preview.port")).
      getOrElse(_default_preview_port)
    val website = config.value("bok.website").getOrElse("website.d")
    val websitepath = project.resolve(website).toAbsolutePath.normalize
    println(s"Serving ${websitepath} at http://127.0.0.1:${port}/")
    println("Use this local Web server instead of opening generated HTML files directly.")
    runner.run(Vector("python3", "-m", "http.server", port), websitepath)
  }

  def runWorkflow(config: WorkflowConfig, runner: Runner): Unit =
    if (_require_workflow(config))
      runner.run(config.command, config.project, config.env)

  private def _require_workflow(config: WorkflowConfig): Boolean =
    if (config.command.isEmpty)
      RAISE.invalidArgumentFault(
        s"Missing bok workflow command: bok.workflow.${config.name}.command\n" +
          s"""Example:
             |bok:
             |  workflow:
             |    ${config.name}:
             |      command: "etc/website-${config.name}.sh"
             |""".stripMargin
      )
    else
      true

  def publishVideo(
    config: PublicationConfig,
    voicevox: CozyVideo.VoicevoxClient,
    videorunner: CozyVideo.VideoProcessRunner
  ): Vector[CozyVideoPublisher.PublishVideoResult] =
    _publish_video_packages(config, voicevox, videorunner)

  def updatePublication(
    config: PublicationConfig,
    voicevox: CozyVideo.VoicevoxClient,
    videorunner: CozyVideo.VideoProcessRunner
  ): Vector[CozyVideoPublisher.PublishVideoResult] =
    publishVideo(config, voicevox, videorunner)

  def publish(
    config: PublicationConfig,
    runner: Runner,
    voicevox: CozyVideo.VoicevoxClient,
    videorunner: CozyVideo.VideoProcessRunner
  ): Unit = {
    val preflight = _publish_preflight(config)
    val planned = Vector(
      PublishStep("preflight", "succeeded", "Publish preflight passed."),
      PublishStep("update-publication", if (preflight.packages.isEmpty) "skipped" else if (config.dryRun) "planned" else "pending", s"${preflight.packages.size} .video package(s)."),
      PublishStep("build", if (config.dryRun) "planned" else "pending", s"strategy=${preflight.build.strategy}"),
      PublishStep("stage", preflight.stage.map(_ => if (config.dryRun) "planned" else "pending").getOrElse("skipped"), preflight.stage.map(_.command.mkString(" ")).getOrElse("No stage workflow configured.")),
      PublishStep("upload", if (config.dryRun) "planned" else "pending", preflight.upload.command.mkString(" "))
    )
    if (config.dryRun) {
      _write_publish_manifest(config, preflight, planned)
      _print_publish_plan(config, preflight)
    } else {
      var steps = Vector(PublishStep("preflight", "succeeded", "Publish preflight passed."))
      try {
        if (preflight.packages.isEmpty) {
          steps :+= PublishStep("update-publication", "skipped", "No .video packages to publish.")
          _publish_status("update-publication", "skipped")
        } else {
          _publish_status("update-publication", "start")
          val results = updatePublication(config, voicevox, videorunner)
          steps :+= PublishStep("update-publication", "succeeded", s"${results.size} package(s) published.")
          _publish_status("update-publication", "succeeded")
        }
        _write_publish_manifest(config, preflight, steps)

        _publish_status("build", "start")
        build(preflight.build, runner)
        steps :+= PublishStep("build", "succeeded", s"strategy=${preflight.build.strategy}")
        _publish_status("build", "succeeded")
        _write_publish_manifest(config, preflight, steps)

        preflight.stage match {
          case Some(stage) =>
            _publish_status("stage", "start")
            runWorkflow(stage, runner)
            steps :+= PublishStep("stage", "succeeded", stage.command.mkString(" "))
            _publish_status("stage", "succeeded")
          case None =>
            steps :+= PublishStep("stage", "skipped", "No stage workflow configured.")
            _publish_status("stage", "skipped")
        }
        _write_publish_manifest(config, preflight, steps)

        _publish_status("upload", "start")
        runWorkflow(preflight.upload, runner)
        steps :+= PublishStep("upload", "succeeded", preflight.upload.command.mkString(" "))
        _publish_status("upload", "succeeded")
        _write_publish_manifest(config, preflight, steps)
      } catch {
        case e: Throwable =>
          val failed = _failed_step(steps)
          steps :+= PublishStep(failed, "failed", Option(e.getMessage).getOrElse(e.toString))
          _publish_status(failed, "failed")
          _write_publish_manifest(config, preflight, steps)
          throw e
      }
    }
  }

  private def _publish_preflight(config: PublicationConfig): PublishPreflight = {
    val stage = WorkflowConfig.create("stage", List(config.project.toString))
    val upload = WorkflowConfig.create("upload", List(config.project.toString))
    val optionalstage = if (stage.command.nonEmpty) Some(stage) else None
    _require_workflow(upload)
    val buildconfig = BuildConfig.create(List(
      config.project.toString,
      "--strategy", config.strategy,
      "--warehouse", config.warehousePath.toString,
      "--publication", config.publicationPath.toString
    ))
    val packages = if (config.videoEnabled) _video_packages(config) else Vector.empty
    _validate_publish_path("publication", config.publicationPath, config.project, config.sourcePath)
    _validate_publish_path("warehouse", config.warehousePath, config.project, config.sourcePath)
    _reject_path_overlap("publication", config.publicationPath, "warehouse", config.warehousePath)
    _reject_path_overlap("publication", config.publicationPath, "website", buildconfig.websitePath)
    _reject_path_overlap("publication", config.publicationPath, "doxsite", buildconfig.doxsitePath)
    _reject_path_overlap("warehouse", config.warehousePath, "website", buildconfig.websitePath)
    _reject_path_overlap("warehouse", config.warehousePath, "doxsite", buildconfig.doxsitePath)
    PublishPreflight(optionalstage, upload, buildconfig, packages)
  }

  private def _validate_publish_path(name: String, path: Path, project: Path, source: Path): Unit = {
    if (path == source || path.startsWith(source) || source.startsWith(path))
      RAISE.invalidArgumentFault(s"Invalid ${name} path overlaps BoK source: ${path}")
    val parent = Option(path.getParent).getOrElse(project)
    if (Files.exists(path) && !Files.isDirectory(path))
      RAISE.invalidArgumentFault(s"Invalid ${name} path is not a directory: ${path}")
    if (!Files.exists(path) && !Files.exists(parent))
      RAISE.invalidArgumentFault(s"Invalid ${name} path parent does not exist: ${parent}")
    if (Files.exists(parent) && !Files.isWritable(parent))
      RAISE.invalidArgumentFault(s"Invalid ${name} path parent is not writable: ${parent}")
    if (Files.exists(path) && !Files.isWritable(path))
      RAISE.invalidArgumentFault(s"Invalid ${name} path is not writable: ${path}")
  }

  private def _reject_path_overlap(leftname: String, left: Path, rightname: String, right: Path): Unit =
    if ({
      val leftpath = left.toAbsolutePath.normalize()
      val rightpath = right.toAbsolutePath.normalize()
      leftpath == rightpath || leftpath.startsWith(rightpath) || rightpath.startsWith(leftpath)
    })
      RAISE.invalidArgumentFault(s"Invalid ${leftname}/${rightname} path overlap: ${left.toAbsolutePath.normalize()} / ${right.toAbsolutePath.normalize()}")

  private def _failed_step(steps: Vector[PublishStep]): String =
    if (!steps.exists(_.name == "update-publication"))
      "update-publication"
    else if (!steps.exists(_.name == "build"))
      "build"
    else if (!steps.exists(_.name == "stage"))
      "stage"
    else if (!steps.exists(_.name == "upload"))
      "upload"
    else
      "publish"

  private def _publish_status(step: String, status: String): Unit =
    println(s"bok publish: ${step}: ${status}")

  private def _print_publish_plan(config: PublicationConfig, preflight: PublishPreflight): Unit = {
    println("bok publish dry-run")
    println(s"project: ${config.project}")
    println(s"publication: ${config.publicationPath}")
    println(s"warehouse: ${config.warehousePath}")
    println(s"strategy: ${config.strategy}")
    println("steps:")
    println(s"- update-publication: ${preflight.packages.size} .video package(s)")
    println(s"- build: strategy=${preflight.build.strategy}")
    println(s"- stage: ${preflight.stage.map(_.command.mkString(" ")).getOrElse("skipped")}")
    println(s"- upload: ${preflight.upload.command.mkString(" ")}")
    println(s"manifest: ${config.manifestPath}")
  }

  private def _write_publish_manifest(
    config: PublicationConfig,
    preflight: PublishPreflight,
    steps: Vector[PublishStep]
  ): Unit = {
    Files.createDirectories(config.manifestPath.getParent)
    val json = Json.obj(
      "schema" -> Json.fromString("cozy.bok.publish-manifest.v1"),
      "project" -> Json.fromString(config.project.toString),
      "source" -> Json.fromString(config.sourcePath.toString),
      "publication" -> Json.fromString(config.publicationPath.toString),
      "warehouse" -> Json.fromString(config.warehousePath.toString),
      "strategy" -> Json.fromString(config.strategy),
      "dryRun" -> Json.fromBoolean(config.dryRun),
      "force" -> Json.fromBoolean(config.force),
      "videoEnabled" -> Json.fromBoolean(config.videoEnabled),
      "videoPackages" -> Json.fromValues(preflight.packages.map(x => Json.fromString(x.toString))),
      "publicationArtifacts" -> Json.fromValues(preflight.packages.map(x => Json.obj(
        "sourcePackage" -> Json.fromString(x.toString),
        "registryRoot" -> Json.fromString(config.publicationPath.toString),
        "warehouseRoot" -> Json.fromString(config.warehousePath.toString)
      ))),
      "buildCommands" -> Json.arr(
        Json.fromValues(_dox_antora_command(preflight.build).map(Json.fromString)),
        Json.fromValues(_dox_site_command(preflight.build).map(Json.fromString))
      ),
      "buildCommand" -> Json.fromValues(_dox_site_command(preflight.build).map(Json.fromString)),
      "stageCommand" -> Json.fromValues(preflight.stage.toVector.flatMap(_.command).map(Json.fromString)),
      "uploadCommand" -> Json.fromValues(preflight.upload.command.map(Json.fromString)),
      "steps" -> Json.fromValues(steps.map(_publish_step_json))
    )
    Files.writeString(config.manifestPath, json.spaces2, StandardCharsets.UTF_8)
  }

  private def _publish_step_json(step: PublishStep): Json =
    Json.obj(
      "name" -> Json.fromString(step.name),
      "status" -> Json.fromString(step.status),
      "message" -> Json.fromString(step.message)
    )

  private def _publish_video_packages(
    config: PublicationConfig,
    voicevox: CozyVideo.VoicevoxClient,
    videorunner: CozyVideo.VideoProcessRunner
  ): Vector[CozyVideoPublisher.PublishVideoResult] =
    if (!config.videoEnabled)
      Vector.empty
    else
      _video_packages(config).map { packagedir =>
        CozyVideoPublisher.publish(
          CozyVideoPublisher.PublishVideoConfig(
            packagedir,
            config.publicationPath,
            config.warehousePath,
            config.version,
            config.force
          ),
          voicevox,
          videorunner
        )
      }

  private def _video_packages(config: PublicationConfig): Vector[Path] = {
    if (!Files.isDirectory(config.sourcePath))
      RAISE.invalidArgumentFault(s"Missing BoK source directory: ${config.sourcePath}")
    val stream = Files.walk(config.sourcePath)
    try {
      val dirs = stream.iterator.asScala.toVector.filter(Files.isDirectory(_)).map(_.toAbsolutePath.normalize())
      dirs.find(_.getFileName.toString.endsWith(".video.d")).foreach { path =>
        RAISE.invalidArgumentFault(s"*.video.d is reserved for generated/work directories: $path")
      }
      dirs.filter(_.getFileName.toString.endsWith(".video")).sortBy(_.toString).map { path =>
        if (!_has_video_descriptor(path))
          RAISE.invalidArgumentFault(s"Missing video descriptor in .video package: $path")
        path
      }
    } finally {
      stream.close()
    }
  }

  private def _has_video_descriptor(path: Path): Boolean =
    Vector("video.yaml", "video.yml", "video.json").exists(x => Files.isRegularFile(path.resolve(x)))

  private def _run_antora(config: BuildConfig, runner: Runner): Unit = {
    config.localeMode match {
      case LocaleMode.SingleLocaleRoot =>
        _copy_ui_bundle(config, config.antoraPath)
        runner.run(_docker_antora(config, config.antora, config.website), config.project)
      case LocaleMode.MultiLocaleSubdirs =>
        config.languages.foreach { lang =>
          val antoradir = Paths.get(config.antora).resolve(lang).toString
          val websitedir = Paths.get(config.website).resolve(lang).toString
          _copy_ui_bundle(config, config.project.resolve(antoradir))
          runner.run(_docker_antora(config, antoradir, websitedir), config.project)
        }
    }
  }

  private def _dox_antora_command(config: BuildConfig): Vector[String] =
    Vector("dox", "antora", "-strategy", config.strategy) ++
      _publication_options(config, includerdf = false) ++
      Vector(config.source)

  private def _dox_site_command(config: BuildConfig): Vector[String] =
    Vector("dox", "site", "-strategy", config.strategy, "-output.scope.policy", config.siteOutputScopePolicy) ++
      _publication_options(config, includerdf = true) ++
      Vector(config.source)

  private def _publication_options(config: BuildConfig, includerdf: Boolean): Vector[String] = {
    val base = Vector(
      "-publication", config.publication.publicationPath(config.project).toString
    )
    if (includerdf && config.publication.mergeRdf)
      base ++ Vector(
        "-publication.repository", config.publication.warehousePath(config.project).toString,
        "-publication.rdf.missing.policy", config.publication.missingRdfPolicy
      )
    else
      base
  }

  private def _docker_antora(config: BuildConfig, workdir: String, output: String): Vector[String] =
    Vector(
      "docker",
      "run",
      "--rm",
      "-e",
      "SMARTDOX_KROKI_PORT=9609",
      "-e",
      s"SMARTDOX_KROKI_DOCKER_IMAGE=${config.dockerImage}",
      "-v",
      s"${config.project.toString}:/workspace",
      "-w",
      s"/workspace/${workdir}",
      config.dockerImage,
      "antora",
      "antora-playbook.yml",
      "--to-dir",
      s"/workspace/${output}"
    )

  private def _smartdox_toolchain_env(config: BuildConfig): Map[String, String] =
    Map(
      "SMARTDOX_KROKI_DOCKER_IMAGE" -> config.dockerImage,
      "SMARTDOX_PDF_DOCKER_IMAGE" -> config.dockerImage,
      "SMARTDOX_COZY_TOOLCHAIN_IMAGE" -> config.dockerImage
    )

  private def _normalize_doxsite_output(config: BuildConfig): Unit =
    config.localeMode match {
      case LocaleMode.SingleLocaleRoot =>
        _single_locale_doxsite_dirs(config).foreach(_delete_directory)
      case LocaleMode.MultiLocaleSubdirs =>
        Unit
    }

  private def _single_locale_doxsite_dirs(config: BuildConfig): Vector[Path] =
    (config.languages ++ Vector("ja", "en")).distinct.map(config.doxsitePath.resolve)

  private def _copy_ui_bundle(config: BuildConfig, target: Path): Unit =
    {
      if (!Files.isRegularFile(config.uiBundlePath))
        _write_default_ui_bundle(config.uiBundlePath, ProjectFilePolicy.Default)
      if (Files.isRegularFile(config.uiBundlePath)) {
        Files.createDirectories(target)
        _copy_ui_bundle_with_cozy_assets(config.uiBundlePath, target.resolve("ui-bundle.zip"))
      }
    }

  private def _copy_ui_bundle_with_cozy_assets(source: Path, target: Path): Unit = {
    val assets = Vector(
      "css/bootstrap-grid.min.css" -> "cozy/antora-ui/css/bootstrap-grid.min.css",
      "css/cozy-bok-dashboard.css" -> "cozy/antora-ui/css/cozy-bok-dashboard.css"
    )
    val assetnames = assets.map(_._1).toSet
    val tmp = Files.createTempFile(Option(target.getParent).getOrElse(Paths.get(".")), "ui-bundle-", ".zip")
    try {
      val in = new ZipInputStream(Files.newInputStream(source))
      val out = new ZipOutputStream(Files.newOutputStream(tmp))
      try {
        var entry = in.getNextEntry
        while (entry != null) {
          if (!entry.isDirectory) {
            if (!assetnames.contains(entry.getName)) {
              out.putNextEntry(new ZipEntry(entry.getName))
              in.transferTo(out)
              out.closeEntry()
            }
          }
          in.closeEntry()
          entry = in.getNextEntry
        }
        assets.foreach {
          case (name, resource) =>
            _resource_bytes(resource) match {
              case Some(bytes) => _zip_bytes(out, name, bytes)
              case None => RAISE.noReachDefect
            }
        }
      } finally {
        out.close()
        in.close()
      }
      Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING)
    } finally {
      Files.deleteIfExists(tmp)
    }
  }

  private def _copy_directory(source: Path, dest: Path): Unit =
    if (Files.exists(source)) {
      val stream = Files.walk(source)
      try {
        stream.iterator.asScala.foreach { path =>
          val rel = source.relativize(path)
          val target = dest.resolve(rel)
          if (Files.isDirectory(path))
            Files.createDirectories(target)
          else {
            Option(target.getParent).foreach(Files.createDirectories(_))
            Files.copy(path, target, StandardCopyOption.REPLACE_EXISTING)
          }
        }
      } finally {
        stream.close()
      }
    }

  private def _write_bok_pages(config: BuildConfig): Unit =
    config.localeMode match {
      case LocaleMode.SingleLocaleRoot =>
        _write_home_page(config, config.websitePath, config.defaultLocale)
        _write_special_pages(config, config.websitePath, config.defaultLocale, writeLocalizedGlossaryIndexes = true)
        _write_category_pages(config, config.websitePath, config.defaultLocale)
      case LocaleMode.MultiLocaleSubdirs =>
        config.languages.foreach { lang =>
          val target = config.websitePath.resolve(lang)
          _write_home_page(config, target, lang)
          _write_special_pages(config, target, lang, writeLocalizedGlossaryIndexes = false)
          _write_category_pages(config, target, lang)
        }
    }

  private def _write_home_page(config: BuildConfig, target: Path, locale: String): Unit =
    {
      val page = target.resolve("index.html")
      _write_text(
        page,
      s"""<!doctype html>
         |<html lang="${_html_escape(locale)}">
         |<head>
         |  <meta charset="utf-8">
         |  <meta name="viewport" content="width=device-width, initial-scale=1">
         |  <title>${_html_escape(_uif(locale, "home.document.title", config.siteTitle))}</title>
         |${_site_css_links(config, page)}
         |</head>
         |<body class="article ${_html_escape(_dashboard_theme_class(config))}">
         |<header class="header">
         |  <nav class="navbar">
         |    <div class="navbar-brand">
         |      <a class="navbar-item" href="index.html">${_html_escape(config.siteTitle)}</a>
         |      <button class="navbar-burger" aria-controls="topbar-nav" aria-expanded="false" aria-label="Toggle main menu">
         |        <span></span>
         |        <span></span>
         |        <span></span>
         |      </button>
         |    </div>
         |    <div id="topbar-nav" class="navbar-menu">
         |      <div class="navbar-end">
         |        <a class="navbar-item" href="index.html">${_html_escape(_ui(locale, "nav.home"))}</a>
         |        ${_category_nav_menu(locale, _regular_category_summaries(config.sourcePath), "")}
         |      </div>
         |    </div>
         |  </nav>
         |</header>
         |<div class="body body-dashboard">
         |  <main class="article">
         |    <div class="toolbar" role="navigation">
         |      <button class="nav-toggle"></button>
         |      <a href="index.html" class="home-link is-current"></a>
         |      <nav class="breadcrumbs" aria-label="breadcrumbs">
         |        <ul>
         |          <li><a href="index.html">${_html_escape(config.siteTitle)}</a></li>
         |          <li>${_html_escape(_ui(locale, "dashboard"))}</li>
         |        </ul>
         |      </nav>
         |    </div>
         |    <div class="content">
         |      <article class="doc">
         |        ${_home_dashboard(config, locale)}
         |        ${_source_narrative_section(config.sourcePath.resolve("index.dox"), locale)}
         |        <div class="sect1" id="operation-policy">
         |          <h2>${_html_escape(_ui(locale, "operation.policy"))}</h2>
         |          <div class="sectionbody">
         |            <p>${_html_escape(_ui(locale, "operation.policy.body"))}</p>
         |          </div>
         |        </div>
         |      </article>
         |    </div>
         |  </main>
         |</div>
         |</body>
         |</html>
         |""".stripMargin
      )
    }

  private def _write_category_pages(config: BuildConfig, target: Path, locale: String): Unit = {
    val categories = _category_contents(config.sourcePath)
    categories.foreach { category =>
      val page = target.resolve(category.slug).resolve("index.html")
      _write_text(
        page,
        _category_html_page(config, category, categories, locale, page)
      )
    }
  }

  private def _write_special_pages(
    config: BuildConfig,
    target: Path,
    locale: String,
    writeLocalizedGlossaryIndexes: Boolean
  ): Unit = {
    val categories = _category_contents(config.sourcePath)
    val terms = _terms(config, categories)
    val glossarybody = _glossary_dashboard_body(config, categories, terms, _language_index_root_prefix(config), locale)
    val historyhref = _latest_history_year_page(target.resolve("history"))
    _write_rdf_page(config, target, locale, categories)
    _write_term_hub_pages(config, target, locale, categories, terms)
    _write_text(
      target.resolve("glossary").resolve("index.html"),
      _special_html_page(
        config,
        categories,
        locale,
        target.resolve("glossary").resolve("index.html"),
        _ui(locale, "glossary.title"),
        _ui(locale, "glossary.description"),
        glossarybody
      )
    )
    if (historyhref.isEmpty) {
      val historypage = target.resolve("history").resolve("index.html")
      _write_text(
        historypage,
        _special_html_page_with_toc(
          config,
          categories,
          locale,
          historypage,
          _ui(locale, "history.title"),
          _ui(locale, "history.description"),
          _history_dashboard_body(locale),
          Vector("dashboard" -> "Dashboard", "timeline" -> "Timeline", "operation-notes" -> "Operation Notes")
        )
      )
    }
    val manualpage = target.resolve("manual").resolve("index.html")
    _write_text(
      manualpage,
      _special_html_page_with_toc(
        config,
        categories,
        locale,
        manualpage,
        _ui(locale, "manual.title"),
        _ui(locale, "manual.description"),
        _manual_dashboard_body(locale),
        Vector("dashboard" -> "Dashboard", "basic-operations" -> "Basic Operations", "page-types" -> "Page Types", "notes" -> "Notes")
      )
    )
    if (writeLocalizedGlossaryIndexes) {
      _write_text(
        target.resolve("ja").resolve("glossary").resolve("index.html"),
        _localized_glossary_index_page(config, categories, "ja")
      )
      _write_text(
        target.resolve("en").resolve("glossary").resolve("index.html"),
        _localized_glossary_index_page(config, categories, "en")
      )
    }
  }

  private def _write_rdf_page(config: BuildConfig, target: Path, locale: String, categories: Vector[CategoryContent]): Unit = {
    _copy_rdf_publication_artifacts(config, target)
    val page = target.resolve("rdf").resolve("index.html")
    _write_text(
      page,
      _rdf_dedicated_page(config, categories, locale, page)
    )
  }

  private def _copy_rdf_publication_artifacts(config: BuildConfig, target: Path): Unit = {
    _copy_if_exists(config.doxsitePath.resolve("site.ttl"), target.resolve("rdf").resolve("site.ttl"))
    _copy_if_exists(config.doxsitePath.resolve("site.jsonld"), target.resolve("rdf").resolve("site.jsonld"))
    _copy_if_exists(config.doxsitePath.resolve("metadata/rdf/graph.json"), target.resolve("metadata/rdf/graph.json"))
    _copy_if_exists(config.doxsitePath.resolve("metadata/glossary/terms.json"), target.resolve("metadata/glossary/terms.json"))
  }

  private def _copy_if_exists(source: Path, target: Path): Unit =
    if (Files.isRegularFile(source)) {
      Option(target.getParent).foreach(Files.createDirectories(_))
      Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING)
    }

  private def _glossary_dashboard_body(
    config: BuildConfig,
    categories: Vector[CategoryContent],
    terms: Vector[TermEntry],
    languagerootprefix: String,
    locale: String
  ): String = {
    val categorycount = categories.size
    val categorieswithterms = terms.flatMap(_.category).distinct.size
    s"""<div class="sect1 bok-term-dashboard" id="term-groups">
       |  <h2>${_html_escape(_ui(locale, "term.dashboard.title"))}</h2>
       |  <div class="sectionbody">
       |    <p>${_html_escape(_ui(locale, "term.dashboard.description"))}</p>
       |    <p>${_html_escape(_ui(locale, "term.dashboard.source.path"))} <code>glossary/&lt;category&gt;/</code></p>
       |    ${_glossary_metric_cards(categorycount, categorieswithterms, terms.size)}
       |    ${_term_group_cards(locale, terms, categories)}
       |  </div>
       |</div>
       |<div class="sect1" id="language-index">
       |  <h2>${_html_escape(_ui(locale, "term.language.index"))}</h2>
       |  <div class="sectionbody">
       |    ${_glossary_language_links(config, languagerootprefix)}
       |  </div>
       |</div>
       |<div class="sect1" id="recent-terms">
       |  <h2>${_html_escape(_ui(locale, "term.recent"))}</h2>
       |  <div class="sectionbody">
       |    ${_glossary_recent_terms(terms)}
       |  </div>
       |</div>""".stripMargin
  }

  private def _history_dashboard_body(locale: String): String =
    s"""<div class="sect1" id="timeline">
       |  <h2>Timeline</h2>
       |  <div class="sectionbody">
       |    <p>${_html_escape(_ui(locale, "history.timeline.description"))}</p>
       |    <ul>
       |      <li>${_html_escape(_ui(locale, "history.timeline.item.started"))}</li>
       |      <li>${_html_escape(_ui(locale, "history.timeline.item.record"))}</li>
       |    </ul>
       |  </div>
       |</div>
       |<div class="sect1" id="operation-notes">
       |  <h2>Operation Notes</h2>
       |  <div class="sectionbody">
       |    <p>${_html_escape(_ui(locale, "history.operation.notes"))}</p>
       |  </div>
       |</div>""".stripMargin

  private def _manual_dashboard_body(locale: String): String =
    s"""<div class="sect1" id="basic-operations">
       |  <h2>Basic Operations</h2>
       |  <div class="sectionbody">
       |    <ul>
       |      <li><code>cozy bok doctor</code>: ${_html_escape(_ui(locale, "manual.operation.doctor"))}</li>
       |      <li><code>cozy bok build --strategy preview</code>: ${_html_escape(_ui(locale, "manual.operation.build"))}</li>
       |      <li><code>cozy bok preview</code>: ${_html_escape(_ui(locale, "manual.operation.preview"))}</li>
       |      <li><code>cozy bok publish . --dry-run</code>: ${_html_escape(_ui(locale, "manual.operation.publish.dryrun"))}</li>
       |    </ul>
       |  </div>
       |</div>
       |<div class="sect1" id="page-types">
       |  <h2>Page Types</h2>
       |  <div class="sectionbody">
       |    <ul>
       |      <li>Home Dashboard: ${_html_escape(_ui(locale, "manual.page.home"))}</li>
       |      <li>Category Dashboard: ${_html_escape(_ui(locale, "manual.page.category"))}</li>
       |      <li>Glossary: ${_html_escape(_ui(locale, "manual.page.glossary"))}</li>
       |      <li>History: ${_html_escape(_ui(locale, "manual.page.history"))}</li>
       |    </ul>
       |  </div>
       |</div>
       |<div class="sect1" id="notes">
       |  <h2>Notes</h2>
       |  <div class="sectionbody">
       |    <p>${_html_escape(_ui(locale, "manual.notes"))}</p>
       |  </div>
       |</div>""".stripMargin

  private def _rdf_dedicated_page(
    config: BuildConfig,
    categories: Vector[CategoryContent],
    locale: String,
    page: Path
  ): String =
    s"""<!doctype html>
       |<html lang="${_html_escape(locale)}">
       |<head>
       |  <meta charset="utf-8">
       |  <meta name="viewport" content="width=device-width, initial-scale=1">
       |  <title>${_html_escape(_ui(locale, "rdf.graph.title"))} - ${_html_escape(config.siteTitle)}</title>
       |${_site_css_links(config, page)}
       |</head>
       |<body class="article ${_html_escape(_dashboard_theme_class(config))}">
       |${_category_header(config, categories, locale)}
       |<div class="body body-dashboard bok-rdf-body">
       |  <main class="article bok-rdf-main">
       |    <div class="content">
       |      <article class="doc bok-rdf-doc">
       |        ${_rdf_workspace(locale)}
       |      </article>
       |    </div>
       |  </main>
       |</div>
       |</body>
       |</html>
       |""".stripMargin

  private def _rdf_workspace(locale: String): String =
    s"""<section class="bok-rdf-workspace" data-graph="../metadata/rdf/graph.json" data-terms="../metadata/glossary/terms.json" data-triples="site.ttl">
       |  <div class="bok-rdf-hero">
       |    <div>
       |      <span class="bok-dashboard-eyebrow">RDF</span>
       |      <h1 class="page">${_html_escape(_ui(locale, "rdf.graph.title"))}</h1>
       |      <p>${_html_escape(_ui(locale, "rdf.graph.description"))}</p>
       |    </div>
       |    <div class="bok-rdf-hero-actions">
       |      <a href="../index.html">${_html_escape(_ui(locale, "nav.home"))}</a>
       |      <a href="site.ttl">site.ttl</a>
       |      <a href="site.jsonld">site.jsonld</a>
       |      <a href="../metadata/rdf/graph.json">graph.json</a>
       |    </div>
       |  </div>
       |  <div class="bok-rdf-toolbar">
       |    <div class="bok-rdf-view-switch" role="tablist" aria-label="RDF views">
       |      <button class="is-active" type="button" data-rdf-view="graph">${_html_escape(_ui(locale, "rdf.graph.view.graph"))}</button>
       |      <button type="button" data-rdf-view="triples">${_html_escape(_ui(locale, "rdf.graph.view.triples"))}</button>
       |    </div>
       |    <label>${_html_escape(_ui(locale, "rdf.graph.category.filter"))}<input id="bok-rdf-category-filter" type="text" placeholder="category"></label>
       |    <label>${_html_escape(_ui(locale, "rdf.graph.term.filter"))}<input id="bok-rdf-term-filter" type="text" placeholder="term"></label>
       |    <span id="bok-rdf-viewer-status">${_html_escape(_ui(locale, "rdf.graph.loading"))}</span>
       |  </div>
       |  <div class="bok-rdf-panels">
       |    <section class="bok-rdf-panel bok-rdf-panel-graph is-active" data-rdf-panel="graph" aria-label="${_html_escape(_ui(locale, "rdf.graph.view.graph"))}">
       |      <div id="bok-rdf-viewer-graph" class="bok-rdf-viewer-graph"></div>
       |    </section>
       |    <section class="bok-rdf-panel bok-rdf-panel-triples" data-rdf-panel="triples" aria-label="${_html_escape(_ui(locale, "rdf.graph.view.triples"))}">
       |      <div class="bok-rdf-triples-header">
       |        <strong>${_html_escape(_ui(locale, "rdf.graph.triples.title"))}</strong>
       |        <span id="bok-rdf-triples-status">${_html_escape(_ui(locale, "rdf.graph.triples.loading"))}</span>
       |      </div>
       |      <pre id="bok-rdf-triples-view" class="bok-rdf-triples-view"></pre>
       |    </section>
       |  </div>
       |</section>
       |<script>
       |${_rdf_viewer_script(locale)}
       |</script>""".stripMargin

  private def _rdf_viewer_script(locale: String): String =
    s"""(function() {
       |  const root = document.querySelector('.bok-rdf-workspace');
       |  const graphTarget = document.getElementById('bok-rdf-viewer-graph');
       |  const graphStatus = document.getElementById('bok-rdf-viewer-status');
       |  const triplesTarget = document.getElementById('bok-rdf-triples-view');
       |  const triplesStatus = document.getElementById('bok-rdf-triples-status');
       |  const input = document.getElementById('bok-rdf-category-filter');
       |  const termInput = document.getElementById('bok-rdf-term-filter');
       |  if (!root || !graphTarget || !graphStatus) return;
       |  const params = new URLSearchParams(window.location.search);
       |  const initialCategory = params.get('category') || '';
       |  const initialTerm = params.get('term') || '';
       |  let focusedNodeId = null;
       |  if (input) input.value = initialCategory;
       |  if (termInput) termInput.value = initialTerm;
       |  function activate(view) {
       |    document.querySelectorAll('[data-rdf-view]').forEach(function(button) {
       |      button.classList.toggle('is-active', button.getAttribute('data-rdf-view') === view);
       |    });
       |    document.querySelectorAll('[data-rdf-panel]').forEach(function(panel) {
       |      panel.classList.toggle('is-active', panel.getAttribute('data-rdf-panel') === view);
       |    });
       |  }
       |  document.querySelectorAll('[data-rdf-view]').forEach(function(button) {
       |    button.addEventListener('click', function() { activate(button.getAttribute('data-rdf-view')); });
       |  });
       |  function hasTerm(item, term) {
       |    return !term || (item.terms || []).indexOf(term) >= 0;
       |  }
       |  function termLabel(termIndex, term) {
       |    const item = termIndex[term];
       |    return item ? (item.title || term) : term;
       |  }
       |  function renderGraph(data, category, term, termIndex) {
    const allNodes = (data.nodes || []).slice().sort(function(a, b) {
      const degree = (b.degree || 0) - (a.degree || 0);
      return degree !== 0 ? degree : String(a.id || '').localeCompare(String(b.id || ''));
    });
    const allEdges = (data.edges || []).slice().sort(function(a, b) {
      return String(a.source || '').localeCompare(String(b.source || '')) ||
        String(a.target || '').localeCompare(String(b.target || '')) ||
        String(a.predicate || a.label || '').localeCompare(String(b.predicate || b.label || ''));
    });
    const categoryNodeIds = new Set();
    const termNodeIds = new Set();
    allNodes.forEach(function(node) {
      if (category && node.category === category) categoryNodeIds.add(node.id);
      if (term && hasTerm(node, term)) termNodeIds.add(node.id);
    });
    const matchingEdges = allEdges.filter(function(edge) {
      const matchesCategory = !category || edge.category === category || categoryNodeIds.has(edge.source) || categoryNodeIds.has(edge.target);
      const matchesTerm = !term || hasTerm(edge, term) || termNodeIds.has(edge.source) || termNodeIds.has(edge.target);
      return matchesCategory && matchesTerm;
    });
    const edgeNodeIds = new Set();
    matchingEdges.forEach(function(edge) {
      if (edge.source) edgeNodeIds.add(edge.source);
      if (edge.target) edgeNodeIds.add(edge.target);
    });
    const matchingNodes = allNodes.filter(function(node) {
      const matchesCategory = !category || node.category === category || edgeNodeIds.has(node.id);
      const matchesTerm = !term || hasTerm(node, term) || edgeNodeIds.has(node.id);
      return matchesCategory && matchesTerm;
    }).slice(0, 120);
    const nodeIds = new Set(matchingNodes.map(function(node) { return node.id; }));
    const edges = matchingEdges.filter(function(edge) {
      return nodeIds.has(edge.source) && nodeIds.has(edge.target);
    }).slice(0, 180);
    const focus = focusedNodeId && nodeIds.has(focusedNodeId) ? focusedNodeId : null;
    if (focusedNodeId && !focus) focusedNodeId = null;
    const focusGraph = focus ? focusedGraphSlice(focus, matchingNodes, edges) : {
      nodes: matchingNodes,
      edges: edges,
      roles: {}
    };
    const visibleNodes = focusGraph.nodes;
    const visibleEdges = focusGraph.edges;
    graphStatus.textContent = visibleNodes.length + ' nodes / ' + visibleEdges.length + ' edges' + (focus ? ' / focus: ' + label(focus) : '') + (data.truncated ? ' (truncated)' : '');
    graphTarget.innerHTML =
      '<div class="bok-rdf-graph-summary">' +
        '<span><b>' + visibleNodes.length + '</b>nodes</span>' +
        '<span><b>' + visibleEdges.length + '</b>edges</span>' +
        '<span><b>' + escapeHtml(category || 'all') + '</b>category</span>' +
        '<span><b>' + escapeHtml(term ? termLabel(termIndex, term) : 'all') + '</b>term</span>' +
      '</div>' +
      '<div class="bok-rdf-focus-bar">' +
        (focus ? '<span>${_javascript_string(_ui(locale, "rdf.graph.focus.node"))}: <b>' + escapeHtml(label(focus)) + '</b></span><button type="button" data-rdf-clear-focus="true">${_javascript_string(_ui(locale, "rdf.graph.focus.clear"))}</button>' : '<span>${_javascript_string(_ui(locale, "rdf.graph.focus.help"))}</span>') +
      '</div>' +
      '<div class="bok-rdf-graph-layout">' +
        '<div class="bok-rdf-graph-canvas" data-rdf-graph-canvas="true"></div>' +
        '<div class="bok-rdf-graph-detail"><h2>Nodes</h2><div class="bok-rdf-node-cloud">' + visibleNodes.map(function(node) {
          const degree = node.degree == null ? '-' : node.degree;
          const role = focusGraph.roles[node.id] || 'normal';
          return '<button type="button" class="bok-rdf-node bok-rdf-node-' + escapeHtml(cssName(node.node_type || node.type || 'unknown')) + ' bok-rdf-node-role-' + escapeHtml(role) + '" data-rdf-focus-node="' + escapeHtml(node.id) + '"><strong>' + escapeHtml(node.label || label(node.id)) + '</strong><em>' + escapeHtml(node.category || '-') + ' / degree ' + escapeHtml(degree) + '</em></button>';
        }).join('') + '</div></div>' +
      '</div>';
    const canvas = graphTarget.querySelector('[data-rdf-graph-canvas]');
    if (!canvas) return;
    if (visibleNodes.length === 0) {
      canvas.innerHTML = '<div class="bok-rdf-empty">No graph nodes match the current filter.</div>';
      return;
    }
    const clearButton = graphTarget.querySelector('[data-rdf-clear-focus]');
    if (clearButton) clearButton.addEventListener('click', function() { focusedNodeId = null; renderGraph(data, category, term, termIndex); });
    graphTarget.querySelectorAll('[data-rdf-focus-node]').forEach(function(button) {
      button.addEventListener('click', function() {
        const node = visibleNodes.filter(function(item) { return item.id === button.getAttribute('data-rdf-focus-node'); })[0];
        if (node) showNodeDetail(node, visibleEdges);
      });
    });
    renderGraphSvg(canvas, visibleNodes, visibleEdges, focusGraph.roles, focus);
  }
  function focusedGraphSlice(focus, nodes, edges) {
    const nodeMap = {};
    nodes.forEach(function(node) { nodeMap[node.id] = node; });
    const near = new Set([focus]);
    const oneHopEdges = [];
    edges.forEach(function(edge) {
      if (edge.source === focus && edge.target) {
        near.add(edge.target);
        oneHopEdges.push(edge);
      }
      if (edge.target === focus && edge.source) {
        near.add(edge.source);
        oneHopEdges.push(edge);
      }
    });
    const visibleIds = new Set(Array.from(near));
    const schemaEdges = [];
    const schemaEdgeKeys = new Set();
    for (let depth = 0; depth < 4; depth += 1) {
      let changed = false;
      edges.forEach(function(edge) {
        const sourceVisible = visibleIds.has(edge.source);
        const targetVisible = visibleIds.has(edge.target);
        if (!sourceVisible && !targetVisible) return;
        const sourceNode = nodeMap[edge.source];
        const targetNode = nodeMap[edge.target];
        const requiredFromSource = sourceVisible && isSchemaRequiredEdge(edge, sourceNode, 'outgoing');
        const requiredFromTarget = targetVisible && isSchemaRequiredEdge(edge, targetNode, 'incoming');
        if (!requiredFromSource && !requiredFromTarget) return;
        const key = edgeKey(edge);
        if (!schemaEdgeKeys.has(key)) {
          schemaEdgeKeys.add(key);
          schemaEdges.push(edge);
        }
        if (edge.source && !visibleIds.has(edge.source)) {
          visibleIds.add(edge.source);
          changed = true;
        }
        if (edge.target && !visibleIds.has(edge.target)) {
          visibleIds.add(edge.target);
          changed = true;
        }
      });
      if (!changed) break;
    }
    const roles = {};
    Array.from(visibleIds).forEach(function(id) { roles[id] = near.has(id) ? 'near' : 'schema'; });
    roles[focus] = 'focus';
    const visibleNodes = nodes.filter(function(node) { return visibleIds.has(node.id); }).slice(0, 100);
    const visibleNodeIds = new Set(visibleNodes.map(function(node) { return node.id; }));
    const visibleEdgeKeys = new Set();
    const visibleEdges = [];
    oneHopEdges.concat(schemaEdges).forEach(function(edge) {
      const key = edgeKey(edge);
      if (!visibleEdgeKeys.has(key) && visibleNodeIds.has(edge.source) && visibleNodeIds.has(edge.target)) {
        visibleEdgeKeys.add(key);
        visibleEdges.push(edge);
      }
    });
    return {nodes: visibleNodes, edges: visibleEdges.slice(0, 160), roles: roles};
  }
  function isSchemaRequiredEdge(edge, node, direction) {
    if (!node) return isDefaultDescriptionPredicate(edge.predicate || edge.label);
    const schemaPredicates = schemaRequiredPredicates(node, direction);
    if (schemaPredicates.length === 0) return isDefaultDescriptionPredicate(edge.predicate || edge.label);
    return schemaPredicates.some(function(predicate) { return predicateMatches(edge, predicate); });
  }
  function schemaRequiredPredicates(node, direction) {
    const schema = node.schema || {};
    let values = [];
    [
      node.requiredPredicates,
      node.required_predicates,
      node.descriptivePredicates,
      node.descriptive_predicates,
      schema.requiredPredicates,
      schema.required_predicates,
      schema.descriptivePredicates,
      schema.descriptive_predicates,
      schema[direction + 'RequiredPredicates'],
      schema[direction + '_required_predicates']
    ].forEach(function(item) { values = values.concat(asArray(item)); });
    return values.map(function(value) { return String(value); }).filter(Boolean);
  }
  function schemaInterpretation(node) {
    const schema = node.schema || {};
    const profile = currentPredicateProfile();
    const required = uniqueStrings([node.requiredPredicates, node.required_predicates, schema.requiredPredicates, schema.required_predicates]);
    const descriptive = uniqueStrings([node.descriptivePredicates, node.descriptive_predicates, schema.descriptivePredicates, schema.descriptive_predicates]);
    const outgoing = uniqueStrings([schema.outgoingRequiredPredicates, schema.outgoing_required_predicates]);
    const incoming = uniqueStrings([schema.incomingRequiredPredicates, schema.incoming_required_predicates]);
    const fallback = required.length + descriptive.length + outgoing.length + incoming.length === 0;
    const schemaPredicates = uniqueStrings([required, descriptive, outgoing, incoming]);
    return {
      profile: profile.name || 'cncf-rdf-1.5-hop-v1',
      entityType: node.node_type || node.type || 'unknown',
      category: node.category || '-',
      predicateRoles: fallback ? Object.keys(profile.roles || {}).sort() : predicateRoles(schemaPredicates, profile),
      identityPredicates: profileRolePredicates(profile, 'identity'),
      linkPredicates: profileRolePredicates(profile, 'link'),
      hierarchyPredicates: profileRolePredicates(profile, 'hierarchy'),
      provenancePredicates: profileRolePredicates(profile, 'provenance'),
      requiredPredicates: required,
      descriptivePredicates: descriptive,
      outgoingRequiredPredicates: outgoing,
      incomingRequiredPredicates: incoming,
      fallback: fallback,
      expansion: fallback ? 'predicate profile fallback' : 'node schema metadata'
    };
  }
  function defaultPredicateProfile() {
    return {
      name: 'cncf-rdf-1.5-hop-v1',
      roles: {
        identity: [
          'rdf:type',
          'owl:sameAs',
          'schema:sameAs',
          'skos:exactMatch',
          'skos:closeMatch',
          'textus:primaryRdfAnchor'
        ],
        descriptive: [
          'rdfs:label',
          'rdfs:comment',
          'skos:prefLabel',
          'skos:altLabel',
          'skos:definition',
          'schema:name',
          'schema:title',
          'schema:description',
          'schema:summary'
        ],
        link: [
          'rdfs:seeAlso',
          'schema:about',
          'schema:url',
          'schema:memberOf'
        ],
        hierarchy: [
          'skos:broader',
          'skos:narrower',
          'dcterms:isPartOf',
          'dcterms:hasPart',
          'schema:isPartOf',
          'schema:hasPart'
        ],
        provenance: [
          'dcterms:source',
          'prov:wasDerivedFrom',
          'prov:generatedAtTime',
          'rdfs:isDefinedBy'
        ]
      }
    };
  }
  function activePredicateProfile(data) {
    const base = defaultPredicateProfile();
    const configured = (data && data.predicateProfile) || {};
    const roles = {};
    Object.keys(base.roles).forEach(function(role) {
      roles[role] = configured.roles && Object.prototype.hasOwnProperty.call(configured.roles, role) ?
        uniqueStrings([base.roles[role], configured.roles[role]]) :
        uniqueStrings([base.roles[role]]);
    });
    if (configured.roles) {
      Object.keys(configured.roles).forEach(function(role) {
        if (!roles[role]) roles[role] = uniqueStrings([configured.roles[role]]);
      });
    }
    return {
      name: configured.name || base.name,
      roles: roles
    };
  }
  function currentPredicateProfile() {
    return window.__bokRdfPredicateProfile || defaultPredicateProfile();
  }
  function profileRolePredicates(profile, role) {
    return uniqueStrings([profile && profile.roles && profile.roles[role]]);
  }
  function profilePredicates(profile) {
    const roles = (profile && profile.roles) || {};
    return uniqueStrings(Object.keys(roles).map(function(role) { return roles[role]; }));
  }
  function predicateRoles(predicates, profile) {
    const roles = (profile && profile.roles) || {};
    const result = [];
    Object.keys(roles).sort().forEach(function(role) {
      const rolePredicates = profileRolePredicates(profile, role);
      const matched = predicates.some(function(predicate) {
        return rolePredicates.some(function(candidate) {
          return predicateKey(predicate) === predicateKey(candidate);
        });
      });
      if (matched) result.push(role);
    });
    return result;
  }
  function uniqueStrings(values) {
    const seen = new Set();
    const result = [];
    values.forEach(function(item) {
      asArray(item).forEach(function(value) {
        const text = String(value || '').trim();
        if (text && !seen.has(text)) {
          seen.add(text);
          result.push(text);
        }
      });
    });
    return result;
  }
  function renderSchemaInterpretation(node) {
    const interpretation = schemaInterpretation(node);
    return '<div class="bok-rdf-node-schema">' +
      '<strong>${_javascript_string(_ui(locale, "rdf.graph.node.schema"))}</strong>' +
      '<dl class="bok-rdf-node-schema-object">' +
        schemaProperty('profile', interpretation.profile) +
        schemaProperty('entityType', interpretation.entityType) +
        schemaProperty('category', interpretation.category) +
        schemaProperty('expansion', interpretation.expansion) +
        schemaProperty('predicateRoles', interpretation.predicateRoles) +
        schemaProperty('identityPredicates', interpretation.identityPredicates) +
        schemaProperty('linkPredicates', interpretation.linkPredicates) +
        schemaProperty('hierarchyPredicates', interpretation.hierarchyPredicates) +
        schemaProperty('provenancePredicates', interpretation.provenancePredicates) +
        schemaProperty('requiredPredicates', interpretation.requiredPredicates) +
        schemaProperty('descriptivePredicates', interpretation.descriptivePredicates) +
        schemaProperty('outgoingRequiredPredicates', interpretation.outgoingRequiredPredicates) +
        schemaProperty('incomingRequiredPredicates', interpretation.incomingRequiredPredicates) +
        schemaProperty('fallback', interpretation.fallback ? 'true' : 'false') +
      '</dl>' +
    '</div>';
  }
  function schemaProperty(name, value) {
    const rendered = Array.isArray(value) ? (value.length ? value.map(function(item) {
      return '<code>' + escapeHtml(item) + '</code>';
    }).join(' ') : '<em>-</em>') : '<code>' + escapeHtml(value) + '</code>';
    return '<dt>' + escapeHtml(name) + '</dt><dd>' + rendered + '</dd>';
  }
  function asArray(value) {
    if (Array.isArray(value)) return value;
    if (value == null) return [];
    return [value];
  }
  function predicateMatches(edge, predicate) {
    const expected = predicateKey(predicate);
    return predicateKey(edge.predicate) === expected || predicateKey(edge.label) === expected;
  }
  function isDefaultDescriptionPredicate(predicate) {
    const key = predicateKey(predicate);
    return profilePredicates(currentPredicateProfile()).some(function(candidate) {
      return predicateKey(candidate) === key;
    });
  }
  function predicateKey(value) {
    return String(value || '').split(/[\\/#:]/).filter(Boolean).pop().toLowerCase().replace(/[^a-z0-9]/g, '');
  }
  function edgeKey(edge) {
    return String(edge.source || '') + '\\n' + String(edge.predicate || edge.label || '') + '\\n' + String(edge.target || '');
  }
  function showNodeDetail(node, edges) {
    const canvas = graphTarget.querySelector('[data-rdf-graph-canvas]');
    if (!canvas || !node) return;
    let panel = canvas.querySelector('.bok-rdf-node-popover');
    if (!panel) {
      panel = document.createElement('aside');
      panel.setAttribute('class', 'bok-rdf-node-popover');
      panel.setAttribute('role', 'dialog');
      panel.setAttribute('aria-label', '${_javascript_string(_ui(locale, "rdf.graph.node.detail"))}');
      canvas.appendChild(panel);
    }
    const related = edges.filter(function(edge) { return edge.source === node.id || edge.target === node.id; }).slice(0, 6);
    panel.hidden = false;
    panel.innerHTML =
      '<button type="button" class="bok-rdf-node-popover-close" data-rdf-node-popover-close="true" aria-label="${_javascript_string(_ui(locale, "rdf.graph.node.close"))}">×</button>' +
      '<div class="bok-rdf-node-popover-eyebrow">${_javascript_string(_ui(locale, "rdf.graph.node.detail"))}</div>' +
      '<h2>' + escapeHtml(node.label || label(node.id)) + '</h2>' +
      '<dl>' +
        '<dt>ID</dt><dd title="' + escapeHtml(node.id) + '">' + escapeHtml(node.id) + '</dd>' +
        '<dt>Category</dt><dd>' + escapeHtml(node.category || '-') + '</dd>' +
        '<dt>Type</dt><dd>' + escapeHtml(node.node_type || node.type || '-') + '</dd>' +
        '<dt>Degree</dt><dd>' + escapeHtml(node.degree == null ? '-' : node.degree) + '</dd>' +
      '</dl>' +
      renderSchemaInterpretation(node) +
      '<div class="bok-rdf-node-popover-actions"><button type="button" data-rdf-neighborhood="true">${_javascript_string(_ui(locale, "rdf.graph.node.neighborhood"))}</button></div>' +
      '<div class="bok-rdf-node-popover-relations"><strong>${_javascript_string(_ui(locale, "rdf.graph.node.relations"))}</strong><ul>' +
        (related.length ? related.map(function(edge) { return '<li>' + escapeHtml(label(edge.source)) + ' <b>' + escapeHtml(edge.label || label(edge.predicate)) + '</b> ' + escapeHtml(label(edge.target)) + '</li>'; }).join('') : '<li>-</li>') +
      '</ul></div>';
    const close = panel.querySelector('[data-rdf-node-popover-close]');
    if (close) close.addEventListener('click', function() { panel.hidden = true; });
    const focusButton = panel.querySelector('[data-rdf-neighborhood]');
    if (focusButton) focusButton.addEventListener('click', function() {
      focusedNodeId = node.id;
      renderGraph(window.__bokRdfGraphData, input ? input.value.trim() : '', termInput ? termInput.value.trim() : '', window.__bokRdfTermIndex || {});
    });
  }
  function renderGraphSvg(canvas, nodes, edges, roles, focus) {
    const svgNs = "http://www.w3.org/2000/svg";
    const width = 1120;
    const height = 660;
    const svg = document.createElementNS("http://www.w3.org/2000/svg", "svg");
    svg.setAttribute('class', 'bok-rdf-graph-svg');
    svg.setAttribute('viewBox', '0 0 ' + width + ' ' + height);
    svg.setAttribute('role', 'img');
    svg.setAttribute('aria-label', 'RDF graph');
    const defs = document.createElementNS(svgNs, 'defs');
    const marker = document.createElementNS(svgNs, 'marker');
    marker.setAttribute('id', 'bok-rdf-arrow');
    marker.setAttribute('viewBox', '0 0 10 10');
    marker.setAttribute('refX', '8');
    marker.setAttribute('refY', '5');
    marker.setAttribute('markerWidth', '6');
    marker.setAttribute('markerHeight', '6');
    marker.setAttribute('orient', 'auto-start-reverse');
    const arrow = document.createElementNS(svgNs, 'path');
    arrow.setAttribute('d', 'M 0 0 L 10 5 L 0 10 z');
    arrow.setAttribute('class', 'bok-rdf-graph-arrow');
    marker.appendChild(arrow);
    defs.appendChild(marker);
    svg.appendChild(defs);
    const positions = {};
    const cx = width / 2;
    const cy = height / 2;
    const rx = width * 0.38;
    const ry = height * 0.32;
    nodes.forEach(function(node, index) {
      const angle = nodes.length === 1 ? -Math.PI / 2 : (2 * Math.PI * index / nodes.length) - Math.PI / 2;
      positions[node.id] = {
        x: nodes.length === 1 ? cx : cx + Math.cos(angle) * rx,
        y: nodes.length === 1 ? cy : cy + Math.sin(angle) * ry
      };
    });
    const edgeLayer = document.createElementNS(svgNs, 'g');
    edgeLayer.setAttribute('class', 'bok-rdf-graph-edges');
    edges.forEach(function(edge) {
      const source = positions[edge.source];
      const target = positions[edge.target];
      if (!source || !target) return;
      const line = document.createElementNS(svgNs, 'line');
      line.setAttribute('class', 'bok-rdf-graph-edge');
      line.setAttribute('x1', source.x);
      line.setAttribute('y1', source.y);
      line.setAttribute('x2', target.x);
      line.setAttribute('y2', target.y);
      line.setAttribute('marker-end', 'url(#bok-rdf-arrow)');
      edgeLayer.appendChild(line);
      const text = document.createElementNS(svgNs, 'text');
      text.setAttribute('class', 'bok-rdf-graph-edge-label');
      text.setAttribute('x', (source.x + target.x) / 2);
      text.setAttribute('y', (source.y + target.y) / 2 - 6);
      text.textContent = label(edge.label || edge.predicate || 'related');
      edgeLayer.appendChild(text);
    });
    svg.appendChild(edgeLayer);
    const nodeLayer = document.createElementNS(svgNs, 'g');
    nodeLayer.setAttribute('class', 'bok-rdf-graph-nodes');
    nodes.forEach(function(node) {
      const point = positions[node.id];
      const group = document.createElementNS(svgNs, 'g');
      const role = roles[node.id] || 'normal';
      group.setAttribute('class', 'bok-rdf-graph-node bok-rdf-graph-node-' + cssName(node.node_type || node.type || 'unknown') + ' bok-rdf-graph-node-role-' + role);
      group.setAttribute('transform', 'translate(' + point.x + ' ' + point.y + ')');
      group.setAttribute('data-rdf-focus-node', node.id);
      group.setAttribute('tabindex', '0');
      group.setAttribute('role', 'button');
      const circle = document.createElementNS(svgNs, 'circle');
      circle.setAttribute('r', Math.max(18, Math.min(34, 18 + (node.degree || 0) * 2)));
      const title = document.createElementNS(svgNs, 'title');
      title.textContent = (node.label || label(node.id)) + ' / ' + (node.category || '-');
      const text = document.createElementNS(svgNs, 'text');
      text.setAttribute('y', 48);
      text.textContent = label(node.label || node.id);
      group.appendChild(title);
      group.appendChild(circle);
      group.appendChild(text);
      group.addEventListener('click', function() { showNodeDetail(node, edges); });
      group.addEventListener('keydown', function(event) { if (event.key === 'Enter' || event.key === ' ') { event.preventDefault(); showNodeDetail(node, edges); } });
      nodeLayer.appendChild(group);
    });
    svg.appendChild(nodeLayer);
    canvas.innerHTML = '';
    canvas.appendChild(svg);
  }
  function renderTriples(text) {
       |    if (!triplesTarget || !triplesStatus) return;
       |    triplesTarget.textContent = text;
       |    triplesStatus.textContent = text.split('\\n').filter(function(line) { return line.trim(); }).length + ' lines';
       |  }
       |  function label(value) {
       |    const parts = String(value || '').split(/[\\/#]/).filter(Boolean);
       |    return parts.length ? parts[parts.length - 1] : value;
       |  }
       |  function escapeHtml(value) {
       |    return String(value == null ? '' : value).replace(/[&<>"']/g, function(c) {
       |      return {'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c];
       |    });
       |  }
       |  function cssName(value) {
       |    return String(value == null ? 'unknown' : value).toLowerCase().replace(/[^a-z0-9_-]+/g, '-');
       |  }
       |  const graphPromise = fetch(root.getAttribute('data-graph')).then(function(response) {
       |    if (!response.ok) throw new Error('missing graph metadata');
       |    return response.json();
       |  });
       |  const termsPromise = fetch(root.getAttribute('data-terms')).then(function(response) {
       |    if (!response.ok) return {terms: []};
       |    return response.json();
       |  }).catch(function() { return {terms: []}; });
       |  Promise.all([graphPromise, termsPromise]).then(function(results) {
       |    const data = results[0];
       |    const terms = results[1];
       |    const termIndex = {};
       |    (terms.terms || []).forEach(function(term) {
       |      if (term && term.id) termIndex[term.id] = term;
       |    });
       |    window.__bokRdfGraphData = data;
       |    window.__bokRdfTermIndex = termIndex;
       |    window.__bokRdfPredicateProfile = activePredicateProfile(data);
       |    renderGraph(data, initialCategory, initialTerm, termIndex);
       |    function refresh() { focusedNodeId = null; renderGraph(data, input ? input.value.trim() : '', termInput ? termInput.value.trim() : '', termIndex); }
       |    if (input) input.addEventListener('input', refresh);
       |    if (termInput) termInput.addEventListener('input', refresh);
       |  }).catch(function() {
       |    graphStatus.textContent = '${_javascript_string(_ui(locale, "rdf.graph.metadata.missing"))}';
       |    graphTarget.innerHTML = '<div class="bok-rdf-empty">${_javascript_string(_ui(locale, "rdf.graph.metadata.missing"))}</div>';
       |  });
       |  if (triplesTarget) {
       |    fetch(root.getAttribute('data-triples')).then(function(response) {
       |      if (!response.ok) throw new Error('missing RDF triples');
       |      return response.text();
       |    }).then(renderTriples).catch(function() {
       |      if (triplesStatus) triplesStatus.textContent = '${_javascript_string(_ui(locale, "rdf.graph.triples.missing"))}';
       |      triplesTarget.textContent = '';
       |    });
       |  }
       |}());""".stripMargin

  private def _glossary_metric_cards(
    categorycount: Int,
    categorieswithterms: Int,
    termcount: Int
  ): String =
    s"""<div class="bok-dashboard-grid">
       |  <div class="bok-metric-card">
       |    <div class="bok-metric-label">Terms</div>
       |    <div class="bok-metric-value">${termcount}</div>
       |    <div class="bok-metric-note">Total glossary terms</div>
       |  </div>
       |  <div class="bok-metric-card">
       |    <div class="bok-metric-label">Groups</div>
       |    <div class="bok-metric-value">${categorycount}</div>
       |    <div class="bok-metric-note">Category groups</div>
       |  </div>
       |  <div class="bok-metric-card">
       |    <div class="bok-metric-label">Active Groups</div>
       |    <div class="bok-metric-value">${categorieswithterms}</div>
       |    <div class="bok-metric-note">Groups with terms</div>
       |  </div>
       |</div>""".stripMargin

  private def _language_index_root_prefix(config: BuildConfig): String =
    config.localeMode match {
      case LocaleMode.SingleLocaleRoot => "../"
      case LocaleMode.MultiLocaleSubdirs => "../../"
    }

  private def _glossary_language_links(config: BuildConfig, rootprefix: String): String = {
    val langs = (config.languages ++ Vector("ja", "en")).distinct.filter(x => x == "ja" || x == "en")
    langs.map {
      case "ja" => s"""<a class="bok-special-link" href="${rootprefix}ja/glossary/index.html">日本語索引ページ</a>"""
      case "en" => s"""<a class="bok-special-link" href="${rootprefix}en/glossary/index.html">英語索引ページ</a>"""
      case other => s"""<a class="bok-special-link" href="${rootprefix}${_html_escape(other)}/glossary/index.html">${_html_escape(other)} index page</a>"""
    }.mkString("""<div class="bok-special-links">""", "\n", "</div>")
  }

  private def _glossary_recent_terms(terms: Vector[TermEntry]): String =
    if (terms.isEmpty)
      "<p>No glossary terms yet.</p>"
    else
      terms.take(10).map { term =>
        s"""<li><a href="${_html_escape(term.glossaryHref)}">${_html_escape(term.title)}</a>${_reading_label(term)}: ${_html_escape(term.categorySlug)}</li>"""
      }.mkString("<ol>\n", "\n", "\n</ol>")

  private def _glossary_index_href(href: String): String =
    href.stripPrefix("../glossary/").stripPrefix("glossary/")

  private def _terms(config: BuildConfig, categories: Vector[CategoryContent]): Vector[TermEntry] =
    _term_index(config).map(_.terms).filter(_.nonEmpty).getOrElse(_fallback_terms(categories))

  private def _term_index(config: BuildConfig): Option[TermIndex] = {
    val path = config.doxsitePath.resolve("metadata/glossary/terms.json")
    if (!Files.isRegularFile(path))
      None
    else
      parser.parse(Files.readString(path, StandardCharsets.UTF_8)).toOption.flatMap(_.as[TermIndex].toOption)
  }

  private def _fallback_terms(categories: Vector[CategoryContent]): Vector[TermEntry] =
    categories.flatMap { category =>
      category.terms.map { item =>
        val slug = item.href.split('/').filter(_.nonEmpty).lastOption.getOrElse(item.title).stripSuffix(".html")
        TermEntry(
          s"${category.slug}:${slug}",
          slug,
          item.title,
          item.reading,
          Some(category.slug),
          s"glossary/${category.slug}/${slug}.dox",
          s"glossary/${category.slug}/${slug}.html",
          s"<p>${_html_escape(item.brief)}</p>",
          Some(item.brief),
          Vector.empty,
          Vector.empty,
          Vector.empty,
          Vector.empty,
          Vector.empty,
          TermQuality(isolated = true, unreferenced = true, weaklyconnected = true)
        )
      }
    }.sortBy(x => (x.categorySlug, x.slug))

  private def _term_group_cards(locale: String, terms: Vector[TermEntry], categories: Vector[CategoryContent]): String = {
    val titles = categories.map(x => x.slug -> x.title).toMap
    val cards = terms.groupBy(_.categorySlug).toVector.sortBy(_._1).map {
      case (category, xs) =>
        val title = titles.getOrElse(category, category)
        val links = xs.sortBy(_.title).take(8).map { term =>
          s"""<li><a href="${_html_escape(term.glossaryHref)}">${_html_escape(term.title)}</a>${_reading_label(term)} <a class="bok-term-rdf-mini" href="${_html_escape(term.rdfHrefFromGlossary)}">RDF</a></li>"""
        }.mkString("<ul>", "", "</ul>")
        val more = if (xs.size > 8) s"""<div class="bok-more">${_html_escape(_uif(locale, "dashboard.more", xs.size - 8))}</div>""" else ""
        s"""<div class="bok-term-group-card"><h3><a href="../${_html_escape(category)}/index.html">${_html_escape(title)}</a></h3>${links}${more}</div>"""
    }.mkString("\n")
    s"""<div class="bok-term-group-grid">${cards}</div>"""
  }

  private def _write_term_hub_pages(
    config: BuildConfig,
    target: Path,
    locale: String,
    categories: Vector[CategoryContent],
    terms: Vector[TermEntry]
  ): Unit =
    terms.foreach { term =>
      val page = target.resolve(term.publicPath)
      _write_text(page, _term_hub_page(config, categories, locale, page, term))
    }

  private def _term_hub_page(
    config: BuildConfig,
    categories: Vector[CategoryContent],
    locale: String,
    page: Path,
    term: TermEntry
  ): String =
    s"""<!doctype html>
       |<html lang="${_html_escape(locale)}">
       |<head>
       |  <meta charset="utf-8">
       |  <meta name="viewport" content="width=device-width, initial-scale=1">
       |  <title>${_html_escape(term.title)} - ${_html_escape(config.siteTitle)}</title>
       |${_site_css_links(config, page)}
       |</head>
       |<body class="article ${_html_escape(_dashboard_theme_class(config))}">
       |${_category_header(config, categories, locale, "../../")}
       |<div class="body body-dashboard bok-term-hub-body">
       |  <main class="article">
       |    <div class="content">
       |      <article class="doc">
       |        ${_term_hub(term, locale)}
       |      </article>
       |    </div>
       |  </main>
       |</div>
       |</body>
       |</html>
       |""".stripMargin

  private def _term_hub(term: TermEntry, locale: String): String =
    s"""<section class="bok-dashboard-shell bok-term-hub" id="term-hub">
       |  <header class="bok-dashboard-hero">
       |    <div class="bok-dashboard-hero-copy">
       |      <p class="bok-dashboard-eyebrow">${_html_escape(_ui(locale, "term.hub.eyebrow"))}</p>
       |      <h1 class="page">${_html_escape(term.title)}</h1>
       |      <p class="bok-dashboard-lead">${_html_escape(term.summary.getOrElse(_ui(locale, "term.hub.description")))}</p>
       |    </div>
       |    <div class="bok-dashboard-hero-facts">
       |      <span class="bok-dashboard-hero-fact"><strong>${_html_escape(term.categorySlug)}</strong><em>${_html_escape(_ui(locale, "dashboard.matrix.category"))}</em></span>
       |      <span class="bok-dashboard-hero-fact"><strong>${term.rdfRefs.size}</strong><em>RDF</em></span>
       |      <span class="bok-dashboard-hero-fact"><strong>${term.termRefs.size}</strong><em>${_html_escape(_ui(locale, "term.related.terms"))}</em></span>
       |    </div>
       |  </header>
       |  <div class="bok-dashboard container-fluid bok-dashboard-command-center">
       |    <div class="row g-3">
       |      ${_dashboard_card("col-12 col-xl-7", "bok-card-purpose bok-card-term-definition", _ui(locale, "term.definition"), _term_definition_body(term))}
       |      ${_dashboard_card("col-12 col-xl-5", "bok-card-readiness", _ui(locale, "term.quality"), _term_quality_body(term, locale))}
       |      ${_dashboard_card("col-12 col-xl-6", "bok-card-related", _ui(locale, "term.rdf.resources"), _term_rdf_refs_body(term, locale))}
       |      ${_dashboard_card("col-12 col-xl-3", "bok-card-map", _ui(locale, "term.related.articles"), _term_refs_body(term.articleRefs, locale))}
       |      ${_dashboard_card("col-12 col-xl-3", "bok-card-map", _ui(locale, "term.related.terms"), _term_refs_body(term.termRefs, locale))}
       |      ${_dashboard_card("col-12 col-xl-3", "bok-card-map", _ui(locale, "term.related.videos"), _term_refs_body(term.videoRefs, locale))}
       |      ${_dashboard_card("col-12 col-xl-3", "bok-card-actions", _ui(locale, "dashboard.card.next.actions"), _term_actions_body(term, locale))}
       |    </div>
       |  </div>
       |</section>""".stripMargin

  private def _term_definition_body(term: TermEntry): String = {
    val reading = term.reading.filterNot(_ == term.title).map(x => s"""<p class="bok-term-reading-large">${_html_escape(x)}</p>""").getOrElse("")
    s"""${reading}<div class="bok-term-definition-html">${term.definitionHtml}</div>"""
  }

  private def _term_quality_body(term: TermEntry, locale: String): String = {
    val flags = Vector(
      term.quality.isolated -> _ui(locale, "term.quality.isolated"),
      term.quality.unreferenced -> _ui(locale, "term.quality.unreferenced"),
      term.quality.weaklyconnected -> _ui(locale, "term.quality.weakly.connected")
    ).collect { case (true, label) => label }
    if (flags.isEmpty)
      s"""<p class="bok-card-muted">${_html_escape(_ui(locale, "term.quality.ok"))}</p>"""
    else
      flags.map(x => s"""<li class="list-group-item"><span class="badge bok-badge-info">info</span>${_html_escape(x)}</li>""").mkString("""<ul class="list-group bok-alert-list">""", "", "</ul>")
  }

  private def _term_rdf_refs_body(term: TermEntry, locale: String): String =
    if (term.rdfRefs.isEmpty)
      s"""<p class="bok-card-muted">${_html_escape(_ui(locale, "term.rdf.empty"))}</p>"""
    else
      term.rdfRefs.take(8).map { ref =>
        val predicate = ref.predicate.map(x => s" <small>${_html_escape(_short_uri_label(x))}</small>").getOrElse("")
        s"""<li class="list-group-item"><span>${_html_escape(ref.label)}</span>${predicate}<em>${_html_escape(ref.direction)}</em></li>"""
      }.mkString("""<ul class="list-group bok-map-list">""", "", "</ul>")

  private def _term_refs_body(refs: Vector[TermReference], locale: String): String =
    if (refs.isEmpty)
      s"""<p class="bok-card-muted">${_html_escape(_ui(locale, "term.refs.empty"))}</p>"""
    else
      refs.take(6).map(x => s"""<li class="list-group-item"><a href="${_html_escape(x.path)}">${_html_escape(x.title)}</a><span>${_html_escape(x.relation)}</span></li>""").mkString("""<ul class="list-group bok-map-list">""", "", "</ul>")

  private def _term_actions_body(term: TermEntry, locale: String): String =
    Vector(
      _ui(locale, "term.action.open.rdf") -> term.rdfHrefFromTerm,
      _ui(locale, "glossary.title") -> "../../glossary/index.html"
    ).map { case (label, href) => s"""<li><a href="${_html_escape(href)}">${_html_escape(label)}</a></li>""" }.mkString("""<ol class="bok-action-list">""", "", "</ol>")

  private def _short_uri_label(value: String): String = {
    val a = value.split('#').lastOption.getOrElse(value)
    a.split('/').filter(_.nonEmpty).lastOption.getOrElse(a)
  }

  private def _localized_glossary_index_page(
    config: BuildConfig,
    categories: Vector[CategoryContent],
    lang: String
  ): String = {
    val terms = _localized_glossary_terms(lang, categories.flatMap { category =>
      category.terms.map(term => category -> term)
    })
    val title = lang match {
      case "ja" => "日本語 Glossary 索引"
      case "en" => "English Glossary 索引"
      case other => s"${other} Glossary 索引"
    }
    val indexnav = _localized_glossary_index_nav(lang, terms)
    val sections = _localized_glossary_index_sections(lang, terms)
    s"""<!doctype html>
       |<html lang="${_html_escape(lang)}">
       |<head>
       |  <meta charset="utf-8">
       |  <meta name="viewport" content="width=device-width, initial-scale=1">
       |  <title>${_html_escape(title)} - ${_html_escape(config.siteTitle)}</title>
       |  <link rel="stylesheet" href="../../_/css/bootstrap-grid.min.css">
       |  <link rel="stylesheet" href="../../_/css/site.css">
       |  <link rel="stylesheet" href="../../_/css/cozy-bok-dashboard.css">
       |</head>
       |<body class="article ${_html_escape(_dashboard_theme_class(config))}">
       |<div class="body">
       |  <main class="article">
       |    <div class="toolbar" role="navigation">
       |      <a href="../../index.html" class="home-link"></a>
       |      <nav class="breadcrumbs" aria-label="breadcrumbs">
       |        <ul>
       |          <li><a href="../../index.html">${_html_escape(config.siteTitle)}</a></li>
       |          <li><a href="../../glossary/index.html">Glossary</a></li>
       |          <li>${_html_escape(title)}</li>
       |        </ul>
       |      </nav>
       |    </div>
       |    <div class="content">
       |      ${_localized_glossary_toc_panel(config)}
       |      <article class="doc">
       |        <h1 class="page">${_html_escape(title)}</h1>
       |        <p>This page is a language-specific entry point for glossary browsing.</p>
       |        <div class="sect1" id="index">
       |          <h2>索引</h2>
       |          <div class="sectionbody">
       |            ${indexnav}
       |          </div>
       |        </div>
       |        <div class="sect1" id="terms">
       |          <h2>Terms</h2>
       |          <div class="sectionbody">
       |            ${sections}
       |          </div>
       |        </div>
       |      </article>
       |    </div>
       |  </main>
       |</div>
       |</body>
       |</html>
       |""".stripMargin
  }

  private def _localized_glossary_terms(
    lang: String,
    terms: Vector[(CategoryContent, CategoryPageItem)]
  ): Vector[(CategoryContent, CategoryPageItem)] =
    lang match {
      case "ja" => terms.filter { case (_, term) => _has_japanese_character(_ja_index_text(term)) }
      case _ => terms
    }

  private def _localized_glossary_index_nav(
    lang: String,
    terms: Vector[(CategoryContent, CategoryPageItem)]
  ): String = {
    val grouped = terms.groupBy { case (_, term) => _localized_index_key(lang, term) }
    _localized_index_keys(lang).map { key =>
      if (grouped.get(key).exists(_.nonEmpty))
        s"""<a class="bok-index-link" href="#${_html_escape(_localized_index_anchor(key))}">${_html_escape(key)}</a>"""
      else
        s"""<span class="bok-index-link is-disabled">${_html_escape(key)}</span>"""
    }.mkString("""<div class="bok-index-nav">""", "\n", "</div>")
  }

  private def _localized_glossary_index_sections(
    lang: String,
    terms: Vector[(CategoryContent, CategoryPageItem)]
  ): String =
    if (terms.isEmpty)
      "<p>No glossary terms yet.</p>"
    else {
      val keys = _localized_index_keys(lang)
      val grouped = terms.groupBy { case (_, term) => _localized_index_key(lang, term) }
      val activekeys = keys.filter(key => grouped.get(key).exists(_.nonEmpty))
      activekeys.map { key =>
        val items = grouped.getOrElse(key, Vector.empty).sortBy { case (category, term) =>
          (_localized_index_sort_key(lang, term), category.slug)
        }.map {
          case (category, term) =>
            val href = s"../../glossary/${_glossary_index_href(term.href)}"
            s"""<li><a href="${_html_escape(href)}">${_html_escape(term.title)}</a>${_reading_label(term)}: ${_html_escape(category.title)}</li>"""
        }.mkString("<ul>\n", "\n", "\n</ul>")
        s"""<section class="bok-index-section" id="${_html_escape(_localized_index_anchor(key))}">
           |  <h3>${_html_escape(key)}</h3>
           |  ${items}
           |</section>""".stripMargin
      }.mkString("\n")
    }

  private def _localized_index_keys(lang: String): Vector[String] =
    lang match {
      case "ja" =>
        "あいうえおかきくけこさしすせそたちつてとなにぬねのはひふへほまみむめもやゆよらりるれろわをん".map(_.toString).toVector :+ "その他"
      case "en" =>
        ('A' to 'Z').map(_.toString).toVector :+ "Other"
      case _ =>
        ('A' to 'Z').map(_.toString).toVector :+ "Other"
    }

  private def _localized_index_key(lang: String, term: CategoryPageItem): String =
    lang match {
      case "ja" => _ja_index_key(_ja_index_text(term))
      case "en" => _en_index_key(term.title)
      case _ => _en_index_key(term.title)
    }

  private def _localized_index_sort_key(lang: String, term: CategoryPageItem): String =
    lang match {
      case "ja" => _ja_index_text(term)
      case _ => term.title.toLowerCase(java.util.Locale.ROOT)
    }

  private def _ja_index_text(term: CategoryPageItem): String =
    term.reading.getOrElse(term.title)

  private def _reading_label(term: CategoryPageItem): String =
    term.reading.filterNot(_ == term.title).map { reading =>
      s""" <span class="bok-term-reading">(${_html_escape(reading)})</span>"""
    }.getOrElse("")

  private def _reading_label(term: TermEntry): String =
    term.reading.filterNot(_ == term.title).map { reading =>
      s""" <span class="bok-term-reading">(${_html_escape(reading)})</span>"""
    }.getOrElse("")

  private def _localized_index_anchor(key: String): String =
    key match {
      case "その他" => "index-other"
      case "Other" => "index-other"
      case other => s"index-${other.toLowerCase(java.util.Locale.ROOT)}"
    }

  private def _ja_index_key(title: String): String = {
    val keys = _localized_index_keys("ja").toSet
    title.trim.headOption.map(_normalize_kana).flatMap { ch =>
      val value = ch.toString
      if (keys.contains(value))
        Some(value)
      else
        _voiced_kana_base.get(ch).map(_.toString).filter(keys.contains)
    }.getOrElse("その他")
  }

  private def _en_index_key(title: String): String =
    title.trim.find(_.isLetter).map(_.toUpper).filter(ch => ch >= 'A' && ch <= 'Z').
      map(_.toString).getOrElse("Other")

  private def _has_japanese_character(value: String): Boolean =
    value.exists(ch =>
      (ch >= '\u3040' && ch <= '\u309f') ||
      (ch >= '\u30a0' && ch <= '\u30ff') ||
      (ch >= '\u4e00' && ch <= '\u9fff')
    )

  private def _normalize_kana(ch: Char): Char =
    if (ch >= '\u30a1' && ch <= '\u30f6')
      (ch - 0x60).toChar
    else
      ch match {
        case 'ぁ' => 'あ'
        case 'ぃ' => 'い'
        case 'ぅ' => 'う'
        case 'ぇ' => 'え'
        case 'ぉ' => 'お'
        case 'ゃ' => 'や'
        case 'ゅ' => 'ゆ'
        case 'ょ' => 'よ'
        case 'ゎ' => 'わ'
        case other => other
      }

  private val _voiced_kana_base: Map[Char, Char] =
    Map(
      'が' -> 'か', 'ぎ' -> 'き', 'ぐ' -> 'く', 'げ' -> 'け', 'ご' -> 'こ',
      'ざ' -> 'さ', 'じ' -> 'し', 'ず' -> 'す', 'ぜ' -> 'せ', 'ぞ' -> 'そ',
      'だ' -> 'た', 'ぢ' -> 'ち', 'づ' -> 'つ', 'で' -> 'て', 'ど' -> 'と',
      'ば' -> 'は', 'び' -> 'ひ', 'ぶ' -> 'ふ', 'べ' -> 'へ', 'ぼ' -> 'ほ',
      'ぱ' -> 'は', 'ぴ' -> 'ひ', 'ぷ' -> 'ふ', 'ぺ' -> 'へ', 'ぽ' -> 'ほ'
    )

  private def _special_html_page(
    config: BuildConfig,
    categories: Vector[CategoryContent],
    locale: String,
    page: Path,
    title: String,
    description: String,
    body: String
  ): String =
    _special_html_page_with_toc(
      config,
      categories,
      locale,
      page,
      title,
      description,
      body,
      Vector("dashboard" -> "Dashboard", "term-groups" -> "Term Groups", "language-index" -> "Language Index", "recent-terms" -> "Recent Terms")
    )

  private def _special_html_page_with_toc(
    config: BuildConfig,
    categories: Vector[CategoryContent],
    locale: String,
    page: Path,
    title: String,
    description: String,
    body: String,
    tocitems: Vector[(String, String)]
  ): String =
    s"""<!doctype html>
       |<html lang="${_html_escape(locale)}">
       |<head>
       |  <meta charset="utf-8">
       |  <meta name="viewport" content="width=device-width, initial-scale=1">
       |  <title>${_html_escape(title)} - ${_html_escape(config.siteTitle)}</title>
       |${_site_css_links(config, page)}
       |</head>
       |<body class="article ${_html_escape(_dashboard_theme_class(config))}">
       |${_category_header(config, categories, locale)}
       |<div class="body">
       |  ${_special_nav_container(config, categories)}
       |  <main class="article">
       |    <div class="toolbar" role="navigation">
       |      <button class="nav-toggle"></button>
       |      <a href="../index.html" class="home-link"></a>
       |      <nav class="breadcrumbs" aria-label="breadcrumbs">
       |        <ul>
       |          <li><a href="../index.html">${_html_escape(config.siteTitle)}</a></li>
       |          <li>${_html_escape(title)}</li>
       |        </ul>
       |      </nav>
       |    </div>
       |    <div class="content">
       |      ${_special_toc_panel(config, tocitems)}
       |      <article class="doc">
       |        <h1 class="page">${_html_escape(title)}</h1>
       |        <p>${_html_escape(description)}</p>
       |        <div class="sect1" id="dashboard">
       |          <h2>Dashboard</h2>
       |          <div class="sectionbody">
       |            <p>${_html_escape(_ui(locale, "special.console.description"))}</p>
       |          </div>
       |        </div>
       |        ${body}
       |      </article>
       |    </div>
       |  </main>
       |</div>
       |</body>
       |</html>
       |""".stripMargin

  private def _special_toc_panel(config: BuildConfig, tocitems: Vector[(String, String)]): String = {
    val items = tocitems.map {
      case (id, label) => s"""        <li><a href="#${_html_escape(id)}">${_html_escape(label)}</a></li>"""
    }.mkString("\n")
    s"""<aside class="toc sidebar" data-title="Contents" data-levels="2">
       |    <div class="toc-menu">
       |      <h3>On this page</h3>
       |      <ul>
       |${items}
       |      </ul>
       |      <div class="bok-special-links">
       |        <h3>BoK Console</h3>
       |        <a class="bok-special-link" href="../glossary/index.html">Glossary</a>
       |        <a class="bok-special-link" href="${_html_escape(_history_href(config, "../"))}">History</a>
       |        <a class="bok-special-link" href="../manual/index.html">Manual</a>
       |      </div>
       |    </div>
       |  </aside>""".stripMargin
  }

  private def _special_nav_container(config: BuildConfig, categories: Vector[CategoryContent]): String = {
    val items = categories.map { category =>
      s"""<li class="nav-item" data-depth="1"><a class="nav-link" href="../${_html_escape(category.slug)}/index.html">${_html_escape(category.title)}</a></li>"""
    }.mkString("\n                  ")
    s"""<div class="nav-container" data-component="home" data-version="">
       |    <aside class="nav">
       |      <div class="panels">
       |        <div class="nav-panel-menu is-active" data-panel="menu">
       |          <nav class="nav-menu">
       |            <button class="nav-menu-toggle" aria-label="Toggle expand/collapse all" style="display: none"></button>
       |            <h3 class="title"><a href="../index.html">${_html_escape(config.siteTitle)}</a></h3>
       |            <ul class="nav-list">
       |              <li class="nav-item" data-depth="0">
       |                <ul class="nav-list">
       |                  ${items}
       |                </ul>
       |              </li>
       |            </ul>
       |          </nav>
       |        </div>
       |      </div>
       |    </aside>
       |  </div>""".stripMargin
  }

  private def _glossary_toc_panel(config: BuildConfig): String =
    s"""<aside class="toc sidebar" data-title="Contents" data-levels="2">
      |    <div class="toc-menu">
      |      <h3>On this page</h3>
      |      <ul>
      |        <li><a href="#dashboard">Dashboard</a></li>
      |        <li><a href="#term-groups">Term Groups</a></li>
      |        <li><a href="#language-index">Language Index</a></li>
      |        <li><a href="#recent-terms">Recent Terms</a></li>
      |      </ul>
      |      <div class="bok-special-links">
      |        <h3>BoK Console</h3>
      |        <a class="bok-special-link" href="../glossary/index.html">Glossary</a>
      |        <a class="bok-special-link" href="${_html_escape(_history_href(config, "../"))}">History</a>
      |        <a class="bok-special-link" href="../manual/index.html">Manual</a>
      |      </div>
      |    </div>
      |  </aside>""".stripMargin

  private def _localized_glossary_toc_panel(config: BuildConfig): String =
    s"""<aside class="toc sidebar" data-title="Contents" data-levels="2">
       |    <div class="toc-menu">
       |      <h3>On this page</h3>
       |      <ul>
       |        <li><a href="#terms">Terms</a></li>
       |      </ul>
       |      <div class="bok-special-links">
       |        <h3>BoK Console</h3>
       |        <a class="bok-special-link" href="../../glossary/index.html">Glossary</a>
       |        <a class="bok-special-link" href="${_html_escape(_history_href(config, "../../"))}">History</a>
       |        <a class="bok-special-link" href="../../manual/index.html">Manual</a>
       |      </div>
       |    </div>
       |  </aside>""".stripMargin

  private def _category_html_page(
    config: BuildConfig,
    category: CategoryContent,
    categories: Vector[CategoryContent],
    locale: String,
    page: Path
  ): String =
    s"""<!doctype html>
       |<html lang="${_html_escape(locale)}">
       |<head>
       |  <meta charset="utf-8">
       |  <meta name="viewport" content="width=device-width, initial-scale=1">
       |  <title>${_html_escape(_uif(locale, "category.document.title", category.title, config.siteTitle))}</title>
       |${_site_css_links(config, page)}
       |</head>
       |<body class="article ${_html_escape(_dashboard_theme_class(config))}">
       |${_category_header(config, categories, locale)}
       |<div class="body body-dashboard">
       |  <main class="article">
       |    <div class="toolbar" role="navigation">
       |      <button class="nav-toggle"></button>
       |      <a href="../index.html" class="home-link"></a>
       |      <nav class="breadcrumbs" aria-label="breadcrumbs">
       |        <ul>
       |          <li><a href="../index.html">${_html_escape(config.siteTitle)}</a></li>
       |          <li>${_html_escape(category.title)}</li>
       |        </ul>
       |      </nav>
       |    </div>
       |    <div class="content">
       |      <article class="doc">
       |        ${_category_dashboard(config, category, locale)}
       |        ${_source_narrative_section(config.sourcePath.resolve(category.slug).resolve("index.dox"), locale)}
       |      </article>
       |    </div>
       |  </main>
       |</div>
       |</body>
       |</html>
       |""".stripMargin

  private def _category_header(
    config: BuildConfig,
    categories: Vector[CategoryContent],
    locale: String,
    rootPrefix: String = "../"
  ): String = {
    s"""<header class="header">
       |  <nav class="navbar">
       |    <div class="navbar-brand">
       |      <a class="navbar-item" href="${_html_escape(rootPrefix)}index.html">${_html_escape(config.siteTitle)}</a>
       |      <button class="navbar-burger" aria-controls="topbar-nav" aria-expanded="false" aria-label="Toggle main menu">
       |        <span></span>
       |        <span></span>
       |        <span></span>
       |      </button>
       |    </div>
       |    <div id="topbar-nav" class="navbar-menu">
       |      <div class="navbar-end">
       |        <a class="navbar-item" href="${_html_escape(rootPrefix)}index.html">${_html_escape(_ui(locale, "nav.home"))}</a>
       |        ${_category_nav_menu(locale, categories.map(x => CategorySummary(x.slug, x.title, x.description, x.purpose)), rootPrefix)}
       |      </div>
       |    </div>
       |  </nav>
       |</header>""".stripMargin
  }

  private def _category_nav_container(
    config: BuildConfig,
    current: CategoryContent,
    categories: Vector[CategoryContent]
  ): String = {
    val items = categories.map { category =>
      val currentclass = if (category.slug == current.slug) " is-current" else ""
      s"""<li class="nav-item" data-depth="1"><a class="nav-link${currentclass}" href="../${_html_escape(category.slug)}/index.html">${_html_escape(category.title)}</a></li>"""
    }.mkString("\n                  ")
    s"""<div class="nav-container" data-component="home" data-version="">
       |    <aside class="nav">
       |      <div class="panels">
       |        <div class="nav-panel-menu is-active" data-panel="menu">
       |          <nav class="nav-menu">
       |            <button class="nav-menu-toggle" aria-label="Toggle expand/collapse all" style="display: none"></button>
       |            <h3 class="title"><a href="../index.html">${_html_escape(config.siteTitle)}</a></h3>
       |            <ul class="nav-list">
       |              <li class="nav-item" data-depth="0">
       |                <ul class="nav-list">
       |                  ${items}
       |                </ul>
       |              </li>
       |            </ul>
       |          </nav>
       |        </div>
       |      </div>
       |    </aside>
       |  </div>""".stripMargin
  }

  private def _category_toc_panel(config: BuildConfig, category: CategoryContent, locale: String): String =
    s"""<aside class="toc sidebar" data-title="Contents" data-levels="2">
      |    <div class="toc-menu">
      |      <h3>On this page</h3>
       |      <ul>
       |        <li><a href="#dashboard">Dashboard</a></li>
      |      </ul>
      |      <div class="bok-special-links">
      |        <h3>BoK Console</h3>
      |        <a class="bok-special-link" href="../glossary/index.html">Glossary</a>
      |        <a class="bok-special-link" href="${_html_escape(_history_href(config, "../"))}">History</a>
      |        <a class="bok-special-link" href="../manual/index.html">Manual</a>
      |      </div>
      |    </div>
      |  </aside>""".stripMargin

  private def _source_narrative_section(path: Path, locale: String): String = {
    val body = _source_narrative_html(path, locale)
    if (body.isEmpty)
      ""
    else
      s"""<div class="bok-narrative-corner" id="narrative">
         |  <div class="sectionbody">
         |    ${body}
         |  </div>
         |</div>""".stripMargin
  }

  private def _source_narrative_html(path: Path, locale: String): String =
    if (!Files.isRegularFile(path))
      ""
    else {
      val content = Files.readString(path, StandardCharsets.UTF_8)
      val dox = Dox2Parser.parseWithFilename(Dox2Parser.Config.default, path.toString, content)
      val rule = Dox2HtmlTransformer.Rule(isDocument = false, isDefaultCss = false)
      val context = _smartdox_context(locale)
      val html = Dox2HtmlTransformer(context, rule).transform(_language_filter(dox, context)).take
      _strip_dashboard_owned_narrative_sections(_strip_dox_document_title(_dox_body_fragment(html))).trim
    }

  private def _strip_dashboard_owned_narrative_sections(html: String): String =
    """(?is)<section>\s*<h[2-6]>\s*(Quick Links|Category Portfolio|Navigation|Operation Notes)\s*</h[2-6]>.*?</section>""".r.replaceAllIn(html, "")

  private def _language_filter(dox: Dox, context: SmartDoxContext): Dox =
    Dox.transform(dox, new LanguageFilterTransformer(context.doxContext))

  private def _smartdox_context(locale: String): SmartDoxContext =
    SmartDoxContext.create().withTargetI18NContext(_to_locale(locale))

  private def _to_locale(value: String): Locale =
    Locale.forLanguageTag(value.replace('_', '-'))

  private def _ui(locale: String, key: String): String =
    _ui_context(_to_locale(locale)).message(key)

  private def _uif(locale: String, key: String, args: Any*): String =
    _ui_context(_to_locale(locale)).message(key, args: _*)

  private def _ui_context(locale: Locale): I18NContext = {
    val bundle = I18NContext.loadResourceBundle(_ui_resource_base, locale, _ui_resource_config)
    I18NContext.default.copy(locale = locale, resourceBundle = bundle)
  }


  private def _dashboard_theme_class(config: BuildConfig): String =
    s"bok-dashboard-theme-${config.dashboardColorGroup}"

  private val _dashboard_color_groups = Set("aurora", "lagoon", "meadow", "ocean", "ember", "slate")

  private def _dashboard_color_group(parsed: ParsedArgs, config: CozyProjectYamlConfig.Config, site: SiteConfig): String = {
    val raw =
      parsed.property("dashboard-color-group").
        orElse(config.value("bok.dashboard.color-group")).
        orElse(config.value("bok.dashboard.color_group")).
        orElse(site.value("site.metadata.dashboard_color_group")).
        orElse(site.value("site.metadata.dashboard-color-group")).
        orElse(site.value("site.metadata.dashboard.color_group")).
        getOrElse("aurora")
    val normalized = raw.trim.toLowerCase(java.util.Locale.ROOT).replace('_', '-').replace(' ', '-')
    if (_dashboard_color_groups.contains(normalized)) normalized else "aurora"
  }

  private def _site_asset_href(config: BuildConfig, page: Path, path: String): String =
    _site_root_prefix(config, page) + path

  private def _site_css_links(config: BuildConfig, page: Path): String =
    s"""  <link rel="stylesheet" href="${_html_escape(_site_asset_href(config, page, "_/css/bootstrap-grid.min.css"))}">
       |  <link rel="stylesheet" href="${_html_escape(_site_asset_href(config, page, "_/css/site.css"))}">
       |  <link rel="stylesheet" href="${_html_escape(_site_asset_href(config, page, "_/css/cozy-bok-dashboard.css"))}">""".stripMargin

  private def _site_root_prefix(config: BuildConfig, page: Path): String = {
    val pagedir = Option(page.getParent).getOrElse(config.websitePath)
    val relative = config.websitePath.toAbsolutePath.normalize.relativize(pagedir.toAbsolutePath.normalize)
    val depth =
      if (relative.toString.isEmpty)
        0
      else
        relative.iterator.asScala.length
    if (depth == 0)
      ""
    else
      "../" * depth
  }

  private def _strip_dox_document_title(html: String): String =
    html.replaceFirst("""(?s)\A\s*<h1[^>]*>.*?</h1>\s*""", "")

  private def _dox_body_fragment(html: String): String =
    _regex_first(html, """(?s)<body>\s*<article[^>]*>(.*?)</article>\s*</body>""").
      orElse(_regex_first(html, """(?s)<body[^>]*>(.*?)</body>""")).
      getOrElse(html)

  private def _regex_first(value: String, regex: String): Option[String] =
    regex.r.findFirstMatchIn(value).map(_.group(1))

  private def _category_nav_menu(locale: String, categories: Vector[CategorySummary], prefix: String): String =
    if (categories.isEmpty)
      ""
    else {
      val dropdownitems = categories.map { category =>
        s"""<a class="navbar-item navbar-dropdown-item" href="${_html_escape(prefix)}${_html_escape(category.slug)}/index.html">${_html_escape(category.title)}</a>"""
      }.mkString("\n          ")
      s"""<div class="navbar-item has-dropdown is-hoverable navbar-category-nav navbar-category-dropdown" aria-label="${_html_escape(_ui(locale, "nav.categories"))}">
         |  <a class="navbar-link navbar-category-toggle" href="#">${_html_escape(_ui(locale, "nav.categories"))}</a>
         |  <div class="navbar-dropdown navbar-category-menu">
         |          ${dropdownitems}
         |  </div>
         |</div>""".stripMargin
    }

  private def _home_nav_container(config: BuildConfig): String = {
    val items = _regular_category_summaries(config.sourcePath).map { category =>
      s"""<li class="nav-item" data-depth="1"><a class="nav-link" href="${_html_escape(category.slug)}/index.html">${_html_escape(category.title)}</a></li>"""
    }.mkString("\n          ")
    s"""<div class="nav-container" data-component="home" data-version="">
       |    <aside class="nav">
       |      <div class="panels">
       |        <div class="nav-panel-menu is-active" data-panel="menu">
       |          <nav class="nav-menu">
       |            <button class="nav-menu-toggle" aria-label="Toggle expand/collapse all" style="display: none"></button>
       |            <h3 class="title"><a href="index.html">${_html_escape(config.siteTitle)}</a></h3>
       |            <ul class="nav-list">
       |              <li class="nav-item" data-depth="0">
       |                <ul class="nav-list">
       |                  ${items}
       |                </ul>
       |              </li>
       |            </ul>
       |          </nav>
       |        </div>
       |      </div>
       |    </aside>
       |  </div>""".stripMargin
  }

  private def _home_toc_panel(config: BuildConfig, locale: String): String =
    s"""<aside class="toc sidebar" data-title="Contents" data-levels="2">
      |    <div class="toc-menu">
      |      <h3>On this page</h3>
       |      <ul>
       |        ${_home_dashboard_toc_item(config)}
      |        <li><a href="#operation-policy">Operation Policy</a></li>
      |      </ul>
      |      <div class="bok-special-links">
      |        <h3>BoK Console</h3>
      |        <a class="bok-special-link" href="glossary/index.html">Glossary</a>
      |        <a class="bok-special-link" href="${_html_escape(_history_href(config, ""))}">History</a>
      |        <a class="bok-special-link" href="manual/index.html">Manual</a>
      |      </div>
      |    </div>
      |  </aside>""".stripMargin

  private def _home_dashboard_toc_item(config: BuildConfig): String =
    if (_dashboard(config).isDefined || !_bok_purpose(config).isEmpty)
      """<li><a href="#dashboard">Dashboard</a></li>"""
    else
      ""

  private def _home_dashboard(config: BuildConfig, locale: String): String = {
    val purpose = _bok_purpose(config)
    val dashboard = _dashboard(config)
    if (dashboard.isEmpty && purpose.isEmpty)
      ""
    else {
      val hero = _dashboard_hero(
        _uif(locale, "home.page.title", config.siteTitle),
        _ui(locale, "home.intro"),
        Vector(
          _ui(locale, "dashboard.kpi.categories") -> dashboard.map(_.counts.categoryCount.toString).getOrElse("-"),
          _ui(locale, "dashboard.kpi.articles") -> dashboard.map(_.counts.articleCount.toString).getOrElse("-"),
          _ui(locale, "dashboard.kpi.rdf.triples") -> dashboard.map(_.rdf.tripleCount.toString).getOrElse("-")
        )
      )
      s"""<section class="bok-dashboard-shell" id="dashboard">
         |  ${hero}
         |  ${_home_dashboard_grid(config, purpose, dashboard, locale)}
         |</section>""".stripMargin
    }
  }

  private def _category_dashboard(config: BuildConfig, category: CategoryContent, locale: String): String = {
    val site = _dashboard(config)
    val dashboard = site.flatMap(_.categories.find(_.name == category.slug))
    if (!category.purpose.isEmpty || dashboard.isDefined) {
      val categoryrdf = dashboard.flatMap(_.rdf)
      val hero = _dashboard_hero(
        _uif(locale, "category.page.title", category.title),
        _uif(locale, "category.intro", category.description),
        Vector(
          _ui(locale, "dashboard.kpi.articles") -> dashboard.map(_.counts.articleCount.toString).getOrElse(category.articles.size.toString),
          _ui(locale, "dashboard.kpi.terms") -> dashboard.map(_.counts.glossaryTermCount.toString).getOrElse(category.terms.size.toString),
          _ui(locale, "dashboard.kpi.rdf") -> categoryrdf.map(_.tripleCount.toString).getOrElse("-")
        )
      )
      s"""<section class="bok-dashboard-shell" id="dashboard">
         |  ${hero}
         |  ${_category_dashboard_grid(config, category, dashboard, categoryrdf, locale)}
         |</section>""".stripMargin
    }
    else
      s"<p>${_html_escape(_ui(locale, "dashboard.unavailable"))}</p>"
  }

  private def _dashboard_hero(title: String, body: String, facts: Vector[(String, String)]): String = {
    val facthtml = facts.map {
      case (label, value) =>
        s"""<span class="bok-dashboard-hero-fact"><strong>${_html_escape(value)}</strong><em>${_html_escape(label)}</em></span>"""
    }.mkString("\n")
    s"""<header class="bok-dashboard-hero">
       |  <div class="bok-dashboard-hero-copy">
       |    <p class="bok-dashboard-eyebrow">Dashboard</p>
       |    <h1 class="page">${_html_escape(title)}</h1>
       |    <p class="bok-dashboard-lead">${_html_escape(body)}</p>
       |  </div>
       |  <div class="bok-dashboard-hero-facts">
       |    ${facthtml}
       |  </div>
       |</header>""".stripMargin
  }

  private def _bok_purpose(config: BuildConfig): BokPurpose =
    _site_purpose(_load_site_config(config.sourcePath))

  private def _site_purpose(site: SiteConfig): BokPurpose =
    _purpose_from_parts(
      site.value("site.metadata.vision"),
      _first_non_empty(
        site.goalTree("site.metadata.goals"),
        site.goalTree("site.metadata.goal_tree")
      ),
      site.list("site.metadata.goals"),
      site.list("site.metadata.subgoals")
    )

  private def _first_non_empty[T](xs: Vector[T]*): Vector[T] =
    xs.find(_.nonEmpty).getOrElse(Vector.empty)

  private def _purpose_from_parts(
    vision: Option[String],
    structuredgoals: Vector[BokGoal],
    flatgoals: Vector[String],
    flatsubgoals: Vector[String]
  ): BokPurpose =
    if (structuredgoals.nonEmpty)
      BokPurpose(vision, structuredgoals)
    else
      BokPurpose(vision, Vector.empty, flatgoals, flatsubgoals)

  private def _purpose_dashboard(purpose: BokPurpose): String =
    if (purpose.isEmpty)
      ""
    else
      s"""<div class="bok-purpose">
         |  ${purpose.vision.map(x => s"""<p><strong>Vision:</strong> ${_html_escape(x)}</p>""").getOrElse("")}
         |  ${_purpose_list("Goals", if (purpose.goals.nonEmpty) purpose.goals.map(_.title) else purpose.flatGoals)}
         |  ${_purpose_list("Subgoals", purpose.flatSubgoals)}
         |</div>""".stripMargin

  private def _purpose_list(label: String, values: Vector[String]): String =
    if (values.isEmpty)
      ""
    else
      values.map(x => s"<li>${_html_escape(x)}</li>").mkString(s"<div><strong>${label}:</strong><ul>", "", "</ul></div>")

  private def _home_dashboard_grid(config: BuildConfig, purpose: BokPurpose, dashboard: Option[BokDashboard], locale: String): String = {
    val cards = Vector[Option[String]](
      dashboard.map(x => _dashboard_card("col-12", "bok-card-activity bok-card-notification", _ui(locale, "dashboard.card.recent.activity"), _recent_activity_body(locale, config, x.increments), Vector("reader", "contributor", "project_manager"))),
      if (purpose.isEmpty) None else Some(_dashboard_card("col-12 col-xl-8", "bok-card-purpose", _ui(locale, "dashboard.card.vision"), _purpose_card_body(locale, purpose), Vector("reader", "contributor", "project_manager"))),
      dashboard.map(x => _dashboard_card(if (purpose.isEmpty) "col-12 col-xl-7" else "col-12 col-xl-4", "bok-card-matrix", _ui(locale, "dashboard.card.category.matrix"), _category_matrix_body(locale, x), Vector("reader", "contributor", "project_manager"))),
      dashboard.map(x => _kpi_card(locale, "col-6 col-md-3", _ui(locale, "dashboard.kpi.categories"), x.counts.categoryCount.toString, _ui(locale, "dashboard.kpi.categories.note"))),
      dashboard.map(x => _kpi_card(locale, "col-6 col-md-3", _ui(locale, "dashboard.kpi.articles"), x.counts.articleCount.toString, _ui(locale, "dashboard.kpi.articles.note"))),
      dashboard.map(x => _kpi_card_link("col-6 col-md-3", _ui(locale, "dashboard.kpi.terms"), x.counts.glossaryTermCount.toString, _ui(locale, "dashboard.kpi.terms.note"), "glossary/index.html")),
      dashboard.map(x => _kpi_card_link("col-6 col-md-3", _ui(locale, "dashboard.kpi.rdf.triples"), x.rdf.tripleCount.toString, _ui(locale, "dashboard.kpi.rdf.triples.note"), "rdf/index.html")),
      Some(_dashboard_card("col-12 col-xl-5", "bok-card-quality", _ui(locale, "dashboard.card.quality.alerts"), _quality_alerts_body(locale, dashboard.isDefined), Vector("contributor", "project_manager"))),
      dashboard.map(x => _dashboard_card("col-12 col-xl-8", "bok-card-chart", _ui(locale, "dashboard.card.growth"), _dashboard_increment_chart(locale, x.increments, _ui(locale, "dashboard.chart.bok.additions")), Vector("project_manager", "contributor"))),
      Some(_dashboard_card("col-12 col-md-6 col-xl-3", "bok-card-readiness", _ui(locale, "dashboard.card.readiness"), _home_readiness_body(locale, config, dashboard), Vector("site_administrator", "project_manager"))),
      Some(_dashboard_card("col-12 col-md-6 col-xl-3", "bok-card-actions", _ui(locale, "dashboard.card.next.actions"), _next_actions_body(locale, config), Vector("site_administrator", "project_manager")))
    ).flatten
    _dashboard_container(locale, cards)
  }

  private def _category_dashboard_grid(
    config: BuildConfig,
    category: CategoryContent,
    dashboard: Option[DashboardCategory],
    rdf: Option[DashboardRdfSummary],
    locale: String
  ): String = {
    val cards = Vector[Option[String]](
      if (category.purpose.isEmpty) None else Some(_dashboard_card("col-12 col-xl-8", "bok-card-purpose", _ui(locale, "dashboard.card.category.vision"), _purpose_card_body(locale, category.purpose), Vector("reader", "contributor", "project_manager"))),
      Some(_dashboard_card("col-12 col-xl-6", "bok-card-map", _ui(locale, "dashboard.card.term.map"), _page_map_body(category.terms, _ui(locale, "dashboard.term.empty")), Vector("reader", "contributor", "project_manager"))),
      Some(_dashboard_card("col-12 col-xl-6", "bok-card-map", _ui(locale, "dashboard.card.article.map"), _page_map_body(category.articles, _ui(locale, "dashboard.article.empty")), Vector("reader", "contributor", "project_manager"))),
      rdf.map(x => _kpi_card_link("col-6 col-md-3", _ui(locale, "dashboard.kpi.rdf"), x.tripleCount.toString, _ui(locale, "dashboard.kpi.rdf.note"), s"../rdf/index.html?category=${_url_query_escape(category.slug)}")),
      dashboard.map(x => _kpi_card(locale, "col-6 col-md-3", _ui(locale, "dashboard.kpi.articles"), x.counts.articleCount.toString, _ui(locale, "dashboard.kpi.category.articles.note"))),
      dashboard.map(x => _kpi_card(locale, "col-6 col-md-3", _ui(locale, "dashboard.kpi.terms"), x.counts.glossaryTermCount.toString, _ui(locale, "dashboard.kpi.category.terms.note"))),
      Some(_kpi_card(locale, "col-6 col-md-3", _ui(locale, "dashboard.kpi.issues"), "0", _ui(locale, "dashboard.kpi.issues.note"))),
      Some(_dashboard_card("col-12 col-xl-5", "bok-card-quality", _ui(locale, "dashboard.card.local.quality.alerts"), _quality_alerts_body(locale, dashboard.isDefined), Vector("contributor", "project_manager"))),
      dashboard.map(x => _dashboard_card("col-12 col-xl-7", "bok-card-chart", _ui(locale, "dashboard.card.category.growth"), _dashboard_increment_chart(locale, x.increments, _uif(locale, "dashboard.chart.category.additions", x.title)), Vector("project_manager", "contributor"))),
      Some(_dashboard_card("col-12 col-md-6 col-xl-3", "bok-card-readiness", _ui(locale, "dashboard.card.category.readiness"), _category_readiness_body(locale, dashboard), Vector("site_administrator", "project_manager"))),
      Some(_dashboard_card("col-12 col-md-6 col-xl-4", "bok-card-activity", _ui(locale, "dashboard.card.recent.changes"), _category_recent_changes_body(locale, category), Vector("reader", "contributor", "project_manager"))),
      Some(_dashboard_card("col-12 col-md-6 col-xl-5", "bok-card-related", _ui(locale, "dashboard.card.related.knowledge"), _related_knowledge_body(locale, config, category), Vector("reader", "contributor", "project_manager")))
    ).flatten
    _dashboard_container(locale, cards)
  }

  private def _dashboard_container(locale: String, cards: Vector[String]): String = {
    val defaultactor = "reader"
    cards.mkString(
      s"""<div class="bok-dashboard container-fluid bok-dashboard-command-center" data-bok-dashboard="true" data-bok-default-actor="${defaultactor}" data-bok-default-card-mode="hide" data-bok-status-all="${_html_escape(_uif(locale, "dashboard.actor.status.all", cards.size.toString))}" data-bok-status-filtered="${_html_escape(_ui(locale, "dashboard.actor.status.filtered"))}" data-bok-status-dimmed="${_html_escape(_ui(locale, "dashboard.actor.status.dimmed"))}">
         |  ${_dashboard_actor_filter(locale, cards, defaultactor)}
         |  <div class="row g-3">""".stripMargin,
      "\n",
      s"""  </div>
         |  ${_dashboard_actor_filter_script}
         |</div>""".stripMargin
    )
  }

  private def _dashboard_actor_filter(locale: String, cards: Vector[String], defaultactor: String): String = {
    val cardcount = cards.size
    val defaultcount = _dashboard_actor_count(cards, defaultactor)
    val buttons = Vector(
      "all" -> _ui(locale, "dashboard.actor.all"),
      "reader" -> _ui(locale, "dashboard.actor.reader"),
      "contributor" -> _ui(locale, "dashboard.actor.contributor"),
      "project_manager" -> _ui(locale, "dashboard.actor.project.manager"),
      "site_administrator" -> _ui(locale, "dashboard.actor.site.administrator")
    ).map {
      case (key, label) =>
        val pressed = if (key == defaultactor) "true" else "false"
        s"""<button type="button" class="bok-dashboard-actor-button${if (key == defaultactor) " is-active" else ""}" data-bok-actor-filter="${_html_escape(key)}" aria-pressed="${pressed}">${_html_escape(label)}</button>"""
    }.mkString("\n")
    val modebuttons = Vector(
      "hide" -> _ui(locale, "dashboard.actor.mode.hide"),
      "dim" -> _ui(locale, "dashboard.actor.mode.dim")
    ).map {
      case (key, label) =>
        val pressed = if (key == "hide") "true" else "false"
        s"""<button type="button" class="bok-dashboard-actor-mode-button${if (key == "hide") " is-active" else ""}" data-bok-actor-mode="${_html_escape(key)}" aria-pressed="${pressed}">${_html_escape(label)}</button>"""
    }.mkString("\n")
    s"""<div class="bok-dashboard-actor-filter" role="group" aria-label="${_html_escape(_ui(locale, "dashboard.actor.filter"))}">
       |  <span class="bok-dashboard-actor-filter-label">${_html_escape(_ui(locale, "dashboard.actor.filter"))}</span>
       |  ${buttons}
       |  <span class="bok-dashboard-actor-mode-label">${_html_escape(_ui(locale, "dashboard.actor.mode"))}</span>
       |  ${modebuttons}
       |  <span class="bok-dashboard-actor-status" data-bok-actor-status="true">${_html_escape(_uif(locale, "dashboard.actor.status.filtered", defaultcount.toString, cardcount.toString, _ui(locale, "dashboard.actor.reader")))}</span>
      |</div>""".stripMargin
  }

  private def _dashboard_actor_count(cards: Vector[String], actor: String): Int =
    if (actor == "all" || actor == "site_administrator")
      cards.size
    else
      cards.count { x =>
        val marker = "data-bok-actors=\""
        val start = x.indexOf(marker)
        if (start < 0)
          false
        else {
          val rest = x.substring(start + marker.length)
          val end = rest.indexOf('"')
          val value = if (end >= 0) rest.substring(0, end) else rest
          value.split("\\s+").contains(actor)
        }
      }

  private def _dashboard_actor_filter_script: String =
    """<script>
      |(function () {
      |  var validActors = ["all", "reader", "contributor", "project_manager", "site_administrator"];
      |  var validModes = ["hide", "dim"];
      |
      |  function defaultActor(root) {
      |    var value = root.getAttribute("data-bok-default-actor") || "reader";
      |    return validActors.indexOf(value) >= 0 ? value : "reader";
      |  }
      |
      |  function actorFromUrl(root) {
      |    try {
      |      var params = new URLSearchParams(window.location.search);
      |      var fallback = defaultActor(root);
      |      var value = params.get("actor") || fallback;
      |      return validActors.indexOf(value) >= 0 ? value : fallback;
      |    } catch (e) {
      |      return defaultActor(root);
      |    }
      |  }
      |
      |  function modeFromUrl() {
      |    try {
      |      var params = new URLSearchParams(window.location.search);
      |      var value = params.get("display") || "hide";
      |      return validModes.indexOf(value) >= 0 ? value : "hide";
      |    } catch (e) {
      |      return "hide";
      |    }
      |  }
      |
      |  function updateUrl(root, actor, mode) {
      |    if (!window.history || !window.history.replaceState) return;
      |    try {
      |      var url = new URL(window.location.href);
      |      if (actor === defaultActor(root)) {
      |        url.searchParams.delete("actor");
      |      } else {
      |        url.searchParams.set("actor", actor);
      |      }
      |      if (mode === "hide") {
      |        url.searchParams.delete("display");
      |      } else {
      |        url.searchParams.set("display", mode);
      }
      |      window.history.replaceState({}, "", url.toString());
      |    } catch (e) {
      |    }
      |  }
      |
      |  function format(template, values) {
      |    return template.replace(/\{(\d+)\}/g, function (_, index) {
      |      return values[index] || "";
      |    });
      |  }
      |
      |  function actorLabel(root, actor) {
      |    var button = root.querySelector("[data-bok-actor-filter='" + actor + "']");
      |    return button ? button.textContent : actor;
      |  }
      |
      |  function ensureActorChips(root) {
      |    root.querySelectorAll(".bok-card[data-bok-actors]").forEach(function (card) {
      |      if (card.querySelector(".bok-card-actor-chips")) return;
      |      var actors = (card.getAttribute("data-bok-actors") || "").split(/\s+/).filter(Boolean);
      |      if (actors.length === 0) return;
      |      var chips = document.createElement("div");
      |      chips.className = "bok-card-actor-chips";
      |      actors.forEach(function (actor) {
      |        var chip = document.createElement("span");
      |        chip.textContent = actorLabel(root, actor);
      |        chips.appendChild(chip);
      |      });
      |      var title = card.querySelector(".card-title");
      |      if (title) {
      |        title.insertAdjacentElement("afterend", chips);
      |      }
      |    });
      |  }
      |
      |  function applyActor(root, actor, mode, updateLocation) {
      |    root.setAttribute("data-bok-current-actor", actor);
      |    root.setAttribute("data-bok-card-mode", mode);
      |    root.querySelectorAll("[data-bok-actor-filter]").forEach(function (button) {
      |      var selected = button.getAttribute("data-bok-actor-filter") === actor;
      |      button.classList.toggle("is-active", selected);
      |      button.setAttribute("aria-pressed", selected ? "true" : "false");
      |    });
      |    root.querySelectorAll("[data-bok-actor-mode]").forEach(function (button) {
      |      var selected = button.getAttribute("data-bok-actor-mode") === mode;
      |      button.classList.toggle("is-active", selected);
      |      button.setAttribute("aria-pressed", selected ? "true" : "false");
      |    });
      |    root.querySelectorAll(".bok-card[data-bok-actors]").forEach(function (card) {
      |      var actors = (card.getAttribute("data-bok-actors") || "").split(/\s+/);
      |      var matches = actor === "all" || actor === "site_administrator" || actors.indexOf(actor) >= 0;
      |      var hide = !matches && mode === "hide";
      |      var dim = !matches && mode === "dim";
      |      var wrapper = card.closest("[data-bok-card]") || card;
      |      wrapper.hidden = hide;
      |      wrapper.classList.toggle("is-bok-filter-hidden", hide);
      |      wrapper.classList.toggle("is-bok-filter-dimmed", dim);
      |    });
      |    var cards = Array.prototype.slice.call(root.querySelectorAll(".bok-card[data-bok-actors]"));
      |    var total = cards.length;
      |    var matching = cards.filter(function (card) {
      |      var actors = (card.getAttribute("data-bok-actors") || "").split(/\s+/);
      |      return actor === "all" || actor === "site_administrator" || actors.indexOf(actor) >= 0;
      |    }).length;
      |    var visible = cards.filter(function (card) {
      |      return !(card.closest("[data-bok-card]") || card).hidden;
      |    }).length;
      |    var status = root.querySelector("[data-bok-actor-status]");
      |    if (status) {
      |      if (actor === "all" || actor === "site_administrator") {
      |        status.textContent = (root.getAttribute("data-bok-status-all") || format("All {0} cards are visible.", [String(total)])).replace("{0}", String(total));
      |      } else if (mode === "dim") {
      |        status.textContent = format(root.getAttribute("data-bok-status-dimmed") || "{2}: highlighting {0} of {1} cards.", [String(matching), String(total), actorLabel(root, actor)]);
      |      } else {
      |        status.textContent = format(root.getAttribute("data-bok-status-filtered") || "{2}: showing {0} of {1} cards.", [String(visible), String(total), actorLabel(root, actor)]);
      |      }
      |    }
      |    if (updateLocation) updateUrl(root, actor, mode);
      |  }
      |
      |  document.querySelectorAll("[data-bok-dashboard]").forEach(function (root) {
      |    ensureActorChips(root);
      |    applyActor(root, actorFromUrl(root), modeFromUrl(), false);
      |    root.querySelectorAll("[data-bok-actor-filter]").forEach(function (button) {
      |      button.addEventListener("click", function () {
      |        applyActor(root, button.getAttribute("data-bok-actor-filter") || defaultActor(root), root.getAttribute("data-bok-card-mode") || "hide", true);
      |      });
      |    });
      |    root.querySelectorAll("[data-bok-actor-mode]").forEach(function (button) {
      |      button.addEventListener("click", function () {
      |        applyActor(root, root.getAttribute("data-bok-current-actor") || defaultActor(root), button.getAttribute("data-bok-actor-mode") || "hide", true);
      |      });
      |    });
      |  });
      |}());
      |</script>""".stripMargin

  private def _dashboard_card(column: String, semantic: String, title: String, body: String, actors: Vector[String] = Vector.empty): String = {
    val actorattr =
      if (actors.isEmpty)
        ""
      else
        s""" data-bok-actors="${_html_escape(actors.mkString(" "))}""""
    s"""<div class="${_html_escape(column)}" data-bok-card="true">
       |  <section class="card bok-card ${_html_escape(semantic)}"${actorattr}>
       |    <div class="card-body">
       |      <h3 class="card-title">${_html_escape(title)}</h3>
       |      ${body}
       |    </div>
       |  </section>
       |</div>""".stripMargin
  }

  private def _kpi_card(locale: String, column: String, label: String, value: String, note: String): String =
    _dashboard_card(
      column,
      "bok-card-kpi",
      label,
      s"""<div class="bok-kpi-value">${_html_escape(value)}</div>
         |<div class="bok-kpi-label">${_html_escape(label)}</div>
         |<div class="bok-kpi-note">${_html_escape(note)}</div>""".stripMargin,
      Vector("reader", "contributor", "project_manager")
    )

  private def _kpi_card_link(column: String, label: String, value: String, note: String, href: String): String =
    _dashboard_card(
      column,
      "bok-card-kpi bok-card-kpi-link",
      label,
      s"""<a class="bok-kpi-link" href="${_html_escape(href)}">
         |  <span class="bok-kpi-value">${_html_escape(value)}</span>
         |  <span class="bok-kpi-label">${_html_escape(label)}</span>
         |  <span class="bok-kpi-note">${_html_escape(note)}</span>
         |</a>""".stripMargin,
      Vector("reader", "contributor", "project_manager")
    )

  private def _purpose_card_body(locale: String, purpose: BokPurpose): String =
    if (purpose.isEmpty)
      ""
    else
      s"""${purpose.vision.map(x => s"""<div class="bok-purpose-vision-panel"><span class="bok-purpose-node-label">V</span><span class="bok-purpose-vision-copy"><small>${_html_escape(_ui(locale, "dashboard.purpose.vision"))}</small><strong>${_html_escape(x)}</strong></span></div>""").getOrElse("")}
         |${_purpose_tree(locale, purpose)}""".stripMargin

  private def _purpose_tree(locale: String, purpose: BokPurpose): String =
    if (purpose.goals.nonEmpty)
      _purpose_goal_tree(locale, purpose.goals)
    else
      _flat_purpose_body(locale, purpose.flatGoals, purpose.flatSubgoals)

  private def _flat_purpose_body(locale: String, goals: Vector[String], subgoals: Vector[String]): String =
    Vector(
      if (goals.nonEmpty) Some(_purpose_flat_list(locale, "dashboard.purpose.goals", goals)) else None,
      if (subgoals.nonEmpty) Some(_purpose_flat_list(locale, "dashboard.purpose.subgoals", subgoals)) else None
    ).flatten.mkString("""<div class="bok-purpose-flat">""", "", "</div>")

  private def _purpose_flat_list(locale: String, labelkey: String, values: Vector[String]): String =
    values.take(5).zipWithIndex.map {
      case (value, i) =>
        s"""<li><span class="bok-purpose-node-label">${i + 1}</span><span>${_html_escape(value)}</span></li>"""
    }.mkString(s"""<div class="bok-purpose-flat-group"><strong>${_html_escape(_ui(locale, labelkey))}</strong><ul class="bok-purpose-flat-list">""", "", "</ul></div>")

  private def _purpose_goal_tree(locale: String, goals: Vector[BokGoal]): String = {
    val shown = goals.filterNot(_.isEmpty).take(3)
    if (shown.isEmpty)
      ""
    else {
      val body = shown.zipWithIndex.map {
        case (goal, i) =>
          val subgoalhtml =
            if (goal.subgoals.isEmpty)
              ""
            else
              goal.subgoals.take(3).zipWithIndex.map {
                case (subgoal, j) =>
                  val goallabel = s"G${i + 1}"
                  s"""<li><span class="bok-purpose-node-label">S${j + 1}</span><span class="bok-purpose-subgoal-copy"><small>${_html_escape(_uif(locale, "dashboard.purpose.supports.goal", goallabel))}</small><span>${_html_escape(subgoal)}</span></span></li>"""
              }.mkString("""<ul class="bok-purpose-subgoals">""", "", "</ul>")
          val moresubgoals =
            if (goal.subgoals.size > 3)
              s"""<div class="bok-more">${_html_escape(_uif(locale, "dashboard.more", goal.subgoals.size - 3))}</div>"""
            else
              ""
          s"""<div class="bok-purpose-goal">
             |  <div class="bok-purpose-goal-head"><span class="bok-purpose-node-label">G${i + 1}</span><strong>${_html_escape(goal.title)}</strong></div>
             |  ${subgoalhtml}${moresubgoals}
             |</div>""".stripMargin
      }.mkString
      val moregoals =
        if (goals.filterNot(_.isEmpty).size > 3)
          s"""<div class="bok-more">${_html_escape(_uif(locale, "dashboard.more", goals.filterNot(_.isEmpty).size - 3))}</div>"""
        else
          ""
      s"""<div class="bok-purpose-tree">${body}${moregoals}</div>"""
    }
  }

  private def _home_readiness_body(locale: String, config: BuildConfig, dashboard: Option[BokDashboard]): String =
    _definition_list(Vector(
      _ui(locale, "dashboard.readiness.strategy") -> config.strategy,
      _ui(locale, "dashboard.readiness.scope") -> config.siteOutputScopePolicy,
      _ui(locale, "dashboard.readiness.metadata") -> dashboard.map(_ => _ui(locale, "dashboard.status.available")).getOrElse(_ui(locale, "dashboard.status.missing")),
      _ui(locale, "dashboard.readiness.issue.count") -> _ui(locale, "dashboard.status.zero.known")
    ))

  private def _category_readiness_body(locale: String, dashboard: Option[DashboardCategory]): String =
    _definition_list(Vector(
      _ui(locale, "dashboard.readiness.status") -> _ui(locale, "dashboard.status.active"),
      _ui(locale, "dashboard.readiness.metadata") -> dashboard.map(_ => _ui(locale, "dashboard.status.available")).getOrElse(_ui(locale, "dashboard.status.missing")),
      _ui(locale, "dashboard.readiness.freshness") -> dashboard.flatMap(_.increments.buckets.lastOption.map(_.label)).getOrElse(_ui(locale, "dashboard.activity.none")),
      _ui(locale, "dashboard.readiness.issue.count") -> _ui(locale, "dashboard.status.zero.known")
    ))

  private def _definition_list(items: Vector[(String, String)]): String =
    items.map {
      case (label, value) =>
        s"""<div class="bok-definition-row"><span>${_html_escape(label)}</span><strong>${_html_escape(value)}</strong></div>"""
    }.mkString("""<div class="bok-definition-list">""", "", "</div>")

  private def _quality_alerts_body(locale: String, metadataavailable: Boolean): String = {
    val items =
      if (metadataavailable)
        Vector(_ui(locale, "dashboard.quality.no.critical"), _ui(locale, "dashboard.quality.diagnostics.available"))
      else
        Vector(_ui(locale, "dashboard.quality.metadata.missing"), _ui(locale, "dashboard.quality.refresh"))
    items.take(5).map(x => s"""<li class="list-group-item"><span class="badge bok-badge-info">info</span>${_html_escape(x)}</li>""").
      mkString("""<ul class="list-group bok-alert-list">""", "", "</ul>")
  }

  private def _category_matrix_body(locale: String, dashboard: BokDashboard): String = {
    val cards =
      if (dashboard.categories.isEmpty)
        s"""<p class="bok-card-muted">${_html_escape(_ui(locale, "dashboard.category.metadata.empty"))}</p>"""
      else
        dashboard.categories.map { category =>
          val freshness = category.increments.buckets.lastOption.map(_.label).getOrElse("-")
          val rdfvalue = category.rdf.map(_.tripleCount.toString).getOrElse("-")
          s"""<div class="bok-category-summary-card">
             |  <a class="bok-category-summary-title" href="${_html_escape(category.name)}/index.html">${_html_escape(category.title)}</a>
             |  <span class="bok-category-summary-freshness">${_html_escape(_ui(locale, "dashboard.readiness.freshness"))}: ${_html_escape(freshness)}</span>
             |  <span class="bok-category-summary-metrics">
             |    <span><b>${category.counts.articleCount}</b>${_html_escape(_ui(locale, "dashboard.kpi.articles"))}</span>
             |    <span><b>${category.counts.glossaryTermCount}</b>${_html_escape(_ui(locale, "dashboard.kpi.terms"))}</span>
             |    <a class="bok-category-rdf-link" href="rdf/index.html?category=${_html_escape(_url_query_escape(category.name))}"><b>${_html_escape(rdfvalue)}</b>${_html_escape(_ui(locale, "dashboard.kpi.rdf"))}</a>
             |  </span>
             |</div>""".stripMargin
        }.mkString("\n")
    s"""<div class="bok-category-summary-grid">
       |  ${cards}
       |</div>
       |${_dashboard_distribution_chart(locale, dashboard.counts, _ui(locale, "dashboard.chart.item.distribution"))}""".stripMargin
  }

  private def _recent_activity_body(locale: String, config: BuildConfig, increments: DashboardIncrements): String =
    if (increments.buckets.isEmpty)
      s"""<div class="bok-notification-summary">
         |  <span>${_html_escape(_ui(locale, "dashboard.activity.history.check"))}</span>
         |  <strong>0</strong>
         |</div>
         |<p class="bok-card-muted">${_html_escape(_ui(locale, "dashboard.activity.empty"))}</p>
         |<p class="bok-card-link"><a href="${_html_escape(_history_href(config, ""))}">${_html_escape(_ui(locale, "dashboard.activity.open.history"))}</a></p>""".stripMargin
    else {
      val latest = increments.buckets.last
      s"""<div class="bok-notification-summary">
         |  <span>${_html_escape(_uif(locale, "dashboard.activity.latest", latest.label))}</span>
         |  <strong>+${latest.count}</strong>
         |</div>
         |${increments.buckets.takeRight(5).reverse.map { bucket =>
        s"""<li class="list-group-item"><time datetime="${_html_escape(bucket.startDate)}">${_html_escape(bucket.label)}</time><strong>+${bucket.count}</strong></li>"""
      }.mkString("""<ul class="list-group bok-activity-list">""", "", "</ul>")}
         |<p class="bok-card-link"><a href="${_html_escape(_history_href(config, ""))}">${_html_escape(_ui(locale, "dashboard.activity.open.history"))}</a></p>""".stripMargin
    }

  private def _next_actions_body(locale: String, config: BuildConfig): String = {
    val project = if (config.project == _logical_cwd) "" else " <bok-root>"
    Vector(
      s"cozy bok build${project} --strategy preview",
      s"cozy bok preview${project}",
      s"cozy bok publish${project} --dry-run",
      _ui(locale, "dashboard.action.fix.diagnostics")
    ).map(x => s"<li><code>${_html_escape(x)}</code></li>").
      mkString("<ol class=\"bok-action-list\">", "", "</ol>")
  }

  private def _quick_links_body(locale: String, config: BuildConfig, prefix: String): String =
    Vector(
      _ui(locale, "glossary.title") -> s"${prefix}glossary/index.html",
      _ui(locale, "history.title") -> _history_href(config, prefix),
      _ui(locale, "manual.title") -> s"${prefix}manual/index.html"
    ).map {
      case (label, href) =>
        s"""<li class="list-group-item"><a href="${_html_escape(href)}">${_html_escape(label)}</a></li>"""
    }.mkString("""<ul class="list-group bok-quick-links-list">""", "", "</ul>")

  private def _page_map_body(items: Vector[CategoryPageItem], empty: String): String =
    if (items.isEmpty)
      s"""<p class="bok-card-muted">${_html_escape(empty)}</p>"""
    else {
      val shown = items.take(5).map { item =>
        s"""<li class="list-group-item"><a href="${_html_escape(item.href)}">${_html_escape(item.title)}</a><span>${_html_escape(item.brief)}</span></li>"""
      }.mkString
      val more = if (items.size > 5) s"""<li class="list-group-item bok-more">+${items.size - 5} more</li>""" else ""
      s"""<ul class="list-group bok-map-list">${shown}${more}</ul>"""
    }

  private def _category_recent_changes_body(locale: String, category: CategoryContent): String = {
    val items = (category.articles ++ category.terms).sortBy(-_.modifiedAtMillis).take(5)
    _page_map_body(items, _ui(locale, "dashboard.local.change.empty"))
  }

  private def _related_knowledge_body(locale: String, config: BuildConfig, category: CategoryContent): String =
    s"""<ul class="list-group bok-related-list">
       |  <li class="list-group-item"><a href="../glossary/index.html">${_html_escape(_ui(locale, "glossary.title"))}</a></li>
       |  <li class="list-group-item"><a href="../glossary/${_html_escape(category.slug)}/index.html">${_html_escape(_uif(locale, "dashboard.related.category.terms", category.title))}</a></li>
       |  <li class="list-group-item"><a href="../rdf/index.html?category=${_html_escape(_url_query_escape(category.slug))}">${_html_escape(_ui(locale, "rdf.graph.title"))}</a></li>
       |  <li class="list-group-item"><a href="${_html_escape(_history_href(config, "../"))}">${_html_escape(_ui(locale, "history.title"))}</a></li>
       |  <li class="list-group-item"><a href="../manual/index.html">${_html_escape(_ui(locale, "manual.title"))}</a></li>
       |</ul>""".stripMargin

  private def _dashboard_cards(counts: DashboardCounts, includecategories: Boolean): String = {
    val categorycard =
      if (includecategories)
        s"""  <div class="bok-metric-card">
           |    <div class="bok-metric-label">Categories</div>
           |    <div class="bok-metric-value">${counts.categoryCount}</div>
           |    <div class="bok-metric-note">SmartDox categories</div>
           |  </div>
           |""".stripMargin
      else
        ""
    s"""<div class="bok-dashboard-grid">
       |${categorycard}  <div class="bok-metric-card">
       |    <div class="bok-metric-label">Articles</div>
       |    <div class="bok-metric-value">${counts.articleCount}</div>
       |    <div class="bok-metric-note">Article / Blog pages</div>
       |  </div>
       |  <div class="bok-metric-card">
       |    <div class="bok-metric-label">Terms</div>
       |    <div class="bok-metric-value">${counts.glossaryTermCount}</div>
       |    <div class="bok-metric-note">Site glossary terms</div>
       |  </div>
       |  <div class="bok-metric-card">
       |    <div class="bok-metric-label">Total Items</div>
       |    <div class="bok-metric-value">${counts.totalItemCount}</div>
       |    <div class="bok-metric-note">Articles + terms</div>
       |  </div>
       |</div>""".stripMargin
  }

  private def _dashboard_distribution_chart(locale: String, counts: DashboardCounts, label: String): String = {
    val articlecount = counts.articleCount
    val termcount = counts.glossaryTermCount
    val total = math.max(1, articlecount + termcount)
    val articlewidth = _dashboard_bar_width(articlecount, total)
    val termwidth = _dashboard_bar_width(termcount, total)
    s"""<div class="bok-dashboard-chart" aria-label="${_html_escape(label)}" data-chart="distribution-ratio">
       |  <div class="bok-chart-row"><span>${_html_escape(_ui(locale, "dashboard.kpi.articles"))}</span><div><b style="width:${articlewidth}%"></b></div><em>${articlecount}</em></div>
       |  <div class="bok-chart-row"><span>${_html_escape(_ui(locale, "dashboard.kpi.terms"))}</span><div><b style="width:${termwidth}%"></b></div><em>${termcount}</em></div>
       |</div>""".stripMargin
  }

  private def _dashboard_rdf_cards(rdf: DashboardRdfSummary): String =
    s"""<div class="bok-dashboard-grid">
       |  <div class="bok-metric-card">
       |    <div class="bok-metric-label">RDF Resources</div>
       |    <div class="bok-metric-value">${rdf.resourceCount}</div>
       |    <div class="bok-metric-note">Site RDF resources</div>
       |  </div>
       |  <div class="bok-metric-card">
       |    <div class="bok-metric-label">RDF Triples</div>
       |    <div class="bok-metric-value">${rdf.tripleCount}</div>
       |    <div class="bok-metric-note">Generated site graph triples</div>
       |  </div>
       |  <div class="bok-metric-card">
       |    <div class="bok-metric-label">RDF Subjects</div>
       |    <div class="bok-metric-value">${rdf.subjectCount}</div>
       |    <div class="bok-metric-note">Distinct graph subjects</div>
       |  </div>
       |  <div class="bok-metric-card">
       |    <div class="bok-metric-label">RDF Predicates</div>
       |    <div class="bok-metric-value">${rdf.predicateCount}</div>
       |    <div class="bok-metric-note">Distinct graph predicates</div>
       |  </div>
       |</div>""".stripMargin

  private def _dashboard_increment_chart(locale: String, increments: DashboardIncrements, label: String): String =
    if (increments.buckets.isEmpty)
      s"""<p>${_html_escape(_ui(locale, "dashboard.increment.empty"))}</p>"""
    else if (!increments.buckets.forall(_.hasBreakdown))
      _dashboard_increment_total_chart(locale, increments, label)
    else {
      val cumulativearticles = increments.buckets.scanLeft(0)(_ + _.articleCount).tail
      val cumulativeterms = increments.buckets.scanLeft(0)(_ + _.glossaryTermCount).tail
      val points = increments.buckets.zip(cumulativearticles.zip(cumulativeterms))
      val max = math.max(1, (cumulativearticles ++ cumulativeterms).max)
      val pointcount = increments.buckets.length
      def x(index: Int): Double =
        if (pointcount <= 1) 50.0 else 8.0 + (84.0 * index.toDouble / (pointcount - 1).toDouble)
      def y(value: Int): Double =
        88.0 - (76.0 * value.toDouble / max.toDouble)
      def coordinates(values: Vector[Int]): String =
        values.zipWithIndex.map {
          case (value, index) => f"${x(index)}%.2f,${y(value)}%.2f"
        }.mkString(" ")
      val articlecoordinates = coordinates(cumulativearticles)
      val termcoordinates = coordinates(cumulativeterms)
      val articlemarkers = increments.buckets.zip(cumulativearticles).zipWithIndex.map {
        case ((bucket, value), index) =>
          val cx = x(index)
          val cy = y(value)
          val title = _uif(locale, "dashboard.chart.title.cumulative.articles", bucket.label, value, bucket.articleCount)
          f"""      <circle class="bok-cumulative-point-articles" cx="${cx}%.2f" cy="${cy}%.2f" r="2.8"><title>${_html_escape(title)}</title></circle>"""
      }.mkString("\n")
      val termmarkers = increments.buckets.zip(cumulativeterms).zipWithIndex.map {
        case ((bucket, value), index) =>
          val cx = x(index)
          val cy = y(value)
          val title = _uif(locale, "dashboard.chart.title.cumulative.terms", bucket.label, value, bucket.glossaryTermCount)
          f"""      <circle class="bok-cumulative-point-terms" cx="${cx}%.2f" cy="${cy}%.2f" r="2.8"><title>${_html_escape(title)}</title></circle>"""
      }.mkString("\n")
      val axis = points.map {
        case (bucket, (articlevalue, termvalue)) =>
          s"""    <span><time datetime="${_html_escape(bucket.startDate)}">${_html_escape(bucket.label)}</time><em>${_html_escape(_uif(locale, "dashboard.chart.axis.article.term", articlevalue, termvalue))}</em></span>"""
      }.mkString("\n")
      val range = s"${increments.buckets.head.startDate} - ${increments.buckets.last.endDate}"
      s"""<div class="bok-dashboard-chart" aria-label="${_html_escape(label)}" data-chart="cumulative-date" data-scale="${_html_escape(increments.scale)}">
         |  <div class="bok-cumulative-chart">
         |    <div class="bok-cumulative-chart-head">
         |      <span class="bok-cumulative-chart-title">${_html_escape(label)}</span>
         |      <span class="bok-cumulative-chart-range">${_html_escape(range)}</span>
         |      <span class="bok-cumulative-chart-scale">${_html_escape(increments.scale)}</span>
         |    </div>
         |    <svg class="bok-cumulative-chart-svg" viewBox="0 0 100 100" role="img" aria-label="${_html_escape(_uif(locale, "dashboard.chart.aria.cumulative", label))}">
         |      <line class="bok-cumulative-axis-x" x1="6" y1="88" x2="94" y2="88"></line>
         |      <line class="bok-cumulative-axis-y" x1="6" y1="12" x2="6" y2="88"></line>
         |      <polyline class="bok-cumulative-line bok-cumulative-line-articles" points="${articlecoordinates}"></polyline>
         |      <polyline class="bok-cumulative-line bok-cumulative-line-terms" points="${termcoordinates}"></polyline>
         |      <g class="bok-cumulative-points">
         |${articlemarkers}
         |${termmarkers}
         |      </g>
         |    </svg>
         |    <div class="bok-cumulative-legend">
         |      <span><i class="bok-cumulative-marker bok-cumulative-marker-articles"></i>${_html_escape(_ui(locale, "dashboard.kpi.articles"))}</span>
         |      <span><i class="bok-cumulative-marker bok-cumulative-marker-terms"></i>${_html_escape(_ui(locale, "dashboard.kpi.terms"))}</span>
         |    </div>
         |    <div class="bok-cumulative-axis">
         |${axis}
         |    </div>
         |  </div>
         |</div>""".stripMargin
    }

  private def _dashboard_increment_total_chart(locale: String, increments: DashboardIncrements, label: String): String = {
    val cumulativetotals = increments.buckets.scanLeft(0)(_ + _.count).tail
    val max = math.max(1, cumulativetotals.max)
    val pointcount = increments.buckets.length
    def x(index: Int): Double =
      if (pointcount <= 1) 50.0 else 8.0 + (84.0 * index.toDouble / (pointcount - 1).toDouble)
    def y(value: Int): Double =
      88.0 - (76.0 * value.toDouble / max.toDouble)
    val coordinates = cumulativetotals.zipWithIndex.map {
      case (value, index) => f"${x(index)}%.2f,${y(value)}%.2f"
    }.mkString(" ")
    val markers = increments.buckets.zip(cumulativetotals).zipWithIndex.map {
      case ((bucket, value), index) =>
        val cx = x(index)
        val cy = y(value)
        val title = _uif(locale, "dashboard.chart.title.cumulative.total", bucket.label, value, bucket.count)
        f"""      <circle class="bok-cumulative-point-total" cx="${cx}%.2f" cy="${cy}%.2f" r="2.8"><title>${_html_escape(title)}</title></circle>"""
    }.mkString("\n")
    val axis = increments.buckets.zip(cumulativetotals).map {
      case (bucket, value) =>
        s"""    <span><time datetime="${_html_escape(bucket.startDate)}">${_html_escape(bucket.label)}</time><em>${value}</em></span>"""
    }.mkString("\n")
    val range = s"${increments.buckets.head.startDate} - ${increments.buckets.last.endDate}"
    s"""<div class="bok-dashboard-chart" aria-label="${_html_escape(label)}" data-chart="cumulative-date" data-scale="${_html_escape(increments.scale)}">
       |  <div class="bok-cumulative-chart">
       |    <div class="bok-cumulative-chart-head">
       |      <span class="bok-cumulative-chart-title">${_html_escape(label)}</span>
       |      <span class="bok-cumulative-chart-range">${_html_escape(range)}</span>
       |      <span class="bok-cumulative-chart-scale">${_html_escape(increments.scale)}</span>
       |    </div>
       |    <svg class="bok-cumulative-chart-svg" viewBox="0 0 100 100" role="img" aria-label="${_html_escape(_uif(locale, "dashboard.chart.aria.cumulative", label))}">
       |      <line class="bok-cumulative-axis-x" x1="6" y1="88" x2="94" y2="88"></line>
       |      <line class="bok-cumulative-axis-y" x1="6" y1="12" x2="6" y2="88"></line>
       |      <polyline class="bok-cumulative-line bok-cumulative-line-total" points="${coordinates}"></polyline>
       |      <g class="bok-cumulative-points">
       |${markers}
       |      </g>
       |    </svg>
       |    <div class="bok-cumulative-legend">
       |      <span><i class="bok-cumulative-marker bok-cumulative-marker-total"></i>${_html_escape(_ui(locale, "dashboard.matrix.total"))}</span>
       |    </div>
       |    <div class="bok-cumulative-axis">
       |${axis}
       |    </div>
       |  </div>
       |</div>""".stripMargin
  }

  private def _dashboard(config: BuildConfig): Option[BokDashboard] = {
    val path = config.doxsitePath.resolve("metadata/dashboard/site.json")
    if (!Files.isRegularFile(path))
      None
    else {
      val content = Files.readString(path, StandardCharsets.UTF_8)
      parser.parse(content) match {
        case Left(_) => None
        case Right(json) => json.as[BokDashboard] match {
          case Left(_) => None
          case Right(dashboard) => Some(dashboard)
        }
      }
    }
  }

  private def _history_href(config: BuildConfig, prefix: String): String =
    _latest_history_year_page(config.websitePath.resolve("history")) match {
      case Some(file) => s"${prefix}history/${file}"
      case None => s"${prefix}history/index.html"
    }

  private def _latest_history_year_page(dir: Path): Option[String] =
    if (!Files.isDirectory(dir))
      None
    else {
      val stream = Files.list(dir)
      try {
        stream.iterator.asScala.toVector.
          filter(Files.isRegularFile(_)).
          map(_.getFileName.toString).
          collect { case name if name.matches("""\d{4}\.html""") => name }.
          sortBy(identity).
          lastOption
      } finally {
        stream.close()
      }
    }

  private def _home_category_list(config: BuildConfig, locale: String): String = {
    val categories = _regular_category_summaries(config.sourcePath)
    if (categories.isEmpty)
      s"<p>${_html_escape(_ui(locale, "home.categories.empty"))}</p>"
    else
      categories.map { category =>
        val htmlclass = if (category.slug == "glossary") """ class="glossary"""" else ""
        s"""<li><a${htmlclass} href="${_html_escape(category.slug)}/index.html">${_html_escape(category.title)}</a>: ${_html_escape(category.description)}</li>"""
      }.mkString("<ul>\n", "\n", "\n</ul>")
  }

  private final case class CategorySummary(slug: String, title: String, description: String, purpose: BokPurpose)

  private def _regular_category_summaries(source: Path): Vector[CategorySummary] =
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

  private def _category_contents(source: Path): Vector[CategoryContent] =
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

  private def _article_page_items(dir: Path): Vector[CategoryPageItem] =
    if (!Files.isDirectory(dir))
      Vector.empty
    else {
      val stream = Files.walk(dir)
      try {
        stream.iterator.asScala.toVector.
          filter(Files.isRegularFile(_)).
          filter(_.getFileName.toString.endsWith(".dox")).
          filterNot(_.getFileName.toString == "index.dox").
          filterNot(x => dir.relativize(x).toString.replace(java.io.File.separatorChar, '/').startsWith("glossary/")).
          map { file =>
            val rel = dir.relativize(file).toString.replace(java.io.File.separatorChar, '/')
            val href = rel.stripSuffix(".dox") + ".html"
            val content = Files.readString(file, StandardCharsets.UTF_8)
            CategoryPageItem(
              href,
              _dox_title(content, file),
              _dox_brief(content),
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
          filter(_.getFileName.toString.endsWith(".dox")).
          filterNot(_.getFileName.toString == "index.dox").
          map { file =>
            val rel = dir.relativize(file).toString.replace(java.io.File.separatorChar, '/')
            val href = s"../glossary/${category}/${rel.stripSuffix(".dox")}.html"
            val content = Files.readString(file, StandardCharsets.UTF_8)
            CategoryPageItem(
              href,
              _dox_title(content, file),
              _dox_brief(content),
              _modified_at_millis(file),
              _dox_reading(content)
            )
          }.sortBy(_.href)
      } finally {
        stream.close()
      }
    }

  private def _dox_title(content: String, file: Path): String =
    content.linesIterator.map(_.trim).find(_.nonEmpty).getOrElse(_titleize(file.getFileName.toString.stripSuffix(".dox")))

  private def _dox_brief(content: String): String = {
    val lines = content.linesIterator.toVector
    lines.zipWithIndex.collectFirst {
      case (line, i) if line.trim == "## BRIEF" =>
        lines.drop(i + 1).map(_.trim).find(_.nonEmpty).getOrElse("")
    }.filter(_.nonEmpty).getOrElse("Category entry.")
  }

  private def _dox_reading(content: String): Option[String] =
    _dox_head_value(content, Vector("reading", "yomi", "読み"))

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

  private def _modified_at_millis(file: Path): Long =
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

    def flush(): Unit = {
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
            flush()
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
    flush()
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

  private def _html_escape(value: String): String =
    value.flatMap {
      case '&' => "&amp;"
      case '<' => "&lt;"
      case '>' => "&gt;"
      case '"' => "&quot;"
      case '\'' => "&#39;"
      case c => c.toString
    }

  private def _javascript_string(value: String): String =
    value.flatMap {
      case '\\' => "\\\\"
      case '\'' => "\\'"
      case '"' => "\\\""
      case '\n' => "\\n"
      case '\r' => "\\r"
      case '\t' => "\\t"
      case c if c.isControl => f"\\u${c.toInt}%04x"
      case c => c.toString
    }

  private def _url_query_escape(value: String): String =
    URLEncoder.encode(value, StandardCharsets.UTF_8.name)

  private def _delete_directory(path: Path): Unit =
    if (Files.exists(path)) {
      val stream = Files.walk(path)
      try {
        stream.iterator.asScala.toVector.reverse.foreach(Files.deleteIfExists)
      } finally {
        stream.close()
      }
    }

  private def _write(path: Path, content: String, policy: ProjectFilePolicy): Unit =
    policy match {
      case ProjectFilePolicy.Skip =>
        Unit
      case ProjectFilePolicy.Overwrite =>
        _write_text(path, content)
      case ProjectFilePolicy.Default =>
        if (!Files.exists(path))
          _write_text(path, content)
        else if (Files.readString(path, StandardCharsets.UTF_8) == content)
          Unit
        else
          _write_text(Paths.get(path.toString + ".bak"), content)
    }

  private def _write_text(path: Path, content: String): Unit = {
    Option(path.getParent).foreach(Files.createDirectories(_))
    Files.writeString(path, content, StandardCharsets.UTF_8)
  }

  private def _read_text(path: Path): String =
    Files.readString(path, StandardCharsets.UTF_8)

  private def _write_default_ui_bundle(path: Path, policy: ProjectFilePolicy): Unit =
    policy match {
      case ProjectFilePolicy.Skip =>
        Unit
      case ProjectFilePolicy.Overwrite =>
        _write_default_ui_bundle(path)
      case ProjectFilePolicy.Default =>
        if (!Files.exists(path))
          _write_default_ui_bundle(path)
    }

  private def _write_default_ui_bundle(path: Path): Unit = {
    Option(path.getParent).foreach(Files.createDirectories(_))
    val out = new ZipOutputStream(Files.newOutputStream(path))
    try {
      _zip_text(out, "layouts/default.hbs", _default_ui_layout())
      _zip_text(out, "layouts/404.hbs", _default_ui_layout())
      _zip_text(out, "partials/header-content.hbs", _default_ui_header())
      _zip_text(out, "partials/footer-content.hbs", "")
      _zip_text(out, "helpers/or.js", _default_ui_or_helper())
      _zip_text(out, "helpers/relativize.js", _default_ui_relativize_helper())
      _zip_default_ui_assets(out)
    } finally {
      out.close()
    }
  }

  private def _zip_text(out: ZipOutputStream, name: String, content: String): Unit = {
    _zip_bytes(out, name, content.getBytes(StandardCharsets.UTF_8))
  }

  private def _zip_bytes(out: ZipOutputStream, name: String, content: Array[Byte]): Unit = {
    out.putNextEntry(new ZipEntry(name))
    out.write(content)
    out.closeEntry()
  }

  private def _zip_default_ui_assets(out: ZipOutputStream): Unit =
    _resource_text("cozy/antora-ui/manifest.txt") match {
      case Some(manifest) =>
        manifest.linesIterator.map(_.trim).filter(_.nonEmpty).foreach { name =>
          _resource_bytes(s"cozy/antora-ui/${name}") match {
            case Some(bytes) => _zip_bytes(out, name, bytes)
            case None => RAISE.noReachDefect
          }
        }
      case None =>
        _zip_text(out, "css/site.css", _default_ui_css())
        _zip_text(out, "js/site.js", "")
    }

  private def _resource_text(name: String): Option[String] =
    _resource_bytes(name).map(x => new String(x, StandardCharsets.UTF_8))

  private def _resource_bytes(name: String): Option[Array[Byte]] =
    Option(getClass.getClassLoader.getResourceAsStream(name)).map { in =>
      try {
        in.readAllBytes()
      } finally {
        in.close()
      }
    }

  private def _default_ui_layout(): String =
    """<!doctype html>
      |<html lang="{{site.keys.lang}}">
      |<head>
      |  <meta charset="utf-8">
      |  <meta name="viewport" content="width=device-width, initial-scale=1">
      |  <title>{{page.title}} - {{site.title}}</title>
      |  <link rel="stylesheet" href="{{uiRootPath}}/css/bootstrap-grid.min.css">
      |  <link rel="stylesheet" href="{{uiRootPath}}/css/site.css">
      |  <link rel="stylesheet" href="{{uiRootPath}}/css/cozy-bok-dashboard.css">
      |</head>
      |<body class="article">
      |  {{> header-content}}
      |  <div class="body">
      |    <div class="nav-container"{{#if page.component}} data-component="{{page.component.name}}" data-version="{{page.version}}"{{/if}}>
      |      <aside class="nav">
      |        <div class="panels">
      |          <div class="nav-panel-menu is-active" data-panel="menu">
      |            <nav class="nav-menu">
      |              <button class="nav-menu-toggle" aria-label="Toggle expand/collapse all" style="display: none"></button>
      |              <h3 class="title"><a href="{{siteRootPath}}/index.html">{{site.title}}</a></h3>
      |              <ul class="nav-list">
      |                <li class="nav-item" data-depth="0">
      |                  <ul class="nav-list">
      |                    <li class="nav-item" data-depth="1"><a class="nav-link" href="{{siteRootPath}}/index.html">Home</a></li>
      |                  </ul>
      |                </li>
      |              </ul>
      |            </nav>
      |          </div>
      |        </div>
      |      </aside>
      |    </div>
      |    <main class="article">
      |      <div class="toolbar" role="navigation">
      |        <button class="nav-toggle"></button>
      |        <a href="{{siteRootPath}}/index.html" class="home-link{{#if page.home}} is-current{{/if}}"></a>
      |        <nav class="breadcrumbs" aria-label="breadcrumbs">
      |          <ul>
      |            <li><a href="{{siteRootPath}}/index.html">{{site.title}}</a></li>
      |            <li>{{page.title}}</li>
      |          </ul>
      |        </nav>
      |      </div>
      |      <div class="content">
      |        <aside class="toc sidebar" data-title="Contents" data-levels="2">
      |          <div class="toc-menu"></div>
      |        </aside>
      |        <article class="doc">
      |          <h1 class="page">{{page.title}}</h1>
      |          {{{page.contents}}}
      |        </article>
      |      </div>
      |    </main>
      |  </div>
      |  {{> footer-content}}
      |  <script src="{{uiRootPath}}/js/site.js"></script>
      |</body>
      |</html>
      |""".stripMargin

  private def _default_ui_header(): String =
    """<header class="header">
      |  <nav class="navbar">
      |    <div class="navbar-brand">
      |      <a class="navbar-item" href="{{{or site.url siteRootPath}}}/">{{site.title}}</a>
      |      <button class="navbar-burger" aria-controls="topbar-nav" aria-expanded="false" aria-label="Toggle main menu">
      |        <span></span>
      |        <span></span>
      |        <span></span>
      |      </button>
      |    </div>
      |    <div id="topbar-nav" class="navbar-menu">
      |      <div class="navbar-end">
      |        <a class="navbar-item" href="{{siteRootPath}}/index.html">Home</a>
      |      </div>
      |    </div>
      |  </nav>
      |</header>
      |""".stripMargin

  private def _default_ui_or_helper(): String =
    """'use strict'
      |
      |module.exports = (...args) => {
      |  const numArgs = args.length
      |  if (numArgs === 3) return args[0] || args[1]
      |  if (numArgs < 3) throw new Error('{{or}} helper expects at least 2 arguments')
      |  args.pop()
      |  return args.some((it) => it)
      |}
      |""".stripMargin

  private def _default_ui_relativize_helper(): String =
    """'use strict'
      |
      |const { posix: path } = require('path')
      |
      |module.exports = (to, from, ctx) => {
      |  if (!to) return '#'
      |  if (to.charAt() !== '/') return to
      |  if (!ctx) from = (ctx = from).data.root.page.url
      |  if (!from) return (ctx.data.root.site.path || '') + to
      |  let hash = ''
      |  const hashIdx = to.indexOf('#')
      |  if (~hashIdx) {
      |    hash = to.slice(hashIdx)
      |    to = to.slice(0, hashIdx)
      |  }
      |  if (to === from) return hash || (isDir(to) ? './' : path.basename(to))
      |  const rel = path.relative(path.dirname(from + '.'), to)
      |  return rel ? (isDir(to) ? rel + '/' : rel) + hash : (isDir(to) ? './' : '../' + path.basename(to)) + hash
      |}
      |
      |function isDir (str) {
      |  return str.charAt(str.length - 1) === '/'
      |}
      |""".stripMargin

  private def _default_ui_css(): String =
    s"""body {
      |  margin: 0;
      |  color: #1f2933;
      |  background: #ffffff;
      |  font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif;
      |  line-height: 1.7;
      |}
      |
      |.header {
      |  border-bottom: 1px solid #d8dee4;
      |  background: #f7f9fb;
      |}
      |
      |.navbar {
      |  display: flex;
      |  align-items: center;
      |  gap: 2rem;
      |  min-height: 3.25rem;
      |  max-width: 72rem;
      |  margin: 0 auto;
      |  padding: 0 1rem;
      |}
      |
      |.navbar-brand {
      |  flex: 0 0 auto;
      |}
      |
      |.navbar-menu {
      |  display: flex;
      |  flex: 1 1 auto;
      |  justify-content: flex-end;
      |  min-width: 0;
      |}
      |
      |.navbar-end {
      |  display: flex;
      |  align-items: center;
      |  flex-wrap: wrap;
      |  justify-content: flex-end;
      |  gap: 0.25rem 1rem;
      |}
      |
      |.navbar-item {
      |  color: #1f2933;
      |  font-weight: 600;
      |  text-decoration: none;
      |  white-space: nowrap;
      |}
      |
      |.navbar-brand .navbar-item {
      |  font-size: 1.05rem;
      |}
      |
      |main.article {
      |  min-width: 0;
      |  padding: 2rem 2rem 4rem;
      |}
      |
      |.body {
      |  display: grid;
      |  grid-template-columns: minmax(12rem, 16rem) minmax(0, 1fr) minmax(11rem, 14rem);
      |  gap: 2rem;
      |  max-width: 88rem;
      |  margin: 0 auto;
      |  padding: 0 1rem;
      |}
      |
      |.body-dashboard {
      |  display: block;
      |  max-width: 112rem;
      |}
      |
      |.body-dashboard main.article {
      |  padding-left: 0;
      |  padding-right: 0;
      |}
      |
      |.body-dashboard .content {
      |  display: block;
      |}
      |
      |.nav,
      |.toc {
      |  color: #52616b;
      |  font-size: 0.92rem;
      |  padding-top: 2rem;
      |}
      |
      |.nav-container,
      |.toc-menu {
      |  position: sticky;
      |  top: 1rem;
      |}
      |
      |.nav-title,
      |.toc-menu h3 {
      |  margin: 0 0 0.75rem;
      |  color: #1f2933;
      |  font-size: 0.78rem;
      |  letter-spacing: 0.08em;
      |  text-transform: uppercase;
      |}
      |
      |.nav-list,
      |.toc-menu ul {
      |  list-style: none;
      |  margin: 0;
      |  padding: 0;
      |}
      |
      |.nav-list li,
      |.toc-menu li {
      |  margin: 0.35rem 0;
      |}
      |
      |.nav a,
      |.toc a {
      |  color: #34495e;
      |  text-decoration: none;
      |}
      |
      |.nav a:hover,
      |.toc a:hover {
      |  color: #0b5cad;
      |  text-decoration: underline;
      |}
      |
      |.nav-hint,
      |.toc-hint {
      |  margin: 0;
      |  color: #7b8794;
      |}
      |
      |@media (max-width: 64rem) {
      |  .body {
      |    display: block;
      |    max-width: 72rem;
      |  }
      |
      |  .nav,
      |  .toc {
      |    display: none;
      |  }
      |
      |  main.article {
      |    padding: 1.5rem 1rem 3rem;
      |  }
      |}
      |
      |.doc h1,
      |.doc h2,
      |.doc h3 {
      |  line-height: 1.3;
      |}
      |
      |.doc a {
      |  color: #0b5cad;
      |}
      |
      |.doc .reference-section {
      |  margin-top: 3rem;
      |  padding-top: 1rem;
      |  border-top: 1px solid #d8dee4;
      |}
      |
      |.container-fluid {
      |  width: 100%;
      |  box-sizing: border-box;
      |}
      |
      |.row {
      |  display: grid;
      |  grid-template-columns: repeat(12, minmax(0, 1fr));
      |}
      |
      |.g-3 {
      |  gap: 1rem;
      |}
      |
      |.col-12 {
      |  grid-column: span 12;
      |}
      |
      |.col-6 {
      |  grid-column: span 6;
      |}
      |
      |@media (min-width: 48rem) {
      |  .col-md-3 {
      |    grid-column: span 3;
      |  }
      |
      |  .col-md-6 {
      |    grid-column: span 6;
      |  }
      |}
      |
      |@media (min-width: 75rem) {
      |  .col-xl-2 {
      |    grid-column: span 2;
      |  }
      |
      |  .col-xl-3 {
      |    grid-column: span 3;
      |  }
      |
      |  .col-xl-4 {
      |    grid-column: span 4;
      |  }
      |
      |  .col-xl-5 {
      |    grid-column: span 5;
      |  }
      |
      |  .col-xl-6 {
      |    grid-column: span 6;
      |  }
      |
      |  .col-xl-7 {
      |    grid-column: span 7;
      |  }
      |
      |  .col-xl-8 {
      |    grid-column: span 8;
      |  }
      |}
      |
      |.card {
      |  height: 100%;
      |  background: #ffffff;
      |  border: 1px solid rgba(31, 41, 51, 0.08);
      |  border-radius: 16px;
      |  box-shadow: 0 10px 28px rgba(15, 23, 42, 0.08);
      |}
      |
      |.card-body {
      |  padding: 1rem;
      |}
      |
      |.card-title {
      |  margin: 0 0 0.75rem;
      |  color: #1f2933;
      |  font-size: 0.95rem;
      |  font-weight: 800;
      |  letter-spacing: 0.01em;
      |}
      |
      |.badge {
      |  display: inline-flex;
      |  align-items: center;
      |  border-radius: 999px;
      |  padding: 0.14rem 0.5rem;
      |  font-size: 0.72rem;
      |  font-weight: 700;
      |}
      |
      |.list-group {
      |  list-style: none;
      |  margin: 0;
      |  padding: 0;
      |}
      |
      |.list-group-item {
      |  display: flex;
      |  align-items: center;
      |  justify-content: space-between;
      |  gap: 0.75rem;
      |  padding: 0.48rem 0;
      |  border-bottom: 1px solid #eef2f7;
      |}
      |
      |.list-group-item:last-child {
      |  border-bottom: 0;
      |}
      |
      |.progress {
      |  height: 0.45rem;
      |  overflow: hidden;
      |  background: #e9ecef;
      |  border-radius: 999px;
      |}
      |
      |.bok-dashboard {
      |  margin: 1rem 0 1.75rem;
      |}
      |
      |.bok-card {
      |  position: relative;
      |  overflow: hidden;
      |}
      |
      |.bok-card::before {
      |  content: "";
      |  position: absolute;
      |  inset: 0 auto 0 0;
      |  width: 0.28rem;
      |  background: #228be6;
      |}
      |
      |.bok-card-purpose::before {
      |  background: #0ca678;
      |}
      |
      |.bok-card-readiness::before {
      |  background: #f08c00;
      |}
      |
      |.bok-card-kpi::before {
      |  background: #5c7cfa;
      |}
      |
      |.bok-card-chart::before {
      |  background: #15aabf;
      |}
      |
      |.bok-card-quality::before {
      |  background: #e67700;
      |}
      |
      |.bok-card-matrix::before,
      |.bok-card-map::before {
      |  background: #7048e8;
      |}
      |
      |.bok-card-quick-links::before,
      |.bok-card-actions::before,
      |.bok-card-related::before {
      |  background: #495057;
      |}
      |
      |.bok-purpose-vision {
      |  margin: 0 0 0.65rem;
      |  color: #243b53;
      |  font-size: 1rem;
      |}
      |
      |.bok-purpose-vision-panel {
      |  display: flex;
      |  align-items: flex-start;
      |  gap: 0.7rem;
      |  margin: 0 0 0.85rem;
      |  padding: 0.9rem 0.95rem 0.95rem 1rem;
      |  border: 1px solid rgba(255, 255, 255, 0.18);
      |  border-radius: 16px;
      |  background: rgba(255, 255, 255, 0.16);
      |  box-shadow: 0 10px 24px rgba(0, 0, 0, 0.14);
      |}
      |
      |.bok-purpose-vision-copy {
      |  display: grid;
      |  gap: 0.18rem;
      |  min-width: 0;
      |}
      |
      |.bok-purpose-vision-copy small {
      |  color: #bfdbfe;
      |  font-size: 0.66rem;
      |  font-weight: 900;
      |  letter-spacing: 0.08em;
      |  text-transform: uppercase;
      |}
      |
      |.bok-purpose-vision-copy strong {
      |  color: #fff;
      |  line-height: 1.45;
      |}
      |
      |.bok-purpose-list {
      |  margin-top: 0.45rem;
      |}
      |
      |.bok-purpose-list ul,
      |.bok-action-list {
      |  margin: 0.25rem 0 0;
      |  padding-left: 1.2rem;
      |}
      |
      |.bok-more,
      |.bok-card-muted {
      |  color: #6c757d;
      |}
      |
      |.bok-kpi-value {
      |  color: #172b4d;
      |  font-size: 2rem;
      |  font-weight: 850;
      |  line-height: 1;
      |}
      |
      |.bok-kpi-label {
      |  margin-top: 0.4rem;
      |  color: #1f2933;
      |  font-weight: 800;
      |}
      |
      |.bok-kpi-note {
      |  color: #687782;
      |  font-size: 0.82rem;
      |}
      |
      |.bok-definition-row {
      |  display: flex;
      |  align-items: center;
      |  justify-content: space-between;
      |  gap: 1rem;
      |  padding: 0.38rem 0;
      |  border-bottom: 1px solid #eef2f7;
      |}
      |
      |.bok-definition-row span {
      |  color: #687782;
      |}
      |
      |.bok-definition-row strong {
      |  color: #1f2933;
      |  text-align: right;
      |}
      |
      |.bok-badge-info {
      |  color: #0b5cad;
      |  background: #e7f5ff;
      |}
      |
      |.bok-alert-list .list-group-item {
      |  justify-content: flex-start;
      |}
      |
      |.bok-matrix-table {
      |  width: 100%;
      |  border-collapse: collapse;
      |  font-size: 0.86rem;
      |}
      |
      |.bok-matrix-table th,
      |.bok-matrix-table td {
      |  padding: 0.45rem 0.35rem;
      |  border-bottom: 1px solid #eef2f7;
      |  text-align: left;
      |}
      |
      |.bok-matrix-table th {
      |  color: #52616b;
      |  font-size: 0.75rem;
      |  letter-spacing: 0.05em;
      |  text-transform: uppercase;
      |}
      |
      |.bok-map-list .list-group-item {
      |  align-items: flex-start;
      |  flex-direction: column;
      |}
      |
      |.bok-map-list span {
      |  color: #687782;
      |  font-size: 0.82rem;
      |}
      |
      |.bok-dashboard-grid {
      |  display: grid;
      |  grid-template-columns: repeat(auto-fit, minmax(10rem, 1fr));
      |  gap: 1rem;
      |  margin: 1rem 0;
      |}
      |
      |.bok-metric-card {
      |  background: #ffffff;
      |  border: 1px solid #e9ecef;
      |  border-radius: 0.8rem;
      |  padding: 1rem;
      |  box-shadow: 0 6px 18px rgba(15, 23, 42, 0.06);
      |}
      |
      |.bok-metric-label {
      |  color: #52616b;
      |  font-size: 0.78rem;
      |  font-weight: 700;
      |  letter-spacing: 0.06em;
      |  text-transform: uppercase;
      |}
      |
      |.bok-metric-value {
      |  color: #172b4d;
      |  font-size: 1.9rem;
      |  font-weight: 850;
      |}
      |
      |.bok-metric-note {
      |  color: #687782;
      |  font-size: 0.82rem;
      |}
      |
      |.bok-dashboard-chart[data-chart="distribution-ratio"] {
      |  margin: 1rem 0 0;
      |}
      |
      |.bok-chart-row {
      |  display: grid;
      |  grid-template-columns: 5rem minmax(0, 1fr) 2.5rem;
      |  align-items: center;
      |  gap: 0.6rem;
      |  margin: 0.45rem 0;
      |  color: #52616b;
      |  font-size: 0.86rem;
      |}
      |
      |.bok-chart-row div {
      |  height: 0.55rem;
      |  overflow: hidden;
      |  background: #edf2f7;
      |  border-radius: 999px;
      |}
      |
      |.bok-chart-row b {
      |  display: block;
      |  height: 100%;
      |  background: linear-gradient(90deg, #5c7cfa, #15aabf);
      |  border-radius: 999px;
      |}
      |
      |.bok-chart-row em {
      |  color: #1f2933;
      |  font-style: normal;
      |  font-weight: 700;
      |  text-align: right;
      |}
      |
      |.bok-dashboard-chart[data-chart="cumulative-date"] {
      |  background: transparent;
      |  border-radius: 0;
      |  margin: 0;
      |  padding: 0;
      |  box-shadow: none;
      |}
      |
      |.bok-cumulative-chart-head {
      |  display: flex;
      |  align-items: baseline;
      |  flex-wrap: wrap;
      |  gap: 0.5rem 1rem;
      |  margin-bottom: 0.6rem;
      |}
      |
      |.bok-cumulative-chart-title {
      |  color: #1f2933;
      |  font-weight: 700;
      |}
      |
      |.bok-cumulative-chart-range,
      |.bok-cumulative-chart-scale {
      |  color: #5d5d5d;
      |  font-size: 0.82rem;
      |}
      |
      |.bok-cumulative-chart-svg {
      |  display: block;
      |  width: 100%;
      |  height: 12rem;
      |  overflow: visible;
      |}
      |
      |.bok-cumulative-axis-x,
      |.bok-cumulative-axis-y {
      |  stroke: #d8dee4;
      |  stroke-width: 0.7;
      |}
      |
      |.bok-cumulative-line {
      |  fill: none;
      |  stroke-width: 2.6;
      |  stroke-linecap: round;
      |  stroke-linejoin: round;
      |}
      |
      |.bok-cumulative-line-articles {
      |  stroke: #5c7cfa;
      |}
      |
      |.bok-cumulative-line-terms {
      |  stroke: #15aabf;
      |}
      |
      |.bok-cumulative-line-total {
      |  stroke: #0b7285;
      |}
      |
      |.bok-cumulative-points circle {
      |  stroke: #fff;
      |  stroke-width: 1.2;
      |}
      |
      |.bok-cumulative-point-articles {
      |  fill: #5c7cfa;
      |}
      |
      |.bok-cumulative-point-terms {
      |  fill: #15aabf;
      |}
      |
      |.bok-cumulative-point-total {
      |  fill: #0b7285;
      |}
      |
      |.bok-cumulative-legend {
      |  display: flex;
      |  flex-wrap: wrap;
      |  gap: 0.5rem 1rem;
      |  color: #424242;
      |  font-size: 0.85rem;
      |  margin: 0.25rem 0 0.6rem;
      |}
      |
      |.bok-cumulative-legend span {
      |  display: inline-flex;
      |  align-items: center;
      |  gap: 0.35rem;
      |}
      |
      |.bok-cumulative-marker {
      |  width: 0.7rem;
      |  height: 0.7rem;
      |  border-radius: 999px;
      |}
      |
      |.bok-cumulative-marker-articles {
      |  background: #5c7cfa;
      |}
      |
      |.bok-cumulative-marker-terms {
      |  background: #15aabf;
      |}
      |
      |.bok-cumulative-marker-total {
      |  background: #0b7285;
      |}
      |
      |.bok-cumulative-axis {
      |  display: flex;
      |  justify-content: space-between;
      |  gap: 0.75rem;
      |  color: #5d5d5d;
      |  font-size: 0.75rem;
      |  overflow-x: auto;
      |}
      |
      |.bok-cumulative-axis span {
      |  display: flex;
      |  flex-direction: column;
      |  align-items: center;
      |  min-width: 4.5rem;
      |}
      |
      |.bok-cumulative-axis em {
      |  color: #1f2933;
      |  font-style: normal;
      |  font-weight: 700;
      |}
      |
      |.navbar-category-dropdown {
      |  position: relative;
      |}
      |
      |.navbar-category-dropdown > .navbar-category-toggle {
      |  color: #fff;
      |  font-weight: 700;
      |}
      |
      |.navbar-category-dropdown > .navbar-category-menu {
      |  left: auto;
      |  right: 0;
      |  max-width: min(22rem, calc(100vw - 1rem));
      |}
      |
      |.navbar-category-dropdown:hover > .navbar-category-menu,
      |.navbar-category-dropdown:focus-within > .navbar-category-menu {
      |  display: block;
      |}
      |
      |@media screen and (max-width: 1023.5px) {
      |  .navbar-category-dropdown > .navbar-category-toggle {
      |    color: #1f2933;
      |  }
      |
      |  .navbar-category-dropdown > .navbar-category-menu {
      |    position: static;
      |    display: block;
      |    width: 100%;
      |    margin: 0.25rem 0 0.5rem;
      |    border-radius: 0.5rem;
      |    box-shadow: none;
      |  }
      |}
      |
      |.bok-category-matrix-link {
      |  color: #0f4f9f;
      |  font-weight: 850;
      |  text-decoration: none;
      |}
      |
      |.bok-category-matrix-link:hover {
      |  text-decoration: underline;
      |}
      |
      |.bok-category-summary-grid {
      |  display: grid;
      |  grid-template-columns: repeat(auto-fit, minmax(13rem, 1fr));
      |  gap: 0.75rem;
      |  margin-bottom: 1rem;
      |}
      |
      |.bok-category-summary-card {
      |  display: grid;
      |  gap: 0.45rem;
      |  padding: 0.9rem;
      |  color: #1f2937;
      |  text-decoration: none;
      |  background: #ffffff;
      |  border: 1px solid #e0e7ff;
      |  border-radius: 18px;
      |  box-shadow: 0 10px 24px rgba(15, 23, 42, 0.08);
      |}
      |
      |.bok-category-summary-card:hover,
      |.bok-category-summary-card:focus {
      |  color: #0f4f9f;
      |  text-decoration: none;
      |  transform: translateY(-1px);
      |  box-shadow: 0 16px 34px rgba(15, 23, 42, 0.13);
      |}
      |
      |.bok-category-summary-title {
      |  color: #111827;
      |  font-size: 1rem;
      |  font-weight: 900;
      |}
      |
      |.bok-category-summary-freshness {
      |  color: #64748b;
      |  font-size: 0.78rem;
      |}
      |
      |.bok-category-summary-metrics {
      |  display: grid;
      |  grid-template-columns: repeat(3, minmax(0, 1fr));
      |  gap: 0.45rem;
      |}
      |
      |.bok-category-summary-metrics span {
      |  display: flex;
      |  min-height: 3.1rem;
      |  flex-direction: column;
      |  justify-content: center;
      |  padding: 0.45rem;
      |  color: #475569;
      |  background: #f8fafc;
      |  border-radius: 12px;
      |  text-align: center;
      |  font-size: 0.72rem;
      |  font-weight: 800;
      |}
      |
      |.bok-category-summary-metrics b {
      |  color: #0f172a;
      |  font-size: 1.25rem;
      |  line-height: 1;
      |}
      |
      |.body.body-dashboard {
      |  max-width: 118rem;
      |  background: radial-gradient(circle at top left, rgba(92, 124, 250, 0.18), transparent 24rem), linear-gradient(135deg, #f4f7fb 0, #eef4ff 48%, #f8fafc 100%);
      |  border-radius: 28px;
      |  padding: 1.25rem;
      |}
      |
      |.body-dashboard main.article {
      |  padding-top: 0.75rem;
      |}
      |
      |.body-dashboard .doc {
      |  padding: 0 0 3rem;
      |}
      |
      |.body-dashboard h1.page {
      |  color: #fff;
      |  background: linear-gradient(135deg, #172b4d, #2554a6);
      |  border-radius: 22px;
      |  padding: 1.35rem 1.6rem;
      |  box-shadow: 0 18px 42px rgba(15, 23, 42, 0.18);
      |  letter-spacing: 0.01em;
      |}
      |
      |.bok-card {
      |  min-height: 100%;
      |  border: 0;
      |  border-radius: 22px;
      |  background: rgba(255, 255, 255, 0.92);
      |  box-shadow: 0 16px 42px rgba(15, 23, 42, 0.10), 0 1px 0 rgba(255, 255, 255, 0.75) inset;
      |  backdrop-filter: blur(6px);
      |  transition: transform 0.18s ease, box-shadow 0.18s ease;
      |}
      |
      |.bok-card:hover {
      |  transform: translateY(-2px);
      |  box-shadow: 0 22px 54px rgba(15, 23, 42, 0.14), 0 1px 0 rgba(255, 255, 255, 0.8) inset;
      |}
      |
      |.bok-card::before {
      |  width: 0.42rem;
      |}
      |
      |.bok-card .card-body {
      |  padding: 1.1rem 1.2rem 1.15rem;
      |}
      |
      |.bok-card .card-title {
      |  display: flex;
      |  align-items: center;
      |  gap: 0.5rem;
      |  margin-bottom: 0.9rem;
      |  color: #334155;
      |  font-size: 0.78rem;
      |  font-weight: 900;
      |  letter-spacing: 0.1em;
      |  text-transform: uppercase;
      |}
      |
      |.bok-card .card-title::before {
      |  content: "";
      |  width: 0.62rem;
      |  height: 0.62rem;
      |  border-radius: 999px;
      |  background: currentColor;
      |  opacity: 0.48;
      |}
      |
      |.bok-card-purpose {
      |  color: #f8fafc;
      |  background: linear-gradient(135deg, #0f766e 0, #0f4f78 100%);
      |}
      |
      |.bok-card-purpose .card-title,
      |.bok-card-purpose .bok-purpose-vision,
      |.bok-card-purpose .bok-purpose-list strong,
      |.bok-card-purpose .bok-more {
      |  color: #fff;
      |}
      |
      |.bok-card-purpose li {
      |  color: #dff7f2;
      |}
      |
      |.bok-card-readiness {
      |  background: linear-gradient(135deg, #fff7ed, #ffffff);
      |}
      |
      |.bok-card-kpi {
      |  background: linear-gradient(160deg, #ffffff 0, #f8fbff 60%, #edf4ff 100%);
      |}
      |
      |.bok-card-kpi .card-body {
      |  min-height: 8.4rem;
      |  display: flex;
      |  flex-direction: column;
      |  justify-content: center;
      |}
      |
      |.bok-kpi-value {
      |  color: #0f3d73;
      |  font-size: 2.85rem;
      |  font-weight: 900;
      |  letter-spacing: -0.05em;
      |}
      |
      |.bok-kpi-label {
      |  font-size: 0.92rem;
      |  letter-spacing: 0.03em;
      |  text-transform: uppercase;
      |}
      |
      |.bok-kpi-note {
      |  margin-top: 0.2rem;
      |}
      |
      |.bok-card-chart {
      |  background: linear-gradient(180deg, #ffffff, #f8fbff);
      |}
      |
      |.bok-card-chart .bok-dashboard-chart {
      |  min-height: 15rem;
      |}
      |
      |.bok-cumulative-chart-svg {
      |  height: 14rem;
      |}
      |
      |.bok-card-quality {
      |  background: linear-gradient(160deg, #fffaf0, #fff);
      |}
      |
      |.bok-card-matrix,
      |.bok-card-map {
      |  background: linear-gradient(160deg, #ffffff, #faf8ff);
      |}
      |
      |.bok-card-quick-links,
      |.bok-card-actions,
      |.bok-card-related {
      |  background: linear-gradient(160deg, #ffffff, #f8fafc);
      |}
      |
      |.bok-alert-list .list-group-item {
      |  border-radius: 12px;
      |  margin: 0.35rem 0;
      |  padding: 0.55rem 0.65rem;
      |  background: #fff7ed;
      |  border-bottom: 0;
      |}
      |
      |.bok-action-list li {
      |  margin: 0.38rem 0;
      |}
      |
      |.bok-chart-row div {
      |  height: 0.78rem;
      |  background: #e2e8f0;
      |}
      |
      |.bok-chart-row b {
      |  background: linear-gradient(90deg, #2563eb, #06b6d4);
      |}
      |
      |@media (max-width: 48rem) {
      |  .body.body-dashboard {
      |    border-radius: 0;
      |    padding: 0.75rem;
      |  }
      |
      |  .body-dashboard h1.page {
      |    border-radius: 18px;
      |  }
      |
      |  .bok-kpi-value {
      |    font-size: 2.2rem;
      |  }
      |}
      |
      |.body.body-dashboard {
      |  max-width: none;
      |  margin: 0;
      |  padding: 0;
      |  background: #edf3fb;
      |}
      |
      |.body-dashboard .content {
      |  display: block;
      |}
      |
      |.body-dashboard .doc {
      |  max-width: none;
      |  margin: 0;
      |  padding: 0 1.25rem 3rem;
      |}
      |
      |.bok-dashboard-shell {
      |  max-width: 118rem;
      |  margin: 0 auto;
      |  padding: 1.25rem 0 2rem;
      |}
      |
      |.bok-dashboard-hero {
      |  display: grid;
      |  grid-template-columns: minmax(0, 1fr) minmax(18rem, 26rem);
      |  gap: 1.25rem;
      |  align-items: stretch;
      |  margin: 0 0 1.25rem;
      |  padding: 1.4rem;
      |  color: #fff;
      |  background: radial-gradient(circle at 14% 8%, rgba(255, 255, 255, 0.24), transparent 20rem), linear-gradient(135deg, #0f2742 0, #214f95 48%, #0f766e 100%);
      |  border-radius: 28px;
      |  box-shadow: 0 24px 64px rgba(15, 23, 42, 0.22);
      |}
      |
      |.bok-dashboard-hero h1.page {
      |  margin: 0 !important;
      |  padding: 0 !important;
      |  color: #fff !important;
      |  background: transparent !important;
      |  border-radius: 0 !important;
      |  box-shadow: none !important;
      |  font-size: clamp(2rem, 4vw, 4rem);
      |  line-height: 0.95;
      |  letter-spacing: -0.05em;
      |}
      |
      |.bok-dashboard-eyebrow {
      |  margin: 0 0 0.65rem;
      |  color: #bfe7ff;
      |  font-size: 0.78rem;
      |  font-weight: 900;
      |  letter-spacing: 0.22em;
      |  text-transform: uppercase;
      |}
      |
      |.bok-dashboard-lead {
      |  max-width: 54rem;
      |  margin: 0.9rem 0 0;
      |  color: #e6f2ff;
      |  font-size: 1.02rem;
      |  line-height: 1.65;
      |}
      |
      |.bok-dashboard-hero-facts {
      |  display: grid;
      |  grid-template-columns: repeat(3, minmax(0, 1fr));
      |  gap: 0.75rem;
      |  align-content: end;
      |}
      |
      |.bok-dashboard-hero-fact {
      |  display: flex;
      |  min-height: 7rem;
      |  flex-direction: column;
      |  justify-content: center;
      |  padding: 0.9rem;
      |  text-align: center;
      |  background: rgba(255, 255, 255, 0.12);
      |  border: 1px solid rgba(255, 255, 255, 0.18);
      |  border-radius: 20px;
      |}
      |
      |.bok-dashboard-hero-fact strong {
      |  color: #fff;
      |  font-size: 2.35rem;
      |  font-weight: 950;
      |  line-height: 1;
      |  text-decoration: none;
      |}
      |
      |.bok-dashboard-hero-fact em {
      |  margin-top: 0.45rem;
      |  color: #d7ecff;
      |  font-size: 0.75rem;
      |  font-style: normal;
      |  font-weight: 800;
      |  letter-spacing: 0.08em;
      |  text-transform: uppercase;
      |}
      |
      |.bok-dashboard.container-fluid {
      |  padding-left: 0;
      |  padding-right: 0;
      |}
      |
      |.bok-dashboard > .row {
      |  --bs-gutter-x: 1.15rem;
      |  --bs-gutter-y: 1.15rem;
      |}
      |
      |.navbar-category-nav {
      |  display: inline-flex;
      |  align-items: center;
      |  margin-left: 0.3rem;
      |}
      |
      |.navbar-category-dropdown > .navbar-category-toggle {
      |  min-height: 2.2rem;
      |  padding: 0.35rem 0.85rem;
      |  color: #fff !important;
      |  background: rgba(255, 255, 255, 0.12);
      |  border: 1px solid rgba(255, 255, 255, 0.18);
      |  border-radius: 999px;
      |  font-weight: 800;
      |}
      |
      |.navbar-category-dropdown > .navbar-category-toggle:hover,
      |.navbar-category-dropdown > .navbar-category-toggle:focus {
      |  color: #fff !important;
      |  background: rgba(255, 255, 255, 0.22);
      |  text-decoration: none;
      |}
      |
      |@media (max-width: 64rem) {
      |  .bok-dashboard-hero {
      |    grid-template-columns: 1fr;
      |  }
      |}
      |
      |@media (max-width: 48rem) {
      |  .body-dashboard .doc {
      |    padding-left: 0.75rem;
      |    padding-right: 0.75rem;
      |  }
      |
      |  .bok-dashboard-hero,
      |  .bok-dashboard-hero-facts {
      |    grid-template-columns: 1fr;
      |  }
      |}
      |
      |.bok-purpose-tree {
      |  display: grid;
      |  gap: 0.65rem;
      |  margin-top: 0.85rem;
      |}
      |
      |.bok-purpose-goal {
      |  position: relative;
      |  padding: 0.72rem 0.78rem 0.72rem 0.95rem;
      |  background: rgba(255, 255, 255, 0.12);
      |  border: 1px solid rgba(255, 255, 255, 0.16);
      |  border-radius: 16px;
      |}
      |
      |.bok-purpose-goal::before {
      |  content: "";
      |  position: absolute;
      |  left: 0.43rem;
      |  top: 1.05rem;
      |  bottom: 0.78rem;
      |  width: 2px;
      |  background: rgba(255, 255, 255, 0.28);
      |}
      |
      |.bok-purpose-goal-head {
      |  display: flex;
      |  align-items: flex-start;
      |  gap: 0.55rem;
      |}
      |
      |.bok-purpose-goal-head strong {
      |  color: #fff;
      |  line-height: 1.45;
      |}
      |
      |.bok-purpose-node-label {
      |  display: inline-flex;
      |  flex: 0 0 auto;
      |  align-items: center;
      |  justify-content: center;
      |  min-width: 1.7rem;
      |  height: 1.35rem;
      |  color: #fff;
      |  background: rgba(255, 255, 255, 0.20);
      |  border-radius: 999px;
      |  font-size: 0.68rem;
      |  font-weight: 900;
      |  letter-spacing: 0.04em;
      |}
      |
      |.bok-purpose-subgoals {
      |  display: grid;
      |  gap: 0.42rem;
      |  margin: 0.55rem 0 0 2.25rem;
      |  padding: 0;
      |  list-style: none;
      |}
      |
      |.bok-purpose-subgoals li {
      |  position: relative;
      |  display: flex;
      |  align-items: flex-start;
      |  gap: 0.5rem;
      |  color: #dff7f2;
      |  line-height: 1.45;
      |}
      |
      |.bok-purpose-subgoals li::before {
      |  content: "";
      |  position: absolute;
      |  left: -1.35rem;
      |  top: 0.68rem;
      |  width: 1.05rem;
      |  height: 2px;
      |  background: rgba(255, 255, 255, 0.28);
      |}
      |
      |.bok-purpose-unassigned {
      |  margin-top: 0.65rem;
      |  padding: 0.65rem 0.75rem;
      |  background: rgba(255, 255, 255, 0.10);
      |  border: 1px dashed rgba(255, 255, 255, 0.30);
      |  border-radius: 14px;
      |}
      |
      |.bok-purpose-unassigned > strong {
      |  color: #fff;
      |}
      |
      |.body-dashboard .toolbar {
      |  display: none !important;
      |}
      |
      |.body.body-dashboard {
      |  min-height: calc(100vh - 3.5rem);
      |  background: #0b1220 !important;
      |  background-image: radial-gradient(circle at 10% 0, rgba(34, 197, 94, 0.22), transparent 24rem), radial-gradient(circle at 86% 10%, rgba(59, 130, 246, 0.28), transparent 28rem), linear-gradient(135deg, #0b1220 0, #111827 48%, #172554 100%) !important;
      |}
      |
      |.body-dashboard .doc {
      |  color: #dbeafe;
      |}
      |
      |.bok-dashboard-shell strong,
      |.bok-dashboard-shell b,
      |.bok-dashboard-shell em {
      |  text-decoration: none !important;
      |}
      |
      |.bok-dashboard-hero {
      |  border: 1px solid rgba(148, 163, 184, 0.28);
      |  background: linear-gradient(135deg, rgba(15, 23, 42, 0.94), rgba(30, 64, 175, 0.78) 55%, rgba(20, 83, 45, 0.82)) !important;
      |  box-shadow: 0 30px 80px rgba(0, 0, 0, 0.42), 0 0 0 1px rgba(255, 255, 255, 0.06) inset;
      |}
      |
      |.bok-dashboard-hero-fact {
      |  min-height: 8.2rem;
      |  background: linear-gradient(180deg, rgba(255, 255, 255, 0.18), rgba(255, 255, 255, 0.08));
      |  box-shadow: 0 18px 36px rgba(0, 0, 0, 0.22), 0 1px 0 rgba(255, 255, 255, 0.18) inset;
      |}
      |
      |.bok-dashboard-hero-fact strong {
      |  font-size: 3rem;
      |  letter-spacing: -0.08em;
      |}
      |
      |.bok-dashboard > .row {
      |  --bs-gutter-x: 1.35rem;
      |  --bs-gutter-y: 1.35rem;
      |}
      |
      |.bok-card {
      |  background: #f8fafc !important;
      |  border: 1px solid rgba(148, 163, 184, 0.20) !important;
      |  border-radius: 26px !important;
      |  box-shadow: 0 22px 56px rgba(0, 0, 0, 0.28) !important;
      |}
      |
      |.bok-card::before {
      |  width: 100% !important;
      |  height: 0.42rem !important;
      |  inset: 0 0 auto 0 !important;
      |}
      |
      |.bok-card-purpose {
      |  background: linear-gradient(135deg, #0f766e 0, #164e63 50%, #1e3a8a 100%) !important;
      |}
      |
      |.bok-card-kpi .card-body {
      |  min-height: 10.5rem !important;
      |}
      |
      |.bok-kpi-value {
      |  color: #0f172a !important;
      |  font-size: 3.45rem !important;
      |}
      |""".stripMargin


  private def _project(parsed: ParsedArgs): Path =
    _resolve_bok_project(parsed.pathProperty("project-dir").
      orElse(parsed.pathProperty("project")).
      orElse(parsed.argument("project").map(_to_path)).
      getOrElse(_logical_cwd))

  private def _category_project(parsed: ParsedArgs): Path =
    _resolve_bok_project(parsed.pathProperty("project-dir").
      orElse(parsed.pathProperty("project")).
      getOrElse(_logical_cwd))

  private def _resolve_bok_project(input: Path): Path =
    _find_bok_root(input).getOrElse(input.toAbsolutePath.normalize)

  private def _find_bok_root(input: Path): Option[Path] = {
    val start = _existing_directory(input.toAbsolutePath.normalize)
    Iterator.iterate(Option(start))(_.flatMap(x => Option(x.getParent))).
      takeWhile(_.nonEmpty).
      flatten.
      find(_is_bok_root)
  }

  private def _existing_directory(path: Path): Path =
    if (Files.isRegularFile(path))
      Option(path.getParent).getOrElse(path)
    else
      path

  private def _is_bok_root(path: Path): Boolean =
    Files.isDirectory(path.resolve("src/main/doxsite")) ||
      Files.isRegularFile(path.resolve("src/main/doxsite/site.conf")) ||
      _has_bok_config(path)

  private def _has_bok_config(path: Path): Boolean =
    CozyProjectYamlConfig.operationDefaultFiles(path).filter(x => Files.isRegularFile(x)).exists { file =>
      val content = _read_text(file)
      content.contains("bok:") || content.contains("\"bok\"") || content.contains("bok.")
    }

  private def _load_config(project: Path): CozyProjectYamlConfig.Config =
    CozyProjectYamlConfig.loadOperationDefaults(project)

  private def _load_site_config(source: Path): SiteConfig = {
    val file = source.resolve("site.conf")
    if (!Files.isRegularFile(file))
      SiteConfig.empty
    else {
      val lines = Files.readAllLines(file, StandardCharsets.UTF_8).asScala.toVector
      SiteConfig(_parse_site_values(lines), _parse_site_lists(lines), _parse_site_goal_trees(lines))
    }
  }

  private def _to_path(value: Any): Path = value match {
    case m: java.io.File => m.toPath.toAbsolutePath.normalize
    case m: Path => m.toAbsolutePath.normalize
    case m =>
      val path = Paths.get(m.toString)
      if (path.isAbsolute)
        path.normalize
      else
        _logical_cwd.resolve(path).normalize
  }

  private def _logical_cwd: Path =
    sys.env.get("PWD").map(Paths.get(_).toAbsolutePath.normalize).getOrElse(Paths.get(".").toAbsolutePath.normalize)

  private def _boolean(config: CozyProjectYamlConfig.Config, path: String, default: Boolean): Boolean =
    config.boolean(path).getOrElse(default)

  private def _strategy(parsed: ParsedArgs): String =
    _strategy(parsed, "wip")

  private def _strategy(parsed: ParsedArgs, default: String): String =
    parsed.property("strategy").getOrElse(default) match {
      case "wip" => "work-in-progress"
      case "draft" => "draft"
      case "preview" => "production-preview"
      case "production" => "production"
      case other => other
    }

  private def _publication_settings(
    parsed: ParsedArgs,
    config: CozyProjectYamlConfig.Config,
    strategy: String
  ): PublicationSettings = {
    val publication = parsed.pathProperty("publication").
      map(_.toString).
      orElse(config.value("bok.publication")).
      getOrElse("src/main/publication")
    val warehouse = parsed.pathProperty("warehouse").
      map(_.toString).
      orElse(config.value("bok.warehouse")).
      getOrElse("warehouse")
    val merge = _boolean(config, "bok.rdf.merge-publication-artifacts", true)
    val defaultpolicy = if (strategy == "production") "fail" else "warn"
    val missingpolicy = parsed.property("rdf-missing-artifact-policy").
      orElse(config.value("bok.rdf.missing-artifact-policy")).
      getOrElse(defaultpolicy)
    PublicationSettings(publication, warehouse, merge, missingpolicy)
  }

  private val _generated_gitignore_entries = Vector(
    "/target/",
    "/website.d/",
    "/doxsite.d/",
    "/antora.d/",
    "/repository.d/",
    "/.bsp/",
    "/.metals/",
    "/.idea/"
  )

  private def _inspect_bok(input: Path): BokInspection = {
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
    missingconfig ++ olddocker ++ missingsource ++ missingsite ++ gitignoreissue ++ missingupload
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
    createconfig ++ updatedocker ++ gitignorefix
  }

  private val _bok_guide_scenarios: Vector[(String, String, Vector[String])] = Vector(
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
        s"6. cozy bok preview --port ${_default_preview_port}",
        s"7. open http://127.0.0.1:${_default_preview_port}/ in a browser; use the local Web server instead of opening website.d directly"
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
        "4. cozy bok publish-video . --warehouse <warehouse-dir>",
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

  private def _print_bok_guide_scenario(name: String, title: String, lines: Vector[String]): Unit = {
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

  private def _print_bok_inspection(inspection: BokInspection, config: DoctorConfig): Unit = {
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

  private def _apply_bok_fixes(inspection: BokInspection, config: DoctorConfig): Unit =
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

  private def _languages(config: CozyProjectYamlConfig.Config): Vector[String] =
    config.list("site.metadata.in_language") match {
      case xs if xs.nonEmpty => xs
      case _ =>
        config.value("site.metadata.in_language").map { x =>
          x.stripPrefix("[").stripSuffix("]").split(",").toVector.map(_.trim.stripPrefix("\"").stripSuffix("\"")).filter(_.nonEmpty)
        }.getOrElse(Vector("ja", "en"))
    }

  private def _languages(config: CozyProjectYamlConfig.Config, site: SiteConfig): Vector[String] =
    _languages(config) match {
      case xs if xs != Vector("ja", "en") => xs
      case _ =>
        site.list("site.metadata.in_language") match {
          case xs if xs.nonEmpty => xs
          case _ => site.value("site.metadata.in_language").map(_parse_inline_list).filter(_.nonEmpty).getOrElse(Vector("ja", "en"))
        }
    }

  private def _locale_mode(config: CozyProjectYamlConfig.Config, site: SiteConfig): LocaleMode =
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

  private def _default_locale(
    config: CozyProjectYamlConfig.Config,
    site: SiteConfig,
    languages: Vector[String]
  ): String =
    config.value("site.output.default_locale").
      orElse(config.value("bok.output.default_locale")).
      orElse(site.value("site.output.default_locale")).
      getOrElse(languages.headOption.getOrElse("ja"))

  private def _direct_assets(project: Path, config: CozyProjectYamlConfig.Config): DirectAssetsConfig = {
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

  private def _parse_key_value(value: String): Option[(String, String)] = {
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

  private def _parse_site_values(lines: Vector[String]): Map[String, String] = {
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

  private def _parse_site_lists(lines: Vector[String]): Map[String, Vector[String]] =
    _parse_site_values(lines).collect {
      case (key, value) if value.startsWith("[") && value.endsWith("]") =>
        key -> _parse_inline_list(value)
    } ++ _parse_site_block_lists(lines)

  private def _parse_site_goal_trees(lines: Vector[String]): Map[String, Vector[BokGoal]] = {
    val candidates = Set("site.metadata.goals", "site.metadata.goal_tree")
    var stack = Vector.empty[(Int, String)]
    var result = Map.empty[String, Vector[BokGoal]]
    var collecting: Option[(Int, String, Vector[BokGoal], Option[BokGoal], Boolean, Vector[String])] = None

    def flushCurrent(path: String, goals: Vector[BokGoal], current: Option[BokGoal]): Vector[BokGoal] =
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
            result = result.updated(path, flushCurrent(path, goals, current))
            collecting = None
          } else if (trimmed == "{" || trimmed == "{") {
            collecting = Some((baseindent, path, flushCurrent(path, goals, current), Some(BokGoal("", Vector.empty)), false, Vector.empty))
          } else if (trimmed == "}" || trimmed == "},") {
            collecting = Some((baseindent, path, flushCurrent(path, goals, current), None, false, Vector.empty))
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
        result = result.updated(path, flushCurrent(path, goals, current))
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

  private def _parse_inline_list(value: String): Vector[String] =
    value.stripPrefix("[").stripSuffix("]").split(",").toVector.map(x => _unquote(x.trim)).filter(_.nonEmpty)

  private def _unquote(value: String): String = {
    val s = value.trim
    if (s.length >= 2 && ((s.head == '"' && s.last == '"') || (s.head == '\'' && s.last == '\'')))
      s.substring(1, s.length - 1)
    else
      s
  }

  private def _cozy_config(): String =
    _cozy_config(None)

  private def _cozy_config(config: Option[CreateConfig]): String =
    s"""cozy:
       |  docker-image: ${_default_docker_image}
       |
       |bok:
       |  source: src/main/doxsite
       |  publication: src/main/publication
       |  warehouse: warehouse
       |  strategy: production
       |  website: website.d
       |  website-staging: ${config.map(_default_website_staging).getOrElse("../website-staging")}
       |  antora: antora.d
       |  doxsite: doxsite.d
       |  ui-bundle: src/main/antora-ui/build/ui-bundle.zip
       |  rdf:
       |    merge-publication-artifacts: true
       |  video:
       |    enabled: true
       |    force: false
       |  arcadia:
       |    enabled: false
       |    source: src/main/arcadiasite
       |  direct-assets:
       |    enabled: false
       |    items: []
       |  workflow:
       |    stage:
       |      # Copy etc/website-stage.sh.proto to etc/website-stage.sh and configure it.
       |      command: ""
       |    upload:
       |      # Copy etc/website-upload.sh.proto to etc/website-upload.sh and configure it.
       |      command: ""
       |""".stripMargin

  private def _readme(config: CreateConfig): String =
    s"""# ${config.name}
       |
       |This project is a SmartDox BoK source project for ${config.name}.
       |
       |- Operation URL: ${config.url}
       |- Main language: ${config.language}
       |- Site source: `src/main/doxsite`
       |- Generated public site files and publication repositories are intentionally outside this source scaffold.
       |
       |Category structure is defined by directories that contain `category.yaml`.
       |""".stripMargin

  private def _structure(config: CreateConfig): String =
    s"""# BoK Structure
       |
       |`src/main/doxsite` is the cozy-generated SmartDox source tree.
       |`site.conf` holds site metadata such as name, URL, language, license, navigation mode, and output mode.
       |
       |Directories with `category.yaml` are BoK categories.
       |`knowledgehub/` contains KnowledgeHub framework articles.
       |`book-knowledge/` contains Book/RDF/embedding articles.
       |`glossary/` contains terms used for automatic glossary linking.
       |`history/` contains operation history entries.
       |`manual/` contains Cozy BoK operation guidance.
       |`rdf/` contains minimal RDF and JSON-LD machine-readable placeholders.
       |`src/main/antora-ui/build/ui-bundle.zip` is a minimal local Antora UI bundle for offline BoK generation.
       |`assets/css/` contains restrained reading CSS only.
       |
       |Only source files, metadata, RDF seeds, glossary terms, and minimal CSS are generated here.
       |""".stripMargin

  private def _site_conf(config: CreateConfig): String =
    s"""site {
       |  metadata {
       |    name = "${config.name}"
       |    url = "${config.url}"
       |    in_language = ["${config.language}"]
       |    license = "CC-BY-SA-4.0"
       |    # Select one of: aurora, lagoon, meadow, ocean, ember, slate
       |    dashboard_color_group = "aurora"
       |    vision = "Build a shared knowledge base for ${config.name}."
       |    goals = [
       |      {
       |        title = "Organize concepts and technology knowledge"
       |        subgoals = ["Maintain category dashboards"]
       |      },
       |      {
       |        title = "Keep knowledge searchable and reusable"
       |        subgoals = ["Maintain glossary and history"]
       |      }
       |    ]
       |  }
       |  navigation {
       |    mode = "category"
       |  }
       |  output {
       |    locale_mode = "single_locale_root"
       |    default_locale = "${config.language}"
       |  }
       |  header {
       |    language_toggle = false
       |  }
       |}
       |
       |output.scope.policy = home_only
       |""".stripMargin

  private def _today: String =
    LocalDate.now.toString

  private def _site_index(config: CreateConfig): String =
    s"""Home
       |======
       |
       |# HEAD
       |
       |status=work-in-progress
       |published_at=${_today}
       |
       |## HEADLINE
       |${config.name}
       |
       |## BRIEF
       |${config.name} の目的、対象範囲、運用方針を説明するHome narrative。
       |
       |# Overview
       |
       |${config.name} is a BoK site for organizing KnowledgeHub concepts, book knowledge materialization, RDF vocabulary, and operation terms.
       |
       |## BoK Console
       |
       |- `glossary/index.dox`: BoK内で共有する用語集。
       |- `history/index.dox`: BoK運用の更新履歴。
       |- `manual/index.dox`: BoK運用マニュアル。
       |
       |## Operation Focus
       |
       |このBoKはSmartDox本文、Category、RDF素材、用語自動リンクを中心に運用します。
       |日本語単独運用のため、生成HTMLはサイトroot直下に配置します。
       |""".stripMargin

  private def _category(
    name: String,
    title: String,
    description: String,
    purpose: BokPurpose = BokPurpose.empty
  ): String =
    s"""name: ${name}
       |title: ${title}
       |description:
       |  en: ${description}
       |  ja: ${description}
       |${_purpose_yaml(purpose)}
       |""".stripMargin

  private def _category_index(
    category: String,
    title: String,
    purpose: String,
    articles: Vector[CategoryArticle] = Vector.empty,
    terms: Vector[CategoryTerm] = Vector.empty
  ): String =
    s"""${title}
       |======
       |
       |# HEAD
       |
       |status=work-in-progress
       |published_at=${_today}
       |
       |## HEADLINE
       |${title}
       |
       |## BRIEF
       |${purpose}
       |
       |# Overview
       |
       |${purpose}
       |""".stripMargin

  private def _purpose_yaml(purpose: BokPurpose): String =
    if (purpose.isEmpty)
      ""
    else
      Vector(
        purpose.vision.map(x => s"vision: ${_yaml_quote(x)}"),
        if (purpose.goals.nonEmpty) Some(_yaml_goal_tree_block("goals", purpose.goals)) else None,
        if (purpose.flatGoals.nonEmpty) Some(_yaml_list_block("goals", purpose.flatGoals)) else None,
        if (purpose.flatSubgoals.nonEmpty) Some(_yaml_list_block("subgoals", purpose.flatSubgoals)) else None
      ).flatten.mkString("", "\n", "\n")

  private def _yaml_goal_tree_block(key: String, values: Vector[BokGoal]): String =
    values.filterNot(_.isEmpty).map { goal =>
      val subgoals =
        if (goal.subgoals.isEmpty)
          ""
        else
          goal.subgoals.map(x => s"      - ${_yaml_quote(x)}").mkString("\n    subgoals:\n", "\n", "")
      s"  - title: ${_yaml_quote(goal.title)}${subgoals}"
    }.mkString(s"${key}:\n", "\n", "")

  private def _yaml_list_block(key: String, values: Vector[String]): String =
    values.map(x => s"  - ${_yaml_quote(x)}").mkString(s"${key}:\n", "\n", "")

  private def _yaml_quote(value: String): String =
    "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\""

  private def _dashboard_bar_width(value: Int, total: Int): Int =
    if (total <= 0)
      8
    else
      math.max(8, math.round(value.toDouble / total.toDouble * 100.0).toInt)

  private def _glossary_index(): String =
    s"""用語集
      |======
      |
      |# HEAD
      |
      |status=work-in-progress
      |published_at=${_today}
      |
      |## HEADLINE
      |用語集
      |
      |## BRIEF
      |BoK全体で共有する用語と概念のDashboard。
      |
      |# Dashboard
      |
      |用語集はBoK全体の語彙、カテゴリ横断の概念、ドメイン固有語を集約します。
      |
      |## Quick Links
      |
      |- <a href="../index.html">BoK Home</a>
      |- <a href="../history/index.html">History</a>
      |
      |## Term Groups
      |
      |- Terms in categories: `glossary/<category>/` にあるカテゴリ別用語。
      |- Shared terms: この用語集直下に置くBoK横断用語。
      |
      |## Operation Notes
      |
      |用語はSmartDoxの自動リンクとRDF/JSON-LD連携の基盤です。新しいカテゴリ用語を追加した場合は、カテゴリトップページと用語集Dashboardの両方から辿れるようにします。
      |""".stripMargin

  private def _history_index(): String =
    s"""History
      |=======
      |
      |# HEAD
      |
      |status=work-in-progress
      |published_at=${_today}
      |
      |## HEADLINE
      |History
      |
      |## BRIEF
      |BoK運用、更新履歴、公開履歴のDashboard。
      |
      |# Dashboard
      |
      |HistoryはBoKの変更、公開、運用イベントを集約するページです。
      |
      |## Quick Links
      |
      |- <a href="../index.html">BoK Home</a>
      |- <a href="../glossary/index.html">Glossary</a>
      |
      |## Timeline
      |
      |- 2026: Cozy BoK source and site operations started.
      |
      |## Operation Notes
      |
      |公開、構成変更、カテゴリ追加、重要な用語変更はここに記録します。
      |""".stripMargin

  private def _manual_index(): String =
    s"""BoK Manual
      |==========
      |
      |# HEAD
      |
      |status=work-in-progress
      |published_at=${_today}
      |
      |## HEADLINE
      |BoK Manual
      |
      |## BRIEF
      |Cozy BoK source and site operation manual.
      |
      |# Dashboard
      |
      |このManualはBoKの作成、カテゴリ追加、ビルド、プレビュー、公開準備の入口です。
      |
      |## Quick Links
      |
      |- <a href="../index.html">BoK Home</a>
      |- <a href="../glossary/index.html">Glossary</a>
      |- <a href="../history/index.html">History</a>
      |
      |## Basic Operations
      |
      |- `cozy bok create --save <dir>`: BoK source scaffoldを作成します。
      |- `cozy bok create-category <name> --project <dir>`: カテゴリDashboard、記事seed、用語seedを追加します。
      |- `cozy bok build <dir> --strategy wip`: SmartDox/Antoraを使って `website.d` を生成します。
      |- `cozy bok preview <dir> --port 8980`: 生成済み `website.d` をローカル確認します。
      |
      |## Page Types
      |
      |- Home: BoK全体Dashboard。
      |- Category Dashboard: カテゴリ単位のKPI、記事、用語、運用メモ。
      |- Glossary: BoK全体の語彙Dashboard。
      |- History: BoK運用と公開履歴Dashboard。
      |- Manual: BoK運用手順の入口。
      |
       |## Operation Notes
       |
       |BoKの標準ページはDashboardとして扱い、通常記事とは異なる情報集約ページにします。Glossary、History、Manualはカテゴリ一覧ではなくBoK Consoleとして扱います。
       |Manualは運用手順ページなので、SmartDoxの自動用語リンク対象外です。
       |""".stripMargin

  private def _category_name(name: String): String =
    name.split("[^A-Za-z0-9]+").toVector.filter(_.nonEmpty).map { part =>
      part.head.toUpper + part.tail
    }.mkString match {
      case "" => "Category"
      case x => x
    }

  private def _article(title: String, purpose: String): String =
    s"""${title}
       |======
       |
       |# HEAD
       |
       |status=work-in-progress
       |published_at=${_today}
       |
       |## HEADLINE
       |${title}
       |
       |## BRIEF
       |${purpose}
       |
       |# 目的
       |
       |${purpose}
       |
       |# 執筆メモ
       |
       |This is a cozy-generated BoK article seed.
       |""".stripMargin

  private def _glossary(title: String, definition: String, reading: Option[String]): String = {
    val headproperties =
      (Vector("status=work-in-progress", s"published_at=${_today}") ++ reading.toVector.map(x => s"reading=${x}")).mkString("\n")
    s"""${title}
       |======
       |
       |# HEAD
       |
       |${headproperties}
       |
       |# Definition
       |
       |${definition}
       |""".stripMargin
  }

  private def _site_ttl(config: CreateConfig): String =
    s"""@prefix schema: <https://schema.org/> .
       |@prefix kh: <${config.url}/rdf/ontology/knowledgehub#> .
       |
       |<${config.url}/>
       |  a schema:WebSite ;
       |  schema:name "${config.name}" ;
       |  schema:inLanguage "${config.language}" .
       |""".stripMargin

  private def _site_jsonld(config: CreateConfig): String =
    s"""{
       |  "@context": "https://schema.org",
       |  "@type": "WebSite",
       |  "name": "${config.name}",
       |  "url": "${config.url}",
       |  "inLanguage": "${config.language}"
       |}
       |""".stripMargin

  private def _schema_ttl(config: CreateConfig): String =
    s"""@prefix schema: <https://schema.org/> .
      |@prefix kh: <${config.url}/rdf/schema/knowledgehub#> .
      |
      |kh:KnowledgeItem a schema:DefinedTerm .
      |""".stripMargin

  private def _schema_jsonld(): String =
    """{
      |  "@context": "https://schema.org",
      |  "@type": "DefinedTermSet",
      |  "name": "KnowledgeHub Schema"
      |}
      |""".stripMargin

  private def _ontology_ttl(config: CreateConfig): String =
    s"""@prefix owl: <http://www.w3.org/2002/07/owl#> .
      |@prefix kh: <${config.url}/rdf/ontology/knowledgehub#> .
      |
      |kh:KnowledgeHubOntology a owl:Ontology .
      |""".stripMargin

  private def _ontology_jsonld(): String =
    """{
      |  "@context": {
      |    "owl": "http://www.w3.org/2002/07/owl#"
      |  },
      |  "@type": "owl:Ontology",
      |  "name": "KnowledgeHub Ontology"
      |}
      |""".stripMargin

  private def _css(): String =
    """body {
      |  line-height: 1.7;
      |}
      |
      |main {
      |  max-width: 78rem;
      |}
      |""".stripMargin

  private def _default_website_staging(config: CreateConfig): String = {
    val name = Option(config.save.getFileName).map(_.toString).filter(_.nonEmpty).getOrElse("bok")
    s"../${name}-website"
  }

  private def _website_stage_script(config: CreateConfig): String = {
    val staging = _default_website_staging(config)
    s"""#!/bin/sh
       |set -eu
       |
       |# Prototype staging workflow.
       |# Copy this file to etc/website-stage.sh, review the paths, and set it in
       |# bok.workflow.stage.command when the project needs a persistent published
       |# website working tree. Direct upload from website.d is usually simpler.
       |
       |PROJECT_DIR=$$(cd "$$(dirname "$$0")/.." && pwd)
       |cd "$$PROJECT_DIR"
       |
       |WEBSITE_BUILD_DIR=$${WEBSITE_BUILD_DIR:-website.d}
       |WEBSITE_STAGING_DIR=$${WEBSITE_STAGING_DIR:-$staging}
       |
       |if [ ! -d "$$WEBSITE_BUILD_DIR" ]; then
       |  echo "Website build directory is missing: $$WEBSITE_BUILD_DIR" >&2
       |  echo "Run: cozy bok build" >&2
       |  exit 2
       |fi
       |
       |mkdir -p "$$WEBSITE_STAGING_DIR"
       |rsync -av --checksum --delete "$$WEBSITE_BUILD_DIR"/ "$$WEBSITE_STAGING_DIR"/
       |
       |if [ -d "$$WEBSITE_STAGING_DIR/.git" ]; then
       |  git -C "$$WEBSITE_STAGING_DIR" status --short
       |fi
       |""".stripMargin
  }

  private def _website_upload_script(config: CreateConfig): String =
    """#!/bin/sh
      |set -eu
      |
      |# Prototype AWS S3 upload workflow.
      |# Copy this file to etc/website-upload.sh and set it in
      |# bok.workflow.upload.command. Cozy reads conf/cozy/config.* and .cozy/config.*
      |# and passes bok.workflow.upload.env.* values to this script as environment
      |# variables. Put sensitive values in .cozy/config.*.
      |
      |PROJECT_DIR=$(cd "$(dirname "$0")/.." && pwd)
      |cd "$PROJECT_DIR"
      |
      |WEBSITE_SOURCE_DIR=${WEBSITE_SOURCE_DIR:-website.d}
      |AWS_S3_URI=${AWS_S3_URI:-}
      |AWS_S3_SYNC_DELETE=${AWS_S3_SYNC_DELETE:-true}
      |AWS_CLOUDFRONT_DISTRIBUTION_ID=${AWS_CLOUDFRONT_DISTRIBUTION_ID:-}
      |
      |if [ ! -d "$WEBSITE_SOURCE_DIR" ]; then
      |  echo "Website source directory is missing: $WEBSITE_SOURCE_DIR" >&2
      |  echo "Run: cozy bok build" >&2
      |  echo "Or set WEBSITE_SOURCE_DIR via bok.workflow.upload.env.WEBSITE_SOURCE_DIR." >&2
      |  exit 2
      |fi
      |
      |if [ -z "$AWS_S3_URI" ]; then
      |  cat >&2 <<'MSG'
      |AWS_S3_URI is not configured.
      |
      |Configure the upload target through Cozy workflow environment settings.
      |Use conf/cozy/config.yaml for public defaults and .cozy/config.yaml for
      |sensitive local overrides. Example:
      |
      |bok:
      |  workflow:
      |    upload:
      |      env:
      |        WEBSITE_SOURCE_DIR: website.d
      |        AWS_S3_URI: s3://example-bucket/path/
      |        AWS_S3_SYNC_DELETE: "true"
      |        AWS_CLOUDFRONT_DISTRIBUTION_ID: EXAMPLE123
      |
      |Cozy intentionally does not embed hosting credentials or provider policy.
      |MSG
      |  exit 2
      |fi
      |
      |if ! command -v aws >/dev/null 2>&1; then
      |  echo "aws CLI is required for this upload workflow." >&2
      |  exit 2
      |fi
      |
      |if [ "$AWS_S3_SYNC_DELETE" = "true" ]; then
      |  aws s3 sync "$WEBSITE_SOURCE_DIR"/ "$AWS_S3_URI" --delete
      |else
      |  aws s3 sync "$WEBSITE_SOURCE_DIR"/ "$AWS_S3_URI"
      |fi
      |
      |if [ -n "$AWS_CLOUDFRONT_DISTRIBUTION_ID" ]; then
      |  aws cloudfront create-invalidation --distribution-id "$AWS_CLOUDFRONT_DISTRIBUTION_ID" --paths "/*"
      |fi
      |""".stripMargin


  sealed trait ProjectFilePolicy
  object ProjectFilePolicy {
    case object Default extends ProjectFilePolicy
    case object Skip extends ProjectFilePolicy
    case object Overwrite extends ProjectFilePolicy

    def create(args: ParsedArgs): ProjectFilePolicy =
      if (args.request.switches.exists(x => x.name == "no-project-files" || x.name == "no-scaffold-files"))
        Skip
      else if (args.request.switches.exists(x => x.name == "overwrite-project-files" || x.name == "force-project-files"))
        Overwrite
      else
        Default
  }

  object CreateConfig {
    def create(args: List[String]): CreateConfig = {
      val parsed = BokArgs.create(args)
      val save = parsed.requiredPath("save", "<dir>")
      parsed.validateNoUnrecognized()
      CreateConfig(
        save,
        parsed.property("name").getOrElse("KnowledgeHub BoK"),
        parsed.property("url").getOrElse("https://www.asamioffice.com/kokubunji/knowledgehub"),
        parsed.property("language").getOrElse("ja"),
        ProjectFilePolicy.create(parsed)
      )
    }
  }

  object CategoryConfig {
    def create(args: List[String]): CategoryConfig = {
      val parsed = BokArgs.category(args)
      val name = parsed.argument("name").getOrElse(
        RAISE.invalidArgumentFault("Missing category name for bok create-category")
      )
      parsed.validateNoUnrecognized()
      CategoryConfig(
        _category_project(parsed),
        name,
        parsed.property("title").getOrElse(_titleize(name)),
        parsed.property("description").getOrElse(s"${_titleize(name)} category."),
        BokPurpose(parsed.property("vision"), Vector.empty, parsed.properties("goal"), parsed.properties("subgoal")),
        parsed.properties("article").map(_parse_category_article),
        parsed.properties("term").map(_parse_category_term),
        ProjectFilePolicy.create(parsed)
      )
    }
  }

  object DoctorConfig {
    def create(args: List[String], fix: Boolean): DoctorConfig = {
      var input: Option[Path] = None
      var fixswitch = false
      var dryrun = false
      args.foreach {
        case "--fix" => fixswitch = true
        case "--dry-run" => dryrun = true
        case x if x.startsWith("--") => RAISE.invalidArgumentFault(s"Unknown option: ${x}")
        case x =>
          if (input.isDefined)
            RAISE.invalidArgumentFault(s"Unknown argument: ${x}")
          input = Some(_to_path(x))
      }
      DoctorConfig(
        input.getOrElse(_logical_cwd),
        fix || fixswitch,
        dryrun
      )
    }
  }

  object PreviewConfig {
    def create(args: List[String]): PreviewConfig = {
      var input: Option[Path] = None
      var port: Option[Int] = None

      def take(xs: List[String]): Unit =
        xs match {
          case Nil =>
          case "--port" :: value :: rest =>
            port = Some(_parse_port(value))
            take(rest)
          case "--port" :: Nil =>
            RAISE.invalidArgumentFault("Missing --port <port>")
          case x :: _ if x.startsWith("--port=") =>
            RAISE.invalidArgumentFault("Use --port <port>, not --port=<port>")
          case x :: _ if x.startsWith("--") =>
            RAISE.invalidArgumentFault(s"Unknown option: ${x}")
          case x :: rest =>
            if (input.isDefined)
              RAISE.invalidArgumentFault(s"Unknown argument: ${x}")
            input = Some(_to_path(x))
            take(rest)
        }

      take(args)
      PreviewConfig(input.getOrElse(_logical_cwd), port)
    }

    private def _parse_port(value: String): Int =
      try {
        value.toInt
      } catch {
        case _: NumberFormatException => RAISE.invalidArgumentFault(s"Invalid --port <number>: ${value}")
      }
  }

  private def _parse_category_article(value: String): CategoryArticle = {
    val xs = value.split(":", 3).toVector
    xs match {
      case Vector(slug, title, purpose) => CategoryArticle(slug, title, purpose)
      case Vector(slug, title) => CategoryArticle(slug, title, s"${title} article.")
      case Vector(slug) => CategoryArticle(slug, _titleize(slug), s"${_titleize(slug)} article.")
      case _ => RAISE.invalidArgumentFault(s"Invalid bok category article: ${value}")
    }
  }

  private def _parse_category_term(value: String): CategoryTerm = {
    val xs = value.split(":", 4).toVector
    xs match {
      case Vector(path, title, definition, reading) => CategoryTerm(path, title, definition, Some(reading).filter(_.nonEmpty))
      case Vector(path, title, definition) => CategoryTerm(path, title, definition)
      case Vector(path, title) => CategoryTerm(path, title, s"${title} definition.")
      case Vector(path) => CategoryTerm(path, _titleize(path), s"${_titleize(path)} definition.")
      case _ => RAISE.invalidArgumentFault(s"Invalid bok category term: ${value}")
    }
  }

  private def _titleize(value: String): String =
    value.split("[/_-]+").toVector.filter(_.nonEmpty).map { part =>
      part.head.toUpper + part.tail
    }.mkString(" ")

  object BuildConfig {
    def create(args: List[String]): BuildConfig = {
      val parsed = BokArgs.build(args)
      val project = _project(parsed)
      parsed.validateNoUnrecognized()
      val config = _load_config(project)
      val source = config.value("bok.source").getOrElse("src/main/doxsite")
      val site = _load_site_config(project.resolve(source))
      val languages = _languages(config, site)
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
        source,
        config.value("bok.website").getOrElse("website.d"),
        config.value("bok.antora").getOrElse("antora.d"),
        config.value("bok.doxsite").getOrElse("doxsite.d"),
        config.value("bok.arcadia-site").getOrElse("arcadiasite.d"),
        config.value("bok.ui-bundle").getOrElse("src/main/antora-ui/build/ui-bundle.zip"),
        _strategy(parsed),
        dockerimage,
        site.value("output.scope.policy").orElse(config.value("bok.output.scope.policy")).getOrElse("home_only"),
        site.value("site.metadata.name").getOrElse("KnowledgeHub BoK"),
        _locale_mode(config, site),
        _default_locale(config, site, languages),
        languages,
        ArcadiaConfig(_boolean(config, "bok.arcadia.enabled", false), config.value("bok.arcadia.source").getOrElse("src/main/arcadiasite")),
        _direct_assets(project, config),
        _publication_settings(parsed, config, _strategy(parsed)),
        _dashboard_color_group(parsed, config, site)
      )
    }
  }

  object PublicationConfig {
    def create(name: String, args: List[String]): PublicationConfig = {
      val parsed = BokArgs.publication(name, args)
      val project = _project(parsed)
      parsed.validateNoUnrecognized()
      val config = _load_config(project)
      val strategy = _strategy(parsed, config.value("bok.strategy").getOrElse("production"))
      val publication = parsed.pathProperty("publication").
        map(_.toString).
        orElse(config.value("bok.publication")).
        getOrElse("src/main/publication")
      val warehouse = parsed.pathProperty("warehouse").
        map(_.toString).
        orElse(config.value("bok.warehouse")).
        getOrElse("warehouse")
      val force = parsed.request.switches.exists(_.name == "force") || _boolean(config, "bok.video.force", false)
      val videoenabled = _boolean(config, "bok.video.enabled", true)
      val dryrun = parsed.request.switches.exists(_.name == "dry-run")
      if (dryrun && name != "publish")
        RAISE.invalidArgumentFault(s"--dry-run is only supported by bok publish: ${name}")
      PublicationConfig(
        project,
        config.value("bok.source").getOrElse("src/main/doxsite"),
        publication,
        warehouse,
        parsed.property("version"),
        force,
        videoenabled,
        dryrun,
        strategy
      )
    }
  }

  private def _workflow_command(config: CozyProjectYamlConfig.Config, name: String): Vector[String] =
    config.value(s"bok.workflow.${name}.command").map(x => Vector("sh", "-c", x)).getOrElse(Vector.empty)

  private def _workflow_env(config: CozyProjectYamlConfig.Config, name: String): Map[String, String] =
    config.mapUnder(s"bok.workflow.${name}.env")

  object WorkflowConfig {
    def create(name: String, args: List[String]): WorkflowConfig = {
      val parsed = BokArgs.workflow(name, args)
      val project = _project(parsed)
      parsed.validateNoUnrecognized()
      val config = _load_config(project)
      WorkflowConfig(
        project,
        name,
        _workflow_command(config, name),
        _workflow_env(config, name)
      )
    }
  }
}
