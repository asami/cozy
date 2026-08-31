package cozy.document

import cozy.scaffold.CozyHelpText
import java.io.{ByteArrayOutputStream, PrintStream}
import java.nio.charset.StandardCharsets
import java.nio.file.{Files, LinkOption, Path}
import scala.collection.JavaConverters._
import scala.util.control.NonFatal
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Aug. 31, 2026
 * @version Aug. 31, 2026
 * @author  ASAMI, Tomoharu
 */
final class CozyDocumentProjectSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  "Cozy Document Project" should {
    "scaffold the exact standard and standard-video authored skeletons without fake outputs" in {
      _with_temp_dir("cozy-document-project-scaffold") { root =>
        Given("an existing direct parent and two absent Document Project packages")
        val standardparent = Files.createDirectory(root.resolve("standard-parent"))
        val videoparent = Files.createDirectory(root.resolve("video-parent"))

        When("the standard and standard-video commands scaffold their packages")
        val standardoutput = _execute(List("document-project", "scaffold", "standard-doc", "--profile", "standard", "--language", "en", "--workspace", "directory", "--save", standardparent.toString))
        val videooutput = _execute(List("document-project", "scaffold", "video-doc", "--profile", "standard-video", "--language", "ja", "--workspace", "bok", "--save", videoparent.toString))
        val standard = standardparent.resolve("standard-doc.dox")
        val video = videoparent.resolve("video-doc.dox")

        Then("each profile contains only its declared authored sources")
        _relative_files(standard) shouldBe Set(
          "document-project.yaml", "index.dox", "content/core-en.yaml", "infographic/infographic.svg",
          "presentation/visual-pages.yaml", "review/README.md"
        )
        _relative_files(video) shouldBe Set(
          "document-project.yaml", "index.dox", "content/core-ja.yaml", "infographic/infographic.svg",
          "presentation/visual-pages.yaml", "review/README.md", "video/storyboard.md"
        )
        Files.readString(standard.resolve("content/core-en.yaml"), StandardCharsets.UTF_8) should include("accepted: []")
        Files.exists(standard.resolve("target"), LinkOption.NOFOLLOW_LINKS) shouldBe false
        Files.exists(standard.resolve("dashboard.html"), LinkOption.NOFOLLOW_LINKS) shouldBe false
        standardoutput should startWith("Cozy Document Project Scaffold")
        videooutput should include("workspace: bok")
      }
    }

    "admit the exact closed descriptor and Core through inspect and verify" in {
      _with_temp_dir("cozy-document-project-admission") { root =>
        Given("a standard scaffold with the closed descriptor and Content Core")
        val parent = Files.createDirectory(root.resolve("parent"))
        _execute(List("document-project", "scaffold", "sample", "--profile", "standard", "--language", "en", "--workspace", "directory", "--save", parent.toString))
        val project = parent.resolve("sample.dox")

        When("inspect and verify read the admitted project")
        val inspect = _execute(List("document-project", "inspect", project.toString))
        val verify = _execute(List("document-project", "verify", project.toString))

        Then("both reports identify the project and descriptor schema")
        inspect should startWith("Cozy Document Project Inspect")
        inspect should include("project: sample")
        inspect should include("schema: cozy.document-project.v1")
        verify should startWith("Cozy Document Project Verify")
        verify should include("schema: cozy.document-project.v1")

        And("a parsed unknown field remains a closed-descriptor diagnostic")
        Files.writeString(project.resolve("document-project.yaml"), Files.readString(project.resolve("document-project.yaml"), StandardCharsets.UTF_8) + "unknown: value\n", StandardCharsets.UTF_8)
        _failure(List("document-project", "inspect", project.toString)) should include("DP-DESC-002")
      }
    }

    "report DP42-02 deferred work categories through plan without creating authority" in {
      _with_temp_dir("cozy-document-project-plan") { root =>
        Given("a valid scaffolded standard Document Project")
        val parent = Files.createDirectory(root.resolve("parent"))
        _execute(List("document-project", "scaffold", "sample", "--profile", "standard", "--language", "en", "--workspace", "directory", "--save", parent.toString))
        val project = parent.resolve("sample.dox")

        When("plan reads the declared project before DP42-02 declares operations and branches")
        val plan = _execute(List("document-project", "plan", project.toString))

        Then("the report distinguishes active, omitted, blocked, and eligible declared work")
        plan should include("active: no declared work")
        plan should include("omitted: no declared work")
        plan should include("blocked: operation and branch resolution pending DP42-02 (not state or authority)")
        plan should include("eligible: no declared work")
      }
    }

