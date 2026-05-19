package cozy

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path, Paths, StandardCopyOption}
import java.security.MessageDigest
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter

import org.goldenport.RAISE
import scala.collection.JavaConverters._

/*
 * @since   May. 20, 2026
 * @version May. 20, 2026
 * @author  ASAMI, Tomoharu
 */
object CozyCarPublisher {
  def publish(args: List[String]): Unit = {
    val projectdir = _project_dir(args)
    val warehouse = _required_path(args, "warehouse")
    val name = _required_value(args, "name")
    val version = _required_value(args, "version")
    val config = _project_config(projectdir)
    val sourcecar = _car(args).getOrElse(_build_temp_car(projectdir, name, version, args))
    val target = _publish_car(warehouse, name, version, sourcecar)
    val catalog = _updated_catalog(projectdir, warehouse, name, version, target, args, config)
    val sourcecatalog = _source_catalog_path(projectdir, name)
    val publiccatalog = _public_catalog_path(warehouse, name)
    _write_text(sourcecatalog, catalog.toYaml)
    _write_text(publiccatalog, catalog.toYaml)
  }

  private def _build_temp_car(
    projectdir: Path,
    name: String,
    version: String,
    args: List[String]
  ): Path = {
    val mainjar = _required_path(args, "main-jar")
    val tempcar = Files.createTempFile("cozy-publish-car-", ".car")
    val buildargs =
      _remove_publish_only_args(args) ++
        Vector(
          s"--save=$tempcar",
          s"--project-dir=$projectdir",
          s"--name=$name",
          s"--version=$version",
          s"--main-jar=$mainjar"
        )
    CozyArchivePackager.buildCar(buildargs.toList)
    tempcar
  }

  private def _publish_car(warehouse: Path, name: String, version: String, sourcecar: Path): Path = {
    if (!Files.isRegularFile(sourcecar))
      RAISE.invalidArgumentFault(s"CAR archive does not exist: $sourcecar")
    val target = warehouse.resolve("repository").resolve("car").resolve(name).resolve(version).resolve(s"$name-$version.car")
    Files.createDirectories(target.getParent)
    Files.copy(sourcecar, target, StandardCopyOption.REPLACE_EXISTING)
    target
  }

