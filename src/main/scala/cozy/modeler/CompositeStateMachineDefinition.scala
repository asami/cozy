package cozy.modeler

/*
 * @since   Sep.  7, 2026
 * @version Sep.  7, 2026
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
}

final case class CompositeStateMachineSourceIdentity(line: Option[Int])

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

final case class CompositeStateMachineLogicalAction(
  identity: String,
  kind: String,
  operation: CompositeStateMachineOperation,
  inputBinding: Option[String],
  source: CompositeStateMachineSourceIdentity
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
