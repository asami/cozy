package cozy.modeler

import org.goldenport.kaleidox.{Config => KaleidoxConfig, Model => KaleidoxModel}
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Sep.  7, 2026
 * @version Sep. 22, 2026
 * @author  ASAMI, Tomoharu
 */
final class CompositeStateMachineCmlSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  "CML Composite StateMachine grammar" should {
    "normalize and integrate a flat two-constituent definition" which {
      "accept canonical punctuation and case aliases during ordinary ModelBuilder construction" in {
        Given("two flat constituent StateMachines, complete derivations, typed operation action, and occurrence declarations")
        val model = _model(_accepted_source("# composite state machine"))

        When("the ordinary ModelBuilder is constructed")
        noException should be thrownBy {
          Modeler.ModelBuilder(model)
        }

        Then("the normalized Composite StateMachine surface is accepted without generated artifacts")
        model.divisions.nonEmpty shouldBe true
      }

      "preserve distinct equal-action occurrences by validating each declared placement" in {
        Given("two constituent action occurrences with distinct identities and the same logical ACTION reference")
        val model = _model(_accepted_source())

        When("the grammar boundary validates their constituent transition provenance")
        noException should be thrownBy {
          Modeler.ModelBuilder(model)
        }

        Then("both declarations remain valid rather than being deduplicated")
        model.divisions.nonEmpty shouldBe true
      }
    }

    "enforce role, state, rule, and initial configuration contracts" which {
      "reject a duplicate normalized constituent role" in {
        Given("a Composite StateMachine whose second constituent reuses the first role")
        val model = _model(_accepted_source().replace("#### fulfillment", "#### PAYMENT"))

        When("ModelBuilder validates the constituent identities")
        val error = intercept[RuntimeException] {
          Modeler.ModelBuilder(model)
        }

        Then("the role identity diagnostic is emitted")
        error.getMessage should include("CONSTITUENT role 'payment' must be unique")
      }

      "reject a StateMachine reference that cannot be selected" in {
        Given("a constituent referencing a missing StateMachine")
        val model = _model(_accepted_source().replace("state-machine = FulfillmentLifecycle", "state-machine = MissingLifecycle"))

        When("ModelBuilder resolves the constituent reference")
        val error = intercept[RuntimeException] {
          Modeler.ModelBuilder(model)
        }

        Then("the unknown StateMachine diagnostic identifies the reference")
        error.getMessage should include("unknown STATE-MACHINE 'MissingLifecycle'")
      }

      "reject an incomplete derivation mapping" in {
        Given("a derivation WHEN mapping that omits the fulfillment role")
        val model = _model(_accepted_source().replace("payment.Awaiting, fulfillment.Waiting", "payment.Awaiting"))

        When("ModelBuilder validates the rule configuration")
        val error = intercept[RuntimeException] {
          Modeler.ModelBuilder(model)
        }

        Then("the missing role is diagnosed rather than inferred")
        error.getMessage should include("DERIVATION 'pending' WHEN is incomplete")
      }

      "reject an INITIAL mapping outside the constituent state universe" in {
        Given("an INITIAL configuration naming a state absent from the payment constituent")
        val source = _accepted_source().replace(
          "### INITIAL\n\npayment.Awaiting, fulfillment.Waiting",
          "### INITIAL\n\npayment.Unknown, fulfillment.Waiting"
        )
        val model = _model(source)

        When("ModelBuilder validates the declared initial configuration")
        val error = intercept[RuntimeException] {
          Modeler.ModelBuilder(model)
        }

        Then("the unknown constituent state is diagnosed")
        error.getMessage should include("INITIAL state for role 'payment' references unknown state 'Unknown'")
      }

      "reject an ambiguous reachable derivation without declaration-order selection" in {
        Given("two derivation rules matching the declared initial configuration")
        val source = _accepted_source().replace(
          "### INITIAL\n\npayment.Awaiting, fulfillment.Waiting",
          "#### duplicate-pending\n\nstate = Pending\nwhen = payment.Awaiting, fulfillment.Waiting\n\n### INITIAL\n\npayment.Awaiting, fulfillment.Waiting"
        )
        val model = _model(source)

        When("ModelBuilder traverses the reachable configuration from INITIAL")
        val error = intercept[RuntimeException] {
          Modeler.ModelBuilder(model)
        }

        Then("the ambiguous configuration is rejected rather than selecting either rule")
        error.getMessage should include("ambiguous reachable derivation configuration")
      }
    }

