package cozy.modeler

import org.goldenport.kaleidox.{Config => KaleidoxConfig, Model => KaleidoxModel}
import org.goldenport.event.EventClazz
import org.goldenport.sm.{Activity, EventNameGuard, FinalTransitionTo, NameTransitionTo, Parcel, StateClass, StateMachine, StateMachineClass, StateMachineLogic, StateMachineRule, Transition, Transitions}
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.simplemodeling.model.{MComponent, MPackageRef, SimpleModel}
import org.simplemodeling.model.domain.{MDomainComponent, MDomainStateMachine}
import org.smartdox.Description

/*
 * @since   Aug. 14, 2026
 *  version Aug. 14, 2026
 * @version Sep. 18, 2026
 * @author  ASAMI, Tomoharu
 */
final class ModelerStateMachineProjectionSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  "CML StateMachine projection" should {
    "select diagram StateMachines deterministically" which {
      "prefers an exact top-level StateMachine name over an entity-local candidate" in {
        Given("a CML model with matching top-level and entity-local lifecycle StateMachines")
        val model = _selection_model()
        val modeler = new Modeler()
        val expected = model.stateMachineModel.getClass("lifecycle").getOrElse(
          throw new IllegalArgumentException("The top-level lifecycle StateMachine is required for this specification.")
        )

        When("the diagram selector receives the shared lifecycle name")
        val selected = modeler._select_state_machine(model, "lifecycle")
        val actual = selected.getOrElse(
          throw new IllegalArgumentException("The diagram selector must return a lifecycle StateMachine.")
        )

        Then("the exact top-level StateMachine object wins")
        (actual eq expected) shouldBe true
      }

      "selects a named StateMachine beneath a named entity" in {
        Given("a CML model with Person-owned lifecycle and billing StateMachines")
        val model = _selection_model()
        val modeler = new Modeler()

        When("the diagram selector receives an Entity.StateMachine name")
        val selected = modeler._select_state_machine(model, "Person.billing")

        Then("the qualified entity machine is selected")
        selected.map(_.name) shouldBe Some("billing")
      }

      "selects the only StateMachine for a bare entity name" in {
        Given("a CML model with one Order-owned StateMachine")
        val model = _selection_model()
        val modeler = new Modeler()

        When("the diagram selector receives the bare entity name")
        val selected = modeler._select_state_machine(model, "Order")

        Then("the entity's single StateMachine is selected")
        selected.map(_.name) shouldBe Some("fulfillment")
      }

      "rejects a bare entity name when it has multiple machine candidates" in {
        Given("a CML model with multiple Person-owned StateMachines")
        val model = _selection_model()
        val modeler = new Modeler()

        When("the diagram selector receives the ambiguous entity name")
        val error = intercept[RuntimeException] {
          modeler._select_state_machine(model, "Person")
        }

        Then("the syntax diagnostic names the available machine candidates")
        error.getMessage should include("multiple StateMachine candidates: lifecycle, billing")
      }
    }

