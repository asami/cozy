package cozy.modeler

import java.nio.file.{Files, Paths}
import org.goldenport.kaleidox.{Config => KaleidoxConfig, Model => KaleidoxModel}
import org.scalatest.GivenWhenThen
import org.scalatest.wordspec.AnyWordSpec

final class StateMachineProvidedApiAbiGenerationSpec
  extends AnyWordSpec with GivenWhenThen with ModelerSpecSupport {
  private val _base = Paths.get(sys.props("user.dir")).toAbsolutePath.normalize()
  private val _fixture = _base.resolve("src/test/resources/modeler/skill-driven-workflow-producer.cml")
  private val _provided =
    """### OPERATION
      |
      |#### beginReview
      |
      |operation = WorkflowService.beginReview
      |
      |""".stripMargin

  "StateMachine Provided API producer ABI" should {
    "lower an explicit CML Provided operation to additive Scala and JSON metadata" in {
      Given("a WORKFLOW with one explicitly qualified Provided operation")
      val input = _base.resolve("target/test-input/statemachine-provided-api.cml")
      val normalout = _base.resolve("target/test-generated/statemachine-provided-api-normal")
      val valueout = _base.resolve("target/test-generated/statemachine-provided-api-value")
      delete_recursively(normalout)
      delete_recursively(valueout)
      write_file(input, _source(_provided))
      val normalized = CompositeStateMachineCml.workflowDefinitions(_model(_source(_provided))).head
      val api = StateMachineApiSpi.fromWorkflow(normalized, _ => StateMachineRequiredOperationMetadata(
        ContextContract("review-context", Vector.empty, Vector.empty),
        CompletionContract("review-completion", Vector.empty),
        EvidenceContract("review-evidence", Vector.empty),
        Vector.empty
      ))

      When("both public Scala routes generate twice")
      _run("modeler-scala", input, normalout)
      val first = tree_snapshot(normalout)
      _run("modeler-scala", input, normalout)
      _run("modeler-scala-value", input, valueout)

      Then("the additive metadata retains operation identity, types, source, and deterministic output")
      tree_snapshot(normalout) shouldBe first
      val root = "target/scala-3.3.8/src_managed/main/scala/domain/statemachine/providedapi"
      val abi = normalout.resolve(root).resolve("StateMachineProvidedApiAbi.scala")
      val bootstrap = normalout.resolve(root).resolve("StateMachineProvidedApiComponentFactoryBootstrap.scala")
      val descriptor = normalout.resolve(root).resolve("WorkflowProducerStateMachineProvidedApi1.scala")
      val sidecar = normalout.resolve(StateMachineProvidedApiAbiGenerator.metadataPath)
      Vector(abi, bootstrap, descriptor, sidecar).foreach(path => Files.exists(path) shouldBe true)
      Files.readString(abi) should include("cozy.cml.statemachine-provided-api-abi.v1")
      Files.readString(abi) should include("GENERATOR: String = \"cozy.modeler.StateMachineProvidedApiAbiGenerator\"")
      Files.readString(descriptor) should include("generator = \"cozy.modeler.StateMachineProvidedApiAbiGenerator\"")
      Files.readString(descriptor) should include("WorkflowService\", \"beginReview\", Some(\"ReviewContext\"), Some(\"ReviewResult\")")
      Files.readString(descriptor) should include("SourceIdentity(Some(")
      Files.readString(bootstrap) should include("WorkflowProducerStateMachineProvidedApi1.componentFactoryMetadata")
      Files.readString(sidecar) should include("\"generator\":\"cozy.modeler.StateMachineProvidedApiAbiGenerator\"")
      Files.readString(sidecar) should include("\"service\":\"WorkflowService\",\"operation\":\"beginReview\",\"inputType\":\"ReviewContext\",\"resultType\":\"ReviewResult\"")
      api.providedOperations shouldBe Vector(StateMachineProvidedOperation(
        StateMachineOperationIdentity("WorkflowService", "beginReview"),
        Some(StateMachineInputTypeReference("ReviewContext")),
        Some(StateMachineResultTypeReference("ReviewResult"))
      ))
      Vector(abi, bootstrap, descriptor, sidecar).foreach { path =>
        Files.readAllBytes(path).toVector shouldBe Files.readAllBytes(valueout.resolve(normalout.relativize(path))).toVector
      }

      And("the closed Workflow ABI v1 output is unchanged by the additive Provided declaration")
      val baseline = _base.resolve("target/test-generated/statemachine-provided-api-baseline")
      val baselineinput = _base.resolve("target/test-input/statemachine-provided-api-baseline.cml")
      delete_recursively(baseline)
      write_file(baselineinput, _source(""))
      _run("modeler-scala", baselineinput, baseline)
      val legacy = "target/scala-3.3.8/src_managed/main/scala/domain/statemachine/workflow"
      Files.readString(normalout.resolve(legacy).resolve("StateMachineWorkflowAbi.scala")) shouldBe
        Files.readString(baseline.resolve(legacy).resolve("StateMachineWorkflowAbi.scala"))
      Files.readString(normalout.resolve(legacy).resolve("WorkflowProducerStateMachineWorkflow1.scala")) shouldBe
        Files.readString(baseline.resolve(legacy).resolve("WorkflowProducerStateMachineWorkflow1.scala"))
      Files.exists(baseline.resolve(StateMachineProvidedApiAbiGenerator.metadataPath)) shouldBe false
    }

    "reject ambiguous or missing explicit CML operation declarations" in {
      Given("the same WORKFLOW with malformed Provided operation sections")
      val unknown = _provided.replace("WorkflowService.beginReview", "WorkflowService.notDeclared")
      val unqualified = _provided.replace("WorkflowService.beginReview", "beginReview")
      val duplicate = _provided + _provided
      val mismatched = _provided.replace("#### beginReview", "#### otherName")
      val oldheading = _provided.replace("### OPERATION", "### PROVIDED-OPERATION")

      When("the normalized CML parser reads each variant")
      val errors = Vector(unknown, unqualified, duplicate, mismatched, oldheading).map { section =>
        intercept[RuntimeException] {
          CompositeStateMachineCml.workflowDefinitions(_model(_source(section)))
        }
      }

      Then("none is admitted by name inference or ambiguous section placement")
      errors(0).getMessage should include("must resolve to one qualified normalized CML Operation")
      errors(1).getMessage should include("must resolve to one qualified normalized CML Operation")
      errors(2).getMessage should include("at most one OPERATION section")
      errors(3).getMessage should include("identity must match OPERATION")
      errors(4).getMessage should include("does not admit misplaced structural section 'PROVIDED-OPERATION'")
    }
  }

  private def _source(provided: String): String =
    Files.readString(_fixture)
      .replace(
        "#### reviewChange\n\n##### TYPE",
        """#### beginReview
          |
          |##### TYPE
          |
          |COMMAND
          |
          |##### INPUT
          |
          |###### TYPE
          |
          |ReviewContext
          |
          |##### OUTPUT
          |
          |###### TYPE
          |
          |ReviewResult
          |
          |#### reviewChange
          |
          |##### TYPE""".stripMargin
      )
      .replace("# STATE-MACHINE", provided + "# STATE-MACHINE")

  private def _model(source: String): KaleidoxModel = {
    val model = KaleidoxModel.parseWitoutLocation(KaleidoxConfig.default, source)
    if (model.errors.nonEmpty)
      throw new IllegalArgumentException(s"CML fixture must parse: ${model.errors.mkString(" | ")}")
    model
  }

  private def _run(command: String, input: java.nio.file.Path, out: java.nio.file.Path): Unit =
    cozy.Cozy.main(Array(command, input.toString, "--save", out.toString))
}