    "validate logical actions and occurrence provenance" which {
      "reject an unresolved Operation reference" in {
        Given("a global ACTION with an unknown normalized CML Operation")
        val model = _model(_accepted_source().replace("operation = capturePayment", "operation = missingOperation"))

        When("ModelBuilder resolves the typed Action operation")
        val error = intercept[RuntimeException] {
          Modeler.ModelBuilder(model)
        }

        Then("the action diagnostic requires one known operation")
        error.getMessage should include("must resolve to one normalized CML Operation")
      }

      "reject a typed subject binding that disagrees with the Operation input" in {
        Given("an action INPUT bound to a constituent subject with the wrong declared type")
        val model = _model(_accepted_source().replace("subject-type = PaymentCommand", "subject-type = WrongCommand"))

        When("ModelBuilder checks the Action input contract")
        val error = intercept[RuntimeException] {
          Modeler.ModelBuilder(model)
        }

        Then("the typed subject mismatch is diagnosed")
        error.getMessage should include("must match OPERATION input 'PaymentCommand'")
      }

      "normalize full Action metadata into typed values" in {
        Given("an ACTION with external, out-of-UnitOfWork, required-idempotency, and compensation metadata")
        val source = _accepted_source().replace(
          "input = payment.subject",
          """input = payment.subject
effect = EXTERNAL
transaction = OUTSIDE_UNIT_OF_WORK
idempotency = REQUIRED
idempotency-key = payment-command
compensation-handler = cancel-payment"""
        )
        val model = _model(source)

        When("the CML Action is normalized")
        val action = CompositeStateMachineCml.definitions(model).head.actions.head

        Then("the exact semantic metadata is represented by closed typed values")
        action.metadata shouldBe Some(CompositeStateMachineActionMetadata(
          effectClass = CompositeStateMachineEffectClass.External,
          transactionRequirement = CompositeStateMachineTransactionRequirement.OutsideUnitOfWork,
          idempotency = CompositeStateMachineIdempotency.Required("payment-command"),
          compensationHandlerRef = Some("cancel-payment")
        ))
      }

      "reject an authored empty base metadata field" in {
        Given("an ACTION whose EFFECT metadata field is authored without a value")
        val source = _accepted_source().replace(
          "input = payment.subject",
          """input = payment.subject
effect =
transaction = REQUIRED
idempotency = NOT_REQUIRED"""
        )
        val model = _model(source)

        When("the CML Action metadata is normalized")
        val error = intercept[RuntimeException] {
          CompositeStateMachineCml.definitions(model)
        }

        Then("the empty EFFECT field is diagnosed as nonempty metadata")
        error.getMessage should include("metadata requires nonempty EFFECT")
      }

      "reject a duplicate authored metadata field" in {
        Given("an ACTION that authors EFFECT metadata more than once")
        val source = _accepted_source().replace(
          "input = payment.subject",
          """input = payment.subject
effect = LOCAL
effect = EXTERNAL
transaction = REQUIRED
idempotency = NOT_REQUIRED"""
        )
        val model = _model(source)

        When("the CML Action metadata is normalized")
        val error = intercept[RuntimeException] {
          CompositeStateMachineCml.definitions(model)
        }

        Then("the duplicate EFFECT field is rejected as non-unique")
        error.getMessage should include("metadata field 'EFFECT' must be unique")
      }

      "reject an invalid exact enum metadata value" in {
        Given("an ACTION whose EFFECT metadata value is outside the exact enum")
        val source = _accepted_source().replace(
          "input = payment.subject",
          """input = payment.subject
effect = REMOTE
transaction = REQUIRED
idempotency = NOT_REQUIRED"""
        )
        val model = _model(source)

        When("the CML Action metadata is normalized")
        val error = intercept[RuntimeException] {
          CompositeStateMachineCml.definitions(model)
        }

        Then("the invalid EFFECT enum value is rejected")
        error.getMessage should include("EFFECT must be LOCAL or EXTERNAL")
      }

      "reject required idempotency without a key" in {
        Given("an ACTION that requires idempotency but omits IDEMPOTENCY-KEY")
        val source = _accepted_source().replace(
          "input = payment.subject",
          """input = payment.subject
effect = LOCAL
transaction = REQUIRED
idempotency = REQUIRED"""
        )
        val model = _model(source)

        When("the CML Action metadata is normalized")
        val error = intercept[RuntimeException] {
          CompositeStateMachineCml.definitions(model)
        }

        Then("the missing required idempotency key is diagnosed")
        error.getMessage should include("IDEMPOTENCY-KEY is required and must be nonempty")
      }

      "reject required idempotency with an empty key" in {
        Given("an ACTION that requires idempotency with an authored empty IDEMPOTENCY-KEY")
        val source = _accepted_source().replace(
          "input = payment.subject",
          """input = payment.subject
effect = LOCAL
transaction = REQUIRED
idempotency = REQUIRED
idempotency-key ="""
        )
        val model = _model(source)

        When("the CML Action metadata is normalized")
        val error = intercept[RuntimeException] {
          CompositeStateMachineCml.definitions(model)
        }

        Then("the empty required idempotency key is diagnosed")
        error.getMessage should include("IDEMPOTENCY-KEY is required and must be nonempty")
      }

      "preserve a legacy Action with no metadata" in {
        Given("a legacy ACTION without Phase 47.1 metadata fields")
        val model = _model(_accepted_source())

        When("the CML Action is normalized")
        val action = CompositeStateMachineCml.definitions(model).head.actions.head

        Then("the legacy Action remains valid and has no normalized metadata")
        action.metadata shouldBe None
      }

      "reject partial Action metadata" in {
        Given("an ACTION that supplies EFFECT without the required base metadata fields")
        val source = _accepted_source().replace(
          "input = payment.subject",
          """input = payment.subject
effect = EXTERNAL"""
        )
        val model = _model(source)

        When("the CML Action metadata is normalized")
        val error = intercept[RuntimeException] {
          CompositeStateMachineCml.definitions(model)
        }

        Then("the missing base metadata is diagnosed")
        error.getMessage should include("metadata requires nonempty TRANSACTION")
      }

      "reject an invalid idempotency key pairing" in {
        Given("an ACTION that supplies IDEMPOTENCY-KEY with NOT_REQUIRED idempotency")
        val source = _accepted_source().replace(
          "input = payment.subject",
          """input = payment.subject
effect = LOCAL
transaction = REQUIRED
idempotency = NOT_REQUIRED
idempotency-key = payment-command"""
        )
        val model = _model(source)

        When("the CML Action metadata is normalized")
        val error = intercept[RuntimeException] {
          CompositeStateMachineCml.definitions(model)
        }

        Then("the incompatible idempotency key is rejected")
        error.getMessage should include("IDEMPOTENCY-KEY is prohibited")
      }

      "reject a compensation handler outside its applicability boundary" in {
        Given("an ACTION with a compensation handler on a local in-UnitOfWork effect")
        val source = _accepted_source().replace(
          "input = payment.subject",
          """input = payment.subject
effect = LOCAL
transaction = REQUIRED
idempotency = NOT_REQUIRED
compensation-handler = cancel-payment"""
        )
        val model = _model(source)

        When("the CML Action metadata is normalized")
        val error = intercept[RuntimeException] {
          CompositeStateMachineCml.definitions(model)
        }

        Then("the handler applicability boundary is diagnosed")
        error.getMessage should include("COMPENSATION-HANDLER requires EFFECT=EXTERNAL and TRANSACTION=OUTSIDE_UNIT_OF_WORK")
      }
    }

