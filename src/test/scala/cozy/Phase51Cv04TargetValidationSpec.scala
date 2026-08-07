package cozy

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path}
import java.nio.file.attribute.PosixFilePermissions
import java.security.MessageDigest
import java.util.Comparator
import cozy.compatibility.CncfRuntimeDescriptorContract
import cozy.runtime.CozySbtBridge
import org.scalacheck.{Gen, Prop, Test}
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

final class Phase51Cv04TargetValidationSpec
    extends AnyWordSpec
    with Matchers
    with GivenWhenThen {
  "Phase 51 CV-04 CNCF runtime descriptor validation" should {
    "validate the exact descriptor contract" which {
      "accepts an exact target, schemas, runtime identity, and digest" in {
        _with_temp_dir("cozy-phase51-cv04-valid") { directory =>
          Given("a valid CNCF runtime descriptor and its owning-build digest")
          val descriptor = _write_descriptor(directory.resolve("runtime.yaml"))
          val digest = _sha256(descriptor)

          When("Cozy validates the complete generation invocation")
          val validated = CncfRuntimeDescriptorContract.requireValidInvocation(
            _invocation(descriptor, digest),
            "spec"
          )

          Then("the exact target and descriptor identity are retained")
          validated.map(_.targetVersion) shouldBe Some("0.5.2-SNAPSHOT")
          validated.map(_.sha256) shouldBe Some(digest)
        }
      }

      "returns deterministic structured diagnostics for missing contract arguments" in {
        Given("a descriptor option without its target and digest")
        val args = Vector("modeler-scala-value", "information.cml", "--cncf-runtime-descriptor", "runtime.yaml")

        When("CLI preflight validates the selected descriptor contract")
        val error = intercept[Exception] {
          CozyCliPreflight.parse(args.toArray)
        }

        Then("the diagnostic names the source, expected value, actual value, and correction")
        error.getMessage should include("CNCF_DESCRIPTOR_ARGUMENT_MISSING")
        error.getMessage should include("\"source\":\"cli\"")
        error.getMessage should include("\"actual\":\"missing\"")
        error.getMessage should include("\"correctiveAction\"")
      }

      "rejects target, root schema, runtime, and predefined Result schema mismatches" in {
        _with_temp_dir("cozy-phase51-cv04-fields") { directory =>
          Given("descriptors whose authoritative identity fields differ from the selected contract")
          val cases = Vector(
            "version: 0.5.1-SNAPSHOT" -> CncfRuntimeDescriptorContract.DiagnosticCode.TargetMismatch,
            "schemaVersion: 2" -> CncfRuntimeDescriptorContract.DiagnosticCode.SchemaMismatch,
            "runtime: other" -> CncfRuntimeDescriptorContract.DiagnosticCode.RuntimeMismatch,
            "predefinedResults:\n  schemaVersion: other" ->
              CncfRuntimeDescriptorContract.DiagnosticCode.PredefinedResultSchemaMismatch
          )

          When("each mismatch is validated")
          val diagnostics = cases.zipWithIndex.map { case ((replacement, code), index) =>
            val descriptor = _write_descriptor(
              directory.resolve(s"runtime-$index.yaml"),
              Some(replacement)
            )
            val result = CncfRuntimeDescriptorContract.validateDescriptor(
              descriptor,
              "0.5.2-SNAPSHOT",
              Some(_sha256(descriptor)),
              "spec"
            )
            code -> result.left.toOption.get.map(_.code)
          }

          Then("each mismatch has its stable semantic code")
          diagnostics.foreach { case (code, actual) =>
            actual should contain(code)
          }
        }
      }

      "rejects malformed, missing, and digest-mismatched descriptors before output" in {
        _with_temp_dir("cozy-phase51-cv04-integrity") { directory =>
          Given("missing, malformed, and tampered runtime descriptors")
          val missing = directory.resolve("missing.yaml")
          val malformed = _write(directory.resolve("malformed.yaml"), "schemaVersion: [\n")
          val valid = _write_descriptor(directory.resolve("runtime.yaml"))
          val output = directory.resolve("generated")

          When("the contracts are validated")
          val missingresult = CncfRuntimeDescriptorContract.validateDescriptor(
            missing,
            "0.5.2-SNAPSHOT",
            Some("0" * 64),
            "spec"
          )
          val malformedresult = CncfRuntimeDescriptorContract.validateDescriptor(
            malformed,
            "0.5.2-SNAPSHOT",
            Some(_sha256(malformed)),
            "spec"
          )
          val error = intercept[Exception] {
            CozyCliPreflight.parse(
              (_invocation(valid, "0" * 64) ++ Vector("--save", output.toString)).toArray
            )
          }

          Then("validation fails before any generated output exists")
          missingresult.left.toOption.get.map(_.code) should contain(
            CncfRuntimeDescriptorContract.DiagnosticCode.DescriptorMissing
          )
          malformedresult.left.toOption.get.map(_.code) should contain(
            CncfRuntimeDescriptorContract.DiagnosticCode.DescriptorMalformed
          )
          error.getMessage should include("CNCF_DESCRIPTOR_DIGEST_MISMATCH")
          Files.exists(output) shouldBe false
        }
      }

      "distinguishes unreadable descriptors and invalid digest syntax" in {
        _with_temp_dir("cozy-phase51-cv04-unreadable") { directory =>
          Given("a descriptor without read permission and a descriptor paired with an uppercase digest")
          val unreadable = _write_descriptor(directory.resolve("unreadable.yaml"))
          val invaliddigest = _write_descriptor(directory.resolve("invalid-digest.yaml"))
          val permissions = Files.getPosixFilePermissions(unreadable)
          Files.setPosixFilePermissions(
            unreadable,
            PosixFilePermissions.fromString("---------")
          )

          When("the production validator evaluates both integrity failures")
          val unreadableresult =
            try CncfRuntimeDescriptorContract.validateDescriptor(
              unreadable,
              "0.5.2-SNAPSHOT",
              Some("0" * 64),
              "spec"
            )
            finally Files.setPosixFilePermissions(unreadable, permissions)
          val invaliddigestresult = CncfRuntimeDescriptorContract.validateDescriptor(
            invaliddigest,
            "0.5.2-SNAPSHOT",
            Some("A" * 64),
            "spec"
          )

          Then("each failure has its distinct typed diagnostic without a raw exception")
          unreadableresult.left.toOption.get.map(_.code) shouldBe Vector(
            CncfRuntimeDescriptorContract.DiagnosticCode.DescriptorUnreadable
          )
          invaliddigestresult.left.toOption.get.map(_.code) shouldBe Vector(
            CncfRuntimeDescriptorContract.DiagnosticCode.DigestInvalid
          )
        }
      }

      "rejects at least fifty deterministic digest mutations" in {
        _with_temp_dir("cozy-phase51-cv04-property") { directory =>
          Given("one descriptor and generated non-matching SHA-256 values")
          val descriptor = _write_descriptor(directory.resolve("runtime.yaml"))
          val actual = _sha256(descriptor)
          val mutation = Gen.chooseNum(0, 63)
          val property = Prop.forAll(mutation) { index =>
            val replacement = if (actual.charAt(index) == '0') '1' else '0'
            val digest = actual.updated(index, replacement)
            CncfRuntimeDescriptorContract.validateDescriptor(
              descriptor,
              "0.5.2-SNAPSHOT",
              Some(digest),
              "property"
            ).left.toOption.exists(
              _.map(_.code).contains(CncfRuntimeDescriptorContract.DiagnosticCode.DigestMismatch)
            )
          }

          When("the mutation property is evaluated")
          val result = Test.check(Test.Parameters.default.withMinSuccessfulTests(50), property)

          Then("every digest mutation is rejected")
          result.passed shouldBe true
        }
      }
    }

    "preserve the owning-build bridge contract" which {
      "forwards the exact descriptor digest with its path and target" in {
        Given("sbt-cozy settings with the extracted descriptor identity")
        val settings = Map(
          "generation.versions.cncf" -> "0.5.2-SNAPSHOT",
          "runtime.cncf.descriptor" -> "/tmp/runtime.yaml",
          "runtime.cncf.descriptor.sha256" -> ("a" * 64)
        )

        When("the Cozy bridge constructs modeler arguments")
        val args = CozySbtBridge._modeler_args_for_settings_for_test(settings)

        Then("the target, path, and digest travel as one contract")
        args should contain allElementsOf List(
          "--cncf-version", "0.5.2-SNAPSHOT",
          "--cncf-runtime-descriptor", "/tmp/runtime.yaml",
          "--cncf-runtime-descriptor-sha256", "a" * 64
        )
      }

      "rejects a tampered descriptor before delegated generation emits output" in {
        _with_temp_dir("cozy-phase51-cv04-bridge") { directory =>
          Given("CLI and bridge generation requests with the same non-matching owning-build digest")
          val descriptor = _write_descriptor(directory.resolve("runtime.yaml"))
          val digest = "0" * 64
          val output = directory.resolve("generated")
          val request = _write(
            directory.resolve("request.json"),
            s"""{
               |  "version": "v1",
               |  "action": "generate",
               |  "arguments": [
               |    "modeler-scala",
               |    "${directory.resolve("missing.cml")}",
               |    "--save",
               |    "$output"
               |  ],
               |  "settings": {
               |    "generation.versions.cncf": "0.5.2-SNAPSHOT",
               |    "runtime.cncf.descriptor": "$descriptor",
               |    "runtime.cncf.descriptor.sha256": "$digest"
               |  }
               |}
               |""".stripMargin
          )

          When("both entry points validate their complete invocation")
          val clierror = intercept[Exception] {
            CozyCliPreflight.parse(_invocation(descriptor, digest).toArray)
          }
          val bridgeerror = intercept[Exception] {
            CozySbtBridge.execute(List("v1", "--request", request.toString))
          }

          Then("only the source field differs between deterministic diagnostics")
          clierror.getMessage should include("\"source\":\"cli\"")
          bridgeerror.getMessage should include("\"source\":\"sbt-bridge\"")
          _normalize_diagnostic_source(clierror.getMessage) shouldBe
            _normalize_diagnostic_source(bridgeerror.getMessage)
          And("validation leaves the output boundary untouched")
          Files.exists(output) shouldBe false
        }
      }

      "rejects contradictory project, bridge, and request descriptor sources" in {
        _with_temp_dir("cozy-phase51-cv04-source-conflict") { directory =>
          Given("project and bridge targets disagree while request and bridge descriptors also disagree")
          _write(
            directory.resolve(".cozy/config.yaml"),
            """generation:
              |  versions:
              |    cncf: 0.5.1-SNAPSHOT
              |""".stripMargin
          )
          val requestdescriptor = _write_descriptor(directory.resolve("request-runtime.yaml"))
          val bridgedescriptor = _write_descriptor(
            directory.resolve("bridge-runtime.yaml"),
            Some("version: 0.5.1-SNAPSHOT")
          )
          val output = directory.resolve("generated")
          val request = _write(
            directory.resolve("request-conflict.json"),
            s"""{
               |  "version": "v1",
               |  "action": "generate",
               |  "arguments": [
               |    "modeler-scala",
               |    "${directory.resolve("missing.cml")}",
               |    "--save", "$output",
               |    "--cncf-version", "0.5.2-SNAPSHOT",
               |    "--cncf-runtime-descriptor", "$requestdescriptor",
               |    "--cncf-runtime-descriptor-sha256", "${_sha256(requestdescriptor)}"
               |  ],
               |  "settings": {
               |    "generation.versions.cncf": "0.5.1-SNAPSHOT",
               |    "runtime.cncf.descriptor": "$bridgedescriptor",
               |    "runtime.cncf.descriptor.sha256": "${_sha256(bridgedescriptor)}"
               |  }
               |}
               |""".stripMargin
          )

          When("the bridge resolves each contradictory source set")
          val projecterror = intercept[Exception] {
            CozySbtBridge._version_args_for_test(
              Map("generation.versions.cncf" -> "0.5.2-SNAPSHOT"),
              directory
            )
          }
          val requesterror = intercept[Exception] {
            CozySbtBridge.execute(List("v1", "--request", request.toString))
          }

          Then("both conflicts fail with the stable typed source-conflict diagnostic")
          projecterror.getMessage should include("CNCF_DESCRIPTOR_SOURCE_CONFLICT")
          requesterror.getMessage should include("CNCF_DESCRIPTOR_SOURCE_CONFLICT")
          And("no delegated generation output is emitted")
          Files.exists(output) shouldBe false
        }
      }
    }
  }

  private def _invocation(descriptor: Path, digest: String): Vector[String] =
    Vector(
      "modeler-scala-value",
      "information.cml",
      "--cncf-version",
      "0.5.2-SNAPSHOT",
      "--cncf-runtime-descriptor",
      descriptor.toString,
      "--cncf-runtime-descriptor-sha256",
      digest,
      "--generation-source-identity",
      "src/main/cozy/information.cml"
    )

  private def _write_descriptor(path: Path, replacement: Option[String] = None): Path = {
    val base =
      """schemaVersion: 1
        |runtime: cncf
        |version: 0.5.2-SNAPSHOT
        |predefinedResults:
        |  schemaVersion: cncf.predefined-result.v1
        |  resultNames: []
        |""".stripMargin
    val content = replacement.fold(base) {
      case value if value.startsWith("predefinedResults:") =>
        base.replace(
          "predefinedResults:\n  schemaVersion: cncf.predefined-result.v1",
          value
        )
      case value =>
        val key = value.takeWhile(_ != ':')
        base.linesIterator.map { line =>
          if (line.startsWith(s"$key:")) value else line
        }.mkString("", "\n", "\n")
    }
    _write(path, content)
  }

  private def _write(path: Path, content: String): Path = {
    Option(path.getParent).foreach(Files.createDirectories(_))
    Files.write(path, content.getBytes(StandardCharsets.UTF_8))
    path.toAbsolutePath.normalize()
  }

  private def _sha256(path: Path): String =
    MessageDigest.getInstance("SHA-256").
      digest(Files.readAllBytes(path)).
      map(byte => f"${byte & 0xff}%02x").
      mkString

  private def _normalize_diagnostic_source(message: String): String =
    message.
      replace("\"source\":\"cli\"", "\"source\":\"entry-point\"").
      replace("\"source\":\"sbt-bridge\"", "\"source\":\"entry-point\"")

  private def _with_temp_dir[A](prefix: String)(body: Path => A): A = {
    val directory = Files.createTempDirectory(prefix)
    try body(directory)
    finally {
      val stream = Files.walk(directory)
      try stream.sorted(Comparator.reverseOrder()).forEach(path => Files.deleteIfExists(path))
      finally stream.close()
    }
  }
}
