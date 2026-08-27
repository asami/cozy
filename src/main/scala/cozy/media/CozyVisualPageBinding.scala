package cozy.media

import java.nio.ByteBuffer
import java.nio.charset.{CodingErrorAction, StandardCharsets}
import java.nio.file.{Files, LinkOption, Path}
import java.security.MessageDigest

import com.fasterxml.jackson.core.{JsonFactory, JsonToken}

import scala.util.control.NonFatal

/*
 * @since   Aug. 27, 2026
 * @version Aug. 27, 2026
 * @author  ASAMI, Tomoharu
 */
private[cozy] object CozyVisualPageBinding {
  final case class SlotBinding(semanticSlot: String, physicalSlot: String)
  final case class PatternBinding(visualPattern: String, slots: Vector[SlotBinding])
  final case class Binding(
    id: String,
    profile: String,
    catalog: CozyVisualPage.CatalogReference,
    patterns: Vector[PatternBinding]
  ) {
    def canonicalJson: String = CozyVisualPageBinding.canonicalJson(this)
    def bindingIdentity: String = CozyVisualPageBinding.bindingIdentity(this)
  }

  final case class ValidatedBinding(
    binding: Binding,
    canonicalJson: String,
    bindingIdentity: String
  ) {
    def id: String = binding.id
    def profile: String = binding.profile
    def catalog: CozyVisualPage.CatalogReference = binding.catalog
    def patterns: Vector[PatternBinding] = binding.patterns
  }

  final case class BindingFault(code: String, path: String, reason: String)
    extends IllegalArgumentException(s"$code path=$path reason=$reason")

  private sealed trait Value
  private final case class VObject(fields: Vector[(String, Value)]) extends Value
  private final case class VArray(values: Vector[Value]) extends Value
  private final case class VString(value: String) extends Value
  private final case class VNumber(value: Long) extends Value
  private final case class VBoolean(value: Boolean) extends Value
  private case object VNull extends Value

  private val _schema = "cozy.visual-page.binding.v1"
  private val _profile = "business"
  private val _token_pattern = "[A-Za-z0-9][A-Za-z0-9._-]*".r
  private val _extension = ".json"
  private val _semantic_slots = Vector("knowledge", "nodes", "relations", "assets", "parameters")
  private val _root_fields = Vector("schema", "version", "id", "profile", "catalog", "patterns")

  def load(input: Path, validated: CozyVisualPage.ValidatedDocument): ValidatedBinding = {
    val inputpath = _direct_input(input)
    val inputtext = _read_utf8(inputpath)
    val parsed = _parse_json(inputtext, "$")
    val binding = _parse_binding(parsed, "$", validated)
    val canonical = canonicalJson(binding)
    ValidatedBinding(binding, canonical, bindingIdentity(binding))
  }

  def canonicalJson(binding: Binding): String = _canonical(_binding_value(binding))

  def bindingIdentity(binding: Binding): String = {
    val bytes = canonicalJson(binding).getBytes(StandardCharsets.UTF_8)
    "sha256:" + MessageDigest.getInstance("SHA-256").digest(bytes).map(byte => f"${byte & 0xff}%02x").mkString
  }

  private def _parse_binding(value: Value, path: String, validated: CozyVisualPage.ValidatedDocument): Binding = {
    val fields = _object(value, path)
    _exact_fields(fields, _root_fields, path)
    val schema = _string(_field(fields, "schema", path), s"$path.schema")
    if (schema != _schema) _fail("VISUAL_PAGE_BINDING_SCHEMA", s"$path.schema", s"must be exactly ${_schema}")
    val version = _integer(_field(fields, "version", path), s"$path.version")
    if (version != 1) _fail("VISUAL_PAGE_BINDING_VERSION", s"$path.version", "must be integer 1")
    val id = _token(_string(_field(fields, "id", path), s"$path.id"), s"$path.id", "VISUAL_PAGE_BINDING_ID")
    val profile = _token(_string(_field(fields, "profile", path), s"$path.profile"), s"$path.profile", "VISUAL_PAGE_BINDING_PROFILE")
    if (profile != _profile) _fail("VISUAL_PAGE_BINDING_PROFILE", s"$path.profile", s"must be exactly ${_profile}")

    val catalogpath = s"$path.catalog"
    val catalogfields = _object(_field(fields, "catalog", path), catalogpath)
    _exact_fields(catalogfields, Vector("id", "revision"), catalogpath)
    val catalogid = _token(_string(_field(catalogfields, "id", catalogpath), s"$catalogpath.id"), s"$catalogpath.id", "VISUAL_PAGE_BINDING_CATALOG")
    val catalogrevision = _positive_integer(_field(catalogfields, "revision", catalogpath), s"$catalogpath.revision")
    val expectedcatalog = validated.catalog
    if (catalogid != expectedcatalog.id || catalogrevision != expectedcatalog.revision)
      _fail("VISUAL_PAGE_BINDING_CATALOG", catalogpath, s"must equal resolved catalog {id=${expectedcatalog.id},revision=${expectedcatalog.revision}}")

    val patternvalues = _array(_field(fields, "patterns", path), s"$path.patterns")
    if (patternvalues.isEmpty) _fail("VISUAL_PAGE_BINDING_PATTERNS", s"$path.patterns", "must be nonempty")
    val patterns = patternvalues.zipWithIndex.map { case (item, index) => _parse_pattern(item, s"$path.patterns[$index]") }
    val patternids = patterns.map(_.visualPattern)
    _unique(patternids, s"$path.patterns", "visual pattern")
    val expectedpatterns = expectedcatalog.visualPatterns.map(_.id)
    _unique(expectedpatterns, s"$path.catalog", "resolved visual pattern")
    val unknownpatterns = patternids.filterNot(expectedpatterns.contains)
    if (unknownpatterns.nonEmpty)
      _fail("VISUAL_PAGE_BINDING_PATTERN", s"$path.patterns", s"unknown visual pattern: ${unknownpatterns.head}")
    val missingpatterns = expectedpatterns.filterNot(patternids.contains)
    if (missingpatterns.nonEmpty)
      _fail("VISUAL_PAGE_BINDING_PATTERNS", s"$path.patterns", s"missing visual patterns: ${missingpatterns.mkString(",")}")
    val selectedpatterns = validated.document.pages.map(_.visual.pattern).distinct
    val unboundselected = selectedpatterns.filterNot(patternids.contains)
    if (unboundselected.nonEmpty)
      _fail("VISUAL_PAGE_BINDING_UNBOUND_PATTERN", s"$path.patterns", s"supplied Visual Page uses unbound pattern: ${unboundselected.head}")
    Binding(id, profile, CozyVisualPage.CatalogReference(catalogid, catalogrevision), patterns.sortBy(_.visualPattern))
  }

  private def _parse_pattern(value: Value, path: String): PatternBinding = {
    val fields = _object(value, path)
    _exact_fields(fields, Vector("visualPattern", "slots"), path)
    val visualpattern = _token(_string(_field(fields, "visualPattern", path), s"$path.visualPattern"), s"$path.visualPattern", "VISUAL_PAGE_BINDING_PATTERN")
    val slotpath = s"$path.slots"
    val slotvalues = _array(_field(fields, "slots", path), slotpath)
    if (slotvalues.size != _semantic_slots.size)
      _fail("VISUAL_PAGE_BINDING_SLOTS", slotpath, "must contain exactly five entries")
    val slots = slotvalues.zipWithIndex.map { case (item, index) => _parse_slot(item, s"$slotpath[$index]") }
    val semantics = slots.map(_.semanticSlot)
    _unique(semantics, slotpath, "semantic slot")
    val unknownsemantics = semantics.filterNot(_semantic_slots.contains)
    if (unknownsemantics.nonEmpty)
      _fail("VISUAL_PAGE_BINDING_SEMANTIC_SLOT", slotpath, s"unknown semantic slot: ${unknownsemantics.head}")
    val missingsemantics = _semantic_slots.filterNot(semantics.contains)
    if (missingsemantics.nonEmpty)
      _fail("VISUAL_PAGE_BINDING_SEMANTIC_SLOT", slotpath, s"missing semantic slots: ${missingsemantics.mkString(",")}")
    if (semantics != _semantic_slots)
      _fail("VISUAL_PAGE_BINDING_SLOT_ORDER", slotpath, s"semantic slots must use canonical order: ${_semantic_slots.mkString(",")}")
    val physicalslots = slots.map(_.physicalSlot)
    _unique(physicalslots, slotpath, "physical slot")
    PatternBinding(visualpattern, slots)
  }

  private def _parse_slot(value: Value, path: String): SlotBinding = {
    val fields = _object(value, path)
    _exact_fields(fields, Vector("semanticSlot", "physicalSlot"), path)
    val semantic = _string(_field(fields, "semanticSlot", path), s"$path.semanticSlot")
    if (semantic.isEmpty || semantic != semantic.trim)
      _fail("VISUAL_PAGE_BINDING_SEMANTIC_SLOT", s"$path.semanticSlot", "must be a nonempty canonical semantic slot")
    val physical = _token(
      _string(_field(fields, "physicalSlot", path), s"$path.physicalSlot"),
      s"$path.physicalSlot",
      "VISUAL_PAGE_BINDING_PHYSICAL_SLOT"
    )
    SlotBinding(semantic, physical)
  }

  private def _binding_value(binding: Binding): Value = VObject(Vector(
    "schema" -> VString(_schema),
    "version" -> VNumber(1),
    "id" -> VString(binding.id),
    "profile" -> VString(binding.profile),
    "catalog" -> VObject(Vector("id" -> VString(binding.catalog.id), "revision" -> VNumber(binding.catalog.revision))),
    "patterns" -> VArray(binding.patterns.sortBy(_.visualPattern).map { pattern =>
      VObject(Vector(
        "visualPattern" -> VString(pattern.visualPattern),
        "slots" -> VArray(pattern.slots.map { slot =>
          VObject(Vector("semanticSlot" -> VString(slot.semanticSlot), "physicalSlot" -> VString(slot.physicalSlot)))
        })
      ))
    })
  ))

  private def _parse_json(text: String, source: String): Value = {
    val parser = new JsonFactory().createParser(text)
    try {
      val token = parser.nextToken()
      if (token == null) _fail("VISUAL_PAGE_BINDING_JSON", source, "empty JSON input")
      val value = _json_value(parser, source)
      if (parser.nextToken() != null) _fail("VISUAL_PAGE_BINDING_JSON", source, "trailing JSON tokens are not admitted")
      value
    } catch {
      case fault: BindingFault => throw fault
      case NonFatal(error) => _fail("VISUAL_PAGE_BINDING_JSON", source, Option(error.getMessage).getOrElse("invalid JSON"))
    } finally parser.close()
  }

  private def _json_value(parser: com.fasterxml.jackson.core.JsonParser, source: String): Value = parser.getCurrentToken match {
    case JsonToken.START_OBJECT =>
      var fields = Vector.empty[(String, Value)]
      var seen = Set.empty[String]
      var token = parser.nextToken()
      while (token != JsonToken.END_OBJECT) {
        if (token == null || token != JsonToken.FIELD_NAME)
          _fail("VISUAL_PAGE_BINDING_JSON", source, "object member name is required")
        val name = parser.getCurrentName
        if (seen.contains(name)) _fail("VISUAL_PAGE_BINDING_DUPLICATE_FIELD", source, s"duplicate JSON field: $name")
        seen += name
        if (parser.nextToken() == null) _fail("VISUAL_PAGE_BINDING_JSON", source, s"missing JSON value for field: $name")
        fields :+= name -> _json_value(parser, source)
        token = parser.nextToken()
      }
      VObject(fields)
    case JsonToken.START_ARRAY =>
      var values = Vector.empty[Value]
      var token = parser.nextToken()
      while (token != JsonToken.END_ARRAY) {
        if (token == null) _fail("VISUAL_PAGE_BINDING_JSON", source, "unterminated JSON array")
        values :+= _json_value(parser, source)
        token = parser.nextToken()
      }
      VArray(values)
    case JsonToken.VALUE_STRING => VString(parser.getText)
    case JsonToken.VALUE_NUMBER_INT =>
      try VNumber(parser.getLongValue)
      catch { case NonFatal(_) => _fail("VISUAL_PAGE_BINDING_JSON", source, "integer is outside supported range") }
    case JsonToken.VALUE_TRUE => VBoolean(true)
    case JsonToken.VALUE_FALSE => VBoolean(false)
    case JsonToken.VALUE_NULL => VNull
    case JsonToken.VALUE_NUMBER_FLOAT => _fail("VISUAL_PAGE_BINDING_JSON", source, "floating-point values are not admitted")
    case _ => _fail("VISUAL_PAGE_BINDING_JSON", source, s"unsupported JSON token: ${parser.getCurrentToken}")
  }

  private def _object(value: Value, path: String): Vector[(String, Value)] = value match {
    case VObject(fields) => fields
    case _ => _fail("VISUAL_PAGE_BINDING_TYPE", path, "must be an object")
  }

  private def _array(value: Value, path: String): Vector[Value] = value match {
    case VArray(values) => values
    case _ => _fail("VISUAL_PAGE_BINDING_TYPE", path, "must be an array")
  }

  private def _string(value: Value, path: String): String = value match {
    case VString(item) => item
    case _ => _fail("VISUAL_PAGE_BINDING_TYPE", path, "must be a string")
  }

  private def _integer(value: Value, path: String): Int = value match {
    case VNumber(number) if number >= Int.MinValue && number <= Int.MaxValue => number.toInt
    case VNumber(_) => _fail("VISUAL_PAGE_BINDING_RANGE", path, "must be a 32-bit integer")
    case _ => _fail("VISUAL_PAGE_BINDING_TYPE", path, "must be an integer")
  }

  private def _positive_integer(value: Value, path: String): Int = {
    val number = _integer(value, path)
    if (number <= 0) _fail("VISUAL_PAGE_BINDING_CATALOG", path, "must be a positive integer")
    number
  }

  private def _field(fields: Vector[(String, Value)], name: String, path: String): Value =
    fields.find(_._1 == name).map(_._2).getOrElse(_fail("VISUAL_PAGE_BINDING_FIELD_MISSING", s"$path.$name", "required field is missing"))

  private def _exact_fields(fields: Vector[(String, Value)], expected: Vector[String], path: String): Unit = {
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
      _fail("VISUAL_PAGE_BINDING_FIELDS", path, reason)
    }
  }

  private def _token(value: String, path: String, code: String): String = {
    if (value == null || value.isEmpty || value != value.trim || !_token_pattern.pattern.matcher(value).matches)
      _fail(code, path, "must be a nonempty trimmed canonical identity token")
    value
  }

  private def _unique(values: Vector[String], path: String, label: String): Unit =
    values.groupBy(identity).collectFirst { case (value, duplicates) if duplicates.size > 1 => value }.foreach { value =>
      _fail("VISUAL_PAGE_BINDING_DUPLICATE", path, s"duplicate $label: $value")
    }

  private def _direct_input(path: Path): Path = {
    if (path == null) _fail("VISUAL_PAGE_BINDING_INPUT", "input", "file path is required")
    val normalized = path.toAbsolutePath.normalize()
    if (_extension != Option(normalized.getFileName).map(_.toString).flatMap { name =>
      val index = name.lastIndexOf('.')
      if (index >= 0) Some(name.substring(index)) else None
    }.getOrElse(""))
      _fail("VISUAL_PAGE_BINDING_FORMAT", "input", "input must end in .json")
    if (Files.isSymbolicLink(normalized))
      _fail("VISUAL_PAGE_BINDING_INPUT", "input", "input must not be a symbolic link")
    if (!Files.isRegularFile(normalized, LinkOption.NOFOLLOW_LINKS))
      _fail("VISUAL_PAGE_BINDING_INPUT", "input", "input must be an existing direct regular non-symlink file")
    normalized
  }

  private def _read_utf8(path: Path): String = try {
    StandardCharsets.UTF_8.newDecoder()
      .onMalformedInput(CodingErrorAction.REPORT)
      .onUnmappableCharacter(CodingErrorAction.REPORT)
      .decode(ByteBuffer.wrap(Files.readAllBytes(path)))
      .toString
  } catch {
    case fault: BindingFault => throw fault
    case NonFatal(error) => _fail("VISUAL_PAGE_BINDING_READ", "input", Option(error.getMessage).getOrElse("cannot read UTF-8 file"))
  }

  private def _canonical(value: Value): String = value match {
    case VObject(fields) => fields.map { case (key, item) => _json_string(key) + ":" + _canonical(item) }.mkString("{", ",", "}")
    case VArray(values) => values.map(_canonical).mkString("[", ",", "]")
    case VString(item) => _json_string(item)
    case VNumber(item) => item.toString
    case VBoolean(item) => item.toString
    case VNull => "null"
  }

  private def _json_string(value: String): String = {
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

  private def _fail(code: String, path: String, reason: String): Nothing = throw BindingFault(code, path, reason)
}
