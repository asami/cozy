package cozy.modeler

import CmlSemanticFoundation._
import CmlCausalMetadata._
import CmlDynamicMetadata.{CausalLinkProjection, ModelReference}

/*
 * @since   Oct.  5, 2026
 * @version Oct.  5, 2026
 */
private[cozy] object CmlCausalTraversal {
  sealed abstract class LinkKind
  case object Cause extends LinkKind
  case object Reaction extends LinkKind
  case object ActorOperation extends LinkKind
  case object AffectedSubject extends LinkKind
  case object RuleInput extends LinkKind
  case object RuleOutput extends LinkKind
  case object WorkflowRule extends LinkKind
  case object AggregateMember extends LinkKind
  case object Collaborator extends LinkKind
  case object ModelReferenceLink extends LinkKind

  final case class TraversalLink(
    owner: ElementRecord,
    kind: LinkKind,
    source: Presence[ModelReference],
    target: Presence[ModelReference],
    sourceAttribution: SourceAttribution,
    origin: ReferenceOrigin,
    evidenceField: String,
    evidenceIndex: Int,
    semanticReference: Option[SemanticReference]
  )

  sealed abstract class ReferenceTarget
  final case class LocalTarget(link: TraversalLink, element: ElementRecord) extends ReferenceTarget
  final case class ExternalTarget(link: TraversalLink) extends ReferenceTarget
  final case class AbsentTarget(link: TraversalLink, absence: Absent) extends ReferenceTarget

  final class Traversal private[CmlCausalTraversal] (
    val graph: Graph,
    val allLinks: Vector[TraversalLink],
    private val _nodes_by_id: Map[ModelElementId, ElementRecord],
    private val _outgoing: Map[ModelElementId, Vector[TraversalLink]],
    private val _incoming: Map[ModelElementId, Vector[TraversalLink]]
  ) {
    val nodes: Vector[ElementRecord] = graph.catalog.elements

    def node(id: ModelElementId): Either[CausalDiagnostic, ElementRecord] = _node(id, "node")

    def outgoing(id: ModelElementId): Either[CausalDiagnostic, Vector[TraversalLink]] =
      _node(id, "outgoing").map(_ => _outgoing.getOrElse(id, Vector.empty))

    def incoming(id: ModelElementId): Either[CausalDiagnostic, Vector[TraversalLink]] =
      _node(id, "incoming").map(_ => _incoming.getOrElse(id, Vector.empty))

    def causes(id: ModelElementId): Either[CausalDiagnostic, Presence[Vector[TraversalLink]]] =
      _node(id, "causes").map(_ => _causal(_incoming.getOrElse(id, Vector.empty)))

    def consequences(id: ModelElementId): Either[CausalDiagnostic, Presence[Vector[TraversalLink]]] =
      _node(id, "consequences").map(_ => _causal(_outgoing.getOrElse(id, Vector.empty)))

    def affecting(id: ModelElementId): Either[CausalDiagnostic, Presence[Vector[CausalLinkProjection]]] =
      _node(id, "affecting").map { _ =>
        graph.causalLinks match {
          case absence: Absent => absence
          case Present(carriers) => Present(carriers.filter { carrier =>
            _values(carrier.affectedElements).exists(reference => reference.boundary == Local && reference.identity == id)
          })
        }
      }

    def resolve(link: TraversalLink): Either[CausalDiagnostic, ReferenceTarget] = {
      if (link == null) Left(CausalDiagnostic(InvalidShape, "resolve.link", "The Causal traversal link is invalid."))
      // Equality avoids hashing or dereferencing any untrusted nested value.
      else if (!allLinks.contains(link)) Left(CausalDiagnostic(UnknownLink, "resolve.link",
        "The Causal traversal link is not admitted by this graph."))
      else link.target match {
        case Present(ModelReference(identity, Local)) => Right(LocalTarget(link, _nodes_by_id(identity)))
        case Present(ModelReference(_, External)) => Right(ExternalTarget(link))
        case absence: Absent => Right(AbsentTarget(link, absence))
      }
    }

    private def _node(id: ModelElementId, api: String): Either[CausalDiagnostic, ElementRecord] = {
      val path = api + ".identity"
      if (id == null || !_valid_text(id.modelId) || !_valid_text(id.elementId))
        Left(CausalDiagnostic(InvalidShape, path, "The Causal query identity is invalid."))
      else _nodes_by_id.get(id).toRight(CausalDiagnostic(UnknownElement, path,
        "The Causal element identity is unknown or unaddressable."))
    }

    private def _causal(links: Vector[TraversalLink]): Presence[Vector[TraversalLink]] = graph.causalLinks match {
      case absence: Absent => absence
      case Present(_) => Present(links.filter(link => link.kind == Cause || link.kind == Reaction))
    }
  }

  def build(graph: Graph): Either[Vector[CausalDiagnostic], Traversal] = {
    if (graph == null) Left(Vector(CausalDiagnostic(InvalidShape, "graph", "An admitted Causal graph is required.")))
    else {
      val nodes = graph.catalog.elements.collect { case record if record.identity.isInstanceOf[Present[_]] =>
        record.identity.asInstanceOf[Present[ModelElementId]].value -> record
      }.toMap
      val links = _links(graph)
      Right(new Traversal(graph, links, nodes, _index(links, _.source), _index(links, _.target)))
    }
  }

  private def _links(graph: Graph): Vector[TraversalLink] = {
    // Each family has its own full-record occurrence cursor, consumed only here.
    var causal = _values(graph.causalLinks)
    var actors = _values(graph.actorOperations)
    var aggregates = _values(graph.aggregateBoundaries)
    var rules = _values(graph.ruleContexts)
    var collaborators = _values(graph.collaboratorContexts)
    var references = graph.modelReferences
    def _take_[A](values: Vector[A], owner: ElementRecord, element: A => ElementRecord): (Vector[A], Vector[A]) = {
      val index = values.indexWhere(value => element(value) == owner)
      if (index < 0) (Vector.empty, values)
      else (Vector(values(index)), values.patch(index, Vector.empty[A], 1))
    }
    graph.catalog.elements.flatMap { owner =>
      val (causalvalues, causalrest) = _take_(causal, owner, (value: CausalLinkProjection) => value.element)
      causal = causalrest
      val (actorvalues, actorrest) = _take_(actors, owner, (value: ActorOperations) => value.element)
      actors = actorrest
      val (aggregatevalues, aggregaterest) = _take_(aggregates, owner, (value: AggregateMembership) => value.element)
      aggregates = aggregaterest
      val (rulevalues, rulerest) = _take_(rules, owner, (value: RuleContext) => value.element)
      rules = rulerest
      val (collaboratorvalues, collaboratorrest) = _take_(collaborators, owner, (value: CollaboratorContext) => value.element)
      collaborators = collaboratorrest
      val (referencevalues, referencerest) = _take_(references, owner, (value: ModelReferences) => value.element)
      references = referencerest
      val source = _owner_source(owner)
      causalvalues.flatMap(_causal_links) ++
        actorvalues.flatMap(value => _values(value.operations).zipWithIndex.map { case (target, index) =>
          _context(owner, ActorOperation, source, Present(target), "actor-operation", "actorOperations.operations", index)
        }) ++ aggregatevalues.map { value =>
          val target = value.aggregateBoundary match {
            case Present(boundary) => boundary.aggregate match {
              case Present(reference) => Present(ModelReference(reference.identity, reference.boundary))
              case absence: Absent => absence
            }
            case absence: Absent => absence
          }
          _context(owner, AggregateMember, source, target, "aggregate-member", "aggregateBoundaries.aggregate", 0)
        } ++ rulevalues.flatMap(value => _values(value.rules).zipWithIndex.map { case (target, index) =>
          _context(owner, WorkflowRule, source, Present(target), "workflow-rule", "ruleContexts.rules", index)
        }) ++ collaboratorvalues.flatMap(value => _values(value.collaborators).zipWithIndex.map { case (target, index) =>
          _context(owner, Collaborator, source, Present(target), "collaborator", "collaboratorContexts.collaborators", index)
        }) ++ referencevalues.flatMap(value => _values(value.references).zipWithIndex.map { case (reference, index) =>
          val ModelTarget(identity) = reference.target
          TraversalLink(owner, ModelReferenceLink, source, Present(ModelReference(identity, reference.boundary)),
            reference.source, reference.origin, "modelReferences.references", index, Some(reference))
        })
    }
  }

  private def _causal_links(carrier: CausalLinkProjection): Vector[TraversalLink] = {
    val owner = carrier.element
    val kind = if (carrier.kind == CmlDynamicMetadata.Cause) Cause else Reaction
    val direct = TraversalLink(owner, kind, carrier.source, carrier.target, owner.source, Declared, "causalLinks", 0, None)
    val affected = _values(carrier.affectedElements).zipWithIndex.map { case (target, index) =>
      _context(owner, AffectedSubject, carrier.source, Present(target), "affected-subject", "causalLinks.affectedElements", index)
    }
    val rule = carrier.rule match {
      case Present(reference) => Vector(
        _context(owner, RuleInput, carrier.source, Present(reference), "rule-input", "causalLinks.rule", 0),
        _context(owner, RuleOutput, Present(reference), carrier.target, "rule-output", "causalLinks.rule", 1))
      case _: Absent => Vector.empty
    }
    Vector(direct) ++ affected ++ rule
  }

  private def _context(owner: ElementRecord, kind: LinkKind, source: Presence[ModelReference], target: Presence[ModelReference],
    token: String, field: String, index: Int): TraversalLink =
    TraversalLink(owner, kind, source, target, owner.source,
      Derived("cozy.cml.causal." + token + ".v1", Vector(owner.source)), field, index, None)

  private def _owner_source(owner: ElementRecord): Presence[ModelReference] = owner.identity match {
    case Present(identity) => Present(ModelReference(identity, Local))
    case absence: Absent => absence
  }

  private def _values[A](presence: Presence[Vector[A]]): Vector[A] = presence match {
    case Present(values) => values
    case _: Absent => Vector.empty
  }

  private def _index(links: Vector[TraversalLink], endpoint: TraversalLink => Presence[ModelReference]): Map[ModelElementId, Vector[TraversalLink]] =
    links.foldLeft(Map.empty[ModelElementId, Vector[TraversalLink]]) { (index, link) =>
      endpoint(link) match {
        case Present(ModelReference(identity, Local)) => index.updated(identity, index.getOrElse(identity, Vector.empty) :+ link)
        case _ => index
      }
    }

  private def _valid_text(text: String): Boolean = {
    def _whitespace_(codepoint: Int): Boolean = Character.isWhitespace(codepoint) || Character.isSpaceChar(codepoint)
    if (text == null || text.isEmpty || _whitespace_(text.codePointAt(0)) || _whitespace_(text.codePointBefore(text.length))) false
    else {
      var offset = 0
      var control = false
      while (offset < text.length && !control) {
        val codepoint = text.codePointAt(offset)
        control = Character.isISOControl(codepoint)
        offset += Character.charCount(codepoint)
      }
      !control
    }
  }
}
