package cozy.archive

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path}
import io.circe.{HCursor, Json}
import io.circe.parser
import scala.collection.JavaConverters._
import com.fasterxml.jackson.core.{JsonFactory, JsonParseException, JsonParser => JacksonParser}
import org.goldenport.RAISE

/*
 * @since   May. 20, 2026
 *  version Jul. 13, 2026
 * @version Aug.  7, 2026
 * @author  ASAMI, Tomoharu
 */
final case class RepositoryArtifactCatalog(
  schemaVersion: String,
  kind: String,
  artifactId: String,
  recommended: Option[String],
  latestStable: Option[String],
  latestSnapshot: Option[String],
  status: Option[String],
  aliases: Vector[String],
  versions: Vector[RepositoryArtifactCatalogVersion],
  tags: Vector[String] = Vector.empty,
  terms: Vector[String] = Vector.empty,
  namespace: Option[String] = None,
  id: Option[String] = None
) {
  def validate: RepositoryArtifactCatalog = {
    RepositoryArtifactCatalog.validate(this, None)
    this
  }

  def validateSourcePath(path: Path): RepositoryArtifactCatalog = {
    RepositoryArtifactCatalog.validate(this, Some(path))
    this
  }

  def toYaml: String =
    RepositoryArtifactCatalog.toYaml(this)
}

final case class RepositoryArtifactCatalogVersion(
  version: String,
  channel: Option[String],
  status: Option[String],
  component: Option[String],
  publishedAt: Option[String],
  file: Option[String],
  runtime: Option[RepositoryArtifactRuntimeRequirement],
  checksumSha256: Option[String],
  integrityKey: Option[String] = None
) {
  def effectiveStatus: String = status.getOrElse("active")
}

final case class RepositoryArtifactRuntimeRequirement(
  minimum: Option[String],
  maximum: Option[String],
  excluded: Vector[String],
  tested: Vector[String]
)

object RepositoryArtifactCatalog {
  private val _valid_kinds = Set("car", "sar")
  private val _valid_statuses = Set("active", "deprecated", "disabled")
  private val _valid_channels = Set("stable", "snapshot")

  def load(path: Path): RepositoryArtifactCatalog =
    load(path, path)

  /** Parse bytes from `path` while applying persisted-path projections to `logicalPath`. */
  def load(path: Path, logicalPath: Path): RepositoryArtifactCatalog = {
      val catalog = _load_unvalidated(path)
      catalog.validateSourcePath(logicalPath)
  }

  /** Publisher-only preflight: establish coordinate identity before path/version projections. */
  private[archive] def _load_for_coordinate(
    path: Path,
    expectedcoordinate: CozyComponentReleaseCoordinateCodec.Coordinate
  ): RepositoryArtifactCatalog = {
    val catalog = _load_unvalidated(path)
    _validate_kind_and_schema(catalog, Some(path))
    if (catalog.kind != "car" || catalog.namespace != Some(expectedcoordinate.namespace) || catalog.id != Some(expectedcoordinate.id))
      RAISE.invalidArgumentFault(
        s"component.release-coordinate.mismatch source=$path expected=${expectedcoordinate.qualifiedId} actual=${catalog.namespace.map(_ + ".").getOrElse("")}${catalog.id.getOrElse(catalog.artifactId)}"
      )
    catalog.validateSourcePath(path)
  }

  def parse(text: String): RepositoryArtifactCatalog =
    _parse_yaml(text).validate

  private def _load_unvalidated(path: Path): RepositoryArtifactCatalog = {
    val text = new String(Files.readAllBytes(path), StandardCharsets.UTF_8)
    if (path.getFileName.toString.toLowerCase(java.util.Locale.ROOT).endsWith(".json")) _parse_json(text)
    else _parse_yaml(text)
  }

