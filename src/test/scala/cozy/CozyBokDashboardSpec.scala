package cozy

import cozy.bok.CozyBok
import java.time.Instant
import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path, Paths}
import java.nio.file.attribute.FileTime
import java.util.zip.{ZipEntry, ZipInputStream, ZipOutputStream}
import scala.collection.JavaConverters._
import org.scalatest.GivenWhenThen
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Jun. 21, 2026
 *  version Jun. 29, 2026
 * @version Jul.  1, 2026
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
              |vision: "Keep architecture knowledge compact and navigable."
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
          _write(
            dir.resolve("src/main/doxsite/architecture/overview.dox"),
            """Architecture Overview
              |=====================
              |
              |# HEAD
              |
              |## HEADLINE
              |
              |Architecture Overview
              |
              |## BRIEF
              |
              |Overview article.
              |
              |# Overview
              |
              |Architecture overview source text.
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
            """<select class="bok-dashboard-actor-select" data-bok-actor-filter"""
          )
          home should include("""<option value="reader" selected>""")
          home should include("""<option value="site_administrator">""")
          home should include(
            """<select class="bok-dashboard-actor-select" data-bok-actor-mode"""
          )
          home should include("""<option value="hide" selected>""")
          home should include("""<option value="dim">""")
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
            """control.tagName === "SELECT" ? "change" : "click"""
          )
          home should include("""control.value = actor""")
          home should include("""control.value = mode""")
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
          home should include("""class="card bok-card bok-card-kpi bok-card-kpi-link"""")
          home should include("""class="card bok-card bok-card-matrix" id="categories"""")
          home should include("""href="category/index.html"""")
          home should include("""class="card bok-card bok-card-analysis-entry"""")
          home should include("""class="bok-analysis-entry-flow"""")
          home should include("""class="bok-analysis-entry-analysis"""")
          home should include("""class="bok-analysis-entry-relation"""")
          home should include("""class="bok-analysis-entry-arrow-column bok-analysis-entry-input-arrows"""")
          home should include("""class="bok-analysis-entry-column bok-analysis-entry-inputs"""")
          home should include("""class="bok-analysis-entry-center"""")
          home should include("""bok-analysis-entry-mono-koto-process""")
          home should include("""class="bok-analysis-entry-output-relation"""")
          home should include("""class="bok-analysis-entry-arrow-column bok-analysis-entry-output-arrows"""")
          home should include("""class="bok-analysis-entry-column bok-analysis-entry-outputs"""")
          home should include("""href="articles/index.html"""")
          home should include("""href="glossary/index.html"""")
          home should include("""href="scenarios/index.html"""")
          home should include("""href="projects/index.html"""")
          home should include("""bok-analysis-entry-article""")
          home should include("""bok-analysis-entry-project""")
          home should include("""bok-analysis-entry-rdf""")
          home should include("""href="rdf/index.html"""")
          home should include("""class="card bok-card bok-card-chart"""")
          home should include(
            """class="card bok-card bok-card-activity bok-card-notification""""
          )
          home should not include ("""class="bok-notification-summary"""")
          home should include ("""class="card-title bok-card-title-with-action"""")
          home should include ("""class="bok-card-title-link" href="history/index.html"""")
          home should include(
            """class="list-group bok-activity-list bok-activity-list-with-category""""
          )
          home should include("""class="bok-activity-kind"""")
          home should include(
            """<div class="col-12 col-xl-4" data-bok-card="true">
  <section class="card bok-card bok-card-quality""""
          )
          home should include(
            """<div class="col-12 col-md-6 col-xl-6" data-bok-card="true">
  <section class="card bok-card bok-card-readiness""""
          )
          home should include(
            """<div class="col-12 col-md-6 col-xl-6" data-bok-card="true">
  <section class="card bok-card bok-card-actions""""
          )
          home should include("Home Source Headline")
          home should include("Home narrative brief.")
          val articles = _read(dir.resolve("website.d/articles/index.html"))
          articles should include("""class="bok-dashboard-shell bok-article-dashboard"""")
          articles should include("""class="bok-article-grid"""")
          articles should include("""data-article-category="architecture"""")
          articles should include("""href="../architecture/overview.html"""")
          articles should not include ("""<h3><a href="../architecture/index.html">Architecture</a></h3>""")
          articles should include("""new URLSearchParams(window.location.search).get('category')""")
          val categoryindex = _read(dir.resolve("website.d/category/index.html"))
          categoryindex should include("""class="bok-dashboard-shell bok-category-index-dashboard"""")
          categoryindex should include("""href="../architecture/index.html"""")
          categoryindex should include("""href="../rdf/index.html?category=architecture"""")
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
          home.indexOf(
            """href="architecture/index.html">Architecture</a>"""
          ) should be < home.indexOf(
            """href="concept/index.html">Concept</a>"""
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
          val analysisstart = home.indexOf("""bok-card-analysis-entry""")
          val matrixstart = home.indexOf("""bok-card-matrix""")
          val readinessstart = home.indexOf("""bok-card-readiness""")
          dashboardstart should be < narrativestart
          notificationstart should be < matrixstart
          notificationstart should be < analysisstart
          analysisstart should be < matrixstart
          matrixstart should be < readinessstart
          val notification = home.substring(notificationstart, matrixstart)
          notification should include ("""class="bok-activity-kind"""")
          notification should include ("""class="bok-activity-category">Architecture</span>""")
          notification should include ("""href="architecture/overview.html">Architecture Overview</a>""")
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
          category should include("""class="card bok-card bok-card-analysis-entry"""")
          category should include("""href="../articles/index.html?category=architecture"""")
          category should include("""href="../glossary/architecture/index.html"""")
          category should include("""href="../scenarios/index.html?category=architecture"""")
          category should include("""href="../projects/index.html?category=architecture"""")
          category should include("""bok-analysis-entry-article""")
          category should include("""bok-analysis-entry-project""")
          category should include("""bok-analysis-entry-rdf""")
          category should include("""href="../rdf/index.html?category=architecture"""")
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

      "use source fragment summary as Home hero brief" in {
        _with_temp_dir("cozy-bok-dashboard-summary-fragment") { dir =>
          Given(
            "a BoK source tree whose generated Home fragment has summary but no brief"
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
              |KnowledgeHub
              |""".stripMargin
          )
          val config = CozyBok.BuildConfig.create(
            List(dir.toString, "--strategy", "preview")
          )

          When("Cozy builds the Home dashboard")
          CozyBok.build(config, new SummaryFragmentRunner)

          Then(
            "the Home dashboard lead uses the fragment summary as its effective brief"
          )
          val home = _read(dir.resolve("website.d/index.html"))
          home should include("""<h1 class="page">KnowledgeHub</h1>""")
          home should include("KnowledgeHub project summary from fragment metadata.")
          home should not include ("BoK全体の状態")
        }
      }

      "use source summary when Home fragment is absent" in {
        _with_temp_dir("cozy-bok-dashboard-source-summary") { dir =>
          Given(
            "a BoK source tree with a Home summary and no generated Home document fragment"
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
              |KnowledgeHub
              |
              |## SUMMARY
              |
              |KnowledgeHub project summary from source.
              |""".stripMargin
          )
          val config = CozyBok.BuildConfig.create(
            List(dir.toString, "--strategy", "preview")
          )

          When("Cozy builds the Home dashboard")
          CozyBok.build(config, new NoDocumentFragmentRunner)

          Then("the Home dashboard lead falls back to the source summary")
          val home = _read(dir.resolve("website.d/index.html"))
          home should include("""<h1 class="page">KnowledgeHub</h1>""")
          home should include("KnowledgeHub project summary from source.")
          home should not include ("BoK全体の状態")
        }
      }

      "omit empty SmartDox document fragments from narrative sections" in {
        _with_temp_dir("cozy-bok-dashboard-empty-narrative") { dir =>
          Given("a Category source whose SmartDox fragment has no article body")
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
              |""".stripMargin
          )
          val config = CozyBok.BuildConfig.create(
            List(dir.toString, "--strategy", "preview")
          )

          When("Cozy builds dashboard pages from SmartDox document fragments")
          CozyBok.build(config, new EmptyCategoryDocumentFragmentRunner)

          Then("the Home narrative remains visible")
          val home = _read(dir.resolve("website.d/index.html"))
          home should include("""class="bok-narrative-corner" id="narrative"""")
          home should include("Home narrative source text.")

          And("the empty Category fragment does not leave a blank narrative card")
          val category = _read(dir.resolve("website.d/architecture/index.html"))
          category should not include ("""class="bok-narrative-corner" id="narrative"""")
          category should not include ("<article></article>")
        }
      }

      "fallback to source document activity when dashboard increment metadata is empty" in {
        _with_temp_dir("cozy-bok-dashboard-recent-fallback") { dir =>
          Given("a BoK source tree with recent source documents and empty dashboard increments")
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
            dir.resolve("src/main/doxsite/architecture/category.yaml"),
            """name: Architecture
              |title: Architecture
              |description: Architecture category.
              |vision: "Keep architecture changes visible."
              |""".stripMargin
          )
          val categoryindex = dir.resolve("src/main/doxsite/architecture/index.dox")
          _write(
            categoryindex,
            """Architecture
              |============
              |
              |# Overview
              |
              |Architecture category top.
              |""".stripMargin
          )
          _touch(categoryindex, "2026-06-24T00:00:00Z")
          Vector(
            "old" -> "2026-05-01T00:00:00Z",
            "sixth" -> "2026-06-18T00:00:00Z",
            "fifth" -> "2026-06-19T00:00:00Z",
            "fourth" -> "2026-06-20T00:00:00Z",
            "third" -> "2026-06-21T00:00:00Z",
            "second" -> "2026-06-22T00:00:00Z",
            "first" -> "2026-06-23T00:00:00Z"
          ).foreach {
            case (name, instant) =>
              val file = dir.resolve(s"src/main/doxsite/architecture/${name}.dox")
              _write(
                file,
                s"""Recent ${name}
                   |=============
                   |
                   |# Overview
                   |
                   |Recent ${name} body.
                   |""".stripMargin
              )
              _touch(file, instant)
          }
          val term = dir.resolve("src/main/doxsite/glossary/architecture/recent-term.dox")
          _write(
            term,
            """# HEAD
              |
              |title = "Wrong Source Heading"
              |brief = "Source term brief."
              |
              |# Overview
              |
              |Term source body.
              |""".stripMargin
          )
          _touch(term, "2026-06-24T12:00:00Z")
          val scenario = dir.resolve("src/main/doxsite/scenario/architecture/recent-scenario.dox")
          _write(
            scenario,
            """# HEAD
              |
              |title = "Recent Scenario"
              |brief = "Recent scenario summary."
              |scenario.type = "simple"
              |scenario.id = "scenario:recent"
              |
              |# Overview
              |
              |- [start] Reader: open scenario knowledge
              |""".stripMargin
          )
          _touch(scenario, "2026-06-24T00:00:00Z")
          val bibliography = dir.resolve("src/main/doxsite/bibliography/architecture/recent-reference.bib.dox")
          _write(
            bibliography,
            """Recent Reference
              |================
              |
              |# Overview
              |
              |Reference knowledge.
              |""".stripMargin
          )
          _touch(bibliography, "2026-06-25T00:00:00Z")
          val config = CozyBok.BuildConfig.create(
            List(dir.toString, "--strategy", "preview")
          )

          When("Cozy builds the dashboard")
          CozyBok.build(config, new EmptyIncrementsDashboardRunner)

          Then("the Home recent changes card shows the latest five source documents")
          val home = _read(dir.resolve("website.d/index.html"))
          val notificationstart = home.indexOf("""bok-card-notification""")
          val matrixstart = home.indexOf("""bok-card-matrix""")
          val notification = home.substring(notificationstart, matrixstart)
          notification should include ("Recent Reference")
          notification should include ("Recent Term Metadata")
          notification should include ("""glossary/architecture/recent-term.html""")
          notification should not include ("Wrong Source Heading")
          notification should include ("Recent Scenario")
          notification should include ("Recent first")
          notification should include ("Recent second")
          notification should include ("""class="bok-activity-category">Architecture</span>""")
          notification should include ("""bok-activity-list-with-category"""")
          notification should not include ("Recent third")
          notification should not include ("Recent fourth")
          notification should not include ("Recent fifth")
          notification should not include ("Recent sixth")
          notification should not include ("Recent old")

          And("the Category recent changes card includes category-local scenario and bibliography metadata")
          val category = _read(dir.resolve("website.d/architecture/index.html"))
          val categorystart = category.indexOf("""bok-card-notification""")
          val categoryend = category.indexOf("""bok-card-related""")
          val categorynotification = category.substring(categorystart, categoryend)
          categorynotification should include ("Recent Reference")
          categorynotification should include ("Recent Term Metadata")
          categorynotification should include ("""../glossary/architecture/recent-term.html""")
          categorynotification should not include ("Wrong Source Heading")
          categorynotification should include ("Recent Scenario")
          categorynotification should include ("""../bibliography/architecture/recent-reference.html""")
          categorynotification should include ("""../scenario/architecture/recent-scenario.html""")
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
            """bok-card-analysis-entry"""
          )
          home.indexOf("""bok-card-analysis-entry""") should be < home.indexOf(
            """bok-card-matrix"""
          )
          home.indexOf("""bok-card-purpose""") should be < home.indexOf(
            """bok-card-readiness"""
          )
          val category = _read(
            dir.resolve("website.d/architecture/index.html")
          )
          category should include(
            """<body class="article bok-dashboard-theme-sand">"""
          )
          category.indexOf("""bok-card-category-purpose""") should be < category.indexOf(
            """bok-card-analysis-entry"""
          )
          category.indexOf("""bok-card-analysis-entry""") should be < category.indexOf(
            """bok-card-map"""
          )
          category should not include (
            """class="card bok-card bok-card-activity bok-card-notification""""
          )

          And(
            "the generated CSS provides dashboard groups and keeps the Category dropdown inside the viewport"
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
          css should include("body.bok-dashboard-theme-sand")
          css should include("body.bok-dashboard-theme-sand .bok-dashboard-command-center")
          css should include("background: rgba(255,251,235,.70)")
          css should include("body.bok-dashboard-theme-sand .bok-dashboard-actor-filter")
          css should include("background: rgba(255,247,237,.82)")
          css should include("body.bok-dashboard-theme-sand .bok-dashboard-actor-select")
          css should include("body.bok-dashboard-theme-sand .body-dashboard .bok-card-purpose .bok-purpose-vision-copy strong")
          css should include("color: #78350f !important")
          css should include(".bok-card-purpose {\n  min-height: auto")
          css should include("height: fit-content")
          css should include(".bok-purpose-flat {\n  display: grid")
          css should include("gap: .56rem")
          css should include("padding: .62rem .72rem .66rem")
          css should include(".bok-card-category-purpose .bok-purpose-vision-panel")
          css should include(".bok-card-category-purpose .bok-purpose-tree")
          css should include(".bok-purpose-tree-label")
          css should include(".bok-card-category-purpose .bok-purpose-goal")
          css should include(".bok-card-category-purpose {\n  min-height: auto")
          css should include("height: fit-content")
          css should include("[data-bok-card]:has(.bok-card-category-purpose)")
          css should include("padding: .72rem .88rem .62rem")
          css should include("padding: .42rem .52rem .44rem")
          css should include("padding: .46rem .58rem .48rem .68rem")
          css should include("body.bok-dashboard-theme-sand .body-dashboard .bok-card-category-purpose .bok-purpose-tree")
          css should include("body.bok-dashboard-theme-sand .body-dashboard .bok-card-category-purpose .bok-purpose-goal")
          css should include("body.bok-dashboard-theme-paper")
          css should include("body.bok-dashboard-theme-paper .bok-dashboard-command-center")
          css should include("background: rgba(255,255,255,.72)")
          css should include("body.bok-dashboard-theme-paper .bok-dashboard-command-center .bok-card")
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
          css should include(".bok-dashboard-actor-select-label")
          css should include(".bok-dashboard-actor-select")
          css should include("flex-wrap: nowrap")
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
          css should include(".bok-rdf-tabbar")
          css should include(".bok-rdf-filterbar")
          css should include(".bok-rdf-view-switch")
          css should include("""button[role="tab"]""")
          css should include("""button[aria-selected="true"]""")
          css should include(".bok-rdf-panel")
          css should include(".bok-rdf-panel-title")
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
          css should include("Dashboard readability refinements")
          css should include("white-space: nowrap")
          css should include(
            "KPI cards: centered highlight numbers read better as dashboard metrics"
          )
          css should include(".body-dashboard .bok-card-kpi .card-body")
          css should include("align-items: center !important")
          css should include("text-align: center")
          css should include(".bok-card-analysis-entry")
          css should include(".bok-analysis-entry-flow")
          css should include("grid-template-columns: max-content max-content")
          css should include("width: fit-content")
          css should include("max-width: 100%")
          css should include("margin: .45rem auto 0")
          css should include(".bok-analysis-entry-analysis")
          css should include(".bok-analysis-entry-relation")
          css should include("grid-template-columns: 13.2rem 2.2rem 13.2rem")
          css should include(".bok-analysis-entry-output-relation")
          css should include("grid-template-columns: 2.2rem 13.2rem")
          css should include(".bok-analysis-entry-arrow-column")
          css should include("width: 2.2rem")
          css should include("white-space: nowrap")
          css should include("@media (max-width: 48rem)")
          css should include(".bok-analysis-entry-center")
          css should include(".bok-analysis-entry-column")
          css should include("grid-template-columns: repeat(auto-fit, minmax(13rem, 1fr))")
          css should include(".bok-analysis-entry-tile")
          css should include(".bok-analysis-entry-static")
          css should include(".bok-analysis-entry-mono-koto-process")
          css should include(".bok-analysis-entry-project")
          css should include(".bok-analysis-entry-rdf")
          css should include(
            "Dashboard hero: separate marker, title, and summary as distinct zones"
          )
          css should include(".body-dashboard .bok-dashboard-hero-copy")
          css should include("gap: 1.1rem")
          css should include("border-left: 4px solid rgba(191,231,255,.48)")
          css should include(
            "Layout corrections: use full-width separators and keep Recent Changes compact"
          )
          css should include("border-bottom: 1px solid rgba(148,163,184,.28)")
          css should include(
            "grid-template-columns: minmax(10rem, 13rem) minmax(0, 1fr) auto"
          )
          css should include(".body-dashboard .bok-card-notification .bok-activity-list")
          css should include("grid-template-columns: 6.3rem 5.1rem minmax(0, 1fr)")
          css should include(".bok-activity-list-with-category")
          css should include(".body-dashboard .bok-card-notification .bok-activity-category")
          css should include(".body-dashboard .bok-card-notification .bok-activity-kind")
          css should include(".body-dashboard .bok-card .bok-card-title-link")
          css should include(".bok-scenario-grid")
          css should include(".bok-scenario-tile")
          css should include("Header navigation should read as navigation text")
          css should include("background: transparent !important")
          css should include("border-radius: 0 !important")
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
          CozyBok.build(config, new LocaleFragmentRunner)

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
          CozyBok.build(config, new LocaleFragmentRunner)

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
          cwd.resolve("doxsite.d/metadata/documents/fragments.json"),
          _document_fragments_json
        )
        _write(
          cwd.resolve("doxsite.d/site.ttl"),
          "@prefix ex: <https://example.com/> .\n"
        )
        _write(cwd.resolve("doxsite.d/site.jsonld"), "{\"@graph\":[]}\n")
      }
    }
  }

  private class SummaryFragmentRunner extends DashboardRunner {
    override def run(command: Vector[String], cwd: Path): Unit = {
      super.run(command, cwd)
      if (command.take(2) == Vector("dox", "site"))
        _write(
          cwd.resolve("doxsite.d/metadata/documents/fragments.json"),
          _summary_document_fragments_json
        )
    }
  }

  private class NoDocumentFragmentRunner extends RecordingRunner {
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

  private class EmptyCategoryDocumentFragmentRunner extends DashboardRunner {
    override def run(command: Vector[String], cwd: Path): Unit = {
      super.run(command, cwd)
      if (command.take(2) == Vector("dox", "site"))
        _write(
          cwd.resolve("doxsite.d/metadata/documents/fragments.json"),
          _empty_category_document_fragments_json
        )
    }
  }

  private class EmptyIncrementsDashboardRunner extends RecordingRunner {
    override def run(command: Vector[String], cwd: Path): Unit = {
      super.run(command, cwd)
      if (command.take(2) == Vector("dox", "site")) {
        _write(
          cwd.resolve("doxsite.d/metadata/dashboard/site.json"),
          _empty_increments_dashboard_json
        )
        _write(
          cwd.resolve("doxsite.d/metadata/rdf/graph.json"),
          _rdf_graph_json
        )
        _write(
          cwd.resolve("doxsite.d/metadata/documents/fragments.json"),
          """{"fragments": []}"""
        )
        _write(
          cwd.resolve("doxsite.d/metadata/glossary/terms.json"),
          _recent_terms_json
        )
        _write(
          cwd.resolve("doxsite.d/metadata/bibliography/bibliography.json"),
          _recent_bibliography_json
        )
        _write(
          cwd.resolve("doxsite.d/site.ttl"),
          "@prefix ex: <https://example.com/> .\n"
        )
        _write(cwd.resolve("doxsite.d/site.jsonld"), "{\"@graph\":[]}\n")
      }
    }
  }

  private class LocaleFragmentRunner extends RecordingRunner {
    override def run(command: Vector[String], cwd: Path): Unit = {
      super.run(command, cwd)
      if (command.take(2) == Vector("dox", "site")) {
        _write(
          cwd.resolve("doxsite.d/metadata/documents/fragments.json"),
          _locale_document_fragments_json
        )
        _write(cwd.resolve("doxsite.d/site.ttl"), "@prefix ex: <https://example.com/> .\n")
        _write(cwd.resolve("doxsite.d/site.jsonld"), "{\"@graph\":[]}\n")
      }
    }
  }

  private def _document_fragments_json: String =
    """{
      |  "fragments": [
      |    {"source_path": "index.dox", "public_path": "index.html", "locale": "ja", "kind": "article", "category": null, "title": "Home", "headline": "Home Source Headline", "brief": "Home narrative brief.", "body_html": "<p>Home narrative source text.</p><p><a href=\"target.html\">site link</a> <a class=\"glossary\" href=\"glossary/architecture/runtime.html\">Runtime</a></p>"},
      |    {"source_path": "index.dox", "public_path": "index.html", "locale": "en", "kind": "article", "category": null, "title": "Home", "headline": "Home Source Headline", "brief": "Home narrative brief.", "body_html": "<p>Home narrative source text.</p><p><a href=\"target.html\">site link</a> <a class=\"glossary\" href=\"glossary/architecture/runtime.html\">Runtime</a></p>"},
      |    {"source_path": "architecture/index.dox", "public_path": "architecture/index.html", "locale": "ja", "kind": "article", "category": "architecture", "title": "Architecture", "headline": "Architecture Source Headline", "brief": "Architecture narrative brief.", "body_html": "<p>Architecture narrative source text.</p><h2>Focus</h2><p>Make architecture decisions reviewable.</p>"},
      |    {"source_path": "architecture/index.dox", "public_path": "architecture/index.html", "locale": "en", "kind": "article", "category": "architecture", "title": "Architecture", "headline": "Architecture Source Headline", "brief": "Architecture narrative brief.", "body_html": "<p>Architecture narrative source text.</p><h2>Focus</h2><p>Make architecture decisions reviewable.</p>"},
      |    {"source_path": "index.md", "public_path": "index.html", "locale": "ja", "kind": "article", "category": null, "title": "Markdown Home", "headline": "Markdown Home Headline", "brief": "Markdown home brief.", "body_html": "<p>Markdown home source text with <strong>bold</strong> knowledge and <a href=\"https://example.com\">a reference</a>.</p>"},
      |    {"source_path": "index.md", "public_path": "index.html", "locale": "en", "kind": "article", "category": null, "title": "Markdown Home", "headline": "Markdown Home Headline", "brief": "Markdown home brief.", "body_html": "<p>Markdown home source text with <strong>bold</strong> knowledge and <a href=\"https://example.com\">a reference</a>.</p>"},
      |    {"source_path": "architecture/index.md", "public_path": "architecture/index.html", "locale": "ja", "kind": "article", "category": "architecture", "title": "Architecture", "headline": "Architecture Markdown Headline", "brief": "Architecture markdown brief.", "body_html": "<p>Architecture markdown narrative.</p>"},
      |    {"source_path": "architecture/index.md", "public_path": "architecture/index.html", "locale": "en", "kind": "article", "category": "architecture", "title": "Architecture", "headline": "Architecture Markdown Headline", "brief": "Architecture markdown brief.", "body_html": "<p>Architecture markdown narrative.</p>"}
      |  ]
      |}
      |""".stripMargin

  private def _summary_document_fragments_json: String =
    """{
      |  "fragments": [
      |    {"source_path": "index.html", "public_path": "index.html", "locale": "ja", "kind": "article", "category": null, "title": "Home", "headline": "KnowledgeHub", "brief": null, "summary": "KnowledgeHub project summary from fragment metadata.", "description": "Description should not be used before summary.", "body_html": "<p>Home narrative source text.</p>"}
      |  ]
      |}
      |""".stripMargin

  private def _empty_category_document_fragments_json: String =
    """{
      |  "fragments": [
      |    {"source_path": "index.dox", "public_path": "index.html", "locale": "ja", "kind": "article", "category": null, "title": "Home", "headline": "Home", "brief": null, "body_html": "<p>Home narrative source text.</p>"},
      |    {"source_path": "architecture/index.dox", "public_path": "architecture/index.html", "locale": "ja", "kind": "article", "category": "architecture", "title": "Architecture", "headline": "Architecture", "brief": null, "body_html": "<html><head><style type=\"text/css\"><!-- h1 { color: black } --></style><script type=\"application/ld+json\">{}</script></head><body><article></article></body></html>"}
      |  ]
      |}
      |""".stripMargin

  private def _locale_document_fragments_json: String =
    """{
      |  "fragments": [
      |    {"source_path": "index.dox", "public_path": "index.html", "locale": "ja", "kind": "article", "category": null, "title": "Home", "headline": null, "brief": null, "body_html": "<p>日本語ホーム本文</p>"},
      |    {"source_path": "index.dox", "public_path": "index.html", "locale": "en", "kind": "article", "category": null, "title": "Home", "headline": null, "brief": null, "body_html": "<p>English home narrative</p>"},
      |    {"source_path": "concept/index.dox", "public_path": "concept/index.html", "locale": "ja", "kind": "article", "category": "concept", "title": "Concept", "headline": null, "brief": null, "body_html": "<p>日本語カテゴリ本文</p>"},
      |    {"source_path": "concept/index.dox", "public_path": "concept/index.html", "locale": "en", "kind": "article", "category": "concept", "title": "Concept", "headline": null, "brief": null, "body_html": "<p>English category narrative</p>"}
      |  ]
      |}
      |""".stripMargin

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
      |      {"label": "2026-05-01", "start_date": "2026-05-01", "end_date": "2026-05-01", "count": 9, "article_count": 9, "glossary_term_count": 0},
      |      {"label": "2026-06-17", "start_date": "2026-06-17", "end_date": "2026-06-17", "count": 0, "article_count": 0, "glossary_term_count": 0},
      |      {"label": "2026-06-18", "start_date": "2026-06-18", "end_date": "2026-06-18", "count": 1, "article_count": 1, "glossary_term_count": 0},
      |      {"label": "2026-06-19", "start_date": "2026-06-19", "end_date": "2026-06-19", "count": 2, "article_count": 1, "glossary_term_count": 1},
      |      {"label": "2026-06-20", "start_date": "2026-06-20", "end_date": "2026-06-20", "count": 3, "article_count": 2, "glossary_term_count": 1},
      |      {"label": "2026-06-21", "start_date": "2026-06-21", "end_date": "2026-06-21", "count": 4, "article_count": 2, "glossary_term_count": 2},
      |      {"label": "2026-06-22", "start_date": "2026-06-22", "end_date": "2026-06-22", "count": 5, "article_count": 3, "glossary_term_count": 2},
      |      {"label": "2026-06-23", "start_date": "2026-06-23", "end_date": "2026-06-23", "count": 6, "article_count": 3, "glossary_term_count": 3}
      |    ]
      |  },
      |  "categories": [
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
      |    },
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
      |    }
      |  ]
      |}
      |""".stripMargin

  private def _empty_increments_dashboard_json: String =
    """{
      |  "counts": {
      |    "category_count": 1,
      |    "article_count": 7,
      |    "glossary_term_count": 0,
      |    "total_item_count": 7
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
      |  "categories": []
      |}
      |""".stripMargin

  private def _recent_bibliography_json: String =
    """{
      |  "entries": [
      |    {
      |      "id": "bib:recent-reference",
      |      "key": "recentReference",
      |      "slug": "recent-reference",
      |      "entry_type": "book",
      |      "title": "Recent Reference",
      |      "summary": "Recent reference summary.",
      |      "category": "architecture",
      |      "source_path": "bibliography/architecture/recent-reference.bib.dox",
      |      "public_path": "bibliography/architecture/recent-reference.html",
      |      "authors": ["Example Author"],
      |      "published_at": "2026",
      |      "source_kind": "internal",
      |      "refs": ["bib:recent-reference"],
      |      "needs_resolution": false
      |    }
      |  ]
      |}
      |""".stripMargin

  private def _recent_terms_json: String =
    """{
      |  "terms": [
      |    {
      |      "id": "architecture:recent-term",
      |      "slug": "recent-term",
      |      "title": "Recent Term Metadata",
      |      "reading": null,
      |      "category": "architecture",
      |      "source_path": "glossary/architecture/recent-term.dox",
      |      "public_path": "glossary/architecture/recent-term.html",
      |      "definition_html": "<p>Recent term definition.</p>",
      |      "summary": "Recent term summary.",
      |      "aliases": [],
      |      "article_refs": [],
      |      "term_refs": [],
      |      "rdf_refs": [],
      |      "video_refs": [],
      |      "term_type": "concept",
      |      "quality": {"isolated": false, "unreferenced": false, "weakly_connected": false}
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

  private def _touch(path: Path, instant: String): Unit =
    Files.setLastModifiedTime(path, FileTime.from(Instant.parse(instant)))

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