    "reject a regular-file scaffold parent at the path gate" in {
      _with_temp_dir("cozy-document-project-scaffold-parent") { root =>
        Given("an existing regular file used as a scaffold parent")
        val parent = root.resolve("parent-file")
        Files.writeString(parent, "not a directory\n", StandardCharsets.UTF_8)

        When("scaffold receives the regular-file parent through --save")
        val failure = _failure(List("document-project", "scaffold", "sample", "--profile", "standard", "--language", "en", "--workspace", "directory", "--save", parent.toString))

        Then("the first and sole stable diagnostic is the unsafe-path token")
        _diagnostic_tokens(failure) shouldBe Vector("DP-PATH-001")
      }
    }

    "reject an unsafe initial source before a closed-invalid descriptor during verify" in {
      _with_temp_dir("cozy-document-project-verify-path-precedence") { root =>
        Given("a scaffolded project with a closed-invalid descriptor and an external source")
        val parent = Files.createDirectory(root.resolve("parent"))
        _execute(List("document-project", "scaffold", "sample", "--profile", "standard", "--language", "en", "--workspace", "directory", "--save", parent.toString))
        val project = parent.resolve("sample.dox")
        val descriptor = project.resolve("document-project.yaml")
        Files.writeString(descriptor, Files.readString(descriptor, StandardCharsets.UTF_8) + "unknown: value\n", StandardCharsets.UTF_8)
        val external = root.resolve("external-index.dox")
        Files.writeString(external, "external source\n", StandardCharsets.UTF_8)
        Files.delete(project.resolve("index.dox"))
        Files.createSymbolicLink(project.resolve("index.dox"), external)

        When("verify admits unconditional authored sources before descriptor validation")
        val failure = _failure(List("document-project", "verify", project.toString))

        Then("the first and sole stable diagnostic is the unsafe-path token")
        _diagnostic_tokens(failure) shouldBe Vector("DP-PATH-001")
      }
    }

    "reject a raw traversal Content Core path before closed-descriptor validation" in {
      _with_temp_dir("cozy-document-project-content-core-path") { root =>
        Given("a valid scaffolded project whose raw descriptor Content Core path traverses")
        val parent = Files.createDirectory(root.resolve("parent"))
        _execute(List("document-project", "scaffold", "sample", "--profile", "standard", "--language", "en", "--workspace", "directory", "--save", parent.toString))
        val project = parent.resolve("sample.dox")
        val descriptor = project.resolve("document-project.yaml")
        Files.writeString(descriptor, Files.readString(descriptor, StandardCharsets.UTF_8).replace("contentCore: content/core-en.yaml", "contentCore: content/../core-en.yaml"), StandardCharsets.UTF_8)

        When("inspect loads the raw structured descriptor before closed validation")
        val failure = _failure(List("document-project", "inspect", project.toString))

        Then("the first and sole stable diagnostic is the unsafe-path token")
        _diagnostic_tokens(failure) shouldBe Vector("DP-PATH-001")
      }
    }

    "reject unsafe direct, symlinked, and escaped authored source paths before descriptor loading" in {
      _with_temp_dir("cozy-document-project-path") { root =>
        Given("a direct package and an external file available for symbolic links")
        val parent = Files.createDirectory(root.resolve("parent"))
        _execute(List("document-project", "scaffold", "sample", "--profile", "standard", "--language", "en", "--workspace", "directory", "--save", parent.toString))
        val project = parent.resolve("sample.dox")
        val external = root.resolve("external.yaml")
        Files.writeString(external, "schema: cozy.document-project.v1\n", StandardCharsets.UTF_8)

        When("the descriptor source is replaced by a symbolic link")
        Files.delete(project.resolve("document-project.yaml"))
        Files.createSymbolicLink(project.resolve("document-project.yaml"), external)

        Then("inspection rejects its unsafe source at the path gate")
        _failure(List("document-project", "inspect", project.toString)) should include("DP-PATH-001")

        And("a verified source that escapes through a symbolic-link directory is also rejected")
        _execute(List("document-project", "scaffold", "second", "--profile", "standard", "--language", "en", "--workspace", "directory", "--save", parent.toString))
        val second = parent.resolve("second.dox")
        val externalpresentation = Files.createDirectory(root.resolve("external-presentation"))
        Files.writeString(externalpresentation.resolve("visual-pages.yaml"), "pages: []\n", StandardCharsets.UTF_8)
        Files.delete(second.resolve("presentation/visual-pages.yaml"))
        Files.delete(second.resolve("presentation"))
        Files.createSymbolicLink(second.resolve("presentation"), externalpresentation)
        _failure(List("document-project", "verify", second.toString)) should include("DP-PATH-001")

        And("an arbitrary descriptor operand is rejected as a project path")
        _failure(List("document-project", "inspect", root.resolve("not-a-project.yaml").toString)) should include("DP-PATH-001")
      }
    }

