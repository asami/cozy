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
 * @version Jul. 12, 2026
 * @author  ASAMI, Tomoharu
 */
class CozyCarLintSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  "Cozy CAR lint" should {
    "aggregate build CML and ABI findings" in {
      _with_temp_dir("cozy-car-lint-aggregate") { dir =>
        Given("a CAR project with build files, CML source, and an ABI manifest")
        _write_project(dir)
        _write_cml(dir)
        _write_manifest(dir.resolve("target/cozy/abi-manifest.json"), _manifest("1.4.0"))

        When("Cozy runs integrated CAR lint")
        val findings = CozyCarLint.lint(dir, None, noabi = false)

        Then("the result includes build, CML, and ABI categories")
        findings.map(_.category).toSet should contain ("build")
        findings.map(_.category).toSet should contain ("cml")
        findings.map(_.category).toSet should contain ("abi")
        findings.find(_.code == "cml.domain.string-attribute").map(_.level) shouldBe Some(CozyCarLint.Level.Fail)
        findings.find(_.code == "abi.baseline.missing").map(_.level) shouldBe Some(CozyCarLint.Level.Warn)
      }
    }

    "skip ABI findings when no-abi is requested" in {
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
      }
    }

    "warn when ABI manifest is absent from integrated CAR lint" in {
      _with_temp_dir("cozy-car-lint-missing-abi") { dir =>
        Given("a CAR project that has not produced a current ABI manifest yet")
        _write_project(dir)
        Files.createDirectories(dir.resolve("src/main/car"))

        When("Cozy runs integrated CAR lint")
        val findings = CozyCarLint.lint(dir, None, noabi = false)

        Then("the ABI category reports a warning instead of a hard failure")
        findings.find(_.code == "abi.manifest.missing").map(_.level) shouldBe Some(CozyCarLint.Level.Warn)
      }
    }

    "render category in JSON findings" in {
      _with_temp_dir("cozy-car-lint-json") { dir =>
        Given("a CAR project with an ABI manifest")
        _write_project(dir)
        _write_manifest(dir.resolve("target/cozy/abi-manifest.json"), _manifest("1.4.0"))

        When("Cozy renders integrated lint as JSON")
        val json = Json.parse(CozyCarLint.toJson(CozyCarLint.lint(dir, None, noabi = false)))

        Then("each finding carries its source category")
        val findings = (json \ "findings").as[Seq[play.api.libs.json.JsValue]]
        findings.exists(x => (x \ "category").as[String] == "build") shouldBe true
        findings.exists(x => (x \ "category").as[String] == "abi") shouldBe true
      }
    }

    "allow missing ABI baseline warnings in strict mode for the first ABI release" in {
      _with_temp_dir("cozy-car-lint-strict") { dir =>
        Given("a CAR project whose ABI manifest starts ABI operation without a baseline")
        _write_project(dir)
        _write_manifest(dir.resolve("target/cozy/abi-manifest.json"), _manifest("1.4.0"))

        When("Cozy runs integrated CAR lint in strict mode")
        val out = new ByteArrayOutputStream()
        val exitcode = Console.withOut(new PrintStream(out, true, StandardCharsets.UTF_8.name())) {
          CozyCarLint.execute(List(dir.toString, "--strict"), Some("0.1.11"))
        }

        Then("the baseline warning is visible but does not stop the initial ABI release")
        exitcode shouldBe 0
        out.toString(StandardCharsets.UTF_8.name()) should include ("abi.baseline.missing")
      }
    }

    "write only JSON to stdout through the CLI in JSON format" in {
      _with_temp_dir("cozy-car-lint-json-stdout") { dir =>
        Given("a CAR project with build metadata and no ABI requirement for this lint run")
        _write_project(dir)

        When("Cozy runs CAR lint through the CLI in JSON format")
        val captured = _capture_process_io {
          cozy.Cozy.main(Array("lint", "car", dir.toString, "--format", "json", "--no-abi"))
        }

        Then("stdout is a single JSON object without logback or human report text")
        val stdout = captured.stdout.trim
        stdout should startWith ("{")
        stdout should endWith ("}")
        stdout.linesIterator.size shouldBe 1
        Json.parse(stdout)
        stdout should not include "logback"
        stdout should not include "OK "
      }
    }

    "decide JSON output policy before runtime initialization" in {
      _with_temp_dir("cozy-car-lint-json-preflight") { dir =>
        Given("a JSON CAR lint command with command arguments")
        val args = Array("lint", "car", dir.toString, "--format", "json", "--no-abi")

        When("Cozy performs the preflight parse")
        val preflight = cozy.CozyCliPreflight.parse(args)

        Then("the output policy is machine JSON and lint arguments are preserved for the renderer")
        preflight.outputPolicy.stdoutMode shouldBe cozy.StdoutMode.MachineJson
        preflight.jsonLintCommand shouldBe Some("car" -> List(dir.toString, "--format", "json", "--no-abi"))
      }
    }

    "configure Cozy logging before Kaleidox runtime initialization" in {
      val configkey = "logback.configurationFile"
      val statuskey = "logback.statusListenerClass"
      val oldconfig = Option(System.getProperty(configkey))
      val oldstatus = Option(System.getProperty(statuskey))
      try {
        Given("a JSON CLI output policy before runtime is built")
        System.clearProperty(configkey)
        System.clearProperty(statuskey)
        val preflight = cozy.CozyCliPreflight.parse(Array("lint", "car", ".", "--format", "json"))

        When("Cozy configures logging from the preflight policy")
        cozy.CozyCliLogging.configure(preflight.outputPolicy)

        Then("Cozy owns the logback configuration used by embedded Kaleidox")
        System.getProperty(configkey) should include ("cozy-logback.xml")
        System.getProperty(statuskey) shouldBe "ch.qos.logback.core.status.NopStatusListener"
      } finally {
        _restore_system_property(configkey, oldconfig)
        _restore_system_property(statuskey, oldstatus)
      }
    }

    "accept equals-form JSON format through the CLI" in {
      _with_temp_dir("cozy-car-lint-json-equals") { dir =>
        Given("a CAR project with build metadata")
        _write_project(dir)

        When("Cozy runs CAR lint with --format=json")
        val captured = _capture_process_io {
          cozy.Cozy.main(Array("lint", "car", dir.toString, "--format=json", "--no-abi"))
        }

        Then("stdout remains directly parseable JSON")
        Json.parse(captured.stdout.trim)
      }
    }
  }

  private def _restore_system_property(name: String, value: Option[String]): Unit =
    value match {
      case Some(v) => System.setProperty(name, v)
      case None => System.clearProperty(name)
    }

  private def _write_project(dir: Path): Unit = {
    _write(dir.resolve("project.yaml"), "project:\n  name: sample\n")
    _write(dir.resolve("project/plugins.sbt"), """addSbtPlugin("org.goldenport" % "sbt-cozy" % "0.1.11")""")
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
      try stream.iterator().asScala.toVector.sortBy(_.getNameCount).reverse.foreach(Files.deleteIfExists)
      finally stream.close()
    }
}

private final case class CozyCarLintCapturedIo(stdout: String, stderr: String)
