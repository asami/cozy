package cozy.publication

import org.goldenport.RAISE
import org.goldenport.value._
import cozy.config.CozyProjectYamlConfig
import cozy.runtime.CozyCliArgs
import play.api.libs.json._
import org.goldenport.cli.spec
import java.nio.charset.StandardCharsets
import java.nio.channels.FileChannel
import java.nio.file.attribute.BasicFileAttributes
import java.nio.file.{AtomicMoveNotSupportedException, FileAlreadyExistsException, Files, LinkOption, Path, Paths, StandardCopyOption, StandardOpenOption}
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import scala.util.Try
import scala.util.control.NonFatal
import scala.collection.JavaConverters._
import scala.sys.process._

/*
 * @since   May. 20, 2026
 *  version Jun.  8, 2026
 *  version Jun. 19, 2026
 * @version Aug. 12, 2026
 * @author  ASAMI, Tomoharu
 */
private[cozy] object CozyPublicationCompiler {
  private val _schema = "cozy.publish-project.v1"
  private val _valid_kinds = Set("car", "sar", "sample-single", "sample-multi", "maven-repository")
  private val _default_excluded_segments = Set(
    "target",
    ".git",
    ".bsp",
    ".bloop",
    ".metals",
    ".idea",
    ".cache",
    ".vscode",
    ".scala-build",
    "car.d",
    "component.d",
    "component-repository.d",
    "tmprepo",
    "repository.d"
  )
  private val _slug_pattern = "^[a-z0-9][a-z0-9-]*$".r

  final case class ProjectMetadata(
    name: String,
    title: String,
    kind: String,
    publicationPath: Option[String],
    descriptiveAttributes: DescriptiveAttributes,
    summary: Option[String],
    description: Option[String],
    organization: String,
    version: String,
    scalaVersion: String,
    sbtVersion: String,
    buildSettings: BuildSettings
  )
  final case class BuildSettings(
    cozyPlugin: Boolean,
    cozyPackaging: Option[String],
    cncfVersion: Option[String],
    cncfDependency: Boolean,
    sbtCozyPlugin: Boolean
  )
  final case class SourceFile(path: String, size: Long, sha256: String)
  final case class ArtifactFile(
    layer: String,
    artifactType: String,
    module: Option[String],
    sampleName: Option[String],
    version: String,
    extension: String,
    path: String,
    name: String
  )
  final case class SamplePublication(
    name: String,
    title: String,
    descriptiveAttributes: DescriptiveAttributes,
    summary: Option[String],
    description: Option[String],
    directory: String,
    version: String,
    root: Path,
    files: Vector[SourceFile]
  )
  final case class Publication(
    project: ProjectMetadata,
    pages: Vector[PublicationPage],
    sourceManifestEnabled: Boolean,
    sourceFiles: Vector[SourceFile],
    samples: Vector[SamplePublication],
    repositoryModules: Vector[String]
  )
  final case class PublicationPage(
    path: String,
    title: DescriptiveAttributes.Text,
    descriptiveAttributes: DescriptiveAttributes
  )
  final case class ArticleRelation(path: String, role: String, title: Option[String])

  /*
   * The immutable input to the one-file publication-bundle writer.  Keeping
   * the source/provenance inputs here lets a caller prepare metadata before a
   * registry transaction without creating a second lock domain.
   */
  private[cozy] final case class MetadataPublication(
    name: String,
    publicationPath: Option[String],
    projectDir: Path,
    entries: Vector[(String, JsValue)]
  )

  private[publication] final case class SiteRootEvidence(
    lexical: Path,
    real: Path,
    fileKey: AnyRef
  )

  def publish(args: List[String]): Unit = {
    val projectdir = _project_dir(args)
    if (!Files.isDirectory(projectdir))
      RAISE.invalidArgumentFault(s"Project directory does not exist: ${projectdir}")
    if (!Files.isRegularFile(projectdir.resolve("build.sbt")))
      RAISE.invalidArgumentFault(s"Not an sbt project directory: ${projectdir}")

    val config = CozyProjectYamlConfig.loadOperationDefaults(projectdir)
    val savedir = _publication_output(projectdir, args, config)
    val publication = _compile(projectdir, savedir, args, config)
    _write(publication, savedir, projectdir)
  }

  private def _compile(projectdir: Path, savedir: Path, args: List[String], config: CozyProjectYamlConfig.Config): Publication = {
    val buildsbt = Files.readString(projectdir.resolve("build.sbt"), StandardCharsets.UTF_8)
    val publicmetadata = _public_metadata(projectdir)
    val cliname = _value(args, "name")
    val publicname = _metadata_value(publicmetadata, "name")
    val configname = config.value("publication.name")
    val rawname = cliname.orElse(publicname).orElse(configname).orElse(_sbt_setting(buildsbt, "name"))
    val name = rawname match {
      case Some(x) if cliname.nonEmpty || publicname.nonEmpty || configname.nonEmpty => _validate_name(x, "publication name")
      case Some(x) => _slugify(x)
      case None => _slugify(projectdir.getFileName.toString)
    }
    if (name.isEmpty)
      RAISE.invalidArgumentFault("Publication name is empty after slug normalization")
    val title = _value(args, "title").orElse(_metadata_value(publicmetadata, "title")).orElse(config.value("publication.title")).orElse(_sbt_setting(buildsbt, "name")).getOrElse(name)
    val publicationpath = _value(args, "path").orElse(_metadata_value(publicmetadata, "path")).orElse(config.value("publication.path")).map(_validate_publication_path)
    val descriptiveattributes = _descriptive_attributes(projectdir, args, publicmetadata, config)
    val summary = descriptiveattributes.summary.default
    val description = descriptiveattributes.description.default
    val organization = _value(args, "organization").orElse(_sbt_setting(buildsbt, "organization")).getOrElse("")
    val version = _value(args, "version").orElse(_sbt_setting(buildsbt, "version")).getOrElse("")
    val scalaversion = _value(args, "scala-version").orElse(_sbt_setting(buildsbt, "scalaVersion")).getOrElse("")
    val sbtversion = _value(args, "sbt-version").orElse(_sbt_version(projectdir)).getOrElse("")
    val buildsettings = _build_settings(buildsbt)
    val samplesdir = _config_path(projectdir, config.value("publication.samples_dir")).getOrElse(projectdir.resolve("samples"))
    val kind = _value(args, "kind").orElse(_metadata_value(publicmetadata, "kind")).orElse(config.value("publication.kind")).map(_.trim).filter(_.nonEmpty).getOrElse(_detect_kind(projectdir, buildsbt, samplesdir))
    if (!_valid_kinds.contains(kind))
      RAISE.invalidArgumentFault(s"Invalid --kind: ${kind}. Expected one of: ${_valid_kinds.toVector.sorted.mkString(", ")}")
    val sourcemanifestenabled = config.boolean("publication.source_manifest.enabled").getOrElse(true)
    val excludes = _default_excluded_segments ++ config.list("publication.source_manifest.excludes")

    val project = ProjectMetadata(
      name = name,
      title = title,
      kind = kind,
      publicationPath = publicationpath,
      descriptiveAttributes = descriptiveattributes,
      summary = summary,
      description = description,
      organization = organization,
      version = version,
      scalaVersion = scalaversion,
      sbtVersion = sbtversion,
      buildSettings = buildsettings
    )
    val sourcefiles =
      if (sourcemanifestenabled) _source_manifest(projectdir, savedir, excludes)
      else Vector.empty
    val samples =
      if (kind == "sample-multi")
        _sample_publications(samplesdir, savedir, project.version, excludes)
      else
        Vector.empty

    val repositorymodules = config.list("warehouse.repository_artifacts.modules") match {
      case Vector() => Vector(project.name)
      case xs => xs.toVector
    }

    Publication(project, _publication_pages(publicmetadata), sourcemanifestenabled, sourcefiles, samples, repositorymodules)
  }

  private def _publication_pages(metadata: CozyProjectYamlConfig.Config): Vector[PublicationPage] =
    metadata.publicationPageJsons.flatMap { json =>
      val path = json.hcursor.downField("path").as[String].toOption.map(_.trim).filter(_.nonEmpty)
      path.map { p =>
        PublicationPage(
          path = _validate_publication_path(p),
          title = DescriptiveAttributes.textFromJson(json, "title"),
          descriptiveAttributes = DescriptiveAttributes.fromJson(json)
        )
      }
    }

  def unpublish(args: List[String]): Unit = {
    val savedir = _required_path(args, "save")
    val name = _value(args, "name").map(_validate_name(_, "publication name")).getOrElse(RAISE.invalidArgumentFault("Missing --name"))
    PublicationRegistry.remove(savedir, name)
  }

  def publishMavenRepository(args: List[String]): Unit = {
    val repositorydir = _project_dir(args)
    if (!Files.isDirectory(repositorydir))
      RAISE.invalidArgumentFault(s"Maven repository directory does not exist: ${repositorydir}")
    val savedir = _required_path(args, "save")
    val name = _value(args, "name").map(_validate_name(_, "publication name")).getOrElse("maven-repository")
    val title = _value(args, "title").getOrElse("Maven Repository")
    val publicationpath = _value(args, "path").map(_validate_publication_path)
    val coordinates = _value(args, "maven-coordinates")
    val metadata = Json.obj(
      "schema" -> _schema,
      "type" -> "project-metadata",
      "project" -> Json.obj(
        "name" -> name,
        "title" -> title,
        "kind" -> "maven-repository",
        "buildSettings" -> _build_settings_json(BuildSettings(
          cozyPlugin = false,
          cozyPackaging = None,
          cncfVersion = None,
          cncfDependency = false,
          sbtCozyPlugin = false
        ))
      ),
      "publication" -> (Json.obj() ++ publicationpath.map(x => Json.obj("path" -> x)).getOrElse(Json.obj()))
    )
    PublicationRegistry.publishMetadata(
      root = savedir,
      name = name,
      publicationPath = publicationpath,
      projectDir = repositorydir,
      entries = Vector(s"metadata/projects/${name}/metadata.json" -> metadata)
    )
    CozyWarehouseIndexer.publishMaven(
      warehousedir = repositorydir,
      savedir = savedir,
      name = name,
      title = title,
      coordinates = coordinates
    )
  }

  def registerMetadata(root: Path, name: String, entries: Vector[(String, JsValue)]): Unit =
    PublicationRegistry.registerMetadata(root, name, entries.map {
      case (path, json) => PublicationBundleEntry(path, _publication_bundle_key(path), json)
    })

  private[publication] def replaceMetadata(
    root: Path,
    name: String,
    entries: Vector[(String, JsValue)],
    removePrefixes: Vector[String],
    expectedBundleDigests: Map[String, String] = Map.empty
  ): Unit =
    PublicationRegistry.replaceMetadata(
      root,
      name,
      entries.map { case (path, json) => PublicationBundleEntry(path, _publication_bundle_key(path), json) },
      removePrefixes,
      expectedBundleDigests
    )

  /*
   * Article-media registration holds one registry transaction across its
   * complete snapshot/preflight/replacement lifecycle.  The capability is
   * opaque outside this compiler: it is made only while the one existing
   * registry lock is owned, and every operation proves its owner thread and
   * lifetime before touching the filesystem.
   */
  private[publication] sealed trait RegistryLockCapability {
    private[publication] def realRoot: Path
    private[publication] def validateRootEvidence(): Unit
    private[publication] def validateSnapshot(expectedBundleDigests: Map[String, String]): Unit
    private[publication] def replaceMetadata(
      name: String,
      entries: Vector[(String, JsValue)],
      removePrefixes: Vector[String],
      expectedBundleDigests: Map[String, String],
      createArticleMedia: Boolean
    ): Unit
    private[publication] def publishMetadata(publication: MetadataPublication): Unit
    private[publication] def bundleBytes(name: String): Option[Vector[Byte]]
    private[publication] def restoreBundle(
      name: String,
      bytes: Option[Vector[Byte]],
      expectedBundleDigests: Map[String, String]
    ): Unit
  }

  private final class RegistryLockCapabilityImpl(
    private val _real_root: Path,
    ownerthread: Thread,
    private val _site_root_evidence: Option[SiteRootEvidence]
  ) extends RegistryLockCapability {
    private var _active = true

    override def realRoot: Path = {
      _require_owner()
      _real_root
    }

    override def validateRootEvidence(): Unit = {
      _require_owner()
      _site_root_evidence.foreach(PublicationRegistry.validateSiteRootEvidence)
    }

    override def validateSnapshot(expectedBundleDigests: Map[String, String]): Unit = {
      _require_owner()
      PublicationRegistry.validateRegistrySnapshotLocked(_real_root, expectedBundleDigests)
    }

    override def replaceMetadata(
      name: String,
      entries: Vector[(String, JsValue)],
      removePrefixes: Vector[String],
      expectedBundleDigests: Map[String, String],
      createArticleMedia: Boolean
    ): Unit = {
      _require_owner()
      PublicationRegistry.replaceMetadataLocked(
        _real_root,
        name,
        entries.map { case (path, json) => PublicationBundleEntry(path, _publication_bundle_key(path), json) },
        removePrefixes,
        expectedBundleDigests,
        createArticleMedia
      )
    }

    override def publishMetadata(publication: MetadataPublication): Unit = {
      _require_owner()
      PublicationRegistry.publishMetadataLocked(_real_root, publication)
    }

    override def bundleBytes(name: String): Option[Vector[Byte]] = {
      _require_owner()
      PublicationRegistry.bundleBytesLocked(_real_root, name)
    }

    override def restoreBundle(
      name: String,
      bytes: Option[Vector[Byte]],
      expectedBundleDigests: Map[String, String]
    ): Unit = {
      _require_owner()
      PublicationRegistry.restoreBundleLocked(_real_root, name, bytes, expectedBundleDigests)
    }

    private[CozyPublicationCompiler] def _close(): Unit = {
      _require_owner()
      _active = false
    }

    private def _require_owner(): Unit = {
      if (!_active)
        RAISE.invalidArgumentFault("Publication registry lock capability is no longer active")
      if (Thread.currentThread ne ownerthread)
        RAISE.invalidArgumentFault("Publication registry lock capability must be used by its owner thread")
    }
  }

  private[publication] def withRegistryLockCapability[A](root: Path)(f: RegistryLockCapability => A): A = {
    if (f == null)
      RAISE.invalidArgumentFault("Publication registry lock callback must be defined")
    PublicationRegistry.withRegistryLock(root) { realroot =>
      val capability = new RegistryLockCapabilityImpl(realroot, Thread.currentThread, None)
      try f(capability) finally capability._close()
    }
  }

  private[publication] def captureSiteRootEvidence(root: Path): SiteRootEvidence =
    PublicationRegistry.captureSiteRootEvidence(root)

  private[publication] def withExistingRegistryLockCapability[A](evidence: SiteRootEvidence)(f: RegistryLockCapability => A): A = {
    if (evidence == null || f == null)
      RAISE.invalidArgumentFault("Existing publication registry lock inputs must be defined")
    PublicationRegistry.withExistingRegistryLock(evidence) { realroot =>
      val capability = new RegistryLockCapabilityImpl(realroot, Thread.currentThread, Some(evidence))
      try f(capability) finally capability._close()
    }
  }

  def publishMetadata(
    root: Path,
    name: String,
    publicationPath: Option[String],
    projectDir: Path,
    entries: Vector[(String, JsValue)]
  ): Unit =
    publishMetadata(_metadata_publication(name, publicationPath, projectDir, entries), root)

  private[cozy] def metadataPublication(
    name: String,
    publicationPath: Option[String],
    projectDir: Path,
    entries: Vector[(String, JsValue)]
  ): MetadataPublication =
    _metadata_publication(name, publicationPath, projectDir, entries)

  private[cozy] def publishMetadata(publication: MetadataPublication, root: Path): Unit =
    PublicationRegistry.publishMetadata(root, publication)

  /* Build the exact legacy bundle before a caller enters a larger transaction.
   * This is read-only: it deliberately exercises source provenance and entry
   * canonicalization without acquiring the registry lock or writing a file. */
  private[cozy] def validateMetadataPublication(publication: MetadataPublication): MetadataPublication =
    PublicationRegistry.validateMetadataPublication(publication)

  private def _metadata_publication(
    name: String,
    publicationpath: Option[String],
    projectdir: Path,
    entries: Vector[(String, JsValue)]
  ): MetadataPublication = {
    if (projectdir == null || entries == null)
      RAISE.invalidArgumentFault("Publication metadata plan inputs must be defined")
    MetadataPublication(name, publicationpath, projectdir, entries)
  }

  private def _write(publication: Publication, savedir: Path, projectdir: Path): Unit = {
    val name = publication.project.name
    _delete_legacy_placeholder(savedir.resolve(s"repository/artifacts/${name}"))
    _delete_legacy_placeholder(savedir.resolve(s"maven/artifacts/${name}"))
    _delete_legacy_placeholder(savedir.resolve(s"download/artifacts/${name}"))
    val staging = Files.createTempDirectory("cozy-publication-")
    try {
      _write_publication_files(publication, staging)
      PublicationRegistry.publish(savedir, publication, projectdir, staging)
    } finally {
      _delete_directory(staging)
    }
  }

  private def _write_publication_files(publication: Publication, savedir: Path): Unit = {
    val name = publication.project.name
    _delete_legacy_placeholder(savedir.resolve(s"repository/artifacts/${name}"))
    _delete_legacy_placeholder(savedir.resolve(s"maven/artifacts/${name}"))
    _delete_legacy_placeholder(savedir.resolve(s"download/artifacts/${name}"))
    val metadatadir = savedir.resolve("metadata")
    _write_pair(metadatadir.resolve(s"catalog/projects/${name}"), _catalog_project_yaml(publication), _catalog_project_json(publication))
    _write_pair(metadatadir.resolve(s"catalog/samples/${name}"), _catalog_sample_yaml(publication), _catalog_sample_json(publication))
    _write_pair(metadatadir.resolve(s"projects/${name}/metadata"), _project_metadata_yaml(publication), _project_metadata_json(publication))
    _write_pair(metadatadir.resolve(s"samples/${name}/metadata"), _sample_metadata_yaml(publication), _sample_metadata_json(publication))
    _write_publication_pages(publication, metadatadir)
    publication.samples.foreach(_write_sample(publication.project, metadatadir, _))
    _write_repository_artifact(publication, metadatadir)
    _write_download_artifact(publication, metadatadir)
    if (publication.sourceManifestEnabled)
      _write_pair(metadatadir.resolve(s"source-manifest/${name}"), _source_manifest_yaml(publication), _source_manifest_json(publication))
  }

  private def _delete_legacy_placeholder(base: Path): Unit = {
    val yaml = Paths.get(base.toString + ".yaml")
    val json = Paths.get(base.toString + ".json")
    if (_is_legacy_placeholder(yaml) || _is_legacy_placeholder(json)) {
      Files.deleteIfExists(yaml)
      Files.deleteIfExists(json)
    }
  }

  private def _is_legacy_placeholder(path: Path): Boolean =
    Files.isRegularFile(path) && Files.readString(path, StandardCharsets.UTF_8).contains("placeholder")

  private def _write_pair(base: Path, yaml: String, json: JsValue): Unit = {
    _write_text(Paths.get(base.toString + ".yaml"), yaml)
    _write_text(Paths.get(base.toString + ".json"), Json.prettyPrint(json) + "\n")
  }

  private def _catalog_project_yaml(p: Publication): String =
    _yaml_header("catalog-project") +
      s"""project:
         |  name: ${_yaml_string(p.project.name)}
         |  title: ${_yaml_string(p.project.title)}
         |  kind: ${_yaml_string(p.project.kind)}
         |  metadata: ${_yaml_string(s"metadata/projects/${p.project.name}/metadata")}
         |""".stripMargin

  private def _catalog_project_json(p: Publication): JsValue =
    Json.obj(
      "schema" -> _schema,
      "type" -> "catalog-project",
      "project" -> Json.obj(
        "name" -> p.project.name,
        "title" -> p.project.title,
        "kind" -> p.project.kind,
        "metadata" -> s"metadata/projects/${p.project.name}/metadata"
      )
    )

  private def _catalog_sample_yaml(p: Publication): String =
    _yaml_header("catalog-sample") +
      s"""sample:
         |  name: ${_yaml_string(p.project.name)}
         |  title: ${_yaml_string(p.project.title)}
         |  kind: ${_yaml_string(p.project.kind)}
         |  metadata: ${_yaml_string(s"metadata/samples/${p.project.name}/metadata")}
         |${_catalog_sample_download_yaml(p)}""".stripMargin

  private def _catalog_sample_json(p: Publication): JsValue = {
    val sample = Json.obj(
      "name" -> p.project.name,
      "title" -> p.project.title,
      "kind" -> p.project.kind,
      "metadata" -> s"metadata/samples/${p.project.name}/metadata"
    )
    Json.obj(
      "schema" -> _schema,
      "type" -> "catalog-sample",
      "sample" -> (if (p.samples.nonEmpty) sample + ("download" -> _sample_collection_download_json(p.project)) else sample)
    )
  }

  private def _project_metadata_yaml(p: Publication): String =
    _yaml_header("project-metadata") +
      _project_yaml(p.project) +
      _publication_yaml(p)

  private def _project_metadata_json(p: Publication): JsValue =
    Json.obj(
      "schema" -> _schema,
      "type" -> "project-metadata",
      "project" -> _project_json(p.project),
      "publication" -> _publication_json(p)
    )

  private def _sample_metadata_yaml(p: Publication): String =
    _yaml_header("sample-metadata") +
      _project_yaml(p.project) +
      _publication_yaml(p) +
      _sample_collection_download_yaml(p) +
      _sample_refs_yaml(p)

  private def _sample_metadata_json(p: Publication): JsValue = {
    val base = Json.obj(
      "schema" -> _schema,
      "type" -> "sample-metadata",
      "project" -> _project_json(p.project),
      "publication" -> _publication_json(p),
      "samples" -> JsArray(p.samples.map(sample => _sample_ref_json(p.project, sample)))
    )
    if (p.samples.nonEmpty)
      base + ("download" -> _sample_collection_download_json(p.project))
    else
      base
  }

  private def _catalog_sample_download_yaml(p: Publication): String =
    if (p.samples.nonEmpty)
      s"""  download:
         |    artifact: ${_yaml_string(s"metadata/artifacts/download/${p.project.name}")}
         |    types:
         |      - "sample-collection-zip"
         |      - "sample-zip"
         |""".stripMargin
    else
      ""

  private def _sample_collection_download_yaml(p: Publication): String =
    if (p.samples.nonEmpty)
      s"""download:
         |  artifact: ${_yaml_string(s"metadata/artifacts/download/${p.project.name}")}
         |  types:
         |    - "sample-collection-zip"
         |    - "sample-zip"
         |""".stripMargin
    else
      ""

  private def _sample_collection_download_json(project: ProjectMetadata): JsObject =
    Json.obj(
      "artifact" -> s"metadata/artifacts/download/${project.name}",
      "types" -> Json.arr("sample-collection-zip", "sample-zip")
    )

  private def _publication_pages_yaml(p: Publication): String =
    _yaml_header("publication-pages") +
      s"""publication:
         |  name: ${_yaml_string(p.project.name)}
         |  title: ${_yaml_string(p.project.title)}
         |${p.project.publicationPath.map(x => s"  path: ${_yaml_string(x)}\n").getOrElse("")}pages:
         |${p.pages.map(_publication_page_yaml).mkString}""".stripMargin

  private def _publication_page_yaml(p: PublicationPage): String =
    s"""  - path: ${_yaml_string(p.path)}
       |${_text_yaml("title", p.title, 4)}${_descriptive_yaml(p.descriptiveAttributes, 4)}""".stripMargin

  private def _publication_pages_json(p: Publication): JsValue =
    Json.obj(
      "schema" -> _schema,
      "type" -> "publication-pages",
      "publication" -> (Json.obj(
        "name" -> p.project.name,
        "title" -> p.project.title
      ) ++ p.project.publicationPath.map(x => Json.obj("path" -> x)).getOrElse(Json.obj())),
      "pages" -> JsArray(p.pages.map(_publication_page_json))
    )

  private def _publication_page_json(p: PublicationPage): JsObject =
    Json.obj("path" -> p.path) ++
      _text_json("title", p.title) ++
      _descriptive_json(p.descriptiveAttributes)

  private def _sample_refs_yaml(p: Publication): String =
    if (p.samples.nonEmpty)
      s"""samples:
         |${p.samples.map(sample => _sample_ref_yaml(p.project, sample)).mkString}""".stripMargin
    else
      ""

  private def _write_repository_artifact(publication: Publication, savedir: Path): Unit =
    publication.project.kind match {
      case "car" | "sar" =>
        _write_pair(
          savedir.resolve(s"artifacts/repository/${publication.project.name}"),
          _repository_artifact_yaml(publication),
          _repository_artifact_json(publication)
        )
      case _ =>
        Unit
    }

  private def _write_download_artifact(publication: Publication, savedir: Path): Unit =
    if (publication.samples.nonEmpty)
      _write_pair(
        savedir.resolve(s"artifacts/download/${publication.project.name}"),
        _download_artifact_yaml(publication),
        _download_artifact_json(publication)
      )

  private def _write_publication_pages(publication: Publication, savedir: Path): Unit =
    if (publication.pages.nonEmpty)
      _write_pair(
        savedir.resolve(s"publication-pages/${publication.project.name}"),
        _publication_pages_yaml(publication),
        _publication_pages_json(publication)
      )

  private def _repository_artifact_yaml(p: Publication): String =
    _yaml_header("repository-artifact") +
      _project_yaml(p.project) +
      s"""artifact:
         |  layer: "repository"
         |  status: "planned"
         |  kinds:
         |${_repository_kind_yaml(p)}
         |  files:
         |${_repository_files(p).map(_artifact_file_yaml).mkString}""".stripMargin

  private def _repository_artifact_json(p: Publication): JsValue =
    Json.obj(
      "schema" -> _schema,
      "type" -> "repository-artifact",
      "project" -> _project_json(p.project),
      "artifact" -> Json.obj(
        "layer" -> "repository",
        "status" -> "planned",
        "kinds" -> Json.arr(Json.obj(
          "type" -> p.project.kind,
          "versions" -> Json.arr(_artifact_version(p.project.version)),
          "latestRelease" -> _artifact_version(p.project.version)
        )),
        "files" -> JsArray(_repository_files(p).map(_artifact_file_json))
      )
    )

  private def _download_artifact_yaml(p: Publication): String =
    _yaml_header("download-artifact") +
      _project_yaml(p.project) +
      {
        val files = _sample_collection_zip_file(p.project) +: p.samples.map(sample => _sample_zip_file(p.project, sample))
      s"""artifact:
         |  layer: "download"
         |  status: "planned"
         |  kinds:
         |    - type: "sample-collection-zip"
         |      latest_release: ${_yaml_string(_artifact_version(p.project.version))}
         |      versions: [${_yaml_string(_artifact_version(p.project.version))}]
         |    - type: "sample-zip"
         |      latest_release: ${_yaml_string(_artifact_version(p.project.version))}
         |      versions: [${p.samples.map(_.version).distinct.sorted.map(_yaml_string).mkString(", ")}]
         |  files:
         |${files.map(_artifact_file_yaml).mkString}""".stripMargin
      }

  private def _download_artifact_json(p: Publication): JsValue =
    {
      val files = _sample_collection_zip_file(p.project) +: p.samples.map(sample => _sample_zip_file(p.project, sample))
    Json.obj(
      "schema" -> _schema,
      "type" -> "download-artifact",
      "project" -> _project_json(p.project),
      "artifact" -> Json.obj(
        "layer" -> "download",
        "status" -> "planned",
        "kinds" -> Json.arr(
          Json.obj(
            "type" -> "sample-collection-zip",
            "versions" -> Json.arr(_artifact_version(p.project.version)),
            "latestRelease" -> _artifact_version(p.project.version)
          ),
          Json.obj(
            "type" -> "sample-zip",
            "versions" -> p.samples.map(_.version).distinct.sorted,
            "latestRelease" -> _artifact_version(p.project.version)
          )
        ),
        "files" -> JsArray(files.map(_artifact_file_json))
      )
    )
    }

  private def _repository_kind_yaml(p: Publication): String =
    s"""    - type: ${_yaml_string(p.project.kind)}
       |      latest_release: ${_yaml_string(_artifact_version(p.project.version))}
       |      versions: [${_yaml_string(_artifact_version(p.project.version))}]
       |""".stripMargin

  private def _write_sample(project: ProjectMetadata, metadatadir: Path, sample: SamplePublication): Unit = {
    val itembase = metadatadir.resolve(s"samples/${project.name}/items/${sample.name}/${sample.version}")
    _write_pair(itembase.resolve("metadata"), _sample_item_yaml(project, sample), _sample_item_json(project, sample))
    _copy_sample_files(itembase.resolve("files"), sample)
    _write_text(metadatadir.resolve(s"samples/${project.name}/items/${sample.name}/latest.json"), Json.prettyPrint(Json.obj(
      "schema" -> _schema,
      "type" -> "sample-latest",
      "project" -> _project_json(project),
      "sample" -> _sample_json(sample),
      "latest" -> Json.obj(
        "version" -> sample.version,
        "metadata" -> s"metadata/samples/${project.name}/items/${sample.name}/${sample.version}/metadata"
      )
    )) + "\n")
  }

  private def _sample_ref_yaml(project: ProjectMetadata, p: SamplePublication): String =
    s"""    - name: ${_yaml_string(p.name)}
       |      title: ${_yaml_string(p.title)}
       |${_descriptive_yaml(p.descriptiveAttributes, 6)}      version: ${_yaml_string(p.version)}
       |      directory: ${_yaml_string(p.directory)}
       |      metadata: ${_yaml_string(s"metadata/samples/${project.name}/items/${p.name}/${p.version}/metadata")}
       |""".stripMargin

  private def _sample_ref_json(project: ProjectMetadata, p: SamplePublication): JsValue =
    Json.obj(
      "name" -> p.name,
      "title" -> p.title,
      "version" -> p.version,
      "directory" -> p.directory,
      "metadata" -> s"metadata/samples/${project.name}/items/${p.name}/${p.version}/metadata"
    ) ++ _descriptive_json(p.descriptiveAttributes)

  private def _sample_item_yaml(project: ProjectMetadata, sample: SamplePublication): String =
    _yaml_header("sample-item") +
      _project_yaml(project) +
      s"""sample:
         |  name: ${_yaml_string(sample.name)}
         |  title: ${_yaml_string(sample.title)}
         |${_descriptive_yaml(sample.descriptiveAttributes, 2)}  version: ${_yaml_string(sample.version)}
         |  directory: ${_yaml_string(sample.directory)}
         |  files_path: ${_yaml_string(s"metadata/samples/${project.name}/items/${sample.name}/${sample.version}/files")}
         |  download:
         |    artifact: ${_yaml_string(s"metadata/artifacts/download/${project.name}")}
         |    type: "sample-zip"
         |""".stripMargin

  private def _sample_item_json(project: ProjectMetadata, sample: SamplePublication): JsValue =
    Json.obj(
      "schema" -> _schema,
      "type" -> "sample-item",
      "project" -> _project_json(project),
      "sample" -> (_sample_json(sample) +
        ("filesPath" -> JsString(s"metadata/samples/${project.name}/items/${sample.name}/${sample.version}/files")) +
        ("download" -> _sample_download_json(project, sample)))
    )

  private def _sample_json(p: SamplePublication): JsObject =
    Json.obj(
      "name" -> p.name,
      "title" -> p.title,
      "version" -> p.version,
      "directory" -> p.directory
    ) ++ _descriptive_json(p.descriptiveAttributes)

  private def _sample_download_json(project: ProjectMetadata, sample: SamplePublication): JsObject =
    Json.obj(
      "artifact" -> s"metadata/artifacts/download/${project.name}",
      "type" -> "sample-zip"
    )

  private def _repository_files(p: Publication): Vector[ArtifactFile] =
    p.repositoryModules.map { module =>
      val version = _artifact_version(p.project.version)
      ArtifactFile(
        layer = "repository",
        artifactType = p.project.kind,
        module = Some(module),
        sampleName = None,
        version = version,
        extension = p.project.kind,
        path = s"repository/${p.project.kind}/${module}/${version}/${module}-${version}.${p.project.kind}",
        name = s"${module}-${version}.${p.project.kind}"
      )
    }

  private def _sample_zip_file(project: ProjectMetadata, sample: SamplePublication): ArtifactFile =
    ArtifactFile(
      layer = "download",
      artifactType = "sample-zip",
      module = Some(project.name),
      sampleName = Some(sample.name),
      version = sample.version,
      extension = "zip",
      path = CozyPublicationPaths.sampleDownloadPath(project.name, project.publicationPath, sample.name, sample.version),
      name = s"${sample.name}-${sample.version}.zip"
    )

  private def _sample_collection_zip_file(project: ProjectMetadata): ArtifactFile = {
    val version = _artifact_version(project.version)
    ArtifactFile(
      layer = "download",
      artifactType = "sample-collection-zip",
      module = Some(project.name),
      sampleName = None,
      version = version,
      extension = "zip",
      path = CozyPublicationPaths.collectionDownloadPath(project.name, project.publicationPath, version),
      name = s"${project.name}-${version}.zip"
    )
  }

  private def _artifact_file_yaml(p: ArtifactFile): String =
    s"""    - warehouse_path: ${_yaml_string(p.path)}
       |      public_path: ${_yaml_string(_public_artifact_path(p.path))}
       |      name: ${_yaml_string(p.name)}
       |      version: ${_yaml_string(p.version)}
       |      type: ${_yaml_string(p.artifactType)}
       |      module: ${_yaml_string(p.module.getOrElse(""))}
       |      sample: ${_yaml_string(p.sampleName.getOrElse(""))}
       |      extension: ${_yaml_string(p.extension)}
       |      expected: true
       |""".stripMargin

  private def _artifact_file_json(p: ArtifactFile): JsValue =
    Json.obj(
      "layer" -> p.layer,
      "type" -> p.artifactType,
      "module" -> p.module,
      "artifactId" -> p.module,
      "sampleName" -> p.sampleName,
      "version" -> p.version,
      "extension" -> p.extension,
      "warehousePath" -> p.path,
      "publicPath" -> _public_artifact_path(p.path),
      "name" -> p.name,
      "expected" -> true
    )

  private def _public_artifact_path(warehousepath: String): String =
    if (warehousepath.startsWith("maven/"))
      s"repository/${warehousepath}"
    else if (warehousepath.startsWith("repository/"))
      warehousepath
    else if (warehousepath.startsWith("download/"))
      s"repository/${warehousepath}"
    else
      warehousepath

  private def _artifact_version(version: String): String =
    Option(version).map(_.trim).filter(_.nonEmpty).getOrElse("0.0.0-SNAPSHOT")

  private def _source_manifest_yaml(p: Publication): String =
    _yaml_header("source-manifest") +
      _project_yaml(p.project) +
      s"""files:
         |${p.sourceFiles.map(_source_file_yaml).mkString}""".stripMargin

  private def _source_manifest_json(p: Publication): JsValue =
    Json.obj(
      "schema" -> _schema,
      "type" -> "source-manifest",
      "project" -> _project_json(p.project),
      "files" -> JsArray(p.sourceFiles.map(_source_file_json))
    )

  private def _yaml_header(kind: String): String =
    s"""schema: ${_yaml_string(_schema)}
       |type: ${_yaml_string(kind)}
       |""".stripMargin

  private def _project_yaml(p: ProjectMetadata): String =
    s"""project:
       |  name: ${_yaml_string(p.name)}
         |  title: ${_yaml_string(p.title)}
         |  kind: ${_yaml_string(p.kind)}
         |${_descriptive_yaml(p.descriptiveAttributes)}
         |  organization: ${_yaml_string(p.organization)}
       |  version: ${_yaml_string(p.version)}
       |  scala_version: ${_yaml_string(p.scalaVersion)}
       |  sbt_version: ${_yaml_string(p.sbtVersion)}
       |${_build_settings_yaml(p.buildSettings, 2)}
       |""".stripMargin

  private def _publication_yaml(p: Publication): String = {
    val source = if (p.sourceManifestEnabled) s"  source_manifest: metadata/source-manifest/${p.project.name}\n" else ""
    val path = p.project.publicationPath.map(x => s"  path: ${_yaml_string(x)}\n").getOrElse("")
    s"""publication:
       |${source}${path}${_article_relations_yaml(p)}""".stripMargin
  }

  private def _article_relations_yaml(p: Publication): String = {
    val xs = _article_relations(p)
    if (xs.isEmpty)
      ""
    else
      s"""  articles:
         |${xs.map(_article_relation_yaml).mkString}""".stripMargin
  }

  private def _article_relation_yaml(p: ArticleRelation): String =
    s"""    - path: ${_yaml_string(p.path)}
       |      role: ${_yaml_string(p.role)}
       |${p.title.map(x => s"      title: ${_yaml_string(x)}\n").getOrElse("")}""".stripMargin

  private def _article_relation_json(p: ArticleRelation): JsObject =
    Json.obj(
      "path" -> p.path,
      "role" -> p.role
    ) ++ p.title.map(x => Json.obj("title" -> x)).getOrElse(Json.obj())

  private def _article_relations(p: Publication): Vector[ArticleRelation] = {
    val primary = p.project.publicationPath.map(path => ArticleRelation(path, "primary", Some(p.project.title))).toVector
    val pages = p.pages.map(page => ArticleRelation(page.path, "page", page.title.default))
    (primary ++ pages).foldLeft(Vector.empty[ArticleRelation]) { (z, x) =>
      if (z.exists(_.path == x.path)) z else z :+ x
    }
  }

  private def _publication_json(p: Publication): JsValue = {
    val source =
      if (p.sourceManifestEnabled) Json.obj("sourceManifest" -> s"metadata/source-manifest/${p.project.name}")
      else Json.obj()
    val base = p.project.publicationPath match {
      case Some(path) => source + ("path" -> JsString(path))
      case None => source
    }
    _article_relations(p) match {
      case Vector() => base
      case xs => base + ("articles" -> JsArray(xs.map(_article_relation_json)))
    }
  }

  private def _source_file_yaml(p: SourceFile): String =
    s"""  - path: ${_yaml_string(p.path)}
       |    size: ${p.size}
       |    sha256: ${_yaml_string(p.sha256)}
       |""".stripMargin

  private def _source_file_json(p: SourceFile): JsValue =
    Json.obj(
      "path" -> p.path,
      "size" -> p.size,
      "sha256" -> p.sha256
    )

  private def _project_json(p: ProjectMetadata): JsValue =
    Json.obj(
      "name" -> p.name,
      "title" -> p.title,
      "kind" -> p.kind,
      "organization" -> p.organization,
      "version" -> p.version,
      "scalaVersion" -> p.scalaVersion,
      "sbtVersion" -> p.sbtVersion,
      "buildSettings" -> _build_settings_json(p.buildSettings)
    ) ++ _descriptive_json(p.descriptiveAttributes)

  private def _build_settings_yaml(p: BuildSettings, indent: Int): String = {
    val sp = " " * indent
    val packaging = p.cozyPackaging.map(x => s"${sp}  cozy_packaging: ${_yaml_string(x)}\n").getOrElse("")
    val cncfversion = p.cncfVersion.map(x => s"${sp}  cncf_version: ${_yaml_string(x)}\n").getOrElse("")
    s"""${sp}build_settings:
       |${sp}  cozy_plugin: ${p.cozyPlugin}
       |${packaging}${cncfversion}${sp}  cncf_dependency: ${p.cncfDependency}
       |${sp}  sbt_cozy_plugin: ${p.sbtCozyPlugin}
       |""".stripMargin
  }

  private def _build_settings_json(p: BuildSettings): JsObject =
    Json.obj(
      "cozyPlugin" -> p.cozyPlugin,
      "cncfDependency" -> p.cncfDependency,
      "sbtCozyPlugin" -> p.sbtCozyPlugin
    ) ++ p.cozyPackaging.map(x => Json.obj("cozyPackaging" -> x)).getOrElse(Json.obj()) ++
      p.cncfVersion.map(x => Json.obj("cncfVersion" -> x)).getOrElse(Json.obj())

  private def _descriptive_yaml(p: DescriptiveAttributes, indent: Int = 2): String =
    DescriptiveAttributes.Fields.map(name => _text_yaml(name, p.field(name), indent)).mkString

  private def _text_yaml(name: String, text: DescriptiveAttributes.Text, indent: Int): String = {
    val sp = " " * indent
    val default = text.default.map(x => s"${sp}${name}: ${_yaml_string(x)}\n").getOrElse("")
    val i18n =
      if (text.i18n.isEmpty)
        ""
      else
        s"${sp}${name}_i18n:\n" + text.i18n.toVector.sortBy(_._1).map {
          case (k, v) => s"${sp}  ${k}: ${_yaml_string(v)}\n"
        }.mkString
    default + i18n
  }

  private def _descriptive_json(p: DescriptiveAttributes): JsObject =
    DescriptiveAttributes.Fields.foldLeft(Json.obj()) { (z, name) =>
      z ++ _text_json(name, p.field(name))
    }

  private def _text_json(name: String, text: DescriptiveAttributes.Text): JsObject = {
    val default = text.default.map(x => Json.obj(name -> x)).getOrElse(Json.obj())
    val i18n =
      if (text.i18n.isEmpty)
        Json.obj()
      else
        Json.obj(s"${name}_i18n" -> JsObject(text.i18n.toVector.sortBy(_._1).map {
          case (k, v) => k -> JsString(v)
        }))
    default ++ i18n
  }

  private def _source_manifest(projectdir: Path, savedir: Path, excludes: Set[String]): Vector[SourceFile] = {
    val save = savedir.toAbsolutePath.normalize()
    val stream = Files.walk(projectdir)
    try {
      stream.iterator().asScala.toVector.collect {
        case p if Files.isRegularFile(p) && !_excluded(projectdir, p, save, excludes) =>
          val rel = projectdir.relativize(p).toString.replace('\\', '/')
          SourceFile(rel, Files.size(p), _sha256(p))
      }.sortBy(_.path)
    } finally {
      stream.close()
    }
  }

  private def _sample_publications(samplesdir: Path, savedir: Path, projectversion: String, excludes: Set[String]): Vector[SamplePublication] =
    _validate_unique_sample_names(_sample_dirs(samplesdir).map(dir => dir -> _validate_name(_slugify(dir.getFileName.toString), "sample name"))).map { case (dir, name) =>
      val buildsbt = Files.readString(dir.resolve("build.sbt"), StandardCharsets.UTF_8)
      val metadata = _public_metadata(dir)
      val rawname = dir.getFileName.toString
      val title = _metadata_value(metadata, "title").orElse(_sbt_setting(buildsbt, "name")).getOrElse(rawname)
      val version = Option(projectversion).map(_.trim).filter(_.nonEmpty).getOrElse("0.0.0-SNAPSHOT")
      val descriptiveattributes = metadata.descriptiveAttributes.
        orElse(_descriptive_attributes(_readme_summary(dir), _readme_description(dir)))
      SamplePublication(
        name = name,
        title = title,
        descriptiveAttributes = descriptiveattributes,
        summary = descriptiveattributes.summary.default,
        description = descriptiveattributes.description.default,
        directory = samplesdir.relativize(dir).toString.replace('\\', '/'),
        version = version,
        root = dir,
        files = _source_manifest(dir, savedir, excludes)
      )
    }

  private def _validate_unique_sample_names(samples: Vector[(Path, String)]): Vector[(Path, String)] = {
    val duplicates = samples.groupBy(_._2).collect {
      case (name, xs) if xs.size > 1 =>
        s"${name}: ${xs.map(_._1.getFileName.toString).sorted.mkString(", ")}"
    }.toVector.sorted
    if (duplicates.nonEmpty)
      RAISE.invalidArgumentFault(s"Duplicate sample slug(s): ${duplicates.mkString("; ")}")
    samples
  }

  private def _sample_dirs(samplesdir: Path): Vector[Path] =
    if (!Files.isDirectory(samplesdir))
      Vector.empty
    else
      Option(samplesdir.toFile.listFiles()).toVector.flatten.
        filter(f => f.isDirectory && new java.io.File(f, "build.sbt").isFile).
        map(_.toPath.toAbsolutePath.normalize()).
        sortBy(_.getFileName.toString)

  private def _copy_sample_files(targetdir: Path, sample: SamplePublication): Unit = {
    _delete_directory(targetdir)
    sample.files.foreach { f =>
      val source = sample.root.resolve(f.path)
      val target = targetdir.resolve(f.path)
      Option(target.getParent).foreach(Files.createDirectories(_))
      Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING)
    }
  }

  private def _excluded(projectdir: Path, path: Path, savedir: Path, excludes: Set[String]): Boolean = {
    val abs = path.toAbsolutePath.normalize()
    val rel = projectdir.relativize(path).toString.replace('\\', '/')
    val segments = rel.split('/').toVector
    val normalizedexcludes = excludes.map(_.trim.stripPrefix("/").stripSuffix("/")).filter(_.nonEmpty)
    val excludedbyname = normalizedexcludes.exists(x => !x.contains("/") && segments.contains(x))
    val excludedbypath = normalizedexcludes.exists(x => x.contains("/") && (rel == x || rel.startsWith(x + "/")))
    excludedbyname || excludedbypath || abs.startsWith(savedir)
  }

  private def _sha256(path: Path): String = {
    val digest = MessageDigest.getInstance("SHA-256")
    val in = Files.newInputStream(path)
    val buffer = new Array[Byte](8192)
    try {
      var n = in.read(buffer)
      while (n >= 0) {
        if (n > 0)
          digest.update(buffer, 0, n)
        n = in.read(buffer)
      }
    } finally {
      in.close()
    }
    digest.digest().map(b => f"${b & 0xff}%02x").mkString
  }

  private def _delete_directory(path: Path): Unit =
    if (Files.exists(path)) {
      val stream = Files.walk(path)
      try {
        stream.iterator().asScala.toVector.reverse.foreach(Files.deleteIfExists)
      } finally {
        stream.close()
      }
    }

  private def _detect_kind(projectdir: Path, buildsbt: String, samplesdir: Path): String = {
    val childbuilds = Option(projectdir.toFile.listFiles()).toVector.flatten.count(f => f.isDirectory && new java.io.File(f, "build.sbt").isFile)
    val samplebuilds = Option(samplesdir.toFile.listFiles()).toVector.flatten.count(f => f.isDirectory && new java.io.File(f, "build.sbt").isFile)
    if (_contains_sar_marker(projectdir, buildsbt))
      "sar"
    else if (_contains_car_marker(projectdir, buildsbt))
      "car"
    else if (samplebuilds > 1 || childbuilds > 1 || _project_definition_count(buildsbt) > 1)
      "sample-multi"
    else
      "sample-single"
  }

  private def _contains_sar_marker(projectdir: Path, buildsbt: String): Boolean =
    buildsbt.contains("cozyPackaging := \"sar\"") ||
      Files.isRegularFile(projectdir.resolve("subsystem-descriptor.yaml")) ||
      Files.isRegularFile(projectdir.resolve("subsystem-descriptor.yml"))

  private def _contains_car_marker(projectdir: Path, buildsbt: String): Boolean =
    buildsbt.contains("CozyPlugin") &&
      (buildsbt.contains("cozyPackaging := \"car\"") ||
        Files.isDirectory(projectdir.resolve("src/main/car")) ||
        Files.isDirectory(projectdir.resolve("src/main/cozy")))

  private def _build_settings(buildsbt: String): BuildSettings =
    BuildSettings(
      cozyPlugin = buildsbt.contains("CozyPlugin") || buildsbt.contains("sbt-cozy"),
      cozyPackaging = _sbt_setting(buildsbt, "cozyPackaging"),
      cncfVersion = _sbt_setting(buildsbt, "cncfVersion").orElse(_sbt_val(buildsbt, "cncfVersion")).
        orElse(_library_dependency_version(buildsbt, "org.goldenport", "goldenport-cncf")),
      cncfDependency = buildsbt.contains("goldenport-cncf") || buildsbt.contains("org.goldenport.cncf"),
      sbtCozyPlugin = buildsbt.contains("sbt-cozy") || buildsbt.contains("CozyPlugin")
    )

  private def _library_dependency_version(buildsbt: String, organization: String, artifactprefix: String): Option[String] = {
    val versionpattern = """%\s*"([^"]+)"""".r
    buildsbt.linesIterator.find(line => line.contains("\"" + organization + "\"") && line.contains("\"" + artifactprefix)).flatMap { line =>
      versionpattern.findAllMatchIn(line).map(_.group(1).trim).toVector.lastOption
    }.filter(_.nonEmpty)
  }

  private def _project_definition_count(buildsbt: String): Int =
    "(?m)^\\s*lazy\\s+val\\s+\\w+\\s*=\\s*\\(?project\\b".r.findAllIn(buildsbt).length

  private def _sbt_setting(buildsbt: String, key: String): Option[String] = {
    val pattern = ("""(?m)^\s*(?:ThisBuild\s*/\s*)?""" + java.util.regex.Pattern.quote(key) + """\s*:=\s*"([^"]+)""").r
    pattern.findFirstMatchIn(buildsbt).map(_.group(1).trim).filter(_.nonEmpty)
  }

  private def _sbt_val(buildsbt: String, key: String): Option[String] = {
    val pattern = ("""(?m)^\s*(?:lazy\s+)?val\s+""" + java.util.regex.Pattern.quote(key) + """\s*=\s*"([^"]+)""").r
    pattern.findFirstMatchIn(buildsbt).map(_.group(1).trim).filter(_.nonEmpty)
  }

  private def _sbt_version(projectdir: Path): Option[String] = {
    val path = projectdir.resolve("project/build.properties")
    if (!Files.isRegularFile(path))
      None
    else
      Files.readAllLines(path, StandardCharsets.UTF_8).asScala.collectFirst {
        case line if line.trim.startsWith("sbt.version=") =>
          line.trim.substring("sbt.version=".length).trim
      }.filter(_.nonEmpty)
  }

  private def _public_metadata(projectdir: Path): CozyProjectYamlConfig.Config =
    Vector("project.yaml", "project.yml", "project.json", "project.conf", "project.hocon", "project.xml").
      map(projectdir.resolve).
      find(Files.isRegularFile(_)).
      map(CozyProjectYamlConfig.loadPublic).
      getOrElse(CozyProjectYamlConfig.Config.empty)

  private def _metadata_value(metadata: CozyProjectYamlConfig.Config, key: String): Option[String] =
    metadata.value(key).
      orElse(metadata.value(s"project.${key}")).
      orElse(metadata.value(s"publication.${key}"))

  private def _descriptive_attributes(
    projectdir: Path,
    args: List[String],
    metadata: CozyProjectYamlConfig.Config,
    config: CozyProjectYamlConfig.Config
  ): DescriptiveAttributes = {
    val cli = _descriptive_attributes(_value(args, "summary"), _value(args, "description"))
    val readme = _descriptive_attributes(_readme_summary(projectdir), _readme_description(projectdir))
    val conf = _descriptive_attributes(config.value("publication.summary"), config.value("publication.description"))
    cli.orElse(metadata.descriptiveAttributes).orElse(readme).orElse(conf)
  }

  private def _descriptive_attributes(summary: Option[String], description: Option[String]): DescriptiveAttributes =
    DescriptiveAttributes(
      summary = DescriptiveAttributes.Text(summary),
      description = DescriptiveAttributes.Text(description)
    )

  private def _readme_summary(projectdir: Path): Option[String] =
    _readme_lines(projectdir).find(line => line.nonEmpty && !line.startsWith("#")).map(_trim_sentence)

  private def _readme_description(projectdir: Path): Option[String] =
    _readme_lines(projectdir).filter(line => line.nonEmpty && !line.startsWith("#")).take(3).mkString("\n") match {
      case "" => None
      case x => Some(x)
    }

  private def _readme_lines(projectdir: Path): Vector[String] = {
    val candidates = Vector("README.md", "README.adoc", "README.txt").map(projectdir.resolve)
    candidates.find(Files.isRegularFile(_)) match {
      case Some(path) =>
        Files.readAllLines(path, StandardCharsets.UTF_8).asScala.toVector.map(_.trim)
      case None =>
        Vector.empty
    }
  }

  private def _trim_sentence(value: String): String =
    if (value.length <= 160)
      value
    else
      value.take(157).trim + "..."

  private def _project_dir(args: List[String]): Path =
    _parsed_with_project(args).pathProperty("project").
      orElse(_parsed_with_project(args).argument("project").map(p => Paths.get(p).toAbsolutePath.normalize())).
      getOrElse(RAISE.invalidArgumentFault("Missing project directory for publish-project"))

  private def _required_path(args: List[String], key: String): Path =
    _parsed(args).pathProperty(key).getOrElse(RAISE.invalidArgumentFault(s"Missing --${key}"))

  private def _publication_output(projectdir: Path, args: List[String], config: CozyProjectYamlConfig.Config): Path =
    _parsed(args).pathProperty("save").
      orElse(_config_path(projectdir, config.value("publication.output"))).
      getOrElse(projectdir.resolve("target/publication").toAbsolutePath.normalize())

  private def _config_path(projectdir: Path, value: Option[String]): Option[Path] =
    value.map { p =>
      val path = Paths.get(p)
      if (path.isAbsolute)
        path.normalize()
      else
        projectdir.resolve(path).toAbsolutePath.normalize()
    }

  private def _value(args: List[String], key: String): Option[String] =
    _parsed(args).property(key)

  private val _request_parameters = Vector(
    spec.Parameter.propertyFileOption("project"),
    spec.Parameter.propertyFileOption("save"),
    spec.Parameter.property("kind"),
    spec.Parameter.property("name"),
    spec.Parameter.property("title"),
    spec.Parameter.property("path"),
    spec.Parameter.property("summary"),
    spec.Parameter.property("description"),
    spec.Parameter.property("organization"),
    spec.Parameter.property("version"),
    spec.Parameter.property("scala-version"),
    spec.Parameter.property("sbt-version"),
    spec.Parameter.property("maven-coordinates"),
    spec.Parameter.property("repository-artifacts"),
    spec.Parameter.property("repository-modules"),
    spec.Parameter.property("download-samples")
  )

  private def _parsed(args: List[String]): CozyCliArgs.Parsed =
    CozyCliArgs.parse(_request_parameters: _*)(args)

  private def _parsed_with_project(args: List[String]): CozyCliArgs.Parsed =
    CozyCliArgs.parse((spec.Parameter.argumentFile("project") +: _request_parameters): _*)(args)

  private def _validate_publication_path(value: String): String = {
    CozyPublicationPaths.validatePublicationPath(value)
  }

  private def _validate_name(value: String, label: String): String = {
    val name = value.trim
    _slug_pattern.findFirstIn(name) match {
      case Some(x) if x == name => name
      case _ => RAISE.invalidArgumentFault(s"Invalid ${label}: ${value}. Expected ${_slug_pattern.regex}")
    }
  }

  private def _slugify(value: String): String =
    value.toLowerCase(java.util.Locale.ROOT).
      replaceAll("[^a-z0-9]+", "-").
      stripPrefix("-").
      stripSuffix("-")

  private def _yaml_string(value: String): String =
    "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\""

  private def _optional_yaml(key: String, value: Option[String], indent: Int = 2): String =
    value.map(x => " " * indent + s"${key}: ${_yaml_string(x)}\n").getOrElse("")

  private def _write_text(path: Path, text: String): Unit = {
    Option(path.getParent).foreach(Files.createDirectories(_))
    Files.writeString(path, text, StandardCharsets.UTF_8)
  }

  private final case class PublicationBundleEntry(
    path: String,
    key: String,
    metadata: JsValue
  )
  private final case class PublicationBundle(
    publication: String,
    publicationPath: Option[String],
    sourceRepository: String,
    sourcePath: String,
    sourceCommit: Option[String],
    entries: Vector[PublicationBundleEntry],
    raw: Option[JsObject] = None
  )
  private final case class MetadataRemoval(path: String, exact: Boolean)

  private def _publication_bundle_key(path: String): String = {
    val stripped = path.replaceFirst("""\.[^.]+$""", "")
    if (stripped.startsWith("metadata/"))
      stripped.substring("metadata/".length)
    else
      stripped
  }

  private object PublicationRegistry {
    private val _registry_locks = new ConcurrentHashMap[String, Object]()
    def publish(root: Path, publication: Publication, projectdir: Path, staging: Path): Unit = {
      val name = publication.project.name
      val bundle = PublicationBundle(
        publication = name,
        publicationPath = publication.project.publicationPath,
        sourceRepository = _source_repository(projectdir),
        sourcePath = _source_path(projectdir),
        sourceCommit = _source_commit(projectdir),
        entries = _bundle_entries(staging)
      )
      _with_registry_lock(root) { realroot =>
        val target = _locked_target(realroot, name, requireexisting = false)
        _check_collisions(realroot, bundle)
        _write_bundle(target, bundle)
      }
    }

    def publishMetadata(
      root: Path,
      name: String,
      publicationPath: Option[String],
      projectDir: Path,
      entries: Vector[(String, JsValue)]
    ): Unit =
      publishMetadata(root, MetadataPublication(name, publicationPath, projectDir, entries))

    def publishMetadata(root: Path, publication: MetadataPublication): Unit = {
      if (publication == null)
        RAISE.invalidArgumentFault("Publication metadata plan must be defined")
      val bundle = _metadata_bundle(publication)
      _with_registry_lock(root) { realroot =>
        val target = _locked_target(realroot, bundle.publication, requireexisting = false)
        _check_collisions(realroot, bundle)
        _write_bundle(target, bundle)
      }
    }

    def publishMetadataLocked(realroot: Path, publication: MetadataPublication): Unit = {
      if (publication == null)
        RAISE.invalidArgumentFault("Publication metadata plan must be defined")
      val bundle = _metadata_bundle(publication)
      val target = _locked_target(realroot, bundle.publication, requireexisting = false)
      _check_collisions(realroot, bundle)
      _write_bundle(target, bundle)
    }

    def validateMetadataPublication(publication: MetadataPublication): MetadataPublication = {
      if (publication == null)
        RAISE.invalidArgumentFault("Publication metadata plan must be defined")
      val bundle = _metadata_bundle(publication)
      publication.copy(
        name = bundle.publication,
        publicationPath = bundle.publicationPath,
        entries = bundle.entries.map(x => x.path -> x.metadata)
      )
    }

    private def _metadata_bundle(publication: MetadataPublication): PublicationBundle = {
      if (publication.projectDir == null || publication.entries == null)
        RAISE.invalidArgumentFault("Publication metadata plan inputs must be defined")
      val bundle = PublicationBundle(
        publication = publication.name,
        publicationPath = publication.publicationPath,
        sourceRepository = _source_repository(publication.projectDir),
        sourcePath = _source_path(publication.projectDir),
        sourceCommit = _source_commit(publication.projectDir),
        entries = publication.entries.map {
          case (path, json) => PublicationBundleEntry(path, _publication_bundle_key(path), json)
        }.map(_validate_entry).distinct.sortBy(_.path)
      )
      bundle
    }

    def registerMetadata(root: Path, name: String, entries: Vector[PublicationBundleEntry]): Unit = {
      _replace_metadata(root, name, entries, Vector.empty, Map.empty)
    }

    def replaceMetadata(
      root: Path,
      name: String,
      entries: Vector[PublicationBundleEntry],
      removePrefixes: Vector[String],
      expectedBundleDigests: Map[String, String]
    ): Unit =
      _replace_metadata(root, name, entries, removePrefixes, expectedBundleDigests)

    def remove(root: Path, name: String): Unit = {
      _with_registry_lock(root) { realroot =>
        val target = _locked_target(realroot, name, requireexisting = true)
        _load_bundle(target, name).getOrElse(RAISE.invalidArgumentFault(s"Publication bundle not found: ${name}"))
        Files.delete(target)
      }
    }

    private def _load_bundle(target: Path, expectedname: String): Option[PublicationBundle] = {
      if (Files.isRegularFile(target, LinkOption.NOFOLLOW_LINKS)) {
        val json = Json.parse(Files.readString(target, StandardCharsets.UTF_8))
        if ((json \ "type").asOpt[String].contains("publication-bundle")) {
          val entriesjson = (json \ "entries") match {
            case JsDefined(value) => value
            case JsUndefined() =>
              RAISE.invalidArgumentFault(s"Invalid publication bundle entries: entries are missing: ${target.getFileName}")
          }
          val entriesarray = entriesjson match {
            case JsArray(values) => values.toVector
            case _ =>
              RAISE.invalidArgumentFault(s"Invalid publication bundle entries: entries must be an array: ${target.getFileName}")
          }
          Some(PublicationBundle(
            publication = _declared_bundle_name(json, target.getFileName.toString, expectedname),
            publicationPath = (json \ "publication" \ "path").asOpt[String],
            sourceRepository = (json \ "sourceRepository").asOpt[String].getOrElse(""),
            sourcePath = (json \ "sourcePath").asOpt[String].getOrElse(""),
            sourceCommit = (json \ "sourceCommit").asOpt[String],
            entries = entriesarray.map {
              case entry: JsObject =>
                PublicationBundleEntry(
                  path = _validate_relative_metadata_path((entry \ "path").as[String]),
                  key = (entry \ "key").asOpt[String].getOrElse(_logical_key(_validate_relative_metadata_path((entry \ "path").as[String]))),
                  metadata = (entry \ "metadata").as[JsValue]
                )
              case _ =>
                RAISE.invalidArgumentFault(s"Invalid publication bundle entries: every entry must be an object: ${target.getFileName}")
            },
            raw = json.asOpt[JsObject]
          ))
        } else
          None
      } else {
        if (Files.exists(target, LinkOption.NOFOLLOW_LINKS))
          RAISE.invalidArgumentFault(s"Publication bundle must be a direct regular file: ${target.getFileName}")
        None
      }
    }

    private def _all_bundles(root: Path): Vector[PublicationBundle] =
      if (!Files.isDirectory(root))
        Vector.empty
      else {
        val stream = Files.list(root)
        try {
          stream.iterator().asScala.toVector.filter(_.getFileName.toString.endsWith(".json")).flatMap { path =>
            val filename = path.getFileName.toString
            _load_bundle(path, _filename_stem(filename))
          }
        } finally {
          stream.close()
        }
      }

    private def _check_collisions(root: Path, bundle: PublicationBundle): Unit = {
      val mine = bundle.entries.map(_.path).toSet
      val collisions = _all_bundles(root).filterNot(_.publication == bundle.publication).flatMap { other =>
        other.entries.map(_.path).filter(mine.contains).map(path => s"${path} (${other.publication})")
      }
      if (collisions.nonEmpty)
        RAISE.invalidArgumentFault(s"Publication registry path collision for ${bundle.publication}: ${collisions.sorted.mkString(", ")}")
      bundle.publicationPath.foreach { path =>
        val pathcollisions = _all_bundles(root).filterNot(_.publication == bundle.publication).filter(_.publicationPath.contains(path)).map(_.publication)
        if (pathcollisions.nonEmpty)
          RAISE.invalidArgumentFault(s"Publication registry publication.path collision for ${bundle.publication}: ${path} (${pathcollisions.sorted.mkString(", ")})")
      }
    }

    private def _replace_metadata(
      root: Path,
      name: String,
      entries: Vector[PublicationBundleEntry],
      removeprefixes: Vector[String],
      expectedbundledigests: Map[String, String]
    ): Unit =
      _with_registry_lock(root) { realroot =>
        _replace_metadata_locked(realroot, name, entries, removeprefixes, expectedbundledigests, createarticlemedia = false)
      }

    def replaceMetadataLocked(
      realroot: Path,
      name: String,
      entries: Vector[PublicationBundleEntry],
      removeprefixes: Vector[String],
      expectedbundledigests: Map[String, String],
      createarticlemedia: Boolean
    ): Unit =
      _replace_metadata_locked(realroot, name, entries, removeprefixes, expectedbundledigests, createarticlemedia)

    def bundleBytesLocked(realroot: Path, name: String): Option[Vector[Byte]] = {
      val target = _locked_target(realroot, name, requireexisting = false)
      if (Files.exists(target, LinkOption.NOFOLLOW_LINKS)) Some(Files.readAllBytes(target).toVector) else None
    }

    def restoreBundleLocked(
      realroot: Path,
      name: String,
      bytes: Option[Vector[Byte]],
      expectedbundledigests: Map[String, String]
    ): Unit = {
      if (bytes == null || expectedbundledigests == null)
        RAISE.invalidArgumentFault("Publication bundle rollback inputs must be defined")
      val target = _locked_target(realroot, name, requireexisting = false)
      bytes match {
        case Some(value) => _write_exact_bundle(target, value.toArray)
        case None => Files.deleteIfExists(target)
      }
      validateRegistrySnapshotLocked(realroot, expectedbundledigests)
    }

    def validateRegistrySnapshotLocked(realroot: Path, expectedbundledigests: Map[String, String]): Unit = {
      if (realroot == null || !Files.isDirectory(realroot) || expectedbundledigests == null)
        RAISE.invalidArgumentFault("Publication metadata replacement inputs must be defined")
      if (realroot.toAbsolutePath.normalize() != realroot || realroot.toRealPath() != realroot)
        RAISE.invalidArgumentFault(s"Publication registry replacement requires a canonical real root: $realroot")
      val actual = CozyArticleMediaRegistry.load(realroot).bundleDigests
      if (actual != expectedbundledigests)
        RAISE.invalidArgumentFault("Publication bundle stale configured snapshot")
    }

    private def _replace_metadata_locked(
      realroot: Path,
      name: String,
      entries: Vector[PublicationBundleEntry],
      removeprefixes: Vector[String],
      expectedbundledigests: Map[String, String],
      createarticlemedia: Boolean
    ): Unit = {
      if (realroot == null || !Files.isDirectory(realroot) || entries == null || removeprefixes == null || expectedbundledigests == null)
        RAISE.invalidArgumentFault("Publication metadata replacement inputs must be defined")
      if (realroot.toAbsolutePath.normalize() != realroot || realroot.toRealPath() != realroot)
        RAISE.invalidArgumentFault(s"Publication registry replacement requires a canonical real root: $realroot")
      if (createarticlemedia && name != "article-media")
        RAISE.invalidArgumentFault("Publication metadata creation is limited to article-media")
      val target = _locked_target(realroot, name, requireexisting = !createarticlemedia)
      _validate_expected_bundle_digests(realroot, expectedbundledigests)
      val old = _load_bundle(target, name).getOrElse {
        if (createarticlemedia)
          PublicationBundle("article-media", None, "", ".", None, Vector.empty)
        else
          RAISE.invalidArgumentFault(s"Publication bundle not found: ${name}")
      }
      val validatedentries = entries.map(_validate_entry)
      val replacepaths = validatedentries.map(_.path).toSet
      val removals = removeprefixes.map(_validate_metadata_removal).distinct
      val bundle = old.copy(
        entries = (old.entries.filterNot { entry =>
          replacepaths.contains(entry.path) || removals.exists { removal =>
            if (removal.exact) entry.path == removal.path else entry.path.startsWith(removal.path)
          }
        } ++ validatedentries).distinct.sortBy(_.path)
      )
      _check_collisions(realroot, bundle)
      _write_bundle(target, bundle)
    }

    def withRegistryLock[A](root: Path)(f: Path => A): A =
      _with_registry_lock(root)(f)

    def captureSiteRootEvidence(root: Path): SiteRootEvidence = {
      val lexical = _site_root_path(root)
      val real = _site_root_real_path(lexical)
      val attributes = _site_root_attributes(lexical)
      SiteRootEvidence(lexical, real, attributes.fileKey())
    }

    def validateSiteRootEvidence(evidence: SiteRootEvidence): Unit = {
      if (evidence == null)
        RAISE.invalidArgumentFault("Existing publication registry root evidence must be defined")
      val actual = captureSiteRootEvidence(evidence.lexical)
      if (actual != evidence)
        RAISE.invalidArgumentFault("Publication registry root evidence has changed")
    }

    def withExistingRegistryLock[A](evidence: SiteRootEvidence)(f: Path => A): A = {
      if (evidence == null || f == null)
        RAISE.invalidArgumentFault("Existing publication registry lock inputs must be defined")
      validateSiteRootEvidence(evidence)
      val realroot = evidence.real
      val monitor = _registry_locks.computeIfAbsent(realroot.toString, new java.util.function.Function[String, Object] {
        override def apply(value: String): Object = new Object()
      })
      monitor.synchronized {
        validateSiteRootEvidence(evidence)
        val lockpath = realroot.resolve(".cozy-publication-registry.lock")
        if (!Files.exists(lockpath, LinkOption.NOFOLLOW_LINKS))
          try Files.createFile(lockpath)
          catch {
            case _: FileAlreadyExistsException =>
          }
        _validate_lock_path(lockpath)
        val channel = FileChannel.open(lockpath, StandardOpenOption.WRITE)
        try {
          val lock = channel.lock()
          try f(realroot) finally lock.release()
        } finally channel.close()
      }
    }

    private def _with_registry_lock[A](root: Path)(f: Path => A): A = {
      Files.createDirectories(root)
      val realroot = root.toAbsolutePath.normalize().toRealPath()
      val monitor = _registry_locks.computeIfAbsent(realroot.toString, new java.util.function.Function[String, Object] {
        override def apply(value: String): Object = new Object()
      })
      monitor.synchronized {
        val lockpath = realroot.resolve(".cozy-publication-registry.lock")
        if (!Files.exists(lockpath, LinkOption.NOFOLLOW_LINKS))
          try Files.createFile(lockpath)
          catch {
            case _: FileAlreadyExistsException =>
          }
        _validate_lock_path(lockpath)
        val channel = FileChannel.open(lockpath, StandardOpenOption.WRITE)
        try {
          val lock = channel.lock()
          try f(realroot) finally lock.release()
        } finally channel.close()
      }
    }

    private def _site_root_path(root: Path): Path = {
      val lexical = Option(root).map(_.toAbsolutePath.normalize()).getOrElse(
        RAISE.invalidArgumentFault("Configured publication root must be defined")
      )
      if (Files.isSymbolicLink(lexical) || !Files.isDirectory(lexical, LinkOption.NOFOLLOW_LINKS))
        RAISE.invalidArgumentFault(s"Configured publication root must be an existing direct non-symlink directory: $lexical")
      lexical
    }

    private def _site_root_real_path(lexical: Path): Path = {
      val real = try lexical.toRealPath() catch {
        case NonFatal(e) => RAISE.invalidArgumentFault(s"Configured publication root cannot be resolved: ${e.getMessage}")
      }
      if (real != lexical)
        RAISE.invalidArgumentFault(s"Configured publication root must not use a lexical or symlink alias: $lexical")
      real
    }

    private def _site_root_attributes(path: Path): BasicFileAttributes = {
      val attributes = try Files.readAttributes(path, classOf[BasicFileAttributes], LinkOption.NOFOLLOW_LINKS) catch {
        case NonFatal(e) => RAISE.invalidArgumentFault(s"Configured publication root attributes cannot be read: ${e.getMessage}")
      }
      if (attributes.fileKey() == null)
        RAISE.invalidArgumentFault(s"Configured publication root has no stable file identity: $path")
      attributes
    }

    private def _validate_lock_path(lockpath: Path): Unit =
      if (Files.exists(lockpath, LinkOption.NOFOLLOW_LINKS) &&
        (Files.isSymbolicLink(lockpath) || !Files.isRegularFile(lockpath, LinkOption.NOFOLLOW_LINKS)))
        RAISE.invalidArgumentFault(s"Publication registry lock must be a direct regular file: ${lockpath}")

    private def _locked_target(realroot: Path, name: String, requireexisting: Boolean): Path = {
      val validatedname = _validate_bundle_name(name)
      val target = realroot.resolve(s"$validatedname.json").normalize()
      if (!target.startsWith(realroot))
        RAISE.invalidArgumentFault(s"Publication bundle target escapes configured root: $name")
      if (Files.isSymbolicLink(target))
        RAISE.invalidArgumentFault(s"Publication bundle target must not be a symbolic link: $validatedname")
      val exists = Files.exists(target, LinkOption.NOFOLLOW_LINKS)
      if (exists && !Files.isRegularFile(target, LinkOption.NOFOLLOW_LINKS))
        RAISE.invalidArgumentFault(s"Publication bundle target must be a direct regular file: $validatedname")
      if (requireexisting && !exists)
        RAISE.invalidArgumentFault(s"Publication bundle not found: $validatedname")
      if (exists)
        _load_bundle(target, validatedname).getOrElse(
          RAISE.invalidArgumentFault(s"Publication bundle declaration is invalid: $validatedname")
        )
      target
    }

    private def _validate_expected_bundle_digests(realroot: Path, expectedbundledigests: Map[String, String]): Unit =
      if (expectedbundledigests.nonEmpty) {
        expectedbundledigests.keys.foreach(_validate_bundle_name)
        val actual = CozyArticleMediaRegistry.load(realroot).bundleDigests
        if (actual != expectedbundledigests)
          RAISE.invalidArgumentFault("Publication bundle stale configured snapshot")
      }

    private def _validate_metadata_removal(value: String): MetadataRemoval = {
      val raw = Option(value).getOrElse(RAISE.invalidArgumentFault("Publication bundle metadata removal must be defined"))
      val path = _validate_relative_metadata_path(raw.stripSuffix("/"))
      if (raw.endsWith(".json")) MetadataRemoval(path, exact = true)
      else MetadataRemoval(path + "/", exact = false)
    }

    private def _write_bundle(target: Path, bundle: PublicationBundle): Unit = {
      val name = _validate_bundle_name(bundle.publication)
      if (target.getFileName.toString != s"$name.json")
        RAISE.invalidArgumentFault(s"Publication bundle target does not match declaration: $name")
      val temporary = Files.createTempFile(target.getParent, target.getFileName.toString + ".", ".tmp")
      try {
        val bytes = (Json.prettyPrint(_bundle_json(bundle)) + "\n").getBytes(StandardCharsets.UTF_8)
        Files.write(temporary, bytes, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING)
        val channel = FileChannel.open(temporary, StandardOpenOption.WRITE)
        try channel.force(true) finally channel.close()
        try {
          Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
        } catch {
          case _: AtomicMoveNotSupportedException =>
            RAISE.invalidArgumentFault(s"Atomic publication bundle replacement is not supported: ${target}")
        }
      } finally {
        Files.deleteIfExists(temporary)
      }
    }

    private def _write_exact_bundle(target: Path, bytes: Array[Byte]): Unit = {
      val temporary = Files.createTempFile(target.getParent, target.getFileName.toString + ".", ".rollback.tmp")
      try {
        Files.write(temporary, bytes, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING)
        val channel = FileChannel.open(temporary, StandardOpenOption.WRITE)
        try channel.force(true) finally channel.close()
        try Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
        catch {
          case _: AtomicMoveNotSupportedException =>
            RAISE.invalidArgumentFault(s"Atomic publication bundle rollback is not supported: $target")
        }
      } finally Files.deleteIfExists(temporary)
    }

    private def _bundle_json(p: PublicationBundle): JsValue = {
      val existingpublication = p.raw.flatMap(x => (x \ "publication").asOpt[JsObject]).getOrElse(Json.obj())
      val publication = existingpublication ++ (Json.obj(
        "name" -> p.publication
      ) ++ p.publicationPath.map(x => Json.obj("path" -> x)).getOrElse(Json.obj()))
      val canonical = Json.obj(
        "schema" -> _schema,
        "type" -> "publication-bundle",
        "publication" -> publication,
        "sourceRepository" -> p.sourceRepository,
        "sourcePath" -> p.sourcePath,
        "sourceCommit" -> p.sourceCommit,
        "entries" -> JsArray(p.entries.sortBy(_.path).map(_entry_json))
      )
      p.raw.map(_ ++ canonical).getOrElse(canonical)
    }

    private def _entry_json(p: PublicationBundleEntry): JsValue =
      Json.obj(
        "path" -> p.path,
        "key" -> p.key,
        "metadata" -> p.metadata
      )

    private def _bundle_entries(staging: Path): Vector[PublicationBundleEntry] =
      _relative_files(staging).filter(x => x.endsWith(".json") && _is_public_metadata_entry(x)).map { rel =>
        val path = _validate_relative_metadata_path(rel)
        PublicationBundleEntry(
          path = path,
          key = _logical_key(path),
          metadata = Json.parse(Files.readString(staging.resolve(path), StandardCharsets.UTF_8))
        )
      }.sortBy(_.path)

    private def _is_public_metadata_entry(path: String): Boolean =
      path.startsWith("metadata/") && !path.contains("/files/")

    private def _validate_entry(p: PublicationBundleEntry): PublicationBundleEntry =
      {
        val path = _validate_relative_metadata_path(p.path)
        p.copy(path = path, key = _logical_key(path))
      }

    private def _relative_files(root: Path): Vector[String] =
      if (!Files.exists(root))
        Vector.empty
      else {
        val stream = Files.walk(root)
        try {
          stream.iterator().asScala.toVector.filter(Files.isRegularFile(_)).map { path =>
            root.relativize(path).toString.replace('\\', '/')
          }.map(_validate_relative_path).distinct.sorted
        } finally {
          stream.close()
        }
      }

    private def _validate_relative_metadata_path(path: String): String = {
      val rel = _validate_relative_path(path)
      if (!rel.startsWith("metadata/"))
        RAISE.invalidArgumentFault(s"Publication bundle entry must be under metadata/: ${path}")
      rel
    }

    private def _validate_relative_path(path: String): String = {
      if (path == null || path.contains('\\') || path.contains("\u0000") || path.startsWith("/"))
        RAISE.invalidArgumentFault(s"Invalid publication bundle path: ${path}")
      val segments = path.split("/", -1).toVector
      if (segments.isEmpty || segments.exists(x => x.isEmpty || x == "." || x == ".."))
        RAISE.invalidArgumentFault(s"Invalid publication bundle path: ${path}")
      val normalized = Paths.get(path).normalize()
      if (normalized.isAbsolute || normalized.startsWith(".."))
        RAISE.invalidArgumentFault(s"Invalid publication bundle path: ${path}")
      normalized.toString.replace('\\', '/')
    }

    private def _validate_bundle_name(value: String): String = {
      val name = Option(value).getOrElse("")
      if (!name.matches("[A-Za-z0-9][A-Za-z0-9._-]*") || name == "." || name == ".." ||
        name.contains('/') || name.contains('\\') || name.contains('\u0000'))
        RAISE.invalidArgumentFault(s"Publication bundle name must be a safe filename segment: $value")
      name
    }

    private def _filename_stem(filename: String): String = {
      if (!filename.endsWith(".json"))
        RAISE.invalidArgumentFault(s"Publication bundle filename must end with .json: $filename")
      _validate_bundle_name(filename.stripSuffix(".json"))
    }

    private def _declared_bundle_name(json: JsValue, filename: String, expectedname: String): String = {
      val declared = (json \ "publication" \ "name").asOpt[String].map(_validate_bundle_name).getOrElse(
        RAISE.invalidArgumentFault(s"Publication bundle has invalid publication.name: $filename")
      )
      if (declared != expectedname)
        RAISE.invalidArgumentFault(s"Publication bundle declaration does not match filename or requested name: $filename")
      declared
    }

    private def _logical_key(path: String): String = {
      val stripped = path.replaceFirst("""\.[^.]+$""", "")
      if (stripped.startsWith("metadata/"))
        stripped.substring("metadata/".length)
      else
        stripped
    }

    private def _source_repository(projectdir: Path): String =
      _git_toplevel(projectdir).map(_.getFileName.toString).getOrElse(projectdir.getFileName.toString)

    private def _source_path(projectdir: Path): String = {
      val normalized = projectdir.toAbsolutePath.normalize()
      _git_toplevel(projectdir).filter(root => normalized.startsWith(root)).map { root =>
        val relative = root.relativize(normalized).toString.replace('\\', '/')
        if (relative.isEmpty) "." else relative
      }.getOrElse(".")
    }

    private def _source_commit(projectdir: Path): Option[String] =
      Try(Process(Seq("git", "-C", projectdir.toString, "rev-parse", "HEAD")).!!.trim).toOption.filter(_.nonEmpty)

    private def _git_toplevel(projectdir: Path): Option[Path] =
      Try(Process(Seq("git", "-C", projectdir.toString, "rev-parse", "--show-toplevel")).!!.trim).toOption.
        filter(_.nonEmpty).map(x => Paths.get(x).toAbsolutePath.normalize())
  }
}
