package cozy

import cozy.bok.CozyBok
import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path, Paths}
import java.util.zip.{ZipEntry, ZipInputStream, ZipOutputStream}
import scala.collection.JavaConverters._
import org.scalatest.GivenWhenThen
import org.scalatest.wordspec.AnyWordSpec
import org.goldenport.test.matchers.SpecVocabulary

/*
 * @since   Jun. 21, 2026
 * @version Jun. 21, 2026
 * @author  ASAMI, Tomoharu
 */
class CozyBokDashboardSpec extends AnyWordSpec with GivenWhenThen with SpecVocabulary {
  "Cozy BoK dashboard rendering" should {
    "use SmartDox source narratives" which {
      "render Home and Category index.dox as narrative sections" in {
        _with_temp_dir("cozy-bok-dashboard-narrative") { dir =>
          Given("a BoK source tree with Home and Category narrative index.dox files")
          _write(dir.resolve("src/main/doxsite/site.conf"),
            """site {
              |  output {
              |    locale_mode = "single_locale_root"
              |  }
              |}
              |""".stripMargin)
          _write(dir.resolve("src/main/doxsite/index.dox"),
            """Home
              |======
              |
              |# HEAD
              |
              |## BRIEF
              |Home narrative brief.
              |
              |# Overview
              |
              |Home narrative source text.
              |
              |## Quick Links
              |
              |- `cozy bok build` keeps generated dashboard metadata out of source.
              |- Raw text <script>alert("bad")</script> is content, not executable markup.
              |
              |## Operation Focus
              |
              |Operate this BoK through source files and generated dashboard pages.
              |""".stripMargin)
          _write(dir.resolve("src/main/doxsite/architecture/category.yaml"),
            """name: Architecture
              |title: Architecture
              |description: Architecture category.
              |""".stripMargin)
          _write(dir.resolve("src/main/doxsite/architecture/index.dox"),
            """Architecture
              |============
              |
              |# HEAD
              |
              |## BRIEF
              |Architecture narrative brief.
              |
              |# Overview
              |
              |Architecture narrative source text.
              |
              |## Focus
              |
              |Make architecture decisions reviewable.
              |""".stripMargin)
          val config = CozyBok.BuildConfig.create(List(dir.toString, "--strategy", "preview"))
          val runner = new DashboardRunner

          When("Cozy builds dashboard pages")
          CozyBok.build(config, runner)

          Then("the Home dashboard includes the SmartDox-rendered narrative section")
          val home = _read(dir.resolve("website.d/index.html"))
          home should include ("""href="_/css/bootstrap-grid.min.css"""")
          home should include ("""href="_/css/site.css"""")
          home should include ("""href="_/css/cozy-bok-dashboard.css"""")
          _read(dir.resolve("website.d/architecture/index.html")) should include ("""href="../_/css/bootstrap-grid.min.css"""")
          _read(dir.resolve("website.d/architecture/index.html")) should include ("""href="../_/css/site.css"""")
          _read(dir.resolve("website.d/architecture/index.html")) should include ("""href="../_/css/cozy-bok-dashboard.css"""")
          home should include ("""class="bok-dashboard container-fluid bok-dashboard-command-center"""")
          home should include ("""class="bok-dashboard-shell" id="dashboard"""")
          home should include ("""class="bok-dashboard-hero"""")
          home should include ("""class="bok-dashboard-hero-facts"""")
          home should include ("""class="body body-dashboard"""")
          home should not include ("""class="nav-container"""")
          home should not include ("""class="toc sidebar"""")
          home should include ("""class="row g-3"""")
          home should include ("""class="card bok-card bok-card-purpose"""")
          home should include ("""class="card bok-card bok-card-kpi"""")
          home should include ("""class="card bok-card bok-card-chart"""")
          home should include ("""id="narrative"""")
          home should include ("Home narrative source text.")
          home should include ("Quick Links")
          home should include ("Operation Focus")
          home should include ("Operate this BoK through source files and generated dashboard pages.")
          val dashboardstart = home.indexOf("""class="bok-dashboard container-fluid bok-dashboard-command-center"""")
          val narrativestart = home.indexOf("""id="narrative"""")
          dashboardstart should be < narrativestart
          home.substring(dashboardstart, narrativestart) should not include ("Home narrative source text.")
          home should not include ("""<html><head>""")
          home should not include ("""application/ld+json""")
          And("raw source markup is not passed through by Cozy's own inline HTML conversion")
          _read(dir.resolve("website.d/index.html")) should not include ("<script>alert")
          And("the Category dashboard includes the SmartDox-rendered narrative section")
          val category = _read(dir.resolve("website.d/architecture/index.html"))
          category should include ("""class="body body-dashboard"""")
          category should include ("""class="bok-dashboard-shell" id="dashboard"""")
          category should include ("""class="bok-dashboard-hero"""")
          category should not include ("""class="nav-container"""")
          category should not include ("""class="toc sidebar"""")
          category should include ("""id="narrative"""")
          category should include ("Architecture narrative source text.")
          category should include ("Make architecture decisions reviewable.")
        }
      }

      "keep scaffolded Category index.dox free of generated dashboard fragments" in {
        _with_temp_dir("cozy-bok-dashboard-scaffold") { dir =>
          Given("an existing BoK source scaffold")
          CozyBok.create(CozyBok.CreateConfig.create(List("--save", dir.toString, "--name", "KnowledgeHub BoK")))

          When("Cozy creates a category scaffold")
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
            "knowledgehub:KnowledgeHub:KnowledgeHub term."
          )))

          Then("the Category index remains source narrative rather than generated dashboard output")
          _read(dir.resolve("src/main/doxsite/knowledgehub/index.dox")) should include ("# Overview")
          _read(dir.resolve("src/main/doxsite/knowledgehub/index.dox")) should include ("KnowledgeHub category.")
          _read(dir.resolve("src/main/doxsite/knowledgehub/index.dox")) should include ("## Navigation")
          _read(dir.resolve("src/main/doxsite/knowledgehub/index.dox")) should not include ("""class="bok-metric-card"""")
          _read(dir.resolve("src/main/doxsite/knowledgehub/index.dox")) should not include ("""class="bok-dashboard-chart"""")
          _read(dir.resolve("src/main/doxsite/knowledgehub/index.dox")) should not include ("- Articles: 1")
          _read(dir.resolve("src/main/doxsite/knowledgehub/index.dox")) should not include ("- Terms: 1")
        }
      }

      "apply selectable color groups and right-aligned Category dropdown styling" in {
        _with_temp_dir("cozy-bok-dashboard-theme") { dir =>
          Given("a BoK source tree with a dashboard color group in site metadata")
          _write(dir.resolve("src/main/doxsite/site.conf"),
            """site {
              |  metadata {
              |    dashboard_color_group = "ocean"
              |  }
              |  output {
              |    locale_mode = "single_locale_root"
              |  }
              |}
              |""".stripMargin)
          _write(dir.resolve("src/main/doxsite/index.dox"),
            """Home
              |======
              |
              |# Overview
              |
              |Home narrative.
              |""".stripMargin)
          _write(dir.resolve("src/main/doxsite/architecture/category.yaml"),
            """name: Architecture
              |title: Architecture
              |description: Architecture category.
              |""".stripMargin)
          _write(dir.resolve("src/main/doxsite/architecture/index.dox"),
            """Architecture
              |============
              |
              |# Overview
              |
              |Architecture narrative.
              |""".stripMargin)
          _write_zip(dir.resolve("src/main/antora-ui/build/ui-bundle.zip"), Vector(
            "css/cozy-bok-dashboard.css" -> "old dashboard css",
            "css/bootstrap-grid.min.css" -> "old bootstrap grid"
          ))
          val config = CozyBok.BuildConfig.create(List(dir.toString, "--strategy", "preview"))

          When("Cozy builds the dashboard pages")
          CozyBok.build(config, new DashboardRunner)

          Then("the configured color group is applied to dashboard pages")
          config.dashboardColorGroup shouldBe "ocean"
          _read(dir.resolve("website.d/index.html")) should include ("""<body class="article bok-dashboard-theme-ocean">""")
          _read(dir.resolve("website.d/architecture/index.html")) should include ("""<body class="article bok-dashboard-theme-ocean">""")

          And("the generated CSS provides selectable groups and keeps the Category dropdown inside the viewport")
          val css = _read(Paths.get("src/main/resources/cozy/antora-ui/css/cozy-bok-dashboard.css"))
          css should include ("body.bok-dashboard-theme-aurora")
          css should include ("body.bok-dashboard-theme-lagoon")
          css should include ("body.bok-dashboard-theme-ocean")
          css should include ("body.bok-dashboard-theme-ember")
          css should include ("body.bok-dashboard-theme-slate")
          css should include (".navbar-category-dropdown > .navbar-category-menu")
          css should include ("left: auto")
          css should include ("right: 0")
          css should include ("max-width: min(22rem, calc(100vw - 1rem))")
          css should not include ("right:auto;left:0")
          _zip_text(dir.resolve("antora.d/ui-bundle.zip"), "css/cozy-bok-dashboard.css") should include ("body.bok-dashboard-theme-lagoon")
          _zip_text(dir.resolve("antora.d/ui-bundle.zip"), "css/cozy-bok-dashboard.css") should not include ("old dashboard css")
          _zip_text(dir.resolve("antora.d/ui-bundle.zip"), "css/bootstrap-grid.min.css") should include (".container-fluid")
          _zip_text(dir.resolve("antora.d/ui-bundle.zip"), "css/bootstrap-grid.min.css") should not include ("old bootstrap grid")

          And("the command line can override site metadata and invalid values fall back to the default")
          CozyBok.BuildConfig.create(List(dir.toString, "--strategy", "preview", "--dashboard-color-group", "ember")).dashboardColorGroup shouldBe "ember"
          CozyBok.BuildConfig.create(List(dir.toString, "--strategy", "preview", "--dashboard-color-group", "lagoon")).dashboardColorGroup shouldBe "lagoon"
          CozyBok.BuildConfig.create(List(dir.toString, "--strategy", "preview", "--dashboard-color-group", "unknown")).dashboardColorGroup shouldBe "aurora"
        }
      }

      "filter single index.dox narrative by configured default locale" in {
        _with_temp_dir("cozy-bok-dashboard-default-locale") { dir =>
          Given("a single-locale BoK whose index.dox carries language-specific spans")
          _write(dir.resolve("src/main/doxsite/site.conf"),
            """site {
              |  metadata {
              |    in_language = ["ja", "en"]
              |  }
              |  output {
              |    locale_mode = "single_locale_root"
              |    default_locale = "en"
              |  }
              |}
              |""".stripMargin)
          _write(dir.resolve("src/main/doxsite/index.dox"),
            """Home
              |======
              |
              |# Overview
              |
              |<span lang="ja">日本語ホーム本文</span><span lang="en">English home narrative</span>
              |""".stripMargin)
          val config = CozyBok.BuildConfig.create(List(dir.toString, "--strategy", "preview"))

          When("Cozy builds the single-locale dashboard")
          CozyBok.build(config, new RecordingRunner)

          Then("the configured default locale selects the English narrative from the same index.dox")
          config.defaultLocale shouldBe "en"
          _read(dir.resolve("website.d/index.html")) should include ("""<html lang="en">""")
          _read(dir.resolve("website.d/index.html")) should include ("English home narrative")
          _read(dir.resolve("website.d/index.html")) should not include ("日本語ホーム本文")
        }
      }

      "filter one multilingual index.dox into each locale subdirectory" in {
        _with_temp_dir("cozy-bok-dashboard-multilingual") { dir =>
          Given("a multi-locale BoK whose Home and Category index.dox files contain language-marked content")
          _write(dir.resolve("src/main/doxsite/site.conf"),
            """site {
              |  metadata {
              |    in_language = ["ja", "en"]
              |  }
              |  output {
              |    locale_mode = "multi_locale_subdirs"
              |    default_locale = "ja"
              |  }
              |}
              |""".stripMargin)
          _write(dir.resolve("src/main/doxsite/index.dox"),
            """Home
              |======
              |
              |# Overview
              |
              |<span lang="ja">日本語ホーム本文</span><span lang="en">English home narrative</span>
              |""".stripMargin)
          _write(dir.resolve("src/main/doxsite/concept/category.yaml"),
            """name: Concept
              |title: Concept
              |description: Concept category.
              |""".stripMargin)
          _write(dir.resolve("src/main/doxsite/concept/index.dox"),
            """Concept
              |=======
              |
              |# Overview
              |
              |<span lang="ja">日本語カテゴリ本文</span><span lang="en">English category narrative</span>
              |""".stripMargin)
          val config = CozyBok.BuildConfig.create(List(dir.toString, "--strategy", "preview"))

          When("Cozy builds the multi-locale dashboard pages")
          CozyBok.build(config, new RecordingRunner)

          Then("each locale output is generated from the same source index.dox with locale filtering")
          config.localeMode shouldBe CozyBok.LocaleMode.MultiLocaleSubdirs
          config.defaultLocale shouldBe "ja"
          _read(dir.resolve("website.d/ja/index.html")) should include ("""<html lang="ja">""")
          _read(dir.resolve("website.d/en/index.html")) should include ("""<html lang="en">""")
          _read(dir.resolve("website.d/ja/index.html")) should include ("""href="../_/css/bootstrap-grid.min.css"""")
          _read(dir.resolve("website.d/ja/index.html")) should include ("""href="../_/css/site.css"""")
          _read(dir.resolve("website.d/ja/index.html")) should include ("""href="../_/css/cozy-bok-dashboard.css"""")
          _read(dir.resolve("website.d/en/index.html")) should include ("""href="../_/css/bootstrap-grid.min.css"""")
          _read(dir.resolve("website.d/en/index.html")) should include ("""href="../_/css/site.css"""")
          _read(dir.resolve("website.d/en/index.html")) should include ("""href="../_/css/cozy-bok-dashboard.css"""")
          _read(dir.resolve("website.d/ja/concept/index.html")) should include ("""href="../../_/css/bootstrap-grid.min.css"""")
          _read(dir.resolve("website.d/ja/concept/index.html")) should include ("""href="../../_/css/site.css"""")
          _read(dir.resolve("website.d/ja/concept/index.html")) should include ("""href="../../_/css/cozy-bok-dashboard.css"""")
          _read(dir.resolve("website.d/en/concept/index.html")) should include ("""href="../../_/css/bootstrap-grid.min.css"""")
          _read(dir.resolve("website.d/en/concept/index.html")) should include ("""href="../../_/css/site.css"""")
          _read(dir.resolve("website.d/en/glossary/index.html")) should include ("""href="../../_/css/site.css"""")
          _read(dir.resolve("website.d/ja/index.html")) should include ("日本語ホーム本文")
          _read(dir.resolve("website.d/ja/index.html")) should not include ("English home narrative")
          _read(dir.resolve("website.d/en/index.html")) should include ("English home narrative")
          _read(dir.resolve("website.d/en/index.html")) should not include ("日本語ホーム本文")
          _read(dir.resolve("website.d/en/index.html")) should not include ("This dashboard aggregates the whole BoK status")
          _read(dir.resolve("website.d/en/index.html")) should not include ("BoK全体の状態")
          _read(dir.resolve("website.d/ja/concept/index.html")) should include ("日本語カテゴリ本文")
          _read(dir.resolve("website.d/en/concept/index.html")) should include ("English category narrative")
          _read(dir.resolve("website.d/en/concept/index.html")) should not include ("This dashboard aggregates this category")
          _read(dir.resolve("website.d/en/concept/index.html")) should not include ("このカテゴリの目的")
          _read(dir.resolve("website.d/en/glossary/index.html")) should include ("Dashboard for terms and vocabulary shared across the BoK.")
          _read(dir.resolve("website.d/en/glossary/index.html")) should not include ("BoK全体で共有する用語")
          _read(dir.resolve("website.d/en/manual/index.html")) should include ("Manual pages are excluded from automatic glossary linking.")
          _read(dir.resolve("website.d/en/manual/index.html")) should not include ("Manualは自動用語リンク対象外")
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
      if (command.take(2) == Vector("dox", "site"))
        _write(cwd.resolve("doxsite.d/metadata/dashboard/site.json"), _dashboard_json)
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

  private def _write_zip(path: Path, entries: Vector[(String, String)]): Path = {
    Option(path.getParent).foreach(Files.createDirectories(_))
    val out = new ZipOutputStream(Files.newOutputStream(path))
    try {
      entries.foreach {
        case (name, content) =>
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
