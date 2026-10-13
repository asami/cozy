package cozy

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path, Paths}
import java.nio.file.attribute.PosixFilePermissions
import java.util.Comparator
import cozy.compatibility.CncfRuntimeDescriptorContract
import cozy.runtime.CozySbtBridge
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Jul. 28, 2026
 *  version Aug. 14, 2026
 * @version Oct.  9, 2026
 * @author  ASAMI, Tomoharu
 */

final class Phase51Cv04TargetValidationSpec
    extends AnyWordSpec
    with Matchers
    with GivenWhenThen {
  "Phase 51 CV-04 CNCF runtime descriptor validation" should {
    "validate the exact descriptor contract" which {
      "accepts an exact target, schemas, runtime identity, and selected path" in {
        _with_temp_dir("cozy-phase51-cv04-valid") { directory =>
          Given("a valid selected CNCF runtime descriptor")
          val descriptor = _write_descriptor(directory.resolve("runtime.yaml"))

          When("Cozy validates the complete generation invocation")
          val validated = CncfRuntimeDescriptorContract.requireValidInvocation(
            _invocation(descriptor),
            "spec"
          )

          Then("the exact target and descriptor identity are retained")
          validated.map(_.targetVersion) shouldBe Some("0.5.2-SNAPSHOT")
          validated.map(_.path) shouldBe Some(descriptor.toAbsolutePath.normalize())
        }
      }

      "returns deterministic structured diagnostics for missing contract arguments" in {
        Given("a descriptor option without its target")
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

      "rejects malformed and missing descriptors before output" in {
        _with_temp_dir("cozy-phase51-cv04-integrity") { directory =>
          Given("missing and malformed runtime descriptors")
          val missing = directory.resolve("missing.yaml")
          val malformed = _write(directory.resolve("malformed.yaml"), "schemaVersion: [\n")
          val output = directory.resolve("generated")

          When("the contracts are validated")
          val missingresult = CncfRuntimeDescriptorContract.validateDescriptor(
            missing,
            "0.5.2-SNAPSHOT",
            "spec"
          )
          val malformedresult = CncfRuntimeDescriptorContract.validateDescriptor(
            malformed,
            "0.5.2-SNAPSHOT",
            "spec"
          )
          val error = intercept[Exception] {
            CozyCliPreflight.parse(
              (_invocation(malformed) ++ Vector("--save", output.toString)).toArray
            )
          }

          Then("validation fails before any generated output exists")
          missingresult.left.toOption.get.map(_.code) should contain(
            CncfRuntimeDescriptorContract.DiagnosticCode.DescriptorMissing
          )
          malformedresult.left.toOption.get.map(_.code) should contain(
            CncfRuntimeDescriptorContract.DiagnosticCode.DescriptorMalformed
          )
          error.getMessage should include("CNCF_DESCRIPTOR_MALFORMED")
          Files.exists(output) shouldBe false
        }
      }

      "returns a typed diagnostic for unreadable descriptors" in {
        _with_temp_dir("cozy-phase51-cv04-unreadable") { directory =>
          Given("a descriptor without read permission")
          val unreadable = _write_descriptor(directory.resolve("unreadable.yaml"))
          val permissions = Files.getPosixFilePermissions(unreadable)
          Files.setPosixFilePermissions(
            unreadable,
            PosixFilePermissions.fromString("---------")
          )

          When("the production validator reads the selected descriptor")
          val unreadableresult =
            try CncfRuntimeDescriptorContract.validateDescriptor(
              unreadable,
              "0.5.2-SNAPSHOT",
              "spec"
            )
            finally Files.setPosixFilePermissions(unreadable, permissions)

          Then("the access failure has its typed diagnostic without a raw exception")
          unreadableresult.left.toOption.get.map(_.code) shouldBe Vector(
            CncfRuntimeDescriptorContract.DiagnosticCode.DescriptorUnreadable
          )
        }
      }

    }

    "preserve the owning-build bridge contract" which {
      "forwards the selected descriptor path and target" in {
        Given("sbt-cozy settings with the extracted descriptor identity")
        val settings = Map(
          "generation.versions.cncf" -> "0.5.2-SNAPSHOT",
          "runtime.cncf.descriptor" -> "/tmp/runtime.yaml"
        )

        When("the Cozy bridge constructs modeler arguments")
        val args = CozySbtBridge._modeler_args_for_settings_for_test(settings)

        Then("the target and path travel as one contract")
        args should contain allElementsOf List(
          "--cncf-version", "0.5.2-SNAPSHOT",
          "--cncf-runtime-descriptor", "/tmp/runtime.yaml"
        )
      }

      "rejects a malformed descriptor before delegated generation emits output" in {
        _with_temp_dir("cozy-phase51-cv04-bridge") { directory =>
          Given("CLI and bridge generation requests selecting the same malformed descriptor")
          val descriptor = _write(directory.resolve("runtime.yaml"), "schemaVersion: [\n")
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
               |    "runtime.cncf.descriptor": "$descriptor"
               |  }
               |}
               |""".stripMargin
          )

          When("both entry points validate their complete invocation")
          val clierror = intercept[Exception] {
            CozyCliPreflight.parse(_invocation(descriptor).toArray)
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
               |    "--cncf-runtime-descriptor", "$requestdescriptor"
               |  ],
               |  "settings": {
               |    "generation.versions.cncf": "0.5.1-SNAPSHOT",
               |    "runtime.cncf.descriptor": "$bridgedescriptor"
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

  private def _invocation(descriptor: Path): Vector[String] =
    Vector(
      "modeler-scala-value",
      "information.cml",
      "--cncf-version",
      "0.5.2-SNAPSHOT",
      "--cncf-runtime-descriptor",
      descriptor.toString,
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

  private def _normalize_diagnostic_source(message: String): String =
    message.
      replace("\"source\":\"cli\"", "\"source\":\"entry-point\"").
      replace("\"source\":\"sbt-bridge\"", "\"source\":\"entry-point\"")

  private def _with_temp_dir[A](prefix: String)(body: Path => A): A = {
    val workroot = Paths.get("target/phase51-cv04-target-validation-spec/work").toAbsolutePath.normalize()
    Files.createDirectories(workroot)
    val directory = Files.createTempDirectory(workroot, prefix)
    try body(directory)
    finally {
      val stream = Files.walk(directory)
      try stream.sorted(Comparator.reverseOrder()).forEach(path => Files.deleteIfExists(path))
      finally stream.close()
    }
  }
}
