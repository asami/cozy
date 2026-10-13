package cozy.ui

import java.nio.charset.StandardCharsets
import cozy.ui.CozyLogicalUi._
import cozy.ui.CozyLogicalUiReadiness._
import cozy.ui.CozyLogicalUiReadinessReview._
import cozy.ui.CozyLogicalUiReview.ReviewDiagnostic
import cozy.ui.CozyLogicalUiSelection._
import cozy.ui.CozyLogicalUiSemantics._
import org.scalacheck.{Gen, Prop, Test}
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import play.api.libs.json._

/*
 * @since   Oct. 8, 2026
 * @version Oct. 13, 2026
 * @author  ASAMI, Tomoharu
 */
final class CozyLogicalUiReadinessReviewSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  "Complete structural review" should {
    "complete authority and human-readable declarations" which {
      "bind four complete source declarations, all scenario metadata and diagnostics without accepting" in {
        Given("the complete shared SalesOrder candidate and its 21 declared profile/outcome cases")
        val base = _ready
        val scenario = _scenario
        val kernel = _right(CozyLogicalUiReview.renderCandidate(base.kernelCandidate, base.projection, base.semantics))

        When("the structural producer consumes the exact complete candidate and explicit scenario")
        val preview = _right(renderCandidate(base, scenario))
        val json = Json.parse(preview.receiptJson)

        Then("full canonical product declarations are JSON subtrees and human-readable source evidence remains inert")
        preview.receipt.schema shouldBe "cozy.logical-ui-target-ready-review.v1"
        preview.receipt.version shouldBe 1
        preview.receipt.rendererProfile shouldBe "cozy.logical-ui-target-ready-review.renderer.v1"
        preview.receipt.binding shouldBe CompleteBinding(None)
        preview.receipt.sourceInputs shouldBe SourceInputs(base.kernelCandidate.canonicalContent,
          base.projection.canonicalContent, base.semantics.canonicalContent, base.canonicalContent)
        Vector("kernelContent" -> base.kernelCandidate.canonicalContent, "projectionContent" -> base.projection.canonicalContent,
          "semanticContent" -> base.semantics.canonicalContent, "readinessContent" -> base.canonicalContent).foreach { case (name, content) =>
          (json \ "sourceInputs" \ name).get shouldBe Json.parse(content)
          (json \ "sourceInputs" \ name).get.isInstanceOf[JsObject] shouldBe true
        }
        (json \ "binding" \ "decision").get shouldBe JsNull
        (json \ "scenario" \ "normal" \ "fixtureId").as[String] shouldBe CozyLogicalUiSalesOrderFixtures.readyFixtureId
        (json \ "scenario" \ "normal" \ "initialOrder" \ "label").as[String] shouldBe scenario.normal.initialOrder.label
        (json \ "scenario" \ "normal" \ "successfulOrder" \ "domainState" \ "stateId").as[String] shouldBe "confirmed"
        (json \ "scenario" \ "coverage").as[Vector[JsObject]].size shouldBe 21
        preview.receipt.scenario.coverage.map(_.coverageId).toSet shouldBe scenario.coverage.map(_.coverageId).toSet
        preview.receipt.sourceFlow.operationBinding shouldBe CozyLogicalUiSalesOrderFixtures.operationBinding
        preview.receipt.sourceFlow.stateMachineBinding shouldBe CozyLogicalUiSalesOrderFixtures.stateMachineBinding
        preview.receipt.sourceFlow.powertypeBinding shouldBe CozyLogicalUiSalesOrderFixtures.powertypeBinding
        preview.receipt.sourceFlow.powertypeVariantId shouldBe "awaiting-confirmation"
        preview.receipt.sourceFlow.from shouldBe scenario.normal.initialOrder.domainState
        preview.receipt.sourceFlow.to shouldBe scenario.normal.successfulOrder.domainState
        preview.receipt.sourceFlow.workflowState shouldBe Some(WorkflowStateReference("sales-order-workflow", "awaiting"))
        preview.receipt.outputs shouldBe Vector("review.html", "readiness-review-receipt.json")
        preview.diagnostics shouldBe kernel.diagnostics.sortBy(value => (value.code, value.path, value.reason))
        preview.receipt.diagnostics shouldBe preview.diagnostics
        preview.htmlBytes shouldBe preview.html.getBytes(StandardCharsets.UTF_8).toVector
        Vector("Complete authority and provenance", "Kernel candidate evidence", "Window placement, navigation and region persistence",
          "Source control roles and global focus", "Input intent, Value/Datatype and local constraint feedback",
          "Text identities, source attribution and preserved default values", "Outcomes: separate UI, domain, Workflow evidence and mapped recoveries",
          "Scenario normal records and flow", "Profile and outcome coverage", "Full canonical readinessContent",
          "required-text", "ascii-identifier", "DATATYPE_FORMAT", "recover", "awaiting").foreach { text => preview.html should include (text) }
        preview.html should not include ("<script")
        preview.html should not include ("<form")
        preview.html should not include ("<button")
        preview.html should not include ("<iframe")
      }

      "distinguish genuine complete acceptance and attribution from candidate and kernel evidence" in {
        Given("an explicit specification decision naming the exact complete candidate and reviewer")
        val base = _ready
        val decision = TargetReadyAcceptanceDecision("review-spec-decision", "review-spec-reviewer")
        val accepted = _right(CozyLogicalUiReadiness.accept(base, decision))
        val candidate = _right(renderCandidate(base, _scenario))

        When("genuine accepted and null source modes are projected and their roles are compared")
        val preview = _right(renderAccepted(accepted, _scenario))
        val json = Json.parse(preview.receiptJson)
        val failures = Vector(renderCandidate(null, _scenario), renderAccepted(null, _scenario), renderAccepted(accepted, null)).map(_left)

        Then("only accepted mode carries the complete attributed decision while both consume identical full declarations")
        preview.receipt.binding.decision shouldBe Some(decision)
        preview.receipt.sourceInputs shouldBe candidate.receipt.sourceInputs
        (json \ "binding" \ "decision" \ "reviewerId").as[String] shouldBe decision.reviewerId
        preview.html should include ("Accepted complete input; explicit attributed decision")
        preview.html should include ("Kernel candidate evidence")
        failures.map(_.path) shouldBe Vector("completeCandidate", "accepted", "scenario")
        failures.map(_.code).distinct shouldBe Vector("LUI501_REVIEW_CONTRACT_INVALID")
      }
    }

