package cozy.media

import java.nio.ByteBuffer
import java.nio.charset.{CodingErrorAction, StandardCharsets}
import java.nio.file.{AtomicMoveNotSupportedException, Files, LinkOption, Path, Paths, StandardCopyOption}
import java.security.MessageDigest
import com.fasterxml.jackson.core.{JsonFactory, JsonToken}
import scala.util.control.NonFatal

/*
 * @since   Aug. 27, 2026
 * @version Aug. 27, 2026
 * @author  ASAMI, Tomoharu
 */
private[cozy] object CozyMediaPresentationMigration {
  final case class CommandConfig(legacySlideIr: Path, semanticMap: Path, catalog: Path, save: Path)

  object CommandConfig {
    def create(args: List[String]): CommandConfig = {
      var legacy = Option.empty[Path]
      var semanticmap = Option.empty[Path]
      var catalog = Option.empty[Path]
      var save = Option.empty[Path]
      var rest = args
      while (rest.nonEmpty) {
        rest match {
          case option :: value :: tail if option == "--semantic-map" || option == "--catalog" || option == "--save" =>
            option match {
              case "--semantic-map" =>
                if (semanticmap.nonEmpty) _fail("MIGRATION_COMMAND", "$command.semanticMap", "--semantic-map may appear once")
                semanticmap = Some(_cli_path(value, "$command.semanticMap"))
              case "--catalog" =>
                if (catalog.nonEmpty) _fail("MIGRATION_COMMAND", "$command.catalog", "--catalog may appear once")
                catalog = Some(_cli_path(value, "$command.catalog"))
              case "--save" =>
                if (save.nonEmpty) _fail("MIGRATION_COMMAND", "$command.save", "--save may appear once")
                save = Some(_cli_path(value, "$command.save"))
            }
            rest = tail
          case option :: tail if option.startsWith("--semantic-map=") =>
            if (semanticmap.nonEmpty) _fail("MIGRATION_COMMAND", "$command.semanticMap", "--semantic-map may appear once")
            semanticmap = Some(_cli_path(option.drop("--semantic-map=".length), "$command.semanticMap"))
            rest = tail
          case option :: tail if option.startsWith("--catalog=") =>
            if (catalog.nonEmpty) _fail("MIGRATION_COMMAND", "$command.catalog", "--catalog may appear once")
            catalog = Some(_cli_path(option.drop("--catalog=".length), "$command.catalog"))
            rest = tail
          case option :: tail if option.startsWith("--save=") =>
            if (save.nonEmpty) _fail("MIGRATION_COMMAND", "$command.save", "--save may appear once")
            save = Some(_cli_path(option.drop("--save=".length), "$command.save"))
            rest = tail
          case option :: _ if option.startsWith("--") =>
            _fail("MIGRATION_COMMAND", "$command", s"unsupported or valueless option: $option")
          case value :: tail =>
            if (legacy.nonEmpty) _fail("MIGRATION_COMMAND", "$command.legacySlideIr", "exactly one legacy Slide IR file is required")
            legacy = Some(_cli_path(value, "$command.legacySlideIr"))
            rest = tail
        }
      }
      CommandConfig(
        legacy.getOrElse(_fail("MIGRATION_COMMAND", "$command.legacySlideIr", "missing legacy Slide IR file")),
        semanticmap.getOrElse(_fail("MIGRATION_COMMAND", "$command.semanticMap", "missing --semantic-map")),
        catalog.getOrElse(_fail("MIGRATION_COMMAND", "$command.catalog", "missing --catalog")),
        save.getOrElse(_fail("MIGRATION_COMMAND", "$command.save", "missing --save"))
      )
    }
  }

  final case class MigrationFault(code: String, path: String, reason: String)
    extends IllegalArgumentException(s"$code path=$path reason=$reason")

  private sealed trait MapValue
  private final case class MObject(fields: Vector[(String, MapValue)]) extends MapValue
  private final case class MArray(values: Vector[MapValue]) extends MapValue
  private final case class MString(value: String) extends MapValue
  private final case class MNumber(value: Long) extends MapValue
  private final case class MBoolean(value: Boolean) extends MapValue
  private case object MNull extends MapValue

  private final case class Target(pageId: String, kind: String, id: String)
  private final case class SourceAddress(slideId: String, elementIndex: Int)
  private final case class Binding(source: SourceAddress, target: Target)
  private final case class MigrationMap(legacySlideIrSha256: String, visualPageSet: MapValue, bindings: Vector[Binding])
  private final case class SourceElement(source: SourceAddress, element: CozyMediaSlideIr.Element)

  private val _map_schema = "cozy.visual-page.migration-map.v1"
  private val _set_schema = "cozy.visual-page-set.v1"
  private val _report_schema = "cozy.visual-page.migration-report.v1"
  private val _sha256_pattern = "[0-9a-f]{64}".r
  private val _target_kinds = Set("node-label", "asset-id", "source-path")

  def execute(args: List[String]): String = _migrate(CommandConfig.create(args))

  private def _migrate(config: CommandConfig): String = {
    val legacy = _direct_input(config.legacySlideIr, "legacySlideIr")
    val semanticmap = _semantic_map_input(config.semanticMap)
    val output = _output(config.save)
    val maptext = _read_utf8(semanticmap, "semanticMap")
    val migrationmap = _parse_map(_parse_json(maptext, "$.semanticMap"))
    val legacybytes = try Files.readAllBytes(legacy) catch {
      case NonFatal(e) => _fail("MIGRATION_LEGACY_READ", "legacySlideIr", Option(e.getMessage).getOrElse("cannot read raw legacy Slide IR bytes"))
    }
    val legacydigest = _sha256(legacybytes)
    if (migrationmap.legacySlideIrSha256 != legacydigest)
      _fail("MIGRATION_LEGACY_DIGEST", "$.legacySlideIrSha256", "declared digest does not match raw direct legacy Slide IR bytes")
    val legacydocument = try CozyMediaSlideIr.load(legacy) catch {
      case fault: MigrationFault => throw fault
      case NonFatal(e) => _fail("MIGRATION_LEGACY_SOURCE", "legacySlideIr", Option(e.getMessage).getOrElse("legacy Slide IR is invalid"))
    }
    val sourceroot = try Option(semanticmap.getParent).getOrElse(Paths.get(".").toAbsolutePath.normalize()).toRealPath() catch {
      case NonFatal(e) => _fail("MIGRATION_SOURCE_ROOT", "semanticMap", Option(e.getMessage).getOrElse("semantic-map directory cannot be resolved"))
    }
    val validated = _validate_page_set(migrationmap.visualPageSet, config.catalog, sourceroot)
    val pages = validated.document match {
      case set: CozyVisualPage.PageSet => set.pages
      case _: CozyVisualPage.Page => _fail("MIGRATION_TARGET_PAGE_SET", "$.visualPageSet", "must be a complete Visual Page Set")
    }
    _validate_bindings(legacydocument, migrationmap.bindings, pages)
    _atomic_write(output, validated.canonicalJson + "\n")
    _report(legacydigest, migrationmap.bindings.size, validated)
  }

  private def _validate_page_set(value: MapValue, catalog: Path, sourceroot: Path): CozyVisualPage.ValidatedDocument = {
    val fields = _object(value, "$.visualPageSet")
    val schema = _string(_field(fields, "schema", "$.visualPageSet"), "$.visualPageSet.schema")
    if (schema != _set_schema)
      _fail("MIGRATION_TARGET_PAGE_SET", "$.visualPageSet.schema", s"must be exactly ${_set_schema}")
    try CozyVisualPage.validateEmbeddedPageSet(_canonical(value), catalog, sourceroot) catch {
      case fault: CozyVisualPage.VisualPageFault =>
        _fail("MIGRATION_VISUAL_PAGE", fault.path, s"${fault.code}: ${fault.reason}")
      case NonFatal(e) =>
        _fail("MIGRATION_VISUAL_PAGE", "$.visualPageSet", Option(e.getMessage).getOrElse("Visual Page Set validation failed"))
    }
  }

  private def _validate_bindings(
    legacydocument: CozyMediaSlideIr.Document,
    bindings: Vector[Binding],
    pages: Vector[CozyVisualPage.Page]
  ): Unit = {
    val sourceelements = legacydocument.slides.flatMap { slide =>
      slide.elements.zipWithIndex.map { case (element, index) => SourceElement(SourceAddress(slide.id, index), element) }
    }
    val sources = sourceelements.map(_.source)
    bindings.map(_.source).find(address => bindings.count(_.source == address) > 1).foreach { address =>
      _fail("MIGRATION_BINDING_DUPLICATE", "$.bindings", s"duplicate source binding: ${address.slideId}[${address.elementIndex}]")
    }
    bindings.foreach { binding =>
      if (!sources.contains(binding.source)) {
        if (legacydocument.slides.exists(_.id == binding.source.slideId))
          _fail("MIGRATION_BINDING_RANGE", "$.bindings", s"elementIndex is out of range: ${binding.source.slideId}[${binding.source.elementIndex}]")
        else
          _fail("MIGRATION_BINDING_SOURCE", "$.bindings", s"slideId does not resolve: ${binding.source.slideId}")
      }
    }
    sources.find(source => !bindings.map(_.source).contains(source)).foreach { source =>
      _fail("MIGRATION_BINDING_MISSING", "$.bindings", s"missing source binding: ${source.slideId}[${source.elementIndex}]")
    }
    bindings.map(_.target).find(target => bindings.count(_.target == target) > 1).foreach { target =>
      _fail("MIGRATION_TARGET_DUPLICATE", "$.bindings", s"duplicate target: ${target.pageId}/${target.kind}/${target.id}")
    }
    bindings.foreach { binding =>
      val element = sourceelements.find(_.source == binding.source).getOrElse(
        _fail("MIGRATION_BINDING_SOURCE", "$.bindings", "source binding does not resolve")
      ).element
      val value = _target_value(binding.target, pages)
      element.text match {
        case Some(text) =>
          if (binding.target.kind != "node-label" && binding.target.kind != "source-path")
            _fail("MIGRATION_TARGET_KIND", "$.bindings", s"text source requires node-label or source-path: ${binding.source.slideId}[${binding.source.elementIndex}]")
          if (value != text)
            _fail("MIGRATION_TARGET_VALUE", "$.bindings", s"target value does not match text source: ${binding.source.slideId}[${binding.source.elementIndex}]")
        case None =>
          val asset = element.asset.getOrElse(_fail("MIGRATION_LEGACY_SOURCE", "legacySlideIr", "source element has neither text nor asset"))
          if (binding.target.kind != "asset-id")
            _fail("MIGRATION_TARGET_KIND", "$.bindings", s"asset source requires asset-id: ${binding.source.slideId}[${binding.source.elementIndex}]")
          if (value != asset)
            _fail("MIGRATION_TARGET_VALUE", "$.bindings", s"target value does not match asset source: ${binding.source.slideId}[${binding.source.elementIndex}]")
      }
    }
  }

  private def _target_value(target: Target, pages: Vector[CozyVisualPage.Page]): String = {
    val matches = pages.filter(_.id == target.pageId)
    if (matches.size != 1)
      _fail("MIGRATION_TARGET_PAGE", "$.bindings.target.pageId", s"pageId must resolve exactly once: ${target.pageId}")
    val page = matches.head
    val values = target.kind match {
      case "node-label" => page.logical.nodes.filter(_.label == target.id).map(_.label)
      case "asset-id" => page.assets.filter(_.id == target.id).map(_.id)
      case "source-path" => page.sources.filter(_.path == target.id).map(_.path)
      case _ => _fail("MIGRATION_TARGET_KIND", "$.bindings.target.kind", s"unsupported target kind: ${target.kind}")
    }
    if (values.size != 1)
      _fail("MIGRATION_TARGET_RESOLUTION", "$.bindings.target.id", s"target id must resolve exactly once in ${target.kind}: ${target.id}")
    values.head
  }

  private def _parse_map(value: MapValue): MigrationMap = {
    val fields = _object(value, "$")
    _exact_fields(fields, Vector("schema", "version", "legacySlideIrSha256", "visualPageSet", "bindings"), "$")
    if (_string(_field(fields, "schema", "$"), "$.schema") != _map_schema)
      _fail("MIGRATION_SCHEMA", "$.schema", s"must be exactly ${_map_schema}")
    if (_nonnegative_int(_field(fields, "version", "$"), "$.version") != 1)
      _fail("MIGRATION_VERSION", "$.version", "must be integer 1")
    val digest = _string(_field(fields, "legacySlideIrSha256", "$"), "$.legacySlideIrSha256")
    if (!_sha256_pattern.pattern.matcher(digest).matches)
      _fail("MIGRATION_LEGACY_DIGEST", "$.legacySlideIrSha256", "must be 64 lowercase hexadecimal characters")
    MigrationMap(
      digest,
      _field(fields, "visualPageSet", "$"),
      _array(_field(fields, "bindings", "$"), "$.bindings").zipWithIndex.map { case (item, index) => _parse_binding(item, s"$$.bindings[$index]") }
    )
  }

  private def _parse_binding(value: MapValue, path: String): Binding = {
    val fields = _object(value, path)
    _exact_fields(fields, Vector("slideId", "elementIndex", "target"), path)
    val targetfields = _object(_field(fields, "target", path), s"$path.target")
    _exact_fields(targetfields, Vector("pageId", "kind", "id"), s"$path.target")
    val kind = _required_text(_string(_field(targetfields, "kind", s"$path.target"), s"$path.target.kind"), s"$path.target.kind")
    if (!_target_kinds.contains(kind))
      _fail("MIGRATION_TARGET_KIND", s"$path.target.kind", "must be node-label, asset-id, or source-path")
    Binding(
      SourceAddress(
        _required_text(_string(_field(fields, "slideId", path), s"$path.slideId"), s"$path.slideId"),
        _nonnegative_int(_field(fields, "elementIndex", path), s"$path.elementIndex")
      ),
      Target(
        _required_text(_string(_field(targetfields, "pageId", s"$path.target"), s"$path.target.pageId"), s"$path.target.pageId"),
        kind,
        _required_text(_string(_field(targetfields, "id", s"$path.target"), s"$path.target.id"), s"$path.target.id")
      )
    )
  }

  private def _report(legacydigest: String, bindingcount: Int, validated: CozyVisualPage.ValidatedDocument): String =
    Vector(
      s"schema: ${_report_schema}",
      "version: 1",
      s"legacySlideIrSha256: $legacydigest",
      s"sourceElementCount: $bindingcount",
      s"bindingCount: $bindingcount",
      s"catalogIdentity: ${validated.catalogIdentity}",
      s"visualPageSetIdentity: ${validated.documentIdentity}",
      "status: migrated"
    ).mkString("\n")

  private def _semantic_map_input(path: Path): Path = {
    val input = _direct_input(path, "semanticMap")
    if (_extension(input) != ".json")
      _fail("MIGRATION_SEMANTIC_MAP_FORMAT", "semanticMap", "semantic map must end in .json")
    input
  }

  private def _output(path: Path): Path = {
    val output = Option(path).map(_.toAbsolutePath.normalize()).getOrElse(_fail("MIGRATION_SAVE", "$save", "output path is required"))
    if (_extension(output) != ".json")
      _fail("MIGRATION_OUTPUT_FORMAT", "$save", "output must end in .json")
    output
  }

  private def _direct_input(path: Path, label: String): Path = {
    val input = Option(path).map(_.toAbsolutePath.normalize()).getOrElse(_fail("MIGRATION_INPUT", label, "file path is required"))
    if (Files.isSymbolicLink(input) || !Files.isRegularFile(input, LinkOption.NOFOLLOW_LINKS))
      _fail("MIGRATION_INPUT", label, "must be an existing direct regular non-symlink file")
    input
  }

  private def _read_utf8(path: Path, label: String): String = try {
    StandardCharsets.UTF_8.newDecoder()
      .onMalformedInput(CodingErrorAction.REPORT)
      .onUnmappableCharacter(CodingErrorAction.REPORT)
      .decode(ByteBuffer.wrap(Files.readAllBytes(path)))
      .toString
  } catch {
    case fault: MigrationFault => throw fault
    case NonFatal(e) => _fail("MIGRATION_READ", label, Option(e.getMessage).getOrElse("cannot read UTF-8 file"))
  }

  private def _atomic_write(path: Path, text: String): Unit = {
    val output = path.toAbsolutePath.normalize()
    val parent = Option(output.getParent).getOrElse(_fail("MIGRATION_SAVE", "$save", "output parent is required"))
    if (Files.isSymbolicLink(parent) || !Files.isDirectory(parent, LinkOption.NOFOLLOW_LINKS))
      _fail("MIGRATION_SAVE", "$save", "output parent must be an existing direct directory")
    if (Files.exists(output, LinkOption.NOFOLLOW_LINKS) && Files.isSymbolicLink(output))
      _fail("MIGRATION_SAVE", "$save", "output must not be a symbolic link")
    var temporary = Option.empty[Path]
    try {
      val prefix = Option(output.getFileName).map(_.toString).filter(_.length >= 3).getOrElse("vps")
      val staged = Files.createTempFile(parent, prefix, ".tmp")
      temporary = Some(staged)
      Files.write(staged, text.getBytes(StandardCharsets.UTF_8))
      try Files.move(staged, output, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
      catch {
        case _: AtomicMoveNotSupportedException => _fail("MIGRATION_SAVE_ATOMIC", "$save", "filesystem does not support same-directory atomic output replacement")
      }
      temporary = None
    } catch {
      case fault: MigrationFault => throw fault
      case NonFatal(e) => _fail("MIGRATION_SAVE", "$save", Option(e.getMessage).getOrElse("cannot write output"))
    } finally temporary.foreach(path => Files.deleteIfExists(path))
  }

  private def _parse_json(text: String, source: String): MapValue = {
    val parser = new JsonFactory().createParser(text)
    try {
      val token = parser.nextToken()
      if (token == null) _fail("MIGRATION_JSON", source, "empty JSON input")
      val value = _json_value(parser, source)
      if (parser.nextToken() != null) _fail("MIGRATION_JSON", source, "trailing JSON tokens are not admitted")
      value
    } catch {
      case fault: MigrationFault => throw fault
      case NonFatal(e) => _fail("MIGRATION_JSON", source, Option(e.getMessage).getOrElse("invalid JSON"))
    } finally parser.close()
  }

  private def _json_value(parser: com.fasterxml.jackson.core.JsonParser, source: String): MapValue = parser.getCurrentToken match {
    case JsonToken.START_OBJECT =>
      var fields = Vector.empty[(String, MapValue)]
      var seen = Set.empty[String]
      while (parser.nextToken() != JsonToken.END_OBJECT) {
        if (parser.getCurrentToken != JsonToken.FIELD_NAME) _fail("MIGRATION_JSON", source, "object member name is required")
        val name = parser.getCurrentName
        if (seen.contains(name)) _fail("MIGRATION_DUPLICATE_FIELD", source, s"duplicate JSON field: $name")
        seen += name
        if (parser.nextToken() == null) _fail("MIGRATION_JSON", source, s"missing JSON value for field: $name")
        fields :+= name -> _json_value(parser, source)
      }
      MObject(fields)
    case JsonToken.START_ARRAY =>
      var values = Vector.empty[MapValue]
      while (parser.nextToken() != JsonToken.END_ARRAY) values :+= _json_value(parser, source)
      MArray(values)
    case JsonToken.VALUE_STRING => MString(parser.getText)
    case JsonToken.VALUE_NUMBER_INT =>
      try MNumber(parser.getLongValue) catch { case NonFatal(_) => _fail("MIGRATION_JSON", source, "integer is outside supported range") }
    case JsonToken.VALUE_TRUE => MBoolean(true)
    case JsonToken.VALUE_FALSE => MBoolean(false)
    case JsonToken.VALUE_NULL => MNull
    case JsonToken.VALUE_NUMBER_FLOAT => _fail("MIGRATION_JSON", source, "floating-point values are not admitted")
    case _ => _fail("MIGRATION_JSON", source, s"unsupported JSON token: ${parser.getCurrentToken}")
  }

  private def _object(value: MapValue, path: String): Vector[(String, MapValue)] = value match {
    case MObject(fields) => fields
    case _ => _fail("MIGRATION_TYPE", path, "must be an object")
  }

  private def _array(value: MapValue, path: String): Vector[MapValue] = value match {
    case MArray(values) => values
    case _ => _fail("MIGRATION_TYPE", path, "must be an array")
  }

  private def _string(value: MapValue, path: String): String = value match {
    case MString(text) => text
    case _ => _fail("MIGRATION_TYPE", path, "must be a string")
  }

  private def _nonnegative_int(value: MapValue, path: String): Int = value match {
    case MNumber(number) if number >= 0 && number <= Int.MaxValue => number.toInt
    case MNumber(_) => _fail("MIGRATION_RANGE", path, "must be a nonnegative 32-bit integer")
    case _ => _fail("MIGRATION_TYPE", path, "must be an integer")
  }

  private def _required_text(value: String, path: String): String = {
    if (value == null || value.isEmpty || value != value.trim || value.exists(_.isControl))
      _fail("MIGRATION_TEXT", path, "must be a nonempty trimmed text value without control characters")
    value
  }

  private def _field(fields: Vector[(String, MapValue)], name: String, path: String): MapValue =
    fields.find(_._1 == name).map(_._2).getOrElse(_fail("MIGRATION_FIELD_MISSING", s"$path.$name", "required field is missing"))

  private def _exact_fields(fields: Vector[(String, MapValue)], expected: Vector[String], path: String): Unit = {
    val actual = fields.map(_._1).toSet
    val required = expected.toSet
    if (actual != required) {
      val missing = (required -- actual).toVector.sorted
      val unknown = (actual -- required).toVector.sorted
      val reason = Vector(
        if (missing.nonEmpty) Some("missing=" + missing.mkString(",")) else None,
        if (unknown.nonEmpty) Some("unknown=" + unknown.mkString(",")) else None
      ).flatten.mkString(" ")
      _fail("MIGRATION_FIELDS", path, reason)
    }
  }

  private def _canonical(value: MapValue): String = value match {
    case MObject(fields) => fields.map { case (key, item) => _json_string(key) + ":" + _canonical(item) }.mkString("{", ",", "}")
    case MArray(values) => values.map(_canonical).mkString("[", ",", "]")
    case MString(text) => _json_string(text)
    case MNumber(number) => number.toString
    case MBoolean(flag) => flag.toString
    case MNull => "null"
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

  private def _sha256(bytes: Array[Byte]): String =
    MessageDigest.getInstance("SHA-256").digest(bytes).map(byte => f"${byte & 0xff}%02x").mkString

  private def _cli_path(value: String, path: String): Path = try {
    if (value == null || value.isEmpty || value != value.trim) _fail("MIGRATION_COMMAND", path, "path must be nonempty and trimmed")
    Paths.get(value).toAbsolutePath.normalize()
  } catch {
    case fault: MigrationFault => throw fault
    case NonFatal(_) => _fail("MIGRATION_COMMAND", path, "path is invalid")
  }

  private def _extension(path: Path): String = Option(path).flatMap(item => Option(item.getFileName)).map(_.toString).flatMap { name =>
    val index = name.lastIndexOf('.')
    if (index >= 0) Some(name.substring(index)) else None
  }.getOrElse("")

  private def _fail(code: String, path: String, reason: String): Nothing = throw MigrationFault(code, path, reason)
}
