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
class CozyBokMonoKotoSpec
    extends AnyWordSpec
    with GivenWhenThen
    with CozySpecVocabulary {
  "Cozy BoK mono-koto analysis" should {
    "derive business-domain analysis surfaces from term, scenario, and CML metadata" which {
      "render mono/koto classification and CML alignment diagnostics without editing source" in {
        _with_temp_dir("cozy-bok-mono-koto") { dir =>
          Given("a BoK source tree and SmartDox handoff metadata for terms, scenarios, and RDF")
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
            dir.resolve("src/main/doxsite/technology/category.yaml"),
            "name: Technology\ntitle: Technology\n"
          )
          _write(
            dir.resolve("src/main/doxsite/technology/index.dox"),
            "Technology\n==========\n\n# Overview\nTechnology category.\n"
          )
          _write(
            dir.resolve("src/main/doxsite/scenario/technology/review.md"),
            """---
              |title: Architecture Review Scenario
              |brief: Review knowledge before publication.
              |scenario:
              |  type: use-case
              |  id: scenario:review
              |  terms:
              |    - technology:architecture-review
              |    - technology:knowledge-owner
              |    - technology:reviewer
              |status: draft
              |---
              |
              |# UseCase
              |
              |## Architecture Review Scenario
              |
              |### MainFlow
              |
              |- [review] Reviewer: review content quality
              |- end
              |""".stripMargin
          )
          val config =
            CozyBok.BuildConfig.create(List(dir.toString, "--strategy", "preview"))

          When("Cozy builds the BoK as a metadata consumer")
          CozyBok.build(config, new MonoKotoMetadataRunner)

          Then("the Glossary dashboard summarizes term types and mono/koto analysis")
          val glossary = _read(dir.resolve("website.d/glossary/index.html"))
          glossary should include_html("Glossary Workflow")
          glossary should include_html("Candidate extraction")
          glossary should include_html("Done / target")
          glossary should include_html("Term definition")
          glossary should include_html("Term classification")
          glossary should include_html("Type group breakdown")
          glossary should include_html("Mono")
          glossary should include_html("Koto")
          glossary should include_html("Term Usage")
          glossary should include_html("RDF connection")
          glossary should include_html("CML connection")
          glossary should include_html("entityKind")
          glossary should include_html("Term Classification")
          glossary should include_html("Missing Analysis")
          glossary should include_html("No event/scenario reference")
          glossary should include_html("Architecture Review")
          glossary should include_html("Knowledge Owner")
          glossary should include_html("Reviewer")

          And("the Event Term Hub shows koto classification, related scenarios, and CML linkage")
          val event =
            _read(dir.resolve("website.d/glossary/technology/architecture-review.html"))
          event should include_html("Mono/Koto Analysis")
          event should include_html("Koto")
          event should include_html("CML linkage")
          event should include_html("event: knowledge.reviewed")
          event should include_html("statemachine: KnowledgeLifecycle")
          event should include_html("Architecture Review Scenario")
          event should include_html("No mono/koto or CML alignment issue is known.")

          And("the Actor and Role Term Hubs remain mono terms with alignment diagnostics")
          val actor =
            _read(dir.resolve("website.d/glossary/technology/knowledge-owner.html"))
          actor should include_html("Mono")
          actor should include_html("No CML linkage is recorded yet.")
          val role =
            _read(dir.resolve("website.d/glossary/technology/reviewer.html"))
          role should include_html("Mono")
          role should include_html("No CML linkage is recorded yet.")

          And("general term-level CML linkage supports Entity and Rule elements")
          val concept =
            _read(dir.resolve("website.d/glossary/technology/architecture.html"))
          concept should include_html("entity: ArchitectureModel")
          val rule =
            _read(dir.resolve("website.d/glossary/technology/review-rule.html"))
          rule should include_html("Rule")
          rule should include_html("rule: ReviewApprovalRule")

          And("the RDF viewer exposes mono/koto and CML linkage in the node view")
          val rdf = _read(dir.resolve("website.d/rdf/index.html"))
          rdf should include_html("nodeMonoKotoValues")
          rdf should include_html("nodeCmlLinkValues")
          rdf should include_html("monoKoto")
          rdf should include_html("CML linkage")
          val nodedetail = _read(dir.resolve("website.d/rdf/node.html"))
          nodedetail should include_html("data-terms=\"../metadata/glossary/terms.json\"")
          nodedetail should include_html("monoKoto")
          nodedetail should include_html("CML linkage")
        }
      }

      "render CML elements as project metadata without auto-generating glossary terms" in {
        _with_temp_dir("cozy-bok-cml-derived-mono-koto") { dir =>
          Given("a BoK source tree with CAR project metadata generated from CML")
          val externalproject = dir.resolve("external/nict-knowledgehub")
          _write(
            externalproject.resolve("build.sbt"),
            "ThisBuild / version := \"0.1.0\"\n"
          )
          _write(
            externalproject.resolve("src/main/cozy/nict-knowledgehub.cml"),
            """# ENTITY
              |
              |## KnowledgeItem
              |
              |# STATEMACHINE
              |
              |## KnowledgeLifecycle
              |""".stripMargin
          )
          _write(
            dir.resolve("conf/cozy/config.yaml"),
            s"""bok:
               |  projects:
               |    nict-knowledgehub:
               |      repository: path
               |      path: ${externalproject.toString}
               |""".stripMargin
          )
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
          val pkg = dir.resolve("src/main/doxsite/projects/technology/nict-knowledgehub")
          _write(pkg.resolve("index.dox"), "NictKnowledgeHub\n================\n")
          _write(
            pkg.resolve("project.yaml"),
            """project:
              |  type: car
              |  name: nict-knowledgehub
              |  mode: external
              |  ref: nict-knowledgehub
              |car:
              |  module: nict-knowledgehub
              |title: NICT KnowledgeHub
              |version: 0.1.0
              |publication:
              |  path: projects/technology/nict-knowledgehub
              |""".stripMargin
          )

          When("Cozy registers project metadata and builds the project page")
          CozyBok.publishProjects(CozyBok.PublicationConfig.create("publish-projects", List(dir.toString)))
          CozyBok.build(
            CozyBok.BuildConfig.create(List(dir.toString, "--strategy", "preview")),
            new EmptyMetadataRunner
          )

          Then("CML-only elements are displayed as unlinked model elements, not generated terms")
          val page = _read(dir.resolve("website.d/projects/technology/nict-knowledgehub/index.html"))
          page should include("""class="bok-project-cml-tabs"""")
          page should include("""class="bok-project-cml-source-card"""")
          page should include_html("nict-knowledgehub.cml")
          page should include_html("src/main/cozy/nict-knowledgehub.cml")
          page should not include ("Source: direct-cml-scan")
          page should include("bok-project-cml-summary-total")
          page should include("bok-project-cml-summary-breakdown")
          page should include("""<span class="bok-project-unlinked-term">-</span>""")
          page should not include ("No linked glossary term")
          page should not include ("Role")
          page should not include ("Layer")
          page should include_html("KnowledgeItem")
          page should include_html("KnowledgeLifecycle")
          dir.resolve("website.d/glossary/cml/knowledge-item.html") shouldNot exist_path
          dir.resolve("website.d/glossary/cml/knowledge-lifecycle.html") shouldNot exist_path
        }
      }
    }
  }

  private class MonoKotoMetadataRunner extends CozyBok.Runner {
    def run(command: Vector[String], cwd: Path): Unit =
      if (command.take(2) == Vector("dox", "site")) {
        _write(cwd.resolve("doxsite.d/metadata/dashboard/site.json"), _dashboard_json)
        _write(cwd.resolve("doxsite.d/metadata/rdf/graph.json"), _rdf_graph_json)
        _write(cwd.resolve("doxsite.d/metadata/glossary/terms.json"), _terms_json)
        _write(cwd.resolve("doxsite.d/metadata/scenarios/scenarios.json"), _scenarios_json)
        _write(cwd.resolve("doxsite.d/site.ttl"), "@prefix ex: <https://example.com/> .\n")
        _write(cwd.resolve("doxsite.d/site.jsonld"), "{\"@graph\":[]}\n")
      }
  }

  private class EmptyMetadataRunner extends CozyBok.Runner {
    def run(command: Vector[String], cwd: Path): Unit =
      if (command.take(2) == Vector("dox", "site")) {
        _write(cwd.resolve("doxsite.d/metadata/dashboard/site.json"), _dashboard_json)
        _write(cwd.resolve("doxsite.d/metadata/rdf/graph.json"), """{"nodes": [], "edges": [], "truncated": false}\n""")
        _write(cwd.resolve("doxsite.d/site.ttl"), "@prefix ex: <https://example.com/> .\n")
        _write(cwd.resolve("doxsite.d/site.jsonld"), "{\"@graph\":[]}\n")
      }
  }

  private def _dashboard_json: String =
    """{
      |  "counts": {"category_count": 1, "article_count": 1, "glossary_term_count": 5, "total_item_count": 6},
      |  "rdf": {"resource_count": 1, "triple_count": 2, "subject_count": 1, "predicate_count": 2},
      |  "increments": {"scale": "day", "buckets": []},
      |  "categories": [{"name": "technology", "title": "Technology", "counts": {"category_count": 0, "article_count": 1, "glossary_term_count": 5, "total_item_count": 6}, "increments": {"scale": "day", "buckets": []}, "rdf": {"resource_count": 1, "triple_count": 2, "subject_count": 1, "predicate_count": 2}}]
      |}
      |""".stripMargin

  private def _rdf_graph_json: String =
    """{
      |  "nodes": [
      |    {"id": "https://example.com/knowledge/review", "label": "Architecture Review", "type": "uri", "node_type": "uri", "category": "technology", "degree": 2, "terms": ["technology:architecture-review"]}
      |  ],
      |  "edges": [],
      |  "truncated": false
      |}
      |""".stripMargin

  private def _scenarios_json: String =
    """{
      |  "scenarios": [
      |    {"id": "scenario:review", "slug": "review", "scenario_type": "use-case", "title": "Architecture Review Scenario", "summary": "Review knowledge before publication.", "category": "technology", "source_path": "scenario/technology/review.md", "public_path": "scenario/technology/review.html", "terms": ["technology:architecture-review", "technology:knowledge-owner", "technology:reviewer"], "status": "draft"}
      |  ]
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
      |      "article_refs": [], "term_refs": [], "rdf_refs": [{"resource": "https://example.com/knowledge/review", "label": "Architecture Review RDF", "predicate": "schema:about", "direction": "outgoing"}], "video_refs": [],
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
      |    },
      |    {
      |      "id": "technology:architecture",
      |      "slug": "architecture",
      |      "title": "Architecture",
      |      "reading": null,
      |      "category": "technology",
      |      "source_path": "glossary/technology/architecture.dox",
      |      "public_path": "glossary/technology/architecture.html",
      |      "definition_html": "<p>Architecture concept.</p>",
      |      "summary": "Architecture concept.",
      |      "aliases": [],
      |      "term_type": "concept",
      |      "cml": [{"kind": "entity", "name": "ArchitectureModel"}],
      |      "article_refs": [], "term_refs": [], "rdf_refs": [], "video_refs": [],
      |      "quality": {"isolated": false, "unreferenced": false, "weakly_connected": false}
      |    },
      |    {
      |      "id": "technology:review-rule",
      |      "slug": "review-rule",
      |      "title": "Review Rule",
      |      "reading": null,
      |      "category": "technology",
      |      "source_path": "glossary/technology/review-rule.dox",
      |      "public_path": "glossary/technology/review-rule.html",
      |      "definition_html": "<p>Review approval rule.</p>",
      |      "summary": "Review approval rule.",
      |      "aliases": [],
      |      "term_type": "rule",
      |      "cml": {"rule": "ReviewApprovalRule"},
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
