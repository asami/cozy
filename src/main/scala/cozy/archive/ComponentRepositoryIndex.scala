package cozy.archive

import java.nio.charset.StandardCharsets
import java.nio.file.{AtomicMoveNotSupportedException, Files, Path, StandardCopyOption}
import java.time.Instant
import scala.util.Try
import io.circe.{ACursor, Decoder, HCursor, Json}
import io.circe.parser

/*
 * Cozy producer/validator for the CNCF Component Repository index contract.
 *
 * @since   Jul. 21, 2026
 * @version Jul. 21, 2026
 * @author  ASAMI, Tomoharu
 */
final case class ComponentRepositoryIndexEntry(
  kind: String,
  artifactId: String,
  catalog: String,
  status: String,
  recommended: Option[String],
  latestStable: Option[String],
  latestSnapshot: Option[String]
) {
  def identity: (String, String) = kind -> artifactId
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
  val SchemaVersion = "cncf.component-repository-index.v1"
  val PublicPath = "repository/catalog/index.json"

  private val _valid_kinds = Set("car", "sar")
  private val _valid_statuses = Set("active", "deprecated", "disabled")
  private val _artifact_id_pattern = "[A-Za-z0-9][A-Za-z0-9._-]*".r
  private val _catalog_extensions = Vector(".yaml", ".yml", ".json")

  def load(path: Path): ComponentRepositoryIndex =
    parse(new String(Files.readAllBytes(path), StandardCharsets.UTF_8))

  def parse(text: String): ComponentRepositoryIndex = {
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
    val current = existing.map(_validated).getOrElse(ComponentRepositoryIndex(SchemaVersion, generatedAt, Vector.empty))
    val retained = current.artifacts.filterNot(_.identity == (catalog.kind -> catalog.artifactId))
    val artifacts =
      if (catalog.versions.isEmpty)
        retained
      else
        retained :+ ComponentRepositoryIndexEntry(
          catalog.kind,
          catalog.artifactId,
          s"${catalog.kind}/${catalog.artifactId}.yaml",
          catalog.status.getOrElse("active"),
          catalog.recommended,
          catalog.latestStable,
          catalog.latestSnapshot
        )
    _validated(ComponentRepositoryIndex(SchemaVersion, generatedAt, artifacts).normalized)
  }

  def validateCatalogs(index: ComponentRepositoryIndex, indexPath: Path): ComponentRepositoryIndex = {
    val normalized = _validated(index.normalized)
    val catalogroot = Option(indexPath.getParent).getOrElse(
      throw new IllegalArgumentException(s"Component repository index has no parent directory: $indexPath")
    )
    normalized.artifacts.foreach { entry =>
      val path = catalogroot.resolve(entry.catalog).normalize()
      _require(path.startsWith(catalogroot.normalize()), s"Component repository catalog escapes index directory: ${entry.catalog}")
      _require(Files.isRegularFile(path), s"Component repository catalog is missing: ${entry.catalog}")
      val catalog = RepositoryArtifactCatalog.load(path)
      _require(catalog.kind == entry.kind, s"Component repository catalog kind mismatch: ${entry.kind}:${entry.artifactId}")
      _require(catalog.artifactId == entry.artifactId, s"Component repository catalog identity mismatch: ${entry.kind}:${entry.artifactId}")
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
      Set("kind", "artifactId", "catalog", "status", "recommended", "latestStable", "latestSnapshot"),
      context
    )
    val entry = ComponentRepositoryIndexEntry(
      _required[String](cursor, "kind", context),
      _required[String](cursor, "artifactId", context),
      _required[String](cursor, "catalog", context),
      _required[String](cursor, "status", context),
      _optional[String](cursor, "recommended", context),
      _optional[String](cursor, "latestStable", context),
      _optional[String](cursor, "latestSnapshot", context)
    )
    _validate_entry(entry)
    entry
  }

  private def _entry_json(entry: ComponentRepositoryIndexEntry): Json = {
    val fields = Vector(
      Some("kind" -> Json.fromString(entry.kind)),
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
    _require(index.schemaVersion == SchemaVersion, s"Unsupported component repository index schemaVersion: ${index.schemaVersion}")
    val duplicates = index.artifacts.groupBy(_.identity).collect {
      case (identity, entries) if entries.size > 1 => s"${identity._1}:${identity._2}"
    }.toVector.sorted
    _require(duplicates.isEmpty, s"Duplicate component repository artifacts: ${duplicates.mkString(", ")}")
    index.artifacts.foreach(_validate_entry)
    index
  }

  private def _validate_entry(entry: ComponentRepositoryIndexEntry): Unit = {
    _require(_valid_kinds.contains(entry.kind), s"Unsupported component artifact kind: ${entry.kind}")
    _require(_artifact_id_pattern.pattern.matcher(entry.artifactId).matches(), s"Invalid component repository artifactId: ${entry.artifactId}")
    _require(_valid_statuses.contains(entry.status), s"Invalid component repository artifact status: ${entry.status}")
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
    val valid =
      normalized == entry.catalog &&
        segments == Vector(entry.kind, filename) &&
        !segments.contains("..") &&
        stem == entry.artifactId
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

  private def _only_fields(cursor: HCursor, expected: Set[String], context: String): Unit = {
    val unknown = cursor.keys.toVector.flatten.filterNot(expected).sorted
    _require(unknown.isEmpty, s"Unknown component repository index $context fields: ${unknown.mkString(", ")}")
  }

  private def _require(condition: Boolean, message: => String): Unit =
    if (!condition)
      throw new IllegalArgumentException(message)
}
