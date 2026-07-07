package cozy.lint

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path}
import java.util.zip.{ZipEntry, ZipOutputStream}
import scala.collection.JavaConverters._
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Jul.  7, 2026
 * @version Jul.  7, 2026
 * @author  ASAMI, Tomoharu
 */
class CozyCarAbiLintSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  "Cozy CAR ABI lint" should {
    "accept a patch upgrade when CAR ABI is unchanged" in {
      _with_temp_dir("cozy-car-abi-patch-ok") { dir =>
        Given("baseline and current manifests with the same public operation and entity schema")
        val baseline = _write_manifest(dir.resolve("baseline.json"), _manifest("1.4.0"))
        val current = _write_manifest(dir.resolve("current.json"), _manifest("1.4.1"))

        When("Cozy lints the CAR ABI")
        val findings = CozyCarAbiLint.lint(current, Some(baseline))

        Then("the patch compatibility check succeeds")
        findings.find(_.code == "abi.compatibility.patch").map(_.level) shouldBe Some(CozyCarAbiLint.Level.Ok)
        findings.exists(_.level == CozyCarAbiLint.Level.Fail) shouldBe false
      }
    }

    "reject a patch upgrade when CAR ABI changes" in {
      _with_temp_dir("cozy-car-abi-patch-change") { dir =>
        Given("a patch upgrade that adds a public operation")
        val baseline = _write_manifest(dir.resolve("baseline.json"), _manifest("1.4.0"))
        val current = _write_manifest(dir.resolve("current.json"), _manifest("1.4.1", extraoperation = true))

        When("Cozy lints the CAR ABI")
        val findings = CozyCarAbiLint.lint(current, Some(baseline))

        Then("the ABI addition is a patch-level failure")
        findings.find(_.code == "abi.patch.operation.added").map(_.level) shouldBe Some(CozyCarAbiLint.Level.Fail)
      }
    }

    "reject a patch upgrade when exported components change" in {
      _with_temp_dir("cozy-car-abi-component-change") { dir =>
        Given("a patch upgrade that adds an exported component")
        val baseline = _write_manifest(dir.resolve("baseline.json"), _manifest("1.4.0"))
        val current = _write_manifest(dir.resolve("current.json"), _manifest("1.4.1", extracomponent = true))

        When("Cozy lints the CAR ABI")
        val findings = CozyCarAbiLint.lint(current, Some(baseline))

        Then("the exported component addition is a patch-level failure")
        findings.find(_.code == "abi.patch.component.added").map(_.level) shouldBe Some(CozyCarAbiLint.Level.Fail)
      }
    }

    "allow backward-compatible additions in a minor upgrade" in {
      _with_temp_dir("cozy-car-abi-minor-additions") { dir =>
        Given("a minor upgrade that adds an operation and an optional entity field")
        val baseline = _write_manifest(dir.resolve("baseline.json"), _manifest("1.4.0"))
        val current = _write_manifest(dir.resolve("current.json"), _manifest("1.5.0", extraoperation = true, optionalfield = true))

        When("Cozy lints the CAR ABI")
        val findings = CozyCarAbiLint.lint(current, Some(baseline))

        Then("the additions are accepted")
        findings.find(_.code == "abi.operation.added").map(_.level) shouldBe Some(CozyCarAbiLint.Level.Ok)
        findings.find(_.code == "abi.entity.field.optional-added").map(_.level) shouldBe Some(CozyCarAbiLint.Level.Ok)
        findings.exists(_.level == CozyCarAbiLint.Level.Fail) shouldBe false
      }
    }

    "reject breaking changes in a minor upgrade" in {
      _with_temp_dir("cozy-car-abi-minor-breaking") { dir =>
        Given("a minor upgrade that changes an operation input type")
        val baseline = _write_manifest(dir.resolve("baseline.json"), _manifest("1.4.0"))
        val current = _write_manifest(dir.resolve("current.json"), _manifest("1.5.0", changedinput = true))

        When("Cozy lints the CAR ABI")
        val findings = CozyCarAbiLint.lint(current, Some(baseline))

        Then("the changed public operation signature is rejected")
        findings.find(_.code == "abi.operation.changed").map(_.level) shouldBe Some(CozyCarAbiLint.Level.Fail)
      }
    }

    "permit breaking changes in a major upgrade" in {
      _with_temp_dir("cozy-car-abi-major-breaking") { dir =>
        Given("a major upgrade that changes an operation input type")
        val baseline = _write_manifest(dir.resolve("baseline.json"), _manifest("1.4.0"))
        val current = _write_manifest(dir.resolve("current.json"), _manifest("2.0.0", changedinput = true))

        When("Cozy lints the CAR ABI")
        val findings = CozyCarAbiLint.lint(current, Some(baseline))

        Then("the finding is informational rather than failing")
        findings.find(_.code == "abi.operation.changed").map(_.level) shouldBe Some(CozyCarAbiLint.Level.Ok)
        findings.exists(_.level == CozyCarAbiLint.Level.Fail) shouldBe false
      }
    }

