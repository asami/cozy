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

    "define a closed reusable document-production model" which {
      "validate its stable Work Products and closed reference vocabulary" in {
        Given("the immutable document-production definition")
        val definition = CozyDocumentWorkflow.documentProduction

        When("the reusable definition is validated before projection")
        val validation = CozyDocumentWorkflow.validate(definition)

        Then("every stable Work Product role and disposition reference is closed")
        validation shouldBe Vector.empty
        definition.workProducts.map(_.id).distinct shouldBe definition.workProducts.map(_.id)
        definition.workProducts.map(_.role.value).toSet shouldBe Set("authority", "plan", "candidate", "review-projection", "deliverable", "receipt")
        definition.profiles.flatMap(_.bindings.map(_.disposition.value)).toSet shouldBe Set("required", "optional", "disabled")
      }

      "reject duplicate Work Product ids before a projection can use them" in {
        Given("the immutable definition with a duplicate Work Product")
        val definition = CozyDocumentWorkflow.documentProduction
        val invalid = definition.copy(workProducts = definition.workProducts :+ definition.workProducts.head)

        When("the duplicate definition is validated")
        val errors = CozyDocumentWorkflow.validate(invalid)

        Then("validation reports the duplicate Work Product id")
        errors should contain("duplicate Work Product id: content-core-candidate")
      }

      "reject unknown Work Product dependencies before a projection can use them" in {
        Given("the immutable definition with an unknown article dependency")
        val definition = CozyDocumentWorkflow.documentProduction
        val invalid = definition.copy(workProducts = definition.workProducts.map { product =>
          if (product.id == "article-source") product.copy(dependencies = product.dependencies :+ "unknown-work-product") else product
        })

        When("the definition with an unknown dependency is validated")
        val errors = CozyDocumentWorkflow.validate(invalid)

        Then("validation reports the unknown dependency reference")
        errors should contain("Work Product article-source references unknown dependency Work Product: unknown-work-product")
      }

      "reject Work Product dependency cycles before a projection can use them" in {
        Given("the immutable definition with a content-core dependency cycle")
        val definition = CozyDocumentWorkflow.documentProduction
        val invalid = definition.copy(workProducts = definition.workProducts.map { product =>
          if (product.id == "content-core-candidate") product.copy(dependencies = Vector("content-core")) else product
        })

        When("the cyclic definition is validated")
        val errors = CozyDocumentWorkflow.validate(invalid)

        Then("validation reports the dependency cycle")
        errors should contain("dependency cycle includes Work Product: content-core-candidate")
      }

      "reject empty Work Product metadata before a projection can use it" in {
        Given("the immutable definition with an empty article label")
        val definition = CozyDocumentWorkflow.documentProduction
        val invalid = definition.copy(workProducts = definition.workProducts.map { product =>
          if (product.id == "article-source") product.copy(label = " ") else product
        })

        When("the incomplete definition is validated")
        val errors = CozyDocumentWorkflow.validate(invalid)

        Then("validation reports the empty required metadata")
        errors should contain("Work Product has empty required metadata: article-source")
      }

      "reject profile bindings outside the closed Work Product definition" in {
        Given("the immutable definition with an outside standard profile binding")
        val definition = CozyDocumentWorkflow.documentProduction
        val invalid = definition.copy(profiles = definition.profiles.map { profile =>
          if (profile.id == "standard") profile.copy(bindings = profile.bindings :+ CozyDocumentWorkflow.WorkProductBinding("outside-work-product", CozyDocumentWorkflow.WorkProductDisposition.Required, None)) else profile
        })

        When("the outside-binding definition is validated")
        val errors = CozyDocumentWorkflow.validate(invalid)

        Then("validation reports the unknown profile binding")
        errors should contain("profile standard references unknown Work Product binding: outside-work-product")
      }

      "reject deviations from the canonical profile binding matrix" in {
        Given("independently mutated standard and standard-video profile bindings")
        val definition = CozyDocumentWorkflow.documentProduction
        val standarddeviation = definition.copy(profiles = definition.profiles.map { profile =>
          if (profile.id == "standard") profile.copy(bindings = profile.bindings.map { binding =>
            if (binding.workProductId == "article-pdf") binding.copy(disposition = CozyDocumentWorkflow.WorkProductDisposition.Optional) else binding
          }) else profile
        })
        val standardvideodeviation = definition.copy(profiles = definition.profiles.map { profile =>
          if (profile.id == "standard-video") profile.copy(bindings = profile.bindings.map { binding =>
            if (binding.workProductId == "video-storyboard") binding.copy(disposition = CozyDocumentWorkflow.WorkProductDisposition.Optional) else binding
          }) else profile
        })

        When("each mutated profile matrix is validated")
        val standarderrors = CozyDocumentWorkflow.validate(standarddeviation)
        val standardvideoerrors = CozyDocumentWorkflow.validate(standardvideodeviation)

        Then("validation reports each exact canonical disposition and reason deviation")
        standarderrors should contain("profile standard Work Product binding article-pdf differs from canonical matrix: expected disposition required with reason none, found disposition optional with reason none")
        standardvideoerrors should contain("profile standard-video Work Product binding video-storyboard differs from canonical matrix: expected disposition required with reason none, found disposition optional with reason none")
      }

      "resolve the standard and standard-video video branches from one definition" in {
        Given("the reusable document-production definition")
        val definition = CozyDocumentWorkflow.documentProduction

        When("the standard and standard-video profiles are resolved")
        val standard = _resolved("standard")
        val standardvideo = _resolved("standard-video")

        Then("standard visibly omits the video branch without a private descriptor DAG")
        standard.definition shouldBe definition
        standard.workProducts.filter(_.workProduct.id.startsWith("video-")).map(_.binding.disposition.value).toSet shouldBe Set("disabled")
        standard.workProducts.filter(_.workProduct.id.startsWith("video-")).flatMap(_.binding.reason).toSet shouldBe Set("profile standard disables video branch")

        And("standard-video activates the same branch without the disabled reason")
        standardvideo.definition shouldBe definition
        standardvideo.workProducts.filter(_.workProduct.id.startsWith("video-")).map(_.binding.disposition.value).toSet shouldBe Set("required")
        standardvideo.workProducts.filter(_.workProduct.id.startsWith("video-")).flatMap(_.binding.reason) shouldBe Vector.empty
      }
    }

    "report static workflow categories through plan without creating authority" in {
      _with_temp_dir("cozy-document-project-plan") { root =>
        Given("valid scaffolded standard and standard-video Document Projects")
        val standardparent = Files.createDirectory(root.resolve("standard-parent"))
        val videoparent = Files.createDirectory(root.resolve("video-parent"))
        _execute(List("document-project", "scaffold", "sample", "--profile", "standard", "--language", "en", "--workspace", "directory", "--save", standardparent.toString))
        _execute(List("document-project", "scaffold", "sample-video", "--profile", "standard-video", "--language", "en", "--workspace", "directory", "--save", videoparent.toString))
        val standard = standardparent.resolve("sample.dox")
        val video = videoparent.resolve("sample-video.dox")

        When("plan resolves each static profile without operation execution")
        val standardplan = _execute(List("document-project", "plan", standard.toString))
        val videoplan = _execute(List("document-project", "plan", video.toString))

        Then("the reports distinguish deterministic active, omitted, blocked, and eligible model categories")
        standardplan should include("active: work-product content-core [authority, required]")
        standardplan should include("omitted: work-product video-storyboard [plan, disabled: profile standard disables video branch]")
        standardplan should include("blocked: operation article.render-pdf [execution and Operation Attempts are reserved for Phase 42.1]")
        standardplan should include("eligible: operation article.render-pdf [provider: smartdox-rendering]")
        videoplan should include("active: work-product video-storyboard [plan, required]")
        videoplan should include("omitted: none")
        videoplan should not include "profile standard disables video branch"

        And("planning creates neither generated authority nor evidence")
        Files.exists(standard.resolve("target"), LinkOption.NOFOLLOW_LINKS) shouldBe false
        Files.exists(standard.resolve("state"), LinkOption.NOFOLLOW_LINKS) shouldBe false
        Files.exists(standard.resolve("operation-receipt-evidence"), LinkOption.NOFOLLOW_LINKS) shouldBe false
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

    "reject a scaffold parent reached through a symbolic-link ancestor at the path gate" in {
      _with_temp_dir("cozy-document-project-scaffold-symbolic-ancestor") { root =>
        Given("a real parent, a symbolic-link alias, and a child directory reached through that alias")
        val realparent = Files.createDirectory(root.resolve("real-parent"))
        val link = root.resolve("linked-parent")
        Files.createSymbolicLink(link, realparent)
        val child = Files.createDirectory(link.resolve("child"))

        When("scaffold receives the child parent path through the symbolic-link ancestor")
        val failure = _failure(List("document-project", "scaffold", "sample", "--profile", "standard", "--language", "en", "--workspace", "directory", "--save", child.toString))

        Then("the path gate emits only DP-PATH-001 and creates no package in the real target")
        _diagnostic_tokens(failure) shouldBe Vector("DP-PATH-001")
        Files.exists(realparent.resolve("child/sample.dox"), LinkOption.NOFOLLOW_LINKS) shouldBe false
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

    "admit declared run operations without executing or recording an attempt" in {
      _with_temp_dir("cozy-document-project-run") { root =>
        Given("an admitted standard Document Project")
        val parent = Files.createDirectory(root.resolve("parent"))
        _execute(List("document-project", "scaffold", "sample", "--profile", "standard", "--language", "en", "--workspace", "directory", "--save", parent.toString))
        val project = parent.resolve("sample.dox")

        When("run names declared, dry-run declared, and undeclared logical operations")
        val declared = _failure(List("document-project", "run", project.toString, "--operation", "article.render-pdf"))
        val dryrun = _failure(List("document-project", "run", project.toString, "--operation", "article.render-pdf", "--dry-run"))
        val undeclared = _failure(List("document-project", "run", project.toString, "--operation", "render"))

        Then("declared names stop at the Phase 42.1 execution boundary")
        declared should include("DP-OP-001")
        declared should include("execution and Operation Attempts are reserved for Phase 42.1")
        dryrun should include("DP-OP-001")
        dryrun should include("execution and Operation Attempts are reserved for Phase 42.1")

        And("an undeclared name remains a distinct declared-operation admission failure")
        undeclared should include("DP-OP-001")
        undeclared should include("undeclared logical operation: render")

        And("none of the admissions creates generated evidence")
        Files.exists(project.resolve("target"), LinkOption.NOFOLLOW_LINKS) shouldBe false
        Files.exists(project.resolve("state"), LinkOption.NOFOLLOW_LINKS) shouldBe false
        Files.exists(project.resolve("operation-receipt-evidence"), LinkOption.NOFOLLOW_LINKS) shouldBe false
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

  private def _resolved(profile: String): CozyDocumentWorkflow.ResolvedWorkflow =
    CozyDocumentWorkflow.resolve(profile) match {
      case Right(value) => value
      case Left(cause) => throw new RuntimeException(cause)
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
