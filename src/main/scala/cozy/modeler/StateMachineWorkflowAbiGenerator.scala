package cozy.modeler

import java.util.Locale
import org.goldenport.realm.Realm

/*
 * @since   Sep. 17, 2026
 * @version Oct.  3, 2026
 * @author  ASAMI, Tomoharu
 */
/** Emits the additive StateMachine/Workflow schema and direct bootstrap metadata. */
private[modeler] object StateMachineWorkflowAbiGenerator {
  val schemaVersion: String = "cozy.cml.statemachine-workflow-abi.v1"
  val bootstrapSchemaVersion: String = "cozy.cml.statemachine-workflow-bootstrap.v1"
  val policySchemaVersion: String = "cozy.cml.statemachine-workflow-abi.v2"
  val policyBootstrapSchemaVersion: String = "cozy.cml.statemachine-workflow-bootstrap.v2"
  val lifecycleSchemaVersion: String = "cozy.cml.statemachine-workflow-abi.v3"
  val lifecycleBootstrapSchemaVersion: String = "cozy.cml.statemachine-workflow-bootstrap.v3"
  val executionSafetySchemaVersion: String = "cozy.cml.statemachine-workflow-abi.v4"
  val executionSafetyBootstrapSchemaVersion: String = "cozy.cml.statemachine-workflow-bootstrap.v4"
  val semanticOutcomeSchemaVersion: String = "cozy.cml.statemachine-workflow-abi.v5"
  val semanticOutcomeBootstrapSchemaVersion: String = "cozy.cml.statemachine-workflow-bootstrap.v5"
  val metadataPath: String = "target/cozy/statemachine-workflow-abi.json"

  private val _root = "target/scala-3.3.8/src_managed/main/scala/domain/statemachine/workflow"

  def generate(workflows: Vector[WorkflowDefinition]): Realm = {
    val outcomebearing = _has_semantic_outcomes(workflows)
    val safetybearing = outcomebearing || _has_execution_safety(workflows)
    val lifecyclebearing = safetybearing || _has_lifecycle(workflows)
    val policybearing = lifecyclebearing || _has_policies(workflows)
    val builder = Realm.Builder()
    builder.set(s"${_root}/StateMachineWorkflowAbi.scala", _abi_source(policybearing, lifecyclebearing, safetybearing, outcomebearing))
    builder.set(s"${_root}/StateMachineWorkflowComponentFactoryBootstrap.scala", _bootstrap_source(workflows, policybearing, lifecyclebearing, safetybearing, outcomebearing))
    workflows.zipWithIndex.foreach { case (workflow, index) =>
      val objectname = _object_name(workflow, index)
      builder.set(s"${_root}/$objectname.scala", _workflow_source(workflow, objectname, policybearing, lifecyclebearing, safetybearing, outcomebearing))
    }
    builder.build()
  }

  def canonicalJson(workflows: Vector[WorkflowDefinition]): String = {
    val outcomebearing = _has_semantic_outcomes(workflows)
    val safetybearing = outcomebearing || _has_execution_safety(workflows)
    val lifecyclebearing = safetybearing || _has_lifecycle(workflows)
    val policybearing = lifecyclebearing || _has_policies(workflows)
    _json_object(Vector(
      "schemaVersion" -> _json_string(_schema_version(policybearing, lifecyclebearing, safetybearing, outcomebearing)),
      "workflows" -> _json_array(workflows.map(_workflow_json(_, policybearing, lifecyclebearing, safetybearing, outcomebearing)))
    ))
  }

  private def _has_policies(workflows: Vector[WorkflowDefinition]): Boolean =
    workflows.exists(_.invocationPolicies.nonEmpty)

  private def _has_lifecycle(workflows: Vector[WorkflowDefinition]): Boolean =
    workflows.exists(x => x.lifecyclePolicy.nonEmpty || x.waits.nonEmpty)

  private def _has_execution_safety(workflows: Vector[WorkflowDefinition]): Boolean =
    workflows.exists(x => x.failurePolicies.nonEmpty || x.executionSafetyPolicy.nonEmpty)

  private def _has_semantic_outcomes(workflows: Vector[WorkflowDefinition]): Boolean =
    workflows.exists(_.semanticOutcomes.nonEmpty)

  private def _schema_version(policybearing: Boolean, lifecyclebearing: Boolean, safetybearing: Boolean, outcomebearing: Boolean): String =
    if (outcomebearing) semanticOutcomeSchemaVersion else if (safetybearing) executionSafetySchemaVersion else if (lifecyclebearing) lifecycleSchemaVersion else if (policybearing) policySchemaVersion else schemaVersion

  private def _abi_source(policybearing: Boolean, lifecyclebearing: Boolean, safetybearing: Boolean, outcomebearing: Boolean): String = {
    val declarations = if (policybearing)
      """  sealed trait InvocationTarget { def identity: String }
        |  object InvocationTarget {
        |    final case class Action(identity: String) extends InvocationTarget
        |    final case class Participant(identity: String) extends InvocationTarget
        |  }
        |  final case class RetryPolicy(maximumAttempts: Int, fixedRetryDelayMillis: Long)
        |  final case class InvocationPolicy(identity: String, target: InvocationTarget,
        |    actionIdentity: String, retry: Option[RetryPolicy],
        |    executionTimeoutMillis: Option[Long], source: SourceIdentity)
        |""".stripMargin else ""
    val policyfield = if (policybearing) ", invocationPolicies: Vector[InvocationPolicy] = Vector.empty" else ""
    val lifecycledeclarations = if (lifecyclebearing)
      """  sealed trait CancellationMode { def canonicalValue: String }
        |  object CancellationMode {
        |    case object Cooperative extends CancellationMode {
        |      val canonicalValue: String = "COOPERATIVE"
        |    }
        |  }
        |  final case class LifecyclePolicy(deadlineMillis: Option[Long], cancellation: Option[CancellationMode], source: SourceIdentity)
        |  sealed trait WaitTrigger
        |  object WaitTrigger {
        |    final case class Timer(delayMillis: Long) extends WaitTrigger
        |    final case class Signal(identity: String) extends WaitTrigger
        |  }
        |  final case class WaitDeclaration(identity: String, state: String, stateIdentity: String, trigger: WaitTrigger, source: SourceIdentity)
        |""".stripMargin else ""
    val lifecyclefields = if (lifecyclebearing) ", lifecyclePolicy: Option[LifecyclePolicy] = None, waits: Vector[WaitDeclaration] = Vector.empty" else ""
    val safetydeclarations = if (safetybearing)
      """  sealed trait FailureClassification { def canonicalValue: String }
        |  object FailureClassification {
        |    case object Retryable extends FailureClassification { val canonicalValue: String = "RETRYABLE" }
        |    case object Permanent extends FailureClassification { val canonicalValue: String = "PERMANENT" }
        |  }
        |  final case class FailureRule(identity: String, classification: FailureClassification, source: SourceIdentity)
        |  final case class FailurePolicy(identity: String, target: InvocationTarget, actionIdentity: String, defaultClassification: FailureClassification, rules: Vector[FailureRule], source: SourceIdentity)
        |  sealed trait DuplicateProtection { def canonicalValue: String }
        |  object DuplicateProtection {
        |    case object NotRequired extends DuplicateProtection { val canonicalValue: String = "NOT_REQUIRED" }
        |    final case class Required(idempotencyKeyRef: String) extends DuplicateProtection { val canonicalValue: String = "REQUIRED" }
        |  }
        |  final case class ExecutionSafetyPolicy(logicalExecutionIdentityRef: String, duplicateProtection: DuplicateProtection, source: SourceIdentity)
        |""".stripMargin else ""
    val safetyfields = if (safetybearing) ", failurePolicies: Vector[FailurePolicy] = Vector.empty, executionSafetyPolicy: Option[ExecutionSafetyPolicy] = None" else ""
    val outcomedeclarations = if (outcomebearing)
      """  enum SemanticOutcome(val canonicalValue: String) {
        |    case NeedsInput extends SemanticOutcome("NEEDS_INPUT")
        |    case NeedsRevision extends SemanticOutcome("NEEDS_REVISION")
        |    case Rejected extends SemanticOutcome("REJECTED")
        |  }
        |  final case class SemanticOutcomeTransition(role: String, stateMachine: String, from: String, to: String, on: String)
        |  final case class SemanticOutcomeDeclaration(identity: String, target: InvocationTarget, actionIdentity: String, outcome: SemanticOutcome, transition: SemanticOutcomeTransition, source: SourceIdentity)
        |""".stripMargin else ""
    val outcomefield = if (outcomebearing) ", semanticOutcomes: Vector[SemanticOutcomeDeclaration] = Vector.empty" else ""
    s"""package domain.statemachine.workflow
       |
       |object StateMachineWorkflowAbi {
       |  val VERSION: String = ${_quote(_schema_version(policybearing, lifecyclebearing, safetybearing, outcomebearing))}
       |
       |  final case class SourceIdentity(line: Option[Int])
       |  final case class WorkflowSourceCorrelation(root: SourceIdentity, definition: SourceIdentity)
       |  final case class State(name: String, source: SourceIdentity)
       |  final case class Operation(service: String, name: String, inputType: Option[String], resultType: Option[String], source: SourceIdentity)
       |  final case class Action(identity: String, kind: String, operation: Operation, inputBinding: Option[String], source: SourceIdentity)
       |  final case class StateMachineOperationIdentity(service: String, operation: String) {
       |    def canonicalValue: String = s"$$service.$$operation"
       |  }
       |  final case class StateMachineInputTypeReference(value: String)
       |  final case class StateMachineResultTypeReference(value: String)
       |  final case class StateMachineProvidedOperation(identity: StateMachineOperationIdentity, inputType: Option[StateMachineInputTypeReference], resultType: Option[StateMachineResultTypeReference])
       |  final case class StateMachineRequiredOperationIdentity(capability: String)
       |  final case class StateMachineConstraint(identity: String, value: String)
       |  final case class StateMachineRequiredOperationMetadata(contextContract: ContextContract, completionContract: CompletionContract, evidenceContract: EvidenceContract, constraints: Vector[StateMachineConstraint])
       |  final case class StateMachineRequiredOperation(identity: StateMachineRequiredOperationIdentity, actionIdentity: String, operation: StateMachineOperationIdentity, inputType: Option[StateMachineInputTypeReference], resultType: Option[StateMachineResultTypeReference], metadata: StateMachineRequiredOperationMetadata)
       |  final case class StateMachineApiSpi(providedOperations: Vector[StateMachineProvidedOperation], requiredOperations: Vector[StateMachineRequiredOperation])
       |  final case class ProviderIdentity(value: String)
       |  final case class StateMachineProviderBinding(requiredOperation: StateMachineRequiredOperationIdentity, provider: ProviderIdentity)
       |  final case class ContextReference(identity: String, revision: String)
       |  final case class ContextSnapshot(workflowRevision: String, modelRevision: Option[String] = None, workspaceRevision: Option[String] = None, evidenceRevision: Option[String] = None)
       |  final case class ContextBundle(summary: String, requiredFacts: Vector[ContextReference], references: Vector[ContextReference], snapshot: ContextSnapshot)
       |  final case class ContextContract(identity: String, requiredFacts: Vector[ContextReference], requiredReferences: Vector[ContextReference])
       |  final case class CompletionContract(identity: String, requiredFacts: Vector[ContextReference])
       |  final case class EvidenceContract(identity: String, requiredEvidence: Vector[ContextReference])
       |  final case class StateMachineOperationInput(typeReference: StateMachineInputTypeReference, contextReference: ContextReference)
       |  final case class StateMachineOperationResult(typeReference: StateMachineResultTypeReference, contextReference: ContextReference)
       |  final case class StateMachineOperationFailure(code: String, message: String, evidence: Vector[ContextReference])
       |  final case class ProviderExecutionRequest(runId: StateMachineRunIdentity, requiredOperation: StateMachineRequiredOperation, input: Option[StateMachineOperationInput], context: ContextBundle)
       |  trait StateMachineProvider {
       |    def identity: ProviderIdentity
       |    def execute(request: ProviderExecutionRequest): ActionExecution
       |  }
       |  final case class StateMachineRunIdentity(value: String)
       |  final case class ContinuationIdentity(value: String)
       |  final case class StateMachineRevision(value: String)
       |  final case class Continuation(runId: StateMachineRunIdentity, continuationId: ContinuationIdentity, expectedRevision: StateMachineRevision, requiredOperation: StateMachineRequiredOperation, context: ContextBundle)
       |  final case class ContinuationResult(runId: StateMachineRunIdentity, continuationId: ContinuationIdentity, expectedRevision: StateMachineRevision, operation: StateMachineOperationIdentity, requiredOperation: StateMachineRequiredOperationIdentity, contextSnapshot: ContextSnapshot, requiredOperationMetadata: StateMachineRequiredOperationMetadata, completion: StateMachineOperationResult, completionFacts: Vector[ContextReference], evidence: Vector[ContextReference])
       |  sealed trait ActionExecution
       |  object ActionExecution {
       |    final case class Completed(result: StateMachineOperationResult) extends ActionExecution
       |    final case class Suspended(continuation: Continuation) extends ActionExecution
       |    final case class Failed(failure: StateMachineOperationFailure) extends ActionExecution
       |  }
       |  final case class WorkflowRequiredOperationDescriptor(identity: StateMachineRequiredOperationIdentity, actionIdentity: String, operation: StateMachineOperationIdentity, inputType: Option[StateMachineInputTypeReference], resultType: Option[StateMachineResultTypeReference], capabilitySource: SourceIdentity, actionSource: SourceIdentity)
       |${declarations}${lifecycledeclarations}${safetydeclarations}${outcomedeclarations}  final case class WorkflowDescriptor(schemaVersion: String, identity: String, version: String, source: WorkflowSourceCorrelation, states: Vector[State], actions: Vector[Action], requiredOperations: Vector[WorkflowRequiredOperationDescriptor]$policyfield$lifecyclefields$safetyfields$outcomefield)
       |  final case class ComponentFactoryMetadata(workflow: WorkflowDescriptor)
       |}
       |""".stripMargin
  }

  private def _bootstrap_source(workflows: Vector[WorkflowDefinition], policybearing: Boolean, lifecyclebearing: Boolean, safetybearing: Boolean, outcomebearing: Boolean): String = {
    val references = workflows.zipWithIndex.map { case (workflow, index) =>
      s"${_object_name(workflow, index)}.componentFactoryMetadata"
    }
    val metadata = _vector(references)
    s"""package domain.statemachine.workflow
       |
       |object StateMachineWorkflowComponentFactoryBootstrap {
       |  val schemaVersion: String = ${_quote(if (outcomebearing) semanticOutcomeBootstrapSchemaVersion else if (safetybearing) executionSafetyBootstrapSchemaVersion else if (lifecyclebearing) lifecycleBootstrapSchemaVersion else if (policybearing) policyBootstrapSchemaVersion else bootstrapSchemaVersion)}
       |  val componentFactoryMetadata: Vector[StateMachineWorkflowAbi.ComponentFactoryMetadata] = $metadata
       |}
       |""".stripMargin
  }

  private def _workflow_source(
    workflow: WorkflowDefinition,
    objectname: String,
    policybearing: Boolean,
    lifecyclebearing: Boolean,
    safetybearing: Boolean,
    outcomebearing: Boolean
  ): String =
    s"""package domain.statemachine.workflow
       |
       |object $objectname {
       |  val workflow: StateMachineWorkflowAbi.WorkflowDescriptor = ${_workflow_descriptor(workflow, policybearing, lifecyclebearing, safetybearing, outcomebearing)}
       |  val componentFactoryMetadata: StateMachineWorkflowAbi.ComponentFactoryMetadata = StateMachineWorkflowAbi.ComponentFactoryMetadata(workflow)
       |}
       |""".stripMargin

  private def _workflow_descriptor(value: WorkflowDefinition, policybearing: Boolean, lifecyclebearing: Boolean, safetybearing: Boolean, outcomebearing: Boolean): String = {
    val policies = if (policybearing)
      s",\n  invocationPolicies = ${_vector(value.invocationPolicies.map(_invocation_policy))}" else ""
    val lifecycle = if (lifecyclebearing)
      s",\n  lifecyclePolicy = ${_option(value.lifecyclePolicy.map(_lifecycle_policy))},\n  waits = ${_vector(value.waits.map(_wait_declaration))}" else ""
    val safety = if (safetybearing)
      s",\n  failurePolicies = ${_vector(value.failurePolicies.map(_failure_policy))},\n  executionSafetyPolicy = ${_option(value.executionSafetyPolicy.map(_execution_safety_policy))}" else ""
    val outcomes = if (outcomebearing)
      s",\n  semanticOutcomes = ${_vector(value.semanticOutcomes.map(_semantic_outcome_declaration))}" else ""
    s"""StateMachineWorkflowAbi.WorkflowDescriptor(
       |  schemaVersion = ${_quote(_schema_version(policybearing, lifecyclebearing, safetybearing, outcomebearing))},
       |  identity = ${_quote(value.identity)},
       |  version = ${_quote(value.version)},
       |  source = ${_workflow_source_correlation(value.source)},
       |  states = ${_vector(value.compositeStateMachine.states.map(_state))},
       |  actions = ${_vector(value.compositeStateMachine.actions.map(_action))},
       |  requiredOperations = ${_vector(value.requiredOperations.map(_required_spi))}$policies$lifecycle$safety$outcomes
       |)""".stripMargin
  }

  private def _semantic_outcome(value: WorkflowSemanticOutcome): String = value match {
    case WorkflowSemanticOutcome.NeedsInput => "StateMachineWorkflowAbi.SemanticOutcome.NeedsInput"
    case WorkflowSemanticOutcome.NeedsRevision => "StateMachineWorkflowAbi.SemanticOutcome.NeedsRevision"
    case WorkflowSemanticOutcome.Rejected => "StateMachineWorkflowAbi.SemanticOutcome.Rejected"
  }

  private def _semantic_outcome_transition(value: WorkflowSemanticOutcomeTransition): String =
    s"StateMachineWorkflowAbi.SemanticOutcomeTransition(${_quote(value.role)}, ${_quote(value.stateMachine)}, ${_quote(value.from)}, ${_quote(value.to)}, ${_quote(value.on)})"

  private def _semantic_outcome_declaration(value: WorkflowSemanticOutcomeDeclaration): String =
    s"StateMachineWorkflowAbi.SemanticOutcomeDeclaration(${_quote(value.identity)}, ${_invocation_target(value.target)}, ${_quote(value.actionIdentity)}, ${_semantic_outcome(value.outcome)}, ${_semantic_outcome_transition(value.transition)}, ${_workflow_source(value.source)})"

  private def _failure_classification(value: WorkflowFailureClassification): String = value match {
    case WorkflowFailureClassification.Retryable => "StateMachineWorkflowAbi.FailureClassification.Retryable"
    case WorkflowFailureClassification.Permanent => "StateMachineWorkflowAbi.FailureClassification.Permanent"
  }

  private def _failure_rule(value: WorkflowFailureRule): String =
    s"StateMachineWorkflowAbi.FailureRule(${_quote(value.identity)}, ${_failure_classification(value.classification)}, ${_workflow_source(value.source)})"

  private def _failure_policy(value: WorkflowFailurePolicy): String =
    s"StateMachineWorkflowAbi.FailurePolicy(${_quote(value.identity)}, ${_invocation_target(value.target)}, ${_quote(value.actionIdentity)}, ${_failure_classification(value.defaultClassification)}, ${_vector(value.rules.map(_failure_rule))}, ${_workflow_source(value.source)})"

  private def _duplicate_protection(value: WorkflowDuplicateProtection): String = value match {
    case WorkflowDuplicateProtection.NotRequired => "StateMachineWorkflowAbi.DuplicateProtection.NotRequired"
    case WorkflowDuplicateProtection.Required(key) => s"StateMachineWorkflowAbi.DuplicateProtection.Required(${_quote(key)})"
  }

  private def _execution_safety_policy(value: WorkflowExecutionSafetyPolicy): String =
    s"StateMachineWorkflowAbi.ExecutionSafetyPolicy(${_quote(value.logicalExecutionIdentityRef)}, ${_duplicate_protection(value.duplicateProtection)}, ${_workflow_source(value.source)})"

  private def _invocation_policy(value: WorkflowInvocationPolicy): String =
    s"StateMachineWorkflowAbi.InvocationPolicy(${_quote(value.identity)}, ${_invocation_target(value.target)}, ${_quote(value.actionIdentity)}, ${_option(value.retry.map(_retry_policy))}, ${_option(value.executionTimeoutMillis.map(x => s"${x}L"))}, ${_workflow_source(value.source)})"

  private def _invocation_target(value: WorkflowInvocationTarget): String = value match {
    case WorkflowInvocationTarget.Action(identity) => s"StateMachineWorkflowAbi.InvocationTarget.Action(${_quote(identity)})"
    case WorkflowInvocationTarget.Participant(identity) => s"StateMachineWorkflowAbi.InvocationTarget.Participant(${_quote(identity)})"
  }

  private def _retry_policy(value: WorkflowRetryPolicy): String =
    s"StateMachineWorkflowAbi.RetryPolicy(${value.maximumAttempts}, ${value.fixedRetryDelayMillis}L)"

  private def _lifecycle_policy(value: WorkflowLifecyclePolicy): String =
    s"StateMachineWorkflowAbi.LifecyclePolicy(${_option(value.deadlineMillis.map(x => s"${x}L"))}, ${_option(value.cancellation.map(_cancellation_mode))}, ${_workflow_source(value.source)})"

  private def _cancellation_mode(value: WorkflowCancellationMode): String = value match {
    case WorkflowCancellationMode.Cooperative => "StateMachineWorkflowAbi.CancellationMode.Cooperative"
  }

  private def _wait_declaration(value: WorkflowWaitDeclaration): String =
    s"StateMachineWorkflowAbi.WaitDeclaration(${_quote(value.identity)}, ${_quote(value.state)}, ${_quote(value.stateIdentity)}, ${_wait_trigger(value.trigger)}, ${_workflow_source(value.source)})"

  private def _wait_trigger(value: WorkflowWaitTrigger): String = value match {
    case WorkflowWaitTrigger.Timer(delay) => s"StateMachineWorkflowAbi.WaitTrigger.Timer(${delay}L)"
    case WorkflowWaitTrigger.Signal(identity) => s"StateMachineWorkflowAbi.WaitTrigger.Signal(${_quote(identity)})"
  }

  private def _workflow_source_correlation(value: WorkflowSourceCorrelation): String =
    s"StateMachineWorkflowAbi.WorkflowSourceCorrelation(${_workflow_source(value.root)}, ${_workflow_source(value.definition)})"

  private def _workflow_source(value: WorkflowSourceIdentity): String =
    s"StateMachineWorkflowAbi.SourceIdentity(${_option(value.line.map(_.toString))})"

  private def _state(value: CompositeStateMachineState): String =
    s"StateMachineWorkflowAbi.State(${_quote(value.name)}, ${_source(value.source)})"

  private def _action(value: CompositeStateMachineLogicalAction): String =
    s"StateMachineWorkflowAbi.Action(${_quote(value.identity)}, ${_quote(value.kind)}, ${_operation(value.operation, value.source)}, ${_option(value.inputBinding.map(_quote))}, ${_source(value.source)})"

  private def _required_spi(value: WorkflowRequiredOperation): String =
    s"StateMachineWorkflowAbi.WorkflowRequiredOperationDescriptor(StateMachineWorkflowAbi.StateMachineRequiredOperationIdentity(${_quote(value.capability)}), ${_quote(value.action.identity)}, StateMachineWorkflowAbi.StateMachineOperationIdentity(${_quote(value.action.operation.service)}, ${_quote(value.action.operation.name)}), ${_option(value.action.operation.inputType.map(_input_type_reference))}, ${_option(value.action.operation.outputType.map(_result_type_reference))}, ${_workflow_source(value.source)}, ${_source(value.action.source)})"

  private def _input_type_reference(value: String): String =
    s"StateMachineWorkflowAbi.StateMachineInputTypeReference(${_quote(value)})"

  private def _result_type_reference(value: String): String =
    s"StateMachineWorkflowAbi.StateMachineResultTypeReference(${_quote(value)})"

  private def _operation(
    value: CompositeStateMachineOperation,
    source: CompositeStateMachineSourceIdentity
  ): String =
    s"StateMachineWorkflowAbi.Operation(${_quote(value.service)}, ${_quote(value.name)}, ${_option(value.inputType.map(_quote))}, ${_option(value.outputType.map(_quote))}, ${_source(source)})"

  private def _source(value: CompositeStateMachineSourceIdentity): String =
    s"StateMachineWorkflowAbi.SourceIdentity(${_option(value.line.map(_.toString))})"

  private def _workflow_json(value: WorkflowDefinition, policybearing: Boolean, lifecyclebearing: Boolean, safetybearing: Boolean, outcomebearing: Boolean): String =
    _json_object(Vector(
      "identity" -> _json_string(value.identity),
      "version" -> _json_string(value.version),
      "source" -> _workflow_source_json(value.source),
      "states" -> _json_array(value.compositeStateMachine.states.map(_state_json)),
      "actions" -> _json_array(value.compositeStateMachine.actions.map(_action_json)),
      "requiredSpi" -> _json_array(value.requiredOperations.map(_required_spi_json))
    ) ++ (if (policybearing) Vector("invocationPolicies" -> _json_array(value.invocationPolicies.map(_invocation_policy_json))) else Vector.empty)
      ++ (if (lifecyclebearing) Vector(
        "lifecyclePolicy" -> _json_option(value.lifecyclePolicy.map(_lifecycle_policy_json)),
        "waits" -> _json_array(value.waits.map(_wait_declaration_json))
      ) else Vector.empty)
      ++ (if (safetybearing) Vector(
        "failurePolicies" -> _json_array(value.failurePolicies.map(_failure_policy_json)),
        "executionSafetyPolicy" -> _json_option(value.executionSafetyPolicy.map(_execution_safety_policy_json))
      ) else Vector.empty)
      ++ (if (outcomebearing) Vector("semanticOutcomes" -> _json_array(value.semanticOutcomes.map(_semantic_outcome_json))) else Vector.empty))

  private def _semantic_outcome_json(value: WorkflowSemanticOutcomeDeclaration): String =
    _json_object(Vector(
      "identity" -> _json_string(value.identity),
      "target" -> _json_object(Vector(
        "kind" -> _json_string(value.target match {
          case _: WorkflowInvocationTarget.Action => "ACTION"
          case _: WorkflowInvocationTarget.Participant => "PARTICIPANT"
        }),
        "identity" -> _json_string(value.target.identity)
      )),
      "actionIdentity" -> _json_string(value.actionIdentity),
      "outcome" -> _json_string(value.outcome.canonicalValue),
      "transition" -> _json_object(Vector(
        "role" -> _json_string(value.transition.role),
        "stateMachine" -> _json_string(value.transition.stateMachine),
        "from" -> _json_string(value.transition.from),
        "to" -> _json_string(value.transition.to),
        "on" -> _json_string(value.transition.on)
      )),
      "source" -> _workflow_source_json(value.source)
    ))

  private def _failure_policy_json(value: WorkflowFailurePolicy): String =
    _json_object(Vector(
      "identity" -> _json_string(value.identity),
      "target" -> _json_object(Vector(
        "kind" -> _json_string(value.target match {
          case _: WorkflowInvocationTarget.Action => "ACTION"
          case _: WorkflowInvocationTarget.Participant => "PARTICIPANT"
        }),
        "identity" -> _json_string(value.target.identity)
      )),
      "actionIdentity" -> _json_string(value.actionIdentity),
      "defaultClassification" -> _json_string(value.defaultClassification.canonicalValue),
      "rules" -> _json_array(value.rules.map(_failure_rule_json)),
      "source" -> _workflow_source_json(value.source)
    ))

  private def _failure_rule_json(value: WorkflowFailureRule): String =
    _json_object(Vector(
      "identity" -> _json_string(value.identity),
      "classification" -> _json_string(value.classification.canonicalValue),
      "source" -> _workflow_source_json(value.source)
    ))

  private def _execution_safety_policy_json(value: WorkflowExecutionSafetyPolicy): String =
    _json_object(Vector(
      "logicalExecutionIdentityRef" -> _json_string(value.logicalExecutionIdentityRef),
      "duplicateProtection" -> _json_object(Vector(
        "kind" -> _json_string(value.duplicateProtection.canonicalValue),
        "idempotencyKeyRef" -> (value.duplicateProtection match {
          case WorkflowDuplicateProtection.NotRequired => "null"
          case WorkflowDuplicateProtection.Required(key) => _json_string(key)
        })
      )),
      "source" -> _workflow_source_json(value.source)
    ))

  private def _lifecycle_policy_json(value: WorkflowLifecyclePolicy): String =
    _json_object(Vector(
      "deadlineMillis" -> _json_option(value.deadlineMillis.map(_.toString)),
      "cancellation" -> _json_option(value.cancellation.map(x => _json_string(x.canonicalValue))),
      "source" -> _workflow_source_json(value.source)
    ))

  private def _wait_declaration_json(value: WorkflowWaitDeclaration): String =
    _json_object(Vector(
      "identity" -> _json_string(value.identity),
      "state" -> _json_string(value.state),
      "stateIdentity" -> _json_string(value.stateIdentity),
      "trigger" -> (value.trigger match {
        case WorkflowWaitTrigger.Timer(delay) => _json_object(Vector("kind" -> _json_string("TIMER"), "delayMillis" -> delay.toString))
        case WorkflowWaitTrigger.Signal(identity) => _json_object(Vector("kind" -> _json_string("SIGNAL"), "identity" -> _json_string(identity)))
      }),
      "source" -> _workflow_source_json(value.source)
    ))

  private def _invocation_policy_json(value: WorkflowInvocationPolicy): String =
    _json_object(Vector(
      "identity" -> _json_string(value.identity),
      "target" -> _json_object(Vector(
        "kind" -> _json_string(value.target match {
          case _: WorkflowInvocationTarget.Action => "ACTION"
          case _: WorkflowInvocationTarget.Participant => "PARTICIPANT"
        }),
        "identity" -> _json_string(value.target.identity)
      )),
      "actionIdentity" -> _json_string(value.actionIdentity),
      "retry" -> _json_option(value.retry.map(_retry_policy_json)),
      "executionTimeoutMillis" -> _json_option(value.executionTimeoutMillis.map(_.toString)),
      "source" -> _workflow_source_json(value.source)
    ))

  private def _retry_policy_json(value: WorkflowRetryPolicy): String =
    _json_object(Vector(
      "maximumAttempts" -> value.maximumAttempts.toString,
      "fixedRetryDelayMillis" -> value.fixedRetryDelayMillis.toString
    ))

  private def _workflow_source_json(value: WorkflowSourceCorrelation): String =
    _json_object(Vector(
      "root" -> _workflow_source_json(value.root),
      "definition" -> _workflow_source_json(value.definition)
    ))

  private def _workflow_source_json(value: WorkflowSourceIdentity): String =
    _json_object(Vector("line" -> _json_option(value.line.map(_.toString))))

  private def _state_json(value: CompositeStateMachineState): String =
    _json_object(Vector(
      "name" -> _json_string(value.name),
      "source" -> _source_json(value.source)
    ))

  private def _action_json(value: CompositeStateMachineLogicalAction): String =
    _json_object(Vector(
      "identity" -> _json_string(value.identity),
      "kind" -> _json_string(value.kind),
      "operation" -> _operation_json(value.operation, value.source),
      "inputBinding" -> _json_option(value.inputBinding.map(_json_string)),
      "source" -> _source_json(value.source)
    ))

  private def _required_spi_json(value: WorkflowRequiredOperation): String =
    _json_object(Vector(
      "capability" -> _json_string(value.capability),
      "actionIdentity" -> _json_string(value.action.identity),
      "operation" -> _operation_json(value.action.operation, value.action.source),
      "source" -> _workflow_source_json(value.source)
    ))

  private def _operation_json(
    value: CompositeStateMachineOperation,
    source: CompositeStateMachineSourceIdentity
  ): String =
    _json_object(Vector(
      "service" -> _json_string(value.service),
      "name" -> _json_string(value.name),
      "inputType" -> _json_option(value.inputType.map(_json_string)),
      "resultType" -> _json_option(value.outputType.map(_json_string)),
      "source" -> _source_json(source)
    ))

  private def _source_json(value: CompositeStateMachineSourceIdentity): String =
    _json_object(Vector("line" -> _json_option(value.line.map(_.toString))))

  private def _object_name(value: WorkflowDefinition, index: Int): String = {
    val words = value.identity.split("[^A-Za-z0-9]+").toVector.filter(_.nonEmpty)
    val stem = words.map(word => word.take(1).toUpperCase(Locale.ROOT) + word.drop(1)).mkString
    val normalized = if (stem.isEmpty) "Workflow" else stem
    val prefixed = if (normalized.head.isDigit) s"Workflow$normalized" else normalized
    s"${prefixed}StateMachineWorkflow${index + 1}"
  }

  private def _vector(values: Vector[String]): String =
    if (values.isEmpty) "Vector.empty" else values.mkString("Vector(", ", ", ")")

  private def _option(value: Option[String]): String = value.map(x => s"Some($x)").getOrElse("None")

  private def _json_object(fields: Vector[(String, String)]): String =
    fields.map { case (name, value) => s"${_json_string(name)}:$value" }.mkString("{", ",", "}")

  private def _json_array(values: Vector[String]): String = values.mkString("[", ",", "]")

  private def _json_option(value: Option[String]): String = value.getOrElse("null")

  private def _json_string(value: String): String = _quote(value)

  private def _quote(value: String): String = {
    val escaped = value.flatMap {
      case '\\' => "\\\\"
      case '"' => "\\\""
      case '\n' => "\\n"
      case '\r' => "\\r"
      case '\t' => "\\t"
      case character if Character.isISOControl(character) => f"\\u${character.toInt}%04x"
      case character => character.toString
    }
    "\"" + escaped + "\""
  }
}
