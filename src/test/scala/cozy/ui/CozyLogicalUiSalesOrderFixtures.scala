package cozy.ui

import cozy.ui.CozyLogicalUi._
import cozy.ui.CozyLogicalUiSemantics._

/*
 * @since   Oct. 8, 2026
 * @version Oct. 8, 2026
 * @author  ASAMI, Tomoharu
 */
private[cozy] object CozyLogicalUiSalesOrderFixtures {
  val salesOrder = ComponentCoordinate("org.example.sales", "SalesOrder", "1.0.0")
  val businessUseCase = UseCaseReference(Business, "business-confirm-sales-order")
  val systemUseCase = UseCaseReference(System, "system-confirm-sales-order")
  val uiUseCase = UseCaseReference(Ui, "ui-confirm-sales-order")
  val useCases = UseCaseLayers(
    businessUseCase,
    systemUseCase,
    uiUseCase,
    Vector(
      UseCaseRealization(businessUseCase, systemUseCase),
      UseCaseRealization(systemUseCase, uiUseCase)
    )
  )

  val entityBinding = ComponentBinding(salesOrder, "entity")
  val aggregateBinding = ComponentBinding(salesOrder, "aggregate")
  val serviceBinding = ComponentBinding(salesOrder, "service")
  val operationBinding = ComponentBinding(salesOrder, "operation")
  val valueBinding = ComponentBinding(salesOrder, "value")
  val datatypeBinding = ComponentBinding(salesOrder, "datatype")
  val viewBinding = ComponentBinding(salesOrder, "view")
  val workflowBinding = ComponentBinding(salesOrder, "workflow")
  val powertypeBinding = ComponentBinding(salesOrder, "powertype")
  val stateMachineBinding = ComponentBinding(salesOrder, "state-machine")
  val allBindings = Vector(
    entityBinding,
    aggregateBinding,
    serviceBinding,
    operationBinding,
    valueBinding,
    datatypeBinding,
    viewBinding,
    workflowBinding,
    powertypeBinding,
    stateMachineBinding
  )
  val candidateInput = CandidateInput(
    Vector(ComponentSurface(salesOrder, allBindings.map(_.exportId))),
    allBindings,
    useCases
  )

  val listScreen = LogicalScreen(
    "order-list",
    "browse",
    Vector("search", "sort"),
    ScreenSubject(ViewRole, viewBinding),
    Vector(SemanticRegion("list-root", None, 0)),
    Vector(
      ScreenInteraction("entry", EntryInteraction, Vector(ComponentUsage(EntityRole, entityBinding)), None, None),
      ScreenInteraction("to-detail", NavigationInteraction, Vector.empty, None, Some(NavigationEndpoint("to-detail", "order-detail")))
    ),
    Vector(NormalFeedback, LoadingFeedback, EmptyFeedback, UnavailableFeedback)
  )

  val detailScreen = LogicalScreen(
    "order-detail",
    "confirm",
    Vector("review"),
    ScreenSubject(WorkflowRole, workflowBinding),
    Vector(
      SemanticRegion("detail-root", None, 0),
      SemanticRegion("detail-body", Some("detail-root"), 1)
    ),
    Vector(
      ScreenInteraction(
        "input",
        InputInteraction,
        Vector(ComponentUsage(ValueRole, valueBinding), ComponentUsage(DatatypeRole, datatypeBinding)),
        None,
        None
      ),
      ScreenInteraction(
        "select",
        SelectionInteraction,
        Vector(ComponentUsage(PowertypeRole, powertypeBinding)),
        None,
        None
      ),
      ScreenInteraction(
        "invoke",
        InvocationInteraction,
        Vector(
          ComponentUsage(ServiceRole, serviceBinding),
          ComponentUsage(OperationRole, operationBinding),
          ComponentUsage(StateMachineRole, stateMachineBinding)
        ),
        Some(MutationAction(aggregateBinding, operationBinding)),
        None
      ),
      ScreenInteraction(
        "observe",
        ObservationInteraction,
        Vector(
          ComponentUsage(AggregateRole, aggregateBinding)
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

  def projectionInput(candidate: LogicalUiCandidate, reversed: Boolean = false): UseCaseScreenProjection = {
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
      Vector(listScreen, detailScreen),
      Vector(
        ScreenInteractionMapping(uiusecase, "confirm", "order-list", "entry"),
        ScreenInteractionMapping(uiusecase, "confirm", "order-list", "to-detail"),
        ScreenInteractionMapping(uiusecase, "confirm", "order-detail", "select"),
        ScreenInteractionMapping(uiusecase, "confirm", "order-detail", "invoke"),
        ScreenInteractionMapping(uiusecase, "confirm", "order-detail", "observe"),
        ScreenInteractionMapping(uiusecase, "enter", "order-detail", "input"),
        ScreenInteractionMapping(uiusecase, "failure", "order-detail", "feedback")
      ),
      Vector(AggregateBoundary(aggregateBinding, aggregateBinding, Vector(aggregateBinding, entityBinding), Vector(operationBinding)))
    )
    if (reversed) values.copy(
      catalog = values.catalog.reverse.map(value => value.copy(realizations = value.realizations.reverse)),
      steps = values.steps.reverse,
      screens = values.screens.reverse.map(screen => screen.copy(
        secondaryPurposes = screen.secondaryPurposes.reverse,
        regions = screen.regions.reverse,
        interactions = screen.interactions.reverse.map(interaction => interaction.copy(componentUsages = interaction.componentUsages.reverse)),
        feedbackStates = screen.feedbackStates.reverse
      )),
      mappings = values.mappings.reverse,
      aggregateBoundaries = values.aggregateBoundaries.reverse.map(boundary => boundary.copy(
        members = boundary.members.reverse, publicOperations = boundary.publicOperations.reverse
      ))
    ) else values
  }

  def semanticsInput(projection: LogicalUiProjection, reversed: Boolean = false): LogicalUiSemanticsInput = {
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
        ComponentSemanticBinding(EntityRole, entityBinding),
        ComponentSemanticBinding(AggregateRole, aggregateBinding),
        ComponentSemanticBinding(ServiceRole, serviceBinding),
        ComponentSemanticBinding(OperationRole, operationBinding),
        ComponentSemanticBinding(ValueRole, valueBinding, Some(ExactlyOneMultiplicity)),
        ComponentSemanticBinding(DatatypeRole, datatypeBinding, Some(ZeroOrMoreMultiplicity)),
        ComponentSemanticBinding(ViewRole, viewBinding),
        ComponentSemanticBinding(WorkflowRole, workflowBinding),
        ComponentSemanticBinding(PowertypeRole, powertypeBinding, powertypeVariantId = Some("awaiting-confirmation")),
        ComponentSemanticBinding(
          StateMachineRole,
          stateMachineBinding,
          domainStates = Vector(
            DomainStateReference(stateMachineBinding, "draft"),
            DomainStateReference(stateMachineBinding, "confirmed")
          )
        )
      ),
      Vector(
        ConstraintDeclaration(
          ConstraintIdentity("sales-order-code", "DATATYPE_FORMAT"),
          DatatypeConstraint,
          datatypeBinding,
          LocalDeterministicValidation
        ),
        ConstraintDeclaration(
          ConstraintIdentity("confirm-order", "VALUE_LOCAL"),
          ValueConstraint,
          valueBinding,
          ContextDependentValidation
        ),
        ConstraintDeclaration(
          ConstraintIdentity("sales-order", "AGGREGATE_INVARIANT"),
          AggregateInvariant,
          aggregateBinding,
          ServerAuthoritativeValidation
        ),
        ConstraintDeclaration(
          ConstraintIdentity("confirm-order", "SALES_ORDER_PRE"),
          OperationPrecondition,
          operationBinding,
          LocalDeterministicValidation
        ),
        ConstraintDeclaration(
          ConstraintIdentity("confirm-order", "SALES_ORDER_POST"),
          OperationPostcondition,
          operationBinding,
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
          operationBinding,
          stateMachineBinding,
          DomainStateReference(stateMachineBinding, "draft"),
          DomainStateReference(stateMachineBinding, "confirmed"),
          Some(WorkflowStateReference("sales-order-workflow", "awaiting")),
          SucceededInteractionState
        )
      )
    )
    if (reversed) values.copy(
      screens = values.screens.reverse.map(screen => screen.copy(
        regions = screen.regions.reverse,
        interactions = screen.interactions.reverse,
        interactionStates = screen.interactionStates.reverse
      )),
      componentBindings = values.componentBindings.reverse.map(binding => binding.copy(domainStates = binding.domainStates.reverse)),
      constraints = values.constraints.reverse,
      feedbackAssociations = values.feedbackAssociations.reverse,
      transitionActions = values.transitionActions.reverse
    ) else values
  }

  final case class DemoOrder(
    orderId: String, label: String, amount: String, domainState: DomainStateReference)

  final case class CompactSuccessFixture(
    fixtureId: String, coverageId: String, initialOrder: DemoOrder,
    successfulOrder: DemoOrder, successUiState: UiInteractionState)

  val compactSuccess: CompactSuccessFixture = CompactSuccessFixture(
    "sales-order-compact-v1",
    "P50-COMPACT-NORMAL-001",
    DemoOrder("SO-001", "Sales order SO-001", "100.00", DomainStateReference(stateMachineBinding, "draft")),
    DemoOrder("SO-001", "Sales order SO-001", "100.00", DomainStateReference(stateMachineBinding, "confirmed")),
    SucceededInteractionState
  )

  val compactWebScenario: CozyLogicalUiCompactWeb.CompactScenario = {
    val fixture = compactSuccess
    def _record_(order: DemoOrder): CozyLogicalUiCompactWeb.OrderRecord =
      CozyLogicalUiCompactWeb.OrderRecord(order.orderId, order.label, order.amount, order.domainState)
    CozyLogicalUiCompactWeb.CompactScenario(
      fixture.fixtureId,
      fixture.coverageId,
      CozyLogicalUiCompactWeb.CompactFlow(
        UiUseCaseStep(uiUseCase, "confirm", NormalPath),
        listScreen.id, "entry", "to-detail", detailScreen.id, "select", "invoke", "observe"
      ),
      _record_(fixture.initialOrder),
      _record_(fixture.successfulOrder),
      fixture.successUiState
    )
  }

  def candidate(reversed: Boolean = false): Either[LogicalUiError,
    (LogicalUiCandidate, LogicalUiProjection, LogicalUiSemanticProjection)] = {
    val input = if (reversed) candidateInput.copy(
      componentSurfaces = candidateInput.componentSurfaces.reverse.map(surface => surface.copy(exportIds = surface.exportIds.reverse)),
      componentBindings = candidateInput.componentBindings.reverse,
      useCases = candidateInput.useCases.copy(realizations = candidateInput.useCases.realizations.reverse)
    ) else candidateInput
    for {
      candidate <- CozyLogicalUi.candidate(input)
      projection <- CozyLogicalUi.project(candidate, projectionInput(candidate, reversed))
      semantics <- CozyLogicalUiSemantics.project(projection, semanticsInput(projection, reversed))
    } yield (candidate, projection, semantics)
  }
}
