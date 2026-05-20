package cozy.publication

import org.goldenport.RAISE
import play.api.libs.json._
import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path, Paths}
import java.security.MessageDigest
import scala.util.Try
import scala.collection.JavaConverters._

/*
 * @since   May. 20, 2026
 * @version May. 20, 2026
 * @author  ASAMI, Tomoharu
 */
private[cozy] object CozyWarehouseIndexer {
  private val _schema = "cozy.publish-project.v1"
  private val _slug_pattern = "^[a-z0-9][a-z0-9-]*$".r
  private val _checksum_extensions = Set("sha1", "md5")
  private val _artifact_extensions = Set("jar", "pom", "car", "sar", "zip")

  final case class MavenCoordinate(groupId: String, artifactId: String) {
    def path: String = groupId.replace('.', '/') + "/" + artifactId
    def key: String = s"${groupId}:${artifactId}"
  }
  final case class IndexedFile(
    layer: String,
    artifactType: String,
    groupId: Option[String],
    artifactId: Option[String],
    sampleName: Option[String],
    version: String,
    classifier: Option[String],
    extension: String,
    path: String,
    name: String,
    size: Long,
    sha256: String,
    sha1: Option[String],
    md5: Option[String]
  )
  final case class MavenArtifact(coordinate: MavenCoordinate, versions: Vector[String], latestRelease: Option[String], files: Vector[IndexedFile])
  final case class RepositoryArtifact(kind: String, versions: Vector[String], files: Vector[IndexedFile])
  final case class DownloadArtifact(kind: String, versions: Vector[String], files: Vector[IndexedFile])
  final case class IndexResult(name: String, title: String, maven: Vector[MavenArtifact], repository: Vector[RepositoryArtifact], download: Vector[DownloadArtifact])

  def index(args: List[String]): Unit = {
    val warehousedir = _warehouse_dir(args)
    if (!Files.isDirectory(warehousedir))
      RAISE.invalidArgumentFault(s"Warehouse directory does not exist: ${warehousedir}")
    val savedir = _required_path(args, "save")
    val name = _value(args, "name").map(_validate_name).getOrElse(RAISE.invalidArgumentFault("Missing --name"))
    val title = _value(args, "title").getOrElse(name)
    val repositorykinds = _csv(args, "repository-artifacts").map(_.toLowerCase(java.util.Locale.ROOT)).filter(_.nonEmpty)
    val repositorymodules = _csv(args, "repository-modules").filter(_.nonEmpty) match {
      case Vector() => Vector(name)
      case xs => xs
    }
    val downloadsamples = _csv(args, "download-samples").filter(_.nonEmpty) match {
      case Vector() => Vector(name)
      case xs => xs
    }
    val downloadpublicationpaths = downloadsamples.map(publication => publication -> _publication_path(savedir, publication)).toMap
    val result = IndexResult(
      name = name,
      title = title,
      maven = Vector.empty,
      repository = Vector.empty,
      download = Vector(_index_download_samples(warehousedir, downloadsamples, downloadpublicationpaths))
    )
    _check_repository_consistency(warehousedir, savedir, name, repositorykinds, repositorymodules)
    _check_download_consistency(warehousedir, savedir, name, downloadsamples, downloadpublicationpaths)
    _write_release(result, savedir)
  }

  def publishMaven(
    warehousedir: Path,
    savedir: Path,
    name: String,
    title: String,
    coordinates: Option[String]
  ): Unit = {
    if (!Files.isDirectory(warehousedir))
      RAISE.invalidArgumentFault(s"Warehouse directory does not exist: ${warehousedir}")
    val configured = _csv(coordinates).map(_coordinate)
    val coordinatelist = if (configured.nonEmpty) configured else _discover_maven_coordinates(warehousedir)
    val result = IndexResult(
      name = name,
      title = title,
      maven = coordinatelist.map(_index_maven(warehousedir, _)),
      repository = Vector.empty,
      download = Vector.empty
    )
    _write_maven(result, savedir)
  }

  private def _write_release(p: IndexResult, savedir: Path): Unit =
    CozyPublicationCompiler.registerMetadata(savedir, p.name, Vector(
      s"metadata/releases/${p.name}.json" -> _release_json(p)
    ))

  private def _write_maven(p: IndexResult, savedir: Path): Unit =
    CozyPublicationCompiler.registerMetadata(savedir, p.name, Vector(
      s"metadata/artifacts/maven/${p.name}.json" -> _maven_json(p),
      s"metadata/releases/${p.name}.json" -> _release_json(p)
    ))

  private def _check_repository_consistency(
    warehousedir: Path,
    savedir: Path,
    name: String,
    repositorykinds: Vector[String],
    repositorymodules: Vector[String]
  ): Unit = {
    _expected_paths(savedir, name, s"metadata/artifacts/repository/${name}.json") match {
      case xs if xs.nonEmpty =>
        _check_paths_exist(warehousedir, "repository", xs)
      case _ =>
        val expected = for {
          kind <- repositorykinds
          module <- repositorymodules
        } yield warehousedir.resolve("repository").resolve(kind).resolve(module)
        val existing = expected.exists(Files.exists(_))
        if (existing)
          RAISE.invalidArgumentFault(s"Warehouse repository artifacts exist for ${name}, but publication registry metadata/artifacts/repository/${name}.json is missing")
    }
  }

  private def _check_download_consistency(
    warehousedir: Path,
    savedir: Path,
    name: String,
    downloadsamples: Vector[String],
    publicationpaths: Map[String, Option[String]]
  ): Unit = {
    _expected_paths(savedir, name, s"metadata/artifacts/download/${name}.json") match {
      case xs if xs.nonEmpty =>
        _check_paths_exist(warehousedir, "download", xs)
      case _ =>
        val existing = downloadsamples.exists { publication =>
          _download_scan_bases(publication, publicationpaths.getOrElse(publication, None)).exists { base =>
            Files.exists(warehousedir.resolve("repository/download").resolve(base)) ||
              Files.exists(warehousedir.resolve("download").resolve(base))
          }
        }
        if (existing)
          RAISE.invalidArgumentFault(s"Warehouse download artifacts exist for ${name}, but publication registry metadata/artifacts/download/${name}.json is missing")
    }
  }

  private def _expected_paths(savedir: Path, name: String, path: String): Vector[String] =
    _bundle_entry(savedir, name, path) match {
      case None => Vector.empty
      case Some(json) =>
      (json \ "artifact" \ "files").asOpt[Vector[JsObject]].toVector.flatten.flatMap { x =>
        (x \ "warehousePath").asOpt[String].orElse((x \ "path").asOpt[String])
      }.filter(_.nonEmpty).distinct.sorted
    }

  private def _publication_path(savedir: Path, publication: String): Option[String] = {
    Vector(
      s"metadata/samples/${publication}/metadata.json",
      s"metadata/projects/${publication}/metadata.json"
    ).flatMap(path => _bundle_entry(savedir, publication, path)).
      flatMap(json => (json \ "publication" \ "path").asOpt[String]).
      headOption.map(CozyPublicationPaths.validatePublicationPath)
  }

  private def _bundle_entry(savedir: Path, name: String, path: String): Option[JsValue] = {
    val bundle = savedir.resolve(s"${name}.json")
    if (!Files.isRegularFile(bundle))
      None
    else {
      val json = Json.parse(Files.readString(bundle, StandardCharsets.UTF_8))
      (json \ "entries").asOpt[Vector[JsObject]].getOrElse(Vector.empty).collectFirst {
        case entry if (entry \ "path").asOpt[String].contains(path) => (entry \ "metadata").as[JsValue]
      }
    }
  }

  private def _check_paths_exist(warehousedir: Path, layer: String, paths: Vector[String]): Unit = {
    val missing = paths.filterNot(path => Files.isRegularFile(warehousedir.resolve(path)))
    if (missing.nonEmpty)
      RAISE.invalidArgumentFault(s"Missing ${layer} artifact(s) in warehouse: ${missing.mkString(", ")}")
  }

  private def _index_maven(warehousedir: Path, coordinate: MavenCoordinate): MavenArtifact = {
    val artifactdir = warehousedir.resolve("maven").resolve(coordinate.path)
    val files =
      if (Files.isDirectory(artifactdir)) {
        val stream = Files.walk(artifactdir)
        try {
          stream.iterator().asScala.toVector.collect {
            case p if Files.isRegularFile(p) && _is_artifact_file(p) =>
              val version = artifactdir.relativize(p).iterator().asScala.toVector.headOption.map(_.toString).getOrElse("")
              val parsed = _parse_maven_file(coordinate.artifactId, version, p.getFileName.toString)
              _indexed_file(
                warehousedir,
                p,
                layer = "maven",
                artifacttype = parsed._2,
                groupid = Some(coordinate.groupId),
                artifactid = Some(coordinate.artifactId),
                samplename = None,
                version = version,
                classifier = parsed._1
              )
          }.sortBy(_.path)
        } finally {
          stream.close()
        }
      } else {
        Vector.empty
      }
    val versions = _sort_versions(files.map(_.version).distinct)
    MavenArtifact(coordinate, versions, _latest_release(versions), files)
  }

  private def _discover_maven_coordinates(warehousedir: Path): Vector[MavenCoordinate] = {
    val root = warehousedir.resolve("maven")
    if (!Files.isDirectory(root))
      Vector.empty
    else {
      val stream = Files.walk(root)
      try {
        stream.iterator().asScala.toVector.collect {
          case p if Files.isRegularFile(p) && _is_artifact_file(p) =>
            val rel = root.relativize(p).iterator().asScala.toVector.map(_.toString)
            rel match {
              case xs if xs.size >= 4 =>
                val groupid = xs.dropRight(3).mkString(".")
                val artifactid = xs(xs.size - 3)
                Some(MavenCoordinate(groupid, artifactid))
              case _ =>
                None
            }
        }.flatten.distinct.sortBy(_.key)
      } finally {
        stream.close()
      }
    }
  }

  private def _index_repository(warehousedir: Path, kind: String, modules: Vector[String]): RepositoryArtifact = {
    val files = modules.flatMap { module =>
      val artifactdir = warehousedir.resolve("repository").resolve(kind).resolve(module)
      if (Files.isDirectory(artifactdir)) {
        val stream = Files.walk(artifactdir)
        try {
          stream.iterator().asScala.toVector.collect {
            case p if Files.isRegularFile(p) && p.getFileName.toString.toLowerCase(java.util.Locale.ROOT).endsWith(s".${kind}") =>
              _indexed_file(
                warehousedir,
                p,
                layer = "repository",
                artifacttype = kind,
                groupid = None,
                artifactid = Some(module),
                samplename = None,
                version = _infer_repository_version(warehousedir, p, kind).getOrElse("unknown"),
                classifier = None
              )
          }
        } finally {
          stream.close()
        }
      } else {
        Vector.empty
      }
    }.sortBy(_.path)
    RepositoryArtifact(kind, _sort_versions(files.map(_.version).distinct), files)
  }

  private def _index_download_samples(
    warehousedir: Path,
    publications: Vector[String],
    publicationpaths: Map[String, Option[String]]
  ): DownloadArtifact = {
    val files = publications.flatMap { publication =>
      val indexed = _download_scan_bases(publication, publicationpaths.getOrElse(publication, None)).flatMap { base =>
        _index_download_sample_base(warehousedir, publication, warehousedir.resolve("repository/download").resolve(base)) ++
          _index_download_sample_base(warehousedir, publication, warehousedir.resolve("download").resolve(base))
      }
      _prefer_first_download_files(indexed)
    }.sortBy(_.path)
    DownloadArtifact("sample-zip", _sort_versions(files.map(_.version).distinct), files)
  }

  private def _download_scan_bases(publication: String, publicationpath: Option[String]): Vector[String] = {
    val canonical = CozyPublicationPaths.downloadBase(publication, publicationpath)
    // Transitional compatibility only. Remove the legacy samples/<publication>
    // scan after existing warehouses have migrated to publication.path-based URLs.
    Vector(canonical, s"samples/${publication}").distinct
  }

  private def _index_download_sample_base(warehousedir: Path, publication: String, dir: Path): Vector[IndexedFile] =
    if (Files.isDirectory(dir)) {
      val stream = Files.walk(dir)
      try {
        stream.iterator().asScala.toVector.collect {
          case p if Files.isRegularFile(p) && p.getFileName.toString.toLowerCase(java.util.Locale.ROOT).endsWith(".zip") =>
            val rel = dir.relativize(p).iterator().asScala.toVector.map(_.toString)
            val collectionarchive = rel.size == 2
            val filename = p.getFileName.toString
            val (sample, version) =
              if (collectionarchive) {
                None -> rel.headOption.getOrElse(_infer_version(filename, "zip").getOrElse("unknown"))
              } else {
                _download_sample_and_version(rel, filename)
              }
            _indexed_file(
              warehousedir,
              p,
              layer = "download",
              artifacttype = if (collectionarchive) "sample-collection-zip" else "sample-zip",
              groupid = None,
              artifactid = Some(publication),
              samplename = sample,
              version = version,
              classifier = None
            )
        }
      } finally {
        stream.close()
      }
    } else {
      Vector.empty
    }

  private def _prefer_first_download_files(files: Vector[IndexedFile]): Vector[IndexedFile] =
    files.foldLeft(Vector.empty[IndexedFile]) { (z, file) =>
      val key = (file.artifactType, file.sampleName, file.version, file.name)
      if (z.exists(x => (x.artifactType, x.sampleName, x.version, x.name) == key))
        z
      else
        z :+ file
    }

  private def _download_sample_and_version(rel: Vector[String], filename: String): (Option[String], String) =
    rel match {
      case Vector(version, sample, _) if filename == s"${sample}-${version}.zip" =>
        Some(sample) -> version
      // Legacy download layout: <sample>/<version>/<sample>-<version>.zip.
      // Keep this only while index-warehouse accepts pre-migration warehouses.
      case Vector(sample, version, _) =>
        Some(sample) -> version
      case _ =>
        rel.headOption -> _infer_version(filename, "zip").getOrElse("unknown")
    }

  private def _indexed_file(
    warehousedir: Path,
    path: Path,
    layer: String,
    artifacttype: String,
    groupid: Option[String],
    artifactid: Option[String],
    samplename: Option[String],
    version: String,
    classifier: Option[String]
  ): IndexedFile = {
    val rel = warehousedir.relativize(path).toString.replace('\\', '/')
    val extension = path.getFileName.toString.reverse.takeWhile(_ != '.').reverse
    IndexedFile(
      layer = layer,
      artifactType = artifacttype,
      groupId = groupid,
      artifactId = artifactid,
      sampleName = samplename,
      version = version,
      classifier = classifier,
      extension = extension,
      path = rel,
      name = path.getFileName.toString,
      size = Files.size(path),
      sha256 = _sha256(path),
      sha1 = _sidecar(path, "sha1"),
      md5 = _sidecar(path, "md5")
    )
  }

  private def _maven_yaml(p: IndexResult): String =
    _yaml_header("maven-artifact") +
      _project_yaml(p) +
      s"""artifact:
         |  layer: "maven"
         |  status: ${_yaml_string(if (p.maven.exists(_.files.nonEmpty)) "available" else "missing")}
         |  coordinates:
         |${p.maven.map(_maven_coordinate_yaml).mkString}
         |  files:
         |${p.maven.flatMap(_.files).map(_file_yaml).mkString}""".stripMargin

  private def _maven_json(p: IndexResult): JsValue =
    Json.obj(
      "schema" -> _schema,
      "type" -> "maven-artifact",
      "project" -> _project_json(p),
      "artifact" -> Json.obj(
        "layer" -> "maven",
        "status" -> (if (p.maven.exists(_.files.nonEmpty)) "available" else "missing"),
        "coordinates" -> JsArray(p.maven.map { x =>
          Json.obj(
            "groupId" -> x.coordinate.groupId,
            "artifactId" -> x.coordinate.artifactId,
            "versions" -> x.versions,
            "latestRelease" -> x.latestRelease
          )
        }),
        "files" -> JsArray(p.maven.flatMap(_.files).map(_file_json))
      )
    )

  private def _repository_yaml(p: IndexResult): String =
    _yaml_header("repository-artifact") +
      _project_yaml(p) +
      s"""artifact:
         |  layer: "repository"
         |  status: ${_yaml_string(if (p.repository.exists(_.files.nonEmpty)) "available" else "missing")}
         |  kinds:
         |${p.repository.map(_repository_kind_yaml).mkString}
         |  files:
         |${p.repository.flatMap(_.files).map(_file_yaml).mkString}""".stripMargin

  private def _repository_json(p: IndexResult): JsValue =
    Json.obj(
      "schema" -> _schema,
      "type" -> "repository-artifact",
      "project" -> _project_json(p),
      "artifact" -> Json.obj(
        "layer" -> "repository",
        "status" -> (if (p.repository.exists(_.files.nonEmpty)) "available" else "missing"),
        "kinds" -> JsArray(p.repository.map { x =>
          Json.obj(
            "type" -> x.kind,
            "versions" -> x.versions,
            "latestRelease" -> _latest_release(x.versions)
          )
        }),
        "files" -> JsArray(p.repository.flatMap(_.files).map(_file_json))
      )
    )

  private def _download_yaml(p: IndexResult): String =
    _yaml_header("download-artifact") +
      _project_yaml(p) +
      s"""artifact:
         |  layer: "download"
         |  status: ${_yaml_string(if (p.download.exists(_.files.nonEmpty)) "available" else "missing")}
         |  kinds:
         |${p.download.map(_download_kind_yaml).mkString}
         |  files:
         |${p.download.flatMap(_.files).map(_file_yaml).mkString}""".stripMargin

  private def _download_json(p: IndexResult): JsValue =
    Json.obj(
      "schema" -> _schema,
      "type" -> "download-artifact",
      "project" -> _project_json(p),
      "artifact" -> Json.obj(
        "layer" -> "download",
        "status" -> (if (p.download.exists(_.files.nonEmpty)) "available" else "missing"),
        "kinds" -> JsArray(p.download.map { x =>
          Json.obj(
            "type" -> x.kind,
            "versions" -> x.versions,
            "latestRelease" -> _latest_release(x.versions)
          )
        }),
        "files" -> JsArray(p.download.flatMap(_.files).map(_file_json))
      )
    )

  private def _release_yaml(p: IndexResult): String =
    _yaml_header("release-history") +
      _project_yaml(p) +
      s"""release:
         |  name: ${_yaml_string(p.name)}
         |  latest: ${_yaml_string(_latest_release(_release_versions_ascending(p)).getOrElse(""))}
         |  versions:
         |${_release_versions(p).map(v => _release_version_yaml(v, p)).mkString}""".stripMargin

  private def _release_json(p: IndexResult): JsValue =
    Json.obj(
      "schema" -> _schema,
      "type" -> "release-history",
      "project" -> _project_json(p),
      "release" -> Json.obj(
        "name" -> p.name,
        "latest" -> _latest_release(_release_versions_ascending(p)),
        "versions" -> JsArray(_release_versions(p).map { v =>
          Json.obj(
            "version" -> v,
            "artifacts" -> JsArray(_release_artifacts(v, p))
          )
        })
      )
    )

  private def _release_versions(p: IndexResult): Vector[String] =
    _release_versions_ascending(p).reverse

  private def _release_versions_ascending(p: IndexResult): Vector[String] =
    _sort_versions((p.maven.flatMap(_.versions) ++ p.repository.flatMap(_.versions) ++ p.download.flatMap(_.versions)).filter(_ != "unknown").distinct)

  private def _release_artifacts(version: String, p: IndexResult): Vector[JsValue] =
    p.maven.flatMap(_.files).filter(_.version == version).map { f =>
      Json.obj(
        "layer" -> "maven",
        "groupId" -> f.groupId,
        "artifactId" -> f.artifactId,
        "warehousePath" -> f.path,
        "publicPath" -> _public_artifact_path(f.path)
      )
    } ++ p.repository.flatMap(_.files).filter(_.version == version).map { f =>
      Json.obj(
        "layer" -> "repository",
        "type" -> f.artifactType,
        "module" -> f.artifactId,
        "warehousePath" -> f.path,
        "publicPath" -> _public_artifact_path(f.path)
      )
    } ++ p.download.flatMap(_.files).filter(_.version == version).map { f =>
      Json.obj(
        "layer" -> "download",
        "type" -> f.artifactType,
        "publication" -> f.artifactId,
        "sample" -> f.sampleName,
        "warehousePath" -> f.path,
        "publicPath" -> _public_artifact_path(f.path)
      )
    }

  private def _release_version_yaml(version: String, p: IndexResult): String =
    s"""    - version: ${_yaml_string(version)}
       |      artifacts:
       |${_release_artifacts(version, p).map(x => s"        - ${Json.stringify(x)}\n").mkString}""".stripMargin

  private def _maven_coordinate_yaml(p: MavenArtifact): String =
    s"""    - group_id: ${_yaml_string(p.coordinate.groupId)}
       |      artifact_id: ${_yaml_string(p.coordinate.artifactId)}
       |      latest_release: ${_yaml_string(p.latestRelease.getOrElse(""))}
       |      versions: [${p.versions.map(_yaml_string).mkString(", ")}]
       |""".stripMargin

  private def _repository_kind_yaml(p: RepositoryArtifact): String =
    s"""    - type: ${_yaml_string(p.kind)}
       |      latest_release: ${_yaml_string(_latest_release(p.versions).getOrElse(""))}
       |      versions: [${p.versions.map(_yaml_string).mkString(", ")}]
       |""".stripMargin

  private def _download_kind_yaml(p: DownloadArtifact): String =
    s"""    - type: ${_yaml_string(p.kind)}
       |      latest_release: ${_yaml_string(_latest_release(p.versions).getOrElse(""))}
       |      versions: [${p.versions.map(_yaml_string).mkString(", ")}]
       |""".stripMargin

  private def _file_yaml(p: IndexedFile): String =
    s"""    - warehouse_path: ${_yaml_string(p.path)}
       |      public_path: ${_yaml_string(_public_artifact_path(p.path))}
       |      name: ${_yaml_string(p.name)}
      |      version: ${_yaml_string(p.version)}
      |      type: ${_yaml_string(p.artifactType)}
      |      sample: ${_yaml_string(p.sampleName.getOrElse(""))}
      |      extension: ${_yaml_string(p.extension)}
       |      classifier: ${_yaml_string(p.classifier.getOrElse(""))}
       |      size: ${p.size}
       |      sha256: ${_yaml_string(p.sha256)}
       |      sha1: ${_yaml_string(p.sha1.getOrElse(""))}
       |      md5: ${_yaml_string(p.md5.getOrElse(""))}
       |""".stripMargin

  private def _file_json(p: IndexedFile): JsValue =
    Json.obj(
      "layer" -> p.layer,
      "type" -> p.artifactType,
      "groupId" -> p.groupId,
      "artifactId" -> p.artifactId,
      "sampleName" -> p.sampleName,
      "version" -> p.version,
      "classifier" -> p.classifier,
      "extension" -> p.extension,
      "warehousePath" -> p.path,
      "publicPath" -> _public_artifact_path(p.path),
      "name" -> p.name,
      "size" -> p.size,
      "sha256" -> p.sha256,
      "sha1" -> p.sha1,
      "md5" -> p.md5
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

  private def _yaml_header(kind: String): String =
    s"""schema: ${_yaml_string(_schema)}
       |type: ${_yaml_string(kind)}
       |""".stripMargin

  private def _project_yaml(p: IndexResult): String =
    s"""project:
       |  name: ${_yaml_string(p.name)}
       |  title: ${_yaml_string(p.title)}
       |""".stripMargin

  private def _project_json(p: IndexResult): JsValue =
    Json.obj("name" -> p.name, "title" -> p.title)

  private def _write_pair(base: Path, yaml: String, json: JsValue): Unit = {
    _write_text(Paths.get(base.toString + ".yaml"), yaml)
    _write_text(Paths.get(base.toString + ".json"), Json.prettyPrint(json) + "\n")
  }

  private def _warehouse_dir(args: List[String]): Path =
    _value(args, "warehouse").orElse(_positional_args(args).headOption).
      map(p => Paths.get(p).toAbsolutePath.normalize()).
      getOrElse(RAISE.invalidArgumentFault("Missing warehouse directory for index-warehouse"))

  private def _required_path(args: List[String], key: String): Path =
    _value(args, key).
      map(p => Paths.get(p).toAbsolutePath.normalize()).
      getOrElse(RAISE.invalidArgumentFault(s"Missing --${key}"))

  private def _value(args: List[String], key: String): Option[String] = {
    val prefix = s"--${key}="
    args.collectFirst {
      case s if s.startsWith(prefix) => s.substring(prefix.length)
    }.orElse {
      args.sliding(2).collectFirst {
        case List(flag, value) if flag == s"--${key}" => value
      }
    }.map(_.trim).filter(_.nonEmpty)
  }

  private def _csv(args: List[String], key: String): Vector[String] =
    _value(args, key).toVector.flatMap(_.split(',')).map(_.trim).filter(_.nonEmpty)

  private def _csv(value: Option[String]): Vector[String] =
    value.toVector.flatMap(_.split(',')).map(_.trim).filter(_.nonEmpty)

  private def _positional_args(args: List[String]): Vector[String] = {
    val optionnameswithvalue = Set("warehouse", "save", "name", "title", "maven-coordinates", "repository-artifacts", "repository-modules", "download-samples")
    val b = Vector.newBuilder[String]
    var skipnext = false
    args.foreach { arg =>
      if (skipnext) {
        skipnext = false
      } else if (arg.startsWith("--")) {
        val key = arg.drop(2).takeWhile(_ != '=')
        if (!arg.contains("=") && optionnameswithvalue.contains(key))
          skipnext = true
      } else {
        b += arg
      }
    }
    b.result()
  }

  private def _coordinate(s: String): MavenCoordinate =
    s.split(':').toVector match {
      case Vector(groupid, artifactid) if groupid.nonEmpty && artifactid.nonEmpty =>
        MavenCoordinate(groupid, artifactid)
      case _ =>
        RAISE.invalidArgumentFault(s"Invalid Maven coordinate: ${s}. Expected groupId:artifactId")
    }

  private def _is_artifact_file(path: Path): Boolean = {
    val name = path.getFileName.toString
    val ext = name.reverse.takeWhile(_ != '.').reverse.toLowerCase(java.util.Locale.ROOT)
    _artifact_extensions.contains(ext) && !_checksum_extensions.contains(ext)
  }

  private def _parse_maven_file(artifactid: String, version: String, name: String): (Option[String], String) = {
    val ext = name.reverse.takeWhile(_ != '.').reverse
    val base = name.stripSuffix("." + ext)
    val prefix = s"${artifactid}-${version}"
    val classifier =
      if (base == prefix)
        None
      else if (base.startsWith(prefix + "-"))
        Some(base.substring(prefix.length + 1))
      else
        None
    classifier -> ext
  }

  private def _infer_version(name: String, kind: String): Option[String] = {
    val base = name.stripSuffix("." + kind)
    "([0-9]+(?:\\.[0-9A-Za-z-]+)+)(?:[-_].*)?$".r.findFirstMatchIn(base).map(_.group(1))
  }

  private def _infer_repository_version(warehousedir: Path, path: Path, kind: String): Option[String] = {
    val rel = warehousedir.relativize(path).iterator().asScala.toVector.map(_.toString)
    rel.reverse.drop(1).find(_looks_like_version).orElse(_infer_version(path.getFileName.toString, kind))
  }

  private def _looks_like_version(value: String): Boolean =
    value.matches("[0-9]+(?:\\.[0-9A-Za-z-]+)+(?:-[0-9A-Za-z.-]+)?")

  private def _latest_release(versions: Vector[String]): Option[String] =
    versions.reverse.find(!_.toUpperCase(java.util.Locale.ROOT).contains("SNAPSHOT")).orElse(versions.lastOption)

  private def _sort_versions(xs: Vector[String]): Vector[String] =
    xs.sortBy(x => x.split("[.-]").toVector.map(part => f"${Try(part.toInt).getOrElse(0)}%08d:$part").mkString("|"))

  private def _sidecar(path: Path, ext: String): Option[String] = {
    val p = Paths.get(path.toString + "." + ext)
    if (Files.isRegularFile(p))
      Some(Files.readString(p, StandardCharsets.UTF_8).trim.split("\\s+").headOption.getOrElse(""))
    else
      None
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

  private def _validate_name(value: String): String = {
    val name = value.trim
    _slug_pattern.findFirstIn(name) match {
      case Some(x) if x == name => name
      case _ => RAISE.invalidArgumentFault(s"Invalid publication name: ${value}. Expected ${_slug_pattern.regex}")
    }
  }

  private def _yaml_string(value: String): String =
    "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\""

  private def _write_text(path: Path, text: String): Unit = {
    Option(path.getParent).foreach(Files.createDirectories(_))
    Files.writeString(path, text, StandardCharsets.UTF_8)
  }
}
