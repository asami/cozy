package cozy

import cozy.bok.CozyBok
import cozy.video.CozyVideoSpec
import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path, Paths}
import java.time.YearMonth
import java.util.zip.ZipInputStream
import scala.collection.JavaConverters._
import io.circe.parser
import org.scalatest.GivenWhenThen
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Jun.  3, 2026
 *  version Jun. 27, 2026
 * @version Jul.  6, 2026
 * @author  ASAMI, Tomoharu
 */
class CozyBokSpec
    extends AnyWordSpec
    with GivenWhenThen
    with CozySpecVocabulary {
  "Cozy BoK source scaffolding" should {
    "source project scaffolds" which {
      "create a KnowledgeHub BoK source scaffold" in {
        _with_temp_dir("cozy-bok-create") { dir =>
          Given("a requested KnowledgeHub BoK project name, URL, and language")
          When("Cozy creates the BoK source scaffold")
          CozyBok.create(
            CozyBok.CreateConfig.create(
              List(
                "--save",
                dir.toString,
                "--name",
                "KnowledgeHub BoK",
                "--url",
                "https://www.asamioffice.com/kokubunji/knowledgehub",
                "--language",
                "ja"
              )
            )
          )

          Then(
            "the scaffold contains source, configuration, UI, manual, history, and RDF seed files"
          )
          dir.resolve("README.md") should be_regular_file
          dir.resolve("STRUCTURE.md") should be_regular_file
          dir.resolve("conf/cozy/config.yaml") should be_regular_file
          dir.resolve("etc/website-stage.sh.proto") should be_regular_file
          dir.resolve("etc/website-upload.sh.proto") should be_regular_file
          dir.resolve("etc/website-stage.sh") shouldNot exist_path
          dir.resolve("etc/website-upload.sh") shouldNot exist_path
          dir.resolve("src/main/doxsite/site.conf") should be_regular_file
          dir.resolve(
            "src/main/doxsite/glossary/category.yaml"
          ) should be_regular_file
          dir.resolve("src/main/doxsite/glossary/index.dox") shouldNot exist_path
          dir.resolve(
            "src/main/doxsite/history/category.yaml"
          ) should be_regular_file
          dir.resolve("src/main/doxsite/history/index.dox") should be_regular_file
          dir.resolve("src/main/doxsite/manual/index.dox") shouldNot exist_path
          dir.resolve(
            "src/main/doxsite/manual/local-rules.dox"
          ) should be_regular_file
          dir.resolve("src/main/doxsite/rdf/site.ttl") should be_regular_file
          And("the scaffold contains site UI assets")
          dir.resolve(
            "src/main/doxsite/assets/css/knowledgehub.css"
          ) should be_regular_file
          dir.resolve(
            "src/main/antora-ui/build/ui-bundle.zip"
          ) should be_regular_file
          And(
            "generated work directories are not created during scaffold creation"
          )
          dir.resolve(
            "src/main/doxsite/knowledgehub/category.yaml"
          ) shouldNot exist_path
          dir.resolve(
            "src/main/doxsite/site-structure.yaml"
          ) shouldNot exist_path
          dir.resolve("website.d") shouldNot exist_path
          _read(dir.resolve("src/main/doxsite/index.dox")) should startWith(
            "Home\n======"
          )
          _read(dir.resolve("src/main/doxsite/index.dox")) should include(
            "## HEADLINE\n\nKnowledgeHub BoK"
          )
          _read(dir.resolve("src/main/doxsite/index.dox")) should include(
            "## BRIEF\n\nKnowledgeHub BoK のカテゴリ、用語、RDFから知識を探索するための短い導入。"
          )
          _read(
            dir.resolve("src/main/doxsite/index.dox")
          ) should not include ("## HEADLINE\nKnowledgeHub BoK")
          _read(
            dir.resolve("src/main/doxsite/index.dox")
          ) should not include ("## BRIEF\nKnowledgeHub BoK のカテゴリ")
          _read(dir.resolve("src/main/doxsite/index.dox")) should include(
            "# Overview"
          )
          _read(dir.resolve("src/main/doxsite/index.dox")) should include(
            "KnowledgeHub BoK のカテゴリ、用語、RDFから知識を探索するための短い導入。"
          )
          _read(dir.resolve("src/main/doxsite/index.dox")) should include(
            "KnowledgeHub BoKは、カテゴリ、用語、RDFのつながりから知識を探索するためのBoKです。"
          )
          _read(
            dir.resolve("src/main/doxsite/index.dox")
          ) should not include ("## Quick Links")
          _read(
            dir.resolve("src/main/doxsite/index.dox")
          ) should not include ("## BoK Console")
          _read(
            dir.resolve("src/main/doxsite/index.dox")
          ) should not include ("## Operation Focus")
          _read(
            dir.resolve("src/main/doxsite/index.dox")
          ) should not include ("日本語単独運用")
          _read(
            dir.resolve("src/main/doxsite/history/index.dox")
          ) should include("# Dashboard")
          _read(
            dir.resolve("src/main/doxsite/manual/local-rules.dox")
          ) should include("Local Rules")
          _read(
            dir.resolve("src/main/doxsite/manual/local-rules.dox")
          ) should include("KnowledgeHub BoK")
          _read(
            dir.resolve("src/main/doxsite/manual/local-rules.dox")
          ) should include("src/main/doxsite")
          _read(
            dir.resolve("src/main/doxsite/manual/local-rules.dox")
          ) should include(".cozy/")
          _read(dir.resolve("conf/cozy/config.yaml")) should include(
            "textus-toolchain"
          )
          _read(dir.resolve("conf/cozy/config.yaml")) should include(
            "repository: repository"
          )
          _read(dir.resolve("conf/cozy/config.yaml")) should include(
            "website-staging"
          )
          _read(dir.resolve("conf/cozy/config.yaml")) should include(
            "backup:"
          )
          _read(dir.resolve("conf/cozy/config.yaml")) should include(
            "enabled: false"
          )
          _read(dir.resolve("conf/cozy/config.yaml")) should include(
            "dir: website.backup"
          )
          _read(dir.resolve("conf/cozy/config.yaml")) should include(
            "compressed: true"
          )
          _read(dir.resolve("conf/cozy/config.yaml")) should include(
            "workflow:"
          )
          _read(dir.resolve("conf/cozy/config.yaml")) should include("stage:")
          _read(dir.resolve("conf/cozy/config.yaml")) should include(
            "website-stage.sh.proto"
          )
          _read(
            dir.resolve("conf/cozy/config.yaml")
          ) should not include ("missing-artifact-policy: warn")
          _read(dir.resolve("src/main/doxsite/site.conf")) should include(
            """locale_mode = "single_locale_root""""
          )
          _read(dir.resolve("src/main/doxsite/site.conf")) should include(
            """vision = "Build a shared knowledge base for KnowledgeHub BoK.""""
          )
          _read(dir.resolve("src/main/doxsite/site.conf")) should include(
            """goals = ["""
          )
          _read(dir.resolve("src/main/doxsite/site.conf")) should include(
            """subgoals = ["""
          )
          _read(dir.resolve("src/main/doxsite/site.conf")) should include(
            "output.scope.policy = home_only"
          )
          val stageproto = _read(dir.resolve("etc/website-stage.sh.proto"))
          stageproto should include("WEBSITE_STAGING_DIR")
          stageproto should include(
            "REPOSITORY_SOURCE_DIR=${REPOSITORY_SOURCE_DIR:-repository}"
          )
          stageproto should include("rsync -av --checksum --delete")
          stageproto should include(
            """rsync -av --checksum "$REPOSITORY_SOURCE_DIR"/ "$WEBSITE_STAGING_DIR/repository"/"""
          )
          stageproto should not include ("""rsync -av --checksum --delete "$REPOSITORY_SOURCE_DIR"/""")
          val uploadproto = _read(dir.resolve("etc/website-upload.sh.proto"))
          uploadproto should include(
            "WEBSITE_SOURCE_DIR=${WEBSITE_SOURCE_DIR:-website.d}"
          )
          uploadproto should include(
            "REPOSITORY_SOURCE_DIR=${REPOSITORY_SOURCE_DIR:-repository}"
          )
          uploadproto should include("AWS_S3_URI")
          uploadproto should include("aws s3 sync")
          uploadproto should include("--exclude \"repository/*\"")
          uploadproto should include(
            "aws s3 sync \"$WEBSITE_SOURCE_DIR/repository\"/ \"$AWS_S3_URI/repository/\""
          )
          uploadproto should include(
            "aws s3 sync \"$REPOSITORY_SOURCE_DIR\"/ \"$AWS_S3_URI/repository/\""
          )
          uploadproto should not include ("""aws s3 sync "$REPOSITORY_SOURCE_DIR"/ "$AWS_S3_URI/repository/" --delete""")
          uploadproto should include("aws cloudfront create-invalidation")
          _read(dir.resolve("src/main/doxsite/index.dox")) should include(
            "published_at="
          )
          _read(
            dir.resolve("src/main/doxsite/history/index.dox")
          ) should include("published_at=")
          _read(dir.resolve("README.md")) should not include ("site-structure")
          val css = _zip_text(
            dir.resolve("src/main/antora-ui/build/ui-bundle.zip"),
            "css/site.css"
          )
          val dashboardcss = _zip_text(
            dir.resolve("src/main/antora-ui/build/ui-bundle.zip"),
            "css/cozy-bok-dashboard.css"
          )
          val bootstrapgrid = _zip_text(
            dir.resolve("src/main/antora-ui/build/ui-bundle.zip"),
            "css/bootstrap-grid.min.css"
          )
          bootstrapgrid should include("Bootstrap")
          bootstrapgrid should include(".container-fluid")
          bootstrapgrid should include(".row")
          bootstrapgrid should include(".col-xl-8")
          css should include(".navbar-menu")
          css should include(".navbar-dropdown")
          css should include(".nav-container")
          css should include(".lang-toggle")
          css should include("a.glossary")
          css should include(".bok-special-links")
          css should include(".bok-dashboard-grid")
          css should include(".body.body-dashboard")
          css should include("radial-gradient")
          css should include(".body-dashboard .toolbar")
          css should include("min-height:calc(100vh - 3.5rem)")
          css should include("#0b1220")
          css should include("0 30px 80px")
          css should include(".bok-card:hover")
          css should include(".bok-card-purpose")
          css should include("font-size:2.85rem")
          css should include(".bok-purpose-tree")
          css should include(".bok-purpose-goal")
          css should include(".bok-purpose-subgoals")
          css should include(".bok-category-summary-grid")
          css should include(".bok-category-summary-card")
          css should not include (".row{display:grid")
          css should include(".card-title")
          dashboardcss should include(
            "Card accents: keep the surface border stable and draw an outer highlight ring"
          )
          dashboardcss should include("--bok-card-accent")
          dashboardcss should include("--bok-card-ring")
          dashboardcss should include("0 0 0 4px var(--bok-card-ring)")
          dashboardcss should include("0 0 0 9px var(--bok-card-ring-glow)")
          dashboardcss should include(
            "transform: translateY(-5px) scale(1.006)"
          )
          css should include(".bok-cumulative-line")
          css should include(".bok-index-nav")
          dashboardcss should include(".bok-index-term-category")
          dashboardcss should include("Cozy BoK Dashboard")
          dashboardcss should include(".body-dashboard .toolbar")
          dashboardcss should include("#0b1220")
          dashboardcss should include("0 30px 80px")
          dashboardcss should include(".bok-dashboard-command-center")
          dashboardcss should include(".bok-purpose-vision-panel")
          dashboardcss should include(".bok-purpose-vision-copy")
          dashboardcss should include(".bok-purpose-subgoal-copy")
          dashboardcss should include(".navbar-category-dropdown > .navbar-category-toggle")
          dashboardcss should include("background: transparent")
          dashboardcss should include("border-radius: 0")
          dashboardcss should include(
            "KPI cards: centered highlight numbers read better as dashboard metrics"
          )
          dashboardcss should include("align-items: center !important")
          dashboardcss should include(
            "Dashboard hero: separate marker, title, and summary as distinct zones"
          )
          dashboardcss should include("gap: 1.1rem")
          dashboardcss should include("border-left: 4px solid rgba(191,231,255,.48)")
          dashboardcss should include(
            "Layout corrections: use full-width separators and keep Recent Changes compact"
          )
          dashboardcss should include(
            "grid-template-columns: minmax(10rem, 13rem) minmax(0, 1fr) auto"
          )
          _zip_text(
            dir.resolve("src/main/antora-ui/build/ui-bundle.zip"),
            "js/site.js"
          ) should include("navbar-burger")
          _zip_text(
            dir.resolve("src/main/antora-ui/build/ui-bundle.zip"),
            "layouts/default.hbs"
          ) should include("{{> nav}}")
          _zip_text(
            dir.resolve("src/main/antora-ui/build/ui-bundle.zip"),
            "partials/nav-menu.hbs"
          ) should include("{{#with page.navigation}}")
          _zip_text(
            dir.resolve("src/main/antora-ui/build/ui-bundle.zip"),
            "partials/nav-tree.hbs"
          ) should include("""{{> nav-tree navigation=./items level=(increment ../level)}}""")
          _zip_text(
            dir.resolve("src/main/antora-ui/build/ui-bundle.zip"),
            "helpers/eq.js"
          ) should include("a === b")
          _zip_text(
            dir.resolve("src/main/antora-ui/build/ui-bundle.zip"),
            "helpers/increment.js"
          ) should include("+ 1")
          _zip_text(
            dir.resolve("src/main/antora-ui/build/ui-bundle.zip"),
            "img/menu.svg"
          ) should include("<svg")
          _zip_bytes(
            dir.resolve("src/main/antora-ui/build/ui-bundle.zip"),
            "font/roboto-latin-400-normal.woff2"
          ) should not be empty
          val header = _zip_text(
            dir.resolve("src/main/antora-ui/build/ui-bundle.zip"),
            "partials/header-content.hbs"
          )
          header should include(
            """class="navbar-item has-dropdown is-hoverable navbar-bok-nav navbar-bok-dropdown""""
          )
          header should include(
            """class="navbar-link navbar-bok-toggle" href="#">BoK</a>"""
          )
          header should include(
            """class="navbar-item navbar-dropdown-item" href="{{siteRootPath}}/glossary/index.html">Glossary</a>"""
          )
          header should include(
            """class="navbar-item navbar-dropdown-item" href="{{siteRootPath}}/history/index.html">History</a>"""
          )
          header should include(
            """class="navbar-item navbar-dropdown-item" href="{{siteRootPath}}/manual/index.html">BoK Manual</a>"""
          )
          header should not include ("Lexicon")
        }
      }

      "create an English Home scaffold when requested" in {
        _with_temp_dir("cozy-bok-create-english") { dir =>
          Given("a requested English BoK project")
          When("Cozy creates the BoK source scaffold")
          CozyBok.create(
            CozyBok.CreateConfig.create(
              List(
                "--save",
                dir.toString,
                "--name",
                "KnowledgeHub BoK",
                "--language",
                "en"
              )
            )
          )

          Then("the Home narrative seed uses English reader-facing text")
          _read(dir.resolve("src/main/doxsite/index.dox")) should include(
            "A short introduction for exploring KnowledgeHub BoK through categories, terms, and RDF."
          )
          _read(dir.resolve("src/main/doxsite/index.dox")) should include(
            "KnowledgeHub BoK is a BoK site for exploring knowledge through categories, terms, and RDF relationships."
          )
          _read(
            dir.resolve("src/main/doxsite/index.dox")
          ) should not include ("カテゴリ、用語、RDFから知識を探索")
        }
      }

      "create a category with article and term seeds" in {
        _with_temp_dir("cozy-bok-create-category") { dir =>
          Given("an existing BoK source scaffold")
          CozyBok.create(
            CozyBok.CreateConfig.create(List("--save", dir.toString))
          )
          When(
            "Cozy creates a category with article and glossary term seed metadata"
          )
          CozyBok.createCategory(
            CozyBok.CategoryConfig.create(
              List(
                "knowledgehub",
                "--project",
                dir.toString,
                "--title",
                "KnowledgeHub",
                "--description",
                "KnowledgeHub category.",
                "--vision",
                "Make KnowledgeHub concepts actionable.",
                "--goal",
                "Explain the core model.",
                "--goal",
                "Connect concepts to operations.",
                "--subgoal",
                "Maintain examples.",
                "--article",
                "knowledgehub-overview:KnowledgeHub Overview:Overview article.",
                "--term",
                "glossary/knowledgehub:KnowledgeHub:KnowledgeHub definition.:ナレッジハブ"
              )
            )
          )

          Then(
            "the category, article, and glossary files are written with source narrative metadata"
          )
          dir.resolve(
            "src/main/doxsite/knowledgehub/category.yaml"
          ) should be_regular_file
          dir.resolve(
            "src/main/doxsite/knowledgehub/index.dox"
          ) should be_regular_file
          dir.resolve(
            "src/main/doxsite/knowledgehub/knowledgehub-overview.dox"
          ) should be_regular_file
          dir.resolve(
            "src/main/doxsite/glossary/knowledgehub/knowledgehub.dox"
          ) should be_regular_file
          _read(
            dir.resolve("src/main/doxsite/knowledgehub/index.dox")
          ) should include("## HEADLINE\n\nKnowledgeHub")
          _read(
            dir.resolve("src/main/doxsite/knowledgehub/index.dox")
          ) should include("## BRIEF\n\nKnowledgeHub category.")
          _read(
            dir.resolve("src/main/doxsite/knowledgehub/index.dox")
          ) should not include ("## HEADLINE\nKnowledgeHub")
          _read(
            dir.resolve("src/main/doxsite/knowledgehub/index.dox")
          ) should not include ("## BRIEF\nKnowledgeHub category.")
          _read(
            dir.resolve(
              "src/main/doxsite/knowledgehub/knowledgehub-overview.dox"
            )
          ) should include("## HEADLINE\n\nKnowledgeHub Overview")
          _read(
            dir.resolve(
              "src/main/doxsite/knowledgehub/knowledgehub-overview.dox"
            )
          ) should include("## BRIEF\n\nOverview article.")
          _read(
            dir.resolve("src/main/doxsite/knowledgehub/index.dox")
          ) should include("# Overview")
          _read(
            dir.resolve("src/main/doxsite/knowledgehub/index.dox")
          ) should include("KnowledgeHub category.")
          _read(
            dir.resolve("src/main/doxsite/knowledgehub/index.dox")
          ) should not include ("## Navigation")
          _read(
            dir.resolve("src/main/doxsite/knowledgehub/index.dox")
          ) should not include ("## Operation Notes")
          _read(
            dir.resolve("src/main/doxsite/knowledgehub/index.dox")
          ) should not include ("""<a href="knowledgehub-overview.html">KnowledgeHub Overview</a>""")
          _read(
            dir.resolve("src/main/doxsite/knowledgehub/index.dox")
          ) should not include ("""<a href="../glossary/knowledgehub/knowledgehub.html">KnowledgeHub</a>""")
          _read(
            dir.resolve("src/main/doxsite/knowledgehub/index.dox")
          ) should not include ("## Vision")
          _read(
            dir.resolve("src/main/doxsite/knowledgehub/index.dox")
          ) should not include ("Make KnowledgeHub concepts actionable.")
          _read(
            dir.resolve("src/main/doxsite/knowledgehub/index.dox")
          ) should not include ("## Goals")
          _read(
            dir.resolve("src/main/doxsite/knowledgehub/index.dox")
          ) should not include ("- Explain the core model.")
          _read(
            dir.resolve("src/main/doxsite/knowledgehub/index.dox")
          ) should not include ("- Connect concepts to operations.")
          _read(
            dir.resolve("src/main/doxsite/knowledgehub/index.dox")
          ) should not include ("## Subgoals")
          _read(
            dir.resolve("src/main/doxsite/knowledgehub/index.dox")
          ) should not include ("- Maintain examples.")
          _read(
            dir.resolve(
              "src/main/doxsite/knowledgehub/knowledgehub-overview.dox"
            )
          ) should include("published_at=")
          _read(
            dir.resolve(
              "src/main/doxsite/glossary/knowledgehub/knowledgehub.dox"
            )
          ) should include("published_at=")
          _read(
            dir.resolve(
              "src/main/doxsite/glossary/knowledgehub/knowledgehub.dox"
            )
          ) should include("reading=ナレッジハブ")
          _read(
            dir.resolve("src/main/doxsite/knowledgehub/category.yaml")
          ) should include(
            """vision: "Make KnowledgeHub concepts actionable.""""
          )
          _read(
            dir.resolve("src/main/doxsite/knowledgehub/category.yaml")
          ) should include("goals:")
          _read(
            dir.resolve("src/main/doxsite/knowledgehub/category.yaml")
          ) should include("""  - "Explain the core model."""")
          _read(
            dir.resolve("src/main/doxsite/knowledgehub/category.yaml")
          ) should include("""  - "Connect concepts to operations."""")
          _read(
            dir.resolve("src/main/doxsite/knowledgehub/category.yaml")
          ) should include("subgoals:")
          _read(
            dir.resolve("src/main/doxsite/knowledgehub/category.yaml")
          ) should include("""  - "Maintain examples."""")
        }
      }

      "use configured public preview port by default" in {
        _with_temp_dir("cozy-bok-preview-config-port") { dir =>
          Given("a BoK project with a public preview port setting")
          _write(
            dir.resolve("conf/cozy/config.yaml"),
            "bok:\n  website: website.d\n  preview:\n    port: 8981\n"
          )
          _write(dir.resolve("website.d/index.html"), "<html></html>\n")
          val runner = new RecordingRunner

          When("Cozy starts preview without a CLI port")
          val output = _capture {
            CozyBok.preview(List(dir.toString), runner)
          }

          Then("the configured port is used")
          output should include("http://127.0.0.1:8981/")
          runner.commands should contain(
            Vector("python3", "-m", "http.server", "8981")
          )
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
          Then(
            "the parser rejects the non-canonical form and points to the supported syntax"
          )
          e.getMessage should include("--save <dir>")
        }
      }

    }

  }

  "Cozy BoK site build" should {
    "invoke SmartDox and render site" which {
      "map strategy and render generated dashboards" in {
        _with_temp_dir("cozy-bok-build") { dir =>
          Given(
            "a BoK project with SmartDox source, glossary data, and dashboard metadata"
          )
          _write(
            dir.resolve("src/main/doxsite/site.conf"),
            """site {
          |  metadata {
          |    vision = "Make engineering knowledge operational."
          |    goals = [
          |      "Publish reusable knowledge",
          |      "Support daily learning"
          |    ]
          |    subgoals = [
          |      "Keep glossary current"
          |    ]
          |  }
          |  output {
          |    locale_mode = "single_locale_root"
          |  }
          |}
          |""".stripMargin
          )
          _write(
            dir.resolve("src/main/doxsite/glossary/category.yaml"),
            "name: Glossary\ntitle: 用語集\ndescription: BoK全体で共有する用語集。\n"
          )
          _write(
            dir.resolve("src/main/doxsite/architecture/category.yaml"),
            """name: Architecture
          |title: Architecture
          |description: Architecture category.
          |vision: Make architecture decisions traceable.
          |goals:
          |  - title: Explain architecture concepts
          |    subgoals:
          |      - Maintain architecture glossary
          |  - title: Link concepts to runtime
          |""".stripMargin
          )
          _write(
            dir.resolve("src/main/doxsite/architecture/overview.dox"),
            "Overview\n========\n\n# HEAD\n\n## BRIEF\nArchitecture overview.\n"
          )
          _write(
            dir.resolve("src/main/doxsite/manual/index.dox"),
            """Stale Manual
          |============
          |
          |# Dashboard
          |
          |This stale project-local manual source must not override the Cozy-owned standard manual.
          |""".stripMargin
          )
          _write(
            dir.resolve("src/main/doxsite/manual/local-rules.dox"),
            "Local Rules\n===========\n\n# Overview\n\nKnowledgeHub BoK project-local operation rules.\n\n## Project Scope\n\nSource root is `src/main/doxsite`.\n"
          )
          _write(
            dir.resolve("src/main/doxsite/glossary/architecture/runtime.dox"),
            "Runtime\n=======\n\n# HEAD\n\nreading=らんたいむ\n\n# Definition\nRuntime term.\n"
          )
          _write(
            dir.resolve("src/main/doxsite/glossary/architecture/asuka.dox"),
            "あすか\n======\n\n# HEAD\n\n# Definition\nJapanese term.\n"
          )
          _write(
            dir.resolve("src/main/doxsite/glossary/architecture/cloud.dox"),
            "Cloud\n=======\n\n# HEAD\n\n# Definition\nEnglish-only term.\n"
          )
          _write(dir.resolve("doxsite.d/ja/index.html"), "stale ja\n")
          _write(dir.resolve("doxsite.d/en/index.html"), "stale en\n")
          _write(
            dir.resolve("doxsite-cache-work-in-progress.d/stale.error_msg"),
            "stale\n"
          )
          val config =
            CozyBok.BuildConfig.create(List(dir.toString, "--strategy", "wip"))
          val runner = new RecordingRunner

          When("Cozy builds the BoK using the wip strategy")
          CozyBok.build(config, runner)

          Then(
            "the SmartDox commands receive normalized strategy, publication, and repository settings"
          )
          config.strategy shouldBe "work-in-progress"
          runner.commands should contain_where[Vector[String]] { command =>
            command.take(4) == Vector(
              "dox",
              "antora",
              "-strategy",
              "work-in-progress"
            ) &&
            command.contains("-publication") &&
            command.last == "src/main/doxsite"
          }
          runner.commands should contain_where[Vector[String]] { command =>
            command.take(6) == Vector(
              "dox",
              "site",
              "-strategy",
              "work-in-progress",
              "-output.scope.policy",
              "home_only"
            ) &&
            command.contains("-publication") &&
            command.contains("-publication.repository") &&
            command.contains("-publication.rdf.missing.policy") &&
            command.last == "src/main/doxsite"
          }
          And(
            "the generated site renders dashboard, glossary, history, manual, and navigation conventions"
          )
          _read(dir.resolve("website.d/index.html")) should include(
            """<h1 class="page">KnowledgeHub BoK</h1>"""
          )
          _read(
            dir.resolve("website.d/index.html")
          ) should not include ("""<h1 class="page">KnowledgeHub BoK Dashboard</h1>""")
          _read(dir.resolve("website.d/index.html")) should include(
            """href="_/css/cozy-bok-dashboard.css""""
          )
          _read(dir.resolve("website.d/index.html")) should include(
            "BoK全体の状態、目的、カテゴリ、記事、用語、RDF、更新推移を集約するDashboard"
          )
          _read(dir.resolve("website.d/index.html")) should include(
            """<span class="bok-dashboard-hero-fact"><strong>3</strong><em>用語</em></span>"""
          )
          _read(dir.resolve("website.d/index.html")) should include(
            """class="bok-purpose-vision-panel""""
          )
          _read(dir.resolve("website.d/index.html")) should include(
            """class="bok-purpose-node-label">V</span>"""
          )
          _read(dir.resolve("website.d/index.html")) should include(
            "Make engineering knowledge operational."
          )
          _read(dir.resolve("website.d/index.html")) should include(
            """class="bok-purpose-flat""""
          )
          _read(dir.resolve("website.d/index.html")) should include(
            """class="bok-purpose-flat-group"><strong>Goals</strong>"""
          )
          _read(dir.resolve("website.d/index.html")) should include(
            """class="bok-purpose-flat-group"><strong>Subgoals</strong>"""
          )
          _read(dir.resolve("website.d/index.html")) should include(
            "Publish reusable knowledge"
          )
          _read(dir.resolve("website.d/index.html")) should include(
            "Support daily learning"
          )
          _read(dir.resolve("website.d/index.html")) should include(
            "Keep glossary current"
          )
          _read(dir.resolve("website.d/index.html")) should include(
            """class="bok-dashboard container-fluid bok-dashboard-command-center""""
          )
          _read(dir.resolve("website.d/index.html")) should include(
            """class="bok-dashboard-shell" id="dashboard""""
          )
          _read(dir.resolve("website.d/index.html")) should include(
            """class="bok-dashboard-hero""""
          )
          _read(dir.resolve("website.d/index.html")) should include(
            """class="bok-dashboard-hero-facts""""
          )
          _read(dir.resolve("website.d/index.html")) should include(
            """class="row g-3""""
          )
          _read(dir.resolve("website.d/index.html")) should include(
            """class="col-12 col-xl-8""""
          )
          _read(dir.resolve("website.d/index.html")) should include(
            """<div class="col-12" data-bok-card="true">"""
          )
          _zip_text(
            dir.resolve("antora.d/ui-bundle.zip"),
            "css/bootstrap-grid.min.css"
          ) should include("Bootstrap Grid")
          val antoraheader = _zip_text(
            dir.resolve("antora.d/ui-bundle.zip"),
            "partials/header-content.hbs"
          )
          antoraheader should include("""class="navbar-item has-dropdown is-hoverable navbar-bok-nav navbar-bok-dropdown"""")
          antoraheader should include("""{{siteRootPath}}/glossary/index.html""")
          antoraheader should include("""{{siteRootPath}}/architecture/index.html""")
          antoraheader should not include ("""href="{{siteRootPath}}/history/index.html">History</a>
        <a class="navbar-item" href="{{siteRootPath}}/glossary/index.html">用語集</a>""")
          val supplementalheader = _read(
            dir.resolve("antora.d/supplemental-ui/partials/header-content.hbs")
          )
          supplementalheader should include("""class="navbar-item has-dropdown is-hoverable navbar-bok-nav navbar-bok-dropdown"""")
          supplementalheader should include("""{{siteRootPath}}/glossary/index.html""")
          supplementalheader should include("""{{siteRootPath}}/architecture/index.html""")
          _read(dir.resolve("website.d/index.html")) should include(
            """class="card bok-card bok-card-purpose""""
          )
          _read(dir.resolve("website.d/index.html")) should include(
            """<h3 class="card-title">BoK Vision</h3>"""
          )
          _read(dir.resolve("website.d/index.html")) should include(
            """class="card bok-card bok-card-readiness""""
          )
          _read(
            dir.resolve("website.d/index.html")
          ) should not include ("""bok-card-knowledge-entry""")
          _read(dir.resolve("website.d/index.html")) should include(
            """class="card bok-card bok-card-kpi bok-card-kpi-link""""
          )
          _read(dir.resolve("website.d/index.html")) should include(
            """class="card bok-card bok-card-chart""""
          )
          _read(dir.resolve("website.d/index.html")) should include(
            """class="card bok-card bok-card-quality""""
          )
          _read(dir.resolve("website.d/index.html")) should include(
            """class="card bok-card bok-card-matrix""""
          )
          _read(dir.resolve("website.d/index.html")) should include(
            """class="card bok-card bok-card-activity bok-card-notification""""
          )
          _read(dir.resolve("website.d/index.html")) should not include (
            """class="bok-notification-summary""""
          )
          _read(dir.resolve("website.d/index.html")) should include(
            """class="bok-activity-kind""""
          )
          _read(dir.resolve("website.d/index.html")) should include(
            """class="card bok-card bok-card-actions""""
          )
          _read(dir.resolve("website.d/index.html")) should include(
            """data-bok-actors="reader contributor project_manager""""
          )
          _read(dir.resolve("website.d/index.html")) should include(
            """data-bok-actors="site_administrator project_manager""""
          )
          _read(dir.resolve("website.d/index.html"))
            .indexOf("""bok-card-notification""") should be < _read(
            dir.resolve("website.d/index.html")
          ).indexOf("""bok-card-purpose""")
          _read(dir.resolve("website.d/index.html"))
            .indexOf("""bok-card-purpose""") should be < _read(
            dir.resolve("website.d/index.html")
          ).indexOf("""bok-card-matrix""")
          _read(dir.resolve("website.d/index.html"))
            .indexOf("""bok-card-purpose""") should be < _read(
            dir.resolve("website.d/index.html")
          ).indexOf("""bok-card-readiness""")
          _read(dir.resolve("website.d/index.html")) should include(
            """<span class="bok-kpi-label">カテゴリ</span>"""
          )
          _read(dir.resolve("website.d/index.html")) should include(
            """<span class="bok-kpi-value">1</span>"""
          )
          _read(dir.resolve("website.d/index.html")) should include(
            """class="bok-kpi-link" href="glossary/index.html""""
          )
          _read(dir.resolve("website.d/index.html")) should include(
            """class="bok-kpi-link" href="rdf/index.html""""
          )
          _read(dir.resolve("website.d/index.html")) should include(
            """<span class="bok-kpi-label">RDFトリプル</span>"""
          )
          _read(dir.resolve("website.d/index.html")) should include(
            """<span class="bok-kpi-value">42</span>"""
          )
          _read(dir.resolve("website.d/index.html")) should include(
            """aria-label="BoK項目分布""""
          )
          _read(dir.resolve("website.d/index.html")) should include(
            """data-chart="distribution-ratio""""
          )
          _read(dir.resolve("website.d/index.html")) should include(
            """class="bok-chart-row"><span>記事</span>"""
          )
          _read(dir.resolve("website.d/index.html")) should include(
            """class="bok-chart-row"><span>用語</span>"""
          )
          _read(dir.resolve("website.d/index.html")) should include(
            """aria-label="BoK増分""""
          )
          _read(dir.resolve("website.d/index.html")) should include(
            """data-chart="cumulative-date""""
          )
          _read(dir.resolve("website.d/index.html")) should include(
            """bok-cumulative-line"""
          )
          _read(dir.resolve("website.d/index.html")) should include(
            """class="bok-cumulative-line bok-cumulative-line-articles""""
          )
          _read(dir.resolve("website.d/index.html")) should include(
            """class="bok-cumulative-line bok-cumulative-line-terms""""
          )
          _read(dir.resolve("website.d/index.html")) should include(
            """bok-cumulative-marker-articles"""
          )
          _read(dir.resolve("website.d/index.html")) should include(
            """bok-cumulative-marker-terms"""
          )
          _read(dir.resolve("website.d/index.html")) should include(
            """datetime="2026-06-03""""
          )
          _read(dir.resolve("website.d/index.html")) should include(
            """datetime="2026-06-04""""
          )
          _read(dir.resolve("website.d/index.html")) should include(
            """記事:1 用語:3"""
          )
          _read(dir.resolve("website.d/index.html")) should include(
            """2026-06-03 - 2026-06-04"""
          )
          _read(dir.resolve("website.d/index.html")) should include(
            """class="body body-dashboard""""
          )
          _read(
            dir.resolve("website.d/index.html")
          ) should not include ("""class="nav-container"""")
          _read(
            dir.resolve("website.d/index.html")
          ) should not include ("""class="toc sidebar"""")
          _read(
            dir.resolve("website.d/index.html")
          ) should not include ("""class="bok-special-links"""")
          _read(
            dir.resolve("website.d/index.html")
          ) should not include ("""id="operation-policy"""")
          _read(
            dir.resolve("website.d/index.html")
          ) should not include ("Operation Policy")
          _read(
            dir.resolve("website.d/index.html")
          ) should not include ("このBoKはSmartDox本文")
          dir.resolve("website.d/history/index.html") should be_regular_file
          dir.resolve("website.d/manual/index.html") should be_regular_file
          dir.resolve("website.d/manual/local-rules.html") should be_regular_file
          _read(dir.resolve("website.d/history/index.html")) should include(
            "BoK運用、更新履歴、公開履歴のDashboard"
          )
          _read(dir.resolve("website.d/manual/index.html")) should include(
            "Cozy BoK source and site operation manual"
          )
          _read(
            dir.resolve("website.d/manual/index.html")
          ) should not include ("This stale project-local manual source")
          _read(dir.resolve("website.d/manual/index.html")) should include(
            "GitHub Markdown"
          )
          _read(dir.resolve("website.d/manual/index.html")) should include(
            "SmartDox"
          )
          _read(dir.resolve("website.d/manual/index.html")) should include(
            "マルチリンガル"
          )
          _read(dir.resolve("website.d/manual/index.html")) should include(
            "Knowledge Contributor"
          )
          _read(dir.resolve("website.d/manual/index.html")) should include(
            "Knowledge Owner"
          )
          _read(dir.resolve("website.d/manual/index.html")) should include(
            "Pull Request"
          )
          _read(dir.resolve("website.d/manual/index.html")) should include(
            "BoK Manager"
          )
          _read(dir.resolve("website.d/manual/index.html")) should include(
            "Site Administrator"
          )
          _read(dir.resolve("website.d/manual/index.html")) should include(
            "local-rules.html"
          )
          _read(
            dir.resolve("website.d/manual/local-rules.html")
          ) should include("KnowledgeHub BoK")
          _read(
            dir.resolve("website.d/manual/local-rules.html")
          ) should include("Project Scope")
          _read(dir.resolve("website.d/glossary/index.html")) should not include (
            """glossary/&lt;category&gt;/"""
          )
          _read(dir.resolve("website.d/glossary/index.html")) should include(
            """href="architecture/runtime.html""""
          )
          _read(dir.resolve("website.d/glossary/index.html")) should include(
            """<div class="bok-metric-label">用語数</div>"""
          )
          _read(dir.resolve("website.d/glossary/index.html")) should include(
            """<div class="bok-metric-value">3</div>"""
          )
          _read(dir.resolve("website.d/glossary/index.html")) should include(
            """<div class="bok-metric-label">カテゴリ数</div>"""
          )
          _read(dir.resolve("website.d/glossary/index.html")) should include(
            """<div class="bok-metric-label">用語タイプ数</div>"""
          )
          _read(dir.resolve("website.d/glossary/index.html")) should include(
            """<div class="bok-metric-label">RDF接続数</div>"""
          )
          _read(dir.resolve("website.d/glossary/index.html")) should include(
            """class="bok-glossary-connection-rate""""
          )
          _read(dir.resolve("website.d/glossary/index.html")) should include(
            """href="../ja/glossary/index.html">日本語索引ページ</a>"""
          )
          _read(dir.resolve("website.d/glossary/index.html")) should include(
            """href="../en/glossary/index.html">英語索引ページ</a>"""
          )
          _read(dir.resolve("website.d/glossary/index.html")) should include(
            """id="recent-terms""""
          )
          _read(dir.resolve("website.d/glossary/index.html")) should include(
            """class="bok-special-links""""
          )
          dir.resolve("website.d/ja/glossary/index.html") should be_regular_file
          dir.resolve("website.d/en/glossary/index.html") should be_regular_file
          _read(dir.resolve("website.d/ja/glossary/index.html")) should include(
            """<header class="header">"""
          )
          _read(dir.resolve("website.d/ja/glossary/index.html")) should include(
            "navbar-bok-nav"
          )
          _read(dir.resolve("website.d/ja/glossary/index.html")) should include(
            "navbar-category-nav"
          )
          _read(dir.resolve("website.d/ja/glossary/index.html")) should include(
            "日本語用語索引"
          )
          _read(dir.resolve("website.d/ja/glossary/index.html")) should include(
            "日本語で用語を探すための索引ページです。"
          )
          _read(dir.resolve("website.d/ja/glossary/index.html")) should include(
            """<h2>用語</h2>"""
          )
          _read(
            dir.resolve("website.d/ja/glossary/index.html")
          ) should not include ("This page is a language-specific entry point")
          _read(dir.resolve("website.d/ja/glossary/index.html")) should include(
            """href="#index-あ">あ</a>"""
          )
          _read(dir.resolve("website.d/ja/glossary/index.html")) should include(
            """id="index-あ""""
          )
          _read(dir.resolve("website.d/ja/glossary/index.html")) should include(
            """href="../../glossary/architecture/asuka.html""""
          )
          _read(dir.resolve("website.d/ja/glossary/index.html")) should include(
            """href="#index-ら">ら</a>"""
          )
          _read(dir.resolve("website.d/ja/glossary/index.html")) should include(
            """id="index-ら""""
          )
          _read(dir.resolve("website.d/ja/glossary/index.html")) should include(
            """href="../../glossary/architecture/runtime.html""""
          )
          _read(dir.resolve("website.d/ja/glossary/index.html")) should include(
            """>らんたいむ</a> <span class="bok-index-term-category">[Architecture]</span>"""
          )
          _read(
            dir.resolve("website.d/ja/glossary/index.html")
          ) should not include (
            """>らんたいむ</a>: Architecture"""
          )
          _read(
            dir.resolve("website.d/ja/glossary/index.html")
          ) should not include (
            """Runtime</a> <span class="bok-term-reading">(らんたいむ)</span>"""
          )
          _read(
            dir.resolve("website.d/ja/glossary/index.html")
          ) should not include ("""href="../../glossary/architecture/cloud.html"""")
          _read(dir.resolve("website.d/en/glossary/index.html")) should include(
            "英語用語索引"
          )
          _read(dir.resolve("website.d/en/glossary/index.html")) should include(
            "英語表記の用語を探すための索引ページです。"
          )
          _read(dir.resolve("website.d/en/glossary/index.html")) should include(
            """<h2>索引</h2>"""
          )
          _read(dir.resolve("website.d/en/glossary/index.html")) should include(
            """<h2>用語</h2>"""
          )
          _read(
            dir.resolve("website.d/en/glossary/index.html")
          ) should not include (
            "This page is a language-specific entry point"
          )
          _read(dir.resolve("website.d/en/glossary/index.html")) should include(
            """<span class="bok-index-term-category">[Architecture]</span>"""
          )
          _read(dir.resolve("website.d/en/glossary/index.html")) should include(
            """href="#index-c">C</a>"""
          )
          _read(dir.resolve("website.d/en/glossary/index.html")) should include(
            """href="#index-r">R</a>"""
          )
          _read(dir.resolve("website.d/en/glossary/index.html")) should include(
            """id="index-r""""
          )
          _read(dir.resolve("website.d/en/glossary/index.html")) should include(
            """href="../../glossary/architecture/cloud.html""""
          )
          _read(dir.resolve("website.d/en/glossary/index.html")) should include(
            """href="../../glossary/architecture/runtime.html""""
          )
          _read(
            dir.resolve("website.d/index.html")
          ) should not include ("""class="navbar-item" href="glossary/index.html"""")
          _read(
            dir.resolve("website.d/index.html")
          ) should not include ("""class="navbar-item" href="history/index.html"""")
          _read(
            dir.resolve("website.d/index.html")
          ) should not include ("""class="navbar-item" href="manual/index.html"""")
          _read(dir.resolve("website.d/index.html")) should include(
            """class="navbar-item has-dropdown is-hoverable navbar-bok-nav navbar-bok-dropdown""""
          )
          _read(dir.resolve("website.d/index.html")) should include(
            """class="navbar-link navbar-bok-toggle" href="#">BoK</a>"""
          )
          _read(dir.resolve("website.d/index.html")) should include(
            """class="navbar-item navbar-dropdown-item" href="glossary/index.html">用語集</a>"""
          )
          _read(dir.resolve("website.d/index.html")) should include(
            """class="navbar-item navbar-dropdown-item" href="articles/index.html">記事</a>"""
          )
          _read(dir.resolve("website.d/index.html")) should include(
            """class="navbar-item navbar-dropdown-item" href="history/index.html">履歴</a>"""
          )
          _read(dir.resolve("website.d/index.html")) should include(
            """class="navbar-item navbar-dropdown-item" href="manual/index.html">BoKマニュアル</a>"""
          )
          _read(dir.resolve("website.d/index.html")) should include(
            """class="navbar-item navbar-dropdown-item" href="bibliography/index.html">参考資料</a>"""
          )
          _read(dir.resolve("website.d/index.html")) should include(
            """class="navbar-item has-dropdown is-hoverable navbar-category-nav navbar-category-dropdown""""
          )
          _read(dir.resolve("website.d/index.html")) should include(
            """class="navbar-link navbar-category-toggle" href="#">カテゴリ</a>"""
          )
          _read(dir.resolve("website.d/index.html")) should include(
            """class="navbar-dropdown navbar-category-menu""""
          )
          _read(
            dir.resolve("website.d/index.html")
          ) should not include ("""class="navbar-category-link" href="architecture/index.html">Architecture</a>""")
          _read(dir.resolve("website.d/index.html")) should include(
            """class="navbar-item navbar-dropdown-item" href="architecture/index.html">Architecture</a>"""
          )
          _read(
            dir.resolve("website.d/index.html")
          ) should not include ("""class="navbar-item" href="architecture/index.html">Architecture</a>""")
          _read(dir.resolve("website.d/index.html")) should include(
            """class="bok-category-summary-grid""""
          )
          _read(dir.resolve("website.d/index.html")) should include(
            """class="bok-category-summary-card""""
          )
          _read(dir.resolve("website.d/index.html")) should include(
            """class="bok-category-summary-title" href="architecture/index.html">Architecture</a>"""
          )
          _read(dir.resolve("website.d/index.html")) should include(
            """<b>1</b>記事"""
          )
          _read(dir.resolve("website.d/index.html")) should include(
            """<b>3</b>用語"""
          )
          _read(dir.resolve("website.d/index.html")) should include(
            """class="bok-category-rdf-link" href="rdf/index.html?category=architecture"><b>7</b>RDF</a>"""
          )
          dir.resolve("website.d/rdf/index.html") should be_regular_file
          dir.resolve("website.d/rdf/node.html") should be_regular_file
          dir.resolve("website.d/rdf/site.ttl") should be_regular_file
          dir.resolve("website.d/rdf/site.jsonld") should be_regular_file
          dir.resolve("website.d/metadata/rdf/graph.json") should be_regular_file
          _read(dir.resolve("website.d/rdf/index.html")) should include(
            "RDF Graph"
          )
          _read(dir.resolve("website.d/rdf/index.html")) should include(
            """<body class="article bok-dashboard-theme-paper">"""
          )
          _read(dir.resolve("website.d/rdf/index.html")) should include(
            """class="body body-dashboard bok-rdf-body""""
          )
          _read(dir.resolve("website.d/rdf/index.html")) should include(
            """class="bok-rdf-workspace""""
          )
          _read(dir.resolve("website.d/rdf/index.html")) should include(
            """class="bok-rdf-panel bok-rdf-panel-graph is-active""""
          )
          _read(dir.resolve("website.d/rdf/index.html")) should include(
            """class="bok-rdf-panel bok-rdf-panel-triples""""
          )
          _read(dir.resolve("website.d/rdf/index.html")) should include(
            """class="bok-rdf-tabbar""""
          )
          _read(dir.resolve("website.d/rdf/index.html")) should include(
            """class="bok-rdf-filterbar""""
          )
          _read(dir.resolve("website.d/rdf/index.html")) should not include (
            """class="bok-rdf-toolbar""""
          )
          _read(dir.resolve("website.d/rdf/index.html")) should include(
            """data-rdf-view="graph""""
          )
          _read(dir.resolve("website.d/rdf/index.html")) should include(
            """role="tab" aria-selected="true" aria-controls="bok-rdf-panel-graph" data-rdf-view="graph""""
          )
          _read(dir.resolve("website.d/rdf/index.html")) should include(
            """aria-controls="bok-rdf-panel-nodes" data-rdf-view="nodes""""
          )
          _read(dir.resolve("website.d/rdf/index.html")) should include(
            """data-rdf-view="triples""""
          )
          _read(dir.resolve("website.d/rdf/index.html")) should include(
            """data-rdf-panel="nodes""""
          )
          _read(dir.resolve("website.d/rdf/index.html")) should include(
            """data-rdf-panel="triples" role="tabpanel""""
          )
          _read(dir.resolve("website.d/rdf/index.html")) should include(
            "bok-rdf-node-list-view"
          )
          _read(dir.resolve("website.d/rdf/index.html")) should include(
            "renderNodeListView"
          )
          _read(dir.resolve("website.d/rdf/index.html")) should include(
            "nodeListCard"
          )
          _read(dir.resolve("website.d/rdf/index.html")) should include(
            "bok-rdf-node-list-card"
          )
          _read(dir.resolve("website.d/rdf/index.html")) should not include (
            "bok-rdf-graph-detail"
          )
          _read(dir.resolve("website.d/rdf/index.html")) should not include (
            "bok-rdf-node-cloud"
          )
          _read(dir.resolve("website.d/rdf/index.html")) should include(
            "表示中のRDFノードを一覧"
          )
          _read(dir.resolve("website.d/rdf/index.html")) should include(
            """data-graph="../metadata/rdf/graph.json""""
          )
          _read(dir.resolve("website.d/rdf/index.html")) should include(
            """data-triples="site.ttl""""
          )
          _read(dir.resolve("website.d/rdf/index.html")) should include(
            """data-rdf-graph-canvas="true""""
          )
          _read(dir.resolve("website.d/rdf/index.html")) should include(
            """document.createElementNS("http://www.w3.org/2000/svg", "svg")"""
          )
          _read(dir.resolve("website.d/rdf/index.html")) should include(
            "bok-rdf-graph-node"
          )
          _read(dir.resolve("website.d/rdf/index.html")) should include(
            "bok-rdf-graph-edge"
          )
          _read(dir.resolve("website.d/rdf/index.html")) should include(
            "bok-rdf-node-popover"
          )
          _read(dir.resolve("website.d/rdf/index.html")) should include(
            """data-rdf-neighborhood="true""""
          )
          _read(dir.resolve("website.d/rdf/index.html")) should include(
            "node.html?id="
          )
          _read(dir.resolve("website.d/rdf/index.html")) should include(
            "RDFノード詳細"
          )
          _read(dir.resolve("website.d/rdf/index.html")) should include(
            "focusedGraphSlice"
          )
          _read(dir.resolve("website.d/rdf/index.html")) should include(
            "compactRdfLabel"
          )
          _read(dir.resolve("website.d/rdf/index.html")) should include(
            "compactNodeLabel"
          )
          _read(dir.resolve("website.d/rdf/index.html")) should include(
            "rdfNamespacePrefixes"
          )
          _read(dir.resolve("website.d/rdf/index.html")) should include(
            "Compact label"
          )
          _read(dir.resolve("website.d/rdf/index.html")) should include(
            "Full IRI"
          )
          _read(dir.resolve("website.d/rdf/index.html")) should include(
            "bok-rdf-node-compact-label"
          )
          _read(dir.resolve("website.d/rdf/index.html")) should include(
            "bok-rdf-node-full-iri"
          )
          _read(dir.resolve("website.d/rdf/index.html")) should include(
            "['schema', 'https://schema.org/']"
          )
          _read(dir.resolve("website.d/rdf/index.html")) should include(
            "['bok', 'https://www.simplemodeling.org/bok/']"
          )
          _read(dir.resolve("website.d/rdf/index.html")) should include(
            "schemaRequiredPredicates"
          )
          _read(dir.resolve("website.d/rdf/index.html")) should include(
            "schemaInterpretation"
          )
          _read(dir.resolve("website.d/rdf/index.html")) should include(
            "renderSchemaInterpretation"
          )
          _read(dir.resolve("website.d/rdf/index.html")) should include(
            "defaultInformationView"
          )
          _read(dir.resolve("website.d/rdf/index.html")) should include(
            "activeInformationView"
          )
          _read(dir.resolve("website.d/rdf/index.html")) should include(
            "currentInformationView"
          )
          _read(dir.resolve("website.d/rdf/index.html")) should include(
            "cncf-rdf-1.5-hop-information-view-v1"
          )
          _read(dir.resolve("website.d/rdf/index.html")) should include(
            "informationView"
          )
          _read(dir.resolve("website.d/rdf/index.html")) should include(
            "Information"
          )
          _read(dir.resolve("website.d/rdf/index.html")) should include(
            "Information View"
          )
          _read(dir.resolve("website.d/rdf/index.html")) should include(
            "informationView"
          )
          _read(dir.resolve("website.d/rdf/index.html")) should include(
            "informationSchemas"
          )
          _read(dir.resolve("website.d/rdf/index.html")) should include(
            "schemaGroup('informationView'"
          )
          _read(dir.resolve("website.d/rdf/index.html")) should include(
            "schemaGroup('information'"
          )
          _read(dir.resolve("website.d/rdf/index.html")) should include(
            "schemaGroup('schema'"
          )
          _read(dir.resolve("website.d/rdf/index.html")) should include(
            "schemaGroup('predicate'"
          )
          _read(dir.resolve("website.d/rdf/index.html")) should include(
            "information.identity"
          )
          _read(dir.resolve("website.d/rdf/index.html")) should include(
            "information.description"
          )
          _read(
            dir.resolve("website.d/rdf/index.html")
          ) should not include ("RdfNodeEntity")
          _read(
            dir.resolve("website.d/rdf/index.html")
          ) should not include ("entity.identity")
          _read(
            dir.resolve("website.d/rdf/index.html")
          ) should not include ("informationObject")
          _read(
            dir.resolve("website.d/rdf/index.html")
          ) should not include ("schema-aware")
          _read(
            dir.resolve("website.d/rdf/index.html")
          ) should not include ("RDFノードを押す")
          _read(dir.resolve("website.d/rdf/index.html")) should include(
            "schema.directional"
          )
          _read(dir.resolve("website.d/rdf/index.html")) should include(
            "schema.expansion"
          )
          _read(dir.resolve("website.d/rdf/index.html")) should include(
            "defaultPredicateProfile"
          )
          _read(dir.resolve("website.d/rdf/index.html")) should include(
            "activePredicateProfile"
          )
          _read(dir.resolve("website.d/rdf/index.html")) should include(
            "__bokRdfInformationView"
          )
          _read(dir.resolve("website.d/rdf/index.html")) should include(
            "uniqueStrings([base.roles[role], configured.roles[role]])"
          )
          _read(dir.resolve("website.d/rdf/index.html")) should include(
            "cncf-rdf-1.5-hop-v1"
          )
          _read(dir.resolve("website.d/rdf/index.html")) should include(
            "textus:primaryRdfAnchor"
          )
          _read(dir.resolve("website.d/rdf/index.html")) should include(
            "skos:exactMatch"
          )
          _read(dir.resolve("website.d/rdf/index.html")) should include(
            "skos:closeMatch"
          )
          _read(dir.resolve("website.d/rdf/index.html")) should include(
            """class="bok-rdf-node-schema""""
          )
          _read(dir.resolve("website.d/rdf/index.html")) should include(
            "bok-rdf-node-schema-groups"
          )
          _read(dir.resolve("website.d/rdf/index.html")) should include(
            "bok-rdf-node-schema-group"
          )
          _read(dir.resolve("website.d/rdf/index.html")) should include(
            """class="bok-rdf-node-schema-object""""
          )
          _read(dir.resolve("website.d/rdf/index.html")) should include(
            "bok-rdf-node-popover-actions-primary"
          )
          _read(dir.resolve("website.d/rdf/index.html")) should include(
            "predicateProfile"
          )
          _read(dir.resolve("website.d/rdf/index.html")) should include(
            "predicateRoles"
          )
          _read(dir.resolve("website.d/rdf/index.html")) should include(
            "identityPredicates"
          )
          _read(dir.resolve("website.d/rdf/index.html")) should include(
            "linkPredicates"
          )
          _read(dir.resolve("website.d/rdf/index.html")) should include(
            "hierarchyPredicates"
          )
          _read(dir.resolve("website.d/rdf/index.html")) should include(
            "provenancePredicates"
          )
          _read(dir.resolve("website.d/rdf/index.html")) should include(
            "requiredPredicates"
          )
          _read(dir.resolve("website.d/rdf/index.html")) should include(
            "descriptivePredicates"
          )
          _read(dir.resolve("website.d/rdf/index.html")) should include(
            "outgoingRequiredPredicates"
          )
          _read(dir.resolve("website.d/rdf/index.html")) should include(
            "incomingRequiredPredicates"
          )
          _read(dir.resolve("website.d/rdf/index.html")) should include(
            "isDefaultDescriptionPredicate"
          )
          _read(dir.resolve("website.d/rdf/index.html")) should include(
            "1.5+hop"
          )
          _read(
            dir.resolve("website.d/rdf/index.html")
          ) should not include ("""class="nav-container"""")
          _read(
            dir.resolve("website.d/rdf/index.html")
          ) should not include ("""class="toc sidebar"""")
          _read(
            dir.resolve("website.d/rdf/index.html")
          ) should not include ("""<h2>Dashboard</h2>""")
          _read(dir.resolve("website.d/rdf/index.html")) should include(
            "site.ttl"
          )
          _read(dir.resolve("website.d/rdf/node.html")) should include(
            """class="bok-rdf-node-page""""
          )
          _read(dir.resolve("website.d/rdf/node.html")) should include(
            "bok-rdf-node-detail-grid"
          )
          _read(dir.resolve("website.d/rdf/node.html")) should include(
            "informationView"
          )
          _read(dir.resolve("website.d/rdf/node.html")) should include(
            "predicateProfile"
          )
          _read(dir.resolve("website.d/rdf/node.html")) should include(
            "bok-rdf-node-schema-groups"
          )
          _read(dir.resolve("website.d/rdf/node.html")) should include("接続数")
          _read(dir.resolve("website.d/rdf/node.html")) should include(
            "index.html?node="
          )
          _read(
            dir.resolve("website.d/metadata/rdf/graph.json")
          ) should include(""""category": "architecture"""")
          _read(
            dir.resolve("website.d/metadata/rdf/graph.json")
          ) should include(""""informationView"""")
          _read(
            dir.resolve("website.d/metadata/rdf/graph.json")
          ) should include(""""test-rdf-1.5-hop-information-view"""")
          _read(
            dir.resolve("website.d/metadata/rdf/graph.json")
          ) should include(""""informationView"""")
          _read(
            dir.resolve("website.d/metadata/rdf/graph.json")
          ) should include(""""informationSchemas"""")
          _read(
            dir.resolve("website.d/metadata/rdf/graph.json")
          ) should not include (""""informationObject"""")
          _read(
            dir.resolve("website.d/metadata/rdf/graph.json")
          ) should include(""""predicateProfile"""")
          _read(
            dir.resolve("website.d/metadata/rdf/graph.json")
          ) should include(""""test-rdf-1.5-hop-profile"""")
          _read(
            dir.resolve("website.d/index.html")
          ) should not include ("Lexicon")
          _read(
            dir.resolve("website.d/architecture/index.html")
          ) should include("""<h1 class="page">Architecture</h1>""")
          _read(
            dir.resolve("website.d/architecture/index.html")
          ) should not include ("""<h1 class="page">Architecture Dashboard</h1>""")
          _read(
            dir.resolve("website.d/architecture/index.html")
          ) should include("このカテゴリの目的、記事、用語、更新推移を集約するDashboard")
          _read(
            dir.resolve("website.d/architecture/index.html")
          ) should include(
            """class="bok-dashboard container-fluid bok-dashboard-command-center""""
          )
          _read(
            dir.resolve("website.d/architecture/index.html")
          ) should include("""class="bok-dashboard-shell" id="dashboard"""")
          _read(
            dir.resolve("website.d/architecture/index.html")
          ) should include("""class="bok-dashboard-hero"""")
          _read(
            dir.resolve("website.d/architecture/index.html")
          ) should include("""class="row g-3"""")
          _read(
            dir.resolve("website.d/architecture/index.html")
          ) should include("""class="card bok-card bok-card-purpose bok-card-category-purpose"""")
          _read(
            dir.resolve("website.d/architecture/index.html")
          ) should include("""class="col-12 col-xl-8"""")
          _read(
            dir.resolve("website.d/architecture/index.html")
          ) should include("""class="card bok-card bok-card-readiness"""")
          _read(
            dir.resolve("website.d/architecture/index.html")
          ) should include("""class="card bok-card bok-card-kpi"""")
          _read(
            dir.resolve("website.d/architecture/index.html")
          ) should include("""class="card bok-card bok-card-chart"""")
          _read(
            dir.resolve("website.d/architecture/index.html")
          ) should include("""class="card bok-card bok-card-quality"""")
          _read(
            dir.resolve("website.d/architecture/index.html")
          ) should include(
            """class="card bok-card bok-card-map" data-bok-actors="reader contributor project_manager""""
          )
          _read(
            dir.resolve("website.d/architecture/index.html")
          ) should include("""class="card bok-card bok-card-related"""")
          _read(dir.resolve("website.d/architecture/index.html"))
            .indexOf("""bok-card-purpose""") should be < _read(
            dir.resolve("website.d/architecture/index.html")
          ).indexOf("""bok-card-map""")
          _read(dir.resolve("website.d/architecture/index.html"))
            .indexOf("""bok-card-map""") should be < _read(
            dir.resolve("website.d/architecture/index.html")
          ).indexOf("""bok-card-readiness""")
          _read(
            dir.resolve("website.d/architecture/index.html")
          ) should include("""class="bok-purpose-vision-panel"""")
          _read(
            dir.resolve("website.d/architecture/index.html")
          ) should include("""class="bok-purpose-tree-label"><span class="bok-purpose-node-label">G</span><strong>Goals</strong>""")
          _read(
            dir.resolve("website.d/architecture/index.html")
          ) should include("Make architecture decisions traceable.")
          _read(
            dir.resolve("website.d/architecture/index.html")
          ) should include(
            """class="bok-purpose-goal-head"><span class="bok-purpose-node-label">G1</span><strong>Explain architecture concepts</strong>"""
          )
          _read(
            dir.resolve("website.d/architecture/index.html")
          ) should include(
            """class="bok-purpose-node-label">S1</span><span class="bok-purpose-subgoal-copy"><small>G1を支援</small><span>Maintain architecture glossary</span>"""
          )
          _read(
            dir.resolve("website.d/architecture/index.html")
          ) should include("Explain architecture concepts")
          _read(
            dir.resolve("website.d/architecture/index.html")
          ) should include("Link concepts to runtime")
          _read(
            dir.resolve("website.d/architecture/index.html")
          ) should include("Maintain architecture glossary")
          _read(
            dir.resolve("website.d/architecture/index.html")
          ) should include("""class="bok-dashboard-chart"""")
          _read(
            dir.resolve("website.d/architecture/index.html")
          ) should include("""aria-label="Architecture 増分"""")
          _read(
            dir.resolve("website.d/architecture/index.html")
          ) should include("""data-chart="cumulative-date"""")
          _read(
            dir.resolve("website.d/architecture/index.html")
          ) should include(
            """class="bok-cumulative-line bok-cumulative-line-articles""""
          )
          _read(
            dir.resolve("website.d/architecture/index.html")
          ) should include(
            """class="bok-cumulative-line bok-cumulative-line-terms""""
          )
          _read(
            dir.resolve("website.d/architecture/index.html")
          ) should include("""datetime="2026-06-03"""")
          _read(
            dir.resolve("website.d/architecture/index.html")
          ) should include("""datetime="2026-06-04"""")
          _read(
            dir.resolve("website.d/architecture/index.html")
          ) should include("""<div class="bok-kpi-label">記事</div>""")
          _read(
            dir.resolve("website.d/architecture/index.html")
          ) should include("""<div class="bok-kpi-label">用語</div>""")
          _read(
            dir.resolve("website.d/architecture/index.html")
          ) should include("記事一覧")
          _read(
            dir.resolve("website.d/architecture/index.html")
          ) should include("用語一覧")
          _read(
            dir.resolve("website.d/architecture/index.html")
          ) should include("""class="body body-dashboard"""")
          _read(
            dir.resolve("website.d/architecture/index.html")
          ) should not include ("""class="nav-container"""")
          _read(
            dir.resolve("website.d/architecture/index.html")
          ) should not include ("""class="toc sidebar"""")
          _read(
            dir.resolve("website.d/architecture/index.html")
          ) should include("""<a href="../glossary/index.html">用語集</a>""")
          _read(
            dir.resolve("website.d/architecture/index.html")
          ) should include("""href="../glossary/architecture/runtime.html"""")
          _read(
            dir.resolve("website.d/architecture/index.html")
          ) should not include ("""class="navbar-item" href="../glossary/index.html"""")
          _read(
            dir.resolve("website.d/architecture/index.html")
          ) should not include ("""class="navbar-item" href="../history/index.html"""")
          _read(
            dir.resolve("website.d/architecture/index.html")
          ) should not include ("""class="navbar-item" href="../manual/index.html"""")
          _read(
            dir.resolve("website.d/architecture/index.html")
          ) should include(
            """class="navbar-item has-dropdown is-hoverable navbar-bok-nav navbar-bok-dropdown""""
          )
          _read(
            dir.resolve("website.d/architecture/index.html")
          ) should include(
            """class="navbar-link navbar-bok-toggle" href="#">BoK</a>"""
          )
          _read(
            dir.resolve("website.d/architecture/index.html")
          ) should include(
            """class="navbar-item navbar-dropdown-item" href="../glossary/index.html">用語集</a>"""
          )
          _read(
            dir.resolve("website.d/architecture/index.html")
          ) should include(
            """class="navbar-item navbar-dropdown-item" href="../articles/index.html">記事</a>"""
          )
          _read(
            dir.resolve("website.d/architecture/index.html")
          ) should include(
            """class="navbar-item navbar-dropdown-item" href="../history/index.html">履歴</a>"""
          )
          _read(
            dir.resolve("website.d/architecture/index.html")
          ) should include(
            """class="navbar-item navbar-dropdown-item" href="../manual/index.html">BoKマニュアル</a>"""
          )
          _read(
            dir.resolve("website.d/architecture/index.html")
          ) should include(
            """class="navbar-item navbar-dropdown-item" href="../bibliography/index.html">参考資料</a>"""
          )
          _read(
            dir.resolve("website.d/architecture/index.html")
          ) should include(
            """class="navbar-item navbar-dropdown-item" href="../architecture/index.html">Architecture</a>"""
          )
          _read(
            dir.resolve("website.d/architecture/index.html")
          ) should not include ("""class="navbar-category-link" href="../architecture/index.html">Architecture</a>""")
          _read(
            dir.resolve("website.d/architecture/index.html")
          ) should not include ("""class="navbar-item" href="../architecture/index.html">Architecture</a>""")
          _read(
            dir.resolve("website.d/architecture/index.html")
          ) should not include ("Lexicon")
          dir.resolve(
            "src/main/antora-ui/build/ui-bundle.zip"
          ) should be_regular_file
          dir.resolve("doxsite.d/ja") shouldNot exist_path
          dir.resolve("doxsite.d/en") shouldNot exist_path
          dir.resolve(
            "doxsite-cache-work-in-progress.d/stale.error_msg"
          ) shouldNot exist_path
          runner.commands should not_contain_where[Vector[String]](
            _.headOption.contains("arcadia")
          )
        }
      }

      "succeed without dashboard metadata and not fabricate dashboard counts" in {
        _with_temp_dir("cozy-bok-no-dashboard-metadata") { dir =>
          Given("a BoK project whose SmartDox output has no dashboard metadata")
          _write(
            dir.resolve(".cozy/config.yaml"),
            "bok:\n  docker-image: antora-image\n"
          )
          _write(
            dir.resolve("src/main/doxsite/site.conf"),
            "site {\n  output {\n    locale_mode = \"single_locale_root\"\n  }\n}\n"
          )
          _write(
            dir.resolve("src/main/doxsite/architecture/category.yaml"),
            "name: Architecture\ntitle: Architecture\ndescription: Architecture category.\n"
          )
          _write(
            dir.resolve("src/main/doxsite/architecture/overview.dox"),
            "Overview\n========\n\n# HEAD\n\n## BRIEF\nArchitecture overview.\n"
          )
          val config =
            CozyBok.BuildConfig.create(List(dir.toString, "--strategy", "wip"))

          When("Cozy builds the site")
          CozyBok.build(config, new NoDashboardRunner)

          Then(
            "the build succeeds and missing dashboard data is reported without fabricated metrics"
          )
          _read(
            dir.resolve("website.d/index.html")
          ) should not include ("""<div class="bok-kpi-label">カテゴリ</div>""")
          _read(
            dir.resolve("website.d/architecture/index.html")
          ) should include("Dashboard metadata is not available.")
          _read(
            dir.resolve("website.d/architecture/index.html")
          ) should not include ("""class="bok-dashboard-grid"""")
        }
      }

      "render legacy dashboard increments as total series" in {
        _with_temp_dir("cozy-bok-legacy-dashboard") { dir =>
          Given("a BoK project with legacy dashboard increment metadata")
          _write(
            dir.resolve(".cozy/config.yaml"),
            "bok:\n  docker-image: antora-image\n"
          )
          _write(
            dir.resolve("src/main/doxsite/site.conf"),
            "site {\n  output {\n    locale_mode = \"single_locale_root\"\n  }\n}\n"
          )
          _write(
            dir.resolve("src/main/doxsite/architecture/category.yaml"),
            "name: Architecture\ntitle: Architecture\ndescription: Architecture category.\n"
          )
          _write(
            dir.resolve("src/main/doxsite/architecture/overview.dox"),
            "Overview\n========\n\n# HEAD\n\n## BRIEF\nArchitecture overview.\n"
          )
          val config =
            CozyBok.BuildConfig.create(List(dir.toString, "--strategy", "wip"))

          When("Cozy renders the dashboard")
          CozyBok.build(config, new LegacyDashboardRunner)

          Then("the increment graph is rendered as the legacy total series")
          val home = _read(dir.resolve("website.d/index.html"))
          home should include(
            """class="bok-cumulative-line bok-cumulative-line-total""""
          )
          home should include("""bok-cumulative-marker-total""")
          home should include(">合計</span>")
          home should not include ("""class="bok-cumulative-line bok-cumulative-line-articles"""")
          home should not include ("A:")
          home should include("合計")
        }
      }

      "link SmartDox generated history year page when available" in {
        _with_temp_dir("cozy-bok-history-year") { dir =>
          Given("a BoK site where SmartDox generated a yearly history page")
          _write(
            dir.resolve(".cozy/config.yaml"),
            "bok:\n  docker-image: antora-image\n"
          )
          _write(
            dir.resolve("src/main/doxsite/site.conf"),
            "site {\n  output {\n    locale_mode = \"single_locale_root\"\n  }\n}\n"
          )
          _write(
            dir.resolve("src/main/doxsite/glossary/category.yaml"),
            "name: Glossary\ntitle: 用語集\ndescription: BoK全体で共有する用語集。\n"
          )
          _write(
            dir.resolve("src/main/doxsite/architecture/category.yaml"),
            "name: Architecture\ntitle: Architecture\ndescription: Architecture category.\n"
          )
          _write(
            dir.resolve("src/main/doxsite/architecture/overview.dox"),
            "Overview\n========\n\n# HEAD\n\n## BRIEF\nArchitecture overview.\n"
          )
          _write(
            dir.resolve("src/main/doxsite/glossary/architecture/runtime.dox"),
            "Runtime\n=======\n\n# HEAD\n\n# Definition\nRuntime term.\n"
          )
          val config =
            CozyBok.BuildConfig.create(List(dir.toString, "--strategy", "wip"))
          val runner = new HistoryYearRunner

          When("Cozy post-processes site navigation")
          CozyBok.build(config, runner)

          Then(
            "category and glossary pages link to the generated history year page"
          )
          _read(
            dir.resolve("website.d/architecture/index.html")
          ) should include("""href="../history/2026.html">履歴</a>""")
          _read(dir.resolve("website.d/glossary/index.html")) should include(
            """href="../history/2026.html">履歴</a>"""
          )
          _read(dir.resolve("website.d/ja/glossary/index.html")) should include(
            """href="../../history/2026.html">History</a>"""
          )
          dir.resolve("website.d/history/index.html") shouldNot exist_path
        }
      }

    }

    "resolve build configuration" which {
      "let CLI docker image override config" in {
        _with_temp_dir("cozy-bok-docker-override") { dir =>
          Given("a BoK config Docker image and a CLI Docker image")
          _write(
            dir.resolve(".cozy/config.yaml"),
            "bok:\n  docker-image: config-image\n"
          )
          When("build configuration is resolved")
          val config = CozyBok.BuildConfig.create(
            List(dir.toString, "--docker-image", "cli-image")
          )
          Then("the CLI Docker image takes precedence")
          config.dockerImage shouldBe "cli-image"
        }
      }

      "read conf/cozy defaults before .cozy local overrides" in {
        _with_temp_dir("cozy-bok-config-precedence") { dir =>
          Given("shared BoK defaults under conf/cozy")
          _write(
            dir.resolve("conf/cozy/config.yaml"),
            "bok:\n  docker-image: shared-image\n"
          )
          When("build configuration is resolved")
          val shared = CozyBok.BuildConfig.create(List(dir.toString))
          Then("shared defaults are used first")
          shared.dockerImage shouldBe "shared-image"

          Given("local overrides under .cozy")
          _write(
            dir.resolve(".cozy/config.yaml"),
            "bok:\n  docker-image: local-image\n"
          )
          When("build configuration is resolved again")
          val local = CozyBok.BuildConfig.create(List(dir.toString))
          Then("local overrides take precedence")
          local.dockerImage shouldBe "local-image"
        }
      }

      "default to the standard Textus toolchain Docker image" in {
        _with_temp_dir("cozy-bok-default-docker") { dir =>
          Given("a BoK project without Docker image settings")
          When("build configuration is resolved")
          val config = CozyBok.BuildConfig.create(List(dir.toString))
          Then("the canonical Textus toolchain image is selected")
          config.dockerImage shouldBe "ghcr.io/asami/textus-toolchain:latest"
        }
      }

      "pass the configured Textus toolchain image to SmartDox Kroki execution" in {
        _with_temp_dir("cozy-bok-smartdox-kroki-toolchain") { dir =>
          Given("a BoK build with a configured Textus toolchain image")
          val runner = new EnvRecordingRunner
          val config = CozyBok.BuildConfig.create(
            List(dir.toString, "--docker-image", "example/toolchain:dev")
          )

          When("Cozy builds through SmartDox and Docker Antora")
          CozyBok.build(config, runner)

          Then(
            "SmartDox host-side commands receive the toolchain image as Kroki Docker image"
          )
          val smartdoxenvs = runner.envs.filter(_.nonEmpty)
          smartdoxenvs should have size 2
          smartdoxenvs.foreach { env =>
            env("SMARTDOX_KROKI_DOCKER_IMAGE") shouldBe "example/toolchain:dev"
            env("SMARTDOX_PDF_DOCKER_IMAGE") shouldBe "example/toolchain:dev"
            env(
              "SMARTDOX_COZY_TOOLCHAIN_IMAGE"
            ) shouldBe "example/toolchain:dev"
          }

          And(
            "Docker Antora starts the internal Kroki server on SmartDox's Antora port"
          )
          runner.commands.exists(cmd =>
            cmd.contains("SMARTDOX_KROKI_PORT=9609") &&
              cmd.contains("SMARTDOX_KROKI_DOCKER_IMAGE=example/toolchain:dev")
          ) shouldBe true
        }
      }

      "use standard cozy docker image config when bok docker image is omitted" in {
        _with_temp_dir("cozy-bok-standard-docker") { dir =>
          Given(
            "a project-level Cozy Docker image setting and no BoK-specific Docker image"
          )
          _write(
            dir.resolve(".cozy/config.yaml"),
            "cozy:\n  docker-image: cozy-config-image\n"
          )
          When("build configuration is resolved")
          val config = CozyBok.BuildConfig.create(List(dir.toString))
          Then(
            "the standard Cozy Docker image setting is used as the BoK fallback"
          )
          config.dockerImage shouldBe "cozy-config-image"
        }
      }

      "override dox site output scope policy" in {
        _with_temp_dir("cozy-bok-site-output-scope") { dir =>
          Given("a BoK config with an explicit output scope policy")
          _write(
            dir.resolve(".cozy/config.yaml"),
            """bok:
          |  output:
          |    scope:
          |      policy: all
          |""".stripMargin
          )
          When("build configuration is resolved")
          val config = CozyBok.BuildConfig.create(List(dir.toString))
          Then("the configured output scope policy is passed to SmartDox")
          config.siteOutputScopePolicy shouldBe "all"
        }
      }

      "use pdf docker image config as compatibility fallback" in {
        _with_temp_dir("cozy-bok-pdf-docker") { dir =>
          Given(
            "a legacy PDF Docker image setting and no newer Cozy or BoK Docker image setting"
          )
          _write(
            dir.resolve(".cozy/config.yaml"),
            "pdf:\n  docker-image: pdf-config-image\n"
          )
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
          _write(
            dir.resolve(".cozy/config.yaml"),
            "bok:\n  docker-image: config-image\n"
          )
          _write(dir.resolve("src/main/doxsite/site.conf"), "site {}\n")
          _write(
            dir.resolve("src/main/doxsite/concept/category.yaml"),
            "name: Concept\n"
          )
          val nested = dir.resolve("src/main/doxsite/concept")

          When("build configuration is resolved from the nested path")
          val config = CozyBok.BuildConfig.create(List(nested.toString))

          Then("Cozy uses the BoK project root for all build paths")
          config.project shouldBe dir.toAbsolutePath.normalize
          config.sourcePath shouldBe dir.resolve("src/main/doxsite")
          config.dockerImage shouldBe "config-image"
        }
      }

      "default doctor input to current directory" in {
        Given("no explicit doctor project argument")
        val expected = sys.env
          .get("PWD")
          .map(Paths.get(_).toAbsolutePath.normalize)
          .getOrElse(Paths.get(".").toAbsolutePath.normalize)

        When("Cozy creates the doctor configuration")
        val config = CozyBok.DoctorConfig.create(Nil, fix = false)

        Then("the logical current directory is used as the inspection input")
        config.input shouldBe expected
      }

      "default preview input to current directory" in {
        Given("no explicit preview project argument")
        val expected = sys.env
          .get("PWD")
          .map(Paths.get(_).toAbsolutePath.normalize)
          .getOrElse(Paths.get(".").toAbsolutePath.normalize)

        When("Cozy creates the preview configuration")
        val config = CozyBok.PreviewConfig.create(Nil)

        Then("the logical current directory is used as the preview input")
        config.input shouldBe expected
      }

      "default build input to current directory" in {
        Given("no explicit build project argument")
        val expected = sys.env
          .get("PWD")
          .map(Paths.get(_).toAbsolutePath.normalize)
          .getOrElse(Paths.get(".").toAbsolutePath.normalize)

        When("Cozy creates the build configuration")
        val config = CozyBok.BuildConfig.create(List("--strategy", "preview"))

        Then("the logical current directory is used as the BoK project root")
        config.project shouldBe expected
        config.strategy shouldBe "production-preview"
      }

      "report BoK root markers and safe repair candidates" in {
        _with_temp_dir("cozy-bok-doctor") { dir =>
          Given(
            "a BoK project with a legacy Docker image and incomplete generated-directory ignores"
          )
          _write(
            dir.resolve(".cozy/config.yaml"),
            "cozy:\n  docker-image: simplemodeling/cozy-toolchain:latest\nbok:\n  source: src/main/doxsite\n"
          )
          _write(dir.resolve("src/main/doxsite/site.conf"), "site {}\n")
          _write(dir.resolve(".gitignore"), "/target/\n")

          When("Cozy inspects the BoK project in dry-run fix mode")
          val output = _capture {
            CozyBok.doctor(
              CozyBok.DoctorConfig.create(
                List(
                  dir.resolve("src/main/doxsite").toString,
                  "--fix",
                  "--dry-run"
                ),
                fix = false
              )
            )
          }

          Then(
            "the diagnostic identifies the root and the planned non-destructive repairs"
          )
          output should include("status: needs-fix")
          output should include(s"root: ${dir.toAbsolutePath.normalize}")
          output should include("bok root markers:")
          output should include("src/main/doxsite/site.conf")
          output should include("Legacy Docker image reference found")
          output should include("Replace legacy Docker image")
          output should include("Append generated/work directory ignores")
          output should include("next steps:")
          output should include("cozy bok build --strategy preview")
          output should include("cozy bok build <bok-root> --strategy preview")
          output should include("cozy bok preview --port 8980")
          output should include("cozy bok preview <bok-root>")
          output should include("http://127.0.0.1:8980/")
          output should include(
            "Strategy states: draft -> wip (work-in-progress) -> preview -> production/publish"
          )
          output should include(
            "Strategy meaning: draft/wip are authoring states"
          )
          output should include(
            "use the local Web server instead of opening generated HTML directly"
          )
          _read(dir.resolve(".cozy/config.yaml")) should include(
            "simplemodeling/cozy-toolchain:latest"
          )
          _read(dir.resolve(".gitignore")) should not include ("/website.d/")
        }
      }

      "include configured preview port in doctor next steps" in {
        _with_temp_dir("cozy-bok-doctor-preview-port") { dir =>
          Given("a BoK project with a public preview port setting")
          _write(
            dir.resolve("conf/cozy/config.yaml"),
            "bok:\n  preview:\n    port: 8982\n"
          )
          _write(dir.resolve("src/main/doxsite/site.conf"), "site {}\n")

          When("Cozy inspects the BoK project")
          val output = _capture {
            CozyBok.doctor(
              CozyBok.DoctorConfig.create(List(dir.toString), fix = false)
            )
          }

          Then("the next steps use the configured preview port")
          output should include("cozy bok preview")
          output should include("http://127.0.0.1:8982/")
        }
      }

      "lint and repair SmartDox metadata section spacing" in {
        _with_temp_dir("cozy-bok-doctor-dox-spacing") { dir =>
          Given("a BoK project with Markdown-style SmartDox metadata sections")
          _write(
            dir.resolve("conf/cozy/config.yaml"),
            "bok:\n  source: src/main/doxsite\n  workflow:\n    upload:\n      command: etc/upload.sh\n"
          )
          _write(dir.resolve("src/main/doxsite/site.conf"), "site {}\n")
          _write(
            dir.resolve("src/main/doxsite/index.dox"),
            """Home
              |======
              |
              |# HEAD
              |status=work-in-progress
              |
              |
              |## HEADLINE
              |KnowledgeHub
              |
              |## BRIEF
              |KnowledgeHub brief.
              |
              |# Overview
              |
              |Body.
              |""".stripMargin
          )

          When("Cozy inspects the BoK source")
          val output = _capture {
            CozyBok.doctor(
              CozyBok.DoctorConfig.create(List(dir.toString), fix = false)
            )
          }

          Then(
            "the diagnostic reports Dox metadata sections that miss the required blank line"
          )
          output should include("status: needs-fix")
          output should include(
            "SmartDox Dox metadata section heading must be followed by a blank line"
          )
          output should include("src/main/doxsite/index.dox:4 # HEAD")
          output should include("src/main/doxsite/index.dox:8 ## HEADLINE")
          output should include("src/main/doxsite/index.dox:11 ## BRIEF")

          When("Cozy previews safe fixes")
          val dryrun = _capture {
            CozyBok.doctor(
              CozyBok.DoctorConfig
                .create(List(dir.toString, "--fix", "--dry-run"), fix = false)
            )
          }

          Then(
            "the dry-run lists the Dox spacing repair without modifying the source"
          )
          dryrun should include("planned fixes:")
          dryrun should include(
            "Insert blank lines after 3 SmartDox metadata section headings in src/main/doxsite/index.dox"
          )
          _read(dir.resolve("src/main/doxsite/index.dox")) should include(
            "## HEADLINE\nKnowledgeHub"
          )

          When("Cozy applies the safe fixes")
          _capture {
            CozyBok.doctor(
              CozyBok.DoctorConfig.create(List(dir.toString), fix = true)
            )
          }

          Then(
            "only the required blank lines are inserted for Dox metadata parsing"
          )
          val fixed = _read(dir.resolve("src/main/doxsite/index.dox"))
          fixed should include("# HEAD\n\nstatus=work-in-progress")
          fixed should include("## HEADLINE\n\nKnowledgeHub")
          fixed should include("## BRIEF\n\nKnowledgeHub brief.")
          fixed should not include ("## HEADLINE\nKnowledgeHub")
          fixed should not include ("## BRIEF\nKnowledgeHub brief.")
        }
      }

      "lint Markdown front matter without applying SmartDox spacing rules" in {
        _with_temp_dir("cozy-bok-doctor-markdown") { dir =>
          Given(
            "a BoK project with Markdown source front matter missing basic BoK metadata"
          )
          _write(
            dir.resolve("conf/cozy/config.yaml"),
            "bok:\n  source: src/main/doxsite\n  workflow:\n    upload:\n      command: etc/upload.sh\n"
          )
          _write(dir.resolve("src/main/doxsite/site.conf"), "site {}\n")
          _write(
            dir.resolve("src/main/doxsite/index.md"),
            """---
              |title: Markdown Home
              |---
              |
              |# Overview
              |
              |Markdown body.
              |""".stripMargin
          )
          _write(
            dir.resolve("src/main/doxsite/concept/article.md"),
            """---
              |headline: Concept Article
              |brief: Concept article brief.
              |---
              |
              |# Concept Article
              |
              |Markdown body.
              |""".stripMargin
          )

          When("Cozy inspects the BoK source")
          val output = _capture {
            CozyBok.doctor(
              CozyBok.DoctorConfig.create(List(dir.toString), fix = false)
            )
          }

          Then(
            "the diagnostic reports Markdown front matter issues separately from SmartDox Dox spacing"
          )
          output should include("Markdown front matter metadata issue")
          output should include(
            "src/main/doxsite/index.md: front matter should include brief, summary, or description"
          )
          output should not include ("src/main/doxsite/index.md:1 ---")
          output should not include ("SmartDox Dox metadata section heading must be followed by a blank line: src/main/doxsite/index.md")

          When("Cozy previews safe fixes")
          val dryrun = _capture {
            CozyBok.doctor(
              CozyBok.DoctorConfig
                .create(List(dir.toString, "--fix", "--dry-run"), fix = false)
            )
          }

          Then(
            "Markdown front matter is diagnostic-only and source text is unchanged"
          )
          dryrun should include("Markdown front matter metadata issue")
          dryrun should not include ("Insert blank lines after")
          _read(dir.resolve("src/main/doxsite/index.md")) should include(
            "title: Markdown Home"
          )
          _read(
            dir.resolve("src/main/doxsite/index.md")
          ) should not include ("brief:")
        }
      }

      "apply safe BoK repairs without changing source content" in {
        _with_temp_dir("cozy-bok-fix") { dir =>
          Given(
            "a BoK project whose operational config and generated-directory ignores are stale"
          )
          _write(
            dir.resolve(".cozy/config.yaml"),
            "cozy:\n  docker-image: simplemodeling/cozy-toolchain:latest\nbok:\n  source: src/main/doxsite\n"
          )
          _write(dir.resolve("src/main/doxsite/site.conf"), "site {}\n")
          _write(
            dir.resolve("src/main/doxsite/concept/index.dox"),
            "Concept\n=======\n"
          )
          _write(dir.resolve(".gitignore"), "/target/\n")

          When("Cozy applies safe BoK fixes")
          _capture {
            CozyBok.doctor(
              CozyBok.DoctorConfig.create(List(dir.toString), fix = true)
            )
          }

          Then(
            "the canonical Docker image and generated-directory ignores are updated"
          )
          _read(dir.resolve(".cozy/config.yaml")) should include(
            "ghcr.io/asami/textus-toolchain:latest"
          )
          _read(
            dir.resolve(".cozy/config.yaml")
          ) should not include ("simplemodeling/cozy-toolchain:latest")
          _read(dir.resolve(".gitignore")) should include("/website.d/")
          _read(dir.resolve(".gitignore")) should include("/doxsite.d/")
          _read(dir.resolve(".gitignore")) should include("/antora.d/")
          And("the source article remains untouched")
          _read(
            dir.resolve("src/main/doxsite/concept/index.dox")
          ) shouldBe "Concept\n=======\n"
        }
      }
    }

    "invoke SmartDox compatibility output" which {
      "use simplemodelingorg compatibility locale default from site.conf" in {
        _with_temp_dir("cozy-bok-simplemodeling") { dir =>
          Given(
            "a simplemodeling.org compatible site.conf with Japanese and English languages"
          )
          _write(
            dir.resolve(".cozy/config.yaml"),
            "bok:\n  docker-image: antora-image\n"
          )
          _write(
            dir.resolve("src/main/doxsite/site.conf"),
            """simplemodelingorg = true
          |site {
          |  metadata {
          |    in_language = ["ja", "en"]
          |  }
          |}
          |""".stripMargin
          )
          val config = CozyBok.BuildConfig.create(List(dir.toString))
          val runner = new RecordingRunner

          When("Cozy builds the BoK")
          CozyBok.build(config, runner)

          Then("localized website outputs are planned for both ja and en")
          runner.commands should contain_where[Vector[String]](
            _.contains("/workspace/website.d/ja")
          )
          runner.commands should contain_where[Vector[String]](
            _.contains("/workspace/website.d/en")
          )
        }
      }

    }

    "run optional build extensions" which {
      "include arcadia step only when enabled" in {
        _with_temp_dir("cozy-bok-arcadia") { dir =>
          Given("a BoK config with Arcadia site generation enabled")
          _write(
            dir.resolve(".cozy/config.yaml"),
            """bok:
          |  docker-image: antora-image
          |  arcadia:
          |    enabled: true
          |    source: src/main/arcadiasite
          |""".stripMargin
          )
          val config = CozyBok.BuildConfig.create(List(dir.toString))
          val runner = new RecordingRunner

          When("Cozy builds the BoK")
          CozyBok.build(config, runner)

          Then(
            "the Arcadia generation step is included exactly when configured"
          )
          runner.commands should contain(
            Vector("arcadia", "site", "src/main/arcadiasite", "arcadiasite.d")
          )
        }
      }

      "copy direct assets only in production when configured" in {
        _with_temp_dir("cozy-bok-direct-assets") { dir =>
          Given("a production BoK build with configured direct assets")
          _write(
            dir.resolve(".cozy/config.yaml"),
            """bok:
          |  docker-image: antora-image
          |  direct-assets:
          |    enabled: true
          |    items:
          |      - source: src/main/javascript/knowledge-graph
          |        destination: website.d/knowledge-graph
          |""".stripMargin
          )
          _write(
            dir.resolve("src/main/javascript/knowledge-graph/app.js"),
            "console.log('graph')\n"
          )
          val config = CozyBok.BuildConfig.create(
            List(dir.toString, "--strategy", "production")
          )
          val runner = new RecordingRunner

          When("Cozy builds the site")
          CozyBok.build(config, runner)

          Then("the direct assets are copied into the generated website")
          runner.commands should contain(
            Vector(
              "dox",
              "site-mark",
              "-strategy",
              "production",
              "-output.scope.policy",
              "all",
              "src/main/doxsite"
            )
          )
          dir.resolve("website.d/knowledge-graph/app.js") should be_regular_file
        }
      }

      "ignore unrelated yaml items when direct assets are enabled" in {
        _with_temp_dir("cozy-bok-direct-assets-scope") { dir =>
          Given(
            "a BoK config containing direct assets and unrelated publication YAML items"
          )
          _write(
            dir.resolve(".cozy/config.yaml"),
            """bok:
          |  docker-image: antora-image
          |  direct-assets:
          |    enabled: true
          |publication:
          |  items:
          |    - source: unrelated/source
          |      destination: website.d/unrelated
          |""".stripMargin
          )
          _write(
            dir.resolve("unrelated/source/app.js"),
            "console.log('unrelated')\n"
          )
          val config = CozyBok.BuildConfig.create(
            List(dir.toString, "--strategy", "production")
          )

          When("Cozy builds the site")
          CozyBok.build(config, new RecordingRunner)

          Then("only BoK direct asset settings are applied")
          dir.resolve("website.d/unrelated/app.js") shouldNot exist_path
        }
      }

    }

    "resolve publication build settings" which {
      "apply publication config and CLI precedence" in {
        _with_temp_dir("cozy-bok-publication-options") { dir =>
          Given(
            "publication defaults in config plus overriding CLI publication and warehouse options"
          )
          _write(
            dir.resolve(".cozy/config.yaml"),
            """bok:
              |  publication: configured-publication
              |  rdf:
              |    merge-publication-artifacts: true
              |    missing-artifact-policy: warn
              |""".stripMargin
          )
          _write(
            dir.resolve("src/main/doxsite/site.conf"),
            "site { output { locale_mode = \"single_locale_root\" } }\n"
          )
          val config = CozyBok.BuildConfig.create(
            List(
              dir.toString,
              "--strategy",
              "production",
              "--publication",
              dir.resolve("cli-publication").toString,
              "--warehouse",
              dir.resolve("cli-warehouse").toString,
              "--rdf-missing-artifact-policy",
              "fail"
            )
          )
          val runner = new RecordingRunner

          When("Cozy builds the BoK in production mode")
          CozyBok.build(config, runner)

          Then(
            "SmartDox receives the CLI publication, repository, and RDF missing policy values"
          )
          val sitecommand =
            runner.commands.find(_.take(2) == Vector("dox", "site")).get
          sitecommand should contain("-publication")
          sitecommand should contain(
            dir.resolve("cli-publication").toAbsolutePath.normalize().toString
          )
          sitecommand should contain("-publication.repository")
          sitecommand should contain(
            dir
              .resolve("cli-warehouse/repository")
              .toAbsolutePath
              .normalize()
              .toString
          )
          sitecommand should contain("-publication.rdf.missing.policy")
          sitecommand should contain("fail")
        }
      }

      "use a publication repository link root when bok.repository is a separate directory" in {
        _with_temp_dir("cozy-bok-publication-repository-root") { dir =>
          Given(
            "a BoK config whose public repository root is not named repository"
          )
          _write(
            dir.resolve("conf/cozy/config.yaml"),
            """bok:
              |  repository: public-repository
              |  rdf:
              |    merge-publication-artifacts: true
              |""".stripMargin
          )
          _write(
            dir.resolve("src/main/doxsite/site.conf"),
            "site { output { locale_mode = \"single_locale_root\" } }\n"
          )
          Files.createDirectories(dir.resolve("public-repository"))
          val config = CozyBok.BuildConfig.create(
            List(dir.toString, "--strategy", "preview")
          )
          val runner = new RecordingRunner

          When("Cozy builds the BoK")
          CozyBok.build(config, runner)

          Then(
            "SmartDox receives the configured physical repository root without a generated symlink"
          )
          val sitecommand =
            runner.commands.find(_.take(2) == Vector("dox", "site")).get
          sitecommand should contain("-publication.repository")
          sitecommand should contain(
            dir.resolve("public-repository").toAbsolutePath.normalize().toString
          )
          Files.exists(
            dir.resolve("target/cozy-bok/publication-repository-root")
          ) shouldBe false
        }
      }

      "derive the publication repository link root from bok.warehouse configuration" in {
        _with_temp_dir("cozy-bok-publication-warehouse-root") { dir =>
          Given(
            "a BoK config whose warehouse is the parent of the public repository"
          )
          _write(
            dir.resolve("conf/cozy/config.yaml"),
            """bok:
              |  warehouse: public-warehouse
              |  rdf:
              |    merge-publication-artifacts: true
              |""".stripMargin
          )
          _write(
            dir.resolve("src/main/doxsite/site.conf"),
            "site { output { locale_mode = \"single_locale_root\" } }\n"
          )
          Files.createDirectories(dir.resolve("public-warehouse/repository"))
          val config = CozyBok.BuildConfig.create(
            List(dir.toString, "--strategy", "preview")
          )
          val runner = new RecordingRunner

          When("Cozy builds the BoK")
          CozyBok.build(config, runner)

          Then(
            "SmartDox receives the repository root under the configured warehouse"
          )
          val sitecommand =
            runner.commands.find(_.take(2) == Vector("dox", "site")).get
          sitecommand should contain("-publication.repository")
          sitecommand should contain(
            dir
              .resolve("public-warehouse/repository")
              .toAbsolutePath
              .normalize()
              .toString
          )
        }
      }

      "use production RDF missing artifact failure by default" in {
        _with_temp_dir("cozy-bok-production-rdf-policy") { dir =>
          Given(
            "a production BoK build without an explicit RDF missing artifact policy"
          )
          val config = CozyBok.BuildConfig.create(
            List(dir.toString, "--strategy", "production")
          )
          val runner = new RecordingRunner

          When("build configuration is translated to SmartDox site arguments")
          CozyBok.build(config, runner)

          Then("missing RDF artifacts default to fail")
          val sitecommand =
            runner.commands.find(_.take(2) == Vector("dox", "site")).get
          sitecommand should contain("-publication.repository")
          sitecommand should contain(
            dir.resolve("repository").toAbsolutePath.normalize().toString
          )
          sitecommand should contain("-publication.rdf.missing.policy")
          sitecommand should contain("fail")
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
          _write(
            pkg.resolve("video.yaml"),
            """video:
          |  name: tutorial
          |title: Tutorial Video
          |version: 0.1.0
          |publish:
          |  module: textus
          |""".stripMargin
          )
          val runner = CozyVideoSpec.PublishingRunner()
          val config = CozyBok.PublicationConfig.create(
            "publish-video",
            List(dir.toString)
          )

          When("Cozy publishes video metadata for the BoK")
          val results = CozyBok.publishVideo(
            config,
            CozyVideoSpec.RecordingVoicevoxClient(),
            runner
          )

          Then(
            "publication registry entries and repository video artifacts are created outside the source package"
          )
          results.size shouldBe 1
          dir.resolve("src/main/publication/tutorial.json") should be_regular_file
          dir.resolve(
            "repository/video/textus/0.1.0/tutorial-0.1.0.mp4"
          ) should be_regular_file
          val sourcefiles = Files
            .walk(pkg)
            .iterator()
            .asScala
            .toVector
            .filter(Files.isRegularFile(_))
            .map(_.getFileName.toString)
          sourcefiles should not_contain_where[String](_.endsWith(".mp4"))
          sourcefiles should not_contain_where[String](_.endsWith(".ttl"))
          sourcefiles should not_contain_where[String](_.endsWith(".jsonld"))
          sourcefiles should not_contain_where[String](_.endsWith(".srt"))
        }
      }

      "honor bok video enabled false" in {
        _with_temp_dir("cozy-bok-publish-video-disabled") { dir =>
          Given(
            "a BoK source tree with a .video package and video publication disabled"
          )
          val pkg = dir.resolve("src/main/doxsite/concepts/tutorial.video")
          _write(pkg.resolve("index.dox"), "# Tutorial\n")
          _write(pkg.resolve("script.json"), _video_script_json)
          _write(
            pkg.resolve("video.yaml"),
            """video:
          |  name: tutorial
          |title: Tutorial Video
          |version: 0.1.0
          |publish:
          |  module: textus
          |""".stripMargin
          )
          _write(
            dir.resolve(".cozy/config.yaml"),
            """bok:
          |  video:
          |    enabled: false
          |""".stripMargin
          )
          val config = CozyBok.PublicationConfig.create(
            "publish-video",
            List(dir.toString)
          )

          When("Cozy publishes video metadata")
          val results = CozyBok.publishVideo(
            config,
            CozyVideoSpec.RecordingVoicevoxClient(),
            CozyVideoSpec.PublishingRunner()
          )

          Then(
            "no video publication registry or repository artifacts are written"
          )
          results shouldBe empty
          dir.resolve("src/main/publication/tutorial.json") shouldNot exist_path
          dir.resolve(
            "repository/video/textus/0.1.0/tutorial-0.1.0.mp4"
          ) shouldNot exist_path
        }
      }

      "reject .video.d work directories" in {
        _with_temp_dir("cozy-bok-publish-video-workdir") { dir =>
          Given("a BoK source tree containing a .video.d work directory")
          Files.createDirectories(
            dir.resolve("src/main/doxsite/concepts/bad.video.d")
          )
          val config = CozyBok.PublicationConfig.create(
            "publish-video",
            List(dir.toString)
          )

          When("Cozy discovers video packages")
          val e = intercept[Throwable] {
            CozyBok.publishVideo(
              config,
              CozyVideoSpec.RecordingVoicevoxClient(),
              CozyVideoSpec.PublishingRunner()
            )
          }

          Then("the generated/work directory naming is rejected")
          e.getMessage should include("*.video.d is reserved")
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
          _write(
            pkg.resolve("video.yaml"),
            """video:
          |  name: tutorial
          |title: Tutorial Video
          |version: 0.1.0
          |publish:
          |  module: textus
          |""".stripMargin
          )
          _write(
            dir.resolve("src/main/doxsite/site.conf"),
            "site { output { locale_mode = \"single_locale_root\" } }\n"
          )
          val config = CozyBok.PublicationConfig.create(
            "publish",
            List(dir.toString, "--strategy", "production")
          )
          val runner = new RecordingRunner

          When("Cozy runs publish preflight")
          val e = intercept[Throwable] {
            CozyBok.publish(
              config,
              runner,
              CozyVideoSpec.RecordingVoicevoxClient(),
              CozyVideoSpec.PublishingRunner()
            )
          }

          Then(
            "publication update, build, upload, and artifact writes are not started"
          )
          e.getMessage should include("Missing bok workflow command")
          runner.commands shouldBe empty
          dir.resolve("src/main/publication/tutorial.json") shouldNot exist_path
          dir.resolve(
            "repository/video/textus/0.1.0/tutorial-0.1.0.mp4"
          ) shouldNot exist_path
        }
      }

      "run update publication then build then configured upload" in {
        _with_temp_dir("cozy-bok-publish-flow") { dir =>
          Given("a BoK project with a configured upload workflow")
          _write(
            dir.resolve(".cozy/config.yaml"),
            """bok:
          |  docker-image: antora-image
          |  workflow:
          |    upload:
          |      command: "etc/upload.sh"
          |      env:
          |        AWS_S3_URI: s3://publish.example/
          |        AWS_S3_SYNC_DELETE: "false"
          |""".stripMargin
          )
          _write(
            dir.resolve("src/main/doxsite/site.conf"),
            "site { output { locale_mode = \"single_locale_root\" } }\n"
          )
          val config = CozyBok.PublicationConfig.create(
            "publish",
            List(dir.toString, "--strategy", "production")
          )
          val runner = new EnvRecordingRunner

          When("Cozy runs one-stop publish")
          CozyBok.publish(
            config,
            runner,
            CozyVideoSpec.RecordingVoicevoxClient(),
            CozyVideoSpec.PublishingRunner()
          )

          Then(
            "publication update, site build, optional stage skip, and upload execute in deterministic order"
          )
          runner.commands should contain_where[Vector[String]](
            _.take(2) == Vector("dox", "antora")
          )
          runner.commands should contain_where[Vector[String]](
            _.take(2) == Vector("dox", "site")
          )
          runner.commands.last shouldBe Vector("sh", "-c", "etc/upload.sh")

          And("the publish upload step receives workflow environment settings")
          val uploadenv = runner.envcalls
            .collectFirst {
              case (Vector("sh", "-c", "etc/upload.sh"), _, env) => env
            }
            .getOrElse(Map.empty)
          uploadenv should contain("AWS_S3_URI" -> "s3://publish.example/")
          uploadenv should contain("AWS_S3_SYNC_DELETE" -> "false")
          uploadenv should contain("REPOSITORY_SOURCE_DIR" -> "repository")
        }
      }

      "print planned steps in dry-run without publication build or upload side effects" in {
        _with_temp_dir("cozy-bok-publish-dry-run") { dir =>
          Given(
            "a BoK project with video packages and upload workflow configuration"
          )
          val pkg = dir.resolve("src/main/doxsite/concepts/tutorial.video")
          _write(pkg.resolve("index.dox"), "# Tutorial\n")
          _write(pkg.resolve("script.json"), _video_script_json)
          _write(
            pkg.resolve("video.yaml"),
            """video:
          |  name: tutorial
          |title: Tutorial Video
          |version: 0.1.0
          |publish:
          |  module: textus
          |""".stripMargin
          )
          _write(
            dir.resolve(".cozy/config.yaml"),
            """bok:
          |  workflow:
          |    upload:
          |      command: "etc/upload.sh"
          |""".stripMargin
          )
          _write(
            dir.resolve("src/main/doxsite/site.conf"),
            "site { output { locale_mode = \"single_locale_root\" } }\n"
          )
          val config = CozyBok.PublicationConfig.create(
            "publish",
            List(dir.toString, "--dry-run", "--strategy", "production")
          )
          val runner = new RecordingRunner

          When("Cozy runs one-stop publish in dry-run mode")
          val out = _capture {
            CozyBok.publish(
              config,
              runner,
              CozyVideoSpec.RecordingVoicevoxClient(),
              CozyVideoSpec.PublishingRunner()
            )
          }

          Then(
            "planned steps and manifest are written without publication, repository, site, or upload side effects"
          )
          out should include("bok publish dry-run")
          out should include("update-publication")
          out should include("build")
          out should include("upload")
          runner.commands shouldBe empty
          dir.resolve("src/main/publication") shouldNot exist_path
          dir.resolve("repository") shouldNot exist_path
          dir.resolve("warehouse") shouldNot exist_path
          dir.resolve("website.d") shouldNot exist_path
          dir.resolve("doxsite.d") shouldNot exist_path
          val manifest = _manifest(dir)
          manifest.hcursor
            .downField("dryRun")
            .as[Boolean]
            .fold(throw _, identity) shouldBe true
          manifest.hcursor
            .downField("steps")
            .downArray
            .downField("name")
            .as[String]
            .fold(throw _, identity) shouldBe "preflight"
          _read(
            dir.resolve("target/cozy-bok/publish/latest/manifest.json")
          ) should include("\"planned\"")
        }
      }

      "accept external publication and warehouse paths" in {
        _with_temp_dir("cozy-bok-publish-external-project") { dir =>
          _with_temp_dir("cozy-bok-publish-external-output") { external =>
            Given(
              "publication and warehouse paths outside the BoK project directory"
            )
            _write(
              dir.resolve("src/main/doxsite/site.conf"),
              "site { output { locale_mode = \"single_locale_root\" } }\n"
            )
            _write(
              dir.resolve(".cozy/config.yaml"),
              """bok:
            |  video:
            |    enabled: false
            |  workflow:
            |    upload:
            |      command: "etc/upload.sh"
            |""".stripMargin
            )
            val publication = external.resolve("publication")
            val warehouse = external.resolve("warehouse")
            val config = CozyBok.PublicationConfig.create(
              "publish",
              List(
                dir.toString,
                "--dry-run",
                "--publication",
                publication.toString,
                "--warehouse",
                warehouse.toString
              )
            )
            val runner = new RecordingRunner

            When("Cozy plans a publish dry-run")
            _capture {
              CozyBok.publish(
                config,
                runner,
                CozyVideoSpec.RecordingVoicevoxClient(),
                CozyVideoSpec.PublishingRunner()
              )
            }

            Then(
              "the external paths are accepted and the repository is resolved under the warehouse"
            )
            val manifest =
              _read(dir.resolve("target/cozy-bok/publish/latest/manifest.json"))
            manifest should include(
              publication.toAbsolutePath.normalize().toString
            )
            manifest should include(
              warehouse.toAbsolutePath.normalize().toString
            )
            manifest should include(
              warehouse
                .resolve("repository")
                .toAbsolutePath
                .normalize()
                .toString
            )
            runner.commands shouldBe empty
          }
        }
      }

      "accept external publication and direct repository paths" in {
        _with_temp_dir("cozy-bok-publish-external-repository-project") { dir =>
          _with_temp_dir("cozy-bok-publish-external-repository-output") {
            external =>
              Given(
                "publication and repository paths outside the BoK project directory"
              )
              _write(
                dir.resolve("src/main/doxsite/site.conf"),
                "site { output { locale_mode = \"single_locale_root\" } }\n"
              )
              _write(
                dir.resolve(".cozy/config.yaml"),
                """bok:
            |  video:
            |    enabled: false
            |  workflow:
            |    upload:
            |      command: "etc/upload.sh"
            |""".stripMargin
              )
              val publication = external.resolve("publication")
              val repository = external.resolve("repository-root")
              val config = CozyBok.PublicationConfig.create(
                "publish",
                List(
                  dir.toString,
                  "--dry-run",
                  "--publication",
                  publication.toString,
                  "--repository",
                  repository.toString
                )
              )
              val runner = new RecordingRunner

              When("Cozy plans a publish dry-run")
              _capture {
                CozyBok.publish(
                  config,
                  runner,
                  CozyVideoSpec.RecordingVoicevoxClient(),
                  CozyVideoSpec.PublishingRunner()
                )
              }

              Then(
                "the direct repository path is accepted and recorded in the operation manifest"
              )
              val manifest = _read(
                dir.resolve("target/cozy-bok/publish/latest/manifest.json")
              )
              manifest should include(
                publication.toAbsolutePath.normalize().toString
              )
              manifest should include(
                repository.toAbsolutePath.normalize().toString
              )
              manifest should not include (repository
                .resolve("repository")
                .toAbsolutePath
                .normalize()
                .toString)
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
            CozyBok.PublicationConfig.create(
              "publish-video",
              List(dir.toString, "--dry-run")
            )
          }
          val e2 = intercept[Throwable] {
            CozyBok.PublicationConfig.create(
              "update-publication",
              List(dir.toString, "--dry-run")
            )
          }

          Then("dry-run is rejected outside the one-stop publish command")
          e1.getMessage should include("only supported by bok publish")
          e2.getMessage should include("only supported by bok publish")
        }
      }

      "reject source and publication path overlap before side effects" in {
        _with_temp_dir("cozy-bok-publish-preflight-path") { dir =>
          Given(
            "a publication registry path that overlaps the BoK source directory"
          )
          _write(
            dir.resolve("src/main/doxsite/site.conf"),
            "site { output { locale_mode = \"single_locale_root\" } }\n"
          )
          _write(
            dir.resolve(".cozy/config.yaml"),
            """bok:
          |  video:
          |    enabled: false
          |  workflow:
          |    upload:
          |      command: "etc/upload.sh"
          |""".stripMargin
          )
          val config = CozyBok.PublicationConfig.create(
            "publish",
            List(
              dir.toString,
              "--publication",
              dir.resolve("src/main/doxsite/publication").toString
            )
          )
          val runner = new RecordingRunner

          When("Cozy runs publish preflight")
          val e = intercept[Throwable] {
            CozyBok.publish(
              config,
              runner,
              CozyVideoSpec.RecordingVoicevoxClient(),
              CozyVideoSpec.PublishingRunner()
            )
          }

          Then(
            "the overlap is rejected before manifest or command side effects"
          )
          e.getMessage should include("overlaps BoK source")
          runner.commands shouldBe empty
          dir.resolve(
            "target/cozy-bok/publish/latest/manifest.json"
          ) shouldNot exist_path
        }
      }

    }

    "record publish failures" which {
      "record build failure and prevent upload" in {
        _with_temp_dir("cozy-bok-publish-build-failure") { dir =>
          Given("a configured one-stop publish whose site build fails")
          _write(
            dir.resolve("src/main/doxsite/site.conf"),
            "site { output { locale_mode = \"single_locale_root\" } }\n"
          )
          _write(
            dir.resolve(".cozy/config.yaml"),
            """bok:
          |  video:
          |    enabled: false
          |  workflow:
          |    upload:
          |      command: "etc/upload.sh"
          |""".stripMargin
          )
          val config = CozyBok.PublicationConfig.create(
            "publish",
            List(dir.toString, "--strategy", "production")
          )
          val runner = new FailingBuildRunner

          When("Cozy executes the publish flow")
          val e = intercept[RuntimeException] {
            CozyBok.publish(
              config,
              runner,
              CozyVideoSpec.RecordingVoicevoxClient(),
              CozyVideoSpec.PublishingRunner()
            )
          }

          Then("the build failure is recorded and upload is skipped")
          e.getMessage should include("build boom")
          runner.commands should not contain (Vector(
            "sh",
            "-c",
            "etc/upload.sh"
          ))
          val manifest =
            _read(dir.resolve("target/cozy-bok/publish/latest/manifest.json"))
          manifest should include("\"name\" : \"update-publication\"")
          manifest should include("\"status\" : \"skipped\"")
          manifest should include("\"name\" : \"build\"")
          manifest should include("\"status\" : \"failed\"")
          manifest should include("build boom")
        }
      }

      "record upload failure distinctly after build" in {
        _with_temp_dir("cozy-bok-publish-upload-failure") { dir =>
          Given(
            "a configured one-stop publish whose upload command fails after a successful build"
          )
          _write(
            dir.resolve("src/main/doxsite/site.conf"),
            "site { output { locale_mode = \"single_locale_root\" } }\n"
          )
          _write(
            dir.resolve(".cozy/config.yaml"),
            """bok:
          |  video:
          |    enabled: false
          |  workflow:
          |    upload:
          |      command: "etc/upload.sh"
          |""".stripMargin
          )
          val config = CozyBok.PublicationConfig.create(
            "publish",
            List(dir.toString, "--strategy", "production")
          )
          val runner = new FailingUploadRunner

          When("Cozy executes the publish flow")
          val e = intercept[RuntimeException] {
            CozyBok.publish(
              config,
              runner,
              CozyVideoSpec.RecordingVoicevoxClient(),
              CozyVideoSpec.PublishingRunner()
            )
          }

          Then("the upload failure is recorded as an upload step failure")
          e.getMessage should include("upload boom")
          runner.commands should contain_where[Vector[String]](
            _.take(2) == Vector("dox", "antora")
          )
          runner.commands should contain(Vector("sh", "-c", "etc/upload.sh"))
          val manifest =
            _read(dir.resolve("target/cozy-bok/publish/latest/manifest.json"))
          manifest should include("\"name\" : \"upload\"")
          manifest should include("\"status\" : \"failed\"")
          manifest should include("upload boom")
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

        Then(
          "BoK publication command surfaces and dry-run support are documented"
        )
        help should include("version, --version")
        help should include("--vision <text>")
        help should include("--goal <text>")
        help should include("--subgoal <text>")
        help should include(
          "bok publish-video <project-dir> [--publication <dir>] [--repository <dir>] [--warehouse <dir>]"
        )
        help should include(
          "bok update-publication <project-dir> [--publication <dir>] [--repository <dir>] [--warehouse <dir>]"
        )
        help should include(
          "bok publish <project-dir> [--publication <dir>] [--repository <dir>] [--warehouse <dir>]"
        )
        help should include("bok doctor [<project-dir>] [--fix] [--dry-run]")
        help should include("bok fix [<project-dir>] [--dry-run]")
        help should include("bok guide [scenario]")
        help should include("--no-bib-service")
        help should include("Open http://127.0.0.1:<port>/")
        help should include("--dry-run")
      }

      "show scenario-based BoK operation guide" in {
        Given("a user who wants to learn BoK operations by scenario")

        When("Cozy renders the BoK guide index")
        val overview = _capture {
          CozyBok.execute(List("bok", "guide"))
        }

        Then("the guide lists operational scenarios")
        overview should include("Cozy BoK guide")
        overview should include("create-bok")
        overview should include("daily-build")
        overview should include("publish-dry-run")

        When("Cozy renders a concrete scenario")
        val scenario = _capture {
          CozyBok.execute(List("bok", "guide", "daily-build"))
        }

        Then("the scenario describes the command sequence")
        scenario should include("Cozy BoK guide: daily-build")
        scenario should include("cozy bok doctor")
        scenario should include("Vision, Goals, and Subgoals")
        scenario should include("cozy bok build --strategy preview")
        scenario should include("--no-bib-service")
        scenario should include("cozy bok preview --port 8980")
        scenario should include("http://127.0.0.1:8980/")
      }

      "show preview usage without starting a server" in {
        Given("a user asks for preview command help")
        val runner = new RecordingRunner

        When("Cozy renders preview usage")
        val output = _capture {
          CozyBok.preview(List("--help"), runner)
        }

        Then("usage is shown and no HTTP server command is executed")
        output should include(
          "Usage: cozy bok preview [<project-dir>] [--port <port>]"
        )
        runner.commands shouldBe empty
      }

      "show publication and workflow usage without starting command execution" in {
        Given("a user asks for BoK publication and workflow command help")

        When("Cozy renders the command-specific help text")
        val publish = _capture {
          CozyBok.execute(List("bok", "publish", "--help"))
        }
        val publishvideo = _capture {
          CozyBok.execute(List("bok", "publish-video", "--help"))
        }
        val update = _capture {
          CozyBok.execute(List("bok", "update-publication", "--help"))
        }
        val searchbibliography = _capture {
          CozyBok.execute(List("bok", "search-bibliography", "--help"))
        }
        val updatebibliography = _capture {
          CozyBok.execute(List("bok", "update-bibliography", "--help"))
        }
        val stage = _capture {
          CozyBok.execute(List("bok", "stage", "--help"))
        }
        val upload = _capture {
          CozyBok.execute(List("bok", "upload", "--help"))
        }

        Then(
          "usage is shown before argument parsing, preflight, or workflow execution"
        )
        publish should include("Usage: cozy bok publish <project-dir>")
        publish should include("--dry-run")
        publishvideo should include(
          "Usage: cozy bok publish-video <project-dir>"
        )
        update should include(
          "Usage: cozy bok update-publication <project-dir>"
        )
        searchbibliography should include("Usage: cozy bok search-bibliography <query>")
        updatebibliography should include("Usage: cozy bok update-bibliography [<project-dir>]")
        updatebibliography should include("--report-only")
        updatebibliography should include("--no-fetch")
        stage should include("Usage: cozy bok stage [<project-dir>]")
        stage should include("bok.workflow.stage.command")
        upload should include("Usage: cozy bok upload [<project-dir>]")
        upload should include("bok.workflow.upload.command")
        upload should include("bok.backup.enabled")
        upload should include("website.backup")
        publish should not include ("Missing bok workflow command")
      }

      "validate preview port as integer metadata" in {
        _with_temp_dir("cozy-bok-preview-port") { dir =>
          Given("a BoK preview command with a non-integer port")
          When("the preview command metadata is parsed")
          val e = intercept[Throwable] {
            CozyBok.preview(
              List(dir.toString, "--port", "not-int"),
              new RecordingRunner
            )
          }
          Then("the invalid port is rejected explicitly")
          e.getMessage should include("not-int")
        }
      }

      "announce local Web server preview URL" in {
        _with_temp_dir("cozy-bok-preview-url") { dir =>
          Given("a BoK project with generated website output")
          _write(
            dir.resolve(".cozy/config.yaml"),
            "bok:\n  website: website.d\n"
          )
          _write(dir.resolve("website.d/index.html"), "<html></html>\n")
          val runner = new RecordingRunner

          When("Cozy starts the preview server")
          val output = _capture {
            CozyBok.preview(List(dir.toString, "--port", "8099"), runner)
          }

          Then(
            "the local browser URL is shown instead of implying file-based inspection"
          )
          output should include("http://127.0.0.1:8099/")
          output should include(
            "instead of opening generated HTML files directly"
          )
          runner.commands should contain(
            Vector("python3", "-m", "http.server", "8099")
          )
        }
      }
    }

    "run configured workflows" which {
      "require registered workflow command for explicit stage execution" in {
        _with_temp_dir("cozy-bok-workflow-missing") { dir =>
          Given("a BoK stage command without a registered shell command")
          When("Cozy resolves the explicit stage workflow")
          val e = intercept[Throwable] {
            CozyBok.runWorkflow(
              CozyBok.WorkflowConfig.create("stage", List(dir.toString)),
              new RecordingRunner
            )
          }
          Then(
            "the missing stage workflow command is reported as configuration error"
          )
          e.getMessage should include("bok.workflow.stage.command")
        }
      }

      "run only registered workflow command for stage and upload" in {
        _with_temp_dir("cozy-bok-workflow") { dir =>
          Given("registered stage and upload workflow commands")
          _write(
            dir.resolve(".cozy/config.yaml"),
            """bok:
              |  workflow:
              |    stage:
              |      command: "etc/website-stage.sh"
              |    upload:
              |      command: "etc/website-upload.sh"
              |""".stripMargin
          )
          val runner = new RecordingRunner

          When("Cozy runs the workflows")
          CozyBok.runWorkflow(
            CozyBok.WorkflowConfig.create("stage", List(dir.toString)),
            runner
          )
          CozyBok.runWorkflow(
            CozyBok.WorkflowConfig.create("upload", List(dir.toString)),
            runner
          )

          Then("only the configured external workflow commands are executed")
          runner.commands shouldBe Vector(
            Vector("sh", "-c", "etc/website-stage.sh"),
            Vector("sh", "-c", "etc/website-upload.sh")
          )
        }
      }

      "pass workflow environment settings with local overrides" in {
        _with_temp_dir("cozy-bok-workflow-env") { dir =>
          Given(
            "a public upload workflow environment and a local sensitive override"
          )
          _write(
            dir.resolve("conf/cozy/config.yaml"),
            """bok:
              |  workflow:
              |    upload:
              |      command: "etc/website-upload.sh"
              |      env:
              |        WEBSITE_SOURCE_DIR: website.d
              |        AWS_S3_URI: s3://public.example/
              |        AWS_S3_SYNC_DELETE: "true"
              |""".stripMargin
          )
          _write(
            dir.resolve(".cozy/config.yaml"),
            """bok:
              |  workflow:
              |    upload:
              |      env:
              |        AWS_S3_URI: s3://private.example/
              |        AWS_CLOUDFRONT_DISTRIBUTION_ID: SECRET123
              |""".stripMargin
          )
          val runner = new EnvRecordingRunner

          When("Cozy runs the upload workflow")
          CozyBok.runWorkflow(
            CozyBok.WorkflowConfig.create("upload", List(dir.toString)),
            runner
          )

          Then(
            "the configured workflow command receives merged environment variables"
          )
          runner.commands shouldBe Vector(
            Vector("sh", "-c", "etc/website-upload.sh")
          )
          runner.envs should have size 1
          runner.envs.head should contain("WEBSITE_SOURCE_DIR" -> "website.d")
          runner.envs.head should contain(
            "REPOSITORY_SOURCE_DIR" -> "repository"
          )
          runner.envs.head should contain("AWS_S3_SYNC_DELETE" -> "true")

          And("the local sensitive config overrides public environment values")
          runner.envs.head should contain(
            "AWS_S3_URI" -> "s3://private.example/"
          )
          runner.envs.head should contain(
            "AWS_CLOUDFRONT_DISTRIBUTION_ID" -> "SECRET123"
          )
        }
      }

      "derive workflow repository source from bok.warehouse configuration" in {
        _with_temp_dir("cozy-bok-workflow-warehouse-env") { dir =>
          Given(
            "a workflow command and a warehouse parent configured in public BoK config"
          )
          _write(
            dir.resolve("conf/cozy/config.yaml"),
            """bok:
              |  warehouse: public-warehouse
              |  workflow:
              |    upload:
              |      command: "etc/website-upload.sh"
              |""".stripMargin
          )
          val runner = new EnvRecordingRunner

          When("Cozy runs the upload workflow")
          CozyBok.runWorkflow(
            CozyBok.WorkflowConfig.create("upload", List(dir.toString)),
            runner
          )

          Then(
            "the script receives the public repository under the configured warehouse"
          )
          runner.envs should have size 1
          runner.envs.head should contain(
            "REPOSITORY_SOURCE_DIR" -> "public-warehouse/repository"
          )
        }
      }

      "keep upload backup disabled by default" in {
        _with_temp_dir("cozy-bok-upload-backup-default") { dir =>
          Given("an upload workflow without an explicit backup setting")
          _write(
            dir.resolve("conf/cozy/config.yaml"),
            """bok:
              |  workflow:
              |    upload:
              |      command: "etc/website-upload.sh"
              |""".stripMargin
          )
          _write(dir.resolve("website.d/index.html"), "<html>site</html>\n")
          val runner = new RecordingRunner

          When("Cozy runs the upload workflow")
          CozyBok.runWorkflow(
            CozyBok.WorkflowConfig.create("upload", List(dir.toString)),
            runner
          )

          Then("the configured upload command runs")
          runner.commands should contain(Vector("sh", "-c", "etc/website-upload.sh"))

          And("no website backup directory is created by default")
          dir.resolve("website.backup") shouldNot exist_path
        }
      }

      "create compressed website backup before upload when enabled" in {
        _with_temp_dir("cozy-bok-upload-backup-compressed") { dir =>
          Given("an upload workflow with compressed website backup enabled")
          _write(
            dir.resolve("conf/cozy/config.yaml"),
            """bok:
              |  backup:
              |    enabled: true
              |  workflow:
              |    upload:
              |      command: "etc/website-upload.sh"
              |""".stripMargin
          )
          _write(dir.resolve("website.d/index.html"), "<html>published</html>\n")
          val runner = new RecordingRunner

          When("Cozy runs the upload workflow")
          CozyBok.runWorkflow(
            CozyBok.WorkflowConfig.create("upload", List(dir.toString)),
            runner
          )

          Then("the generated website is backed up as a compressed snapshot")
          val zips = _files_under(dir.resolve("website.backup")).filter(_.getFileName.toString == "website.d.zip")
          zips should have size 1
          _zip_text(zips.head, "website.d/index.html") should include("published")

          And("the upload workflow still runs after the backup")
          runner.commands should contain(Vector("sh", "-c", "etc/website-upload.sh"))
        }
      }

      "reject backup directories inside the website source before upload" in {
        _with_temp_dir("cozy-bok-upload-backup-nested-root") { dir =>
          Given("an upload workflow whose backup directory is nested under the website source")
          _write(
            dir.resolve("conf/cozy/config.yaml"),
            """bok:
              |  backup:
              |    enabled: true
              |    dir: website.d/backup
              |  workflow:
              |    upload:
              |      command: "etc/website-upload.sh"
              |""".stripMargin
          )
          _write(dir.resolve("website.d/index.html"), "<html>published</html>\n")
          val runner = new RecordingRunner

          When("Cozy runs the upload workflow")
          val error = intercept[RuntimeException] {
            CozyBok.runWorkflow(
              CozyBok.WorkflowConfig.create("upload", List(dir.toString)),
              runner
            )
          }

          Then("the unsafe backup configuration is rejected before backup or upload side effects")
          error.getMessage should include("Website backup directory must be outside the website source directory")
          dir.resolve("website.d/backup") shouldNot exist_path
          runner.commands shouldBe empty
        }
      }

      "rotate website backups by keeping the first upload, last completed-month backup, and every current-month backup" in {
        _with_temp_dir("cozy-bok-upload-backup-rotation") { dir =>
          Given("an upload workflow with existing first, completed-month, and current-month backups")
          _write(
            dir.resolve("conf/cozy/config.yaml"),
            """bok:
              |  backup:
              |    enabled: true
              |    compressed: false
              |  workflow:
              |    upload:
              |      command: "etc/website-upload.sh"
              |""".stripMargin
          )
          _write(dir.resolve("website.d/index.html"), "<html>new</html>\n")
          val root = dir.resolve("website.backup")
          val current = YearMonth.now()
          val firstmonth = current.minusMonths(3)
          val previous = current.minusMonths(1)
          val first = _backup_snapshot(root, firstmonth, "01", "010000")
          val firstmonthlast = _backup_snapshot(root, firstmonth, "02", "020000")
          val previousold = _backup_snapshot(root, previous, "01", "010000")
          val previouslast = _backup_snapshot(root, previous, "02", "020000")
          val currentold = _backup_snapshot(root, current, "01", "010000")
          Vector(first, firstmonthlast, previousold, previouslast, currentold).foreach { path =>
            _write(path.resolve("website.d/index.html"), path.getFileName.toString)
          }

          When("Cozy runs the upload workflow and applies backup rotation")
          CozyBok.runWorkflow(
            CozyBok.WorkflowConfig.create("upload", List(dir.toString)),
            new RecordingRunner
          )

          Then("the first backup is preserved for operation history")
          first should exist_path

          And("only the last backup from completed months is preserved")
          firstmonthlast should exist_path
          previousold shouldNot exist_path
          previouslast should exist_path

          And("every current-month backup is preserved")
          currentold should exist_path
          _files_under(root).filter(_.endsWith("website.d/index.html")).map(_read) should contain("<html>new</html>\n")
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
        _write_smartdox_metadata(cwd)
    }
  }

  private class EnvRecordingRunner extends RecordingRunner {
    var envcalls = Vector.empty[(Vector[String], Path, Map[String, String])]
    def envs: Vector[Map[String, String]] = envcalls.map(_._3)
    override def run(
        command: Vector[String],
        cwd: Path,
        env: Map[String, String]
    ): Unit = {
      envcalls = envcalls :+ (command, cwd, env)
      super.run(command, cwd)
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
        _write_smartdox_metadata(cwd)
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
        _write(
          cwd.resolve("doxsite.d/metadata/dashboard/site.json"),
          _legacy_dashboard_json
        )
    }
  }

  private class HistoryYearRunner extends RecordingRunner {
    override def run(command: Vector[String], cwd: Path): Unit = {
      super.run(command, cwd)
      _write(
        cwd.resolve("website.d/history/2026.html"),
        "smartdox-generated-history-year\n"
      )
    }
  }

  private def _write_smartdox_metadata(cwd: Path): Unit = {
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
      |      "rdf": {
      |        "resource_count": 2,
      |        "triple_count": 7,
      |        "subject_count": 2,
      |        "predicate_count": 3
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

  private def _rdf_graph_json: String =
    """{
      |  "informationView": {
      |    "name": "test-rdf-1.5-hop-information-view",
      |    "label": "Test Information View",
      |    "concept": "1.5+hop",
      |    "attributes": ["information.schema", "information.identity", "information.description", "schema.expansion"],
      |    "informationSchemas": [
      |      {
      |        "name": "architecture-information-v1",
      |        "label": "Architecture Information",
      |        "match": {"categories": ["architecture"]},
      |        "requiredPredicates": ["rdf:type"],
      |        "descriptivePredicates": ["https://schema.org/description"],
      |        "outgoingRequiredPredicates": ["https://schema.org/about"],
      |        "incomingRequiredPredicates": ["https://schema.org/mentions"]
      |      }
      |    ],
      |    "predicateProfile": {
      |      "name": "test-rdf-1.5-hop-profile",
      |      "roles": {
      |        "identity": ["textus:primaryRdfAnchor"],
      |        "link": ["rdfs:seeAlso"],
      |        "provenance": ["prov:wasDerivedFrom"]
      |      }
      |    }
      |  },
      |  "nodes": [
      |    {"id": "https://www.simplemodeling.org/architecture/overview", "label": "overview", "node_type": "uri", "category": "architecture", "degree": 1, "requiredPredicates": ["https://schema.org/name"], "schema": {"descriptivePredicates": ["https://schema.org/description"], "outgoingRequiredPredicates": ["https://schema.org/about"], "incomingRequiredPredicates": ["https://schema.org/mentions"]}},
      |    {"id": "https://schema.org/name", "label": "name", "node_type": "uri", "category": null, "degree": 1}
      |  ],
      |  "edges": [
      |    {"source": "https://www.simplemodeling.org/architecture/overview", "target": "https://schema.org/name", "predicate": "https://schema.org/name", "label": "name", "category": "architecture"}
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
      |      "definition_html": "<p>Runtime term.</p>",
      |      "summary": "Runtime term.",
      |      "aliases": [],
      |      "article_refs": [],
      |      "term_refs": [],
      |      "rdf_refs": [],
      |      "video_refs": [],
      |      "quality": {"isolated": false, "unreferenced": false, "weakly_connected": false}
      |    },
      |    {
      |      "id": "architecture:asuka",
      |      "slug": "asuka",
      |      "title": "あすか",
      |      "reading": "あすか",
      |      "category": "architecture",
      |      "source_path": "glossary/architecture/asuka.dox",
      |      "public_path": "glossary/architecture/asuka.html",
      |      "definition_html": "<p>Japanese term.</p>",
      |      "summary": "Japanese term.",
      |      "aliases": [],
      |      "article_refs": [],
      |      "term_refs": [],
      |      "rdf_refs": [],
      |      "video_refs": [],
      |      "quality": {"isolated": false, "unreferenced": false, "weakly_connected": false}
      |    },
      |    {
      |      "id": "architecture:cloud",
      |      "slug": "cloud",
      |      "title": "Cloud",
      |      "reading": null,
      |      "category": "architecture",
      |      "source_path": "glossary/architecture/cloud.dox",
      |      "public_path": "glossary/architecture/cloud.html",
      |      "definition_html": "<p>English-only term.</p>",
      |      "summary": "English-only term.",
      |      "aliases": [],
      |      "article_refs": [],
      |      "term_refs": [],
      |      "rdf_refs": [],
      |      "video_refs": [],
      |      "quality": {"isolated": false, "unreferenced": false, "weakly_connected": false}
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

  private def _backup_snapshot(root: Path, yearmonth: YearMonth, day: String, time: String): Path =
    root.
      resolve(f"${yearmonth.getYear}%04d").
      resolve(f"${yearmonth.getMonthValue}%02d").
      resolve(day).
      resolve(time)

  private def _files_under(path: Path): Vector[Path] =
    if (!Files.exists(path))
      Vector.empty
    else {
      val stream = Files.walk(path)
      try {
        stream.iterator.asScala.toVector.filter(Files.isRegularFile(_)).sortBy(_.toString)
      } finally {
        stream.close()
      }
    }

  private def _manifest(dir: Path): io.circe.Json =
    parser
      .parse(_read(dir.resolve("target/cozy-bok/publish/latest/manifest.json")))
      .fold(throw _, identity)

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
      Iterator
        .continually(in.getNextEntry)
        .takeWhile(_ != null)
        .find(_.getName == name) match {
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
