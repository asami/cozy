package cozy.lint

import java.io.{ByteArrayOutputStream, PrintStream}
import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path}
import java.util.zip.{ZipEntry, ZipOutputStream}
import scala.collection.JavaConverters._
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Jul.  7, 2026
 * @version Jul. 15, 2026
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

    "identify each operation signature field in an ABI change" in {
      _with_temp_dir("cozy-car-abi-generated-name-change") { dir =>
        Given("a minor upgrade whose operation kind, generated type names, and execution changed")
        val baseline = _write_manifest(dir.resolve("baseline.json"), _manifest("1.4.0"))
        val current = _write_manifest(
          dir.resolve("current.json"),
          _manifest(
            "1.5.0",
            changedinput = true,
            changedoutput = true,
            changedkind = true,
            changedexecution = true
          )
        )

        When("Cozy compares the generated operation ABI")
        val findings = CozyCarAbiLint.lint(current, Some(baseline))

        Then("the diagnostic names each changed generated identity and its previous value")
        val finding = findings.find(_.code == "abi.operation.changed").getOrElse(fail("operation ABI change finding is missing"))
        finding.level shouldBe CozyCarAbiLint.Level.Fail
        finding.message should include ("kind: 'command' -> 'query'")
        finding.message should include ("input: 'RegisterUser' -> 'RegisterUserV2'")
        finding.message should include ("output: 'OperationResult' -> 'RegisterUserResult'")
        finding.message should include ("execution: 'async' -> 'sync'")
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

    "compare service-qualified operation identities" in {
      _with_temp_dir("cozy-car-abi-qualified-operation") { dir =>
        Given("a baseline that exports the same operation name from two services")
        val baseline = _write_manifest(dir.resolve("baseline.json"), _full_surface_manifest("1.4.0"))
        val current = _write_manifest(dir.resolve("current.json"), _full_surface_manifest("1.5.0", secondaryservice = false))

        When("Cozy compares the full CAR ABI surface")
        val findings = CozyCarAbiLint.lint(current, Some(baseline))

        Then("removing one service does not hide its operation behind the remaining operation name")
        findings.find(_.code == "abi.service.removed").map(_.level) shouldBe Some(CozyCarAbiLint.Level.Fail)
        val removed = findings.find(_.code == "abi.operation.removed").getOrElse(fail("qualified operation removal is missing"))
        removed.level shouldBe CozyCarAbiLint.Level.Fail
        removed.message should include ("Audit.lookup")
      }
    }

    "reject request and response type field contract changes" in {
      _with_temp_dir("cozy-car-abi-type-field") { dir =>
        Given("a full-surface baseline and a minor upgrade that changes request-field multiplicity")
        val baseline = _write_manifest(dir.resolve("baseline.json"), _full_surface_manifest("1.4.0"))
        val current = _write_manifest(dir.resolve("current.json"), _full_surface_manifest("1.5.0", multiplicity = "0..1"))

        When("Cozy compares the generated type surface")
        val findings = CozyCarAbiLint.lint(current, Some(baseline))

        Then("the changed multiplicity is a breaking type-field change")
        findings.find(_.code == "abi.type.field.changed").map(_.level) shouldBe Some(CozyCarAbiLint.Level.Fail)
      }
    }

    "normalize legacy fields that omit default multiplicity" in {
      _with_temp_dir("cozy-car-abi-legacy-multiplicity") { dir =>
        Given("a legacy baseline with required fields and a patch manifest that writes explicit single multiplicity")
        val baseline = _write_manifest(dir.resolve("baseline.json"), _manifest("1.4.0"))
        val explicitmultiplicity = _manifest("1.4.1").replace(
          "\"required\": true",
          "\"multiplicity\": \"1\",\n              \"required\": true"
        )
        val current = _write_manifest(dir.resolve("current.json"), explicitmultiplicity)

        When("Cozy compares the legacy and canonical field forms")
        val findings = CozyCarAbiLint.lint(current, Some(baseline))

        Then("omitting the historical default does not create a false breaking change")
        findings.find(_.code == "abi.compatibility.patch").map(_.level) shouldBe Some(CozyCarAbiLint.Level.Ok)
        findings.exists(_.code == "abi.patch.entity.field.changed") shouldBe false
      }
    }

    "report one dependency-range change for a patch upgrade" in {
      _with_temp_dir("cozy-car-abi-dependency-range") { dir =>
        Given("a patch upgrade that changes one component ABI dependency range")
        val baseline = _write_manifest(dir.resolve("baseline.json"), _with_dependency(_manifest("1.4.0"), "[1.0.0,2.0.0)"))
        val current = _write_manifest(dir.resolve("current.json"), _with_dependency(_manifest("1.4.1"), "[1.0.0,3.0.0)"))

        When("Cozy compares the dependency surface")
        val findings = CozyCarAbiLint.lint(current, Some(baseline))

        Then("the changed range is rejected once without duplicate diagnostics")
        val rangechanges = findings.filter(_.code == "abi.patch.dependency.range-changed")
        rangechanges should have size 1
        rangechanges.head.level shouldBe CozyCarAbiLint.Level.Fail
      }
    }

    "reject one added dependency in a patch upgrade" in {
      _with_temp_dir("cozy-car-abi-dependency-added") { dir =>
        Given("a patch upgrade that adds one component ABI dependency")
        val baseline = _write_manifest(dir.resolve("baseline.json"), _manifest("1.4.0"))
        val current = _write_manifest(dir.resolve("current.json"), _with_dependency(_manifest("1.4.1"), "[1.0.0,2.0.0)"))

        When("Cozy compares the dependency surface")
        val findings = CozyCarAbiLint.lint(current, Some(baseline))

        Then("the added dependency is rejected once at patch level")
        val additions = findings.filter(_.code == "abi.patch.dependency.added")
        additions should have size 1
        additions.head.level shouldBe CozyCarAbiLint.Level.Fail
      }
    }

    "order SemVer prerelease qualifiers before the final release" in {
      _with_temp_dir("cozy-car-abi-semver-final") { dir =>
        Given("an ABI baseline from a prerelease and the unchanged final release")
        val baseline = _write_manifest(dir.resolve("baseline.json"), _manifest("1.4.0-rc.1"))
        val current = _write_manifest(dir.resolve("current.json"), _manifest("1.4.0"))

        When("Cozy evaluates the qualifier-aware version policy")
        val findings = CozyCarAbiLint.lint(current, Some(baseline))

        Then("the final release is a valid successor at the same core version")
        findings.find(_.code == "abi.compatibility.patch").map(_.level) shouldBe Some(CozyCarAbiLint.Level.Ok)
        findings.exists(_.code == "abi.version.regression") shouldBe false
      }
    }

    "reject a prerelease behind its released ABI baseline" in {
      _with_temp_dir("cozy-car-abi-semver-regression") { dir =>
        Given("a final ABI baseline and a current SNAPSHOT at the same core version")
        val baseline = _write_manifest(dir.resolve("baseline.json"), _manifest("1.4.0"))
        val current = _write_manifest(dir.resolve("current.json"), _manifest("1.4.0-SNAPSHOT"))

        When("Cozy evaluates the qualifier-aware version policy")
        val findings = CozyCarAbiLint.lint(current, Some(baseline))

        Then("the prerelease is rejected as a version regression")
        findings.find(_.code == "abi.version.regression").map(_.level) shouldBe Some(CozyCarAbiLint.Level.Fail)
      }
    }

    "reject a baseline retained for another CAR" in {
      _with_temp_dir("cozy-car-abi-name-mismatch") { dir =>
        Given("an ABI baseline whose CAR identity differs from the current manifest")
        val othermanifest = _manifest("1.4.0").replace("textus-user-account", "textus-other-component")
        val baseline = _write_manifest(dir.resolve("baseline.json"), othermanifest)
        val current = _write_manifest(dir.resolve("current.json"), _manifest("1.5.0"))

        When("Cozy starts compatibility comparison")
        val findings = CozyCarAbiLint.lint(current, Some(baseline))

        Then("the unrelated baseline is rejected before SemVer policy is applied")
        findings.find(_.code == "abi.car.name.changed").map(_.level) shouldBe Some(CozyCarAbiLint.Level.Fail)
        findings.exists(_.code == "abi.compatibility.patch") shouldBe false
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

    "allow the first ABI baseline missing warning in strict mode" in {
      _with_temp_dir("cozy-car-abi-first-baseline") { dir =>
        Given("a CAR ABI manifest from the first release that starts ABI operation")
        val current = _write_manifest(dir.resolve("current.json"), _manifest("1.4.0"))

        When("Cozy lints the CAR ABI in strict mode without a baseline")
        val out = new ByteArrayOutputStream()
        val exitcode = Console.withOut(new PrintStream(out, true, StandardCharsets.UTF_8.name())) {
          CozyCarAbiLint.execute(List(current.toString, "--strict"))
        }

        Then("the missing baseline remains a warning but does not stop the initial release")
        exitcode shouldBe 0
        out.toString(StandardCharsets.UTF_8.name()) should include ("abi.baseline.missing")
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

    "warn when a CAR project root has no current ABI manifest" in {
      _with_temp_dir("cozy-car-abi-current-missing") { dir =>
        Given("a CAR project before an ABI manifest has been generated or authored")
        _write(dir.resolve("project.yaml"), "project:\n  name: sample\n  type: car\n")

        When("Cozy lints the project ABI")
        val findings = CozyCarAbiLint.lint(dir, None)

        Then("the missing current manifest is a warning for project-root lint")
        findings.find(_.code == "abi.manifest.missing").map(_.level) shouldBe Some(CozyCarAbiLint.Level.Warn)
      }
    }

    "prefer the highest source versioned baseline over generated target baselines" in {
      _with_temp_dir("cozy-car-abi-source-versioned-baseline") { dir =>
        Given("a CAR project with source-managed release baselines and target-generated fallback baselines")
        _write(dir.resolve("project.yaml"), "project:\n  name: sample\n  type: car\n")
        _write_manifest(dir.resolve("src/main/car/1.3.0/abi-manifest.json"), _manifest("1.3.0", changedinput = true))
        _write_manifest(dir.resolve("src/main/car/1.4.0/abi-manifest.json"), _manifest("1.4.0"))
        _write_manifest(dir.resolve("target/cozy/abi-baseline.json"), _manifest("1.3.0", changedinput = true))
        _write_manifest(dir.resolve("target/cozy/abi-manifest.json"), _manifest("1.4.1"))

        When("Cozy lints the project ABI without an explicit baseline")
        val findings = CozyCarAbiLint.lint(dir, None)

        Then("the nearest lower source-managed release manifest is used as the compatibility baseline")
        findings.find(_.code == "abi.compatibility.patch").map(_.level) shouldBe Some(CozyCarAbiLint.Level.Ok)
        findings.exists(_.code == "abi.operation.changed") shouldBe false
      }
    }

    "ignore top-level current and non-release manifests when selecting automatic baselines" in {
      _with_temp_dir("cozy-car-abi-source-baseline-filter") { dir =>
        Given("a CAR project with a source current manifest and ineligible baseline directories")
        _write(dir.resolve("project.yaml"), "project:\n  name: sample\n  type: car\n")
        _write_manifest(dir.resolve("src/main/car/abi-manifest.json"), _manifest("1.4.1"))
        _write_manifest(dir.resolve("src/main/car/1.4.1/abi-manifest.json"), _manifest("1.4.1", changedinput = true))
        _write_manifest(dir.resolve("src/main/car/1.4.2/abi-manifest.json"), _manifest("1.4.2", changedinput = true))
        _write_manifest(dir.resolve("src/main/car/1.4.0-SNAPSHOT/abi-manifest.json"), _manifest("1.4.0", changedinput = true))
        _write_manifest(dir.resolve("src/main/car/latest/abi-manifest.json"), _manifest("1.4.0", changedinput = true))
        _write_manifest(dir.resolve("src/main/car/1.3.9/abi-manifest.json"), _manifest("1.3.9"))

        When("Cozy lints the project ABI without an explicit baseline")
        val findings = CozyCarAbiLint.lint(dir, None)

        Then("only the highest lower released SemVer directory is eligible")
        findings.exists(_.level == CozyCarAbiLint.Level.Fail) shouldBe false
        findings.exists(_.code == "abi.baseline.missing") shouldBe false
      }
    }

    "select the nearest retained prerelease baseline by full SemVer order" in {
      _with_temp_dir("cozy-car-abi-prerelease-baseline") { dir =>
        Given("a CAR project with retained release-candidate and older final ABI baselines")
        _write(dir.resolve("project.yaml"), "project:\n  name: sample\n  type: car\n")
        _write_manifest(dir.resolve("src/main/car/1.3.9/abi-manifest.json"), _manifest("1.3.9"))
        _write_manifest(dir.resolve("src/main/car/1.4.0-rc.2/abi-manifest.json"), _manifest("1.4.0-rc.2", changedinput = true))
        _write_manifest(dir.resolve("target/cozy/abi-manifest.json"), _manifest("1.4.0-rc.10"))

        When("Cozy discovers the source-managed baseline")
        val findings = CozyCarAbiLint.lint(dir, None)

        Then("numeric qualifier order selects the nearest release candidate instead of the older final version")
        findings.find(_.code == "abi.patch.operation.changed").map(_.level) shouldBe Some(CozyCarAbiLint.Level.Fail)
        findings.exists(_.code == "abi.baseline.missing") shouldBe false
      }
    }

    "reject a stale retained-baseline directory coordinate" in {
      _with_temp_dir("cozy-car-abi-retained-coordinate") { dir =>
        Given("a retained baseline whose directory and manifest declare different versions")
        _write(dir.resolve("project.yaml"), "project:\n  name: sample\n  type: car\n")
        _write_manifest(dir.resolve("src/main/car/1.4.0/abi-manifest.json"), _manifest("1.3.0"))
        _write_manifest(dir.resolve("target/cozy/abi-manifest.json"), _manifest("1.4.1"))

        When("Cozy discovers the source-managed baseline")
        val findings = CozyCarAbiLint.lint(dir, None)

        Then("the stale retained coordinate is rejected before ABI comparison")
        findings.find(_.code == "abi.baseline.version-mismatch").map(_.level) shouldBe Some(CozyCarAbiLint.Level.Fail)
        findings.exists(_.code == "abi.compatibility.patch") shouldBe false
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
    changedoutput: Boolean = false,
    changedkind: Boolean = false,
    changedexecution: Boolean = false,
    extracomponent: Boolean = false
  ): String = {
    val kind = if (changedkind) "query" else "command"
    val input = if (changedinput) "RegisterUserV2" else "RegisterUser"
    val output = if (changedoutput) "RegisterUserResult" else "OperationResult"
    val execution = if (changedexecution) "sync" else "async"
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
       |          "kind": "$kind",
       |          "input": "$input",
       |          "output": "$output",
       |          "execution": "$execution"
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

  private def _full_surface_manifest(
    version: String,
    secondaryservice: Boolean = true,
    multiplicity: String = "1"
  ): String = {
    val secondaryservicejson =
      if (secondaryservice)
        """,
          |        {
          |          "name": "Audit"
          |        }""".stripMargin
      else
        ""
    val secondaryoperationjson =
      if (secondaryservice)
        """,
          |        {
          |          "service": "Audit",
          |          "name": "lookup",
          |          "kind": "QUERY",
          |          "input": "LookupRequest",
          |          "output": "LookupResponse"
          |        }""".stripMargin
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
       |        }
       |      ],
       |      "services": [
       |        {
       |          "name": "Catalog"
       |        }$secondaryservicejson
       |      ],
       |      "operations": [
       |        {
       |          "service": "Catalog",
       |          "name": "lookup",
       |          "kind": "QUERY",
       |          "input": "LookupRequest",
       |          "output": "LookupResponse"
       |        }$secondaryoperationjson
       |      ],
       |      "types": [
       |        {
       |          "name": "LookupRequest",
       |          "kind": "query",
       |          "fields": [
       |            {
       |              "name": "id",
       |              "type": "String",
       |              "multiplicity": "$multiplicity",
       |              "required": true
       |            }
       |          ]
       |        },
       |        {
       |          "name": "LookupResponse",
       |          "kind": "value",
       |          "fields": []
       |        }
       |      ],
       |      "entities": []
       |    },
       |    "dependencies": []
       |  }
       |}
       |""".stripMargin
  }

  private def _with_dependency(manifest: String, abirange: String): String =
    manifest.replace(
      "\"dependencies\": []",
      s""""dependencies": [
         |      {
         |        "name": "textus-shared-contract",
         |        "abiRange": "$abirange"
         |      }
         |    ]""".stripMargin
    )

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
