package cozy

import cozy.bok.CozyBok
import cozy.video.CozyVideoSpec
import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path}
import java.util.zip.ZipInputStream
import scala.collection.JavaConverters._
import org.scalatest.funsuite.AnyFunSuite

/*
 * @since   Jun.  3, 2026
 * @version Jun. 19, 2026
 * @author  ASAMI, Tomoharu
 */
class CozyBokSpec extends AnyFunSuite {
  test("bok create writes KnowledgeHub BoK source scaffold") {
    _with_temp_dir("cozy-bok-create") { dir =>
      CozyBok.create(CozyBok.CreateConfig.create(List(
        "--save",
        dir.toString,
        "--name",
        "KnowledgeHub BoK",
        "--url",
        "https://www.asamioffice.com/kokubunji/knowledgehub",
        "--language",
        "ja"
      )))

      assert(Files.isRegularFile(dir.resolve("README.md")))
      assert(Files.isRegularFile(dir.resolve("STRUCTURE.md")))
      assert(Files.isRegularFile(dir.resolve("conf/cozy/config.yaml")))
      assert(Files.isRegularFile(dir.resolve("src/main/doxsite/site.conf")))
      assert(Files.isRegularFile(dir.resolve("src/main/doxsite/glossary/category.yaml")))
      assert(!Files.exists(dir.resolve("src/main/doxsite/glossary/index.dox")))
      assert(Files.isRegularFile(dir.resolve("src/main/doxsite/history/category.yaml")))
      assert(Files.isRegularFile(dir.resolve("src/main/doxsite/history/index.dox")))
      assert(Files.isRegularFile(dir.resolve("src/main/doxsite/manual/index.dox")))
      assert(Files.isRegularFile(dir.resolve("src/main/doxsite/rdf/site.ttl")))
      assert(Files.isRegularFile(dir.resolve("src/main/doxsite/assets/css/knowledgehub.css")))
      assert(Files.isRegularFile(dir.resolve("src/main/antora-ui/build/ui-bundle.zip")))
      assert(!Files.exists(dir.resolve("src/main/doxsite/knowledgehub/category.yaml")))
      assert(!Files.exists(dir.resolve("src/main/doxsite/site-structure.yaml")))
      assert(!Files.exists(dir.resolve("website.d")))
      assert(_read(dir.resolve("src/main/doxsite/index.dox")).startsWith("Home\n======"))
      assert(_read(dir.resolve("src/main/doxsite/index.dox")).contains("# Dashboard"))
      assert(_read(dir.resolve("src/main/doxsite/history/index.dox")).contains("# Dashboard"))
      assert(_read(dir.resolve("src/main/doxsite/manual/index.dox")).contains("# Dashboard"))
      assert(_read(dir.resolve("src/main/doxsite/manual/index.dox")).contains("cozy bok build"))
      assert(_read(dir.resolve("src/main/doxsite/manual/index.dox")).contains("自動用語リンク対象外"))
      assert(_read(dir.resolve("conf/cozy/config.yaml")).contains("cozy-toolchain"))
      assert(!_read(dir.resolve("conf/cozy/config.yaml")).contains("missing-artifact-policy: warn"))
      assert(_read(dir.resolve("src/main/doxsite/site.conf")).contains("""locale_mode = "single_locale_root""""))
      assert(_read(dir.resolve("src/main/doxsite/site.conf")).contains("output.scope.policy = home_only"))
      assert(_read(dir.resolve("src/main/doxsite/index.dox")).contains("published_at="))
      assert(_read(dir.resolve("src/main/doxsite/history/index.dox")).contains("published_at="))
      assert(_read(dir.resolve("src/main/doxsite/manual/index.dox")).contains("published_at="))
      assert(!_read(dir.resolve("README.md")).contains("site-structure"))
      val css = _zip_text(dir.resolve("src/main/antora-ui/build/ui-bundle.zip"), "css/site.css")
      assert(css.contains(".navbar-menu"))
      assert(css.contains(".nav-container"))
      assert(css.contains(".lang-toggle"))
      assert(css.contains("a.glossary"))
      assert(css.contains(".bok-special-links"))
      assert(css.contains(".bok-dashboard-grid"))
      assert(css.contains(".bok-cumulative-line"))
      assert(css.contains(".bok-index-nav"))
      assert(_zip_text(dir.resolve("src/main/antora-ui/build/ui-bundle.zip"), "js/site.js").contains("navbar-burger"))
      assert(_zip_text(dir.resolve("src/main/antora-ui/build/ui-bundle.zip"), "img/menu.svg").contains("<svg"))
      assert(_zip_bytes(dir.resolve("src/main/antora-ui/build/ui-bundle.zip"), "font/roboto-latin-400-normal.woff2").nonEmpty)
      val header = _zip_text(dir.resolve("src/main/antora-ui/build/ui-bundle.zip"), "partials/header-content.hbs")
      assert(!header.contains("""href="{{siteRootPath}}/glossary/index.html">Glossary</a>"""))
      assert(!header.contains("""href="{{siteRootPath}}/history/index.html">History</a>"""))
      assert(!header.contains("""href="{{siteRootPath}}/manual/index.html">Manual</a>"""))
      assert(!header.contains("Lexicon"))
    }
  }

  test("bok create-category writes a category with article and term seeds") {
    _with_temp_dir("cozy-bok-create-category") { dir =>
      CozyBok.create(CozyBok.CreateConfig.create(List("--save", dir.toString)))
      CozyBok.createCategory(CozyBok.CategoryConfig.create(List(
        "knowledgehub",
        "--project",
        dir.toString,
        "--title",
        "KnowledgeHub",
        "--description",
        "KnowledgeHub category.",
        "--article",
        "knowledgehub-overview:KnowledgeHub Overview:Overview article.",
        "--term",
        "glossary/knowledgehub:KnowledgeHub:KnowledgeHub definition.:ナレッジハブ"
      )))

      assert(Files.isRegularFile(dir.resolve("src/main/doxsite/knowledgehub/category.yaml")))
      assert(Files.isRegularFile(dir.resolve("src/main/doxsite/knowledgehub/index.dox")))
      assert(Files.isRegularFile(dir.resolve("src/main/doxsite/knowledgehub/knowledgehub-overview.dox")))
      assert(Files.isRegularFile(dir.resolve("src/main/doxsite/glossary/knowledgehub/knowledgehub.dox")))
      assert(_read(dir.resolve("src/main/doxsite/knowledgehub/index.dox")).contains("""<a href="knowledgehub-overview.html">KnowledgeHub Overview</a>"""))
      assert(_read(dir.resolve("src/main/doxsite/knowledgehub/index.dox")).contains("""<a href="../glossary/knowledgehub/knowledgehub.html">KnowledgeHub</a>"""))
      assert(_read(dir.resolve("src/main/doxsite/knowledgehub/index.dox")).contains("# Dashboard"))
      assert(_read(dir.resolve("src/main/doxsite/knowledgehub/index.dox")).contains("""class="bok-metric-card""""))
      assert(_read(dir.resolve("src/main/doxsite/knowledgehub/index.dox")).contains("""class="bok-dashboard-chart""""))
      assert(_read(dir.resolve("src/main/doxsite/knowledgehub/index.dox")).contains("- Articles: 1"))
      assert(_read(dir.resolve("src/main/doxsite/knowledgehub/index.dox")).contains("- Terms: 1"))
      assert(_read(dir.resolve("src/main/doxsite/knowledgehub/knowledgehub-overview.dox")).contains("published_at="))
      assert(_read(dir.resolve("src/main/doxsite/glossary/knowledgehub/knowledgehub.dox")).contains("published_at="))
      assert(_read(dir.resolve("src/main/doxsite/glossary/knowledgehub/knowledgehub.dox")).contains("reading=ナレッジハブ"))
    }
  }

  test("bok equals form does not satisfy canonical metadata") {
    _with_temp_dir("cozy-bok-equals-options") { dir =>
      val e = intercept[Throwable] {
        CozyBok.CreateConfig.create(List(s"--save=${dir}"))
      }
      assert(e.getMessage.contains("--save <dir>"))
    }
  }

  test("bok build maps strategy and uses docker image from config") {
    _with_temp_dir("cozy-bok-build") { dir =>
      _write(dir.resolve(".cozy/config.yaml"), "bok:\n  docker-image: smartdox-antora:test\n")
      _write(dir.resolve("src/main/doxsite/site.conf"), "site {\n  output {\n    locale_mode = \"single_locale_root\"\n  }\n}\n")
      _write(dir.resolve("src/main/doxsite/glossary/category.yaml"), "name: Glossary\ntitle: 用語集\ndescription: BoK全体で共有する用語集。\n")
      _write(dir.resolve("src/main/doxsite/architecture/category.yaml"), "name: Architecture\ntitle: Architecture\ndescription: Architecture category.\n")
      _write(dir.resolve("src/main/doxsite/architecture/overview.dox"), "Overview\n========\n\n# HEAD\n\n## BRIEF\nArchitecture overview.\n")
      _write(dir.resolve("src/main/doxsite/glossary/architecture/runtime.dox"), "Runtime\n=======\n\n# HEAD\n\nreading=らんたいむ\n\n# Definition\nRuntime term.\n")
      _write(dir.resolve("src/main/doxsite/glossary/architecture/asuka.dox"), "あすか\n======\n\n# HEAD\n\n# Definition\nJapanese term.\n")
      _write(dir.resolve("src/main/doxsite/glossary/architecture/cloud.dox"), "Cloud\n=======\n\n# HEAD\n\n# Definition\nEnglish-only term.\n")
      _write(dir.resolve("doxsite.d/ja/index.html"), "stale ja\n")
      _write(dir.resolve("doxsite.d/en/index.html"), "stale en\n")
      _write(dir.resolve("doxsite-cache-work-in-progress.d/stale.error_msg"), "stale\n")
      val config = CozyBok.BuildConfig.create(List(dir.toString, "--strategy", "wip"))
      val runner = new RecordingRunner

      CozyBok.build(config, runner)

      assert(config.strategy == "work-in-progress")
      assert(runner.commands.exists { command =>
        command.take(4) == Vector("dox", "antora", "-strategy", "work-in-progress") &&
          command.contains("-publication") &&
          command.last == "src/main/doxsite"
      })
      assert(runner.commands.exists { command =>
        command.take(6) == Vector("dox", "site", "-strategy", "work-in-progress", "-output.scope.policy", "home_only") &&
          command.contains("-publication") &&
          command.contains("-publication.repository") &&
          command.contains("-publication.rdf.missing.policy") &&
          command.last == "src/main/doxsite"
      })
      assert(runner.commands.exists(_.contains("smartdox-antora:test")))
      assert(runner.commands.exists(_.contains("/workspace/website.d")))
      assert(_read(dir.resolve("website.d/index.html")).contains("KnowledgeHub BoKのHome画面"))
      assert(_read(dir.resolve("website.d/index.html")).contains("""<div class="bok-metric-label">Categories</div>"""))
      assert(_read(dir.resolve("website.d/index.html")).contains("""<div class="bok-metric-value">1</div>"""))
      assert(_read(dir.resolve("website.d/index.html")).contains("""<div class="bok-metric-label">RDF Triples</div>"""))
      assert(_read(dir.resolve("website.d/index.html")).contains("""<div class="bok-metric-value">42</div>"""))
      assert(_read(dir.resolve("website.d/index.html")).contains("""aria-label="BoK item distribution""""))
      assert(_read(dir.resolve("website.d/index.html")).contains("""data-chart="distribution-ratio""""))
      assert(_read(dir.resolve("website.d/index.html")).contains("""class="bok-chart-row"><span>Articles</span>"""))
      assert(_read(dir.resolve("website.d/index.html")).contains("""class="bok-chart-row"><span>Terms</span>"""))
      assert(_read(dir.resolve("website.d/index.html")).contains("""aria-label="BoK additions""""))
      assert(_read(dir.resolve("website.d/index.html")).contains("""data-chart="cumulative-date""""))
      assert(_read(dir.resolve("website.d/index.html")).contains("""bok-cumulative-line"""))
      assert(_read(dir.resolve("website.d/index.html")).contains("""class="bok-cumulative-line bok-cumulative-line-articles""""))
      assert(_read(dir.resolve("website.d/index.html")).contains("""class="bok-cumulative-line bok-cumulative-line-terms""""))
      assert(_read(dir.resolve("website.d/index.html")).contains("""bok-cumulative-marker-articles"""))
      assert(_read(dir.resolve("website.d/index.html")).contains("""bok-cumulative-marker-terms"""))
      assert(_read(dir.resolve("website.d/index.html")).contains("""datetime="2026-06-03""""))
      assert(_read(dir.resolve("website.d/index.html")).contains("""datetime="2026-06-04""""))
      assert(_read(dir.resolve("website.d/index.html")).contains("""A:1 T:3"""))
      assert(_read(dir.resolve("website.d/index.html")).contains("""2026-06-03 - 2026-06-04"""))
      assert(_read(dir.resolve("website.d/index.html")).contains("""<a class="bok-special-link" href="glossary/index.html">Glossary</a>"""))
      assert(_read(dir.resolve("website.d/index.html")).contains("""href="history/index.html""""))
      assert(_read(dir.resolve("website.d/index.html")).contains("""href="manual/index.html""""))
      assert(_read(dir.resolve("website.d/index.html")).contains("""class="bok-special-links""""))
      assert(_read(dir.resolve("website.d/glossary/index.html")).contains("""glossary/&lt;category&gt;/"""))
      assert(_read(dir.resolve("website.d/glossary/index.html")).contains("""href="architecture/runtime.html""""))
      assert(_read(dir.resolve("website.d/glossary/index.html")).contains("""<div class="bok-metric-label">Terms</div>"""))
      assert(_read(dir.resolve("website.d/glossary/index.html")).contains("""<div class="bok-metric-value">3</div>"""))
      assert(_read(dir.resolve("website.d/glossary/index.html")).contains("""href="../ja/glossary/index.html">日本語索引ページ</a>"""))
      assert(_read(dir.resolve("website.d/glossary/index.html")).contains("""href="../en/glossary/index.html">英語索引ページ</a>"""))
      assert(_read(dir.resolve("website.d/glossary/index.html")).contains("""id="recent-terms""""))
      assert(_read(dir.resolve("website.d/glossary/index.html")).contains("""class="bok-special-links""""))
      assert(Files.isRegularFile(dir.resolve("website.d/ja/glossary/index.html")))
      assert(Files.isRegularFile(dir.resolve("website.d/en/glossary/index.html")))
      assert(_read(dir.resolve("website.d/ja/glossary/index.html")).contains("""href="#index-あ">あ</a>"""))
      assert(_read(dir.resolve("website.d/ja/glossary/index.html")).contains("""id="index-あ""""))
      assert(_read(dir.resolve("website.d/ja/glossary/index.html")).contains("""href="../../glossary/architecture/asuka.html""""))
      assert(_read(dir.resolve("website.d/ja/glossary/index.html")).contains("""href="#index-ら">ら</a>"""))
      assert(_read(dir.resolve("website.d/ja/glossary/index.html")).contains("""id="index-ら""""))
      assert(_read(dir.resolve("website.d/ja/glossary/index.html")).contains("""href="../../glossary/architecture/runtime.html""""))
      assert(_read(dir.resolve("website.d/ja/glossary/index.html")).contains("""<span class="bok-term-reading">(らんたいむ)</span>"""))
      assert(!_read(dir.resolve("website.d/ja/glossary/index.html")).contains("""href="../../glossary/architecture/cloud.html""""))
      assert(_read(dir.resolve("website.d/en/glossary/index.html")).contains("""href="#index-c">C</a>"""))
      assert(_read(dir.resolve("website.d/en/glossary/index.html")).contains("""href="#index-r">R</a>"""))
      assert(_read(dir.resolve("website.d/en/glossary/index.html")).contains("""id="index-r""""))
      assert(_read(dir.resolve("website.d/en/glossary/index.html")).contains("""href="../../glossary/architecture/cloud.html""""))
      assert(_read(dir.resolve("website.d/en/glossary/index.html")).contains("""href="../../glossary/architecture/runtime.html""""))
      assert(!_read(dir.resolve("website.d/index.html")).contains("""class="navbar-item" href="glossary/index.html""""))
      assert(!_read(dir.resolve("website.d/index.html")).contains("""class="navbar-item" href="history/index.html""""))
      assert(!_read(dir.resolve("website.d/index.html")).contains("""class="navbar-item" href="manual/index.html""""))
      assert(!_read(dir.resolve("website.d/index.html")).contains("Lexicon"))
      assert(_read(dir.resolve("website.d/architecture/index.html")).contains("""class="bok-dashboard-grid""""))
      assert(_read(dir.resolve("website.d/architecture/index.html")).contains("""class="bok-dashboard-chart""""))
      assert(_read(dir.resolve("website.d/architecture/index.html")).contains("""aria-label="Architecture item distribution""""))
      assert(_read(dir.resolve("website.d/architecture/index.html")).contains("""data-chart="distribution-ratio""""))
      assert(_read(dir.resolve("website.d/architecture/index.html")).contains("""class="bok-chart-row"><span>Articles</span>"""))
      assert(_read(dir.resolve("website.d/architecture/index.html")).contains("""aria-label="Architecture additions""""))
      assert(_read(dir.resolve("website.d/architecture/index.html")).contains("""data-chart="cumulative-date""""))
      assert(_read(dir.resolve("website.d/architecture/index.html")).contains("""class="bok-cumulative-line bok-cumulative-line-articles""""))
      assert(_read(dir.resolve("website.d/architecture/index.html")).contains("""class="bok-cumulative-line bok-cumulative-line-terms""""))
      assert(_read(dir.resolve("website.d/architecture/index.html")).contains("""datetime="2026-06-03""""))
      assert(_read(dir.resolve("website.d/architecture/index.html")).contains("""datetime="2026-06-04""""))
      assert(_read(dir.resolve("website.d/architecture/index.html")).contains("<li>Articles: 1</li>"))
      assert(_read(dir.resolve("website.d/architecture/index.html")).contains("<li>Terms: 3</li>"))
      assert(_read(dir.resolve("website.d/architecture/index.html")).contains("""class="bok-special-link" href="../glossary/index.html""""))
      assert(_read(dir.resolve("website.d/architecture/index.html")).contains("""href="../glossary/architecture/runtime.html""""))
      assert(!_read(dir.resolve("website.d/architecture/index.html")).contains("""class="navbar-item" href="../glossary/index.html""""))
      assert(!_read(dir.resolve("website.d/architecture/index.html")).contains("""class="navbar-item" href="../history/index.html""""))
      assert(!_read(dir.resolve("website.d/architecture/index.html")).contains("""class="navbar-item" href="../manual/index.html""""))
      assert(!_read(dir.resolve("website.d/architecture/index.html")).contains("Lexicon"))
      assert(Files.isRegularFile(dir.resolve("src/main/antora-ui/build/ui-bundle.zip")))
      assert(!Files.exists(dir.resolve("doxsite.d/ja")))
      assert(!Files.exists(dir.resolve("doxsite.d/en")))
      assert(!Files.exists(dir.resolve("doxsite-cache-work-in-progress.d/stale.error_msg")))
      assert(!runner.commands.exists(_.headOption.contains("arcadia")))
    }
  }

  test("bok build succeeds without dashboard metadata and does not fabricate dashboard counts") {
    _with_temp_dir("cozy-bok-no-dashboard-metadata") { dir =>
      _write(dir.resolve(".cozy/config.yaml"), "bok:\n  docker-image: antora-image\n")
      _write(dir.resolve("src/main/doxsite/site.conf"), "site {\n  output {\n    locale_mode = \"single_locale_root\"\n  }\n}\n")
      _write(dir.resolve("src/main/doxsite/architecture/category.yaml"), "name: Architecture\ntitle: Architecture\ndescription: Architecture category.\n")
      _write(dir.resolve("src/main/doxsite/architecture/overview.dox"), "Overview\n========\n\n# HEAD\n\n## BRIEF\nArchitecture overview.\n")
      val config = CozyBok.BuildConfig.create(List(dir.toString, "--strategy", "wip"))

      CozyBok.build(config, new NoDashboardRunner)

      assert(!_read(dir.resolve("website.d/index.html")).contains("""<div class="bok-metric-label">Categories</div>"""))
      assert(_read(dir.resolve("website.d/architecture/index.html")).contains("Dashboard metadata is not available."))
      assert(!_read(dir.resolve("website.d/architecture/index.html")).contains("""class="bok-dashboard-grid""""))
    }
  }

  test("bok build renders legacy dashboard increments as total series") {
    _with_temp_dir("cozy-bok-legacy-dashboard") { dir =>
      _write(dir.resolve(".cozy/config.yaml"), "bok:\n  docker-image: antora-image\n")
      _write(dir.resolve("src/main/doxsite/site.conf"), "site {\n  output {\n    locale_mode = \"single_locale_root\"\n  }\n}\n")
      _write(dir.resolve("src/main/doxsite/architecture/category.yaml"), "name: Architecture\ntitle: Architecture\ndescription: Architecture category.\n")
      _write(dir.resolve("src/main/doxsite/architecture/overview.dox"), "Overview\n========\n\n# HEAD\n\n## BRIEF\nArchitecture overview.\n")
      val config = CozyBok.BuildConfig.create(List(dir.toString, "--strategy", "wip"))

      CozyBok.build(config, new LegacyDashboardRunner)

      val home = _read(dir.resolve("website.d/index.html"))
      assert(home.contains("""class="bok-cumulative-line bok-cumulative-line-total""""))
      assert(home.contains("""bok-cumulative-marker-total"""))
      assert(home.contains(">Total</span>"))
      assert(!home.contains("""class="bok-cumulative-line bok-cumulative-line-articles""""))
      assert(!home.contains("A:"))
    }
  }

  test("bok console links SmartDox generated history year page when available") {
    _with_temp_dir("cozy-bok-history-year") { dir =>
      _write(dir.resolve(".cozy/config.yaml"), "bok:\n  docker-image: antora-image\n")
      _write(dir.resolve("src/main/doxsite/site.conf"), "site {\n  output {\n    locale_mode = \"single_locale_root\"\n  }\n}\n")
      _write(dir.resolve("src/main/doxsite/glossary/category.yaml"), "name: Glossary\ntitle: 用語集\ndescription: BoK全体で共有する用語集。\n")
      _write(dir.resolve("src/main/doxsite/architecture/category.yaml"), "name: Architecture\ntitle: Architecture\ndescription: Architecture category.\n")
      _write(dir.resolve("src/main/doxsite/architecture/overview.dox"), "Overview\n========\n\n# HEAD\n\n## BRIEF\nArchitecture overview.\n")
      _write(dir.resolve("src/main/doxsite/glossary/architecture/runtime.dox"), "Runtime\n=======\n\n# HEAD\n\n# Definition\nRuntime term.\n")
      val config = CozyBok.BuildConfig.create(List(dir.toString, "--strategy", "wip"))
      val runner = new HistoryYearRunner

      CozyBok.build(config, runner)

      assert(_read(dir.resolve("website.d/index.html")).contains("""href="history/2026.html">History</a>"""))
      assert(_read(dir.resolve("website.d/architecture/index.html")).contains("""href="../history/2026.html">History</a>"""))
      assert(_read(dir.resolve("website.d/glossary/index.html")).contains("""href="../history/2026.html">History</a>"""))
      assert(_read(dir.resolve("website.d/ja/glossary/index.html")).contains("""href="../../history/2026.html">History</a>"""))
    }
  }

  test("bok build CLI docker image overrides config") {
    _with_temp_dir("cozy-bok-docker-override") { dir =>
      _write(dir.resolve(".cozy/config.yaml"), "bok:\n  docker-image: config-image\n")
      val config = CozyBok.BuildConfig.create(List(dir.toString, "--docker-image", "cli-image"))
      assert(config.dockerImage == "cli-image")
    }
  }

  test("bok build reads conf/cozy defaults before .cozy local overrides") {
    _with_temp_dir("cozy-bok-config-precedence") { dir =>
      _write(dir.resolve("conf/cozy/config.yaml"), "bok:\n  docker-image: shared-image\n")
      val shared = CozyBok.BuildConfig.create(List(dir.toString))
      assert(shared.dockerImage == "shared-image")

      _write(dir.resolve(".cozy/config.yaml"), "bok:\n  docker-image: local-image\n")
      val local = CozyBok.BuildConfig.create(List(dir.toString))
      assert(local.dockerImage == "local-image")
    }
  }

  test("bok build defaults to the standard Cozy toolchain Docker image") {
    _with_temp_dir("cozy-bok-default-docker") { dir =>
      val config = CozyBok.BuildConfig.create(List(dir.toString))
      assert(config.dockerImage == "ghcr.io/asami/cozy-toolchain:latest")
    }
  }

  test("bok build can use standard cozy docker image config when bok docker image is omitted") {
    _with_temp_dir("cozy-bok-standard-docker") { dir =>
      _write(dir.resolve(".cozy/config.yaml"), "cozy:\n  docker-image: cozy-config-image\n")
      val config = CozyBok.BuildConfig.create(List(dir.toString))
      assert(config.dockerImage == "cozy-config-image")
    }
  }

  test("bok build can override dox site output scope policy") {
    _with_temp_dir("cozy-bok-site-output-scope") { dir =>
      _write(dir.resolve(".cozy/config.yaml"),
        """bok:
          |  output:
          |    scope:
          |      policy: all
          |""".stripMargin)
      val config = CozyBok.BuildConfig.create(List(dir.toString))
      assert(config.siteOutputScopePolicy == "all")
    }
  }

  test("bok build can use pdf docker image config as compatibility fallback") {
    _with_temp_dir("cozy-bok-pdf-docker") { dir =>
      _write(dir.resolve(".cozy/config.yaml"), "pdf:\n  docker-image: pdf-config-image\n")
      val config = CozyBok.BuildConfig.create(List(dir.toString))
      assert(config.dockerImage == "pdf-config-image")
    }
  }

  test("bok build uses simplemodelingorg compatibility locale default from site.conf") {
    _with_temp_dir("cozy-bok-simplemodeling") { dir =>
      _write(dir.resolve(".cozy/config.yaml"), "bok:\n  docker-image: antora-image\n")
      _write(dir.resolve("src/main/doxsite/site.conf"),
        """simplemodelingorg = true
          |site {
          |  metadata {
          |    in_language = ["ja", "en"]
          |  }
          |}
          |""".stripMargin)
      val config = CozyBok.BuildConfig.create(List(dir.toString))
      val runner = new RecordingRunner

      CozyBok.build(config, runner)

      assert(runner.commands.exists(_.contains("/workspace/website.d/ja")))
      assert(runner.commands.exists(_.contains("/workspace/website.d/en")))
    }
  }

  test("bok build includes arcadia step only when enabled") {
    _with_temp_dir("cozy-bok-arcadia") { dir =>
      _write(dir.resolve(".cozy/config.yaml"),
        """bok:
          |  docker-image: antora-image
          |  arcadia:
          |    enabled: true
          |    source: src/main/arcadiasite
          |""".stripMargin)
      val config = CozyBok.BuildConfig.create(List(dir.toString))
      val runner = new RecordingRunner

      CozyBok.build(config, runner)

      assert(runner.commands.exists(_ == Vector("arcadia", "site", "src/main/arcadiasite", "arcadiasite.d")))
    }
  }

  test("bok build copies direct assets only in production when configured") {
    _with_temp_dir("cozy-bok-direct-assets") { dir =>
      _write(dir.resolve(".cozy/config.yaml"),
        """bok:
          |  docker-image: antora-image
          |  direct-assets:
          |    enabled: true
          |    items:
          |      - source: src/main/javascript/knowledge-graph
          |        destination: website.d/knowledge-graph
          |""".stripMargin)
      _write(dir.resolve("src/main/javascript/knowledge-graph/app.js"), "console.log('graph')\n")
      val config = CozyBok.BuildConfig.create(List(dir.toString, "--strategy", "production"))
      val runner = new RecordingRunner

      CozyBok.build(config, runner)

      assert(runner.commands.exists(_ == Vector("dox", "site-mark", "-strategy", "production", "-output.scope.policy", "all", "src/main/doxsite")))
      assert(Files.isRegularFile(dir.resolve("website.d/knowledge-graph/app.js")))
    }
  }

  test("bok build ignores unrelated yaml items when direct assets are enabled") {
    _with_temp_dir("cozy-bok-direct-assets-scope") { dir =>
      _write(dir.resolve(".cozy/config.yaml"),
        """bok:
          |  docker-image: antora-image
          |  direct-assets:
          |    enabled: true
          |publication:
          |  items:
          |    - source: unrelated/source
          |      destination: website.d/unrelated
          |""".stripMargin)
      _write(dir.resolve("unrelated/source/app.js"), "console.log('unrelated')\n")
      val config = CozyBok.BuildConfig.create(List(dir.toString, "--strategy", "production"))

      CozyBok.build(config, new RecordingRunner)

      assert(!Files.exists(dir.resolve("website.d/unrelated/app.js")))
    }
  }



  test("bok build applies publication config and CLI precedence") {
    _with_temp_dir("cozy-bok-publication-options") { dir =>
      _write(dir.resolve(".cozy/config.yaml"),
        """bok:
          |  publication: configured-publication
          |  warehouse: configured-warehouse
          |  rdf:
          |    merge-publication-artifacts: true
          |    missing-artifact-policy: warn
          |""".stripMargin)
      _write(dir.resolve("src/main/doxsite/site.conf"), "site { output { locale_mode = \"single_locale_root\" } }\n")
      val config = CozyBok.BuildConfig.create(List(
        dir.toString,
        "--strategy", "production",
        "--publication", dir.resolve("cli-publication").toString,
        "--warehouse", dir.resolve("cli-warehouse").toString,
        "--rdf-missing-artifact-policy", "fail"
      ))
      val runner = new RecordingRunner

      CozyBok.build(config, runner)

      val sitecommand = runner.commands.find(_.take(2) == Vector("dox", "site")).get
      assert(sitecommand.contains("-publication"))
      assert(sitecommand.contains(dir.resolve("cli-publication").toAbsolutePath.normalize().toString))
      assert(sitecommand.contains("-publication.repository"))
      assert(sitecommand.contains(dir.resolve("cli-warehouse").toAbsolutePath.normalize().toString))
      assert(sitecommand.contains("-publication.rdf.missing.policy"))
      assert(sitecommand.contains("fail"))
    }
  }

  test("bok build uses production RDF missing artifact failure by default") {
    _with_temp_dir("cozy-bok-production-rdf-policy") { dir =>
      val config = CozyBok.BuildConfig.create(List(dir.toString, "--strategy", "production"))
      val runner = new RecordingRunner

      CozyBok.build(config, runner)

      val sitecommand = runner.commands.find(_.take(2) == Vector("dox", "site")).get
      assert(sitecommand.contains("-publication.rdf.missing.policy"))
      assert(sitecommand.contains("fail"))
    }
  }

  test("bok publish-video discovers .video packages and writes publication registry") {
    _with_temp_dir("cozy-bok-publish-video") { dir =>
      val pkg = dir.resolve("src/main/doxsite/concepts/tutorial.video")
      _write(pkg.resolve("index.dox"), "# Tutorial\n")
      _write(pkg.resolve("script.json"), _video_script_json)
      _write(pkg.resolve("video.yaml"),
        """video:
          |  name: tutorial
          |title: Tutorial Video
          |version: 0.1.0
          |publish:
          |  module: textus
          |""".stripMargin)
      val runner = CozyVideoSpec.PublishingRunner()
      val config = CozyBok.PublicationConfig.create("publish-video", List(dir.toString))

      val results = CozyBok.publishVideo(config, CozyVideoSpec.RecordingVoicevoxClient(), runner)

      assert(results.size == 1)
      assert(Files.isRegularFile(dir.resolve("src/main/publication/tutorial.json")))
      assert(Files.isRegularFile(dir.resolve("warehouse/repository/video/textus/0.1.0/tutorial-0.1.0.mp4")))
      val sourcefiles = Files.walk(pkg).iterator().asScala.toVector.filter(Files.isRegularFile(_)).map(_.getFileName.toString)
      assert(!sourcefiles.exists(_.endsWith(".mp4")))
      assert(!sourcefiles.exists(_.endsWith(".ttl")))
      assert(!sourcefiles.exists(_.endsWith(".jsonld")))
      assert(!sourcefiles.exists(_.endsWith(".srt")))
    }
  }

  test("bok publish-video honors bok video enabled false") {
    _with_temp_dir("cozy-bok-publish-video-disabled") { dir =>
      val pkg = dir.resolve("src/main/doxsite/concepts/tutorial.video")
      _write(pkg.resolve("index.dox"), "# Tutorial\n")
      _write(pkg.resolve("script.json"), _video_script_json)
      _write(pkg.resolve("video.yaml"),
        """video:
          |  name: tutorial
          |title: Tutorial Video
          |version: 0.1.0
          |publish:
          |  module: textus
          |""".stripMargin)
      _write(dir.resolve(".cozy/config.yaml"),
        """bok:
          |  video:
          |    enabled: false
          |""".stripMargin)
      val config = CozyBok.PublicationConfig.create("publish-video", List(dir.toString))

      val results = CozyBok.publishVideo(config, CozyVideoSpec.RecordingVoicevoxClient(), CozyVideoSpec.PublishingRunner())

      assert(results.isEmpty)
      assert(!Files.exists(dir.resolve("src/main/publication/tutorial.json")))
      assert(!Files.exists(dir.resolve("warehouse/repository/video/textus/0.1.0/tutorial-0.1.0.mp4")))
    }
  }

  test("bok publish-video rejects .video.d work directories") {
    _with_temp_dir("cozy-bok-publish-video-workdir") { dir =>
      Files.createDirectories(dir.resolve("src/main/doxsite/concepts/bad.video.d"))
      val config = CozyBok.PublicationConfig.create("publish-video", List(dir.toString))

      val e = intercept[Throwable] {
        CozyBok.publishVideo(config, CozyVideoSpec.RecordingVoicevoxClient(), CozyVideoSpec.PublishingRunner())
      }

      assert(e.getMessage.contains("*.video.d is reserved"))
    }
  }

  test("bok publish validates upload workflow before publication update") {
    _with_temp_dir("cozy-bok-publish-upload-preflight") { dir =>
      val pkg = dir.resolve("src/main/doxsite/concepts/tutorial.video")
      _write(pkg.resolve("index.dox"), "# Tutorial\n")
      _write(pkg.resolve("script.json"), _video_script_json)
      _write(pkg.resolve("video.yaml"),
        """video:
          |  name: tutorial
          |title: Tutorial Video
          |version: 0.1.0
          |publish:
          |  module: textus
          |""".stripMargin)
      _write(dir.resolve("src/main/doxsite/site.conf"), "site { output { locale_mode = \"single_locale_root\" } }\n")
      val config = CozyBok.PublicationConfig.create("publish", List(dir.toString, "--strategy", "production"))
      val runner = new RecordingRunner

      val e = intercept[Throwable] {
        CozyBok.publish(config, runner, CozyVideoSpec.RecordingVoicevoxClient(), CozyVideoSpec.PublishingRunner())
      }

      assert(e.getMessage.contains("Missing bok workflow command"))
      assert(runner.commands.isEmpty)
      assert(!Files.exists(dir.resolve("src/main/publication/tutorial.json")))
      assert(!Files.exists(dir.resolve("warehouse/repository/video/textus/0.1.0/tutorial-0.1.0.mp4")))
    }
  }

  test("bok publish runs update publication then build then configured upload") {
    _with_temp_dir("cozy-bok-publish-flow") { dir =>
      _write(dir.resolve(".cozy/config.yaml"),
        """bok:
          |  docker-image: antora-image
          |  workflow:
          |    upload:
          |      command: "etc/upload.sh"
          |""".stripMargin)
      _write(dir.resolve("src/main/doxsite/site.conf"), "site { output { locale_mode = \"single_locale_root\" } }\n")
      val config = CozyBok.PublicationConfig.create("publish", List(dir.toString, "--strategy", "production"))
      val runner = new RecordingRunner

      CozyBok.publish(config, runner, CozyVideoSpec.RecordingVoicevoxClient(), CozyVideoSpec.PublishingRunner())

      assert(runner.commands.exists(_.take(2) == Vector("dox", "antora")))
      assert(runner.commands.exists(_.take(2) == Vector("dox", "site")))
      assert(runner.commands.last == Vector("sh", "-c", "etc/upload.sh"))
    }
  }

  test("cozy help lists BoK publication path options") {
    val help = _capture {
      Cozy.main(Array("--help"))
    }

    assert(help.contains("bok publish-video <project-dir> [--publication <dir>] [--warehouse <dir>]"))
    assert(help.contains("bok update-publication <project-dir> [--publication <dir>] [--warehouse <dir>]"))
    assert(help.contains("bok publish <project-dir> [--publication <dir>] [--warehouse <dir>]"))
  }

  test("bok preview validates port as integer metadata") {
    _with_temp_dir("cozy-bok-preview-port") { dir =>
      val e = intercept[Throwable] {
        CozyBok.preview(List(dir.toString, "--port", "not-int"), new RecordingRunner)
      }
      assert(e.getMessage.contains("not-int"))
    }
  }

  test("bok commit and upload require registered workflow commands") {
    _with_temp_dir("cozy-bok-workflow-missing") { dir =>
      val e = intercept[Throwable] {
        CozyBok.runWorkflow(CozyBok.WorkflowConfig.create("commit", List(dir.toString)), new RecordingRunner)
      }
      assert(e.getMessage.contains("bok.workflow.commit.command"))
    }
  }

  test("bok commit and upload run only registered workflow command") {
    _with_temp_dir("cozy-bok-workflow") { dir =>
      _write(dir.resolve(".cozy/config.yaml"),
        """bok:
          |  workflow:
          |    commit:
          |      command: "etc/website-commit.sh"
          |    upload:
          |      command: "etc/website-upload.sh"
          |""".stripMargin)
      val runner = new RecordingRunner

      CozyBok.runWorkflow(CozyBok.WorkflowConfig.create("commit", List(dir.toString)), runner)
      CozyBok.runWorkflow(CozyBok.WorkflowConfig.create("upload", List(dir.toString)), runner)

      assert(runner.commands == Vector(
        Vector("sh", "-c", "etc/website-commit.sh"),
        Vector("sh", "-c", "etc/website-upload.sh")
      ))
    }
  }



  private val _video_script_json: String =
    """{
      |  "title": "Tutorial Script",
      |  "voice": {"speaker": "ずんだもん", "style": "ノーマル"},
      |  "scenes": [
      |    {"id": "intro", "speaker": "ずんだもん", "line": "こんにちは", "duration": 1.0}
      |  ]
      |}
      |""".stripMargin

  private class RecordingRunner extends CozyBok.Runner {
    var calls = Vector.empty[(Vector[String], Path)]
    def commands: Vector[Vector[String]] = calls.map(_._1)
    def run(command: Vector[String], cwd: Path): Unit = {
      calls = calls :+ (command -> cwd)
      if (command.take(2) == Vector("dox", "site"))
        _write(cwd.resolve("doxsite.d/metadata/dashboard/site.json"), _dashboard_json)
    }
  }

  private class NoDashboardRunner extends RecordingRunner {
    override def run(command: Vector[String], cwd: Path): Unit =
      calls = calls :+ (command -> cwd)
  }

  private class LegacyDashboardRunner extends RecordingRunner {
    override def run(command: Vector[String], cwd: Path): Unit = {
      calls = calls :+ (command -> cwd)
      if (command.take(2) == Vector("dox", "site"))
        _write(cwd.resolve("doxsite.d/metadata/dashboard/site.json"), _legacy_dashboard_json)
    }
  }

  private class HistoryYearRunner extends RecordingRunner {
    override def run(command: Vector[String], cwd: Path): Unit = {
      super.run(command, cwd)
      _write(cwd.resolve("website.d/history/2026.html"), "smartdox-generated-history-year\n")
    }
  }

  private def _dashboard_json: String =
    """{
      |  "counts": {
      |    "category_count": 1,
      |    "article_count": 1,
      |    "glossary_term_count": 3,
      |    "total_item_count": 4
      |  },
      |  "rdf": {
      |    "resource_count": 4,
      |    "triple_count": 42,
      |    "subject_count": 12,
      |    "predicate_count": 9
      |  },
      |  "increments": {
      |    "scale": "day",
      |    "buckets": [
      |      {"label": "2026-06-03", "start_date": "2026-06-03", "end_date": "2026-06-03", "count": 1, "article_count": 1, "glossary_term_count": 0},
      |      {"label": "2026-06-04", "start_date": "2026-06-04", "end_date": "2026-06-04", "count": 3, "article_count": 0, "glossary_term_count": 3}
      |    ]
      |  },
      |  "categories": [
      |    {
      |      "name": "architecture",
      |      "title": "Architecture",
      |      "counts": {
      |        "category_count": 0,
      |        "article_count": 1,
      |        "glossary_term_count": 3,
      |        "total_item_count": 4
      |      },
      |      "increments": {
      |        "scale": "day",
      |        "buckets": [
      |          {"label": "2026-06-03", "start_date": "2026-06-03", "end_date": "2026-06-03", "count": 1, "article_count": 1, "glossary_term_count": 0},
      |          {"label": "2026-06-04", "start_date": "2026-06-04", "end_date": "2026-06-04", "count": 3, "article_count": 0, "glossary_term_count": 3}
      |        ]
      |      }
      |    }
      |  ]
      |}
      |""".stripMargin

  private def _legacy_dashboard_json: String =
    """{
      |  "counts": {
      |    "category_count": 1,
      |    "article_count": 1,
      |    "glossary_term_count": 0,
      |    "total_item_count": 1
      |  },
      |  "rdf": {
      |    "resource_count": 0,
      |    "triple_count": 0,
      |    "subject_count": 0,
      |    "predicate_count": 0
      |  },
      |  "increments": {
      |    "scale": "day",
      |    "buckets": [
      |      {"label": "2026-06-03", "start_date": "2026-06-03", "end_date": "2026-06-03", "count": 1},
      |      {"label": "2026-06-04", "start_date": "2026-06-04", "end_date": "2026-06-04", "count": 2}
      |    ]
      |  },
      |  "categories": []
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

  private def _capture(body: => Unit): String = {
    val out = new ByteArrayOutputStream()
    Console.withOut(out) {
      body
    }
    out.toString(StandardCharsets.UTF_8.name())
  }

  private def _zip_text(path: Path, name: String): String = {
    new String(_zip_bytes(path, name), StandardCharsets.UTF_8)
  }

  private def _zip_bytes(path: Path, name: String): Array[Byte] = {
    val in = new ZipInputStream(Files.newInputStream(path))
    try {
      Iterator.continually(in.getNextEntry).takeWhile(_ != null).find(_.getName == name) match {
        case Some(_) =>
          Stream.continually(in.read).takeWhile(_ != -1).map(_.toByte).toArray
        case None =>
          fail(s"Missing zip entry: ${name}")
      }
    } finally {
      in.close()
    }
  }

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
