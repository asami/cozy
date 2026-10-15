package cozy.modeler

import play.api.libs.json._
import scala.util.control.NonFatal
import cozy.archive.CozyComponentReleaseCoordinateCodec
import CmlCapabilityModel._
import CmlCapabilitySource.{CapabilitySource, SourceScope}
import CmlCapabilityGeneration.Generated
import CmlCapabilityGenerationContext.{Issue, IssueKind, Producer}
import CmlCapabilityProjectionReferenceJsonPart._
import CmlCapabilityProductionRequest.PackageOutput

/*
 * @since   Oct. 14, 2026
 * @version Oct. 15, 2026
 * @author  ASAMI, Tomoharu
 */
private[cozy] object CmlCapabilityComponentUsageProjection {
  def encode(generated: Generated, sources: Vector[CapabilitySource], producer: Producer,
    output: PackageOutput): Either[Vector[Issue], JsObject] = {
    if (generated == null || sources == null || sources.exists(_ == null) || producer == null || output == null ||
      output.component == null || output.component.component == null || output.packagecoordinate == null || output.usagecapabilities == null)
      return Left(Vector(Issue(IssueKind.Malformed, "usage", None, Vector.empty, "Generated, ordered headers, producer and explicit package selection are required.")))
    if (producer != Producer("cozy", org.simplemodeling.cozy.BuildInfo.version))
      return Left(Vector(Issue(IssueKind.ProducerMismatch, "producer", None, Vector.empty, "Usage producer must equal the actual Cozy build.")))
    if (sources.exists(x => !CmlCapabilitySource._valid_text(x.modelAuthority) || !CmlCapabilitySource._valid_path(x.path) ||
      !CmlCapabilitySource._valid_text(x.sourceRevision) || x.line == null || x.line.exists(_ <= 0)))
      return Left(Vector(Issue(IssueKind.Malformed, "sources", None, Vector.empty, "Complete valid selected source headers are required.")))
    val coordinateissues = try {
      CozyComponentReleaseCoordinateCodec.admit(output.component.component.namespace().value(),
        output.component.component.localId().value(), output.component.version, "component")
      Vector.empty[Issue]
    } catch { case NonFatal(_) => Vector(Issue(IssueKind.Malformed, "component", None, Vector.empty, "An exact canonical Component release is required.")) }
    val packageissues = if (CmlCapabilitySource._valid_text(output.packagecoordinate.coordinate) &&
      CmlCapabilitySource._valid_text(output.packagecoordinate.version)) Vector.empty
      else Vector(Issue(IssueKind.Malformed, "package", None, Vector.empty, "An explicit valid independent package domain is required."))
    if (coordinateissues.nonEmpty || packageissues.nonEmpty) return Left(coordinateissues ++ packageissues)
    val scopes = sources.map(x => SourceScope(x.modelAuthority, x.path))
    val documents = generated.validated.documents
    if (scopes != documents.map(_.scope) || scopes.distinct.size != scopes.size)
      return Left(Vector(Issue(IssueKind.MissingCoverage, "sources", None, sources, "Every original document requires exactly one header in original document order.")))
    val definitions = documents.flatMap(_.model.componentUsageCapabilities)
    val issues = output.usagecapabilities.zipWithIndex.flatMap { case (reference, index) =>
      val field = s"usageCapabilities[$index]"
      if (reference == null || reference.identity == null || !CmlCapabilitySource._valid_text(reference.identity.modelId) ||
        !CmlCapabilitySource._valid_text(reference.identity.elementId) || !CmlCapabilitySource._valid_text(reference.contractVersion))
        Vector(Issue(IssueKind.Malformed, field, None, Vector.empty, "An explicit valid versioned Usage reference is required."))
      else {
        val matches = definitions.filter(x => x.identity == reference.identity && x.contractVersion == reference.contractVersion)
        if (matches.size != 1) Vector(Issue(IssueKind.UnknownSnapshot, field, None, matches.map(_.source), "Select exactly one original versioned Usage definition."))
        else if (matches.head.component != output.component.component)
          Vector(Issue(IssueKind.OutputConflict, field, Some(matches.head.source), Vector.empty, "Selected Usage owner must equal the package Component."))
        else if (output.usagecapabilities.take(index).contains(reference))
          Vector(Issue(IssueKind.DuplicateSnapshot, field, Some(matches.head.source), Vector.empty, "Repeated exact Usage selection."))
        else Vector.empty
      }
    }
    if (issues.nonEmpty) Left(issues)
    else {
      val selected = output.usagecapabilities.toSet
      val contributing = documents.zip(sources).flatMap { case (document, source) =>
        val records = document.model.componentUsageCapabilities.filter(x => x.component == output.component.component &&
          selected(VersionedComponentUsageCapabilityReference(x.identity, x.contractVersion)))
        if (records.isEmpty) Vector.empty else Vector(Json.obj("source" -> _source(source),
          "componentUsageCapabilities" -> JsArray(records.map(_usage))))
      }
      Right(Json.obj("schemaVersion" -> 1, "generatedAbiVersion" -> "cozy.component-usage-capability.v1",
        "producer" -> Json.obj("id" -> producer.id, "version" -> producer.version),
        "component" -> (_component(output.component.component) + ("version" -> JsString(output.component.version))),
        "package" -> Json.obj("coordinate" -> output.packagecoordinate.coordinate, "version" -> output.packagecoordinate.version),
        "documents" -> JsArray(contributing)))
    }
  }
  private def _usage(value: ComponentUsageCapability): JsObject = Json.obj(
    "kind" -> "COMPONENT_USAGE", "component" -> _component(value.component), "identity" -> _model_id(value.identity), "contractVersion" -> value.contractVersion,
    "name" -> value.name, "summary" -> _presence(value.summary, JsString), "description" -> _presence(value.description, JsString),
    "source" -> _source(value.source), "demandReferences" -> _presence(value.demandReferences, (x: Vector[ModelLink]) => JsArray(x.map(_model_link))),
    "discoveryReferences" -> _presence(value.discoveryReferences, (x: Vector[ModelLink]) => JsArray(x.map(_model_link))),
    "componentMappings" -> _presence(value.componentMappings, (x: Vector[ComponentUsageCapabilityMapping]) => JsArray(x.map(_mapping))),
    "dependencies" -> _presence(value.dependencies, (x: Vector[ComponentUsageCapabilityDependency]) => JsArray(x.map(_dependency))),
    "grounding" -> _presence(value.grounding, (x: Vector[GroundingReference]) => JsArray(x.map(_grounding))),
    "traceability" -> _presence(value.traceability, (x: Vector[TraceabilityReference]) => JsArray(x.map(_traceability))))
  private def _mapping(value: ComponentUsageCapabilityMapping): JsObject = Json.obj(
    "target" -> Json.obj("kind" -> "COMPONENT", "identity" -> _model_id(value.target.identity), "contractVersion" -> value.target.contractVersion),
    "boundary" -> _boundary(value.boundary), "source" -> _source(value.source), "origin" -> _origin(value.origin))
  private def _dependency(value: ComponentUsageCapabilityDependency): JsObject = Json.obj(
    "target" -> Json.obj("kind" -> "COMPONENT_USAGE", "identity" -> _model_id(value.target.identity), "contractVersion" -> value.target.contractVersion),
    "boundary" -> _boundary(value.boundary), "source" -> _source(value.source), "origin" -> _origin(value.origin))
}
