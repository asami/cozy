package cozy.modeler

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import org.goldenport.kaleidox.{Config => KaleidoxConfig, Model => KaleidoxModel}
import org.goldenport.realm.Realm
import org.scalacheck.{Gen, Prop, Test}
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Sep. 17, 2026
 * @version Sep. 17, 2026
 * @author  ASAMI, Tomoharu
 */
final class StateMachineWorkflowAbiGenerationSpec extends AnyWordSpec with Matchers with GivenWhenThen with ModelerSpecSupport {
  "Generated StateMachine/Workflow ABI" should {
    "project the normalized Workflow source boundary" which {
      "emit byte-identical schemas, direct ComponentFactory metadata, and canonical sidecars through both public Scala routes" in {
        Given("a structural WORKFLOW with one typed Required SPI Operation")
        val base = Paths.get(sys.props("user.dir")).toAbsolutePath.normalize()
        val input = base.resolve("target/test-input/statemachine-workflow-abi-generation.cml")
        val normalout = base.resolve("target/test-generated/statemachine-workflow-abi-normal")
        val valueout = base.resolve("target/test-generated/statemachine-workflow-abi-value")
        delete_recursively(normalout)
        delete_recursively(valueout)
        write_file(input, _source())

        When("the public normal and value Scala routes each generate the same source twice")
        _run("modeler-scala", input, normalout)
        val normalfirst = tree_snapshot(normalout)
        _run("modeler-scala", input, normalout)
        val normalsecond = tree_snapshot(normalout)
        _run("modeler-scala-value", input, valueout)
        val valuefirst = tree_snapshot(valueout)
        _run("modeler-scala-value", input, valueout)
        val valuesecond = tree_snapshot(valueout)

        Then("both routes emit the fixed ABI and bootstrap versions with direct descriptor references")
        val root = "target/scala-3.3.8/src_managed/main/scala/domain/statemachine/workflow"
        val normalroot = normalout.resolve(root)
        val valueroot = valueout.resolve(root)
        val normalabi = normalroot.resolve("StateMachineWorkflowAbi.scala")
        val valueabi = valueroot.resolve("StateMachineWorkflowAbi.scala")
        val normalbootstrap = normalroot.resolve("StateMachineWorkflowComponentFactoryBootstrap.scala")
        val valuebootstrap = valueroot.resolve("StateMachineWorkflowComponentFactoryBootstrap.scala")
        val normalworkflow = normalroot.resolve("OrderProgressStateMachineWorkflow1.scala")
        val valueworkflow = valueroot.resolve("OrderProgressStateMachineWorkflow1.scala")
        val normaljson = normalout.resolve(StateMachineWorkflowAbiGenerator.metadataPath)
        val valuejson = valueout.resolve(StateMachineWorkflowAbiGenerator.metadataPath)
        Files.exists(normalabi) shouldBe true
        Files.exists(valueabi) shouldBe true
        Files.exists(normalbootstrap) shouldBe true
        Files.exists(valuebootstrap) shouldBe true
        Files.exists(normalworkflow) shouldBe true
        Files.exists(valueworkflow) shouldBe true
        Files.exists(normaljson) shouldBe true
        Files.exists(valuejson) shouldBe true
        Files.readString(normalabi) shouldBe Files.readString(valueabi)
        Files.readString(normalabi) should include("val VERSION: String = \"cozy.cml.statemachine-workflow-abi.v1\"")
        Files.readString(normalabi) should include("final case class StateMachineOperationIdentity(service: String, operation: String)")
        Files.readString(normalabi) should include("final case class StateMachineInputTypeReference(value: String)")
        Files.readString(normalabi) should include("final case class StateMachineResultTypeReference(value: String)")
        Files.readString(normalabi) should include("final case class StateMachineApiSpi")
        Files.readString(normalabi) should include("final case class StateMachineProvidedOperation")
        Files.readString(normalabi) should include("final case class StateMachineRequiredOperationIdentity(capability: String)")
        Files.readString(normalabi) should include("final case class StateMachineConstraint(identity: String, value: String)")
        Files.readString(normalabi) should include("final case class StateMachineRequiredOperationMetadata(contextContract: ContextContract, completionContract: CompletionContract, evidenceContract: EvidenceContract, constraints: Vector[StateMachineConstraint])")
        Files.readString(normalabi) should include("final case class StateMachineRequiredOperation(identity: StateMachineRequiredOperationIdentity, actionIdentity: String, operation: StateMachineOperationIdentity, inputType: Option[StateMachineInputTypeReference], resultType: Option[StateMachineResultTypeReference], metadata: StateMachineRequiredOperationMetadata)")
        Files.readString(normalabi) should include("final case class StateMachineProviderBinding(requiredOperation: StateMachineRequiredOperationIdentity, provider: ProviderIdentity)")
        Files.readString(normalabi) should include("final case class ContextReference(identity: String, revision: String)")
        Files.readString(normalabi) should include("final case class ContextSnapshot(workflowRevision: String, modelRevision: Option[String] = None, workspaceRevision: Option[String] = None, evidenceRevision: Option[String] = None)")
        Files.readString(normalabi) should include("final case class ContextBundle(summary: String, requiredFacts: Vector[ContextReference], references: Vector[ContextReference], snapshot: ContextSnapshot)")
        Files.readString(normalabi) should include("final case class ContextContract")
        Files.readString(normalabi) should include("final case class CompletionContract")
        Files.readString(normalabi) should include("final case class EvidenceContract")
        Files.readString(normalabi) should include("final case class StateMachineOperationInput(typeReference: StateMachineInputTypeReference, contextReference: ContextReference)")
        Files.readString(normalabi) should include("final case class StateMachineOperationResult(typeReference: StateMachineResultTypeReference, contextReference: ContextReference)")
        Files.readString(normalabi) should include("final case class StateMachineOperationFailure(code: String, message: String, evidence: Vector[ContextReference])")
        Files.readString(normalabi) should include("final case class ProviderExecutionRequest(runId: StateMachineRunIdentity, requiredOperation: StateMachineRequiredOperation, input: Option[StateMachineOperationInput], context: ContextBundle)")
        Files.readString(normalabi) should include("trait StateMachineProvider {")
        Files.readString(normalabi) should include("final case class StateMachineRunIdentity(value: String)")
        Files.readString(normalabi) should include("final case class ContinuationIdentity(value: String)")
        Files.readString(normalabi) should include("final case class StateMachineRevision(value: String)")
        Files.readString(normalabi) should include("final case class Continuation(runId: StateMachineRunIdentity, continuationId: ContinuationIdentity, expectedRevision: StateMachineRevision, requiredOperation: StateMachineRequiredOperation, context: ContextBundle)")
        Files.readString(normalabi) should include("final case class ContinuationResult(runId: StateMachineRunIdentity, continuationId: ContinuationIdentity, expectedRevision: StateMachineRevision, operation: StateMachineOperationIdentity, requiredOperation: StateMachineRequiredOperationIdentity, contextSnapshot: ContextSnapshot, requiredOperationMetadata: StateMachineRequiredOperationMetadata, completion: StateMachineOperationResult, completionFacts: Vector[ContextReference], evidence: Vector[ContextReference])")
        Files.readString(normalabi) should include("final case class Completed(result: StateMachineOperationResult) extends ActionExecution")
        Files.readString(normalabi) should include("final case class Suspended(continuation: Continuation) extends ActionExecution")
        Files.readString(normalabi) should include("final case class Failed(failure: StateMachineOperationFailure) extends ActionExecution")
        val expectedbootstrap = """package domain.statemachine.workflow
          |
          |object StateMachineWorkflowComponentFactoryBootstrap {
          |  val schemaVersion: String = "cozy.cml.statemachine-workflow-bootstrap.v1"
          |  val componentFactoryMetadata: Vector[StateMachineWorkflowAbi.ComponentFactoryMetadata] = Vector(OrderProgressStateMachineWorkflow1.componentFactoryMetadata)
          |}
          |""".stripMargin
        Files.readString(normalbootstrap) shouldBe expectedbootstrap
        Files.readString(valuebootstrap) shouldBe expectedbootstrap
        Files.readString(normalworkflow) shouldBe Files.readString(valueworkflow)
        Files.readString(normalworkflow) should include("val workflow: StateMachineWorkflowAbi.WorkflowDescriptor")
        Files.readString(normalworkflow) should include("StateMachineWorkflowAbi.ComponentFactoryMetadata(workflow)")

        And("the workflow descriptor retains ordered source correlation without instantiating generic Required SPI metadata")
        val workflowcontent = Files.readString(normalworkflow)
        workflowcontent should include("identity = \"OrderProgress\"")
        workflowcontent should include("version = \"workflow-v1\"")
        workflowcontent should include("StateMachineWorkflowAbi.State(\"Pending\"")
        workflowcontent should include("StateMachineWorkflowAbi.Action(\"capture-payment\", \"OPERATION\"")
        workflowcontent should include("StateMachineWorkflowAbi.Operation(\"OrderService\", \"capturePayment\", Some(\"PaymentCommand\"), Some(\"PaymentResult\"")
        workflowcontent should include("requiredOperations = Vector(StateMachineWorkflowAbi.WorkflowRequiredOperationDescriptor(StateMachineWorkflowAbi.StateMachineRequiredOperationIdentity(\"capture-payment-capability\"), \"capture-payment\", StateMachineWorkflowAbi.StateMachineOperationIdentity(\"OrderService\", \"capturePayment\"), Some(StateMachineWorkflowAbi.StateMachineInputTypeReference(\"PaymentCommand\")), Some(StateMachineWorkflowAbi.StateMachineResultTypeReference(\"PaymentResult\"))")
        workflowcontent should not include "StateMachineWorkflowAbi.StateMachineApiSpi("
        workflowcontent should not include "StateMachineWorkflowAbi.StateMachineRequiredOperation("
        workflowcontent should not include "metadata"
        workflowcontent should not include "StateMachineProviderBinding("
        workflowcontent should not include "ProviderIdentity("
        Files.readAllBytes(normaljson).toVector shouldBe Files.readAllBytes(valuejson).toVector
        Files.readString(normaljson) should include("{\"schemaVersion\":\"cozy.cml.statemachine-workflow-abi.v1\",\"workflows\":[")
        Files.readString(normaljson) should include("\"requiredSpi\":[{\"capability\":\"capture-payment-capability\"")
        Files.readString(normaljson) should include("\"inputType\":\"PaymentCommand\"")
        Files.readString(normaljson) should include("\"resultType\":\"PaymentResult\"")

        And("the generated producer boundary contains no execution-mode, runtime, transport, or proxy implementation vocabulary")
        val generated = Files.readString(normalabi) + Files.readString(normalbootstrap) + workflowcontent + Files.readString(normaljson)
        generated should not include "InvocationBinding"
        generated should not include "ORCHESTRATION"
        generated should not include "RUNTIME"
        generated should not include "REST"
        generated should not include "transport"
        generated should not include "proxy"
        generated should not include "Skill"

        And("both public routes remain byte-identical across repeated output")
        normalfirst shouldBe normalsecond
        valuefirst shouldBe valuesecond
      }

      "retain located Workflow and Operation provenance without inventing contract values" in {
        Given("a location-aware structural WORKFLOW source")
        val model = _model_with_location(_source())
        val workflow = CompositeStateMachineCml.workflowDefinitions(model).head

        When("the normalized Workflow is rendered as an ABI descriptor")
        val generated = StateMachineWorkflowAbiGenerator.generate(Vector(workflow))
        val descriptor = _realm_string(generated, "target/scala-3.3.8/src_managed/main/scala/domain/statemachine/workflow/OrderProgressStateMachineWorkflow1.scala")

        Then("root, definition, action, operation, and Required SPI sources remain observable")
        workflow.source.root.line should not be empty
        workflow.source.definition.line should not be empty
        descriptor should include("WorkflowSourceCorrelation(StateMachineWorkflowAbi.SourceIdentity(Some(")
        descriptor should include("StateMachineWorkflowAbi.Operation(\"OrderService\", \"capturePayment\"")
        descriptor should include("StateMachineWorkflowAbi.WorkflowRequiredOperationDescriptor(StateMachineWorkflowAbi.StateMachineRequiredOperationIdentity(\"capture-payment-capability\"")
        descriptor should not include "StateMachineWorkflowAbi.StateMachineRequiredOperation("
        descriptor should not include "metadata"
      }
    }

    "retain deterministic empty producer output" which {
      "emit typed empty bootstrap metadata and a canonical empty sidecar" in {
        Given("an empty normalized Workflow collection")
        val workflows = Vector.empty[WorkflowDefinition]

        When("the generated ABI projection is requested")
        val generated = StateMachineWorkflowAbiGenerator.generate(workflows)
        val sidecar = StateMachineWorkflowAbiGenerator.canonicalJson(workflows)

        Then("the fixed schema and bootstrap remain present with no descriptor values")
        _realm_string(generated, "target/scala-3.3.8/src_managed/main/scala/domain/statemachine/workflow/StateMachineWorkflowAbi.scala") should include("cozy.cml.statemachine-workflow-abi.v1")
        _realm_string(generated, "target/scala-3.3.8/src_managed/main/scala/domain/statemachine/workflow/StateMachineWorkflowComponentFactoryBootstrap.scala") should include("componentFactoryMetadata: Vector[StateMachineWorkflowAbi.ComponentFactoryMetadata] = Vector.empty")
        sidecar shouldBe "{\"schemaVersion\":\"cozy.cml.statemachine-workflow-abi.v1\",\"workflows\":[]}"
      }

      "remain deterministic under ScalaCheck-driven output repetition" in {
        Given("one normalized Workflow and a generated repetition count")
        val workflow = CompositeStateMachineCml.workflowDefinitions(_model(_source())).head
        val repetitions = Gen.choose(1, 24)

        When("ScalaCheck repeats schema and sidecar generation")
        val property = Prop.forAll(repetitions) { count =>
          val sources = Vector.fill(count)(StateMachineWorkflowAbiGenerator.generate(Vector(workflow)))
            .map(_realm_string(_, "target/scala-3.3.8/src_managed/main/scala/domain/statemachine/workflow/OrderProgressStateMachineWorkflow1.scala"))
          val sidecars = Vector.fill(count)(StateMachineWorkflowAbiGenerator.canonicalJson(Vector(workflow)))
          sources.distinct.size == 1 && sidecars.distinct.size == 1
        }
        val result = Test.check(Test.Parameters.default.withMinSuccessfulTests(30), property)

        Then("every generated repetition is byte-identical")
        result.passed shouldBe true
      }
    }
  }

