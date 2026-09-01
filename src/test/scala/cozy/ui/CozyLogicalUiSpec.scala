package cozy.ui

import cozy.ui.CozyLogicalUi._
import org.scalacheck.{Gen, Prop, Test}
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Sep. 1, 2026
 * @version Sep. 2, 2026
 * @author  ASAMI, Tomoharu
 */
final class CozyLogicalUiSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  "Cozy Logical UI v1 candidate admission" should {
    "admit only canonical Component coordinates and explicit exports" in {
      Given("one identity-pinned public Component surface and its declared export")
      val candidate = _right(CozyLogicalUi.candidate(_valid_input))

      When("the closed Logical UI candidate is constructed")
      val surfaces = candidate.input.componentSurfaces.filter(_.component == _sales_order)
      val bindings = candidate.input.componentBindings.filter(_.component == _sales_order)

      Then("the direct binding retains namespace, id, version, qualified name, and opaque export without source discovery")
      surfaces should have size 1
      bindings should have size 1
      val surface = surfaces.head
      val binding = bindings.head
      surface.component.namespace shouldBe "org.example.sales"
      surface.component.id shouldBe "SalesOrder"
      surface.component.version shouldBe "1.0.0"
      surface.component.qualifiedName shouldBe "org.example.sales.SalesOrder"
      binding.exportId shouldBe "order-summary"
      candidate.canonicalContent should include ("org.example.sales")
    }

    "admit distinct bindings when opaque versions and exports contain delimiter-shaped text" in {
      Given("two valid public surfaces whose version and export text would collide under a delimiter key")
      val firstcomponent = ComponentCoordinate("org.example.collision", "Thing", "v#x")
      val secondcomponent = ComponentCoordinate("org.example.collision", "Thing", "v")
      val input = _valid_input.copy(
        componentSurfaces = Vector(
          ComponentSurface(firstcomponent, Vector("e1")),
          ComponentSurface(secondcomponent, Vector("x#e1"))
        ),
        componentBindings = Vector(
          ComponentBinding(firstcomponent, "e1"),
          ComponentBinding(secondcomponent, "x#e1")
        )
      )

      When("candidate construction normalizes and checks the exact binding identities")
      val candidate = _right(CozyLogicalUi.candidate(input))

      Then("both coordinate and opaque-export pairs remain admitted as distinct bindings")
      candidate.input.componentBindings should have size 2
      candidate.input.componentBindings.toSet shouldBe Set(
        ComponentBinding(firstcomponent, "e1"),
        ComponentBinding(secondcomponent, "x#e1")
      )
    }

