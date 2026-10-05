package cozy.modeler

import org.goldenport.cncf.component.identity.{ComponentId, ComponentNamespace, ComponentLocalId, ComponentIdentityResult}
import play.api.libs.json._
import CmlSemanticFoundation.{ModelElementId, Presence}
import CmlCapabilityModel._
import CmlCapabilitySource.{CapabilitySource, CapabilityOrigin}
import CmlCapabilityJsonPart._
import CmlCapabilityReferenceJsonPart._
import CmlCapabilityDefinitionJsonPart._

/*
 * @since   Oct. 5, 2026
 * @version Oct. 5, 2026
 * @author  ASAMI, Tomoharu
 */
private[modeler] object CmlCapabilityDeclarationJsonPart {
  def _component_id(value: JsValue, context: Context): Result[ComponentId] = {
    def _namespace_(item: JsValue, location: Context): Result[ComponentNamespace] =
      _text(item, location).flatMap(x => _identity_result(ComponentNamespace.parse(x), location))
    def _local_id_(item: JsValue, location: Context): Result[ComponentLocalId] =
      _text(item, location).flatMap(x => _identity_result(ComponentLocalId.parse(x), location))
    _record(value, context, Vector(_entry("namespace", _namespace_), _entry("id", _local_id_)))
      .map(x => ComponentId.of(x._get[ComponentNamespace]("namespace"), x._get[ComponentLocalId]("id")))
  }

  private def _identity_result[A](result: ComponentIdentityResult[A], context: Context): Result[A] =
    if (result.isSuccess()) Right(result.value().get())
    else _failure(context, "Invalid canonical Component identity.")

  def _direction(value: JsValue, context: Context): Result[Direction] =
    _tag(value, context, Vector("PROVIDED" -> Provided, "REQUIRED" -> Required))

  def _declaration_key(value: JsValue, context: Context): Result[DeclarationKey] =
    _record(value, context, Vector(_entry("component", _component_id), _entry("direction", _direction),
      _entry("capability", _component_reference)))
      .map(x => DeclarationKey(x._get[ComponentId]("component"), x._get[Direction]("direction"),
        x._get[VersionedComponentCapabilityReference]("capability")))

  def _declaration(value: JsValue, context: Context): Result[ComponentDeclaration] =
    _record(value, context, Vector(_entry("component", _component_id), _entry("direction", _direction),
      _entry("capability", _component_reference), _entry("source", _source),
      _entry("realizations", _presence(_array(_realization))), _entry("traceability", _presence(_array(_traceability)))))
      .map(x => ComponentDeclaration(x._get[ComponentId]("component"), x._get[Direction]("direction"),
        x._get[VersionedComponentCapabilityReference]("capability"), x._get[CapabilitySource]("source"),
        x._get[Presence[Vector[RealizationEdge]]]("realizations"), x._get[Presence[Vector[TraceabilityReference]]]("traceability")))

  def _realization(value: JsValue, context: Context): Result[RealizationEdge] = {
    val kinds: Vector[(String, TargetKind)] = Vector("OPERATION" -> Operation, "WORKFLOW" -> Workflow, "STATE_MACHINE" -> StateMachine)
    _record(value, context, Vector(_entry("declarationKey", _declaration_key), _entry("targetKind", _tag(_, _, kinds)),
      _entry("target", _model_reference), _entry("targetComponent", _component_id), _entry("targetContractVersion", _text),
      _entry("generatedAbiVersion", _text), _entry("source", _source), _entry("origin", _origin)))
      .map(x => RealizationEdge(x._get[DeclarationKey]("declarationKey"), x._get[TargetKind]("targetKind"),
        x._get[ModelReference]("target"), x._get[ComponentId]("targetComponent"), x._get[String]("targetContractVersion"),
        x._get[String]("generatedAbiVersion"), x._get[CapabilitySource]("source"), x._get[CapabilityOrigin]("origin")))
  }

  def _compatibility(value: JsValue, context: Context): Result[CompatibilityEdge] =
    _record(value, context, Vector(_entry("identity", _model_id), _entry("requiredVersion", _text),
      _entry("providedVersion", _text), _entry("source", _source), _entry("origin", _origin)))
      .map(x => CompatibilityEdge(x._get[ModelElementId]("identity"), x._get[String]("requiredVersion"),
        x._get[String]("providedVersion"), x._get[CapabilitySource]("source"), x._get[CapabilityOrigin]("origin")))
}
