package cozy.archive

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path}
import java.security.MessageDigest
import java.util.Comparator
import java.util.zip.{ZipEntry, ZipFile, ZipOutputStream}
import scala.collection.JavaConverters._

import cozy.compatibility.{
  CarMetadataCompatibility,
  CncfRuntimeCompatibility,
  MavenCoordinate
}
import cozy.modeler.CmlModelMetadata
import org.scalacheck.{Gen, Prop, Test}
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import play.api.libs.json.Json

final class CozyCarRuntimeManifestSpec
    extends AnyWordSpec
    with Matchers
    with GivenWhenThen {
  "CNCF CAR development runtime evidence" should {
    "project one CML-derived descriptor identically for development and packaged CAR routes" in {
      _with_temp_dir { root =>
        Given("one CAR project and its generated CML style snapshot")
        _write(
          root.resolve("project.yaml"),
          """project:
            |  name: sample-component
            |  kind: car
            |  component:
            |    name: sample-component
            |    version: 0.1.0-SNAPSHOT
            |packaging:
            |  kind: car
            |  car:
            |    runtime:
            |      cncf:
            |        minimum: 0.5.17
            |        tested: [0.5.17]
            |""".stripMargin
        )
        val metadata = _write(root.resolve("target/cozy/model-metadata.json"), _model_metadata_with_component_style)
        _write(
          root.resolve("src/main/car/abi-manifest.json"),
          """{"format":"cozy.car.abi-manifest.v1","car":{"name":"sample-component","version":"0.1.0-SNAPSHOT"},"abi":{"exports":{"components":[{"name":"sample-component"}]}}}"""
        )
        val development = root.resolve(CozyDevelopmentRuntimeManifest.COMPONENT_DESCRIPTOR_IDENTITY)
        val archive = root.resolve("out/sample.car")
        val mainjar = _write(root.resolve("artifacts/main.jar"), "main")

        When("the development and packaged routes render the same CML projection")
        CozyArchivePackager.writeDevelopmentComponentDescriptor(root, development)
        cozy.CarPackagingSpecSupport.buildCarWithContract(List(
          "--save", archive.toString,
          "--project-dir", root.toString,
          "--main-jar", mainjar.toString,
          "--model-metadata", metadata.toString,
          "--name", "sample-component",
          "--version", "0.1.0-SNAPSHOT",
          "--component", "sample-component"
        ))

        Then("both routes retain one semantically identical schema-v2 descriptor")
        Json.parse(Files.readString(development, StandardCharsets.UTF_8)) shouldBe
          Json.parse(_zip_text(archive, "component-descriptor.json"))
      }
    }

    "reject a source descriptor when generated CML style metadata is present" in {
      _with_temp_dir { root =>
        Given("a CML-derived project with a competing source descriptor")
        _write(
          root.resolve("project.yaml"),
          """project:
            |  name: sample-component
            |  kind: car
            |  component:
            |    name: sample-component
            |    version: 0.1.0
            |""".stripMargin
        )
        _write(root.resolve("target/cozy/model-metadata.json"), _model_metadata_with_component_style)
        _write(root.resolve("src/main/car/component-descriptor.json"), "{}")

        When("development descriptor generation evaluates competing authority")
        val error = intercept[Throwable] {
          CozyArchivePackager.writeDevelopmentComponentDescriptor(
            root,
            root.resolve(CozyDevelopmentRuntimeManifest.COMPONENT_DESCRIPTOR_IDENTITY)
          )
        }

        Then("the CML snapshot remains the sole descriptor authority")
        error.getMessage should include("CML component style snapshot cannot be overridden")
      }
    }

    "preserve a source descriptor for style-less legacy CML development" in {
      _with_temp_dir { root =>
        Given("a CML project without a selected component style and a legacy source descriptor")
        _write(
          root.resolve("project.yaml"),
          """project:
            |  name: sample-component
            |  kind: car
            |  component:
            |    name: sample-component
            |    version: 0.1.0
            |""".stripMargin
        )
        _write(root.resolve("src/main/cozy/sample.cml"), "# COMPONENT\n\n## Sample\n")
        val source = _write(
          root.resolve("src/main/car/component-descriptor.json"),
          """{"name":"sample-component","version":"0.1.0","component":"sample-component"}"""
        )
        val output = root.resolve(CozyDevelopmentRuntimeManifest.COMPONENT_DESCRIPTOR_IDENTITY)

        When("the development descriptor is prepared")
        CozyArchivePackager.writeDevelopmentComponentDescriptor(root, output)

        Then("the legacy source descriptor remains the development projection")
        Files.readString(output, StandardCharsets.UTF_8) shouldBe Files.readString(source, StandardCharsets.UTF_8)
      }
    }

    "reject descriptor coordinates that contradict the project-owned CAR contract" in {
      _with_temp_dir { root =>
        Given("a project contract and descriptor whose versions differ")
        _write(
          root.resolve("project.json"),
          """{"project":{"name":"sample","kind":"car","component":{"name":"sample","version":"0.0.1-SNAPSHOT"}},"build":{"cozyVersion":"0.3.1-SNAPSHOT","dependencies":{"compile":["org.goldenport::goldenport-cncf:0.5.17"]}},"packaging":{"kind":"car","car":{"runtime":{"cncf":{"minimum":"0.5.17","excluded":[],"tested":["0.5.17"]}}}}}"""
        )
        _write(root.resolve("src/main/car/component-descriptor.json"), """{"name":"sample","version":"0.0.2-SNAPSHOT","component":"sample"}""")
        _write(root.resolve("src/main/car/abi-manifest.json"), """{"format":"cozy.car.abi-manifest.v1","car":{"name":"sample","version":"0.0.2-SNAPSHOT"},"abi":{"exports":{"components":[{"name":"sample"}]}}}""")
        val classes = root.resolve("target/scala-3.3.8/classes")
        _write(classes.resolve("sample.class"), "compiled")
        val classpath = root.resolve(CozyDevelopmentRuntimeManifest.RUNTIME_CLASSPATH_IDENTITY)
        _write(classpath, classes.toString)

        When("development evidence is prepared")
        val exception = intercept[Throwable] {
          CozyDevelopmentRuntimeManifest.write(root, classpath, root.resolve("target/cncf.d/car-runtime-manifest.json"))
        }

        Then("the contradictory descriptor is rejected before manifest publication")
        exception.getMessage should include("Development runtime project version mismatch")
        exception.getMessage should include("expected=0.0.1-SNAPSHOT actual=0.0.2-SNAPSHOT")
      }
    }

    "write stable development evidence without treating mutable class bytes as archive integrity" in {
      _with_temp_dir { root =>
        Given("a CAR project with prepared classpath, descriptor, ABI, and mutable classes")
        _write(
          root.resolve("project.json"),
          """{
            |  "project": {
            |    "name": "sample",
            |    "kind": "car",
            |    "component": {"name": "sample", "version": "0.0.1-SNAPSHOT"}
            |  },
            |  "build": {
            |    "cozyVersion": "0.3.1-SNAPSHOT",
            |    "dependencies": {"compile": ["org.goldenport::goldenport-cncf:0.5.17"]}
            |  },
            |  "packaging": {
            |    "kind": "car",
            |    "car": {"runtime": {"cncf": {"minimum": "0.5.17", "excluded": [], "tested": ["0.5.17"]}}}
            |  }
            |}
            |""".stripMargin
        )
        _write(
          root.resolve("src/main/car/component-descriptor.json"),
          """{"name":"sample","version":"0.0.1-SNAPSHOT","component":"sample"}"""
        )
        _write(
          root.resolve("src/main/car/abi-manifest.json"),
          """{"format":"cozy.car.abi-manifest.v1","car":{"name":"sample","version":"0.0.1-SNAPSHOT"},"abi":{"exports":{"components":[{"name":"sample"}]}}}"""
        )
        val classes = root.resolve("target/scala-3.3.8/classes")
        _write(classes.resolve("sample.class"), "first compilation")
        val classpath = root.resolve(CozyDevelopmentRuntimeManifest.RUNTIME_CLASSPATH_IDENTITY)
        _write(classpath, classes.toString)
        val manifest = root.resolve("target/cncf.d/car-runtime-manifest.json")

        When("Cozy prepares development runtime evidence twice around a class-only recompilation")
        CozyDevelopmentRuntimeManifest.write(root, classpath, manifest)
        val before = Json.parse(Files.readString(manifest, StandardCharsets.UTF_8))
        _write(classes.resolve("sample.class"), "second compilation")
        CozyDevelopmentRuntimeManifest.write(root, classpath, manifest)
        val after = Json.parse(Files.readString(manifest, StandardCharsets.UTF_8))

        Then("the legacy development schema records only stable evidence")
        (after \ "schemaVersion").as[String] shouldBe "cncf.car-development-runtime-manifest.v1"
        (after \ "sourceKind").as[String] shouldBe "development-directory"
        (after \ "car" \ "component").as[String] shouldBe "sample"
        (after \ "evidence").as[Vector[play.api.libs.json.JsObject]].map(entry => (entry \ "path").as[String]) shouldBe Vector(
          "target/cncf.d/runtime-classpath.txt",
          "src/main/car/component-descriptor.json",
          "src/main/car/abi-manifest.json"
        )
        before shouldBe after

        When("the prepared classpath later names a deleted directory")
        Files.delete(classes.resolve("sample.class"))
        Files.delete(classes)
        val stale = intercept[Throwable] {
          CozyDevelopmentRuntimeManifest.write(root, classpath, manifest)
        }

        Then("Cozy refuses to publish stale development evidence")
        stale.getMessage should include("Development runtime classpath entry is missing")
        stale.getMessage should include(classes.toString)
      }
    }

    "project style-less generated CML metadata through the legacy development manifest route" in {
      _with_temp_dir { root =>
        Given("a style-less CML source and a legacy CAR descriptor")
        _write(
          root.resolve("project.json"),
          """{
            |  "project": {
            |    "name": "sample",
            |    "kind": "car",
            |    "component": {"name": "sample", "version": "0.0.1-SNAPSHOT"}
            |  },
            |  "build": {
            |    "cozyVersion": "0.3.1-SNAPSHOT",
            |    "dependencies": {"compile": ["org.goldenport::goldenport-cncf:0.5.17"]}
            |  },
            |  "packaging": {
            |    "kind": "car",
            |    "car": {"runtime": {"cncf": {"minimum": "0.5.17", "excluded": [], "tested": ["0.5.17"]}}}
            |  }
            |}
            |""".stripMargin
        )
        val source = _write(
          root.resolve("src/main/cozy/sample.cml"),
          """# ENTITY
            |
            |## Sample
            |""".stripMargin
        )
        val metadata = root.resolve("target/cozy/model-metadata.json")
        CmlModelMetadata.write(
          source,
          metadata,
          root.resolve("target/cozy/model-metadata.yaml"),
          "src/main/cozy/sample.cml",
          "concept"
        )
        _write(
          root.resolve("src/main/car/component-descriptor.json"),
          """{"name":"sample","version":"0.0.1-SNAPSHOT","component":"sample"}"""
        )
        _write(
          root.resolve("src/main/car/abi-manifest.json"),
          """{"format":"cozy.car.abi-manifest.v1","car":{"name":"sample","version":"0.0.1-SNAPSHOT"},"abi":{"exports":{"components":[{"name":"sample"}]}}}"""
        )
        val classes = root.resolve("target/scala-3.3.8/classes")
        _write(classes.resolve("sample.class"), "compiled")
        val classpath = _write(
          root.resolve(CozyDevelopmentRuntimeManifest.RUNTIME_CLASSPATH_IDENTITY),
          classes.toString
        )
        val manifestpath = root.resolve("target/cncf.d/car-runtime-manifest.json")

        When("Cozy writes development runtime evidence from generated style-less metadata")
        CozyDevelopmentRuntimeManifest.write(root, classpath, manifestpath)

        Then("the generated metadata omits a style and selects the v1 source descriptor identity")
        val generated = Json.parse(Files.readString(metadata, StandardCharsets.UTF_8))
        (generated \ "componentStyle").toOption shouldBe None
        val manifest = Json.parse(Files.readString(manifestpath, StandardCharsets.UTF_8))
        (manifest \ "schemaVersion").as[String] shouldBe "cncf.car-development-runtime-manifest.v1"
        (manifest \ "evidence").as[Vector[play.api.libs.json.JsObject]].map(entry => (entry \ "path").as[String]) shouldBe Vector(
          "target/cncf.d/runtime-classpath.txt",
          "src/main/car/component-descriptor.json",
          "src/main/car/abi-manifest.json"
        )

        When("a style-less CML source attempts to supply a schema-v2 descriptor")
        _write(
          root.resolve("src/main/car/component-descriptor.json"),
          """{"schemaVersion":2,"name":"sample","version":"0.0.1-SNAPSHOT","component":"sample","componentStyle":{}}"""
        )
        val error = intercept[Throwable] {
          CozyDevelopmentRuntimeManifest.write(root, classpath, manifestpath)
        }

        Then("development admission rejects the competing v2 source authority")
        error.getMessage should include("Style-less CML source component-descriptor.json must be a legacy descriptor")

        When("the source omits its schema version but still declares a component style")
        _write(
          root.resolve("src/main/car/component-descriptor.json"),
          """{"name":"sample","version":"0.0.1-SNAPSHOT","component":"sample","componentStyle":{}}"""
        )
        val styleerror = intercept[Throwable] {
          CozyDevelopmentRuntimeManifest.write(root, classpath, manifestpath)
        }

        Then("development admission rejects the componentStyle authority independently")
        styleerror.getMessage should include("Style-less CML source component-descriptor.json must be a legacy descriptor")
      }
    }
  }

  "CNCF CAR runtime manifest packaging" should {
    "preserve runtime range and exact archive bytes across generated versions" in {
      Given("generated CAR coordinates and arbitrary packaged component bytes")
      val versions = for {
        patch <- Gen.choose(0, 999)
        suffix <- Gen.oneOf("", "-SNAPSHOT")
        content <- Gen.nonEmptyListOf(Gen.alphaNumChar)
      } yield (s"0.5.${patch}${suffix}", content.mkString)

      When("Cozy writes CNCF-owned runtime admission evidence")
      val property = Prop.forAll(versions) { case (cncfversion, content) =>
        _with_temp_dir { root =>
          val component = root.resolve("component/main.jar")
          Files.createDirectories(component.getParent)
          Files.writeString(component, content, StandardCharsets.UTF_8)
          val abi = root.resolve("abi-manifest.json")
          Files.writeString(abi, """{"format":"cozy.car.abi-manifest.v1"}""")
          CozyCarRuntimeManifest.write(
            root,
            _contract(cncfversion),
            "sample",
            "0.0.1-SNAPSHOT",
            "sample"
          )
          val manifest = Json.parse(
            Files.readString(
              root.resolve(CozyCarRuntimeManifest.FILE_NAME),
              StandardCharsets.UTF_8
            )
          )
          val entries = (manifest \ "integrity" \ "entries")
            .as[Vector[play.api.libs.json.JsObject]]
            .map { entry =>
              (entry \ "path").as[String] -> (entry \ "sha256").as[String]
            }
            .toMap
          (manifest \ "runtime" \ "cncf" \ "minimum").as[String] == cncfversion &&
          (manifest \ "runtime" \ "cncf" \ "tested").as[Vector[String]] == Vector(cncfversion) &&
          entries.keySet == Set("abi-manifest.json", "component/main.jar") &&
          entries("component/main.jar") == _sha256(content.getBytes(StandardCharsets.UTF_8))
        }
      }

      Then("at least fifty generated contracts retain their range and digest evidence")
      val result = Test.check(
        Test.Parameters.default.withMinSuccessfulTests(50),
        property
      )
      result.passed shouldBe true
    }

    "admit package bytes and reject any post-package modification" in {
      _with_temp_dir { root =>
        Given("one package-generated CAR runtime manifest and descriptor")
        val staged = root.resolve("staged")
        _write(staged.resolve("component/main.jar"), "main")
        _write(
          staged.resolve("component-descriptor.json"),
          """{
            |  "name": "sample",
            |  "version": "0.0.1-SNAPSHOT",
            |  "component": "sample"
            |}
            |""".stripMargin
        )
        val contract = _contract("0.5.17")
        CozyCarRuntimeManifest.write(
          staged,
          contract,
          "sample",
          "0.0.1-SNAPSHOT",
          "sample"
        )
        val validarchive = _zip(staged, root.resolve("valid.car"))

        When("publication admission evaluates the unchanged archive")
        noException should be thrownBy CozyCarRuntimeManifest.requireValidArchive(
          validarchive,
          contract,
          "sample",
          "0.0.1-SNAPSHOT",
          "sample",
          expectedGenerationProvenance = None
        )

        And("component bytes are changed without regenerating the manifest")
        _write(staged.resolve("component/main.jar"), "tampered")
        val tamperedarchive = _zip(staged, root.resolve("tampered.car"))
        val error = intercept[Throwable] {
          CozyCarRuntimeManifest.requireValidArchive(
            tamperedarchive,
            contract,
            "sample",
            "0.0.1-SNAPSHOT",
            "sample",
            expectedGenerationProvenance = None
          )
        }

        Then("publication rejects the digest contradiction")
        error.getMessage should include(
          "car-runtime-manifest.json digest mismatch for component/main.jar"
        )
      }
    }
  }

  private def _contract(cncfversion: String): CarMetadataCompatibility.Contract =
    CarMetadataCompatibility.Contract(
      cozyVersion = org.simplemodeling.cozy.BuildInfo.version,
      cncfCompileCoordinate = s"org.goldenport::goldenport-cncf:${cncfversion}",
      cncfCompileTarget = MavenCoordinate(
        "org.goldenport",
        "goldenport-cncf",
        cncfversion
      ),
      runtimeCompatibility = CncfRuntimeCompatibility(
        Some(cncfversion),
        None,
        Vector.empty,
        Vector(cncfversion)
      )
    )

  private def _with_temp_dir[A](body: Path => A): A = {
    val root = Files.createTempDirectory("cozy-car-runtime-manifest")
    try body(root)
    finally _delete_tree(root)
  }

  private def _sha256(bytes: Array[Byte]): String =
    MessageDigest.getInstance("SHA-256").digest(bytes).
      map(byte => f"${byte & 0xff}%02x").mkString

  private def _write(path: Path, content: String): Path = {
    Option(path.getParent).foreach(Files.createDirectories(_))
    Files.writeString(path, content, StandardCharsets.UTF_8)
    path
  }

  private def _zip(root: Path, archive: Path): Path = {
    val output = new ZipOutputStream(Files.newOutputStream(archive))
    try {
      val stream = Files.walk(root)
      try {
        stream.iterator().asScala.
          filter(Files.isRegularFile(_)).
          toVector.
          sortBy(_.toString).
          foreach { path =>
            val relative = root.relativize(path).toString.replace('\\', '/')
            output.putNextEntry(new ZipEntry(relative))
            output.write(Files.readAllBytes(path))
            output.closeEntry()
          }
      } finally {
        stream.close()
      }
    } finally {
      output.close()
    }
    archive
  }

  private def _zip_text(archive: Path, name: String): String = {
    val zip = new ZipFile(archive.toFile)
    try {
      val entry = Option(zip.getEntry(name)).getOrElse(
        throw new IllegalArgumentException(s"Archive entry not found: $name")
      )
      val input = zip.getInputStream(entry)
      try new String(input.readAllBytes(), StandardCharsets.UTF_8)
      finally input.close()
    } finally {
      zip.close()
    }
  }

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

  private def _delete_tree(path: Path): Unit = {
    if (Files.exists(path)) {
      val stream = Files.walk(path)
      try {
        stream.sorted(Comparator.reverseOrder()).iterator().asScala.foreach(
          Files.deleteIfExists(_)
        )
      } finally {
        stream.close()
      }
    }
  }
}
