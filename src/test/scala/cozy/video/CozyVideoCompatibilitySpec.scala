package cozy.video

import io.circe.{Json, parser}
import java.nio.file.{Files, Path}
import org.scalacheck.{Gen, Prop, Test}
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import CozyVideoTools.{VideoExecutionConfig, VideoToolMode, VideoToolStatus}

/*
 * @since   Oct. 11, 2026
 * @version Oct. 11, 2026
 * @author  ASAMI, Tomoharu
 */
final class CozyVideoCompatibilitySpec extends AnyWordSpec with Matchers with GivenWhenThen {
  import CozyVideoCompatibilitySpec._

  "Public transcription" should {
    "consume independent operating selections" which {
      "preserve transcripts and literal argv across every FFmpeg and whisper mode combination" in {
        _with_project { (root, home) =>
          val combinations = for { ffmpeg <- Vector("host", "docker"); whisper <- Vector("host", "docker") } yield (ffmpeg, whisper)
          combinations.foreach { case (ffmpeg, whisper) =>
            Given(s"selected $ffmpeg FFmpeg and $whisper whisper with distinct executable and image origins")
            _settings(root, home, ffmpeg, whisper)
            val config = _transcribe_config(root)
            val runner = new ConsumerRunner()
            When("the public consumer transcribes using its selected processes")
            val result = CozyVideo.transcribe(config, _empty_registry, runner)
            Then("paths, model, summaries, origins and timed transcript products agree with each selected tool")
            _expect_transcription(root, config, runner, ffmpeg, whisper)
            result should include("toolMode: docker")
          }
        }
      }

      "keep selected command and path interpretation coherent for generated mixed-mode cases" in {
        _with_project { (root, home) =>
          Given("generated independent portable modes and save names with spaces")
          val cases = for { ffmpeg <- Gen.oneOf("host", "docker"); whisper <- Gen.oneOf("host", "docker"); number <- Gen.choose(1, 100000) } yield (ffmpeg, whisper, number)
          val property = Prop.forAll(cases) { case (ffmpeg, whisper, number) =>
            Given(s"a normal layered $ffmpeg/$whisper operating configuration")
            _settings(root, home, ffmpeg, whisper)
            val config = _transcribe_config(root).copy(saveDir = root.resolve(s"output spaces/case-$number"))
            val runner = new ConsumerRunner()
            When("public transcription consumes the generated selection")
            CozyVideo.transcribe(config, _empty_registry, runner)
            Then("each selected command and path yields the same compatible transcript contract")
            _expect_transcription(root, config, runner, ffmpeg, whisper)
            true
          }
          When("ScalaCheck executes forty consumer interpretations with a retained seed")
          val checked = Test.check(Test.Parameters.default.withMinSuccessfulTests(40).withInitialSeed(71603L), property)
          Then("all generated mixed-mode contracts hold")
          checked.passed shouldBe true
          checked.succeeded should be >= 40
        }
      }

      "retain unconfigured Docker image commands and the container model without a host model" in {
        _with_project { (root, _) =>
          Given("an unconfigured project and no host model")
          val config = _transcribe_config(root)
          val runner = new ConsumerRunner()
          When("public transcription uses its default execution")
          CozyVideo.transcribe(config, _empty_registry, runner)
          Then("both producers use the existing default image and whisper uses the container model")
          runner.commands should have size 3
          runner.commands.foreach { args =>
            args.head shouldBe "docker"
            args should contain(CozyVideoModel.VideoToolSettings.DEFAULT_DOCKER_IMAGE)
          }
          runner.commands.last should contain("/opt/textus/models/ggml-base.bin")
          _json(config.saveDir.resolve("manifest.json")).hcursor.get[String]("toolMode").toOption shouldBe Some("docker")
        }
      }

      "preserve legacy explicit CLI mode and image precedence with literal executable tokens" in {
        _with_project { (root, home) =>
          Vector("host", "docker").foreach { mode =>
            Given("per-tool selections opposite to the explicit legacy CLI mode")
            _settings(root, home, if (mode == "host") "docker" else "host", if (mode == "host") "docker" else "host")
            val config = _transcribe_config(root).copy(toolMode = Some(mode), dockerImage = Some("cli-image"))
            val runner = new ConsumerRunner()
            When("public transcription resolves legacy CLI overrides")
            CozyVideo.transcribe(config, _empty_registry, runner)
            Then("CLI mode/image wins while the selected executable stays one literal argv element")
            val tools = _json(config.saveDir.resolve("manifest.json")).hcursor.downField("effectiveTools")
            Vector("ffmpeg", "whisper-cpp").foreach { tool =>
              tools.downField(tool).get[String]("mode").toOption shouldBe Some(mode)
              tools.downField(tool).get[String]("dockerImage").toOption shouldBe Some("cli-image")
              tools.downField(tool).downField("origins").get[String]("mode").toOption shouldBe Some("cli:--tool-mode")
            }
            runner.commands.head should contain("/literal tools/ffmpeg $(kept)")
            runner.commands.last should contain("/literal tools/whisper $(kept)")
            runner.commands.head.head shouldBe (if (mode == "host") "/literal tools/ffmpeg $(kept)" else "docker")
            if (mode == "docker") runner.commands.head should contain("cli-image")
          }
        }
      }

      "retain four-argument execution adapter source construction and global dispatch" in {
        _with_project { (root, _) =>
          Given("legacy four-argument Host and Docker adapter construction")
          Vector(VideoToolMode.Host, VideoToolMode.Docker).foreach { mode =>
            val adapter = CozyVideoModel.VideoTranscribeExecution(mode, "legacy-image", "literal-model", None)
            When("the legacy adapter projects into ordinary runtime dispatch")
            val execution = adapter.toVideoExecutionConfig
            val args = CozyVideoImplementation._execution_command(root, execution, "whisper-cpp", Vector("--version"))
            Then("an empty selection map retains its old global mode and image")
            execution.selections shouldBe empty
            execution.selected("ffmpeg").mode shouldBe mode
            execution.selected("whisper-cpp").mode shouldBe mode
            args.head shouldBe (if (mode == VideoToolMode.Host) "whisper-cli" else "docker")
            if (mode == VideoToolMode.Docker) args should contain("legacy-image")
          }
        }
      }

      "choose the host model only for selected Host whisper and honor existing model precedence" in {
        _with_project { (root, home) =>
          Given("Host whisper under global Docker with every existing model spelling configured")
          _settings(root, home, "docker", "host")
          _write(root.resolve(".cozy/config.json"), _json(root.resolve(".cozy/config.json")).deepMerge(Json.obj(
            "tools" -> Json.obj("whisperModel" -> Json.fromString("first.bin"), "whisper-model" -> Json.fromString("second.bin")),
            "video" -> Json.obj("whisper" -> Json.obj("model" -> Json.fromString("third.bin")), "whisper-model" -> Json.fromString("fourth.bin")))))
          val config = _transcribe_config(root)
          When("the model is resolved with and without a CLI override")
          val yaml = CozyVideoImplementation._transcribe_execution(config)
          val cli = CozyVideoImplementation._transcribe_execution(config.copy(whisperModel = Some("cli.bin")))
          Then("the first YAML spelling wins and the CLI overrides it for selected Host whisper")
          yaml.modelPath shouldBe root.resolve("first.bin").toString
          cli.modelPath shouldBe root.resolve("cli.bin").toString
          yaml.toolMode shouldBe VideoToolMode.Docker
          Given("selected Docker whisper under global Host with a host model CLI value")
          _settings(root, home, "host", "docker", global = "host")
          When("public transcription executes selected Docker whisper")
          val runner = new ConsumerRunner()
          CozyVideo.transcribe(config.copy(whisperModel = Some("cli.bin")), _empty_registry, runner)
          Then("the container model is consumed instead of the host CLI model")
          runner.commands.last should contain("/opt/textus/models/ggml-base.bin")
          runner.commands.last should not contain root.resolve("cli.bin").toString
        }
      }

      "reject missing selected Host model configuration before producers and work creation" in {
        _with_project { (root, home) =>
          Given("Host whisper under legacy Docker without a configured model")
          _settings(root, home, "docker", "host", model = false)
          val config = _transcribe_config(root)
          val runner = new ConsumerRunner()
          When("public transcription resolves its required Host model")
          val failure = intercept[Throwable](CozyVideo.transcribe(config, _empty_registry, runner))
          Then("the existing model guidance is reported before work or producers")
          failure.getMessage should include("Host transcription requires --whisper-model")
          runner.commands shouldBe empty
          Files.exists(config.saveDir) shouldBe false
        }
      }

      "validate malformed authored operating layers even when legacy CLI shadows them" in {
        _with_project { (root, _) =>
          Given("an unsupported authored tool mode and an explicit CLI Docker override")
          _write(root.resolve(".cozy/config.json"), Json.obj("video" -> Json.obj("tools" -> Json.obj("ffmpeg" -> Json.obj("mode" -> Json.fromString("automatic"))))))
          val runner = new ConsumerRunner()
          When("public transcription resolves all authored operating layers")
          val failure = intercept[Throwable](CozyVideo.transcribe(_transcribe_config(root).copy(toolMode = Some("docker")), _empty_registry, runner))
          Then("invalid shadowed input fails before producer execution")
          failure.getMessage should include("mode")
          runner.commands shouldBe empty
        }
      }
    }

    "apply selected container path constraints before work" which {
      "allow external input and save exactly when their consuming modes permit them" in {
        _with_project { (root, home) =>
          for { ffmpeg <- Vector("host", "docker"); whisper <- Vector("host", "docker"); externalinput <- Vector(false, true); externalsave <- Vector(false, true) } {
            Given(s"$ffmpeg FFmpeg, $whisper whisper and external input=$externalinput/save=$externalsave")
            _settings(root, home, ffmpeg, whisper)
            val input = (if (externalinput) home else root).resolve("input spaces/movie.mp4")
            Files.createDirectories(input.getParent)
            Files.writeString(input, "illustrative video fixture")
            val save = (if (externalsave) home else root).resolve(s"save-$ffmpeg-$whisper-$externalinput-$externalsave")
            val config = _transcribe_config(root).copy(inputVideo = input.getParent.resolve("../input spaces/movie.mp4"), saveDir = save.getParent.resolve(s"unused/../${save.getFileName}"), projectRootOverride = Some(root.resolve("unused/..")))
            val runner = new ConsumerRunner()
            val inputfailure = externalinput && ffmpeg == "docker"
            val savefailure = externalsave && (ffmpeg == "docker" || whisper == "docker")
            When("public transcription normalizes and validates selected paths")
            if (inputfailure || savefailure) {
              val failure = intercept[Throwable](CozyVideo.transcribe(config, _empty_registry, runner))
              Then("the matching legacy Docker prefix is reported before directory creation or producers")
              failure.getMessage should include(if (inputfailure) "Docker transcription requires input video under project root" else "Docker transcription requires --save under project root")
              Files.exists(save) shouldBe false
              runner.commands shouldBe empty
            } else {
              CozyVideo.transcribe(config, _empty_registry, runner)
              Then("the allowed paths normalize and each process sees its own host or workspace path")
              val manifest = _json(save.resolve("manifest.json"))
              manifest.hcursor.get[String]("inputVideo").toOption shouldBe Some(input.toString)
              manifest.hcursor.get[String]("audioPath").toOption shouldBe Some(save.resolve("audio.wav").toString)
              runner.commands.head should contain(_selected_path(root, input, ffmpeg))
              runner.commands.last should contain(_selected_path(root, save.resolve("audio.wav"), whisper))
              runner.commands.last should contain(_selected_path(root, save.resolve("whisper"), whisper))
            }
          }
        }
      }
    }

    "gate only consumed preflight checks" which {
      "retain production managed whisper semantics and ignore unused failing tools and images" in {
        _with_project { (root, home) =>
          Vector(("host", "host"), ("host", "docker"), ("docker", "host"), ("docker", "docker")).foreach { case (ffmpeg, whisper) =>
            Given("production providers with failing unused Remotion, Playwright and Pillow images")
            _settings(root, home, ffmpeg, whisper)
            val probe = new SelectedProbe(missingimages = Set("unused-image"))
            val recorder = new ContextRecorder()
            val registry = CozyVideoTools.VideoToolRegistry(CozyVideoTools.VideoToolRegistry.production(probe).providers :+ recorder)
            val runner = new ConsumerRunner()
            When("public transcription checks the production operation context")
            CozyVideo.transcribe(_transcribe_config(root).copy(checkTools = true), registry, runner)
            Then("only the two consumer selections govern Docker inventory and actual whisper still executes")
            val context = recorder.context.get
            context.execution.selections.keySet shouldBe Set("ffmpeg", "whisper-cpp")
            context.execution.requiresDocker shouldBe (ffmpeg == "docker" || whisper == "docker")
            val checks = registry.checks(context)
            if (whisper == "docker") checks.find(_.name == "whisper-cpp").get.status shouldBe VideoToolStatus.Unchecked
            checks.find(_.name == "textus-toolchain-image").get.status should not be VideoToolStatus.Available
            runner.commands.last should contain("-oj")
            val inventory = CozyVideoTools.DockerImageProvider(probe).check(context)
            inventory.status shouldBe (if (context.execution.requiresDocker) VideoToolStatus.Available else VideoToolStatus.Unchecked)
          }
        }
      }

      "stop for unavailable selected executables models daemon and consuming images" in {
        _with_project { (root, home) =>
          val cases = Vector(
            ProbeFailure("host", "host", Set.empty, Set("/literal tools/ffmpeg $(kept)"), true, true, "ffmpeg"),
            ProbeFailure("host", "host", Set.empty, Set("/literal tools/whisper $(kept)"), true, true, "whisper-cpp"),
            ProbeFailure("host", "host", Set.empty, Set.empty, true, false, "whisper-cpp"),
            ProbeFailure("host", "docker", Set.empty, Set.empty, false, true, "docker-toolchain"),
            ProbeFailure("docker", "host", Set("ffmpeg-image"), Set.empty, true, true, "docker-image"),
            ProbeFailure("host", "docker", Set("whisper-image"), Set.empty, true, true, "docker-image"))
          cases.foreach { scenario =>
            Given(s"a missing required ${scenario.check} in production preflight")
            _settings(root, home, scenario.ffmpeg, scenario.whisper)
            val probe = new SelectedProbe(scenario.images, scenario.executables, scenario.daemon, scenario.model)
            val runner = new ConsumerRunner()
            When("the public operation checks required dependencies")
            val failure = intercept[Throwable](CozyVideo.transcribe(_transcribe_config(root).copy(checkTools = true), CozyVideoTools.VideoToolRegistry.production(probe), runner))
            Then("the selected requirement stops every producer with existing context and hint")
            failure.getMessage should include(s"Cannot transcribe video: ${scenario.check} is not available.")
            runner.commands shouldBe empty
          }
        }
      }

      "reject required Missing Unchecked and Unsupported while preserving partial registry compatibility" in {
        _with_project { (root, home) =>
          Given("Host consumers and required statuses supplied by a partial injected registry")
          _settings(root, home, "host", "host")
          Vector(VideoToolStatus.Missing, VideoToolStatus.Unchecked, VideoToolStatus.Unsupported).foreach { status =>
            val registry = _registry("ffmpeg", VideoToolMode.Host, status)
            val runner = new ConsumerRunner()
            When("public transcription checks the supplied required status")
            val failure = intercept[Throwable](CozyVideo.transcribe(_transcribe_config(root).copy(checkTools = true), registry, runner))
            Then("all unavailable statuses retain the operation context and setup hint")
            failure.getMessage should include("Cannot transcribe video: ffmpeg is not available. selected setup hint")
            runner.commands shouldBe empty
          }
          Given("a partial registry containing only an unrelated Unsupported tool")
          val runner = new ConsumerRunner()
          When("public transcription checks that partial inventory")
          CozyVideo.transcribe(_transcribe_config(root).copy(checkTools = true), _registry("python-pillow", VideoToolMode.Host, VideoToolStatus.Unsupported), runner)
          Then("absent required checks are not invented and unrelated status does not gate producers")
          runner.commands should have size 3
        }
      }
    }

    "retain ordinary producer failure semantics" which {
      "stop after extraction failure before invoking whisper" in {
        _with_project { (root, _) =>
          Given("an injected selected FFmpeg extraction failure")
          val runner: ConsumerRunner = new ConsumerRunner(failure = Some("ffmpeg"))
          When("public transcription attempts extraction")
          val failure = intercept[Throwable](CozyVideo.transcribe(_transcribe_config(root), _empty_registry, runner))
          Then("extraction stops the operation without any whisper or alternate producer")
          failure.getMessage should include("ffmpeg audio extraction failed")
          runner.commands should have size 1
        }
      }

      "retain extracted audio when actual whisper fails without a mode or executable retry" in {
        _with_project { (root, _) =>
          Given("an actual selected whisper process failure after extraction")
          val config = _transcribe_config(root)
          val runner: ConsumerRunner = new ConsumerRunner(failure = Some("whisper"))
          When("public transcription invokes selected whisper")
          val failure = intercept[Throwable](CozyVideo.transcribe(config, _empty_registry, runner))
          Then("the failure retains ordinary audio output and attempts no alternate producer")
          failure.getMessage should include("whisper.cpp transcription failed")
          runner.commands should have size 3
          Files.isRegularFile(config.saveDir.resolve("audio.wav")) shouldBe true
          Files.exists(config.saveDir.resolve("manifest.json")) shouldBe false
        }
      }

      "treat an optional whisper version failure as nonfatal" in {
        _with_project { (root, _) =>
          Given("a failed optional version query and successful actual transcription")
          val config = _transcribe_config(root)
          val runner = new ConsumerRunner(failure = Some("version"))
          When("public transcription continues to its actual selected whisper process")
          CozyVideo.transcribe(config, _empty_registry, runner)
          Then("the transcript remains available with a null optional version")
          runner.commands should have size 3
          _json(config.saveDir.resolve("manifest.json")).hcursor.downField("whisperVersion").focus shouldBe Some(Json.Null)
          Files.isRegularFile(config.saveDir.resolve("transcript.json")) shouldBe true
        }
      }
    }
  }