  private def _parse_yaml(text: String): RepositoryArtifactCatalog = {
    val parsed = YamlParser.parse(text)
    val root = parsed.values
    if (root.get("kind").contains("car") && root.get("schemaVersion").contains("2"))
      _validate_canonical_shape(parsed)
    val versions = parsed.versions.map(_version)
    RepositoryArtifactCatalog(
      schemaVersion = root.getOrElse("schemaVersion", "1"),
      kind = root.getOrElse("kind", ""),
      artifactId = root.getOrElse("artifactId", ""),
      recommended = _non_empty(root.get("recommended")),
      latestStable = _non_empty(root.get("latestStable")),
      latestSnapshot = _non_empty(root.get("latestSnapshot")),
      status = _non_empty(root.get("status")),
      aliases = parsed.lists.get("aliases").getOrElse(_csv(root.get("aliases"))),
      versions = versions,
      tags = parsed.lists.get("tags").getOrElse(_csv(root.get("tags"))),
      terms = parsed.lists.get("terms").getOrElse(_csv(root.get("terms"))),
      namespace = _non_empty(root.get("namespace")),
      id = _non_empty(root.get("id"))
    )
  }

  def validate(catalog: RepositoryArtifactCatalog, sourcepath: Option[Path]): Unit = {
    _validate_kind_and_schema(catalog, sourcepath)
    _require(catalog.artifactId.nonEmpty, "Repository artifact catalog requires artifactId")
    if (catalog.kind == "car")
      _validate_car_coordinate(catalog)
    else
      _require(catalog.namespace.isEmpty && catalog.id.isEmpty, "SAR catalog must not carry component namespace or id")
    catalog.status.foreach(status => _require(_valid_statuses.contains(status), s"Invalid repository artifact catalog status: $status"))
    sourcepath.foreach(path => _validate_source_path(catalog, path))
    _validate_versions(catalog)
    _validate_selector(catalog, "recommended", catalog.recommended, None)
    _validate_selector(catalog, "latestStable", catalog.latestStable, Some("stable"))
    _validate_selector(catalog, "latestSnapshot", catalog.latestSnapshot, Some("snapshot"))
  }

  private def _validate_kind_and_schema(catalog: RepositoryArtifactCatalog, sourcepath: Option[Path]): Unit = {
    _require(_valid_kinds.contains(catalog.kind), s"Invalid repository artifact catalog kind: ${catalog.kind}")
    _require(
      (catalog.kind == "sar" && catalog.schemaVersion == "1") ||
        (catalog.kind == "car" && catalog.schemaVersion == "2"),
      s"repository.artifact.catalog.schema.unsupported source=${sourcepath.map(_.toString).getOrElse("catalog")} expected=${if (catalog.kind == "car") "2" else "1"} actual=${catalog.schemaVersion}"
    )
  }

  def toYaml(catalog: RepositoryArtifactCatalog): String = {
    catalog.validate
    val lines =
      Vector(
        s"schemaVersion: ${catalog.schemaVersion}",
        s"kind: ${catalog.kind}"
      ) ++
        _optional_line("namespace", catalog.namespace) ++
        _optional_line("id", catalog.id) ++
        Vector(s"artifactId: ${catalog.artifactId}") ++
        _optional_line("recommended", catalog.recommended) ++
        _optional_line("latestStable", catalog.latestStable) ++
        _optional_line("latestSnapshot", catalog.latestSnapshot) ++
        _optional_line("status", catalog.status) ++
        _list_lines("aliases", catalog.aliases, 0) ++
        _list_lines("tags", catalog.tags, 0) ++
        _list_lines("terms", catalog.terms, 0) ++
        Vector("versions:") ++
        catalog.versions.flatMap(_version_lines)
    lines.mkString("\n") + "\n"
  }

  private def _version(values: Map[String, String]): RepositoryArtifactCatalogVersion =
    RepositoryArtifactCatalogVersion(
      version = values.getOrElse("version", ""),
      channel = _non_empty(values.get("channel")),
      status = _non_empty(values.get("status")),
      component = _non_empty(values.get("component")),
      publishedAt = _non_empty(values.get("publishedAt")),
      file = _non_empty(values.get("file")),
      runtime = _runtime(values),
      checksumSha256 = _non_empty(values.get("checksum.sha256")),
      integrityKey = _non_empty(values.get("integrityKey"))
    )

  private def _runtime(values: Map[String, String]): Option[RepositoryArtifactRuntimeRequirement] = {
    val minimum = _non_empty(values.get("runtime.cncf.minimum"))
    val maximum = _non_empty(values.get("runtime.cncf.maximum"))
    val excluded = _csv(values.get("runtime.cncf.excluded"))
    val tested = _csv(values.get("runtime.cncf.tested"))
    if (minimum.isEmpty && maximum.isEmpty && excluded.isEmpty && tested.isEmpty)
      None
    else
      Some(RepositoryArtifactRuntimeRequirement(minimum, maximum, excluded, tested))
  }

