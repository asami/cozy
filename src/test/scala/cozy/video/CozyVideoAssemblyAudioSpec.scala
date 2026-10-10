package cozy.video

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path, Paths}
import scala.collection.mutable.ArrayBuffer
import io.circe.Json
import org.scalacheck.{Gen, Prop, Test}
import org.scalatest.GivenWhenThen
import org.scalatest.wordspec.AnyWordSpec
import cozy.CozySpecVocabulary

/*
 * @since   Oct.  5, 2026
 * @version Oct.  8, 2026
 */
object CozyVideoAssemblyAudioSpec {
  private final class AssemblyAudioRunner(
    inputs: Vector[Json], inputpaths: Vector[Path], finalsummary: Json, finalpath: Path,
    failmux: Boolean = false, failprobe: Boolean = false, omitstage: Boolean = false
  ) extends CozyVideo.VideoProcessRunner {
    val commands: ArrayBuffer[Vector[String]] = ArrayBuffer.empty
    def run(args: Vector[String], cwd: Path): CozyVideo.VideoCommandResult = {
      commands += args
      val output = _command_path(args, cwd)
      if (args.contains("ffprobe")) {
        val originalindex = inputpaths.indexWhere(_.toAbsolutePath.normalize() == output)
        if (originalindex >= 0)
          CozyVideo.VideoCommandResult(0, inputs(originalindex).noSpaces, "")
        else if (output == finalpath.toAbsolutePath.normalize()) {
          if (failprobe) CozyVideo.VideoCommandResult(1, "", "planned final probe failure")
          else CozyVideo.VideoCommandResult(0, finalsummary.noSpaces, "")
        } else {
          CozyVideo.VideoCommandResult(1, "", s"Undeclared input probe: $output")
        }
      } else {
        Files.createDirectories(output.getParent)
        if (!omitstage) Files.writeString(output, "new-stage", StandardCharsets.UTF_8)
        if (failmux && output == finalpath.toAbsolutePath.normalize())
          CozyVideo.VideoCommandResult(1, "", "planned partial mux failure; Non-monotonous DTS retained")
        else CozyVideo.VideoCommandResult(0, "stage created", "")
      }
    }
  }

  private def _command_path(args: Vector[String], cwd: Path): Path = {
    val path = Paths.get(args.last)
    val translated = if (args.head == "docker" && path.startsWith(Paths.get("/workspace")))
      cwd.resolve(Paths.get("/workspace").relativize(path)) else path
    translated.toAbsolutePath.normalize()
  }
}

final class CozyVideoAssemblyAudioSpec extends AnyWordSpec with GivenWhenThen with CozySpecVocabulary {
  import CozyVideoAssemblyAudioSpec._