  "Public replay" should {
    "consume selected Playwright without altering recording" which {
      "use selected Host Playwright under global Docker with external script and output" in {
        _with_project { (root, home) =>
          Given("Host Playwright with a literal node executable under legacy Docker and external paths")
          _replay_settings(root, "host", "docker")
          val script = home.resolve("demo.json")
          _replay_fixture(script)
          val output = home.resolve("recording.webm")
          val config = CozyVideo.ReplayConfig(script, Some(output), projectRootOverride = Some(root))
          val runner = new ConsumerRunner()
          When("public replay previews and executes the selected Host command")
          val preview = CozyVideo.replay(config.copy(dryRun = true), _empty_registry, runner)
          val result = CozyVideo.replay(config.copy(checkTools = true), _registry("python-pillow", VideoToolMode.Host, VideoToolStatus.Unsupported), runner)
          Then("the literal host command agrees with preview and records compatible effective settings")
          runner.commands should have size 1
          runner.commands.head.head shouldBe "/literal node $(kept)"
          preview should include(runner.commands.head.map(CozyVideoImplementation._shell_quote).mkString(" "))
          result should include("toolMode: docker")
          Files.isRegularFile(output) shouldBe true
          val manifest = _json(root.resolve("target/cozy-video/replay/demo/manifest.json"))
          manifest.hcursor.get[String]("schema").toOption shouldBe Some("cozy.video.replay-manifest.v1")
          manifest.hcursor.get[String]("outputVideo").toOption shouldBe Some(output.toString)
          manifest.hcursor.downField("effectiveTools").downField("playwright").get[String]("mode").toOption shouldBe Some("host")
          manifest.hcursor.downField("effectiveTools").downField("playwright").downField("origins").get[String]("executable").toOption.get should include("video.tools.playwright.executable")
        }
      }

      "use selected Docker Playwright under global Host and preserve contained-path constraints" in {
        _with_project { (root, home) =>
          Given("Docker Playwright with its own image under legacy Host")
          _replay_settings(root, "docker", "host")
          val script = root.resolve("demo.json")
          _replay_fixture(script)
          val output = root.resolve("recording.webm")
          val config = CozyVideo.ReplayConfig(script, Some(output), projectRootOverride = Some(root))
          val runner = new ConsumerRunner()
          When("public replay previews and executes the selected Docker command")
          val preview = CozyVideo.replay(config.copy(dryRun = true), _empty_registry, runner)
          CozyVideo.replay(config, _empty_registry, runner)
          Then("preview agrees with selected argv and workspace paths while summaries remain Host")
          runner.commands.head.head shouldBe "docker"
          runner.commands.head should contain("playwright-image")
          runner.commands.head should contain("/literal node $(kept)")
          runner.commands.head.last shouldBe "/workspace/target/cozy-video/replay/demo/replay.mjs"
          preview should include(runner.commands.head.map(CozyVideoImplementation._shell_quote).mkString(" "))
          val manifest = _json(root.resolve("target/cozy-video/replay/demo/manifest.json"))
          manifest.hcursor.get[String]("toolMode").toOption shouldBe Some("host")
          manifest.hcursor.downField("effectiveTools").downField("playwright").get[String]("dockerImage").toOption shouldBe Some("playwright-image")
          Given("the same selected Docker operation with external script or output")
          val external = home.resolve("demo.json")
          _replay_fixture(external)
          Vector(config.copy(scriptFile = external), config.copy(saveFile = Some(home.resolve("recording.webm")))).foreach { invalid =>
            val blockedrunner = new ConsumerRunner()
            When("public replay validates selected Docker root constraints")
            val failure = intercept[Throwable](CozyVideo.replay(invalid, _empty_registry, blockedrunner))
            Then("the existing Docker replay error stops producers")
            failure.getMessage should include("Docker replay requires")
            blockedrunner.commands shouldBe empty
          }
        }
      }

      "reject unavailable selected Playwright checks and ignore unrelated checks" in {
        _with_project { (root, _) =>
          Given("selected Host Playwright and a valid replay script")
          _replay_settings(root, "host", "docker")
          _replay_fixture(root.resolve("demo.json"))
          val config = CozyVideo.ReplayConfig(root.resolve("demo.json"), checkTools = true, projectRootOverride = Some(root))
          Vector(VideoToolStatus.Missing, VideoToolStatus.Unchecked, VideoToolStatus.Unsupported).foreach { status =>
            val runner = new ConsumerRunner()
            When("public replay checks its selected unavailable status")
            val failure = intercept[Throwable](CozyVideo.replay(config, _registry("playwright", VideoToolMode.Host, status), runner))
            Then("every required unavailable status fails with replay context and hint")
            failure.getMessage should include("Cannot replay video demo: playwright is not available. selected setup hint")
            runner.commands shouldBe empty
          }
          Given("only an unrelated failing partial registry entry")
          val runner = new ConsumerRunner()
          When("public replay checks and executes that partial inventory")
          CozyVideo.replay(config, _registry("ffmpeg", VideoToolMode.Docker, VideoToolStatus.Unsupported), runner)
          Then("unrelated checks and absent checks do not gate replay")
          runner.commands should have size 1
        }
      }
    }
  }