    "normalize provider-neutral candidate-admission Actions" which {
      "retain a typed Judgment candidate contract and its one deterministic local Admission correlation" in {
        Given("one direct-field Judgment Action and one local Admission Action that names it")
        val model = _model_with_location(_candidate_admission_source())

        When("the Composite StateMachine Actions are normalized")
        val actions = CompositeStateMachineCml.definitions(model).head.actions
        val judgment = actions.find(_.identity == "judge-payment").getOrElse(fail("Missing Judgment Action"))
        val admission = actions.find(_.identity == "admit-payment").getOrElse(fail("Missing Admission Action"))
        val judgmentsemantic = judgment.candidateAdmission.collect {
          case value: CompositeStateMachineJudgmentAction => value
        }.getOrElse(fail("Missing normalized Judgment semantics"))
        val admissionsemantic = admission.candidateAdmission.collect {
          case value: CompositeStateMachineAdmissionAction => value
        }.getOrElse(fail("Missing normalized Admission semantics"))

        Then("the closed source IR preserves semantic references and their Action source correlation without a transition directive")
        judgment.kind shouldBe "JUDGMENT"
        judgmentsemantic.goal.value shouldBe "payment-review"
        judgmentsemantic.context.value shouldBe "order-context"
        judgmentsemantic.candidate.value shouldBe "payment-candidate"
        judgmentsemantic.alternatives.map(_.value) should contain only ("approve", "reject")
        judgmentsemantic.criteria.map(_.value) should contain only ("amount-valid", "fraud-clear")
        judgmentsemantic.expectedResult.value shouldBe "decision"
        judgmentsemantic.rationale.value shouldBe "decision-rationale"
        judgmentsemantic.evidence.value shouldBe "payment-evidence"
        judgmentsemantic.evidenceScope.value shouldBe "order"
        judgmentsemantic.evidenceFreshness.value shouldBe "current"
        judgmentsemantic.evidenceProvenance.value shouldBe "payment-ledger"
        judgment.source.line should not be empty
        judgmentsemantic.source shouldBe judgment.source
        judgmentsemantic.goal.source shouldBe judgment.source
        admission.kind shouldBe "ADMISSION"
        admissionsemantic.candidateAction.value shouldBe judgment.identity
        admission.source.line should not be empty
        admissionsemantic.source shouldBe admission.source
        admission.metadata shouldBe Some(CompositeStateMachineActionMetadata(
          CompositeStateMachineEffectClass.Local,
          CompositeStateMachineTransactionRequirement.Required,
          CompositeStateMachineIdempotency.NotRequired,
          None
        ))
      }

      "reject missing, duplicate, ambiguous, and execution-placement Judgment fields" in {
        Given("CAM sources with a missing GOAL or RATIONALE, duplicate ALTERNATIVE or EXPECTED-RESULT, provider vocabulary, and a next-state directive")
        val missinggoal = _model(_candidate_admission_source().replace("goal = payment-review\n", ""))
        val missingrationale = _model(_candidate_admission_source().replace("rationale = decision-rationale\n", ""))
        val duplicatealternative = _model(_candidate_admission_source().replace("alternative = reject", "alternative = approve"))
        val ambiguousresult = _model(_candidate_admission_source().replace(
          "expected-result = decision",
          "expected-result = decision\nexpected-result = alternative-decision"
        ))
        val providerplacement = _model(_candidate_admission_source().replace(
          "goal = payment-review",
          "goal = payment-review\nprovider = local"
        ))
        val nextstate = _model(_candidate_admission_source().replace(
          "expected-result = decision",
          "expected-result = decision\nnext-state = Complete"
        ))

        When("the Action grammar validates their direct semantic fields")
        val missinggoalerror = intercept[RuntimeException] {
          CompositeStateMachineCml.definitions(missinggoal)
        }
        val missingrationaleerror = intercept[RuntimeException] {
          CompositeStateMachineCml.definitions(missingrationale)
        }
        val duplicatealternativeerror = intercept[RuntimeException] {
          CompositeStateMachineCml.definitions(duplicatealternative)
        }
        val ambiguousresulterror = intercept[RuntimeException] {
          CompositeStateMachineCml.definitions(ambiguousresult)
        }
        val providerplacementerror = intercept[RuntimeException] {
          CompositeStateMachineCml.definitions(providerplacement)
        }
        val nextstateerror = intercept[RuntimeException] {
          CompositeStateMachineCml.definitions(nextstate)
        }

        Then("absence, repeated ambiguity, duplicate alternatives, execution placement, and state directives are fail-closed")
        missinggoalerror.getMessage should include("requires exactly one direct GOAL value")
        missingrationaleerror.getMessage should include("requires exactly one direct RATIONALE value")
        duplicatealternativeerror.getMessage should include("ALTERNATIVE 'approve' must be unique")
        ambiguousresulterror.getMessage should include("requires exactly one direct EXPECTED-RESULT value")
        providerplacementerror.getMessage should include("does not admit execution-placement vocabulary 'PROVIDER'")
        nextstateerror.getMessage should include("does not admit direct field 'next-state'")
      }

      "reject an Admission Action that is nonlocal or does not identify a Judgment Action" in {
        Given("one Admission Action with a legacy candidate target, one with external effect metadata, and one Judgment without Admission")
        val invalidtarget = _model(_candidate_admission_source().replace("candidate-action = judge-payment", "candidate-action = capture-payment"))
        val nonlocal = _model(_candidate_admission_source().replace("effect = LOCAL", "effect = EXTERNAL"))
        val missingadmission = _model(_candidate_admission_source_without_admission())

        When("the deterministic candidate-admission relationship is validated")
        val invalidtargeterror = intercept[RuntimeException] {
          CompositeStateMachineCml.definitions(invalidtarget)
        }
        val nonlocalerror = intercept[RuntimeException] {
          CompositeStateMachineCml.definitions(nonlocal)
        }
        val missingadmissionerror = intercept[RuntimeException] {
          CompositeStateMachineCml.definitions(missingadmission)
        }

        Then("Admission remains a local boundary over exactly one Judgment candidate")
        invalidtargeterror.getMessage should include("must resolve to one JUDGMENT Action")
        nonlocalerror.getMessage should include("requires metadata EFFECT=LOCAL and TRANSACTION=REQUIRED")
        missingadmissionerror.getMessage should include("KIND=JUDGMENT requires exactly one ADMISSION Action")
      }
    }