    "project transition ACTION metadata" which {
      "normalizes a non-Workflow StateMachine into the typed declaration model" in {
        Given("a CML StateMachine transition with an event, target state, and ACTION")
        val model = _action_model(_single_action_source())
        val builder = Modeler.ModelBuilder(model)

        When("the ordinary StateMachine projection builds its component definition")
        val component = builder.build().elements.collectFirst {
          case c: MDomainComponent if c.stateMachineDefinitions.nonEmpty => c
        }.getOrElse(throw new IllegalArgumentException("A component with a normalized lifecycle StateMachine is required for this specification."))
        val normalized = component.stateMachineDefinitions.head.normalization match {
          case Some(MComponent.StateMachineNormalization.Accepted(value)) => value
          case Some(MComponent.StateMachineNormalization.Rejected(diagnostics)) =>
            fail(s"The non-Workflow StateMachine must normalize: $diagnostics")
          case None =>
            fail("The non-Workflow StateMachine must have a normalization result.")
        }
        val transition = normalized.transitions.head

        Then("semantic identities and ordered actions are retained without a raw expression guard")
        normalized.identity.qualifiedName shouldBe "lifecycle"
        normalized.version shouldBe 1
        normalized.initialState.map(_.path) shouldBe Some(Vector("Draft"))
        transition.identity.declarationOrder shouldBe 0
        transition.source.map(_.path) shouldBe Some(Vector("Draft"))
        transition.trigger.eventName shouldBe "publish"
        transition.target shouldBe MComponent.StateMachineTransitionTarget.State(
          MComponent.StateMachineStateIdentity(normalized.identity, Vector("Published"))
        )
        transition.guard shouldBe MComponent.StateMachineGuardProgram.Predicate(
          MComponent.PredicateProgram(expression = MComponent.StateMachinePredicate.Always)
        )
        transition.actions.transition.map(_.reference) shouldBe Vector("recordPayment")
        transition.sourceLocation.declarationPath shouldBe Vector(
          "StateMachine", "lifecycle", "root", "state", "Draft", "0", "call", "0"
        )
        val binding = component.stateMachineTransitionRules.head.binding.getOrElse(
          fail("The generated transition rule must retain its explicit normalized binding.")
        )
        binding.entityName shouldBe "Person"
        binding.machine shouldBe normalized.identity
        binding.version shouldBe normalized.version
        binding.transition shouldBe transition.identity
        binding.source shouldBe transition.source.getOrElse(
          fail("The normalized transition must have a source state.")
        )
        binding.target shouldBe transition.target
        binding.trigger shouldBe transition.trigger
      }

      "projects an explicit operation trigger without requiring a same-named Event declaration" in {
        Given("a non-Workflow StateMachine transition bound to entity.updateSalesOrder and no Event declaration")
        val model = _action_model(_operation_trigger_source())
        val builder = Modeler.ModelBuilder(model)

        When("the component definition is projected through the ordinary StateMachine path")
        val component = builder.build().elements.collectFirst {
          case c: MDomainComponent if c.stateMachineDefinitions.nonEmpty => c
        }.getOrElse(fail("A component with an explicit operation-bound StateMachine is required."))
        val normalized = component.stateMachineDefinitions.head.normalization match {
          case Some(MComponent.StateMachineNormalization.Accepted(value)) => value
          case other => fail(s"Expected an accepted explicit-operation normalization, got $other")
        }
        val transition = normalized.transitions.head
        val binding = component.stateMachineTransitionRules.head.binding.getOrElse(
          fail("The explicit operation transition must retain its generated binding.")
        )

        Then("the typed operation identity, Operation rule trigger, and normalized trigger schema version are retained")
        transition.trigger.eventName shouldBe "operation:entity.updateSalesOrder"
        transition.operation shouldBe Some(MComponent.StateMachineOperationIdentity("entity", "updateSalesOrder"))
        component.stateMachineTransitionRules.head.trigger shouldBe MComponent.TransitionTrigger.Operation
        binding.operation shouldBe transition.operation
        binding.version shouldBe normalized.version
      }

      "rejects a malformed explicit operation trigger with a safe diagnostic" in {
        Given("a non-Workflow StateMachine whose operation marker omits its operation segment")
        val model = _action_model(_malformed_operation_trigger_source())
        val builder = Modeler.ModelBuilder(model)

        When("the StateMachine normalizer receives the parsed declaration")
        val normalization = new ModelStateMachineProjector(builder).
          normalizeStateMachine(_entity_state_machine(model))

        Then("projection rejects the marker without treating it as a legacy Event")
        val diagnostic = normalization match {
          case MComponent.StateMachineNormalization.Rejected(Vector(value)) => value
          case other => fail(s"Expected one invalid-operation-trigger diagnostic, got $other")
        }
        diagnostic.code shouldBe "invalid-operation-trigger"
        diagnostic.message should include("exactly two nonempty service.operation segments")
        diagnostic.transition.map(_.declarationOrder) shouldBe Some(0)
      }

      "records a deterministic diagnostic for a legacy raw guard without changing the legacy carrier" in {
        Given("a non-Workflow StateMachine with an existing raw expression guard")
        val model = _action_model(_raw_expression_guard_source())
        val builder = Modeler.ModelBuilder(model)

        When("the component is projected")
        val component = builder.build().elements.collectFirst {
          case c: MDomainComponent if c.stateMachineDefinitions.nonEmpty => c
        }.getOrElse(throw new IllegalArgumentException("A component with a lifecycle StateMachine is required for this specification."))

        Then("the typed normalization rejects the raw guard while the pre-Phase-63.1 generation carrier remains intact")
        val diagnostic = component.stateMachineDefinitions.head.normalization match {
          case Some(MComponent.StateMachineNormalization.Rejected(Vector(value))) => value
          case other => fail(s"Expected one raw-expression diagnostic, got $other")
        }
        diagnostic.code shouldBe "legacy-raw-expression-not-admitted"
        diagnostic.machine shouldBe MComponent.StateMachineIdentity("lifecycle")
        diagnostic.transition.map(_.declarationOrder) shouldBe Some(0)
        diagnostic.sourceLocation.declarationPath shouldBe Vector(
          "StateMachine", "lifecycle", "root", "state", "Draft", "0", "call", "0"
        )
        component.stateMachineTransitionRules.head.guard shouldBe Some(
          MComponent.RuleGuard.Expression("event.amount > 0")
        )
      }

      "records a deterministic diagnostic for an invalid target state" in {
        Given("an already-parsed non-Workflow StateMachine that names no declared target state")
        val model = _action_model(_single_action_source())
        val builder = Modeler.ModelBuilder(model)

        When("the StateMachine normalizer receives the parsed declaration")
        val normalization = new ModelStateMachineProjector(builder).
          normalizeStateMachine(_invalid_target_state_machine())

        Then("the typed normalization rejects the declaration with stable provenance")
        val diagnostic = normalization match {
          case MComponent.StateMachineNormalization.Rejected(Vector(value)) => value
          case other => fail(s"Expected one invalid-target diagnostic, got $other")
        }
        diagnostic.code shouldBe "invalid-target-state"
        diagnostic.transition.map(_.declarationOrder) shouldBe Some(0)
        diagnostic.sourceLocation.declarationPath shouldBe Vector(
          "StateMachine", "lifecycle", "root", "state", "Draft", "0", "global", "0"
        )
      }

      "rejects a composite-only StateMachine without an initial state" in {
        Given("an already-parsed non-Workflow StateMachine whose root has only a composite")
        val model = _action_model(_single_action_source())
        val builder = Modeler.ModelBuilder(model)

        When("the StateMachine normalizer receives the parsed declaration")
        val normalization = new ModelStateMachineProjector(builder).
          normalizeStateMachine(_composite_only_state_machine())

        Then("the typed normalization emits one machine-level missing-initial-state diagnostic")
        val diagnostic = normalization match {
          case MComponent.StateMachineNormalization.Rejected(Vector(value)) => value
          case other => fail(s"Expected one missing-initial-state diagnostic, got $other")
        }
        diagnostic.code shouldBe "missing-initial-state"
        diagnostic.transition shouldBe None
        diagnostic.sourceLocation.declarationPath shouldBe Vector("StateMachine", "lifecycle")
      }

      "honors an explicit INIT transition as the normalized initial state" in {
        Given("an already-parsed non-Workflow StateMachine with an INIT transition to Published")
        val model = _action_model(_single_action_source())
        val builder = Modeler.ModelBuilder(model)

        When("the StateMachine normalizer receives the parsed declaration")
        val normalization = new ModelStateMachineProjector(builder).
          normalizeStateMachine(_explicit_initial_state_machine())
        val normalized = normalization match {
          case MComponent.StateMachineNormalization.Accepted(value) => value
          case other => fail(s"Expected an accepted explicit-initial normalization, got $other")
        }

        Then("the typed model records Published as initial and omits INIT from the state and transition identities")
        normalized.initialState.map(_.path) shouldBe Some(Vector("Published"))
        normalized.states.map(_.path) shouldBe Vector(Vector("Draft"), Vector("Published"))
        normalized.transitions.map(_.trigger.eventName) shouldBe Vector("publish")
      }

      "records final targets in deterministic terminal transition metadata" in {
        Given("an already-parsed non-Workflow StateMachine with a transition to FINAL")
        val model = _action_model(_single_action_source())
        val builder = Modeler.ModelBuilder(model)

        When("the StateMachine normalizer receives the parsed declaration")
        val normalization = new ModelStateMachineProjector(builder).
          normalizeStateMachine(_final_transition_state_machine())
        val normalized = normalization match {
          case MComponent.StateMachineNormalization.Accepted(value) => value
          case other => fail(s"Expected an accepted final-transition normalization, got $other")
        }
        val transition = normalized.transitions.head

        Then("the transition target is Final and its identity is listed as terminal metadata")
        transition.target shouldBe MComponent.StateMachineTransitionTarget.Final
        normalized.topology.terminalTransitions shouldBe Vector(transition.identity)
      }

      "rejects nested composites with stable declaration provenance" in {
        Given("an already-parsed non-Workflow StateMachine containing a nested composite")
        val model = _action_model(_single_action_source())
        val builder = Modeler.ModelBuilder(model)

        When("the StateMachine normalizer receives the nested declaration")
        val normalization = new ModelStateMachineProjector(builder).
          normalizeStateMachine(_nested_composite_state_machine())
        val diagnostic = normalization match {
          case MComponent.StateMachineNormalization.Rejected(Vector(value)) => value
          case other => fail(s"Expected one nested-composite diagnostic, got $other")
        }

        Then("the rejection names the unsupported branch and its deterministic declaration path")
        diagnostic.code shouldBe "unsupported-nested-composite"
        diagnostic.transition shouldBe None
        diagnostic.sourceLocation.declarationPath shouldBe Vector(
          "StateMachine", "lifecycle", "root", "composite", "Review", "0"
        )
      }

      "orders transitions deterministically across distinct declaration containers" in {
        Given("an already-parsed StateMachine with state, rule, and composite transition containers")
        val model = _action_model(_single_action_source())
        val builder = Modeler.ModelBuilder(model)

        When("the StateMachine normalizer receives the parsed declaration")
        val normalization = new ModelStateMachineProjector(builder).
          normalizeStateMachine(_distinct_container_state_machine())
        val normalized = normalization match {
          case MComponent.StateMachineNormalization.Accepted(value) => value
          case other => fail(s"Expected an accepted distinct-container normalization, got $other")
        }

        Then("the typed transitions preserve state, rule, then nested-composite declaration order")
        normalized.transitions.map(_.trigger.eventName) shouldBe Vector("publish", "archive", "approve")
        normalized.transitions.map(_.sourceLocation.declarationPath) shouldBe Vector(
          Vector("StateMachine", "lifecycle", "root", "state", "Draft", "0", "global", "0"),
          Vector("StateMachine", "lifecycle", "root", "rule", "global", "0"),
          Vector("StateMachine", "lifecycle", "root", "composite", "Review", "0", "state", "Pending", "0", "global", "0")
        )
      }

      "normalizes an admitted named guard as a typed binding reference" in {
        Given("a non-Workflow StateMachine with a named guard")
        val model = _action_model(_named_guard_source())
        val builder = Modeler.ModelBuilder(model)

        When("the component is projected")
        val component = builder.build().elements.collectFirst {
          case c: MDomainComponent if c.stateMachineDefinitions.nonEmpty => c
        }.getOrElse(throw new IllegalArgumentException("A component with a lifecycle StateMachine is required for this specification."))
        val normalization = component.stateMachineDefinitions.head.normalization match {
          case Some(MComponent.StateMachineNormalization.Accepted(value)) => value
          case other => fail(s"Expected an accepted named-guard normalization, got $other")
        }
        val transition = normalization.transitions.head

        Then("the guard is a nominal binding identity instead of a raw expression string")
        transition.guard shouldBe MComponent.StateMachineGuardProgram.Named(
          MComponent.StateMachineGuardIdentity(
            MComponent.StateMachineTransitionIdentity(MComponent.StateMachineIdentity("lifecycle"), 0),
            "paymentConfirmed"
          )
        )
        transition.actions.transition.head.identity.phase shouldBe MComponent.StateMachineActionPhase.Transition
        transition.actions.transition.head.identity.declarationOrder shouldBe 0
        transition.actions.transition.head.reference shouldBe "recordPayment"
      }

      "retains parsed declaration order independently of legacy rule order" in {
        Given("two already-parsed non-Workflow transitions from one source state")
        val model = _action_model(_two_transition_source())
        val builder = Modeler.ModelBuilder(model)

        When("the component definition is normalized")
        val component = builder.build().elements.collectFirst {
          case c: MDomainComponent if c.stateMachineDefinitions.nonEmpty => c
        }.getOrElse(throw new IllegalArgumentException("A component with a lifecycle StateMachine is required for this specification."))
        val normalized = component.stateMachineDefinitions.head.normalization match {
          case Some(MComponent.StateMachineNormalization.Accepted(value)) => value
          case other => fail(s"Expected accepted transition normalization, got $other")
        }

        Then("transition identities follow parsed declaration order")
        normalized.transitions.map(_.identity.declarationOrder) shouldBe Vector(0, 1)
        normalized.transitions.map(_.trigger.eventName) shouldBe Vector("publish", "archive")
      }

      "retains one-level composite and named shallow-history topology" in {
        Given("an already-parsed non-Workflow StateMachine with Review shallow history")
        val model = _action_model(_history_normalization_source())
        val builder = Modeler.ModelBuilder(model)

        When("the component definition is normalized")
        val component = builder.build().elements.collectFirst {
          case c: MDomainComponent if c.stateMachineDefinitions.nonEmpty => c
        }.getOrElse(throw new IllegalArgumentException("A component with a lifecycle StateMachine is required for this specification."))
        val normalized = component.stateMachineDefinitions.head.normalization match {
          case Some(MComponent.StateMachineNormalization.Accepted(value)) => value
          case other => fail(s"Expected accepted one-level topology normalization, got $other")
        }
        val review = MComponent.StateMachineStateIdentity(normalized.identity, Vector("Review"))
        val pending = MComponent.StateMachineStateIdentity(normalized.identity, Vector("Review", "Pending"))
        val resume = normalized.transitions.find(_.trigger.eventName == "resume").getOrElse(
          fail("A normalized resume transition is required.")
        )

        Then("the direct leaves, fallback, and history writes remain typed metadata")
        normalized.historyFieldName shouldBe Some("lifecycleHistory")
        normalized.topology.composites shouldBe Vector(
          MComponent.StateMachineCompositeTopology(
            review,
            Vector(
              pending,
              MComponent.StateMachineStateIdentity(normalized.identity, Vector("Review", "Approved"))
            )
          )
        )
        resume.target shouldBe MComponent.StateMachineTransitionTarget.ShallowHistory(review, Some(pending))
        normalized.transitions.find(_.trigger.eventName == "submit").map(_.historyWrites) shouldBe Some(
          Vector(MComponent.StateMachineHistoryWrite(review, pending))
        )
        component.stateMachineTransitionRules.find(_.historyCompositeName.contains("Review")).map(
          _.historyDirectLeafValues
        ) shouldBe Some(Map("Pending" -> 2, "Approved" -> 3))
      }

      "carries explicit bindings for every one-level named shallow-history transition" in {
        Given("an already-parsed StateMachine with root and composite transitions")
        val model = _action_model(_history_normalization_source())
        val builder = Modeler.ModelBuilder(model)

        When("the component transition rules are projected")
        val component = builder.build().elements.collectFirst {
          case c: MDomainComponent if c.stateMachineDefinitions.nonEmpty => c
        }.getOrElse(throw new IllegalArgumentException("A component with a lifecycle StateMachine is required for this specification."))
        val normalized = component.stateMachineDefinitions.head.normalization match {
          case Some(MComponent.StateMachineNormalization.Accepted(value)) => value
          case other => fail(s"Expected accepted one-level topology normalization, got $other")
        }
        val bindings = component.stateMachineTransitionRules.map(_.binding.getOrElse(
          fail("Every normalized StateMachine transition must retain its explicit binding.")
        ))

        Then("each generated rule retains the exact normalized identity, source, target, and trigger")
        bindings.map(_.entityName) shouldBe Vector("Person", "Person", "Person")
        bindings.map(_.machine) shouldBe normalized.transitions.map(_.identity.machine)
        bindings.map(_.version) shouldBe normalized.transitions.map(_ => normalized.version)
        bindings.map(_.transition) shouldBe normalized.transitions.map(_.identity)
        bindings.map(_.source) shouldBe normalized.transitions.map(_.source.getOrElse(
          fail("The fixture's normalized transitions must all have a source state.")
        ))
        bindings.map(_.target) shouldBe normalized.transitions.map(_.target)
        bindings.map(_.trigger) shouldBe normalized.transitions.map(_.trigger)
      }

      "rejects a named history transition without a declared history field" in {
        Given("a parsed history topology whose HISTORY-FIELD declaration is absent")
        val model = _action_model(_history_normalization_source().replace(
          "- HISTORY-FIELD :: lifecycleHistory\n",
          ""
        ))
        val builder = Modeler.ModelBuilder(model)
        val statemachine = _entity_state_machine(model)

        When("the parsed StateMachine is normalized directly")
        val normalization = new StateMachineNormalizationProjector(builder).normalize(statemachine)
        val diagnostic = normalization match {
          case MComponent.StateMachineNormalization.Rejected(Vector(value)) => value
          case other => fail(s"Expected one missing-history-field diagnostic, got $other")
        }

        Then("the named history transition is rejected with its stable identity and source path")
        diagnostic.code shouldBe "missing-history-field"
        diagnostic.transition shouldBe Some(
          MComponent.StateMachineTransitionIdentity(MComponent.StateMachineIdentity("lifecycle"), 1)
        )
        diagnostic.transition.map(_.declarationOrder) shouldBe Some(1)
        diagnostic.sourceLocation.declarationPath shouldBe Vector(
          "StateMachine", "lifecycle", "root", "state", "Suspended", "1", "call", "0"
        )
      }

      "rejects a transition whose trigger is absent from declared events" in {
        Given("an already-parsed StateMachine with one declared event and a different ON trigger")
        val model = _action_model(_history_normalization_source())
        val builder = Modeler.ModelBuilder(model)
        val statemachine = _undeclared_event_state_machine()

        When("the parsed StateMachine is normalized directly")
        val normalization = new StateMachineNormalizationProjector(builder).normalize(statemachine)
        val diagnostic = normalization match {
          case MComponent.StateMachineNormalization.Rejected(Vector(value)) => value
          case other => fail(s"Expected one undeclared-event diagnostic, got $other")
        }

        Then("the transition is rejected with its stable identity and source path")
        diagnostic.code shouldBe "undeclared-event"
        diagnostic.transition shouldBe Some(
          MComponent.StateMachineTransitionIdentity(MComponent.StateMachineIdentity("lifecycle"), 0)
        )
        diagnostic.transition.map(_.declarationOrder) shouldBe Some(0)
        diagnostic.sourceLocation.declarationPath shouldBe Vector(
          "StateMachine", "lifecycle", "root", "state", "Draft", "0", "global", "0"
        )
      }

      "rejects an entity-owned history field that is not an entity attribute" in {
        Given("a parsed entity that retains lifecycleHistory but names missingHistory in HISTORY-FIELD")
        val model = _action_model(_history_normalization_source().replace(
          "- HISTORY-FIELD :: lifecycleHistory",
          "- HISTORY-FIELD :: missingHistory"
        ))
        val builder = Modeler.ModelBuilder(model)
        val entityclass = model.getEntityModel.flatMap(_.get("Person")).getOrElse(
          throw new IllegalArgumentException("Person entity is required for this specification.")
        )
        val statemachine = _entity_state_machine(model)

        When("the parsed StateMachine is normalized directly with its owning EntityClass")
        val normalization = new StateMachineNormalizationProjector(builder).normalize(statemachine, Some(entityclass))
        val diagnostic = normalization match {
          case MComponent.StateMachineNormalization.Rejected(Vector(value)) => value
          case other => fail(s"Expected one invalid-history-field diagnostic, got $other")
        }

        Then("the machine-level invalid history field is reported without transition identity")
        diagnostic.code shouldBe "invalid-history-field"
        diagnostic.transition shouldBe None
        diagnostic.sourceLocation.declarationPath shouldBe Vector("StateMachine", "lifecycle")
      }

      "maps one nonempty transition ACTION through every real consumer" in {
        Given("a CML StateMachine transition with one ACTION")
        val model = _action_model(_single_action_source())
        val statemachine = _entity_state_machine(model)
        val builder = Modeler.ModelBuilder(model)

        When("the legacy and ModelStateMachineProjector paths project the StateMachine and build its component")
        val legacy = new Modeler()._project_state_machine(statemachine)
        val modern = new ModelStateMachineProjector(builder).projectStateMachine(statemachine)
        val component = builder.build().elements.collectFirst {
          case component: MDomainComponent if component.stateMachineTransitionRules.nonEmpty => component
        }.getOrElse(throw new IllegalArgumentException("A component with Person lifecycle transition rules is required for this specification."))

        Then("each consumer retains the trimmed transition action metadata")
        legacy.transitions.head.action.map(_.name) shouldBe Some("recordPayment")
        modern.transitions.head.action.map(_.name) shouldBe Some("recordPayment")
        component.stateMachineTransitionRules.head.plan.transition.map(_.script) shouldBe Some("recordPayment")
      }

      "projects no action metadata for a whitespace-only transition ACTION" in {
        Given("a CML StateMachine transition with a whitespace-only ACTION")
        val model = _action_model(_whitespace_action_source())
        val statemachine = _entity_state_machine(model)
        val builder = Modeler.ModelBuilder(model)

        When("the legacy and ModelStateMachineProjector paths project the StateMachine and build its component")
        val legacy = new Modeler()._project_state_machine(statemachine)
        val modern = new ModelStateMachineProjector(builder).projectStateMachine(statemachine)
        val component = builder.build().elements.collectFirst {
          case c: MDomainComponent if c.stateMachineTransitionRules.nonEmpty => c
        }.getOrElse(throw new IllegalArgumentException("A component with Person lifecycle transition rules is required for this specification."))

        Then("none of the transition consumers receives action metadata")
        legacy.transitions.head.action shouldBe None
        modern.transitions.head.action shouldBe None
        component.stateMachineTransitionRules.head.plan.transition shouldBe None
      }

      "rejects multiple transition ACTION lines instead of truncating them" in {
        Given("a CML StateMachine transition with two ACTION lines")
        val model = KaleidoxModel.parseWitoutLocation(KaleidoxConfig.default, _multiple_action_source())

        When("the Modeler projects the StateMachine")
        val error = intercept[RuntimeException] {
          Modeler.ModelBuilder(model).build()
        }

        Then("the syntax diagnostic rejects the unsupported multiplicity")
        error.getMessage should include("transition ACTION must contain exactly one nonempty action line")
      }
    }