  private def _validate_source_path(catalog: RepositoryArtifactCatalog, path: Path): Unit = {
    val filename = path.getFileName.toString
    val stem = filename.stripSuffix(".yaml").stripSuffix(".yml").stripSuffix(".json")
    _require(stem == catalog.artifactId, s"Catalog filename does not match artifactId: $filename != ${catalog.artifactId}")
    if (catalog.kind == "car") {
      val coordinate = _car_coordinate(catalog, catalog.versions.headOption.map(_.version).getOrElse("0.0.0"))
      val segments = path.iterator().asScala.map(_.toString).toVector
      val catalogindex = segments.lastIndexOf("catalog")
      val actual = if (catalogindex >= 0) segments.drop(catalogindex + 1).mkString("/") else ""
      _projection(coordinate.carCatalogRelativePath, actual, "catalogPath", path.toString)
    } else
      Option(path.getParent).flatMap(parent => Option(parent.getFileName)).foreach { kind =>
        _require(kind.toString == catalog.kind, s"Catalog path kind does not match catalog kind: $kind != ${catalog.kind}")
      }
  }

  private def _validate_versions(catalog: RepositoryArtifactCatalog): Unit = {
    val duplicates = catalog.versions.groupBy(_.version).collect { case (version, xs) if xs.size > 1 => version }.toVector.sorted
    _require(duplicates.isEmpty, s"Duplicate repository artifact catalog versions: ${duplicates.mkString(", ")}")
    val suffix = "." + catalog.kind
    catalog.versions.foreach { version =>
      _require(version.version.nonEmpty, "Repository artifact catalog version requires version")
      version.channel.foreach(channel => _require(_valid_channels.contains(channel), s"Invalid repository artifact catalog channel: $channel"))
      version.status.foreach(status => _require(_valid_statuses.contains(status), s"Invalid repository artifact catalog version status: $status"))
      _require(version.file.nonEmpty, s"Repository artifact catalog version requires file: ${version.version}")
      version.file.foreach(file => _require(file.endsWith(suffix), s"Repository artifact file must end with $suffix: $file"))
      if (catalog.kind == "car") {
        val coordinate = _car_coordinate(catalog, version.version)
        _projection(coordinate.mavenArtifactId, catalog.artifactId, "artifactId", "catalog")
        _projection(s"repository/car/${coordinate.carRepositoryRelativePath}", version.file.get, "file", "catalog")
        val digest = version.checksumSha256.getOrElse(
          throw new IllegalArgumentException(s"component.repository.integrity.mismatch source=catalog field=checksum.sha256 expected=lowercase-64-hex actual=missing")
        )
        _require(_sha256.pattern.matcher(digest).matches(), s"component.repository.integrity.mismatch source=catalog field=checksum.sha256 expected=lowercase-64-hex actual=$digest")
        _projection(coordinate.integrityKey(digest), version.integrityKey.getOrElse("missing"), "integrityKey", "catalog")
      }
    }
  }

  private def _validate_selector(
    catalog: RepositoryArtifactCatalog,
    name: String,
    selector: Option[String],
    expectedchannel: Option[String]
  ): Unit =
    selector.foreach { version =>
      val target = catalog.versions.find(_.version == version)
      _require(target.nonEmpty, s"Catalog $name points to missing version: $version")
      _require(!target.exists(_.effectiveStatus == "disabled"), s"Catalog $name points to disabled version: $version")
      expectedchannel.foreach { channel =>
        _require(target.flatMap(_.channel).contains(channel), s"Catalog $name must point to $channel version: $version")
      }
    }

  private def _version_lines(version: RepositoryArtifactCatalogVersion): Vector[String] =
    Vector(s"  - version: ${version.version}") ++
      _optional_line("channel", version.channel, 4) ++
      _optional_line("status", version.status, 4) ++
      _optional_line("component", version.component, 4) ++
      _optional_line("publishedAt", version.publishedAt, 4) ++
      _optional_line("file", version.file, 4) ++
      version.runtime.toVector.flatMap(_runtime_lines) ++
      version.checksumSha256.toVector.flatMap(value => Vector("    checksum:", s"      sha256: $value")) ++
      _optional_line("integrityKey", version.integrityKey, 4)

