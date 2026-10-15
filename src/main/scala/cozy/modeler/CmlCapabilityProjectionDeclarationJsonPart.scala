package cozy.modeler

import play.api.libs.json._
import CmlCapabilityModel._
import CmlCapabilityProjectionReferenceJsonPart._

/*
 * @since   Oct. 14, 2026
 * @version Oct. 15, 2026
 * @author  ASAMI, Tomoharu
 */
private[modeler] object CmlCapabilityProjectionDeclarationJsonPart {
  private def _direction(value: Direction): String = value match {
    case Provided => "PROVIDED"
    case Required => "REQUIRED"
  }
  private def _capability(value: VersionedComponentCapabilityReference): JsObject =
    Json.obj("kind" -> "COMPONENT", "identity" -> _model_id(value.identity), "contractVersion" -> value.contractVersion)
  private def _key(value: DeclarationKey): JsObject = Json.obj(
    "component" -> _component(value.component), "direction" -> _direction(value.direction), "capability" -> _capability(value.capability))
  private[modeler] def _declaration(value: ComponentDeclaration): JsObject = Json.obj(
    "component" -> _component(value.component), "direction" -> _direction(value.direction), "capability" -> _capability(value.capability),
    "source" -> _source(value.source),
    "realizations" -> _presence(value.realizations, (items: Vector[RealizationEdge]) => JsArray(items.map(_realization))),
    "traceability" -> _presence(value.traceability, (items: Vector[TraceabilityReference]) => JsArray(items.map(_traceability))))
  private def _realization(value: RealizationEdge): JsObject = {
    val kind = value.targetKind match {
      case Operation => "OPERATION"
      case Workflow => "WORKFLOW"
      case StateMachine => "STATE_MACHINE"
    }
    Json.obj("declarationKey" -> _key(value.declarationKey), "targetKind" -> kind, "target" -> _model_reference(value.target),
      "targetComponent" -> _component(value.targetComponent), "targetContractVersion" -> value.targetContractVersion,
      "generatedAbiVersion" -> value.generatedAbiVersion, "source" -> _source(value.source), "origin" -> _origin(value.origin))
  }
}
