package cozy.compatibility

import cozy.config.CozyProjectYamlConfig
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Jul. 28, 2026
 *  version Jul. 28, 2026
 *  version Aug. 21, 2026
 * @version Sep.  1, 2026
 * @author  ASAMI, Tomoharu
 */
final class Phase51Cv07DevelopmentReleaseAcceptanceSpec
    extends AnyWordSpec
    with Matchers
    with GivenWhenThen {
  private val _owner =
    GenerationEvidenceOwner(
      "CV-07 executable specification",
      "Phase51Cv07DevelopmentReleaseAcceptanceSpec"
    )
  private val _releasedpair =
    GenerationCompatibilityBoundary.createPair("0.5.1", "0.3.0")
  private val _developmentpair =
    GenerationCompatibilityBoundary.createPair(
      "0.5.3-SNAPSHOT",
      org.simplemodeling.cozy.BuildInfo.version
    )
  private val _evidence =
    GenerationCompatibilityEvidence(
      GenerationCompatibility.evidenceSchema,
      _owner,
      Vector(GenerationPairEvidence(_releasedpair, GenerationPairStatus.Proven)),
      Some(_releasedpair)
    )

  "Cozy CV-07 generation acceptance" should {
    "derive lifecycle from the owning output version" which {
      "accept an explicit mutable pair only for development output" in {
        Given("an exact SNAPSHOT target and generator pair without persistent pair evidence")
        val sources = GenerationSourceValues(
          Some(_developmentpair),
          Some(_developmentpair),
          None
        )

        When("development and release outputs are evaluated")
        val development = GenerationCompatibility.accept(
          sources,
          "0.1.0-SNAPSHOT",
          org.simplemodeling.cozy.BuildInfo.version,
          _evidence
        )
        val release = GenerationCompatibility.accept(
          sources,
          "0.1.0",
          org.simplemodeling.cozy.BuildInfo.version,
          _evidence
        )

        Then("development is admitted with a notice and release rejection precedes evidence lookup")
        development.lifecycle shouldBe GenerationLifecycle.Development
        development.result shouldBe GenerationAdmission.Supported
        development.notices.map(_.code) shouldBe Vector(
          GenerationNoticeCode.MutableDevelopmentPairAccepted
        )
        release.lifecycle shouldBe GenerationLifecycle.Release
        release.result shouldBe GenerationAdmission.Unsupported
        release.diagnostics.map(_.code) shouldBe Vector(
          GenerationDiagnosticCode.SnapshotNotAllowedForRelease
        )
      }

      "accept an immutable proven pair for release output" in {
        Given("an exact proven released target and generator pair")
        val sources =
          GenerationSourceValues(Some(_releasedpair), None, None)

        When("a release output is evaluated")
        val decision = GenerationCompatibility.accept(
          sources,
          "0.1.0",
          "0.3.0",
          _evidence
        )

        Then("the immutable release pair is admitted without a development notice")
        decision.lifecycle shouldBe GenerationLifecycle.Release
        decision.result shouldBe GenerationAdmission.Supported
        decision.notices shouldBe empty
      }
    }

    "bind execution to the selected exact Cozy coordinate" which {
      "reject a cache or launcher process executing a different Cozy version" in {
        Given("a selected released pair but a different executing generator")
        val sources =
          GenerationSourceValues(Some(_releasedpair), None, None)

        When("the accepted pair is checked against the executing BuildInfo version")
        val decision = GenerationCompatibility.accept(
          sources,
          "0.1.0",
          "0.3.1-SNAPSHOT",
          _evidence
        )

        Then("the execution identity mismatch is deterministic")
        decision.result shouldBe GenerationAdmission.Unsupported
        decision.diagnostics.map(_.code) shouldBe Vector(
          GenerationDiagnosticCode.ExecutingGeneratorMismatch
        )
        decision.diagnostics.head.expected shouldBe Some("0.3.0")
        decision.diagnostics.head.actual shouldBe Some("0.3.1-SNAPSHOT")
      }
    }

    "derive lifecycle from the command-owned output version" which {
      "require component output identity for component model generation" in {
        Given("a component model request with exact generator coordinates but no component version")
        val args = List(
          "modeler-scala",
          "sample.cml",
          "--cncf-version", "0.5.1",
          "--cozy-generator-version",
          org.simplemodeling.cozy.BuildInfo.version
        )

        When("the CLI generation boundary resolves the owning output")
        val error = intercept[Throwable] {
          GenerationCompatibilityBoundary.requireValidInvocation(args, "spec")
        }

        Then("the CNCF target is not substituted for the missing component lifecycle")
        error.getMessage should include("MissingComponentVersion")
        error.getMessage should include("expected=--component-version")
      }

      "require scaffold output identity for CAR project generation" in {
        Given("a CAR scaffold request with exact generator coordinates but no project version")
        val args = List(
          "car-sbt-project",
          "sample.cml",
          "--cncf-version", "0.5.1",
          "--cozy-generator-version",
          org.simplemodeling.cozy.BuildInfo.version
        )

        When("the CLI generation boundary resolves the owning output")
        val error = intercept[Throwable] {
          GenerationCompatibilityBoundary.requireValidInvocation(args, "spec")
        }

        Then("the target runtime version is not treated as the generated CAR version")
        error.getMessage should include("MissingVersion")
        error.getMessage should include("expected=--version")
      }

      "use the CNCF target lifecycle only for CNCF value generation" in {
        Given("the CNCF-owned value-model generation command")
        val args = List(
          "modeler-scala-value",
          "information.cml",
          "--cncf-version", "0.5.3-SNAPSHOT",
          "--cozy-generator-version",
          org.simplemodeling.cozy.BuildInfo.version
        )

        When("the CLI generation boundary evaluates the request")
        val decision =
          GenerationCompatibilityBoundary.requireValidInvocation(args, "spec").get

        Then("the explicit mutable pair is admitted as CNCF development output")
        decision.lifecycle shouldBe GenerationLifecycle.Development
        decision.result shouldBe GenerationAdmission.Supported
      }
    }

    "keep the published default owned by compatibility evidence" which {
      "reject a mutable or unproven published default" in {
        Given("evidence that attempts to publish a mutable development pair as default")
        val mutabledefault = _evidence.copy(
          publishedDefault = Some(_developmentpair)
        )
        val unproven = _evidence.copy(
          entries = Vector(
            GenerationPairEvidence(_releasedpair, GenerationPairStatus.Unproven)
          ),
          publishedDefault = Some(_releasedpair)
        )

        When("the evidence is validated")
        val mutableerrors = GenerationCompatibility.validate(mutabledefault)
        val unprovenerrors = GenerationCompatibility.validate(unproven)

        Then("neither default is accepted")
        mutableerrors.map(_.code) should contain(
          GenerationDiagnosticCode.MalformedEvidence
        )
        unprovenerrors.map(_.code) should contain(
          GenerationDiagnosticCode.MalformedEvidence
        )
      }

      "fail when neither an explicit pair nor a published default exists" in {
        Given("valid evidence without a published default")
        val nodefault = _evidence.copy(publishedDefault = None)

        When("generation has no explicit source")
        val decision = GenerationCompatibility.accept(
          GenerationSourceValues(None, None, None),
          "0.1.0-SNAPSHOT",
          "0.3.0",
          nodefault
        )

        Then("the missing authority is reported without inferred coordinates")
        decision.result shouldBe GenerationAdmission.Unsupported
        decision.pair shouldBe None
        decision.diagnostics.map(_.code) shouldBe Vector(
          GenerationDiagnosticCode.MissingPublishedDefault
        )
      }
    }

    "ship immutable-only evidence with the current immutable default" in {
      Given("the packaged Cozy compatibility evidence")

      When("the resource is loaded")
      val evidence = GenerationCompatibilityEvidenceLoader.load().toOption.get

      Then("only immutable release evidence is packaged and the immutable current pair remains default")
      evidence.entries shouldBe Vector(
        GenerationPairEvidence(
          GenerationCompatibilityBoundary.createPair(
            "0.5.1",
            "0.3.0"
          ),
          GenerationPairStatus.Unproven
        ),
        GenerationPairEvidence(
          GenerationCompatibilityBoundary.createPair(
            "0.5.2",
            "0.3.1"
          ),
          GenerationPairStatus.Proven
        ),
        GenerationPairEvidence(
          GenerationCompatibilityBoundary.createPair(
            "0.5.2",
            "0.3.2.1"
          ),
          GenerationPairStatus.Proven
        ),
        GenerationPairEvidence(
          GenerationCompatibilityBoundary.createPair(
            "0.5.2",
            "0.3.2.2"
          ),
          GenerationPairStatus.Proven
        )
      )
      evidence.publishedDefault shouldBe Some(
        GenerationCompatibilityBoundary.createPair("0.5.2", "0.3.2.2")
      )
    }

    "apply the lifecycle gate to CAR project metadata" which {
      "accept the ArtScene development shape and reject the same pair for release" in {
        Given("ArtScene-shaped metadata with an exact development generator pair")
        val development = _artscene_metadata("0.1.2-SNAPSHOT")
        val release = _artscene_metadata("0.1.2")

        When("the CAR project boundary evaluates both owning output versions")
        val developmentdecision =
          CarMetadataCompatibility.evaluateProject(development)
        val releasedecision =
          CarMetadataCompatibility.evaluateProject(release)

        Then("only the development output admits the mutable exact pair")
        developmentdecision.isAccepted shouldBe true
        releasedecision.isAccepted shouldBe false
        releasedecision.diagnostics.map(_.code) should contain(
          CarMetadataCompatibility.DiagnosticCode.ReleaseGenerationPairRejected
        )
      }
    }
  }

  private def _artscene_metadata(
    outputversion: String
  ): CozyProjectYamlConfig.Config =
    CozyProjectYamlConfig.Config(
      Map(
        "project.kind" -> "car",
        "project.component.version" -> outputversion,
        "build.cozyVersion" -> "0.3.2-SNAPSHOT",
        "packaging.car.runtime.cncf.minimum" -> "0.5.1"
      ),
      Map(
        "build.dependencies.compile" ->
          Vector("org.goldenport::goldenport-cncf:0.5.1"),
        "packaging.car.runtime.cncf.tested" -> Vector("0.5.1")
      )
    )
}
