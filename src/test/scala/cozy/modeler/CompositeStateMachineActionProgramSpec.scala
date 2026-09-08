package cozy.modeler

import org.goldenport.realm.Realm
import org.scalacheck.{Gen, Prop, Test}
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Sep.  8, 2026
 * @version Sep.  8, 2026
 * @author  ASAMI, Tomoharu
 */
final class CompositeStateMachineActionProgramSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  "Composite StateMachine logical-action program generation" should {
    "emit a deterministic, causally ordered producer descriptor" which {
      "retain repeated logical-action occurrences with typed metadata and provenance" in {
        Given("a normalized definition whose one logical action occurs at all constituent placements and a derived transition")
        val definitions = Vector(_definition(Some(_metadata)))

        When("the additive logical-action program source and canonical JSON are generated twice")
        val generated = CompositeStateMachineActionProgram.generate(definitions)
        val regenerated = CompositeStateMachineActionProgram.generate(definitions)
        val definitionsource = _realm_string(generated, _definition_path)
        val regeneratedsource = _realm_string(regenerated, _definition_path)
        val json = CompositeStateMachineActionProgram.canonicalJson(definitions)
        val regeneratedjson = CompositeStateMachineActionProgram.canonicalJson(definitions)

        Then("the source and JSON are byte-stable and preserve the typed logical action")
        definitionsource shouldBe regeneratedsource
        json shouldBe regeneratedjson
        definitionsource should include ("schemaVersion = \"cozy.cml.logical-action-program.v1\"")
        definitionsource should include ("actionId = \"capture-payment\"")
        definitionsource should include ("Operation(\"OrderService\", \"capturePayment\", Some(\"PaymentCommand\"))")
        definitionsource should include ("inputBinding = Some(\"payment.subject\")")
        definitionsource should include ("effectClass = \"EXTERNAL\"")
        definitionsource should include ("transactionRequirement = \"OUTSIDE_UNIT_OF_WORK\"")
        definitionsource should include ("Idempotency(required = true, keyRef = Some(\"payment-command\"))")
        definitionsource should include ("compensationHandlerRef = Some(\"cancel-payment\")")
        json should include ("\"schemaVersion\":\"cozy.cml.logical-action-program.v1\"")
        json should include ("\"operation\":{\"service\":\"OrderService\",\"name\":\"capturePayment\",\"inputType\":\"PaymentCommand\"}")
        json should include ("\"occurrenceSource\":{\"line\":41}")

        And("the generated occurrence records retain each placement in causal order rather than deduplicating the logical action")
        definitionsource.indexOf("occurrenceId = \"constituent:payment-exit:1\"") should be < definitionsource.indexOf("occurrenceId = \"constituent:payment-transition:2\"")
        definitionsource.indexOf("occurrenceId = \"constituent:payment-entry:3\"") should be < definitionsource.indexOf("occurrenceId = \"derived:completed:4\"")
        "actionId = \"capture-payment\"".r.findAllMatchIn(definitionsource).size shouldBe 5
        json should include ("\"placement\":\"exit\"")
        json should include ("\"placement\":\"transition\"")
        json should include ("\"placement\":\"entry\"")
        json should include ("\"placement\":\"derived-transition\"")
        json should include ("\"ordinal\":4")
      }

      "preserve the causal placement order for every authored placement permutation" in {
        Given("every source ordering of the same exit, transition, and entry occurrences")
        val placementorders = Gen.oneOf(Vector("exit", "transition", "entry").permutations.toVector)

        When("ScalaCheck generates program descriptors for each admitted authored permutation")
        val property = Prop.forAll(placementorders) { placements =>
          val generated = CompositeStateMachineActionProgram.generate(Vector(_definition(Some(_metadata), placements)))
          val definitionsource = _realm_string(generated, _definition_path)
          definitionsource.indexOf("occurrenceId = \"constituent:payment-exit:1\"") <
            definitionsource.indexOf("occurrenceId = \"constituent:payment-transition:2\"") &&
            definitionsource.indexOf("occurrenceId = \"constituent:payment-transition:2\"") <
              definitionsource.indexOf("occurrenceId = \"constituent:payment-entry:3\"")
        }
        val check = Test.check(Test.Parameters.default.withMinSuccessfulTests(12), property)

        Then("each generated descriptor composes constituent placements in exit, transition, entry order before derived occurrences")
        check.passed shouldBe true
      }
    }