  "Public final assembly on the video presentation timeline" should {
    "decode parts independently before one final AAC encode" which {
      "preserve part order and cumulative PCM positions for narrated inputs" in {
        Given("two admitted 130-frame and 131-frame narrated parts at 18fps, a positive second audio origin and previous successful outputs")
        val dir = _fixture(2)
        val second = _summary(131, 18, true)
        val secondstreams = second.hcursor.get[Vector[Json]]("streams").toOption.get
        val inputs = Vector(_summary(130, 18, true), second.mapObject(_.add("format", Json.obj("duration" -> Json.fromString("99")))
          .add("streams", Json.arr(secondstreams.head.mapObject(_.add("duration", Json.fromString("999"))),
          secondstreams.last.mapObject(_.add("start_time", Json.fromString("0.1"))
            .remove("duration_ts").add("duration", Json.fromString("7.1")))))))
        val runner = new AssemblyAudioRunner(inputs, _input_paths(dir, 2), _summary(261, 18, true), dir.resolve("build/final.mp4"))
        When("the public build interprets the selected parts")
        val result = _build(dir, runner)
        Then("exact rational video durations win over unrelated decimal/format durations and each edit timeline is decoded before one AAC mux")
        result should include("parts: 2")
        val commands = runner.commands.filter(_.contains("ffmpeg"))
        val decodes = commands.filter(_.contains("pcm_s16le"))
        decodes.map(args => args(args.indexOf("-i") + 1)) shouldBe Vector(
          dir.resolve("build/parts/part-1.mp4").toString, dir.resolve("build/parts/part-2.mp4").toString)
        decodes.map(_samples) shouldBe Vector(346666L, 349334L)
        decodes.foreach { args =>
          args should contain("0:a:0")
          args should contain("48000")
          args should contain("2")
          args(args.indexOf("-af") + 1) should startWith("aresample=48000:first_pts=0,apad,")
        }
        val extracts = commands.filter(_.contains("-an"))
        extracts should have size 2
        extracts.foreach { args => args should contain("0:v:0"); args should contain("copy"); args should contain("-copyts") }
        val pcmconcat = commands.find(_.last.endsWith("combined.wav")).get
        pcmconcat should contain("copy")
        commands.count(_.contains("aac")) shouldBe 1
        commands.last should contain("1:a:0")
        commands.last should contain("0:v:0")
        commands.last should contain("+faststart")
        val workspace = _processing_workspace(runner, dir)
        _read(workspace.resolve("presentation/video-concat.txt")) should
          include("duration 7.222222222222222222")
        val original = _read(workspace.resolve("concat.txt"))
        original.indexOf("part-1.mp4") should be < original.indexOf("part-2.mp4")
        original should not include "duration"
        _read(dir.resolve("build/final.mp4")) shouldBe "new-stage"
        _read(dir.resolve("build/manifest.json")) should include("\"validated\"")
      }

      "keep fractional boundaries within one sample without accumulated rounding loss" in {
        Given("integer and fractional frame durations with mixed audio presence")
        val cases = for {
          fps <- Gen.oneOf(12, 18, 24, 29, 30)
          count <- Gen.choose(2, 5)
          frames <- Gen.listOfN(count, Gen.choose(1, 131))
          audio <- Gen.listOfN(count - 1, Gen.oneOf(true, false))
        } yield (fps, frames.toVector, true +: audio.toVector)
        val property = Prop.forAll(cases) { case (fps, frames, audio) =>
          val dir = _fixture(frames.size)
          val runner = new AssemblyAudioRunner(frames.zip(audio).map { case (n, present) => _summary(n, fps, present) }, _input_paths(dir, frames.size),
            _summary(frames.sum, fps, true), dir.resolve("build/final.mp4"))
          _build(dir, runner)
          val allocations = runner.commands.filter(_.contains("pcm_s16le")).map(_samples).toVector
          val boundaries = allocations.scanLeft(BigInt(0))(_ + _).tail
          val frameends = frames.scanLeft(0)(_ + _).tail
          allocations.forall(_ > 0) && boundaries.zip(frameends).forall { case (samples, end) =>
            val remaining = BigInt(end) * 48000 - samples * fps
            remaining >= 0 && remaining < fps
          } && runner.commands.count(_.contains("anullsrc=r=48000:cl=stereo")) == audio.count(!_)
        }
        When("twenty deterministic ScalaCheck timeline use cases are built on the public boundary")
        val result = Test.check(Test.Parameters.default.withMinSuccessfulTests(20)
          .withInitialSeed(71402L), property)
        Then("every cumulative PCM boundary is at or just below its independent frame position by less than one 48k sample")
        result.passed shouldBe true
        result.succeeded should be >= 20
      }

      "admit exact decimal stream durations when rational timing is absent" in {
        Given("a narrated half-second input and an audio-free 1.25-second input without rational video duration fields")
        val dir = _fixture(2)
        val inputs = Vector(_summary(1, 2, true), _summary(5, 4, false)).map { summary =>
          val streams = summary.hcursor.get[Vector[Json]]("streams").toOption.get
          summary.mapObject(_.add("streams", Json.fromValues(streams.map { stream =>
            if (stream.hcursor.get[String]("codec_type").toOption.contains("video"))
              stream.mapObject(_.remove("duration_ts").remove("time_base"))
            else stream
          })))
        }
        val runner = new AssemblyAudioRunner(inputs, _input_paths(dir, 2), _summary(7, 4, true), dir.resolve("build/final.mp4"))
        When("public final build admits the decimal video presentations")
        _build(dir, runner)
        Then("finite PCM allocations cover 0.5 and 1.25 seconds exactly and silence belongs only to the audio-free interval")
        runner.commands.filter(_.contains("pcm_s16le")).map(_samples).toVector shouldBe Vector(24000L, 60000L)
        runner.commands.count(_.contains("anullsrc=r=48000:cl=stereo")) shouldBe 1
        _read(_processing_workspace(runner, dir).resolve("presentation/video-concat.txt")) should include("duration 1.250000000000000000")
      }
    }

    "retain legitimate audio-free presentations" which {
      "copy ordered video-only inputs without synthesizing audio" in {
        Given("two admitted video-only parts with their original public concat order")
        val dir = _fixture(2)
        val runner = new AssemblyAudioRunner(Vector.fill(2)(_summary(130, 18, false)), _input_paths(dir, 2), _summary(260, 18, false), dir.resolve("build/final.mp4"))
        When("the public final build assembles them")
        _build(dir, runner)
        Then("one copyts mux retains video alone and introduces no audio source, decode or AAC encode")
        val muxes = runner.commands.filter(_.contains("ffmpeg"))
        muxes should have size 1
        muxes.head should contain("-copyts")
        muxes.head should contain("copy")
        muxes.head should not contain "aac"
        muxes.head should not contain "pcm_s16le"
        val concatpath = _processing_workspace(runner, dir).resolve("concat.txt")
        muxes.head should contain(concatpath.toString)
        val concat = _read(concatpath)
        concat.indexOf("part-1.mp4") should be < concat.indexOf("part-2.mp4")
      }
    }

    "process the selected inputs in their declared order" which {
      "report mux, probe, presentation and missing-result failures at the selected final destination" in {
        Vector("partial mux", "probe tool", "invalid presentation", "missing stage").foreach { cause =>
          Given(s"a selected Host part, a previous final MP4 at the old fixed stage, its adjacent manifest and $cause")
          val finalpath = "target/cozy-video/ffmpeg/rendered.mp4"
          val dir = _fixture(1, finalpath = finalpath)
          if (cause == "missing stage") Files.delete(dir.resolve(finalpath))
          val normal = _summary(130, 18, true)
          val streams = normal.hcursor.get[Vector[Json]]("streams").toOption.get
          val summary = if (cause == "invalid presentation") normal.mapObject(_.add("streams", Json.arr(
            streams.head, streams.last.mapObject(_.remove("duration_ts").add("duration", Json.fromString("8"))))))
            else normal
          val runner = new AssemblyAudioRunner(Vector(normal), _input_paths(dir, 1), summary, dir.resolve(finalpath),
            failmux = cause == "partial mux", failprobe = cause == "probe tool", omitstage = cause == "missing stage")
          When("the public Host build writes, muxes and admits the selected final presentation")
          val failure = intercept[Throwable] { _build(dir, runner) }
          Then("the actionable failure leaves diagnostics and does not accept a new manifest")
          failure.getMessage should not be empty
          _unaccepted_manifest(dir, finalpath)
          val output = _mux_output(runner, dir)
          output shouldBe dir.resolve(finalpath)
          val workspace = _processing_workspace(runner, dir)
          workspace.getParent shouldBe dir.resolve("target/cozy-video/ffmpeg")
          workspace.getFileName.toString should startWith("assembly-")
          Files.isRegularFile(output.resolveSibling("rendered.mp4.stderr.txt")) shouldBe true
          if (cause == "partial mux")
            _read(output.resolveSibling("rendered.mp4.stderr.txt")) should include("Non-monotonous DTS retained")
        }
      }

      "preserve selected Host bytes and order at old rendered, presentation, parts and concat paths" in {
        val oldrendered = "target/cozy-video/ffmpeg/rendered.mp4"
        val cases = Vector(Vector(oldrendered), Vector(oldrendered, "build/parts/part-2.mp4"),
          Vector("target/cozy-video/ffmpeg/presentation/video-01.mp4", "build/parts/part-2.mp4"),
          Vector("target/cozy-video/ffmpeg/parts/part-01.mp4", "build/parts/part-2.mp4"),
          Vector("target/cozy-video/ffmpeg/concat.txt", "build/parts/part-2.mp4"))
        cases.foreach { partpaths =>
          Vector(false, true).foreach { failmux =>
            Given(s"selected narrated Host inputs at ${partpaths.mkString(", ")}, previous final files and mux failure=$failmux")
            val dir = _fixture(partpaths.size, partpaths)
            val inputpaths = partpaths.map(dir.resolve(_))
            val inputs = partpaths.indices.map(index => _summary(130 + index, 18, true)).toVector
            val runner = new AssemblyAudioRunner(inputs, inputpaths,
              _summary(partpaths.indices.map(130 + _).sum, 18, true), dir.resolve("build/final.mp4"), failmux = failmux)
            When("public Host assembly interprets the exact selected input paths")
            if (failmux) {
              val failure = intercept[Throwable] { _build(dir, runner) }
              Then("the failed mux identifies the failed operation and every selected input remains readable")
              failure.getMessage should include("planned partial mux failure")
              _unaccepted_manifest(dir)
            } else {
              _build(dir, runner)
              Then("the declared final output is validated while every selected original survives")
              _read(dir.resolve("build/final.mp4")) shouldBe "new-stage"
              _read(dir.resolve("build/manifest.json")) should include("\"validated\"")
            }
            inputpaths.zipWithIndex.foreach { case (path, index) =>
              _read(path) shouldBe s"original-part-${index + 1}"
            }
            val media = runner.commands.filter(_.contains("ffmpeg"))
            media.dropRight(1).map(args => _command_path(args, dir)).foreach { output =>
              inputpaths should not contain output
              output.startsWith(_processing_workspace(runner, dir)) shouldBe true
            }
            val concat = _read(_processing_workspace(runner, dir).resolve("concat.txt"))
            inputpaths.foreach(path => concat should include(s"file '$path'"))
            if (inputpaths.size > 1) {
              concat.indexOf(inputpaths.head.toString) should be < concat.indexOf(inputpaths.last.toString)
              runner.commands.filter(_.contains("ffprobe")).take(inputpaths.size).map(_.last).toVector shouldBe inputpaths.map(_.toString)
              media.filter(_.contains("pcm_s16le")).map(_samples).toVector shouldBe Vector(346666L, 349334L)
              media.filter(_.contains("-an")).map(args => args(args.indexOf("-i") + 1)).toVector shouldBe inputpaths.map(_.toString)
            }
          }
        }
      }

      "read selected Docker inputs at former processing paths across failed and successful muxes" in {
        Given("Docker inputs at the old parts and presentation stages and successful final media and manifest at old rendered.mp4")
        val partpaths = Vector("target/cozy-video/ffmpeg/parts/part-01.mp4", "target/cozy-video/ffmpeg/presentation/video-01.mp4")
        val finalpath = "target/cozy-video/ffmpeg/rendered.mp4"
        val dir = _fixture(2, partpaths, finalpath)
        val inputpaths = partpaths.map(dir.resolve(_))
        val inputs = Vector(_summary(130, 18, true), _summary(131, 18, true))
        val failed = new AssemblyAudioRunner(inputs, inputpaths, _summary(261, 18, true), dir.resolve(finalpath), failmux = true)
        When("the public Docker build partially writes its final mux and fails")
        val failure = intercept[Throwable] { _build(dir, failed, "docker") }
        Then("both original inputs remain intact and the failed attempt is not accepted by a manifest")
        failure.getMessage should include("planned partial mux failure")
        _unaccepted_manifest(dir, finalpath)
        inputpaths.zipWithIndex.foreach { case (path, index) => _read(path) shouldBe s"original-part-${index + 1}" }
        val failedworkspace = _processing_workspace(failed, dir)
        _read(dir.resolve(finalpath).resolveSibling("rendered.mp4.stderr.txt")) should include("Non-monotonous DTS retained")
        Given("the same admitted originals after the failed attempt and a successful Docker runner")
        val successful = new AssemblyAudioRunner(inputs, inputpaths, _summary(261, 18, true), dir.resolve(finalpath))
        When("the public Docker build is attempted again")
        _build(dir, successful, "docker")
        Then("a different private workspace validates the output and preserves original input bytes and declared order")
        val workspace = _processing_workspace(successful, dir)
        workspace should not be failedworkspace
        workspace.getParent shouldBe dir.resolve("target/cozy-video/ffmpeg")
        workspace.getFileName.toString should startWith("assembly-")
        inputpaths.zipWithIndex.foreach { case (path, index) =>
          _read(path) shouldBe s"original-part-${index + 1}"
        }
        successful.commands.filter(_.contains("ffprobe")).take(2).map(args => _command_path(args, dir)).toVector shouldBe inputpaths
        successful.commands.filter(_.contains("pcm_s16le")).map(_samples).toVector shouldBe Vector(346666L, 349334L)
        val concat = _read(workspace.resolve("concat.txt"))
        concat.indexOf(inputpaths.head.toString.stripPrefix(dir.toString)) should be < concat.indexOf(inputpaths.last.toString.stripPrefix(dir.toString))
        _read(dir.resolve(finalpath)) shouldBe "new-stage"
        _read(dir.resolve(finalpath).getParent.resolve("manifest.json")) should include("\"validated\"")
      }
    }

    "report genuine media and I/O failures" which {
      "reject missing, malformed, negative, non-finite and overrunning input metadata before mux" in {
        val valid = _summary(130, 18, true)
        val video = valid.hcursor.get[Vector[Json]]("streams").toOption.get.head
        val audio = valid.hcursor.get[Vector[Json]]("streams").toOption.get.last
        val invalid = Vector(
          Json.obj("streams" -> Json.arr()),
          Json.obj("streams" -> Json.arr(video.mapObject(_.remove("duration_ts").remove("duration")))),
          Json.obj("streams" -> Json.arr(video.mapObject(_.add("time_base", Json.fromString("broken"))))),
          Json.obj("streams" -> Json.arr(video.mapObject(_.remove("duration_ts").add("duration", Json.fromString("NaN"))))),
          Json.obj("streams" -> Json.arr(video.mapObject(_.remove("duration_ts").add("duration", Json.fromString("not-a-number"))))),
          Json.obj("streams" -> Json.arr(video.mapObject(_.add("duration_ts", Json.fromInt(-1))))),
          Json.obj("streams" -> Json.arr(video.mapObject(_.remove("start_time")))),
          Json.obj("streams" -> Json.arr(video.mapObject(_.add("start_time", Json.fromString("0.1"))))),
          Json.obj("streams" -> Json.arr(video, audio.mapObject(_.add("start_time", Json.fromString("-0.1"))))),
          Json.obj("streams" -> Json.arr(video, audio.mapObject(_.remove("duration_ts").add("duration", Json.fromString("Infinity"))))),
          Json.obj("streams" -> Json.arr(video, audio.mapObject(_.remove("duration_ts").add("duration", Json.fromString("7.264222222222222222")))))
        )
        invalid.foreach { summary =>
          Given("a bad first input presentation, including the former 42ms audio overhang, and prior final MP4/manifest")
          val dir = _fixture(2)
          val runner = new AssemblyAudioRunner(Vector(summary, valid), _input_paths(dir, 2), valid, dir.resolve("build/final.mp4"))
          When("public build probes the actual selected input")
          val failure = intercept[Throwable] { _build(dir, runner) }
          Then("the fault identifies that input and requests rerender before mux")
          failure.getMessage should include("part-1.mp4")
          failure.getMessage should include("Rerender")
          runner.commands.exists(_.contains("ffmpeg")) shouldBe false
          _unaccepted_manifest(dir)
        }
      }

      "reject missing results, partial mux failures and failed final probe validation" in {
        Vector("partial mux", "probe tool", "contradictory audio", "missing stage", "no video", "zero format").foreach { cause =>
          Given(s"one rendered part, previous final outputs, and $cause failure")
          val dir = _fixture(1)
          if (cause == "missing stage") Files.delete(dir.resolve("build/final.mp4"))
          val normal = _summary(130, 18, true)
          val contradictory = normal.mapObject(_.add("streams", Json.arr(
            normal.hcursor.get[Vector[Json]]("streams").toOption.get.head,
            Json.obj("codec_type" -> Json.fromString("audio"), "start_time" -> Json.fromString("0"), "duration" -> Json.fromString("8")))))
          val summary = cause match {
            case "contradictory audio" => contradictory
            case "no video" => Json.obj("format" -> Json.obj("duration" -> Json.fromString("1")), "streams" -> Json.arr())
            case "zero format" => normal.mapObject(_.add("format", Json.obj("duration" -> Json.fromString("0"))))
            case _ => normal
          }
          val runner = new AssemblyAudioRunner(Vector(normal), _input_paths(dir, 1), summary, dir.resolve("build/final.mp4"),
            failmux = cause == "partial mux", failprobe = cause == "probe tool", omitstage = cause == "missing stage")
          When("the public final build reaches its mux and final presentation admission")
          val failure = intercept[Throwable] { _build(dir, runner) }
          Then("the genuine failure is reported and no successful manifest is written")
          failure.getMessage should not be empty
          if (cause == "partial mux") {
            failure.getMessage should include("Non-monotonous DTS retained")
            _read(_mux_output(runner, dir).resolveSibling("final.mp4.stderr.txt")) should include("Non-monotonous DTS retained")
          }
          _unaccepted_manifest(dir)
        }
      }
    }
  }

