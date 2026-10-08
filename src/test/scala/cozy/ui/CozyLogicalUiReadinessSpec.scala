package cozy.ui

import cozy.ui.CozyLogicalUi.{candidate => _, accept => _, _}
import cozy.ui.CozyLogicalUiReadiness._
import cozy.ui.CozyLogicalUiSelection._
import cozy.ui.CozyLogicalUiSemantics._
import org.scalacheck.{Gen, Prop, Test}
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import play.api.libs.json.Json

/*
 * @since   Oct. 8, 2026
 * @version Oct. 8, 2026
 * @author  ASAMI, Tomoharu
 */
final class CozyLogicalUiReadinessSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  "Complete target-neutral Logical UI admission" should {
    "the shared SalesOrder declaration" which {
      "admit one complete source chain covering three profiles, seven controls and all declared feedback outcomes" in {
        Given("the additive SalesOrder mappings, semantic associations and readiness declarations")
        val legacy = _right(CozyLogicalUiSalesOrderFixtures.candidate())

        When("the complete fixture is constructed through the existing and readiness factories")
        val ready = _right(CozyLogicalUiSalesOrderFixtures.readyCandidate())

        Then("one complete candidate retains the original kernel and all profile, field, outcome and source joins")
        ready.kernelCandidate.identity shouldBe legacy._1.identity
        ready.projection.identity should not be legacy._2.identity
        ready.input.profiles.map(_.windowClass).toSet shouldBe Set(CompactIntent, MediumIntent, ExpandedIntent)
        ready.input.profiles.find(_.windowClass == MediumIntent).get.detailPlacement shouldBe NavigateToDetail
        ready.input.profiles.find(_.windowClass == MediumIntent).get.persistentNavigation shouldBe true
        ready.input.profiles.find(_.windowClass == ExpandedIntent).get.regions.map(_.persistent).distinct shouldBe Vector(true)
        ready.input.controls.size shouldBe 7
        ready.input.controls.flatMap(_.focusOrder).sorted shouldBe Vector(1, 2, 3, 4)
        ready.input.fields.head.initialValue shouldBe "SO-001"
        ready.input.fields.head.localRules.map(_.ruleKind).toSet shouldBe Set(RequiredTextRule, AsciiIdentifierRule)
        ready.input.outcomes.map(_.feedbackState).toSet shouldBe Set(
          NormalFeedback, LoadingFeedback, EmptyFeedback, ValidationFailedFeedback,
          ConflictFeedback, UnavailableFeedback, OperationFailedFeedback)
        ready.input.outcomes.find(_.feedbackState == NormalFeedback).get.domainState.stateId shouldBe "confirmed"
        ready.input.outcomes.filterNot(_.feedbackState == NormalFeedback).map(_.domainState.stateId).distinct shouldBe Vector("draft")
        ready.input.outcomes.find(_.feedbackState == LoadingFeedback).get.uiState shouldBe PendingInteractionState
        ready.input.outcomes.find(_.feedbackState == EmptyFeedback).get.uiState shouldBe IdleInteractionState
        ready.input.outcomes.find(_.feedbackState == UnavailableFeedback).get.recoveries.head.kind shouldBe RetryInteraction
        ready.input.outcomes.find(_.feedbackState == ConflictFeedback).get.recoveries.head.kind shouldBe EditInput
        ready.input.outcomes.find(_.feedbackState == NormalFeedback).get.workflowState shouldBe
          Some(WorkflowStateReference("sales-order-workflow", "awaiting"))
        CozyLogicalUiSalesOrderFixtures.readyFixtureId shouldBe "sales-order-target-ready-v1"
        CozyLogicalUiReadiness.schema shouldBe "cozy.logical-ui-target-ready.v1"
        CozyLogicalUiReadiness.version shouldBe 1
        ready.identity should not be ready.inputIdentity
      }

      "retain invalid and empty initial text as review data without evaluating local or domain predicates" in {
        Given("a required input field with exact local declarations and opaque initial review values")
        val base = _ready
        val values = Vector("", "lowercase", " ", "SO-001")
        val inputs = values.map(value => base.input.copy(fields = Vector(base.input.fields.head.copy(initialValue = value))))

        When("each complete declaration is admitted without executing its predicates")
        val results = inputs.map(input => _right(_admit(base, input)))

        Then("all supplied initial text survives and source context/server constraints remain separate evidence")
        results.map(_.input.fields.head.initialValue) shouldBe values
        results.head.semantics.input.constraints.map(_.validation).toSet shouldBe Set(
          LocalDeterministicValidation, ContextDependentValidation, ServerAuthoritativeValidation)
      }

      "admit mapped return-to-entry recovery and decimal keyboard intent as closed target-neutral declarations" in {
        Given("an admitted failure outcome, its mapped list entry and a declared input field")
        val base = _ready
        val outcome = base.input.outcomes.find(_.feedbackState == ConflictFeedback).get
        val recovery = RecoveryBinding(ReturnToEntry, UiUseCaseStep(CozyLogicalUiSalesOrderFixtures.uiUseCase,
          "recover", AlternativePath), InteractionRef("order-list", "entry"))
        val input = base.input.copy(fields = Vector(base.input.fields.head.copy(inputIntent = DecimalInputIntent)),
          outcomes = Vector(outcome.copy(recoveries = Vector(recovery))))

        When("the additional closed metadata joins the same source authority")
        val result = _right(_admit(base, input))

        Then("input intent and recovery remain inert typed declarations")
        result.input.fields.head.inputIntent shouldBe DecimalInputIntent
        result.input.outcomes.head.recoveries shouldBe Vector(recovery)
      }

      "retain source Query interactions as observation controls without focus ranks" in {
        Given("a factory-admitted screen whose existing observation interaction is declared as a source query")
        val base = _ready
        val source = base.projection.input.copy(screens = base.projection.input.screens.map(screen =>
          screen.copy(interactions = screen.interactions.map(interaction =>
            if (screen.id == "order-detail" && interaction.id == "observe") interaction.copy(kind = QueryInteraction) else interaction))))
        val projection = _right(CozyLogicalUi.project(base.kernelCandidate, source))
        val semantics = _right(CozyLogicalUiSemantics.project(projection,
          base.semantics.input.copy(projectionIdentity = projection.identity)))

        When("the same observation metadata joins the admitted source query chain")
        val result = _right(candidate(base.kernelCandidate, projection, semantics,
          base.input.copy(semanticProjectionIdentity = semantics.identity)))

        Then("query remains inert observation meaning with no interactive focus rank")
        val control = result.input.controls.find(_.control == InteractionRef("order-detail", "observe")).get
        control.role shouldBe ObservationControl
        control.focusOrder shouldBe None
      }
    }

