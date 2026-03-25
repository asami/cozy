package cozy.modeler

import java.nio.file.Paths
import org.scalatest.funsuite.AnyFunSuite
import org.goldenport.kaleidox.{Config => KaleidoxConfig, Model => KaleidoxModel}

/*
 * @since   Mar. 25, 2026
 * @version Mar. 25, 2026
 * @author  ASAMI, Tomoharu
 */
class AddressValueModelSpec extends AnyFunSuite {
  private val base = Paths.get("/Users/asami/src/dev2026/simplemodeling-model").toAbsolutePath.normalize()
  private val addressCml = base.resolve("src/main/cozy/address.cml")
  private val snapshot = base.resolve("docs/journal/2026/03/address-cml-pre-validation-snapshot.cml")

  test("pre-validation snapshot builds a ValueModel") {
    val model = KaleidoxModel.load(KaleidoxConfig.default.withoutLocation, snapshot.toFile)
    val valueModel = model.getValueModel.getOrElse(fail("ValueModel is missing for snapshot"))

    assert(valueModel.classes.nonEmpty, s"snapshot ValueModel is empty")
    assert(valueModel.classes.contains("Address"), s"snapshot missing Address")
    assert(valueModel.classes.contains("CountryCode"), s"snapshot missing CountryCode")
  }

  test("address.cml keeps the same top-level ValueModel shape as the snapshot") {
    val snapshotModel = KaleidoxModel.load(KaleidoxConfig.default.withoutLocation, snapshot.toFile)
    val addressModel = KaleidoxModel.load(KaleidoxConfig.default.withoutLocation, addressCml.toFile)

    val snapshotValue = snapshotModel.getValueModel.getOrElse(fail("snapshot ValueModel is missing"))
    val addressValue = addressModel.getValueModel.getOrElse(fail("address.cml ValueModel is missing"))

    assert(addressValue.classes.keySet == snapshotValue.classes.keySet,
      s"address.cml value classes differ from snapshot: address=${addressValue.classes.keySet}, snapshot=${snapshotValue.classes.keySet}")
  }
}
