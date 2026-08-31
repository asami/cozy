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
    "scaffold public no-video and video profile skeletons without fake outputs" in {
      _with_temp_dir("cozy-document-project-scaffold") { root =>
        Given("an existing direct parent and three absent Document Project packages")
        val standardparent = Files.createDirectory(root.resolve("standard-parent"))
        val videoparent = Files.createDirectory(root.resolve("video-parent"))
        val bokparent = Files.createDirectory(root.resolve("bok-parent"))

        When("the public profile commands scaffold their packages")
        val standardoutput = _execute(List("document-project", "scaffold", "standard-doc", "--profile", "standard", "--language", "en", "--workspace", "directory", "--save", standardparent.toString))
        val videooutput = _execute(List("document-project", "scaffold", "video-doc", "--profile", "standard-video", "--language", "ja", "--workspace", "bok", "--save", videoparent.toString))
        val bokoutput = _execute(List("document-project", "scaffold", "bok-doc", "--profile", "bok", "--language", "en", "--workspace", "bok", "--save", bokparent.toString))
        val standard = standardparent.resolve("standard-doc.dox")
        val video = videoparent.resolve("video-doc.dox")
        val bok = bokparent.resolve("bok-doc.dox")

        Then("each profile contains only its declared authored sources")
        _relative_files(standard) shouldBe Set(
          "document-project.yaml", "index.dox", "content/core-en.yaml", "infographic/infographic.svg",
          "presentation/visual-pages.yaml", "review/README.md"
        )
        _relative_files(video) shouldBe Set(
          "document-project.yaml", "index.dox", "content/core-ja.yaml", "infographic/infographic.svg",
          "presentation/visual-pages.yaml", "review/README.md", "video/storyboard.md"
        )
        _relative_files(bok) shouldBe _relative_files(standard)
        Files.readString(standard.resolve("content/core-en.yaml"), StandardCharsets.UTF_8) should include("accepted: []")
        Files.exists(standard.resolve("target"), LinkOption.NOFOLLOW_LINKS) shouldBe false
        Files.exists(standard.resolve("dashboard.html"), LinkOption.NOFOLLOW_LINKS) shouldBe false
        standardoutput should startWith("Cozy Document Project Scaffold")
        videooutput should include("workspace: bok")
        bokoutput should include("profile: bok")
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

        And("successful inspections and verification expose only a deterministic disposable state cache")
        val state = project.resolve("target/document-project/state.yaml")
        Files.isRegularFile(state, LinkOption.NOFOLLOW_LINKS) shouldBe true
        inspect should include("state: target/document-project/state.yaml")
        verify should include("state: target/document-project/state.yaml")
        val firststate = Files.readString(state, StandardCharsets.UTF_8)
        firststate should include("schema: cozy.document-project-state.v1")
        firststate should include("workProducts:")
        firststate should include("coverage: satisfied")
        firststate should include("currentness: current")
        firststate should include("review: pending")
        firststate should include("readiness: ready")
        firststate should include("coverage: not-applicable")
        firststate should include("readiness: omitted")
        firststate should include("reason: profile standard disables video branch")
        Files.exists(project.resolve("evidence/attempts"), LinkOption.NOFOLLOW_LINKS) shouldBe false
        val descriptor = project.resolve("document-project.yaml")
        val descriptorbytes = Files.readAllBytes(descriptor)
        val coretext = Files.readString(project.resolve("content/core-en.yaml"), StandardCharsets.UTF_8)

        When("inspect replaces a cache state entry hard linked to the authored descriptor")
        Files.delete(state)
        Files.createLink(state, descriptor)
        _execute(List("document-project", "inspect", project.toString))

        Then("the descriptor bytes remain authored evidence and the regenerated cache is independent")
        Files.readAllBytes(descriptor) shouldBe descriptorbytes
        Files.isSameFile(state, descriptor) shouldBe false
        Files.readString(state, StandardCharsets.UTF_8) should include("schema: cozy.document-project-state.v1")

        When("the cache is deleted and inspect reconstructs it")
        Files.delete(state)
        Files.delete(state.getParent)
        _execute(List("document-project", "inspect", project.toString))

        Then("the reconstructed cache remains deterministic and authored inputs remain unchanged")
        Files.readString(state, StandardCharsets.UTF_8) shouldBe firststate
        Files.readAllBytes(descriptor) shouldBe descriptorbytes
        Files.readString(project.resolve("content/core-en.yaml"), StandardCharsets.UTF_8) shouldBe coretext

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
        definition.workProducts.map(_.role.value).toSet shouldBe Set("authority", "plan", "candidate", "review-projection", "site-deliverable", "deliverable", "receipt")
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

      "resolve no-video and video branches, including the hidden profile, from one definition" in {
        Given("the reusable document-production definition")
        val definition = CozyDocumentWorkflow.documentProduction

        When("the public and hidden profiles are resolved")
        val standard = _resolved("standard")
        val standardvideo = _resolved("standard-video")
        val bok = _resolved("bok")
        val hidden = _resolved("simplemodeling-org-video")

        Then("standard visibly omits the video branch without a private descriptor DAG")
        standard.definition shouldBe definition
        val standardvideoids = Set("video-storyboard", "video-review", "video-deliverable", "video-logical-chart-html")
        standard.workProducts.filter(value => standardvideoids.contains(value.workProduct.id)).map(_.binding.disposition.value).toSet shouldBe Set("disabled")
        standard.workProducts.filter(value => standardvideoids.contains(value.workProduct.id)).flatMap(_.binding.reason).toSet shouldBe Set("profile standard disables video branch")
        bok.workProducts.map(_.binding.disposition) shouldBe standard.workProducts.map(_.binding.disposition)
        CozyDocumentWorkflow.isHiddenProfile(hidden.profile.id) shouldBe true

        And("standard-video activates the delivery branch and makes the video logical chart available")
        standardvideo.definition shouldBe definition
        standardvideo.workProducts.filter(value => Set("video-storyboard", "video-review", "video-deliverable").contains(value.workProduct.id)).map(_.binding.disposition.value).toSet shouldBe Set("required")
        standardvideo.workProducts.find(_.workProduct.id == "video-logical-chart-html").map(_.binding.disposition.value) shouldBe Some("optional")
        standardvideo.workProducts.filter(value => standardvideoids.contains(value.workProduct.id)).flatMap(_.binding.reason) shouldBe Vector.empty
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
        And("failed source validation creates no disposable state cache")
        Files.exists(project.resolve("target"), LinkOption.NOFOLLOW_LINKS) shouldBe false
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

    "generate deterministic dashboard and core review projections without changing authorities" in {
      _with_temp_dir("cozy-document-project-dashboard") { root =>
        Given("an admitted standard project with an HTML-sensitive accepted Core entry")
        val parent = Files.createDirectory(root.resolve("parent"))
        _execute(List("document-project", "scaffold", "sample", "--profile", "standard", "--language", "en", "--workspace", "directory", "--save", parent.toString))
        val project = parent.resolve("sample.dox")
        val core = project.resolve("content/core-en.yaml")
        Files.writeString(core, Files.readString(core, StandardCharsets.UTF_8).replace("accepted: []", "accepted:\n  - id: html-sensitive\n    text: \"<script>& text\""), StandardCharsets.UTF_8)
        val descriptorbytes = Files.readAllBytes(project.resolve("document-project.yaml"))
        val corebytes = Files.readAllBytes(core)
        val sourcebytes = Files.readAllBytes(project.resolve("index.dox"))

        When("dashboard is requested without an explicit output path and then repeated")
        val firstoutput = _execute(List("document-project", "dashboard", project.toString))
        val dashboard = project.resolve("target/document-project/project-dashboard.html")
        val firstbytes = Files.readAllBytes(dashboard)
        _execute(List("document-project", "dashboard", project.toString))
        val secondbytes = Files.readAllBytes(dashboard)

        Then("the default dashboard is self-contained, escaped, structurally accessible, and byte-identical")
        firstoutput should startWith("Cozy Document Project Dashboard")
        Files.isRegularFile(dashboard, LinkOption.NOFOLLOW_LINKS) shouldBe true
        firstbytes shouldBe secondbytes
        val dashboardtext = Files.readString(dashboard, StandardCharsets.UTF_8)
        dashboardtext should include("<h2>Workflow</h2>")
        dashboardtext should include("Branch (active/omitted)")
        dashboardtext should include(">active<")
        dashboardtext should include(">omitted<")
        dashboardtext should include("<h2>Work Product matrix</h2>")
        dashboardtext should include("<h2>Work Product details</h2>")
        dashboardtext should include("&lt;script&gt;&amp; text")
        dashboardtext should include("profile standard disables video branch")
        dashboardtext should include("not-applicable")
        dashboardtext should include("readiness")
        dashboardtext should include("Producer operation")
        dashboardtext should include("Consumer operations")
        dashboardtext should include("no receipt or currentness authority")
        And("disabled video products are not applicable in the dashboard matrix")
        Vector("video-storyboard", "video-review", "video-deliverable").foreach { id =>
          dashboardtext should include(s"$id</th><td>not-applicable</td><td>not-applicable</td><td>pending</td><td>omitted")
        }
        Files.exists(project.resolve("target/document-project/state.yaml"), LinkOption.NOFOLLOW_LINKS) shouldBe false
        Files.readAllBytes(project.resolve("document-project.yaml")) shouldBe descriptorbytes
        Files.readAllBytes(core) shouldBe corebytes
        Files.readAllBytes(project.resolve("index.dox")) shouldBe sourcebytes

        When("dashboard is requested with an explicit output path")
        val explicit = root.resolve("saved/dashboard.html")
        _execute(List("document-project", "dashboard", project.toString, "--save", explicit.toString))

        Then("the exact explicit path is the selected generated projection")
        Files.isRegularFile(explicit, LinkOption.NOFOLLOW_LINKS) shouldBe true
        Files.readAllBytes(explicit) shouldBe firstbytes

        When("dashboard is directed to an explicit HTML path under the project projection directory")
        val internal = project.resolve("target/document-project/explicit-dashboard.html")
        _execute(List("document-project", "dashboard", project.toString, "--save", internal.toString))

        Then("the permitted project-internal projection path is generated")
        Files.isRegularFile(internal, LinkOption.NOFOLLOW_LINKS) shouldBe true
        Files.readAllBytes(internal) shouldBe firstbytes

        When("an existing regular explicit destination is regenerated")
        val existingparent = Files.createDirectory(root.resolve("existing"))
        val existing = existingparent.resolve("dashboard.html")
        Files.writeString(existing, "old projection\n", StandardCharsets.UTF_8)
        _execute(List("document-project", "dashboard", project.toString, "--save", existing.toString))

        Then("the regular destination is atomically replaced with the same deterministic bytes")
        Files.readAllBytes(existing) shouldBe firstbytes
      }
    }

    "generate core and active video review projections without persistence" in {
      _with_temp_dir("cozy-document-project-review") { root =>
        Given("admitted standard and standard-video projects")
        val standardparent = Files.createDirectory(root.resolve("standard-parent"))
        val videoparent = Files.createDirectory(root.resolve("video-parent"))
        _execute(List("document-project", "scaffold", "core-doc", "--profile", "standard", "--language", "en", "--workspace", "directory", "--save", standardparent.toString))
        _execute(List("document-project", "scaffold", "video-doc", "--profile", "standard-video", "--language", "en", "--workspace", "directory", "--save", videoparent.toString))
        val standard = standardparent.resolve("core-doc.dox")
        val video = videoparent.resolve("video-doc.dox")
        val standardcore = standard.resolve("content/core-en.yaml")
        Files.writeString(standardcore, Files.readString(standardcore, StandardCharsets.UTF_8).replace("accepted: []", "accepted:\n  - id: accepted-entry\n    text: \"Accepted semantic statement\""), StandardCharsets.UTF_8)
        val standardcorebytes = Files.readAllBytes(standardcore)
        val storyboardbytes = Files.readAllBytes(video.resolve("video/storyboard.md"))
        val visualpagebytes = Files.readAllBytes(video.resolve("presentation/visual-pages.yaml"))

        When("core review and standard-video review are requested twice")
        _execute(List("document-project", "review", standard.toString, "--kind", "core"))
        val coreview = standard.resolve("target/document-project/core-review.html")
        val firstcorebytes = Files.readAllBytes(coreview)
        _execute(List("document-project", "review", standard.toString, "--kind", "core"))
        val explicitcoreview = root.resolve("saved/core-review.html")
        _execute(List("document-project", "review", standard.toString, "--kind", "core", "--save", explicitcoreview.toString))
        _execute(List("document-project", "review", video.toString, "--kind", "video"))
        val videoreview = video.resolve("target/document-project/video-review.html")
        val videoreviewtext = Files.readString(videoreview, StandardCharsets.UTF_8)

        Then("reviews are deterministic source projections with explicit non-authority boundaries")
        Files.readAllBytes(coreview) shouldBe firstcorebytes
        Files.isRegularFile(explicitcoreview, LinkOption.NOFOLLOW_LINKS) shouldBe true
        Files.readAllBytes(explicitcoreview) shouldBe firstcorebytes
        Files.readString(coreview, StandardCharsets.UTF_8) should include("Accepted Core entries")
        Files.readString(coreview, StandardCharsets.UTF_8) should include("<table aria-label=\"Accepted Core entries\">")
        Files.readString(coreview, StandardCharsets.UTF_8) should include("<th scope=\"col\">Entry ID</th>")
        Files.readString(coreview, StandardCharsets.UTF_8) should include("<th scope=\"col\">Text</th>")
        Files.readString(coreview, StandardCharsets.UTF_8) should include("Accepted semantic statement")
        Files.readString(coreview, StandardCharsets.UTF_8) should include("not yet persisted")
        Files.readString(coreview, StandardCharsets.UTF_8) should include("Candidate, feedback, and acceptance surface")
        Files.readString(coreview, StandardCharsets.UTF_8) should not include "logical-operation"
        videoreviewtext should include("Storyboard source projection")
        videoreviewtext should include("<table aria-label=\"Storyboard source projection\">")
        videoreviewtext should include("<table aria-label=\"Visual-page source projection\">")
        videoreviewtext should include("<th scope=\"col\">Source</th>")
        videoreviewtext should include("<th scope=\"col\">Content</th>")
        videoreviewtext should include("Visual-page source projection")
        videoreviewtext should include("Storyboard source belongs here.")
        videoreviewtext should include("pages: []")
        videoreviewtext should include("No provider")
        videoreviewtext should not include "video.render-review"
        Files.exists(standard.resolve("target/document-project/state.yaml"), LinkOption.NOFOLLOW_LINKS) shouldBe false
        Files.exists(video.resolve("target/document-project/state.yaml"), LinkOption.NOFOLLOW_LINKS) shouldBe false
        Files.exists(standard.resolve("evidence"), LinkOption.NOFOLLOW_LINKS) shouldBe false
        Files.exists(video.resolve("evidence"), LinkOption.NOFOLLOW_LINKS) shouldBe false
        Files.readAllBytes(standard.resolve("content/core-en.yaml")) shouldBe standardcorebytes
        Files.readAllBytes(video.resolve("video/storyboard.md")) shouldBe storyboardbytes
        Files.readAllBytes(video.resolve("presentation/visual-pages.yaml")) shouldBe visualpagebytes

        Given("an admitted standard project with no accepted Core entries")
        val emptycoreparent = Files.createDirectory(root.resolve("empty-core-parent"))
        _execute(List("document-project", "scaffold", "empty-core-doc", "--profile", "standard", "--language", "en", "--workspace", "directory", "--save", emptycoreparent.toString))
        val emptycoreproject = emptycoreparent.resolve("empty-core-doc.dox")

        When("the empty Core review is requested")
        _execute(List("document-project", "review", emptycoreproject.toString, "--kind", "core"))

        Then("the Core review exposes an accessible table state for no entries")
        val emptycoreviewtext = Files.readString(emptycoreproject.resolve("target/document-project/core-review.html"), StandardCharsets.UTF_8)
        emptycoreviewtext should include("<h2>Accepted Core entries</h2>")
        emptycoreviewtext should include("<table aria-label=\"Accepted Core entries\">")
        emptycoreviewtext should include("No accepted Core entries are present.")

        When("standard requests the disabled video review")
        val failure = _failure(List("document-project", "review", standard.toString, "--kind", "video"))

        Then("the existing operation/profile diagnostic is retained")
        _diagnostic_tokens(failure) shouldBe Vector("DP-OP-001")
        failure should include("logical operation video.render-review is disabled for profile standard")
      }
    }

    "generate a deterministic logical chart from the current project IR without persistence" in {
      _with_temp_dir("cozy-document-project-logical-chart") { root =>
        Given("standard and standard-video projects with HTML-sensitive Core, Visual Page, and storyboard IR")
        val standardparent = Files.createDirectory(root.resolve("standard-parent"))
        val videoparent = Files.createDirectory(root.resolve("video-parent"))
        _execute(List("document-project", "scaffold", "chart-doc", "--profile", "standard", "--language", "en", "--workspace", "directory", "--save", standardparent.toString))
        _execute(List("document-project", "scaffold", "chart-video", "--profile", "standard-video", "--language", "en", "--workspace", "directory", "--save", videoparent.toString))
        val standard = standardparent.resolve("chart-doc.dox")
        val video = videoparent.resolve("chart-video.dox")
        val standardcore = standard.resolve("content/core-en.yaml")
        val standardvisualpages = standard.resolve("presentation/visual-pages.yaml")
        val standardarticle = standard.resolve("index.dox")
        Files.writeString(standardcore, Files.readString(standardcore, StandardCharsets.UTF_8).replace("accepted: []", "accepted:\n  - id: chart-entry\n    text: \"<script>& core\""), StandardCharsets.UTF_8)
        Files.writeString(standardvisualpages, "pages: [\"<script>& visual\"]\n", StandardCharsets.UTF_8)
        Files.writeString(standardarticle, "article content stays outside the logical chart\n", StandardCharsets.UTF_8)
        val standardcorebytes = Files.readAllBytes(standardcore)
        val standardvisualbytes = Files.readAllBytes(standardvisualpages)
        val standardarticlebytes = Files.readAllBytes(standardarticle)

        When("the standard Slide Logical Chart is requested twice and saved at an exact path")
        val standardoutput = _execute(List("document-project", "review", standard.toString, "--kind", "slide-logical-chart"))
        val standardchart = standard.resolve("target/document-project/slide-logical-chart-review.html")
        val standardfirstbytes = Files.readAllBytes(standardchart)
        _execute(List("document-project", "review", standard.toString, "--kind", "slide-logical-chart"))
        val standardsecondbytes = Files.readAllBytes(standardchart)
        val explicit = root.resolve("saved/logical-chart.html")
        _execute(List("document-project", "review", standard.toString, "--kind", "slide-logical-chart", "--save", explicit.toString))

        Then("the standard chart is titled, escaped, deterministic, and limited to the slide IR")
        standardoutput should startWith("Cozy Document Project Slide Logical Chart")
        Files.readAllBytes(standardchart) shouldBe standardfirstbytes
        standardsecondbytes shouldBe standardfirstbytes
        Files.readAllBytes(explicit) shouldBe standardfirstbytes
        val standardcharttext = Files.readString(standardchart, StandardCharsets.UTF_8)
        standardcharttext should include("<title>Slide Logical Chart - chart-doc</title>")
        standardcharttext should include("<h1>Slide Logical Chart</h1>")
        standardcharttext should include("Accepted Core entries")
        And("the chart identifies the closed Logical Chart Work Product")
        standardcharttext should include("Work Product: <code>explanation-structure-review-html</code>; label: <span>Slide Logical Chart HTML</span>; operation: <code>slide-logical-chart.render-review</code>")
        standardcharttext should include("chart-entry")
        standardcharttext should include("&lt;script&gt;&amp; core")
        standardcharttext should include("Visual Page IR source")
        standardcharttext should include("&lt;script&gt;&amp; visual")
        standardcharttext should include("Video storyboard IR")
        standardcharttext should include("Not included in the Slide Logical Chart.")
        standardcharttext should not include ">missing<"
        standardcharttext should not include ">complete<"
        standardcharttext should include("not an authority, provider run, receipt, state cache, feedback record, or write-back mechanism")
        standardcharttext should not include("article content stays outside the logical chart")
        Files.exists(standard.resolve("target/document-project/state.yaml"), LinkOption.NOFOLLOW_LINKS) shouldBe false
        Files.exists(standard.resolve("evidence"), LinkOption.NOFOLLOW_LINKS) shouldBe false
        Files.readAllBytes(standardcore) shouldBe standardcorebytes
        Files.readAllBytes(standardvisualpages) shouldBe standardvisualbytes
        Files.readAllBytes(standardarticle) shouldBe standardarticlebytes

        val videocore = video.resolve("content/core-en.yaml")
        val videovisualpages = video.resolve("presentation/visual-pages.yaml")
        val videostoryboard = video.resolve("video/storyboard.md")
        Files.writeString(videocore, Files.readString(videocore, StandardCharsets.UTF_8).replace("accepted: []", "accepted:\n  - id: video-chart-entry\n    text: \"Video <script>& core\""), StandardCharsets.UTF_8)
        Files.writeString(videovisualpages, "pages: [\"<script>& video visual\"]\n", StandardCharsets.UTF_8)
        Files.writeString(videostoryboard, "# Storyboard\n\n<script>& storyboard\n", StandardCharsets.UTF_8)
        val videocorebytes = Files.readAllBytes(videocore)
        val videovisualbytes = Files.readAllBytes(videovisualpages)
        val videostoryboardbytes = Files.readAllBytes(videostoryboard)

        When("the standard-video Video Logical Chart is requested twice")
        _execute(List("document-project", "review", video.toString, "--kind", "video-logical-chart"))
        val videochart = video.resolve("target/document-project/video-logical-chart-review.html")
        val videofirstbytes = Files.readAllBytes(videochart)
        _execute(List("document-project", "review", video.toString, "--kind", "video-logical-chart"))

        Then("the active chart includes the storyboard and Visual Page IR without changing sources")
        Files.readAllBytes(videochart) shouldBe videofirstbytes
        val videocharttext = Files.readString(videochart, StandardCharsets.UTF_8)
        videocharttext should include("video-chart-entry")
        videocharttext should include("Video &lt;script&gt;&amp; core")
        videocharttext should include("&lt;script&gt;&amp; video visual")
        videocharttext should include("&lt;script&gt;&amp; storyboard")
        videocharttext should include("<h1>Video Logical Chart</h1>")
        videocharttext should include("video-logical-chart.render-review")
        Files.exists(video.resolve("target/document-project/state.yaml"), LinkOption.NOFOLLOW_LINKS) shouldBe false
        Files.exists(video.resolve("evidence"), LinkOption.NOFOLLOW_LINKS) shouldBe false
        Files.readAllBytes(videocore) shouldBe videocorebytes
        Files.readAllBytes(videovisualpages) shouldBe videovisualbytes
        Files.readAllBytes(videostoryboard) shouldBe videostoryboardbytes

        When("the standard-video dashboard is projected")
        _execute(List("document-project", "dashboard", video.toString))

        Then("slide and video logical-chart Work Products are independently current from their IR inputs")
        val dashboardtext = Files.readString(video.resolve("target/document-project/project-dashboard.html"), StandardCharsets.UTF_8)
        dashboardtext should include("explanation-structure-review-html<br/><span>Slide Logical Chart HTML</span></th><td>active</td><td>review-projection</td><td>optional</td>")
        dashboardtext should include("explanation-structure-review-html</th><td>satisfied</td><td>current</td><td>pending</td><td>ready")
        dashboardtext should include("video-logical-chart-html<br/><span>Video Logical Chart HTML</span></th><td>active</td><td>review-projection</td><td>optional</td>")
        dashboardtext should include("video-logical-chart-html</th><td>satisfied</td><td>current</td><td>pending</td><td>ready")
        dashboardtext should include("phase-41-explanation-structure")
        dashboardtext should include("content-core")
        dashboardtext should include("slide-logical-chart")
        dashboardtext should include("slide-logical-chart-reference")
      }
    }

    "reflect mixed feedback dispositions with explicit reasons and bounded authority writes" in {
      _with_temp_dir("cozy-document-project-feedback") { root =>
        Given("an admitted standard project and a direct JSON feedback batch")
        val parent = Files.createDirectory(root.resolve("parent"))
        _execute(List("document-project", "scaffold", "sample", "--profile", "standard", "--language", "en", "--workspace", "directory", "--save", parent.toString))
        val project = parent.resolve("sample.dox")
        val core = project.resolve("content/core-en.yaml")
        val article = project.resolve("index.dox")
        val slides = project.resolve("presentation/visual-pages.yaml")
        val infographic = project.resolve("infographic/infographic.svg")
        val corebefore = Files.readAllBytes(core)
        val articlebefore = Files.readAllBytes(article)
        val slidesbefore = Files.readAllBytes(slides)
        val feedback = root.resolve("feedback.json")
        Files.writeString(feedback, """{
          |  "reason": "first review batch",
          |  "changes": [
          |    {"target":"core","replacement":{"accepted":[{"id":"claim-1","text":"Accepted claim"}]},"applicability":"applicable","disposition":"accepted"},
          |    {"target":"article","replacement":"proposed article","applicability":"applicable","disposition":"rejected","rejectionReason":"article needs another pass"},
          |    {"target":"slides","replacement":"proposed slides","applicability":"not-applicable","disposition":"not-applicable","notApplicableReason":"slides are deferred"},
          |    {"target":"infographic","replacement":"<svg>accepted</svg>\n","applicability":"applicable","disposition":"accepted"},
          |    {"target":"video","replacement":"proposed storyboard","applicability":"not-applicable","disposition":"not-applicable","notApplicableReason":"profile does not use video"}
          |  ]
          |}""".stripMargin, StandardCharsets.UTF_8)

        When("the mixed feedback batch is reflected")
        val output = _execute(List("document-project", "reflect-feedback", project.toString, feedback.toString))

        Then("accepted items write their mapped authority and every item reports its disposition")
        output should startWith("Cozy Document Project Feedback Reflection")
        output should include("reflected: core content/core-en.yaml")
        output should include("rejected: article — article needs another pass")
        output should include("not-applicable: slides — slides are deferred")
        output should include("reflected: infographic infographic/infographic.svg")
        output should include("not-applicable: video — profile does not use video")
        output should not include "first review batch"
        output should not include "proposed article"
        output should not include "proposed storyboard"
        Files.readString(core, StandardCharsets.UTF_8) should include("claim-1")
        Files.readString(core, StandardCharsets.UTF_8) should include("Accepted claim")
        Files.readString(core, StandardCharsets.UTF_8) should include("schema: \"cozy.content-core.v1\"")
        Files.readString(core, StandardCharsets.UTF_8) should include("id: \"sample:core:en\"")
        Files.readString(core, StandardCharsets.UTF_8) should include("language: \"en\"")
        Files.readAllBytes(article) shouldBe articlebefore
        Files.readAllBytes(slides) shouldBe slidesbefore
        Files.readString(infographic, StandardCharsets.UTF_8) shouldBe "<svg>accepted</svg>\n"
        Files.readAllBytes(core) should not equal corebefore
        Files.exists(project.resolve("target"), LinkOption.NOFOLLOW_LINKS) shouldBe false
        Files.exists(project.resolve("evidence"), LinkOption.NOFOLLOW_LINKS) shouldBe false

        Given("the same standard project and an amended batch that accepts the earlier article proposal")
        val amended = root.resolve("amended-feedback.json")
        Files.writeString(amended, """{
          |  "reason": "amended decision",
          |  "changes": [
          |    {"target":"article","replacement":"accepted article source\n","applicability":"applicable","disposition":"accepted"},
          |    {"target":"video","replacement":"still no video","applicability":"not-applicable","disposition":"not-applicable","notApplicableReason":"profile does not use video"}
          |  ]
          |}""".stripMargin, StandardCharsets.UTF_8)

        When("the amended batch is reflected")
        val amendedoutput = _execute(List("document-project", "reflect-feedback", project.toString, amended.toString))

        Then("the formerly rejected proposal can be accepted later without persisting feedback state")
        amendedoutput should include("reflected: article index.dox")
        Files.readString(article, StandardCharsets.UTF_8) shouldBe "accepted article source\n"
        Files.exists(project.resolve("target"), LinkOption.NOFOLLOW_LINKS) shouldBe false
        Files.exists(project.resolve("evidence"), LinkOption.NOFOLLOW_LINKS) shouldBe false
      }
    }

    "reflect YAML feedback through the common structured schema" in {
      _with_temp_dir("cozy-document-project-feedback-yaml") { root =>
        Given("an admitted standard project and a direct YAML feedback batch")
        val parent = Files.createDirectory(root.resolve("parent"))
        _execute(List("document-project", "scaffold", "sample", "--profile", "standard", "--language", "en", "--workspace", "directory", "--save", parent.toString))
        val project = parent.resolve("sample.dox")
        val core = project.resolve("content/core-en.yaml")
        val article = project.resolve("index.dox")
        val slides = project.resolve("presentation/visual-pages.yaml")
        val corebefore = Files.readAllBytes(core)
        val articlebefore = Files.readAllBytes(article)
        val slidesbefore = Files.readAllBytes(slides)
        val feedback = root.resolve("feedback.yaml")
        Files.writeString(feedback, """reason: yaml review batch
          |changes:
          |  - target: core
          |    replacement:
          |      accepted:
          |        - id: yaml-claim
          |          text: Accepted YAML claim
          |    applicability: applicable
          |    disposition: accepted
          |  - target: article
          |    replacement: proposed YAML article
          |    applicability: applicable
          |    disposition: rejected
          |    rejectionReason: article needs another pass
          |  - target: slides
          |    replacement: proposed YAML slides
          |    applicability: not-applicable
          |    disposition: not-applicable
          |    notApplicableReason: slides are deferred
          |  - target: video
          |    replacement: proposed YAML storyboard
          |    applicability: not-applicable
          |    disposition: not-applicable
          |    notApplicableReason: profile does not use video
          |""".stripMargin, StandardCharsets.UTF_8)

        When("the YAML feedback batch is reflected")
        val output = _execute(List("document-project", "reflect-feedback", project.toString, feedback.toString))

        Then("YAML has the same disposition output and bounded authority behavior as JSON")
        output should startWith("Cozy Document Project Feedback Reflection")
        output should include("reflected: core content/core-en.yaml")
        output should include("rejected: article — article needs another pass")
        output should include("not-applicable: slides — slides are deferred")
        output should include("not-applicable: video — profile does not use video")
        output should not include "yaml review batch"
        output should not include "proposed YAML article"
        output should not include "proposed YAML storyboard"
        Files.readString(core, StandardCharsets.UTF_8) should include("yaml-claim")
        Files.readAllBytes(core) should not equal corebefore
        Files.readAllBytes(article) shouldBe articlebefore
        Files.readAllBytes(slides) shouldBe slidesbefore
        Files.exists(project.resolve("target"), LinkOption.NOFOLLOW_LINKS) shouldBe false
        Files.exists(project.resolve("evidence"), LinkOption.NOFOLLOW_LINKS) shouldBe false
      }
    }

    "map every accepted feedback target, including the active video source" in {
      _with_temp_dir("cozy-document-project-feedback-targets") { root =>
        Given("an admitted standard-video project and one accepted replacement for each authority mapping")
        val parent = Files.createDirectory(root.resolve("parent"))
        _execute(List("document-project", "scaffold", "video", "--profile", "standard-video", "--language", "en", "--workspace", "directory", "--save", parent.toString))
        val project = parent.resolve("video.dox")
        val feedback = root.resolve("all-targets.json")
        Files.writeString(feedback, """{
          |  "reason": "accept all authorities",
          |  "changes": [
          |    {"target":"core","replacement":{"accepted":[]},"applicability":"applicable","disposition":"accepted"},
          |    {"target":"article","replacement":"article\n","applicability":"applicable","disposition":"accepted"},
          |    {"target":"slides","replacement":"slides\n","applicability":"applicable","disposition":"accepted"},
          |    {"target":"infographic","replacement":"infographic\n","applicability":"applicable","disposition":"accepted"},
          |    {"target":"video","replacement":"storyboard\n","applicability":"applicable","disposition":"accepted"}
          |  ]
          |}""".stripMargin, StandardCharsets.UTF_8)

        When("all accepted feedback targets are reflected")
        val output = _execute(List("document-project", "reflect-feedback", project.toString, feedback.toString))

        Then("each target maps to its exact in-project authority path")
        output should include("reflected: core content/core-en.yaml")
        output should include("reflected: article index.dox")
        output should include("reflected: slides presentation/visual-pages.yaml")
        output should include("reflected: infographic infographic/infographic.svg")
        output should include("reflected: video video/storyboard.md")
        Files.readString(project.resolve("index.dox"), StandardCharsets.UTF_8) shouldBe "article\n"
        Files.readString(project.resolve("presentation/visual-pages.yaml"), StandardCharsets.UTF_8) shouldBe "slides\n"
        Files.readString(project.resolve("infographic/infographic.svg"), StandardCharsets.UTF_8) shouldBe "infographic\n"
        Files.readString(project.resolve("video/storyboard.md"), StandardCharsets.UTF_8) shouldBe "storyboard\n"
        Files.exists(project.resolve("target"), LinkOption.NOFOLLOW_LINKS) shouldBe false
        Files.exists(project.resolve("evidence"), LinkOption.NOFOLLOW_LINKS) shouldBe false
      }
    }

    "prevalidate feedback combinations, replacements, profile rules, and unsafe input without writes" in {
      _with_temp_dir("cozy-document-project-feedback-invalid") { root =>
        Given("an admitted standard project and unchanged authority bytes")
        val parent = Files.createDirectory(root.resolve("parent"))
        _execute(List("document-project", "scaffold", "sample", "--profile", "standard", "--language", "en", "--workspace", "directory", "--save", parent.toString))
        val project = parent.resolve("sample.dox")
        val core = project.resolve("content/core-en.yaml")
        val article = project.resolve("index.dox")
        val corebefore = Files.readAllBytes(core)
        val articlebefore = Files.readAllBytes(article)

        When("an incompatible applicability/disposition pair is supplied")
        val incompatible = root.resolve("incompatible.json")
        Files.writeString(incompatible, """{"reason":"invalid","changes":[{"target":"article","replacement":"new","applicability":"not-applicable","disposition":"accepted"}]}""", StandardCharsets.UTF_8)
        val incompatiblefailure = _failure(List("document-project", "reflect-feedback", project.toString, incompatible.toString))

        Then("the grammar diagnostic is returned before any authority write")
        _diagnostic_tokens(incompatiblefailure) shouldBe Vector("DP-CLI-001")
        Files.readAllBytes(core) shouldBe corebefore
        Files.readAllBytes(article) shouldBe articlebefore
        Files.exists(project.resolve("target"), LinkOption.NOFOLLOW_LINKS) shouldBe false

        When("a Core replacement repeats an entry identity")
        val duplicatecore = root.resolve("duplicate-core.json")
        Files.writeString(duplicatecore, """{"reason":"invalid","changes":[{"target":"core","replacement":{"accepted":[{"id":"same","text":"one"},{"id":"same","text":"two"}]},"applicability":"applicable","disposition":"accepted"},{"target":"video","replacement":"none","applicability":"not-applicable","disposition":"not-applicable","notApplicableReason":"profile does not use video"}]}""", StandardCharsets.UTF_8)
        val duplicatefailure = _failure(List("document-project", "reflect-feedback", project.toString, duplicatecore.toString))

        Then("the descriptor diagnostic is returned and the sources remain unchanged")
        _diagnostic_tokens(duplicatefailure) shouldBe Vector("DP-DESC-001")
        Files.readAllBytes(core) shouldBe corebefore
        Files.readAllBytes(article) shouldBe articlebefore

        When("standard receives an applicable video item after an accepted Core proposal")
        val videofailurebatch = root.resolve("video-applicable.json")
        Files.writeString(videofailurebatch, """{"reason":"invalid profile","changes":[{"target":"core","replacement":{"accepted":[{"id":"would-not-write","text":"proposal"}]},"applicability":"applicable","disposition":"accepted"},{"target":"video","replacement":"video","applicability":"applicable","disposition":"rejected","rejectionReason":"disabled"}]}""", StandardCharsets.UTF_8)
        val videofailure = _failure(List("document-project", "reflect-feedback", project.toString, videofailurebatch.toString))

        Then("the profile operation diagnostic wins before all writes")
        _diagnostic_tokens(videofailure) shouldBe Vector("DP-OP-001")
        Files.readAllBytes(core) shouldBe corebefore
        Files.exists(project.resolve("target"), LinkOption.NOFOLLOW_LINKS) shouldBe false

        When("a symbolic-link feedback input is supplied")
        val external = root.resolve("external.json")
        Files.writeString(external, "{}", StandardCharsets.UTF_8)
        val linked = root.resolve("linked-feedback.json")
        Files.createSymbolicLink(linked, external)
        val pathfailure = _failure(List("document-project", "reflect-feedback", project.toString, linked.toString))

        Then("unsafe input is rejected without changing an authority")
        _diagnostic_tokens(pathfailure) shouldBe Vector("DP-PATH-001")
        Files.readAllBytes(core) shouldBe corebefore
        Files.readAllBytes(article) shouldBe articlebefore

        When("an accepted authority source is replaced by a symbolic link")
        val externalarticle = root.resolve("external-article.dox")
        Files.writeString(externalarticle, "external article\n", StandardCharsets.UTF_8)
        Files.delete(article)
        Files.createSymbolicLink(article, externalarticle)
        val sourcebatch = root.resolve("unsafe-source.json")
        Files.writeString(sourcebatch, """{"reason":"unsafe source","changes":[{"target":"article","replacement":"new article","applicability":"applicable","disposition":"accepted"},{"target":"video","replacement":"none","applicability":"not-applicable","disposition":"not-applicable","notApplicableReason":"profile does not use video"}]}""", StandardCharsets.UTF_8)
        val sourcefailure = _failure(List("document-project", "reflect-feedback", project.toString, sourcebatch.toString))

        Then("the unsafe source is rejected before any replacement")
        _diagnostic_tokens(sourcefailure) shouldBe Vector("DP-PATH-001")
        Files.readAllBytes(core) shouldBe corebefore
        Files.readString(externalarticle, StandardCharsets.UTF_8) shouldBe "external article\n"

        When("a feedback file has an unsupported suffix")
        val unsupported = root.resolve("unsupported.conf")
        Files.writeString(unsupported, "not valid feedback", StandardCharsets.UTF_8)
        val unsupportedfailure = _failure(List("document-project", "reflect-feedback", project.toString, unsupported.toString))

        Then("the suffix is rejected before structured parsing without changing an authority")
        _diagnostic_tokens(unsupportedfailure) shouldBe Vector("DP-CLI-001")
        Files.readAllBytes(core) shouldBe corebefore

        When("feedback JSON or YAML syntax is malformed")
        val malformedjson = root.resolve("malformed.json")
        Files.writeString(malformedjson, "{\"reason\": \"missing close\"", StandardCharsets.UTF_8)
        val malformedjsonfailure = _failure(List("document-project", "reflect-feedback", project.toString, malformedjson.toString))
        val malformedyaml = root.resolve("malformed.yaml")
        Files.writeString(malformedyaml, "reason: [missing close", StandardCharsets.UTF_8)
        val malformedyamlfailure = _failure(List("document-project", "reflect-feedback", project.toString, malformedyaml.toString))

        Then("both syntax failures use the feedback grammar diagnostic without changing an authority")
        _diagnostic_tokens(malformedjsonfailure) shouldBe Vector("DP-CLI-001")
        _diagnostic_tokens(malformedyamlfailure) shouldBe Vector("DP-CLI-001")
        Files.readAllBytes(core) shouldBe corebefore
        Files.readString(externalarticle, StandardCharsets.UTF_8) shouldBe "external article\n"
      }
    }

    "reject unsafe dashboard output destinations without direct-write fallback" in {
      _with_temp_dir("cozy-document-project-dashboard-path") { root =>
        Given("an admitted standard project, a real output parent, and a symbolic-link alias")
        val parent = Files.createDirectory(root.resolve("parent"))
        _execute(List("document-project", "scaffold", "sample", "--profile", "standard", "--language", "en", "--workspace", "directory", "--save", parent.toString))
        val project = parent.resolve("sample.dox")
        val realoutput = Files.createDirectory(root.resolve("real-output"))
        val linkedoutput = root.resolve("linked-output")
        Files.createSymbolicLink(linkedoutput, realoutput)
        val linkedparentdestination = linkedoutput.resolve("dashboard.html")
        val external = root.resolve("external-dashboard.html")
        Files.writeString(external, "external\n", StandardCharsets.UTF_8)
        val symlinkdestination = root.resolve("symlink-dashboard.html")
        Files.createSymbolicLink(symlinkdestination, external)
        val fileparent = root.resolve("file-output")
        Files.writeString(fileparent, "not a directory\n", StandardCharsets.UTF_8)
        val fileparentdestination = fileparent.resolve("dashboard.html")

        When("dashboard is directed through a symbolic-link parent, non-directory parent, or symbolic-link destination")
        val parentfailure = _failure(List("document-project", "dashboard", project.toString, "--save", linkedparentdestination.toString))
        val fileparentfailure = _failure(List("document-project", "dashboard", project.toString, "--save", fileparentdestination.toString))
        val destinationfailure = _failure(List("document-project", "dashboard", project.toString, "--save", symlinkdestination.toString))

        Then("all unsafe paths reject with DP-PATH-001 and publish no projection")
        _diagnostic_tokens(parentfailure) shouldBe Vector("DP-PATH-001")
        _diagnostic_tokens(fileparentfailure) shouldBe Vector("DP-PATH-001")
        _diagnostic_tokens(destinationfailure) shouldBe Vector("DP-PATH-001")
        Files.exists(linkedparentdestination, LinkOption.NOFOLLOW_LINKS) shouldBe false
        Files.readString(external, StandardCharsets.UTF_8) shouldBe "external\n"
      }
    }

    "reject Project-internal projection collisions before creating parents or replacing authorities" in {
      _with_temp_dir("cozy-document-project-projection-collision") { root =>
        Given("an admitted standard-video project and direct authored authorities")
        val parent = Files.createDirectory(root.resolve("parent"))
        _execute(List("document-project", "scaffold", "collision", "--profile", "standard-video", "--language", "en", "--workspace", "directory", "--save", parent.toString))
        val project = parent.resolve("collision.dox")
        val descriptor = project.resolve("document-project.yaml")
        val core = project.resolve("content/core-en.yaml")
        val article = project.resolve("index.dox")
        val visualpages = project.resolve("presentation/visual-pages.yaml")
        val descriptorbytes = Files.readAllBytes(descriptor)
        val corebytes = Files.readAllBytes(core)
        val articlebytes = Files.readAllBytes(article)
        val visualpagebytes = Files.readAllBytes(visualpages)

        When("each projection command is directed at an authored Project authority")
        val dashboardfailure = _failure(List("document-project", "dashboard", project.toString, "--save", descriptor.toString))
        val corereviewfailure = _failure(List("document-project", "review", project.toString, "--kind", "core", "--save", core.toString))
        val videoreviewfailure = _failure(List("document-project", "review", project.toString, "--kind", "video", "--save", article.toString))
        val logicalchartfailure = _failure(List("document-project", "review", project.toString, "--kind", "slide-logical-chart", "--save", visualpages.toString))

        Then("dashboard and every review kind reject with one path diagnostic before publication")
        Vector(dashboardfailure, corereviewfailure, videoreviewfailure, logicalchartfailure).foreach { failure =>
          _diagnostic_tokens(failure) shouldBe Vector("DP-PATH-001")
        }
        Files.readAllBytes(descriptor) shouldBe descriptorbytes
        Files.readAllBytes(core) shouldBe corebytes
        Files.readAllBytes(article) shouldBe articlebytes
        Files.readAllBytes(visualpages) shouldBe visualpagebytes

        When("an in-project state or evidence path is selected before its parent exists")
        val state = project.resolve("target/document-project/state.yaml")
        val attempt = project.resolve("evidence/attempts/projection.html")
        val statefailure = _failure(List("document-project", "dashboard", project.toString, "--save", state.toString))
        val attemptfailure = _failure(List("document-project", "review", project.toString, "--kind", "core", "--save", attempt.toString))

        Then("state and evidence paths reject before creating target or evidence parents")
        _diagnostic_tokens(statefailure) shouldBe Vector("DP-PATH-001")
        _diagnostic_tokens(attemptfailure) shouldBe Vector("DP-PATH-001")
        Files.exists(project.resolve("target"), LinkOption.NOFOLLOW_LINKS) shouldBe false
        Files.exists(project.resolve("evidence"), LinkOption.NOFOLLOW_LINKS) shouldBe false
        Files.exists(project.resolve("evidence/attempts"), LinkOption.NOFOLLOW_LINKS) shouldBe false
      }
    }

    "keep strict grammar and missing-value diagnostics distinct" in {
      Given("the frozen public grammar")

      When("an unsupported spelling, an unknown command, and known forms missing required values are parsed")
      val spelling = _failure(List("document-project", "inspect", "sample.dox", "--save=out.html"))
      val unknown = _failure(List("document-project", "unknown", "sample.dox"))
      val missingproject = _failure(List("document-project", "verify"))
      val missingoperation = _failure(List("document-project", "run", "sample.dox"))
      val missingreviewkind = _failure(List("document-project", "review", "sample.dox"))
      val invalidreviewkind = _failure(List("document-project", "review", "sample.dox", "--kind", "logical-chart"))
      val reviewoperation = _failure(List("document-project", "review", "sample.dox", "--kind", "core", "--operation", "article.render-pdf"))
      val missingfeedback = _failure(List("document-project", "reflect-feedback", "sample.dox"))
      val extrafeedback = _failure(List("document-project", "reflect-feedback", "sample.dox", "feedback.json", "extra"))

      Then("grammar failures precede only with DP-CLI-001 and missing forms use DP-CLI-002")
      spelling should include("DP-CLI-001")
      unknown should include("DP-CLI-001")
      missingproject should include("DP-CLI-002")
      missingoperation should include("DP-CLI-002")
      missingreviewkind should include("DP-CLI-002")
      invalidreviewkind should include("DP-CLI-001")
      reviewoperation should include("DP-CLI-001")
      missingfeedback should include("DP-CLI-002")
      extrafeedback should include("DP-CLI-001")
    }

    "record one immutable attempt for each eligible run without executing a provider" in {
      _with_temp_dir("cozy-document-project-run") { root =>
        Given("an admitted standard Document Project")
        val parent = Files.createDirectory(root.resolve("parent"))
        _execute(List("document-project", "scaffold", "sample", "--profile", "standard", "--language", "en", "--workspace", "directory", "--save", parent.toString))
        val project = parent.resolve("sample.dox")
        val attempts = project.resolve("evidence/attempts")
        val authored = project.resolve("index.dox")
        val authoredbytes = Files.readAllBytes(authored)

        When("dry-run admits the declared operation")
        val dryrun = _execute(List("document-project", "run", project.toString, "--operation", "article.render-pdf", "--dry-run"))

        Then("dry-run reports the selected operation and does not persist any evidence")
        dryrun should include("operation: article.render-pdf")
        dryrun should include("provider: smartdox-rendering")
        dryrun should include("profile: standard")
        dryrun should include("outcome: not-recorded")
        Files.exists(attempts, LinkOption.NOFOLLOW_LINKS) shouldBe false
        Files.exists(project.resolve("target"), LinkOption.NOFOLLOW_LINKS) shouldBe false

        When("an eligible operation is run twice")
        val firstoutput = _execute(List("document-project", "run", project.toString, "--operation", "article.render-pdf"))
        val firstattempt = project.resolve(firstoutput.linesIterator.find(_.startsWith("attempt: ")).get.stripPrefix("attempt: "))
        val firstbytes = Files.readAllBytes(firstattempt)
        val secondoutput = _execute(List("document-project", "run", project.toString, "--operation", "article.render-pdf"))
        val secondattempt = project.resolve(secondoutput.linesIterator.find(_.startsWith("attempt: ")).get.stripPrefix("attempt: "))

        Then("each run records one canonical attempt and preserves the first immutable bytes")
        firstoutput should include("outcome: recorded")
        secondoutput should include("outcome: recorded")
        firstattempt should not equal secondattempt
        Files.readAllBytes(firstattempt) shouldBe firstbytes
        Files.readAllBytes(authored) shouldBe authoredbytes
        _relative_files(attempts).size shouldBe 2
        val attempttext = Files.readString(firstattempt, StandardCharsets.UTF_8)
        attempttext.linesIterator.filterNot(_.startsWith(" ")).map(_.takeWhile(_ != ':')).toVector shouldBe Vector(
          "schema", "id", "operation", "provider", "profile", "inputs", "outcome", "diagnostics", "outputs", "receipt"
        )
        attempttext.linesIterator.take(10).toVector shouldBe Vector(
          "schema: cozy.document-operation-attempt.v1",
          attempttext.linesIterator.drop(1).next(),
          "operation: article.render-pdf",
          "provider: smartdox-rendering",
          "profile: standard",
          "inputs:",
          "  - path: document-project.yaml",
          attempttext.linesIterator.drop(7).next(),
          "  - path: content/core-en.yaml",
          attempttext.linesIterator.drop(9).next()
        )
        attempttext should include("outcome: recorded")
        attempttext should include("provider execution is deferred; this dispatch was recorded only")
        attempttext should include("outputs: []")
        attempttext should include("receipt: none")
        attempttext should include("path: index.dox")
        attempttext should include("path: infographic/infographic.svg")
        attempttext should include("path: presentation/visual-pages.yaml")
        attempttext should include("path: review/README.md")

        And("recorded dispatch creates no generated state or deliverable")
        Files.exists(project.resolve("target"), LinkOption.NOFOLLOW_LINKS) shouldBe false
        Files.exists(project.resolve("state"), LinkOption.NOFOLLOW_LINKS) shouldBe false
        Files.exists(project.resolve("operation-receipt-evidence"), LinkOption.NOFOLLOW_LINKS) shouldBe false
      }
    }

    "reject unknown and profile-disabled operations without creating attempts" in {
      _with_temp_dir("cozy-document-project-run-admission") { root =>
        Given("admitted standard and standard-video Document Projects")
        val standardparent = Files.createDirectory(root.resolve("standard-parent"))
        val videoparent = Files.createDirectory(root.resolve("video-parent"))
        _execute(List("document-project", "scaffold", "standard", "--profile", "standard", "--language", "en", "--workspace", "directory", "--save", standardparent.toString))
        _execute(List("document-project", "scaffold", "video", "--profile", "standard-video", "--language", "en", "--workspace", "directory", "--save", videoparent.toString))
        val standard = standardparent.resolve("standard.dox")
        val video = videoparent.resolve("video.dox")

        When("unknown and disabled operations are requested")
        val unknown = _failure(List("document-project", "run", standard.toString, "--operation", "render"))
        val disabled = _failure(List("document-project", "run", standard.toString, "--operation", "video.render-review"))

        Then("both admissions reject with DP-OP-001 and create no evidence")
        unknown should include("DP-OP-001")
        unknown should include("undeclared logical operation: render")
        disabled should include("DP-OP-001")
        disabled should include("disabled for profile standard")
        Files.exists(standard.resolve("evidence"), LinkOption.NOFOLLOW_LINKS) shouldBe false
        Files.exists(video.resolve("evidence"), LinkOption.NOFOLLOW_LINKS) shouldBe false
      }
    }

    "reject unsafe initial run input before writing an attempt" in {
      _with_temp_dir("cozy-document-project-run-input") { root =>
        Given("a standard scaffold whose required initial source is replaced by a symbolic link")
        val parent = Files.createDirectory(root.resolve("parent"))
        _execute(List("document-project", "scaffold", "sample", "--profile", "standard", "--language", "en", "--workspace", "directory", "--save", parent.toString))
        val project = parent.resolve("sample.dox")
        val external = root.resolve("external-index.dox")
        Files.writeString(external, "external source\n", StandardCharsets.UTF_8)
        Files.delete(project.resolve("index.dox"))
        Files.createSymbolicLink(project.resolve("index.dox"), external)

        When("an eligible run is requested")
        val failure = _failure(List("document-project", "run", project.toString, "--operation", "article.render-pdf"))

        Then("path admission wins and no attempt directory is created")
        _diagnostic_tokens(failure) shouldBe Vector("DP-PATH-001")
        Files.exists(project.resolve("evidence"), LinkOption.NOFOLLOW_LINKS) shouldBe false
      }
    }

    "reject malformed run input before writing an attempt" in {
      _with_temp_dir("cozy-document-project-run-malformed") { root =>
        Given("a standard scaffold whose descriptor has an unknown closed field")
        val parent = Files.createDirectory(root.resolve("parent"))
        _execute(List("document-project", "scaffold", "sample", "--profile", "standard", "--language", "en", "--workspace", "directory", "--save", parent.toString))
        val project = parent.resolve("sample.dox")
        val descriptor = project.resolve("document-project.yaml")
        Files.writeString(descriptor, Files.readString(descriptor, StandardCharsets.UTF_8) + "unknown: value\n", StandardCharsets.UTF_8)

        When("an eligible run is requested")
        val failure = _failure(List("document-project", "run", project.toString, "--operation", "article.render-pdf"))

        Then("descriptor validation rejects before evidence publication")
        failure should include("DP-DESC-002")
        Files.exists(project.resolve("evidence"), LinkOption.NOFOLLOW_LINKS) shouldBe false
      }
    }

    "record the activated video source identity without executing its provider" in {
      _with_temp_dir("cozy-document-project-run-video") { root =>
        Given("an admitted standard-video Document Project")
        val parent = Files.createDirectory(root.resolve("parent"))
        _execute(List("document-project", "scaffold", "sample", "--profile", "standard-video", "--language", "en", "--workspace", "directory", "--save", parent.toString))
        val project = parent.resolve("sample.dox")

        When("the activated video operation is recorded")
        val output = _execute(List("document-project", "run", project.toString, "--operation", "video.render-review"))
        val attempt = project.resolve(output.linesIterator.find(_.startsWith("attempt: ")).get.stripPrefix("attempt: "))

        Then("the attempt includes the direct storyboard identity and no generated output")
        Files.readString(attempt, StandardCharsets.UTF_8) should include("path: video/storyboard.md")
        Files.exists(project.resolve("target"), LinkOption.NOFOLLOW_LINKS) shouldBe false
        Files.exists(project.resolve("video-review"), LinkOption.NOFOLLOW_LINKS) shouldBe false
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

    "separate slide and video review products while resolving hidden profiles" in {
      _with_temp_dir("cozy-document-project-profile-reviews") { root =>
        Given("a public bok project and a video bok project promoted to the hidden profile identity")
        val parent = Files.createDirectory(root.resolve("parent"))
        _execute(List("document-project", "scaffold", "bok", "--profile", "bok", "--language", "en", "--workspace", "bok", "--save", parent.toString))
        _execute(List("document-project", "scaffold", "bok-video", "--profile", "bok-video", "--language", "en", "--workspace", "bok", "--save", parent.toString))
        val bok = parent.resolve("bok.dox")
        val hidden = parent.resolve("bok-video.dox")
        val hiddenDescriptor = hidden.resolve("document-project.yaml")
        Files.writeString(hiddenDescriptor, Files.readString(hiddenDescriptor, StandardCharsets.UTF_8).replace("profile: bok-video", "profile: simplemodeling-org-video"), StandardCharsets.UTF_8)

        When("explicit review kinds are requested")
        _execute(List("document-project", "review", bok.toString, "--kind", "slides"))
        _execute(List("document-project", "review", bok.toString, "--kind", "slide-logical-chart"))
        _execute(List("document-project", "review", hidden.toString, "--kind", "video-logical-chart"))
        _execute(List("document-project", "dashboard", bok.toString))
        _execute(List("document-project", "dashboard", hidden.toString))

        Then("each review has its own Work Product output and only video profiles admit the video chart")
        Files.readString(bok.resolve("target/document-project/slides-review.html"), StandardCharsets.UTF_8) should include("<h1>Slide Review</h1>")
        Files.readString(bok.resolve("target/document-project/slide-logical-chart-review.html"), StandardCharsets.UTF_8) should include("<h1>Slide Logical Chart</h1>")
        Files.readString(hidden.resolve("target/document-project/video-logical-chart-review.html"), StandardCharsets.UTF_8) should include("<h1>Video Logical Chart</h1>")
        Files.readString(bok.resolve("target/document-project/project-dashboard.html"), StandardCharsets.UTF_8) should include("href=\"slides-review.html\"")
        Files.readString(bok.resolve("target/document-project/project-dashboard.html"), StandardCharsets.UTF_8) should include("href=\"../../infographic/infographic.svg\"")
        Files.readString(bok.resolve("target/document-project/project-dashboard.html"), StandardCharsets.UTF_8) should include("Infographic final artifact")
        Files.readString(bok.resolve("target/document-project/project-dashboard.html"), StandardCharsets.UTF_8) should not include("video-logical-chart-review.html")
        Files.readString(bok.resolve("target/document-project/project-dashboard.html"), StandardCharsets.UTF_8) should include("<th scope=\"col\">Next action</th>")
        Files.readString(bok.resolve("target/document-project/project-dashboard.html"), StandardCharsets.UTF_8) should include("Generate Article site HTML with Cozy Site")
        Files.readString(hidden.resolve("target/document-project/project-dashboard.html"), StandardCharsets.UTF_8) should include("href=\"video-logical-chart-review.html\"")
        _failure(List("document-project", "review", bok.toString, "--kind", "video-logical-chart")) should include("logical operation video-logical-chart.render-review is disabled for profile bok")
        _execute(List("document-project", "verify", hidden.toString)) should include("Cozy Document Project Verify")
        CozyHelpText._text should not include("simplemodeling-org")
      }
    }

    "publish the frozen public Document Project help forms" in {
      Given("the Cozy public help text")

      When("the Document Project command section is inspected")
      val help = CozyHelpText._text

      Then("each no-alias form and the Phase 42.1 projection boundary are described")
      help should include("document-project inspect <project>")
      help should include("document-project dashboard <project> [--save <dashboard.html>]")
      help should include("document-project review <project> --kind core|slides|video|slide-logical-chart|video-logical-chart [--save <review.html>]")
      help should include("document-project reflect-feedback <project> <feedback>")
      help should include("document-project run <project> --operation <logical-operation> [--dry-run]")
      help should include("document-project scaffold <slug> --profile standard|standard-video|bok|bok-video --language <tag> --workspace directory|bok --save <parent>")
      help should include("Dashboard defaults to target/document-project/project-dashboard.html")
      help should include("Core review defaults to target/document-project/core-review.html")
      help should include("Video review defaults to target/document-project/video-review.html")
      help should include("Slide and video logical charts default to target/document-project/slide-logical-chart-review.html")
      help should include("Slide Logical Chart visualizes current Content Core and Visual Page IR")
      help should include("same-directory temporary file and atomic move")
      help should include("direct JSON or YAML structured feedback with one common object schema")
      help should include("each item retains its proposal, applicability, and disposition")
      help should include("A non-video profile requires a not-applicable video item")
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