  "Public inspection and dry-run" should {
    "retain whole-operation compatibility" which {
      "preserve all portable Host and default Docker profiles and independent ffprobe" in {
        _with_project { (root, _) =>
          Vector(None, Some("host")).foreach { mode =>
            Given("a portable standard-profile project with an independently selected ffprobe executable")
            _project_fixture(root)
            _write(root.resolve(".cozy/config.json"), Json.obj("video" -> Json.obj("tools" -> Json.obj("ffprobe" -> Json.obj("executable" -> Json.fromString("/independent/probe"))))))
            val projectfile = root.resolve("video.json")
            val runner = new ConsumerRunner()
            When("public inspection and ordinary build dry-run interpret that profile")
            val inspected = CozyVideo.inspect(CozyVideo.InspectConfig(projectfile, checkTools = false, toolMode = mode), _empty_registry)
            val dryrun = CozyVideo.build(CozyVideo.BuildConfig(projectfile, dryRun = true, checkTools = false, toolMode = mode), _empty_registry, runner)
            Then("authored quality and effective software policy retain the normal renderMedia route and selected probe")
            Vector(inspected, dryrun).foreach { text =>
              text should include("encodingPolicy: standard")
              text should include("effectiveEncoder: libx264")
              text should include("encodingSettingsRole: authored")
              text should include("externalFrameEncoding: false")
              text should include(s"tool.ffprobe: mode=${mode.getOrElse("docker")} executable=/independent/probe")
              Vector("remotion", "ffmpeg", "playwright", "python-pillow", "whisper-cpp").foreach { tool =>
                text should include(s"tool.$tool: mode=${mode.getOrElse("docker")}")
              }
            }
            dryrun should include("/independent/probe")
            runner.commands shouldBe empty
          }
        }
      }
    }
  }

