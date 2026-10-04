package cozy.modeler

import scala.util.control.NonFatal
import CmlSemanticFoundation._
import CmlCausalMetadata._

/*
 * @since   Oct.  5, 2026
 * @version Oct.  5, 2026
 */
private[cozy] object CmlCausalMetadataProjection {
  final case class Peers(
    dynamic: Option[CmlDynamicMetadata.Graph],
    usecase: Option[CmlUseCaseMetadata.Graph],
    structure: Option[CmlStructureMetadata.Graph]
  )

  final case class Facts(
    causallinks: Presence[Vector[CmlDynamicMetadata.CausalLinkProjection]],
    actoroperations: Presence[Vector[ActorOperations]],
    aggregateboundaries: Presence[Vector[AggregateMembership]],
    rulecontexts: Presence[Vector[RuleContext]],
    collaboratorcontexts: Presence[Vector[CollaboratorContext]],
    modelreferences: Vector[ModelReferences],
    capabilitygaps: Vector[CapabilityGap]
  )

  def admit(envelope: CmlSemanticMetadata.Envelope): Either[Vector[CausalDiagnostic], Peers] = {
    val root = CmlSemanticMetadata.toJson(envelope)
    for {
      dynamic <- _peer(envelope, CmlDynamicMetadata.namespace, "Dynamic", () => CmlDynamicMetadata.read(root))
      usecase <- _peer(envelope, CmlUseCaseMetadata.namespace, "Use Case", () => CmlUseCaseMetadata.read(root))
      structure <- _peer(envelope, CmlStructureMetadata.namespace, "Structure", () => CmlStructureMetadata.read(root))
    } yield Peers(dynamic, usecase, structure)
  }

  // Consume each matching full-record occurrence exactly once; anonymous payloads stay ordered.
  def ordered[A](catalog: Catalog, values: Vector[A], element: A => ElementRecord): Vector[A] = {
    catalog.elements.foldLeft((Vector.empty[A], values)) { case ((orderedvalues, remaining), record) =>
      val index = remaining.indexWhere(value => element(value) == record)
      if (index < 0) (orderedvalues, remaining)
      else (orderedvalues :+ remaining(index), remaining.patch(index, Vector.empty[A], 1))
    }._1
  }

  def derive(catalog: Catalog, peers: Peers): Facts = {
    val links = peers.dynamic.fold[Presence[Vector[CmlDynamicMetadata.CausalLinkProjection]]](_missing("Dynamic")) { graph =>
      Present(ordered(catalog, graph.causalLinks, (value: CmlDynamicMetadata.CausalLinkProjection) => value.element))
    }
    val actors = peers.usecase.fold[Presence[Vector[ActorOperations]]](_missing("Use Case")) { graph =>
      Present(ordered(catalog, graph.actors.map(value => ActorOperations(value.element, value.operations)),
        (value: ActorOperations) => value.element))
    }
    val aggregates = peers.structure.fold[Presence[Vector[AggregateMembership]]](_missing("Structure")) { graph =>
      Present(ordered(catalog, graph.elements.map(value => AggregateMembership(value.element, value.aggregateBoundary)),
        (value: AggregateMembership) => value.element))
    }
    val rules = peers.dynamic.fold[Presence[Vector[RuleContext]]](_missing("Dynamic")) { graph =>
      val values = graph.workflows.map(value => RuleContext(value.element, value.rules)) ++
        graph.activities.map(value => RuleContext(value.element, value.rules))
      Present(ordered(catalog, values, (value: RuleContext) => value.element))
    }
    val collaborators = peers.usecase.fold[Presence[Vector[CollaboratorContext]]](_missing("Use Case")) { graph =>
      Present(ordered(catalog, graph.useCases.map(value => CollaboratorContext(value.element, value.collaborators)),
        (value: CollaboratorContext) => value.element))
    }
    val references = catalog.elements.map { record =>
      val filtered = record.references match {
        case Present(values) => Present(values.filter(_.target.isInstanceOf[ModelTarget]))
        case absence: Absent => absence
      }
      ModelReferences(record, filtered)
    }
    val gaps = _gaps(catalog, links, actors, aggregates, rules, collaborators)
    Facts(links, actors, aggregates, rules, collaborators, references, gaps)
  }

  private def _peer[A, D](envelope: CmlSemanticMetadata.Envelope, namespace: String, label: String,
    read: () => Either[Vector[D], A]): Either[Vector[CausalDiagnostic], Option[A]] = {
    if (!envelope.extensions.value.contains(namespace)) Right(None)
    else {
      val failure = Vector(CausalDiagnostic(InvalidSupplier, "extensions." + namespace,
        "The supplied " + label + " metadata is not admitted."))
      try read().map(Some(_)).left.map(_ => failure)
      catch { case NonFatal(_) => Left(failure) }
    }
  }

  private def _missing(label: String): Absent = Absent(NotDeclared, "No admitted " + label + " metadata is supplied.")

  private def _gap(owner: Option[ElementRecord], field: String, value: Presence[_]): Vector[CapabilityGap] = value match {
    case absence: Absent => Vector(CapabilityGap(owner, field, absence))
    case Present(_) => Vector.empty
  }

  private def _values[A](value: Presence[Vector[A]]): Vector[A] = value match {
    case Present(values) => values
    case _: Absent => Vector.empty
  }

  private def _gaps(catalog: Catalog,
    links: Presence[Vector[CmlDynamicMetadata.CausalLinkProjection]],
    actors: Presence[Vector[ActorOperations]], aggregates: Presence[Vector[AggregateMembership]],
    rules: Presence[Vector[RuleContext]], collaborators: Presence[Vector[CollaboratorContext]]): Vector[CapabilityGap] = {
    val families = _gap(None, "causalLinks", links) ++ _gap(None, "actorOperations", actors) ++
      _gap(None, "aggregateBoundaries", aggregates) ++ _gap(None, "ruleContexts", rules) ++
      _gap(None, "collaboratorContexts", collaborators)
    val core = catalog.elements.flatMap(record => _gap(Some(record), "identity", record.identity) ++
      _gap(Some(record), "modelReferences.references", record.references))
    val causal = _values(links).flatMap(value => _gap(Some(value.element), "causalLinks.source", value.source) ++
      _gap(Some(value.element), "causalLinks.target", value.target) ++
      _gap(Some(value.element), "causalLinks.rule", value.rule) ++
      _gap(Some(value.element), "causalLinks.affectedElements", value.affectedElements))
    val actor = _values(actors).flatMap(value => _gap(Some(value.element), "actorOperations.operations", value.operations))
    val aggregate = _values(aggregates).flatMap { value =>
      _gap(Some(value.element), "aggregateBoundaries.aggregateBoundary", value.aggregateBoundary) ++
        (value.aggregateBoundary match {
          case Present(boundary) => _gap(Some(value.element), "aggregateBoundaries.aggregate", boundary.aggregate) ++
            _gap(Some(value.element), "aggregateBoundaries.membership", boundary.membership)
          case _: Absent => Vector.empty
        })
    }
    val rule = _values(rules).flatMap(value => _gap(Some(value.element), "ruleContexts.rules", value.rules))
    val collaborator = _values(collaborators).flatMap(value => _gap(Some(value.element), "collaboratorContexts.collaborators", value.collaborators))
    families ++ core ++ causal ++ actor ++ aggregate ++ rule ++ collaborator :+
      CapabilityGap(None, "causalInference", Absent(Unsupported,
        "No causation is inferred from names, membership, generic relations or expression text."))
  }
}
