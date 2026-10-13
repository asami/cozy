package cozy.ui

import cozy.ui.CozyLogicalUi._
import cozy.ui.CozyLogicalUiReview._
import cozy.ui.CozyLogicalUiSemantics._
import cozy.ui.CozyLogicalUiSalesOrderFixtures.{
  salesOrder => _sales_order,
  businessUseCase => _business_use_case,
  systemUseCase => _system_use_case,
  uiUseCase => _ui_use_case,
  useCases => _use_cases,
  entityBinding => _entity_binding,
  aggregateBinding => _aggregate_binding,
  serviceBinding => _service_binding,
  operationBinding => _operation_binding,
  valueBinding => _value_binding,
  datatypeBinding => _datatype_binding,
  viewBinding => _view_binding,
  workflowBinding => _workflow_binding,
  powertypeBinding => _powertype_binding,
  stateMachineBinding => _state_machine_binding,
  allBindings => _all_bindings
}
import org.scalacheck.{Gen, Prop, Test}
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Sep. 2, 2026
 * @version Oct. 13, 2026
 * @author  ASAMI, Tomoharu
 */
final class CozyLogicalUiPhase43AcceptanceSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  "SalesOrder Logical UI acceptance" should {
    "admit one exact public model" which {
      "preserve the candidate, projection, semantics, and accepted authority flow" in {
        Given("one repository-controlled SalesOrder fixture with exact public bindings and ordered Business to System to UI realizations")
        val fixture = _fixture()
        val accepted = fixture._1
        val candidate = accepted.candidate
        val projection = fixture._2
        val semantics = fixture._3

        When("the accepted candidate, screen projection, and typed semantics are inspected")
        val coordinate = candidate.input.componentSurfaces.head.component
        val detail = projection.input.screens.find(_.id == "order-detail").get
        val invocation = detail.interactions.find(_.id == "invoke").get
        val transition = semantics.input.transitionActions.head

        Then("the exact SalesOrder identity, three-layer authority, Aggregate Operation boundary, and explicit acceptance decision remain available")
        candidate.input.componentSurfaces should have size 1
        coordinate shouldBe _sales_order
        coordinate.qualifiedName shouldBe "org.example.sales.SalesOrder"
        coordinate.canonicalIdentity shouldBe "org.example.sales.SalesOrder@1.0.0"
        candidate.input.useCases.realizations shouldBe Vector(
          UseCaseRealization(_business_use_case, _system_use_case),
          UseCaseRealization(_system_use_case, _ui_use_case)
        )
        candidate.input.componentBindings should contain allElementsOf _all_bindings
        projection.input.aggregateBoundaries.head.root shouldBe _aggregate_binding
        projection.input.aggregateBoundaries.head.publicOperations should contain (_operation_binding)
        detail.subject shouldBe ScreenSubject(WorkflowRole, _workflow_binding)
        invocation.componentUsages.map(_.role).toSet should contain allElementsOf Set(ServiceRole, OperationRole, StateMachineRole)
        invocation.mutation shouldBe Some(MutationAction(_aggregate_binding, _operation_binding))
        transition.operation shouldBe _operation_binding
        transition.stateMachine shouldBe _state_machine_binding
        transition.workflowState shouldBe Some(WorkflowStateReference("sales-order-workflow", "awaiting"))
        transition.from shouldBe DomainStateReference(_state_machine_binding, "draft")
        transition.to shouldBe DomainStateReference(_state_machine_binding, "confirmed")
        semantics.input.componentBindings.map(_.role).toSet shouldBe Set(
          EntityRole,
          AggregateRole,
          ServiceRole,
          OperationRole,
          ValueRole,
          DatatypeRole,
          ViewRole,
          WorkflowRole,
          PowertypeRole,
          StateMachineRole
        )
        accepted.candidate.input shouldBe candidate.input
        accepted.decision.decisionId shouldBe "accepted-sales-order"
      }
    }