    "diagnose incomplete projection input" which {
      "rejects an unnamed composite StateMachine rule" in {
        Given("an otherwise valid StateMachineClass with an unnamed nested composite rule")
        val invalid = _unnamed_composite_state_machine()
        val validmodel = _action_model(_single_action_source())

        When("the legacy and ModelStateMachineProjector paths project it")
        val legacyerror = intercept[RuntimeException] {
          new Modeler()._project_state_machine(invalid)
        }
        val modernerror = intercept[RuntimeException] {
          new ModelStateMachineProjector(Modeler.ModelBuilder(validmodel)).projectStateMachine(invalid)
        }

        Then("both paths raise the same syntax diagnostic rather than inventing a name")
        legacyerror.getMessage should include("composite state requires a name")
        modernerror.getMessage shouldBe legacyerror.getMessage
      }
    }

    "resolve diagram packages" which {
      "selects the resolved domain package or the model root target" in {
        Given("a model with a domain package and an empty model")
        val domainmodel = _domain_package_model()
        val emptymodel = SimpleModel(Vector.empty)
        val modeler = new Modeler()

        When("class-diagram target selection resolves the standard domain request")
        val resolved = modeler._diagram_target_package(domainmodel, "domain")
        val fallback = modeler._diagram_target_package(emptymodel, "domain")

        Then("the actual resolved package object is selected or the root is used as fallback")
        resolved shouldBe domainmodel.getPackage("domain").get
        fallback shouldBe emptymodel.root
      }
    }
  }

  private def _selection_model(): KaleidoxModel =
    KaleidoxModel.parseWitoutLocation(KaleidoxConfig.default, _selection_source())

  private def _selection_source(): String =
    """# STATE-MACHINE
      |
      |## lifecycle
      |
      |### State
      |
      |#### Draft
      |
      |value = 1
      |
      |# Entity
      |
      |## Person
      |
      |### Attribute
      |
      || name | type     | multiplicity |
      ||------+----------+--------------|
      || id   | entityid | 1            |
      |
      |### StateMachine
      |
      |#### lifecycle
      |
      |##### State
      |
      |###### Draft
      |
      |#### billing
      |
      |##### State
      |
      |###### Pending
      |
      |# Entity
      |
      |## Order
      |
      |### Attribute
      |
      || name | type     | multiplicity |
      ||------+----------+--------------|
      || id   | entityid | 1            |
      |
      |### StateMachine
      |
      |#### fulfillment
      |
      |##### State
      |
      |###### Pending
      |""".stripMargin

  private def _single_action_source(): String =
    s"""# COMPONENT
      |
      |## Domain
      |
      |### PACKAGE
      |
      |domain
      |
      |# Entity
      |
      |## Person
      |
      |### Attribute
      |
      || name   | type     | multiplicity |
      ||--------+----------+--------------|
      || id     | entityid | 1            |
      || status | int      | 1            |
      |
      |### StateMachine
      |
      |#### lifecycle
      |
      |##### State
      |
      |###### Draft
      |
      |####### Transition
      |
      |- TO :: Published
      |- ON :: publish
      |${_action_line("recordPayment", prefix = "  ", suffix = "  ")}
      |
      |###### Published
      |""".stripMargin

  private def _whitespace_action_source(): String =
    _single_action_source().replace(
      _action_line("recordPayment", prefix = "  ", suffix = "  "),
      _action_line("", prefix = "  ", suffix = "  ")
    )

  private def _explicit_initial_state_machine(): StateMachineClass = {
    val init = StateClass(
      "INIT",
      0,
      Transitions.global(Vector(
        Transition(EventNameGuard("start"), NameTransitionTo("Published"), Activity.Empty)
      ))
    )
    val draft = StateClass(
      "Draft",
      1,
      Transitions.global(Vector(
        Transition(EventNameGuard("publish"), NameTransitionTo("Published"), Activity.Empty)
      ))
    )
    val statemachinerule = StateMachineRule(
      name = Some("lifecycle"),
      states = List(init, draft, StateClass("Published", 2))
    )
    val logic = new StateMachineLogic {
      val rule: StateMachineRule = statemachinerule
      def execute(
        stateMachine: StateMachine,
        activity: Activity,
        parcel: Parcel
      ): Parcel = parcel
    }
    StateMachineClass("lifecycle", statemachinerule, logic)
  }

  private def _final_transition_state_machine(): StateMachineClass = {
    val statemachinerule = StateMachineRule(
      name = Some("lifecycle"),
      states = List(
        StateClass(
          "Draft",
          1,
          Transitions.global(Vector(
            Transition(EventNameGuard("complete"), FinalTransitionTo, Activity.Empty)
          ))
        )
      )
    )
    val logic = new StateMachineLogic {
      val rule: StateMachineRule = statemachinerule
      def execute(
        stateMachine: StateMachine,
        activity: Activity,
        parcel: Parcel
      ): Parcel = parcel
    }
    StateMachineClass("lifecycle", statemachinerule, logic)
  }

  private def _nested_composite_state_machine(): StateMachineClass = {
    val detail = StateMachineRule(
      name = Some("Detail"),
      states = List(StateClass("Pending", 1))
    )
    val review = StateMachineRule(
      name = Some("Review"),
      statemachines = List(detail)
    )
    val statemachinerule = StateMachineRule(
      name = Some("lifecycle"),
      states = List(StateClass("Draft", 1)),
      statemachines = List(review)
    )
    val logic = new StateMachineLogic {
      val rule: StateMachineRule = statemachinerule
      def execute(
        stateMachine: StateMachine,
        activity: Activity,
        parcel: Parcel
      ): Parcel = parcel
    }
    StateMachineClass("lifecycle", statemachinerule, logic)
  }

  private def _distinct_container_state_machine(): StateMachineClass = {
    val draft = StateClass(
      "Draft",
      1,
      Transitions.global(Vector(
        Transition(EventNameGuard("publish"), NameTransitionTo("Published"), Activity.Empty)
      ))
    )
    val review = StateMachineRule(
      name = Some("Review"),
      states = List(
        StateClass(
          "Pending",
          3,
          Transitions.global(Vector(
            Transition(EventNameGuard("approve"), NameTransitionTo("Pending"), Activity.Empty)
          ))
        )
      )
    )
    val statemachinerule = StateMachineRule(
      name = Some("lifecycle"),
      states = List(draft, StateClass("Published", 2)),
      statemachines = List(review),
      transitions = Transitions.global(Vector(
        Transition(EventNameGuard("archive"), NameTransitionTo("Draft"), Activity.Empty)
      ))
    )
    val logic = new StateMachineLogic {
      val rule: StateMachineRule = statemachinerule
      def execute(
        stateMachine: StateMachine,
        activity: Activity,
        parcel: Parcel
      ): Parcel = parcel
    }
    StateMachineClass("lifecycle", statemachinerule, logic)
  }

  private def _raw_expression_guard_source(): String =
    _single_action_source().replace(
      "- ON :: publish",
      "- ON :: publish\n- guard :: event.amount > 0"
    )

  private def _invalid_target_state_machine(): StateMachineClass = {
    val statemachinerule = StateMachineRule(
      name = Some("lifecycle"),
      states = List(
        StateClass(
          "Draft",
          1,
          Transitions.global(
            Vector(Transition(EventNameGuard("publish"), NameTransitionTo("Undeclared"), Activity.Empty))
          )
        ),
        StateClass("Published", 2)
      )
    )
    val logic = new StateMachineLogic {
      val rule: StateMachineRule = statemachinerule
      def execute(
        stateMachine: StateMachine,
        activity: Activity,
        parcel: Parcel
      ): Parcel = parcel
    }
    StateMachineClass("lifecycle", statemachinerule, logic)
  }

  private def _undeclared_event_state_machine(): StateMachineClass = {
    val statemachinerule = StateMachineRule(
      name = Some("lifecycle"),
      events = List(EventClazz("approved")),
      states = List(
        StateClass(
          "Draft",
          1,
          Transitions.global(Vector(
            Transition(EventNameGuard("publish"), NameTransitionTo("Published"), Activity.Empty)
          ))
        ),
        StateClass("Published", 2)
      )
    )
    val logic = new StateMachineLogic {
      val rule: StateMachineRule = statemachinerule
      def execute(
        stateMachine: StateMachine,
        activity: Activity,
        parcel: Parcel
      ): Parcel = parcel
    }
    StateMachineClass("lifecycle", statemachinerule, logic)
  }

  private def _composite_only_state_machine(): StateMachineClass = {
    val composite = StateMachineRule(
      name = Some("Review"),
      states = List(StateClass("Pending", 1))
    )
    val statemachinerule = StateMachineRule(
      name = Some("lifecycle"),
      statemachines = List(composite)
    )
    val logic = new StateMachineLogic {
      val rule: StateMachineRule = statemachinerule
      def execute(
        stateMachine: StateMachine,
        activity: Activity,
        parcel: Parcel
      ): Parcel = parcel
    }
    StateMachineClass("lifecycle", statemachinerule, logic)
  }

  private def _named_guard_source(): String =
    _single_action_source().replace(
      "- ON :: publish",
      "- ON :: publish\n- guard :: paymentConfirmed"
    )

  private def _operation_trigger_source(): String =
    _single_action_source().replace(
      "- ON :: publish",
      "- ON :: operation:entity.updateSalesOrder"
    )

  private def _malformed_operation_trigger_source(): String =
    _single_action_source().replace(
      "- ON :: publish",
      "- ON :: operation:entity"
    )

  private def _two_transition_source(): String =
    _single_action_source().replace(
      s"""- TO :: Published
         |- ON :: publish
         |${_action_line("recordPayment", prefix = "  ", suffix = "  ")}""".stripMargin,
      s"""- TO :: Published
         |- ON :: publish
         |${_action_line("recordPayment", prefix = "  ", suffix = "  ")}
         |
         |####### Transition
         |
         |- TO :: Published
         |- ON :: archive""".stripMargin
    )

  private def _history_normalization_source(): String =
    """# Entity
      |
      |## Person
      |
      |### Attribute
      |
      || name             | type     | multiplicity |
      ||------------------+----------+--------------|
      || id               | entityid | 1            |
      || status           | int      | 1            |
      || lifecycleHistory | record   | 1            |
      |
      |### StateMachine
      |
      |#### lifecycle
      |
      |- HISTORY-FIELD :: lifecycleHistory
      |
      |##### State
      |
      |###### Draft
      |
      |####### Transition
      |
      |- TO :: Pending
      |- ON :: submit
      |
      |###### Review
      |
      |####### State
      |
      |######## Pending
      |
      |######### Transition
      |
      |- TO :: Approved
      |- ON :: approve
      |
      |######## Approved
      |
      |###### Suspended
      |
      |####### Transition
      |
      |- TO :: Review.HISTORY
      |- ON :: resume
      |
      |##### Event
      |
      |###### submit
      |
      |###### approve
      |
      |###### resume
      |""".stripMargin

  private def _multiple_action_source(): String =
    _single_action_source().replace(
      _action_line("recordPayment", prefix = "  ", suffix = "  "),
      _action_line("recordPayment") + "\n" + _action_line("publishReceipt")
    )

  private def _action_line(
    action: String,
    prefix: String = " ",
    suffix: String = ""
  ): String =
    s"- ACTION ::$prefix$action$suffix"

  private def _action_model(source: String): KaleidoxModel =
    KaleidoxModel.parseWitoutLocation(KaleidoxConfig.default, source)

  private def _entity_state_machine(model: KaleidoxModel): StateMachineClass =
    model.getEntityModel.
      flatMap(_.get("Person")).
      flatMap(_.stateMachines.headOption).
      getOrElse(throw new IllegalArgumentException("Person lifecycle StateMachine is required for this specification."))

  private def _unnamed_composite_state_machine(): StateMachineClass = {
    val composite = StateMachineRule(states = List(StateClass("Nested", 1)))
    val staterule = StateMachineRule(
      name = Some("lifecycle"),
      states = List(StateClass("Draft", 1)),
      statemachines = List(composite)
    )
    val logic = new StateMachineLogic {
      val rule: StateMachineRule = staterule
      def execute(
        stateMachine: StateMachine,
        activity: Activity,
        parcel: Parcel
      ): Parcel = parcel
    }
    StateMachineClass("lifecycle", staterule, logic)
  }

  private def _domain_package_model(): SimpleModel =
    SimpleModel(
      Vector(
        new MDomainStateMachine(
          Description.name("lifecycle"),
          MPackageRef("domain")
        )
      )
    )
}
