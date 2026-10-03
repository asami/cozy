package cozy.modeler

import play.api.libs.json.{JsObject, JsValue}

import CmlSemanticFoundation._

/*
 * @since   Oct.  4, 2026
 * @version Oct.  4, 2026
 * @author  ASAMI, Tomoharu
 */
private[cozy] object CmlDynamicMetadata {
  val namespace: String = "cozy.cml.dynamic"
  val schemaVersion: String = "cozy.cml.dynamic.v1"

  final case class ModelReference(identity: ModelElementId, boundary: ReferenceBoundary)
  final case class DeclaredExpression(text: String, references: Presence[Vector[ModelReference]])

  sealed abstract class ActivityKind
  case object Action extends ActivityKind
  case object Branch extends ActivityKind
  case object Merge extends ActivityKind
  case object Fork extends ActivityKind
  case object Join extends ActivityKind
  case object Start extends ActivityKind
  case object End extends ActivityKind

  sealed abstract class CausalKind
  case object Cause extends CausalKind
  case object Reaction extends CausalKind

  final case class WorkflowProjection(
    element: ElementRecord,
    purpose: Presence[String],
    activities: Presence[Vector[ModelReference]],
    flows: Presence[Vector[ModelReference]],
    participants: Presence[Vector[ModelReference]],
    affectedElements: Presence[Vector[ModelReference]],
    stateMachines: Presence[Vector[ModelReference]],
    operations: Presence[Vector[ModelReference]],
    events: Presence[Vector[ModelReference]],
    rules: Presence[Vector[ModelReference]],
    stateEffects: Presence[Vector[ModelReference]]
  )

  final case class ActivityProjection(
    element: ElementRecord,
    workflow: Presence[ModelReference],
    activityKind: Presence[ActivityKind],
    participants: Presence[Vector[ModelReference]],
    affectedElements: Presence[Vector[ModelReference]],
    operations: Presence[Vector[ModelReference]],
    events: Presence[Vector[ModelReference]],
    rules: Presence[Vector[ModelReference]],
    stateEffects: Presence[Vector[ModelReference]]
  )

  final case class FlowProjection(
    element: ElementRecord,
    workflow: Presence[ModelReference],
    source: Presence[ModelReference],
    target: Presence[ModelReference],
    guard: Presence[DeclaredExpression]
  )

  final case class StateMachineProjection(
    element: ElementRecord,
    owner: Presence[ModelReference],
    affectedElements: Presence[Vector[ModelReference]],
    states: Presence[Vector[ModelReference]],
    transitions: Presence[Vector[ModelReference]],
    workflows: Presence[Vector[ModelReference]]
  )

  final case class StateProjection(
    element: ElementRecord,
    stateMachine: Presence[ModelReference],
    entryActions: Presence[Vector[DeclaredExpression]],
    exitActions: Presence[Vector[DeclaredExpression]]
  )

  final case class TransitionProjection(
    element: ElementRecord,
    stateMachine: Presence[ModelReference],
    fromState: Presence[ModelReference],
    toState: Presence[ModelReference],
    triggers: Presence[Vector[DeclaredExpression]],
    guard: Presence[DeclaredExpression],
    actions: Presence[Vector[DeclaredExpression]],
    activities: Presence[Vector[ModelReference]],
    workflows: Presence[Vector[ModelReference]],
    affectedElements: Presence[Vector[ModelReference]],
    stateEffects: Presence[Vector[ModelReference]]
  )

  final case class CausalLinkProjection(
    element: ElementRecord,
    kind: CausalKind,
    source: Presence[ModelReference],
    target: Presence[ModelReference],
    rule: Presence[ModelReference],
    affectedElements: Presence[Vector[ModelReference]]
  )

  sealed abstract class DynamicDiagnosticKind
  case object InvalidShape extends DynamicDiagnosticKind
  case object UnsupportedSchemaVersion extends DynamicDiagnosticKind
  case object InvalidCoreBinding extends DynamicDiagnosticKind
  case object DuplicateProjection extends DynamicDiagnosticKind
  case object MissingProjection extends DynamicDiagnosticKind
  case object InvalidDynamicSemantics extends DynamicDiagnosticKind
  case object PublicationFailure extends DynamicDiagnosticKind
  case object ExtensionConflict extends DynamicDiagnosticKind
  case object UnknownNode extends DynamicDiagnosticKind
  case object UnknownProjection extends DynamicDiagnosticKind

  final case class DynamicDiagnostic(
    kind: DynamicDiagnosticKind,
    path: String,
    detail: String,
    publication: Option[CmlSemanticMetadata.PublicationDiagnostic]
  )

  final class Graph private[CmlDynamicMetadata] (
    val catalog: Catalog,
    val envelope: CmlSemanticMetadata.Envelope,
    val workflows: Vector[WorkflowProjection],
    val activities: Vector[ActivityProjection],
    val flows: Vector[FlowProjection],
    val stateMachines: Vector[StateMachineProjection],
    val states: Vector[StateProjection],
    val transitions: Vector[TransitionProjection],
    val causalLinks: Vector[CausalLinkProjection]
  )

  def build(
    catalog: Catalog,
    workflows: Vector[WorkflowProjection],
    activities: Vector[ActivityProjection],
    flows: Vector[FlowProjection],
    stateMachines: Vector[StateMachineProjection],
    states: Vector[StateProjection],
    transitions: Vector[TransitionProjection],
    causalLinks: Vector[CausalLinkProjection],
    extensions: JsObject
  ): Either[Vector[DynamicDiagnostic], Graph] = {
    CmlSemanticMetadata.build(catalog, extensions) match {
      case Left(diagnostics) => Left(diagnostics.map(_publication_failure))
      case Right(_) if extensions.value.contains(namespace) => Left(Vector(_diagnostic(
        ExtensionConflict, "extensions", "The Dynamic extension namespace is already supplied by the caller."
      )))
      case Right(base) =>
        CmlDynamicMetadataValidation.validate(catalog, workflows, activities, flows, stateMachines,
          states, transitions, causalLinks).flatMap { _ =>
          val child = CmlDynamicMetadataCodec.dynamicJson(base, workflows, activities, flows,
            stateMachines, states, transitions, causalLinks)
          CmlSemanticMetadata.build(catalog, extensions + (namespace -> child)) match {
            case Left(diagnostics) => Left(diagnostics.map(_publication_failure))
            case Right(envelope) => Right(new Graph(catalog, envelope, workflows, activities, flows,
              stateMachines, states, transitions, causalLinks))
          }
        }
    }
  }

  def toJson(graph: Graph): JsObject = {
    require(graph != null, "An admitted Dynamic graph is required.")
    CmlSemanticMetadata.toJson(graph.envelope)
  }

  def canonicalJson(graph: Graph): String = {
    require(graph != null, "An admitted Dynamic graph is required.")
    CmlSemanticMetadata.canonicalJson(graph.envelope)
  }

  def read(value: JsValue): Either[Vector[DynamicDiagnostic], Graph] = {
    CmlDynamicMetadataCodec.read(value)
  }

  private[modeler] def _diagnostic(kind: DynamicDiagnosticKind, path: String, detail: String): DynamicDiagnostic = {
    DynamicDiagnostic(kind, path, detail, None)
  }

  private[modeler] def _publication_failure(value: CmlSemanticMetadata.PublicationDiagnostic): DynamicDiagnostic = {
    DynamicDiagnostic(PublicationFailure, value.path, "The foundation metadata publication is invalid.", Some(value))
  }
}
