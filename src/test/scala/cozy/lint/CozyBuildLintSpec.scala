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

    "verify SimpleModeling dependencies against the public repository" in {
      _with_temp_dir("cozy-build-lint-public-dependency") { dir =>
        Given("a project that depends on a published SimpleModeling artifact")
        _write_plugins(dir, """addSbtPlugin("org.goldenport" % "sbt-cozy" % "0.1.11")""")
        _write(
          dir.resolve("build.sbt"),
          """scalaVersion := "2.12.18"
            |libraryDependencies += "org.simplemodeling" %% "simplemodeler" % "1.1.22"
            |""".stripMargin
        )

        When("Cozy lints public dependency availability")
        val findings = CozyBuildLint.lint(
          dir,
          Some("0.1.11"),
          _public_artifacts("org.simplemodeling:simplemodeler_2.12:1.1.22" -> true)
        )

        Then("the public dependency check is OK")
        findings.find(_.code == "build.public-dependency").map(_.level) shouldBe Some(CozyBuildLint.Level.Ok)
      }
    }

    "warn when a SimpleModeling dependency is not published" in {
      _with_temp_dir("cozy-build-lint-missing-public-dependency") { dir =>
        Given("a project that depends on an unpublished SimpleModeling artifact")
        _write_plugins(dir, """addSbtPlugin("org.goldenport" % "sbt-cozy" % "0.1.11")""")
        _write(
          dir.resolve("build.sbt"),
          """scalaVersion := "2.12.18"
            |libraryDependencies += "org.simplemodeling" %% "simplemodeler" % "1.1.99"
            |""".stripMargin
        )

        When("Cozy lints public dependency availability")
        val findings = CozyBuildLint.lint(
          dir,
          Some("0.1.11"),
          _public_artifacts("org.simplemodeling:simplemodeler_2.12:1.1.99" -> false)
        )

        Then("the missing public dependency is a warning")
        findings.find(_.code == "build.public-dependency").map(_.level) shouldBe Some(CozyBuildLint.Level.Warn)
      }
    }

    "warn when only the public POM is available" in {
      _with_temp_dir("cozy-build-lint-pom-without-jar") { dir =>
        Given("a project whose dependency has a public POM but no binary artifact")
        _write_plugins(dir, """addSbtPlugin("org.goldenport" % "sbt-cozy" % "0.1.11")""")
        _write(
          dir.resolve("build.sbt"),
          """scalaVersion := "2.12.18"
            |libraryDependencies += "org.simplemodeling" %% "simplemodeler" % "1.1.99"
            |""".stripMargin
        )

        When("Cozy lints public dependency availability")
        val findings = CozyBuildLint.lint(
          dir,
          Some("0.1.11"),
          _public_artifacts("org.simplemodeling:simplemodeler_2.12:1.1.99" -> false)
        )

        Then("the incomplete publication is a warning")
        findings.find(_.code == "build.public-dependency").map(_.level) shouldBe Some(CozyBuildLint.Level.Warn)
      }
    }

    "verify Goldenport and SmartDox dependencies against the public repository" in {
      _with_temp_dir("cozy-build-lint-public-repository-groups") { dir =>
        Given("a project that depends on Goldenport and SmartDox artifacts from the public repository")
        _write_plugins(dir, """addSbtPlugin("org.goldenport" % "sbt-cozy" % "0.1.11")""")
        _write(
          dir.resolve("build.sbt"),
          """scalaVersion := "2.12.18"
            |libraryDependencies += "org.goldenport" %% "kaleidox" % "0.6.16"
            |libraryDependencies += "org.smartdox" %% "smartdox" % "2.4.15"
            |""".stripMargin
        )

        When("Cozy lints public dependency availability")
        val findings = CozyBuildLint.lint(
          dir,
          Some("0.1.11"),
          _public_artifacts(
            "org.goldenport:kaleidox_2.12:0.6.16" -> true,
            "org.smartdox:smartdox_2.12:2.4.15" -> true
          )
        )

        Then("both public dependency checks are OK")
        findings.filter(_.code == "build.public-dependency").map(_.level) shouldBe Vector(
          CozyBuildLint.Level.Ok,
          CozyBuildLint.Level.Ok
        )
      }
    }

    "ignore commented out dependency declarations" in {
      _with_temp_dir("cozy-build-lint-commented-dependency") { dir =>
        Given("a project with a commented out SimpleModeling repository dependency")
        _write_plugins(dir, """addSbtPlugin("org.goldenport" % "sbt-cozy" % "0.1.11")""")
        _write(
          dir.resolve("build.sbt"),
          """scalaVersion := "2.12.18"
            |// libraryDependencies += "org.goldenport" %% "goldenport-sexpr" % "2.0.13"
            |libraryDependencies += "org.goldenport" %% "kaleidox" % "0.6.16"
            |""".stripMargin
        )

        When("Cozy lints public dependency availability")
        val findings = CozyBuildLint.lint(
          dir,
          Some("0.1.11"),
          _public_artifacts("org.goldenport:kaleidox_2.12:0.6.16" -> true)
        )

        Then("only active dependencies are checked")
        val dependencyfindings = findings.filter(_.code == "build.public-dependency")
        dependencyfindings should have size 1
        dependencyfindings.head.message should include("kaleidox")
      }
    }

    "resolve multiline fallback versions in SimpleModeling dependency declarations" in {
      _with_temp_dir("cozy-build-lint-public-dependency-variable") { dir =>
        Given("a project that declares a SimpleModeling dependency version through a multiline fallback")
        _write_plugins(dir, """addSbtPlugin("org.goldenport" % "sbt-cozy" % "0.1.11")""")
        _write(
          dir.resolve("build.sbt"),
          """scalaVersion := "2.12.18"
            |val simplemodelerVersion =
            |  sys.props.getOrElse("simplemodeler.version", sys.env.getOrElse("SIMPLEMODELER_VERSION", "1.1.22"))
            |libraryDependencies += "org.simplemodeling" %% "simplemodeler" % simplemodelerVersion
            |""".stripMargin
        )

        When("Cozy lints public dependency availability")
        val findings = CozyBuildLint.lint(
          dir,
          Some("0.1.11"),
          _public_artifacts("org.simplemodeling:simplemodeler_2.12:1.1.22" -> true)
        )

        Then("the dependency version is resolved and checked")
        val dependencyfindings = findings.filter(_.code == "build.public-dependency")
        dependencyfindings.map(_.level) shouldBe Vector(CozyBuildLint.Level.Ok)
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

  private def _public_artifacts(values: (String, Boolean)*): CozyBuildLint.PublicArtifactAvailability = {
    val map = values.toMap
    new CozyBuildLint.PublicArtifactAvailability {
      def exists(dependency: CozyBuildLint.DependencyDeclaration): Option[Boolean] =
        map.get(s"${dependency.group}:${dependency.artifact}:${dependency.version}")
    }
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
