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

/*
 * @since   Oct. 10, 2026
 * @version Oct. 12, 2026
 * @author  ASAMI, Tomoharu
 */
object CozyVideoMixedRenderSpec {
  private final class CompositionRunner(
    handoffchange: Json => Json = identity,
    omithandoff: Boolean = false,
    failencoder: Boolean = false,
    failrenderer: Boolean = false
  ) extends CozyVideo.VideoProcessRunner {
    val commands: ArrayBuffer[Vector[String]] = ArrayBuffer.empty
    val compositions: ArrayBuffer[Json] = ArrayBuffer.empty

    def run(args: Vector[String], cwd: Path): CozyVideo.VideoCommandResult = {
      commands += args
      args.find(_.endsWith("render.mjs")) match {
        case Some(script) =>
          val workspace = _path(cwd, script).getParent.getParent
          val props = _json(workspace.resolve("props.json"))
          compositions += props
          if (failrenderer) CozyVideo.VideoCommandResult(7, "", "selected renderer failed")
          else {
            if (args.contains("--frame-audio")) {
              val count = props.hcursor.downField("timing").get[Int]("totalFrames").toOption.get
              val names = Vector.tabulate(count)(index => f"frames/frame-$index%05d.png")
              val image = new BufferedImage(props.hcursor.get[Int]("width").toOption.get, props.hcursor.get[Int]("height").toOption.get, BufferedImage.TYPE_INT_RGB)
              names.foreach { name =>
                val file = workspace.resolve(name)
                Files.createDirectories(file.getParent)
                ImageIO.write(image, "png", file.toFile)
              }
              _pcm(workspace.resolve("composition.wav"), count.toDouble / props.hcursor.get[Int]("fps").toOption.get)
              val handoff = Json.obj("schema" -> Json.fromString("cozy.video.frame-audio.v1"),
                "width" -> props.hcursor.downField("width").focus.get, "height" -> props.hcursor.downField("height").focus.get,
                "fps" -> props.hcursor.downField("fps").focus.get, "frameCount" -> Json.fromInt(count),
                "frames" -> Json.fromValues(names.map(Json.fromString)), "audioPath" -> Json.fromString("composition.wav"))
              if (!omithandoff) _write(workspace.resolve("frame-audio.json"), handoffchange(handoff))
            } else _touch(cwd.resolve(props.hcursor.get[String]("outputPath").toOption.get))
            CozyVideo.VideoCommandResult(0, "complete selected composition", "")
          }
        case None if args.contains("/selected/ffprobe") || args.contains("ffprobe") =>
          val props = compositions.last
          CozyVideo.VideoCommandResult(0, _summary(props).noSpaces, "")
        case None =>
          val output = _path(cwd, args.last)
          _touch(output)
          if (failencoder && args.contains("libx264")) CozyVideo.VideoCommandResult(9, "", "selected software encoder failed after partial output")
          else CozyVideo.VideoCommandResult(0, "ordinary encoder/assembly output", "")
      }
    }
  }

  private def _summary(props: Json): Json = {
    val count = props.hcursor.downField("timing").get[Int]("totalFrames").toOption.get
    val fps = props.hcursor.get[Int]("fps").toOption.get
    val duration = count.toDouble / fps
    Json.obj("format" -> Json.obj("duration" -> Json.fromString(duration.toString)), "streams" -> Json.arr(
      Json.obj("codec_type" -> Json.fromString("video"), "start_time" -> Json.fromString("0"),
        "duration_ts" -> Json.fromLong(count * 1000L), "time_base" -> Json.fromString(s"1/${fps * 1000}"),
        "nb_frames" -> Json.fromString(count.toString), "duration" -> Json.fromString(duration.toString)),
      Json.obj("codec_type" -> Json.fromString("audio"), "start_time" -> Json.fromString("0"),
        "duration_ts" -> Json.fromLong(count * 48000L / fps), "time_base" -> Json.fromString("1/48000"),
        "duration" -> Json.fromString((count * 48000L / fps / 48000.0).toString))))
  }

