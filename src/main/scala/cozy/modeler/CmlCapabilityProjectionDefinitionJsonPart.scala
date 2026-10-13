package cozy.modeler

import play.api.libs.json._
import CmlCapabilityModel._
import CmlCapabilityProjectionReferenceJsonPart._

/*
 * @since   Oct. 14, 2026
 * @version Oct. 14, 2026
 * @author  ASAMI, Tomoharu
 */
private[modeler] object CmlCapabilityProjectionDefinitionJsonPart {
  private[modeler] def _definition(value: ComponentCapability): JsObject = Json.obj(
    "kind" -> "COMPONENT", "identity" -> _model_id(value.identity), "contractVersion" -> value.contractVersion,
    "name" -> value.name, "summary" -> _presence(value.summary, JsString), "description" -> _presence(value.description, JsString),
    "source" -> _source(value.source),
    "discoveryReferences" -> _presence(value.discoveryReferences, (items: Vector[ModelLink]) => JsArray(items.map(_model_link))),
    "grounding" -> _presence(value.grounding, (items: Vector[GroundingReference]) => JsArray(items.map(_grounding))),
    "traceability" -> _presence(value.traceability, (items: Vector[TraceabilityReference]) => JsArray(items.map(_traceability))))
  private[modeler] def _compatibility(value: CompatibilityEdge): JsObject = Json.obj(
    "identity" -> _model_id(value.identity), "requiredVersion" -> value.requiredVersion, "providedVersion" -> value.providedVersion,
    "source" -> _source(value.source), "origin" -> _origin(value.origin))
}
