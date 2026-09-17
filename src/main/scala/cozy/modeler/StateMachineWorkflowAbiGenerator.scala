package cozy.modeler

import java.util.Locale
import org.goldenport.realm.Realm

/*
 * @since   Sep. 17, 2026
 * @version Sep. 17, 2026
 * @author  ASAMI, Tomoharu
 */
/** Emits the additive StateMachine/Workflow schema and direct bootstrap metadata. */
private[modeler] object StateMachineWorkflowAbiGenerator {
  val schemaVersion: String = "cozy.cml.statemachine-workflow-abi.v1"
  val bootstrapSchemaVersion: String = "cozy.cml.statemachine-workflow-bootstrap.v1"
  val metadataPath: String = "target/cozy/statemachine-workflow-abi.json"

  private val _root = "target/scala-3.3.8/src_managed/main/scala/domain/statemachine/workflow"

  def generate(workflows: Vector[WorkflowDefinition]): Realm = {
    val builder = Realm.Builder()
    builder.set(s"${_root}/StateMachineWorkflowAbi.scala", _abi_source)
    builder.set(s"${_root}/StateMachineWorkflowComponentFactoryBootstrap.scala", _bootstrap_source(workflows))
    workflows.zipWithIndex.foreach { case (workflow, index) =>
      val objectname = _object_name(workflow, index)
      builder.set(s"${_root}/$objectname.scala", _workflow_source(workflow, objectname))
    }
    builder.build()
  }

  def canonicalJson(workflows: Vector[WorkflowDefinition]): String =
    _json_object(Vector(
      "schemaVersion" -> _json_string(schemaVersion),
      "workflows" -> _json_array(workflows.map(_workflow_json))
    ))

  private def _abi_source: String =
    s"""package domain.statemachine.workflow
       |
       |object StateMachineWorkflowAbi {
       |  val VERSION: String = ${_quote(schemaVersion)}
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
       |  final case class WorkflowDescriptor(schemaVersion: String, identity: String, version: String, source: WorkflowSourceCorrelation, states: Vector[State], actions: Vector[Action], requiredOperations: Vector[WorkflowRequiredOperationDescriptor])
       |  final case class ComponentFactoryMetadata(workflow: WorkflowDescriptor)
       |}
       |""".stripMargin

  private def _bootstrap_source(workflows: Vector[WorkflowDefinition]): String = {
    val references = workflows.zipWithIndex.map { case (workflow, index) =>
      s"${_object_name(workflow, index)}.componentFactoryMetadata"
    }
    val metadata = _vector(references)
    s"""package domain.statemachine.workflow
       |
       |object StateMachineWorkflowComponentFactoryBootstrap {
       |  val schemaVersion: String = ${_quote(bootstrapSchemaVersion)}
       |  val componentFactoryMetadata: Vector[StateMachineWorkflowAbi.ComponentFactoryMetadata] = $metadata
       |}
       |""".stripMargin
  }

  private def _workflow_source(
    workflow: WorkflowDefinition,
    objectname: String
  ): String =
    s"""package domain.statemachine.workflow
       |
       |object $objectname {
       |  val workflow: StateMachineWorkflowAbi.WorkflowDescriptor = ${_workflow_descriptor(workflow)}
       |  val componentFactoryMetadata: StateMachineWorkflowAbi.ComponentFactoryMetadata = StateMachineWorkflowAbi.ComponentFactoryMetadata(workflow)
       |}
       |""".stripMargin

  private def _workflow_descriptor(value: WorkflowDefinition): String =
    s"""StateMachineWorkflowAbi.WorkflowDescriptor(
       |  schemaVersion = ${_quote(schemaVersion)},
       |  identity = ${_quote(value.identity)},
       |  version = ${_quote(value.version)},
       |  source = ${_workflow_source_correlation(value.source)},
       |  states = ${_vector(value.compositeStateMachine.states.map(_state))},
       |  actions = ${_vector(value.compositeStateMachine.actions.map(_action))},
       |  requiredOperations = ${_vector(value.requiredOperations.map(_required_spi))}
       |)""".stripMargin

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

  private def _workflow_json(value: WorkflowDefinition): String =
    _json_object(Vector(
      "identity" -> _json_string(value.identity),
      "version" -> _json_string(value.version),
      "source" -> _workflow_source_json(value.source),
      "states" -> _json_array(value.compositeStateMachine.states.map(_state_json)),
      "actions" -> _json_array(value.compositeStateMachine.actions.map(_action_json)),
      "requiredSpi" -> _json_array(value.requiredOperations.map(_required_spi_json))
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