  private def _expect_transcription(root: Path, config: CozyVideo.TranscribeConfig, runner: ConsumerRunner, ffmpeg: String, whisper: String): Unit = {
    runner.commands should have size 3
    val extraction = runner.commands.head
    val recognition = runner.commands.last
    extraction.head shouldBe (if (ffmpeg == "docker") "docker" else "/literal tools/ffmpeg $(kept)")
    recognition.head shouldBe (if (whisper == "docker") "docker" else "/literal tools/whisper $(kept)")
    extraction should contain("/literal tools/ffmpeg $(kept)")
    recognition should contain("/literal tools/whisper $(kept)")
    if (ffmpeg == "docker") extraction should contain("ffmpeg-image")
    if (whisper == "docker") recognition should contain("whisper-image")
    extraction should contain(_selected_path(root, config.inputVideo, ffmpeg))
    extraction.last shouldBe _selected_path(root, config.saveDir.resolve("audio.wav"), ffmpeg)
    recognition should contain(_selected_path(root, config.saveDir.resolve("audio.wav"), whisper))
    recognition.last shouldBe _selected_path(root, config.saveDir.resolve("whisper"), whisper)
    recognition(recognition.indexOf("-m") + 1) shouldBe (if (whisper == "docker") "/opt/textus/models/ggml-base.bin" else root.resolve("model.bin").toString)
    val manifest = _json(config.saveDir.resolve("manifest.json"))
    manifest.hcursor.get[String]("schema").toOption shouldBe Some("cozy.video.transcription-manifest.v1")
    manifest.hcursor.get[Int]("segmentCount").toOption shouldBe Some(1)
    manifest.hcursor.get[String]("dockerImage").toOption shouldBe Some(CozyVideoModel.VideoToolSettings.DEFAULT_DOCKER_IMAGE)
    val tools = manifest.hcursor.downField("effectiveTools")
    tools.focus.get.asObject.get.keys.toSet shouldBe Set("ffmpeg", "whisper-cpp")
    Vector("ffmpeg" -> ffmpeg, "whisper-cpp" -> whisper).foreach { case (tool, mode) =>
      tools.downField(tool).get[String]("mode").toOption shouldBe Some(mode)
      tools.downField(tool).downField("origins").get[String]("executable").toOption.get should include("home/.cozy/config.json")
      tools.downField(tool).downField("origins").get[String]("mode").toOption.get should include("project/.cozy/config.json")
    }
    val transcript = _json(config.saveDir.resolve("transcript.json"))
    transcript.hcursor.get[String]("schema").toOption shouldBe Some("cozy.video.transcript.v1")
    val segment = transcript.hcursor.downField("segments").downArray
    segment.get[Double]("start").toOption shouldBe Some(1.25)
    segment.get[Double]("end").toOption shouldBe Some(2.5)
    segment.get[String]("text").toOption shouldBe Some("Selected narration")
    Files.readString(config.saveDir.resolve("captions.srt")) should include("00:00:01,250 --> 00:00:02,500")
    _json(config.saveDir.resolve("narration.json")).hcursor.downField("scenes").downArray.get[String]("narration").toOption shouldBe Some("Selected narration")
  }
}

