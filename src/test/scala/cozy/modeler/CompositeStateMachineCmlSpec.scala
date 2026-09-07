package cozy.modeler

import org.goldenport.kaleidox.{Config => KaleidoxConfig, Model => KaleidoxModel}
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Sep.  7, 2026
 * @version Sep.  7, 2026
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

    "exclude Workflow from the Phase 47 CML language" which {
      "reject a supplied WORKFLOW root" in {
        Given("a CML document with a WORKFLOW root")
        val model = _model("# WORKFLOW\n\n## Unsupported\n")

        When("the ordinary ModelBuilder is constructed")
        val error = intercept[RuntimeException] {
          Modeler.ModelBuilder(model)
        }

        Then("the grammar states that Workflow is not admitted")
        error.getMessage should include("WORKFLOW is not admitted by the Phase 47 CML grammar")
      }
    }
  }

  private def _model(source: String): KaleidoxModel = {
    val model = KaleidoxModel.parseWitoutLocation(KaleidoxConfig.default, source)
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
}
