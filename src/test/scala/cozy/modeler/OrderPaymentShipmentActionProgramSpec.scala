package cozy.modeler

import java.nio.file.{Files, Path, Paths}
import org.goldenport.kaleidox.{Config => KaleidoxConfig, Model => KaleidoxModel}
import org.scalacheck.{Gen, Prop, Test}
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Sep.  8, 2026
 * @version Sep.  8, 2026
 * @author  ASAMI, Tomoharu
 */
final class OrderPaymentShipmentActionProgramSpec extends AnyWordSpec with Matchers with GivenWhenThen with ModelerSpecSupport {
  "Order/Payment/Shipment logical-action program generation" should {
    "generate the checked-in fixture through the real modeler-scala route" which {
      "retain byte-stable descriptors, causal provenance, and metadata" in {
        Given("the checked-in Order/Payment/Shipment CML fixture and two isolated modeler-scala output roots")
        val input = _fixture_path
        val base = Paths.get(sys.props("user.dir")).toAbsolutePath.normalize()
        val outone = base.resolve("target/test-generated/order-payment-shipment-action-program/route-one")
        val outtwo = base.resolve("target/test-generated/order-payment-shipment-action-program/route-two")
        delete_recursively(outone)
        delete_recursively(outtwo)

        When("the real modeler-scala route generates the checked-in fixture twice")
        run_modeler_scala(input, outone)
        run_modeler_scala(input, outtwo)
        val sourcepath = "target/scala-3.3.8/src_managed/main/scala/domain/composite/statemachine/actionprogram/OrderPaymentShipmentActionProgram1.scala"
        val jsonpath = "target/cozy/cml-logical-action-program.json"
        val sourceone = Files.readString(outone.resolve(sourcepath))
        val sourcetwo = Files.readString(outtwo.resolve(sourcepath))
        val jsonone = Files.readString(outone.resolve(jsonpath))
        val jsontwo = Files.readString(outtwo.resolve(jsonpath))

        Then("both generated logical-action artifacts are byte-identical and use the frozen ABI")
        Files.readAllBytes(outone.resolve(sourcepath)).toVector shouldBe Files.readAllBytes(outtwo.resolve(sourcepath)).toVector
        Files.readAllBytes(outone.resolve(jsonpath)).toVector shouldBe Files.readAllBytes(outtwo.resolve(jsonpath)).toVector
        sourceone shouldBe sourcetwo
        jsonone shouldBe jsontwo
        sourceone should include ("schemaVersion = \"cozy.cml.logical-action-program.v1\"")
        jsonone should include ("\"schemaVersion\":\"cozy.cml.logical-action-program.v1\"")

        And("the logical actions retain typed operations and all required Phase 47.1 metadata")
        sourceone should include ("Operation(\"PaymentService\", \"recordAuthorization\", Some(\"PaymentCommand\"))")
        sourceone should include ("Operation(\"ShipmentService\", \"reserveShipment\", Some(\"ShipmentCommand\"))")
        sourceone should include ("Operation(\"ShipmentService\", \"releaseShipment\", Some(\"ShipmentCommand\"))")
        sourceone should include ("effectClass = \"LOCAL\"")
        sourceone should include ("transactionRequirement = \"REQUIRED\"")
        sourceone should include ("Idempotency(required = true, keyRef = Some(\"record-authorization\"))")
        sourceone should include ("effectClass = \"EXTERNAL\"")
        sourceone should include ("transactionRequirement = \"OUTSIDE_UNIT_OF_WORK\"")
        sourceone should include ("Idempotency(required = true, keyRef = Some(\"reserve-shipment\"))")
        sourceone should include ("compensationHandlerRef = Some(\"release-shipment\")")
        jsonone should include ("\"actionId\":\"release-shipment\"")
        jsonone should include ("\"compensationHandlerRef\":\"release-shipment\"")

        And("the three payment placements precede the derived shipment reservation while retaining provenance")
        sourceone.indexOf("occurrenceId = \"constituent:payment-authorization-exit:1\"") should be < sourceone.indexOf("occurrenceId = \"constituent:payment-authorization-transition:2\"")
        sourceone.indexOf("occurrenceId = \"constituent:payment-authorization-transition:2\"") should be < sourceone.indexOf("occurrenceId = \"constituent:payment-authorization-entry:3\"")
        sourceone.indexOf("occurrenceId = \"constituent:payment-authorization-entry:3\"") should be < sourceone.indexOf("occurrenceId = \"derived:reserve-shipment:4\"")
        sourceone should include ("origin = \"constituent\"")
        sourceone should include ("causalTransition = \"payment-authorization-exit\"")
        sourceone should include ("placement = \"exit\"")
        sourceone should include ("role = Some(\"payment\")")
        sourceone should include ("from = \"Awaiting\"")
        sourceone should include ("to = \"Authorized\"")
        sourceone should include ("on = Some(\"authorized\")")
        sourceone should include ("origin = \"derived\"")
        sourceone should include ("causalTransition = \"reserve-shipment\"")
        sourceone should include ("placement = \"derived-transition\"")
        sourceone should include ("from = \"AwaitingPayment\"")
        sourceone should include ("to = \"AwaitingShipment\"")
      }
    }

    "render the normalized fixture descriptor repeatedly" which {
      "remain byte-stable for every generated repetition count" in {
        Given("the normalized definitions from the checked-in Order/Payment/Shipment fixture")
        val definitions = CompositeStateMachineCml.definitions(_fixture_model)

        When("ScalaCheck renders the canonical descriptor repeatedly for generated counts")
        val repetitioncounts = Gen.choose(1, 24)
        val property = Prop.forAll(repetitioncounts) { count =>
          val renderings = Vector.fill(count)(CompositeStateMachineActionProgram.canonicalJson(definitions))
          renderings.distinct.size == 1 && renderings.forall(_ == renderings.head)
        }
        val check = Test.check(Test.Parameters.default.withMinSuccessfulTests(16), property)

        Then("every repeated canonical rendering is byte-identical")
        check.passed shouldBe true
      }
    }
  }

  private def _fixture_model: KaleidoxModel = {
    val model = KaleidoxModel.parseWitoutLocation(KaleidoxConfig.default, Files.readString(_fixture_path))
    if (model.errors.nonEmpty)
      fail(s"Order/Payment/Shipment fixture must parse without errors: ${model.errors.mkString(" | ")}")
    model
  }

  private val _fixture_path: Path =
    Paths.get(sys.props("user.dir")).toAbsolutePath.normalize().resolve("src/test/resources/modeler/order-payment-shipment-action-program.cml")
}
