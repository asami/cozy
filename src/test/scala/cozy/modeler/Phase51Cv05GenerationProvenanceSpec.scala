package cozy.modeler

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path, Paths}
import java.nio.file.attribute.PosixFilePermission
import scala.collection.JavaConverters._
import java.util.Comparator
import cozy.CozyCliPreflight
import cozy.compatibility.CncfRuntimeDescriptorContract
import cozy.runtime.CozySbtBridge
import org.scalacheck.{Gen, Prop, Test}
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import play.api.libs.json.{JsBoolean, JsNull, JsNumber, JsObject, JsString, JsValue, Json}

/*
 * @since   Jul. 27, 2026
 *  version Jul. 28, 2026
 *  version Aug.  7, 2026
 * @version Oct.  9, 2026
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
          val output = directory.resolve("generated")

          When("Cozy generates Scala with the complete owning-build contract")
          _generate(source, descriptor, output)

          val provenance = _provenance(output).as[JsObject]
          val text = Files.readString(_provenance_path(output), StandardCharsets.UTF_8)
          val paths = _generated_scala_files(output).map(path =>
            output.relativize(path).toString.replace(java.io.File.separatorChar, '/')
          )
          val validated = GenerationProvenance.requireValidGeneratedOutput(output, source, _inputs())

          Then("the document contains exactly the nonhash target, generator, source, and actual ordered paths")
          provenance shouldBe validated.toJson
          provenance.keys shouldBe Set("schemaVersion", "target", "generator", "source", "output")
          (provenance \ "schemaVersion").as[String] shouldBe GenerationProvenance.SCHEMA_VERSION
          (provenance \ "target").as[JsObject] shouldBe Json.obj("cncfVersion" -> "0.5.2-SNAPSHOT")
          (provenance \ "generator").as[JsObject] shouldBe Json.obj(
            "cozyVersion" -> org.simplemodeling.cozy.BuildInfo.version,
            "simpleModelerBackendVersion" -> org.simplemodeling.cozy.BuildInfo.simpleModelerVersion,
            "simpleModelingModelVersion" -> org.simplemodeling.cozy.BuildInfo.simpleModelingModelVersion
          )
          (provenance \ "source").as[JsObject] shouldBe Json.obj("identity" -> "src/main/cozy/address-literate.cml")
          paths should not be empty
          (provenance \ "output").as[JsObject] shouldBe Json.obj(
            "artifacts" -> paths.map(path => Json.obj("path" -> path))
          )
          text should not include directory.toAbsolutePath.normalize().toString
          text should not include source.toAbsolutePath.normalize().toString
          text should not include "generatedAt"
          text should not include "timestamp"
        }
      }

      "emit deterministic nonhash metadata for cold and repeated generation" in {
        _with_temp_dir("cozy-phase51-cv05-determinism") { directory =>
          Given(
            "the same CML bytes, target descriptor, and stable source identity"
          )
          val source = _source
          val descriptor = _write_descriptor(directory.resolve("runtime.yaml"))
          val cold = directory.resolve("cold")
          val repeated = directory.resolve("repeated")

          When("Cozy generates into two independent output roots")
          _generate(source, descriptor, cold)
          _generate(source, descriptor, repeated)

          val coldtext = Files.readString(_provenance_path(cold), StandardCharsets.UTF_8)
          val repeatedtext = Files.readString(_provenance_path(repeated), StandardCharsets.UTF_8)
          val coldpaths = _generated_scala_files(cold).map(cold.relativize(_).toString)
          val repeatedpaths = _generated_scala_files(repeated).map(repeated.relativize(_).toString)

          Then("both roots expose equal nonhash metadata and ordered relative artifact paths")
          coldtext shouldBe repeatedtext
          coldpaths shouldBe repeatedpaths
          coldpaths should not be empty
        }
      }

      "validate generated provenance through the owning-build CLI boundary" in {
        _with_temp_dir("cozy-phase51-cv05-cli-validate") { directory =>
          Given("generated provenance and the exact owning-build inputs")
          val source = _copy_source(directory.resolve("address-literate.cml"))
          val descriptor = _write_descriptor(directory.resolve("runtime.yaml"))
          val output = directory.resolve("generated")
          When("Cozy produces the current result for the owning-build validator")
          _generate(source, descriptor, output)
          val arguments = Array(
            "generation-provenance-validate",
            source.toString,
            "--save",
            output.toString,
            "--cncf-version",
            "0.5.2-SNAPSHOT",
            "--cozy-generator-version",
            org.simplemodeling.cozy.BuildInfo.version,
            "--generation-source-identity",
            "src/main/cozy/address-literate.cml"
          )

          When(
            "the CLI validates exact inputs and then receives a contradictory generator"
          )
          cozy.Cozy.main(arguments)
          val contradictions = Vector(
            "--cozy-generator-version" -> "0.0.0-contradictory",
            "--cncf-version" -> "0.0.0-target",
            "--generation-source-identity" -> "src/main/cozy/other.cml"
          ).map { case (option, value) =>
            intercept[Exception] {
              cozy.Cozy.main(arguments.updated(arguments.indexOf(option) + 1, value))
            }
          }
          val modelerror = intercept[Exception] {
            cozy.Cozy.main(arguments ++ Array("--simplemodeling-model-version", "0.0.0-model"))
          }
          val backendresult = GenerationProvenance.validate(
            _provenance_path(output), output, source,
            _inputs().copy(simpleModelerBackendVersion = "0.0.0-backend")
          )

          Then("the exact hash-free invocation succeeds and every selected version or identity contradiction is diagnosed")
          val errors = contradictions :+ modelerror
          errors.foreach { error =>
            error.getMessage should include("GENERATION_PROVENANCE_INPUT_MISMATCH")
            error.getMessage should include("expected")
            error.getMessage should include("actual")
            error.getMessage should include("correctiveAction")
          }
          contradictions(0).getMessage should include("generator.cozyVersion")
          contradictions(1).getMessage should include("target.cncfVersion")
          contradictions(2).getMessage should include("source.identity")
          modelerror.getMessage should include("generator.simpleModelingModelVersion")
          backendresult.left.toOption.get.map(_.source) should contain("generator.simpleModelerBackendVersion")
          cozy.Cozy._help_text should include("generation-provenance-validate")
          cozy.Cozy._help_text should not include "--generation-source-sha256"
        }
      }

      "use one parsed descriptor value for the selected catalog" in {
        _with_temp_dir("cozy-phase51-cv05-descriptor-snapshot") { directory =>
          Given("a descriptor parsed once before its selected path changes")
          val descriptor = _write_descriptor(directory.resolve("runtime.yaml"))
          val validated = CncfRuntimeDescriptorContract.requireValidDescriptor(
            descriptor,
            "0.5.2-SNAPSHOT",
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
            |  results:
            |    ChangedAfterValidation:
            |      runtimeClassName: org.example.ChangedAfterValidation
            |      fields: []
            |""".stripMargin
          )
          val catalog =
            PredefinedResultCatalog.fromValidatedDescriptor(validated)

          val changedvalidated = CncfRuntimeDescriptorContract.requireValidDescriptor(
            descriptor, "0.5.2-SNAPSHOT", "spec"
          )
          val changedcatalog = PredefinedResultCatalog.fromValidatedDescriptor(changedvalidated)

          Then("the original parsed catalog stays empty and a new read resolves the changed catalog with the same target and path")
          catalog.names shouldBe empty
          changedcatalog.names should contain("ChangedAfterValidation")
          validated.targetVersion shouldBe "0.5.2-SNAPSHOT"
          changedvalidated.targetVersion shouldBe validated.targetVersion
          validated.path shouldBe descriptor.toAbsolutePath.normalize()
          changedvalidated.path shouldBe validated.path
        }
      }
    }

    "reject contradictory or non-reproducible evidence" which {
      "admit changed readable contents while rejecting missing paths and known-input contradictions" in {
        _with_temp_dir("cozy-phase51-cv05-stage-contract") { directory =>
          Given("one generated result with a readable source and a declared nonempty Scala path inventory")
          val source = _copy_source(directory.resolve("address-literate.cml"))
          val descriptor = _write_descriptor(directory.resolve("runtime.yaml"))
          val output = directory.resolve("generated")
          When("Cozy produces the initial metadata and generated paths")
          _generate(source, descriptor, output)
          val expected = _inputs()

          When("source and output contents change, followed by added and removed Scala paths and a target contradiction")
          _write(source, "# VALUE\n\n## ChangedSource\n")
          val generatedfile = _generated_scala_files(output).head
          _write(generatedfile, "package domain\nobject ChangedOutput\n")
          val changed = GenerationProvenance.validate(_provenance_path(output), output, source, expected)
          val added = _write(output.resolve("target/generated/Added.scala"), "object Added\n")
          val addedresult = GenerationProvenance.validate(_provenance_path(output), output, source, expected)
          Files.delete(added)
          Files.delete(generatedfile)
          val removedresult = GenerationProvenance.validate(_provenance_path(output), output, source, expected)
          val document = _provenance(output).as[JsObject]
          _write(_provenance_path(output), Json.prettyPrint(document ++ Json.obj(
            "target" -> Json.obj("cncfVersion" -> "0.5.1")
          )))
          val contradictory = GenerationProvenance.validate(_provenance_path(output), output, source, expected)

          Then("content changes alone are valid while path and exact-target failures remain actionable")
          changed.isRight shouldBe true
          Vector(addedresult, removedresult).foreach { result =>
            result.left.toOption.get.map(_.code) should contain(GenerationProvenance.DiagnosticCode.OutputTampered)
            result.left.toOption.get.map(_.message).mkString should include("path inventory")
          }
          contradictory.left.toOption.get.map(_.code) should contain(GenerationProvenance.DiagnosticCode.InputMismatch)
        }
      }

      "ignore arbitrary historical extras while requiring every known field and supported schema" in {
        _with_temp_dir("cozy-phase51-cv05-unknown-fields") { directory =>
          Given("a current V1 document and nonscalar historical extras at every object level")
          val source = _copy_source(directory.resolve("address-literate.cml"))
          val output = directory.resolve("generated")
          _write(output.resolve("target/generated/Address.scala"), "object Address\n")
          When("the direct producer selects its readable source and writes the known projection")
          val sourceinput = GenerationProvenance.requireSourceInput(source, _inputs().sourceIdentity)
          val manifest = GenerationProvenance.write(output, sourceinput, _inputs())
          val document = manifest.toJson
          val historical = _with_extras(_with_extras(document, "sha256", Json.obj("retired" -> Json.arr(true, 7))),
            "runtimeDescriptorSha256", Json.arr(false, "retired")) ++
            Json.obj("evidenceDigest" -> Json.arr("ignored", false))
          val invalid = Vector(
            document - "target",
            document ++ Json.obj("target" -> Json.obj("cncfVersion" -> 42)),
            document ++ Json.obj("generator" -> Json.obj("cozyVersion" -> "selected")),
            document ++ Json.obj("source" -> Json.obj("identity" -> false)),
            document ++ Json.obj("output" -> Json.obj("artifacts" -> Json.arr(4))),
            document ++ Json.obj("schemaVersion" -> "cozy.generation-provenance.future"),
            document - "schemaVersion",
            document ++ Json.obj("source" -> Json.obj("identity" -> "src//model.cml")),
            document ++ Json.obj("output" -> Json.obj("artifacts" -> Json.arr(Json.obj("path" -> "C:/model.scala"))))
          )
          val names = Gen.nonEmptyListOf(Gen.alphaLowerChar).map(chars => "extra_" + chars.mkString)
          val values: Gen[JsValue] = Gen.oneOf(
            Gen.alphaStr.map(JsString.apply),
            Gen.choose(-100, 100).map(number => JsNumber(BigDecimal(number))),
            Gen.oneOf(true, false).map(JsBoolean.apply),
            Gen.const(JsNull),
            Gen.alphaStr.map(value => Json.arr(Json.obj("value" -> value), false))
          )
          val identities = Gen.nonEmptyListOf(Gen.alphaLowerChar).map(chars => s"src/main/cozy/${chars.mkString}.cml")
          val property = Prop.forAll(names, values, identities) { (name, value, sourceidentity) =>
            val inputs = _inputs().copy(sourceIdentity = sourceidentity)
            val selected = GenerationProvenance.requireSourceInput(source, sourceidentity)
            val current = GenerationProvenance.write(output, selected, inputs)
            _write(_provenance_path(output), Json.prettyPrint(_with_extras(current.toJson, name, value)))
            GenerationProvenance.validate(_provenance_path(output), output, source, inputs) == Right(current)
          }

          When("the production loader projects historical extras and evaluates invalid known fields and twenty arbitrary extra cases")
          _write(_provenance_path(output), Json.prettyPrint(historical))
          val accepted = GenerationProvenance.validate(_provenance_path(output), output, source, _inputs())
          val reemitted = accepted.toOption.get.toJson
          val invalidresults = invalid.map { variant =>
            _write(_provenance_path(output), Json.prettyPrint(variant))
            GenerationProvenance.validate(_provenance_path(output), output, source, _inputs())
          }
          val propertyresult = Test.check(Test.Parameters.default.withMinSuccessfulTests(20), property)

          Then("unknown fields have no typed or emitted effect and known-field, path, and schema failures remain failures")
          accepted shouldBe Right(manifest)
          reemitted shouldBe document
          invalidresults.foreach(_.isLeft shouldBe true)
          invalidresults(1).left.toOption.get.map(_.code) should contain(GenerationProvenance.DiagnosticCode.ProvenanceMalformed)
          invalidresults(5).left.toOption.get.map(_.code) should contain(GenerationProvenance.DiagnosticCode.SchemaMismatch)
          invalidresults(6).left.toOption.get.map(_.code) should contain(GenerationProvenance.DiagnosticCode.SchemaMismatch)
          propertyresult.passed shouldBe true
        }
      }

      "admit packaged V1 historical metadata without requiring its local source" in {
        _with_temp_dir("cozy-phase51-cv05-packaged-without-source") { directory =>
          Given("a valid direct V1 projection with nonscalar historical extras at every object level")
          val source = _copy_source(directory.resolve("address-literate.cml"))
          val output = directory.resolve("generated")
          _write(output.resolve("target/generated/Address.scala"), "object Address\n")
          When("the producer selects the actual readable source reference")
          val selected = GenerationProvenance.requireSourceInput(source, _inputs().sourceIdentity)
          val manifest = GenerationProvenance.write(output, selected, _inputs())
          val historical = _with_extras(_with_extras(manifest.toJson, "sha256", Json.arr(false, 17)),
            "digest", Json.obj("retired" -> true)) ++ Json.obj("evidenceDigest" -> Json.arr("old", false))

          When("packaged-only validation reads the selected metadata after the local source is removed")
          _write(_provenance_path(output), Json.prettyPrint(historical))
          Files.delete(source)
          val admitted = GenerationProvenance.requireValidPackagedMetadata(
            _provenance_path(output), "0.5.2-SNAPSHOT", org.simplemodeling.cozy.BuildInfo.version
          )
          val targeterror = intercept[Exception] {
            GenerationProvenance.requireValidPackagedMetadata(
              _provenance_path(output), "0.0.0-other", org.simplemodeling.cozy.BuildInfo.version
            )
          }
          val generatorerror = intercept[Exception] {
            GenerationProvenance.requireValidPackagedMetadata(_provenance_path(output), "0.5.2-SNAPSHOT", "0.0.0-other")
          }

          Then("the known typed projection is admitted and re-emitted alone while selected-version contradictions still fail")
          admitted shouldBe manifest
          admitted.toJson shouldBe manifest.toJson
          targeterror.getMessage should include("GENERATION_PROVENANCE_INPUT_MISMATCH")
          targeterror.getMessage should include("target.cncfVersion")
          generatorerror.getMessage should include("GENERATION_PROVENANCE_INPUT_MISMATCH")
          generatorerror.getMessage should include("generator.cozyVersion")
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
            descriptor.toString
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
      "use the actual selected source after readable content changes" in {
        _with_temp_dir("cozy-phase51-cv05-source-input") { directory =>
          Given("an explicit readable source reference selected before its model changes")
          val source = _copy_source(directory.resolve("address-literate.cml"))
          When("the producer selects the actual readable source reference")
          val selected = GenerationProvenance.requireSourceInput(source, _inputs().sourceIdentity)
          val descriptor = _write_descriptor(directory.resolve("runtime.yaml"))
          val output = directory.resolve("generated")

          When("the selected source changes and actual generation and model metadata read its current path")
          _write(source, """# VALUE
            |
            |## CurrentAddress
            |
            |### ATTRIBUTE
            |
            || name | type | multiplicity |
            ||------+------|--------------|
            || currentName | string | 1 |
            |""".stripMargin)
          _generate(selected.path, descriptor, output)
          val manifest = GenerationProvenance.write(output, selected, _inputs())
          val paths = _generated_scala_files(output).map(_.getFileName.toString)
          val metadata = Files.readString(output.resolve("target/cozy/model-metadata.json"), StandardCharsets.UTF_8)

          Then("the reference keeps the actual normalized path and both consumers expose the current model")
          selected.path shouldBe source.toAbsolutePath.normalize()
          selected.identity shouldBe _inputs().sourceIdentity
          paths should contain("CurrentAddress.scala")
          (Json.parse(metadata) \ "modelElements").as[Vector[JsValue]] shouldBe Vector(
            Json.obj(
              "kind" -> "value",
              "name" -> "CurrentAddress",
              "termId" -> "cml:current-address",
              "glossaryPath" -> "glossary/cml/current-address.html",
              "descriptive" -> Json.obj(
                "label" -> "CurrentAddress",
                "brief" -> "",
                "summary" -> "",
                "description" -> ""
              ),
              "narrative" -> "",
              "inputKind" -> "",
              "representation" -> "",
              "underlyingType" -> "",
              "relationships" -> Json.arr(),
              "constraints" -> Json.arr(),
              "implementation" -> Json.arr(),
              "rdfCandidates" -> Json.arr(),
              "fields" -> Json.arr(
                Json.obj(
                  "name" -> "currentName",
                  "type" -> "string",
                  "multiplicity" -> "1",
                  "required" -> true,
                  "constraints" -> Json.arr()
                )
              )
            )
          )
          manifest.inputs shouldBe _inputs()
        }
      }

      "return typed diagnostics for missing sources and invalid expected inputs" in {
        _with_temp_dir("cozy-phase51-cv05-total-validation") { directory =>
          Given(
            "valid generated provenance whose source is subsequently removed"
          )
          val source = _copy_source(directory.resolve("address-literate.cml"))
          val descriptor = _write_descriptor(directory.resolve("runtime.yaml"))
          val output = directory.resolve("generated")
          When("Cozy produces metadata for the selected source")
          _generate(source, descriptor, output)
          val expected = _inputs()
          Files.delete(source)

          When(
            "validation evaluates the missing source and an empty selected version"
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
            expected.copy(cncfTargetVersion = " ")
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

      "reject missing source, empty output and invalid inputs before writing final metadata" in {
        _with_temp_dir("cozy-phase51-cv05-write-preservation") { directory =>
          Given("three previous successful metadata documents and selected source references")
          val source = _copy_source(directory.resolve("address-literate.cml"))
          When("the producer selects the actual readable source reference")
          val selected = GenerationProvenance.requireSourceInput(source, _inputs().sourceIdentity)
          val roots = Vector("missing-source", "empty-output", "invalid-input").map(directory.resolve(_))
          When("the direct producer writes each initial successful metadata document")
          roots.foreach { root =>
            _write(root.resolve("target/generated/Address.scala"), "object Address\n")
            GenerationProvenance.write(root, selected, _inputs())
          }
          val previous = roots.map(root => Files.readString(_provenance_path(root), StandardCharsets.UTF_8))

          Given("the first selected source is now missing")
          Files.delete(source)
          When("the first producer validates its selected source before writing metadata")
          val missingerror = intercept[Exception] { GenerationProvenance.write(roots(0), selected, _inputs()) }
          Given("the source is readable again but the second output inventory is empty")
          _copy_source(source)
          Files.delete(roots(1).resolve("target/generated/Address.scala"))
          When("the second producer validates its generated-output inventory before writing metadata")
          val emptyerror = intercept[Exception] { GenerationProvenance.write(roots(1), selected, _inputs()) }
          When("the third producer receives an empty generator version")
          val invaliderror = intercept[Exception] {
            GenerationProvenance.write(roots(2), selected, _inputs().copy(cozyGeneratorVersion = ""))
          }
          val current = roots.map(root => Files.readString(_provenance_path(root), StandardCharsets.UTF_8))
          Then("each typed input rejection precedes the final write and the previous documents remain unchanged")
          missingerror.getMessage should include("GENERATION_PROVENANCE_SOURCE_TAMPERED")
          emptyerror.getMessage should include("GENERATION_PROVENANCE_OUTPUT_TAMPERED")
          invaliderror.getMessage should include("GENERATION_PROVENANCE_INPUT_MISMATCH")
          current shouldBe previous
        }
      }

      "preserve successful metadata and generated output when distributed descriptor preflight fails" in {
        _with_temp_dir("cozy-phase51-cv05-preflight-preservation") { directory =>
          Given("a previously generated result and its selected runtime descriptor")
          val source = _copy_source(directory.resolve("address-literate.cml"))
          val descriptor = _write_descriptor(directory.resolve("runtime.yaml"))
          val output = directory.resolve("generated")
          When("Cozy produces the initial successful metadata and generated output")
          _generate(source, descriptor, output)
          val beforemetadata = Files.readString(_provenance_path(output), StandardCharsets.UTF_8)
          val generated = _generated_scala_files(output)
          val beforeoutput = generated.map(path => Files.readString(path, StandardCharsets.UTF_8))

          When("the selected descriptor becomes malformed and actual generation runs preflight")
          _write(descriptor, "schemaVersion: [\n")
          val error = intercept[Exception] { _generate(source, descriptor, output) }
          val aftermetadata = Files.readString(_provenance_path(output), StandardCharsets.UTF_8)
          val afterpaths = _generated_scala_files(output)
          val afteroutput = afterpaths.map(path => Files.readString(path, StandardCharsets.UTF_8))

          Then("descriptor admission fails before source emission and leaves the previous successful outputs observable")
          error.getMessage should include("DESCRIPTOR")
          aftermetadata shouldBe beforemetadata
          afterpaths shouldBe generated
          afteroutput shouldBe beforeoutput
        }
      }

      "return actual source and output access failures without comparing contents" in {
        _with_temp_dir("cozy-phase51-cv05-unreadable") { directory =>
          Given("valid metadata with one required source and one required Scala path")
          val source = _copy_source(directory.resolve("address-literate.cml"))
          val output = directory.resolve("generated")
          val artifact = _write(output.resolve("target/generated/Address.scala"), "object Address\n")
          When("the producer writes metadata for the readable source and Scala file")
          GenerationProvenance.write(output,
            GenerationProvenance.requireSourceInput(source, _inputs().sourceIdentity), _inputs())
          val sourcepermissions = Files.getPosixFilePermissions(source)
          val outputpermissions = Files.getPosixFilePermissions(artifact)
          val readpermissions = Set(PosixFilePermission.OWNER_READ,
            PosixFilePermission.GROUP_READ, PosixFilePermission.OTHERS_READ)

          When("each selected regular file has its read permission removed at the validation stage")
          Files.setPosixFilePermissions(source, sourcepermissions.asScala.filterNot(readpermissions.contains).toSet.asJava)
          val sourcefailure = try GenerationProvenance.validate(_provenance_path(output), output, source, _inputs())
          finally Files.setPosixFilePermissions(source, sourcepermissions)
          Files.setPosixFilePermissions(artifact, outputpermissions.asScala.filterNot(readpermissions.contains).toSet.asJava)
          val outputfailure = try GenerationProvenance.validate(_provenance_path(output), output, source, _inputs())
          finally Files.setPosixFilePermissions(artifact, outputpermissions)

          Then("genuine unreadability retains the typed source and output failure categories with corrective actions")
          sourcefailure.left.toOption.get.map(_.code) should contain(GenerationProvenance.DiagnosticCode.SourceTampered)
          outputfailure.left.toOption.get.map(_.code) should contain(GenerationProvenance.DiagnosticCode.OutputTampered)
          (sourcefailure.left.toOption.get ++ outputfailure.left.toOption.get).foreach { diagnostic =>
            diagnostic.expected should not be empty
            diagnostic.actual should not be empty
            diagnostic.correctiveAction should not be empty
          }
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
            directory.resolve("runtime.yaml").toString
          )

          When("the bridge creates provenance arguments")
          val identityargs = CozySbtBridge._generation_source_identity_args_for_test(
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
          cozy.Cozy._help_text should include("--generation-source-identity")
          cozy.Cozy._help_text should include(
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
          val delegated =
            projectroot.resolve("target/sbt-cozy/delegate-work/run-0")
          _generate(source, descriptor, delegated)
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

          val installed = _provenance_path(projectroot)
          val exists = Files.isRegularFile(installed)
          val admitted = GenerationProvenance.requireValidForPackaging(
            provenancePath = installed,
            projectRoot = projectroot,
            expectedCncfTargetVersion = Some("0.5.2-SNAPSHOT"),
            expectedCozyGeneratorVersion = Some(org.simplemodeling.cozy.BuildInfo.version)
          )

          Then("the project target owns valid metadata for the installed Scala path set")
          exists shouldBe true
          admitted shouldBe rebound
        }
      }

      "validate the exact delegated manifest selected for rebinding" in {
        _with_temp_dir("cozy-phase51-cv05-rebind-path") { directory =>
          Given("a valid canonical delegated manifest and a malformed alternate path")
          val projectroot = directory.resolve("project")
          val source = _copy_source(
            projectroot.resolve("src/main/cozy/address-literate.cml")
          )
          val descriptor = _write_descriptor(directory.resolve("runtime.yaml"))
          val delegated =
            projectroot.resolve("target/sbt-cozy/delegate-work/run-0")
          _generate(source, descriptor, delegated)
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
              evidence ++ Json.obj("source" -> Json.obj("identity" -> false))
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

          val published = Files.exists(_provenance_path(projectroot))

          Then("the selected manifest fails known-field validation without publishing final provenance")
          error.getMessage should include(
            "GENERATION_PROVENANCE_MALFORMED"
          )
          published shouldBe false
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
        "--simplemodeling-model-version",
        org.simplemodeling.cozy.BuildInfo.simpleModelingModelVersion,
        "--generation-source-identity",
        "src/main/cozy/address-literate.cml",
        "--save",
        output.toString
      )
    )

  private def _inputs(): GenerationProvenance.Inputs =
    GenerationProvenance.Inputs(
      cncfTargetVersion = "0.5.2-SNAPSHOT",
      cozyGeneratorVersion = org.simplemodeling.cozy.BuildInfo.version,
      simpleModelerBackendVersion = org.simplemodeling.cozy.BuildInfo.simpleModelerVersion,
      simpleModelingModelVersion = org.simplemodeling.cozy.BuildInfo.simpleModelingModelVersion,
      sourceIdentity = "src/main/cozy/address-literate.cml"
    )

  private def _with_extras(document: JsObject, name: String, value: JsValue): JsObject = {
    val extra = Json.obj(name -> value)
    val output = (document \ "output").as[JsObject]
    val artifacts = (output \ "artifacts").as[Vector[JsObject]].map(_ ++ extra)
    document ++ extra ++ Json.obj(
      "target" -> ((document \ "target").as[JsObject] ++ extra),
      "generator" -> ((document \ "generator").as[JsObject] ++ extra),
      "source" -> ((document \ "source").as[JsObject] ++ extra),
      "output" -> (output ++ extra ++ Json.obj("artifacts" -> artifacts))
    )
  }

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

  private def _write(path: Path, content: String): Path = {
    Option(path.getParent).foreach(Files.createDirectories(_))
    Files.writeString(path, content, StandardCharsets.UTF_8)
    path.toAbsolutePath.normalize()
  }

  private def _with_temp_dir[A](prefix: String)(body: Path => A): A = {
    val workroot = Paths.get("target/cozy-test/work/phase51-cv05-generation-provenance-spec").toAbsolutePath.normalize()
    Files.createDirectories(workroot)
    val directory = Files.createTempDirectory(workroot, s"$prefix-")
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
