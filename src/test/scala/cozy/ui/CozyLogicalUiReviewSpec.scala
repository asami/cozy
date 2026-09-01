package cozy.ui

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Paths}

import cozy.ui.CozyLogicalUi._
import cozy.ui.CozyLogicalUiReview._
import cozy.ui.CozyLogicalUiSemantics._
import org.scalacheck.{Gen, Prop, Test}
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Sep. 1, 2026
 * @version Sep. 2, 2026
 * @author  ASAMI, Tomoharu
 */
final class CozyLogicalUiReviewSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  "Cozy Logical UI review projection" should {
    "render complete self-contained HTML and equal receipts for equivalent normalized input order" in {
      Given("one accepted Logical UI, its screen projection, and its typed semantic projection")
      val first = _fixture()
      val second = _fixture(reversed = true)

      When("both equivalent input orderings are rendered through the review boundary")
      val firstreview = _right(CozyLogicalUiReview.render(first._1, first._2, first._3))
      val secondreview = _right(CozyLogicalUiReview.render(second._1, second._2, second._3))
      val property = Prop.forAll(Gen.oneOf(true, false)) { reverse =>
        val value = _fixture(reversed = reverse)
        _right(CozyLogicalUiReview.render(value._1, value._2, value._3)).html == firstreview.html
      }
      val propertyresult = Test.check(Test.Parameters.default.withMinSuccessfulTests(20), property).passed

      Then("HTML bytes and typed receipts are deterministic and expose the complete read-only review structure")
      firstreview.html shouldBe secondreview.html
      firstreview.receipt shouldBe secondreview.receipt
      firstreview.html should include("Business -&gt; System -&gt; UI")
      firstreview.html should include("Step-to-screen interaction coverage")
      firstreview.html should include("Navigation and reachability")
      firstreview.html should include("Component semantic bindings")
      firstreview.html should include("Constraints, validation authority")
      firstreview.html should include("StateMachine transition admission evidence")
      firstreview.html should include("Review diagnostics")
      firstreview.html should include("data-screen-id=\"order-list\"")
      firstreview.html should include("Logical primary purpose")
      firstreview.html should include("Logical secondary purposes")
      firstreview.html should include("search, sort")
      firstreview.html should include("Semantic Purpose")
      firstreview.html should include("Workflow state")
      firstreview.html should include("workflowId=sales-order-workflow; stateId=awaiting")
      firstreview.html should not include "<script"
      firstreview.html should not include "<form"
      firstreview.html should not include "receiptIdentity"
      firstreview.html should not include "http://"
      firstreview.html should not include "https://"
      propertyresult shouldBe true
    }

    "bind exact receipt identities while excluding receipt identity from HTML" in {
      Given("a valid accepted candidate and normalized review tuple")
      val fixture = _fixture()

      When("the typed receipt and emitted HTML are inspected")
      val reviewvalue = _right(CozyLogicalUiReview.render(fixture._1, fixture._2, fixture._3))
      val receipt = reviewvalue.receipt
      val identities = Vector(
        receipt.acceptedIdentity,
        receipt.candidateIdentity,
        receipt.consumedInputIdentity,
        receipt.useCaseIdentity,
        receipt.catalogIdentity,
        receipt.projectionIdentity,
        receipt.semanticProjectionIdentity,
        receipt.htmlIdentity,
        receipt.identity
      )

      Then("every bound identity is present, distinct where roles differ, and receipt identity is not copied into the page")
      receipt.schema shouldBe "cozy.logical-ui-review.v1"
      receipt.version shouldBe 1
      receipt.rendererProfile shouldBe "cozy.logical-ui-review.renderer.v1"
      identities.forall(value => value.startsWith("sha256:")) shouldBe true
      identities.distinct should have size identities.size
      reviewvalue.html should not include receipt.identity
      reviewvalue.html should not include "receiptIdentity"
    }

