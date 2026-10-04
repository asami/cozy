package cozy.modeler

import play.api.libs.json.{JsArray, JsObject, JsString, JsValue, Json}

import CmlUseCaseMetadata._
import CmlUseCaseMetadataCodec._

/*
 * @since   Oct.  4, 2026
 * @version Oct.  4, 2026
 * @author  ASAMI, Tomoharu
 */
private[cozy] object CmlUseCaseFlowCodec {
  def participations(value: JsValue, path: String): Result[Vector[ActorParticipation]] = {
    _array(value, path, _participation)
  }

  private def _participation(value: JsValue, path: String): Result[ActorParticipation] = {
    for {
      objectvalue <- _projection(value, Vector("role", "target"), path)
      rolevalue <- _field(objectvalue, "role", path + ".role")
      role <- rolevalue match {
        case JsString("actor") => Right(Actor)
        case JsString("primary") => Right(Primary)
        case JsString("secondary") => Right(Secondary)
        case JsString("supporting") => Right(Supporting)
        case JsString("stakeholder") => Right(Stakeholder)
        case _ => Left(_invalid_shape(path + ".role"))
      }
      target <- _field(objectvalue, "target", path + ".target").flatMap(_reference(_, path + ".target"))
    } yield ActorParticipation(role, target)
  }

  def flows(value: JsValue, path: String): Result[Vector[UseCaseFlow]] = {
    _array(value, path, _flow)
  }

  private def _flow(value: JsValue, path: String): Result[UseCaseFlow] = {
    for {
      objectvalue <- _projection(value, Vector("kind", "name", "summary", "description", "condition", "steps", "alternates", "exceptions"), path)
      kindvalue <- _field(objectvalue, "kind", path + ".kind")
      kind <- kindvalue match {
        case JsString("main") => Right(MainFlow)
        case JsString("alternate") => Right(AlternateFlow)
        case JsString("exception") => Right(ExceptionFlow)
        case _ => Left(_invalid_shape(path + ".kind"))
      }
      name <- _presence_field(objectvalue, "name", path, _string)
      summary <- _presence_field(objectvalue, "summary", path, _string)
      description <- _presence_field(objectvalue, "description", path, _string)
      condition <- _presence_field(objectvalue, "condition", path, _expression)
      steps <- _presence_field(objectvalue, "steps", path, _expressions)
      alternates <- _presence_field(objectvalue, "alternates", path, _expressions)
      exceptions <- _presence_field(objectvalue, "exceptions", path, _expressions)
    } yield UseCaseFlow(kind, name, summary, description, condition, steps, alternates, exceptions)
  }

  def participationsJson(values: Vector[ActorParticipation]): JsArray = {
    JsArray(values.map { value =>
      val role = value.role match {
        case Actor => "actor"
        case Primary => "primary"
        case Secondary => "secondary"
        case Supporting => "supporting"
        case Stakeholder => "stakeholder"
      }
      Json.obj("role" -> role, "target" -> _reference_json(value.target))
    })
  }

  def flowsJson(values: Vector[UseCaseFlow]): JsArray = {
    JsArray(values.map { value =>
      val kind = value.kind match {
        case MainFlow => "main"
        case AlternateFlow => "alternate"
        case ExceptionFlow => "exception"
      }
      Json.obj("kind" -> kind,
        "name" -> _presence_json(value.name, JsString),
        "summary" -> _presence_json(value.summary, JsString),
        "description" -> _presence_json(value.description, JsString),
        "condition" -> _presence_json(value.condition, _expression_json),
        "steps" -> _presence_json(value.steps, _expressions_json),
        "alternates" -> _presence_json(value.alternates, _expressions_json),
        "exceptions" -> _presence_json(value.exceptions, _expressions_json))
    })
  }
}
