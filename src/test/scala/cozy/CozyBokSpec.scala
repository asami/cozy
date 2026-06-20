package cozy

import cozy.bok.CozyBok
import cozy.video.CozyVideoSpec
import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path}
import java.util.zip.ZipInputStream
import scala.collection.JavaConverters._
import io.circe.parser
import org.scalatest.GivenWhenThen
import org.scalatest.wordspec.AnyWordSpec
import org.goldenport.test.matchers.SpecVocabulary

/*
 * @since   Jun.  3, 2026
 * @version Jun. 20, 2026
 * @author  ASAMI, Tomoharu
 */
class CozyBokSpec extends AnyWordSpec with GivenWhenThen with SpecVocabulary {
  "Cozy BoK source scaffolding" should {
    "source project scaffolds" which {
    "create a KnowledgeHub BoK source scaffold" in {
    _with_temp_dir("cozy-bok-create") { dir =>
      Given("a requested KnowledgeHub BoK project name, URL, and language")
      When("Cozy creates the BoK source scaffold")
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

      Then("the scaffold contains source, configuration, UI, manual, history, and RDF seed files")
      dir.resolve("README.md") should beRegularFile
      dir.resolve("STRUCTURE.md") should beRegularFile
      dir.resolve("conf/cozy/config.yaml") should beRegularFile
      dir.resolve("etc/website-stage.sh.proto") should beRegularFile
      dir.resolve("etc/website-upload.sh.proto") should beRegularFile
      dir.resolve("etc/website-stage.sh") shouldNot existPath
      dir.resolve("etc/website-upload.sh") shouldNot existPath
      dir.resolve("src/main/doxsite/site.conf") should beRegularFile
      dir.resolve("src/main/doxsite/glossary/category.yaml") should beRegularFile
      dir.resolve("src/main/doxsite/glossary/index.dox") shouldNot existPath
      dir.resolve("src/main/doxsite/history/category.yaml") should beRegularFile
      dir.resolve("src/main/doxsite/history/index.dox") should beRegularFile
      dir.resolve("src/main/doxsite/manual/index.dox") should beRegularFile
      dir.resolve("src/main/doxsite/rdf/site.ttl") should beRegularFile
      And("the scaffold contains site UI assets")
      dir.resolve("src/main/doxsite/assets/css/knowledgehub.css") should beRegularFile
      dir.resolve("src/main/antora-ui/build/ui-bundle.zip") should beRegularFile
      And("generated work directories are not created during scaffold creation")
      dir.resolve("src/main/doxsite/knowledgehub/category.yaml") shouldNot existPath
      dir.resolve("src/main/doxsite/site-structure.yaml") shouldNot existPath
      dir.resolve("website.d") shouldNot existPath
      _read(dir.resolve("src/main/doxsite/index.dox")) should startWith ("Home\n======")
      _read(dir.resolve("src/main/doxsite/index.dox")) should include ("# Dashboard")
      _read(dir.resolve("src/main/doxsite/history/index.dox")) should include ("# Dashboard")
      _read(dir.resolve("src/main/doxsite/manual/index.dox")) should include ("# Dashboard")
      _read(dir.resolve("src/main/doxsite/manual/index.dox")) should include ("cozy bok build")
      _read(dir.resolve("src/main/doxsite/manual/index.dox")) should include ("自動用語リンク対象外")
      _read(dir.resolve("conf/cozy/config.yaml")) should include ("cozy-toolchain")
      _read(dir.resolve("conf/cozy/config.yaml")) should include ("website-staging")
      _read(dir.resolve("conf/cozy/config.yaml")) should include ("workflow:")
      _read(dir.resolve("conf/cozy/config.yaml")) should include ("stage:")
      _read(dir.resolve("conf/cozy/config.yaml")) should include ("website-stage.sh.proto")
      _read(dir.resolve("conf/cozy/config.yaml")) should not include ("missing-artifact-policy: warn")
      _read(dir.resolve("src/main/doxsite/site.conf")) should include ("""locale_mode = "single_locale_root"""")
      _read(dir.resolve("src/main/doxsite/site.conf")) should include ("output.scope.policy = home_only")
      _read(dir.resolve("etc/website-stage.sh.proto")) should include ("WEBSITE_STAGING_DIR")
      _read(dir.resolve("etc/website-stage.sh.proto")) should include ("rsync -av --checksum --delete")
      _read(dir.resolve("etc/website-upload.sh.proto")) should include ("WEBSITE_SOURCE_DIR=${WEBSITE_SOURCE_DIR:-website.d}")
      _read(dir.resolve("etc/website-upload.sh.proto")) should include ("AWS_S3_URI")
      _read(dir.resolve("etc/website-upload.sh.proto")) should include ("aws s3 sync")
      _read(dir.resolve("etc/website-upload.sh.proto")) should include ("aws cloudfront create-invalidation")
      _read(dir.resolve("src/main/doxsite/index.dox")) should include ("published_at=")
      _read(dir.resolve("src/main/doxsite/history/index.dox")) should include ("published_at=")
      _read(dir.resolve("src/main/doxsite/manual/index.dox")) should include ("published_at=")
      _read(dir.resolve("README.md")) should not include ("site-structure")
      val css = _zip_text(dir.resolve("src/main/antora-ui/build/ui-bundle.zip"), "css/site.css")
      css should include (".navbar-menu")
      css should include (".nav-container")
      css should include (".lang-toggle")
      css should include ("a.glossary")
      css should include (".bok-special-links")
      css should include (".bok-dashboard-grid")
      css should include (".bok-cumulative-line")
      css should include (".bok-index-nav")
      _zip_text(dir.resolve("src/main/antora-ui/build/ui-bundle.zip"), "js/site.js") should include ("navbar-burger")
      _zip_text(dir.resolve("src/main/antora-ui/build/ui-bundle.zip"), "img/menu.svg") should include ("<svg")
      _zip_bytes(dir.resolve("src/main/antora-ui/build/ui-bundle.zip"), "font/roboto-latin-400-normal.woff2") should not be empty
      val header = _zip_text(dir.resolve("src/main/antora-ui/build/ui-bundle.zip"), "partials/header-content.hbs")
      header should not include ("""href="{{siteRootPath}}/glossary/index.html">Glossary</a>""")
      header should not include ("""href="{{siteRootPath}}/history/index.html">History</a>""")
      header should not include ("""href="{{siteRootPath}}/manual/index.html">Manual</a>""")
      header should not include ("Lexicon")
    }
  }

    "create a category with article and term seeds" in {
    _with_temp_dir("cozy-bok-create-category") { dir =>
      Given("an existing BoK source scaffold")
      CozyBok.create(CozyBok.CreateConfig.create(List("--save", dir.toString)))
      When("Cozy creates a category with article and glossary term seed metadata")
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

      Then("the category, article, and glossary files are written with dashboard metadata")
      dir.resolve("src/main/doxsite/knowledgehub/category.yaml") should beRegularFile
      dir.resolve("src/main/doxsite/knowledgehub/index.dox") should beRegularFile
      dir.resolve("src/main/doxsite/knowledgehub/knowledgehub-overview.dox") should beRegularFile
      dir.resolve("src/main/doxsite/glossary/knowledgehub/knowledgehub.dox") should beRegularFile
      _read(dir.resolve("src/main/doxsite/knowledgehub/index.dox")) should include ("""<a href="knowledgehub-overview.html">KnowledgeHub Overview</a>""")
      _read(dir.resolve("src/main/doxsite/knowledgehub/index.dox")) should include ("""<a href="../glossary/knowledgehub/knowledgehub.html">KnowledgeHub</a>""")
      _read(dir.resolve("src/main/doxsite/knowledgehub/index.dox")) should include ("# Dashboard")
      _read(dir.resolve("src/main/doxsite/knowledgehub/index.dox")) should include ("""class="bok-metric-card"""")
      _read(dir.resolve("src/main/doxsite/knowledgehub/index.dox")) should include ("""class="bok-dashboard-chart"""")
      _read(dir.resolve("src/main/doxsite/knowledgehub/index.dox")) should include ("- Articles: 1")
      _read(dir.resolve("src/main/doxsite/knowledgehub/index.dox")) should include ("- Terms: 1")
      _read(dir.resolve("src/main/doxsite/knowledgehub/knowledgehub-overview.dox")) should include ("published_at=")
      _read(dir.resolve("src/main/doxsite/glossary/knowledgehub/knowledgehub.dox")) should include ("published_at=")
      _read(dir.resolve("src/main/doxsite/glossary/knowledgehub/knowledgehub.dox")) should include ("reading=ナレッジハブ")
    }
  }

    }

    "command metadata" which {
    "reject equals-form options as non-canonical metadata" in {
    _with_temp_dir("cozy-bok-equals-options") { dir =>
      Given("a BoK command option written in --key=value form")
      When("the command metadata parser validates the arguments")
      val e = intercept[Throwable] {
        CozyBok.CreateConfig.create(List(s"--save=${dir}"))
      }
      Then("the parser rejects the non-canonical form and points to the supported syntax")
      e.getMessage should include ("--save <dir>")
    }
  }

  }

    }

  "Cozy BoK site build" should {
    "invoke SmartDox and render site" which {
    "map strategy and use docker image from config" in {
    _with_temp_dir("cozy-bok-build") { dir =>
      Given("a BoK project with SmartDox source, glossary data, dashboard metadata, and a configured Docker image")
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

      When("Cozy builds the BoK using the wip strategy")
      CozyBok.build(config, runner)

      Then("the SmartDox commands receive normalized strategy, publication, repository, and Docker settings")
      config.strategy shouldBe "work-in-progress"
      runner.commands should containWhere[Vector[String]] { command =>
        command.take(4) == Vector("dox", "antora", "-strategy", "work-in-progress") &&
          command.contains("-publication") &&
          command.last == "src/main/doxsite"
      }
      runner.commands should containWhere[Vector[String]] { command =>
        command.take(6) == Vector("dox", "site", "-strategy", "work-in-progress", "-output.scope.policy", "home_only") &&
          command.contains("-publication") &&
          command.contains("-publication.repository") &&
          command.contains("-publication.rdf.missing.policy") &&
          command.last == "src/main/doxsite"
      }
      runner.commands should containWhere[Vector[String]](_.contains("smartdox-antora:test"))
      runner.commands should containWhere[Vector[String]](_.contains("/workspace/website.d"))
      And("the generated site renders dashboard, glossary, history, manual, and navigation conventions")
      _read(dir.resolve("website.d/index.html")) should include ("KnowledgeHub BoKのHome画面")
      _read(dir.resolve("website.d/index.html")) should include ("""<div class="bok-metric-label">Categories</div>""")
      _read(dir.resolve("website.d/index.html")) should include ("""<div class="bok-metric-value">1</div>""")
      _read(dir.resolve("website.d/index.html")) should include ("""<div class="bok-metric-label">RDF Triples</div>""")
      _read(dir.resolve("website.d/index.html")) should include ("""<div class="bok-metric-value">42</div>""")
      _read(dir.resolve("website.d/index.html")) should include ("""aria-label="BoK item distribution"""")
      _read(dir.resolve("website.d/index.html")) should include ("""data-chart="distribution-ratio"""")
      _read(dir.resolve("website.d/index.html")) should include ("""class="bok-chart-row"><span>Articles</span>""")
      _read(dir.resolve("website.d/index.html")) should include ("""class="bok-chart-row"><span>Terms</span>""")
      _read(dir.resolve("website.d/index.html")) should include ("""aria-label="BoK additions"""")
      _read(dir.resolve("website.d/index.html")) should include ("""data-chart="cumulative-date"""")
      _read(dir.resolve("website.d/index.html")) should include ("""bok-cumulative-line""")
      _read(dir.resolve("website.d/index.html")) should include ("""class="bok-cumulative-line bok-cumulative-line-articles"""")
      _read(dir.resolve("website.d/index.html")) should include ("""class="bok-cumulative-line bok-cumulative-line-terms"""")
      _read(dir.resolve("website.d/index.html")) should include ("""bok-cumulative-marker-articles""")
      _read(dir.resolve("website.d/index.html")) should include ("""bok-cumulative-marker-terms""")
      _read(dir.resolve("website.d/index.html")) should include ("""datetime="2026-06-03"""")
      _read(dir.resolve("website.d/index.html")) should include ("""datetime="2026-06-04"""")
      _read(dir.resolve("website.d/index.html")) should include ("""A:1 T:3""")
      _read(dir.resolve("website.d/index.html")) should include ("""2026-06-03 - 2026-06-04""")
      _read(dir.resolve("website.d/index.html")) should include ("""<a class="bok-special-link" href="glossary/index.html">Glossary</a>""")
      _read(dir.resolve("website.d/index.html")) should include ("""href="history/index.html"""")
      _read(dir.resolve("website.d/index.html")) should include ("""href="manual/index.html"""")
      _read(dir.resolve("website.d/index.html")) should include ("""class="bok-special-links"""")
      _read(dir.resolve("website.d/glossary/index.html")) should include ("""glossary/&lt;category&gt;/""")
      _read(dir.resolve("website.d/glossary/index.html")) should include ("""href="architecture/runtime.html"""")
      _read(dir.resolve("website.d/glossary/index.html")) should include ("""<div class="bok-metric-label">Terms</div>""")
      _read(dir.resolve("website.d/glossary/index.html")) should include ("""<div class="bok-metric-value">3</div>""")
      _read(dir.resolve("website.d/glossary/index.html")) should include ("""href="../ja/glossary/index.html">日本語索引ページ</a>""")
      _read(dir.resolve("website.d/glossary/index.html")) should include ("""href="../en/glossary/index.html">英語索引ページ</a>""")
      _read(dir.resolve("website.d/glossary/index.html")) should include ("""id="recent-terms"""")
      _read(dir.resolve("website.d/glossary/index.html")) should include ("""class="bok-special-links"""")
      dir.resolve("website.d/ja/glossary/index.html") should beRegularFile
      dir.resolve("website.d/en/glossary/index.html") should beRegularFile
      _read(dir.resolve("website.d/ja/glossary/index.html")) should include ("""href="#index-あ">あ</a>""")
      _read(dir.resolve("website.d/ja/glossary/index.html")) should include ("""id="index-あ"""")
      _read(dir.resolve("website.d/ja/glossary/index.html")) should include ("""href="../../glossary/architecture/asuka.html"""")
      _read(dir.resolve("website.d/ja/glossary/index.html")) should include ("""href="#index-ら">ら</a>""")
      _read(dir.resolve("website.d/ja/glossary/index.html")) should include ("""id="index-ら"""")
      _read(dir.resolve("website.d/ja/glossary/index.html")) should include ("""href="../../glossary/architecture/runtime.html"""")
      _read(dir.resolve("website.d/ja/glossary/index.html")) should include ("""<span class="bok-term-reading">(らんたいむ)</span>""")
      _read(dir.resolve("website.d/ja/glossary/index.html")) should not include ("""href="../../glossary/architecture/cloud.html"""")
      _read(dir.resolve("website.d/en/glossary/index.html")) should include ("""href="#index-c">C</a>""")
      _read(dir.resolve("website.d/en/glossary/index.html")) should include ("""href="#index-r">R</a>""")
      _read(dir.resolve("website.d/en/glossary/index.html")) should include ("""id="index-r"""")
      _read(dir.resolve("website.d/en/glossary/index.html")) should include ("""href="../../glossary/architecture/cloud.html"""")
      _read(dir.resolve("website.d/en/glossary/index.html")) should include ("""href="../../glossary/architecture/runtime.html"""")
      _read(dir.resolve("website.d/index.html")) should not include ("""class="navbar-item" href="glossary/index.html"""")
      _read(dir.resolve("website.d/index.html")) should not include ("""class="navbar-item" href="history/index.html"""")
      _read(dir.resolve("website.d/index.html")) should not include ("""class="navbar-item" href="manual/index.html"""")
      _read(dir.resolve("website.d/index.html")) should not include ("Lexicon")
      _read(dir.resolve("website.d/architecture/index.html")) should include ("""class="bok-dashboard-grid"""")
      _read(dir.resolve("website.d/architecture/index.html")) should include ("""class="bok-dashboard-chart"""")
      _read(dir.resolve("website.d/architecture/index.html")) should include ("""aria-label="Architecture item distribution"""")
      _read(dir.resolve("website.d/architecture/index.html")) should include ("""data-chart="distribution-ratio"""")
      _read(dir.resolve("website.d/architecture/index.html")) should include ("""class="bok-chart-row"><span>Articles</span>""")
      _read(dir.resolve("website.d/architecture/index.html")) should include ("""aria-label="Architecture additions"""")
      _read(dir.resolve("website.d/architecture/index.html")) should include ("""data-chart="cumulative-date"""")
      _read(dir.resolve("website.d/architecture/index.html")) should include ("""class="bok-cumulative-line bok-cumulative-line-articles"""")
      _read(dir.resolve("website.d/architecture/index.html")) should include ("""class="bok-cumulative-line bok-cumulative-line-terms"""")
      _read(dir.resolve("website.d/architecture/index.html")) should include ("""datetime="2026-06-03"""")
      _read(dir.resolve("website.d/architecture/index.html")) should include ("""datetime="2026-06-04"""")
      _read(dir.resolve("website.d/architecture/index.html")) should include ("<li>Articles: 1</li>")
      _read(dir.resolve("website.d/architecture/index.html")) should include ("<li>Terms: 3</li>")
      _read(dir.resolve("website.d/architecture/index.html")) should include ("""class="bok-special-link" href="../glossary/index.html"""")
      _read(dir.resolve("website.d/architecture/index.html")) should include ("""href="../glossary/architecture/runtime.html"""")
      _read(dir.resolve("website.d/architecture/index.html")) should not include ("""class="navbar-item" href="../glossary/index.html"""")
      _read(dir.resolve("website.d/architecture/index.html")) should not include ("""class="navbar-item" href="../history/index.html"""")
      _read(dir.resolve("website.d/architecture/index.html")) should not include ("""class="navbar-item" href="../manual/index.html"""")
      _read(dir.resolve("website.d/architecture/index.html")) should not include ("Lexicon")
      dir.resolve("src/main/antora-ui/build/ui-bundle.zip") should beRegularFile
      dir.resolve("doxsite.d/ja") shouldNot existPath
      dir.resolve("doxsite.d/en") shouldNot existPath
      dir.resolve("doxsite-cache-work-in-progress.d/stale.error_msg") shouldNot existPath
      runner.commands should notContainWhere[Vector[String]](_.headOption.contains("arcadia"))
    }
  }

    "succeed without dashboard metadata and not fabricate dashboard counts" in {
    _with_temp_dir("cozy-bok-no-dashboard-metadata") { dir =>
      Given("a BoK project whose SmartDox output has no dashboard metadata")
      _write(dir.resolve(".cozy/config.yaml"), "bok:\n  docker-image: antora-image\n")
      _write(dir.resolve("src/main/doxsite/site.conf"), "site {\n  output {\n    locale_mode = \"single_locale_root\"\n  }\n}\n")
      _write(dir.resolve("src/main/doxsite/architecture/category.yaml"), "name: Architecture\ntitle: Architecture\ndescription: Architecture category.\n")
      _write(dir.resolve("src/main/doxsite/architecture/overview.dox"), "Overview\n========\n\n# HEAD\n\n## BRIEF\nArchitecture overview.\n")
      val config = CozyBok.BuildConfig.create(List(dir.toString, "--strategy", "wip"))

      When("Cozy builds the site")
      CozyBok.build(config, new NoDashboardRunner)

      Then("the build succeeds and missing dashboard data is reported without fabricated metrics")
      _read(dir.resolve("website.d/index.html")) should not include ("""<div class="bok-metric-label">Categories</div>""")
      _read(dir.resolve("website.d/architecture/index.html")) should include ("Dashboard metadata is not available.")
      _read(dir.resolve("website.d/architecture/index.html")) should not include ("""class="bok-dashboard-grid"""")
    }
  }

    "render legacy dashboard increments as total series" in {
    _with_temp_dir("cozy-bok-legacy-dashboard") { dir =>
      Given("a BoK project with legacy dashboard increment metadata")
      _write(dir.resolve(".cozy/config.yaml"), "bok:\n  docker-image: antora-image\n")
      _write(dir.resolve("src/main/doxsite/site.conf"), "site {\n  output {\n    locale_mode = \"single_locale_root\"\n  }\n}\n")
      _write(dir.resolve("src/main/doxsite/architecture/category.yaml"), "name: Architecture\ntitle: Architecture\ndescription: Architecture category.\n")
      _write(dir.resolve("src/main/doxsite/architecture/overview.dox"), "Overview\n========\n\n# HEAD\n\n## BRIEF\nArchitecture overview.\n")
      val config = CozyBok.BuildConfig.create(List(dir.toString, "--strategy", "wip"))

      When("Cozy renders the dashboard")
      CozyBok.build(config, new LegacyDashboardRunner)

      Then("the increment graph is rendered as the legacy total series")
      val home = _read(dir.resolve("website.d/index.html"))
      home should include ("""class="bok-cumulative-line bok-cumulative-line-total"""")
      home should include ("""bok-cumulative-marker-total""")
      home should include (">Total</span>")
      home should not include ("""class="bok-cumulative-line bok-cumulative-line-articles"""")
      home should not include ("A:")
    }
  }

    "link SmartDox generated history year page when available" in {
    _with_temp_dir("cozy-bok-history-year") { dir =>
      Given("a BoK site where SmartDox generated a yearly history page")
      _write(dir.resolve(".cozy/config.yaml"), "bok:\n  docker-image: antora-image\n")
      _write(dir.resolve("src/main/doxsite/site.conf"), "site {\n  output {\n    locale_mode = \"single_locale_root\"\n  }\n}\n")
      _write(dir.resolve("src/main/doxsite/glossary/category.yaml"), "name: Glossary\ntitle: 用語集\ndescription: BoK全体で共有する用語集。\n")
      _write(dir.resolve("src/main/doxsite/architecture/category.yaml"), "name: Architecture\ntitle: Architecture\ndescription: Architecture category.\n")
      _write(dir.resolve("src/main/doxsite/architecture/overview.dox"), "Overview\n========\n\n# HEAD\n\n## BRIEF\nArchitecture overview.\n")
      _write(dir.resolve("src/main/doxsite/glossary/architecture/runtime.dox"), "Runtime\n=======\n\n# HEAD\n\n# Definition\nRuntime term.\n")
      val config = CozyBok.BuildConfig.create(List(dir.toString, "--strategy", "wip"))
      val runner = new HistoryYearRunner

      When("Cozy post-processes site navigation")
      CozyBok.build(config, runner)

      Then("home, category, glossary, and index pages link to the generated history year page")
      _read(dir.resolve("website.d/index.html")) should include ("""href="history/2026.html">History</a>""")
      _read(dir.resolve("website.d/architecture/index.html")) should include ("""href="../history/2026.html">History</a>""")
      _read(dir.resolve("website.d/glossary/index.html")) should include ("""href="../history/2026.html">History</a>""")
      _read(dir.resolve("website.d/ja/glossary/index.html")) should include ("""href="../../history/2026.html">History</a>""")
    }
  }

    }

    "resolve build configuration" which {
    "let CLI docker image override config" in {
    _with_temp_dir("cozy-bok-docker-override") { dir =>
      Given("a BoK config Docker image and a CLI Docker image")
      _write(dir.resolve(".cozy/config.yaml"), "bok:\n  docker-image: config-image\n")
      When("build configuration is resolved")
      val config = CozyBok.BuildConfig.create(List(dir.toString, "--docker-image", "cli-image"))
      Then("the CLI Docker image takes precedence")
      config.dockerImage shouldBe "cli-image"
    }
  }

    "read conf/cozy defaults before .cozy local overrides" in {
    _with_temp_dir("cozy-bok-config-precedence") { dir =>
      Given("shared BoK defaults under conf/cozy")
      _write(dir.resolve("conf/cozy/config.yaml"), "bok:\n  docker-image: shared-image\n")
      When("build configuration is resolved")
      val shared = CozyBok.BuildConfig.create(List(dir.toString))
      Then("shared defaults are used first")
      shared.dockerImage shouldBe "shared-image"

      Given("local overrides under .cozy")
      _write(dir.resolve(".cozy/config.yaml"), "bok:\n  docker-image: local-image\n")
      When("build configuration is resolved again")
      val local = CozyBok.BuildConfig.create(List(dir.toString))
      Then("local overrides take precedence")
      local.dockerImage shouldBe "local-image"
    }
  }

    "default to the standard Cozy toolchain Docker image" in {
    _with_temp_dir("cozy-bok-default-docker") { dir =>
      Given("a BoK project without Docker image settings")
      When("build configuration is resolved")
      val config = CozyBok.BuildConfig.create(List(dir.toString))
      Then("the canonical Cozy toolchain image is selected")
      config.dockerImage shouldBe "ghcr.io/asami/cozy-toolchain:latest"
    }
  }

    "use standard cozy docker image config when bok docker image is omitted" in {
    _with_temp_dir("cozy-bok-standard-docker") { dir =>
      Given("a project-level Cozy Docker image setting and no BoK-specific Docker image")
      _write(dir.resolve(".cozy/config.yaml"), "cozy:\n  docker-image: cozy-config-image\n")
      When("build configuration is resolved")
      val config = CozyBok.BuildConfig.create(List(dir.toString))
      Then("the standard Cozy Docker image setting is used as the BoK fallback")
      config.dockerImage shouldBe "cozy-config-image"
    }
  }

    "override dox site output scope policy" in {
    _with_temp_dir("cozy-bok-site-output-scope") { dir =>
      Given("a BoK config with an explicit output scope policy")
      _write(dir.resolve(".cozy/config.yaml"),
        """bok:
          |  output:
          |    scope:
          |      policy: all
          |""".stripMargin)
      When("build configuration is resolved")
      val config = CozyBok.BuildConfig.create(List(dir.toString))
      Then("the configured output scope policy is passed to SmartDox")
      config.siteOutputScopePolicy shouldBe "all"
    }
  }

    "use pdf docker image config as compatibility fallback" in {
    _with_temp_dir("cozy-bok-pdf-docker") { dir =>
      Given("a legacy PDF Docker image setting and no newer Cozy or BoK Docker image setting")
      _write(dir.resolve(".cozy/config.yaml"), "pdf:\n  docker-image: pdf-config-image\n")
      When("build configuration is resolved")
      val config = CozyBok.BuildConfig.create(List(dir.toString))
      Then("the PDF Docker image is used as compatibility fallback")
      config.dockerImage shouldBe "pdf-config-image"
    }
  }

    }

    "inspect and repair BoK roots" which {
      "resolve a nested doxsite path back to the BoK project root" in {
        _with_temp_dir("cozy-bok-root-resolution") { dir =>
          Given("a BoK project and a category path inside src/main/doxsite")
          _write(dir.resolve(".cozy/config.yaml"), "bok:\n  docker-image: config-image\n")
          _write(dir.resolve("src/main/doxsite/site.conf"), "site {}\n")
          _write(dir.resolve("src/main/doxsite/concept/category.yaml"), "name: Concept\n")
          val nested = dir.resolve("src/main/doxsite/concept")

          When("build configuration is resolved from the nested path")
          val config = CozyBok.BuildConfig.create(List(nested.toString))

          Then("Cozy uses the BoK project root for all build paths")
          config.project shouldBe dir.toAbsolutePath.normalize
          config.sourcePath shouldBe dir.resolve("src/main/doxsite")
          config.dockerImage shouldBe "config-image"
        }
      }

      "report BoK root, signals, and safe repair candidates" in {
        _with_temp_dir("cozy-bok-doctor") { dir =>
          Given("a BoK project with a legacy Docker image and incomplete generated-directory ignores")
          _write(dir.resolve(".cozy/config.yaml"), "cozy:\n  docker-image: simplemodeling/cozy-toolchain:latest\nbok:\n  source: src/main/doxsite\n")
          _write(dir.resolve("src/main/doxsite/site.conf"), "site {}\n")
          _write(dir.resolve(".gitignore"), "/target/\n")

          When("Cozy inspects the BoK project in dry-run fix mode")
          val output = _capture {
            CozyBok.doctor(CozyBok.DoctorConfig.create(List(dir.resolve("src/main/doxsite").toString, "--fix", "--dry-run"), fix = false))
          }

          Then("the diagnostic identifies the root and the planned non-destructive repairs")
          output should include ("status: needs-fix")
          output should include (s"root: ${dir.toAbsolutePath.normalize}")
          output should include ("src/main/doxsite/site.conf")
          output should include ("Legacy Docker image reference found")
          output should include ("Replace legacy Docker image")
          output should include ("Append generated/work directory ignores")
          _read(dir.resolve(".cozy/config.yaml")) should include ("simplemodeling/cozy-toolchain:latest")
          _read(dir.resolve(".gitignore")) should not include ("/website.d/")
        }
      }

      "apply safe BoK repairs without changing source content" in {
        _with_temp_dir("cozy-bok-fix") { dir =>
          Given("a BoK project whose operational config and generated-directory ignores are stale")
          _write(dir.resolve(".cozy/config.yaml"), "cozy:\n  docker-image: simplemodeling/cozy-toolchain:latest\nbok:\n  source: src/main/doxsite\n")
          _write(dir.resolve("src/main/doxsite/site.conf"), "site {}\n")
          _write(dir.resolve("src/main/doxsite/concept/index.dox"), "Concept\n=======\n")
          _write(dir.resolve(".gitignore"), "/target/\n")

          When("Cozy applies safe BoK fixes")
          _capture {
            CozyBok.doctor(CozyBok.DoctorConfig.create(List(dir.toString), fix = true))
          }

          Then("the canonical Docker image and generated-directory ignores are updated")
          _read(dir.resolve(".cozy/config.yaml")) should include ("ghcr.io/asami/cozy-toolchain:latest")
          _read(dir.resolve(".cozy/config.yaml")) should not include ("simplemodeling/cozy-toolchain:latest")
          _read(dir.resolve(".gitignore")) should include ("/website.d/")
          _read(dir.resolve(".gitignore")) should include ("/doxsite.d/")
          _read(dir.resolve(".gitignore")) should include ("/antora.d/")
          And("the source article remains untouched")
          _read(dir.resolve("src/main/doxsite/concept/index.dox")) shouldBe "Concept\n=======\n"
        }
      }
    }

    "invoke SmartDox compatibility output" which {
    "use simplemodelingorg compatibility locale default from site.conf" in {
    _with_temp_dir("cozy-bok-simplemodeling") { dir =>
      Given("a simplemodeling.org compatible site.conf with Japanese and English languages")
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

      When("Cozy builds the BoK")
      CozyBok.build(config, runner)

      Then("localized website outputs are planned for both ja and en")
      runner.commands should containWhere[Vector[String]](_.contains("/workspace/website.d/ja"))
      runner.commands should containWhere[Vector[String]](_.contains("/workspace/website.d/en"))
    }
  }

    }

    "run optional build extensions" which {
    "include arcadia step only when enabled" in {
    _with_temp_dir("cozy-bok-arcadia") { dir =>
      Given("a BoK config with Arcadia site generation enabled")
      _write(dir.resolve(".cozy/config.yaml"),
        """bok:
          |  docker-image: antora-image
          |  arcadia:
          |    enabled: true
          |    source: src/main/arcadiasite
          |""".stripMargin)
      val config = CozyBok.BuildConfig.create(List(dir.toString))
      val runner = new RecordingRunner

      When("Cozy builds the BoK")
      CozyBok.build(config, runner)

      Then("the Arcadia generation step is included exactly when configured")
      runner.commands should contain (Vector("arcadia", "site", "src/main/arcadiasite", "arcadiasite.d"))
    }
  }

    "copy direct assets only in production when configured" in {
    _with_temp_dir("cozy-bok-direct-assets") { dir =>
      Given("a production BoK build with configured direct assets")
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

      When("Cozy builds the site")
      CozyBok.build(config, runner)

      Then("the direct assets are copied into the generated website")
      runner.commands should contain (Vector("dox", "site-mark", "-strategy", "production", "-output.scope.policy", "all", "src/main/doxsite"))
      dir.resolve("website.d/knowledge-graph/app.js") should beRegularFile
    }
  }

    "ignore unrelated yaml items when direct assets are enabled" in {
    _with_temp_dir("cozy-bok-direct-assets-scope") { dir =>
      Given("a BoK config containing direct assets and unrelated publication YAML items")
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

      When("Cozy builds the site")
      CozyBok.build(config, new RecordingRunner)

      Then("only BoK direct asset settings are applied")
      dir.resolve("website.d/unrelated/app.js") shouldNot existPath
    }
  }



    }

    "resolve publication build settings" which {
    "apply publication config and CLI precedence" in {
    _with_temp_dir("cozy-bok-publication-options") { dir =>
      Given("publication and warehouse defaults in config plus overriding CLI options")
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

      When("Cozy builds the BoK in production mode")
      CozyBok.build(config, runner)

      Then("SmartDox receives the CLI publication, repository, and RDF missing policy values")
      val sitecommand = runner.commands.find(_.take(2) == Vector("dox", "site")).get
      sitecommand should contain ("-publication")
      sitecommand should contain (dir.resolve("cli-publication").toAbsolutePath.normalize().toString)
      sitecommand should contain ("-publication.repository")
      sitecommand should contain (dir.resolve("cli-warehouse").toAbsolutePath.normalize().toString)
      sitecommand should contain ("-publication.rdf.missing.policy")
      sitecommand should contain ("fail")
    }
  }

    "use production RDF missing artifact failure by default" in {
    _with_temp_dir("cozy-bok-production-rdf-policy") { dir =>
      Given("a production BoK build without an explicit RDF missing artifact policy")
      val config = CozyBok.BuildConfig.create(List(dir.toString, "--strategy", "production"))
      val runner = new RecordingRunner

      When("build configuration is translated to SmartDox site arguments")
      CozyBok.build(config, runner)

      Then("missing RDF artifacts default to fail")
      val sitecommand = runner.commands.find(_.take(2) == Vector("dox", "site")).get
      sitecommand should contain ("-publication.rdf.missing.policy")
      sitecommand should contain ("fail")
    }
  }

  }

    }

  "Cozy BoK publication" should {
    "publish video packages" which {
    "discover .video packages and write publication registry" in {
    _with_temp_dir("cozy-bok-publish-video") { dir =>
      Given("a BoK source tree containing a valid .video package")
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

      When("Cozy publishes video metadata for the BoK")
      val results = CozyBok.publishVideo(config, CozyVideoSpec.RecordingVoicevoxClient(), runner)

      Then("publication registry entries and warehouse video artifacts are created outside the source package")
      results.size shouldBe 1
      dir.resolve("src/main/publication/tutorial.json") should beRegularFile
      dir.resolve("warehouse/repository/video/textus/0.1.0/tutorial-0.1.0.mp4") should beRegularFile
      val sourcefiles = Files.walk(pkg).iterator().asScala.toVector.filter(Files.isRegularFile(_)).map(_.getFileName.toString)
      sourcefiles should notContainWhere[String](_.endsWith(".mp4"))
      sourcefiles should notContainWhere[String](_.endsWith(".ttl"))
      sourcefiles should notContainWhere[String](_.endsWith(".jsonld"))
      sourcefiles should notContainWhere[String](_.endsWith(".srt"))
    }
  }

    "honor bok video enabled false" in {
    _with_temp_dir("cozy-bok-publish-video-disabled") { dir =>
      Given("a BoK source tree with a .video package and video publication disabled")
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

      When("Cozy publishes video metadata")
      val results = CozyBok.publishVideo(config, CozyVideoSpec.RecordingVoicevoxClient(), CozyVideoSpec.PublishingRunner())

      Then("no video publication registry or warehouse artifacts are written")
      results shouldBe empty
      dir.resolve("src/main/publication/tutorial.json") shouldNot existPath
      dir.resolve("warehouse/repository/video/textus/0.1.0/tutorial-0.1.0.mp4") shouldNot existPath
    }
  }

    "reject .video.d work directories" in {
    _with_temp_dir("cozy-bok-publish-video-workdir") { dir =>
      Given("a BoK source tree containing a .video.d work directory")
      Files.createDirectories(dir.resolve("src/main/doxsite/concepts/bad.video.d"))
      val config = CozyBok.PublicationConfig.create("publish-video", List(dir.toString))

      When("Cozy discovers video packages")
      val e = intercept[Throwable] {
        CozyBok.publishVideo(config, CozyVideoSpec.RecordingVoicevoxClient(), CozyVideoSpec.PublishingRunner())
      }

      Then("the generated/work directory naming is rejected")
      e.getMessage should include ("*.video.d is reserved")
    }
  }

    }

    "execute one-stop publish" which {
    "validate upload workflow before publication update" in {
    _with_temp_dir("cozy-bok-publish-upload-preflight") { dir =>
      Given("a one-stop publish request without configured upload workflow")
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

      When("Cozy runs publish preflight")
      val e = intercept[Throwable] {
        CozyBok.publish(config, runner, CozyVideoSpec.RecordingVoicevoxClient(), CozyVideoSpec.PublishingRunner())
      }

      Then("publication update, build, upload, and artifact writes are not started")
      e.getMessage should include ("Missing bok workflow command")
      runner.commands shouldBe empty
      dir.resolve("src/main/publication/tutorial.json") shouldNot existPath
      dir.resolve("warehouse/repository/video/textus/0.1.0/tutorial-0.1.0.mp4") shouldNot existPath
    }
  }

    "run update publication then build then configured upload" in {
    _with_temp_dir("cozy-bok-publish-flow") { dir =>
      Given("a BoK project with a configured upload workflow")
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

      When("Cozy runs one-stop publish")
      CozyBok.publish(config, runner, CozyVideoSpec.RecordingVoicevoxClient(), CozyVideoSpec.PublishingRunner())

      Then("publication update, site build, optional stage skip, and upload execute in deterministic order")
      runner.commands should containWhere[Vector[String]](_.take(2) == Vector("dox", "antora"))
      runner.commands should containWhere[Vector[String]](_.take(2) == Vector("dox", "site"))
      runner.commands.last shouldBe Vector("sh", "-c", "etc/upload.sh")
    }
  }

    "print planned steps in dry-run without publication build or upload side effects" in {
    _with_temp_dir("cozy-bok-publish-dry-run") { dir =>
      Given("a BoK project with video packages and upload workflow configuration")
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
          |  workflow:
          |    upload:
          |      command: "etc/upload.sh"
          |""".stripMargin)
      _write(dir.resolve("src/main/doxsite/site.conf"), "site { output { locale_mode = \"single_locale_root\" } }\n")
      val config = CozyBok.PublicationConfig.create("publish", List(dir.toString, "--dry-run", "--strategy", "production"))
      val runner = new RecordingRunner

      When("Cozy runs one-stop publish in dry-run mode")
      val out = _capture {
        CozyBok.publish(config, runner, CozyVideoSpec.RecordingVoicevoxClient(), CozyVideoSpec.PublishingRunner())
      }

      Then("planned steps and manifest are written without publication, warehouse, site, or upload side effects")
      out should include ("bok publish dry-run")
      out should include ("update-publication")
      out should include ("build")
      out should include ("upload")
      runner.commands shouldBe empty
      dir.resolve("src/main/publication") shouldNot existPath
      dir.resolve("warehouse") shouldNot existPath
      dir.resolve("website.d") shouldNot existPath
      dir.resolve("doxsite.d") shouldNot existPath
      val manifest = _manifest(dir)
      manifest.hcursor.downField("dryRun").as[Boolean].fold(throw _, identity) shouldBe true
      manifest.hcursor.downField("steps").downArray.downField("name").as[String].fold(throw _, identity) shouldBe "preflight"
      _read(dir.resolve("target/cozy-bok/publish/latest/manifest.json")) should include ("\"planned\"")
    }
  }

    "accept external publication and warehouse paths" in {
    _with_temp_dir("cozy-bok-publish-external-project") { dir =>
      _with_temp_dir("cozy-bok-publish-external-output") { external =>
        Given("publication and warehouse paths outside the BoK project directory")
        _write(dir.resolve("src/main/doxsite/site.conf"), "site { output { locale_mode = \"single_locale_root\" } }\n")
        _write(dir.resolve(".cozy/config.yaml"),
          """bok:
            |  video:
            |    enabled: false
            |  workflow:
            |    upload:
            |      command: "etc/upload.sh"
            |""".stripMargin)
        val publication = external.resolve("publication")
        val warehouse = external.resolve("warehouse")
        val config = CozyBok.PublicationConfig.create("publish", List(
          dir.toString,
          "--dry-run",
          "--publication", publication.toString,
          "--warehouse", warehouse.toString
        ))
        val runner = new RecordingRunner

        When("Cozy plans a publish dry-run")
        _capture {
          CozyBok.publish(config, runner, CozyVideoSpec.RecordingVoicevoxClient(), CozyVideoSpec.PublishingRunner())
        }

        Then("the external paths are accepted and recorded in the operation manifest")
        val manifest = _read(dir.resolve("target/cozy-bok/publish/latest/manifest.json"))
        manifest should include (publication.toAbsolutePath.normalize().toString)
        manifest should include (warehouse.toAbsolutePath.normalize().toString)
        runner.commands shouldBe empty
      }
    }
  }

    }

    "validate publish preflight" which {
    "accept dry-run only for bok publish" in {
    _with_temp_dir("cozy-bok-publish-dry-run-command-scope") { dir =>
      Given("dry-run is supplied to lower-level publication commands")
      When("publication command metadata is parsed")
      val e1 = intercept[Throwable] {
        CozyBok.PublicationConfig.create("publish-video", List(dir.toString, "--dry-run"))
      }
      val e2 = intercept[Throwable] {
        CozyBok.PublicationConfig.create("update-publication", List(dir.toString, "--dry-run"))
      }

      Then("dry-run is rejected outside the one-stop publish command")
      e1.getMessage should include ("only supported by bok publish")
      e2.getMessage should include ("only supported by bok publish")
    }
  }

    "reject source and publication path overlap before side effects" in {
    _with_temp_dir("cozy-bok-publish-preflight-path") { dir =>
      Given("a publication registry path that overlaps the BoK source directory")
      _write(dir.resolve("src/main/doxsite/site.conf"), "site { output { locale_mode = \"single_locale_root\" } }\n")
      _write(dir.resolve(".cozy/config.yaml"),
        """bok:
          |  video:
          |    enabled: false
          |  workflow:
          |    upload:
          |      command: "etc/upload.sh"
          |""".stripMargin)
      val config = CozyBok.PublicationConfig.create("publish", List(
        dir.toString,
        "--publication", dir.resolve("src/main/doxsite/publication").toString
      ))
      val runner = new RecordingRunner

      When("Cozy runs publish preflight")
      val e = intercept[Throwable] {
        CozyBok.publish(config, runner, CozyVideoSpec.RecordingVoicevoxClient(), CozyVideoSpec.PublishingRunner())
      }

      Then("the overlap is rejected before manifest or command side effects")
      e.getMessage should include ("overlaps BoK source")
      runner.commands shouldBe empty
      dir.resolve("target/cozy-bok/publish/latest/manifest.json") shouldNot existPath
    }
  }

    }

    "record publish failures" which {
    "record build failure and prevent upload" in {
    _with_temp_dir("cozy-bok-publish-build-failure") { dir =>
      Given("a configured one-stop publish whose site build fails")
      _write(dir.resolve("src/main/doxsite/site.conf"), "site { output { locale_mode = \"single_locale_root\" } }\n")
      _write(dir.resolve(".cozy/config.yaml"),
        """bok:
          |  video:
          |    enabled: false
          |  workflow:
          |    upload:
          |      command: "etc/upload.sh"
          |""".stripMargin)
      val config = CozyBok.PublicationConfig.create("publish", List(dir.toString, "--strategy", "production"))
      val runner = new FailingBuildRunner

      When("Cozy executes the publish flow")
      val e = intercept[RuntimeException] {
        CozyBok.publish(config, runner, CozyVideoSpec.RecordingVoicevoxClient(), CozyVideoSpec.PublishingRunner())
      }

      Then("the build failure is recorded and upload is skipped")
      e.getMessage should include ("build boom")
      runner.commands should not contain (Vector("sh", "-c", "etc/upload.sh"))
      val manifest = _read(dir.resolve("target/cozy-bok/publish/latest/manifest.json"))
      manifest should include ("\"name\" : \"update-publication\"")
      manifest should include ("\"status\" : \"skipped\"")
      manifest should include ("\"name\" : \"build\"")
      manifest should include ("\"status\" : \"failed\"")
      manifest should include ("build boom")
    }
  }

    "record upload failure distinctly after build" in {
    _with_temp_dir("cozy-bok-publish-upload-failure") { dir =>
      Given("a configured one-stop publish whose upload command fails after a successful build")
      _write(dir.resolve("src/main/doxsite/site.conf"), "site { output { locale_mode = \"single_locale_root\" } }\n")
      _write(dir.resolve(".cozy/config.yaml"),
        """bok:
          |  video:
          |    enabled: false
          |  workflow:
          |    upload:
          |      command: "etc/upload.sh"
          |""".stripMargin)
      val config = CozyBok.PublicationConfig.create("publish", List(dir.toString, "--strategy", "production"))
      val runner = new FailingUploadRunner

      When("Cozy executes the publish flow")
      val e = intercept[RuntimeException] {
        CozyBok.publish(config, runner, CozyVideoSpec.RecordingVoicevoxClient(), CozyVideoSpec.PublishingRunner())
      }

      Then("the upload failure is recorded as an upload step failure")
      e.getMessage should include ("upload boom")
      runner.commands should containWhere[Vector[String]](_.take(2) == Vector("dox", "antora"))
      runner.commands should contain (Vector("sh", "-c", "etc/upload.sh"))
      val manifest = _read(dir.resolve("target/cozy-bok/publish/latest/manifest.json"))
      manifest should include ("\"name\" : \"upload\"")
      manifest should include ("\"status\" : \"failed\"")
      manifest should include ("upload boom")
    }
  }

  }

    }

  "Cozy BoK command surface" should {
    "render help and parse metadata" which {
    "list BoK publication path options in help" in {
    Given("a user requesting Cozy help")
    When("the help text is rendered")
    val help = _capture {
      Cozy.main(Array("--help"))
    }

    Then("BoK publication command surfaces and dry-run support are documented")
    help should include ("bok publish-video <project-dir> [--publication <dir>] [--warehouse <dir>]")
    help should include ("bok update-publication <project-dir> [--publication <dir>] [--warehouse <dir>]")
    help should include ("bok publish <project-dir> [--publication <dir>] [--warehouse <dir>]")
    help should include ("bok doctor [<project-dir>] [--fix] [--dry-run]")
    help should include ("bok fix [<project-dir>] [--dry-run]")
    help should include ("--dry-run")
  }

    "validate preview port as integer metadata" in {
    _with_temp_dir("cozy-bok-preview-port") { dir =>
      Given("a BoK preview command with a non-integer port")
      When("the preview command metadata is parsed")
      val e = intercept[Throwable] {
        CozyBok.preview(List(dir.toString, "--port", "not-int"), new RecordingRunner)
      }
      Then("the invalid port is rejected explicitly")
      e.getMessage should include ("not-int")
    }
  }

    }

    "run configured workflows" which {
    "require registered workflow command for explicit stage execution" in {
    _with_temp_dir("cozy-bok-workflow-missing") { dir =>
      Given("a BoK stage command without a registered shell command")
      When("Cozy resolves the explicit stage workflow")
      val e = intercept[Throwable] {
        CozyBok.runWorkflow(CozyBok.WorkflowConfig.create("stage", List(dir.toString)), new RecordingRunner)
      }
      Then("the missing stage workflow command is reported as configuration error")
      e.getMessage should include ("bok.workflow.stage.command")
    }
  }

    "run only registered workflow command for stage and upload" in {
    _with_temp_dir("cozy-bok-workflow") { dir =>
      Given("registered stage and upload workflow commands")
      _write(dir.resolve(".cozy/config.yaml"),
        """bok:
          |  workflow:
          |    stage:
          |      command: "etc/website-stage.sh"
          |    upload:
          |      command: "etc/website-upload.sh"
          |""".stripMargin)
      val runner = new RecordingRunner

      When("Cozy runs the workflows")
      CozyBok.runWorkflow(CozyBok.WorkflowConfig.create("stage", List(dir.toString)), runner)
      CozyBok.runWorkflow(CozyBok.WorkflowConfig.create("upload", List(dir.toString)), runner)

      Then("only the configured external workflow commands are executed")
      runner.commands shouldBe Vector(
        Vector("sh", "-c", "etc/website-stage.sh"),
        Vector("sh", "-c", "etc/website-upload.sh")
      )
    }
  }

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

  private class FailingBuildRunner extends RecordingRunner {
    override def run(command: Vector[String], cwd: Path): Unit = {
      calls = calls :+ (command -> cwd)
      if (command.take(2) == Vector("dox", "antora"))
        throw new RuntimeException("build boom")
    }
  }

  private class FailingUploadRunner extends RecordingRunner {
    override def run(command: Vector[String], cwd: Path): Unit = {
      calls = calls :+ (command -> cwd)
      if (command == Vector("sh", "-c", "etc/upload.sh"))
        throw new RuntimeException("upload boom")
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

  private def _manifest(dir: Path): io.circe.Json =
    parser.parse(_read(dir.resolve("target/cozy-bok/publish/latest/manifest.json"))).fold(throw _, identity)

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
