package cozy.modeler

import play.api.libs.json._
import CmlCapabilityModel._
import CmlCapabilitySource.{CapabilitySource, SourceScope}
import CmlCapabilityGeneration.Generated
import CmlCapabilityGenerationContext.{Issue, IssueKind, Producer}
import CmlCapabilityProjectionReferenceJsonPart._

/*
 * @since   Oct. 14, 2026
 * @version Oct. 14, 2026
 * @author  ASAMI, Tomoharu
 */
private[cozy] object CmlCapabilityApplicationProjection {
  def encode(generated: Generated, sources: Vector[CapabilitySource], producer: Producer): Either[Vector[Issue], JsObject] = {
    if (generated == null || sources == null || sources.exists(_ == null) || producer == null)
      return Left(Vector(Issue(IssueKind.Malformed, "application", None, Vector.empty, "Generated, ordered headers and producer are required.")))
    if (producer != Producer("cozy", org.simplemodeling.cozy.BuildInfo.version))
      return Left(Vector(Issue(IssueKind.ProducerMismatch, "producer", None, Vector.empty, "Application producer must equal the actual Cozy build.")))
    if (sources.exists(x => !CmlCapabilitySource._valid_text(x.modelAuthority) || !CmlCapabilitySource._valid_path(x.path) ||
      !CmlCapabilitySource._valid_text(x.sourceRevision) || x.line == null || x.line.exists(_ <= 0)))
      return Left(Vector(Issue(IssueKind.Malformed, "sources", None, Vector.empty, "Complete valid selected source headers are required.")))
    val scopes = sources.map(x => SourceScope(x.modelAuthority, x.path))
    val documents = generated.validated.documents
    if (scopes != documents.map(_.scope) || scopes.distinct.size != scopes.size)
      Left(Vector(Issue(IssueKind.MissingCoverage, "sources", None, sources, "Every original document requires exactly one header in original document order.")))
    else Right(Json.obj("schemaVersion" -> 1, "generatedAbiVersion" -> "cozy.application-capability.v1",
      "producer" -> Json.obj("id" -> producer.id, "version" -> producer.version),
      "documents" -> JsArray(documents.zip(sources).map { case (document, source) =>
        Json.obj("source" -> _source(source), "applicationCapabilities" -> JsArray(document.model.applicationCapabilities.map(_application)))
      })))
  }
  private def _application(value: ApplicationCapability): JsObject = Json.obj(
    "kind" -> "APPLICATION", "identity" -> _model_id(value.identity), "contractVersion" -> value.contractVersion,
    "name" -> value.name, "summary" -> _presence(value.summary, JsString), "description" -> _presence(value.description, JsString),
    "source" -> _source(value.source), "demandReferences" -> _presence(value.demandReferences, (x: Vector[ModelLink]) => JsArray(x.map(_model_link))),
    "discoveryReferences" -> _presence(value.discoveryReferences, (x: Vector[ModelLink]) => JsArray(x.map(_model_link))),
    "componentMappings" -> _presence(value.componentMappings, (x: Vector[ApplicationComponentMapping]) => JsArray(x.map(_mapping))),
    "dependencies" -> _presence(value.dependencies, (x: Vector[ApplicationDependency]) => JsArray(x.map(_dependency))),
    "grounding" -> _presence(value.grounding, (x: Vector[GroundingReference]) => JsArray(x.map(_grounding))),
    "traceability" -> _presence(value.traceability, (x: Vector[TraceabilityReference]) => JsArray(x.map(_traceability))))
  private def _mapping(value: ApplicationComponentMapping): JsObject = Json.obj(
    "target" -> Json.obj("kind" -> "COMPONENT", "identity" -> _model_id(value.target.identity), "contractVersion" -> value.target.contractVersion),
    "boundary" -> _boundary(value.boundary), "source" -> _source(value.source), "origin" -> _origin(value.origin))
  private def _dependency(value: ApplicationDependency): JsObject = Json.obj(
    "target" -> Json.obj("kind" -> "APPLICATION", "identity" -> _model_id(value.target.identity), "contractVersion" -> value.target.contractVersion),
    "boundary" -> _boundary(value.boundary), "source" -> _source(value.source), "origin" -> _origin(value.origin))
}
