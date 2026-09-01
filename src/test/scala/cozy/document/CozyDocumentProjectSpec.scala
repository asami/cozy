package cozy.document

import cozy.scaffold.CozyHelpText
import cozy.media.CozyMedia
import java.io.{ByteArrayOutputStream, PrintStream}
import java.nio.charset.StandardCharsets
import java.nio.file.{Files, LinkOption, Path}
import java.security.MessageDigest
import scala.collection.JavaConverters._
import scala.util.control.NonFatal
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Aug. 31, 2026
 * @version Sep. 1, 2026
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
        firststate should include("reason: \"profile standard disables video branch\"")
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

        When("dashboard is requested before any default review output exists")
        _execute(List("document-project", "dashboard", project.toString))
        val dashboard = project.resolve("target/document-project/project-dashboard.html")

        Then("review-projection Work Products remain blocked and identify generation as the next action")
        val initialdashboardtext = Files.readString(dashboard, StandardCharsets.UTF_8)
        initialdashboardtext should include("core-review-html</th><td>missing</td><td>missing</td><td>pending</td><td>blocked")
        initialdashboardtext should include("Generate Core review HTML")
        initialdashboardtext should include("slide-review-html</th><td>missing</td><td>missing</td><td>pending</td><td>blocked")
        initialdashboardtext should include("Generate Slide review HTML")

        When("the default core and slide reviews are generated before the dashboard is repeated")
        _execute(List("document-project", "review", project.toString, "--kind", "core"))
        _execute(List("document-project", "review", project.toString, "--kind", "slides"))
        val firstoutput = _execute(List("document-project", "dashboard", project.toString))
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
        dashboardtext should include("core-review-html</th><td>missing</td><td>missing</td><td>pending</td><td>blocked")
        dashboardtext should include("slide-review-html</th><td>missing</td><td>missing</td><td>pending</td><td>blocked")
        dashboardtext should include("<a href=\"core-review.html\">core-review.html</a>")
        dashboardtext should include("<a href=\"slides-review.html\">slides-review.html</a>")
        dashboardtext should include("<a href=\"../../infographic/infographic.svg\">../../infographic/infographic.svg</a>")
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

        Then("the exact external path is selected and its links resolve from that output parent")
        Files.isRegularFile(explicit, LinkOption.NOFOLLOW_LINKS) shouldBe true
        val explicittext = Files.readString(explicit, StandardCharsets.UTF_8)
        val explicitcorehref = explicit.getParent.relativize(project.resolve("target/document-project/core-review.html")).toString.replace('\\', '/')
        val explicitslideshref = explicit.getParent.relativize(project.resolve("target/document-project/slides-review.html")).toString.replace('\\', '/')
        val explicitinfographichref = explicit.getParent.relativize(project.resolve("infographic/infographic.svg")).toString.replace('\\', '/')
        explicittext should include(s"""<a href="$explicitcorehref">$explicitcorehref</a>""")
        explicittext should include(s"""<a href="$explicitslideshref">$explicitslideshref</a>""")
        explicittext should include(s"""<a href="$explicitinfographichref">$explicitinfographichref</a>""")

        When("dashboard is directed to an allowed nested HTML path under the project projection directory")
        val internal = project.resolve("target/document-project/nested/explicit-dashboard.html")
        _execute(List("document-project", "dashboard", project.toString, "--save", internal.toString))

        Then("the nested project-internal projection path is generated with links relative to its parent")
        Files.isRegularFile(internal, LinkOption.NOFOLLOW_LINKS) shouldBe true
        val internaltext = Files.readString(internal, StandardCharsets.UTF_8)
        val internalcorehref = internal.getParent.relativize(project.resolve("target/document-project/core-review.html")).toString.replace('\\', '/')
        val internalslideshref = internal.getParent.relativize(project.resolve("target/document-project/slides-review.html")).toString.replace('\\', '/')
        val internalinfographichref = internal.getParent.relativize(project.resolve("infographic/infographic.svg")).toString.replace('\\', '/')
        internaltext should include(s"""<a href="$internalcorehref">$internalcorehref</a>""")
        internaltext should include(s"""<a href="$internalslideshref">$internalslideshref</a>""")
        internaltext should include(s"""<a href="$internalinfographichref">$internalinfographichref</a>""")

        When("an existing regular explicit destination is regenerated")
        val existingparent = Files.createDirectory(root.resolve("existing"))
        val existing = existingparent.resolve("dashboard.html")
        Files.writeString(existing, "old projection\n", StandardCharsets.UTF_8)
        _execute(List("document-project", "dashboard", project.toString, "--save", existing.toString))

        Then("the regular destination is atomically replaced with a projection linked from its parent")
        val existingtext = Files.readString(existing, StandardCharsets.UTF_8)
        val existinginfographichref = existing.getParent.relativize(project.resolve("infographic/infographic.svg")).toString.replace('\\', '/')
        existingtext should include("<h1>Cozy Document Project Dashboard</h1>")
        existingtext should include(s"""<a href="$existinginfographichref">$existinginfographichref</a>""")

        When("the disposable state is rebuilt after its cache directory is deleted")
        _execute(List("document-project", "inspect", project.toString))
        val firststatebytes = Files.readAllBytes(project.resolve("target/document-project/state.yaml"))
        _delete(project.resolve("target/document-project"))
        _execute(List("document-project", "inspect", project.toString))
        val secondstatebytes = Files.readAllBytes(project.resolve("target/document-project/state.yaml"))

        Then("the source-derived state remains byte-identical without generated review cache evidence")
        firststatebytes shouldBe secondstatebytes
        Files.readString(project.resolve("target/document-project/state.yaml"), StandardCharsets.UTF_8) should include("core-review-html")
        Files.readString(project.resolve("target/document-project/state.yaml"), StandardCharsets.UTF_8) should include("currentness: missing")
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

        When("the standard dashboard is projected before the Slide Logical Chart is generated")
        _execute(List("document-project", "dashboard", standard.toString))

        Then("the Slide Logical Chart remains blocked until its default review HTML exists")
        val standardbeforechart = Files.readString(standard.resolve("target/document-project/project-dashboard.html"), StandardCharsets.UTF_8)
        standardbeforechart should include("explanation-structure-review-html</th><td>missing</td><td>missing</td><td>pending</td><td>blocked</td><td>default review HTML is not generated")
        standardbeforechart should include("Generate Slide Logical Chart HTML")

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
        standardcharttext should include("Work Product: <code>explanation-structure-review-html</code>; label: <span>Slide Logical Chart HTML</span>.")
        standardcharttext should not include("slide-logical-chart.render-review")
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

        When("the standard-video dashboard is projected before the Video Logical Chart is generated")
        _execute(List("document-project", "dashboard", video.toString))

        Then("the Video Logical Chart remains blocked until its default review HTML exists")
        val videobeforechart = Files.readString(video.resolve("target/document-project/project-dashboard.html"), StandardCharsets.UTF_8)
        videobeforechart should include("video-logical-chart-html</th><td>missing</td><td>missing</td><td>pending</td><td>blocked</td><td>default review HTML is not generated")
        videobeforechart should include("Generate Video Logical Chart HTML")

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
        videocharttext should not include("video-logical-chart.render-review")
        Files.exists(video.resolve("target/document-project/state.yaml"), LinkOption.NOFOLLOW_LINKS) shouldBe false
        Files.exists(video.resolve("evidence"), LinkOption.NOFOLLOW_LINKS) shouldBe false
        Files.readAllBytes(videocore) shouldBe videocorebytes
        Files.readAllBytes(videovisualpages) shouldBe videovisualbytes
        Files.readAllBytes(videostoryboard) shouldBe videostoryboardbytes

        When("the standard-video dashboard is projected")
        _execute(List("document-project", "dashboard", video.toString))

        Then("slide and video logical-chart Work Products distinguish generated output from available IR inputs")
        val dashboardtext = Files.readString(video.resolve("target/document-project/project-dashboard.html"), StandardCharsets.UTF_8)
        dashboardtext should include("explanation-structure-review-html<br/><span>Slide Logical Chart HTML</span></th><td>active</td><td>review-projection</td><td>optional</td>")
        dashboardtext should include("explanation-structure-review-html</th><td>missing</td><td>missing</td><td>pending</td><td>blocked</td><td>default review HTML is not generated")
        dashboardtext should include("video-logical-chart-html<br/><span>Video Logical Chart HTML</span></th><td>active</td><td>review-projection</td><td>optional</td>")
        dashboardtext should include("video-logical-chart-html</th><td>missing</td><td>missing</td><td>pending</td><td>blocked")
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

    "derive current sidecar evidence and a safe public-source dashboard projection" in {
      _with_temp_dir("cozy-document-project-sidecar-current") { root =>
        Given("a scaffolded project, direct public media mapping, and accepted Core dialogue evidence")
        val project = _scaffolded_project(root, "sidecar-current")
        val request = project.resolve("evidence/dialogue/request.txt")
        val response = project.resolve("evidence/dialogue/response.txt")
        Files.createDirectories(request.getParent)
        Files.writeString(request, "request", StandardCharsets.UTF_8)
        Files.writeString(response, "response", StandardCharsets.UTF_8)
        val core = "content/core-en.yaml"
        Files.writeString(project.resolve(core), _core_yaml("sidecar-current", "en", "accepted Core"), StandardCharsets.UTF_8)
        _write_sidecar(
          project,
          Map(
            "content-core" -> _file_evidence(project, core, "source"),
            "article-source" -> _file_evidence(project, "index.dox", "source"),
            "visual-pages" -> _file_evidence(project, "presentation/visual-pages.yaml", "source"),
            "infographic-svg" -> _file_evidence(project, "infographic/infographic.svg", "source")
          ),
          Map("content-core" -> _accepted_review(project, core, request, response))
        )

        When("inspect and dashboard consume the one evidence-derived model")
        _execute(List("document-project", "inspect", project.toString))
        _execute(List("document-project", "dashboard", project.toString))

        Then("state records sidecar identity, current declared evidence, and accepted review")
        val state = Files.readString(project.resolve("target/document-project/state.yaml"), StandardCharsets.UTF_8)
        state should include("evidence:")
        state should include("path: evidence/document-project.yaml")
        state should include("article-source")
        state should include("review: accepted")

        And("the dashboard exposes only the safe SmartDox source mapping")
        val dashboard = Files.readString(project.resolve("target/document-project/project-dashboard.html"), StandardCharsets.UTF_8)
        dashboard should include("Safe public source")
        dashboard should include("public-article")
        dashboard should include("index.dox")
        dashboard should not include("media/article-media.yaml")
        dashboard should include("Workspace integration")
        dashboard should include("Aggregate build")
        dashboard should include("External delivery")
      }
    }

    "accept standalone and isolated BoK local drivers through bounded read-only projections" in {
      _with_temp_dir("cozy-document-project-driver-acceptance") { root =>
        Given("direct local parents for a standard-video directory driver and an isolated non-Article-8 BoK driver")
        val standaloneparent = Files.createDirectory(root.resolve("standalone-parent"))
        val bokparent = Files.createDirectory(root.resolve("bok-parent"))

        When("the two direct local fixture packages are scaffolded with their independent profile and workspace selections")
        _execute(List("document-project", "scaffold", "standalone", "--profile", "standard-video", "--language", "en", "--workspace", "directory", "--save", standaloneparent.toString))
        _execute(List("document-project", "scaffold", "isolated-bok", "--profile", "bok", "--language", "en", "--workspace", "bok", "--save", bokparent.toString))
        val standalone = standaloneparent.resolve("standalone.dox")
        val bok = bokparent.resolve("isolated-bok.dox")
        val standalonearticlebefore = Files.readAllBytes(standalone.resolve("index.dox"))
        val standalonevisualbefore = Files.readAllBytes(standalone.resolve("presentation/visual-pages.yaml"))
        val standaloneinfographicbefore = Files.readAllBytes(standalone.resolve("infographic/infographic.svg"))
        val standalonecore = standalone.resolve("content/core-en.yaml")
        val standalonecorebefore = Files.readAllBytes(standalonecore)
        val corefeedback = root.resolve("standalone-core-feedback.json")
        Files.writeString(
          corefeedback,
          """{"reason":"accepted local Core replacement","changes":[{"target":"core","replacement":{"accepted":[{"id":"driver-core","text":"Accepted standalone driver Core"}]},"applicability":"applicable","disposition":"accepted"}]}""",
          StandardCharsets.UTF_8
        )

        Then("the fixtures retain their selected profile and workspace without creating external or Article-8 state")
        Files.readString(standalone.resolve("document-project.yaml"), StandardCharsets.UTF_8) should include("profile: standard-video")
        Files.readString(standalone.resolve("document-project.yaml"), StandardCharsets.UTF_8) should include("kind: directory")
        Files.readString(bok.resolve("document-project.yaml"), StandardCharsets.UTF_8) should include("profile: bok")
        Files.readString(bok.resolve("document-project.yaml"), StandardCharsets.UTF_8) should include("kind: bok")
        Files.exists(standalone.resolve("target"), LinkOption.NOFOLLOW_LINKS) shouldBe false
        Files.exists(bok.resolve("target"), LinkOption.NOFOLLOW_LINKS) shouldBe false

        When("plan and verify prove the bounded local drivers without external acceptance")
        val standaloneplan = _execute(List("document-project", "plan", standalone.toString))
        val bokplan = _execute(List("document-project", "plan", bok.toString))
        val standaloneverify = _execute(List("document-project", "verify", standalone.toString))
        val bokverify = _execute(List("document-project", "verify", bok.toString))

        Then("each driver resolves its selected profile and verifies only its local package")
        standaloneplan should include("eligible: operation video.render-review [provider: cozy-video]")
        bokplan should include("eligible: operation article.render-pdf [provider: smartdox-rendering]")
        bokplan should include("omitted: work-product video-review [review-projection, disabled: profile bok disables video branch]")
        standaloneverify should include("Cozy Document Project Verify")
        bokverify should include("Cozy Document Project Verify")
        Files.exists(standalone.resolve("target/document-project/state.yaml"), LinkOption.NOFOLLOW_LINKS) shouldBe true
        Files.exists(bok.resolve("target/document-project/state.yaml"), LinkOption.NOFOLLOW_LINKS) shouldBe true

        When("the selected local operations are recorded without provider execution")
        val standaloneoperations = Vector(
          "article.render-pdf",
          "summary-slides.render-pdf",
          "infographic.render-png",
          "video.render-review",
          "slide-logical-chart.render-review",
          "video-logical-chart.render-review"
        )
        val bokoperations = Vector(
          "article.render-pdf",
          "summary-slides.render-pdf",
          "infographic.render-png",
          "slide-logical-chart.render-review"
        )
        val standaloneoutputs = standaloneoperations.map(operation => _execute(List("document-project", "run", standalone.toString, "--operation", operation)))
        val bokoutputs = bokoperations.map(operation => _execute(List("document-project", "run", bok.toString, "--operation", operation)))
        val bokvideofailure = _failure(List("document-project", "run", bok.toString, "--operation", "video.render-review"))

        Then("every selected dispatch is recorded only and the disabled BoK video operation remains rejected")
        standaloneoperations.zip(standaloneoutputs).foreach { case (operation, output) =>
          output should include(s"operation: $operation")
          output should include("outcome: recorded")
          val attempt = standalone.resolve(output.linesIterator.find(_.startsWith("attempt: ")).get.stripPrefix("attempt: "))
          Files.readString(attempt, StandardCharsets.UTF_8) should include("provider execution is deferred; this dispatch was recorded only")
          Files.readString(attempt, StandardCharsets.UTF_8) should include("outputs: []")
        }
        bokoperations.zip(bokoutputs).foreach { case (operation, output) =>
          output should include(s"operation: $operation")
          output should include("outcome: recorded")
          val attempt = bok.resolve(output.linesIterator.find(_.startsWith("attempt: ")).get.stripPrefix("attempt: "))
          Files.readString(attempt, StandardCharsets.UTF_8) should include("provider execution is deferred; this dispatch was recorded only")
          Files.readString(attempt, StandardCharsets.UTF_8) should include("outputs: []")
        }
        bokvideofailure should include("DP-OP-001")
        bokvideofailure should include("disabled for profile bok")
        _relative_files(standalone.resolve("evidence/attempts")).size shouldBe standaloneoperations.size
        _relative_files(bok.resolve("evidence/attempts")).size shouldBe bokoperations.size

        When("the standalone Core review is generated")
        _execute(List("document-project", "review", standalone.toString, "--kind", "core"))

        Then("the standalone Core review leaves Core authority unchanged")
        Files.readAllBytes(standalonecore) shouldBe standalonecorebefore

        When("the local accepted Core feedback is reflected")
        val feedbackoutput = _execute(List("document-project", "reflect-feedback", standalone.toString, corefeedback.toString))

        Then("only the accepted Core authority changes through the bounded feedback command")
        feedbackoutput should include("reflected: core content/core-en.yaml")
        Files.readAllBytes(standalonecore) should not equal standalonecorebefore
        Files.readAllBytes(standalone.resolve("index.dox")) shouldBe standalonearticlebefore
        Files.readAllBytes(standalone.resolve("presentation/visual-pages.yaml")) shouldBe standalonevisualbefore
        Files.readAllBytes(standalone.resolve("infographic/infographic.svg")) shouldBe standaloneinfographicbefore

        Given("current source evidence for both local fixtures and an accepted Core dialogue record for the standalone driver")
        val standalonecorepath = "content/core-en.yaml"
        val standaloneevidence = Map(
          "content-core" -> _file_evidence(standalone, standalonecorepath, "source"),
          "article-source" -> _file_evidence(standalone, "index.dox", "source"),
          "visual-pages" -> _file_evidence(standalone, "presentation/visual-pages.yaml", "source"),
          "infographic-svg" -> _file_evidence(standalone, "infographic/infographic.svg", "source")
        )
        val request = standalone.resolve("evidence/dialogue/request.txt")
        val response = standalone.resolve("evidence/dialogue/response.txt")
        Files.createDirectories(request.getParent)
        Files.writeString(request, "standalone local request", StandardCharsets.UTF_8)
        Files.writeString(response, "standalone local response", StandardCharsets.UTF_8)
        _write_sidecar(
          standalone,
          standaloneevidence,
          Map("content-core" -> _accepted_review(standalone, standalonecorepath, request, response, "local-ai-provider", "local-ai-model")),
          "standard-video"
        )
        _write_sidecar(
          bok,
          Map(
            "content-core" -> _file_evidence(bok, "content/core-en.yaml", "source"),
            "article-source" -> _file_evidence(bok, "index.dox", "source"),
            "visual-pages" -> _file_evidence(bok, "presentation/visual-pages.yaml", "source"),
            "infographic-svg" -> _file_evidence(bok, "infographic/infographic.svg", "source")
          ),
          Map.empty,
          "bok"
        )

        When("inspect derives each fixture's current sidecar-bound state")
        _execute(List("document-project", "inspect", standalone.toString))
        _execute(List("document-project", "inspect", bok.toString))

        Then("the standalone Core dialogue is accepted and both fixtures retain evidence-derived state")
        val standalonestate = Files.readString(standalone.resolve("target/document-project/state.yaml"), StandardCharsets.UTF_8)
        val bokstate = Files.readString(bok.resolve("target/document-project/state.yaml"), StandardCharsets.UTF_8)
        standalonestate should include("review: accepted")
        standalonestate should include("path: evidence/document-project.yaml")
        bokstate should include("path: evidence/document-project.yaml")
        standalonestate.linesIterator.filter(line => line.nonEmpty && !line.startsWith(" ")).map(_.takeWhile(_ != ':')).toVector shouldBe Vector(
          "schema", "project", "profile", "workspace", "sources", "evidence", "criteria", "workProducts"
        )
        standalonestate should include("criteria:\n  satisfied: 4\n  total: 17")
        bokstate should include("criteria:\n  satisfied: 3\n  total: 13")
        bokstate should include("notApplicable:\n    - id: video-storyboard-authored\n      reason: \"profile bok disables video branch\"")
        val standalonesidecar = Files.readString(standalone.resolve("evidence/document-project.yaml"), StandardCharsets.UTF_8)
        standalonesidecar should include("provider: local-ai-provider")
        standalonesidecar should include("model: local-ai-model")

        When("the local review and dashboard projections are requested for both fixtures")
        _execute(List("document-project", "review", standalone.toString, "--kind", "core"))
        _execute(List("document-project", "review", standalone.toString, "--kind", "slides"))
        _execute(List("document-project", "review", standalone.toString, "--kind", "video"))
        _execute(List("document-project", "review", standalone.toString, "--kind", "slide-logical-chart"))
        _execute(List("document-project", "review", standalone.toString, "--kind", "video-logical-chart"))
        _execute(List("document-project", "dashboard", standalone.toString))
        _execute(List("document-project", "review", bok.toString, "--kind", "core"))
        _execute(List("document-project", "review", bok.toString, "--kind", "slides"))
        _execute(List("document-project", "review", bok.toString, "--kind", "slide-logical-chart"))
        _execute(List("document-project", "dashboard", bok.toString))

        Then("each local review is present while the isolated BoK fixture omits video review output")
        Files.readString(standalone.resolve("target/document-project/core-review.html"), StandardCharsets.UTF_8) should include("<h1>Core Review</h1>")
        Files.readString(standalone.resolve("target/document-project/slides-review.html"), StandardCharsets.UTF_8) should include("<h1>Slide Review</h1>")
        Files.readString(standalone.resolve("target/document-project/video-review.html"), StandardCharsets.UTF_8) should include("<h1>Video Review</h1>")
        Files.readString(standalone.resolve("target/document-project/slide-logical-chart-review.html"), StandardCharsets.UTF_8) should include("<h1>Slide Logical Chart</h1>")
        Files.readString(standalone.resolve("target/document-project/video-logical-chart-review.html"), StandardCharsets.UTF_8) should include("<h1>Video Logical Chart</h1>")
        Files.readString(bok.resolve("target/document-project/core-review.html"), StandardCharsets.UTF_8) should include("<h1>Core Review</h1>")
        Files.readString(bok.resolve("target/document-project/slides-review.html"), StandardCharsets.UTF_8) should include("<h1>Slide Review</h1>")
        Files.readString(bok.resolve("target/document-project/slide-logical-chart-review.html"), StandardCharsets.UTF_8) should include("<h1>Slide Logical Chart</h1>")
        Files.exists(bok.resolve("target/document-project/video-review.html"), LinkOption.NOFOLLOW_LINKS) shouldBe false

        And("both dashboards expose only the safe public article mapping and keep delivery read-only")
        val standalonedashboard = Files.readString(standalone.resolve("target/document-project/project-dashboard.html"), StandardCharsets.UTF_8)
        val bokdashboard = Files.readString(bok.resolve("target/document-project/project-dashboard.html"), StandardCharsets.UTF_8)
        standalonedashboard should include("profile: <code>standard-video</code>; workspace: <code>directory</code>")
        bokdashboard should include("profile: <code>bok</code>; workspace: <code>bok</code>")
        Vector(standalonedashboard, bokdashboard).foreach { dashboard =>
          dashboard should include("Safe public source")
          dashboard should include("public-article")
          dashboard should include("index.dox")
          dashboard should not include "content/core-en.yaml"
          dashboard should not include "evidence/document-project.yaml"
          dashboard should not include "target/document-project/state.yaml"
          dashboard should not include "receipt-media.json"
          dashboard should include("External delivery</th><td>Read-only and non-invoked; no publication, deployment, upload, or registration is performed.")
          dashboard should include("<h2>Criterion coverage</h2>")
          dashboard should include("<table aria-label=\"Criterion coverage\">")
        }
        standalonedashboard should include("4/17 applicable criteria satisfied")
        bokdashboard should include("3/13 applicable criteria satisfied")
        standalonedashboard should include("video-review<br/><span>Video review HTML</span></th><td>active</td><td>review-projection</td><td>required")
        standalonedashboard should include("video-deliverable<br/><span>Video deliverable</span></th><td>active</td><td>deliverable</td><td>required")
        bokdashboard should include("video-review<br/><span>Video review HTML</span></th><td>omitted</td><td>review-projection</td><td>disabled")
        bokdashboard should include("video-deliverable<br/><span>Video deliverable</span></th><td>omitted</td><td>deliverable</td><td>disabled")
        bokdashboard should include("video-logical-chart-html<br/><span>Video Logical Chart HTML</span></th><td>omitted</td><td>review-projection</td><td>disabled")
        bokdashboard should include("profile bok disables video branch")
        bokdashboard should not include("<th scope=\"row\">Video Review</th>")
        bokdashboard should not include("video-review.html")
        Vector(standalonedashboard, bokdashboard).foreach { dashboard =>
          dashboard should include("article-pdf<br/><span>Article PDF</span></th><td>active</td><td>deliverable</td><td>required")
          dashboard should include("summary-slides-pdf<br/><span>Summary slides PDF</span></th><td>active</td><td>deliverable</td><td>optional")
          dashboard should include("infographic-png<br/><span>Infographic PNG</span></th><td>active</td><td>deliverable</td><td>optional")
        }
      }
    }

    "propagate stale sidecar evidence from a changed infographic and artifact hash mismatch" in {
      _with_temp_dir("cozy-document-project-sidecar-stale") { root =>
        Given("a sidecar that records the current infographic, a missing article source, and a retained receipt-backed PDF")
        val project = _scaffolded_project(root, "sidecar-stale")
        _write_receipt_media_descriptor(project)
        val pdf = project.resolve("target/cozy-media/article.pdf")
        _write_sidecar(
          project,
          Map(
            "article-source" -> "kind: none",
            "infographic-svg" -> _file_evidence(project, "infographic/infographic.svg", "source"),
            "article-pdf" -> _receipt_evidence("receipt-media.json", "article-pdf")
          )
        )
        Files.writeString(project.resolve("infographic/infographic.svg"), "<svg>changed</svg>\n", StandardCharsets.UTF_8)
        Files.writeString(pdf, "changed PDF", StandardCharsets.UTF_8)

        When("the immutable source and artifact identities no longer match current bytes")
        _execute(List("document-project", "inspect", project.toString))

        Then("hash mismatch and non-current receipt are stale, and the changed infographic stales its declared article consumer")
        val state = Files.readString(project.resolve("target/document-project/state.yaml"), StandardCharsets.UTF_8)
        state should include("id: infographic-svg\n    role: authority\n    disposition: required\n    criterion: infographic-svg-authored\n    coverage: missing\n    currentness: stale")
        state should include("id: article-source\n    role: authority\n    disposition: required\n    criterion: article-source-authored\n    coverage: missing\n    currentness: stale")
        state should include("id: article-source\n    role: authority\n    disposition: required\n    criterion: article-source-authored\n    coverage: missing\n    currentness: stale\n    review: pending\n    readiness: blocked\n    reason: \"a declared dependency is stale\"")
        state should include("id: article-pdf\n    role: deliverable\n    disposition: required\n    criterion: article-pdf-rendered\n    coverage: missing\n    currentness: stale")
      }
    }

    "keep a retained failed attempt separate from later current product evidence" in {
      _with_temp_dir("cozy-document-project-sidecar-attempt") { root =>
        Given("a retained failed article PDF attempt, a current direct artifact identity, and a later stale infographic dependency")
        val project = _scaffolded_project(root, "sidecar-attempt")
        val pdf = project.resolve("target/article.pdf")
        Files.createDirectories(pdf.getParent)
        Files.writeString(pdf, "current PDF", StandardCharsets.UTF_8)
        _write_sidecar(project, Map(
          "article-source" -> _file_evidence(project, "index.dox", "source"),
          "infographic-svg" -> _file_evidence(project, "infographic/infographic.svg", "source"),
          "article-pdf" -> _file_evidence(project, "target/article.pdf", "artifact")
        ))
        Files.writeString(project.resolve("infographic/infographic.svg"), "<svg>changed</svg>\n", StandardCharsets.UTF_8)
        val attemptid = "11111111-1111-4111-8111-111111111111"
        val attempt = project.resolve(s"evidence/attempts/$attemptid.yaml")
        Files.createDirectories(attempt.getParent)
        Files.writeString(attempt, _failed_attempt_yaml(project, attemptid, "article.render-pdf", "smartdox-rendering", "standard"), StandardCharsets.UTF_8)

        When("inspect derives current product evidence alongside historical failure evidence")
        _execute(List("document-project", "inspect", project.toString))

        Then("the current artifact becomes stale from its changed dependency rather than becoming failed")
        val state = Files.readString(project.resolve("target/document-project/state.yaml"), StandardCharsets.UTF_8)
        state should include("id: article-source\n    role: authority\n    disposition: required\n    criterion: article-source-authored\n    coverage: missing\n    currentness: stale")
        state should include("id: article-pdf\n    role: deliverable\n    disposition: required\n    criterion: article-pdf-rendered\n    coverage: missing\n    currentness: stale")
        state should not include("id: article-pdf\n    role: deliverable\n    disposition: required\n    criterion: article-pdf-rendered\n    coverage: missing\n    currentness: failed")
        state should include(s"path: evidence/attempts/$attemptid.yaml")

        And("the dashboard retains the failure as historical evidence")
        _execute(List("document-project", "dashboard", project.toString))
        Files.readString(project.resolve("target/document-project/project-dashboard.html"), StandardCharsets.UTF_8) should include("failed; historical attempt")

        When("a retained attempt with reordered top-level keys is added")
        val reorderedid = "33333333-3333-4333-8333-333333333333"
        val reorderedattempt = project.resolve(s"evidence/attempts/$reorderedid.yaml")
        val reorderedyaml = _reorder_attempt_top_level(_failed_attempt_yaml(project, reorderedid, "article.render-pdf", "smartdox-rendering", "standard"))
        Files.writeString(reorderedattempt, reorderedyaml, StandardCharsets.UTF_8)
        val previousstateafterorder = Files.readAllBytes(project.resolve("target/document-project/state.yaml"))
        val reorderedfailure = _failure(List("document-project", "inspect", project.toString))

        Then("reordered retained evidence is rejected before it can influence the disposable state")
        reorderedfailure should include("DP-DESC-002")
        Files.readAllBytes(project.resolve("target/document-project/state.yaml")) shouldBe previousstateafterorder

        When("a retained attempt with a mismatched filename is added")
        val malformedattempt = project.resolve("evidence/attempts/malformed.yaml")
        Files.writeString(malformedattempt, _failed_attempt_yaml(project, "22222222-2222-4222-8222-222222222222", "article.render-pdf", "smartdox-rendering", "standard"), StandardCharsets.UTF_8)
        val previousstate = Files.readAllBytes(project.resolve("target/document-project/state.yaml"))
        val malformedfailure = _failure(List("document-project", "inspect", project.toString))

        Then("malformed retained evidence is rejected before it can influence the disposable state")
        malformedfailure should include("DP-DESC-002")
        Files.readAllBytes(project.resolve("target/document-project/state.yaml")) shouldBe previousstate
      }
    }

    "derive accepted, rejected, and stale Core dialogue review without autonomous authority" in {
      _with_temp_dir("cozy-document-project-sidecar-review") { root =>
        Given("direct request and response evidence for an accepted Core dialogue")
        val project = _scaffolded_project(root, "sidecar-review")
        val request = project.resolve("evidence/dialogue/request.txt")
        val response = project.resolve("evidence/dialogue/response.txt")
        Files.createDirectories(request.getParent)
        Files.writeString(request, "request", StandardCharsets.UTF_8)
        Files.writeString(response, "response", StandardCharsets.UTF_8)
        val core = "content/core-en.yaml"
        _write_sidecar(project, Map("content-core" -> _file_evidence(project, core, "source")), Map("content-core" -> _accepted_review(project, core, request, response)))

        When("the accepted authority identity is current")
        _execute(List("document-project", "inspect", project.toString))

        Then("the Core review is accepted without executing a provider")
        Files.readString(project.resolve("target/document-project/state.yaml"), StandardCharsets.UTF_8) should include("review: accepted")

        When("the descriptor Core changes after that acceptance evidence")
        Files.writeString(project.resolve(core), _core_yaml("sidecar-review", "en", "changed accepted Core"), StandardCharsets.UTF_8)
        _execute(List("document-project", "inspect", project.toString))

        Then("the prior acceptance is stale")
        Files.readString(project.resolve("target/document-project/state.yaml"), StandardCharsets.UTF_8) should include("review: stale")

        When("a replacement sidecar records an explicit rejection with current request and response")
        _write_sidecar(project, Map("content-core" -> _file_evidence(project, core, "source")), Map("content-core" -> _rejected_review(project, request, response, "editor rejected the proposal")))
        _execute(List("document-project", "inspect", project.toString))

        Then("the project records rejected review separately from Core authority")
        val state = Files.readString(project.resolve("target/document-project/state.yaml"), StandardCharsets.UTF_8)
        state should include("review: rejected")
        state should include("readiness: failed")

        When("a replacement sidecar records a rejection reason containing YAML-special characters and line breaks")
        val rejectionreason = "editor: rejected # unsafe" + 0.toChar + 8.toChar + 12.toChar + 27.toChar + "\nnext\tline with \"quotes\" and \\slash"
        _write_sidecar(project, Map("content-core" -> _file_evidence(project, core, "source")), Map("content-core" -> _rejected_review(project, request, response, rejectionreason)))
        _execute(List("document-project", "inspect", project.toString))

        Then("the rejected reason remains one deterministic YAML double-quoted scalar")
        val rejectedstate = Files.readString(project.resolve("target/document-project/state.yaml"), StandardCharsets.UTF_8)
        rejectedstate should include("reason: \"review rejected: editor: rejected # unsafe\\u0000\\u0008\\u000c\\u001b\\nnext\\tline with \\\"quotes\\\" and \\\\slash\"")
        rejectedstate.linesIterator.count(_.contains("reason: \"review rejected:")) shouldBe 1
      }
    }

    "reject invalid, mismatched, and unsafe evidence sidecars" in {
      _with_temp_dir("cozy-document-project-sidecar-invalid") { root =>
        Given("an admitted project with a generated sidecar fixture")
        val project = _scaffolded_project(root, "sidecar-invalid")
        _write_sidecar(project)

        When("the public mapping project id, current source hash, or product path is invalid")
        val sidecar = project.resolve("evidence/document-project.yaml")
        val projectmismatch = Files.readString(sidecar, StandardCharsets.UTF_8).replace("project: sidecar-invalid", "project: another-project")
        Files.writeString(sidecar, projectmismatch, StandardCharsets.UTF_8)
        val projectfailure = _failure(List("document-project", "inspect", project.toString))
        _write_sidecar(project)
        val hashmismatch = Files.readString(sidecar, StandardCharsets.UTF_8).replaceFirst("sha256: [0-9a-f]{64}", "sha256: " + ("0" * 64))
        Files.writeString(sidecar, hashmismatch, StandardCharsets.UTF_8)
        val hashfailure = _failure(List("document-project", "inspect", project.toString))
        _write_sidecar(project, Map("article-source" -> ("kind: source\npath: ../outside.dox\nsha256: \"" + ("0" * 64) + "\"")))
        val unsafefailure = _failure(List("document-project", "inspect", project.toString))
        _write_sidecar(project, Map("article-pdf" -> _file_evidence(project, "index.dox", "artifact")))
        val authoredartifactfailure = _failure(List("document-project", "inspect", project.toString))
        _write_sidecar(project, Map("article-pdf" -> ("kind: artifact\npath: target/document-project/state.yaml\nsha256: \"" + ("0" * 64) + "\"")))
        val cacheartifactfailure = _failure(List("document-project", "inspect", project.toString))
        val media = project.resolve("media/article-media.yaml")
        Files.writeString(media, "schema: cozy.media.v0\narticleMedia:\n  articleIdentity: public-article\n", StandardCharsets.UTF_8)
        val mediaschemafailure = _failure(List("document-project", "inspect", project.toString))

        val realsidecar = project.resolve("evidence/document-project-real.yaml")
        Files.move(sidecar, realsidecar)
        Files.createSymbolicLink(sidecar, realsidecar)
        val sidecarsymlinkfailure = _failure(List("document-project", "inspect", project.toString))

        Then("the closed sidecar admission rejects each condition before a state cache is written")
        projectfailure should include("DP-DESC-002")
        hashfailure should include("DP-DESC-002")
        unsafefailure should include("DP-PATH-001")
        authoredartifactfailure should include("DP-PATH-001")
        cacheartifactfailure should include("DP-PATH-001")
        mediaschemafailure should include("DP-DESC-002")
        sidecarsymlinkfailure should include("DP-PATH-001")
        Files.exists(project.resolve("target/document-project/state.yaml"), LinkOption.NOFOLLOW_LINKS) shouldBe false
      }
    }

    "preserve legacy no-sidecar state and dashboard compatibility" in {
      _with_temp_dir("cozy-document-project-sidecar-legacy") { root =>
        Given("a scaffolded project with no evidence sidecar")
        val project = _scaffolded_project(root, "sidecar-legacy")

        When("inspect and dashboard are requested")
        _execute(List("document-project", "inspect", project.toString))
        _execute(List("document-project", "dashboard", project.toString))

        Then("the cache records no sidecar and the dashboard retains the missing/pending legacy projection")
        Files.readString(project.resolve("target/document-project/state.yaml"), StandardCharsets.UTF_8) should include("sidecar: none")
        val dashboard = Files.readString(project.resolve("target/document-project/project-dashboard.html"), StandardCharsets.UTF_8)
        dashboard should include("core-review-html</th><td>missing</td><td>missing</td><td>pending</td><td>blocked")
        dashboard should not include("Safe public source")
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

  private def _scaffolded_project(root: Path, slug: String): Path = {
    val parent = Files.createDirectory(root.resolve(s"$slug-parent"))
    _execute(List("document-project", "scaffold", slug, "--profile", "standard", "--language", "en", "--workspace", "directory", "--save", parent.toString))
    parent.resolve(s"$slug.dox")
  }

  private def _write_sidecar(project: Path, evidence: Map[String, String] = Map.empty, review: Map[String, String] = Map.empty, profileid: String = "standard"): Unit = {
    val media = project.resolve("media/article-media.yaml")
    Files.createDirectories(media.getParent)
    Files.writeString(media, "schema: cozy.media.v1\narticleMedia:\n  articleIdentity: public-article\n", StandardCharsets.UTF_8)
    val products = _resolved(profileid).workProducts.filter(_.binding.disposition != CozyDocumentWorkflow.WorkProductDisposition.Disabled).map { value =>
      val id = value.workProduct.id
      val itemevidence = evidence.getOrElse(id, "kind: none")
      val itemreview = review.getOrElse(id, "kind: none")
      s"  - id: $id\n    evidence:\n${_indent(itemevidence, 6)}\n    review:\n${_indent(itemreview, 6)}"
    }
    val sidecar = project.resolve("evidence/document-project.yaml")
    Files.createDirectories(sidecar.getParent)
    Files.writeString(
      sidecar,
      s"schema: cozy.document-project-evidence.v1\nproject: ${project.getFileName.toString.stripSuffix(".dox")}\npublicSource:\n  kind: smartdox\n  identity: public-article\n  path: index.dox\n  sha256: ${_sha256(project.resolve("index.dox"))}\n  mediaDescriptor: media/article-media.yaml\nproducts:\n${products.mkString("\n")}\n",
      StandardCharsets.UTF_8
    )
  }

  private def _file_evidence(project: Path, path: String, kind: String): String =
    s"kind: $kind\npath: $path\nsha256: ${_sha256(project.resolve(path))}"

  private def _receipt_evidence(media: String, resource: String): String =
    s"kind: receipt\nmediaDescriptor: $media\nresourceId: $resource"

  private def _write_receipt_media_descriptor(project: Path): Unit = {
    val knowledge = project.resolve("knowledge/article.dox")
    val source = project.resolve("media/source.txt")
    val descriptor = project.resolve("receipt-media.json")
    Files.createDirectories(knowledge.getParent)
    Files.createDirectories(source.getParent)
    Files.writeString(knowledge, "knowledge", StandardCharsets.UTF_8)
    Files.writeString(source, "source", StandardCharsets.UTF_8)
    Files.writeString(
      descriptor,
      "{\n  \"schema\": \"cozy.media.v1\",\n  \"knowledge\": {\"id\": \"document-project/article\", \"source\": \"knowledge/article.dox\"},\n  \"profiles\": {\"site\": {\"root\": \"publication\"}},\n  \"resources\": [{\"id\": \"article-pdf\", \"kind\": \"document\", \"source\": \"media/source.txt\", \"output\": \"target/cozy-media/article.pdf\", \"build\": \"copy\", \"publications\": {\"site\": \"article.pdf\"}}]\n}\n",
      StandardCharsets.UTF_8
    )
    CozyMedia.build(CozyMedia.CommandConfig(descriptor.toRealPath()))
  }

  private def _accepted_review(project: Path, core: String, request: Path, response: Path, provider: String = "human-editor", model: String = "editorial-record"): String =
    s"kind: core-dialogue\nprovider: $provider\nmodel: $model\nrequest:\n  path: ${_project_relative(project, request)}\n  sha256: ${_sha256(request)}\nresponse:\n  path: ${_project_relative(project, response)}\n  sha256: ${_sha256(response)}\naccepted:\n  acceptedAuthority:\n    path: $core\n    sha256: ${_sha256(project.resolve(core))}"

  private def _rejected_review(project: Path, request: Path, response: Path, reason: String): String =
    s"kind: core-dialogue\nprovider: human-editor\nmodel: editorial-record\nrequest:\n  path: ${_project_relative(project, request)}\n  sha256: ${_sha256(request)}\nresponse:\n  path: ${_project_relative(project, response)}\n  sha256: ${_sha256(response)}\nrejected:\n  rejectionReason: ${_yaml_double_quoted(reason)}"

  private def _reorder_attempt_top_level(value: String): String = {
    val lines = value.linesIterator.toVector
    (lines.slice(1, 2) ++ lines.slice(0, 1) ++ lines.drop(2)).mkString("\n") + "\n"
  }

  private def _yaml_double_quoted(value: String): String = {
    val builder = new StringBuilder("\"")
    value.foreach {
      case '\\' => builder.append("\\\\")
      case '"' => builder.append("\\\"")
      case '\r' => builder.append("\\r")
      case '\n' => builder.append("\\n")
      case '\t' => builder.append("\\t")
      case character if Character.isISOControl(character) => builder.append(f"\\u${character.toInt}%04x")
      case character => builder.append(character)
    }
    builder.append('"').result()
  }

  private def _failed_attempt_yaml(project: Path, attemptid: String, operation: String, provider: String, profile: String): String = {
    val descriptor = CozyDocumentProject.Descriptor(
      project.getFileName.toString.stripSuffix(".dox"),
      profile,
      "en",
      "directory",
      "content/core-en.yaml"
    )
    val inputs = CozyDocumentProject._state_sources(project, descriptor).map { case (path, source) =>
      s"  - path: $path\n    sha256: ${_sha256(source)}"
    }
    (Vector(
      "schema: cozy.document-operation-attempt.v1",
      s"id: $attemptid",
      s"operation: $operation",
      s"provider: $provider",
      s"profile: $profile",
      "inputs:"
    ) ++ inputs ++ Vector(
      "outcome: failed",
      "diagnostics: [retained failure]",
      "outputs: []",
      "receipt: none"
    )).mkString("\n") + "\n"
  }

  private def _core_yaml(slug: String, language: String, text: String): String =
    s"schema: cozy.content-core.v1\nid: $slug:core:$language\nlanguage: $language\naccepted:\n  - id: accepted-core\n    text: $text\n"

  private def _project_relative(project: Path, path: Path): String =
    project.relativize(path).toString.replace('\\', '/')

  private def _indent(value: String, spaces: Int): String =
    value.linesIterator.map(line => (" " * spaces) + line).mkString("\n")

  private def _sha256(path: Path): String =
    MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path)).map(value => f"${value & 0xff}%02x").mkString

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
