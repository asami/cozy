package cozy.video

import io.circe.{Json, parser}
import java.awt.image.BufferedImage
import java.nio.file.{Files, Path, Paths}
import javax.imageio.ImageIO
import scala.collection.mutable.ArrayBuffer
import org.scalacheck.{Gen, Prop, Test}
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import CozyVideoTools.{VideoExecutionConfig, VideoToolMode}

/*
 * @since   Oct. 11, 2026
 * @version Oct. 11, 2026
 * @author  ASAMI, Tomoharu
 */
final class CozyVideoHardwareEncodingSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  import CozyVideoHardwareEncodingSpec._

  "Explicit hardware operating settings" should {
    "resolve independent fields" which {
      "retain layered name and bitrate origins and normalize numeric JSON" in {
        Given("a home hardware name, project host FFmpeg and highest integral JSON bitrate")
        _with_project { (root, home) =>
          _fixture(root)
          _write(home.resolve(".cozy/config.json"), _encoder(Json.obj("name" -> Json.fromString("h264_videotoolbox"))))
          _write(root.resolve("conf/cozy/config.json"), _operating(Json.obj("bitrate" -> Json.fromString("3M"))))
          _write(root.resolve(".cozy/config.json"), _encoder(Json.obj("bitrate" -> Json.fromBigDecimal(BigDecimal("4500000.0")))))
          When("public settings and inspection interpret all operating layers")
          val execution = VideoExecutionConfig.create(root, None, None, None, None)
          val inspection = _inspect(root)
          Then("each encoder field keeps its winning origin and the numeric bitrate becomes integer text")
          execution.encoderName shouldBe "h264_videotoolbox"
          execution.encoderOrigin should include(home.resolve(".cozy/config.json").toString)
          execution.encoderBitrate shouldBe Some("4500000")
          execution.encoderBitrateOrigin should include(root.resolve(".cozy/config.json").toString)
          execution.selected("ffmpeg").mode shouldBe VideoToolMode.Host
          inspection should include("effectiveEncoderBitrate: 4500000")
          inspection should include("video.encoder.bitrate")
          VideoExecutionConfig(VideoToolMode.Host, "image", "url", encoderName = "h264_videotoolbox").externalFrameEncoding shouldBe true
        }
      }

      "accept positive integer strings and k/M forms through normal inspection" in {
        Given("positive bitrate strings, optional surrounding spaces and a signed-64-bit maximum")
        Vector("1", " 2500k ", "6M", Long.MaxValue.toString).foreach { bitrate =>
          _with_project { (root, _) =>
            _fixture(root)
            _write(root.resolve(".cozy/config.json"), _operating(Json.obj("name" -> Json.fromString("h264_videotoolbox"), "bitrate" -> Json.fromString(bitrate))))
            When("inspection resolves the explicitly authored bitrate")
            val inspection = _inspect(root)
            Then("the effective bitrate uses the trimmed valid argv text")
            inspection should include(s"effectiveEncoderBitrate: ${bitrate.trim}")
          }
        }
      }
    }

    "reject invalid authored or resolved selections" which {
      "validate every encoder layer even below a valid hardware override" in {
        Given("unknown names/fields, malformed encoder maps and invalid bitrate types, grammar or range")
        val invalidbitrates = Vector(Json.Null, Json.True, Json.obj(), Json.arr(Json.fromInt(1)),
          Json.fromInt(0), Json.fromInt(-1), Json.fromDoubleOrNull(1.5),
          Json.fromBigInt(BigInt(Long.MaxValue) + 1)) ++
          Vector("", " ", "0", "-1", "1.5", "01", "1m", "1K", "1G", "+1", "1e3", "1 k", "9223372036854776k", "9223372036855M").map(Json.fromString)
        val invalidencoders = Vector(Json.Null, Json.arr(), Json.fromString("hardware"), Json.True,
          Json.obj("unknown" -> Json.fromInt(1)), Json.obj("name" -> Json.fromString("hevc_videotoolbox")),
          Json.obj("name" -> Json.fromString(" ")), Json.obj("name" -> Json.fromInt(1))) ++
          invalidbitrates.map(value => Json.obj("bitrate" -> value))
        invalidencoders.foreach { encoder =>
          _with_project { (root, home) =>
            _fixture(root)
            _write(home.resolve(".cozy/config.json"), _encoder(encoder))
            _write(root.resolve(".cozy/config.json"), _operating(Json.obj("name" -> Json.fromString("h264_videotoolbox"), "bitrate" -> Json.fromString("6M"))))
            val runner = new HardwareRunner()
            When("public render resolves the full authored layer set")
            val failure = intercept[Throwable](_render(root, runner))
            Then("invalid lower input fails before any producer despite a valid highest override")
            failure.getMessage should not be empty
            runner.commands shouldBe empty
          }
        }
      }

      "fail hardware with Docker FFmpeg and software with a retained bitrate before producing" in {
        Given("final hardware/Docker and cross-layer software/bitrate contradictions")
        _with_project { (root, home) =>
          _fixture(root)
          _write(root.resolve(".cozy/config.json"), _encoder(Json.obj("name" -> Json.fromString("h264_videotoolbox"))))
          val docker = new HardwareRunner()
          When("public render resolves hardware against the unconfigured Docker tool default")
          val dockerfailure = intercept[Throwable](_render(root, docker))
          Then("the unresolved host prerequisite stops rendering")
          dockerfailure.getMessage should include("requires explicitly selected host FFmpeg")
          docker.commands shouldBe empty
          Given("a lower hardware bitrate shadowed only by a higher software name")
          _write(home.resolve(".cozy/config.json"), _encoder(Json.obj("name" -> Json.fromString("h264_videotoolbox"), "bitrate" -> Json.fromString("6M"))))
          _write(root.resolve(".cozy/config.json"), _operating(Json.obj("name" -> Json.fromString("libx264"))))
          val software = new HardwareRunner()
          When("public render independently resolves name and bitrate")
          val softwarefailure = intercept[Throwable](_render(root, software))
          Then("the inherited bitrate contradicts software instead of being silently discarded")
          softwarefailure.getMessage should include("contradicts libx264")
          software.commands shouldBe empty
        }
      }
    }
  }

  "Selected hardware producer policy" should {
    "encode the complete composition" which {
      "apply each profile bitrate without software controls and preserve final-card/audio endings" in {
        Given("all three authored quality profiles on Docker Remotion and host VideoToolbox")
        Vector("lightweight" -> "2M", "standard" -> "6M", "quality" -> "12M").foreach { case (policy, bitrate) =>
          _with_project { (root, _) =>
            _fixture(root, policy = policy)
            _hardware(root)
            val runner = new HardwareRunner()
            When("public render interprets the complete PNG/PCM handoff and selected encoder")
            val rendered = _render(root, runner)
            Then("the profile bitrate requires hardware and no x264 controls reach that producer")
            rendered should include("remotion")
            runner.commands should have size 2
            runner.commands.head.head shouldBe "docker"
            runner.commands.head should contain("renderer-image")
            runner.commands.head should contain("--frame-audio")
            val args = runner.commands.last
            _hardware_expectations(args, bitrate)
            args should contain("yuv420p")
            args should contain("320k")
            args should contain("18000")
            args should not contain "-shortest"
            val props = runner.compositions.head
            val timing = props.hcursor.downField("timing")
            timing.get[Int]("contentFrames").toOption shouldBe Some(42)
            timing.get[Int]("finalPageStartFrame").toOption shouldBe Some(42)
            timing.get[Int]("finalPageHoldFrames").toOption shouldBe Some(24)
            timing.get[Int]("totalFrames").toOption shouldBe Some(66)
            _value(args, "-frames:v") shouldBe "66"
            _value(args, "-af") should include("atrim=end_sample=264000,")
            _value(args, "-video_track_timescale") shouldBe "12000"
            _value(args, "-movie_timescale") shouldBe "48000"
            _value(args, "-ar") shouldBe "48000"
            _value(args, "-ac") shouldBe "2"
            val workspace = root.resolve("target/cozy-video/remotion/narrated")
            val handoff = _json(workspace.resolve("frame-audio.json"))
            handoff.hcursor.get[Vector[String]]("frames").toOption.get should have size 66
            handoff.noSpaces should not include "/workspace/"
            handoff.noSpaces should not include "assetsInfo"
            Files.readString(workspace.resolve("encode-frames.txt")) should endWith("frame-00065.png'\n")
            val evidence = _json(workspace.resolve("encoding-result.json")).hcursor
            evidence.downField("effectiveEncoder").get[String]("bitrate").toOption shouldBe Some(bitrate)
            evidence.downField("effectiveEncoder").get[String]("bitrateOrigin").toOption shouldBe Some("profile-default")
            evidence.downField("effectiveEncoder").get[Boolean]("hardwareRequired").toOption shouldBe Some(true)
          }
        }
      }

      "project explicit bitrate intent beside authored controls and keep build as stream copy with independent probe" in {
        Given("a standard authored CRF/preset profile with explicit host hardware bitrate")
        _with_project { (root, _) =>
          _fixture(root)
          _hardware(root, Some(Json.fromString("4500k")))
          val runner = new HardwareRunner()
          When("public inspection, dry-run, render and build interpret the same encoder selection")
          val inspection = _inspect(root)
          val dryrun = CozyVideo.build(CozyVideo.BuildConfig(root.resolve("video.yaml"), dryRun = true, checkTools = false), CozyVideo.VideoToolRegistry(Vector.empty))
          _render(root, runner)
          val built = CozyVideo.build(CozyVideo.BuildConfig(root.resolve("video.yaml"), dryRun = false, checkTools = false), CozyVideo.VideoToolRegistry(Vector.empty), runner)
          Then("presentation separates authored settings from effective hardware controls and build never re-encodes")
          Vector(inspection, dryrun).foreach { output =>
            output should include("encodingSettingsRole: authored")
            output should include("encodingPolicy: standard")
            output should include("encodingCrf: 21")
            output should include("encodingX264Preset: fast")
            output should include("encoder: h264_videotoolbox")
            output should include("effectiveEncoderBitrate: 4500k")
            output should include("video.encoder.bitrate")
            output should include("hardwareRequired: true (selection intent)")
            output should include("activeEncoderCrf: none")
            output should include("activeEncoderX264Preset: none")
          }
          built should include("parts: 1")
          runner.commands.count(_.contains("h264_videotoolbox")) shouldBe 1
          val concat = runner.commands.find(_.contains("copy")).get
          concat.head shouldBe "/selected/ffmpeg"
          _value(concat, "-c") shouldBe "copy"
          val probe = runner.commands.find(_.contains("/selected/ffprobe")).get
          probe.head shouldBe "docker"
          probe should contain("probe-image")
          val manifest = _json(root.resolve("build/manifest.json")).hcursor
          manifest.downField("encoding").get[Int]("crf").toOption shouldBe Some(21)
          manifest.get[String]("encodingSettingsRole").toOption shouldBe Some("authored")
          manifest.downField("effectiveEncoder").focus shouldBe _json(root.resolve("target/cozy-video/remotion/narrated/encoding-result.json")).hcursor.downField("effectiveEncoder").focus
          manifest.downField("effectiveEncoder").get[String]("bitrate").toOption shouldBe Some("4500k")
          manifest.downField("effectiveEncoder").get[Option[Int]]("crf").toOption shouldBe Some(None)
        }
      }

      "sample positive explicit bitrates and all profiles with complete timeline handoff interpretation" in {
        Given("generated positive bitrate integers, profile selections and frame rates")
        _with_project { (root, _) =>
          val cases = for {
            bitrate <- Gen.choose(1, 12000000)
            policy <- Gen.oneOf("lightweight", "standard", "quality")
            fps <- Gen.oneOf(12, 18, 24, 29, 30)
          } yield (bitrate, policy, fps)
          val property = Prop.forAll(cases) { case (bitrate, policy, fps) =>
            _fixture(root, policy = policy, fps = fps)
            _hardware(root, Some(Json.fromInt(bitrate)))
            val runner = new HardwareRunner()
            _render(root, runner)
            val props = runner.compositions.head
            val count = props.hcursor.downField("timing").get[Int]("totalFrames").toOption.get
            val samples = BigInt(count) * 48000 / fps
            val args = runner.commands.last
            _value(args, "-b:v") == bitrate.toString && _value(args, "-frames:v") == count.toString &&
              _value(args, "-af").contains(s"atrim=end_sample=$samples,") &&
              BigInt(count) * 48000 - samples * fps >= 0 && BigInt(count) * 48000 - samples * fps < fps &&
              !args.contains("-crf") && !args.contains("-preset") && !args.contains("-tune") &&
              _value(args, "-allow_sw") == "0" && _value(args, "-require_sw") == "0"
          }
          When("ScalaCheck interprets fifteen normal public render requests with actual handoff parsing")
          val result = Test.check(Test.Parameters.default.withMinSuccessfulTests(15).withInitialSeed(71602L), property)
          Then("positive explicit bitrates and exact frame/floor-sample endings hold across profile and rate choices")
          result.passed shouldBe true
          result.succeeded should be >= 15
        }
      }

      "reach the same hardware policy from public simple-java2d rendering" in {
        Given("selected host Pillow and FFmpeg with a quality profile and combined PCM input")
        _with_project { (root, _) =>
          _fixture(root, policy = "quality", finalpage = "none")
          _hardware(root)
          val runner = new HardwareRunner()
          When("public simple-java2d rendering interprets its still-frame producer")
          CozyVideo.render(CozyVideo.RenderConfig(root.resolve("video.yaml"), "simple-java2d"), CozyVideo.VideoToolRegistry(Vector.empty), runner)
          Then("the selected producer gets the shared 12M hardware options and retains shortest/audio behavior")
          runner.commands.head.head shouldBe "/selected/python"
          _hardware_expectations(runner.commands.last, "12M")
          runner.commands.last should contain("-shortest")
          _value(runner.commands.last, "-c:a") shouldBe "aac"
        }
      }
    }

    "stop on selected initialization or handoff failure" which {
      "preserve actual external encoder exit/stderr and ordinary partial output without retry" in {
        Given("successful frame production followed by selected hardware initialization failure")
        _with_project { (root, _) =>
          _fixture(root)
          _hardware(root)
          val runner = new HardwareRunner(failhardware = true)
          When("public render observes the failing hardware encoder")
          val failure = intercept[Throwable](_render(root, runner))
          Then("its actual exit and stderr remain recorded and no alternative codec or environment runs")
          failure.getMessage should include("hardware initialization refused")
          runner.commands should have size 2
          runner.commands.last should contain("h264_videotoolbox")
          runner.commands.exists(_.contains("libx264")) shouldBe false
          Files.isRegularFile(root.resolve("build/parts/narrated.mp4")) shouldBe true
          val evidence = _json(root.resolve("target/cozy-video/remotion/narrated/encoding-result.json")).hcursor
          evidence.get[Int]("exitCode").toOption shouldBe Some(19)
          evidence.get[String]("stderr").toOption shouldBe Some("hardware initialization refused")
          evidence.downField("effectiveEncoder").get[Boolean]("hardwareRequired").toOption shouldBe Some(true)
        }
      }

      "stop simple-java2d on hardware initialization failure with its diagnostics and partial output" in {
        Given("a still producer whose selected host hardware encoder writes a partial output then fails")
        _with_project { (root, _) =>
          _fixture(root, finalpage = "none")
          _hardware(root)
          val runner = new HardwareRunner(failhardware = true)
          When("public simple-java2d render interprets the selected initialization failure")
          val failure = intercept[Throwable](CozyVideo.render(CozyVideo.RenderConfig(root.resolve("video.yaml"), "simple-java2d"), CozyVideo.VideoToolRegistry(Vector.empty), runner))
          Then("the actual exit and stderr surface without another encoder call")
          failure.getMessage should include("exit=19")
          failure.getMessage should include("hardware initialization refused")
          runner.commands should have size 2
          runner.commands.exists(_.contains("libx264")) shouldBe false
          Files.isRegularFile(root.resolve("build/parts/narrated.mp4")) shouldBe true
        }
      }

      "require valid hardware encodingPolicy in composition props before invoking FFmpeg" in {
        Given("hardware frame production with omitted or invalid planned encodingPolicy")
        Vector[Json => Json](json => json.mapObject(_.remove("encodingPolicy")),
          json => json.mapObject(_.add("encodingPolicy", Json.fromString("automatic")))).foreach { change =>
          _with_project { (root, _) =>
            _fixture(root)
            _hardware(root)
            val runner = new HardwareRunner(propschange = change)
            When("public render consumes the producer's planned props and handoff")
            val failure = intercept[Throwable](_render(root, runner))
            Then("missing policy stops before hardware options can be guessed")
            failure.getMessage should include("requires valid planned encodingPolicy")
            runner.commands should have size 1
          }
        }
      }
    }
  }

  "Software compatibility" should {
    "retain unconfigured renderMedia and explicit libx264 legacy props" in {
      Given("a project with no encoder setting and the same project with explicit software handoff")
      _with_project { (root, _) =>
        _fixture(root)
        val baseline = new HardwareRunner()
        When("the unconfigured public renderer produces media")
        _render(root, baseline)
        Then("Docker renderMedia remains the sole producer")
        baseline.commands should have size 1
        baseline.commands.head should not contain "--frame-audio"
        Given("explicit software encoding and legacy props without encodingPolicy")
        _write(root.resolve(".cozy/config.json"), _operating(Json.obj("name" -> Json.fromString("libx264"))))
        val software = new HardwareRunner(propschange = json => json.mapObject(_.remove("encodingPolicy")))
        When("the explicit software render consumes its complete frame/audio handoff")
        _render(root, software)
        Then("the established CRF and preset remain active without a hardware bitrate")
        software.commands should have size 2
        val args = software.commands.last
        args should contain("libx264")
        _value(args, "-crf") shouldBe "21"
        _value(args, "-preset") shouldBe "fast"
        args should not contain "-b:v"
        args should not contain "-allow_sw"
      }
    }
  }

  private def _hardware_expectations(args: Vector[String], bitrate: String): Unit = {
    args.head shouldBe "/selected/ffmpeg"
    _value(args, "-c:v") shouldBe "h264_videotoolbox"
    _value(args, "-b:v") shouldBe bitrate
    _value(args, "-allow_sw") shouldBe "0"
    _value(args, "-require_sw") shouldBe "0"
    Vector("-crf", "-preset", "-tune").foreach(option => args should not contain option)
  }
}

