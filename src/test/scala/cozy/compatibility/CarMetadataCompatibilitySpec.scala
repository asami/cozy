package cozy.compatibility

import cozy.compatibility.CarMetadataCompatibility.DiagnosticCode
import cozy.config.CozyProjectYamlConfig
import org.scalacheck.{Gen, Prop, Test}
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

final class CarMetadataCompatibilitySpec
    extends AnyWordSpec
    with Matchers
    with GivenWhenThen {
  private val _cozy_version = org.simplemodeling.cozy.BuildInfo.version
  private val _cncf_coordinate = "org.goldenport::goldenport-cncf:0.5.17"

  "CAR project metadata compatibility" should {
    "recognize either project or packaging metadata as the CAR boundary" in {
      Given("metadata whose project kind is generic but whose packaging kind is CAR")
      val metadata = _metadata(
        Some(_cozy_version),
        Vector(_cncf_coordinate),
        Some("0.5.17"),
        Some("0.5.19"),
        Vector.empty,
        Vector("0.5.17")
      ).copy(values = Map(
        "project.kind" -> "component",
        "packaging.kind" -> "car",
        "build.cozyVersion" -> _cozy_version,
        "packaging.car.runtime.cncf.minimum" -> "0.5.17",
        "packaging.car.runtime.cncf.maximum" -> "0.5.19"
      ))

      When("the compatibility evaluator classifies and validates the project")
      val decision = CarMetadataCompatibility.evaluate(
        metadata,
        Vector(_artifact("0.5.17"))
      )

      Then("the packaging declaration activates the strict CAR gate")
      decision.isAccepted shouldBe true
    }

    "share one project decision before resolved artifact admission" in {
      Given("a valid project-owned contract and a variant that excludes its compile target")
      val valid = _metadata(
        Some(_cozy_version),
        Vector(_cncf_coordinate),
        Some("0.5.17"),
        Some("0.5.19"),
        Vector.empty,
        Vector("0.5.17")
      )
      val excluded = valid.copy(lists = valid.lists.updated(
        "packaging.car.runtime.cncf.excluded",
        Vector("0.5.17")
      ))

      When("the metadata-only evaluator checks both project contracts")
      val accepted = CarMetadataCompatibility.evaluateProject(valid)
      val rejected = CarMetadataCompatibility.evaluateProject(excluded)
      val report = accepted.contract.map(_.render).getOrElse("")

      Then("the accepted report is complete and the contradiction keeps the package diagnostic")
      accepted.isAccepted shouldBe true
      accepted.diagnostics shouldBe empty
      report should include("cozyVersion=" + _cozy_version)
      report should include("cncfCompileCoordinate=" + _cncf_coordinate)
      report should include("runtimeMinimum=0.5.17")
      report should include("runtimeMaximum=0.5.19")
      report should include("runtimeExcluded=")
      report should include("runtimeTested=0.5.17")
      rejected.diagnostics.map(_.code) should contain(
        DiagnosticCode.CompileTargetExcluded
      )
      rejected.diagnostics.map(_.code) should not contain(
        DiagnosticCode.ResolvedCncfVersionMissing
      )
    }

    "reject contradictory project and resolved-artifact evidence" which {
      "returns stable typed diagnostics for every owned semantic" in {
        Given("one valid contract and variants that remove or contradict each owned value")
        val valid = _metadata(
          Some(_cozy_version),
          Vector(_cncf_coordinate),
          Some("0.5.17"),
          Some("0.5.19"),
          Vector("0.5.16"),
          Vector("0.5.17")
        )
        val resolved = Vector(_artifact("0.5.17"))
        val variants = Vector(
          "missing-cozy" -> (
            valid.copy(values = valid.values - "build.cozyVersion"),
            resolved,
            DiagnosticCode.CozyVersionMissing
          ),
          "missing-compile" -> (
            valid.copy(lists = valid.lists.updated("build.dependencies.compile", Vector.empty)),
            resolved,
            DiagnosticCode.CncfCompileTargetMissing
          ),
          "multiple-compile" -> (
            valid.copy(lists = valid.lists.updated(
              "build.dependencies.compile",
              Vector(_cncf_coordinate, "org.goldenport:goldenport-cncf_3:0.5.17")
            )),
            resolved,
            DiagnosticCode.CncfCompileTargetMultiple
          ),
          "missing-minimum" -> (
            valid.copy(values = valid.values - "packaging.car.runtime.cncf.minimum"),
            resolved,
            DiagnosticCode.RuntimeMinimumMissing
          ),
          "missing-tested" -> (
            valid.copy(lists = valid.lists.updated("packaging.car.runtime.cncf.tested", Vector.empty)),
            resolved,
            DiagnosticCode.RuntimeTestedMissing
          ),
          "inverted-range" -> (
            valid.copy(values = valid.values ++ Map(
              "packaging.car.runtime.cncf.minimum" -> "0.5.20",
              "packaging.car.runtime.cncf.maximum" -> "0.5.19"
            )),
            resolved,
            DiagnosticCode.RuntimeRangeInvalid
          ),
          "below-minimum" -> (
            valid.copy(values = valid.values.updated(
              "packaging.car.runtime.cncf.minimum",
              "0.5.18"
            )),
            resolved,
            DiagnosticCode.CompileTargetBelowMinimum
          ),
          "above-maximum" -> (
            valid.copy(values = valid.values.updated(
              "packaging.car.runtime.cncf.maximum",
              "0.5.16"
            )),
            resolved,
            DiagnosticCode.CompileTargetAboveMaximum
          ),
          "excluded" -> (
            valid.copy(lists = valid.lists.updated(
              "packaging.car.runtime.cncf.excluded",
              Vector("0.5.17")
            )),
            resolved,
            DiagnosticCode.CompileTargetExcluded
          ),
          "untested" -> (
            valid.copy(lists = valid.lists.updated(
              "packaging.car.runtime.cncf.tested",
              Vector("0.5.18")
            )),
            resolved,
            DiagnosticCode.CompileTargetUntested
          ),
          "resolved-missing" -> (
            valid,
            Vector.empty,
            DiagnosticCode.ResolvedCncfVersionMissing
          ),
          "resolved-version-mismatch" -> (
            valid,
            Vector(_artifact("0.5.18")),
            DiagnosticCode.ResolvedCncfVersionMismatch
          ),
          "resolved-version-missing" -> (
            valid,
            Vector(_artifact("0.5.17").copy(version = None)),
            DiagnosticCode.ResolvedCncfVersionMismatch
          ),
          "resolved-identity-mismatch" -> (
            valid,
            Vector(_artifact(
              "0.5.17",
              "other",
              "com.example:other-runtime_3:0.5.17"
            )),
            DiagnosticCode.ResolvedCncfIdentityMismatch
          ),
          "resolved-multiple" -> (
            valid,
            Vector(
              _artifact("0.5.17", source = "first.jar"),
              _artifact("0.5.17", source = "second.jar")
            ),
            DiagnosticCode.ResolvedCncfArtifactMultiple
          )
        )

        When("the production metadata evaluator checks every variant")
        val results = variants.map { case (name, (metadata, artifacts, expected)) =>
          name -> (
            CarMetadataCompatibility.evaluate(metadata, artifacts).diagnostics.map(_.code),
            expected
          )
        }

        Then("each contradiction retains its owning semantic diagnostic")
        results.foreach { case (name, (actual, expected)) =>
          withClue(name) {
            actual should contain(expected)
          }
        }
      }
    }

    "admit exact generated patch-version contracts as a property" in {
      Given("generated CNCF patch versions used consistently by compile, runtime, and resolution")
      val versions = Gen.choose(1, 500).map(patch => s"0.5.$patch")
      val property = Prop.forAll(versions) { version =>
        val metadata = _metadata(
          Some(_cozy_version),
          Vector(s"org.goldenport::goldenport-cncf:$version"),
          Some(version),
          Some(version),
          Vector.empty,
          Vector(version)
        )
        val decision = CarMetadataCompatibility.evaluate(
          metadata,
          Vector(_artifact(version))
        )
        decision.isAccepted &&
          decision.contract.exists(_.cncfCompileTarget.version == version) &&
          decision.contract.exists(_.runtimeCompatibility.tested == Vector(version))
      }

      When("the production evaluator checks at least fifty generated contracts")
      val result = Test.check(
        Test.Parameters.default.withMinSuccessfulTests(50),
        property
      )

      Then("every exact contract is accepted without numeric-version inference")
      result.passed shouldBe true
    }
  }

  private def _artifact(
    version: String,
    runtime: String = "cncf",
    modulecoordinate: String = "",
    source: String = "goldenport-cncf_3.jar"
  ): CarMetadataCompatibility.ResolvedCncfArtifact = {
    val module =
      if (modulecoordinate.isEmpty)
        s"org.goldenport:goldenport-cncf_3:$version"
      else
        modulecoordinate
    CarMetadataCompatibility.ResolvedCncfArtifact(
      source,
      Some(runtime),
      Some(module),
      Some(version)
    )
  }

  private def _metadata(
    cozyversion: Option[String],
    dependencies: Vector[String],
    minimum: Option[String],
    maximum: Option[String],
    excluded: Vector[String],
    tested: Vector[String]
  ): CozyProjectYamlConfig.Config = {
    val values =
      Map(
        "project.kind" -> "car",
        "packaging.kind" -> "car"
      ) ++
        cozyversion.map("build.cozyVersion" -> _) ++
        minimum.map("packaging.car.runtime.cncf.minimum" -> _) ++
        maximum.map("packaging.car.runtime.cncf.maximum" -> _)
    CozyProjectYamlConfig.Config(
      values,
      Map(
        "build.dependencies.compile" -> dependencies,
        "packaging.car.runtime.cncf.excluded" -> excluded,
        "packaging.car.runtime.cncf.tested" -> tested
      )
    )
  }
}
