package cozy.bok

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path, Paths}
import scala.collection.JavaConverters._
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Oct.  7, 2026
 * @version Oct.  7, 2026
 */
class CozyBokPublicArticleCatalogueSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  "BoK public article catalogue" should {
    "consume native producer selection for shared lists and counts" which {
      "retain the ordinary article and flattened package index only (P751-02F-CATALOGUE-COZY-01)" in {
        Given("physical public articles, every direct/deep private suffix, nested project, bare _source and partial")
        _with_project { project =>
          val config = _config(project, false)
          _public_sources(config.sourcepath)
          When("Cozy metadata consumes the actual physical Realm and SmartDox facade")
          val categories = CozyBokImplementation._category_contents(config.sourcepath)
          val count = CozyBokImplementation._source_article_count(config)
          Then("both exact physical public articles supply canonical hrefs, titles, briefs and the same count")
          categories.map(_.slug) shouldBe Vector("development-process")
          val category = categories.head
          category.articles.map(article => (article.href, article.title, article.brief)) shouldBe Vector(
            ("ordinary.html", "Ordinary Article", "Ordinary brief."),
            ("package.html", "Package Article", "Package brief."))
          category.articles.size shouldBe 2
          count shouldBe 2
          category.terms.map(_.href) shouldBe Vector("../glossary/development-process/term.html")
        }
      }

      "retain ordinary suffixes and an unrecognized dox directory while excluding presentation entries (P751-02F-CATALOGUE-COZY-02)" in {
        Given("legacy no-status Dox/Org/Markdown sources, a dox directory without index and generic/glossary entries")
        _with_project { project =>
          val config = _config(project, false)
          _category(config.sourcepath)
          Vector("a.dox", "b.org", "c.md", "d.markdown", "folder.dox/child.org", "index.dox", "glossary/local.dox").foreach { relative =>
            _write(config.sourcepath.resolve(s"development-process/$relative"), _article("Legacy Article", "Legacy brief."))
          }
          When("the actual metadata catalogue is requested")
          val categories = CozyBokImplementation._category_contents(config.sourcepath)
          val count = CozyBokImplementation._source_article_count(config)
          Then("only ordinary articles count, with producer-mapped public paths and no invented status requirement")
          categories.head.articles.map(_.href) shouldBe Vector("a.html", "b.html", "c.html", "d.html", "folder.dox/child.html")
          count shouldBe 5
        }
      }

      "keep empty and missing source/category input empty (P751-02F-CATALOGUE-COZY-03)" in {
        Given("a missing source root, an empty source root and a declared category without documents")
        _with_project { project =>
          val config = _config(project, false)
          val missing = project.resolve("missing-source")
          val empty = project.resolve("empty-source")
          Files.createDirectories(empty)
          _category(config.sourcepath)
          When("the normal catalogue reads each source boundary")
          val missingcategories = CozyBokImplementation._category_contents(missing)
          val emptycategories = CozyBokImplementation._category_contents(empty)
          val declared = CozyBokImplementation._category_contents(config.sourcepath)
          Then("no fake source or fallback scan creates an article")
          missingcategories shouldBe Vector.empty
          emptycategories shouldBe Vector.empty
          declared.map(_.articles) shouldBe Vector(Vector.empty)
          CozyBokImplementation._source_article_count(config) shouldBe 0
        }
      }
    }