  private def _summary(frames: Int, fps: Int, audio: Boolean): Json = {
    val duration = Json.fromString((BigDecimal(frames) / BigDecimal(fps)).toString)
    val video = Json.obj("codec_type" -> Json.fromString("video"), "duration_ts" -> Json.fromInt(frames),
      "time_base" -> Json.fromString(s"1/$fps"), "duration" -> duration, "start_time" -> Json.fromString("0"))
    val sound = video.mapObject(_.add("codec_type", Json.fromString("audio")))
    Json.obj("format" -> Json.obj("duration" -> duration), "streams" -> Json.fromValues(if (audio) Vector(video, sound) else Vector(video)))
  }

  private def _samples(args: Vector[String]): Long =
    args(args.indexOf("-af") + 1).split("atrim=end_sample=")(1).takeWhile(_.isDigit).toLong

  private def _input_paths(dir: Path, count: Int): Vector[Path] =
    (1 to count).map(index => dir.resolve(s"build/parts/part-$index.mp4")).toVector

  private def _mux_output(runner: AssemblyAudioRunner, dir: Path): Path =
    _command_path(runner.commands.filter(_.contains("ffmpeg")).last, dir)

  private def _processing_workspace(runner: AssemblyAudioRunner, dir: Path): Path = {
    val args = runner.commands.filter(_.contains("ffmpeg")).last
    val list = _command_path(Vector(args.head, args(args.indexOf("-i") + 1)), dir)
    if (list.getFileName.toString == "video-concat.txt") list.getParent.getParent else list.getParent
  }

