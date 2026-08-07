package cozy.archive

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path}
import java.util.zip.{ZipEntry, ZipFile, ZipOutputStream}

import scala.collection.JavaConverters._

import cozy.modeler.ComponentApiDescriptor
import cozy.runtime.CozySbtBridge
import cozy.CarPackagingSpecSupport
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import play.api.libs.json.Json

/*
 * @since   Aug.  7, 2026
 * @version Aug.  7, 2026
 * @author  ASAMI, Tomoharu
 */
final class Phase56CanonicalCarDescriptorCodecSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  "Phase 56 canonical CAR descriptor codecs" should {
    "enforce component and API descriptor contracts" should {
    "emit canonical descriptor, API descriptor, and ABI component coordinates" in {
      _with_temp_directory("phase56-canonical-descriptor") { root =>
        Given("one namespace-qualified project identity")
        _write(root.resolve("project.yaml"), _project_yaml)
        _write(root.resolve("src/main/car/component-descriptor.json"), _descriptor_json)
        val development = root.resolve("target/cncf.d/component-descriptor.json")
        val model = _write(root.resolve("api-model.json"), """{"provided":[],"required":[]}""")
        val api = root.resolve("component-api-descriptor.json")

        When("Cozy writes each canonical descriptor")
        CozyArchivePackager._write_development_component_descriptor(root, development)
        ComponentApiDescriptor.write(model, api, "org.example.textus", "UserAccount", "0.6.0-SNAPSHOT")
        val coordinate = CozyComponentReleaseCoordinateCodec.admit("org.example.textus", "UserAccount", "0.6.0-SNAPSHOT", "test")
        val abi = Json.parse(CozyCarAbiManifest.create(Vector.empty, coordinate))

        Then("all documents carry the one exact coordinate without legacy identity fields")
        val descriptor = Json.parse(Files.readString(development))
        (descriptor \ "schemaVersion").as[Int] shouldBe 3
        (descriptor \ "component").as[play.api.libs.json.JsObject] shouldBe coordinate.componentJson
        (descriptor \ "component").asOpt[String] shouldBe empty
        (descriptor \ "name").toOption shouldBe empty
        (descriptor \ "version").toOption shouldBe empty
        (descriptor \ "component").as[play.api.libs.json.JsObject].value.get("name") shouldBe empty
        val apijson = Json.parse(Files.readString(api))
        (apijson \ "schemaVersion").as[String] shouldBe "cncf.component-api.v2"
        (apijson \ "component").as[play.api.libs.json.JsObject] shouldBe coordinate.componentJson
        (abi \ "format").as[String] shouldBe "cozy.car.abi-manifest.v2"
        (abi \ "component").as[play.api.libs.json.JsObject] shouldBe coordinate.componentJson
        (abi \ "car").toOption shouldBe empty
        (abi \ "abi" \ "exports" \ "components").as[Vector[play.api.libs.json.JsObject]].head.value.get("name") shouldBe empty
      }
    }

    "derive the only admitted API artifact projection" in {
      Given("a canonical release coordinate")
      val coordinate = CozyComponentReleaseCoordinateCodec.admit("org.example.textus", "UserAccount", "0.6.0-SNAPSHOT", "test")

      When("the shared API artifact projection is read")
      val artifactpath = coordinate.apiArtifactPath

      Then("the API path is derived from the shared Maven artifact projection")
      coordinate.mavenArtifactId shouldBe "textus-user-account"
      artifactpath shouldBe "spi/textus-user-account-api.jar"
    }

    "reject a source-managed descriptor mismatch through the development packager" in {
      Given("a source-managed descriptor with a conflicting namespace")
      _with_temp_directory("phase56-development-mismatch") { root =>
        _write(root.resolve("project.yaml"), _project_yaml)
        _write(root.resolve("src/main/car/component-descriptor.json"), _descriptor_json.replace("org.example.textus", "org.other.textus"))

        When("the development descriptor route reads the source")
        val mismatch = intercept[Throwable] {
      CozyArchivePackager._write_development_component_descriptor(root, root.resolve("target/cncf.d/component-descriptor.json"))
        }

        Then("the error names expected and actual coordinates")
        mismatch.getMessage should include("component.release-coordinate.mismatch")
        mismatch.getMessage should include("expected=org.example.textus.UserAccount:0.6.0-SNAPSHOT")
        mismatch.getMessage should include("actual=org.other.textus.UserAccount:0.6.0-SNAPSHOT")
      }
    }

    "retain shared identity validation codes for missing or malformed canonical fields" in {
      Given("missing namespace, malformed ID, and missing release inputs")
      val cases = Vector(
        (null, "UserAccount", "0.6.0", "component.identity.namespace.required"),
        ("org..example", "UserAccount", "0.6.0", "component.identity.namespace.segment-format"),
        ("org.example", null, "0.6.0", "component.identity.local-id.required"),
        ("org.example", "user-account", "0.6.0", "component.identity.local-id.format"),
        ("org.example", "UserAccount", null, "component.identity.release.required"),
        ("org.example", "UserAccount", "bad release", "component.identity.release.format")
      )

      When("the shared adapter admits each supplied coordinate")
      val errors = cases.map { case (namespace, id, version, _) =>
        intercept[Throwable] {
          CozyComponentReleaseCoordinateCodec.admit(namespace, id, version, "test")
        }
      }

      Then("the adapter preserves the collaborator API diagnostic codes")
      cases.zip(errors).foreach { case ((_, _, _, code), error) =>
        error.getMessage should include(code)
      }
    }

    "reject unsupported descriptor and API schemas at their owning boundaries" in {
      _with_temp_directory("phase56-unsupported-schema") { root =>
        Given("a development descriptor at obsolete schema 2")
        _write(root.resolve("project.yaml"), _project_yaml)
        _write(root.resolve("src/main/car/component-descriptor.json"), _descriptor_json.replace("\"schemaVersion\":3", "\"schemaVersion\":2"))

        When("the source descriptor route is evaluated")
        val descriptorerror = intercept[Throwable] {
      CozyArchivePackager._write_development_component_descriptor(root, root.resolve("target/cncf.d/component-descriptor.json"))
        }
        val apipath = _write(root.resolve("api-v1.json"), """{"schemaVersion":"cncf.component-api.v1","component":{"namespace":"org.example.textus","id":"UserAccount","version":"0.6.0"},"provided":[]}""")
        val apierror = intercept[Throwable] {
          ComponentApiJarPackager._build_api_jar(root.resolve("unused.jar"), apipath, root.resolve("output.jar"))
        }

        Then("both owning boundaries use their stable schema codes")
        descriptorerror.getMessage should include("component.descriptor.schema.unsupported")
        apierror.getMessage should include("component.api.schema.unsupported")
      }
    }

    "reject legacy identity fields in source-managed component descriptors" in {
      Given("canonical descriptors contaminated by a legacy root or component field")
      val cases = Vector(
        _descriptor_json.replace("\"component\":", "\"name\":\"UserAccount\",\"component\":"),
        _descriptor_json.replace("\"component\":", "\"version\":\"0.6.0-SNAPSHOT\",\"component\":"),
        """{"schemaVersion":3,"component":"UserAccount"}""",
        _descriptor_json.replace("\"version\":\"0.6.0-SNAPSHOT\"}", "\"version\":\"0.6.0-SNAPSHOT\",\"name\":\"UserAccount\"}")
      )
      _with_temp_directory("phase56-legacy-component-fields") { root =>
        _write(root.resolve("project.yaml"), _project_yaml)

        When("each source-managed document is admitted")
        val errors = cases.zipWithIndex.map { case (descriptor, index) =>
          _write(root.resolve("src/main/car/component-descriptor.json"), descriptor)
          intercept[Throwable] {
        CozyArchivePackager._write_development_component_descriptor(root, root.resolve(s"target/cncf.d/component-${index}.json"))
          }
        }

        Then("the canonical schema rejects every legacy identity shape")
        errors.foreach(_.getMessage should include("component"))
      }
    }
    }

