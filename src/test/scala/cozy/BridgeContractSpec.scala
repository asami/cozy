package cozy

import java.nio.file.{Files, Path, Paths}
import play.api.libs.json.Json
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Apr. 23, 2026
 * @version May. 13, 2026
 * @author  ASAMI, Tomoharu
 */
final class BridgeContractSpec extends AnyWordSpec with Matchers {
  private val base = Paths.get(sys.props("user.dir")).toAbsolutePath.normalize()
  private val contractDir = base.resolve("bridge").resolve("sbt-bridge").resolve("v1")

  "sbt-bridge v1 contract" should {
    "provide canonical fixture files" in {
      val files = Vector(
        "README.md",
        "contract.json",
        "request-generate.json",
        "request-package-car.json",
        "request-package-sar.json",
        "request-publish-project.json",
        "request-distribute-samples.json",
        "request-index-warehouse.json",
        "response-success.json",
        "response-error.json"
      )
      files.foreach { name =>
        Files.isRegularFile(contractDir.resolve(name)) shouldBe true
      }
    }

    "load canonical request fixtures through the real bridge parser" in {
      val generate = CozySbtBridge.loadRequestForTest(contractDir.resolve("request-generate.json"))
      val car = CozySbtBridge.loadRequestForTest(contractDir.resolve("request-package-car.json"))
      val sar = CozySbtBridge.loadRequestForTest(contractDir.resolve("request-package-sar.json"))
      val publish = CozySbtBridge.loadRequestForTest(contractDir.resolve("request-publish-project.json"))
      val samples = CozySbtBridge.loadRequestForTest(contractDir.resolve("request-distribute-samples.json"))
      val warehouse = CozySbtBridge.loadRequestForTest(contractDir.resolve("request-index-warehouse.json"))

      generate.version shouldBe "v1"
      generate.action shouldBe "generate"
      generate.arguments.head shouldBe "modeler-scala"
      car.action shouldBe "package-car"
      sar.action shouldBe "package-sar"
      publish.action shouldBe "publish-project"
      publish.arguments should contain ("--kind=car")
      samples.action shouldBe "distribute-samples"
      samples.arguments should contain ("--name=textus-tutorial")
      warehouse.action shouldBe "index-warehouse"
      warehouse.arguments should contain ("--maven-coordinates=org.example:textus-tutorial_3")
      warehouse.arguments should contain ("--repository-modules=textus-tutorial")
      warehouse.arguments should contain ("--download-samples=textus-tutorial")
    }

    "render canonical success and error compatibility envelopes" in {
      val success = Json.parse(CozySbtBridge.renderSuccessEnvelopeForTest("generate"))
      val error = Json.parse(CozySbtBridge.renderErrorEnvelopeForTest("generate", "Bridge command failed with a diagnostic message."))
      val successFixture = Json.parse(Files.readString(contractDir.resolve("response-success.json")))
      val errorFixture = Json.parse(Files.readString(contractDir.resolve("response-error.json")))

      success shouldBe successFixture
      error shouldBe errorFixture
    }
  }
}