    "reject unexported elements and malformed Component coordinates" in {
      Given("a public surface with one closed export and an invalid coordinate fixture")
      val unexported = _valid_input.copy(componentBindings = Vector(
        ComponentBinding(_sales_order, "private-order-state")
      ))
      val malformed = _valid_input.copy(componentSurfaces = Vector(
        ComponentSurface(ComponentCoordinate("org..example", "SalesOrder", "1.0.0"), Vector("order-summary"))
      ))

      When("a candidate attempts local-name admission or a noncanonical coordinate")
      val unexportedresult = CozyLogicalUi.candidate(unexported)
      val malformedresult = CozyLogicalUi.candidate(malformed)

      Then("both values fail closed instead of inspecting Component source or inferring an export")
      _left(unexportedresult).code shouldBe "LUI43_COMPONENT_EXPORT_CLOSED"
      _left(malformedresult).code shouldBe "LUI43_COMPONENT_COORDINATE_INVALID"
    }
  }

  "Cozy Logical UI v1 UseCase authority" should {
    "keep Business, System, and UI identities distinct and joined only by explicit realization" in {
      Given("a wrong-layer realization and a candidate without the required System identity")
      val wrongrealization = _valid_input.copy(useCases = _valid_use_cases.copy(realizations = Vector(
        UseCaseRealization(_business_use_case, _ui_use_case),
        UseCaseRealization(_system_use_case, _ui_use_case)
      )))
      val missingidentity = _valid_input.copy(useCases = _valid_use_cases.copy(
        system = UseCaseReference(System, "")
      ))

      When("the candidate constructor validates the three-layer realization boundary")
      val wrongresult = CozyLogicalUi.candidate(wrongrealization)
      val missingresult = CozyLogicalUi.candidate(missingidentity)

      Then("only Business -> System -> UI with three nonempty distinct identities is admitted")
      _left(wrongresult).code shouldBe "LUI43_USE_CASE_REALIZATION_INVALID"
      _left(missingresult).code shouldBe "LUI43_INPUT_TEXT_INVALID"
    }
  }

  "Cozy Logical UI v1 acceptance" should {
    "separate candidate, consumed-input, feedback-decision, and accepted identities" in {
      Given("one constructed candidate and an explicit decision bound to that exact candidate")
      val candidate = _right(CozyLogicalUi.candidate(_valid_input))
      val decision = AcceptanceDecision("review-001", candidate.identity)
      val mismatched = AcceptanceDecision("review-002", "sha256:" + "0" * 64)

      When("acceptance is requested with the bound decision and with an unbound decision")
      val accepted = _right(CozyLogicalUi.accept(candidate, decision))
      val rejection = CozyLogicalUi.accept(candidate, mismatched)

      Then("only the bound decision creates accepted authority and every authority role has a distinct identity")
      accepted.candidate.identity shouldBe candidate.identity
      accepted.decision.decisionIdentity shouldBe decision.decisionIdentity
      Set(candidate.identity, candidate.inputIdentity, decision.decisionIdentity, accepted.identity).size shouldBe 4
      _left(rejection).code shouldBe "LUI43_ACCEPTANCE_CANDIDATE_MISMATCH"
    }
  }

  "Cozy Logical UI v1 codec" should {
    "preserve deterministic semantic identity across canonical ordering and a strict codec roundtrip" in {
      Given("equivalent ordered and reversed canonical inputs plus a ScalaCheck ordering generator")
      val reversed = _valid_input.copy(
        componentSurfaces = _valid_input.componentSurfaces.reverse.map(surface => surface.copy(exportIds = surface.exportIds.reverse)),
        componentBindings = _valid_input.componentBindings.reverse,
        useCases = _valid_use_cases.copy(realizations = _valid_use_cases.realizations.reverse)
      )
      val property = Prop.forAll(Gen.oneOf(true, false)) { reverse =>
        val input = if (reverse) reversed else _valid_input
        val first = CozyLogicalUi.candidate(input)
        val second = CozyLogicalUi.candidate(_valid_input)
        (first, second) match {
          case (Right(left), Right(right)) => left.identity == right.identity && left.inputIdentity == right.inputIdentity
          case _ => false
        }
      }

      When("the candidate is normalized, encoded, decoded, and challenged with unknown or mismatched identity fields")
      val candidate = _right(CozyLogicalUi.candidate(reversed))
      val encoded = CozyLogicalUiCodec.encodeCandidate(candidate)
      val decoded = CozyLogicalUiCodec.decodeCandidate(encoded)
      val unknown = CozyLogicalUiCodec.decodeCandidate(encoded.stripSuffix("}") + ",\"unknown\":true}")
      val mismatched = CozyLogicalUiCodec.decodeCandidate(encoded.replace(candidate.identity, "sha256:" + "f" * 64))
      val propertyresult = Test.check(Test.Parameters.default.withMinSuccessfulTests(20), property)

      Then("canonical bytes and SHA-256 identities remain stable while closed malformed documents are rejected")
      encoded shouldBe _right(decoded).canonicalJson
      _right(decoded).identity shouldBe candidate.identity
      propertyresult.passed shouldBe true
      _left(unknown).code shouldBe "LUI43_CODEC_SCHEMA_INVALID"
      _left(mismatched).code shouldBe "LUI43_CODEC_IDENTITY_MISMATCH"
    }
  }

  "Cozy Logical UI v1 non-authority boundary" should {
    "exclude raw prompts, codec bytes, and future HTML from accepted semantic authority" in {
      Given("one candidate plus raw prompt, codec bytes, and a future review HTML projection")
      val rawprompt = "make a dashboard from whatever private CML fields are available"
      val candidate = _right(CozyLogicalUi.candidate(_valid_input))
      val codecbytes = CozyLogicalUiCodec.encodeCandidate(candidate)
      val reviewhtml = "<main data-logical-ui=\"candidate\">review only</main>"
      val decision = AcceptanceDecision("review-003", candidate.identity)

      When("only the explicit candidate and bound acceptance decision are supplied to the authority constructor")
      val accepted = _right(CozyLogicalUi.accept(candidate, decision))

      Then("the accepted identity depends on the candidate and decision, never on prompts, serialized formatting, or review projection bytes")
      accepted.candidate.canonicalContent should not include rawprompt
      accepted.candidate.canonicalContent should not include codecbytes
      accepted.candidate.canonicalContent should not include reviewhtml
      accepted.identity should not be candidate.identity
      accepted.decision.candidateIdentity shouldBe candidate.identity
    }
  }

  "Cozy UseCase-to-Screen projection v1" should {
    "admit one-to-many, many-to-one, reused-screen, and system-only step mappings" in {
      Given("two opaque catalog UseCaseLayers values, four paths on one UI UseCase, and one reused Logical Screen")
      val candidate = _right(CozyLogicalUi.candidate(_projection_input))
      val projection = _projection(candidate)

      When("the typed projection is normalized against the exact candidate identity")
      val result = CozyLogicalUi.project(candidate, projection)

      Then("normal, alternative, and exception steps may map to one or many interactions while system-only remains unmapped")
      val normalized = _right(result)
      normalized.input.catalog should have size 2
      normalized.input.steps.count(_.path == SystemOnlyPath) shouldBe 1
      normalized.input.mappings.count(_.stepId == "alternative-path") shouldBe 2
      normalized.input.mappings.count(_.screenId == "order-detail") should be >= 1
      normalized.identity should startWith("sha256:")
    }

    "normalize delimiter-shaped UseCase, step, and mapping identities without collision" in {
      Given("two catalog UseCaseLayers whose opaque UI and step IDs collide under delimiter-concatenated keys")
      val candidate = _right(CozyLogicalUi.candidate(_projection_input))
      val firstbusiness = UseCaseReference(Business, "business-delimiter")
      val firstsystem = UseCaseReference(System, "system-delimiter")
      val firstui = UseCaseReference(Ui, "a:b")
      val firstusecases = UseCaseLayers(
        firstbusiness,
        firstsystem,
        firstui,
        Vector(
          UseCaseRealization(firstbusiness, firstsystem),
          UseCaseRealization(firstsystem, firstui)
        )
      )
      val secondbusiness = UseCaseReference(Business, "business-other-delimiter")
      val secondsystem = UseCaseReference(System, "system-other-delimiter")
      val secondui = UseCaseReference(Ui, "a")
      val secondusecases = UseCaseLayers(
        secondbusiness,
        secondsystem,
        secondui,
        Vector(
          UseCaseRealization(secondbusiness, secondsystem),
          UseCaseRealization(secondsystem, secondui)
        )
      )
      val projection = _projection(candidate).copy(
        catalog = _projection(candidate).catalog ++ Vector(firstusecases, secondusecases),
        steps = _projection_steps ++ Vector(
          UiUseCaseStep(firstui, "c", NormalPath),
          UiUseCaseStep(secondui, "b:c", NormalPath)
        ),
        mappings = _projection_mappings ++ Vector(
          ScreenInteractionMapping(firstui, "c", "order-detail", "entry"),
          ScreenInteractionMapping(secondui, "b:c", "order-detail", "entry")
        )
      )

      When("the projection is normalized with structurally typed identity keys")
      val result = CozyLogicalUi.project(candidate, projection)

      Then("distinct opaque identities remain admitted with deterministic normalized ordering")
      val normalized = _right(result)
      normalized.input.steps should contain allElementsOf Vector(
        UiUseCaseStep(firstui, "c", NormalPath),
        UiUseCaseStep(secondui, "b:c", NormalPath)
      )
      normalized.input.mappings should contain allElementsOf Vector(
        ScreenInteractionMapping(firstui, "c", "order-detail", "entry"),
        ScreenInteractionMapping(secondui, "b:c", "order-detail", "entry")
      )
      normalized.identity should startWith("sha256:")
    }

    "preserve purpose, subject, ordered regions, all Component roles, interactions, feedback, and navigation" in {
      Given("one Logical Screen with an Aggregate subject, a semantic region tree, all closed interaction kinds, and all feedback states")
      val candidate = _right(CozyLogicalUi.candidate(_projection_input))
      val screen = _projection(candidate).screens.head

      When("the screen is admitted through UseCase-to-Screen projection")
      val projected = _right(CozyLogicalUi.project(candidate, _projection(candidate)))
      val normalized = projected.input.screens.find(_.id == screen.id).get

      Then("the typed screen retains its semantic surface through exact public bindings without source discovery")
      normalized.primaryPurpose shouldBe "inspect"
      normalized.subject.role shouldBe AggregateRole
      normalized.regions.map(_.id) shouldBe Vector("body", "root")
      normalized.regions.find(_.id == "body").get.order shouldBe 1
      normalized.interactions.map(_.kind).toSet shouldBe Set(
        EntryInteraction,
        InputInteraction,
        QueryInteraction,
        SelectionInteraction,
        InvocationInteraction,
        ObservationInteraction,
        FeedbackInteraction,
        NavigationInteraction
      )
      normalized.interactions.flatMap(_.componentUsages.map(_.role)).toSet should contain allElementsOf Set(
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
      normalized.feedbackStates.toSet shouldBe Set(
        NormalFeedback,
        LoadingFeedback,
        EmptyFeedback,
        UnavailableFeedback,
        ValidationFailedFeedback,
        ConflictFeedback,
        OperationFailedFeedback
      )
      normalized.interactions.find(_.kind == NavigationInteraction).get.navigation.get.targetScreenId shouldBe "order-detail"
    }

    "fail closed for unknown bindings, unused candidates, missing coverage, system mappings, and unjustified screens" in {
      Given("a valid projection plus independently malformed binding, candidate, coverage, system-step, and screen variants")
      val candidate = _right(CozyLogicalUi.candidate(_projection_input))
      val valid = _projection(candidate)
      val unknown = valid.copy(screens = valid.screens.map(screen => screen.copy(subject = ScreenSubject(AggregateRole, _unknown_binding))))
      val unusedcandidate = _right(CozyLogicalUi.candidate(_projection_input.copy(
        componentSurfaces = _projection_input.componentSurfaces.map(surface => surface.copy(exportIds = surface.exportIds :+ "unused-binding")),
        componentBindings = _projection_input.componentBindings :+ ComponentBinding(_sales_order, "unused-binding")
      )))
      val missingcoverage = valid.copy(mappings = valid.mappings.filterNot(_.stepId == "normal-path"))
      val systemmapping = valid.copy(mappings = valid.mappings :+ ScreenInteractionMapping(_ui_use_case, "system-path", "order-detail", "observe"))
      val unusedscreen = valid.copy(screens = valid.screens :+ _unused_screen)

      When("each variant is submitted to the typed projection boundary")
      val unknownresult = CozyLogicalUi.project(candidate, unknown)
      val unusedresult = CozyLogicalUi.project(unusedcandidate, valid.copy(candidateIdentity = unusedcandidate.identity))
      val missingresult = CozyLogicalUi.project(candidate, missingcoverage)
      val systemresult = CozyLogicalUi.project(candidate, systemmapping)
      val screenresult = CozyLogicalUi.project(candidate, unusedscreen)

      Then("the projection reports stable closure, justification, and coverage diagnostics")
      _left(unknownresult).code shouldBe "LUI43_PROJECTION_COMPONENT_CLOSED"
      _left(unusedresult).code shouldBe "LUI43_PROJECTION_COMPONENT_UNJUSTIFIED"
      _left(missingresult).code shouldBe "LUI43_PROJECTION_STEP_COVERAGE"
      _left(systemresult).code shouldBe "LUI43_PROJECTION_SYSTEM_STEP_MAPPING"
      _left(screenresult).code shouldBe "LUI43_PROJECTION_SCREEN_UNJUSTIFIED"
    }

    "fail closed for malformed regions and navigation and for an invocation without an Operation role" in {
      Given("a valid projection with a malformed region tree, an unknown navigation target, and a mutation whose Operation usage is removed")
      val candidate = _right(CozyLogicalUi.candidate(_projection_input))
      val valid = _projection(candidate)
      val malformedregion = valid.copy(screens = valid.screens.map(screen => screen.copy(
        regions = screen.regions.map(region => if (region.id == "body") region.copy(parentId = Some("missing")) else region)
      )))
      val malformednavigation = valid.copy(screens = valid.screens.map(screen => screen.copy(
        interactions = screen.interactions.map(interaction => if (interaction.kind == NavigationInteraction) interaction.copy(navigation = Some(NavigationEndpoint("order", "missing-screen"))) else interaction)
      )))
      val missingoperationrole = valid.copy(screens = valid.screens.map(screen => screen.copy(
        interactions = screen.interactions.map(interaction => if (interaction.kind == InvocationInteraction) interaction.copy(componentUsages = interaction.componentUsages.filterNot(_.role == OperationRole)) else interaction)
      )))

      When("the projection validates each malformed semantic surface")
      val regionresult = CozyLogicalUi.project(candidate, malformedregion)
      val navigationresult = CozyLogicalUi.project(candidate, malformednavigation)
      val operationresult = CozyLogicalUi.project(candidate, missingoperationrole)

      Then("malformed trees, endpoints, and mutation bindings fail closed with precise diagnostics")
      _left(regionresult).code shouldBe "LUI43_PROJECTION_REGION_INVALID"
      _left(navigationresult).code shouldBe "LUI43_PROJECTION_NAVIGATION_INVALID"
      _left(operationresult).code shouldBe "LUI43_PROJECTION_OPERATION_MISSING"
    }

    "reject direct child mutation and admit root mutation through its declared public Operation" in {
      Given("an Aggregate boundary with a root, a direct child, and one public Operation")
      val candidate = _right(CozyLogicalUi.candidate(_projection_input))
      val valid = _projection(candidate)
      val admitted = CozyLogicalUi.project(candidate, valid)
      val childmutation = valid.copy(screens = valid.screens.map(screen => screen.copy(
        interactions = screen.interactions.map(interaction => if (interaction.kind == InvocationInteraction) interaction.copy(mutation = Some(MutationAction(_entity_binding, _operation_binding))) else interaction)
      )))

      When("the same invocation targets the child, then the Aggregate root")
      val rejected = CozyLogicalUi.project(candidate, childmutation)

      Then("the direct child is rejected even when a public Operation exists and the exact root mutation is admitted")
      _right(admitted).input.aggregateBoundaries.head.publicOperations should contain (_operation_binding)
      _left(rejected).code shouldBe "LUI43_PROJECTION_AGGREGATE_MUTATION_BYPASS"
    }

    "reject a mutation target that belongs to no declared Aggregate boundary" in {
      Given("a valid projection whose invocation targets an admitted Component binding outside its Aggregate boundary")
      val candidate = _right(CozyLogicalUi.candidate(_projection_input))
      val valid = _projection(candidate)
      val unownedtarget = valid.copy(screens = valid.screens.map(screen => screen.copy(
        interactions = screen.interactions.map(interaction => if (interaction.kind == InvocationInteraction)
          interaction.copy(mutation = Some(MutationAction(_view_binding, _operation_binding)))
        else interaction)
      )))

      When("the projection validates the invocation mutation target")
      val result = CozyLogicalUi.project(candidate, unownedtarget)

      Then("the mutation fails closed before an Aggregate public Operation can authorize it")
      _left(result).code shouldBe "LUI43_PROJECTION_AGGREGATE_MUTATION_BYPASS"
    }

    "reject a public Operation binding reused by a second Aggregate boundary" in {
      Given("a valid projection plus a second Aggregate boundary that declares the first boundary's public Operation")
      val candidate = _right(CozyLogicalUi.candidate(_projection_input))
      val valid = _projection(candidate)
      val reusedoperation = AggregateBoundary(
        _service_binding,
        _service_binding,
        Vector(_service_binding, _view_binding),
        Vector(_operation_binding)
      )
      val overlapping = valid.copy(aggregateBoundaries = valid.aggregateBoundaries :+ reusedoperation)

      When("the projection validates both Aggregate boundary ownership sets")
      val result = CozyLogicalUi.project(candidate, overlapping)

      Then("the reused public Operation fails closed as an overlapping Component binding")
      _left(result).code shouldBe "LUI43_PROJECTION_COMPONENT_CLOSED"
    }

    "reject an Aggregate boundary whose root is absent from its members" in {
      Given("a valid projection whose Aggregate boundary declares a root that is not one of its members")
      val candidate = _right(CozyLogicalUi.candidate(_projection_input))
      val valid = _projection(candidate)
      val missingroot = valid.copy(aggregateBoundaries = valid.aggregateBoundaries.map(boundary => boundary.copy(root = _service_binding)))

      When("the typed projection validates the Aggregate boundary")
      val result = CozyLogicalUi.project(candidate, missingroot)

      Then("the boundary fails closed before Aggregate mutation checks")
      _left(result).code shouldBe "LUI43_PROJECTION_COMPONENT_CLOSED"
    }

    "admit an Aggregate screen subject when interaction Aggregate usage is redundant" in {
      Given("a valid projection whose screen subject carries Aggregate while its observation interaction does not")
      val candidate = _right(CozyLogicalUi.candidate(_projection_input))
      val valid = _projection(candidate)
      val subjectonly = valid.copy(screens = valid.screens.map(screen => screen.copy(
        interactions = screen.interactions.map(interaction => if (interaction.kind == ObservationInteraction)
          interaction.copy(componentUsages = interaction.componentUsages.filterNot(_.role == AggregateRole))
        else interaction)
      )))

      When("the typed projection is normalized with the subject as the Aggregate role usage")
      val result = CozyLogicalUi.project(candidate, subjectonly)

      Then("the Aggregate boundary remains justified by the explicit screen subject")
      val normalized = _right(result)
      normalized.input.screens.head.subject.role shouldBe AggregateRole
      normalized.input.aggregateBoundaries should have size 1
    }

    "admit a public Workflow binding as a deterministic Logical Screen subject and reject closed Workflow bindings" in {
      Given("a Workflow public Component binding, a mapped Workflow screen, and unknown and unexported Workflow binding variants")
      val candidate = _right(CozyLogicalUi.candidate(_workflow_projection_input))
      val workflowprojection = _workflow_projection(candidate)
      val unexportedinput = _workflow_projection_input.copy(componentBindings = _workflow_projection_bindings :+ _unknown_binding)
      val unknownsubject = workflowprojection.copy(screens = workflowprojection.screens.map(screen =>
        screen.copy(subject = ScreenSubject(WorkflowRole, _unknown_binding))
      ))

      When("the Workflow subject is normalized and exact public Component admission is challenged")
      val normalized = _right(CozyLogicalUi.project(candidate, workflowprojection))
      val unexportedresult = CozyLogicalUi.candidate(unexportedinput)
      val unknownresult = CozyLogicalUi.project(candidate, unknownsubject)
      val permuted = _right(CozyLogicalUi.project(candidate, workflowprojection.copy(
        screens = workflowprojection.screens.reverse,
        mappings = workflowprojection.mappings.reverse
      )))

      Then("Workflow remains a mapped Logical Screen pattern source under one exact public binding and canonical identity")
      normalized.input.screens.head.subject shouldBe ScreenSubject(WorkflowRole, _workflow_binding)
      normalized.input.screens.head.subject.binding shouldBe _workflow_binding
      normalized.input.mappings should contain allElementsOf _projection_mappings
      normalized.identity shouldBe permuted.identity
      _left(unexportedresult).code shouldBe "LUI43_COMPONENT_EXPORT_CLOSED"
      _left(unknownresult).code shouldBe "LUI43_PROJECTION_COMPONENT_CLOSED"
    }

    "normalize catalog, step, screen, mapping, and boundary permutations to one projection identity" in {
      Given("a valid projection and a permutation generator over its canonical vectors")
      val candidate = _right(CozyLogicalUi.candidate(_projection_input))
      val projection = _projection(candidate)
      val property = Prop.forAll(Gen.oneOf(true, false)) { reverse =>
        val value = if (reverse) projection.copy(
          catalog = projection.catalog.reverse,
          steps = projection.steps.reverse,
          screens = projection.screens.reverse.map(screen => screen.copy(
            regions = screen.regions.reverse,
            interactions = screen.interactions.reverse.map(interaction => interaction.copy(componentUsages = interaction.componentUsages.reverse)),
            feedbackStates = screen.feedbackStates.reverse
          )),
          mappings = projection.mappings.reverse,
          aggregateBoundaries = projection.aggregateBoundaries.reverse.map(boundary => boundary.copy(
            members = boundary.members.reverse,
            publicOperations = boundary.publicOperations.reverse
          ))
        ) else projection
        CozyLogicalUi.project(candidate, value) match {
          case Right(result) => result.identity == _right(CozyLogicalUi.project(candidate, projection)).identity && result.canonicalContent == _right(CozyLogicalUi.project(candidate, projection)).canonicalContent
          case Left(_) => false
        }
      }

      When("the candidate and projection are normalized repeatedly")
      val result = Test.check(Test.Parameters.default.withMinSuccessfulTests(20), property)

      Then("equivalent ordering produces identical canonical content and projection identity")
      result.passed shouldBe true
    }
  }

  private val _sales_order = ComponentCoordinate("org.example.sales", "SalesOrder", "1.0.0")
  private val _customer = ComponentCoordinate("org.example.sales", "Customer", "1.0.0")
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
  private val _valid_input = CandidateInput(
    Vector(
      ComponentSurface(_sales_order, Vector("order-summary", "order-status")),
      ComponentSurface(_customer, Vector("customer-summary"))
    ),
    Vector(
      ComponentBinding(_sales_order, "order-summary"),
      ComponentBinding(_customer, "customer-summary")
    ),
    _valid_use_cases
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
  private val _unknown_binding = ComponentBinding(_sales_order, "unknown")
  private val _projection_bindings = Vector(
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
  private val _projection_input = CandidateInput(
    Vector(ComponentSurface(_sales_order, _projection_bindings.map(_.exportId))),
    _projection_bindings,
    _valid_use_cases
  )
  private val _workflow_projection_bindings = _projection_bindings :+ _workflow_binding
  private val _workflow_projection_input = CandidateInput(
    Vector(ComponentSurface(_sales_order, _workflow_projection_bindings.map(_.exportId))),
    _workflow_projection_bindings,
    _valid_use_cases
  )
  private val _other_business_use_case = UseCaseReference(Business, "business-review-sales-order")
  private val _other_system_use_case = UseCaseReference(System, "system-review-sales-order")
  private val _other_ui_use_case = UseCaseReference(Ui, "ui-review-sales-order")
  private val _other_use_cases = UseCaseLayers(
    _other_business_use_case,
    _other_system_use_case,
    _other_ui_use_case,
    Vector(
      UseCaseRealization(_other_business_use_case, _other_system_use_case),
      UseCaseRealization(_other_system_use_case, _other_ui_use_case)
    )
  )

  private val _projection_screen = LogicalScreen(
    "order-detail",
    "inspect",
    Vector("review"),
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
        ComponentUsage(OperationRole, _operation_binding)
      ), Some(MutationAction(_aggregate_binding, _operation_binding)), None),
      ScreenInteraction("observe", ObservationInteraction, Vector(ComponentUsage(AggregateRole, _aggregate_binding), ComponentUsage(StateMachineRole, _state_machine_binding)), None, None),
      ScreenInteraction("feedback", FeedbackInteraction, Vector.empty, None, None),
      ScreenInteraction("navigate", NavigationInteraction, Vector.empty, None, Some(NavigationEndpoint("order", "order-detail")))
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

  private val _projection_steps = Vector(
    UiUseCaseStep(_ui_use_case, "normal-path", NormalPath),
    UiUseCaseStep(_ui_use_case, "alternative-path", AlternativePath),
    UiUseCaseStep(_ui_use_case, "exception-path", ExceptionPath),
    UiUseCaseStep(_ui_use_case, "system-path", SystemOnlyPath),
    UiUseCaseStep(_other_ui_use_case, "other-path", NormalPath)
  )

  private val _projection_mappings = Vector(
    ScreenInteractionMapping(_ui_use_case, "normal-path", "order-detail", "entry"),
    ScreenInteractionMapping(_ui_use_case, "normal-path", "order-detail", "select"),
    ScreenInteractionMapping(_ui_use_case, "normal-path", "order-detail", "invoke"),
    ScreenInteractionMapping(_ui_use_case, "normal-path", "order-detail", "observe"),
    ScreenInteractionMapping(_ui_use_case, "normal-path", "order-detail", "navigate"),
    ScreenInteractionMapping(_ui_use_case, "alternative-path", "order-detail", "input"),
    ScreenInteractionMapping(_ui_use_case, "alternative-path", "order-detail", "query"),
    ScreenInteractionMapping(_ui_use_case, "exception-path", "order-detail", "feedback"),
    ScreenInteractionMapping(_other_ui_use_case, "other-path", "order-detail", "observe")
  )

  private val _projection_boundary = AggregateBoundary(
    _aggregate_binding,
    _aggregate_binding,
    Vector(_aggregate_binding, _entity_binding),
    Vector(_operation_binding)
  )

  private val _unused_screen = LogicalScreen(
    "unused-screen",
    "unused",
    Vector.empty,
    ScreenSubject(ViewRole, _view_binding),
    Vector(SemanticRegion("root", None, 0)),
    Vector(ScreenInteraction("unused", ObservationInteraction, Vector.empty, None, None)),
    Vector(NormalFeedback)
  )

  private def _projection(candidate: LogicalUiCandidate): UseCaseScreenProjection = UseCaseScreenProjection(
    candidate.identity,
    Vector(_other_use_cases, candidate.input.useCases),
    _projection_steps,
    Vector(_projection_screen),
    _projection_mappings,
    Vector(_projection_boundary)
  )

  private def _workflow_projection(candidate: LogicalUiCandidate): UseCaseScreenProjection = {
    val projection = _projection(candidate)
    projection.copy(screens = projection.screens.map(screen =>
      screen.copy(subject = ScreenSubject(WorkflowRole, _workflow_binding))
    ))
  }

  private def _right[A](value: Either[LogicalUiError, A]): A = value match {
    case Right(result) => result
    case Left(error) => fail(s"Expected a Logical UI result but received ${error.code}: ${error.reason}")
  }

  private def _left[A](value: Either[LogicalUiError, A]): LogicalUiError = value match {
    case Left(error) => error
    case Right(_) => fail("Expected a closed Logical UI failure")
  }
}
