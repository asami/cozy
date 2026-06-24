package cozy.bok.scenario

import cozy.CozySpecVocabulary
import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path}
import scala.collection.JavaConverters._
import org.scalatest.GivenWhenThen
import org.scalatest.OptionValues
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Jun. 24, 2026
 * @version Jun. 24, 2026
 * @author  ASAMI, Tomoharu
 */
class CozyBokScenarioModelSpec
    extends AnyWordSpec
    with GivenWhenThen
    with OptionValues
    with CozySpecVocabulary {
  "Cozy BoK scenario model" should {
    "normalize source documents through SmartDox Dox IR" which {
      "extract simple scenario steps from SmartDox source" in {
        _with_temp_dir("cozy-bok-simple-scenario") { dir =>
          Given("a SmartDox scenario document with HEAD metadata and list steps")
          _write(
            dir.resolve("scenario/concept/simple.dox"),
            """# HEAD
              |
              |title = "Simple onboarding"
              |brief = "A lightweight onboarding scenario."
              |scenario.type = "simple"
              |scenario.id = "SC-SIMPLE"
              |scenario.terms = ["onboarding"]
              |
              |# Scenario
              |
              |## Steps
              |
              |- [start] Reader: open the guide
              |- [finish] Reader: complete the checklist
              |""".stripMargin
          )

          When("Cozy extracts scenario metadata through the SmartDox parser")
          val metadata = ScenarioMetadata.create(dir)

          Then("the simple scenario keeps document metadata and list-derived steps")
          val scenario = _only_scenario(metadata)
          scenario.id shouldBe "SC-SIMPLE"
          scenario.scenarioType shouldBe "simple"
          scenario.title shouldBe "Simple onboarding"
          scenario.summary shouldBe Some("A lightweight onboarding scenario.")
          scenario.category shouldBe Some("concept")
          scenario.sourcePath shouldBe "scenario/concept/simple.dox"
          scenario.publicPath shouldBe "scenario/concept/simple.html"
          scenario.terms shouldBe Vector("onboarding")
          scenario.steps.map(_.id) shouldBe Vector(Some("start"), Some("finish"))
          scenario.steps.map(_.actor) shouldBe Vector(Some("Reader"), Some("Reader"))
          scenario.steps.map(_.action) shouldBe Vector("open the guide", "complete the checklist")
        }
      }

      "extract use case flows and relationships from Markdown source" in {
        _with_temp_dir("cozy-bok-use-case-scenario") { dir =>
          Given("a Markdown use case with YAML front matter and OFAD-style flow sections")
          _write(
            dir.resolve("scenario/concept/reserve-room.md"),
            """---
              |title: Reserve a meeting room
              |brief: Meeting room reservation use case.
              |scenario:
              |  type: use-case
              |  id: UC-ROOM-RESERVE
              |  goal: Reserve a room for a meeting
              |  primary_actor: Employee
              |  secondary_actors:
              |    - Facility Manager
              |  terms:
              |    - reservation
              |  status: published
              |---
              |
              |# UseCase
              |
              |## Reserve a meeting room
              |
              |### MainFlow
              |
              |- Employee: open reservation screen
              |- Employee: extend UC-ROOM-WAITLIST when no room is available
              |
              |### AlternateFlow
              |
              |- Facility Manager: abort reservation
              |
              |### ExceptionFlow
              |
              |- System: abort session timeout
              |""".stripMargin
          )

          When("Cozy interprets the Dox IR as a use case scenario")
          val scenario = _only_scenario(ScenarioMetadata.create(dir))

          Then("the public scenario metadata keeps source, category, and title information")
          scenario.id shouldBe "UC-ROOM-RESERVE"
          scenario.scenarioType shouldBe "use-case"
          scenario.slug shouldBe "reserve-room"
          scenario.sourcePath shouldBe "scenario/concept/reserve-room.md"
          scenario.publicPath shouldBe "scenario/concept/reserve-room.html"
          scenario.category shouldBe Some("concept")

          And("the nested use case model is populated from front matter and flow sections")
          val usecase = scenario.useCase.value
          usecase.useCaseId shouldBe Some("UC-ROOM-RESERVE")
          usecase.name shouldBe "Reserve a meeting room"
          usecase.goal shouldBe Some("Reserve a room for a meeting")
          usecase.primaryActor shouldBe Some("Employee")
          usecase.secondaryActors shouldBe Vector("Facility Manager")
          usecase.status shouldBe Some("published")
          usecase.flows.map(_.flowType) shouldBe Vector("main", "alternate", "exception")
          usecase.flows.head.steps.map(_.directive) should contain (Some("extend"))
          usecase.flows(1).steps.head.id shouldBe None
          usecase.flows(1).steps.head.actor shouldBe Some("Facility Manager")

          And("relationship directives are preserved for later model processing")
          scenario.relationships should containWhere[ScenarioMetadata.ScenarioRelationship] { relationship =>
            relationship.relation == "extend" &&
              relationship.target == "UC-ROOM-WAITLIST" &&
              relationship.condition.contains("no room is available")
          }
        }
      }

      "extract persona journey metadata from Markdown source" in {
        _with_temp_dir("cozy-bok-persona-journey") { dir =>
          Given("a Markdown persona journey with structured front matter")
          _write(
            dir.resolve("scenario/technology/admin-journey.markdown"),
            """---
              |title: Admin publication journey
              |brief: Site administrator publication journey.
              |scenario:
              |  type: persona-journey
              |  id: PJ-ADMIN-PUBLISH
              |  persona: Site Administrator
              |  journey_stages:
              |    - stage
              |    - upload
              |  goals:
              |    - publish safely
              |  pain_points:
              |    - accidental upload
              |  touchpoints:
              |    - cozy bok publish
              |---
              |
              |# Journey
              |
              |Publication journey overview.
              |""".stripMargin
          )

          When("Cozy extracts persona journey scenario metadata")
          val scenario = _only_scenario(ScenarioMetadata.create(dir))

          Then("the persona journey model preserves structured journey fields")
          scenario.id shouldBe "PJ-ADMIN-PUBLISH"
          scenario.scenarioType shouldBe "persona-journey"
          scenario.category shouldBe Some("technology")
          scenario.sourcePath shouldBe "scenario/technology/admin-journey.markdown"
          val journey = scenario.personaJourney.value
          journey.persona shouldBe Some("Site Administrator")
          journey.journeyStages shouldBe Vector("stage", "upload")
          journey.goals shouldBe Vector("publish safely")
          journey.painPoints shouldBe Vector("accidental upload")
          journey.touchpoints shouldBe Vector("cozy bok publish")
        }
      }

      "ignore scenario metadata outside the scenario category directory" in {
        _with_temp_dir("cozy-bok-scenario-location") { dir =>
          Given("a normal article with scenario metadata outside the scenario source tree")
          _write(
            dir.resolve("concept/reserve-room.md"),
            """---
              |title: Reserve a meeting room
              |scenario:
              |  type: use-case
              |  id: UC-IGNORED
              |---
              |
              |# UseCase
              |""".stripMargin
          )
          And("a scenario source under scenario/<category>")
          _write(
            dir.resolve("scenario/concept/project-onboarding.md"),
            """---
              |title: Project onboarding
              |scenario:
              |  type: simple
              |  id: SC-PROJECT-ONBOARDING
              |  category: concept
              |---
              |
              |- Reader: open the onboarding page
              |""".stripMargin
          )

          When("Cozy extracts scenario metadata")
          val metadata = ScenarioMetadata.create(dir)

          Then("only documents under scenario/<category> become scenarios")
          metadata.scenarios.map(_.id) shouldBe Vector("SC-PROJECT-ONBOARDING")
          metadata.scenarios.head.sourcePath shouldBe "scenario/concept/project-onboarding.md"
          metadata.scenarios.head.publicPath shouldBe "scenario/concept/project-onboarding.html"
          metadata.scenarios.head.category shouldBe Some("concept")
        }
      }
    }
  }

  private def _only_scenario(metadata: ScenarioMetadata): ScenarioMetadata.ScenarioEntry = {
    metadata.scenarios should have size 1
    metadata.scenarios.head
  }

  private def _with_temp_dir[A](name: String)(f: Path => A): A = {
    val dir = Files.createTempDirectory(name)
    try {
      f(dir)
    } finally {
      _delete(dir)
    }
  }

  private def _write(path: Path, content: String): Unit = {
    Option(path.getParent).foreach(Files.createDirectories(_))
    Files.write(path, content.getBytes(StandardCharsets.UTF_8))
  }

  private def _delete(path: Path): Unit =
    if (Files.exists(path)) {
      val stream = Files.walk(path)
      try {
        stream.iterator.asScala.toVector.reverse.foreach(Files.deleteIfExists)
      } finally {
        stream.close()
      }
    }
}