object CozyVideoCompatibilitySpec {
  private val _empty_registry = CozyVideoTools.VideoToolRegistry(Vector.empty)

  private final case class ProbeFailure(ffmpeg: String, whisper: String, images: Set[String], executables: Set[String], daemon: Boolean, model: Boolean, check: String)

  private final class ContextRecorder extends CozyVideoTools.VideoToolProvider {
    var context: Option[CozyVideoTools.VideoToolContext] = None
    def check(context: CozyVideoTools.VideoToolContext): CozyVideoTools.VideoToolCheck = {
      this.context = Some(context)
      CozyVideoTools.VideoToolCheck("context-fixture", VideoToolMode.Host, VideoToolStatus.Unchecked, "executable contract fixture")
    }
  }

  private final class SelectedProbe(missingimages: Set[String] = Set.empty, missingexecutables: Set[String] = Set.empty, daemonavailable: Boolean = true, modelavailable: Boolean = true) extends CozyVideoTools.VideoToolProbe {
    def command(args: Vector[String], cwd: Path): CozyVideoTools.VideoCommandResult = {
      val failed = (args.take(2) == Vector("docker", "version") && !daemonavailable) ||
        (args.take(3) == Vector("docker", "image", "inspect") && missingimages.contains(args.last)) || missingexecutables.contains(args.head) ||
        args.takeRight(3) == Vector("textus-toolchain", "check", "video")
      if (failed) CozyVideoTools.VideoCommandResult(1, "", "selected prerequisite unavailable")
      else CozyVideoTools.VideoCommandResult(0, "/selected/available", "")
    }
    def httpGet(uri: java.net.URI): CozyVideoTools.VideoHttpResult = CozyVideoTools.VideoHttpResult(0, "", Some("unused service"))
    def exists(path: Path): Boolean = modelavailable
  }

