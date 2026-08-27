package cozy.video

import cozy.CozySpecVocabulary
import cozy.media.CozyVisualPage
import io.circe.Json
import io.circe.parser
import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path}
import java.security.MessageDigest
import org.scalacheck.{Gen, Prop, Test}
import org.scalatest.GivenWhenThen
import org.scalatest.wordspec.AnyWordSpec
import scala.collection.JavaConverters._

/*
 * @since   Aug. 26, 2026
 * @version Aug. 28, 2026
 * @author  ASAMI, Tomoharu
 */
final class CozyVideoStoryboardReviewSpec
    extends AnyWordSpec
    with GivenWhenThen
    with CozySpecVocabulary {
  "Cozy Video Storyboard review evidence" should {
    "preserve v1 evidence and handoff schemas, canonical field order, and non-consumer output" in {
      _with_temp_dir("normal") { root =>
        Given("an approved Storyboard project without an optional visualStory declaration")
        val storyboard = _storyboard("")
        val source = _write_storyboard(root, storyboard)
        val project = _write_project(root, Some(_review_json(source, CozyVideo.storyboardIdentity(storyboard), None)))
        val save = root.resolve("review")

        When("the typed storyboard review evidence command writes its package")
        val result = CozyVideo.storyboardReviewEvidence(CozyVideo.StoryboardReviewConfig(project, save))
        val evidencetext = Files.readString(result.evidencePath, StandardCharsets.UTF_8)
        val handofftext = Files.readString(result.handoffPath, StandardCharsets.UTF_8)
        val evidence = parser.parse(evidencetext).toOption.get
        val handoff = parser.parse(handofftext).toOption.get

        Then("the deterministic evidence and handoff carry only the approved normalized Storyboard")
        evidence.hcursor.get[String]("schema").toOption shouldBe Some("cozy.video.storyboard-review-evidence.v1")
        evidence.hcursor.get[String]("status").toOption shouldBe Some("validated")
        evidence.hcursor.get[String]("identity").toOption shouldBe Some(result.evidenceIdentity)
        evidence.hcursor.downField("visualInputs").focus shouldBe empty
        handoff.hcursor.get[String]("schema").toOption shouldBe Some("cozy.video.storyboard-handoff.v1")
        evidencetext should startWith(s"""{"identity":"${result.evidenceIdentity}","schema":"cozy.video.storyboard-review-evidence.v1","status":"validated"""")
        handofftext should startWith("{\"schema\":\"cozy.video.storyboard-handoff.v1\",\"evidencePath\"")
        handoff.hcursor.downField("identity").focus shouldBe empty
        handoff.noSpaces.toLowerCase should not include "pptx"
        handoff.noSpaces.toLowerCase should not include "accepted"
      }
    }

    "copy requested visual inputs in authored order and record their identities" in {
      _with_temp_dir("visual-inputs") { root =>
        Given("an approved Storyboard and an ordered visualStory input subset")
        val storyboard = _storyboard("assets/title.svg", Vector("assets/title.svg"))
        val source = _write_storyboard(root, storyboard)
        val input = root.resolve("assets/title.svg")
        Files.createDirectories(input.getParent)
        Files.writeString(input, "title", StandardCharsets.UTF_8)
        val evidencedirectory = "target/cozy-video/storyboard-review"
        val project = _write_project(root, Some(_review_json(source, CozyVideo.storyboardIdentity(storyboard), Some(evidencedirectory -> Vector("assets/title.svg")))))
        val save = root.resolve(evidencedirectory)

        When("the visual-story evidence command is generated")
        val result = CozyVideo.storyboardReviewEvidence(CozyVideo.StoryboardReviewConfig(project, save))
        val evidence = parser.parse(Files.readString(result.evidencePath, StandardCharsets.UTF_8)).toOption.get
        val rows = evidence.hcursor.downField("visualInputs").as[Vector[Json]].toOption.get

        Then("only the admitted current input is copied and identities preserve source order")
        rows.map(_.hcursor.get[String]("reference").toOption.get) shouldBe Vector("assets/title.svg")
        rows.map(_.hcursor.get[String]("identity").toOption.get) shouldBe result.visualInputIdentities
        result.saveDir shouldBe save.toRealPath()
        Files.isRegularFile(save.resolve("visual-inputs/assets/title.svg")) shouldBe true
        result.visualInputIdentities should have size 1
      }
    }

    "route storyboard review-evidence directly before unsupported storyboard dispatch" in {
      _with_temp_dir("route") { root =>
        Given("a valid project and a direct storyboard review-evidence command line")
        val storyboard = _storyboard("")
        val source = _write_storyboard(root, storyboard)
        val project = _write_project(root, Some(_review_json(source, CozyVideo.storyboardIdentity(storyboard), None)))
        val save = root.resolve("route-review")

        When("the direct CLI route is executed")
        val routed = CozyVideo.execute(List("video", "storyboard", "review-evidence", project.toString, "--save", save.toString))

        Then("the direct route succeeds and emits both typed package files")
        routed shouldBe true
        Files.isRegularFile(save.resolve("review-evidence.json")) shouldBe true
        Files.isRegularFile(save.resolve("handoff.json")) shouldBe true
      }
    }

    "list every storyboard operation when the direct command has no subcommand" in {
      Given("the direct video storyboard command without a subcommand")

      When("the command is executed")
      val failure = intercept[IllegalArgumentException] {
        CozyVideo.execute(List("video", "storyboard"))
      }

      Then("the missing-command diagnostic lists every supported storyboard operation")
      failure.getMessage should include("validate")
      failure.getMessage should include("inspect")
      failure.getMessage should include("convert")
      failure.getMessage should include("review-evidence")
    }

    "reject changed or unapproved Storyboard sources before writing evidence" in {
      _with_temp_dir("approval") { root =>
        Given("a project whose approved identity describes an earlier normalized Storyboard")
        val original = _storyboard("")
        val source = _write_storyboard(root, original)
        val project = _write_project(root, Some(_review_json(source, CozyVideo.storyboardIdentity(original), None)))
        Files.writeString(source, CozyVideo.canonicalStoryboardJson(_storyboard("changed")), StandardCharsets.UTF_8)

        When("evidence generation reloads the selected Storyboard")
        val failure = intercept[Exception] {
          CozyVideo.storyboardReviewEvidence(CozyVideo.StoryboardReviewConfig(project, root.resolve("approval-review")))
        }

        Then("the changed source is rejected closed with an approval diagnostic")
        failure.getMessage should include("STORYBOARD_REVIEW_APPROVAL_MISMATCH")
        Files.exists(root.resolve("approval-review/review-evidence.json")) shouldBe false
      }
    }

    "reject missing undeclared and symlinked visual inputs" in {
      _with_temp_dir("input-safety") { root =>
        Given("visualStory declarations covering missing, undeclared, and symlinked references")
        val sourcestoryboard = _storyboard("assets/title.svg", Vector("assets/title.svg"))
        val source = _write_storyboard(root, sourcestoryboard)
        val identity = CozyVideo.storyboardIdentity(sourcestoryboard)
        val missingproject = _write_project(root, Some(_review_json(source, identity, Some("target/cozy-video/missing" -> Vector("assets/title.svg")))))
        val missingfailure = intercept[Exception] {
          CozyVideo.storyboardReviewEvidence(CozyVideo.StoryboardReviewConfig(missingproject, root.resolve("target/cozy-video/missing")))
        }
        val undeclaredproject = _write_project(root, Some(_review_json(source, identity, Some("target/cozy-video/undeclared" -> Vector("assets/other.svg")))))
        val undeclaredfailure = intercept[Exception] {
          CozyVideo.storyboardReviewEvidence(CozyVideo.StoryboardReviewConfig(undeclaredproject, root.resolve("target/cozy-video/undeclared")))
        }
        val target = root.resolve("outside.svg")
        Files.writeString(target, "outside", StandardCharsets.UTF_8)
        val linked = root.resolve("assets/title.svg")
        Files.createDirectories(linked.getParent)
        Files.createSymbolicLink(linked, target)
        val symlinkproject = _write_project(root, Some(_review_json(source, identity, Some("target/cozy-video/symlink" -> Vector("assets/title.svg")))))

        When("each visual input is admitted")
        val symlinkfailure = intercept[Exception] {
          CozyVideo.storyboardReviewEvidence(CozyVideo.StoryboardReviewConfig(symlinkproject, root.resolve("target/cozy-video/symlink")))
        }

        Then("every unsafe input fails closed with a specific diagnostic")
        missingfailure.getMessage should include("STORYBOARD_REVIEW_INPUT_UNSAFE")
        undeclaredfailure.getMessage should include("STORYBOARD_REVIEW_INPUT_UNDECLARED")
        symlinkfailure.getMessage should include("STORYBOARD_REVIEW_INPUT_UNSAFE")
      }
    }

    "reject malformed stale or unapproved visual evidence at the build gate before tool work" in {
      _with_temp_dir("build-gate") { root =>
        Given("a visual-story project with a generated evidence package")
        val storyboard = _storyboard("assets/title.svg", Vector("assets/title.svg"))
        val source = _write_storyboard(root, storyboard)
        val input = root.resolve("assets/title.svg")
        Files.createDirectories(input.getParent)
        Files.writeString(input, "title", StandardCharsets.UTF_8)
        val evidencedirectory = "target/cozy-video/build-gate"
        val project = _write_project(root, Some(_review_json(source, CozyVideo.storyboardIdentity(storyboard), Some(evidencedirectory -> Vector("assets/title.svg")))))
        val save = root.resolve(evidencedirectory)
        val generated = CozyVideo.storyboardReviewEvidence(CozyVideo.StoryboardReviewConfig(project, save))
        val originalevidence = Files.readString(generated.evidencePath, StandardCharsets.UTF_8)
        _write_project(root, Some(_review_json(source, CozyVideo.storyboardIdentity(storyboard), Some(evidencedirectory -> Vector("assets/title.svg")), Some(generated.evidenceIdentity))))
        Files.writeString(generated.evidencePath, "{}", StandardCharsets.UTF_8)

        When("a dry-run build reaches its approval gate")
        val failure = intercept[Exception] {
          CozyVideo.build(CozyVideo.BuildConfig(project, dryRun = true, checkTools = false), CozyVideo.VideoToolRegistry(Vector.empty))
        }

        Then("malformed visual evidence is rejected before any tool or process work")
        failure.getMessage should include("STORYBOARD_REVIEW_EVIDENCE_IDENTITY_MISSING")

        Files.writeString(generated.evidencePath, originalevidence, StandardCharsets.UTF_8)
        Files.writeString(input, "changed", StandardCharsets.UTF_8)
        When("the selected visual input changes after evidence generation")
        val stale = intercept[Exception] {
          CozyVideo.build(CozyVideo.BuildConfig(project, dryRun = true, checkTools = false), CozyVideo.VideoToolRegistry(Vector.empty))
        }
        Then("the stale package is rejected before build work")
        stale.getMessage should include("STORYBOARD_REVIEW_EVIDENCE_STALE")

        Files.writeString(input, "title", StandardCharsets.UTF_8)
        _write_project(root, Some(_review_json(source, CozyVideo.storyboardIdentity(storyboard), Some(evidencedirectory -> Vector("assets/title.svg")), Some("sha256:" + "0" * 64))))
        When("the visual evidence is present but not human-approved")
        val unapproved = intercept[Exception] {
          CozyVideo.build(CozyVideo.BuildConfig(project, dryRun = true, checkTools = false), CozyVideo.VideoToolRegistry(Vector.empty))
        }
        Then("the unapproved evidence identity is rejected before build work")
        unapproved.getMessage should include("STORYBOARD_REVIEW_APPROVED_EVIDENCE_MISMATCH")
      }
    }

    "keep legacy projects without storyboardReview behaviorally unchanged" in {
      _with_temp_dir("legacy") { root =>
        Given("a legacy project descriptor without a storyboardReview declaration")
        val project = _write_project(root, None)

        When("the existing dry-run build path plans the project")
        val output = CozyVideo.build(CozyVideo.BuildConfig(project, dryRun = true, checkTools = false), CozyVideo.VideoToolRegistry(Vector.empty))

        Then("the new gate is not applied to the legacy project")
        output should include("Dry-Run")
      }
    }

    "write v2 visual-page evidence and a self-identifying handoff from literal current inputs" in {
      _with_temp_dir("v2-visual-page") { root =>
        Given("a v2 Storyboard visual-page screen, its exact PageSet/catalog/binding resources, and a selected renderer")
        val storyboard = _v2_storyboard()
        val source = _write_storyboard(root, storyboard)
        val material = _write_visual_page_material(root)
        val project = _write_v2_project(root, source, storyboard, material.binding, "sha256:" + "0" * 64)
        val save = root.resolve("target/cozy-video/v2-review")

        When("the v2 review-evidence route derives the review package")
        val generated = CozyVideo.storyboardReviewEvidence(CozyVideo.StoryboardReviewConfig(project, save))
        val evidencetext = Files.readString(generated.evidencePath, StandardCharsets.UTF_8)
        val handofftext = Files.readString(generated.handoffPath, StandardCharsets.UTF_8)
        val evidence = parser.parse(evidencetext).toOption.get
        val handoff = parser.parse(handofftext).toOption.get

        Then("both v2 values retain literal screen references, every resolved identity, and deterministic renderer proof")
        evidence.hcursor.get[String]("schema").toOption shouldBe Some("cozy.video.storyboard-review-evidence.v2")
        evidence.hcursor.downField("visualPages").downArray.get[String]("kind").toOption shouldBe Some("visual-page")
        evidence.hcursor.downField("visualPages").downArray.get[String]("source").toOption shouldBe Some("visual-pages.json")
        evidence.hcursor.downField("visualPages").downArray.get[String]("visualPageSetIdentity").toOption.get should startWith("sha256:")
        val assetsha = evidence.hcursor.downField("visualPages").downArray.downField("assets").downArray.get[String]("sha256").toOption.get
        assetsha.length shouldBe 64
        evidence.hcursor.downField("effectiveRenderers").downArray.get[String]("partId").toOption shouldBe Some("storyboard")
        evidence.hcursor.downField("effectiveRenderers").downArray.get[String]("identity").toOption.get should startWith("sha256:")
        handoff.hcursor.get[String]("schema").toOption shouldBe Some("cozy.video.storyboard-handoff.v2")
        handoff.hcursor.get[String]("identity").toOption.get should startWith("sha256:")
        evidencetext should startWith("""{"schema":"cozy.video.storyboard-review-evidence.v2","status":"validated","source":""")
        evidencetext should endWith(s""""identity":"${generated.evidenceIdentity}"}""")
        handofftext should startWith("""{"schema":"cozy.video.storyboard-handoff.v2","status":"validated","evidencePath":""")
        handofftext should endWith(s""""identity":"${handoff.hcursor.get[String]("identity").toOption.get}"}""")
        handoff.hcursor.downField("visualPages").downArray.get[String]("bindingIdentity").toOption shouldBe
          evidence.hcursor.downField("visualPages").downArray.get[String]("bindingIdentity").toOption
      }
    }

    "reject a missing or unsafe v2 visualPage declaration without choosing a binding" in {
      _with_temp_dir("v2-visual-page-declaration") { root =>
        Given("a v2 visual-page Storyboard and direct candidate binding material")
        val storyboard = _v2_storyboard()
        val source = _write_storyboard(root, storyboard)
        val material = _write_visual_page_material(root)
        val missing = _write_v2_project(root, source, storyboard, material.binding, "sha256:" + "0" * 64, visualpage = None)

        When("the declaration is absent and then uses an escaping binding path")
        val missingfailure = intercept[Exception] {
          CozyVideo.storyboardReviewEvidence(CozyVideo.StoryboardReviewConfig(missing, root.resolve("target/cozy-video/missing")))
        }
        val unsafe = _write_v2_project(root, source, storyboard, material.binding, "sha256:" + "0" * 64, bindingref = "../binding.json")
        val unsafefailure = intercept[Exception] {
          CozyVideo.storyboardReviewEvidence(CozyVideo.StoryboardReviewConfig(unsafe, root.resolve("target/cozy-video/v2-review")))
        }

        Then("both routes fail closed rather than inferring or traversing a business binding")
        missingfailure.getMessage should include("STORYBOARD_REVIEW_VISUAL_PAGE_MISSING")
        unsafefailure.getMessage should include("STORYBOARD_REVIEW_PATH_INVALID")
      }
    }

    "reject stale v2 visual-page proof inputs and handoff records before confirmation planning" in {
      _with_temp_dir("v2-build-gate") { root =>
        Given("an approved v2 visual-page evidence package with direct current inputs")
        val storyboard = _v2_storyboard()
        val source = _write_storyboard(root, storyboard)
        val material = _write_visual_page_material(root)
        val project = _write_v2_project(root, source, storyboard, material.binding, "sha256:" + "0" * 64)
        val save = root.resolve("target/cozy-video/v2-review")
        val generated = CozyVideo.storyboardReviewEvidence(CozyVideo.StoryboardReviewConfig(project, save))
        _write_v2_project(root, source, storyboard, material.binding, generated.evidenceIdentity)
        val binding = Files.readString(material.binding, StandardCharsets.UTF_8)
        val catalog = Files.readString(root.resolve("catalog.json"), StandardCharsets.UTF_8)
        val asset = root.resolve("assets/diagram.svg")
        val assetbytes = Files.readString(asset, StandardCharsets.UTF_8)
        val pagefile = root.resolve("visual-pages.json")
        val pages = Files.readString(pagefile, StandardCharsets.UTF_8)
        val assetidentity = _sha256(asset)

        When("the binding, catalog, page asset, effective renderer, part selection, and handoff are changed in turn")
        Files.writeString(material.binding, binding.replace("nodes-slot", "nodes-revised-slot"), StandardCharsets.UTF_8)
        val bindingfailure = intercept[Exception] {
          CozyVideo.build(CozyVideo.BuildConfig(project, dryRun = true, checkTools = false, mode = Some("confirmation")), CozyVideo.VideoToolRegistry(Vector.empty))
        }
        Files.writeString(material.binding, binding, StandardCharsets.UTF_8)
        Files.writeString(root.resolve("catalog.json"), catalog.replace("\"core\"", "\"changed\""), StandardCharsets.UTF_8)
        val catalogfailure = intercept[Exception] {
          CozyVideo.build(CozyVideo.BuildConfig(project, dryRun = true, checkTools = false, mode = Some("confirmation")), CozyVideo.VideoToolRegistry(Vector.empty))
        }
        Files.writeString(root.resolve("catalog.json"), catalog, StandardCharsets.UTF_8)
        Files.writeString(asset, "<svg>changed</svg>", StandardCharsets.UTF_8)
        Files.writeString(pagefile, pages.replace(assetidentity, _sha256(asset)), StandardCharsets.UTF_8)
        val assetfailure = intercept[Exception] {
          CozyVideo.build(CozyVideo.BuildConfig(project, dryRun = true, checkTools = false, mode = Some("confirmation")), CozyVideo.VideoToolRegistry(Vector.empty))
        }
        Files.writeString(asset, assetbytes, StandardCharsets.UTF_8)
        Files.writeString(pagefile, pages, StandardCharsets.UTF_8)
        _write_v2_project(root, source, storyboard, material.binding, generated.evidenceIdentity, renderer = Some(Json.obj("engine" -> Json.fromString("remotion"), "fps" -> Json.fromInt(60))))
        val rendererfailure = intercept[Exception] {
          CozyVideo.build(CozyVideo.BuildConfig(project, dryRun = true, checkTools = false, mode = Some("confirmation")), CozyVideo.VideoToolRegistry(Vector.empty))
        }
        _write_v2_project(root, source, storyboard, material.binding, generated.evidenceIdentity, renderer = None)
        val norendererfailure = intercept[Exception] {
          CozyVideo.build(CozyVideo.BuildConfig(project, dryRun = true, checkTools = false, mode = Some("confirmation")), CozyVideo.VideoToolRegistry(Vector.empty))
        }
        _write_v2_project(root, source, storyboard, material.binding, generated.evidenceIdentity, storyboardpart = None)
        val nopartfailure = intercept[Exception] {
          CozyVideo.build(CozyVideo.BuildConfig(project, dryRun = true, checkTools = false, mode = Some("confirmation")), CozyVideo.VideoToolRegistry(Vector.empty))
        }
        _write_v2_project(root, source, storyboard, material.binding, generated.evidenceIdentity)
        Files.writeString(generated.handoffPath, "{}", StandardCharsets.UTF_8)
        val handofffailure = intercept[Exception] {
          CozyVideo.build(CozyVideo.BuildConfig(project, dryRun = true, checkTools = false, mode = Some("confirmation")), CozyVideo.VideoToolRegistry(Vector.empty))
        }

        Then("each current-input or self-identity mismatch fails closed before cache reuse or renderer work")
        bindingfailure.getMessage should include("STORYBOARD_REVIEW_EVIDENCE_STALE")
        catalogfailure.getMessage should include("VISUAL_PAGE_SCREEN_RESOLUTION_INVALID")
        assetfailure.getMessage should include("STORYBOARD_REVIEW_EVIDENCE_STALE")
        rendererfailure.getMessage should include("STORYBOARD_REVIEW_EVIDENCE_STALE")
        norendererfailure.getMessage should include("STORYBOARD_REVIEW_EFFECTIVE_RENDERER_MISSING")
        nopartfailure.getMessage should include("STORYBOARD_REVIEW_STORYBOARD_PART_MISSING")
        handofffailure.getMessage should include("STORYBOARD_REVIEW_HANDOFF_IDENTITY_MISSING")
      }
    }

    "reject duplicate v2 evidence and handoff keys before confirmation dry-run build work" in {
      _with_temp_dir("v2-duplicate-json") { root =>
        Given("an approved v2 visual-page evidence package and a runner that records any renderer action")
        val storyboard = _v2_storyboard()
        val source = _write_storyboard(root, storyboard)
        val material = _write_visual_page_material(root)
        val project = _write_v2_project(root, source, storyboard, material.binding, "sha256:" + "0" * 64)
        val save = root.resolve("target/cozy-video/v2-review")
        val generated = CozyVideo.storyboardReviewEvidence(CozyVideo.StoryboardReviewConfig(project, save))
        _write_v2_project(root, source, storyboard, material.binding, generated.evidenceIdentity)
        val originalevidence = Files.readString(generated.evidencePath, StandardCharsets.UTF_8)
        val originalhandoff = Files.readString(generated.handoffPath, StandardCharsets.UTF_8)
        var rendererinvoked = false
        val runner = new CozyVideo.VideoProcessRunner {
          def run(args: Vector[String], cwd: Path): CozyVideo.VideoCommandResult = {
            rendererinvoked = true
            CozyVideo.VideoCommandResult(0, "", "")
          }
        }

        When("the saved evidence and then the saved handoff repeat their schema field before a confirmation dry-run")
        Files.writeString(
          generated.evidencePath,
          originalevidence.replace("{\"schema\":", "{\"schema\":\"tampered\",\"schema\":"),
          StandardCharsets.UTF_8
        )
        val evidencefailure = intercept[Exception] {
          CozyVideo.build(
            CozyVideo.BuildConfig(project, dryRun = true, checkTools = false, mode = Some("confirmation")),
            CozyVideo.VideoToolRegistry(Vector.empty),
            runner
          )
        }
        Files.writeString(generated.evidencePath, originalevidence, StandardCharsets.UTF_8)
        Files.writeString(
          generated.handoffPath,
          originalhandoff.replace("{\"schema\":", "{\"schema\":\"tampered\",\"schema\":"),
          StandardCharsets.UTF_8
        )
        val handofffailure = intercept[Exception] {
          CozyVideo.build(
            CozyVideo.BuildConfig(project, dryRun = true, checkTools = false, mode = Some("confirmation")),
            CozyVideo.VideoToolRegistry(Vector.empty),
            runner
          )
        }

        Then("both duplicate proof values fail closed before renderer or approval work")
        evidencefailure.getMessage should include("STORYBOARD_REVIEW_EVIDENCE_DUPLICATE_FIELD")
        handofffailure.getMessage should include("STORYBOARD_REVIEW_EVIDENCE_DUPLICATE_FIELD")
        rendererinvoked shouldBe false
      }
    }

    "keep generated evidence identity stable for semantically identical Storyboard inputs" in {
      Given("a generator of equivalent Storyboard captions")
      val captions = Gen.oneOf(Vector("", "caption", "another caption"))
      val property = Prop.forAll(captions) { caption =>
        _with_temp_dir("identity-property") { root =>
          val storyboard = _storyboard(caption)
          val source = _write_storyboard(root, storyboard)
          val project = _write_project(root, Some(_review_json(source, CozyVideo.storyboardIdentity(storyboard), None)))
          val first = CozyVideo.storyboardReviewEvidence(CozyVideo.StoryboardReviewConfig(project, root.resolve("first")))
          val second = CozyVideo.storyboardReviewEvidence(CozyVideo.StoryboardReviewConfig(project, root.resolve("second")))
          first.evidenceIdentity == second.evidenceIdentity &&
          Files.readString(first.evidencePath, StandardCharsets.UTF_8) == Files.readString(second.evidencePath, StandardCharsets.UTF_8)
        }
      }

      When("ScalaCheck evaluates equivalent deterministic generator outputs")
      val result = Test.check(Test.Parameters.default.withMinSuccessfulTests(30), property)

      Then("equivalent normalized Storyboard inputs have one evidence identity")
      withClue(s"ScalaCheck status=${result.status}, succeeded=${result.succeeded}, discarded=${result.discarded}") {
        result.passed shouldBe true
      }
    }
  }

  private def _storyboard(caption: String, diagramrefs: Vector[String] = Vector.empty): CozyVideo.Storyboard =
    CozyVideo.Storyboard(
      "cozy.video.storyboard.v1",
      1,
      Vector(CozyVideo.StoryboardScene(
        "intro",
        1,
        "opening",
        "narrator",
        "narration",
        "Welcome to Cozy.",
        CozyVideo.StoryboardScreen("Welcome", "Screen"),
        caption,
        BigDecimal("1.0"),
        BigDecimal("0.0"),
        "cut",
        Vector.empty,
        diagramrefs,
        Vector.empty,
        Vector.empty,
        "Show the screen."
      ))
    )

  private final case class VisualPageMaterial(binding: Path)

  private def _v2_storyboard(): CozyVideo.Storyboard =
    CozyVideo.Storyboard(
      "cozy.video.storyboard.v2",
      2,
      Vector(CozyVideo.StoryboardScene(
        "visual",
        1,
        "opening",
        "narrator",
        "narration",
        "Visual Page proof.",
        CozyVideoImplementation.StoryboardVisualPageScreen("visual-pages.json", "catalog.json", "overview"),
        "Visual Page",
        BigDecimal("1.0"),
        BigDecimal("0.0"),
        "cut",
        Vector.empty,
        Vector.empty,
        Vector.empty,
        Vector.empty,
        "Review the exact visual page."
      ))
    )

  private def _write_visual_page_material(root: Path): VisualPageMaterial = {
    val asset = root.resolve("assets/diagram.svg")
    val source = root.resolve("sources/research.txt")
    Files.createDirectories(asset.getParent)
    Files.createDirectories(source.getParent)
    Files.writeString(asset, "<svg/>", StandardCharsets.UTF_8)
    Files.writeString(source, "research", StandardCharsets.UTF_8)
    val catalog = _visual_catalog()
    val page = CozyVisualPage.Page(
      "overview",
      "Cozy",
      "en",
      CozyVisualPage.CatalogReference("core", 1),
      CozyVisualPage.Logical(
        "sequence",
        Vector(
          CozyVisualPage.Node("discover", "step", "Discover", Vector("research")),
          CozyVisualPage.Node("apply", "step", "Apply", Vector("research"))
        ),
        Vector(CozyVisualPage.Relation("next", "next", "discover", "apply", Vector("research")))
      ),
      CozyVisualPage.Visual("flow-horizontal", Vector.empty),
      Vector(CozyVisualPage.Asset("diagram", "assets/diagram.svg", "image/svg+xml", _sha256(asset))),
      Vector(CozyVisualPage.SourceBinding("research", "sources/research.txt"))
    )
    Files.writeString(root.resolve("catalog.json"), CozyVisualPage.canonicalCatalogJson(catalog), StandardCharsets.UTF_8)
    Files.writeString(root.resolve("visual-pages.json"), CozyVisualPage.canonicalJson(CozyVisualPage.PageSet("pages", Vector(page))), StandardCharsets.UTF_8)
    val binding = root.resolve("binding.json")
    Files.writeString(binding, _binding_json, StandardCharsets.UTF_8)
    VisualPageMaterial(binding)
  }

  private def _write_v2_project(
    root: Path,
    source: Path,
    storyboard: CozyVideo.Storyboard,
    binding: Path,
    approval: String,
    visualpage: Option[Boolean] = Some(true),
    bindingref: String = "binding.json",
    renderer: Option[Json] = Some(Json.obj("engine" -> Json.fromString("remotion"), "fps" -> Json.fromInt(30))),
    storyboardpart: Option[String] = Some("storyboard.json")
  ): Path = {
    val reviewfields = Vector(
      "source" -> Json.fromString(root.relativize(source).toString.replace(java.io.File.separatorChar, '/')),
      "approvedIdentity" -> Json.fromString(CozyVideo.storyboardIdentity(storyboard))
    ) ++ visualpage.toVector.map { _ =>
      "visualPage" -> Json.obj(
        "binding" -> Json.fromString(bindingref),
        "evidenceDirectory" -> Json.fromString("target/cozy-video/v2-review"),
        "approvedEvidenceIdentity" -> Json.fromString(approval)
      )
    }
    val projectfields = Vector(
      Some("name" -> Json.fromString("v2-review")),
      renderer.map(value => "renderer" -> value),
      Some("storyboardReview" -> Json.obj(reviewfields: _*)),
      Some("parts" -> Json.fromValues(storyboardpart.toVector.map { value =>
        Json.obj("id" -> Json.fromString("storyboard"), "type" -> Json.fromString("storyboard"), "storyboard" -> Json.fromString(value))
      }))
    ).flatten
    val project = root.resolve("video-v2.json")
    Files.writeString(project, Json.obj(projectfields: _*).noSpaces, StandardCharsets.UTF_8)
    project
  }

  private def _visual_catalog(): CozyVisualPage.Catalog = CozyVisualPage.Catalog(
    "core",
    1,
    Vector(
      CozyVisualPage.RelationDefinition("next", "from-to"),
      CozyVisualPage.RelationDefinition("causes", "from-to"),
      CozyVisualPage.RelationDefinition("depends-on", "from-to"),
      CozyVisualPage.RelationDefinition("enables", "from-to"),
      CozyVisualPage.RelationDefinition("maps-to", "from-to")
    ),
    Vector(
      CozyVisualPage.LogicalPattern("sequence", Vector(CozyVisualPage.NodeRole("step", 2, 8)), Vector(CozyVisualPage.RelationRule("next", Vector("step"), Vector("step"), 1, 7, "linear"))),
      CozyVisualPage.LogicalPattern("causal-chain", Vector(CozyVisualPage.NodeRole("cause", 1, 7), CozyVisualPage.NodeRole("effect", 1, 7)), Vector(CozyVisualPage.RelationRule("causes", Vector("cause"), Vector("effect"), 1, 16, "acyclic"), CozyVisualPage.RelationRule("enables", Vector("cause"), Vector("effect"), 1, 16, "acyclic"))),
      CozyVisualPage.LogicalPattern("dependency-map", Vector(CozyVisualPage.NodeRole("dependency", 1, 7), CozyVisualPage.NodeRole("dependent", 1, 7)), Vector(CozyVisualPage.RelationRule("depends-on", Vector("dependent"), Vector("dependency"), 1, 16, "acyclic"))),
      CozyVisualPage.LogicalPattern("mapping", Vector(CozyVisualPage.NodeRole("source", 1, 7), CozyVisualPage.NodeRole("target", 1, 7)), Vector(CozyVisualPage.RelationRule("maps-to", Vector("source"), Vector("target"), 1, 16, "bipartite")))
    ),
    Vector(
      CozyVisualPage.VisualPattern("flow-horizontal", Vector("causal-chain", "sequence"), Vector(CozyVisualPage.ParameterDefinition("emphasisNode", "node-ref", false), CozyVisualPage.ParameterDefinition("showRelationLabels", "boolean", false))),
      CozyVisualPage.VisualPattern("flow-vertical", Vector("causal-chain", "dependency-map", "sequence"), Vector(CozyVisualPage.ParameterDefinition("emphasisNode", "node-ref", false), CozyVisualPage.ParameterDefinition("showRelationLabels", "boolean", false))),
      CozyVisualPage.VisualPattern("mapping-columns", Vector("mapping"), Vector(CozyVisualPage.ParameterDefinition("showRelationLabels", "boolean", false), CozyVisualPage.ParameterDefinition("sourceColumnTitle", "string", true), CozyVisualPage.ParameterDefinition("targetColumnTitle", "string", true)))
    )
  )

  private def _binding_json: String = {
    val slots = Vector("knowledge", "nodes", "relations", "assets", "parameters").map { slot =>
      s"""{"semanticSlot":"$slot","physicalSlot":"$slot-slot"}"""
    }.mkString("[", ",", "]")
    val patterns = Vector("flow-horizontal", "flow-vertical", "mapping-columns").map { pattern =>
      s"""{"visualPattern":"$pattern","slots":$slots}"""
    }.mkString("[", ",", "]")
    s"""{"schema":"cozy.visual-page.binding.v1","version":1,"id":"business-default","profile":"business","catalog":{"id":"core","revision":1},"patterns":$patterns}"""
  }

  private def _sha256(path: Path): String =
    MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path)).map(value => f"${value & 0xff}%02x").mkString

  private def _write_storyboard(root: Path, storyboard: CozyVideo.Storyboard): Path = {
    val source = root.resolve("storyboard.json")
    Files.writeString(source, CozyVideo.canonicalStoryboardJson(storyboard), StandardCharsets.UTF_8)
    source
  }

  private def _review_json(
    source: Path,
    identity: String,
    visual: Option[(String, Vector[String])],
    evidenceidentity: Option[String] = None
  ): String = {
    val root = source.getParent
    val sourcepath = root.relativize(source).toString.replace(java.io.File.separatorChar, '/')
    val base = Json.obj(
      "source" -> Json.fromString(sourcepath),
      "approvedIdentity" -> Json.fromString(identity)
    )
    val review = visual.map { case (directory, refs) =>
      val visualfields = Vector(
        Some("evidenceDirectory" -> Json.fromString(directory)),
        Some("inputRefs" -> Json.fromValues(refs.map(Json.fromString))),
        evidenceidentity.map(value => "approvedEvidenceIdentity" -> Json.fromString(value))
      ).flatten
      base.deepMerge(Json.obj("visualStory" -> Json.obj(visualfields: _*)))
    }.getOrElse(base)
    review.noSpaces
  }

  private def _write_project(root: Path, review: Option[String]): Path = {
    val project = root.resolve("video.yaml")
    val base = Json.obj(
      "name" -> Json.fromString("review-test"),
      "parts" -> Json.arr()
    )
    val document = review.map(value => base.deepMerge(Json.obj("storyboardReview" -> parser.parse(value).toOption.get))).getOrElse(base)
    Files.writeString(project, document.spaces2, StandardCharsets.UTF_8)
    project
  }

  private def _with_temp_dir[A](name: String)(body: Path => A): A = {
    val root = Files.createTempDirectory("cozy-video-storyboard-review-" + name + "-")
    try body(root)
    finally _delete(root)
  }

  private def _delete(path: Path): Unit =
    if (Files.exists(path)) {
      val stream = Files.walk(path)
      try stream.iterator().asScala.toVector.sortBy(_.toString.length).reverse.foreach(Files.delete)
      finally stream.close()
    }
}
