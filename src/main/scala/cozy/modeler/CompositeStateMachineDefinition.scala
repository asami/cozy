package cozy.modeler

/*
 * @since   Sep.  7, 2026
 * @version Sep. 22, 2026
 * @author  ASAMI, Tomoharu
 */
/** Immutable Cozy-side normalized IR for a Composite StateMachine definition. */
final case class CompositeStateMachineDefinition(
  identity: String,
  name: String,
  source: CompositeStateMachineSourceIdentity,
  constituents: Vector[CompositeStateMachineConstituent],
  states: Vector[CompositeStateMachineState],
  derivations: Vector[CompositeStateMachineDerivation],
  initialConfiguration: Option[CompositeStateMachineConfiguration],
  actions: Vector[CompositeStateMachineLogicalAction],
  constituentActions: Vector[CompositeStateMachineConstituentAction],
  derivedActions: Vector[CompositeStateMachineDerivedAction]
) {
  def abiVersion: String = CompositeStateMachineDefinition.ABI_VERSION
}

object CompositeStateMachineDefinition {
  val ABI_VERSION: String = "cozy.cml.composite-statemachine.v1"
  val bootstrapAbiVersion: String = "cozy.cml.composite-statemachine-bootstrap.v1"
}

final case class CompositeStateMachineSourceIdentity(line: Option[Int])

/** Source correlation retained for a first-class WORKFLOW CML root or definition. */
final case class WorkflowSourceIdentity(line: Option[Int])

/** Retains both source locations that identify a normalized workflow definition. */
final case class WorkflowSourceCorrelation(
  root: WorkflowSourceIdentity,
  definition: WorkflowSourceIdentity
)

/**
 * Immutable CML WORKFLOW source contract lowered to the established Composite
 * StateMachine semantic model without introducing execution semantics.
 */
final case class WorkflowDefinition(
  identity: String,
  version: String,
  source: WorkflowSourceCorrelation,
  compositeStateMachine: CompositeStateMachineDefinition,
  requiredOperations: Vector[WorkflowRequiredOperation]
)

/** Declares one required capability and the existing OPERATION Action that fulfills it. */
final case class WorkflowRequiredOperation(
  capability: String,
  action: CompositeStateMachineLogicalAction,
  source: WorkflowSourceIdentity
)

final case class CompositeStateMachineReference(name: String)

final case class CompositeStateMachineSubject(name: String, `type`: String)

final case class CompositeStateMachineConstituent(
  role: String,
  stateMachine: CompositeStateMachineReference,
  states: Vector[String],
  subject: Option[CompositeStateMachineSubject],
  source: CompositeStateMachineSourceIdentity
)

final case class CompositeStateMachineState(
  name: String,
  source: CompositeStateMachineSourceIdentity
)

final case class CompositeStateMachineConfigurationBinding(role: String, state: String)

final case class CompositeStateMachineConfiguration(
  bindings: Vector[CompositeStateMachineConfigurationBinding],
  source: CompositeStateMachineSourceIdentity
)

final case class CompositeStateMachineDerivation(
  identity: String,
  state: String,
  configuration: CompositeStateMachineConfiguration,
  source: CompositeStateMachineSourceIdentity
)

final case class CompositeStateMachineOperation(
  service: String,
  name: String,
  inputType: Option[String],
  outputType: Option[String] = None
)

sealed trait CompositeStateMachineEffectClass {
  def canonicalValue: String
}

object CompositeStateMachineEffectClass {
  case object Local extends CompositeStateMachineEffectClass {
    val canonicalValue: String = "LOCAL"
  }

  case object External extends CompositeStateMachineEffectClass {
    val canonicalValue: String = "EXTERNAL"
  }
}

sealed trait CompositeStateMachineTransactionRequirement {
  def canonicalValue: String
}

object CompositeStateMachineTransactionRequirement {
  case object Required extends CompositeStateMachineTransactionRequirement {
    val canonicalValue: String = "REQUIRED"
  }

  case object OutsideUnitOfWork extends CompositeStateMachineTransactionRequirement {
    val canonicalValue: String = "OUTSIDE_UNIT_OF_WORK"
  }
}

sealed trait CompositeStateMachineIdempotency {
  def canonicalValue: String
}

object CompositeStateMachineIdempotency {
  case object NotRequired extends CompositeStateMachineIdempotency {
    val canonicalValue: String = "NOT_REQUIRED"
  }

