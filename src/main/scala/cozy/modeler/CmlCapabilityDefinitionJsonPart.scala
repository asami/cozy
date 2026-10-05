package cozy.modeler

import play.api.libs.json._
import CmlSemanticFoundation.{ModelElementId, Presence, ReferenceBoundary}
import CmlCapabilityModel._
import CmlCapabilitySource.{CapabilitySource, CapabilityOrigin}
import CmlCapabilityJsonPart._
import CmlCapabilityReferenceJsonPart._

/*
 * @since   Oct. 5, 2026
 * @version Oct. 5, 2026
 * @author  ASAMI, Tomoharu
 */
private[modeler] object CmlCapabilityDefinitionJsonPart {
  private val _common_fields = Vector(
    _entry("identity", _definition_id), _entry("contractVersion", _text), _entry("name", _text),
    _entry("summary", _presence(_text)), _entry("description", _presence(_text)), _entry("source", _source)
  )
  private val _shared_edges = Vector(
    _entry("discoveryReferences", _presence(_array(_model_link(false)))),
    _entry("grounding", _presence(_array(_grounding))), _entry("traceability", _presence(_array(_traceability)))
  )

  def _application(value: JsValue, context: Context): Result[ApplicationCapability] =
    _record(value, context, _common_fields ++ Vector(
      _entry("demandReferences", _presence(_array(_model_link(true)))),
      _entry("discoveryReferences", _presence(_array(_model_link(false)))),
      _entry("componentMappings", _presence(_array(_mapping))), _entry("dependencies", _presence(_array(_dependency))),
      _entry("grounding", _presence(_array(_grounding))), _entry("traceability", _presence(_array(_traceability)))
    )).map { fields =>
      ApplicationCapability(fields._get[ModelElementId]("identity"), fields._get[String]("contractVersion"),
        fields._get[String]("name"), fields._get[Presence[String]]("summary"), fields._get[Presence[String]]("description"),
        fields._get[CapabilitySource]("source"), fields._get[Presence[Vector[ModelLink]]]("demandReferences"),
        fields._get[Presence[Vector[ModelLink]]]("discoveryReferences"),
        fields._get[Presence[Vector[ApplicationComponentMapping]]]("componentMappings"),
        fields._get[Presence[Vector[ApplicationDependency]]]("dependencies"),
        fields._get[Presence[Vector[GroundingReference]]]("grounding"), fields._get[Presence[Vector[TraceabilityReference]]]("traceability"))
    }

  def _component(value: JsValue, context: Context): Result[ComponentCapability] =
    _record(value, context, _common_fields ++ _shared_edges).map { fields =>
      ComponentCapability(fields._get[ModelElementId]("identity"), fields._get[String]("contractVersion"),
        fields._get[String]("name"), fields._get[Presence[String]]("summary"), fields._get[Presence[String]]("description"),
        fields._get[CapabilitySource]("source"), fields._get[Presence[Vector[ModelLink]]]("discoveryReferences"),
        fields._get[Presence[Vector[GroundingReference]]]("grounding"), fields._get[Presence[Vector[TraceabilityReference]]]("traceability"))
    }

  def _component_reference(value: JsValue, context: Context): Result[VersionedComponentCapabilityReference] =
    _record(value, context, Vector(_entry("kind", _tag(_, _, Vector("COMPONENT" -> ()))),
      _entry("identity", _model_id), _entry("contractVersion", _text)))
      .map(x => VersionedComponentCapabilityReference(x._get[ModelElementId]("identity"), x._get[String]("contractVersion")))

  def _application_reference(value: JsValue, context: Context): Result[VersionedApplicationCapabilityReference] =
    _record(value, context, Vector(_entry("kind", _tag(_, _, Vector("APPLICATION" -> ()))),
      _entry("identity", _model_id), _entry("contractVersion", _text)))
      .map(x => VersionedApplicationCapabilityReference(x._get[ModelElementId]("identity"), x._get[String]("contractVersion")))

  def _mapping(value: JsValue, context: Context): Result[ApplicationComponentMapping] =
    _record(value, context, Vector(_entry("target", _component_reference), _entry("boundary", _boundary),
      _entry("source", _source), _entry("origin", _origin)))
      .map(x => ApplicationComponentMapping(x._get[VersionedComponentCapabilityReference]("target"),
        x._get[ReferenceBoundary]("boundary"), x._get[CapabilitySource]("source"), x._get[CapabilityOrigin]("origin")))

  def _dependency(value: JsValue, context: Context): Result[ApplicationDependency] =
    _record(value, context, Vector(_entry("target", _application_reference), _entry("boundary", _boundary),
      _entry("source", _source), _entry("origin", _origin)))
      .map(x => ApplicationDependency(x._get[VersionedApplicationCapabilityReference]("target"),
        x._get[ReferenceBoundary]("boundary"), x._get[CapabilitySource]("source"), x._get[CapabilityOrigin]("origin")))
}
