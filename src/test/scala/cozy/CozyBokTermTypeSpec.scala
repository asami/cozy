package cozy

import cozy.bok.CozyBok
import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path}
import scala.collection.JavaConverters._
import org.scalatest.GivenWhenThen
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Jun. 25, 2026
 * @version Jul.  2, 2026
 * @author  ASAMI, Tomoharu
 */
class CozyBokTermTypeSpec
    extends AnyWordSpec
    with GivenWhenThen
    with CozySpecVocabulary {
  "Cozy BoK glossary term types" should {
    "render event, actor, and role terms from SmartDox terms metadata" in {
      _with_temp_dir("cozy-bok-term-types") { dir =>
        Given("a BoK source tree and SmartDox terms.json with event, actor, and role terms")
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
        _write(dir.resolve("src/main/doxsite/technology/category.yaml"), "name: Technology\ntitle: Technology\n")
        _write(dir.resolve("src/main/doxsite/technology/index.dox"), "Technology\n==========\n\n# Overview\nTechnology category.\n")

        When("Cozy builds term hub pages from SmartDox metadata")
        val config = CozyBok.BuildConfig.create(List(dir.toString, "--strategy", "preview"))
        CozyBok.build(config, new TermTypeMetadataRunner)

        Then("the Glossary dashboard exposes term type counts and labels")
        val glossary = _read(dir.resolve("website.d/glossary/index.html"))
        glossary should include("Glossary Workflow")
        glossary should include("Candidate extraction")
        glossary should include("Done / target")
        glossary should include("Term definition")
        glossary should include("Term classification")
        glossary should include("Type group breakdown")
        glossary should include("Mono")
        glossary should include("Koto")
        glossary should include("Term Usage")
        glossary should include("RDF connection")
        glossary should include("CML connection")
        glossary should not include ("2 / 1")
        glossary should include("""class="bok-workflow-detail"""")
        glossary should include("No missing items")
        glossary should include("CML need undecided")
        glossary should include("Article Dashboard")
        glossary should include("Scenario Dashboard")
        glossary should include("Check term types")
        glossary should include("Connect RDF")
        glossary should include("Connect CML")
        glossary should include("entityKind")
        glossary should include("Term Classification")
        glossary should include("RDF Connections")
        glossary should include("RDF-linked terms")
        glossary should include("RDF triples")
        glossary should include("Connection breakdown")
        glossary should include("href=\"../rdf/index.html\"")
        glossary should include("Project Connections")
        glossary should include("CML-linked terms")
        glossary should include("Project-connected terms")
        glossary should include("Project classification")
        glossary should include("Source-only projects")
        glossary should include("href=\"../projects/index.html\"")
        glossary should include("Unclassified Terms")
        glossary should include("Mono Term Types")
        glossary should include("Koto Term Types")
        glossary should include("""class="bok-term-analysis-route-group bok-term-analysis-route-group-unclassified"""")
        glossary should include("""class="bok-term-analysis-route-group bok-term-analysis-route-group-mono"""")
        glossary should include("""class="bok-term-analysis-route-group bok-term-analysis-route-group-koto"""")
        glossary should include("Open classification procedure")
        glossary should include("""href="../manual/index.html#glossary-term-classification"""")
        glossary should include("Checkpoints")
        glossary should include("Missing Analysis")
        glossary should include("Definition, related articles, RDF explanation, CML value/powertype/rule")
        glossary should include("Entity")
        glossary should not include ("Project Terms")
        glossary should not include ("Nested relationships")
        glossary should include("Resource")
        glossary should include("Artifact")
        glossary should include("Action")
        glossary should include("Process")
        glossary should include("Task")
        glossary should include("Scenario")
        glossary should include("State")
        glossary should include("Date/period, actors, roles, scenarios, CML event")
        glossary should include("Organization, roles, participating events, responsibilities, operation target")
        glossary should include("Actors, responsibilities, permissions, event/scenario usage, CML representation")
        glossary should include("Decision condition, target, CML rule, evidence")
        glossary should include("Event")
        glossary should include("Actor")
        glossary should include("Role")
        glossary should include("Architecture Review")
        glossary should include("Knowledge Owner")
        glossary should include("Reviewer")
        glossary should include("""href="technology/architecture-review.html"""")
        glossary should include("""href="technology/knowledge-owner.html"""")
        glossary should include("""href="technology/reviewer.html"""")

        And("the Event term hub shows event-specific metadata")
        val event = _read(dir.resolve("website.d/glossary/technology/architecture-review.html"))
        event should include("Event")
        event should include("Occurred at")
        event should include("2026-06-25")
        event should include("KnowledgeHub")
        event should include("technology:knowledge-owner")
        event should include("knowledge.reviewed")

        And("the Actor and Role term hubs show their type-specific metadata")
        val actor = _read(dir.resolve("website.d/glossary/technology/knowledge-owner.html"))
        actor should include("Actor")
        actor should include("Organization")
        actor should include("KnowledgeHub")
        actor should include("technology:reviewer")
        val role = _read(dir.resolve("website.d/glossary/technology/reviewer.html"))
        role should include("Role")
        role should include("Responsibilities")
        role should include("Review content quality")
        role should include("Approve changes")
      }
    }
  }

  private class TermTypeMetadataRunner extends CozyBok.Runner {
    def run(command: Vector[String], cwd: Path): Unit =
      if (command.take(2) == Vector("dox", "site")) {
        _write(cwd.resolve("doxsite.d/metadata/dashboard/site.json"), _dashboard_json)
        _write(cwd.resolve("doxsite.d/metadata/rdf/graph.json"), """{"nodes": [], "edges": [], "truncated": false}\n""")
        _write(cwd.resolve("doxsite.d/metadata/glossary/terms.json"), _terms_json)
        _write(cwd.resolve("doxsite.d/site.ttl"), "@prefix ex: <https://example.com/> .\n")
        _write(cwd.resolve("doxsite.d/site.jsonld"), "{\"@graph\":[]}\n")
      }
  }

  private def _dashboard_json: String =
    """{
      |  "counts": {"category_count": 1, "article_count": 1, "glossary_term_count": 3, "total_item_count": 4},
      |  "rdf": {"resource_count": 0, "triple_count": 0, "subject_count": 0, "predicate_count": 0},
      |  "increments": {"scale": "day", "buckets": []},
      |  "categories": [{"name": "technology", "title": "Technology", "counts": {"category_count": 0, "article_count": 1, "glossary_term_count": 3, "total_item_count": 4}, "increments": {"scale": "day", "buckets": []}, "rdf": {"resource_count": 0, "triple_count": 0, "subject_count": 0, "predicate_count": 0}}]
      |}
      |""".stripMargin

  private def _terms_json: String =
    """{
      |  "terms": [
      |    {
      |      "id": "technology:architecture-review",
      |      "slug": "architecture-review",
      |      "title": "Architecture Review",
      |      "reading": null,
      |      "category": "technology",
      |      "source_path": "glossary/technology/architecture-review.dox",
      |      "public_path": "glossary/technology/architecture-review.html",
      |      "definition_html": "<p>Architecture review event.</p>",
      |      "summary": "Architecture review event.",
      |      "aliases": [],
      |      "term_type": "event",
      |      "event": {"occurred_at": "2026-06-25", "location": "KnowledgeHub", "actors": ["technology:knowledge-owner"], "roles": ["technology:reviewer"], "participants": ["technology:knowledge-owner"], "scenarios": ["scenario:review"], "evidence": ["bib:design-patterns"], "cml_event": "knowledge.reviewed", "cml_component": "knowledgehub", "cml_statemachine": "KnowledgeLifecycle"},
      |      "article_refs": [], "term_refs": [], "rdf_refs": [], "video_refs": [],
      |      "quality": {"isolated": false, "unreferenced": false, "weakly_connected": false}
      |    },
      |    {
      |      "id": "technology:knowledge-owner",
      |      "slug": "knowledge-owner",
      |      "title": "Knowledge Owner",
      |      "reading": null,
      |      "category": "technology",
      |      "source_path": "glossary/technology/knowledge-owner.dox",
      |      "public_path": "glossary/technology/knowledge-owner.html",
      |      "definition_html": "<p>Owner actor.</p>",
      |      "summary": "Owner actor.",
      |      "aliases": [],
      |      "term_type": "actor",
      |      "actor": {"roles": ["technology:reviewer"], "organization": "KnowledgeHub", "description": "Owns a knowledge source."},
      |      "article_refs": [], "term_refs": [], "rdf_refs": [], "video_refs": [],
      |      "quality": {"isolated": false, "unreferenced": false, "weakly_connected": false}
      |    },
      |    {
      |      "id": "technology:reviewer",
      |      "slug": "reviewer",
      |      "title": "Reviewer",
      |      "reading": null,
      |      "category": "technology",
      |      "source_path": "glossary/technology/reviewer.dox",
      |      "public_path": "glossary/technology/reviewer.html",
      |      "definition_html": "<p>Reviewer role.</p>",
      |      "summary": "Reviewer role.",
      |      "aliases": [],
      |      "term_type": "role",
      |      "role": {"actors": ["technology:knowledge-owner"], "responsibilities": ["Review content quality"], "permissions": ["Approve changes"]},
      |      "article_refs": [], "term_refs": [], "rdf_refs": [], "video_refs": [],
      |      "quality": {"isolated": false, "unreferenced": false, "weakly_connected": false}
      |    }
      |  ]
      |}
      |""".stripMargin

  private def _with_temp_dir[A](prefix: String)(f: Path => A): A = {
    val dir = Files.createTempDirectory(prefix)
    try f(dir) finally _delete(dir)
  }

  private def _write(path: Path, content: String): Path = {
    Option(path.getParent).foreach(Files.createDirectories(_))
    Files.write(path, content.getBytes(StandardCharsets.UTF_8))
    path
  }

  private def _read(path: Path): String =
    new String(Files.readAllBytes(path), StandardCharsets.UTF_8)

  private def _delete(path: Path): Unit =
    if (Files.exists(path)) {
      val stream = Files.walk(path)
      try stream.iterator.asScala.toVector.reverse.foreach(Files.deleteIfExists)
      finally stream.close()
    }
}
