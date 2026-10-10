package cozy.video

import io.circe.{Json, parser}
import java.awt.image.BufferedImage
import java.nio.file.{Files, Path}
import javax.imageio.ImageIO
import org.scalacheck.{Gen, Prop, Test}
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import CozyVideoTools.{VideoExecutionConfig, VideoToolMode}
import CozyVideoModel.VideoToolSettings

/*
 * @since   Oct. 10, 2026
 * @version Oct. 11, 2026
 * @author  ASAMI, Tomoharu
 */
final class CozyVideoExecutionSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  import CozyVideoExecutionSpec._
  "Video operating configuration" should {
    "resolve explicit fields and compatibility" which {
      "retain portable Docker defaults and the unconfigured renderMedia baseline" in {
        Given("an isolated project and no authored operating settings")
        _with_project { (root, _) =>
          When("execution settings are resolved")
          val execution = _resolve(root)
          Then("portable tools use Docker, provider restrictions stay explicit, and normal rendering owns encoding")
          Vector("remotion", "ffmpeg", "ffprobe", "playwright", "python-pillow", "whisper-cpp", "piper")
            .foreach(tool => execution.selected(tool).mode shouldBe VideoToolMode.Docker)
          execution.selected("macos-say").mode shouldBe VideoToolMode.Host
          execution.selected("remotion").executable shouldBe "node"
          execution.externalFrameEncoding shouldBe false
          execution.encoderName shouldBe "libx264"
          VideoExecutionConfig(VideoToolMode.Host, "legacy-image", "http://localhost:50021").selected("ffmpeg").mode shouldBe VideoToolMode.Host
        }
      }

      "retain whole-operation host and legacy CLI mode/image precedence" in {
        Given("per-tool Docker mode/image and a descriptor Docker selection")
        _with_project { (root, _) =>
          _config(root.resolve(".cozy"), Json.obj("ffmpeg" -> Json.obj("mode" -> Json.fromString("docker"), "docker-image" -> Json.fromString("per-tool"))))
          When("legacy host CLI mode and an explicit image are resolved")
          val execution = _resolve(root, Some("host"), Some("cli-image"), Some(VideoToolSettings(Some("docker"), Some("descriptor"), None, None)))
          Then("CLI wins for applicable tools, executables stay independent, and the host baseline uses renderMedia")
          execution.selected("ffmpeg").mode shouldBe VideoToolMode.Host
          execution.selected("ffmpeg").dockerImage shouldBe "cli-image"
          execution.selected("remotion").mode shouldBe VideoToolMode.Host
          execution.selected("ffmpeg").origins("mode") shouldBe "cli:--tool-mode"
          // The explicit CLI image supersedes the per-tool image at this boundary.
          execution.externalFrameEncoding shouldBe false
        }
      }

      "apply specificity before descriptor globals and resolve each field across all operation layers" in {
        Given("home, conf/cozy and .cozy each supplying different FFmpeg fields")
        _with_project { (root, home) =>
          _config(home.resolve(".cozy"), Json.obj("ffmpeg" -> Json.obj("executable" -> Json.fromString("/host tools/ffmpeg"), "docker-image" -> Json.fromString("lower-image"))))
          _config(root.resolve("conf/cozy"), Json.obj("ffmpeg" -> Json.obj("mode" -> Json.fromString("host"))))
          _config(root.resolve(".cozy"), Json.obj("ffmpeg" -> Json.obj("executable" -> Json.fromString("/project tools/ffmpeg"))))
          When("descriptor Docker defaults and per-tool operating layers are resolved together")
          val execution = _resolve(root, descriptor = Some(VideoToolSettings(Some("docker"), Some("descriptor"), None, None)))
          Then("the highest individual fields win without discarding the unused lower image")
          val selected = execution.selected("ffmpeg")
          selected.mode shouldBe VideoToolMode.Host
          selected.executable shouldBe "/project tools/ffmpeg"
          selected.dockerImage shouldBe "lower-image"
          selected.origins("mode") should include("conf/cozy/config.json")
          selected.origins("executable") should include(".cozy/config.json")
          execution.selected("remotion").mode shouldBe VideoToolMode.Docker
          execution.externalFrameEncoding shouldBe true
        }
      }

      "follow existing loader file order within a directory" in {
        Given("two supported configuration files in the same .cozy directory")
        _with_project { (root, _) =>
          val directory = root.resolve(".cozy")
          Files.createDirectories(directory)
          Files.writeString(directory.resolve("config.yaml"), "video:\n  tools:\n    ffmpeg:\n      executable: earlier\n")
          _config(directory, Json.obj("ffmpeg" -> Json.obj("executable" -> Json.fromString("later"))))
          When("operating files are loaded using their canonical order")
          val execution = _resolve(root)
          Then("the later supported JSON file overrides that field")
          execution.selected("ffmpeg").executable shouldBe "later"
        }
      }

      "make explicit software encoder and FFmpeg image overrides reach external video encoding" in {
        Given("equal Docker modes with an explicit encoder or FFmpeg image selection")
        _with_project { (root, _) =>
          _write(root.resolve(".cozy/config.json"), Json.obj("video" -> Json.obj("encoder" -> Json.obj("name" -> Json.fromString("libx264")))))
          When("the explicit software encoder is resolved")
          val execution = _resolve(root)
          Then("encoding leaves Remotion even though the runtime modes agree")
          execution.externalFrameEncoding shouldBe true
          execution.encoderOrigin should include("video.encoder.name")
          Given("a FFmpeg image override with the default renderer image")
          _config(root.resolve(".cozy"), Json.obj("ffmpeg" -> Json.obj("docker-image" -> Json.fromString("selected-ffmpeg-image"))))
          When("the image selection is resolved")
          val imageexecution = _resolve(root)
          Then("the selected image also forces the external encoder route")
          imageexecution.externalFrameEncoding shouldBe true
          imageexecution.selected("ffmpeg").dockerImage shouldBe "selected-ffmpeg-image"
        }
      }
    }

    "fail authored unsupported input without fallback" which {
      "reject unknown tools, fields, modes, malformed maps, blanks, controls and same-layer contradictions" in {
        Given("invalid explicit operating values, including a lower invalid layer beneath a valid override")
        val invalid = Vector(
          Json.obj("unknown" -> Json.obj()),
          Json.obj("ffmpeg" -> Json.obj("flags" -> Json.fromString("-version"))),
          Json.obj("ffmpeg" -> Json.obj("mode" -> Json.fromString("automatic"))),
          Json.arr(Json.fromString("ffmpeg")),
          Json.obj("ffmpeg" -> Json.fromString("host")),
          Json.obj("ffmpeg" -> Json.obj("executable" -> Json.fromString("  "))),
          Json.obj("ffmpeg" -> Json.obj("executable" -> Json.fromString("ffmpeg\u0000extra"))),
          Json.obj("ffmpeg" -> Json.obj("executable" -> Json.fromString("ffmpeg\nextra"))),
          Json.obj("ffmpeg" -> Json.obj("mode" -> Json.fromString("host"), "docker-image" -> Json.fromString("image"))),
          Json.obj("macos-say" -> Json.obj("mode" -> Json.fromString("docker"))),
          Json.obj("piper" -> Json.obj("mode" -> Json.fromString("host")))
        )
        invalid.foreach { tools =>
          _with_project { (root, home) =>
            _config(home.resolve(".cozy"), tools)
            _config(root.resolve(".cozy"), Json.obj("ffmpeg" -> Json.obj("mode" -> Json.fromString("docker"))))
            When("the complete authored layer set is interpreted")
            val failure = intercept[Throwable](_resolve(root))
            Then("configuration fails before any process or alternate provider can be chosen")
            failure.getMessage should not be empty
          }
        }
      }

      "leave hardware unsupported until the software native gate" in {
        Given("an explicitly selected VideoToolbox encoder")
        _with_project { (root, _) =>
          _write(root.resolve(".cozy/config.json"), Json.obj("video" -> Json.obj("encoder" -> Json.obj("name" -> Json.fromString("h264_videotoolbox")))))
          When("S01 settings are resolved")
          val failure = intercept[Throwable](_resolve(root))
          Then("the hardware request fails explicitly instead of selecting software")
          failure.getMessage should include("Unsupported video encoder")
        }
      }
    }
  }

  "Selected command/path dispatch" should {
    "keep independent selections at actual producer boundaries" which {
      "preserve executable as one argv element and ffprobe as a separate selection" in {
        Given("Docker Remotion, host FFmpeg and separate Docker ffprobe executable/image")
        _with_project { (root, _) =>
          _config(root.resolve(".cozy"), Json.obj(
            "ffmpeg" -> Json.obj("mode" -> Json.fromString("host"), "executable" -> Json.fromString("/tools/ffmpeg $(literal)")),
            "ffprobe" -> Json.obj("executable" -> Json.fromString("/probe tools/ffprobe"), "docker-image" -> Json.fromString("probe-image"))))
          val execution = _resolve(root)
          val file = root.resolve("media spaces/movie.mp4")
          When("each actual command receives its own path mapping")
          val ffmpeg = CozyVideoImplementation._execution_command(root, execution, "ffmpeg", Vector(CozyVideoImplementation._execution_path(root, execution, file, "ffmpeg")))
          val ffprobe = CozyVideoImplementation._execution_command(root, execution, "ffprobe", Vector(CozyVideoImplementation._execution_path(root, execution, file, "ffprobe")))
          Then("FFmpeg keeps a literal host executable and path while ffprobe uses its own selected container")
          ffmpeg shouldBe Vector("/tools/ffmpeg $(literal)", file.toString)
          ffprobe should contain("probe-image")
          ffprobe should contain("/probe tools/ffprobe")
          ffprobe.last shouldBe "/workspace/media spaces/movie.mp4"
        }
      }

      "map arbitrary contained paths consistently for every selected logical tool" in {
        Given("host and Docker tool selections and generated paths containing spaces")
        _with_project { (root, _) =>
          val cases = for { mode <- Gen.oneOf("host", "docker"); tool <- Gen.oneOf("remotion", "ffmpeg", "ffprobe", "playwright", "python-pillow"); number <- Gen.choose(1, 1000) } yield (mode, tool, number)
          val property = Prop.forAll(cases) { case (mode, tool, number) =>
            val execution = VideoExecutionConfig(VideoToolMode.parse(mode), "image", "url")
            val path = root.resolve(s"media spaces/frame-$number.png")
            val mapped = CozyVideoImplementation._execution_path(root, execution, path, tool)
            val args = CozyVideoImplementation._execution_command(root, execution, tool, Vector(mapped))
            args.last == (if (mode == "docker") s"/workspace/media spaces/frame-$number.png" else path.toString) &&
              (if (mode == "docker") args.head == "docker" else args.head == execution.selected(tool).executable)
          }
          When("ScalaCheck interprets fifty independent selected-tool mappings")
          val result = Test.check(Test.Parameters.default.withMinSuccessfulTests(50).withInitialSeed(71601L), property)
          Then("command dispatch and path projection always agree on execution mode")
          result.passed shouldBe true
          result.succeeded should be >= 50
        }
      }

      "mount external selected inputs read-only and output parents for writing" in {
        Given("a selected Docker tool and files outside the project")
        _with_project { (root, home) =>
          val input = home.resolve("inputs/source.wav")
          val output = home.resolve("outputs/movie.mp4")
          When("the ordinary mount closure and command are prepared")
          val mounts = CozyVideoImplementation._selected_path_mounts(root, Vector(input), Vector(output))
          val args = CozyVideoImplementation._execution_command(root, _resolve(root), "ffmpeg", Vector(input.toString, output.toString), mounts = mounts)
          Then("external input and output-parent mounts retain their access and literal paths")
          args should contain(s"$input:$input:ro")
          args should contain(s"${output.getParent}:${output.getParent}:rw")
          args.last shouldBe output.toString
        }
      }

      "normalize selected host say narration through independently selected Docker FFmpeg" in {
        Given("host say and a separately selected Docker FFmpeg executable/image")
        _with_project { (root, _) =>
          _config(root.resolve(".cozy"), Json.obj(
            "macos-say" -> Json.obj("executable" -> Json.fromString("/selected/say")),
            "ffmpeg" -> Json.obj("executable" -> Json.fromString("/selected/audio-ffmpeg"), "docker-image" -> Json.fromString("audio-image"))))
          val execution = _resolve(root)
          val commands = scala.collection.mutable.ArrayBuffer.empty[Vector[String]]
          val runner = new CozyVideo.VideoProcessRunner {
            def run(args: Vector[String], cwd: Path): CozyVideo.VideoCommandResult = {
              commands += args
              if (args.head == "/selected/say") Files.writeString(Path.of(args(args.indexOf("-o") + 1)), "generated AIFF")
              else CozyVideoImplementation._write_silence_wav(Path.of(args.last), 0.5)
              CozyVideo.VideoCommandResult(0, "selected narration output", "")
            }
          }
          val provider = new CozyVideoImplementation.MacosSayNarrationProvider(root, runner, execution)
          When("the selected narration provider interprets text and normalizes its audio")
          val audio = provider.synthesize("Selected speech", Json.obj("voiceName" -> Json.fromString("Samantha")))
          Then("say stays on the host and only normalization uses the selected Docker tool and external temp mounts")
          commands.head.head shouldBe "/selected/say"
          commands.last.head shouldBe "docker"
          commands.last should contain("audio-image")
          commands.last should contain("/selected/audio-ffmpeg")
          commands.last.exists(_.endsWith(":rw")) shouldBe true
          audio.wav should not be empty
          audio.voiceIdentity shouldBe Some("Samantha")
        }
      }

      "probe only the selected FFmpeg executable and reject its missing dependency without fallback" in {
        Given("a missing explicitly selected host FFmpeg and an injected prerequisite probe")
        _with_project { (root, _) =>
          _config(root.resolve(".cozy"), Json.obj("ffmpeg" -> Json.obj("mode" -> Json.fromString("host"), "executable" -> Json.fromString("/missing/ffmpeg"))))
          val execution = _resolve(root)
          val commands = scala.collection.mutable.ArrayBuffer.empty[Vector[String]]
          val probe = new CozyVideoTools.VideoToolProbe {
            def command(args: Vector[String], cwd: Path): CozyVideoTools.VideoCommandResult = {
              commands += args
              CozyVideoTools.VideoCommandResult(127, "", "selected executable missing")
            }
            def httpGet(uri: java.net.URI): CozyVideoTools.VideoHttpResult = CozyVideoTools.VideoHttpResult(0, "", Some("not requested"))
            def exists(path: Path): Boolean = false
          }
          val project = CozyVideoModel.VideoProject(None, None, None, None, None, Vector.empty)
          val context = CozyVideoTools.VideoToolContext(root.resolve("video.yaml"), root, project, execution)
          When("prerequisite checking interprets the selected executable and rendering requires it")
          val check = CozyVideoTools.FfmpegProvider(probe).check(context)
          val failure = intercept[Throwable](CozyVideoImplementation._validate_render_tools("remotion", execution, Vector(check)))
          Then("the host selection remains missing and no installed default executable or Docker provider is attempted")
          commands.toVector shouldBe Vector(Vector("/missing/ffmpeg", "-version"))
          check.mode shouldBe VideoToolMode.Host
          check.status shouldBe CozyVideoTools.VideoToolStatus.Missing
          failure.getMessage should include("Required execution dependency unavailable")
        }
      }
    }
  }

  "Public operation prerequisites" should {
    "gate only consumed selected tools through the production registry" which {
      "render with Docker Remotion and host FFmpeg despite missing unused ffprobe and Playwright images" in {
        Given("a mixed project, available selected renderer and encoder, and missing images selected only by unused tools")
        _with_project { (root, _) =>
          _operation_fixture(root)
          val registry = CozyVideoTools.VideoToolRegistry.production(new SelectedProbe(Set("probe-image", "unused-image")))
          val runner = new OperationRunner()
          val execution = _resolve(root)
          val project = CozyVideoModel.VideoProject(None, None, None, None, None, Vector.empty)
          val context = CozyVideoTools.VideoToolContext(root.resolve("video.yaml"), root, project, execution)
          When("the public render checks the production providers and renders the mixed composition")
          val rendered = CozyVideo.render(CozyVideo.RenderConfig(root.resolve("video.yaml"), "remotion"), registry, runner)
          val inventory = registry.checks(context).find(_.name == "docker-image").get
          Then("unused image inventory stays informational and names the actual failed selected image")
          rendered should include("remotion")
          runner.commands should have size 2
          runner.commands.head.head shouldBe "docker"
          runner.commands.head should contain("renderer-image")
          runner.commands.last.head shouldBe "/selected/ffmpeg"
          inventory.status shouldBe CozyVideoTools.VideoToolStatus.Missing
          val missingimage = Vector("probe-image", "unused-image").find(image => inventory.message.contains(s"Docker image is missing: $image.")).get
          inventory.setupHint shouldBe Some(s"Run: docker pull $missingimage")
          Files.isRegularFile(root.resolve("build/parts/part.mp4")) shouldBe true
        }
      }

      "stop public rendering before its producer when the selected Docker renderer image is missing" in {
        Given("a mixed project whose required selected renderer image is unavailable")
        _with_project { (root, _) =>
          _operation_fixture(root)
          val registry = CozyVideoTools.VideoToolRegistry.production(new SelectedProbe(Set("renderer-image")))
          val runner = new OperationRunner()
          When("the public render checks its consumed selected prerequisites")
          val failure = intercept[Throwable](CozyVideo.render(CozyVideo.RenderConfig(root.resolve("video.yaml"), "remotion"), registry, runner))
          Then("the required renderer image guard prevents every producer and fallback")
          failure.getMessage should include("Required execution dependency unavailable")
          failure.getMessage should include("remotion-node")
          failure.getMessage should include("renderer-image")
          runner.commands shouldBe empty
        }
      }

      "stop public rendering before its producer when its required Docker daemon is unavailable" in {
        Given("a mixed project with host FFmpeg but an unreachable daemon required by selected Remotion")
        _with_project { (root, _) =>
          _operation_fixture(root)
          val registry = CozyVideoTools.VideoToolRegistry.production(new SelectedProbe(Set.empty, daemonavailable = false))
          val runner = new OperationRunner()
          When("the public render checks the production daemon and tool guards")
          val failure = intercept[Throwable](CozyVideo.render(CozyVideo.RenderConfig(root.resolve("video.yaml"), "remotion"), registry, runner))
          Then("the required Docker daemon failure stops rendering without any alternate producer")
          failure.getMessage should include("Required execution dependency unavailable")
          failure.getMessage should include("docker-toolchain")
          runner.commands shouldBe empty
        }
      }

      "build through host FFmpeg and selected Docker ffprobe despite missing unused renderer and Playwright images" in {
        Given("a rendered part, available selected build tools, and missing images used only by rendering and replay")
        _with_project { (root, _) =>
          _operation_fixture(root)
          Files.createDirectories(root.resolve("build/parts"))
          Files.writeString(root.resolve("build/parts/part.mp4"), "existing rendered part")
          val registry = CozyVideoTools.VideoToolRegistry.production(new SelectedProbe(Set("renderer-image", "unused-image")))
          val runner = new OperationRunner()
          When("the public build checks the production registry and assembles its existing part")
          val built = CozyVideo.build(CozyVideo.BuildConfig(root.resolve("video.yaml"), dryRun = false, checkTools = true), registry, runner)
          Then("only the independently selected FFmpeg and ffprobe producers are required")
          built should include("parts: 1")
          runner.commands should have size 2
          runner.commands.head.head shouldBe "/selected/ffmpeg"
          runner.commands.last.head shouldBe "docker"
          runner.commands.last should contain("probe-image")
          runner.commands.last should contain("/selected/ffprobe")
          Files.isRegularFile(root.resolve("build/final.mp4")) shouldBe true
        }
      }

      "stop public build before assembly when its selected Docker ffprobe image is missing" in {
        Given("a rendered part and an unavailable image required by the selected build probe")
        _with_project { (root, _) =>
          _operation_fixture(root)
          Files.createDirectories(root.resolve("build/parts"))
          Files.writeString(root.resolve("build/parts/part.mp4"), "existing rendered part")
          val registry = CozyVideoTools.VideoToolRegistry.production(new SelectedProbe(Set("probe-image")))
          val runner = new OperationRunner()
          When("the public build checks its selected prerequisites")
          val failure = intercept[Throwable](CozyVideo.build(CozyVideo.BuildConfig(root.resolve("video.yaml"), dryRun = false, checkTools = true), registry, runner))
          Then("the required ffprobe image guard stops assembly without using another probe")
          failure.getMessage should include("Required execution dependency unavailable")
          failure.getMessage should include("ffprobe")
          failure.getMessage should include("probe-image")
          runner.commands shouldBe empty
        }
      }
    }
  }

  private def _operation_fixture(root: Path): Unit = {
    Files.writeString(root.resolve("video.yaml"), """name: selected-prerequisites
      |output: build/final.mp4
      |renderer:
      |  engine: remotion
      |  fps: 12
      |  width: 4
      |  height: 4
      |visualEffects:
      |  finalPage: none
      |parts:
      |  - id: part
      |    type: dialogue
      |    script: script.json
      |    audioDir: build/audio/part
      |    output: build/parts/part.mp4
      |""".stripMargin)
    _write(root.resolve("script.json"), Json.obj("scenes" -> Json.arr(Json.obj(
      "id" -> Json.fromString("scene"), "narration" -> Json.fromString("Selected narration"), "duration" -> Json.fromInt(1)))))
    CozyVideoImplementation._write_silence_wav(root.resolve("build/audio/part/scene.wav"), 1.0)
    _write(root.resolve("build/audio/part/manifest.json"), Json.arr(Json.obj(
      "sceneId" -> Json.fromString("scene"), "file" -> Json.fromString("scene.wav"),
      "leadSilence" -> Json.fromInt(0), "audioDuration" -> Json.fromInt(1),
      "targetDuration" -> Json.fromInt(1), "tailSilence" -> Json.fromInt(0))))
    _config(root.resolve(".cozy"), Json.obj(
      "remotion" -> Json.obj("docker-image" -> Json.fromString("renderer-image")),
      "ffmpeg" -> Json.obj("mode" -> Json.fromString("host"), "executable" -> Json.fromString("/selected/ffmpeg")),
      "ffprobe" -> Json.obj("executable" -> Json.fromString("/selected/ffprobe"), "docker-image" -> Json.fromString("probe-image")),
      "playwright" -> Json.obj("docker-image" -> Json.fromString("unused-image"))))
  }

  private def _resolve(root: Path, mode: Option[String] = None, image: Option[String] = None, descriptor: Option[VideoToolSettings] = None): VideoExecutionConfig =
    VideoExecutionConfig.create(root, descriptor, mode, image, None)

  private def _config(directory: Path, tools: Json): Unit =
    _write(directory.resolve("config.json"), Json.obj("video" -> Json.obj("tools" -> tools)))

  private def _write(path: Path, json: Json): Unit = {
    Files.createDirectories(path.getParent)
    Files.writeString(path, json.spaces2)
  }

  private def _with_project(body: (Path, Path) => Unit): Unit = CozyVideoExecutionSpec.synchronized {
    val directory = Files.createTempDirectory("cozy-video-execution-spec-").toRealPath()
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

object CozyVideoExecutionSpec {
  private final class SelectedProbe(missingimages: Set[String], daemonavailable: Boolean = true) extends CozyVideoTools.VideoToolProbe {
    def command(args: Vector[String], cwd: Path): CozyVideoTools.VideoCommandResult =
      if (args.take(2) == Vector("docker", "version") && !daemonavailable)
        CozyVideoTools.VideoCommandResult(1, "", "selected daemon unavailable")
      else if (args.take(3) == Vector("docker", "image", "inspect") && missingimages.contains(args.last))
        CozyVideoTools.VideoCommandResult(1, "", "selected image unavailable")
      else CozyVideoTools.VideoCommandResult(0, "/available/selected-tool", "")
    def httpGet(uri: java.net.URI): CozyVideoTools.VideoHttpResult = CozyVideoTools.VideoHttpResult(200, "available")
    def exists(path: Path): Boolean = true
  }

  private final class OperationRunner extends CozyVideo.VideoProcessRunner {
    val commands: scala.collection.mutable.ArrayBuffer[Vector[String]] = scala.collection.mutable.ArrayBuffer.empty

    def run(args: Vector[String], cwd: Path): CozyVideo.VideoCommandResult = {
      commands += args
      args.find(_.endsWith("render.mjs")) match {
        case Some(script) =>
          val workspace = _path(cwd, script).getParent.getParent
          val props = parser.parse(Files.readString(workspace.resolve("props.json"))).toOption.get
          val count = props.hcursor.downField("timing").get[Int]("totalFrames").toOption.get
          val names = Vector.tabulate(count)(index => s"frame-$index.png")
          val image = new BufferedImage(props.hcursor.get[Int]("width").toOption.get, props.hcursor.get[Int]("height").toOption.get, BufferedImage.TYPE_INT_RGB)
          names.foreach(name => ImageIO.write(image, "png", workspace.resolve(name).toFile))
          CozyVideoImplementation._write_silence_wav(workspace.resolve("composition.wav"), count.toDouble / props.hcursor.get[Int]("fps").toOption.get)
          Files.writeString(workspace.resolve("frame-audio.json"), Json.obj(
            "schema" -> Json.fromString("cozy.video.frame-audio.v1"),
            "width" -> props.hcursor.downField("width").focus.get, "height" -> props.hcursor.downField("height").focus.get,
            "fps" -> props.hcursor.downField("fps").focus.get, "frameCount" -> Json.fromInt(count),
            "frames" -> Json.fromValues(names.map(Json.fromString)), "audioPath" -> Json.fromString("composition.wav")).noSpaces)
          CozyVideo.VideoCommandResult(0, "selected full composition", "")
        case None if args.contains("/selected/ffprobe") =>
          CozyVideo.VideoCommandResult(0, """{"format":{"duration":"1"},"streams":[{"codec_type":"video","duration":"1"}]}""", "")
        case None =>
          val output = _path(cwd, args.last)
          Files.createDirectories(output.getParent)
          Files.writeString(output, "selected ordinary output")
          CozyVideo.VideoCommandResult(0, "selected encoder or assembly", "")
      }
    }
  }

  private def _path(root: Path, value: String): Path =
    if (value.startsWith("/workspace/")) root.resolve(value.stripPrefix("/workspace/")) else Path.of(value)
}
