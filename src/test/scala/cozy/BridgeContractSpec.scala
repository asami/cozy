package cozy

import java.nio.file.{Files, Path, Paths}
import scala.collection.JavaConverters._
import cozy.runtime.CozySbtBridge
import play.api.libs.json.Json
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Apr. 23, 2026
 * @version May. 20, 2026
 * @author  ASAMI, Tomoharu
 */
final class BridgeContractSpec extends AnyWordSpec with Matchers {
  private val _base = Paths.get(sys.props("user.dir")).toAbsolutePath.normalize()
  private val _contract_dir = _base.resolve("bridge").resolve("sbt-bridge").resolve("v1")

  "sbt-bridge v1 contract" should {
    "provide canonical fixture files" in {
      val files = Vector(
        "README.md",
        "contract.json",
        "request-generate.json",
        "request-package-car.json",
        "request-package-sar.json",
        "request-publish-car.json",
        "request-publish-sar.json",
        "request-publish-project.json",
        "request-distribute-samples.json",
        "request-index-warehouse.json",
        "response-success.json",
        "response-error.json"
      )
      files.foreach { name =>
        Files.isRegularFile(_contract_dir.resolve(name)) shouldBe true
      }
    }

    "load canonical request fixtures through the real bridge parser" in {
      val generate = CozySbtBridge.loadRequestForTest(_contract_dir.resolve("request-generate.json"))
      val car = CozySbtBridge.loadRequestForTest(_contract_dir.resolve("request-package-car.json"))
      val sar = CozySbtBridge.loadRequestForTest(_contract_dir.resolve("request-package-sar.json"))
      val publishcar = CozySbtBridge.loadRequestForTest(_contract_dir.resolve("request-publish-car.json"))
      val publishsar = CozySbtBridge.loadRequestForTest(_contract_dir.resolve("request-publish-sar.json"))
      val publish = CozySbtBridge.loadRequestForTest(_contract_dir.resolve("request-publish-project.json"))
      val samples = CozySbtBridge.loadRequestForTest(_contract_dir.resolve("request-distribute-samples.json"))
      val warehouse = CozySbtBridge.loadRequestForTest(_contract_dir.resolve("request-index-warehouse.json"))

      generate.version shouldBe "v1"
      generate.action shouldBe "generate"
      generate.arguments.head shouldBe "modeler-scala"
      car.action shouldBe "package-car"
      car.arguments should contain ("--project-dir=/tmp/sample-project")
      sar.action shouldBe "package-sar"
      publishcar.action shouldBe "publish-car"
      publishcar.arguments should contain ("--warehouse=/tmp/warehouse")
      publishsar.action shouldBe "publish-sar"
      publishsar.arguments should contain ("--sar=/tmp/sample-subsystem.sar")
      publish.action shouldBe "publish-project"
      publish.arguments should contain ("--kind=car")
      samples.action shouldBe "distribute-samples"
      samples.arguments should contain ("--name=textus-tutorial")
      samples.arguments should contain ("--path=textus/tutorial/textus-tutorial")
      samples.arguments should contain ("--dry-run")
      warehouse.action shouldBe "index-warehouse"
      warehouse.arguments should contain ("--maven-coordinates=org.example:textus-tutorial_3")
      warehouse.arguments should contain ("--repository-modules=textus-tutorial")
      warehouse.arguments should contain ("--download-samples=textus-tutorial")
    }

    "render canonical success and error compatibility envelopes" in {
      val success = Json.parse(CozySbtBridge.renderSuccessEnvelopeForTest("generate"))
      val error = Json.parse(CozySbtBridge.renderErrorEnvelopeForTest("generate", "Bridge command failed with a diagnostic message."))
      val successfixture = Json.parse(Files.readString(_contract_dir.resolve("response-success.json")))
      val errorfixture = Json.parse(Files.readString(_contract_dir.resolve("response-error.json")))

      success shouldBe successfixture
      error shouldBe errorfixture
    }

    "dispatch publish-car through the bridge runtime" in {
      _with_temp_dir("cozy-bridge-publish-car") { dir =>
        val projectdir = dir.resolve("project")
        val warehouse = dir.resolve("warehouse")
        val car = _write(dir.resolve("input/sample.car"), "car-body")
        _write_project_yaml(projectdir, "sample-component")
        val request = _write(
          dir.resolve("request.json"),
          s"""{
             |  "version": "v1",
             |  "action": "publish-car",
             |  "arguments": [
             |    "${projectdir.toString}",
             |    "--warehouse=${warehouse.toString}",
             |    "--name=sample-component",
             |    "--version=0.1.0",
             |    "--car=${car.toString}"
             |  ],
             |  "settings": {}
             |}
             |""".stripMargin
        )

        CozySbtBridge.execute(List("v1", s"--request=$request"))

        Files.isRegularFile(warehouse.resolve("repository/car/sample-component/0.1.0/sample-component-0.1.0.car")) shouldBe true
        Files.isRegularFile(warehouse.resolve("repository/catalog/car/sample-component.yaml")) shouldBe true
      }
    }

    "dispatch publish-sar through the bridge runtime" in {
      _with_temp_dir("cozy-bridge-publish-sar") { dir =>
        val projectdir = dir.resolve("project")
        val warehouse = dir.resolve("warehouse")
        val sar = _write(dir.resolve("input/sample.sar"), "sar-body")
        _write_project_yaml(projectdir, "sample-subsystem")
        val request = _write(
          dir.resolve("request.json"),
          s"""{
             |  "version": "v1",
             |  "action": "publish-sar",
             |  "arguments": [
             |    "${projectdir.toString}",
             |    "--warehouse=${warehouse.toString}",
             |    "--name=sample-subsystem",
             |    "--version=0.1.0",
             |    "--sar=${sar.toString}"
             |  ],
             |  "settings": {}
             |}
             |""".stripMargin
        )

        CozySbtBridge.execute(List("v1", s"--request=$request"))

        Files.isRegularFile(warehouse.resolve("repository/sar/sample-subsystem/0.1.0/sample-subsystem-0.1.0.sar")) shouldBe true
        Files.isRegularFile(warehouse.resolve("repository/catalog/sar/sample-subsystem.yaml")) shouldBe true
      }
    }
  }

  private def _with_temp_dir(prefix: String)(body: Path => Unit): Unit = {
    val dir = Files.createTempDirectory(prefix)
    try {
      body(dir)
    } finally {
      Files.walk(dir).iterator().asScala.toVector.reverse.foreach(Files.deleteIfExists)
    }
  }

  private def _write(path: Path, text: String): Path = {
    Option(path.getParent).foreach(Files.createDirectories(_))
    Files.writeString(path, text)
    path
  }

  private def _write_project_yaml(projectdir: Path, name: String): Unit =
    _write(
      projectdir.resolve("project.yaml"),
      s"""name: $name
         |version: 0.1.0
         |packaging:
         |  kind: car
         |  car:
         |    runtime:
         |      cncf:
         |        minimum: 0.4.8
         |        excluded: []
         |        tested:
         |          - 0.4.8
         |""".stripMargin
    )
}
