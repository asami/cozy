package cozy.archive

import java.nio.charset.StandardCharsets
import java.nio.file.{AtomicMoveNotSupportedException, Files, Path, StandardCopyOption}
import java.time.Instant

import com.fasterxml.jackson.core.{JsonFactory, JsonParseException, JsonParser => JacksonParser}
import io.circe.{HCursor, Json}
import io.circe.parser
import scala.util.control.NonFatal

/*
 * @since   Aug. 21, 2026
 * @version Aug. 21, 2026
 * @author  ASAMI, Tomoharu
 */
/**
 * One-way, warehouse-owned migration from the retired component repository
 * index v1 layout to the canonical v2 CAR layout.  Identity is supplied only
 * by the explicit migration manifest; this code never derives it from a
 * legacy archive, descriptor, CML, artifact id, or path.
 */
private[archive] object LegacyComponentRepositoryMigration {
  private val _legacy_schema = "cncf.component-repository-index.v1"
  private val _mapping_schema = "cozy.component-repository-migration.v1"
  private val _artifact_id = "[A-Za-z0-9][A-Za-z0-9._-]*".r

  private final case class Mapping(artifactId: String, namespace: String, id: String)
  private final case class LegacyEntry(
    kind: String,
    artifactId: String,
    catalog: String,
    status: String,
    recommended: Option[String],
    latestStable: Option[String],
    latestSnapshot: Option[String]
  )
  private final case class LegacyIndex(entries: Vector[LegacyEntry])
  private final case class Prepared(destination: Path, bytes: Array[Byte])
  private final case class Staged(destination: Path, temporary: Path)
  private final case class Snapshot(destination: Path, backup: Option[Path])

  /** Returns a strict v2 index only when `indexPath` is a v1 index. */
  def migrateIfNeeded(warehouse: Path, indexPath: Path, generatedAt: Instant): Option[ComponentRepositoryIndex] = {
    if (!Files.isRegularFile(indexPath))
      None
    else {
      val original = Files.readAllBytes(indexPath)
      val schema = _schema(original)
      if (schema != _legacy_schema)
        None
      else {
        try {
          _require_no_duplicate_json_fields(new String(original, StandardCharsets.UTF_8), "index")
          val legacy = _legacy_index(original)
          val mappings = _load_mapping(warehouse)
          val migrated = _prepare(warehouse, indexPath, legacy, mappings, generatedAt)
          _commit(migrated._2 :+ Prepared(indexPath, ComponentRepositoryIndex.render(migrated._1).getBytes(StandardCharsets.UTF_8)))
          Some(migrated._1)
        } catch {
          case error: Throwable if _is_migration_error(error) => throw error
          case NonFatal(error) =>
            throw new IllegalArgumentException("component.repository.migration.transaction.failure", error)
        }
      }
    }
  }

  private def _schema(bytes: Array[Byte]): String =
    parser.parse(new String(bytes, StandardCharsets.UTF_8)).toOption.flatMap(_.hcursor.get[String]("schemaVersion").toOption).getOrElse("")

  private def _legacy_index(bytes: Array[Byte]): LegacyIndex = {
    val json = parser.parse(new String(bytes, StandardCharsets.UTF_8)).fold(
      _ => _fail("index.shape"),
      identity
    )
    val cursor = _object(json, "index")
    _only(cursor, Set("schemaVersion", "generatedAt", "artifacts"), "index")
    if (_string(cursor, "schemaVersion", "index") != _legacy_schema)
      _fail("index.schema")
    _string(cursor, "generatedAt", "index")
    val artifacts = _array(cursor, "artifacts", "index")
    val entries = artifacts.zipWithIndex.map { case (value, n) =>
      val context = s"index.artifacts[$n]"
      val entry = _object(value, context)
      _only(entry, Set("kind", "artifactId", "catalog", "status", "recommended", "latestStable", "latestSnapshot"), context)
      LegacyEntry(
        _string(entry, "kind", context),
        _string(entry, "artifactId", context),
        _string(entry, "catalog", context),
        _string(entry, "status", context),
        _optional_string(entry, "recommended", context),
        _optional_string(entry, "latestStable", context),
        _optional_string(entry, "latestSnapshot", context)
      )
    }
    val duplicate = entries.groupBy(entry => entry.kind -> entry.artifactId).collect {
      case (identity, values) if values.size > 1 => identity
    }.headOption
    if (duplicate.nonEmpty || entries.exists(entry => !Set("car", "sar").contains(entry.kind) || !_artifact_id.pattern.matcher(entry.artifactId).matches()))
      _fail("index.shape")
    LegacyIndex(entries)
  }

  private def _load_mapping(warehouse: Path): Vector[Mapping] = {
    val path = warehouse.resolve(".cozy/component-repository-migration.v1.json")
    if (!Files.isRegularFile(path))
      _fail("mapping.missing")
    val text = new String(Files.readAllBytes(path), StandardCharsets.UTF_8)
    _require_no_duplicate_json_fields(text, "mapping")
    val json = parser.parse(text).fold(_ => _fail("mapping.shape"), identity)
    val cursor = _object(json, "mapping")
    _only(cursor, Set("schema", "cars"), "mapping")
    if (_string(cursor, "schema", "mapping") != _mapping_schema)
      _fail("mapping.schema")
    val mappings = _array(cursor, "cars", "mapping").zipWithIndex.map { case (value, n) =>
      val context = s"mapping.cars[$n]"
      val item = _object(value, context)
      _only(item, Set("artifactId", "namespace", "id"), context)
      Mapping(_string(item, "artifactId", context), _string(item, "namespace", context), _string(item, "id", context))
    }
    val duplicateartifact = mappings.groupBy(_.artifactId).exists(_._2.size > 1)
    val duplicateidentity = mappings.groupBy(mapping => mapping.namespace -> mapping.id).exists(_._2.size > 1)
    if (duplicateartifact || duplicateidentity || mappings.exists(mapping => !_artifact_id.pattern.matcher(mapping.artifactId).matches()))
      _fail("mapping.duplicate")
    mappings.foreach { mapping =>
      try {
        val coordinate = CozyComponentReleaseCoordinateCodec.admit(mapping.namespace, mapping.id, "0.0.0", "migration-manifest")
        if (coordinate.mavenArtifactId != mapping.artifactId) _fail("mapping.identity")
      }
      catch { case NonFatal(_) => _fail("mapping.identity") }
    }
    mappings
  }

  private def _prepare(
    warehouse: Path,
    indexPath: Path,
    legacy: LegacyIndex,
    mappings: Vector[Mapping],
    generatedAt: Instant
  ): (ComponentRepositoryIndex, Vector[Prepared]) = {
    val cars = legacy.entries.filter(_.kind == "car")
    val mappingByArtifact = mappings.map(mapping => mapping.artifactId -> mapping).toMap
    if (cars.exists(entry => !mappingByArtifact.contains(entry.artifactId)))
      _fail("mapping.missing")
    if (mappings.exists(mapping => !cars.exists(_.artifactId == mapping.artifactId)))
      _fail("mapping.unused")

    val prepared = scala.collection.mutable.ArrayBuffer.empty[Prepared]
    val v2cars = cars.map { entry =>
      val mapping = mappingByArtifact(entry.artifactId)
      val catalogPath = _legacy_catalog_path(warehouse, indexPath, entry.catalog)
      if (!Files.isRegularFile(catalogPath) || Files.isSymbolicLink(catalogPath))
        _fail("catalog.path")
      val catalog = RepositoryArtifactCatalog.loadLegacyMigration(catalogPath)
      if (catalog.artifactId != entry.artifactId || catalog.versions.isEmpty)
        _fail("catalog.shape")
      val versions = catalog.versions.map { version =>
        if (version.version.trim.isEmpty || version.file.forall(_.trim.isEmpty))
          _fail("catalog.shape")
        val coordinate = _coordinate(mapping, version.version)
        val legacyArchive = _inside_warehouse(warehouse, version.file.get, "archive.path")
        if (!Files.isRegularFile(legacyArchive) || Files.isSymbolicLink(legacyArchive))
          _fail("archive.path")
        val digest = RepositoryArtifactPublisher.sha256(legacyArchive)
        version.checksumSha256.foreach { expected =>
          if (expected != digest) _fail("integrity.mismatch")
        }
        val legacySidecar = legacyArchive.resolveSibling(legacyArchive.getFileName.toString + ".sha256")
        if (Files.exists(legacySidecar)) {
          if (!Files.isRegularFile(legacySidecar) || Files.isSymbolicLink(legacySidecar) ||
            new String(Files.readAllBytes(legacySidecar), StandardCharsets.UTF_8) != digest + "\n")
            _fail("integrity.mismatch")
        }
        val canonicalArchive = warehouse.resolve("repository/car").resolve(coordinate.carRepositoryRelativePath).normalize()
        val canonicalSidecar = canonicalArchive.resolveSibling(canonicalArchive.getFileName.toString + ".sha256")
        _add_if_new(prepared, canonicalArchive, Files.readAllBytes(legacyArchive))
        _add_if_new(prepared, canonicalSidecar, (digest + "\n").getBytes(StandardCharsets.UTF_8))
        version.copy(
          component = Some(coordinate.qualifiedId),
          file = Some(s"repository/car/${coordinate.carRepositoryRelativePath}"),
          checksumSha256 = Some(digest),
          integrityKey = Some(coordinate.integrityKey(digest))
        )
      }
      if (versions.map(_.version).distinct.size != versions.size)
        _fail("catalog.shape")
      val coordinate = _coordinate(mapping, "0.0.0")
      val migratedCatalog = RepositoryArtifactCatalog(
        schemaVersion = "2", kind = "car", artifactId = coordinate.mavenArtifactId,
        recommended = catalog.recommended, latestStable = catalog.latestStable, latestSnapshot = catalog.latestSnapshot,
        status = catalog.status.orElse(Some(entry.status)), aliases = catalog.aliases, versions = versions,
        tags = catalog.tags, terms = catalog.terms, namespace = Some(mapping.namespace), id = Some(mapping.id)
      )
      val canonicalCatalog = warehouse.resolve("repository/catalog").resolve(coordinate.carCatalogRelativePath).normalize()
      _add_if_new(prepared, canonicalCatalog, migratedCatalog.toYaml.getBytes(StandardCharsets.UTF_8))
      _copy_sidecars(prepared, catalogPath, canonicalCatalog, entry.artifactId)
      ComponentRepositoryIndexEntry(
        "car", coordinate.mavenArtifactId, coordinate.carCatalogRelativePath, entry.status,
        migratedCatalog.recommended, migratedCatalog.latestStable, migratedCatalog.latestSnapshot,
        Some(mapping.namespace), Some(mapping.id)
      )
    }
    val sars = legacy.entries.filter(_.kind == "sar").map { entry =>
      ComponentRepositoryIndexEntry("sar", entry.artifactId, entry.catalog, entry.status, entry.recommended, entry.latestStable, entry.latestSnapshot)
    }
    val index = ComponentRepositoryIndex(ComponentRepositoryIndex.SCHEMA_VERSION, generatedAt, v2cars ++ sars).normalized
    val staged = _stage(prepared.toVector)
    try {
      val candidates = staged.map(item => item.destination.toAbsolutePath.normalize() -> Some(item.temporary)).toMap
      ComponentRepositoryIndex.validateCatalogs(index, indexPath, candidates)
      staged.foreach(item => Files.deleteIfExists(item.temporary))
      index -> prepared.toVector
    } catch {
      case error: Throwable =>
        staged.foreach(item => Files.deleteIfExists(item.temporary))
        throw error
    }
  }

  private def _coordinate(mapping: Mapping, version: String): CozyComponentReleaseCoordinateCodec.Coordinate =
    try CozyComponentReleaseCoordinateCodec.admit(mapping.namespace, mapping.id, version, "migration-manifest")
    catch { case NonFatal(_) => _fail("mapping.identity") }

  private def _copy_sidecars(prepared: scala.collection.mutable.ArrayBuffer[Prepared], legacyCatalog: Path, canonicalCatalog: Path, artifactId: String): Unit =
    Vector(".cml", ".model-metadata.json", ".model-metadata.yaml").foreach { suffix =>
      val source = legacyCatalog.resolveSibling(artifactId + suffix)
      if (Files.exists(source)) {
        if (!Files.isRegularFile(source) || Files.isSymbolicLink(source)) _fail("catalog.sidecar")
        _add_if_new(prepared, canonicalCatalog.resolveSibling(artifactId + suffix), Files.readAllBytes(source))
      }
    }

  private def _add_if_new(prepared: scala.collection.mutable.ArrayBuffer[Prepared], destination: Path, bytes: Array[Byte]): Unit = {
    val normalized = destination.toAbsolutePath.normalize()
    prepared.find(_.destination.toAbsolutePath.normalize() == normalized) match {
      case Some(existing) if java.util.Arrays.equals(existing.bytes, bytes) => ()
      case Some(_) => _fail("collision")
      case None if Files.exists(normalized) =>
        if (!Files.isRegularFile(normalized) || Files.isSymbolicLink(normalized) || !java.util.Arrays.equals(Files.readAllBytes(normalized), bytes))
          _fail("collision")
      case None => prepared += Prepared(normalized, bytes)
    }
  }

  private def _stage(prepared: Vector[Prepared]): Vector[Staged] =
    prepared.map { item =>
      Option(item.destination.getParent).foreach(Files.createDirectories(_))
      val temporary = Files.createTempFile(item.destination.getParent, s".${item.destination.getFileName}-migration-", ".tmp")
      Files.write(temporary, item.bytes)
      Staged(item.destination, temporary)
    }

  /** The index candidate is deliberately appended last: it is the visibility marker. */
  private def _commit(prepared: Vector[Prepared]): Unit = {
    val staged = _stage(prepared)
    var snapshots = Vector.empty[Snapshot]
    try {
      snapshots = staged.map(item => _snapshot(item.destination))
      staged.foreach { item => _replace(item.destination, item.temporary) }
    } catch {
      case error: Throwable =>
        snapshots.reverse.foreach(_restore)
        throw error
    } finally {
      staged.foreach(item => Files.deleteIfExists(item.temporary))
      snapshots.foreach(_.backup.foreach(Files.deleteIfExists(_)))
    }
  }

  private def _snapshot(destination: Path): Snapshot =
    if (Files.exists(destination)) {
      val backup = Files.createTempFile(destination.getParent, s".${destination.getFileName}-migration-rollback-", ".bak")
      Files.copy(destination, backup, StandardCopyOption.REPLACE_EXISTING)
      Snapshot(destination, Some(backup))
    } else Snapshot(destination, None)

  private def _restore(snapshot: Snapshot): Unit = snapshot.backup match {
    case Some(backup) => _replace(snapshot.destination, backup)
    case None => Files.deleteIfExists(snapshot.destination)
  }

  private def _replace(destination: Path, source: Path): Unit =
    try Files.move(source, destination, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
    catch { case _: AtomicMoveNotSupportedException => Files.move(source, destination, StandardCopyOption.REPLACE_EXISTING) }

  private def _inside_warehouse(warehouse: Path, relative: String, code: String): Path = {
    val root = warehouse.toAbsolutePath.normalize()
    val path = root.resolve(relative).normalize()
    if (relative.trim.isEmpty || Path.of(relative).isAbsolute || !path.startsWith(root)) _fail(code)
    path
  }

  /** v1 index catalog values are relative to the directory containing index.json. */
  private def _legacy_catalog_path(warehouse: Path, indexPath: Path, relative: String): Path = {
    val warehouseroot = warehouse.toAbsolutePath.normalize()
    val catalogroot = Option(indexPath.toAbsolutePath.normalize().getParent).getOrElse(_fail("catalog.path"))
    val path = catalogroot.resolve(relative).normalize()
    val traversal = relative.replace('\\', '/').split('/').contains("..")
    if (relative.trim.isEmpty || Path.of(relative).isAbsolute || traversal ||
      !catalogroot.startsWith(warehouseroot) || !path.startsWith(catalogroot) || !path.startsWith(warehouseroot))
      _fail("catalog.path")
    path
  }

  private def _object(json: Json, context: String): HCursor =
    if (json.isObject) json.hcursor else _fail(s"$context.shape")

  private def _array(cursor: HCursor, name: String, context: String): Vector[Json] =
    cursor.get[Vector[Json]](name).fold(_ => _fail(s"$context.shape"), identity)

  private def _string(cursor: HCursor, name: String, context: String): String =
    cursor.get[String](name).fold(_ => _fail(s"$context.shape"), _.trim) match {
      case "" => _fail(s"$context.shape")
      case value => value
    }

  private def _optional_string(cursor: HCursor, name: String, context: String): Option[String] =
    cursor.get[Option[String]](name).fold(_ => _fail(s"$context.shape"), _.map(_.trim).filter(_.nonEmpty))

  private def _only(cursor: HCursor, allowed: Set[String], context: String): Unit =
    if (cursor.keys.toVector.flatten.exists(field => !allowed.contains(field))) _fail(s"$context.shape")

  private def _require_no_duplicate_json_fields(text: String, source: String): Unit = {
    val parser = new JsonFactory().enable(JacksonParser.Feature.STRICT_DUPLICATE_DETECTION).createParser(text)
    try {
      try while (parser.nextToken() != null) {}
      catch { case _: JsonParseException => _fail(s"$source.duplicate-field") }
    } finally parser.close()
  }

  private def _is_migration_error(error: Throwable): Boolean =
    Option(error.getMessage).exists(_.startsWith("component.repository.migration."))

  private def _fail(code: String): Nothing =
    throw new IllegalArgumentException(s"component.repository.migration.$code")
}