  final case class Required(keyRef: String) extends CompositeStateMachineIdempotency {
    val canonicalValue: String = s"REQUIRED($keyRef)"
  }
}

final case class CompositeStateMachineActionMetadata(
  effectClass: CompositeStateMachineEffectClass,
  transactionRequirement: CompositeStateMachineTransactionRequirement,
  idempotency: CompositeStateMachineIdempotency,
  compensationHandlerRef: Option[String]
)

/** Closed provider-neutral CAM source semantics carried by one logical Action. */
sealed trait CompositeStateMachineCandidateAdmissionAction {
  def source: CompositeStateMachineSourceIdentity
}

final case class CompositeStateMachineJudgmentGoalReference(
  value: String,
  source: CompositeStateMachineSourceIdentity
)

final case class CompositeStateMachineJudgmentContextReference(
  value: String,
  source: CompositeStateMachineSourceIdentity
)

final case class CompositeStateMachineCandidateIdentity(
  value: String,
  source: CompositeStateMachineSourceIdentity
)

final case class CompositeStateMachineJudgmentAlternativeReference(
  value: String,
  source: CompositeStateMachineSourceIdentity
)

final case class CompositeStateMachineJudgmentCriterionReference(
  value: String,
  source: CompositeStateMachineSourceIdentity
)

final case class CompositeStateMachineJudgmentExpectedResultReference(
  value: String,
  source: CompositeStateMachineSourceIdentity
)

final case class CompositeStateMachineJudgmentEvidenceReference(
  value: String,
  source: CompositeStateMachineSourceIdentity
)

final case class CompositeStateMachineJudgmentEvidenceScopeReference(
  value: String,
  source: CompositeStateMachineSourceIdentity
)

final case class CompositeStateMachineJudgmentEvidenceFreshnessReference(
  value: String,
  source: CompositeStateMachineSourceIdentity
)

final case class CompositeStateMachineJudgmentEvidenceProvenanceReference(
  value: String,
  source: CompositeStateMachineSourceIdentity
)

final case class CompositeStateMachineJudgmentActionReference(
  value: String,
  source: CompositeStateMachineSourceIdentity
)

/** A semantic candidate/result/evidence contract; it intentionally has no transition directive. */
final case class CompositeStateMachineJudgmentAction(
  goal: CompositeStateMachineJudgmentGoalReference,
  context: CompositeStateMachineJudgmentContextReference,
  candidate: CompositeStateMachineCandidateIdentity,
  alternatives: Vector[CompositeStateMachineJudgmentAlternativeReference],
  criteria: Vector[CompositeStateMachineJudgmentCriterionReference],
  expectedResult: CompositeStateMachineJudgmentExpectedResultReference,
  evidence: CompositeStateMachineJudgmentEvidenceReference,
  evidenceScope: CompositeStateMachineJudgmentEvidenceScopeReference,
  evidenceFreshness: CompositeStateMachineJudgmentEvidenceFreshnessReference,
  evidenceProvenance: CompositeStateMachineJudgmentEvidenceProvenanceReference,
  source: CompositeStateMachineSourceIdentity
) extends CompositeStateMachineCandidateAdmissionAction

/** The deterministic local boundary that admits exactly one Judgment Action candidate. */
final case class CompositeStateMachineAdmissionAction(
  candidateAction: CompositeStateMachineJudgmentActionReference,
  source: CompositeStateMachineSourceIdentity
) extends CompositeStateMachineCandidateAdmissionAction

final case class CompositeStateMachineLogicalAction(
  identity: String,
  kind: String,
  operation: CompositeStateMachineOperation,
  inputBinding: Option[String],
  source: CompositeStateMachineSourceIdentity,
  metadata: Option[CompositeStateMachineActionMetadata] = None,
  candidateAdmission: Option[CompositeStateMachineCandidateAdmissionAction] = None
)

final case class CompositeStateMachineConstituentAction(
  identity: String,
  role: String,
  from: String,
  to: String,
  on: String,
  placement: String,
  action: CompositeStateMachineLogicalAction,
  source: CompositeStateMachineSourceIdentity
)

final case class CompositeStateMachineDerivedAction(
  derivedTransition: String,
  from: String,
  to: String,
  action: CompositeStateMachineLogicalAction,
  source: CompositeStateMachineSourceIdentity
)
