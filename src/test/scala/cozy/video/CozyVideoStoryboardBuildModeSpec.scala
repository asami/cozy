package cozy.video

import cozy.CozySpecVocabulary
import io.circe.Json
import io.circe.parser
import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path}
import org.scalatest.GivenWhenThen
import org.scalatest.wordspec.AnyWordSpec
import scala.collection.JavaConverters._
import scala.collection.mutable.ArrayBuffer

/*
 * @since   Aug. 26, 2026
 * @version Aug. 26, 2026
 * @author  ASAMI, Tomoharu
 */
final class CozyVideoStoryboardBuildModeSpec
    extends AnyWordSpec
    with GivenWhenThen
    with CozySpecVocabulary {
  "Cozy Video Storyboard build modes" should {
    "reject a Storyboard project without a mode while preserving the legacy no-mode build" in {
      _with_temp_dir("mode-required") { root =>
        Given("an approved Storyboard project and an independent legacy dialogue project")
        val storyboardroot = root.resolve("storyboard")
        val legacyroot = root.resolve("legacy")
        val storyboardproject = _write_storyboard_project(storyboardroot, "storyboard.md", _storyboard())
        val legacyproject = _write_legacy_project(legacyroot)
        val runner = new FakeRunner

        When("a Storyboard build omits --mode and the legacy build omits it")
        val failure = intercept[Exception] {
          CozyVideo.build(
            CozyVideo.BuildConfig(storyboardproject, dryRun = false, checkTools = false, toolMode = Some("host")),
            CozyVideo.VideoToolRegistry(Vector.empty),
            runner
          )
        }
        val legacy = CozyVideo.build(
          CozyVideo.BuildConfig(legacyproject, dryRun = false, checkTools = false, toolMode = Some("host")),
          CozyVideo.VideoToolRegistry(Vector.empty),
          runner
        )

        Then("the Storyboard path fails closed before process work and the legacy output is assembled")
        failure.getMessage should include("Storyboard video build requires --mode confirmation or --mode final")
        runner.commandCount shouldBe 2
        legacy should include("Cozy Video Build")
        Files.isRegularFile(legacyroot.resolve("build/final.mp4")) shouldBe true
      }
    }

    "write a generated handoff and separate confirmation records from an approved Markdown Storyboard" in {
      _with_temp_dir("confirmation") { root =>
        Given("an approved project-owned storyboard.md and no source-managed script.json")
        val project = _write_storyboard_project(root, "storyboard.md", _storyboard())
        val runner = new FakeRunner

        When("the public confirmation mode builds the approved Storyboard")
        val output = _build(project, "confirmation", runner)
        val handoff = root.resolve("target/cozy-video/storyboard/board/handoff.json")
        val manifest = root.resolve("target/cozy-video/confirmation/manifest.json")
        val confirmation = root.resolve("target/cozy-video/confirmation/confirmation.mp4")

        Then("Cozy writes only generated target handoff and confirmation output records")
        Files.exists(root.resolve("script.json")) shouldBe false
        output should include("mode: confirmation")
        output should include("cache: miss")
        Files.isRegularFile(handoff) shouldBe true
        _string(_json(handoff), "schema") shouldBe "cozy.video.storyboard-build-handoff.v1"
        _string(_json(handoff), "storyboardIdentity") shouldBe CozyVideo.storyboardIdentity(_storyboard())
        _string(_json(manifest), "schema") shouldBe "cozy.video.confirmation.v1"
        Files.isRegularFile(confirmation) shouldBe true
        Files.exists(root.resolve("build/final.mp4")) shouldBe false
      }
    }

    "fail final before output for missing or mismatched confirmation approval, then accept the exact identity" in {
      _with_temp_dir("final-approval") { root =>
        Given("a completed confirmation build without a recorded confirmation approval")
        val storyboard = _storyboard()
        val project = _write_storyboard_project(root, "storyboard.md", storyboard)
        val runner = new FakeRunner
        _build(project, "confirmation", runner)
        val confirmation = root.resolve("target/cozy-video/confirmation/confirmation.mp4")
        val confirmationbytes = Files.readAllBytes(confirmation)
        val confirmationmanifest = root.resolve("target/cozy-video/confirmation/manifest.json")
        val identity = _string(_json(confirmationmanifest), "identity")

        When("final is attempted with no approval, a mismatched approval, and then the exact manifest identity")
        val missing = intercept[Exception] {
          _build(project, "final", runner)
        }
        _write_storyboard_project(root, "storyboard.md", storyboard, Some("sha256:" + "0" * 64))
        val mismatched = intercept[Exception] {
          _build(project, "final", runner)
        }
        _write_storyboard_project(root, "storyboard.md", storyboard, Some(identity))
        val finalresult = _build(project, "final", runner)

        Then("only the exact approval admits a distinct final output and leaves confirmation bytes intact")
        missing.getMessage should include("requires confirmationReview.approvedIdentity")
        mismatched.getMessage should include("requires confirmationReview.approvedIdentity")
        finalresult should include("mode: final")
        Files.readAllBytes(confirmation) shouldBe confirmationbytes
        Files.isRegularFile(root.resolve("build/final.mp4")) shouldBe true
        _string(_json(root.resolve("target/cozy-video/final/manifest.json")), "schema") shouldBe "cozy.video.final.v1"
      }
    }

    "use only the canonical final Storyboard manifest for completed video review evidence" in {
      _with_temp_dir("final-review-evidence") { root =>
        Given("a successful confirmation followed by an approved final Storyboard build without a legacy project manifest")
        val storyboard = _storyboard()
        val project = _write_storyboard_project(root, "storyboard.md", storyboard)
        val runner = new FakeRunner
        _build(project, "confirmation", runner)
        val confirmationmanifest = root.resolve("target/cozy-video/confirmation/manifest.json")
        _write_storyboard_project(root, "storyboard.md", storyboard, Some(_string(_json(confirmationmanifest), "identity")))
        _build(project, "final", runner)
        val plan = CozyVideoImplementation._plan(project, Some("host"), None)
        _write_review_evidence_inputs(plan)

        When("the public review-evidence command validates the final Storyboard artifact")
        val review = CozyVideo.reviewEvidence(
          CozyVideo.ReviewEvidenceConfig(project, root.resolve("review"), toolMode = Some("host")),
          CozyVideo.VideoToolRegistry(Vector.empty),
          runner
        )
        val manifest = _json(root.resolve("review/review-manifest.json"))
        val finalmanifest = _json(root.resolve("target/cozy-video/final/manifest.json"))

        Then("review evidence records target/cozy-video/final/manifest.json and does not require legacy fields")
        review should include("Cozy Video Review Evidence")
        manifest.hcursor.downField("videoManifest").get[String]("path").toOption.map { path =>
          Files.isSameFile(Path.of(path), root.resolve("target/cozy-video/final/manifest.json"))
        } shouldBe Some(true)
        finalmanifest.hcursor.downField("output").get[String]("sha256").toOption shouldBe
          manifest.hcursor.downField("finalVideo").get[String]("sha256").toOption.map(value => s"sha256:$value")
        Files.exists(root.resolve("build/manifest.json")) shouldBe false
      }
    }

    "reject incomplete, noncanonical, and symlinked Storyboard final manifests before review evidence" in {
      _with_temp_dir("final-manifest-trust") { root =>
        Given("a completed approved Storyboard final build and its required review inputs")
        val storyboard = _storyboard()
        val project = _write_storyboard_project(root, "storyboard.md", storyboard)
        val runner = new FakeRunner
        _build(project, "confirmation", runner)
        val confirmation = root.resolve("target/cozy-video/confirmation/manifest.json")
        _write_storyboard_project(root, "storyboard.md", storyboard, Some(_string(_json(confirmation), "identity")))
        _build(project, "final", runner)
        val plan = CozyVideoImplementation._plan(project, Some("host"), None)
        _write_review_evidence_inputs(plan)
        val finaldir = root.resolve("target/cozy-video/final")
        val finalmanifest = finaldir.resolve("manifest.json")
        val canonical = Files.readString(finalmanifest, StandardCharsets.UTF_8)

        When("review evidence receives incomplete, noncanonical, and symlinked final manifest inputs")
        Files.writeString(finalmanifest, """{"schema":"cozy.video.final.v1"}""", StandardCharsets.UTF_8)
        val incomplete = intercept[Exception] {
          CozyVideo.reviewEvidence(CozyVideo.ReviewEvidenceConfig(project, root.resolve("review-incomplete"), toolMode = Some("host")), CozyVideo.VideoToolRegistry(Vector.empty), runner)
        }
        Files.writeString(finalmanifest, _json_text(canonical).spaces2, StandardCharsets.UTF_8)
        val noncanonical = intercept[Exception] {
          CozyVideo.reviewEvidence(CozyVideo.ReviewEvidenceConfig(project, root.resolve("review-noncanonical"), toolMode = Some("host")), CozyVideo.VideoToolRegistry(Vector.empty), runner)
        }
        Files.writeString(finalmanifest, canonical, StandardCharsets.UTF_8)
        val direct = root.resolve("target/cozy-video/final-direct")
        Files.move(finaldir, direct)
        Files.createSymbolicLink(finaldir, direct)
        val symlinked = intercept[Exception] {
          CozyVideo.reviewEvidence(CozyVideo.ReviewEvidenceConfig(project, root.resolve("review-symlinked"), toolMode = Some("host")), CozyVideo.VideoToolRegistry(Vector.empty), runner)
        }

        Then("each untrusted manifest route fails closed while the canonical direct manifest remains the accepted form")
        incomplete.getMessage should include("Storyboard final manifest is incomplete, stale, or noncanonical")
        noncanonical.getMessage should include("Storyboard final manifest is incomplete, stale, or noncanonical")
        symlinked.getMessage should include("direct project-contained non-symlink directories")
      }
    }

    "reject a symlinked confirmation ancestor before final approval input is trusted" in {
      _with_temp_dir("confirmation-ancestor") { root =>
        Given("a completed confirmation whose generated directory is replaced by a symbolic link")
        val project = _write_storyboard_project(root, "storyboard.md", _storyboard())
        val runner = new FakeRunner
        _build(project, "confirmation", runner)
        val confirmation = root.resolve("target/cozy-video/confirmation")
        val direct = root.resolve("target/cozy-video/confirmation-direct")
        Files.move(confirmation, direct)
        Files.createSymbolicLink(confirmation, direct)

        When("the public final build attempts to load confirmation approval input")
        val failure = intercept[Exception] {
          _build(project, "final", runner)
        }

        Then("the generated confirmation ancestor is rejected before its leaf files are trusted")
        failure.getMessage should include("direct project-contained non-symlink directories")
      }
    }

    "reuse only an exact confirmation cache input and invalidate it when the effective renderer changes" in {
      _with_temp_dir("cache") { root =>
        Given("a confirmation project with a deterministic fake media runner")
        val storyboard = _storyboard()
        val project = _write_storyboard_project(root, "storyboard.md", storyboard)
        val runner = new FakeRunner

        When("the same confirmation is built twice and then its renderer setting changes")
        _build(project, "confirmation", runner)
        val firstcount = runner.commandCount
        val hit = _build(project, "confirmation", runner)
        val hitcount = runner.commandCount
        _write_storyboard_project(root, "storyboard.md", storyboard, renderer = Some("remotion"))
        val miss = _build(project, "confirmation", runner)

        Then("the exact cache avoids ffmpeg and ffprobe while the effective setting change reruns them")
        hit should include("cache: hit")
        hitcount shouldBe firstcount
        miss should include("cache: miss")
        runner.commandCount shouldBe firstcount + 2
      }
    }

    "invalidate an otherwise exact Storyboard cache when title or character configuration changes" in {
      _with_temp_dir("title-character-cache") { root =>
        Given("a Storyboard project with stable nested character configuration")
        val storyboard = _storyboard()
        val initial = """  "title": "First title",
                        |  "characters": {"narrator": {"voice": {"fallbackSpeakerId": 7}, "side": "left"}},
                        |""".stripMargin
        val project = _write_storyboard_project(root, "storyboard.md", storyboard, configuration = initial)
        val runner = new FakeRunner

        When("only title and then only character configuration change between confirmation builds")
        _build(project, "confirmation", runner)
        val firstcount = runner.commandCount
        _write_storyboard_project(root, "storyboard.md", storyboard, configuration = initial.replace("First title", "Second title"))
        val titlemiss = _build(project, "confirmation", runner)
        _write_storyboard_project(root, "storyboard.md", storyboard, configuration = initial.replace("\"left\"", "\"right\""))
        val charactermiss = _build(project, "confirmation", runner)

        Then("both output-affecting project inputs are canonical cache identity members")
        titlemiss should include("cache: miss")
        charactermiss should include("cache: miss")
        runner.commandCount shouldBe firstcount + 4
      }
    }

    "invalidate an unchanged confirmation cache when a renderable part input or type changes" in {
      _with_temp_dir("part-input-cache") { root =>
        Given("a web-demo Storyboard part with direct steps and recording inputs")
        val storyboard = _storyboard()
        val project = _write_web_demo_storyboard_project(root, storyboard)
        val runner = new FakeRunner

        When("the same confirmation is reused, then its steps, recording tree, and effective part type change")
        _build(project, "confirmation", runner)
        val firstcount = runner.commandCount
        val hit = _build(project, "confirmation", runner)
        Files.writeString(root.resolve("steps.json"), "{\"steps\":[{\"kind\":\"click\"}]}", StandardCharsets.UTF_8)
        val stepsmiss = _build(project, "confirmation", runner)
        Files.writeString(root.resolve("build/record/board/recording.txt"), "changed recording", StandardCharsets.UTF_8)
        val recordingmiss = _build(project, "confirmation", runner)
        Files.writeString(
          project,
          Files.readString(project, StandardCharsets.UTF_8).replace("\"type\": \"web-demo\"", "\"type\": \"dialogue\""),
          StandardCharsets.UTF_8
        )
        val typemiss = _build(project, "confirmation", runner)

        Then("each material resolved part input or configuration change reruns assembly instead of reusing stale output")
        hit should include("cache: hit")
        stepsmiss should include("cache: miss")
        recordingmiss should include("cache: miss")
        typemiss should include("cache: miss")
        runner.commandCount shouldBe firstcount + 6
      }
    }

    "reject a symlinked renderable Storyboard part output before assembly starts" in {
      _with_temp_dir("part-output-symlink") { root =>
        Given("an approved Storyboard part whose renderable output is replaced by a symbolic link")
        val project = _write_storyboard_project(root, "storyboard.md", _storyboard())
        val output = root.resolve("build/parts/board.mp4")
        val direct = root.resolve("build/parts/board-direct.mp4")
        Files.move(output, direct)
        Files.createSymbolicLink(output, direct)
        val runner = new FakeRunner

        When("confirmation attempts to assemble the Storyboard project")
        val failure = intercept[Exception] {
          _build(project, "confirmation", runner)
        }

        Then("the unsafe part output fails closed before ffmpeg or ffprobe can run")
        failure.getMessage should include("Storyboard part board output must not use a symbolic-link path")
        runner.commandCount shouldBe 0
      }
    }

    "reject a symlinked external renderable Storyboard part parent before assembly starts" in {
      _with_temp_dir("external-part-parent-symlink") { root =>
        Given("an approved Storyboard project whose external renderable part output parent contains a symbolic link")
        val projectroot = root.resolve("project")
        val directparent = root.resolve("external-part-direct")
        val linkedparent = root.resolve("external-part-link")
        Files.createDirectories(directparent)
        Files.createSymbolicLink(linkedparent, directparent)
        val project = _write_storyboard_project(
          projectroot,
          "storyboard.md",
          _storyboard(),
          outputpath = "build/final.mp4",
          partoutputpath = "../external-part-link/board.mp4"
        )
        val runner = new FakeRunner

        When("confirmation prepares the configured external part output")
        val failure = intercept[Exception] {
          _build(project, "confirmation", runner)
        }

        Then("the unsafe external parent fails closed before ffmpeg or ffprobe can run")
        failure.getMessage should include("Storyboard part board output directory")
        failure.getMessage should include("symbolic-link")
        runner.commandCount shouldBe 0
      }
    }

    "reject a symlinked external final output parent after confirmation approval" in {
      _with_temp_dir("external-final-parent-symlink") { root =>
        Given("a Storyboard project whose external final output parent contains a symbolic link")
        val projectroot = root.resolve("project")
        val directparent = root.resolve("external-final-direct")
        val linkedparent = root.resolve("external-final-link")
        Files.createDirectories(directparent)
        Files.createSymbolicLink(linkedparent, directparent)
        val outputpath = "../external-final-link/final.mp4"
        val storyboard = _storyboard()
        val project = _write_storyboard_project(projectroot, "storyboard.md", storyboard, outputpath = outputpath)
        val runner = new FakeRunner

        When("confirmation is built for the configured final output")
        _build(project, "confirmation", runner)
        val confirmationmanifest = projectroot.resolve("target/cozy-video/confirmation/manifest.json")
        Then("the confirmation output is direct and available for approval")
        Files.isRegularFile(confirmationmanifest) shouldBe true
        val confirmationidentity = _string(_json(confirmationmanifest), "identity")
        Given("the direct confirmation identity is approved for final")
        _write_storyboard_project(
          projectroot,
          "storyboard.md",
          storyboard,
          confirmationidentity = Some(confirmationidentity),
          outputpath = outputpath
        )
        val commandcount = runner.commandCount

        When("final is attempted with the approved confirmation")
        val failure = intercept[Exception] {
          _build(project, "final", runner)
        }

        Then("the unsafe external final parent fails closed before another process invocation")
        failure.getMessage should include("Storyboard final output directory")
        failure.getMessage should include("symbolic-link")
        runner.commandCount shouldBe commandcount
      }
    }

    "bind every renderable Storyboard and legacy part artifact before accepting a mode cache" in {
      _with_temp_dir("mixed-part-artifacts") { root =>
        Given("a mixed Storyboard and legacy project with direct output artifacts for both parts")
        val storyboard = _storyboard()
        val project = _write_storyboard_project(root, "storyboard.md", storyboard)
        val legacyoutput = root.resolve("build/parts/legacy.mp4")
        val legacyartifact = root.resolve("target/cozy-video/confirmation/part-artifacts/legacy.json")
        Files.writeString(root.resolve("legacy.json"), """{"scenes":[{"id":"legacy","line":"Legacy"}]}""", StandardCharsets.UTF_8)
        Files.writeString(legacyoutput, "pre-rendered-legacy-part", StandardCharsets.UTF_8)
        Files.writeString(
          project,
          s"""{
             |  "output": "build/final.mp4",
             |  "storyboardReview": {"source": "storyboard.md", "approvedIdentity": "${CozyVideo.storyboardIdentity(storyboard)}"},
             |  "parts": [
             |    {"id": "board", "type": "storyboard", "storyboard": "storyboard.md", "output": "build/parts/board.mp4"},
             |    {"id": "legacy", "type": "dialogue", "script": "legacy.json", "output": "build/parts/legacy.mp4"}
             |  ]
             |}
             |""".stripMargin,
          StandardCharsets.UTF_8
        )
        val runner = new FakeRunner

        When("a cached confirmation is followed by a changed legacy output and then a deleted mode-local legacy artifact manifest")
        _build(project, "confirmation", runner)
        val hit = _build(project, "confirmation", runner)
        Files.writeString(legacyoutput, "changed-legacy-part", StandardCharsets.UTF_8)
        val changed = _build(project, "confirmation", runner)
        Files.deleteIfExists(legacyartifact)
        val deleted = _build(project, "confirmation", runner)

        Then("the cache accepts only exact current mode-local output provenance and deterministically recreates a missing record")
        hit should include("cache: hit")
        changed should include("cache: miss")
        deleted should include("cache: miss")
        Files.isRegularFile(legacyartifact) shouldBe true
        _string(_json(legacyartifact), "schema") shouldBe "cozy.video.storyboard-part-artifact.v1"
        _string(_json(legacyartifact), "mode") shouldBe "confirmation"
        _string(_json(legacyartifact), "partId") shouldBe "legacy"
        _string(_json(legacyartifact).hcursor.downField("output").focus.get, "path") shouldBe "build/parts/legacy.mp4"
        _string(_json(legacyartifact), "identity") should startWith("sha256:")
      }
    }

    "record project narration configuration in the Storyboard confirmation cache input" in {
      _with_temp_dir("narration-cache-input") { root =>
        Given("an approved Storyboard project with explicit reproducible narration configuration")
        val storyboard = _storyboard().copy(scenes = _storyboard().scenes.map(_.copy(
          pronunciationNotes = Vector(
            CozyVideo.StoryboardPronunciationNote("Cozy", "コージー"),
            CozyVideo.StoryboardPronunciationNote("Reimu", "れいむ")
          )
        )))
        val project = _write_storyboard_project(
          root,
          "storyboard.md",
          storyboard,
          configuration = """  "narration": {"provider": "voicevox"},
                            |  "voice": {"fallbackSpeakerId": 7},
                            |  "voiceTextNormalization": {"dictionary": {"Cozy": "コージー"}},
                            |  "characters": {"narrator": {"voice": {"fallbackSpeakerId": 9}}},
                            |""".stripMargin
        )
        val runner = new FakeRunner

        When("confirmation builds the projected Storyboard")
        _build(project, "confirmation", runner)
        val narration = _json(root.resolve("target/cozy-video/confirmation/manifest.json")).hcursor
          .downField("cacheInput").downField("narration").downArray

        Then("the cache records the exact projected provider, voice, and normalization input")
        narration.get[String]("provider").toOption shouldBe Some("voicevox")
        narration.downField("voice").get[Int]("fallbackSpeakerId").toOption shouldBe Some(7)
        narration.downField("narration").get[String]("provider").toOption shouldBe Some("voicevox")
        narration.downField("voiceTextNormalization").downField("dictionary").get[String]("Cozy").toOption shouldBe Some("コージー")
        narration.downField("storyboardPronunciationNotes").as[Vector[Json]].toOption.map(_.map { note =>
          _string(note, "surface") -> _string(note, "reading")
        }) shouldBe Some(Vector("Cozy" -> "コージー", "Reimu" -> "れいむ"))
      }
    }

    "preserve normalized Storyboard and handoff identity across equivalent Markdown and JSON source representations" in {
      _with_temp_dir("representation") { root =>
        Given("equivalent approved Markdown and JSON Storyboard projects")
        val storyboard = _storyboard()
        val markdownroot = root.resolve("markdown")
        val jsonroot = root.resolve("json")
        val markdownproject = _write_storyboard_project(markdownroot, "storyboard.md", storyboard)
        val jsonproject = _write_storyboard_project(jsonroot, "storyboard.json", storyboard)
        Files.writeString(jsonroot.resolve("storyboard.json"), CozyVideo.canonicalStoryboardJson(storyboard), StandardCharsets.UTF_8)
        val markdownrunner = new FakeRunner
        val jsonrunner = new FakeRunner

        When("each approved representation creates its confirmation handoff")
        _build(markdownproject, "confirmation", markdownrunner)
        _build(jsonproject, "confirmation", jsonrunner)
        val markdownhandoff = _json(markdownroot.resolve("target/cozy-video/storyboard/board/handoff.json"))
        val jsonhandoff = _json(jsonroot.resolve("target/cozy-video/storyboard/board/handoff.json"))

        Then("source filename and location do not change the normalized Storyboard or handoff identity")
        _string(markdownhandoff, "storyboardIdentity") shouldBe _string(jsonhandoff, "storyboardIdentity")
        _string(markdownhandoff, "identity") shouldBe _string(jsonhandoff, "identity")
      }
    }

    "prove an ordered Reimu then Marisa Storyboard flow through confirmation, final, and review evidence for Markdown and JSON" in {
      _with_temp_dir("reimu-marisa-representations") { root =>
        Given("equivalent two-scene Reimu then Marisa Storyboards in Markdown and JSON representations")
        val storyboard = _reimu_marisa_storyboard()
        val markdownroot = root.resolve("markdown")
        val jsonroot = root.resolve("json")
        val markdownproject = _write_storyboard_project(markdownroot, "storyboard.md", storyboard)
        val jsonproject = _write_storyboard_project(jsonroot, "storyboard.json", storyboard)
        val markdownrunner = new FakeRunner
        val jsonrunner = new FakeRunner

        When("both representations build confirmation, declare its exact identity, build final, and produce review evidence")
        _build(markdownproject, "confirmation", markdownrunner)
        _build(jsonproject, "confirmation", jsonrunner)
        val markdownconfirmationmanifest = markdownroot.resolve("target/cozy-video/confirmation/manifest.json")
        val jsonconfirmationmanifest = jsonroot.resolve("target/cozy-video/confirmation/manifest.json")
        val markdownconfirmationidentity = _string(_json(markdownconfirmationmanifest), "identity")
        val jsonconfirmationidentity = _string(_json(jsonconfirmationmanifest), "identity")
        _write_storyboard_project(markdownroot, "storyboard.md", storyboard, Some(markdownconfirmationidentity))
        _write_storyboard_project(jsonroot, "storyboard.json", storyboard, Some(jsonconfirmationidentity))
        _build(markdownproject, "final", markdownrunner)
        _build(jsonproject, "final", jsonrunner)
        val markdownplan = CozyVideoImplementation._plan(markdownproject, Some("host"), None)
        val jsonplan = CozyVideoImplementation._plan(jsonproject, Some("host"), None)
        _write_review_evidence_inputs(markdownplan)
        _write_review_evidence_inputs(jsonplan)
        CozyVideo.reviewEvidence(
          CozyVideo.ReviewEvidenceConfig(markdownproject, markdownroot.resolve("review"), toolMode = Some("host")),
          CozyVideo.VideoToolRegistry(Vector.empty),
          markdownrunner
        )
        CozyVideo.reviewEvidence(
          CozyVideo.ReviewEvidenceConfig(jsonproject, jsonroot.resolve("review"), toolMode = Some("host")),
          CozyVideo.VideoToolRegistry(Vector.empty),
          jsonrunner
        )

        Then("both generated handoffs and manifests preserve one normalized identity and ordered Reimu/Marisa scene speakers")
        val markdownhandoff = _json(markdownroot.resolve("target/cozy-video/storyboard/board/handoff.json"))
        val jsonhandoff = _json(jsonroot.resolve("target/cozy-video/storyboard/board/handoff.json"))
        val markdownfinalmanifestpath = markdownroot.resolve("target/cozy-video/final/manifest.json")
        val jsonfinalmanifestpath = jsonroot.resolve("target/cozy-video/final/manifest.json")
        val markdownfinalmanifest = _json(markdownfinalmanifestpath)
        val jsonfinalmanifest = _json(jsonfinalmanifestpath)
        val expectedidentity = CozyVideo.storyboardIdentity(storyboard)
        val expectedspeakers = Vector("reimu" -> "Reimu", "marisa" -> "Marisa")
        _json(markdownproject).hcursor.downField("confirmationReview").get[String]("approvedIdentity").toOption shouldBe
          Some(markdownconfirmationidentity)
        _json(jsonproject).hcursor.downField("confirmationReview").get[String]("approvedIdentity").toOption shouldBe
          Some(jsonconfirmationidentity)
        _string(_json(markdownconfirmationmanifest), "mode") shouldBe "confirmation"
        _string(_json(jsonconfirmationmanifest), "mode") shouldBe "confirmation"
        _string(markdownfinalmanifest, "mode") shouldBe "final"
        _string(jsonfinalmanifest, "mode") shouldBe "final"
        _string(markdownhandoff, "storyboardIdentity") shouldBe expectedidentity
        _string(jsonhandoff, "storyboardIdentity") shouldBe expectedidentity
        _string(markdownhandoff, "storyboardIdentity") shouldBe _string(jsonhandoff, "storyboardIdentity")
        _string(markdownhandoff, "identity") shouldBe _string(jsonhandoff, "identity")
        _scene_speakers(markdownhandoff) shouldBe expectedspeakers
        _scene_speakers(jsonhandoff) shouldBe expectedspeakers
        markdownfinalmanifest.hcursor.downField("storyboards").as[Vector[Json]].toOption.map(_.map { part =>
          _string(part, "storyboardIdentity")
        }) shouldBe Some(Vector(expectedidentity))
        jsonfinalmanifest.hcursor.downField("storyboards").as[Vector[Json]].toOption.map(_.map { part =>
          _string(part, "storyboardIdentity")
        }) shouldBe Some(Vector(expectedidentity))
        Files.isRegularFile(markdownroot.resolve("target/cozy-video/confirmation/confirmation.mp4")) shouldBe true
        Files.isRegularFile(jsonroot.resolve("target/cozy-video/confirmation/confirmation.mp4")) shouldBe true
        Files.isRegularFile(markdownroot.resolve("build/final.mp4")) shouldBe true
        Files.isRegularFile(jsonroot.resolve("build/final.mp4")) shouldBe true
        Files.isRegularFile(markdownconfirmationmanifest) shouldBe true
        Files.isRegularFile(jsonconfirmationmanifest) shouldBe true
        (markdownconfirmationmanifest == markdownfinalmanifestpath) shouldBe false
        (jsonconfirmationmanifest == jsonfinalmanifestpath) shouldBe false
        val markdownreview = _json(markdownroot.resolve("review/review-manifest.json"))
        val jsonreview = _json(jsonroot.resolve("review/review-manifest.json"))
        markdownreview.hcursor.downField("videoManifest").get[String]("path").toOption.map { path =>
          Files.isSameFile(Path.of(path), markdownfinalmanifestpath)
        } shouldBe Some(true)
        jsonreview.hcursor.downField("videoManifest").get[String]("path").toOption.map { path =>
          Files.isSameFile(Path.of(path), jsonfinalmanifestpath)
        } shouldBe Some(true)
        Files.exists(markdownroot.resolve("script.json")) shouldBe false
        Files.exists(jsonroot.resolve("script.json")) shouldBe false
        Files.exists(markdownroot.resolve("target/cozy-video/storyboard/board/script.json")) shouldBe false
        Files.exists(jsonroot.resolve("target/cozy-video/storyboard/board/script.json")) shouldBe false
      }
    }

    "project each selected Storyboard section into its own handoff while retaining approval provenance" in {
      _with_temp_dir("section-handoffs") { root =>
        Given("one approved source Storyboard with opening and summary sections selected by separate parts")
        val storyboard = _sectioned_storyboard()
        val project = _write_sectioned_storyboard_project(root, storyboard)
        val runner = new FakeRunner

        When("confirmation builds both selected Storyboard parts")
        _build(project, "confirmation", runner)
        val opening = _json(root.resolve("target/cozy-video/storyboard/opening/handoff.json"))
        val summary = _json(root.resolve("target/cozy-video/storyboard/summary/handoff.json"))

        Then("each handoff contains only its selected scenes and retains the complete approved source identity")
        val sourceidentity = CozyVideo.storyboardIdentity(storyboard)
        _string(opening, "storyboardIdentity") shouldBe sourceidentity
        _string(summary, "storyboardIdentity") shouldBe sourceidentity
        _string(opening, "projectedStoryboardIdentity") shouldBe CozyVideo.storyboardIdentity(storyboard.copy(scenes = Vector(storyboard.scenes.head)))
        _string(summary, "projectedStoryboardIdentity") shouldBe CozyVideo.storyboardIdentity(storyboard.copy(scenes = Vector(storyboard.scenes(1))))
        _string(opening, "storyboardSection") shouldBe "opening"
        _string(summary, "storyboardSection") shouldBe "summary"
        _scene_ids(opening) shouldBe Vector("opening")
        _scene_ids(summary) shouldBe Vector("summary")
        _scene_ids(opening) should not contain "summary"
        _scene_ids(summary) should not contain "opening"
      }
    }
  }

  private def _build(project: Path, mode: String, runner: FakeRunner): String =
    CozyVideo.build(
      CozyVideo.BuildConfig(project, dryRun = false, checkTools = false, toolMode = Some("host"), mode = Some(mode)),
      CozyVideo.VideoToolRegistry(Vector.empty),
      runner
    )

  private def _write_storyboard_project(
    root: Path,
    source: String,
    storyboard: CozyVideo.Storyboard,
    confirmationidentity: Option[String] = None,
    renderer: Option[String] = None,
    configuration: String = "",
    outputpath: String = "build/final.mp4",
    partoutputpath: String = "build/parts/board.mp4"
  ): Path = {
    Files.createDirectories(root)
    val sourcepath = root.resolve(source)
    val content =
      if (source.endsWith(".json")) CozyVideo.canonicalStoryboardJson(storyboard)
      else CozyVideo.canonicalStoryboardMarkdown(storyboard)
    Files.writeString(sourcepath, content, StandardCharsets.UTF_8)
    val partoutput = root.resolve(partoutputpath)
    Files.createDirectories(partoutput.getParent)
    Files.writeString(partoutput, "pre-rendered-storyboard-part", StandardCharsets.UTF_8)
    val confirmation = confirmationidentity.map(identity => s""",
         |  "confirmationReview": {"approvedIdentity": "$identity"}""".stripMargin).getOrElse("")
    val rendererentry = renderer.map(value => s""",
         |  "renderer": {"engine": "$value"}""".stripMargin).getOrElse("")
    val project = root.resolve("video.json")
    Files.writeString(
      project,
      s"""{
         |$configuration  "output": "$outputpath",
         |  "storyboardReview": {
         |    "source": "$source",
         |    "approvedIdentity": "${CozyVideo.storyboardIdentity(storyboard)}"
         |  },
         |  "parts": [
         |    {"id": "board", "type": "storyboard", "storyboard": "$source", "output": "$partoutputpath"}
         |  ]$rendererentry$confirmation
         |}
         |""".stripMargin,
      StandardCharsets.UTF_8
    )
    project
  }

  private def _write_legacy_project(root: Path): Path = {
    Files.createDirectories(root)
    val script = root.resolve("script.json")
    val output = root.resolve("build/parts/legacy.mp4")
    Files.createDirectories(output.getParent)
    Files.writeString(script, """{"scenes":[{"id":"legacy","line":"Legacy"}]}""", StandardCharsets.UTF_8)
    Files.writeString(output, "pre-rendered-legacy-part", StandardCharsets.UTF_8)
    val project = root.resolve("video.json")
    Files.writeString(
      project,
      """{
        |  "output": "build/final.mp4",
        |  "parts": [
        |    {"id": "legacy", "type": "dialogue", "script": "script.json", "output": "build/parts/legacy.mp4"}
        |  ]
        |}
        |""".stripMargin,
      StandardCharsets.UTF_8
    )
    project
  }

  private def _write_web_demo_storyboard_project(root: Path, storyboard: CozyVideo.Storyboard): Path = {
    Files.createDirectories(root)
    Files.writeString(root.resolve("storyboard.md"), CozyVideo.canonicalStoryboardMarkdown(storyboard), StandardCharsets.UTF_8)
    Files.writeString(root.resolve("steps.json"), "{\"steps\":[]}", StandardCharsets.UTF_8)
    val recorddir = root.resolve("build/record/board")
    Files.createDirectories(recorddir)
    Files.writeString(recorddir.resolve("recording.txt"), "initial recording", StandardCharsets.UTF_8)
    val output = root.resolve("build/parts/board.mp4")
    Files.createDirectories(output.getParent)
    Files.writeString(output, "pre-rendered-storyboard-part", StandardCharsets.UTF_8)
    val project = root.resolve("video.json")
    Files.writeString(
      project,
      s"""{
         |  "output": "build/final.mp4",
         |  "storyboardReview": {"source": "storyboard.md", "approvedIdentity": "${CozyVideo.storyboardIdentity(storyboard)}"},
         |  "parts": [
         |    {"id": "board", "type": "web-demo", "storyboard": "storyboard.md", "steps": "steps.json", "recordDir": "build/record/board", "output": "build/parts/board.mp4"}
         |  ]
         |}
         |""".stripMargin,
      StandardCharsets.UTF_8
    )
    project
  }

  private def _write_sectioned_storyboard_project(root: Path, storyboard: CozyVideo.Storyboard): Path = {
    Files.createDirectories(root)
    Files.writeString(root.resolve("storyboard.md"), CozyVideo.canonicalStoryboardMarkdown(storyboard), StandardCharsets.UTF_8)
    Vector("opening", "summary").foreach { id =>
      val output = root.resolve(s"build/parts/$id.mp4")
      Files.createDirectories(output.getParent)
      Files.writeString(output, s"pre-rendered-storyboard-$id", StandardCharsets.UTF_8)
    }
    val project = root.resolve("video.json")
    Files.writeString(
      project,
      s"""{
         |  "output": "build/final.mp4",
         |  "storyboardReview": {
         |    "source": "storyboard.md",
         |    "approvedIdentity": "${CozyVideo.storyboardIdentity(storyboard)}"
         |  },
         |  "parts": [
         |    {"id": "opening", "type": "storyboard", "storyboard": "storyboard.md", "storyboardSection": "opening", "output": "build/parts/opening.mp4"},
         |    {"id": "summary", "type": "storyboard", "storyboard": "storyboard.md", "storyboardSection": "summary", "output": "build/parts/summary.mp4"}
         |  ]
         |}
         |""".stripMargin,
      StandardCharsets.UTF_8
    )
    project
  }

  private def _write_review_evidence_inputs(plan: CozyVideo.VideoPlan): Unit = {
    val part = plan.parts.head
    val encoding = plan.encoding
    val scenes = part.script.get.scenes
    val sceneids = scenes.map(_.id.get)
    val totalframes = sceneids.size * encoding.fps
    Files.createDirectories(part.audioDir.get)
    Files.writeString(
      part.audioDir.get.resolve("manifest.json"),
      Json.fromValues(sceneids.zipWithIndex.map { case (sceneid, index) =>
        Json.obj(
          "sceneId" -> Json.fromString(sceneid),
          "speaker" -> Json.Null,
          "file" -> Json.fromString(f"${index + 1}%02d.wav"),
          "leadSilence" -> Json.fromDoubleOrNull(0.0),
          "audioDuration" -> Json.fromDoubleOrNull(1.0),
          "targetDuration" -> Json.fromDoubleOrNull(1.0),
          "tailSilence" -> Json.fromDoubleOrNull(0.0)
        )
      }).noSpaces,
      StandardCharsets.UTF_8
    )
    val props = Json.obj(
      "partId" -> Json.fromString(part.id),
      "encodingPolicy" -> Json.fromString(encoding.policy.name),
      "fps" -> Json.fromInt(encoding.fps),
      "width" -> Json.fromInt(encoding.width),
      "height" -> Json.fromInt(encoding.height),
      "crf" -> Json.fromInt(encoding.crf),
      "x264Preset" -> encoding.x264Preset.map(Json.fromString).getOrElse(Json.Null),
      "timing" -> Json.obj(
        "openingFrames" -> Json.fromInt(0),
        "contentFrames" -> Json.fromInt(totalframes),
        "summaryStartFrame" -> Json.fromInt(totalframes),
        "summaryFrames" -> Json.fromInt(0),
        "finalPageStartFrame" -> Json.fromInt(totalframes),
        "finalPageHoldFrames" -> Json.fromInt(0),
        "totalFrames" -> Json.fromInt(totalframes)
      ),
      "scenes" -> Json.fromValues(sceneids.zipWithIndex.map { case (sceneid, index) =>
        Json.obj(
          "id" -> Json.fromString(sceneid),
          "startFrame" -> Json.fromInt(index * encoding.fps),
          "durationFrames" -> Json.fromInt(encoding.fps),
          "leadInFrames" -> Json.fromInt(0),
          "sectionTransitionFrames" -> Json.fromInt(0)
        )
      })
    )
    val propspath = plan.projectRoot.resolve("target/cozy-video/remotion").resolve(part.id).resolve("props.json")
    Files.createDirectories(propspath.getParent)
    Files.writeString(propspath, props.noSpaces, StandardCharsets.UTF_8)
  }

  private def _storyboard(): CozyVideo.Storyboard =
    CozyVideo.Storyboard(
      "cozy.video.storyboard.v1",
      1,
      Vector(
        CozyVideo.StoryboardScene(
          "opening",
          1,
          "opening",
          "narrator",
          "narration",
          "Welcome to Cozy.",
          CozyVideo.StoryboardScreen("Welcome", "Cozy video modes"),
          "Welcome",
          BigDecimal("3.5"),
          BigDecimal("0.25"),
          "fade",
          Vector.empty,
          Vector.empty,
          Vector.empty,
          Vector.empty,
          "Show the opening"
        )
      )
    )

  private def _sectioned_storyboard(): CozyVideo.Storyboard = {
    val opening = _storyboard().scenes.head
    val summary = opening.copy(
      id = "summary",
      order = 2,
      section = "summary",
      narration = "Cozy summarizes the result.",
      screen = CozyVideo.StoryboardScreen("Summary", "Cozy video modes summary"),
      caption = "Summary",
      direction = "Show the summary"
    )
    _storyboard().copy(scenes = Vector(opening, summary))
  }

  private def _reimu_marisa_storyboard(): CozyVideo.Storyboard = {
    val reimu = _storyboard().scenes.head.copy(
      id = "reimu",
      section = "conversation",
      speaker = "Reimu",
      role = "dialogue",
      narration = "Reimu opens the conversation.",
      screen = CozyVideo.StoryboardScreen("Reimu", "The conversation begins."),
      caption = "Reimu",
      direction = "Show Reimu first"
    )
    val marisa = reimu.copy(
      id = "marisa",
      order = 2,
      speaker = "Marisa",
      narration = "Marisa follows with the answer.",
      screen = CozyVideo.StoryboardScreen("Marisa", "The answer follows."),
      caption = "Marisa",
      direction = "Show Marisa second"
    )
    _storyboard().copy(scenes = Vector(reimu, marisa))
  }

  private def _json(path: Path): Json =
    parser.parse(Files.readString(path, StandardCharsets.UTF_8)).fold(
      error => throw new IllegalArgumentException(error.getMessage),
      identity
    )

  private def _json_text(text: String): Json =
    parser.parse(text).fold(
      error => throw new IllegalArgumentException(error.getMessage),
      identity
    )

  private def _string(json: Json, field: String): String =
    json.hcursor.downField(field).as[String].fold(
      error => throw new IllegalArgumentException(error.getMessage),
      identity
    )

  private def _scene_ids(json: Json): Vector[String] =
    json.hcursor.downField("storyboard").downField("scenes").as[Vector[Json]].fold(
      error => throw new IllegalArgumentException(error.getMessage),
      _.map(scene => _string(scene, "id"))
    )

  private def _scene_speakers(json: Json): Vector[(String, String)] =
    json.hcursor.downField("storyboard").downField("scenes").as[Vector[Json]].fold(
      error => throw new IllegalArgumentException(error.getMessage),
      _.map(scene => _string(scene, "id") -> _string(scene, "speaker"))
    )

  private def _with_temp_dir[A](label: String)(body: Path => A): A = {
    val root = Files.createTempDirectory("cozy-video-storyboard-mode-" + label)
    try body(root)
    finally _delete(root)
  }

  private def _delete(path: Path): Unit =
    if (Files.exists(path))
      Files.walk(path).iterator().asScala.toVector.sortBy(_.getNameCount).reverse.foreach(Files.deleteIfExists)

  private final class FakeRunner extends CozyVideo.VideoProcessRunner {
    private val _commands = ArrayBuffer.empty[Vector[String]]

    def commandCount: Int = _commands.size

    def run(args: Vector[String], cwd: Path): CozyVideo.VideoCommandResult = {
      _commands += args
      if (args.contains("ffmpeg")) {
        val output = Path.of(args.last)
        Files.createDirectories(output.getParent)
        Files.writeString(output, s"fake-mp4-${_commands.size}", StandardCharsets.UTF_8)
        CozyVideo.VideoCommandResult(0, "ffmpeg ok", "")
      } else if (args.contains("ffprobe")) {
        CozyVideo.VideoCommandResult(0, """{"format":{"duration":"1.000"},"streams":[]}""", "")
      } else {
        CozyVideo.VideoCommandResult(0, "ok", "")
      }
    }
  }
}
