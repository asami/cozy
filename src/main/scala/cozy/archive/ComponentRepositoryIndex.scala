package cozy.archive

import java.nio.charset.StandardCharsets
import java.nio.file.{AtomicMoveNotSupportedException, Files, Path, StandardCopyOption}
import java.time.Instant
import scala.util.Try
import io.circe.{ACursor, Decoder, HCursor, Json}
import io.circe.parser
import com.fasterxml.jackson.core.{JsonFactory, JsonParseException, JsonParser => JacksonParser}

/*
 * Cozy producer/validator for the CNCF Component Repository index contract.
 *
 * @since   Jul. 21, 2026
 * @version Aug.  7, 2026
 * @author  ASAMI, Tomoharu
 */
final case class ComponentRepositoryIndexEntry(
  kind: String,
  artifactId: String,
  catalog: String,
  status: String,
  recommended: Option[String],
  latestStable: Option[String],
  latestSnapshot: Option[String],
  namespace: Option[String] = None,
  id: Option[String] = None
) {
  def identity: (String, String, String) =
    if (kind == "car") (kind, namespace.getOrElse(""), id.getOrElse(""))
    else (kind, "", artifactId)
}

final case class ComponentRepositoryIndex(
  schemaVersion: String,
  generatedAt: Instant,
  artifacts: Vector[ComponentRepositoryIndexEntry]
) {
  def normalized: ComponentRepositoryIndex = copy(artifacts = artifacts.sortBy(_.identity))

  def render: String = ComponentRepositoryIndex.render(this)
}

object ComponentRepositoryIndex {
  val SCHEMA_VERSION = "cncf.component-repository-index.v2"
  val PUBLIC_PATH = "repository/catalog/index.json"

  private val _valid_kinds = Set("car", "sar")
  private val _valid_statuses = Set("active", "deprecated", "disabled")
  private val _artifact_id_pattern = "[A-Za-z0-9][A-Za-z0-9._-]*".r
  private val _catalog_extensions = Vector(".yaml", ".yml", ".json")

  def load(path: Path): ComponentRepositoryIndex =
    parse(new String(Files.readAllBytes(path), StandardCharsets.UTF_8))

  def parse(text: String): ComponentRepositoryIndex = {
    _require_no_json_duplicate_fields(text)
    val json = parser.parse(text).fold(
      error => throw new IllegalArgumentException(s"Invalid component repository index JSON: ${error.message}"),
      identity
    )
    val cursor = json.hcursor
    _only_fields(cursor, Set("schemaVersion", "generatedAt", "artifacts"), "index")
    val schemaversion = _required[String](cursor, "schemaVersion", "index")
    val generatedtext = _required[String](cursor, "generatedAt", "index")
    val generatedat = Try(Instant.parse(generatedtext)).getOrElse(
      throw new IllegalArgumentException(s"Invalid component repository index generatedAt: $generatedtext")
    )
    val entries = _required[Vector[Json]](cursor, "artifacts", "index").zipWithIndex.map {
      case (entry, index) => _entry(entry.hcursor, index)
    }
    _validated(ComponentRepositoryIndex(schemaversion, generatedat, entries).normalized)
  }

  def update(
    existing: Option[ComponentRepositoryIndex],
    catalog: RepositoryArtifactCatalog,
    generatedAt: Instant
  ): ComponentRepositoryIndex = {
    val current = existing.map(_validated).getOrElse(ComponentRepositoryIndex(SCHEMA_VERSION, generatedAt, Vector.empty))
    val retained = current.artifacts.filterNot(_.identity == _identity(catalog))
    val artifacts =
      if (catalog.versions.isEmpty)
        retained
      else
        retained :+ ComponentRepositoryIndexEntry(
          catalog.kind,
          catalog.artifactId,
          if (catalog.kind == "car") {
            CozyComponentReleaseCoordinateCodec.admit(
              catalog.namespace.getOrElse(""), catalog.id.getOrElse(""), "0.0.0", "index"
            ).carCatalogRelativePath
          } else s"${catalog.kind}/${catalog.artifactId}.yaml",
          catalog.status.getOrElse("active"),
          catalog.recommended,
          catalog.latestStable,
          catalog.latestSnapshot,
          catalog.namespace,
          catalog.id
        )
    _validated(ComponentRepositoryIndex(SCHEMA_VERSION, generatedAt, artifacts).normalized)
  }

