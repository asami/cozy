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

private[cozy] trait CozyBokDashboardCore {
  self: CozyBokImplementation.type =>
  private[bok] def _dashboard_theme_class(config: BuildConfig): String =
    s"bok-dashboard-theme-${config.dashboardColorGroup}"

  private[bok] def _category_dashboard_theme_class(config: BuildConfig): String =
    s"bok-dashboard-theme-${_category_dashboard_color_group(config.dashboardColorGroup)}"

  private[bok] def _support_dashboard_theme_class: String =
    "bok-dashboard-theme-paper"

  private def _category_dashboard_color_group(group: String): String =
    group match {
      case "aurora" => "lagoon"
      case "lagoon" => "meadow"
      case "meadow" => "lagoon"
      case "ocean" => "sand"
      case "ember" => "slate"
      case "slate" => "aurora"
      case _ => "lagoon"
    }

  private val _dashboard_color_groups = Set("aurora", "lagoon", "meadow", "ocean", "ember", "slate", "sand")

  private[bok] def _dashboard_color_group(parsed: ParsedArgs, config: CozyProjectYamlConfig.Config, site: SiteConfig): String = {
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

  private[bok] def _relative_href(page: Path, destination: Path): String =
    page.toAbsolutePath.normalize.getParent.relativize(destination.toAbsolutePath.normalize).toString.replace(java.io.File.separatorChar, '/')

  private[bok] def _site_css_links(config: BuildConfig, page: Path): String =
    s"""  <link rel="stylesheet" href="${_html_escape(_site_asset_href(config, page, "_/css/bootstrap-grid.min.css"))}">
       |  <link rel="stylesheet" href="${_html_escape(_site_asset_href(config, page, "_/css/site.css"))}">
       |  <link rel="stylesheet" href="${_html_escape(_site_asset_href(config, page, "_/css/cozy-bok-dashboard.css"))}">""".stripMargin

  private[bok] def _site_root_prefix(config: BuildConfig, page: Path): String = {
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

  private[bok] def _rendered_body_fragment(html: String): String =
    _regex_first(html, """(?s)<body>\s*<article[^>]*>(.*?)</article>\s*</body>""").
      orElse(_regex_first(html, """(?s)<body[^>]*>(.*?)</body>""")).
      getOrElse(html)

  private def _regex_first(value: String, regex: String): Option[String] =
    regex.r.findFirstMatchIn(value).map(_.group(1))

  private[bok] def _category_nav_menu(locale: String, categories: Vector[CategorySummary], prefix: String): String =
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

  private[bok] def _bok_nav_menu(config: BuildConfig, locale: String, prefix: String): String =
    s"""<div class="navbar-item has-dropdown is-hoverable navbar-bok-nav navbar-bok-dropdown" aria-label="${_html_escape(_ui(locale, "nav.bok"))}">
       |  <a class="navbar-link navbar-bok-toggle" href="#">${_html_escape(_ui(locale, "nav.bok"))}</a>
       |  <div class="navbar-dropdown navbar-bok-menu">
       |    <a class="navbar-item navbar-dropdown-item" href="${_html_escape(prefix)}glossary/index.html">${_html_escape(_ui(locale, "glossary.title"))}</a>
       |    <a class="navbar-item navbar-dropdown-item" href="${_html_escape(prefix)}articles/index.html">${_html_escape(_ui(locale, "dashboard.kpi.articles"))}</a>
       |    <a class="navbar-item navbar-dropdown-item" href="${_html_escape(prefix)}scenarios/index.html">${_html_escape(_ui(locale, "scenario.title"))}</a>
       |    <a class="navbar-item navbar-dropdown-item" href="${_html_escape(prefix)}projects/index.html">${_html_escape(_ui(locale, "project.title"))}</a>
       |    <a class="navbar-item navbar-dropdown-item" href="${_html_escape(prefix)}repository/index.html">${_html_escape(_component_repository_title(locale))}</a>
       |    <a class="navbar-item navbar-dropdown-item" href="${_html_escape(prefix)}bibliography/index.html">${_html_escape(_ui(locale, "bibliography.title"))}</a>
       |    <a class="navbar-item navbar-dropdown-item" href="${_html_escape(prefix)}tags/index.html">${_html_escape(_ui(locale, "tag.title"))}</a>
       |    <a class="navbar-item navbar-dropdown-item" href="${_html_escape(_history_href(config, prefix))}">${_html_escape(_ui(locale, "history.title"))}</a>
       |    <a class="navbar-item navbar-dropdown-item" href="${_html_escape(prefix)}manual/index.html">${_html_escape(_ui(locale, "manual.title"))}</a>
       |  </div>
       |</div>""".stripMargin

  private def _home_nav_container(config: BuildConfig): String = {
    val items = _regular_category_summaries(config.sourcepath).map { category =>
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

  private[bok] def _home_dashboard(config: BuildConfig, locale: String): String = {
    val purpose = _bok_purpose(config)
    val dashboard = _dashboard(config)
    val sourcedocument = _source_document(config.sourcepath, "index")
    val fragment = _source_document_fragment(config, sourcedocument, locale)
    val articlecount = _source_article_count(config)
    if (dashboard.isEmpty && purpose.isEmpty)
      ""
    else {
      val hero = _dashboard_hero(
        fragment.flatMap(_.effectiveHeadline).orElse(sourcedocument.map(_dox_title)).getOrElse(_uif(locale, "home.page.title", config.siteTitle)),
        fragment.flatMap(_.effectiveBrief).orElse(sourcedocument.map(_dox_brief)).getOrElse(_ui(locale, "home.intro")),
        Vector(
          _ui(locale, "dashboard.kpi.categories") -> dashboard.map(_.counts.categorycount.toString).getOrElse("-"),
          _ui(locale, "dashboard.kpi.articles") -> articlecount.toString,
          _ui(locale, "dashboard.kpi.terms") -> dashboard.map(_.counts.glossarytermcount.toString).getOrElse("-"),
          _ui(locale, "dashboard.kpi.rdf.triples") -> dashboard.map(_.rdf.triplecount.toString).getOrElse("-")
        )
      )
      s"""<section class="bok-dashboard-shell" id="dashboard">
         |  ${hero}
         |  ${_home_dashboard_grid(config, purpose, dashboard, locale)}
         |</section>""".stripMargin
    }
  }

  private[bok] def _category_dashboard(config: BuildConfig, category: CategoryContent, locale: String): String = {
    val site = _dashboard(config)
    val dashboard = site.flatMap(_.categories.find(_.name == category.slug))
    val sourcedocument = _source_document(config.sourcepath.resolve(category.slug), "index")
    val fragment = _source_document_fragment(config, sourcedocument, locale)
    val categoryterms = _category_term_page_items(config, category.slug)
    val categoryarticlecount = category.articles.size
    if (!category.purpose.isEmpty || dashboard.isDefined) {
      val categoryrdf = dashboard.flatMap(_.rdf)
      val hero = _dashboard_hero(
        fragment.flatMap(_.effectiveHeadline).orElse(sourcedocument.map(_dox_title)).getOrElse(_uif(locale, "category.page.title", category.title)),
        fragment.flatMap(_.effectiveBrief).orElse(sourcedocument.map(_dox_brief)).getOrElse(_uif(locale, "category.intro", category.description)),
        Vector(
          _ui(locale, "dashboard.kpi.articles") -> categoryarticlecount.toString,
          _ui(locale, "dashboard.kpi.terms") -> dashboard.map(_.counts.glossarytermcount.toString).getOrElse(categoryterms.size.toString),
          _ui(locale, "dashboard.kpi.rdf") -> categoryrdf.map(_.triplecount.toString).getOrElse("-")
        )
      )
      s"""<section class="bok-dashboard-shell" id="dashboard">
         |  ${hero}
         |  ${_category_dashboard_grid(config, category, categoryterms, dashboard, categoryrdf, locale)}
         |</section>""".stripMargin
    }
    else
      s"<p>${_html_escape(_ui(locale, "dashboard.unavailable"))}</p>"
  }

  private[bok] def _dashboard_hero(title: String, body: String, facts: Vector[(String, String)]): String = {
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
    _site_purpose(_load_site_config(config.sourcepath))

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

  private[bok] def _first_non_empty[T](xs: Vector[T]*): Vector[T] =
    xs.find(_.nonEmpty).getOrElse(Vector.empty)

  private[bok] def _purpose_from_parts(
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
    val scenarios = _scenario_index(config).map(_.scenarios).getOrElse(Vector.empty)
    val bibliographies = _bibliography_index(config).map(_.entries).getOrElse(Vector.empty)
    val termcount = dashboard.map(_.counts.glossarytermcount).getOrElse(_terms(config).size)
    val articlecount = _source_article_count(config)
    val rdfcount = dashboard.map(_.rdf.triplecount).getOrElse(0)
    val projectcount = _project_package_dirs(config.sourcepath).size
    val tagcount = _tag_index(config, locale).tags.size
    val cards = Vector[Option[String]](
      dashboard.map(x => _recent_activity_card(config, locale, x, _home_recent_items(config))),
      if (purpose.isEmpty) None else Some(_dashboard_card("col-12 col-xl-8", "bok-card-purpose", _ui(locale, "dashboard.card.vision"), _purpose_card_body(locale, purpose), Vector("reader", "contributor", "project_manager"))),
      Some(_analysis_entry_card(locale, termcount, articlecount, scenarios.size, projectcount, rdfcount, "articles/index.html", "glossary/index.html", "scenarios/index.html", "projects/index.html", "rdf/index.html")),
      dashboard.map(x => _dashboard_card(if (purpose.isEmpty) "col-12 col-xl-7" else "col-12 col-xl-4", "bok-card-matrix", _ui(locale, "dashboard.card.category.matrix"), _category_matrix_body(config, locale, x), Vector("reader", "contributor", "project_manager"), Some("categories"))),
      dashboard.map(x => _kpi_card_link("col-6 col-md-3", _ui(locale, "dashboard.kpi.categories"), x.counts.categorycount.toString, _ui(locale, "dashboard.kpi.categories.note"), "category/index.html")),
      dashboard.map(_ => _kpi_card_link("col-6 col-md-3", _ui(locale, "dashboard.kpi.articles"), articlecount.toString, _ui(locale, "dashboard.kpi.articles.note"), "articles/index.html")),
      dashboard.map(x => _kpi_card_link("col-6 col-md-3", _ui(locale, "dashboard.kpi.terms"), x.counts.glossarytermcount.toString, _ui(locale, "dashboard.kpi.terms.note"), "glossary/index.html")),
      dashboard.map(x => _kpi_card_link("col-6 col-md-3", _ui(locale, "dashboard.kpi.rdf.triples"), x.rdf.triplecount.toString, _ui(locale, "dashboard.kpi.rdf.triples.note"), "rdf/index.html")),
      Some(_kpi_card_link("col-6 col-md-3", _ui(locale, "dashboard.kpi.scenarios"), scenarios.size.toString, _ui(locale, "dashboard.kpi.scenarios.note"), "scenarios/index.html")),
      Some(_kpi_card_link("col-6 col-md-3", _ui(locale, "project.title"), projectcount.toString, _ui(locale, "project.kpi.note"), "projects/index.html")),
      Some(_kpi_card_link("col-6 col-md-3", _ui(locale, "tag.title"), tagcount.toString, _ui(locale, "tag.kpi.note"), "tags/index.html")),
      Some(_kpi_card_link("col-6 col-md-3", _ui(locale, "dashboard.kpi.bibliography"), bibliographies.size.toString, _ui(locale, "dashboard.kpi.bibliography.note"), "bibliography/index.html", Vector("contributor", "project_manager"))),
      Some(_dashboard_card("col-12 col-xl-4", "bok-card-quality", _ui(locale, "dashboard.card.quality.alerts"), _quality_alerts_body(locale, dashboard.isDefined), Vector("contributor", "project_manager"))),
      dashboard.map { x =>
        val increments = _article_adjusted_increments(x.increments, x.counts.articlecount - articlecount)
        _dashboard_card("col-12 col-xl-8", "bok-card-chart", _ui(locale, "dashboard.card.growth"), _dashboard_increment_chart(locale, increments, _ui(locale, "dashboard.chart.bok.additions")), Vector("project_manager", "contributor"))
      },
      Some(_dashboard_card("col-12 col-md-6 col-xl-6", "bok-card-readiness", _ui(locale, "dashboard.card.readiness"), _home_readiness_body(locale, config, dashboard), Vector("site_administrator", "project_manager"))),
      Some(_dashboard_card("col-12 col-md-6 col-xl-6", "bok-card-actions", _ui(locale, "dashboard.card.next.actions"), _next_actions_body(locale, config), Vector("site_administrator", "project_manager")))
    ).flatten
    _dashboard_container(locale, cards)
  }

  private def _category_dashboard_grid(
    config: BuildConfig,
    category: CategoryContent,
    categoryterms: Vector[CategoryPageItem],
    dashboard: Option[DashboardCategory],
    rdf: Option[DashboardRdfSummary],
    locale: String
  ): String = {
    val categoryscenarios = _scenario_index(config).map(_.scenarios.filter(_.category.contains(category.slug))).getOrElse(Vector.empty)
    val categoryprojects = _project_package_dirs(config.sourcepath).count { path =>
      val projects = config.sourcepath.resolve("projects").toAbsolutePath.normalize()
      val relative = projects.relativize(path.toAbsolutePath.normalize())
      relative.getNameCount >= 1 && relative.getName(0).toString == category.slug
    }
    val categoryarticlecount = category.articles.size
    val categoryrdfcount = rdf.map(_.triplecount).getOrElse(0)
    val categorytagcount = _tag_index(config, locale).forCategory(category.slug).tags.size
    val cards = Vector[Option[String]](
      if (category.purpose.isEmpty) None else Some(_dashboard_card("col-12 col-xl-8", "bok-card-purpose bok-card-category-purpose", _ui(locale, "dashboard.card.category.vision"), _purpose_card_body(locale, category.purpose), Vector("reader", "contributor", "project_manager"))),
      Some(_analysis_entry_card(locale, categoryterms.size, categoryarticlecount, categoryscenarios.size, categoryprojects, categoryrdfcount, s"../articles/index.html?category=${_url_query_escape(category.slug)}", s"../glossary/${category.slug}/index.html", s"../scenarios/index.html?category=${_url_query_escape(category.slug)}", s"../projects/index.html?category=${_url_query_escape(category.slug)}", s"../rdf/index.html?category=${_url_query_escape(category.slug)}")),
      Some(_dashboard_card("col-12 col-xl-6", "bok-card-map", _ui(locale, "dashboard.card.term.map"), _page_map_body(categoryterms, _ui(locale, "dashboard.term.empty")), Vector("reader", "contributor", "project_manager"))),
      Some(_dashboard_card("col-12 col-xl-6", "bok-card-map", _ui(locale, "dashboard.card.article.map"), _page_map_body(category.articles, _ui(locale, "dashboard.article.empty")), Vector("reader", "contributor", "project_manager"))),
      rdf.map(x => _category_rdf_kpi_card(locale, category, x)),
      dashboard.map(_ => _kpi_card(locale, "col-6 col-md-3", _ui(locale, "dashboard.kpi.articles"), categoryarticlecount.toString, _ui(locale, "dashboard.kpi.category.articles.note"))),
      dashboard.map(x => _kpi_card(locale, "col-6 col-md-3", _ui(locale, "dashboard.kpi.terms"), x.counts.glossarytermcount.toString, _ui(locale, "dashboard.kpi.category.terms.note"))),
      Some(_kpi_card_link("col-6 col-md-3", _ui(locale, "dashboard.kpi.scenarios"), categoryscenarios.size.toString, _ui(locale, "dashboard.kpi.scenarios.note"), s"../scenarios/index.html?category=${_url_query_escape(category.slug)}")),
      Some(_kpi_card_link("col-6 col-md-3", _ui(locale, "project.title"), categoryprojects.toString, _ui(locale, "project.kpi.note"), s"../projects/index.html?category=${_url_query_escape(category.slug)}")),
      Some(_kpi_card_link("col-6 col-md-3", _ui(locale, "tag.title"), categorytagcount.toString, _ui(locale, "tag.kpi.note"), s"../tags/index.html?category=${_url_query_escape(category.slug)}")),
      Some(_kpi_card(locale, "col-6 col-md-3", _ui(locale, "dashboard.kpi.issues"), "0", _ui(locale, "dashboard.kpi.issues.note"))),
      Some(_dashboard_card("col-12 col-xl-5", "bok-card-quality", _ui(locale, "dashboard.card.local.quality.alerts"), _quality_alerts_body(locale, dashboard.isDefined), Vector("contributor", "project_manager"))),
      dashboard.map { x =>
        val increments = _article_adjusted_increments(x.increments, x.counts.articlecount - categoryarticlecount)
        _dashboard_card("col-12 col-xl-7", "bok-card-chart", _ui(locale, "dashboard.card.category.growth"), _dashboard_increment_chart(locale, increments, _uif(locale, "dashboard.chart.category.additions", x.title)), Vector("project_manager", "contributor"))
      },
      Some(_dashboard_card("col-12 col-md-6 col-xl-3", "bok-card-readiness", _ui(locale, "dashboard.card.category.readiness"), _category_readiness_body(locale, dashboard), Vector("site_administrator", "project_manager"))),
      _recent_activity_card(config, locale, category),
      Some(_dashboard_card("col-12 col-md-6 col-xl-5", "bok-card-related", _ui(locale, "dashboard.card.related.knowledge"), _related_knowledge_body(locale, config, category, rdf), Vector("reader", "contributor", "project_manager")))
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
    val actoroptions = Vector(
      "all" -> _ui(locale, "dashboard.actor.all"),
      "reader" -> _ui(locale, "dashboard.actor.reader"),
      "contributor" -> _ui(locale, "dashboard.actor.contributor"),
      "project_manager" -> _ui(locale, "dashboard.actor.project.manager"),
      "site_administrator" -> _ui(locale, "dashboard.actor.site.administrator")
    ).map {
      case (key, label) =>
        val selected = if (key == defaultactor) " selected" else ""
        s"""<option value="${_html_escape(key)}"${selected}>${_html_escape(label)}</option>"""
    }.mkString("\n")
    val modeoptions = Vector(
      "hide" -> _ui(locale, "dashboard.actor.mode.hide"),
      "dim" -> _ui(locale, "dashboard.actor.mode.dim")
    ).map {
      case (key, label) =>
        val selected = if (key == "hide") " selected" else ""
        s"""<option value="${_html_escape(key)}"${selected}>${_html_escape(label)}</option>"""
    }.mkString("\n")
    s"""<div class="bok-dashboard-actor-filter" role="group" aria-label="${_html_escape(_ui(locale, "dashboard.actor.filter"))}">
       |  <label class="bok-dashboard-actor-select-label"><span>${_html_escape(_ui(locale, "dashboard.actor.filter"))}</span><select class="bok-dashboard-actor-select" data-bok-actor-filter aria-label="${_html_escape(_ui(locale, "dashboard.actor.filter"))}">
       |${actoroptions}
       |  </select></label>
       |  <label class="bok-dashboard-actor-select-label bok-dashboard-actor-mode-select-label"><span>${_html_escape(_ui(locale, "dashboard.actor.mode"))}</span><select class="bok-dashboard-actor-select" data-bok-actor-mode aria-label="${_html_escape(_ui(locale, "dashboard.actor.mode"))}">
       |${modeoptions}
       |  </select></label>
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

}