    "read a manifest from a CAR archive" in {
      _with_temp_dir("cozy-car-abi-archive") { dir =>
        Given("a CAR archive that contains abi-manifest.json")
        val archive = _write_car(dir.resolve("sample.car"), _manifest("1.4.0"))

        When("Cozy lints the CAR archive without a baseline")
        val findings = CozyCarAbiLint.lint(archive, None)

        Then("the manifest is readable and baseline absence is reported")
        findings.find(_.code == "abi.manifest").map(_.level) shouldBe Some(CozyCarAbiLint.Level.Ok)
        findings.find(_.code == "abi.baseline.missing").map(_.level) shouldBe Some(CozyCarAbiLint.Level.Warn)
      }
    }

    "participate in build lint for CAR projects" in {
      _with_temp_dir("cozy-car-abi-build-lint") { dir =>
        Given("a CAR project that has a generated ABI manifest")
        _write(dir.resolve("project.yaml"), "project:\n  name: sample\n  type: car\n")
        val target = dir.resolve("target/cozy")
        Files.createDirectories(target)
        _write_manifest(target.resolve("abi-manifest.json"), _manifest("1.4.0"))

        When("Cozy build lint runs")
        val findings = CozyBuildLint.lint(dir, Some("0.1.11"))

        Then("ABI manifest lint is included in build lint")
        findings.find(_.code == "abi.manifest").map(_.level) shouldBe Some(CozyBuildLint.Level.Ok)
      }
    }

    "skip build lint ABI warnings for non-CAR project metadata" in {
      _with_temp_dir("cozy-non-car-project-yaml") { dir =>
        Given("a non-CAR project that has project.yaml but no CAR source")
        _write(dir.resolve("project.yaml"), "project:\n  name: sample\n")

        When("Cozy build lint runs")
        val findings = CozyBuildLint.lint(dir, Some("0.1.11"))

        Then("ABI manifest warnings are not emitted")
        findings.exists(_.code == "abi.manifest.missing") shouldBe false
      }
    }

    "warn when a manifest only has skeletal generated surface" in {
      _with_temp_dir("cozy-car-abi-skeletal") { dir =>
        Given("a generated manifest that has entity names but no fields or operations")
        val current = _write_manifest(dir.resolve("current.json"), _skeletal_manifest("1.4.0"))

        When("Cozy lints the CAR ABI")
        val findings = CozyCarAbiLint.lint(current, None)

        Then("Cozy reports that explicit ABI metadata is needed for release enforcement")
        findings.find(_.code == "abi.surface.skeletal").map(_.level) shouldBe Some(CozyCarAbiLint.Level.Warn)
      }
    }
  }

  private def _manifest(
    version: String,
    extraoperation: Boolean = false,
    optionalfield: Boolean = false,
    changedinput: Boolean = false,
    extracomponent: Boolean = false
  ): String = {
    val input = if (changedinput) "RegisterUserV2" else "RegisterUser"
    val extracomponentjson =
      if (extracomponent)
        """,
          |        {
          |          "name": "audit-component"
          |        }""".stripMargin
      else
        ""
    val extraop =
      if (extraoperation)
        """,
          |          {
          |            "name": "user.load",
          |            "kind": "query",
          |            "input": "LoadUser",
          |            "output": "User",
          |            "execution": "sync"
          |          }""".stripMargin
      else
        ""
    val optional =
      if (optionalfield)
        """,
          |          {
          |            "name": "displayName",
          |            "type": "String",
          |            "required": false
          |          }""".stripMargin
      else
        ""
    s"""{
       |  "format": "cozy.car.abi-manifest.v1",
       |  "car": {
       |    "name": "textus-user-account",
       |    "version": "$version"
       |  },
       |  "abi": {
       |    "version": 1,
       |    "exports": {
       |      "components": [
       |        {
       |          "name": "user-account"
       |        }$extracomponentjson
       |      ],
       |      "operations": [
       |        {
       |          "name": "user.register",
       |          "kind": "command",
       |          "input": "$input",
       |          "output": "OperationResult",
       |          "execution": "async"
       |        }$extraop
       |      ],
       |      "entities": [
       |        {
       |          "name": "User",
       |          "fields": [
       |            {
       |              "name": "id",
       |              "type": "EntityId",
       |              "required": true
       |            },
       |            {
       |              "name": "email",
       |              "type": "EmailAddress",
       |              "required": true
       |            }$optional
       |          ]
       |        }
       |      ]
       |    },
       |    "dependencies": []
       |  }
       |}
       |""".stripMargin
  }

  private def _skeletal_manifest(version: String): String =
    s"""{
       |  "format": "cozy.car.abi-manifest.v1",
       |  "car": {
       |    "name": "textus-user-account",
       |    "version": "$version"
       |  },
       |  "abi": {
       |    "version": 1,
       |    "exports": {
       |      "components": [
       |        {
       |          "name": "user-account"
       |        }
       |      ],
       |      "operations": [],
       |      "entities": [
       |        {
       |          "name": "User",
       |          "fields": []
       |        }
       |      ]
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

  private def _write_car(path: Path, manifest: String): Path = {
    Option(path.getParent).foreach(Files.createDirectories(_))
    val out = new ZipOutputStream(Files.newOutputStream(path))
    try {
      out.putNextEntry(new ZipEntry("abi-manifest.json"))
      out.write(manifest.getBytes(StandardCharsets.UTF_8))
      out.closeEntry()
    } finally {
      out.close()
    }
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