  private def _runtime_lines(runtime: RepositoryArtifactRuntimeRequirement): Vector[String] =
    Vector("    runtime:", "      cncf:") ++
      _optional_line("minimum", runtime.minimum, 8) ++
      _optional_line("maximum", runtime.maximum, 8) ++
      _list_lines("excluded", runtime.excluded, 8) ++
      _list_lines("tested", runtime.tested, 8)

  private def _optional_line(name: String, value: Option[String], indent: Int = 0): Vector[String] =
    value.map(v => " " * indent + s"$name: $v").toVector

  private def _list_lines(name: String, values: Vector[String], indent: Int): Vector[String] = {
    val prefix = " " * indent
    if (values.isEmpty)
      Vector(s"$prefix$name: []")
    else
      s"$prefix$name:" +: values.map(value => s"$prefix  - $value")
  }

  private def _non_empty(value: Option[String]): Option[String] =
    value.map(_.trim).filter(_.nonEmpty)

  private def _csv(value: Option[String]): Vector[String] =
    _non_empty(value).map { v =>
      if (v == "[]")
        Vector.empty[String]
      else
        v.stripPrefix("[").stripSuffix("]").split(",").toVector.map(_unquote).map(_.trim).filter(_.nonEmpty)
    }.getOrElse(Vector.empty)

  private def _unquote(value: String): String = {
    val t = value.trim
    if (t.length >= 2 && ((t.head == '"' && t.last == '"') || (t.head == '\'' && t.last == '\'')))
      t.substring(1, t.length - 1)
    else
      t
  }

  private def _parse_json(text: String): RepositoryArtifactCatalog = {
    _require_no_json_duplicate_fields(text)
    val json = parser.parse(text).fold(throw _, identity)
    val cursor = if (json.isObject) json.hcursor else _json_type_error("catalog", "root", "object", json)
    val schema = _json_string(cursor, "schemaVersion", "catalog").getOrElse("1")
    val kind = _json_string(cursor, "kind", "catalog").getOrElse("")
    val canonical = kind == "car" && schema == "2"
    if (canonical)
      _json_only_fields(cursor, Set("schemaVersion", "kind", "namespace", "id", "artifactId", "recommended", "latestStable", "latestSnapshot", "status", "aliases", "tags", "terms", "versions"), "catalog")
    val versions = _json_array(cursor, "versions", "catalog").getOrElse(Vector.empty).zipWithIndex.map {
      case (value, index) =>
        val versioncontext = s"catalog.versions[$index]"
        val versioncursor = _json_object(value, versioncontext, "version")
        if (canonical)
          _json_only_fields(versioncursor, Set("version", "channel", "status", "component", "publishedAt", "file", "runtime", "checksum", "integrityKey"), versioncontext)
        RepositoryArtifactCatalogVersion(
          version = _json_string(versioncursor, "version", versioncontext).getOrElse(""),
          channel = _json_string(versioncursor, "channel", versioncontext),
          status = _json_string(versioncursor, "status", versioncontext),
          component = _json_string(versioncursor, "component", versioncontext),
          publishedAt = _json_string(versioncursor, "publishedAt", versioncontext).orElse(_json_string(versioncursor, "published_at", versioncontext)),
          file = _json_string(versioncursor, "file", versioncontext),
          runtime = _json_runtime(versioncursor, versioncontext, canonical),
          checksumSha256 = _json_checksum(versioncursor, versioncontext, canonical).orElse(_json_string(versioncursor, "checksumSha256", versioncontext)),
          integrityKey = _json_string(versioncursor, "integrityKey", versioncontext)
        )
    }
    RepositoryArtifactCatalog(
      schemaVersion = schema,
      kind = kind,
      artifactId = _json_string(cursor, "artifactId", "catalog").orElse(_json_string(cursor, "artifact_id", "catalog")).getOrElse(""),
      recommended = _json_string(cursor, "recommended", "catalog"),
      latestStable = _json_string(cursor, "latestStable", "catalog").orElse(_json_string(cursor, "latest_stable", "catalog")),
      latestSnapshot = _json_string(cursor, "latestSnapshot", "catalog").orElse(_json_string(cursor, "latest_snapshot", "catalog")),
      status = _json_string(cursor, "status", "catalog"),
      aliases = _json_strings(cursor, "aliases", "catalog"),
      versions = versions,
      tags = _json_strings(cursor, "tags", "catalog"),
      terms = _json_strings(cursor, "terms", "catalog"),
      namespace = _json_string(cursor, "namespace", "catalog"),
      id = _json_string(cursor, "id", "catalog")
    )
  }

