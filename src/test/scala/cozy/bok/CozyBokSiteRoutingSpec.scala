package cozy.bok

import java.net.URI
import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path, Paths}
import scala.collection.JavaConverters._
import org.scalacheck.{Gen, Prop, Test}
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Oct.  5, 2026
 * @version Oct.  5, 2026
 * @author  ASAMI, Tomoharu
 */
class CozyBokSiteRoutingSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  "BoK site input and asset routing" should {
    "hand generated notice data to the configured home producer" which {
      "retain both input directories and normalize fixed output in both locale layouts" in {
        Vector(false, true).foreach { multilocale =>
          Vector("arcadiasite.d", "generated/authored-site").foreach { destination =>
            _with_project { project =>
              Given("authored templates, configured Dox input and JA/EN notices with a configured Cozy destination")
              val config = _config(project, multilocale, true, destination, "generated/public-site")
              val runner = new SiteContractRunner(config, produceoutput = true)
              _write(project.resolve("arcadiasite.d/stale.html"), "old producer output")
              _write(config.arcadiaSitePath.resolve("obsolete.html"), "old configured output")

              When("Cozy builds and copies the external producer's fixed output using both actual inputs")
              CozyBok.build(config, runner)
              val locales = if (multilocale) config.languages else Vector(config.defaultLocale)
              val homes = locales.map { locale =>
                val relative = if (multilocale) s"$locale/index.html" else "index.html"
                (locale, _read(config.websitePath.resolve(relative)), _read(config.arcadiaSitePath.resolve(relative)))
              }
              val css = locales.map { locale =>
                val root = if (multilocale) config.websitePath.resolve(locale) else config.websitePath
                val page = root.resolve("development-process/index.html")
                (root, _css_targets(page, _read(page)))
              }
              val metadata = Vector("rdf/site.ttl", "rdf/site.jsonld", "metadata/rdf/graph.json", "metadata/cncf/knowledge-source.json").map { relative =>
                Files.isRegularFile(config.websitePath.resolve(relative))
              }
              val stale = Vector(project.resolve("arcadiasite.d/stale.html"), config.arcadiaSitePath.resolve("obsolete.html")).map(Files.exists(_))
              val template = _read(project.resolve(config.arcadia.source).resolve("template.txt"))
              val notices = config.languages.map { locale =>
                Files.isRegularFile(config.doxsitePath.resolve(s"WEB-INF/data/$locale/notice01.yaml"))
              }
              val uri = URI.create(_article_uri)

              Then("the input argv, notice title and valid article link survive at the configured destination and website")
              runner.commands.filter(_.take(2) == Vector("arcadia", "site")) shouldBe
                Vector(Vector("arcadia", "site", config.arcadia.source, config.doxsite))
              runner.commands.indexWhere(_.take(2) == Vector("dox", "site")) should be <
                runner.commands.indexWhere(_.take(2) == Vector("arcadia", "site"))
              runner.workingDirectories.forall(_ == config.project) shouldBe true
              runner.inputNotices.map(_._1) shouldBe config.languages
              runner.inputNotices.foreach { case (locale, notice) =>
                notice should include(s"notice.title: ${_notice_title(locale)}")
                notice should include(s"notice.uri: ${_article_uri}")
              }
              homes.foreach { case (locale, home, authored) =>
                home shouldBe authored
                home should include(_notice_title(locale))
                home should include(s"""href="${_article_uri}"""")
                home should not include "N/A"
              }
              uri.isAbsolute shouldBe false
              uri.getPath shouldBe "development-process/static-model-harness.html"
              css.foreach { case (root, targets) => _expect_css(root, targets) }
              metadata shouldBe Vector(true, true, true, true)
              stale shouldBe Vector(false, false)
              template shouldBe "authored template input\n"
              notices.forall(identity) shouldBe true
            }
          }
        }
      }

      "retain Dashboard ownership and locale assets when Arcadia is disabled" in {
        Vector(false, true).foreach { multilocale =>
          _with_project { project =>
            Given("an Arcadia-disabled project in either locale layout with existing native output")
            val config = _config(project, multilocale, false, "generated/authored-site", "website.d")
            val runner = new SiteContractRunner(config, produceoutput = false)
            _write(project.resolve("arcadiasite.d/retained.txt"), "disabled producer remains untouched")

            When("Cozy builds Dashboard homes and the actual auxiliary glossary pages")
            CozyBok.build(config, runner)
            val roots = if (multilocale) config.languages.map(config.websitePath.resolve(_)) else Vector(config.websitePath)
            val homes = roots.map(root => _read(root.resolve("index.html")))
            val categorycss = roots.map { root =>
              val page = root.resolve("development-process/index.html")
              (root, _css_targets(page, _read(page)))
            }
            val auxiliary = Vector("ja", "en").map { locale =>
              val page = config.websitePath.resolve(s"$locale/glossary/index.html")
              (Files.isRegularFile(page), if (!multilocale) _css_targets(page, _read(page)) else Vector.empty)
            }
            val nested = Vector("ja/ja/glossary/index.html", "en/en/glossary/index.html").map { relative =>
              Files.exists(config.websitePath.resolve(relative))
            }
            val retained = _read(project.resolve("arcadiasite.d/retained.txt"))

            Then("Dashboard homes and CSS use the declared layout without nested locale fallbacks")
            runner.commands.exists(_.take(2) == Vector("arcadia", "site")) shouldBe false
            homes.foreach(_ should include("bok-dashboard-shell"))
            categorycss.foreach { case (root, targets) => _expect_css(root, targets) }
            auxiliary.foreach { case (exists, targets) =>
              exists shouldBe true
              if (!multilocale) _expect_css(config.websitePath, targets)
            }
            nested shouldBe Vector(false, false)
            retained shouldBe "disabled producer remains untouched"
          }
        }
      }

      "reject a successful Arcadia process that omits its declared output" in {
        Vector("arcadiasite.d", "generated/authored-site").foreach { destination =>
          _with_project { project =>
            Given("an enabled producer with stale default output that will report success without generating output")
            val config = _config(project, true, true, destination, "website.d")
            val runner = new SiteContractRunner(config, produceoutput = false)
            _write(project.resolve("arcadiasite.d/index.html"), "stale home")

            When("Cozy requires the declared producer directory after the successful process handoff")
            val error = intercept[Throwable](CozyBok.build(config, runner))
            val homes = config.languages.map(locale => Files.exists(config.websitePath.resolve(s"$locale/index.html")))
            val outputexists = Files.exists(project.resolve("arcadiasite.d"))

            Then("the missing output is actionable and no Dashboard or stale home is substituted")
            error.getMessage should include("Arcadia site completed without its required output directory")
            error.getMessage should include(project.resolve("arcadiasite.d").toString)
            error.getMessage should include(config.arcadia.source)
            error.getMessage should include(config.doxsite)
            homes shouldBe Vector(false, false)
            outputexists shouldBe false
          }
        }
      }
    }

    "route shared CSS independently of canonical machine metadata" which {
      "resolve actual assets over fifty generated depths in every layout and website destination" in {
        Given("generated page depths and names, both layouts, JA/EN and root/category/special pages")
        val pages = for {
          depth <- Gen.choose(1, 7)
          letters <- Gen.listOfN(8, Gen.alphaLowerChar)
        } yield (depth, letters.mkString)
        val property = Prop.forAll(pages) { case (depth, name) =>
          Vector(false, true).foreach { multilocale =>
            Vector("website.d", "generated/public-site").foreach { website =>
              _with_project { project =>
                Given("actual distinct root and locale assets under the declared custom or default website")
                val config = _config(project, multilocale, false, "arcadiasite.d", website)
                val roots = Vector(config.websitePath) ++ config.languages.map(config.websitePath.resolve(_))
                roots.foreach(_write_assets)
                val pagetargets = Vector(config.websitePath) ++ config.languages.map(config.websitePath.resolve(_))
                val relatives = Vector("index.html", "development-process/index.html", "manual/index.html",
                  Vector.fill(depth)(name).mkString("/") + "/page.html")

                When("the shared CSS producer interprets every actual page path and global prefix")
                val observations = pagetargets.flatMap { root =>
                  relatives.map { relative =>
                    val page = root.resolve(relative)
                    val html = CozyBokImplementation._site_css_links(config, page)
                    val targets = _css_targets(page, html)
                    val globalprefix = CozyBokImplementation._site_root_prefix(config, page)
                    val globaldepth = config.websitePath.relativize(page.getParent).iterator.asScala.count(_.toString.nonEmpty)
                    val assetroot = if (multilocale) root else config.websitePath
                    (assetroot, targets, globalprefix, globaldepth)
                  }
                }

                Then("all three CSS files resolve to exactly the declared asset root while global metadata keeps its root")
                observations.foreach { case (root, targets, globalprefix, globaldepth) =>
                  _expect_css(root, targets)
                  globalprefix shouldBe ("../" * globaldepth)
                }
              }
            }
          }
          true
        }

        When("ScalaCheck interprets the routing property for at least fifty successful generated trials")
        val result = Test.check(Test.Parameters.default.withMinSuccessfulTests(50), property)

        Then("every trial resolves all CSS files without cross-locale or canonical-root leakage")
        result.passed shouldBe true
        result.succeeded should be >= 50
      }
    }
  }

