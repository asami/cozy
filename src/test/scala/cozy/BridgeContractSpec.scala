package cozy

import java.nio.file.{Files, Path, Paths}
import scala.collection.JavaConverters._
import cozy.runtime.CozySbtBridge
import play.api.libs.json.Json
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Apr. 23, 2026
 *  version May. 20, 2026
 * @version Jun. 27, 2026
 * @author  ASAMI, Tomoharu
 */
final class BridgeContractSpec extends AnyWordSpec with Matchers with GivenWhenThen {
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
        "request-publish-video.json",
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
      val publishvideo = CozySbtBridge.loadRequestForTest(_contract_dir.resolve("request-publish-video.json"))
      val samples = CozySbtBridge.loadRequestForTest(_contract_dir.resolve("request-distribute-samples.json"))
      val warehouse = CozySbtBridge.loadRequestForTest(_contract_dir.resolve("request-index-warehouse.json"))

      generate.version shouldBe "v1"
      generate.action shouldBe "generate"
      generate.arguments.head shouldBe "modeler-scala"
      car.action shouldBe "package-car"
      car.arguments should contain allElementsOf Vector("--project-dir", "/tmp/sample-project")
      sar.action shouldBe "package-sar"
      publishcar.action shouldBe "publish-car"
      publishcar.arguments should contain allElementsOf Vector("--warehouse", "/tmp/warehouse")
      publishsar.action shouldBe "publish-sar"
      publishsar.arguments should contain allElementsOf Vector("--sar", "/tmp/sample-subsystem.sar")
      publish.action shouldBe "publish-project"
      publish.arguments should contain allElementsOf Vector("--kind", "car")
      publishvideo.action shouldBe "publish-video"
      publishvideo.arguments should contain allElementsOf Vector("--warehouse", "/tmp/warehouse")
      samples.action shouldBe "distribute-samples"
      samples.arguments should contain allElementsOf Vector("--name", "textus-tutorial")
      samples.arguments should contain allElementsOf Vector("--path", "textus/tutorial/textus-tutorial")
      samples.arguments should contain ("--dry-run")
      warehouse.action shouldBe "index-warehouse"
      warehouse.arguments should contain allElementsOf Vector("--maven-coordinates", "org.example:textus-tutorial_3")
      warehouse.arguments should contain allElementsOf Vector("--repository-modules", "textus-tutorial")
      warehouse.arguments should contain allElementsOf Vector("--download-samples", "textus-tutorial")
    }

    "render canonical success and error compatibility envelopes" in {
      val success = Json.parse(CozySbtBridge.renderSuccessEnvelopeForTest("generate"))
      val error = Json.parse(CozySbtBridge.renderErrorEnvelopeForTest("generate", "Bridge command failed with a diagnostic message."))
      val successfixture = Json.parse(Files.readString(_contract_dir.resolve("response-success.json")))
      val errorfixture = Json.parse(Files.readString(_contract_dir.resolve("response-error.json")))

      success shouldBe successfixture
      error shouldBe errorfixture
    }

    "resolve generation version settings with bridge overrides taking precedence" in {
      _with_temp_dir("cozy-bridge-generation-settings") { dir =>
        _write(
          dir.resolve(".cozy/config.yaml"),
          """generation:
            |  versions:
            |    cncf: 0.4.10
            |    simplemodeling_model: 0.1.7
            |    cncf_collaborator_api: 0.1.0
            |""".stripMargin
        )

        val args = CozySbtBridge.versionArgsForTest(
          Map("generation.versions.cncf" -> "0.4.11"),
          dir
        )

        args should contain allElementsOf List("--cncf-version", "0.4.11")
        args should contain allElementsOf List("--simplemodeling-model-version", "0.1.7")
        args should contain allElementsOf List("--cncf-collaborator-api-version", "0.1.0")
      }
    }

    "use sbt project dir setting as generation config base" in {
      _with_temp_dir("cozy-bridge-generation-project-dir") { dir =>
        val projectdir = dir.resolve("consumer")
        _write(
          projectdir.resolve(".cozy/config.yaml"),
          """generation:
            |  versions:
            |    cncf: 0.4.10
            |    simplemodeling_model: 0.1.7
            |    cncf_collaborator_api: 0.1.0
            |""".stripMargin
        )

        val args = CozySbtBridge.versionArgsForSettingsForTest(
          Map(
            "sbt.project_dir" -> projectdir.toString,
            "generation.versions.cncf" -> "0.4.11"
          )
        )

        args should contain allElementsOf List("--cncf-version", "0.4.11")
        args should contain allElementsOf List("--simplemodeling-model-version", "0.1.7")
        args should contain allElementsOf List("--cncf-collaborator-api-version", "0.1.0")
      }
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
             |    "--warehouse", "${warehouse.toString}",
             |    "--name", "sample-component",
             |    "--version", "0.1.0",
             |    "--car", "${car.toString}"
             |  ],
             |  "settings": {}
             |}
             |""".stripMargin
        )

        CozySbtBridge.execute(List("v1", "--request", request.toString))

        Files.isRegularFile(warehouse.resolve("repository/car/sample-component/0.1.0/sample-component-0.1.0.car")) shouldBe true
        Files.isRegularFile(warehouse.resolve("repository/catalog/car/sample-component.yaml")) shouldBe true
      }
    }

    "accept inline request option from sbt-cozy delegates" in {
      Given("an sbt-cozy delegate request written with --request=<file>")
      _with_temp_dir("cozy-bridge-inline-request") { dir =>
        val projectdir = dir.resolve("project")
        val warehouse = dir.resolve("warehouse")
        val car = _write(dir.resolve("input/sample-inline.car"), "car-body")
        _write_project_yaml(projectdir, "sample-inline-component")
        val request = _write(
          dir.resolve("request.json"),
          s"""{
             |  "version": "v1",
             |  "action": "publish-car",
             |  "arguments": [
             |    "${projectdir.toString}",
             |    "--warehouse", "${warehouse.toString}",
             |    "--name", "sample-inline-component",
             |    "--version", "0.1.0",
             |    "--car", "${car.toString}"
             |  ],
             |  "settings": {}
             |}
             |""".stripMargin
        )

        When("the bridge executes the inline request option")
        CozySbtBridge.execute(List("v1", s"--request=${request.toString}"))

        Then("the runtime reads the request and publishes the CAR artifact")
        Files.isRegularFile(warehouse.resolve("repository/car/sample-inline-component/0.1.0/sample-inline-component-0.1.0.car")) shouldBe true
        And("the artifact catalog is published through the same request")
        Files.isRegularFile(warehouse.resolve("repository/catalog/car/sample-inline-component.yaml")) shouldBe true
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
             |    "--warehouse", "${warehouse.toString}",
             |    "--name", "sample-subsystem",
             |    "--version", "0.1.0",
             |    "--sar", "${sar.toString}"
             |  ],
             |  "settings": {}
             |}
             |""".stripMargin
        )

        CozySbtBridge.execute(List("v1", "--request", request.toString))

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
