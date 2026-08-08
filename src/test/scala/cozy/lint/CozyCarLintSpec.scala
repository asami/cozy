package cozy.lint

import java.io.{ByteArrayOutputStream, PrintStream}
import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path}
import scala.collection.JavaConverters._
import org.scalacheck.{Gen, Prop, Test}
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import play.api.libs.json.Json

/*
 * @since   Jul.  7, 2026
 *  version Jul. 28, 2026
 * @version Aug.  8, 2026
 * @author  ASAMI, Tomoharu
 */
class CozyCarLintSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  private val _cid07e = afterWord(
    "in spec:phase-56-component-identity-project-contract, examples:E-CID07E-1,E-CID07E-2,E-CID07E-3, rules:CID07-R1, phase:56, slice:CID-07E"
  )

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

      "reports the shared CAR metadata compatibility decision" in {
        _with_temp_dir("cozy-car-lint-compatibility") { dir =>
          Given("a declared CAR with one accepted contract and one excluded-target variant")
          _write_project(dir)
          _write_valid_cml(dir)
          _write_compatibility_project(dir, excluded = false)

          When("integrated CAR lint evaluates the project-owned contract")
          val accepted = CozyCarLint.lint(dir, None, noabi = true)
          _write_compatibility_project(dir, excluded = true)
          val rejected = CozyCarLint.lint(dir, None, noabi = true)

          Then("Review reports the same accepted facts and typed package diagnostic")
          accepted
            .find(_.code == "car.metadata.compatibility.accepted")
            .map(_.level) shouldBe Some(CozyCarLint.Level.Ok)
          val report = accepted
            .find(_.code == "car.metadata.compatibility.accepted")
            .map(_.message)
            .getOrElse("")
          report should include("cozyVersion=0.3.1")
          report should include(
            "cncfCompileCoordinate=org.goldenport::goldenport-cncf:0.5.17"
          )
          report should include("runtimeTested=0.5.17")
          rejected
            .find(_.code == "CAR_METADATA_CNCF_COMPILE_TARGET_EXCLUDED")
            .map(_.level) shouldBe Some(CozyCarLint.Level.Fail)
        }
      }

      "accepts generated exact CNCF versions through integrated lint" in {
        Given("generated compatible project-owned CNCF identities")
        val property = Prop.forAll(Gen.chooseNum(1, 19)) { patch =>
          _with_temp_dir("cozy-car-lint-compatibility-property") { dir =>
            val cncfversion = s"0.5.$patch"
            _write_project(dir)
            _write_valid_cml(dir)
            _write_compatibility_project(
              dir,
              excluded = false,
              cncfversion = cncfversion
            )

            When("integrated CAR lint evaluates each generated project")
            val findings = CozyCarLint.lint(dir, None, noabi = true)

            Then("the accepted report preserves the generated exact identity")
            findings
              .find(_.code == "car.metadata.compatibility.accepted")
              .exists(_.message.contains(s"runtimeTested=$cncfversion"))
          }
        }

        Test.check(
          Test.Parameters.default.withMinSuccessfulTests(50),
          property
        ).passed shouldBe true
      }

      "released identity compatibility metadata" which {
        "E-CID07E-1 Corpus legacy release" must _cid07e {
          "downgrades only the rejected generation pair after deferred identity classification" in {
            _with_temp_dir("cozy-car-lint-cid07e-corpus") { dir =>
              Given("an exact Corpus legacy release with complete runtime metadata and an older Cozy generator")
              _write_released_legacy_project(dir, "textus-corpus", "Corpus", "0.1.0", Some("0.3.0"))
              _write_valid_cml(dir)

              When("integrated CAR lint evaluates the released legacy project")
              val findings = CozyCarLint.lint(dir, None, noabi = true)

              Then("the deferred identity and generation-pair diagnostics are warnings")
              findings.find(_.code == "CAR_COMPONENT_IDENTITY_MIGRATION_DEFERRED").map(_.level) shouldBe Some(CozyCarLint.Level.Warn)
              findings.find(_.code == "CAR_METADATA_RELEASE_GENERATION_PAIR_REJECTED").map(_.level) shouldBe Some(CozyCarLint.Level.Warn)
              findings.exists(x => x.code == "CAR_METADATA_RELEASE_GENERATION_PAIR_REJECTED" && x.level == CozyCarLint.Level.Fail) shouldBe false
            }
          }
        }

        "E-CID07E-2 GeoResolver legacy release" must _cid07e {
          "downgrades only the missing Cozy generator metadata after deferred identity classification" in {
            _with_temp_dir("cozy-car-lint-cid07e-georesolver") { dir =>
              Given("an exact GeoResolver legacy release with complete metadata except its historical Cozy generator field")
              _write_released_legacy_project(dir, "textus-georesolver", "GeoResolver", "0.2.1", None)
              _write_valid_cml(dir)

              When("integrated CAR lint evaluates the released legacy project")
              val findings = CozyCarLint.lint(dir, None, noabi = true)

              Then("the deferred identity and missing Cozy version diagnostics are warnings")
              findings.find(_.code == "CAR_COMPONENT_IDENTITY_MIGRATION_DEFERRED").map(_.level) shouldBe Some(CozyCarLint.Level.Warn)
              findings.find(_.code == "CAR_METADATA_COZY_VERSION_MISSING").map(_.level) shouldBe Some(CozyCarLint.Level.Warn)
              findings.exists(x => x.code == "CAR_METADATA_COZY_VERSION_MISSING" && x.level == CozyCarLint.Level.Fail) shouldBe false
            }
          }
        }

        "E-CID07E-3 canonical UserAccount snapshot" must _cid07e {
          "keeps missing Cozy generator metadata as a failure outside deferred legacy identity" in {
            _with_temp_dir("cozy-car-lint-cid07e-user-account") { dir =>
              Given("a canonical UserAccount SNAPSHOT with complete runtime metadata but no Cozy generator field")
              _write_canonical_snapshot_project(dir)
              _write_valid_cml(dir)

              When("integrated CAR lint evaluates the canonical development project")
              val findings = CozyCarLint.lint(dir, None, noabi = true)

              Then("canonical identity remains accepted while missing Cozy metadata remains a failure")
              findings.find(_.code == "CAR_COMPONENT_IDENTITY_CANONICAL").map(_.level) shouldBe Some(CozyCarLint.Level.Ok)
              findings.find(_.code == "CAR_METADATA_COZY_VERSION_MISSING").map(_.level) shouldBe Some(CozyCarLint.Level.Fail)
            }
          }
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
        Given("a JSON CLI output policy before runtime is built")
        val configkey = "logback.configurationFile"
        val statuskey = "logback.statusListenerClass"
        val oldconfig = Option(System.getProperty(configkey))
        val oldstatus = Option(System.getProperty(statuskey))
        try {
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

  private def _write_compatibility_project(
    dir: Path,
    excluded: Boolean,
    cncfversion: String = "0.5.17"
  ): Unit = {
    val exclusions =
      if (excluded)
        s"        excluded:\n          - $cncfversion"
      else
        "        excluded: []"
    _write(
      dir.resolve("project.yaml"),
      s"""project:
         |  kind: car
         |  name: sample
         |packaging:
         |  kind: car
         |  car:
         |    runtime:
         |      cncf:
         |        minimum: $cncfversion
         |        maximum: 0.5.19
         |$exclusions
         |        tested:
         |          - $cncfversion
         |build:
         |  cozyVersion: 0.3.1
         |  dependencies:
         |    compile:
         |      - org.goldenport::goldenport-cncf:$cncfversion
         |""".stripMargin
    )
  }

  private def _write_released_legacy_project(
      dir: Path,
      artifact: String,
      componentname: String,
      version: String,
      cozyversion: Option[String]
  ): Unit = {
    _write_project(dir)
    val cozyline = cozyversion.map(value => s"  cozyVersion: $value\n").getOrElse("")
    _write(
      dir.resolve("project.yaml"),
      s"""project:
         |  kind: car
         |  name: $artifact
         |  component:
         |    name: $componentname
         |    version: $version
         |packaging:
         |  kind: car
         |  car:
         |    runtime:
         |      cncf:
         |        minimum: 0.5.17
         |        maximum: 0.5.19
         |        excluded: []
         |        tested:
         |          - 0.5.17
         |build:
         |$cozyline  dependencies:
         |    compile:
         |      - org.goldenport::goldenport-cncf:0.5.17
         |""".stripMargin
    )
  }

  private def _write_canonical_snapshot_project(dir: Path): Unit = {
    _write_project(dir)
    _write(
      dir.resolve("project.yaml"),
      """project:
        |  kind: car
        |  namespace: org.simplemodeling.textus
        |  id: UserAccount
        |  component:
        |    version: 0.6.0-SNAPSHOT
        |packaging:
        |  kind: car
        |  car:
        |    runtime:
        |      cncf:
        |        minimum: 0.5.17
        |        maximum: 0.5.19
        |        excluded: []
        |        tested:
        |          - 0.5.17
        |build:
        |  dependencies:
        |    compile:
        |      - org.goldenport::goldenport-cncf:0.5.17
        |""".stripMargin
    )
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
        || name  | type   | multiplicity |
        ||-------+--------+--------------|
        || email | string | 1            |
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
