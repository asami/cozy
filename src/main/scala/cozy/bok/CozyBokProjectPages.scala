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

private[cozy] trait CozyBokProjectPages {
  self: CozyBokImplementation.type =>
  private[bok] def _write_project_pages(
    config: BuildConfig,
    target: Path,
    locale: String,
    categories: Vector[CategoryContent]
  ): Unit = {
    val projects = _resolved_project_packages(config)
    _write_project_index_page(config, target, locale, categories, projects)
    projects.foreach { project =>
      val articlebody = _source_narrative_html(project.article, locale)
      val page = target.resolve(project.publicationpath).resolve("index.html")
      val cmlbody = _project_model_terms_html(config, locale, project, page, target)
      val surfacebody = _project_component_surface_html(locale, project)
      val dashboardbody = _project_detail_dashboard(config, locale, project)
      val siebody = _project_sie_handoff_html(config, locale, project, page, target)
      val repositorybody = _project_repository_car_html(config, locale, project, page, target)
      val narrativebody = _project_narrative_html(locale, articlebody)
      val pagebody =
        s"""${dashboardbody}
           |${siebody}
           |${surfacebody}
           |${cmlbody}
           |${repositorybody}
           |${narrativebody}""".stripMargin
      _write_text(
        page,
        _project_html_page(
          config,
          categories,
          locale,
          page,
          target,
          project.title,
          project.summary.getOrElse("CAR project."),
          project.tags,
          Some(_project_category(config, project)),
          pagebody
        )
      )
    }
  }

  private def _project_sie_handoff_html(
    config: BuildConfig,
    locale: String,
    project: CozyBokProjectPublisher.ResolvedBokProject,
    page: Path,
    target: Path
  ): String =
    project.sie.map { sie =>
      val integration = _sie_index(config).forProject(project)
      val componentrow = sie.component.map { component =>
        s"""  <dt>${_html_escape(_ui(locale, "project.label.sie.component"))}</dt><dd><code>${_html_escape(component)}</code></dd>
           |""".stripMargin
      }.getOrElse("")
      val subsystemrow = sie.subsystem.map { subsystem =>
        s"""  <dt>${_html_escape(_ui(locale, "project.label.sie.subsystem"))}</dt><dd><code>${_html_escape(subsystem)}</code></dd>
           |""".stripMargin
      }.getOrElse("")
      val artifacts = _project_sie_artifacts_html(locale, sie, page, target)
      val relations = _project_sie_relations_html(config, locale, project, page, target)
      val information = integration.toVector.flatMap(_.informationinstances)
      val informationbody = _sie_information_section(config, page, locale, information, "bok-project-sie-information")
      val diagnosticsbody = integration.map(_project_sie_diagnostics_html(locale, _)).getOrElse("")
      s"""<section class="bok-project-section bok-project-sie" id="project-sie">
         |  <div class="bok-project-section-head">
         |    <h2>${_html_escape(_ui(locale, "project.section.sie"))}</h2>
         |    <p>${_html_escape(_ui(locale, "project.section.sie.description"))}</p>
         |  </div>
         |  <dl class="bok-project-detail-dl">
         |    <dt>${_html_escape(_ui(locale, "project.label.sie.projection"))}</dt><dd><code>${_html_escape(sie.projection)}</code></dd>
         |${componentrow}${subsystemrow}    <dt>${_html_escape(_ui(locale, "project.label.sie.handoff"))}</dt><dd><a href="${_html_escape(sie.handoffbase)}">${_html_escape(sie.handoffbase)}</a></dd>
         |    <dt>${_html_escape(_ui(locale, "project.label.sie.manifest"))}</dt><dd><a href="${_html_escape(sie.manifest)}">${_html_escape(sie.manifest)}</a></dd>
         |  </dl>
         |  ${artifacts}
         |  ${informationbody}
         |  ${diagnosticsbody}
         |  ${relations}
         |</section>""".stripMargin
    }.getOrElse("")

  private def _project_sie_diagnostics_html(
    locale: String,
    projection: CozyBokSieHandoff.Projection
  ): String =
    if (projection.diagnostics.isEmpty)
      ""
    else {
      val items = projection.diagnostics.map { diagnostic =>
        s"""<li class="bok-sie-diagnostic bok-sie-diagnostic-${_html_escape(diagnostic.severity)}"><code>${_html_escape(diagnostic.code)}</code> ${_html_escape(diagnostic.message)}</li>"""
      }.mkString
      s"""<section class="bok-project-sie-diagnostics" id="project-sie-diagnostics">
         |  <h3>${_html_escape(_ui(locale, "project.section.sie.diagnostics"))}</h3>
         |  <ul>${items}</ul>
         |</section>""".stripMargin
    }

  private def _project_sie_artifacts_html(
    locale: String,
    sie: CozyBokProjectPublisher.ProjectSieInfo,
    page: Path,
    target: Path
  ): String =
    if (sie.artifacts.isEmpty)
      ""
    else {
      val rows = sie.artifacts.sortBy(x => (x.kind, x.artifactId)).flatMap { artifact =>
        artifact.catalog.versions.map { version =>
          val versionpage = target.resolve("repository").resolve(artifact.kind).resolve(artifact.artifactId).resolve(s"${version.version}.html")
          val versionhref = _relative_href(page, versionpage)
          val recommended = if (artifact.catalog.recommended.contains(version.version)) _ui(locale, "project.label.sie.recommended") else ""
          val lateststable = if (artifact.catalog.latestStable.contains(version.version)) _ui(locale, "project.label.sie.latest.stable") else ""
          val selectors = Vector(recommended, lateststable).filter(_.nonEmpty).mkString(", ")
          s"""<tr>
             |  <td>${_html_escape(artifact.kind.toUpperCase(Locale.ROOT))}</td>
             |  <td><code>${_html_escape(artifact.artifactId)}</code></td>
             |  <td><a href="${_html_escape(versionhref)}">${_html_escape(version.version)}</a></td>
             |  <td>${_html_escape(version.channel.getOrElse("-"))}</td>
             |  <td>${_html_escape(if (selectors.isEmpty) "-" else selectors)}</td>
             |</tr>""".stripMargin
        }
      }.mkString("\n")
      s"""<section class="bok-project-sie-artifacts" id="project-sie-artifacts">
         |  <h3>${_html_escape(_ui(locale, "project.section.sie.artifacts"))}</h3>
         |  <div class="bok-project-table-wrap">
         |    <table class="table table-sm bok-project-cml-table">
         |      <thead><tr><th>${_html_escape(_ui(locale, "project.label.sie.artifact.kind"))}</th><th>${_html_escape(_ui(locale, "project.label.sie.artifact"))}</th><th>${_html_escape(_repository_car_version_label(locale))}</th><th>${_html_escape(_repository_car_channel_label(locale))}</th><th>${_html_escape(_ui(locale, "project.label.sie.selectors"))}</th></tr></thead>
         |      <tbody>
         |${rows}
         |      </tbody>
         |    </table>
         |  </div>
         |</section>""".stripMargin
    }

  private def _project_sie_relations_html(
    config: BuildConfig,
    locale: String,
    project: CozyBokProjectPublisher.ResolvedBokProject,
    page: Path,
    target: Path
  ): String = {
    val terms = _terms(config)
    val directterms = terms.filter(term => project.terms.exists(_term_reference_matches(_, term)))
    val cmlrows = project.cml.toVector.flatMap(_project_cml_term_rows(locale, _, terms))
    val cmlterms = cmlrows.flatMap(_.terms)
    val relatedterms = (directterms ++ cmlterms).groupBy(_.id).values.map(_.head).toVector.sortBy(_.title)
    val scenarios = _scenario_index(config).toVector.flatMap(_.scenarios).
      filter(scenario => relatedterms.exists(scenario.isRelatedTo)).sortBy(_.title)
    val termitems = relatedterms.map { term =>
      val href = _relative_href(page, target.resolve(term.publicpath))
      s"""<li><a href="${_html_escape(href)}">${_html_escape(term.title)}</a></li>"""
    }.mkString
    val scenarioitems = scenarios.map { scenario =>
      val href = _relative_href(page, target.resolve(scenario.publicpath))
      s"""<li><a href="${_html_escape(href)}">${_html_escape(scenario.title)}</a></li>"""
    }.mkString
    val rdfitems = relatedterms.filter(_.rdfrefs.nonEmpty).map { term =>
      val href = s"${_relative_href(page, target.resolve("rdf/index.html"))}?term=${_url_query_escape(term.id)}"
      s"""<li><a href="${_html_escape(href)}">${_html_escape(term.title)}</a></li>"""
    }.mkString
    val cmlitem =
      if (cmlrows.nonEmpty)
        s"""<li><a href="#project-model-terms">${_html_escape(_ui(locale, "project.section.model.terms"))}</a></li>"""
      else
        ""
    val tagchips = _tag_chips(config, page, project.tags, Some(_project_category(config, project)), locale)
    val groups = Vector(
      _project_sie_relation_group(_ui(locale, "project.section.model.terms"), cmlitem),
      _project_sie_relation_group(_ui(locale, "glossary.title"), termitems),
      _project_sie_relation_group(_ui(locale, "term.related.scenarios"), scenarioitems),
      if (tagchips.isEmpty) "" else s"""<div class="bok-project-sie-relation"><h4>${_html_escape(_ui(locale, "tag.title"))}</h4>${tagchips}</div>""",
      _project_sie_relation_group(_ui(locale, "term.rdf.resources"), rdfitems)
    ).filter(_.nonEmpty).mkString
    if (groups.isEmpty)
      ""
    else
      s"""<div class="bok-project-sie-relations">
         |    <h3>${_html_escape(_ui(locale, "project.section.sie.relations"))}</h3>
         |    <p>${_html_escape(_ui(locale, "project.section.sie.relations.description"))}</p>
         |    <div class="bok-project-sie-relation-grid">${groups}</div>
         |  </div>""".stripMargin
  }

  private def _project_sie_relation_group(title: String, items: String): String =
    if (items.isEmpty)
      ""
    else
      s"""<div class="bok-project-sie-relation"><h4>${_html_escape(title)}</h4><ul>${items}</ul></div>"""

  private[bok] def _sie_information_for_term(
    index: CozyBokSieHandoff.Index,
    term: TermEntry
  ): Vector[CozyBokSieHandoff.InformationInstance] =
    index.informationinstances.filter(instance => instance.termrefs.exists(_term_reference_matches(_, term))).
      sortBy(x => (x.label, x.id, x.projection))

  private[bok] def _sie_information_section(
    config: BuildConfig,
    page: Path,
    locale: String,
    instances: Vector[CozyBokSieHandoff.InformationInstance],
    cssclass: String
  ): String =
    if (instances.isEmpty)
      ""
    else {
      val target = _locale_website_root(config, locale)
      val items = instances.sortBy(x => (x.label, x.id, x.projection)).map { instance =>
        val rdfhref = s"${_relative_href(page, target.resolve("rdf/node.html"))}?id=${_url_query_escape(instance.graphNodeId)}"
        val projecthref = _relative_href(page, target.resolve(instance.projectpath))
        s"""<li><a href="${_html_escape(rdfhref)}">${_html_escape(instance.label)}</a> <code>${_html_escape(instance.schema)}</code> <span>${_html_escape(instance.projection)}</span> <a href="${_html_escape(projecthref)}">${_html_escape(_ui(locale, "project.title"))}</a></li>"""
      }.mkString
      s"""<section class="${_html_escape(cssclass)}" id="${_html_escape(cssclass)}">
         |  <h3>${_html_escape(_ui(locale, "project.section.sie.information"))}</h3>
         |  <ul>${items}</ul>
         |</section>""".stripMargin
    }

  private[bok] def _term_sie_information_card(
    config: BuildConfig,
    page: Path,
    term: TermEntry,
    locale: String
  ): String = {
    val instances = _sie_information_for_term(_sie_index(config), term)
    if (instances.isEmpty)
      ""
    else
      _dashboard_card(
        "col-12 col-xl-6",
        "bok-card-related bok-card-sie-information",
        _ui(locale, "project.section.sie.information"),
        _sie_information_section(config, page, locale, instances, "bok-term-sie-information")
      )
  }

  private[bok] def _resolved_project_packages(config: BuildConfig): Vector[CozyBokProjectPublisher.ResolvedBokProject] = {
    val bokconfig = _load_config(config.project)
    _project_package_dirs(config.sourcepath).map { packagedir =>
      val publishconfig = CozyBokProjectPublisher.PublishProjectConfig(
        packagedir,
        config.publication.publicationPath(config.project),
        config.publication.artifactBasePath(config.project),
        None,
        force = false,
        config.project,
        bokconfig,
        Some(config.publication.repositoryPath(config.project))
      )
      CozyBokProjectPublisher.resolve(publishconfig)
    }
  }

  private def _write_project_index_page(
    config: BuildConfig,
    target: Path,
    locale: String,
    categories: Vector[CategoryContent],
    projects: Vector[CozyBokProjectPublisher.ResolvedBokProject]
  ): Unit = {
    val page = target.resolve("projects/index.html")
    _write_text(
      page,
      _project_index_page(config, categories, locale, page, target, projects)
    )
  }

  private def _project_index_page(
    config: BuildConfig,
    categories: Vector[CategoryContent],
    locale: String,
    page: Path,
    target: Path,
    projects: Vector[CozyBokProjectPublisher.ResolvedBokProject]
  ): String =
    s"""<!doctype html>
       |<html lang="${_html_escape(locale)}">
       |<head>
       |  <meta charset="utf-8">
       |  <meta name="viewport" content="width=device-width, initial-scale=1">
       |  <title>${_html_escape(_ui(locale, "project.title"))} - ${_html_escape(config.siteTitle)}</title>
       |${_site_css_links(config, page)}
       |</head>
       |<body class="article ${_html_escape(_support_dashboard_theme_class)}">
       |${_category_header(config, categories, locale)}
       |<div class="body body-dashboard bok-project-body">
       |  <main class="article bok-project-main">
       |    <div class="content">
       |      <article class="doc bok-project-doc">
       |        <section class="bok-dashboard-shell bok-project-dashboard" id="dashboard">
       |          ${_dashboard_hero(
                    _ui(locale, "project.title"),
                    _ui(locale, "project.description"),
                    Vector(
                      _ui(locale, "project.metric.total") -> projects.size.toString,
                      _ui(locale, "project.metric.categories") -> projects.map(_project_category(config, _)).distinct.size.toString
                    )
                  )}
       |          ${_project_dashboard_body(config, locale, page, target, projects)}
       |        </section>
       |      </article>
       |    </div>
       |  </main>
       |</div>
       |</body>
       |</html>
       |""".stripMargin

  private def _project_dashboard_body(
    config: BuildConfig,
    locale: String,
    page: Path,
    target: Path,
    projects: Vector[CozyBokProjectPublisher.ResolvedBokProject]
  ): String =
    if (projects.isEmpty)
      s"""<div class="bok-dashboard container-fluid bok-dashboard-command-center">
         |  <div class="row g-3">
         |    ${_dashboard_card("col-12", "bok-card-map bok-card-project-map", _ui(locale, "project.title"), s"""<p class="bok-card-muted">${_html_escape(_ui(locale, "project.empty"))}</p>""", Vector("reader", "contributor", "project_manager"))}
         |  </div>
         |</div>""".stripMargin
    else {
      val categories = projects.groupBy(x => _project_category(config, x)).toVector.sortBy(_._1)
      val artifactrecords = projects.flatMap(project => _project_artifact_records(config, project))
      val plannedartifactcount = artifactrecords.size
      val currentartifactcount = artifactrecords.count(_.exists)
      val missingartifactcount = plannedartifactcount - currentartifactcount
      val plannedartifacttypes = _project_artifact_type_counts(artifactrecords)
      val modecounts = _project_mode_counts(projects)
      val cmlelementcount = projects.flatMap(_.cml.toVector).map(_.modelElements.size).sum
      val cmlelementkinds = _project_cml_element_kind_counts(projects)
      val metrics = categories.map {
        case (category, xs) =>
          s"""<div class="bok-metric-card"><div class="bok-metric-label">${_html_escape(category)}</div><div class="bok-metric-value">${xs.size}</div><div class="bok-metric-note">${_html_escape(_ui(locale, "project.metric.note"))}</div></div>"""
      }.mkString("""<div class="bok-dashboard-grid bok-project-metrics">""", "", "</div>")
      val health = (Vector(
        s"""<li><span>${_html_escape(_ui(locale, "project.label.distribution.artifacts"))}${_project_artifact_availability_counts_html(locale, currentartifactcount, missingartifactcount)}</span><b>${plannedartifactcount}</b></li>""",
        s"""<li><span>${_html_escape(_ui(locale, "project.label.artifact.kind.counts"))}${_project_artifact_type_counts_html(locale, plannedartifacttypes)}</span><b>${plannedartifactcount}</b></li>"""
      ) :+ s"""<li><span>${_html_escape(_ui(locale, "project.label.placement"))}${_project_mode_counts_html(locale, modecounts)}</span><b>${projects.size}</b></li>""" :+ s"""<li><span>${_html_escape(_ui(locale, "project.label.cml.terms"))}${_project_cml_element_kind_counts_html(locale, cmlelementkinds)}</span><b>${cmlelementcount}</b></li>""").mkString("""<ul class="bok-project-health-list">""", "", "</ul>")
      val items = projects.take(30).map { project =>
        val category = _project_category(config, project)
        val summary = project.summary.map(x => s"""<p>${_html_escape(x)}</p>""").getOrElse("")
        val href = _relative_href(page, target.resolve(project.publicationpath).resolve("index.html"))
        val artifactrecords = _project_artifact_records(config, project)
        val plannedartifactcount = artifactrecords.size
        val currentartifactcount = artifactrecords.count(_.exists)
        val missingartifactcount = plannedartifactcount - currentartifactcount
        val plannedartifacttypes = _project_artifact_type_counts(artifactrecords)
        val artifactstatus = _project_artifact_status(config, project)
        val cmlcount = project.cml.map(_.modelElements.size).getOrElse(0)
        val cmlkinds = _project_cml_element_kind_counts(Vector(project))
        s"""<article class="bok-project-tile" data-project-category="${_html_escape(category)}">
           |  <div class="bok-project-tile-head">
           |    <span>${_html_escape(project.descriptor.project.projecttype)}</span>
           |    <code>${_html_escape(project.module)}</code>
           |  </div>
           |  <h3><a href="${_html_escape(href)}">${_html_escape(project.title)}</a></h3>
           |  ${summary}
           |  <div class="bok-project-meta"><span>${_html_escape(project.version)}</span><span>${_html_escape(_ui(locale, "project.label.placement"))}: ${_html_escape(_project_mode_label(locale, project.projectmode))}</span><span>${_html_escape(_ui(locale, "project.label.distribution.artifacts"))} ${plannedartifactcount}</span><span class="bok-project-status bok-project-status-${_html_escape(_tag_segment(artifactstatus))}">${_html_escape(_ui(locale, "project.label.distributed.artifacts"))} ${currentartifactcount}</span><span>${_html_escape(_ui(locale, "project.label.undistributed.artifacts"))} ${missingartifactcount}</span><span>${_html_escape(_ui(locale, "project.label.cml.terms"))} ${cmlcount}</span></div>
           |  <div class="bok-project-artifact-kind-line"><span>${_html_escape(_ui(locale, "project.label.artifact.kind.counts"))}</span>${_project_artifact_type_counts_html(locale, plannedartifacttypes)}</div>
           |  ${_project_cml_element_kind_counts_inline_html(locale, cmlkinds)}
           |</article>""".stripMargin
      }.mkString("""<div class="bok-project-grid">""", "", "</div>")
      s"""<div class="bok-dashboard container-fluid bok-dashboard-command-center">
         |  <div class="row g-3">
         |    ${_dashboard_card("col-12 col-xl-6", "bok-card-kpi bok-card-project-summary", _ui(locale, "project.metric.summary"), metrics, Vector("reader", "contributor", "project_manager"))}
         |    ${_dashboard_card("col-12 col-xl-6", "bok-card-map bok-card-project-health", _ui(locale, "project.card.health"), health, Vector("reader", "contributor", "project_manager"))}
         |    ${_dashboard_card("col-12", "bok-card-map bok-card-project-map", _ui(locale, "project.title"), items, Vector("reader", "contributor", "project_manager"))}
         |  </div>
         |</div>
         |<script>
         |(() => {
         |  const category = new URLSearchParams(window.location.search).get('category');
         |  if (!category) return;
         |  document.querySelectorAll('[data-project-category]').forEach((item) => {
         |    item.hidden = item.dataset.projectCategory !== category;
         |  });
         |})();
         |</script>""".stripMargin
    }

  private[bok] def _project_relative_path(project: Path, path: Path): String =
    try {
      project.toAbsolutePath.normalize.relativize(path.toAbsolutePath.normalize).toString.replace(java.io.File.separatorChar, '/')
    } catch {
      case NonFatal(_) => path.toString
    }

  private[bok] def _project_category(config: BuildConfig, project: CozyBokProjectPublisher.ResolvedBokProject): String = {
    val projects = config.sourcepath.resolve("projects").toAbsolutePath.normalize()
    val relative = projects.relativize(project.packagedir.toAbsolutePath.normalize())
    if (relative.getNameCount >= 1) relative.getName(0).toString else "project"
  }

  private final case class ProjectArtifactRecord(artifacttype: String, warehousepath: String, path: Path, exists: Boolean)

  private def _project_artifact_path(config: BuildConfig, project: CozyBokProjectPublisher.ResolvedBokProject): Path =
    _project_artifact_path(config, project.warehousePath)

  private def _project_artifact_path(config: BuildConfig, warehousepath: String): Path = {
    val repository = config.publication.repositoryPath(config.project)
    val warehouse = config.publication.artifactBasePath(config.project)
    warehousepath match {
      case "repository" =>
        repository.toAbsolutePath.normalize()
      case path if path.startsWith("repository/") =>
        repository.resolve(path.stripPrefix("repository/")).toAbsolutePath.normalize()
      case path =>
        warehouse.resolve(path).toAbsolutePath.normalize()
    }
  }

  private def _project_artifact_status(config: BuildConfig, project: CozyBokProjectPublisher.ResolvedBokProject): String =
    if (project.reference.isSourceOnly) "source-only"
    else if (Files.isRegularFile(_project_artifact_path(config, project))) "published"
    else "missing"

  private def _project_artifact_records(config: BuildConfig, project: CozyBokProjectPublisher.ResolvedBokProject): Vector[ProjectArtifactRecord] = {
    val paths = project.catalog.map { info =>
      info.catalog.versions.map { version =>
        version.file.getOrElse(s"repository/car/${project.module}/${version.version}/${project.module}-${version.version}.car")
      }
    }.getOrElse(Vector(project.warehousePath))
    paths.distinct.map { warehousepath =>
      val path = _project_artifact_path(config, warehousepath)
      ProjectArtifactRecord(_project_artifact_type(warehousepath), warehousepath, path, Files.isRegularFile(path))
    }
  }

  private def _project_artifact_type(warehousepath: String): String = {
    val name = Paths.get(warehousepath).getFileName.toString.toLowerCase(Locale.ROOT)
    if (name.endsWith(".jar")) "jar"
    else if (name.endsWith(".sar")) "sar"
    else if (name.endsWith(".car")) "car"
    else "other"
  }

  private def _project_artifact_type_counts(records: Vector[ProjectArtifactRecord]): Vector[(String, Int)] = {
    val grouped = records.groupBy(_.artifacttype).map {
      case (kind, xs) => kind -> xs.size
    }
    _project_artifact_type_order.map(kind => kind -> grouped.getOrElse(kind, 0)).toVector
  }

  private val _project_artifact_type_order: Vector[String] =
    Vector("car", "sar", "jar")

  private def _project_artifact_type_counts_html(locale: String, counts: Vector[(String, Int)]): String =
    counts.map {
      case (kind, count) =>
        s"""<span>${_html_escape(_project_artifact_type_label(kind))} ${count}</span>"""
    }.mkString(s"""<span class="bok-project-artifact-kind-counts" aria-label="${_html_escape(_ui(locale, "project.label.artifact.kind.counts"))}">""", "", "</span>")

  private def _project_artifact_type_label(kind: String): String =
    kind.toUpperCase(Locale.ROOT)

  private def _project_artifact_availability_counts_html(locale: String, distributed: Int, undistributed: Int): String =
    Vector(
      _ui(locale, "project.label.distributed.artifacts") -> distributed,
      _ui(locale, "project.label.undistributed.artifacts") -> undistributed
    ).map {
      case (label, count) =>
        s"""<span>${_html_escape(label)} ${count}</span>"""
    }.mkString(s"""<span class="bok-project-artifact-availability-counts" aria-label="${_html_escape(_ui(locale, "project.label.distribution.availability"))}">""", "", "</span>")

  private def _project_artifact_status_label(locale: String, status: String): String =
    status match {
      case "published" => _ui(locale, "project.status.published")
      case "missing" => _ui(locale, "project.status.missing")
      case "source-only" => _ui(locale, "project.status.source.only")
      case other => other
    }

  private def _project_artifact_status_summary_label(locale: String, status: String): String =
    status match {
      case "published" => _ui(locale, "project.status.summary.published")
      case "missing" => _ui(locale, "project.status.summary.missing")
      case "source-only" => _ui(locale, "project.status.summary.source.only")
      case other => other
    }

  private def _project_artifact_status_fact_label(locale: String, status: String): String =
    status match {
      case "published" => _ui(locale, "project.status.fact.published")
      case "missing" => _ui(locale, "project.status.fact.missing")
      case "source-only" => _ui(locale, "project.status.fact.source.only")
      case other => other
    }

  private def _project_mode_label(locale: String, mode: String): String =
    mode match {
      case "external" => _ui(locale, "project.mode.external")
      case "internal" => _ui(locale, "project.mode.internal")
      case "local" => _ui(locale, "project.mode.internal")
      case other => other
    }

  private def _project_mode_counts(projects: Vector[CozyBokProjectPublisher.ResolvedBokProject]): Vector[(String, Int)] = {
    val grouped = projects.groupBy(_.projectmode).map {
      case (mode, xs) => mode -> xs.size
    }
    _project_mode_order.map(mode => mode -> grouped.getOrElse(mode, 0)).toVector
  }

  private val _project_mode_order: Vector[String] =
    Vector("internal", "external")

  private def _project_mode_counts_html(locale: String, counts: Vector[(String, Int)]): String =
    counts.map {
      case (mode, count) =>
        s"""<span>${_html_escape(_project_mode_label(locale, mode))} ${count}</span>"""
    }.mkString(s"""<span class="bok-project-mode-counts" aria-label="${_html_escape(_ui(locale, "project.label.placement"))}">""", "", "</span>")

  private def _project_cml_element_kind_counts(projects: Vector[CozyBokProjectPublisher.ResolvedBokProject]): Vector[(String, Int)] =
    projects.flatMap(_.cml.toVector).flatMap(_.modelElements).groupBy(_.kind).toVector.map {
      case (kind, xs) => kind -> xs.size
    }.sortBy(_._1)

  private def _project_cml_element_kind_counts_html(locale: String, counts: Vector[(String, Int)]): String =
    if (counts.isEmpty)
      ""
    else
      counts.map {
        case (kind, count) =>
          s"""<span>${_html_escape(_project_cml_element_kind_label(kind))} ${count}</span>"""
      }.mkString(s"""<span class="bok-project-cml-kind-counts" aria-label="${_html_escape(_ui(locale, "project.label.cml.kind.counts"))}">""", "", "</span>")

  private def _project_cml_element_kind_label(kind: String): String =
    kind.split("[-_]").filter(_.nonEmpty).map { part =>
      part.head.toUpper + part.tail
    }.mkString(" ")

  private def _project_cml_element_kind_counts_inline_html(locale: String, counts: Vector[(String, Int)]): String =
    if (counts.isEmpty)
      ""
    else
      s"""<div class="bok-project-cml-kind-line"><span>${_html_escape(_ui(locale, "project.label.cml.kind.counts"))}</span>${_project_cml_element_kind_counts_html(locale, counts)}</div>"""

  private def _project_artifact_records_html(locale: String, planned: Vector[ProjectArtifactRecord], current: Vector[ProjectArtifactRecord]): String = {
    val plannedcounts = _project_artifact_type_counts(planned)
    val currentcounts = _project_artifact_type_counts(current)
    val planneditems =
      if (planned.isEmpty)
        s"""<li>${_html_escape(_ui(locale, "project.artifacts.none.planned"))}</li>"""
      else
        planned.map(record => s"""<li><code>${_html_escape(record.warehousepath)}</code></li>""").mkString
    val currentitems =
      if (current.isEmpty)
        s"""<li>${_html_escape(_ui(locale, "project.artifacts.none.current"))}</li>"""
      else
        current.map(record => s"""<li><code>${_html_escape(record.warehousepath)}</code></li>""").mkString
    s"""<div class="bok-project-artifact-lists">
       |  <div><h4>${_html_escape(_ui(locale, "project.label.planned.artifacts"))}</h4><div class="bok-project-artifact-kind-line"><span>${_html_escape(_ui(locale, "project.label.planned.artifact.kind.counts"))}</span>${_project_artifact_type_counts_html(locale, plannedcounts)}</div><ul>${planneditems}</ul></div>
       |  <div><h4>${_html_escape(_ui(locale, "project.label.current.artifacts"))}</h4><div class="bok-project-artifact-kind-line"><span>${_html_escape(_ui(locale, "project.label.current.artifact.kind.counts"))}</span>${_project_artifact_type_counts_html(locale, currentcounts)}</div><ul>${currentitems}</ul></div>
       |</div>""".stripMargin
  }

  private def _project_detail_dashboard(
    config: BuildConfig,
    locale: String,
    project: CozyBokProjectPublisher.ResolvedBokProject
  ): String = {
    val artifactrecords = _project_artifact_records(config, project)
    val plannedartifacts = artifactrecords
    val currentartifacts = artifactrecords.filter(_.exists)
    val missingartifactcount = plannedartifacts.size - currentartifacts.size
    val cmlcount = project.cml.map(_.modelElements.size).getOrElse(0)
    val cmlkinds = _project_cml_element_kind_counts(Vector(project))
    val source = project.projectref.getOrElse(project.projectmode)
    val facts = Vector(
      _ui(locale, "project.label.type") -> project.descriptor.project.projecttype,
      _ui(locale, "project.label.version") -> project.version,
      _ui(locale, "project.label.distribution.artifacts") -> plannedartifacts.size.toString,
      _ui(locale, "project.label.distributed.artifacts") -> currentartifacts.size.toString,
      _ui(locale, "project.label.undistributed.artifacts") -> missingartifactcount.toString,
      _ui(locale, "project.label.cml.terms") -> cmlcount.toString
    ).map {
      case (label, value) =>
        s"""<span class="bok-project-fact"><strong>${_html_escape(value)}</strong><em>${_html_escape(label)}</em></span>"""
    }.mkString
    val sourceonlynotice =
      if (project.reference.isSourceOnly)
        s"""<p class="bok-project-source-only-note">${_html_escape(_ui(locale, "project.artifacts.source.only"))}</p>"""
      else
        ""
    val product =
      s"""<dl class="bok-project-detail-dl">
         |  <dt>${_html_escape(_ui(locale, "project.label.module"))}</dt><dd><code>${_html_escape(project.module)}</code></dd>
         |  <dt>${_html_escape(_ui(locale, "project.label.placement"))}</dt><dd>${_html_escape(_project_mode_label(locale, project.projectmode))}</dd>
         |  <dt>${_html_escape(_ui(locale, "project.label.source.project"))}</dt><dd>${_html_escape(source)}</dd>
         |  <dt>${_html_escape(_ui(locale, "project.label.version.source"))}</dt><dd>${_html_escape(project.versionsource)}</dd>
         |  <dt>${_html_escape(_ui(locale, "project.label.package"))}</dt><dd><code>${_html_escape(config.sourcepath.relativize(project.packagedir).toString.replace(java.io.File.separatorChar, '/'))}</code></dd>
         |</dl>
         |${sourceonlynotice}
         |${_project_artifact_records_html(locale, plannedartifacts, currentartifacts)}
         |${_project_cml_element_kind_counts_inline_html(locale, cmlkinds)}""".stripMargin
    s"""<section class="bok-project-detail-dashboard" id="project-dashboard">
       |  <div class="bok-project-detail-hero">
       |    <div>
       |      <p class="bok-dashboard-eyebrow">${_html_escape(_ui(locale, "project.card.dashboard"))}</p>
       |      <h2>${_html_escape(project.title)}</h2>
       |      <p>${_html_escape(project.summary.getOrElse("CAR project."))}</p>
       |    </div>
       |    <div class="bok-project-facts">${facts}</div>
       |  </div>
       |  <div class="bok-dashboard container-fluid bok-dashboard-command-center">
       |    <div class="row g-3">
       |      ${_dashboard_card("col-12", "bok-card-map bok-card-project-overview", _ui(locale, "project.card.product.metadata"), product, Vector("reader", "contributor", "project_manager"))}
       |    </div>
       |  </div>
       |</section>""".stripMargin
  }

  private def _project_component_surface_html(locale: String, project: CozyBokProjectPublisher.ResolvedBokProject): String = {
    def _operation_items_(xs: Vector[CozyBokProjectPublisher.CmlOperationSurface]): String =
      if (xs.isEmpty)
        s"""<p class="bok-project-empty-note">${_html_escape(_ui(locale, "project.surface.no.operations"))}</p>"""
      else
        xs.map { element =>
          val summary = _project_descriptive_summary(element.descriptive)
          val function = _project_operation_function(locale, element)
          val functionhtml = if (function.isEmpty) "" else s"""<span><code>${_html_escape(function)}</code></span>"""
          val summaryhtml = if (summary.isEmpty) "" else s"""<span>${_html_escape(summary)}</span>"""
          s"""<li><strong>${_html_escape(element.name)}</strong>${functionhtml}${summaryhtml}</li>"""
        }.mkString("""<ul class="bok-project-operation-list">""", "", "</ul>")
    def _service_node_(service: CozyBokProjectPublisher.CmlServiceSurface): String = {
      val summary = service.descriptive.summary.orElse(service.descriptive.brief).orElse(service.descriptive.description).getOrElse("")
      s"""<li class="bok-project-service-node">
         |  <div class="bok-project-node-head"><span>Service</span><strong>${_html_escape(service.name)}</strong></div>
         |  ${if (summary.isEmpty) "" else s"""<p>${_html_escape(summary)}</p>"""}
         |  <div class="bok-project-operation-branch">
         |    <div class="bok-project-node-head bok-project-node-head-sub"><span>Operation</span></div>
         |    ${_operation_items_(service.operations)}
         |  </div>
         |</li>""".stripMargin
    }
    def _component_tree_(component: CozyBokProjectPublisher.CmlComponentSurface): String = {
      val summary = component.descriptive.summary.orElse(component.descriptive.brief).orElse(component.descriptive.description).getOrElse("")
      val servicebranch =
        if (component.services.isEmpty)
          s"""<div class="bok-project-service-branch">
             |  <div class="bok-project-node-head bok-project-node-head-sub"><span>Service</span></div>
             |  <p class="bok-project-empty-note">${_html_escape(_ui(locale, "project.surface.no.services"))}</p>
             |</div>""".stripMargin
        else
          component.services.map(_service_node_).mkString("""<ul class="bok-project-service-branch">""", "", "</ul>")
      s"""<article class="bok-project-component-tree">
         |  <div class="bok-project-node-head"><span>Component</span><strong><code>${_html_escape(component.name)}</code></strong></div>
         |  ${if (summary.isEmpty) "" else s"""<p>${_html_escape(summary)}</p>"""}
         |  ${servicebranch}
         |</article>""".stripMargin
    }
    val surface = project.cml.flatMap(_.surface.component).map(_component_tree_).getOrElse {
      s"""<article class="bok-project-component-tree">
         |  <div class="bok-project-node-head"><span>Component</span><strong><code>${_html_escape(project.module)}</code></strong></div>
         |  <p class="bok-project-empty-note">${_html_escape(_ui(locale, "project.surface.no.component"))}</p>
         |</article>""".stripMargin
    }
    s"""<section class="bok-project-section bok-project-surface" id="project-surface">
       |  <div class="bok-project-section-head">
       |    <h2>${_html_escape(_ui(locale, "project.section.surface"))}</h2>
       |    <p>${_html_escape(_ui(locale, "project.section.surface.description"))}</p>
       |  </div>
       |  ${surface}
       |</section>""".stripMargin
  }

  private final case class ProjectCmlTermRow(
    kind: String,
    name: String,
    terms: Vector[TermEntry],
    function: String,
    summary: String
  )

  private def _project_model_terms_html(config: BuildConfig, locale: String, project: CozyBokProjectPublisher.ResolvedBokProject, page: Path, target: Path): String =
    project.cml.map { cml =>
      val terms = _terms(config)
      val termrows = _project_cml_term_rows(locale, cml, terms)
      val linkedcount = termrows.count(_.terms.nonEmpty)
      val unlinkedcount = termrows.size - linkedcount
      def _row_html_(row: ProjectCmlTermRow, includesignature: Boolean): String = {
        val termhtml =
          if (row.terms.isEmpty)
            s"""<span class="bok-project-unlinked-term">-</span>"""
          else
            row.terms.map { term =>
              val href = _relative_href(page, target.resolve(term.publicpath))
              s"""<a href="${_html_escape(href)}">${_html_escape(term.title)}</a>"""
            }.mkString("""<span class="bok-project-linked-terms">""", "", "</span>")
        val signaturecell =
          if (includesignature)
            s"""  <td>${_html_escape(row.function)}</td>
               |""".stripMargin
          else
            ""
        s"""<tr>
           |  <td>${_html_escape(row.name)}</td>
           |  <td>${termhtml}</td>
           |${signaturecell}  <td>${_html_escape(row.summary)}</td>
           |</tr>""".stripMargin
      }
      val preferredkinds = Vector("Component", "Service", "Operation", "Entity")
      val actualkinds = termrows.map(_.kind).distinct
      val tabkinds = preferredkinds.filter(actualkinds.contains) ++ actualkinds.filterNot(preferredkinds.contains)
      val initialkind = tabkinds.find(_ == "Entity").orElse(tabkinds.headOption)
      val tabbuttons = tabkinds.map { kind =>
          val rows = termrows.filter(_.kind == kind)
          val termcount = rows.flatMap(_.terms.map(_.id)).distinct.size
          val modelcount = rows.size
          val active = initialkind.contains(kind)
          val tabid = s"project-cml-tab-${_tag_segment(kind)}"
          val panelid = s"project-cml-panel-${_tag_segment(kind)}"
          s"""<button type="button" role="tab" id="${_html_escape(tabid)}" aria-selected="${active}" aria-controls="${_html_escape(panelid)}" class="${if (active) "is-active" else ""}" data-project-cml-tab="${_html_escape(kind)}">${_html_escape(kind)}(${termcount}/${modelcount})</button>"""
      }.mkString("\n")
      val tabpanels = tabkinds.map { kind =>
          val includesignature = kind == "Operation"
          val rows = termrows.filter(_.kind == kind).map(_row_html_(_, includesignature)).mkString("\n")
          val headers =
            if (includesignature)
              s"""<th>${_html_escape(_ui(locale, "project.model.name"))}</th><th>${_html_escape(_ui(locale, "project.model.term"))}</th><th>${_html_escape(_ui(locale, "project.model.function"))}</th><th>${_html_escape(_ui(locale, "project.model.description"))}</th>"""
            else
              s"""<th>${_html_escape(_ui(locale, "project.model.name"))}</th><th>${_html_escape(_ui(locale, "project.model.term"))}</th><th>${_html_escape(_ui(locale, "project.model.description"))}</th>"""
          val active = initialkind.contains(kind)
          val tabid = s"project-cml-tab-${_tag_segment(kind)}"
          val panelid = s"project-cml-panel-${_tag_segment(kind)}"
          s"""<section id="${_html_escape(panelid)}" class="bok-project-cml-tab-panel ${if (active) "is-active" else ""}" role="tabpanel" aria-labelledby="${_html_escape(tabid)}" data-project-cml-panel="${_html_escape(kind)}"${if (active) "" else " hidden"}>
             |  <div class="bok-project-table-wrap">
             |    <table class="table table-sm bok-project-cml-table">
             |      <thead><tr>${headers}</tr></thead>
             |      <tbody>
             |${rows}
             |      </tbody>
             |    </table>
             |  </div>
             |</section>""".stripMargin
      }.mkString("\n")
      val summarycards =
        s"""<div class="bok-project-cml-link-summary">
           |  <article class="bok-project-cml-summary-card bok-project-cml-summary-total">
           |    <strong>${termrows.size}</strong>
           |    <span>${_html_escape(_ui(locale, "project.model.count.total"))}</span>
           |  </article>
           |  <article class="bok-project-cml-summary-card bok-project-cml-summary-breakdown">
           |    <span>${_html_escape(_ui(locale, "project.model.count.term.links"))}</span>
           |    <dl>
           |      <dt>${_html_escape(_ui(locale, "project.model.count.linked"))}</dt><dd>${linkedcount}</dd>
           |      <dt>${_html_escape(_ui(locale, "project.model.count.unlinked"))}</dt><dd>${unlinkedcount}</dd>
           |    </dl>
           |  </article>
           |</div>""".stripMargin
      val sources = _project_cml_source_cards(locale, cml)
      if (termrows.isEmpty)
        ""
      else
        s"""<section class="bok-project-section project-cml-glossary" id="project-model-terms">
           |  <div class="bok-project-section-head">
           |    <h2>${_html_escape(_ui(locale, "project.section.model.terms"))}</h2>
           |    <p>${_html_escape(_ui(locale, "project.section.model.terms.description"))}</p>
           |  </div>
           |  ${summarycards}
           |  ${sources}
           |  <div class="bok-project-cml-tabs" data-project-cml-tabs>
           |    <div class="bok-project-cml-tablist" role="tablist" aria-label="${_html_escape(_ui(locale, "project.section.model.terms"))}">
           |${tabbuttons}
           |    </div>
           |${tabpanels}
           |  </div>
           |  <script>
           |(() => {
           |  document.querySelectorAll('[data-project-cml-tabs]').forEach((root) => {
           |    const tabs = Array.from(root.querySelectorAll('[data-project-cml-tab]'));
           |    const panels = Array.from(root.querySelectorAll('[data-project-cml-panel]'));
           |    tabs.forEach((tab) => {
           |      tab.addEventListener('click', () => {
           |        const name = tab.dataset.projectCmlTab;
           |        tabs.forEach((x) => {
           |          const active = x === tab;
           |          x.classList.toggle('is-active', active);
           |          x.setAttribute('aria-selected', active ? 'true' : 'false');
           |        });
           |        panels.forEach((panel) => {
           |          const active = panel.dataset.projectCmlPanel === name;
           |          panel.classList.toggle('is-active', active);
           |          panel.hidden = !active;
           |        });
           |      });
           |    });
           |  });
           |})();
           |  </script>
           |</section>""".stripMargin
    }.getOrElse("")

  private def _project_cml_source_cards(locale: String, cml: CozyBokProjectPublisher.ProjectCmlInfo): String = {
    val path = cml.sourceprojectrelativepath
    val name = Option(java.nio.file.Paths.get(path).getFileName).map(_.toString).getOrElse(path)
    s"""<div class="bok-project-cml-sources" aria-label="${_html_escape(_ui(locale, "project.model.source.title"))}">
       |  <article class="bok-project-cml-source-card">
       |    <span>${_html_escape(_ui(locale, "project.model.source.title"))}</span>
       |    <strong>${_html_escape(name)}</strong>
       |    <code>${_html_escape(path)}</code>
       |  </article>
       |</div>""".stripMargin
  }

  private def _project_cml_term_rows(locale: String, cml: CozyBokProjectPublisher.ProjectCmlInfo, terms: Vector[TermEntry]): Vector[ProjectCmlTermRow] = {
    def _linked_terms_(kind: String, name: String): Vector[TermEntry] =
      terms.filter(term => term.cmlLinks.exists(link => _project_cml_link_matches(link, kind, name)))
    val surface = cml.surface.component.toVector.flatMap { component =>
      val componentrow = ProjectCmlTermRow(
        "Component",
        component.name,
        _linked_terms_("component", component.name),
        "",
        _project_descriptive_summary(component.descriptive)
      )
      val servicerows = component.services.flatMap { service =>
        val servicerow = ProjectCmlTermRow(
          "Service",
          service.name,
          _linked_terms_("service", service.name),
          "",
          _project_descriptive_summary(service.descriptive)
        )
        val operationrows = service.operations.map { operation =>
          ProjectCmlTermRow(
            "Operation",
            operation.name,
            _linked_terms_("operation", operation.name),
            _project_operation_function(locale, operation),
            _project_descriptive_summary(operation.descriptive)
          )
        }
        servicerow +: operationrows
      }
      componentrow +: servicerows
    }
    val model = cml.modelElements.map { element =>
      ProjectCmlTermRow(
        _project_cml_element_kind_label(element.kind),
        element.name,
        _linked_terms_(element.kind, element.name),
        "",
        _project_descriptive_summary(element.descriptive)
      )
    }
    surface ++ model
  }

  private def _project_descriptive_summary(descriptive: CozyBokProjectPublisher.CmlDescriptive): String =
    descriptive.summary.orElse(descriptive.description).orElse(descriptive.brief).getOrElse("")

  private def _project_operation_function(locale: String, operation: CozyBokProjectPublisher.CmlOperationSurface): String = {
    val input = operation.inputtype.getOrElse(_ui(locale, "project.surface.operation.input.unspecified"))
    val output = operation.outputtype.getOrElse(_ui(locale, "project.surface.operation.output.unspecified"))
    operation.operationtype match {
      case Some(kind) => s"${kind}: ${input} -> ${output}"
      case None => s"${input} -> ${output}"
    }
  }

  private def _project_cml_link_matches(link: TermCmlLink, kind: String, name: String): Boolean = {
    def _normalize_(x: String): String =
      x.trim.toLowerCase(Locale.ROOT).replace("_", "-")
    _normalize_(link.kind) == _normalize_(kind) && _normalize_(link.value) == _normalize_(name)
  }

  private def _project_narrative_html(locale: String, articlebody: String): String =
    if (articlebody.trim.isEmpty)
      ""
    else
      s"""<section class="bok-project-section bok-project-narrative" id="project-narrative">
         |  <div class="bok-project-section-head">
         |    <h2>${_html_escape(_ui(locale, "project.section.narrative"))}</h2>
         |  </div>
         |  <div class="bok-project-narrative-body">
         |${articlebody}
         |  </div>
         |</section>""".stripMargin

}
