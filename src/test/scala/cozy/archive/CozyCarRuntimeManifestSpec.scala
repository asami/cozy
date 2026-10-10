package cozy.archive

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path}
import java.util.Comparator
import java.util.zip.{ZipEntry, ZipFile, ZipOutputStream}
import scala.collection.JavaConverters._

import cozy.compatibility.{
  CarMetadataCompatibility,
  CncfRuntimeCompatibility,
  MavenCoordinate
}
import cozy.modeler.{CmlModelMetadata, GenerationProvenance}
import org.scalacheck.{Gen, Prop, Test}
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import play.api.libs.json.Json

/*
 * @since   Jul. 29, 2026
 *  version Aug. 26, 2026
 * @version Oct.  9, 2026
 * @author  ASAMI, Tomoharu
 */
final class CozyCarRuntimeManifestSpec
    extends AnyWordSpec
    with Matchers
    with GivenWhenThen {
  "CNCF CAR development runtime evidence" should {
    "project one generated carrier declaration identically for development and packaged CAR routes" in {
      _with_temp_dir { root =>
        Given("one CAR project with a canonical descriptor, ABI, and Component knowledge consumer contract")
        _write(root.resolve("project.json"), _project_json("0.0.1-SNAPSHOT"))
        _write(root.resolve("project.yaml"), _project_yaml("0.0.1-SNAPSHOT"))
        _write(root.resolve("src/main/car/component-descriptor.json"), _descriptor("0.0.1-SNAPSHOT"))
        _write(root.resolve("src/main/car/abi-manifest.json"), _abi_manifest("0.0.1-SNAPSHOT"))
        val rawcarrier = _consumer_contract("org.example", "Sample", "0.0.1-SNAPSHOT")
        val sourcecarrier = _write(root.resolve("src/main/car/component-knowledge.json"), rawcarrier)
        val classes = root.resolve("target/scala-3.3.8/classes")
        _write(classes.resolve("sample.class"), "compiled")
        val classpath = _write(root.resolve(CozyDevelopmentRuntimeManifest.RUNTIME_CLASSPATH_IDENTITY), classes.toString)
        val developmentmanifest = root.resolve("target/cncf.d/car-runtime-manifest.json")
        val archive = root.resolve("out/sample.car")
        val mainjar = _write(root.resolve("artifacts/main.jar"), "main")

        When("Cozy prepares development evidence and packages the same source tree")
        CozyDevelopmentRuntimeManifest.write(root, classpath, developmentmanifest)
        cozy.CarPackagingSpecSupport.buildCarWithContract(List(
          "--save", archive.toString,
          "--project-dir", root.toString,
          "--main-jar", mainjar.toString,
          "--name", "example-sample",
          "--version", "0.0.1-SNAPSHOT",
          "--component", "Sample"
        ))

        And("the prepared manifest, both descriptors and both carrier copies are read back")
        val manifest = Json.parse(Files.readString(developmentmanifest, StandardCharsets.UTF_8))
        val developmentdescriptor = Json.parse(Files.readString(root.resolve(CozyDevelopmentRuntimeManifest.COMPONENT_DESCRIPTOR_IDENTITY), StandardCharsets.UTF_8))
        val packageddescriptor = Json.parse(_zip_text(archive, "component-descriptor.json"))
        val declaration = (developmentdescriptor \ "componentKnowledge").as[play.api.libs.json.JsObject]
        val sourcebytes = Files.readAllBytes(sourcecarrier).toVector
        val copiedbytes = Files.readAllBytes(root.resolve(CozyDevelopmentRuntimeManifest.COMPONENT_KNOWLEDGE_IDENTITY)).toVector
        val expecteddeclaration = _carrier_declaration
        val packagedcarrier = Json.parse(_zip_text(archive, "component-knowledge.json"))
        val expectedcarrier = Json.parse(rawcarrier)

        Then("both descriptors and raw carrier copies agree while development evidence declares only paths")
        developmentdescriptor shouldBe packageddescriptor
        declaration shouldBe expecteddeclaration
        copiedbytes shouldBe sourcebytes
        packagedcarrier shouldBe expectedcarrier
        manifest shouldBe _development_manifest(componentknowledge = true)
      }
    }

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

    "reject an authored source descriptor carrier declaration as competing authority" in {
      _with_temp_dir { root =>
        Given("a style-less CAR project whose source descriptor declares componentKnowledge directly")
        _write(root.resolve("project.json"), _project_json("0.0.1-SNAPSHOT"))
        _write(
          root.resolve("src/main/car/component-descriptor.json"),
          _descriptor("0.0.1-SNAPSHOT", "\"componentKnowledge\":{}")
        )

        When("development descriptor preparation evaluates the source descriptor")
        val error = intercept[Throwable] {
          CozyArchivePackager._write_development_component_descriptor(
            root,
            root.resolve(CozyDevelopmentRuntimeManifest.COMPONENT_DESCRIPTOR_IDENTITY)
          )
        }

        Then("only Cozy may generate the carrier declaration")
        error.getMessage should include("must not author componentKnowledge")
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

    "retain path-only development metadata across class-only recompilation" in {
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

        When("Cozy prepares and reads the initial development manifest")
        CozyDevelopmentRuntimeManifest.write(root, classpath, manifest)
        val before = Json.parse(Files.readString(manifest, StandardCharsets.UTF_8))

        Given("the compiled class bytes are replaced by another compilation")
        _write(classes.resolve("sample.class"), "second compilation")

        When("Cozy prepares and reads the manifest for the current classes")
        CozyDevelopmentRuntimeManifest.write(root, classpath, manifest)
        val after = Json.parse(Files.readString(manifest, StandardCharsets.UTF_8))

        Then("both documents retain the complete required path-only development projection")
        before shouldBe _development_manifest()
        after shouldBe _development_manifest()
        before shouldBe after
      }
    }

    "retain the complete development projection over fifty valid current-input rewrites" in {
      Given("canonical CAR and ABI contracts with two existing classpath entries and generated valid rewrites")
      val rewrites = for {
        reversed <- Gen.oneOf(true, false)
        duplicates <- Gen.choose(1, 4)
        whitespace <- Gen.oneOf("", " ", "\t", " \t ")
        separator <- Gen.oneOf("\n", java.io.File.pathSeparator)
        formatted <- Gen.oneOf(true, false)
        compiled <- Gen.alphaNumStr
      } yield (reversed, duplicates, whitespace, separator, formatted, compiled)
      val property = Prop.forAll(rewrites) { case (reversed, duplicates, whitespace, separator, formatted, compiled) =>
        _with_temp_dir { root =>
          Given("a canonical project with a compiled directory and an existing dependency entry")
          val classpath = _prepare_development_project(root)
          val classes = root.resolve("target/scala-3.3.8/classes")
          val dependency = _write(root.resolve("artifacts/dependency.jar"), "dependency")
          _write(classpath, Vector(classes, dependency).mkString("\n"))
          val abi = root.resolve("src/main/car/abi-manifest.json")
          val manifest = root.resolve("target/cncf.d/car-runtime-manifest.json")

          When("Cozy prepares and reads the original valid development manifest")
          CozyDevelopmentRuntimeManifest.write(root, classpath, manifest)
          val before = Json.parse(Files.readString(manifest, StandardCharsets.UTF_8))

          Given("valid whitespace, order, duplicate, ABI formatting and compiled-byte changes")
          val entries = if (reversed) Vector(dependency, classes) else Vector(classes, dependency)
          val operands = Vector.fill(duplicates)(entries).flatten.map(path => s"$whitespace$path$whitespace")
          _write(classpath, operands.mkString(separator) + "\n")
          val abijson = Json.parse(_abi_manifest("0.0.1-SNAPSHOT"))
          _write(abi, if (formatted) Json.prettyPrint(abijson) + "\n" else Json.stringify(abijson))
          _write(classes.resolve("sample.class"), compiled)

          When("Cozy validates the current inputs and prepares and reads the manifest again")
          CozyDevelopmentRuntimeManifest.write(root, classpath, manifest)
          val after = Json.parse(Files.readString(manifest, StandardCharsets.UTF_8))
          val currentclasspath = Files.readString(classpath, StandardCharsets.UTF_8)
          val currentabi = Json.parse(Files.readString(abi, StandardCharsets.UTF_8))
          val currentcompiled = Files.readString(classes.resolve("sample.class"), StandardCharsets.UTF_8)

          Then("both manifests equal the independently authored path-only contract despite the actual rewrites")
          before shouldBe _development_manifest()
          after shouldBe _development_manifest()
          after shouldBe before
          currentclasspath shouldBe operands.mkString(separator) + "\n"
          currentabi shouldBe abijson
          currentcompiled shouldBe compiled
          true
        }
      }

      When("ScalaCheck executes fifty actual development writer and readback trials")
      val result = Test.check(Test.Parameters.default.withMinSuccessfulTests(50), property)

      Then("all fifty valid rewrites satisfy the current path and metadata contract")
      result.passed shouldBe true
      result.succeeded shouldBe 50
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

        When("Cozy prepares and reads a successful development manifest")
        CozyDevelopmentRuntimeManifest.write(root, classpath, manifest)

        Then("the current inputs produce the canonical development projection")
        Json.parse(Files.readString(manifest, StandardCharsets.UTF_8)) shouldBe _development_manifest()

        Given("the declared runtime directory is subsequently deleted")
        Files.delete(classes.resolve("sample.class"))
        Files.delete(classes)

        When("the prepared classpath later names a deleted directory")
        val stale = intercept[Throwable] {
          CozyDevelopmentRuntimeManifest.write(root, classpath, manifest)
        }

        Then("Cozy reports the missing actual entry and its declaration")
        stale.getMessage should include("Development runtime classpath entry is missing")
        stale.getMessage should include(classes.toString)
        stale.getMessage should include(classpath.toString)
      }
    }

    "current required inputs" which {
      "reject missing or empty evidence" in {
        Given("the required classpath and ABI evidence, each independently missing or empty")
        val failures = for {
          identity <- Vector("target/cncf.d/runtime-classpath.txt", "src/main/car/abi-manifest.json")
          missing <- Vector(true, false)
        } yield (identity, missing)
        failures.foreach { case (identity, missing) =>
          _with_temp_dir { root =>
            Given("a canonical project with current required evidence")
            val classpath = _prepare_development_project(root)
            val manifest = root.resolve("target/cncf.d/car-runtime-manifest.json")

            When("Cozy prepares and reads a successful manifest")
            CozyDevelopmentRuntimeManifest.write(root, classpath, manifest)

            Then("the current inputs produce the canonical development projection")
            Json.parse(Files.readString(manifest, StandardCharsets.UTF_8)) shouldBe _development_manifest()

            Given(s"required evidence $identity is ${if (missing) "missing" else "empty"}")
            val evidence = root.resolve(identity)
            if (missing) Files.delete(evidence) else _write(evidence, "")

            When("the development writer evaluates the invalid evidence")
            val error = intercept[Throwable] {
              CozyDevelopmentRuntimeManifest.write(root, classpath, manifest)
            }

            Then("the required evidence diagnostic identifies the path")
            error.getMessage should include("Development runtime evidence is missing or empty")
            error.getMessage should include(evidence.toString)
          }
        }
      }

      "reject an invalid classpath operand" in {
        _with_temp_dir { root =>
          Given("a canonical project with valid classpath and ABI evidence")
          val classpath = _prepare_development_project(root)
          val manifest = root.resolve("target/cncf.d/car-runtime-manifest.json")

          When("Cozy prepares and reads a successful manifest")
          CozyDevelopmentRuntimeManifest.write(root, classpath, manifest)

          Then("the current inputs produce the canonical development projection")
          Json.parse(Files.readString(manifest, StandardCharsets.UTF_8)) shouldBe _development_manifest()

          Given("the classpath now contains an operand with an embedded NUL")
          _write(classpath, "invalid\u0000operand")

          When("Cozy evaluates the invalid Path.of operand")
          val error = intercept[Throwable] {
            CozyDevelopmentRuntimeManifest.write(root, classpath, manifest)
          }

          Then("the classpath diagnostic identifies its declaration")
          error.getMessage should include("Development runtime classpath entry is invalid")
          error.getMessage should include(classpath.toString)
        }
      }

      "reject malformed or contradictory ABI evidence" in {
        Given("invalid ABI JSON, an unsupported ABI format and contradictory ABI coordinates")
        val failures = Vector(
          "{" -> "is not valid JSON",
          _abi_manifest("0.0.1-SNAPSHOT").replace("cozy.car.abi-manifest.v2", "obsolete") -> "car.abi.schema.unsupported",
          _abi_manifest("0.0.2-SNAPSHOT") -> "component.release-coordinate.mismatch"
        )
        failures.foreach { case (content, diagnostic) =>
          _with_temp_dir { root =>
            Given("a canonical project with valid classpath and ABI evidence")
            val classpath = _prepare_development_project(root)
            val manifest = root.resolve("target/cncf.d/car-runtime-manifest.json")

            When("Cozy prepares and reads a successful manifest")
            CozyDevelopmentRuntimeManifest.write(root, classpath, manifest)

            Then("the current inputs produce the canonical development projection")
            Json.parse(Files.readString(manifest, StandardCharsets.UTF_8)) shouldBe _development_manifest()

            Given("the ABI evidence is replaced with the selected invalid contract")
            _write(root.resolve("src/main/car/abi-manifest.json"), content)

            When("Cozy evaluates the invalid ABI contract")
            val error = intercept[Throwable] {
              CozyDevelopmentRuntimeManifest.write(root, classpath, manifest)
            }

            Then("the ABI failure remains actionable and identifies its source")
            error.getMessage should include(diagnostic)
            error.getMessage should include("src/main/car/abi-manifest.json")
          }
        }
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

        When("Cozy generates the style-less CML metadata")
        CmlModelMetadata.write(
          source,
          metadata,
          root.resolve("target/cozy/model-metadata.yaml"),
          "src/main/cozy/sample.cml",
          "concept"
        )

        Given("the canonical descriptor, ABI and current runtime classpath")
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
        val generated = Json.parse(Files.readString(metadata, StandardCharsets.UTF_8))
        val manifest = Json.parse(Files.readString(manifestpath, StandardCharsets.UTF_8))
        val developmentdescriptor = Json.parse(Files.readString(root.resolve(CozyDevelopmentRuntimeManifest.COMPONENT_DESCRIPTOR_IDENTITY), StandardCharsets.UTF_8))
        val sourcedescriptor = Json.parse(Files.readString(root.resolve("src/main/car/component-descriptor.json"), StandardCharsets.UTF_8))

        Then("the metadata omits a style and the complete manifest selects canonical path-only evidence")
        (generated \ "componentStyle").toOption shouldBe None
        manifest shouldBe _development_manifest()

        And("unrelated canonical componentStyle source content is preserved")
        developmentdescriptor shouldBe sourcedescriptor

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
    "preserve runtime range across generated versions" in {
      Given("generated CNCF versions and a CAR coordinate with component and ABI files")
      val versions = for {
        patch <- Gen.choose(0, 999)
        suffix <- Gen.oneOf("", "-SNAPSHOT")
        content <- Gen.nonEmptyListOf(Gen.alphaNumChar)
      } yield (s"0.5.${patch}${suffix}", content.mkString)

      When("Cozy projects the CAR coordinate and CNCF runtime range")
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
          (manifest \ "runtime" \ "cncf" \ "minimum").as[String] == cncfversion &&
          (manifest \ "runtime" \ "cncf" \ "tested").as[Vector[String]] == Vector(cncfversion)
        }
      }

      Then("at least fifty generated contracts retain their selected runtime minimum and tested version")
      val result = Test.check(
        Test.Parameters.default.withMinSuccessfulTests(50),
        property
      )
      result.passed shouldBe true
    }

    "admit canonical CARs and reject required runtime or packaged provenance contradictions" in {
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

        When("publication admission evaluates the current selected archive with optional provenance")

        Then("the canonical CAR is admitted")
        noException should be thrownBy CozyCarRuntimeManifest.requireValidArchive(
          validarchive,
          contract,
          coordinate,
          requireGenerationProvenance = false
        )

        Given("packaged provenance with the selected CNCF target, Cozy generator and safe logical paths")
        val provenance = GenerationProvenance.Manifest(
          GenerationProvenance.Inputs(
            cncfTargetVersion = contract.cncfCompileTarget.version,
            cozyGeneratorVersion = contract.cozyVersion,
            simpleModelerBackendVersion = "0.2.0",
            simpleModelingModelVersion = "0.2.0",
            sourceIdentity = "src/main/cozy/Sample.cml"
          ),
          Vector(GenerationProvenance.Artifact("src/main/scala/PublicApi.scala"))
        )
        val provenancepath = staged.resolve("generation-provenance.json")
        _write(provenancepath, provenance.toJsonString)
        CozyCarRuntimeManifest.write(
          staged, contract, coordinate.mavenArtifactId, coordinate.version, "Sample", Some(coordinate)
        )
        val provenancearchive = _zip(staged, root.resolve("provenance.car"))

        When("publication admission requires the actual packaged provenance")

        Then("the selected packaged metadata is admitted")
        noException should be thrownBy CozyCarRuntimeManifest.requireValidArchive(
          provenancearchive, contract, coordinate, requireGenerationProvenance = true
        )

        Given("the required packaged provenance entry is missing")
        Files.delete(provenancepath)
        CozyCarRuntimeManifest.write(
          staged, contract, coordinate.mavenArtifactId, coordinate.version, "Sample", Some(coordinate)
        )
        val missingarchive = _zip(staged, root.resolve("missing-provenance.car"))

        When("publication admission requires provenance from that CAR")
        val missing = intercept[Throwable] {
          CozyCarRuntimeManifest.requireValidArchive(
            missingarchive, contract, coordinate, requireGenerationProvenance = true
          )
        }

        Then("the required named packaged entry is diagnosed")
        missing.getMessage should include("Generated release CAR requires generation-provenance.json")

        Given("packaged provenance declaring a different CNCF target")
        _write(
          provenancepath,
          provenance.copy(inputs = provenance.inputs.copy(cncfTargetVersion = "0.5.18")).toJsonString
        )
        CozyCarRuntimeManifest.write(
          staged, contract, coordinate.mavenArtifactId, coordinate.version, "Sample", Some(coordinate)
        )
        val wrongtargetarchive = _zip(staged, root.resolve("wrong-target-provenance.car"))

        When("publication admission evaluates the required packaged target")
        val wrongtarget = intercept[Throwable] {
          CozyCarRuntimeManifest.requireValidArchive(
            wrongtargetarchive, contract, coordinate, requireGenerationProvenance = true
          )
        }

        Then("the packaged input mismatch is diagnosed")
        wrongtarget.getMessage should include("GENERATION_PROVENANCE_INPUT_MISMATCH")
      }
    }
  }

  private def _development_manifest(componentknowledge: Boolean = false): play.api.libs.json.JsValue = {
    val carrier = if (componentknowledge) """{"path":"target/cncf.d/component-knowledge.json"},""" else ""
    Json.parse(s"""{
      |  "schemaVersion": "cncf.car-development-runtime-manifest.v2",
      |  "sourceKind": "development-directory",
      |  "car": {"name":"example-sample","version":"0.0.1-SNAPSHOT","component":"Sample"},
      |  "runtime": {"cncf":{"minimum":"0.5.2-SNAPSHOT","maximum":null,"excluded":[],"tested":["0.5.2-SNAPSHOT"]}},
      |  "evidence": [
      |    {"path":"target/cncf.d/runtime-classpath.txt"},
      |    {"path":"target/cncf.d/component-descriptor.json"},
      |    $carrier
      |    {"path":"src/main/car/abi-manifest.json"}
      |  ]
      |}""".stripMargin)
  }

  private def _prepare_development_project(root: Path): Path = {
    _write(root.resolve("project.json"), _project_json("0.0.1-SNAPSHOT"))
    _write(root.resolve("src/main/car/component-descriptor.json"), _descriptor("0.0.1-SNAPSHOT"))
    _write(root.resolve("src/main/car/abi-manifest.json"), _abi_manifest("0.0.1-SNAPSHOT"))
    val classes = root.resolve("target/scala-3.3.8/classes")
    _write(classes.resolve("sample.class"), "compiled")
    _write(root.resolve("target/cncf.d/runtime-classpath.txt"), classes.toString)
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

  private def _carrier_declaration: play.api.libs.json.JsObject =
    Json.obj(
      "carrierSchema" -> "cncf.component-knowledge-carrier.v1",
      "consumerContractSchema" -> "cncf.component-knowledge-consumer.v1",
      "logicalPath" -> "component-knowledge.json"
    )

  private def _consumer_contract(namespace: String, id: String, version: String): String = {
    val digest = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"
    s"""{
       |  "schema": "cncf.component-knowledge-consumer.v1",
       |  "componentId": "$namespace.$id",
       |  "logicalRelease": "$version",
       |  "resources": [
       |    {
       |      "logicalIdentity": {
       |        "componentId": "$namespace.$id",
       |        "logicalRelease": "$version",
       |        "parentComponentId": null,
       |        "childRole": "Documentation",
       |        "logicalResource": "urn:cncf:resource:fixture:documentation"
       |      },
       |      "logicalPath": "documentation/fixture.md",
       |      "kind": "documentation",
       |      "role": "documentation",
       |      "language": "en",
       |      "mediaType": "text/markdown",
       |      "size": 1,
       |      "sha256": "$digest",
       |      "metadata": {
       |        "authority": "component",
       |        "stability": "stable",
       |        "source": "component-declared",
       |        "license": "Apache-2.0",
       |        "disclosure": "metadata-only"
       |      },
       |      "availability": "available",
       |      "integrity": "verified",
       |      "authorization": "granted",
       |      "provenance": {
       |        "sourceKind": "expanded-car",
       |        "artifactCoordinate": "org.example:fixture:$version",
       |        "logicalSource": "component-car:fixture",
       |        "resolutionStep": "expanded-car:1",
       |        "externalDeploymentRequired": false,
       |        "matchingDigest": "$digest"
       |      }
       |    }
       |  ]
       |}
       |""".stripMargin
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