  private val _sha256 = "[0-9a-f]{64}".r

  private def _require_no_json_duplicate_fields(text: String): Unit = {
    val input = new JsonFactory().enable(JacksonParser.Feature.STRICT_DUPLICATE_DETECTION).createParser(text)
    try {
      try while (input.nextToken() != null) {}
      catch {
        case error: JsonParseException if Option(error.getOriginalMessage).exists(_.contains("Duplicate field")) =>
          throw new IllegalArgumentException("repository.artifact.catalog.duplicate-field source=catalog path=json")
      }
    } finally input.close()
  }

  private def _validate_canonical_shape(parsed: YamlParser.Parsed): Unit = {
    val rootallowed = Set("schemaVersion", "kind", "namespace", "id", "artifactId", "recommended", "latestStable", "latestSnapshot", "status", "aliases", "tags", "terms", "versions")
    val versionallowed = Set("version", "channel", "status", "component", "publishedAt", "file", "runtime", "runtime.cncf", "runtime.cncf.minimum", "runtime.cncf.maximum", "runtime.cncf.excluded", "runtime.cncf.tested", "checksum", "checksum.sha256", "integrityKey")
    val rootseen = parsed.values.keySet ++ parsed.lists.keySet.map(_.takeWhile(_ != '.')) ++ parsed.rootfields.map(_.takeWhile(_ != '.'))
    val rootunknown = rootseen.filterNot(rootallowed).toVector.sorted
    _require(rootunknown.isEmpty, s"component.release-coordinate.projection-mismatch source=catalog field=unknown expected=canonical-fields actual=${rootunknown.mkString(",")}")
    val versionseen = parsed.versions.zip(parsed.versionfields).flatMap {
      case (values, fields) => values.keySet ++ fields
    }
    val versionunknown = versionseen.filterNot(versionallowed).distinct.sorted
    _require(versionunknown.isEmpty, s"component.release-coordinate.projection-mismatch source=catalog field=versions expected=canonical-fields actual=${versionunknown.mkString(",")}")
    val emptyruntime = parsed.versionfields.zipWithIndex.collect {
      case (fields, index) if fields.contains("runtime") && !fields.exists(_.startsWith("runtime.cncf.")) => index
    }
    _require(emptyruntime.isEmpty, s"component.release-coordinate.projection-mismatch source=catalog field=versions expected=runtime.cncf actual=${emptyruntime.map(index => s"runtime[$index]").mkString(",")}")
  }

  private def _validate_car_coordinate(catalog: RepositoryArtifactCatalog): Unit = {
    val namespace = catalog.namespace.getOrElse(
      throw new IllegalArgumentException("component.release-coordinate.mismatch source=catalog expected=namespace actual=missing")
    )
    val id = catalog.id.getOrElse(
      throw new IllegalArgumentException("component.release-coordinate.mismatch source=catalog expected=id actual=missing")
    )
    // Re-admit the shared identity even before a release is selected.
    CozyComponentReleaseCoordinateCodec.admitIdentity(namespace, id, "catalog")
  }

  private def _car_coordinate(
    catalog: RepositoryArtifactCatalog,
    version: String
  ): CozyComponentReleaseCoordinateCodec.Coordinate =
    CozyComponentReleaseCoordinateCodec.admit(
      catalog.namespace.getOrElse(""),
      catalog.id.getOrElse(""),
      version,
      "catalog"
    )

  private def _projection(expected: String, actual: String, field: String, source: String): Unit =
    CozyComponentReleaseCoordinateCodec.requireProjection(expected, actual, field, source)

