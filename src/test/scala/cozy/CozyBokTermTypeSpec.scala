package cozy

import cozy.bok.CozyBok
import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path}
import scala.collection.JavaConverters._
import org.scalatest.GivenWhenThen
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Jun. 25, 2026
 * @version Jul. 23, 2026
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
        _write(cwd.resolve("doxsite.d/metadata/rdf/graph.json"), """{"nodes": [], "edges": [], "truncated": false}""" + "\n")
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

package bok {
  class CozyBokLexidoxProjectionSpec extends AnyWordSpec with GivenWhenThen with CozySpecVocabulary {
    "LexiDox term projection" should {
      "retain pages with a shared Term IRI and classify independently of knowledge form" in {
        val dir = Files.createTempDirectory("cozy-lexidox-projection")
        try {
          Given("producer pages and fragment routes for two Spaces and a process Pattern")
          val config = CozyBok.BuildConfig.create(List(dir.toString, "--strategy", "preview"))
          def _write_(path: Path, value: String): Unit = {
            Files.createDirectories(path.getParent)
            Files.write(path, value.getBytes(StandardCharsets.UTF_8))
          }
          def _page_(space: String, term: String, concept: String, title: String, extra: String): String =
            s"""{"space":"$space","space_iri":"https://example.org/space/$space","term_iri":"https://example.org/term/$term","concept_iri":"https://example.org/concept/$concept","page_iri":"https://example.org/page/$space/$term","source_document_iri":"https://example.org/source/$space","titles":[{"text":"$title"}],"aliases":[],"sections":[{"name":"DEFINITION","content":{"text":"Definition"},"span":{"source":"spaces/$space.dox"}}]$extra}"""
          val pages = Vector(
            _page_("sales", "work", "sales-work", "作品", ""),
            _page_("history", "work", "literary-work", "作品", ""),
            _page_("retry", "retry", "retry", "Retry", ",\"concept_type\":\"process\",\"knowledge_form\":\"pattern\"")
          )
          val envelope = s"""{"schema":"lexidox-knowledge/1","pages":[${pages.mkString(",")}],"references":[{"label":"作品","span":{"source":"spaces/retry.dox"},"resolution":{"status":"resolved","page_iri":"https://example.org/page/history/work"}}]}"""
          val handoff = config.doxsitePath.resolve("metadata/lexidox")
          _write_(handoff.resolve("knowledge-v1.json"), envelope)
          _write_(handoff.resolve("knowledge-v1.ttl"), "# producer graph")
          _write_(handoff.resolve("knowledge-v1.jsonld"), "{}")
          val fragments = Vector("sales", "history", "retry").map { space =>
            s"""{"source_path":"spaces/$space.dox","public_path":"spaces/$space.html","locale":"${config.defaultLocale}","body_html":""}"""
          }
          _write_(config.doxsitePath.resolve("metadata/documents/fragments.json"),
            s"""{"fragments":[${fragments.mkString(",")}]}""")

          val legacy = """{"id":"old-sales","slug":"old-sales","title":"作品","source_path":"spaces/sales.dox","public_path":"glossary/sales.html","definition_html":"legacy","reading":"さくひん","category":"sales","aliases":["旧販売"],"cml":{"component":"Sales"},"tags":["catalog"],"article_refs":[{"title":"Article","path":"article.html"}],"video_refs":[{"title":"Video","path":"video.html"}],"quality":{"isolated":true}}"""
          val legacyindex = config.doxsitePath.resolve("metadata/glossary/terms.json")
          _write_(legacyindex, s"""{"terms":[$legacy]}""")

          When("Cozy reads the existing producer handoffs")
          val terms = CozyBokImplementation._terms(config)

          Then("each page keeps its own Space, Concept and published route")
          terms.map(_.pageiri).distinct.size shouldBe 3
          terms.filter(_.title == "作品").map(_.termiri).distinct.size shouldBe 1
          terms.filter(_.title == "作品").map(_.conceptiri).distinct.size shouldBe 2
          terms.filter(_.title == "作品").map(_.space).flatten.toSet shouldBe Set("sales", "history")
          terms.map(_.publicpath).toSet shouldBe Set("spaces/sales.html", "spaces/history.html", "spaces/retry.html")
          val sales = terms.find(_.space.contains("sales")).get
          sales.reading shouldBe Some("さくひん")
          sales.category shouldBe Some("sales")
          sales.cmlLinks.map(x => (x.kind, x.value)) should contain (("component", "Sales"))
          sales.tags shouldBe Vector("catalog")
          sales.articlerefs.map(_.path) shouldBe Vector("article.html")
          sales.videorefs.map(_.path) shouldBe Vector("video.html")
          sales.quality.isolated shouldBe true
          sales.aliases should contain ("旧販売")
          sales.termtype shouldBe ""
          sales.knowledgeform shouldBe None
          val history = terms.find(_.space.contains("history")).get
          val candidates = Vector(sales, history)
          CozyBokImplementation._term_reference_candidates("old-sales", candidates) shouldBe Vector(sales)
          CozyBokImplementation._term_reference_candidates("旧販売", candidates) shouldBe Vector(sales)
          CozyBokImplementation._term_reference_candidates("作品", candidates) shouldBe empty
          CozyBokImplementation._term_reference_candidates("https://example.org/term/work", candidates) shouldBe empty
          CozyBokImplementation._term_reference_candidates(sales.pageiri.get, candidates) shouldBe Vector(sales)
          val scenario = CozyBokModel.ScenarioEntry("s", "s", "scenario", "Scenario", None, None, "s.dox", "s.html", Vector("作品"), Vector.empty, None, CozyBokModel.TermExtraction.empty)
          CozyBokImplementation._scenario_related_to_term(scenario, sales, candidates) shouldBe false
          CozyBokImplementation._scenario_related_to_term(scenario.copy(terms = Vector(sales.pageiri.get)), sales, candidates) shouldBe true
          CozyBokImplementation._scenario_related_to_term(scenario.copy(terms = Vector(sales.conceptiri.get)), sales, candidates) shouldBe true
          val legacyterm = sales.copy(pageiri = None, id = "legacy", title = "Legacy", slug = "legacy")
          CozyBokImplementation._scenario_related_to_term(scenario.copy(terms = Vector("Legacy")), legacyterm, Vector(legacyterm)) shouldBe true
          _write_(legacyindex, s"""{"terms":[$legacy,$legacy]}""")
          val ambiguous = CozyBokImplementation._terms(config)
          ambiguous.find(_.space.contains("sales")).get.cmlLinks shouldBe empty
          ambiguous.count(_.pageiri.isEmpty) shouldBe 2
          _write_(legacyindex, s"""{"terms":[$legacy]}""")
          val shared = history.copy(conceptiri = sales.conceptiri)
          CozyBokImplementation._term_reference_candidates(sales.conceptiri.get, Vector(sales, shared)) shouldBe Vector(sales, shared)
          val retry = terms.find(_.title == "Retry").get
          retry.termtype shouldBe "process"
          retry.knowledgeform shouldBe Some("pattern")
          retry.termrefs.map(_.path) shouldBe Vector("spaces/history.html")
          CozyBokImplementation._term_reference_matches("作品", terms.head) shouldBe false
          CozyBokImplementation._term_reference_matches("https://example.org/concept/sales-work", terms.head) shouldBe true
          val index = CozyBokImplementation._localized_glossary_index_page(config, Vector.empty, "ja")
          index should include ("[sales]")
          index should include ("[history]")
          index should include ("../../spaces/sales.html")
          index should include ("../../spaces/history.html")
          val rdf = CozyBokImplementation._rdf_dedicated_page(config, Vector.empty, "ja", dir.resolve("rdf/index.html"))
          rdf should include ("metadata/lexidox/knowledge-v1.ttl")
          rdf should include ("metadata/lexidox/knowledge-v1.jsonld")
          rdf should include ("https://example.org/concept/literary-work")
          val output = dir.resolve("output")
          _write_(output.resolve("spaces/sales.html"), "producer page")
          CozyBokImplementation._write_term_hub_pages(config, output, "ja", Vector.empty, terms)
          new String(Files.readAllBytes(output.resolve("spaces/sales.html")), StandardCharsets.UTF_8) shouldBe "producer page"

          When("a fragment route is absent or unsafe")
          _write_(config.doxsitePath.resolve("metadata/documents/fragments.json"),
            s"""{"fragments":[{"source_path":"spaces/sales.dox","public_path":"../outside.html","locale":"${config.defaultLocale}","body_html":""}]}""")
          val fallback = CozyBokImplementation._terms(config)
          Then("the declared page IRIs remain usable without fabricating a local route")
          fallback.forall(term => term.pageiri.contains(term.publicpath)) shouldBe true
          val fallbackterm = fallback.find(_.space.contains("sales")).get
          fallbackterm.termHubHrefFromCategory shouldBe fallbackterm.publicpath
          CozyBokImplementation._term_href(dir.resolve("project/index.html"), dir, fallbackterm) shouldBe fallbackterm.publicpath
          CozyBokImplementation._term_href(dir.resolve("project/index.html"), dir, sales) shouldBe "../spaces/sales.html"
          val fallbackindex = CozyBokImplementation._localized_glossary_index_page(config, Vector.empty, "ja")
          fallbackindex should include ("href=\"https://example.org/page/sales/work\"")
          fallbackindex should not include ("../../https://example.org/page/sales/work")

          When("the handoff is partial or contains a symbolic-link file")
          Files.delete(handoff.resolve("knowledge-v1.ttl"))
          intercept[Throwable] { CozyBokImplementation._terms(config) }.getMessage should include ("Incomplete LexiDox")
          _write_(handoff.resolve("knowledge-v1.ttl"), "# producer graph")
          Files.delete(handoff.resolve("knowledge-v1.jsonld"))
          Files.createSymbolicLink(handoff.resolve("knowledge-v1.jsonld"), handoff.resolve("knowledge-v1.ttl"))
          intercept[Throwable] { CozyBokImplementation._terms(config) }.getMessage should include ("regular file")
          Files.delete(handoff.resolve("knowledge-v1.jsonld"))
          _write_(handoff.resolve("knowledge-v1.jsonld"), "{}")
          val moved = config.doxsitePath.resolve("metadata/lexidox-real")
          Files.move(handoff, moved)
          Files.createSymbolicLink(handoff, moved)
          intercept[Throwable] { CozyBokImplementation._terms(config) }.getMessage should include ("unsafe")
          Files.delete(handoff)
          Files.move(moved, handoff)

          When("a producer identity is not an absolute HTTP(S) IRI")
          _write_(handoff.resolve("knowledge-v1.json"), envelope.replace("https://example.org/page/sales/work", "javascript:alert(1)"))
          Then("Cozy rejects it before placing it in a link")
          intercept[Throwable] { CozyBokImplementation._terms(config) }.getMessage should include ("Invalid absolute HTTP(S) LexiDox IRI page_iri")
        } finally {
          val stream = Files.walk(dir)
          try stream.iterator.asScala.toVector.reverse.foreach(Files.deleteIfExists)
          finally stream.close()
        }
      }
    }
  }
}
