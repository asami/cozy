package cozy.modeler

/*
 * @since   Oct. 27, 2026
 * @version Oct. 27, 2026
 */
object CmlModelEditModel {
  val schemaVersion = "cozy.cml.model-edit.v1"
  val publicationVersion = "cozy.cml.model-edit-production.v1"
  val candidateVersion = "cozy.cml.model-edit-candidate.v1"
  val candidateV2Version = "cozy.cml.model-edit-candidate.v2"

  sealed abstract class ElementKind(val name: String)
  case object Actor extends ElementKind("actor")
  case object Goal extends ElementKind("goal")
  case object UseCase extends ElementKind("use-case")
  case object Slice extends ElementKind("slice")
  case object Command extends ElementKind("command")
  case object Operation extends ElementKind("operation")
  case object Aggregate extends ElementKind("aggregate")
  case object Entity extends ElementKind("entity")
  case object DomainEvent extends ElementKind("domain-event")
  case object Policy extends ElementKind("policy")
  case object Component extends ElementKind("component")
  case object Query extends ElementKind("query")
  case object View extends ElementKind("view")
  val elementKinds: Vector[ElementKind] = Vector(Actor, Goal, UseCase, Slice, Command,
    Operation, Aggregate, Entity, DomainEvent, Policy, Component, Query, View)

  sealed abstract class RelationKind(val name: String)
  case object ActorGoal extends RelationKind("actor-goal")
  case object GoalUseCase extends RelationKind("goal-use-case")
  case object UseCaseSlice extends RelationKind("use-case-slice")
  case object SliceSpecification extends RelationKind("slice-specification")
  case object SliceTicket extends RelationKind("slice-ticket")
  case object Cause extends RelationKind("cause")
  case object Reaction extends RelationKind("reaction")
  case object Dependency extends RelationKind("dependency")
  case object Context extends RelationKind("context")
  val relationKinds: Vector[RelationKind] = Vector(ActorGoal, GoalUseCase, UseCaseSlice,
    SliceSpecification, SliceTicket, Cause, Reaction, Dependency, Context)
  val families: Vector[String] = elementKinds.map(_.name) ++ relationKinds.map(_.name)

  sealed abstract class EvidenceState(val name: String)
  case object Present extends EvidenceState("present")
  case object Missing extends EvidenceState("missing")
  case object Unavailable extends EvidenceState("unavailable")
  case object Unsupported extends EvidenceState("unsupported")
  case object External extends EvidenceState("external")
  val evidenceStates: Vector[EvidenceState] = Vector(Present, Missing, Unavailable, Unsupported, External)

  final case class RecordRef(owner: String, identity: String, revision: Long)
  final case class Source(owner: String, identity: String, revision: String)
  final case class Producer(name: String, version: String, revision: String, modified: Boolean)
  final case class Field(name: String, datatype: String, multiplicity: String)
  final case class Element(ref: RecordRef, kind: ElementKind, title: String,
    intent: Option[String], fields: Vector[Field], scope: Vector[RecordRef],
    acceptance: Vector[RecordRef], lifecycle: Option[String], source: Source)
  final case class Specification(owner: String, identity: String, sourceRevision: String)
  final case class TicketObservation(state: String, revision: Option[String])
  final case class Ticket(provider: String, owner: String, externalIdentity: String,
    observation: Option[TicketObservation])
  sealed trait Endpoint
  final case class LocalEndpoint(ref: RecordRef) extends Endpoint
  final case class ExternalEndpoint(ref: RecordRef) extends Endpoint
  final case class SpecificationEndpoint(specification: Specification) extends Endpoint
  final case class TicketEndpoint(ticket: Ticket) extends Endpoint
  final case class Relationship(ref: RecordRef, kind: RelationKind, from: Endpoint,
    to: Endpoint, source: Source)
  final case class Coverage(family: String, status: EvidenceState, detail: Option[String])
  final case class Document(schemaVersion: String, coverage: Vector[Coverage],
    elements: Vector[Element], relationships: Vector[Relationship])
  final case class Limitation(code: String, detail: String)
  final case class Publication(schemaVersion: String, source: Source, producer: Producer,
    document: Document, limitations: Vector[Limitation])
  final case class Diagnostic(code: String, path: String, detail: String)
  final case class ElementReplacement(expected: RecordRef, replacement: Element)
  final case class RelationshipReplacement(expected: RecordRef, replacement: Relationship)
  final case class Candidate(schemaVersion: String, source: Source,
    elements: Vector[ElementReplacement], relationships: Vector[RelationshipReplacement],
    removedRelationships: Vector[RecordRef])
  final case class CandidateV2(schemaVersion: String, source: Source,
    elements: Vector[ElementReplacement], relationships: Vector[RelationshipReplacement],
    removedRelationships: Vector[RecordRef], addedElements: Vector[Element],
    addedRelationships: Vector[Relationship], coverage: Vector[Coverage])
  final case class Rendering(schemaVersion: String, source: Source, producer: Producer,
    candidateSource: Source, text: String, limitations: Vector[Limitation])
}
