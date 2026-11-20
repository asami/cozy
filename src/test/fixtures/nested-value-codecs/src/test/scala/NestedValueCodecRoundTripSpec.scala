import domain.value.{Branch, Envelope, Leaf}
import io.circe.parser.decode
import io.circe.syntax.*
import org.goldenport.cncf.entity.EntityStoreFieldBinding
import org.scalacheck.{Gen, Prop, Test}
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Nov. 18, 2026
 * @version Nov. 18, 2026
 * @author  ASAMI, Tomoharu
 */
final class NestedValueCodecRoundTripSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  "Generated nested value codecs" should {
    "supply the entity persistence binding and preserve ordered Japanese text" in {
      Given("a generated envelope with a present optional branch and ordered branches")
      val value = Envelope(Some(Branch(Leaf("東京"))), Vector(Branch(Leaf("二")), Branch(Leaf("一"))))
      When("the persistence binding captures the generated codecs and JSON is encoded")
      val binding = EntityStoreFieldBinding.application[Envelope]("fixture:Envelope")
      val json = value.asJson.noSpaces
      Then("the binding captures the generated value and the exact JSON order roundtrips")
      binding.applicationClassName shouldBe Some("domain.value.Envelope")
      json shouldBe """{"optional":{"leaf":{"text":"東京"}},"ordered":[{"leaf":{"text":"二"}},{"leaf":{"text":"一"}}]}"""
      decode[Envelope](json) shouldBe Right(value)
    }

    "roundtrip varied optional and ordered nested values" in {
      Given("varied text including non-ASCII and both optional states")
      val text = Gen.oneOf(Gen.alphaStr, Gen.const("日本語"), Gen.const("é"))
      val property = Prop.forAll(Gen.listOf(text), Gen.option(text)) { (items, optional) =>
        val value = Envelope(optional.map(x => Branch(Leaf(x))), items.toVector.map(x => Branch(Leaf(x))))
        decode[Envelope](value.asJson.noSpaces) == Right(value)
      }
      When("the generated codecs encode and decode each value")
      val result = Test.check(Test.Parameters.default.withMinSuccessfulTests(100), property)
      Then("all cases preserve the original value and order")
      result.passed shouldBe true
    }
  }
}
