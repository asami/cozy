package cozy.ui

import com.fasterxml.jackson.core.{JsonFactory, JsonParser, JsonToken}
import scala.util.control.NonFatal
import CozyLogicalUi._

/*
 * @since   Sep. 1, 2026
 * @version Sep. 1, 2026
 * @author  ASAMI, Tomoharu
 */
private[cozy] object CozyLogicalUiCodec {
  private sealed trait JsonValue
  private final case class JsonObject(fields: Vector[(String, JsonValue)]) extends JsonValue
  private final case class JsonArray(values: Vector[JsonValue]) extends JsonValue
  private final case class JsonString(value: String) extends JsonValue
  private final case class JsonNumber(value: String) extends JsonValue
  private final case class JsonBoolean(value: Boolean) extends JsonValue
  private case object JsonNull extends JsonValue
  private final class CodecFault(val error: LogicalUiError) extends RuntimeException(error.reason)

  private val _candidate_fields = Vector(
    "schema",
    "version",
    "kind",
    "inputIdentity",
    "componentSurfaces",
    "componentBindings",
    "useCases",
    "identity"
  )

  def encodeCandidate(candidate: LogicalUiCandidate): String = candidate.canonicalJson

  def decodeCandidate(text: String): Either[LogicalUiError, LogicalUiCandidate] =
    try {
      val fields = _object(_parse(text), "$")
      _exact_fields(fields, _candidate_fields, "$")
      _schema_version(fields, "$")
      if (_string(_field(fields, "kind", "$") , "$.kind") != "candidate")
        _fault("LUI43_CODEC_SCHEMA_INVALID", "$.kind", "kind must be candidate")
      val inputidentity = _string(_field(fields, "inputIdentity", "$") , "$.inputIdentity")
      val identity = _string(_field(fields, "identity", "$") , "$.identity")
      val input = CandidateInput(
        _array(_field(fields, "componentSurfaces", "$") , "$.componentSurfaces").zipWithIndex.map { case (value, index) =>
          _component_surface(value, s"$$.componentSurfaces[$index]")
        },
        _array(_field(fields, "componentBindings", "$") , "$.componentBindings").zipWithIndex.map { case (value, index) =>
          _component_binding(value, s"$$.componentBindings[$index]")
        },
        _use_case_layers(_field(fields, "useCases", "$") , "$.useCases")
      )
      val candidate = CozyLogicalUi.candidate(input) match {
        case Right(value) => value
        case Left(error) => _fault(error.code, error.path, error.reason)
      }
      if (candidate.inputIdentity != inputidentity)
        _fault("LUI43_CODEC_IDENTITY_MISMATCH", "$.inputIdentity", "input identity does not match canonical logical content")
      if (candidate.identity != identity)
        _fault("LUI43_CODEC_IDENTITY_MISMATCH", "$.identity", "candidate identity does not match canonical logical content")
      Right(candidate)
    } catch {
      case fault: CodecFault => Left(fault.error)
      case NonFatal(error) => Left(LogicalUiError("LUI43_CODEC_MALFORMED", "$", Option(error.getMessage).getOrElse("malformed Logical UI document")))
    }

  private def _component_surface(value: JsonValue, path: String): ComponentSurface = {
    val fields = _object(value, path)
    _exact_fields(fields, Vector("component", "exportIds"), path)
    ComponentSurface(
      _component_coordinate(_field(fields, "component", path), s"$path.component"),
      _array(_field(fields, "exportIds", path), s"$path.exportIds").zipWithIndex.map { case (item, index) =>
        _string(item, s"$path.exportIds[$index]")
      }
    )
  }

  private def _component_binding(value: JsonValue, path: String): ComponentBinding = {
    val fields = _object(value, path)
    _exact_fields(fields, Vector("component", "exportId"), path)
    ComponentBinding(
      _component_coordinate(_field(fields, "component", path), s"$path.component"),
      _string(_field(fields, "exportId", path), s"$path.exportId")
    )
  }

  private def _component_coordinate(value: JsonValue, path: String): ComponentCoordinate = {
    val fields = _object(value, path)
    _exact_fields(fields, Vector("namespace", "id", "version"), path)
    ComponentCoordinate(
      _string(_field(fields, "namespace", path), s"$path.namespace"),
      _string(_field(fields, "id", path), s"$path.id"),
      _string(_field(fields, "version", path), s"$path.version")
    )
  }

  private def _use_case_layers(value: JsonValue, path: String): UseCaseLayers = {
    val fields = _object(value, path)
    _exact_fields(fields, Vector("business", "system", "ui", "realizations"), path)
    UseCaseLayers(
      _use_case_reference(_field(fields, "business", path), s"$path.business"),
      _use_case_reference(_field(fields, "system", path), s"$path.system"),
      _use_case_reference(_field(fields, "ui", path), s"$path.ui"),
      _array(_field(fields, "realizations", path), s"$path.realizations").zipWithIndex.map { case (item, index) =>
        _use_case_realization(item, s"$path.realizations[$index]")
      }
    )
  }

  private def _use_case_realization(value: JsonValue, path: String): UseCaseRealization = {
    val fields = _object(value, path)
    _exact_fields(fields, Vector("source", "target"), path)
    UseCaseRealization(
      _use_case_reference(_field(fields, "source", path), s"$path.source"),
      _use_case_reference(_field(fields, "target", path), s"$path.target")
    )
  }

  private def _use_case_reference(value: JsonValue, path: String): UseCaseReference = {
    val fields = _object(value, path)
    _exact_fields(fields, Vector("layer", "id"), path)
    UseCaseReference(
      _layer(_string(_field(fields, "layer", path), s"$path.layer"), s"$path.layer"),
      _string(_field(fields, "id", path), s"$path.id")
    )
  }

  private def _layer(value: String, path: String): UseCaseLayer = value match {
    case "business" => Business
    case "system" => System
    case "ui" => Ui
    case _ => _fault("LUI43_CODEC_SCHEMA_INVALID", path, "layer must be business, system, or ui")
  }

  private def _schema_version(fields: Vector[(String, JsonValue)], path: String): Unit = {
    if (_string(_field(fields, "schema", path), s"$path.schema") != CozyLogicalUi.schema)
      _fault("LUI43_CODEC_SCHEMA_INVALID", s"$path.schema", s"schema must be ${CozyLogicalUi.schema}")
    _field(fields, "version", path) match {
      case JsonNumber("1") =>
      case _ => _fault("LUI43_CODEC_SCHEMA_INVALID", s"$path.version", "version must be 1")
    }
  }

  private def _parse(text: String): JsonValue = {
    if (text == null)
      _fault("LUI43_CODEC_MALFORMED", "$", "Logical UI document is required")
    val parser = new JsonFactory().enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION).createParser(text)
    try {
      val token = parser.nextToken()
      if (token == null)
        _fault("LUI43_CODEC_MALFORMED", "$", "Logical UI document must contain JSON")
      val value = _read_value(parser, "$")
      if (parser.nextToken() != null)
        _fault("LUI43_CODEC_MALFORMED", "$", "Logical UI document must contain exactly one JSON value")
      value
    } catch {
      case fault: CodecFault => throw fault
      case NonFatal(error) => _fault("LUI43_CODEC_MALFORMED", "$", Option(error.getMessage).getOrElse("invalid JSON"))
    } finally {
      parser.close()
    }
  }

  private def _read_value(parser: JsonParser, path: String): JsonValue = parser.currentToken() match {
    case JsonToken.START_OBJECT =>
      val fields = Vector.newBuilder[(String, JsonValue)]
      var token = parser.nextToken()
      while (token != JsonToken.END_OBJECT) {
        if (token != JsonToken.FIELD_NAME)
          _fault("LUI43_CODEC_MALFORMED", path, "JSON object field name is required")
        val name = parser.getCurrentName
        val valuetoken = parser.nextToken()
        if (valuetoken == null)
          _fault("LUI43_CODEC_MALFORMED", path, "JSON object value is required")
        fields += name -> _read_value(parser, s"$path.$name")
        token = parser.nextToken()
      }
      JsonObject(fields.result())
    case JsonToken.START_ARRAY =>
      val values = Vector.newBuilder[JsonValue]
      var token = parser.nextToken()
      var index = 0
      while (token != JsonToken.END_ARRAY) {
        if (token == null)
          _fault("LUI43_CODEC_MALFORMED", path, "JSON array must terminate")
        values += _read_value(parser, s"$path[$index]")
        index += 1
        token = parser.nextToken()
      }
      JsonArray(values.result())
    case JsonToken.VALUE_STRING => JsonString(parser.getText)
    case JsonToken.VALUE_NUMBER_INT => JsonNumber(parser.getText)
    case JsonToken.VALUE_TRUE => JsonBoolean(true)
    case JsonToken.VALUE_FALSE => JsonBoolean(false)
    case JsonToken.VALUE_NULL => JsonNull
    case _ => _fault("LUI43_CODEC_MALFORMED", path, "unsupported JSON value")
  }

  private def _object(value: JsonValue, path: String): Vector[(String, JsonValue)] = value match {
    case JsonObject(fields) => fields
    case _ => _fault("LUI43_CODEC_SCHEMA_INVALID", path, "object is required")
  }

  private def _array(value: JsonValue, path: String): Vector[JsonValue] = value match {
    case JsonArray(values) => values
    case _ => _fault("LUI43_CODEC_SCHEMA_INVALID", path, "array is required")
  }

  private def _string(value: JsonValue, path: String): String = value match {
    case JsonString(item) => item
    case _ => _fault("LUI43_CODEC_SCHEMA_INVALID", path, "string is required")
  }

  private def _field(fields: Vector[(String, JsonValue)], name: String, path: String): JsonValue =
    fields.find(_._1 == name).map(_._2).getOrElse(
      _fault("LUI43_CODEC_SCHEMA_INVALID", path, s"missing field: $name")
    )

  private def _exact_fields(fields: Vector[(String, JsonValue)], expected: Vector[String], path: String): Unit = {
    val names = fields.map(_._1)
    if (names.size != expected.size || names.toSet != expected.toSet)
      _fault("LUI43_CODEC_SCHEMA_INVALID", path, s"fields must be exactly: ${expected.mkString(", ")}")
  }

  private def _fault(code: String, path: String, reason: String): Nothing =
    throw new CodecFault(LogicalUiError(code, path, reason))
}