  private def _updated_catalog(
    projectdir: Path,
    warehouse: Path,
    name: String,
    version: String,
    publishedcar: Path,
    args: List[String],
    config: CozyProjectYamlConfig.Config
  ): RepositoryArtifactCatalog = {
    val sourcepath = _source_catalog_path(projectdir, name)
    val existing: RepositoryArtifactCatalog =
      if (Files.isRegularFile(sourcepath))
        RepositoryArtifactCatalog.load(sourcepath)
      else
        RepositoryArtifactCatalog(
          schemaVersion = "1",
          kind = "car",
          artifactId = name,
          recommended = None,
          latestStable = None,
          latestSnapshot = None,
          status = Some("active"),
          aliases = Vector.empty,
          versions = Vector.empty
        )
    if (existing.kind != "car" || existing.artifactId != name)
      RAISE.invalidArgumentFault(s"CAR catalog does not match requested artifact: $sourcepath")

    val channel = _value(args, "channel").getOrElse(if (version.endsWith("-SNAPSHOT")) "snapshot" else "stable")
    val status = _value(args, "status").getOrElse("active")
    val component = _value(args, "component").
      orElse(config.value("packaging.car.manifest_metadata.component")).
      orElse(config.value("project.name")).
      getOrElse(name)
    val file = _warehouse_relative_path(warehouse, publishedcar)
    val runtime = _runtime_requirement(config)
    val entry = RepositoryArtifactCatalogVersion(
      version = version,
      channel = Some(channel),
      status = Some(status),
      component = Some(component),
      publishedAt = Some(_value(args, "published-at").getOrElse(OffsetDateTime.now().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME))),
      file = Some(file),
      runtime = runtime,
      checksumSha256 = Some(_sha256(publishedcar))
    )
    val versions = (existing.versions.filterNot(_.version == version) :+ entry).sortBy(_.version)
    val recommended =
      if (_flag(args, "recommended"))
        Some(version)
      else
        existing.recommended.orElse(Some(version))
    val lateststable =
      if (channel == "stable") Some(version) else existing.latestStable
    val latestsnapshot =
      if (channel == "snapshot") Some(version) else existing.latestSnapshot
    RepositoryArtifactCatalog(
      schemaVersion = existing.schemaVersion,
      kind = "car",
      artifactId = name,
      recommended = recommended,
      latestStable = lateststable,
      latestSnapshot = latestsnapshot,
      status = existing.status.orElse(Some("active")),
      aliases = existing.aliases,
      versions = versions
    ).validate
  }

  private def _runtime_requirement(config: CozyProjectYamlConfig.Config): Option[RepositoryArtifactRuntimeRequirement] = {
    val minimum = config.value("packaging.car.runtime.cncf.minimum")
    val maximum = config.value("packaging.car.runtime.cncf.maximum")
    val excluded = config.list("packaging.car.runtime.cncf.excluded")
    val tested = config.list("packaging.car.runtime.cncf.tested")
    if (minimum.isEmpty && maximum.isEmpty && excluded.isEmpty && tested.isEmpty)
      None
    else
      Some(RepositoryArtifactRuntimeRequirement(minimum, maximum, excluded, tested))
  }

  private def _project_config(projectdir: Path): CozyProjectYamlConfig.Config = {
    val project = CozyProjectYamlConfig.load(projectdir.resolve("project.yaml"))
    val local = CozyProjectYamlConfig.load(projectdir.resolve(".cozy/config.yaml"))
    project.merge(local)
  }

  private def _source_catalog_path(projectdir: Path, name: String): Path =
    projectdir.resolve("src/main/catalog/car").resolve(s"$name.yaml")

  private def _public_catalog_path(warehouse: Path, name: String): Path =
    warehouse.resolve("repository/catalog/car").resolve(s"$name.yaml")

  private def _warehouse_relative_path(warehouse: Path, file: Path): String =
    warehouse.toAbsolutePath.normalize().relativize(file.toAbsolutePath.normalize()).iterator().asScala.map(_.toString).mkString("/")

  private def _sha256(path: Path): String = {
    val digest = MessageDigest.getInstance("SHA-256")
    val in = Files.newInputStream(path)
    try {
      val buffer = new Array[Byte](8192)
      var n = in.read(buffer)
      while (n >= 0) {
        if (n > 0)
          digest.update(buffer, 0, n)
        n = in.read(buffer)
      }
    } finally {
      in.close()
    }
    digest.digest().map(b => "%02x".format(b & 0xff)).mkString
  }

  private def _remove_publish_only_args(args: List[String]): Vector[String] = {
    val skip = Set("warehouse", "car", "name", "version", "channel", "status", "published-at", "recommended")
    def _go_(xs: List[String], acc: Vector[String]): Vector[String] =
      xs match {
        case Nil => acc
        case x :: tail if x.startsWith("--") && x.contains("=") && skip.contains(x.drop(2).takeWhile(_ != '=')) =>
          _go_(tail, acc)
        case x :: _ :: tail if x.startsWith("--") && skip.contains(x.drop(2)) =>
          _go_(tail, acc)
        case "--recommended" :: tail =>
          _go_(tail, acc)
        case x :: tail =>
          _go_(tail, acc :+ x)
      }
    _go_(args, Vector.empty)
  }

  private def _project_dir(args: List[String]): Path =
    _value(args, "project-dir").
      orElse(_positional_args(args).headOption).
      map(p => Paths.get(p).toAbsolutePath.normalize()).
      getOrElse(RAISE.invalidArgumentFault("Missing project directory for publish-car"))

  private def _car(args: List[String]): Option[Path] =
    _path(args, "car")

  private def _required_path(args: List[String], key: String): Path =
    _path(args, key).getOrElse(RAISE.invalidArgumentFault(s"Missing --$key"))

  private def _path(args: List[String], key: String): Option[Path] =
    _value(args, key).map(p => Paths.get(p).toAbsolutePath.normalize())

  private def _required_value(args: List[String], key: String): String =
    _value(args, key).getOrElse(RAISE.invalidArgumentFault(s"Missing --$key"))

  private def _value(args: List[String], key: String): Option[String] = {
    val prefix = s"--$key="
    args.collectFirst {
      case s if s.startsWith(prefix) => s.substring(prefix.length)
    }.orElse {
      args.sliding(2).collectFirst {
        case List(flag, value) if flag == s"--$key" => value
      }
    }
  }

  private def _flag(args: List[String], key: String): Boolean =
    args.exists(_ == s"--$key") ||
      _value(args, key).exists(x => x.equalsIgnoreCase("true") || x == "1" || x.equalsIgnoreCase("yes"))

  private def _positional_args(args: List[String]): Vector[String] = {
    val result = Vector.newBuilder[String]
    var skip = false
    args.foreach { arg =>
      if (skip) {
        skip = false
      } else if (arg.startsWith("--") && !arg.contains("=")) {
        skip = true
      } else if (!arg.startsWith("--")) {
        result += arg
      }
    }
    result.result()
  }

  private def _write_text(path: Path, text: String): Unit = {
    Option(path.getParent).foreach(Files.createDirectories(_))
    Files.writeString(path, text, StandardCharsets.UTF_8)
  }
}
