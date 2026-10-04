package cozy.modeler

import scala.util.control.NonFatal
import play.api.libs.json.{JsObject, JsValue}
import CmlSemanticFoundation._
import CmlDynamicMetadata.{CausalLinkProjection, ModelReference}
import CmlStructureMetadata.AggregateBoundary

/*
 * @since   Oct.  5, 2026
 * @version Oct.  5, 2026
 */
private[cozy] object CmlCausalMetadata {
  val namespace: String = "cozy.cml.causal"
  val schemaVersion: String = "cozy.cml.causal.v1"

  final case class ActorOperations(element: ElementRecord, operations: Presence[Vector[ModelReference]])
  final case class AggregateMembership(element: ElementRecord, aggregateBoundary: Presence[AggregateBoundary])
  final case class RuleContext(element: ElementRecord, rules: Presence[Vector[ModelReference]])
  final case class CollaboratorContext(element: ElementRecord, collaborators: Presence[Vector[ModelReference]])
  final case class ModelReferences(element: ElementRecord, references: Presence[Vector[SemanticReference]])
  final case class CapabilityGap(owner: Option[ElementRecord], field: String, absence: Absent)

  sealed abstract class CausalDiagnosticKind
  case object InvalidShape extends CausalDiagnosticKind
  case object UnsupportedSchemaVersion extends CausalDiagnosticKind
  case object PublicationFailure extends CausalDiagnosticKind
  case object InvalidSupplier extends CausalDiagnosticKind
  case object ExtensionConflict extends CausalDiagnosticKind
  case object InvalidProjection extends CausalDiagnosticKind
  case object UnknownElement extends CausalDiagnosticKind
  case object UnknownLink extends CausalDiagnosticKind

  final case class CausalDiagnostic(kind: CausalDiagnosticKind, path: String, detail: String)

  final class Graph private[CmlCausalMetadata] (
    val catalog: Catalog,
    val envelope: CmlSemanticMetadata.Envelope,
    val causalLinks: Presence[Vector[CausalLinkProjection]],
    val actorOperations: Presence[Vector[ActorOperations]],
    val aggregateBoundaries: Presence[Vector[AggregateMembership]],
    val ruleContexts: Presence[Vector[RuleContext]],
    val collaboratorContexts: Presence[Vector[CollaboratorContext]],
    val modelReferences: Vector[ModelReferences],
    val capabilityGaps: Vector[CapabilityGap]
  )

  def build(envelope: CmlSemanticMetadata.Envelope): Either[Vector[CausalDiagnostic], Graph] = {
    if (envelope == null) Left(Vector(CausalDiagnostic(InvalidShape, "envelope",
      "An admitted semantic metadata envelope is required.")))
    else {
      // Re-admit publication shape without replacing the caller's Catalog instance.
      _admit_publication(envelope).flatMap { base =>
        if (base.extensions.value.contains(namespace)) Left(Vector(CausalDiagnostic(
          ExtensionConflict, "extensions", "The Causal extension namespace is already supplied by the caller.")))
        else CmlCausalMetadataProjection.admit(base).flatMap { peers =>
          val facts = CmlCausalMetadataProjection.derive(base.catalog, peers)
          val child = CmlCausalMetadataCodec.causalJson(base, peers, facts)
          CmlSemanticMetadata.build(base.catalog, base.extensions + (namespace -> child)) match {
            case Left(_) => Left(Vector(_publication_failure))
            case Right(published) => Right(new Graph(base.catalog, published, facts.causallinks,
              facts.actoroperations, facts.aggregateboundaries, facts.rulecontexts,
              facts.collaboratorcontexts, facts.modelreferences, facts.capabilitygaps))
          }
        }
      }
    }
  }

  def read(value: JsValue): Either[Vector[CausalDiagnostic], Graph] = CmlCausalMetadataCodec.read(value)

  def toJson(graph: Graph): JsObject = {
    require(graph != null, "An admitted Causal graph is required.")
    CmlSemanticMetadata.toJson(graph.envelope)
  }

  def canonicalJson(graph: Graph): String = {
    require(graph != null, "An admitted Causal graph is required.")
    CmlSemanticMetadata.canonicalJson(graph.envelope)
  }

  private[modeler] def _publication_failure: CausalDiagnostic = CausalDiagnostic(
    PublicationFailure, "publication", "The semantic metadata publication is invalid.")

  private[modeler] def _contains_null(value: JsValue): Boolean = value match {
    case null => true
    case objectvalue: JsObject => objectvalue.fields.exists { case (key, child) => key == null || _contains_null(child) }
    case play.api.libs.json.JsArray(values) => values.exists(_contains_null)
    case _ => false
  }

  private def _admit_publication(envelope: CmlSemanticMetadata.Envelope): Either[Vector[CausalDiagnostic], CmlSemanticMetadata.Envelope] = {
    try {
      CmlSemanticMetadata.build(envelope.catalog, envelope.extensions).flatMap { base =>
        val root = CmlSemanticMetadata.toJson(base)
        if (_contains_null(root)) Left(Vector(CmlSemanticMetadata.PublicationDiagnostic(
          CmlSemanticMetadata.InvalidShape, "publication", "The semantic metadata publication is invalid.", None)))
        else CmlSemanticMetadata.read(root).map(_ => base)
      }.left.map(_ => Vector(_publication_failure))
    } catch {
      case NonFatal(_) => Left(Vector(_publication_failure))
    }
  }
}
