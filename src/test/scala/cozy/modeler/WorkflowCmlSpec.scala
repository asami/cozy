package cozy.modeler

import org.goldenport.kaleidox.{Config => KaleidoxConfig, Model => KaleidoxModel}
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Sep. 17, 2026
 * @version Sep. 17, 2026
 * @author  ASAMI, Tomoharu
 */
final class WorkflowCmlSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  "CML WORKFLOW source contract" should {
    "interpret Literate Model source layers" which {
      "lower structural content while leaving narrative prose non-executable" in {
        Given("a WORKFLOW definition with direct version metadata, narrative prose, and one Composite StateMachine child")
        val model = _model(_workflow_source())

        When("the Workflow source is normalized")
        val workflow = CompositeStateMachineCml.workflowDefinitions(model).head

        Then("only the declared Composite StateMachine contributes workflow state semantics")
        workflow.identity shouldBe "OrderProgress"
        workflow.version shouldBe "workflow-v1"
        workflow.compositeStateMachine.states.map(_.name) should contain only ("Pending", "Complete")
        workflow.compositeStateMachine.actions.map(_.identity) should contain only "capture-payment"
      }

      "remain outside the direct Composite StateMachine generator-input collection" in {
        Given("a valid WORKFLOW source with one structural Composite StateMachine child")
        val model = _model(_workflow_source())

        When("ModelBuilder validates the workflow and the direct Composite StateMachine and Workflow source collections are normalized")
        Modeler.ModelBuilder(model)
        val definitions = CompositeStateMachineCml.definitions(model)
        val workflows = CompositeStateMachineCml.workflowDefinitions(model)

        Then("the workflow lowers through workflowDefinitions without entering the direct generator-input collection")
        definitions shouldBe Vector.empty
        workflows.map(_.identity) should contain only "OrderProgress"
      }

      "leave a narrative-only child definition outside the structural workflow model" in {
        Given("a WORKFLOW root whose direct child contains prose and nonstructural headings only")
        val model = _model(_narrative_workflow_source)

        When("the ordinary ModelBuilder and Workflow normalizer inspect the document")
        noException should be thrownBy {
          Modeler.ModelBuilder(model)
        }
        val workflows = CompositeStateMachineCml.workflowDefinitions(model)

        Then("the narrative child remains non-executable and creates no workflow definition")
        workflows shouldBe Vector.empty
      }
    }

    "preserve normalized source correlation and capability declarations" which {
      "retain workflow root and definition identities while mapping a capability to an existing Operation Action" in {
        Given("a structural WORKFLOW definition with one REQUIRED-OPERATION capability")
        val model = _model(_workflow_source())

        When("the definition is lowered to the existing Composite StateMachine semantics")
        val workflow = CompositeStateMachineCml.workflowDefinitions(model).head
        val required = workflow.requiredOperations.head

        Then("the workflow source correlation and non-executing capability mapping are retained")
        workflow.source shouldBe WorkflowSourceCorrelation(
          WorkflowSourceIdentity(None),
          WorkflowSourceIdentity(None)
        )
        required.capability shouldBe "capture-payment-capability"
        required.action.operation shouldBe CompositeStateMachineOperation(
          "OrderService",
          "capturePayment",
          Some("PaymentCommand")
        )
        required.action.kind shouldBe "OPERATION"
      }

      "retain distinct root and definition source locations when parsing with locations" in {
        Given("a structural WORKFLOW definition parsed with source locations enabled")
        val model = _model_with_location(_workflow_source())

        When("the located definition is lowered to the existing Composite StateMachine semantics")
        val workflow = CompositeStateMachineCml.workflowDefinitions(model).head
        val rootline = workflow.source.root.line
        val definitionline = workflow.source.definition.line

        Then("the normalized source correlation retains nonempty distinct root and definition lines")
        rootline should not be empty
        definitionline should not be empty
        rootline should not be definitionline
      }
    }

