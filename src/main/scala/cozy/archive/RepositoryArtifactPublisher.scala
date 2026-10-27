package cozy.archive

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, LinkOption, Path, Paths, StandardCopyOption}
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
 *  version Aug. 28, 2026
 * @version Oct. 28, 2026
 * @author  ASAMI, Tomoharu
 */
private[cozy] object RepositoryArtifactPublisher {
  private final case class PreparedCarSidecars(
    source: Path,
    metadatajson: String,
    metadatayaml: Option[String],
    componentknowledge: Option[CozyComponentKnowledgeCarrier]
  )

  final case class Policy(
    kind: String,
    archiveOption: String,
    missingProjectMessage: String,
    missingArchiveMessage: String,
    versionEntry: (String, String, String, List[String]) => RepositoryArtifactCatalogVersion,
    buildArchive: List[String] => Path,
    coordinate: Option[CozyComponentReleaseCoordinateCodec.Coordinate] = None
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
    policy.coordinate.foreach { coordinate =>
      CozyComponentReleaseCoordinateCodec.requireProjection(coordinate.mavenArtifactId, name, "name", "publish-car")
      CozyComponentReleaseCoordinateCodec.requireProjection(coordinate.version, version, "version", "publish-car")
    }
    val suppliedarchive = path(publicationargs, policy.archiveOption)
    val indexpath = componentRepositoryIndexPath(warehouse)
    val generatedat = OffsetDateTime.parse(publishedAt(publicationargs)).toInstant
    val existingindex =
      if (Files.isRegularFile(indexpath))
        Some(
          LegacyComponentRepositoryMigration.migrateIfNeeded(warehouse, indexpath, generatedat).
            getOrElse(ComponentRepositoryIndex.load(indexpath))
        )
      else
        None
    _preflight_catalog(projectdir, name, policy)
    var generatedarchive: Option[Path] = None
    var publicationfailure: Throwable = null
    try {
      val carsidecars =
        if (policy.kind == "car") Some(_prepare_car_sidecars(projectdir, publicationargs, policy.coordinate))
        else None
      val sourcearchive = suppliedarchive.getOrElse {
        val generated = policy.buildArchive(publicationargs)
        generatedarchive = Some(generated)
        generated
      }
      val target = _archive_target(warehouse, name, version, policy)
      if (!Files.isRegularFile(sourcearchive))
        RAISE.invalidArgumentFault(s"${policy.missingArchiveMessage}: $sourcearchive")
      val localpublication = flag(publicationargs, "local-publication")
      val catalog = _updated_catalog(projectdir, warehouse, name, version, target, publicationargs, policy, localpublication)
      val sourcecatalog = sourceCatalogPath(projectdir, policy.kind, name, policy.coordinate)
      val publiccatalog = publicCatalogPath(warehouse, policy.kind, name, policy.coordinate)
      val metadatapath = mavenMetadataPath(warehouse, policy.kind, name, policy.coordinate)
      val updatedindex = ComponentRepositoryIndex.update(existingindex, catalog, generatedat)
      val metadata =
        if (catalog.versions.isEmpty) None
        else Some(RepositoryArtifactMavenMetadata.toXml(catalog, publishedAt(publicationargs)))

      Files.createDirectories(target.getParent)
      Files.copy(sourcearchive, target, StandardCopyOption.REPLACE_EXISTING)
      if (catalog.versions.isEmpty) {
        if (!localpublication)
          Files.deleteIfExists(sourcecatalog)
        Files.deleteIfExists(publiccatalog)
        Files.deleteIfExists(metadatapath)
      } else {
        if (!localpublication)
          writeText(sourcecatalog, catalog.toYaml)
        writeText(publiccatalog, catalog.toYaml)
        writeText(metadatapath, metadata.get)
      }
      carsidecars.foreach { sidecars =>
        val catalogdir = policy.coordinate.map(c => warehouse.resolve("repository/catalog/car").resolve(c.groupPath)).getOrElse(warehouse.resolve("repository/catalog/car"))
        val artifact = policy.coordinate.map(_.mavenArtifactId).getOrElse(name)
        Files.createDirectories(catalogdir)
        Files.copy(sidecars.source, catalogdir.resolve(s"$artifact.cml"), StandardCopyOption.REPLACE_EXISTING)
        writeText(catalogdir.resolve(s"$artifact.model-metadata.json"), sidecars.metadatajson)
        val yamlpath = catalogdir.resolve(s"$artifact.model-metadata.yaml")
        sidecars.metadatayaml match {
          case Some(yaml) => writeText(yamlpath, yaml)
          case None => Files.deleteIfExists(yamlpath)
        }
        val knowledgepath = catalogdir.resolve(artifact).resolve(version).resolve("component-knowledge.json")
        sidecars.componentknowledge match {
          case Some(carrier) =>
            Files.createDirectories(knowledgepath.getParent)
            Files.copy(carrier.source, knowledgepath, StandardCopyOption.REPLACE_EXISTING)
          case None => Files.deleteIfExists(knowledgepath)
        }
      }
      ComponentRepositoryIndex.write(indexpath, updatedindex)
    } catch {
      case error: Throwable =>
        publicationfailure = error
        throw error
    } finally {
      val cleanuperrors = _cleanup_paths(generatedarchive.toVector)
      if (publicationfailure != null)
        _add_suppressed(publicationfailure, cleanuperrors)
      else
        _throw_cleanup_errors(cleanuperrors)
    }
  }

  def projectConfig(projectDir: Path): CozyProjectYamlConfig.Config = {
    CozyProjectYamlConfig.loadProjectConfig(projectDir)
  }

  def projectDir(args: List[String], missingMessage: String): Path =
    _parse(args).pathProperty("project-dir").
      orElse(_parse(args).argument("project").map(p => Paths.get(p).toAbsolutePath.normalize())).
      getOrElse(RAISE.invalidArgumentFault(missingMessage))

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

  def removePublishOnlyArgs(args: List[String], skipKeys: Set[String]): Vector[String] = {
    def _go_(xs: List[String], acc: Vector[String]): Vector[String] =
      xs match {
        case Nil => acc
        case "--keep-recommended" :: tail =>
          _go_(tail, acc)
        case "--local-publication" :: tail if skipKeys.contains("local-publication") =>
          _go_(tail, acc)
        case x :: tail if x.startsWith("--") && x.contains("=") && skipKeys.contains(x.drop(2).takeWhile(_ != '=')) =>
          _go_(tail, acc)
        case x :: _ :: tail if x.startsWith("--") && skipKeys.contains(x.drop(2)) =>
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
    spec.Parameter("keep-recommended", spec.Parameter.SwitchKind),
    spec.Parameter("local-publication", spec.Parameter.SwitchKind)
  )

  private def _parse(args: List[String]): CozyCliArgs.Parsed =
    CozyCliArgs.parseStrict(_request_parameters: _*)(args)

  def sourceCatalogPath(projectDir: Path, kind: String, name: String, coordinate: Option[CozyComponentReleaseCoordinateCodec.Coordinate] = None): Path =
    coordinate.map(c => projectDir.resolve("src/main/catalog").resolve(c.carCatalogRelativePath)).
      getOrElse(projectDir.resolve(s"src/main/catalog/$kind").resolve(s"$name.yaml"))

  def publicCatalogPath(warehouse: Path, kind: String, name: String, coordinate: Option[CozyComponentReleaseCoordinateCodec.Coordinate] = None): Path =
    coordinate.map(c => warehouse.resolve("repository/catalog").resolve(c.carCatalogRelativePath)).
      getOrElse(warehouse.resolve(s"repository/catalog/$kind").resolve(s"$name.yaml"))

  def mavenMetadataPath(warehouse: Path, kind: String, name: String, coordinate: Option[CozyComponentReleaseCoordinateCodec.Coordinate] = None): Path =
    coordinate.map(c => warehouse.resolve("repository/car").resolve(c.groupPath).resolve(c.mavenArtifactId).resolve("maven-metadata.xml")).
      getOrElse(warehouse.resolve("repository").resolve(kind).resolve(name).resolve("maven-metadata.xml"))

  def componentRepositoryIndexPath(warehouse: Path): Path =
    warehouse.resolve(ComponentRepositoryIndex.PUBLIC_PATH)

  def warehouseRelativePath(warehouse: Path, file: Path): String =
    warehouse.toAbsolutePath.normalize().relativize(file.toAbsolutePath.normalize()).iterator().asScala.map(_.toString).mkString("/")

  def publishedAt(args: List[String]): String =
    value(args, "published-at").getOrElse(OffsetDateTime.now().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME))

  def writeText(path: Path, text: String): Unit = {
    if (Files.exists(path, LinkOption.NOFOLLOW_LINKS) && !Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS))
      RAISE.invalidArgumentFault(s"Repository publication output must be a regular file, not a symbolic link or nonregular leaf: $path")
    Option(path.getParent).foreach(Files.createDirectories(_))
    Files.writeString(path, text, StandardCharsets.UTF_8)
  }

  private def _archive_target(warehouse: Path, name: String, version: String, policy: Policy): Path =
    policy.coordinate.map(c => warehouse.resolve("repository/car").resolve(c.carRepositoryRelativePath)).
      getOrElse(warehouse.resolve("repository").resolve(policy.kind).resolve(name).resolve(version).resolve(s"$name-$version.${policy.kind}"))

  private def _cleanup_paths(paths: Vector[Path]): Vector[Throwable] =
    paths.flatMap { path =>
      try {
        Files.deleteIfExists(path)
        None
      } catch {
        case error: Throwable => Some(error)
      }
    }

  private def _add_suppressed(primary: Throwable, errors: Vector[Throwable]): Unit =
    errors.foreach(primary.addSuppressed)

  private def _throw_cleanup_errors(errors: Vector[Throwable]): Unit =
    errors.headOption.foreach { primary =>
      _add_suppressed(primary, errors.tail)
      throw primary
    }

  private def _updated_catalog(
    projectdir: Path,
    warehouse: Path,
    name: String,
    version: String,
    publishedarchive: Path,
    args: List[String],
    policy: Policy,
    localpublication: Boolean
  ): RepositoryArtifactCatalog = {
    val sourcepath = sourceCatalogPath(projectdir, policy.kind, name, policy.coordinate)
    val publicpath = publicCatalogPath(warehouse, policy.kind, name, policy.coordinate)
    val existing: RepositoryArtifactCatalog =
      if (localpublication && Files.isRegularFile(publicpath))
        RepositoryArtifactCatalog.load(publicpath)
      else if (!localpublication && Files.isRegularFile(sourcepath))
        RepositoryArtifactCatalog.load(sourcepath)
      else if (!localpublication && Files.isRegularFile(publicpath))
        RepositoryArtifactCatalog.load(publicpath)
      else
        RepositoryArtifactCatalog(
          schemaVersion = if (policy.coordinate.isDefined) "2" else "1",
          kind = policy.kind,
          artifactId = name,
          recommended = None,
          latestStable = None,
          latestSnapshot = None,
          status = Some("active"),
          aliases = Vector.empty,
          versions = Vector.empty,
          namespace = policy.coordinate.map(_.namespace),
          id = policy.coordinate.map(_.id)
        )
    if (existing.kind != policy.kind || existing.artifactId != name ||
      policy.coordinate.exists(c => existing.namespace != Some(c.namespace) || existing.id != Some(c.id)))
      RAISE.invalidArgumentFault(s"${policy.kind.toUpperCase} catalog does not match requested artifact: $sourcepath")

    val requestedchannel = value(args, "channel").getOrElse(if (_is_snapshot_version(version)) "snapshot" else "stable")
    val snapshotpublish = _is_snapshot_version(version) || requestedchannel == "snapshot"
    val channel =
      if (policy.coordinate.isDefined && snapshotpublish) "snapshot"
      else requestedchannel
    val entry0 = policy.versionEntry(version, channel, warehouseRelativePath(warehouse, publishedarchive), args)
    val entry = policy.coordinate.map { coordinate =>
      entry0.copy(
        component = Some(coordinate.qualifiedId),
        file = Some(s"repository/car/${coordinate.carRepositoryRelativePath}")
      )
    }.getOrElse(entry0)
    val releaseversions = existing.versions.filterNot(_is_snapshot_catalog_version)
    val versions =
      if (policy.coordinate.isDefined && snapshotpublish)
        (releaseversions.filterNot(_.version == version) :+ entry).sortBy(_.version)
      else if (snapshotpublish)
        releaseversions
      else if (policy.coordinate.isDefined)
        (releaseversions.filterNot(_.version == version) :+ entry).sortBy(_.version)
      else
        (releaseversions.filterNot(_.version == version) :+ entry).sortBy(_.version)
    def _valid_stable_selector_(selector: Option[String]): Option[String] =
      selector.filter(value => versions.exists(version => version.version == value && version.channel.contains("stable")))
    def _valid_snapshot_selector_(selector: Option[String]): Option[String] =
      selector.filter(value => versions.exists(version => version.version == value && version.channel.contains("snapshot")))
    val stablepublish = !snapshotpublish && channel == "stable"
    val recommended =
      if (snapshotpublish)
        _valid_stable_selector_(existing.recommended)
      else if (stablepublish && !flag(args, "keep-recommended"))
        Some(version)
      else
        _valid_stable_selector_(existing.recommended).orElse(if (stablepublish) Some(version) else None)
    val lateststable =
      if (snapshotpublish) _valid_stable_selector_(existing.latestStable)
      else if (stablepublish) Some(version)
      else _valid_stable_selector_(existing.latestStable)
    val latestsnapshot =
      if (policy.coordinate.isDefined && snapshotpublish) Some(version)
      else if (policy.coordinate.isDefined) _valid_snapshot_selector_(existing.latestSnapshot)
      else None
    RepositoryArtifactCatalog(
      schemaVersion = existing.schemaVersion,
      kind = policy.kind,
      artifactId = name,
      recommended = recommended,
      latestStable = lateststable,
      latestSnapshot = latestsnapshot,
      status = existing.status.orElse(Some("active")),
      aliases = existing.aliases,
      versions = versions,
      tags = existing.tags,
      terms = existing.terms,
      namespace = policy.coordinate.map(_.namespace),
      id = policy.coordinate.map(_.id)
    ).validate
  }

  private def _preflight_catalog(
    projectdir: Path,
    name: String,
    policy: Policy
  ): Unit = {
    val sourcepath = sourceCatalogPath(projectdir, policy.kind, name, policy.coordinate)
    if (Files.isRegularFile(sourcepath)) {
      val existing = policy.coordinate.map(RepositoryArtifactCatalog._load_for_coordinate(sourcepath, _)).
        getOrElse(RepositoryArtifactCatalog.load(sourcepath))
      if (existing.kind != policy.kind || existing.artifactId != name ||
        policy.coordinate.exists(c => existing.namespace != Some(c.namespace) || existing.id != Some(c.id)))
        RAISE.invalidArgumentFault(s"component.release-coordinate.mismatch source=$sourcepath expected=${policy.coordinate.map(_.qualifiedId).getOrElse(name)} actual=${existing.namespace.map(_ + ".").getOrElse("")}${existing.id.getOrElse(existing.artifactId)}")
    }
  }

  private def _is_snapshot_catalog_version(version: RepositoryArtifactCatalogVersion): Boolean =
    version.channel.contains("snapshot") || _is_snapshot_version(version.version)

  private def _is_snapshot_version(version: String): Boolean =
    version.toUpperCase(java.util.Locale.ROOT).contains("SNAPSHOT")

  private def _prepare_car_sidecars(
    projectdir: Path,
    args: List[String],
    coordinate: Option[CozyComponentReleaseCoordinateCodec.Coordinate]
  ): PreparedCarSidecars = {
    val resolved = CarCmlSourceResolver.resolve(projectdir).fold(
      issue => RAISE.invalidArgumentFault(s"${issue.code}: ${issue.message}"),
      identity
    )
    path(args, "model-metadata").filter(Files.isRegularFile(_)).map { metadata =>
      val yaml = metadata.resolveSibling(metadata.getFileName.toString.stripSuffix(".json") + ".yaml")
      PreparedCarSidecars(
        resolved.source,
        Files.readString(metadata, StandardCharsets.UTF_8),
        Option(yaml).filter(Files.isRegularFile(_)).map(Files.readString(_, StandardCharsets.UTF_8)),
        coordinate.flatMap(CozyComponentKnowledgeCarrier.fromProject(projectdir, _))
      )
    }.getOrElse {
      try {
        val metadata = CmlModelMetadata.fromCml(resolved.source, resolved.projectRelativePath, "cml")
        PreparedCarSidecars(
          resolved.source,
          metadata.toJsonString,
          Some(metadata.toYamlString),
          coordinate.flatMap(CozyComponentKnowledgeCarrier.fromProject(projectdir, _))
        )
      } catch {
        case NonFatal(e) =>
          RAISE.invalidArgumentFault(s"car.cml.metadata.generation_failed: Could not generate CML model metadata from ${resolved.projectRelativePath}: ${Option(e.getMessage).getOrElse(e.getClass.getSimpleName)}")
      }
    }
  }

}
