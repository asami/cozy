import domain.HistoryComponent
import org.goldenport.cncf.statemachine.CmlStateMachineTransitionTarget
import org.goldenport.cncf.statemachine.HistoryRecordWrite
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Aug. 14, 2026
 * @version Aug. 14, 2026
 * @author  ASAMI, Tomoharu
 */
final class GeneratedStateMachineHistoryRuntimeSpec
  extends AnyWordSpec
  with Matchers
  with GivenWhenThen {

  "Compiled generated CML history runtime" should {
    "preserve named shallow-history metadata and flattened state values" in {
      Given("a compiled generated component with Review named shallow history")
      val component = new HistoryComponent()

      When("the generated lifecycle definition and rules are inspected")
      val definition = component.stateMachineDefinitions.find(_.name == "lifecycle").get
      val submit = component.stateMachineTransitionRules.find(_.eventName == "submit").get
      val approve = component.stateMachineTransitionRules.find(_.eventName == "approve").get
      val suspend = component.stateMachineTransitionRules.find(_.eventName == "suspend").get
      val resume = component.stateMachineTransitionRules.find(_.eventName == "resume").get

      Then("the named history composite retains its direct leaves and fallback")
      definition.states.toSet shouldBe Set("Draft", "Pending", "Approved", "Suspended")
      definition.historyFieldName shouldBe Some("lifecycleHistory")
      definition.historyComposites.map(x => x.name -> x.directLeaves) shouldBe
        Vector("Review" -> Vector("Pending", "Approved"))
      definition.historyComposites.flatMap(_.fallbackLeaf) shouldBe Vector("Pending")
      resume.historyCompositeName shouldBe Some("Review")
      resume.historyDirectLeaves shouldBe Vector("Pending", "Approved")
      resume.historyFallbackLeaf shouldBe Some("Pending")

      And("enter, direct-leaf move, and leave rules preserve flattened values and required writes")
      submit.fromStateValue shouldBe Some(1)
      submit.toStateValue shouldBe Some(2)
      submit.expectedHistoryRecordWrites shouldBe Vector(HistoryRecordWrite("Review", "Pending"))
      approve.fromStateValue shouldBe Some(2)
      approve.toStateValue shouldBe Some(3)
      approve.expectedHistoryRecordWrites shouldBe Vector(HistoryRecordWrite("Review", "Approved"))
      suspend.fromStateValue shouldBe Some(3)
      suspend.toStateValue shouldBe Some(4)
      suspend.expectedHistoryRecordWrites shouldBe Vector(HistoryRecordWrite("Review", "Approved"))
    }

    "load the generated normalized transition ABI through its typed provider" in {
      Given("the compiled generated HistoryComponent")
      val component = new HistoryComponent()

      When("the lifecycle definition is loaded through its typed normalized API")
      val definition = component.stateMachineDefinitions
        .find(_.name == "lifecycle")
        .getOrElse(fail("generated lifecycle definition is missing"))
      val normalized = definition.normalized
        .getOrElse(fail("generated lifecycle definition is missing normalized ABI data"))
      val resume = normalized.transitions
        .find(_.trigger.identity.name == "resume")
        .getOrElse(fail("generated lifecycle resume transition is missing"))

      Then("the normalized machine and initial state retain their typed identities")
      normalized.identity.name shouldBe "lifecycle"
      normalized.initialState.machine shouldBe normalized.identity
      normalized.initialState.path.render shouldBe "Draft"

      And("the generated transitions retain declaration-order identities")
      normalized.transitions.map { transition =>
        transition.identity.machine.name -> transition.identity.declarationOrder
      } shouldBe Vector(
        "lifecycle" -> 0,
        "lifecycle" -> 1,
        "lifecycle" -> 2,
        "lifecycle" -> 3
      )

      And("the resume transition encodes Review shallow history and its Pending fallback")
      resume.target match {
        case CmlStateMachineTransitionTarget.ShallowHistory(target) =>
          target.composite.machine shouldBe normalized.identity
          target.composite.path.render shouldBe "Review"
          target.fallbackLeaf.machine shouldBe normalized.identity
          target.fallbackLeaf.path.render shouldBe "Review/Pending"
        case other =>
          fail(s"expected a Review shallow-history target but got $other")
      }
    }
  }
}