    "fail-closed complete scenario binding" which {
      "reject malformed, null, duplicate, missing and foreign coverage at its actual source path" in {
        Given("the one complete scenario with structurally independent invalid coverage cases")
        val scenario = _scenario
        val first = scenario.coverage.head
        val variants = Vector(
          scenario.copy(coverage = null) -> "scenario.coverage",
          scenario.copy(coverage = Vector(null)) -> "scenario.coverage[0]",
          scenario.copy(coverage = Vector(first.copy(coverageId = " padded "))) -> "scenario.coverage[0].coverageId",
          scenario.copy(coverage = Vector(first.copy(coverageId = null))) -> "scenario.coverage[0].coverageId",
          scenario.copy(coverage = Vector(first.copy(windowClass = null))) -> "scenario.coverage[0].windowClass",
          scenario.copy(coverage = Vector(first.copy(outcomeId = "foreign"))) -> "scenario.coverage[0].outcomeId",
          scenario.copy(coverage = scenario.coverage :+ first) -> "scenario.coverage[21].coverageId",
          scenario.copy(coverage = scenario.coverage :+ first.copy(coverageId = "distinct")) -> "scenario.coverage[21]",
          scenario.copy(coverage = scenario.coverage.tail) -> "scenario.coverage",
          scenario.copy(normal = scenario.normal.copy(coverageId = "foreign-normal")) -> "scenario.normal.coverageId",
          scenario.copy(normal = scenario.normal.copy(fixtureId = "legacy-fixture")) -> "scenario.normal.fixtureId")

        When("each malformed declaration reaches the shared projection admission boundary")
        val errors = variants.map { case (value, _) => _left(renderCandidate(_ready, value)) }

        Then("nothing is omitted silently and errors name the exact invalid declaration")
        errors.map(_.code).distinct shouldBe Vector("LUI501_REVIEW_CONTRACT_INVALID")
        errors.map(_.path) shouldBe variants.map(_._2)
      }

      "reject normal source flow and contradictory retained domain-state joins" in {
        Given("a normal source declaration with foreign interaction, step and from/to evidence variants")
        val base = _ready
        val scenario = _scenario
        val normal = scenario.normal
        val variants = Vector(
          scenario.copy(normal = normal.copy(flow = normal.flow.copy(commandInteractionId = "foreign"))) -> "scenario.normal.flow.commandInteractionId",
          scenario.copy(normal = normal.copy(flow = normal.flow.copy(step = normal.flow.step.copy(stepId = "foreign")))) -> "scenario.normal.flow.step",
          scenario.copy(normal = normal.copy(initialOrder = normal.initialOrder.copy(domainState = normal.successfulOrder.domainState))) -> "scenario.normal.initialOrder.domainState",
          scenario.copy(normal = normal.copy(successfulOrder = normal.successfulOrder.copy(domainState = normal.initialOrder.domainState))) -> "scenario.normal.successfulOrder.domainState")
        val contradiction = base.input.copy(outcomes = base.input.outcomes.map(outcome =>
          if (outcome.feedbackState == ConflictFeedback) outcome.copy(domainState = normal.successfulOrder.domainState) else outcome))
        val changed = _admit(base, contradiction)

        When("normal flow variants and a factory-admitted conflicting failure state are projected")
        val errors = variants.map { case (value, _) => _left(renderCandidate(base, value)) }
        val failure = _left(renderCandidate(changed, scenario))

        Then("the target preserves exact source operation evidence and refuses contradictory failure state")
        errors.map(_.path) shouldBe variants.map(_._2)
        failure.path shouldBe ("input.outcomes[" + changed.input.outcomes.indexWhere(_.feedbackState == ConflictFeedback) + "].domainState")
        failure.code shouldBe "LUI501_REVIEW_CONTRACT_INVALID"
      }
    }

