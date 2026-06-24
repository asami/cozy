package cozy.bok

import cozy.CozySpecVocabulary

import cozy.bok._
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
class CozyBokBibliographySpec
    extends AnyWordSpec
    with GivenWhenThen
    with CozySpecVocabulary {
  "Cozy BoK bibliography knowledge" should {
    "consume SmartDox bibliography metadata" which {
      "render a Bibliography dashboard and connect Home, Category, and Term Hub navigation" in {
        _with_temp_dir("cozy-bok-bibliography") { dir =>
          Given("a BoK source tree and SmartDox-generated bibliography metadata")
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
          _write(dir.resolve("src/main/doxsite/concept/index.dox"), "Concept\n=======\n")
          _write(
            dir.resolve("src/main/doxsite/glossary/concept/pattern.md"),
            """---
              |title: Pattern
              |brief: Pattern term.
              |---
              |
              |Pattern definition.
              |""".stripMargin
          )
          val config = CozyBok.BuildConfig.create(List(dir.toString, "--strategy", "preview"))

          When("Cozy builds BoK pages from SmartDox machine-readable metadata")
          CozyBok.build(config, new BibliographyMetadataRunner)

          Then("the Bibliography dashboard is generated as a special BoK page")
          val bibliography = _read(dir.resolve("website.d/bibliography/index.html"))
          bibliography should include("Bibliography")
          bibliography should include("Design Patterns")
          bibliography should include("book")
          bibliography should include("DOI 10.5555/design-patterns")
          bibliography should include("Open source")
          bibliography should include("data-bibliography-category=\"concept\"")
          _read(dir.resolve("website.d/metadata/bibliography/bibliography.json")) should include("bib:design-patterns")

          And("each bibliography entry public path resolves to a generated detail page")
          val detail = _read(dir.resolve("website.d/bibliography/concept/design-patterns.html"))
          detail should include("Design Patterns")
          detail should include("DOI 10.5555/design-patterns")
          detail should include("Bibliography")

          And("the Home dashboard exposes reference knowledge as a first-class entry")
          val home = _read(dir.resolve("website.d/index.html"))
          home should include("References")
          home should include("bibliography/index.html")

          And("the Category dashboard links to category-local bibliography entries")
          val category = _read(dir.resolve("website.d/concept/index.html"))
          category should include("../bibliography/index.html?category=concept")
          category should include("1 category references")

          And("the Term Hub shows references related to the term")
          val term = _read(dir.resolve("website.d/glossary/concept/pattern.html"))
          term should include("Related References")
          term should include("Design Patterns")
          term should include("../../bibliography/concept/design-patterns.html")
        }
      }

      "fail when the external SmartDox runtime does not hand off declared bibliography references" in {
        _with_temp_dir("cozy-bok-bibliography-missing-handoff") { dir =>
          Given("a BoK source that declares a bibliography reference")
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
          _write(
            dir.resolve("src/main/doxsite/technology/index.md"),
            """---
              |title: Technology
              |bibliography:
              |  refs:
              |    - openlibrary:works/OL31219436W
              |---
              |
              |# Technology
              |
              |This category cites an external bibliography id.
              |""".stripMargin
          )
          val config = CozyBok.BuildConfig.create(List(dir.toString, "--strategy", "preview"))

          When("Cozy builds with a SmartDox runner that emits an empty bibliography handoff")
          val error = intercept[Throwable] {
            CozyBok.build(config, new EmptyBibliographyMetadataRunner)
          }

          Then("Cozy reports the SmartDox runtime boundary instead of regenerating the handoff")
          error.getMessage should include("SmartDox bibliography metadata was not generated")
          error.getMessage should include("Update the dox/SmartDox runtime")
        }
      }
    }

    "search external reference providers" which {
      "render deterministic stub provider results without modifying BoK source" in {
        Given("a stub bibliography provider for a known title search")
        val provider = new BibliographySearchProvider {
          val name = "crossref"
          def search(query: String, limit: Int): Vector[BibliographySearchResult] =
            Vector(
              BibliographySearchResult(
                "crossref",
                "doi:10.5555/design-patterns",
                "gamma1994designpatterns",
                "Design Patterns",
                Vector("Erich Gamma", "Richard Helm"),
                Some("1994"),
                Some("10.5555/design-patterns"),
                Some("9780201633610"),
                Some("https://example.com/design-patterns"),
                "book"
              )
            ).take(limit)
        }

        When("Cozy searches bibliography candidates through the injected registry")
        val text = CozyBok.searchBibliography(
          BibliographySearchConfig("Design Patterns", "crossref", 5, "text"),
          BibliographySearchRegistry(Vector(provider))
        )
        val json = CozyBok.searchBibliography(
          BibliographySearchConfig("Design Patterns", "crossref", 5, "json"),
          BibliographySearchRegistry(Vector(provider))
        )

        Then("the text output gives a usable bib id and BibTeX key candidate")
        text should include("Design Patterns")
        text should include("bib-id: doi:10.5555/design-patterns")
        text should include("bibtex-key: gamma1994designpatterns")
        text should include("DOI 10.5555/design-patterns")

        And("the JSON output remains deterministic for tool consumption")
        json should include("\"candidates\"")
        json should include("\"bibtex_key\": \"gamma1994designpatterns\"")
        json should include("\"isbn\": \"9780201633610\"")
      }

      "mix provider candidates when searching all providers" in {
        Given("two bibliography providers that both return useful but different candidates")
        val crossref = new BibliographySearchProvider {
          val name = "crossref"
          def search(query: String, limit: Int): Vector[BibliographySearchResult] =
            Vector(BibliographySearchResult("crossref", "doi:10.5555/patterns", "ref1994patterns", "Patterns Article", Vector("A. Author"), Some("1994"), Some("10.5555/patterns"), None, None, "article"))
        }
        val openlibrary = new BibliographySearchProvider {
          val name = "openlibrary"
          def search(query: String, limit: Int): Vector[BibliographySearchResult] =
            Vector(BibliographySearchResult("openlibrary", "openlibrary:works/OL123W", "gamma1995designpatterns", "Design Patterns", Vector("Erich Gamma"), Some("1995"), None, Some("9780201633610"), None, "book"))
        }

        When("Cozy searches all providers with a limit that previously could be filled by the first provider")
        val text = CozyBok.searchBibliography(
          BibliographySearchConfig("Design Patterns", "all", 2, "text"),
          BibliographySearchRegistry(Vector(crossref, openlibrary))
        )

        Then("the output keeps candidates from multiple providers")
        text should include("crossref: Patterns Article")
        text should include("openlibrary: Design Patterns")
        text should include("bib-id: openlibrary:works/OL123W")
      }
    }

    "update bibliography cache" which {
      "parse nested BibTeX values used by external bibliography providers" in {
        Given("BibTeX with nested braces in the title field")
        val bibtex =
          """@book{gamma1995designpatterns,
            |  title = {Design Patterns: {Elements} of Reusable Object-Oriented Software},
            |  author = {Gamma, Erich and Helm, Richard},
            |  year = {1995}
            |}
            |""".stripMargin

        When("Cozy parses the cached BibTeX")
        val fields = BibliographyBibtexParser.parse(bibtex).getOrElse(Map.empty)

        Then("the nested title content remains part of the parsed field")
        fields("title") should include("Design Patterns")
        fields("title") should include("{Elements}")
        fields("author") should include("Gamma, Erich")
      }

      "fetch unresolved bib ids without rewriting source documents" in {
        _with_temp_dir("cozy-bok-bibliography-unresolved-cache") { dir =>
          Given("existing bibliography metadata with an unresolved DOI reference")
          _write(dir.resolve("doxsite.d/metadata/bibliography/bibliography.json"), _unresolved_bibliography_json)
          val source = _write(dir.resolve("src/main/doxsite/technology/article.md"), "---\nbibliography:\n  refs:\n    - doi:10.5555/design-patterns\n---\n")
          val before = _read(source)
          val fetcher = new BibliographyBibtexFetcher {
            def fetch(sourceurl: String): Option[String] = None
            override def fetchBibId(bibid: String): Option[String] =
              Some("@book{gamma1995designpatterns, title={Design Patterns}, author={Gamma, Erich and Helm, Richard}, year={1995}, isbn={9780201633610}}\n")
          }

          When("Cozy updates bibliography cache for unresolved bib ids")
          val output = CozyBok.updateBibliography(BibliographyUpdateConfig(dir, force = false), fetcher)

          Then("the cache is written and the source file remains unchanged")
          output should include("target/cozy-bok/bibliography/cache/doi-10-5555-design-patterns.bib")
          _read(dir.resolve("target/cozy-bok/bibliography/cache/doi-10-5555-design-patterns.bib")) should include("@book{gamma1995designpatterns")
          _read(source) shouldBe before
        }
      }

      "use cached BibTeX to materialize effective bibliography during build" in {
        _with_temp_dir("cozy-bok-bibliography-effective-cache") { dir =>
          Given("an unresolved SmartDox bibliography reference and a pre-existing bibliography cache")
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
          _write(dir.resolve("src/main/doxsite/technology/index.dox"), "Technology\n==========\n")
          _write(dir.resolve("target/cozy-bok/bibliography/cache/doi-10-5555-design-patterns.bib"), "@book{gamma1995designpatterns, title={Design Patterns}, author={Gamma, Erich and Helm, Richard}, year={1995}, isbn={9780201633610}}\n")
          val config = CozyBok.BuildConfig.create(List(dir.toString, "--strategy", "preview"))

          When("Cozy builds pages after SmartDox emits the unresolved reference")
          CozyBok.build(config, new UnresolvedBibliographyMetadataRunner)

          Then("the generated bibliography metadata and dashboard use cached effective fields")
          val metadata = _read(dir.resolve("website.d/metadata/bibliography/bibliography.json"))
          metadata should include("Design Patterns")
          metadata should include("\"source_kind\" : \"external-cache\"")
          metadata should include("\"needs_resolution\" : false")
          _read(dir.resolve("website.d/bibliography/index.html")) should include("Design Patterns")
        }
      }

      "fetch explicit BibTeX source URLs without rewriting source documents" in {
        _with_temp_dir("cozy-bok-bibliography-cache") { dir =>
          Given("existing bibliography metadata with a BibTeX source URL")
          _write(dir.resolve("doxsite.d/metadata/bibliography/bibliography.json"), _bibliography_json)
          val source = _write(dir.resolve("src/main/doxsite/bibliography/concept/design-patterns.dox"), "Design Patterns\n===============\n")
          val before = _read(source)
          val fetcher = new BibliographyBibtexFetcher {
            def fetch(sourceurl: String): Option[String] =
              Some("@book{gamma1994designpatterns, title={Design Patterns}}\n")
          }

          When("Cozy updates the bibliography cache through the injected fetcher")
          val output = CozyBok.updateBibliography(BibliographyUpdateConfig(dir, force = false), fetcher)

          Then("the cache is written under target and the source file remains unchanged")
          output should include("target/cozy-bok/bibliography/cache/bib-design-patterns.bib")
          _read(dir.resolve("target/cozy-bok/bibliography/cache/bib-design-patterns.bib")) should include("@book{gamma1994designpatterns")
          _read(source) shouldBe before
        }
      }
    }
  }

  private class UnresolvedBibliographyMetadataRunner extends CozyBok.Runner {
    def run(command: Vector[String], cwd: Path): Unit =
      if (command.take(2) == Vector("dox", "site")) {
        _write(cwd.resolve("doxsite.d/metadata/dashboard/site.json"), _dashboard_json)
        _write(cwd.resolve("doxsite.d/metadata/rdf/graph.json"), _rdf_graph_json)
        _write(cwd.resolve("doxsite.d/metadata/glossary/terms.json"), "{\"terms\": []}\n")
        _write(cwd.resolve("doxsite.d/metadata/bibliography/bibliography.json"), _unresolved_bibliography_json)
        _write(cwd.resolve("doxsite.d/site.ttl"), "@prefix ex: <https://example.com/> .\n")
        _write(cwd.resolve("doxsite.d/site.jsonld"), "{\"@graph\":[]}\n")
      }
  }

  private class EmptyBibliographyMetadataRunner extends CozyBok.Runner {
    def run(command: Vector[String], cwd: Path): Unit =
      if (command.take(2) == Vector("dox", "site")) {
        _write(cwd.resolve("doxsite.d/metadata/dashboard/site.json"), _dashboard_json)
        _write(cwd.resolve("doxsite.d/metadata/rdf/graph.json"), _rdf_graph_json)
        _write(cwd.resolve("doxsite.d/metadata/glossary/terms.json"), "{\"terms\": []}\n")
        _write(cwd.resolve("doxsite.d/metadata/bibliography/bibliography.json"), "{\"entries\": []}\n")
        _write(cwd.resolve("doxsite.d/site.ttl"), "@prefix ex: <https://example.com/> .\n")
        _write(cwd.resolve("doxsite.d/site.jsonld"), "{\"@graph\":[]}\n")
      }
  }

  private class BibliographyMetadataRunner extends CozyBok.Runner {
    def run(command: Vector[String], cwd: Path): Unit =
      if (command.take(2) == Vector("dox", "site")) {
        _write(cwd.resolve("doxsite.d/metadata/dashboard/site.json"), _dashboard_json)
        _write(cwd.resolve("doxsite.d/metadata/rdf/graph.json"), _rdf_graph_json)
        _write(cwd.resolve("doxsite.d/metadata/glossary/terms.json"), _terms_json)
        _write(cwd.resolve("doxsite.d/metadata/bibliography/bibliography.json"), _bibliography_json)
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
      |    "id": "concept:pattern",
      |    "slug": "pattern",
      |    "title": "Pattern",
      |    "reading": null,
      |    "category": "concept",
      |    "source_path": "glossary/concept/pattern.md",
      |    "public_path": "glossary/concept/pattern.html",
      |    "definition_html": "<p>Pattern definition.</p>",
      |    "summary": "Pattern term.",
      |    "aliases": [],
      |    "article_refs": [],
      |    "term_refs": [],
      |    "rdf_refs": [],
      |    "video_refs": [],
      |    "quality": {
      |      "isolated": false,
      |      "unreferenced": false,
      |      "weakly_connected": false
      |    }
      |  }]
      |}
      |""".stripMargin

  private def _unresolved_bibliography_json: String =
    """{
      |  "entries": [{
      |    "id": "doi:10.5555/design-patterns",
      |    "slug": "doi-10-5555-design-patterns",
      |    "entry_type": "article",
      |    "title": "doi:10.5555/design-patterns",
      |    "summary": "Unresolved bibliography reference.",
      |    "category": "technology",
      |    "source_path": "technology/article.md",
      |    "public_path": "bibliography/technology/doi-10-5555-design-patterns.html",
      |    "authors": [],
      |    "terms": [],
      |    "identifiers": {"doi": "10.5555/design-patterns"},
      |    "bibtex": {},
      |    "body_html": "",
      |    "source_kind": "external-ref",
      |    "refs": ["doi:10.5555/design-patterns"],
      |    "needs_resolution": true,
      |    "quality": {"missing_citation": true, "missing_terms": true, "missing_source": false, "missing_narrative": true, "needs_curation": true}
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
      |    "summary": "Reusable object-oriented design catalog.",
      |    "category": "concept",
      |    "source_path": "bibliography/concept/design-patterns.dox",
      |    "public_path": "bibliography/concept/design-patterns.html",
      |    "authors": ["Erich Gamma", "Richard Helm"],
      |    "published_at": "1994-10-21",
      |    "publisher": "Addison-Wesley",
      |    "source_url": "https://example.com/design-patterns",
      |    "accessed_at": "2026-06-24",
      |    "terms": ["concept:pattern"],
      |    "citation": "Gamma et al. Design Patterns.",
      |    "identifiers": {
      |      "doi": "10.5555/design-patterns",
      |      "isbn": "9780201633610"
      |    },
      |    "bibtex": {
      |      "key": "gamma1994designpatterns",
      |      "entry_type": "book",
      |      "source_url": "https://example.com/design-patterns.bib"
      |    },
      |    "body_html": "<p>A reference book for design patterns.</p>",
      |    "quality": {}
      |  }]
      |}
      |""".stripMargin

  private def _with_temp_dir[A](prefix: String)(f: Path => A): A = {
    val dir = Files.createTempDirectory(prefix)
    try {
      f(dir)
    } finally {
      val stream = Files.walk(dir)
      try {
        stream.iterator.asScala.toVector.reverse.foreach(Files.deleteIfExists)
      } finally {
        stream.close()
      }
    }
  }

  private def _write(path: Path, content: String): Path = {
    Option(path.getParent).foreach(Files.createDirectories(_))
    Files.write(path, content.getBytes(StandardCharsets.UTF_8))
    path
  }

  private def _read(path: Path): String =
    new String(Files.readAllBytes(path), StandardCharsets.UTF_8)
}
