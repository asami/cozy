package cozy

import com.sun.net.httpserver.{HttpExchange, HttpHandler, HttpServer}
import java.io.{ByteArrayOutputStream, PrintStream}
import java.net.InetSocketAddress
import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path}
import java.util.zip.{ZipEntry, ZipFile, ZipOutputStream}

import scala.collection.JavaConverters._

import cozy.archive.CozyArchivePackager
import org.scalatest.GivenWhenThen
import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.matchers.should.Matchers
import play.api.libs.json.{Json, JsValue}

/*
 * @since   May. 20, 2026
 *  version May. 22, 2026
 *  version Jun. 18, 2026
 * @version Jul.  8, 2026
 * @author  ASAMI, Tomoharu
 */
class CozyArchivePackagerSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  "Cozy archive packager" should {
    "write descriptor-first CAR layout" in {
    _with_temp_dir("cozy-car") { dir =>
      Given("component artifacts, CAR source content, and public entity descriptors")
      val mainjar = _write(dir.resolve("artifacts/main.jar"), "main")
      val libjar = _write(dir.resolve("artifacts/dep.jar"), "dep")
      val spijar = _write(dir.resolve("artifacts/spi.jar"), "spi")
      val defaultconf = _write(dir.resolve("conf/default.conf"), "service.timeout=10")
      val dependencymanifest = _write(dir.resolve("conf/component-dependencies.yaml"), "dependencies:\n  shared:\n    - org.postgresql:postgresql:42.7.3\n")
      _write(dir.resolve("docs/guide/intro.md"), "# intro")
      val carnote = _write(dir.resolve("src/main/car/manual/component.md"), "# component")
      val carwebdescriptor = _write(dir.resolve("src/main/car/web/web.yaml"), "apps:\n  - name: from-car\n")
      _write(dir.resolve("src/main/web/web.yaml"), "apps:\n  - name: from-web-app\n")
      val webapp = _write(dir.resolve("src/main/web/cwitter/index.html"), "<!doctype html><title>Cwitter</title>")
      val assembly = _write(dir.resolve("assembly-descriptor.yaml"), "subsystem: sample-component\ncomponents:\n  - name: sample-component\n")
      val archive = dir.resolve("out/sample.car")

      When("Cozy packages the component as a CAR")
      CozyArchivePackager.buildCar(List(
        "--save", archive.toString,
        "--main-jar", mainjar.toString,
        "--lib-jars", libjar.toString,
        "--spi-jars", spijar.toString,
        "--car-dir", carnote.getParent.getParent.toString,
        "--default-conf", defaultconf.toString,
        "--dependency-manifest", dependencymanifest.toString,
        "--web-dir", webapp.getParent.getParent.toString,
        "--assembly-descriptor", assembly.toString,
        "--name", "sample-component",
        "--version", "0.1.0",
        "--component", "sample-component",
        "--entities", "Notice:usageKind=public-content,operationKind=resource,applicationDomain=cms;SalesOrder:usage_kind=business-object,operation_kind=resource,application_domain=business"
      ))

      val entries = _zip_entries(archive)
      val descriptor = _zip_text(archive, "component-descriptor.json")
      val abimanifest = _zip_text(archive, "abi-manifest.json")
      val abijson = Json.parse(abimanifest)
      val webdescriptor = _zip_text(archive, "web/web.yaml")

      Then("descriptor-first CAR entries are written without deprecated paths")
      entries should contain ("component-descriptor.json")
      entries should contain ("abi-manifest.json")
      entries should contain ("component/main.jar")
      entries should contain ("lib/dep.jar")
      entries should contain ("spi/spi.jar")
      entries should contain ("config/default.conf")
      entries should contain ("component-dependencies.yaml")
      entries should not contain "cozy/component-dependencies.yaml"
      entries should contain ("assembly-descriptor.yaml")
      entries should contain ("web/web.yaml")
      entries should contain ("web/cwitter/index.html")
      entries should contain ("manual/component.md")
      entries should not contain "docs/guide/intro.md"
      entries should not contain "meta/manifest.json"
      entries should not contain "component.d/provider.car"

      And("CAR source web descriptors override web source descriptors")
      webdescriptor shouldBe Files.readString(carwebdescriptor)
      webdescriptor should not include "from-web-app"

      And("the component descriptor carries public entity metadata")
      descriptor should include (""""entities": [""")
      descriptor should include (""""entity": "Notice"""")
      descriptor should include (""""usageKind": "public-content"""")
      descriptor should include (""""operationKind": "resource"""")
      descriptor should include (""""applicationDomain": "cms"""")
      descriptor should include (""""entity": "SalesOrder"""")
      descriptor should include (""""usageKind": "business-object"""")
      descriptor should include (""""applicationDomain": "business"""")

      And("the generated ABI manifest carries the CAR coordinate, exported component, and exported entities")
      (abijson \ "format").as[String] shouldBe "cozy.car.abi-manifest.v1"
      (abijson \ "car" \ "name").as[String] shouldBe "sample-component"
      (abijson \ "car" \ "version").as[String] shouldBe "0.1.0"
      (abijson \ "abi" \ "exports" \ "components").as[Seq[JsValue]].map(x => (x \ "name").as[String]) should contain ("sample-component")
      (abijson \ "abi" \ "exports" \ "entities").as[Seq[JsValue]].map(x => (x \ "name").as[String]) should contain allOf ("Notice", "SalesOrder")
    }
  }

    "embed source-managed current ABI manifest and exclude historical baselines" in {
    _with_temp_dir("cozy-car-source-abi") { dir =>
      Given("a CAR source directory with current and historical ABI manifests")
      val mainjar = _write(dir.resolve("artifacts/main.jar"), "main")
      val cardir = dir.resolve("src/main/car")
      val currentabi = _write(cardir.resolve("abi-manifest.json"), _abi_manifest("sample-component", "0.1.0", "source-component"))
      _write(cardir.resolve("0.0.9/abi-manifest.json"), _abi_manifest("sample-component", "0.0.9", "old-component"))
      _write(cardir.resolve("manual/component.md"), "# component")
      val archive = dir.resolve("out/sample.car")

      When("Cozy packages the CAR")
      CozyArchivePackager.buildCar(List(
        "--save", archive.toString,
        "--main-jar", mainjar.toString,
        "--car-dir", cardir.toString,
        "--name", "sample-component",
        "--version", "0.1.0",
        "--component", "generated-component"
      ))

      val entries = _zip_entries(archive)

      Then("the current source-managed ABI manifest is embedded at the CAR top level")
      _zip_text(archive, "abi-manifest.json") shouldBe Files.readString(currentabi)

      And("historical baseline manifests are not archived as current CAR content")
      entries should not contain "0.0.9/abi-manifest.json"
      entries should contain ("manual/component.md")
    }
  }

    "embed source-managed component descriptor" in {
    _with_temp_dir("cozy-car-source-component-descriptor") { dir =>
      Given("a CAR source directory with a source-managed component descriptor")
      val mainjar = _write(dir.resolve("artifacts/main.jar"), "main")
      val cardir = dir.resolve("src/main/car")
      val sourcedescriptor = _write(
        cardir.resolve("component-descriptor.json"),
        """{
          |  "name": "sample-component",
          |  "version": "0.1.0",
          |  "component": "source-component",
          |  "extensions": {
          |    "source": "scaffold"
          |  }
          |}
          |""".stripMargin
      )
      _write(cardir.resolve("manual/component.md"), "# component")
      val archive = dir.resolve("out/sample.car")

      When("Cozy packages the CAR")
      CozyArchivePackager.buildCar(List(
        "--save", archive.toString,
        "--main-jar", mainjar.toString,
        "--car-dir", cardir.toString,
        "--name", "sample-component",
        "--version", "0.1.0",
        "--component", "source-component"
      ))

      Then("the source-managed component descriptor is embedded at the CAR top level")
      _zip_text(archive, "component-descriptor.json") shouldBe Files.readString(sourcedescriptor)

      And("the source descriptor is not duplicated as ordinary CAR source content")
      _zip_entries(archive).count(_ == "component-descriptor.json") shouldBe 1
      _zip_entries(archive) should contain ("manual/component.md")
    }
  }

    "let componentlet metadata override source-managed component descriptor" in {
    _with_temp_dir("cozy-car-componentlet-source-descriptor") { dir =>
      Given("a CAR source descriptor and packaging metadata that declares componentlets")
      val projectdir = dir.resolve("project")
      val mainjar = _write(dir.resolve("artifacts/main.jar"), "main")
      val cardir = projectdir.resolve("src/main/car")
      _write(
        cardir.resolve("component-descriptor.json"),
        """{
          |  "name": "sample-component",
          |  "version": "0.1.0",
          |  "component": "sample-component"
          |}
          |""".stripMargin
      )
      _write(
        projectdir.resolve("project.yaml"),
        """packaging:
          |  car:
          |    manifest_metadata:
          |      componentlets: notice-admin
          |      componentlet.notice-admin.kind: componentlet
          |""".stripMargin
      )
      val archive = dir.resolve("out/sample.car")

      When("Cozy packages the CAR")
      CozyArchivePackager.buildCar(List(
        "--save", archive.toString,
        "--project-dir", projectdir.toString,
        "--main-jar", mainjar.toString,
        "--name", "sample-component",
        "--version", "0.1.0",
        "--component", "sample-component"
      ))

      Then("the structured descriptor generated from componentlet metadata is embedded")
      val descriptor = _zip_text(archive, "component-descriptor.json")
      descriptor should include ("\"componentlets\"")
      descriptor should include ("\"version\":\"0.1.0\"")
      descriptor should include ("\"name\":\"notice-admin\"")
    }
  }

    "let an explicit ABI manifest override the source-managed current manifest" in {
    _with_temp_dir("cozy-car-explicit-abi") { dir =>
      Given("a CAR source manifest and an explicit ABI manifest")
      val mainjar = _write(dir.resolve("artifacts/main.jar"), "main")
      val cardir = dir.resolve("src/main/car")
      _write(cardir.resolve("abi-manifest.json"), _abi_manifest("sample-component", "0.1.0", "source-component"))
      val explicitabi = _write(dir.resolve("abi/explicit.json"), _abi_manifest("sample-component", "0.1.0", "explicit-component"))
      val archive = dir.resolve("out/sample.car")

      When("Cozy packages the CAR with --abi-manifest")
      CozyArchivePackager.buildCar(List(
        "--save", archive.toString,
        "--main-jar", mainjar.toString,
        "--car-dir", cardir.toString,
        "--abi-manifest", explicitabi.toString,
        "--name", "sample-component",
        "--version", "0.1.0",
        "--component", "generated-component"
      ))

      Then("the explicit ABI manifest is embedded")
      _zip_text(archive, "abi-manifest.json") shouldBe Files.readString(explicitabi)
    }
  }

    "reject ABI manifests whose coordinate does not match the CAR" in {
    _with_temp_dir("cozy-car-abi-coordinate") { dir =>
      Given("a source-managed ABI manifest with a stale version")
      val mainjar = _write(dir.resolve("artifacts/main.jar"), "main")
      val cardir = dir.resolve("src/main/car")
      _write(cardir.resolve("abi-manifest.json"), _abi_manifest("sample-component", "0.0.9", "source-component"))
      val archive = dir.resolve("out/sample.car")

      When("Cozy packages a different CAR version")
      val ex = intercept[Throwable] {
        CozyArchivePackager.buildCar(List(
          "--save", archive.toString,
          "--main-jar", mainjar.toString,
          "--car-dir", cardir.toString,
          "--name", "sample-component",
          "--version", "0.1.0",
          "--component", "generated-component"
        ))
      }

      Then("the stale ABI manifest is rejected before archive creation")
      ex.getMessage should include ("declares sample-component:0.0.9")
      ex.getMessage should include ("building sample-component:0.1.0")
    }
  }

    "prefer structured component descriptor override" in {
    _with_temp_dir("cozy-car-componentlet") { dir =>
      val mainjar = _write(dir.resolve("artifacts/main.jar"), "main")
      val archive = dir.resolve("out/sample.car")
      val descriptorjson =
        """{"component":{"name":"sample-component","version":"0.1.0","kind":"component","isPrimary":"true"},"componentlets":[{"name":"notice-admin","kind":"componentlet"},{"name":"public-notice","kind":"componentlet"}]}"""

      CozyArchivePackager.buildCar(List(
        "--save", archive.toString,
        "--main-jar", mainjar.toString,
        "--name", "sample-component",
        "--version", "0.1.0",
        "--component", "sample-component",
        "--extensions", s"""{"componentDescriptorJson":${Json.stringify(Json.toJson(descriptorjson))}}"""
      ))

      val descriptor = _zip_text(archive, "component-descriptor.json")
      descriptor shouldBe descriptorjson
      descriptor should include ("\"componentlets\"")
      descriptor should include ("\"name\":\"notice-admin\"")
      descriptor should include ("\"name\":\"public-notice\"")
    }
  }

    "reject structured component descriptor override without version" in {
    _with_temp_dir("cozy-car-componentlet-version") { dir =>
      val mainjar = _write(dir.resolve("artifacts/main.jar"), "main")
      val archive = dir.resolve("out/sample.car")
      val descriptorjson =
        """{"component":{"name":"sample-component","kind":"component"},"componentlets":[]}"""

      val ex = intercept[IllegalArgumentException] {
        CozyArchivePackager.buildCar(List(
          "--save", archive.toString,
          "--main-jar", mainjar.toString,
          "--name", "sample-component",
          "--version", "0.1.0",
          "--component", "sample-component",
          "--extensions", s"""{"componentDescriptorJson":${Json.stringify(Json.toJson(descriptorjson))}}"""
        ))
      }

      ex.getMessage should include ("componentDescriptorJson must declare CAR version")
    }
  }

    "read project packaging policy and write dependency manifest" in {
    _with_temp_dir("cozy-car-project-policy") { dir =>
      val projectdir = dir.resolve("project")
      val mainjar = _write(dir.resolve("artifacts/main.jar"), "main")
      val libjar = _write(dir.resolve("artifacts/dep.jar"), "dep")
      val archive = dir.resolve("out/sample.car")
      _write(
        projectdir.resolve("project.yaml"),
        """packaging:
          |  car:
          |    include_dependencies: false
          |    manifest_metadata:
          |      component: policy-component
          |    dependencies:
          |      provided:
          |        - org.goldenport:goldenport-cncf_3:0.4.8-SNAPSHOT
          |      shared:
          |        - org.postgresql:postgresql:42.7.3
          |      repositories:
          |        - maven-central
          |""".stripMargin
      )

      CozyArchivePackager.buildCar(List(
        "--save", archive.toString,
        "--project-dir", projectdir.toString,
        "--main-jar", mainjar.toString,
        "--lib-jars", libjar.toString,
        "--name", "sample-component",
        "--version", "0.1.0",
        "--component", "sample-component"
      ))

      val entries = _zip_entries(archive)
      val manifest = _zip_text(archive, "component-dependencies.yaml")
      val descriptor = _zip_text(archive, "component-descriptor.json")
      entries should contain ("component-dependencies.yaml")
      entries should not contain "cozy/component-dependencies.yaml"
      entries should not contain "lib/dep.jar"
      descriptor should include (""""component": "policy-component"""")
      manifest should include ("provided:")
      manifest should include ("shared:")
      manifest should include ("\"org.postgresql:postgresql:42.7.3\"")
    }
  }

    "write project component config into component descriptor" in {
    _with_temp_dir("cozy-car-project-component-config") { dir =>
      val projectdir = dir.resolve("project")
      val mainjar = _write(dir.resolve("artifacts/main.jar"), "main")
      val archive = dir.resolve("out/sample.car")
      _write(
        projectdir.resolve("project.yaml"),
        """project:
          |  component:
          |    config:
          |      textus.component.art-scene.datastores.application.policy: local-default
          |""".stripMargin
      )

      CozyArchivePackager.buildCar(List(
        "--save", archive.toString,
        "--project-dir", projectdir.toString,
        "--main-jar", mainjar.toString,
        "--name", "sample-component",
        "--version", "0.1.0",
        "--component", "sample-component"
      ))

      val descriptor = Json.parse(_zip_text(archive, "component-descriptor.json"))
      val config = descriptor \ "config"
      (config \ "textus.component.art-scene.datastores.application.policy").as[String] shouldBe "local-default"
    }
  }

    "accept CNCF runtime requirement while defaulting project CAR policy" in {
    _with_temp_dir("cozy-car-runtime-requirement") { dir =>
      val projectdir = dir.resolve("project")
      val mainjar = _write(dir.resolve("artifacts/main.jar"), "main")
      val libjar = _write(dir.resolve("artifacts/dep.jar"), "dep")
      val archive = dir.resolve("out/sample.car")
      _write(projectdir.resolve("src/main/web-inf/web.yaml"), "apps:\n  - name: sample-component\n")
      _write(projectdir.resolve("src/main/web-inf/form.yaml"), "expose:\n  sample-component.notice.search: public\n")
      _write(projectdir.resolve("src/main/web-inf/admin.yaml"), "web:\n  admin:\n    pages: []\n")
      _write(
        projectdir.resolve("repository/textus/runtime-catalog.yaml"),
        """schemaVersion: 1
          |baseProvided:
          |  - org.goldenport:goldenport-cncf_3
          |  - org.simplemodeling:simplemodeling-model_3
          |""".stripMargin
      )
      _write(
        projectdir.resolve("project.yaml"),
        """packaging:
          |  car:
          |    runtime:
          |      cncf:
          |        minimum: 0.4.8
          |        excluded: []
          |        tested:
          |          - 0.4.8
          |""".stripMargin
      )

      CozyArchivePackager.buildCar(List(
        "--save", archive.toString,
        "--project-dir", projectdir.toString,
        "--main-jar", mainjar.toString,
        "--lib-jars", libjar.toString,
        "--name", "sample-component",
        "--version", "0.1.0",
        "--component", "sample-component"
      ))

      val entries = _zip_entries(archive)
      entries should contain ("web/WEB-INF/web.yaml")
      entries should contain ("web/WEB-INF/form.yaml")
      entries should contain ("web/WEB-INF/admin.yaml")
      entries should not contain "web/web.yaml"
      entries should not contain "component-dependencies.yaml"
      entries should not contain "lib/dep.jar"
    }
  }

    "read CNCF runtime descriptor from lib jar without embedding dependencies" in {
    _with_temp_dir("cozy-car-runtime-descriptor-jar") { dir =>
      val projectdir = dir.resolve("project")
      val mainjar = _write(dir.resolve("artifacts/main.jar"), "main")
      val cncfjar = _write_zip(
        dir.resolve("artifacts/goldenport-cncf_3.jar"),
        "META-INF/cncf/runtime.yaml",
        """schemaVersion: 1
          |runtime: cncf
          |version: 0.4.10-SNAPSHOT
          |scalaBinaryVersion: "3"
          |module: org.goldenport:goldenport-cncf_3:0.4.10-SNAPSHOT
          |baseProvided:
          |  - org.goldenport:goldenport-cncf_3
          |  - org.typelevel:cats-core_3
          |""".stripMargin
      )
      val archive = dir.resolve("out/sample.car")
      _write(
        projectdir.resolve("project.yaml"),
        """packaging:
          |  car:
          |    runtime:
          |      cncf:
          |        minimum: 0.4.10-SNAPSHOT
          |    dependencies:
          |      shared:
          |        - org.postgresql:postgresql:42.7.3
          |""".stripMargin
      )

      val stderr = new ByteArrayOutputStream()
      Console.withErr(new PrintStream(stderr)) {
        CozyArchivePackager.buildCar(List(
          "--save", archive.toString,
          "--project-dir", projectdir.toString,
          "--main-jar", mainjar.toString,
          "--lib-jars", cncfjar.toString,
          "--name", "sample-component",
          "--version", "0.1.0",
          "--component", "sample-component"
        ))
      }

      val entries = _zip_entries(archive)
      stderr.toString(StandardCharsets.UTF_8.name()) should not include "CNCF runtime catalog is unavailable"
      entries should not contain "lib/goldenport-cncf_3.jar"
      _zip_text(archive, "component-dependencies.yaml") should include ("org.postgresql:postgresql:42.7.3")
    }
  }

    "accept CNCF runtime version above declared minimum" in {
    _with_temp_dir("cozy-car-runtime-minimum-compatible") { dir =>
      val projectdir = dir.resolve("project")
      val mainjar = _write(dir.resolve("artifacts/main.jar"), "main")
      val cncfjar = _write_zip(
        dir.resolve("artifacts/goldenport-cncf_3.jar"),
        "META-INF/cncf/runtime.yaml",
        """schemaVersion: 1
          |runtime: cncf
          |version: 0.4.10
          |module: org.goldenport:goldenport-cncf_3:0.4.10
          |""".stripMargin
      )
      val archive = dir.resolve("out/sample.car")
      _write(
        projectdir.resolve("project.yaml"),
        """packaging:
          |  car:
          |    runtime:
          |      cncf:
          |        minimum: 0.4.9
          |""".stripMargin
      )

      CozyArchivePackager.buildCar(List(
        "--save", archive.toString,
        "--project-dir", projectdir.toString,
        "--main-jar", mainjar.toString,
        "--lib-jars", cncfjar.toString,
        "--name", "sample-component",
        "--version", "0.1.0",
        "--component", "sample-component"
      ))

      Files.exists(archive) shouldBe true
    }
  }

    "reject CNCF runtime version below declared minimum" in {
    _with_temp_dir("cozy-car-runtime-minimum-too-low") { dir =>
      val projectdir = dir.resolve("project")
      val mainjar = _write(dir.resolve("artifacts/main.jar"), "main")
      val cncfjar = _write_zip(
        dir.resolve("artifacts/goldenport-cncf_3.jar"),
        "META-INF/cncf/runtime.yaml",
        """schemaVersion: 1
          |runtime: cncf
          |version: 0.4.8
          |module: org.goldenport:goldenport-cncf_3:0.4.8
          |""".stripMargin
      )
      val archive = dir.resolve("out/sample.car")
      _write(
        projectdir.resolve("project.yaml"),
        """packaging:
          |  car:
          |    runtime:
          |      cncf:
          |        minimum: 0.4.9
          |""".stripMargin
      )

      val error = intercept[RuntimeException] {
        CozyArchivePackager.buildCar(List(
          "--save", archive.toString,
          "--project-dir", projectdir.toString,
          "--main-jar", mainjar.toString,
          "--lib-jars", cncfjar.toString,
          "--name", "sample-component",
          "--version", "0.1.0",
          "--component", "sample-component"
        ))
      }

      error.getMessage should include ("below")
      error.getMessage should include ("0.4.8")
      error.getMessage should include ("0.4.9")
    }
  }

    "reject CNCF runtime version above declared maximum" in {
    _with_temp_dir("cozy-car-runtime-maximum-exceeded") { dir =>
      val projectdir = dir.resolve("project")
      val mainjar = _write(dir.resolve("artifacts/main.jar"), "main")
      val cncfjar = _write_zip(
        dir.resolve("artifacts/goldenport-cncf_3.jar"),
        "META-INF/cncf/runtime.yaml",
        """schemaVersion: 1
          |runtime: cncf
          |version: 0.4.10
          |module: org.goldenport:goldenport-cncf_3:0.4.10
          |""".stripMargin
      )
      val archive = dir.resolve("out/sample.car")
      _write(
        projectdir.resolve("project.yaml"),
        """packaging:
          |  car:
          |    runtime:
          |      cncf:
          |        maximum: 0.4.9
          |""".stripMargin
      )

      val error = intercept[RuntimeException] {
        CozyArchivePackager.buildCar(List(
          "--save", archive.toString,
          "--project-dir", projectdir.toString,
          "--main-jar", mainjar.toString,
          "--lib-jars", cncfjar.toString,
          "--name", "sample-component",
          "--version", "0.1.0",
          "--component", "sample-component"
        ))
      }

      error.getMessage should include ("maximum")
      error.getMessage should include ("0.4.10")
      error.getMessage should include ("0.4.9")
    }
  }

    "reject excluded CNCF runtime version" in {
    _with_temp_dir("cozy-car-runtime-excluded") { dir =>
      val projectdir = dir.resolve("project")
      val mainjar = _write(dir.resolve("artifacts/main.jar"), "main")
      val cncfjar = _write_zip(
        dir.resolve("artifacts/goldenport-cncf_3.jar"),
        "META-INF/cncf/runtime.yaml",
        """schemaVersion: 1
          |runtime: cncf
          |version: 0.4.10
          |module: org.goldenport:goldenport-cncf_3:0.4.10
          |""".stripMargin
      )
      val archive = dir.resolve("out/sample.car")
      _write(
        projectdir.resolve("project.yaml"),
        """packaging:
          |  car:
          |    runtime:
          |      cncf:
          |        excluded:
          |          - 0.4.10
          |""".stripMargin
      )

      val error = intercept[RuntimeException] {
        CozyArchivePackager.buildCar(List(
          "--save", archive.toString,
          "--project-dir", projectdir.toString,
          "--main-jar", mainjar.toString,
          "--lib-jars", cncfjar.toString,
          "--name", "sample-component",
          "--version", "0.1.0",
          "--component", "sample-component"
        ))
      }

      error.getMessage should include ("excluded")
      error.getMessage should include ("0.4.10")
    }
  }

    "reject CNCF runtime tested list that omits resolved runtime descriptor" in {
    _with_temp_dir("cozy-car-runtime-tested-mismatch") { dir =>
      val projectdir = dir.resolve("project")
      val mainjar = _write(dir.resolve("artifacts/main.jar"), "main")
      val cncfjar = _write_zip(
        dir.resolve("artifacts/goldenport-cncf_3.jar"),
        "META-INF/cncf/runtime.yaml",
        """schemaVersion: 1
          |runtime: cncf
          |version: 0.4.10
          |module: org.goldenport:goldenport-cncf_3:0.4.10
          |baseProvided:
          |  - org.goldenport:goldenport-cncf_3
          |""".stripMargin
      )
      val archive = dir.resolve("out/sample.car")
      _write(
        projectdir.resolve("project.yaml"),
        """packaging:
          |  car:
          |    runtime:
          |      cncf:
          |        tested:
          |          - 0.4.9
          |""".stripMargin
      )

      val error = intercept[RuntimeException] {
        CozyArchivePackager.buildCar(List(
          "--save", archive.toString,
          "--project-dir", projectdir.toString,
          "--main-jar", mainjar.toString,
          "--lib-jars", cncfjar.toString,
          "--name", "sample-component",
          "--version", "0.1.0",
          "--component", "sample-component"
        ))
      }

      error.getMessage should include ("packaging.car.runtime.cncf.tested")
      error.getMessage should include ("0.4.10")
    }
  }

    "prefer explicit runtime catalog URL over runtime jar descriptor" in {
    _with_temp_dir("cozy-car-runtime-catalog-url") { dir =>
      val projectdir = dir.resolve("project")
      val mainjar = _write(dir.resolve("artifacts/main.jar"), "main")
      val cncfjar = _write_zip(
        dir.resolve("artifacts/goldenport-cncf_3.jar"),
        "META-INF/cncf/runtime.yaml",
        """schemaVersion: 1
          |runtime: cncf
          |version: 0.4.10-SNAPSHOT
          |baseProvided:
          |  - org.typelevel:cats-core_3
          |""".stripMargin
      )
      val archive = dir.resolve("out/sample.car")
      val server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0)
      server.createContext("/runtime-catalog.yaml", new HttpHandler {
        def handle(exchange: HttpExchange): Unit = {
          val body =
            """schemaVersion: 1
              |baseProvided:
              |  - org.postgresql:postgresql
              |""".stripMargin
          val bytes = body.getBytes(StandardCharsets.UTF_8)
          exchange.sendResponseHeaders(200, bytes.length)
          val out = exchange.getResponseBody
          try out.write(bytes)
          finally out.close()
        }
      })
      server.start()
      try {
        _write(
          projectdir.resolve("project.yaml"),
          s"""packaging:
             |  car:
             |    runtime:
             |      cncf:
             |        minimum: 0.4.10-SNAPSHOT
             |        catalog: http://127.0.0.1:${server.getAddress.getPort}/runtime-catalog.yaml
             |    dependencies:
             |      shared:
             |        - org.postgresql:postgresql:42.7.3
             |""".stripMargin
        )

        val ex = intercept[Throwable] {
          CozyArchivePackager.buildCar(List(
            "--save", archive.toString,
            "--project-dir", projectdir.toString,
            "--main-jar", mainjar.toString,
            "--lib-jars", cncfjar.toString,
            "--name", "sample-component",
            "--version", "0.1.0",
            "--component", "sample-component"
          ))
        }
        ex.getMessage should include ("base-provided")
        ex.getMessage should include ("org.postgresql:postgresql:42.7.3")
      } finally {
        server.stop(0)
      }
    }
  }

    "embed dependency jars only when project policy enables them" in {
    _with_temp_dir("cozy-car-include-dependencies") { dir =>
      val projectdir = dir.resolve("project")
      val mainjar = _write(dir.resolve("artifacts/main.jar"), "main")
      val libjar = _write(dir.resolve("artifacts/dep.jar"), "dep")
      val archive = dir.resolve("out/sample.car")
      _write(
        projectdir.resolve("project.yaml"),
        """packaging:
          |  car:
          |    include_dependencies: true
          |""".stripMargin
      )

      CozyArchivePackager.buildCar(List(
        "--save", archive.toString,
        "--project-dir", projectdir.toString,
        "--main-jar", mainjar.toString,
        "--lib-jars", libjar.toString,
        "--name", "sample-component",
        "--version", "0.1.0",
        "--component", "sample-component"
      ))

      val entries = _zip_entries(archive)
      entries should contain ("lib/dep.jar")
    }
  }

    "write only component-owned dependencies with CNCF runtime requirement" in {
    _with_temp_dir("cozy-car-component-owned-deps") { dir =>
      val projectdir = dir.resolve("project")
      val mainjar = _write(dir.resolve("artifacts/main.jar"), "main")
      val archive = dir.resolve("out/sample.car")
      _write(
        projectdir.resolve("repository/textus/runtime-catalog.yaml"),
        """schemaVersion: 1
          |baseProvided:
          |  - org.goldenport:goldenport-cncf_3
          |  - org.typelevel:cats-core_3
          |""".stripMargin
      )
      _write(
        projectdir.resolve("project.yaml"),
        """packaging:
          |  car:
          |    runtime:
          |      cncf:
          |        minimum: 0.4.8
          |    dependencies:
          |      shared:
          |        - org.postgresql:postgresql:42.7.3
          |      local:
          |        - com.example:legacy-driver:1.2.0
          |      repositories:
          |        - maven-central
          |""".stripMargin
      )

      CozyArchivePackager.buildCar(List(
        "--save", archive.toString,
        "--project-dir", projectdir.toString,
        "--main-jar", mainjar.toString,
        "--name", "sample-component",
        "--version", "0.1.0",
        "--component", "sample-component"
      ))

      val manifest = _zip_text(archive, "component-dependencies.yaml")
      manifest should not include "goldenport-cncf"
      manifest should not include "cats-core"
      manifest should include ("\"org.postgresql:postgresql:42.7.3\"")
      manifest should include ("\"com.example:legacy-driver:1.2.0\"")
      manifest should include ("repositories:")
    }
  }

    "reject dependencies already provided by CNCF runtime catalog" in {
    _with_temp_dir("cozy-car-base-provided-overlap") { dir =>
      val projectdir = dir.resolve("project")
      val mainjar = _write(dir.resolve("artifacts/main.jar"), "main")
      val archive = dir.resolve("out/sample.car")
      _write(
        projectdir.resolve("repository/textus/runtime-catalog.yaml"),
        """schemaVersion: 1
          |baseProvided:
          |  - org.typelevel:cats-core_3
          |""".stripMargin
      )
      _write(
        projectdir.resolve("project.yaml"),
        """packaging:
          |  car:
          |    runtime:
          |      cncf:
          |        minimum: 0.4.8
          |    dependencies:
          |      shared:
          |        - org.typelevel:cats-core_3:2.10.0
          |""".stripMargin
      )

      val ex = intercept[Throwable] {
        CozyArchivePackager.buildCar(List(
          "--save", archive.toString,
          "--project-dir", projectdir.toString,
          "--main-jar", mainjar.toString,
          "--name", "sample-component",
          "--version", "0.1.0",
          "--component", "sample-component"
        ))
      }
      ex.getMessage should include ("base-provided")
      ex.getMessage should include ("org.typelevel:cats-core_3:2.10.0")
    }
  }

    "prefer exported CNCF runtime catalog from configured runtime project" in {
    _with_temp_dir("cozy-car-exported-runtime-catalog") { dir =>
      val projectdir = dir.resolve("project")
      val runtimedir = dir.resolve("cncf-runtime")
      val mainjar = _write(dir.resolve("artifacts/main.jar"), "main")
      val archive = dir.resolve("out/sample.car")
      _write(
        runtimedir.resolve("target/cncf.d/runtime-catalog.yaml"),
        """schemaVersion: 1
          |baseProvided:
          |  - org.typelevel:cats-core_3
          |""".stripMargin
      )
      _write(
        projectdir.resolve("repository/textus/runtime-catalog.yaml"),
        """schemaVersion: 1
          |baseProvided:
          |  - org.example:older-runtime-entry
          |""".stripMargin
      )
      _write(
        projectdir.resolve("project.yaml"),
        """packaging:
          |  car:
          |    runtime:
          |      cncf:
          |        minimum: 0.4.8
          |        project_dir: ../cncf-runtime
          |    dependencies:
          |      shared:
          |        - org.typelevel:cats-core_3:2.10.0
          |""".stripMargin
      )

      val ex = intercept[Throwable] {
        CozyArchivePackager.buildCar(List(
          "--save", archive.toString,
          "--project-dir", projectdir.toString,
          "--main-jar", mainjar.toString,
          "--name", "sample-component",
          "--version", "0.1.0",
          "--component", "sample-component"
        ))
      }
      ex.getMessage should include ("base-provided")
      ex.getMessage should include ("org.typelevel:cats-core_3:2.10.0")
    }
  }

    "warn and continue when CNCF runtime catalog is unavailable" in {
    _with_temp_dir("cozy-car-missing-runtime-catalog") { dir =>
      val projectdir = dir.resolve("project")
      val mainjar = _write(dir.resolve("artifacts/main.jar"), "main")
      val archive = dir.resolve("out/sample.car")
      _write(
        projectdir.resolve("project.yaml"),
        """packaging:
          |  car:
          |    runtime:
          |      cncf:
          |        minimum: 0.4.8
          |        version: 0.4.8
          |    dependencies:
          |      shared:
          |        - org.postgresql:postgresql:42.7.3
          |""".stripMargin
      )
      val stderr = new ByteArrayOutputStream()
      Console.withErr(new PrintStream(stderr)) {
        CozyArchivePackager.buildCar(List(
          "--save", archive.toString,
          "--project-dir", projectdir.toString,
          "--main-jar", mainjar.toString,
          "--name", "sample-component",
          "--version", "0.1.0",
          "--component", "sample-component"
        ))
      }

      Files.isRegularFile(archive) shouldBe true
      stderr.toString(StandardCharsets.UTF_8.name()) should include ("CNCF runtime catalog is unavailable")
    }
  }

    "query configured CNCF command for runtime descriptor" in {
    _with_temp_dir("cozy-car-runtime-descriptor-command") { dir =>
      val projectdir = dir.resolve("project")
      val mainjar = _write(dir.resolve("artifacts/main.jar"), "main")
      val archive = dir.resolve("out/sample.car")
      val command = _write(
        dir.resolve("bin/cncf-descriptor"),
        """#!/bin/sh
          |cat <<'EOF'
          |schemaVersion: 1
          |runtime: cncf
          |version: 0.4.10-SNAPSHOT
          |baseProvided:
          |  - org.typelevel:cats-core_3
          |EOF
          |""".stripMargin
      )
      command.toFile.setExecutable(true)
      _write(
        projectdir.resolve("project.yaml"),
        s"""packaging:
           |  car:
           |    runtime:
           |      cncf:
           |        minimum: 0.4.10-SNAPSHOT
           |        command: ${command.toAbsolutePath}
           |    dependencies:
           |      shared:
           |        - org.typelevel:cats-core_3:2.10.0
           |""".stripMargin
      )

      val ex = intercept[Throwable] {
        CozyArchivePackager.buildCar(List(
          "--save", archive.toString,
          "--project-dir", projectdir.toString,
          "--main-jar", mainjar.toString,
          "--name", "sample-component",
          "--version", "0.1.0",
          "--component", "sample-component"
        ))
      }
      ex.getMessage should include ("base-provided")
      ex.getMessage should include ("org.typelevel:cats-core_3:2.10.0")
    }
  }

    "write descriptor at SAR top level" in {
    _with_temp_dir("cozy-sar") { dir =>
      val sourcedir = dir.resolve("src")
      _write(sourcedir.resolve("subsystem-descriptor.yaml"), "subsystem: textus-identity\n")
      val extension = _write(dir.resolve("ext/grpc.jar"), "grpc")
      val appconf = _write(dir.resolve("conf/application.conf"), "env=dev")
      val archive = dir.resolve("out/sample.sar")

      CozyArchivePackager.buildSar(List(
        "--save", archive.toString,
        "--source-dir", sourcedir.toString,
        "--source-files", "subsystem-descriptor.yaml",
        "--extension-jars", extension.toString,
        "--application-conf", appconf.toString
      ))

      val entries = _zip_entries(archive)
      entries should contain ("subsystem-descriptor.yaml")
      entries should contain ("extension/grpc.jar")
      entries should contain ("config/application.conf")
      entries should not contain "subsystem/subsystem-descriptor.yaml"
      entries should not contain "meta/manifest.json"
    }
  }

  }

  private def _with_temp_dir[A](prefix: String)(f: Path => A): A = {
    val dir = Files.createTempDirectory(prefix)
    try f(dir)
    finally _delete_tree(dir)
  }

  private def _write(path: Path, content: String): Path = {
    Option(path.getParent).foreach(Files.createDirectories(_))
    Files.write(path, content.getBytes("UTF-8"))
    path
  }

  private def _write_zip(
    path: Path,
    entryname: String,
    content: String
  ): Path = {
    Option(path.getParent).foreach(Files.createDirectories(_))
    val zip = new ZipOutputStream(Files.newOutputStream(path))
    try {
      zip.putNextEntry(new ZipEntry(entryname))
      zip.write(content.getBytes(StandardCharsets.UTF_8))
      zip.closeEntry()
    } finally {
      zip.close()
    }
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

  private def _zip_entries(path: Path): Set[String] = {
    val zip = new ZipFile(path.toFile)
    try zip.entries().asScala.map(_.getName).toSet
    finally zip.close()
  }

  private def _zip_text(path: Path, entryname: String): String = {
    val zip = new ZipFile(path.toFile)
    try {
      val entry = zip.getEntry(entryname)
      val in = zip.getInputStream(entry)
      try scala.io.Source.fromInputStream(in, "UTF-8").mkString
      finally in.close()
    } finally {
      zip.close()
    }
  }

  private def _delete_tree(path: Path): Unit =
    if (Files.exists(path)) {
      val stream = Files.walk(path)
      try {
        stream.iterator().asScala.toVector.sortBy(_.getNameCount)(Ordering[Int].reverse).foreach(Files.deleteIfExists(_))
      } finally {
        stream.close()
      }
    }
}
