package cozy.bok

import org.goldenport.RAISE
import org.goldenport.cli.{Request => CliRequest}
import org.goldenport.cli.spec
import cozy.config.CozyProjectYamlConfig
import java.time.LocalDate
import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path, Paths, StandardCopyOption}
import java.util.zip.{ZipEntry, ZipOutputStream}
import scala.collection.JavaConverters._
import scala.util.matching.Regex
import scala.sys.process._
import io.circe.{Decoder, HCursor}
import io.circe.parser

/*
 * @since   Jun.  3, 2026
 * @version Jun. 19, 2026
 * @author  ASAMI, Tomoharu
 */
private[cozy] object CozyBok {
  private val _default_docker_image = "ghcr.io/asami/cozy-toolchain:latest"

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
    articles: Vector[CategoryArticle],
    terms: Vector[CategoryTerm],
    policy: ProjectFilePolicy
  )
  final case class CategoryArticle(slug: String, title: String, purpose: String) {
    def fileName: String = s"${slug}.dox"
    def htmlName: String = s"${slug}.html"
  }
  final case class CategoryTerm(path: String, title: String, definition: String, reading: Option[String] = None) {
    def termPath: String = path.stripPrefix("glossary/").stripPrefix("/")
    def fileName: String = s"${termPath}.dox"
    def htmlName(category: String): String = s"../glossary/${category}/${termPath}.html"
  }
  private final case class CategoryContent(
    slug: String,
    title: String,
    description: String,
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
    increments: DashboardIncrements
  )
  private final case class BokDashboard(
    counts: DashboardCounts,
    rdf: DashboardRdfSummary,
    increments: DashboardIncrements,
    categories: Vector[DashboardCategory]
  )

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
    } yield DashboardCategory(name, title, counts, increments)

  private implicit val _bok_dashboard_decoder: Decoder[BokDashboard] = (c: HCursor) =>
    for {
      counts <- c.downField("counts").as[DashboardCounts]
      rdf <- c.downField("rdf").as[DashboardRdfSummary]
      increments <- c.downField("increments").as[DashboardIncrements]
      categories <- c.downField("categories").as[Vector[DashboardCategory]]
    } yield BokDashboard(counts, rdf, increments, categories)

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
    languages: Vector[String],
    arcadia: ArcadiaConfig,
    directAssets: DirectAssetsConfig
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
  final case class WorkflowConfig(project: Path, name: String, command: Vector[String])
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
      _p_project,
      spec.Parameter.property("strategy"),
      spec.Parameter.property("docker-image")
    )

    private val _preview_request = spec.Request(
      _p_project,
      spec.Parameter.propertyInt("port")
    )

    private val _workflow_request = spec.Request(_p_project)

    def create(args: List[String]): ParsedArgs = _parse("bok-create", _create_request, args)
    def category(args: List[String]): ParsedArgs = _parse("bok-create-category", _category_request, args)
    def build(args: List[String]): ParsedArgs = _parse("bok-build", _build_request, args)
    def preview(args: List[String]): ParsedArgs = _parse("bok-preview", _preview_request, args)
    def workflow(name: String, args: List[String]): ParsedArgs = _parse(s"bok-${name}", _workflow_request, args)

    private def _parse(name: String, request: spec.Request, args: List[String]): ParsedArgs =
      ParsedArgs(request.build(CliRequest(name), args))
  }
  final case class SiteConfig(values: Map[String, String], lists: Map[String, Vector[String]]) {
    def value(path: String): Option[String] = values.get(path).map(_.trim).filter(_.nonEmpty)
    def boolean(path: String): Option[Boolean] =
      value(path).map(_.toLowerCase(java.util.Locale.ROOT)).collect {
        case "true" | "yes" | "on" => true
        case "false" | "no" | "off" => false
      }
    def list(path: String): Vector[String] = lists.getOrElse(path, Vector.empty)
  }
  object SiteConfig {
    val empty: SiteConfig = SiteConfig(Map.empty, Map.empty)
  }

  trait Runner {
    def run(command: Vector[String], cwd: Path): Unit
  }

  object ProcessRunner extends Runner {
    def run(command: Vector[String], cwd: Path): Unit = {
      val exit = Process(command, cwd.toFile).!
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
      case "bok" :: "preview" :: rest =>
        preview(rest, ProcessRunner)
        true
      case "bok" :: "commit" :: rest =>
        runWorkflow(WorkflowConfig.create("commit", rest), ProcessRunner)
        true
      case "bok" :: "upload" :: rest =>
        runWorkflow(WorkflowConfig.create("upload", rest), ProcessRunner)
        true
      case "bok" :: other :: _ =>
        RAISE.invalidArgumentFault(s"Unsupported bok command: ${other}")
      case _ =>
        false
    }

  def create(config: CreateConfig): Unit = {
    val sitedir = config.save.resolve("src/main/doxsite")
    _write(config.save.resolve("conf/cozy/config.yaml"), _cozy_config(), config.policy)
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
    _write_default_ui_bundle(config.save.resolve("src/main/antora-ui/build/ui-bundle.zip"), config.policy)
  }

  def createCategory(config: CategoryConfig): Unit = {
    val dir = config.project.resolve("src/main/doxsite").resolve(config.name)
    _write(dir.resolve("category.yaml"), _category(_category_name(config.name), config.title, config.description), config.policy)
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
    runner.run(Vector("dox", "antora", "-strategy", config.strategy, config.source), config.project)
    _run_antora(config, runner)
    runner.run(Vector("dox", "site", "-strategy", config.strategy, "-output.scope.policy", config.siteOutputScopePolicy, config.source), config.project)
    _normalize_doxsite_output(config)
    _delete_directory(config.project.resolve(s"doxsite-cache-${config.strategy}.d"))
    if (config.arcadia.enabled) {
      runner.run(Vector("arcadia", "site", config.arcadia.source, config.arcadiaSite), config.project)
      _copy_directory(config.arcadiaSitePath, config.websitePath)
    }
    _write_home_page(config)
    _write_special_pages(config)
    _write_category_pages(config)
    if (config.strategy == "production") {
      runner.run(Vector("dox", "site-mark", "-strategy", "production", "-output.scope.policy", "all", config.source), config.project)
      if (config.directAssets.enabled)
        config.directAssets.items.foreach { item =>
          _copy_directory(config.project.resolve(item.source), config.project.resolve(item.destination))
        }
    }
  }

  def preview(args: List[String], runner: Runner): Unit = {
    val parsed = BokArgs.preview(args)
    val project = _project(parsed)
    val port = parsed.optionalInt("port").map(_.toString).getOrElse("8080")
    parsed.validateNoUnrecognized()
    val config = _load_config(project)
    val website = config.value("bok.website").getOrElse("website.d")
    runner.run(Vector("python3", "-m", "http.server", port), project.resolve(website))
  }

  def runWorkflow(config: WorkflowConfig, runner: Runner): Unit =
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
      runner.run(config.command, config.project)

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

  private def _docker_antora(config: BuildConfig, workdir: String, output: String): Vector[String] =
    Vector(
      "docker",
      "run",
      "--rm",
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
        Files.copy(config.uiBundlePath, target.resolve("ui-bundle.zip"), StandardCopyOption.REPLACE_EXISTING)
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

  private def _write_home_page(config: BuildConfig): Unit =
    _write_text(
      config.websitePath.resolve("index.html"),
      s"""<!doctype html>
         |<html lang="ja">
         |<head>
         |  <meta charset="utf-8">
         |  <meta name="viewport" content="width=device-width, initial-scale=1">
         |  <title>${_html_escape(config.siteTitle)}</title>
         |  <link rel="stylesheet" href="_/css/site.css">
         |</head>
         |<body class="article">
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
         |        <a class="navbar-item" href="index.html">Home</a>
         |        ${_home_nav_items(config)}
         |      </div>
         |    </div>
         |  </nav>
         |</header>
         |<div class="body">
         |  ${_home_nav_container(config)}
         |  <main class="article">
         |    <div class="toolbar" role="navigation">
         |      <button class="nav-toggle"></button>
         |      <a href="index.html" class="home-link is-current"></a>
         |      <nav class="breadcrumbs" aria-label="breadcrumbs">
         |        <ul>
         |          <li><a href="index.html">${_html_escape(config.siteTitle)}</a></li>
         |          <li>Home</li>
         |        </ul>
         |      </nav>
         |    </div>
         |    <div class="content">
         |      ${_home_toc_panel(config)}
         |      <article class="doc">
         |        <h1 class="page">${_html_escape(config.siteTitle)}</h1>
         |        <p>KnowledgeHub BoKのHome画面です。登録済みカテゴリへ移動できます。</p>
         |        ${_home_dashboard(config)}
         |        <div class="sect1" id="categories">
         |          <h2>カテゴリ</h2>
         |          <div class="sectionbody">
         |            ${_home_category_list(config)}
         |          </div>
         |        </div>
         |        <div class="sect1" id="operation-policy">
         |          <h2>運用方針</h2>
         |          <div class="sectionbody">
         |            <p>このBoKはSmartDox本文、Category、RDF素材、用語自動リンクを中心に運用します。</p>
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

  private def _write_category_pages(config: BuildConfig): Unit = {
    val categories = _category_contents(config.sourcePath)
    categories.foreach { category =>
      _write_text(
        config.websitePath.resolve(category.slug).resolve("index.html"),
        _category_html_page(config, category, categories)
      )
    }
  }

  private def _write_special_pages(config: BuildConfig): Unit = {
    val categories = _category_contents(config.sourcePath)
    val glossarybody = _glossary_dashboard_body(config, categories)
    _write_text(
      config.websitePath.resolve("glossary").resolve("index.html"),
      _special_html_page(
        config,
        categories,
        "Glossary",
        "BoK全体で共有する用語と語彙のDashboard。",
        glossarybody
      )
    )
    _write_text(
      config.websitePath.resolve("ja").resolve("glossary").resolve("index.html"),
      _localized_glossary_index_page(config, categories, "ja")
    )
    _write_text(
      config.websitePath.resolve("en").resolve("glossary").resolve("index.html"),
      _localized_glossary_index_page(config, categories, "en")
    )
  }

  private def _glossary_dashboard_body(config: BuildConfig, categories: Vector[CategoryContent]): String = {
    val terms = categories.flatMap { category =>
      category.terms.map(term => category -> term)
    }
    val categorycount = categories.size
    val categorieswithterms = categories.count(_.terms.nonEmpty)
    s"""<div class="sect1" id="term-groups">
       |  <h2>Term Groups</h2>
       |  <div class="sectionbody">
       |    <p>SmartDox連動用語は <code>glossary/&lt;category&gt;/</code> に配置します。</p>
       |    ${_glossary_metric_cards(categorycount, categorieswithterms, terms.size)}
       |  </div>
       |</div>
       |<div class="sect1" id="language-index">
       |  <h2>Language Index</h2>
       |  <div class="sectionbody">
       |    ${_glossary_language_links(config)}
       |  </div>
       |</div>
       |<div class="sect1" id="recent-terms">
       |  <h2>Recent Terms</h2>
       |  <div class="sectionbody">
       |    ${_glossary_recent_terms(terms)}
       |  </div>
       |</div>""".stripMargin
  }

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

  private def _glossary_language_links(config: BuildConfig): String = {
    val langs = (config.languages ++ Vector("ja", "en")).distinct.filter(x => x == "ja" || x == "en")
    langs.map {
      case "ja" => """<a class="bok-special-link" href="../ja/glossary/index.html">日本語索引ページ</a>"""
      case "en" => """<a class="bok-special-link" href="../en/glossary/index.html">英語索引ページ</a>"""
      case other => s"""<a class="bok-special-link" href="../${_html_escape(other)}/glossary/index.html">${_html_escape(other)} index page</a>"""
    }.mkString("""<div class="bok-special-links">""", "\n", "</div>")
  }

  private def _glossary_recent_terms(terms: Vector[(CategoryContent, CategoryPageItem)]): String =
    if (terms.isEmpty)
      "<p>No glossary terms yet.</p>"
    else
      terms.sortBy { case (_, term) => -term.modifiedAtMillis }.take(10).map {
        case (category, term) =>
          s"""<li><a href="${_html_escape(_glossary_index_href(term.href))}">${_html_escape(term.title)}</a>: ${_html_escape(category.title)}</li>"""
      }.mkString("<ol>\n", "\n", "\n</ol>")

  private def _glossary_index_href(href: String): String =
    href.stripPrefix("../glossary/").stripPrefix("glossary/")

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
       |  <link rel="stylesheet" href="../../_/css/site.css">
       |</head>
       |<body class="article">
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
    title: String,
    description: String,
    body: String
  ): String =
    s"""<!doctype html>
       |<html lang="ja">
       |<head>
       |  <meta charset="utf-8">
       |  <meta name="viewport" content="width=device-width, initial-scale=1">
       |  <title>${_html_escape(title)} - ${_html_escape(config.siteTitle)}</title>
       |  <link rel="stylesheet" href="../_/css/site.css">
       |</head>
       |<body class="article">
       |${_category_header(config, categories)}
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
       |      ${_glossary_toc_panel(config)}
       |      <article class="doc">
       |        <h1 class="page">${_html_escape(title)}</h1>
       |        <p>${_html_escape(description)}</p>
       |        <div class="sect1" id="dashboard">
       |          <h2>Dashboard</h2>
       |          <div class="sectionbody">
       |            <p>This is a special BoK Console page. It is linked from the right-side console, not from ordinary category navigation.</p>
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
    categories: Vector[CategoryContent]
  ): String =
    s"""<!doctype html>
       |<html lang="ja">
       |<head>
       |  <meta charset="utf-8">
       |  <meta name="viewport" content="width=device-width, initial-scale=1">
       |  <title>${_html_escape(category.title)} - ${_html_escape(config.siteTitle)}</title>
       |  <link rel="stylesheet" href="../_/css/site.css">
       |</head>
       |<body class="article">
       |${_category_header(config, categories)}
       |<div class="body">
       |  ${_category_nav_container(config, category, categories)}
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
       |      ${_category_toc_panel(config)}
       |      <article class="doc">
       |        <h1 class="page">${_html_escape(category.title)}</h1>
       |        <p>${_html_escape(category.description)}</p>
       |        <div class="sect1" id="dashboard">
       |          <h2>Dashboard</h2>
       |          <div class="sectionbody">
       |            ${_category_dashboard(config, category)}
       |          </div>
       |        </div>
       |        <div class="sect1" id="summary">
       |          <h2>Summary</h2>
       |          <div class="sectionbody">
       |            <ul>
       |              <li>Category: ${_html_escape(category.title)}</li>
       |              <li>Purpose: ${_html_escape(category.description)}</li>
       |              <li>Articles: ${category.articles.size}</li>
       |              <li>Terms: ${category.terms.size}</li>
       |            </ul>
       |          </div>
       |        </div>
       |        <div class="sect1" id="articles">
       |          <h2>Articles</h2>
       |          <div class="sectionbody">
       |            ${_category_item_list(category.articles)}
       |          </div>
       |        </div>
       |        <div class="sect1" id="terms">
       |          <h2>Terms</h2>
       |          <div class="sectionbody">
       |            ${_category_item_list(category.terms)}
       |          </div>
       |        </div>
       |        <div class="sect1" id="operation-notes">
       |          <h2>Operation Notes</h2>
       |          <div class="sectionbody">
       |            <p>このページはカテゴリの状態を集約するDashboardです。カテゴリ配下の記事、用語、運用上の注目点をここに集約します。</p>
       |          </div>
       |        </div>
       |      </article>
       |    </div>
       |  </main>
       |</div>
       |</body>
       |</html>
       |""".stripMargin

  private def _category_header(config: BuildConfig, categories: Vector[CategoryContent]): String = {
    val items = categories.map { category =>
      s"""<a class="navbar-item" href="../${_html_escape(category.slug)}/index.html">${_html_escape(category.title)}</a>"""
    }.mkString("\n        ")
    s"""<header class="header">
       |  <nav class="navbar">
       |    <div class="navbar-brand">
       |      <a class="navbar-item" href="../index.html">${_html_escape(config.siteTitle)}</a>
       |      <button class="navbar-burger" aria-controls="topbar-nav" aria-expanded="false" aria-label="Toggle main menu">
       |        <span></span>
       |        <span></span>
       |        <span></span>
       |      </button>
       |    </div>
       |    <div id="topbar-nav" class="navbar-menu">
       |      <div class="navbar-end">
       |        <a class="navbar-item" href="../index.html">Home</a>
       |        ${items}
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

  private def _category_toc_panel(config: BuildConfig): String =
    s"""<aside class="toc sidebar" data-title="Contents" data-levels="2">
      |    <div class="toc-menu">
      |      <h3>On this page</h3>
      |      <ul>
      |        <li><a href="#dashboard">Dashboard</a></li>
      |        <li><a href="#summary">Summary</a></li>
      |        <li><a href="#articles">Articles</a></li>
      |        <li><a href="#terms">Terms</a></li>
      |      </ul>
      |      <div class="bok-special-links">
      |        <h3>BoK Console</h3>
      |        <a class="bok-special-link" href="../glossary/index.html">Glossary</a>
      |        <a class="bok-special-link" href="${_html_escape(_history_href(config, "../"))}">History</a>
      |        <a class="bok-special-link" href="../manual/index.html">Manual</a>
      |      </div>
      |    </div>
      |  </aside>""".stripMargin

  private def _category_item_list(items: Vector[CategoryPageItem]): String =
    if (items.isEmpty)
      "<p>No entries yet.</p>"
    else
      items.map { item =>
        s"""<li><a href="${_html_escape(item.href)}">${_html_escape(item.title)}</a>: ${_html_escape(item.brief)}</li>"""
      }.mkString("<ul>\n", "\n", "\n</ul>")

  private def _home_nav_items(config: BuildConfig): String =
    _regular_category_summaries(config.sourcePath).map { category =>
      s"""<a class="navbar-item" href="${_html_escape(category.slug)}/index.html">${_html_escape(category.title)}</a>"""
    }.mkString("\n        ")

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

  private def _home_toc_panel(config: BuildConfig): String =
    s"""<aside class="toc sidebar" data-title="Contents" data-levels="2">
      |    <div class="toc-menu">
      |      <h3>On this page</h3>
      |      <ul>
      |        ${_home_dashboard_toc_item(config)}
      |        <li><a href="#categories">カテゴリ</a></li>
      |        <li><a href="#operation-policy">運用方針</a></li>
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
    _dashboard(config).map(_ => """<li><a href="#dashboard">Dashboard</a></li>""").getOrElse("")

  private def _home_dashboard(config: BuildConfig): String =
    _dashboard(config).map { dashboard =>
      s"""<div class="sect1" id="dashboard">
         |  <h2>Dashboard</h2>
         |  <div class="sectionbody">
         |    ${_dashboard_cards(dashboard.counts, includecategories = true)}
         |    ${_dashboard_rdf_cards(dashboard.rdf)}
         |    ${_dashboard_distribution_chart(dashboard.counts, "BoK item distribution")}
         |    ${_dashboard_increment_chart(dashboard.increments, "BoK additions")}
         |  </div>
         |</div>""".stripMargin
    }.getOrElse("")

  private def _category_dashboard(config: BuildConfig, category: CategoryContent): String =
    _dashboard(config).flatMap(_.categories.find(_.name == category.slug)).map { dashboard =>
      s"""${_dashboard_cards(dashboard.counts, includecategories = false)}
         |${_dashboard_distribution_chart(dashboard.counts, s"${dashboard.title} item distribution")}
         |${_dashboard_increment_chart(dashboard.increments, s"${dashboard.title} additions")}""".stripMargin
    }.getOrElse("<p>Dashboard metadata is not available.</p>")

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

  private def _dashboard_distribution_chart(counts: DashboardCounts, label: String): String = {
    val articlecount = counts.articleCount
    val termcount = counts.glossaryTermCount
    val total = math.max(1, articlecount + termcount)
    val articlewidth = _dashboard_bar_width(articlecount, total)
    val termwidth = _dashboard_bar_width(termcount, total)
    s"""<div class="bok-dashboard-chart" aria-label="${_html_escape(label)}" data-chart="distribution-ratio">
       |  <div class="bok-chart-row"><span>Articles</span><div><b style="width:${articlewidth}%"></b></div><em>${articlecount}</em></div>
       |  <div class="bok-chart-row"><span>Terms</span><div><b style="width:${termwidth}%"></b></div><em>${termcount}</em></div>
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

  private def _dashboard_increment_chart(increments: DashboardIncrements, label: String): String =
    if (increments.buckets.isEmpty)
      """<p>No dashboard increment metadata yet.</p>"""
    else if (!increments.buckets.forall(_.hasBreakdown))
      _dashboard_increment_total_chart(increments, label)
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
          val title = s"${bucket.label}: cumulative articles ${value} (+${bucket.articleCount})"
          f"""      <circle class="bok-cumulative-point-articles" cx="${cx}%.2f" cy="${cy}%.2f" r="2.8"><title>${_html_escape(title)}</title></circle>"""
      }.mkString("\n")
      val termmarkers = increments.buckets.zip(cumulativeterms).zipWithIndex.map {
        case ((bucket, value), index) =>
          val cx = x(index)
          val cy = y(value)
          val title = s"${bucket.label}: cumulative terms ${value} (+${bucket.glossaryTermCount})"
          f"""      <circle class="bok-cumulative-point-terms" cx="${cx}%.2f" cy="${cy}%.2f" r="2.8"><title>${_html_escape(title)}</title></circle>"""
      }.mkString("\n")
      val axis = points.map {
        case (bucket, (articlevalue, termvalue)) =>
          s"""    <span><time datetime="${_html_escape(bucket.startDate)}">${_html_escape(bucket.label)}</time><em>A:${articlevalue} T:${termvalue}</em></span>"""
      }.mkString("\n")
      val range = s"${increments.buckets.head.startDate} - ${increments.buckets.last.endDate}"
      s"""<div class="bok-dashboard-chart" aria-label="${_html_escape(label)}" data-chart="cumulative-date" data-scale="${_html_escape(increments.scale)}">
         |  <div class="bok-cumulative-chart">
         |    <div class="bok-cumulative-chart-head">
         |      <span class="bok-cumulative-chart-title">${_html_escape(label)}</span>
         |      <span class="bok-cumulative-chart-range">${_html_escape(range)}</span>
         |      <span class="bok-cumulative-chart-scale">${_html_escape(increments.scale)}</span>
         |    </div>
         |    <svg class="bok-cumulative-chart-svg" viewBox="0 0 100 100" role="img" aria-label="${_html_escape(label)} cumulative additions">
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
         |      <span><i class="bok-cumulative-marker bok-cumulative-marker-articles"></i>Articles</span>
         |      <span><i class="bok-cumulative-marker bok-cumulative-marker-terms"></i>Terms</span>
         |    </div>
         |    <div class="bok-cumulative-axis">
         |${axis}
         |    </div>
         |  </div>
         |</div>""".stripMargin
    }

  private def _dashboard_increment_total_chart(increments: DashboardIncrements, label: String): String = {
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
        val title = s"${bucket.label}: cumulative total ${value} (+${bucket.count})"
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
       |    <svg class="bok-cumulative-chart-svg" viewBox="0 0 100 100" role="img" aria-label="${_html_escape(label)} cumulative additions">
       |      <line class="bok-cumulative-axis-x" x1="6" y1="88" x2="94" y2="88"></line>
       |      <line class="bok-cumulative-axis-y" x1="6" y1="12" x2="6" y2="88"></line>
       |      <polyline class="bok-cumulative-line bok-cumulative-line-total" points="${coordinates}"></polyline>
       |      <g class="bok-cumulative-points">
       |${markers}
       |      </g>
       |    </svg>
       |    <div class="bok-cumulative-legend">
       |      <span><i class="bok-cumulative-marker bok-cumulative-marker-total"></i>Total</span>
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

  private def _home_category_list(config: BuildConfig): String = {
    val categories = _regular_category_summaries(config.sourcePath)
    if (categories.isEmpty)
      "<p>カテゴリはまだ登録されていません。`cozy bok create-category` で追加します。</p>"
    else
      categories.map { category =>
        val htmlclass = if (category.slug == "glossary") """ class="glossary"""" else ""
        s"""<li><a${htmlclass} href="${_html_escape(category.slug)}/index.html">${_html_escape(category.title)}</a>: ${_html_escape(category.description)}</li>"""
      }.mkString("<ul>\n", "\n", "\n</ul>")
  }

  private final case class CategorySummary(slug: String, title: String, description: String)

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
              _yaml_description(category).getOrElse("")
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

  private def _html_escape(value: String): String =
    value.flatMap {
      case '&' => "&amp;"
      case '<' => "&lt;"
      case '>' => "&gt;"
      case '"' => "&quot;"
      case '\'' => "&#39;"
      case c => c.toString
    }

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
      |  <link rel="stylesheet" href="{{uiRootPath}}/css/site.css">
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
    """body {
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
      |.bok-dashboard-chart[data-chart="cumulative-date"] {
      |  background: #fff;
      |  border-radius: 0.7rem;
      |  margin: 1rem 0 1.5rem;
      |  padding: 1rem 1.1rem;
      |  box-shadow: 0 2px 10px rgba(0, 0, 0, 0.08);
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
      |""".stripMargin

  private def _project(parsed: ParsedArgs): Path =
    parsed.pathProperty("project-dir").
      orElse(parsed.pathProperty("project")).
      orElse(parsed.argument("project").map(Paths.get(_).toAbsolutePath.normalize)).
      getOrElse(Paths.get(".").toAbsolutePath.normalize)

  private def _category_project(parsed: ParsedArgs): Path =
    parsed.pathProperty("project-dir").
      orElse(parsed.pathProperty("project")).
      getOrElse(Paths.get(".").toAbsolutePath.normalize)

  private def _load_config(project: Path): CozyProjectYamlConfig.Config =
    CozyProjectYamlConfig.loadOperationDefaults(project)

  private def _load_site_config(source: Path): SiteConfig = {
    val file = source.resolve("site.conf")
    if (!Files.isRegularFile(file))
      SiteConfig.empty
    else {
      val lines = Files.readAllLines(file, StandardCharsets.UTF_8).asScala.toVector
      SiteConfig(_parse_site_values(lines), _parse_site_lists(lines))
    }
  }

  private def _to_path(value: Any): Path = value match {
    case m: java.io.File => m.toPath.toAbsolutePath.normalize
    case m: Path => m.toAbsolutePath.normalize
    case m => Paths.get(m.toString).toAbsolutePath.normalize
  }

  private def _boolean(config: CozyProjectYamlConfig.Config, path: String, default: Boolean): Boolean =
    config.boolean(path).getOrElse(default)

  private def _strategy(parsed: ParsedArgs): String =
    parsed.property("strategy").getOrElse("wip") match {
      case "wip" => "work-in-progress"
      case "draft" => "draft"
      case "preview" => "production-preview"
      case "production" => "production"
      case other => other
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
    s"""cozy:
       |  docker-image: ${_default_docker_image}
       |
       |bok:
       |  source: src/main/doxsite
       |  website: website.d
       |  antora: antora.d
       |  doxsite: doxsite.d
       |  ui-bundle: src/main/antora-ui/build/ui-bundle.zip
       |  arcadia:
       |    enabled: false
       |    source: src/main/arcadiasite
       |  direct-assets:
       |    enabled: false
       |    items: []
       |  workflow:
       |    commit:
       |      command: ""
       |    upload:
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
       |${config.name} の全体状況、主要カテゴリ、運用入口を集約するDashboard。
       |
       |# Dashboard
       |
       |${config.name} is a BoK site for organizing KnowledgeHub concepts, book knowledge materialization, RDF vocabulary, and operation terms.
       |
       |## Quick Links
       |
       |- <a href="glossary/index.html">Glossary</a>: BoK全体で共有する用語と語彙。
       |- <a href="history/index.html">History</a>: BoK運用と更新履歴。
       |- <a href="manual/index.html">Manual</a>: BoK運用手順とページ種別の説明。
       |
       |## BoK Console
       |
       |- `glossary/index.dox`: BoK内で共有する用語集。
       |- `history/index.dox`: BoK運用の更新履歴。
       |- `manual/index.dox`: BoK運用マニュアル。
       |
       |## Category Overview
       |
       |カテゴリを追加すると、各カテゴリトップページはDashboardとして生成されます。
       |
       |## Operation Focus
       |
       |このBoKはSmartDox本文、Category、RDF素材、用語自動リンクを中心に運用します。
       |日本語単独運用のため、生成HTMLはサイトroot直下に配置します。
       |""".stripMargin

  private def _category(name: String, title: String, description: String): String =
    s"""name: ${name}
       |title: ${title}
       |description:
       |  en: ${description}
       |  ja: ${description}
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
       |# Dashboard
       |
       |${purpose}
       |
       |${_category_dashboard_cards(articles, terms)}
       |
       |## Summary
       |
       |- Category: ${title}
       |- Purpose: ${purpose}
       |- Articles: ${articles.size}
       |- Terms: ${terms.size}
       |
       |## Navigation
       |
       |- <a href="../index.html">BoK Home</a>
       |- <a href="../glossary/index.html">Glossary</a>
       |- <a href="../history/index.html">History</a>
       |${_article_links(articles)}${_term_links(category, terms)}
       |
       |## Operation Notes
       |
       |このページはカテゴリの状態を集約するDashboardです。カテゴリ配下の記事、用語、運用上の注目点をここに集約します。
       |""".stripMargin

  private def _category_dashboard_cards(
    articles: Vector[CategoryArticle],
    terms: Vector[CategoryTerm]
  ): String =
    _category_dashboard_cards(articles.size, terms.size)

  private def _category_dashboard_cards(
    articlecount: Int,
    termcount: Int
  ): String = {
    val total = articlecount + termcount
    val articlewidth = _dashboard_bar_width(articlecount, total)
    val termwidth = _dashboard_bar_width(termcount, total)
    s"""<div class="bok-dashboard-grid">
       |  <div class="bok-metric-card">
       |    <div class="bok-metric-label">Articles</div>
       |    <div class="bok-metric-value">${articlecount}</div>
       |    <div class="bok-metric-note">Category-owned articles</div>
       |  </div>
       |  <div class="bok-metric-card">
       |    <div class="bok-metric-label">Terms</div>
       |    <div class="bok-metric-value">${termcount}</div>
       |    <div class="bok-metric-note">Terms in this category</div>
       |  </div>
       |  <div class="bok-metric-card">
       |    <div class="bok-metric-label">Total Items</div>
       |    <div class="bok-metric-value">${total}</div>
       |    <div class="bok-metric-note">Articles + terms</div>
       |  </div>
       |</div>
       |
       |<div class="bok-dashboard-chart" aria-label="Category item distribution">
       |  <div class="bok-chart-row"><span>Articles</span><div><b style="width:${articlewidth}%"></b></div><em>${articlecount}</em></div>
       |  <div class="bok-chart-row"><span>Terms</span><div><b style="width:${termwidth}%"></b></div><em>${termcount}</em></div>
       |</div>""".stripMargin
  }

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
      |- `cozy bok preview <dir> --port 8080`: 生成済み `website.d` をローカル確認します。
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

  private def _article_links(articles: Vector[CategoryArticle]): String =
    if (articles.isEmpty)
      ""
    else
      articles.map {
        article => s"""- <a href="${_html_escape(article.htmlName)}">${_html_escape(article.title)}</a>"""
      }.mkString("\n# 記事\n\n", "\n", "\n")

  private def _term_links(category: String, terms: Vector[CategoryTerm]): String =
    if (terms.isEmpty)
      ""
    else
      terms.map { term =>
        s"""- <a href="${_html_escape(term.htmlName(category))}">${_html_escape(term.title)}</a>"""
      }.mkString("\n# 用語\n\n", "\n", "\n")

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
        parsed.properties("article").map(_parse_category_article),
        parsed.properties("term").map(_parse_category_term),
        ProjectFilePolicy.create(parsed)
      )
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
        _languages(config, site),
        ArcadiaConfig(_boolean(config, "bok.arcadia.enabled", false), config.value("bok.arcadia.source").getOrElse("src/main/arcadiasite")),
        _direct_assets(project, config)
      )
    }
  }

  object WorkflowConfig {
    def create(name: String, args: List[String]): WorkflowConfig = {
      val parsed = BokArgs.workflow(name, args)
      val project = _project(parsed)
      parsed.validateNoUnrecognized()
      val config = _load_config(project)
      WorkflowConfig(
        project,
        name,
        config.value(s"bok.workflow.${name}.command").map(x => Vector("sh", "-c", x)).getOrElse(Vector.empty)
      )
    }
  }
}
