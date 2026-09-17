package cozy.modeler

/*
 * @since   Sep.  7, 2026
 * @version Sep. 17, 2026
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
  inputType: Option[String]
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

final case class CompositeStateMachineLogicalAction(
  identity: String,
  kind: String,
  operation: CompositeStateMachineOperation,
  inputBinding: Option[String],
  source: CompositeStateMachineSourceIdentity,
  metadata: Option[CompositeStateMachineActionMetadata] = None
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