  /**
   * `candidateFiles` overlays prospective final paths with prepared sibling files
   * (or an intended absence) so publication can validate a complete transaction
   * without making any candidate visible in the repository.
   */
  def validateCatalogs(
    index: ComponentRepositoryIndex,
    indexPath: Path,
    candidateFiles: Map[Path, Option[Path]] = Map.empty
  ): ComponentRepositoryIndex = {
    val normalized = _validated(index.normalized)
    val catalogroot = Option(indexPath.getParent).getOrElse(
      throw new IllegalArgumentException(s"Component repository index has no parent directory: $indexPath")
    )
    normalized.artifacts.foreach { entry =>
      val path = catalogroot.resolve(entry.catalog).normalize()
      _require(path.startsWith(catalogroot.normalize()), s"Component repository catalog escapes index directory: ${entry.catalog}")
      val candidate = candidateFiles.get(path.toAbsolutePath.normalize()).flatten
      val catalogpath = candidate.getOrElse(path)
      _require(candidate.isDefined || Files.isRegularFile(path), s"Component repository catalog is missing: ${entry.catalog}")
      val catalog =
        if (candidate.isDefined) RepositoryArtifactCatalog.load(catalogpath, path)
        else RepositoryArtifactCatalog.load(path)
      _require(catalog.kind == entry.kind, s"Component repository catalog kind mismatch: ${entry.kind}:${entry.artifactId}")
      _require(catalog.artifactId == entry.artifactId, s"Component repository catalog identity mismatch: ${entry.kind}:${entry.artifactId}")
      if (entry.kind == "car") _validate_car_entry(entry, catalog, catalogroot, candidateFiles)
      _require(catalog.status.getOrElse("active") == entry.status, s"Component repository catalog status mismatch: ${entry.kind}:${entry.artifactId}")
      _require(catalog.recommended == entry.recommended, s"Component repository recommended selector is stale: ${entry.kind}:${entry.artifactId}")
      _require(catalog.latestStable == entry.latestStable, s"Component repository latestStable selector is stale: ${entry.kind}:${entry.artifactId}")
      _require(catalog.latestSnapshot == entry.latestSnapshot, s"Component repository latestSnapshot selector is stale: ${entry.kind}:${entry.artifactId}")
    }
    normalized
  }

  def render(index: ComponentRepositoryIndex): String = {
    val normalized = _validated(index.normalized)
    Json.obj(
      "schemaVersion" -> Json.fromString(normalized.schemaVersion),
      "generatedAt" -> Json.fromString(normalized.generatedAt.toString),
      "artifacts" -> Json.arr(normalized.artifacts.map(_entry_json): _*)
    ).spaces2 + "\n"
  }

