package cozy

import cozy.bok.CozyBok
import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path, Paths}
import java.util.zip.{ZipEntry, ZipInputStream, ZipOutputStream}
import scala.collection.JavaConverters._
import org.scalatest.GivenWhenThen
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Jun. 21, 2026
 * @version Jun. 24, 2026
 * @author  ASAMI, Tomoharu
 */
class CozyBokDashboardSpec
    extends AnyWordSpec
    with GivenWhenThen
    with CozySpecVocabulary {
  "Cozy BoK dashboard rendering" should {
    "use SmartDox source narratives" which {
      "render Home and Category index.dox as narrative sections" in {
        _with_temp_dir("cozy-bok-dashboard-narrative") { dir =>
          Given(
            "a BoK source tree with Home and Category narrative index.dox files"
          )
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
            dir.resolve("src/main/doxsite/index.dox"),
            """Home
              |======
              |
              |# HEAD
              |
              |## HEADLINE
              |
              |Home Source Headline
              |
              |## BRIEF
              |
              |Home narrative brief.
              |
              |# Overview
              |
              |Home narrative source text.
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
              |# HEAD
              |
              |## HEADLINE
              |
              |Architecture Source Headline
              |
              |## BRIEF
              |
              |Architecture narrative brief.
              |
              |# Overview
              |
              |Architecture narrative source text.
              |
              |## Focus
              |
              |Make architecture decisions reviewable.
              |""".stripMargin
          )
          val config = CozyBok.BuildConfig.create(
            List(dir.toString, "--strategy", "preview")
          )
          val runner = new DashboardRunner

          When("Cozy builds dashboard pages")
          CozyBok.build(config, runner)

          Then(
            "the Home dashboard includes the SmartDox-rendered narrative section"
          )
          val home = _read(dir.resolve("website.d/index.html"))
          home should include("""href="_/css/bootstrap-grid.min.css"""")
          home should include("""href="_/css/site.css"""")
          home should include("""href="_/css/cozy-bok-dashboard.css"""")
          _read(
            dir.resolve("website.d/architecture/index.html")
          ) should include("""href="../_/css/bootstrap-grid.min.css"""")
          _read(
            dir.resolve("website.d/architecture/index.html")
          ) should include("""href="../_/css/site.css"""")
          _read(
            dir.resolve("website.d/architecture/index.html")
          ) should include("""href="../_/css/cozy-bok-dashboard.css"""")
          home should include(
            """class="bok-dashboard container-fluid bok-dashboard-command-center""""
          )
          home should include(
            """data-bok-dashboard="true" data-bok-default-actor="reader" data-bok-default-card-mode="hide""""
          )
          home should include(
            """data-bok-actor-filter="all" aria-pressed="false""""
          )
          home should include(
            """data-bok-actor-filter="reader" aria-pressed="true""""
          )
          home should include(
            """data-bok-actor-filter="contributor" aria-pressed="false""""
          )
          home should include(
            """data-bok-actor-filter="project_manager" aria-pressed="false""""
          )
          home should include(
            """data-bok-actor-filter="site_administrator" aria-pressed="false""""
          )
          home should include(
            """data-bok-actor-mode="hide" aria-pressed="true""""
          )
          home should include(
            """data-bok-actor-mode="dim" aria-pressed="false""""
          )
          home should include("""data-bok-status-all=""")
          home should include("""data-bok-status-filtered="{2}:""")
          home should include("""data-bok-status-dimmed="{2}:""")
          home should include("""data-bok-actor-status="true"""")
          home should include("""data-bok-card="true"""")
          home should include("""URLSearchParams(window.location.search)""")
          home should include("""function defaultActor(root)""")
          home should include("""actor === "all"""")
          home should include("""actor === "site_administrator"""")
          home should include("""var validModes = ["hide", "dim"]""")
          home should include("""params.get("display") || "hide"""")
          home should include(
            """root.setAttribute("data-bok-card-mode", mode)"""
          )
          home should include(
            """wrapper.classList.toggle("is-bok-filter-hidden", hide)"""
          )
          home should include(
            """wrapper.classList.toggle("is-bok-filter-dimmed", dim)"""
          )
          home should include("""bok-card-actor-chips""")
          home should include("""ensureActorChips(root)""")
          home should include(
            """format(root.getAttribute("data-bok-status-filtered")"""
          )
          home should include("""class="bok-dashboard-shell" id="dashboard"""")
          home should include("""class="bok-dashboard-hero"""")
          home should include("""class="bok-dashboard-hero-facts"""")
          home should include("""class="body body-dashboard"""")
          home should not include ("""class="nav-container"""")
          home should not include ("""class="toc sidebar"""")
          home should include("""class="row g-3"""")
          home should not include ("""class="card bok-card bok-card-purpose"""")
          home should include("""class="card bok-card bok-card-kpi"""")
          home should include("""class="card bok-card bok-card-chart"""")
          home should include(
            """class="card bok-card bok-card-activity bok-card-notification""""
          )
          home should include("""class="bok-notification-summary"""")
          home should include("Home Source Headline")
          home should include("Home narrative brief.")
          home should include("""id="narrative"""")
          home should not include ("""<h2>Narrative</h2>""")
          home should not include ("""<a href="#narrative">Narrative</a>""")
          home should include("Home narrative source text.")
          home should include(
            """class="bok-category-rdf-link" href="rdf/index.html?category=architecture"><b>7</b>RDF</a>"""
          )
          home should include(
            """class="bok-category-rdf-value"><b>0</b>RDF</span>"""
          )
          home should not include ("""href="rdf/index.html?category=concept"><b>0</b>RDF</a>""")
          home should not include ("""class="bok-category-rdf-link" href="rdf/index.html"><b>0</b>RDF</a>""")
          home should not include ("""bok-card-knowledge-entry""")
          home should include(
            """class="bok-kpi-link" href="glossary/index.html""""
          )
          home should include("""href="rdf/index.html"""")
          home should include("""href="history/index.html"""")
          home should include(
            """href="architecture/index.html">Architecture</a>"""
          )
          home should include(
            """data-bok-actors="contributor project_manager""""
          )
          home should include(
            """data-bok-actors="project_manager contributor""""
          )
          home should include(
            """data-bok-actors="site_administrator project_manager""""
          )
          home should include(
            """data-bok-actors="reader contributor project_manager""""
          )
          home should not include ("Category Portfolio")
          val dashboardstart = home.indexOf(
            """class="bok-dashboard container-fluid bok-dashboard-command-center""""
          )
          val narrativestart = home.indexOf("""id="narrative"""")
          val notificationstart = home.indexOf("""bok-card-notification""")
          val matrixstart = home.indexOf("""bok-card-matrix""")
          val readinessstart = home.indexOf("""bok-card-readiness""")
          dashboardstart should be < narrativestart
          notificationstart should be < matrixstart
          matrixstart should be < readinessstart
          home.substring(
            dashboardstart,
            narrativestart
          ) should not include ("Home narrative source text.")
          home should not include ("""<html><head>""")
          home should not include ("""application/ld+json""")
          And(
            "the Category dashboard includes the SmartDox-rendered narrative section"
          )
          val category = _read(dir.resolve("website.d/architecture/index.html"))
          category should include("""class="body body-dashboard"""")
          category should include(
            """class="bok-dashboard-shell" id="dashboard""""
          )
          category should include("""class="bok-dashboard-hero"""")
          category should include("Architecture Source Headline")
          category should include("Architecture narrative brief.")
          category should include(
            """class="card bok-card bok-card-map" data-bok-actors="reader contributor project_manager""""
          )
          category should include(
            """data-bok-actors="site_administrator project_manager""""
          )
          category should include(
            """href="../rdf/index.html?category=architecture""""
          )
          category should not include ("""class="card bok-card bok-card-purpose"""")
          category should not include ("""class="nav-container"""")
          category should not include ("""class="toc sidebar"""")
          category should include("""id="narrative"""")
          category should not include ("""<h2>Narrative</h2>""")
          category should not include ("""<a href="#narrative">Narrative</a>""")
          category should include("Architecture narrative source text.")
          category should include("Make architecture decisions reviewable.")
        }
      }

      "render Markdown source documents through SmartDox Dox metadata" in {
        _with_temp_dir("cozy-bok-dashboard-markdown") { dir =>
          Given(
            "a BoK source tree whose Home, Category, and article sources are GitHub Markdown"
          )
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
            dir.resolve("src/main/doxsite/index.md"),
            """---
              |title: Markdown Home
              |headline: Markdown Home Headline
              |brief: Markdown home brief.
              |status: work-in-progress
              |---
              |
              |# Overview
              |
              |Markdown home source text with **bold** knowledge and [a reference](https://example.com).
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
            dir.resolve("src/main/doxsite/architecture/index.md"),
            """# HEAD
              |
              |status=work-in-progress
              |
              |## HEADLINE
              |
              |Architecture Markdown Headline
              |
              |## BRIEF
              |
              |Architecture markdown brief.
              |
              |# Overview
              |
              |Architecture markdown narrative.
              |""".stripMargin
          )
          _write(
            dir.resolve("src/main/doxsite/architecture/guide.md"),
            """---
              |headline: Architecture Guide
              |brief: Guide from Markdown front matter.
              |---
              |
              |# Architecture Guide
              |
              |- first point
              |- second point
              |""".stripMargin
          )

          When("Cozy builds dashboard pages")
          CozyBok.build(
            CozyBok.BuildConfig.create(
              List(dir.toString, "--strategy", "preview")
            ),
            new DashboardRunner
          )

          Then(
            "the Home dashboard uses Markdown front matter through Dox metadata"
          )
          val home = _read(dir.resolve("website.d/index.html"))
          home should include("Markdown Home Headline")
          home should include("Markdown home brief.")
          home should include("Markdown home source text")
          home should include("bold")
          home should include("https://example.com")

          And("the Category dashboard and article map accept Markdown sources")
          val category = _read(dir.resolve("website.d/architecture/index.html"))
          category should include("Architecture Markdown Headline")
          category should include("Architecture markdown brief.")
          category should include("Architecture markdown narrative.")
          category should include("""href="guide.html"""")
          category should include("Architecture Guide")
          category should include("Guide from Markdown front matter.")
        }
      }

      "keep scaffolded Category index.dox free of generated dashboard fragments" in {
        _with_temp_dir("cozy-bok-dashboard-scaffold") { dir =>
          Given("an existing BoK source scaffold")
          CozyBok.create(
            CozyBok.CreateConfig.create(
              List("--save", dir.toString, "--name", "KnowledgeHub BoK")
            )
          )

          When("Cozy creates a category scaffold")
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
                "--article",
                "knowledgehub-overview:KnowledgeHub Overview:Overview article.",
                "--term",
                "knowledgehub:KnowledgeHub:KnowledgeHub term."
              )
            )
          )

          Then(
            "the Category index remains source narrative rather than generated dashboard output"
          )
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
          ) should not include ("""class="bok-metric-card"""")
          _read(
            dir.resolve("src/main/doxsite/knowledgehub/index.dox")
          ) should not include ("""class="bok-dashboard-chart"""")
          _read(
            dir.resolve("src/main/doxsite/knowledgehub/index.dox")
          ) should not include ("- Articles: 1")
          _read(
            dir.resolve("src/main/doxsite/knowledgehub/index.dox")
          ) should not include ("- Terms: 1")
        }
      }

      "apply selectable color groups and right-aligned Category dropdown styling" in {
        _with_temp_dir("cozy-bok-dashboard-theme") { dir =>
          Given(
            "a BoK source tree with a dashboard color group in site metadata"
          )
          _write(
            dir.resolve("src/main/doxsite/site.conf"),
            """site {
              |  metadata {
              |    dashboard_color_group = "ocean"
              |    vision = "Make KnowledgeHub useful from the first screen."
              |    goals = [
              |      "Help readers find terms",
              |      "Help contributors improve weak links"
              |    ]
              |    subgoals = [
              |      "Show RDF graph navigation",
              |      "Keep operational cards compact"
              |    ]
              |  }
              |  output {
              |    locale_mode = "single_locale_root"
              |  }
              |}
              |""".stripMargin
          )
          _write(
            dir.resolve("src/main/doxsite/index.dox"),
            """Home
              |======
              |
              |# Overview
              |
              |Home narrative.
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
              |
              |Architecture narrative.
              |""".stripMargin
          )
          _write_zip(
            dir.resolve("src/main/antora-ui/build/ui-bundle.zip"),
            Vector(
              "css/cozy-bok-dashboard.css" -> "old dashboard css",
              "css/bootstrap-grid.min.css" -> "old bootstrap grid"
            )
          )
          val config = CozyBok.BuildConfig.create(
            List(dir.toString, "--strategy", "preview")
          )

          When("Cozy builds the dashboard pages")
          CozyBok.build(config, new DashboardRunner)

          Then("the configured color group is applied to dashboard pages")
          config.dashboardColorGroup shouldBe "ocean"
          val home = _read(dir.resolve("website.d/index.html"))
          home should include(
            """<body class="article bok-dashboard-theme-ocean">"""
          )
          home should include("""<h3 class="card-title">BoK Vision</h3>""")
          home should include("Make KnowledgeHub useful from the first screen.")
          home.indexOf("""bok-card-notification""") should be < home.indexOf(
            """bok-card-purpose"""
          )
          home.indexOf("""bok-card-purpose""") should be < home.indexOf(
            """bok-card-matrix"""
          )
          home.indexOf("""bok-card-purpose""") should be < home.indexOf(
            """bok-card-readiness"""
          )
          _read(
            dir.resolve("website.d/architecture/index.html")
          ) should include(
            """<body class="article bok-dashboard-theme-ocean">"""
          )

          And(
            "the generated CSS provides selectable groups and keeps the Category dropdown inside the viewport"
          )
          val css = _read(
            Paths.get(
              "src/main/resources/cozy/antora-ui/css/cozy-bok-dashboard.css"
            )
          )
          css should include("body.bok-dashboard-theme-aurora")
          css should include("body.bok-dashboard-theme-lagoon")
          css should include("body.bok-dashboard-theme-meadow")
          css should include("body.bok-dashboard-theme-ocean")
          css should include("body.bok-dashboard-theme-ember")
          css should include("body.bok-dashboard-theme-slate")
          css should include(
            "Card accents: keep the surface border stable and draw an outer highlight ring"
          )
          css should include("--bok-card-accent")
          css should include("--bok-card-ring")
          css should include("0 0 0 4px var(--bok-card-ring)")
          css should include("0 0 0 9px var(--bok-card-ring-glow)")
          css should include("transform: translateY(-5px) scale(1.006)")
          css should include(".bok-card::before")
          css should include("display: none")
          css should include(".bok-dashboard-actor-mode-button")
          css should include("[data-bok-card].is-bok-filter-hidden")
          css should include("display: none !important")
          css should include("[data-bok-card].is-bok-filter-dimmed")
          css should include("filter: grayscale(.82) saturate(.4)")
          css should include(
            """.bok-dashboard[data-bok-current-actor="site_administrator"] .bok-card-actor-chips"""
          )
          css should include(
            ".navbar-category-dropdown > .navbar-category-menu"
          )
          css should include(".navbar-bok-dropdown > .navbar-bok-menu")
          css should include(".navbar-bok-dropdown:hover > .navbar-bok-menu")
          css should include("background: #0f172a")
          css should include("color: #e0f2fe !important")
          css should include(".navbar-dropdown-item:hover")
          css should include("left: auto")
          css should include("right: 0")
          css should include("max-width: min(22rem, calc(100vw - 1rem))")
          css should not include ("right:auto;left:0")
          css should include("Contrast guard")
          css should include(".body-dashboard .bok-card:not(.bok-card-purpose)")
          css should include("color: #0f172a !important;")
          css should include(".body-dashboard .bok-card-purpose")
          css should include("color: #f8fafc !important;")
          css should include(".bok-rdf-workspace")
          css should include(".bok-rdf-view-switch")
          css should include(".bok-rdf-panel")
          css should include(".bok-rdf-panels")
          css should include(".bok-rdf-graph-canvas")
          css should include(".bok-rdf-graph-svg")
          css should include(".bok-rdf-graph-edge")
          css should include(".bok-rdf-graph-node")
          css should include(".bok-rdf-node-popover")
          css should include(
            ".bok-rdf-node-popover dd.bok-rdf-node-compact-label"
          )
          css should include(".bok-rdf-node-popover dd.bok-rdf-node-full-iri")
          css should include("overflow-wrap: anywhere")
          css should include(".bok-rdf-node-popover-actions")
          css should include(".bok-rdf-node-popover-actions-primary")
          css should include(".body-dashboard .bok-narrative-corner")
          css should include(".body-dashboard .doc > .sect1")
          css should include(".bok-rdf-node-schema")
          css should include(".bok-rdf-node-schema-groups")
          css should include(".bok-rdf-node-schema-group")
          css should include(".bok-rdf-node-schema-object")
          css should include(".bok-rdf-node-schema-object code")
          css should include(".bok-rdf-graph-node-role-focus")
          css should include(".bok-rdf-graph-node-role-schema")
          css should include(".bok-rdf-triples-view")
          _zip_text(
            dir.resolve("antora.d/ui-bundle.zip"),
            "css/cozy-bok-dashboard.css"
          ) should include("body.bok-dashboard-theme-lagoon")
          _zip_text(
            dir.resolve("antora.d/ui-bundle.zip"),
            "css/cozy-bok-dashboard.css"
          ) should not include ("old dashboard css")
          _zip_text(
            dir.resolve("antora.d/ui-bundle.zip"),
            "css/bootstrap-grid.min.css"
          ) should include(".container-fluid")
          _zip_text(
            dir.resolve("antora.d/ui-bundle.zip"),
            "css/bootstrap-grid.min.css"
          ) should not include ("old bootstrap grid")

          And(
            "the command line can override site metadata and invalid values fall back to the default"
          )
          CozyBok.BuildConfig
            .create(
              List(
                dir.toString,
                "--strategy",
                "preview",
                "--dashboard-color-group",
                "ember"
              )
            )
            .dashboardColorGroup shouldBe "ember"
          CozyBok.BuildConfig
            .create(
              List(
                dir.toString,
                "--strategy",
                "preview",
                "--dashboard-color-group",
                "lagoon"
              )
            )
            .dashboardColorGroup shouldBe "lagoon"
          CozyBok.BuildConfig
            .create(
              List(
                dir.toString,
                "--strategy",
                "preview",
                "--dashboard-color-group",
                "meadow"
              )
            )
            .dashboardColorGroup shouldBe "meadow"
          CozyBok.BuildConfig
            .create(
              List(
                dir.toString,
                "--strategy",
                "preview",
                "--dashboard-color-group",
                "unknown"
              )
            )
            .dashboardColorGroup shouldBe "aurora"
        }
      }

      "filter single index.dox narrative by configured default locale" in {
        _with_temp_dir("cozy-bok-dashboard-default-locale") { dir =>
          Given(
            "a single-locale BoK whose index.dox carries language-specific spans"
          )
          _write(
            dir.resolve("src/main/doxsite/site.conf"),
            """site {
              |  metadata {
              |    in_language = ["ja", "en"]
              |  }
              |  output {
              |    locale_mode = "single_locale_root"
              |    default_locale = "en"
              |  }
              |}
              |""".stripMargin
          )
          _write(
            dir.resolve("src/main/doxsite/index.dox"),
            """Home
              |======
              |
              |# Overview
              |
              |<span lang="ja">日本語ホーム本文</span><span lang="en">English home narrative</span>
              |""".stripMargin
          )
          val config = CozyBok.BuildConfig.create(
            List(dir.toString, "--strategy", "preview")
          )

          When("Cozy builds the single-locale dashboard")
          CozyBok.build(config, new RecordingRunner)

          Then(
            "the configured default locale selects the English narrative from the same index.dox"
          )
          config.defaultLocale shouldBe "en"
          _read(dir.resolve("website.d/index.html")) should include(
            """<html lang="en">"""
          )
          _read(dir.resolve("website.d/index.html")) should include(
            "English home narrative"
          )
          _read(
            dir.resolve("website.d/index.html")
          ) should not include ("日本語ホーム本文")
        }
      }

      "filter one multilingual index.dox into each locale subdirectory" in {
        _with_temp_dir("cozy-bok-dashboard-multilingual") { dir =>
          Given(
            "a multi-locale BoK whose Home and Category index.dox files contain language-marked content"
          )
          _write(
            dir.resolve("src/main/doxsite/site.conf"),
            """site {
              |  metadata {
              |    in_language = ["ja", "en"]
              |  }
              |  output {
              |    locale_mode = "multi_locale_subdirs"
              |    default_locale = "ja"
              |  }
              |}
              |""".stripMargin
          )
          _write(
            dir.resolve("src/main/doxsite/index.dox"),
            """Home
              |======
              |
              |# Overview
              |
              |<span lang="ja">日本語ホーム本文</span><span lang="en">English home narrative</span>
              |""".stripMargin
          )
          _write(
            dir.resolve("src/main/doxsite/concept/category.yaml"),
            """name: Concept
              |title: Concept
              |description: Concept category.
              |""".stripMargin
          )
          _write(
            dir.resolve("src/main/doxsite/concept/index.dox"),
            """Concept
              |=======
              |
              |# Overview
              |
              |<span lang="ja">日本語カテゴリ本文</span><span lang="en">English category narrative</span>
              |""".stripMargin
          )
          val config = CozyBok.BuildConfig.create(
            List(dir.toString, "--strategy", "preview")
          )

          When("Cozy builds the multi-locale dashboard pages")
          CozyBok.build(config, new RecordingRunner)

          Then(
            "each locale output is generated from the same source index.dox with locale filtering"
          )
          config.localeMode shouldBe CozyBok.LocaleMode.MultiLocaleSubdirs
          config.defaultLocale shouldBe "ja"
          _read(dir.resolve("website.d/ja/index.html")) should include(
            """<html lang="ja">"""
          )
          _read(dir.resolve("website.d/en/index.html")) should include(
            """<html lang="en">"""
          )
          _read(dir.resolve("website.d/ja/index.html")) should include(
            """href="../_/css/bootstrap-grid.min.css""""
          )
          _read(dir.resolve("website.d/ja/index.html")) should include(
            """href="../_/css/site.css""""
          )
          _read(dir.resolve("website.d/ja/index.html")) should include(
            """href="../_/css/cozy-bok-dashboard.css""""
          )
          _read(dir.resolve("website.d/en/index.html")) should include(
            """href="../_/css/bootstrap-grid.min.css""""
          )
          _read(dir.resolve("website.d/en/index.html")) should include(
            """href="../_/css/site.css""""
          )
          _read(dir.resolve("website.d/en/index.html")) should include(
            """href="../_/css/cozy-bok-dashboard.css""""
          )
          _read(dir.resolve("website.d/ja/concept/index.html")) should include(
            """href="../../_/css/bootstrap-grid.min.css""""
          )
          _read(dir.resolve("website.d/ja/concept/index.html")) should include(
            """href="../../_/css/site.css""""
          )
          _read(dir.resolve("website.d/ja/concept/index.html")) should include(
            """href="../../_/css/cozy-bok-dashboard.css""""
          )
          _read(dir.resolve("website.d/en/concept/index.html")) should include(
            """href="../../_/css/bootstrap-grid.min.css""""
          )
          _read(dir.resolve("website.d/en/concept/index.html")) should include(
            """href="../../_/css/site.css""""
          )
          _read(dir.resolve("website.d/en/glossary/index.html")) should include(
            """href="../../_/css/site.css""""
          )
          _read(dir.resolve("website.d/ja/index.html")) should include(
            "日本語ホーム本文"
          )
          _read(
            dir.resolve("website.d/ja/index.html")
          ) should not include ("English home narrative")
          _read(dir.resolve("website.d/en/index.html")) should include(
            "English home narrative"
          )
          _read(
            dir.resolve("website.d/en/index.html")
          ) should not include ("日本語ホーム本文")
          _read(
            dir.resolve("website.d/en/index.html")
          ) should not include ("This dashboard aggregates the whole BoK status")
          _read(
            dir.resolve("website.d/en/index.html")
          ) should not include ("BoK全体の状態")
          _read(dir.resolve("website.d/ja/concept/index.html")) should include(
            "日本語カテゴリ本文"
          )
          _read(dir.resolve("website.d/en/concept/index.html")) should include(
            "English category narrative"
          )
          _read(
            dir.resolve("website.d/en/concept/index.html")
          ) should not include ("This dashboard aggregates this category")
          _read(
            dir.resolve("website.d/en/concept/index.html")
          ) should not include ("このカテゴリの目的")
          _read(dir.resolve("website.d/en/glossary/index.html")) should include(
            "Dashboard for terms and vocabulary shared across the BoK."
          )
          _read(
            dir.resolve("website.d/en/glossary/index.html")
          ) should not include ("BoK全体で共有する用語")
          _read(dir.resolve("website.d/en/manual/index.html")) should include(
            "Manual pages are excluded from automatic glossary linking."
          )
          _read(
            dir.resolve("website.d/en/manual/index.html")
          ) should not include ("Manualは自動用語リンク対象外")
        }
      }
    }
  }

  private class RecordingRunner extends CozyBok.Runner {
    var calls = Vector.empty[(Vector[String], Path)]
    def run(command: Vector[String], cwd: Path): Unit =
      calls = calls :+ (command -> cwd)
  }

  private class DashboardRunner extends RecordingRunner {
    override def run(command: Vector[String], cwd: Path): Unit = {
      super.run(command, cwd)
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
      |    "triple_count": 8,
      |    "subject_count": 4,
      |    "predicate_count": 3
      |  },
      |  "increments": {
      |    "scale": "day",
      |    "buckets": [
      |      {"label": "2026-06-21", "start_date": "2026-06-21", "end_date": "2026-06-21", "count": 2, "article_count": 1, "glossary_term_count": 1}
      |    ]
      |  },
      |  "categories": [
      |    {
      |      "name": "architecture",
      |      "title": "Architecture",
      |      "counts": {
      |        "category_count": 0,
      |        "article_count": 1,
      |        "glossary_term_count": 1,
      |        "total_item_count": 2
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
      |          {"label": "2026-06-21", "start_date": "2026-06-21", "end_date": "2026-06-21", "count": 2, "article_count": 1, "glossary_term_count": 1}
      |        ]
      |      }
      |    },
      |    {
      |      "name": "concept",
      |      "title": "Concept",
      |      "counts": {
      |        "category_count": 0,
      |        "article_count": 1,
      |        "glossary_term_count": 1,
      |        "total_item_count": 2
      |      },
      |      "rdf": {
      |        "resource_count": 0,
      |        "triple_count": 0,
      |        "subject_count": 0,
      |        "predicate_count": 0
      |      },
      |      "increments": {
      |        "scale": "day",
      |        "buckets": [
      |          {"label": "2026-06-21", "start_date": "2026-06-21", "end_date": "2026-06-21", "count": 2, "article_count": 1, "glossary_term_count": 1}
      |        ]
      |      }
      |    }
      |  ]
      |}
      |""".stripMargin

  private def _rdf_graph_json: String =
    """{
      |  "nodes": [
      |    {"id": "https://www.simplemodeling.org/architecture/overview", "label": "overview", "node_type": "uri", "category": "architecture", "degree": 1},
      |    {"id": "https://schema.org/name", "label": "name", "node_type": "uri", "category": null, "degree": 1}
      |  ],
      |  "edges": [
      |    {"source": "https://www.simplemodeling.org/architecture/overview", "target": "https://schema.org/name", "predicate": "https://schema.org/name", "label": "name", "category": "architecture"}
      |  ],
      |  "truncated": false
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

  private def _write_zip(
      path: Path,
      entries: Vector[(String, String)]
  ): Path = {
    Option(path.getParent).foreach(Files.createDirectories(_))
    val out = new ZipOutputStream(Files.newOutputStream(path))
    try {
      entries.foreach { case (name, content) =>
        out.putNextEntry(new ZipEntry(name))
        out.write(content.getBytes(StandardCharsets.UTF_8))
        out.closeEntry()
      }
    } finally {
      out.close()
    }
    path
  }

  private def _zip_text(path: Path, name: String): String = {
    val in = new ZipInputStream(Files.newInputStream(path))
    try {
      var entry = in.getNextEntry
      while (entry != null) {
        if (entry.getName == name)
          return new String(in.readAllBytes(), StandardCharsets.UTF_8)
        in.closeEntry()
        entry = in.getNextEntry
      }
      fail(s"Zip entry not found: $name")
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
