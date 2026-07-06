package cozy.lint

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path}
import java.io.{ByteArrayOutputStream, PrintStream}
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Jul.  6, 2026
 * @version Jul.  6, 2026
 * @author  ASAMI, Tomoharu
 */
class CozyBuildLintSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  "Cozy build lint" should {
    "accept the latest sbt-cozy plugin version" in {
      _with_temp_dir("cozy-build-lint-latest") { dir =>
        Given("a project that declares the latest sbt-cozy plugin")
        _write_plugins(dir, """addSbtPlugin("org.goldenport" % "sbt-cozy" % "0.1.11")""")

        When("Cozy lints the build")
        val findings = CozyBuildLint.lint(dir, Some("0.1.11"))

        Then("the latest-version check is OK")
        findings.find(_.code == "build.sbt-cozy-latest").map(_.level) shouldBe Some(CozyBuildLint.Level.Ok)
      }
    }

    "warn when sbt-cozy is older than the latest published version" in {
      _with_temp_dir("cozy-build-lint-old") { dir =>
        Given("a project that declares an old sbt-cozy plugin")
        _write_plugins(dir, """addSbtPlugin("org.goldenport" % "sbt-cozy" % "0.1.10")""")

        When("Cozy lints the build")
        val findings = CozyBuildLint.lint(dir, Some("0.1.11"))

        Then("the stale plugin is a warning")
        findings.find(_.code == "build.sbt-cozy-latest").map(_.level) shouldBe Some(CozyBuildLint.Level.Warn)
      }
    }

    "warn when sbt-cozy is a SNAPSHOT version" in {
      _with_temp_dir("cozy-build-lint-snapshot") { dir =>
        Given("a project that declares a SNAPSHOT sbt-cozy plugin")
        _write_plugins(dir, """addSbtPlugin("org.goldenport" % "sbt-cozy" % "0.1.12-SNAPSHOT")""")

        When("Cozy lints the build")
        val findings = CozyBuildLint.lint(dir, Some("0.1.11"))

        Then("the development plugin is a warning")
        findings.find(_.code == "build.sbt-cozy-latest").map(_.level) shouldBe Some(CozyBuildLint.Level.Warn)
      }
    }

    "warn when sbt-cozy plugin declaration is missing" in {
      _with_temp_dir("cozy-build-lint-missing") { dir =>
        Given("a project without an sbt-cozy plugin declaration")
        _write(dir.resolve("build.sbt"), """enablePlugins(org.goldenport.cozy.CozyPlugin)""")

        When("Cozy lints the build")
        val findings = CozyBuildLint.lint(dir, Some("0.1.11"))

        Then("the missing plugin is a warning")
        findings.find(_.code == "build.sbt-cozy-plugin").map(_.level) shouldBe Some(CozyBuildLint.Level.Warn)
      }
    }

    "resolve variable based sbt-cozy plugin declarations" in {
      _with_temp_dir("cozy-build-lint-variable") { dir =>
        Given("a project that declares sbt-cozy through a local variable")
        _write_plugins(
          dir,
          """val sbtCozyVersion = "0.1.11"
            |addSbtPlugin("org.goldenport" % "sbt-cozy" % sbtCozyVersion)
            |""".stripMargin
        )

        When("Cozy lints the build")
        val findings = CozyBuildLint.lint(dir, Some("0.1.11"))

        Then("the variable version is accepted")
        findings.find(_.code == "build.sbt-cozy-latest").map(_.level) shouldBe Some(CozyBuildLint.Level.Ok)
      }
    }

    "resolve scaffolded sbt-cozy plugin declarations with system property and environment fallback" in {
      _with_temp_dir("cozy-build-lint-scaffolded-variable") { dir =>
        Given("a project that declares sbt-cozy through the standard Cozy scaffold expression")
        _write_plugins(
          dir,
          """val sbtCozyVersion = sys.props.getOrElse("sbt.cozy.version", sys.env.getOrElse("SBT_COZY_VERSION", "0.1.11"))
            |addSbtPlugin("org.goldenport" % "sbt-cozy" % sbtCozyVersion)
            |""".stripMargin
        )

        When("Cozy lints the build")
        val findings = CozyBuildLint.lint(dir, Some("0.1.11"))

        Then("the scaffolded fallback version is accepted")
        findings.find(_.code == "build.sbt-cozy-latest").map(_.level) shouldBe Some(CozyBuildLint.Level.Ok)
      }
    }

    "warn when latest published version cannot be checked" in {
      _with_temp_dir("cozy-build-lint-no-latest") { dir =>
        Given("a project with an sbt-cozy plugin declaration")
        _write_plugins(dir, """addSbtPlugin("org.goldenport" % "sbt-cozy" % "0.1.11")""")

        When("Cozy lints the build without latest metadata")
        val findings = CozyBuildLint.lint(dir, None)

        Then("the metadata gap is a warning")
        findings.find(_.code == "build.sbt-cozy-latest").map(_.level) shouldBe Some(CozyBuildLint.Level.Warn)
      }
    }

    "make warnings fail in strict mode" in {
      _with_temp_dir("cozy-build-lint-strict") { dir =>
        Given("a project that declares an old sbt-cozy plugin")
        _write_plugins(dir, """addSbtPlugin("org.goldenport" % "sbt-cozy" % "0.1.10")""")

        When("Cozy runs build lint in strict mode")
        val exitcode = _capture_stdout {
          CozyBuildLint.execute(List(dir.toString, "--strict"), Some("0.1.11"))
        }

        Then("the warning produces a failing exit code")
        exitcode shouldBe 1
      }
    }

    "render machine readable JSON findings" in {
      _with_temp_dir("cozy-build-lint-json") { dir =>
        Given("a project that declares the latest sbt-cozy plugin")
        _write_plugins(dir, """addSbtPlugin("org.goldenport" % "sbt-cozy" % "0.1.11")""")

        When("Cozy renders JSON lint output")
        val json = CozyBuildLint.toJson(CozyBuildLint.lint(dir, Some("0.1.11")))

        Then("the result is machine readable")
        json should include(""""findings"""")
        json should include(""""code":"build.sbt-cozy-latest"""")
        json should include(""""level":"OK"""")
      }
    }
  }

  private def _write_plugins(dir: Path, content: String): Path =
    _write(dir.resolve("project").resolve("plugins.sbt"), content)

  private def _with_temp_dir[A](prefix: String)(body: Path => A): A = {
    val dir = Files.createTempDirectory(prefix)
    try {
      body(dir)
    } finally {
      _delete(dir)
    }
  }

  private def _capture_stdout[A](body: => A): A = {
    val out = new ByteArrayOutputStream()
    Console.withOut(new PrintStream(out, true, StandardCharsets.UTF_8.name())) {
      body
    }
  }

  private def _write(path: Path, content: String): Path = {
    Files.createDirectories(path.getParent)
    Files.write(path, content.getBytes(StandardCharsets.UTF_8))
    path
  }

  private def _delete(path: Path): Unit =
    if (Files.exists(path)) {
      val stream = Files.walk(path)
      try {
        stream.sorted(java.util.Comparator.reverseOrder()).forEach(Files.delete(_))
      } finally {
        stream.close()
      }
    }
}
