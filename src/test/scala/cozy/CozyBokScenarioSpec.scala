package cozy

import cozy.bok.CozyBok
import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path}
import scala.collection.JavaConverters._
import org.scalatest.GivenWhenThen
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Jun. 24, 2026
 * @version Jun. 24, 2026
 * @author  ASAMI, Tomoharu
 */
class CozyBokScenarioSpec
    extends AnyWordSpec
    with GivenWhenThen
    with CozySpecVocabulary {
  "Cozy BoK scenario rendering" should {
    "consume Cozy-generated scenario metadata" which {
      "render a scenario dashboard and connect Home, Category, and Term Hub navigation" in {
        _with_temp_dir("cozy-bok-scenario") { dir =>
          Given("a BoK source tree that contains scenario source documents")
          _write(
            dir.resolve("src/main/doxsite/site.conf"),
            """site {
              |  output {
              |    locale_mode = "single_locale_root"
              |    default_locale = "en"
              |  }
              |}
              |""".stripMargin
          )
          _write(
            dir.resolve("src/main/doxsite/concept/category.yaml"),
            """name: Concept
              |title: Concept
              |description: Concept category.
              |""".stripMargin
          )
          _write(
            dir.resolve("src/main/doxsite/concept/index.dox"),
            "Concept\n=======\n"
          )
          _write(
            dir.resolve("src/main/doxsite/scenario/concept/reserve-room.md"),
            """---
              |title: Reserve a meeting room
              |brief: Meeting room reservation use case.
              |scenario:
              |  type: use-case
              |  id: UC-ROOM-RESERVE
              |  terms:
              |    - concept:reservation
              |    - Reservation
              |  primary_actor: Employee
              |  goal: Reserve a meeting room
              |status: published
              |---
              |
              |# UseCase
              |
              |## Reserve a meeting room
              |
              |### MainFlow
              |
              |- [open] Employee: open reservation screen
              |- [select] Employee: select a room and time
              |- end
              |""".stripMargin
          )
          _write(
            dir.resolve("src/main/doxsite/glossary/concept/reservation.md"),
            """---
              |title: Reservation
              |brief: Reservation term.
              |---
              |
              |Reservation definition.
              |""".stripMargin
          )
          val config = CozyBok.BuildConfig.create(
            List(dir.toString, "--strategy", "preview")
          )

          When("Cozy builds BoK pages and extracts scenario metadata from Dox IR")
          CozyBok.build(config, new ScenarioMetadataRunner)

          Then("the scenario dashboard is generated as a BoK special page")
          val scenarios = _read(dir.resolve("website.d/scenarios/index.html"))
          scenarios should include("Scenarios")
          scenarios should include("Reserve a meeting room")
          scenarios should include("use-case")
          scenarios should include("UC-ROOM-RESERVE")
          scenarios should include("Meeting room reservation use case.")
          scenarios should include("""href="../scenario/concept/reserve-room.html"""")
          scenarios should include("""data-scenario-category="concept"""")
          scenarios should include("new URLSearchParams(window.location.search).get('category')")
          _read(
            dir.resolve("website.d/metadata/scenarios/scenarios.json")
          ) should include("UC-ROOM-RESERVE")
          _read(
            dir.resolve("doxsite.d/metadata/scenarios/scenarios.json")
          ) should include("UC-ROOM-RESERVE")

          And("the Home dashboard links to scenario knowledge")
          val home = _read(dir.resolve("website.d/index.html"))
          home should include("""href="scenarios/index.html"""")
          home should include("Scenarios")

          And("the Category dashboard links to category scenario knowledge")
          val category = _read(dir.resolve("website.d/concept/index.html"))
          category should include("""href="../scenarios/index.html?category=concept"""")
          category should include("1 category scenarios")

          And("the Term Hub shows scenarios related to the term metadata")
          val term =
            _read(dir.resolve("website.d/glossary/concept/reservation.html"))
          term should include("Related Scenarios")
          term should include("Reserve a meeting room")
          term should include("""href="../../scenario/concept/reserve-room.html"""")
        }
      }

      "render without failing when scenario source documents are absent" in {
        _with_temp_dir("cozy-bok-scenario-absent") { dir =>
          Given("a BoK source tree that does not contain scenario source documents")
          _write(
            dir.resolve("src/main/doxsite/site.conf"),
            """site {
              |  output {
              |    locale_mode = "single_locale_root"
              |    default_locale = "en"
              |  }
              |}
              |""".stripMargin
          )
          _write(
            dir.resolve("src/main/doxsite/concept/category.yaml"),
            """name: Concept
              |title: Concept
              |description: Concept category.
              |""".stripMargin
          )
          val config = CozyBok.BuildConfig.create(
            List(dir.toString, "--strategy", "preview")
          )

          When("Cozy builds BoK pages without scenario source documents")
          CozyBok.build(config, new NoScenarioMetadataRunner)

          Then("the scenario page exists and reports missing metadata without failing the build")
          _read(dir.resolve("website.d/scenarios/index.html")) should include(
            "No scenario metadata yet."
          )
        }
      }
    }
  }

  private class ScenarioMetadataRunner extends CozyBok.Runner {
    def run(command: Vector[String], cwd: Path): Unit =
      if (command.take(2) == Vector("dox", "site")) {
        _write(cwd.resolve("doxsite.d/metadata/dashboard/site.json"), _dashboard_json)
        _write(cwd.resolve("doxsite.d/metadata/rdf/graph.json"), _rdf_graph_json)
        _write(cwd.resolve("doxsite.d/metadata/glossary/terms.json"), _terms_json)
        _write(cwd.resolve("doxsite.d/site.ttl"), "@prefix ex: <https://example.com/> .\n")
        _write(cwd.resolve("doxsite.d/site.jsonld"), "{\"@graph\":[]}\n")
      }
  }

  private class NoScenarioMetadataRunner extends ScenarioMetadataRunner {
    override def run(command: Vector[String], cwd: Path): Unit =
      if (command.take(2) == Vector("dox", "site")) {
        _write(cwd.resolve("doxsite.d/metadata/dashboard/site.json"), _dashboard_json)
        _write(cwd.resolve("doxsite.d/metadata/rdf/graph.json"), _rdf_graph_json)
        _write(cwd.resolve("doxsite.d/metadata/glossary/terms.json"), _terms_json)
        _write(cwd.resolve("doxsite.d/site.ttl"), "@prefix ex: <https://example.com/> .\n")
        _write(cwd.resolve("doxsite.d/site.jsonld"), "{\"@graph\":[]}\n")
      }
  }

  private def _dashboard_json: String =
    """{
      |  "counts": {
      |    "category_count": 1,
      |    "article_count": 1,
      |    "glossary_term_count": 1,
      |    "total_item_count": 2
      |  },
      |  "rdf": {
      |    "resource_count": 2,
      |    "triple_count": 3,
      |    "subject_count": 2,
      |    "predicate_count": 2
      |  },
      |  "increments": {
      |    "scale": "day",
      |    "buckets": []
      |  },
      |  "categories": [{
      |    "name": "concept",
      |    "title": "Concept",
      |    "counts": {
      |      "category_count": 0,
      |      "article_count": 1,
      |      "glossary_term_count": 1,
      |      "total_item_count": 2
      |    },
      |    "increments": {
      |      "scale": "day",
      |      "buckets": []
      |    },
      |    "rdf": {
      |      "resource_count": 2,
      |      "triple_count": 3,
      |      "subject_count": 2,
      |      "predicate_count": 2
      |    }
      |  }]
      |}
      |""".stripMargin

  private def _rdf_graph_json: String =
    """{
      |  "nodes": [],
      |  "edges": [],
      |  "truncated": false
      |}
      |""".stripMargin

  private def _terms_json: String =
    """{
      |  "terms": [{
      |    "id": "concept:reservation",
      |    "slug": "reservation",
      |    "title": "Reservation",
      |    "reading": null,
      |    "category": "concept",
      |    "source_path": "glossary/concept/reservation.md",
      |    "public_path": "glossary/concept/reservation.html",
      |    "definition_html": "<p>Reservation definition.</p>",
      |    "summary": "Reservation term.",
      |    "aliases": [],
      |    "article_refs": [],
      |    "term_refs": [],
      |    "rdf_refs": [],
      |    "video_refs": [],
      |    "quality": {
      |      "isolated": false,
      |      "unreferenced": false,
      |      "weakly_connected": true
      |    }
      |  }]
      |}
      |""".stripMargin

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

  private def _read(path: Path): String =
    new String(Files.readAllBytes(path), StandardCharsets.UTF_8)

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
