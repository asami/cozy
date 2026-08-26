package cozy.bok

import org.goldenport.RAISE
import org.goldenport.cli.{Request => CliRequest}
import org.goldenport.cli.spec
import cozy.bok.scenario.ScenarioMetadata
import cozy.bok.BibliographyEntry._
import cozy.archive.CozyComponentKnowledgeCarrier
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

private[cozy] trait CozyBokRepositoryPages {
  self: CozyBokImplementation.type =>
  private[bok] def _repository_catalog_paths(config: BuildConfig, kind: String): Vector[Path] = {
    val repositoryroot = config.publication.repositoryPath(config.project).toAbsolutePath.normalize
    val dir = repositoryroot.resolve(s"catalog/$kind").toAbsolutePath.normalize
    if (!_repository_path_admitted(repositoryroot, dir, directory = true))
      Vector.empty
    else {
      val stream = Files.walk(dir)
      try {
        stream.iterator.asScala.toVector.
          filter(path => _repository_path_admitted(repositoryroot, path, directory = false)).
          filter(path => _is_repository_catalog_file(path)).
          sortBy(_.toAbsolutePath.normalize.toString)
      } finally {
        stream.close()
      }
    }
  }

  private def _is_repository_catalog_file(path: Path): Boolean = {
    val name = path.getFileName.toString.toLowerCase(Locale.ROOT)
    !name.contains(".model-metadata.") &&
      name != "component-knowledge.json" &&
      (name.endsWith(".yaml") || name.endsWith(".yml") || name.endsWith(".json"))
  }

  private[bok] def _repository_path_admitted(repositoryroot: Path, candidate: Path, directory: Boolean): Boolean = {
    val root = repositoryroot.toAbsolutePath.normalize
    val path = candidate.toAbsolutePath.normalize
    if (
      !path.startsWith(root) ||
      Files.isSymbolicLink(root) ||
      !Files.isDirectory(root, LinkOption.NOFOLLOW_LINKS)
    )
      false
    else {
      val segments = root.relativize(path).iterator.asScala
      var current = root
      var admitted = true
      while (segments.hasNext && admitted) {
        current = current.resolve(segments.next())
        admitted = !Files.isSymbolicLink(current)
      }
      admitted &&
        (if (directory) Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS) else Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS))
    }
  }

  private[bok] def _read_repository_catalog(config: BuildConfig, kind: String, path: Path): Option[RepositoryCatalogSource] =
    _load_repository_catalog(path) match {
      case catalog if catalog.kind == kind => Some(_repository_catalog_source(config, path, catalog))
      case _ => None
    }

  private[bok] def _load_repository_catalog(path: Path): _root_.cozy.archive.RepositoryArtifactCatalog = {
    _root_.cozy.RepositoryArtifactCatalog.load(path)
  }

  private[bok] def _repository_car_entry(
    source: RepositoryCatalogSource
  ): RepositoryCarEntry =
    RepositoryCarEntry(
      artifactid = source.catalog.artifactId,
      aliases = source.catalog.aliases,
      tags = source.catalog.tags,
      terms = source.catalog.terms,
      status = source.catalog.status,
      recommended = source.catalog.recommended,
      lateststable = source.catalog.latestStable,
      latestsnapshot = source.catalog.latestSnapshot,
      sourcepath = _repository_catalog_public_source(source),
      repositoryroot = source.repositoryroot,
      sidecars = _repository_car_sidecars(source.repositoryroot, source.path, source.catalog.artifactId),
      versions = source.catalog.versions.map(_repository_car_version(source.repositoryroot, source.catalog, _))
    )

  private def _repository_car_sidecars(
    repositoryroot: Path,
    catalogpath: Path,
    artifactid: String
  ): RepositoryCarSidecars = {
    val catalogdir = catalogpath.getParent
    def _sidecar_(suffix: String): Option[String] = {
      val path = catalogdir.resolve(s"${artifactid}${suffix}")
      if (Files.isRegularFile(path)) Some(_repository_car_public_path(repositoryroot, path)) else None
    }
    RepositoryCarSidecars(
      _sidecar_(".cml"),
      _sidecar_(".model-metadata.json"),
      _sidecar_(".model-metadata.yaml")
    )
  }

  private def _repository_car_public_path(repositoryroot: Path, path: Path): String = {
    val relative = repositoryroot.relativize(path.toAbsolutePath.normalize()).toString.replace(java.io.File.separatorChar, '/')
    s"repository/${relative}"
  }

  private[bok] def _copy_repository_car_sidecars(target: Path, index: RepositoryCarIndex): Unit = {
    index.entries.foreach { entry =>
      entry.sidecars.paths.distinct.foreach { publicpath =>
        val relative = publicpath.stripPrefix("repository/")
        _copy_if_exists(entry.repositoryroot.resolve(relative), target.resolve(publicpath))
      }
    }
  }

  private def _repository_car_version(
    repositoryroot: Path,
    catalog: _root_.cozy.archive.RepositoryArtifactCatalog,
    version: _root_.cozy.archive.RepositoryArtifactCatalogVersion
  ): RepositoryCarVersion = {
    val archive = _repository_car_archive_metadata(repositoryroot, catalog, version)
    RepositoryCarVersion(
      version = version.version,
      channel = version.channel,
      status = version.status,
      component = version.component,
      publishedat = version.publishedAt,
      file = version.file,
      runtimecncfminimum = version.runtime.flatMap(_.minimum),
      runtimecncfmaximum = version.runtime.flatMap(_.maximum),
      runtimecncftested = version.runtime.map(_.tested).getOrElse(Vector.empty),
      checksumsha256 = version.checksumSha256,
      componentdescriptor = archive.componentdescriptor,
      abimanifest = archive.abimanifest,
      componentknowledge = _repository_car_component_knowledge(repositoryroot, catalog, version, archive),
      links = RepositoryCarLinks.fromComponentDescriptor(archive.componentdescriptor),
      archiveavailable = archive.available
    )
  }

  private[bok] def _write_repository_car_archive_metadata(target: Path, index: RepositoryCarIndex): Unit =
    index.entries.foreach { entry =>
      entry.versions.foreach { version =>
        Vector(
          version.componentdescriptor.map("component-descriptor.json" -> _),
          version.abimanifest.map("abi-manifest.json" -> _)
        ).flatten.foreach { case (name, json) =>
          _write_text(target.resolve(version.metadataPublicPath(entry.artifactid, name)), json.spaces2 + "\n")
        }
        version.componentknowledge.foreach { knowledge =>
          _copy_if_exists(knowledge.source, target.resolve(knowledge.consumercontractpath))
        }
      }
    }

  /**
   * Keep a catalog consumer-contract transport version-scoped.  The BOK
   * builder copies only the sidecar that Cozy published for this exact CAR
   * release, after verifying it against the archive descriptor declaration.
   */
  private def _repository_car_component_knowledge(
    repositoryroot: Path,
    catalog: _root_.cozy.archive.RepositoryArtifactCatalog,
    version: _root_.cozy.archive.RepositoryArtifactCatalogVersion,
    archive: RepositoryCarArchiveMetadata
  ): Option[RepositoryCarComponentKnowledge] =
    archive.componentknowledgecarrier.map { carrier =>
      val coordinate = _root_.cozy.archive.CozyComponentReleaseCoordinateCodec.admit(
        catalog.namespace.getOrElse(""),
        catalog.id.getOrElse(""),
        version.version,
        "repository component knowledge"
      )
      val source = repositoryroot.resolve("catalog/car")
        .resolve(coordinate.groupPath)
        .resolve(coordinate.mavenArtifactId)
        .resolve(version.version)
        .resolve("component-knowledge.json")
        .toAbsolutePath.normalize
      if (!Files.isRegularFile(source))
        RAISE.invalidArgumentFault(
          s"repository.component-knowledge.transport.missing artifact=${catalog.artifactId} version=${version.version}"
        )
      _require_component_knowledge_transport(
        carrier,
        Files.readAllBytes(source),
        s"repository CAR ${catalog.artifactId} ${version.version}"
      )
      RepositoryCarComponentKnowledge(
        carrier,
        s"repository/car/${catalog.artifactId}/${version.version}/component-knowledge.json",
        source
      )
    }

  private def _require_component_knowledge_transport(
    carrier: Json,
    bytes: Array[Byte],
    label: String
  ): Unit = {
    val fields = carrier.asObject.getOrElse(
      RAISE.invalidArgumentFault(s"$label componentKnowledge declaration must be an object.")
    )
    val expected = Set("carrierSchema", "consumerContractSchema", "logicalPath", "sha256")
    if (fields.keys.toSet != expected)
      RAISE.invalidArgumentFault(s"$label componentKnowledge declaration must contain exactly the canonical carrier fields.")
    def _string(name: String): String =
      fields(name).flatMap(_.asString).getOrElse(
        RAISE.invalidArgumentFault(s"$label componentKnowledge.$name must be a string.")
      )
    if (_string("carrierSchema") != CozyComponentKnowledgeCarrier.CARRIER_SCHEMA)
      RAISE.invalidArgumentFault(s"$label componentKnowledge carrier schema is unsupported.")
    if (_string("consumerContractSchema") != CozyComponentKnowledgeCarrier.CONSUMER_CONTRACT_SCHEMA)
      RAISE.invalidArgumentFault(s"$label componentKnowledge consumer-contract schema is unsupported.")
    if (_string("logicalPath") != CozyComponentKnowledgeCarrier.ARCHIVE_LOGICAL_PATH)
      RAISE.invalidArgumentFault(s"$label componentKnowledge logical path is unsupported.")
    val expectedsha = _string("sha256")
    val actualsha = java.security.MessageDigest.getInstance("SHA-256").digest(bytes)
      .map(byte => f"${byte & 0xff}%02x").mkString
    if (!expectedsha.matches("[0-9a-f]{64}") || expectedsha != actualsha)
      RAISE.invalidArgumentFault(s"$label componentKnowledge transport digest does not match its archive declaration.")
  }

  private def _repository_car_archive_metadata(
    repositoryroot: Path,
    catalog: _root_.cozy.archive.RepositoryArtifactCatalog,
    version: _root_.cozy.archive.RepositoryArtifactCatalogVersion
  ): RepositoryCarArchiveMetadata =
    version.file.map(_repository_catalog_artifact_path(repositoryroot, _)).filter(Files.isRegularFile(_)).map { path =>
      val actualdigest = _root_.cozy.archive.RepositoryArtifactPublisher.sha256(path)
      val expecteddigest = version.checksumSha256.getOrElse("missing")
      if (actualdigest != expecteddigest)
        throw new IllegalArgumentException(
          s"component.repository.integrity.mismatch source=archive field=checksum.sha256 expected=${expecteddigest} actual=${actualdigest}"
        )
      val expectedkey = _root_.cozy.archive.CozyComponentReleaseCoordinateCodec.admit(
        catalog.namespace.getOrElse(""),
        catalog.id.getOrElse(""),
        version.version,
        "archive"
      ).integrityKey(actualdigest)
      val actualkey = version.integrityKey.getOrElse("missing")
      if (actualkey != expectedkey)
        throw new IllegalArgumentException(
          s"component.repository.integrity.mismatch source=archive field=integrityKey expected=${expectedkey} actual=${actualkey}"
        )
      val zip = new ZipFile(path.toFile)
      try {
        def _json_entry_(name: String): Option[Json] =
          Option(zip.getEntry(name)).map { entry =>
            val in = zip.getInputStream(entry)
            try {
              val text = new String(in.readAllBytes(), StandardCharsets.UTF_8)
              parser.parse(text).fold(
                error => RAISE.invalidArgumentFault(s"Invalid repository CAR metadata JSON: ${path}!/${name}: ${error.message}"),
                identity
              )
            } finally {
              in.close()
            }
          }
        val descriptor = _json_entry_("component-descriptor.json")
        RepositoryCarArchiveMetadata(
          descriptor,
          _json_entry_("abi-manifest.json"),
          descriptor.flatMap(_.hcursor.downField("componentKnowledge").focus),
          available = true
        )
      } finally {
        zip.close()
      }
    }.getOrElse(RepositoryCarArchiveMetadata(None, None, None, available = false))

  private def _repository_catalog_artifact_path(repositoryroot: Path, value: String): Path =
    repositoryroot.resolve(value.stripPrefix("repository/")).toAbsolutePath.normalize

  private[bok] def _repository_car_dashboard_body(
    config: BuildConfig,
    locale: String,
    page: Path,
    target: Path,
    index: RepositoryCarIndex
  ): String = {
    val rows =
      if (index.entries.isEmpty)
        s"""<p class="bok-card-muted">${_html_escape(_repository_car_empty(locale))}</p>"""
      else {
        val body = index.entries.map { entry =>
          val version = entry.effectiveVersion.getOrElse("-")
          val versions = entry.versions.size.toString
          val source = entry.sourcepath
          val aliases = if (entry.aliases.isEmpty) "-" else entry.aliases.mkString(", ")
          val href = _relative_href(page, target.resolve(entry.publicPath))
          s"""<tr>
             |  <td><a href="${_html_escape(href)}"><code>${_html_escape(entry.artifactid)}</code></a></td>
             |  <td>${_html_escape(version)}</td>
             |  <td>${_html_escape(versions)}</td>
             |  <td>${_html_escape(aliases)}</td>
             |  <td><code>${_html_escape(source)}</code></td>
             |</tr>""".stripMargin
        }.mkString("\n")
        s"""<div class="bok-project-table-wrap">
           |  <table class="table table-sm bok-project-cml-table">
           |    <thead><tr><th>CAR</th><th>${_html_escape(_repository_car_latest_label(locale))}</th><th>${_html_escape(_repository_car_versions_label(locale))}</th><th>${_html_escape(_repository_car_aliases_label(locale))}</th><th>${_html_escape(_repository_car_catalog_label(locale))}</th></tr></thead>
           |    <tbody>
           |${body}
           |    </tbody>
           |  </table>
           |</div>""".stripMargin
      }
    val diagnostics = _repository_car_diagnostics_html(locale, index.diagnostics)
    s"""<section class="bok-dashboard-shell bok-repository-car-dashboard" id="dashboard">
       |  ${_dashboard_hero(
              _repository_car_title(locale),
              _repository_car_description(locale),
              Vector(
                "CAR" -> index.entries.size.toString,
                _repository_car_versions_label(locale) -> index.entries.map(_.versions.size).sum.toString
              )
            )}
       |  <div class="bok-dashboard container-fluid bok-dashboard-command-center">
       |    <div class="row g-3">
       |      ${_dashboard_card("col-12", "bok-card-map bok-card-project-map", _repository_car_title(locale), rows, Vector("reader", "contributor", "project_manager"))}
       |      ${diagnostics}
       |    </div>
       |  </div>
       |</section>""".stripMargin
  }

  private def _repository_car_diagnostics_html(locale: String, diagnostics: Vector[RepositoryCarDiagnostic]): String =
    if (diagnostics.isEmpty)
      ""
    else {
      val items = diagnostics.map { diagnostic =>
        val subject = diagnostic.projecttitle.getOrElse(
          diagnostic.version.map(x => s"${diagnostic.artifactid} ${x}").getOrElse(diagnostic.artifactid)
        )
        val coordinate =
          if (diagnostic.metadataname.isDefined || diagnostic.metadataversion.isDefined)
            Some(
              s"${_repository_car_metadata_coordinate_label(locale)}: " +
                Vector(diagnostic.metadataname, diagnostic.metadataversion).flatten.mkString(" ")
            )
          else
            None
        val details = Vector(diagnostic.projectpath, coordinate).flatten.
          map(x => s" <code>${_html_escape(x)}</code>").mkString
        s"""<li><strong>${_html_escape(subject)}</strong><span>${_html_escape(_repository_car_diagnostic_message(locale, diagnostic.code))}${details}</span></li>"""
      }.mkString("\n")
      val body =
        s"""<ul class="bok-repository-car-diagnostic-list">
           |${items}
           |</ul>""".stripMargin
      _dashboard_card(
        "col-12",
        "bok-card-map bok-card-project-issues",
        _repository_car_diagnostics_label(locale),
        body,
        Vector("contributor", "project_manager")
      )
    }

  private[bok] def _project_repository_car_html(
    config: BuildConfig,
    locale: String,
    project: CozyBokProjectPublisher.ResolvedBokProject,
    page: Path,
    target: Path
  ): String =
    project.catalog.map { info =>
      val versions = info.catalog.versions.map { version =>
        val current = info.selectedversion.exists(_.version == version.version)
        val file = version.file.getOrElse("")
        val versionpage = target.resolve("repository").resolve("car").resolve(info.catalog.artifactId).resolve(s"${version.version}.html")
        val versionhref = _relative_href(page, versionpage)
        s"""<tr>
           |  <td><a href="${_html_escape(versionhref)}">${if (current) s"""<strong>${_html_escape(version.version)}</strong>""" else _html_escape(version.version)}</a></td>
           |  <td>${_html_escape(version.channel.getOrElse("-"))}</td>
           |  <td>${_html_escape(version.status.getOrElse("active"))}</td>
           |  <td><code>${_html_escape(file)}</code></td>
           |</tr>""".stripMargin
      }.mkString("\n")
      s"""<section class="bok-project-section bok-project-repository-cars" id="project-repository-cars">
         |  <div class="bok-project-section-head">
         |    <h2>${_html_escape(_repository_car_title(locale))}</h2>
         |    <p>${_html_escape(_repository_car_project_description(locale))}</p>
         |  </div>
         |  <div class="bok-project-table-wrap">
         |    <table class="table table-sm bok-project-cml-table">
         |      <thead><tr><th>${_html_escape(_repository_car_version_label(locale))}</th><th>${_html_escape(_repository_car_channel_label(locale))}</th><th>${_html_escape(_repository_car_status_label(locale))}</th><th>${_html_escape(_repository_car_file_label(locale))}</th></tr></thead>
         |      <tbody>
         |${versions}
         |      </tbody>
         |    </table>
         |  </div>
         |  <p class="bok-card-muted"><code>${_html_escape(_project_relative_path(config.project, info.path))}</code></p>
         |</section>""".stripMargin
    }.getOrElse("")

  private[bok] def _repository_car_module_body(
    config: BuildConfig,
    target: Path,
    page: Path,
    locale: String,
    entry: RepositoryCarEntry,
    projects: Vector[CozyBokProjectPublisher.ResolvedBokProject]
  ): String = {
    val relatedprojects = _repository_car_related_projects(entry, projects)
    val category = relatedprojects.headOption.map(project => _project_category(config, project))
    val archivemetadatarows = entry.effectiveVersion.
      flatMap(selected => entry.versions.find(_.version == selected)).
      map(_repository_car_archive_metadata_rows(target, page, locale, entry.artifactid, _)).
      getOrElse(Vector.empty)
    val versionrows = entry.versions.map { version =>
      val href = _relative_href(page, target.resolve(entry.versionPublicPath(version)))
      val selected = entry.effectiveVersion.contains(version.version)
      s"""<tr>
         |  <td><a href="${_html_escape(href)}">${if (selected) s"""<strong>${_html_escape(version.version)}</strong>""" else _html_escape(version.version)}</a></td>
         |  <td>${_html_escape(version.channel.getOrElse("-"))}</td>
         |  <td>${_html_escape(version.status.getOrElse("active"))}</td>
         |  <td><code>${_html_escape(version.file.getOrElse("-"))}</code></td>
         |</tr>""".stripMargin
    }.mkString("\n")
    s"""<section class="bok-project-section bok-repository-car-detail" id="repository-car-detail">
       |  ${_repository_car_properties_table(locale, Vector(
              _repository_car_catalog_label(locale) -> s"<code>${_html_escape(entry.sourcepath)}</code>",
              _repository_car_latest_label(locale) -> _html_escape(entry.effectiveVersion.getOrElse("-")),
              _repository_car_status_label(locale) -> _html_escape(entry.status.getOrElse("active")),
              _repository_car_aliases_label(locale) -> _html_escape(if (entry.aliases.isEmpty) "-" else entry.aliases.mkString(", ")),
              _repository_car_tags_label(locale) -> _repository_car_tag_links(target, page, entry.tags, category),
              _repository_car_terms_label(locale) -> _repository_car_term_links(config, target, page, entry.terms),
              _repository_car_sidecars_label(locale) -> _repository_car_sidecar_links(target, page, locale, entry.sidecars)
            ) ++ archivemetadatarows)}
       |  <h2>${_html_escape(_repository_car_versions_label(locale))}</h2>
       |  <div class="bok-project-table-wrap">
       |    <table class="table table-sm bok-project-cml-table">
       |      <thead><tr><th>${_html_escape(_repository_car_version_label(locale))}</th><th>${_html_escape(_repository_car_channel_label(locale))}</th><th>${_html_escape(_repository_car_status_label(locale))}</th><th>${_html_escape(_repository_car_file_label(locale))}</th></tr></thead>
       |      <tbody>
       |${versionrows}
       |      </tbody>
       |    </table>
       |  </div>
       |  ${_repository_car_related_projects_html(target, page, locale, relatedprojects)}
       |</section>""".stripMargin
  }

  private[bok] def _repository_car_version_body(
    config: BuildConfig,
    target: Path,
    page: Path,
    locale: String,
    entry: RepositoryCarEntry,
    version: RepositoryCarVersion,
    projects: Vector[CozyBokProjectPublisher.ResolvedBokProject]
  ): String = {
    val relatedprojects = _repository_car_related_projects(entry, projects)
    val category = relatedprojects.headOption.map(project => _project_category(config, project))
    val runtime =
      Vector(
        version.runtimecncfminimum.map(x => "minimum" -> x),
        version.runtimecncfmaximum.map(x => "maximum" -> x),
        if (version.runtimecncftested.isEmpty) None else Some("tested" -> version.runtimecncftested.mkString(", "))
      ).flatten.map { case (label, value) =>
        s"${_html_escape(label)}: ${_html_escape(value)}"
      }.mkString("<br>")
    s"""<section class="bok-project-section bok-repository-car-version" id="repository-car-version">
       |  ${_repository_car_properties_table(locale, Vector(
              _repository_car_catalog_label(locale) -> s"<code>${_html_escape(entry.sourcepath)}</code>",
              _repository_car_version_label(locale) -> _html_escape(version.version),
              _repository_car_channel_label(locale) -> _html_escape(version.channel.getOrElse("-")),
              _repository_car_status_label(locale) -> _html_escape(version.status.getOrElse("active")),
              _repository_car_component_label(locale) -> _html_escape(version.component.getOrElse("-")),
              _repository_car_published_at_label(locale) -> _html_escape(version.publishedat.getOrElse("-")),
              _repository_car_file_label(locale) -> s"<code>${_html_escape(version.file.getOrElse("-"))}</code>",
              _repository_car_runtime_label(locale) -> (if (runtime.isEmpty) "-" else runtime),
              _repository_car_checksum_label(locale) -> _html_escape(version.checksumsha256.getOrElse("-")),
              _repository_car_tags_label(locale) -> _repository_car_tag_links(target, page, entry.tags, category),
              _repository_car_terms_label(locale) -> _repository_car_term_links(config, target, page, entry.terms),
              _repository_car_sidecars_label(locale) -> _repository_car_sidecar_links(target, page, locale, entry.sidecars)
            ) ++ _repository_car_archive_metadata_rows(target, page, locale, entry.artifactid, version))}
       |  ${_repository_car_related_projects_html(target, page, locale, relatedprojects)}
       |</section>""".stripMargin
  }

  private[bok] def _repository_car_properties_table(locale: String, rows: Vector[(String, String)]): String = {
    val body = rows.map { case (label, value) =>
      s"""<tr><th>${_html_escape(label)}</th><td>${value}</td></tr>"""
    }.mkString("\n")
    s"""<div class="bok-project-table-wrap">
       |  <table class="table table-sm bok-project-cml-table">
       |    <tbody>
       |${body}
       |    </tbody>
       |  </table>
       |</div>""".stripMargin
  }

  private[bok] def _repository_car_related_projects_html(
    target: Path,
    page: Path,
    locale: String,
    projects: Vector[CozyBokProjectPublisher.ResolvedBokProject]
  ): String =
    if (projects.isEmpty)
      s"""<p class="bok-card-muted">${_html_escape(_repository_car_no_related_project(locale))}</p>"""
    else {
      val items = projects.map { project =>
        val href = _relative_href(page, target.resolve(project.publicationpath).resolve("index.html"))
        s"""<li><a href="${_html_escape(href)}">${_html_escape(project.title)}</a></li>"""
      }.mkString("\n")
      s"""<section class="bok-project-section bok-repository-car-projects" id="repository-car-projects">
         |  <h2>${_html_escape(_repository_car_related_project_label(locale))}</h2>
         |  <ul>
         |${items}
         |  </ul>
         |</section>""".stripMargin
    }

  private[bok] def _repository_car_related_projects(
    entry: RepositoryCarEntry,
    projects: Vector[CozyBokProjectPublisher.ResolvedBokProject]
  ): Vector[CozyBokProjectPublisher.ResolvedBokProject] =
    projects.filter { project =>
      project.module == entry.artifactid ||
        project.catalog.exists(_.catalog.artifactId == entry.artifactid) ||
        project.sie.exists(_.artifacts.exists(x => x.kind == "car" && x.artifactId == entry.artifactid))
    }.sortBy(_.publicationpath)

  private[bok] def _repository_car_title(locale: String): String =
    locale match {
      case "ja" => "CARリポジトリ"
      case _ => "Repository CARs"
    }

  private[bok] def _repository_car_description(locale: String): String =
    locale match {
      case "ja" => "repository/catalog/car で管理される公開済みCARを知識化した一覧です。"
      case _ => "Published CAR knowledge entries from repository/catalog/car."
    }

  private def _repository_car_project_description(locale: String): String =
    locale match {
      case "ja" => "このProjectに対応するrepository CAR catalogと公開versionです。"
      case _ => "Repository CAR catalog versions associated with this Project."
    }

  private[bok] def _repository_car_module_description(locale: String, entry: RepositoryCarEntry): String =
    locale match {
      case "ja" => s"${entry.artifactid} のCAR catalogと公開versionです。"
      case _ => s"CAR catalog and published versions for ${entry.artifactid}."
    }

  private[bok] def _repository_car_version_description(locale: String, entry: RepositoryCarEntry, version: RepositoryCarVersion): String =
    locale match {
      case "ja" => s"${entry.artifactid} ${version.version} の公開CAR version情報です。"
      case _ => s"Published CAR version information for ${entry.artifactid} ${version.version}."
    }

  private[bok] def _repository_car_empty(locale: String): String =
    locale match {
      case "ja" => "Repository CAR catalogはまだありません。"
      case _ => "No repository CAR catalog entries are available."
    }

  private def _repository_car_latest_label(locale: String): String =
    locale match {
      case "ja" => "代表version"
      case _ => "Selected version"
    }

  private[bok] def _repository_car_versions_label(locale: String): String =
    locale match {
      case "ja" => "Versions数"
      case _ => "Versions"
    }

  private[bok] def _repository_car_version_label(locale: String): String =
    locale match {
      case "ja" => "Version"
      case _ => "Version"
    }

  private[bok] def _repository_car_channel_label(locale: String): String =
    locale match {
      case "ja" => "チャネル"
      case _ => "Channel"
    }

  private[bok] def _repository_car_status_label(locale: String): String =
    locale match {
      case "ja" => "状態"
      case _ => "Status"
    }

  private[bok] def _repository_car_file_label(locale: String): String =
    locale match {
      case "ja" => "ファイル"
      case _ => "File"
    }

  private def _repository_car_aliases_label(locale: String): String =
    locale match {
      case "ja" => "別名"
      case _ => "Aliases"
    }

  private def _repository_car_tags_label(locale: String): String =
    locale match {
      case "ja" => "タグ"
      case _ => "Tags"
    }

  private def _repository_car_tag_links(
    target: Path,
    page: Path,
    tags: Vector[String],
    category: Option[String]
  ): String = {
    val links = tags.map(_tag_key(_, category)).filter(_.nonEmpty).distinct.map { key =>
      val tag = _tag_entry_from_usage(key, Vector.empty)
      val href = _relative_href(page, target.resolve(tag.publicpath))
      s"""<a class="bok-tag-chip" href="${_html_escape(href)}">${_html_escape(tag.effectiveTitle)}</a>"""
    }
    if (links.isEmpty) "-" else links.mkString(" ")
  }

  private def _repository_car_terms_label(locale: String): String =
    locale match {
      case "ja" => "用語"
      case _ => "Terms"
    }

  private def _repository_car_term_links(
    config: BuildConfig,
    target: Path,
    page: Path,
    references: Vector[String]
  ): String = {
    val terms = _terms(config)
    val links = references.distinct.map { reference =>
      terms.find(_term_reference_matches(reference, _)) match {
        case Some(term) =>
          val termhref = _relative_href(page, target.resolve(term.publicpath))
          val rdfhref = s"${_relative_href(page, target.resolve("rdf/index.html"))}?term=${_url_query_escape(term.id)}"
          s"""<span class="bok-repository-car-term"><a href="${_html_escape(termhref)}">${_html_escape(term.title)}</a><a class="bok-term-rdf-mini" href="${_html_escape(rdfhref)}">RDF</a></span>"""
        case None => _html_escape(reference)
      }
    }
    if (links.isEmpty) "-" else links.mkString(", ")
  }

  private[bok] def _repository_car_catalog_label(locale: String): String =
    locale match {
      case "ja" => "カタログ"
      case _ => "Catalog"
    }

  private def _repository_car_component_label(locale: String): String =
    locale match {
      case "ja" => "Component"
      case _ => "Component"
    }

  private def _repository_car_published_at_label(locale: String): String =
    locale match {
      case "ja" => "公開日時"
      case _ => "Published at"
    }

  private def _repository_car_runtime_label(locale: String): String =
    locale match {
      case "ja" => "Runtime"
      case _ => "Runtime"
    }

  private def _repository_car_checksum_label(locale: String): String =
    locale match {
      case "ja" => "Checksum"
      case _ => "Checksum"
    }

  private def _repository_car_sidecars_label(locale: String): String =
    locale match {
      case "ja" => "関連metadata"
      case _ => "Related metadata"
    }

  private def _repository_car_sidecar_links(
    target: Path,
    page: Path,
    locale: String,
    sidecars: RepositoryCarSidecars
  ): String = {
    val items = Vector(
      sidecars.cml.map("CML" -> _),
      sidecars.modelmetadatajson.map(_repository_car_model_metadata_label(locale, "JSON") -> _),
      sidecars.modelmetadatayaml.map(_repository_car_model_metadata_label(locale, "YAML") -> _)
    ).flatten.map { case (label, publicpath) =>
      val href = _relative_href(page, target.resolve(publicpath))
      s"""<a href="${_html_escape(href)}">${_html_escape(label)}</a>"""
    }
    if (items.isEmpty) "-" else items.mkString("<br>")
  }

  private def _repository_car_model_metadata_label(locale: String, format: String): String =
    locale match {
      case "ja" => s"モデルメタデータ (${format})"
      case _ => s"Model metadata (${format})"
    }

  private def _repository_car_archive_metadata_rows(
    target: Path,
    page: Path,
    locale: String,
    artifactid: String,
    version: RepositoryCarVersion
  ): Vector[(String, String)] =
    Vector(
      version.componentdescriptor.map(json =>
        _repository_car_component_descriptor_label(locale) -> _repository_car_archive_metadata_link(
          target,
          page,
          version.metadataPublicPath(artifactid, "component-descriptor.json"),
          _repository_car_component_descriptor_summary(json)
        )
      ),
      version.abimanifest.map(json =>
        _repository_car_abi_manifest_label(locale) -> _repository_car_archive_metadata_link(
          target,
          page,
          version.metadataPublicPath(artifactid, "abi-manifest.json"),
          _repository_car_abi_manifest_summary(json)
        )
      ),
      if (version.links.isEmpty) None else Some(
        _repository_car_runtime_links_label(locale) -> _repository_car_runtime_links(version.links)
      )
    ).flatten

  private def _repository_car_archive_metadata_link(
    target: Path,
    page: Path,
    publicpath: String,
    summary: String
  ): String = {
    val href = _relative_href(page, target.resolve(publicpath))
    s"""<a href="${_html_escape(href)}">${summary}</a>"""
  }

  private def _repository_car_runtime_links(links: RepositoryCarLinks): String = {
    val items = Vector(
      links.help.map("Help" -> _),
      links.manual.map("Manual" -> _),
      links.openapi.map("OpenAPI" -> _),
      links.mcp.map("MCP" -> _)
    ).flatten.map { case (label, href) =>
      s"""<a href="${_html_escape(href)}">${_html_escape(label)}</a>"""
    }
    items.mkString(" ")
  }

  private def _repository_car_runtime_links_label(locale: String): String =
    locale match {
      case "ja" => "コンポーネント公開面"
      case _ => "Component surfaces"
    }

  private def _repository_car_component_descriptor_summary(json: Json): String = {
    val cursor = json.hcursor
    val canonicalcomponent = cursor.downField("component")
    val namespace = canonicalcomponent.get[String]("namespace").toOption
    val id = canonicalcomponent.get[String]("id").toOption
    val (name, version, component) =
      if (namespace.isDefined || id.isDefined) {
        val identity = s"${namespace.getOrElse("")}.${id.getOrElse("")}"
        (identity, canonicalcomponent.get[String]("version").toOption.getOrElse("-"), identity)
      } else {
        val legacyname = cursor.get[String]("name").toOption.
          orElse(canonicalcomponent.get[String]("name").toOption).
          getOrElse("-")
        val legacyversion = cursor.get[String]("version").toOption.
          orElse(canonicalcomponent.get[String]("version").toOption).
          getOrElse("-")
        val legacycomponent = cursor.get[String]("component").toOption.
          orElse(cursor.get[String]("componentName").toOption).
          orElse(canonicalcomponent.get[String]("componentName").toOption).
          getOrElse(legacyname)
        (legacyname, legacyversion, legacycomponent)
      }
    val entitycount = cursor.downField("entities").as[Vector[Json]].toOption.map(_.size).getOrElse(0)
    _html_escape(s"${name} ${version} / ${component} / entities ${entitycount}")
  }

  private def _repository_car_abi_manifest_summary(json: Json): String = {
    val cursor = json.hcursor
    val car = cursor.downField("car")
    val component = cursor.downField("component")
    val abi = cursor.downField("abi")
    val exports = abi.downField("exports")
    val namespace = component.get[String]("namespace").toOption
    val id = component.get[String]("id").toOption
    val (name, version) =
      if (namespace.isDefined || id.isDefined)
        (s"${namespace.getOrElse("")}.${id.getOrElse("")}", component.get[String]("version").toOption.getOrElse("-"))
      else
        (car.get[String]("name").toOption.getOrElse("-"), car.get[String]("version").toOption.getOrElse("-"))
    val abiversion = abi.get[Int]("version").toOption.getOrElse(1)
    def _count_(field: String): Int = exports.downField(field).as[Vector[Json]].toOption.map(_.size).getOrElse(0)
    _html_escape(
      s"${name} ${version} / ABI ${abiversion} / components ${_count_("components")} / operations ${_count_("operations")} / entities ${_count_("entities")}"
    )
  }

  private def _repository_car_component_descriptor_label(locale: String): String =
    locale match {
      case "ja" => "コンポーネント記述子"
      case _ => "Component descriptor"
    }

  private def _repository_car_abi_manifest_label(locale: String): String =
    locale match {
      case "ja" => "ABIマニフェスト"
      case _ => "ABI manifest"
    }

  private def _repository_car_related_project_label(locale: String): String =
    locale match {
      case "ja" => "関連Project"
      case _ => "Related Projects"
    }

  private def _repository_car_no_related_project(locale: String): String =
    locale match {
      case "ja" => "関連Projectはまだありません。"
      case _ => "No related Project is available."
    }

  private def _repository_car_diagnostics_label(locale: String): String =
    locale match {
      case "ja" => "CARリポジトリ診断"
      case _ => "CAR Repository Diagnostics"
    }

  private def _repository_car_metadata_coordinate_label(locale: String): String =
    locale match {
      case "ja" => "metadata座標"
      case _ => "metadata coordinate"
    }

  private[bok] def _repository_car_diagnostic_message(locale: String, code: String): String =
    (locale, code) match {
      case ("ja", "catalog-without-project") => "公開CARに対応するProject定義がありません。"
      case ("ja", "project-without-catalog") => "Projectに対応する公開CAR catalogがありません。"
      case ("ja", "archive-without-component-descriptor") => "CARにcomponent-descriptor.jsonがありません。"
      case ("ja", "archive-without-abi-manifest") => "CARにabi-manifest.jsonがありません。"
      case ("ja", "component-descriptor-coordinate-mismatch") => "component descriptorの座標がcatalogと一致しません。"
      case ("ja", "abi-manifest-coordinate-mismatch") => "ABI manifestの座標がcatalogと一致しません。"
      case ("ja", "repository-index-invalid") => "Component Repository indexを読み込めません。"
      case ("ja", "index-catalog-unavailable") => "Component Repository indexが参照するcatalogを利用できません。"
      case ("ja", "index-catalog-invalid") => "Component Repository indexが参照するcatalogを読み込めません。"
      case ("ja", "index-catalog-mismatch") => "Component Repository indexとcatalogの内容が一致しません。"
      case (_, "catalog-without-project") => "The published CAR has no related Project definition."
      case (_, "project-without-catalog") => "The Project has no corresponding published CAR catalog."
      case (_, "archive-without-component-descriptor") => "The CAR does not contain component-descriptor.json."
      case (_, "archive-without-abi-manifest") => "The CAR does not contain abi-manifest.json."
      case (_, "component-descriptor-coordinate-mismatch") => "The component descriptor coordinate does not match the catalog."
      case (_, "abi-manifest-coordinate-mismatch") => "The ABI manifest coordinate does not match the catalog."
      case (_, "repository-index-invalid") => "The Component Repository index is invalid."
      case (_, "index-catalog-unavailable") => "The catalog referenced by the Component Repository index is unavailable."
      case (_, "index-catalog-invalid") => "The catalog referenced by the Component Repository index is invalid."
      case (_, "index-catalog-mismatch") => "The Component Repository index and catalog do not match."
      case _ => code
    }

}