    "keep delimiter-bearing UseCase and catalog identities injective and order-stable" in {
      Given("valid UseCase IDs containing delimiters, quotes, and control characters")
      val usecases = _use_cases_with_ids(
        "business:|>[],\"\n\t\u0001x",
        "system:|>[],\"\n\t\u0002x",
        "ui:|>[],\"\n\t\u0003x"
      )
      val changedusecases = _use_cases_with_ids(
        "business:|>[],\"\n\t\u0001y",
        "system:|>[],\"\n\t\u0002x",
        "ui:|>[],\"\n\t\u0003x"
      )
      val first = _fixture_for(usecases, reversed = false, Some(WorkflowStateReference("sales-order-workflow", "awaiting")))
      val permuted = _fixture_for(usecases, reversed = true, Some(WorkflowStateReference("sales-order-workflow", "awaiting")))
      val changed = _fixture_for(changedusecases, reversed = false, Some(WorkflowStateReference("sales-order-workflow", "awaiting")))

      When("the same normalized tuple and a minimally changed logical tuple are rendered")
      val firstreview = _right(CozyLogicalUiReview.render(first._1, first._2, first._3))
      val permutedreview = _right(CozyLogicalUiReview.render(permuted._1, permuted._2, permuted._3))
      val changedreview = _right(CozyLogicalUiReview.render(changed._1, changed._2, changed._3))

      Then("canonical JSON framing keeps equivalent permutations equal and distinct tuples distinct")
      firstreview.html shouldBe permutedreview.html
      firstreview.receipt.useCaseIdentity shouldBe permutedreview.receipt.useCaseIdentity
      firstreview.receipt.catalogIdentity shouldBe permutedreview.receipt.catalogIdentity
      firstreview.receipt.useCaseIdentity should not equal changedreview.receipt.useCaseIdentity
      firstreview.receipt.catalogIdentity should not equal changedreview.receipt.catalogIdentity
    }

    "show absent Workflow state without collapsing domain and UI state evidence" in {
      Given("a valid semantic transition admission without an optional Workflow state")
      val fixture = _fixture_without_workflow_state()

      When("the review HTML renders the transition admission evidence")
      val reviewvalue = _right(CozyLogicalUiReview.render(fixture._1, fixture._2, fixture._3))

      Then("the Workflow state is explicitly missing while domain and UI states remain separate")
      reviewvalue.html should include("<th>Workflow state</th>")
      reviewvalue.html should include("<td>missing</td>")
      reviewvalue.html should include("draft -&gt; confirmed")
      reviewvalue.html should include("succeeded")
    }

    "identify a Workflow subject through its exact public binding without workflow execution behavior" in {
      Given("an accepted review tuple whose mapped Logical Screen has a public Workflow subject")
      val fixture = _workflow_subject_fixture()

      When("the deterministic read-only review HTML is rendered")
      val reviewvalue = _right(CozyLogicalUiReview.render(fixture._1, fixture._2, fixture._3))

      Then("the page identifies the Workflow pattern source but does not acquire a Workflow runner, route, or executable request surface")
      fixture._2.input.screens.find(_.id == "order-detail").get.subject shouldBe ScreenSubject(WorkflowRole, _workflow_binding)
      fixture._3.input.componentBindings.find(_.role == WorkflowRole).get.binding shouldBe _workflow_binding
      reviewvalue.html should include("Workflow: <code>org.example.sales.SalesOrder@1.0.0#workflow</code>")
      reviewvalue.html should include("<td>Workflow</td><td><code>org.example.sales.SalesOrder@1.0.0#workflow</code>")
      reviewvalue.html should not include "Workflow execution"
      reviewvalue.html should not include "workflow runner"
      reviewvalue.html should not include "data-workflow"
      reviewvalue.html should not include "<form"
    }

