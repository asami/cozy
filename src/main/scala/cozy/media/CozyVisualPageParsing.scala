package cozy.media

import cozy.media.CozyVisualPage._
import java.nio.ByteBuffer
import java.nio.charset.{CodingErrorAction, StandardCharsets}
import java.nio.file.{AtomicMoveNotSupportedException, Files, LinkOption, Path, Paths, StandardCopyOption}
import java.security.MessageDigest
import scala.util.control.NonFatal

/*
 * @since   Sep. 2, 2026
 * @version Sep. 2, 2026
 * @author  ASAMI, Tomoharu
 */
private[cozy] trait CozyVisualPageParsing {
  private val _token_pattern = "[A-Za-z0-9][A-Za-z0-9._-]*".r

  private def _fail(code: String, path: String, reason: String): Nothing =
    CozyVisualPage._fail(code, path, reason)

  private[cozy] final def _page_value(page: Page): Value = VObject(Vector(
    "schema" -> VString("cozy.visual-page.v1"),
    "version" -> VNumber(1),
    "id" -> VString(page.id),
    "knowledge" -> VString(page.knowledge),
    "language" -> VString(page.language),
    "catalog" -> VObject(Vector("id" -> VString(page.catalog.id), "revision" -> VNumber(page.catalog.revision))),
    "logical" -> _logical_value(page.logical),
    "visual" -> _visual_value(page.visual),
    "assets" -> VArray(page.assets.map(_asset_value)),
    "sources" -> VArray(page.sources.map(_source_value))
  ))

  private[cozy] final def _set_value(set: PageSet): Value = VObject(Vector(
    "schema" -> VString("cozy.visual-page-set.v1"),
    "version" -> VNumber(1),
    "id" -> VString(set.id),
    "pages" -> VArray(set.pages.map(_page_value))
  ))

  private[cozy] final def _document_value(document: Document): Value = document match {
    case page: Page => _page_value(page)
    case set: PageSet => _set_value(set)
  }

  private[cozy] final def _logical_value(logical: Logical): Value = VObject(Vector(
    "pattern" -> VString(logical.pattern),
    "nodes" -> VArray(logical.nodes.map(node => VObject(Vector(
      "id" -> VString(node.id), "role" -> VString(node.role), "label" -> VString(node.label), "sourceRefs" -> VArray(node.sourceRefs.map(VString))
    )))),
    "relations" -> VArray(logical.relations.map(relation => VObject(Vector(
      "id" -> VString(relation.id), "type" -> VString(relation.relationType), "from" -> VString(relation.from), "to" -> VString(relation.to), "sourceRefs" -> VArray(relation.sourceRefs.map(VString))
    )) )
  )))

  private[cozy] final def _visual_value(visual: Visual): Value = VObject(Vector(
    "pattern" -> VString(visual.pattern),
    "parameters" -> VObject(visual.parameters.sortBy(_.name).map(parameter => parameter.name -> _parameter_value(parameter.value)))
  ))

  private[cozy] final def _parameter_value(value: ParameterValue): Value = value match {
    case NodeReference(item) => VString(item)
    case BooleanParameter(item) => VBoolean(item)
    case StringParameter(item) => VString(item)
  }

  private[cozy] final def _asset_value(asset: Asset): Value = VObject(Vector(
    "id" -> VString(asset.id), "path" -> VString(asset.path), "mediaType" -> VString(asset.mediaType), "sha256" -> VString(asset.sha256)
  ))

  private[cozy] final def _source_value(source: SourceBinding): Value =
    VObject(Vector("id" -> VString(source.id), "path" -> VString(source.path)))

  private[cozy] final def _catalog_value(catalog: Catalog): Value = VObject(Vector(
    "schema" -> VString("cozy.presentation-semantics.catalog.v1"),
    "version" -> VNumber(1),
    "id" -> VString(catalog.id),
    "revision" -> VNumber(catalog.revision),
    "relations" -> VArray(catalog.relations.map(item => VObject(Vector("id" -> VString(item.id), "direction" -> VString(item.direction))))),
    "logicalPatterns" -> VArray(catalog.logicalPatterns.map(pattern => VObject(Vector(
      "id" -> VString(pattern.id),
      "nodeRoles" -> VArray(pattern.nodeRoles.map(role => VObject(Vector("role" -> VString(role.role), "min" -> VNumber(role.min), "max" -> VNumber(role.max))))),
      "relationRules" -> VArray(pattern.relationRules.map(rule => VObject(Vector(
        "relation" -> VString(rule.relation), "fromRoles" -> VArray(rule.fromRoles.map(VString)), "toRoles" -> VArray(rule.toRoles.map(VString)),
        "min" -> VNumber(rule.min), "max" -> VNumber(rule.max), "topology" -> VString(rule.topology)
      ))))
    )))),
    "visualPatterns" -> VArray(catalog.visualPatterns.map(pattern => VObject(Vector(
      "id" -> VString(pattern.id), "compatibleLogicalPatterns" -> VArray(pattern.compatibleLogicalPatterns.map(VString)),
      "parameters" -> VArray(pattern.parameters.map(parameter => VObject(Vector(
        "name" -> VString(parameter.name), "type" -> VString(parameter.parameterType), "required" -> VBoolean(parameter.required)
      ))))
    ))))
  ))

  private[cozy] final def _canonical(value: Value): String = value match {
    case VObject(fields) => fields.map { case (key, item) => _json_string(key) + ":" + _canonical(item) }.mkString("{", ",", "}")
    case VArray(values) => values.map(_canonical).mkString("[", ",", "]")
    case VString(item) => _json_string(item)
    case VNumber(item) => item.toString
    case VBoolean(item) => item.toString
    case VNull => "null"
  }

  private[cozy] final def _json_string(value: String): String = {
    val escaped = value.flatMap {
      case '"' => "\\\""
      case '\\' => "\\\\"
      case '\b' => "\\b"
      case '\f' => "\\f"
      case '\n' => "\\n"
      case '\r' => "\\r"
      case '\t' => "\\t"
      case character if character < ' ' => f"\\u${character.toInt}%04x"
      case character => character.toString
    }
    "\"" + escaped + "\""
  }

  private[cozy] final def _identity(value: Value): String =
    "sha256:" + MessageDigest.getInstance("SHA-256").digest(_canonical(value).getBytes(StandardCharsets.UTF_8)).map(byte => f"${byte & 0xff}%02x").mkString

  private[cozy] final def _sha256(path: Path): String =
    MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path)).map(byte => f"${byte & 0xff}%02x").mkString

  private[cozy] final def _markdown_line(key: String, value: Value): String = s"$key: ${_canonical(value)}"
  private[cozy] final def _parameter_text(value: ParameterValue): String = _canonical(_parameter_value(value))
  private[cozy] final def _normalize_newlines(value: String): String = Option(value).getOrElse("").replace("\r\n", "\n").replace('\r', '\n')

  private[cozy] final def _safe_direct_file(root: Path, value: String, path: String): Path = {
    if (value.isEmpty || value != value.trim || value.exists(_.isControl) || value.contains('\\') || value.startsWith("/") || value.matches("^[A-Za-z][A-Za-z0-9+.-]*:.*"))
      _fail("VISUAL_PAGE_PATH", path, "must be a safe relative POSIX path")
    val segments = value.split("/", -1).toVector
    if (segments.exists(segment => segment.isEmpty || segment == "." || segment == ".."))
      _fail("VISUAL_PAGE_PATH", path, "must not contain empty, dot, or traversal segments")
    val documentroot = try root.toRealPath() catch {
      case NonFatal(_) => _fail("VISUAL_PAGE_PATH_ROOT", path, "input document root cannot be resolved")
    }
    val resolved = documentroot.resolve(value).normalize()
    if (!resolved.startsWith(documentroot)) _fail("VISUAL_PAGE_PATH", path, "path escapes input document root")
    var current = documentroot
    segments.foreach { segment =>
      current = current.resolve(segment)
      if (Files.isSymbolicLink(current)) _fail("VISUAL_PAGE_PATH_SYMLINK", path, "path traverses a symbolic link")
    }
    if (!Files.isRegularFile(resolved, LinkOption.NOFOLLOW_LINKS)) _fail("VISUAL_PAGE_PATH_FILE", path, "must resolve to an existing direct regular file")
    resolved
  }

  private[cozy] final def _direct_input(path: Path, label: String): Path = {
    if (path == null) _fail("VISUAL_PAGE_INPUT", label, "file path is required")
    val normalized = path.toAbsolutePath.normalize()
    if (!Files.isRegularFile(normalized, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(normalized))
      _fail("VISUAL_PAGE_INPUT", label, "must be an existing direct regular non-symlink file")
    normalized
  }

  private[cozy] final def _read_utf8(path: Path, label: String): String = try {
    StandardCharsets.UTF_8.newDecoder()
      .onMalformedInput(CodingErrorAction.REPORT)
      .onUnmappableCharacter(CodingErrorAction.REPORT)
      .decode(ByteBuffer.wrap(Files.readAllBytes(path)))
      .toString
  } catch {
    case NonFatal(e) => _fail("VISUAL_PAGE_READ", label, Option(e.getMessage).getOrElse("cannot read UTF-8 file"))
  }

  private[cozy] final def _atomic_write(path: Path, text: String): Unit = {
    val output = path.toAbsolutePath.normalize()
    val parent = Option(output.getParent).getOrElse(_fail("VISUAL_PAGE_SAVE", "$save", "output parent is required"))
    if (!Files.isDirectory(parent, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(parent))
      _fail("VISUAL_PAGE_SAVE", "$save", "output parent must be an existing direct directory")
    if (Files.exists(output, LinkOption.NOFOLLOW_LINKS) && Files.isSymbolicLink(output))
      _fail("VISUAL_PAGE_SAVE", "$save", "output must not be a symbolic link")
    var temporary = Option.empty[Path]
    try {
      val prefix = Option(output.getFileName).map(_.toString).filter(_.length >= 3).getOrElse("vpg")
      val staged = Files.createTempFile(parent, prefix, ".tmp")
      temporary = Some(staged)
      Files.write(staged, text.getBytes(StandardCharsets.UTF_8))
      try Files.move(staged, output, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
      catch {
        case _: AtomicMoveNotSupportedException => _fail("VISUAL_PAGE_SAVE_ATOMIC", "$save", "filesystem does not support same-directory atomic output replacement")
      }
      temporary = None
    } catch {
      case fault: VisualPageFault => throw fault
      case NonFatal(e) => _fail("VISUAL_PAGE_SAVE", "$save", Option(e.getMessage).getOrElse("cannot write output"))
    } finally temporary.foreach(path => Files.deleteIfExists(path))
  }

  private[cozy] final def _cli_path(value: String, path: String): Path = try {
    if (value == null || value.isEmpty || value != value.trim) _fail("VISUAL_PAGE_COMMAND", path, "path must be nonempty and trimmed")
    Paths.get(value).toAbsolutePath.normalize()
  } catch {
    case fault: VisualPageFault => throw fault
    case NonFatal(_) => _fail("VISUAL_PAGE_COMMAND", path, "path is invalid")
  }

  private[cozy] final def _extension(path: Path): String = Option(path).flatMap(item => Option(item.getFileName)).map(_.toString).flatMap { name =>
    val index = name.lastIndexOf('.')
    if (index >= 0) Some(name.substring(index)) else None
  }.getOrElse("")

  private[cozy] final def _object(value: Value, path: String): Vector[(String, Value)] = value match {
    case VObject(fields) => fields
    case _ => _fail("VISUAL_PAGE_TYPE", path, "must be an object")
  }

  private[cozy] final def _array(value: Value, path: String): Vector[Value] = value match {
    case VArray(values) => values
    case _ => _fail("VISUAL_PAGE_TYPE", path, "must be an array")
  }

  private[cozy] final def _string(value: Value, path: String): String = value match {
    case VString(item) => item
    case _ => _fail("VISUAL_PAGE_TYPE", path, "must be a string")
  }

  private[cozy] final def _boolean(value: Value, path: String): Boolean = value match {
    case VBoolean(item) => item
    case _ => _fail("VISUAL_PAGE_TYPE", path, "must be a boolean")
  }

  private[cozy] final def _positive_int(value: Value, path: String): Int = {
    val number = _positive_or_zero_int(value, path)
    if (number <= 0) _fail("VISUAL_PAGE_RANGE", path, "must be a positive integer")
    number
  }

  private[cozy] final def _positive_or_zero_int(value: Value, path: String): Int = value match {
    case VNumber(number) if number >= 0 && number <= Int.MaxValue => number.toInt
    case VNumber(_) => _fail("VISUAL_PAGE_RANGE", path, "must be a nonnegative 32-bit integer")
    case _ => _fail("VISUAL_PAGE_TYPE", path, "must be an integer")
  }

  private[cozy] final def _string_array(value: Value, path: String): Vector[String] = {
    val items = _array(value, path).zipWithIndex.map { case (item, index) => _stable_id(_string(item, s"$path[$index]"), s"$path[$index]") }
    _unique(items, path, "array value")
    items
  }

  private[cozy] final def _field(fields: Vector[(String, Value)], name: String, path: String): Value =
    fields.find(_._1 == name).map(_._2).getOrElse(_fail("VISUAL_PAGE_FIELD_MISSING", s"$path.$name", "required field is missing"))

  private[cozy] final def _exact_fields(fields: Vector[(String, Value)], expected: Vector[String], path: String): Unit = {
    _unique(fields.map(_._1), path, "field")
    val actual = fields.map(_._1).toSet
    val required = expected.toSet
    if (actual != required) {
      val missing = (required -- actual).toVector.sorted
      val unknown = (actual -- required).toVector.sorted
      val reason = Vector(
        if (missing.nonEmpty) Some("missing=" + missing.mkString(",")) else None,
        if (unknown.nonEmpty) Some("unknown=" + unknown.mkString(",")) else None
      ).flatten.mkString(" ")
      _fail("VISUAL_PAGE_FIELDS", path, reason)
    }
  }

  private[cozy] final def _schema_version(fields: Vector[(String, Value)], schema: String, path: String): Unit = {
    if (_string(_field(fields, "schema", path), s"$path.schema") != schema)
      _fail("VISUAL_PAGE_SCHEMA", s"$path.schema", s"must be exactly $schema")
    if (_positive_or_zero_int(_field(fields, "version", path), s"$path.version") != 1)
      _fail("VISUAL_PAGE_VERSION", s"$path.version", "must be integer 1")
  }

  private[cozy] final def _stable_id(value: String, path: String): String = {
    if (value == null || value != value.trim || !_token_pattern.pattern.matcher(value).matches)
      _fail("VISUAL_PAGE_ID", path, "must be a nonempty trimmed stable token")
    value
  }

  private[cozy] final def _required_text(value: String, path: String): String = {
    if (value == null || value.isEmpty || value != value.trim || value.exists(_.isControl))
      _fail("VISUAL_PAGE_TEXT", path, "must be a nonempty trimmed text value without control characters")
    value
  }

  private[cozy] final def _unique(values: Vector[String], path: String, label: String): Unit =
    values.groupBy(identity).collectFirst { case (value, duplicates) if duplicates.size > 1 => value }.foreach { value =>
      _fail("VISUAL_PAGE_DUPLICATE", path, s"duplicate $label: $value")
    }
}
