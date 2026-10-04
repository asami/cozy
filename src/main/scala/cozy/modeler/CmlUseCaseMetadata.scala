package cozy.modeler

import play.api.libs.json.{JsObject, JsValue}

import CmlSemanticFoundation._
import CmlDynamicMetadata.{DeclaredExpression, ModelReference}

/*
 * @since   Oct.  4, 2026
 * @version Oct.  4, 2026
 * @author  ASAMI, Tomoharu
 */
private[cozy] object CmlUseCaseMetadata {
  val namespace: String = "cozy.cml.use-case"
  val schemaVersion: String = "cozy.cml.use-case.v1"

  sealed abstract class ActorRole
  case object Actor extends ActorRole
  case object Primary extends ActorRole
  case object Secondary extends ActorRole
  case object Supporting extends ActorRole
  case object Stakeholder extends ActorRole

  sealed abstract class FlowKind
  case object MainFlow extends FlowKind
  case object AlternateFlow extends FlowKind
  case object ExceptionFlow extends FlowKind

  final case class ActorParticipation(role: ActorRole, target: ModelReference)

  final case class UseCaseFlow(
    kind: FlowKind,
    name: Presence[String],
    summary: Presence[String],
    description: Presence[String],
    condition: Presence[DeclaredExpression],
    steps: Presence[Vector[DeclaredExpression]],
    alternates: Presence[Vector[DeclaredExpression]],
    exceptions: Presence[Vector[DeclaredExpression]]
  )

  final case class ActorProjection(
    element: ElementRecord,
    actorKind: Presence[String],
    summary: Presence[String],
    description: Presence[String],
    useCases: Presence[Vector[ModelReference]],
    workflows: Presence[Vector[ModelReference]],
    operations: Presence[Vector[ModelReference]],
    events: Presence[Vector[ModelReference]],
    domainElements: Presence[Vector[ModelReference]]
  )

  final case class UseCaseProjection(
    element: ElementRecord,
    declaredId: Presence[String],
    summary: Presence[String],
    description: Presence[String],
    goal: Presence[String],
    trigger: Presence[DeclaredExpression],
    preconditions: Presence[Vector[DeclaredExpression]],
    postconditions: Presence[Vector[DeclaredExpression]],
    priority: Presence[String],
    lifecycleStatus: Presence[String],
    actorReferences: Presence[Vector[ActorParticipation]],
    flows: Presence[Vector[UseCaseFlow]],
    domainElements: Presence[Vector[ModelReference]],
    operations: Presence[Vector[ModelReference]],
    events: Presence[Vector[ModelReference]],
    collaborators: Presence[Vector[ModelReference]],
    realizingWorkflows: Presence[Vector[ModelReference]]
  )

  sealed abstract class UseCaseDiagnosticKind
  case object InvalidShape extends UseCaseDiagnosticKind
  case object UnsupportedSchemaVersion extends UseCaseDiagnosticKind
  case object InvalidCoreBinding extends UseCaseDiagnosticKind
  case object DuplicateProjection extends UseCaseDiagnosticKind
  case object MissingProjection extends UseCaseDiagnosticKind
  case object InvalidUseCaseSemantics extends UseCaseDiagnosticKind
  case object PublicationFailure extends UseCaseDiagnosticKind
  case object ExtensionConflict extends UseCaseDiagnosticKind
  case object UnknownElement extends UseCaseDiagnosticKind
  case object UnknownProjection extends UseCaseDiagnosticKind

  final case class UseCaseDiagnostic(
    kind: UseCaseDiagnosticKind,
    path: String,
    detail: String,
    publication: Option[CmlSemanticMetadata.PublicationDiagnostic]
  )

  final class Graph private[CmlUseCaseMetadata] (
    val catalog: Catalog,
    val envelope: CmlSemanticMetadata.Envelope,
    val actors: Vector[ActorProjection],
    val useCases: Vector[UseCaseProjection]
  )

  def build(
    catalog: Catalog,
    actors: Vector[ActorProjection],
    useCases: Vector[UseCaseProjection],
    extensions: JsObject
  ): Either[Vector[UseCaseDiagnostic], Graph] = {
    CmlSemanticMetadata.build(catalog, extensions) match {
      case Left(diagnostics) => Left(diagnostics.map(_publication_failure))
      case Right(_) if extensions.value.contains(namespace) => Left(Vector(_diagnostic(
        ExtensionConflict, "extensions", "The Use Case extension namespace is already supplied by the caller."
      )))
      case Right(base) => CmlUseCaseMetadataValidation.validate(catalog, actors, useCases).flatMap { _ =>
        val child = CmlUseCaseMetadataCodec.useCaseJson(base, actors, useCases)
        CmlSemanticMetadata.build(catalog, extensions + (namespace -> child)) match {
          case Left(diagnostics) => Left(diagnostics.map(_publication_failure))
          case Right(envelope) => Right(new Graph(catalog, envelope, actors, useCases))
        }
      }
    }
  }

  def read(value: JsValue): Either[Vector[UseCaseDiagnostic], Graph] = {
    CmlUseCaseMetadataCodec.read(value)
  }

  def toJson(graph: Graph): JsObject = {
    require(graph != null, "An admitted Use Case graph is required.")
    CmlSemanticMetadata.toJson(graph.envelope)
  }

  def canonicalJson(graph: Graph): String = {
    require(graph != null, "An admitted Use Case graph is required.")
    CmlSemanticMetadata.canonicalJson(graph.envelope)
  }

  private[modeler] def _diagnostic(kind: UseCaseDiagnosticKind, path: String, detail: String): UseCaseDiagnostic = {
    UseCaseDiagnostic(kind, path, detail, None)
  }

  private[modeler] def _publication_failure(value: CmlSemanticMetadata.PublicationDiagnostic): UseCaseDiagnostic = {
    UseCaseDiagnostic(PublicationFailure, value.path, "The foundation metadata publication is invalid.", Some(value))
  }
}
