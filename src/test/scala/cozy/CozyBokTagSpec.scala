package cozy

import cozy.bok.CozyBok
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import scala.collection.JavaConverters._
import org.scalatest.GivenWhenThen
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Jun. 28, 2026
 * @version Jul. 13, 2026
 * @author  ASAMI, Tomoharu
 */
class CozyBokTagSpec
    extends AnyWordSpec
    with GivenWhenThen
    with CozySpecVocabulary {
  "Cozy BoK tag navigation" should {
    "consume hierarchical tag metadata from BoK handoff metadata" which {
      "render namespace pages and tag resource pages from canonical tag keys" in {
        _with_temp_dir("cozy-bok-tags") { dir =>
          Given(
            "a BoK source tree, SmartDox metadata with explicit tags, and a tagged repository CAR"
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
          _write(
            dir.resolve("src/main/doxsite/technology/category.yaml"),
            """name: Technology
              |title: Technology
              |description: Technology category.
              |""".stripMargin
          )
          _write(
            dir.resolve("src/main/doxsite/technology/index.dox"),
            "Technology\n==========\n"
          )
          _write(
            dir.resolve("src/main/doxsite/scenario/technology/review.md"),
            """---
              |title: Knowledge Review
              |scenario:
              |  type: simple
              |  id: SC-REVIEW
              |  tags:
              |    - review
              |    - knowledge-flow
              |---
              |
              |# Scenario
              |
              |- Reviewer: reviews knowledge.
              |""".stripMargin
          )
          _write(
            dir.resolve("repository/catalog/car/review-runtime.yaml"),
            """schemaVersion: 1
              |kind: car
              |artifactId: review-runtime
              |recommended: 1.0.0
              |tags:
              |  - technology.review
              |versions:
              |  - version: 1.0.0
              |    channel: stable
              |    file: repository/car/review-runtime/1.0.0/review-runtime-1.0.0.car
              |""".stripMargin
          )
          val config = CozyBok.BuildConfig.create(List(dir.toString, "--strategy", "preview"))

          When("Cozy builds BoK pages from generated metadata")
          CozyBok.build(config, new TagMetadataRunner)

          Then("the tag dashboard lists hierarchical tags and linked knowledge items")
          val tags = _read(dir.resolve("website.d/tags/index.html"))
          tags should include_html("Tags")
          tags should include_html("technology.review")
          tags should include_html("workflow.review")
          tags should include_html("workflow.review.checklist")
          tags should include_html("bok-tag-tree")
          tags should include_html("Tag Tree")
          tags should include_html("class=\"bok-tag-tree-segment\" href=\"workflow/review.html\">review</a>")
          tags should include_html("data-tag-categories=\"technology\"")
          tags should include_html("new URLSearchParams(window.location.search).get('category')")

          And("namespace and leaf tag pages use hierarchical URLs")
          val namespace = _read(dir.resolve("website.d/tags/technology/index.html"))
          namespace should include_html("technology.review")
          val review = _read(dir.resolve("website.d/tags/technology/review.html"))
          review should include_html("Antora tag article.")
          review should include_html("Review tag definition from Antora.")
          review should include_html("Purpose")
          review should include_html("""<tr><th>FQN</th><td><code>technology.review</code></td></tr>""")
          review should include_html("Linked knowledge")
          review should include_html("Knowledge Review")
          review should include_html("Design Patterns")
          review should include_html("Architecture Pattern")
          review should include_html("Review Article")
          review should include_html("Repository CARs")
          review should include_html("review-runtime")
          review should include_html(
            "href=\"../../repository/car/review-runtime/index.html\""
          )
          review should include_html(
            "href=\"../../rdf/index.html?tag=technology.review\""
          )
          review should not(include_html("bok-dashboard-shell"))
          val workflowreview = _read(dir.resolve("website.d/tags/workflow/review.html"))
          workflowreview should include_html("""<h1 class="page">review</h1>""")
          workflowreview should include_html("""<tr><th>FQN</th><td><code>workflow.review</code></td></tr>""")
          val checklist = _read(dir.resolve("website.d/tags/workflow/review/checklist.html"))
          checklist should include_html("""<h1 class="page">checklist</h1>""")
          checklist should include_html("""<tr><th>FQN</th><td><code>workflow.review.checklist</code></td></tr>""")
          _read(dir.resolve("website.d/tags/knowledge/graph.html")) should include_html("Architecture Pattern")
          val termpage = _read(dir.resolve("website.d/glossary/technology/architecture-pattern.html"))
          termpage should include_html("Antora term article.")
          termpage should include_html("knowledge.graph")
          termpage should not(include_html("bok-term-hub"))

          And("tag pages connect to RDF graph metadata through the canonical tag filter")
          val rdf = _read(dir.resolve("website.d/rdf/index.html"))
          rdf should include_html("bok-rdf-tag-filter")
          rdf should include_html("params.get('tag')")
          rdf should include_html("function hasTag(item, tag)")
          rdf should include_html("hasTag(edge, tag)")
          rdf should include_html("hasTag(node, tag)")

          And("Home and Category dashboards expose the tag navigation entry point")
          _read(dir.resolve("website.d/index.html")) should include_html("href=\"tags/index.html\"")
          _read(dir.resolve("website.d/technology/index.html")) should include_html(
            "href=\"../tags/index.html?category=technology\""
          )

          And("generated article pages expose tag chips and omit category index self links from Antora navigation")
          val article = _read(dir.resolve("website.d/technology/review-article.html"))
          article should include_html("bok-article-tag-chip-list")
          article should include_html("bok-article-tag-namespace\">technology</span>")
          article should include_html("class=\"bok-article-tag-leaf\" href=\"../tags/technology/review.html\">review</a>")
          article should not(include_html("href=\"index.html\">Technology</a>"))

          And("Cozy publishes the SmartDox tag handoff metadata")
          _read(dir.resolve("website.d/metadata/tags/tags.json")) should include_html("technology.review")
        }
      }
    }
    "fallback to usage-derived tags when SmartDox tag index is absent" which {
      "normalize category-scoped short tags to hierarchical canonical keys" in {
        _with_temp_dir("cozy-bok-tags-fallback") { dir =>
          Given("SmartDox metadata contains tags on documents but no dedicated tag index")
          _write_minimal_source(dir)
          val config = CozyBok.BuildConfig.create(List(dir.toString, "--strategy", "preview"))

          When("Cozy builds tag pages from usage-derived tag metadata")
          CozyBok.build(config, new TagMetadataRunner(writetagindex = false))

          Then("short tags in the technology category become technology-scoped tags")
          val tags = _read(dir.resolve("website.d/tags/index.html"))
          tags should include_html("technology.review")
          _read(dir.resolve("website.d/tags/technology/index.html")) should include_html("technology.review")
          val review = _read(dir.resolve("website.d/tags/technology/review.html"))
          review should include_html("""<h1 class="page">review</h1>""")
          review should include_html("""<li><a href="../../tags/technology/index.html">technology</a></li>""")
          review should include_html("""<tr><th>FQN</th><td><code>technology.review</code></td></tr>""")
          review should include_html("Technology Overview")

          And("no SmartDox tag handoff metadata is invented by Cozy")
          Files.exists(dir.resolve("website.d/metadata/tags/tags.json")) shouldBe false
        }
      }
    }
    "omit empty tag presentation when tag metadata is absent" which {
      "preserve an untagged article without generating tag containers or leaf pages" in {
        _with_temp_dir("cozy-bok-tags-empty") { dir =>
          Given("SmartDox metadata contains an article fragment without tag metadata")
          _write_minimal_source(dir)
          val config = CozyBok.BuildConfig.create(List(dir.toString, "--strategy", "preview"))

          When("Cozy builds tag and article pages without tag metadata")
          CozyBok.build(config, new NoTagMetadataRunner)

          Then("the article remains available without an empty tag container")
          val article = _read(dir.resolve("website.d/technology/review-article.html"))
          article should include_html("Generated article body.")
          article should not(include_html("bok-article-tag-chip-list"))
          article should not(include_html("bok-article-tags"))

          And("the tag dashboard reports its empty state without inventing a leaf page")
          _read(dir.resolve("website.d/tags/index.html")) should include_html(
            "No tag metadata yet."
          )
          Files.exists(
            dir.resolve("website.d/tags/technology/review.html")
          ) shouldBe false
        }
      }
    }
  }

  private class TagMetadataRunner(writetagindex: Boolean = true) extends CozyBok.Runner {
    def run(command: Vector[String], cwd: Path): Unit =
      if (command.take(2) == Vector("dox", "site")) {
        _write(cwd.resolve("doxsite.d/metadata/dashboard/site.json"), _dashboard_json)
        _write(cwd.resolve("doxsite.d/metadata/rdf/graph.json"), _rdf_graph_json)
        _write(cwd.resolve("doxsite.d/metadata/documents/fragments.json"), _fragments_json)
        _write(cwd.resolve("doxsite.d/metadata/glossary/terms.json"), _terms_json)
        _write(cwd.resolve("doxsite.d/metadata/bibliography/bibliography.json"), _bibliography_json)
        if (writetagindex)
          _write(cwd.resolve("doxsite.d/metadata/tags/tags.json"), _tags_json)
        _write(cwd.resolve("website.d/tags/technology/review.html"), _tag_antora_html)
        _write(cwd.resolve("website.d/technology/review-article.html"), _article_antora_html)
        _write(cwd.resolve("website.d/glossary/technology/architecture-pattern.html"), _term_antora_html)
        _write(cwd.resolve("doxsite.d/site.ttl"), "@prefix ex: <https://example.com/> .\n")
        _write(cwd.resolve("doxsite.d/site.jsonld"), "{\"@graph\":[]}\n")
      }
  }

  private class NoTagMetadataRunner extends CozyBok.Runner {
    def run(command: Vector[String], cwd: Path): Unit =
      if (command.take(2) == Vector("dox", "site")) {
        _write(cwd.resolve("doxsite.d/metadata/dashboard/site.json"), _dashboard_json)
        _write(cwd.resolve("doxsite.d/metadata/rdf/graph.json"), """{"nodes": [], "edges": [], "truncated": false}
          |""".stripMargin)
        _write(
          cwd.resolve("doxsite.d/metadata/documents/fragments.json"),
          _fragments_without_tags_json
        )
        _write(cwd.resolve("website.d/technology/review-article.html"), _article_antora_html)
        _write(cwd.resolve("doxsite.d/site.ttl"), "@prefix ex: <https://example.com/> .\n")
        _write(cwd.resolve("doxsite.d/site.jsonld"), "{\"@graph\":[]}\n")
      }
  }

  private def _write_minimal_source(dir: Path): Unit = {
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
      """name: Technology
        |title: Technology
        |description: Technology category.
        |""".stripMargin
    )
    _write(dir.resolve("src/main/doxsite/technology/index.dox"), "Technology\n==========\n")
  }

  private def _dashboard_json: String =
    """{
      |  "counts": {"category_count": 1, "article_count": 1, "glossary_term_count": 1, "total_item_count": 2},
      |  "rdf": {"resource_count": 2, "triple_count": 3, "subject_count": 2, "predicate_count": 2},
      |  "increments": {"scale": "day", "buckets": []},
      |  "categories": [{
      |    "name": "technology",
      |    "title": "Technology",
      |    "counts": {"category_count": 0, "article_count": 1, "glossary_term_count": 1, "total_item_count": 2},
      |    "increments": {"scale": "day", "buckets": []},
      |    "rdf": {"resource_count": 2, "triple_count": 3, "subject_count": 2, "predicate_count": 2}
      |  }]
      |}
      |""".stripMargin

  private def _rdf_graph_json: String =
    """{
      |  "nodes": [
      |    {"id": "https://www.simplemodeling.org/technology/review-article", "label": "Review Article", "node_type": "uri", "category": "technology", "degree": 1, "terms": [], "tags": ["technology.review"]},
      |    {"id": "https://example.com/review", "label": "review", "node_type": "uri", "category": "technology", "degree": 1, "terms": [], "tags": ["technology.review"]}
      |  ],
      |  "edges": [
      |    {"source": "https://www.simplemodeling.org/technology/review-article", "target": "https://example.com/review", "predicate": "https://schema.org/about", "label": "about", "category": "technology", "terms": [], "tags": ["technology.review"]}
      |  ],
      |  "truncated": false
      |}
      |""".stripMargin

  private def _fragments_json: String =
    """{
      |  "fragments": [{
      |    "source_path": "technology/index.dox",
      |    "public_path": "technology/index.html",
      |    "locale": "en",
      |    "kind": "article",
      |    "category": "technology",
      |    "title": "Technology Overview",
      |    "headline": "Technology Overview",
      |    "brief": "Tagged article.",
      |    "body_html": "<p>Tagged article.</p>",
      |    "tags": ["review"]
      |  }]
      |}
      |""".stripMargin

  private def _fragments_without_tags_json: String =
    """{
      |  "fragments": [{
      |    "source_path": "technology/review-article.dox",
      |    "public_path": "technology/review-article.html",
      |    "locale": "en",
      |    "kind": "article",
      |    "category": "technology",
      |    "title": "Review Article",
      |    "headline": "Review Article",
      |    "brief": "Untagged article.",
      |    "body_html": "<p>Generated article body.</p>",
      |    "tags": []
      |  }]
      |}
      |""".stripMargin

  private def _terms_json: String =
    """{
      |  "terms": [{
      |    "id": "technology:architecture-pattern",
      |    "slug": "architecture-pattern",
      |    "title": "Architecture Pattern",
      |    "category": "technology",
      |    "source_path": "glossary/technology/architecture-pattern.dox",
      |    "public_path": "glossary/technology/architecture-pattern.html",
      |    "definition_html": "<p>Pattern term.</p>",
      |    "tags": ["review", "knowledge.graph"]
      |  }]
      |}
      |""".stripMargin

  private def _bibliography_json: String =
    """{
      |  "entries": [{
      |    "id": "bib:design-patterns",
      |    "slug": "design-patterns",
      |    "entry_type": "book",
      |    "title": "Design Patterns",
      |    "category": "technology",
      |    "source_path": "bibliography/technology/design-patterns.bib.dox",
      |    "public_path": "bibliography/technology/design-patterns.html",
      |    "source_kind": "internal",
      |    "needs_resolution": false,
      |    "tags": ["review"]
      |  }]
      |}
      |""".stripMargin

  private def _tags_json: String =
    """{
      |  "tags": [{
      |    "id": "tag:technology.review",
      |    "key": "technology.review",
      |    "segments": ["technology", "review"],
      |    "namespace": "technology",
      |    "parent": "tag:technology",
      |    "slug": "technology/review",
      |    "label": "review",
      |    "title": "technology.review",
      |    "summary": "Technology review resources.",
      |    "locale": "en",
      |    "source_path": "tags/technology/review.dox",
      |    "public_path": "tags/technology/review.html",
      |    "body_html": "<p>Review tag definition.</p>",
      |    "refs": [
      |      {"kind": "article", "title": "Review Article", "public_path": "technology/review-article.html", "category": "technology"},
      |      {"kind": "article", "title": "Technology Overview", "public_path": "technology/index.html", "category": "technology"},
      |      {"kind": "scenario", "title": "Knowledge Review", "public_path": "scenario/technology/review.html", "category": "technology"},
      |      {"kind": "term", "title": "Architecture Pattern", "public_path": "glossary/technology/architecture-pattern.html", "category": "technology"},
      |      {"kind": "bibliography", "title": "Design Patterns", "public_path": "bibliography/technology/design-patterns.html", "category": "technology"}
      |    ],
      |    "children": []
      |  }, {
      |    "id": "tag:workflow.review",
      |    "key": "workflow.review",
      |    "segments": ["workflow", "review"],
      |    "namespace": "workflow",
      |    "parent": "tag:workflow",
      |    "slug": "workflow/review",
      |    "label": "review",
      |    "title": "Workflow Review",
      |    "summary": "Workflow review resources.",
      |    "locale": "en",
      |    "public_path": "tags/workflow/review.html",
      |    "refs": [
      |      {"kind": "scenario", "title": "Knowledge Review", "public_path": "scenario/technology/review.html", "category": "technology"}
      |    ],
      |    "children": []
      |  }, {
      |    "id": "tag:workflow.review.checklist",
      |    "key": "workflow.review.checklist",
      |    "segments": ["workflow", "review", "checklist"],
      |    "namespace": "workflow",
      |    "parent": "tag:workflow.review",
      |    "slug": "workflow/review/checklist",
      |    "label": "checklist",
      |    "title": "Workflow Review Checklist",
      |    "summary": "Workflow review checklist resources.",
      |    "locale": "en",
      |    "public_path": "tags/workflow/review/checklist.html",
      |    "refs": [
      |      {"kind": "article", "title": "Review Article", "public_path": "technology/review-article.html", "category": "technology"}
      |    ],
      |    "children": []
      |  }]
      |}
      |""".stripMargin

  private def _tag_antora_html: String =
    """<!doctype html>
      |<html lang="en">
      |<head><meta charset="utf-8"><title>technology.review</title></head>
      |<body class="article">
      |<div class="body">
      |<main class="article">
      |<div class="content">
      |<article class="doc">
      |<h1 class="page">technology.review</h1>
      |<div class="sect1"><h2>Purpose</h2><div class="sectionbody"><p>Antora tag article.</p><p>Review tag definition from Antora.</p></div></div>
      |</article>
      |</div>
      |</main>
      |</div>
      |</body>
      |</html>
      |""".stripMargin

  private def _article_antora_html: String =
    """<!doctype html>
      |<html lang="en">
      |<head><meta charset="utf-8"><title>Review Article</title></head>
      |<body class="article">
      |<div class="body">
      |<main class="article">
      |<div class="content">
      |<article class="doc">
      |<h1 class="page">Review Article</h1>
      |<p>Generated article body.</p>
      |</article>
      |</div>
      |<div class="nav-container">
      |<li class="nav-item" data-depth="1">
      |<a class="nav-link" href="index.html">Technology</a>
      |</li>
      |</div>
      |</main>
      |</div>
      |</body>
      |</html>
      |""".stripMargin

  private def _term_antora_html: String =
    """<!doctype html>
      |<html lang="en">
      |<head><meta charset="utf-8"><title>Architecture Pattern</title></head>
      |<body class="article">
      |<div class="body">
      |<main class="article">
      |<div class="content">
      |<article class="doc">
      |<h1 class="page">Architecture Pattern</h1>
      |<p>Antora term article.</p>
      |<p><a class="bok-tag-chip" href="../../tags/knowledge/graph.html">knowledge.graph</a></p>
      |</article>
      |</div>
      |</main>
      |</div>
      |</body>
      |</html>
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