  private def _pcm(path: Path, seconds: Double): Unit = {
    val samples = math.max(1, math.floor(seconds * 48000).toInt)
    val data = Array.tabulate[Byte](samples * 4)(index => if (index % 4 == 1) 16.toByte else 0.toByte)
    CozyVideoImplementation._write_wav(path, CozyVideoImplementation.WaveData(1, 48000, 2, 16, data))
  }

  private def _json(path: Path): Json = parser.parse(Files.readString(path)).toOption.get
  private def _write(path: Path, json: Json): Unit = {
    Files.createDirectories(path.getParent)
    Files.writeString(path, json.spaces2)
  }
  private def _touch(path: Path): Unit = {
    Files.createDirectories(path.getParent)
    Files.writeString(path, "ordinary media output")
  }
  private def _path(root: Path, value: String): Path =
    if (value.startsWith("/workspace/")) root.resolve(value.stripPrefix("/workspace/"))
    else { val path = Paths.get(value); if (path.isAbsolute) path else root.resolve(path) }
}

final class CozyVideoMixedRenderSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  import CozyVideoMixedRenderSpec._

  "Connected software rendering and final assembly" should {
    "consume one complete composition" which {
      "render Docker PNG/PCM and encode through the selected host executable before ordinary build" in {
        Given("a narrated two-scene project with final-card hold, Docker Remotion, host FFmpeg and independent Docker ffprobe")
        _with_project { root =>
          _fixture(root, mixed = true)
          val runner = new CompositionRunner()
          When("the public render and build requests interpret their normal producer boundaries")
          val rendered = _render(root, runner)
          val built = _build(root, runner)
          Then("the selected software encoder consumes the full frame/audio handoff and build uses the independent probe")
          rendered should include("remotion")
          built should include("parts: 1")
          val renderer = runner.commands.find(_.exists(_.endsWith("render.mjs"))).get
          renderer.head shouldBe "docker"
          renderer should contain("renderer-image")
          renderer should contain("/selected/node")
          renderer should contain("--frame-audio")
          val encoder = runner.commands.find(_.contains("libx264")).get
          encoder.head shouldBe "/selected/ffmpeg"
          encoder should contain("yuv420p")
          encoder should contain("21")
          encoder should contain("fast")
          encoder should contain("128k")
          encoder should contain("18000")
          encoder should not contain "-shortest"
          val props = runner.compositions.head
          val count = props.hcursor.downField("timing").get[Int]("totalFrames").toOption.get
          encoder(encoder.indexOf("-frames:v") + 1) shouldBe count.toString
          encoder(encoder.indexOf("-af") + 1) should include(s"atrim=end_sample=${count * 48000L / 12}")
          val probe = runner.commands.find(_.contains("/selected/ffprobe")).get
          probe.head shouldBe "docker"
          probe should contain("probe-image")
          probe.last should startWith("/workspace/")
          val workspace = root.resolve("target/cozy-video/remotion/narrated")
          val handoff = _json(workspace.resolve("frame-audio.json"))
          handoff.hcursor.get[Vector[String]]("frames").toOption.get should have size count
          handoff.noSpaces should not include "/workspace/"
          handoff.noSpaces should not include "assetsInfo"
          val partmanifest = _json(root.resolve("build/parts/narrated.manifest.json"))
          partmanifest.hcursor.downField("invocation").downField("encoding").get[Int]("exitCode").toOption shouldBe Some(0)
          partmanifest.hcursor.downField("invocation").downField("encoding").get[Int]("frameCount").toOption shouldBe Some(count)
          val manifest = _json(root.resolve("build/manifest.json"))
          manifest.hcursor.downField("effectiveTools").downField("ffmpeg").get[String]("executable").toOption shouldBe Some("/selected/ffmpeg")
          manifest.hcursor.get[String]("encoder").toOption shouldBe Some("libx264")
          Files.isRegularFile(root.resolve("build/final.mp4")) shouldBe true
        }
      }

      "present the resolved operating settings and command modes on normal inspect and build dry-run" in {
        Given("the connected software project with per-tool settings and their operating source")
        _with_project { root =>
          _fixture(root, mixed = true)
          When("the ordinary inspection and build dry-run requests interpret the project")
          val inspection = CozyVideo.inspect(CozyVideo.InspectConfig(root.resolve("video.yaml"), checkTools = false), CozyVideo.VideoToolRegistry(Vector.empty))
          val dryrun = CozyVideo.build(CozyVideo.BuildConfig(root.resolve("video.yaml"), dryRun = true, checkTools = false), CozyVideo.VideoToolRegistry(Vector.empty))
          Then("both presentations expose effective tool selections and source while command preview reflects the host encoder")
          Vector(inspection, dryrun).foreach { output =>
            output should include("tool.ffmpeg: mode=host executable=/selected/ffmpeg")
            output should include("tool.ffprobe: mode=docker executable=/selected/ffprobe image=probe-image")
            output should include("video.tools.ffmpeg.mode")
            output should include("encoder: libx264")
            output should include("externalFrameEncoding: true")
          }
          dryrun should include("project.concat: ffmpeg (host)")
          dryrun should include("'/selected/ffmpeg'")
        }
      }

      "retain narration intervals, total frames and final-card timing across baseline and mixed modes" in {
        Given("the same full scene/audio inputs with lead, extended narration and trailing silence")
        _with_project { root =>
          _fixture(root, mixed = false)
          val baseline = new CompositionRunner()
          When("the ordinary Docker baseline renders the composition")
          _render(root, baseline)
          val baselineprops = baseline.compositions.head
          Given("the explicit mixed operating settings for the same content")
          _operating(root)
          val mixed = new CompositionRunner()
          When("the mixed route renders and encodes the complete composition")
          _render(root, mixed)
          Then("renderer props, narration coverage and timing remain unchanged")
          mixed.compositions.head shouldBe baselineprops
          baseline.commands.exists(_.contains("--frame-audio")) shouldBe false
          mixed.commands.exists(_.contains("--frame-audio")) shouldBe true
          val timing = baselineprops.hcursor.downField("timing")
          timing.get[Int]("contentFrames").toOption shouldBe Some(42)
          timing.get[Int]("finalPageStartFrame").toOption shouldBe Some(42)
          timing.get[Int]("finalPageHoldFrames").toOption shouldBe Some(24)
          timing.get[Int]("totalFrames").toOption shouldBe Some(66)
          val scenes = baselineprops.hcursor.get[Vector[Json]]("scenes").toOption.get
          scenes.head.hcursor.get[Int]("durationFrames").toOption shouldBe Some(30)
          scenes.head.hcursor.get[Int]("leadInFrames").toOption shouldBe Some(3)
          scenes.head.hcursor.get[Int]("audioDurationFrames").toOption shouldBe Some(18)
          scenes.head.hcursor.get[Int]("effectiveTailSilenceFrames").toOption shouldBe Some(9)
          scenes.last.hcursor.get[Int]("startFrame").toOption shouldBe Some(30)
          val encoder = mixed.commands.find(_.contains("libx264")).get
          encoder(encoder.indexOf("-video_track_timescale") + 1) shouldBe "12000"
          encoder(encoder.indexOf("-movie_timescale") + 1) shouldBe "48000"
          encoder(encoder.indexOf("-af") + 1) should include("atrim=end_sample=264000")
        }
      }

      "preserve whole-operation host renderMedia baseline with no new settings" in {
        Given("the same project with explicit legacy host mode and no tool-specific settings")
        _with_project { root =>
          _fixture(root, mixed = false)
          val runner = new CompositionRunner()
          When("the public render interprets the compatible whole-host request")
          CozyVideo.render(CozyVideo.RenderConfig(root.resolve("video.yaml"), "remotion", toolMode = Some("host")), CozyVideo.VideoToolRegistry(Vector.empty), runner)
          Then("the ordinary host node invocation produces media without a frame-encoding handoff")
          runner.commands.head.head shouldBe "node"
          runner.commands.head should not contain "--frame-audio"
          Files.isRegularFile(root.resolve("build/parts/narrated.mp4")) shouldBe true
        }
      }

      "dispatch the simple renderer through selected Python and the same software encoding controls" in {
        Given("a simple renderer with host Python/FFmpeg selections and the existing combined audio input")
        _with_project { root =>
          _fixture(root, mixed = false)
          Files.writeString(root.resolve("video.yaml"), Files.readString(root.resolve("video.yaml")).replace("finalPage: end-card", "finalPage: none"))
          _pcm(root.resolve("build/audio/narrated/script.wav"), 3.5)
          _write(root.resolve(".cozy/config.json"), Json.obj("video" -> Json.obj("tools" -> Json.obj(
            "python-pillow" -> Json.obj("mode" -> Json.fromString("host"), "executable" -> Json.fromString("/selected/python")),
            "ffmpeg" -> Json.obj("mode" -> Json.fromString("host"), "executable" -> Json.fromString("/selected/ffmpeg"))))))
          val commands = ArrayBuffer.empty[Vector[String]]
          val runner = new CozyVideo.VideoProcessRunner {
            def run(args: Vector[String], cwd: Path): CozyVideo.VideoCommandResult = {
              commands += args
              if (args.head == "/selected/python") {
                val output = Path.of(args.last).getParent.resolve("frame.png")
                ImageIO.write(new BufferedImage(4, 4, BufferedImage.TYPE_INT_RGB), "png", output.toFile)
              } else _touch(Path.of(args.last))
              CozyVideo.VideoCommandResult(0, "selected simple output", "")
            }
          }
          When("the public simple-java2d render request interprets its normal producer pipeline")
          CozyVideo.render(CozyVideo.RenderConfig(root.resolve("video.yaml"), "simple-java2d"), CozyVideo.VideoToolRegistry(Vector.empty), runner)
          Then("Python and FFmpeg commands each retain their selections and profile quality controls")
          commands.head.head shouldBe "/selected/python"
          commands.last.head shouldBe "/selected/ffmpeg"
          commands.last should contain("libx264")
          commands.last(commands.last.indexOf("-crf") + 1) shouldBe "21"
          commands.last(commands.last.indexOf("-preset") + 1) shouldBe "fast"
        }
      }
    }

    "stop without hidden encoding fallback" which {
      "fail a successful renderer that omitted its required handoff" in {
        Given("selected mixed rendering whose process succeeds without frame/audio handoff")
        _with_project { root =>
          _fixture(root, mixed = true)
          val runner = new CompositionRunner(omithandoff = true)
          When("the public render interprets the missing producer output")
          val failure = intercept[Throwable](_render(root, runner))
          Then("handoff failure prevents FFmpeg encoding and no baseline renderMedia retry occurs")
          failure.getMessage should include("Missing Cozy frame/audio handoff")
          runner.commands should have size 1
          Files.exists(root.resolve("build/parts/narrated.mp4")) shouldBe false
        }
      }

      "reject an omitted handoff on a repeated render with identical composition timing" in {
        Given("a mixed project whose first successful render leaves a valid handoff in its reusable workspace")
        _with_project { root =>
          _fixture(root, mixed = true)
          val first = new CompositionRunner()
          When("the first public render produces and encodes its full composition")
          val rendered = _render(root, first)
          Then("the initial renderer and encoder succeed with a persisted handoff")
          rendered should include("remotion")
          first.commands should have size 2
          first.commands.count(_.contains("libx264")) shouldBe 1
          val workspace = root.resolve("target/cozy-video/remotion/narrated")
          Files.isRegularFile(workspace.resolve("frame-audio.json")) shouldBe true
          Given("a second successful renderer that supplies no handoff for the same unchanged project")
          val second = new CompositionRunner(omithandoff = true)
          When("the second public render reuses the composition workspace")
          val failure = intercept[Throwable](_render(root, second))
          Then("the previous handoff cannot authorize encoding and no alternate rendering is attempted")
          failure.getMessage should include("Missing Cozy frame/audio handoff")
          second.compositions.toVector shouldBe first.compositions.toVector
          second.commands should have size 1
          second.commands.head should contain("--frame-audio")
          second.commands.count(_.contains("libx264")) shouldBe 0
          Files.exists(workspace.resolve("frame-audio.json")) shouldBe false
          Files.isRegularFile(workspace.resolve("frames/frame-00000.png")) shouldBe true
          Files.isRegularFile(workspace.resolve("composition.wav")) shouldBe true
        }
      }

      "reject wrong dimensions/count, duplicate or unordered frames and unsafe/missing paths" in {
        Given("each invalid handoff is returned by the actual public mixed renderer boundary")
        val changes: Vector[Json => Json] = Vector(
          json => json.mapObject(_.add("width", Json.fromInt(0))),
          json => json.mapObject(_.add("fps", Json.fromInt(24))),
          json => json.mapObject(_.add("frameCount", Json.fromInt(1))),
          json => json.mapObject(_.add("frames", Json.fromValues(json.hcursor.get[Vector[String]]("frames").toOption.get.reverse.map(Json.fromString)))),
          json => json.mapObject(_.add("frames", Json.fromValues(Vector.fill(66)(Json.fromString("frames/frame-00000.png"))))),
          json => json.mapObject(_.add("audioPath", Json.fromString("../outside.wav"))),
          json => json.mapObject(_.add("audioPath", Json.fromString("/workspace/composition.wav"))),
          json => json.mapObject(_.add("audioPath", Json.fromString("missing.wav")))
        )
        changes.foreach { change =>
          _with_project { root =>
            _fixture(root, mixed = true)
            val runner = new CompositionRunner(handoffchange = change)
            When("the public render consumes the malformed handoff")
            val failure = intercept[Throwable](_render(root, runner))
            Then("the structured input failure occurs before any external encoder call")
            failure.getMessage should not be empty
            runner.commands should have size 1
          }
        }
      }

      "report selected renderer and encoder exits with ordinary partial-output behavior" in {
        Given("a selected renderer failure followed by a separate selected encoder failure case")
        _with_project { root =>
          _fixture(root, mixed = true)
          val renderer = new CompositionRunner(failrenderer = true)
          When("the selected renderer fails")
          val renderfailure = intercept[Throwable](_render(root, renderer))
          Then("there is no encoder attempt or alternate renderer")
          renderfailure.getMessage should include("selected renderer failed")
          renderer.commands should have size 1
          Given("a successful renderer followed by a partial external encoder failure")
          val encoder = new CompositionRunner(failencoder = true)
          When("the selected software encoder fails after writing its ordinary output")
          val encodefailure = intercept[Throwable](_render(root, encoder))
          Then("the exact encoder exit remains inspectable and no fallback or protected-output transaction runs")
          encodefailure.getMessage should include("selected software encoder failed")
          encoder.commands should have size 2
          Files.isRegularFile(root.resolve("build/parts/narrated.mp4")) shouldBe true
          _json(root.resolve("target/cozy-video/remotion/narrated/encoding-result.json")).hcursor.get[Int]("exitCode").toOption shouldBe Some(9)
        }
      }
    }
  }

  "Frame/audio timing admission" should {
    "preserve exact planned frame counts and floor audio endings for generated timelines" in {
      Given("positive generated composition frame counts and frame rates")
      _with_project { root =>
        val cases = for { count <- Gen.choose(1, 40); fps <- Gen.oneOf(12, 18, 24, 29, 30) } yield (count, fps)
        val property = Prop.forAll(cases) { case (count, fps) =>
          val workspace = Files.createTempDirectory(root, "frames-")
          val names = Vector.tabulate(count)(index => s"frame-$index.png")
          val image = new BufferedImage(4, 4, BufferedImage.TYPE_INT_RGB)
          names.foreach(name => ImageIO.write(image, "png", workspace.resolve(name).toFile))
          _pcm(workspace.resolve("composition.wav"), count.toDouble / fps)
          val props = Json.obj("width" -> Json.fromInt(4), "height" -> Json.fromInt(4), "fps" -> Json.fromInt(fps),
            "crf" -> Json.fromInt(23), "x264Preset" -> Json.Null, "timing" -> Json.obj("totalFrames" -> Json.fromInt(count)))
          _write(workspace.resolve("frame-audio.json"), Json.obj("schema" -> Json.fromString("cozy.video.frame-audio.v1"),
            "width" -> Json.fromInt(4), "height" -> Json.fromInt(4), "fps" -> Json.fromInt(fps), "frameCount" -> Json.fromInt(count),
            "frames" -> Json.fromValues(names.map(Json.fromString)), "audioPath" -> Json.fromString("composition.wav")))
          val handoff = CozyVideoFrameEncoding.load(workspace, props)
          val execution = CozyVideoTools.VideoExecutionConfig(CozyVideoTools.VideoToolMode.Host, "image", "url")
          val args = CozyVideoFrameEncoding.arguments(handoff, workspace, root, execution, props, root.resolve("output.mp4"))
          val samples = BigInt(count) * 48000 / fps
          handoff.frameCount == count && handoff.frames.size == count &&
            args(args.indexOf("-frames:v") + 1) == count.toString &&
            args(args.indexOf("-af") + 1).contains(s"atrim=end_sample=$samples,") &&
            BigInt(count) * 48000 - samples * fps >= 0 && BigInt(count) * 48000 - samples * fps < fps && !args.contains("-shortest")
        }
        When("ScalaCheck interprets twenty complete renderer/encoder handoffs")
        val result = Test.check(Test.Parameters.default.withMinSuccessfulTests(20).withInitialSeed(71603L), property)
        Then("N visual frames survive and audio ends at their exact floor sample boundary")
        result.passed shouldBe true
        result.succeeded should be >= 20
      }
    }
  }

  private def _render(root: Path, runner: CozyVideo.VideoProcessRunner): String =
    CozyVideo.render(CozyVideo.RenderConfig(root.resolve("video.yaml"), "remotion"), CozyVideo.VideoToolRegistry(Vector.empty), runner)
  private def _build(root: Path, runner: CozyVideo.VideoProcessRunner): String =
    CozyVideo.build(CozyVideo.BuildConfig(root.resolve("video.yaml"), dryRun = false, checkTools = false), CozyVideo.VideoToolRegistry(Vector.empty), runner)

  private def _fixture(root: Path, mixed: Boolean): Unit = {
    Files.writeString(root.resolve("video.yaml"), """name: mixed-software
      |output: build/final.mp4
      |renderer:
      |  engine: remotion
      |  fps: 12
      |  width: 320
      |  height: 180
      |  crf: 21
      |  x264Preset: fast
      |visualEffects:
      |  opening: none
      |  sectionStart: none
      |  summary: none
      |  finalPage: end-card
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
    _write(audio.resolve("manifest.json"), Json.arr(
      Json.obj("sceneId" -> Json.fromString("first"), "file" -> Json.fromString("first.wav"), "leadSilence" -> Json.fromDoubleOrNull(0.25), "audioDuration" -> Json.fromDoubleOrNull(1.5), "targetDuration" -> Json.fromInt(2), "tailSilence" -> Json.fromDoubleOrNull(0.75)),
      Json.obj("sceneId" -> Json.fromString("last"), "file" -> Json.fromString("last.wav"), "leadSilence" -> Json.fromInt(0), "audioDuration" -> Json.fromDoubleOrNull(0.5), "targetDuration" -> Json.fromInt(1), "tailSilence" -> Json.fromDoubleOrNull(0.5))))
    if (mixed) _operating(root)
  }

  private def _operating(root: Path): Unit =
    _write(root.resolve(".cozy/config.json"), Json.obj("video" -> Json.obj("tools" -> Json.obj(
      "remotion" -> Json.obj("executable" -> Json.fromString("/selected/node"), "docker-image" -> Json.fromString("renderer-image")),
      "ffmpeg" -> Json.obj("mode" -> Json.fromString("host"), "executable" -> Json.fromString("/selected/ffmpeg")),
      "ffprobe" -> Json.obj("executable" -> Json.fromString("/selected/ffprobe"), "docker-image" -> Json.fromString("probe-image"))),
      "encoder" -> Json.obj("name" -> Json.fromString("libx264")))))

  private def _with_project(body: Path => Unit): Unit = CozyVideoExecutionSpec.synchronized {
    val directory = Files.createTempDirectory("cozy-video-mixed-spec-").toRealPath()
    val root = Files.createDirectories(directory.resolve("project"))
    val home = Files.createDirectories(directory.resolve("home"))
    val previous = System.getProperty("user.home")
    System.setProperty("user.home", home.toString)
    try body(root)
    finally {
      if (previous == null) System.clearProperty("user.home") else System.setProperty("user.home", previous)
      CozyVideoImplementation._delete_tree(directory)
    }
  }
}
