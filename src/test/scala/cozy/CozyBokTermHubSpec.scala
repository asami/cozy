package cozy

import cozy.bok.CozyBok
import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path}
import scala.collection.JavaConverters._
import org.scalatest.GivenWhenThen
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Jun. 22, 2026
 * @version Jun. 24, 2026
 * @author  ASAMI, Tomoharu
 */
class CozyBokTermHubSpec
    extends AnyWordSpec
    with GivenWhenThen
    with CozySpecVocabulary {
  "Cozy BoK term hub rendering" should {
    "consume SmartDox term metadata" which {
      "render Glossary dashboard, Term Hub, and term RDF navigation from terms.json" in {
        _with_temp_dir("cozy-bok-term-hub") { dir =>
          Given("a BoK source tree and SmartDox-generated term metadata")
          _write(
            dir.resolve("src/main/doxsite/site.conf"),
            """site {
              |  output {
              |    locale_mode = "single_locale_root"
              |  }
              |}
              |""".stripMargin
          )
          _write(
            dir.resolve("src/main/doxsite/architecture/category.yaml"),
            """name: Architecture
              |title: Architecture
              |description: Architecture category.
              |""".stripMargin
          )
          _write(
            dir.resolve("src/main/doxsite/architecture/index.dox"),
            """Architecture
              |============
              |
              |# Overview
              |Architecture narrative.
              |""".stripMargin
          )
          _write(
            dir.resolve("src/main/doxsite/glossary/architecture/runtime.md"),
            """---
              |title: Runtime
              |brief: Runtime markdown term brief.
              |reading: らんたいむ
              |---
              |
              |Runtime source definition.
              |""".stripMargin
          )
          val config = CozyBok.BuildConfig.create(
            List(dir.toString, "--strategy", "preview")
          )

          When("Cozy builds the BoK")
          CozyBok.build(config, new TermMetadataRunner)

          Then(
            "the Glossary page becomes a term dashboard driven by SmartDox metadata"
          )
          val glossary = _read(dir.resolve("website.d/glossary/index.html"))
          glossary should include("Term Dashboard")
          glossary should include("glossary/&lt;category&gt;/")
          glossary should include("class=\"bok-term-group-grid\"")
          glossary should include("href=\"architecture/runtime.html\"")
          glossary should include(
            "href=\"../rdf/index.html?term=architecture%3Aruntime\""
          )
          glossary should include("RDF")

          And(
            "the Term Hub keeps the existing term URL and shows definition, RDF, and quality cards"
          )
          val term =
            _read(dir.resolve("website.d/glossary/architecture/runtime.html"))
          term should include("class=\"bok-dashboard-shell bok-term-hub\"")
          term should include("Term Hub")
          term should include("Runtime")
          term should include("らんたいむ")
          term should include(
            "<p>Runtime definition from SmartDox metadata.</p>"
          )
          term should include("class=\"card bok-card bok-card-related\"")
          term should include("runtime-resource")
          term should include("Runtime Article")
          term should include("architecture/runtime-article.html")
          term should include("Runtime Video")
          term should include("/repository/video/runtime.mp4")
          term should include(
            "href=\"../../rdf/index.html?term=architecture%3Aruntime\""
          )
          term should not include ("Runtime source definition")

          And("the RDF viewer accepts the term metadata and term query surface")
          val rdf = _read(dir.resolve("website.d/rdf/index.html"))
          rdf should include("data-terms=\"../metadata/glossary/terms.json\"")
          rdf should include("bok-rdf-term-filter")
          rdf should include("params.get('term')")
          rdf should include("fetch(root.getAttribute('data-terms'))")
          rdf should include("termLabel(termIndex, term)")
          rdf should include("hasTerm(edge, term)")
          _read(
            dir.resolve("website.d/metadata/glossary/terms.json")
          ) should include("architecture:runtime")
          _read(
            dir.resolve("website.d/metadata/rdf/graph.json")
          ) should include("\"terms\": [\"architecture:runtime\"]")
        }
      }

      "fall back to source glossary items when terms metadata is absent" in {
        _with_temp_dir("cozy-bok-term-fallback") { dir =>
          Given("a BoK source tree whose SmartDox output has no terms.json")
          _write(
            dir.resolve("src/main/doxsite/site.conf"),
            """site {
              |  output {
              |    locale_mode = "single_locale_root"
              |  }
              |}
              |""".stripMargin
          )
          _write(
            dir.resolve("src/main/doxsite/architecture/category.yaml"),
            """name: Architecture
              |title: Architecture
              |description: Architecture category.
              |""".stripMargin
          )
          _write(
            dir.resolve("src/main/doxsite/architecture/index.dox"),
            "Architecture\n============\n"
          )
          _write(
            dir.resolve("src/main/doxsite/glossary/architecture/runtime.md"),
            """---
              |title: Runtime
              |brief: Runtime markdown term brief.
              |reading: らんたいむ
              |---
              |
              |Runtime source definition.
              |""".stripMargin
          )
          val config = CozyBok.BuildConfig.create(
            List(dir.toString, "--strategy", "preview")
          )

          When("Cozy builds without SmartDox term metadata")
          CozyBok.build(config, new NoTermMetadataRunner)

          Then(
            "the Glossary dashboard and Term Hub still render without failing"
          )
          _read(dir.resolve("website.d/glossary/index.html")) should include(
            "href=\"architecture/runtime.html\""
          )
          _read(dir.resolve("website.d/glossary/index.html")) should include(
            "らんたいむ"
          )
          _read(
            dir.resolve("website.d/glossary/architecture/runtime.html")
          ) should include("class=\"bok-dashboard-shell bok-term-hub\"")
          _read(
            dir.resolve("website.d/glossary/architecture/runtime.html")
          ) should include("Runtime markdown term brief.")
          _read(
            dir.resolve("website.d/glossary/architecture/runtime.html")
          ) should include("らんたいむ")
        }
      }
    }
  }

  private class TermMetadataRunner extends CozyBok.Runner {
    def run(command: Vector[String], cwd: Path): Unit =
      if (command.take(2) == Vector("dox", "site")) {
        _write(
          cwd.resolve("doxsite.d/metadata/dashboard/site.json"),
          _dashboard_json
        )
        _write(
          cwd.resolve("doxsite.d/metadata/rdf/graph.json"),
          _rdf_graph_json
        )
        _write(
          cwd.resolve("doxsite.d/metadata/glossary/terms.json"),
          _terms_json
        )
        _write(
          cwd.resolve("doxsite.d/site.ttl"),
          "@prefix ex: <https://example.com/> .\n"
        )
        _write(cwd.resolve("doxsite.d/site.jsonld"), "{\"@graph\":[]}\n")
      }
  }

  private class NoTermMetadataRunner extends TermMetadataRunner {
    override def run(command: Vector[String], cwd: Path): Unit =
      if (command.take(2) == Vector("dox", "site")) {
        _write(
          cwd.resolve("doxsite.d/metadata/dashboard/site.json"),
          _dashboard_json
        )
        _write(
          cwd.resolve("doxsite.d/metadata/rdf/graph.json"),
          _rdf_graph_json
        )
        _write(
          cwd.resolve("doxsite.d/site.ttl"),
          "@prefix ex: <https://example.com/> .\n"
        )
        _write(cwd.resolve("doxsite.d/site.jsonld"), "{\"@graph\":[]}\n")
      }
  }

  private def _dashboard_json: String =
    """{
      |  "counts": {"category_count": 1, "article_count": 1, "glossary_term_count": 1, "total_item_count": 2},
      |  "rdf": {"resource_count": 2, "triple_count": 2, "subject_count": 1, "predicate_count": 1},
      |  "increments": {"scale": "day", "buckets": []},
      |  "categories": [
      |    {"name": "architecture", "title": "Architecture", "counts": {"category_count": 0, "article_count": 1, "glossary_term_count": 1, "total_item_count": 2}, "increments": {"scale": "day", "buckets": []}, "rdf": {"resource_count": 2, "triple_count": 2, "subject_count": 1, "predicate_count": 1}}
      |  ]
      |}
      |""".stripMargin

  private def _rdf_graph_json: String =
    """{
      |  "nodes": [
      |    {"id": "https://www.simplemodeling.org/glossary/architecture/runtime", "label": "runtime", "node_type": "uri", "category": "architecture", "degree": 1, "terms": ["architecture:runtime"]},
      |    {"id": "https://example.com/runtime-resource", "label": "runtime-resource", "node_type": "uri", "category": "architecture", "degree": 1, "terms": ["architecture:runtime"]}
      |  ],
      |  "edges": [
      |    {"source": "https://www.simplemodeling.org/glossary/architecture/runtime", "target": "https://example.com/runtime-resource", "predicate": "https://schema.org/about", "label": "about", "category": "architecture", "terms": ["architecture:runtime"]}
      |  ],
      |  "truncated": false
      |}
      |""".stripMargin

  private def _terms_json: String =
    """{
      |  "terms": [
      |    {
      |      "id": "architecture:runtime",
      |      "slug": "runtime",
      |      "title": "Runtime",
      |      "reading": "らんたいむ",
      |      "category": "architecture",
      |      "source_path": "glossary/architecture/runtime.dox",
      |      "public_path": "glossary/architecture/runtime.html",
      |      "definition_html": "<p>Runtime definition from SmartDox metadata.</p>",
      |      "summary": "Runtime summary.",
      |      "aliases": ["Execution Runtime"],
      |      "article_refs": [{"title": "Runtime Article", "path": "architecture/runtime-article.html", "relation": "article"}],
      |      "term_refs": [],
      |      "rdf_refs": [{"resource": "https://example.com/runtime-resource", "label": "runtime-resource", "predicate": "https://schema.org/about", "direction": "outgoing"}],
      |      "video_refs": [{"title": "Runtime Video", "path": "/repository/video/runtime.mp4", "relation": "video"}],
      |      "quality": {"isolated": false, "unreferenced": false, "weakly_connected": false}
      |    }
      |  ]
      |}
      |""".stripMargin

  private def _with_temp_dir[A](prefix: String)(f: Path => A): A = {
    val dir = Files.createTempDirectory(prefix)
    try {
      f(dir)
    } finally {
      _delete(dir)
    }
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
      try {
        stream.iterator.asScala.toVector.reverse.foreach(Files.deleteIfExists)
      } finally {
        stream.close()
      }
    }
}
