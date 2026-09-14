package cozy.document

import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Sep. 14, 2026
 * @version Sep. 14, 2026
 * @author  ASAMI, Tomoharu
 */
final class CozyDocumentProjectLocalBuildSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  "Cozy Document Project local-build catalog" should {
    "request a build when the output is absent" in {
      Given("a required input with a timestamp and an absent output")
      val observation = CozyDocumentProjectLocalBuild.FreshnessObservation(
        Vector(CozyDocumentProjectLocalBuild.InputObservation("index.dox", Some(10L))),
        CozyDocumentProjectLocalBuild.OutputObservation(None),
        force = false
      )

      When("freshness is evaluated")
      val decision = CozyDocumentProjectLocalBuild.evaluate(observation)

      Then("the missing-output decision requests a build")
      decision shouldBe CozyDocumentProjectLocalBuild.FreshnessDecision.MissingOutput
    }

    "request a build when a dependency is newer than the output" in {
      Given("a current output and a strictly newer required input")
      val observation = CozyDocumentProjectLocalBuild.FreshnessObservation(
        Vector(CozyDocumentProjectLocalBuild.InputObservation("content/core.yaml", Some(20L))),
        CozyDocumentProjectLocalBuild.OutputObservation(Some(10L)),
        force = false
      )

      When("freshness is evaluated")
      val decision = CozyDocumentProjectLocalBuild.evaluate(observation)

      Then("the newer dependency decision requests a build")
      decision shouldBe CozyDocumentProjectLocalBuild.FreshnessDecision.DependencyNewer("content/core.yaml")
    }

    "reuse an output when input and output timestamps are equal" in {
      Given("a required input and output with equal timestamps")
      val observation = CozyDocumentProjectLocalBuild.FreshnessObservation(
        Vector(CozyDocumentProjectLocalBuild.InputObservation("config", Some(10L))),
        CozyDocumentProjectLocalBuild.OutputObservation(Some(10L)),
        force = false
      )

      When("freshness is evaluated")
      val decision = CozyDocumentProjectLocalBuild.evaluate(observation)

      Then("strict newer-than semantics reuse the current output")
      decision shouldBe CozyDocumentProjectLocalBuild.FreshnessDecision.ReuseCurrentOutput
    }

    "reject a missing required input before considering other decisions" in {
      Given("a missing required input, an absent output, and force enabled")
      val observation = CozyDocumentProjectLocalBuild.FreshnessObservation(
        Vector(CozyDocumentProjectLocalBuild.InputObservation("content/en/document.yaml", None)),
        CozyDocumentProjectLocalBuild.OutputObservation(None),
        force = true
      )

      When("freshness is evaluated")
      val decision = CozyDocumentProjectLocalBuild.evaluate(observation)

      Then("the missing-input decision is returned first")
      decision shouldBe CozyDocumentProjectLocalBuild.FreshnessDecision.MissingRequiredInput("content/en/document.yaml")
    }

    "request a forced build despite current outputs" in {
      Given("current required inputs and output with force enabled")
      val observation = CozyDocumentProjectLocalBuild.FreshnessObservation(
        Vector(CozyDocumentProjectLocalBuild.InputObservation("index.dox", Some(10L))),
        CozyDocumentProjectLocalBuild.OutputObservation(Some(10L)),
        force = true
      )

      When("freshness is evaluated")
      val decision = CozyDocumentProjectLocalBuild.evaluate(observation)

      Then("force takes priority over output reuse")
      decision shouldBe CozyDocumentProjectLocalBuild.FreshnessDecision.ForcedBuild
    }

    "include configuration in freshness dependencies" in {
      Given("an article input, newer configuration, and a current output")
      val observation = CozyDocumentProjectLocalBuild.FreshnessObservation(
        Vector(
          CozyDocumentProjectLocalBuild.InputObservation("config", Some(20L)),
          CozyDocumentProjectLocalBuild.InputObservation("index.dox", Some(10L))
        ),
        CozyDocumentProjectLocalBuild.OutputObservation(Some(10L)),
        force = false
      )

      When("freshness is evaluated")
      val decision = CozyDocumentProjectLocalBuild.evaluate(observation)

      Then("the changed configuration requests a build")
      decision shouldBe CozyDocumentProjectLocalBuild.FreshnessDecision.DependencyNewer("config")
    }

    "reuse article output when only the Document Description changed" in {
      Given("an article target whose direct inputs are unchanged index.dox and config")
      val observation = CozyDocumentProjectLocalBuild.FreshnessObservation(
        Vector(
          CozyDocumentProjectLocalBuild.InputObservation("config", Some(10L)),
          CozyDocumentProjectLocalBuild.InputObservation("index.dox", Some(10L))
        ),
        CozyDocumentProjectLocalBuild.OutputObservation(Some(10L)),
        force = false
      )

      When("article freshness is evaluated after a Document Description-only change")
      val decision = CozyDocumentProjectLocalBuild.evaluate(observation)

      Then("the unchanged article input set reuses its current output")
      decision shouldBe CozyDocumentProjectLocalBuild.FreshnessDecision.ReuseCurrentOutput
    }

    "select document-structure-html when the target selector is omitted" in {
      Given("the frozen local-build target catalog and an omitted selector")
      val selected = CozyDocumentProjectLocalBuild.select(None)

      When("the local-build target is selected")
      val selectedid = selected.id

      Then("the compatibility default is the structure-centered HTML target")
      selectedid shouldBe "document-structure-html"
      selected.outputRoot shouldBe "target/document-project/local-build/document-structure-html/"
    }

    "declare the three exact target contracts" in {
      Given("the package-private immutable local-build catalog")
      val contracts = CozyDocumentProjectLocalBuild.targetContracts

      When("all declared target contracts are read")
      val ids = contracts.map(_.id)

      Then("the catalog contains exactly the frozen IDs and source/output contracts")
      ids shouldBe Vector("document-structure-html", "document-reader-html", "smartdox-article-html")
      contracts.foreach { contract =>
        contract.targetDefinitionPath shouldBe "build/document-project-targets.yaml"
      }
      contracts.take(2).foreach { contract =>
        contract.sourceCategories shouldBe Vector(
          "config",
          "content/core.yaml",
          "content/<locale>/document.yaml",
          "content/<locale>/confirmation-vocabulary.yaml"
        )
        contract.outputRoot shouldBe s"target/document-project/local-build/${contract.id}/"
      }
      contracts.last.sourceCategories shouldBe Vector("config", "index.dox")
      contracts.last.outputRoot shouldBe "target/document-project/local-build/smartdox-article-html/"
    }

    "reject an unknown target without selecting a nearby contract" in {
      Given("the frozen local-build target catalog and an unknown selector")
      val targetid = "unknown-html"

      When("the unknown local-build target is selected")
      val fault = the[CozyDocumentProjectLocalBuild.UnknownTarget] thrownBy {
        CozyDocumentProjectLocalBuild.select(Some(targetid))
      }

      Then("selection fails with the exact unknown target identity")
      fault.targetId shouldBe targetid
      fault.getMessage should include(targetid)
    }
  }
}