  private def _json_runtime(cursor: HCursor, context: String, strict: Boolean): Option[RepositoryArtifactRuntimeRequirement] = {
    _json_field(cursor, "runtime") match {
      case None => None
      case Some(value) =>
        val runtimecursor = _json_object(value, s"$context.runtime", "runtime")
        if (strict)
          _json_only_fields(runtimecursor, Set("cncf"), s"$context.runtime")
        _json_field(runtimecursor, "cncf") match {
          case None => None
          case Some(cncfvalue) =>
            val cncf = _json_object(cncfvalue, s"$context.runtime.cncf", "runtime.cncf")
            if (strict)
              _json_only_fields(cncf, Set("minimum", "maximum", "excluded", "tested"), s"$context.runtime.cncf")
            val minimum = _json_string(cncf, "minimum", s"$context.runtime.cncf")
            val maximum = _json_string(cncf, "maximum", s"$context.runtime.cncf")
            val excluded = _json_strings(cncf, "excluded", s"$context.runtime.cncf")
            val tested = _json_strings(cncf, "tested", s"$context.runtime.cncf")
            if (minimum.isEmpty && maximum.isEmpty && excluded.isEmpty && tested.isEmpty)
              None
            else
              Some(RepositoryArtifactRuntimeRequirement(minimum, maximum, excluded, tested))
        }
    }
  }

  private def _json_checksum(cursor: HCursor, context: String, strict: Boolean): Option[String] =
    _json_field(cursor, "checksum") match {
      case None => None
      case Some(value) =>
        val checksumcursor = _json_object(value, s"$context.checksum", "checksum")
        if (strict)
          _json_only_fields(checksumcursor, Set("sha256"), s"$context.checksum")
        _json_string(checksumcursor, "sha256", s"$context.checksum")
    }

  private def _json_field(cursor: HCursor, name: String): Option[Json] =
    cursor.downField(name).focus

  private def _json_string(cursor: HCursor, name: String, context: String): Option[String] =
    _json_field(cursor, name).map { value =>
      if (value.isNull)
        _json_type_error(context, name, "string", value)
      value.asString.getOrElse(_json_type_error(context, name, "string", value))
    }

  private def _json_strings(cursor: HCursor, name: String, context: String): Vector[String] =
    _json_array(cursor, name, context).map(_.zipWithIndex.map {
      case (value, index) =>
        if (value.isNull)
          _json_type_error(context, s"$name[$index]", "string", value)
        value.asString.getOrElse(_json_type_error(context, s"$name[$index]", "string", value))
    }).getOrElse(Vector.empty)

  private def _json_array(cursor: HCursor, name: String, context: String): Option[Vector[Json]] =
    _json_field(cursor, name).map { value =>
      value.asArray.getOrElse(_json_type_error(context, name, "array", value))
    }

  private def _json_object(value: Json, context: String, field: String): HCursor =
    if (value.isObject)
      value.hcursor
    else
      _json_type_error(context, field, "object", value)

  private def _json_type_error(context: String, field: String, expected: String, value: Json): Nothing =
    throw new IllegalArgumentException(
      s"repository.artifact.catalog.json.type-mismatch source=$context field=$field expected=$expected actual=${_json_type(value)}"
    )

  private def _json_type(value: Json): String =
    if (value.isNull) "null"
    else if (value.isString) "string"
    else if (value.isBoolean) "boolean"
    else if (value.isNumber) "number"
    else if (value.isArray) "array"
    else if (value.isObject) "object"
    else "unknown"

  private def _json_only_fields(cursor: HCursor, expected: Set[String], source: String): Unit = {
    val unknown = cursor.keys.toVector.flatten.filterNot(expected).toVector.sorted
    _require(unknown.isEmpty, s"component.release-coordinate.projection-mismatch source=$source field=unknown expected=canonical-fields actual=${unknown.mkString(",")}")
  }

  private def _require(condition: Boolean, message: => String): Unit =
    if (!condition)
      throw new IllegalArgumentException(message)

  private object YamlParser {
    final case class Parsed(
      values: Map[String, String],
      lists: Map[String, Vector[String]],
      versions: Vector[Map[String, String]],
      rootfields: Set[String],
      versionfields: Vector[Set[String]]
    )

