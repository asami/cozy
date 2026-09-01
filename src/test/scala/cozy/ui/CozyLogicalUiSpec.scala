package cozy.ui

import cozy.ui.CozyLogicalUi._
import org.scalacheck.{Gen, Prop, Test}
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Sep. 1, 2026
 * @version Sep. 1, 2026
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

  private def _right[A](value: Either[LogicalUiError, A]): A = value match {
    case Right(result) => result
    case Left(error) => fail(s"Expected a Logical UI result but received ${error.code}: ${error.reason}")
  }

  private def _left[A](value: Either[LogicalUiError, A]): LogicalUiError = value match {
    case Left(error) => error
    case Right(_) => fail("Expected a closed Logical UI failure")
  }
}