    "reject incomplete, ambiguous, and execution-bound source forms" which {
      "reject a standalone REQUIRED-OPERATION section as malformed structural syntax" in {
        Given("a WORKFLOW definition with REQUIRED-OPERATION but no Composite StateMachine child")
        val model = _model(_standalone_required_operation_source)

        When("the Workflow source structure is normalized")
        val error = intercept[RuntimeException] {
          CompositeStateMachineCml.workflowDefinitions(model)
        }

        Then("the structural capability section receives an explicit diagnostic")
        error.getMessage should include("requires a COMPOSITE-STATEMACHINE structural section when REQUIRED-OPERATION is declared")
      }

      "reject multiple REQUIRED-OPERATION sections" in {
        Given("a structural WORKFLOW definition with two REQUIRED-OPERATION sections")
        val model = _model(_workflow_source().replace(
          "### REQUIRED-OPERATION\n\n#### capture-payment-capability\n\naction = capture-payment",
          "### REQUIRED-OPERATION\n\n#### capture-payment-capability\n\naction = capture-payment\n\n### REQUIRED-OPERATION\n\n#### refund-payment-capability\n\naction = capture-payment"
        ))

        When("the Workflow source structure is normalized")
        val error = intercept[RuntimeException] {
          CompositeStateMachineCml.workflowDefinitions(model)
        }

        Then("the ambiguous required-operation sections are diagnosed")
        error.getMessage should include("accepts at most one REQUIRED-OPERATION section")
      }

      "reject duplicate REQUIRED-OPERATION capability identities" in {
        Given("a REQUIRED-OPERATION section with duplicate capability identities")
        val model = _model(_workflow_source().replace(
          "#### capture-payment-capability\n\naction = capture-payment",
          "#### capture-payment-capability\n\naction = capture-payment\n\n#### CapturePaymentCapability\n\naction = capture-payment"
        ))

        When("the required capabilities are normalized")
        val error = intercept[RuntimeException] {
          CompositeStateMachineCml.workflowDefinitions(model)
        }

        Then("the duplicate capability identity is diagnosed")
        error.getMessage should include("REQUIRED-OPERATION capability 'capture-payment-capability' must be unique")
      }

      "reject nested structural content in a REQUIRED-OPERATION entry" in {
        Given("a REQUIRED-OPERATION entry containing a nested heading")
        val model = _model(_workflow_source().replace(
          "#### capture-payment-capability\n\naction = capture-payment",
          "#### capture-payment-capability\n\n##### nested-content\n\naction = capture-payment"
        ))

        When("the required capabilities are normalized")
        val error = intercept[RuntimeException] {
          CompositeStateMachineCml.workflowDefinitions(model)
        }

        Then("the nested structural content is diagnosed")
        error.getMessage should include("does not admit nested structural content")
      }

      "reject undeclared direct metadata in a REQUIRED-OPERATION entry" in {
        Given("a REQUIRED-OPERATION entry with an undeclared direct metadata field")
        val model = _model(_workflow_source().replace(
          "#### capture-payment-capability\n\naction = capture-payment",
          "#### capture-payment-capability\n\nmetadata = forbidden\naction = capture-payment"
        ))

        When("the required capabilities are normalized")
        val error = intercept[RuntimeException] {
          CompositeStateMachineCml.workflowDefinitions(model)
        }

        Then("the undeclared direct metadata is diagnosed")
        error.getMessage should include("does not admit 'metadata'")
      }

      "reject a structural definition without direct version metadata" in {
        Given("a structural WORKFLOW definition without a version field")
        val model = _model(_workflow_source().replace("version = workflow-v1\n\n", ""))

        When("the Workflow source is normalized")
        val error = intercept[RuntimeException] {
          CompositeStateMachineCml.workflowDefinitions(model)
        }

        Then("the required direct version metadata is diagnosed")
        error.getMessage should include("requires exactly one direct nonempty VERSION metadata value")
      }

      "reject duplicate direct version metadata" in {
        Given("a structural WORKFLOW definition with two direct version values")
        val model = _model(_workflow_source().replace(
          "version = workflow-v1",
          "version = workflow-v1\nversion = workflow-v2"
        ))

        When("the Workflow source is normalized")
        val error = intercept[RuntimeException] {
          CompositeStateMachineCml.workflowDefinitions(model)
        }

        Then("the duplicate version metadata is diagnosed")
        error.getMessage should include("requires exactly one direct nonempty VERSION metadata value")
      }

      "reject an authored empty direct version metadata value" in {
        Given("a structural WORKFLOW definition whose direct version metadata is empty")
        val model = _model(_workflow_source().replace("version = workflow-v1", "version ="))

        When("the Workflow source is normalized")
        val error = intercept[RuntimeException] {
          CompositeStateMachineCml.workflowDefinitions(model)
        }

        Then("the empty version metadata is diagnosed")
        error.getMessage should include("VERSION metadata is required")
      }

      "reject a nested VERSION heading instead of direct version metadata" in {
        Given("a structural WORKFLOW definition whose VERSION is authored as a nested heading")
        val model = _model(_workflow_source().replace(
          "version = workflow-v1",
          "### VERSION\n\nworkflow-v1"
        ))

        When("the Workflow source is normalized")
        val error = intercept[RuntimeException] {
          CompositeStateMachineCml.workflowDefinitions(model)
        }

        Then("the nested version metadata receives an explicit direct-value diagnostic")
        error.getMessage should include("VERSION metadata must be a direct value")
      }

      "reject a REQUIRED-OPERATION entry with a missing ACTION value" in {
        Given("a REQUIRED-OPERATION entry without a direct ACTION field")
        val model = _model(_workflow_source().replace("action = capture-payment", ""))

        When("the required capability is normalized")
        val error = intercept[RuntimeException] {
          CompositeStateMachineCml.workflowDefinitions(model)
        }

        Then("the missing ACTION receives an explicit cardinality diagnostic")
        error.getMessage should include("requires exactly one direct ACTION value")
      }

      "reject a REQUIRED-OPERATION entry with an empty ACTION value" in {
        Given("a REQUIRED-OPERATION entry with an empty direct ACTION field")
        val model = _model(_workflow_source().replace("action = capture-payment", "action ="))

        When("the required capability is normalized")
        val error = intercept[RuntimeException] {
          CompositeStateMachineCml.workflowDefinitions(model)
        }

        Then("the empty ACTION receives an explicit required-value diagnostic")
        error.getMessage should include("ACTION is required")
      }

      "reject direct provider metadata as execution-binding vocabulary" in {
        Given("a WORKFLOW definition with a direct provider metadata field")
        val model = _model(_workflow_source().replace(
          "version = workflow-v1",
          "version = workflow-v1\nprovider = local"
        ))

        When("the Workflow source structure is classified")
        val error = intercept[RuntimeException] {
          CompositeStateMachineCml.workflowDefinitions(model)
        }

        Then("the direct provider field is diagnosed as execution-binding vocabulary")
        error.getMessage should include("does not admit execution-binding vocabulary 'provider'")
      }

      "reject direct provider execution metadata on the REQUIRED-OPERATION section" in {
        Given("a REQUIRED-OPERATION section with a direct provider metadata field")
        val model = _model(_workflow_source().replace(
          "### REQUIRED-OPERATION\n\n#### capture-payment-capability",
          "### REQUIRED-OPERATION\n\nprovider = local\n\n#### capture-payment-capability"
        ))

        When("the required-operation section is normalized")
        val error = intercept[RuntimeException] {
          CompositeStateMachineCml.workflowDefinitions(model)
        }

        Then("the section-level provider field is diagnosed as execution-binding vocabulary")
        error.getMessage should include("WORKFLOW 'OrderProgress' REQUIRED-OPERATION does not admit execution-binding vocabulary 'provider'")
      }

      "reject a capability that names an undeclared Action" in {
        Given("a REQUIRED-OPERATION capability referring to a missing Action")
        val model = _model(_workflow_source().replace(
          "#### capture-payment-capability\n\naction = capture-payment",
          "#### capture-payment-capability\n\naction = missing-action"
        ))

        When("the required capability is normalized")
        val error = intercept[RuntimeException] {
          CompositeStateMachineCml.workflowDefinitions(model)
        }

        Then("the unknown Action reference is diagnosed")
        error.getMessage should include("references unknown ACTION 'missing-action'")
      }

      "reject a non-Operation Action before it can satisfy a required capability" in {
        Given("a required capability referring to an Action declared with a non-Operation kind")
        val model = _model(_workflow_source().replace("kind = OPERATION", "kind = SCRIPT"))

        When("the Workflow source is normalized")
        val error = intercept[RuntimeException] {
          CompositeStateMachineCml.workflowDefinitions(model)
        }

        Then("the non-Operation declaration is rejected rather than becoming a capability target")
        error.getMessage should include("KIND must be OPERATION")
      }

      "reject duplicate capability mappings to the same Action" in {
        Given("two REQUIRED-OPERATION capabilities mapped to one declared Action")
        val model = _model(_workflow_source().replace(
          "#### capture-payment-capability\n\naction = capture-payment",
          """#### capture-payment-capability
            |
            |action = capture-payment
            |
            |#### CapturePaymentReceipt
            |
            |action = capture-payment""".stripMargin
        ))

        When("the required capabilities are normalized")
        val error = intercept[RuntimeException] {
          CompositeStateMachineCml.workflowDefinitions(model)
        }

        Then("the duplicate Action mapping is diagnosed")
        error.getMessage should include("ACTION 'capture-payment' must be mapped at most once")
      }

      "reject a second Composite StateMachine structural child as ambiguous" in {
        Given("a structural WORKFLOW definition with two Composite StateMachine children")
        val model = _model(_workflow_source().replace(
          "### REQUIRED-OPERATION",
          "### COMPOSITE-STATEMACHINE\n\n### REQUIRED-OPERATION"
        ))

        When("the Workflow structure is classified")
        val error = intercept[RuntimeException] {
          CompositeStateMachineCml.workflowDefinitions(model)
        }

        Then("the ambiguous structural declaration is diagnosed")
        error.getMessage should include("requires exactly one COMPOSITE-STATEMACHINE structural section")
      }

      "reject misplaced Composite StateMachine structure at the workflow definition level" in {
        Given("a direct WORKFLOW STATE section outside the Composite StateMachine child")
        val model = _model(_workflow_source().replace(
          "### REQUIRED-OPERATION",
          "### STATE\n\n#### Invalid\n\n### REQUIRED-OPERATION"
        ))

        When("the Workflow structure is classified")
        val error = intercept[RuntimeException] {
          CompositeStateMachineCml.workflowDefinitions(model)
        }

        Then("the undeclared structural content is diagnosed")
        error.getMessage should include("does not admit misplaced structural section 'STATE'")
      }

      "reject execution-binding vocabulary instead of interpreting it as workflow semantics" in {
        Given("a WORKFLOW definition that authors an ORCHESTRATION heading")
        val model = _model(_workflow_source().replace("### Overview", "### ORCHESTRATION"))

        When("the Workflow structure is classified")
        val error = intercept[RuntimeException] {
          CompositeStateMachineCml.workflowDefinitions(model)
        }

        Then("the execution-binding vocabulary is diagnosed")
        error.getMessage should include("does not admit execution-binding vocabulary 'ORCHESTRATION'")
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

  private def _narrative_workflow_source: String =
    """# WORKFLOW
      |
      |## Overview
      |
      |This document explains a future order-processing approach.
      |
      |### Summary
      |
      |The heading and prose are narrative, not a workflow declaration.
      |""".stripMargin

  private def _standalone_required_operation_source: String =
    """# WORKFLOW
      |
      |## OrderProgress
      |
      |version = workflow-v1
      |
      |### REQUIRED-OPERATION
      |
      |#### capture-payment-capability
      |
      |action = capture-payment
      |""".stripMargin

  private def _workflow_source(): String =
    """# WORKFLOW
      |
      |## OrderProgress
      |
      |version = workflow-v1
      |
      |This prose gives human readers context and has no executable workflow meaning.
      |
      |### Overview
      |
      |The payment and fulfillment lifecycles jointly describe order progress.
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
      |##### fulfillment
      |
      |state-machine = FulfillmentLifecycle
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
      |when = payment.Awaiting, fulfillment.Waiting
      |
      |##### complete
      |
      |state = Complete
      |when = payment.Paid, fulfillment.Shipped
      |
      |##### payment-paid
      |
      |state = Pending
      |when = payment.Paid, fulfillment.Waiting
      |
      |##### fulfillment-shipped
      |
      |state = Pending
      |when = payment.Awaiting, fulfillment.Shipped
      |
      |#### INITIAL
      |
      |payment.Awaiting, fulfillment.Waiting
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