object CozyVideoHardwareEncodingSpec {
  /** Injected operation boundary: these outputs specify behavior, not native acceptance. */
  private final class HardwareRunner(failhardware: Boolean = false, propschange: Json => Json = identity) extends CozyVideo.VideoProcessRunner {
    val commands: ArrayBuffer[Vector[String]] = ArrayBuffer.empty
    val compositions: ArrayBuffer[Json] = ArrayBuffer.empty

    def run(args: Vector[String], cwd: Path): CozyVideo.VideoCommandResult = {
      commands += args
      args.find(_.endsWith("render.mjs")) match {
        case Some(script) =>
          val workspace = _path(cwd, script).getParent.getParent
          val props = propschange(_json(workspace.resolve("props.json")))
          _write(workspace.resolve("props.json"), props)
          compositions += props
          if (args.contains("--frame-audio")) {
            val count = props.hcursor.downField("timing").get[Int]("totalFrames").toOption.get
            val names = Vector.tabulate(count)(index => f"frames/frame-$index%05d.png")
            names.foreach(name => _png(workspace.resolve(name)))
            _pcm(workspace.resolve("composition.wav"), count.toDouble / props.hcursor.get[Int]("fps").toOption.get)
            _write(workspace.resolve("frame-audio.json"), Json.obj("schema" -> Json.fromString("cozy.video.frame-audio.v1"),
              "width" -> props.hcursor.downField("width").focus.get, "height" -> props.hcursor.downField("height").focus.get,
              "fps" -> props.hcursor.downField("fps").focus.get, "frameCount" -> Json.fromInt(count),
              "frames" -> Json.fromValues(names.map(Json.fromString)), "audioPath" -> Json.fromString("composition.wav")))
          } else _touch(cwd.resolve(props.hcursor.get[String]("outputPath").toOption.get))
          CozyVideo.VideoCommandResult(0, "complete composition fixture", "")
        case None if args.head == "/selected/python" =>
          _png(_path(cwd, args.last).getParent.resolve("frame.png"))
          CozyVideo.VideoCommandResult(0, "still frame fixture", "")
        case None if args.contains("/selected/ffprobe") =>
          CozyVideo.VideoCommandResult(0, _summary(compositions.last).noSpaces, "")
        case None =>
          _touch(_path(cwd, args.last))
          if (failhardware && args.contains("h264_videotoolbox")) CozyVideo.VideoCommandResult(19, "", "hardware initialization refused")
          else CozyVideo.VideoCommandResult(0, "ordinary encoder or stream copy fixture", "")
      }
    }
  }