    "keep direct Composite StateMachine and WORKFLOW roots as distinct source forms" which {
      "normalize a direct Composite StateMachine root without a Workflow wrapper" in {
        Given("a valid direct COMPOSITE-STATEMACHINE CML root")
        val model = _model(_accepted_source())

        When("the direct Composite StateMachine source is normalized")
        val definitions = CompositeStateMachineCml.definitions(model)
        val workflows = CompositeStateMachineCml.workflowDefinitions(model)

        Then("the direct definition remains available and no Workflow source form is inferred")
        definitions.map(_.identity) should contain only "OrderProgress"
        workflows shouldBe Vector.empty
      }
    }
  }

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

  private def _accepted_source(root: String = "# COMPOSITE-STATEMACHINE"): String =
    s"""$root
      |
      |## OrderProgress
      |
      |### CONSTITUENT
      |
      |#### payment
      |
      |state-machine = PaymentLifecycle
      |subject = payment
      |subject-type = PaymentCommand
      |
      |#### fulfillment
      |
      |state-machine = FulfillmentLifecycle
      |
      |### STATE
      |
      |#### Pending
      |
      |#### Complete
      |
      |### DERIVATION
      |
      |#### pending
      |
      |state = Pending
      |when = payment.Awaiting, fulfillment.Waiting
      |
      |#### complete
      |
      |state = Complete
      |when = payment.Paid, fulfillment.Shipped
      |
      |#### payment-paid
      |
      |state = Pending
      |when = payment.Paid, fulfillment.Waiting
      |
      |#### fulfillment-shipped
      |
      |state = Pending
      |when = payment.Awaiting, fulfillment.Shipped
      |
      |### INITIAL
      |
      |payment.Awaiting, fulfillment.Waiting
      |
      |### ACTION
      |
      |#### capture-payment
      |
      |kind = OPERATION
      |operation = capturePayment
      |input = payment.subject
      |
      |### CONSTITUENT-ACTION
      |
      |#### payment-captured-exit
      |
      |role = payment
      |from = Awaiting
      |to = Paid
      |on = captured
      |placement = exit
      |action = capture-payment
      |
      |#### payment-captured-transition
      |
      |role = payment
      |from = Awaiting
      |to = Paid
      |on = captured
      |placement = transition
      |action = capture-payment
      |
      |### DERIVED-ACTION
      |
      |#### completed
      |
      |from = Pending
      |to = Complete
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
      |## FulfillmentLifecycle
      |
      |### State
      |
      |#### Waiting
      |
      |##### Transition
      |
      |to = Shipped
      |on = shipped
      |
      |#### Shipped
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

  private def _candidate_admission_source(): String =
    _accepted_source().replace(
      """#### capture-payment
        |
        |kind = OPERATION
        |operation = capturePayment
        |input = payment.subject""".stripMargin,
      """#### judge-payment
        |
        |kind = JUDGMENT
        |operation = capturePayment
        |input = payment.subject
        |goal = payment-review
        |context = order-context
        |candidate = payment-candidate
        |alternative = approve
        |alternative = reject
        |criteria = amount-valid
        |criteria = fraud-clear
        |expected-result = decision
        |rationale = decision-rationale
        |evidence = payment-evidence
        |evidence-scope = order
        |evidence-freshness = current
        |evidence-provenance = payment-ledger
        |
        |#### admit-payment
        |
        |kind = ADMISSION
        |operation = capturePayment
        |input = payment.subject
        |candidate-action = judge-payment
        |effect = LOCAL
        |transaction = REQUIRED
        |idempotency = NOT_REQUIRED
        |
        |#### capture-payment
        |
        |kind = OPERATION
        |operation = capturePayment
        |input = payment.subject""".stripMargin
    )

  private def _candidate_admission_source_without_admission(): String =
    _candidate_admission_source().replace(
      """#### admit-payment
        |
        |kind = ADMISSION
        |operation = capturePayment
        |input = payment.subject
        |candidate-action = judge-payment
        |effect = LOCAL
        |transaction = REQUIRED
        |idempotency = NOT_REQUIRED
        |
        |""".stripMargin,
      ""
    )
}
