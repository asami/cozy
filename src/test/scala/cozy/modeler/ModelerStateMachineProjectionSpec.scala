package cozy.modeler

import org.goldenport.kaleidox.{Config => KaleidoxConfig, Model => KaleidoxModel}
import org.goldenport.sm.{Activity, Parcel, StateClass, StateMachine, StateMachineClass, StateMachineLogic, StateMachineRule}
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.simplemodeling.model.{MPackageRef, SimpleModel}
import org.simplemodeling.model.domain.{MDomainComponent, MDomainStateMachine}
import org.smartdox.Description

/*
 * @since   Aug. 14, 2026
 * @version Aug. 14, 2026
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
