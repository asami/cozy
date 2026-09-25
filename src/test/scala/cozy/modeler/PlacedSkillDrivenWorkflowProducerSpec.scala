package cozy.modeler

import java.nio.file.{Files, Paths}
import org.goldenport.kaleidox.{Config => KaleidoxConfig, Model => KaleidoxModel}
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/** Additive placed-action fixture; the released Phase 62.3 fixture is unchanged. */
final class PlacedSkillDrivenWorkflowProducerSpec
    extends AnyWordSpec with Matchers with GivenWhenThen with ModelerSpecSupport {
  "The placed Skill-driven Workflow producer fixture" should {
    "generate one causally ordered ActionProgram alongside its Workflow ABI" in {
      Given("the additive CML fixture with placed Actions and explicit Provided Operations")
      val base = Paths.get(sys.props("user.dir")).toAbsolutePath.normalize()
      val input = base.resolve("src/test/resources/modeler/skill-driven-workflow-executable.cml")
      val first = base.resolve("target/test-generated/skill-driven-workflow-executable-first")
      val second = base.resolve("target/test-generated/skill-driven-workflow-executable-second")
      When("the CML fixture is normalized")
      val model = KaleidoxModel.parseWitoutLocation(KaleidoxConfig.default, Files.readString(input))
      Then("the normalized model preserves the intended Workflow and Action declarations")
      model.errors shouldBe empty
      val workflow = CompositeStateMachineCml.workflowDefinitions(model).head
      workflow.identity shouldBe "WorkflowProducerPlaced"
      workflow.compositeStateMachine.actions.map(_.identity) shouldBe
        Vector("BuildProject", "RunTests", "ReviewChange", "CommitChanges")
      workflow.compositeStateMachine.constituentActions.size shouldBe 4
      workflow.requiredOperations.map(_.action.identity) shouldBe Vector("ReviewChange")
      workflow.providedOperations.map(_.operation.name) shouldBe Vector("beginReview", "submitReview")

      When("both public generation runs consume the same CML fixture")
      delete_recursively(first)
      delete_recursively(second)
      run_modeler_scala(input, first)
      run_modeler_scala(input, second)
      val actionpath = "target/cozy/cml-logical-action-program.json"
      val workflowpath = "target/cozy/statemachine-workflow-abi.json"
      val providedpath = StateMachineProvidedApiAbiGenerator.metadataPath
      val firstaction = Files.readString(first.resolve(actionpath))
      val secondaction = Files.readString(second.resolve(actionpath))
      val firstworkflow = Files.readString(first.resolve(workflowpath))
      val firstprovided = Files.readString(first.resolve(providedpath))

      Then("the Workflow, ActionProgram, and Provided API sidecars are deterministic and aligned")
      firstaction shouldBe secondaction
      Files.readAllBytes(first.resolve(workflowpath)).toVector shouldBe
        Files.readAllBytes(second.resolve(workflowpath)).toVector
      Files.readAllBytes(first.resolve(providedpath)).toVector shouldBe
        Files.readAllBytes(second.resolve(providedpath)).toVector
      firstworkflow should include ("\"identity\":\"WorkflowProducerPlaced\"")
      firstworkflow should include ("\"actionIdentity\":\"ReviewChange\"")
      firstprovided should include ("\"operation\":\"beginReview\"")
      firstprovided should include ("\"operation\":\"submitReview\"")
      firstprovided should include ("\"resultType\":\"WorkflowInteraction\"")
      firstaction should include ("\"schemaVersion\":\"cozy.cml.logical-action-program.v1\"")
      val ordered = Vector(
        "constituent:build-project-before-review:1",
        "constituent:run-tests-before-review:2",
        "constituent:review-change-boundary:3",
        "constituent:commit-changes-after-review:4"
      )
      val positions = ordered.map(id => firstaction.indexOf("\"occurrenceId\":\"" + id + "\""))
      positions.forall(_ >= 0) shouldBe true
      positions shouldBe positions.sorted
      positions.distinct.size shouldBe 4
      firstaction should include ("\"placement\":\"exit\"")
      firstaction should include ("\"placement\":\"transition\"")
      firstaction should include ("\"placement\":\"entry\"")
      firstaction should include ("\"effectClass\":\"EXTERNAL\"")
      firstaction should include ("\"transactionRequirement\":\"OUTSIDE_UNIT_OF_WORK\"")
    }
  }
}