  private def _run(command: String, input: Path, out: Path): Unit =
    cozy.Cozy.main(Array(command, input.toString, "--save", out.toString))

  private def _realm_string(realm: Realm, path: String): String =
    realm.get(path).collect { case value: Realm.StringData => value.string }.getOrElse(fail(s"Missing generated artifact: $path"))

  private def _model(source: String): KaleidoxModel = {
    val model = KaleidoxModel.parseWitoutLocation(KaleidoxConfig.default, source)
    if (model.errors.nonEmpty)
      throw new IllegalArgumentException(s"CML fixture must parse without errors: ${model.errors.mkString(" | ")}")
    model
  }

  private def _model_with_location(source: String): KaleidoxModel = {
    val model = KaleidoxModel.parse(KaleidoxConfig.default, source)
    if (model.errors.nonEmpty)
      throw new IllegalArgumentException(s"CML fixture must parse without errors: ${model.errors.mkString(" | ")}")
    model
  }

  private def _source(): String =
    """# COMPONENT
      |
      |## Domain
      |
      |### PACKAGE
      |
      |domain
      |
      |# VALUE
      |
      |## PaymentCommand
      |- input-kind :: COMMAND
      |
      |### ATTRIBUTE
      || name | type   | multiplicity |
      ||------+--------+--------------|
      || id   | string | 1            |
      |
      |## PaymentResult
      |
      |### EXTENDS
      |
      |OperationResult
      |
      |# WORKFLOW
      |
      |## OrderProgress
      |
      |version = workflow-v1
      |
      |### COMPOSITE-STATEMACHINE
      |
      |#### CONSTITUENT
      |
      |##### payment
      |
      |state-machine = PaymentLifecycle
      |subject = payment
      |subject-type = PaymentCommand
      |
      |#### STATE
      |
      |##### Pending
      |
      |##### Complete
      |
      |#### DERIVATION
      |
      |##### pending
      |
      |state = Pending
      |when = payment.Awaiting
      |
      |##### complete
      |
      |state = Complete
      |when = payment.Paid
      |
      |#### ACTION
      |
      |##### capture-payment
      |
      |kind = OPERATION
      |operation = capturePayment
      |input = payment.subject
      |
      |### REQUIRED-OPERATION
      |
      |#### capture-payment-capability
      |
      |action = capture-payment
      |
      |# STATE-MACHINE
      |
      |## PaymentLifecycle
      |
      |### State
      |
      |#### Awaiting
      |
      |##### Transition
      |
      |to = Paid
      |on = captured
      |
      |#### Paid
      |
      |# SERVICE
      |
      |## OrderService
      |
      |### OPERATION
      |
      |#### capturePayment
      |
      |##### TYPE
      |
      |COMMAND
      |
      |##### INPUT
      |
      |###### TYPE
      |
      |PaymentCommand
      |
      |##### OUTPUT
      |
      |###### TYPE
      |
      |PaymentResult
      |""".stripMargin
}
