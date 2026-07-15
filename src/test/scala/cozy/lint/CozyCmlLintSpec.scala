package cozy.lint

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path}
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Jul.  6, 2026
 * @version Jul. 15, 2026
 * @author  ASAMI, Tomoharu
 */
class CozyCmlLintSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  "Cozy CML lint" should {
    "fail entity raw string attributes" in {
      _with_temp_dir("cozy-cml-lint-entity-string") { dir =>
        Given("an entity CML with a raw string attribute")
        val path = _write(
          dir.resolve("model.cml"),
          """# ENTITY
            |
            |## Exhibition
            |
            |### Attribute
            |
            || name | type   | multiplicity |
            ||------+--------+--------------|
            || id   | entityid | 1          |
            || name | string | 1            |
            |""".stripMargin
        )

        When("Cozy lints the normalized CML AST model")
        val findings = CozyCmlLint.lint(path)

        Then("the entity string attribute is a FAIL")
        findings.map(_.code) should contain("cml.domain.string-attribute")
        findings.find(_.code == "cml.domain.string-attribute").map(_.level) shouldBe Some(CozyCmlLint.Level.Fail)
        findings.find(_.code == "cml.domain.string-attribute").map(_.line) shouldBe Some(10)
      }
    }

    "allow a single value string representation" in {
      _with_temp_dir("cozy-cml-lint-value-string") { dir =>
        Given("a value object with a single value string attribute")
        val path = _write(
          dir.resolve("model.cml"),
          """# VALUE
            |
            |## FacilityName
            |
            |### Attribute
            |
            || name  | type   | multiplicity |
            ||-------+--------+--------------|
            || value | string | 1            |
            |""".stripMargin
        )

        When("Cozy lints the CML")
        val findings = CozyCmlLint.lint(path)

        Then("the internal representation is accepted")
        findings shouldBe empty
      }
    }

    "warn value raw string attributes that are not a single value representation" in {
      _with_temp_dir("cozy-cml-lint-value-label-string") { dir =>
        Given("a value object with a raw label string attribute")
        val path = _write(
          dir.resolve("model.cml"),
          """# VALUE
            |
            |## FacilityLabel
            |
            |### Attribute
            |
            || name  | type   | multiplicity |
            ||-------+--------+--------------|
            || label | string | 1            |
            |""".stripMargin
        )

        When("Cozy lints the CML")
        val findings = CozyCmlLint.lint(path)

        Then("the raw value string attribute is a WARN")
        findings.map(_.code) should contain("cml.value.string-attribute")
        findings.find(_.code == "cml.value.string-attribute").map(_.level) shouldBe Some(CozyCmlLint.Level.Warn)
      }
    }

    "accept dedicated value types from entities" in {
      _with_temp_dir("cozy-cml-lint-dedicated-type") { dir =>
        Given("an entity CML using a dedicated value type")
        val path = _write(
          dir.resolve("model.cml"),
          """# ENTITY
            |
            |## Facility
            |
            |### Attribute
            |
            || name | type         | multiplicity |
            ||------+--------------+--------------|
            || id   | entityid     | 1            |
            || name | FacilityName | 1            |
            |""".stripMargin
        )

        When("Cozy lints the CML")
        val findings = CozyCmlLint.lint(path)

        Then("the dedicated type is accepted")
        findings shouldBe empty
      }
    }

    "reject string-only Datatypes that duplicate predefined scalars" in {
      _with_temp_dir("cozy-cml-lint-predefined-wrapper") { dir =>
        Given("a title Datatype that only wraps a string")
        val path = _write(
          dir.resolve("model.cml"),
          """# DATATYPE
            |
            |## UserAccountTitle
            |
            |### Attribute
            |
            || name  | type   | multiplicity |
            ||-------+--------+--------------|
            || value | string | 1            |
            |""".stripMargin
        )

        When("Cozy lints the normalized Datatype")
        val findings = CozyCmlLint.lint(path)

        Then("the nominal wrapper fails with the predefined replacement")
        findings.map(_.code) should contain("cml.datatype.predefined-scalar-wrapper")
        findings.find(_.code == "cml.datatype.predefined-scalar-wrapper").map(_.message) should contain(
          "UserAccountTitle only wraps string and duplicates predefined 'title'; use the predefined type directly."
        )
        And("the finding points to the Datatype declaration")
        findings.find(_.code == "cml.datatype.predefined-scalar-wrapper").map(_.line) shouldBe Some(3)
      }
    }