  private def _value(args: Vector[String], option: String): String = args(args.indexOf(option) + 1)
  private def _render(root: Path, runner: CozyVideo.VideoProcessRunner): String =
    CozyVideo.render(CozyVideo.RenderConfig(root.resolve("video.yaml"), "remotion"), CozyVideo.VideoToolRegistry(Vector.empty), runner)
  private def _inspect(root: Path): String =
    CozyVideo.inspect(CozyVideo.InspectConfig(root.resolve("video.yaml"), checkTools = false), CozyVideo.VideoToolRegistry(Vector.empty))

  private def _fixture(root: Path, policy: String = "standard", fps: Int = 12, finalpage: String = "end-card"): Unit = {
    Files.writeString(root.resolve("video.yaml"), s"""name: selected-hardware
      |output: build/final.mp4
      |renderer:
      |  engine: remotion
      |  policy: $policy
      |  fps: $fps
      |  width: 4
      |  height: 4
      |  crf: 21
      |  x264Preset: fast
      |visualEffects:
      |  opening: none
      |  sectionStart: none
      |  summary: none
      |  finalPage: $finalpage
      |parts:
      |  - id: narrated
      |    type: dialogue
      |    script: script.json
      |    audioDir: build/audio/narrated
      |    output: build/parts/narrated.mp4
      |""".stripMargin)
    _write(root.resolve("script.json"), Json.obj("scenes" -> Json.arr(
      Json.obj("id" -> Json.fromString("first"), "narration" -> Json.fromString("First narration"), "duration" -> Json.fromInt(2), "tailSilence" -> Json.fromDoubleOrNull(0.75)),
      Json.obj("id" -> Json.fromString("last"), "narration" -> Json.fromString("Last narration"), "duration" -> Json.fromInt(1), "tailSilence" -> Json.fromDoubleOrNull(0.5)))))
    val audio = root.resolve("build/audio/narrated")
    _pcm(audio.resolve("first.wav"), 1.5)
    _pcm(audio.resolve("last.wav"), 0.5)
    _pcm(audio.resolve("script.wav"), 3.5)
    _write(audio.resolve("manifest.json"), Json.arr(
      Json.obj("sceneId" -> Json.fromString("first"), "file" -> Json.fromString("first.wav"), "leadSilence" -> Json.fromDoubleOrNull(0.25), "audioDuration" -> Json.fromDoubleOrNull(1.5), "targetDuration" -> Json.fromInt(2), "tailSilence" -> Json.fromDoubleOrNull(0.75)),
      Json.obj("sceneId" -> Json.fromString("last"), "file" -> Json.fromString("last.wav"), "leadSilence" -> Json.fromInt(0), "audioDuration" -> Json.fromDoubleOrNull(0.5), "targetDuration" -> Json.fromInt(1), "tailSilence" -> Json.fromDoubleOrNull(0.5))))
  }

