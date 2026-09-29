package cozyruntime

import cozy.ui.CozyLogicalUiRuntime._
import cozy.ui.CozyLogicalUiRuntimeValidation
import cozy.ui.CozyLogicalUiSelection._
import cozy.ui.CozyLogicalUiVocabulary._
import org.scalacheck.{Gen, Prop, Test}
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Sep. 29, 2026
 * @version Sep. 29, 2026
 * @author  ASAMI, Tomoharu
 */
final class CozyLogicalUiRuntimeAcceptanceSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  "Cozy Logical UI runtime provisional scenario" should {
    "admit a public multiple-resource List model with the exact provisional contract and typed presentation roles" in {
      Given("an external-package fixture with two planned-scenario resources and explicit provenance")
      val model = CozyLogicalUiRuntimeFixtures.listModel

      When("the public runtime model is admitted")
      val admitted = CozyLogicalUiRuntimeValidation.validate(model)

      Then("the List retains the exact minimum contract, fixture provenance, and typed title/description roles")
      admitted shouldBe Right(model)
      model.metadata.contract shouldBe minimumContract
      model.metadata.provenance.producerId shouldBe "cozy-phase-74-fixture"
      model.screen.purpose shouldBe BrowsePurpose
      model.screen.display shouldBe CollectionDisplay
      val list = model.screen.asInstanceOf[ResourceList]
      list.items.map(_.id) shouldBe Vector("resource-a", "resource-b")
      list.items.head.fields.map(_.role) shouldBe Vector(TitleRole, DescriptionRole)
    }

    "make compact selection propose navigation and resolve the matching fixture Detail by logical identities" in {
      Given("an admitted resource List and the second opaque resource identity")
      val model = CozyLogicalUiRuntimeFixtures.listModel
      val itemid = "resource-b"

      When("a compact client selects that resource")
      val transition = _right(select(model, itemid, CompactIntent))
      val detail = CozyLogicalUiRuntimeFixtures.detailFor(transition.detailRequest.resourceId)

      Then("the transition proposes navigation and the fixture Detail has matching screen/resource identity and visible values")
      transition.selection shouldBe Selection("resource-list", itemid)
      transition.detailRequest shouldBe DetailRequest("resource-detail", itemid)
      transition.placement shouldBe NavigateToDetail
      transition.pattern shouldBe NavigatePattern
      detail shouldBe CozyLogicalUiRuntimeFixtures.detailFor(itemid)
      val resolved = detail.screen.asInstanceOf[ResourceDetail]
      resolved.id shouldBe transition.detailRequest.screenId
      resolved.resourceId shouldBe transition.detailRequest.resourceId
      resolved.sections.head.fields.map(_.value) should contain theSameElementsInOrderAs Vector(
        TextValue("Blue Pencil"),
        TextValue("source:blue"),
        NumberValue(BigDecimal("2.00")),
        BooleanValue(false)
      )
    }

    "make expanded selection update the same logical detail region with the same selection and request as compact selection" in {
      Given("an admitted List whose first and second resources can be selected repeatedly")
      val model = CozyLogicalUiRuntimeFixtures.listModel

      When("an expanded client changes its selection from the first resource to the second")
      val first = _right(select(model, "resource-a", ExpandedIntent))
      val second = _right(select(model, "resource-b", ExpandedIntent))
      val compact = _right(select(model, "resource-b", CompactIntent))

      Then("both adaptive modes retain the same second-resource Selection and DetailRequest while only placement/pattern differ")
      first.selection shouldBe Selection("resource-list", "resource-a")
      second.selection shouldBe Selection("resource-list", "resource-b")
      second.detailRequest shouldBe compact.detailRequest
      second.selection shouldBe compact.selection
      second.placement shouldBe UpdateDetailRegion
      second.pattern shouldBe SelectPattern
      compact.placement shouldBe NavigateToDetail
    }

