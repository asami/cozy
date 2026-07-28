package cozy

import java.nio.file.{Files, Path, Paths}
import scala.collection.JavaConverters._
import cozy.runtime.CozySbtBridge
import play.api.libs.json.Json
import org.scalacheck.{Gen, Prop, Test}
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Apr. 23, 2026
 *  version May. 20, 2026
 *  version Jun. 27, 2026
 * @version Jul. 28, 2026
 * @author  ASAMI, Tomoharu
 */
final class BridgeContractSpec
    extends AnyWordSpec
    with Matchers
    with GivenWhenThen {
  private val _base =
    Paths.get(sys.props("user.dir")).toAbsolutePath.normalize()
  private val _contract_dir =
    _base.resolve("bridge").resolve("sbt-bridge").resolve("v1")

  "sbt-bridge v1 contract" should {
    "describe the wire contract" which {
      "provides canonical fixture files" in {
        Given("the versioned bridge contract directory")
        val files = Vector(
          "README.md",
          "contract.json",
          "request-generate.json",
          "request-rebind-generation-provenance.json",
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
        When("the canonical fixture set is inspected")

        Then("every request and response fixture is present")
        files.foreach { name =>
          Files.isRegularFile(_contract_dir.resolve(name)) shouldBe true
        }
      }

      "loads canonical request fixtures through the real bridge parser" in {
        Given("canonical request fixtures for every action declared by the v1 contract")

        When("the runtime parser loads each fixture")
        val contract = Json.parse(
          Files.readString(_contract_dir.resolve("contract.json"))
        )
        val readme = Files.readString(_contract_dir.resolve("README.md"))
        val generate = CozySbtBridge.loadRequestForTest(
          _contract_dir.resolve("request-generate.json")
        )
        val rebind = CozySbtBridge.loadRequestForTest(
          _contract_dir.resolve("request-rebind-generation-provenance.json")
        )
        val car = CozySbtBridge.loadRequestForTest(
          _contract_dir.resolve("request-package-car.json")
        )
        val sar = CozySbtBridge.loadRequestForTest(
          _contract_dir.resolve("request-package-sar.json")
        )
        val publishcar = CozySbtBridge.loadRequestForTest(
          _contract_dir.resolve("request-publish-car.json")
        )
        val publishsar = CozySbtBridge.loadRequestForTest(
          _contract_dir.resolve("request-publish-sar.json")
        )
        val publish = CozySbtBridge.loadRequestForTest(
          _contract_dir.resolve("request-publish-project.json")
        )
        val publishvideo = CozySbtBridge.loadRequestForTest(
          _contract_dir.resolve("request-publish-video.json")
        )
        val samples = CozySbtBridge.loadRequestForTest(
          _contract_dir.resolve("request-distribute-samples.json")
        )
        val warehouse = CozySbtBridge.loadRequestForTest(
          _contract_dir.resolve("request-index-warehouse.json")
        )

        Then("each fixture preserves its action and representative arguments")
        generate.version shouldBe "v1"
        generate.action shouldBe "generate"
        generate.arguments.head shouldBe "modeler-scala"
        val supportedactions = (contract \ "supportedActions").as[Vector[String]]
        supportedactions should contain(
          "rebind-generation-provenance"
        )
        supportedactions.foreach { action =>
          readme should include(s"`$action`")
        }
        rebind.action shouldBe "rebind-generation-provenance"
        rebind.arguments should contain allElementsOf Vector(
          "--delegated-provenance",
          "/tmp/delegate-work/run-0/target/cozy/generation-provenance.json",
          "--delegated-output-root",
          "/tmp/delegate-work/run-0",
          "--project-root",
          "/tmp/sample-project"
        )
        car.action shouldBe "package-car"
        car.arguments should contain allElementsOf Vector(
          "--project-dir",
          "/tmp/sample-project"
        )
        sar.action shouldBe "package-sar"
        publishcar.action shouldBe "publish-car"
        publishcar.arguments should contain allElementsOf Vector(
          "--warehouse",
          "/tmp/warehouse"
        )
        publishsar.action shouldBe "publish-sar"
        publishsar.arguments should contain allElementsOf Vector(
          "--sar",
          "/tmp/sample-subsystem.sar"
        )
        publish.action shouldBe "publish-project"
        publish.arguments should contain allElementsOf Vector("--kind", "car")
        publishvideo.action shouldBe "publish-video"
        publishvideo.arguments should contain allElementsOf Vector(
          "--warehouse",
          "/tmp/warehouse"
        )
        samples.action shouldBe "distribute-samples"
        samples.arguments should contain allElementsOf Vector(
          "--name",
          "textus-tutorial"
        )
        samples.arguments should contain allElementsOf Vector(
          "--path",
          "textus/tutorial/textus-tutorial"
        )
        samples.arguments should contain("--dry-run")
        warehouse.action shouldBe "index-warehouse"
        warehouse.arguments should contain allElementsOf Vector(
          "--maven-coordinates",
          "org.example:textus-tutorial_3"
        )
        warehouse.arguments should contain allElementsOf Vector(
          "--repository-modules",
          "textus-tutorial"
        )
        warehouse.arguments should contain allElementsOf Vector(
          "--download-samples",
          "textus-tutorial"
        )
      }

      "renders canonical success and error compatibility envelopes" in {
        Given("the canonical response fixtures")
        val successfixture = Json.parse(
          Files.readString(_contract_dir.resolve("response-success.json"))
        )
        val errorfixture = Json.parse(
          Files.readString(_contract_dir.resolve("response-error.json"))
        )

        When("the bridge renders success and error envelopes")
        val success =
          Json.parse(CozySbtBridge.renderSuccessEnvelopeForTest("generate"))
        val error = Json.parse(
          CozySbtBridge.renderErrorEnvelopeForTest(
            "generate",
            "Bridge command failed with a diagnostic message."
          )
        )

        Then("the rendered envelopes match the wire fixtures")
        success shouldBe successfixture
        error shouldBe errorfixture
      }
    }

    "resolve generation configuration" which {
      "rejects bridge values that contradict project defaults" in {
        _with_temp_dir("cozy-bridge-generation-settings") { dir =>
          Given("project-local generation version defaults")
          _write(
            dir.resolve(".cozy/config.yaml"),
            """generation:
            |  versions:
            |    cncf: 0.4.10
            |    simplemodeling_model: 0.1.7
            |    cncf_collaborator_api: 0.1.0
            |""".stripMargin
          )

          When("the owning-build bridge supplies a different CNCF target")
          val error = intercept[Exception] {
            CozySbtBridge.versionArgsForTest(
              Map("generation.versions.cncf" -> "0.4.11"),
              dir
            )
          }

          Then("generation is rejected with the typed source-conflict diagnostic")
          error.getMessage should include("CNCF_DESCRIPTOR_SOURCE_CONFLICT")
        }
      }

      "carry the selected CNCF runtime descriptor into delegated generation" in {
        Given("bridge settings with an extracted CNCF runtime descriptor")
        val descriptor = "/tmp/cncf-runtime.yaml"
        val digest = "a" * 64

        When("the bridge constructs modeler version arguments")
        val args = CozySbtBridge.versionArgsForSettingsForTest(Map(
          "generation.versions.cncf" -> "0.5.0",
          "runtime.cncf.descriptor" -> descriptor,
          "runtime.cncf.descriptor.sha256" -> digest
        ))

        Then("the descriptor path accompanies the selected CNCF version")
        args should contain allElementsOf List(
          "--cncf-version", "0.5.0",
          "--cncf-runtime-descriptor", descriptor,
          "--cncf-runtime-descriptor-sha256", digest
        )
      }

      "uses the sbt project directory as the generation config base" in {
        _with_temp_dir("cozy-bridge-generation-project-dir") { dir =>
          Given("generation defaults under the sbt consumer project")
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

          When("the bridge resolves settings for that project")
          val args = CozySbtBridge.versionArgsForSettingsForTest(
            Map("sbt.project_dir" -> projectdir.toString)
          )

          Then("the project-local generation defaults become modeler arguments")
          args should contain allElementsOf List("--cncf-version", "0.4.10")
          args should contain allElementsOf List(
            "--simplemodeling-model-version",
            "0.1.7"
          )
          args should contain allElementsOf List(
            "--cncf-collaborator-api-version",
            "0.1.0"
          )
        }
      }

      "excludes ambient global defaults from owning-build generation sources" in {
        _with_temp_dir("cozy-bridge-global-generation-default") { dir =>
          Given("a global operation default that differs from the owning-build target")
          val globalconfigdir = dir.resolve("home/.cozy").toAbsolutePath.normalize()
          val globalconfig = _write(
            globalconfigdir.resolve("config.yaml"),
            """generation:
              |  versions:
              |    cncf: 0.4.9
              |""".stripMargin
          )

          When("the bridge resolves an explicit owning-build target")
          val args = CozySbtBridge.versionArgsForDefaultFilesForTest(
            Map("generation.versions.cncf" -> "0.5.2-SNAPSHOT"),
            Vector(globalconfig),
            globalconfigdir
          )

          Then("the ambient value is ignored rather than treated as a conflicting project source")
          args should contain allElementsOf List(
            "--cncf-version",
            "0.5.2-SNAPSHOT"
          )
          args should not contain "0.4.9"
        }
      }

      "forward component module and version settings to model generation" in {
        Given("an sbt bridge generation request with build identity settings")
        val settings = Map(
          "component.module" -> "textus-scraper",
          "component.version" -> "0.1.0-SNAPSHOT"
        )

        When("the bridge converts the settings to modeler arguments")
        val args = CozySbtBridge.componentApiArgsForTest(settings)

        Then("the modeler receives both component identity values")
        args shouldBe List(
          "--component-module",
          "textus-scraper",
          "--component-version",
          "0.1.0-SNAPSHOT"
        )
      }

      "forward component identity and the selected CNCF runtime contract together" in {
        Given("an sbt bridge generation request with component and runtime settings")
        val descriptor = "/tmp/cncf-runtime.yaml"
        val digest = "b" * 64
        val settings = Map(
          "component.module" -> "textus-art-scene",
          "component.version" -> "0.1.0-SNAPSHOT",
          "generation.versions.cncf" -> "0.5.1-SNAPSHOT",
          "runtime.cncf.descriptor" -> descriptor,
          "runtime.cncf.descriptor.sha256" -> digest
        )

        When("the bridge constructs the complete modeler argument list")
        val args = CozySbtBridge.modelerArgsForSettingsForTest(settings)

        Then("component API generation and predefined Result resolution receive one coherent contract")
        args should contain allElementsOf List(
          "--component-module", "textus-art-scene",
          "--component-version", "0.1.0-SNAPSHOT",
          "--cncf-version", "0.5.1-SNAPSHOT",
          "--cncf-runtime-descriptor", descriptor,
          "--cncf-runtime-descriptor-sha256", digest
        )
      }

      "derive stable project-relative source identities across generated path segments" in {
        _with_temp_dir("cozy-bridge-source-identity-property") { projectdir =>
          Given("generated lowercase source-path segments under one owning sbt project")
          val segment = Gen.nonEmptyListOf(Gen.alphaLowerChar).map(_.mkString)
          val property = Prop.forAll(Gen.nonEmptyListOf(segment)) { segments =>
            val identity = (segments :+ "model.cml").mkString("/")
            val source = projectdir.resolve(identity)
            val args = List(
              source.toString,
              "--cncf-runtime-descriptor",
              projectdir.resolve("runtime.yaml").toString,
              "--cncf-runtime-descriptor-sha256",
              "a" * 64
            )
            CozySbtBridge.generationSourceIdentityArgsForTest(
              args,
              Map("sbt.project_dir" -> projectdir.toString)
            ) == List("--generation-source-identity", identity)
          }

          When("the production bridge derives each source identity")
          val result = Test.check(
            Test.Parameters.default.withMinSuccessfulTests(50),
            property
          )

          Then("every generated identity remains canonical and project-relative")
          result.passed shouldBe true
        }
      }
    }

    "dispatch publication requests" which {
      "dispatches publish-car through the bridge runtime" in {
        _with_temp_dir("cozy-bridge-publish-car") { dir =>
          Given("a publish-car bridge request and a valid CAR project")
          val projectdir = dir.resolve("project")
          val warehouse = dir.resolve("warehouse")
          _write_project_yaml(projectdir, "sample-component")
          val car = _build_car(
            projectdir,
            dir.resolve("input/sample.car"),
            "sample-component"
          )
          val request = _write(
            dir.resolve("request.json"),
            s"""{
             |  "version": "v1",
             |  "action": "publish-car",
             |  "arguments": [
             |    "${projectdir.toString}",
             |    "--warehouse", "${warehouse.toString}",
             |    "--name", "sample-component",
             |    "--version", "0.1.0-SNAPSHOT",
             |    "--car", "${car.toString}"
             |  ],
             |  "settings": {}
             |}
             |""".stripMargin
          )

          When("the bridge executes the request")
          CozySbtBridge.execute(List("v1", "--request", request.toString))

          Then("the snapshot CAR is published without a release catalog")
          Files.isRegularFile(
            warehouse.resolve(
              "repository/car/sample-component/0.1.0-SNAPSHOT/sample-component-0.1.0-SNAPSHOT.car"
            )
          ) shouldBe true
          Files.isRegularFile(
            warehouse.resolve("repository/catalog/car/sample-component.yaml")
          ) shouldBe false
        }
      }

      "accepts the inline request option from sbt-cozy delegates" in {
        Given("an sbt-cozy delegate request written with --request=<file>")
        _with_temp_dir("cozy-bridge-inline-request") { dir =>
          val projectdir = dir.resolve("project")
          val warehouse = dir.resolve("warehouse")
          _write_project_yaml(projectdir, "sample-inline-component")
          val car = _build_car(
            projectdir,
            dir.resolve("input/sample-inline.car"),
            "sample-inline-component"
          )
          val request = _write(
            dir.resolve("request.json"),
            s"""{
             |  "version": "v1",
             |  "action": "publish-car",
             |  "arguments": [
             |    "${projectdir.toString}",
             |    "--warehouse", "${warehouse.toString}",
             |    "--name", "sample-inline-component",
             |    "--version", "0.1.0-SNAPSHOT",
             |    "--car", "${car.toString}"
             |  ],
             |  "settings": {}
             |}
             |""".stripMargin
          )

          When("the bridge executes the inline request option")
          CozySbtBridge.execute(List("v1", s"--request=${request.toString}"))

          Then("the runtime reads the request and publishes the CAR artifact")
          Files.isRegularFile(
            warehouse.resolve(
              "repository/car/sample-inline-component/0.1.0-SNAPSHOT/sample-inline-component-0.1.0-SNAPSHOT.car"
            )
          ) shouldBe true
          And("the request does not publish a release catalog for the snapshot")
          Files.isRegularFile(
            warehouse.resolve(
              "repository/catalog/car/sample-inline-component.yaml"
            )
          ) shouldBe false
        }
      }

      "dispatches publish-sar through the bridge runtime" in {
        _with_temp_dir("cozy-bridge-publish-sar") { dir =>
          Given("a publish-sar bridge request and a prebuilt SAR")
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

          When("the bridge executes the request")
          CozySbtBridge.execute(List("v1", "--request", request.toString))

          Then("the SAR and its catalog are published")
          Files.isRegularFile(
            warehouse.resolve(
              "repository/sar/sample-subsystem/0.1.0/sample-subsystem-0.1.0.sar"
            )
          ) shouldBe true
          Files.isRegularFile(
            warehouse.resolve("repository/catalog/sar/sample-subsystem.yaml")
          ) shouldBe true
        }
      }
    }
  }

  private def _with_temp_dir(prefix: String)(body: Path => Unit): Unit = {
    val dir = Files.createTempDirectory(prefix)
    try {
      body(dir)
    } finally {
      Files
        .walk(dir)
        .iterator()
        .asScala
        .toVector
        .reverse
        .foreach(Files.deleteIfExists)
    }
  }

  private def _write(path: Path, text: String): Path = {
    Option(path.getParent).foreach(Files.createDirectories(_))
    Files.writeString(path, text)
    path
  }

  private def _build_car(
    projectdir: Path,
    path: Path,
    name: String
  ): Path = {
    val mainjar = _write(projectdir.resolve("target/component.jar"), "component")
    val abimanifest = _write(
      projectdir.resolve("src/main/car/abi-manifest.json"),
      _abi_manifest(name, "0.1.0-SNAPSHOT", name)
    )
    CarPackagingSpecSupport.buildCarWithContract(List(
      "--save", path.toString,
      "--project-dir", projectdir.toString,
      "--main-jar", mainjar.toString,
      "--name", name,
      "--version", "0.1.0-SNAPSHOT",
      "--component", name,
      "--abi-manifest", abimanifest.toString
    ))
    path
  }

  private def _abi_manifest(
    name: String,
    version: String,
    component: String
  ): String =
    s"""{
       |  "format": "cozy.car.abi-manifest.v1",
       |  "car": {
       |    "name": "$name",
       |    "version": "$version"
       |  },
       |  "abi": {
       |    "version": 1,
       |    "exports": {
       |      "components": [
       |        {
       |          "name": "$component"
       |        }
       |      ],
       |      "operations": [],
       |      "entities": []
       |    },
       |    "dependencies": []
       |  }
       |}
       |""".stripMargin

  private def _write_project_yaml(projectdir: Path, name: String): Unit = {
    _write(
      projectdir.resolve("project.yaml"),
      s"""project:
         |  kind: car
         |  name: $name
         |  component:
         |    name: $name
         |    version: 0.1.0-SNAPSHOT
         |build:
         |  cozyVersion: 0.3.1-SNAPSHOT
         |  dependencies:
         |    compile:
         |      - org.goldenport::goldenport-cncf:0.5.2-SNAPSHOT
         |packaging:
         |  kind: car
         |  car:
         |    runtime:
         |      cncf:
         |        minimum: 0.5.2-SNAPSHOT
         |        excluded: []
         |        tested:
         |          - 0.5.2-SNAPSHOT
         |""".stripMargin
    )
    _write(
      projectdir.resolve(s"src/main/cozy/${name}.cml"),
      s"# COMPONENT\n\n## ${name}\n"
    )
  }
}