    "publish the generated compiler validation contract" which {
      "reject compiler selections that lack required metadata while retaining legacy CML as a descriptor" in {
        Given("a normalized legacy logical action without Phase 47.1 metadata")
        val generated = CompositeStateMachineActionProgram.generate(Vector(_definition(None)))
        val definitionsource = _realm_string(generated, _definition_path)
        val compilersource = _realm_string(generated, _compiler_path)
        val json = CompositeStateMachineActionProgram.canonicalJson(Vector(_definition(None)))

        When("the generated compiler ABI describes compilation of its occurrence records")
        val missingmetadata = "case occurrence if occurrence.metadata.isEmpty => MissingMetadata(occurrence.occurrenceId, occurrence.actionId)"

        Then("legacy input remains representable but cannot become a compiled occurrence without metadata")
        definitionsource should include ("metadata = None")
        json should include ("\"metadata\":null")
        compilersource should include ("final case class MissingMetadata(occurrenceId: String, actionId: String) extends ValidationError")
        compilersource should include (missingmetadata)
        compilersource should include ("if (errors.nonEmpty)")
        compilersource should include ("Left(errors)")
      }

      "use the existing CNCF Free UnitOfWork path for consumer-supplied bindings" in {
        Given("the generated consumer compiler source for a metadata-bearing action program")
        val generated = CompositeStateMachineActionProgram.generate(Vector(_definition(Some(_metadata))))
        val compilersource = _realm_string(generated, _compiler_path)

        When("the emitted ABI validates bindings and selected occurrence identifiers before composition")
        val validationnames = Vector(
          "UnknownBinding",
          "DuplicateBinding",
          "MissingBinding",
          "IncompatibleBinding",
          "UnknownOccurrenceSelection",
          "DuplicateOccurrenceSelection"
        )

        Then("the ABI rejects each invalid binding or selection category as a value and composes only existing ExecProgram fragments")
        compilersource should include ("type ExecProgram[A] = org.goldenport.cncf.unitofwork.ExecProgram[A]")
        compilersource should include ("final case class Binding(actionId: String, operation: CompositeStateMachineActionProgramAbi.Operation, inputBinding: Option[String], program: ExecProgram[Unit])")
        validationnames.foreach { name =>
          compilersource should include (s"final case class $name")
        }
        compilersource should include ("expected.operation != binding.operation || expected.inputBinding != binding.inputBinding")
        compilersource should include ("definition.occurrences.filter(occurrence => requestedOccurrenceIds.contains(occurrence.occurrenceId))")
        compilersource should include ("Free.pure[org.goldenport.cncf.unitofwork.UnitOfWorkOp, Unit](())")
        compilersource should include ("acc.flatMap(_ => program)")
        compilersource should not include ("ActionOp")
      }
    }
  }

  private def _realm_string(realm: Realm, path: String): String =
    realm.get(path).collect { case value: Realm.StringData => value.string }.getOrElse(
      fail(s"Missing generated logical-action program source: $path")
    )

  private def _definition(
    metadata: Option[CompositeStateMachineActionMetadata],
    placements: Vector[String] = Vector("transition", "entry", "exit")
  ): CompositeStateMachineDefinition = {
    val action = CompositeStateMachineLogicalAction(
      identity = "capture-payment",
      kind = "OPERATION",
      operation = CompositeStateMachineOperation("OrderService", "capturePayment", Some("PaymentCommand")),
      inputBinding = Some("payment.subject"),
      source = CompositeStateMachineSourceIdentity(Some(12)),
      metadata = metadata
    )
    CompositeStateMachineDefinition(
      identity = "OrderProgress",
      name = "OrderProgress",
      source = CompositeStateMachineSourceIdentity(Some(1)),
      constituents = Vector.empty,
      states = Vector.empty,
      derivations = Vector.empty,
      initialConfiguration = None,
      actions = Vector(action),
      constituentActions = placements.map {
        case "exit" =>
          CompositeStateMachineConstituentAction("payment-exit", "payment", "Awaiting", "Paid", "captured", "exit", action, CompositeStateMachineSourceIdentity(Some(41)))
        case "transition" =>
          CompositeStateMachineConstituentAction("payment-transition", "payment", "Awaiting", "Paid", "captured", "transition", action, CompositeStateMachineSourceIdentity(Some(42)))
        case "entry" =>
          CompositeStateMachineConstituentAction("payment-entry", "payment", "Awaiting", "Paid", "captured", "entry", action, CompositeStateMachineSourceIdentity(Some(43)))
      },
      derivedActions = Vector(
        CompositeStateMachineDerivedAction("completed", "Pending", "Complete", action, CompositeStateMachineSourceIdentity(Some(51)))
      )
    )
  }

  private val _metadata = CompositeStateMachineActionMetadata(
    CompositeStateMachineEffectClass.External,
    CompositeStateMachineTransactionRequirement.OutsideUnitOfWork,
    CompositeStateMachineIdempotency.Required("payment-command"),
    Some("cancel-payment")
  )

  private val _root = "target/scala-3.3.8/src_managed/main/scala/domain/composite/statemachine/actionprogram"
  private val _definition_path = s"${_root}/OrderProgressActionProgram1.scala"
  private val _compiler_path = s"${_root}/LogicalActionCompiler.scala"
}