    "allow field configuration to change visible Detail fields without changing the model consumer or selection semantics" in {
      Given("a fixture Detail whose configured fields omit, add, and reorder displayable values")
      val model = CozyLogicalUiRuntimeFixtures.listModel
      val configured = Vector(
        Field("availability", "Availability", StatusRole, TextValue("available")),
        Field("product-name", "Product name", TitleRole, TextValue("Amber Notebook")),
        Field("price", "Price", ContentRole, NumberValue(BigDecimal("12.50")))
      )
      val detail = CozyLogicalUiRuntimeFixtures.configuredDetail("resource-a", configured)

      When("the unchanged List consumer selects the matching resource and the configured Detail is admitted")
      val transition = _right(select(model, "resource-a", ExpandedIntent))
      val admitted = CozyLogicalUiRuntimeValidation.validate(detail)

      Then("the logical selection remains intact while visible Detail labels and values follow the supplied configuration order")
      transition shouldBe Transition(
        Selection("resource-list", "resource-a"),
        DetailRequest("resource-detail", "resource-a"),
        UpdateDetailRegion
      )
      admitted shouldBe Right(detail)
      val fields = detail.screen.asInstanceOf[ResourceDetail].sections.head.fields
      fields.map(_.id) shouldBe Vector("availability", "product-name", "price")
      fields.map(_.value) shouldBe Vector(
        TextValue("available"),
        TextValue("Amber Notebook"),
        NumberValue(BigDecimal("12.50"))
      )
    }

    "project fake semantic productname and opaque source content through roles while retaining inert Action descriptors" in {
      Given("fixture resources with a semantic productName and opaque source content plus enabled and disabled actions")
      val model = CozyLogicalUiRuntimeFixtures.listModel

      When("an external consumer reads only presentation roles, typed values, and generic Action descriptors")
      val list = model.screen.asInstanceOf[ResourceList]
      val title = list.items.head.fields.find(_.role == TitleRole)
      val description = list.items.head.fields.find(_.role == DescriptionRole)
      val actions = list.items.map(_.actions.head)

      Then("the product name is TitleRole text, opaque content is displayable text, and Actions retain IDs/patterns without execution")
      title.map(_.value) shouldBe Some(TextValue("Amber Notebook"))
      description.map(_.value) shouldBe Some(TextValue("source:amber"))
      actions.map(_.id) shouldBe Vector("edit-resource", "edit-resource")
      actions.map(_.enabled) shouldBe Vector(true, false)
      actions.map(_.pattern) shouldBe Vector(CommandPattern, CommandPattern)
    }

    "preserve ordered selection and DetailRequest deterministically across resource permutations and adaptive modes" in {
      Given("two admitted resource order permutations and a ScalaCheck selection-index generator")
      val property = Prop.forAll(Gen.oneOf(false, true), Gen.choose(0, CozyLogicalUiRuntimeFixtures.resources.size - 1)) {
        (reverse, index) =>
          val ordered = if (reverse) CozyLogicalUiRuntimeFixtures.resources.reverse else CozyLogicalUiRuntimeFixtures.resources
          val model = CozyLogicalUiRuntimeFixtures.listFor(ordered)
          val itemid = ordered(index).id
          val compact = select(model, itemid, CompactIntent)
          val expanded = select(model, itemid, ExpandedIntent)
          val repeated = select(model, itemid, CompactIntent)
          (compact, expanded, repeated) match {
            case (Right(compacttransition), Right(expandedtransition), Right(repeatedtransition)) =>
              model.screen.asInstanceOf[ResourceList].items.map(_.id) == ordered.map(_.id) &&
                compacttransition.selection == expandedtransition.selection &&
                compacttransition.detailRequest == expandedtransition.detailRequest &&
                compacttransition == repeatedtransition
            case _ => false
          }
      }

      When("each generated resource order is admitted and selected through both adaptive intents")
      val result = Test.check(Test.Parameters.default.withMinSuccessfulTests(20), property)

      Then("input order, shared logical selection/detail identity, and repeated compact transitions remain deterministic")
      result.passed shouldBe true
    }
  }

  private def _right[A](value: Either[RuntimeError, A]): A = value match {
    case Right(result) => result
    case Left(error) => fail(s"Expected an admitted runtime value but received ${error.code}: ${error.reason}")
  }
}
