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

private[cozy] trait CozyBokDashboardAnalysis {
  self: CozyBokImplementation.type =>
  private[bok] def _dashboard_actor_filter_script: String =
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
      |    var select = root.querySelector("select[data-bok-actor-filter]");
      |    if (select) {
      |      var option = select.querySelector("option[value='" + actor + "']");
      |      if (option) return option.textContent;
      |    }
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
      |    root.querySelectorAll("[data-bok-actor-filter]").forEach(function (control) {
      |      if (control.tagName === "SELECT") {
      |        control.value = actor;
      |      } else {
      |        var selected = control.getAttribute("data-bok-actor-filter") === actor;
      |        control.classList.toggle("is-active", selected);
      |        control.setAttribute("aria-pressed", selected ? "true" : "false");
      |      }
      |    });
      |    root.querySelectorAll("[data-bok-actor-mode]").forEach(function (control) {
      |      if (control.tagName === "SELECT") {
      |        control.value = mode;
      |      } else {
      |        var selected = control.getAttribute("data-bok-actor-mode") === mode;
      |        control.classList.toggle("is-active", selected);
      |        control.setAttribute("aria-pressed", selected ? "true" : "false");
      |      }
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
      |    root.querySelectorAll("[data-bok-actor-filter]").forEach(function (control) {
      |      var eventName = control.tagName === "SELECT" ? "change" : "click";
      |      control.addEventListener(eventName, function () {
      |        var value = control.tagName === "SELECT" ? control.value : control.getAttribute("data-bok-actor-filter");
      |        applyActor(root, value || defaultActor(root), root.getAttribute("data-bok-card-mode") || "hide", true);
      |      });
      |    });
      |    root.querySelectorAll("[data-bok-actor-mode]").forEach(function (control) {
      |      var eventName = control.tagName === "SELECT" ? "change" : "click";
      |      control.addEventListener(eventName, function () {
      |        var value = control.tagName === "SELECT" ? control.value : control.getAttribute("data-bok-actor-mode");
      |        applyActor(root, root.getAttribute("data-bok-current-actor") || defaultActor(root), value || "hide", true);
      |      });
      |    });
      |  });
      |}());
      |</script>""".stripMargin

  private[bok] def _dashboard_card(column: String, semantic: String, title: String, body: String, actors: Vector[String] = Vector.empty, anchorid: Option[String] = None): String = {
    val actorattr =
      if (actors.isEmpty)
        ""
      else
        s""" data-bok-actors="${_html_escape(actors.mkString(" "))}""""
    val idattr = anchorid.map(x => s""" id="${_html_escape(x)}"""").getOrElse("")
    s"""<div class="${_html_escape(column)}" data-bok-card="true">
       |  <section class="card bok-card ${_html_escape(semantic)}"${idattr}${actorattr}>
       |    <div class="card-body">
       |      <h3 class="card-title">${_html_escape(title)}</h3>
       |      ${body}
       |    </div>
       |  </section>
       |</div>""".stripMargin
  }

  private[bok] def _recent_activity_card(config: BuildConfig, locale: String, dashboard: BokDashboard, fallbackitems: Vector[DashboardRecentItem]): String = {
    val actorattr = """ data-bok-actors="reader contributor project_manager""""
    s"""<div class="col-12" data-bok-card="true">
       |  <section class="card bok-card bok-card-activity bok-card-notification"${actorattr}>
       |    <div class="card-body">
       |      <h3 class="card-title bok-card-title-with-action"><span>${_html_escape(_ui(locale, "dashboard.card.recent.activity"))}</span><a class="bok-card-title-link" href="${_html_escape(_history_href(config, ""))}">${_html_escape(_ui(locale, "dashboard.activity.open.history"))}</a></h3>
       |      ${_recent_activity_body(locale, dashboard.increments, fallbackitems, true)}
       |    </div>
       |  </section>
       |</div>""".stripMargin
  }

  private[bok] def _recent_activity_card(config: BuildConfig, locale: String, category: CategoryContent): Option[String] = {
    val items = _category_recent_items(config, category)
    if (items.isEmpty)
      None
    else {
    val actorattr = """ data-bok-actors="reader contributor project_manager""""
    Some(s"""<div class="col-12 col-md-6 col-xl-4" data-bok-card="true">
       |  <section class="card bok-card bok-card-activity bok-card-notification"${actorattr}>
       |    <div class="card-body">
       |      <h3 class="card-title bok-card-title-with-action"><span>${_html_escape(_ui(locale, "dashboard.card.recent.changes"))}</span><a class="bok-card-title-link" href="${_html_escape(_history_href(config, "../"))}">${_html_escape(_ui(locale, "dashboard.activity.open.history"))}</a></h3>
       |      ${_recent_activity_body(locale, DashboardIncrements("day", Vector.empty), items, false)}
       |    </div>
       |  </section>
       |</div>""".stripMargin)
    }
  }

  private[bok] def _kpi_card(locale: String, column: String, label: String, value: String, note: String): String =
    _dashboard_card(
      column,
      "bok-card-kpi",
      label,
      s"""<div class="bok-kpi-value">${_html_escape(value)}</div>
         |<div class="bok-kpi-label">${_html_escape(label)}</div>
         |<div class="bok-kpi-note">${_html_escape(note)}</div>""".stripMargin,
      Vector("reader", "contributor", "project_manager")
    )

  private[bok] def _kpi_card_link(column: String, label: String, value: String, note: String, href: String, actors: Vector[String] = Vector("reader", "contributor", "project_manager")): String =
    _dashboard_card(
      column,
      "bok-card-kpi bok-card-kpi-link",
      label,
      s"""<a class="bok-kpi-link" href="${_html_escape(href)}">
         |  <span class="bok-kpi-value">${_html_escape(value)}</span>
         |  <span class="bok-kpi-label">${_html_escape(label)}</span>
         |  <span class="bok-kpi-note">${_html_escape(note)}</span>
         |</a>""".stripMargin,
      actors
    )

  private[bok] def _analysis_entry_card(
    locale: String,
    termcount: Int,
    articlecount: Int,
    scenariocount: Int,
    projectcount: Int,
    rdfcount: Int,
    articlehref: String,
    glossaryhref: String,
    scenariohref: String,
    projecthref: String,
    rdfhref: String
  ): String =
    _dashboard_card(
      "col-12",
      "bok-card-analysis-entry",
      _ui(locale, "dashboard.card.analysis.entry"),
      s"""<p class="bok-analysis-entry-lead">${_html_escape(_ui(locale, "dashboard.analysis.entry.description"))}</p>
         |<div class="bok-analysis-entry-flow">
         |  <div class="bok-analysis-entry-analysis">
         |    <div class="bok-analysis-entry-group-title">${_html_escape(_ui(locale, "dashboard.analysis.entry.mono.koto.title"))}</div>
         |    <div class="bok-analysis-entry-relation">
         |      <div class="bok-analysis-entry-column bok-analysis-entry-inputs">
         |        ${_analysis_entry_tile(Some(articlehref), _ui(locale, "dashboard.kpi.articles"), _ui(locale, "dashboard.analysis.entry.article.note"), _uif(locale, "dashboard.analysis.entry.count.articles", articlecount.toString), "article")}
         |        ${_analysis_entry_tile(Some(scenariohref), _ui(locale, "scenario.title"), _ui(locale, "dashboard.analysis.entry.scenario.note"), _uif(locale, "dashboard.analysis.entry.count.scenarios", scenariocount.toString), "scenario")}
         |      </div>
         |      <div class="bok-analysis-entry-arrow-column bok-analysis-entry-input-arrows" aria-hidden="true">
         |        <span class="bok-analysis-entry-arrow">➜</span>
         |        <span class="bok-analysis-entry-arrow">➜</span>
         |      </div>
         |      <div class="bok-analysis-entry-center">
         |        ${_analysis_entry_tile(Some(glossaryhref), _ui(locale, "glossary.title"), _ui(locale, "dashboard.analysis.entry.glossary.note"), _uif(locale, "dashboard.analysis.entry.count.terms", termcount.toString), "mono-koto-process")}
         |      </div>
         |    </div>
         |  </div>
         |  <div class="bok-analysis-entry-output-relation">
         |    <div class="bok-analysis-entry-arrow-column bok-analysis-entry-output-arrows" aria-hidden="true">
         |      <span class="bok-analysis-entry-arrow">➜</span>
         |      <span class="bok-analysis-entry-arrow">➜</span>
         |    </div>
         |    <div class="bok-analysis-entry-column bok-analysis-entry-outputs">
         |      ${_analysis_entry_tile(Some(rdfhref), _ui(locale, "dashboard.kpi.rdf.triples"), _ui(locale, "dashboard.analysis.entry.rdf.note"), _uif(locale, "dashboard.analysis.entry.count.rdf", rdfcount.toString), "rdf")}
         |      ${_analysis_entry_tile(Some(projecthref), _ui(locale, "project.title"), _ui(locale, "dashboard.analysis.entry.project.note"), _uif(locale, "dashboard.analysis.entry.count.projects", projectcount.toString), "project")}
         |    </div>
         |  </div>
         |</div>""".stripMargin,
      Vector("reader", "contributor", "project_manager")
    )

  private def _analysis_entry_tile(href: Option[String], title: String, note: String, count: String, kind: String): String = {
    val body =
      s"""  <span class="bok-analysis-entry-kicker">${_html_escape(count)}</span>
         |  <strong>${_html_escape(title)}</strong>
         |  <em>${_html_escape(note)}</em>""".stripMargin
    href match {
      case Some(value) =>
        s"""<a class="bok-analysis-entry-tile bok-analysis-entry-${_html_escape(kind)}" href="${_html_escape(value)}">
           |${body}
           |</a>""".stripMargin
      case None =>
        s"""<div class="bok-analysis-entry-tile bok-analysis-entry-${_html_escape(kind)} bok-analysis-entry-static">
           |${body}
           |</div>""".stripMargin
    }
  }

  private[bok] def _purpose_card_body(locale: String, purpose: BokPurpose): String =
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
    if (goals.isEmpty && subgoals.isEmpty)
      ""
    else
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
      s"""<div class="bok-purpose-tree"><div class="bok-purpose-tree-label"><span class="bok-purpose-node-label">G</span><strong>${_html_escape(_ui(locale, "dashboard.purpose.goals"))}</strong></div>${body}${moregoals}</div>"""
    }
  }

  private[bok] def _home_readiness_body(locale: String, config: BuildConfig, dashboard: Option[BokDashboard]): String =
    _definition_list(Vector(
      _ui(locale, "dashboard.readiness.strategy") -> config.strategy,
      _ui(locale, "dashboard.readiness.scope") -> config.siteOutputScopePolicy,
      _ui(locale, "dashboard.readiness.metadata") -> dashboard.map(_ => _ui(locale, "dashboard.status.available")).getOrElse(_ui(locale, "dashboard.status.missing")),
      _ui(locale, "dashboard.readiness.issue.count") -> _ui(locale, "dashboard.status.zero.known")
    ))

  private[bok] def _category_readiness_body(locale: String, dashboard: Option[DashboardCategory]): String =
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

  private[bok] def _quality_alerts_body(locale: String, metadataavailable: Boolean): String = {
    val items =
      if (metadataavailable)
        Vector(_ui(locale, "dashboard.quality.no.critical"), _ui(locale, "dashboard.quality.diagnostics.available"))
      else
        Vector(_ui(locale, "dashboard.quality.metadata.missing"), _ui(locale, "dashboard.quality.refresh"))
    items.take(5).map(x => s"""<li class="list-group-item"><span class="badge bok-badge-info">info</span>${_html_escape(x)}</li>""").
      mkString("""<ul class="list-group bok-alert-list">""", "", "</ul>")
  }

  private[bok] def _category_matrix_body(config: BuildConfig, locale: String, dashboard: BokDashboard, prefix: String = ""): String = {
    val articlecounts = _category_contents(config.sourcepath).map(x => x.slug -> x.articles.size).toMap
    val cards =
      if (dashboard.categories.isEmpty)
        s"""<p class="bok-card-muted">${_html_escape(_ui(locale, "dashboard.category.metadata.empty"))}</p>"""
      else
        dashboard.categories.sortBy(_.name).map { category =>
          val freshness = category.increments.buckets.lastOption.map(_.label).getOrElse("-")
          val rdfitem = _category_rdf_metric(locale, category, prefix)
          val articlecount = articlecounts.getOrElse(category.name, category.counts.articlecount)
          s"""<div class="bok-category-summary-card">
             |  <a class="bok-category-summary-title" href="${_html_escape(prefix)}${_html_escape(category.name)}/index.html">${_html_escape(category.title)}</a>
             |  <span class="bok-category-summary-freshness">${_html_escape(_ui(locale, "dashboard.readiness.freshness"))}: ${_html_escape(freshness)}</span>
             |  <span class="bok-category-summary-metrics">
             |    <span><b>${articlecount}</b>${_html_escape(_ui(locale, "dashboard.kpi.articles"))}</span>
             |    <span><b>${category.counts.glossarytermcount}</b>${_html_escape(_ui(locale, "dashboard.kpi.terms"))}</span>
             |    ${rdfitem}
             |  </span>
             |</div>""".stripMargin
        }.mkString("\n")
    val counts = dashboard.counts.copy(articlecount = _source_article_count(config))
    s"""<div class="bok-category-summary-grid">
       |  ${cards}
       |</div>
       |${_dashboard_distribution_chart(locale, counts, _ui(locale, "dashboard.chart.item.distribution"))}""".stripMargin
  }

  private[bok] def _category_source_matrix_body(locale: String, categories: Vector[CategoryContent], prefix: String): String = {
    val cards =
      if (categories.isEmpty)
        s"""<p class="bok-card-muted">${_html_escape(_ui(locale, "dashboard.category.metadata.empty"))}</p>"""
      else
        categories.sortBy(_.slug).map { category =>
          s"""<div class="bok-category-summary-card">
             |  <a class="bok-category-summary-title" href="${_html_escape(prefix)}${_html_escape(category.slug)}/index.html">${_html_escape(category.title)}</a>
             |  <span class="bok-category-summary-freshness">${_html_escape(_ui(locale, "dashboard.readiness.freshness"))}: -</span>
             |  <span class="bok-category-summary-metrics">
             |    <span><b>${category.articles.size}</b>${_html_escape(_ui(locale, "dashboard.kpi.articles"))}</span>
             |    <span><b>${category.terms.size}</b>${_html_escape(_ui(locale, "dashboard.kpi.terms"))}</span>
             |    <span class="bok-category-rdf-value"><b>-</b>${_html_escape(_ui(locale, "dashboard.kpi.rdf"))}</span>
             |  </span>
             |</div>""".stripMargin
        }.mkString("\n")
    s"""<div class="bok-category-summary-grid">
       |  ${cards}
       |</div>""".stripMargin
  }

  private[bok] def _article_adjusted_increments(increments: DashboardIncrements, overcount: Int): DashboardIncrements =
    if (overcount <= 0)
      increments
    else {
      var remaining = overcount
      val buckets = increments.buckets.map { bucket =>
        val removed = math.min(remaining, bucket.articlecount)
        remaining = remaining - removed
        if (removed == 0)
          bucket
        else
          bucket.copy(
            count = math.max(0, bucket.count - removed),
            articlecount = math.max(0, bucket.articlecount - removed)
          )
      }
      increments.copy(buckets = buckets)
    }

  private def _recent_activity_body(locale: String, increments: DashboardIncrements, fallbackitems: Vector[DashboardRecentItem] = Vector.empty, includecategory: Boolean = false): String =
    if (fallbackitems.nonEmpty)
      _recent_activity_fallback_body(locale, fallbackitems, includecategory)
    else
      _recent_activity_buckets(increments) match {
        case Vector() =>
          s"""<p class="bok-card-muted">${_html_escape(_ui(locale, "dashboard.activity.empty"))}</p>"""
        case buckets =>
          s"""${buckets.map { bucket =>
            s"""<li class="list-group-item"><time datetime="${_html_escape(bucket.startdate)}">${_html_escape(bucket.label)}</time><span class="bok-activity-kind">${_html_escape(_recent_activity_bucket_kind(locale, bucket))}</span><span>${_html_escape(_uif(locale, "dashboard.activity.count", bucket.count.toString))}</span></li>"""
          }.mkString("""<ul class="list-group bok-activity-list">""", "", "</ul>")}"""
      }

  private def _recent_activity_fallback_body(locale: String, items: Vector[DashboardRecentItem], includecategory: Boolean): String =
    s"""${items.map { item =>
      val date = Instant.ofEpochMilli(item.modifiedatmillis).atZone(ZoneOffset.UTC).toLocalDate.toString
      val category = if (includecategory) s"""<span class="bok-activity-category">${_html_escape(item.category.getOrElse("-"))}</span>""" else ""
      s"""<li class="list-group-item"><time datetime="${_html_escape(date)}">${_html_escape(date)}</time><span class="bok-activity-kind">${_html_escape(_ui(locale, item.kindkey))}</span>${category}<a href="${_html_escape(item.href)}">${_html_escape(item.title)}</a></li>"""
    }.mkString(s"""<ul class="list-group bok-activity-list${if (includecategory) " bok-activity-list-with-category" else ""}">""", "", "</ul>")}"""

  private def _recent_activity_bucket_kind(locale: String, bucket: DashboardBucket): String = {
    val kinds = Vector(
      (bucket.articlecount > 0) -> _ui(locale, "dashboard.activity.kind.article"),
      (bucket.glossarytermcount > 0) -> _ui(locale, "dashboard.activity.kind.term")
    ).collect { case (true, label) => label }
    if (kinds.isEmpty)
      _ui(locale, "dashboard.activity.kind.update")
    else
      kinds.mkString(" / ")
  }

  private def _recent_activity_buckets(increments: DashboardIncrements): Vector[DashboardBucket] = {
    val active = increments.buckets.filter(_.count > 0)
    val dated = active.flatMap(bucket => _dashboard_bucket_date(bucket).map(_ -> bucket))
    if (dated.nonEmpty) {
      val latestdate = dated.maxBy(_._1.toEpochDay)._1
      val cutoff = latestdate.minusMonths(1)
      dated.
        filter {
          case (date, _) => !date.isBefore(cutoff) && !date.isAfter(latestdate)
        }.
        sortBy(_._1.toEpochDay).
        map(_._2).
        takeRight(5).
        reverse
    } else {
      active.takeRight(5).reverse
    }
  }

  private def _dashboard_bucket_date(bucket: DashboardBucket): Option[LocalDate] =
    _parse_local_date(bucket.enddate).orElse(_parse_local_date(bucket.startdate))

  private def _parse_local_date(value: String): Option[LocalDate] =
    try {
      Some(LocalDate.parse(value))
    } catch {
      case NonFatal(_) => None
    }

  private[bok] def _home_recent_items(config: BuildConfig): Vector[DashboardRecentItem] = {
    val categories = _category_contents(config.sourcepath)
    val categorylabels = categories.map(x => x.slug -> x.title).toMap
    val articleitems = categories.flatMap { category =>
      val articles = category.articles.map { item =>
        DashboardRecentItem(s"${category.slug}/${item.href}", item.title, Some(category.title), "dashboard.activity.kind.article", item.modifiedatmillis)
      }
      articles
    }
    val termitems = _recent_term_items_from_home(config, categories)
    val scenarioitems = _scenario_index(config).map(_.scenarios.map { item =>
      DashboardRecentItem(item.hrefFromHome, item.title, item.category.map(x => categorylabels.getOrElse(x, x)), "dashboard.activity.kind.scenario", _source_modified_at_millis(config, item.sourcepath))
    }).getOrElse(Vector.empty)
    val bibliographyitems = _bibliography_index(config).map(_.entries.map { item =>
      DashboardRecentItem(item.publicpath, item.title, item.category.map(x => categorylabels.getOrElse(x, x)), "dashboard.activity.kind.bibliography", _source_modified_at_millis(config, item.sourcepath))
    }).getOrElse(Vector.empty)
    val items = (articleitems ++ termitems ++ scenarioitems ++ bibliographyitems).groupBy(_.href).values.map(_.maxBy(_.modifiedatmillis)).toVector
    val dated = items.flatMap(item => _modified_date(item.modifiedatmillis).map(_ -> item))
    if (dated.nonEmpty) {
      val latestdate = dated.maxBy(_._1.toEpochDay)._1
      val cutoff = latestdate.minusMonths(1)
      dated.
        filter {
          case (date, _) => !date.isBefore(cutoff) && !date.isAfter(latestdate)
        }.
        sortBy(_._1.toEpochDay).
        map(_._2).
        takeRight(5).
        reverse
    } else {
      Vector.empty
    }
  }

  private def _category_recent_items(config: BuildConfig, category: CategoryContent): Vector[DashboardRecentItem] = {
    val articles = category.articles.map { item =>
      DashboardRecentItem(item.href, item.title, Some(category.title), "dashboard.activity.kind.article", item.modifiedatmillis)
    }
    val terms = _recent_term_items_from_category(config, category)
    val scenarios = _scenario_index(config).map(_.scenarios.filter(_.category.contains(category.slug)).map { item =>
      DashboardRecentItem(item.hrefFromCategory, item.title, Some(category.title), "dashboard.activity.kind.scenario", _source_modified_at_millis(config, item.sourcepath))
    }).getOrElse(Vector.empty)
    val bibliographies = _bibliography_index(config).map(_.entries.filter(_.category.contains(category.slug)).map { item =>
      DashboardRecentItem("../" + item.publicpath, item.title, Some(category.title), "dashboard.activity.kind.bibliography", _source_modified_at_millis(config, item.sourcepath))
    }).getOrElse(Vector.empty)
    (articles ++ terms ++ scenarios ++ bibliographies).sortBy(-_.modifiedatmillis).take(5)
  }

  private def _recent_term_items_from_home(config: BuildConfig, categories: Vector[CategoryContent]): Vector[DashboardRecentItem] = {
    val categorylabels = categories.map(x => x.slug -> x.title).toMap
    val terms = _terms(config)
    if (terms.nonEmpty)
      terms.map { term =>
        DashboardRecentItem(
          term.termHubHrefFromHome,
          term.title,
          term.category.map(x => categorylabels.getOrElse(x, x)),
          "dashboard.activity.kind.term",
          _source_modified_at_millis(config, term.sourcepath)
        )
      }
    else
      Vector.empty
  }

  private def _recent_term_items_from_category(config: BuildConfig, category: CategoryContent): Vector[DashboardRecentItem] = {
    val terms = _terms(config).filter(_.category.contains(category.slug))
    if (terms.nonEmpty)
      terms.map { term =>
        DashboardRecentItem(
          term.termHubHrefFromCategory,
          term.title,
          Some(category.title),
          "dashboard.activity.kind.term",
          _source_modified_at_millis(config, term.sourcepath)
        )
      }
    else
      Vector.empty
  }

  private def _source_modified_at_millis(config: BuildConfig, sourcepath: String): Long = {
    val path = config.sourcepath.resolve(sourcepath)
    if (Files.isRegularFile(path))
      _modified_at_millis(path)
    else
      0L
  }

  private def _modified_date(millis: Long): Option[LocalDate] =
    if (millis <= 0L)
      None
    else
      Some(Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate)

  private[bok] def _next_actions_body(locale: String, config: BuildConfig): String = {
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

  private[bok] def _page_map_body(items: Vector[CategoryPageItem], empty: String): String =
    if (items.isEmpty)
      s"""<p class="bok-card-muted">${_html_escape(empty)}</p>"""
    else {
      val shown = items.take(5).map { item =>
        s"""<li class="list-group-item"><a href="${_html_escape(item.href)}">${_html_escape(item.title)}</a><span>${_html_escape(item.brief)}</span></li>"""
      }.mkString
      val more = if (items.size > 5) s"""<li class="list-group-item bok-more">+${items.size - 5} more</li>""" else ""
      s"""<ul class="list-group bok-map-list">${shown}${more}</ul>"""
    }

  private[bok] def _related_knowledge_body(locale: String, config: BuildConfig, category: CategoryContent, rdf: Option[DashboardRdfSummary]): String = {
    val rdfitem =
      rdf.filter(_.triplecount > 0).
        map(_ => s"""  <li class="list-group-item"><a href="../rdf/index.html?category=${_html_escape(_url_query_escape(category.slug))}">${_html_escape(_ui(locale, "rdf.graph.title"))}</a></li>""").
        getOrElse("")
    val scenarioitem =
      _scenario_index(config).map(_.scenarios.count(_.category.contains(category.slug))).filter(_ > 0).
        map(count => s"""  <li class="list-group-item"><a href="../scenarios/index.html?category=${_html_escape(_url_query_escape(category.slug))}">${_html_escape(_uif(locale, "scenario.category.link", count.toString))}</a></li>""").
        getOrElse("")
    val bibliographyitem =
      _bibliography_index(config).map(_.entries.count(_.category.contains(category.slug))).filter(_ > 0).
        map(count => s"""  <li class="list-group-item"><a href="../bibliography/index.html?category=${_html_escape(_url_query_escape(category.slug))}">${_html_escape(_uif(locale, "bibliography.category.link", count.toString))}</a></li>""").
        getOrElse("")
    val projectitem = {
      val count = _project_package_dirs(config.sourcepath).count { path =>
        val projects = config.sourcepath.resolve("projects").toAbsolutePath.normalize()
        val relative = projects.relativize(path.toAbsolutePath.normalize())
        relative.getNameCount >= 1 && relative.getName(0).toString == category.slug
      }
      if (count > 0)
        s"""  <li class="list-group-item"><a href="../projects/index.html?category=${_html_escape(_url_query_escape(category.slug))}">${_html_escape(_uif(locale, "project.category.link", count.toString))}</a></li>"""
      else
        ""
    }
    s"""<ul class="list-group bok-related-list">
       |  <li class="list-group-item"><a href="../glossary/index.html">${_html_escape(_ui(locale, "glossary.title"))}</a></li>
       |  <li class="list-group-item"><a href="../glossary/${_html_escape(category.slug)}/index.html">${_html_escape(_uif(locale, "dashboard.related.category.terms", category.title))}</a></li>
       |${rdfitem}
       |${scenarioitem}
       |${projectitem}
       |${bibliographyitem}
       |  <li class="list-group-item"><a href="${_html_escape(_history_href(config, "../"))}">${_html_escape(_ui(locale, "history.title"))}</a></li>
       |  <li class="list-group-item"><a href="../manual/index.html">${_html_escape(_ui(locale, "manual.title"))}</a></li>
       |</ul>""".stripMargin
  }

  private def _category_rdf_metric(locale: String, category: DashboardCategory, prefix: String = ""): String = {
    val value = category.rdf.map(_.triplecount.toString).getOrElse("-")
    category.rdf.filter(_.triplecount > 0).
      map(_ => s"""<a class="bok-category-rdf-link" href="${_html_escape(prefix)}rdf/index.html?category=${_html_escape(_url_query_escape(category.name))}"><b>${_html_escape(value)}</b>${_html_escape(_ui(locale, "dashboard.kpi.rdf"))}</a>""").
      getOrElse(s"""<span class="bok-category-rdf-value"><b>${_html_escape(value)}</b>${_html_escape(_ui(locale, "dashboard.kpi.rdf"))}</span>""")
  }

  private[bok] def _category_rdf_kpi_card(locale: String, category: CategoryContent, rdf: DashboardRdfSummary): String =
    if (rdf.triplecount > 0)
      _kpi_card_link("col-6 col-md-3", _ui(locale, "dashboard.kpi.rdf"), rdf.triplecount.toString, _ui(locale, "dashboard.kpi.rdf.note"), s"../rdf/index.html?category=${_url_query_escape(category.slug)}")
    else
      _kpi_card(locale, "col-6 col-md-3", _ui(locale, "dashboard.kpi.rdf"), rdf.triplecount.toString, _ui(locale, "dashboard.kpi.rdf.note"))

  private def _dashboard_cards(counts: DashboardCounts, includecategories: Boolean): String = {
    val categorycard =
      if (includecategories)
        s"""  <div class="bok-metric-card">
           |    <div class="bok-metric-label">Categories</div>
           |    <div class="bok-metric-value">${counts.categorycount}</div>
           |    <div class="bok-metric-note">SmartDox categories</div>
           |  </div>
           |""".stripMargin
      else
        ""
    s"""<div class="bok-dashboard-grid">
       |${categorycard}  <div class="bok-metric-card">
       |    <div class="bok-metric-label">Articles</div>
       |    <div class="bok-metric-value">${counts.articlecount}</div>
       |    <div class="bok-metric-note">Article / Blog pages</div>
       |  </div>
       |  <div class="bok-metric-card">
       |    <div class="bok-metric-label">Terms</div>
       |    <div class="bok-metric-value">${counts.glossarytermcount}</div>
       |    <div class="bok-metric-note">Site glossary terms</div>
       |  </div>
       |  <div class="bok-metric-card">
       |    <div class="bok-metric-label">Total Items</div>
       |    <div class="bok-metric-value">${counts.totalitemcount}</div>
       |    <div class="bok-metric-note">Articles + terms</div>
       |  </div>
       |</div>""".stripMargin
  }

  private def _dashboard_distribution_chart(locale: String, counts: DashboardCounts, label: String): String = {
    val articlecount = counts.articlecount
    val termcount = counts.glossarytermcount
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
       |    <div class="bok-metric-value">${rdf.resourcecount}</div>
       |    <div class="bok-metric-note">Site RDF resources</div>
       |  </div>
       |  <div class="bok-metric-card">
       |    <div class="bok-metric-label">RDF Triples</div>
       |    <div class="bok-metric-value">${rdf.triplecount}</div>
       |    <div class="bok-metric-note">Generated site graph triples</div>
       |  </div>
       |  <div class="bok-metric-card">
       |    <div class="bok-metric-label">RDF Subjects</div>
       |    <div class="bok-metric-value">${rdf.subjectcount}</div>
       |    <div class="bok-metric-note">Distinct graph subjects</div>
       |  </div>
       |  <div class="bok-metric-card">
       |    <div class="bok-metric-label">RDF Predicates</div>
       |    <div class="bok-metric-value">${rdf.predicatecount}</div>
       |    <div class="bok-metric-note">Distinct graph predicates</div>
       |  </div>
       |</div>""".stripMargin

  private[bok] def _dashboard_increment_chart(locale: String, increments: DashboardIncrements, label: String): String =
    if (increments.buckets.isEmpty)
      s"""<p>${_html_escape(_ui(locale, "dashboard.increment.empty"))}</p>"""
    else if (!increments.buckets.forall(_.hasbreakdown))
      _dashboard_increment_total_chart(locale, increments, label)
    else {
      val cumulativearticles = increments.buckets.scanLeft(0)(_ + _.articlecount).tail
      val cumulativeterms = increments.buckets.scanLeft(0)(_ + _.glossarytermcount).tail
      val points = increments.buckets.zip(cumulativearticles.zip(cumulativeterms))
      val max = math.max(1, (cumulativearticles ++ cumulativeterms).max)
      val pointcount = increments.buckets.length
      def _x_(index: Int): Double =
        if (pointcount <= 1) 50.0 else 8.0 + (84.0 * index.toDouble / (pointcount - 1).toDouble)
      def _y_(value: Int): Double =
        88.0 - (76.0 * value.toDouble / max.toDouble)
      def _coordinates_(values: Vector[Int]): String =
        values.zipWithIndex.map {
          case (value, index) => f"${_x_(index)}%.2f,${_y_(value)}%.2f"
        }.mkString(" ")
      val articlecoordinates = _coordinates_(cumulativearticles)
      val termcoordinates = _coordinates_(cumulativeterms)
      val articlemarkers = increments.buckets.zip(cumulativearticles).zipWithIndex.map {
        case ((bucket, value), index) =>
          val cx = _x_(index)
          val cy = _y_(value)
          val title = _uif(locale, "dashboard.chart.title.cumulative.articles", bucket.label, value, bucket.articlecount)
          f"""      <circle class="bok-cumulative-point-articles" cx="${cx}%.2f" cy="${cy}%.2f" r="2.8"><title>${_html_escape(title)}</title></circle>"""
      }.mkString("\n")
      val termmarkers = increments.buckets.zip(cumulativeterms).zipWithIndex.map {
        case ((bucket, value), index) =>
          val cx = _x_(index)
          val cy = _y_(value)
          val title = _uif(locale, "dashboard.chart.title.cumulative.terms", bucket.label, value, bucket.glossarytermcount)
          f"""      <circle class="bok-cumulative-point-terms" cx="${cx}%.2f" cy="${cy}%.2f" r="2.8"><title>${_html_escape(title)}</title></circle>"""
      }.mkString("\n")
      val axis = points.map {
        case (bucket, (articlevalue, termvalue)) =>
          s"""    <span><time datetime="${_html_escape(bucket.startdate)}">${_html_escape(bucket.label)}</time><em>${_html_escape(_uif(locale, "dashboard.chart.axis.article.term", articlevalue, termvalue))}</em></span>"""
      }.mkString("\n")
      val range = s"${increments.buckets.head.startdate} - ${increments.buckets.last.enddate}"
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
    def _x_(index: Int): Double =
      if (pointcount <= 1) 50.0 else 8.0 + (84.0 * index.toDouble / (pointcount - 1).toDouble)
    def _y_(value: Int): Double =
      88.0 - (76.0 * value.toDouble / max.toDouble)
    val coordinates = cumulativetotals.zipWithIndex.map {
      case (value, index) => f"${_x_(index)}%.2f,${_y_(value)}%.2f"
    }.mkString(" ")
    val markers = increments.buckets.zip(cumulativetotals).zipWithIndex.map {
      case ((bucket, value), index) =>
        val cx = _x_(index)
        val cy = _y_(value)
        val title = _uif(locale, "dashboard.chart.title.cumulative.total", bucket.label, value, bucket.count)
        f"""      <circle class="bok-cumulative-point-total" cx="${cx}%.2f" cy="${cy}%.2f" r="2.8"><title>${_html_escape(title)}</title></circle>"""
    }.mkString("\n")
    val axis = increments.buckets.zip(cumulativetotals).map {
      case (bucket, value) =>
        s"""    <span><time datetime="${_html_escape(bucket.startdate)}">${_html_escape(bucket.label)}</time><em>${value}</em></span>"""
    }.mkString("\n")
    val range = s"${increments.buckets.head.startdate} - ${increments.buckets.last.enddate}"
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

  private[bok] def _document_fragment_index(config: BuildConfig): Option[DocumentFragmentIndex] = {
    val path = config.doxsitePath.resolve("metadata/documents/fragments.json")
    if (!Files.isRegularFile(path))
      None
    else
      parser.parse(Files.readString(path, StandardCharsets.UTF_8)).toOption.flatMap(_.as[DocumentFragmentIndex].toOption)
  }

  private[bok] def _category_term_page_items(config: BuildConfig, category: String): Vector[CategoryPageItem] =
    _terms(config).filter(_.category.contains(category)).map { term =>
      CategoryPageItem(
        term.termHubHrefFromCategory,
        term.title,
        term.summary.getOrElse(""),
        0L,
        term.reading
      )
    }

  private[bok] def _dashboard(config: BuildConfig): Option[BokDashboard] = {
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

  private[bok] def _history_href(config: BuildConfig, prefix: String): String =
    _latest_history_year_page(config.websitePath.resolve("history")) match {
      case Some(file) => s"${prefix}history/${file}"
      case None => s"${prefix}history/index.html"
    }

  private[bok] def _latest_history_year_page(dir: Path): Option[String] =
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
    val categories = _regular_category_summaries(config.sourcepath)
    if (categories.isEmpty)
      s"<p>${_html_escape(_ui(locale, "home.categories.empty"))}</p>"
    else
      categories.map { category =>
        val htmlclass = if (category.slug == "glossary") """ class="glossary"""" else ""
        s"""<li><a${htmlclass} href="${_html_escape(category.slug)}/index.html">${_html_escape(category.title)}</a>: ${_html_escape(category.description)}</li>"""
      }.mkString("<ul>\n", "\n", "\n</ul>")
  }

}