    "warn when a string-only Datatype has no distinct declared contract" in {
      _with_temp_dir("cozy-cml-lint-nominal-wrapper") { dir =>
        Given("an unclassified Datatype that only wraps a string")
        val path = _write(
          dir.resolve("model.cml"),
          """# DATATYPE
            |
            |## ProviderPayload
            |
            |### Attribute
            |
            || name  | type   | multiplicity |
            ||-------+--------+--------------|
            || value | string | 1            |
            |""".stripMargin
        )

        When("Cozy lints the normalized Datatype")
        val findings = CozyCmlLint.lint(path)

        Then("the missing scalar contract remains visible as debt")
        findings.map(_.code) should contain("cml.datatype.nominal-string-wrapper")
        findings.find(_.code == "cml.datatype.nominal-string-wrapper").map(_.level) shouldBe Some(CozyCmlLint.Level.Warn)
      }
    }

    "accept a constrained string-backed domain scalar" in {
      _with_temp_dir("cozy-cml-lint-constrained-domain-scalar") { dir =>
        Given("a nominal Datatype with explicit range and syntax constraints")
        val path = _write(
          dir.resolve("model.cml"),
          """# DATATYPE
            |
            |## MessageCode
            |
            |### Attribute
            |
            || name  | type   | multiplicity | min-length | max-length | pattern     |
            ||-------+--------+--------------+------------+------------+-------------|
            || value | string | 1            | 1          | 64         | [A-Z0-9_-]+ |
            |""".stripMargin
        )

        When("Cozy lints the normalized Datatype")
        val findings = CozyCmlLint.lint(path)

        Then("the explicit domain contract prevents an unclassified-wrapper warning")
        findings.map(_.code) should not contain "cml.datatype.nominal-string-wrapper"
      }
    }

    "accept a constrained nominal scalar even when its name resembles a predefined type" in {
      _with_temp_dir("cozy-cml-lint-constrained-predefined-name") { dir =>
        Given("a title-like Datatype with a narrower explicit range")
        val path = _write(
          dir.resolve("model.cml"),
          """# DATATYPE
            |
            |## UserAccountTitle
            |
            |### Attribute
            |
            || name  | type   | multiplicity | max-length |
            ||-------+--------+--------------+------------|
            || value | string | 1            | 80         |
            |""".stripMargin
        )

        When("Cozy lints the normalized Datatype")
        val findings = CozyCmlLint.lint(path)

        Then("the narrower contract is not rejected as a redundant alias")
        findings.map(_.code) should not contain "cml.datatype.predefined-scalar-wrapper"
        findings.map(_.code) should not contain "cml.datatype.nominal-string-wrapper"
      }
    }

    "not replace a narrower token hash with the generic token type" in {
      _with_temp_dir("cozy-cml-lint-token-hash") { dir =>
        Given("a token hash Datatype whose algorithm contract is not declared yet")
        val path = _write(
          dir.resolve("model.cml"),
          """# DATATYPE
            |
            |## UserAccountTokenHash
            |
            |### Attribute
            |
            || name  | type   | multiplicity |
            ||-------+--------+--------------|
            || value | string | 1            |
            |""".stripMargin
        )

        When("Cozy lints the narrower scalar")
        val findings = CozyCmlLint.lint(path)

        Then("the missing contract is warned without a lossy replacement")
        findings.map(_.code) should contain("cml.datatype.nominal-string-wrapper")
        findings.map(_.code) should not contain "cml.datatype.predefined-scalar-wrapper"
      }
    }

    "render machine readable JSON findings" in {
      _with_temp_dir("cozy-cml-lint-json") { dir =>
        Given("an entity CML with a raw string attribute")
        val path = _write(
          dir.resolve("model.cml"),
          """# ENTITY
            |
            |## Exhibition
            |
            |### Attribute
            |
            || name | type   | multiplicity |
            ||------+--------+--------------|
            || name | string | 1            |
            |""".stripMargin
        )

        When("Cozy renders JSON lint output")
        val json = CozyCmlLint.toJson(CozyCmlLint.lint(path))

        Then("the result is machine readable")
        json should include(""""findings"""")
        json should include(""""code":"cml.domain.string-attribute"""")
        json should include(""""level":"FAIL"""")
      }
    }
  }

  private def _with_temp_dir[A](prefix: String)(body: Path => A): A = {
    val dir = Files.createTempDirectory(prefix)
    try {
      body(dir)
    } finally {
      _delete(dir)
    }
  }

  private def _write(path: Path, content: String): Path = {
    Files.createDirectories(path.getParent)
    Files.write(path, content.getBytes(StandardCharsets.UTF_8))
    path
  }

  private def _delete(path: Path): Unit =
    if (Files.exists(path)) {
      val stream = Files.walk(path)
      try {
        stream.sorted(java.util.Comparator.reverseOrder()).forEach(Files.delete(_))
      } finally {
        stream.close()
      }
    }
}