  private def _fixture(count: Int, partpaths: Vector[String] = Vector.empty, finalpath: String = "build/final.mp4"): Path = {
    val root = Paths.get(sys.props("user.dir")).resolve("target/test-generated/video-assembly-audio")
    Files.createDirectories(root)
    val dir = Files.createTempDirectory(root, "assembly-")
    _write(dir.resolve("script.json"), """{"scenes":[{"id":"scene","duration":1.0,"line":"Retain complete narration"}]}""")
    val parts = (1 to count).map { index =>
      val partpath = partpaths.lift(index - 1).getOrElse(s"build/parts/part-$index.mp4")
      _write(dir.resolve(partpath), s"original-part-$index")
      Json.obj("id" -> Json.fromString(s"part-$index"), "type" -> Json.fromString("dialogue"),
        "script" -> Json.fromString("script.json"), "output" -> Json.fromString(partpath))
    }
    _write(dir.resolve("video.json"), Json.obj("output" -> Json.fromString(finalpath), "parts" -> Json.fromValues(parts)).spaces2)
    _write(dir.resolve(finalpath), "previous-video")
    _write(dir.resolve(finalpath).getParent.resolve("manifest.json"), "previous-manifest")
    dir
  }

  private def _build(dir: Path, runner: CozyVideo.VideoProcessRunner, mode: String = "host"): String =
    CozyVideo.build(CozyVideo.BuildConfig(dir.resolve("video.json"), dryRun = false, checkTools = false, toolMode = Some(mode)),
      CozyVideo.VideoToolRegistry(Vector.empty), runner)

  private def _unaccepted_manifest(dir: Path, finalpath: String = "build/final.mp4"): Unit = {
    _read(dir.resolve(finalpath).getParent.resolve("manifest.json")) should not include "\"validated\""
  }

  private def _read(path: Path): String = Files.readString(path, StandardCharsets.UTF_8)
  private def _write(path: Path, content: String): Unit = {
    Files.createDirectories(path.getParent)
    Files.writeString(path, content, StandardCharsets.UTF_8)
    ()
  }
}
