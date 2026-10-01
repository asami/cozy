package cozy.bok

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path, Paths}
import scala.collection.JavaConverters._
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.prop.TableDrivenPropertyChecks
import org.scalatest.wordspec.AnyWordSpec

/*
 * @author  ASAMI, Tomoharu
 * @since   Oct.  1, 2026
 * @version Oct.  1, 2026
 */
class CozyBokSiteCompositionSpec
    extends AnyWordSpec
    with GivenWhenThen
    with Matchers
    with TableDrivenPropertyChecks {
  "Cozy BoK stylesheet destinations" should {
    "resolve root and localized home and nested pages to their Antora asset roots" in {
      _with_project { project =>
        Given("a multi-locale website with Japanese and English Antora outputs")
        val config = _config(project, "multi_locale_subdirs", enabled = false)
        val cases = Table(
          ("page", "assetroot"),
          ("index.html", ""),
          ("category/index.html", ""),
          ("ja/index.html", "ja"),
          ("en/index.html", "en"),
          ("ja/glossary/architecture/index.html", "ja"),
          ("en/glossary/architecture/index.html", "en"),
          ("january/index.html", ""),
          ("ja/../category/index.html", "")
        )
        forAll(cases) { (relative, assetroot) =>
          val page = config.websitePath.resolve(relative)

          When("Cozy renders the page's stylesheet links")
          val destinations = _css_destinations(page, CozyBokImplementation._site_css_links(config, page))

          Then("all three links resolve to the selected Antora CSS directory")
          destinations shouldBe _css_paths(config.websitePath.resolve(assetroot))
        }
      }
    }

    "keep single-locale root assets for home, nested and language-index pages" in {
      _with_project { project =>
        Given("a single-locale website, including localized glossary indexes")
        val config = _config(project, "single_locale_root", enabled = false)
        val cases = Table("page", "index.html", "architecture/index.html", "ja/glossary/index.html", "en/glossary/index.html")
        forAll(cases) { relative =>
          val page = config.websitePath.resolve(relative)

          When("Cozy renders the page's stylesheet links")
          val destinations = _css_destinations(page, CozyBokImplementation._site_css_links(config, page))

          Then("every stylesheet still resolves under the website root")
          destinations shouldBe _css_paths(config.websitePath)
        }
      }
    }
  }

  "Cozy BoK and Arcadia composition" should {
    "preserve Arcadia root, home and category bytes through the complete build in both locale modes" in {
      forAll(Table("mode", "single_locale_root", "multi_locale_subdirs")) { mode =>
        _with_project { project =>
          Given("enabled Arcadia with distinct owned-page bytes and tag metadata targeting home and category indexes")
          val config = _config(project, mode, enabled = true)
          val paths = _owned_paths(mode)
          val outputs = paths.map(path => path -> _arcadia_bytes(path)).toMap
          val runner = new FixtureRunner(outputs, config)

          When("Cozy performs the complete build, invoking and copying Arcadia before BoK generation")
          CozyBok.build(config, runner)

          Then("Arcadia is invoked once and its output bytes survive at every owned destination")
          runner.commands.count(_.take(2) == Vector("arcadia", "site")) shouldBe 1
          outputs.foreach { case (relative, bytes) =>
            Files.readAllBytes(config.arcadiaSitePath.resolve(relative)).toVector shouldBe bytes.toVector
            Files.readAllBytes(config.websitePath.resolve(relative)).toVector shouldBe bytes.toVector
          }
          And("Cozy still generates its dedicated knowledge pages")
          val prefix = if (mode == "single_locale_root") "" else "ja/"
          Files.readString(config.websitePath.resolve(prefix + "articles/index.html"), StandardCharsets.UTF_8) should include("bok-article-doc")
          And("ordinary unowned articles still receive knowledge tag chips in every output locale")
          val prefixes = if (mode == "single_locale_root") Vector("") else Vector("ja/", "en/")
          prefixes.foreach { localeprefix =>
            Files.readString(config.websitePath.resolve(localeprefix + "architecture/overview.html"), StandardCharsets.UTF_8) should include("bok-knowledge-tag-chip-list")
          }
        }
      }
    }

    "generate BoK fallback for individual missing locale and category destinations" in {
      _with_project { project =>
        Given("Arcadia with a root and Japanese home but only selected category outputs")
        val config = _config(project, "multi_locale_subdirs", enabled = true)
        val preserved = Vector("index.html", "ja/index.html", "ja/architecture/index.html", "en/category/index.html")
        val outputs = preserved.map(path => path -> _arcadia_bytes(path)).toMap
        val runner = new FixtureRunner(outputs, config)

        When("Cozy builds and composes the partial Arcadia output")
        CozyBok.build(config, runner)

        Then("present Arcadia files retain their exact bytes")
        outputs.foreach { case (relative, bytes) =>
          Files.readAllBytes(config.websitePath.resolve(relative)).toVector shouldBe bytes.toVector
        }
        And("missing locale home, regular-category and category-index files receive BoK pages")
        Vector("en/index.html", "en/architecture/index.html", "ja/guide/index.html", "en/guide/index.html", "ja/category/index.html").foreach { relative =>
          Files.isRegularFile(config.arcadiaSitePath.resolve(relative)) shouldBe false
          Files.readString(config.websitePath.resolve(relative), StandardCharsets.UTF_8) should include("<!doctype html>")
        }
      }
    }

    "generate BoK pages when Arcadia is disabled despite stale Arcadia files" in {
      forAll(Table("mode", "single_locale_root", "multi_locale_subdirs")) { mode =>
        _with_project { project =>
          Given("disabled Arcadia and stale regular files at all of its would-be owned destinations")
          val config = _config(project, mode, enabled = false)
          val paths = _owned_paths(mode)
          paths.foreach(relative => _write_bytes(config.arcadiaSitePath.resolve(relative), _arcadia_bytes(relative)))
          val runner = new FixtureRunner(Map.empty, config)

          When("Cozy performs the complete build")
          CozyBok.build(config, runner)

          Then("Arcadia is never invoked and stale files confer no ownership")
          runner.commands.filter(_.headOption.contains("arcadia")) shouldBe empty
          val generated = if (mode == "single_locale_root") paths else paths.filterNot(_ == "index.html")
          generated.foreach { relative =>
            Files.readString(config.websitePath.resolve(relative), StandardCharsets.UTF_8) should include("<!doctype html>")
            Files.readAllBytes(config.websitePath.resolve(relative)).toVector should not equal (_arcadia_bytes(relative).toVector)
            Files.readAllBytes(config.arcadiaSitePath.resolve(relative)).toVector shouldBe _arcadia_bytes(relative).toVector
          }
        }
      }
    }
  }

  private def _config(project: Path, mode: String, enabled: Boolean): CozyBok.BuildConfig = {
    _write_text(project.resolve("conf/cozy/config.yaml"),
      s"""bok:
         |  website: composed-site.d
         |  arcadia-site: composed-arcadia.d
         |  arcadia:
         |    enabled: ${enabled}
         |""".stripMargin)
    _write_text(project.resolve("src/main/doxsite/site.conf"),
      s"""site {
         |  metadata {
         |    in_language = ["ja", "en"]
         |  }
         |  output {
         |    locale_mode = "${mode}"
         |  }
         |}
         |""".stripMargin)
    Vector("architecture", "guide").foreach { category =>
      _write_text(project.resolve(s"src/main/doxsite/${category}/category.yaml"), s"name: ${category}\ntitle: ${category}\ndescription: Composition fixture.\n")
    }
    val config = CozyBok.BuildConfig.create(List(project.toString, "--strategy", "wip", "--no-bib-service"))
    config.localeMode shouldBe CozyBok.LocaleMode.create(mode)
    config
  }

  private def _owned_paths(mode: String): Vector[String] = {
    val pages = Vector("index.html", "architecture/index.html", "guide/index.html", "category/index.html")
    if (mode == "single_locale_root") pages
    else Vector("index.html") ++ Vector("ja", "en").flatMap(locale => pages.map(page => s"${locale}/${page}"))
  }

  private def _css_destinations(page: Path, html: String): Vector[Path] =
    """href="([^"]+)""".r.findAllMatchIn(html).
      map(matched => page.toAbsolutePath.normalize.getParent.resolve(matched.group(1)).normalize).toVector

  private def _css_paths(root: Path): Vector[Path] =
    Vector("bootstrap-grid.min.css", "site.css", "cozy-bok-dashboard.css").
      map(name => root.resolve("_/css").resolve(name).toAbsolutePath.normalize)

  private def _arcadia_bytes(relative: String): Array[Byte] =
    s"""<!doctype html>\r
<html><body><article><h1 class="page">Arcadia</h1>Arcadia: ${relative} — 日本語</article></body></html>\r
""".getBytes(StandardCharsets.UTF_8)

  private def _composition_tags_json: String =
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
      |    "summary": "Composition fixture references.",
      |    "public_path": "tags/technology/review.html",
      |    "refs": [
      |      {"kind": "article", "title": "Arcadia", "public_path": "index.html"},
      |      {"kind": "article", "title": "Arcadia", "public_path": "category/index.html"},
      |      {"kind": "article", "title": "Arcadia", "public_path": "architecture/index.html", "category": "architecture"},
      |      {"kind": "article", "title": "Architecture Overview", "public_path": "architecture/overview.html", "category": "architecture"}
      |    ],
      |    "children": []
      |  }]
      |}
      |""".stripMargin

  private class FixtureRunner(outputs: Map[String, Array[Byte]], config: CozyBok.BuildConfig) extends CozyBok.Runner {
    private var _commands = Vector.empty[Vector[String]]
    def commands: Vector[Vector[String]] = _commands
    def run(command: Vector[String], cwd: Path): Unit = {
      _commands = _commands :+ command
      if (command.take(2) == Vector("dox", "site")) {
        _write_text(config.doxsitePath.resolve("metadata/tags/tags.json"), _composition_tags_json)
        val prefixes = if (config.localeMode == CozyBok.LocaleMode.SingleLocaleRoot) Vector("") else Vector("ja/", "en/")
        prefixes.foreach { prefix =>
          val relative = prefix + "architecture/overview.html"
          val content = """<!doctype html><html><body><article><h1 class="page">Architecture Overview</h1><p>Ordinary article.</p></article></body></html>"""
          _write_text(config.doxsitePath.resolve(relative), content)
          _write_text(config.websitePath.resolve(relative), content)
        }
      }
      if (command.take(2) == Vector("arcadia", "site")) {
        val outputroot = cwd.resolve(command(3))
        outputs.foreach { case (relative, bytes) =>
          _write_bytes(outputroot.resolve(relative), bytes)
        }
      }
    }
  }

  private def _write_text(path: Path, text: String): Unit =
    _write_bytes(path, text.getBytes(StandardCharsets.UTF_8))

  private def _write_bytes(path: Path, bytes: Array[Byte]): Unit = {
    Files.createDirectories(path.getParent)
    Files.write(path, bytes)
    ()
  }

  private def _with_project(body: Path => Unit): Unit = {
    val root = Paths.get("target/phase75.2-site-composition").toAbsolutePath.normalize
    Files.createDirectories(root)
    val project = Files.createTempDirectory(root, "fixture-")
    try {
      body(project)
    } finally {
      val stream = Files.walk(project)
      try {
        stream.iterator.asScala.toVector.reverse.foreach(Files.deleteIfExists)
      } finally {
        stream.close()
      }
    }
  }
}
