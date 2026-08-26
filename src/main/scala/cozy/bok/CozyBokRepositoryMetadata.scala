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

private[cozy] trait CozyBokRepositoryMetadata {
  self: CozyBokImplementation.type =>
  private[bok] final case class RepositoryCarIndex(
    entries: Vector[RepositoryCarEntry],
    diagnostics: Vector[RepositoryCarDiagnostic]
  ) {
    def toJsonString: String =
      Json.obj(
        "entries" -> entries.map(_.toJson).asJson,
        "diagnostics" -> diagnostics.map(_.toJson).asJson
      ).spaces2 + "\n"
  }

  private final case class ComponentReferenceIndex(
    kind: String,
    entries: Vector[ComponentReferenceEntry],
    diagnostics: Vector[RepositoryCatalogDiagnostic] = Vector.empty
  ) {
    def toJsonString: String =
      Json.obj(
        "schemaVersion" -> Json.fromString("cncf.component-reference-index.v1"),
        "kind" -> Json.fromString(kind),
        "entries" -> entries.map(_.toJson).asJson,
        "diagnostics" -> diagnostics.map(_.toJson).asJson
      ).spaces2 + "\n"
  }

  private final case class ComponentReferenceEntry(
    name: String,
    title: String,
    kind: String,
    aliases: Vector[String],
    tags: Vector[String],
    terms: Vector[String],
    status: Option[String],
    recommended: Option[String],
    lateststable: Option[String],
    latestsnapshot: Option[String],
    sourcepath: String,
    publicpath: String,
    versions: Vector[ComponentReferenceVersion]
  ) {
    def toJson: Json =
      Json.obj(
        "name" -> Json.fromString(name),
        "title" -> Json.fromString(title),
        "kind" -> Json.fromString(kind),
        "aliases" -> aliases.asJson,
        "tags" -> tags.asJson,
        "terms" -> terms.asJson,
        "status" -> status.asJson,
        "recommended" -> recommended.asJson,
        "latest_stable" -> lateststable.asJson,
        "latest_snapshot" -> latestsnapshot.asJson,
        "source_path" -> Json.fromString(sourcepath),
        "public_path" -> Json.fromString(publicpath),
        "versions" -> versions.map(_.toJson).asJson
      )
  }

  private final case class ComponentReferenceVersion(
    version: String,
    channel: Option[String],
    status: Option[String],
    publishedat: Option[String],
    file: Option[String]
  ) {
    def toJson: Json =
      Json.obj(
        "version" -> Json.fromString(version),
        "channel" -> channel.asJson,
        "status" -> status.asJson,
        "published_at" -> publishedat.asJson,
        "file" -> file.asJson
      )
  }

  private[bok] final case class RepositoryCarDiagnostic(
    code: String,
    artifactid: String,
    version: Option[String],
    metadataname: Option[String],
    metadataversion: Option[String],
    projectpath: Option[String],
    projecttitle: Option[String]
  ) {
    def toJson: Json =
      Json.obj(
        "code" -> Json.fromString(code),
        "artifact_id" -> Json.fromString(artifactid),
        "version" -> version.asJson,
        "metadata_name" -> metadataname.asJson,
        "metadata_version" -> metadataversion.asJson,
        "project_path" -> projectpath.asJson,
        "project_title" -> projecttitle.asJson
      )
  }

  private[bok] final case class RepositoryCatalogSource(
    path: Path,
    repositoryroot: Path,
    sourcepath: String,
    catalog: _root_.cozy.archive.RepositoryArtifactCatalog
  )

  private[bok] final case class RepositoryCatalogDiagnostic(
    code: String,
    kind: String,
    artifactid: String,
    catalog: Option[String]
  ) {
    def toJson: Json =
      Json.obj(
        "code" -> Json.fromString(code),
        "kind" -> Json.fromString(kind),
        "artifact_id" -> Json.fromString(artifactid),
        "catalog" -> catalog.asJson
      )
  }

  private[bok] final case class RepositoryCatalogDiscovery(
    sources: Vector[RepositoryCatalogSource],
    diagnostics: Vector[RepositoryCatalogDiagnostic]
  )

  private[bok] final case class RepositoryCarEntry(
    artifactid: String,
    aliases: Vector[String],
    tags: Vector[String],
    terms: Vector[String],
    status: Option[String],
    recommended: Option[String],
    lateststable: Option[String],
    latestsnapshot: Option[String],
    sourcepath: String,
    repositoryroot: Path,
    sidecars: RepositoryCarSidecars,
    versions: Vector[RepositoryCarVersion]
  ) {
    def title: String = artifactid
    def publicPath: String = s"repository/car/${artifactid}/index.html"
    def versionPublicPath(version: RepositoryCarVersion): String =
      s"repository/car/${artifactid}/${version.version}.html"
    def effectiveVersion: Option[String] =
      recommended.orElse(lateststable).orElse(latestsnapshot).orElse(versions.headOption.map(_.version))
    def toJson: Json =
      Json.obj(
        "artifact_id" -> Json.fromString(artifactid),
        "aliases" -> aliases.asJson,
        "tags" -> tags.asJson,
        "terms" -> terms.asJson,
        "status" -> status.asJson,
        "recommended" -> recommended.asJson,
        "latest_stable" -> lateststable.asJson,
        "latest_snapshot" -> latestsnapshot.asJson,
        "source_path" -> Json.fromString(sourcepath),
        "sidecars" -> sidecars.toJson,
        "versions" -> versions.map(_.toJson).asJson
      )
    def toJsonString: String = toJson.spaces2 + "\n"
  }

  private[bok] final case class RepositoryCarSidecars(
    cml: Option[String],
    modelmetadatajson: Option[String],
    modelmetadatayaml: Option[String]
  ) {
    def paths: Vector[String] = Vector(cml, modelmetadatajson, modelmetadatayaml).flatten
    def toJson: Json =
      Json.obj(
        "cml" -> cml.asJson,
        "model_metadata_json" -> modelmetadatajson.asJson,
        "model_metadata_yaml" -> modelmetadatayaml.asJson
      )
  }

  private[bok] final case class RepositoryCarVersion(
    version: String,
    channel: Option[String],
    status: Option[String],
    component: Option[String],
    publishedat: Option[String],
    file: Option[String],
    runtimecncfminimum: Option[String],
    runtimecncfmaximum: Option[String],
    runtimecncftested: Vector[String],
    checksumsha256: Option[String],
    componentdescriptor: Option[Json],
    abimanifest: Option[Json],
    componentknowledge: Option[RepositoryCarComponentKnowledge],
    links: RepositoryCarLinks,
    archiveavailable: Boolean
  ) {
    def metadataPublicPath(artifactid: String, name: String): String =
      s"repository/car/${artifactid}/${version}/${name}"

    def toJson: Json =
      Json.obj(
        "version" -> Json.fromString(version),
        "channel" -> channel.asJson,
        "status" -> status.asJson,
        "component" -> component.asJson,
        "published_at" -> publishedat.asJson,
        "file" -> file.asJson,
        "runtime" -> Json.obj(
          "cncf" -> Json.obj(
            "minimum" -> runtimecncfminimum.asJson,
            "maximum" -> runtimecncfmaximum.asJson,
            "tested" -> runtimecncftested.asJson
          )
        ),
        "checksum" -> Json.obj("sha256" -> checksumsha256.asJson),
        "component_descriptor" -> componentdescriptor.asJson,
        "abi_manifest" -> abimanifest.asJson,
        "component_knowledge" -> componentknowledge.map(_.toJson).asJson,
        "links" -> links.toJson
      )
  }

  /**
   * A version-scoped, digest-bound consumer-contract sidecar.  `source` is
   * build-only and is never serialized into published catalog metadata.
   */
  private[bok] final case class RepositoryCarComponentKnowledge(
    carrier: Json,
    consumercontractpath: String,
    source: Path
  ) {
    def toJson: Json =
      Json.obj(
        "carrier" -> carrier,
        "consumer_contract" -> Json.fromString(consumercontractpath)
      )
  }

  private[bok] final case class RepositoryCarLinks(
    help: Option[String],
    manual: Option[String],
    openapi: Option[String],
    mcp: Option[String]
  ) {
    def isEmpty: Boolean = Vector(help, manual, openapi, mcp).flatten.isEmpty
    def toJson: Json =
      Json.obj(
        "help" -> help.asJson,
        "manual" -> manual.asJson,
        "openapi" -> openapi.asJson,
        "mcp" -> mcp.asJson
      )
  }

  private[bok] object RepositoryCarLinks {
    val empty = RepositoryCarLinks(None, None, None, None)

    def fromComponentDescriptor(descriptor: Option[Json]): RepositoryCarLinks =
      descriptor.map { json =>
        val links = json.hcursor.downField("links")
        RepositoryCarLinks(
          _safe_declared_link(links.get[String]("help").toOption),
          _safe_declared_link(links.get[String]("manual").toOption),
          _safe_declared_link(links.get[String]("openapi").toOption),
          _safe_declared_link(links.get[String]("mcp").toOption)
        )
      }.getOrElse(empty)

    private def _safe_declared_link(link: Option[String]): Option[String] =
      link.map(_.trim).filter { value =>
        value.startsWith("https://") ||
          value.startsWith("http://") ||
          (value.startsWith("/") && !value.startsWith("//"))
      }
  }

  private[bok] final case class RepositoryCarArchiveMetadata(
    componentdescriptor: Option[Json],
    abimanifest: Option[Json],
    componentknowledgecarrier: Option[Json],
    available: Boolean
  )

  private[bok] final case class RepositoryCarMetadataCoordinate(
    name: Option[String],
    version: Option[String],
    expectedname: String
  )

  private[bok] def _write_repository_car_metadata(config: BuildConfig): Unit = {
    val index = _repository_car_index(config)
    _write_text(
      config.doxsitePath.resolve("metadata/repository/car/index.json"),
      index.toJsonString
    )
    index.entries.foreach { entry =>
      _write_text(
        config.doxsitePath.resolve("metadata/repository/car").resolve(s"${entry.artifactid}.json"),
        entry.toJsonString
      )
    }
  }

  private[bok] def _write_component_reference_metadata(config: BuildConfig): Unit = {
    val indexes = Vector(
      _car_component_reference_index(config),
      _sar_component_reference_index(config)
    )
    indexes.filter(index => index.entries.nonEmpty || index.diagnostics.nonEmpty).foreach { index =>
      _write_text(
        config.doxsitePath.resolve("metadata/cncf/component-references").resolve(s"${index.kind}.json"),
        index.toJsonString
      )
    }
  }

  private def _car_component_reference_index(config: BuildConfig): ComponentReferenceIndex = {
    val repositoryentries = _repository_car_index(config).entries.map(_repository_car_component_reference_entry)
    val projectentries = _safe_resolved_project_packages(config).map(_project_car_component_reference_entry(config, _))
    ComponentReferenceIndex("car", _merge_car_component_reference_entries(repositoryentries, projectentries))
  }

  private def _repository_car_component_reference_entry(entry: RepositoryCarEntry): ComponentReferenceEntry =
    ComponentReferenceEntry(
      name = entry.artifactid,
      title = entry.title,
      kind = "car",
      aliases = entry.aliases,
      tags = entry.tags,
      terms = entry.terms,
      status = entry.status,
      recommended = entry.recommended,
      lateststable = entry.lateststable,
      latestsnapshot = entry.latestsnapshot,
      sourcepath = entry.sourcepath,
      publicpath = entry.publicPath,
      versions = entry.versions.map { version =>
        ComponentReferenceVersion(
          version.version,
          version.channel,
          version.status,
          version.publishedat,
          version.file
        )
      }
    )

  private def _project_car_component_reference_entry(
    config: BuildConfig,
    project: CozyBokProjectPublisher.ResolvedBokProject
  ): ComponentReferenceEntry = {
    val issnapshot = project.version.contains("SNAPSHOT")
    ComponentReferenceEntry(
      name = project.name,
      title = project.title,
      kind = "car",
      aliases = Vector.empty,
      tags = project.tags,
      terms = project.terms,
      status = None,
      recommended = Some(project.version),
      lateststable = if (issnapshot) None else Some(project.version),
      latestsnapshot = if (issnapshot) Some(project.version) else None,
      sourcepath = _project_component_reference_source_path(config, project),
      publicpath = s"${project.publicationpath}/index.html",
      versions = Vector(
        ComponentReferenceVersion(
          project.version,
          Some(if (issnapshot) "snapshot" else "stable"),
          None,
          None,
          project.catalog.flatMap(_.selectedversion).flatMap(_.file)
        )
      )
    )
  }

  private def _merge_car_component_reference_entries(
    repositoryentries: Vector[ComponentReferenceEntry],
    projectentries: Vector[ComponentReferenceEntry]
  ): Vector[ComponentReferenceEntry] = {
    val repositorynames = repositoryentries.map(_.name).toSet
    val projectonly = projectentries.filterNot(entry => repositorynames.contains(entry.name))
    val duplicateprojectnames = projectonly.groupBy(_.name).collect {
      case (name, entries) if entries.size > 1 => name
    }.toVector.sorted
    if (duplicateprojectnames.nonEmpty)
      RAISE.invalidArgumentFault(
        s"Conflicting BoK CAR project component-reference identities: ${duplicateprojectnames.mkString(", ")}"
      )
    (repositoryentries ++ projectonly).sortBy(entry => (entry.name, entry.sourcepath))
  }

  private def _project_component_reference_source_path(
    config: BuildConfig,
    project: CozyBokProjectPublisher.ResolvedBokProject
  ): String = {
    val projectroot = config.project.toAbsolutePath.normalize
    val descriptor = project.descriptorfile.toAbsolutePath.normalize
    if (descriptor.startsWith(projectroot))
      projectroot.relativize(descriptor).toString.replace(java.io.File.separatorChar, '/')
    else
      project.packagedir.toString
  }

  private def _sar_component_reference_index(config: BuildConfig): ComponentReferenceIndex = {
    val projects = _safe_resolved_project_packages(config)
    val discovery = _repository_catalog_discovery(config, projects, "sar")
    val entries = discovery.sources.map { source =>
      val catalog = source.catalog
      ComponentReferenceEntry(
        name = catalog.artifactId,
        title = catalog.artifactId,
        kind = "sar",
        aliases = catalog.aliases,
        tags = catalog.tags,
        terms = catalog.terms,
        status = catalog.status,
        recommended = catalog.recommended,
        lateststable = catalog.latestStable,
        latestsnapshot = catalog.latestSnapshot,
        sourcepath = source.sourcepath,
        publicpath = s"repository/sar/${catalog.artifactId}/index.html",
        versions = catalog.versions.map { version =>
          ComponentReferenceVersion(
            version.version,
            version.channel,
            version.status,
            version.publishedAt,
            version.file
          )
        }
      )
    }
    ComponentReferenceIndex("sar", entries, discovery.diagnostics)
  }

}
