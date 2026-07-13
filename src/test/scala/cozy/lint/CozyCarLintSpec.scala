package cozy.lint

import java.io.{ByteArrayOutputStream, PrintStream}
import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path}
import scala.collection.JavaConverters._
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import play.api.libs.json.Json

/*
 * @since   Jul.  7, 2026
 * @version Jul. 14, 2026
 * @author  ASAMI, Tomoharu
 */
class CozyCarLintSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  "Cozy CAR lint" should {
    "aggregate project findings" which {
      "includes build CML documentation and ABI findings" in {
        _with_temp_dir("cozy-car-lint-aggregate") { dir =>
          Given(
            "a CAR project with build files, documentation, CML source, and an ABI manifest"
          )
          _write_project(dir)
          _write_cml(dir)
          _write_manifest(
            dir.resolve("target/cozy/abi-manifest.json"),
            _manifest("1.4.0")
          )

          When("Cozy runs integrated CAR lint")
          val findings = CozyCarLint.lint(dir, None, noabi = false)

          Then("the result includes build, CML, documentation, and ABI categories")
          findings.map(_.category).toSet should contain("build")
          findings.map(_.category).toSet should contain("cml")
          findings.map(_.category).toSet should contain("abi")
          findings.map(_.category).toSet should contain("documentation")
          findings
            .find(_.code == "cml.domain.string-attribute")
            .map(_.level) shouldBe Some(CozyCarLint.Level.Fail)
          findings
            .find(_.code == "abi.baseline.missing")
            .map(_.level) shouldBe Some(CozyCarLint.Level.Warn)
          findings.exists(_.code.startsWith("car.cml.source.")) shouldBe false
          findings
            .find(_.code == "car.documentation.component-help.missing")
            .map(_.level) shouldBe Some(CozyCarLint.Level.Warn)
        }
      }

      "accepts complete CAR documentation and descriptive generated-help metadata" in {
        _with_temp_dir("cozy-car-lint-documentation-complete") { dir =>
          Given("a CAR project with a packaged reference manual, user guide, and descriptive CML metadata")
          _write_project(dir)
          _write_valid_cml(dir)

          When("Cozy runs integrated CAR lint")
          val findings = CozyCarLint.lint(dir, None, noabi = true)

          Then("the documentation category confirms all deterministic documentation contracts")
          val documentation = findings.filter(_.category == "documentation")
          documentation should not be empty
          documentation.exists(_.level == CozyCarLint.Level.Warn) shouldBe false
          documentation.map(_.code) should contain allOf (
            "car.documentation.reference-manual.present",
            "car.documentation.user-guide.present",
            "car.documentation.component.description.present",
            "car.documentation.service.description.present",
            "car.documentation.operation.description.present"
          )
        }
      }

      "warns when manuals and generated-help descriptions are absent" in {
        _with_temp_dir("cozy-car-lint-documentation-missing") { dir =>
          Given("a CAR project without manuals and with an undescribed component")
          _write_project_without_documentation(dir)
          _write_valid_cml_without_documentation(dir)

          When("Cozy runs integrated CAR lint")
          val findings = CozyCarLint.lint(dir, None, noabi = true)

          Then("the documentation category identifies each missing publication and description")
          val warnings = findings.filter(x => x.category == "documentation" && x.level == CozyCarLint.Level.Warn)
          warnings.map(_.code) should contain allOf (
            "car.documentation.reference-manual.missing",
            "car.documentation.user-guide.missing",
            "car.documentation.component.description.missing"
          )
        }
      }

      "warns when service and operation descriptions are too thin" in {
        _with_temp_dir("cozy-car-lint-documentation-thin") { dir =>
          Given("a CAR project whose component is described but whose service and operation help is terse")
          _write_project(dir)
          _write(
            dir.resolve("src/main/cozy/sample.cml"),
            _component_help +
              """
                |# SERVICE
                |
                |## Catalog
                |
                |### SUMMARY
                |
                |Catalog.
                |
                |### OPERATION
                |
                |#### findItem
                |
                |##### SUMMARY
                |
                |Find item.
                |""".stripMargin
          )

          When("Cozy runs integrated CAR lint")
          val findings = CozyCarLint.lint(dir, None, noabi = true)

          Then("service and operation help is reported independently from component help")
          val warnings = findings.filter(x => x.category == "documentation" && x.level == CozyCarLint.Level.Warn)
          warnings.map(_.code) should contain allOf (
            "car.documentation.service.description.thin",
            "car.documentation.operation.description.thin"
          )
          warnings.map(_.code) should not contain "car.documentation.component.description.thin"
        }
      }

      "reports an ambiguous CAR publication CML source" in {
        _with_temp_dir("cozy-car-lint-ambiguous-cml") { dir =>
          Given("a CAR project with two noncanonical CML sources")
          _write_project(dir)
          _write(dir.resolve("src/main/cozy/a.cml"), "# COMPONENT\n\n## A\n")
          _write(dir.resolve("src/main/cozy/b.cml"), "# COMPONENT\n\n## B\n")

          When("Cozy runs integrated CAR lint")
          val findings = CozyCarLint.lint(dir, None, noabi = true)

          Then(
            "lint uses the publication resolver and requires an explicit source"
          )
          findings
            .find(_.code == "car.cml.source.ambiguous")
            .map(_.level) shouldBe Some(CozyCarLint.Level.Fail)
        }
      }

      "reports a missing explicit CML source without falling back" in {
        _with_temp_dir("cozy-car-lint-missing-explicit-cml") { dir =>
          Given("a CAR project whose explicit CML source is missing")
          _write_project(dir)
          _write(
            dir.resolve("project.yaml"),
            "project:\n  name: sample\ncml:\n  source: src/main/cozy/missing.cml\n"
          )
          _write_valid_cml(dir)

          When("Cozy runs integrated CAR lint")
          val findings = CozyCarLint.lint(dir, None, noabi = true)

          Then(
            "lint reports the configured source instead of selecting the canonical fallback"
          )
          findings
            .find(_.code == "car.cml.source.not_found")
            .map(_.level) shouldBe Some(CozyCarLint.Level.Fail)
        }
      }

      "skips ABI findings when no-abi is requested" in {
        _with_temp_dir("cozy-car-lint-no-abi") { dir =>
          Given("a CAR project without a generated ABI manifest")
          _write_project(dir)
          _write_cml(dir)

          When("Cozy runs integrated CAR lint with no-abi")
          val findings = CozyCarLint.lint(dir, None, noabi = true)

          Then("ABI findings are absent while other categories still run")
          findings.exists(_.category == "abi") shouldBe false
          findings.exists(_.category == "build") shouldBe true
          findings.exists(_.category == "cml") shouldBe true
          findings.exists(_.category == "documentation") shouldBe true
        }
      }

      "warns when ABI manifest is absent from integrated CAR lint" in {
        _with_temp_dir("cozy-car-lint-missing-abi") { dir =>
          Given(
            "a CAR project that has not produced a current ABI manifest yet"
          )
          _write_project(dir)
          _write_valid_cml(dir)
          Files.createDirectories(dir.resolve("src/main/car"))

          When("Cozy runs integrated CAR lint")
          val findings = CozyCarLint.lint(dir, None, noabi = false)

          Then("the ABI category reports a warning instead of a hard failure")
          findings
            .find(_.code == "abi.manifest.missing")
            .map(_.level) shouldBe Some(CozyCarLint.Level.Warn)
        }
      }

      "renders category in JSON findings" in {
        _with_temp_dir("cozy-car-lint-json") { dir =>
          Given("a CAR project with an ABI manifest")
          _write_project(dir)
          _write_valid_cml(dir)
          _write_manifest(
            dir.resolve("target/cozy/abi-manifest.json"),
            _manifest("1.4.0")
          )

          When("Cozy renders integrated lint as JSON")
          val json = Json.parse(
            CozyCarLint.toJson(CozyCarLint.lint(dir, None, noabi = false))
          )

          Then("each finding carries its source category")
          val findings = (json \ "findings").as[Seq[play.api.libs.json.JsValue]]
          findings.exists(x =>
            (x \ "category").as[String] == "build"
          ) shouldBe true
          findings.exists(x =>
            (x \ "category").as[String] == "abi"
          ) shouldBe true
          findings.exists(x =>
            (x \ "category").as[String] == "documentation"
          ) shouldBe true
        }
      }
    }