    "source-chain precedence" which {
      "reject missing roots before traversing malformed readiness descendants" in {
        Given("a valid complete chain and null root variants with a null profile vector")
        val base = _ready
        val malformed = base.input.copy(profiles = null)

        When("each root boundary is admitted")
        val errors = Vector(
          candidate(null, base.projection, base.semantics, malformed),
          candidate(base.kernelCandidate, null, base.semantics, malformed),
          candidate(base.kernelCandidate, base.projection, null, malformed),
          candidate(base.kernelCandidate, base.projection, base.semantics, null)).map(_left)

        Then("stable root diagnostics identify the first missing value without null exceptions")
        errors.map(_.code) shouldBe Vector("LUI501_CHAIN_INVALID", "LUI501_CHAIN_INVALID", "LUI501_CHAIN_INVALID", "LUI501_INPUT_INVALID")
        errors.map(_.path) shouldBe Vector("kernelCandidate", "projection", "semantics", "input")
      }

      "reject factory-produced foreign candidate, projection, semantics and consumed semantic identity" in {
        Given("a complete chain and a distinct factory-produced legacy and revised source chain")
        val base = _ready
        val legacy = _right(CozyLogicalUiSalesOrderFixtures.candidate())
        val foreign = _right(CozyLogicalUi.candidate(base.kernelCandidate.input.copy(useCases =
          base.kernelCandidate.input.useCases.copy(ui = UseCaseReference(Ui, "other-ui"),
            realizations = Vector(UseCaseRealization(CozyLogicalUiSalesOrderFixtures.businessUseCase,
              CozyLogicalUiSalesOrderFixtures.systemUseCase), UseCaseRealization(CozyLogicalUiSalesOrderFixtures.systemUseCase,
              UseCaseReference(Ui, "other-ui")))))))
        val malformed = base.input.copy(semanticProjectionIdentity = "foreign", profiles = null)

        When("the complete factory receives disagreeing roots and a mismatched readiness source")
        val errors = Vector(
          candidate(foreign, base.projection, base.semantics, base.input),
          candidate(base.kernelCandidate, legacy._2, base.semantics, base.input),
          candidate(base.kernelCandidate, base.projection, legacy._3, base.input),
          _admit(base, malformed)).map(_left)

        Then("chain mismatches precede metadata diagnostics")
        errors.map(_.code).distinct shouldBe Vector("LUI501_CHAIN_INVALID")
        errors.map(_.path) shouldBe Vector("projection.candidate", "semantics.projectionIdentity",
          "semantics.projectionIdentity", "input.semanticProjectionIdentity")
      }

      "require source factory admission of exact Operation, StateMachine and Workflow transition evidence" in {
        Given("one genuine semantic projection and transition declarations with foreign operation, machine or malformed Workflow data")
        val base = _ready
        val action = base.semantics.input.transitionActions.head
        val actions = Vector(
          action.copy(operation = CozyLogicalUiSalesOrderFixtures.valueBinding),
          action.copy(stateMachine = CozyLogicalUiSalesOrderFixtures.operationBinding),
          action.copy(workflowState = Some(WorkflowStateReference("", "awaiting"))),
          action.copy(workflowState = Some(null)),
          action.copy(step = action.step.copy(stepId = "undeclared")))

        When("the existing semantic factory attempts to produce source authority for complete readiness")
        val errors = actions.map(value => _left(CozyLogicalUiSemantics.project(base.projection,
          base.semantics.input.copy(transitionActions = Vector(value)))))

        Then("the complete chain cannot consume invented or malformed source transitions")
        errors.map(_.code) shouldBe Vector("LUI43_SEMANTICS_TRANSITION_ADMISSION_INVALID",
          "LUI43_SEMANTICS_TRANSITION_ADMISSION_INVALID", "LUI43_SEMANTICS_STATE_INVALID",
          "LUI43_SEMANTICS_STATE_INVALID", "LUI43_SEMANTICS_TRANSITION_ADMISSION_INVALID")
        errors.map(_.path) shouldBe Vector("semantics.transitionActions[0].operation", "semantics.transitionActions[0].stateMachine",
          "semantics.transitionActions[0].workflowState", "semantics.transitionActions[0].workflowState", "semantics.transitionActions[0].step")
      }
    }

