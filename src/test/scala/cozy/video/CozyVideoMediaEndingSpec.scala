package cozy.video

import java.awt.image.BufferedImage
import java.nio.{ByteBuffer, ByteOrder}
import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path, Paths, StandardCopyOption}
import scala.collection.mutable.ArrayBuffer
import javax.imageio.ImageIO
import io.circe.{Json, parser}
import org.scalacheck.{Gen, Prop, Test}
import org.scalatest.GivenWhenThen
import org.scalatest.wordspec.AnyWordSpec
import cozy.CozySpecVocabulary

/*
 * @since   Oct.  5, 2026
 * @version Oct.  5, 2026
 */
object CozyVideoMediaEndingSpec {
  private final class NativeRunner extends CozyVideo.VideoProcessRunner {
    val observations: ArrayBuffer[(Vector[String], CozyVideo.VideoCommandResult)] = ArrayBuffer.empty
    def run(args: Vector[String], cwd: Path): CozyVideo.VideoCommandResult = {
      val result = CozyVideo.VideoProcessRunner.default.run(args, cwd)
      observations += args -> result
      result
    }
  }
}

final class CozyVideoMediaEndingSpec
    extends AnyWordSpec
    with GivenWhenThen
    with CozySpecVocabulary {
  "An author retaining complete narration and the ending card" should {
    "carry public synthesis samples across rounded timing boundaries" which {
      "retain boundary WAVs and reject explicit rounded-down review windows" in {
        Vector(23999, 24000, 24001, 48001).foreach { samples =>
          val dir = _public_timing_evidence(18, samples)
          if (samples == 24001) {
            val props = _props(dir)
            val scene = _scenes(props).head
            _int(scene, "audioDurationFrames") shouldBe 19

            Given("the public 24001-sample props with only the optional integer audio field omitted")
            _write_props(dir, props.mapObject(_.add("scenes", Json.fromValues(Vector(scene.mapObject(_.remove("audioDurationFrames")))))))
            When("review interprets the older optional field shape with its actual WAV")
            val optional = _review(dir, "review-public-optional", CozyVideoSpec.ReviewEvidenceRunner(), "host", None)
            Then("the actual scene envelope and full audio coverage remain accepted")
            optional should include("Cozy Video Review Evidence")
            _tail_frame(_json(dir.resolve("review-public-optional/review-manifest.json"))) shouldBe
              _int(props.hcursor.downField("timing").focus.get, "openingFrames") + _int(scene, "leadInFrames") + 19

            Vector("audioDurationFrames" -> 18, "durationFrames" -> (_int(scene, "durationFrames") - 1)).foreach { case (field, value) =>
              Given(s"explicit $field=$value that omits actual WAV coverage despite stored audioDuration=1.000")
              _write_props(dir, props.mapObject(_.add("scenes", Json.fromValues(Vector(scene.mapObject(_.add(field, Json.fromInt(value))))))))
              val runner = CozyVideoSpec.ReviewEvidenceRunner()
              When("review is requested for the contradictory integer window")
              val failure = intercept[IllegalArgumentException] {
                _review(dir, s"review-public-unsafe-$field", runner, "host", None)
              }
              Then("the actionable timing mismatch is reported before any frame extraction")
              failure.getMessage should include("does not match")
              runner.commands shouldBe empty
            }
          }
        }
      }

      "retain actual sample coverage across sample offsets and frame rates" in {
        Given("public synthesis inputs spanning both sides of one- and two-second millisecond rounding boundaries")
        val cases = for {
          n <- Gen.choose(1, 2)
          d <- Gen.choose(-1, 11)
          fps <- Gen.oneOf(12, 18, 24, 30)
        } yield (fps, 24000 * n + d)
        val property = Prop.forAll(cases) { case (fps, samples) =>
          _public_timing_evidence(fps, samples)
          true
        }
        When("twenty ScalaCheck public synthesis, render, final build and review use cases are interpreted")
        val result = Test.check(Test.Parameters.default.withMinSuccessfulTests(20), property)
        Then("rounded historical seconds coexist with minimal actual WAV coverage in every case")
        result.passed shouldBe true
        result.succeeded should be >= 20
      }

      "reject a missing actual source WAV before review extraction" in {
        Given("a successful public synthesis, render, final build and review fixture")
        val dir = _public_timing_evidence(18, 24001)
        val entry = _json(dir.resolve("build/audio/dialogue/manifest.json")).asArray.get.head
        val source = dir.resolve("build/audio/dialogue").resolve(entry.hcursor.get[String]("file").toOption.get)
        Files.delete(source)
        val runner = CozyVideoSpec.ReviewEvidenceRunner()
        When("review is requested after its selected actual source WAV has been removed")
        val failure = intercept[IllegalArgumentException] {
          _review(dir, "review-public-missing-wav", runner, "host", None)
        }
        Then("an actionable Missing audio file failure prevents guessing from rounded manifest seconds")
        failure.getMessage should include("Missing audio file")
        failure.getMessage should include(source.toString)
        runner.commands shouldBe empty
      }
    }

    "stage deterministic integer coverage" which {
      "keep adjacent scenes, full WAV windows and independent holds on one frame grid" in {
        Given("integer and fractional PCM WAVs, authored leads and tails, target floors and one section change")
        val cases = for {
          fps <- Gen.oneOf(12, 18, 24, 30)
          samples <- Gen.oneOf(4800, 8640, 24000, 32880)
          lead <- Gen.oneOf(0.0, 0.2, 0.27)
          tail <- Gen.oneOf(0.0, 0.1, 0.43)
          target <- Gen.oneOf(0.0, 1.0, 2.0)
        } yield (fps, samples, lead, tail, target)
        val property = Prop.forAll(cases) { case (fps, samples, lead, tail, target) =>
          Given(s"three ordered scenes at $fps fps with $samples actual samples per WAV")
          val dir = _fixture(fps, samples, lead, tail, target, 3, summary = true)
          When("the public render operation stages the narration through the production adapter")
          CozyVideo.render(CozyVideo.RenderConfig(dir.resolve("video.json"), "remotion", toolMode = Some("host")),
            CozyVideo.VideoToolRegistry(Vector.empty), ProfileRenderRunner())
          val props = _props(dir)
          val scenes = _scenes(props)
          val entries = _json(dir.resolve("build/audio/dialogue/manifest.json")).asArray.get.toVector
          val timing = props.hcursor.downField("timing").focus.get
          Then("every full audio window and effective tail fits its scene without gaps or double transitions")
          var boundary = 0
          scenes.zip(entries).zipWithIndex.foreach { case ((scene, entry), index) =>
            val transition = _int(scene, "sectionTransitionFrames")
            val duration = _int(scene, "durationFrames")
            val audioframes = _int(scene, "audioDurationFrames")
            val leadframes = _int(scene, "leadInFrames")
            val effective = entry.hcursor.get[Double]("tailSilence").toOption.get
            _int(scene, "startFrame") shouldBe boundary
            transition shouldBe (if (index == 1) math.round(1.2 * fps).toInt else 0)
            leadframes - transition shouldBe math.round(lead * fps).toInt
            audioframes.toDouble / fps should be >= (samples.toDouble / 24000)
            (audioframes - 1).toDouble / fps should be < (samples.toDouble / 24000)
            (duration - leadframes - audioframes).toDouble / fps should be >= effective
            (duration - transition).toDouble / fps should be >= target
            _int(scene, "effectiveTailSilenceFrames").toDouble / fps should be >= effective
            scene.hcursor.get[Double]("requestedTailSilenceSeconds").toOption shouldBe Some(tail)
            scene.hcursor.get[Double]("effectiveTailSilenceSeconds").toOption shouldBe Some(effective)
            boundary += duration
          }
          And("content, summary, credits and the fixed final hold share the accumulated composition boundary")
          _int(timing, "contentFrames") shouldBe boundary
          val opening = _int(timing, "openingFrames")
          val summaryframes = _int(timing, "summaryFrames")
          _int(timing, "summaryStartFrame") shouldBe opening + boundary
          _int(timing, "creditPageStartFrame") shouldBe opening + boundary + summaryframes
          _int(timing, "finalPageStartFrame") shouldBe opening + boundary + summaryframes + _int(timing, "creditPageHoldFrames")
          _int(timing, "finalPageHoldFrames") shouldBe 2 * fps
          _int(timing, "totalFrames") shouldBe _int(timing, "finalPageStartFrame") + 2 * fps
          true
        }
        When("twenty randomized authoring use cases are interpreted")
        val result = Test.check(Test.Parameters.default.withMinSuccessfulTests(20), property)
        Then("all staged use cases retain complete narration and ordered endings")
        result.passed shouldBe true
        result.succeeded should be >= 20
      }

      "accept fractional review timing and reject explicit under-covering windows" in {
        Given("a fractional narration project staged and assembled with deterministic command substitutes")
        val dir = _fixture(18, 8640, 0.2, 0.1, 0.0, 1, summary = false)
        val runner = ProfileRenderRunner()
        CozyVideo.render(CozyVideo.RenderConfig(dir.resolve("video.json"), "remotion", toolMode = Some("host")),
          CozyVideo.VideoToolRegistry(Vector.empty), runner)
        CozyVideo.build(CozyVideo.BuildConfig(dir.resolve("video.json"), dryRun = false, checkTools = false,
          toolMode = Some("host"), mode = Some("final")), CozyVideo.VideoToolRegistry(Vector.empty), runner)
        When("the public review operation interprets current fractional props and their actual audio manifest")
        val review = _review(dir, "review-current", CozyVideoSpec.ReviewEvidenceRunner(), "host", None)
        val evidence = _json(dir.resolve("review-current/review-manifest.json"))
        val tail = _tail_frame(evidence)
        val props = _props(dir)
        val scene = _scenes(props).head
        Then("review accepts the full window and selects tail only after all seven audio frames")
        review should include("Cozy Video Review Evidence")
        tail shouldBe _int(props.hcursor.downField("timing").focus.get, "openingFrames") + _int(scene, "leadInFrames") + 7

        Given("the same props with their optional integer audio field omitted")
        _write_props(dir, props.mapObject(_.add("scenes", Json.fromValues(Vector(scene.mapObject(_.remove("audioDurationFrames")))))))
        When("review interprets the structurally supported older field shape")
        val optional = _review(dir, "review-optional", CozyVideoSpec.ReviewEvidenceRunner(), "host", None)
        Then("the actual manifest still determines full audio coverage")
        optional should include("Cozy Video Review Evidence")

        Vector("audioDurationFrames" -> 6, "durationFrames" -> 12).foreach { case (field, value) =>
          Given(s"explicit $field that cannot retain the full fractional timing contract")
          _write_props(dir, props.mapObject(_.add("scenes", Json.fromValues(Vector(scene.mapObject(_.add(field, Json.fromInt(value))))))))
          When("review is requested for the contradictory timing")
          val failure = intercept[IllegalArgumentException] {
            _review(dir, s"review-unsafe-$field", CozyVideoSpec.ReviewEvidenceRunner(), "host", None)
          }
          Then("review reports an actionable manifest timing contradiction requiring rerender")
          failure.getMessage should include("does not match")
        }
      }
    }

    "interpret decoded PCM with independent alignment" which {
      "accept complete late tones and reject total or partial tail loss across encoded delays" in {
        Given("the complete 8640-sample fixture source with independent early and late markers")
        val source = _pcm_samples(_wav_bytes(8640).drop(44))
        val speechstart = 85.0 / 18
        val cases = for {
          delay <- Gen.choose(0, 1024)
          amplitude <- Gen.choose(0.5, 1.0)
        } yield (delay, amplitude)
        val property = Prop.forAll(cases) { case (delay, amplitude) =>
          Given(s"decoded PCM delayed by $delay samples at amplitude $amplitude with the original complete source reference")
          val prefix = Vector.fill(math.round(speechstart * 24000).toInt + delay)(0.0)
          val encoded = prefix ++ source.map(value => math.round(value * amplitude).toDouble)
          val totalcut = prefix ++ source.take(8000).map(value => math.round(value * amplitude).toDouble)
          val partialcut = prefix ++ source.take(8544).map(value => math.round(value * amplitude).toDouble)
          When("the early marker alone aligns full, former-six-frame-cut and 0.356-second-cut decoded audio")
          val complete = _audio_measurement(encoded, source, speechstart)
          val missing = _audio_measurement(totalcut, source, speechstart)
          val partial = _audio_measurement(partialcut, source, speechstart)
          Then("the complete tone retains all four bins and its ending at independently estimated alignment")
          _complete_audio_evidence(complete)
          And("the former six-frame cut has zero late RMS and no late-end coverage")
          missing.hcursor.get[Double]("lateToneRms").toOption.get shouldBe 0.0
          missing.hcursor.get[Vector[Double]]("lateToneSubwindowRms").toOption.get shouldBe Vector.fill(4)(0.0)
          missing.hcursor.get[Double]("lateSignalEndSeconds").toOption.get shouldBe 0.0
          missing.hcursor.get[Double]("lateSignalCoverageSeconds").toOption.get should be < 0.359
          And("partial tail loss fails ending coverage despite positive aggregate and final-bin RMS")
          partial.hcursor.get[Double]("lateToneRms").toOption.get should be > 1000.0
          partial.hcursor.get[Vector[Double]]("lateToneSubwindowRms").toOption.get.last should be > 1000.0
          partial.hcursor.get[Double]("lateSignalCoverageSeconds").toOption.get should be < 0.359
          Given("decoded PCM with its independent early marker removed while the complete late tone remains")
          val unmarked = encoded.zipWithIndex.map { case (value, index) =>
            if (index >= prefix.size + 960 && index < prefix.size + 1920) 0.0 else value
          }
          When("measurement is requested without an independent encoded marker")
          val failure = intercept[IllegalStateException] {
            _audio_measurement(unmarked, source, speechstart)
          }
          Then("an actionable missing-marker failure prevents alignment from the late tone")
          failure.getMessage should include("Missing independent encoded early marker")
          true
        }
        When("twenty deterministic ScalaCheck delay and amplitude cases are interpreted")
        val result = Test.check(Test.Parameters.default.withMinSuccessfulTests(20).withInitialSeed(714L), property)
        Then("complete media is accepted while total and partial tail loss are rejected in every case")
        result.passed shouldBe true
        result.succeeded should be >= 20
      }
    }

    "retain actual encoded media in the native regression" which {
      "preserve the late tone and opaque last card in both direct and final outputs" in {
        Given("the explicitly enabled Docker regression with a 0.36-second PCM16 mono 24k WAV, an independent early marker and a final 20ms tone")
        if (!sys.props.get("cozy.video.remotion.integration").contains("true"))
          cancel("Set -Dcozy.video.remotion.integration=true to execute the native media-ending regression.")
        val image = sys.props.getOrElse("cozy.video.remotion.image", CozyVideo.VideoToolSettings.DEFAULT_DOCKER_IMAGE)
        val dir = _fixture(18, 8640, 0.2, 0.1, 0.0, 1, summary = false)
        val runner = new CozyVideoMediaEndingSpec.NativeRunner
        When("the public API renders the actual Remotion image and builds the final MP4")
        CozyVideo.render(CozyVideo.RenderConfig(dir.resolve("video.json"), "remotion", checkTools = true,
          toolMode = Some("docker"), dockerImage = Some(image)), CozyVideo.VideoToolRegistry.default, runner)
        CozyVideo.build(CozyVideo.BuildConfig(dir.resolve("video.json"), dryRun = false, checkTools = true,
          toolMode = Some("docker"), dockerImage = Some(image), mode = Some("final")), CozyVideo.VideoToolRegistry.default, runner)
        val props = _props(dir)
        val timing = props.hcursor.downField("timing").focus.get
        val scene = _scenes(props).head
        val speechstart = (_int(timing, "openingFrames") + _int(scene, "leadInFrames")).toDouble / 18
        And("actual codecs, stereo 48k audio, MP4 format and brand, entire decoded PCM, independent-marker alignment, frame counts, stream PTS and last presented frames are retained separately")
        val measurements = Vector("direct" -> "build/parts/dialogue.mp4", "final" -> "build/final.mp4").map { case (name, path) =>
          _measure(dir, image, name, path, speechstart)
        }
        val review = _review(dir, "review-native", runner, "docker", Some(image))
        val reviewtail = _tail_frame(_json(dir.resolve("review-native/review-manifest.json")))
        _write(dir.resolve("measurements.json"), Json.fromValues(measurements.map(_._1)).spaces2)
        info(s"Native measurements retained under $dir; player EOF and actual provider acceptance remain separate.")
        Then("direct and single-part products are H264/AAC MP4 with complete ending tones, onset within 1ms and media ends on one sample grid")
        _int(timing, "totalFrames") shouldBe 130
        val renderlog = runner.observations.filter(_._1.contains("node")).map(_._2.stdout).mkString("\n")
        val rendererframes = "(?i)(?:rendered|encoded)\\s+(\\d+)\\s*/\\s*(\\d+)".r.findAllMatchIn(
          renderlog.replaceAll("\\u001B\\[[;\\d]*m", "")).map(m => m.group(2).toInt).toVector
        rendererframes should contain(130)
        measurements.foreach { case (record, frame) =>
          _complete_audio_evidence(record)
          math.abs(record.hcursor.get[Double]("observedOffsetSeconds").toOption.get) should be <= 0.001
          _presentation_evidence(record, 130)
          record.hcursor.get[Int]("decodedFrames").toOption.get shouldBe _int(timing, "totalFrames")
          And("the actual final presented frame is a full-sized opaque nonblack green end card")
          frame.getWidth shouldBe 1280
          frame.getHeight shouldBe 720
          record.hcursor.get[Double]("centralGreenFraction").toOption.get should be > 0.99
          record.hcursor.get[Double]("centralMeanRgb").toOption.get should be > 40.0
          record.hcursor.get[Boolean]("centralOpaque").toOption.get shouldBe true
        }
        _int(timing, "finalPageHoldFrames") shouldBe 36
        review should include("Cozy Video Review Evidence")
        reviewtail shouldBe _int(timing, "openingFrames") + _int(scene, "leadInFrames") + 7

        Given("a task-owned copy of the same real rendered part selected second without another renderer invocation")
        Files.copy(dir.resolve("build/parts/dialogue.mp4"), dir.resolve("build/parts/second.mp4"), StandardCopyOption.REPLACE_EXISTING)
        _two_part_project(dir, "second.mp4", "two-part.mp4")
        When("the native public final build assembles both narrated MP4 presentation timelines")
        CozyVideo.build(CozyVideo.BuildConfig(dir.resolve("two-video.json"), dryRun = false, checkTools = true,
          toolMode = Some("docker"), dockerImage = Some(image)), CozyVideo.VideoToolRegistry.default, runner)
        val two = _measure(dir, image, "two-part", "build/two-part.mp4", speechstart)
        val second = _audio_measurement(_pcm_samples(Files.readAllBytes(dir.resolve("two-part-all-audio.pcm"))),
          _pcm_samples(_wav_bytes(8640).drop(44)), 130.0 / 18 + speechstart)
        _write(dir.resolve("two-part-second-audio.json"), second.spaces2)
        Then("the two-part product remains H264/AAC MP4 and both markers and all late-tail bins retain positions within 1ms with 260 ordered frames")
        Vector(two._1, second).foreach { record =>
          _complete_audio_evidence(record)
          math.abs(record.hcursor.get[Double]("observedOffsetSeconds").toOption.get) should be <= 0.001
        }
        _presentation_evidence(two._1, 260)

        Given("a task-owned video-only copy of that rendered part selected before the narrated original")
        _docker(dir, image, Vector("ffmpeg", "-y", "-copyts", "-i", "/workspace/build/parts/dialogue.mp4",
          "-map", "0:v:0", "-an", "-c:v", "copy", "-movie_timescale", "48000", "/workspace/build/parts/silent.mp4"))
        _two_part_project(dir, "silent.mp4", "mixed.mp4", silentfirst = true)
        When("native public final build allocates silence for the video-only interval and preserves the narrated second interval")
        CozyVideo.build(CozyVideo.BuildConfig(dir.resolve("two-video.json"), dryRun = false, checkTools = true,
          toolMode = Some("docker"), dockerImage = Some(image)), CozyVideo.VideoToolRegistry.default, runner)
        val mixed = _measure(dir, image, "mixed", "build/mixed.mp4", 130.0 / 18 + speechstart)
        val mixedpcm = _pcm_samples(Files.readAllBytes(dir.resolve("mixed-all-audio.pcm")))
        Then("the mixed product remains H264/AAC MP4, its first interval is silent, its complete second narration aligns within 1ms and the opaque card reaches common end")
        _complete_audio_evidence(mixed._1)
        math.abs(mixed._1.hcursor.get[Double]("observedOffsetSeconds").toOption.get) should be <= 0.001
        _pcm_rms(mixedpcm, 0.05, 130.0 / 18 - 0.05) should be < 1.0
        _presentation_evidence(mixed._1, 260)
        runner.observations.count(_._1.contains("node")) shouldBe 1
        val stderr = runner.observations.map(_._2.stderr).mkString("\n")
        _write(dir.resolve("native-command-observations.json"), Json.fromValues(runner.observations.map { case (args, result) =>
          Json.obj("args" -> Json.fromValues(args.map(Json.fromString)), "exitCode" -> Json.fromInt(result.exitCode),
            "stdout" -> Json.fromString(result.stdout), "stderr" -> Json.fromString(result.stderr))
        }).spaces2)
        stderr.toLowerCase should not include "non-monoton"
      }
    }
  }

  private def _public_timing_evidence(fps: Int, samples: Int): Path = {
    Given(s"one narrated scene with $samples PCM16 mono samples at 24k, lead 0.2, tail 0.1 and target zero at $fps fps")
    val dir = _fixture(fps, samples, 0.2, 0.1, 0.0, 1, summary = false)
    val scriptpath = dir.resolve("script.json")
    val script = _json(scriptpath).mapObject(_.add("narration", Json.obj("provider" -> Json.fromString("voicevox"))).
      add("voice", Json.obj("fallbackSpeakerId" -> Json.fromInt(3))))
    _write(scriptpath, script.spaces2)
    val runner = ProfileRenderRunner()
    val provider = CozyVideoSpec.RecordingVoicevoxClient(audioBytes = _wav_bytes(samples))
    When("the public synthesis writes its normalized WAV and rounded manifest, then render, final build and review consume them")
    CozyVideo.synthesize(CozyVideo.SynthesizeConfig(scriptpath, dir.resolve("build/audio/dialogue"),
      voicevoxUrl = Some("http://voicevox.example"), checkTools = false, toolMode = Some("host")),
      CozyVideo.VideoToolRegistry(Vector.empty), provider, runner)
    val entries = _json(dir.resolve("build/audio/dialogue/manifest.json")).asArray.get.toVector
    val entry = entries.head
    val source = dir.resolve("build/audio/dialogue").resolve(entry.hcursor.get[String]("file").toOption.get)
    val wave = CozyVideoImplementation._read_wav(source)
    CozyVideo.render(CozyVideo.RenderConfig(dir.resolve("video.json"), "remotion", toolMode = Some("host")),
      CozyVideo.VideoToolRegistry(Vector.empty), runner)
    CozyVideo.build(CozyVideo.BuildConfig(dir.resolve("video.json"), dryRun = false, checkTools = false,
      toolMode = Some("host"), mode = Some("final")), CozyVideo.VideoToolRegistry(Vector.empty), runner)
    val review = _review(dir, "review-public-current", CozyVideoSpec.ReviewEvidenceRunner(), "host", None)
    val evidence = _json(dir.resolve("review-public-current/review-manifest.json"))
    val props = _props(dir)
    val scene = _scenes(props).head
    val timing = props.hcursor.downField("timing").focus.get
    val actualduration = samples.toDouble / 24000
    val stored = math.round(actualduration * 1000).toDouble / 1000
    val audioframes = math.max(1, math.ceil(actualduration * fps).toInt)
    val leadframes = math.round(0.2 * fps).toInt
    val tailframes = math.ceil(0.1 * fps).toInt
    val sceneframes = VideoTiming.evaluate(0.0, 0.2, actualduration, 0.1, fps).effectiveFrameCount.toInt
    Then("synthesis retains exact normalized samples and its historical rounded continuous values")
    entries should have size 1
    wave.data.length / 2 shouldBe samples
    wave.samplerate shouldBe 24000
    wave.channels shouldBe 1
    wave.bitspersample shouldBe 16
    entry.hcursor.get[Double]("audioDuration").toOption shouldBe Some(stored)
    entry.hcursor.get[Double]("leadSilence").toOption shouldBe Some(0.2)
    entry.hcursor.get[Double]("targetDuration").toOption shouldBe Some(0.0)
    entry.hcursor.get[Double]("tailSilence").toOption shouldBe Some(0.1)
    scene.hcursor.get[Double]("audioDuration").toOption shouldBe Some(stored)
    scene.hcursor.get[Double]("duration").toOption shouldBe Some(0.2 + stored + 0.1)
    scene.hcursor.get[Double]("requestedTailSilenceSeconds").toOption shouldBe Some(0.1)
    scene.hcursor.get[Double]("effectiveTailSilenceSeconds").toOption shouldBe Some(0.1)
    And("the smallest integer audio window and scene envelope retain the full actual audio and tail")
    _int(scene, "audioDurationFrames") shouldBe audioframes
    audioframes.toDouble / fps should be >= actualduration
    (audioframes - 1).toDouble / fps should be < actualduration
    _int(scene, "durationFrames") shouldBe sceneframes
    _int(scene, "leadInFrames") shouldBe leadframes
    _int(scene, "effectiveTailSilenceFrames") shouldBe tailframes
    sceneframes should be >= (leadframes + audioframes + tailframes)
    _int(scene, "sectionTransitionFrames") shouldBe 0
    _int(scene, "startFrame") shouldBe 0
    And("composition boundaries and the opaque end-card asset keep their independently configured holds")
    val opening = _int(timing, "openingFrames")
    opening shouldBe math.round(4.5 * fps).toInt
    _int(timing, "contentFrames") shouldBe sceneframes
    _int(timing, "summaryFrames") shouldBe 0
    _int(timing, "summaryStartFrame") shouldBe opening + sceneframes
    _int(timing, "creditPageStartFrame") shouldBe opening + sceneframes
    _int(timing, "creditPageHoldFrames") shouldBe 0
    _int(timing, "finalPageStartFrame") shouldBe opening + sceneframes
    _int(timing, "finalPageHoldFrames") shouldBe 2 * fps
    _int(timing, "totalFrames") shouldBe opening + sceneframes + 2 * fps
    props.hcursor.get[Double]("durationSeconds").toOption shouldBe Some(_int(timing, "totalFrames").toDouble / fps)
    Files.readString(dir.resolve("assets/end-card.svg"), StandardCharsets.UTF_8) should include("fill=\"#00c800\"")
    And("review selects the tail after the actual ceiling audio window while preserving continuous evidence")
    review should include("Cozy Video Review Evidence")
    _tail_frame(evidence) shouldBe opening + leadframes + audioframes
    val reviewscene = evidence.hcursor.get[Vector[Json]]("parts").toOption.get.head.hcursor.get[Vector[Json]]("scenes").toOption.get.head
    reviewscene.hcursor.downField("authoredTiming").get[Double]("audioDurationSeconds").toOption shouldBe Some(stored)
    reviewscene.hcursor.downField("authoredTiming").get[Double]("leadSilenceSeconds").toOption shouldBe Some(0.2)
    reviewscene.hcursor.downField("authoredTiming").get[Double]("targetDurationSeconds").toOption shouldBe Some(0.0)
    reviewscene.hcursor.downField("authoredTiming").get[Double]("requestedTailSilenceSeconds").toOption shouldBe Some(0.1)
    reviewscene.hcursor.downField("audio").get[Double]("durationSeconds").toOption shouldBe Some(stored)
    reviewscene.hcursor.get[Double]("effectiveTailSilenceSeconds").toOption shouldBe Some(0.1)
    dir
  }

  private def _fixture(fps: Int, samples: Int, lead: Double, tail: Double, target: Double, count: Int, summary: Boolean): Path = {
    val root = Paths.get(sys.props("user.dir")).toAbsolutePath.normalize().resolve("target/test-generated/video-media-ending")
    Files.createDirectories(root)
    val dir = Files.createTempDirectory(root, "ending-")
    _write(dir.resolve("assets/guide.svg"), """<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 160 240"><rect width="160" height="240" fill="#284b63"/></svg>""")
    _write(dir.resolve("assets/end-card.svg"), """<svg xmlns="http://www.w3.org/2000/svg" width="1280" height="720" viewBox="0 0 1280 720"><rect width="1280" height="720" fill="#00c800"/></svg>""")
    val scenes = (0 until count).map { index =>
      Json.obj("id" -> Json.fromString(s"scene-$index"), "speaker" -> Json.fromString("guide"),
        "line" -> Json.fromString("Retain the complete narration"), "caption" -> Json.fromString("Retain the complete narration"),
        "section" -> Json.fromString(if (index == 0) "first" else "next"),
        "duration" -> Json.fromDoubleOrNull(target), "leadSilence" -> Json.fromDoubleOrNull(lead),
        "tailSilence" -> Json.fromDoubleOrNull(tail), "visual" -> Json.obj("heading" -> Json.fromString("Complete narration")))
    }.toVector
    _write(dir.resolve("script.json"), Json.obj("characters" -> Json.obj("guide" -> Json.obj(
      "asset" -> Json.fromString("assets/guide.svg"), "mouthClosedAsset" -> Json.fromString("assets/guide.svg"),
      "mouthOpenAsset" -> Json.fromString("assets/guide.svg"), "side" -> Json.fromString("left"))),
      "scenes" -> Json.fromValues(scenes)).spaces2)
    _write(dir.resolve("video.json"), s"""{"output":"build/final.mp4","renderer":{"engine":"remotion","policy":"lightweight","fps":$fps,"width":1280,"height":720,"strategy":"slide","effectProfile":"compact"},"visualEffects":{"opening":"title-hold-subtle-motion","sectionStart":"line-sweep","summary":"${if (summary) "overview-and-conclusion-hold" else "none"}","finalPage":"end-card"},"assets":{"finalPage":{"path":"assets/end-card.svg","required":true,"license":"CC0","provenance":"Executable specification fixture"}},"parts":[{"id":"dialogue","type":"dialogue","script":"script.json","audioDir":"build/audio/dialogue","output":"build/parts/dialogue.mp4"}]}""")
    val audio = dir.resolve("build/audio/dialogue")
    Files.createDirectories(audio)
    val entries = scenes.zipWithIndex.map { case (scene, index) =>
      val name = s"scene-$index.wav"
      Files.write(audio.resolve(name), _wav_bytes(samples))
      val duration = samples.toDouble / 24000
      Json.obj("sceneId" -> scene.hcursor.downField("id").focus.get, "speaker" -> Json.fromString("guide"),
        "file" -> Json.fromString(name), "leadSilence" -> Json.fromDoubleOrNull(lead),
        "audioDuration" -> Json.fromDoubleOrNull(duration), "targetDuration" -> Json.fromDoubleOrNull(target),
        "tailSilence" -> Json.fromDoubleOrNull(math.max(tail, math.max(target - lead - duration, 0.0))))
    }
    _write(audio.resolve("manifest.json"), Json.fromValues(entries).spaces2)
    dir
  }

  private def _wav_bytes(samples: Int): Array[Byte] = {
    val buffer = ByteBuffer.allocate(44 + samples * 2).order(ByteOrder.LITTLE_ENDIAN)
    buffer.put("RIFF".getBytes(StandardCharsets.US_ASCII)).putInt(36 + samples * 2)
    buffer.put("WAVEfmt ".getBytes(StandardCharsets.US_ASCII)).putInt(16).putShort(1.toShort).putShort(1.toShort)
    buffer.putInt(24000).putInt(48000).putShort(2.toShort).putShort(16.toShort)
    buffer.put("data".getBytes(StandardCharsets.US_ASCII)).putInt(samples * 2)
    (0 until samples).foreach { index =>
      val value = if (index >= 960 && index < 1920)
        math.round(12000 * math.sin(2 * math.Pi * 500 * index / 24000)).toInt
      else if (index >= 8160)
        math.round(12000 * math.sin(2 * math.Pi * 1000 * index / 24000)).toInt
      else 0
      buffer.putShort(value.toShort)
    }
    buffer.array()
  }

  private def _measure(dir: Path, image: String, name: String, path: String, speechstart: Double): (Json, BufferedImage) = {
    val probe = _docker(dir, image, Vector("ffprobe", "-v", "error", "-count_frames", "-show_streams", "-show_format", "-of", "json", s"/workspace/$path"))
    _write(dir.resolve(s"$name-ffprobe.json"), probe.stdout)
    val json = parser.parse(probe.stdout).toOption.get
    val video = json.hcursor.get[Vector[Json]]("streams").toOption.get.find(_.hcursor.get[String]("codec_type").toOption.contains("video")).get
    val audio = json.hcursor.get[Vector[Json]]("streams").toOption.get.find(_.hcursor.get[String]("codec_type").toOption.contains("audio")).get
    val count = video.hcursor.get[String]("nb_read_frames").toOption.get.toInt
    val pts = _docker(dir, image, Vector("ffprobe", "-v", "error", "-show_frames", "-show_entries",
      "frame=media_type,pts_time,best_effort_timestamp_time,pkt_duration_time", "-of", "json", s"/workspace/$path"))
    _write(dir.resolve(s"$name-presentation-timestamps.json"), pts.stdout)
    val packets = _docker(dir, image, Vector("ffprobe", "-v", "error", "-show_packets", "-of", "json", s"/workspace/$path"))
    _write(dir.resolve(s"$name-mux-packets.json"), packets.stdout)
    val packetjson = parser.parse(packets.stdout).toOption.get.hcursor.get[Vector[Json]]("packets").toOption.get
    val framejson = parser.parse(pts.stdout).toOption.get.hcursor.get[Vector[Json]]("frames").toOption.get
    val framepts = framejson.filter(_.hcursor.get[String]("media_type").toOption.contains("video"))
      .map(_.hcursor.get[String]("best_effort_timestamp_time").toOption.get.toDouble)
    val decodedresult = _docker(dir, image, Vector("ffmpeg", "-hide_banner", "-loglevel", "error", "-nostdin", "-i", s"/workspace/$path",
      "-vn", "-ac", "1", "-ar", "24000", "-f", "s16le", "-y", s"/workspace/$name-all-audio.pcm"))
    val decoded = _pcm_samples(Files.readAllBytes(dir.resolve(s"$name-all-audio.pcm")))
    val source = _pcm_samples(Files.readAllBytes(dir.resolve("build/audio/dialogue/scene-0.wav")).drop(44))
    val measurement = _audio_measurement(decoded, source, speechstart)
    _docker(dir, image, Vector("ffmpeg", "-hide_banner", "-loglevel", "error", "-nostdin", "-i", s"/workspace/$path",
      "-vf", s"select=eq(n\\,${count - 1})", "-frames:v", "1", "-y", s"/workspace/$name-last-frame.png"))
    val frame = Option(ImageIO.read(dir.resolve(s"$name-last-frame.png").toFile)).getOrElse(
      throw new IllegalStateException(s"Unreadable last presented frame in $name"))
    val (green, nonblack, opaque) = _green_card(frame)
    _docker(dir, image, Vector("ffmpeg", "-hide_banner", "-loglevel", "error", "-nostdin", "-i", s"/workspace/$path",
      "-vf", s"select=gte(n\\,${count - 36})", "-fps_mode", "passthrough", "-y", s"/workspace/$name-card-%03d.png"))
    val cards = (1 to 36).map(index => Option(ImageIO.read(dir.resolve(f"$name-card-$index%03d.png").toFile)).getOrElse(
      throw new IllegalStateException(s"Missing native end-card frame $index for $name")))
    val cardsvalid = cards.forall { card =>
      val (fraction, mean, solid) = _green_card(card, full = true)
      fraction > 0.99 && mean > 40 && solid && card.getWidth == 1280 && card.getHeight == 720
    }
    val dtsordered = Vector("video", "audio").forall { kind =>
      val stamps = packetjson.filter(_.hcursor.get[String]("codec_type").toOption.contains(kind))
        .flatMap(_.hcursor.get[String]("dts_time").toOption.map(_.toDouble))
      stamps.nonEmpty && stamps.sliding(2).forall(pair => pair.size < 2 || pair(1) > pair(0))
    }
    (measurement.deepMerge(Json.obj("output" -> Json.fromString(name), "decodedFrames" -> Json.fromInt(count),
      "videoCodec" -> video.hcursor.downField("codec_name").focus.get,
      "audioCodec" -> audio.hcursor.downField("codec_name").focus.get,
      "audioSampleRate" -> Json.fromInt(audio.hcursor.get[String]("sample_rate").toOption.get.toInt),
      "audioChannels" -> audio.hcursor.downField("channels").focus.get,
      "formatName" -> json.hcursor.downField("format").downField("format_name").focus.get,
      "majorBrand" -> json.hcursor.downField("format").downField("tags").downField("major_brand").focus.get,
      "videoPresentedEnd" -> Json.fromDoubleOrNull(_stream_end(video)),
      "audioPresentedEnd" -> Json.fromDoubleOrNull(_stream_end(audio)),
      "videoFramePts" -> Json.fromValues(framepts.map(Json.fromDoubleOrNull)),
      "muxDtsOrdered" -> Json.fromBoolean(dtsordered), "endCardFramesOpaqueGreen" -> Json.fromBoolean(cardsvalid),
      "decodedAudioSamples24000" -> Json.fromInt(decoded.size),
      "audioEditPresentation" -> audio, "editLists" -> _edit_lists(dir.resolve(path)),
      "packetSkipPadding" -> Json.fromValues(packetjson.flatMap(_.hcursor.downField("side_data_list").focus)),
      "decoderStderr" -> Json.fromString(decodedresult.stderr),
      "centralGreenFraction" -> Json.fromDoubleOrNull(green), "centralMeanRgb" -> Json.fromDoubleOrNull(nonblack),
      "centralOpaque" -> Json.fromBoolean(opaque))), frame)
  }

  private def _two_part_project(dir: Path, second: String, output: String, silentfirst: Boolean = false): Unit = {
    val original = _json(dir.resolve("video.json"))
    val part = original.hcursor.get[Vector[Json]]("parts").toOption.get.head
    val copied = part.mapObject(_.add("id", Json.fromString("second")).add("output", Json.fromString(s"build/parts/$second")))
    val parts = if (silentfirst) Vector(copied, part) else Vector(part, copied)
    _write(dir.resolve("two-video.json"), original.mapObject(_.add("output", Json.fromString(s"build/$output"))
      .add("parts", Json.fromValues(parts))).spaces2)
  }

  private def _stream_end(stream: Json): Double = {
    val base = stream.hcursor.get[String]("time_base").toOption.get.split("/").map(_.toDouble)
    val duration = stream.hcursor.get[Long]("duration_ts").toOption.get * base(0) / base(1)
    stream.hcursor.get[String]("start_time").toOption.get.toDouble + duration
  }

  private def _presentation_evidence(record: Json, frames: Int): Unit = {
    record.hcursor.get[String]("videoCodec").toOption.get shouldBe "h264"
    record.hcursor.get[String]("audioCodec").toOption.get shouldBe "aac"
    record.hcursor.get[Int]("audioSampleRate").toOption.get shouldBe 48000
    record.hcursor.get[Int]("audioChannels").toOption.get shouldBe 2
    val formats = record.hcursor.get[String]("formatName").toOption.get.split(",").toVector
    formats should contain("mp4")
    formats should not contain "matroska"
    val majorbrand = record.hcursor.get[String]("majorBrand").toOption.get.trim
    majorbrand should fullyMatch regex "(?:iso.*|mp41|mp42|avc1)"
    majorbrand should not be "qt"
    record.hcursor.get[Int]("decodedFrames").toOption.get shouldBe frames
    val videoend = record.hcursor.get[Double]("videoPresentedEnd").toOption.get
    val audioend = record.hcursor.get[Double]("audioPresentedEnd").toOption.get
    math.abs(videoend - audioend) should be <= (1.0 / 48000 + 1e-9)
    math.abs(videoend - frames.toDouble / 18) should be < 1e-6
    val stamps = record.hcursor.get[Vector[Double]]("videoFramePts").toOption.get
    stamps should have size frames
    math.abs(stamps.head) should be < 1e-6
    stamps.sliding(2).foreach { pair =>
      pair(1) should be > pair(0)
      math.abs(pair(1) - pair(0) - 1.0 / 18) should be < 2e-6
    }
    record.hcursor.get[Boolean]("muxDtsOrdered").toOption.get shouldBe true
    record.hcursor.get[Boolean]("endCardFramesOpaqueGreen").toOption.get shouldBe true
    record.hcursor.get[String]("decoderStderr").toOption.get.toLowerCase should not include "non-monoton"
  }

  private def _edit_lists(path: Path): Json = {
    val bytes = Files.readAllBytes(path)
    val entries = Vector.newBuilder[Json]
    def _boxes_(start: Int, end: Int): Unit = {
      var offset = start
      while (offset + 8 <= end) {
        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN)
        val shortsize = buffer.getInt(offset).toLong & 0xffffffffL
        val kind = new String(bytes, offset + 4, 4, StandardCharsets.US_ASCII)
        val header = if (shortsize == 1) 16 else 8
        val size = if (shortsize == 1) buffer.getLong(offset + 8) else if (shortsize == 0) end - offset else shortsize
        if (size < header || size > end - offset) throw new IllegalStateException(s"Invalid native MP4 box $kind in $path")
        val body = offset + header
        val limit = offset + size.toInt
        if (Set("moov", "trak", "edts").contains(kind)) _boxes_(body, limit)
        else if (kind == "elst") {
          val version = bytes(body).toInt
          val count = buffer.getInt(body + 4)
          var cursor = body + 8
          (0 until count).foreach { _ =>
            val duration = if (version == 1) buffer.getLong(cursor) else buffer.getInt(cursor).toLong & 0xffffffffL
            val media = if (version == 1) buffer.getLong(cursor + 8) else buffer.getInt(cursor + 4).toLong
            entries += Json.obj("version" -> Json.fromInt(version), "segmentDurationTicks" -> Json.fromLong(duration),
              "mediaTimeTicks" -> Json.fromLong(media))
            cursor += (if (version == 1) 20 else 12)
          }
        }
        offset = limit
      }
    }
    _boxes_(0, bytes.length)
    Json.fromValues(entries.result())
  }

  private def _pcm_samples(bytes: Array[Byte]): Vector[Double] = {
    val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
    Vector.fill(bytes.length / 2)(buffer.getShort().toDouble)
  }

  private def _audio_measurement(decoded: Vector[Double], source: Vector[Double], speechstart: Double): Json = {
    val sourcemarker = (960 until math.min(1920, source.size)).find(index => math.abs(source(index)) > 1000).getOrElse(
      throw new IllegalStateException("Missing independent source early marker in fixture samples 960..1919"))
    val markerstart = math.floor((speechstart + 0.02) * 24000).toInt + 1
    val markerend = math.ceil((speechstart + 0.20) * 24000).toInt
    val encodedmarker = (math.max(0, markerstart) until math.min(markerend, decoded.size))
      .find(index => math.abs(decoded(index)) > 1000).getOrElse(
        throw new IllegalStateException(s"Missing independent encoded early marker strictly within ${speechstart + 0.02}..${speechstart + 0.20} seconds"))
    val offset = encodedmarker.toDouble / 24000 - speechstart - sourcemarker.toDouble / 24000
    val observedstart = speechstart + offset
    val start = observedstart + 0.342
    val end = observedstart + 0.358
    val bins = (0 until 4).map(index => _pcm_rms(decoded, start + index * 0.004, start + (index + 1) * 0.004)).toVector
    val latest = (math.max(0, math.round((observedstart + 0.30) * 24000).toInt) until
      math.min(decoded.size, math.round((observedstart + 0.40) * 24000).toInt))
      .reverse.find(index => math.abs(decoded(index)) > 1000)
    val signalend = latest.map(index => (index + 1).toDouble / 24000).getOrElse(0.0)
    val coverage = latest.map(_ => signalend - observedstart).getOrElse(0.0)
    Json.obj("speechStartSeconds" -> Json.fromDoubleOrNull(speechstart),
      "observedSpeechStartSeconds" -> Json.fromDoubleOrNull(observedstart),
      "observedOffsetSeconds" -> Json.fromDoubleOrNull(offset),
      "sourceMarkerSample" -> Json.fromInt(sourcemarker), "encodedMarkerSample" -> Json.fromInt(encodedmarker),
      "earlyMarkerRms" -> Json.fromDoubleOrNull(_pcm_rms(decoded, observedstart + 0.05, observedstart + 0.07)),
      "lateToneRms" -> Json.fromDoubleOrNull(_pcm_rms(decoded, start, end)),
      "lateToneSubwindowRms" -> Json.fromValues(bins.map(Json.fromDoubleOrNull)),
      "lateSignalEndSeconds" -> Json.fromDoubleOrNull(signalend),
      "lateSignalCoverageSeconds" -> Json.fromDoubleOrNull(coverage),
      "measurementStartSeconds" -> Json.fromDoubleOrNull(start),
      "measurementEndSeconds" -> Json.fromDoubleOrNull(end))
  }

  private def _pcm_rms(samples: Vector[Double], start: Double, end: Double): Double = {
    val first = math.round(start * 24000).toInt
    val last = math.round(end * 24000).toInt
    val window = (first until last).map(index => samples.lift(index).getOrElse(0.0))
    math.sqrt(window.map(value => value * value).sum / window.size)
  }

  private def _complete_audio_evidence(record: Json): Unit = {
    math.abs(record.hcursor.get[Double]("observedOffsetSeconds").toOption.get) should be <= (1.0 / 18)
    record.hcursor.get[Double]("earlyMarkerRms").toOption.get should be > 1000.0
    record.hcursor.get[Double]("lateToneRms").toOption.get should be > 1000.0
    val bins = record.hcursor.get[Vector[Double]]("lateToneSubwindowRms").toOption.get
    bins.size shouldBe 4
    bins.foreach(value => value should be > 1000.0)
    record.hcursor.get[Double]("lateSignalCoverageSeconds").toOption.get should be >= 0.359
  }

  private def _docker(dir: Path, image: String, args: Vector[String]): CozyVideo.VideoCommandResult = {
    val result = CozyVideo.VideoProcessRunner.default.run(Vector("docker", "run", "--rm", "--network=none",
      "-v", s"$dir:/workspace", "-w", "/workspace", image) ++ args, dir)
    if (!result.isSuccess) throw new IllegalStateException(s"Native media observation failed: $result")
    result
  }

  private def _green_card(frame: BufferedImage, full: Boolean = false): (Double, Double, Boolean) = {
    var green = 0L
    var total = 0L
    var opaque = true
    val xs = if (full) 0 until frame.getWidth else frame.getWidth / 4 until frame.getWidth * 3 / 4
    val ys = if (full) 0 until frame.getHeight else frame.getHeight / 4 until frame.getHeight * 3 / 4
    val pixels = xs.size.toLong * ys.size
    for (y <- ys; x <- xs) {
      val rgb = frame.getRGB(x, y)
      val r = (rgb >> 16) & 255
      val g = (rgb >> 8) & 255
      val b = rgb & 255
      if (g > 120 && g > r + 80 && g > b + 80) green += 1
      total += r + g + b
      opaque = opaque && ((rgb >>> 24) == 255)
    }
    (green.toDouble / pixels, total.toDouble / (pixels * 3), opaque)
  }

  private def _review(dir: Path, name: String, runner: CozyVideo.VideoProcessRunner, mode: String, image: Option[String]): String =
    CozyVideo.reviewEvidence(CozyVideo.ReviewEvidenceConfig(dir.resolve("video.json"), dir.resolve(name),
      toolMode = Some(mode), dockerImage = image), CozyVideo.VideoToolRegistry(Vector.empty), runner)

  private def _tail_frame(evidence: Json): Int = {
    val scene = evidence.hcursor.get[Vector[Json]]("parts").toOption.get.head.hcursor.get[Vector[Json]]("scenes").toOption.get.head
    val tail = scene.hcursor.get[Vector[Json]]("frames").toOption.get.find(_.hcursor.get[String]("kind").toOption.contains("tail")).get
    _int(tail, "absoluteFrame")
  }

  private def _props(dir: Path): Json = _json(dir.resolve("target/cozy-video/remotion/dialogue/props.json"))
  private def _write_props(dir: Path, props: Json): Unit = _write(dir.resolve("target/cozy-video/remotion/dialogue/props.json"), props.spaces2)
  private def _scenes(props: Json): Vector[Json] = props.hcursor.get[Vector[Json]]("scenes").toOption.get
  private def _int(json: Json, field: String): Int = json.hcursor.get[Int](field).toOption.get
  private def _json(path: Path): Json = parser.parse(Files.readString(path, StandardCharsets.UTF_8)).toOption.get
  private def _write(path: Path, contents: String): Unit = {
    Files.createDirectories(path.getParent)
    Files.writeString(path, contents, StandardCharsets.UTF_8)
    ()
  }
}