  /** These outputs document dispatch contracts; they are not native media evidence. */
  private final class ConsumerRunner(failure: Option[String] = None) extends CozyVideo.VideoProcessRunner {
    val commands = scala.collection.mutable.ArrayBuffer.empty[Vector[String]]
    def run(args: Vector[String], cwd: Path): CozyVideo.VideoCommandResult = {
      commands += args
      val operation = if (args.contains("-vn")) "ffmpeg" else if (args.contains("--version")) "version" else if (args.contains("-oj")) "whisper" else "replay"
      if (failure.contains(operation)) CozyVideo.VideoCommandResult(1, "", s"selected $operation fixture failure")
      else {
        operation match {
          case "ffmpeg" =>
            val output = _path(cwd, args.last)
            Files.createDirectories(output.getParent)
            Files.writeString(output, "illustrative extracted audio")
          case "whisper" =>
            _write(Path.of(_path(cwd, args.last).toString + ".json"), Json.obj("segments" -> Json.arr(Json.obj(
              "start" -> Json.fromDoubleOrNull(1.25), "end" -> Json.fromDoubleOrNull(2.5), "text" -> Json.fromString(" Selected narration ")))))
          case "replay" =>
            val props = _json(_path(cwd, args.last).getParent.resolve("props.json"))
            props.hcursor.get[String]("outputPath").toOption.foreach { value =>
              val output = cwd.resolve(value).normalize()
              Files.createDirectories(output.getParent)
              Files.writeString(output, "illustrative recorded video")
            }
          case _ =>
        }
        CozyVideo.VideoCommandResult(0, "selected fixture version/output", "")
      }
    }
  }