    "structured metadata diagnostics" which {
      "reject null vectors and elements at their exact declaration paths" in {
        Given("a complete input with each root vector and declaration element replaced by null")
        val base = _ready
        val input = base.input
        val variants = Vector(
          input.copy(profiles = null), input.copy(controls = null), input.copy(fields = null),
          input.copy(textSources = null), input.copy(outcomes = null),
          input.copy(profiles = Vector(null)), input.copy(controls = Vector(null)),
          input.copy(fields = Vector(null)), input.copy(textSources = Vector(null)), input.copy(outcomes = Vector(null)))

        When("each malformed declaration reaches admission")
        val errors = variants.map(value => _left(_admit(base, value)))

        Then("nullable declaration structure fails without dereferencing it")
        errors.map(_.code).distinct shouldBe Vector("LUI501_ELEMENT_INVALID")
        errors.map(_.path) shouldBe Vector("input.profiles", "input.controls", "input.fields", "input.textSources", "input.outcomes",
          "input.profiles[0]", "input.controls[0]", "input.fields[0]", "input.textSources[0]", "input.outcomes[0]")
      }

      "reject missing, foreign, duplicate and contradictory profiles and regions" in {
        Given("exact profile declarations with omitted window classes and invalid region or placement variants")
        val base = _ready
        val input = base.input
        val profile = input.profiles.head
        val region = profile.regions.head
        val variants = Vector(
          input.copy(profiles = input.profiles.tail),
          input.copy(profiles = input.profiles :+ profile),
          input.copy(profiles = profile.copy(windowClass = null) +: input.profiles.tail),
          input.copy(profiles = profile.copy(detailPlacement = UpdateDetailRegion) +: input.profiles.tail),
          input.copy(profiles = profile.copy(regions = null) +: input.profiles.tail),
          input.copy(profiles = profile.copy(regions = Vector(null)) +: input.profiles.tail),
          input.copy(profiles = profile.copy(regions = profile.regions.tail) +: input.profiles.tail),
          input.copy(profiles = profile.copy(regions = profile.regions :+ region) +: input.profiles.tail),
          input.copy(profiles = profile.copy(regions = region.copy(region = RegionRef("foreign", "root")) +: profile.regions.tail) +: input.profiles.tail),
          input.copy(profiles = profile.copy(regions = region.copy(region = null) +: profile.regions.tail) +: input.profiles.tail),
          input.copy(profiles = profile.copy(regions = region.copy(role = null) +: profile.regions.tail) +: input.profiles.tail),
          input.copy(profiles = profile.copy(regions = profile.regions.map(_.copy(role = SecondaryRegion))) +: input.profiles.tail))

        When("all profile and region variants are interpreted")
        val errors = variants.map(value => _left(_admit(base, value)))

        Then("each error has a stable structured code and the exact input location")
        errors.map(_.code) shouldBe Vector("LUI501_PROFILE_INVALID", "LUI501_DUPLICATE_INVALID", "LUI501_PROFILE_INVALID",
          "LUI501_PROFILE_INVALID", "LUI501_ELEMENT_INVALID", "LUI501_ELEMENT_INVALID", "LUI501_REGION_INVALID",
          "LUI501_DUPLICATE_INVALID", "LUI501_REGION_INVALID", "LUI501_REGION_INVALID", "LUI501_REGION_INVALID", "LUI501_REGION_INVALID")
        errors.map(_.path) shouldBe Vector("input.profiles", "input.profiles[3]", "input.profiles[0].windowClass",
          "input.profiles[0].detailPlacement", "input.profiles[0].regions", "input.profiles[0].regions[0]",
          "input.profiles[0].regions", "input.profiles[0].regions[3]", "input.profiles[0].regions[0].region",
          "input.profiles[0].regions[0].region", "input.profiles[0].regions[0].role", "input.profiles[0].regions")
      }

      "reject incomplete, foreign, incompatible or null control roles and invalid global focus ranks" in {
        Given("one complete control set with invalid references, labels, role compatibility and focus containers")
        val base = _ready
        val input = base.input
        val control = input.controls.head
        val focusable = input.controls.find(_.role == InputControl).get
        val focusindex = input.controls.indexOf(focusable)
        val variants = Vector(
          input.copy(controls = input.controls.tail), input.copy(controls = input.controls :+ control),
          input.copy(controls = control.copy(control = null) +: input.controls.tail),
          input.copy(controls = control.copy(control = InteractionRef("foreign", "interaction")) +: input.controls.tail),
          input.copy(controls = control.copy(role = null) +: input.controls.tail),
          input.copy(controls = control.copy(role = CommandControl) +: input.controls.tail),
          input.copy(controls = control.copy(labelTextId = "missing") +: input.controls.tail),
          input.copy(controls = control.copy(focusOrder = null) +: input.controls.tail),
          input.copy(controls = control.copy(focusOrder = Some(1)) +: input.controls.tail),
          input.copy(controls = input.controls.updated(focusindex, focusable.copy(focusOrder = None))),
          input.copy(controls = input.controls.updated(focusindex, focusable.copy(focusOrder = Some(0)))),
          input.copy(controls = input.controls.updated(focusindex, focusable.copy(focusOrder = Some(1)))))

        When("every invalid control set is admitted")
        val errors = variants.map(value => _left(_admit(base, value)))

        Then("coverage, reference, role, label and global focus failures remain distinct")
        errors.map(_.code) shouldBe Vector("LUI501_CONTROL_INVALID", "LUI501_DUPLICATE_INVALID",
          "LUI501_CONTROL_INVALID", "LUI501_CONTROL_INVALID", "LUI501_CONTROL_INVALID", "LUI501_CONTROL_INVALID",
          "LUI501_TEXT_REFERENCE_INVALID", "LUI501_FOCUS_INVALID", "LUI501_FOCUS_INVALID",
          "LUI501_FOCUS_INVALID", "LUI501_FOCUS_INVALID", "LUI501_DUPLICATE_INVALID")
        errors(4).path shouldBe "input.controls[0].role"
        errors(7).path shouldBe "input.controls[0].focusOrder"
        errors.last.path should startWith("input.controls.focusOrder[")
      }

      "reject invalid text identities, blank display text and unresolved message sources" in {
        Given("text source declarations with strict IDs and preserved display whitespace")
        val base = _ready
        val input = base.input
        val text = input.textSources.head
        val outcome = input.outcomes.head
        val variants = Vector(
          input.copy(textSources = text.copy(textId = null) +: input.textSources.tail),
          input.copy(textSources = text.copy(sourceIdentity = " source") +: input.textSources.tail),
          input.copy(textSources = text.copy(defaultText = null) +: input.textSources.tail),
          input.copy(textSources = text.copy(defaultText = " \t\n ") +: input.textSources.tail),
          input.copy(textSources = input.textSources :+ text),
          input.copy(outcomes = Vector(outcome.copy(messageTextId = "undeclared"))))

        When("text and message declarations reach source admission")
        val errors = variants.map(value => _left(_admit(base, value)))

        Then("text shape, duplicate identity and missing source diagnostics identify exact fields")
        errors.map(_.code) shouldBe Vector.fill(4)("LUI501_TEXT_INVALID") ++
          Vector("LUI501_DUPLICATE_INVALID", "LUI501_TEXT_REFERENCE_INVALID")
        errors.take(4).map(_.path) shouldBe Vector("input.textSources[0].textId", "input.textSources[0].sourceIdentity",
          "input.textSources[0].defaultText", "input.textSources[0].defaultText")
        errors.last.path shouldBe "input.outcomes[0].messageTextId"
      }

      "reject missing, duplicate and foreign field bindings, null values and missing required predicates" in {
        Given("an exact source Input interaction and invalid field metadata variants")
        val base = _ready
        val field = base.input.fields.head
        val variants = Vector(
          base.input.copy(fields = Vector.empty), base.input.copy(fields = Vector(field, field)),
          base.input.copy(fields = Vector(field.copy(fieldId = " field"))),
          base.input.copy(fields = Vector(field.copy(control = null))),
          base.input.copy(fields = Vector(field.copy(control = InteractionRef("order-detail", "invoke")))),
          base.input.copy(fields = Vector(field.copy(valueBinding = CozyLogicalUiSalesOrderFixtures.entityBinding))),
          base.input.copy(fields = Vector(field.copy(datatypeBinding = null))),
          base.input.copy(fields = Vector(field.copy(labelTextId = "unknown"))),
          base.input.copy(fields = Vector(field.copy(inputIntent = null))),
          base.input.copy(fields = Vector(field.copy(initialValue = null))),
          base.input.copy(fields = Vector(field.copy(localRules = null))),
          base.input.copy(fields = Vector(field.copy(localRules = Vector(null)))),
          base.input.copy(fields = Vector(field.copy(localRules = Vector.empty))))

        When("field declarations are joined to the exact semantic Value and Datatype usages")
        val errors = variants.map(value => _left(_admit(base, value)))

        Then("no invented field or malformed descendant becomes target-ready input")
        errors.map(_.code) shouldBe Vector("LUI501_FIELD_INVALID", "LUI501_DUPLICATE_INVALID",
          "LUI501_FIELD_INVALID", "LUI501_FIELD_INVALID", "LUI501_FIELD_INVALID", "LUI501_FIELD_INVALID",
          "LUI501_FIELD_INVALID", "LUI501_TEXT_REFERENCE_INVALID", "LUI501_FIELD_INVALID", "LUI501_FIELD_INVALID",
          "LUI501_ELEMENT_INVALID", "LUI501_ELEMENT_INVALID", "LUI501_RULE_INVALID")
        errors(5).path shouldBe "input.fields[0].valueBinding"
        errors(6).path shouldBe "input.fields[0].datatypeBinding"
        errors(11).path shouldBe "input.fields[0].localRules[0]"
      }

      "deny context, server and operation constraints as local field predicates and require exact feedback associations" in {
        Given("source constraints of several authorities and an admitted field's exact local pair")
        val base = _ready
        val field = base.input.fields.head
        val rule = field.localRules.head
        val invalidrules = Vector(
          rule.copy(constraint = null), rule.copy(ruleKind = null),
          rule.copy(constraint = ConstraintIdentity("confirm-order", "VALUE_LOCAL")),
          rule.copy(constraint = ConstraintIdentity("sales-order", "AGGREGATE_INVARIANT")),
          rule.copy(constraint = ConstraintIdentity("confirm-order", "SALES_ORDER_PRE")),
          rule.copy(constraint = ConstraintIdentity("confirm-order", "SALES_ORDER_POST")),
          rule.copy(constraint = ConstraintIdentity("sales-order-code", "different-detail")),
          rule.copy(feedbackId = "conflict"), rule.copy(feedbackId = null), rule.copy(messageTextId = "missing"))
        val withoutassociation = _right(CozyLogicalUiSemantics.project(base.projection,
          base.semantics.input.copy(feedbackAssociations = base.semantics.input.feedbackAssociations.filterNot(
            _.constraint == ConstraintIdentity("sales-order-code", "DATATYPE_FORMAT")))))

        When("local rule declarations and a source without the exact feedback association are interpreted")
        val errors = invalidrules.map(value => _left(_admit(base, base.input.copy(
          fields = Vector(field.copy(localRules = Vector(value)))))))
        val duplicateerror = _left(_admit(base, base.input.copy(fields = Vector(field.copy(localRules = field.localRules :+ rule)))))
        val associationerror = _left(candidate(base.kernelCandidate, base.projection, withoutassociation,
          base.input.copy(semanticProjectionIdentity = withoutassociation.identity)))

        Then("only exact local Value/Datatype declarations can be paired with field feedback")
        errors.take(9).map(_.code).distinct shouldBe Vector("LUI501_RULE_INVALID")
        errors.last.code shouldBe "LUI501_TEXT_REFERENCE_INVALID"
        errors.head.path shouldBe "input.fields[0].localRules[0].constraint"
        errors(1).path shouldBe "input.fields[0].localRules[0].ruleKind"
        duplicateerror.code shouldBe "LUI501_DUPLICATE_INVALID"
        associationerror.code shouldBe "LUI501_RULE_INVALID"
        associationerror.path shouldBe "input.fields[0].localRules[0].feedbackId"
      }

      "reject foreign or missing outcome steps, triggers, feedback, lifecycle, domain and Workflow state evidence" in {
        Given("a declared success outcome with invalid source and state variants")
        val base = _ready
        val outcome = base.input.outcomes.find(_.feedbackState == NormalFeedback).get
        val variants = Vector(
          outcome.copy(outcomeId = ""), outcome.copy(step = null),
          outcome.copy(step = outcome.step.copy(stepId = "undeclared")), outcome.copy(trigger = null),
          outcome.copy(trigger = InteractionRef("foreign", "invoke")), outcome.copy(feedbackControl = null),
          outcome.copy(feedbackControl = InteractionRef("foreign", "feedback")), outcome.copy(feedbackState = null),
          outcome.copy(feedbackControl = InteractionRef("order-list", "entry"), feedbackState = ConflictFeedback),
          outcome.copy(uiState = null), outcome.copy(uiState = FailedInteractionState), outcome.copy(domainState = null),
          outcome.copy(domainState = outcome.domainState.copy(stateId = "unknown")),
          outcome.copy(domainState = outcome.domainState.copy(stateMachineBinding = CozyLogicalUiSalesOrderFixtures.operationBinding)),
          outcome.copy(workflowState = null), outcome.copy(workflowState = Some(null)),
          outcome.copy(workflowState = Some(WorkflowStateReference("foreign", "awaiting"))),
          outcome.copy(workflowState = Some(WorkflowStateReference("sales-order-workflow", "advanced"))))

        When("all invalid outcome bindings are admitted")
        val errors = variants.map(value => _left(_admit(base, base.input.copy(outcomes = Vector(value)))))
        val duplicateerror = _left(_admit(base, base.input.copy(outcomes = Vector(outcome, outcome))))

        Then("unmapped interactions and undeclared state evidence fail with exact structured paths")
        errors.map(_.code) shouldBe Vector("LUI501_OUTCOME_INVALID") ++ Vector.fill(6)("LUI501_SOURCE_INVALID") ++
          Vector.fill(11)("LUI501_OUTCOME_INVALID")
        errors(1).path shouldBe "input.outcomes[0].trigger"
        errors(5).path shouldBe "input.outcomes[0].feedbackControl"
        errors(7).path shouldBe "input.outcomes[0].feedbackState"
        errors(11).path shouldBe "input.outcomes[0].domainState"
        errors.last.path shouldBe "input.outcomes[0].workflowState"
        duplicateerror.code shouldBe "LUI501_DUPLICATE_INVALID"
      }

      "reject missing, foreign, duplicate, null and kind-incompatible recoveries" in {
        Given("an unavailable outcome and its exact invocation retry admission")
        val base = _ready
        val outcome = base.input.outcomes.find(_.feedbackState == UnavailableFeedback).get
        val recovery = outcome.recoveries.head
        val variants = Vector(
          outcome.copy(recoveries = null), outcome.copy(recoveries = Vector(null)),
          outcome.copy(recoveries = Vector(recovery.copy(step = null))),
          outcome.copy(recoveries = Vector(recovery.copy(target = null))),
          outcome.copy(recoveries = Vector(recovery.copy(step = recovery.step.copy(stepId = "foreign")))),
          outcome.copy(recoveries = Vector(recovery.copy(target = InteractionRef("foreign", "invoke")))),
          outcome.copy(recoveries = Vector(recovery.copy(kind = null))),
          outcome.copy(recoveries = Vector(recovery.copy(kind = EditInput))),
          outcome.copy(recoveries = Vector(recovery.copy(kind = ReturnToEntry))),
          outcome.copy(recoveries = Vector(recovery.copy(target = InteractionRef("order-list", "entry")))),
          outcome.copy(recoveries = Vector(recovery, recovery)))

        When("recovery declarations are joined to source steps and their closed interaction kinds")
        val errors = variants.map(value => _left(_admit(base, base.input.copy(outcomes = Vector(value)))))

        Then("only exact source retry/edit/entry semantics enter the readiness contract")
        errors.map(_.code) shouldBe Vector.fill(2)("LUI501_ELEMENT_INVALID") ++ Vector.fill(4)("LUI501_SOURCE_INVALID") ++
          Vector.fill(4)("LUI501_RECOVERY_INVALID") ++ Vector("LUI501_DUPLICATE_INVALID")
        errors(2).path shouldBe "input.outcomes[0].recoveries[0].target"
        errors(6).path shouldBe "input.outcomes[0].recoveries[0].kind"
        errors.last.path shouldBe "input.outcomes[0].recoveries[1]"
      }
    }
  }

  "Complete Logical UI identity and currentness" should {
    "canonical structural declarations" which {
      "bind all unordered input permutations to identical complete content and consumed input" in {
        Given("one complete source chain and independent randomized permutations of every readiness declaration vector")
        val base = _ready
        val property = Prop.forAll(Gen.choose(0, Int.MaxValue)) { seed =>
          val random = new scala.util.Random(seed)
          val input = base.input.copy(
            profiles = random.shuffle(base.input.profiles).map(profile => profile.copy(regions = random.shuffle(profile.regions))),
            controls = random.shuffle(base.input.controls),
            fields = random.shuffle(base.input.fields).map(field => field.copy(localRules = random.shuffle(field.localRules))),
            textSources = random.shuffle(base.input.textSources),
            outcomes = random.shuffle(base.input.outcomes).map(outcome => outcome.copy(recoveries = random.shuffle(outcome.recoveries))))
          _admit(base, input) match {
            case Right(value) => value.identity == base.identity && value.inputIdentity == base.inputIdentity &&
              value.canonicalContent == base.canonicalContent && verifyCurrent(base, base.kernelCandidate, base.projection, base.semantics, input) == Right(())
            case Left(_) => false
          }
        }

        When("ScalaCheck admits independent declaration orderings and the fixture reverses the entire existing chain")
        val result = Test.check(Test.Parameters.default.withMinSuccessfulTests(30), property)
        val reversed = _right(CozyLogicalUiSalesOrderFixtures.readyCandidate(reversed = true))

        Then("ordering alone cannot revise complete Logical UI authority")
        result.passed shouldBe true
        reversed.identity shouldBe base.identity
        reversed.inputIdentity shouldBe base.inputIdentity
        reversed.canonicalContent shouldBe base.canonicalContent
      }

      "stale complete values when labels, sources, profiles, fields, outcomes or rules change under the same legacy kernel" in {
        Given("one complete candidate and admitted metadata revisions retaining the same kernel")
        val base = _ready
        val input = base.input
        val text = input.textSources.head
        val field = input.fields.head
        val outcome = input.outcomes.head
        val variants = Vector(
          input.copy(textSources = text.copy(defaultText = text.defaultText + " revised") +: input.textSources.tail),
          input.copy(textSources = text.copy(sourceIdentity = text.sourceIdentity + ":revision") +: input.textSources.tail),
          input.copy(profiles = input.profiles.head.copy(persistentNavigation = true) +: input.profiles.tail),
          input.copy(fields = Vector(field.copy(initialValue = "SO-002"))),
          input.copy(outcomes = outcome.copy(messageTextId = "normal-message") +: input.outcomes.tail),
          input.copy(fields = Vector(field.copy(localRules = field.localRules.head.copy(messageTextId = "normal-message") +: field.localRules.tail))))

        When("complete declarations are rebuilt and old values are checked against revised input")
        val revised = variants.map(value => _right(_admit(base, value)))
        val errors = variants.map(value => _left(verifyCurrent(base, base.kernelCandidate, base.projection, base.semantics, value)))
        val nullerror = _left(verifyCurrent(null, base.kernelCandidate, base.projection, base.semantics, base.input))

        Then("every meaningful declaration revision changes complete and consumed identity while legacy identity remains shared")
        revised.map(_.kernelCandidate.identity).distinct shouldBe Vector(base.kernelCandidate.identity)
        revised.map(_.identity) should not contain base.identity
        revised.map(_.inputIdentity) should not contain base.inputIdentity
        errors.map(_.code).distinct shouldBe Vector("LUI501_CURRENTNESS_INVALID")
        errors.map(_.path).distinct shouldBe Vector("value")
        nullerror.code shouldBe "LUI501_CURRENTNESS_INVALID"
      }

      "stale the complete chain when screen or semantic source evidence changes" in {
        Given("a complete candidate with independently revised screen purpose and admitted semantic purpose")
        val base = _ready
        val source = base.projection.input
        val changedprojection = _right(CozyLogicalUi.project(base.kernelCandidate,
          source.copy(screens = source.screens.head.copy(primaryPurpose = "revised-purpose") +: source.screens.tail)))
        val changedsemantics = _right(CozyLogicalUiSemantics.project(changedprojection,
          base.semantics.input.copy(projectionIdentity = changedprojection.identity)))
        val semanticinput = base.semantics.input
        val revisedsemantics = _right(CozyLogicalUiSemantics.project(base.projection,
          semanticinput.copy(screens = semanticinput.screens.head.copy(purpose = InspectPurpose) +: semanticinput.screens.tail)))

        When("the old complete value is compared with each admitted revised source chain")
        val sourceerror = _left(verifyCurrent(base, base.kernelCandidate, changedprojection, changedsemantics,
          base.input.copy(semanticProjectionIdentity = changedsemantics.identity)))
        val semanticerror = _left(verifyCurrent(base, base.kernelCandidate, base.projection, revisedsemantics,
          base.input.copy(semanticProjectionIdentity = revisedsemantics.identity)))

        Then("source projection and semantic changes invalidate the complete value even with an unchanged kernel candidate")
        sourceerror.code shouldBe "LUI501_CURRENTNESS_INVALID"
        semanticerror.code shouldBe "LUI501_CURRENTNESS_INVALID"
      }

      "preserve padded display text and injectively frame opaque Unicode, quote, control and delimiter-bearing identities" in {
        Given("a declared source label with opaque punctuation and two nearby distinct source identities")
        val base = _ready
        val text = base.input.textSources.head
        val display = "  注文 \"quoted\" | : \n \u0001  "
        val firstinput = base.input.copy(textSources = text.copy(sourceIdentity = "opaque|a:b\"\u0001注文", defaultText = display) +: base.input.textSources.tail)
        val secondinput = base.input.copy(textSources = text.copy(sourceIdentity = "opaque|a:b\"\u0001注文|", defaultText = display) +: base.input.textSources.tail)

        When("both complete declarations are serialized with structural JSON framing")
        val first = _right(_admit(base, firstinput))
        val second = _right(_admit(base, secondinput))
        val parsed = Json.parse(first.canonicalContent)

        Then("source and display content survive byte-for-byte and distinct opaque values have distinct complete identities")
        first.input.textSources.find(_.textId == text.textId).get.defaultText shouldBe display
        val parsedtext = (parsed \ "readiness" \ "textSources").as[Vector[play.api.libs.json.JsValue]].head
        (parsedtext \ "defaultText").as[String] shouldBe display
        first.identity should not be second.identity
        first.canonicalContent should not be second.canonicalContent
      }
    }
  }

  "Exact complete Logical UI acceptance" should {
    "explicit fixture decisions" which {
      "bind candidate, consumed input, decision and accepted roles with attributable reviewer data" in {
        Given("a complete candidate and an explicit specification fixture decision")
        val base = _ready
        val decision = TargetReadyAcceptanceDecision("spec-decision", base.identity, "spec-reviewer")

        When("the exact complete candidate receives that explicit data decision")
        val accepted = _right(accept(base, decision))
        val anotherreviewer = _right(accept(base, decision.copy(reviewerId = "other-spec-reviewer")))

        Then("the four roles differ and accepted authority retains its exact candidate and attribution")
        Vector(base.identity, base.inputIdentity, decision.decisionIdentity, accepted.identity).distinct.size shouldBe 4
        accepted.candidate should be theSameInstanceAs base
        accepted.decision shouldBe decision
        anotherreviewer.identity should not be accepted.identity
        anotherreviewer.decision.decisionIdentity should not be decision.decisionIdentity
      }

      "reject kernel-only, revised, null, blank and untrimmed acceptance or reviewer data" in {
        Given("a complete candidate, a revised complete candidate and explicit malformed decision fixtures")
        val base = _ready
        val revised = _right(_admit(base, base.input.copy(fields = Vector(base.input.fields.head.copy(initialValue = "SO-002")))))
        val decision = TargetReadyAcceptanceDecision("spec-decision", base.identity, "spec-reviewer")
        val decisions = Vector(
          null, decision.copy(decisionId = null), decision.copy(decisionId = ""), decision.copy(decisionId = " decision"),
          decision.copy(candidateIdentity = base.kernelCandidate.identity), decision.copy(candidateIdentity = null),
          decision.copy(reviewerId = null), decision.copy(reviewerId = ""), decision.copy(reviewerId = " reviewer"))

        When("invalid decisions and an old decision on revised complete input reach acceptance admission")
        val errors = decisions.map(value => _left(accept(base, value)))
        val staleerror = _left(accept(revised, decision))
        val nullerror = _left(accept(null, decision))

        Then("the boundary requires exact complete identity and valid attribution without inferring consent")
        errors.map(_.code).distinct shouldBe Vector("LUI501_ACCEPTANCE_INVALID")
        errors.map(_.path) shouldBe Vector("decision", "decision.decisionId", "decision.decisionId", "decision.decisionId",
          "decision.candidateIdentity", "decision.candidateIdentity", "decision.reviewerId", "decision.reviewerId", "decision.reviewerId")
        staleerror.path shouldBe "decision.candidateIdentity"
        nullerror.path shouldBe "candidate"
        revised.kernelCandidate.identity shouldBe base.kernelCandidate.identity
      }
    }
  }

  private def _ready: TargetReadyCandidate = _right(CozyLogicalUiSalesOrderFixtures.readyCandidate())
  private def _admit(base: TargetReadyCandidate, input: ReadinessInput): Either[LogicalUiError, TargetReadyCandidate] =
    candidate(base.kernelCandidate, base.projection, base.semantics, input)
  private def _right[A](value: Either[LogicalUiError, A]): A = value match {
    case Right(result) => result
    case Left(error) => fail(error.toString)
  }
  private def _left[A](value: Either[LogicalUiError, A]): LogicalUiError = value match {
    case Left(error) => error
    case Right(_) => fail("Expected a structured target-readiness diagnostic")
  }
}
