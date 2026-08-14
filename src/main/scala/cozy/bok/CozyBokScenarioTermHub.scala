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

private[cozy] trait CozyBokScenarioTermHub {
  self: CozyBokImplementation.type =>
  private[bok] def _scenario_index(config: BuildConfig): Option[ScenarioIndex] = {
    val path = config.doxsitePath.resolve("metadata/scenarios/scenarios.json")
    if (!Files.isRegularFile(path))
      None
    else
      parser.parse(Files.readString(path, StandardCharsets.UTF_8)).toOption.flatMap(_.as[ScenarioIndex].toOption)
  }

  private[bok] def _scenario_dedicated_page(
    config: BuildConfig,
    categories: Vector[CategoryContent],
    locale: String,
    page: Path,
    scenarios: Vector[ScenarioEntry]
  ): String =
    s"""<!doctype html>
       |<html lang="${_html_escape(locale)}">
       |<head>
       |  <meta charset="utf-8">
       |  <meta name="viewport" content="width=device-width, initial-scale=1">
       |  <title>${_html_escape(_ui(locale, "scenario.title"))} - ${_html_escape(config.siteTitle)}</title>
       |${_site_css_links(config, page)}
       |</head>
       |<body class="article ${_html_escape(_support_dashboard_theme_class)}">
       |${_category_header(config, categories, locale)}
       |<div class="body body-dashboard bok-scenario-body">
       |  <main class="article bok-scenario-main">
       |    <div class="content">
       |      <article class="doc bok-scenario-doc">
       |        <section class="bok-dashboard-shell bok-scenario-dashboard" id="dashboard">
       |          ${_dashboard_hero(
                    _ui(locale, "scenario.title"),
                    _ui(locale, "scenario.description"),
                    Vector(
                      _ui(locale, "scenario.metric.total") -> scenarios.size.toString,
                      _ui(locale, "scenario.metric.types") -> scenarios.map(_.scenariotype).distinct.size.toString,
                      _ui(locale, "scenario.metric.categories") -> scenarios.flatMap(_.category).distinct.size.toString
                    )
                  )}
       |          ${_scenario_dashboard_body(config, page, locale, scenarios)}
       |        </section>
       |      </article>
       |    </div>
       |  </main>
       |</div>
       |</body>
       |</html>
       |""".stripMargin

  private def _scenario_dashboard_body(config: BuildConfig, page: Path, locale: String, scenarios: Vector[ScenarioEntry]): String =
    if (scenarios.isEmpty)
      s"""<div class="bok-dashboard container-fluid bok-dashboard-command-center">
         |  <div class="row g-3">
         |    ${_dashboard_card("col-12 col-xl-4", "bok-card-kpi bok-card-term-extraction-progress", _ui(locale, "term.extraction.progress.title"), _term_extraction_progress_card(locale, 0, 0), Vector("contributor", "project_manager"))}
         |    ${_dashboard_card("col-12", "bok-card-map bok-card-scenario-map", _ui(locale, "scenario.title"), s"""<p class="bok-card-muted">${_html_escape(_ui(locale, "scenario.empty"))}</p>""", Vector("reader", "contributor", "project_manager"))}
         |  </div>
         |</div>""".stripMargin
    else {
      val progress = _term_extraction_progress_card(
        locale,
        scenarios.count(x => x.termextraction.isPlanned && _is_term_extraction_done(x.termextraction)),
        scenarios.count(_.termextraction.isPlanned)
      )
      val bytype = scenarios.groupBy(_.scenariotype).toVector.sortBy(_._1)
      val metrics = bytype.map {
        case (kind, xs) =>
          s"""<div class="bok-metric-card"><div class="bok-metric-label">${_html_escape(kind)}</div><div class="bok-metric-value">${xs.size}</div><div class="bok-metric-note">${_html_escape(_ui(locale, "scenario.metric.note"))}</div></div>"""
      }.mkString("""<div class="bok-dashboard-grid bok-scenario-metrics">""", "", "</div>")
      val items = scenarios.take(30).map { scenario =>
        val summary = scenario.summary.map(x => s"""<p>${_html_escape(x)}</p>""").getOrElse("")
        val terms = if (scenario.terms.isEmpty) "" else scenario.terms.take(5).map(x => s"""<span class="badge bok-badge-info">${_html_escape(x)}</span>""").mkString(" ")
        val tagchips = _tag_chips(config, page, scenario.tags, scenario.category, locale)
        val href = s"../${scenario.hrefFromHome}"
        s"""<article class="bok-scenario-tile" data-scenario-category="${_html_escape(scenario.categorySlug)}">
           |  <div class="bok-scenario-tile-head">
           |    <span>${_html_escape(scenario.scenariotype)}</span>
           |    <code>${_html_escape(scenario.id)}</code>
           |  </div>
           |  <h3><a href="${_html_escape(href)}">${_html_escape(scenario.title)}</a></h3>
           |  ${summary}
           |  <div class="bok-scenario-terms">${terms}</div>
           |  ${tagchips}
           |</article>""".stripMargin
      }.mkString("""<div class="bok-scenario-grid">""", "", "</div>")
      s"""<div class="bok-dashboard container-fluid bok-dashboard-command-center">
         |  <div class="row g-3">
         |    ${_dashboard_card("col-12 col-xl-4", "bok-card-kpi bok-card-term-extraction-progress", _ui(locale, "term.extraction.progress.title"), progress, Vector("contributor", "project_manager"))}
         |    ${_dashboard_card("col-12 col-xl-4", "bok-card-kpi bok-card-scenario-summary", _ui(locale, "scenario.metric.summary"), metrics, Vector("reader", "contributor", "project_manager"))}
         |    ${_dashboard_card("col-12 col-xl-8", "bok-card-map bok-card-scenario-map", _ui(locale, "scenario.title"), items, Vector("reader", "contributor", "project_manager"))}
         |  </div>
         |</div>
         |<script>
         |(() => {
         |  const category = new URLSearchParams(window.location.search).get('category');
         |  if (!category) return;
         |  document.querySelectorAll('[data-scenario-category]').forEach((item) => {
         |    item.hidden = item.dataset.scenarioCategory !== category;
         |  });
         |})();
         |</script>""".stripMargin
    }

  private[bok] def _term_group_cards(locale: String, terms: Vector[TermEntry], categories: Vector[CategoryContent]): String = {
    val titles = categories.map(x => x.slug -> x.title).toMap
    val cards = terms.groupBy(_.categorySlug).toVector.sortBy(_._1).map {
      case (category, xs) =>
        val title = titles.getOrElse(category, category)
        val links = xs.sortBy(_.title).take(8).map { term =>
          val typelabel = if (term.termtype == "concept") "" else s""" <span class="badge bok-badge-info">${_html_escape(_term_type_label(term.termtype, locale))}</span>"""
          s"""<li><a href="${_html_escape(term.glossaryHref)}">${_html_escape(term.title)}</a>${_reading_label(term)}${typelabel} <a class="bok-term-rdf-mini" href="${_html_escape(term.rdfHrefFromGlossary)}">RDF</a></li>"""
        }.mkString("<ul>", "", "</ul>")
        val more = if (xs.size > 8) s"""<div class="bok-more">${_html_escape(_uif(locale, "dashboard.more", xs.size - 8))}</div>""" else ""
        s"""<div class="bok-term-group-card"><h3><a href="../${_html_escape(category)}/index.html">${_html_escape(title)}</a></h3>${links}${more}</div>"""
    }.mkString("\n")
    s"""<div class="bok-term-group-grid">${cards}</div>"""
  }

  private[bok] def _write_term_hub_pages(
    config: BuildConfig,
    target: Path,
    locale: String,
    categories: Vector[CategoryContent],
    terms: Vector[TermEntry]
  ): Unit =
    terms.foreach { term =>
      val page = target.resolve(term.publicpath)
      if (!(term.sourcepath.startsWith("glossary/") && Files.isRegularFile(page))) {
        val scenarios = _scenario_index(config).map(_.scenarios.filter(_.isRelatedTo(term))).getOrElse(Vector.empty)
        val bibliographies = _bibliography_index(config).map(_.entries.filter(_bibliography_related_to_term(_, term))).getOrElse(Vector.empty)
        val repositorycars = _repository_car_index(config).entries.filter(_repository_car_related_to_term(_, term))
        _write_text(page, _term_hub_page(config, categories, locale, page, term, scenarios, bibliographies, repositorycars))
      }
    }

  private def _term_hub_page(
    config: BuildConfig,
    categories: Vector[CategoryContent],
    locale: String,
    page: Path,
    term: TermEntry,
    scenarios: Vector[ScenarioEntry],
    bibliographies: Vector[BibliographyEntry],
    repositorycars: Vector[RepositoryCarEntry]
  ): String =
    s"""<!doctype html>
       |<html lang="${_html_escape(locale)}">
       |<head>
       |  <meta charset="utf-8">
       |  <meta name="viewport" content="width=device-width, initial-scale=1">
       |  <title>${_html_escape(term.title)} - ${_html_escape(config.siteTitle)}</title>
       |${_site_css_links(config, page)}
       |</head>
       |<body class="article ${_html_escape(_support_dashboard_theme_class)}">
       |${_category_header(config, categories, locale, "../../")}
       |<div class="body body-dashboard bok-term-hub-body">
       |  <main class="article">
       |    <div class="content">
       |      <article class="doc">
       |        ${_term_hub(config, page, term, locale, scenarios, bibliographies, repositorycars)}
       |      </article>
       |    </div>
       |  </main>
       |</div>
       |</body>
       |</html>
       |""".stripMargin

  private def _term_hub(
    config: BuildConfig,
    page: Path,
    term: TermEntry,
    locale: String,
    scenarios: Vector[ScenarioEntry],
    bibliographies: Vector[BibliographyEntry],
    repositorycars: Vector[RepositoryCarEntry]
  ): String =
    s"""<section class="bok-dashboard-shell bok-term-hub" id="term-hub">
       |  <header class="bok-dashboard-hero">
       |    <div class="bok-dashboard-hero-copy">
       |      <p class="bok-dashboard-eyebrow">${_html_escape(_ui(locale, "term.hub.eyebrow"))}</p>
       |      <h1 class="page">${_html_escape(term.title)}</h1>
       |      <p class="bok-dashboard-lead">${_html_escape(term.summary.getOrElse(_ui(locale, "term.hub.description")))}</p>
       |      ${_tag_chips(config, page, term.tags, term.category, locale)}
       |    </div>
       |    <div class="bok-dashboard-hero-facts">
       |      <span class="bok-dashboard-hero-fact"><strong>${_html_escape(term.categorySlug)}</strong><em>${_html_escape(_ui(locale, "dashboard.matrix.category"))}</em></span>
       |      <span class="bok-dashboard-hero-fact"><strong>${_html_escape(_term_type_label(term.termtype, locale))}</strong><em>${_html_escape(_ui(locale, "term.type"))}</em></span>
       |      <span class="bok-dashboard-hero-fact"><strong>${_html_escape(_mono_koto_label(term.monoKotoKind, locale))}</strong><em>${_html_escape(_ui(locale, "term.analysis.kind"))}</em></span>
       |      <span class="bok-dashboard-hero-fact"><strong>${term.rdfrefs.size}</strong><em>RDF</em></span>
       |      <span class="bok-dashboard-hero-fact"><strong>${term.termrefs.size}</strong><em>${_html_escape(_ui(locale, "term.related.terms"))}</em></span>
       |    </div>
       |  </header>
       |  <div class="bok-dashboard container-fluid bok-dashboard-command-center">
       |    <div class="row g-3">
       |      ${_dashboard_card("col-12 col-xl-7", "bok-card-purpose bok-card-term-definition", _ui(locale, "term.definition"), _term_definition_body(term))}
       |      ${_dashboard_card("col-12 col-xl-5", "bok-card-analysis bok-card-mono-koto", _ui(locale, "term.analysis"), _term_analysis_body(term, scenarios, locale))}
       |      ${_term_type_cards(term, locale)}
       |      ${_dashboard_card("col-12 col-xl-5", "bok-card-readiness", _ui(locale, "term.quality"), _term_quality_body(term, locale))}
       |      ${_dashboard_card("col-12 col-xl-6", "bok-card-related", _ui(locale, "term.rdf.resources"), _term_rdf_refs_body(term, locale))}
       |      ${_dashboard_card("col-12 col-xl-3", "bok-card-map", _ui(locale, "term.related.articles"), _term_refs_body(term.articlerefs, locale))}
       |      ${_dashboard_card("col-12 col-xl-3", "bok-card-map", _ui(locale, "term.related.terms"), _term_refs_body(term.termrefs, locale))}
       |      ${_dashboard_card("col-12 col-xl-3", "bok-card-map", _ui(locale, "term.related.videos"), _term_refs_body(term.videorefs, locale))}
       |      ${_dashboard_card("col-12 col-xl-3", "bok-card-map", _ui(locale, "term.related.scenarios"), _term_scenarios_body(scenarios, locale))}
       |      ${_dashboard_card("col-12 col-xl-3", "bok-card-map", _ui(locale, "term.related.bibliography"), _term_bibliography_body(bibliographies, locale))}
       |      ${_dashboard_card("col-12 col-xl-3", "bok-card-map bok-card-repository-car", _repository_car_title(locale), _term_repository_cars_body(config, page, repositorycars, locale))}
       |      ${_term_sie_information_card(config, page, term, locale)}
       |      ${_dashboard_card("col-12 col-xl-3", "bok-card-actions", _ui(locale, "dashboard.card.next.actions"), _term_actions_body(term, locale))}
       |    </div>
       |  </div>
       |</section>""".stripMargin


  private def _term_type_cards(term: TermEntry, locale: String): String = term.termtype match {
    case "event" =>
      term.event.map(x => _dashboard_card("col-12 col-xl-5", "bok-card-related bok-card-term-type", _ui(locale, "term.type.event"), _term_event_body(x, locale))).getOrElse("")
    case "actor" =>
      term.actor.map(x => _dashboard_card("col-12 col-xl-5", "bok-card-related bok-card-term-type", _ui(locale, "term.type.actor"), _term_actor_body(x, locale))).getOrElse("")
    case "role" =>
      term.role.map(x => _dashboard_card("col-12 col-xl-5", "bok-card-related bok-card-term-type", _ui(locale, "term.type.role"), _term_role_body(x, locale))).getOrElse("")
    case _ => ""
  }

  private[bok] def _term_type_label(value: String, locale: String): String = value match {
    case "unclassified" => _ui(locale, "term.type.unclassified")
    case "event" => _ui(locale, "term.type.event")
    case "actor" => _ui(locale, "term.type.actor")
    case "role" => _ui(locale, "term.type.role")
    case "entity" => _ui(locale, "term.type.entity")
    case "resource" => _ui(locale, "term.type.resource")
    case "artifact" => _ui(locale, "term.type.artifact")
    case "action" => _ui(locale, "term.type.action")
    case "process" => _ui(locale, "term.type.process")
    case "task" => _ui(locale, "term.type.task")
    case "scenario" => _ui(locale, "term.type.scenario")
    case "state" => _ui(locale, "term.type.state")
    case "rule" => _ui(locale, "term.type.rule")
    case _ => _ui(locale, "term.type.concept")
  }

  private[bok] def _mono_koto_label(value: String, locale: String): String = value match {
    case "koto" => _ui(locale, "term.analysis.koto")
    case "rule" => _ui(locale, "term.analysis.rule")
    case _ => _ui(locale, "term.analysis.mono")
  }

  private def _term_analysis_body(term: TermEntry, scenarios: Vector[ScenarioEntry], locale: String): String = {
    val cmlvalues = term.cmlLinks.map(x => s"${x.kind}: ${x.value}")
    val diagnostics = _term_analysis_diagnostics(term, scenarios, locale)
    val rows = Vector(
      _ui(locale, "term.analysis.kind") -> Vector(_mono_koto_label(term.monoKotoKind, locale)),
      _ui(locale, "term.type") -> Vector(_term_type_label(term.termtype, locale)),
      _ui(locale, "term.analysis.cml.linkage") -> cmlvalues,
      _ui(locale, "term.related.scenarios") -> scenarios.map(_.title),
      _ui(locale, "term.analysis.diagnostics") -> diagnostics
    )
    _term_metadata_table(rows, locale)
  }

  private def _term_analysis_diagnostics(term: TermEntry, scenarios: Vector[ScenarioEntry], locale: String): Vector[String] = {
    val cml = if (term.cmlLinks.isEmpty) Vector(_ui(locale, "term.analysis.diagnostic.cml.missing")) else Vector.empty
    val scenario =
      if (term.termtype == "event" && scenarios.isEmpty && term.event.forall(_.scenarios.isEmpty))
        Vector(_ui(locale, "term.analysis.diagnostic.scenario.missing"))
      else
        Vector.empty
    val mismatch =
      if (term.cmlLinks.nonEmpty && term.cmlLinks.forall(x => _cml_analysis_kind(x.kind) != term.monoKotoKind))
        Vector(_ui(locale, "term.analysis.diagnostic.classification.mismatch"))
      else
        Vector.empty
    val result = cml ++ scenario ++ mismatch
    if (result.isEmpty) Vector(_ui(locale, "term.analysis.diagnostic.ok")) else result
  }

  private def _cml_analysis_kind(kind: String): String = kind match {
    case "event" | "operation" | "statemachine" => "koto"
    case "rule" => "rule"
    case _ => "mono"
  }

  private def _term_event_body(event: TermEvent, locale: String): String = {
    val rows = Vector(
      _ui(locale, "term.event.occurred.at") -> event.occurredat.toVector,
      _ui(locale, "term.event.period") -> Vector(event.startat.toVector.mkString, event.endat.toVector.mkString).filter(_.nonEmpty),
      _ui(locale, "term.event.location") -> event.location.toVector,
      _ui(locale, "term.event.actors") -> event.actors,
      _ui(locale, "term.event.roles") -> event.roles,
      _ui(locale, "term.event.participants") -> event.participants,
      _ui(locale, "term.event.scenarios") -> event.scenarios,
      _ui(locale, "term.event.evidence") -> event.evidence,
      _ui(locale, "term.event.cml") -> Vector(event.cmlcomponent, event.cmlevent, event.cmlstatemachine).flatten
    )
    _term_metadata_table(rows, locale)
  }

  private def _term_actor_body(actor: TermActor, locale: String): String =
    _term_metadata_table(Vector(
      _ui(locale, "term.actor.organization") -> actor.organization.toVector,
      _ui(locale, "term.actor.roles") -> actor.roles,
      _ui(locale, "term.actor.description") -> actor.description.toVector
    ), locale)

  private def _term_role_body(role: TermRole, locale: String): String =
    _term_metadata_table(Vector(
      _ui(locale, "term.role.actors") -> role.actors,
      _ui(locale, "term.role.responsibilities") -> role.responsibilities,
      _ui(locale, "term.role.permissions") -> role.permissions
    ), locale)

  private def _term_metadata_table(rows: Vector[(String, Vector[String])], locale: String): String = {
    val body = rows.collect { case (label, values) if values.nonEmpty =>
      s"""<tr><th>${_html_escape(label)}</th><td>${values.map(_html_escape).mkString("<br>")}</td></tr>"""
    }.mkString("\n")
    if (body.isEmpty)
      s"""<p class="bok-card-muted">${_html_escape(_ui(locale, "term.type.metadata.empty"))}</p>"""
    else
      s"""<table class="table table-sm bok-metadata-table"><tbody>${body}</tbody></table>"""
  }

  private def _term_definition_body(term: TermEntry): String = {
    val reading = term.reading.filterNot(_ == term.title).map(x => s"""<p class="bok-term-reading-large">${_html_escape(x)}</p>""").getOrElse("")
    s"""${reading}<div class="bok-term-definition-html">${term.definitionhtml}</div>"""
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
    if (term.rdfrefs.isEmpty)
      s"""<p class="bok-card-muted">${_html_escape(_ui(locale, "term.rdf.empty"))}</p>"""
    else
      term.rdfrefs.take(8).map { ref =>
        val predicate = ref.predicate.map(x => s" <small>${_html_escape(_short_uri_label(x))}</small>").getOrElse("")
        s"""<li class="list-group-item"><span>${_html_escape(ref.label)}</span>${predicate}<em>${_html_escape(ref.direction)}</em></li>"""
      }.mkString("""<ul class="list-group bok-map-list">""", "", "</ul>")

  private def _term_refs_body(refs: Vector[TermReference], locale: String): String =
    if (refs.isEmpty)
      s"""<p class="bok-card-muted">${_html_escape(_ui(locale, "term.refs.empty"))}</p>"""
    else
      refs.take(6).map(x => s"""<li class="list-group-item"><a href="${_html_escape(x.path)}">${_html_escape(x.title)}</a><span>${_html_escape(x.relation)}</span></li>""").mkString("""<ul class="list-group bok-map-list">""", "", "</ul>")

  private def _term_scenarios_body(scenarios: Vector[ScenarioEntry], locale: String): String =
    if (scenarios.isEmpty)
      s"""<p class="bok-card-muted">${_html_escape(_ui(locale, "scenario.empty"))}</p>"""
    else
      scenarios.take(6).map { scenario =>
        s"""<li class="list-group-item"><a href="../../${_html_escape(scenario.publicpath)}">${_html_escape(scenario.title)}</a><span>${_html_escape(scenario.scenariotype)}</span></li>"""
      }.mkString("""<ul class="list-group bok-map-list">""", "", "</ul>")

  private def _term_bibliography_body(entries: Vector[BibliographyEntry], locale: String): String =
    if (entries.isEmpty)
      s"""<p class="bok-card-muted">${_html_escape(_ui(locale, "bibliography.empty"))}</p>"""
    else
      entries.take(6).map { entry =>
        val resolution = if (entry.needsresolution) s" / ${_ui(locale, "bibliography.unresolved")}" else ""
        s"""<li class="list-group-item"><a href="../../${_html_escape(entry.publicpath)}">${_html_escape(entry.title)}</a><span>${_html_escape(entry.entrytype + " / " + entry.sourcekind + resolution)}</span></li>"""
      }.mkString("""<ul class="list-group bok-map-list">""", "", "</ul>")

  private def _term_repository_cars_body(
    config: BuildConfig,
    page: Path,
    entries: Vector[RepositoryCarEntry],
    locale: String
  ): String =
    if (entries.isEmpty)
      s"""<p class="bok-card-muted">${_html_escape(_repository_car_empty(locale))}</p>"""
    else
      entries.take(6).map { entry =>
        val href = _relative_href(page, config.websitePath.resolve(entry.publicPath))
        s"""<li class="list-group-item"><a href="${_html_escape(href)}">${_html_escape(entry.title)}</a><span>${_html_escape(entry.effectiveVersion.getOrElse("-"))}</span></li>"""
      }.mkString("""<ul class="list-group bok-map-list">""", "", "</ul>")

  private def _repository_car_related_to_term(entry: RepositoryCarEntry, term: TermEntry): Boolean =
    entry.terms.exists(_term_reference_matches(_, term))

  private[bok] def _term_reference_matches(value: String, term: TermEntry): Boolean =
    value == term.id || value == term.title || value == term.slug || term.aliases.contains(value)

  private def _bibliography_related_to_term(entry: BibliographyEntry, term: TermEntry): Boolean =
    entry.terms.exists(_term_reference_matches(_, term))

  private def _term_actions_body(term: TermEntry, locale: String): String =
    Vector(
      _ui(locale, "term.action.open.rdf") -> term.rdfHrefFromTerm,
      _ui(locale, "glossary.title") -> "../../glossary/index.html"
    ).map { case (label, href) => s"""<li><a href="${_html_escape(href)}">${_html_escape(label)}</a></li>""" }.mkString("""<ol class="bok-action-list">""", "", "</ol>")

  private[bok] def _bibliography_search_json(results: Vector[BibliographySearchResult]): String =
    results.map { result =>
      val authors = result.authors.map(_json_string).mkString("[", ", ", "]")
      val fields = Vector(
        "provider" -> Some(result.provider),
        "bib_id" -> Some(result.bibid),
        "citation_key" -> Some(result.citationkey),
        "title" -> Some(result.title),
        "year" -> result.year,
        "doi" -> result.doi,
        "isbn" -> result.isbn,
        "source_url" -> result.sourceurl,
        "entry_type" -> Some(result.entrytype)
      ).collect { case (key, Some(value)) => s"${_json_string(key)}: ${_json_string(value)}" }
      (fields :+ s"${_json_string("authors")}: ${authors}").mkString("{", ", ", "}")
    }.mkString("{\n  \"candidates\": [\n    ", ",\n    ", "\n  ]\n}")

  private def _json_string(value: String): String =
    "\"" + value.flatMap {
      case '\\' => "\\\\"
      case '"' => "\\\""
      case '\n' => "\\n"
      case '\r' => "\\r"
      case '\t' => "\\t"
      case c if c < ' ' => ""
      case c => c.toString
    } + "\""

  private[bok] def _safe_file_name(value: String): String =
    value.replaceAll("[^A-Za-z0-9_-]+", "-").stripPrefix("-").stripSuffix("-") match {
      case "" => "reference"
      case x => x
    }

  private def _short_uri_label(value: String): String = {
    val a = value.split('#').lastOption.getOrElse(value)
    a.split('/').filter(_.nonEmpty).lastOption.getOrElse(a)
  }

}