    "preserve development runtime selectors" should {
    "render Maven artifact name separately from the local component ID" in {
      Given("a UserAccount development root with canonical descriptor and ABI evidence")
      _with_temp_directory("phase56-development-runtime-name") { root =>
        _write(root.resolve("project.json"), _runtime_project_json)
        _write(root.resolve("src/main/car/component-descriptor.json"), _descriptor_json)
        _write(
          root.resolve("src/main/car/abi-manifest.json"),
          """{"format":"cozy.car.abi-manifest.v2","component":{"namespace":"org.example.textus","id":"UserAccount","version":"0.6.0-SNAPSHOT"},"abi":{"version":1,"exports":{"components":[{"namespace":"org.example.textus","id":"UserAccount"}]},"dependencies":[]}}"""
        )
        val classes = root.resolve("target/classes")
        _write(classes.resolve("Sample.class"), "compiled")
        val classpath = _write(root.resolve("target/cncf.d/runtime-classpath.txt"), classes.toString)
        val output = root.resolve("target/cncf.d/car-runtime-manifest.json")

        When("development runtime evidence is emitted")
        CozyDevelopmentRuntimeManifest.write(root, classpath, output)

        Then("car.name uses the Maven artifact projection while car.component keeps the local ID")
        val manifest = Json.parse(Files.readString(output))
        (manifest \ "car" \ "name").as[String] shouldBe "textus-user-account"
        (manifest \ "car" \ "component").as[String] shouldBe "UserAccount"
      }
    }
    }

