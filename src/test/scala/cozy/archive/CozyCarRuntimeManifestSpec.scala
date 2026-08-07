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

/*
 * @since   Jul. 29, 2026
 * @version Aug.  7, 2026
 * @author  ASAMI, Tomoharu
 */
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
          _project_yaml("0.1.0-SNAPSHOT")
        )
        val metadata = _write(root.resolve("target/cozy/model-metadata.json"), _model_metadata_with_component_style)
        _write(
          root.resolve("src/main/car/abi-manifest.json"),
          _abi_manifest("0.1.0-SNAPSHOT")
        )
        val development = root.resolve(CozyDevelopmentRuntimeManifest.COMPONENT_DESCRIPTOR_IDENTITY)
        val archive = root.resolve("out/sample.car")
        val mainjar = _write(root.resolve("artifacts/main.jar"), "main")

        When("the development and packaged routes render the same CML projection")
        CozyArchivePackager._write_development_component_descriptor(root, development)
        cozy.CarPackagingSpecSupport.buildCarWithContract(List(
          "--save", archive.toString,
          "--project-dir", root.toString,
          "--main-jar", mainjar.toString,
          "--model-metadata", metadata.toString,
          "--name", "example-sample",
          "--version", "0.1.0-SNAPSHOT",
          "--component", "Sample"
        ))

        Then("both routes retain one semantically identical schema-3 descriptor")
        Json.parse(Files.readString(development, StandardCharsets.UTF_8)) shouldBe
          Json.parse(_zip_text(archive, "component-descriptor.json"))
      }
    }

    "reject a source descriptor when generated CML style metadata is present" in {
      _with_temp_dir { root =>
        Given("a CML-derived project with a competing source descriptor")
        _write(
          root.resolve("project.yaml"),
          _project_yaml("0.1.0-SNAPSHOT")
        )
        _write(root.resolve("target/cozy/model-metadata.json"), _model_metadata_with_component_style)
        _write(root.resolve("src/main/car/component-descriptor.json"), "{}")

        When("development descriptor generation evaluates competing authority")
        val error = intercept[Throwable] {
        CozyArchivePackager._write_development_component_descriptor(
            root,
            root.resolve(CozyDevelopmentRuntimeManifest.COMPONENT_DESCRIPTOR_IDENTITY)
          )
        }

        Then("the CML snapshot remains the sole descriptor authority")
        error.getMessage should include("CML component style snapshot cannot be overridden")
      }
    }

    "preserve a canonical source descriptor for style-less CML development" in {
      _with_temp_dir { root =>
        Given("a CML project without a selected component style and a canonical source descriptor")
        _write(
          root.resolve("project.yaml"),
          _project_yaml("0.1.0-SNAPSHOT")
        )
        _write(root.resolve("src/main/cozy/sample.cml"), "# COMPONENT\n\n## Sample\n")
        val source = _write(
          root.resolve("src/main/car/component-descriptor.json"),
          _descriptor("0.1.0-SNAPSHOT")
        )
        val output = root.resolve(CozyDevelopmentRuntimeManifest.COMPONENT_DESCRIPTOR_IDENTITY)

        When("the development descriptor is prepared")
        CozyArchivePackager._write_development_component_descriptor(root, output)

        Then("the canonical source descriptor remains the development projection")
        Files.readString(output, StandardCharsets.UTF_8) shouldBe Files.readString(source, StandardCharsets.UTF_8)
      }
    }

    "reject descriptor coordinates that contradict the project-owned CAR contract" in {
      _with_temp_dir { root =>
        Given("a project contract and descriptor whose versions differ")
        _write(
          root.resolve("project.json"),
          _project_json("0.0.1-SNAPSHOT")
        )
        _write(root.resolve("src/main/car/component-descriptor.json"), _descriptor("0.0.2-SNAPSHOT"))
        _write(root.resolve("src/main/car/abi-manifest.json"), _abi_manifest("0.0.2-SNAPSHOT"))
        val classes = root.resolve("target/scala-3.3.8/classes")
        _write(classes.resolve("sample.class"), "compiled")
        val classpath = root.resolve(CozyDevelopmentRuntimeManifest.RUNTIME_CLASSPATH_IDENTITY)
        _write(classpath, classes.toString)

        When("development evidence is prepared")
        val exception = intercept[Throwable] {
          CozyDevelopmentRuntimeManifest.write(root, classpath, root.resolve("target/cncf.d/car-runtime-manifest.json"))
        }

        Then("the contradictory descriptor is rejected before manifest publication")
        exception.getMessage should include("component.release-coordinate.mismatch")
        exception.getMessage should include("expected=org.example.Sample:0.0.1-SNAPSHOT")
        exception.getMessage should include("actual=org.example.Sample:0.0.2-SNAPSHOT")
      }
    }

    "write deterministic stable evidence across class-only recompilation" in {
      _with_temp_dir { root =>
        Given("a CAR project with prepared classpath, descriptor, ABI, and mutable classes")
        _write(
          root.resolve("project.json"),
          _project_json("0.0.1-SNAPSHOT")
        )
        _write(
          root.resolve("src/main/car/component-descriptor.json"),
          _descriptor("0.0.1-SNAPSHOT")
        )
        _write(
          root.resolve("src/main/car/abi-manifest.json"),
          _abi_manifest("0.0.1-SNAPSHOT")
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

        Then("the development schema records only stable evidence")
        (after \ "schemaVersion").as[String] shouldBe "cncf.car-development-runtime-manifest.v2"
        (after \ "sourceKind").as[String] shouldBe "development-directory"
        (after \ "car" \ "name").as[String] shouldBe "example-sample"
        (after \ "car" \ "component").as[String] shouldBe "Sample"
        (after \ "evidence").as[Vector[play.api.libs.json.JsObject]].map(entry => (entry \ "path").as[String]) shouldBe Vector(
          "target/cncf.d/runtime-classpath.txt",
          "target/cncf.d/component-descriptor.json",
          "src/main/car/abi-manifest.json"
        )
        before shouldBe after
      }
    }

    "reject a stale or deleted runtime classpath entry" in {
      _with_temp_dir { root =>
        Given("a CAR project with prepared classpath, descriptor, ABI, and mutable classes")
        _write(root.resolve("project.json"), _project_json("0.0.1-SNAPSHOT"))
        _write(root.resolve("src/main/car/component-descriptor.json"), _descriptor("0.0.1-SNAPSHOT"))
        _write(root.resolve("src/main/car/abi-manifest.json"), _abi_manifest("0.0.1-SNAPSHOT"))
        val classes = root.resolve("target/scala-3.3.8/classes")
        _write(classes.resolve("sample.class"), "compiled")
        val classpath = root.resolve(CozyDevelopmentRuntimeManifest.RUNTIME_CLASSPATH_IDENTITY)
        _write(classpath, classes.toString)
        val manifest = root.resolve("target/cncf.d/car-runtime-manifest.json")
        CozyDevelopmentRuntimeManifest.write(root, classpath, manifest)
        Files.delete(classes.resolve("sample.class"))
        Files.delete(classes)

        When("the prepared classpath later names a deleted directory")
        val stale = intercept[Throwable] {
          CozyDevelopmentRuntimeManifest.write(root, classpath, manifest)
        }

        Then("Cozy refuses to publish stale development evidence")
        stale.getMessage should include("Development runtime classpath entry is missing")
        stale.getMessage should include(classes.toString)
      }
    }

    "project style-less generated CML metadata through the canonical development manifest route" in {
      _with_temp_dir { root =>
        Given("a style-less CML source and a canonical CAR descriptor")
        _write(
          root.resolve("project.json"),
          _project_json("0.0.1-SNAPSHOT")
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
          _descriptor("0.0.1-SNAPSHOT", "\"componentStyle\":{\"provider\":\"preserved\"}")
        )
        _write(
          root.resolve("src/main/car/abi-manifest.json"),
          _abi_manifest("0.0.1-SNAPSHOT")
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

        Then("the generated metadata omits a style and selects canonical target descriptor evidence")
        val generated = Json.parse(Files.readString(metadata, StandardCharsets.UTF_8))
        (generated \ "componentStyle").toOption shouldBe None
        val manifest = Json.parse(Files.readString(manifestpath, StandardCharsets.UTF_8))
        (manifest \ "schemaVersion").as[String] shouldBe "cncf.car-development-runtime-manifest.v2"
        (manifest \ "evidence").as[Vector[play.api.libs.json.JsObject]].map(entry => (entry \ "path").as[String]) shouldBe Vector(
          "target/cncf.d/runtime-classpath.txt",
          "target/cncf.d/component-descriptor.json",
          "src/main/car/abi-manifest.json"
        )

        And("unrelated canonical componentStyle source content is preserved")
        Json.parse(Files.readString(root.resolve(CozyDevelopmentRuntimeManifest.COMPONENT_DESCRIPTOR_IDENTITY), StandardCharsets.UTF_8)) shouldBe
          Json.parse(Files.readString(root.resolve("src/main/car/component-descriptor.json"), StandardCharsets.UTF_8))

      }
    }

    "reject style-less development metadata when the descriptor schema is missing" in {
      _with_temp_dir { root =>
        Given("a style-less CML source and a descriptor without canonical schema")
        _write(root.resolve("project.json"), _project_json("0.0.1-SNAPSHOT"))
        val source = _write(root.resolve("src/main/cozy/sample.cml"), "# ENTITY\n\n## Sample\n")
        val metadata = root.resolve("target/cozy/model-metadata.json")
        CmlModelMetadata.write(source, metadata, root.resolve("target/cozy/model-metadata.yaml"), "src/main/cozy/sample.cml", "concept")
        _write(root.resolve("src/main/car/component-descriptor.json"), """{"component":{"namespace":"org.example","id":"Sample","version":"0.0.1-SNAPSHOT"}}""")
        _write(root.resolve("src/main/car/abi-manifest.json"), _abi_manifest("0.0.1-SNAPSHOT"))
        val classes = root.resolve("target/scala-3.3.8/classes")
        _write(classes.resolve("sample.class"), "compiled")
        val classpath = _write(root.resolve(CozyDevelopmentRuntimeManifest.RUNTIME_CLASSPATH_IDENTITY), classes.toString)
        val manifestpath = root.resolve("target/cncf.d/car-runtime-manifest.json")

        When("the development manifest route evaluates the missing schema")
        val error = intercept[Throwable] {
          CozyDevelopmentRuntimeManifest.write(root, classpath, manifestpath)
        }

        Then("development admission rejects the missing canonical schema")
        error.getMessage should include("component.descriptor.schema.unsupported")
      }
    }

    "reject style-less development metadata when the descriptor declares obsolete schema 2" in {
      _with_temp_dir { root =>
        Given("a style-less CML source and a schema-2 descriptor")
        _write(root.resolve("project.json"), _project_json("0.0.1-SNAPSHOT"))
        val source = _write(root.resolve("src/main/cozy/sample.cml"), "# ENTITY\n\n## Sample\n")
        val metadata = root.resolve("target/cozy/model-metadata.json")
        CmlModelMetadata.write(source, metadata, root.resolve("target/cozy/model-metadata.yaml"), "src/main/cozy/sample.cml", "concept")
        _write(root.resolve("src/main/car/component-descriptor.json"), """{"schemaVersion":2,"component":{"namespace":"org.example","id":"Sample","version":"0.0.1-SNAPSHOT"}}""")
        _write(root.resolve("src/main/car/abi-manifest.json"), _abi_manifest("0.0.1-SNAPSHOT"))
        val classes = root.resolve("target/scala-3.3.8/classes")
        _write(classes.resolve("sample.class"), "compiled")
        val classpath = _write(root.resolve(CozyDevelopmentRuntimeManifest.RUNTIME_CLASSPATH_IDENTITY), classes.toString)
        val manifestpath = root.resolve("target/cncf.d/car-runtime-manifest.json")

        When("the development manifest route evaluates obsolete schema 2")
        val error = intercept[Throwable] {
          CozyDevelopmentRuntimeManifest.write(root, classpath, manifestpath)
        }

        Then("development admission rejects obsolete schema 2")
        error.getMessage should include("component.descriptor.schema.unsupported")
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
          Files.writeString(abi, _abi_manifest("0.0.1-SNAPSHOT"))
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
          """{"schemaVersion":3,"component":{"namespace":"org.example.textus","id":"Sample","version":"0.0.1-SNAPSHOT"}}"""
        )
        _write(
          staged.resolve("abi-manifest.json"),
          _abi_manifest("0.0.1-SNAPSHOT", "org.example.textus")
        )
        val coordinate = CozyComponentReleaseCoordinateCodec.admit("org.example.textus", "Sample", "0.0.1-SNAPSHOT", "spec")
        val contract = _contract("0.5.17")
        CozyCarRuntimeManifest.write(
          staged,
          contract,
          coordinate.mavenArtifactId,
          "0.0.1-SNAPSHOT",
          "Sample",
          Some(coordinate)
        )
        val validarchive = _zip(staged, root.resolve("valid.car"))

        When("publication admission evaluates the unchanged archive")
        noException should be thrownBy CozyCarRuntimeManifest.requireValidArchive(
          validarchive,
          contract,
          coordinate,
          expectedGenerationProvenance = None
        )

        And("component bytes are changed without regenerating the manifest")
        _write(staged.resolve("component/main.jar"), "tampered")
        val tamperedarchive = _zip(staged, root.resolve("tampered.car"))
        val error = intercept[Throwable] {
          CozyCarRuntimeManifest.requireValidArchive(
            tamperedarchive,
            contract,
            coordinate,
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

  private def _project_yaml(version: String): String =
    s"""project:
       |  namespace: org.example
       |  id: Sample
       |  kind: car
       |  component:
       |    version: $version
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
       |        tested: [0.5.2-SNAPSHOT]
       |""".stripMargin

  private def _project_json(version: String): String =
    s"""{
       |  "project": {
       |    "namespace": "org.example",
       |    "id": "Sample",
       |    "kind": "car",
       |    "component": {"version": "$version"}
       |  },
       |  "build": {
       |    "cozyVersion": "0.3.1-SNAPSHOT",
       |    "dependencies": {"compile": ["org.goldenport::goldenport-cncf:0.5.2-SNAPSHOT"]}
       |  },
       |  "packaging": {
       |    "kind": "car",
       |    "car": {"runtime": {"cncf": {"minimum": "0.5.2-SNAPSHOT", "excluded": [], "tested": ["0.5.2-SNAPSHOT"]}}}
       |  }
       |}
       |""".stripMargin

  private def _descriptor(version: String): String =
    _descriptor(version, "")

  private def _descriptor(version: String, extension: String): String = {
    val suffix = Option(extension).map(_.trim).filter(_.nonEmpty).map(value => s",$value").getOrElse("")
    s"""{"schemaVersion":3,"component":{"namespace":"org.example","id":"Sample","version":"$version"}$suffix}"""
  }

  private def _abi_manifest(
    version: String,
    namespace: String = "org.example"
  ): String =
    s"""{"format":"cozy.car.abi-manifest.v2","component":{"namespace":"$namespace","id":"Sample","version":"$version"},"abi":{"version":1,"exports":{"components":[{"namespace":"$namespace","id":"Sample"}],"operations":[],"entities":[]},"dependencies":[]}}"""

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
    val workroot = Path.of("target/cozy-test/work/cozy-car-runtime-manifest-spec").toAbsolutePath.normalize()
    Files.createDirectories(workroot)
    val root = Files.createTempDirectory(workroot, "cozy-car-runtime-manifest-")
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
