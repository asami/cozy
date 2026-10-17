package cozy.modeler

import com.fasterxml.jackson.core.{JsonFactory, JsonParser, JsonToken}
import play.api.libs.json._
import scala.util.control.NonFatal
import CmlSemanticFoundation.{Presence, Present, Absent, AbsenceReason, NotDeclared, NotRepresented, Unsupported, NotApplicable}
import CmlCapabilitySource._

/*
 * @since   Oct. 5, 2026
 * @version Oct. 15, 2026
 * @author  ASAMI, Tomoharu
 */
private[modeler] object CmlCapabilityJsonPart {
  type Result[A] = Either[Vector[SyntaxIssue], A]
  type Decoder[A] = (JsValue, Context) => Result[A]
  final case class Context(scope: SourceScope, sectionpath: Vector[String], line: Option[Int], fieldpath: String) {
    def _child(name: String): Context = copy(fieldpath = if (fieldpath.isEmpty) name else s"$fieldpath.$name")
    def _index(index: Int): Context = copy(fieldpath = s"$fieldpath[$index]")
    def _issue(kind: SyntaxIssueKind, message: String): SyntaxIssue =
      SyntaxIssue(kind, fieldpath, scope, sectionpath, line, message)
  }
  final case class Fields(values: Map[String, Any]) {
    // Only values produced by the corresponding field decoder reach this staging map.
    def _get[A](name: String): A = values(name).asInstanceOf[A]
  }

  def _failure[A](context: Context, message: String, kind: SyntaxIssueKind = InvalidField): Result[A] =
    Left(Vector(context._issue(kind, message)))

  def _sequence[A](values: Vector[Result[A]]): Result[Vector[A]] = {
    val issues = values.collect { case Left(errors) => errors }.flatten
    if (issues.nonEmpty) Left(issues) else Right(values.collect { case Right(value) => value })
  }

  def _entry[A](name: String, decoder: Decoder[A]): (String, Decoder[Any]) =
    name -> ((value: JsValue, context: Context) => decoder(value, context).map(x => x: Any))

  def _record(value: JsValue, context: Context, expected: Vector[(String, Decoder[Any])]): Result[Fields] =
    value match {
      case objectvalue: JsObject =>
        val fields = objectvalue.fields.toVector
        val results = fields.map { case (name, field) =>
          expected.find(_._1 == name) match {
            case Some((_, decoder)) => decoder(field, context._child(name)).map(name -> _)
            case None => _failure[(String, Any)](context._child(name), "Unknown field in closed object.")
          }
        }
        val missing = expected.filterNot(x => fields.exists(_._1 == x._1)).map { case (name, _) =>
          _failure[(String, Any)](context._child(name), "Mandatory field is missing.")
        }
        _sequence(results ++ missing).map(x => Fields(x.toMap))
      case _ => _failure(context, "An object is required.")
    }

  def _text(value: JsValue, context: Context): Result[String] = value match {
    case JsString(text) if _valid_text(text) => Right(text)
    case _ => _failure(context, "Nonempty, Unicode-edge-unpadded, control-free text is required.")
  }

  def _tag[A](value: JsValue, context: Context, choices: Vector[(String, A)]): Result[A] =
    _text(value, context).flatMap { text =>
      choices.find(_._1 == text).map(x => Right(x._2)).getOrElse(
        _failure(context, "Unsupported closed syntax tag.", UnsupportedSyntax))
    }

  def _array[A](decoder: Decoder[A]): Decoder[Vector[A]] = (value, context) => value match {
    case JsArray(items) => _sequence(items.toVector.zipWithIndex.map { case (item, index) => decoder(item, context._index(index)) })
    case _ => _failure(context, "An ordered array is required.")
  }

  def _presence[A](decoder: Decoder[A]): Decoder[Presence[A]] = (value, context) => {
    val state = (value \ "state").toOption
    state match {
      case Some(JsString("PRESENT")) =>
        _record(value, context, Vector(_entry("state", _tag(_, _, Vector("PRESENT" -> ()))), _entry("value", decoder)))
          .map(x => Present(x._get[A]("value")))
      case Some(JsString("ABSENT")) =>
        val reasons: Vector[(String, AbsenceReason)] = Vector("NotDeclared" -> NotDeclared, "NotRepresented" -> NotRepresented,
          "Unsupported" -> Unsupported, "NotApplicable" -> NotApplicable)
        _record(value, context, Vector(_entry("state", _tag(_, _, Vector("ABSENT" -> ()))),
          _entry("reason", _tag(_, _, reasons)), _entry("detail", _text)))
          .map(x => Absent(x._get[AbsenceReason]("reason"), x._get[String]("detail")))
      case _ => _failure(context._child("state"), "Presence requires exactly PRESENT or ABSENT.")
    }
  }

  def _json(text: String, context: Context): Result[JsValue] = {
    val parser = new JsonFactory().enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION).createParser(text)
    try {
      val token = parser.nextToken()
      if (token == null) _failure(context, "A JSON value is required.", MalformedInput)
      else {
        if (token == JsonToken.START_OBJECT || token == JsonToken.START_ARRAY) parser.skipChildren()
        if (parser.nextToken() != null) _failure(context, "Exactly one JSON value is required.", MalformedInput)
        else Right(Json.parse(text))
      }
    } catch {
      case NonFatal(_) => _failure(context, "Malformed JSON or duplicate JSON key.", MalformedInput)
    } finally parser.close()
  }
}
