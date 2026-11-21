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
      "consume the delivered producer presentation through the normal catalog" in {
        val dir = Files.createTempDirectory("cozy-lexidox-producer")
        try {
          Given("the unchanged SmartDox producer JSON and the required handoff files")
          val config = CozyBok.BuildConfig.create(List(dir.toString, "--strategy", "preview"))
          val handoff = config.doxsitePath.resolve("metadata/lexidox")
          Files.createDirectories(handoff)
          val input = getClass.getResourceAsStream("/lexidox/phase93-producer-knowledge-v1.json")
          try Files.copy(input, handoff.resolve("knowledge-v1.json")) finally input.close()
          Files.write(handoff.resolve("knowledge-v1.ttl"), "# staging only".getBytes(StandardCharsets.UTF_8))
          Files.write(handoff.resolve("knowledge-v1.jsonld"), "{}".getBytes(StandardCharsets.UTF_8))
          val fragments = config.doxsitePath.resolve("metadata/documents/fragments.json")
          Files.createDirectories(fragments.getParent)
          Files.write(fragments, s"""{"fragments":[{"source_path":"presentation/retry.dox","public_path":"presentation/retry.html","locale":"${config.defaultLocale}","body_html":""}]}""".getBytes(StandardCharsets.UTF_8))

          When("the catalog resolves the producer page before selecting labels")
          val catalog = CozyBokImplementation._presentation_catalog(config).get
          val binding = cozy.document.CozyDocumentPatternCodec.TermBinding("retry", "claim", "software-architecture",
            "https://example.org/term/retry", "https://example.org/concept/retry", "https://example.org/page/retry", "named-pattern", Some("bold"), None)
          val occurrence = cozy.document.CozyDocumentDescriptionV2.InlineTerm(binding, "the retry pattern")
          val resolved = Vector("original", "established-japanese", "japanese").map(profile =>
            cozy.document.CozyGlossaryPresentation.resolve(occurrence, catalog, "ja", Some(profile)).toOption.get)

          Then("all profiles preserve the producer identity and projected link")
          resolved.map(_.selection.get.label) shouldBe Vector("Retry", "リトライ", "再試行")
          resolved.map(_.identity).distinct shouldBe Vector(cozy.document.CozyGlossaryPresentation.PageIdentity(
            "software-architecture", "https://example.org/term/retry", "https://example.org/concept/retry", "https://example.org/page/retry"))
          resolved.map(_.link).distinct shouldBe Vector("presentation/retry.html")
          catalog.references.head.visibleText shouldBe Some("the retry pattern")
          catalog.references.head.issue shouldBe None
          catalog.diagnostics shouldBe empty
          catalog.presentationDiagnostics.map(_.code) shouldBe Vector("LEX_PRESENTATION_DUPLICATE")
          catalog.presentationDiagnostics.head.related should have size 1
          catalog.pages.find(_.identity.page == "https://example.org/page/backoff").get.presentation shouldBe None
          catalog.references(1).visibleText shouldBe None
        } finally {
          val stream = Files.walk(dir)
          try stream.iterator.asScala.toVector.reverse.foreach(Files.deleteIfExists)
          finally stream.close()
        }
      }

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
          val span = """{"source":"spaces/retry.dox","start":{"line":2,"column":1},"end":{"line":2,"column":8}}"""
          val original = s"""{"collection":"titles","language":"en","text":"Retry","span":$span}"""
          val canonical = s"""{"collection":"titles","language":"ja","text":"再試行","span":$span}"""
          val established = s"""{"collection":"aliases","language":"ja","text":"リトライ","span":$span}"""
          val extension = s""", "presentation":{"version":1,"role":"named-pattern","original":$original,"canonical":[$canonical],"established_japanese":$established,"span":$span}"""
          val pages = Vector(
            _page_("sales", "work", "sales-work", "作品", ""),
            _page_("history", "work", "literary-work", "作品", ""),
            _page_("retry", "retry", "retry", "Retry", ",\"concept_type\":\"process\",\"knowledge_form\":\"pattern\"" + extension)
              .replace("""{"text":"Retry"}""", """{"text":"Retry","language":"en"},{"text":"再試行","language":"ja"}""")
              .replace(""""aliases":[]""", """"aliases":[{"text":"リトライ","language":"ja"}]""")
          )
          val envelope = s"""{"schema":"lexidox-knowledge/1","pages":[${pages.mkString(",")}],"references":[{"label":"作品","span":{"source":"spaces/retry.dox"},"resolution":{"status":"resolved","page_iri":"https://example.org/page/history/work"}},{"label":"lookup key","visible_text":"Retry visible","span":{"source":"spaces/sales.dox"},"resolution":{"status":"resolved","space":"retry","term_iri":"https://example.org/term/retry","concept_iri":"https://example.org/concept/retry","page_iri":"https://example.org/page/retry/retry"}},{"label":"missing","span":$span,"resolution":{"status":"failed","diagnostic_indexes":[0]}}],"diagnostics":[{"code":"LEX_UNDEFINED","severity":"error","message":"undefined","span":$span,"related":[],"candidates":[]}],"presentation_diagnostics":[{"code":"LEX_PRESENTATION_LABEL","severity":"error","message":"invalid","span":$span,"related":[],"candidates":[]}]}"""
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
          And("the normal reader admits presentation without changing the producer identities or page route")
          val catalog = CozyBokImplementation._presentation_catalog(config).get
          catalog.references(1).visibleText shouldBe Some("Retry visible")
          catalog.references(2).raw.hcursor.downField("resolution").get[Vector[Int]]("diagnostic_indexes").toOption shouldBe Some(Vector(0))
          catalog.diagnostics.map(_.code) shouldBe Vector("LEX_UNDEFINED")
          catalog.presentationDiagnostics.map(_.code) shouldBe Vector("LEX_PRESENTATION_LABEL")
          sales.termrefs.find(_.path == "spaces/retry.html").map(_.title) shouldBe Some("Retry visible")
          val binding = cozy.document.CozyDocumentPatternCodec.TermBinding("retry", "claim", "retry",
            "https://example.org/term/retry", "https://example.org/concept/retry", "https://example.org/page/retry/retry", "named-pattern", Some("bold"), None)
          val occurrence = cozy.document.CozyDocumentDescriptionV2.InlineTerm(binding, "Retry")
          val resolved = Vector("original", "established-japanese", "japanese").map(profile =>
            cozy.document.CozyGlossaryPresentation.resolve(occurrence, catalog, "ja", Some(profile)).toOption.get)
          resolved.map(_.selection.get.label) shouldBe Vector("Retry", "リトライ", "再試行")
          resolved.map(_.identity).distinct should have size 1
          resolved.map(_.link).distinct shouldBe Vector("spaces/retry.html")
          resolved.head.page.presentation.get.original.span.get.source shouldBe "spaces/retry.dox"
          cozy.document.CozyGlossaryPresentation.resolve(occurrence.copy(binding = binding.copy(concept = "https://example.org/concept/other")), catalog, "ja", Some("original")).left.toOption.get.code shouldBe "LEX_PRESENTATION_IDENTITY_MISMATCH"
          cozy.document.CozyGlossaryPresentation.resolve(occurrence, catalog.copy(pages = catalog.pages ++ catalog.pages.filter(_.identity.page == binding.page)), "ja", Some("original")).left.toOption.get.code shouldBe "LEX_PRESENTATION_AMBIGUOUS"

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

