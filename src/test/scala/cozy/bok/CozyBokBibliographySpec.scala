package cozy.bok

import cozy.CozySpecVocabulary

import cozy.bok._
import java.io.ByteArrayOutputStream
import java.io.PrintStream
import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path}
import scala.collection.JavaConverters._
import org.scalatest.GivenWhenThen
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Jun. 24, 2026
 * @version Jun. 25, 2026
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
          val output = CozyBok.updateBibliography(BibliographyUpdateConfig(dir, force = false, reportonly = false), fetcher)

          Then("the cache is written and the source file remains unchanged")
          output should include("target/cozy-bok/bibliography/cache/doi-10-5555-design-patterns.bib")
          _read(dir.resolve("target/cozy-bok/bibliography/cache/doi-10-5555-design-patterns.bib")) should include("@book{gamma1995designpatterns")
          _read(source) shouldBe before
        }
      }

      "resolve uncached external bib ids during normal build" in {
        _with_temp_dir("cozy-bok-bibliography-build-fetch") { dir =>
          Given("an unresolved SmartDox bibliography reference without a pre-existing cache")
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
          val fetcher = new RecordingBibtexFetcher(Some("@book{gamma1995designpatterns, title={Design Patterns}, author={Gamma, Erich and Helm, Richard}, year={1995}, isbn={9780201633610}}\n"))
          val config = CozyBok.BuildConfig.create(List(dir.toString, "--strategy", "preview"))

          When("Cozy builds through SmartDox and resolves bibliography metadata through the injected fetcher")
          CozyBok.build(config, new UnresolvedBibliographyMetadataRunner, fetcher)

          Then("the unresolved bib id is fetched into cache and rendered as effective bibliography")
          fetcher.bibids should contain("doi:10.5555/design-patterns")
          _read(dir.resolve("target/cozy-bok/bibliography/cache/doi-10-5555-design-patterns.bib")) should include("@book{gamma1995designpatterns")
          val metadata = _read(dir.resolve("website.d/metadata/bibliography/bibliography.json"))
          metadata should include("Design Patterns")
          metadata should include("\"source_kind\" : \"external-cache\"")
          metadata should include("\"needs_resolution\" : false")
          _read(dir.resolve("website.d/bibliography/index.html")) should include("Design Patterns")
        }
      }

      "resolve BoK bibliography .bib sources before external providers during build" in {
        _with_temp_dir("cozy-bok-bibliography-build-local-bib") { dir =>
          Given("an unresolved bibliography id and a matching .bib file in the BoK bibliography source tree")
          _write(dir.resolve("src/main/doxsite/site.conf"), "site.output.locale_mode = single_locale_root\n")
          _write(dir.resolve("src/main/doxsite/concept/category.yaml"), "name: Concept\ntitle: Concept\n")
          _write(dir.resolve("src/main/doxsite/concept/index.dox"), "Concept\n=======\n")
          _write(
            dir.resolve("src/main/doxsite/bibliography/concept/design-patterns.bib"),
            "@book{design-patterns, title={Design Patterns}, author={Gamma, Erich and Helm, Richard}, year={1994}}\n"
          )
          val config = CozyBok.BuildConfig.create(List(dir.toString, "--strategy", "preview"))

          When("Cozy builds with the default bibliography resolver")
          CozyBok.build(config, new LocalBibidBibliographyMetadataRunner)

          Then("the local .bib entry is cached and rendered as effective bibliography")
          _read(dir.resolve("target/cozy-bok/bibliography/cache/bib-design-patterns.bib")) should include("@book{design-patterns")
          val metadata = _read(dir.resolve("website.d/metadata/bibliography/bibliography.json"))
          metadata should include("Design Patterns")
          metadata should include("\"source_kind\" : \"external-cache\"")
          metadata should include("\"needs_resolution\" : false")
        }
      }

      "prefer category BoK bibliography .bib sources over global BoK bibliography .bib sources" in {
        _with_temp_dir("cozy-bok-bibliography-build-category-bib") { dir =>
          Given("an unresolved category bibliography id and both global and category BibTeX source files")
          _write(dir.resolve("src/main/doxsite/site.conf"), "site.output.locale_mode = single_locale_root\n")
          _write(dir.resolve("src/main/doxsite/concept/category.yaml"), "name: Concept\ntitle: Concept\n")
          _write(dir.resolve("src/main/doxsite/concept/index.dox"), "Concept\n=======\n")
          _write(
            dir.resolve("src/main/doxsite/bibliography/design-patterns.bib"),
            "@book{design-patterns, title={Global Design Patterns}, author={Global Author}, year={1994}}\n"
          )
          _write(
            dir.resolve("src/main/doxsite/bibliography/concept/design-patterns.bib"),
            "@book{design-patterns, title={Category Design Patterns}, author={Category Author}, year={1994}}\n"
          )
          val config = CozyBok.BuildConfig.create(List(dir.toString, "--strategy", "preview"))

          When("Cozy resolves bibliography during the build")
          CozyBok.build(config, new LocalBibidBibliographyMetadataRunner)

          Then("the category BibTeX source is used before the global source")
          val cache = _read(dir.resolve("target/cozy-bok/bibliography/cache/bib-design-patterns.bib"))
          cache should include("Category Design Patterns")
          cache should not include("Global Design Patterns")
        }
      }

      "supplement curated .bib.dox bibliography entries from matching local BibTeX without replacing curated metadata" in {
        _with_temp_dir("cozy-bok-bibliography-bib-dox-supplement") { dir =>
          Given("a curated bibliography .bib.dox handoff and a matching local BibTeX source")
          _write(dir.resolve("src/main/doxsite/site.conf"), "site.output.locale_mode = single_locale_root\n")
          _write(dir.resolve("src/main/doxsite/concept/category.yaml"), "name: Concept\ntitle: Concept\n")
          _write(dir.resolve("src/main/doxsite/concept/index.dox"), "Concept\n=======\n")
          _write(
            dir.resolve("src/main/doxsite/bibliography/concept/design-patterns.bib"),
            "@book{design-patterns, title={BibTeX Design Patterns}, author={Gamma, Erich and Helm, Richard}, year={1994}, isbn={9780201633610}}\n"
          )
          val config = CozyBok.BuildConfig.create(List(dir.toString, "--strategy", "preview"))

          When("Cozy builds effective bibliography metadata")
          CozyBok.build(config, new BibDoxBibliographyMetadataRunner)

          Then("the curated title remains authoritative")
          val metadata = _read(dir.resolve("website.d/metadata/bibliography/bibliography.json"))
          metadata should include("\"title\" : \"Curated Design Patterns\"")
          metadata should not include("\"title\" : \"BibTeX Design Patterns\"")

          And("the BibTeX fields supplement missing author and identifier data")
          metadata should include("Gamma, Erich")
          metadata should include("9780201633610")
          metadata should include("\"needs_resolution\" : false")
        }
      }

      "fail normal build when external bib ids cannot be resolved" in {
        _with_temp_dir("cozy-bok-bibliography-build-fetch-failure") { dir =>
          Given("an unresolved SmartDox bibliography reference and an unavailable bibliography service")
          _write(dir.resolve("src/main/doxsite/site.conf"), "site.output.locale_mode = single_locale_root\n")
          _write(dir.resolve("src/main/doxsite/technology/category.yaml"), "name: Technology\ntitle: Technology\n")
          _write(dir.resolve("src/main/doxsite/technology/index.dox"), "Technology\n==========\n")
          val config = CozyBok.BuildConfig.create(List(dir.toString, "--strategy", "preview"))

          When("Cozy builds with bibliography service enabled")
          val error = intercept[Throwable] {
            CozyBok.build(config, new UnresolvedBibliographyMetadataRunner, new RecordingBibtexFetcher(None))
          }

          Then("the unresolved bibliography reference is a build failure")
          error.getMessage should include("unresolved bibliography reference: doi:10.5555/design-patterns")
          error.getMessage should include("--no-bib-service")
        }
      }

      "warn and finish offline build when external bib ids are not cached" in {
        _with_temp_dir("cozy-bok-bibliography-build-offline") { dir =>
          Given("an unresolved SmartDox bibliography reference and offline bibliography mode")
          _write(dir.resolve("src/main/doxsite/site.conf"), "site.output.locale_mode = single_locale_root\n")
          _write(dir.resolve("src/main/doxsite/technology/category.yaml"), "name: Technology\ntitle: Technology\n")
          _write(dir.resolve("src/main/doxsite/technology/index.dox"), "Technology\n==========\n")
          val fetcher = new RecordingBibtexFetcher(Some("@book{gamma1995designpatterns, title={Design Patterns}}\n"))
          val config = CozyBok.BuildConfig.create(List(dir.toString, "--strategy", "preview", "--no-bib-service"))

          When("Cozy builds without bibliography service")
          val output = _capture {
            CozyBok.build(config, new UnresolvedBibliographyMetadataRunner, fetcher)
          }

          Then("the build succeeds with a warning and does not call the fetcher")
          output should include("warning: unresolved bibliography reference: doi:10.5555/design-patterns")
          fetcher.bibids shouldBe empty
          _read(dir.resolve("website.d/metadata/bibliography/bibliography.json")) should include("\"needs_resolution\" : true")
          _read(dir.resolve("website.d/bibliography/index.html")) should include("Unresolved")
        }
      }

      "materialize embedded raw BibTeX during offline build without calling external providers" in {
        _with_temp_dir("cozy-bok-bibliography-build-offline-raw") { dir =>
          Given("SmartDox bibliography metadata containing raw BibTeX")
          _write(dir.resolve("src/main/doxsite/site.conf"), "site.output.locale_mode = single_locale_root\n")
          _write(dir.resolve("src/main/doxsite/concept/category.yaml"), "name: Concept\ntitle: Concept\n")
          _write(dir.resolve("src/main/doxsite/concept/index.dox"), "Concept\n=======\n")
          val fetcher = new RecordingBibtexFetcher(Some("@book{unexpected, title={Unexpected}}\n"))
          val config = CozyBok.BuildConfig.create(List(dir.toString, "--strategy", "preview", "--no-bib-service"))

          When("Cozy builds without bibliography service")
          val output = _capture {
            CozyBok.build(config, new BibliographyRawMetadataRunner, fetcher)
          }

          Then("the embedded BibTeX is cached and applied without external fetches or warnings")
          output should not include("warning: unresolved bibliography reference")
          fetcher.urls shouldBe empty
          fetcher.bibids shouldBe empty
          _read(dir.resolve("target/cozy-bok/bibliography/cache/bib-design-patterns.bib")) should include("@book{gamma1994designpatterns")
          val metadata = _read(dir.resolve("website.d/metadata/bibliography/bibliography.json"))
          metadata should include("\"key\" : \"gamma1994designpatterns\"")
          metadata should include("\"needs_resolution\" : false")
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
          CozyBok.build(config, new UnresolvedBibliographyMetadataRunner, new FailingBibtexFetcher)

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
          _write(dir.resolve("doxsite.d/metadata/bibliography/bibliography.json"), _bibliography_source_url_json)
          val source = _write(dir.resolve("src/main/doxsite/bibliography/concept/design-patterns.dox"), "Design Patterns\n===============\n")
          val before = _read(source)
          val fetcher = new BibliographyBibtexFetcher {
            def fetch(sourceurl: String): Option[String] =
              Some("@book{gamma1994designpatterns, title={Design Patterns}}\n")
          }

          When("Cozy updates the bibliography cache through the injected fetcher")
          val output = CozyBok.updateBibliography(BibliographyUpdateConfig(dir, force = false, reportonly = false), fetcher)

          Then("the cache is written under target and the source file remains unchanged")
          output should include("target/cozy-bok/bibliography/cache/bib-design-patterns.bib")
          _read(dir.resolve("target/cozy-bok/bibliography/cache/bib-design-patterns.bib")) should include("@book{gamma1994designpatterns")
          _read(source) shouldBe before
        }
      }

      "report missing bibliography cache entries without fetching" in {
        _with_temp_dir("cozy-bok-bibliography-report-only") { dir =>
          Given("existing bibliography metadata with an unresolved DOI reference")
          _write(dir.resolve("doxsite.d/metadata/bibliography/bibliography.json"), _unresolved_bibliography_json)
          val fetcher = new RecordingBibtexFetcher(Some("@book{gamma1995designpatterns, title={Design Patterns}}\n"))

          When("Cozy checks bibliography cache in report-only mode")
          val output = CozyBok.updateBibliography(BibliographyUpdateConfig(dir, force = false, reportonly = true), fetcher)

          Then("the missing bib id is reported without writing cache or calling the fetcher")
          output should include("bibliography cache missing: doi:10.5555/design-patterns")
          fetcher.bibids shouldBe empty
          Files.exists(dir.resolve("target/cozy-bok/bibliography/cache/doi-10-5555-design-patterns.bib")) shouldBe false
        }
      }

      "report explicit BibTeX source URL failures instead of claiming no updates" in {
        _with_temp_dir("cozy-bok-bibliography-source-url-failure") { dir =>
          Given("existing bibliography metadata with a BibTeX source URL that cannot be fetched")
          _write(dir.resolve("doxsite.d/metadata/bibliography/bibliography.json"), _bibliography_source_url_json)
          val fetcher = new RecordingBibtexFetcher(None)

          When("Cozy updates bibliography cache through the unavailable source")
          val output = CozyBok.updateBibliography(BibliographyUpdateConfig(dir, force = false, reportonly = false), fetcher)

          Then("the unresolved cache entry is reported explicitly")
          output should include("bibliography cache unresolved: bib:design-patterns")
          output should not include("bibliography cache: no updates")
          fetcher.urls should contain("https://example.com/design-patterns.bib")
        }
      }

      "resolve repository .bib sources through the update-bibliography CLI" in {
        _with_temp_dir("cozy-bok-bibliography-update-repository-bib") { dir =>
          Given("an unresolved bibliography id and a matching .bib file in the configured repository")
          _write(dir.resolve("conf/cozy/config.yaml"), "bok:\n  repository: repository\n")
          _write(dir.resolve("doxsite.d/metadata/bibliography/bibliography.json"), _local_bibid_bibliography_json)
          _write(
            dir.resolve("repository/bibliography/design-patterns.bib"),
            "@book{design-patterns, title={Design Patterns}, author={Gamma, Erich and Helm, Richard}, year={1994}}\n"
          )

          When("Cozy updates bibliography through the public CLI path")
          val output = _capture {
            CozyBok.execute(List("bok", "update-bibliography", dir.toString))
          }

          Then("the repository .bib entry is used as a resolver source before external providers")
          output should include("target/cozy-bok/bibliography/cache/bib-design-patterns.bib")
          _read(dir.resolve("target/cozy-bok/bibliography/cache/bib-design-patterns.bib")) should include("@book{design-patterns")
        }
      }

      "prefer repository bibliography .bib sources over repository catalog bibliography .bib sources" in {
        _with_temp_dir("cozy-bok-bibliography-update-repository-precedence") { dir =>
          Given("matching BibTeX entries in repository bibliography and catalog bibliography")
          _write(dir.resolve("conf/cozy/config.yaml"), "bok:\n  repository: repository\n")
          _write(dir.resolve("doxsite.d/metadata/bibliography/bibliography.json"), _local_bibid_bibliography_json)
          _write(
            dir.resolve("repository/bibliography/design-patterns.bib"),
            "@book{design-patterns, title={Repository Design Patterns}, author={Repository Author}, year={1994}}\n"
          )
          _write(
            dir.resolve("repository/catalog/bibliography/design-patterns.bib"),
            "@book{design-patterns, title={Catalog Design Patterns}, author={Catalog Author}, year={1994}}\n"
          )

          When("Cozy updates bibliography through the public CLI path")
          _capture {
            CozyBok.execute(List("bok", "update-bibliography", dir.toString))
          }

          Then("the repository bibliography source is used before catalog bibliography")
          val cache = _read(dir.resolve("target/cozy-bok/bibliography/cache/bib-design-patterns.bib"))
          cache should include("Repository Design Patterns")
          cache should not include("Catalog Design Patterns")
        }
      }

      "resolve repository catalog bibliography .bib sources as local resolver fallback" in {
        _with_temp_dir("cozy-bok-bibliography-update-repository-catalog") { dir =>
          Given("an unresolved bibliography id and a matching .bib file in repository catalog bibliography")
          _write(dir.resolve("conf/cozy/config.yaml"), "bok:\n  repository: repository\n")
          _write(dir.resolve("doxsite.d/metadata/bibliography/bibliography.json"), _local_bibid_bibliography_json)
          _write(
            dir.resolve("repository/catalog/bibliography/design-patterns.bib"),
            "@book{design-patterns, title={Catalog Design Patterns}, author={Catalog Author}, year={1994}}\n"
          )

          When("Cozy updates bibliography through the public CLI path")
          val output = _capture {
            CozyBok.execute(List("bok", "update-bibliography", dir.toString))
          }

          Then("the catalog bibliography source is used before external providers")
          output should include("target/cozy-bok/bibliography/cache/bib-design-patterns.bib")
          _read(dir.resolve("target/cozy-bok/bibliography/cache/bib-design-patterns.bib")) should include("Catalog Design Patterns")
        }
      }

      "ignore broad repository root and project root bibliography .bib files" in {
        _with_temp_dir("cozy-bok-bibliography-update-repository-boundary") { dir =>
          Given("matching BibTeX files outside the approved resolver source directories")
          _write(dir.resolve("conf/cozy/config.yaml"), "bok:\n  repository: repository\n")
          _write(dir.resolve("doxsite.d/metadata/bibliography/bibliography.json"), _local_bibid_bibliography_json)
          _write(
            dir.resolve("repository/design-patterns.bib"),
            "@book{design-patterns, title={Repository Root Design Patterns}}\n"
          )
          _write(
            dir.resolve("bibliography/design-patterns.bib"),
            "@book{design-patterns, title={Project Root Design Patterns}}\n"
          )

          When("Cozy updates bibliography through the public CLI path")
          val output = _capture {
            CozyBok.execute(List("bok", "update-bibliography", dir.toString))
          }

          Then("the broad repository root and project root bibliography files are not resolver sources")
          output should include("bibliography cache unresolved: bib:design-patterns")
          Files.exists(dir.resolve("target/cozy-bok/bibliography/cache/bib-design-patterns.bib")) shouldBe false
        }
      }

      "resolve explicit source URLs only from approved local bibliography paths" in {
        _with_temp_dir("cozy-bok-bibliography-update-source-paths") { dir =>
          Given("bibliography metadata pointing at repository catalog bibliography")
          _write(dir.resolve("conf/cozy/config.yaml"), "bok:\n  repository: repository\n")
          _write(dir.resolve("doxsite.d/metadata/bibliography/bibliography.json"), _bibliography_source_url_json("repository/catalog/bibliography/design-patterns.bib"))
          _write(
            dir.resolve("repository/catalog/bibliography/design-patterns.bib"),
            "@book{gamma1994designpatterns, title={Catalog Design Patterns}}\n"
          )

          When("Cozy updates bibliography through the public CLI path")
          val output = _capture {
            CozyBok.execute(List("bok", "update-bibliography", dir.toString))
          }

          Then("the approved repository catalog source URL resolves")
          output should include("target/cozy-bok/bibliography/cache/bib-design-patterns.bib")
          _read(dir.resolve("target/cozy-bok/bibliography/cache/bib-design-patterns.bib")) should include("Catalog Design Patterns")
        }
      }

      "reject explicit source URLs that escape approved local bibliography roots" in {
        _with_temp_dir("cozy-bok-bibliography-update-source-path-escape") { dir =>
          Given("bibliography metadata pointing through an approved prefix to an unapproved repository root file")
          _write(dir.resolve("conf/cozy/config.yaml"), "bok:\n  repository: repository\n")
          _write(dir.resolve("doxsite.d/metadata/bibliography/bibliography.json"), _bibliography_source_url_json("repository/bibliography/../design-patterns.bib"))
          _write(
            dir.resolve("repository/design-patterns.bib"),
            "@book{gamma1994designpatterns, title={Repository Root Design Patterns}}\n"
          )

          When("Cozy updates bibliography through the public CLI path")
          val output = _capture {
            CozyBok.execute(List("bok", "update-bibliography", dir.toString))
          }

          Then("the path traversal source URL is rejected as outside the approved resolver roots")
          output should include("bibliography cache unresolved: bib:design-patterns")
          Files.exists(dir.resolve("target/cozy-bok/bibliography/cache/bib-design-patterns.bib")) shouldBe false
        }
      }

      "treat embedded raw BibTeX as locally complete in report-only mode" in {
        _with_temp_dir("cozy-bok-bibliography-report-only-raw") { dir =>
          Given("existing bibliography metadata with embedded raw BibTeX")
          _write(dir.resolve("doxsite.d/metadata/bibliography/bibliography.json"), _bibliography_raw_json)
          val fetcher = new RecordingBibtexFetcher(Some("@book{unexpected, title={Unexpected}}\n"))

          When("Cozy checks bibliography cache in report-only mode")
          val output = CozyBok.updateBibliography(BibliographyUpdateConfig(dir, force = false, reportonly = true), fetcher)

          Then("no external cache is reported missing because the BibTeX is already local metadata")
          output should include("bibliography cache: complete")
          fetcher.urls shouldBe empty
          fetcher.bibids shouldBe empty
        }
      }

      "resolve explicit BibTeX source URLs during normal build" in {
        _with_temp_dir("cozy-bok-bibliography-build-source-url") { dir =>
          Given("SmartDox bibliography metadata with an explicit BibTeX source URL")
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
          _write(dir.resolve("src/main/doxsite/concept/category.yaml"), "name: Concept\ntitle: Concept\n")
          _write(dir.resolve("src/main/doxsite/concept/index.dox"), "Concept\n=======\n")
          val fetcher = new RecordingBibtexFetcher(Some("@book{gamma1994designpatterns, title={Design Patterns}, author={Gamma, Erich and Helm, Richard}, year={1994}}\n"))
          val config = CozyBok.BuildConfig.create(List(dir.toString, "--strategy", "preview"))

          When("Cozy builds with bibliography service enabled")
          CozyBok.build(config, new BibliographySourceUrlMetadataRunner, fetcher)

          Then("the BibTeX source URL is fetched into cache and applied to effective metadata")
          fetcher.urls should contain("https://example.com/design-patterns.bib")
          _read(dir.resolve("target/cozy-bok/bibliography/cache/bib-design-patterns.bib")) should include("@book{gamma1994designpatterns")
          val metadata = _read(dir.resolve("website.d/metadata/bibliography/bibliography.json"))
          metadata should include("\"key\" : \"gamma1994designpatterns\"")
          metadata should include("\"needs_resolution\" : false")
        }
      }
    }
  }

  private class RecordingBibtexFetcher(result: Option[String]) extends BibliographyBibtexFetcher {
    var urls = Vector.empty[String]
    var bibids = Vector.empty[String]
    def fetch(sourceurl: String): Option[String] = {
      urls :+= sourceurl
      result
    }
    override def fetchBibId(bibid: String): Option[String] = {
      bibids :+= bibid
      result
    }
  }

  private class FailingBibtexFetcher extends BibliographyBibtexFetcher {
    def fetch(sourceurl: String): Option[String] =
      throw new AssertionError(s"Unexpected bibliography source URL fetch: ${sourceurl}")
    override def fetchBibId(bibid: String): Option[String] =
      throw new AssertionError(s"Unexpected bibliography id fetch: ${bibid}")
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

  private class BibliographySourceUrlMetadataRunner extends CozyBok.Runner {
    def run(command: Vector[String], cwd: Path): Unit =
      if (command.take(2) == Vector("dox", "site")) {
        _write(cwd.resolve("doxsite.d/metadata/dashboard/site.json"), _dashboard_json)
        _write(cwd.resolve("doxsite.d/metadata/rdf/graph.json"), _rdf_graph_json)
        _write(cwd.resolve("doxsite.d/metadata/glossary/terms.json"), "{\"terms\": []}\n")
        _write(cwd.resolve("doxsite.d/metadata/bibliography/bibliography.json"), _bibliography_source_url_json)
        _write(cwd.resolve("doxsite.d/site.ttl"), "@prefix ex: <https://example.com/> .\n")
        _write(cwd.resolve("doxsite.d/site.jsonld"), "{\"@graph\":[]}\n")
      }
  }

  private class LocalBibidBibliographyMetadataRunner extends CozyBok.Runner {
    def run(command: Vector[String], cwd: Path): Unit =
      if (command.take(2) == Vector("dox", "site")) {
        _write(cwd.resolve("doxsite.d/metadata/dashboard/site.json"), _dashboard_json)
        _write(cwd.resolve("doxsite.d/metadata/rdf/graph.json"), _rdf_graph_json)
        _write(cwd.resolve("doxsite.d/metadata/glossary/terms.json"), "{\"terms\": []}\n")
        _write(cwd.resolve("doxsite.d/metadata/bibliography/bibliography.json"), _local_bibid_bibliography_json)
        _write(cwd.resolve("doxsite.d/site.ttl"), "@prefix ex: <https://example.com/> .\n")
        _write(cwd.resolve("doxsite.d/site.jsonld"), "{\"@graph\":[]}\n")
      }
  }

  private class BibDoxBibliographyMetadataRunner extends CozyBok.Runner {
    def run(command: Vector[String], cwd: Path): Unit =
      if (command.take(2) == Vector("dox", "site")) {
        _write(cwd.resolve("doxsite.d/metadata/dashboard/site.json"), _dashboard_json)
        _write(cwd.resolve("doxsite.d/metadata/rdf/graph.json"), _rdf_graph_json)
        _write(cwd.resolve("doxsite.d/metadata/glossary/terms.json"), "{\"terms\": []}\n")
        _write(cwd.resolve("doxsite.d/metadata/bibliography/bibliography.json"), _bibliography_bib_dox_json)
        _write(cwd.resolve("doxsite.d/site.ttl"), "@prefix ex: <https://example.com/> .\n")
        _write(cwd.resolve("doxsite.d/site.jsonld"), "{\"@graph\":[]}\n")
      }
  }

  private class BibliographyRawMetadataRunner extends CozyBok.Runner {
    def run(command: Vector[String], cwd: Path): Unit =
      if (command.take(2) == Vector("dox", "site")) {
        _write(cwd.resolve("doxsite.d/metadata/dashboard/site.json"), _dashboard_json)
        _write(cwd.resolve("doxsite.d/metadata/rdf/graph.json"), _rdf_graph_json)
        _write(cwd.resolve("doxsite.d/metadata/glossary/terms.json"), "{\"terms\": []}\n")
        _write(cwd.resolve("doxsite.d/metadata/bibliography/bibliography.json"), _bibliography_raw_json)
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
      |      "entry_type": "book"
      |    },
      |    "body_html": "<p>A reference book for design patterns.</p>",
      |    "quality": {}
      |  }]
      |}
      |""".stripMargin

  private def _bibliography_source_url_json: String =
    _bibliography_source_url_json("https://example.com/design-patterns.bib")

  private def _bibliography_source_url_json(sourceurl: String): String =
    s"""{
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
      |      "isbn": "9780201633610"
      |    },
      |    "bibtex": {
      |      "source_url": "${sourceurl}"
      |    },
      |    "body_html": "<p>A reference book for design patterns.</p>",
      |    "quality": {}
      |  }]
      |}
      |""".stripMargin

  private def _bibliography_bib_dox_json: String =
    """{
      |  "entries": [{
      |    "id": "bib:design-patterns",
      |    "slug": "design-patterns",
      |    "entry_type": "book",
      |    "title": "Curated Design Patterns",
      |    "summary": "Curated BoK reference source.",
      |    "category": "concept",
      |    "source_path": "bibliography/concept/design-patterns.bib.dox",
      |    "public_path": "bibliography/concept/design-patterns.html",
      |    "authors": [],
      |    "terms": ["concept:pattern"],
      |    "identifiers": {},
      |    "bibtex": {},
      |    "body_html": "<p>Curated BoK narrative.</p>",
      |    "source_kind": "internal",
      |    "needs_resolution": false,
      |    "quality": {}
      |  }]
      |}
      |""".stripMargin

  private def _local_bibid_bibliography_json: String =
    """{
      |  "entries": [{
      |    "id": "bib:design-patterns",
      |    "slug": "design-patterns",
      |    "entry_type": "book",
      |    "title": "bib:design-patterns",
      |    "summary": "Unresolved local bibliography reference.",
      |    "category": "concept",
      |    "source_path": "concept/article.md",
      |    "public_path": "bibliography/concept/design-patterns.html",
      |    "authors": [],
      |    "terms": ["concept:pattern"],
      |    "identifiers": {},
      |    "bibtex": {},
      |    "body_html": "",
      |    "source_kind": "external-ref",
      |    "refs": ["bib:design-patterns"],
      |    "needs_resolution": true,
      |    "quality": {"missing_citation": true, "missing_terms": false, "missing_source": false, "missing_narrative": true, "needs_curation": true}
      |  }]
      |}
      |""".stripMargin

  private def _bibliography_raw_json: String =
    """{
      |  "entries": [{
      |    "id": "bib:design-patterns",
      |    "slug": "design-patterns",
      |    "entry_type": "book",
      |    "title": "Design Patterns",
      |    "summary": "Reusable object-oriented design catalog.",
      |    "category": "concept",
      |    "source_path": "bibliography/concept/design-patterns.bib",
      |    "public_path": "bibliography/concept/design-patterns.html",
      |    "authors": [],
      |    "terms": ["concept:pattern"],
      |    "identifiers": {
      |      "isbn": "9780201633610"
      |    },
      |    "bibtex": {
      |      "raw": "@book{gamma1994designpatterns, title={Design Patterns}, author={Gamma, Erich and Helm, Richard}, year={1994}}\n"
      |    },
      |    "body_html": "",
      |    "source_kind": "bibtex-only",
      |    "quality": {
      |      "needs_curation": true
      |    }
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

  private def _capture(f: => Unit): String = {
    val out = new ByteArrayOutputStream()
    Console.withOut(new PrintStream(out, true, "UTF-8")) {
      f
    }
    out.toString("UTF-8")
  }
}