    "compose normal website lists with external products only emulated" which {
      "exclude private content and aliases in SingleLocaleRoot JA (P751-02F-CATALOGUE-COZY-04)" in {
        Given("real physical sources and normal single-locale configuration")
        _with_project { project =>
          val config = _config(project, false)
          _public_sources(config.sourcepath)
          When("normal CozyBok.build composes external products and real catalogue/count writers")
          CozyBok.build(config, new ContractRunner(config))
          val pages = _catalogue_pages(config)
          Then("the actual article and category fallbacks have exactly two public links and compatible glossary")
          _expect_catalogue(config, pages)
        }
      }

      "exclude private content and aliases in MultiLocaleSubdirs JA/EN (P751-02F-CATALOGUE-COZY-05)" in {
        Given("the same real sources and normal bilingual site configuration")
        _with_project { project =>
          val config = _config(project, true)
          _public_sources(config.sourcepath)
          When("normal CozyBok.build composes both locale websites")
          CozyBok.build(config, new ContractRunner(config))
          val pages = _catalogue_pages(config)
          Then("both actual locale lists retain only the ordinary and flattened package articles")
          _expect_catalogue(config, pages)
        }
      }
    }
  }

  private val _private_paths = (for {
    directory <- Vector("", "arbitrary/deeper/")
    suffix <- Vector("dox", "org", "md", "markdown")
  } yield s"package.dox/${directory}private.$suffix") ++ Vector(
    "package.dox/arbitrary/nested.dox/index.dox", "package.dox/review/bare.md", "_partial.dox")
  private val _private_public_paths = Vector(
    "package.dox/private.html", "package.dox/arbitrary/deeper/private.html",
    "package.dox/arbitrary/nested.html", "package.dox/arbitrary/nested.dox/index.html",
    "package.dox/review/bare.html", "_partial.html")

  private def _public_sources(root: Path): Unit = {
    _category(root)
    _write(root.resolve("development-process/ordinary.dox"), _article("Ordinary Article", "Ordinary brief."))
    _write(root.resolve("development-process/package.dox/index.dox"), _article("Package Article", "Package brief."))
    _private_paths.foreach(path => _write(root.resolve(s"development-process/$path"), _article("Private sentinel", "Private content.")))
    _write(root.resolve("development-process/package.dox/review/bare.md"), "---\n_source\n---\nPrivate bare sentinel.\n")
    _write(root.resolve("development-process/index.dox"), _article("Category index", "Category presentation."))
    _write(root.resolve("glossary/development-process/term.dox"), _article("Glossary Term", "Glossary brief."))
  }

  private def _category(root: Path): Unit =
    _write(root.resolve("development-process/category.yaml"), "name: DevelopmentProcess\ntitle: Development Process\ndescription: Public knowledge.\n")

  private def _article(title: String, brief: String): String =
    s"$title\n====================\n\n# HEAD\n\n## HEADLINE\n\n$title\n\n## BRIEF\n\n$brief\n"

  private def _config(project: Path, multilocale: Boolean): CozyBok.BuildConfig = {
    _write(project.resolve(".cozy/config.yaml"), "bok:\n  docker-image: antora-image\n  arcadia:\n    enabled: true\n    source: src/main/arcadiasite\n")
    _write(project.resolve("src/main/arcadiasite/template.txt"), "Authored home input.\n")
    _write(project.resolve("src/main/doxsite/site.conf"),
      (if (multilocale) "simplemodelingorg = true\n" else "site.output.locale_mode = \"single_locale_root\"\n") +
      "site.metadata.in_language = [\"ja\", \"en\"]\n")
    CozyBok.BuildConfig.create(List(project.toString, "--no-bib-service"))
  }

  private def _locales(config: CozyBok.BuildConfig): Vector[String] =
    if (config.localeMode == CozyBok.LocaleMode.MultiLocaleSubdirs) config.languages else Vector(config.defaultLocale)

  private def _relative(config: CozyBok.BuildConfig, locale: String, path: String): String =
    if (config.localeMode == CozyBok.LocaleMode.MultiLocaleSubdirs) s"$locale/$path" else path

  private def _catalogue_pages(config: CozyBok.BuildConfig): Vector[(String, String)] =
    _locales(config).flatMap(locale => Vector("articles", "development-process").map { category =>
      category -> _read(config.websitePath.resolve(_relative(config, locale, s"$category/index.html")))
    })

  private def _expect_catalogue(config: CozyBok.BuildConfig, pages: Vector[(String, String)]): Unit = {
    pages.foreach { case (category, html) =>
      val prefix = if (category == "articles") "../development-process/" else ""
      html should include(s"""href="${prefix}ordinary.html"""")
      html should include(s"""href="${prefix}package.html"""")
      html should include("Ordinary Article")
      html should include("Package Article")
      html should include("Ordinary brief.")
      html should include("Package brief.")
      html should include("""class="bok-dashboard-hero-fact"><strong>2</strong>""")
      html should not include "Private sentinel"
      html should not include "Private content."
      html should not include "Private bare sentinel."
      html should not include "package.dox/index"
      _private_paths.foreach { path =>
        html should not include path
      }
      _private_public_paths.foreach(path => html should not include path)
    }
    CozyBokImplementation._source_article_count(config) shouldBe 2
    _locales(config).foreach { locale =>
      val glossary = _read(config.websitePath.resolve(_relative(config, locale, "glossary/index.html")))
      glossary should include("Glossary Term")
    }
    _locales(config).foreach { locale =>
      val terms = _read(config.websitePath.resolve(_relative(config, locale, "metadata/glossary/terms.json")))
      terms should include("Glossary Term")
      terms should include("glossary/development-process/term.html")
    }
  }

  private def _terms_json: String =
    """{
      |  "terms": [
      |    {
      |      "id": "development-process:term",
      |      "slug": "term",
      |      "title": "Glossary Term",
      |      "category": "development-process",
      |      "source_path": "glossary/development-process/term.dox",
      |      "public_path": "glossary/development-process/term.html",
      |      "definition_html": "<p>Glossary brief.</p>",
      |      "summary": "Glossary brief."
      |    }
      |  ]
      |}
      |""".stripMargin

  private def _dashboard_json: String =
    """{
      |  "counts": {
      |    "category_count": 1,
      |    "article_count": 2,
      |    "glossary_term_count": 1,
      |    "total_item_count": 3
      |  },
      |  "rdf": {
      |    "resource_count": 0,
      |    "triple_count": 0,
      |    "subject_count": 0,
      |    "predicate_count": 0
      |  },
      |  "increments": {
      |    "scale": "day",
      |    "buckets": []
      |  },
      |  "categories": [
      |    {
      |      "name": "development-process",
      |      "title": "Development Process",
      |      "counts": {
      |        "category_count": 0,
      |        "article_count": 2,
      |        "glossary_term_count": 1,
      |        "total_item_count": 3
      |      },
      |      "increments": {
      |        "scale": "day",
      |        "buckets": []
      |      },
      |      "rdf": {
      |        "resource_count": 0,
      |        "triple_count": 0,
      |        "subject_count": 0,
      |        "predicate_count": 0
      |      }
      |    }
      |  ]
      |}
      |""".stripMargin

  // Emulate only process products/assets. Native Realm, config, metadata and composition remain real.
  private class ContractRunner(config: CozyBok.BuildConfig) extends CozyBok.Runner {
    override def run(command: Vector[String], cwd: Path): Unit = {
      if (command.headOption.contains("docker")) {
        val output = cwd.resolve(command(command.indexOf("--to-dir") + 1).stripPrefix("/workspace/"))
        Vector("bootstrap-grid.min.css", "site.css", "cozy-bok-dashboard.css").foreach { name =>
          _write(output.resolve(s"_/css/$name"), "/* external stylesheet */\n")
        }
      } else if (command.take(2) == Vector("dox", "site")) {
        _write(config.doxsitePath.resolve("metadata/glossary/terms.json"), _terms_json)
        _write(config.doxsitePath.resolve("metadata/dashboard/site.json"), _dashboard_json)
        _write(config.doxsitePath.resolve("metadata/rdf/graph.json"), "{\"nodes\":[],\"edges\":[],\"truncated\":false}\n")
        _write(config.doxsitePath.resolve("site.ttl"), "@prefix ex: <https://example.com/> .\n")
        _write(config.doxsitePath.resolve("site.jsonld"), "{\"@graph\":[]}\n")
      } else if (command.take(2) == Vector("arcadia", "site")) {
        _locales(config).foreach { locale =>
          _write(cwd.resolve("arcadiasite.d").resolve(_relative(config, locale, "index.html")), "<html><body>Authored home.</body></html>\n")
        }
      }
    }
  }

  private def _write(path: Path, content: String): Unit = {
    Files.createDirectories(path.getParent)
    Files.write(path, content.getBytes(StandardCharsets.UTF_8))
    ()
  }

  private def _read(path: Path): String = new String(Files.readAllBytes(path), StandardCharsets.UTF_8)

  private def _with_project[A](f: Path => A): A = {
    val workroot = Paths.get(sys.props("user.dir")).resolve("target/cozy-bok-public-article-catalogue-spec")
    Files.createDirectories(workroot)
    val project = Files.createTempDirectory(workroot, "project-")
    try f(project)
    finally {
      val stream = Files.walk(project)
      try stream.iterator.asScala.toVector.sortBy(_.getNameCount).reverse.foreach(Files.deleteIfExists(_))
      finally stream.close()
    }
  }
}