package bok {
  /** Explicit native acceptance driver. It is never invoked by the ordinary test suite. */
  object CozyBokLexidoxNativeDriver extends org.scalatest.matchers.should.Matchers {
    private val _image = "ghcr.io/asami/textus-toolchain:latest"
    private val _metadata = Vector("json", "ttl", "jsonld").map(x => s"metadata/lexidox/knowledge-v1.$x")

    def main(args: Array[String]): Unit = {
      require(args.length == 4, "Expected fresh workspace, producer fixture directory, published producer jar, dependencies classpath file")
      val workspace = java.nio.file.Paths.get(args(0)).toAbsolutePath.normalize()
      val fixture = java.nio.file.Paths.get(args(1)).toRealPath()
      val jar = java.nio.file.Paths.get(args(2)).toRealPath()
      val classpathfile = java.nio.file.Paths.get(args(3)).toRealPath()
      require(!Files.exists(workspace) && workspace.getParent.toRealPath() == workspace.getParent,
        "Workspace must be fresh with an existing canonical parent")
      require(Files.isDirectory(fixture) && Files.isRegularFile(jar) && Files.isRegularFile(classpathfile))
      val dependencies = _read(classpathfile).trim
      require(dependencies.nonEmpty && dependencies.split(java.io.File.pathSeparator).forall(x => java.nio.file.Paths.get(x).isAbsolute && Files.exists(java.nio.file.Paths.get(x))))
      val javaexec = java.nio.file.Paths.get(System.getProperty("java.home"), "bin", "java").toString
      val producerdependencies = dependencies.split(java.io.File.pathSeparator).filterNot(
        _.replace('\\', '/').endsWith("/org.smartdox/smartdox_2.12/2.4.19-SNAPSHOT/jars/smartdox_2.12.jar")
      )
      val classpath = (jar.toString +: producerdependencies.toVector).mkString(java.io.File.pathSeparator)
      Files.createDirectory(workspace)
      val project = Files.createDirectory(workspace.resolve("project"))
      val source = project.resolve("src/main/doxsite")
      _copy(fixture, source)
      _publish(source)
      _authored_controls(source)
      Files.write(source.resolve("site.conf"), ("\nsite.output.locale_mode = \"multi_locale_subdirs\"\n" +
        "site.metadata.in_language = [\"ja\", \"en\"]\nsite.output.default_locale = \"ja\"\n" +
        "site.output.scope.policy = \"all\"\n").getBytes(StandardCharsets.UTF_8),
        java.nio.file.StandardOpenOption.APPEND)
      Files.createDirectories(project.resolve(".cozy"))
      Files.write(project.resolve(".cozy/config.yaml"), "bok:\n  output:\n    scope:\n      policy: all\n".getBytes(StandardCharsets.UTF_8))
      val config = CozyBok.BuildConfig.create(List(project.toString, "--strategy", "preview", "--no-bib-service", "--docker-image", _image))
      var commands = Vector.empty[Vector[String]]
      val runner = new CozyBok.Runner {
        override def run(command: Vector[String], cwd: Path): Unit = run(command, cwd, Map.empty)
        override def run(command: Vector[String], cwd: Path, env: Map[String, String]): Unit = {
          require(cwd == project)
          commands :+= command
          val actual = command.head match {
            case "dox" => Vector(javaexec, "-cp", classpath, "org.smartdox.service.SmartDoxService") ++ command.tail
            case "docker" =>
              require(command.take(3) == Vector("docker", "run", "--rm") && command.contains(_image))
              val workdir = command(command.indexOf("-w") + 1).stripPrefix("/workspace/")
              val docs = project.resolve(workdir).resolve("docs")
              require(docs.toRealPath().startsWith(workspace.toRealPath()))
              val git = org.eclipse.jgit.api.Git.init().setDirectory(docs.toFile).call()
              try {
                git.add().addFilepattern(".").call()
                git.commit().setMessage("LexiDox acceptance fixture").setAuthor("Fixture", "fixture@example.invalid")
                  .setCommitter("Fixture", "fixture@example.invalid").call()
              } finally git.close()
              command.take(2) ++ Vector("--pull=never", "--network=none") ++ command.drop(2)
            case other => sys.error(s"Unexpected build command: $other")
          }
          val builder = new ProcessBuilder(actual: _*).directory(cwd.toFile).inheritIO()
          env.foreach { case (key, value) => builder.environment().put(key, value) }
          val process = builder.start()
          require(process.waitFor() == 0, s"Native child failed: ${actual.mkString(" ")}")
        }
      }
      CozyBok.build(config, runner)
      commands.count(_.take(2) == Vector("dox", "site")) shouldBe 1
      commands.count(_.head == "docker") shouldBe 2
      _metadata.foreach { relative =>
        Files.readAllBytes(config.websitePath.resolve(relative)).toVector shouldBe Files.readAllBytes(config.doxsitePath.resolve(relative)).toVector
      }
      val wire = play.api.libs.json.Json.parse(_read(config.doxsitePath.resolve(_metadata.head)))
      (wire \ "schema").as[String] shouldBe "lexidox-knowledge/1"
      val spaces = (wire \ "spaces").as[Vector[play.api.libs.json.JsValue]]
      spaces.map(x => (x \ "id").as[String] -> (x \ "iri").as[String]).toMap shouldBe Map(
        "sales" -> "https://example.org/space/sales", "history" -> "https://example.org/space/history")
      val pages = (wire \ "pages").as[Vector[play.api.libs.json.JsValue]]
      val work = pages.filter(x => (x \ "term_iri").as[String] == "https://example.org/term/work")
      work.map(x => (x \ "page_iri").as[String]).toSet shouldBe Set("https://example.org/page/sales/work", "https://example.org/page/history/work")
      work.map(x => (x \ "concept_iri").as[String]).distinct.size shouldBe 2
      val retry = pages.find(x => (x \ "page_iri").as[String] == "https://example.org/page/sales/retry").get
      (retry \ "concept_type").as[String] shouldBe "process"
      (retry \ "knowledge_form").as[String] shouldBe "pattern"
      (retry \ "sections").as[Vector[play.api.libs.json.JsValue]].map(x => (x \ "original_heading").as[String]) shouldBe
        Vector("DEFINITION", "CONTEXT", "PROBLEM", "SOLUTION", "SOURCE", "REFERENCES", "SimpleModeling", "Implementation Notes")
      val patternhtml = _read(config.doxsitePath.resolve("en/spaces/pattern.html"))
      patternhtml should include ("https://example.org/backoff")
      patternhtml should include ("Repeat a failed operation")
      val legacyhtml = _read(config.doxsitePath.resolve("en/spaces/legacy.html"))
      legacyhtml should include ("Existing content.")
      legacyhtml should include ("the concept")
      val referencehtml = _read(config.doxsitePath.resolve("ja/spaces/reference.html"))
      referencehtml should include ("https://example.org/page/sales/work")
      referencehtml should include ("https://example.org/page/history/work")
      val refs = (wire \ "references").as[Vector[play.api.libs.json.JsValue]]
      refs.map(x => (x \ "resolution" \ "status").as[String]) shouldBe Vector("resolved", "resolved", "failed")
      refs.take(2).map(x => (x \ "resolution" \ "page_iri").as[String]) shouldBe Vector("https://example.org/page/sales/work", "https://example.org/page/history/work")
      val diagnostics = (wire \ "diagnostics").as[Vector[play.api.libs.json.JsValue]]
      val undefined = diagnostics.find(x => (x \ "code").as[String] == "LEX_REFERENCE_UNDEFINED").get
      (undefined \ "value").as[String] shouldBe "未知"
      (undefined \ "span" \ "source").as[String] shouldBe "spaces/reference.dox"
      (undefined \ "span" \ "start" \ "line").as[Int] shouldBe 14
      val terms = CozyBokImplementation._terms(config)
      terms.filter(_.title == "作品").map(_.space).flatten.toSet shouldBe Set("sales", "history")
      terms.find(_.title == "Retry").get.termtype shouldBe "process"
      val glossary = play.api.libs.json.Json.parse(_read(config.doxsitePath.resolve("metadata/glossary/terms.json")))
      val legacyterms = (glossary \ "terms").as[Vector[play.api.libs.json.JsValue]]
      val legacyterm = legacyterms.find(x => (x \ "title").as[String] == "Legacy Process").get
      (legacyterm \ "term_type").as[String] shouldBe "concept"
      (legacyterm \ "definition_html").as[String] should include ("A legacy process.")
      (legacyterm \ "definition_html").as[String] should include ("Legacy relationships.")
      terms.find(_.title == "Legacy Process").get.termtype shouldBe "concept"
      val legacycontrol = _read(config.doxsitePath.resolve("en/glossary/technology/legacy-process.html"))
      legacycontrol should include ("A legacy process.")
      legacycontrol should include ("Legacy relationships.")
      val index = _read(config.websitePath.resolve("ja/glossary/index.html"))
      index should include ("作品")
      index should include ("[sales]")
      index should include ("[history]")
      index should include ("Legacy Process")
      Vector("ja", "en").foreach { locale =>
        Vector("sales", "history", "pattern").foreach { name =>
          Files.isRegularFile(config.websitePath.resolve(s"$locale/spaces/$name.html")) shouldBe true
        }
      }
      Vector("sales", "history").foreach { space =>
        index should include (s"""<a href="../spaces/$space.html">作品</a> <span class="bok-term-space">[$space]</span>""")
      }
      val processgroup = index.split("<section class=\"bok-term-type-issue-group bok-term-type-issue-group-process\">", 2)
      processgroup.length shouldBe 2
      processgroup(1).split("</section>", 2).head should include ("<a href=\"../spaces/pattern.html\">Retry</a>")
      val manifest = play.api.libs.json.Json.parse(_read(config.websitePath.resolve("metadata/cncf/knowledge-source.json")))
      val resources = (manifest \ "resources").as[Vector[play.api.libs.json.JsValue]].map { resource =>
        ((resource \ "kind").as[String], (resource \ "href").as[String], (resource \ "mediaType").as[String])
      }
      resources.filter(_._1.startsWith("lexidox-")) shouldBe Vector(
        ("lexidox-knowledge", "metadata/lexidox/knowledge-v1.json", "application/json"),
        ("lexidox-rdf-turtle", "metadata/lexidox/knowledge-v1.ttl", "text/turtle"),
        ("lexidox-rdf-jsonld", "metadata/lexidox/knowledge-v1.jsonld", "application/ld+json")
      )
      val turtle = _read(config.websitePath.resolve(_metadata(1)))
      val graph = play.api.libs.json.Json.parse(_read(config.websitePath.resolve(_metadata(2)))).as[Vector[play.api.libs.json.JsValue]]
      val ontology = "https://www.simplemodeling.org/lexidox/ontology/1#"
      Vector(
        ("https://example.org/page/sales/work", ontology + "inSpace", "https://example.org/space/sales"),
        ("https://example.org/page/history/work", ontology + "concept", "https://example.org/concept/literary-work"),
        ("https://example.org/page/sales/retry", "http://www.w3.org/ns/prov#wasDerivedFrom", "https://example.org/source/pattern")
      ).foreach { case (subject, predicate, obj) =>
        turtle should include (s"<$subject> <$predicate> <$obj> .")
        graph.exists { node =>
          (node \ "@id").asOpt[String].contains(subject) &&
            (node \ predicate).asOpt[Vector[play.api.libs.json.JsValue]].getOrElse(Vector.empty).exists(x => (x \ "@id").asOpt[String].contains(obj))
        } shouldBe true
      }
      val rdfpage = _read(config.websitePath.resolve("ja/rdf/index.html"))
      _metadata.foreach(path => rdfpage should include (path))
      config.siteOutputScopePolicy shouldBe "all"
      _variants(workspace, fixture, javaexec, classpath)
      println(s"LexiDox native acceptance evidence: $workspace; producer=$jar; commands=${commands.size}")
    }

    private def _variants(workspace: Path, fixture: Path, javaexec: String, classpath: String): Unit = {
      def _run_(name: String)(edit: Path => Unit): play.api.libs.json.JsValue = {
        val project = Files.createDirectory(workspace.resolve(name))
        val source = project.resolve("src/main/doxsite")
        _copy(fixture, source)
        _publish(source)
        edit(source)
        val command = Vector(javaexec, "-cp", classpath, "org.smartdox.service.SmartDoxService",
          "site", "-strategy", "production-preview", "-output.scope.policy", "all", "src/main/doxsite")
        val process = new ProcessBuilder(command: _*).directory(project.toFile).inheritIO().start()
        require(process.waitFor() == 0, s"Producer variant failed: $name")
        play.api.libs.json.Json.parse(_read(project.resolve("doxsite.d/metadata/lexidox/knowledge-v1.json")))
      }
      def _change_(source: Path, relative: String)(f: String => String): Unit = {
        val path = source.resolve(relative)
        Files.write(path, f(_read(path)).getBytes(StandardCharsets.UTF_8))
      }
      val legacyalias = _run_("v1-legacy-alias") { source =>
        _change_(source, "spaces/pattern.dox") { text =>
          text.replace("concept_type = \"process\"\n  knowledge_form = \"pattern\"", "term_type = \"process\"")
            .split("# CONTEXT", 2).head
        }
      }
      val aliaspage = (legacyalias \ "pages").as[Vector[play.api.libs.json.JsValue]]
        .find(x => (x \ "page_iri").as[String] == "https://example.org/page/sales/retry").get
      (aliaspage \ "concept_type").as[String] shouldBe "process"
      (aliaspage \ "knowledge_form").asOpt[String] shouldBe None
      (aliaspage \ "sections").as[Vector[play.api.libs.json.JsValue]].map(x => (x \ "original_heading").as[String]) shouldBe Vector("DEFINITION")
      (legacyalias \ "diagnostics").as[Vector[play.api.libs.json.JsValue]].exists(x =>
        (x \ "code").as[String] == "LEX_SECTION_REQUIRED" && (x \ "span" \ "source").asOpt[String].contains("spaces/pattern.dox")) shouldBe false
      val shared = _run_("same-concept") { source =>
        _change_(source, "spaces/history.dox")(_.replace("https://example.org/concept/literary-work", "https://example.org/concept/sales-work"))
        _change_(source, "spaces/pattern.dox")(_ + "\n# notes\n\nlower [link](https://example.org/lower).\n\n# Mixed Case\n\nmixed.\n\n# 補足\n\n日本語。\n")
      }
      val sharedpages = (shared \ "pages").as[Vector[play.api.libs.json.JsValue]]
      val sharedwork = sharedpages.filter(x => (x \ "term_iri").as[String] == "https://example.org/term/work")
      sharedwork.map(x => (x \ "page_iri").as[String]).distinct.size shouldBe 2
      sharedwork.map(x => (x \ "concept_iri").as[String]).distinct shouldBe Vector("https://example.org/concept/sales-work")
      val sharedconfig = CozyBok.BuildConfig.create(List(workspace.resolve("same-concept").toString, "--strategy", "preview", "--no-bib-service"))
      val projected = CozyBokImplementation._terms(sharedconfig).filter(_.title == "作品")
      projected.map(_.pageiri).flatten.distinct.size shouldBe 2
      projected.map(_.conceptiri).flatten.distinct shouldBe Vector("https://example.org/concept/sales-work")
      Files.createDirectory(sharedconfig.websitePath)
      CozyBok.finalizeMetadata(sharedconfig)
      _metadata.foreach { relative =>
        Files.readAllBytes(sharedconfig.websitePath.resolve(relative)).toVector shouldBe Files.readAllBytes(sharedconfig.doxsitePath.resolve(relative)).toVector
      }
      val sharedretry = sharedpages.find(x => (x \ "page_iri").as[String].endsWith("/retry")).get
      val sections = (sharedretry \ "sections").as[Vector[play.api.libs.json.JsValue]]
      sections.takeRight(3).map(x => (x \ "original_heading").as[String]) shouldBe Vector("notes", "Mixed Case", "補足")
      (sections.takeRight(3).head \ "content" \ "source").as[String] should include ("https://example.org/lower")
      val references = _run_("global-references") { source =>
        _change_(source, "spaces/reference.dox")(_ => "参照\n====\n\n# HEAD\n\nstatus=published\npublished_at=2026-11-21\nlexidox { version = 1 }\n\n# 本文\n\n<lexiref term=\"Retry\">Retry</lexiref>\n<lexiref term=\"作品\">作品</lexiref>\n<lexiref term=\"未知\">未知</lexiref>\n")
      }
      val refs = (references \ "references").as[Vector[play.api.libs.json.JsValue]]
      refs.map(x => (x \ "resolution" \ "status").as[String]) shouldBe Vector("resolved", "failed", "failed")
      (refs.head \ "resolution" \ "page_iri").as[String] shouldBe "https://example.org/page/sales/retry"
      val diagnostics = (references \ "diagnostics").as[Vector[play.api.libs.json.JsValue]]
      val ambiguous = diagnostics.find(x => (x \ "code").as[String] == "LEX_REFERENCE_AMBIGUOUS").get
      (ambiguous \ "value").as[String] shouldBe "作品"
      (ambiguous \ "span" \ "source").as[String] shouldBe "spaces/reference.dox"
      val candidates = (ambiguous \ "candidates").as[Vector[play.api.libs.json.JsValue]]
      candidates.map(x => (x \ "space").as[String]).toSet shouldBe Set("sales", "history")
      candidates.map(x => (x \ "page_iri").as[String]).toSet shouldBe Set("https://example.org/page/sales/work", "https://example.org/page/history/work")
      diagnostics.exists(x => (x \ "code").as[String] == "LEX_REFERENCE_UNDEFINED" && (x \ "value").as[String] == "未知") shouldBe true
      val invalid = _run_("invalid-sections-and-iri") { source =>
        _change_(source, "spaces/sales.dox")(_ + "\n# UNKNOWN\n\nUnknown.\n")
        _change_(source, "spaces/pattern.dox")(_.replace("# SOLUTION\n\nRetry with [backoff](https://example.org/backoff).\n\n", ""))
        _change_(source, "spaces/history.dox")(_.replace("https://example.org/page/history/work", "not-an-absolute-iri"))
      }
      val errors = (invalid \ "diagnostics").as[Vector[play.api.libs.json.JsValue]]
      Vector("LEX_SECTION_UNKNOWN" -> "UNKNOWN", "LEX_SECTION_REQUIRED" -> "SOLUTION", "LEX_IDENTITY" -> "not-an-absolute-iri").foreach { case (code, value) =>
        errors.exists(x => (x \ "code").as[String] == code && (x \ "value").asOpt[String].contains(value)) shouldBe true
      }
    }

    private def _authored_controls(source: Path): Unit = {
      val glossary = source.resolve("glossary/technology/legacy-process.dox")
      Files.createDirectories(glossary.getParent)
      Files.write(glossary, ("Legacy Process\n==============\n\n# HEAD\n\nstatus=published\npublished_at=2026-11-21\nterm_type=process\n\n# DEFINITION\n\nA legacy process.\n\n# RELATIONS\n\nLegacy relationships.\n").getBytes(StandardCharsets.UTF_8))
      Files.write(source.resolve("index.dox"), ("LexiDox Acceptance\n===================\n\n# HEAD\n\nstatus=published\npublished_at=2026-11-21\n\n# Body\n\nAcceptance fixture landing page.\n").getBytes(StandardCharsets.UTF_8))
    }

    private def _publish(source: Path): Unit = {
      Vector("sales", "history", "pattern", "reference", "legacy").foreach { name =>
        val path = source.resolve(s"spaces/$name.dox")
        val value = _read(path).replace("# HEAD\n\n", "# HEAD\n\nstatus=published\npublished_at=2026-11-21\n")
        Files.write(path, value.getBytes(StandardCharsets.UTF_8))
      }
    }

    private def _read(path: Path): String = new String(Files.readAllBytes(path), StandardCharsets.UTF_8)
    private def _copy(source: Path, target: Path): Unit = {
      val stream = Files.walk(source)
      try stream.iterator.asScala.foreach { path =>
        val destination = target.resolve(source.relativize(path))
        if (Files.isDirectory(path)) Files.createDirectories(destination)
        else Files.copy(path, destination)
      } finally stream.close()
    }
  }
}
