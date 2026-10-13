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
 * @author  ASAMI, Tomoharu
 */
class CozyBokArcadiaCategoryOwnershipSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  "BoK category producer ownership" should {
    "compose configured Arcadia pages" which {
      "preserve authored homes and categories at default destinations in both layouts (P751-02D-01)" in {
        Vector(false, true).foreach { multilocale =>
          _with_project { project =>
            Given("default destinations and bilingual category sources in the selected locale layout")
            val config = _config(project, multilocale, true, "arcadiasite.d", "website.d")
            val ownership = _locales(config).flatMap(locale => _categories.map(locale -> _)).toSet
            val runner = new CategoryContractRunner(config, ownership, Set.empty)

            When("Cozy builds and copies the external authored homes and categories")
            CozyBok.build(config, runner)

            Then("each rendered locale retains exact UTF-8 authored content and article anchors with normal special pages and metadata")
            _expect_authored_homes(config)
            ownership.foreach { case (locale, category) => _expect_authored_category(config, locale, category) }
            _expect_special_and_metadata(config)
          }
        }
      }

      "preserve authored categories at custom producer and website destinations in both layouts (P751-02D-02)" in {
        Vector(false, true).foreach { multilocale =>
          _with_project { project =>
            Given("custom configured producer and website destinations with bilingual category sources")
            val config = _config(project, multilocale, true, "generated/authored-site", "generated/public-site")
            val ownership = _locales(config).flatMap(locale => _categories.map(locale -> _)).toSet
            val runner = new CategoryContractRunner(config, ownership, Set.empty)

            When("Cozy normalizes fixed producer output and composes the custom website")
            CozyBok.build(config, runner)

            Then("each exact custom-destination counterpart owns its copied category and home")
            _expect_authored_homes(config)
            ownership.foreach { case (locale, category) =>
              _expect_authored_category(config, locale, category)
              _read(project.resolve("arcadiasite.d").resolve(_relative(config, locale, s"$category/index.html"))) shouldBe
                _authored_category(locale, category)
            }
            _expect_special_and_metadata(config)
          }
        }
      }

      "generate category fallback when the enabled producer supplies only homes in both layouts (P751-02D-03)" in {
        Vector(false, true).foreach { multilocale =>
          _with_project { project =>
            Given("an enabled Arcadia producer with authored homes and no category counterparts")
            val config = _config(project, multilocale, true, "arcadiasite.d", "website.d")
            val runner = new CategoryContractRunner(config, Set.empty, Set.empty)

            When("Cozy builds the website with missing authored categories")
            CozyBok.build(config, runner)

            Then("homes remain authored while every category has actual source articles and all three layout-relative styles")
            _expect_authored_homes(config)
            _locales(config).foreach { locale =>
              _categories.foreach { category =>
                Files.exists(config.arcadiaSitePath.resolve(_relative(config, locale, s"$category/index.html"))) shouldBe false
                _expect_fallback(config, locale, category)
              }
            }
            _expect_special_and_metadata(config)
          }
        }
      }

      "retain Dashboard and category generation with a disabled producer and stale output in both layouts (P751-02D-04)" in {
        Vector(false, true).foreach { multilocale =>
          _with_project { project =>
            Given("Arcadia is disabled and both native and configured output contain stale authored pages")
            val config = _config(project, multilocale, false, "generated/authored-site", "website.d")
            val runner = new CategoryContractRunner(config, Set.empty, Set.empty)
            val stale = Vector("index.html") ++ _categories.map(category => s"$category/index.html")
            val staleoutputs = Vector(project.resolve("arcadiasite.d"), config.arcadiaSitePath).flatMap { output =>
              _locales(config).flatMap { locale =>
                stale.map(relative => output.resolve(_relative(config, locale, relative)))
              }
            }
            staleoutputs.foreach(path => _write(path, "stale authored UTF-8 日本語\n"))

            When("Cozy builds with the external producer disabled")
            CozyBok.build(config, runner)

            Then("the producer never runs, stale output remains untouched, and Dashboard and styled categories are generated")
            runner.commands.exists(_.take(2) == Vector("arcadia", "site")) shouldBe false
            staleoutputs.foreach(path => _read(path) shouldBe "stale authored UTF-8 日本語\n")
            _locales(config).foreach { locale =>
              _read(_root(config, locale).resolve("index.html")) should include("bok-dashboard-shell")
              _categories.foreach(category => _expect_fallback(config, locale, category))
            }
            _expect_special_and_metadata(config)
          }
        }
      }

      "preserve only the exact JA category-A and EN category-B counterparts (P751-02D-05)" in {
        Given("two bilingual categories with ownership only for JA development-process and EN architecture")
        _with_project { project =>
          val config = _config(project, true, true, "generated/authored-site", "generated/public-site")
          val ownership = Set("ja" -> "development-process", "en" -> "architecture")
          val runner = new CategoryContractRunner(config, ownership, Set.empty)

          When("Cozy composes the partially authored multilingual website")
          CozyBok.build(config, runner)

          Then("only those exact locale/category paths retain authored content and their opposite counterparts use normal fallback")
          _expect_authored_homes(config)
          _expect_authored_category(config, "ja", "development-process")
          _expect_authored_category(config, "en", "architecture")
          _expect_fallback(config, "en", "development-process")
          _expect_fallback(config, "ja", "architecture")
          _expect_special_and_metadata(config)
        }
      }

      "replace Antora-only website categories with BoK fallback in both layouts (P751-02D-06)" in {
        Vector(false, true).foreach { multilocale =>
          _with_project { project =>
            Given("Antora supplies final website categories while Arcadia supplies homes without category counterparts")
            val config = _config(project, multilocale, true, "generated/authored-site", "website.d")
            val antoraonly = _locales(config).flatMap(locale => _categories.map(locale -> _)).toSet
            val runner = new CategoryContractRunner(config, Set.empty, antoraonly)

            When("Cozy composes the website after Antora output already contains each category")
            CozyBok.build(config, runner)

            Then("final website existence grants no Arcadia ownership and styled source-article fallback replaces Antora content")
            runner.antoraPages.toSet shouldBe antoraonly
            _expect_authored_homes(config)
            antoraonly.foreach { case (locale, category) =>
              Files.exists(config.arcadiaSitePath.resolve(_relative(config, locale, s"$category/index.html"))) shouldBe false
              _expect_fallback(config, locale, category)
              _read(_root(config, locale).resolve(s"$category/index.html")) should not include "Antora-only category"
            }
            _expect_special_and_metadata(config)
          }
        }
      }
    }
  }

  private val _categories = Vector("development-process", "architecture")
  private val _css_names = Vector("bootstrap-grid.min.css", "site.css", "cozy-bok-dashboard.css")

  private def _article_title(category: String): String =
    if (category == "development-process") "Static Model Harness" else "Domain Architecture"

  private def _locales(config: CozyBok.BuildConfig): Vector[String] =
    if (config.localeMode == CozyBok.LocaleMode.MultiLocaleSubdirs) config.languages else Vector(config.defaultLocale)

  private def _relative(config: CozyBok.BuildConfig, locale: String, relative: String): String =
    if (config.localeMode == CozyBok.LocaleMode.MultiLocaleSubdirs) s"$locale/$relative" else relative

  private def _root(config: CozyBok.BuildConfig, locale: String): Path =
    config.websitePath.resolve(_relative(config, locale, ""))

  private def _authored_home(locale: String): String =
    s"""<html lang="$locale"><body>Authored home 日本語 — $locale<a href="development-process/article.html">Static Model Harness</a></body></html>
       |""".stripMargin

  private def _authored_category(locale: String, category: String): String =
    s"""<html lang="$locale"><body>Authored category 日本語 — $locale/$category<a href="article.html">${_article_title(category)}</a></body></html>
       |""".stripMargin

  private def _config(project: Path, multilocale: Boolean, enabled: Boolean, destination: String, website: String): CozyBok.BuildConfig = {
    _write(project.resolve(".cozy/config.yaml"),
      s"""bok:
         |  docker-image: antora-image
         |  doxsite: generated/dox-input
         |  arcadia-site: $destination
         |  website: $website
         |  arcadia:
         |    enabled: $enabled
         |    source: src/main/arcadiasite
         |""".stripMargin)
    _write(project.resolve("src/main/arcadiasite/template.txt"), "authored template input\n")
    _write(project.resolve("src/main/doxsite/site.conf"),
      (if (multilocale) "simplemodelingorg = true\n" else "site.output.locale_mode = \"single_locale_root\"\n") +
        "site.metadata.in_language = [\"ja\", \"en\"]\nsite.metadata.vision = \"Build shared knowledge.\"\n")
    _categories.foreach { category =>
      val title = if (category == "development-process") "Development Process" else "Architecture"
      val name = if (category == "development-process") "DevelopmentProcess" else "Architecture"
      _write(project.resolve(s"src/main/doxsite/$category/category.yaml"),
        s"name: $name\ntitle: $title\ndescription: Modeling knowledge.\nvision: Describe shared domain knowledge.\n")
      _write(project.resolve(s"src/main/doxsite/$category/article.dox"),
        s"${_article_title(category)}\n====================\n\n# HEAD\n\n## HEADLINE\n\n${_article_title(category)}\n\n## BRIEF\n\nDomain structure.\n")
    }
    CozyBok.BuildConfig.create(List(project.toString, "--no-bib-service"))
  }

  // Emulate external products only; configuration, composition and BoK writers are real.
  private class CategoryContractRunner(
    config: CozyBok.BuildConfig,
    ownership: Set[(String, String)],
    antoraonly: Set[(String, String)]
  ) extends CozyBok.Runner {
    private var _calls = Vector.empty[Vector[String]]
    private var _antora_pages = Vector.empty[(String, String)]
    def commands: Vector[Vector[String]] = _calls
    def antoraPages: Vector[(String, String)] = _antora_pages

    override def run(command: Vector[String], cwd: Path): Unit = {
      _calls = _calls :+ command
      if (command.headOption.contains("docker")) {
        val output = cwd.resolve(command(command.indexOf("--to-dir") + 1).stripPrefix("/workspace/"))
        _write_assets(output)
        antoraonly.filter { case (locale, _) =>
          output.toAbsolutePath.normalize == _root(config, locale).toAbsolutePath.normalize
        }.foreach { case (locale, category) =>
          _write(output.resolve(s"$category/index.html"), "<html><body>Antora-only category</body></html>\n")
          _antora_pages = _antora_pages :+ (locale -> category)
        }
      } else if (command.take(2) == Vector("dox", "site")) {
        config.languages.foreach { locale =>
          _categories.zipWithIndex.foreach { case (category, index) =>
            _write(config.doxsitePath.resolve(s"WEB-INF/data/$locale/notice${index + 1}.yaml"),
              s"notice.title: ${_article_title(category)}\nnotice.uri: $category/article.html\nnotice.category:\n  title: $category\n  uri: $category/index.html\n")
          }
        }
        _write(config.doxsitePath.resolve("metadata/rdf/graph.json"), "{\"nodes\":[],\"edges\":[],\"truncated\":false}\n")
        _write(config.doxsitePath.resolve("site.ttl"), "@prefix ex: <https://example.com/> .\n")
        _write(config.doxsitePath.resolve("site.jsonld"), "{\"@graph\":[]}\n")
      } else if (command.take(2) == Vector("arcadia", "site")) {
        _read(cwd.resolve(command(2)).resolve("template.txt"))
        config.languages.foreach { locale =>
          _read(cwd.resolve(command(3)).resolve(s"WEB-INF/data/$locale/notice1.yaml"))
        }
        val output = cwd.resolve("arcadiasite.d")
        _locales(config).foreach { locale =>
          _write(output.resolve(_relative(config, locale, "index.html")), _authored_home(locale))
        }
        ownership.foreach { case (locale, category) =>
          _write(output.resolve(_relative(config, locale, s"$category/index.html")), _authored_category(locale, category))
        }
      }
    }
  }

  private def _expect_authored_homes(config: CozyBok.BuildConfig): Unit = {
    _locales(config).foreach { locale =>
      val home = _read(_root(config, locale).resolve("index.html"))
      home shouldBe _authored_home(locale)
      home should include("日本語")
      home should include("""href="development-process/article.html">Static Model Harness</a>""")
      _read(config.arcadiaSitePath.resolve(_relative(config, locale, "index.html"))) shouldBe home
    }
  }

  private def _expect_authored_category(config: CozyBok.BuildConfig, locale: String, category: String): Unit = {
    val html = _read(_root(config, locale).resolve(s"$category/index.html"))
    html shouldBe _authored_category(locale, category)
    html should include(s"""href="article.html">${_article_title(category)}</a>""")
    _read(config.arcadiaSitePath.resolve(_relative(config, locale, s"$category/index.html"))) shouldBe html
  }

  private def _expect_fallback(config: CozyBok.BuildConfig, locale: String, category: String): Unit = {
    val root = _root(config, locale)
    val page = root.resolve(s"$category/index.html")
    val html = _read(page)
    html should include(_article_title(category))
    html should include("""href="article.html"""")
    html should not include "Authored category"
    val targets = """<link rel="stylesheet" href="([^"]+)""".r.findAllMatchIn(html).map { matched =>
      page.getParent.resolve(matched.group(1)).toAbsolutePath.normalize
    }.toVector
    targets shouldBe _css_names.map(name => root.resolve(s"_/css/$name").toAbsolutePath.normalize)
    targets.map(Files.isRegularFile(_)) shouldBe Vector(true, true, true)
  }

  private def _expect_special_and_metadata(config: CozyBok.BuildConfig): Unit = {
    _locales(config).foreach { locale =>
      Vector("category", "articles", "glossary", "manual", "rdf").foreach { special =>
        Files.isRegularFile(_root(config, locale).resolve(s"$special/index.html")) shouldBe true
      }
    }
    Vector("rdf/site.ttl", "rdf/site.jsonld", "metadata/rdf/graph.json", "metadata/cncf/knowledge-source.json").foreach { relative =>
      Files.isRegularFile(config.websitePath.resolve(relative)) shouldBe true
    }
  }

  private def _write_assets(root: Path): Unit =
    _css_names.foreach(name => _write(root.resolve(s"_/css/$name"), s"/* ${root.toString}: $name */\n"))

  private def _write(path: Path, content: String): Unit = {
    Files.createDirectories(path.getParent)
    Files.write(path, content.getBytes(StandardCharsets.UTF_8))
    ()
  }

  private def _read(path: Path): String = new String(Files.readAllBytes(path), StandardCharsets.UTF_8)

  private def _with_project[A](f: Path => A): A = {
    val workroot = Paths.get(sys.props("user.dir")).toAbsolutePath.normalize.resolve("target/cozy-bok-arcadia-category-ownership-spec")
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