    "enforce ABI manifest contracts" should {
    "reject ABI format v1 at the development runtime evidence boundary" in {
      _with_temp_directory("phase56-unsupported-abi") { root =>
        Given("a canonical project, descriptor, and obsolete ABI format")
        _write(root.resolve("project.json"), _runtime_project_json)
        _write(root.resolve("src/main/car/component-descriptor.json"), _descriptor_json)
        _write(root.resolve("src/main/car/abi-manifest.json"), """{"format":"cozy.car.abi-manifest.v1"}""")
        val classes = root.resolve("target/classes")
        _write(classes.resolve("Sample.class"), "compiled")
        val classpath = _write(root.resolve("target/cncf.d/runtime-classpath.txt"), classes.toString)

        When("development runtime evidence validates the ABI document")
        val error = intercept[Throwable] {
          CozyDevelopmentRuntimeManifest.write(root, classpath, root.resolve("target/cncf.d/car-runtime-manifest.json"))
        }

        Then("the ABI owning boundary exposes its stable schema code")
        error.getMessage should include("car.abi.schema.unsupported")
      }
    }

    "merge ABI dependencies by qualified identity without conflating equal local IDs" in {
      Given("two dependency namespaces that share one local ID")
      val first = CozyCarAbiManifest.Dependency("org.alpha.textus", "Shared", "[1,2)")
      val second = CozyCarAbiManifest.Dependency("org.beta.textus", "Shared", "[1,2)")
      val owner = CozyComponentReleaseCoordinateCodec.admit("org.owner.textus", "Owner", "0.6.0", "test")

      When("the ABI manifest is emitted")
      val manifest = Json.parse(CozyCarAbiManifest.create(Vector.empty, owner, Vector(first, second)))

      Then("both namespace-qualified dependencies remain visible")
      (manifest \ "abi" \ "dependencies").as[Vector[play.api.libs.json.JsObject]].size shouldBe 2
    }

    "render configured ABI dependencies into a non-CML CAR" in {
      Given("a non-CML CAR project with equal local dependency IDs in two namespaces")
      _with_temp_directory("phase56-non-cml-abi-dependencies") { root =>
        _write(
          root.resolve("project.yaml"),
          _project_yaml +
            """packaging:
              |  car:
              |    abi:
              |      dependencies:
              |        - namespace: org.alpha.textus
              |          id: Shared
              |          abiRange: "[1,2)"
              |        - namespace: org.beta.textus
              |          id: Shared
              |          abiRange: "[2,3)"
              |""".stripMargin
        )
        val mainjar = _write(root.resolve("component.jar"), "component")
        val car = root.resolve("textus-user-account.car")

        When("the non-CML CAR is packaged without a source ABI manifest")
        CarPackagingSpecSupport.buildCarWithContract(List(
          "--save", car.toString,
          "--project-dir", root.toString,
          "--main-jar", mainjar.toString,
          "--name", "textus-user-account",
          "--version", "0.6.0-SNAPSHOT",
          "--component", "UserAccount"
        ))
        val manifest = Json.parse(_zip_text(car, "abi-manifest.json"))

        Then("both qualified dependencies are emitted with their configured ABI ranges")
        (manifest \ "abi" \ "dependencies").as[Vector[play.api.libs.json.JsObject]] shouldBe Vector(
          Json.obj("namespace" -> "org.alpha.textus", "id" -> "Shared", "abiRange" -> "[1,2)"),
          Json.obj("namespace" -> "org.beta.textus", "id" -> "Shared", "abiRange" -> "[2,3)")
        )
      }
    }

    "reject conflicting ABI ranges for one exact qualified dependency" in {
      Given("one ABI dependency identity with two different ranges")
      val owner = CozyComponentReleaseCoordinateCodec.admit("org.owner.textus", "Owner", "0.6.0", "test")

      When("the ABI codec merges the dependencies")
      val error = intercept[Throwable] {
        CozyCarAbiManifest.create(Vector.empty, owner, Vector(
          CozyCarAbiManifest.Dependency("org.alpha.textus", "Shared", "[1,2)"),
          CozyCarAbiManifest.Dependency("org.alpha.textus", "Shared", "[2,3)")
        ))
      }

      Then("the conflicting range is rejected by qualified identity")
      error.getMessage should include("org.alpha.textus.Shared")
    }

    "reject legacy and malformed source ABI identity structures" in {
      Given("one canonical coordinate and source ABI v2 documents with forbidden identity shapes")
      val coordinate = CozyComponentReleaseCoordinateCodec.admit("org.example.textus", "UserAccount", "0.6.0", "test")
      val cases = Vector(
        """{"format":"cozy.car.abi-manifest.v2","car":{},"component":{"namespace":"org.example.textus","id":"UserAccount","version":"0.6.0"},"abi":{"version":1,"exports":{"components":[{"namespace":"org.example.textus","id":"UserAccount"}]},"dependencies":[]}}""",
        """{"format":"cozy.car.abi-manifest.v2","component":{"namespace":"org.example.textus","id":"UserAccount","version":"0.6.0"},"abi":{"version":"1","exports":{"components":[{"namespace":"org.example.textus","id":"UserAccount"}]},"dependencies":[]}}""",
        """{"format":"cozy.car.abi-manifest.v2","component":{"namespace":"org.example.textus","id":"UserAccount","version":"0.6.0"},"abi":{"version":1,"exports":{"components":[{"name":"UserAccount"}]},"dependencies":[]}}""",
        """{"format":"cozy.car.abi-manifest.v2","component":{"namespace":"org.example.textus","id":"UserAccount","version":"0.6.0"},"abi":{"version":1,"exports":{"components":[{"namespace":"org.example.textus","id":"UserAccount"}]},"dependencies":[{"namespace":"org.dep","id":"Dependency","abiRange":"[1,2)","name":"Dependency"}]}}"""
      )

      When("the ABI source validator admits each document")
      val errors = cases.map { document =>
        intercept[Throwable] {
          CozyCarAbiManifest._validate_source_manifest(Json.parse(document), coordinate, "source-abi")
        }
      }

      Then("each legacy or non-exact ABI shape fails closed")
      errors.foreach(_.getMessage should include("ABI"))
    }
    }