    "deterministic declarations and complete currentness" which {
      "render identical complete observables for independent source and coverage permutations" in {
        Given("a complete baseline and independent randomized source declarations and coverage ordering")
        val base = _ready
        val baseline = _right(renderCandidate(base, _scenario))
        val property = Prop.forAll(Gen.choose(0, Int.MaxValue), Gen.choose(0, Int.MaxValue)) { (inputseed, coverageseed) =>
          val random = new scala.util.Random(inputseed)
          val kernelinput = base.kernelCandidate.input.copy(componentBindings = random.shuffle(base.kernelCandidate.input.componentBindings),
            componentSurfaces = random.shuffle(base.kernelCandidate.input.componentSurfaces).map(surface => surface.copy(exportIds = random.shuffle(surface.exportIds))))
          val kernel = _right(CozyLogicalUi.candidate(kernelinput))
          val projection = _right(CozyLogicalUi.project(kernel, base.projection.input.copy(
            screens = random.shuffle(base.projection.input.screens).map(screen => screen.copy(
              regions = random.shuffle(screen.regions), interactions = random.shuffle(screen.interactions), feedbackStates = random.shuffle(screen.feedbackStates))),
            steps = random.shuffle(base.projection.input.steps), mappings = random.shuffle(base.projection.input.mappings))))
          val semantics = _right(CozyLogicalUiSemantics.project(projection, base.semantics.input.copy(
            screens = random.shuffle(base.semantics.input.screens), componentBindings = random.shuffle(base.semantics.input.componentBindings),
            constraints = random.shuffle(base.semantics.input.constraints), feedbackAssociations = random.shuffle(base.semantics.input.feedbackAssociations),
            transitionActions = random.shuffle(base.semantics.input.transitionActions))))
          val input = base.input.copy(profiles = random.shuffle(base.input.profiles).map(profile => profile.copy(regions = random.shuffle(profile.regions))),
            controls = random.shuffle(base.input.controls), fields = random.shuffle(base.input.fields).map(field => field.copy(localRules = random.shuffle(field.localRules))),
            textSources = random.shuffle(base.input.textSources), outcomes = random.shuffle(base.input.outcomes).map(outcome => outcome.copy(recoveries = random.shuffle(outcome.recoveries))))
          val ready = _right(CozyLogicalUiReadiness.candidate(kernel, projection, semantics, input))
          val scenario = _scenario.copy(coverage = new scala.util.Random(coverageseed).shuffle(_scenario.coverage))
          val current = _right(renderCandidate(ready, scenario))
          current.html == baseline.html && current.receipt == baseline.receipt && current.receiptJson == baseline.receiptJson &&
            current.diagnostics == baseline.diagnostics
        }

        When("ScalaCheck constructs at least 30 independent complete projections")
        val result = Test.check(Test.Parameters.default.withMinSuccessfulTests(30), property)

        Then("declaration order cannot revise a complete structural product")
        result.passed shouldBe true
        result.succeeded should be >= 30
      }


    }

