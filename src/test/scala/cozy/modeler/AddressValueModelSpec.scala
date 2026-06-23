package cozy.modeler

import java.nio.file.Paths
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.goldenport.kaleidox.{Config => KaleidoxConfig, Model => KaleidoxModel}

/*
 * @since   Mar. 25, 2026
 * @version Jun. 23, 2026
 * @author  ASAMI, Tomoharu
 */
class AddressValueModelSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  private val _base = Paths.get("/Users/asami/src/dev2026/simplemodeling-model").toAbsolutePath.normalize()
  private val _address_cml = _base.resolve("src/main/cozy/address.cml")
  private val _snapshot = _base.resolve("docs/journal/2026/03/address-cml-pre-validation-snapshot.cml")

  "Address value model parsing" should {
    "preserve the pre-validation snapshot as a ValueModel" in {
      Given("the historical address CML pre-validation snapshot")
      val model = KaleidoxModel.load(KaleidoxConfig.default.withoutLocation, _snapshot.toFile)

      When("Kaleidox loads the snapshot")
      val valuemodel = model.getValueModel.getOrElse(fail("ValueModel is missing for snapshot"))

      Then("the snapshot exposes the expected value classes")
      valuemodel.classes should not be empty
      valuemodel.classes should contain key ("Address")
      valuemodel.classes should contain key ("CountryCode")
    }

    "keep address.cml aligned with the snapshot top-level ValueModel shape" in {
      Given("the current address.cml and its historical snapshot")
      val snapshotmodel = KaleidoxModel.load(KaleidoxConfig.default.withoutLocation, _snapshot.toFile)
      val addressmodel = KaleidoxModel.load(KaleidoxConfig.default.withoutLocation, _address_cml.toFile)

      When("both files are normalized into Kaleidox value models")
      val snapshotvalue = snapshotmodel.getValueModel.getOrElse(fail("snapshot ValueModel is missing"))
      val addressvalue = addressmodel.getValueModel.getOrElse(fail("address.cml ValueModel is missing"))

      Then("the top-level value class set remains compatible")
      withClue(s"address=${addressvalue.classes.keySet}, snapshot=${snapshotvalue.classes.keySet}") {
        addressvalue.classes.keySet shouldBe snapshotvalue.classes.keySet
      }
    }
  }
}
