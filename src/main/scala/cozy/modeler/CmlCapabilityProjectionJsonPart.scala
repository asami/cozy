package cozy.modeler

import play.api.libs.json._
import CmlCapabilityGenerationContext.GenerationContext
import CmlCapabilityModel._
import CmlSemanticFoundation.Presence
import CmlCapabilityProjectionReferenceJsonPart._
import CmlCapabilityProjectionDefinitionJsonPart._
import CmlCapabilityProjectionDeclarationJsonPart._

/*
 * @since   Oct. 14, 2026
 * @version Oct. 15, 2026
 * @author  ASAMI, Tomoharu
 */
private[modeler] object CmlCapabilityProjectionJsonPart {
  private[modeler] def _encode(context: GenerationContext, capabilities: Vector[ComponentCapability],
    declarations: Vector[ComponentDeclaration], compatibility: Presence[Vector[CompatibilityEdge]]): JsObject = Json.obj(
    "schemaVersion" -> context.schemaversion, "generatedAbiVersion" -> context.generatedabiversion,
    "producer" -> Json.obj("id" -> context.producer.id, "version" -> context.producer.version),
    "component" -> (_component(context.component.component) + ("version" -> JsString(context.component.version))),
    "package" -> Json.obj("coordinate" -> context.packagecoordinate.coordinate, "version" -> context.packagecoordinate.version),
    "source" -> _source(context.source), "capabilities" -> JsArray(capabilities.map(_definition)),
    "declarations" -> JsArray(declarations.map(_declaration)),
    "compatibility" -> _presence(compatibility, (items: Vector[CompatibilityEdge]) => JsArray(items.map(_compatibility))))
}