  private def _encoder(encoder: Json): Json = Json.obj("video" -> Json.obj("encoder" -> encoder))
  private def _operating(encoder: Json): Json = Json.obj("video" -> Json.obj("tools" -> Json.obj(
    "remotion" -> Json.obj("executable" -> Json.fromString("/selected/node"), "docker-image" -> Json.fromString("renderer-image")),
    "ffmpeg" -> Json.obj("mode" -> Json.fromString("host"), "executable" -> Json.fromString("/selected/ffmpeg")),
    "ffprobe" -> Json.obj("executable" -> Json.fromString("/selected/ffprobe"), "docker-image" -> Json.fromString("probe-image")),
    "python-pillow" -> Json.obj("mode" -> Json.fromString("host"), "executable" -> Json.fromString("/selected/python"))),
    "encoder" -> encoder))
  private def _hardware(root: Path, bitrate: Option[Json] = None): Unit =
    _write(root.resolve(".cozy/config.json"), _operating(Json.obj((Vector("name" -> Json.fromString("h264_videotoolbox")) ++ bitrate.toVector.map("bitrate" -> _)): _*)))

  private def _summary(props: Json): Json = {
    val count = props.hcursor.downField("timing").get[Int]("totalFrames").toOption.get
    val fps = props.hcursor.get[Int]("fps").toOption.get
    Json.obj("format" -> Json.obj("duration" -> Json.fromString((count.toDouble / fps).toString)), "streams" -> Json.arr(
      Json.obj("codec_type" -> Json.fromString("video"), "start_time" -> Json.fromString("0"),
        "duration_ts" -> Json.fromLong(count * 1000L), "time_base" -> Json.fromString(s"1/${fps * 1000}"),
        "nb_frames" -> Json.fromString(count.toString), "duration" -> Json.fromString((count.toDouble / fps).toString)),
      Json.obj("codec_type" -> Json.fromString("audio"), "start_time" -> Json.fromString("0"),
        "duration_ts" -> Json.fromLong(count * 48000L / fps), "time_base" -> Json.fromString("1/48000"),
        "duration" -> Json.fromString((count * 48000L / fps / 48000.0).toString))))
  }

