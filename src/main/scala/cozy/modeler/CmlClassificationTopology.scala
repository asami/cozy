package cozy.modeler

import CmlSemanticFoundation._
import CmlClassificationMetadata.{ClassificationDiagnostic, ModelReference, PowertypeClassificationProjection, InvalidShape, UnknownNode, UnknownDimension}

/*
 * @since   Oct. 3, 2026
 * @version Oct.  3, 2026
 * @author  ASAMI, Tomoharu
 */
private[cozy] object CmlClassificationTopology {
  sealed abstract class LinkKind
  case object Generalization extends LinkKind
  case object TraitRealization extends LinkKind
  case object PowertypeClassification extends LinkKind

  final case class TopologyLink(
    element: ElementRecord,
    kind: LinkKind,
    source: Presence[ModelReference],
    target: Presence[ModelReference]
  )

  final class Topology private[CmlClassificationTopology] (
    val graph: CmlClassificationMetadata.Graph,
    val nodes: Vector[ElementRecord],
    val links: Vector[TopologyLink],
    private val _dimensions: Vector[PowertypeClassificationProjection]
  ) {
    private val _nodes_by_id: Map[ModelElementId, ElementRecord] = nodes.flatMap { record =>
      record.identity match {
        case Present(id) => Some(id -> record)
        case _: Absent => None
      }
    }.toMap
    private val _outgoing_by_id: Map[ModelElementId, Vector[TopologyLink]] =
      _link_index(links, _.source)
    private val _incoming_by_id: Map[ModelElementId, Vector[TopologyLink]] =
      _link_index(links, _.target)
    private val _dimensions_by_subject: Map[ModelElementId, Vector[PowertypeClassificationProjection]] =
      _dimensions.flatMap(value => _local_identity(value.subject).map(_ -> value))
        .groupBy(_._1).map { case (id, values) => id -> values.map(_._2) }
    private val _dimensions_by_id: Map[ModelElementId, PowertypeClassificationProjection] =
      _dimensions.flatMap { value =>
        value.element.identity match {
          case Present(id) => Some(id -> value)
          case _: Absent => None
        }
      }.toMap

    def node(id: ModelElementId): Either[ClassificationDiagnostic, ElementRecord] =
      if (!_valid_identity(id)) Left(_malformed("node.identity"))
      else _nodes_by_id.get(id).toRight(_unknown_node)

    def outgoing(id: ModelElementId): Either[ClassificationDiagnostic, Vector[TopologyLink]] =
      node(id).map(_ => _outgoing_by_id.getOrElse(id, Vector.empty))

    def incoming(id: ModelElementId): Either[ClassificationDiagnostic, Vector[TopologyLink]] =
      node(id).map(_ => _incoming_by_id.getOrElse(id, Vector.empty))

    def dimensions(id: ModelElementId): Either[ClassificationDiagnostic, Vector[PowertypeClassificationProjection]] =
      node(id).map(_ => _dimensions_by_subject.getOrElse(id, Vector.empty))

    def dimension(id: ModelElementId): Either[ClassificationDiagnostic, PowertypeClassificationProjection] =
      if (!_valid_identity(id)) Left(_malformed("dimension.identity"))
      else _dimensions_by_id.get(id).toRight(ClassificationDiagnostic(
        UnknownDimension, "dimension.identity", "The Classification dimension identity is unknown or unaddressable.", None
      ))
  }

  def build(graph: CmlClassificationMetadata.Graph): Either[Vector[ClassificationDiagnostic], Topology] =
    if (graph == null) Left(Vector(ClassificationDiagnostic(
      InvalidShape, "graph", "An admitted Classification graph is required.", None
    ))) else {
      val carriers: Vector[(TopologyLink, Option[PowertypeClassificationProjection])] =
        graph.generalizations.map(value => (TopologyLink(value.element, Generalization, value.subtype, value.supertype), None)) ++
          graph.traitRealizations.map(value => (TopologyLink(value.element, TraitRealization, value.subject, value.traitTarget), None)) ++
          graph.powertypeClassifications.map(value => (TopologyLink(value.element, PowertypeClassification, value.subject, value.powertype), Some(value)))
      val groups = carriers.groupBy(_._1.element)
      val offsets = scala.collection.mutable.Map.empty[ElementRecord, Int]
      val ordered = graph.catalog.elements.flatMap { record =>
        groups.get(record).map { occurrences =>
          val offset = offsets.getOrElse(record, 0)
          offsets.update(record, offset + 1)
          occurrences(offset)
        }
      }
      val nodes = graph.catalog.elements.filter(record => _node_kinds.contains(record.kind))
      Right(new Topology(graph, nodes, ordered.map(_._1), ordered.flatMap(_._2)))
    }

  private val _node_kinds: Set[String] = Set("entity", "value", "aggregate", "trait", "powertype", "powertype-kind")
  private val _unknown_node: ClassificationDiagnostic = ClassificationDiagnostic(
    UnknownNode, "node.identity", "The Classification node identity is unknown or unaddressable.", None
  )

  private def _link_index(links: Vector[TopologyLink], endpoint: TopologyLink => Presence[ModelReference]): Map[ModelElementId, Vector[TopologyLink]] =
    links.flatMap(link => _local_identity(endpoint(link)).map(_ -> link))
      .groupBy(_._1).map { case (id, values) => id -> values.map(_._2) }

  private def _local_identity(reference: Presence[ModelReference]): Option[ModelElementId] = reference match {
    case Present(ModelReference(id, Local)) => Some(id)
    case _ => None
  }

  private def _malformed(path: String): ClassificationDiagnostic =
    ClassificationDiagnostic(InvalidShape, path, "The Classification query identity is invalid.", None)

  private def _valid_identity(id: ModelElementId): Boolean =
    id != null && _valid_component(id.modelId) && _valid_component(id.elementId)

  private def _valid_component(value: String): Boolean =
    value != null && value.nonEmpty && !_whitespace(value.codePointAt(0)) &&
      !_whitespace(value.codePointBefore(value.length)) && !_contains_control(value)

  private def _whitespace(codepoint: Int): Boolean =
    Character.isWhitespace(codepoint) || Character.isSpaceChar(codepoint)

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