    "opaque source presentation" which {
      "round-trip Unicode, control, quotes, closing-script and padded text as escaped read-only evidence" in {
        Given("opaque labels, source identities, text IDs and order presentation with preserved whitespace")
        val opaque = "  日本語 \"<&> </script><script>source()</script>\n\t\u0001\u2028\u2029  "
        val base = _ready
        val originaltext = base.input.textSources.find(_.textId == "input-label").get
        val textid = "opaque\"<&>日本語"
        val sourceid = "source\"<&>日本語"
        val input = base.input.copy(textSources = base.input.textSources.map(text =>
          if (text == originaltext) text.copy(textId = textid, sourceIdentity = sourceid, defaultText = opaque) else text),
          controls = base.input.controls.map(control => if (control.role == InputControl) control.copy(labelTextId = textid) else control),
          fields = Vector(base.input.fields.head.copy(labelTextId = textid, initialValue = opaque)))
        val revised = _admit(base, input)
        val scenario = _scenario.copy(normal = _scenario.normal.copy(initialOrder = _scenario.normal.initialOrder.copy(label = opaque, amount = opaque),
          successfulOrder = _scenario.normal.successfulOrder.copy(label = opaque, amount = opaque)))

        When("the structural producer serializes complete product declarations and renders read-only sections")
        val preview = _right(renderCandidate(revised, scenario))
        val json = Json.parse(preview.receiptJson)
        val texts = (json \ "sourceInputs" \ "readinessContent" \ "readiness" \ "textSources").as[Vector[JsObject]]

        Then("JSON preserves actual values and HTML never creates executable source markup")
        (texts.find(text => (text \ "textId").as[String] == textid).get \ "defaultText").as[String] shouldBe opaque
        (texts.find(text => (text \ "textId").as[String] == textid).get \ "sourceIdentity").as[String] shouldBe sourceid
        (json \ "scenario" \ "normal" \ "initialOrder" \ "label").as[String] shouldBe opaque
        (json \ "scenario" \ "normal" \ "successfulOrder" \ "amount").as[String] shouldBe opaque
        preview.html should include ("  日本語 &quot;&lt;&amp;&gt; &lt;/script&gt;")
        preview.html should not include ("<script>")
        preview.html should include ("opaque&quot;&lt;&amp;&gt;日本語")
        preview.html should include ("source&quot;&lt;&amp;&gt;日本語")
      }
    }
  }

  private def _ready: TargetReadyCandidate = _right(CozyLogicalUiSalesOrderFixtures.readyCandidate())
  private def _scenario: ReadinessScenario = CozyLogicalUiSalesOrderFixtures.readinessWebScenario()
  private def _admit(base: TargetReadyCandidate, input: ReadinessInput): TargetReadyCandidate =
    _right(CozyLogicalUiReadiness.candidate(base.kernelCandidate, base.projection, base.semantics, input))
  private def _right[A](value: Either[LogicalUiError, A]): A = value match {
    case Right(result) => result
    case Left(error) => fail(error.toString)
  }
  private def _left[A](value: Either[LogicalUiError, A]): LogicalUiError = value match {
    case Left(error) => error
    case Right(_) => fail("expected structured rejection")
  }
}