    "fail closed for identity, currentness, and output-parent traversal changes" in {
      Given("a current review, a direct output parent, and explicit-parent traversal and intermediate-link escape fixtures")
      val fixture = _fixture()
      val parent = Files.createTempDirectory("cozy-logical-ui-review-")
      val target = parent.resolve("review.html")
      Files.write(target, "preserve-me".getBytes(StandardCharsets.UTF_8))
      val outer = Files.createTempDirectory(Files.createDirectories(Paths.get("target")), "cozy-logical-ui-review-")
      val escapedparent = Files.createDirectory(outer.resolve("parent"))
      val external = Files.createDirectory(outer.resolve("external"))
      val link = Files.createSymbolicLink(escapedparent.resolve("link"), external)
      val escapesentinel = outer.resolve("escaped.html")
      Files.write(escapesentinel, "preserve-escape".getBytes(StandardCharsets.UTF_8))
      val traversingparent = link.resolve("..")
      val traversingtarget = traversingparent.resolve("escaped.html")
      val intermediateexternal = Files.createDirectory(external.resolve("nested"))
      val intermediatesentinel = intermediateexternal.resolve("intermediate.html")
      Files.write(intermediatesentinel, "preserve-intermediate".getBytes(StandardCharsets.UTF_8))
      val intermediateparent = link.resolve("nested")
      val intermediatetarget = intermediateparent.resolve("intermediate.html")

      When("the tuple, receipt, HTML, direct target, or explicit output parent contract no longer matches")
      val reviewvalue = _right(CozyLogicalUiReview.render(fixture._1, fixture._2, fixture._3))
      val changedsemantic = _right(CozyLogicalUiSemantics.project(fixture._2, fixture._3.input.copy(
        screens = fixture._3.input.screens.map(value => value.copy(purpose = BrowsePurpose))
      )))
      val staleinput = CozyLogicalUiReview.verifyCurrent(fixture._1, fixture._2, changedsemantic, reviewvalue)
      val stalereceipt = CozyLogicalUiReview.verifyCurrent(fixture._1, fixture._2, fixture._3, reviewvalue.copy(receipt = reviewvalue.receipt.copy(identity = "sha256:" + "0" * 64)))
      val stalehtml = CozyLogicalUiReview.verifyCurrent(fixture._1, fixture._2, fixture._3, reviewvalue.copy(html = "changed"))
      val invalidtarget = CozyLogicalUiReview.write(reviewvalue, parent, parent.resolve("review.txt"))
      val invalidtraversingparent = CozyLogicalUiReview.write(reviewvalue, traversingparent, traversingtarget)
      val invalidintermediateparent = CozyLogicalUiReview.write(reviewvalue, intermediateparent, intermediatetarget)
      val differentprojection = CozyLogicalUiReview.render(fixture._1, _different_projection(), fixture._3)

      Then("all stale or invalid operations return stable diagnostics and preserve direct and externally located prior bytes")
      _left(staleinput).code shouldBe "LUI43_REVIEW_CURRENTNESS_INVALID"
      _left(stalereceipt).code shouldBe "LUI43_REVIEW_CURRENTNESS_INVALID"
      _left(stalehtml).code shouldBe "LUI43_REVIEW_CURRENTNESS_INVALID"
      _left(invalidtarget).code shouldBe "LUI43_REVIEW_OUTPUT_INVALID"
      _left(invalidtraversingparent).code shouldBe "LUI43_REVIEW_OUTPUT_INVALID"
      _left(invalidintermediateparent).code shouldBe "LUI43_REVIEW_OUTPUT_INVALID"
      Files.readString(target, StandardCharsets.UTF_8) shouldBe "preserve-me"
      Files.readString(escapesentinel, StandardCharsets.UTF_8) shouldBe "preserve-escape"
      Files.readString(intermediatesentinel, StandardCharsets.UTF_8) shouldBe "preserve-intermediate"
      _left(differentprojection).code shouldBe "LUI43_REVIEW_IDENTITY_MISMATCH"
    }