    def parse(text: String): Parsed = {
      var values = Map.empty[String, String]
      var lists = Map.empty[String, Vector[String]]
      var versions = Vector.empty[Map[String, String]]
      var versionvalues = Map.empty[String, String]
      var rootstack = Vector.empty[(Int, String)]
      var versionstack = Vector.empty[(Int, String)]
      var rootseen = Set.empty[String]
      var versionseen = Set.empty[String]
      var versionfields = Vector.empty[Set[String]]
      var versionactive = false
      var inversions = false

      def _finish_version_(): Unit =
        if (versionactive) {
          versions :+= versionvalues
          versionfields :+= versionseen
          versionvalues = Map.empty
          versionseen = Set.empty
          versionactive = false
        }

      def _path_(stack: Vector[(Int, String)], key: String): String =
        (stack.map(_._2) :+ key).mkString(".")

      def _drop_stack_(stack: Vector[(Int, String)], indent: Int): Vector[(Int, String)] =
        stack.dropRight(stack.reverse.takeWhile(_._1 >= indent).length)

      def _append_list_(path: String, value: String): Unit =
        if (path.nonEmpty)
          lists = lists.updated(path, lists.getOrElse(path, Vector.empty) :+ _unquote(value))

      def _record_root_(path: String): Unit = {
        if (rootseen.contains(path))
          throw new IllegalArgumentException(s"repository.artifact.catalog.duplicate-field source=catalog path=$path")
        rootseen += path
      }

      def _record_version_(path: String): Unit = {
        if (versionseen.contains(path))
          throw new IllegalArgumentException(s"repository.artifact.catalog.duplicate-field source=catalog path=versions[${versions.size}].$path")
        versionseen += path
      }

      def _append_version_list_(path: String, value: String): Unit =
        if (path.nonEmpty) {
          val current = versionvalues.get(path).map(v => if (v == "[]") "" else v + ",").getOrElse("")
          versionvalues = versionvalues.updated(path, current + _unquote(value))
        }

      text.split("\\r?\\n").toVector.foreach { raw =>
        val line = _strip_comment(raw)
        if (line.trim.nonEmpty) {
          val indent = line.takeWhile(_ == ' ').length
          val trimmed = line.trim
          if (indent == 0)
            inversions = trimmed == "versions:"
          if (inversions && indent == 2 && trimmed.startsWith("- ")) {
            _finish_version_()
            versionactive = true
            versionstack = Vector.empty
            val rest = trimmed.drop(2).trim
            if (rest.contains(":")) {
              val (key, value) = _split_key_value(rest)
              _record_version_(key)
              versionvalues = versionvalues.updated(key, _unquote(value))
            }
          } else if (inversions && indent > 2) {
            if (trimmed.startsWith("- ")) {
              versionstack = _drop_stack_(versionstack, indent)
              val path = versionstack.map(_._2).mkString(".")
              if (!versionseen.contains(path)) _record_version_(path)
              _append_version_list_(path, trimmed.drop(2).trim)
            } else if (trimmed.contains(":")) {
              val (key, value) = _split_key_value(trimmed)
              versionstack = _drop_stack_(versionstack, indent)
              val path = _path_(versionstack, key)
              if (value.isEmpty) {
                _record_version_(path)
                versionstack :+= indent -> key
              } else {
                _record_version_(path)
                versionvalues = versionvalues.updated(path, _unquote(value))
              }
            }
          } else if (trimmed.startsWith("- ")) {
            rootstack = _drop_stack_(rootstack, indent)
            val path = rootstack.map(_._2).mkString(".")
            if (!rootseen.contains(path)) _record_root_(path)
            _append_list_(path, trimmed.drop(2).trim)
          } else if (trimmed.contains(":")) {
            val (key, value) = _split_key_value(trimmed)
            rootstack = _drop_stack_(rootstack, indent)
            val path = _path_(rootstack, key)
            if (value.isEmpty) {
              _record_root_(path)
              rootstack :+= indent -> key
            } else {
              _record_root_(path)
              values = values.updated(path, _unquote(value))
            }
          }
        }
      }
      _finish_version_()
      Parsed(values, lists, versions, rootseen, versionfields)
    }

    private def _strip_comment(value: String): String = {
      val trimmed = value.trim
      if (trimmed.startsWith("#"))
        ""
      else
        value
    }

    private def _split_key_value(value: String): (String, String) = {
      val idx = value.indexOf(':')
      value.substring(0, idx).trim -> value.substring(idx + 1).trim
    }
  }
}
