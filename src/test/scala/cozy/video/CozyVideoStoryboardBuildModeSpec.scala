package cozy.video

import cozy.CozySpecVocabulary
import io.circe.Json
import io.circe.parser
import java.nio.charset.StandardCharsets
import java.nio.file.attribute.{FileTime, PosixFilePermission}
import java.nio.file.{Files, Path}
import org.scalacheck.{Gen, Prop, Test}
import org.scalatest.GivenWhenThen
import org.scalatest.wordspec.AnyWordSpec
import scala.collection.JavaConverters._
import scala.collection.mutable.ArrayBuffer

/*
 * @since   Aug. 26, 2026
 * @version Sep. 29, 2026
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

    "preserve Storyboard v1 and v2 identity while leaving generated legacy VideoScene tail silence unset" in {
      Given("typed v1 and v2 Storyboards whose scene timing is converted for the legacy video renderer")
      val v1 = _storyboard()
      val v2 = v1.copy(schema = "cozy.video.storyboard.v2", version = 2)
      val v1identity = CozyVideo.storyboardIdentity(v1)
      val v2identity = CozyVideo.storyboardIdentity(v2)

      When("the Storyboard scenes are projected into VideoScene values")
      val v1scene = CozyVideoImplementation._storyboard_video_scene(v1.scenes.head)
      val v2scene = CozyVideoImplementation._storyboard_video_scene(v2.scenes.head)

      Then("the source Storyboard schemas and identities remain unchanged, and effective tail timing is not invented in their legacy schema")
      v1.schema shouldBe "cozy.video.storyboard.v1"
      v1.version shouldBe 1
      v2.schema shouldBe "cozy.video.storyboard.v2"
      v2.version shouldBe 2
      CozyVideo.storyboardIdentity(v1) shouldBe v1identity
      CozyVideo.storyboardIdentity(v2) shouldBe v2identity
      v1scene.tailSilence shouldBe None
      v2scene.tailSilence shouldBe None
      v1scene.duration shouldBe Some(v1.scenes.head.duration.toDouble)
      v2scene.duration shouldBe Some(v2.scenes.head.duration.toDouble)
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
        _string(_json(handoff), "schema") shouldBe "cozy.video.storyboard-build-handoff.v2"
        _string(_json(handoff), "sourcePath") shouldBe "storyboard.md"
        _json(handoff).hcursor.downField("storyboardIdentity").focus shouldBe None
        _string(_json(manifest), "schema") shouldBe "cozy.video.confirmation.v2"
        Files.isRegularFile(confirmation) shouldBe true
        Files.exists(root.resolve("build/final.mp4")) shouldBe false
      }
    }

    "build final without confirmation and ignore well-formed stale confirmation approval metadata" in {
      _with_temp_dir("final-approval") { root =>
        Given("a Storyboard project with no confirmation artifact and an optional stale confirmation declaration")
        val storyboard = _storyboard()
        val project = _write_storyboard_project(root, "storyboard.md", storyboard)
        val runner = new FakeRunner

        When("final is built without confirmation and again with a mismatched optional confirmationReview")
        val first = _build(project, "final", runner)
        _write_storyboard_project(root, "storyboard.md", storyboard, Some("sha256:" + "0" * 64))
        val second = _build(project, "final", runner)

        Then("both final builds succeed without manufacturing confirmation artifacts")
        first should include("mode: final")
        second should include("mode: final")
        Files.isRegularFile(root.resolve("build/final.mp4")) shouldBe true
        _string(_json(root.resolve("target/cozy-video/final/manifest.json")), "schema") shouldBe "cozy.video.final.v2"
        Files.exists(root.resolve("target/cozy-video/confirmation")) shouldBe false
      }
    }

    "use only the canonical final Storyboard manifest for completed video review evidence" in {
      _with_temp_dir("final-review-evidence") { root =>
        Given("a final Storyboard build without a confirmation prerequisite or legacy project manifest")
        val storyboard = _storyboard()
        val project = _write_storyboard_project(root, "storyboard.md", storyboard)
        val runner = new FakeRunner
        val plan = CozyVideoImplementation._plan(project, Some("host"), None)
        _write_review_evidence_inputs(plan)
        _build(project, "final", runner)

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
        finalmanifest.hcursor.downField("output").downField("sha256").focus shouldBe None
        manifest.hcursor.downField("finalVideo").get[String]("sha256").toOption.nonEmpty shouldBe true
        Files.exists(root.resolve("build/manifest.json")) shouldBe false
      }
    }

    "accept current native final metadata and reject a newer declared source without runner work" in {
      _with_temp_dir("final-currentness") { root =>
        Given("review inputs prepared before a completed final Storyboard build")
        val project = _write_storyboard_project(root, "storyboard.md", _storyboard())
        val runner = new FakeRunner
        val plan = CozyVideoImplementation._plan(project, Some("host"), None)
        _write_review_evidence_inputs(plan)
        _build(project, "final", runner)
        val finalvideo = root.resolve("build/final.mp4")
        val before = runner.commandCount

        When("native final metadata is checked while current and after its declared Storyboard becomes newer")
        val current = CozyVideoImplementation._validated_storyboard_final_manifest(plan, finalvideo)
        Files.setLastModifiedTime(
          root.resolve("storyboard.md"),
          FileTime.fromMillis(Files.getLastModifiedTime(finalvideo).toMillis + 2000L)
        )
        val stale = intercept[Exception] {
          CozyVideoImplementation._validated_storyboard_final_manifest(plan, finalvideo)
        }

        Then("the current v2 manifest is accepted, stale metadata is rejected, and validation starts no process")
        current shouldBe root.resolve("target/cozy-video/final/manifest.json")
        stale.getMessage should include("Storyboard final manifest is incomplete, stale, or noncanonical")
        runner.commandCount shouldBe before
      }
    }

    "reject incomplete, noncanonical, and symlinked Storyboard final manifests before review evidence" in {
      _with_temp_dir("final-manifest-trust") { root =>
        Given("a completed Storyboard final build and its required review inputs")
        val storyboard = _storyboard()
        val project = _write_storyboard_project(root, "storyboard.md", storyboard)
        val runner = new FakeRunner
        val plan = CozyVideoImplementation._plan(project, Some("host"), None)
        _write_review_evidence_inputs(plan)
        _build(project, "final", runner)
        val finaldir = root.resolve("target/cozy-video/final")
        val finalmanifest = finaldir.resolve("manifest.json")
        val canonical = Files.readString(finalmanifest, StandardCharsets.UTF_8)

        When("review evidence receives incomplete, noncanonical, and symlinked final manifest inputs")
        Files.writeString(finalmanifest, """{"schema":"cozy.video.final.v1"}""", StandardCharsets.UTF_8)
        val incomplete = intercept[Exception] {
          CozyVideo.reviewEvidence(CozyVideo.ReviewEvidenceConfig(project, root.resolve("review-incomplete"), toolMode = Some("host")), CozyVideo.VideoToolRegistry(Vector.empty), runner)
        }
        Files.writeString(finalmanifest, _json_text(canonical).mapObject(_.add("mode", Json.fromString("confirmation"))).noSpaces, StandardCharsets.UTF_8)
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

    "ignore an unrelated symlinked confirmation subtree while final remains independent" in {
      _with_temp_dir("confirmation-ancestor") { root =>
        Given("an outside confirmation sentinel linked only from the confirmation subtree")
        val project = _write_storyboard_project(root, "storyboard.md", _storyboard())
        val runner = new FakeRunner
        val outside = root.resolve("outside-confirmation")
        Files.createDirectories(outside)
        val sentinel = outside.resolve("sentinel.txt")
        Files.writeString(sentinel, "outside-bytes", StandardCharsets.UTF_8)
        val bytes = Files.readAllBytes(sentinel)
        val modified = Files.getLastModifiedTime(sentinel)
        val permissions = Files.getPosixFilePermissions(sentinel)
        val confirmation = root.resolve("target/cozy-video/confirmation")
        Files.createDirectories(confirmation.getParent)
        Files.createSymbolicLink(confirmation, outside)

        When("final builds without reading or replacing the optional confirmation subtree")
        val result = _build(project, "final", runner)

        Then("final succeeds and the unrelated link and outside sentinel remain unchanged")
        result should include("mode: final")
        Files.isSymbolicLink(confirmation) shouldBe true
        Files.readAllBytes(sentinel) shouldBe bytes
        Files.getLastModifiedTime(sentinel) shouldBe modified
        Files.getPosixFilePermissions(sentinel) shouldBe permissions
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

        Then("the current cache is read-only and probes once while an effective setting change reruns assembly")
        hit should include("cache: hit")
        hitcount shouldBe firstcount + 1
        miss should include("cache: miss")
        runner.commandCount shouldBe firstcount + 3
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

    "invalidate a current native record when its narration provider, voice, or normalization configuration changes" in {
      _with_temp_dir("narration-configuration-cache") { root =>
        Given("a confirmation record whose project narration configuration is recorded")
        val storyboard = _storyboard()
        val initial = """  "narration": {"provider": "voicevox", "style": "first"},
                        |  "voice": {"fallbackSpeakerId": 7},
                        |  "voiceTextNormalization": {"dictionary": {"Cozy": "first"}},
                        |""".stripMargin
        val project = _write_storyboard_project(root, "storyboard.md", storyboard, configuration = initial)
        val runner = new FakeRunner
        _build(project, "confirmation", runner)
        val output = root.resolve("target/cozy-video/confirmation/confirmation.mp4")
        val beforemux = runner.muxCount

        When("provider, voice, and normalization change while the project FileTime is equal to the current output")
        Files.writeString(
          project,
          Files.readString(project, StandardCharsets.UTF_8)
            .replace("voicevox", "macos-say")
            .replace("\"first\"", "\"second\"")
            .replace("fallbackSpeakerId\": 7", "fallbackSpeakerId\": 8"),
          StandardCharsets.UTF_8
        )
        Files.setLastModifiedTime(project, FileTime.fromMillis(Files.getLastModifiedTime(output).toMillis))
        val changed = _build(project, "confirmation", runner)

        Then("configuration disagreement regenerates independently of older-or-equal declared FileTime checks")
        changed should include("cache: miss")
        runner.muxCount shouldBe beforemux + 1
        val narration = _json(root.resolve("target/cozy-video/confirmation/manifest.json")).hcursor.downField("narration").downArray
        narration.get[String]("provider").toOption shouldBe Some("macos-say")
        narration.downField("voice").get[Int]("fallbackSpeakerId").toOption shouldBe Some(8)
        narration.downField("voiceTextNormalization").downField("dictionary").get[String]("Cozy").toOption shouldBe Some("second")
      }
    }

    "use deterministic FileTime ordering for native reuse without mutating a current product" in {
      val deltas = Gen.oneOf(-2000L, 0L, 2000L)
      val property = Prop.forAll(deltas) { delta =>
        _with_temp_dir("filetime-order") { root =>
          Given("a completed confirmation product with explicitly controlled Storyboard modification time")
          val project = _write_storyboard_project(root, "storyboard.md", _storyboard())
          val runner = new FakeRunner
          _build(project, "confirmation", runner)
          val output = root.resolve("target/cozy-video/confirmation/confirmation.mp4")
          val beforetime = Files.getLastModifiedTime(output)
          val products = _native_products(root, "confirmation")
          val before = _artifact_snapshot(products)
          val beforemux = runner.muxCount
          val beforeprobe = runner.probeCount
          Files.setLastModifiedTime(root.resolve("storyboard.md"), FileTime.fromMillis(beforetime.toMillis + delta))

          When("the native mode is selected with an older, equal, or newer declared source")
          val result = _build(project, "confirmation", runner)

          Then("only a newer declared source regenerates; older or equal source reuse is read-only apart from the allowed probe")
          if (delta > 0L)
            result.contains("cache: miss")
          else
            result.contains("cache: hit") && _same_artifact_snapshot(before) &&
              runner.muxCount == beforemux && runner.probeCount == beforeprobe + 1
        }
      }

      When("ScalaCheck evaluates at least ten timestamp order variations")
      val result = Test.check(Test.Parameters.default.withMinSuccessfulTests(10), property)

      Then("every newer/equal/older decision preserves the declared FileTime contract")
      withClue(s"ScalaCheck status=${result.status}, succeeded=${result.succeeded}, discarded=${result.discarded}") {
        result.passed shouldBe true
      }
    }

    "use FileTime rather than source-body equality for current native products" in {
      val content = Gen.alphaStr.suchThat(_.nonEmpty)
      val property = Prop.forAll(content, Gen.oneOf(-2000L, 0L)) { (suffix, delta) =>
        _with_temp_dir("filetime-content") { root =>
          Given("a completed confirmation product and a different but older-or-equal valid Storyboard body")
          val project = _write_storyboard_project(root, "storyboard.md", _storyboard())
          val runner = new FakeRunner
          _build(project, "confirmation", runner)
          val output = root.resolve("target/cozy-video/confirmation/confirmation.mp4")
          val before = _artifact_snapshot(_native_products(root, "confirmation"))
          val beforemux = runner.muxCount
          val beforeprobe = runner.probeCount
          val changed = _storyboard().copy(scenes = _storyboard().scenes.map(_.copy(narration = s"Changed $suffix")))
          Files.writeString(root.resolve("storyboard.md"), CozyVideo.canonicalStoryboardMarkdown(changed), StandardCharsets.UTF_8)
          Files.setLastModifiedTime(root.resolve("storyboard.md"), FileTime.fromMillis(Files.getLastModifiedTime(output).toMillis + delta))

          When("confirmation observes the changed source at an older-or-equal declared FileTime")
          val result = _build(project, "confirmation", runner)

          Then("the product is reused without a mux or product write while the allowed probe runs once")
          result.contains("cache: hit") && _same_artifact_snapshot(before) &&
            runner.muxCount == beforemux && runner.probeCount == beforeprobe + 1
        }
      }

      When("ScalaCheck evaluates at least ten content and timestamp variations")
      val result = Test.check(Test.Parameters.default.withMinSuccessfulTests(10), property)

      Then("native currentness remains FileTime-based rather than source-body-equality based")
      withClue(s"ScalaCheck status=${result.status}, succeeded=${result.succeeded}, discarded=${result.discarded}") {
        result.passed shouldBe true
      }
    }

    "use actual ProjectContext layer FileTime for current native products" in {
      _with_temp_dir("project-context-filetime") { root =>
        Given("a marked project with a declared project-context configuration file")
        val contextfile = root.resolve("conf/cozy/config.yaml")
        Files.createDirectories(contextfile.getParent)
        Files.writeString(contextfile, "video:\n  currentness: native\n", StandardCharsets.UTF_8)
        val project = _write_storyboard_project(root, "storyboard.md", _storyboard())
        val runner = new FakeRunner
        _build(project, "confirmation", runner)
        val output = root.resolve("target/cozy-video/confirmation/confirmation.mp4")
        val before = _artifact_snapshot(_native_products(root, "confirmation"))
        val beforemux = runner.muxCount
        val beforeprobe = runner.probeCount
        Files.setLastModifiedTime(contextfile, FileTime.fromMillis(Files.getLastModifiedTime(output).toMillis - 2000L))

        When("the declared context file is older, equal, and then newer than the completed confirmation output")
        val older = _build(project, "confirmation", runner)
        val olderunchanged = _same_artifact_snapshot(before)
        val oldermux = runner.muxCount
        val olderprobe = runner.probeCount
        Files.setLastModifiedTime(contextfile, FileTime.fromMillis(Files.getLastModifiedTime(output).toMillis))
        val equal = _build(project, "confirmation", runner)
        val equalunchanged = _same_artifact_snapshot(before)
        val equalmux = runner.muxCount
        val equalprobe = runner.probeCount
        Files.setLastModifiedTime(contextfile, FileTime.fromMillis(Files.getLastModifiedTime(output).toMillis + 2000L))
        val stale = _build(project, "confirmation", runner)
        val stalemux = runner.muxCount

        Then("older and equal context files reuse without a write, while a newer context file regenerates")
        older should include("cache: hit")
        olderunchanged shouldBe true
        oldermux shouldBe beforemux
        olderprobe shouldBe beforeprobe + 1
        equal should include("cache: hit")
        equalunchanged shouldBe true
        equalmux shouldBe beforemux
        equalprobe shouldBe beforeprobe + 2
        stale should include("cache: miss")
        stalemux shouldBe beforemux + 1
      }
    }

    "regenerate stale or missing native metadata while ignoring an ordinary current-record extra" in {
      _with_temp_dir("native-record-currentness") { root =>
        Given("a completed confirmation product with every native mode record")
        val project = _write_storyboard_project(root, "storyboard.md", _storyboard())
        val runner = new FakeRunner
        _build(project, "confirmation", runner)
        val output = root.resolve("target/cozy-video/confirmation/confirmation.mp4")
        val manifest = root.resolve("target/cozy-video/confirmation/manifest.json")
        val handoff = root.resolve("target/cozy-video/storyboard/board/handoff.json")
        val artifact = root.resolve("target/cozy-video/confirmation/part-artifacts/board.json")

        When("the mode record is old-schema, then current with an ordinary extra, then a required record is missing or older than its declared input")
        Files.writeString(manifest, "{\"schema\":\"cozy.video.confirmation.v1\"}", StandardCharsets.UTF_8)
        val oldschema = _build(project, "confirmation", runner)
        val extra = _json(manifest).mapObject(_.add("ordinaryExtra", Json.fromString("retained"))).noSpaces
        Files.writeString(manifest, extra, StandardCharsets.UTF_8)
        val unrelated = root.resolve("target/cozy-video/confirmation/unrelated.txt")
        Files.writeString(unrelated, "unrelated", StandardCharsets.UTF_8)
        val beforecurrent = _artifact_snapshot(_native_products(root, "confirmation"))
        val beforemux = runner.muxCount
        val current = _build(project, "confirmation", runner)
        val currentunchanged = _same_artifact_snapshot(beforecurrent)
        Files.delete(artifact)
        val deleted = _build(project, "confirmation", runner)
        Files.setLastModifiedTime(manifest, FileTime.fromMillis(Files.getLastModifiedTime(root.resolve("storyboard.md")).toMillis - 2000L))
        val stale = _build(project, "confirmation", runner)

        Then("v1, missing, and stale required records regenerate, while a current ordinary extra causes only the allowed probe and no product write")
        oldschema should include("cache: miss")
        current should include("cache: hit")
        currentunchanged shouldBe true
        runner.muxCount should be >= (beforemux + 2)
        Files.readString(unrelated, StandardCharsets.UTF_8) shouldBe "unrelated"
        deleted should include("cache: miss")
        stale should include("cache: miss")
        Files.isRegularFile(handoff) shouldBe true
        Files.isRegularFile(artifact) shouldBe true
        Files.isRegularFile(output) shouldBe true
      }
    }

    "invalidate an unchanged confirmation cache when a renderable part input or type changes" in {
      _with_temp_dir("part-input-cache") { root =>
        Given("a web-demo Storyboard part with direct steps and recording inputs")
        val storyboard = _storyboard()
        val project = _write_web_demo_storyboard_project(root, storyboard)
        val runner = new FakeRunner

        When("the same confirmation is reused, then its steps, recording child, recording-child deletion, and effective part type change")
        _build(project, "confirmation", runner)
        val firstcount = runner.commandCount
        val hit = _build(project, "confirmation", runner)
        Files.writeString(root.resolve("steps.json"), "{\"steps\":[{\"kind\":\"click\"}]}", StandardCharsets.UTF_8)
        val stepsmiss = _build(project, "confirmation", runner)
        Files.writeString(root.resolve("build/record/board/recording.txt"), "changed recording", StandardCharsets.UTF_8)
        val recordingmiss = _build(project, "confirmation", runner)
        Files.delete(root.resolve("build/record/board/recording.txt"))
        Files.setLastModifiedTime(
          root.resolve("build/record/board"),
          FileTime.fromMillis(Files.getLastModifiedTime(root.resolve("target/cozy-video/confirmation/confirmation.mp4")).toMillis + 2000L)
        )
        val deletionmiss = _build(project, "confirmation", runner)
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
        deletionmiss should include("cache: miss")
        typemiss should include("cache: miss")
        runner.commandCount shouldBe firstcount + 9
      }
    }

    "treat selected Storyboard assets and selected credit profile configuration as declared native inputs" in {
      _with_temp_dir("asset-credit-inputs") { root =>
        Given("a Storyboard with a direct selected asset and a selected direct credit profile")
        Files.writeString(root.resolve("diagram.svg"), "diagram", StandardCharsets.UTF_8)
        _write_credit_profile(root)
        val storyboard = _storyboard().copy(scenes = _storyboard().scenes.map(_.copy(assetRefs = Vector("diagram.svg"))))
        val project = _write_storyboard_project(
          root,
          "storyboard.md",
          storyboard,
          configuration = "  \"credits\": {\"profile\": \"native-test\", \"include\": [\"native-test-credit\"], \"exclude\": []},\n"
        )
        val runner = new FakeRunner
        _build(project, "confirmation", runner)
        val output = root.resolve("target/cozy-video/confirmation/confirmation.mp4")

        When("the selected asset and then the selected credit profile receive newer deterministic FileTimes")
        Files.setLastModifiedTime(root.resolve("diagram.svg"), FileTime.fromMillis(Files.getLastModifiedTime(output).toMillis + 2000L))
        val assetmiss = _build(project, "confirmation", runner)
        val profile = root.resolve("conf/cozy/video/credit-profiles/native-test.yaml")
        Files.setLastModifiedTime(profile, FileTime.fromMillis(Files.getLastModifiedTime(output).toMillis + 4000L))
        val creditmiss = _build(project, "confirmation", runner)

        Then("each actual selected input invalidates every required product and rewrites the selected credit files only through staging")
        assetmiss should include("cache: miss")
        creditmiss should include("cache: miss")
        _credit_products(root, "confirmation").foreach { path =>
          Files.isRegularFile(path) shouldBe true
        }
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
        failure.getMessage should include("generation input is unavailable")
        failure.getMessage should include("InvalidRequiredInput")
        failure.getMessage should include("board.mp4")
        failure.getMessage should include("symbolic link")
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
        failure.getMessage should include("generation input is unavailable")
        failure.getMessage should include("InvalidRequiredInput")
        failure.getMessage should include("external-part-link")
        failure.getMessage should include("unsafe ancestor")
        runner.commandCount shouldBe 0
      }
    }

    "reject a symlinked external final output parent without confirmation setup" in {
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

        When("final is attempted directly")
        val failure = intercept[Exception] {
          _build(project, "final", runner)
        }

        Then("the unsafe external final parent fails closed before another process invocation")
        failure.getMessage should include("Storyboard final output directory")
        failure.getMessage should include("symbolic-link")
        runner.commandCount shouldBe 0
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
        _string(_json(legacyartifact), "schema") shouldBe "cozy.video.storyboard-part-artifact.v2"
        _string(_json(legacyartifact), "mode") shouldBe "confirmation"
        _string(_json(legacyartifact), "partId") shouldBe "legacy"
        _string(_json(legacyartifact).hcursor.downField("output").focus.get, "path") shouldBe "build/parts/legacy.mp4"
        _json(legacyartifact).hcursor.downField("identity").focus shouldBe None
      }
    }

    "invalidate confirmation and final caches when only a legacy renderer configuration changes at equal FileTime" in {
      _with_temp_dir("mixed-part-renderer-currentness") { root =>
        Given("a mixed Storyboard and legacy project whose two outputs and descriptor share one FileTime")
        val project = _write_storyboard_project(root, "storyboard.md", _storyboard())
        val legacyoutput = root.resolve("build/parts/legacy.mp4")
        val confirmationoutput = root.resolve("target/cozy-video/confirmation/confirmation.mp4")
        val finaloutput = root.resolve("build/final.mp4")
        Files.writeString(root.resolve("legacy.json"), """{"scenes":[{"id":"legacy","line":"Legacy"}]}""", StandardCharsets.UTF_8)
        Files.writeString(legacyoutput, "pre-rendered-legacy-part", StandardCharsets.UTF_8)
        Files.writeString(
          project,
          s"""{
             |  "output": "build/final.mp4",
             |  "parts": [
             |    {"id": "board", "type": "storyboard", "storyboard": "storyboard.md", "output": "build/parts/board.mp4"},
             |    {"id": "legacy", "type": "dialogue", "script": "legacy.json", "output": "build/parts/legacy.mp4", "renderer": {"engine": "simple-java2d"}}
             |  ]
             |}
             |""".stripMargin,
          StandardCharsets.UTF_8
        )
        val runner = new FakeRunner

        When("both modes build, reuse unchanged records, then the legacy renderer changes with its descriptor FileTime pinned equal to both outputs")
        _build(project, "confirmation", runner)
        _build(project, "final", runner)
        val confirmationhit = _build(project, "confirmation", runner)
        val finalhit = _build(project, "final", runner)
        val sharedtime = Files.getLastModifiedTime(confirmationoutput)
        Files.writeString(
          project,
          Files.readString(project, StandardCharsets.UTF_8).replace("simple-java2d", "remotion"),
          StandardCharsets.UTF_8
        )
        Files.setLastModifiedTime(project, sharedtime)
        Files.setLastModifiedTime(finaloutput, sharedtime)
        val confirmationmiss = _build(project, "confirmation", runner)
        val finalmiss = _build(project, "final", runner)
        val confirmationrepeat = _build(project, "confirmation", runner)
        val finalrepeat = _build(project, "final", runner)
        val confirmationrenderers = _json(root.resolve("target/cozy-video/confirmation/manifest.json")).hcursor
          .downField("renderers").downField("parts").as[Vector[Json]].toOption.get
        val finalrenderers = _json(root.resolve("target/cozy-video/final/manifest.json")).hcursor
          .downField("renderers").downField("parts").as[Vector[Json]].toOption.get

        Then("both ordered metadata records bind the updated legacy renderer and both mode caches miss once then hit")
        confirmationhit should include("cache: hit")
        finalhit should include("cache: hit")
        confirmationmiss should include("cache: miss")
        finalmiss should include("cache: miss")
        confirmationrepeat should include("cache: hit")
        finalrepeat should include("cache: hit")
        confirmationrenderers.map { entry =>
          (_string(entry, "partId"), entry.hcursor.downField("configured").get[String]("engine").toOption, _string(entry, "effective"))
        } shouldBe Vector(("board", None, "engine=legacy"), ("legacy", Some("remotion"), "engine=remotion"))
        finalrenderers.map { entry =>
          (_string(entry, "partId"), entry.hcursor.downField("configured").get[String]("engine").toOption, _string(entry, "effective"))
        } shouldBe Vector(("board", None, "engine=legacy"), ("legacy", Some("remotion"), "engine=remotion"))
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
          .downField("narration").downArray

        Then("the descriptive native manifest records the projected provider, voice, and normalization input")
        narration.get[String]("provider").toOption shouldBe Some("voicevox")
        narration.downField("voice").get[Int]("fallbackSpeakerId").toOption shouldBe Some(7)
        narration.downField("narration").get[String]("provider").toOption shouldBe Some("voicevox")
        narration.downField("voiceTextNormalization").downField("dictionary").get[String]("Cozy").toOption shouldBe Some("コージー")
        narration.downField("storyboardPronunciationNotes").as[Vector[Json]].toOption.map(_.map { note =>
          _string(note, "surface") -> _string(note, "reading")
        }) shouldBe Some(Vector("Cozy" -> "コージー", "Reimu" -> "れいむ"))
      }
    }

    "preserve normalized Storyboard scenes across equivalent Markdown and JSON source representations" in {
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

        Then("each direct declared source emits its complete normalized selected Storyboard without a generated identity")
        _scene_ids(markdownhandoff) shouldBe _scene_ids(jsonhandoff)
        markdownhandoff.hcursor.downField("identity").focus shouldBe None
        jsonhandoff.hcursor.downField("identity").focus shouldBe None
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

        When("both representations build confirmation and final independently, then produce review evidence")
        _build(markdownproject, "confirmation", markdownrunner)
        _build(jsonproject, "confirmation", jsonrunner)
        val markdownconfirmationmanifest = markdownroot.resolve("target/cozy-video/confirmation/manifest.json")
        val jsonconfirmationmanifest = jsonroot.resolve("target/cozy-video/confirmation/manifest.json")
        val markdownplan = CozyVideoImplementation._plan(markdownproject, Some("host"), None)
        val jsonplan = CozyVideoImplementation._plan(jsonproject, Some("host"), None)
        _write_review_evidence_inputs(markdownplan)
        _write_review_evidence_inputs(jsonplan)
        _build(markdownproject, "final", markdownrunner)
        _build(jsonproject, "final", jsonrunner)
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

        Then("both generated handoffs and manifests preserve the ordered Reimu/Marisa scene speakers without native identities")
        val markdownhandoff = _json(markdownroot.resolve("target/cozy-video/storyboard/board/handoff.json"))
        val jsonhandoff = _json(jsonroot.resolve("target/cozy-video/storyboard/board/handoff.json"))
        val markdownfinalmanifestpath = markdownroot.resolve("target/cozy-video/final/manifest.json")
        val jsonfinalmanifestpath = jsonroot.resolve("target/cozy-video/final/manifest.json")
        val markdownfinalmanifest = _json(markdownfinalmanifestpath)
        val jsonfinalmanifest = _json(jsonfinalmanifestpath)
        val expectedspeakers = Vector("reimu" -> "Reimu", "marisa" -> "Marisa")
        _string(_json(markdownconfirmationmanifest), "mode") shouldBe "confirmation"
        _string(_json(jsonconfirmationmanifest), "mode") shouldBe "confirmation"
        _string(markdownfinalmanifest, "mode") shouldBe "final"
        _string(jsonfinalmanifest, "mode") shouldBe "final"
        markdownhandoff.hcursor.downField("storyboardIdentity").focus shouldBe None
        jsonhandoff.hcursor.downField("storyboardIdentity").focus shouldBe None
        _scene_speakers(markdownhandoff) shouldBe expectedspeakers
        _scene_speakers(jsonhandoff) shouldBe expectedspeakers
        _scene_projection(markdownhandoff) shouldBe Vector(
          ("reimu", 1, "conversation", "Reimu", "Reimu opens the conversation."),
          ("marisa", 2, "conversation", "Marisa", "Marisa follows with the answer.")
        )
        _scene_projection(jsonhandoff) shouldBe Vector(
          ("reimu", 1, "conversation", "Reimu", "Reimu opens the conversation."),
          ("marisa", 2, "conversation", "Marisa", "Marisa follows with the answer.")
        )
        markdownfinalmanifest.hcursor.downField("storyboards").as[Vector[Json]].toOption.map(_.map { part =>
          _string(part, "sourcePath")
        }) shouldBe Some(Vector("storyboard.md"))
        jsonfinalmanifest.hcursor.downField("storyboards").as[Vector[Json]].toOption.map(_.map { part =>
          _string(part, "sourcePath")
        }) shouldBe Some(Vector("storyboard.json"))
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

    "project each selected Storyboard section into its own handoff from declared sources" in {
      _with_temp_dir("section-handoffs") { root =>
        Given("one declared source Storyboard with opening and summary sections selected by separate parts")
        val storyboard = _sectioned_storyboard()
        val project = _write_sectioned_storyboard_project(root, storyboard)
        val runner = new FakeRunner

        When("confirmation builds both selected Storyboard parts")
        _build(project, "confirmation", runner)
        val opening = _json(root.resolve("target/cozy-video/storyboard/opening/handoff.json"))
        val summary = _json(root.resolve("target/cozy-video/storyboard/summary/handoff.json"))

        Then("each handoff contains only its selected scenes and retains its direct source path")
        _string(opening, "sourcePath") shouldBe "storyboard.md"
        _string(summary, "sourcePath") shouldBe "storyboard.md"
        _string(opening, "storyboardSection") shouldBe "opening"
        _string(summary, "storyboardSection") shouldBe "summary"
        _scene_ids(opening) shouldBe Vector("opening")
        _scene_ids(summary) shouldBe Vector("summary")
        _scene_ids(opening) should not contain "summary"
        _scene_ids(summary) should not contain "opening"
      }
    }

    "use declared per-part Storyboards and ignore an obsolete optional review source and evidence" in {
      _with_temp_dir("independent-part-sources") { root =>
        Given("two safe declared Storyboards and an optional review pointing to a different source with nonexistent evidence")
        val first = _storyboard()
        val second = _sectioned_storyboard().copy(scenes = Vector(_sectioned_storyboard().scenes(1).copy(order = 1)))
        Files.writeString(root.resolve("first.md"), CozyVideo.canonicalStoryboardMarkdown(first), StandardCharsets.UTF_8)
        Files.writeString(root.resolve("second.json"), CozyVideo.canonicalStoryboardJson(second), StandardCharsets.UTF_8)
        Files.writeString(root.resolve("obsolete.md"), CozyVideo.canonicalStoryboardMarkdown(first), StandardCharsets.UTF_8)
        Vector("first", "second").foreach { id =>
          val output = root.resolve(s"build/parts/$id.mp4")
          Files.createDirectories(output.getParent)
          Files.writeString(output, s"part-$id", StandardCharsets.UTF_8)
        }
        val project = root.resolve("video.json")
        Files.writeString(project, s"""{
          |  "output": "build/final.mp4",
          |  "storyboardReview": {"source": "obsolete.md", "approvedIdentity": "sha256:${"0" * 64}", "visualStory": {"evidenceDirectory": "missing-evidence", "inputRefs": [], "approvedEvidenceIdentity": "sha256:${"1" * 64}"}},
          |  "parts": [
          |    {"id": "first", "type": "storyboard", "storyboard": "first.md", "output": "build/parts/first.mp4"},
          |    {"id": "second", "type": "storyboard", "storyboard": "second.json", "output": "build/parts/second.mp4"}
          |  ]
          |}""".stripMargin, StandardCharsets.UTF_8)
        val runner = new FakeRunner

        When("confirmation builds from each part's declared Storyboard rather than review metadata")
        val result = _build(project, "confirmation", runner)
        val firsthandoff = _json(root.resolve("target/cozy-video/storyboard/first/handoff.json"))
        val secondhandoff = _json(root.resolve("target/cozy-video/storyboard/second/handoff.json"))

        Then("both independent source projections are assembled without opening the obsolete review evidence")
        result should include("mode: confirmation")
        _scene_ids(firsthandoff) shouldBe Vector("opening")
        _scene_ids(secondhandoff) shouldBe Vector("summary")
        Files.exists(root.resolve("missing-evidence")) shouldBe false
      }
    }

    "preserve a completed confirmation when a later final build is independently selected" in {
      _with_temp_dir("confirmation-preserved") { root =>
        Given("a completed optional confirmation build")
        val project = _write_storyboard_project(root, "storyboard.md", _storyboard())
        val runner = new FakeRunner
        _build(project, "confirmation", runner)
        val confirmation = root.resolve("target/cozy-video/confirmation/confirmation.mp4")
        val manifest = root.resolve("target/cozy-video/confirmation/manifest.json")
        val confirmationbytes = Files.readAllBytes(confirmation)
        val manifestbytes = Files.readAllBytes(manifest)
        val confirmationtime = Files.getLastModifiedTime(confirmation)
        val manifesttime = Files.getLastModifiedTime(manifest)
        val confirmationpermissions = Files.getPosixFilePermissions(confirmation)
        val manifestpermissions = Files.getPosixFilePermissions(manifest)

        When("final is selected after confirmation without recording any approval")
        val result = _build(project, "final", runner)

        Then("final succeeds and every existing confirmation artifact remains byte-for-byte and time unchanged")
        result should include("mode: final")
        Files.readAllBytes(confirmation) shouldBe confirmationbytes
        Files.readAllBytes(manifest) shouldBe manifestbytes
        Files.getLastModifiedTime(confirmation) shouldBe confirmationtime
        Files.getLastModifiedTime(manifest) shouldBe manifesttime
        Files.getPosixFilePermissions(confirmation) shouldBe confirmationpermissions
        Files.getPosixFilePermissions(manifest) shouldBe manifestpermissions
      }
    }

    "reject unsafe declared Storyboard inputs before process work and preserve final sentinels" in {
      _with_temp_dir("unsafe-storyboard-input") { root =>
        Given("a final output and manifest sentinel plus invalid declared Storyboard source variants")
        val project = _write_storyboard_project(root, "storyboard.md", _storyboard())
        val finaloutput = root.resolve("build/final.mp4")
        val finalmanifest = root.resolve("target/cozy-video/final/manifest.json")
        Files.createDirectories(finalmanifest.getParent)
        Files.writeString(finaloutput, "final-sentinel", StandardCharsets.UTF_8)
        Files.writeString(finalmanifest, "manifest-sentinel", StandardCharsets.UTF_8)
        val outputbytes = Files.readAllBytes(finaloutput)
        val manifestbytes = Files.readAllBytes(finalmanifest)
        val outputtime = Files.getLastModifiedTime(finaloutput)
        val manifesttime = Files.getLastModifiedTime(finalmanifest)
        val outputpermissions = Files.getPosixFilePermissions(finaloutput)
        val manifestpermissions = Files.getPosixFilePermissions(finalmanifest)
        val runner = new FakeRunner
        val projecttext = Files.readString(project, StandardCharsets.UTF_8)
        Files.writeString(root.resolve("malformed.md"), "not a storyboard", StandardCharsets.UTF_8)
        Files.createDirectories(root.resolve("target/cozy-video"))
        Files.writeString(root.resolve("target/cozy-video/generated.md"), CozyVideo.canonicalStoryboardMarkdown(_storyboard()), StandardCharsets.UTF_8)
        val outside = root.resolve("outside.md")
        Files.writeString(outside, CozyVideo.canonicalStoryboardMarkdown(_storyboard()), StandardCharsets.UTF_8)
        Files.createSymbolicLink(root.resolve("linked.md"), outside)

        When("missing, malformed, generated-target, symlinked, and unmatched-section sources are built")
        val failures = Vector("missing.md", "malformed.md", "target/cozy-video/generated.md", "linked.md").map { source =>
          Files.writeString(project, projecttext.replace("storyboard.md", source), StandardCharsets.UTF_8)
          intercept[Exception] { _build(project, "final", runner) }
        } :+ {
          Files.writeString(project, projecttext.replace("\"output\": \"build/parts/board.mp4\"", "\"storyboardSection\": \"missing\", \"output\": \"build/parts/board.mp4\""), StandardCharsets.UTF_8)
          intercept[Exception] { _build(project, "final", runner) }
        }

        Then("each source failure occurs before process invocation or final sentinel mutation")
        failures should have size 5
        runner.commandCount shouldBe 0
        Files.readAllBytes(finaloutput) shouldBe outputbytes
        Files.readAllBytes(finalmanifest) shouldBe manifestbytes
        Files.getLastModifiedTime(finaloutput) shouldBe outputtime
        Files.getLastModifiedTime(finalmanifest) shouldBe manifesttime
        Files.getPosixFilePermissions(finaloutput) shouldBe outputpermissions
        Files.getPosixFilePermissions(finalmanifest) shouldBe manifestpermissions
      }
    }

    "reject missing native build tools before staging or process work" in {
      _with_temp_dir("missing-tool") { root =>
        Given("a valid Storyboard project and a required host ffmpeg check marked missing")
        val project = _write_storyboard_project(root, "storyboard.md", _storyboard())
        val runner = new FakeRunner
        val tools = CozyVideo.VideoToolRegistry(Vector(StaticTool(
          CozyVideo.VideoToolCheck("ffmpeg", CozyVideo.VideoToolMode.Host, CozyVideo.VideoToolStatus.Missing, "planned missing ffmpeg")
        )))

        When("confirmation requests tool checking before native assembly")
        val failure = intercept[Exception] {
          CozyVideo.build(
            CozyVideo.BuildConfig(project, dryRun = false, checkTools = true, toolMode = Some("host"), mode = Some("confirmation")),
            tools,
            runner
          )
        }

        Then("the unavailable tool prevents mux, probe, staging, and generated-mode output")
        failure.getMessage should include("ffmpeg")
        runner.commandCount shouldBe 0
        Files.exists(root.resolve("target/cozy-video/confirmation")) shouldBe false
      }
    }

    "preserve every completed final artifact when staged mux or probe work fails" in {
      _with_temp_dir("staged-failure-preservation") { root =>
        Given("completed final and confirmation products plus an unrelated file")
        val project = _write_storyboard_project(root, "storyboard.md", _storyboard())
        val runner = new FakeRunner
        _build(project, "confirmation", runner)
        _build(project, "final", runner)
        val finaloutput = root.resolve("build/final.mp4")
        val snapshot = _artifact_snapshot(_native_products(root, "final"))
        val confirmation = _artifact_snapshot(_native_products(root, "confirmation"))
        val unrelated = root.resolve("target/cozy-video/unrelated.txt")
        Files.writeString(unrelated, "unrelated", StandardCharsets.UTF_8)
        val unrelatedsnapshot = _artifact_snapshot(Vector(unrelated))
        Files.setLastModifiedTime(root.resolve("storyboard.md"), FileTime.fromMillis(Files.getLastModifiedTime(finaloutput).toMillis + 2000L))
        runner.setFfmpegFailure(true)
        runner.setFfmpegPartialFailure(true)

        When("a stale final encounters partial staged ffmpeg, nonzero probe, malformed probe, invalid probe, and staged metadata failures")
        val ffmpegfailure = intercept[Exception] {
          _build(project, "final", runner)
        }
        runner.setFfmpegFailure(false)
        runner.setFfmpegPartialFailure(false)
        runner.setProbeResults(Vector(CozyVideo.VideoCommandResult(1, "", "planned probe failure")))
        val nonzeroprobe = intercept[Exception] { _build(project, "final", runner) }
        runner.setProbeResults(Vector(CozyVideo.VideoCommandResult(0, "not-json", "")))
        val malformedprobe = intercept[Exception] { _build(project, "final", runner) }
        runner.setProbeResults(Vector(CozyVideo.VideoCommandResult(0, """{"format":{"duration":"0"},"streams":[]}""", "")))
        val invalidprobe = intercept[Exception] { _build(project, "final", runner) }
        runner.setStagingWriteFailure(true)
        val metadatafailure = intercept[Exception] { _build(project, "final", runner) }
        runner.restoreStagingPermissions()

        Then("every prior product's bytes, mode, and mtime, unrelated file, and confirmation remain unchanged")
        Vector(ffmpegfailure, nonzeroprobe, malformedprobe, invalidprobe, metadatafailure).foreach { failure =>
          failure.getMessage should include("assembly failed")
        }
        _same_artifact_snapshot(snapshot) shouldBe true
        _same_artifact_snapshot(confirmation) shouldBe true
        _same_artifact_snapshot(unrelatedsnapshot) shouldBe true
      }
    }

    "regenerates after an invalid reused probe rather than accepting a structurally current final" in {
      _with_temp_dir("invalid-reuse-probe") { root =>
        Given("a structurally current confirmation product and a fake probe that is invalid only on reuse")
        val project = _write_storyboard_project(root, "storyboard.md", _storyboard())
        val runner = new FakeRunner
        _build(project, "confirmation", runner)
        val beforemux = runner.muxCount
        runner.setProbeResults(Vector(
          CozyVideo.VideoCommandResult(0, """{"format":{"duration":"NaN"},"streams":[]}""", ""),
          CozyVideo.VideoCommandResult(0, """{"format":{"duration":"1.000"},"streams":[{"codec_type":"video"}]}""", "")
        ))

        When("the read-only cache probe is invalid and the staged regeneration probe is valid")
        val result = _build(project, "confirmation", runner)

        Then("the invalid existing media does not produce a hit and a real staged regeneration replaces it")
        result should include("cache: miss")
        runner.muxCount shouldBe beforemux + 1
      }
    }

    "preserves the complete prior final set when staged credit generation cannot write" in {
      _with_temp_dir("staged-credit-failure") { root =>
        Given("a credited final product whose selected credit records already exist")
        _write_credit_profile(root)
        val project = _write_storyboard_project(
          root,
          "storyboard.md",
          _storyboard(),
          configuration = "  \"credits\": {\"profile\": \"native-test\", \"include\": [\"native-test-credit\"], \"exclude\": []},\n"
        )
        val runner = new FakeRunner
        _build(project, "final", runner)
        val products = _native_products(root, "final") ++ _credit_products(root, "final")
        val before = _artifact_snapshot(products)
        Files.setLastModifiedTime(
          root.resolve("storyboard.md"),
          FileTime.fromMillis(Files.getLastModifiedTime(root.resolve("build/final.mp4")).toMillis + 2000L)
        )
        runner.setStagingWriteFailure(true)

        When("staged credit output is denied after mux and staged probe but before installation")
        val failure = intercept[Exception] { _build(project, "final", runner) }
        runner.restoreStagingPermissions()

        Then("all prior final, mode, handoff, part-record, and credit bytes, modes, and mtimes remain unchanged")
        failure.getMessage should include("assembly failed")
        _same_artifact_snapshot(before) shouldBe true
      }
    }

    "dry-run final projects variable declared Storyboards without confirmation approval" in {
      val words = Gen.oneOf("one", "two", "three")
      val property = Prop.forAll(words, words, Gen.oneOf(true, false)) { (narration, caption, obsolete) =>
        _with_temp_dir("approval-free-dry-run") { root =>
          Given("a valid declared Storyboard with variable narration/caption and optional obsolete review metadata")
          val storyboard = _storyboard().copy(scenes = _storyboard().scenes.map(_.copy(narration = narration, caption = caption)))
          val project = _write_storyboard_project(root, "storyboard.md", storyboard)
          if (obsolete) {
            val identity = "sha256:" + ("0" * 64)
            val review = "\"storyboardReview\": {\"source\": \"storyboard.md\", \"approvedIdentity\": \"" + identity + "\"},\n  \"parts\""
            val text = Files.readString(project, StandardCharsets.UTF_8).replace("\"parts\"", review)
            Files.writeString(project, text, StandardCharsets.UTF_8)
          }
          val runner = new FakeRunner
          When("final dry-run plans the declared source without confirmation approval")
          val output = CozyVideo.build(CozyVideo.BuildConfig(project, dryRun = true, checkTools = false, toolMode = Some("host"), mode = Some("final")), CozyVideo.VideoToolRegistry(Vector.empty), runner)
          val plan = CozyVideoImplementation._plan(project, Some("host"), None)
          Then("the source-derived scene is planned without runner work or generated final output")
          output.contains("mode: final") && plan.parts.head.script.get.scenes.head.line.contains(narration) && runner.commandCount == 0 && !Files.exists(root.resolve("build/final.mp4"))
        }
      }

      When("ScalaCheck evaluates at least ten approval-free source variations")
      val result = Test.check(Test.Parameters.default.withMinSuccessfulTests(10), property)

      Then("all declared sources plan directly with no generated output or runner invocation")
      withClue(s"ScalaCheck status=${result.status}, succeeded=${result.succeeded}, discarded=${result.discarded}") {
        result.passed shouldBe true
      }
    }
  }

  private final case class ArtifactSnapshot(
    bytes: Vector[Byte],
    modified: FileTime,
    permissions: Set[PosixFilePermission]
  )

  private final case class StaticTool(result: CozyVideo.VideoToolCheck) extends CozyVideo.VideoToolProvider {
    def check(context: CozyVideo.VideoToolContext): CozyVideo.VideoToolCheck = result
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

  private def _scene_projection(json: Json): Vector[(String, Int, String, String, String)] =
    json.hcursor.downField("storyboard").downField("scenes").as[Vector[Json]].fold(
      error => throw new IllegalArgumentException(error.getMessage),
      _.map(scene => (
        _string(scene, "id"),
        scene.hcursor.get[Int]("order").fold(error => throw new IllegalArgumentException(error.getMessage), identity),
        _string(scene, "section"),
        _string(scene, "speaker"),
        _string(scene, "narration")
      ))
    )

  private def _native_products(root: Path, mode: String): Vector[Path] = {
    val moderoot = root.resolve(s"target/cozy-video/$mode")
    Vector(
      if (mode == "confirmation") moderoot.resolve("confirmation.mp4") else root.resolve("build/final.mp4"),
      moderoot.resolve("manifest.json"),
      root.resolve("target/cozy-video/storyboard/board/handoff.json"),
      moderoot.resolve("part-artifacts/board.json")
    )
  }

  private def _credit_products(root: Path, mode: String): Vector[Path] = {
    val directory = if (mode == "confirmation") root.resolve("target/cozy-video/confirmation/credits") else root.resolve("build/credits")
    Vector(directory.resolve("credits.json"), directory.resolve("credits.md"), directory.resolve("renderer-props.json"))
  }

  private def _write_credit_profile(root: Path): Unit = {
    val profile = root.resolve("conf/cozy/video/credit-profiles/native-test.yaml")
    Files.createDirectories(profile.getParent)
    Files.writeString(
      profile,
      """schema: cozy.video.credits.v1
        |profile: native-test
        |presentation:
        |  title: {en: Credits}
        |  hold-seconds: 0.0
        |credits:
        |  - id: native-test-credit
        |    label: {en: Native Test Credit}
        |    publication-text: {en: Native Test Credit}
        |    surfaces: [video, publication]
        |""".stripMargin,
      StandardCharsets.UTF_8
    )
  }

  private def _artifact_snapshot(paths: Vector[Path]): Map[Path, ArtifactSnapshot] =
    paths.map { path =>
      path -> ArtifactSnapshot(
        Files.readAllBytes(path).toVector,
        Files.getLastModifiedTime(path),
        Files.getPosixFilePermissions(path).asScala.toSet
      )
    }.toMap

  private def _same_artifact_snapshot(snapshot: Map[Path, ArtifactSnapshot]): Boolean =
    snapshot.forall { case (path, expected) =>
        Files.readAllBytes(path).toVector == expected.bytes &&
        Files.getLastModifiedTime(path) == expected.modified &&
        Files.getPosixFilePermissions(path).asScala.toSet == expected.permissions
    }

  private def _with_temp_dir[A](label: String)(body: Path => A): A = {
    val root = Files.createTempDirectory("cozy-video-storyboard-mode-" + label).toRealPath()
    try body(root)
    finally _delete(root)
  }

  private def _delete(path: Path): Unit =
    if (Files.exists(path))
      Files.walk(path).iterator().asScala.toVector.sortBy(_.getNameCount).reverse.foreach(Files.deleteIfExists)

  private final class FakeRunner extends CozyVideo.VideoProcessRunner {
    private val _commands = ArrayBuffer.empty[Vector[String]]
    private var _ffmpeg_failure = false
    private var _ffmpeg_partial_failure = false
    private var _probe_results = Vector.empty[CozyVideo.VideoCommandResult]
    private var _staging_write_failure = false
    private var _staging_permissions = Option.empty[(Path, java.util.Set[PosixFilePermission])]

    def commandCount: Int = _commands.size
    def muxCount: Int = _commands.count(_.contains("ffmpeg"))
    def probeCount: Int = _commands.count(_.contains("ffprobe"))
    def setFfmpegFailure(value: Boolean): Unit = _ffmpeg_failure = value
    def setFfmpegPartialFailure(value: Boolean): Unit = _ffmpeg_partial_failure = value
    def setProbeResults(values: Vector[CozyVideo.VideoCommandResult]): Unit = _probe_results = values
    def setStagingWriteFailure(value: Boolean): Unit = _staging_write_failure = value
    def restoreStagingPermissions(): Unit = _staging_permissions.foreach { case (path, permissions) =>
      Files.setPosixFilePermissions(path, permissions)
      _staging_permissions = None
    }

    def run(args: Vector[String], cwd: Path): CozyVideo.VideoCommandResult = {
      _commands += args
      if (args.contains("ffmpeg")) {
        if (_ffmpeg_failure) {
          if (_ffmpeg_partial_failure) {
            val partial = Path.of(args.last)
            Files.createDirectories(partial.getParent)
            Files.writeString(partial, "partial-fake-mp4", StandardCharsets.UTF_8)
          }
          return CozyVideo.VideoCommandResult(1, "", "planned ffmpeg failure")
        }
        val output = Path.of(args.last)
        Files.createDirectories(output.getParent)
        Files.writeString(output, s"fake-mp4-${_commands.size}", StandardCharsets.UTF_8)
        if (_staging_write_failure) {
          val staging = output.getParent
          _staging_permissions = Some(staging -> Files.getPosixFilePermissions(staging))
          Files.setPosixFilePermissions(staging, Set(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_EXECUTE).asJava)
        }
        CozyVideo.VideoCommandResult(0, "ffmpeg ok", "")
      } else if (args.contains("ffprobe")) {
        _probe_results.headOption match {
          case Some(result) =>
            _probe_results = _probe_results.tail
            result
          case None => CozyVideo.VideoCommandResult(0, """{"format":{"duration":"1.000"},"streams":[{"codec_type":"video"}]}""", "")
        }
      } else {
        CozyVideo.VideoCommandResult(0, "ok", "")
      }
    }
  }
}
