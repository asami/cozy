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

private[cozy] trait CozyBokGlossaryAnalysis {
  self: CozyBokImplementation.type =>
  private[bok] def _safe_resolved_project_packages(config: BuildConfig): Vector[CozyBokProjectPublisher.ResolvedBokProject] =
    try {
      _resolved_project_packages(config)
    } catch {
      case NonFatal(_) => Vector.empty
    }

  private def _term_type_chip_list(termtypes: Vector[String], locale: String): String =
    termtypes.map { termtype =>
      s"""<span class="bok-term-type-chip bok-term-type-chip-${_html_escape(termtype)}">${_html_escape(_term_type_label(termtype, locale))}</span>"""
    }.mkString("""<div class="bok-term-type-chip-list">""", "", "</div>")

  private val _term_type_route_groups: Vector[(String, Vector[String])] =
    Vector(
      "unclassified" -> Vector("unclassified"),
      "mono" -> Vector("concept", "entity", "actor", "role", "resource", "artifact"),
      "koto" -> Vector("event", "action", "process", "task", "rule", "state", "scenario")
    )

  private val _term_type_route_order: Vector[String] =
    _term_type_route_groups.flatMap(_._2)

  private[bok] def _glossary_type_analysis_routes(locale: String, terms: Vector[TermEntry]): String = {
    val grouped = terms.groupBy(x => _normalized_term_type(x.termtype)).withDefaultValue(Vector.empty)
    val sections = _term_type_route_groups.map {
      case (kind, termtypes) =>
        val count = termtypes.map(t => grouped(t).size).sum
        val issuecount = termtypes.flatMap(t => grouped(t)).map(_glossary_term_issues(_, locale).size).sum
        val routes = termtypes.map(_glossary_type_analysis_route_card(locale, grouped, _)).mkString("\n")
        s"""<section class="bok-term-analysis-route-group bok-term-analysis-route-group-${_html_escape(kind)}">
           |  <div class="bok-term-analysis-route-group-head">
           |    <div>
           |      <span class="badge bok-badge-info">${_html_escape(_term_type_route_group_label(kind, locale))}</span>
           |      <h4>${_html_escape(_ui(locale, s"term.analysis.route.group.${kind}.title"))}</h4>
           |      <p>${_html_escape(_ui(locale, s"term.analysis.route.group.${kind}.description"))}</p>
           |    </div>
           |    <dl>
           |      <dt>${_html_escape(_ui(locale, "term.metric.terms"))}</dt><dd>${count}</dd>
           |      <dt>${_html_escape(_ui(locale, "term.analysis.route.issue.count"))}</dt><dd>${issuecount}</dd>
           |    </dl>
           |  </div>
           |  <div class="bok-term-analysis-route-grid">${routes}</div>
           |</section>""".stripMargin
    }.mkString("\n")
    s"""<div id="term-analysis-routes" class="bok-term-analysis-route-groups">${sections}</div>"""
  }

  private def _glossary_type_analysis_route_card(
    locale: String,
    grouped: Map[String, Vector[TermEntry]],
    termtype: String
  ): String = {
    val xs = grouped.getOrElse(termtype, Vector.empty).sortBy(x => (-_glossary_term_score(x), x.title))
    val examples = _glossary_route_term_links(xs.take(3))
    val issuecount = xs.map(_glossary_term_issues(_, locale).size).sum
    s"""<article class="bok-term-analysis-route bok-term-analysis-route-${_html_escape(termtype)}">
       |  <div class="bok-term-analysis-route-head">
       |    <span class="badge bok-badge-info">${_html_escape(_term_type_label(termtype, locale))}</span>
       |    <strong>${xs.size}</strong>
       |  </div>
       |  <p>${_html_escape(_ui(locale, s"term.analysis.route.${termtype}.description"))}</p>
       |  <dl class="bok-term-analysis-route-checkpoints">
       |    <dt>${_html_escape(_ui(locale, "term.analysis.route.checkpoints"))}</dt>
       |    <dd>${_html_escape(_ui(locale, s"term.analysis.route.${termtype}.checkpoints"))}</dd>
       |    <dt>${_html_escape(_ui(locale, "term.analysis.route.issue.count"))}</dt>
       |    <dd>${issuecount}</dd>
       |  </dl>
       |  ${examples}
       |  <div class="bok-workflow-actions"><a class="bok-workflow-action" href="../manual/index.html#glossary-term-classification">${_html_escape(_ui(locale, "term.analysis.route.manual"))}</a></div>
       |</article>""".stripMargin
  }

  private def _is_entity_nesting_relation(value: String): Boolean = {
    val v = Option(value).map(_.trim.toLowerCase.replace("_", "-")).getOrElse("")
    v == "parent" ||
      v == "child" ||
      v == "part-of" ||
      v == "has-part" ||
      v == "contains" ||
      v == "contained-by" ||
      v == "broader" ||
      v == "narrower"
  }

  private val _operation_target_order: Vector[String] =
    Vector("entity", "file", "url", "external-id", "rdf-node", "artifact", "message-event", "job-task", "none")

  private val _operation_target_groups: Vector[(String, Vector[String])] =
    Vector(
      "managed" -> Vector("entity", "artifact", "message-event", "job-task"),
      "addressable" -> Vector("file", "url", "external-id", "rdf-node"),
      "none" -> Vector("none")
    )

  private def _glossary_operation_target_card(locale: String, terms: Vector[TermEntry]): String = {
    s"""<div id="term-operation-targets" class="bok-operation-targets">
       |  <p class="bok-card-lead">${_html_escape(_ui(locale, "term.operation.targets.lead"))}</p>
       |  <div class="bok-software-term-grid">
       |    ${_space_knowledge_panel(locale, terms)}
       |    ${_space_term_panel(locale, terms)}
       |    ${_space_project_panel(locale, terms)}
       |  </div>
       |  <div class="bok-workflow-actions">
       |    <a class="bok-workflow-action" href="#term-project-connections">${_html_escape(_ui(locale, "term.operation.targets.action.project"))}</a>
       |    <a class="bok-workflow-action" href="#term-rdf-connections">${_html_escape(_ui(locale, "term.operation.targets.action.rdf"))}</a>
       |    <a class="bok-workflow-action" href="#term-analysis-routes">${_html_escape(_ui(locale, "term.operation.targets.action.types"))}</a>
       |  </div>
       |</div>""".stripMargin
  }

  private def _space_knowledge_panel(locale: String, terms: Vector[TermEntry]): String = {
    val articlelinked = terms.count(_.articlerefs.nonEmpty)
    val scenariolinked = terms.count(_.event.exists(_.scenarios.nonEmpty))
    val rdflinked = terms.count(_.rdfrefs.nonEmpty)
    val relationlinked = terms.count(_.termrefs.nonEmpty)
    s"""<section class="bok-software-term-panel bok-software-term-panel-knowledge">
       |  <div class="bok-software-term-panel-head">
       |    <span>${_html_escape(_ui(locale, "term.space.knowledge.eyebrow"))}</span>
       |    <h4>${_html_escape(_ui(locale, "term.space.knowledge.title"))}</h4>
       |    <strong>${terms.size}</strong>
       |  </div>
       |  <p>${_html_escape(_ui(locale, "term.space.knowledge.description"))}</p>
       |  <dl class="bok-software-term-metrics">
       |    <dt>${_html_escape(_ui(locale, "term.space.metric.article"))}</dt><dd>${articlelinked}</dd>
       |    <dt>${_html_escape(_ui(locale, "term.space.metric.scenario"))}</dt><dd>${scenariolinked}</dd>
       |    <dt>${_html_escape(_ui(locale, "term.space.metric.rdf"))}</dt><dd>${rdflinked}</dd>
       |    <dt>${_html_escape(_ui(locale, "term.space.metric.term.relation"))}</dt><dd>${relationlinked}</dd>
       |  </dl>
       |</section>""".stripMargin
  }

  private def _space_term_panel(locale: String, allterms: Vector[TermEntry]): String = {
    val terms = allterms.filter(_is_resource_term)
    val entityterms = terms.filter(term => _effective_resource_type(term).contains("entity"))
    val typedterms = terms.filter(term => _effective_resource_type(term).isDefined)
    val untypedterms = terms.filter(term => _effective_resource_type(term).isEmpty)
    val nonentitychips = _resource_type_order.filterNot(_ == "entity").map { resourcetype =>
      val count = terms.count(term => _effective_resource_type(term).contains(resourcetype))
      s"""<span class="bok-software-target-chip"><b>${_html_escape(_ui(locale, s"term.resource.type.${resourcetype}.label"))}</b><em>${count}</em></span>"""
    }.mkString("""<div class="bok-software-target-chip-list">""", "", "</div>")
    s"""<section class="bok-software-term-panel bok-software-term-panel-entity">
       |  <div class="bok-software-term-panel-head">
       |    <span>${_html_escape(_ui(locale, "term.space.term.eyebrow"))}</span>
       |    <h4>${_html_escape(_ui(locale, "term.space.term.title"))}</h4>
       |    <strong>${terms.size}</strong>
       |  </div>
       |  <p>${_html_escape(_ui(locale, "term.space.term.description"))}</p>
       |  <dl class="bok-software-term-metrics">
       |    <dt>${_html_escape(_ui(locale, "term.project.metric.resource.entity"))}</dt><dd>${entityterms.size}</dd>
       |    <dt>${_html_escape(_ui(locale, "term.project.metric.resource.typed"))}</dt><dd>${typedterms.size}</dd>
       |    <dt>${_html_escape(_ui(locale, "term.project.metric.resource.untyped"))}</dt><dd>${untypedterms.size}</dd>
       |  </dl>
       |  ${nonentitychips}
       |  ${_glossary_route_term_links(terms.sortBy(x => (-_glossary_term_score(x), x.title)).take(4))}
       |</section>""".stripMargin
  }

  private def _space_project_panel(locale: String, terms: Vector[TermEntry]): String = {
    val actorroleterms = terms.filter(term => Set("actor", "role").contains(_normalized_term_type(term.termtype)))
    val actorroleentity = actorroleterms.count(_.cmlLinks.exists(x => _normalize_token(x.kind) == "entity"))
    val cmlentity = terms.count(_.cmlLinks.exists(x => _normalize_token(x.kind) == "entity"))
    val cmlother = terms.count(term => term.cmlLinks.exists(x => _normalize_token(x.kind) != "entity"))
    val artifact = terms.count(term => _effective_resource_type(term).contains("artifact"))
    s"""<section class="bok-software-term-panel bok-software-term-panel-non-entity">
       |  <div class="bok-software-term-panel-head">
       |    <span>${_html_escape(_ui(locale, "term.space.project.eyebrow"))}</span>
       |    <h4>${_html_escape(_ui(locale, "term.space.project.title"))}</h4>
       |    <strong>${cmlentity + cmlother + artifact}</strong>
       |  </div>
       |  <p>${_html_escape(_ui(locale, "term.space.project.description"))}</p>
       |  <dl class="bok-software-term-metrics">
       |    <dt>${_html_escape(_ui(locale, "term.project.metric.mapping.cml.entity"))}</dt><dd>${cmlentity}</dd>
       |    <dt>${_html_escape(_ui(locale, "term.project.metric.mapping.cml.other"))}</dt><dd>${cmlother}</dd>
       |    <dt>${_html_escape(_ui(locale, "term.project.metric.actor.role.entity"))}</dt><dd>${actorroleentity}</dd>
       |    <dt>${_html_escape(_ui(locale, "term.project.metric.mapping.publication"))}</dt><dd>${artifact}</dd>
       |  </dl>
       |  ${_glossary_route_term_links(terms.filter(_.cmlLinks.nonEmpty).sortBy(x => (-_glossary_term_score(x), x.title)).take(4))}
       |</section>""".stripMargin
  }

  private val _resource_type_order: Vector[String] =
    Vector("entity", "file", "url", "external_id", "rdf_node", "artifact", "dataset", "service_endpoint")

  private def _is_resource_term(term: TermEntry): Boolean =
    _normalized_term_type(term.termtype) == "resource" ||
      _effective_resource_type(term).isDefined

  private def _effective_resource_type(term: TermEntry): Option[String] =
    term.resourcetype.map(_normalize_token).orElse {
      _normalized_term_type(term.termtype) match {
        case "entity" => Some("entity")
        case "artifact" => Some("artifact")
        case _ => None
      }
    }.map(_.replace("-", "_"))

  private def _term_matches_operation_target(term: TermEntry, target: String): Boolean = {
    val matches = _operation_targets_for_term(term)
    target match {
      case "none" => matches.isEmpty
      case _ => matches.contains(target)
    }
  }

  private def _operation_targets_for_term(term: TermEntry): Set[String] = {
    val termtype = _normalized_term_type(term.termtype)
    val cmlkinds = term.cmlLinks.map(x => _normalize_token(x.kind)).toSet
    val rdfresources = term.rdfrefs.map(_.resource)
    Set.empty[String] ++
      _operation_target_if(termtype == "entity" || cmlkinds.contains("entity"), "entity") ++
      _operation_target_if(termtype == "artifact" || cmlkinds.contains("artifact"), "artifact") ++
      _operation_target_if(termtype == "event" || cmlkinds.contains("event"), "message-event") ++
      _operation_target_if(termtype == "task" || cmlkinds.contains("operation"), "job-task") ++
      _operation_target_if(term.rdfrefs.nonEmpty, "rdf-node") ++
      _operation_target_if(rdfresources.exists(_looks_like_url), "url") ++
      _operation_target_if(rdfresources.exists(_looks_like_external_id), "external-id") ++
      _operation_target_if(rdfresources.exists(_looks_like_file_target), "file")
  }

  private def _operation_target_if(condition: Boolean, value: String): Set[String] =
    if (condition) Set(value) else Set.empty

  private def _normalize_token(value: String): String =
    Option(value).map(_.trim.toLowerCase(Locale.ROOT).replace("_", "-")).getOrElse("")

  private def _looks_like_url(value: String): Boolean = {
    val v = Option(value).map(_.trim.toLowerCase(Locale.ROOT)).getOrElse("")
    v.startsWith("http://") || v.startsWith("https://")
  }

  private def _looks_like_external_id(value: String): Boolean = {
    val v = Option(value).map(_.trim.toLowerCase(Locale.ROOT)).getOrElse("")
    v.contains("wikidata.org/entity/") ||
      v.contains("doi.org/") ||
      v.startsWith("doi:") ||
      v.startsWith("isbn:") ||
      v.startsWith("orcid:")
  }

  private def _looks_like_file_target(value: String): Boolean = {
    val v = Option(value).map(_.trim.toLowerCase(Locale.ROOT)).getOrElse("")
    v.startsWith("file:") ||
      v.startsWith("/") ||
      v.endsWith(".pdf") ||
      v.endsWith(".dox") ||
      v.endsWith(".md") ||
      v.endsWith(".json") ||
      v.endsWith(".ttl") ||
      v.endsWith(".jsonld")
  }

  private[bok] def _normalized_term_type(value: String): String =
    if (Option(value).map(_.trim).getOrElse("").isEmpty)
      "unclassified"
    else
      value.trim

  private def _term_type_route_group_label(value: String, locale: String): String = value match {
    case "unclassified" => _ui(locale, "term.type.unclassified")
    case other => _mono_koto_label(other, locale)
  }

  private def _glossary_route_term_links(terms: Vector[TermEntry]): String =
    if (terms.isEmpty)
      """<p class="bok-card-muted">-</p>"""
    else
      terms.map { term =>
        s"""<li><a href="${_html_escape(term.glossaryHref)}">${_html_escape(term.title)}</a>${_reading_label(term)}</li>"""
      }.mkString("""<ul class="list-group bok-map-list bok-term-analysis-route-terms">""", "", "</ul>")

  private[bok] def _glossary_type_issue_terms(locale: String, terms: Vector[TermEntry]): String = {
    val groups = _term_type_route_order.flatMap { termtype =>
      val rows = terms.filter(x => _normalized_term_type(x.termtype) == termtype).flatMap { term =>
        val issues = _glossary_term_issues(term, locale)
        if (issues.isEmpty)
          None
        else
          Some(
            s"""<li class="list-group-item">
               |  <a href="${_html_escape(term.glossaryHref)}">${_html_escape(term.title)}</a>
               |  <span>${issues.map(_html_escape).mkString(", ")}</span>
               |</li>""".stripMargin
          )
      }
      if (rows.isEmpty)
        None
      else
        Some(
          s"""<section class="bok-term-type-issue-group bok-term-type-issue-group-${_html_escape(termtype)}">
             |  <h4>${_html_escape(_term_type_label(termtype, locale))}</h4>
             |  ${rows.take(6).mkString("""<ul class="list-group bok-alert-list">""", "", "</ul>")}
             |</section>""".stripMargin
        )
    }
    if (groups.isEmpty)
      s"""<p class="bok-card-muted">${_html_escape(_ui(locale, "term.analysis.missing.none"))}</p>"""
    else
      groups.mkString("""<div class="bok-term-type-issue-grid">""", "\n", "</div>")
  }

  private def _glossary_term_score(term: TermEntry): Int =
    term.rdfrefs.size * 3 +
      term.articlerefs.size * 3 +
      term.termrefs.size * 2 +
      term.videorefs.size +
      term.cmlLinks.size * 2 +
      term.tags.size

  private def _glossary_term_issues(term: TermEntry, locale: String): Vector[String] = {
    val quality = Vector(
      term.quality.isolated -> _ui(locale, "term.quality.isolated"),
      term.quality.unreferenced -> _ui(locale, "term.quality.unreferenced"),
      term.quality.weaklyconnected -> _ui(locale, "term.quality.weakly.connected")
    ).collect { case (true, label) => label }
    val typeissues = _normalized_term_type(term.termtype) match {
      case "unclassified" =>
        Vector(_ui(locale, "term.analysis.issue.unclassified"))
      case "event" =>
        val event = term.event
        Vector(
          event.forall(_.scenarios.isEmpty) -> _ui(locale, "term.analysis.issue.event.scenario.missing"),
          event.forall(_.actors.isEmpty) -> _ui(locale, "term.analysis.issue.event.actor.missing"),
          event.forall(_.roles.isEmpty) -> _ui(locale, "term.analysis.issue.event.role.missing"),
          !term.cmlLinks.exists(x => x.kind == "event" || x.kind == "statemachine") -> _ui(locale, "term.analysis.issue.event.cml.missing")
        ).collect { case (true, label) => label }
      case "actor" =>
        Vector(
          term.actor.forall(_.roles.isEmpty) -> _ui(locale, "term.analysis.issue.actor.role.missing"),
          (term.termrefs.isEmpty && term.articlerefs.isEmpty) -> _ui(locale, "term.analysis.issue.actor.usage.missing")
        ).collect { case (true, label) => label }
      case "role" =>
        Vector(
          term.role.forall(_.actors.isEmpty) -> _ui(locale, "term.analysis.issue.role.actor.missing"),
          term.role.forall(_.responsibilities.isEmpty) -> _ui(locale, "term.analysis.issue.role.responsibility.missing"),
          term.role.forall(_.permissions.isEmpty) -> _ui(locale, "term.analysis.issue.role.permission.missing")
        ).collect { case (true, label) => label }
      case "rule" =>
        Vector(
          !term.cmlLinks.exists(_.kind == "rule") -> _ui(locale, "term.analysis.issue.rule.cml.missing"),
          (term.articlerefs.isEmpty && term.rdfrefs.isEmpty) -> _ui(locale, "term.analysis.issue.rule.evidence.missing")
        ).collect { case (true, label) => label }
      case _ =>
        Vector(
          term.rdfrefs.isEmpty -> _ui(locale, "term.analysis.issue.concept.rdf.missing"),
          term.articlerefs.isEmpty -> _ui(locale, "term.analysis.issue.concept.article.missing"),
          term.cmlLinks.isEmpty -> _ui(locale, "term.analysis.issue.concept.cml.missing")
        ).collect { case (true, label) => label }
    }
    (typeissues ++ quality).distinct
  }

  private[bok] def _glossary_metric_cards(
    locale: String,
    categorycount: Int,
    categorieswithterms: Int,
    terms: Vector[TermEntry]
  ): String = {
    val termcount = terms.size
    val typecount = terms.map(_.termtype).filter(_.nonEmpty).distinct.size
    val rdfcount = terms.map(_.rdfrefs.size).sum
    val articlecount = terms.map(_.articlerefs.size).sum
    val cmlcount = terms.map(_.cmlLinks.size).sum
    val rdfconnected = terms.count(_.rdfrefs.nonEmpty)
    val articleconnected = terms.count(_.articlerefs.nonEmpty)
    val cmlconnected = terms.count(_.cmlLinks.nonEmpty)
    s"""<div class="bok-dashboard-grid bok-glossary-summary-metrics">
       |  ${_glossary_metric_card(_ui(locale, "term.metric.terms"), termcount.toString, _ui(locale, "term.metric.terms.note"))}
       |  ${_glossary_metric_card(_ui(locale, "term.metric.categories"), categorycount.toString, _uif(locale, "term.metric.categories.note", categorieswithterms.toString))}
       |  ${_glossary_metric_card(_ui(locale, "term.metric.types"), typecount.toString, _ui(locale, "term.metric.types.note"))}
       |  ${_glossary_metric_card(_ui(locale, "term.metric.rdf.links"), rdfcount.toString, _ui(locale, "term.metric.rdf.links.note"))}
       |  ${_glossary_metric_card(_ui(locale, "term.metric.article.links"), articlecount.toString, _ui(locale, "term.metric.article.links.note"))}
       |  ${_glossary_metric_card(_ui(locale, "term.metric.cml.links"), cmlcount.toString, _ui(locale, "term.metric.cml.links.note"))}
       |</div>
       |<div class="bok-glossary-connection-rates">
       |  ${_glossary_connection_rate(_ui(locale, "term.metric.rdf.connected"), rdfconnected, termcount)}
       |  ${_glossary_connection_rate(_ui(locale, "term.metric.article.connected"), articleconnected, termcount)}
       |  ${_glossary_connection_rate(_ui(locale, "term.metric.cml.connected"), cmlconnected, termcount)}
       |</div>""".stripMargin
  }

  private def _glossary_metric_card(label: String, value: String, note: String): String =
    s"""<div class="bok-metric-card">
       |  <div class="bok-metric-label">${_html_escape(label)}</div>
       |  <div class="bok-metric-value">${_html_escape(value)}</div>
       |  <div class="bok-metric-note">${_html_escape(note)}</div>
       |</div>""".stripMargin

  private def _glossary_connection_rate(label: String, connected: Int, total: Int): String =
    s"""<span class="bok-glossary-connection-rate"><b>${_html_escape(label)}</b><span>${connected} / ${total}</span></span>"""

  private[bok] def _language_index_root_prefix(config: BuildConfig): String =
    config.localeMode match {
      case LocaleMode.SingleLocaleRoot => "../"
      case LocaleMode.MultiLocaleSubdirs => "../../"
    }

  private[bok] def _glossary_language_links(config: BuildConfig, rootprefix: String): String = {
    val langs = (config.languages ++ Vector("ja", "en")).distinct.filter(x => x == "ja" || x == "en")
    langs.map {
      case "ja" => s"""<a class="bok-special-link" href="${rootprefix}ja/glossary/index.html">日本語索引ページ</a>"""
      case "en" => s"""<a class="bok-special-link" href="${rootprefix}en/glossary/index.html">英語索引ページ</a>"""
      case other => s"""<a class="bok-special-link" href="${rootprefix}${_html_escape(other)}/glossary/index.html">${_html_escape(other)} index page</a>"""
    }.mkString("""<div class="bok-special-links">""", "\n", "</div>")
  }

  private[bok] def _glossary_recent_terms(terms: Vector[TermEntry]): String =
    if (terms.isEmpty)
      "<p>No glossary terms yet.</p>"
    else
      terms.take(10).map { term =>
        s"""<li><a href="${_html_escape(term.glossaryHref)}">${_html_escape(term.title)}</a>${_reading_label(term)}: ${_html_escape(term.categorySlug)}</li>"""
      }.mkString("<ol>\n", "\n", "\n</ol>")

  private def _glossary_index_href(href: String): String =
    href.stripPrefix("../glossary/").stripPrefix("glossary/")

}