  private def _registry(name: String, mode: VideoToolMode, status: CozyVideoTools.VideoToolStatus): CozyVideoTools.VideoToolRegistry =
    CozyVideoTools.VideoToolRegistry(Vector(new CozyVideoTools.VideoToolProvider {
      def check(context: CozyVideoTools.VideoToolContext): CozyVideoTools.VideoToolCheck =
        CozyVideoTools.VideoToolCheck(name, mode, status, "selected contract fixture", Some("selected setup hint"))
    }))

  private def _settings(root: Path, home: Path, ffmpeg: String, whisper: String, global: String = "docker", model: Boolean = true): Unit = {
    _write(home.resolve(".cozy/config.json"), Json.obj("video" -> Json.obj("tools" -> Json.obj(
      "ffmpeg" -> Json.obj("executable" -> Json.fromString("/literal tools/ffmpeg $(kept)")),
      "whisper-cpp" -> Json.obj("executable" -> Json.fromString("/literal tools/whisper $(kept)"))))))
    def _selection_(mode: String, image: String): Json =
      if (mode == "docker") Json.obj("mode" -> Json.fromString(mode), "docker-image" -> Json.fromString(image))
      else Json.obj("mode" -> Json.fromString(mode))
    _write(root.resolve(".cozy/config.json"), Json.obj(
      "tools" -> (if (model) Json.obj("whisperModel" -> Json.fromString("model.bin")) else Json.obj()),
      "video" -> Json.obj("tool-mode" -> Json.fromString(global), "tools" -> Json.obj(
        "ffmpeg" -> _selection_(ffmpeg, "ffmpeg-image"), "whisper-cpp" -> _selection_(whisper, "whisper-image"),
        "remotion" -> Json.obj("docker-image" -> Json.fromString("unused-image")),
        "playwright" -> Json.obj("docker-image" -> Json.fromString("unused-image")),
        "python-pillow" -> Json.obj("docker-image" -> Json.fromString("unused-image"))))))
  }

