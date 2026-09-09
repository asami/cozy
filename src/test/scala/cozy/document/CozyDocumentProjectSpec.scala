package cozy.document

import cozy.scaffold.CozyHelpText
import cozy.media.{CozyExplanation, CozyMedia, CozyVisualPage}
import java.io.{ByteArrayOutputStream, PrintStream}
import java.nio.channels.FileChannel
import java.nio.charset.StandardCharsets
import java.nio.file.{Files, LinkOption, Path, StandardOpenOption}
import java.security.MessageDigest
import scala.collection.JavaConverters._
import scala.util.control.NonFatal
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Aug. 31, 2026
 * @version Sep. 10, 2026
 * @author  ASAMI, Tomoharu
 */
final class CozyDocumentProjectSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  "Cozy Document Project" should {
    "public Document Project behavior" which {
    "scaffold public no-video and video profile skeletons without fake outputs" in {
      _with_temp_dir("cozy-document-project-scaffold") { root =>
        Given("an existing direct parent and four absent Document Project packages")
        val standardparent = Files.createDirectory(root.resolve("standard-parent"))
        val videoparent = Files.createDirectory(root.resolve("video-parent"))
        val bokparent = Files.createDirectory(root.resolve("bok-parent"))
        val bokvideoparent = Files.createDirectory(root.resolve("bok-video-parent"))

        When("the public profile commands scaffold their packages")
        val standardoutput = _execute(List("document-project", "scaffold", "standard-doc", "--profile", "standard", "--language", "en", "--workspace", "directory", "--save", standardparent.toString))
        val videooutput = _execute(List("document-project", "scaffold", "video-doc", "--profile", "standard-video", "--language", "ja", "--workspace", "bok", "--save", videoparent.toString))
        val bokoutput = _execute(List("document-project", "scaffold", "bok-doc", "--profile", "bok", "--language", "en", "--workspace", "bok", "--save", bokparent.toString))
        val bokvideooutput = _execute(List("document-project", "scaffold", "bok-video-doc", "--profile", "bok-video", "--language", "en", "--workspace", "bok", "--save", bokvideoparent.toString))
        val standard = standardparent.resolve("standard-doc.dox")
        val video = videoparent.resolve("video-doc.dox")
        val bok = bokparent.resolve("bok-doc.dox")
        val bokvideo = bokvideoparent.resolve("bok-video-doc.dox")

        Then("each profile contains only its declared authored sources")
        _relative_files(standard) shouldBe Set(
          "document-project.yaml", "index.dox", "content/core-en.yaml", "infographic/infographic.svg",
          "content/presentation-semantics-en.yaml", "presentation/visual-pages.yaml", "review/README.md"
        )
        _relative_files(video) shouldBe Set(
          "document-project.yaml", "index.dox", "content/core-ja.yaml", "infographic/infographic.svg",
          "content/presentation-semantics-ja.yaml", "presentation/visual-pages.yaml", "review/README.md", "video/storyboard.md"
        )
        _relative_files(bok) shouldBe _relative_files(standard)
        _relative_files(bokvideo) shouldBe Set(
          "document-project.yaml", "index.dox", "content/core-en.yaml", "infographic/infographic.svg",
          "content/presentation-semantics-en.yaml", "presentation/visual-pages.yaml", "review/README.md", "video/storyboard.md"
        )
        Files.readString(standard.resolve("content/core-en.yaml"), StandardCharsets.UTF_8) should include("accepted: []")
        Files.exists(standard.resolve("target"), LinkOption.NOFOLLOW_LINKS) shouldBe false
        Files.exists(standard.resolve("dashboard.html"), LinkOption.NOFOLLOW_LINKS) shouldBe false
        standardoutput should startWith("Cozy Document Project Scaffold")
        videooutput should include("workspace: bok")
        bokoutput should include("profile: bok")
        bokvideooutput should include("profile: bok-video")
      }
    }

    "scaffold a deterministic authoring-incomplete Phase 48 presentation semantics sibling" in {
      _with_temp_dir("cozy-document-project-presentation-semantics-scaffold") { root =>
        Given("two distinct parents for identical public standard-video scaffold inputs")
        val firstparent = Files.createDirectory(root.resolve("first-parent"))
        val secondparent = Files.createDirectory(root.resolve("second-parent"))

        When("the identical projects are scaffolded")
        val command = List("document-project", "scaffold", "article-nine", "--profile", "standard-video", "--language", "en", "--workspace", "bok")
        _execute(command ++ List("--save", firstparent.toString))
        _execute(command ++ List("--save", secondparent.toString))
        val first = firstparent.resolve("article-nine.dox")
        val second = secondparent.resolve("article-nine.dox")
        val core = first.resolve("content/core-en.yaml")
        val semantics = first.resolve("content/presentation-semantics-en.yaml")
        val semanticstext = Files.readString(semantics, StandardCharsets.UTF_8)

        Then("the strict v2 authoring skeleton binds exact Core bytes without fabricated semantics or aliases")
        semanticstext should include("schema: cozy.content-core.presentation-semantics.v2")
        semanticstext should include("id: article-nine-presentation-en")
        semanticstext should include("id: article-nine:core:en")
        semanticstext should include(s"identity: sha256:${_sha256(core)}")
        semanticstext should include("composition: {}")
        semanticstext should include("storyFlow:\n  id: article-nine-story-flow-en\n  transitions: []")
        semanticstext should include("structures: []")
        semanticstext should include("schema: cozy.content-core.projection-policy.v1")
        semanticstext should include("id: article-nine-projection-policy-en")
        semanticstext should include("revision: 1")
        semanticstext should include("bindings: []")
        semanticstext should not include "intent:"
        semanticstext should not include "media:"
        semanticstext should not include "emphasis:"
        semanticstext should not include "claims:"
        semanticstext should not include "accepted:"

        And("identical profile inputs in distinct parents produce byte-identical Core and semantic authoring files")
        Files.readAllBytes(second.resolve("content/core-en.yaml")) shouldBe Files.readAllBytes(core)
        Files.readAllBytes(second.resolve("content/presentation-semantics-en.yaml")) shouldBe Files.readAllBytes(semantics)
      }
    }

    "derive the exact Phase 48 authoring-incomplete presentation-semantics state" in {
      _with_temp_dir("cozy-document-project-presentation-semantics-state") { root =>
        Given("a standard project with its exact Phase 48 presentation-semantics sibling")
        val project = _scaffolded_project(root, "semantic-state")

        When("inspect derives the disposable state and verify attempts strict admission")
        val inspect = _execute(List("document-project", "inspect", project.toString))
        val verification = _failure(List("document-project", "verify", project.toString))
        val state = Files.readString(project.resolve("target/document-project/state.yaml"), StandardCharsets.UTF_8)

        Then("authoring-incomplete exposes unavailable semantic values, the strict fault, and the complete missing Work Product row")
        inspect should include("presentationSemantics.schemaIdentity: cozy.content-core.presentation-semantics.v2")
        inspect should include("presentationSemantics.semanticIdentity: unavailable")
        inspect should include("presentationSemantics.state: authoring-incomplete")
        inspect should include("presentationSemantics.storyStepCount: unavailable")
        inspect should include("presentationSemantics.storyTransitionCount: unavailable")
        inspect should include("presentationSemantics.structureCount: unavailable")
        inspect should include("presentationSemantics.coverage: unavailable")
        _diagnostic_tokens(verification) shouldBe Vector("DP-SEM-006")
        state should include("id: presentation-semantics\n    role: authority\n    disposition: required\n    selection: required\n    criterion: presentation-semantics-validated\n    coverage: missing\n    currentness: missing\n    review: pending\n    readiness: blocked")
        Files.exists(project.resolve("target/document-project/presentation-confirmation.html"), LinkOption.NOFOLLOW_LINKS) shouldBe false
        Files.exists(project.resolve("target/document-project/presentation-confirmation.receipt.yaml"), LinkOption.NOFOLLOW_LINKS) shouldBe false
      }
    }

    "derive missing presentation-semantics source state" in {
      _with_temp_dir("cozy-document-project-presentation-semantics-missing") { root =>
        Given("a scaffolded project whose declared semantic source is absent")
        val project = _scaffolded_project(root, "semantic-missing")
        Files.delete(project.resolve("content/presentation-semantics-en.yaml"))

        When("inspect derives the disposable work-product state")
        val inspect = _execute(List("document-project", "inspect", project.toString))

        Then("the semantic Work Product is missing rather than invalid or stale")
        inspect should include("presentationSemantics.state: missing")
        inspect should include("presentationSemantics.contentCore.currentness: unavailable")
        Files.readString(project.resolve("target/document-project/state.yaml"), StandardCharsets.UTF_8) should include("id: presentation-semantics\n    role: authority\n    disposition: required\n    selection: required\n    criterion: presentation-semantics-validated\n    coverage: missing\n    currentness: missing\n    review: pending\n    readiness: blocked")
      }
    }

    "derive stale presentation-semantics state only for an exact prior Core binding" in {
      _with_temp_dir("cozy-document-project-presentation-semantics-stale") { root =>
        Given("a Phase 48 semantic source bound to the exact original Core identity")
        val project = _scaffolded_project(root, "semantic-stale")
        val core = project.resolve("content/core-en.yaml")
        val original = Files.readString(core, StandardCharsets.UTF_8)
        val originalidentity = _sha256(core)
        Files.writeString(core, original.replace("accepted: []", "accepted:\n  - id: accepted\n    text: accepted"), StandardCharsets.UTF_8)
        Files.writeString(project.resolve("content/presentation-semantics-en.yaml"), CozyDocumentProject._presentation_semantics_yaml("semantic-stale", "en", originalidentity), StandardCharsets.UTF_8)

        When("inspect and strict verify read the source after the Core bytes change")
        val inspect = _execute(List("document-project", "inspect", project.toString))
        val verification = _failure(List("document-project", "verify", project.toString))

        Then("only the direct strict Core-binding fault is represented as stale")
        inspect should include("presentationSemantics.state: stale")
        inspect should include("presentationSemantics.contentCore.currentness: stale")
        _diagnostic_tokens(verification) shouldBe Vector("DP-SEM-005")
        verification should include("path=$.contentCore")
      }
    }

    "derive invalid presentation-semantics state for incompatible candidates and independent root faults" in {
      _with_temp_dir("cozy-document-project-presentation-semantics-invalid") { root =>
        Given("a scaffolded project with candidate sources that are not the exact admitted Core binding")
        val project = _scaffolded_project(root, "semantic-invalid")
        val semantics = project.resolve("content/presentation-semantics-en.yaml")
        val core = project.resolve("content/core-en.yaml")
        val identity = _sha256(core)
        val wrongid = CozyDocumentProject._presentation_semantics_yaml("other-project", "en", identity)
        val wronglanguage = CozyDocumentProject._presentation_semantics_yaml("semantic-invalid", "ja", identity)
        Files.writeString(core, Files.readString(core, StandardCharsets.UTF_8).replace("accepted: []", "accepted:\n  - id: accepted\n    text: accepted"), StandardCharsets.UTF_8)
        val staleidentity = CozyDocumentProject._presentation_semantics_yaml("semantic-invalid", "en", identity)
        val stalewithrootorderfault = staleidentity.replace(
          "schema: cozy.content-core.presentation-semantics.v2\nid: semantic-invalid-presentation-en",
          "id: semantic-invalid-presentation-en\nschema: cozy.content-core.presentation-semantics.v2"
        )

        When("each incompatible candidate is inspected and strictly verified")
        Files.writeString(semantics, wrongid, StandardCharsets.UTF_8)
        val wrongidstate = _execute(List("document-project", "inspect", project.toString))
        val wrongidfailure = _failure(List("document-project", "verify", project.toString))
        Files.writeString(semantics, wronglanguage, StandardCharsets.UTF_8)
        val wronglanguagestate = _execute(List("document-project", "inspect", project.toString))
        val wronglanguagefailure = _failure(List("document-project", "verify", project.toString))
        Files.writeString(semantics, stalewithrootorderfault, StandardCharsets.UTF_8)
        val rootfaultstate = _execute(List("document-project", "inspect", project.toString))
        val rootfaultfailure = _failure(List("document-project", "verify", project.toString))

        Then("wrong Core id or language is unavailable and invalid, while an independent root fault overrides stale state")
        wrongidstate should include("presentationSemantics.state: invalid")
        wrongidstate should include("presentationSemantics.contentCore.currentness: unavailable")
        _diagnostic_tokens(wrongidfailure) shouldBe Vector("DP-SEM-005")
        wronglanguagestate should include("presentationSemantics.state: invalid")
        wronglanguagestate should include("presentationSemantics.contentCore.currentness: unavailable")
        _diagnostic_tokens(wronglanguagefailure) shouldBe Vector("DP-SEM-005")
        rootfaultstate should include("presentationSemantics.state: invalid")
        rootfaultstate should include("presentationSemantics.contentCore.currentness: stale")
        _diagnostic_tokens(rootfaultfailure) shouldBe Vector("DP-SEM-002")
      }
    }

    "verify a complete fixed-catalog presentation-semantics document and retain read-only coverage evidence" in {
      _with_temp_dir("cozy-document-project-presentation-semantics-current") { root =>
        Given("an existing standard English scaffold with a complete fixed-catalog presentation-semantics sibling")
        val project = _scaffolded_project(root, "semantic-current")
        _write_valid_presentation_semantics(project)

        When("inspect derives state and document-project verify admits the authored semantics")
        val inspect = _execute(List("document-project", "inspect", project.toString))
        val verify = _execute(List("document-project", "verify", project.toString))
        val state = Files.readString(project.resolve("target/document-project/state.yaml"), StandardCharsets.UTF_8)

        Then("the reports expose the current semantic identity, fixed-catalog counts, and satisfied projection coverage")
        inspect should include("presentationSemantics.state: current")
        inspect should include("presentationSemantics.semanticIdentity: sha256:")
        inspect should include("presentationSemantics.contentCore.currentness: current")
        inspect should include("presentationSemantics.storyStepCount: 2")
        inspect should include("presentationSemantics.storyTransitionCount: 1")
        inspect should include("presentationSemantics.structureCount: 2")
        inspect should include("presentationSemantics.projectionAvailability: available")
        inspect should include("presentationSemantics.coverage: satisfied")
        verify should include("Cozy Document Project Verify")
        verify should include("presentationSemantics.projectionAvailability: available")
        state should include("id: presentation-semantics\n    role: authority\n    disposition: required\n    selection: required\n    criterion: presentation-semantics-validated\n    coverage: satisfied\n    currentness: current\n    review: pending\n    readiness: ready")
        Files.exists(project.resolve("target/document-project/presentation-confirmation.html"), LinkOption.NOFOLLOW_LINKS) shouldBe false
        Files.exists(project.resolve("target/document-project/presentation-confirmation.receipt.yaml"), LinkOption.NOFOLLOW_LINKS) shouldBe false
      }
    }

    "retain current semantic state while blocking projection coverage for a missing Structure" in {
      _with_temp_dir("cozy-document-project-presentation-semantics-coverage") { root =>
        Given("an existing standard English scaffold with one absent Structure in an otherwise valid fixed-catalog document")
        val project = _scaffolded_project(root, "semantic-coverage")
        _write_valid_presentation_semantics(project, includeproblemstructure = false)

        When("inspect derives the semantic state and document-project verify reports projection completeness")
        val inspect = _execute(List("document-project", "inspect", project.toString))
        val verification = _execute(List("document-project", "verify", project.toString))
        val state = Files.readString(project.resolve("target/document-project/state.yaml"), StandardCharsets.UTF_8)

        Then("semantic validation remains current while typed projection availability fails and evidence readiness is blocked")
        inspect should include("presentationSemantics.state: current")
        inspect should include("presentationSemantics.storyStepCount: 2")
        inspect should include("presentationSemantics.structureCount: 1")
        inspect should include("presentationSemantics.projectionAvailability: failed")
        inspect should include("presentationSemantics.coverage: unavailable")
        verification should include("Cozy Document Project Verify")
        verification should include("presentationSemantics.projectionAvailability: failed")
        state should include("id: presentation-semantics\n    role: authority\n    disposition: required\n    selection: required\n    criterion: presentation-semantics-validated\n    coverage: missing\n    currentness: current\n    review: pending\n    readiness: blocked")
      }
    }

    "report malformed presentation-semantics YAML before authored root-order validation" in {
      _with_temp_dir("cozy-document-project-presentation-semantics-malformed") { root =>
        Given("a complete fixed-catalog presentation-semantics document")
        val project = _scaffolded_project(root, "semantic-malformed")
        val semantics = project.resolve("content/presentation-semantics-en.yaml")
        _write_valid_presentation_semantics(project)

        When("the semantic source is replaced with malformed YAML")
        Files.writeString(semantics, "schema: [\n", StandardCharsets.UTF_8)
        val failure = _failure(List("document-project", "verify", project.toString))

        Then("the parser diagnostic precedes authored root-order validation")
        _diagnostic_tokens(failure) shouldBe Vector("DP-SEM-001")
      }
    }

    "report parseable reordered, missing, and extra presentation-semantics root fields as authored root-order faults" in {
      _with_temp_dir("cozy-document-project-presentation-semantics-root-order") { root =>
        Given("a complete fixed-catalog presentation-semantics document in canonical authored root order")
        val project = _scaffolded_project(root, "semantic-root-order")
        _write_valid_presentation_semantics(project)
        val semantics = project.resolve("content/presentation-semantics-en.yaml")
        val canonical = Files.readString(semantics, StandardCharsets.UTF_8)
        val reordered = canonical.replace(
          "schema: cozy.content-core.presentation-semantics.v2\nid: semantic-root-order-presentation-en",
          "id: semantic-root-order-presentation-en\nschema: cozy.content-core.presentation-semantics.v2"
        )
        val missing = canonical.replaceFirst("(?s)storyFlow:\\n.*?\\nstructures:", "structures:")
        val extra = canonical + "extra: value\n"

        When("each raw root-map shape is verified through the strict file loader")
        val failures = Vector(reordered, missing, extra).map { value =>
          Files.writeString(semantics, value, StandardCharsets.UTF_8)
          _failure(List("document-project", "verify", project.toString))
        }

        Then("each parseable noncanonical raw root map is rejected with the established strict diagnostic")
        failures.foreach(value => _diagnostic_tokens(value) shouldBe Vector("DP-SEM-002"))
      }
    }

    "retain generated presentation semantics when a public video scaffold is promoted to the hidden Article 9 profile" in {
      _with_temp_dir("cozy-document-project-article-nine-promotion") { root =>
        Given("a public standard-video scaffold for an Article 9-style package")
        val parent = Files.createDirectory(root.resolve("parent"))
        _execute(List("document-project", "scaffold", "article-nine", "--profile", "standard-video", "--language", "ja", "--workspace", "bok", "--save", parent.toString))
        val project = parent.resolve("article-nine.dox")
        val descriptor = project.resolve("document-project.yaml")
        val semantics = project.resolve("content/presentation-semantics-ja.yaml")
        val semanticsbytes = Files.readAllBytes(semantics)

        When("the existing descriptor promotion changes only the registered hidden profile identity")
        Files.writeString(descriptor, Files.readString(descriptor, StandardCharsets.UTF_8).replace("profile: standard-video", "profile: simplemodeling-org-video"), StandardCharsets.UTF_8)

        Then("the generated strict semantic authority remains direct authored scaffold output rather than a hand-written promotion artifact")
        Files.isRegularFile(semantics, LinkOption.NOFOLLOW_LINKS) shouldBe true
        Files.readAllBytes(semantics) shouldBe semanticsbytes
        Files.readString(semantics, StandardCharsets.UTF_8) should include(s"identity: sha256:${_sha256(project.resolve("content/core-ja.yaml"))}")
        Files.readString(descriptor, StandardCharsets.UTF_8) should include("profile: simplemodeling-org-video")
        CozyDocumentProject._load_project(project).profile shouldBe "simplemodeling-org-video"
      }
    }

    "drive an Article 9-shaped hidden video profile through presentation currentness and recovery" in {
      _with_temp_dir("cozy-document-project-phase-493-s2") { root =>
        Given("an English Article 9 standard-video scaffold in the bok workspace")
        val parent = Files.createDirectory(root.resolve("article-nine-parent"))
        _execute(List("document-project", "scaffold", "article-nine", "--profile", "standard-video", "--language", "en", "--workspace", "bok", "--save", parent.toString))
        val project = parent.resolve("article-nine.dox")
        val descriptor = project.resolve("document-project.yaml")
        val semantics = project.resolve("content/presentation-semantics-en.yaml")
        val core = project.resolve("content/core-en.yaml")
        val html = project.resolve("target/document-project/presentation-confirmation.html")
        val receipt = project.resolve("target/document-project/presentation-confirmation.receipt.yaml")
        Files.writeString(descriptor, Files.readString(descriptor, StandardCharsets.UTF_8).replace("profile: standard-video", "profile: simplemodeling-org-video"), StandardCharsets.UTF_8)
        _activate_optional_work_products(project, "simplemodeling-org-video", Vector("presentation-confirmation-html"))
        _write_valid_presentation_semantics(project)
        val semanticsbytes = Files.readAllBytes(semantics)
        val descriptorbytes = Files.readAllBytes(descriptor)

        When("verify, inspect, plan, Dashboard, and the default presentation review are run")
        val verify = _execute(List("document-project", "verify", project.toString))
        val inspect = _execute(List("document-project", "inspect", project.toString))
        val plan = _execute(List("document-project", "plan", project.toString))
        val dashboardresult = _execute(List("document-project", "dashboard", project.toString))
        val dashboard = Files.readString(project.resolve("target/document-project/project-dashboard.html"), StandardCharsets.UTF_8)
        val review = _execute(List("document-project", "review", project.toString, "--kind", "presentation"))
        _execute(List("document-project", "inspect", project.toString))
        val initialstate = Files.readString(project.resolve("target/document-project/state.yaml"), StandardCharsets.UTF_8)
        val oldhtmlbytes = Files.readAllBytes(html)
        val oldreceiptbytes = Files.readAllBytes(receipt)

        Then("strict semantic coverage is current and the selected confirmation is planned and generated")
        verify should include("presentationSemantics.projectionAvailability: available")
        inspect should include("presentationSemantics.state: current")
        inspect should include("presentationSemantics.contentCore.currentness: current")
        inspect should include("presentationSemantics.coverage: satisfied")
        plan should include("active-optional: work-product presentation-confirmation-html [review-projection, optional; selected]")
        dashboardresult should include("Dashboard")
        dashboard should include("presentation-confirmation-html")
        dashboard should include("Presentation confirmation HTML")
        review should include("Presentation Confirmation")
        Files.isRegularFile(html, LinkOption.NOFOLLOW_LINKS) shouldBe true
        Files.isRegularFile(receipt, LinkOption.NOFOLLOW_LINKS) shouldBe true
        Files.readString(receipt, StandardCharsets.UTF_8) should include("\"projectionIdentity\":")
        initialstate should include("id: presentation-confirmation-html\n    role: review-projection\n    disposition: optional\n    selection: active-optional\n    criterion: presentation-confirmation-rendered\n    coverage: satisfied\n    currentness: current\n    review: pending\n    readiness: ready")

        When("the Content Core bytes change and read-only inspection plus public presentation review run")
        Files.writeString(core, Files.readString(core, StandardCharsets.UTF_8).replace("accepted: []", "accepted:\n  - id: article-nine-change\n    text: Article Nine Change"), StandardCharsets.UTF_8)
        val staleinspect = _execute(List("document-project", "inspect", project.toString))
        val stale = Files.readString(project.resolve("target/document-project/state.yaml"), StandardCharsets.UTF_8)
        val refusal = _failure(List("document-project", "review", project.toString, "--kind", "presentation"))

        Then("the Core change makes both semantic authority and confirmation stale without rewriting retained bytes")
        staleinspect should include("presentationSemantics.contentCore.currentness: stale")
        stale should include("id: presentation-confirmation-html\n    role: review-projection\n    disposition: optional\n    selection: active-optional\n    criterion: presentation-confirmation-rendered\n    coverage: missing\n    currentness: stale")
        _diagnostic_tokens(refusal) shouldBe Vector("DP-SEM-005")
        Files.readAllBytes(semantics) shouldBe semanticsbytes
        Files.readAllBytes(descriptor) shouldBe descriptorbytes
        Files.readAllBytes(html) shouldBe oldhtmlbytes
        Files.readAllBytes(receipt) shouldBe oldreceiptbytes

        Given("the stale project is explicitly reauthored through the existing strict semantic authoring helper")
        _write_valid_presentation_semantics(project)
        Files.readAllBytes(html) shouldBe oldhtmlbytes
        Files.readAllBytes(receipt) shouldBe oldreceiptbytes

        When("the public presentation review runs again after reauthoring")
        val reauthoredinspect = _execute(List("document-project", "inspect", project.toString))
        val reauthoredstate = Files.readString(project.resolve("target/document-project/state.yaml"), StandardCharsets.UTF_8)
        _execute(List("document-project", "review", project.toString, "--kind", "presentation"))
        _execute(List("document-project", "inspect", project.toString))
        val recovered = Files.readString(project.resolve("target/document-project/state.yaml"), StandardCharsets.UTF_8)

        Then("only the same public confirmation route replaces retained bytes and recovers ready evidence")
        reauthoredinspect should include("presentationSemantics.state: current")
        reauthoredstate should include("id: presentation-confirmation-html\n    role: review-projection\n    disposition: optional\n    selection: active-optional\n    criterion: presentation-confirmation-rendered\n    coverage: missing\n    currentness: stale")
        recovered should include("id: presentation-confirmation-html\n    role: review-projection\n    disposition: optional\n    selection: active-optional\n    criterion: presentation-confirmation-rendered\n    coverage: satisfied\n    currentness: current\n    review: pending\n    readiness: ready")
        Files.readAllBytes(html) should not equal oldhtmlbytes
        Files.readAllBytes(receipt) should not equal oldreceiptbytes
      }
    }

    "inspect the exact closed descriptor and Core while strict verify rejects incomplete semantics" in {
      _with_temp_dir("cozy-document-project-admission") { root =>
        Given("a standard scaffold with the closed descriptor and Content Core")
        val parent = Files.createDirectory(root.resolve("parent"))
        _execute(List("document-project", "scaffold", "sample", "--profile", "standard", "--language", "en", "--workspace", "directory", "--save", parent.toString))
        val project = parent.resolve("sample.dox")

        When("inspect reads the admitted project and strict verify validates its Presentation Semantics")
        val inspect = _execute(List("document-project", "inspect", project.toString))
        val verify = _failure(List("document-project", "verify", project.toString))

        Then("inspect identifies the project and descriptor schema while strict verify returns the semantic diagnostic")
        inspect should startWith("Cozy Document Project Inspect")
        inspect should include("project: sample")
        inspect should include("schema: cozy.document-project.v2")
        verify should include("DP-SEM-")

        And("the successful inspection, rather than strict verify, exposes only a deterministic disposable state cache")
        val state = project.resolve("target/document-project/state.yaml")
        Files.isRegularFile(state, LinkOption.NOFOLLOW_LINKS) shouldBe true
        inspect should include("state: target/document-project/state.yaml")
        val firststate = Files.readString(state, StandardCharsets.UTF_8)
        firststate should include("schema: cozy.document-project-state.v2")
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
        Files.readString(state, StandardCharsets.UTF_8) should include("schema: cozy.document-project-state.v2")

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

    "admit only v2 descriptors before a project action can write derived state or evidence" in {
      _with_temp_dir("cozy-document-project-v2-only") { root =>
        Given("a scaffolded v2 Document Project with no derived outputs")
        val parent = Files.createDirectory(root.resolve("parent"))
        _execute(List("document-project", "scaffold", "sample", "--profile", "standard", "--language", "en", "--workspace", "directory", "--save", parent.toString))
        val project = parent.resolve("sample.dox")
        val descriptor = project.resolve("document-project.yaml")

        When("the descriptor claims the retired v1 schema")
        Files.writeString(descriptor, Files.readString(descriptor, StandardCharsets.UTF_8).replace("cozy.document-project.v2", "cozy.document-project.v1"), StandardCharsets.UTF_8)
        val failure = _failure(List("document-project", "inspect", project.toString))

        Then("admission rejects the retired identity before state or evidence is created")
        failure should include("DP-DESC-002")
        Files.exists(project.resolve("target/document-project/state.yaml"), LinkOption.NOFOLLOW_LINKS) shouldBe false
        Files.exists(project.resolve("evidence"), LinkOption.NOFOLLOW_LINKS) shouldBe false
      }
    }

    "reject closed unsafe v2 optional selection and semantic scope descriptors" in {
      _with_temp_dir("cozy-document-project-v2-closed-descriptor") { root =>
        Given("a scaffolded v2 descriptor with its exact self semantic scope")
        val parent = Files.createDirectory(root.resolve("parent"))
        _execute(List("document-project", "scaffold", "sample", "--profile", "standard", "--language", "en", "--workspace", "directory", "--save", parent.toString))
        val project = parent.resolve("sample.dox")
        val descriptor = project.resolve("document-project.yaml")
        val original = Files.readString(descriptor, StandardCharsets.UTF_8)

        When("optional selection duplicates, selects required or profile-disabled products, or semantic scope is malformed")
        val duplicate = original.replace("activeOptionalWorkProducts: []", "activeOptionalWorkProducts:\n  - core-review-html\n  - core-review-html")
        val required = original.replace("activeOptionalWorkProducts: []", "activeOptionalWorkProducts:\n  - content-core")
        val disabled = original.replace("activeOptionalWorkProducts: []", "activeOptionalWorkProducts:\n  - video-review")
        val unknownscope = original.replace("workProducts: []", "workProducts: []\n      unexpected: value")
        val missingself = original.replace("contentCore: sample:core:en", "contentCore: another:core:en")
        val failures = Vector(duplicate, required, disabled, unknownscope, missingself).map { value =>
          Files.writeString(descriptor, value, StandardCharsets.UTF_8)
          _failure(List("document-project", "inspect", project.toString))
        }

        Then("each unsafe closed shape is rejected before any derived state is written")
        failures.foreach(_ should include("DP-DESC-002"))
        Files.exists(project.resolve("target/document-project/state.yaml"), LinkOption.NOFOLLOW_LINKS) shouldBe false
      }
    }

    "resolve deterministic v2 required active inactive and profile-disabled Work Products" in {
      _with_temp_dir("cozy-document-project-v2-selection") { root =>
        Given("a v2 standard project that selects only the article review Work Product")
        val project = _scaffolded_project(root, "selection")
        val descriptor = project.resolve("document-project.yaml")
        Files.writeString(descriptor, Files.readString(descriptor, StandardCharsets.UTF_8).replace("activeOptionalWorkProducts: []", "activeOptionalWorkProducts:\n  - article-review-html"), StandardCharsets.UTF_8)

        When("plan, state, and selected sidecar evidence resolve the descriptor selection")
        val plan = _execute(List("document-project", "plan", project.toString))
        _write_sidecar(project, activeoptionalworkproducts = Vector("article-review-html"))
        _execute(List("document-project", "inspect", project.toString))
        val state = Files.readString(project.resolve("target/document-project/state.yaml"), StandardCharsets.UTF_8)
        val sidecar = Files.readString(project.resolve("evidence/document-project.yaml"), StandardCharsets.UTF_8)

        Then("required, active optional, inactive optional, and profile-disabled products remain distinct in canonical order")
        plan should include("required: work-product content-core [authority, required]")
        plan should include("active-optional: work-product article-review-html [review-projection, optional; selected]")
        plan should include("inactive-optional: work-product core-review-html [review-projection, optional; not-selected]")
        plan should include("profile-disabled: work-product video-review [review-projection, disabled: profile standard disables video branch]")
        state should include("id: article-review-html\n    role: review-projection\n    disposition: optional\n    selection: active-optional")
        state should include("id: core-review-html\n    role: review-projection\n    disposition: optional\n    selection: inactive-optional\n    criterion: core-review-rendered\n    coverage: not-applicable\n    currentness: nonparticipating\n    review: not-applicable\n    readiness: not-selected")
        state should include("id: video-review\n    role: review-projection\n    disposition: disabled\n    selection: profile-disabled\n    criterion: video-review-rendered\n    coverage: not-applicable\n    currentness: not-applicable")
        sidecar.indexOf("id: content-core") should be < sidecar.indexOf("id: article-review-html")
        sidecar.indexOf("id: article-review-html") should be < sidecar.indexOf("id: visual-pages")
        sidecar should not include "id: core-review-html"
      }
    }

    "resolve article review HTML as a selected native typed dry run" in {
      _with_temp_dir("cozy-document-project-v2-article-review") { root =>
        Given("a v2 project that explicitly selects article-review-html")
        val project = _scaffolded_project(root, "article-review")
        val descriptor = project.resolve("document-project.yaml")
        Files.writeString(descriptor, Files.readString(descriptor, StandardCharsets.UTF_8).replace("activeOptionalWorkProducts: []", "activeOptionalWorkProducts:\n  - article-review-html"), StandardCharsets.UTF_8)

        When("the declared review operation is resolved without native invocation")
        val output = _execute(List("document-project", "run", project.toString, "--operation", "article.render-review", "--dry-run"))

        Then("the selected first-class Work Product reports its typed dry-run resolution without generating output")
        output should include("operation: article.render-review")
        output should include("mode: dry-run")
        output should include("outcome: resolved")
      }
    }

    "report selected article review HTML as missing until default generation" in {
      _with_temp_dir("cozy-document-project-v2-article-review-dashboard") { root =>
        Given("a v2 project that selects article-review-html with all review projection prerequisites")
        val project = _scaffolded_project(root, "article-review-dashboard")
        val descriptor = project.resolve("document-project.yaml")
        Files.writeString(descriptor, Files.readString(descriptor, StandardCharsets.UTF_8).replace("activeOptionalWorkProducts: []", "activeOptionalWorkProducts:\n  - article-review-html"), StandardCharsets.UTF_8)

        When("the dashboard is generated for the selected project")
        _execute(List("document-project", "dashboard", project.toString))

        Then("the selected product reports its missing default output and generation action")
        val dashboard = Files.readString(
          project.resolve("target/document-project/project-dashboard.html"),
          StandardCharsets.UTF_8
        )
        dashboard should include("Generate Article review HTML")
        dashboard should include("article-review-html</th><td>missing</td><td>missing</td><td>pending</td><td>blocked</td><td>default review HTML is not generated")
      }
    }

    "present a selected initial project as a user-first dashboard with a safe candidate action" in {
      _with_temp_dir("cozy-document-project-dashboard-user-first-initial") { root =>
        Given("a standard project that selects the Content Core candidate and Article review Work Products")
        val project = _scaffolded_project(root, "dashboard-user-first-initial")
        _activate_optional_work_products(project, "standard", Vector("content-core-candidate", "article-review-html"))

        When("the read-only dashboard is generated")
        _execute(List("document-project", "dashboard", project.toString))
        val dashboard = Files.readString(project.resolve("target/document-project/project-dashboard.html"), StandardCharsets.UTF_8)

        Then("the primary action surface leads with bilingual stage, change, blocker, review, and safe-action outcomes")
        val primary = dashboard.indexOf("id=\"primary-action-surface\"")
        val secondary = dashboard.indexOf("id=\"secondary-diagnostics\"")
        val primarysurface = dashboard.substring(primary, secondary)
        val secondarydiagnostics = dashboard.substring(secondary)
        primary should be >= 0
        secondary should be > primary
        dashboard should include("Current production stage / 現在の制作段階")
        dashboard should include("Latest observed change / 最新に確認された変化")
        dashboard should include("Prioritized blockers / 優先ブロッカー")
        dashboard should include("Pending review / レビュー待ち")
        dashboard should include("Recommended next action / 推奨される次のアクション")
        primarysurface should include("content-core-candidate")
        primarysurface should include("source or retained evidence is not present")
        primarysurface should include("article-review-html")

        val eligible = primarysurface.substring(
          primarysurface.indexOf("Eligible safe actions"),
          primarysurface.indexOf("Optional deliverable selection")
        )
        val candidateactionstart = eligible.indexOf("<strong>Content Core candidate</strong>")
        val candidateaction = eligible.substring(candidateactionstart, eligible.indexOf("</li>", candidateactionstart))
        val contentcoreactionstart = eligible.indexOf("<strong>Content Core</strong>")
        val contentcoreaction = eligible.substring(contentcoreactionstart, eligible.indexOf("</li>", contentcoreactionstart))
        candidateaction should include("<code>content-core-candidate</code>")
        candidateaction should include("cozy document-project content-core candidate &lt;project&gt; &lt;dialogue&gt;")
        contentcoreaction should include("<code>content-core</code>")
        contentcoreaction should include("cozy document-project content-core candidate &lt;project&gt; &lt;dialogue&gt;")
        primarysurface should not include "content-core.compose"
        secondarydiagnostics should include("content-core.compose")
      }
    }

    "recommend Content Core candidate start for a required Core blocker with an inactive candidate option" in {
      _with_temp_dir("cozy-document-project-dashboard-user-first-core-blocker") { root =>
        Given("a standard project whose retained sidecar marks required Content Core evidence missing while candidate remains inactive")
        val project = _scaffolded_project(root, "dashboard-user-first-core-blocker")
        _write_sidecar(project, activeoptionalworkproducts = Vector("article-review-html"))

        When("the read-only dashboard is generated")
        _execute(List("document-project", "dashboard", project.toString))
        val dashboard = Files.readString(project.resolve("target/document-project/project-dashboard.html"), StandardCharsets.UTF_8)

        Then("the required Core blocker recommends the explicit candidate-start contract")
        val recommendation = dashboard.substring(dashboard.indexOf("Recommended next action"), dashboard.indexOf("Eligible safe actions"))
        recommendation should include("content-core</code>")
        recommendation should include("cozy document-project content-core candidate &lt;project&gt; &lt;dialogue&gt;")
        dashboard should include("content-core-candidate")
        dashboard should include("inactive-optional / 無効なオプション")
      }
    }

    "show the first stale selected review change and its safe review command" in {
      _with_temp_dir("cozy-document-project-dashboard-user-first-stale") { root =>
        Given("a selected Article review with a generated review receipt")
        val project = _scaffolded_project(root, "dashboard-user-first-stale")
        _activate_optional_work_products(project, "standard", Vector("article-review-html"))
        _execute(List("document-project", "review", project.toString, "--kind", "article"))
        Files.writeString(project.resolve("index.dox"), Files.readString(project.resolve("index.dox"), StandardCharsets.UTF_8) + "\nChanged article source.\n", StandardCharsets.UTF_8)

        When("the selected review dashboard is generated after its source changes")
        _execute(List("document-project", "dashboard", project.toString))
        val dashboard = Files.readString(project.resolve("target/document-project/project-dashboard.html"), StandardCharsets.UTF_8)

        Then("the latest change preserves the exact stale reason and exposes the existing review command")
        dashboard should include("Latest observed change / 最新に確認された変化")
        dashboard should include("a declared dependency is stale")
        dashboard should include("cozy document-project review &lt;project&gt; --kind article")
        dashboard should include("selected-by-contract preview")
      }
    }

    "show active and inactive optional selection with descriptor activation guidance" in {
      _with_temp_dir("cozy-document-project-dashboard-user-first-optional") { root =>
        Given("a standard project with one selected and one unselected optional Work Product")
        val project = _scaffolded_project(root, "dashboard-user-first-optional")
        _activate_optional_work_products(project, "standard", Vector("article-review-html"))

        When("the dashboard is generated without writing the descriptor")
        val descriptorbefore = Files.readAllBytes(project.resolve("document-project.yaml"))
        _execute(List("document-project", "dashboard", project.toString))
        val dashboard = Files.readString(project.resolve("target/document-project/project-dashboard.html"), StandardCharsets.UTF_8)

        Then("active and inactive options and the closed authoring contract are explicit")
        dashboard should include("Optional deliverable selection / オプション成果物の選択")
        dashboard should include("article-review-html")
        dashboard should include("active-optional / 有効なオプション")
        dashboard should include("core-review-html")
        dashboard should include("inactive-optional / 無効なオプション")
        dashboard should include("activeOptionalWorkProducts")
        dashboard should include("Profile-disabled Work Products cannot be activated")
        Files.readAllBytes(project.resolve("document-project.yaml")) shouldBe descriptorbefore
      }
    }

    "keep workflow and evidence diagnostics below the primary action surface" in {
      _with_temp_dir("cozy-document-project-dashboard-user-first-secondary") { root =>
        Given("an admitted standard project")
        val project = _scaffolded_project(root, "dashboard-user-first-secondary")

        When("the dashboard is generated")
        _execute(List("document-project", "dashboard", project.toString))
        val dashboard = Files.readString(project.resolve("target/document-project/project-dashboard.html"), StandardCharsets.UTF_8)

        Then("one secondary details surface retains the prior diagnostic views after the primary action surface")
        dashboard should include("<details id=\"secondary-diagnostics\">")
        dashboard should include("<summary>Secondary diagnostics / 二次診断</summary>")
        dashboard should include("<h2>Workflow</h2>")
        dashboard should include("<h2>Work Product matrix</h2>")
        dashboard should include("<h2>Criterion coverage</h2>")
        dashboard should include("<h2>Work Product details</h2>")
        dashboard should include("<h2>Review and final artifact links</h2>")
        dashboard should include("<h2>Retained attempts</h2>")
        dashboard should include("<h2>Responsibility boundary</h2>")
        dashboard.indexOf("<main id=\"primary-action-surface\">") should be < dashboard.indexOf("<details id=\"secondary-diagnostics\">")
      }
    }

    "report the missing source reason for article review when infographic is absent" in {
      _with_temp_dir("cozy-document-project-v2-article-review-missing-source") { root =>
        Given("a selected article review project with its infographic source absent")
        val project = _scaffolded_project(root, "article-review-missing-source")
        _activate_optional_work_products(project, "standard", Vector("article-review-html"))
        Files.delete(project.resolve("infographic/infographic.svg"))

        When("inspect and dashboard derive the selected article review state")
        _execute(List("document-project", "inspect", project.toString))
        _execute(List("document-project", "dashboard", project.toString))
        val state = Files.readString(project.resolve("target/document-project/state.yaml"), StandardCharsets.UTF_8)
        val dashboard = Files.readString(project.resolve("target/document-project/project-dashboard.html"), StandardCharsets.UTF_8)

        Then("both projections report the absent source before missing generated output")
        state should include("id: article-review-html\n    role: review-projection\n    disposition: optional\n    selection: active-optional\n    criterion: article-review-rendered\n    coverage: missing\n    currentness: missing\n    review: pending\n    readiness: blocked\n    reason: \"source or retained evidence is not present\"")
        dashboard should include("article-review-html</th><td>missing</td><td>missing</td><td>pending</td><td>blocked</td><td>source or retained evidence is not present")
      }
    }

    "report the missing source reason for video review when infographic is absent" in {
      _with_temp_dir("cozy-document-project-v2-video-review-missing-source") { root =>
        Given("a standard-video project with its infographic source absent")
        val parent = Files.createDirectory(root.resolve("video-review-missing-source-parent"))
        _execute(List("document-project", "scaffold", "video-review-missing-source", "--profile", "standard-video", "--language", "en", "--workspace", "directory", "--save", parent.toString))
        val project = parent.resolve("video-review-missing-source.dox")
        Files.delete(project.resolve("infographic/infographic.svg"))

        When("inspect and dashboard derive the selected video review state")
        _execute(List("document-project", "inspect", project.toString))
        _execute(List("document-project", "dashboard", project.toString))
        val state = Files.readString(project.resolve("target/document-project/state.yaml"), StandardCharsets.UTF_8)
        val dashboard = Files.readString(project.resolve("target/document-project/project-dashboard.html"), StandardCharsets.UTF_8)

        Then("both projections report the absent source before missing generated output")
        state should include("id: video-review\n    role: review-projection\n    disposition: required\n    selection: required\n    criterion: video-review-rendered\n    coverage: missing\n    currentness: missing\n    review: pending\n    readiness: blocked\n    reason: \"source or retained evidence is not present\"")
        dashboard should include("video-review</th><td>missing</td><td>missing</td><td>pending</td><td>blocked</td><td>source or retained evidence is not present")
      }
    }

    "retain article review HTML as missing when a valid sidecar lacks generated output evidence" in {
      _with_temp_dir("cozy-document-project-v2-article-review-sidecar") { root =>
        Given("a v2 project that selects article-review-html and declares its current index.dox source in a valid sidecar")
        val project = _scaffolded_project(root, "article-review-sidecar")
        _write_sidecar(
          project,
          evidence = Map("article-review-html" -> _file_evidence(project, "index.dox", "source")),
          activeoptionalworkproducts = Vector("article-review-html")
        )

        When("the selected project is inspected and its dashboard is generated")
        _execute(List("document-project", "inspect", project.toString))
        _execute(List("document-project", "dashboard", project.toString))
        val state = Files.readString(project.resolve("target/document-project/state.yaml"), StandardCharsets.UTF_8)
        val dashboard = Files.readString(project.resolve("target/document-project/project-dashboard.html"), StandardCharsets.UTF_8)

        Then("the retained sidecar does not replace the required local generated output evidence")
        state should include("id: article-review-html\n    role: review-projection\n    disposition: optional\n    selection: active-optional\n    criterion: article-review-rendered\n    coverage: missing\n    currentness: missing\n    review: pending\n    readiness: blocked\n    reason: \"default review HTML is not generated\"")
        dashboard should include("Generate Article review HTML")
        dashboard should include("article-review-html</th><td>missing</td><td>missing</td><td>pending</td><td>blocked</td><td>default review HTML is not generated")
      }
    }

    "derive stale article review state through receipt-bound dependencies and failed attempts" in {
      _with_temp_dir("cozy-document-project-v2-article-review-propagation") { root =>
        Given("a selected article-review-html product with valid source evidence and a current Content Core identity")
        val project = _scaffolded_project(root, "article-review-propagation")
        _write_sidecar(
          project,
          evidence = Map(
            "content-core" -> _file_evidence(project, "content/core-en.yaml", "source"),
            "article-review-html" -> _file_evidence(project, "index.dox", "source")
          ),
          activeoptionalworkproducts = Vector("article-review-html")
        )
        _execute(List("document-project", "review", project.toString, "--kind", "article"))
        Files.writeString(project.resolve("content/core-en.yaml"), _core_yaml("article-review-propagation", "en", "changed Content Core"), StandardCharsets.UTF_8)
        val attemptid = "44444444-4444-4444-8444-444444444444"
        val attempt = project.resolve(s"evidence/attempts/$attemptid.yaml")
        Files.createDirectories(attempt.getParent)
        Files.writeString(attempt, _failed_attempt_yaml(project, attemptid, "article.render-review", "cozy-review-projection", "standard"), StandardCharsets.UTF_8)

        When("inspect and dashboard derive state with the stale dependency and retained failed attempt")
        _execute(List("document-project", "inspect", project.toString))
        _execute(List("document-project", "dashboard", project.toString))

        Then("the receipt-bound selected article review becomes stale and retains historical failed evidence")
        val state = Files.readString(project.resolve("target/document-project/state.yaml"), StandardCharsets.UTF_8)
        val dashboard = Files.readString(project.resolve("target/document-project/project-dashboard.html"), StandardCharsets.UTF_8)
        state should include("id: content-core\n    role: authority\n    disposition: required\n    selection: required\n    criterion: content-core-accepted\n    coverage: missing\n    currentness: stale")
        state should include("id: article-review-html\n    role: review-projection\n    disposition: optional\n    selection: active-optional\n    criterion: article-review-rendered\n    coverage: missing\n    currentness: stale\n    review: pending\n    readiness: blocked\n    reason: \"a declared dependency is stale\"")
        state should include(s"path: evidence/attempts/$attemptid.yaml")
        dashboard should include("failed; historical attempt")
        dashboard should include("Generate Article review HTML")
      }
    }

    "admit non-empty semantic scope identities without a case or syntax restriction" in {
      _with_temp_dir("cozy-document-project-v2-semantic-identity") { root =>
        Given("a v2 descriptor with a second locale identity containing uppercase and punctuation")
        val project = _scaffolded_project(root, "semantic-identity")
        val descriptor = project.resolve("document-project.yaml")
        val original = Files.readString(descriptor, StandardCharsets.UTF_8)
        val identityscope = original.replace(
          "      workProducts: []",
          """      workProducts: []
            |    - project: semantic-identity-ja
            |      language: ja
            |      contentCore: Shared::Core@2026!
            |      workProducts:
            |        - id: article-review-html
            |          identity: Review.HTML@2026!
            |""".stripMargin
        )
        Files.writeString(descriptor, identityscope, StandardCharsets.UTF_8)

        When("the semantic identity hooks are admitted without reading another project")
        val inspect = _execute(List("document-project", "inspect", project.toString))

        Then("the non-empty identities remain valid regardless of case or punctuation")
        inspect should include("schema: cozy.document-project.v2")
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

      "register required presentation semantics and optional presentation confirmation in every profile" in {
        Given("the immutable document-production definition")
        val definition = CozyDocumentWorkflow.documentProduction
        val semantics = definition.workProducts.find(_.id == "presentation-semantics").get
        val confirmation = definition.workProducts.find(_.id == "presentation-confirmation-html").get

        When("the static presentation Work Products and their profile bindings are resolved")
        val bindings = definition.profiles.map { profile =>
          profile.id -> profile.bindings.map(binding => binding.workProductId -> binding.disposition).toMap
        }.toMap

        Then("presentation semantics is the required strict authority with its closed static links")
        semantics.role shouldBe CozyDocumentWorkflow.WorkProductRole.Authority
        semantics.producer shouldBe "presentation.author"
        semantics.criteria shouldBe Vector("presentation-semantics-validated")
        semantics.dependencies shouldBe Vector("content-core")
        semantics.gates shouldBe Vector("presentation-semantics-validation")
        semantics.evidenceReferences shouldBe Vector("presentation-semantics-reference")
        bindings.values.foreach { binding =>
          binding("presentation-semantics") shouldBe CozyDocumentWorkflow.WorkProductDisposition.Required
        }

        And("presentation confirmation remains a distinct optional review projection in every profile")
        confirmation.role shouldBe CozyDocumentWorkflow.WorkProductRole.ReviewProjection
        confirmation.producer shouldBe "presentation.render-confirmation"
        confirmation.criteria shouldBe Vector("presentation-confirmation-rendered")
        confirmation.dependencies shouldBe Vector("presentation-semantics")
        confirmation.gates shouldBe Vector("presentation-confirmation")
        confirmation.evidenceReferences shouldBe Vector("presentation-confirmation-reference")
        bindings.values.foreach { binding =>
          binding("presentation-confirmation-html") shouldBe CozyDocumentWorkflow.WorkProductDisposition.Optional
        }
      }

      "bind the frozen presentation producers consumers and receipt edges exactly once" in {
        Given("the immutable document-production definition")
        val definition = CozyDocumentWorkflow.documentProduction
        val operations = definition.operations.map(operation => operation.id -> operation).toMap
        val products = definition.workProducts.map(product => product.id -> product).toMap
        val providers = definition.providerBindings.map(binding => binding.id -> binding.provider).toMap

        When("the presentation authority and confirmation operations are inspected")
        val author = operations("presentation.author")
        val confirmation = operations("presentation.render-confirmation")
        val receipt = operations("operation-receipt.record")

        Then("each operation uses its closed Cozy-owned provider and exact product boundary")
        author.providerBinding shouldBe "cozy-presentation-semantics"
        author.consumes shouldBe Vector("content-core")
        author.produces shouldBe Vector("presentation-semantics")
        confirmation.providerBinding shouldBe "cozy-presentation-confirmation"
        confirmation.consumes shouldBe Vector("presentation-semantics")
        confirmation.produces shouldBe Vector("presentation-confirmation-html")
        providers("cozy-presentation-semantics") shouldBe "Cozy Presentation Semantics adapter"
        providers("cozy-presentation-confirmation") shouldBe "Cozy Presentation Confirmation adapter"

        And("the consumers and future operation receipt record both declared presentation products")
        products("content-core").consumers shouldBe Vector("content-core.render-review", "presentation.author", "article.compose", "article.render-review", "visual-pages.author", "infographic.compose", "video.compose-storyboard", "slide-logical-chart.render-review", "video-logical-chart.render-review", "operation-receipt.record")
        products("presentation-semantics").consumers shouldBe Vector("article.compose", "visual-pages.author", "video.compose-storyboard", "presentation.render-confirmation", "operation-receipt.record")
        products("presentation-confirmation-html").consumers shouldBe Vector("operation-receipt.record")
        receipt.consumes should contain("presentation-semantics")
        receipt.consumes should contain("presentation-confirmation-html")
      }

      "order downstream semantic production after the shared presentation authority without a descriptor DAG" in {
        _with_temp_dir("cozy-document-project-p49-static-workflow") { root =>
          Given("the immutable workflow definition and a scaffolded closed descriptor")
          val definition = CozyDocumentWorkflow.documentProduction
          val productids = definition.workProducts.map(_.id)
          val project = _scaffolded_project(root, "static-workflow")
          val descriptor = Files.readString(project.resolve("document-project.yaml"), StandardCharsets.UTF_8)

          When("the static producer and Work Product dependency edges are compared")
          val operations = definition.operations.map(operation => operation.id -> operation).toMap
          val products = definition.workProducts.map(product => product.id -> product).toMap

          Then("the authority follows Content Core and precedes its article Visual Page video and confirmation consumers")
          val contentcoreindex = productids.indexOf("content-core")
          val presentationindex = productids.indexOf("presentation-semantics")
          val confirmationindex = productids.indexOf("presentation-confirmation-html")
          presentationindex should be > contentcoreindex
          productids.indexOf("article-source") should be > presentationindex
          productids.indexOf("visual-pages") should be > presentationindex
          productids.indexOf("video-storyboard") should be > presentationindex
          confirmationindex should be > productids.indexOf("video-storyboard")
          Vector("article.compose", "visual-pages.author", "video.compose-storyboard").foreach { operationid =>
            operations(operationid).consumes should contain("presentation-semantics")
          }
          Vector("article-source", "visual-pages", "video-storyboard").foreach { productid =>
            products(productid).dependencies should contain("presentation-semantics")
          }

          And("the graph remains an immutable Workflow Definition rather than descriptor-owned state")
          definition.id shouldBe "document-production"
          definition.workProducts.map(_.id) should contain("presentation-semantics")
          definition.workProducts.map(_.id) should contain("presentation-confirmation-html")
          descriptor should not include "presentation-semantics"
          descriptor should not include "presentation-confirmation-html"
          descriptor should not include "presentation.author"
          descriptor should not include "presentation.render-confirmation"
        }
      }

      "keep article expression review distinct from shared presentation confirmation" in {
        Given("the immutable document-production definition")
        val definition = CozyDocumentWorkflow.documentProduction
        val products = definition.workProducts.map(product => product.id -> product).toMap

        When("the two review-projection identities are compared")
        val article = products("article-review-html")
        val confirmation = products("presentation-confirmation-html")

        Then("article review retains its article expression operation and dependencies")
        article.producer shouldBe "article.render-review"
        article.dependencies shouldBe Vector("content-core", "article-source", "visual-pages")

        And("presentation confirmation retains its separate shared-semantics operation and dependency")
        confirmation.producer shouldBe "presentation.render-confirmation"
        confirmation.dependencies shouldBe Vector("presentation-semantics")
        confirmation.id should not be article.id
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
        standardplan should include("required: work-product content-core [authority, required]")
        standardplan should include("profile-disabled: work-product video-storyboard [plan, disabled: profile standard disables video branch]")
        standardplan should include("blocked: operation article.render-pdf [native execution is declared only for currently available providers; Operation Attempts are historical evidence]")
        standardplan should include("eligible: operation article.render-pdf [provider: smartdox-rendering]")
        videoplan should include("required: work-product video-storyboard [plan, required]")
        videoplan should not include "profile standard disables video branch"

        And("planning creates neither generated authority nor evidence")
        Files.exists(standard.resolve("target"), LinkOption.NOFOLLOW_LINKS) shouldBe false
        Files.exists(standard.resolve("state"), LinkOption.NOFOLLOW_LINKS) shouldBe false
        Files.exists(standard.resolve("operation-receipt-evidence"), LinkOption.NOFOLLOW_LINKS) shouldBe false
      }
    }

    "project presentation semantics blockers and the deterministic authoring action through plan" in {
      _with_temp_dir("cozy-document-project-presentation-semantics-plan") { root =>
        Given("four projects whose Content Core is admitted while Presentation Semantics is missing, incomplete, invalid, or stale")
        def _admit_core_(project: Path): Unit = {
          val slug = project.getFileName.toString.stripSuffix(".dox")
          val core = project.resolve("content/core-en.yaml")
          Files.writeString(core, Files.readString(core, StandardCharsets.UTF_8).replace("accepted: []", "accepted:\n  - id: accepted-core\n    text: Accepted core"), StandardCharsets.UTF_8)
          Files.writeString(project.resolve("content/presentation-semantics-en.yaml"), CozyDocumentProject._presentation_semantics_yaml(slug, "en", _sha256(core)), StandardCharsets.UTF_8)
        }
        val missing = _scaffolded_project(root, "plan-missing")
        _admit_core_(missing)
        Files.delete(missing.resolve("content/presentation-semantics-en.yaml"))
        val incomplete = _scaffolded_project(root, "plan-incomplete")
        _admit_core_(incomplete)
        val invalid = _scaffolded_project(root, "plan-invalid")
        _admit_core_(invalid)
        Files.writeString(invalid.resolve("content/presentation-semantics-en.yaml"), "schema: [\n", StandardCharsets.UTF_8)
        val stale = _scaffolded_project(root, "plan-stale")
        val stalecore = stale.resolve("content/core-en.yaml")
        val prioridentity = _sha256(stalecore)
        Files.writeString(stalecore, Files.readString(stalecore, StandardCharsets.UTF_8).replace("accepted: []", "accepted:\n  - id: accepted-core\n    text: Accepted core"), StandardCharsets.UTF_8)
        Files.writeString(stale.resolve("content/presentation-semantics-en.yaml"), CozyDocumentProject._presentation_semantics_yaml("plan-stale", "en", prioridentity), StandardCharsets.UTF_8)

        When("plan derives each project without executing an operation")
        val plans = Vector(
          "missing" -> _execute(List("document-project", "plan", missing.toString)),
          "authoring-incomplete" -> _execute(List("document-project", "plan", incomplete.toString)),
          "invalid" -> _execute(List("document-project", "plan", invalid.toString)),
          "stale" -> _execute(List("document-project", "plan", stale.toString))
        )

        Then("every plan places Presentation Semantics after Content Core and before its dependent semantic Work Products")
        plans.foreach { case (state, plan) =>
          plan should include("required: work-product content-core [authority, required")
          plan should include("required: work-product presentation-semantics [authority, required")
          plan should include("required: work-product article-source [authority, required")
          plan.indexOf("work-product content-core [") should be < plan.indexOf("work-product presentation-semantics [")
          plan.indexOf("work-product presentation-semantics [") should be < plan.indexOf("work-product article-source [")
          plan should include(s"state: $state")
          plan should include(s"readiness: ${if (state == "invalid") "failed" else "blocked"}")
          plan should include("reason:")
          plan should include("next-action: presentation-semantics [Author the project-local sibling content/presentation-semantics-en.yaml to the strict v2 contract (cozy.content-core.presentation-semantics.v2); after authoring, use document-project verify <project> only for validation.]")
          plan should not include "run <project> --operation presentation.author"
        }

        And("planning creates no target, state, evidence, confirmation, receipt, or semantic output artifact")
        Vector(missing, incomplete, invalid, stale).foreach { project =>
          Files.exists(project.resolve("target"), LinkOption.NOFOLLOW_LINKS) shouldBe false
          Files.exists(project.resolve("state"), LinkOption.NOFOLLOW_LINKS) shouldBe false
          Files.exists(project.resolve("evidence"), LinkOption.NOFOLLOW_LINKS) shouldBe false
          Files.exists(project.resolve("presentation-confirmation.html"), LinkOption.NOFOLLOW_LINKS) shouldBe false
          Files.exists(project.resolve("presentation-confirmation.receipt.yaml"), LinkOption.NOFOLLOW_LINKS) shouldBe false
        }
      }
    }

    "derive Presentation Semantics Plan state without unrelated retained evidence" in {
      _with_temp_dir("cozy-document-project-presentation-semantics-plan-evidence") { root =>
        Given("a project with complete current fixed-catalog Presentation Semantics and a malformed retained evidence sidecar")
        val project = _scaffolded_project(root, "plan-semantics-evidence")
        _write_valid_presentation_semantics(project)
        val sidecar = project.resolve("evidence/document-project.yaml")
        Files.createDirectories(sidecar.getParent)
        Files.writeString(sidecar, "schema: [\n", StandardCharsets.UTF_8)

        When("Plan derives only the Presentation Semantics state")
        val plan = _execute(List("document-project", "plan", project.toString))
        val inspectfailure = _failure(List("document-project", "inspect", project.toString))

        Then("Plan remains available with current semantic coverage while inspect preserves its strict retained-evidence failure")
        plan should include("required: work-product presentation-semantics [authority, required]; state: current; coverage: satisfied; currentness: current; readiness: ready")
        plan should include("reason: strict semantics, projection, and semantic coverage are current")
        plan should not include("next-action: presentation-semantics")
        inspectfailure should include("DP-DESC-002")
      }
    }

    "use the exact Presentation Semantics authoring instruction for the first Dashboard blocker" in {
      _with_temp_dir("cozy-document-project-presentation-semantics-dashboard") { root =>
        Given("a project with a strictly admissible Content Core and an authoring-incomplete semantic sibling")
        val project = _scaffolded_project(root, "dashboard-semantics")
        val core = project.resolve("content/core-en.yaml")
        val semantics = project.resolve("content/presentation-semantics-en.yaml")
        Files.writeString(core, Files.readString(core, StandardCharsets.UTF_8).replace("accepted: []", "accepted:\n  - id: accepted-core\n    text: Accepted core"), StandardCharsets.UTF_8)
        Files.writeString(semantics, CozyDocumentProject._presentation_semantics_yaml("dashboard-semantics", "en", _sha256(core)), StandardCharsets.UTF_8)
        val corebytes = Files.readAllBytes(core)
        val semanticsbytes = Files.readAllBytes(semantics)
        val descriptorbytes = Files.readAllBytes(project.resolve("document-project.yaml"))

        When("Dashboard projects the read-only snapshot")
        _execute(List("document-project", "dashboard", project.toString))
        val dashboard = Files.readString(project.resolve("target/document-project/project-dashboard.html"), StandardCharsets.UTF_8)

        Then("Presentation Semantics is the first blocker and its exact authoring instruction is shown")
        dashboard should include("presentation-semantics</th><td>missing</td><td>missing</td><td>pending</td><td>blocked")
        dashboard.indexOf("<strong>Presentation semantics</strong>") should be >= 0
        dashboard should include("Author the project-local sibling content/presentation-semantics-en.yaml to the strict v2 contract (cozy.content-core.presentation-semantics.v2); after authoring, use document-project verify &lt;project&gt; only for validation.")
        dashboard should not include("run &lt;project&gt; --operation presentation.author")
        dashboard should not include("presentation-confirmation.html")
        dashboard should not include("presentation-confirmation.receipt.yaml")

        And("Dashboard does not alter semantic source, descriptor bytes, or emit semantic receipts")
        Files.readAllBytes(core) shouldBe corebytes
        Files.readAllBytes(semantics) shouldBe semanticsbytes
        Files.readAllBytes(project.resolve("document-project.yaml")) shouldBe descriptorbytes
        Files.exists(project.resolve("target/document-project/presentation-confirmation.html"), LinkOption.NOFOLLOW_LINKS) shouldBe false
        Files.exists(project.resolve("target/document-project/presentation-confirmation.receipt.yaml"), LinkOption.NOFOLLOW_LINKS) shouldBe false
      }
    }

    "project current Presentation Semantics as a non-running Dashboard no-action" in {
      _with_temp_dir("cozy-document-project-presentation-semantics-dashboard-current") { root =>
        Given("a project with complete current fixed-catalog Presentation Semantics")
        val project = _scaffolded_project(root, "dashboard-semantics-current")
        _write_valid_presentation_semantics(project)

        When("Dashboard projects its read-only action surface")
        _execute(List("document-project", "dashboard", project.toString))
        val dashboard = Files.readString(project.resolve("target/document-project/project-dashboard.html"), StandardCharsets.UTF_8)

        Then("Presentation Semantics reports its explicit current no-action without a presentation author run command")
        dashboard should include("No action: Presentation Semantics is current and ready; Dashboard does not run authoring.")
        dashboard should not include("cozy document-project run &lt;project&gt; --operation presentation.author --dry-run")
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
        Files.writeString(external, "schema: cozy.document-project.v2\n", StandardCharsets.UTF_8)

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
        _activate_optional_work_products(project, "standard", Vector("core-review-html", "slide-review-html"))
        Files.writeString(core, Files.readString(core, StandardCharsets.UTF_8).replace("accepted: []", "accepted:\n  - id: html-sensitive\n    text: \"<script>& text\""), StandardCharsets.UTF_8)
        val descriptorbytes = Files.readAllBytes(project.resolve("document-project.yaml"))
        val corebytes = Files.readAllBytes(core)
        val sourcebytes = Files.readAllBytes(project.resolve("index.dox"))

        When("dashboard is requested before any default review output exists")
        _execute(List("document-project", "dashboard", project.toString))
        val dashboard = project.resolve("target/document-project/project-dashboard.html")

        Then("review-projection Work Products remain blocked and identify stale Presentation Semantics dependency propagation")
        val initialdashboardtext = Files.readString(dashboard, StandardCharsets.UTF_8)
        initialdashboardtext should include("core-review-html</th><td>missing</td><td>missing</td><td>pending</td><td>blocked")
        initialdashboardtext should include("Generate Core review HTML")
        initialdashboardtext should include("presentation-semantics</th><td>missing</td><td>stale</td><td>pending</td><td>blocked")
        initialdashboardtext should include("slide-review-html</th><td>missing</td><td>stale</td><td>pending</td><td>blocked</td><td>a declared dependency is stale")
        initialdashboardtext should include("Generate Slide review HTML")

        When("the default core and slide reviews are generated before the dashboard is repeated")
        _execute(List("document-project", "review", project.toString, "--kind", "core"))
        _execute(List("document-project", "review", project.toString, "--kind", "slides"))
        val firstoutput = _execute(List("document-project", "dashboard", project.toString))
        val firstbytes = Files.readAllBytes(dashboard)
        _execute(List("document-project", "dashboard", project.toString))
        val secondbytes = Files.readAllBytes(dashboard)

        Then("the default dashboard is self-contained, escaped, structurally accessible, byte-identical, and explicit about stale Presentation Semantics dependencies")
        firstoutput should startWith("Cozy Document Project Dashboard")
        Files.isRegularFile(dashboard, LinkOption.NOFOLLOW_LINKS) shouldBe true
        firstbytes shouldBe secondbytes
        val dashboardtext = Files.readString(dashboard, StandardCharsets.UTF_8)
        dashboardtext should include("<h2>Workflow</h2>")
        dashboardtext should include("Selection")
        dashboardtext should include(">required<")
        dashboardtext should include(">active-optional<")
        dashboardtext should include(">profile-disabled<")
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
        dashboardtext should include("presentation-semantics</th><td>missing</td><td>stale</td><td>pending</td><td>blocked")
        dashboardtext should include("slide-review-html</th><td>missing</td><td>stale</td><td>pending</td><td>blocked</td><td>a declared dependency is stale")
        dashboardtext should include("<a href=\"core-review.html\">core-review.html</a>")
        dashboardtext should include("<a href=\"slides-review.html\">slides-review.html</a>")
        dashboardtext should include("<a href=\"../../infographic/infographic.svg\">../../infographic/infographic.svg</a>")
        And("disabled video products are not applicable in the dashboard matrix")
        Vector("video-storyboard", "video-review", "video-deliverable").foreach { id =>
          dashboardtext should include(s"$id</th><td>not-applicable</td><td>not-applicable</td><td>not-applicable</td><td>omitted")
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

    "generate core and active video review projections with local video receipt evidence" in {
      _with_temp_dir("cozy-document-project-review") { root =>
        Given("admitted standard and standard-video projects")
        val standardparent = Files.createDirectory(root.resolve("standard-parent"))
        val videoparent = Files.createDirectory(root.resolve("video-parent"))
        _execute(List("document-project", "scaffold", "core-doc", "--profile", "standard", "--language", "en", "--workspace", "directory", "--save", standardparent.toString))
        _execute(List("document-project", "scaffold", "video-doc", "--profile", "standard-video", "--language", "en", "--workspace", "directory", "--save", videoparent.toString))
        val standard = standardparent.resolve("core-doc.dox")
        val video = videoparent.resolve("video-doc.dox")
        Files.writeString(video.resolve("video/storyboard.md"), _valid_storyboard_with_infographic, StandardCharsets.UTF_8)
        _activate_optional_work_products(standard, "standard", Vector("core-review-html"))
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
        val videostatebeforeinspect = Files.exists(video.resolve("target/document-project/state.yaml"), LinkOption.NOFOLLOW_LINKS)
        _execute(List("document-project", "inspect", video.toString))

        Then("reviews are deterministic semantic projections with local output evidence and explicit non-authority boundaries")
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
        videoreviewtext should include("Ordered scene intent")
        videoreviewtext should include("<table aria-label=\"Ordered semantic video scenes\">")
        videoreviewtext should include("Welcome to Cozy.")
        videoreviewtext should include("Speaker and pronunciation")
        videoreviewtext should include("Renderer input")
        videoreviewtext should include("unavailable; no renderer input is admitted or executed")
        videoreviewtext should include("Storyboard infographic use")
        videoreviewtext should include("Exact current infographic source use is declared through storyboard references for infographic/infographic.svg: scene 1: opening through asset-refs.")
        videoreviewtext should not include("Storyboard source projection")
        videoreviewtext should not include("Visual-page source projection")
        videoreviewtext should not include("<pre><code>")
        videoreviewtext should include("No provider")
        videoreviewtext should not include "video.render-review"
        val videoreceipt = video.resolve("target/document-project/video-review.receipt.yaml")
        Files.isRegularFile(videoreceipt, LinkOption.NOFOLLOW_LINKS) shouldBe true
        Files.readString(videoreceipt, StandardCharsets.UTF_8) should include("kind: video")
        Files.readString(videoreceipt, StandardCharsets.UTF_8) should include("path: target/document-project/video-review.html")
        Files.readString(videoreceipt, StandardCharsets.UTF_8) should include("path: video/storyboard.md")
        Files.exists(standard.resolve("target/document-project/state.yaml"), LinkOption.NOFOLLOW_LINKS) shouldBe false
        videostatebeforeinspect shouldBe false
        Files.readString(video.resolve("target/document-project/state.yaml"), StandardCharsets.UTF_8) should include("id: video-review\n    role: review-projection\n    disposition: required\n    selection: required\n    criterion: video-review-rendered\n    coverage: satisfied\n    currentness: current\n    review: pending\n    readiness: ready")
        Files.exists(standard.resolve("evidence"), LinkOption.NOFOLLOW_LINKS) shouldBe false
        Files.exists(video.resolve("evidence"), LinkOption.NOFOLLOW_LINKS) shouldBe false
        Files.readAllBytes(standard.resolve("content/core-en.yaml")) shouldBe standardcorebytes
        Files.readAllBytes(video.resolve("video/storyboard.md")) shouldBe storyboardbytes
        Files.readAllBytes(video.resolve("presentation/visual-pages.yaml")) shouldBe visualpagebytes

        Given("an admitted standard project with no accepted Core entries")
        val emptycoreparent = Files.createDirectory(root.resolve("empty-core-parent"))
        _execute(List("document-project", "scaffold", "empty-core-doc", "--profile", "standard", "--language", "en", "--workspace", "directory", "--save", emptycoreparent.toString))
        val emptycoreproject = emptycoreparent.resolve("empty-core-doc.dox")
        _activate_optional_work_products(emptycoreproject, "standard", Vector("core-review-html"))

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

    "project a deterministic selected Article review with an escaped page and its default receipt" in {
      _with_temp_dir("cozy-document-project-article-review") { root =>
        Given("a selected article review project with structured article, Core, Visual Page, and infographic inputs")
        val parent = Files.createDirectory(root.resolve("parent"))
        _execute(List("document-project", "scaffold", "article", "--profile", "standard", "--language", "en", "--workspace", "directory", "--save", parent.toString))
        val article = parent.resolve("article.dox")
        _activate_optional_work_products(article, "standard", Vector("article-review-html"))
        val articlepath = article.resolve("index.dox")
        val corepath = article.resolve("content/core-en.yaml")
        val visualpath = article.resolve("presentation/visual-pages.yaml")
        val infographicpath = article.resolve("infographic/infographic.svg")
        Files.writeString(articlepath, "Article review&semantics\n========================\n\n# Purpose\n\nNarrative & context\n\n# Terminology\n\nCozy means a deterministic review boundary.\n\n# Media placement\n\nPlace the infographic after the Flow section.\n\n# Flow\n\nExplain the visual relationship.\n", StandardCharsets.UTF_8)
        Files.writeString(corepath, _core_yaml("article", "en", "Accepted correspondence"), StandardCharsets.UTF_8)
        Files.writeString(visualpath, "pages:\n  - title: \"Visual <script> & intent\"\n    intent: \"Explain & compare\"\n    visible: true\n    options:\n      selectable: false\n    media: [\"infographic/infographic.svg\", \"assets/overview.png\"]\n  - title: \"Closing\"\n    emphasis: \"Next step\"\n    visible: false\n", StandardCharsets.UTF_8)
        val articlebytes = Files.readAllBytes(articlepath)
        val corebytes = Files.readAllBytes(corepath)
        val visualbytes = Files.readAllBytes(visualpath)
        val infographicbytes = Files.readAllBytes(infographicpath)

        When("article review is requested twice at its default and a safe explicit destination")
        _execute(List("document-project", "review", article.toString, "--kind", "article"))
        val defaultreview = article.resolve("target/document-project/article-review.html")
        val receipt = article.resolve("target/document-project/article-review.receipt.yaml")
        val firstbytes = Files.readAllBytes(defaultreview)
        val firstreceiptbytes = Files.readAllBytes(receipt)
        _execute(List("document-project", "review", article.toString, "--kind", "article"))
        val explicit = root.resolve("saved/article-review.html")
        _execute(List("document-project", "review", article.toString, "--kind", "article", "--save", explicit.toString))

        Then("the selected review is deterministic, escaped, page-oriented, and receipt-bound without changing authored sources")
        val review = Files.readString(defaultreview, StandardCharsets.UTF_8)
        Files.readAllBytes(defaultreview) shouldBe firstbytes
        Files.readAllBytes(receipt) shouldBe firstreceiptbytes
        Files.readAllBytes(explicit) shouldBe firstbytes
        Files.readAllBytes(receipt) shouldBe firstreceiptbytes
        Files.readString(receipt, StandardCharsets.UTF_8) should include("kind: article")
        Files.readString(receipt, StandardCharsets.UTF_8) should include("path: target/document-project/article-review.html")
        Files.readString(receipt, StandardCharsets.UTF_8) should include("path: presentation/visual-pages.yaml")
        review should include("<h1>Article Review</h1>")
        review should include("Article structure and narrative")
        review should include("Article review&amp;semantics")
        review should include("Narrative &amp; context")
        review should include("Accepted Content Core correspondence")
        review should include("<h2>Page review</h2>")
        review should include("data-page-count=\"2\"")
        review should include("data-current-page=\"1\"")
        review should include("data-page-id=\"page-1\"")
        review should include("data-page-id=\"page-2\"")
        review should include("Previous page")
        review should include("Next page")
        review should include("ArrowLeft")
        review should include("ArrowRight")
        review should include("Article structure: Purpose, Terminology, Media placement, Flow")
        review should include("Visual &lt;script&gt; &amp; intent")
        review should include("Explain &amp; compare")
        review should include("infographic/infographic.svg")
        review should include("Terminology and media placement")
        review should include("Cozy means a deterministic review boundary.")
        review should include("Place the infographic after the Flow section.")
        review should include("Phase-41 Projection selector: unavailable; the v2 descriptor declares no accepted Phase-41 selector")
        review should include("Current verified input identities")
        review should include("&lt;script&gt;")
        review should include("No provider execution, renderer input")
        review should not include("Native article.render-review execution")
        review should not include("Visual <script> & intent")
        review should not include("<pre><code>")
        review should not include("pages:")
        Files.exists(article.resolve("target/document-project/state.yaml"), LinkOption.NOFOLLOW_LINKS) shouldBe false
        Files.exists(article.resolve("evidence"), LinkOption.NOFOLLOW_LINKS) shouldBe false
        Files.readAllBytes(articlepath) shouldBe articlebytes
        Files.readAllBytes(corepath) shouldBe corebytes
        Files.readAllBytes(visualpath) shouldBe visualbytes
        Files.readAllBytes(infographicpath) shouldBe infographicbytes
      }
    }

    "project Article review with absent optional declarations" in {
      _with_temp_dir("cozy-document-project-article-review-absent-declarations") { root =>
        Given("a selected article review project without explicit terminology or media placement declarations")
        val parent = Files.createDirectory(root.resolve("parent"))
        _execute(List("document-project", "scaffold", "unavailable", "--profile", "standard", "--language", "en", "--workspace", "directory", "--save", parent.toString))
        val project = parent.resolve("unavailable.dox")
        _activate_optional_work_products(project, "standard", Vector("article-review-html"))

        When("article review projects the admitted article without those declarations")
        _execute(List("document-project", "review", project.toString, "--kind", "article"))

        Then("the projection truthfully marks terminology and media placement as not declared")
        val review = Files.readString(project.resolve("target/document-project/article-review.html"), StandardCharsets.UTF_8)
        review should include("No Terminology declaration is present in the admitted article source.")
        review should include("No Media placement declaration is present in the admitted article source.")
      }
    }

    "reject inactive Article review selection" in {
      _with_temp_dir("cozy-document-project-article-review-inactive") { root =>
        Given("an otherwise admitted project that leaves article-review-html inactive")
        val parent = Files.createDirectory(root.resolve("parent"))
        _execute(List("document-project", "scaffold", "inactive", "--profile", "standard", "--language", "en", "--workspace", "directory", "--save", parent.toString))
        val project = parent.resolve("inactive.dox")

        When("article review is requested without the required Work Product selection")
        val failure = _failure(List("document-project", "review", project.toString, "--kind", "article"))

        Then("the established selection diagnostic rejects the article kind")
        _diagnostic_tokens(failure) shouldBe Vector("DP-OP-001")
        failure should include("logical operation article.render-review is not selected for profile standard")
      }
    }

    "reject invalid typed Video Storyboard input" in {
      _with_temp_dir("cozy-document-project-invalid-video-storyboard") { root =>
        Given("an otherwise admitted video profile with the scaffold's invalid typed Storyboard placeholder")
        val parent = Files.createDirectory(root.resolve("parent"))
        _execute(List("document-project", "scaffold", "invalid-video", "--profile", "standard-video", "--language", "en", "--workspace", "directory", "--save", parent.toString))
        val project = parent.resolve("invalid-video.dox")

        When("semantic video review attempts to consume the typed Storyboard")
        val failure = _failure(List("document-project", "review", project.toString, "--kind", "video"))

        Then("the structured diagnostic rejects it before a projection output is published")
        _diagnostic_tokens(failure) shouldBe Vector("DP-DESC-002")
        failure should include("video storyboard is invalid")
        Files.exists(project.resolve("target/document-project/video-review.html"), LinkOption.NOFOLLOW_LINKS) shouldBe false
      }
    }

    "preserve parent narrative around nested sections in article review" in {
      _with_temp_dir("cozy-document-project-nested-article-sections") { root =>
        Given("a selected article review project whose parent section has narrative before and after a child section")
        val project = _scaffolded_project(root, "nested-article")
        _activate_optional_work_products(project, "standard", Vector("article-review-html"))
        val articlepath = project.resolve("index.dox")
        Files.writeString(
          articlepath,
          "Nested article\n==============\n\n# Parent\n\nParent narrative before child.\n\n## Child\n\nChild narrative.\n\nParent narrative after child.\n",
          StandardCharsets.UTF_8
        )

        When("article review projects the nested article structure")
        _execute(List("document-project", "review", project.toString, "--kind", "article"))
        val review = Files.readString(project.resolve("target/document-project/article-review.html"), StandardCharsets.UTF_8)

        Then("the projection includes every direct parent and child narrative")
        review should include("Parent narrative before child.")
        review should include("Child narrative.")
        review should include("Parent narrative after child.")
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
        _activate_optional_work_products(standard, "standard", Vector("explanation-structure-review-html"))
        _activate_optional_work_products(video, "standard-video", Vector("explanation-structure-review-html", "video-logical-chart-html"))
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
        Files.exists(standard.resolve("target/document-project/slide-logical-chart.receipt.yaml"), LinkOption.NOFOLLOW_LINKS) shouldBe false
        Files.exists(standard.resolve("evidence"), LinkOption.NOFOLLOW_LINKS) shouldBe false
        Files.readAllBytes(standardcore) shouldBe standardcorebytes
        Files.readAllBytes(standardvisualpages) shouldBe standardvisualbytes
        Files.readAllBytes(standardarticle) shouldBe standardarticlebytes

        When("the standard dashboard reconstructs the generated Slide Logical Chart")
        _execute(List("document-project", "dashboard", standard.toString))

        Then("the generated Slide Logical Chart remains stale because Presentation Semantics has already made Visual Page IR stale")
        val standardcurrentchart = Files.readString(standard.resolve("target/document-project/project-dashboard.html"), StandardCharsets.UTF_8)
        standardcurrentchart should include("presentation-semantics</th><td>missing</td><td>stale</td><td>pending</td><td>blocked")
        standardcurrentchart should include("visual-pages</th><td>missing</td><td>stale</td><td>pending</td><td>blocked</td><td>a declared dependency is stale")
        standardcurrentchart should include("explanation-structure-review-html</th><td>missing</td><td>stale</td><td>pending</td><td>blocked</td><td>a declared dependency is stale")

        When("the current standard Visual Page IR changes without regenerating the Slide Logical Chart")
        Files.writeString(standardvisualpages, "pages: [\"changed slide visual\"]\n", StandardCharsets.UTF_8)
        _execute(List("document-project", "dashboard", standard.toString))

        Then("the retained Slide Logical Chart is stale with the deterministic mismatch reason")
        val standardstalechart = Files.readString(standard.resolve("target/document-project/project-dashboard.html"), StandardCharsets.UTF_8)
        standardstalechart should include("explanation-structure-review-html</th><td>missing</td><td>stale</td><td>pending</td><td>blocked</td><td>generated logical chart output does not match current project IR")

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
        Files.exists(video.resolve("target/document-project/video-logical-chart.receipt.yaml"), LinkOption.NOFOLLOW_LINKS) shouldBe false
        Files.exists(video.resolve("evidence"), LinkOption.NOFOLLOW_LINKS) shouldBe false
        Files.readAllBytes(videocore) shouldBe videocorebytes
        Files.readAllBytes(videovisualpages) shouldBe videovisualbytes
        Files.readAllBytes(videostoryboard) shouldBe videostoryboardbytes

        When("the standard-video dashboard is projected")
        _execute(List("document-project", "dashboard", video.toString))

        Then("the Slide Logical Chart remains missing while the Video Logical Chart is stale and blocked by a declared dependency")
        val dashboardtext = Files.readString(video.resolve("target/document-project/project-dashboard.html"), StandardCharsets.UTF_8)
        dashboardtext should include("explanation-structure-review-html<br/><span>Slide Logical Chart HTML</span></th><td>active-optional</td><td>review-projection</td><td>optional</td>")
        dashboardtext should include("explanation-structure-review-html</th><td>missing</td><td>missing</td><td>pending</td><td>blocked</td><td>default review HTML is not generated")
        dashboardtext should include("video-logical-chart-html<br/><span>Video Logical Chart HTML</span></th><td>active-optional</td><td>review-projection</td><td>optional</td>")
        dashboardtext should include("video-logical-chart-html</th><td>missing</td><td>stale</td><td>pending</td><td>blocked</td><td>a declared dependency is stale")
        dashboardtext should include("phase-41-explanation-structure")
        dashboardtext should include("content-core")
        dashboardtext should include("slide-logical-chart")
        dashboardtext should include("slide-logical-chart-reference")

        When("the current storyboard IR changes without regenerating the Video Logical Chart")
        Files.writeString(videostoryboard, "# Storyboard\n\nchanged storyboard\n", StandardCharsets.UTF_8)
        _execute(List("document-project", "dashboard", video.toString))

        Then("the retained Video Logical Chart is stale with the deterministic mismatch reason")
        val videostalechart = Files.readString(video.resolve("target/document-project/project-dashboard.html"), StandardCharsets.UTF_8)
        videostalechart should include("video-logical-chart-html</th><td>missing</td><td>stale</td><td>pending</td><td>blocked</td><td>generated logical chart output does not match current project IR")
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
        _activate_optional_work_products(project, "standard-video", Vector("core-review-html", "explanation-structure-review-html"))
        Files.writeString(project.resolve("video/storyboard.md"), _valid_storyboard, StandardCharsets.UTF_8)
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
      val missingcontentcore = _failure(List("document-project", "content-core"))
      val missingcandidateinput = _failure(List("document-project", "content-core", "candidate", "sample.dox"))
      val extrafeedback = _failure(List("document-project", "content-core", "feedback", "sample.dox", "candidate", "feedback.json", "extra"))

      Then("grammar failures precede only with DP-CLI-001 and missing forms use DP-CLI-002")
      spelling should include("DP-CLI-001")
      unknown should include("DP-CLI-001")
      missingproject should include("DP-CLI-002")
      missingoperation should include("DP-CLI-002")
      missingreviewkind should include("DP-CLI-002")
      invalidreviewkind should include("DP-CLI-001")
      reviewoperation should include("DP-CLI-001")
      missingcontentcore should include("DP-CLI-002")
      missingcandidateinput should include("DP-CLI-002")
      extrafeedback should include("DP-CLI-001")
    }

    "materialize a succeeded Content Core candidate with provenance without changing the accepted Core" in {
      _with_temp_dir("cozy-document-project-content-core-candidate") { root =>
        Given("a scaffolded Document Project and one completed dialogue bundle")
        val project = _scaffolded_project(root, "content-core-candidate")
        val core = project.resolve("content/core-en.yaml")
        val corebefore = Files.readAllBytes(core)
        val dialogue = root.resolve("candidate.json")
        Files.writeString(dialogue, _succeeded_dialogue_json("candidate-one", "candidate Core replacement"), StandardCharsets.UTF_8)

        When("the explicit candidate command records the completed dialogue")
        val output = _execute(List("document-project", "content-core", "candidate", project.toString, dialogue.toString))

        Then("the append-only candidate retains the raw dialogue provenance and leaves Content Core unchanged")
        val evidence = project.resolve("evidence/content-core/candidates/candidate-one.yaml")
        output should include("operation: content-core.compose")
        output should include("outcome: succeeded")
        output should include("candidate: candidate-one")
        Files.readAllBytes(core) shouldBe corebefore
        Files.readString(evidence, StandardCharsets.UTF_8) should include("schema: cozy.content-core-candidate.v1")
        Files.readString(evidence, StandardCharsets.UTF_8) should include("text: \"source document\"")
        Files.readString(evidence, StandardCharsets.UTF_8) should include("text: \"completed provider response\"")
        Files.readString(evidence, StandardCharsets.UTF_8) should include("id: \"provider-local\"")
        Files.readString(evidence, StandardCharsets.UTF_8) should include("supersedes: none")
      }
    }

    "record a failed Content Core dialogue as history without creating a candidate or changing Content Core" in {
      _with_temp_dir("cozy-document-project-content-core-failed") { root =>
        Given("a scaffolded Document Project and a completed failed dialogue bundle")
        val project = _scaffolded_project(root, "content-core-failed")
        val core = project.resolve("content/core-en.yaml")
        val corebefore = Files.readAllBytes(core)
        val dialogue = root.resolve("failed.yaml")
        Files.writeString(dialogue, _failed_dialogue_yaml("failed-one"), StandardCharsets.UTF_8)

        When("the explicit candidate command records the failure history")
        val output = _execute(List("document-project", "content-core", "candidate", project.toString, dialogue.toString))

        Then("only the failed provenance history is materialized")
        val attempt = project.resolve("evidence/content-core/attempts/failed-one.yaml")
        output should include("outcome: failed")
        Files.readAllBytes(core) shouldBe corebefore
        Files.exists(attempt, LinkOption.NOFOLLOW_LINKS) shouldBe true
        Files.readString(attempt, StandardCharsets.UTF_8) should include("schema: cozy.content-core-attempt.v1")
        Files.readString(attempt, StandardCharsets.UTF_8) should include("text: \"provider declined the draft\"")
        Files.exists(project.resolve("evidence/content-core/candidates"), LinkOption.NOFOLLOW_LINKS) shouldBe false
      }
    }

    "require feedback-linked revision and explicit human acceptance before replacing Content Core" in {
      _with_temp_dir("cozy-document-project-content-core-acceptance") { root =>
        Given("a scaffolded Document Project, its first candidate, and changes-requested human feedback")
        val project = _scaffolded_project(root, "content-core-acceptance")
        val core = project.resolve("content/core-en.yaml")
        val firstdialogue = root.resolve("first.json")
        val feedback = root.resolve("feedback.json")
        val reviseddialogue = root.resolve("revised.json")
        val acceptance = root.resolve("acceptance.json")
        Files.writeString(firstdialogue, _succeeded_dialogue_json("candidate-first", "first candidate replacement"), StandardCharsets.UTF_8)
        Files.writeString(feedback, _feedback_json("feedback-first", "candidate-first", "changes-requested"), StandardCharsets.UTF_8)
        Files.writeString(reviseddialogue, _succeeded_dialogue_json("candidate-revised", "revised candidate replacement", Some("candidate-first")), StandardCharsets.UTF_8)
        Files.writeString(acceptance, _acceptance_json("acceptance-revised", "candidate-revised"), StandardCharsets.UTF_8)
        _execute(List("document-project", "content-core", "candidate", project.toString, firstdialogue.toString))

        When("feedback is recorded, a revision supersedes the pending candidate, and a human accepts that revision")
        val feedbackoutput = _execute(List("document-project", "content-core", "feedback", project.toString, "candidate-first", feedback.toString))
        val revisedoutput = _execute(List("document-project", "content-core", "candidate", project.toString, reviseddialogue.toString))
        val acceptanceoutput = _execute(List("document-project", "content-core", "accept", project.toString, "candidate-revised", acceptance.toString))

        Then("the immutable links lead to exactly one accepted Core replacement")
        val revisedevidence = Files.readString(project.resolve("evidence/content-core/candidates/candidate-revised.yaml"), StandardCharsets.UTF_8)
        val acceptanceevidence = Files.readString(project.resolve("evidence/content-core/acceptances/acceptance-revised.yaml"), StandardCharsets.UTF_8)
        feedbackoutput should include("decision: changes-requested")
        revisedoutput should include("candidate: candidate-revised")
        acceptanceoutput should include("decision: accepted")
        Files.readString(core, StandardCharsets.UTF_8) should include("text: \"revised candidate replacement\"")
        Files.readString(core, StandardCharsets.UTF_8) should not include "accepted: []"
        revisedevidence should include("supersedes:")
        revisedevidence should include("id: \"candidate-first\"")
        revisedevidence should include("feedback:")
        revisedevidence should include("id: \"feedback-first\"")
        acceptanceevidence should include("priorCore:")
        acceptanceevidence should include("resultingCore:")
      }
    }

    "resume one durable pending acceptance without duplicating evidence and keep the completed retry idempotent" in {
      _with_temp_dir("cozy-document-project-content-core-pending-acceptance") { root =>
        Given("a candidate and a pre-existing direct acceptance record whose prior Core is still current")
        val project = _scaffolded_project(root, "content-core-pending-acceptance")
        val core = project.resolve("content/core-en.yaml")
        val dialogue = root.resolve("candidate.json")
        val acceptance = root.resolve("acceptance.json")
        val feedback = root.resolve("feedback.json")
        val alternate = root.resolve("alternate-acceptance.json")
        Files.writeString(dialogue, _succeeded_dialogue_json("candidate-pending-acceptance", "pending acceptance replacement"), StandardCharsets.UTF_8)
        Files.writeString(acceptance, _acceptance_json("acceptance-pending", "candidate-pending-acceptance"), StandardCharsets.UTF_8)
        Files.writeString(feedback, _feedback_json("feedback-pending", "candidate-pending-acceptance", "changes-requested"), StandardCharsets.UTF_8)
        Files.writeString(alternate, _acceptance_json("acceptance-alternate", "candidate-pending-acceptance"), StandardCharsets.UTF_8)
        _execute(List("document-project", "content-core", "candidate", project.toString, dialogue.toString))
        val prior = _sha256(core)
        val replacement = _accepted_candidate_core_yaml("content-core-pending-acceptance", "en", "accepted-candidate-pending-acceptance", "pending acceptance replacement")
        val acceptanceevidence = project.resolve("evidence/content-core/acceptances/acceptance-pending.yaml")
        Files.createDirectories(acceptanceevidence.getParent)
        Files.writeString(
          acceptanceevidence,
          _acceptance_record_yaml(project, "acceptance-pending", "candidate-pending-acceptance", "human-reviewer", "content/core-en.yaml", prior, _sha256_string(replacement)),
          StandardCharsets.UTF_8
        )
        val evidencebefore = Files.readAllBytes(acceptanceevidence)

        When("feedback and another acceptance are attempted before the exact pending acceptance is resumed")
        val feedbackfailure = _failure(List("document-project", "content-core", "feedback", project.toString, "candidate-pending-acceptance", feedback.toString))
        val alternatefailure = _failure(List("document-project", "content-core", "accept", project.toString, "candidate-pending-acceptance", alternate.toString))
        val resumed = _execute(List("document-project", "content-core", "accept", project.toString, "candidate-pending-acceptance", acceptance.toString))
        val repeated = _execute(List("document-project", "content-core", "accept", project.toString, "candidate-pending-acceptance", acceptance.toString))

        Then("only the durable acceptance record authorizes the replacement and both completion calls report it")
        _diagnostic_tokens(feedbackfailure) shouldBe Vector("DP-OP-001")
        _diagnostic_tokens(alternatefailure) shouldBe Vector("DP-OP-001")
        resumed should include("decision: accepted")
        repeated should include("decision: accepted")
        Files.readString(core, StandardCharsets.UTF_8) should include("text: \"pending acceptance replacement\"")
        Files.readAllBytes(acceptanceevidence) shouldBe evidencebefore
        _relative_files(project.resolve("evidence/content-core/acceptances")) shouldBe Set("acceptance-pending.yaml")
        Files.exists(project.resolve("evidence/content-core/feedback/feedback-pending.yaml"), LinkOption.NOFOLLOW_LINKS) shouldBe false
        Files.exists(project.resolve("evidence/content-core/acceptances/acceptance-alternate.yaml"), LinkOption.NOFOLLOW_LINKS) shouldBe false
      }
    }

    "treat a pending acceptance as historical when the current Core no longer has either recorded identity" in {
      _with_temp_dir("cozy-document-project-content-core-historical-acceptance") { root =>
        Given("a candidate and a pre-existing pending acceptance with a separately changed current Core")
        val project = _scaffolded_project(root, "content-core-historical-acceptance")
        val core = project.resolve("content/core-en.yaml")
        val dialogue = root.resolve("candidate.json")
        val acceptance = root.resolve("acceptance.json")
        Files.writeString(dialogue, _succeeded_dialogue_json("candidate-historical", "historical replacement"), StandardCharsets.UTF_8)
        Files.writeString(acceptance, _acceptance_json("acceptance-historical", "candidate-historical"), StandardCharsets.UTF_8)
        _execute(List("document-project", "content-core", "candidate", project.toString, dialogue.toString))
        val prior = _sha256(core)
        val replacement = _accepted_candidate_core_yaml("content-core-historical-acceptance", "en", "accepted-candidate-historical", "historical replacement")
        val acceptanceevidence = project.resolve("evidence/content-core/acceptances/acceptance-historical.yaml")
        Files.createDirectories(acceptanceevidence.getParent)
        Files.writeString(
          acceptanceevidence,
          _acceptance_record_yaml(project, "acceptance-historical", "candidate-historical", "human-reviewer", "content/core-en.yaml", prior, _sha256_string(replacement)),
          StandardCharsets.UTF_8
        )
        Files.writeString(core, _core_yaml("content-core-historical-acceptance", "en", "independent current Core"), StandardCharsets.UTF_8)
        val currentbefore = Files.readAllBytes(core)
        val evidencebefore = Files.readAllBytes(acceptanceevidence)

        When("the exact acceptance is retried after the Core has a third identity")
        val failure = _failure(List("document-project", "content-core", "accept", project.toString, "candidate-historical", acceptance.toString))

        Then("the durable record remains historical and neither Core nor evidence is changed")
        _diagnostic_tokens(failure) shouldBe Vector("DP-OP-001")
        Files.readAllBytes(core) shouldBe currentbefore
        Files.readAllBytes(acceptanceevidence) shouldBe evidencebefore
      }
    }

    "reject terminal, superseded, and unknown Content Core candidate decisions before writes" in {
      _with_temp_dir("cozy-document-project-content-core-terminal") { root =>
        Given("separate rejected, accepted, superseded, and unknown candidate decision inputs")
        val project = _scaffolded_project(root, "content-core-terminal")
        val rejected = root.resolve("rejected.json")
        val accepted = root.resolve("accepted.json")
        val pending = root.resolve("pending.json")
        val revised = root.resolve("revised.json")
        val rejectedfeedback = root.resolve("rejected-feedback.json")
        val acceptedacceptance = root.resolve("accepted-acceptance.json")
        val duplicateacceptance = root.resolve("duplicate-acceptance.json")
        val pendingfeedback = root.resolve("pending-feedback.json")
        val rejectedlaterfeedback = root.resolve("rejected-later-feedback.json")
        val acceptedlaterfeedback = root.resolve("accepted-later-feedback.json")
        val supersededlaterfeedback = root.resolve("superseded-later-feedback.json")
        val unknownfeedback = root.resolve("unknown-feedback.json")
        Files.writeString(rejected, _succeeded_dialogue_json("candidate-rejected", "rejected replacement"), StandardCharsets.UTF_8)
        Files.writeString(accepted, _succeeded_dialogue_json("candidate-accepted", "accepted replacement"), StandardCharsets.UTF_8)
        Files.writeString(pending, _succeeded_dialogue_json("candidate-pending", "pending replacement"), StandardCharsets.UTF_8)
        Files.writeString(rejectedfeedback, _feedback_json("feedback-rejected", "candidate-rejected", "rejected"), StandardCharsets.UTF_8)
        Files.writeString(acceptedacceptance, _acceptance_json("acceptance-accepted", "candidate-accepted"), StandardCharsets.UTF_8)
        Files.writeString(duplicateacceptance, _acceptance_json("acceptance-duplicate", "candidate-accepted"), StandardCharsets.UTF_8)
        Files.writeString(pendingfeedback, _feedback_json("feedback-pending", "candidate-pending", "changes-requested"), StandardCharsets.UTF_8)
        Files.writeString(revised, _succeeded_dialogue_json("candidate-successor", "successor replacement", Some("candidate-pending")), StandardCharsets.UTF_8)
        Files.writeString(rejectedlaterfeedback, _feedback_json("feedback-rejected-later", "candidate-rejected", "changes-requested"), StandardCharsets.UTF_8)
        Files.writeString(acceptedlaterfeedback, _feedback_json("feedback-accepted-later", "candidate-accepted", "changes-requested"), StandardCharsets.UTF_8)
        Files.writeString(supersededlaterfeedback, _feedback_json("feedback-superseded-later", "candidate-pending", "changes-requested"), StandardCharsets.UTF_8)
        Files.writeString(unknownfeedback, _feedback_json("feedback-unknown", "candidate-unknown", "changes-requested"), StandardCharsets.UTF_8)
        _execute(List("document-project", "content-core", "candidate", project.toString, rejected.toString))
        _execute(List("document-project", "content-core", "feedback", project.toString, "candidate-rejected", rejectedfeedback.toString))
        _execute(List("document-project", "content-core", "candidate", project.toString, accepted.toString))
        _execute(List("document-project", "content-core", "accept", project.toString, "candidate-accepted", acceptedacceptance.toString))
        _execute(List("document-project", "content-core", "candidate", project.toString, pending.toString))
        _execute(List("document-project", "content-core", "feedback", project.toString, "candidate-pending", pendingfeedback.toString))
        _execute(List("document-project", "content-core", "candidate", project.toString, revised.toString))

        When("a terminal, superseded, or unknown candidate receives later feedback")
        val rejectedfailure = _failure(List("document-project", "content-core", "feedback", project.toString, "candidate-rejected", rejectedlaterfeedback.toString))
        val acceptedfailure = _failure(List("document-project", "content-core", "feedback", project.toString, "candidate-accepted", acceptedlaterfeedback.toString))
        val duplicateacceptancefailure = _failure(List("document-project", "content-core", "accept", project.toString, "candidate-accepted", duplicateacceptance.toString))
        val supersededfailure = _failure(List("document-project", "content-core", "feedback", project.toString, "candidate-pending", supersededlaterfeedback.toString))
        val unknownfailure = _failure(List("document-project", "content-core", "feedback", project.toString, "candidate-unknown", unknownfeedback.toString))

        Then("each inadmissible candidate decision rejects before appending later feedback")
        rejectedfailure should include("DP-OP-001")
        acceptedfailure should include("DP-OP-001")
        duplicateacceptancefailure should include("DP-OP-001")
        supersededfailure should include("DP-OP-001")
        unknownfailure should include("DP-OP-001")
        Files.exists(project.resolve("evidence/content-core/feedback/feedback-rejected-later.yaml"), LinkOption.NOFOLLOW_LINKS) shouldBe false
        Files.exists(project.resolve("evidence/content-core/feedback/feedback-accepted-later.yaml"), LinkOption.NOFOLLOW_LINKS) shouldBe false
        Files.exists(project.resolve("evidence/content-core/feedback/feedback-superseded-later.yaml"), LinkOption.NOFOLLOW_LINKS) shouldBe false
        Files.exists(project.resolve("evidence/content-core/feedback/feedback-unknown.yaml"), LinkOption.NOFOLLOW_LINKS) shouldBe false
        Files.exists(project.resolve("evidence/content-core/acceptances/acceptance-duplicate.yaml"), LinkOption.NOFOLLOW_LINKS) shouldBe false
      }
    }

    "reject unsafe or malformed Content Core inputs, generic compose dispatch, retired feedback reflection, and sidecar dialogue review without writes" in {
      _with_temp_dir("cozy-document-project-content-core-admission") { root =>
        Given("a scaffolded Document Project and invalid direct-input forms")
        val project = _scaffolded_project(root, "content-core-admission")
        val core = project.resolve("content/core-en.yaml")
        val corebefore = Files.readAllBytes(core)
        val realdialogue = root.resolve("real-dialogue.json")
        val linked = root.resolve("linked-dialogue.json")
        val malformed = root.resolve("malformed.json")
        val unsupported = root.resolve("unsupported.txt")
        Files.writeString(realdialogue, _succeeded_dialogue_json("candidate-unsafe", "unsafe replacement"), StandardCharsets.UTF_8)
        Files.createSymbolicLink(linked, realdialogue)
        Files.writeString(malformed, "{not-json", StandardCharsets.UTF_8)
        Files.writeString(unsupported, "not a dialogue", StandardCharsets.UTF_8)

        When("the public boundary receives unsafe, malformed, retired, and generic forms")
        val symlinkfailure = _failure(List("document-project", "content-core", "candidate", project.toString, linked.toString))
        val malformedfailure = _failure(List("document-project", "content-core", "candidate", project.toString, malformed.toString))
        val suffixfailure = _failure(List("document-project", "content-core", "candidate", project.toString, unsupported.toString))
        val genericfailure = _failure(List("document-project", "run", project.toString, "--operation", "content-core.compose"))
        val retiredfailure = _failure(List("document-project", "reflect-feedback", project.toString, "feedback.json"))
        _write_sidecar(project)
        val sidecar = project.resolve("evidence/document-project.yaml")
        Files.writeString(sidecar, Files.readString(sidecar, StandardCharsets.UTF_8).replace("review:\n      kind: none", "review:\n      kind: core-dialogue"), StandardCharsets.UTF_8)
        val sidecarfailure = _failure(List("document-project", "inspect", project.toString))

        Then("all rejected forms preserve Core and do not create Content Core history")
        symlinkfailure should include("DP-PATH-001")
        malformedfailure should include("DP-CLI-001")
        suffixfailure should include("DP-CLI-001")
        genericfailure should include("DP-OP-001")
        retiredfailure should include("DP-CLI-001")
        sidecarfailure should include("DP-DESC-002")
        Files.readAllBytes(core) shouldBe corebefore
        Files.exists(project.resolve("evidence/content-core"), LinkOption.NOFOLLOW_LINKS) shouldBe false
      }
    }

    "reject concurrently held Content Core writer locks without mutation and permit sequential retry" in {
      _with_temp_dir("cozy-document-project-content-core-lock") { root =>
        Given("a scaffolded project and admitted candidate, feedback, and acceptance documents")
        val project = _scaffolded_project(root, "content-core-lock")
        val core = project.resolve("content/core-en.yaml")
        val dialogue = root.resolve("candidate.json")
        val feedback = root.resolve("feedback.json")
        val acceptance = root.resolve("acceptance.json")
        Files.writeString(dialogue, _succeeded_dialogue_json("candidate-lock", "locked candidate replacement"), StandardCharsets.UTF_8)
        Files.writeString(feedback, _feedback_json("feedback-lock", "candidate-lock", "changes-requested"), StandardCharsets.UTF_8)
        Files.writeString(acceptance, _acceptance_json("acceptance-lock", "candidate-lock"), StandardCharsets.UTF_8)
        val corebeforecandidate = Files.readAllBytes(core)

        When("candidate is requested while the project-local writer lock is held")
        val candidatefailure = _with_content_core_lock(project) {
          _failure(List("document-project", "content-core", "candidate", project.toString, dialogue.toString))
        }

        Then("candidate returns only retryable DP-OP-001 without evidence or Core mutation")
        _diagnostic_tokens(candidatefailure) shouldBe Vector("DP-OP-001")
        Files.readAllBytes(core) shouldBe corebeforecandidate
        Files.exists(project.resolve("evidence/content-core"), LinkOption.NOFOLLOW_LINKS) shouldBe false

        When("the lock is released and the same candidate is retried")
        val candidateoutput = _execute(List("document-project", "content-core", "candidate", project.toString, dialogue.toString))

        Then("the candidate is appended normally")
        candidateoutput should include("candidate: candidate-lock")
        Files.isRegularFile(project.resolve("evidence/content-core/candidates/candidate-lock.yaml"), LinkOption.NOFOLLOW_LINKS) shouldBe true
        val corebeforefeedback = Files.readAllBytes(core)

        When("feedback is requested while the project-local writer lock is held")
        val feedbackfailure = _with_content_core_lock(project) {
          _failure(List("document-project", "content-core", "feedback", project.toString, "candidate-lock", feedback.toString))
        }

        Then("feedback returns only retryable DP-OP-001 without evidence or Core mutation")
        _diagnostic_tokens(feedbackfailure) shouldBe Vector("DP-OP-001")
        Files.readAllBytes(core) shouldBe corebeforefeedback
        Files.exists(project.resolve("evidence/content-core/feedback/feedback-lock.yaml"), LinkOption.NOFOLLOW_LINKS) shouldBe false

        When("the lock is released and the same feedback is retried")
        val feedbackoutput = _execute(List("document-project", "content-core", "feedback", project.toString, "candidate-lock", feedback.toString))

        Then("the feedback is appended normally")
        feedbackoutput should include("decision: changes-requested")
        Files.isRegularFile(project.resolve("evidence/content-core/feedback/feedback-lock.yaml"), LinkOption.NOFOLLOW_LINKS) shouldBe true
        val corebeforeacceptance = Files.readAllBytes(core)

        When("accept is requested while the project-local writer lock is held")
        val acceptancefailure = _with_content_core_lock(project) {
          _failure(List("document-project", "content-core", "accept", project.toString, "candidate-lock", acceptance.toString))
        }

        Then("accept returns only retryable DP-OP-001 without evidence or Core mutation")
        _diagnostic_tokens(acceptancefailure) shouldBe Vector("DP-OP-001")
        Files.readAllBytes(core) shouldBe corebeforeacceptance
        Files.exists(project.resolve("evidence/content-core/acceptances/acceptance-lock.yaml"), LinkOption.NOFOLLOW_LINKS) shouldBe false

        When("the lock is released and the same acceptance is retried")
        val acceptanceoutput = _execute(List("document-project", "content-core", "accept", project.toString, "candidate-lock", acceptance.toString))

        Then("the single acceptance evidence precedes the accepted Core replacement")
        acceptanceoutput should include("decision: accepted")
        Files.isRegularFile(project.resolve("evidence/content-core/acceptances/acceptance-lock.yaml"), LinkOption.NOFOLLOW_LINKS) shouldBe true
        Files.readString(core, StandardCharsets.UTF_8) should include("text: \"locked candidate replacement\"")
      }
    }

    "give a held direct regular writer lock precedence over a malformed Content Core at the public candidate boundary" in {
      _with_temp_dir("cozy-document-project-content-core-held-lock-precedence") { root =>
        Given("a scaffolded project, admitted dialogue, malformed Core, and held direct regular writer lock")
        val project = _scaffolded_project(root, "content-core-held-lock-precedence")
        val core = project.resolve("content/core-en.yaml")
        val dialogue = root.resolve("candidate.json")
        Files.writeString(dialogue, _succeeded_dialogue_json("candidate-held-lock-precedence", "held lock precedence candidate"), StandardCharsets.UTF_8)
        Files.writeString(core, "malformed: [\n", StandardCharsets.UTF_8)
        val corebefore = Files.readAllBytes(core)

        When("the public Content Core candidate command is admitted while the writer lock is held")
        val failure = _with_content_core_lock(project) {
          _failure(List("document-project", "content-core", "candidate", project.toString, dialogue.toString))
        }

        Then("only retryable DP-OP-001 is returned without mutating the malformed Core or creating evidence")
        _diagnostic_tokens(failure) shouldBe Vector("DP-OP-001")
        Files.readAllBytes(core) shouldBe corebefore
        Files.exists(project.resolve("evidence/content-core"), LinkOption.NOFOLLOW_LINKS) shouldBe false
      }
    }

    "give an unsafe writer lock precedence over a malformed Content Core at the public candidate boundary" in {
      _with_temp_dir("cozy-document-project-content-core-unsafe-lock-precedence") { root =>
        Given("a scaffolded project, admitted dialogue, malformed Core, and symbolic-link writer lock")
        val project = _scaffolded_project(root, "content-core-unsafe-lock-precedence")
        val core = project.resolve("content/core-en.yaml")
        val dialogue = root.resolve("candidate.json")
        val external = root.resolve("external-content-core.lock")
        Files.writeString(dialogue, _succeeded_dialogue_json("candidate-unsafe-lock-precedence", "unsafe lock precedence candidate"), StandardCharsets.UTF_8)
        Files.writeString(core, "malformed: [\n", StandardCharsets.UTF_8)
        val corebefore = Files.readAllBytes(core)
        Files.writeString(external, "external lock\n", StandardCharsets.UTF_8)
        Files.createSymbolicLink(project.resolve(".content-core.lock"), external)

        When("the public Content Core candidate command is admitted with the unsafe writer lock")
        val failure = _failure(List("document-project", "content-core", "candidate", project.toString, dialogue.toString))

        Then("only DP-PATH-001 is returned without mutating the malformed Core or creating evidence")
        _diagnostic_tokens(failure) shouldBe Vector("DP-PATH-001")
        Files.readAllBytes(core) shouldBe corebefore
        Files.exists(project.resolve("evidence/content-core"), LinkOption.NOFOLLOW_LINKS) shouldBe false
      }
    }

    "reject a symbolic-link Content Core writer lock before mutation" in {
      _with_temp_dir("cozy-document-project-content-core-lock-symlink") { root =>
        Given("a scaffolded project, a direct candidate dialogue, and a symbolic-link writer lock")
        val project = _scaffolded_project(root, "content-core-lock-symlink")
        val core = project.resolve("content/core-en.yaml")
        val corebefore = Files.readAllBytes(core)
        val dialogue = root.resolve("candidate.json")
        val external = root.resolve("external-content-core.lock")
        Files.writeString(dialogue, _succeeded_dialogue_json("candidate-lock-symlink", "symbolic-link lock candidate"), StandardCharsets.UTF_8)
        Files.writeString(external, "external lock\n", StandardCharsets.UTF_8)
        Files.createSymbolicLink(project.resolve(".content-core.lock"), external)

        When("the public Content Core candidate command receives the symbolic-link writer lock")
        val failure = _failure(List("document-project", "content-core", "candidate", project.toString, dialogue.toString))

        Then("path admission returns only DP-PATH-001 without changing Core or creating evidence")
        _diagnostic_tokens(failure) shouldBe Vector("DP-PATH-001")
        Files.readAllBytes(core) shouldBe corebefore
        Files.exists(project.resolve("evidence"), LinkOption.NOFOLLOW_LINKS) shouldBe false
      }
    }

    "reject a non-regular Content Core writer lock before mutation" in {
      _with_temp_dir("cozy-document-project-content-core-lock-directory") { root =>
        Given("a scaffolded project, a direct candidate dialogue, and a directory writer lock")
        val project = _scaffolded_project(root, "content-core-lock-directory")
        val core = project.resolve("content/core-en.yaml")
        val corebefore = Files.readAllBytes(core)
        val dialogue = root.resolve("candidate.json")
        Files.writeString(dialogue, _succeeded_dialogue_json("candidate-lock-directory", "directory lock candidate"), StandardCharsets.UTF_8)
        Files.createDirectory(project.resolve(".content-core.lock"))

        When("the public Content Core candidate command receives the directory writer lock")
        val failure = _failure(List("document-project", "content-core", "candidate", project.toString, dialogue.toString))

        Then("path admission returns only DP-PATH-001 without changing Core or creating evidence")
        _diagnostic_tokens(failure) shouldBe Vector("DP-PATH-001")
        Files.readAllBytes(core) shouldBe corebefore
        Files.exists(project.resolve("evidence"), LinkOption.NOFOLLOW_LINKS) shouldBe false
      }
    }

    "execute the sole native Article review provider and retain no P56 evidence authority" in {
      _with_temp_dir("cozy-document-project-run") { root =>
        Given("an admitted standard Document Project with Article review selected")
        val parent = Files.createDirectory(root.resolve("parent"))
        _execute(List("document-project", "scaffold", "sample", "--profile", "standard", "--language", "en", "--workspace", "directory", "--save", parent.toString))
        val project = parent.resolve("sample.dox")
        _activate_optional_work_products(project, "standard", Vector("article-review-html"))
        val authored = project.resolve("index.dox")
        val authoredbytes = Files.readAllBytes(authored)

        When("dry-run resolves the admitted native provider without invocation")
        val dryrun = _execute(List("document-project", "run", project.toString, "--operation", "article.render-review", "--dry-run"))

        Then("dry-run reports the typed declaration without target, attempt, or currentness mutation")
        dryrun should include("operation: article.render-review")
        dryrun should include("provider-binding: cozy-review-projection")
        dryrun should include("profile: standard")
        dryrun should include("outcome: resolved")
        dryrun should include("identity: article-review-html")
        dryrun should include("path: target/document-project/article-review.html")
        dryrun should include("mediaType: text/html")
        Files.exists(project.resolve("evidence/attempts"), LinkOption.NOFOLLOW_LINKS) shouldBe false
        Files.exists(project.resolve("target"), LinkOption.NOFOLLOW_LINKS) shouldBe false

        When("the admitted native operation is run")
        val output = _execute(List("document-project", "run", project.toString, "--operation", "article.render-review"))

        Then("the typed result carries the one HTML output, diagnostics, and generated receipt")
        output should include("outcome: executed")
        output should include("identity: article-review-html")
        output should include("path: target/document-project/article-review.html")
        output should include("mediaType: text/html")
        output should include("native review projection rendered")
        output should include("receipt:")
        output should include("cozy.document-project.native-receipt:")
        output should include("evidence: none")
        output should include("currentness: unchanged")
        val nativehtml = Files.readString(project.resolve("target/document-project/article-review.html"), StandardCharsets.UTF_8)
        nativehtml should include("Native article.render-review execution")
        nativehtml should include("writes only the declared HTML output")
        nativehtml should include("does not persist a receipt file, Operation Attempt, evidence, currentness, or acceptance state")
        nativehtml should include("not a renderer, publication, compatibility adapter, or successor-owned persistence")
        nativehtml should not include("No provider execution, renderer input")
        Files.readAllBytes(authored) shouldBe authoredbytes
        Files.exists(project.resolve("target/document-project/article-review.html"), LinkOption.NOFOLLOW_LINKS) shouldBe true
        Files.exists(project.resolve("target/document-project/article-review.receipt.yaml"), LinkOption.NOFOLLOW_LINKS) shouldBe false
        Files.exists(project.resolve("evidence/attempts"), LinkOption.NOFOLLOW_LINKS) shouldBe false
        Files.exists(project.resolve("state"), LinkOption.NOFOLLOW_LINKS) shouldBe false
        Files.exists(project.resolve("operation-receipt-evidence"), LinkOption.NOFOLLOW_LINKS) shouldBe false
        Files.exists(project.resolve("target/document-project/state.yaml"), LinkOption.NOFOLLOW_LINKS) shouldBe false

        When("a known binding without a native provider is run")
        val blocked = _execute(List("document-project", "run", project.toString, "--operation", "article.render-pdf"))

        Then("it is explicitly blocked without a new output or an Operation Attempt")
        blocked should include("outcome: blocked")
        blocked should include("operation: article.render-pdf")
        blocked should include("provider-binding: smartdox-rendering")
        blocked should include("missing-capability: native typed provider execution is unavailable")
        Files.exists(project.resolve("evidence/attempts"), LinkOption.NOFOLLOW_LINKS) shouldBe false
      }
    }

    "admit Article review inputs and output destination before native provider invocation" in {
      _with_temp_dir("cozy-document-project-native-run-admission") { root =>
        Given("a selected Article review project whose authoritative article source is symbolic")
        val sourceparent = Files.createDirectory(root.resolve("source-parent"))
        _execute(List("document-project", "scaffold", "source", "--profile", "standard", "--language", "en", "--workspace", "directory", "--save", sourceparent.toString))
        val sourceproject = sourceparent.resolve("source.dox")
        _activate_optional_work_products(sourceproject, "standard", Vector("article-review-html"))
        val externalarticle = root.resolve("external-index.dox")
        Files.writeString(externalarticle, "external\n", StandardCharsets.UTF_8)
        Files.delete(sourceproject.resolve("index.dox"))
        Files.createSymbolicLink(sourceproject.resolve("index.dox"), externalarticle)

        When("the native operation is requested with the unsafe authoritative input")
        val sourcefailure = _failure(List("document-project", "run", sourceproject.toString, "--operation", "article.render-review"))

        Then("input admission rejects it before output or evidence mutation")
        _diagnostic_tokens(sourcefailure) shouldBe Vector("DP-PATH-001")
        Files.exists(sourceproject.resolve("target"), LinkOption.NOFOLLOW_LINKS) shouldBe false
        Files.exists(sourceproject.resolve("evidence/attempts"), LinkOption.NOFOLLOW_LINKS) shouldBe false

        Given("a second selected Article review project whose declared output destination is symbolic")
        val outputparent = Files.createDirectory(root.resolve("output-parent"))
        _execute(List("document-project", "scaffold", "output", "--profile", "standard", "--language", "en", "--workspace", "directory", "--save", outputparent.toString))
        val outputproject = outputparent.resolve("output.dox")
        _activate_optional_work_products(outputproject, "standard", Vector("article-review-html"))
        val externaloutput = root.resolve("external-review.html")
        Files.writeString(externaloutput, "unchanged\n", StandardCharsets.UTF_8)
        Files.createDirectories(outputproject.resolve("target/document-project"))
        Files.createSymbolicLink(outputproject.resolve("target/document-project/article-review.html"), externaloutput)

        When("the native operation is requested with the unsafe declared output")
        val outputfailure = _failure(List("document-project", "run", outputproject.toString, "--operation", "article.render-review"))

        Then("output admission rejects it before provider invocation or an Operation Attempt")
        _diagnostic_tokens(outputfailure) shouldBe Vector("DP-PATH-001")
        Files.readString(externaloutput, StandardCharsets.UTF_8) shouldBe "unchanged\n"
        Files.exists(outputproject.resolve("evidence/attempts"), LinkOption.NOFOLLOW_LINKS) shouldBe false

        Given("a third selected Article review project whose infographic source is missing")
        val missingparent = Files.createDirectory(root.resolve("missing-parent"))
        _execute(List("document-project", "scaffold", "missing", "--profile", "standard", "--language", "en", "--workspace", "directory", "--save", missingparent.toString))
        val missingproject = missingparent.resolve("missing.dox")
        _activate_optional_work_products(missingproject, "standard", Vector("article-review-html"))
        Files.delete(missingproject.resolve("infographic/infographic.svg"))

        When("the native operation is requested with the missing infographic authority")
        val missingfailure = _failure(List("document-project", "run", missingproject.toString, "--operation", "article.render-review"))

        Then("infographic admission rejects it before output or an Operation Attempt")
        _diagnostic_tokens(missingfailure) shouldBe Vector("DP-PATH-001")
        missingfailure should include("must be a direct regular non-symlink file")
        Files.exists(missingproject.resolve("target"), LinkOption.NOFOLLOW_LINKS) shouldBe false
        Files.exists(missingproject.resolve("evidence/attempts"), LinkOption.NOFOLLOW_LINKS) shouldBe false

        Given("a fourth selected Article review project whose infographic source is symbolic")
        val symbolicparent = Files.createDirectory(root.resolve("symbolic-parent"))
        _execute(List("document-project", "scaffold", "symbolic", "--profile", "standard", "--language", "en", "--workspace", "directory", "--save", symbolicparent.toString))
        val symbolicproject = symbolicparent.resolve("symbolic.dox")
        _activate_optional_work_products(symbolicproject, "standard", Vector("article-review-html"))
        val externalinfographic = root.resolve("external-infographic.svg")
        Files.writeString(externalinfographic, "<svg>external</svg>\n", StandardCharsets.UTF_8)
        Files.delete(symbolicproject.resolve("infographic/infographic.svg"))
        Files.createSymbolicLink(symbolicproject.resolve("infographic/infographic.svg"), externalinfographic)

        When("the native operation is requested with the symbolic infographic authority")
        val symbolicfailure = _failure(List("document-project", "run", symbolicproject.toString, "--operation", "article.render-review"))

        Then("symbolic infographic admission rejects it before output or an Operation Attempt")
        _diagnostic_tokens(symbolicfailure) shouldBe Vector("DP-PATH-001")
        symbolicfailure should include("must be a direct regular non-symlink file")
        Files.exists(symbolicproject.resolve("target"), LinkOption.NOFOLLOW_LINKS) shouldBe false
        Files.exists(symbolicproject.resolve("evidence/attempts"), LinkOption.NOFOLLOW_LINKS) shouldBe false
      }
    }

    "reject empty native provider output declarations as failed results" in {
      _with_temp_dir("cozy-document-project-native-provider-result") { root =>
        Given("an admitted selected Article review project and an empty provider declaration")
        val project = _scaffolded_project(root, "empty-result")
        _activate_optional_work_products(project, "standard", Vector("article-review-html"))
        val descriptor = CozyDocumentProject._load_project(project)
        val operation = CozyDocumentWorkflow.declaredOperation("article.render-review") match {
          case Right(Some(value)) => value
          case _ => throw new RuntimeException("article.render-review must be declared")
        }
        val declaration = CozyDocumentWorkflow.NativeProviderDeclaration("article.render-review", "cozy-review-projection", Vector.empty)

        When("the provider receives no declared output")
        val result = CozyDocumentProjectProvider.execute(CozyDocumentProjectProvider.Request(project, descriptor, operation, declaration, Vector.empty))

        Then("it cannot represent execution success without outputs and a receipt")
        result shouldBe CozyDocumentProjectProvider.Failed(
          "article.render-review",
          "cozy-review-projection",
          "unresolved provider",
          Vector("native provider requires one admitted destination for every declared output")
        )
        Files.exists(project.resolve("target"), LinkOption.NOFOLLOW_LINKS) shouldBe false
        Files.exists(project.resolve("evidence/attempts"), LinkOption.NOFOLLOW_LINKS) shouldBe false
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

    "publish deterministic presentation confirmation defaults with the canonical receipt" in {
      _with_temp_dir("cozy-document-project-presentation-confirmation") { root =>
        Given("a current Presentation Semantics project with presentation confirmation selected")
        val project = _scaffolded_project(root, "presentation-confirmation")
        _activate_optional_work_products(project, "standard", Vector("presentation-confirmation-html"))
        _write_valid_presentation_semantics(project)
        val html = project.resolve("target/document-project/presentation-confirmation.html")
        val receipt = project.resolve("target/document-project/presentation-confirmation.receipt.yaml")

        When("the default presentation confirmation is run twice")
        val first = _execute(List("document-project", "review", project.toString, "--kind", "presentation"))
        val firsthtml = Files.readAllBytes(html)
        val firstreceipt = Files.readAllBytes(receipt)
        val second = _execute(List("document-project", "review", project.toString, "--kind", "presentation"))

        Then("the exact default HTML and canonical typed receipt are atomically replaced with deterministic bytes")
        first should include("Presentation Confirmation")
        second should include("Presentation Confirmation")
        Files.readAllBytes(html) shouldBe firsthtml
        Files.readAllBytes(receipt) shouldBe firstreceipt
        Files.readString(receipt, StandardCharsets.UTF_8) should include("\"projectionIdentity\":")
      }
    }

    "propagate a changed Core identity to selected semantic products and retained confirmation without write-back" in {
      _with_temp_dir("cozy-document-project-presentation-confirmation-core-stale") { root =>
        Given("a current project with selected confirmation and logical-chart semantic products")
        val project = _scaffolded_project(root, "presentation-confirmation-core-stale")
        _activate_optional_work_products(project, "standard", Vector("presentation-confirmation-html", "explanation-structure-review-html"))
        _write_valid_presentation_semantics(project)
        val core = project.resolve("content/core-en.yaml")
        val semantics = project.resolve("content/presentation-semantics-en.yaml")
        val descriptor = project.resolve("document-project.yaml")
        val html = project.resolve("target/document-project/presentation-confirmation.html")
        val receipt = project.resolve("target/document-project/presentation-confirmation.receipt.yaml")
        val chart = project.resolve("target/document-project/slide-logical-chart-review.html")

        When("the confirmation and selected semantic chart are generated, then the Core identity changes")
        _execute(List("document-project", "review", project.toString, "--kind", "presentation"))
        _execute(List("document-project", "review", project.toString, "--kind", "slide-logical-chart"))
        val semanticsbytes = Files.readAllBytes(semantics)
        val descriptorbytes = Files.readAllBytes(descriptor)
        val htmlbytes = Files.readAllBytes(html)
        val receiptbytes = Files.readAllBytes(receipt)
        val chartbytes = Files.readAllBytes(chart)
        Files.writeString(core, Files.readString(core, StandardCharsets.UTF_8).replace("accepted: []", "accepted:\n  - id: changed-core\n    text: Changed Core"), StandardCharsets.UTF_8)
        _execute(List("document-project", "inspect", project.toString))
        val state = Files.readString(project.resolve("target/document-project/state.yaml"), StandardCharsets.UTF_8)

        Then("the stale Core dependency reaches Presentation Semantics, every selected dependent semantic product, and confirmation")
        state should include("id: presentation-semantics\n    role: authority\n    disposition: required\n    selection: required\n    criterion: presentation-semantics-validated\n    coverage: missing\n    currentness: stale")
        state should include("id: article-source\n    role: authority\n    disposition: required\n    selection: required\n    criterion: article-source-authored\n    coverage: missing\n    currentness: stale")
        state should include("id: visual-pages\n    role: authority\n    disposition: required\n    selection: required\n    criterion: visual-pages-authored\n    coverage: missing\n    currentness: stale")
        state should include("id: explanation-structure-review-html\n    role: review-projection\n    disposition: optional\n    selection: active-optional\n    criterion: slide-logical-chart-rendered\n    coverage: missing\n    currentness: stale")
        state should include("id: presentation-confirmation-html\n    role: review-projection\n    disposition: optional\n    selection: active-optional\n    criterion: presentation-confirmation-rendered\n    coverage: missing\n    currentness: stale")

        And("semantic authorities, the descriptor, and retained generated bytes remain untouched by inspection")
        Files.readAllBytes(semantics) shouldBe semanticsbytes
        Files.readAllBytes(descriptor) shouldBe descriptorbytes
        Files.readAllBytes(html) shouldBe htmlbytes
        Files.readAllBytes(receipt) shouldBe receiptbytes
        Files.readAllBytes(chart) shouldBe chartbytes
      }
    }

    "stale a retained confirmation for a changed valid semantic identity until public review recovers it" in {
      _with_temp_dir("cozy-document-project-presentation-confirmation-semantic-stale") { root =>
        Given("a current project with selected presentation confirmation and retained default bytes")
        val project = _scaffolded_project(root, "presentation-confirmation-semantic-stale")
        _activate_optional_work_products(project, "standard", Vector("presentation-confirmation-html"))
        _write_valid_presentation_semantics(project)
        val semantics = project.resolve("content/presentation-semantics-en.yaml")
        val html = project.resolve("target/document-project/presentation-confirmation.html")
        val receipt = project.resolve("target/document-project/presentation-confirmation.receipt.yaml")
        _execute(List("document-project", "review", project.toString, "--kind", "presentation"))
        val oldhtmlbytes = Files.readAllBytes(html)
        val oldreceiptbytes = Files.readAllBytes(receipt)

        When("the authored Presentation Semantics identity changes while remaining valid")
        Files.writeString(
          semantics,
          Files.readString(semantics, StandardCharsets.UTF_8).replace("The prior reader path was permissive.", "The revised reader path remains explicit."),
          StandardCharsets.UTF_8
        )
        _execute(List("document-project", "inspect", project.toString))
        val stale = Files.readString(project.resolve("target/document-project/state.yaml"), StandardCharsets.UTF_8)

        Then("the prior default confirmation HTML and receipt are stale without being rewritten")
        stale should include("id: presentation-semantics\n    role: authority\n    disposition: required\n    selection: required\n    criterion: presentation-semantics-validated\n    coverage: satisfied\n    currentness: current")
        stale should include("id: presentation-confirmation-html\n    role: review-projection\n    disposition: optional\n    selection: active-optional\n    criterion: presentation-confirmation-rendered\n    coverage: missing\n    currentness: stale")
        Files.readAllBytes(html) shouldBe oldhtmlbytes
        Files.readAllBytes(receipt) shouldBe oldreceiptbytes

        Given("the prior confirmation is stale while its retained HTML and receipt bytes remain unchanged")
        When("the existing public presentation review is run")
        _execute(List("document-project", "review", project.toString, "--kind", "presentation"))
        _execute(List("document-project", "inspect", project.toString))
        val recovered = Files.readString(project.resolve("target/document-project/state.yaml"), StandardCharsets.UTF_8)

        Then("only the public review route recovers current, satisfied, and ready confirmation evidence")
        recovered should include("id: presentation-confirmation-html\n    role: review-projection\n    disposition: optional\n    selection: active-optional\n    criterion: presentation-confirmation-rendered\n    coverage: satisfied\n    currentness: current\n    review: pending\n    readiness: ready")
        Files.readAllBytes(html) should not equal oldhtmlbytes
        Files.readAllBytes(receipt) should not equal oldreceiptbytes
      }
    }

    "keep coverage-failed semantics blocked despite previously current confirmation receipt bytes" in {
      _with_temp_dir("cozy-document-project-presentation-confirmation-coverage") { root =>
        Given("a current project with selected confirmation and retained current default bytes")
        val project = _scaffolded_project(root, "presentation-confirmation-coverage")
        _activate_optional_work_products(project, "standard", Vector("presentation-confirmation-html"))
        _write_valid_presentation_semantics(project)
        val html = project.resolve("target/document-project/presentation-confirmation.html")
        val receipt = project.resolve("target/document-project/presentation-confirmation.receipt.yaml")
        _execute(List("document-project", "review", project.toString, "--kind", "presentation"))
        val oldhtmlbytes = Files.readAllBytes(html)
        val oldreceiptbytes = Files.readAllBytes(receipt)

        When("the semantic source changes to a current but projection-coverage-failed identity")
        _write_valid_presentation_semantics(project, includeproblemstructure = false)
        _execute(List("document-project", "inspect", project.toString))
        val state = Files.readString(project.resolve("target/document-project/state.yaml"), StandardCharsets.UTF_8)

        Then("retained receipt bytes do not make semantic coverage or confirmation ready")
        state should include("id: presentation-semantics\n    role: authority\n    disposition: required\n    selection: required\n    criterion: presentation-semantics-validated\n    coverage: missing\n    currentness: current\n    review: pending\n    readiness: blocked")
        state should include("id: presentation-confirmation-html\n    role: review-projection\n    disposition: optional\n    selection: active-optional\n    criterion: presentation-confirmation-rendered\n    coverage: missing\n    currentness: missing\n    review: pending\n    readiness: blocked")
        Files.readAllBytes(html) shouldBe oldhtmlbytes
        Files.readAllBytes(receipt) shouldBe oldreceiptbytes
      }
    }

    "publish a requested presentation confirmation without default output or receipt" in {
      _with_temp_dir("cozy-document-project-presentation-confirmation-custom-save") { root =>
        Given("a fresh current Presentation Semantics project with presentation confirmation selected")
        val project = _scaffolded_project(root, "presentation-confirmation-custom-save")
        _activate_optional_work_products(project, "standard", Vector("presentation-confirmation-html"))
        _write_valid_presentation_semantics(project)
        val custom = root.resolve("saved-confirmation.html")
        val html = project.resolve("target/document-project/presentation-confirmation.html")
        val receipt = project.resolve("target/document-project/presentation-confirmation.receipt.yaml")

        When("a requested presentation confirmation is saved")
        _execute(List("document-project", "review", project.toString, "--kind", "presentation", "--save", custom.toString))

        Then("only the requested confirmation HTML exists")
        Files.isRegularFile(custom, LinkOption.NOFOLLOW_LINKS) shouldBe true
        Files.exists(html, LinkOption.NOFOLLOW_LINKS) shouldBe false
        Files.exists(receipt, LinkOption.NOFOLLOW_LINKS) shouldBe false
      }
    }

    "keep default Article review output and its generated-review receipt distinct from presentation confirmation" in {
      _with_temp_dir("cozy-document-project-presentation-confirmation-article-review") { root =>
        Given("a current Presentation Semantics project with Article review and presentation confirmation selected")
        val project = _scaffolded_project(root, "presentation-confirmation-article-review")
        _activate_optional_work_products(project, "standard", Vector("presentation-confirmation-html", "article-review-html"))
        _write_valid_presentation_semantics(project)
        val presentationhtml = project.resolve("target/document-project/presentation-confirmation.html")
        val presentationreceipt = project.resolve("target/document-project/presentation-confirmation.receipt.yaml")

        When("default Article review is generated")
        _execute(List("document-project", "review", project.toString, "--kind", "article"))

        Then("Article review retains only its own output and generated-review receipt")
        Files.isRegularFile(project.resolve("target/document-project/article-review.html"), LinkOption.NOFOLLOW_LINKS) shouldBe true
        Files.isRegularFile(project.resolve("target/document-project/article-review.receipt.yaml"), LinkOption.NOFOLLOW_LINKS) shouldBe true
        Files.exists(presentationhtml, LinkOption.NOFOLLOW_LINKS) shouldBe false
        Files.exists(presentationreceipt, LinkOption.NOFOLLOW_LINKS) shouldBe false
      }
    }

    "refuse strict-currentness and projection-coverage presentation confirmation without files" in {
      _with_temp_dir("cozy-document-project-presentation-confirmation-refusal") { root =>
        Given("selected presentation confirmation projects with incomplete semantics and incomplete projection coverage")
        val incomplete = _scaffolded_project(root, "presentation-incomplete")
        _activate_optional_work_products(incomplete, "standard", Vector("presentation-confirmation-html"))
        val incompletehtml = incomplete.resolve("target/document-project/presentation-confirmation.html")
        val incompletereceipt = incomplete.resolve("target/document-project/presentation-confirmation.receipt.yaml")
        val coverage = _scaffolded_project(root, "presentation-coverage")
        _activate_optional_work_products(coverage, "standard", Vector("presentation-confirmation-html"))
        _write_valid_presentation_semantics(coverage, includeproblemstructure = false)
        val coveragehtml = coverage.resolve("target/document-project/presentation-confirmation.html")
        val coveragereceipt = coverage.resolve("target/document-project/presentation-confirmation.receipt.yaml")

        When("presentation confirmation reaches strict validation and projection coverage refusal")
        val incompletefailure = _failure(List("document-project", "review", incomplete.toString, "--kind", "presentation"))
        val coveragefailure = _failure(List("document-project", "review", coverage.toString, "--kind", "presentation"))

        Then("neither refusal publishes confirmation HTML or a typed receipt")
        _diagnostic_tokens(incompletefailure) shouldBe Vector("DP-SEM-006")
        _diagnostic_tokens(coveragefailure) shouldBe Vector("DP-PROJ-001")
        Files.exists(incompletehtml, LinkOption.NOFOLLOW_LINKS) shouldBe false
        Files.exists(incompletereceipt, LinkOption.NOFOLLOW_LINKS) shouldBe false
        Files.exists(coveragehtml, LinkOption.NOFOLLOW_LINKS) shouldBe false
        Files.exists(coveragereceipt, LinkOption.NOFOLLOW_LINKS) shouldBe false
      }
    }

    "reject unselected presentation confirmation without publishing output" in {
      _with_temp_dir("cozy-document-project-presentation-confirmation-unselected") { root =>
        Given("a standard project whose optional presentation confirmation Work Product is not selected")
        val project = _scaffolded_project(root, "presentation-confirmation-unselected")
        val html = project.resolve("target/document-project/presentation-confirmation.html")
        val receipt = project.resolve("target/document-project/presentation-confirmation.receipt.yaml")

        When("presentation confirmation review is requested")
        val failure = _failure(List("document-project", "review", project.toString, "--kind", "presentation"))

        Then("the existing not-selected operation diagnostic is returned before confirmation HTML or receipt publication")
        _diagnostic_tokens(failure) shouldBe Vector("DP-OP-001")
        failure should include("logical operation presentation.render-confirmation is not selected for profile standard")
        Files.exists(html, LinkOption.NOFOLLOW_LINKS) shouldBe false
        Files.exists(receipt, LinkOption.NOFOLLOW_LINKS) shouldBe false
      }
    }

    "admit presentation review while preserving generic run rejection without creating evidence" in {
      _with_temp_dir("cozy-document-project-presentation-confirmation-admission") { root =>
        Given("an admitted standard project with optional presentation confirmation selected")
        val project = _scaffolded_project(root, "presentation-confirmation-admission")
        _activate_optional_work_products(project, "standard", Vector("presentation-confirmation-html"))

        When("dry-run and recording generic confirmation operations are requested")
        val dryrunfailure = _failure(List("document-project", "run", project.toString, "--operation", "presentation.render-confirmation", "--dry-run"))
        val recordingfailure = _failure(List("document-project", "run", project.toString, "--operation", "presentation.render-confirmation"))

        Then("both requests return only the reserved-operation diagnostic before evidence publication")
        _diagnostic_tokens(dryrunfailure) shouldBe Vector("DP-OP-001")
        _diagnostic_tokens(recordingfailure) shouldBe Vector("DP-OP-001")
        dryrunfailure should include("presentation.render-confirmation is reserved for document-project review --kind presentation")
        recordingfailure should include("presentation.render-confirmation is reserved for document-project review --kind presentation")
        Files.exists(project.resolve("evidence"), LinkOption.NOFOLLOW_LINKS) shouldBe false

        When("the presentation review form and public help reach their live admissions")
        _write_valid_presentation_semantics(project)
        val review = _execute(List("document-project", "review", project.toString, "--kind", "presentation"))
        val help = CozyHelpText._text

        Then("the parser and help admit exactly the presentation kind without a generic route")
        review should include("Presentation Confirmation")
        help should include("document-project review <project> --kind presentation [--save <confirmation.html>]")
        _failure(List("document-project", "review", project.toString, "--kind", "presentation-confirmation")) should include("DP-CLI-001")
        Files.exists(project.resolve("evidence"), LinkOption.NOFOLLOW_LINKS) shouldBe false
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

    "admit unsafe initial source paths before rejecting generic Content Core compose" in {
      _with_temp_dir("cozy-document-project-generic-compose-input") { root =>
        Given("a standard scaffold whose required initial source is replaced by a symbolic link")
        val parent = Files.createDirectory(root.resolve("parent"))
        _execute(List("document-project", "scaffold", "sample", "--profile", "standard", "--language", "en", "--workspace", "directory", "--save", parent.toString))
        val project = parent.resolve("sample.dox")
        val external = root.resolve("external-index.dox")
        Files.writeString(external, "external source\n", StandardCharsets.UTF_8)
        Files.delete(project.resolve("index.dox"))
        Files.createSymbolicLink(project.resolve("index.dox"), external)

        When("generic Content Core compose is requested")
        val failure = _failure(List("document-project", "run", project.toString, "--operation", "content-core.compose"))

        Then("source admission takes precedence and creates no generic attempt or Content Core evidence")
        _diagnostic_tokens(failure) shouldBe Vector("DP-PATH-001")
        Files.exists(project.resolve("evidence"), LinkOption.NOFOLLOW_LINKS) shouldBe false
      }
    }

    "admit an unsafe standard-video storyboard before rejecting generic Content Core compose" in {
      _with_temp_dir("cozy-document-project-generic-compose-video-input") { root =>
        Given("a standard-video scaffold whose direct storyboard is replaced by a symbolic link")
        val parent = Files.createDirectory(root.resolve("parent"))
        _execute(List("document-project", "scaffold", "sample", "--profile", "standard-video", "--language", "en", "--workspace", "directory", "--save", parent.toString))
        val project = parent.resolve("sample.dox")
        val external = root.resolve("external-storyboard.md")
        Files.writeString(external, "external storyboard\n", StandardCharsets.UTF_8)
        Files.delete(project.resolve("video/storyboard.md"))
        Files.createSymbolicLink(project.resolve("video/storyboard.md"), external)

        When("generic Content Core compose is requested")
        val failure = _failure(List("document-project", "run", project.toString, "--operation", "content-core.compose"))

        Then("storyboard path admission returns only DP-PATH-001 before any generic attempt or Content Core evidence")
        _diagnostic_tokens(failure) shouldBe Vector("DP-PATH-001")
        Files.exists(project.resolve("evidence"), LinkOption.NOFOLLOW_LINKS) shouldBe false
      }
    }

    "reject a missing standard-video storyboard before generic Content Core compose" in {
      _with_temp_dir("cozy-document-project-generic-compose-video-missing") { root =>
        Given("a standard-video scaffold whose direct storyboard is missing")
        val parent = Files.createDirectory(root.resolve("parent"))
        _execute(List("document-project", "scaffold", "sample", "--profile", "standard-video", "--language", "en", "--workspace", "directory", "--save", parent.toString))
        val project = parent.resolve("sample.dox")
        Files.delete(project.resolve("video/storyboard.md"))

        When("generic Content Core compose is requested")
        val failure = _failure(List("document-project", "run", project.toString, "--operation", "content-core.compose"))

        Then("missing storyboard path admission returns only DP-PATH-001 before any evidence")
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

    "block the activated video operation when its native provider is unavailable" in {
      _with_temp_dir("cozy-document-project-run-video") { root =>
        Given("an admitted standard-video Document Project")
        val parent = Files.createDirectory(root.resolve("parent"))
        _execute(List("document-project", "scaffold", "sample", "--profile", "standard-video", "--language", "en", "--workspace", "directory", "--save", parent.toString))
        val project = parent.resolve("sample.dox")

        When("the activated video operation is requested")
        val output = _execute(List("document-project", "run", project.toString, "--operation", "video.render-review"))

        Then("the typed block names the unavailable provider without an attempt or generated output")
        output should include("outcome: blocked")
        output should include("provider-binding: cozy-video")
        output should include("missing-capability: native typed provider execution is unavailable")
        Files.exists(project.resolve("evidence/attempts"), LinkOption.NOFOLLOW_LINKS) shouldBe false
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
        val semantics = parent.resolve("sample.dox/content/presentation-semantics-en.yaml")
        val semanticsbefore = Files.readAllBytes(semantics)
        Files.writeString(marker, "preserved authored source\n", StandardCharsets.UTF_8)

        When("the same destination is requested again")
        val failure = _failure(command)

        Then("the atomic scaffold gate rejects and leaves existing authored content intact")
        failure should include("DP-SCAFFOLD-001")
        Files.readString(marker, StandardCharsets.UTF_8) shouldBe "preserved authored source\n"
        Files.readAllBytes(semantics) shouldBe semanticsbefore
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
        _activate_optional_work_products(bok, "bok", Vector("slide-review-html", "explanation-structure-review-html"))
        _activate_optional_work_products(hidden, "simplemodeling-org-video", Vector("video-logical-chart-html"))

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
        _failure(List("document-project", "verify", hidden.toString)) should include("DP-SEM-")
        CozyHelpText._text should not include("simplemodeling-org")
      }
    }

    "derive current sidecar evidence and a safe public-source dashboard projection" in {
      _with_temp_dir("cozy-document-project-sidecar-current") { root =>
        Given("a scaffolded project and direct public media mapping with the sidecar review fixed to none")
        val project = _scaffolded_project(root, "sidecar-current")
        val core = "content/core-en.yaml"
        Files.writeString(project.resolve(core), _core_yaml("sidecar-current", "en", "accepted Core"), StandardCharsets.UTF_8)
        _write_sidecar(
          project,
          Map(
            "content-core" -> _file_evidence(project, core, "source"),
            "article-source" -> _file_evidence(project, "index.dox", "source"),
            "visual-pages" -> _file_evidence(project, "presentation/visual-pages.yaml", "source"),
            "infographic-svg" -> _file_evidence(project, "infographic/infographic.svg", "source")
          )
        )

        When("inspect and dashboard consume the one evidence-derived model")
        _execute(List("document-project", "inspect", project.toString))
        _execute(List("document-project", "dashboard", project.toString))

        Then("state records sidecar identity, current declared evidence, and pending sidecar review")
        val state = Files.readString(project.resolve("target/document-project/state.yaml"), StandardCharsets.UTF_8)
        state should include("evidence:")
        state should include("path: evidence/document-project.yaml")
        state should include("article-source")
        state should include("review: pending")

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
        val standalonecore = standalone.resolve("content/core-en.yaml")
        val standalonecorebefore = Files.readAllBytes(standalonecore)

        Then("the fixtures retain their selected profile and workspace without creating external or Article-8 state")
        Files.readString(standalone.resolve("document-project.yaml"), StandardCharsets.UTF_8) should include("profile: standard-video")
        Files.readString(standalone.resolve("document-project.yaml"), StandardCharsets.UTF_8) should include("kind: directory")
        Files.readString(bok.resolve("document-project.yaml"), StandardCharsets.UTF_8) should include("profile: bok")
        Files.readString(bok.resolve("document-project.yaml"), StandardCharsets.UTF_8) should include("kind: bok")
        Files.exists(standalone.resolve("target"), LinkOption.NOFOLLOW_LINKS) shouldBe false
        Files.exists(bok.resolve("target"), LinkOption.NOFOLLOW_LINKS) shouldBe false

        When("plan resolves the bounded local drivers while strict verify rejects incomplete semantics")
        val standaloneplan = _execute(List("document-project", "plan", standalone.toString))
        val bokplan = _execute(List("document-project", "plan", bok.toString))
        val standaloneverify = _failure(List("document-project", "verify", standalone.toString))
        val bokverify = _failure(List("document-project", "verify", bok.toString))

        Then("each Plan resolves its selected profile while strict verify rejects only its local incomplete semantics")
        standaloneplan should include("eligible: operation video.render-review [provider: cozy-video]")
        bokplan should include("eligible: operation article.render-pdf [provider: smartdox-rendering]")
        bokplan should include("profile-disabled: work-product video-review [review-projection, disabled: profile bok disables video branch]")
        standaloneverify should include("DP-SEM-")
        bokverify should include("DP-SEM-")
        Files.exists(standalone.resolve("target/document-project/state.yaml"), LinkOption.NOFOLLOW_LINKS) shouldBe false
        Files.exists(bok.resolve("target/document-project/state.yaml"), LinkOption.NOFOLLOW_LINKS) shouldBe false

        When("the selected local operations without native providers are requested")
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
        _activate_optional_work_products(standalone, "standard-video", Vector.empty)
        _activate_optional_work_products(bok, "bok", Vector.empty)
        val standaloneoutputs = standaloneoperations.map(operation => _execute(List("document-project", "run", standalone.toString, "--operation", operation)))
        val bokoutputs = bokoperations.map(operation => _execute(List("document-project", "run", bok.toString, "--operation", operation)))
        val bokvideofailure = _failure(List("document-project", "run", bok.toString, "--operation", "video.render-review"))

        Then("every unavailable selected dispatch is explicitly blocked and the disabled BoK video operation remains rejected")
        standaloneoperations.zip(standaloneoutputs).foreach { case (operation, output) =>
          output should include(s"operation: $operation")
          output should include("outcome: blocked")
          output should include("missing-capability: native typed provider execution is unavailable")
        }
        bokoperations.zip(bokoutputs).foreach { case (operation, output) =>
          output should include(s"operation: $operation")
          output should include("outcome: blocked")
          output should include("missing-capability: native typed provider execution is unavailable")
        }
        bokvideofailure should include("DP-OP-001")
        bokvideofailure should include("disabled for profile bok")
        Files.exists(standalone.resolve("evidence/attempts"), LinkOption.NOFOLLOW_LINKS) shouldBe false
        Files.exists(bok.resolve("evidence/attempts"), LinkOption.NOFOLLOW_LINKS) shouldBe false

        When("the standalone Core review is generated")
        _execute(List("document-project", "review", standalone.toString, "--kind", "core"))

        Then("the standalone Core review leaves Core authority unchanged")
        Files.readAllBytes(standalonecore) shouldBe standalonecorebefore

        Given("current source evidence for both local fixtures with sidecar reviews fixed to none")
        val standalonecorepath = "content/core-en.yaml"
        val standaloneevidence = Map(
          "content-core" -> _file_evidence(standalone, standalonecorepath, "source"),
          "article-source" -> _file_evidence(standalone, "index.dox", "source"),
          "visual-pages" -> _file_evidence(standalone, "presentation/visual-pages.yaml", "source"),
          "infographic-svg" -> _file_evidence(standalone, "infographic/infographic.svg", "source")
        )
        _write_sidecar(
          standalone,
          standaloneevidence,
          "standard-video"
        )
        Files.writeString(standalone.resolve("video/storyboard.md"), _valid_storyboard, StandardCharsets.UTF_8)
        _write_sidecar(
          bok,
          Map(
            "content-core" -> _file_evidence(bok, "content/core-en.yaml", "source"),
            "article-source" -> _file_evidence(bok, "index.dox", "source"),
            "visual-pages" -> _file_evidence(bok, "presentation/visual-pages.yaml", "source"),
            "infographic-svg" -> _file_evidence(bok, "infographic/infographic.svg", "source")
          ),
          "bok"
        )

        When("inspect derives each fixture's current sidecar-bound state")
        _execute(List("document-project", "inspect", standalone.toString))
        _execute(List("document-project", "inspect", bok.toString))

        Then("both fixtures retain evidence-derived state without a sidecar dialogue alternative")
        val standalonestate = Files.readString(standalone.resolve("target/document-project/state.yaml"), StandardCharsets.UTF_8)
        val bokstate = Files.readString(bok.resolve("target/document-project/state.yaml"), StandardCharsets.UTF_8)
        standalonestate should include("review: pending")
        standalonestate should include("path: evidence/document-project.yaml")
        bokstate should include("path: evidence/document-project.yaml")
        standalonestate.linesIterator.filter(line => line.nonEmpty && !line.startsWith(" ")).map(_.takeWhile(_ != ':')).toVector shouldBe Vector(
          "schema", "project", "profile", "workspace", "sources", "evidence", "criteria", "workProducts"
        )
        standalonestate should include("criteria:\n  satisfied: 3\n  total: 20")
        bokstate should include("criteria:\n  satisfied: 3\n  total: 16")
        bokstate should include("notApplicable:\n    - id: video-storyboard-authored\n      reason: \"profile bok disables video branch\"")

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
        standalonedashboard should include("6/20 applicable criteria satisfied")
        bokdashboard should include("4/16 applicable criteria satisfied")
        standalonedashboard should include("video-review<br/><span>Video review HTML</span></th><td>required</td><td>review-projection</td><td>required")
        standalonedashboard should include("video-deliverable<br/><span>Video deliverable</span></th><td>required</td><td>deliverable</td><td>required")
        bokdashboard should include("video-review<br/><span>Video review HTML</span></th><td>profile-disabled</td><td>review-projection</td><td>disabled")
        bokdashboard should include("video-deliverable<br/><span>Video deliverable</span></th><td>profile-disabled</td><td>deliverable</td><td>disabled")
        bokdashboard should include("video-logical-chart-html<br/><span>Video Logical Chart HTML</span></th><td>profile-disabled</td><td>review-projection</td><td>disabled")
        bokdashboard should include("profile bok disables video branch")
        bokdashboard should not include("<th scope=\"row\">Video Review</th>")
        bokdashboard should not include("video-review.html")
        Vector(standalonedashboard, bokdashboard).foreach { dashboard =>
          dashboard should include("article-pdf<br/><span>Article PDF</span></th><td>required</td><td>deliverable</td><td>required")
          dashboard should include("summary-slides-pdf<br/><span>Summary slides PDF</span></th><td>active-optional</td><td>deliverable</td><td>optional")
          dashboard should include("infographic-png<br/><span>Infographic PNG</span></th><td>active-optional</td><td>deliverable</td><td>optional")
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
        state should include("id: infographic-svg\n    role: authority\n    disposition: required\n    selection: required\n    criterion: infographic-svg-authored\n    coverage: missing\n    currentness: stale")
        state should include("id: article-source\n    role: authority\n    disposition: required\n    selection: required\n    criterion: article-source-authored\n    coverage: missing\n    currentness: stale")
        state should include("id: article-source\n    role: authority\n    disposition: required\n    selection: required\n    criterion: article-source-authored\n    coverage: missing\n    currentness: stale\n    review: pending\n    readiness: blocked\n    reason: \"a declared dependency is stale\"")
        state should include("id: article-pdf\n    role: deliverable\n    disposition: required\n    selection: required\n    criterion: article-pdf-rendered\n    coverage: missing\n    currentness: stale")
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
        state should include("id: article-source\n    role: authority\n    disposition: required\n    selection: required\n    criterion: article-source-authored\n    coverage: missing\n    currentness: stale")
        state should include("id: article-pdf\n    role: deliverable\n    disposition: required\n    selection: required\n    criterion: article-pdf-rendered\n    coverage: missing\n    currentness: stale")
        state should not include("id: article-pdf\n    role: deliverable\n    disposition: required\n    selection: required\n    criterion: article-pdf-rendered\n    coverage: missing\n    currentness: failed")
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

    "derive v2 no-sidecar state without a retired evidence fallback" in {
      _with_temp_dir("cozy-document-project-sidecar-legacy") { root =>
        Given("a scaffolded project with no evidence sidecar")
        val project = _scaffolded_project(root, "sidecar-legacy")

        When("inspect and dashboard are requested")
        _execute(List("document-project", "inspect", project.toString))
        _execute(List("document-project", "dashboard", project.toString))

        Then("the cache records no sidecar and inactive optional Work Products remain nonparticipating")
        Files.readString(project.resolve("target/document-project/state.yaml"), StandardCharsets.UTF_8) should include("sidecar: none")
        val dashboard = Files.readString(project.resolve("target/document-project/project-dashboard.html"), StandardCharsets.UTF_8)
        dashboard should include("core-review-html</th><td>not-applicable</td><td>nonparticipating</td><td>not-applicable</td><td>not-selected")
        dashboard should not include("Safe public source")
      }
    }

    "derive closed private content alignment without changing retained v2 evidence or state contracts" in {
      _with_temp_dir("cozy-document-project-content-alignment") { root =>
        Given("Japanese and English v2 packages with distinct locale expression bytes and one shared semantic scope")
        val japanese = _alignment_project(root, "alignment-ja", "ja", "en")
        val english = _alignment_project(root, "alignment-en", "en", "ja")
        Files.writeString(english.resolve("index.dox"), "# English article\n", StandardCharsets.UTF_8)
        _write_alignment(english, "standard-video", "en", "ja")

        When("each private alignment ledger is evaluated without executing a provider")
        val japanesesnapshot = _alignment_snapshot(japanese)
        val englishsnapshot = _alignment_snapshot(english)

        Then("both independently authored locale artifacts are current after human acceptance and accepted local parity")
        japanesesnapshot.artifacts.map(_.currentness).distinct shouldBe Vector("current")
        englishsnapshot.artifacts.map(_.currentness).distinct shouldBe Vector("current")
        japanesesnapshot.parity shouldBe CozyDocumentProjectAlignment.ParityStatus("accepted", "current", "declared locale parity is accepted; locale text is independently authored")
        Files.readString(japanese.resolve("target/alignment/article-html.out"), StandardCharsets.UTF_8) should not equal Files.readString(english.resolve("target/alignment/article-html.out"), StandardCharsets.UTF_8)
        Files.readString(japanese.resolve("index.dox"), StandardCharsets.UTF_8) should not equal Files.readString(english.resolve("index.dox"), StandardCharsets.UTF_8)

        And("each accepted artifact binds its expected logical provider, renderer, output receipt, and visible shared infographic proof")
        japanesesnapshot.artifacts.map(_.artifact.kind) shouldBe Vector("article-html", "article-pdf", "summary-slides-pdf", "infographic-png", "video-deliverable")
        japanesesnapshot.artifacts.foreach { status =>
          status.artifact.review.decision shouldBe "accepted"
          status.artifact.review.reviewer shouldBe "human-reviewer"
          status.artifact.authority.path should not be empty
          status.artifact.output.path should not be empty
          status.artifact.receipt.path should not be empty
          status.visualusecurrentness shouldBe "current"
        }
        japanesesnapshot.artifacts.map(_.artifact.provider.id) shouldBe Vector("cozy-site", "smartdox-rendering", "cozy-visual-page", "cozy-infographic", "cozy-video")
        japanesesnapshot.artifacts.map(_.artifact.renderer) shouldBe Vector("smartdox-site", "smartdox-pdf", "cozy-pdf", "cozy-png", "cozy-video")

        Given("a preserved v2 sidecar and state snapshot")
        _write_sidecar(japanese, profileid = "standard-video")
        _execute(List("document-project", "inspect", japanese.toString))
        val statebefore = Files.readAllBytes(japanese.resolve("target/document-project/state.yaml"))
        val sidecarbefore = Files.readAllBytes(japanese.resolve("evidence/document-project.yaml"))
        val mediabefore = Files.readAllBytes(japanese.resolve("media/article-media.yaml"))

        When("the existing dashboard displays the alignment section")
        _execute(List("document-project", "dashboard", japanese.toString))
        val dashboard = Files.readString(japanese.resolve("target/document-project/project-dashboard.html"), StandardCharsets.UTF_8)

        Then("the Dashboard is deterministic and read-only for the retained contracts")
        dashboard should include("Content alignment")
        dashboard should include("Shared-infographic visual use")
        dashboard should include("Locale parity")
        dashboard should not include("evidence/content-alignment.yaml")
        Files.readAllBytes(japanese.resolve("target/document-project/state.yaml")) shouldBe statebefore
        Files.readAllBytes(japanese.resolve("evidence/document-project.yaml")) shouldBe sidecarbefore
        Files.readAllBytes(japanese.resolve("media/article-media.yaml")) shouldBe mediabefore
      }
    }

    "derive each alignment stale reason and preserve non-acceptance decisions" in {
      _with_temp_dir("cozy-document-project-content-alignment-stale") { root =>
        Given("one current accepted alignment fixture for each independently changed identity")
        val changes = Vector[(String, Path => Unit, String, Int)](
          ("Content Core", project => Files.writeString(project.resolve("content/core-ja.yaml"), Files.readString(project.resolve("content/core-ja.yaml"), StandardCharsets.UTF_8).replace("accepted ja Core", "changed Core"), StandardCharsets.UTF_8), "Content Core SHA-256 changed", 0),
          ("locale authority", project => Files.writeString(project.resolve("index.dox"), "# changed authority\n", StandardCharsets.UTF_8), "locale authority SHA-256 changed", 0),
          ("shared SVG", project => Files.writeString(project.resolve("infographic/infographic.svg"), "<svg>changed</svg>\n", StandardCharsets.UTF_8), "shared infographic SHA-256 changed", -1),
          ("output", project => Files.writeString(project.resolve("target/alignment/article-html.out"), "changed output\n", StandardCharsets.UTF_8), "output SHA-256 changed", 0),
          ("receipt", project => Files.writeString(project.resolve("evidence/receipts/article-html.yaml"), "changed receipt\n", StandardCharsets.UTF_8), "receipt SHA-256 changed", 0),
          ("provider", project => _replace_alignment(project, "id: cozy-site", "id: changed-provider"), "provider changed", 0),
          ("renderer", project => _replace_alignment(project, "renderer: smartdox-site", "renderer: changed-renderer"), "renderer changed", 0),
          ("profile", project => Files.writeString(project.resolve("document-project.yaml"), Files.readString(project.resolve("document-project.yaml"), StandardCharsets.UTF_8).replace("profile: standard-video", "profile: bok-video"), StandardCharsets.UTF_8), "profile changed", 0)
        )

        changes.zipWithIndex.foreach { case ((label, change, reason, index), ordinal) =>
          val project = _alignment_project(root, s"alignment-stale-$ordinal", "ja", "en")
          change(project)

          When(s"the $label identity changes after the human decision")
          val snapshot = _alignment_snapshot(project)

          Then("the affected accepted consumer is stale with its precise reason")
          if (index < 0) {
            snapshot.artifacts.map(_.currentness).distinct shouldBe Vector("stale")
            snapshot.artifacts.map(_.reason).distinct shouldBe Vector(reason)
          } else {
            snapshot.artifacts(index).currentness shouldBe "stale"
            snapshot.artifacts(index).reason shouldBe reason
          }
        }

        Given("one current accepted alignment fixture whose recorded output has been deleted")
        val missingoutput = _alignment_project(root, "alignment-missing-output", "ja", "en")
        Files.delete(missingoutput.resolve("target/alignment/article-html.out"))

        When("the alignment snapshot reads the deleted recorded output")
        val missingsnapshot = _alignment_snapshot(missingoutput)

        Then("the first accepted artifact is missing with the exact missing-output reason")
        missingsnapshot.artifacts.head.currentness shouldBe "missing"
        missingsnapshot.artifacts.head.reason shouldBe "output is missing"

        Given("a current accepted record whose human review requests changes, rejects, or leaves parity pending")
        val decisions = _alignment_project(root, "alignment-decisions", "ja", "en")
        _replace_alignment(decisions, "decision: accepted", "decision: changes-requested")

        When("the dashboard model reads the changes-requested review")
        val requested = _alignment_snapshot(decisions)

        Then("the human result is retained and never promoted to acceptance")
        requested.artifacts.head.currentness shouldBe "not-accepted"
        requested.artifacts.head.reason shouldBe "human review decision is changes-requested"

        When("the same review is changed to rejected")
        _replace_alignment(decisions, "decision: changes-requested", "decision: rejected")
        val rejected = _alignment_snapshot(decisions)

        Then("rejection remains visible rather than current")
        rejected.artifacts.head.currentness shouldBe "not-accepted"
        rejected.artifacts.head.reason shouldBe "human review decision is rejected"

        Given("a declared local peer with pending parity")
        val pending = _alignment_project(root, "alignment-pending", "ja", "en")
        _replace_alignment(pending, "  decision: accepted\n  peers:", "  decision: pending\n  peers:")

        When("the pending alignment is read")
        val pendingsnapshot = _alignment_snapshot(pending)

        Then("parity remains visibly pending without discovering another project")
        pendingsnapshot.parity shouldBe CozyDocumentProjectAlignment.ParityStatus("pending", "pending", "declared locale parity is pending")
      }
    }

    "reject unsafe alignment grammar while keeping a v2 Article 8-shaped package pending and source-safe" in {
      _with_temp_dir("cozy-document-project-content-alignment-invalid") { root =>
        Given("a current private alignment ledger and a v2 Article 8-shaped package with no accepted Core")
        val malformed = _alignment_project(root, "alignment-invalid", "ja", "en")
        val articleeightparent = Files.createDirectory(root.resolve("article-eight-parent"))
        _execute(List("document-project", "scaffold", "domain-modeling", "--profile", "standard-video", "--language", "ja", "--workspace", "bok", "--save", articleeightparent.toString))
        val articleeight = articleeightparent.resolve("domain-modeling.dox")
        val scaffoldedarticleeightdescriptor = articleeight.resolve("document-project.yaml")
        Files.writeString(scaffoldedarticleeightdescriptor, Files.readString(scaffoldedarticleeightdescriptor, StandardCharsets.UTF_8).replace("profile: standard-video", "profile: simplemodeling-org-video"), StandardCharsets.UTF_8)
        val articleeightdescriptor = Files.readString(articleeight.resolve("document-project.yaml"), StandardCharsets.UTF_8)
        val articleseven = root.resolve("article-seven.dox")
        Files.writeString(articleseven, "# Article 7 remains a legacy single-file source\n", StandardCharsets.UTF_8)
        val articlesevenbefore = Files.readAllBytes(articleseven)

        When("an extra field, escaped output, or symlinked evidence is admitted")
        _replace_alignment(malformed, "parity:\n", "extra: value\nparity:\n")
        val extrafailure = _failure(List("document-project", "dashboard", malformed.toString))
        _write_alignment(malformed, "standard-video", "ja", "en")
        _replace_alignment(malformed, "path: target/alignment/article-html.out", "path: ../outside.out")
        val escapefailure = _failure(List("document-project", "dashboard", malformed.toString))
        _write_alignment(malformed, "standard-video", "ja", "en")
        val visual = malformed.resolve("evidence/visual-review/article-html.yaml")
        val visualtarget = malformed.resolve("evidence/visual-review/article-html-real.yaml")
        Files.move(visual, visualtarget)
        Files.createSymbolicLink(visual, visualtarget)
        val symlinkfailure = _failure(List("document-project", "dashboard", malformed.toString))

        Then("invalid paths and closed grammar are rejected before they can become accepted")
        extrafailure should include("DP-DESC-002")
        escapefailure should include("DP-PATH-001")
        symlinkfailure should include("DP-PATH-001")

        Given("a valid generated alignment ledger with canonical FileIdentity records")
        val reordered = _alignment_project(root, "alignment-reordered", "ja", "en")

        When("the public Dashboard admits a reordered top-level FileIdentity")
        _replace_alignment(
          reordered,
          "  path: content/core-ja.yaml\n  identity: alignment-reordered:core:ja",
          "  identity: alignment-reordered:core:ja\n  path: content/core-ja.yaml"
        )
        val topidentityfailure = _failure(List("document-project", "dashboard", reordered.toString))

        Then("Dashboard rejects the reordered top-level identity before it can become accepted or current")
        topidentityfailure should include("DP-DESC-002")

        Given("the same valid generated ledger before a nested artifact identity is reordered")
        _write_alignment(reordered, "standard-video", "ja", "en")

        When("the public Dashboard admits a reordered nested artifact FileIdentity")
        _replace_alignment(
          reordered,
          "      path: index.dox\n      identity: alignment-reordered:article-html:authority",
          "      identity: alignment-reordered:article-html:authority\n      path: index.dox"
        )
        val nestedidentityfailure = _failure(List("document-project", "dashboard", reordered.toString))

        Then("Dashboard rejects the reordered nested identity before it can become accepted or current")
        nestedidentityfailure should include("DP-DESC-002")

        Given("the same valid generated ledger before a flow-mapping Content Core identity reorders its decoded fields")
        _write_alignment(reordered, "standard-video", "ja", "en")

        When("the public Dashboard admits the flow-mapping Content Core identity")
        _replace_alignment(
          reordered,
          s"contentCore:\n${_indent(_alignment_identity(reordered, "content/core-ja.yaml", "alignment-reordered:core:ja"), 2)}",
          s"contentCore: {identity: alignment-reordered:core:ja, path: content/core-ja.yaml, sha256: ${_sha256(reordered.resolve("content/core-ja.yaml"))}}"
        )
        val flowcorefailure = _failure(List("document-project", "dashboard", reordered.toString))

        Then("Dashboard rejects the reordered flow-mapping Content Core identity with the established descriptor diagnostic")
        _diagnostic_tokens(flowcorefailure) shouldBe Vector("DP-DESC-002")

        Given("the same valid generated ledger before a flow-mapping shared infographic identity reorders its decoded fields")
        _write_alignment(reordered, "standard-video", "ja", "en")

        When("the public Dashboard admits the flow-mapping shared infographic identity")
        _replace_alignment(
          reordered,
          s"sharedInfographic:\n${_indent(_alignment_identity(reordered, "infographic/infographic.svg", "alignment-reordered:infographic"), 2)}",
          s"sharedInfographic: {identity: alignment-reordered:infographic, path: infographic/infographic.svg, sha256: ${_sha256(reordered.resolve("infographic/infographic.svg"))}}"
        )
        val flowinfographicfailure = _failure(List("document-project", "dashboard", reordered.toString))

        Then("Dashboard rejects the reordered flow-mapping shared infographic identity with the established descriptor diagnostic")
        _diagnostic_tokens(flowinfographicfailure) shouldBe Vector("DP-DESC-002")

        Given("the same valid generated ledger before a nested FileIdentity quotes and reorders its keys")
        _write_alignment(reordered, "standard-video", "ja", "en")

        When("the public Dashboard admits the quoted reordered nested artifact identity")
        _replace_alignment(
          reordered,
          s"""    authority:
${_indent(_alignment_identity(reordered, "index.dox", "alignment-reordered:article-html:authority"), 6)}""",
          s"""    authority:
      "identity": alignment-reordered:article-html:authority
      "path": index.dox
      "sha256": ${_sha256(reordered.resolve("index.dox"))}"""
        )
        val quotednestedfailure = _failure(List("document-project", "dashboard", reordered.toString))

        Then("Dashboard rejects the quoted reordered nested identity with the established descriptor diagnostic")
        _diagnostic_tokens(quotednestedfailure) shouldBe Vector("DP-DESC-002")

        When("the empty-Core Article 8-shaped package is projected without a private ledger")
        val descriptor = CozyDocumentProject._load_project(articleeight)
        val alignment = CozyDocumentProjectAlignment.dashboardHtml(articleeight, descriptor)
        _write_sidecar(articleeight, profileid = "simplemodeling-org-video")
        val sourceprojection = CozyDocumentProjectEvidence.snapshot(articleeight, CozyDocumentProject._load_project(articleeight)).sidecar.map(_.publicSource)

        Then("only its normal index.dox is the possible public source, alignment is pending, and Article 7 remains untouched")
        articleeightdescriptor should include("schema: cozy.document-project.v2")
        articleeightdescriptor should include("activeOptionalWorkProducts: []")
        alignment should include("pending")
        alignment should include("Accepted Content Core entries: absent")
        alignment should not include("evidence/")
        alignment should not include("target/")
        sourceprojection.map(_.path.path) shouldBe Some("index.dox")
        Files.readAllBytes(articleseven) shouldBe articlesevenbefore
      }
    }

    "publish the frozen public Document Project help forms" in {
      Given("the Cozy public help text")

      When("the Document Project command section is inspected")
      val help = CozyHelpText._text

      Then("each no-alias form and the Phase 42.1 projection boundary are described")
      help should include("document-project inspect <project>")
      help should include("document-project dashboard <project> [--save <dashboard.html>]")
      help should include("document-project review <project> --kind core|article|slides|video|slide-logical-chart|video-logical-chart [--save <review.html>]")
      help should include("document-project review <project> --kind presentation [--save <confirmation.html>]")
      help should include("document-project content-core candidate <project> <dialogue>")
      help should include("document-project content-core feedback <project> <candidate-id> <feedback>")
      help should include("document-project content-core accept <project> <candidate-id> <acceptance>")
      help should include("document-project run <project> --operation <logical-operation> [--dry-run]")
      help should include("document-project scaffold <slug> --profile standard|standard-video|bok|bok-video --language <tag> --workspace directory|bok --save <parent>")
      help should include("Dashboard defaults to target/document-project/project-dashboard.html")
      help should include("Core review defaults to target/document-project/core-review.html")
      help should include("Article review defaults to target/document-project/article-review.html")
      help should include("Video review defaults to target/document-project/video-review.html")
      help should include("default Article and Video review also write one local disposable generated-review receipt beside the default HTML")
      help should include("never execute providers or persist authored authority, candidates, feedback, acceptance, attempts, delivery state, or external/provider receipts")
      help should include("Slide and video logical charts default to target/document-project/slide-logical-chart-review.html")
      help should include("Slide Logical Chart visualizes current Content Core and Visual Page IR")
      help should include("same-directory temporary file and atomic move")
      help should include("completed direct JSON/YAML dialogue bundle")
      help should include("never invokes an AI provider")
      help should include("append-only below evidence/content-core/")
      help should not include "reflect-feedback"
    }
    }
  }

  private val _alignment_artifacts = Vector(
    ("article-html", "index.dox", "cozy-site", "article.publish-site", "smartdox-site"),
    ("article-pdf", "index.dox", "smartdox-rendering", "article.render-pdf", "smartdox-pdf"),
    ("summary-slides-pdf", "presentation/visual-pages.yaml", "cozy-visual-page", "summary-slides.render-pdf", "cozy-pdf"),
    ("infographic-png", "infographic/infographic.svg", "cozy-infographic", "infographic.render-png", "cozy-png"),
    ("video-deliverable", "video/storyboard.md", "cozy-video", "video.render-deliverable", "cozy-video")
  )

  private def _alignment_project(root: Path, slug: String, language: String, peerlanguage: String): Path = {
    val parent = Files.createDirectory(root.resolve(s"$slug-parent"))
    _execute(List("document-project", "scaffold", slug, "--profile", "standard-video", "--language", language, "--workspace", "directory", "--save", parent.toString))
    val project = parent.resolve(s"$slug.dox")
    Files.writeString(project.resolve("document-project.yaml"), _alignment_descriptor_yaml(slug, language, peerlanguage), StandardCharsets.UTF_8)
    Files.writeString(project.resolve(s"content/core-$language.yaml"), _core_yaml(slug, language, s"accepted $language Core"), StandardCharsets.UTF_8)
    _alignment_artifacts.foreach { case (kind, _, _, _, _) =>
      val output = project.resolve(s"target/alignment/$kind.out")
      val receipt = project.resolve(s"evidence/receipts/$kind.yaml")
      val visual = project.resolve(s"evidence/visual-review/$kind.yaml")
      Files.createDirectories(output.getParent)
      Files.createDirectories(receipt.getParent)
      Files.createDirectories(visual.getParent)
      Files.writeString(output, s"$language $kind output\n", StandardCharsets.UTF_8)
      Files.writeString(receipt, s"$language $kind receipt\n", StandardCharsets.UTF_8)
      Files.writeString(visual, s"$language $kind visual proof\n", StandardCharsets.UTF_8)
    }
    _write_alignment(project, "standard-video", language, peerlanguage)
    project
  }

  private def _alignment_descriptor_yaml(slug: String, language: String, peerlanguage: String): String =
    s"""schema: cozy.document-project.v2
id: $slug
workflow:
  schema: cozy.document-workflow.v2
  id: document-production
profile: standard-video
language: $language
workspace:
  kind: directory
contentCore: content/core-$language.yaml
activeOptionalWorkProducts: []
semanticScope:
  id: $slug
  localeVariants:
    - project: $slug
      language: $language
      contentCore: $slug:core:$language
      workProducts: []
    - project: $slug-$peerlanguage
      language: $peerlanguage
      contentCore: $slug-$peerlanguage:core:$peerlanguage
      workProducts: []
"""

  private def _write_alignment(project: Path, profile: String, language: String, peerlanguage: String): Unit = {
    val ledger = project.resolve("evidence/content-alignment.yaml")
    Files.createDirectories(ledger.getParent)
    Files.writeString(ledger, _alignment_yaml(project, profile, language, peerlanguage), StandardCharsets.UTF_8)
  }

  private def _alignment_yaml(project: Path, profile: String, language: String, peerlanguage: String): String = {
    val slug = project.getFileName.toString.stripSuffix(".dox")
    val sharedidentity = s"$slug:infographic"
    val artifacts = _alignment_artifacts.map { case (kind, authoritypath, provider, operation, renderer) =>
      val outputpath = s"target/alignment/$kind.out"
      val receiptpath = s"evidence/receipts/$kind.yaml"
      val visualpath = s"evidence/visual-review/$kind.yaml"
      s"""  - kind: $kind
    locale: $language
    authority:
${_indent(_alignment_identity(project, authoritypath, s"$slug:$kind:authority"), 6)}
    output:
${_indent(_alignment_identity(project, outputpath, s"$slug:$kind:output"), 6)}
    receipt:
${_indent(_alignment_identity(project, receiptpath, s"$slug:$kind:receipt"), 6)}
    provider:
      id: $provider
      operation: $operation
      profile: $profile
    renderer: $renderer
    review:
      reviewer: human-reviewer
      decision: accepted
      rationale: accepted for local alignment
    sharedInfographicUse:
      identity: $sharedidentity
      consumer: $kind-consumer
      evidence:
${_indent(_alignment_identity(project, visualpath, s"$slug:$kind:visual"), 8)}"""
    }.mkString("\n")
    s"""schema: cozy.content-alignment.v1
project: $slug
semanticScope: $slug
locale: $language
contentCore:
${_indent(_alignment_identity(project, s"content/core-$language.yaml", s"$slug:core:$language"), 2)}
sharedInfographic:
${_indent(_alignment_identity(project, "infographic/infographic.svg", sharedidentity), 2)}
artifacts:
$artifacts
parity:
  decision: accepted
  peers:
    - locale: $peerlanguage
      contentCore: $slug-$peerlanguage:core:$peerlanguage
"""
  }

  private def _alignment_identity(project: Path, path: String, identity: String): String =
    s"path: $path\nidentity: $identity\nsha256: ${_sha256(project.resolve(path))}"

  private def _alignment_snapshot(project: Path): CozyDocumentProjectAlignment.Snapshot =
    CozyDocumentProjectAlignment.snapshot(project, CozyDocumentProject._load_project(project))

  private def _replace_alignment(project: Path, before: String, after: String): Unit = {
    val ledger = project.resolve("evidence/content-alignment.yaml")
    Files.writeString(ledger, Files.readString(ledger, StandardCharsets.UTF_8).replaceFirst(java.util.regex.Pattern.quote(before), java.util.regex.Matcher.quoteReplacement(after)), StandardCharsets.UTF_8)
  }

  private def _scaffolded_project(root: Path, slug: String): Path = {
    val parent = Files.createDirectory(root.resolve(s"$slug-parent"))
    _execute(List("document-project", "scaffold", slug, "--profile", "standard", "--language", "en", "--workspace", "directory", "--save", parent.toString))
    parent.resolve(s"$slug.dox")
  }

  private def _write_valid_presentation_semantics(project: Path, includeproblemstructure: Boolean = true): Unit = {
    val slug = project.getFileName.toString.stripSuffix(".dox")
    val core = project.resolve("content/core-en.yaml")
    val composition = _fixed_presentation_composition()
    val problemstructure = _presentation_structure_yaml("problem-structure", "problem-step", "Problem", "The prior reader path was permissive.", "Show the reader-facing problem.")
    val solutionstructure = _presentation_structure_yaml("solution-structure", "solution-step", "Solution", "The typed path preserves declared content.", "Show the reader-facing solution.")
    val structures = if (includeproblemstructure) Vector(problemstructure, solutionstructure) else Vector(solutionstructure)
    val bindings = for {
      medium <- Vector("article", "slides", "video")
      logicalpattern <- Vector("sequence", "causal-chain")
    } yield _presentation_policy_binding_yaml(medium, logicalpattern)
    val value = s"""schema: cozy.content-core.presentation-semantics.v2
id: $slug-presentation-en
contentCore:
  id: $slug:core:en
  language: en
  identity: sha256:${_sha256(core)}
composition: ${CozyExplanation.canonicalCompositionJson(composition)}
storyFlow:
  id: $slug-story-flow-en
  transitions:
    - id: problem-causes-solution
      relationType: causes
      fromStepId: problem-step
      toStepId: solution-step
structures:
${structures.map(_indent(_, 2)).mkString("\n")}
projectionPolicy:
  schema: cozy.content-core.projection-policy.v1
  id: $slug-projection-policy-en
  revision: 1
  bindings:
${bindings.map(_indent(_, 4)).mkString("\n")}
"""
    Files.writeString(project.resolve("content/presentation-semantics-en.yaml"), value, StandardCharsets.UTF_8)
  }

  private def _fixed_presentation_composition(): CozyExplanation.Composition = {
    val catalog = CozyExplanation.fixedCatalog
    val none = Vector.empty[String]
    val facts = Vector(
      CozyExplanation.Fact("name", CozyExplanation.JsonString("Cozy"), none, none),
      CozyExplanation.Fact("vision", CozyExplanation.JsonString("Make presentation semantics explicit"), none, none),
      CozyExplanation.Fact("goals", CozyExplanation.JsonArray(Vector(_labeled_value("goal", "Reliable plans"))), none, none),
      CozyExplanation.Fact("context", CozyExplanation.JsonString("Typed document workflow"), none, none),
      CozyExplanation.Fact("useCases", CozyExplanation.JsonArray(Vector(_labeled_value("use-case", "Explain document semantics"))), none, none),
      CozyExplanation.Fact("mainScenario", CozyExplanation.JsonObject(Vector(
        "id" -> CozyExplanation.JsonString("scenario"),
        "label" -> CozyExplanation.JsonString("Normalize a document"),
        "steps" -> CozyExplanation.JsonArray(Vector(_labeled_value("scenario-step", "Validate semantics")))
      )), none, none),
      CozyExplanation.Fact("mechanisms", CozyExplanation.JsonArray(Vector(_labeled_value("mechanism", "Typed validation"))), none, none)
    )
    val problem = CozyExplanation.CompositionStep(
      "problem-step", 1, "problem",
      Vector(CozyExplanation.Claim("problem-claim", "The prior reader path was permissive.", "primary", none, none)),
      _presentation_sequence(), none, none, Vector(CozyExplanation.ParameterSelection("problem"))
    )
    val solution = CozyExplanation.CompositionStep(
      "solution-step", 2, "solution",
      Vector(CozyExplanation.Claim("solution-claim", "The typed path preserves declared content.", "primary", none, none)),
      _presentation_causal_chain(), none, none, Vector(CozyExplanation.ParameterSelection("solution"))
    )
    CozyExplanation.Composition(
      "document-composition",
      CozyExplanation.CatalogSelector(catalog.catalog.id, catalog.catalog.revision, catalog.identity),
      CozyExplanation.Subject(CozyExplanation.PatternReference("software-product", 1), facts),
      CozyExplanation.Explanation(
        CozyExplanation.PatternReference("problem-solution", 1),
        Vector(
          CozyExplanation.Parameter("problem", CozyExplanation.JsonString("The reader-facing path was permissive.")),
          CozyExplanation.Parameter("solution", CozyExplanation.JsonString("The sibling schema is typed and closed."))
        ),
        Vector(problem, solution)
      ),
      Vector.empty,
      Vector.empty
    )
  }

  private def _labeled_value(id: String, label: String): CozyExplanation.JsonValue = CozyExplanation.JsonObject(Vector(
    "id" -> CozyExplanation.JsonString(id),
    "label" -> CozyExplanation.JsonString(label),
    "sourceRefs" -> CozyExplanation.JsonArray(Vector.empty),
    "assetRefs" -> CozyExplanation.JsonArray(Vector.empty)
  ))

  private def _presentation_sequence(): CozyVisualPage.Logical = CozyVisualPage.Logical(
    "sequence",
    Vector(
      CozyVisualPage.Node("first", "step", "Reader input", Vector.empty),
      CozyVisualPage.Node("second", "step", "Typed boundary", Vector.empty)
    ),
    Vector(CozyVisualPage.Relation("next", "next", "first", "second", Vector.empty))
  )

  private def _presentation_causal_chain(): CozyVisualPage.Logical = CozyVisualPage.Logical(
    "causal-chain",
    Vector(
      CozyVisualPage.Node("cause", "cause", "Closed schema", Vector.empty),
      CozyVisualPage.Node("effect", "effect", "Faithful projection", Vector.empty)
    ),
    Vector(
      CozyVisualPage.Relation("causes", "causes", "cause", "effect", Vector.empty),
      CozyVisualPage.Relation("enables", "enables", "cause", "effect", Vector.empty)
    )
  )

  private def _presentation_structure_yaml(id: String, stepid: String, heading: String, text: String, intent: String): String =
    s"""- id: $id
  storyStepId: $stepid
  article:
    articleHeading: "$heading"
    visibleText:
      - "$text"
    visualIntent: "$intent"
  visualOverrides: []"""

  private def _presentation_policy_binding_yaml(medium: String, logicalpattern: String): String =
    s"""- medium: $medium
  logicalPattern: $logicalpattern
  visual:
    pattern: flow-horizontal
    parameters:
      showRelationLabels: true"""

  private def _write_sidecar(project: Path, evidence: Map[String, String] = Map.empty, profileid: String = "standard", activeoptionalworkproducts: Vector[String] = Vector.empty): Unit = {
    val selected = _activate_optional_work_products(project, profileid, activeoptionalworkproducts)
    val media = project.resolve("media/article-media.yaml")
    Files.createDirectories(media.getParent)
    Files.writeString(media, "schema: cozy.media.v1\narticleMedia:\n  articleIdentity: public-article\n", StandardCharsets.UTF_8)
    val products = _resolved(profileid, selected).workProducts.filter(_.isParticipating).map { value =>
      val id = value.workProduct.id
      val itemevidence = evidence.getOrElse(id, "kind: none")
      s"  - id: $id\n    evidence:\n${_indent(itemevidence, 6)}\n    review:\n      kind: none"
    }
    val sidecar = project.resolve("evidence/document-project.yaml")
    Files.createDirectories(sidecar.getParent)
    Files.writeString(
      sidecar,
      s"schema: cozy.document-project-evidence.v2\nproject: ${project.getFileName.toString.stripSuffix(".dox")}\npublicSource:\n  kind: smartdox\n  identity: public-article\n  path: index.dox\n  sha256: ${_sha256(project.resolve("index.dox"))}\n  mediaDescriptor: media/article-media.yaml\nproducts:\n${products.mkString("\n")}\n",
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

  private def _reorder_attempt_top_level(value: String): String = {
    val lines = value.linesIterator.toVector
    (lines.slice(1, 2) ++ lines.slice(0, 1) ++ lines.drop(2)).mkString("\n") + "\n"
  }

  private def _failed_attempt_yaml(project: Path, attemptid: String, operation: String, provider: String, profile: String): String = {
    val descriptor = CozyDocumentProject.Descriptor(
      project.getFileName.toString.stripSuffix(".dox"),
      profile,
      "en",
      "directory",
      "content/core-en.yaml",
      Vector.empty,
      CozyDocumentProject.SemanticScope(project.getFileName.toString.stripSuffix(".dox"), Vector(CozyDocumentProject.LocaleVariant(project.getFileName.toString.stripSuffix(".dox"), "en", s"${project.getFileName.toString.stripSuffix(".dox")}:core:en", Vector.empty)))
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

  private def _accepted_candidate_core_yaml(slug: String, language: String, entryid: String, text: String): String =
    s"""schema: "cozy.content-core.v1"
id: "$slug:core:$language"
language: "$language"
accepted:
  - id: "$entryid"
    text: "$text"
"""

  private def _succeeded_dialogue_json(candidateid: String, text: String, supersedes: Option[String] = None): String = {
    val supersedesfield = supersedes.map(value => ",\n  \"supersedes\": \"" + value + "\"").getOrElse("")
    s"""{
  "schema": "cozy.content-core-dialogue.v1",
  "id": "$candidateid",
  "source": "source document",
  "idea": "content-core idea",
  "provider": "provider-local",
  "model": "model-local",
  "request": "completed provider request",
  "response": "completed provider response",
  "outcome": "succeeded",
  "diagnostics": [],
  "candidate": {"accepted": [{"id": "accepted-$candidateid", "text": "$text"}]}$supersedesfield
}
"""
  }

  private def _failed_dialogue_yaml(attemptid: String): String =
    s"""schema: cozy.content-core-dialogue.v1
id: $attemptid
source: source document
idea: content-core idea
provider: provider-local
model: model-local
request: completed provider request
response: provider declined the draft
outcome: failed
diagnostics:
  - provider declined the draft
"""

  private def _feedback_json(feedbackid: String, candidateid: String, decision: String): String = {
    val payload = decision match {
      case "changes-requested" => "\"feedback\": \"please revise the Core\""
      case "rejected" => "\"rejectionReason\": \"human reviewer rejected the Core\""
      case _ => throw new IllegalArgumentException(s"unsupported feedback fixture decision: $decision")
    }
    s"""{
  "schema": "cozy.content-core-feedback.v1",
  "id": "$feedbackid",
  "candidate": "$candidateid",
  "reviewer": "human-reviewer",
  "decision": "$decision",
  $payload
}
"""
  }

  private def _acceptance_json(acceptanceid: String, candidateid: String): String =
    s"""{
  "schema": "cozy.content-core-acceptance.v1",
  "id": "$acceptanceid",
  "candidate": "$candidateid",
  "reviewer": "human-reviewer",
  "decision": "accepted"
}
"""

  private def _acceptance_record_yaml(
    project: Path,
    acceptanceid: String,
    candidateid: String,
    reviewer: String,
    corepath: String,
    prior: String,
    resulting: String
  ): String =
    s"""schema: cozy.content-core-acceptance-record.v1
id: \"$acceptanceid\"
candidate:
  id: \"$candidateid\"
  path: \"evidence/content-core/candidates/$candidateid.yaml\"
  sha256: ${_sha256(project.resolve(s"evidence/content-core/candidates/$candidateid.yaml"))}
reviewer:
  id: \"$reviewer\"
  sha256: ${_sha256_string(reviewer)}
decision:
  value: accepted
  sha256: ${_sha256_string("accepted")}
priorCore:
  path: \"$corepath\"
  sha256: $prior
resultingCore:
  path: \"$corepath\"
  sha256: $resulting
"""

  private def _valid_storyboard: String =
    """# Storyboard
      |schema: "cozy.video.storyboard.v1"
      |version: 1
      |
      |## scene
      |id: "opening"
      |order: 1
      |section: "introduction"
      |speaker: "narrator"
      |role: "narration"
      |narration: |
      |  Welcome to Cozy.
      |screen:
      |  heading: "Welcome"
      |  content: |
      |    Cozy overview
      |caption: "Welcome"
      |duration: 3.5s
      |lead-silence: 0.25s
      |transition: "fade"
      |production-inserts: [{"id":"title-card","kind":"overlay","value":"Welcome"}]
      |diagram-refs: ["assets/overview.svg"]
      |asset-refs: ["assets/opening.png"]
      |pronunciation-notes: [{"surface":"Cozy","reading":"コージー"}]
      |direction: |
      |  Fade in the title
      |""".stripMargin

  private def _valid_storyboard_with_infographic: String =
    _valid_storyboard.replace(
      "asset-refs: [\"assets/opening.png\"]",
      "asset-refs: [\"assets/opening.png\", \"infographic/infographic.svg\"]"
    )

  private def _indent(value: String, spaces: Int): String =
    value.linesIterator.map(line => (" " * spaces) + line).mkString("\n")

  private def _sha256(path: Path): String =
    MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path)).map(value => f"${value & 0xff}%02x").mkString

  private def _sha256_string(value: String): String =
    MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)).map(item => f"${item & 0xff}%02x").mkString

  private def _resolved(profile: String, activeoptionalworkproducts: Vector[String] = Vector.empty): CozyDocumentWorkflow.ResolvedWorkflow =
    CozyDocumentWorkflow.resolve(profile, activeoptionalworkproducts) match {
      case Right(value) => value
      case Left(cause) => throw new RuntimeException(cause)
    }

  private def _activate_optional_work_products(project: Path, profile: String, requested: Vector[String]): Vector[String] = {
    val selected = if (requested.nonEmpty) requested else _resolved(profile).workProducts.collect {
      case value if value.binding.disposition == CozyDocumentWorkflow.WorkProductDisposition.Optional => value.workProduct.id
    }
    val descriptor = project.resolve("document-project.yaml")
    val selection = if (selected.isEmpty) "activeOptionalWorkProducts: []" else "activeOptionalWorkProducts:\n" + selected.map(id => s"  - $id").mkString("\n")
    Files.writeString(descriptor, Files.readString(descriptor, StandardCharsets.UTF_8).replace("activeOptionalWorkProducts: []", selection), StandardCharsets.UTF_8)
    selected
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

  private def _with_content_core_lock[A](project: Path)(body: => A): A = {
    val channel = FileChannel.open(project.resolve(".content-core.lock"), StandardOpenOption.CREATE, StandardOpenOption.WRITE)
    val lock = channel.lock()
    try body
    finally {
      try lock.release()
      finally channel.close()
    }
  }

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