    "enforce dependency resolution and extraction contracts" should {
    "fail closed for the old three-field component API dependency payload" in {
      Given("the legacy dependency argument shape")
      val workroot = Path.of("target/cozy-test/work/phase56-canonical-car-descriptor-codec-spec").toAbsolutePath.normalize()
      Files.createDirectories(workroot)
      val root = Files.createTempDirectory(workroot, "legacy-payload-")
      try {
        When("the legacy payload is parsed")
        val error = intercept[Throwable] {
          ComponentApiDependencyResolver.resolve(List(
            "--consumer-descriptor", root.resolve("consumer.json").toString,
            "--output-dir", root.resolve("output").toString,
            "--dependency", "provider\t0.6.0\tprovider.car"
          ))
        }

        Then("it is not reinterpreted as a namespace-qualified dependency")
        error.getMessage should include("component.api.dependency.payload.v2.required")
      } finally _delete_tree(root)
    }

    "keep namespace-isolated extraction paths for equal artifact filenames" in {
      _with_temp_directory("phase56-api-extraction") { root =>
        Given("a v2 consumer and two providers that share an artifact filename")
        val consumer = _write(
          root.resolve("consumer.json"),
          _api_descriptor(
            "org.consumer.textus",
            "Consumer",
            "0.6.0",
            "[]",
            """[{"apiClass":"example.Alpha","required":true},{"apiClass":"example.Beta","required":true}]"""
          )
        )
        val first = _provider_car(root.resolve("first.car"), "org.alpha.textus", "Shared", "0.6.0", "example.Alpha")
        val second = _provider_car(root.resolve("second.car"), "org.beta.textus", "Shared", "0.6.0", "example.Beta")

        When("both dependency APIs are selected")
        val jars = ComponentApiDependencyResolver._resolve_dependencies(
          consumer,
          Vector(
            ComponentApiDependencyResolver.Dependency("org.alpha.textus", "Shared", "0.6.0", first),
            ComponentApiDependencyResolver.Dependency("org.beta.textus", "Shared", "0.6.0", second)
          ),
          root.resolve("resolved"),
          None
        )

        Then("their group paths prevent an overwrite")
        jars.map(_.toString).distinct.size shouldBe 2
        jars.map(_.toString).mkString("\n") should include("org/alpha/textus")
        jars.map(_.toString).mkString("\n") should include("org/beta/textus")
      }
    }

    "resolve a four-field dependency payload with an exact assembly entry" in {
      _with_temp_directory("phase56-four-field-payload") { root =>
        Given("a matching provider CAR and namespace-qualified assembly declaration")
        val provider = _provider_car(root.resolve("provider.car"), "org.example.textus", "Provider", "0.6.0", "example.Api")
        val consumer = _write(
          root.resolve("consumer.json"),
          _api_descriptor("org.consumer.textus", "Consumer", "0.6.0", "[]", """[{"apiClass":"example.Api","required":true}]""")
        )
        val assembly = _write(root.resolve("assembly.yaml"),
          """components:
            |  - namespace: org.example.textus
            |    id: Provider
            |    version: 0.6.0
            |""".stripMargin)

        When("the command payload carries namespace, ID, release, and archive")
        ComponentApiDependencyResolver.resolve(List(
          "--consumer-descriptor", consumer.toString,
          "--output-dir", root.resolve("resolved").toString,
          "--assembly-descriptor", assembly.toString,
          "--dependency", s"org.example.textus\tProvider\t0.6.0\t${provider}"
        ))

        Then("the exact API JAR is extracted")
        Files.exists(root.resolve("resolved/org/example/textus/textus-provider/0.6.0/textus-provider-api.jar")) shouldBe true
      }
    }

    "report an assembly identity or release disagreement with the dependency key" in {
      _with_temp_directory("phase56-assembly-mismatch") { root =>
        Given("one declared provider and a stale assembly release")
        val provider = _provider_car(root.resolve("provider.car"), "org.example.textus", "Provider", "0.6.0", "example.Api")
        val consumer = _write(root.resolve("consumer.json"), _api_descriptor("org.consumer.textus", "Consumer", "0.6.0", "[]", """[{"apiClass":"example.Api","required":true}]"""))
        val assembly = _write(root.resolve("assembly.yaml"),
          """components:
            |  - namespace: org.example.textus
            |    id: Provider
            |    version: 0.6.1
            |""".stripMargin)

        When("the assembly is checked")
        val error = intercept[Throwable] {
          ComponentApiDependencyResolver._resolve_dependencies(
            consumer,
            Vector(ComponentApiDependencyResolver.Dependency("org.example.textus", "Provider", "0.6.0", provider)),
            root.resolve("resolved"),
            Some(assembly)
          )
        }

        Then("the exact declared dependency key is retained")
        error.getMessage should include("org.example.textus.Provider:0.6.0")
      }
    }

    "reject a mismatched API artifact path at the API JAR boundary" in {
      _with_temp_directory("phase56-api-path-mismatch") { root =>
        Given("a v2 descriptor with a forged artifact projection")
        val descriptor = _write(root.resolve("api.json"),
          _api_descriptor("org.example.textus", "UserAccount", "0.6.0", """[{"version":"0.6.0","artifactPath":"spi/forged-api.jar","publicTypes":[]}]""", "[]"))

        When("the API JAR boundary validates the descriptor")
        val error = intercept[Throwable] {
          ComponentApiJarPackager._build_api_jar(root.resolve("unused.jar"), descriptor, root.resolve("forged-api.jar"))
        }

        Then("the projection mismatch has its stable diagnostic code")
        error.getMessage should include("component.release-coordinate.projection-mismatch")
      }
    }
    }