  private def _pcm(path: Path, seconds: Double): Unit = {
    val samples = math.max(1, math.floor(seconds * 48000).toInt)
    val data = Array.tabulate[Byte](samples * 4)(index => if (index % 4 == 1) 16.toByte else 0.toByte)
    CozyVideoImplementation._write_wav(path, CozyVideoImplementation.WaveData(1, 48000, 2, 16, data))
  }
  private def _png(path: Path): Unit = {
    Files.createDirectories(path.getParent)
    ImageIO.write(new BufferedImage(4, 4, BufferedImage.TYPE_INT_RGB), "png", path.toFile)
  }
  private def _json(path: Path): Json = parser.parse(Files.readString(path)).toOption.get
  private def _write(path: Path, json: Json): Unit = {
    Files.createDirectories(path.getParent)
    Files.writeString(path, json.spaces2)
  }
  private def _touch(path: Path): Unit = {
    Files.createDirectories(path.getParent)
    Files.writeString(path, "ordinary media fixture output")
  }
  private def _path(root: Path, value: String): Path =
    if (value.startsWith("/workspace/")) root.resolve(value.stripPrefix("/workspace/"))
    else { val path = Paths.get(value); if (path.isAbsolute) path else root.resolve(path) }

  private def _with_project(body: (Path, Path) => Unit): Unit = CozyVideoExecutionSpec.synchronized {
    val workdir = Files.createDirectories(Paths.get("target", "cozy-video", "work", "hardware-spec"))
    val directory = Files.createTempDirectory(workdir, "cozy-video-hardware-spec-").toRealPath()
    val root = Files.createDirectories(directory.resolve("project"))
    val home = Files.createDirectories(directory.resolve("home"))
    val previous = System.getProperty("user.home")
    System.setProperty("user.home", home.toString)
    try body(root, home)
    finally {
      if (previous == null) System.clearProperty("user.home") else System.setProperty("user.home", previous)
      CozyVideoImplementation._delete_tree(directory)
    }
  }
}
