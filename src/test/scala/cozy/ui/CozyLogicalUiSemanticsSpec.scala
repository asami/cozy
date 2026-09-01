package cozy.ui

import cozy.ui.CozyLogicalUi._
import cozy.ui.CozyLogicalUiSemantics._
import org.scalacheck.{Gen, Prop, Test}
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Sep. 1, 2026
 * @version Sep. 1, 2026
 * @author  ASAMI, Tomoharu
 */
final class CozyLogicalUiSemanticsSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  "Cozy Logical UI semantic catalogs" should {
    "classify every projected screen region and interaction while preserving exact projection identity" in {
      Given("one normalized Logical UI projection and complete typed screen semantics")
      val candidatevalue = _right(CozyLogicalUi.candidate(_candidate_input))
      val projectionvalue = _right(CozyLogicalUi.project(candidatevalue, _projection(candidatevalue)))
      val inputvalue = _valid_semantics_input(projectionvalue)

      When("the semantic projection is normalized against the supplied screen projection")
      val resultvalue = CozyLogicalUiSemantics.project(projectionvalue, inputvalue)

      Then("each exact screen, region, interaction pattern, and UI state is retained under the supplied projection identity")
      val normalizedvalue = _right(resultvalue)
      normalizedvalue.projectionIdentity shouldBe projectionvalue.identity
      normalizedvalue.input.screens.map(_.screenId).toSet shouldBe projectionvalue.input.screens.map(_.id).toSet
      normalizedvalue.input.screens.head.regions.map(_.display.id).toSet shouldBe Set("collection", "detail")
      normalizedvalue.input.screens.head.interactions.map(_.pattern.id).toSet shouldBe Set("navigate", "select", "input", "command", "observe")
      normalizedvalue.input.screens.head.interactionStates.map(_.state.id).toSet shouldBe Set("idle", "pending", "succeeded", "failed")
      normalizedvalue.identity should startWith("sha256:")
      CozyLogicalUiSemantics.schema shouldBe "cozy.logical-ui-semantics.v1"
    }
  }

  "Cozy Logical UI component and constraint semantics" should {
    "bind Datatype, Value multiplicity, Aggregate invariant, Operation DbC, Powertype, and StateMachine without source discovery" in {
      Given("a projection and explicit semantic bindings for the complete public Component vocabulary")
      val candidatevalue = _right(CozyLogicalUi.candidate(_candidate_input))
      val projectionvalue = _right(CozyLogicalUi.project(candidatevalue, _projection(candidatevalue)))
      val semanticvalue = _right(CozyLogicalUiSemantics.project(projectionvalue, _valid_semantics_input(projectionvalue)))

      When("the typed semantic projection exposes its explicit Component and constraint bindings")
      val bindingsvalue = semanticvalue.input.componentBindings
      val constraintsvalue = semanticvalue.input.constraints

      Then("only exact projected bindings are admitted and every required meaning remains typed")
      bindingsvalue.map(_.role).toSet shouldBe Set(
        EntityRole,
        AggregateRole,
        ServiceRole,
        OperationRole,
        ValueRole,
        DatatypeRole,
        ViewRole,
        PowertypeRole,
        StateMachineRole
      )
      bindingsvalue.find(_.role == ValueRole).get.multiplicity shouldBe Some(ExactlyOneMultiplicity)
      bindingsvalue.find(_.role == DatatypeRole).get.multiplicity shouldBe Some(ZeroOrMoreMultiplicity)
      bindingsvalue.find(_.role == PowertypeRole).get.powertypeVariantId shouldBe Some("awaiting-confirmation")
      bindingsvalue.find(_.role == StateMachineRole).get.domainStates.map(_.stateId) shouldBe Vector("confirmed", "draft")
      constraintsvalue.map(_.kind).toSet shouldBe Set(
        DatatypeConstraint,
        ValueConstraint,
        AggregateInvariant,
        OperationPrecondition,
        OperationPostcondition
      )
      constraintsvalue.filter(_.binding == _operation_binding).map(_.identity).toSet shouldBe Set(
        ConstraintIdentity("confirm-order", "SALES_ORDER_PRE"),
        ConstraintIdentity("confirm-order", "SALES_ORDER_POST")
      )
    }
  }

  "Cozy Logical UI validation semantics" should {
    "keep validation authorities distinct and preserve the whole constraint identity in feedback associations" in {
      Given("typed local, contextual, and server-authoritative constraints plus exact feedback associations")
      val candidatevalue = _right(CozyLogicalUi.candidate(_candidate_input))
      val projectionvalue = _right(CozyLogicalUi.project(candidatevalue, _projection(candidatevalue)))
      val inputvalue = _valid_semantics_input(projectionvalue)

      When("the semantic validation classifications and feedback links are projected")
      val resultvalue = _right(CozyLogicalUiSemantics.project(projectionvalue, inputvalue))

      Then("authority classification remains closed and feedback references the exact shared constraintId/detailCode pair")
      resultvalue.input.constraints.map(_.validation).toSet shouldBe Set(
        LocalDeterministicValidation,
        ContextDependentValidation,
        ServerAuthoritativeValidation
      )
      resultvalue.input.feedbackAssociations should contain allElementsOf Vector(
        FeedbackConstraintAssociation("validation-failed", ConstraintIdentity("confirm-order", "VALUE_LOCAL")),
        FeedbackConstraintAssociation("conflict", ConstraintIdentity("confirm-order", "SALES_ORDER_PRE"))
      )
      val copieddetailvalue = inputvalue.copy(feedbackAssociations = Vector(
        FeedbackConstraintAssociation("validation-failed", ConstraintIdentity("confirm-order", "VALUE_LOCAL_COPY"))
      ))
      _left(CozyLogicalUiSemantics.project(projectionvalue, copieddetailvalue)).code shouldBe "LUI43_SEMANTICS_FEEDBACK_CONSTRAINT_INVALID"
    }
  }

  "Cozy Logical UI semantic closure" should {
    "reject a null multiplicity element with a structured diagnostic" in {
      Given("a valid semantic input with one Value multiplicity element replaced by null")
      val candidatevalue = _right(CozyLogicalUi.candidate(_candidate_input))
      val projectionvalue = _right(CozyLogicalUi.project(candidatevalue, _projection(candidatevalue)))
      val inputvalue = _valid_semantics_input(projectionvalue)
      val malformedvalue = inputvalue.copy(componentBindings = inputvalue.componentBindings.map(item =>
        if (item.role == ValueRole) item.copy(multiplicity = Some(null: Multiplicity)) else item
      ))

      When("the malformed semantic value is submitted before canonical serialization")
      val resultvalue = CozyLogicalUiSemantics.project(projectionvalue, malformedvalue)

      Then("the semantic boundary returns the stable multiplicity diagnostic without dereferencing null")
      _left(resultvalue).code shouldBe "LUI43_SEMANTICS_MULTIPLICITY_INVALID"
    }

    "fail closed for unknown identity, incomplete coverage, incompatible patterns, unadmitted bindings, invalid multiplicity, and incompatible constraint categories" in {
      Given("one valid semantic input and independently malformed variants")
      val candidatevalue = _right(CozyLogicalUi.candidate(_candidate_input))
      val projectionvalue = _right(CozyLogicalUi.project(candidatevalue, _projection(candidatevalue)))
      val inputvalue = _valid_semantics_input(projectionvalue)
      val missingvalue = inputvalue.copy(screens = Vector.empty)
      val unknownvalue = inputvalue.copy(projectionIdentity = "sha256:" + "0" * 64)
      val incompatiblevalue = inputvalue.copy(screens = inputvalue.screens.map(screen => screen.copy(
        interactions = screen.interactions.map(item => if (item.interactionId == "navigate") item.copy(pattern = InputPattern) else item)
      )))
      val unknownbindingvalue = inputvalue.copy(componentBindings = inputvalue.componentBindings :+ ComponentSemanticBinding(EntityRole, _unknown_binding))
      val multiplicityvalue = inputvalue.copy(componentBindings = inputvalue.componentBindings.map(item => if (item.role == ServiceRole) item.copy(multiplicity = Some(ExactlyOneMultiplicity)) else item))
      val categoryvalue = inputvalue.copy(constraints = inputvalue.constraints.map(item => if (item.kind == DatatypeConstraint) item.copy(binding = _value_binding) else item))

      When("each malformed semantic value is submitted to the closed projection boundary")
      val missingresult = CozyLogicalUiSemantics.project(projectionvalue, missingvalue)
      val unknownresult = CozyLogicalUiSemantics.project(projectionvalue, unknownvalue)
      val incompatibleresult = CozyLogicalUiSemantics.project(projectionvalue, incompatiblevalue)
      val unknownbindingresult = CozyLogicalUiSemantics.project(projectionvalue, unknownbindingvalue)
      val multiplicityresult = CozyLogicalUiSemantics.project(projectionvalue, multiplicityvalue)
      val categoryresult = CozyLogicalUiSemantics.project(projectionvalue, categoryvalue)

      Then("the exact semantic diagnostics identify the violated authority boundary")
      _left(missingresult).code shouldBe "LUI43_SEMANTICS_SCREEN_COVERAGE"
      _left(unknownresult).code shouldBe "LUI43_SEMANTICS_PROJECTION_IDENTITY_INVALID"
      _left(incompatibleresult).code shouldBe "LUI43_SEMANTICS_INTERACTION_PATTERN_INVALID"
      _left(unknownbindingresult).code shouldBe "LUI43_SEMANTICS_COMPONENT_BINDING_INVALID"
      _left(multiplicityresult).code shouldBe "LUI43_SEMANTICS_MULTIPLICITY_INVALID"
      _left(categoryresult).code shouldBe "LUI43_SEMANTICS_CONSTRAINT_INVALID"
    }
  }

  "Cozy Logical UI StateMachine admission" should {
    "separate domain, Workflow, and UI states and require a mapped non-system step with matching Operation and StateMachine roles" in {
      Given("one explicit transition action over a normal UI step, domain states, opaque Workflow state, and UI lifecycle state")
      val candidatevalue = _right(CozyLogicalUi.candidate(_candidate_input))
      val projectionvalue = _right(CozyLogicalUi.project(candidatevalue, _projection(candidatevalue)))
      val inputvalue = _valid_semantics_input(projectionvalue)
      val transitionvalue = inputvalue.transitionActions.head

      When("the transition admission is projected without executing any state machine or workflow")
      val resultvalue = _right(CozyLogicalUiSemantics.project(projectionvalue, inputvalue))
      val systemvalue = inputvalue.copy(transitionActions = Vector(transitionvalue.copy(
        step = UiUseCaseStep(_ui_use_case, "system-only", SystemOnlyPath)
      )))
      val mismatchvalue = inputvalue.copy(transitionActions = Vector(transitionvalue.copy(operation = _service_binding)))

      Then("only the admitted action is exposed and the three state domains remain distinct")
      resultvalue.input.transitionActions.head.from shouldBe DomainStateReference(_state_machine_binding, "draft")
      resultvalue.input.transitionActions.head.workflowState shouldBe Some(WorkflowStateReference("sales-order-workflow", "awaiting"))
      resultvalue.input.transitionActions.head.interactionState shouldBe SucceededInteractionState
      _left(CozyLogicalUiSemantics.project(projectionvalue, systemvalue)).code shouldBe "LUI43_SEMANTICS_TRANSITION_ADMISSION_INVALID"
      _left(CozyLogicalUiSemantics.project(projectionvalue, mismatchvalue)).code shouldBe "LUI43_SEMANTICS_TRANSITION_ADMISSION_INVALID"
    }
  }

  "Cozy Logical UI semantic identity" should {
    "remain deterministic for every admitted semantic vector permutation" in {
      Given("a valid semantic input and a ScalaCheck generator selecting canonical or reversed vectors")
      val candidatevalue = _right(CozyLogicalUi.candidate(_candidate_input))
      val projectionvalue = _right(CozyLogicalUi.project(candidatevalue, _projection(candidatevalue)))
      val inputvalue = _valid_semantics_input(projectionvalue)
      val property = Prop.forAll(Gen.oneOf(true, false)) { reverse =>
        val value = if (reverse) _reverse_semantics(inputvalue) else inputvalue
        CozyLogicalUiSemantics.project(projectionvalue, value) match {
          case Right(result) =>
            result.identity == _right(CozyLogicalUiSemantics.project(projectionvalue, inputvalue)).identity &&
              result.canonicalContent == _right(CozyLogicalUiSemantics.project(projectionvalue, inputvalue)).canonicalContent
          case Left(_) => false
        }
      }

      When("the semantic vectors are normalized repeatedly")
      val propertyresult = Test.check(Test.Parameters.default.withMinSuccessfulTests(20), property)

      Then("canonical content and semantic identity remain equal for equivalent structural orderings")
      propertyresult.passed shouldBe true
    }
  }

  private val _sales_order = ComponentCoordinate("org.example.sales", "SalesOrder", "1.0.0")
  private val _business_use_case = UseCaseReference(Business, "business-confirm-sales-order")
  private val _system_use_case = UseCaseReference(System, "system-confirm-sales-order")
  private val _ui_use_case = UseCaseReference(Ui, "ui-confirm-sales-order")
  private val _valid_use_cases = UseCaseLayers(
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
  private val _powertype_binding = ComponentBinding(_sales_order, "powertype")
  private val _state_machine_binding = ComponentBinding(_sales_order, "state-machine")
  private val _unknown_binding = ComponentBinding(_sales_order, "unknown")
  private val _all_bindings = Vector(
    _entity_binding,
    _aggregate_binding,
    _service_binding,
    _operation_binding,
    _value_binding,
    _datatype_binding,
    _view_binding,
    _powertype_binding,
    _state_machine_binding
  )
  private val _candidate_input = CandidateInput(
    Vector(ComponentSurface(_sales_order, _all_bindings.map(_.exportId))),
    _all_bindings,
    _valid_use_cases
  )

  private val _screen = LogicalScreen(
    "order-detail",
    "inspect",
    Vector.empty,
    ScreenSubject(AggregateRole, _aggregate_binding),
    Vector(
      SemanticRegion("root", None, 0),
      SemanticRegion("body", Some("root"), 1)
    ),
    Vector(
      ScreenInteraction("entry", EntryInteraction, Vector(ComponentUsage(EntityRole, _entity_binding)), None, None),
      ScreenInteraction("input", InputInteraction, Vector(ComponentUsage(ValueRole, _value_binding), ComponentUsage(DatatypeRole, _datatype_binding)), None, None),
      ScreenInteraction("query", QueryInteraction, Vector(ComponentUsage(ViewRole, _view_binding)), None, None),
      ScreenInteraction("select", SelectionInteraction, Vector(ComponentUsage(PowertypeRole, _powertype_binding)), None, None),
      ScreenInteraction("invoke", InvocationInteraction, Vector(
        ComponentUsage(ServiceRole, _service_binding),
        ComponentUsage(OperationRole, _operation_binding),
        ComponentUsage(StateMachineRole, _state_machine_binding)
      ), Some(MutationAction(_aggregate_binding, _operation_binding)), None),
      ScreenInteraction("observe", ObservationInteraction, Vector(
        ComponentUsage(AggregateRole, _aggregate_binding),
        ComponentUsage(StateMachineRole, _state_machine_binding)
      ), None, None),
      ScreenInteraction("feedback", FeedbackInteraction, Vector.empty, None, None),
      ScreenInteraction("navigate", NavigationInteraction, Vector.empty, None, Some(NavigationEndpoint("order-detail", "order-detail")))
    ),
    Vector(NormalFeedback)
  )

  private val _step = UiUseCaseStep(_ui_use_case, "confirm", NormalPath)
  private val _projection_input_template = UseCaseScreenProjection(
    "unbound",
    Vector(_valid_use_cases),
    Vector(_step),
    Vector(_screen),
    _screen.interactions.map(item => ScreenInteractionMapping(_ui_use_case, "confirm", "order-detail", item.id)),
    Vector(AggregateBoundary(_aggregate_binding, _aggregate_binding, Vector(_aggregate_binding, _entity_binding), Vector(_operation_binding)))
  )

  private def _projection(candidatevalue: LogicalUiCandidate): UseCaseScreenProjection =
    _projection_input_template.copy(candidateIdentity = candidatevalue.identity)

  private def _valid_semantics_input(projectionvalue: LogicalUiProjection): LogicalUiSemanticsInput = {
    val screenvalue = ScreenSemantics(
      "order-detail",
      ConfirmPurpose,
      Vector(
        RegionDisplayBinding("body", DetailDisplay),
        RegionDisplayBinding("root", CollectionDisplay)
      ),
      Vector(
        InteractionPatternBinding("entry", ObservePattern),
        InteractionPatternBinding("input", InputPattern),
        InteractionPatternBinding("query", ObservePattern),
        InteractionPatternBinding("select", SelectPattern),
        InteractionPatternBinding("invoke", CommandPattern),
        InteractionPatternBinding("observe", ObservePattern),
        InteractionPatternBinding("feedback", ObservePattern),
        InteractionPatternBinding("navigate", NavigatePattern)
      ),
      Vector(
        InteractionStateBinding("entry", IdleInteractionState),
        InteractionStateBinding("input", PendingInteractionState),
        InteractionStateBinding("query", SucceededInteractionState),
        InteractionStateBinding("select", IdleInteractionState),
        InteractionStateBinding("invoke", SucceededInteractionState),
        InteractionStateBinding("observe", IdleInteractionState),
        InteractionStateBinding("feedback", FailedInteractionState),
        InteractionStateBinding("navigate", IdleInteractionState)
      )
    )
    LogicalUiSemanticsInput(
      projectionvalue.identity,
      Vector(screenvalue),
      Vector(
        ComponentSemanticBinding(EntityRole, _entity_binding),
        ComponentSemanticBinding(AggregateRole, _aggregate_binding),
        ComponentSemanticBinding(ServiceRole, _service_binding),
        ComponentSemanticBinding(OperationRole, _operation_binding),
        ComponentSemanticBinding(ValueRole, _value_binding, Some(ExactlyOneMultiplicity)),
        ComponentSemanticBinding(DatatypeRole, _datatype_binding, Some(ZeroOrMoreMultiplicity)),
        ComponentSemanticBinding(ViewRole, _view_binding),
        ComponentSemanticBinding(PowertypeRole, _powertype_binding, powertypeVariantId = Some("awaiting-confirmation")),
        ComponentSemanticBinding(StateMachineRole, _state_machine_binding, domainStates = Vector(
          DomainStateReference(_state_machine_binding, "draft"),
          DomainStateReference(_state_machine_binding, "confirmed")
        ))
      ),
      Vector(
        ConstraintDeclaration(ConstraintIdentity("sales-order-code", "DATATYPE_FORMAT"), DatatypeConstraint, _datatype_binding, LocalDeterministicValidation),
        ConstraintDeclaration(ConstraintIdentity("confirm-order", "VALUE_LOCAL"), ValueConstraint, _value_binding, ContextDependentValidation),
        ConstraintDeclaration(ConstraintIdentity("sales-order", "AGGREGATE_INVARIANT"), AggregateInvariant, _aggregate_binding, ServerAuthoritativeValidation),
        ConstraintDeclaration(ConstraintIdentity("confirm-order", "SALES_ORDER_PRE"), OperationPrecondition, _operation_binding, LocalDeterministicValidation),
        ConstraintDeclaration(ConstraintIdentity("confirm-order", "SALES_ORDER_POST"), OperationPostcondition, _operation_binding, ServerAuthoritativeValidation)
      ),
      Vector(
        FeedbackConstraintAssociation("validation-failed", ConstraintIdentity("confirm-order", "VALUE_LOCAL")),
        FeedbackConstraintAssociation("conflict", ConstraintIdentity("confirm-order", "SALES_ORDER_PRE"))
      ),
      Vector(StateMachineTransitionAction(
        _step,
        "order-detail",
        "invoke",
        _operation_binding,
        _state_machine_binding,
        DomainStateReference(_state_machine_binding, "draft"),
        DomainStateReference(_state_machine_binding, "confirmed"),
        Some(WorkflowStateReference("sales-order-workflow", "awaiting")),
        SucceededInteractionState
      ))
    )
  }

  private def _reverse_semantics(value: LogicalUiSemanticsInput): LogicalUiSemanticsInput =
    value.copy(
      screens = value.screens.reverse.map(screen => screen.copy(
        regions = screen.regions.reverse,
        interactions = screen.interactions.reverse,
        interactionStates = screen.interactionStates.reverse
      )),
      componentBindings = value.componentBindings.reverse.map(item => item.copy(domainStates = item.domainStates.reverse)),
      constraints = value.constraints.reverse,
      feedbackAssociations = value.feedbackAssociations.reverse,
      transitionActions = value.transitionActions.reverse
    )

  private def _right[A](value: Either[LogicalUiError, A]): A = value match {
    case Right(resultvalue) => resultvalue
    case Left(error) => fail(s"Expected a Logical UI semantics result but received ${error.code}: ${error.reason}")
  }

  private def _left[A](value: Either[LogicalUiError, A]): LogicalUiError = value match {
    case Left(error) => error
    case Right(_) => fail("Expected a closed Logical UI semantics failure")
  }
}