  private val _css_names = Vector("bootstrap-grid.min.css", "site.css", "cozy-bok-dashboard.css")
  private val _article_uri = "development-process/static-model-harness.html"

  private def _notice_title(locale: String): String =
    if (locale == "ja") "静的モデル・ハーネス — ドメインの構造を記述する" else "Static Model Harness — Describing Domain Structure"

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
    _write(project.resolve("src/main/doxsite/development-process/category.yaml"),
      "name: DevelopmentProcess\ntitle: Development Process\ndescription: Modeling development.\n")
    _write(project.resolve("src/main/doxsite/development-process/static-model-harness.dox"),
      "Static Model Harness\n====================\n\n# HEAD\n\n## BRIEF\n\nDomain structure.\n")
    CozyBok.BuildConfig.create(List(project.toString, "--no-bib-service"))
  }

  // Only external process output is emulated; Cozy build and page generation remain real.
  private class SiteContractRunner(config: CozyBok.BuildConfig, produceoutput: Boolean) extends CozyBok.Runner {
    private var _calls = Vector.empty[(Vector[String], Path)]
    private var _input_notices = Vector.empty[(String, String)]
    def commands: Vector[Vector[String]] = _calls.map(_._1)
    def workingDirectories: Vector[Path] = _calls.map(_._2)
    def inputNotices: Vector[(String, String)] = _input_notices

    override def run(command: Vector[String], cwd: Path): Unit = {
      _calls = _calls :+ (command -> cwd)
      if (command.headOption.contains("docker")) {
        val output = command(command.indexOf("--to-dir") + 1).stripPrefix("/workspace/")
        _write_assets(cwd.resolve(output))
      } else if (command.take(2) == Vector("dox", "site")) {
        config.languages.foreach { locale =>
          _write(config.doxsitePath.resolve(s"WEB-INF/data/$locale/notice01.yaml"),
            s"notice.title: ${_notice_title(locale)}\nnotice.uri: ${_article_uri}\nnotice.category:\n  title: Development Process\n  uri: development-process/index.html\n")
        }
        _write(config.doxsitePath.resolve("metadata/rdf/graph.json"), "{\"nodes\":[],\"edges\":[],\"truncated\":false}\n")
        _write(config.doxsitePath.resolve("site.ttl"), "@prefix ex: <https://example.com/> .\n")
        _write(config.doxsitePath.resolve("site.jsonld"), "{\"@graph\":[]}\n")
      } else if (command.take(2) == Vector("arcadia", "site")) {
        val template = cwd.resolve(command(2)).resolve("template.txt")
        _read(template)
        _input_notices = config.languages.map { locale =>
          locale -> _read(cwd.resolve(command(3)).resolve(s"WEB-INF/data/$locale/notice01.yaml"))
        }
        if (produceoutput) {
          val locales = if (config.localeMode == CozyBok.LocaleMode.MultiLocaleSubdirs) config.languages else Vector(config.defaultLocale)
          locales.foreach { locale =>
            val notice = _input_notices.find(_._1 == locale).get._2
            val title = notice.linesIterator.find(_.startsWith("notice.title: ")).get.stripPrefix("notice.title: ")
            val uri = notice.linesIterator.find(_.startsWith("notice.uri: ")).get.stripPrefix("notice.uri: ")
            val relative = if (config.localeMode == CozyBok.LocaleMode.MultiLocaleSubdirs) s"$locale/index.html" else "index.html"
            _write(cwd.resolve("arcadiasite.d").resolve(relative), s"""<html lang="$locale"><body><a href="$uri">$title</a></body></html>""" + "\n")
          }
        }
      }
    }
  }

  private def _write_assets(root: Path): Unit =
    _css_names.foreach(name => _write(root.resolve(s"_/css/$name"), s"/* ${root.toString}: $name */\n"))

  private def _css_targets(page: Path, html: String): Vector[(Path, Boolean)] =
    """<link rel="stylesheet" href="([^"]+)""".r.findAllMatchIn(html).map { matched =>
      val target = page.getParent.resolve(matched.group(1)).toAbsolutePath.normalize()
      (target, Files.isRegularFile(target))
    }.toVector

  private def _expect_css(root: Path, targets: Vector[(Path, Boolean)]): Unit = {
    targets.map(_._1) shouldBe _css_names.map(name => root.resolve(s"_/css/$name").toAbsolutePath.normalize())
    targets.map(_._2) shouldBe Vector(true, true, true)
  }

  private def _write(path: Path, content: String): Unit = {
    Files.createDirectories(path.getParent)
    Files.write(path, content.getBytes(StandardCharsets.UTF_8))
    ()
  }

  private def _read(path: Path): String = new String(Files.readAllBytes(path), StandardCharsets.UTF_8)

  private def _with_project[A](f: Path => A): A = {
    val workroot = Paths.get(sys.props("user.dir")).toAbsolutePath.normalize().resolve("target/cozy-bok-site-routing-spec")
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