    "write current HTML atomically and preserve diagnostics without semantic side effects" in {
      Given("a current review and an existing direct regular output parent")
      val fixture = _fixture()
      val parent = Files.createTempDirectory("cozy-logical-ui-review-write-")
      val target = parent.resolve("review.html")

      When("the validated review is written to a direct HTML target")
      val reviewvalue = _right(CozyLogicalUiReview.render(fixture._1, fixture._2, fixture._3))
      val result = CozyLogicalUiReview.write(reviewvalue, parent, target)

      Then("only the exact UTF-8 HTML is installed and no receipt file is created")
      result shouldBe Right(())
      Files.readString(target, StandardCharsets.UTF_8) shouldBe reviewvalue.html
      Files.exists(parent.resolve("review.receipt")) shouldBe false
    }
  }

  private def _fixture(reversed: Boolean = false): (AcceptedLogicalUi, LogicalUiProjection, LogicalUiSemanticProjection) =
    _fixture_for(_use_cases, reversed, Some(WorkflowStateReference("sales-order-workflow", "awaiting")))

  private def _fixture_without_workflow_state(): (AcceptedLogicalUi, LogicalUiProjection, LogicalUiSemanticProjection) =
    _fixture_for(_use_cases, reversed = false, workflowstate = None)

  private def _workflow_subject_fixture(): (AcceptedLogicalUi, LogicalUiProjection, LogicalUiSemanticProjection) = {
    val candidate = _right(CozyLogicalUi.candidate(_workflow_candidate_input))
    val projectioninput = _projection(candidate, reversed = false)
    val projection = _right(CozyLogicalUi.project(candidate, projectioninput.copy(screens = projectioninput.screens.map(screen =>
      if (screen.id == "order-detail") screen.copy(
        subject = ScreenSubject(WorkflowRole, _workflow_binding),
        interactions = screen.interactions.map(interaction =>
          if (interaction.id == "observe") interaction.copy(componentUsages = interaction.componentUsages :+ ComponentUsage(AggregateRole, _aggregate_binding)) else interaction
        )
      ) else screen
    ))))
    val semanticsinput = _semantics(projection, reversed = false, Some(WorkflowStateReference("sales-order-workflow", "awaiting")))
    val semantics = _right(CozyLogicalUiSemantics.project(projection, semanticsinput.copy(componentBindings =
      semanticsinput.componentBindings :+ ComponentSemanticBinding(WorkflowRole, _workflow_binding)
    )))
    val accepted = _right(CozyLogicalUi.accept(candidate, AcceptanceDecision("accepted-workflow-sales-order", candidate.identity)))
    (accepted, projection, semantics)
  }

  private def _fixture_for(
    usecases: UseCaseLayers,
    reversed: Boolean,
    workflowstate: Option[WorkflowStateReference]
  ): (AcceptedLogicalUi, LogicalUiProjection, LogicalUiSemanticProjection) = {
    val candidate = _right(CozyLogicalUi.candidate(_candidate_input.copy(useCases = usecases)))
    val projection = _right(CozyLogicalUi.project(candidate, _projection(candidate, reversed)))
    val semantics = _right(CozyLogicalUiSemantics.project(projection, _semantics(projection, reversed, workflowstate)))
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
      Vector(_list_screen, _detail_screen, _orphan_screen),
      Vector(
        ScreenInteractionMapping(uiusecase, "confirm", "order-list", "entry"),
        ScreenInteractionMapping(uiusecase, "confirm", "order-list", "to-detail"),
        ScreenInteractionMapping(uiusecase, "confirm", "order-detail", "select"),
        ScreenInteractionMapping(uiusecase, "confirm", "order-detail", "invoke"),
        ScreenInteractionMapping(uiusecase, "confirm", "order-detail", "observe"),
        ScreenInteractionMapping(uiusecase, "enter", "order-detail", "input"),
        ScreenInteractionMapping(uiusecase, "failure", "order-detail", "feedback"),
        ScreenInteractionMapping(uiusecase, "confirm", "orphan", "orphan-observe")
      ),
      Vector(AggregateBoundary(_aggregate_binding, _aggregate_binding, Vector(_aggregate_binding, _entity_binding), Vector(_operation_binding)))
    )
    if (reversed) values.copy(steps = values.steps.reverse, screens = values.screens.reverse, mappings = values.mappings.reverse) else values
  }

  private def _semantics(
    projection: LogicalUiProjection,
    reversed: Boolean,
    workflowstate: Option[WorkflowStateReference]
  ): LogicalUiSemanticsInput = {
    val values = LogicalUiSemanticsInput(
      projection.identity,
      Vector(
        ScreenSemantics("order-list", BrowsePurpose, Vector(RegionDisplayBinding("list-root", CollectionDisplay)), Vector(
          InteractionPatternBinding("entry", ObservePattern), InteractionPatternBinding("to-detail", NavigatePattern)
        ), Vector(InteractionStateBinding("entry", IdleInteractionState), InteractionStateBinding("to-detail", IdleInteractionState))),
        ScreenSemantics("order-detail", ConfirmPurpose, Vector(RegionDisplayBinding("detail-body", DetailDisplay), RegionDisplayBinding("detail-root", DetailDisplay)), Vector(
          InteractionPatternBinding("input", InputPattern), InteractionPatternBinding("select", SelectPattern), InteractionPatternBinding("invoke", CommandPattern),
          InteractionPatternBinding("observe", ObservePattern), InteractionPatternBinding("feedback", ObservePattern)
        ), Vector(InteractionStateBinding("input", PendingInteractionState), InteractionStateBinding("select", IdleInteractionState), InteractionStateBinding("invoke", SucceededInteractionState), InteractionStateBinding("observe", IdleInteractionState), InteractionStateBinding("feedback", FailedInteractionState))),
        ScreenSemantics("orphan", InspectPurpose, Vector(RegionDisplayBinding("orphan-root", DetailDisplay)), Vector(InteractionPatternBinding("orphan-observe", ObservePattern)), Vector(InteractionStateBinding("orphan-observe", IdleInteractionState)))
      ),
      Vector(
        ComponentSemanticBinding(EntityRole, _entity_binding), ComponentSemanticBinding(AggregateRole, _aggregate_binding), ComponentSemanticBinding(ServiceRole, _service_binding), ComponentSemanticBinding(OperationRole, _operation_binding),
        ComponentSemanticBinding(ValueRole, _value_binding, Some(ExactlyOneMultiplicity)), ComponentSemanticBinding(DatatypeRole, _datatype_binding, Some(ZeroOrMoreMultiplicity)), ComponentSemanticBinding(ViewRole, _view_binding),
        ComponentSemanticBinding(PowertypeRole, _powertype_binding, powertypeVariantId = Some("awaiting-confirmation")), ComponentSemanticBinding(StateMachineRole, _state_machine_binding, domainStates = Vector(DomainStateReference(_state_machine_binding, "draft"), DomainStateReference(_state_machine_binding, "confirmed")))
      ),
      Vector(
        ConstraintDeclaration(ConstraintIdentity("sales-order-code", "DATATYPE_FORMAT"), DatatypeConstraint, _datatype_binding, LocalDeterministicValidation),
        ConstraintDeclaration(ConstraintIdentity("confirm-order", "VALUE_LOCAL"), ValueConstraint, _value_binding, ContextDependentValidation),
        ConstraintDeclaration(ConstraintIdentity("sales-order", "AGGREGATE_INVARIANT"), AggregateInvariant, _aggregate_binding, ServerAuthoritativeValidation),
        ConstraintDeclaration(ConstraintIdentity("confirm-order", "SALES_ORDER_PRE"), OperationPrecondition, _operation_binding, LocalDeterministicValidation),
        ConstraintDeclaration(ConstraintIdentity("confirm-order", "SALES_ORDER_POST"), OperationPostcondition, _operation_binding, ServerAuthoritativeValidation)
      ),
      Vector(FeedbackConstraintAssociation("validation-failed", ConstraintIdentity("confirm-order", "VALUE_LOCAL")), FeedbackConstraintAssociation("conflict", ConstraintIdentity("confirm-order", "SALES_ORDER_PRE"))),
      Vector(StateMachineTransitionAction(UiUseCaseStep(projection.input.catalog.head.ui, "confirm", NormalPath), "order-detail", "invoke", _operation_binding, _state_machine_binding, DomainStateReference(_state_machine_binding, "draft"), DomainStateReference(_state_machine_binding, "confirmed"), workflowstate, SucceededInteractionState))
    )
    if (reversed) values.copy(screens = values.screens.reverse, componentBindings = values.componentBindings.reverse, constraints = values.constraints.reverse, feedbackAssociations = values.feedbackAssociations.reverse, transitionActions = values.transitionActions.reverse) else values
  }

  private val _sales_order = ComponentCoordinate("org.example.sales", "SalesOrder", "1.0.0")
  private val _business_use_case = UseCaseReference(Business, "business-confirm-sales-order")
  private val _system_use_case = UseCaseReference(System, "system-confirm-sales-order")
  private val _ui_use_case = UseCaseReference(Ui, "ui-confirm-sales-order")
  private val _use_cases = UseCaseLayers(_business_use_case, _system_use_case, _ui_use_case, Vector(UseCaseRealization(_business_use_case, _system_use_case), UseCaseRealization(_system_use_case, _ui_use_case)))
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
  private val _all_bindings = Vector(_entity_binding, _aggregate_binding, _service_binding, _operation_binding, _value_binding, _datatype_binding, _view_binding, _powertype_binding, _state_machine_binding)
  private val _candidate_input = CandidateInput(Vector(ComponentSurface(_sales_order, _all_bindings.map(_.exportId))), _all_bindings, _use_cases)
  private val _workflow_candidate_input = CandidateInput(
    Vector(ComponentSurface(_sales_order, _all_bindings.map(_.exportId) :+ _workflow_binding.exportId)),
    _all_bindings :+ _workflow_binding,
    _use_cases
  )

  private val _list_screen = LogicalScreen("order-list", "browse", Vector("search", "sort"), ScreenSubject(ViewRole, _view_binding), Vector(SemanticRegion("list-root", None, 0)), Vector(
    ScreenInteraction("entry", EntryInteraction, Vector(ComponentUsage(EntityRole, _entity_binding)), None, None), ScreenInteraction("to-detail", NavigationInteraction, Vector.empty, None, Some(NavigationEndpoint("to-detail", "order-detail")))
  ), Vector(NormalFeedback))
  private val _detail_screen = LogicalScreen("order-detail", "confirm", Vector.empty, ScreenSubject(AggregateRole, _aggregate_binding), Vector(SemanticRegion("detail-root", None, 0), SemanticRegion("detail-body", Some("detail-root"), 1)), Vector(
    ScreenInteraction("input", InputInteraction, Vector(ComponentUsage(ValueRole, _value_binding), ComponentUsage(DatatypeRole, _datatype_binding)), None, None), ScreenInteraction("select", SelectionInteraction, Vector(ComponentUsage(PowertypeRole, _powertype_binding)), None, None), ScreenInteraction("invoke", InvocationInteraction, Vector(ComponentUsage(ServiceRole, _service_binding), ComponentUsage(OperationRole, _operation_binding), ComponentUsage(StateMachineRole, _state_machine_binding)), Some(MutationAction(_aggregate_binding, _operation_binding)), None), ScreenInteraction("observe", ObservationInteraction, Vector(ComponentUsage(StateMachineRole, _state_machine_binding)), None, None), ScreenInteraction("feedback", FeedbackInteraction, Vector.empty, None, None)
  ), Vector(NormalFeedback, ValidationFailedFeedback, ConflictFeedback, OperationFailedFeedback))
  private val _orphan_screen = LogicalScreen("orphan", "inspect", Vector.empty, ScreenSubject(ViewRole, _view_binding), Vector(SemanticRegion("orphan-root", None, 0)), Vector(ScreenInteraction("orphan-observe", ObservationInteraction, Vector(ComponentUsage(StateMachineRole, _state_machine_binding)), None, None)), Vector(NormalFeedback))

  private def _use_cases_with_ids(businessid: String, systemid: String, uiid: String): UseCaseLayers = {
    val business = UseCaseReference(Business, businessid)
    val system = UseCaseReference(System, systemid)
    val ui = UseCaseReference(Ui, uiid)
    UseCaseLayers(business, system, ui, Vector(UseCaseRealization(business, system), UseCaseRealization(system, ui)))
  }

  private def _different_projection(): LogicalUiProjection = {
    val otherui = UseCaseReference(Ui, "ui-other")
    val otherusecases = _use_cases.copy(ui = otherui, realizations = Vector(UseCaseRealization(_business_use_case, _system_use_case), UseCaseRealization(_system_use_case, otherui)))
    val candidate = _right(CozyLogicalUi.candidate(_candidate_input.copy(useCases = otherusecases)))
    _right(CozyLogicalUi.project(candidate, _projection(candidate, reversed = false)))
  }

  private def _right[A](value: Either[LogicalUiError, A]): A = value match {
    case Right(resultvalue) => resultvalue
    case Left(error) => fail(s"Expected a Logical UI review result but received ${error.code}: ${error.reason}")
  }

  private def _left[A](value: Either[LogicalUiError, A]): LogicalUiError = value match {
    case Left(error) => error
    case Right(_) => fail("Expected a Logical UI review failure")
  }
}
