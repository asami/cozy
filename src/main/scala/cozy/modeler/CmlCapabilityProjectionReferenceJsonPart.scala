package cozy.modeler

import org.goldenport.cncf.component.identity.ComponentId
import play.api.libs.json._
import CmlSemanticFoundation._
import CmlCapabilitySource.{CapabilitySource, CapabilityOrigin}
import CmlCapabilityModel._

/*
 * @since   Oct. 14, 2026
 * @version Oct. 15, 2026
 * @author  ASAMI, Tomoharu
 */
private[modeler] object CmlCapabilityProjectionReferenceJsonPart {
  private[modeler] def _component(value: ComponentId): JsObject =
    Json.obj("namespace" -> value.namespace().value(), "id" -> value.localId().value())
  private[modeler] def _model_id(value: ModelElementId): JsObject =
    Json.obj("modelId" -> value.modelId, "elementId" -> value.elementId)
  private[modeler] def _source(value: CapabilitySource): JsObject = Json.obj(
    "modelAuthority" -> value.modelAuthority, "path" -> value.path, "sourceRevision" -> value.sourceRevision,
    "line" -> value.line.map[JsValue](x => JsNumber(x)).getOrElse[JsValue](JsNull))
  private[modeler] def _presence[A](value: Presence[A], encode: A => JsValue): JsObject = value match {
    case Present(item) => Json.obj("state" -> "PRESENT", "value" -> encode(item))
    case Absent(reason, detail) => Json.obj("state" -> "ABSENT", "reason" -> reason.toString, "detail" -> detail)
  }
  private[modeler] def _origin(value: CapabilityOrigin): JsObject = value match {
    case CmlCapabilitySource.Declared => Json.obj("kind" -> "DECLARED")
    case CmlCapabilitySource.Derived(rule, inputs) =>
      Json.obj("kind" -> "DERIVED", "ruleId" -> rule, "inputs" -> JsArray(inputs.map(_source)))
  }
  private[modeler] def _boundary(value: ReferenceBoundary): String = value match {
    case Local => "LOCAL"
    case External => "EXTERNAL"
  }
  private[modeler] def _model_reference(value: ModelReference): JsObject =
    Json.obj("identity" -> _model_id(value.identity), "boundary" -> _boundary(value.boundary))
  private def _semantic_target(value: SemanticTarget): JsObject = value match {
    case ModelTarget(identity) => Json.obj("kind" -> "MODEL", "identity" -> _model_id(identity))
    case TermTarget(identity) => Json.obj("kind" -> "TERM", "identity" ->
      Json.obj("vocabularyId" -> identity.vocabularyId, "termId" -> identity.termId))
  }
  private def _relation(value: RelationId): JsObject =
    Json.obj("vocabularyId" -> value.vocabularyId, "relationId" -> value.relationId)
  private def _profile(value: ProfileId): JsObject =
    Json.obj("vocabularyId" -> value.vocabularyId, "profileId" -> value.profileId)
  private def _label(value: LocalizedLabel): JsObject =
    Json.obj("text" -> value.text, "language" -> value.language.map[JsValue](JsString).getOrElse[JsValue](JsNull))
  private[modeler] def _model_link(value: ModelLink): JsObject = {
    val role = value.role match {
      case Goal => "GOAL"
      case UseCase => "USE_CASE"
      case Scenario => "SCENARIO"
      case UseCaseSlice => "USE_CASE_SLICE"
      case Component => "COMPONENT"
      case Specification => "SPECIFICATION"
      case Evidence => "EVIDENCE"
    }
    Json.obj("role" -> role, "target" -> _model_reference(value.target), "source" -> _source(value.source), "origin" -> _origin(value.origin))
  }
  private[modeler] def _traceability(value: TraceabilityReference): JsObject = {
    val role = value.role match {
      case SpecificationTrace => "SPECIFICATION"
      case EvidenceTrace => "EVIDENCE"
    }
    Json.obj("role" -> role, "target" -> _model_reference(value.target), "source" -> _source(value.source), "origin" -> _origin(value.origin))
  }
  private[modeler] def _grounding(value: GroundingReference): JsObject = Json.obj(
    "target" -> _semantic_target(value.target), "boundary" -> _boundary(value.boundary),
    "relation" -> _presence(value.relation, _relation), "profile" -> _presence(value.profile, _profile),
    "context" -> _presence(value.context, JsString), "preferredLabel" -> _presence(value.preferredLabel, _label),
    "source" -> _source(value.source), "origin" -> _origin(value.origin))
}
