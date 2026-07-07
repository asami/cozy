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
 * @version Jul.  7, 2026
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

    "treat warnings as failures in strict mode" in {
      _with_temp_dir("cozy-car-lint-strict") { dir =>
        Given("a CAR project whose ABI manifest has no baseline")
        _write_project(dir)
        _write_manifest(dir.resolve("target/cozy/abi-manifest.json"), _manifest("1.4.0"))

        When("Cozy runs integrated CAR lint in strict mode")
        val out = new ByteArrayOutputStream()
        val exitcode = Console.withOut(new PrintStream(out, true, StandardCharsets.UTF_8.name())) {
          CozyCarLint.execute(List(dir.toString, "--strict"))
        }

        Then("the baseline warning makes the command fail")
        exitcode shouldBe 1
        out.toString(StandardCharsets.UTF_8.name()) should include ("abi.baseline.missing")
      }
    }
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

  private def _delete(path: Path): Unit =
    if (Files.exists(path)) {
      val stream = Files.walk(path)
      try stream.iterator().asScala.toVector.sortBy(_.getNameCount).reverse.foreach(Files.deleteIfExists)
      finally stream.close()
    }
}
