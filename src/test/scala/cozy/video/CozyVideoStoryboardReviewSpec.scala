package cozy.video

import cozy.CozySpecVocabulary
import io.circe.Json
import io.circe.parser
import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path}
import org.scalacheck.{Gen, Prop, Test}
import org.scalatest.GivenWhenThen
import org.scalatest.wordspec.AnyWordSpec
import scala.collection.JavaConverters._

/*
 * @since   Aug. 26, 2026
 * @version Aug. 26, 2026
 * @author  ASAMI, Tomoharu
 */
final class CozyVideoStoryboardReviewSpec
    extends AnyWordSpec
    with GivenWhenThen
    with CozySpecVocabulary {
  "Cozy Video Storyboard review evidence" should {
    "write normal approval evidence and a non-consumer handoff without visual claims" in {
      _with_temp_dir("normal") { root =>
        Given("an approved Storyboard project without an optional visualStory declaration")
        val storyboard = _storyboard("")
        val source = _write_storyboard(root, storyboard)
        val project = _write_project(root, Some(_review_json(source, CozyVideo.storyboardIdentity(storyboard), None)))
        val save = root.resolve("review")

        When("the typed storyboard review evidence command writes its package")
        val result = CozyVideo.storyboardReviewEvidence(CozyVideo.StoryboardReviewConfig(project, save))
        val evidence = parser.parse(Files.readString(result.evidencePath, StandardCharsets.UTF_8)).toOption.get
        val handoff = parser.parse(Files.readString(result.handoffPath, StandardCharsets.UTF_8)).toOption.get

        Then("the deterministic evidence and handoff carry only the approved normalized Storyboard")
        evidence.hcursor.get[String]("schema").toOption shouldBe Some("cozy.video.storyboard-review-evidence.v1")
        evidence.hcursor.get[String]("status").toOption shouldBe Some("validated")
        evidence.hcursor.get[String]("identity").toOption shouldBe Some(result.evidenceIdentity)
        evidence.hcursor.downField("visualInputs").focus shouldBe empty
        handoff.hcursor.get[String]("schema").toOption shouldBe Some("cozy.video.storyboard-handoff.v1")
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
