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
import play.api.libs.json.{Json, JsObject, JsValue}

/*
 * @since   May. 20, 2026
 *  version May. 22, 2026
 *  version Jun. 18, 2026
 * @version Jul. 31, 2026
 * @author  ASAMI, Tomoharu
 */
class CozyArchivePackagerSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  "Cozy archive packager" should {
    "assemble descriptor-first CAR surfaces" which {
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
      val assembly = _write(
        dir.resolve("assembly-descriptor.yaml"),
        """subsystem: sample-component
          |version: 0.1.0
          |components:
          |  - name: sample-component
          |    version: 0.1.0
          |""".stripMargin
      )
      val archive = dir.resolve("out/sample.car")

      When("Cozy packages the component as a CAR")
      CarPackagingSpecSupport.buildCarWithContract(List(
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

    "project a catalog-derived CML component style snapshot into descriptor schema v2" in {
    _with_temp_dir("cozy-car-component-style") { dir =>
      Given("one generated CML metadata document with an admitted style snapshot")
      val mainjar = _write(dir.resolve("artifacts/main.jar"), "main")
      val metadata = _write(dir.resolve("target/cozy/model-metadata.json"), _model_metadata_with_component_style)
      val archive = dir.resolve("out/sample.car")

      When("Cozy packages the generated CML component")
      CarPackagingSpecSupport.buildCarWithContract(List(
        "--save", archive.toString,
        "--main-jar", mainjar.toString,
        "--model-metadata", metadata.toString,
        "--name", "sample-component",
        "--version", "0.1.0",
        "--component", "sample-component"
      ))

      val descriptor = Json.parse(_zip_text(archive, "component-descriptor.json"))

      Then("the packaged descriptor has one exact catalog-derived schema-v2 snapshot")
      (descriptor \ "schemaVersion").as[Int] shouldBe 2
      (descriptor \ "component" \ "name").as[String] shouldBe "sample-component"
      (descriptor \ "componentStyle").as[JsObject] shouldBe
        (Json.parse(_model_metadata_with_component_style) \ "componentStyle").as[JsObject]
    }
  }

    "reject competing project and source descriptor authorities for a CML-derived style snapshot" in {
    _with_temp_dir("cozy-car-component-style-authority") { dir =>
      Given("generated CML metadata and competing project or descriptor declarations")
      val mainjar = _write(dir.resolve("artifacts/main.jar"), "main")
      val metadata = _write(dir.resolve("target/cozy/model-metadata.json"), _model_metadata_with_component_style)

      def _build_(label: String, extra: List[String]): Throwable =
        intercept[Throwable] {
          CarPackagingSpecSupport.buildCarWithContract(List(
            "--save", dir.resolve(s"out/$label.car").toString,
            "--main-jar", mainjar.toString,
            "--model-metadata", metadata.toString,
            "--name", "sample-component",
            "--version", "0.1.0",
            "--component", "sample-component"
          ) ++ extra)
        }

      When("the packager receives scalar, list, kebab-case, or nested project authority keys")
      val projecterrors = Vector(
        "scalar" -> "componentStyle: full-fledged-with-standalone\n",
        "list" -> "componentCapabilities:\n  - domain.full@1\n",
        "kebab" -> "component-style: full-fledged-with-standalone\n",
        "nested-style" -> "component:\n  style: full-fledged-with-standalone\n",
        "nested-capabilities" -> "component:\n  capabilities:\n    - domain.full@1\n"
      ).map { case (label, yaml) =>
        val project = dir.resolve(s"project-$label")
        _write(project.resolve("project.yaml"), yaml)
        label -> _build_(label, List("--project-dir", project.toString))
      }

      val cardir = dir.resolve("source-car")
      _write(cardir.resolve("component-descriptor.json"), "{}")
      val sourceerror = _build_("source", List("--car-dir", cardir.toString))
      val extensionerror = _build_("extension", List("--extensions", "{\"componentDescriptorJson\":\"{}\"}"))
      val policyerror = _build_("policy", List("--config", "operation-mode=legacy"))

      Then("all competing authorities fail before CAR output")
      projecterrors.foreach { case (_, error) =>
        error.getMessage should include ("project.yaml must not declare component style or capability authority")
      }
      sourceerror.getMessage should include ("CML component style snapshot cannot be overridden")
      extensionerror.getMessage should include ("CML component style snapshot cannot be overridden")
      policyerror.getMessage should include ("CML component style snapshot must not declare Component operating")
    }
  }

    "preserve legacy descriptor routes without a generated style snapshot while closing styled CML routes" in {
    _with_temp_dir("cozy-car-cml-descriptor-authority") { dir =>
      Given("a CML CAR with a valid source ABI but no independently selectable descriptor authority")
      val projectdir = dir.resolve("project")
      val cardir = projectdir.resolve("src/main/car")
      val mainjar = _write(dir.resolve("artifacts/main.jar"), "main")
      _write(projectdir.resolve("src/main/cozy/sample.cml"), "# COMPONENT\n\n## Sample\n")
      _write(cardir.resolve("abi-manifest.json"), _abi_manifest("sample-component", "0.1.0-SNAPSHOT", "sample-component"))
      val explicitabi = _write(dir.resolve("abi/explicit.json"), _abi_manifest("sample-component", "0.1.0-SNAPSHOT", "sample-component"))
      val metadata = projectdir.resolve("target/cozy/model-metadata.json")

      def _build_(label: String, model: Option[String], extra: List[String] = Nil): Path = {
        Files.deleteIfExists(metadata)
        model.foreach(value => _write(metadata, value))
        val archive = dir.resolve(s"out/$label.car")
        CarPackagingSpecSupport.buildCarWithContract(List(
          "--save", archive.toString,
          "--project-dir", projectdir.toString,
          "--main-jar", mainjar.toString,
          "--name", "sample-component",
          "--version", "0.1.0-SNAPSHOT",
          "--component", "sample-component"
        ) ++ model.map(_ => List("--model-metadata", metadata.toString)).getOrElse(Nil) ++ extra)
        archive
      }

      def _error_(label: String, model: Option[String], extra: List[String] = Nil): Throwable =
        intercept[Throwable](_build_(label, model, extra))

      When("style-less legacy CML and styled CML authority routes are packaged")
      val missing = _build_("missing", None)
      val empty = _error_("empty", Some("""{"schema":"cozy.cml.model-metadata.v1","componentStyle":{}}"""))
      val unsupported = _error_(
        "unsupported-schema",
        Some(_model_metadata_with_component_style.replace("\"cozy.cml.model-metadata.v1\"", "\"other.v1\"")),
        List("--abi-manifest", explicitabi.toString)
      )
      _write(cardir.resolve("component-descriptor.json"), "{}")
      val source = _error_("source", Some(_model_metadata_with_component_style))
      Files.delete(cardir.resolve("component-descriptor.json"))
      val extension = _error_(
        "extension",
        Some(_model_metadata_with_component_style),
        List("--extensions", "{\"componentDescriptorJson\":\"{}\"}")
      )
      _write(
        cardir.resolve("component-descriptor.json"),
        """{"schemaVersion":2,"name":"sample-component","version":"0.1.0-SNAPSHOT","component":"sample-component","componentStyle":{}}"""
      )
      val legacysourcev2 = _error_("legacy-source-v2", None)
      _write(
        cardir.resolve("component-descriptor.json"),
        """{"name":"sample-component","version":"0.1.0-SNAPSHOT","component":"sample-component","componentStyle":{}}"""
      )
      val legacysourcestyle = _error_("legacy-source-style", None)
      val legacyextensionv2 = _error_(
        "legacy-extension-v2",
        None,
        List("--extensions", "{\"componentDescriptorJson\":\"{\\\"schemaVersion\\\":2,\\\"name\\\":\\\"sample-component\\\",\\\"version\\\":\\\"0.1.0-SNAPSHOT\\\",\\\"component\\\":\\\"sample-component\\\",\\\"componentStyle\\\":{}}\"}")
      )

      Then("absent style metadata remains legacy while malformed, styled, and non-legacy source authorities fail closed")
      (Json.parse(_zip_text(missing, "component-descriptor.json")) \ "schemaVersion").toOption shouldBe None
      empty.getMessage should include("must declare one non-empty componentStyle object")
      unsupported.getMessage should include("must declare schema 'cozy.cml.model-metadata.v1'")
      source.getMessage should include("CML component style snapshot cannot be overridden")
      extension.getMessage should include("CML component style snapshot cannot be overridden")
      legacysourcev2.getMessage should include("Style-less CML source component-descriptor.json must be a legacy descriptor")
      legacysourcestyle.getMessage should include("Style-less CML source component-descriptor.json must be a legacy descriptor")
      legacyextensionv2.getMessage should include("Style-less CML source componentDescriptorJson must be a legacy descriptor")
      Vector("empty", "unsupported-schema", "source", "extension", "legacy-source-v2", "legacy-source-style", "legacy-extension-v2").foreach { label =>
        Files.exists(dir.resolve(s"out/$label.car")) shouldBe false
      }
    }
  }

    "reject an assembly descriptor whose subsystem version differs from the CAR coordinate" in {
    _with_temp_dir("cozy-car-assembly-coordinate") { dir =>
      Given("a CAR assembly whose subsystem retains an older version")
      val mainjar = _write(dir.resolve("artifacts/main.jar"), "main")
      val assembly = _write(
        dir.resolve("assembly-descriptor.yaml"),
        """subsystem: sample-component
          |version: 0.0.9
          |components:
          |  - name: sample-component
          |    version: 0.1.0
          |""".stripMargin
      )
      val archive = dir.resolve("out/sample.car")

      When("Cozy packages the assembly as a newer CAR coordinate")
      val error = intercept[Throwable] {
        CarPackagingSpecSupport.buildCarWithContract(List(
          "--save", archive.toString,
          "--main-jar", mainjar.toString,
          "--assembly-descriptor", assembly.toString,
          "--name", "sample-component",
          "--version", "0.1.0",
          "--component", "sample-component"
        ))
      }

      Then("packaging rejects the stale assembly before writing the archive")
      error.getMessage should include(
        "assembly-descriptor.yaml must declare subsystem version '0.1.0'"
      )
      Files.exists(archive) shouldBe false
    }
  }

    "reject an assembly descriptor whose primary component version differs from the CAR coordinate" in {
    _with_temp_dir("cozy-car-assembly-component-coordinate") { dir =>
      Given("a CAR assembly with the current subsystem version and an older primary component version")
      val mainjar = _write(dir.resolve("artifacts/main.jar"), "main")
      val assembly = _write(
        dir.resolve("assembly-descriptor.yaml"),
        """subsystem: sample-component
          |version: 0.1.0
          |components:
          |  - name: sample-component
          |    version: 0.0.9
          |""".stripMargin
      )
      val archive = dir.resolve("out/sample.car")

      When("Cozy packages the assembly at the current CAR coordinate")
      val error = intercept[Throwable] {
        CarPackagingSpecSupport.buildCarWithContract(List(
          "--save", archive.toString,
          "--main-jar", mainjar.toString,
          "--assembly-descriptor", assembly.toString,
          "--name", "sample-component",
          "--version", "0.1.0",
          "--component", "sample-component"
        ))
      }

      Then("packaging rejects the stale primary component before writing the archive")
      error.getMessage should include(
        "assembly-descriptor.yaml must declare component 'sample-component' at CAR version '0.1.0'"
      )
      Files.exists(archive) shouldBe false
    }
  }

    "merge compatible ABI surfaces from multiple generated CML metadata files" in {
    _with_temp_dir("cozy-car-multiple-model-metadata-abi") { dir =>
      Given("two generated CML metadata files with distinct operation and entity exports")
      val mainjar = _write(dir.resolve("artifacts/main.jar"), "main")
      val first = _write(dir.resolve("target/cozy/model-metadata/model-001.json"), _model_metadata)
      val second = _write(dir.resolve("target/cozy/model-metadata/model-002.json"), _secondary_model_metadata)
      val archive = dir.resolve("out/sample.car")

      When("Cozy packages both metadata documents into one CAR ABI")
      CarPackagingSpecSupport.buildCarWithContract(List(
        "--save", archive.toString,
        "--main-jar", mainjar.toString,
        "--model-metadata", s"${first},${second}",
        "--name", "sample-component",
        "--version", "0.1.0",
        "--component", "sample-component"
      ))

      Then("the ABI contains each distinct authored operation and entity once")
      val exports = Json.parse(_zip_text(archive, "abi-manifest.json")) \ "abi" \ "exports"
      (exports \ "operations").as[Seq[JsValue]].map(x => (x \ "name").as[String]) should
        contain theSameElementsAs Seq("createNotice", "getNotice", "archiveNotice")
      (exports \ "entities").as[Seq[JsValue]].map(x => (x \ "name").as[String]) should
        contain theSameElementsAs Seq("Notice", "NoticeArchive")
    }
  }

    "derive full service, operation, type, entity, and dependency ABI surfaces from canonical metadata" in {
    _with_temp_dir("cozy-car-model-metadata-abi") { dir =>
      Given("generated CML model metadata and one project-declared component ABI dependency")
      val projectdir = dir.resolve("project")
      _write(
        projectdir.resolve("project.yaml"),
        """packaging:
          |  car:
          |    abi:
          |      dependencies:
          |        - name: textus-foundation
          |          abiRange: "[1.2.0,2.0.0)"
          |""".stripMargin
      )
      val mainjar = _write(dir.resolve("artifacts/main.jar"), "main")
      val metadata = _write(dir.resolve("target/cozy/model-metadata.json"), _model_metadata)
      val archive = dir.resolve("out/sample.car")
      val abisidecar = dir.resolve("target/cozy/abi-manifest.json")

      When("Cozy packages the CAR without an explicit ABI manifest")
      CarPackagingSpecSupport.buildCarWithContract(List(
        "--save", archive.toString,
        "--project-dir", projectdir.toString,
        "--main-jar", mainjar.toString,
        "--model-metadata", metadata.toString,
        "--abi-manifest-output", abisidecar.toString,
        "--name", "sample-component",
        "--version", "0.1.0",
        "--component", "sample-component"
      ))

      Then("the generated ABI manifest preserves every canonical public contract surface")
      val abi = Json.parse(_zip_text(archive, "abi-manifest.json")) \ "abi"
      val exports = abi \ "exports"
      (exports \ "services").as[Seq[JsValue]].map(x => (x \ "name").as[String]) shouldBe Seq("Notice")
      val operations = (exports \ "operations").as[Seq[JsValue]]
      operations.map(x => (x \ "name").as[String]) should contain theSameElementsAs Seq("createNotice", "getNotice")
      operations.find(x => (x \ "name").as[String] == "createNotice").get shouldBe Json.obj(
        "service" -> "Notice",
        "name" -> "createNotice",
        "kind" -> "COMMAND",
        "input" -> "CreateNotice",
        "output" -> "CreateNoticeResult"
      )
      val entities = (exports \ "entities").as[Seq[JsValue]]
      entities.map(x => (x \ "name").as[String]) shouldBe Seq("Notice")
      (entities.head \ "fields").as[Seq[JsValue]].head shouldBe Json.obj(
        "name" -> "id",
        "type" -> "entityid",
        "multiplicity" -> "1",
        "required" -> true
      )
      val types = (exports \ "types").as[Seq[JsValue]]
      types.map(x => (x \ "name").as[String]) should contain allOf (
        "CreateNotice", "CreateNoticeResult", "GetNotice", "NoticeResult"
      )
      val createnotice = types.find(x => (x \ "name").as[String] == "CreateNotice").get
      (createnotice \ "kind").as[String] shouldBe "value"
      val getnotice = types.find(x => (x \ "name").as[String] == "GetNotice").get
      (getnotice \ "kind").as[String] shouldBe "value"
      (createnotice \ "fields").as[Seq[JsValue]].head shouldBe Json.obj(
        "name" -> "title",
        "type" -> "string",
        "multiplicity" -> "1",
        "required" -> true
      )
      (abi \ "dependencies").as[Seq[JsValue]] shouldBe Seq(Json.obj(
        "name" -> "textus-foundation",
        "abiRange" -> "[1.2.0,2.0.0)"
      ))
      Files.readString(abisidecar) shouldBe _zip_text(archive, "abi-manifest.json")
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
      CarPackagingSpecSupport.buildCarWithContract(List(
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
      CarPackagingSpecSupport.buildCarWithContract(List(
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
      CarPackagingSpecSupport.buildCarWithContract(List(
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
      Given("generated metadata, a CAR source manifest, and an explicit ABI manifest")
      val mainjar = _write(dir.resolve("artifacts/main.jar"), "main")
      val cardir = dir.resolve("src/main/car")
      _write(cardir.resolve("abi-manifest.json"), _abi_manifest("sample-component", "0.1.0", "source-component"))
      val explicitabi = _write(dir.resolve("abi/explicit.json"), _abi_manifest("sample-component", "0.1.0", "explicit-component"))
      val metadata = _write(dir.resolve("target/cozy/model-metadata.json"), _model_metadata)
      val archive = dir.resolve("out/sample.car")

      When("Cozy packages the CAR with --abi-manifest")
      CarPackagingSpecSupport.buildCarWithContract(List(
        "--save", archive.toString,
        "--main-jar", mainjar.toString,
        "--car-dir", cardir.toString,
        "--abi-manifest", explicitabi.toString,
        "--model-metadata", metadata.toString,
        "--name", "sample-component",
        "--version", "0.1.0",
        "--component", "generated-component"
      ))

      Then("the explicit ABI manifest is embedded")
      _zip_text(archive, "abi-manifest.json") shouldBe Files.readString(explicitabi)
    }
  }
  }

    "validate CAR descriptors and project policy" which {
    "reject generated model metadata with an unsupported schema" in {
    _with_temp_dir("cozy-car-invalid-model-metadata") { dir =>
      Given("generated metadata whose schema is not the CML metadata contract")
      val mainjar = _write(dir.resolve("artifacts/main.jar"), "main")
      val metadata = _write(dir.resolve("target/cozy/model-metadata.json"), "{\"schema\":\"other.v1\"}\n")
      val archive = dir.resolve("out/sample.car")

      When("Cozy packages the CAR without an explicit ABI manifest")
      val ex = intercept[Throwable] {
        CarPackagingSpecSupport.buildCarWithContract(List(
          "--save", archive.toString,
          "--main-jar", mainjar.toString,
          "--model-metadata", metadata.toString,
          "--name", "sample-component",
          "--version", "0.1.0",
          "--component", "sample-component"
        ))
      }

      Then("the unsupported metadata is rejected instead of producing a skeletal ABI")
      ex.getMessage should include("must declare schema 'cozy.cml.model-metadata.v1'")
      archive.toFile.exists() shouldBe false
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
        CarPackagingSpecSupport.buildCarWithContract(List(
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
      Given("a complete structured component descriptor override")
      val mainjar = _write(dir.resolve("artifacts/main.jar"), "main")
      val archive = dir.resolve("out/sample.car")
      val descriptorjson =
        """{"component":{"name":"sample-component","version":"0.1.0","kind":"component","isPrimary":"true"},"componentlets":[{"name":"notice-admin","kind":"componentlet"},{"name":"public-notice","kind":"componentlet"}]}"""

      When("Cozy packages the CAR with that descriptor")
      CarPackagingSpecSupport.buildCarWithContract(List(
        "--save", archive.toString,
        "--main-jar", mainjar.toString,
        "--name", "sample-component",
        "--version", "0.1.0",
        "--component", "sample-component",
        "--extensions", s"""{"componentDescriptorJson":${Json.stringify(Json.toJson(descriptorjson))}}"""
      ))

      Then("the structured descriptor is preserved exactly")
      val descriptor = _zip_text(archive, "component-descriptor.json")
      descriptor shouldBe descriptorjson
      descriptor should include ("\"componentlets\"")
      descriptor should include ("\"name\":\"notice-admin\"")
      descriptor should include ("\"name\":\"public-notice\"")
    }
  }

    "accept descriptor override whose name is the component name" in {
    _with_temp_dir("cozy-car-component-descriptor-component-name") { dir =>
      Given("a descriptor whose component name differs from the versioned archive name")
      val mainjar = _write(dir.resolve("artifacts/main.jar"), "main")
      val archive = dir.resolve("out/sample-component-0.1.0.car")
      val descriptorjson =
        """{"component":{"name":"sample-component","version":"0.1.0","kind":"component"},"componentlets":[]}"""

      When("Cozy packages the versioned archive")
      CarPackagingSpecSupport.buildCarWithContract(List(
        "--save", archive.toString,
        "--main-jar", mainjar.toString,
        "--name", "sample-component-0.1.0",
        "--version", "0.1.0",
        "--component", "sample-component",
        "--extensions", s"""{"componentDescriptorJson":${Json.stringify(Json.toJson(descriptorjson))}}"""
      ))

      Then("the component-name descriptor remains valid")
      _zip_text(archive, "component-descriptor.json") shouldBe descriptorjson
    }
  }

    "reject structured component descriptor override without version" in {
    _with_temp_dir("cozy-car-componentlet-version") { dir =>
      Given("a structured descriptor override without a CAR version")
      val mainjar = _write(dir.resolve("artifacts/main.jar"), "main")
      val archive = dir.resolve("out/sample.car")
      val descriptorjson =
        """{"component":{"name":"sample-component","kind":"component"},"componentlets":[]}"""

      When("Cozy validates the descriptor during packaging")
      val ex = intercept[IllegalArgumentException] {
        CarPackagingSpecSupport.buildCarWithContract(List(
          "--save", archive.toString,
          "--main-jar", mainjar.toString,
          "--name", "sample-component",
          "--version", "0.1.0",
          "--component", "sample-component",
          "--extensions", s"""{"componentDescriptorJson":${Json.stringify(Json.toJson(descriptorjson))}}"""
        ))
      }

      Then("the missing version is rejected")
      ex.getMessage should include ("componentDescriptorJson must declare CAR version")
    }
  }

    "read project packaging policy and write dependency manifest" in {
    _with_temp_dir("cozy-car-project-policy") { dir =>
      Given("project-owned dependency and manifest packaging policy")
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

      When("Cozy packages the CAR from that project")
      CarPackagingSpecSupport.buildCarWithContract(List(
        "--save", archive.toString,
        "--project-dir", projectdir.toString,
        "--main-jar", mainjar.toString,
        "--lib-jars", libjar.toString,
        "--name", "sample-component",
        "--version", "0.1.0",
        "--component", "sample-component"
      ))

      Then("the archive projects the policy without embedding disabled dependencies")
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
      Given("project-owned component configuration")
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

      When("Cozy packages the component")
      CarPackagingSpecSupport.buildCarWithContract(List(
        "--save", archive.toString,
        "--project-dir", projectdir.toString,
        "--main-jar", mainjar.toString,
        "--name", "sample-component",
        "--version", "0.1.0",
        "--component", "sample-component"
      ))

      Then("the descriptor contains the project configuration")
      val descriptor = Json.parse(_zip_text(archive, "component-descriptor.json"))
      val config = descriptor \ "config"
      (config \ "textus.component.art-scene.datastores.application.policy").as[String] shouldBe "local-default"
    }
  }

    "accept CNCF runtime requirement while defaulting project CAR policy" in {
    _with_temp_dir("cozy-car-runtime-requirement") { dir =>
      Given("a minimal runtime contract and conventional project resources")
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

      When("Cozy packages with default CAR policy")
      CarPackagingSpecSupport.buildCarWithContract(List(
        "--save", archive.toString,
        "--project-dir", projectdir.toString,
        "--main-jar", mainjar.toString,
        "--lib-jars", libjar.toString,
        "--name", "sample-component",
        "--version", "0.1.0",
        "--component", "sample-component"
      ))

      Then("the default policy selects project resources without embedding dependencies")
      val entries = _zip_entries(archive)
      entries should contain ("web/WEB-INF/web.yaml")
      entries should contain ("web/WEB-INF/form.yaml")
      entries should contain ("web/WEB-INF/admin.yaml")
      entries should not contain "web/web.yaml"
      entries should not contain "component-dependencies.yaml"
      entries should not contain "lib/dep.jar"
    }
  }
  }

    "admit CNCF runtime compatibility evidence" which {
    "read CNCF runtime descriptor from lib jar without embedding dependencies" in {
    _with_temp_dir("cozy-car-runtime-descriptor-jar") { dir =>
      Given("an exact CNCF runtime descriptor on the compile classpath")
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

      When("Cozy packages with project dependency embedding disabled")
      val stderr = new ByteArrayOutputStream()
      Console.withErr(new PrintStream(stderr)) {
        CarPackagingSpecSupport.buildCarWithContract(List(
          "--save", archive.toString,
          "--project-dir", projectdir.toString,
          "--main-jar", mainjar.toString,
          "--lib-jars", cncfjar.toString,
          "--name", "sample-component",
          "--version", "0.1.0",
          "--component", "sample-component"
        ))
      }

      Then("the descriptor supplies runtime evidence without entering the CAR lib directory")
      val entries = _zip_entries(archive)
      stderr.toString(StandardCharsets.UTF_8.name()) should not include "CNCF runtime catalog is unavailable"
      entries should not contain "lib/goldenport-cncf_3.jar"
      _zip_text(archive, "component-dependencies.yaml") should include ("org.postgresql:postgresql:42.7.3")
    }
  }

    "accept CNCF runtime version above declared minimum" in {
    _with_temp_dir("cozy-car-runtime-minimum-compatible") { dir =>
      Given("a resolved CNCF version inside the declared runtime range")
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

      When("Cozy validates and packages the CAR")
      CarPackagingSpecSupport.buildCarWithContract(List(
        "--save", archive.toString,
        "--project-dir", projectdir.toString,
        "--main-jar", mainjar.toString,
        "--lib-jars", cncfjar.toString,
        "--name", "sample-component",
        "--version", "0.1.0",
        "--component", "sample-component"
      ))

      Then("the compatible archive is created")
      Files.exists(archive) shouldBe true
    }
  }

    "reject CNCF runtime version below declared minimum" in {
    _with_temp_dir("cozy-car-runtime-minimum-too-low") { dir =>
      Given("a resolved CNCF version below the declared minimum")
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

      When("Cozy evaluates the project contract")
      val error = intercept[RuntimeException] {
        CarPackagingSpecSupport.buildCarWithContract(List(
          "--save", archive.toString,
          "--project-dir", projectdir.toString,
          "--main-jar", mainjar.toString,
          "--lib-jars", cncfjar.toString,
          "--name", "sample-component",
          "--version", "0.1.0",
          "--component", "sample-component"
        ))
      }

      Then("the typed range diagnostic identifies both versions")
      error.getMessage should include ("below")
      error.getMessage should include ("0.4.8")
      error.getMessage should include ("0.4.9")
    }
  }

    "reject CNCF runtime version above declared maximum" in {
    _with_temp_dir("cozy-car-runtime-maximum-exceeded") { dir =>
      Given("a resolved CNCF version above the declared maximum")
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

      When("Cozy evaluates the project contract")
      val error = intercept[RuntimeException] {
        CarPackagingSpecSupport.buildCarWithContract(List(
          "--save", archive.toString,
          "--project-dir", projectdir.toString,
          "--main-jar", mainjar.toString,
          "--lib-jars", cncfjar.toString,
          "--name", "sample-component",
          "--version", "0.1.0",
          "--component", "sample-component"
        ))
      }

      Then("the typed range diagnostic identifies both versions")
      error.getMessage should include ("maximum")
      error.getMessage should include ("0.4.10")
      error.getMessage should include ("0.4.9")
    }
  }

    "reject excluded CNCF runtime version" in {
    _with_temp_dir("cozy-car-runtime-excluded") { dir =>
      Given("a resolved CNCF version listed in the excluded set")
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

      When("Cozy evaluates the project contract")
      val error = intercept[RuntimeException] {
        CarPackagingSpecSupport.buildCarWithContract(List(
          "--save", archive.toString,
          "--project-dir", projectdir.toString,
          "--main-jar", mainjar.toString,
          "--lib-jars", cncfjar.toString,
          "--name", "sample-component",
          "--version", "0.1.0",
          "--component", "sample-component"
        ))
      }

      Then("the exclusion diagnostic identifies the rejected version")
      error.getMessage should include ("excluded")
      error.getMessage should include ("0.4.10")
    }
  }

    "reject CNCF runtime tested list that omits resolved runtime descriptor" in {
    _with_temp_dir("cozy-car-runtime-tested-mismatch") { dir =>
      Given("a tested set that omits the resolved CNCF compile target")
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

      When("Cozy evaluates the project contract")
      val error = intercept[RuntimeException] {
        CarPackagingSpecSupport.buildCarWithContract(List(
          "--save", archive.toString,
          "--project-dir", projectdir.toString,
          "--main-jar", mainjar.toString,
          "--lib-jars", cncfjar.toString,
          "--name", "sample-component",
          "--version", "0.1.0",
          "--component", "sample-component"
        ))
      }

      Then("the tested-set diagnostic names the missing target")
      error.getMessage should include ("packaging.car.runtime.cncf.tested")
      error.getMessage should include ("0.4.10")
    }
  }

    "prefer explicit runtime catalog URL over runtime jar descriptor" in {
    _with_temp_dir("cozy-car-runtime-catalog-url") { dir =>
      Given("an explicit runtime catalog URL and a different JAR catalog")
      val projectdir = dir.resolve("project")
      val mainjar = _write(dir.resolve("artifacts/main.jar"), "main")
      val cncfjar = _write_zip(
        dir.resolve("artifacts/goldenport-cncf_3.jar"),
        "META-INF/cncf/runtime.yaml",
        """schemaVersion: 1
          |runtime: cncf
          |version: 0.4.10-SNAPSHOT
          |module: org.goldenport:goldenport-cncf_3:0.4.10-SNAPSHOT
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

        When("Cozy validates component-owned dependencies")
        val ex = intercept[Throwable] {
          CarPackagingSpecSupport.buildCarWithContract(List(
            "--save", archive.toString,
            "--project-dir", projectdir.toString,
            "--main-jar", mainjar.toString,
            "--lib-jars", cncfjar.toString,
            "--name", "sample-component",
            "--version", "0.1.0",
            "--component", "sample-component"
          ))
        }
        Then("the explicit catalog governs the dependency rejection")
        ex.getMessage should include ("base-provided")
        ex.getMessage should include ("org.postgresql:postgresql:42.7.3")
      } finally {
        server.stop(0)
      }
    }
  }
  }

    "validate generated metadata and dependency ownership" which {
    "reject a CML CAR whose generated model metadata side output is missing" in {
    _with_temp_dir("cozy-car-missing-model-metadata") { dir =>
      Given("a CML CAR project without an explicit ABI or generated model metadata")
      val mainjar = _write(dir.resolve("artifacts/main.jar"), "main")
      _write(dir.resolve("src/main/cozy/sample.cml"), "# COMPONENT\n\n## Sample\n")
      val archive = dir.resolve("out/sample.car")

      When("Cozy packages the project")
      val ex = intercept[Throwable] {
        CarPackagingSpecSupport.buildCarWithContract(List(
          "--save", archive.toString,
          "--main-jar", mainjar.toString,
          "--project-dir", dir.toString,
          "--name", "sample-component",
          "--version", "0.1.0-SNAPSHOT",
          "--component", "sample-component"
        ))
      }

      Then("packaging fails instead of silently creating a skeletal ABI")
      ex.getMessage should include("requires generated model metadata")
      archive.toFile.exists() shouldBe false
    }
  }

    "embed dependency jars only when project policy enables them" in {
    Given("a CAR project whose packaging policy enables dependency embedding")
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

      When("Cozy packages the project with a library JAR")
      CarPackagingSpecSupport.buildCarWithContract(List(
        "--save", archive.toString,
        "--project-dir", projectdir.toString,
        "--main-jar", mainjar.toString,
        "--lib-jars", libjar.toString,
        "--name", "sample-component",
        "--version", "0.1.0",
        "--component", "sample-component"
      ))

      Then("the dependency JAR is embedded under the CAR lib directory")
      val entries = _zip_entries(archive)
      entries should contain ("lib/dep.jar")
    }
  }

    "write only component-owned dependencies with CNCF runtime requirement" in {
    Given("a CAR project with component-owned dependencies and a base runtime catalog")
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

      When("Cozy packages the component dependency manifest")
      CarPackagingSpecSupport.buildCarWithContract(List(
        "--save", archive.toString,
        "--project-dir", projectdir.toString,
        "--main-jar", mainjar.toString,
        "--name", "sample-component",
        "--version", "0.1.0",
        "--component", "sample-component"
      ))

      Then("only component-owned dependencies and repositories are projected")
      val manifest = _zip_text(archive, "component-dependencies.yaml")
      manifest should not include "goldenport-cncf"
      manifest should not include "cats-core"
      manifest should include ("\"org.postgresql:postgresql:42.7.3\"")
      manifest should include ("\"com.example:legacy-driver:1.2.0\"")
      manifest should include ("repositories:")
    }
  }

    "reject dependencies already provided by CNCF runtime catalog" in {
    Given("a CAR project that redeclares a dependency provided by the CNCF runtime")
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

      When("Cozy validates component-owned dependencies")
      val ex = intercept[Throwable] {
        CarPackagingSpecSupport.buildCarWithContract(List(
          "--save", archive.toString,
          "--project-dir", projectdir.toString,
          "--main-jar", mainjar.toString,
          "--name", "sample-component",
          "--version", "0.1.0",
          "--component", "sample-component"
        ))
      }
      Then("the base-provided overlap is rejected")
      ex.getMessage should include ("base-provided")
      ex.getMessage should include ("org.typelevel:cats-core_3:2.10.0")
    }
  }

    "prefer exported CNCF runtime catalog from configured runtime project" in {
    Given("a configured CNCF runtime project with an exported runtime catalog")
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

      When("Cozy resolves the runtime catalog for packaging")
      val ex = intercept[Throwable] {
        CarPackagingSpecSupport.buildCarWithContract(List(
          "--save", archive.toString,
          "--project-dir", projectdir.toString,
          "--main-jar", mainjar.toString,
          "--name", "sample-component",
          "--version", "0.1.0",
          "--component", "sample-component"
        ))
      }
      Then("the configured runtime project's catalog governs dependency validation")
      ex.getMessage should include ("base-provided")
      ex.getMessage should include ("org.typelevel:cats-core_3:2.10.0")
    }
  }

    "warn and continue when CNCF runtime catalog is unavailable" in {
    Given("a valid CAR contract whose optional runtime catalog is unavailable")
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
      When("Cozy packages the project")
      val stderr = new ByteArrayOutputStream()
      Console.withErr(new PrintStream(stderr)) {
        CarPackagingSpecSupport.buildCarWithContract(List(
          "--save", archive.toString,
          "--project-dir", projectdir.toString,
          "--main-jar", mainjar.toString,
          "--name", "sample-component",
          "--version", "0.1.0",
          "--component", "sample-component"
        ))
      }

      Then("the archive is created and the missing catalog is reported as a warning")
      Files.isRegularFile(archive) shouldBe true
      stderr.toString(StandardCharsets.UTF_8.name()) should include ("CNCF runtime catalog is unavailable")
    }
  }

    "query configured CNCF command for runtime descriptor" in {
    Given("a CAR project with a configured CNCF runtime descriptor command")
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

      When("Cozy obtains runtime evidence through the configured command")
      val ex = intercept[Throwable] {
        CarPackagingSpecSupport.buildCarWithContract(List(
          "--save", archive.toString,
          "--project-dir", projectdir.toString,
          "--main-jar", mainjar.toString,
          "--name", "sample-component",
          "--version", "0.1.0",
          "--component", "sample-component"
        ))
      }
      Then("the returned runtime catalog governs dependency validation")
      ex.getMessage should include ("base-provided")
      ex.getMessage should include ("org.typelevel:cats-core_3:2.10.0")
    }
  }
  }

    "assemble SAR surfaces" which {
    "write descriptor at SAR top level" in {
    Given("a subsystem descriptor, extension JAR, and application configuration")
    _with_temp_dir("cozy-sar") { dir =>
      val sourcedir = dir.resolve("src")
      _write(sourcedir.resolve("subsystem-descriptor.yaml"), "subsystem: textus-identity\n")
      val extension = _write(dir.resolve("ext/grpc.jar"), "grpc")
      val appconf = _write(dir.resolve("conf/application.conf"), "env=dev")
      val archive = dir.resolve("out/sample.sar")

      When("Cozy packages the inputs as a SAR")
      CozyArchivePackager.buildSar(List(
        "--save", archive.toString,
        "--source-dir", sourcedir.toString,
        "--source-files", "subsystem-descriptor.yaml",
        "--extension-jars", extension.toString,
        "--application-conf", appconf.toString
      ))

      Then("the SAR uses canonical top-level and extension paths")
      val entries = _zip_entries(archive)
      entries should contain ("subsystem-descriptor.yaml")
      entries should contain ("extension/grpc.jar")
      entries should contain ("config/application.conf")
      entries should not contain "subsystem/subsystem-descriptor.yaml"
      entries should not contain "meta/manifest.json"
    }
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

  private def _model_metadata: String =
    """{
      |  "schema": "cozy.cml.model-metadata.v1",
      |  "surface": {
      |    "component": {
      |      "name": "Sample",
      |      "services": [
      |        {
      |          "name": "Notice",
      |          "operations": [
      |            {
      |              "name": "createNotice",
      |              "operationType": "COMMAND",
      |              "inputType": "CreateNotice",
      |              "outputType": "CreateNoticeResult"
      |            },
      |            {
      |              "name": "getNotice",
      |              "operationType": "QUERY",
      |              "inputType": "GetNotice",
      |              "outputType": "NoticeResult"
      |            }
      |          ]
      |        }
      |      ]
      |    }
      |  },
      |  "modelElements": [
      |    {
      |      "kind": "entity",
      |      "name": "Notice",
      |      "fields": [
      |        {"name": "id", "type": "entityid", "multiplicity": "1", "required": true},
      |        {"name": "title", "type": "string", "multiplicity": "?", "required": false}
      |      ]
      |    },
      |    {
      |      "kind": "command",
      |      "name": "CreateNotice",
      |      "fields": [
      |        {"name": "title", "type": "string", "multiplicity": "1", "required": true}
      |      ]
      |    },
      |    {"kind": "value", "name": "CreateNoticeResult", "fields": []},
      |    {"kind": "query", "name": "GetNotice", "fields": []},
      |    {"kind": "value", "name": "NoticeResult", "fields": []}
      |  ]
      |}
       |""".stripMargin

  private def _model_metadata_with_component_style: String =
    """{
      |  "schema": "cozy.cml.model-metadata.v1",
      |  "surface": { "component": { "name": "Sample", "services": [] } },
      |  "modelElements": [],
      |  "componentStyle": {
      |    "apiVersion": "cncf.textus/v1",
      |    "provider": "cncf",
      |    "id": "full-fledged-with-standalone@1",
      |    "version": 1,
      |    "parameterSchema": { "type": "object", "properties": {}, "required": [], "additionalProperties": false },
      |    "parameters": {},
      |    "provides": {
      |      "bundles": ["domain.full@1"],
      |      "capabilities": ["user.fixed-context-compatible@1", "user.multi-user@1"],
      |      "effective": ["domain.aggregate@1", "domain.command@1", "domain.domain-event@1", "domain.entity@1", "domain.optimistic-concurrency@1", "domain.persistence@1", "domain.projection@1", "domain.query@1", "domain.transaction@1", "user.fixed-context-compatible@1", "user.multi-user@1"]
      |    },
      |    "requires": { "subsystemCapabilities": ["datastore.optimistic-concurrency@1", "datastore.persistent@1", "datastore.transactional@1", "user-context.current@1"] }
      |  }
      |}
      |""".stripMargin

  private def _secondary_model_metadata: String =
    """{
      |  "schema": "cozy.cml.model-metadata.v1",
      |  "surface": {
      |    "component": {
      |      "name": "Sample",
      |      "services": [
      |        {
      |          "name": "Archive",
      |          "operations": [
      |            {
      |              "name": "archiveNotice",
      |              "operationType": "COMMAND",
      |              "inputType": "ArchiveNotice",
      |              "outputType": "ArchiveNoticeResult"
      |            }
      |          ]
      |        }
      |      ]
      |    }
      |  },
      |  "modelElements": [
      |    {"kind": "entity", "name": "NoticeArchive"}
      |  ]
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