    "provide the command-line contract" which {
      "fails strict lint when CAR documentation is incomplete" in {
        _with_temp_dir("cozy-car-lint-documentation-strict") { dir =>
          Given("a CAR project with missing manuals and incomplete generated-help descriptions")
          _write_project_without_documentation(dir)
          _write_valid_cml_without_documentation(dir)

          When("Cozy runs integrated CAR lint in strict mode without ABI checks")
          val out = new ByteArrayOutputStream()
          val exitcode = Console.withOut(
            new PrintStream(out, true, StandardCharsets.UTF_8.name())
          ) {
            CozyCarLint.execute(
              List(dir.toString, "--strict", "--no-abi"),
              Some("0.1.11")
            )
          }

          Then("documentation warnings block release readiness")
          exitcode shouldBe 1
          out.toString(StandardCharsets.UTF_8.name()) should include(
            "car.documentation.reference-manual.missing"
          )
        }
      }

      "allows missing ABI baseline warnings in strict mode for the first ABI release" in {
        _with_temp_dir("cozy-car-lint-strict") { dir =>
          Given(
            "a CAR project whose ABI manifest starts ABI operation without a baseline"
          )
          _write_project(dir)
          _write_valid_cml(dir)
          _write_manifest(
            dir.resolve("target/cozy/abi-manifest.json"),
            _manifest("1.4.0")
          )

          When("Cozy runs integrated CAR lint in strict mode")
          val out = new ByteArrayOutputStream()
          val exitcode = Console.withOut(
            new PrintStream(out, true, StandardCharsets.UTF_8.name())
          ) {
            CozyCarLint.execute(List(dir.toString, "--strict"), Some("0.1.11"))
          }

          Then(
            "the baseline warning is visible but does not stop the initial ABI release"
          )
          exitcode shouldBe 0
          out.toString(StandardCharsets.UTF_8.name()) should include(
            "abi.baseline.missing"
          )
        }
      }

      "writes only JSON to stdout through the CLI in JSON format" in {
        _with_temp_dir("cozy-car-lint-json-stdout") { dir =>
          Given(
            "a CAR project with build metadata and no ABI requirement for this lint run"
          )
          _write_project(dir)
          _write_valid_cml(dir)

          When("Cozy runs CAR lint through the CLI in JSON format")
          val captured = _capture_process_io {
            cozy.Cozy.main(
              Array("lint", "car", dir.toString, "--format", "json", "--no-abi")
            )
          }

          Then(
            "stdout is a single JSON object without logback or human report text"
          )
          val stdout = captured.stdout.trim
          stdout should startWith("{")
          stdout should endWith("}")
          stdout.linesIterator.size shouldBe 1
          Json.parse(stdout)
          stdout should not include "logback"
          stdout should not include "OK "
        }
      }

      "decides JSON output policy before runtime initialization" in {
        _with_temp_dir("cozy-car-lint-json-preflight") { dir =>
          Given("a JSON CAR lint command with command arguments")
          val args =
            Array("lint", "car", dir.toString, "--format", "json", "--no-abi")

          When("Cozy performs the preflight parse")
          val preflight = cozy.CozyCliPreflight.parse(args)

          Then(
            "the output policy is machine JSON and lint arguments are preserved for the renderer"
          )
          preflight.outputPolicy.stdoutMode shouldBe cozy.StdoutMode.MachineJson
          preflight.jsonLintCommand shouldBe Some(
            "car" -> List(dir.toString, "--format", "json", "--no-abi")
          )
        }
      }

      "configures Cozy logging before Kaleidox runtime initialization" in {
        val configkey = "logback.configurationFile"
        val statuskey = "logback.statusListenerClass"
        val oldconfig = Option(System.getProperty(configkey))
        val oldstatus = Option(System.getProperty(statuskey))
        try {
          Given("a JSON CLI output policy before runtime is built")
          System.clearProperty(configkey)
          System.clearProperty(statuskey)
          val preflight = cozy.CozyCliPreflight.parse(
            Array("lint", "car", ".", "--format", "json")
          )

          When("Cozy configures logging from the preflight policy")
          cozy.CozyCliLogging.configure(preflight.outputPolicy)

          Then("Cozy owns the logback configuration used by embedded Kaleidox")
          System.getProperty(configkey) should include("cozy-logback.xml")
          System.getProperty(
            statuskey
          ) shouldBe "ch.qos.logback.core.status.NopStatusListener"
        } finally {
          _restore_system_property(configkey, oldconfig)
          _restore_system_property(statuskey, oldstatus)
        }
      }

      "accepts equals-form JSON format through the CLI" in {
        _with_temp_dir("cozy-car-lint-json-equals") { dir =>
          Given("a CAR project with build metadata")
          _write_project(dir)
          _write_valid_cml(dir)

          When("Cozy runs CAR lint with --format=json")
          val captured = _capture_process_io {
            cozy.Cozy.main(
              Array("lint", "car", dir.toString, "--format=json", "--no-abi")
            )
          }

          Then("stdout remains directly parseable JSON")
          Json.parse(captured.stdout.trim)
        }
      }
    }
  }

  private def _restore_system_property(
      name: String,
      value: Option[String]
  ): Unit =
    value match {
      case Some(v) => System.setProperty(name, v)
      case None    => System.clearProperty(name)
    }

  private def _write_project(dir: Path): Unit = {
    _write_project_without_documentation(dir)
    _write(
      dir.resolve("src/main/car/manual/index.md"),
      "# Sample Reference Manual\n\nReference semantics, configuration, operations, errors, and examples.\n"
    )
    _write(
      dir.resolve("src/main/web/docs/user-guide.md"),
      "# Sample User Guide\n\nTask-oriented setup, first invocation, daily workflows, and troubleshooting.\n"
    )
  }

  private def _write_project_without_documentation(dir: Path): Unit = {
    _write(dir.resolve("project.yaml"), "project:\n  name: sample\n")
    _write(
      dir.resolve("project/plugins.sbt"),
      """addSbtPlugin("org.goldenport" % "sbt-cozy" % "0.1.11")"""
    )
    _write(dir.resolve("build.sbt"), "scalaVersion := \"2.12.20\"")
  }

  private def _write_cml(dir: Path): Unit =
    _write(
      dir.resolve("src/main/cozy/model.cml"),
      """# ENTITY
        |
        |## User
        |
        |### Attribute
        |
        || name | type |
        || email | String |
        |""".stripMargin
    )

  private def _write_valid_cml(dir: Path): Unit =
    _write(
      dir.resolve("src/main/cozy/sample.cml"),
      _component_help +
        """
          |# SERVICE
          |
          |## Catalog
          |
          |### DESCRIPTION
          |
          |Provides catalog lookup behavior for users who need to find a registered item.
          |
          |### OPERATION
          |
          |#### findItem
          |
          |##### DESCRIPTION
          |
          |Finds one registered item by its stable identifier and explains a missing result.
          |""".stripMargin
    )

  private def _write_valid_cml_without_documentation(dir: Path): Unit =
    _write(
      dir.resolve("src/main/cozy/sample.cml"),
      "# COMPONENT\n\n## Sample\n"
    )

  private def _component_help: String =
    """# COMPONENT
      |
      |## SampleCatalog
      |
      |### DESCRIPTION
      |
      |Sample coordinates the complete catalog workflow and explains how users find registered items.
      |""".stripMargin

  private def _manifest(version: String): String =
    s"""{
       |  "car": {
       |    "name": "sample",
       |    "version": "$version"
       |  },
       |  "abi": {
       |    "version": 1,
       |    "exports": {
       |      "operations": [],
       |      "entities": []
       |    },
       |    "dependencies": []
       |  }
       |}
       |""".stripMargin

  private def _write_manifest(path: Path, text: String): Path =
    _write(path, text)

  private def _write(path: Path, text: String): Path = {
    Option(path.getParent).foreach(Files.createDirectories(_))
    Files.write(path, text.getBytes(StandardCharsets.UTF_8))
    path
  }

  private def _with_temp_dir[A](prefix: String)(body: Path => A): A = {
    val dir = Files.createTempDirectory(prefix)
    try body(dir)
    finally _delete(dir)
  }

  private def _capture_process_io(body: => Unit): CozyCarLintCapturedIo = {
    val stdout = new ByteArrayOutputStream()
    val stderr = new ByteArrayOutputStream()
    val oldout = System.out
    val olderr = System.err
    val outps = new PrintStream(stdout, true, StandardCharsets.UTF_8.name())
    val errps = new PrintStream(stderr, true, StandardCharsets.UTF_8.name())
    try {
      System.setOut(outps)
      System.setErr(errps)
      Console.withOut(outps) {
        Console.withErr(errps) {
          body
        }
      }
      CozyCarLintCapturedIo(
        stdout.toString(StandardCharsets.UTF_8.name()),
        stderr.toString(StandardCharsets.UTF_8.name())
      )
    } finally {
      System.setOut(oldout)
      System.setErr(olderr)
      outps.close()
      errps.close()
    }
  }

  private def _delete(path: Path): Unit =
    if (Files.exists(path)) {
      val stream = Files.walk(path)
      try
        stream
          .iterator()
          .asScala
          .toVector
          .sortBy(_.getNameCount)
          .reverse
          .foreach(Files.deleteIfExists)
      finally stream.close()
    }
}

private final case class CozyCarLintCapturedIo(stdout: String, stderr: String)