  def writeAtomic(path: Path, index: ComponentRepositoryIndex): Unit = {
    val parent = Option(path.getParent).getOrElse(
      throw new IllegalArgumentException(s"Component repository index has no parent directory: $path")
    )
    Files.createDirectories(parent)
    val temporary = Files.createTempFile(parent, ".component-repository-index-", ".tmp")
    try {
      Files.write(temporary, render(index).getBytes(StandardCharsets.UTF_8))
      try {
        Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
      } catch {
        case _: AtomicMoveNotSupportedException =>
          Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING)
      }
    } finally {
      Files.deleteIfExists(temporary)
    }
  }

  private def _entry(cursor: HCursor, index: Int): ComponentRepositoryIndexEntry = {
    val context = s"artifact[$index]"
    _only_fields(
      cursor,
      Set("kind", "namespace", "id", "artifactId", "catalog", "status", "recommended", "latestStable", "latestSnapshot"),
      context
    )
    val entry = ComponentRepositoryIndexEntry(
      _required[String](cursor, "kind", context),
      _required[String](cursor, "artifactId", context),
      _required[String](cursor, "catalog", context),
      _required[String](cursor, "status", context),
      _optional[String](cursor, "recommended", context),
      _optional[String](cursor, "latestStable", context),
      _optional[String](cursor, "latestSnapshot", context),
      _optional[String](cursor, "namespace", context),
      _optional[String](cursor, "id", context)
    )
    _validate_entry(entry)
    entry
  }

  private def _entry_json(entry: ComponentRepositoryIndexEntry): Json = {
    val fields = Vector(
      Some("kind" -> Json.fromString(entry.kind)),
      entry.namespace.map("namespace" -> Json.fromString(_)),
      entry.id.map("id" -> Json.fromString(_)),
      Some("artifactId" -> Json.fromString(entry.artifactId)),
      Some("catalog" -> Json.fromString(entry.catalog)),
      Some("status" -> Json.fromString(entry.status)),
      entry.recommended.map("recommended" -> Json.fromString(_)),
      entry.latestStable.map("latestStable" -> Json.fromString(_)),
      entry.latestSnapshot.map("latestSnapshot" -> Json.fromString(_))
    ).flatten
    Json.obj(fields: _*)
  }

  private def _validated(index: ComponentRepositoryIndex): ComponentRepositoryIndex = {
    _require(index.schemaVersion == SCHEMA_VERSION,
      s"component.repository-index.schema.unsupported source=index expected=$SCHEMA_VERSION actual=${index.schemaVersion}")
    val duplicates = index.artifacts.groupBy(_.identity).collect {
      case (identity, entries) if entries.size > 1 => identity.productIterator.mkString(":")
    }.toVector.sorted
    _require(duplicates.isEmpty, s"Duplicate component repository artifacts: ${duplicates.mkString(", ")}")
    index.artifacts.foreach(_validate_entry)
    index
  }

  private def _validate_entry(entry: ComponentRepositoryIndexEntry): Unit = {
    _require(_valid_kinds.contains(entry.kind), s"Unsupported component artifact kind: ${entry.kind}")
    _require(_artifact_id_pattern.pattern.matcher(entry.artifactId).matches(), s"Invalid component repository artifactId: ${entry.artifactId}")
    _require(_valid_statuses.contains(entry.status), s"Invalid component repository artifact status: ${entry.status}")
    if (entry.kind == "car") {
      val namespace = entry.namespace.getOrElse(throw new IllegalArgumentException("component.release-coordinate.mismatch source=index expected=namespace actual=missing"))
      val id = entry.id.getOrElse(throw new IllegalArgumentException("component.release-coordinate.mismatch source=index expected=id actual=missing"))
      val coordinate = CozyComponentReleaseCoordinateCodec.admit(namespace, id, "0.0.0", "index")
      CozyComponentReleaseCoordinateCodec.requireProjection(coordinate.mavenArtifactId, entry.artifactId, "artifactId", "index")
    } else
      _require(entry.namespace.isEmpty && entry.id.isEmpty, "SAR index entry must not carry component namespace or id")
    _validate_catalog_path(entry)
    _validate_selector("recommended", entry.recommended)
    _validate_selector("latestStable", entry.latestStable)
    _validate_selector("latestSnapshot", entry.latestSnapshot)
  }

  private def _validate_catalog_path(entry: ComponentRepositoryIndexEntry): Unit = {
    val normalized = entry.catalog.replace('\\', '/')
    val segments = normalized.split('/').toVector
    val filename = segments.lastOption.getOrElse("")
    val extension = _catalog_extensions.find(filename.endsWith)
    val stem = extension.map(filename.stripSuffix).getOrElse("")
    val valid = if (entry.kind == "car") {
      val coordinate = CozyComponentReleaseCoordinateCodec.admit(entry.namespace.getOrElse(""), entry.id.getOrElse(""), "0.0.0", "index")
      CozyComponentReleaseCoordinateCodec.requireProjection(
        coordinate.carCatalogRelativePath, entry.catalog, "catalogPath", "index"
      )
      normalized == entry.catalog &&
        !segments.contains("..") && stem == entry.artifactId
    } else
      normalized == entry.catalog && segments == Vector(entry.kind, filename) && !segments.contains("..") && stem == entry.artifactId
    _require(valid, s"Invalid component repository catalog path for ${entry.kind}:${entry.artifactId}: ${entry.catalog}")
  }

  private def _validate_selector(name: String, value: Option[String]): Unit =
    _require(value.forall(_.trim.nonEmpty), s"Component repository index $name must not be empty")

  private def _required[A: Decoder](cursor: ACursor, field: String, context: String): A =
    cursor.get[A](field).fold(
      error => throw new IllegalArgumentException(s"Component repository index $context requires $field: ${error.message}"),
      identity
    )

  private def _optional[A: Decoder](cursor: ACursor, field: String, context: String): Option[A] =
    cursor.get[Option[A]](field).fold(
      error => throw new IllegalArgumentException(s"Invalid component repository index $context $field: ${error.message}"),
      identity
    )

  private def _require_no_json_duplicate_fields(text: String): Unit = {
    val input = new JsonFactory().enable(JacksonParser.Feature.STRICT_DUPLICATE_DETECTION).createParser(text)
    try {
      try while (input.nextToken() != null) {}
      catch {
        case error: JsonParseException if Option(error.getOriginalMessage).exists(_.contains("Duplicate field")) =>
          val duplicatefield = """Duplicate field ['"]([^'"]+)['"]""".r.findFirstMatchIn(Option(error.getOriginalMessage).getOrElse("")).map(_.group(1)).getOrElse("json")
          throw new IllegalArgumentException(s"component.repository-index.duplicate-field source=index path=json field=$duplicatefield")
        case _: JsonParseException => ()
      }
    } finally input.close()
  }

  private def _only_fields(cursor: HCursor, expected: Set[String], context: String): Unit = {
    val unknown = cursor.keys.toVector.flatten.filterNot(expected).sorted
    _require(unknown.isEmpty, s"Unknown component repository index $context fields: ${unknown.mkString(", ")}")
  }

  private def _require(condition: Boolean, message: => String): Unit =
    if (!condition)
      throw new IllegalArgumentException(message)

  private def _identity(catalog: RepositoryArtifactCatalog): (String, String, String) =
    if (catalog.kind == "car") (catalog.kind, catalog.namespace.getOrElse(""), catalog.id.getOrElse(""))
    else (catalog.kind, "", catalog.artifactId)

  private def _validate_car_entry(
    entry: ComponentRepositoryIndexEntry,
    catalog: RepositoryArtifactCatalog,
    catalogroot: Path,
    candidatefiles: Map[Path, Option[Path]]
  ): Unit = {
    _require(catalog.namespace == entry.namespace && catalog.id == entry.id,
      s"component.release-coordinate.mismatch source=index expected=${entry.namespace.getOrElse("?")}.${entry.id.getOrElse("?")} actual=${catalog.namespace.getOrElse("?")}.${catalog.id.getOrElse("?")}")
    catalog.versions.foreach { release =>
      val coordinate = CozyComponentReleaseCoordinateCodec.admit(entry.namespace.getOrElse(""), entry.id.getOrElse(""), release.version, "index")
      val warehouse = Option(catalogroot.getParent).flatMap(parent => Option(parent.getParent)).getOrElse(
        throw new IllegalArgumentException(s"component.repository.integrity.mismatch source=index field=warehouse actual=missing")
      )
      val archive = warehouse.resolve(release.file.getOrElse("")).toAbsolutePath.normalize()
      val candidatearchive = candidatefiles.get(archive).flatten
      _require(candidatearchive.isDefined || Files.isRegularFile(archive), s"component.repository.integrity.mismatch source=index field=archive expected=regular-file actual=${release.file.getOrElse("missing")}")
      val digest = RepositoryArtifactPublisher.sha256(candidatearchive.getOrElse(archive))
      val expectedkey = coordinate.integrityKey(digest)
      _require(release.checksumSha256.contains(digest) && release.integrityKey.contains(expectedkey),
        s"component.repository.integrity.mismatch source=index field=catalog expected=$expectedkey actual=${release.integrityKey.getOrElse("missing")}")
      val sidecar = archive.resolveSibling(archive.getFileName.toString + ".sha256").toAbsolutePath.normalize()
      val candidatesidecar = candidatefiles.get(sidecar).flatten
      val record = if (candidatesidecar.isDefined) new String(Files.readAllBytes(candidatesidecar.get), StandardCharsets.UTF_8)
        else if (Files.isRegularFile(sidecar)) new String(Files.readAllBytes(sidecar), StandardCharsets.UTF_8) else "missing"
      _require(record == digest + "\n", s"component.repository.integrity.mismatch source=index field=sha256-sidecar expected=$digest actual=${record.trim}")
    }
  }
}
