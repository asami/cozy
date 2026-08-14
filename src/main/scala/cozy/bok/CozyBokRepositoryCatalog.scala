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

private[cozy] trait CozyBokRepositoryCatalog {
  self: CozyBokImplementation.type =>
  private[bok] def _write_repository_car_page(
    config: BuildConfig,
    target: Path,
    locale: String,
    categories: Vector[CategoryContent]
  ): Unit = {
    val index = _repository_car_index(config)
    val projects = _resolved_project_packages(config)
    _copy_repository_car_sidecars(target, index)
    _write_repository_car_archive_metadata(target, index)
    val page = target.resolve("repository").resolve("car").resolve("index.html")
    _write_text(
      page,
      _special_html_page(
        config,
        categories,
        locale,
        page,
        _repository_car_title(locale),
        _repository_car_description(locale),
        _repository_car_dashboard_body(config, locale, page, target, index)
      )
    )
    index.entries.foreach { entry =>
      val modulepage = target.resolve(entry.publicPath)
      _write_text(
        modulepage,
        _special_html_page(
          config,
          categories,
          locale,
          modulepage,
          entry.artifactid,
          _repository_car_module_description(locale, entry),
          _repository_car_module_body(config, target, modulepage, locale, entry, projects)
        )
      )
      entry.versions.foreach { version =>
        val versionpage = target.resolve(entry.versionPublicPath(version))
        _write_text(
          versionpage,
          _special_html_page(
            config,
            categories,
            locale,
            versionpage,
            s"${entry.artifactid} ${version.version}",
            _repository_car_version_description(locale, entry, version),
            _repository_car_version_body(config, target, versionpage, locale, entry, version, projects)
          )
        )
      }
    }
  }

  private[bok] def _write_component_repository_page(
    config: BuildConfig,
    target: Path,
    locale: String,
    categories: Vector[CategoryContent]
  ): Unit = {
    val projects = _safe_resolved_project_packages(config)
    val cardiscovery = _repository_catalog_discovery(config, projects, "car")
    val sardiscovery = _repository_catalog_discovery(config, projects, "sar")
    val carindex = _repository_car_index(config)
    val page = target.resolve("repository/index.html")
    _write_text(
      page,
      _special_html_page(
        config,
        categories,
        locale,
        page,
        _component_repository_title(locale),
        _component_repository_description(locale),
        _component_repository_dashboard_body(
          target,
          page,
          locale,
          carindex,
          sardiscovery.sources,
          cardiscovery.diagnostics ++ sardiscovery.diagnostics
        )
      )
    )
  }

  private def _component_repository_dashboard_body(
    target: Path,
    page: Path,
    locale: String,
    carindex: RepositoryCarIndex,
    sarartifacts: Vector[RepositoryCatalogSource],
    diagnostics: Vector[RepositoryCatalogDiagnostic]
  ): String = {
    val carversions = carindex.entries.map(_.versions.size).sum
    val sarversions = sarartifacts.map(_.catalog.versions.size).sum
    val carhref = _relative_href(page, target.resolve("repository/car/index.html"))
    val sarhref = _relative_href(page, target.resolve("repository/sar/index.html"))
    s"""<section class="bok-dashboard-shell bok-component-repository-dashboard" id="component-repository-dashboard">
       |  ${_dashboard_hero(
            _component_repository_title(locale),
            _component_repository_description(locale),
            Vector(
              "CAR" -> carindex.entries.size.toString,
              "SAR" -> sarartifacts.size.toString
            )
          )}
       |  <div class="bok-dashboard container-fluid bok-dashboard-command-center">
       |    <div class="row g-3">
       |      ${_component_repository_summary_card(locale, "car", carindex.entries.size, carversions, carhref)}
       |      ${_component_repository_summary_card(locale, "sar", sarartifacts.size, sarversions, sarhref)}
       |      ${_repository_catalog_diagnostics_html(locale, diagnostics)}
       |    </div>
       |  </div>
       |</section>""".stripMargin
  }

  private def _component_repository_summary_card(
    locale: String,
    kind: String,
    artifacts: Int,
    versions: Int,
    href: String
  ): String = {
    val label = kind.toUpperCase
    val body =
      s"""<a class="bok-component-repository-link" data-component-kind="${_html_escape(kind)}" href="${_html_escape(href)}">
         |  <span class="bok-component-repository-count">${artifacts}</span>
         |  <span class="bok-component-repository-unit">${_html_escape(_component_repository_artifacts_label(locale))}</span>
         |  <span class="bok-card-muted">${versions} ${_html_escape(_repository_car_versions_label(locale))}</span>
         |</a>""".stripMargin
    _dashboard_card(
      "col-12 col-md-6",
      "bok-card-kpi bok-card-component-repository",
      label,
      body,
      Vector("reader", "contributor", "project_manager")
    )
  }

  private[bok] def _write_repository_sar_pages(
    config: BuildConfig,
    target: Path,
    locale: String,
    categories: Vector[CategoryContent]
  ): Unit = {
    val projects = _resolved_project_packages(config)
    val discovery = _repository_catalog_discovery(config, projects, "sar")
    val artifacts = discovery.sources
    val indexpage = target.resolve("repository/sar/index.html")
    _write_text(
      indexpage,
      _special_html_page(
        config,
        categories,
        locale,
        indexpage,
        _repository_sar_title(locale),
        _repository_sar_description(locale),
        _repository_sar_index_body(target, indexpage, locale, artifacts, discovery.diagnostics)
      )
    )
    artifacts.foreach { artifact =>
      val artifactid = artifact.catalog.artifactId
      val relatedprojects = _sie_repository_artifact_related_projects("sar", artifactid, projects)
      val modulepage = target.resolve("repository/sar").resolve(artifactid).resolve("index.html")
      _write_text(
        modulepage,
        _special_html_page(
          config,
          categories,
          locale,
          modulepage,
          artifactid,
          _repository_sar_module_description(locale, artifactid),
          _repository_sar_module_body(target, modulepage, locale, artifact, relatedprojects)
        )
      )
      artifact.catalog.versions.foreach { version =>
        val versionpage = target.resolve("repository/sar").resolve(artifactid).resolve(s"${version.version}.html")
        _write_text(
          versionpage,
          _special_html_page(
            config,
            categories,
            locale,
            versionpage,
            s"${artifactid} ${version.version}",
            _repository_sar_version_description(locale, artifactid, version.version),
            _repository_sar_version_body(target, versionpage, locale, artifact, version, relatedprojects)
          )
        )
      }
    }
  }

  private def _repository_sar_index_body(
    target: Path,
    page: Path,
    locale: String,
    artifacts: Vector[RepositoryCatalogSource],
    diagnostics: Vector[RepositoryCatalogDiagnostic]
  ): String = {
    val content = if (artifacts.isEmpty)
      s"""<p class="bok-card-muted">${_html_escape(_repository_sar_empty(locale))}</p>"""
    else {
      val rows = artifacts.map { artifact =>
      val artifactid = artifact.catalog.artifactId
      val href = _relative_href(page, target.resolve("repository/sar").resolve(artifactid).resolve("index.html"))
      s"""<tr>
         |  <td><a href="${_html_escape(href)}"><code>${_html_escape(artifactid)}</code></a></td>
         |  <td>${_html_escape(artifact.catalog.recommended.getOrElse("-"))}</td>
         |  <td>${_html_escape(artifact.catalog.latestStable.getOrElse("-"))}</td>
         |  <td>${artifact.catalog.versions.size}</td>
         |  <td><code>${_html_escape(_repository_catalog_public_source(artifact))}</code></td>
         |</tr>""".stripMargin
      }.mkString("\n")
      s"""<div class="bok-project-table-wrap">
         |  <table class="table table-sm bok-project-cml-table">
         |    <thead><tr><th>SAR</th><th>${_html_escape(_ui(locale, "project.label.sie.recommended"))}</th><th>${_html_escape(_ui(locale, "project.label.sie.latest.stable"))}</th><th>${_html_escape(_repository_car_versions_label(locale))}</th><th>${_html_escape(_repository_car_catalog_label(locale))}</th></tr></thead>
         |    <tbody>
         |${rows}
         |    </tbody>
         |  </table>
         |</div>""".stripMargin
    }
    s"""<section class="bok-dashboard-shell bok-repository-car-dashboard" id="dashboard">
       |  ${_dashboard_hero(
            _repository_sar_title(locale),
            _repository_sar_description(locale),
            Vector(
              "SAR" -> artifacts.size.toString,
              _repository_car_versions_label(locale) -> artifacts.map(_.catalog.versions.size).sum.toString
            )
          )}
       |  <div class="bok-dashboard container-fluid bok-dashboard-command-center">
       |    <div class="row g-3">
       |      ${_dashboard_card("col-12", "bok-card-map bok-card-project-map", _repository_sar_title(locale), content, Vector("reader", "contributor", "project_manager"))}
       |      ${_repository_catalog_diagnostics_html(locale, diagnostics)}
       |    </div>
       |  </div>
       |</section>""".stripMargin
  }

  private def _repository_sar_module_body(
    target: Path,
    page: Path,
    locale: String,
    artifact: RepositoryCatalogSource,
    projects: Vector[CozyBokProjectPublisher.ResolvedBokProject]
  ): String = {
    val artifactid = artifact.catalog.artifactId
    val versionrows = artifact.catalog.versions.map { version =>
      val href = _relative_href(page, target.resolve("repository/sar").resolve(artifactid).resolve(s"${version.version}.html"))
      s"""<tr>
         |  <td><a href="${_html_escape(href)}">${_html_escape(version.version)}</a></td>
         |  <td>${_html_escape(version.channel.getOrElse("-"))}</td>
         |  <td>${_html_escape(version.status.getOrElse("active"))}</td>
         |  <td><code>${_html_escape(version.file.getOrElse("-"))}</code></td>
         |</tr>""".stripMargin
    }.mkString("\n")
    s"""<section class="bok-project-section bok-repository-car-detail" id="repository-sar-detail">
       |  ${_repository_car_properties_table(locale, Vector(
            _repository_car_catalog_label(locale) -> s"<code>${_html_escape(_repository_catalog_public_source(artifact))}</code>",
            _ui(locale, "project.label.sie.recommended") -> _html_escape(artifact.catalog.recommended.getOrElse("-")),
            _ui(locale, "project.label.sie.latest.stable") -> _html_escape(artifact.catalog.latestStable.getOrElse("-")),
            _repository_car_status_label(locale) -> _html_escape(artifact.catalog.status.getOrElse("active"))
          ))}
       |  <h2>${_html_escape(_repository_car_versions_label(locale))}</h2>
       |  <div class="bok-project-table-wrap">
       |    <table class="table table-sm bok-project-cml-table">
       |      <thead><tr><th>${_html_escape(_repository_car_version_label(locale))}</th><th>${_html_escape(_repository_car_channel_label(locale))}</th><th>${_html_escape(_repository_car_status_label(locale))}</th><th>${_html_escape(_repository_car_file_label(locale))}</th></tr></thead>
       |      <tbody>
       |${versionrows}
       |      </tbody>
       |    </table>
       |  </div>
       |  ${_repository_car_related_projects_html(target, page, locale, projects)}
       |</section>""".stripMargin
  }

  private def _repository_sar_version_body(
    target: Path,
    page: Path,
    locale: String,
    artifact: RepositoryCatalogSource,
    version: _root_.cozy.archive.RepositoryArtifactCatalogVersion,
    projects: Vector[CozyBokProjectPublisher.ResolvedBokProject]
  ): String =
    s"""<section class="bok-project-section bok-repository-car-version" id="repository-sar-version">
       |  ${_repository_car_properties_table(locale, Vector(
            _repository_car_catalog_label(locale) -> s"<code>${_html_escape(_repository_catalog_public_source(artifact))}</code>",
            _repository_car_version_label(locale) -> _html_escape(version.version),
            _repository_car_channel_label(locale) -> _html_escape(version.channel.getOrElse("-")),
            _repository_car_status_label(locale) -> _html_escape(version.status.getOrElse("active")),
            _repository_car_file_label(locale) -> s"<code>${_html_escape(version.file.getOrElse("-"))}</code>"
          ))}
       |  ${_repository_car_related_projects_html(target, page, locale, projects)}
       |</section>""".stripMargin

  private def _sie_repository_artifact_related_projects(
    kind: String,
    artifactid: String,
    projects: Vector[CozyBokProjectPublisher.ResolvedBokProject]
  ): Vector[CozyBokProjectPublisher.ResolvedBokProject] =
    projects.filter(_.sie.exists(_.artifacts.exists(x => x.kind == kind && x.artifactId == artifactid))).sortBy(_.publicationpath)

  private def _repository_sar_title(locale: String): String =
    locale match {
      case "ja" => "SARリポジトリ"
      case _ => "Repository SARs"
    }

  private def _repository_sar_description(locale: String): String =
    locale match {
      case "ja" => "Component Repositoryで公開されるrepository/catalog/sarのSAR一覧です。"
      case _ => "Published SAR entries from the Component Repository index."
    }

  private def _repository_sar_empty(locale: String): String =
    locale match {
      case "ja" => "Repository SAR catalogはまだありません。"
      case _ => "No repository SAR catalog entries are available."
    }

  private def _repository_catalog_diagnostics_html(
    locale: String,
    diagnostics: Vector[RepositoryCatalogDiagnostic]
  ): String =
    if (diagnostics.isEmpty)
      ""
    else {
      val items = diagnostics.distinct.sortBy(x => (x.kind, x.artifactid, x.code, x.catalog.getOrElse(""))).map { diagnostic =>
        val subject = s"${diagnostic.kind.toUpperCase(Locale.ROOT)} ${diagnostic.artifactid}"
        val catalog = diagnostic.catalog.map(x => s" <code>${_html_escape(x)}</code>").getOrElse("")
        s"""<li data-repository-diagnostic-code="${_html_escape(diagnostic.code)}" data-repository-kind="${_html_escape(diagnostic.kind)}"><strong>${_html_escape(subject)}</strong><span>${_html_escape(_repository_car_diagnostic_message(locale, diagnostic.code))}${catalog}</span></li>"""
      }.mkString("\n")
      _dashboard_card(
        "col-12",
        "bok-card-map bok-card-project-issues",
        _component_repository_diagnostics_label(locale),
        s"""<ul class="bok-repository-car-diagnostic-list">
           |${items}
           |</ul>""".stripMargin,
        Vector("contributor", "project_manager")
      )
    }

  private[bok] def _component_repository_title(locale: String): String =
    locale match {
      case "ja" => "Component Repository"
      case _ => "Component Repository"
    }

  private def _component_repository_description(locale: String): String =
    locale match {
      case "ja" => "公開されているCARとSARを一覧し、component artifactの知識へ移動するDashboardです。"
      case _ => "Dashboard for published CAR and SAR component artifacts."
    }

  private def _component_repository_artifacts_label(locale: String): String =
    locale match {
      case "ja" => "Artifacts"
      case _ => "Artifacts"
    }

  private def _component_repository_diagnostics_label(locale: String): String =
    locale match {
      case "ja" => "Component Repository診断"
      case _ => "Component Repository Diagnostics"
    }

  private def _repository_sar_module_description(locale: String, artifactid: String): String =
    locale match {
      case "ja" => s"${artifactid} のSAR catalogと公開versionです。"
      case _ => s"SAR catalog and published versions for ${artifactid}."
    }

  private def _repository_sar_version_description(locale: String, artifactid: String, version: String): String =
    locale match {
      case "ja" => s"${artifactid} ${version} の公開SAR version情報です。"
      case _ => s"Published SAR version information for ${artifactid} ${version}."
    }

  private[bok] def _repository_car_index(config: BuildConfig): RepositoryCarIndex = {
    val projects = _safe_resolved_project_packages(config)
    val discovery = _repository_index_catalog_sources(config, "car").getOrElse(
      _repository_catalog_fallback_discovery(config, projects, "car")
    )
    val sources = discovery.sources
    val entries = sources.map(_repository_car_entry).
      map(_repository_car_merge_project_metadata(_, projects)).
      sortBy(_.artifactid)
    val diagnostics = discovery.diagnostics.map(_repository_car_diagnostic) ++
      _repository_car_diagnostics(config, entries, projects)
    RepositoryCarIndex(entries, diagnostics)
  }

  private[bok] def _repository_catalog_discovery(
    config: BuildConfig,
    projects: Vector[CozyBokProjectPublisher.ResolvedBokProject],
    kind: String
  ): RepositoryCatalogDiscovery =
    _repository_index_catalog_sources(config, kind).getOrElse(
      _repository_catalog_fallback_discovery(config, projects, kind)
    )

  private def _repository_catalog_fallback_discovery(
    config: BuildConfig,
    projects: Vector[CozyBokProjectPublisher.ResolvedBokProject],
    kind: String
  ): RepositoryCatalogDiscovery = {
    val fallback =
      _repository_catalog_paths(config, kind).flatMap(_read_repository_catalog(config, kind, _)) ++
        _project_repository_catalog_sources(config, projects, kind)
    RepositoryCatalogDiscovery(_deduplicate_repository_catalog_sources(config, kind, fallback), Vector.empty)
  }

  private def _project_repository_catalog_sources(
    config: BuildConfig,
    projects: Vector[CozyBokProjectPublisher.ResolvedBokProject],
    kind: String
  ): Vector[RepositoryCatalogSource] =
    projects.flatMap(_.sie.toVector.flatMap(_.artifacts)).filter(_.kind == kind).map { artifact =>
      _repository_catalog_source(config, artifact.path, artifact.catalog)
    }

  private def _repository_index_catalog_sources(
    config: BuildConfig,
    kind: String
  ): Option[RepositoryCatalogDiscovery] = {
    val repositoryroot = config.publication.repositoryPath(config.project).toAbsolutePath.normalize
    val indexpath = repositoryroot.resolve("catalog/index.json")
    if (!_repository_path_admitted(repositoryroot, indexpath, directory = false)) None
    else Some {
      try {
        val index = _root_.cozy.archive.ComponentRepositoryIndex.load(indexpath)
        val attempts = index.artifacts.filter(_.kind == kind).map { entry =>
          val catalogpath = indexpath.getParent.resolve(entry.catalog).normalize
          val catalogroot = repositoryroot.resolve("catalog").toAbsolutePath.normalize
          if (
            !catalogpath.startsWith(catalogroot) ||
            !_repository_path_admitted(repositoryroot, catalogpath, directory = false)
          )
            Left(RepositoryCatalogDiagnostic("index-catalog-unavailable", entry.kind, entry.artifactId, Some(entry.catalog)))
          else try {
            val catalog = _load_repository_catalog(catalogpath)
            if (
              catalog.kind != entry.kind ||
              catalog.artifactId != entry.artifactId ||
              (entry.kind == "car" && (catalog.namespace != entry.namespace || catalog.id != entry.id)) ||
              catalog.status.getOrElse("active") != entry.status ||
              catalog.recommended != entry.recommended ||
              catalog.latestStable != entry.latestStable ||
              catalog.latestSnapshot != entry.latestSnapshot
            )
              Left(RepositoryCatalogDiagnostic("index-catalog-mismatch", entry.kind, entry.artifactId, Some(entry.catalog)))
            else Right(_repository_catalog_source(config, catalogpath, catalog))
          } catch {
            case NonFatal(_) => Left(RepositoryCatalogDiagnostic("index-catalog-invalid", entry.kind, entry.artifactId, Some(entry.catalog)))
          }
        }
        RepositoryCatalogDiscovery(
          attempts.collect { case Right(source) => source },
          attempts.collect { case Left(diagnostic) => diagnostic }
        )
      } catch {
        case NonFatal(_) => RepositoryCatalogDiscovery(
          Vector.empty,
          Vector(RepositoryCatalogDiagnostic("repository-index-invalid", kind, "*", Some("catalog/index.json")))
        )
      }
    }
  }

  private def _repository_car_diagnostic(diagnostic: RepositoryCatalogDiagnostic): RepositoryCarDiagnostic =
    RepositoryCarDiagnostic(diagnostic.code, diagnostic.artifactid, None, None, None, None, None)

  private def _deduplicate_repository_catalog_sources(
    config: BuildConfig,
    kind: String,
    sources: Vector[RepositoryCatalogSource]
  ): Vector[RepositoryCatalogSource] = {
    val publicrepository = config.publication.repositoryPath(config.project).toAbsolutePath.normalize
    sources.groupBy(_.catalog.artifactId).toVector.sortBy(_._1).map { case (artifactid, candidates) =>
      val catalogs = candidates.map(_.catalog).distinct
      if (catalogs.size > 1)
        RAISE.invalidArgumentFault(s"Conflicting repository ${kind.toUpperCase(Locale.ROOT)} catalogs for SIE artifact: ${artifactid}")
      candidates.distinct.sortBy { candidate =>
        val priority = if (candidate.repositoryroot == publicrepository) 0 else 1
        (priority, candidate.path.toAbsolutePath.normalize.toString)
      }.head
    }
  }

  private[bok] def _repository_catalog_source(
    config: BuildConfig,
    path: Path,
    catalog: _root_.cozy.archive.RepositoryArtifactCatalog
  ): RepositoryCatalogSource = {
    val normalizedpath = path.toAbsolutePath.normalize
    val kinddir = Iterator
      .iterate(Option(normalizedpath.getParent))(_.flatMap(x => Option(x.getParent)))
      .takeWhile(_.nonEmpty)
      .flatten
      .find { x =>
        Option(x.getFileName).exists(_.toString == catalog.kind) &&
        Option(x.getParent).flatMap(y => Option(y.getFileName)).exists(_.toString == "catalog")
      }
      .getOrElse(
        RAISE.invalidArgumentFault(s"Repository catalog must be under repository/catalog/${catalog.kind}: ${path}")
      )
    val catalogdir = kinddir.getParent
    val repositoryroot = catalogdir.getParent
    val projectroot = config.project.toAbsolutePath.normalize
    val sourcepath =
      if (normalizedpath.startsWith(projectroot))
        projectroot.relativize(normalizedpath).toString.replace(java.io.File.separatorChar, '/')
      else {
        val relative = repositoryroot.relativize(normalizedpath).toString.replace(java.io.File.separatorChar, '/')
        s"repository/${relative}"
      }
    RepositoryCatalogSource(normalizedpath, repositoryroot, sourcepath, catalog)
  }

  private[bok] def _repository_catalog_public_source(source: RepositoryCatalogSource): String = source.sourcepath

  private def _repository_car_merge_project_metadata(
    entry: RepositoryCarEntry,
    projects: Vector[CozyBokProjectPublisher.ResolvedBokProject]
  ): RepositoryCarEntry = {
    val relatedprojects = _repository_car_related_projects(entry, projects)
    entry.copy(
      tags = (entry.tags ++ relatedprojects.flatMap(_.tags)).distinct,
      terms = (entry.terms ++ relatedprojects.flatMap(_.terms)).distinct
    )
  }

  private def _repository_car_diagnostics(
    config: BuildConfig,
    entries: Vector[RepositoryCarEntry],
    projects: Vector[CozyBokProjectPublisher.ResolvedBokProject]
  ): Vector[RepositoryCarDiagnostic] = {
    val catalogdiagnostics = entries.flatMap { entry =>
      if (_repository_car_related_projects(entry, projects).nonEmpty)
        None
      else
        Some(RepositoryCarDiagnostic("catalog-without-project", entry.artifactid, None, None, None, None, None))
    }
    val archivemetadata = entries.flatMap { entry =>
      entry.versions.flatMap(_repository_car_archive_diagnostics(entry.artifactid, _))
    }
    val projectdiagnostics = projects.flatMap { project =>
      if (entries.exists(entry => _repository_car_related_projects(entry, Vector(project)).nonEmpty))
        None
      else
        Some(
          RepositoryCarDiagnostic(
            "project-without-catalog",
            project.module,
            None,
            None,
            None,
            Some(_project_relative_path(config.project, project.descriptorfile)),
            Some(project.title)
          )
        )
    }
    (catalogdiagnostics ++ archivemetadata ++ projectdiagnostics).
      sortBy(x => (x.code, x.artifactid, x.version.getOrElse(""), x.projectpath.getOrElse("")))
  }

  private def _repository_car_archive_diagnostics(
    artifactid: String,
    version: RepositoryCarVersion
  ): Vector[RepositoryCarDiagnostic] =
    if (!version.archiveavailable)
      Vector.empty
    else {
      def _diagnostic_(
        code: String,
        coordinate: Option[RepositoryCarMetadataCoordinate]
      ): RepositoryCarDiagnostic =
        RepositoryCarDiagnostic(
          code,
          artifactid,
          Some(version.version),
          coordinate.flatMap(_.name),
          coordinate.flatMap(_.version),
          None,
          None
        )
      val componentdescriptor = version.componentdescriptor match {
        case None => Vector(_diagnostic_("archive-without-component-descriptor", None))
        case Some(json) =>
          val coordinate = _repository_car_component_descriptor_coordinate(artifactid, version, json)
          if (_repository_car_coordinate_mismatches(version.version, coordinate))
            Vector(_diagnostic_("component-descriptor-coordinate-mismatch", coordinate))
          else
            Vector.empty
      }
      val abimanifest = version.abimanifest match {
        case None => Vector(_diagnostic_("archive-without-abi-manifest", None))
        case Some(json) =>
          val coordinate = _repository_car_abi_manifest_coordinate(artifactid, version, json)
          if (_repository_car_coordinate_mismatches(version.version, coordinate))
            Vector(_diagnostic_("abi-manifest-coordinate-mismatch", coordinate))
          else
            Vector.empty
      }
      componentdescriptor ++ abimanifest
    }

  private def _repository_car_component_descriptor_coordinate(
    artifactid: String,
    catalogversion: RepositoryCarVersion,
    json: Json
  ): Option[RepositoryCarMetadataCoordinate] = {
    val cursor = json.hcursor
    val component = cursor.downField("component")
    val namespace = component.get[String]("namespace").toOption
    val id = component.get[String]("id").toOption
    if (namespace.isDefined || id.isDefined) {
      val name = Some(s"${namespace.getOrElse("")}.${id.getOrElse("")}")
      val version = component.get[String]("version").toOption
      Some(RepositoryCarMetadataCoordinate(name, version, catalogversion.component.getOrElse(artifactid)))
    } else {
      val topname = cursor.get[String]("name").toOption
      val componentname = component.get[String]("name").toOption
      val name = topname.orElse(componentname)
      val version = cursor.get[String]("version").toOption.
        orElse(component.get[String]("version").toOption)
      val expectedname = if (topname.isDefined) artifactid else catalogversion.component.getOrElse(artifactid)
      if (name.isDefined || version.isDefined)
        Some(RepositoryCarMetadataCoordinate(name, version, expectedname))
      else
        None
    }
  }

  private def _repository_car_abi_manifest_coordinate(
    artifactid: String,
    catalogversion: RepositoryCarVersion,
    json: Json
  ): Option[RepositoryCarMetadataCoordinate] = {
    val cursor = json.hcursor
    val component = cursor.downField("component")
    val namespace = component.get[String]("namespace").toOption
    val id = component.get[String]("id").toOption
    if (namespace.isDefined || id.isDefined) {
      val name = Some(s"${namespace.getOrElse("")}.${id.getOrElse("")}")
      val version = component.get[String]("version").toOption
      Some(RepositoryCarMetadataCoordinate(name, version, catalogversion.component.getOrElse(artifactid)))
    } else {
      val car = cursor.downField("car")
      val name = car.get[String]("name").toOption
      val version = car.get[String]("version").toOption
      if (name.isDefined || version.isDefined)
        Some(RepositoryCarMetadataCoordinate(name, version, artifactid))
      else
        None
    }
  }

  private def _repository_car_coordinate_mismatches(
    version: String,
    coordinate: Option[RepositoryCarMetadataCoordinate]
  ): Boolean =
    coordinate.exists { value =>
      value.name.exists(_ != value.expectedname) || value.version.exists(_ != version)
    }

}