    "give dashboard phase rejection precedence before any path resolution or output write" in {
      _with_temp_dir("cozy-document-project-dashboard") { root =>
        Given("syntactically complete dashboard arguments with nonexistent project and parent paths")
        val project = root.resolve("missing.dox")
        val output = root.resolve("uncreated/output/dashboard.html")

        When("dashboard is requested in Phase 42")
        val failure = _failure(List("document-project", "dashboard", project.toString, "--save", output.toString))

        Then("the phase diagnostic is the only gate reached and no output is written")
        failure should include("DP-PHASE-001")
        Files.exists(output, LinkOption.NOFOLLOW_LINKS) shouldBe false
        Files.exists(output.getParent, LinkOption.NOFOLLOW_LINKS) shouldBe false
      }
    }

    "keep strict grammar and missing-value diagnostics distinct" in {
      Given("the frozen public grammar")

      When("an unsupported spelling, an unknown command, and known forms missing required values are parsed")
      val spelling = _failure(List("document-project", "inspect", "sample.dox", "--save=out.html"))
      val unknown = _failure(List("document-project", "unknown", "sample.dox"))
      val missingproject = _failure(List("document-project", "verify"))
      val missingoperation = _failure(List("document-project", "run", "sample.dox"))

      Then("grammar failures precede only with DP-CLI-001 and missing forms use DP-CLI-002")
      spelling should include("DP-CLI-001")
      unknown should include("DP-CLI-001")
      missingproject should include("DP-CLI-002")
      missingoperation should include("DP-CLI-002")
    }

    "reject every run operation as undeclared only after project and descriptor admission" in {
      _with_temp_dir("cozy-document-project-run") { root =>
        Given("an admitted standard Document Project")
        val parent = Files.createDirectory(root.resolve("parent"))
        _execute(List("document-project", "scaffold", "sample", "--profile", "standard", "--language", "en", "--workspace", "directory", "--save", parent.toString))
        val project = parent.resolve("sample.dox")

        When("run names a logical operation before DP42-02 declares operations")
        val failure = _failure(List("document-project", "run", project.toString, "--operation", "render"))

        Then("the declared-operation gate rejects without execution")
        failure should include("DP-OP-001")
      }
    }

    "protect an existing scaffold destination without overwrite or merge" in {
      _with_temp_dir("cozy-document-project-overwrite") { root =>
        Given("an already scaffolded destination with authored content")
        val parent = Files.createDirectory(root.resolve("parent"))
        val command = List("document-project", "scaffold", "sample", "--profile", "standard", "--language", "en", "--workspace", "directory", "--save", parent.toString)
        _execute(command)
        val marker = parent.resolve("sample.dox/index.dox")
        Files.writeString(marker, "preserved authored source\n", StandardCharsets.UTF_8)

        When("the same destination is requested again")
        val failure = _failure(command)

        Then("the atomic scaffold gate rejects and leaves existing authored content intact")
        failure should include("DP-SCAFFOLD-001")
        Files.readString(marker, StandardCharsets.UTF_8) shouldBe "preserved authored source\n"
      }
    }

    "publish the frozen public Document Project help forms" in {
      Given("the Cozy public help text")

      When("the Document Project command section is inspected")
      val help = CozyHelpText._text

      Then("each no-alias form and the Phase 42 dashboard boundary are described")
      help should include("document-project inspect <project>")
      help should include("document-project run <project> --operation <logical-operation> [--dry-run]")
      help should include("document-project scaffold <slug> --profile standard|standard-video --language <tag> --workspace directory|bok --save <parent>")
      help should include("Dashboard is reserved for Phase 42.1 and rejects in Phase 42")
    }
  }

  private def _execute(args: List[String]): String = {
    val bytes = new ByteArrayOutputStream()
    Console.withOut(new PrintStream(bytes, true, "UTF-8")) {
      CozyDocumentProject.execute(args) shouldBe true
    }
    bytes.toString("UTF-8").trim
  }

  private def _failure(args: List[String]): String =
    intercept[RuntimeException] {
      CozyDocumentProject.execute(args)
    }.getMessage

  private def _diagnostic_tokens(value: String): Vector[String] =
    """DP-[A-Z]+-\d{3}""".r.findAllIn(value).toVector

  private def _relative_files(root: Path): Set[String] = {
    val stream = Files.walk(root)
    try stream.iterator().asScala.filter(path => Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)).map(root.relativize(_).toString.replace('\\', '/')).toSet
    finally stream.close()
  }

  private def _with_temp_dir(name: String)(body: Path => Unit): Unit = {
    val root = Files.createTempDirectory(name)
    try body(root)
    finally _delete(root)
  }

  private def _delete(path: Path): Unit =
    if (Files.exists(path, LinkOption.NOFOLLOW_LINKS)) {
      val stream = Files.walk(path)
      try stream.iterator().asScala.toVector.sortBy(_.toString.length).reverse.foreach { item =>
        try Files.deleteIfExists(item) catch { case NonFatal(_) => () }
      }
      finally stream.close()
    }
}
