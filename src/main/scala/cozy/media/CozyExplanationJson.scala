package cozy.media

import CozyExplanation._

import java.nio.ByteBuffer
import java.nio.charset.{CodingErrorAction, StandardCharsets}
import java.nio.file.{AtomicMoveNotSupportedException, Files, LinkOption, Path, Paths, StandardCopyOption}
import java.security.MessageDigest

import com.fasterxml.jackson.core.{JsonFactory, JsonToken}

import scala.collection.mutable
import scala.util.control.NonFatal

/*
 * @since   Aug. 28, 2026
 * @version Aug. 28, 2026
 * @author  ASAMI, Tomoharu
 */
private[media] object CozyExplanationJson {
  private[media] def _parse_json(text: String, source: String): JsonValue = {
    if (text == null || text.isEmpty) _fail("EXPLANATION_SCHEMA_INVALID", source, "JSON input is required")
    val parser = new JsonFactory().createParser(text)
    try {
      if (parser.nextToken() == null) _fail("EXPLANATION_SCHEMA_INVALID", source, "JSON input is required")
      val value = _parse_json_value(parser, source)
      if (parser.nextToken() != null) _fail("EXPLANATION_SCHEMA_INVALID", source, "trailing JSON tokens are not admitted")
      value
    } catch {
      case fault: ExplanationFault => throw fault
      case NonFatal(e) => _fail("EXPLANATION_SCHEMA_INVALID", source, Option(e.getMessage).getOrElse("invalid JSON"))
    } finally parser.close()
  }

  private[media] def _parse_json_value(parser: com.fasterxml.jackson.core.JsonParser, source: String): JsonValue = parser.getCurrentToken match {
    case JsonToken.START_OBJECT =>
      val fields = Vector.newBuilder[(String, JsonValue)]
      val seen = mutable.Set.empty[String]
      while (parser.nextToken() != JsonToken.END_OBJECT) {
        if (parser.getCurrentToken != JsonToken.FIELD_NAME) _fail("EXPLANATION_SCHEMA_INVALID", source, "JSON object member name is required")
        val name = parser.getCurrentName
        if (seen.contains(name)) _fail("EXPLANATION_DUPLICATE_FIELD", source, s"duplicate JSON field: $name")
        seen += name
        if (parser.nextToken() == null) _fail("EXPLANATION_SCHEMA_INVALID", source, s"missing JSON value for field: $name")
        fields += name -> _parse_json_value(parser, source)
      }
      JsonObject(fields.result())
    case JsonToken.START_ARRAY =>
      val values = Vector.newBuilder[JsonValue]
      while (parser.nextToken() != JsonToken.END_ARRAY) values += _parse_json_value(parser, source)
      JsonArray(values.result())
    case JsonToken.VALUE_STRING => JsonString(parser.getText)
    case JsonToken.VALUE_TRUE => JsonBoolean(true)
    case JsonToken.VALUE_FALSE => JsonBoolean(false)
    case JsonToken.VALUE_NUMBER_INT =>
      try JsonNumber(parser.getLongValue) catch { case NonFatal(_) => _fail("EXPLANATION_SCHEMA_INVALID", source, "integer is outside supported range") }
    case JsonToken.VALUE_NUMBER_FLOAT => _fail("EXPLANATION_SCHEMA_INVALID", source, "non-finite or floating-point JSON numbers are not admitted")
    case JsonToken.VALUE_NULL => _fail("EXPLANATION_SCHEMA_INVALID", source, "JSON null is not admitted")
    case _ => _fail("EXPLANATION_SCHEMA_INVALID", source, s"unsupported JSON token: ${parser.getCurrentToken}")
  }

  private[media] def _canonical(value: JsonValue): String = value match {
    case JsonObject(fields) => fields.map { case (name, member) => _quote(name) + ":" + _canonical(member) }.mkString("{", ",", "}")
    case JsonArray(values) => values.map(_canonical).mkString("[", ",", "]")
    case JsonString(text) => _quote(text)
    case JsonNumber(number) => number.toString
    case JsonBoolean(value) => value.toString
  }

  private[media] def _quote(value: String): String = {
    val escaped = Option(value).getOrElse("").flatMap {
      case '"' => "\\\""
      case '\\' => "\\\\"
      case '\b' => "\\b"
      case '\f' => "\\f"
      case '\n' => "\\n"
      case '\r' => "\\r"
      case '\t' => "\\t"
      case character if character < ' ' => "\\u%04x".format(character.toInt)
      case character => character.toString
    }
    "\"" + escaped + "\""
  }

  private[media] def _identity(value: JsonValue): String = "sha256:" + _sha256_bytes(_canonical(value).getBytes(StandardCharsets.UTF_8))

  private[media] def _sha256_file(path: Path): String = _sha256_bytes(Files.readAllBytes(path))

  private[media] def _sha256_bytes(bytes: Array[Byte]): String =
    MessageDigest.getInstance("SHA-256").digest(bytes).map(value => f"${value & 0xff}%02x").mkString

  private[media] def _object(value: JsonValue, path: String): Vector[(String, JsonValue)] = value match {
    case JsonObject(fields) => fields
    case _ => _fail("EXPLANATION_SCHEMA_INVALID", path, "must be a JSON object")
  }

  private[media] def _array(value: JsonValue, path: String): Vector[JsonValue] = value match {
    case JsonArray(values) => values
    case _ => _fail("EXPLANATION_SCHEMA_INVALID", path, "must be a JSON array")
  }

  private[media] def _nonempty_array(value: JsonValue, path: String): Vector[JsonValue] = {
    val values = _array(value, path)
    if (values.isEmpty) _fail("EXPLANATION_FACT_INVALID", path, "must be a nonempty ordered array")
    values
  }

  private[media] def _string(value: JsonValue, path: String): String = value match {
    case JsonString(text) => text
    case _ => _fail("EXPLANATION_SCHEMA_INVALID", path, "must be a JSON string")
  }

  private[media] def _boolean(value: JsonValue, path: String): Boolean = value match {
    case JsonBoolean(result) => result
    case _ => _fail("EXPLANATION_SCHEMA_INVALID", path, "must be a JSON boolean")
  }

  private[media] def _positive_int(value: JsonValue, path: String): Int = value match {
    case JsonNumber(number) if number > 0 && number <= Int.MaxValue => number.toInt
    case JsonNumber(_) => _fail("EXPLANATION_SCHEMA_INVALID", path, "must be a positive 32-bit integer")
    case _ => _fail("EXPLANATION_SCHEMA_INVALID", path, "must be a JSON integer")
  }

  private[media] def _nonnegative_int(value: JsonValue, path: String): Int = value match {
    case JsonNumber(number) if number >= 0 && number <= Int.MaxValue => number.toInt
    case JsonNumber(_) => _fail("EXPLANATION_SCHEMA_INVALID", path, "must be a nonnegative 32-bit integer")
    case _ => _fail("EXPLANATION_SCHEMA_INVALID", path, "must be a JSON integer")
  }

  private[media] def _field(fields: Vector[(String, JsonValue)], name: String, path: String): JsonValue =
    fields.find(_._1 == name).map(_._2).getOrElse(_fail("EXPLANATION_SCHEMA_INVALID", s"$path.$name", "required field is missing"))

  private[media] def _exact_fields(fields: Vector[(String, JsonValue)], expected: Vector[String], path: String): Unit = {
    val actual = fields.map(_._1)
    actual.find(name => !expected.contains(name)).foreach(name => _fail("EXPLANATION_UNKNOWN_FIELD", s"$path.$name", "unknown field is not admitted"))
    expected.find(name => !actual.contains(name)).foreach(name => _fail("EXPLANATION_SCHEMA_INVALID", s"$path.$name", "required field is missing"))
  }

  private[media] def _schema_version(fields: Vector[(String, JsonValue)], schema: String, path: String): Unit = {
    if (_string(_field(fields, "schema", path), s"$path.schema") != schema)
      _fail("EXPLANATION_SCHEMA_INVALID", s"$path.schema", s"must be exactly $schema")
    _field(fields, "version", path) match {
      case JsonNumber(1) => ()
      case _ => _fail("EXPLANATION_SCHEMA_INVALID", s"$path.version", "must be integer 1")
    }
  }

  private[media] def _token(value: String, path: String): String = {
    if (value == null || !_token_pattern.pattern.matcher(value).matches)
      _fail("EXPLANATION_SCHEMA_INVALID", path, "must be a nonempty stable token")
    value
  }

  private[media] def _text(value: String, path: String): String = {
    if (value == null || value.isEmpty || value != value.trim || value.exists(_.isControl))
      _fail("EXPLANATION_SCHEMA_INVALID", path, "must be nonempty trimmed text without control characters")
    value
  }

  private[media] def _sha256_text(value: String, path: String): String = {
    if (value == null || !_sha256_pattern.pattern.matcher(value).matches)
      _fail("EXPLANATION_SCHEMA_INVALID", path, "must be 64 lowercase hexadecimal SHA-256 characters")
    value
  }

  private[media] def _identity_text(value: String, path: String): String = {
    if (value == null || !_identity_pattern.pattern.matcher(value).matches)
      _fail("EXPLANATION_SCHEMA_INVALID", path, "must be sha256:<64-lowercase-hex>")
    value
  }

  private[media] def _references(value: JsonValue, path: String): Vector[String] = {
    val references = _array(value, path).zipWithIndex.map { case (item, index) => _token(_string(item, s"$path[$index]"), s"$path[$index]") }
    _unique(references, path, "reference")
    references
  }

  private[media] def _unique(values: Vector[String], path: String, label: String): Unit =
    values.groupBy(identity).collectFirst { case (value, duplicates) if duplicates.size > 1 => value }.foreach { value =>
      _fail("EXPLANATION_SCHEMA_INVALID", path, s"duplicate $label: $value")
    }

  private[media] def _direct_input(path: Path, label: String): Path = {
    if (path == null) _fail("EXPLANATION_REFERENCE_INVALID", label, "direct file path is required")
    val normalized = try path.toAbsolutePath.normalize() catch { case NonFatal(_) => _fail("EXPLANATION_REFERENCE_INVALID", label, "direct file path is invalid") }
    if (!Files.isRegularFile(normalized, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(normalized))
      _fail("EXPLANATION_REFERENCE_INVALID", label, "must be an existing direct regular non-symlink file")
    normalized
  }

  private[media] def _read_utf8(path: Path, label: String): String = try {
    val decoder = StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT)
    decoder.decode(ByteBuffer.wrap(Files.readAllBytes(path))).toString
  } catch {
    case fault: ExplanationFault => throw fault
    case NonFatal(e) => _fail("EXPLANATION_SCHEMA_INVALID", label, Option(e.getMessage).getOrElse("cannot read UTF-8 file"))
  }

  private[media] def _atomic_write(path: Path, text: String): Unit = {
    val output = try path.toAbsolutePath.normalize() catch { case NonFatal(_) => _fail("EXPLANATION_COMMAND_INVALID", "$command.save", "output path is invalid") }
    val parent = Option(output.getParent).getOrElse(_fail("EXPLANATION_COMMAND_INVALID", "$command.save", "output parent is required"))
    if (!Files.isDirectory(parent, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(parent))
      _fail("EXPLANATION_COMMAND_INVALID", "$command.save", "output parent must be an existing direct directory")
    if (Files.exists(output, LinkOption.NOFOLLOW_LINKS) && Files.isSymbolicLink(output))
      _fail("EXPLANATION_COMMAND_INVALID", "$command.save", "output must not be a symbolic link")
    val temporary = Files.createTempFile(parent, ".cozy-explanation-", ".tmp")
    try {
      Files.write(temporary, text.getBytes(StandardCharsets.UTF_8))
      try Files.move(temporary, output, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
      catch {
        case _: AtomicMoveNotSupportedException => _fail("EXPLANATION_COMMAND_INVALID", "$command.save", "filesystem does not support same-directory atomic output replacement")
      }
    } catch {
      case fault: ExplanationFault => throw fault
      case NonFatal(e) => _fail("EXPLANATION_COMMAND_INVALID", "$command.save", Option(e.getMessage).getOrElse("cannot write output"))
    } finally {
      try Files.deleteIfExists(temporary) catch { case NonFatal(_) => () }
    }
  }

  private[media] def _cli_path(value: String, path: String): Path = {
    if (value == null || value.isEmpty || value != value.trim) _fail("EXPLANATION_COMMAND_INVALID", path, "path must be nonempty and trimmed")
    try Paths.get(value) catch { case NonFatal(_) => _fail("EXPLANATION_COMMAND_INVALID", path, "path is invalid") }
  }

  private[media] def _fail(code: String, path: String, reason: String): Nothing = throw ExplanationFault(code, path, reason)
}