    "describe representative screen semantics" which {
      "cover list and detail interaction, feedback, and constraint authority" in {
        Given("the accepted SalesOrder fixture with list/detail screens and complete typed semantic catalogs")
        val fixture = _fixture()
        val projection = fixture._2
        val semantics = fixture._3
        val list = projection.input.screens.find(_.id == "order-list").get
        val detail = projection.input.screens.find(_.id == "order-detail").get

        When("the normalized projection and semantic catalog are consumed as the read-only driver model")
        val navigation = list.interactions.find(_.id == "to-detail").get.navigation.get
        val input = detail.interactions.find(_.id == "input").get
        val selection = detail.interactions.find(_.id == "select").get
        val constraints = semantics.input.constraints
        val feedback = detail.feedbackStates

        Then("navigation, Value/Datatype input, Powertype selection, StateMachine action, all feedback states, and DbC authority classifications are explicit")
        list.subject shouldBe ScreenSubject(ViewRole, _view_binding)
        navigation shouldBe NavigationEndpoint("to-detail", "order-detail")
        input.componentUsages.map(_.role).toSet shouldBe Set(ValueRole, DatatypeRole)
        selection.componentUsages.map(_.role).toSet shouldBe Set(PowertypeRole)
        semantics.input.componentBindings.find(_.role == ValueRole).get.multiplicity shouldBe Some(ExactlyOneMultiplicity)
        semantics.input.componentBindings.find(_.role == DatatypeRole).get.multiplicity shouldBe Some(ZeroOrMoreMultiplicity)
        semantics.input.componentBindings.find(_.role == PowertypeRole).get.powertypeVariantId shouldBe Some("awaiting-confirmation")
        semantics.input.componentBindings.find(_.role == StateMachineRole).get.domainStates.map(_.stateId) shouldBe Vector("confirmed", "draft")
        feedback.toSet shouldBe Set(
          NormalFeedback,
          LoadingFeedback,
          EmptyFeedback,
          UnavailableFeedback,
          ValidationFailedFeedback,
          ConflictFeedback,
          OperationFailedFeedback
        )
        feedback.count(_ == UnavailableFeedback) shouldBe 1
        constraints.map(_.kind).toSet shouldBe Set(
          DatatypeConstraint,
          ValueConstraint,
          AggregateInvariant,
          OperationPrecondition,
          OperationPostcondition
        )
        constraints.map(_.validation).toSet shouldBe Set(
          LocalDeterministicValidation,
          ContextDependentValidation,
          ServerAuthoritativeValidation
        )
        constraints.filter(_.binding == _operation_binding).map(_.kind).toSet shouldBe Set(OperationPrecondition, OperationPostcondition)
        semantics.input.feedbackAssociations should contain allElementsOf Vector(
          FeedbackConstraintAssociation("validation-failed", ConstraintIdentity("confirm-order", "VALUE_LOCAL")),
          FeedbackConstraintAssociation("conflict", ConstraintIdentity("confirm-order", "SALES_ORDER_PRE"))
        )
      }
    }

    "remain deterministic under repeated equivalent input" which {
      "produce one complete review document for every admitted ordering" in {
        Given("canonical and reordered SalesOrder candidate, projection, semantic, and acceptance tuples")
        val first = _fixture()
        val second = _fixture(reversed = true)
        When("equivalent tuples are rendered repeatedly through the read-only review boundary")
        val firstreview = _right(CozyLogicalUiReview.render(first._1, first._2, first._3))
        val secondreview = _right(CozyLogicalUiReview.render(second._1, second._2, second._3))
        val property = Prop.forAll(Gen.oneOf(true, false)) { reverse =>
          val fixture = _fixture(reversed = reverse)
          val review = _right(CozyLogicalUiReview.render(fixture._1, fixture._2, fixture._3))
          review.html == firstreview.html
        }
        val propertyresult = Test.check(Test.Parameters.default.withMinSuccessfulTests(20), property)

        Then("HTML bytes and diagnostics are stable while the page remains read-only")
        firstreview.html shouldBe secondreview.html
        firstreview.htmlBytes shouldBe secondreview.htmlBytes
        firstreview.diagnostics shouldBe empty
        firstreview.html should include ("Business -&gt; System -&gt; UI")
        firstreview.html should include ("Step-to-screen interaction coverage")
        firstreview.html should include ("Navigation and reachability")
        firstreview.html should include ("Component semantic bindings")
        firstreview.html should include ("Constraints, validation authority")
        firstreview.html should include ("StateMachine transition admission evidence")
        firstreview.html should include ("data-screen-id=\"order-list\"")
        firstreview.html should include ("Workflow: <code>org.example.sales.SalesOrder@1.0.0#workflow</code>")
        firstreview.html should include ("workflowId=sales-order-workflow; stateId=awaiting")
        firstreview.html should not include ("<script")
        firstreview.html should not include ("<form")
        firstreview.html should not include ("http://")
        firstreview.html should not include ("https://")
        propertyresult.passed shouldBe true
      }
    }

  }

  private def _fixture(reversed: Boolean = false): (AcceptedLogicalUi, LogicalUiProjection, LogicalUiSemanticProjection) = {
    val fixture = _right(CozyLogicalUiSalesOrderFixtures.candidate(reversed))
    val candidate = fixture._1
    val accepted = _right(CozyLogicalUi.accept(candidate, AcceptanceDecision("accepted-sales-order")))
    (accepted, fixture._2, fixture._3)
  }

  private def _right[A](value: Either[LogicalUiError, A]): A = value match {
    case Right(result) => result
    case Left(error) => fail(s"Expected a Logical UI result but received ${error.code}: ${error.reason}")
  }

}
