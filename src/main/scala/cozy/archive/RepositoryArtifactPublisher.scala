package cozy.archive

import java.nio.charset.StandardCharsets
import java.nio.channels.FileChannel
import java.nio.file.{Files, Path, Paths, StandardCopyOption}
import java.nio.file.StandardOpenOption
import java.security.MessageDigest
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter

import cozy.modeler.CmlModelMetadata
import cozy.config.CozyProjectYamlConfig
import cozy.runtime.CozyCliArgs
import org.goldenport.RAISE
import org.goldenport.cli.spec
import scala.collection.JavaConverters._
import scala.util.control.NonFatal

/*
 * @since   May. 20, 2026
 *  version Jun. 23, 2026
 *  version Jul. 21, 2026
 * @version Aug.  1, 2026
 * @author  ASAMI, Tomoharu
 */
private[cozy] object RepositoryArtifactPublisher {
  private final case class PreparedCarCmlSidecars(
    source: Path,
    metadatajson: String,
    metadatayaml: Option[String]
  )

  final case class Policy(
    kind: String,
    archiveOption: String,
    missingProjectMessage: String,
    missingArchiveMessage: String,
    versionEntry: (String, String, String, Path, List[String]) => RepositoryArtifactCatalogVersion,
    buildArchive: List[String] => Path
  )

  def publish(args: List[String], policy: Policy): Unit = {
    val publicationargs =
      if (value(args, "published-at").isDefined)
        args
      else
        args ++ List("--published-at", OffsetDateTime.now().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME))
    val projectdir = projectDir(publicationargs, policy.missingProjectMessage)
    val warehouse = requiredPath(publicationargs, "warehouse")
    val name = requiredValue(publicationargs, "name")
    val version = requiredValue(publicationargs, "version")
    val sourcearchive = path(publicationargs, policy.archiveOption).getOrElse(policy.buildArchive(publicationargs))
    val carsidecars =
      if (policy.kind == "car") Some(_prepare_car_cml_sidecars(projectdir, name, publicationargs))
      else None
    val indexpath = componentRepositoryIndexPath(warehouse)
    val generatedat = OffsetDateTime.parse(publishedAt(publicationargs)).toInstant
    _with_component_repository_index_lock(warehouse) {
      val existingindex =
        if (Files.isRegularFile(indexpath))
          Some(ComponentRepositoryIndex.validateCatalogs(ComponentRepositoryIndex.load(indexpath), indexpath))
        else
          None
      val target = _publish_archive(warehouse, name, version, sourcearchive, policy)
      val catalog = _updated_catalog(projectdir, warehouse, name, version, target, publicationargs, policy)
      val sourcecatalog = sourceCatalogPath(projectdir, policy.kind, name)
      val publiccatalog = publicCatalogPath(warehouse, policy.kind, name)
      val metadatapath = mavenMetadataPath(warehouse, policy.kind, name)
      val updatedindex = ComponentRepositoryIndex.update(existingindex, catalog, generatedat)
      if (catalog.versions.isEmpty) {
        _delete_if_exists(sourcecatalog)
        _delete_if_exists(publiccatalog)
        _delete_if_exists(metadatapath)
      } else {
        val metadata = RepositoryArtifactMavenMetadata.toXml(catalog, publishedAt(publicationargs))
        writeText(sourcecatalog, catalog.toYaml)
        writeText(publiccatalog, catalog.toYaml)
        writeText(metadatapath, metadata)
      }
      ComponentRepositoryIndex.validateCatalogs(updatedindex, indexpath)
      ComponentRepositoryIndex.writeAtomic(indexpath, updatedindex)
    }
    carsidecars.foreach(_publish_car_cml_sidecars(warehouse, name, _))
  }

  def projectConfig(projectdir: Path): CozyProjectYamlConfig.Config = {
    CozyProjectYamlConfig.loadProjectConfig(projectdir)
  }

  def projectDir(args: List[String], missingmessage: String): Path =
    _parse(args).pathProperty("project-dir").
      orElse(_parse(args).argument("project").map(p => Paths.get(p).toAbsolutePath.normalize())).
      getOrElse(RAISE.invalidArgumentFault(missingmessage))

  def requiredPath(args: List[String], key: String): Path =
    path(args, key).getOrElse(RAISE.invalidArgumentFault(s"Missing --$key"))

  def path(args: List[String], key: String): Option[Path] =
    value(args, key).map(p => Paths.get(p).toAbsolutePath.normalize())

  def requiredValue(args: List[String], key: String): String =
    value(args, key).getOrElse(RAISE.invalidArgumentFault(s"Missing --$key"))

  def value(args: List[String], key: String): Option[String] = {
    _parse(args).property(key)
  }

  def flag(args: List[String], key: String): Boolean =
    args.exists(_ == s"--$key") ||
      args.sliding(2).collectFirst {
        case List(flag, value) if flag == s"--${key}" => value
      }.exists(x => x.equalsIgnoreCase("true") || x == "1" || x.equalsIgnoreCase("yes"))

  def removePublishOnlyArgs(args: List[String], skipkeys: Set[String]): Vector[String] = {
    def _go_(xs: List[String], acc: Vector[String]): Vector[String] =
      xs match {
        case Nil => acc
        case x :: tail if x.startsWith("--") && x.contains("=") && skipkeys.contains(x.drop(2).takeWhile(_ != '=')) =>
          _go_(tail, acc)
        case x :: _ :: tail if x.startsWith("--") && skipkeys.contains(x.drop(2)) =>
          _go_(tail, acc)
        case "--recommended" :: tail =>
          _go_(tail, acc)
        case x :: tail =>
          _go_(tail, acc :+ x)
      }
    _go_(args, Vector.empty)
  }

  private val _request_parameters = Vector(
    spec.Parameter.argumentFile("project"),
    spec.Parameter.propertyFileOption("project-dir"),
    spec.Parameter.propertyFileOption("warehouse"),
    spec.Parameter.propertyFileOption("car"),
    spec.Parameter.propertyFileOption("sar"),
    spec.Parameter.propertyFileOption("main-jar"),
    spec.Parameter.propertyFileOption("source-dir"),
    spec.Parameter.property("source-files"),
    spec.Parameter.propertyFileOption("extension-jars"),
    spec.Parameter.propertyFileOption("application-conf"),
    spec.Parameter.propertyFileOption("lib-jars"),
    spec.Parameter.propertyFileOption("spi-jars"),
    spec.Parameter.propertyFileOption("car-dir"),
    spec.Parameter.propertyFileOption("default-conf"),
    spec.Parameter.propertyFileOption("dependency-manifest"),
    spec.Parameter.propertyFileOption("web-dir"),
    spec.Parameter.propertyFileOption("web-descriptor"),
    spec.Parameter.propertyFileOption("form-descriptor"),
    spec.Parameter.propertyFileOption("admin-descriptor"),
    spec.Parameter.propertyFileOption("assembly-descriptor"),
    spec.Parameter.propertyFileOption("model-metadata"),
    spec.Parameter.property("extensions"),
    spec.Parameter.property("config"),
    spec.Parameter.property("entities"),
    spec.Parameter.property("name"),
    spec.Parameter.property("version"),
    spec.Parameter.property("component"),
    spec.Parameter.property("status"),
    spec.Parameter.property("channel"),
    spec.Parameter.property("published-at"),
    spec.Parameter("recommended", spec.Parameter.SwitchKind)
  )

  private def _parse(args: List[String]): CozyCliArgs.Parsed =
    CozyCliArgs.parseStrict(_request_parameters: _*)(args)

  def sourceCatalogPath(projectdir: Path, kind: String, name: String): Path =
    projectdir.resolve(s"src/main/catalog/$kind").resolve(s"$name.yaml")

  def publicCatalogPath(warehouse: Path, kind: String, name: String): Path =
    warehouse.resolve(s"repository/catalog/$kind").resolve(s"$name.yaml")

  def mavenMetadataPath(warehouse: Path, kind: String, name: String): Path =
    warehouse.resolve("repository").resolve(kind).resolve(name).resolve("maven-metadata.xml")

  def componentRepositoryIndexPath(warehouse: Path): Path =
    warehouse.resolve(ComponentRepositoryIndex.PublicPath)

  def warehouseRelativePath(warehouse: Path, file: Path): String =
    warehouse.toAbsolutePath.normalize().relativize(file.toAbsolutePath.normalize()).iterator().asScala.map(_.toString).mkString("/")

  def sha256(path: Path): String = {
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

  def publishedAt(args: List[String]): String =
    value(args, "published-at").getOrElse(OffsetDateTime.now().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME))

  def writeText(path: Path, text: String): Unit = {
    Option(path.getParent).foreach(Files.createDirectories(_))
    Files.writeString(path, text, StandardCharsets.UTF_8)
  }

  private def _publish_archive(warehouse: Path, name: String, version: String, sourcearchive: Path, policy: Policy): Path = {
    if (!Files.isRegularFile(sourcearchive))
      RAISE.invalidArgumentFault(s"${policy.missingArchiveMessage}: $sourcearchive")
    val target = warehouse.resolve("repository").resolve(policy.kind).resolve(name).resolve(version).resolve(s"$name-$version.${policy.kind}")
    Files.createDirectories(target.getParent)
    Files.copy(sourcearchive, target, StandardCopyOption.REPLACE_EXISTING)
    target
  }

  private def _updated_catalog(
    projectdir: Path,
    warehouse: Path,
    name: String,
    version: String,
    publishedarchive: Path,
    args: List[String],
    policy: Policy
  ): RepositoryArtifactCatalog = {
    val sourcepath = sourceCatalogPath(projectdir, policy.kind, name)
    val existing: RepositoryArtifactCatalog =
      if (Files.isRegularFile(sourcepath))
        RepositoryArtifactCatalog.load(sourcepath)
      else
        RepositoryArtifactCatalog(
          schemaVersion = "1",
          kind = policy.kind,
          artifactId = name,
          recommended = None,
          latestStable = None,
          latestSnapshot = None,
          status = Some("active"),
          aliases = Vector.empty,
          versions = Vector.empty
        )
    if (existing.kind != policy.kind || existing.artifactId != name)
      RAISE.invalidArgumentFault(s"${policy.kind.toUpperCase} catalog does not match requested artifact: $sourcepath")

    val channel = value(args, "channel").getOrElse(if (_is_snapshot_version(version)) "snapshot" else "stable")
    val entry = policy.versionEntry(version, channel, warehouseRelativePath(warehouse, publishedarchive), publishedarchive, args)
    val snapshotpublish = _is_snapshot_version(version) || channel == "snapshot"
    val releaseversions = existing.versions.filterNot(_is_snapshot_catalog_version)
    val versions =
      if (snapshotpublish)
        releaseversions
      else
        (releaseversions.filterNot(_.version == version) :+ entry).sortBy(_.version)
    def _valid_selector_(selector: Option[String]): Option[String] =
      selector.filter(value => versions.exists(_.version == value))
    val recommended =
      if (!snapshotpublish && flag(args, "recommended"))
        Some(version)
      else
        _valid_selector_(existing.recommended).orElse(if (snapshotpublish) None else Some(version))
    val lateststable =
      if (!snapshotpublish && channel == "stable") Some(version) else _valid_selector_(existing.latestStable)
    RepositoryArtifactCatalog(
      schemaVersion = existing.schemaVersion,
      kind = policy.kind,
      artifactId = name,
      recommended = recommended,
      latestStable = lateststable,
      latestSnapshot = None,
      status = existing.status.orElse(Some("active")),
      aliases = existing.aliases,
      versions = versions,
      tags = existing.tags,
      terms = existing.terms
    ).validate
  }

  private def _is_snapshot_catalog_version(version: RepositoryArtifactCatalogVersion): Boolean =
    version.channel.contains("snapshot") || _is_snapshot_version(version.version)

  private def _is_snapshot_version(version: String): Boolean =
    version.toUpperCase(java.util.Locale.ROOT).contains("SNAPSHOT")

  private def _delete_if_exists(path: Path): Unit =
    Files.deleteIfExists(path)

  private val _component_repository_index_monitor = new Object

  private def _with_component_repository_index_lock[A](warehouse: Path)(body: => A): A =
    _component_repository_index_monitor.synchronized {
      val lockpath = warehouse.resolve(".cozy/locks/component-repository-index.lock")
      Files.createDirectories(lockpath.getParent)
      val channel = FileChannel.open(lockpath, StandardOpenOption.CREATE, StandardOpenOption.WRITE)
      try {
        val lock = channel.lock()
        try body
        finally lock.release()
      } finally channel.close()
    }

  private def _prepare_car_cml_sidecars(projectdir: Path, name: String, args: List[String]): PreparedCarCmlSidecars = {
    val resolved = CarCmlSourceResolver.resolve(projectdir, name).fold(
      issue => RAISE.invalidArgumentFault(s"${issue.code}: ${issue.message}"),
      identity
    )
    path(args, "model-metadata").filter(Files.isRegularFile(_)).map { metadata =>
      val yaml = metadata.resolveSibling(metadata.getFileName.toString.stripSuffix(".json") + ".yaml")
      PreparedCarCmlSidecars(resolved.source, Files.readString(metadata, StandardCharsets.UTF_8), Option(yaml).filter(Files.isRegularFile(_)).map(Files.readString(_, StandardCharsets.UTF_8)))
    }.getOrElse {
      try {
        val metadata = CmlModelMetadata.fromCml(resolved.source, resolved.projectrelativepath, "cml")
        PreparedCarCmlSidecars(resolved.source, metadata.toJsonString, Some(metadata.toYamlString))
      } catch {
        case NonFatal(e) =>
          RAISE.invalidArgumentFault(s"car.cml.metadata.generation_failed: Could not generate CML model metadata from ${resolved.projectrelativepath}: ${Option(e.getMessage).getOrElse(e.getClass.getSimpleName)}")
      }
    }
  }

  private def _publish_car_cml_sidecars(warehouse: Path, name: String, sidecars: PreparedCarCmlSidecars): Unit = {
    val catalogdir = warehouse.resolve("repository/catalog/car")
    Files.createDirectories(catalogdir)
    Files.copy(sidecars.source, catalogdir.resolve(s"$name.cml"), StandardCopyOption.REPLACE_EXISTING)
    writeText(catalogdir.resolve(s"$name.model-metadata.json"), sidecars.metadatajson)
    sidecars.metadatayaml.foreach(writeText(catalogdir.resolve(s"$name.model-metadata.yaml"), _))
  }
}
