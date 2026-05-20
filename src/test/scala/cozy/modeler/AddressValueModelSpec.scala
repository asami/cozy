package cozy.modeler

import java.nio.file.Paths
import org.scalatest.funsuite.AnyFunSuite
import org.goldenport.kaleidox.{Config => KaleidoxConfig, Model => KaleidoxModel}

/*
 * @since   Mar. 25, 2026
 * @version May. 20, 2026
 * @author  ASAMI, Tomoharu
 */
class AddressValueModelSpec extends AnyFunSuite {
  private val _base = Paths.get("/Users/asami/src/dev2026/simplemodeling-model").toAbsolutePath.normalize()
  private val _address_cml = _base.resolve("src/main/cozy/address.cml")
  private val _snapshot = _base.resolve("docs/journal/2026/03/address-cml-pre-validation-snapshot.cml")

  test("pre-validation snapshot builds a ValueModel") {
    val model = KaleidoxModel.load(KaleidoxConfig.default.withoutLocation, _snapshot.toFile)
    val valuemodel = model.getValueModel.getOrElse(fail("ValueModel is missing for snapshot"))

    assert(valuemodel.classes.nonEmpty, s"snapshot ValueModel is empty")
    assert(valuemodel.classes.contains("Address"), s"snapshot missing Address")
    assert(valuemodel.classes.contains("CountryCode"), s"snapshot missing CountryCode")
  }

  test("address.cml keeps the same top-level ValueModel shape as the snapshot") {
    val snapshotmodel = KaleidoxModel.load(KaleidoxConfig.default.withoutLocation, _snapshot.toFile)
    val addressmodel = KaleidoxModel.load(KaleidoxConfig.default.withoutLocation, _address_cml.toFile)

    val snapshotvalue = snapshotmodel.getValueModel.getOrElse(fail("snapshot ValueModel is missing"))
    val addressvalue = addressmodel.getValueModel.getOrElse(fail("address.cml ValueModel is missing"))

    assert(addressvalue.classes.keySet == snapshotvalue.classes.keySet,
      s"address.cml value classes differ from snapshot: address=${addressvalue.classes.keySet}, snapshot=${snapshotvalue.classes.keySet}")
  }
}
