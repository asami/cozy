package cozy.modeler

import CmlSemanticFoundation._
import CmlDynamicMetadata.{ActivityProjection, CausalLinkProjection, DynamicDiagnostic, FlowProjection,
  InvalidShape, ModelReference, StateMachineProjection, StateProjection, TransitionProjection,
  UnknownNode, UnknownProjection, WorkflowProjection}

/*
 * @since   Oct.  4, 2026
 * @version Oct.  4, 2026
 * @author  ASAMI, Tomoharu
 */
private[cozy] object CmlDynamicTopology {
  sealed abstract class LinkKind
  case object Flow extends LinkKind
  case object Transition extends LinkKind
  case object Cause extends LinkKind
  case object Reaction extends LinkKind

  final case class TopologyLink(
    element: ElementRecord,
    kind: LinkKind,
    source: Presence[ModelReference],
    target: Presence[ModelReference]
  )

  final class Topology private[CmlDynamicTopology] (
    val graph: CmlDynamicMetadata.Graph,
    val nodes: Vector[ElementRecord],
    val links: Vector[TopologyLink]
  ) {
    private val _nodes_by_id = _identity_index(nodes, (record: ElementRecord) => record)
    private val _outgoing_by_id = _link_index(links, _.source)
    private val _incoming_by_id = _link_index(links, _.target)
    private val _workflows_by_id = _identity_index(graph.workflows, (value: WorkflowProjection) => value.element)
    private val _activities_by_id = _identity_index(graph.activities, (value: ActivityProjection) => value.element)
    private val _flows_by_id = _identity_index(graph.flows, (value: FlowProjection) => value.element)
    private val _machines_by_id = _identity_index(graph.stateMachines, (value: StateMachineProjection) => value.element)
    private val _states_by_id = _identity_index(graph.states, (value: StateProjection) => value.element)
    private val _transitions_by_id = _identity_index(graph.transitions, (value: TransitionProjection) => value.element)
    private val _causal_links_by_id = _identity_index(graph.causalLinks, (value: CausalLinkProjection) => value.element)

    def node(id: ModelElementId): Either[DynamicDiagnostic, ElementRecord] = {
      if (!_valid_identity(id)) Left(_malformed("node.identity"))
      else _nodes_by_id.get(id).toRight(_unknown_node)
    }

    def outgoing(id: ModelElementId): Either[DynamicDiagnostic, Vector[TopologyLink]] = {
      node(id).map(_ => _outgoing_by_id.getOrElse(id, Vector.empty))
    }

    def incoming(id: ModelElementId): Either[DynamicDiagnostic, Vector[TopologyLink]] = {
      node(id).map(_ => _incoming_by_id.getOrElse(id, Vector.empty))
    }

    def workflow(id: ModelElementId): Either[DynamicDiagnostic, WorkflowProjection] = {
      _projection(id, "workflow", _workflows_by_id)
    }

    def activity(id: ModelElementId): Either[DynamicDiagnostic, ActivityProjection] = {
      _projection(id, "activity", _activities_by_id)
    }

    def flow(id: ModelElementId): Either[DynamicDiagnostic, FlowProjection] = {
      _projection(id, "flow", _flows_by_id)
    }

    def stateMachine(id: ModelElementId): Either[DynamicDiagnostic, StateMachineProjection] = {
      _projection(id, "stateMachine", _machines_by_id)
    }

    def state(id: ModelElementId): Either[DynamicDiagnostic, StateProjection] = {
      _projection(id, "state", _states_by_id)
    }

    def transition(id: ModelElementId): Either[DynamicDiagnostic, TransitionProjection] = {
      _projection(id, "transition", _transitions_by_id)
    }

    def causalLink(id: ModelElementId): Either[DynamicDiagnostic, CausalLinkProjection] = {
      _projection(id, "causalLink", _causal_links_by_id)
    }
  }

  def build(graph: CmlDynamicMetadata.Graph): Either[Vector[DynamicDiagnostic], Topology] = {
    if (graph == null) Left(Vector(DynamicDiagnostic(
      InvalidShape, "graph", "An admitted Dynamic graph is required.", None
    ))) else {
      val carriers = graph.flows.map(value => TopologyLink(value.element, Flow, value.source, value.target)) ++
        graph.transitions.map(value => TopologyLink(value.element, Transition, value.fromState, value.toState)) ++
        graph.causalLinks.map { value =>
          val kind = value.kind match {
            case CmlDynamicMetadata.Cause => Cause
            case CmlDynamicMetadata.Reaction => Reaction
          }
          TopologyLink(value.element, kind, value.source, value.target)
        }
      // Complete-record groups retain the carrier order of equal anonymous occurrences.
      val groups = carriers.groupBy(_.element)
      val offsets = scala.collection.mutable.Map.empty[ElementRecord, Int]
      val ordered = graph.catalog.elements.flatMap { record =>
        groups.get(record).map { occurrences =>
          val offset = offsets.getOrElse(record, 0)
          offsets.update(record, offset + 1)
          occurrences(offset)
        }
      }
      val nodes = graph.catalog.elements.filter(record => _node_kinds.contains(record.kind))
      Right(new Topology(graph, nodes, ordered))
    }
  }

  private val _node_kinds = Set("entity", "value", "aggregate", "trait", "powertype", "powertype-kind",
    "workflow", "workflow-activity", "state-machine", "state", "operation", "command", "event", "rule", "action")
  private val _unknown_node = DynamicDiagnostic(
    UnknownNode, "node.identity", "The Dynamic node identity is unknown or unaddressable.", None
  )

  private def _identity_index[A](values: Vector[A], record: A => ElementRecord): Map[ModelElementId, A] = {
    values.flatMap { value =>
      record(value).identity match {
        case Present(id) => Some(id -> value)
        case _: Absent => None
      }
    }.toMap
  }

  private def _projection[A](id: ModelElementId, name: String, index: Map[ModelElementId, A]): Either[DynamicDiagnostic, A] = {
    val path = name + ".identity"
    if (!_valid_identity(id)) Left(_malformed(path))
    else index.get(id).toRight(DynamicDiagnostic(
      UnknownProjection, path, "The Dynamic projection identity is unknown or unaddressable.", None
    ))
  }

  private def _link_index(links: Vector[TopologyLink], endpoint: TopologyLink => Presence[ModelReference]): Map[ModelElementId, Vector[TopologyLink]] = {
    links.flatMap(link => _local_identity(endpoint(link)).map(_ -> link))
      .groupBy(_._1).map { case (id, values) => id -> values.map(_._2) }
  }

  private def _local_identity(reference: Presence[ModelReference]): Option[ModelElementId] = {
    reference match {
      case Present(ModelReference(id, Local)) => Some(id)
      case _ => None
    }
  }

  private def _malformed(path: String): DynamicDiagnostic = {
    DynamicDiagnostic(InvalidShape, path, "The Dynamic query identity is invalid.", None)
  }

  private def _valid_identity(id: ModelElementId): Boolean = {
    id != null && _valid_component(id.modelId) && _valid_component(id.elementId)
  }

  private def _valid_component(value: String): Boolean = {
    value != null && value.nonEmpty && !_whitespace(value.codePointAt(0)) &&
      !_whitespace(value.codePointBefore(value.length)) && !_contains_control(value)
  }

  private def _whitespace(codepoint: Int): Boolean = {
    Character.isWhitespace(codepoint) || Character.isSpaceChar(codepoint)
  }

  private def _contains_control(value: String): Boolean = {
    var offset = 0
    var result = false
    while (offset < value.length && !result) {
      val codepoint = value.codePointAt(offset)
      result = Character.isISOControl(codepoint)
      offset += Character.charCount(codepoint)
    }
    result
  }
}