  private def _transcribe_config(root: Path): CozyVideo.TranscribeConfig = {
    val input = root.resolve("movie.mp4")
    Files.writeString(input, "illustrative input video")
    CozyVideo.TranscribeConfig(input, root.resolve("output"), projectRootOverride = Some(root))
  }

  private def _replay_settings(root: Path, mode: String, global: String): Unit = {
    val selected = Json.obj("mode" -> Json.fromString(mode), "executable" -> Json.fromString("/literal node $(kept)"))
    val fields = if (mode == "docker") selected.deepMerge(Json.obj("docker-image" -> Json.fromString("playwright-image"))) else selected
    _write(root.resolve(".cozy/config.json"), Json.obj("video" -> Json.obj("tool-mode" -> Json.fromString(global), "tools" -> Json.obj("playwright" -> fields))))
  }

  private def _replay_fixture(path: Path): Unit =
    _write(path, Json.obj("schema" -> Json.fromString("cozy.video.replay-script.v1"), "sourceVideo" -> Json.fromString("demo.mp4"),
      "manualReview" -> Json.fromBoolean(false), "steps" -> Json.arr(Json.obj("kind" -> Json.fromString("goto"), "url" -> Json.fromString("http://example.test/")))))

  private def _project_fixture(root: Path): Unit = {
    _write(root.resolve("video.json"), Json.obj("name" -> Json.fromString("compatibility"), "output" -> Json.fromString("build/final.mp4"),
      "renderer" -> Json.obj("engine" -> Json.fromString("remotion"), "policy" -> Json.fromString("standard")),
      "parts" -> Json.arr(Json.obj("id" -> Json.fromString("part"), "type" -> Json.fromString("dialogue"), "script" -> Json.fromString("script.json"), "output" -> Json.fromString("build/part.mp4")))))
    _write(root.resolve("script.json"), Json.obj("scenes" -> Json.arr(Json.obj("id" -> Json.fromString("scene"), "narration" -> Json.fromString("Portable narration"), "duration" -> Json.fromInt(1)))))
  }

  private def _selected_path(root: Path, path: Path, mode: String): String =
    if (mode == "docker") "/workspace/" + root.relativize(path).toString else path.toString

  private def _path(root: Path, value: String): Path =
    if (value.startsWith("/workspace/")) root.resolve(value.stripPrefix("/workspace/")) else Path.of(value)

  private def _json(path: Path): Json = parser.parse(Files.readString(path)).toOption.get

  private def _write(path: Path, json: Json): Unit = {
    Files.createDirectories(path.getParent)
    Files.writeString(path, json.spaces2)
  }

  private def _with_project(body: (Path, Path) => Unit): Unit = CozyVideoExecutionSpec.synchronized {
    val parent = Files.createDirectories(Path.of("target/cozy-video/work/compatibility-spec").toAbsolutePath.normalize())
    val directory = Files.createTempDirectory(parent, "consumer-").toRealPath()
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