    "forward namespace ID and release separately through the SBT bridge" in {
      Given("canonical component build settings plus an obsolete module setting")
      val settings = Map(
        "component.namespace" -> "org.example.textus",
        "component.id" -> "UserAccount",
        "component.version" -> "0.6.0-SNAPSHOT",
        "component.module" -> "ignored-module"
      )

      When("the SBT bridge projects the modeler arguments")
      val args = CozySbtBridge._component_api_args_for_test(settings)

      Then("only the three canonical modeler arguments are forwarded")
      args shouldBe List(
        "--component-namespace", "org.example.textus",
        "--component-id", "UserAccount",
        "--component-version", "0.6.0-SNAPSHOT"
      )
    }
  }

  private def _api_descriptor(namespace: String, id: String, version: String, provided: String, required: String): String =
    s"""{"schemaVersion":"cncf.component-api.v2","component":{"namespace":"$namespace","id":"$id","version":"$version"},"provided":$provided,"required":$required}"""

  private def _provider_car(path: Path, namespace: String, id: String, version: String, apiclass: String): Path = {
    val coordinate = CozyComponentReleaseCoordinateCodec.admit(namespace, id, version, "test")
    val provided = s"""[{"apiClass":"$apiclass","version":"$version","artifactPath":"${coordinate.apiArtifactPath}","abiHash":"sha256:$apiclass"}]"""
    _write_zip(path, Map("component-api-descriptor.json" -> _api_descriptor(namespace, id, version, provided, "[]"), coordinate.apiArtifactPath -> "api"))
  }

  private def _with_temp_directory[A](prefix: String)(f: Path => A): A = {
    val workroot = Path.of("target/cozy-test/work/phase56-canonical-car-descriptor-codec-spec").toAbsolutePath.normalize()
    Files.createDirectories(workroot)
    val root = Files.createTempDirectory(workroot, s"$prefix-")
    try f(root)
    finally _delete_tree(root)
  }

  private def _delete_tree(root: Path): Unit = {
    val stream = Files.walk(root)
    try stream.iterator().asScala.toVector.sortBy(_.getNameCount)(Ordering[Int].reverse).foreach(Files.deleteIfExists(_))
    finally stream.close()
  }

  private def _write(path: Path, content: String): Path = {
    Option(path.getParent).foreach(Files.createDirectories(_))
    Files.writeString(path, content, StandardCharsets.UTF_8)
    path
  }

  private def _write_zip(path: Path, entries: Map[String, String]): Path = {
    val output = new ZipOutputStream(Files.newOutputStream(path))
    try entries.toVector.sortBy(_._1).foreach { case (name, content) =>
      output.putNextEntry(new ZipEntry(name))
      output.write(content.getBytes(StandardCharsets.UTF_8))
      output.closeEntry()
    } finally output.close()
    path
  }

  private def _zip_text(path: Path, entryname: String): String = {
    val archive = new ZipFile(path.toFile)
    try {
      val input = archive.getInputStream(archive.getEntry(entryname))
      try new String(input.readAllBytes(), StandardCharsets.UTF_8)
      finally input.close()
    } finally archive.close()
  }

  private val _project_yaml =
    """project:
      |  namespace: org.example.textus
      |  id: UserAccount
      |  component:
      |    version: 0.6.0-SNAPSHOT
      |""".stripMargin

  private val _descriptor_json =
    """{"schemaVersion":3,"component":{"namespace":"org.example.textus","id":"UserAccount","version":"0.6.0-SNAPSHOT"}}"""

  private def _runtime_project_json: String =
    s"""{
       |  "project": {
       |    "namespace": "org.example.textus",
       |    "id": "UserAccount",
       |    "kind": "car",
       |    "component": {"version": "0.6.0-SNAPSHOT"}
       |  },
       |  "build": {
       |    "cozyVersion": "${org.simplemodeling.cozy.BuildInfo.version}",
       |    "dependencies": {"compile": ["org.goldenport::goldenport-cncf:0.5.17"]}
       |  },
       |  "packaging": {
       |    "kind": "car",
       |    "car": {"runtime": {"cncf": {"minimum": "0.5.17", "excluded": [], "tested": ["0.5.17"]}}}
       |  }
       |}
       |""".stripMargin
}
