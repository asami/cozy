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
 * @version Aug. 19, 2026
 * @author  ASAMI, Tomoharu
 */
private[cozy] object RepositoryArtifactPublisher {
  /** Package-private deterministic failure seam for transaction recovery specifications. */
  private[archive] var _publication_move_fault: Option[Path => Unit] = None
  /** Package-private deterministic failure seam for candidate-preparation specifications. */
  private[archive] var _publication_prepare_fault: Option[Path => Unit] = None
  /** Package-private deterministic failure seam for rollback-snapshot specifications. */
  private[archive] var _publication_snapshot_fault: Option[Path => Unit] = None
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
    _with_component_repository_index_lock(warehouse) {
      val existingindex =
        if (Files.isRegularFile(indexpath))
          Some(ComponentRepositoryIndex.validateCatalogs(ComponentRepositoryIndex.load(indexpath), indexpath))
        else
          None
      _preflight_catalog(projectdir, name, policy)
      val stagedfiles = scala.collection.mutable.ArrayBuffer.empty[StagedFile]
      var generatedarchive: Option[Path] = None
      var publicationfailure: Throwable = null
      def _stage_(create: => StagedFile): Path = {
        val staged = create
        stagedfiles += staged
        staged.path
      }
      try {
      val carsidecars =
        if (policy.kind == "car") Some(_prepare_car_cml_sidecars(projectdir, publicationargs))
        else None
      val sourcearchive = suppliedarchive.getOrElse {
        val generated = policy.buildArchive(publicationargs)
        generatedarchive = Some(generated)
        generated
      }
      val target = _archive_target(warehouse, name, version, policy)
      if (!Files.isRegularFile(sourcearchive))
        RAISE.invalidArgumentFault(s"${policy.missingArchiveMessage}: $sourcearchive")
      val archivecandidate = _stage_(_stage_copy(target, sourcearchive))
      val catalog = _updated_catalog(projectdir, warehouse, name, version, target, archivecandidate, publicationargs, policy)
      val sourcecatalog = sourceCatalogPath(projectdir, policy.kind, name, policy.coordinate)
      val publiccatalog = publicCatalogPath(warehouse, policy.kind, name, policy.coordinate)
      val metadatapath = mavenMetadataPath(warehouse, policy.kind, name, policy.coordinate)
      val updatedindex = ComponentRepositoryIndex.update(existingindex, catalog, generatedat)
      val writes = scala.collection.mutable.LinkedHashMap.empty[Path, Option[Path]]
      writes += target -> Some(archivecandidate)
      policy.coordinate.foreach { _ =>
        writes += target.resolveSibling(target.getFileName.toString + ".sha256") ->
          Some(_stage_(_stage_text(target.resolveSibling(target.getFileName.toString + ".sha256"), sha256(archivecandidate) + "\n")))
      }
      if (catalog.versions.isEmpty) {
        writes += sourcecatalog -> None
        writes += publiccatalog -> None
        writes += metadatapath -> None
      } else {
        val metadata = RepositoryArtifactMavenMetadata.toXml(catalog, publishedAt(publicationargs))
        writes += sourcecatalog -> Some(_stage_(_stage_text(sourcecatalog, catalog.toYaml)))
        writes += publiccatalog -> Some(_stage_(_stage_text(publiccatalog, catalog.toYaml)))
        writes += metadatapath -> Some(_stage_(_stage_text(metadatapath, metadata)))
      }
      carsidecars.foreach { sidecars =>
        val catalogdir = policy.coordinate.map(c => warehouse.resolve("repository/catalog/car").resolve(c.groupPath)).getOrElse(warehouse.resolve("repository/catalog/car"))
        val artifact = policy.coordinate.map(_.mavenArtifactId).getOrElse(name)
        writes += catalogdir.resolve(s"$artifact.cml") -> Some(_stage_(_stage_copy(catalogdir.resolve(s"$artifact.cml"), sidecars.source)))
        writes += catalogdir.resolve(s"$artifact.model-metadata.json") -> Some(_stage_(_stage_text(catalogdir.resolve(s"$artifact.model-metadata.json"), sidecars.metadatajson)))
        sidecars.metadatayaml.foreach { yaml =>
          writes += catalogdir.resolve(s"$artifact.model-metadata.yaml") -> Some(_stage_(_stage_text(catalogdir.resolve(s"$artifact.model-metadata.yaml"), yaml)))
        }
        if (sidecars.metadatayaml.isEmpty)
          writes += catalogdir.resolve(s"$artifact.model-metadata.yaml") -> None
      }
      val indexcandidate = _stage_(_stage_text(indexpath, ComponentRepositoryIndex.render(updatedindex)))
      val overlay = writes.toMap.map { case (path, staged) => path.toAbsolutePath.normalize() -> staged }
      // All candidate bytes, including archive integrity, are checked before a final path changes.
      ComponentRepositoryIndex.validateCatalogs(updatedindex, indexpath, overlay)
      _commit_transaction(writes.toVector, indexpath -> Some(indexcandidate))
      } catch {
        case error: Throwable =>
          publicationfailure = error
          throw error
      } finally {
        val cleanuperrors =
          _cleanup_staged_files(stagedfiles.toVector) ++
            _cleanup_paths(generatedarchive.toVector)
        if (publicationfailure != null)
          _add_suppressed(publicationfailure, cleanuperrors)
        else
          _throw_cleanup_errors(cleanuperrors)
      }
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
        case x :: tail if x.startsWith("--") && x.contains("=") && skipKeys.contains(x.drop(2).takeWhile(_ != '=')) =>
          _go_(tail, acc)
        case x :: _ :: tail if x.startsWith("--") && skipKeys.contains(x.drop(2)) =>
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
    val temporary = Files.createTempFile(path.getParent, s".${path.getFileName.toString}-", ".tmp")
    try {
      Files.writeString(temporary, text, StandardCharsets.UTF_8)
      try Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
      catch { case _: java.nio.file.AtomicMoveNotSupportedException => Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING) }
    } finally Files.deleteIfExists(temporary)
  }

  private def _archive_target(warehouse: Path, name: String, version: String, policy: Policy): Path =
    policy.coordinate.map(c => warehouse.resolve("repository/car").resolve(c.carRepositoryRelativePath)).
      getOrElse(warehouse.resolve("repository").resolve(policy.kind).resolve(name).resolve(version).resolve(s"$name-$version.${policy.kind}"))

  private final case class StagedFile(path: Path, createdparents: Vector[Path])
  private final case class DestinationSnapshot(destination: Path, backup: Option[Path])

  private def _stage_copy(destination: Path, source: Path): StagedFile = {
    val createdparents = _create_parent_directories(destination)
    var staged: Option[Path] = None
    try {
      val prepared = Files.createTempFile(destination.getParent, s".${destination.getFileName}-prepare-", ".tmp")
      staged = Some(prepared)
      _publication_prepare_fault.foreach(_(prepared))
      Files.copy(source, prepared, StandardCopyOption.REPLACE_EXISTING)
      StagedFile(prepared, createdparents)
    } catch {
      case error: Throwable =>
        val cleanuperrors = _cleanup_paths(staged.toVector) ++ _cleanup_empty_directories(createdparents)
        _add_suppressed(error, cleanuperrors)
        throw error
    }
  }

  private def _stage_text(destination: Path, text: String): StagedFile = {
    val createdparents = _create_parent_directories(destination)
    var staged: Option[Path] = None
    try {
      val prepared = Files.createTempFile(destination.getParent, s".${destination.getFileName}-prepare-", ".tmp")
      staged = Some(prepared)
      _publication_prepare_fault.foreach(_(prepared))
      Files.writeString(prepared, text, StandardCharsets.UTF_8)
      StagedFile(prepared, createdparents)
    } catch {
      case error: Throwable =>
        val cleanuperrors = _cleanup_paths(staged.toVector) ++ _cleanup_empty_directories(createdparents)
        _add_suppressed(error, cleanuperrors)
        throw error
    }
  }

  /** Installs all prepared files with index.json as the final visibility point. */
  private def _commit_transaction(
    prepared: Vector[(Path, Option[Path])],
    index: (Path, Option[Path])
  ): Unit = {
    val ordered = prepared.map { case (path, candidate) => path.toAbsolutePath.normalize() -> candidate } :+
      (index._1.toAbsolutePath.normalize() -> index._2)
    var snapshots = Vector.empty[DestinationSnapshot]
    var forwardstarted = false
    var transactionfailure: Throwable = null
    try {
      ordered.foreach { case (destination, _) =>
        snapshots :+= _snapshot(destination)
      }
      forwardstarted = true
      ordered.foreach { case (destination, candidate) =>
        _forward_move_or_delete(destination, candidate)
      }
    } catch {
      case error: Throwable =>
        transactionfailure = error
        if (forwardstarted)
          _add_suppressed(error, _restore_destinations(snapshots.reverse))
        throw error
    } finally {
      val cleanuperrors = _cleanup_paths(ordered.flatMap(_._2)) ++ _cleanup_paths(snapshots.flatMap(_.backup))
      if (transactionfailure != null)
        _add_suppressed(transactionfailure, cleanuperrors)
      else
        _throw_cleanup_errors(cleanuperrors)
    }
  }

  private def _snapshot(destination: Path): DestinationSnapshot =
    if (Files.exists(destination)) {
      val backup = Files.createTempFile(destination.getParent, s".${destination.getFileName}-rollback-", ".bak")
      try {
        _publication_snapshot_fault.foreach(_(destination))
        Files.copy(destination, backup, StandardCopyOption.REPLACE_EXISTING)
        DestinationSnapshot(destination, Some(backup))
      } catch {
        case error: Throwable =>
          _add_suppressed(error, _cleanup_paths(Vector(backup)))
          throw error
      }
    } else
      DestinationSnapshot(destination, None)

  private def _forward_move_or_delete(destination: Path, candidate: Option[Path]): Unit =
    candidate match {
      case Some(staged) =>
        Option(destination.getParent).foreach(Files.createDirectories(_))
        _publication_move_fault.foreach(_(destination))
        _replace(destination, staged)
      case None => Files.deleteIfExists(destination)
    }

  private def _restore_destinations(snapshots: Vector[DestinationSnapshot]): Vector[Throwable] =
    snapshots.flatMap { snapshot =>
      try {
        snapshot.backup match {
          case Some(backup) => _replace(snapshot.destination, backup)
          case None => Files.deleteIfExists(snapshot.destination)
        }
        None
      } catch {
        case error: Throwable => Some(error)
      }
    }

  private def _replace(destination: Path, source: Path): Unit =
    try Files.move(source, destination, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
    catch { case _: java.nio.file.AtomicMoveNotSupportedException => Files.move(source, destination, StandardCopyOption.REPLACE_EXISTING) }

  private def _create_parent_directories(destination: Path): Vector[Path] = {
    var current = Option(destination.getParent)
    var createdparents = Vector.empty[Path]
    while (current.exists(path => !Files.exists(path))) {
      createdparents :+= current.get
      current = Option(current.get.getParent)
    }
    Option(destination.getParent).foreach(Files.createDirectories(_))
    createdparents
  }

  private def _cleanup_staged_files(stagedfiles: Vector[StagedFile]): Vector[Throwable] =
    _cleanup_paths(stagedfiles.map(_.path)) ++
      _cleanup_empty_directories(stagedfiles.flatMap(_.createdparents).distinct)

  private def _cleanup_paths(paths: Vector[Path]): Vector[Throwable] =
    paths.flatMap { path =>
      try {
        Files.deleteIfExists(path)
        None
      } catch {
        case error: Throwable => Some(error)
      }
    }

  private def _cleanup_empty_directories(directories: Vector[Path]): Vector[Throwable] =
    directories.flatMap { directory =>
      try {
        Files.deleteIfExists(directory)
        None
      } catch {
        case _: java.nio.file.DirectoryNotEmptyException => None
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
    candidatearchive: Path,
    args: List[String],
    policy: Policy
  ): RepositoryArtifactCatalog = {
    val sourcepath = sourceCatalogPath(projectdir, policy.kind, name, policy.coordinate)
    val existing: RepositoryArtifactCatalog =
      if (Files.isRegularFile(sourcepath))
        RepositoryArtifactCatalog.load(sourcepath)
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
    val entry0 = policy.versionEntry(version, channel, warehouseRelativePath(warehouse, publishedarchive), candidatearchive, args)
    val entry = policy.coordinate.map { coordinate =>
      val digest = sha256(candidatearchive)
      entry0.copy(
        component = Some(coordinate.qualifiedId),
        file = Some(s"repository/car/${coordinate.carRepositoryRelativePath}"),
        checksumSha256 = Some(digest),
        integrityKey = Some(coordinate.integrityKey(digest))
      )
    }.getOrElse(entry0)
    val releaseversions = existing.versions.filterNot(_is_snapshot_catalog_version)
    val versions =
      if (policy.coordinate.isDefined && snapshotpublish)
        (releaseversions.filterNot(_.version == version) :+ entry).sortBy(_.version)
      else if (snapshotpublish)
        releaseversions
      else if (policy.coordinate.isDefined)
        (existing.versions.filterNot(_.version == version) :+ entry).sortBy(_.version)
      else
        (releaseversions.filterNot(_.version == version) :+ entry).sortBy(_.version)
    def _valid_stable_selector_(selector: Option[String]): Option[String] =
      selector.filter(value => versions.exists(version => version.version == value && version.channel.contains("stable")))
    def _valid_snapshot_selector_(selector: Option[String]): Option[String] =
      selector.filter(value => versions.exists(version => version.version == value && version.channel.contains("snapshot")))
    val recommended =
      if (!snapshotpublish && flag(args, "recommended"))
        Some(version)
      else
        _valid_stable_selector_(existing.recommended).orElse(if (snapshotpublish) None else Some(version))
    val lateststable =
      if (!snapshotpublish && channel == "stable") Some(version) else _valid_stable_selector_(existing.latestStable)
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

  private def _prepare_car_cml_sidecars(projectdir: Path, args: List[String]): PreparedCarCmlSidecars = {
    val resolved = CarCmlSourceResolver.resolve(projectdir).fold(
      issue => RAISE.invalidArgumentFault(s"${issue.code}: ${issue.message}"),
      identity
    )
    path(args, "model-metadata").filter(Files.isRegularFile(_)).map { metadata =>
      val yaml = metadata.resolveSibling(metadata.getFileName.toString.stripSuffix(".json") + ".yaml")
      PreparedCarCmlSidecars(resolved.source, Files.readString(metadata, StandardCharsets.UTF_8), Option(yaml).filter(Files.isRegularFile(_)).map(Files.readString(_, StandardCharsets.UTF_8)))
    }.getOrElse {
      try {
        val metadata = CmlModelMetadata.fromCml(resolved.source, resolved.projectRelativePath, "cml")
        PreparedCarCmlSidecars(resolved.source, metadata.toJsonString, Some(metadata.toYamlString))
      } catch {
        case NonFatal(e) =>
          RAISE.invalidArgumentFault(s"car.cml.metadata.generation_failed: Could not generate CML model metadata from ${resolved.projectRelativePath}: ${Option(e.getMessage).getOrElse(e.getClass.getSimpleName)}")
      }
    }
  }

}
