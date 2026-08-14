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

private[cozy] trait CozyBokGlossaryWorkflow {
  self: CozyBokImplementation.type =>
  private def _term_type_summary_cards(locale: String, terms: Vector[TermEntry]): String = {
    val counts = terms.groupBy(_.termtype).mapValues(_.size).toMap
    val items = Vector("concept", "event", "actor", "role", "rule").map { termtype =>
      val count = counts.getOrElse(termtype, 0)
      s"""<span class="bok-term-type-summary-item"><b>${count}</b>${_html_escape(_term_type_label(termtype, locale))}</span>"""
    }.mkString("\n")
    s"""<div class="bok-term-type-summary">${items}</div>"""
  }

  private def _mono_koto_summary_cards(locale: String, terms: Vector[TermEntry]): String = {
    val counts = terms.groupBy(_.monoKotoKind).mapValues(_.size).toMap
    val diagnostics = terms.count(_.cmlLinks.isEmpty)
    val items = Vector("mono", "koto", "rule").map { kind =>
      val count = counts.getOrElse(kind, 0)
      s"""<span class="bok-term-type-summary-item"><b>${count}</b>${_html_escape(_mono_koto_label(kind, locale))}</span>"""
    } :+ s"""<span class="bok-term-type-summary-item"><b>${diagnostics}</b>${_html_escape(_ui(locale, "term.analysis.unlinked"))}</span>"""
    s"""<div class="bok-term-type-summary bok-mono-koto-summary"><strong>${_html_escape(_ui(locale, "term.analysis"))}</strong>${items.mkString("\n")}</div>"""
  }

  private[bok] def _glossary_mono_koto_workflow(config: BuildConfig, locale: String, terms: Vector[TermEntry]): String = {
    val termcount = terms.size
    val extraction = _glossary_candidate_extraction_progress(config, terms)
    val candidateactual = extraction.actual
    val candidateplanned = extraction.planned
    val definedactual = terms.count(_is_term_basic_defined)
    val classifiedactual = terms.count(_is_term_classified)
    val rdfconnected = terms.count(_.rdfrefs.nonEmpty)
    val cmlconnected = terms.count(_.cmlLinks.nonEmpty)
    val stages = Vector(
      WorkflowStage("01", "extract", candidateactual, candidateplanned, extraction.systemerror, extraction.systemerrorfiles, Vector("article.dashboard" -> "../articles/index.html", "scenario.dashboard" -> "../scenarios/index.html"), _workflow_source_detail(locale, extraction.missing)),
      WorkflowStage("02", "define", definedactual, Some(termcount), Vector("recent.terms" -> "#term-recent-terms"), _workflow_definition_detail(locale, terms)),
      WorkflowStage("03", "classify", classifiedactual, Some(termcount), Vector("check.types" -> "#term-analysis-routes"), _workflow_refine_detail(locale, terms)),
      WorkflowStage("04", "rdf", rdfconnected, Some(termcount), Vector("connect.rdf" -> "#term-rdf-connections"), _workflow_term_detail(locale, "rdf", terms.filter(_.rdfrefs.isEmpty))),
      WorkflowStage("05", "cml", cmlconnected, Some(termcount), Vector("connect.cml" -> "#term-project-connections"), _workflow_term_detail(locale, "cml", terms.filter(_.cmlLinks.isEmpty)))
    )
    val systemerrorfiles = stages.flatMap(_.systemerrorfiles).distinct
    val systemerror = if (stages.exists(_.systemerror)) _workflow_system_error(locale, systemerrorfiles) else ""
    s"""<div class="bok-mono-koto-workflow">
       |  <p class="bok-mono-koto-lead">${_html_escape(_ui(locale, "term.analysis.workflow.lead"))}</p>
       |  ${systemerror}
       |  <div class="bok-workflow-stage-grid">
       |    ${stages.map(_workflow_stage_card(locale, _)).mkString("\n")}
       |  </div>
       |</div>""".stripMargin
  }

  private final case class WorkflowStage(
    step: String,
    key: String,
    actual: Int,
    planned: Option[Int],
    systemerror: Boolean,
    systemerrorfiles: Vector[String],
    actions: Vector[(String, String)],
    detailhtml: String
  )

  private object WorkflowStage {
    def apply(step: String, key: String, actual: Int, planned: Option[Int], actions: Vector[(String, String)], detailhtml: String): WorkflowStage =
      WorkflowStage(step, key, actual, planned, false, Vector.empty, actions, detailhtml)
  }

  private final case class WorkflowSourceStatus(kind: String, title: String, href: String)

  private final case class WorkflowExtractionProgress(
    actual: Int,
    planned: Option[Int],
    missing: Vector[WorkflowSourceStatus],
    systemerror: Boolean,
    systemerrorfiles: Vector[String]
  )

  private def _glossary_candidate_extraction_progress(
    config: BuildConfig,
    terms: Vector[TermEntry]
  ): WorkflowExtractionProgress = {
    val articleindex = _document_fragment_index(config)
    val scenarioindex = _scenario_index(config)
    val systemerror = articleindex.isEmpty || scenarioindex.isEmpty
    val systemerrorfiles =
      Vector(
        articleindex.isEmpty -> "doxsite.d/metadata/documents/fragments.json",
        scenarioindex.isEmpty -> "doxsite.d/metadata/scenarios/scenarios.json"
      ).collect {
        case (true, path) => path
      }
    val articles = articleindex.map(_.fragments.filter(_is_candidate_extraction_article)).getOrElse(Vector.empty)
    val scenarios = scenarioindex.map(_.scenarios).getOrElse(Vector.empty)
    val plannedarticles = articles.count(_.termextraction.isPlanned)
    val plannedscenarios = scenarios.count(_.termextraction.isPlanned)
    val extractedarticles = articles.count { article =>
      article.termextraction.isPlanned &&
      _is_term_extraction_done(article.termextraction)
    }
    val extractedscenarios = scenarios.count { scenario =>
      scenario.termextraction.isPlanned &&
      _is_term_extraction_done(scenario.termextraction)
    }
    val planned = plannedarticles + plannedscenarios
    val actual = extractedarticles + extractedscenarios
    val missingarticles = articles.filter { article =>
      article.termextraction.isPlanned &&
      !_is_term_extraction_done(article.termextraction)
    }.map { article =>
      WorkflowSourceStatus("article", article.effectiveHeadline.getOrElse(article.publicpath), "../" + article.publicpath)
    }
    val missingscenarios = scenarios.filter { scenario =>
      scenario.termextraction.isPlanned &&
      !_is_term_extraction_done(scenario.termextraction)
    }.map { scenario =>
      WorkflowSourceStatus("scenario", scenario.title, "../" + scenario.publicpath)
    }
    WorkflowExtractionProgress(actual, if (planned > 0) Some(planned) else None, missingarticles ++ missingscenarios, systemerror, systemerrorfiles)
  }

  private def _is_candidate_extraction_article(fragment: DocumentFragment): Boolean = {
    val source = _normalize_bok_path(fragment.sourcepath)
    val publicpath = _normalize_bok_path(fragment.publicpath)
    fragment.category.nonEmpty &&
    !source.startsWith("glossary/") &&
    !source.startsWith("scenario/") &&
    !source.startsWith("scenarios/") &&
    !source.startsWith("history/") &&
    !source.startsWith("manual/") &&
    !source.endsWith("/index.dox") &&
    !source.endsWith("/index.md") &&
    !publicpath.endsWith("/index.html")
  }

  private def _normalize_bok_path(value: String): String =
    value.stripPrefix("./").stripPrefix("../").stripPrefix("website.d/").stripPrefix("src/main/doxsite/")

  private[bok] def _is_term_extraction_done(progress: TermExtraction): Boolean =
    progress.isExtracted

  private def _workflow_stage_card(locale: String, stage: WorkflowStage): String = {
    val planned = stage.planned.filter(_ > 0)
    val planneddisplay = planned.map(_.toString).getOrElse("-")
    val percent = planned.map(x => math.max(0, math.min(100, stage.actual * 100 / x))).getOrElse(0)
    val statushtml =
      if (stage.systemerror)
        _workflow_stage_status(locale, "system.error")
      else planned match {
        case None => _workflow_stage_status(locale, "system.error")
        case Some(x) if stage.actual >= x => _workflow_stage_status(locale, "complete")
        case Some(_) => _workflow_stage_status(locale, "incomplete")
      }
    s"""<article class="bok-workflow-stage bok-workflow-stage-${_html_escape(stage.step)}">
       |  <div class="bok-workflow-stage-head">
       |    <span>${_html_escape(stage.step)}</span>
       |    <strong>${_html_escape(_ui(locale, s"term.analysis.workflow.${stage.key}.title"))}</strong>
       |  </div>
       |  <div class="bok-workflow-stage-metric">
       |    <b>${stage.actual} / ${_html_escape(planneddisplay)}</b>
       |    <span>${_html_escape(_ui(locale, "term.analysis.workflow.metric.actual.planned"))}</span>
       |  </div>
       |  <div class="bok-workflow-progress" aria-label="${_html_escape(_ui(locale, "term.analysis.workflow.progress"))}"><span style="width:${percent}%"></span></div>
       |  ${statushtml}
       |  <p>${_html_escape(_ui(locale, s"term.analysis.workflow.${stage.key}.description"))}</p>
       |  ${stage.detailhtml}
       |  ${_workflow_stage_actions(locale, stage.actions)}
       |</article>""".stripMargin
  }

  private def _is_mono_koto_classified(termtype: String): Boolean =
    termtype match {
      case "unclassified" => false
      case "concept" | "entity" | "actor" | "role" | "resource" | "artifact" => true
      case "event" | "action" | "process" | "task" | "rule" | "state" | "scenario" => true
      case _ => false
    }

  private def _is_term_classified(term: TermEntry): Boolean =
    _normalized_term_type(term.termtype) != "unclassified"

  private def _is_term_basic_defined(term: TermEntry): Boolean = {
    val hasdefinition = _strip_html(term.definitionhtml).trim.nonEmpty || term.summary.exists(_.trim.nonEmpty)
    term.title.trim.nonEmpty && hasdefinition
  }

  private def _strip_html(value: String): String =
    value.replaceAll("<[^>]+>", " ").replaceAll("\\s+", " ")

  private def _workflow_stage_actions(locale: String, links: Vector[(String, String)]): String =
    links.map {
      case (key, href) =>
        s"""<a class="bok-workflow-action" href="${_html_escape(href)}">${_html_escape(_ui(locale, s"term.analysis.workflow.action.${key}"))}</a>"""
    }.mkString("""<div class="bok-workflow-actions">""", "", "</div>")

  private def _workflow_system_error(locale: String, files: Vector[String]): String = {
    val details =
      if (files.isEmpty)
        ""
      else
        files.map(x => s"""<li><code>${_html_escape(x)}</code></li>""").mkString(
          s"""<div class="bok-workflow-system-error-files"><span>${_html_escape(_ui(locale, "term.analysis.workflow.system.error.files"))}</span><ul>""",
          "",
          "</ul></div>"
        )
    s"""<div class="bok-workflow-system-error">
       |  <strong>${_html_escape(_ui(locale, "term.analysis.workflow.system.error.title"))}</strong>
       |  <p>${_html_escape(_ui(locale, "term.analysis.workflow.system.error.description"))}</p>
       |  ${details}
       |  <p class="bok-workflow-system-error-repair">${_html_escape(_ui(locale, "term.analysis.workflow.system.error.repair"))}</p>
       |</div>""".stripMargin
  }

  private def _workflow_stage_status(locale: String, key: String): String =
    s"""<div class="bok-workflow-stage-status bok-workflow-stage-status-${_html_escape(key)}">${_html_escape(_ui(locale, s"term.analysis.workflow.status.${key}"))}</div>"""

  private def _workflow_source_detail(locale: String, missing: Vector[WorkflowSourceStatus]): String =
    _workflow_missing_detail(locale, missing.size, missing.take(4).map { source =>
      val label = _ui(locale, s"term.analysis.workflow.source.${source.kind}")
      s"""<li><a href="${_html_escape(source.href)}">${_html_escape(source.title)}</a><span>${_html_escape(label)}</span></li>"""
    })

  private def _workflow_term_detail(locale: String, key: String, missing: Vector[TermEntry]): String =
    _workflow_missing_detail(locale, missing.size, missing.take(4).map { term =>
      s"""<li><a href="${_html_escape(term.glossaryHref)}">${_html_escape(term.title)}</a><span>${_html_escape(_ui(locale, s"term.analysis.workflow.detail.${key}"))}</span></li>"""
    })

  private def _workflow_refine_detail(locale: String, terms: Vector[TermEntry]): String = {
    val missing = terms.filter(x => _normalized_term_type(x.termtype) == "unclassified")
    val summary = _workflow_refine_summary(locale, terms)
    _workflow_missing_detail(locale, missing.size, missing.take(4).map { term =>
      s"""<li><a href="${_html_escape(term.glossaryHref)}">${_html_escape(term.title)}</a><span>${_html_escape(_ui(locale, "term.analysis.workflow.detail.refine"))}</span></li>"""
    }, summary)
  }

  private def _workflow_definition_detail(locale: String, terms: Vector[TermEntry]): String = {
    val missing = terms.filterNot(_is_term_basic_defined)
    _workflow_missing_detail(locale, missing.size, missing.take(4).map { term =>
      s"""<li><a href="${_html_escape(term.glossaryHref)}">${_html_escape(term.title)}</a><span>${_html_escape(_term_definition_issue_label(locale, term))}</span></li>"""
    })
  }

  private def _term_definition_issue_label(locale: String, term: TermEntry): String = {
    val hasdefinition = _strip_html(term.definitionhtml).trim.nonEmpty || term.summary.exists(_.trim.nonEmpty)
    if (!hasdefinition)
      _ui(locale, "term.analysis.workflow.detail.define.basic")
    else
      _ui(locale, "term.analysis.workflow.detail.define")
  }

  private def _workflow_refine_summary(locale: String, terms: Vector[TermEntry]): String = {
    val counts = terms.groupBy(x => _mono_koto_group_for_type(_normalized_term_type(x.termtype))).map {
      case (k, v) => k -> v.size
    }
    val groups = Vector("mono", "koto", "unclassified")
    val items = groups.map { group =>
      val count = counts.getOrElse(group, 0)
      s"""<span class="bok-term-type-chip">${_html_escape(_ui(locale, s"term.analysis.workflow.group.${group}"))} ${count}</span>"""
    }.mkString
    s"""<div class="bok-workflow-refinement-split"><section><h4>${_html_escape(_ui(locale, "term.analysis.workflow.refine.breakdown"))}</h4><div class="bok-term-type-chip-list">${items}</div></section></div>"""
  }

  private def _mono_koto_group_for_type(termtype: String): String =
    termtype match {
      case "concept" | "entity" | "actor" | "role" | "resource" | "artifact" => "mono"
      case "event" | "action" | "process" | "task" | "state" | "scenario" | "rule" => "koto"
      case _ => "unclassified"
    }

  private def _workflow_missing_detail(locale: String, missingcount: Int, items: Vector[String], extrahtml: String = ""): String = {
    val title =
      if (missingcount == 0)
        _ui(locale, "term.analysis.workflow.detail.none")
      else
        _uif(locale, "term.analysis.workflow.detail.missing", missingcount.toString)
    val list =
      if (items.isEmpty)
        ""
      else
        s"""<ul>${items.mkString}</ul>"""
    s"""<div class="bok-workflow-detail"><strong>${_html_escape(title)}</strong>${extrahtml}${list}</div>"""
  }

  private[bok] def _term_extraction_progress_card(locale: String, actual: Int, planned: Int): String = {
    val percent = if (planned <= 0) 0 else math.max(0, math.min(100, actual * 100 / planned))
    val statushtml =
      if (planned <= 0)
        _workflow_stage_status(locale, "system.error")
      else if (actual >= planned)
        _workflow_stage_status(locale, "complete")
      else
        _workflow_stage_status(locale, "incomplete")
    s"""<div class="bok-term-extraction-progress">
       |  <div class="bok-workflow-stage-metric">
       |    <b>${actual} / ${if (planned <= 0) "-" else planned.toString}</b>
       |    <span>${_html_escape(_ui(locale, "term.analysis.workflow.metric.actual.planned"))}</span>
       |  </div>
       |  <div class="bok-workflow-progress" aria-label="${_html_escape(_ui(locale, "term.analysis.workflow.progress"))}"><span style="width:${percent}%"></span></div>
       |  ${statushtml}
       |  <p>${_html_escape(_ui(locale, "term.extraction.progress.description"))}</p>
       |</div>""".stripMargin
  }

  private[bok] def _glossary_rdf_connection_card(
    locale: String,
    terms: Vector[TermEntry],
    dashboard: Option[BokDashboard]
  ): String = {
    val termcount = terms.size
    val linkedterms = terms.count(_.rdfrefs.nonEmpty)
    val rdfrefs = terms.flatMap(_.rdfrefs)
    val externaluris = rdfrefs.count(ref => ref.resource.startsWith("http://") || ref.resource.startsWith("https://"))
    val triples = dashboard.map(_.rdf.triplecount.toString).getOrElse("-")
    val nodes = dashboard.map(_.rdf.resourcecount.toString).getOrElse("-")
    val breakdown = _rdf_connection_breakdown(locale, rdfrefs)
    s"""<div class="bok-connection-card bok-connection-card-rdf">
       |  <p>${_html_escape(_ui(locale, "term.rdf.connection.lead"))}</p>
       |  <div class="bok-connection-metrics">
       |    ${_connection_metric(_ui(locale, "term.rdf.metric.linked.terms"), s"${linkedterms} / ${termcount}", _ui(locale, "term.rdf.metric.linked.terms.note"))}
       |    ${_connection_metric(_ui(locale, "term.rdf.metric.triples"), triples, _ui(locale, "term.rdf.metric.triples.note"))}
       |    ${_connection_metric(_ui(locale, "term.rdf.metric.nodes"), nodes, _ui(locale, "term.rdf.metric.nodes.note"))}
       |    ${_connection_metric(_ui(locale, "term.rdf.metric.external"), externaluris.toString, _ui(locale, "term.rdf.metric.external.note"))}
       |  </div>
       |  <div class="bok-connection-breakdown">
       |    <strong>${_html_escape(_ui(locale, "term.rdf.breakdown"))}</strong>
       |    ${breakdown}
       |  </div>
       |  <p class="bok-connection-checkpoints">${_html_escape(_ui(locale, "term.rdf.checkpoints"))}</p>
       |  <div class="bok-special-links"><a class="bok-special-link" href="../rdf/index.html">${_html_escape(_ui(locale, "rdf.graph.title"))}</a></div>
       |</div>""".stripMargin
  }

  private[bok] def _glossary_usage_card(locale: String, terms: Vector[TermEntry]): String = {
    val termcount = terms.size
    val articleused = terms.count(_.articlerefs.nonEmpty)
    val scenarioused = terms.count(_.event.exists(_.scenarios.nonEmpty))
    val relatedused = terms.count(_.termrefs.nonEmpty)
    val unused = terms.filterNot(_is_term_used_in_bok)
    val unusedlist =
      if (unused.isEmpty)
        s"""<p class="bok-card-muted">${_html_escape(_ui(locale, "term.usage.unused.none"))}</p>"""
      else
        unused.take(6).map { term =>
          s"""<li><a href="${_html_escape(term.glossaryHref)}">${_html_escape(term.title)}</a></li>"""
        }.mkString("""<ul class="list-group bok-alert-list">""", "", "</ul>")
    s"""<div class="bok-connection-card bok-connection-card-usage">
       |  <p>${_html_escape(_ui(locale, "term.usage.lead"))}</p>
       |  <div class="bok-connection-metrics">
       |    ${_connection_metric(_ui(locale, "term.usage.metric.article"), s"${articleused} / ${termcount}", _ui(locale, "term.usage.metric.article.note"))}
       |    ${_connection_metric(_ui(locale, "term.usage.metric.scenario"), s"${scenarioused} / ${termcount}", _ui(locale, "term.usage.metric.scenario.note"))}
       |    ${_connection_metric(_ui(locale, "term.usage.metric.related"), s"${relatedused} / ${termcount}", _ui(locale, "term.usage.metric.related.note"))}
       |    ${_connection_metric(_ui(locale, "term.usage.metric.unused"), unused.size.toString, _ui(locale, "term.usage.metric.unused.note"))}
       |  </div>
       |  <div class="bok-connection-breakdown">
       |    <strong>${_html_escape(_ui(locale, "term.usage.unused.title"))}</strong>
       |    ${unusedlist}
       |  </div>
       |</div>""".stripMargin
  }

  private def _is_term_used_in_bok(term: TermEntry): Boolean =
    term.articlerefs.nonEmpty ||
      term.event.exists(_.scenarios.nonEmpty) ||
      term.termrefs.nonEmpty

  private[bok] def _glossary_project_connection_card(
    locale: String,
    terms: Vector[TermEntry],
    projects: Vector[CozyBokProjectPublisher.ResolvedBokProject]
  ): String = {
    val termcount = terms.size
    val cmllinkedterms = terms.count(_.cmlLinks.nonEmpty)
    val projectlinkedterms = terms.count(_.cmlLinks.nonEmpty)
    val sourceonlyprojects = projects.count(_.projectmode == "source-only")
    val breakdown = _cml_connection_breakdown(locale, terms.flatMap(_.cmlLinks))
    val linkedterms = _project_connected_term_list(locale, terms.filter(_.cmlLinks.nonEmpty))
    s"""<div class="bok-connection-card bok-connection-card-project">
       |  <p>${_html_escape(_ui(locale, "term.project.connection.lead"))}</p>
       |  <div class="bok-connection-metrics">
       |    ${_connection_metric(_ui(locale, "term.project.metric.cml.linked.terms"), s"${cmllinkedterms} / ${termcount}", _ui(locale, "term.project.metric.cml.linked.terms.note"))}
       |    ${_connection_metric(_ui(locale, "term.project.metric.project.linked.terms"), projectlinkedterms.toString, _ui(locale, "term.project.metric.project.linked.terms.note"))}
       |    ${_connection_metric(_ui(locale, "term.project.metric.projects"), projects.size.toString, _ui(locale, "term.project.metric.projects.note"))}
       |    ${_connection_metric(_ui(locale, "term.project.metric.source.only"), sourceonlyprojects.toString, _ui(locale, "term.project.metric.source.only.note"))}
       |  </div>
       |  <div class="bok-connection-breakdown">
       |    <strong>${_html_escape(_ui(locale, "term.project.cml.breakdown"))}</strong>
       |    ${breakdown}
       |  </div>
       |  <div class="bok-connection-breakdown">
       |    <strong>${_html_escape(_ui(locale, "term.project.connected.terms"))}</strong>
       |    ${linkedterms}
       |  </div>
       |  <p class="bok-connection-checkpoints">${_html_escape(_ui(locale, "term.project.checkpoints"))}</p>
       |  <p class="bok-connection-note">${_html_escape(_ui(locale, "term.project.source.only.note"))}</p>
       |  <p class="bok-connection-note">${_html_escape(_ui(locale, "term.project.cml.note"))}</p>
       |  <div class="bok-special-links"><a class="bok-special-link" href="../projects/index.html">${_html_escape(_ui(locale, "project.title"))}</a></div>
       |</div>""".stripMargin
  }

  private def _project_connected_term_list(locale: String, terms: Vector[TermEntry]): String = {
    if (terms.isEmpty)
      s"""<p class="bok-connection-note">${_html_escape(_ui(locale, "term.project.connected.terms.empty"))}</p>"""
    else
      terms.sortBy(_.title).take(8).map { term =>
        val kinds = term.cmlLinks.map(_.kind).distinct.map { kind =>
          s"""<span class="bok-connection-chip"><b>${_html_escape(_cml_kind_label(locale, kind))}</b></span>"""
        }.mkString
        s"""<li><a href="${_html_escape(term.glossaryHref)}">${_html_escape(term.title)}</a><span class="bok-connection-chip-list">${kinds}</span></li>"""
      }.mkString("""<ul class="bok-project-connected-term-list">""", "", "</ul>")
  }

  private def _connection_metric(label: String, value: String, note: String): String =
    s"""<div class="bok-connection-metric">
       |  <span>${_html_escape(label)}</span>
       |  <b>${_html_escape(value)}</b>
       |  <em>${_html_escape(note)}</em>
       |</div>""".stripMargin

  private def _rdf_connection_breakdown(locale: String, refs: Vector[TermRdfReference]): String = {
    val counts = Vector(
      "outgoing" -> refs.count(_.direction == "outgoing"),
      "incoming" -> refs.count(_.direction == "incoming"),
      "identity" -> refs.count(ref => _rdf_predicate_group(ref.predicate) == "identity"),
      "description" -> refs.count(ref => _rdf_predicate_group(ref.predicate) == "description"),
      "hierarchy" -> refs.count(ref => _rdf_predicate_group(ref.predicate) == "hierarchy"),
      "provenance" -> refs.count(ref => _rdf_predicate_group(ref.predicate) == "provenance")
    )
    _connection_breakdown_chips(locale, counts)
  }

  private def _rdf_predicate_group(predicate: Option[String]): String = {
    val p = predicate.getOrElse("").toLowerCase(Locale.ROOT)
    if (p.contains("sameas") || p.contains("exactmatch") || p.contains("closematch") || p.contains("primaryrdfanchor") || p.endsWith("type"))
      "identity"
    else if (p.contains("label") || p.contains("comment") || p.contains("description") || p.contains("definition"))
      "description"
    else if (p.contains("broader") || p.contains("narrower") || p.contains("subclass") || p.contains("partof"))
      "hierarchy"
    else if (p.contains("source") || p.contains("provenance") || p.contains("wasderivedfrom") || p.contains("evidence"))
      "provenance"
    else
      "links"
  }

  private def _cml_connection_breakdown(locale: String, links: Vector[TermCmlLink]): String = {
    val kinds = Vector("entity", "value", "powertype", "event", "operation", "statemachine", "rule", "component", "service")
    val counts = kinds.map(kind => _cml_kind_label(locale, kind) -> links.count(_.kind == kind))
    _connection_breakdown_chips(locale, counts)
  }

  private def _cml_kind_label(locale: String, kind: String): String =
    kind match {
      case "entity" => "entity"
      case "value" => "value"
      case "powertype" => "powertype"
      case "event" => "event"
      case "operation" => "operation"
      case "statemachine" => "statemachine"
      case "rule" => "rule"
      case "component" => "component"
      case "service" => "service"
      case other => other
    }

  private def _connection_breakdown_chips(locale: String, counts: Vector[(String, Int)]): String =
    counts.map {
      case (label, count) =>
        s"""<span class="bok-connection-chip"><b>${_html_escape(label)}</b><em>${count}</em></span>"""
    }.mkString("""<div class="bok-connection-chip-list">""", "", "</div>")

}
