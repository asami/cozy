package cozy.modeler

import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Sep.  7, 2026
 * @version Sep.  7, 2026
 * @author  ASAMI, Tomoharu
 */
final class CompositeStateMachineProjectionMetadataSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  "Composite StateMachine projection metadata" should {
    "preserve normalized CSM declaration order and complete action provenance canonically" in {
      Given("one normalized definition containing constituents, derivations, and both action occurrence kinds")
      val definitions = Vector(_definition)

      When("the pure Cozy projection is rendered")
      val projection = CompositeStateMachineProjectionMetadata.canonicalJson(definitions)

      Then("the versioned JSON preserves every declared metadata field in fixed key and vector order")
      projection shouldBe _expected_projection

      And("JSON-sensitive text is escaped while source lines and typed operation metadata remain present")
      projection should include ("\"name\":\"Order \\\"Progress\\\"\"")
      projection should include ("\"inputBinding\":\"payment\\nsubject\"")
      CompositeStateMachineProjectionMetadata.canonicalJson(definitions) shouldBe projection
    }

    "emit the versioned empty document deterministically" in {
      Given("no normalized Composite StateMachine definitions")
      val definitions = Vector.empty[CompositeStateMachineDefinition]

      When("the pure Cozy projection is rendered twice")
      val first = CompositeStateMachineProjectionMetadata.canonicalJson(definitions)
      val second = CompositeStateMachineProjectionMetadata.canonicalJson(definitions)

      Then("both typed-empty outputs retain only the projection schema and empty definitions vector")
      first shouldBe "{\"schemaVersion\":\"cozy.cml.composite-statemachine-projection.v1\",\"definitions\":[]}"

      And("the empty projection is byte-stable as text")
      second shouldBe first
    }
  }

  private def _definition: CompositeStateMachineDefinition = {
    val action = CompositeStateMachineLogicalAction(
      identity = "capture-payment",
      kind = "OPERATION",
      operation = CompositeStateMachineOperation("OrderService", "capturePayment", Some("PaymentCommand")),
      inputBinding = Some("payment\nsubject"),
      source = CompositeStateMachineSourceIdentity(Some(30))
    )
    CompositeStateMachineDefinition(
      identity = "OrderProgress",
      name = "Order \"Progress\"",
      source = CompositeStateMachineSourceIdentity(Some(10)),
      constituents = Vector(
        CompositeStateMachineConstituent(
          role = "payment",
          stateMachine = CompositeStateMachineReference("PaymentLifecycle"),
          states = Vector("Awaiting", "Paid"),
          subject = Some(CompositeStateMachineSubject("payment", "PaymentCommand")),
          source = CompositeStateMachineSourceIdentity(Some(11))
        ),
        CompositeStateMachineConstituent(
          role = "fulfillment",
          stateMachine = CompositeStateMachineReference("FulfillmentLifecycle"),
          states = Vector("Waiting", "Shipped"),
          subject = None,
          source = CompositeStateMachineSourceIdentity(Some(12))
        )
      ),
      states = Vector(
        CompositeStateMachineState("Pending", CompositeStateMachineSourceIdentity(Some(13))),
        CompositeStateMachineState("Complete", CompositeStateMachineSourceIdentity(Some(14)))
      ),
      derivations = Vector(
        CompositeStateMachineDerivation(
          identity = "pending",
          state = "Pending",
          configuration = CompositeStateMachineConfiguration(
            Vector(
              CompositeStateMachineConfigurationBinding("payment", "Awaiting"),
              CompositeStateMachineConfigurationBinding("fulfillment", "Waiting")
            ),
            CompositeStateMachineSourceIdentity(Some(20))
          ),
          source = CompositeStateMachineSourceIdentity(Some(21))
        )
      ),
      initialConfiguration = Some(CompositeStateMachineConfiguration(
        Vector(
          CompositeStateMachineConfigurationBinding("payment", "Awaiting"),
          CompositeStateMachineConfigurationBinding("fulfillment", "Waiting")
        ),
        CompositeStateMachineSourceIdentity(Some(22))
      )),
      actions = Vector(action),
      constituentActions = Vector(CompositeStateMachineConstituentAction(
        identity = "payment-captured",
        role = "payment",
        from = "Awaiting",
        to = "Paid",
        on = "captured",
        placement = "transition",
        action = action,
        source = CompositeStateMachineSourceIdentity(Some(31))
      )),
      derivedActions = Vector(CompositeStateMachineDerivedAction(
        derivedTransition = "completed",
        from = "Pending",
        to = "Complete",
        action = action,
        source = CompositeStateMachineSourceIdentity(Some(32))
      ))
    )
  }

  private def _expected_projection: String =
    """{"schemaVersion":"cozy.cml.composite-statemachine-projection.v1","definitions":[{"identity":"OrderProgress","name":"Order \"Progress\"","source":{"line":10},"constituents":[{"role":"payment","stateMachine":{"name":"PaymentLifecycle"},"states":["Awaiting","Paid"],"subject":{"name":"payment","type":"PaymentCommand"},"source":{"line":11}},{"role":"fulfillment","stateMachine":{"name":"FulfillmentLifecycle"},"states":["Waiting","Shipped"],"subject":null,"source":{"line":12}}],"states":[{"name":"Pending","source":{"line":13}},{"name":"Complete","source":{"line":14}}],"derivations":[{"identity":"pending","state":"Pending","configuration":{"bindings":[{"role":"payment","state":"Awaiting"},{"role":"fulfillment","state":"Waiting"}],"source":{"line":20}},"source":{"line":21}}],"initialConfiguration":{"bindings":[{"role":"payment","state":"Awaiting"},{"role":"fulfillment","state":"Waiting"}],"source":{"line":22}},"actions":[{"identity":"capture-payment","kind":"OPERATION","operation":{"service":"OrderService","name":"capturePayment","inputType":"PaymentCommand"},"inputBinding":"payment\nsubject","source":{"line":30}}],"constituentActions":[{"identity":"payment-captured","role":"payment","from":"Awaiting","to":"Paid","on":"captured","placement":"transition","action":{"identity":"capture-payment","kind":"OPERATION","operation":{"service":"OrderService","name":"capturePayment","inputType":"PaymentCommand"},"inputBinding":"payment\nsubject","source":{"line":30}},"source":{"line":31}}],"derivedActions":[{"derivedTransition":"completed","from":"Pending","to":"Complete","action":{"identity":"capture-payment","kind":"OPERATION","operation":{"service":"OrderService","name":"capturePayment","inputType":"PaymentCommand"},"inputBinding":"payment\nsubject","source":{"line":30}},"source":{"line":32}}]}]}"""
}
