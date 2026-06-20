package cozy

import cozy.bok.CozyBok
import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path}
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
              |# Dashboard
              |
              |Home narrative source text.
              |
              |## Quick Links
              |
              |- `cozy bok build` keeps generated dashboard data out of source.
              |- Raw text <script>alert("bad")</script> is content, not executable markup.
              |
              |## Operation Focus
              |
              |Operate this BoK through source files and generated dashboards.
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
              |# Dashboard
              |
              |Architecture narrative source text.
              |
              |## Focus
              |
              |Make architecture decisions reviewable.
              |""".stripMargin)
          val config = CozyBok.BuildConfig.create(List(dir.toString, "--strategy", "preview"))
          val runner = new RecordingRunner

          When("Cozy builds dashboard pages")
          CozyBok.build(config, runner)

          Then("the Home dashboard includes the SmartDox-rendered narrative section")
          _read(dir.resolve("website.d/index.html")) should include ("""href="_/css/site.css"""")
          _read(dir.resolve("website.d/architecture/index.html")) should include ("""href="../_/css/site.css"""")
          _read(dir.resolve("website.d/index.html")) should include ("""id="narrative"""")
          _read(dir.resolve("website.d/index.html")) should include ("Home narrative source text.")
          _read(dir.resolve("website.d/index.html")) should include ("Quick Links")
          _read(dir.resolve("website.d/index.html")) should include ("Operation Focus")
          _read(dir.resolve("website.d/index.html")) should include ("Operate this BoK through source files and generated dashboards.")
          _read(dir.resolve("website.d/index.html")) should not include ("""<html><head>""")
          _read(dir.resolve("website.d/index.html")) should not include ("""application/ld+json""")
          And("raw source markup is not passed through by Cozy's own inline HTML conversion")
          _read(dir.resolve("website.d/index.html")) should not include ("<script>alert")
          And("the Category dashboard includes the SmartDox-rendered narrative section")
          _read(dir.resolve("website.d/architecture/index.html")) should include ("""id="narrative"""")
          _read(dir.resolve("website.d/architecture/index.html")) should include ("Architecture narrative source text.")
          _read(dir.resolve("website.d/architecture/index.html")) should include ("Make architecture decisions reviewable.")
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
          _read(dir.resolve("src/main/doxsite/knowledgehub/index.dox")) should include ("# Dashboard")
          _read(dir.resolve("src/main/doxsite/knowledgehub/index.dox")) should include ("KnowledgeHub category.")
          _read(dir.resolve("src/main/doxsite/knowledgehub/index.dox")) should include ("## Navigation")
          _read(dir.resolve("src/main/doxsite/knowledgehub/index.dox")) should not include ("""class="bok-metric-card"""")
          _read(dir.resolve("src/main/doxsite/knowledgehub/index.dox")) should not include ("""class="bok-dashboard-chart"""")
          _read(dir.resolve("src/main/doxsite/knowledgehub/index.dox")) should not include ("- Articles: 1")
          _read(dir.resolve("src/main/doxsite/knowledgehub/index.dox")) should not include ("- Terms: 1")
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
              |# Dashboard
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
              |# Dashboard
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
              |# Dashboard
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
          _read(dir.resolve("website.d/ja/index.html")) should include ("""href="../_/css/site.css"""")
          _read(dir.resolve("website.d/en/index.html")) should include ("""href="../_/css/site.css"""")
          _read(dir.resolve("website.d/ja/concept/index.html")) should include ("""href="../../_/css/site.css"""")
          _read(dir.resolve("website.d/en/concept/index.html")) should include ("""href="../../_/css/site.css"""")
          _read(dir.resolve("website.d/en/glossary/index.html")) should include ("""href="../../_/css/site.css"""")
          _read(dir.resolve("website.d/ja/index.html")) should include ("日本語ホーム本文")
          _read(dir.resolve("website.d/ja/index.html")) should not include ("English home narrative")
          _read(dir.resolve("website.d/en/index.html")) should include ("English home narrative")
          _read(dir.resolve("website.d/en/index.html")) should not include ("日本語ホーム本文")
          _read(dir.resolve("website.d/en/index.html")) should include ("This dashboard aggregates the whole BoK status")
          _read(dir.resolve("website.d/en/index.html")) should not include ("BoK全体の状態")
          _read(dir.resolve("website.d/ja/concept/index.html")) should include ("日本語カテゴリ本文")
          _read(dir.resolve("website.d/en/concept/index.html")) should include ("English category narrative")
          _read(dir.resolve("website.d/en/concept/index.html")) should include ("This dashboard aggregates this category")
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
