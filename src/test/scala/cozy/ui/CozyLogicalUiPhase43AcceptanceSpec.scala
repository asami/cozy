package cozy.ui

import cozy.ui.CozyLogicalUi._
import cozy.ui.CozyLogicalUiReview._
import cozy.ui.CozyLogicalUiSemantics._
import org.scalacheck.{Gen, Prop, Test}
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Sep. 2, 2026
 * @version Sep. 2, 2026
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

        When("the candidate is projected, semantically classified, and explicitly accepted")
        val coordinate = candidate.input.componentSurfaces.head.component
        val detail = projection.input.screens.find(_.id == "order-detail").get
        val invocation = detail.interactions.find(_.id == "invoke").get
        val transition = semantics.input.transitionActions.head

        Then("the exact SalesOrder identity, three-layer authority, Aggregate Operation boundary, and distinct accepted identities remain bound")
        candidate.input.componentSurfaces should have size 1
        coordinate shouldBe _sales_order
        coordinate.qualifiedName shouldBe "org.example.sales.SalesOrder"
        coordinate.canonicalIdentity shouldBe "org.example.sales.SalesOrder@1.0.0"
        candidate.input.useCases.realizations shouldBe Vector(
          UseCaseRealization(_business_use_case, _system_use_case),
          UseCaseRealization(_system_use_case, _ui_use_case)
        )
        candidate.input.componentBindings should contain allElementsOf _all_bindings
        projection.candidate.identity shouldBe candidate.identity
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
        Set(candidate.identity, candidate.inputIdentity, accepted.decision.decisionIdentity, accepted.identity).size shouldBe 4
        accepted.candidate.identity shouldBe candidate.identity
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
      "produce one review document and typed currentness receipt for every admitted ordering" in {
        Given("canonical and reordered SalesOrder candidate, projection, semantic, and acceptance tuples")
        val first = _fixture()
        val second = _fixture(reversed = true)
        val firstreview = _right(CozyLogicalUiReview.render(first._1, first._2, first._3))
        val secondreview = _right(CozyLogicalUiReview.render(second._1, second._2, second._3))

        When("equivalent tuples are rendered repeatedly through the read-only review boundary")
        val property = Prop.forAll(Gen.oneOf(true, false)) { reverse =>
          val fixture = _fixture(reversed = reverse)
          val review = _right(CozyLogicalUiReview.render(fixture._1, fixture._2, fixture._3))
          review.html == firstreview.html && review.receipt == firstreview.receipt
        }
        val propertyresult = Test.check(Test.Parameters.default.withMinSuccessfulTests(20), property)
        val currentresult = CozyLogicalUiReview.verifyCurrent(first._1, first._2, first._3, firstreview)

        Then("canonical identities, HTML bytes, diagnostics, and typed receipt fields are stable while the page remains read-only")
        first._1.identity shouldBe second._1.identity
        first._2.identity shouldBe second._2.identity
        first._3.identity shouldBe second._3.identity
        firstreview.html shouldBe secondreview.html
        firstreview.htmlBytes shouldBe secondreview.htmlBytes
        firstreview.diagnostics shouldBe empty
        firstreview.receipt.schema shouldBe "cozy.logical-ui-review.v1"
        firstreview.receipt.version shouldBe 1
        firstreview.receipt.rendererProfile shouldBe "cozy.logical-ui-review.renderer.v1"
        firstreview.receipt.htmlIdentity should startWith("sha256:")
        firstreview.receipt.identity should startWith("sha256:")
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
        currentresult shouldBe Right(())
        propertyresult.passed shouldBe true
      }
    }

    "diagnose stale semantic input" which {
      "reject a changed typed semantic projection through verifyCurrent" in {
        Given("a current accepted SalesOrder review and an equivalent semantic input changed only in its typed purpose")
        val fixture = _fixture()
        val review = _right(CozyLogicalUiReview.render(fixture._1, fixture._2, fixture._3))
        val changedinput = fixture._3.input.copy(screens = fixture._3.input.screens.map(screen =>
          if (screen.screenId == "order-detail") screen.copy(purpose = BrowsePurpose) else screen
        ))

        When("the changed semantic input is projected and checked against the existing typed review receipt")
        val changedsemantics = _right(CozyLogicalUiSemantics.project(fixture._2, changedinput))
        val result = CozyLogicalUiReview.verifyCurrent(fixture._1, fixture._2, changedsemantics, review)

        Then("currentness fails closed with the existing structured review diagnostic")
        _left(result).code shouldBe "LUI43_REVIEW_CURRENTNESS_INVALID"
        review.receipt.semanticProjectionIdentity should not equal changedsemantics.identity
      }
    }
  }

  private def _fixture(reversed: Boolean = false): (AcceptedLogicalUi, LogicalUiProjection, LogicalUiSemanticProjection) = {
    val candidate = _right(CozyLogicalUi.candidate(_candidate_input))
    val projection = _right(CozyLogicalUi.project(candidate, _projection(candidate, reversed)))
    val semantics = _right(CozyLogicalUiSemantics.project(projection, _semantics(projection, reversed)))
    val accepted = _right(CozyLogicalUi.accept(candidate, AcceptanceDecision("accepted-sales-order", candidate.identity)))
    (accepted, projection, semantics)
  }

  private def _projection(candidate: LogicalUiCandidate, reversed: Boolean): UseCaseScreenProjection = {
    val usecases = candidate.input.useCases
    val uiusecase = usecases.ui
    val values = UseCaseScreenProjection(
      candidate.identity,
      Vector(usecases),
      Vector(
        UiUseCaseStep(uiusecase, "confirm", NormalPath),
        UiUseCaseStep(uiusecase, "enter", AlternativePath),
        UiUseCaseStep(uiusecase, "failure", ExceptionPath),
        UiUseCaseStep(uiusecase, "system-check", SystemOnlyPath)
      ),
      Vector(_list_screen, _detail_screen),
      Vector(
        ScreenInteractionMapping(uiusecase, "confirm", "order-list", "entry"),
        ScreenInteractionMapping(uiusecase, "confirm", "order-list", "to-detail"),
        ScreenInteractionMapping(uiusecase, "confirm", "order-detail", "select"),
        ScreenInteractionMapping(uiusecase, "confirm", "order-detail", "invoke"),
        ScreenInteractionMapping(uiusecase, "confirm", "order-detail", "observe"),
        ScreenInteractionMapping(uiusecase, "enter", "order-detail", "input"),
        ScreenInteractionMapping(uiusecase, "failure", "order-detail", "feedback")
      ),
      Vector(AggregateBoundary(_aggregate_binding, _aggregate_binding, Vector(_aggregate_binding, _entity_binding), Vector(_operation_binding)))
    )
    if (reversed) values.copy(steps = values.steps.reverse, screens = values.screens.reverse, mappings = values.mappings.reverse) else values
  }

  private def _semantics(projection: LogicalUiProjection, reversed: Boolean): LogicalUiSemanticsInput = {
    val usecase = projection.input.catalog.head.ui
    val values = LogicalUiSemanticsInput(
      projection.identity,
      Vector(
        ScreenSemantics(
          "order-list",
          BrowsePurpose,
          Vector(RegionDisplayBinding("list-root", CollectionDisplay)),
          Vector(
            InteractionPatternBinding("entry", ObservePattern),
            InteractionPatternBinding("to-detail", NavigatePattern)
          ),
          Vector(
            InteractionStateBinding("entry", IdleInteractionState),
            InteractionStateBinding("to-detail", IdleInteractionState)
          )
        ),
        ScreenSemantics(
          "order-detail",
          ConfirmPurpose,
          Vector(
            RegionDisplayBinding("detail-body", DetailDisplay),
            RegionDisplayBinding("detail-root", DetailDisplay)
          ),
          Vector(
            InteractionPatternBinding("input", InputPattern),
            InteractionPatternBinding("select", SelectPattern),
            InteractionPatternBinding("invoke", CommandPattern),
            InteractionPatternBinding("observe", ObservePattern),
            InteractionPatternBinding("feedback", ObservePattern)
          ),
          Vector(
            InteractionStateBinding("input", PendingInteractionState),
            InteractionStateBinding("select", IdleInteractionState),
            InteractionStateBinding("invoke", SucceededInteractionState),
            InteractionStateBinding("observe", IdleInteractionState),
            InteractionStateBinding("feedback", FailedInteractionState)
          )
        )
      ),
      Vector(
        ComponentSemanticBinding(EntityRole, _entity_binding),
        ComponentSemanticBinding(AggregateRole, _aggregate_binding),
        ComponentSemanticBinding(ServiceRole, _service_binding),
        ComponentSemanticBinding(OperationRole, _operation_binding),
        ComponentSemanticBinding(ValueRole, _value_binding, Some(ExactlyOneMultiplicity)),
        ComponentSemanticBinding(DatatypeRole, _datatype_binding, Some(ZeroOrMoreMultiplicity)),
        ComponentSemanticBinding(ViewRole, _view_binding),
        ComponentSemanticBinding(WorkflowRole, _workflow_binding),
        ComponentSemanticBinding(PowertypeRole, _powertype_binding, powertypeVariantId = Some("awaiting-confirmation")),
        ComponentSemanticBinding(
          StateMachineRole,
          _state_machine_binding,
          domainStates = Vector(
            DomainStateReference(_state_machine_binding, "draft"),
            DomainStateReference(_state_machine_binding, "confirmed")
          )
        )
      ),
      Vector(
        ConstraintDeclaration(
          ConstraintIdentity("sales-order-code", "DATATYPE_FORMAT"),
          DatatypeConstraint,
          _datatype_binding,
          LocalDeterministicValidation
        ),
        ConstraintDeclaration(
          ConstraintIdentity("confirm-order", "VALUE_LOCAL"),
          ValueConstraint,
          _value_binding,
          ContextDependentValidation
        ),
        ConstraintDeclaration(
          ConstraintIdentity("sales-order", "AGGREGATE_INVARIANT"),
          AggregateInvariant,
          _aggregate_binding,
          ServerAuthoritativeValidation
        ),
        ConstraintDeclaration(
          ConstraintIdentity("confirm-order", "SALES_ORDER_PRE"),
          OperationPrecondition,
          _operation_binding,
          LocalDeterministicValidation
        ),
        ConstraintDeclaration(
          ConstraintIdentity("confirm-order", "SALES_ORDER_POST"),
          OperationPostcondition,
          _operation_binding,
          ServerAuthoritativeValidation
        )
      ),
      Vector(
        FeedbackConstraintAssociation("validation-failed", ConstraintIdentity("confirm-order", "VALUE_LOCAL")),
        FeedbackConstraintAssociation("conflict", ConstraintIdentity("confirm-order", "SALES_ORDER_PRE"))
      ),
      Vector(
        StateMachineTransitionAction(
          UiUseCaseStep(usecase, "confirm", NormalPath),
          "order-detail",
          "invoke",
          _operation_binding,
          _state_machine_binding,
          DomainStateReference(_state_machine_binding, "draft"),
          DomainStateReference(_state_machine_binding, "confirmed"),
          Some(WorkflowStateReference("sales-order-workflow", "awaiting")),
          SucceededInteractionState
        )
      )
    )
    if (reversed) values.copy(
      screens = values.screens.reverse,
      componentBindings = values.componentBindings.reverse,
      constraints = values.constraints.reverse,
      feedbackAssociations = values.feedbackAssociations.reverse,
      transitionActions = values.transitionActions.reverse
    ) else values
  }

  private val _sales_order = ComponentCoordinate("org.example.sales", "SalesOrder", "1.0.0")
  private val _business_use_case = UseCaseReference(Business, "business-confirm-sales-order")
  private val _system_use_case = UseCaseReference(System, "system-confirm-sales-order")
  private val _ui_use_case = UseCaseReference(Ui, "ui-confirm-sales-order")
  private val _use_cases = UseCaseLayers(
    _business_use_case,
    _system_use_case,
    _ui_use_case,
    Vector(
      UseCaseRealization(_business_use_case, _system_use_case),
      UseCaseRealization(_system_use_case, _ui_use_case)
    )
  )

  private val _entity_binding = ComponentBinding(_sales_order, "entity")
  private val _aggregate_binding = ComponentBinding(_sales_order, "aggregate")
  private val _service_binding = ComponentBinding(_sales_order, "service")
  private val _operation_binding = ComponentBinding(_sales_order, "operation")
  private val _value_binding = ComponentBinding(_sales_order, "value")
  private val _datatype_binding = ComponentBinding(_sales_order, "datatype")
  private val _view_binding = ComponentBinding(_sales_order, "view")
  private val _workflow_binding = ComponentBinding(_sales_order, "workflow")
  private val _powertype_binding = ComponentBinding(_sales_order, "powertype")
  private val _state_machine_binding = ComponentBinding(_sales_order, "state-machine")
  private val _all_bindings = Vector(
    _entity_binding,
    _aggregate_binding,
    _service_binding,
    _operation_binding,
    _value_binding,
    _datatype_binding,
    _view_binding,
    _workflow_binding,
    _powertype_binding,
    _state_machine_binding
  )
  private val _candidate_input = CandidateInput(
    Vector(ComponentSurface(_sales_order, _all_bindings.map(_.exportId))),
    _all_bindings,
    _use_cases
  )

  private val _list_screen = LogicalScreen(
    "order-list",
    "browse",
    Vector("search", "sort"),
    ScreenSubject(ViewRole, _view_binding),
    Vector(SemanticRegion("list-root", None, 0)),
    Vector(
      ScreenInteraction("entry", EntryInteraction, Vector(ComponentUsage(EntityRole, _entity_binding)), None, None),
      ScreenInteraction("to-detail", NavigationInteraction, Vector.empty, None, Some(NavigationEndpoint("to-detail", "order-detail")))
    ),
    Vector(NormalFeedback, LoadingFeedback, EmptyFeedback, UnavailableFeedback)
  )

  private val _detail_screen = LogicalScreen(
    "order-detail",
    "confirm",
    Vector("review"),
    ScreenSubject(WorkflowRole, _workflow_binding),
    Vector(
      SemanticRegion("detail-root", None, 0),
      SemanticRegion("detail-body", Some("detail-root"), 1)
    ),
    Vector(
      ScreenInteraction(
        "input",
        InputInteraction,
        Vector(ComponentUsage(ValueRole, _value_binding), ComponentUsage(DatatypeRole, _datatype_binding)),
        None,
        None
      ),
      ScreenInteraction(
        "select",
        SelectionInteraction,
        Vector(ComponentUsage(PowertypeRole, _powertype_binding)),
        None,
        None
      ),
      ScreenInteraction(
        "invoke",
        InvocationInteraction,
        Vector(
          ComponentUsage(ServiceRole, _service_binding),
          ComponentUsage(OperationRole, _operation_binding),
          ComponentUsage(StateMachineRole, _state_machine_binding)
        ),
        Some(MutationAction(_aggregate_binding, _operation_binding)),
        None
      ),
      ScreenInteraction(
        "observe",
        ObservationInteraction,
        Vector(
          ComponentUsage(AggregateRole, _aggregate_binding)
        ),
        None,
        None
      ),
      ScreenInteraction("feedback", FeedbackInteraction, Vector.empty, None, None)
    ),
    Vector(
      NormalFeedback,
      LoadingFeedback,
      EmptyFeedback,
      UnavailableFeedback,
      ValidationFailedFeedback,
      ConflictFeedback,
      OperationFailedFeedback
    )
  )

  private def _right[A](value: Either[LogicalUiError, A]): A = value match {
    case Right(result) => result
    case Left(error) => fail(s"Expected a Logical UI result but received ${error.code}: ${error.reason}")
  }

  private def _left[A](value: Either[LogicalUiError, A]): LogicalUiError = value match {
    case Left(error) => error
    case Right(_) => fail("Expected a closed Logical UI failure")
  }
}
