package cozy.modeler

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path, Paths}
import java.security.MessageDigest
import java.util.Comparator
import cozy.CozyCliPreflight
import cozy.compatibility.CncfRuntimeDescriptorContract
import cozy.runtime.CozySbtBridge
import org.scalacheck.{Gen, Prop, Test}
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import play.api.libs.json.{JsObject, Json}

/*
 * @since   Jul. 27, 2026
 * @version Jul. 28, 2026
 * @author  ASAMI, Tomoharu
 */
final class Phase51Cv05GenerationProvenanceSpec
    extends AnyWordSpec
    with Matchers
    with GivenWhenThen {
  "Phase 51 CV-05 generation provenance" should {
    "produce and consume reproducible evidence" which {
      "record exact reproducible generation evidence" in {
        _with_temp_dir("cozy-phase51-cv05-evidence") { directory =>
          Given("one CML source and one validated CNCF target descriptor")
          val source = _source
          val descriptor = _write_descriptor(directory.resolve("runtime.yaml"))
          val descriptordigest = _sha256(descriptor)
          val output = directory.resolve("generated")

          When("Cozy generates Scala with the complete owning-build contract")
          _generate(source, descriptor, descriptordigest, output)

          Then(
            "packaged metadata records exact target, generator, backend, source, and output evidence"
          )
          val provenance = _provenance(output)
          (provenance \ "schemaVersion").as[String] shouldBe
            GenerationProvenance.SCHEMA_VERSION
          (provenance \ "target" \ "cncfVersion").as[String] shouldBe
            "0.5.2-SNAPSHOT"
          (provenance \ "target" \ "runtimeDescriptorSha256")
            .as[String] shouldBe
            descriptordigest
          (provenance \ "generator" \ "cozyVersion").as[String] shouldBe
            org.simplemodeling.cozy.BuildInfo.version
          (provenance \ "generator" \ "simpleModelerBackendVersion")
            .as[String] shouldBe
            org.simplemodeling.cozy.BuildInfo.simpleModelerVersion
          (provenance \ "generator" \ "simpleModelingModelVersion")
            .as[String] shouldBe
            org.simplemodeling.cozy.BuildInfo.simpleModelingModelVersion
          (provenance \ "source" \ "identity").as[String] shouldBe
            "src/main/cozy/address-literate.cml"
          (provenance \ "source" \ "sha256").as[String] shouldBe _sha256(source)
          (provenance \ "output" \ "artifacts")
            .as[Vector[JsObject]] should not be empty
          (provenance \ "output" \ "digest").as[String] should fullyMatch regex
            "[0-9a-f]{64}"
          (provenance \ "evidenceDigest").as[String] should fullyMatch regex
            "[0-9a-f]{64}"

          And("the document contains no machine-local path or timestamp")
          val text =
            Files.readString(_provenance_path(output), StandardCharsets.UTF_8)
          text should not include directory.toAbsolutePath.normalize().toString
          text should not include source.toAbsolutePath.normalize().toString
          text should not include "generatedAt"
          text should not include "timestamp"
        }
      }

      "emit byte-identical evidence for cold and repeated generation" in {
        _with_temp_dir("cozy-phase51-cv05-determinism") { directory =>
          Given(
            "the same CML bytes, target descriptor, and stable source identity"
          )
          val source = _source
          val descriptor = _write_descriptor(directory.resolve("runtime.yaml"))
          val descriptordigest = _sha256(descriptor)
          val cold = directory.resolve("cold")
          val repeated = directory.resolve("repeated")

          When("Cozy generates into two independent output roots")
          _generate(source, descriptor, descriptordigest, cold)
          _generate(source, descriptor, descriptordigest, repeated)

          Then("the provenance bytes and generated-output digest are identical")
          Files.readAllBytes(_provenance_path(cold)) shouldBe
            Files.readAllBytes(_provenance_path(repeated))
          (_provenance(cold) \ "output" \ "digest").as[String] shouldBe
            (_provenance(repeated) \ "output" \ "digest").as[String]
        }
      }

      "validate generated provenance through the owning-build CLI boundary" in {
        _with_temp_dir("cozy-phase51-cv05-cli-validate") { directory =>
          Given("generated provenance and the exact owning-build inputs")
          val source = _copy_source(directory.resolve("address-literate.cml"))
          val descriptor = _write_descriptor(directory.resolve("runtime.yaml"))
          val descriptordigest = _sha256(descriptor)
          val output = directory.resolve("generated")
          _generate(source, descriptor, descriptordigest, output)
          val arguments = Array(
            "generation-provenance-validate",
            source.toString,
            "--save",
            output.toString,
            "--cncf-version",
            "0.5.2-SNAPSHOT",
            "--cncf-runtime-descriptor-sha256",
            descriptordigest,
            "--cozy-generator-version",
            org.simplemodeling.cozy.BuildInfo.version,
            "--generation-source-identity",
            "src/main/cozy/address-literate.cml",
            "--generation-source-sha256",
            _sha256(source)
          )

          When(
            "the CLI validates exact inputs and then receives a contradictory generator"
          )
          cozy.Cozy.main(arguments)
          val error = intercept[Exception] {
            cozy.Cozy.main(
              arguments.updated(
                arguments.indexOf(org.simplemodeling.cozy.BuildInfo.version),
                "0.0.0-contradictory"
              )
            )
          }

          Then(
            "the exact contract succeeds and the contradictory generator is rejected"
          )
          error.getMessage should include(
            "GENERATION_PROVENANCE_INPUT_MISMATCH"
          )
          error.getMessage should include("generator.cozyVersion")
          cozy.Cozy.helpText should include("generation-provenance-validate")
          cozy.Cozy.helpText should include("--generation-source-sha256")
        }
      }

      "bind catalog generation and provenance to one validated descriptor snapshot" in {
        _with_temp_dir("cozy-phase51-cv05-descriptor-snapshot") { directory =>
          Given("a descriptor validated from one exact byte snapshot")
          val descriptor = _write_descriptor(directory.resolve("runtime.yaml"))
          val digest = _sha256(descriptor)
          val validated = CncfRuntimeDescriptorContract.requireValidDescriptor(
            descriptor,
            "0.5.2-SNAPSHOT",
            Some(digest),
            "spec"
          )

          When(
            "the descriptor file changes after validation and the modeler consumes the validated value"
          )
          _write(
            descriptor,
            """schemaVersion: 1
            |runtime: cncf
            |version: 0.5.2-SNAPSHOT
            |predefinedResults:
            |  schemaVersion: cncf.predefined-result.v1
            |  resultNames: [ChangedAfterValidation]
            |""".stripMargin
          )
          val catalog =
            PredefinedResultCatalog.fromValidatedDescriptor(validated)

          Then(
            "the catalog and recorded identity remain bound to the original bytes"
          )
          catalog.names shouldBe empty
          validated.sha256 shouldBe digest
          validated.sha256 should not be _sha256(descriptor)
        }
      }
    }

    "reject contradictory or non-reproducible evidence" which {
      "reject source, generated-output, and internally contradictory evidence tampering" in {
        _with_temp_dir("cozy-phase51-cv05-tamper") { directory =>
          Given(
            "three independently generated and initially valid provenance documents"
          )
          val source = _copy_source(directory.resolve("address-literate.cml"))
          val descriptor = _write_descriptor(directory.resolve("runtime.yaml"))
          val descriptordigest = _sha256(descriptor)
          val sourceoutput = directory.resolve("source-output")
          val generatedoutput = directory.resolve("generated-output")
          val evidenceoutput = directory.resolve("evidence-output")
          Vector(sourceoutput, generatedoutput, evidenceoutput).foreach(
            _generate(source, descriptor, descriptordigest, _)
          )
          val expected = _inputs(source, descriptordigest)

          When(
            "the CML bytes, generated Scala bytes, and one manifest field are each altered"
          )
          Files.writeString(
            source,
            Files.readString(source, StandardCharsets.UTF_8) + "\n# tampered\n",
            StandardCharsets.UTF_8
          )
          val sourcevalidation = GenerationProvenance.validate(
            _provenance_path(sourceoutput),
            sourceoutput,
            source,
            expected
          )
          _restore_source(source)
          val generatedfile = _generated_scala_files(generatedoutput).head
          Files.writeString(
            generatedfile,
            Files.readString(
              generatedfile,
              StandardCharsets.UTF_8
            ) + "\n// tampered\n",
            StandardCharsets.UTF_8
          )
          val outputvalidation = GenerationProvenance.validate(
            _provenance_path(generatedoutput),
            generatedoutput,
            source,
            expected
          )
          val evidencepath = _provenance_path(evidenceoutput)
          val evidence = _provenance(evidenceoutput)
          val altered = evidence.as[JsObject] ++ Json.obj(
            "target" -> ((evidence \ "target").as[JsObject] ++
              Json.obj("cncfVersion" -> "0.5.1"))
          )
          Files.writeString(
            evidencepath,
            Json.prettyPrint(altered) + "\n",
            StandardCharsets.UTF_8
          )
          val evidencevalidation = GenerationProvenance.validate(
            evidencepath,
            evidenceoutput,
            source,
            expected
          )

          Then("each tamper class has a stable semantic diagnostic")
          sourcevalidation.left.toOption.get.map(_.code) should contain(
            GenerationProvenance.DiagnosticCode.SourceTampered
          )
          outputvalidation.left.toOption.get.map(_.code) should contain(
            GenerationProvenance.DiagnosticCode.OutputTampered
          )
          evidencevalidation.left.toOption.get
            .map(_.code) should contain allOf (
            GenerationProvenance.DiagnosticCode.InputMismatch,
            GenerationProvenance.DiagnosticCode.EvidenceTampered
          )
        }
      }

      "reject unsigned fields at every provenance schema level" in {
        _with_temp_dir("cozy-phase51-cv05-unknown-fields") { directory =>
          Given(
            "valid generated provenance, unsupported fields, and a future schema"
          )
          val source = _copy_source(directory.resolve("address-literate.cml"))
          val descriptor = _write_descriptor(directory.resolve("runtime.yaml"))
          val descriptordigest = _sha256(descriptor)
          val output = directory.resolve("generated")
          _generate(source, descriptor, descriptordigest, output)
          val evidence = _provenance(output).as[JsObject]
          val target = (evidence \ "target").as[JsObject]
          val generator = (evidence \ "generator").as[JsObject]
          val sourceevidence = (evidence \ "source").as[JsObject]
          val outputevidence = (evidence \ "output").as[JsObject]
          val artifacts = (outputevidence \ "artifacts").as[Vector[JsObject]]
          val claim = Json.obj("unsignedClaim" -> "trusted")
          val variants = Vector(
            evidence ++ claim,
            evidence ++ Json.obj("target" -> (target ++ claim)),
            evidence ++ Json.obj("generator" -> (generator ++ claim)),
            evidence ++ Json.obj("source" -> (sourceevidence ++ claim)),
            evidence ++ Json.obj("output" -> (outputevidence ++ claim)),
            evidence ++ Json.obj(
              "output" -> (outputevidence ++
                Json.obj(
                  "artifacts" -> artifacts.updated(0, artifacts.head ++ claim)
                ))
            )
          )
          val futureschema = evidence ++ Json.obj(
            "schemaVersion" -> "cozy.generation-provenance.v2",
            "futureClaim" -> "supported-by-v2"
          )
          val expected = _inputs(source, descriptordigest)

          When(
            "the production validator evaluates unsigned fields and the future schema"
          )
          val validations = variants.zipWithIndex.map { case (variant, index) =>
            val provenancepath = directory.resolve(s"unknown-field-$index.json")
            Files.writeString(
              provenancepath,
              Json.prettyPrint(variant) + "\n",
              StandardCharsets.UTF_8
            )
            GenerationProvenance.validate(
              provenancepath,
              output,
              source,
              expected
            )
          }
          val futureschemapath = directory.resolve("future-schema.json")
          Files.writeString(
            futureschemapath,
            Json.prettyPrint(futureschema) + "\n",
            StandardCharsets.UTF_8
          )
          val futureschemavalidation = GenerationProvenance.validate(
            futureschemapath,
            output,
            source,
            expected
          )

          Then(
            "v1 rejects unsigned claims while the future version remains a schema mismatch"
          )
          validations should have size variants.size
          validations.foreach { validation =>
            validation.left.toOption.get.map(_.code) should contain only
              GenerationProvenance.DiagnosticCode.ProvenanceMalformed
            validation.left.toOption.get.map(_.message).mkString should include(
              "unsignedClaim"
            )
          }
          futureschemavalidation.left.toOption.get
            .map(_.code) should contain only
            GenerationProvenance.DiagnosticCode.SchemaMismatch
          futureschemavalidation.left.toOption.get
            .map(_.message)
            .mkString should include(
            "unsupported"
          )
        }
      }

      "reject machine-local source identities before provenance is written" in {
        _with_temp_dir("cozy-phase51-cv05-source-identity") { directory =>
          Given(
            "absolute, empty-after-normalization, missing, and generated source identities"
          )
          val identities = Vector(
            "/Users/example/project/src/main/cozy/model.cml",
            "C:project/src/main/cozy/model.cml",
            "C:/project/src/main/cozy/model.cml",
            ".",
            "./"
          )
          val segment = Gen.nonEmptyListOf(Gen.alphaLowerChar).map(_.mkString)
          val identitygen = Gen.nonEmptyListOf(segment)
          val property = Prop.forAll(identitygen) { segments =>
            val canonical = segments.mkString("/")
            val decorated = ("." +: segments).mkString("//")
            GenerationProvenance.stableSourceIdentity(decorated) == Right(
              canonical
            ) &&
            GenerationProvenance
              .stableSourceIdentity(
                s"$canonical/../escape.cml"
              )
              .isLeft
          }
          val descriptor = _write_descriptor(directory.resolve("runtime.yaml"))
          val invocation = Array(
            "modeler-scala-value",
            "information.cml",
            "--cncf-version",
            "0.5.2-SNAPSHOT",
            "--cncf-runtime-descriptor",
            descriptor.toString,
            "--cncf-runtime-descriptor-sha256",
            _sha256(descriptor)
          )

          When(
            "the production identity normalizer, property, and CLI preflight evaluate them"
          )
          val results =
            identities.map(GenerationProvenance.stableSourceIdentity)
          val propertyresult = Test.check(
            Test.Parameters.default.withMinSuccessfulTests(50),
            property
          )
          val error = intercept[Exception] {
            CozyCliPreflight.parse(invocation)
          }

          Then(
            "canonical relative identities normalize while every escape or absence is rejected"
          )
          results.foreach { result =>
            result.left.toOption.get.map(_.code) shouldBe Vector(
              GenerationProvenance.DiagnosticCode.InputMismatch
            )
          }
          propertyresult.passed shouldBe true
          error.getMessage should include(
            "GENERATION_PROVENANCE_INPUT_MISMATCH"
          )
          error.getMessage should include("--generation-source-identity")
        }
      }
    }

    "bind source lifecycle and owning-build identity" which {
      "reject a CML source that changes after its pre-generation snapshot" in {
        _with_temp_dir("cozy-phase51-cv05-source-snapshot") { directory =>
          Given("a captured CML source and generated Scala output")
          val source = _copy_source(directory.resolve("address-literate.cml"))
          val capturedtext = Files.readString(source, StandardCharsets.UTF_8)
          val snapshot = GenerationProvenance.requireSourceSnapshot(
            source,
            "src/main/cozy/address-literate.cml"
          )
          val output = directory.resolve("generated")
          _write(
            output.resolve(
              "target/scala-3.3.8/src_managed/main/scala/domain/Captured.scala"
            ),
            "package domain\nobject Captured\n"
          )
          val inputs = _inputs(source, "a" * 64)
          _write(_provenance_path(output), """{"stale":true}""")
          GenerationProvenance.prepareOutput(output)

          When(
            "the source changes after capture but generation reads the immutable snapshot"
          )
          Files.writeString(
            source,
            Files.readString(source, StandardCharsets.UTF_8) + "\n# changed\n",
            StandardCharsets.UTF_8
          )
          val (materializedpath, materialized) =
            GenerationProvenance.withCapturedSource(snapshot) {
              capturedsource =>
                capturedsource -> Files.readString(
                  capturedsource,
                  StandardCharsets.UTF_8
                )
            }
          val error = intercept[Exception] {
            GenerationProvenance.write(output, snapshot, inputs)
          }

          Then(
            "generation observes captured bytes and no stale or failed provenance is committed"
          )
          materializedpath should not be source
          materialized shouldBe capturedtext
          error.getMessage should include(
            "GENERATION_PROVENANCE_SOURCE_TAMPERED"
          )
          error.getMessage should include(
            "changed while Scala generation was running"
          )
          Files.exists(_provenance_path(output)) shouldBe false
        }
      }

      "return typed diagnostics for missing sources and invalid expected inputs" in {
        _with_temp_dir("cozy-phase51-cv05-total-validation") { directory =>
          Given(
            "valid generated provenance whose source is subsequently removed"
          )
          val source = _copy_source(directory.resolve("address-literate.cml"))
          val descriptor = _write_descriptor(directory.resolve("runtime.yaml"))
          val descriptordigest = _sha256(descriptor)
          val output = directory.resolve("generated")
          _generate(source, descriptor, descriptordigest, output)
          val expected = _inputs(source, descriptordigest)
          Files.delete(source)

          When(
            "validation evaluates the missing source and an invalid expected digest"
          )
          val missingresult = GenerationProvenance.validate(
            _provenance_path(output),
            output,
            source,
            expected
          )
          val invalidresult = GenerationProvenance.validate(
            _provenance_path(output),
            output,
            source,
            expected.copy(runtimeDescriptorSha256 = "invalid")
          )

          Then("both failures remain inside the typed Either boundary")
          missingresult.left.toOption.get.map(_.code) should contain(
            GenerationProvenance.DiagnosticCode.SourceTampered
          )
          invalidresult.left.toOption.get.map(_.code) should contain(
            GenerationProvenance.DiagnosticCode.InputMismatch
          )
        }
      }

      "derive an exact bridge identity from the owning sbt project and document the CLI option" in {
        _with_temp_dir("cozy-phase51-cv05-bridge-identity") { directory =>
          Given("a bridge source inside its owning sbt project")
          val source = directory.resolve("src/main/cozy/information.cml")
          _write(source, "# VALUE\n\n## Information\n")
          val args = List(
            source.toString,
            "--cncf-runtime-descriptor",
            directory.resolve("runtime.yaml").toString,
            "--cncf-runtime-descriptor-sha256",
            "a" * 64
          )

          When("the bridge creates provenance arguments")
          val identityargs = CozySbtBridge.generationSourceIdentityArgsForTest(
            args,
            Map("sbt.project_dir" -> directory.toString)
          )

          Then(
            "the project-relative identity is exact and discoverable in CLI help"
          )
          identityargs shouldBe List(
            "--generation-source-identity",
            "src/main/cozy/information.cml"
          )
          cozy.Cozy.helpText should include("--generation-source-identity")
          cozy.Cozy.helpText should include(
            "target/cozy/generation-provenance.json"
          )
        }
      }

      "rebind delegated evidence to the final sbt project output" in {
        _with_temp_dir("cozy-phase51-cv05-rebind") { directory =>
          Given("one delegated Cozy output and its owning sbt project")
          val projectroot = directory.resolve("project")
          val source = _copy_source(
            projectroot.resolve("src/main/cozy/address-literate.cml")
          )
          val descriptor = _write_descriptor(directory.resolve("runtime.yaml"))
          val descriptordigest = _sha256(descriptor)
          val delegated =
            projectroot.resolve("target/sbt-cozy/delegate-work/run-0")
          _generate(source, descriptor, descriptordigest, delegated)
          _generated_scala_files(delegated).foreach { generated =>
            val relative = delegated.relativize(generated)
            _write(
              projectroot.resolve(relative),
              Files.readString(generated, StandardCharsets.UTF_8)
            )
          }

          When("the Cozy bridge authority rebinds evidence after sbt-cozy installation")
          val rebound = GenerationProvenance.rebindForPackaging(
            delegatedProvenancePath = _provenance_path(delegated),
            delegatedOutputRoot = delegated,
            projectRoot = projectroot
          )
          _delete_tree(projectroot.resolve("target/sbt-cozy/delegate-work"))

          Then("the project target owns valid provenance for the installed Scala")
          val installed = _provenance_path(projectroot)
          Files.isRegularFile(installed) shouldBe true
          GenerationProvenance.requireValidForPackaging(
            provenancePath = installed,
            projectRoot = projectroot,
            expectedCncfTargetVersion = Some("0.5.2-SNAPSHOT"),
            expectedCozyGeneratorVersion =
              Some(org.simplemodeling.cozy.BuildInfo.version)
          ) shouldBe rebound
        }
      }

      "validate the exact delegated manifest selected for rebinding" in {
        _with_temp_dir("cozy-phase51-cv05-rebind-path") { directory =>
          Given("a valid canonical delegated manifest and a tampered alternate path")
          val projectroot = directory.resolve("project")
          val source = _copy_source(
            projectroot.resolve("src/main/cozy/address-literate.cml")
          )
          val descriptor = _write_descriptor(directory.resolve("runtime.yaml"))
          val descriptordigest = _sha256(descriptor)
          val delegated =
            projectroot.resolve("target/sbt-cozy/delegate-work/run-0")
          _generate(source, descriptor, descriptordigest, delegated)
          _generated_scala_files(delegated).foreach { generated =>
            val relative = delegated.relativize(generated)
            _write(
              projectroot.resolve(relative),
              Files.readString(generated, StandardCharsets.UTF_8)
            )
          }
          val evidence = _provenance(delegated).as[JsObject]
          val alternate = _write(
            directory.resolve("alternate-provenance.json"),
            Json.prettyPrint(
              evidence ++ Json.obj("evidenceDigest" -> ("0" * 64))
            ) + "\n"
          )

          When("the rebind API receives the alternate path while the canonical manifest remains valid")
          val error = intercept[Exception] {
            GenerationProvenance.rebindForPackaging(
              delegatedProvenancePath = alternate,
              delegatedOutputRoot = delegated,
              projectRoot = projectroot
            )
          }

          Then("the selected manifest fails evidence validation without publishing final provenance")
          error.getMessage should include(
            "GENERATION_PROVENANCE_EVIDENCE_TAMPERED"
          )
          Files.exists(_provenance_path(projectroot)) shouldBe false
        }
      }
    }
  }

  private def _source: Path =
    Paths
      .get(sys.props("user.dir"))
      .toAbsolutePath
      .normalize()
      .resolve("src/test/resources/modeler/address-literate.cml")

  private def _generate(
      source: Path,
      descriptor: Path,
      descriptordigest: String,
      output: Path
  ): Unit =
    cozy.Cozy.main(
      Array(
        "modeler-scala-value",
        source.toString,
        "--cncf-version",
        "0.5.2-SNAPSHOT",
        "--cozy-generator-version",
        org.simplemodeling.cozy.BuildInfo.version,
        "--cncf-runtime-descriptor",
        descriptor.toString,
        "--cncf-runtime-descriptor-sha256",
        descriptordigest,
        "--simplemodeling-model-version",
        org.simplemodeling.cozy.BuildInfo.simpleModelingModelVersion,
        "--generation-source-identity",
        "src/main/cozy/address-literate.cml",
        "--save",
        output.toString
      )
    )

  private def _inputs(
      source: Path,
      descriptordigest: String
  ): GenerationProvenance.Inputs =
    GenerationProvenance.Inputs(
      cncfTargetVersion = "0.5.2-SNAPSHOT",
      runtimeDescriptorSha256 = descriptordigest,
      cozyGeneratorVersion = org.simplemodeling.cozy.BuildInfo.version,
      simpleModelerBackendVersion =
        org.simplemodeling.cozy.BuildInfo.simpleModelerVersion,
      simpleModelingModelVersion =
        org.simplemodeling.cozy.BuildInfo.simpleModelingModelVersion,
      sourceIdentity = "src/main/cozy/address-literate.cml",
      sourceSha256 = _sha256(source)
    )

  private def _provenance(output: Path) =
    Json.parse(
      Files.readString(_provenance_path(output), StandardCharsets.UTF_8)
    )

  private def _provenance_path(output: Path): Path =
    output.resolve(GenerationProvenance.METADATA_PATH)

  private def _generated_scala_files(output: Path): Vector[Path] = {
    val stream = Files.walk(output.resolve("target"))
    try {
      import scala.collection.JavaConverters._
      stream
        .iterator()
        .asScala
        .filter(path =>
          Files.isRegularFile(path) && path.getFileName.toString
            .endsWith(".scala")
        )
        .toVector
        .sortBy(_.toString)
    } finally {
      stream.close()
    }
  }

  private def _write_descriptor(path: Path): Path =
    _write(
      path,
      """schemaVersion: 1
        |runtime: cncf
        |version: 0.5.2-SNAPSHOT
        |predefinedResults:
        |  schemaVersion: cncf.predefined-result.v1
        |  resultNames: []
        |""".stripMargin
    )

  private def _copy_source(path: Path): Path =
    _write(path, Files.readString(_source, StandardCharsets.UTF_8))

  private def _restore_source(path: Path): Unit = {
    Files.writeString(
      path,
      Files.readString(_source, StandardCharsets.UTF_8),
      StandardCharsets.UTF_8
    )
    ()
  }

  private def _write(path: Path, content: String): Path = {
    Option(path.getParent).foreach(Files.createDirectories(_))
    Files.writeString(path, content, StandardCharsets.UTF_8)
    path.toAbsolutePath.normalize()
  }

  private def _sha256(path: Path): String =
    MessageDigest
      .getInstance("SHA-256")
      .digest(Files.readAllBytes(path))
      .map(byte => f"${byte & 0xff}%02x")
      .mkString

  private def _with_temp_dir[A](prefix: String)(body: Path => A): A = {
    val directory = Files.createTempDirectory(prefix)
    try body(directory)
    finally _delete_tree(directory)
  }

  private def _delete_tree(path: Path): Unit =
    if (Files.exists(path)) {
      val stream = Files.walk(path)
      try
        stream
          .sorted(Comparator.reverseOrder())
          .forEach(path => Files.deleteIfExists(path))
      finally stream.close()
    }
}
