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
 *  version Aug. 14, 2026
 * @version Sep.  2, 2026
 * @author  ASAMI, Tomoharu
 */

private[cozy] trait CozyBokGlossaryPages {
  self: CozyBokImplementation.type =>
  private[bok] def _write_scenario_metadata(config: BuildConfig): Unit =
    ScenarioMetadata.write(
      config.sourcepath,
      config.doxsitePath.resolve("metadata/scenarios/scenarios.json")
    )

  private[bok] def _glossary_dashboard_body(
    config: BuildConfig,
    categories: Vector[CategoryContent],
    terms: Vector[TermEntry],
    languagerootprefix: String,
    locale: String
  ): String = {
    val dashboard = _dashboard(config)
    val projects = _safe_resolved_project_packages(config)
    s"""<div class="bok-dashboard container-fluid bok-dashboard-command-center bok-term-dashboard">
       |  <div class="row g-3">
       |    ${_dashboard_card("col-12", "bok-card-kpi bok-card-glossary-summary", _ui(locale, "term.dashboard.summary.title"), _glossary_metric_cards(locale, categories.size, categories.count(_.terms.nonEmpty), terms), Vector("reader", "contributor", "project_manager"))}
       |    ${_dashboard_card("col-12", "bok-card-map bok-card-mono-koto-workflow", _ui(locale, "term.analysis.workflow.title"), _glossary_mono_koto_workflow(config, locale, terms), Vector("reader", "contributor", "project_manager"))}
       |    ${_dashboard_card("col-12", "bok-card-map bok-card-glossary-analysis-routes", _ui(locale, "term.analysis.routes"), _glossary_type_analysis_routes(locale, terms), Vector("reader", "contributor", "project_manager"))}
       |    ${_dashboard_card("col-12 col-xl-6", "bok-card-map bok-card-glossary-usage", _ui(locale, "term.usage.title"), _glossary_usage_card(locale, terms), Vector("reader", "contributor", "project_manager"))}
       |    ${_dashboard_card("col-12 col-xl-6", "bok-card-map bok-card-glossary-rdf", _ui(locale, "term.rdf.connection.title"), s"""<div id="term-rdf-connections">${_glossary_rdf_connection_card(locale, terms, dashboard)}</div>""", Vector("reader", "contributor", "project_manager"))}
       |    ${_dashboard_card("col-12 col-xl-6", "bok-card-map bok-card-glossary-project", _ui(locale, "term.project.connection.title"), s"""<div id="term-project-connections">${_glossary_project_connection_card(locale, terms, projects)}</div>""", Vector("reader", "contributor", "project_manager"))}
       |    ${_dashboard_card("col-12", "bok-card-map bok-card-glossary-type-issues", _ui(locale, "term.analysis.missing"), s"""<div id="term-issue-list">${_glossary_type_issue_terms(locale, terms)}</div>""", Vector("contributor", "project_manager"))}
       |    ${_dashboard_card("col-12", "bok-card-map bok-card-glossary-map", _ui(locale, "term.dashboard.groups.title"), s"""<div id="term-groups">${_term_group_cards(locale, terms, categories)}</div>""", Vector("reader", "contributor", "project_manager"))}
       |    ${_dashboard_card("col-12 col-md-6", "bok-card-map bok-card-glossary-language", _ui(locale, "term.language.index"), s"""<div id="language-index">${_glossary_language_links(config, languagerootprefix)}</div>""", Vector("reader"))}
       |    ${_dashboard_card("col-12 col-md-6", "bok-card-activity bok-card-glossary-recent", _ui(locale, "term.recent"), s"""<div id="recent-terms">${_glossary_recent_terms(terms)}</div>""", Vector("reader", "contributor"))}
       |  </div>
       |</div>""".stripMargin
  }

  private[bok] def _glossary_dedicated_page(
    config: BuildConfig,
    categories: Vector[CategoryContent],
    locale: String,
    page: Path,
    glossarybody: String,
    terms: Vector[TermEntry]
  ): String =
    s"""<!doctype html>
       |<html lang="${_html_escape(locale)}">
       |<head>
       |  <meta charset="utf-8">
       |  <meta name="viewport" content="width=device-width, initial-scale=1">
       |  <title>${_html_escape(_ui(locale, "glossary.title"))} - ${_html_escape(config.siteTitle)}</title>
       |${_site_css_links(config, page)}
       |</head>
       |<body class="article ${_html_escape(_support_dashboard_theme_class)}">
       |${_category_header(config, categories, locale)}
       |<div class="body body-dashboard bok-glossary-body">
       |  <main class="article bok-glossary-main">
       |    <div class="content">
       |      <article class="doc bok-glossary-doc">
       |        <section class="bok-dashboard-shell bok-glossary-dashboard" id="dashboard">
       |          ${_dashboard_hero(
                    _ui(locale, "glossary.title"),
                    _ui(locale, "glossary.description"),
                    Vector(
                      _ui(locale, "dashboard.kpi.terms") -> terms.size.toString,
                      _ui(locale, "dashboard.matrix.category") -> terms.flatMap(_.category).distinct.size.toString,
                      "RDF" -> terms.map(_.rdfrefs.size).sum.toString
                    )
                  )}
       |          ${glossarybody}
       |        </section>
       |      </article>
       |    </div>
       |  </main>
       |</div>
       |</body>
       |</html>
       |""".stripMargin

  private[bok] def _history_dashboard_body(locale: String): String =
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

  private[bok] def _manual_body_with_anchors(body: String): String =
    body.replace(
      "<h3>Glossary Term Classification</h3>",
      """<h3 id="glossary-term-classification">Glossary Term Classification</h3>"""
    )

}
