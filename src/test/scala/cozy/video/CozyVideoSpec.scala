package cozy.video

import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path, Paths}
import java.security.MessageDigest
import scala.collection.JavaConverters._
import scala.collection.mutable.ArrayBuffer
import java.net.URI
import io.circe.Json
import io.circe.parser
import org.goldenport.context.FaultException
import org.scalatest.GivenWhenThen
import org.scalatest.wordspec.AnyWordSpec
import cozy.CozySpecVocabulary

/*
 * @since   Jun. 18, 2026
 *  version Jun. 24, 2026
 *  version Jul. 20, 2026
 * @version Aug. 19, 2026
 * @author  ASAMI, Tomoharu
 */
final class CozyVideoSpec
    extends AnyWordSpec
    with GivenWhenThen
    with CozySpecVocabulary {
  import CozyVideoSpec._

  "Cozy Video" should {
    "encoding policy" which {
      "decode all named policies into their contracted settings" in {
        Given("renderers configured with each named encoding policy")
        val configurations = Vector(
          "lightweight" -> (18, 1280, 720, 32),
          "standard" -> (30, 1280, 720, 23),
          "quality" -> (30, 1920, 1080, 18)
        )

        configurations.foreach { case (name, expected) =>
          When(s"the $name renderer is decoded and resolved")
          val renderer = _decode_renderer(s"""{"policy": "$name"}""").toOption.get
          val settings = renderer.resolveEncoding

          Then(s"the $name policy supplies its contracted encoding settings")
          settings.policy.name should equal(name)
          (settings.fps, settings.width, settings.height, settings.crf) should equal(expected)
          settings.x264Preset should equal(None)
        }
      }

      "resolve lightweight when policy is absent" in {
        Given("a renderer without a policy or explicit encoding fields")
        When("the renderer is decoded and resolved")
        val settings = _decode_renderer("{}").toOption.get.resolveEncoding

        Then("lightweight settings are selected")
        settings.policy.name should equal("lightweight")
        (settings.fps, settings.width, settings.height, settings.crf) should equal((18, 1280, 720, 32))
        settings.x264Preset should equal(None)
      }

      "let each explicit encoding field override its selected policy value" in {
        Given("standard renderers with one explicit encoding override each")
        val configurations = Vector(
          "fps" -> ("{\"policy\": \"standard\", \"fps\": 24}", (24, 1280, 720, 23, None)),
          "width" -> ("{\"policy\": \"standard\", \"width\": 1440}", (30, 1440, 720, 23, None)),
          "height" -> ("{\"policy\": \"standard\", \"height\": 900}", (30, 1280, 900, 23, None)),
          "crf" -> ("{\"policy\": \"standard\", \"crf\": 27}", (30, 1280, 720, 27, None)),
          "x264Preset" -> ("{\"policy\": \"standard\", \"x264Preset\": \"slow\"}", (30, 1280, 720, 23, Some("slow")))
        )

        configurations.foreach { case (field, (configuration, expected)) =>
          When(s"only $field is explicitly configured")
          val settings = _decode_renderer(configuration).toOption.get.resolveEncoding

          Then(s"only $field overrides the standard policy")
          (settings.fps, settings.width, settings.height, settings.crf, settings.x264Preset) should equal(expected)
        }
      }

      "keep encoding policy separate from composition strategy" in {
        Given("a renderer with both an encoding policy and a composition strategy")
        When("the renderer is decoded")
        val renderer = _decode_renderer("{\"policy\": \"quality\", \"strategy\": \"narration-card\"}").toOption.get

        Then("policy resolution leaves the composition strategy unchanged")
        renderer.policy.map(_.name) should equal(Some("quality"))
        renderer.strategy should equal(Some("narration-card"))
        renderer.strategyOrPolicy should equal(Some("narration-card"))
      }

      "reject invalid encoding policy configuration" in {
        Given("renderer configurations with invalid policy or encoding values")
        val configurations = Vector(
          "policy" -> ("{\"policy\": \"archive\"}", "renderer.policy value: 'archive'"),
          "fps" -> ("{\"fps\": 0}", "renderer.fps value: '0'"),
          "width" -> ("{\"width\": -1}", "renderer.width value: '-1'"),
          "height" -> ("{\"height\": 0}", "renderer.height value: '0'"),
          "crf" -> ("{\"crf\": -1}", "renderer.crf value: '-1'"),
          "x264Preset blank" -> ("{\"x264Preset\": \"   \"}", "renderer.x264Preset value: '   '"),
          "x264Preset unsupported" -> ("{\"x264Preset\": \"ultrafast\"}", "renderer.x264Preset value: 'ultrafast'")
        )

        configurations.foreach { case (field, (configuration, expected)) =>
          When(s"the invalid $field value is decoded")
          val result = _decode_renderer(configuration)

          Then("decoding names the invalid field and value")
          result.left.toOption.get.getMessage should include(expected)
        }
      }
    }

    "inspect planning" which {
      "video inspect accepts JSON, YAML, HOCON, and XML project files" in {
        _with_temp_dir("cozy-video-formats") { dir =>
          _write(dir.resolve("script.json"), _script_json)
          val files = Vector(
            "video_project.json" -> _project_json("script.json"),
            "video_project.yaml" -> _project_yaml("script.json"),
            "video_project.conf" -> _project_hocon("script.json"),
            "video_project.xml" -> _project_xml("script.json")
          )

          files.foreach { case (name, text) =>
            _write(dir.resolve(name), text)
            val out = CozyVideo.inspect(
              CozyVideo.InspectConfig(dir.resolve(name), checkTools = false),
              CozyVideo.VideoToolRegistry(Vector.empty)
            )

            ((out.contains("Cozy Video Inspect")) shouldBe true)
            ((out.contains("title: Sample Video")) shouldBe true)
            ((out.contains("parts: 1")) shouldBe true)
            ((out.contains("part[1]: intro")) shouldBe true)
            ((out.contains("type: dialogue")) shouldBe true)
            ((out.contains("scriptStatus: found")) shouldBe true)
            ((out.contains("scenes: 2")) shouldBe true)
            ((out.contains("expandedScenes: 3")) shouldBe true)
            ((out.contains("estimatedDuration: 15.00")) shouldBe true)
          }
        }
      }

      "video inspect reports mixed part plans and unsupported part types" in {
        _with_temp_dir("cozy-video-mixed") { dir =>
          _write(dir.resolve("dialogue.json"), _script_json)
          _write(dir.resolve("storyboard.json"), _script_json)
          _write(dir.resolve("web.json"), _script_json)
          _write(
            dir.resolve("video_project.json"),
            s"""{
           |  "title": "Mixed Video",
           |  "output": "build/final.mp4",
           |  "renderer": {"engine": "remotion", "strategy": "slide"},
           |  "parts": [
           |    {"id": "lecture", "type": "dialogue", "script": "dialogue.json", "output": "01/build/part.mp4", "audioDir": "01/build/audio"},
           |    {"id": "board", "type": "storyboard", "script": "storyboard.json"},
           |    {"id": "demo", "type": "web-demo", "script": "web.json", "steps": "steps.json", "recordDir": "03/build/recording"},
           |    {"id": "future", "type": "future-kind", "script": "missing.json"}
           |  ]
           |}
           |""".stripMargin
          )

          val out = CozyVideo.inspect(
            CozyVideo.InspectConfig(
              dir.resolve("video_project.json"),
              checkTools = false
            ),
            CozyVideo.VideoToolRegistry(Vector.empty)
          )

          ((out.contains("parts: 4")) shouldBe true)
          ((out.contains("part[1]: lecture")) shouldBe true)
          ((out.contains("type: dialogue")) shouldBe true)
          ((out.contains(
            "renderer: engine=remotion, strategy=slide"
          )) shouldBe true)
          ((out.contains(
            "output: " + dir.resolve("01/build/part.mp4").normalize()
          )) shouldBe true)
          ((out.contains(
            "audioDir: " + dir.resolve("01/build/audio").normalize()
          )) shouldBe true)
          ((out.contains("part[3]: demo")) shouldBe true)
          ((out.contains("type: web-demo")) shouldBe true)
          ((out.contains("steps: steps.json")) shouldBe true)
          ((out.contains(
            "recordDir: " + dir.resolve("03/build/recording").normalize()
          )) shouldBe true)
          ((out.contains("part[4]: future")) shouldBe true)
          ((out.contains("type: future-kind (unsupported)")) shouldBe true)
          ((out.contains("scriptStatus: missing")) shouldBe true)
          ((out.contains("artifacts:")) shouldBe true)
          ((out.contains(
            "project-output: planned " + dir
              .resolve("build/final.mp4")
              .normalize()
          )) shouldBe true)
          ((out.contains(
            "part-output: planned " + dir
              .resolve("01/build/part.mp4")
              .normalize()
          )) shouldBe true)
          ((out.contains(
            "part-audio-dir: planned " + dir
              .resolve("01/build/audio")
              .normalize()
          )) shouldBe true)
          ((out.contains(
            "part-steps: missing-input " + dir.resolve("steps.json").normalize()
          )) shouldBe true)
          ((out.contains(
            "part-record-dir: planned " + dir
              .resolve("03/build/recording")
              .normalize()
          )) shouldBe true)
          ((out.contains(
            "part-manifest: planned " + dir
              .resolve("01/build/part.manifest.json")
              .normalize()
          )) shouldBe true)
        }
      }

    }

    "build planning and execution" which {
      "video build dry-run reports artifact and command plans without external tools" in {
        _with_temp_dir("cozy-video-build-dry-run") { dir =>
          _write(dir.resolve("dialogue.json"), _script_json)
          _write(dir.resolve("storyboard.json"), _script_json)
          _write(dir.resolve("web.json"), _script_json)
          _write(dir.resolve("steps.json"), """{"steps": []}""")
          _write(
            dir.resolve("video_project.json"),
            s"""{
           |  "title": "Build Video",
           |  "output": "build/final.mp4",
           |  "renderer": {"engine": "remotion", "strategy": "slide"},
           |  "parts": [
           |    {"id": "lecture", "type": "dialogue", "script": "dialogue.json", "output": "01/build/part.mp4", "audioDir": "01/build/audio"},
           |    {"id": "board", "type": "storyboard", "script": "storyboard.json"},
           |    {"id": "demo", "type": "web-demo", "script": "web.json", "steps": "steps.json", "recordDir": "03/build/recording"},
           |    {"id": "future", "type": "future-kind", "script": "missing.json"}
           |  ]
           |}
           |""".stripMargin
          )

          val out = CozyVideo.build(
            CozyVideo.BuildConfig(
              dir.resolve("video_project.json"),
              dryRun = true,
              checkTools = false
            ),
            CozyVideo.VideoToolRegistry.default
          )

          ((out.contains("Cozy Video Build Dry-Run")) shouldBe true)
          ((out.contains("artifacts:")) shouldBe true)
          ((out.contains(
            "part-script: missing-input " + dir
              .resolve("missing.json")
              .normalize()
          )) shouldBe true)
          ((out.contains("commands:")) shouldBe true)
          ((out.contains(
            "part.lecture.parse-script: cozy (host) - parse dialogue script"
          )) shouldBe true)
          ((out.contains(
            "part.lecture.synthesize: voicevox (external-service) - synthesize scene audio"
          )) shouldBe true)
          ((out.contains(
            "part.lecture.prepare-visuals: python-pillow (docker) - docker run --rm"
          )) shouldBe true)
          ((out.contains(
            "part.lecture.render: remotion (docker) - docker run --rm"
          )) shouldBe true)
          ((out.contains(
            "part.board.render: remotion (docker) - docker run --rm"
          )) shouldBe true)
          ((out.contains(
            "part.demo.capture: playwright (docker) - docker run --rm"
          )) shouldBe true)
          ((out.contains(
            "part.demo.synthesize: voicevox (external-service) - synthesize scene audio"
          )) shouldBe true)
          ((out.contains(
            "project.concat: ffmpeg (docker) - docker run --rm"
          )) shouldBe true)
          ((out.contains(
            "project.manifest: cozy (host) - write project manifest"
          )) shouldBe true)
          ((!out.contains("  - part.future.render:")) shouldBe true)
        }
      }

      "video project manifest follows an output outside the source package" in {
        _with_temp_dir("cozy-video-external-output") { dir =>
          Given("a video project whose final output belongs to a sibling target tree")
          _write(dir.resolve("script.json"), _script_json)
          _write(
            dir.resolve("video_project.json"),
            """{
              |  "title": "External Output",
              |  "output": "../target/media/final.mp4",
              |  "parts": [
              |    {"id": "intro", "type": "dialogue", "script": "script.json"}
              |  ]
              |}
              |""".stripMargin
          )

          When("Cozy plans the video build")
          val out = CozyVideo.build(
            CozyVideo.BuildConfig(
              dir.resolve("video_project.json"),
              dryRun = true,
              checkTools = false
            ),
            CozyVideo.VideoToolRegistry.default
          )

          Then("the project manifest follows the final output outside the source package")
          out should include(
            "project-manifest: planned " + dir.resolve("../target/media/manifest.json").normalize()
          )
          out should not include dir.resolve("build/manifest.json").normalize().toString
        }
      }

      "video build dry-run can use explicit host tool mode" in {
        _with_temp_dir("cozy-video-build-host-mode") { dir =>
          _write(dir.resolve("script.json"), _script_json)
          _write(
            dir.resolve("video_project.json"),
            s"""{
           |  "title": "Host Mode",
           |  "renderer": {"engine": "remotion"},
           |  "parts": [
           |    {"id": "intro", "type": "dialogue", "script": "script.json"}
           |  ]
           |}
           |""".stripMargin
          )

          val out = CozyVideo.build(
            CozyVideo.BuildConfig(
              dir.resolve("video_project.json"),
              dryRun = true,
              checkTools = false,
              toolMode = Some("host")
            ),
            CozyVideo.VideoToolRegistry.default
          )

          ((out.contains("toolMode: host")) shouldBe true)
          ((out.contains(
            "part.intro.prepare-visuals: python-pillow (host) - prepare dialogue visual helper assets"
          )) shouldBe true)
          ((out.contains(
            "part.intro.render: remotion (host) - render dialogue part"
          )) shouldBe true)
          ((out.contains(
            "project.concat: ffmpeg (host) - concat planned part outputs into final video"
          )) shouldBe true)
        }
      }

      "video build dry-run resolves docker mode and image precedence" in {
        _with_temp_dir("cozy-video-build-docker-precedence") { dir =>
          _write(dir.resolve("script.json"), _script_json)
          _write(
            dir.resolve("conf/cozy/config.yaml"),
            "video:\n  docker-image: shared-image\n  tool-mode: host\ncozy:\n  docker-image: cozy-image\n"
          )
          _write(
            dir.resolve(".cozy/config.yaml"),
            "video:\n  docker-image: local-image\n"
          )
          _write(
            dir.resolve("video_project.json"),
            s"""{
           |  "title": "Docker Precedence",
           |  "tools": {
           |    "toolMode": "docker",
           |    "dockerImage": "project-image"
           |  },
           |  "renderer": {"engine": "remotion"},
           |  "parts": [
           |    {"id": "intro", "type": "dialogue", "script": "script.json"}
           |  ]
           |}
           |""".stripMargin
          )

          val project = CozyVideo.build(
            CozyVideo.BuildConfig(
              dir.resolve("video_project.json"),
              dryRun = true,
              checkTools = false
            ),
            CozyVideo.VideoToolRegistry.default
          )
          val cli = CozyVideo.build(
            CozyVideo.BuildConfig(
              dir.resolve("video_project.json"),
              dryRun = true,
              checkTools = false,
              toolMode = Some("host"),
              dockerImage = Some("cli-image")
            ),
            CozyVideo.VideoToolRegistry.default
          )

          ((project.contains("toolMode: docker")) shouldBe true)
          ((project.contains("dockerImage: project-image")) shouldBe true)
          ((project.contains(
            "docker run --rm -v '" + dir + ":/workspace' -w /workspace 'project-image' 'remotion'"
          )) shouldBe true)
          ((cli.contains("toolMode: host")) shouldBe true)
          ((cli.contains("dockerImage: cli-image")) shouldBe true)
          ((cli.contains("part.intro.render: remotion (host)")) shouldBe true)
        }
      }

      "video build dry-run reads video config defaults with local override" in {
        _with_temp_dir("cozy-video-build-config-defaults") { dir =>
          _write(dir.resolve("script.json"), _script_json)
          _write(
            dir.resolve("conf/cozy/config.yaml"),
            "video:\n  docker-image: shared-image\n  tool-mode: host\n"
          )
          _write(
            dir.resolve(".cozy/config.yaml"),
            "video:\n  docker-image: local-image\n  tool-mode: docker\n"
          )
          _write(
            dir.resolve("video_project.json"),
            _project_json("script.json")
          )

          val out = CozyVideo.build(
            CozyVideo.BuildConfig(
              dir.resolve("video_project.json"),
              dryRun = true,
              checkTools = false
            ),
            CozyVideo.VideoToolRegistry.default
          )

          ((out.contains("toolMode: docker")) shouldBe true)
          ((out.contains("dockerImage: local-image")) shouldBe true)
        }
      }

      "video build fails explicitly for invalid tool mode" in {
        _with_temp_dir("cozy-video-build-invalid-tool-mode") { dir =>
          _write(dir.resolve("script.json"), _script_json)
          _write(
            dir.resolve("video_project.json"),
            _project_json("script.json")
          )

          val e = intercept[Throwable] {
            CozyVideo.build(
              CozyVideo.BuildConfig(
                dir.resolve("video_project.json"),
                dryRun = true,
                checkTools = false,
                toolMode = Some("invalid")
              ),
              CozyVideo.VideoToolRegistry.default
            )
          }

          ((e.getMessage.contains("Invalid video tool mode")) shouldBe true)
        }
      }

      "video build dry-run excludes missing inputs from render and concat plans" in {
        _with_temp_dir("cozy-video-build-missing-inputs") { dir =>
          _write(dir.resolve("web.json"), _script_json)
          _write(
            dir.resolve("video_project.json"),
            s"""{
           |  "title": "Missing Inputs",
           |  "parts": [
           |    {"id": "dialogue-missing", "type": "dialogue", "script": "missing-dialogue.json"},
           |    {"id": "web-missing-steps", "type": "web-demo", "script": "web.json", "steps": "missing-steps.json"}
           |  ]
           |}
           |""".stripMargin
          )

          val out = CozyVideo.build(
            CozyVideo.BuildConfig(
              dir.resolve("video_project.json"),
              dryRun = true,
              checkTools = false
            ),
            CozyVideo.VideoToolRegistry.default
          )

          ((out.contains(
            "part-script: missing-input " + dir
              .resolve("missing-dialogue.json")
              .normalize()
          )) shouldBe true)
          ((out.contains(
            "part-steps: missing-input " + dir
              .resolve("missing-steps.json")
              .normalize()
          )) shouldBe true)
          ((!out.contains("  - part.dialogue-missing.render:")) shouldBe true)
          ((!out.contains("  - part.web-missing-steps.capture:")) shouldBe true)
          ((!out.contains("  - part.web-missing-steps.render:")) shouldBe true)
          ((!out.contains(
            "    inputs: " + dir
              .resolve("build/parts/dialogue-missing.mp4")
              .normalize()
          )) shouldBe true)
          ((!out.contains(
            "    inputs: " + dir
              .resolve("build/parts/web-missing-steps.mp4")
              .normalize()
          )) shouldBe true)
        }
      }

      "video build preflights Docker dependencies without an explicit tool check" in {
        _with_temp_dir("cozy-video-build-docker-preflight") { dir =>
          Given("a Docker video build whose daemon dependency is unavailable")
          _write(dir.resolve("script.json"), _script_json)
          _write(dir.resolve("video_project.json"), _project_json("script.json"))
          val runner = AssemblyRunner()
          val tools = CozyVideo.VideoToolRegistry(Vector(
            StubProvider(
              CozyVideo.VideoToolCheck(
                "docker-toolchain",
                CozyVideo.VideoToolMode.Docker,
                CozyVideo.VideoToolStatus.Missing,
                "Docker daemon is not reachable.",
                Some("Install/start Docker Desktop or a compatible Docker daemon.")
              )
            )
          ))

          When("Cozy builds without --check-tools")
          val fault = intercept[FaultException] {
            CozyVideo.build(
              CozyVideo.BuildConfig(
                dir.resolve("video_project.json"),
                dryRun = false,
                checkTools = false
              ),
              tools,
              runner
            )
          }

          Then("the unavailable Docker subsystem stops assembly before runner invocation")
          fault.getMessage should include_text("docker-toolchain")
          fault.getMessage should include_text("Docker daemon is not reachable")
          fault.getMessage should include_text("Install/start Docker Desktop")
          runner.commands shouldBe empty
        }
      }

      "video build assembles rendered parts with ffmpeg and validates with ffprobe" in {
        _with_temp_dir("cozy-video-build-final-docker") { dir =>
          Given("two rendered project parts configured for Docker assembly")
          _write(dir.resolve("dialogue.json"), _script_json)
          _write(dir.resolve("storyboard.json"), _script_json)
          _write_bytes(
            dir.resolve("build/parts/lecture.mp4"),
            Array[Byte](1, 2, 3)
          )
          _write_bytes(
            dir.resolve("build/parts/board.mp4"),
            Array[Byte](4, 5, 6)
          )
          _write(
            dir.resolve("video_project.json"),
            s"""{
           |  "title": "Final Build",
           |  "output": "build/final.mp4",
           |  "parts": [
           |    {"id": "lecture", "type": "dialogue", "script": "dialogue.json", "output": "build/parts/lecture.mp4"},
           |    {"id": "board", "type": "storyboard", "script": "storyboard.json", "output": "build/parts/board.mp4"}
           |  ]
           |}
           |""".stripMargin
          )
          val runner = AssemblyRunner()

          When("Cozy assembles and probes the final video")
          val out = CozyVideo.build(
            CozyVideo.BuildConfig(
              dir.resolve("video_project.json"),
              dryRun = false,
              checkTools = false
            ),
            CozyVideo.VideoToolRegistry(Vector.empty),
            runner
          )

          Then("ffmpeg and ffprobe use container-visible staging before publishing the result")
          out should include_text("Cozy Video Build")
          out should include_text("output: " + dir.resolve("build/final.mp4").normalize())
          out should include_text("manifest: " + dir.resolve("build/manifest.json").normalize())
          out should include_text("parts: 2")
          runner.commands should have size 2
          runner.commands(0).args.take(8) shouldBe Vector(
            "docker",
            "run",
            "--rm",
            "-v",
            s"$dir:/workspace",
            "-w",
            "/workspace",
            "ghcr.io/asami/textus-toolchain:latest"
          )
          runner.commands(0).args should contain("ffmpeg")
          runner.commands(0).args should contain("/workspace/target/cozy-video/ffmpeg/concat.txt")
          runner.commands(0).args should contain("/workspace/target/cozy-video/ffmpeg/rendered.mp4")
          runner.commands(1).args should contain("ffprobe")
          runner.commands(1).args should contain("/workspace/target/cozy-video/ffmpeg/rendered.mp4")
          val concat = _read(dir.resolve("target/cozy-video/ffmpeg/concat.txt"))
          concat should include_text("file '/workspace/target/cozy-video/ffmpeg/parts/part-01.mp4'")
          concat should include_text("file '/workspace/target/cozy-video/ffmpeg/parts/part-02.mp4'")
          dir.resolve("build/final.mp4") should be_regular_file
          val manifest = _read(dir.resolve("build/manifest.json"))
          parser.parse(manifest).toOption.flatMap(_.hcursor.get[String]("status").toOption) shouldBe Some("validated")
          manifest should include_text("\"outputPath\"")
          manifest should include_text("\"finalVideoSha256\"")
          manifest should include_text("\"partOutputs\"")
          manifest should include_text("\"concatListPath\"")
          manifest should include_text("\"ffprobe\"")
        }
      }

      "video build can assemble rendered parts in host mode" in {
        _with_temp_dir("cozy-video-build-final-host") { dir =>
          _write(dir.resolve("script.json"), _script_json)
          _write_bytes(
            dir.resolve("build/parts/intro.mp4"),
            Array[Byte](1, 2, 3)
          )
          _write(
            dir.resolve("video_project.json"),
            _project_json("script.json")
          )
          val runner = AssemblyRunner()

          val out = CozyVideo.build(
            CozyVideo.BuildConfig(
              dir.resolve("video_project.json"),
              dryRun = false,
              checkTools = false,
              toolMode = Some("host")
            ),
            CozyVideo.VideoToolRegistry(Vector.empty),
            runner
          )

          ((out.contains("toolMode: host")) shouldBe true)
          ((runner.commands.size == 2) shouldBe true)
          ((runner.commands(0).args.head == "ffmpeg") shouldBe true)
          ((runner.commands(1).args.head == "ffprobe") shouldBe true)
          ((!runner.commands.exists(_.args.head == "docker")) shouldBe true)
          ((_read(dir.resolve("target/cozy-video/ffmpeg/concat.txt")).contains(
            "file '" + dir.resolve("build/parts/intro.mp4").normalize() + "'"
          )) shouldBe true)
          ((Files.isRegularFile(
            dir.resolve("build/manifest.json")
          )) shouldBe true)
        }
      }

      "cozy video build CLI dispatch assembles rendered parts through injected runner" in {
        _with_temp_dir("cozy-video-build-final-cli") { dir =>
          _write(dir.resolve("script.json"), _script_json)
          _write_bytes(
            dir.resolve("build/parts/intro.mp4"),
            Array[Byte](1, 2, 3)
          )
          _write(
            dir.resolve("video_project.json"),
            _project_json("script.json")
          )
          val runner = AssemblyRunner()

          val out = _capture {
            ((CozyVideo.execute(
              List(
                "video",
                "build",
                dir.resolve("video_project.json").toString,
                "--tool-mode=host"
              ),
              CozyVideo.VideoToolRegistry(Vector.empty),
              RecordingVoicevoxClient(),
              runner
            )) shouldBe true)
          }

          ((out.contains("Cozy Video Build")) shouldBe true)
          ((runner.commands.size == 2) shouldBe true)
          ((runner.commands(0).args.head == "ffmpeg") shouldBe true)
          ((runner.commands(1).args.head == "ffprobe") shouldBe true)
          ((Files.isRegularFile(dir.resolve("build/final.mp4"))) shouldBe true)
          ((Files.isRegularFile(
            dir.resolve("build/manifest.json")
          )) shouldBe true)
        }
      }

      "video build reports missing part outputs runner failures and tool checks" in {
        _with_temp_dir("cozy-video-build-final-errors") { dir =>
          _write(dir.resolve("script.json"), _script_json)
          _write(
            dir.resolve("video_project.json"),
            _project_json("script.json")
          )

          val missingoutput = intercept[Throwable] {
            CozyVideo.build(
              CozyVideo.BuildConfig(
                dir.resolve("video_project.json"),
                dryRun = false,
                checkTools = false
              ),
              CozyVideo.VideoToolRegistry(Vector.empty),
              AssemblyRunner()
            )
          }
          ((missingoutput.getMessage
            .contains("Missing rendered part output")) shouldBe true)
          ((missingoutput.getMessage
            .contains("cozy video render")) shouldBe true)

          _write_bytes(
            dir.resolve("build/parts/intro.mp4"),
            Array[Byte](1, 2, 3)
          )
          val ffmpegfailure = intercept[Throwable] {
            CozyVideo.build(
              CozyVideo.BuildConfig(
                dir.resolve("video_project.json"),
                dryRun = false,
                checkTools = false
              ),
              CozyVideo.VideoToolRegistry(Vector.empty),
              AssemblyRunner(failTool = Some("ffmpeg"))
            )
          }
          ((ffmpegfailure.getMessage
            .contains("ffmpeg concat/mux failed")) shouldBe true)

          val ffprobefailure = intercept[Throwable] {
            CozyVideo.build(
              CozyVideo.BuildConfig(
                dir.resolve("video_project.json"),
                dryRun = false,
                checkTools = false
              ),
              CozyVideo.VideoToolRegistry(Vector.empty),
              AssemblyRunner(failTool = Some("ffprobe"))
            )
          }
          ((ffprobefailure.getMessage
            .contains("ffprobe validation failed")) shouldBe true)

          val invalidprobe = intercept[Throwable] {
            CozyVideo.build(
              CozyVideo.BuildConfig(
                dir.resolve("video_project.json"),
                dryRun = false,
                checkTools = false
              ),
              CozyVideo.VideoToolRegistry(Vector.empty),
              AssemblyRunner(invalidProbeJson = true)
            )
          }
          ((invalidprobe.getMessage
            .contains("ffprobe returned invalid JSON")) shouldBe true)

          val missingtool = intercept[Throwable] {
            CozyVideo.build(
              CozyVideo.BuildConfig(
                dir.resolve("video_project.json"),
                dryRun = false,
                checkTools = true,
                toolMode = Some("host")
              ),
              CozyVideo.VideoToolRegistry(
                Vector(
                  StubProvider(
                    CozyVideo.VideoToolCheck(
                      "ffmpeg",
                      CozyVideo.VideoToolMode.Host,
                      CozyVideo.VideoToolStatus.Missing,
                      "missing ffmpeg",
                      Some("install ffmpeg")
                    )
                  )
                )
              ),
              AssemblyRunner()
            )
          }
          ((missingtool.getMessage.contains("ffmpeg is missing")) shouldBe true)
          ((missingtool.getMessage.contains("install ffmpeg")) shouldBe true)
        }
      }

      "video build fails explicitly for unknown options" in {
        _with_temp_dir("cozy-video-build-unknown-option") { dir =>
          _write(dir.resolve("script.json"), _script_json)
          _write(
            dir.resolve("video_project.json"),
            _project_json("script.json")
          )
          val e = intercept[Throwable] {
            CozyVideo.execute(
              List(
                "video",
                "build",
                dir.resolve("video_project.json").toString,
                "--dry-ran"
              ),
              CozyVideo.VideoToolRegistry(Vector.empty)
            )
          }

          ((e.getMessage.contains("dry-ran")) shouldBe true)
        }
      }

    }

    "rdf generation" which {
      "video rdf generates Turtle JSON-LD and manifest from video artifacts" in {
        _with_temp_dir("cozy-video-rdf") { dir =>
          _write(dir.resolve("script.json"), _script_json)
          _write(
            dir.resolve("video_project.json"),
            _project_json("script.json")
          )
          _write_audio_manifest(
            dir.resolve("build/audio/intro"),
            Vector("title", "description", "summary")
          )
          _write(
            dir.resolve("build/audio/intro/manifest.json"),
            _audio_manifest_json(Vector("title", "description", "summary"))
              .replace("\"speaker\":null", "\"speaker\":\"narrator\"")
              .replace(
                "\"tailSilence\":0.0}",
                "\"tailSilence\":0.0,\"provider\":\"voicevox\",\"executionMode\":\"external-http\",\"voiceIdentity\":\"narrator/default\",\"voiceId\":\"3\",\"modelIdentity\":\"voicevox-model\",\"sampleRate\":24000,\"channels\":1,\"bitsPerSample\":16}"
              )
          )
          _write(
            dir.resolve("build/parts/intro.manifest.json"),
            s"""{
           |  "partId": "intro",
           |  "renderer": "simple-java2d",
           |  "outputPath": "${dir
                .resolve("build/parts/intro.mp4")
                .normalize()}",
           |  "toolMode": "docker",
           |  "dockerImage": "ghcr.io/asami/textus-toolchain:latest"
           |}
           |""".stripMargin
          )
          _write(
            dir.resolve("build/manifest.json"),
            s"""{
           |  "outputPath": "${dir.resolve("build/final.mp4").normalize()}",
           |  "toolMode": "docker",
           |  "dockerImage": "ghcr.io/asami/textus-toolchain:latest",
           |  "concatListPath": "${dir
                .resolve("target/cozy-video/ffmpeg/concat.txt")
                .normalize()}",
           |  "ffprobe": {"format": {"duration": "12.0"}}
           |}
           |""".stripMargin
          )

          val out = CozyVideo.rdf(
            CozyVideo.RdfConfig(
              dir.resolve("video_project.json"),
              dir.resolve("rdf")
            )
          )

          ((out.contains("Cozy Video RDF")) shouldBe true)
          ((out.contains(
            "turtle: " + dir.resolve("rdf/video.ttl").normalize()
          )) shouldBe true)
          ((out.contains(
            "jsonld: " + dir.resolve("rdf/video.jsonld").normalize()
          )) shouldBe true)
          ((Files.isRegularFile(dir.resolve("rdf/video.ttl"))) shouldBe true)
          ((Files.isRegularFile(dir.resolve("rdf/video.jsonld"))) shouldBe true)
          ((Files.isRegularFile(
            dir.resolve("rdf/manifest.json")
          )) shouldBe true)
          val turtle = _read(dir.resolve("rdf/video.ttl"))
          val jsonld = _read(dir.resolve("rdf/video.jsonld"))
          val manifest = _read(dir.resolve("rdf/manifest.json"))
          ((turtle.contains(
            "@prefix cozy-video: <https://www.simplemodeling.org/ns/cozy/video#> ."
          )) shouldBe true)
          ((turtle.contains("cozy-video:VideoProject")) shouldBe true)
          ((turtle.contains("cozy-video:VideoPart")) shouldBe true)
          ((turtle.contains("cozy-video:VideoScene")) shouldBe true)
          ((turtle.contains("cozy-video:VideoUtterance")) shouldBe true)
          ((turtle.contains("cozy-video:VideoArtifact")) shouldBe true)
          ((turtle.contains("cozy-video:speaker")) shouldBe true)
          ((turtle.contains("cozy-video:audioDuration")) shouldBe true)
          ((turtle.contains("cozy-video:narrationProvider")) shouldBe true)
          ((turtle.contains("voicevox-model")) shouldBe true)
          ((turtle.contains("cozy-video:sampleRate")) shouldBe true)
          ((turtle.contains("simple-java2d")) shouldBe true)
          ((turtle.contains("ffprobe")) shouldBe true)
          ((jsonld.contains("\"cozy-video\"")) shouldBe true)
          ((jsonld.contains("cozy-video:VideoProject")) shouldBe true)
          ((manifest.contains("\"tripleCount\"")) shouldBe true)
          ((manifest.contains("\"resourceCount\"")) shouldBe true)
        }
      }

      "video rdf records missing manifests without failing" in {
        _with_temp_dir("cozy-video-rdf-missing-manifests") { dir =>
          _write(dir.resolve("script.json"), _script_json)
          _write(
            dir.resolve("video_project.json"),
            _project_json("script.json")
          )

          CozyVideo.rdf(
            CozyVideo.RdfConfig(
              dir.resolve("video_project.json"),
              dir.resolve("rdf")
            )
          )

          val turtle = _read(dir.resolve("rdf/video.ttl"))
          ((turtle.contains(
            "cozy-video:artifactKind \"audio-manifest\""
          )) shouldBe true)
          ((turtle.contains(
            "cozy-video:artifactKind \"part-manifest\""
          )) shouldBe true)
          ((turtle.contains(
            "cozy-video:artifactKind \"project-manifest\""
          )) shouldBe true)
          ((turtle.contains("cozy-video:status \"missing\"")) shouldBe true)
          ((turtle.contains("cozy-video:VideoScene")) shouldBe true)
        }
      }

      "video rdf percent-encodes resource ids for Turtle-safe output" in {
        _with_temp_dir("cozy-video-rdf-unsafe-ids") { dir =>
          _write(
            dir.resolve("script.json"),
            """{
          |  "title": "Unsafe Id Script",
          |  "scenes": [
          |    {"id": "scene #1", "duration": 1.0, "line": "Hello"}
          |  ]
          |}
          |""".stripMargin
          )
          _write(
            dir.resolve("video_project.json"),
            """{
          |  "name": "Sample Video 2026",
          |  "title": "Sample Video",
          |  "parts": [
          |    {"id": "intro slide", "type": "dialogue", "script": "script.json"}
          |  ]
          |}
          |""".stripMargin
          )

          CozyVideo.rdf(
            CozyVideo.RdfConfig(
              dir.resolve("video_project.json"),
              dir.resolve("rdf")
            )
          )

          val turtle = _read(dir.resolve("rdf/video.ttl"))
          val jsonld = _read(dir.resolve("rdf/video.jsonld"))
          ((turtle.contains(
            "cozy-video:project/Sample%20Video%202026"
          )) shouldBe true)
          ((turtle.contains("cozy-video:part/intro%20slide")) shouldBe true)
          ((turtle.contains(
            "cozy-video:scene/intro%20slide-scene%20%231"
          )) shouldBe true)
          ((jsonld.contains(
            "cozy-video:project/Sample%20Video%202026"
          )) shouldBe true)
          ((jsonld.contains("cozy-video:part/intro%20slide")) shouldBe true)
          ((!turtle.contains("cozy-video:part/intro slide")) shouldBe true)
        }
      }

      "video rdf fails explicitly for invalid manifests inputs and options" in {
        _with_temp_dir("cozy-video-rdf-errors") { dir =>
          _write(dir.resolve("script.json"), _script_json)
          _write(
            dir.resolve("video_project.json"),
            _project_json("script.json")
          )
          _write(dir.resolve("build/audio/intro/manifest.json"), "not json")

          val invalidmanifest = intercept[Throwable] {
            CozyVideo.rdf(
              CozyVideo.RdfConfig(
                dir.resolve("video_project.json"),
                dir.resolve("rdf")
              )
            )
          }
          ((invalidmanifest.getMessage
            .contains("Invalid audio manifest intro JSON")) shouldBe true)

          val missingsave = intercept[Throwable] {
            CozyVideo.execute(
              List("video", "rdf", dir.resolve("video_project.json").toString),
              CozyVideo.VideoToolRegistry(Vector.empty)
            )
          }
          ((missingsave.getMessage.contains("save")) shouldBe true)

          val unknownoption = intercept[Throwable] {
            CozyVideo.execute(
              List(
                "video",
                "rdf",
                dir.resolve("video_project.json").toString,
                "--save",
                dir.resolve("rdf").toString,
                "--unknown"
              ),
              CozyVideo.VideoToolRegistry(Vector.empty)
            )
          }
          ((unknownoption.getMessage.contains("unknown")) shouldBe true)

          val missingproject = intercept[Throwable] {
            CozyVideo.rdf(
              CozyVideo.RdfConfig(
                dir.resolve("missing.json"),
                dir.resolve("rdf")
              )
            )
          }
          ((missingproject.getMessage
            .contains("Missing video project file")) shouldBe true)
        }
      }

    }

    "transcription" which {
      "video transcribe writes transcript captions narration and manifest in docker mode" in {
        _with_temp_dir("cozy-video-transcribe-docker") { dir =>
          val input = dir.resolve("demo.mp4")
          val save = dir.resolve("transcript")
          _write_bytes(input, Array[Byte](1, 2, 3, 4))
          val runner = TranscriptionRunner()

          val out = CozyVideo.transcribe(
            CozyVideo
              .TranscribeConfig(input, save, projectRootOverride = Some(dir)),
            CozyVideo.VideoToolRegistry(Vector.empty),
            runner
          )

          ((out.contains("Cozy Video Transcribe")) shouldBe true)
          ((out.contains("toolMode: docker")) shouldBe true)
          ((out.contains(
            "modelPath: /opt/textus/models/ggml-base.bin"
          )) shouldBe true)
          ((Files.isRegularFile(save.resolve("audio.wav"))) shouldBe true)
          ((Files.isRegularFile(save.resolve("transcript.json"))) shouldBe true)
          ((Files.isRegularFile(save.resolve("captions.srt"))) shouldBe true)
          ((Files.isRegularFile(save.resolve("narration.json"))) shouldBe true)
          ((Files.isRegularFile(save.resolve("manifest.json"))) shouldBe true)
          val transcript = _read(save.resolve("transcript.json"))
          val captions = _read(save.resolve("captions.srt"))
          val narration = _read(save.resolve("narration.json"))
          val manifest = _read(save.resolve("manifest.json"))
          ((transcript.contains("cozy.video.transcript.v1")) shouldBe true)
          ((transcript.contains("Hello world")) shouldBe true)
          ((captions.contains("00:00:00,000 --> 00:00:01,250")) shouldBe true)
          ((narration.contains("cozy.video.narration-draft.v1")) shouldBe true)
          ((manifest.contains("inputSha256")) shouldBe true)
          ((manifest.contains("whisper-cli 1.7.6")) shouldBe true)
          ((runner.commands.head.args.take(8) == Vector(
            "docker",
            "run",
            "--rm",
            "-v",
            s"${dir.toAbsolutePath.normalize}:/workspace",
            "-w",
            "/workspace",
            "ghcr.io/asami/textus-toolchain:latest"
          )) shouldBe true)
          ((runner.commands.exists(
            _.args.contains("/opt/textus/models/ggml-base.bin")
          )) shouldBe true)
        }
      }

      "video transcribe resolves config defaults and CLI whisper model in host mode" in {
        _with_temp_dir("cozy-video-transcribe-config") { dir =>
          val input = dir.resolve("demo.mp4")
          val save = dir.resolve("transcript")
          val configmodel = dir.resolve("models/config.bin")
          val climodel = dir.resolve("models/cli.bin")
          _write_bytes(input, Array[Byte](1, 2, 3))
          _write(configmodel, "model")
          _write(climodel, "model")
          _write(
            dir.resolve("conf/cozy/config.yaml"),
            "video:\n  tool-mode: docker\n  docker-image: conf-image\n"
          )
          _write(
            dir.resolve(".cozy/config.yaml"),
            "video:\n  tool-mode: host\ntools:\n  whisperModel: models/config.bin\n"
          )
          val runner = TranscriptionRunner()

          val out = CozyVideo.transcribe(
            CozyVideo.TranscribeConfig.create(
              List(
                "video",
                "transcribe",
                input.toString,
                s"--save=${save.toString}",
                "--whisper-model=models/cli.bin"
              ).drop(2),
              dir
            ),
            CozyVideo.VideoToolRegistry(Vector.empty),
            runner
          )
          ((out.contains("Cozy Video Transcribe")) shouldBe true)

          ((runner.commands
            .exists(_.args.headOption.contains("ffmpeg"))) shouldBe true)
          ((!runner.commands.exists(
            _.args.headOption.contains("docker")
          )) shouldBe true)
          ((runner.commands.exists(
            _.args.contains(climodel.normalize().toString)
          )) shouldBe true)
          ((Files.isRegularFile(save.resolve("manifest.json"))) shouldBe true)
        }
      }

      "video transcribe validates inputs options and tool checks" in {
        _with_temp_dir("cozy-video-transcribe-failures") { dir =>
          val input = dir.resolve("demo.mp4")
          val save = dir.resolve("transcript")
          val model = dir.resolve("models/model.bin")
          _write_bytes(input, Array[Byte](1, 2, 3))
          _write(model, "model")

          val missinginput = intercept[RuntimeException] {
            CozyVideo.transcribe(
              CozyVideo.TranscribeConfig(dir.resolve("missing.mp4"), save),
              CozyVideo.VideoToolRegistry(Vector.empty),
              TranscriptionRunner()
            )
          }
          ((missinginput.getMessage
            .contains("Missing input video")) shouldBe true)

          val missingsave = intercept[RuntimeException] {
            CozyVideo.execute(
              List("video", "transcribe", input.toString),
              CozyVideo.VideoToolRegistry(Vector.empty)
            )
          }
          ((missingsave.getMessage.contains("save")) shouldBe true)

          val unknown = intercept[RuntimeException] {
            CozyVideo.execute(
              List(
                "video",
                "transcribe",
                input.toString,
                "--save",
                save.toString,
                "--unknown"
              ),
              CozyVideo.VideoToolRegistry(Vector.empty)
            )
          }
          ((unknown.getMessage.contains("Unknown option")) shouldBe true)

          val invalidmode = intercept[RuntimeException] {
            CozyVideo.transcribe(
              CozyVideo
                .TranscribeConfig(input, save, toolMode = Some("remote")),
              CozyVideo.VideoToolRegistry(Vector.empty),
              TranscriptionRunner()
            )
          }
          ((invalidmode.getMessage
            .contains("Invalid video tool mode")) shouldBe true)

          val dockercheck = intercept[RuntimeException] {
            CozyVideo.transcribe(
              CozyVideo.TranscribeConfig(input, save, checkTools = true),
              CozyVideo.VideoToolRegistry(
                Vector(
                  StubProvider(
                    CozyVideo.VideoToolCheck(
                      "docker-image",
                      CozyVideo.VideoToolMode.Docker,
                      CozyVideo.VideoToolStatus.Missing,
                      "missing image",
                      Some("pull image")
                    )
                  )
                )
              ),
              TranscriptionRunner()
            )
          }
          ((dockercheck.getMessage.contains("docker-image")) shouldBe true)
          ((dockercheck.getMessage.contains("pull image")) shouldBe true)

          val hostcheck = intercept[RuntimeException] {
            CozyVideo.transcribe(
              CozyVideo.TranscribeConfig(
                input,
                save,
                checkTools = true,
                toolMode = Some("host"),
                whisperModel = Some(model.toString)
              ),
              CozyVideo.VideoToolRegistry(
                Vector(
                  StubProvider(
                    CozyVideo.VideoToolCheck(
                      "whisper-cpp",
                      CozyVideo.VideoToolMode.Host,
                      CozyVideo.VideoToolStatus.Missing,
                      "missing whisper",
                      Some("install whisper")
                    )
                  )
                )
              ),
              TranscriptionRunner()
            )
          }
          ((hostcheck.getMessage.contains("whisper-cpp")) shouldBe true)
          ((hostcheck.getMessage.contains("install whisper")) shouldBe true)

          val ffmpegfailure = intercept[RuntimeException] {
            CozyVideo.transcribe(
              CozyVideo.TranscribeConfig(
                input,
                save,
                toolMode = Some("host"),
                whisperModel = Some(model.toString)
              ),
              CozyVideo.VideoToolRegistry(Vector.empty),
              TranscriptionRunner(failTool = Some("ffmpeg"))
            )
          }
          ((ffmpegfailure.getMessage
            .contains("ffmpeg audio extraction failed")) shouldBe true)

          val whisperfailure = intercept[RuntimeException] {
            CozyVideo.transcribe(
              CozyVideo.TranscribeConfig(
                input,
                save,
                toolMode = Some("host"),
                whisperModel = Some(model.toString)
              ),
              CozyVideo.VideoToolRegistry(Vector.empty),
              TranscriptionRunner(failTool = Some("whisper-cli"))
            )
          }
          ((whisperfailure.getMessage
            .contains("whisper.cpp transcription failed")) shouldBe true)

          val projectroot = dir.resolve("project-root")
          val projectinput = projectroot.resolve("demo.mp4")
          val projectsave = projectroot.resolve("transcript")
          val outsideinput = dir.resolve("outside/demo.mp4")
          val outsidesave = dir.resolve("outside-transcript")
          _write_bytes(projectinput, Array[Byte](1, 2, 3))
          _write_bytes(outsideinput, Array[Byte](1, 2, 3))
          val inputrunner = TranscriptionRunner()
          val dockerinput = intercept[RuntimeException] {
            CozyVideo.transcribe(
              CozyVideo.TranscribeConfig(
                outsideinput,
                projectsave,
                projectRootOverride = Some(projectroot)
              ),
              CozyVideo.VideoToolRegistry(Vector.empty),
              inputrunner
            )
          }
          ((dockerinput.getMessage.contains(
            "Docker transcription requires input video under project root"
          )) shouldBe true)
          ((inputrunner.commands.isEmpty) shouldBe true)
          val saverunner = TranscriptionRunner()
          val dockersave = intercept[RuntimeException] {
            CozyVideo.transcribe(
              CozyVideo.TranscribeConfig(
                projectinput,
                outsidesave,
                projectRootOverride = Some(projectroot)
              ),
              CozyVideo.VideoToolRegistry(Vector.empty),
              saverunner
            )
          }
          ((dockersave.getMessage.contains(
            "Docker transcription requires --save under project root"
          )) shouldBe true)
          ((saverunner.commands.isEmpty) shouldBe true)
        }
      }

    }

    "voice synthesis" which {
      "shared video pronunciation dictionary provides the canonical Cozy readings" in {
        Given("the pronunciation dictionary bundled with Cozy")

        When("the initial shared terms are converted for speech synthesis")
        val spoken = CozyVideoPronunciations.default.applyTo("値とBoK")

        Then("the Japanese and BoK readings follow the shared contract")
        ((spoken == "あたいとボック") shouldBe true)
      }

      "shared video pronunciation dictionary applies longest matches without converting readings again" in {
        Given("overlapping script readings and a reading that is itself another source term")
        val overrides = Map(
          "値型" -> "かた",
          "あたい" -> "バリュー"
        )

        When("Cozy converts the original narration in one pass")
        val spoken = CozyVideoPronunciations.default.applyTo("値型の値", overrides)

        Then("the longest original match wins and generated readings are not converted again")
        ((spoken == "かたのあたい") shouldBe true)
      }

      "video synthesize writes scene wavs combined wav and manifest through VOICEVOX client" in {
        _with_temp_dir("cozy-video-synthesize") { dir =>
          val script = dir.resolve("script.json")
          val outdir = dir.resolve("audio")
          _write(script, _voicevox_script_json)
          val voicevox = RecordingVoicevoxClient(
            speakersJson = Json.arr(
              Json.obj(
                "name" -> Json.fromString("Character Voice"),
                "styles" -> Json.arr(
                  Json.obj(
                    "name" -> Json.fromString("Normal"),
                    "id" -> Json.fromInt(10)
                  )
                )
              ),
              Json.obj(
                "name" -> Json.fromString("Top Voice"),
                "styles" -> Json.arr(
                  Json.obj(
                    "name" -> Json.fromString("Plain"),
                    "id" -> Json.fromInt(20)
                  )
                )
              )
            )
          )

          val result = CozyVideo.synthesize(
            CozyVideo.SynthesizeConfig(
              script,
              outdir,
              Some("http://voicevox.example")
            ),
            voicevox
          )
          val manifest = parser
            .parse(
              Files.readString(
                outdir.resolve("manifest.json"),
                StandardCharsets.UTF_8
              )
            )
            .toOption
            .flatMap(_.asArray)
            .get

          ((result.contains("Cozy Video Synthesize")) shouldBe true)
          ((result.contains("scenes: 3")) shouldBe true)
          ((Files.isRegularFile(outdir.resolve("01-intro.wav"))) shouldBe true)
          ((Files.isRegularFile(
            outdir.resolve("01-intro-lead.wav")
          )) shouldBe true)
          ((Files.isRegularFile(
            outdir.resolve("01-intro-silence.wav")
          )) shouldBe true)
          ((Files.isRegularFile(
            outdir.resolve("02-fallback.wav")
          )) shouldBe true)
          ((Files.isRegularFile(outdir.resolve("03-silent.wav"))) shouldBe true)
          ((Files.isRegularFile(outdir.resolve("script.wav"))) shouldBe true)
          ((voicevox.calls.map(_.kind) == Vector(
            "speakers",
            "audio_query",
            "synthesis",
            "speakers",
            "audio_query",
            "synthesis"
          )) shouldBe true)
          ((voicevox.calls.collect {
            case c if c.kind == "audio_query" => c.text
          } == Vector(Some("HelloCozy"), Some("TopLine"))) shouldBe true)
          ((voicevox.calls.collect {
            case c if c.kind == "audio_query" => c.speakerId
          } == Vector(Some(10), Some(99))) shouldBe true)
          ((voicevox.audioQueries.exists(
            _.hcursor.downField("speedScale").as[Double].toOption.contains(1.2)
          )) shouldBe true)
          ((voicevox.audioQueries.exists(
            _.hcursor.downField("volumeScale").as[Double].toOption.contains(0.8)
          )) shouldBe true)
          ((manifest.size == 3) shouldBe true)
          ((manifest.head.hcursor
            .downField("sceneId")
            .as[String]
            .toOption
            .contains("intro")) shouldBe true)
          ((manifest.head.hcursor
            .downField("leadSilence")
            .as[Double]
            .toOption
            .contains(0.1)) shouldBe true)
          ((manifest(2).hcursor
            .downField("sceneId")
            .as[String]
            .toOption
            .contains("silent")) shouldBe true)
        }
      }

      "video synthesize resolves voicevox url from script tools and config" in {
        _with_temp_dir("cozy-video-synthesize-url") { dir =>
          val voicevox = RecordingVoicevoxClient()
          _write(
            dir.resolve("script-tools.json"),
            """{"tools": {"voicevoxUrl": "http://script.example"}, "voice":{"fallbackSpeakerId":99}, "scenes": [{"id": "s1", "duration": 0.2, "line": "A"}]}"""
          )
          _write(
            dir.resolve("conf/cozy/config.yaml"),
            "video:\n  voicevox:\n    url: http://config.example\n"
          )

          CozyVideo.synthesize(
            CozyVideo.SynthesizeConfig(
              dir.resolve("script-tools.json"),
              dir.resolve("audio1")
            ),
            voicevox
          )
          ((voicevox.calls.head.baseUrl == "http://script.example") shouldBe true)

          val configvoicevox = RecordingVoicevoxClient()
          _write(
            dir.resolve("script-config.json"),
            """{"voice":{"fallbackSpeakerId":99}, "scenes": [{"id": "s1", "duration": 0.2, "line": "A"}]}"""
          )
          CozyVideo.synthesize(
            CozyVideo.SynthesizeConfig(
              dir.resolve("script-config.json"),
              dir.resolve("audio2")
            ),
            configvoicevox
          )
          ((configvoicevox.calls.head.baseUrl == "http://config.example") shouldBe true)
        }
      }

      "video synthesize applies shared readings and lets script readings override them" in {
        _with_temp_dir("cozy-video-shared-pronunciations") { dir =>
          Given("a script containing shared terms and a script-specific reading")
          val script = dir.resolve("script.json")
          _write(
            script,
            """{
              |  "voice": {"fallbackSpeakerId": 99},
              |  "pronunciations": {"BoK": "ビーオーケー"},
              |  "scenes": [
              |    {"id": "terms", "duration": 0.2, "line": "値とBoK"}
              |  ]
              |}
              |""".stripMargin
          )
          val voicevox = RecordingVoicevoxClient()

          When("Cozy sends the spoken text to VOICEVOX")
          CozyVideo.synthesize(
            CozyVideo.SynthesizeConfig(script, dir.resolve("audio")),
            voicevox
          )

          Then("the common reading is applied and the script override takes priority")
          ((voicevox.calls.collect {
            case call if call.kind == "audio_query" => call.text
          } == Vector(Some("あたいとビーオーケー"))) shouldBe true)
        }
      }

      "video synthesize reports an unavailable selected VOICEVOX service before output" in {
        _with_temp_dir("cozy-video-synthesize-voicevox-preflight") { dir =>
          Given("a VOICEVOX script and a missing selected service dependency")
          val script = dir.resolve("script.json")
          val outdir = dir.resolve("audio")
          _write(script, _voicevox_script_json)
          val voicevox = RecordingVoicevoxClient()
          val tools = CozyVideo.VideoToolRegistry(Vector(
            StubProvider(
              CozyVideo.VideoToolCheck(
                "voicevox",
                CozyVideo.VideoToolMode.ExternalService,
                CozyVideo.VideoToolStatus.Missing,
                "VOICEVOX endpoint is not reachable: http://voicevox.example/version (connection refused)",
                Some("Start VOICEVOX Engine.")
              )
            )
          ))

          When("Cozy preflights synthesis with --check-tools")
          val fault = intercept[FaultException] {
            CozyVideo.synthesize(
              CozyVideo.SynthesizeConfig(
                script,
                outdir,
                Some("http://voicevox.example"),
                checkTools = true
              ),
              tools,
              voicevox
            )
          }

          Then("the external service fault preserves evidence without client calls or output")
          fault.getMessage should include_text("External service connection unavailable")
          fault.getMessage should include_text("voicevox")
          fault.getMessage should include_text("http://voicevox.example")
          fault.getMessage should include_text("connection refused")
          fault.getMessage should include_text("Start VOICEVOX Engine")
          voicevox.calls shouldBe empty
          Files.exists(outdir) shouldBe false
        }
      }

      "video synthesize maps a low-level VOICEVOX client failure to an external service fault" in {
        _with_temp_dir("cozy-video-synthesize-voicevox-client-failure") { dir =>
          Given("a VOICEVOX client whose speakers request fails")
          val script = dir.resolve("script.json")
          val outdir = dir.resolve("audio")
          _write(script, _voicevox_script_json)
          val voicevox = RecordingVoicevoxClient(failSpeakers = true)

          When("Cozy resolves the selected VOICEVOX speaker")
          val fault = intercept[FaultException] {
            CozyVideo.synthesize(
              CozyVideo.SynthesizeConfig(script, outdir, Some("http://voicevox.example")),
              voicevox
            )
          }

          Then("the failure remains explicit as an external service connection fault")
          fault.getMessage should include_text("External service connection unavailable")
          fault.getMessage should include_text("voicevox")
          fault.getMessage should include_text("http://voicevox.example")
          fault.getMessage should include_text("VOICEVOX speakers failed")
          fault.getMessage should include_text("Start VOICEVOX Engine")
          voicevox.calls.map(_.kind) shouldBe Vector("speakers")
          val files = Files.list(outdir)
          try files.iterator().asScala.toVector shouldBe empty
          finally files.close()
        }
      }

      "video synthesize fails explicitly for missing script save option and voicevox errors" in {
        _with_temp_dir("cozy-video-synthesize-errors") { dir =>
          Given("a VOICEVOX script with a selected speaker")
          _write(
            dir.resolve("script.json"),
            """{"voice":{"speakerName":"Missing Voice","styleName":"Missing Style"}, "scenes": [{"id": "s1", "duration": 0.2, "line": "A"}]}"""
          )

          When("Cozy receives a synthesize command without --save")
          val missingsave = intercept[Throwable] {
            CozyVideo.execute(
              List("video", "synthesize", dir.resolve("script.json").toString),
              CozyVideo.VideoToolRegistry(Vector.empty),
              RecordingVoicevoxClient()
            )
          }
          Then("the command reports the missing output option")
          missingsave.getMessage should include_text("Missing --save")

          When("Cozy loads a missing synthesis script")
          val missingfile = intercept[Throwable] {
            CozyVideo.synthesize(
              CozyVideo.SynthesizeConfig(
                dir.resolve("missing.json"),
                dir.resolve("audio")
              ),
              RecordingVoicevoxClient()
            )
          }
          Then("the missing script is reported")
          missingfile.getMessage should include_text("Missing video script file")

          When("the selected VOICEVOX client cannot resolve speakers")
          val voicevoxfailure = intercept[Throwable] {
            CozyVideo.synthesize(
              CozyVideo.SynthesizeConfig(
                dir.resolve("script.json"),
                dir.resolve("audio")
              ),
              RecordingVoicevoxClient(failSpeakers = true)
            )
          }
          Then("the underlying client failure is classified as an external service connection")
          voicevoxfailure.getMessage should include_text("External service connection unavailable")
          voicevoxfailure.getMessage should include_text("voicevox")
          voicevoxfailure.getMessage should include_text("http://127.0.0.1:50021")
          voicevoxfailure.getMessage should include_text("VOICEVOX speakers failed")

          Given("a script with an unsafe scene identifier")
          _write(
            dir.resolve("unsafe-scene.json"),
            """{"voice":{"fallbackSpeakerId":99}, "scenes": [{"id": "../escape", "duration": 0.2, "line": "A"}]}"""
          )
          When("Cozy validates the unsafe scene output path")
          val unsafescene = intercept[Throwable] {
            CozyVideo.synthesize(
              CozyVideo.SynthesizeConfig(
                dir.resolve("unsafe-scene.json"),
                dir.resolve("audio-unsafe")
              ),
              RecordingVoicevoxClient()
            )
          }
          Then("the unsafe scene identifier is rejected")
          unsafescene.getMessage should include_text("Invalid scene id")

          When("the selected VOICEVOX endpoint is invalid")
          val invalidurl = intercept[Throwable] {
            CozyVideo.synthesize(
              CozyVideo.SynthesizeConfig(
                dir.resolve("script.json"),
                dir.resolve("audio-invalid-url"),
                Some("://bad")
              ),
              CozyVideo.VoicevoxClient.default
            )
          }
          Then("the invalid endpoint is classified as an external service connection")
          invalidurl.getMessage should include_text("External service connection unavailable")
          invalidurl.getMessage should include_text("voicevox")
          invalidurl.getMessage should include_text("://bad")
        }
      }

      "video synthesize classifies missing selected execution-provider checks as subsystem failures" in {
        _with_temp_dir("cozy-video-synthesize-execution-provider-preflight") { dir =>
          Given("piper and macOS say scripts with an unrelated registered tool check")
          val piper = dir.resolve("piper.json")
          val macossay = dir.resolve("macos-say.json")
          _write(
            piper,
            """{"narration":{"provider":"piper"}, "voice":{"fallbackSpeakerId":99}, "scenes":[{"id":"s1","duration":0.2,"line":"A"}]}"""
          )
          _write(
            macossay,
            """{"narration":{"provider":"macos-say"}, "voice":{"fallbackSpeakerId":99}, "scenes":[{"id":"s1","duration":0.2,"line":"A"}]}"""
          )
          val tools = CozyVideo.VideoToolRegistry(Vector(
            StubProvider(
              CozyVideo.VideoToolCheck(
                "unrelated",
                CozyVideo.VideoToolMode.Host,
                CozyVideo.VideoToolStatus.Available,
                "unrelated tool is available."
              )
            )
          ))
          val piperrunner = RecordingRunner()

          When("Cozy preflights the selected Docker piper provider")
          val piperfault = intercept[FaultException] {
            CozyVideo.synthesize(
              CozyVideo.SynthesizeConfig(
                piper,
                dir.resolve("piper-audio"),
                checkTools = true
              ),
              tools,
              RecordingVoicevoxClient(),
              piperrunner
            )
          }

          Then("the missing piper check is a subsystem dependency failure before execution")
          piperfault.getMessage should include_text("Required execution dependency unavailable")
          piperfault.getMessage should include_text("dependency=piper")
          piperfault.getMessage should include_text("No tool check is registered")
          piperrunner.commands shouldBe empty
          Files.exists(dir.resolve("piper-audio")) shouldBe false

          Given("a fresh runner for the selected host macOS say provider")
          val macossayrunner = RecordingRunner()
          When("Cozy preflights the selected host macOS say provider")
          val macossayfault = intercept[FaultException] {
            CozyVideo.synthesize(
              CozyVideo.SynthesizeConfig(
                macossay,
                dir.resolve("macos-say-audio"),
                checkTools = true,
                toolMode = Some("host")
              ),
              tools,
              RecordingVoicevoxClient(),
              macossayrunner
            )
          }

          Then("the missing macOS say check is a subsystem dependency failure before execution")
          macossayfault.getMessage should include_text("Required execution dependency unavailable")
          macossayfault.getMessage should include_text("dependency=macos-say")
          macossayfault.getMessage should include_text("No tool check is registered")
          macossayrunner.commands shouldBe empty
          Files.exists(dir.resolve("macos-say-audio")) shouldBe false
        }
      }

    }

    "part rendering" which {
      "video render remotion renders all renderable parts through a runner" in {
        Given("a renderable dialogue and storyboard video project")
        _with_temp_dir("cozy-video-render-remotion") { dir =>
          _write(dir.resolve("dialogue.json"), _script_json)
          _write(dir.resolve("storyboard.json"), _script_json)
          _write_audio_manifest(
            dir.resolve("build/audio/lecture"),
            Vector("title", "description", "summary")
          )
          _write_audio_manifest(
            dir.resolve("build/audio/board"),
            Vector("title", "description", "summary")
          )
          _write(
            dir.resolve("video_project.json"),
            s"""{
           |  "title": "Render Video",
           |  "renderer": {"engine": "remotion"},
           |  "parts": [
           |    {"id": "lecture", "type": "dialogue", "script": "dialogue.json", "output": "build/parts/lecture.mp4"},
           |    {"id": "board", "type": "storyboard", "script": "storyboard.json", "output": "build/parts/board.mp4"},
           |    {"id": "future", "type": "future-kind", "script": "dialogue.json"}
           |  ]
           |}
           |""".stripMargin
          )
          val runner = ProfileRenderRunner()

          When("the project is rendered through the Remotion runner")
          val out = CozyVideo.render(
            CozyVideo
              .RenderConfig(dir.resolve("video_project.json"), "remotion"),
            CozyVideo.VideoToolRegistry(Vector.empty),
            runner
          )

          Then("the runner receives lightweight default properties and CRF")
          ((out.contains("Cozy Video Render")) shouldBe true)
          ((out.contains("parts: 2")) shouldBe true)
          ((out.contains(
            "part.lecture: " + dir
              .resolve("build/parts/lecture.mp4")
              .normalize()
          )) shouldBe true)
          ((out.contains(
            "part.board: " + dir.resolve("build/parts/board.mp4").normalize()
          )) shouldBe true)
          ((runner.commands.size == 2) shouldBe true)
          ((runner.commands.head.args.take(8) == Vector(
            "docker",
            "run",
            "--rm",
            "-v",
            s"$dir:/workspace",
            "-w",
            "/workspace",
            "ghcr.io/asami/textus-toolchain:latest"
          )) shouldBe true)
          ((runner.commands.head.args.contains("node")) shouldBe true)
          ((runner.commands.head.args.exists(
            _.endsWith("target/cozy-video/remotion/lecture/src/render.mjs")
          )) shouldBe true)
          ((Files.isRegularFile(
            dir.resolve("target/cozy-video/remotion/lecture/package.json")
          )) shouldBe true)
          ((Files.isRegularFile(
            dir.resolve("target/cozy-video/remotion/lecture/src/Root.tsx")
          )) shouldBe true)
          ((Files.isRegularFile(
            dir.resolve("target/cozy-video/remotion/lecture/src/render.mjs")
          )) shouldBe true)
          ((Files.isRegularFile(
            dir.resolve("target/cozy-video/remotion/lecture/src/props.ts")
          )) shouldBe true)
          ((Files.isRegularFile(
            dir.resolve("target/cozy-video/remotion/lecture/props.json")
          )) shouldBe true)
          val propsjson = parser.parse(Files.readString(
            dir.resolve("target/cozy-video/remotion/lecture/props.json"),
            StandardCharsets.UTF_8
          )).toOption.get
          propsjson.hcursor.get[Int]("fps").toOption shouldBe Some(18)
          propsjson.hcursor.get[Int]("width").toOption shouldBe Some(1280)
          propsjson.hcursor.get[Int]("height").toOption shouldBe Some(720)
          propsjson.hcursor.get[Int]("crf").toOption shouldBe Some(32)
          _read(dir.resolve("target/cozy-video/remotion/lecture/src/render.mjs")) should include_text("`--crf=${props.crf}`")
          ((Files.isRegularFile(
            dir.resolve(
              "target/cozy-video/remotion/lecture/public/audio/01-title.wav"
            )
          )) shouldBe true)
          ((Files.isRegularFile(
            dir.resolve("build/parts/lecture.manifest.json")
          )) shouldBe true)
          ((Files.isRegularFile(
            dir.resolve("build/parts/board.manifest.json")
          )) shouldBe true)
          ((!Files.exists(
            dir.resolve("build/parts/manifest.json")
          )) shouldBe true)
          val root = _read(
            dir.resolve("target/cozy-video/remotion/lecture/src/Root.tsx")
          )
          val render = _read(
            dir.resolve("target/cozy-video/remotion/lecture/src/render.mjs")
          )
          val props = _read(
            dir.resolve("target/cozy-video/remotion/lecture/src/props.ts")
          )
          ((root.contains("cozyVideoProps")) shouldBe true)
          ((root.contains("staticFile(scene.audioPath)")) shouldBe true)
          ((!root.contains("React.FC<Props> = (props)")) shouldBe true)
          ((!render.contains("--props")) shouldBe true)
          ((props.contains("audio/01-title.wav")) shouldBe true)
        }
      }

      "video render remotion projects configured CRF and x264 preset into the generated command" in {
        Given("a Remotion video project with explicit encoding settings")
        _with_temp_dir("cozy-video-render-remotion-explicit-encoding") { dir =>
          _write(dir.resolve("dialogue.json"), _script_json)
          _write_audio_manifest(
            dir.resolve("build/audio/lecture"),
            Vector("title", "description", "summary")
          )
          _write(
            dir.resolve("video_project.json"),
            """{
              |  "renderer": {"engine": "remotion", "crf": 21, "x264Preset": "slow"},
              |  "parts": [
              |    {"id": "lecture", "type": "dialogue", "script": "dialogue.json"}
              |  ]
              |}
              |""".stripMargin
          )

          When("the project is rendered through the Remotion runner")
          CozyVideo.render(
            CozyVideo.RenderConfig(dir.resolve("video_project.json"), "remotion"),
            CozyVideo.VideoToolRegistry(Vector.empty),
            ProfileRenderRunner()
          )

          Then("the generated props and command retain the configured CRF and preset")
          val propsjson = parser.parse(Files.readString(
            dir.resolve("target/cozy-video/remotion/lecture/props.json"),
            StandardCharsets.UTF_8
          )).toOption.get
          val command = _read(dir.resolve("target/cozy-video/remotion/lecture/src/render.mjs"))
          propsjson.hcursor.get[Int]("crf").toOption shouldBe Some(21)
          propsjson.hcursor.get[String]("x264Preset").toOption shouldBe Some("slow")
          command should include_text("`--crf=${props.crf}`")
          command should include_text("`--x264-preset=${props.x264Preset}`")
        }
      }

      "video render remotion can render one selected part in host mode" in {
        _with_temp_dir("cozy-video-render-selected-host") { dir =>
          _write(dir.resolve("dialogue.json"), _script_json)
          _write(dir.resolve("storyboard.json"), _script_json)
          _write_audio_manifest(
            dir.resolve("build/audio/lecture"),
            Vector("title", "description", "summary")
          )
          _write_audio_manifest(
            dir.resolve("build/audio/board"),
            Vector("title", "description", "summary")
          )
          _write(
            dir.resolve("video_project.json"),
            s"""{
           |  "renderer": {"engine": "remotion"},
           |  "parts": [
           |    {"id": "lecture", "type": "dialogue", "script": "dialogue.json"},
           |    {"id": "board", "type": "storyboard", "script": "storyboard.json"}
           |  ]
           |}
           |""".stripMargin
          )
          val runner = ProfileRenderRunner()

          val out = CozyVideo.render(
            CozyVideo.RenderConfig(
              dir.resolve("video_project.json"),
              "remotion",
              part = Some("board"),
              toolMode = Some("host")
            ),
            CozyVideo.VideoToolRegistry(Vector.empty),
            runner
          )

          ((out.contains("toolMode: host")) shouldBe true)
          ((out.contains("parts: 1")) shouldBe true)
          ((out.contains(
            "part.board: " + dir.resolve("build/parts/board.mp4").normalize()
          )) shouldBe true)
          ((runner.commands.size == 1) shouldBe true)
          ((runner.commands.head.args.head == "node") shouldBe true)
          ((runner.commands.head
            .args(1)
            .endsWith(
              "target/cozy-video/remotion/board/src/render.mjs"
            )) shouldBe true)
          ((Files.isRegularFile(
            dir.resolve("build/parts/board.manifest.json")
          )) shouldBe true)
        }
      }

      "video render selects and stages the generic character-dialogue template from declared assets" in {
        _with_temp_dir("cozy-video-character-dialogue") { dir =>
          Given("a dialogue script with caller-owned character and scene assets")
          _write_bytes(dir.resolve("ja/dialogue/assets/presenter-left.png"), Array[Byte](1, 2, 3))
          _write_bytes(dir.resolve("ja/dialogue/assets/presenter-right.png"), Array[Byte](4, 5, 6))
          _write_bytes(dir.resolve("ja/dialogue/assets/scene.png"), Array[Byte](7, 8, 9))
          _write(dir.resolve("ja/dialogue/script.json"),
            """{
              |  "characters": {
              |    "presenter-left": {"asset":"assets/presenter-left.png","mouthClosedAsset":"assets/presenter-left.png","mouthOpenAsset":"assets/presenter-left.png","side":"left"},
              |    "presenter-right": {"asset":"assets/presenter-right.png","side":"right"}
              |  },
              |  "sections": [{"id":"intro","title":"Introduction"}],
              |  "scenes": [{"id":"intro","speaker":"presenter-left","narration":"Narrated dialogue","section":"intro","effects":{"preset":"concept"},"visual":{"kind":"slide","image":"assets/scene.png"}}]
              |}""".stripMargin)
          _write_audio_manifest(dir.resolve("build/audio/lecture"), Vector("intro"))
          _write(dir.resolve("video_project.json"),
            """{"renderer":{"engine":"remotion"},"parts":[{"id":"lecture","type":"dialogue","script":"ja/dialogue/script.json"}]}""")
          val runner = ProfileRenderRunner()

          When("Cozy renders with the fake runner")
          CozyVideo.render(CozyVideo.RenderConfig(dir.resolve("video_project.json"), "remotion", toolMode = Some("host")), CozyVideo.VideoToolRegistry(Vector.empty), runner)

          Then("the vendored renderer, structured props, and only declared staged assets are present")
          val workdir = dir.resolve("target/cozy-video/remotion/lecture")
          val props = parser.parse(_read(workdir.resolve("props.json"))).toOption.get
          props.hcursor.downField("rendererTemplate").as[String].toOption shouldBe Some("cozy-character-dialogue-v1")
          props.hcursor.downField("rendererTemplateSha256").as[String].toOption.getOrElse("").matches("[0-9a-f]{64}") shouldBe true
          props.hcursor.downField("characters").downField("presenter-left").downField("asset").as[String].toOption.getOrElse("") should startWith("characters/")
          props.hcursor.downField("scenes").downArray.downField("visual").downField("image").as[String].toOption.getOrElse("") should startWith("visuals/")
          props.hcursor.downField("scenes").downArray.downField("section").as[String].toOption shouldBe Some("intro")
          props.hcursor.downField("scenes").downArray.downField("effects").downField("preset").as[String].toOption shouldBe Some("concept")
          props.hcursor.downField("scenes").downArray.downField("line").as[String].toOption shouldBe Some("Narrated dialogue")
          props.hcursor.downField("scenes").downArray.downField("caption").as[String].toOption shouldBe Some("Narrated dialogue")
          props.hcursor.downField("scenes").downArray.downField("startFrame").as[Int].toOption shouldBe Some(0)
          Files.isRegularFile(workdir.resolve("src/DialogueVideo.jsx")) shouldBe true
          Files.isRegularFile(workdir.resolve("public/characters/character-presenter-left-asset.png")) shouldBe true
          Files.isRegularFile(workdir.resolve("public/visuals/scene-intro-visual-image.png")) shouldBe true

          And("an explicit generic strategy opts out")
          _write(dir.resolve("video_project.json"),
            """{"renderer":{"engine":"remotion","strategy":"generic"},"parts":[{"id":"lecture","type":"dialogue","script":"ja/dialogue/script.json"}]}""")
          CozyVideo.render(CozyVideo.RenderConfig(dir.resolve("video_project.json"), "remotion", toolMode = Some("host")), CozyVideo.VideoToolRegistry(Vector.empty), ProfileRenderRunner())
          parser.parse(_read(workdir.resolve("props.json"))).toOption.get.hcursor.downField("rendererTemplate").focus shouldBe Some(Json.Null)

          And("a part-level generic strategy also opts out and the generic root has no duplicate overlays")
          _write(dir.resolve("video_project.json"),
            """{"renderer":{"engine":"remotion"},"parts":[{"id":"lecture","type":"dialogue","script":"ja/dialogue/script.json","renderer":{"strategy":"generic"}}]}""")
          CozyVideo.render(CozyVideo.RenderConfig(dir.resolve("video_project.json"), "remotion", toolMode = Some("host")), CozyVideo.VideoToolRegistry(Vector.empty), ProfileRenderRunner())
          parser.parse(_read(workdir.resolve("props.json"))).toOption.get.hcursor.downField("rendererTemplate").focus shouldBe Some(Json.Null)
          val genericroot = _read(workdir.resolve("src/Root.tsx"))
          genericroot should not include ">OVERVIEW<"
          genericroot should not include ">CONCLUSION<"
          genericroot should not include ">END<"
        }
      }

      "video render preserves validated declarative flow and axis diagrams for the character-dialogue renderer" in {
        _with_temp_dir("cozy-video-declarative-diagrams") { dir =>
          Given("a character-dialogue script with a flow scene and an axis scene")
          _write(
            dir.resolve("dialogue/script.json"),
            """{
              |  "characters": {"guide": {"side": "left"}, "reviewer": {"side": "right"}},
              |  "scenes": [
              |    {"id":"flow-scene","speaker":"guide","line":"Flow explanation","caption":"Flow explanation","visual":{"kind":"diagram","heading":"Flow","diagram":{"layout":"flow","nodes":[{"id":"reality","label":"REALITY","role":"lead","labelPolicy":"atomic"},{"id":"model","label":"Model","role":"lead","labelPolicy":"atomic"}],"edges":[{"from":"reality","to":"model"}]} }},
              |    {"id":"axis-scene","speaker":"reviewer","line":"Axis explanation","caption":"Axis explanation","visual":{"kind":"diagram","heading":"Axis","diagram":{"layout":"axis","clearance":24,"nodes":[{"id":"axis","label":"Axis","role":"axis","labelPolicy":"atomic"},{"id":"primary","label":"Primary view","role":"primary-view","labelPolicy":"balanced"},{"id":"support","label":"Supporting view","role":"supporting-view","labelPolicy":"balanced"}],"edges":[{"from":"axis","to":"primary"}]}}}
              |  ]
              |}""".stripMargin
          )
          _write_audio_manifest(dir.resolve("build/audio/lecture"), Vector("flow-scene", "axis-scene"))
          _write(dir.resolve("video_project.json"), """{"renderer":{"engine":"remotion"},"parts":[{"id":"lecture","type":"dialogue","script":"dialogue/script.json"}]}""")
          val runner = ProfileRenderRunner()

          When("Cozy renders the declared diagram scenes")
          CozyVideo.render(
            CozyVideo.RenderConfig(dir.resolve("video_project.json"), "remotion", toolMode = Some("host")),
            CozyVideo.VideoToolRegistry(Vector.empty),
            runner
          )

          Then("the authored diagrams and both renderer resources are staged with digest provenance")
          val workdir = dir.resolve("target/cozy-video/remotion/lecture")
          val props = parser.parse(_read(workdir.resolve("props.json"))).toOption.get
          props.hcursor.downField("scenes").downArray.downField("visual").downField("diagram").downField("nodes").downArray.get[String]("label").toOption shouldBe Some("REALITY")
          props.hcursor.downField("scenes").downN(1).downField("visual").downField("diagram").get[String]("layout").toOption shouldBe Some("axis")
          Files.isRegularFile(workdir.resolve("src/DialogueVideo.jsx")) shouldBe true
          Files.isRegularFile(workdir.resolve("src/DiagramLayout.js")) shouldBe true
          val resources = props.hcursor.downField("rendererTemplateResources").focus.flatMap(_.asArray).getOrElse(Vector.empty)
          val resourcepaths = resources.map(_.hcursor.get[String]("path").toOption)
          resourcepaths should contain(Some("DialogueVideo.jsx"))
          resourcepaths should contain(Some("DiagramLayout.js"))
          resources.forall(_.hcursor.get[String]("sha256").toOption.exists(_.matches("[0-9a-f]{64}"))) shouldBe true
        }
      }

      "the staged declarative layout module routes an eight-node axis graph and deterministically fits flow retries" in {
        _with_temp_dir("cozy-video-declarative-layout-module") { dir =>
          Given("a rendered character-dialogue workspace containing the pure layout module")
          _write(
            dir.resolve("dialogue/script.json"),
            """{"characters":{"guide":{"side":"left"}},"scenes":[{"id":"layout-module","speaker":"guide","line":"Layout module","visual":{"kind":"diagram","diagram":{"layout":"flow","nodes":[{"id":"start","label":"Start","role":"lead","labelPolicy":"atomic"}]}}}]}"""
          )
          _write_audio_manifest(dir.resolve("build/audio/lecture"), Vector("layout-module"))
          _write(dir.resolve("video_project.json"), """{"renderer":{"engine":"remotion"},"parts":[{"id":"lecture","type":"dialogue","script":"dialogue/script.json"}]}""")
          CozyVideo.render(
            CozyVideo.RenderConfig(dir.resolve("video_project.json"), "remotion", toolMode = Some("host")),
            CozyVideo.VideoToolRegistry(Vector.empty),
            ProfileRenderRunner()
          )
          val workdir = dir.resolve("target/cozy-video/remotion/lecture")
          val script =
            """import {computeDiagramLayout} from './src/DiagramLayout.js';
              |const clearance = 24;
              |const axis = {layout: 'axis', clearance, nodes: [
              |  {id: 'reality', label: 'Reality', role: 'lead', labelPolicy: 'atomic'},
              |  {id: 'knowledge', label: 'Knowledge', role: 'lead', labelPolicy: 'atomic'},
              |  {id: 'model', label: 'Model', role: 'lead', labelPolicy: 'atomic'},
              |  {id: 'axis', label: 'Axis', role: 'axis', labelPolicy: 'atomic'},
              |  {id: 'primary-one', label: 'Primary one', role: 'primary-view', labelPolicy: 'balanced'},
              |  {id: 'primary-two', label: 'Primary two', role: 'primary-view', labelPolicy: 'balanced'},
              |  {id: 'support-one', label: 'Support one', role: 'supporting-view', labelPolicy: 'balanced'},
              |  {id: 'support-two', label: 'Support two', role: 'supporting-view', labelPolicy: 'balanced'}
              |], edges: [
              |  {from: 'reality', to: 'knowledge'}, {from: 'knowledge', to: 'model'}, {from: 'model', to: 'axis'},
              |  {from: 'axis', to: 'primary-one'}, {from: 'primary-one', to: 'primary-two'},
              |  {from: 'axis', to: 'support-one'}, {from: 'support-one', to: 'support-two'}
              |]};
              |const flow = {layout: 'flow', clearance, nodes: Array.from({length: 8}, (_, index) => ({id: `flow-${index}`, label: `Balanced concept ${index}`, role: 'lead', labelPolicy: 'balanced'})), edges: []};
              |const intersects = (a, b) => a.x < b.x + b.width && a.x + a.width > b.x && a.y < b.y + b.height && a.y + a.height > b.y;
              |const distance = (a, b) => Math.max(Math.max(a.x - (b.x + b.width), b.x - (a.x + a.width), 0), Math.max(a.y - (b.y + b.height), b.y - (a.y + a.height), 0));
              |const segmentHits = (start, end, node) => start.x === end.x
              |  ? start.x > node.x && start.x < node.x + node.width && Math.max(start.y, end.y) > node.y && Math.min(start.y, end.y) < node.y + node.height
              |  : start.y > node.y && start.y < node.y + node.height && Math.max(start.x, end.x) > node.x && Math.min(start.x, end.x) < node.x + node.width;
              |const layout = computeDiagramLayout(axis, {sceneId: 'axis-check'});
              |const repeat = computeDiagramLayout(axis, {sceneId: 'axis-check'});
              |const fitted = computeDiagramLayout(flow, {sceneId: 'flow-check'});
              |if (JSON.stringify(layout) !== JSON.stringify(repeat)) throw new Error('non-deterministic-layout');
              |layout.nodes.forEach((node) => { if (node.x < 0 || node.y < 0 || node.x + node.width > layout.canvas.width || node.y + node.height > layout.canvas.height) throw new Error(`stage-overflow:${node.id}`); });
              |layout.nodes.forEach((node, index) => layout.nodes.slice(index + 1).forEach((other) => { if (intersects(node, other) || distance(node, other) < clearance) throw new Error(`clearance:${node.id}`); }));
              |layout.edges.forEach((edge) => edge.points.slice(0, -1).forEach((point, index) => layout.nodes.filter((node) => node.id !== edge.from && node.id !== edge.to).forEach((node) => { if (segmentHits(point, edge.points[index + 1], node)) throw new Error(`node-edge:${node.id}`); })));
              |if (new Set(fitted.nodes.map((node) => node.y)).size < 2 || fitted.nodes.some((node) => node.fontSize < 18) || fitted.clearance < clearance) throw new Error('flow-retry-contract');
              |console.log(JSON.stringify({axisNodes: layout.nodes.length, flowNodes: fitted.nodes.length}));
              |""".stripMargin

          When("the executable JavaScript regression module is run from that workspace")
          val process = new ProcessBuilder("node", "--input-type=module", "--eval", script)
            .directory(workdir.toFile)
            .redirectErrorStream(true)
            .start()
          val output = new String(process.getInputStream.readAllBytes(), StandardCharsets.UTF_8)
          val exit = process.waitFor()

          Then("the axis corridors and flow retry sequence satisfy their executable geometry contract")
          exit shouldBe 0
          output should include("\"axisNodes\":8")
          output should include("\"flowNodes\":8")
        }
      }

      "video render stops invalid declarative diagram contracts before invoking the renderer" in {
        _with_temp_dir("cozy-video-invalid-declarative-diagram") { dir =>
          Given("a character-dialogue script with an edge endpoint outside its declared nodes")
          _write(
            dir.resolve("dialogue/script.json"),
            """{"characters":{"guide":{"side":"left"}},"scenes":[{"id":"invalid-edge","speaker":"guide","line":"Invalid diagram","visual":{"kind":"diagram","diagram":{"layout":"flow","nodes":[{"id":"reality","label":"REALITY","role":"lead","labelPolicy":"atomic"}],"edges":[{"from":"reality","to":"missing"}]}}}]}"""
          )
          _write_audio_manifest(dir.resolve("build/audio/lecture"), Vector("invalid-edge"))
          _write(dir.resolve("video_project.json"), """{"renderer":{"engine":"remotion"},"parts":[{"id":"lecture","type":"dialogue","script":"dialogue/script.json"}]}""")
          val runner = ProfileRenderRunner()

          When("Cozy prepares to render the invalid diagram")
          val endpointerror = intercept[Throwable] {
            CozyVideo.render(
              CozyVideo.RenderConfig(dir.resolve("video_project.json"), "remotion", toolMode = Some("host")),
              CozyVideo.VideoToolRegistry(Vector.empty),
              runner
            )
          }

          Then("the endpoint diagnostic identifies the scene, node, and violation without invoking the runner")
          endpointerror.getMessage should include("Diagram scene invalid-edge")
          endpointerror.getMessage should include("node missing")
          endpointerror.getMessage should include("missing-edge-endpoint")
          runner.commands shouldBe empty

          Given("the same scene with an atomic label that cannot fit the declared node contract")
          _write(
            dir.resolve("dialogue/script.json"),
            """{"characters":{"guide":{"side":"left"}},"scenes":[{"id":"invalid-label","speaker":"guide","line":"Invalid diagram","visual":{"kind":"diagram","diagram":{"layout":"flow","nodes":[{"id":"too-wide","label":"THIS_ATOMIC_LABEL_CANNOT_FIT_IN_A_DIAGRAM_NODE","role":"lead","labelPolicy":"atomic"}]}}}]}"""
          )
          _write_audio_manifest(dir.resolve("build/audio/lecture"), Vector("invalid-label"))
          val atomicrunner = ProfileRenderRunner()

          When("Cozy prepares to render the impossible atomic label")
          val atomicerror = intercept[Throwable] {
            CozyVideo.render(
              CozyVideo.RenderConfig(dir.resolve("video_project.json"), "remotion", toolMode = Some("host")),
              CozyVideo.VideoToolRegistry(Vector.empty),
              atomicrunner
            )
          }

          Then("the fit diagnostic identifies the scene, node, and violation without invoking the runner")
          atomicerror.getMessage should include("Diagram scene invalid-label")
          atomicerror.getMessage should include("node too-wide")
          atomicerror.getMessage should include("impossible-atomic-fit")
          atomicrunner.commands shouldBe empty
        }
      }

      "video render diagnoses missing declared character and visual assets" in {
        _with_temp_dir("cozy-video-character-dialogue-missing-assets") { dir =>
          Given("a character-dialogue script with missing caller-owned assets")
          _write(dir.resolve("dialogue/script.json"), """{"characters":{"presenter-left":{"asset":"missing-character.png"}},"scenes":[{"id":"scene-01","speaker":"presenter-left","line":"Hello","visual":{"image":"missing-visual.png"}}]}""")
          _write_audio_manifest(dir.resolve("build/audio/lecture"), Vector("scene-01"))
          _write(dir.resolve("video_project.json"), """{"parts":[{"id":"lecture","type":"dialogue","script":"dialogue/script.json"}]}""")

          When("Cozy prepares the character-dialogue workspace")
          val error = intercept[Throwable] {
            CozyVideo.render(CozyVideo.RenderConfig(dir.resolve("video_project.json"), "remotion", toolMode = Some("host")), CozyVideo.VideoToolRegistry(Vector.empty), ProfileRenderRunner())
          }

          Then("the diagnostic identifies the character asset field and authored path")
          error.getMessage should include("character presenter-left")
          error.getMessage should include("asset")
          error.getMessage should include("missing-character.png")

          And("a missing scene visual identifies its scene, field, and authored path")
          _write_bytes(dir.resolve("dialogue/presenter-left.png"), Array[Byte](1, 2, 3))
          _write(dir.resolve("dialogue/script.json"), """{"characters":{"presenter-left":{"asset":"presenter-left.png"}},"scenes":[{"id":"scene-01","speaker":"presenter-left","line":"Hello","visual":{"image":"missing-visual.png"}}]}""")
          val visualerror = intercept[Throwable] {
            CozyVideo.render(CozyVideo.RenderConfig(dir.resolve("video_project.json"), "remotion", toolMode = Some("host")), CozyVideo.VideoToolRegistry(Vector.empty), ProfileRenderRunner())
          }
          visualerror.getMessage should include("scene scene-01")
          visualerror.getMessage should include("visual.image")
          visualerror.getMessage should include("missing-visual.png")

          And("a declared character asset may not escape the project through a symbolic link")
          val outside = dir.resolveSibling(dir.getFileName.toString + "-outside-character.png")
          _write_bytes(outside, Array[Byte](9, 8, 7))
          Files.delete(dir.resolve("dialogue/presenter-left.png"))
          Files.createSymbolicLink(dir.resolve("dialogue/presenter-left.png"), outside)
          _write(dir.resolve("dialogue/script.json"), """{"characters":{"presenter-left":{"asset":"presenter-left.png"}},"scenes":[{"id":"scene-01","speaker":"presenter-left","line":"Hello"}]}""")
          val symlinkrunner = ProfileRenderRunner()

          When("Cozy resolves the symbolic-link asset before staging")
          val symlinkerror = intercept[Throwable] {
            CozyVideo.render(CozyVideo.RenderConfig(dir.resolve("video_project.json"), "remotion", toolMode = Some("host")), CozyVideo.VideoToolRegistry(Vector.empty), symlinkrunner)
          }

          Then("the project boundary rejects the external real path before invoking the renderer")
          symlinkerror.getMessage should include("resolves outside project root")
          symlinkrunner.commands shouldBe empty
          Files.deleteIfExists(outside)
        }
      }

      "video render rejects a scene visual symbolic link that resolves outside the project root" in {
        _with_temp_dir("cozy-video-visual-symlink-boundary") { dir =>
          Given("a character-dialogue scene whose visual image is a project-local symbolic link")
          _write_bytes(dir.resolve("dialogue/presenter-left.png"), Array[Byte](1, 2, 3))
          val outside = dir.resolveSibling(dir.getFileName.toString + "-outside-visual.png")
          _write_bytes(outside, Array[Byte](9, 8, 7))
          Files.createDirectories(dir.resolve("dialogue"))
          Files.createSymbolicLink(dir.resolve("dialogue/visual.png"), outside)
          _write(
            dir.resolve("dialogue/script.json"),
            """{"characters":{"presenter-left":{"asset":"presenter-left.png"}},"scenes":[{"id":"scene-visual-symlink","speaker":"presenter-left","line":"Hello","visual":{"image":"visual.png"}}]}"""
          )
          _write_audio_manifest(dir.resolve("build/audio/lecture"), Vector("scene-visual-symlink"))
          _write(dir.resolve("video_project.json"), """{"parts":[{"id":"lecture","type":"dialogue","script":"dialogue/script.json"}]}""")
          val runner = ProfileRenderRunner()

          When("Cozy resolves the scene visual before staging the renderer workspace")
          val error = intercept[Throwable] {
            CozyVideo.render(
              CozyVideo.RenderConfig(dir.resolve("video_project.json"), "remotion", toolMode = Some("host")),
              CozyVideo.VideoToolRegistry(Vector.empty),
              runner
            )
          }

          Then("the real-path project-root escape is rejected before invoking the renderer")
          error.getMessage should include("visual.image asset for scene scene-visual-symlink")
          error.getMessage should include("resolves outside project root")
          runner.commands shouldBe empty
          Files.deleteIfExists(outside)
        }
      }

      "video render stages one reviewed web-demo recording with caller-declared presenter assets" in {
        _with_temp_dir("cozy-video-character-web-demo") { dir =>
          Given("a web-demo part with one reviewed browser recording and a declared presenter")
          _write_bytes(dir.resolve("demo/presenter-left.png"), Array[Byte](1, 2, 3))
          _write_bytes(dir.resolve("demo/presenter-open.png"), Array[Byte](4, 5, 6))
          _write(dir.resolve("demo/script.json"), """{"characters":{"presenter-left":{"asset":"presenter-left.png","mouthClosedAsset":"presenter-left.png","mouthOpenAsset":"presenter-open.png","side":"left","width":216,"bottom":132,"inset":24,"maxHeight":390,"flipX":true}},"scenes":[{"id":"demonstration","speaker":"presenter-left","line":"Review the browser result.","caption":"Review the browser result."}]}""")
          _write(dir.resolve("demo/steps.json"), """{"steps": []}""")
          _write_audio_manifest(dir.resolve("build/audio/demonstration"), Vector("demonstration"))
          _write_bytes(dir.resolve("build/record/demonstration/reviewed.webm"), Array[Byte](7, 8, 9))
          _write(dir.resolve("video_project.json"), """{"parts":[{"id":"demonstration","type":"web-demo","script":"demo/script.json","steps":"demo/steps.json","recordDir":"build/record/demonstration"}]}""")
          val runner = ProfileRenderRunner()

          When("Cozy prepares the Remotion web-demo workspace")
          CozyVideo.render(CozyVideo.RenderConfig(dir.resolve("video_project.json"), "remotion", toolMode = Some("host")), CozyVideo.VideoToolRegistry(Vector.empty), runner)

          Then("the workspace contains the generic recording, presenter, caption, and audio surfaces")
          val workdir = dir.resolve("target/cozy-video/remotion/demonstration")
          val props = parser.parse(_read(workdir.resolve("props.json"))).toOption.get
          props.hcursor.downField("rendererTemplate").as[String].toOption shouldBe Some("cozy-character-web-demo-v1")
          props.hcursor.downField("recordingPath").as[String].toOption shouldBe Some("recording/recording.webm")
          props.hcursor.downField("characters").downField("presenter-left").get[Int]("bottom").toOption shouldBe Some(132)
          props.hcursor.downField("characters").downField("presenter-left").get[Int]("inset").toOption shouldBe Some(24)
          Files.isRegularFile(workdir.resolve("public/recording/recording.webm")) shouldBe true
          Files.isRegularFile(workdir.resolve("public/characters/character-presenter-left-asset.png")) shouldBe true
          Files.isRegularFile(workdir.resolve("public/characters/character-presenter-left-mouthOpenAsset.png")) shouldBe true
          val root = _read(workdir.resolve("src/Root.tsx"))
          root should include("Video")
          root should include("objectFit: 'contain'")
          root should include("mouthOpenAsset")
          root should include("character?.bottom")
          root should include("character?.inset")
          root should include("character?.maxHeight")
          root should include("character?.flipX")
          root should include("scene?.caption || scene?.line")

          Given("no reviewed recording is available")
          Files.delete(dir.resolve("build/record/demonstration/reviewed.webm"))
          val emptyrunner = ProfileRenderRunner()

          When("Cozy resolves the recording directory with no readable recording")
          val emptyerror = intercept[Throwable] {
            CozyVideo.render(CozyVideo.RenderConfig(dir.resolve("video_project.json"), "remotion", toolMode = Some("host")), CozyVideo.VideoToolRegistry(Vector.empty), emptyrunner)
          }

          Then("the missing recording diagnostic is reported before invoking a runner")
          emptyerror.getMessage should include("demonstration")
          emptyrunner.commands shouldBe empty

          Given("more than one reviewed recording is available")
          _write_bytes(dir.resolve("build/record/demonstration/one.webm"), Array[Byte](1))
          _write_bytes(dir.resolve("build/record/demonstration/two.mp4"), Array[Byte](2))
          val multiplerunner = ProfileRenderRunner()

          When("Cozy resolves the recording directory with multiple readable recordings")
          val multipleerror = intercept[Throwable] {
            CozyVideo.render(CozyVideo.RenderConfig(dir.resolve("video_project.json"), "remotion", toolMode = Some("host")), CozyVideo.VideoToolRegistry(Vector.empty), multiplerunner)
          }

          Then("the cardinality diagnostic is reported before invoking a runner")
          multipleerror.getMessage should include("exactly one")
          multiplerunner.commands shouldBe empty

          Given("an absolute recording directory in the web-demo descriptor")
          _write(dir.resolve("video_project.json"), s"""{"parts":[{"id":"demonstration","type":"web-demo","script":"demo/script.json","steps":"demo/steps.json","recordDir":"${dir.resolve("build/record/demonstration")}"}]}""")
          val absoluterunner = ProfileRenderRunner()

          When("Cozy resolves the absolute recording directory")
          val absoluteerror = intercept[Throwable] {
            CozyVideo.render(CozyVideo.RenderConfig(dir.resolve("video_project.json"), "remotion", toolMode = Some("host")), CozyVideo.VideoToolRegistry(Vector.empty), absoluterunner)
          }

          Then("the absolute path is rejected before invoking the renderer")
          absoluteerror.getMessage should include("Absolute Web-demo part demonstration recordDir path is not allowed")
          absoluterunner.commands shouldBe empty

          Given("a traversal recording directory in the web-demo descriptor")
          _write(dir.resolve("video_project.json"), """{"parts":[{"id":"demonstration","type":"web-demo","script":"demo/script.json","steps":"demo/steps.json","recordDir":"../outside-recording"}]}""")
          val traversalrunner = ProfileRenderRunner()

          When("Cozy resolves the traversal recording directory")
          val traversalerror = intercept[Throwable] {
            CozyVideo.render(CozyVideo.RenderConfig(dir.resolve("video_project.json"), "remotion", toolMode = Some("host")), CozyVideo.VideoToolRegistry(Vector.empty), traversalrunner)
          }

          Then("the traversal is rejected before invoking the renderer")
          traversalerror.getMessage should include("Traversal outside project root is not allowed")
          traversalrunner.commands shouldBe empty
        }
      }

      "video render rejects a web-demo recording symbolic link that resolves outside the project root" in {
        _with_temp_dir("cozy-video-recording-symlink-boundary") { dir =>
          Given("a web-demo part whose recording child is a project-local symbolic link")
          _write_bytes(dir.resolve("demo/presenter-left.png"), Array[Byte](1, 2, 3))
          _write_bytes(dir.resolve("demo/presenter-open.png"), Array[Byte](4, 5, 6))
          _write(
            dir.resolve("demo/script.json"),
            """{"characters":{"presenter-left":{"asset":"presenter-left.png","mouthClosedAsset":"presenter-left.png","mouthOpenAsset":"presenter-open.png","side":"left","width":216,"bottom":132,"inset":24,"maxHeight":390,"flipX":true}},"scenes":[{"id":"demonstration","speaker":"presenter-left","line":"Review the browser result.","caption":"Review the browser result."}]}"""
          )
          _write(dir.resolve("demo/steps.json"), """{"steps": []}""")
          _write_audio_manifest(dir.resolve("build/audio/demonstration"), Vector("demonstration"))
          val outside = dir.resolveSibling(dir.getFileName.toString + "-outside-recording.webm")
          _write_bytes(outside, Array[Byte](9, 8, 7))
          Files.createDirectories(dir.resolve("build/record/demonstration"))
          Files.createSymbolicLink(dir.resolve("build/record/demonstration/reviewed.webm"), outside)
          _write(
            dir.resolve("video_project.json"),
            """{"parts":[{"id":"demonstration","type":"web-demo","script":"demo/script.json","steps":"demo/steps.json","recordDir":"build/record/demonstration"}]}"""
          )
          val runner = ProfileRenderRunner()

          When("Cozy resolves the web-demo recording before staging the renderer workspace")
          val error = intercept[Throwable] {
            CozyVideo.render(
              CozyVideo.RenderConfig(dir.resolve("video_project.json"), "remotion", toolMode = Some("host")),
              CozyVideo.VideoToolRegistry(Vector.empty),
              runner
            )
          }

          Then("the real-path project-root escape is rejected before invoking the renderer")
          error.getMessage should include("Web-demo part demonstration recording")
          error.getMessage should include("resolves outside project root")
          runner.commands shouldBe empty
          Files.deleteIfExists(outside)
        }
      }

      "video render simple-java2d renders all renderable parts through python and ffmpeg" in {
        _with_temp_dir("cozy-video-render-simple-java2d") { dir =>
          _write(dir.resolve("dialogue.json"), _script_json)
          _write(dir.resolve("storyboard.json"), _script_json)
          _write_audio_manifest(
            dir.resolve("build/audio/lecture"),
            Vector("title", "description", "summary"),
            Some("dialogue.wav")
          )
          _write_audio_manifest(
            dir.resolve("build/audio/board"),
            Vector("title", "description", "summary"),
            Some("storyboard.wav")
          )
          _write(
            dir.resolve("video_project.json"),
            s"""{
           |  "title": "Simple Render Video",
           |  "parts": [
           |    {"id": "lecture", "type": "dialogue", "script": "dialogue.json", "output": "build/parts/lecture.mp4"},
           |    {"id": "board", "type": "storyboard", "script": "storyboard.json", "output": "build/parts/board.mp4"}
           |  ]
           |}
           |""".stripMargin
          )
          val runner = RenderingRunner()

          val out = CozyVideo.render(
            CozyVideo
              .RenderConfig(dir.resolve("video_project.json"), "simple-java2d"),
            CozyVideo.VideoToolRegistry(Vector.empty),
            runner
          )

          ((out.contains("Cozy Video Render")) shouldBe true)
          ((out.contains("parts: 2")) shouldBe true)
          ((out.contains(
            "part.lecture: " + dir
              .resolve("build/parts/lecture.mp4")
              .normalize()
          )) shouldBe true)
          ((out.contains(
            "simpleJava2dWorkDir: " + dir
              .resolve("target/cozy-video/simple-java2d/lecture")
              .normalize()
          )) shouldBe true)
          ((runner.commands.size == 4) shouldBe true)
          ((runner.commands(0).args.take(8) == Vector(
            "docker",
            "run",
            "--rm",
            "-v",
            s"$dir:/workspace",
            "-w",
            "/workspace",
            "ghcr.io/asami/textus-toolchain:latest"
          )) shouldBe true)
          ((runner.commands(0).args.contains("python3")) shouldBe true)
          ((runner
            .commands(0)
            .args
            .exists(
              _.endsWith(
                "target/cozy-video/simple-java2d/lecture/render_frame.py"
              )
            )) shouldBe true)
          ((runner.commands(1).args.contains("ffmpeg")) shouldBe true)
          ((runner
            .commands(1)
            .args
            .contains(
              "/workspace/target/cozy-video/simple-java2d/lecture/frame.png"
            )) shouldBe true)
          ((runner
            .commands(1)
            .args
            .contains(
              "/workspace/build/audio/lecture/dialogue.wav"
            )) shouldBe true)
          ((Files.isRegularFile(
            dir.resolve("target/cozy-video/simple-java2d/lecture/props.json")
          )) shouldBe true)
          ((Files.isRegularFile(
            dir.resolve(
              "target/cozy-video/simple-java2d/lecture/render_frame.py"
            )
          )) shouldBe true)
          ((Files.isRegularFile(
            dir.resolve("target/cozy-video/simple-java2d/lecture/frame.png")
          )) shouldBe true)
          ((!_read(
            dir.resolve(
              "target/cozy-video/simple-java2d/lecture/render_frame.py"
            )
          ).contains("props.get(\"framePath\"")) shouldBe true)
          ((Files.isRegularFile(
            dir.resolve("build/parts/lecture.mp4")
          )) shouldBe true)
          ((Files.isRegularFile(
            dir.resolve("build/parts/lecture.manifest.json")
          )) shouldBe true)
          val manifest = _read(dir.resolve("build/parts/lecture.manifest.json"))
          ((manifest.contains(
            "\"renderer\" : \"simple-java2d\""
          )) shouldBe true)
          ((manifest.contains("\"framePath\"")) shouldBe true)
          ((manifest.contains("\"audioCombinedPath\"")) shouldBe true)
          ((manifest.contains("\"simpleJava2dWorkDir\"")) shouldBe true)
        }
      }

      "video render simple-java2d can render one selected part in host mode" in {
        _with_temp_dir("cozy-video-render-simple-java2d-host") { dir =>
          _write(dir.resolve("dialogue.json"), _script_json)
          _write(dir.resolve("storyboard.json"), _script_json)
          _write_audio_manifest(
            dir.resolve("build/audio/lecture"),
            Vector("title", "description", "summary"),
            Some("dialogue.wav")
          )
          _write_audio_manifest(
            dir.resolve("build/audio/board"),
            Vector("title", "description", "summary"),
            Some("storyboard.wav")
          )
          _write(
            dir.resolve("video_project.json"),
            s"""{
           |  "parts": [
           |    {"id": "lecture", "type": "dialogue", "script": "dialogue.json"},
           |    {"id": "board", "type": "storyboard", "script": "storyboard.json"}
           |  ]
           |}
           |""".stripMargin
          )
          val runner = RenderingRunner()

          val out = CozyVideo.render(
            CozyVideo.RenderConfig(
              dir.resolve("video_project.json"),
              "simple-java2d",
              part = Some("board"),
              toolMode = Some("host")
            ),
            CozyVideo.VideoToolRegistry(Vector.empty),
            runner
          )

          ((out.contains("toolMode: host")) shouldBe true)
          ((out.contains("parts: 1")) shouldBe true)
          ((out.contains(
            "part.board: " + dir.resolve("build/parts/board.mp4").normalize()
          )) shouldBe true)
          ((runner.commands.size == 2) shouldBe true)
          ((runner.commands(0).args.head == "python3") shouldBe true)
          ((runner.commands(1).args.head == "ffmpeg") shouldBe true)
          ((!runner.commands.exists(_.args.head == "docker")) shouldBe true)
          ((Files.isRegularFile(
            dir.resolve("build/parts/board.manifest.json")
          )) shouldBe true)
        }
      }

      "video render remotion fails for invalid selection renderer inputs and runner failures" in {
        _with_temp_dir("cozy-video-render-errors") { dir =>
          _write(dir.resolve("script.json"), _script_json)
          _write_audio_manifest(
            dir.resolve("build/audio/intro"),
            Vector("title", "description", "summary")
          )
          _write(
            dir.resolve("video_project.json"),
            _project_json("script.json")
          )

          val unknownpart = intercept[Throwable] {
            CozyVideo.render(
              CozyVideo.RenderConfig(
                dir.resolve("video_project.json"),
                "remotion",
                part = Some("missing")
              ),
              CozyVideo.VideoToolRegistry(Vector.empty),
              RecordingRunner()
            )
          }
          ((unknownpart.getMessage
            .contains("Unknown video part")) shouldBe true)

          val unsupportedrenderer = intercept[Throwable] {
            CozyVideo.render(
              CozyVideo.RenderConfig(
                dir.resolve("video_project.json"),
                "simple-java3d"
              ),
              CozyVideo.VideoToolRegistry(Vector.empty),
              RecordingRunner()
            )
          }
          ((unsupportedrenderer.getMessage
            .contains("Unsupported video renderer")) shouldBe true)

          val missingproject = intercept[Throwable] {
            CozyVideo.render(
              CozyVideo.RenderConfig(dir.resolve("missing.json"), "remotion"),
              CozyVideo.VideoToolRegistry(Vector.empty),
              RecordingRunner()
            )
          }
          ((missingproject.getMessage
            .contains("Missing video project file")) shouldBe true)

          val runnerfailure = intercept[Throwable] {
            CozyVideo.render(
              CozyVideo
                .RenderConfig(dir.resolve("video_project.json"), "remotion"),
              CozyVideo.VideoToolRegistry(Vector.empty),
              RecordingRunner(result =
                CozyVideo.VideoCommandResult(1, "", "remotion missing")
              )
            )
          }
          ((runnerfailure.getMessage
            .contains("Remotion render failed")) shouldBe true)
          ((runnerfailure.getMessage
            .contains("remotion missing")) shouldBe true)
        }
      }

      "video render simple-java2d validates combined audio runner failures and tool checks" in {
        _with_temp_dir("cozy-video-render-simple-java2d-errors") { dir =>
          _write(dir.resolve("script.json"), _script_json)
          _write_audio_manifest(
            dir.resolve("build/audio/intro"),
            Vector("title", "description", "summary")
          )
          _write(
            dir.resolve("video_project.json"),
            _project_json("script.json")
          )

          val missingcombined = intercept[Throwable] {
            CozyVideo.render(
              CozyVideo.RenderConfig(
                dir.resolve("video_project.json"),
                "simple-java2d"
              ),
              CozyVideo.VideoToolRegistry(Vector.empty),
              RenderingRunner()
            )
          }
          ((missingcombined.getMessage
            .contains("Missing combined audio file")) shouldBe true)
          ((missingcombined.getMessage
            .contains("cozy video synthesize")) shouldBe true)

          Files.write(
            dir.resolve("build/audio/intro/script.wav"),
            _wav_bytes(0.4)
          )
          val framefailure = intercept[Throwable] {
            CozyVideo.render(
              CozyVideo.RenderConfig(
                dir.resolve("video_project.json"),
                "simple-java2d"
              ),
              CozyVideo.VideoToolRegistry(Vector.empty),
              RenderingRunner(failTool = Some("python3"))
            )
          }
          ((framefailure.getMessage
            .contains("simple-java2d frame render failed")) shouldBe true)

          val ffmpegfailure = intercept[Throwable] {
            CozyVideo.render(
              CozyVideo.RenderConfig(
                dir.resolve("video_project.json"),
                "simple-java2d"
              ),
              CozyVideo.VideoToolRegistry(Vector.empty),
              RenderingRunner(failTool = Some("ffmpeg"))
            )
          }
          ((ffmpegfailure.getMessage
            .contains("simple-java2d ffmpeg encode failed")) shouldBe true)

          val missingtool = intercept[Throwable] {
            CozyVideo.render(
              CozyVideo.RenderConfig(
                dir.resolve("video_project.json"),
                "simple-java2d",
                checkTools = true,
                toolMode = Some("host")
              ),
              CozyVideo.VideoToolRegistry(
                Vector(
                  StubProvider(
                    CozyVideo.VideoToolCheck(
                      "python-pillow",
                      CozyVideo.VideoToolMode.Host,
                      CozyVideo.VideoToolStatus.Available,
                      "ok"
                    )
                  ),
                  StubProvider(
                    CozyVideo.VideoToolCheck(
                      "ffmpeg",
                      CozyVideo.VideoToolMode.Host,
                      CozyVideo.VideoToolStatus.Missing,
                      "missing ffmpeg",
                      Some("install ffmpeg")
                    )
                  )
                )
              ),
              RenderingRunner()
            )
          }
          ((missingtool.getMessage.contains("ffmpeg is missing")) shouldBe true)
          ((missingtool.getMessage.contains("install ffmpeg")) shouldBe true)
        }
      }

      "video render remotion validates audio prerequisites and tool checks" in {
        _with_temp_dir("cozy-video-render-audio-errors") { dir =>
          _write(dir.resolve("script.json"), _script_json)
          _write(
            dir.resolve("video_project.json"),
            _project_json("script.json")
          )

          val missingmanifest = intercept[Throwable] {
            CozyVideo.render(
              CozyVideo
                .RenderConfig(dir.resolve("video_project.json"), "remotion"),
              CozyVideo.VideoToolRegistry(Vector.empty),
              RecordingRunner()
            )
          }
          ((missingmanifest.getMessage
            .contains("Missing audio manifest")) shouldBe true)
          ((missingmanifest.getMessage
            .contains("cozy video synthesize")) shouldBe true)

          _write(
            dir.resolve("build/audio/intro/manifest.json"),
            _audio_manifest_json(Vector("title", "description", "summary"))
          )
          val missingaudio = intercept[Throwable] {
            CozyVideo.render(
              CozyVideo
                .RenderConfig(dir.resolve("video_project.json"), "remotion"),
              CozyVideo.VideoToolRegistry(Vector.empty),
              RecordingRunner()
            )
          }
          ((missingaudio.getMessage
            .contains("Missing audio file")) shouldBe true)

          _write_audio_manifest(
            dir.resolve("build/audio/intro"),
            Vector("title", "description", "summary")
          )
          val missingtool = intercept[Throwable] {
            CozyVideo.render(
              CozyVideo.RenderConfig(
                dir.resolve("video_project.json"),
                "remotion",
                checkTools = true,
                toolMode = Some("host")
              ),
              CozyVideo.VideoToolRegistry(
                Vector(
                  StubProvider(
                    CozyVideo.VideoToolCheck(
                      "remotion-node",
                      CozyVideo.VideoToolMode.Host,
                      CozyVideo.VideoToolStatus.Missing,
                      "missing remotion",
                      Some("install remotion")
                    )
                  )
                )
              ),
              RecordingRunner()
            )
          }
          ((missingtool.getMessage
            .contains("remotion-node is missing")) shouldBe true)
          ((missingtool.getMessage.contains("install remotion")) shouldBe true)
        }
      }

      "video render preflights Docker dependencies without an explicit tool check" in {
        _with_temp_dir("cozy-video-render-docker-preflight") { dir =>
          Given("a Docker Remotion render whose daemon dependency is unavailable")
          _write(dir.resolve("script.json"), _script_json)
          _write(dir.resolve("video_project.json"), _project_json("script.json"))
          val runner = RecordingRunner()
          val tools = CozyVideo.VideoToolRegistry(Vector(
            StubProvider(
              CozyVideo.VideoToolCheck(
                "docker-toolchain",
                CozyVideo.VideoToolMode.Docker,
                CozyVideo.VideoToolStatus.Missing,
                "Docker daemon is not reachable.",
                Some("Install/start Docker Desktop or a compatible Docker daemon.")
              )
            )
          ))

          When("Cozy renders without --check-tools")
          val fault = intercept[FaultException] {
            CozyVideo.render(
              CozyVideo.RenderConfig(dir.resolve("video_project.json"), "remotion"),
              tools,
              runner
            )
          }

          Then("the unavailable Docker subsystem stops rendering before runner invocation")
          fault.getMessage should include_text("docker-toolchain")
          fault.getMessage should include_text("Docker daemon is not reachable")
          fault.getMessage should include_text("Install/start Docker Desktop")
          runner.commands shouldBe empty
        }
      }

    }

    "runtime inspection and tool checks" which {
      "video inspect fails explicitly for missing project files" in {
        _with_temp_dir("cozy-video-missing-project") { dir =>
          val e = intercept[Throwable] {
            CozyVideo.inspect(
              CozyVideo
                .InspectConfig(dir.resolve("missing.json"), checkTools = false),
              CozyVideo.VideoToolRegistry(Vector.empty)
            )
          }

          ((e.getMessage.contains("Missing video project file")) shouldBe true)
        }
      }

      "video inspect fails explicitly for unknown options" in {
        _with_temp_dir("cozy-video-unknown-option") { dir =>
          _write(dir.resolve("script.json"), _script_json)
          _write(
            dir.resolve("video_project.json"),
            _project_json("script.json")
          )
          val e = intercept[Throwable] {
            CozyVideo.execute(
              List(
                "video",
                "inspect",
                dir.resolve("video_project.json").toString,
                "--check-toolz"
              ),
              CozyVideo.VideoToolRegistry(Vector.empty)
            )
          }

          ((e.getMessage.contains("check-toolz")) shouldBe true)
        }
      }

      "video inspect generates unique ids for anonymous subscenes" in {
        val scene = CozyVideo.VideoScene(
          id = None,
          speaker = None,
          line = None,
          narration = None,
          caption = None,
          duration = None,
          targetDuration = None,
          leadSilence = None,
          subscenes = Vector(
            CozyVideo.VideoScene(
              None,
              None,
              Some("one"),
              None,
              None,
              Some(1.0),
              None,
              None,
              Vector.empty
            ),
            CozyVideo.VideoScene(
              None,
              None,
              Some("two"),
              None,
              None,
              Some(1.0),
              None,
              None,
              Vector.empty
            )
          )
        )

        ((scene.expanded(7).flatMap(_.id) == Vector(
          "scene-07.subscene-01",
          "scene-07.subscene-02"
        )) shouldBe true)
      }

      "video inspect does not call tool probes without check-tools" in {
        _with_temp_dir("cozy-video-no-tool-probe") { dir =>
          _write(dir.resolve("script.json"), _script_json)
          _write(
            dir.resolve("video_project.json"),
            _project_json("script.json")
          )
          val probe = RecordingProbe()

          val out = CozyVideo.inspect(
            CozyVideo.InspectConfig(
              dir.resolve("video_project.json"),
              checkTools = false
            ),
            CozyVideo.VideoToolRegistry.production(probe)
          )

          ((!out.contains("tool checks:")) shouldBe true)
          ((probe.commands.isEmpty) shouldBe true)
          ((probe.httpGets.isEmpty) shouldBe true)
          ((probe.existsChecks.isEmpty) shouldBe true)
        }
      }

      "video inspect production registry reports deterministic available tools through probe" in {
        _with_temp_dir("cozy-video-tool-probe-available") { dir =>
          val chromium = dir.resolve("chromium")
          val whisper = dir.resolve("models/ggml-base.bin")
          _write(dir.resolve("script.json"), _script_json)
          _write(
            dir.resolve("video_project.json"),
            _project_json_with_tools("script.json", whisper.toString)
          )
          _write(chromium, "binary")
          _write(whisper, "model")
          val probe = RecordingProbe(
            commandResults = Map(
              Vector(
                "docker",
                "version",
                "--format",
                "{{.Server.Version}}"
              ) -> CozyVideo.VideoCommandResult(0, "25.0\n", ""),
              Vector(
                "docker",
                "image",
                "inspect",
                "ghcr.io/asami/textus-toolchain:latest"
              ) -> CozyVideo.VideoCommandResult(0, "[]", ""),
              Vector("ffmpeg", "-version") -> CozyVideo
                .VideoCommandResult(0, "ffmpeg", ""),
              Vector("ffprobe", "-version") -> CozyVideo
                .VideoCommandResult(0, "ffprobe", ""),
              Vector("node", "--version") -> CozyVideo
                .VideoCommandResult(0, "v22.0.0", ""),
              Vector("npm", "--version") -> CozyVideo
                .VideoCommandResult(0, "10.0.0", ""),
              Vector(
                "node",
                "-e",
                "require.resolve('@remotion/renderer')"
              ) -> CozyVideo
                .VideoCommandResult(0, "/node_modules/@remotion/renderer", ""),
              Vector("node", "-e", "require.resolve('playwright')") -> CozyVideo
                .VideoCommandResult(0, "/node_modules/playwright", ""),
              Vector(
                "node",
                "-e",
                "const { chromium } = require('playwright'); console.log(chromium.executablePath())"
              ) -> CozyVideo
                .VideoCommandResult(0, chromium.toString + "\n", ""),
              Vector("whisper-cli", "--help") -> CozyVideo
                .VideoCommandResult(0, "usage", "")
            ),
            httpResults = Map(
              "http://127.0.0.1:50021/version" -> CozyVideo.VideoHttpResult(
                200,
                "0.0.0"
              )
            )
          )

          val out = CozyVideo.inspect(
            CozyVideo.InspectConfig(
              dir.resolve("video_project.json"),
              checkTools = true,
              toolMode = Some("host")
            ),
            CozyVideo.VideoToolRegistry.production(probe)
          )

          ((out.contains("docker-toolchain: unchecked (docker)")) shouldBe true)
          ((out.contains("docker-image: unchecked (docker)")) shouldBe true)
          ((out.contains(
            "voicevox: available (external-service)"
          )) shouldBe true)
          ((out.contains("ffmpeg: available (host)")) shouldBe true)
          ((out.contains("remotion-node: available (host)")) shouldBe true)
          ((out.contains("playwright: available (host)")) shouldBe true)
          ((out.contains("whisper-cpp: available (host)")) shouldBe true)
          ((out.contains("python-pillow: missing (host)")) shouldBe true)
          ((out.indexOf("docker-toolchain: unchecked") < out.indexOf(
            "docker-image: unchecked"
          )) shouldBe true)
          ((out.indexOf("docker-image: unchecked") < out.indexOf(
            "voicevox: available"
          )) shouldBe true)
          ((!probe.commands.exists(
            _.take(3) == Vector("docker", "run", "--rm")
          )) shouldBe true)
        }
      }

      "video inspect production registry reports missing and unchecked tools through probe" in {
        _with_temp_dir("cozy-video-tool-probe-missing") { dir =>
          _write(dir.resolve("script.json"), _script_json)
          _write(
            dir.resolve("video_project.json"),
            s"""{
           |  "title": "Tool Missing",
           |  "tools": {
           |    "dockerImage": "example/toolchain:dev",
           |    "voicevoxUrl": "http://voicevox.example",
           |    "whisperModel": "models/missing.bin"
           |  },
           |  "parts": [
           |    {"id": "intro", "type": "dialogue", "script": "script.json"}
           |  ]
           |}
           |""".stripMargin
          )
          val probe = RecordingProbe(
            commandResults = Map(
              Vector(
                "docker",
                "version",
                "--format",
                "{{.Server.Version}}"
              ) -> CozyVideo.VideoCommandResult(1, "", "docker unavailable"),
              Vector("ffmpeg", "-version") -> CozyVideo
                .VideoCommandResult(0, "ffmpeg", ""),
              Vector("ffprobe", "-version") -> CozyVideo
                .VideoCommandResult(127, "", "missing"),
              Vector("node", "--version") -> CozyVideo
                .VideoCommandResult(0, "v22.0.0", ""),
              Vector("npm", "--version") -> CozyVideo
                .VideoCommandResult(0, "10.0.0", ""),
              Vector(
                "node",
                "-e",
                "require.resolve('@remotion/renderer')"
              ) -> CozyVideo.VideoCommandResult(1, "", "missing"),
              Vector("node", "-e", "require.resolve('playwright')") -> CozyVideo
                .VideoCommandResult(1, "", "missing"),
              Vector(
                "node",
                "-e",
                "const { chromium } = require('playwright'); console.log(chromium.executablePath())"
              ) -> CozyVideo.VideoCommandResult(1, "", "missing"),
              Vector("whisper-cli", "--help") -> CozyVideo
                .VideoCommandResult(0, "usage", "")
            ),
            httpResults = Map(
              "http://voicevox.example/version" -> CozyVideo.VideoHttpResult(
                0,
                "",
                Some("connection refused")
              )
            )
          )

          val out = CozyVideo.inspect(
            CozyVideo.InspectConfig(
              dir.resolve("video_project.json"),
              checkTools = true,
              toolMode = Some("host")
            ),
            CozyVideo.VideoToolRegistry.production(probe)
          )

          ((out.contains("docker-toolchain: unchecked (docker)")) shouldBe true)
          ((out.contains("docker-image: unchecked (docker)")) shouldBe true)
          ((out.contains("voicevox: missing (external-service)")) shouldBe true)
          ((out.contains(
            "setup: Start VOICEVOX Engine or set tools.voicevoxUrl / video.voicevox.url."
          )) shouldBe true)
          ((out.contains("ffmpeg: missing (host)")) shouldBe true)
          ((out.contains("remotion-node: missing (host)")) shouldBe true)
          ((out.contains("playwright: missing (host)")) shouldBe true)
          ((out.contains("whisper-cpp: missing (host)")) shouldBe true)
          ((out.contains("python-pillow: missing (host)")) shouldBe true)
          ((out.contains(
            "whisper.cpp model is missing: " + dir
              .resolve("models/missing.bin")
              .normalize()
          )) shouldBe true)
        }
      }

      "video inspect production registry reports missing docker image with pull guidance" in {
        _with_temp_dir("cozy-video-tool-probe-image-missing") { dir =>
          _write(dir.resolve("script.json"), _script_json)
          _write(
            dir.resolve("video_project.json"),
            s"""{
           |  "title": "Image Missing",
           |  "tools": {
           |    "dockerImage": "example/toolchain:dev"
           |  },
           |  "parts": [
           |    {"id": "intro", "type": "dialogue", "script": "script.json"}
           |  ]
           |}
           |""".stripMargin
          )
          val probe = RecordingProbe(
            commandResults = Map(
              Vector(
                "docker",
                "version",
                "--format",
                "{{.Server.Version}}"
              ) -> CozyVideo.VideoCommandResult(0, "25.0\n", ""),
              Vector(
                "docker",
                "image",
                "inspect",
                "example/toolchain:dev"
              ) -> CozyVideo.VideoCommandResult(1, "", "No such image"),
              Vector("ffmpeg", "-version") -> CozyVideo
                .VideoCommandResult(0, "ffmpeg", ""),
              Vector("ffprobe", "-version") -> CozyVideo.VideoCommandResult(
                0,
                "ffprobe",
                ""
              )
            )
          )

          val out = CozyVideo.inspect(
            CozyVideo.InspectConfig(
              dir.resolve("video_project.json"),
              checkTools = true
            ),
            CozyVideo.VideoToolRegistry.production(probe)
          )

          ((out.contains("docker-toolchain: available (docker)")) shouldBe true)
          ((out.contains("docker-image: missing (docker)")) shouldBe true)
          ((out.contains(
            "setup: Run: docker pull example/toolchain:dev"
          )) shouldBe true)
          ((out.contains(
            "textus-toolchain-image: unchecked (docker)"
          )) shouldBe true)
          ((!probe.commands.exists(
            _.take(3) == Vector("docker", "run", "--rm")
          )) shouldBe true)
        }
      }

      "video inspect docker mode check-tools focuses on docker image and voicevox" in {
        _with_temp_dir("cozy-video-tool-probe-docker-mode") { dir =>
          _write(dir.resolve("script.json"), _script_json)
          _write(
            dir.resolve("video_project.json"),
            s"""{
           |  "title": "Docker Checks",
           |  "tools": {
           |    "toolMode": "docker",
           |    "dockerImage": "example/toolchain:dev",
           |    "voicevoxUrl": "http://voicevox.example"
           |  },
           |  "parts": [
           |    {"id": "intro", "type": "dialogue", "script": "script.json"}
           |  ]
           |}
           |""".stripMargin
          )
          val probe = RecordingProbe(
            commandResults = Map(
              Vector(
                "docker",
                "version",
                "--format",
                "{{.Server.Version}}"
              ) -> CozyVideo.VideoCommandResult(0, "25.0\n", ""),
              Vector(
                "docker",
                "image",
                "inspect",
                "example/toolchain:dev"
              ) -> CozyVideo.VideoCommandResult(0, "[]", ""),
              Vector(
                "docker",
                "run",
                "--rm",
                "example/toolchain:dev",
                "textus-toolchain",
                "check",
                "video"
              ) -> CozyVideo
                .VideoCommandResult(0, "textus-toolchain check video: ok\n", "")
            ),
            httpResults = Map(
              "http://voicevox.example/version" -> CozyVideo.VideoHttpResult(
                0,
                "",
                Some("connection refused")
              )
            )
          )

          val out = CozyVideo.inspect(
            CozyVideo.InspectConfig(
              dir.resolve("video_project.json"),
              checkTools = true
            ),
            CozyVideo.VideoToolRegistry.production(probe)
          )

          ((out.contains("toolMode: docker")) shouldBe true)
          ((out.contains("docker-image: available (docker)")) shouldBe true)
          ((out.contains(
            "textus-toolchain-image: available (docker)"
          )) shouldBe true)
          ((out.contains("voicevox: missing (external-service)")) shouldBe true)
          ((out.indexOf("docker-image: available") < out.indexOf(
            "textus-toolchain-image: available"
          )) shouldBe true)
          ((out.indexOf("textus-toolchain-image: available") < out.indexOf(
            "voicevox: missing"
          )) shouldBe true)
          ((out.contains("host.docker.internal")) shouldBe true)
          ((out.contains("ffmpeg: unchecked (docker)")) shouldBe true)
          ((out.contains("remotion-node: unchecked (docker)")) shouldBe true)
          ((out.contains("playwright: unchecked (docker)")) shouldBe true)
          ((out.contains("whisper-cpp: unchecked (docker)")) shouldBe true)
          ((out.contains("python-pillow: unchecked (docker)")) shouldBe true)
          ((!probe.commands.exists(
            _.headOption.contains("ffmpeg")
          )) shouldBe true)
          ((!probe.commands.exists(
            _.headOption.contains("node")
          )) shouldBe true)
          ((!probe.commands.exists(
            _.headOption.contains("python3")
          )) shouldBe true)
        }
      }

      "video inspect docker mode reports image content check failure without failing inspect" in {
        _with_temp_dir("cozy-video-toolchain-image-failure") { dir =>
          _write(dir.resolve("script.json"), _script_json)
          _write(
            dir.resolve("video_project.json"),
            s"""{
           |  "title": "Toolchain Image Failure",
           |  "tools": {
           |    "toolMode": "docker",
           |    "dockerImage": "example/toolchain:dev"
           |  },
           |  "parts": [
           |    {"id": "intro", "type": "dialogue", "script": "script.json"}
           |  ]
           |}
           |""".stripMargin
          )
          val probe = RecordingProbe(
            commandResults = Map(
              Vector(
                "docker",
                "version",
                "--format",
                "{{.Server.Version}}"
              ) -> CozyVideo.VideoCommandResult(0, "25.0\n", ""),
              Vector(
                "docker",
                "image",
                "inspect",
                "example/toolchain:dev"
              ) -> CozyVideo.VideoCommandResult(0, "[]", ""),
              Vector(
                "docker",
                "run",
                "--rm",
                "example/toolchain:dev",
                "textus-toolchain",
                "check",
                "video"
              ) -> CozyVideo.VideoCommandResult(
                1,
                "",
                "missing command: whisper-cli"
              )
            )
          )

          val out = CozyVideo.inspect(
            CozyVideo.InspectConfig(
              dir.resolve("video_project.json"),
              checkTools = true
            ),
            CozyVideo.VideoToolRegistry.production(probe)
          )

          ((out.contains("Cozy Video Inspect")) shouldBe true)
          ((out.contains(
            "textus-toolchain-image: missing (docker)"
          )) shouldBe true)
          ((out.contains("missing command: whisper-cli")) shouldBe true)
          ((out.contains(
            "setup: Rebuild and publish the Textus toolchain image in textus-toolchain-runner, then run: docker pull example/toolchain:dev"
          )) shouldBe true)
        }
      }

      "video inspect production registry reports invalid voicevox URL as missing" in {
        _with_temp_dir("cozy-video-tool-probe-invalid-voicevox") { dir =>
          _write(dir.resolve("script.json"), _script_json)
          _write(
            dir.resolve("video_project.json"),
            s"""{
           |  "title": "Invalid Voicevox",
           |  "tools": {
           |    "voicevoxUrl": "http://bad host"
           |  },
           |  "parts": [
           |    {"id": "intro", "type": "dialogue", "script": "script.json"}
           |  ]
           |}
           |""".stripMargin
          )
          val probe = RecordingProbe()

          val out = CozyVideo.inspect(
            CozyVideo.InspectConfig(
              dir.resolve("video_project.json"),
              checkTools = true
            ),
            CozyVideo.VideoToolRegistry.production(probe)
          )

          ((out.contains("voicevox: missing (external-service)")) shouldBe true)
          ((out.contains("VOICEVOX endpoint URL is invalid")) shouldBe true)
          ((out.contains(
            "setup: Set tools.voicevoxUrl or video.voicevox.url to a valid HTTP URL."
          )) shouldBe true)
          ((probe.httpGets.isEmpty) shouldBe true)
        }
      }

      "video build dry-run can include production tool checks without failing on missing tools" in {
        _with_temp_dir("cozy-video-build-check-tools") { dir =>
          _write(dir.resolve("script.json"), _script_json)
          _write(
            dir.resolve("video_project.json"),
            _project_json("script.json")
          )
          val probe = RecordingProbe()

          val out = CozyVideo.build(
            CozyVideo.BuildConfig(
              dir.resolve("video_project.json"),
              dryRun = true,
              checkTools = true
            ),
            CozyVideo.VideoToolRegistry.production(probe)
          )

          ((out.contains("Cozy Video Build Dry-Run")) shouldBe true)
          ((out.contains("commands:")) shouldBe true)
          ((out.contains("tool checks:")) shouldBe true)
          ((out.contains("docker-toolchain: missing (docker)")) shouldBe true)
          ((out.contains("voicevox: missing (external-service)")) shouldBe true)
        }
      }

      "video inspect supports stubbed tool registry for deterministic SPI tests" in {
        _with_temp_dir("cozy-video-stub-tools") { dir =>
          _write(dir.resolve("script.json"), _script_json)
          _write(
            dir.resolve("video_project.json"),
            _project_json("script.json")
          )
          val registry = CozyVideo.VideoToolRegistry(
            Vector(
              StubProvider(
                CozyVideo.VideoToolCheck(
                  "alpha",
                  CozyVideo.VideoToolMode.Host,
                  CozyVideo.VideoToolStatus.Available,
                  "alpha ok"
                )
              ),
              StubProvider(
                CozyVideo.VideoToolCheck(
                  "beta",
                  CozyVideo.VideoToolMode.Docker,
                  CozyVideo.VideoToolStatus.Missing,
                  "beta missing",
                  Some("install beta")
                )
              ),
              StubProvider(
                CozyVideo.VideoToolCheck(
                  "gamma",
                  CozyVideo.VideoToolMode.ExternalService,
                  CozyVideo.VideoToolStatus.Unchecked,
                  "gamma skipped"
                )
              )
            )
          )

          val out = CozyVideo.inspect(
            CozyVideo.InspectConfig(
              dir.resolve("video_project.json"),
              checkTools = true
            ),
            registry
          )

          ((out.indexOf("alpha: available (host)") < out.indexOf(
            "beta: missing (docker)"
          )) shouldBe true)
          ((out.indexOf("beta: missing (docker)") < out.indexOf(
            "gamma: unchecked (external-service)"
          )) shouldBe true)
          ((out.contains("setup: install beta")) shouldBe true)
        }
      }

      "cozy video inspect is wired through the Cozy CLI and help" in {
        _with_temp_dir("cozy-video-cli") { dir =>
          _write(dir.resolve("script.json"), _script_json)
          _write(
            dir.resolve("video_project.json"),
            _project_json("script.json")
          )
          _write_audio_manifest(
            dir.resolve("build/audio/intro"),
            Vector("title", "description", "summary")
          )
          val out = _capture {
            cozy.Cozy.main(
              Array(
                "video",
                "inspect",
                dir.resolve("video_project.json").toString
              )
            )
          }
          val build = _capture {
            cozy.Cozy.main(
              Array(
                "video",
                "build",
                dir.resolve("video_project.json").toString,
                "--dry-run",
                "--tool-mode=host",
                "--docker-image=cli-image"
              )
            )
          }
          val help = _capture {
            cozy.Cozy.main(Array("--help"))
          }
          val runner = ProfileRenderRunner()
          val render = _capture {
            CozyVideo.execute(
              List(
                "video",
                "render",
                dir.resolve("video_project.json").toString,
                "--renderer=remotion"
              ),
              CozyVideo.VideoToolRegistry(Vector.empty),
              RecordingVoicevoxClient(),
              runner
            )
          }
          val rdf = _capture {
            CozyVideo.execute(
              List(
                "video",
                "rdf",
                dir.resolve("video_project.json").toString,
                "--save",
                dir.resolve("rdf").toString
              ),
              CozyVideo.VideoToolRegistry(Vector.empty)
            )
          }

          ((out.contains("Cozy Video Inspect")) shouldBe true)
          ((out.contains("part[1]: intro")) shouldBe true)
          ((build.contains("Cozy Video Build Dry-Run")) shouldBe true)
          ((build.contains("toolMode: host")) shouldBe true)
          ((build.contains("dockerImage: cli-image")) shouldBe true)
          ((render.contains("Cozy Video Render")) shouldBe true)
          ((render.contains(
            "part.intro: " + dir.resolve("build/parts/intro.mp4").normalize()
          )) shouldBe true)
          ((runner.commands.nonEmpty) shouldBe true)
          ((rdf.contains("Cozy Video RDF")) shouldBe true)
          ((Files.isRegularFile(dir.resolve("rdf/video.ttl"))) shouldBe true)
          ((help.contains("video inspect <project-file>")) shouldBe true)
          ((help.contains(
            "video build <project-file> [--dry-run]"
          )) shouldBe true)
          ((help.contains(
            "video synthesize <script-file> --save <audio-dir>"
          )) shouldBe true)
          ((help.contains(
            "video render <project-file> --renderer=remotion|simple-java2d"
          )) shouldBe true)
          ((help.contains(
            "video transcribe <input-video> --save <dir>"
          )) shouldBe true)
          ((help.contains(
            "video demo-script <input-video> --save <script-file>"
          )) shouldBe true)
          ((help.contains("video replay <script-file>")) shouldBe true)
          ((help.contains(
            "video rdf <project-file> --save <dir>"
          )) shouldBe true)
          ((help.contains("publish-video <slug>.video")) shouldBe true)
          ((help.contains("--check-tools")) shouldBe true)
        }
      }

    }

    "review evidence (Part 5)" which {
      "writes deterministic final-video review evidence from Cozy Remotion props and audio manifests" in {
        _with_temp_dir("cozy-video-review-evidence-part-5") { dir =>
          Given("a final video with matching Cozy Remotion props and audio manifests")
          val project = dir.resolve("video_project.json")
          val save = dir.resolve("review")
          _write_bytes(dir.resolve("build/final.mp4"), Array[Byte](1, 2, 3, 4))
          _write_review_video_manifest(dir.resolve("build/final.mp4"))
          _write(
            project,
            """{
              |  "output": "build/final.mp4",
              |  "renderer": {"engine": "remotion", "fps": 10, "width": 100, "height": 50},
              |  "parts": [{"id": "intro", "type": "dialogue", "script": "script.json"}]
              |}
              |""".stripMargin
          )
          _write(dir.resolve("script.json"), _script_json)
          _write(
            dir.resolve("build/audio/intro/manifest.json"),
            """[
              | {"sceneId":"first/scene","speaker":"narrator","file":"01.wav","leadSilence":0.2,"audioDuration":1.0,"targetDuration":2.0,"tailSilence":0.1},
              | {"sceneId":"section-two","speaker":"narrator","file":"02.wav","leadSilence":0.5,"audioDuration":1.0,"targetDuration":2.0,"tailSilence":0.2},
              | {"sceneId":"last","speaker":"narrator","file":"03.wav","leadSilence":0.0,"audioDuration":1.0,"targetDuration":2.0,"tailSilence":0.0}
              |]
              |""".stripMargin
          )
          _write(
            dir.resolve("target/cozy-video/remotion/intro/props.json"),
            """{
              |  "partId": "intro", "fps": 10, "width": 100, "height": 50,
              |  "timing": {"openingFrames": 5, "contentFrames": 70, "summaryStartFrame": 70, "summaryFrames": 5, "finalPageStartFrame": 75, "finalPageHoldFrames": 3, "totalFrames": 78},
              |  "scenes": [
              |    {"id":"first/scene","speaker":"narrator","line":"First line","text":"First text","caption":"First caption","section":"start","startFrame":0,"durationFrames":20,"leadInFrames":2,"sectionTransitionFrames":0},
              |    {"id":"section-two","speaker":"narrator","line":"Second line","text":"Second text","caption":"Second caption","section":"next","startFrame":20,"durationFrames":30,"leadInFrames":15,"sectionTransitionFrames":10},
              |    {"id":"last","speaker":"narrator","line":"Last line","text":"Last text","caption":"Last caption","section":"next","startFrame":50,"durationFrames":20,"leadInFrames":0,"sectionTransitionFrames":0}
              |  ]
              |}
              |""".stripMargin
          )
          val runner = ReviewEvidenceRunner()

          When("Cozy writes host-mode review evidence")
          val out = CozyVideo.reviewEvidence(
            CozyVideo.ReviewEvidenceConfig(project, save, toolMode = Some("host")),
            CozyVideo.VideoToolRegistry(Vector.empty),
            runner
          )
          val manifest = parser.parse(Files.readString(save.resolve("review-manifest.json"), StandardCharsets.UTF_8)).toOption.get
          val videomanifesthash = MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(dir.resolve("build/manifest.json"))).map("%02x".format(_)).mkString

          Then("the deterministic evidence manifest and its extracted frames describe the review timing")
          out should include("Cozy Video Review Evidence")
          manifest.hcursor.get[String]("schema").toOption shouldBe Some("cozy.video.review-evidence.v1")
          manifest.hcursor.get[String]("status").toOption shouldBe Some("validated")
          manifest.hcursor.downField("videoManifest").get[String]("path").toOption shouldBe Some(dir.resolve("build/manifest.json").toAbsolutePath.normalize().toString)
          manifest.hcursor.downField("videoManifest").get[String]("sha256").toOption shouldBe Some(videomanifesthash)
          manifest.hcursor.downField("videoManifest").get[String]("status").toOption shouldBe Some("validated")
          manifest.hcursor.downField("renderer").get[Int]("fps").toOption shouldBe Some(10)
          manifest.hcursor.downField("parts").downArray.downField("scenes").focus.flatMap(_.asArray).map(_.size) shouldBe Some(3)
          manifest.hcursor.downField("frames").focus.flatMap(_.asArray).map(_.map(_.hcursor.get[String]("kind").toOption)) shouldBe Some(Vector(Some("opening"), Some("summary"), Some("final-page")))
          manifest.hcursor.downField("frames").downArray.get[String]("status").toOption shouldBe Some("validated")
          manifest.hcursor.downField("parts").downArray.downField("scenes").downN(1).downField("frames").focus.flatMap(_.asArray).map(_.map(_.hcursor.get[String]("kind").toOption)) shouldBe Some(Vector(Some("transition"), Some("speech")))
          manifest.hcursor.downField("parts").downArray.downField("scenes").downArray.downField("frames").downArray.get[String]("status").toOption shouldBe Some("validated")
          Files.isRegularFile(save.resolve("frames/part-001-scene-001-first-scene-speech.png")) shouldBe true
          runner.commands.size shouldBe 7
          runner.commands.foreach(command => command.args should contain("ffmpeg"))
          runner.commands.foreach(command => command.args should contain("-ss"))
        }
      }

      "rejects unknown review-evidence options" in {
        _with_temp_dir("cozy-video-review-evidence-options-part-5") { dir =>
          Given("a review-evidence command with an unknown option")
          val project = dir.resolve("video_project.json")
          val save = dir.resolve("review")

          When("Cozy parses the command configuration")
          val error = intercept[RuntimeException] {
            CozyVideo.ReviewEvidenceConfig.create(List(project.toString, "--save=" + save, "--unknown=value"))
          }

          Then("the strict option parser rejects that option")
          error.getMessage should include("Too many arguments: --unknown=value")
        }
      }

      "dispatches the review-evidence command" in {
        _with_temp_dir("cozy-video-review-evidence-dispatch-part-5") { dir =>
          Given("a complete host-mode review-evidence project")
          val project = dir.resolve("video_project.json")
          val save = dir.resolve("review")
          _write_review_evidence_fixture(dir, "build/final.mp4", dir.resolve("build/final.mp4"))

          When("Cozy dispatches video review-evidence")
          val dispatched = _capture {
            CozyVideo.execute(
              List("video", "review-evidence", project.toString, "--save=" + save, "--tool-mode=host"),
              CozyVideo.VideoToolRegistry(Vector.empty),
              RecordingVoicevoxClient(),
              ReviewEvidenceRunner()
            )
          }

          Then("the command reports the generated review evidence")
          dispatched should include("Cozy Video Review Evidence")
        }
      }

      "shows review-evidence in the CLI help" in {
        Given("the Cozy command-line help")

        When("a user requests the help output")
        val help = _capture { cozy.Cozy.main(Array("--help")) }

        Then("the review-evidence command is visible")
        help should include("video review-evidence <project-file> --save=<dir>")
      }

      "uses limited Docker mounts for externally located final video and review evidence" in {
        _with_temp_dir("cozy-video-review-evidence-docker-paths-part-5") { dir =>
          Given("a Docker review-evidence project whose project root, final video, and save directory are distinct locations")
          val project = dir.resolve("video_project.json")
          val finalroot = dir.resolveSibling("cozy-video-review-evidence-docker-paths-final")
          val saveroot = dir.resolveSibling("cozy-video-review-evidence-docker-paths-save")
          _delete(finalroot)
          _delete(saveroot)
          val finalvideo = finalroot.resolve("target/media/final.mp4")
          val save = saveroot.resolve("review")
          _write_bytes(finalvideo, Array[Byte](1, 2, 3, 4))
          _write_review_video_manifest(finalvideo)
          _write(
            project,
            s"""{
              |  "output": "${dir.relativize(finalvideo)}",
              |  "renderer": {"engine": "remotion", "fps": 10, "width": 100, "height": 50},
              |  "parts": [{"id": "intro", "type": "dialogue", "script": "script.json"}]
              |}
              |""".stripMargin
          )
          _write(dir.resolve("script.json"), _script_json)
          _write(
            dir.resolve("build/audio/intro/manifest.json"),
            """[
              | {"sceneId":"first/scene","speaker":"narrator","file":"01.wav","leadSilence":0.2,"audioDuration":1.0,"targetDuration":2.0,"tailSilence":0.1},
              | {"sceneId":"section-two","speaker":"narrator","file":"02.wav","leadSilence":0.5,"audioDuration":1.0,"targetDuration":2.0,"tailSilence":0.2},
              | {"sceneId":"last","speaker":"narrator","file":"03.wav","leadSilence":0.0,"audioDuration":1.0,"targetDuration":2.0,"tailSilence":0.0}
              |]
              |""".stripMargin
          )
          _write(
            dir.resolve("target/cozy-video/remotion/intro/props.json"),
            """{
              |  "partId": "intro", "fps": 10, "width": 100, "height": 50,
              |  "timing": {"openingFrames": 5, "contentFrames": 70, "summaryStartFrame": 70, "summaryFrames": 5, "finalPageStartFrame": 75, "finalPageHoldFrames": 3, "totalFrames": 78},
              |  "scenes": [
              |    {"id":"first/scene","speaker":"narrator","line":"First line","text":"First text","caption":"First caption","section":"start","startFrame":0,"durationFrames":20,"leadInFrames":2,"sectionTransitionFrames":0},
              |    {"id":"section-two","speaker":"narrator","line":"Second line","text":"Second text","caption":"Second caption","section":"next","startFrame":20,"durationFrames":30,"leadInFrames":15,"sectionTransitionFrames":10},
              |    {"id":"last","speaker":"narrator","line":"Last line","text":"Last text","caption":"Last caption","section":"next","startFrame":50,"durationFrames":20,"leadInFrames":0,"sectionTransitionFrames":0}
              |  ]
              |}
              |""".stripMargin
          )

          When("Cozy extracts review evidence through Docker's limited bind mounts")
          val runner = ReviewEvidenceRunner()
          CozyVideo.reviewEvidence(
            CozyVideo.ReviewEvidenceConfig(project, save, toolMode = Some("docker")),
            CozyVideo.VideoToolRegistry(Vector.empty),
            runner
          )
          val manifest = parser.parse(Files.readString(save.resolve("review-manifest.json"), StandardCharsets.UTF_8)).toOption.get
          val manifesttext = Files.readString(save.resolve("review-manifest.json"), StandardCharsets.UTF_8)
          val expectedhash = MessageDigest.getInstance("SHA-256").digest(Array[Byte](1, 2, 3, 4)).map("%02x".format(_)).mkString
          val finalmount = s"${finalvideo.toAbsolutePath.normalize()}:/review-input/final.mp4:ro"
          val savemount = s"${save.toAbsolutePath.normalize()}:/review-output:rw"

          Then("Docker mounts the exact final file read-only and the exact save directory read-write")
          runner.commands should have size 7
          runner.commands.foreach { command =>
            val bindmounts = command.args.sliding(2).collect {
              case Vector("-v", mount) => mount
            }.toVector
            bindmounts shouldBe Vector(finalmount, savemount)
            command.args should contain("--network=none")
            command.args should not contain "-w"
            command.args should not contain "/workspace"
            command.cwd shouldBe dir
            val ffmpegargs = command.args.drop(command.args.indexOf("ffmpeg") + 1)
            ffmpegargs(ffmpegargs.indexOf("-i") + 1) shouldBe "/review-input/final.mp4"
            ffmpegargs.last should startWith ("/review-output/frames/")
            ffmpegargs should not contain finalvideo.toAbsolutePath.normalize().toString
            ffmpegargs should not contain save.resolve("frames").toAbsolutePath.normalize().toString
          }

          And("review artifacts are generated directly in the requested host save directory without project-root staging")
          Files.isRegularFile(save.resolve("review-manifest.json")) shouldBe true
          Files.isRegularFile(save.resolve("frames/part-001-scene-001-first-scene-speech.png")) shouldBe true
          Files.exists(dir.resolve("target/cozy-video/review-evidence/input/final.mp4")) shouldBe false
          Files.exists(dir.resolve("target/cozy-video/review-evidence/frames/part-001-scene-001-first-scene-speech.png")) shouldBe false
          manifest.hcursor.downField("finalVideo").get[String]("path").toOption shouldBe Some(finalvideo.toAbsolutePath.normalize().toString)
          manifest.hcursor.downField("finalVideo").get[String]("sha256").toOption shouldBe Some(expectedhash)
          manifest.hcursor.downField("parts").downArray.downField("scenes").downArray.downField("frames").downArray.get[String]("path").toOption shouldBe Some("frames/part-001-scene-001-first-scene-speech.png")
          manifesttext should not include "/review-input"
          manifesttext should not include "/review-output"
        }
      }

      "rejects a missing final video" in {
        _with_temp_dir("cozy-video-review-evidence-failures-part-5") { dir =>
          Given("a review-evidence project without its final video")
          val project = dir.resolve("video_project.json")
          _write(project, """{"output":"build/final.mp4","parts":[{"id":"intro","type":"dialogue","script":"script.json"}]}""")
          _write(dir.resolve("script.json"), _script_json)

          When("Cozy writes review evidence")
          val missingfinal = intercept[RuntimeException] {
            CozyVideo.reviewEvidence(CozyVideo.ReviewEvidenceConfig(project, dir.resolve("review"), toolMode = Some("host")), CozyVideo.VideoToolRegistry(Vector.empty), ReviewEvidenceRunner())
          }

          Then("the missing final video is rejected")
          missingfinal.getMessage should include("Missing final video")
        }
      }

      "rejects missing Cozy Remotion props" in {
        _with_temp_dir("cozy-video-review-evidence-missing-props-part-5") { dir =>
          Given("a final video without matching Cozy Remotion props")
          val project = dir.resolve("video_project.json")
          _write(project, """{"output":"build/final.mp4","parts":[{"id":"intro","type":"dialogue","script":"script.json"}]}""")
          _write(dir.resolve("script.json"), _script_json)
          _write_bytes(dir.resolve("build/final.mp4"), Array[Byte](1))
          _write_review_video_manifest(dir.resolve("build/final.mp4"))

          When("Cozy writes review evidence")
          val missingprops = intercept[RuntimeException] {
            CozyVideo.reviewEvidence(CozyVideo.ReviewEvidenceConfig(project, dir.resolve("review"), toolMode = Some("host")), CozyVideo.VideoToolRegistry(Vector.empty), ReviewEvidenceRunner())
          }

          Then("the missing props evidence is rejected")
          missingprops.getMessage should include("Missing current Cozy Remotion props for part intro")
        }
      }

      "rejects a multipart review-evidence project with one missing current props file before the runner" in {
        _with_temp_dir("cozy-video-review-evidence-multipart-missing-props-part-5") { dir =>
          Given("a validated two-part review-evidence project whose second part lacks current props")
          val project = dir.resolve("video_project.json")
          val save = dir.resolve("review")
          _write_review_evidence_fixture(dir, "build/final.mp4", dir.resolve("build/final.mp4"), Vector("first", "second"))
          Files.delete(dir.resolve("target/cozy-video/remotion/second/props.json"))
          val runner = ReviewEvidenceRunner()

          When("Cozy validates every renderable part's current props")
          val error = intercept[RuntimeException] {
            CozyVideo.reviewEvidence(CozyVideo.ReviewEvidenceConfig(project, save, toolMode = Some("host")), CozyVideo.VideoToolRegistry(Vector.empty), runner)
          }

          Then("the missing second-part props are rejected before any frame command")
          error.getMessage should include("Missing current Cozy Remotion props for part second")
          runner.commands shouldBe empty
        }
      }

      "reports an ffmpeg extraction failure" in {
        _with_temp_dir("cozy-video-review-evidence-ffmpeg-failure-part-5") { dir =>
          Given("a complete review-evidence project and a failing ffmpeg runner")
          val project = dir.resolve("video_project.json")
          _write(project, """{"output":"build/final.mp4","parts":[{"id":"intro","type":"dialogue","script":"script.json"}]}""")
          _write(dir.resolve("script.json"), _script_json)
          _write_bytes(dir.resolve("build/final.mp4"), Array[Byte](1))
          _write_review_video_manifest(dir.resolve("build/final.mp4"))
          _write(dir.resolve("build/audio/intro/manifest.json"), """[{"sceneId":"one","speaker":null,"file":"01.wav","leadSilence":0.0,"audioDuration":1.0,"targetDuration":1.0,"tailSilence":0.0}]""")
          _write(dir.resolve("target/cozy-video/remotion/intro/props.json"), """{"partId":"intro","fps":10,"width":100,"height":50,"timing":{"openingFrames":0,"contentFrames":10,"summaryStartFrame":10,"summaryFrames":0,"finalPageStartFrame":10,"finalPageHoldFrames":0,"totalFrames":10},"scenes":[{"id":"one","startFrame":0,"durationFrames":10,"leadInFrames":0,"sectionTransitionFrames":0}]}""")

          When("Cozy extracts a review frame")
          val extraction = intercept[RuntimeException] {
            CozyVideo.reviewEvidence(CozyVideo.ReviewEvidenceConfig(project, dir.resolve("review"), toolMode = Some("host")), CozyVideo.VideoToolRegistry(Vector.empty), ReviewEvidenceRunner(fail = true))
          }

          Then("the ffmpeg failure is reported")
          extraction.getMessage should include("ffmpeg review evidence extraction failed")
        }
      }

      "rejects an ffmpeg run that does not create its PNG" in {
        _with_temp_dir("cozy-video-review-evidence-missing-output-part-5") { dir =>
          Given("a complete review-evidence project and a runner without PNG output")
          val project = dir.resolve("video_project.json")
          _write(project, """{"output":"build/final.mp4","parts":[{"id":"intro","type":"dialogue","script":"script.json"}]}""")
          _write(dir.resolve("script.json"), _script_json)
          _write_bytes(dir.resolve("build/final.mp4"), Array[Byte](1))
          _write_review_video_manifest(dir.resolve("build/final.mp4"))
          _write(dir.resolve("build/audio/intro/manifest.json"), """[{"sceneId":"one","speaker":null,"file":"01.wav","leadSilence":0.0,"audioDuration":1.0,"targetDuration":1.0,"tailSilence":0.0}]""")
          _write(dir.resolve("target/cozy-video/remotion/intro/props.json"), """{"partId":"intro","fps":10,"width":100,"height":50,"timing":{"openingFrames":0,"contentFrames":10,"summaryStartFrame":10,"summaryFrames":0,"finalPageStartFrame":10,"finalPageHoldFrames":0,"totalFrames":10},"scenes":[{"id":"one","startFrame":0,"durationFrames":10,"leadInFrames":0,"sectionTransitionFrames":0}]}""")

          When("Cozy extracts a review frame")
          val absent = intercept[RuntimeException] {
            CozyVideo.reviewEvidence(CozyVideo.ReviewEvidenceConfig(project, dir.resolve("review"), toolMode = Some("host")), CozyVideo.VideoToolRegistry(Vector.empty), ReviewEvidenceRunner(writeOutput = false))
          }

          Then("the missing PNG is rejected")
          absent.getMessage should include("did not create PNG")
        }
      }

      "rejects a symbolic-link review manifest without overwriting its target" in {
        _with_temp_dir("cozy-video-review-evidence-manifest-symlink-part-5") { dir =>
          Given("a complete project whose review manifest is a symbolic link to an outside file")
          val project = dir.resolve("video_project.json")
          val save = dir.resolve("review")
          val outside = dir.resolveSibling("cozy-video-review-evidence-manifest-symlink-outside.json")
          _write_review_evidence_fixture(dir, "build/final.mp4", dir.resolve("build/final.mp4"))
          _write(outside, "outside manifest must remain unchanged")
          Files.createDirectories(save)
          Files.createSymbolicLink(save.resolve("review-manifest.json"), outside)
          val runner = ReviewEvidenceRunner()

          When("Cozy writes review evidence")
          val error = intercept[RuntimeException] {
            CozyVideo.reviewEvidence(CozyVideo.ReviewEvidenceConfig(project, save, toolMode = Some("host")), CozyVideo.VideoToolRegistry(Vector.empty), runner)
          }

          Then("the link is rejected and the outside file remains unchanged")
          error.getMessage should include("manifest target must not be a symbolic link")
          runner.commands shouldBe empty
          Files.isSymbolicLink(save.resolve("review-manifest.json")) shouldBe true
          _read(outside) shouldBe "outside manifest must remain unchanged"
        }
      }

      "rejects a final-video symbolic link without changing its outside file" in {
        _with_temp_dir("cozy-video-review-evidence-final-symlink-part-5") { dir =>
          Given("a complete project whose final MP4 is a symbolic link to an outside regular file")
          val project = dir.resolve("video_project.json")
          val save = dir.resolve("review")
          val outside = dir.resolveSibling("cozy-video-review-evidence-final-symlink-outside.mp4")
          val outsidebytes = Array[Byte](9, 8, 7, 6)
          _write_review_evidence_fixture(dir, "build/final.mp4", dir.resolve("build/final.mp4"))
          _write_bytes(outside, outsidebytes)
          Files.delete(dir.resolve("build/final.mp4"))
          Files.createSymbolicLink(dir.resolve("build/final.mp4"), outside)
          val runner = ReviewEvidenceRunner()

          When("Cozy resolves the final video for review evidence")
          val error = intercept[RuntimeException] {
            CozyVideo.reviewEvidence(CozyVideo.ReviewEvidenceConfig(project, save, toolMode = Some("host")), CozyVideo.VideoToolRegistry(Vector.empty), runner)
          }

          Then("the link is rejected before the runner and the outside bytes remain unchanged")
          error.getMessage should include("Final video for review evidence must not be a symbolic link")
          runner.commands shouldBe empty
          Files.readAllBytes(outside).toVector shouldBe outsidebytes.toVector
          Files.deleteIfExists(outside)
        }
      }

      "rejects a review-evidence save symbolic link before creating outside artifacts" in {
        _with_temp_dir("cozy-video-review-evidence-save-symlink-part-5") { dir =>
          Given("a complete project whose --save target is a symbolic link to an outside directory")
          val project = dir.resolve("video_project.json")
          val save = dir.resolve("review")
          val outside = dir.resolveSibling("cozy-video-review-evidence-save-symlink-outside")
          _write_review_evidence_fixture(dir, "build/final.mp4", dir.resolve("build/final.mp4"))
          Files.createDirectories(outside)
          Files.createSymbolicLink(save, outside)
          val runner = ReviewEvidenceRunner()

          When("Cozy resolves the review-evidence save directory")
          val error = intercept[RuntimeException] {
            CozyVideo.reviewEvidence(CozyVideo.ReviewEvidenceConfig(project, save, toolMode = Some("host")), CozyVideo.VideoToolRegistry(Vector.empty), runner)
          }

          Then("the link is rejected before the runner creates an outside manifest or frames")
          error.getMessage should include("Review evidence --save target must not be a symbolic link")
          runner.commands shouldBe empty
          Files.exists(outside.resolve("review-manifest.json")) shouldBe false
          Files.exists(outside.resolve("frames")) shouldBe false
          _delete(outside)
        }
      }

      "rejects a review-evidence frames symbolic link before writing outside PNGs" in {
        _with_temp_dir("cozy-video-review-evidence-frames-symlink-part-5") { dir =>
          Given("a complete project whose save frames directory is a symbolic link to an outside directory")
          val project = dir.resolve("video_project.json")
          val save = dir.resolve("review")
          val outside = dir.resolveSibling("cozy-video-review-evidence-frames-symlink-outside")
          _write_review_evidence_fixture(dir, "build/final.mp4", dir.resolve("build/final.mp4"))
          Files.createDirectories(save)
          Files.createDirectories(outside)
          Files.createSymbolicLink(save.resolve("frames"), outside)
          val runner = ReviewEvidenceRunner()

          When("Cozy prepares the review-evidence frames directory")
          val error = intercept[RuntimeException] {
            CozyVideo.reviewEvidence(CozyVideo.ReviewEvidenceConfig(project, save, toolMode = Some("host")), CozyVideo.VideoToolRegistry(Vector.empty), runner)
          }

          Then("the link is rejected before the runner writes an outside PNG")
          error.getMessage should include("Review evidence frames target must not be a symbolic link")
          runner.commands shouldBe empty
          Files.list(outside).iterator().asScala.filter(_.getFileName.toString.endsWith(".png")).toVector shouldBe empty
          _delete(outside)
        }
      }

      "rejects the filesystem root as the review-evidence save directory" in {
        _with_temp_dir("cozy-video-review-evidence-root-save-part-5") { dir =>
          Given("a review-evidence project with a real final video")
          val project = dir.resolve("video_project.json")
          _write(project, """{"output":"build/final.mp4","parts":[]}""")
          _write_bytes(dir.resolve("build/final.mp4"), Array[Byte](1))

          When("Cozy uses the filesystem root as --save")
          val error = intercept[RuntimeException] {
            CozyVideo.reviewEvidence(CozyVideo.ReviewEvidenceConfig(project, Paths.get("/"), toolMode = Some("host")), CozyVideo.VideoToolRegistry(Vector.empty), ReviewEvidenceRunner())
          }

          Then("the root save target is rejected")
          error.getMessage should include("must not be the filesystem root")
        }
      }

      "rejects a final video canonically contained by the review-evidence save directory" in {
        _with_temp_dir("cozy-video-review-evidence-final-overlap-part-5") { dir =>
          Given("a final video inside the requested review-evidence save directory")
          val project = dir.resolve("video_project.json")
          val save = dir.resolve("review")
          _write(project, """{"output":"review/final.mp4","parts":[]}""")
          _write_bytes(save.resolve("final.mp4"), Array[Byte](1))

          When("Cozy resolves review-evidence paths")
          val error = intercept[RuntimeException] {
            CozyVideo.reviewEvidence(CozyVideo.ReviewEvidenceConfig(project, save, toolMode = Some("host")), CozyVideo.VideoToolRegistry(Vector.empty), ReviewEvidenceRunner())
          }

          Then("the canonical overlap is rejected")
          error.getMessage should include("final video must not be inside the --save directory")
        }
      }

      "uses only final and save Docker mounts for an in-project final video" in {
        _with_temp_dir("cozy-video-review-evidence-docker-default-part-5") { dir =>
          Given("a realistic Docker review-evidence project with an in-project final video")
          val project = dir.resolve("video_project.json")
          val finalvideo = dir.resolve("build/final.mp4")
          val save = dir.resolve("review")
          _write_review_evidence_fixture(dir, "build/final.mp4", finalvideo)
          val runner = ReviewEvidenceRunner()

          When("Cozy extracts review evidence through Docker")
          CozyVideo.reviewEvidence(CozyVideo.ReviewEvidenceConfig(project, save, toolMode = Some("docker")), CozyVideo.VideoToolRegistry(Vector.empty), runner)

          Then("every command has only exact final-read-only and save-read-write binds")
          val expected = Vector(s"${finalvideo.toAbsolutePath.normalize()}:/review-input/final.mp4:ro", s"${save.toAbsolutePath.normalize()}:/review-output:rw")
          runner.commands.foreach { command =>
            command.args.sliding(2).collect { case Vector("-v", mount) => mount }.toVector shouldBe expected
            command.args should contain("--network=none")
            command.args should not contain "-w"
            command.args should not contain "/workspace"
          }
        }
      }

      "keeps colliding top-frame part ids in distinct files with matching hashes" in {
        _with_temp_dir("cozy-video-review-evidence-top-frame-collision-part-5") { dir =>
          Given("two reviewable parts whose sanitized ids collide")
          val project = dir.resolve("video_project.json")
          val save = dir.resolve("review")
          _write_review_evidence_fixture(dir, "build/final.mp4", dir.resolve("build/final.mp4"), Vector("a+b", "a-b"))

          When("Cozy extracts their top review frames")
          CozyVideo.reviewEvidence(CozyVideo.ReviewEvidenceConfig(project, save, toolMode = Some("host")), CozyVideo.VideoToolRegistry(Vector.empty), ReviewEvidenceRunner(distinctOutput = true))
          val topframes = parser.parse(_read(save.resolve("review-manifest.json"))).toOption.get.hcursor.downField("frames").focus.flatMap(_.asArray).map(_.toVector).getOrElse(Vector.empty)

          Then("each top frame has a stable indexed path and matches its recorded hash")
          val paths = topframes.map(_.hcursor.get[String]("path").toOption.get)
          paths shouldBe Vector("frames/opening-part-001-a-b.png", "frames/opening-part-002-a-b.png")
          paths.distinct should have size 2
          topframes.foreach { frame =>
            val path = frame.hcursor.get[String]("path").toOption.get
            val expectedhash = frame.hcursor.get[String]("sha256").toOption.get
            MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(save.resolve(path))).map("%02x".format(_)).mkString shouldBe expectedhash
          }
        }
      }

      "rejects duplicate Cozy Remotion props scene ids before frame extraction" in {
        _with_temp_dir("cozy-video-review-evidence-duplicate-props-scenes-part-5") { dir =>
          Given("a validated review-evidence project with duplicate props scene ids")
          val project = dir.resolve("video_project.json")
          val save = dir.resolve("review")
          _write_review_evidence_fixture(dir, "build/final.mp4", dir.resolve("build/final.mp4"))
          _write(dir.resolve("target/cozy-video/remotion/intro/props.json"), """{"partId":"intro","fps":10,"width":100,"height":50,"timing":{"openingFrames":0,"contentFrames":20,"summaryStartFrame":20,"summaryFrames":0,"finalPageStartFrame":20,"finalPageHoldFrames":0,"totalFrames":20},"scenes":[{"id":"scene","startFrame":0,"durationFrames":10,"leadInFrames":0,"sectionTransitionFrames":0},{"id":"scene","startFrame":10,"durationFrames":10,"leadInFrames":0,"sectionTransitionFrames":0}]}""")
          val runner = ReviewEvidenceRunner()

          When("Cozy validates props and audio evidence")
          val error = intercept[RuntimeException] {
            CozyVideo.reviewEvidence(CozyVideo.ReviewEvidenceConfig(project, save, toolMode = Some("host")), CozyVideo.VideoToolRegistry(Vector.empty), runner)
          }

          Then("duplicate props ids are rejected before the runner")
          error.getMessage should include("duplicate scene ids")
          runner.commands shouldBe empty
        }
      }

      "rejects props timing that does not match rendered audio timing" in {
        _with_temp_dir("cozy-video-review-evidence-timing-mismatch-part-5") { dir =>
          Given("a validated review-evidence project with a duration frame mismatch")
          val project = dir.resolve("video_project.json")
          val save = dir.resolve("review")
          _write_review_evidence_fixture(dir, "build/final.mp4", dir.resolve("build/final.mp4"))
          _write(dir.resolve("target/cozy-video/remotion/intro/props.json"), """{"partId":"intro","fps":10,"width":100,"height":50,"timing":{"openingFrames":1,"contentFrames":10,"summaryStartFrame":11,"summaryFrames":0,"finalPageStartFrame":11,"finalPageHoldFrames":0,"totalFrames":11},"scenes":[{"id":"scene","startFrame":0,"durationFrames":9,"leadInFrames":0,"sectionTransitionFrames":0}]}""")
          val runner = ReviewEvidenceRunner()

          When("Cozy validates props timing against the audio manifest")
          val error = intercept[RuntimeException] {
            CozyVideo.reviewEvidence(CozyVideo.ReviewEvidenceConfig(project, save, toolMode = Some("host")), CozyVideo.VideoToolRegistry(Vector.empty), runner)
          }

          Then("the timing mismatch is rejected before the runner")
          error.getMessage should include("timing does not match audio manifest")
          runner.commands shouldBe empty
        }
      }

      "rejects a first-part scene that spills through its opening frames into the next part" in {
        _with_temp_dir("cozy-video-review-evidence-opening-bound-part-5") { dir =>
          Given("a two-part validated project whose first scene exceeds its total after opening frames")
          val project = dir.resolve("video_project.json")
          val save = dir.resolve("review")
          _write_review_evidence_fixture(dir, "build/final.mp4", dir.resolve("build/final.mp4"), Vector("first", "second"))
          _write(dir.resolve("build/audio/first/manifest.json"), """[{"sceneId":"scene","speaker":null,"file":"01.wav","leadSilence":0.0,"audioDuration":0.4,"targetDuration":0.4,"tailSilence":0.0}]""")
          _write(dir.resolve("target/cozy-video/remotion/first/props.json"), """{"partId":"first","fps":10,"width":100,"height":50,"timing":{"openingFrames":5,"contentFrames":10,"summaryStartFrame":10,"summaryFrames":0,"finalPageStartFrame":10,"finalPageHoldFrames":0,"totalFrames":10},"scenes":[{"id":"scene","startFrame":6,"durationFrames":4,"leadInFrames":0,"sectionTransitionFrames":0}]}""")
          val runner = ReviewEvidenceRunner()

          When("Cozy validates the first part's scene bounds")
          val error = intercept[RuntimeException] {
            CozyVideo.reviewEvidence(CozyVideo.ReviewEvidenceConfig(project, save, toolMode = Some("host")), CozyVideo.VideoToolRegistry(Vector.empty), runner)
          }

          Then("the opening-inclusive scene spill is rejected before the runner")
          error.getMessage should include("exceeds declared totalFrames")
          runner.commands shouldBe empty
        }
      }

      "rejects a stale validated project video manifest before frame extraction" in {
        _with_temp_dir("cozy-video-review-evidence-stale-video-manifest-part-5") { dir =>
          Given("a complete project whose validated video manifest has a stale final hash")
          val project = dir.resolve("video_project.json")
          val finalvideo = dir.resolve("build/final.mp4")
          val save = dir.resolve("review")
          _write_review_evidence_fixture(dir, "build/final.mp4", finalvideo)
          _write(finalvideo.getParent.resolve("manifest.json"), s"""{"status":"validated","outputPath":"${finalvideo.toAbsolutePath.normalize()}","finalVideoSha256":"stale"}""")
          val runner = ReviewEvidenceRunner()

          When("Cozy validates provenance before extracting frames")
          val error = intercept[RuntimeException] {
            CozyVideo.reviewEvidence(CozyVideo.ReviewEvidenceConfig(project, save, toolMode = Some("host")), CozyVideo.VideoToolRegistry(Vector.empty), runner)
          }

          Then("the stale manifest is rejected before the runner")
          error.getMessage should include("finalVideoSha256 does not match final video")
          runner.commands shouldBe empty
        }
      }
    }

    "demo replay" which {
      "video demo-script generates replay script from selector event log and transcript" in {
        _with_temp_dir("cozy-video-demo-script-events") { dir =>
          val input = dir.resolve("demo.mp4")
          val events = dir.resolve("events.json")
          val transcript = dir.resolve("transcript.json")
          val save = dir.resolve("build/demo-script.json")
          _write_bytes(input, Array[Byte](1, 2, 3))
          _write(
            events,
            """{
          |  "viewport": {"width": 1440, "height": 900},
          |  "steps": [
          |    {"kind": "navigate", "url": "http://example.test/"},
          |    {"kind": "click", "selector": "#start", "timestampMs": 250},
          |    {"kind": "input", "selector": "#name", "text": "alice"},
          |    {"kind": "keydown", "selector": "#name", "key": "Enter"},
          |    {"kind": "wait", "durationMs": 500}
          |  ]
          |}
          |""".stripMargin
          )
          _write(
            transcript,
            """{
          |  "segments": [
          |    {"index": 1, "start": 0.0, "end": 1.0, "text": "Open the start page"}
          |  ]
          |}
          |""".stripMargin
          )

          val out = CozyVideo.demoScript(
            CozyVideo.DemoScriptConfig(
              input,
              save,
              eventsFile = Some(events),
              transcriptFile = Some(transcript)
            )
          )

          ((out.contains("Cozy Video Demo Script")) shouldBe true)
          ((out.contains("manualReview: true")) shouldBe true)
          val json = parser.parse(_read(save)).toOption.get
          ((json.hcursor
            .downField("schema")
            .as[String]
            .toOption
            .contains("cozy.video.replay-script.v1")) shouldBe true)
          ((json.hcursor
            .downField("manualReview")
            .as[Boolean]
            .toOption
            .contains(true)) shouldBe true)
          ((json.hcursor
            .downField("viewport")
            .downField("width")
            .as[Int]
            .toOption
            .contains(1440)) shouldBe true)
          val steps =
            json.hcursor.downField("steps").focus.flatMap(_.asArray).get
          ((steps
            .map(_.hcursor.downField("kind").as[String].toOption.get)
            .take(5) == Vector(
            "goto",
            "click",
            "fill",
            "press",
            "wait"
          )) shouldBe true)
          ((steps.exists(
            _.hcursor
              .downField("note")
              .as[String]
              .toOption
              .contains("Open the start page")
          )) shouldBe true)
          ((json.hcursor
            .downField("sourceSha256")
            .as[String]
            .toOption
            .exists(_.nonEmpty)) shouldBe true)
        }
      }

      "video demo-script produces manual-review drafts for HAR and video-only inputs" in {
        _with_temp_dir("cozy-video-demo-script-manual") { dir =>
          val input = dir.resolve("demo.mp4")
          val har = dir.resolve("demo.har")
          _write_bytes(input, Array[Byte](1, 2, 3))
          _write(
            har,
            """{
          |  "log": {
          |    "entries": [
          |      {"_resourceType": "document", "request": {"method": "GET", "url": "http://example.test/home"}}
          |    ]
          |  }
          |}
          |""".stripMargin
          )

          CozyVideo.demoScript(
            CozyVideo.DemoScriptConfig(
              input,
              dir.resolve("build/har-demo-script.json"),
              harFile = Some(har)
            )
          )
          CozyVideo.demoScript(
            CozyVideo.DemoScriptConfig(
              input,
              dir.resolve("build/video-only-demo-script.json")
            )
          )

          val harjson = parser
            .parse(_read(dir.resolve("build/har-demo-script.json")))
            .toOption
            .get
          val videojson = parser
            .parse(_read(dir.resolve("build/video-only-demo-script.json")))
            .toOption
            .get
          ((harjson.hcursor
            .downField("manualReview")
            .as[Boolean]
            .toOption
            .contains(true)) shouldBe true)
          ((harjson.noSpaces
            .contains("http://example.test/home")) shouldBe true)
          ((videojson.hcursor
            .downField("manualReview")
            .as[Boolean]
            .toOption
            .contains(true)) shouldBe true)
          ((videojson.noSpaces
            .contains("Manual review is required")) shouldBe true)
        }
      }

      "video demo-script validates inputs options and missing files" in {
        _with_temp_dir("cozy-video-demo-script-errors") { dir =>
          val input = dir.resolve("demo.mp4")
          _write_bytes(input, Array[Byte](1, 2, 3))

          val missinginput = intercept[RuntimeException] {
            CozyVideo.demoScript(
              CozyVideo.DemoScriptConfig(
                dir.resolve("missing.mp4"),
                dir.resolve("build/demo-script.json")
              )
            )
          }
          ((missinginput.getMessage
            .contains("Missing input video")) shouldBe true)

          val missingsave = intercept[RuntimeException] {
            CozyVideo.execute(
              List("video", "demo-script", input.toString),
              CozyVideo.VideoToolRegistry(Vector.empty)
            )
          }
          ((missingsave.getMessage.contains("save")) shouldBe true)

          val missingevents = intercept[RuntimeException] {
            CozyVideo.demoScript(
              CozyVideo.DemoScriptConfig(
                input,
                dir.resolve("build/demo-script.json"),
                eventsFile = Some(dir.resolve("missing-events.json"))
              )
            )
          }
          ((missingevents.getMessage
            .contains("Missing selector event log")) shouldBe true)

          val unknown = intercept[RuntimeException] {
            CozyVideo.execute(
              List(
                "video",
                "demo-script",
                input.toString,
                "--save",
                dir.resolve("build/demo-script.json").toString,
                "--unknown"
              ),
              CozyVideo.VideoToolRegistry(Vector.empty)
            )
          }
          ((unknown.getMessage.contains("unknown")) shouldBe true)
        }
      }

      "video replay dry-runs and executes generated Playwright plans" in {
        _with_temp_dir("cozy-video-replay") { dir =>
          val script = dir.resolve("build/demo-script.json")
          val output = dir.resolve("build/replay.webm")
          _write(
            script,
            """{
          |  "schema": "cozy.video.replay-script.v1",
          |  "sourceVideo": "demo.mp4",
          |  "sourceSha256": "abc123",
          |  "manualReview": false,
          |  "viewport": {"width": 1280, "height": 720},
          |  "steps": [
          |    {"kind": "goto", "url": "http://example.test/"},
          |    {"kind": "click", "selector": "#start"}
          |  ]
          |}
          |""".stripMargin
          )
          val dryrun = CozyVideo.replay(
            CozyVideo.ReplayConfig(
              script,
              dryRun = true,
              projectRootOverride = Some(dir)
            ),
            CozyVideo.VideoToolRegistry(Vector.empty),
            ReplayRunner()
          )
          val runner = ReplayRunner()
          val execute = CozyVideo.replay(
            CozyVideo.ReplayConfig(
              script,
              saveFile = Some(output),
              toolMode = Some("host"),
              projectRootOverride = Some(dir)
            ),
            CozyVideo.VideoToolRegistry(Vector.empty),
            runner
          )

          ((dryrun.contains("Cozy Video Replay Dry-Run")) shouldBe true)
          ((dryrun.contains("'docker' 'run' '--rm'")) shouldBe true)
          ((dryrun.contains("replay.playwright")) shouldBe true)
          ((execute.contains("Cozy Video Replay")) shouldBe true)
          ((execute.contains("toolMode: host")) shouldBe true)
          ((runner.commands.size == 1) shouldBe true)
          ((runner.commands.head.args.head == "node") shouldBe true)
          ((Files.isRegularFile(output)) shouldBe true)
          ((Files.isRegularFile(
            dir.resolve("target/cozy-video/replay/demo-script/manifest.json")
          )) shouldBe true)
          val manifest = _read(
            dir.resolve("target/cozy-video/replay/demo-script/manifest.json")
          )
          ((manifest.contains("cozy.video.replay-manifest.v1")) shouldBe true)
          ((manifest.contains("abc123")) shouldBe true)
          ((manifest.contains("replay.playwright")) shouldBe true)
        }
      }

      "video replay validates inputs runner failures and tool checks" in {
        _with_temp_dir("cozy-video-replay-errors") { dir =>
          val script = dir.resolve("build/demo-script.json")
          _write(
            script,
            """{"schema":"cozy.video.replay-script.v1","steps":[{"kind":"goto","url":"http://example.test/"}]}"""
          )

          val missing = intercept[RuntimeException] {
            CozyVideo.replay(
              CozyVideo.ReplayConfig(
                dir.resolve("missing.json"),
                projectRootOverride = Some(dir)
              ),
              CozyVideo.VideoToolRegistry(Vector.empty),
              ReplayRunner()
            )
          }
          ((missing.getMessage
            .contains("Missing video replay script file")) shouldBe true)

          val unknown = intercept[RuntimeException] {
            CozyVideo.execute(
              List("video", "replay", script.toString, "--unknown"),
              CozyVideo.VideoToolRegistry(Vector.empty)
            )
          }
          ((unknown.getMessage.contains("unknown")) shouldBe true)

          val failure = intercept[RuntimeException] {
            CozyVideo.replay(
              CozyVideo.ReplayConfig(
                script,
                toolMode = Some("host"),
                projectRootOverride = Some(dir)
              ),
              CozyVideo.VideoToolRegistry(Vector.empty),
              ReplayRunner(fail = true)
            )
          }
          ((failure.getMessage
            .contains("Playwright replay failed")) shouldBe true)

          val mp4output = intercept[RuntimeException] {
            CozyVideo.replay(
              CozyVideo.ReplayConfig(
                script,
                saveFile = Some(dir.resolve("build/replay.mp4")),
                toolMode = Some("host"),
                projectRootOverride = Some(dir)
              ),
              CozyVideo.VideoToolRegistry(Vector.empty),
              ReplayRunner()
            )
          }
          ((mp4output.getMessage.contains("must use .webm")) shouldBe true)

          val hostcheck = intercept[RuntimeException] {
            CozyVideo.replay(
              CozyVideo.ReplayConfig(
                script,
                checkTools = true,
                toolMode = Some("host"),
                projectRootOverride = Some(dir)
              ),
              CozyVideo.VideoToolRegistry(
                Vector(
                  StubProvider(
                    CozyVideo.VideoToolCheck(
                      "playwright",
                      CozyVideo.VideoToolMode.Host,
                      CozyVideo.VideoToolStatus.Missing,
                      "missing playwright",
                      Some("install playwright")
                    )
                  )
                )
              ),
              ReplayRunner()
            )
          }
          ((hostcheck.getMessage.contains("playwright")) shouldBe true)
          ((hostcheck.getMessage.contains("install playwright")) shouldBe true)
        }
      }

      "video rdf includes replay script and replay manifest provenance" in {
        _with_temp_dir("cozy-video-rdf-replay") { dir =>
          _write(dir.resolve("script.json"), _script_json)
          _write(
            dir.resolve("video_project.json"),
            _project_json("script.json")
          )
          _write(
            dir.resolve("build/demo-script.json"),
            """{
          |  "schema": "cozy.video.replay-script.v1",
          |  "sourceVideo": "demo.mp4",
          |  "sourceSha256": "abc123",
          |  "manualReview": false,
          |  "steps": [
          |    {"kind": "goto", "url": "http://example.test/"},
          |    {"kind": "click", "selector": "#start", "timestampMs": 250}
          |  ]
          |}
          |""".stripMargin
          )
          _write(
            dir.resolve("target/cozy-video/replay/demo-script/manifest.json"),
            """{
          |  "schema": "cozy.video.replay-manifest.v1",
          |  "toolMode": "host",
          |  "dockerImage": "ghcr.io/asami/textus-toolchain:latest",
          |  "outputVideo": "build/replay.webm"
          |}
          |""".stripMargin
          )

          CozyVideo.rdf(
            CozyVideo.RdfConfig(
              dir.resolve("video_project.json"),
              dir.resolve("rdf")
            )
          )

          val turtle = _read(dir.resolve("rdf/video.ttl"))
          ((turtle.contains("cozy-video:VideoReplay")) shouldBe true)
          ((turtle.contains("cozy-video:VideoReplayStep")) shouldBe true)
          ((turtle.contains(
            "cozy-video:sourceSha256 \"abc123\""
          )) shouldBe true)
          ((turtle.contains("cozy-video:selector \"#start\"")) shouldBe true)
          ((turtle.contains(
            "cozy-video:artifactKind \"replay-manifest\""
          )) shouldBe true)
        }
      }

    }

    "video publication" which {
      "video publisher resolves .video descriptors from YAML and JSON" in {
        _with_temp_dir("cozy-video-publisher-resolve") { dir =>
          Given("a .video package described by YAML")
          val yamlpkg = dir.resolve("intro.video")
          _write(yamlpkg.resolve("index.dox"), "# Intro\n")
          _write(yamlpkg.resolve("script.json"), _script_json)
          _write(
            yamlpkg.resolve("video.yaml"),
            """title: Intro Video
          |version: 0.1.0
          |publish:
          |  module: textus
          |""".stripMargin
          )

          When("Cozy resolves the YAML video descriptor")
          val yaml = CozyVideoPublisher.resolve(
            CozyVideoPublisher.PublishVideoConfig(
              yamlpkg,
              dir.resolve("publication"),
              dir.resolve("warehouse"),
              None,
              force = false
            )
          )

          Then("the YAML descriptor is normalized with defaults")
          yaml.name shouldBe "intro"
          yaml.title shouldBe "Intro Video"
          yaml.version shouldBe "0.1.0"
          yaml.articlePath shouldBe "index.dox"
          yaml.scriptPath shouldBe "script.json"
          yaml.renderer shouldBe "simple-java2d"
          yaml.toolMode shouldBe "docker"
          yaml.module shouldBe "textus"
          yaml.publicPath shouldBe "videos/intro.mp4"

          And("a .video package described by JSON")
          val jsonpkg = dir.resolve("custom.video")
          _write(jsonpkg.resolve("article.dox"), "# Custom\n")
          _write(jsonpkg.resolve("custom-script.json"), _script_json)
          _write(
            jsonpkg.resolve("video.json"),
            """{
          |  "video": {"name": "custom-video"},
          |  "title": "Custom Video",
          |  "article": "article.dox",
          |  "script": "custom-script.json",
          |  "renderer": {"engine": "remotion"},
          |  "toolMode": "host",
          |  "publish": {"module": "custom-module", "publicPath": "videos/custom.mp4"}
          |}
          |""".stripMargin
          )

          When("Cozy resolves the JSON video descriptor with a CLI version")
          val json = CozyVideoPublisher.resolve(
            CozyVideoPublisher.PublishVideoConfig(
              jsonpkg,
              dir.resolve("publication"),
              dir.resolve("warehouse"),
              Some("0.2.0"),
              force = false
            )
          )

          Then(
            "the JSON descriptor keeps explicit descriptor fields and CLI version precedence"
          )
          json.name shouldBe "custom-video"
          json.version shouldBe "0.2.0"
          json.articlePath shouldBe "article.dox"
          json.scriptPath shouldBe "custom-script.json"
          json.renderer shouldBe "remotion"
          json.toolMode shouldBe "host"
          json.module shouldBe "custom-module"
          json.publicPath shouldBe "videos/custom.mp4"
        }
      }

      "video publisher rejects .video.d source packages" in {
        _with_temp_dir("cozy-video-publisher-reject") { dir =>
          Given("a generated work directory using the reserved .video.d suffix")
          val pkg = dir.resolve("bad.video.d")
          Files.createDirectories(pkg)

          When("Cozy resolves the package as a video source package")
          val e = intercept[RuntimeException] {
            CozyVideoPublisher.resolve(
              CozyVideoPublisher.PublishVideoConfig(
                pkg,
                dir.resolve("publication"),
                dir.resolve("warehouse"),
                None,
                force = false
              )
            )
          }

          Then("the reserved generated/work suffix is rejected")
          e.getMessage should include("*.video.d is reserved")
        }
      }

      "publish-video writes warehouse video artifact and publication metadata outside source package" in {
        _with_temp_dir("cozy-video-publisher") { dir =>
          Given(
            "a valid .video package with generated audio, render, RDF, captions, and transcript outputs"
          )
          val pkg = dir.resolve("src/main/doxsite/concepts/tutorial.video")
          val publication = dir.resolve("src/main/publication")
          val warehouse = dir.resolve("warehouse")
          _write(pkg.resolve("index.dox"), "# Tutorial\n")
          _write(pkg.resolve("script.json"), _script_json)
          _write(
            pkg.resolve("assets/legacy/nested-overlay.svg"),
            "<svg><text>legacy overlay</text></svg>\n"
          )
          _write(
            pkg.resolve("video.yaml"),
            """video:
          |  name: tutorial
          |title: Textus Tutorial
          |version: 0.1.0
          |renderer: simple-java2d
          |toolMode: docker
          |publish:
          |  module: textus
          |  publicPath: videos/tutorial.mp4
          |""".stripMargin
          )
          val runner = PublishingRunner()

          When(
            "Cozy publishes the video package into the warehouse-backed repository"
          )
          val result = CozyVideoPublisher.publish(
            CozyVideoPublisher.PublishVideoConfig(
              pkg,
              publication,
              warehouse,
              None,
              force = false
            ),
            RecordingVoicevoxClient(),
            runner
          )

          Then(
            "the video artifact and sidecars are deployed under warehouse/repository"
          )
          val artifact = warehouse.resolve(
            "repository/video/textus/0.1.0/tutorial-0.1.0.mp4"
          )
          result.warehouseArtifact shouldBe artifact.toAbsolutePath.normalize()
          artifact should be_regular_file
          artifact.resolveSibling(
            "tutorial-0.1.0.manifest.json"
          ) should be_regular_file
          artifact.resolveSibling("tutorial-0.1.0.ttl") should be_regular_file
          artifact.resolveSibling("tutorial-0.1.0.jsonld") should be_regular_file
          artifact.resolveSibling("tutorial-0.1.0.srt") should be_regular_file
          artifact.resolveSibling(
            "tutorial-0.1.0.transcript.json"
          ) should be_regular_file
          publication.resolve("tutorial.json") should be_regular_file

          And("the publication bundle registers stable metadata paths")
          val bundle = play.api.libs.json.Json
            .parse(_read(publication.resolve("tutorial.json")))
          val entries =
            (bundle \ "entries").as[Vector[play.api.libs.json.JsObject]]
          val paths = entries.map(entry => (entry \ "path").as[String]).toSet
          paths should contain("metadata/video/tutorial/0.1.0/manifest.json")
          paths should contain("metadata/video/tutorial/0.1.0/rdf.json")
          paths should contain("metadata/video/tutorial/latest.json")
          val video = entries
            .find(entry =>
              (entry \ "path")
                .as[String] == "metadata/videos/tutorial/metadata.json"
            )
            .get
          val videometadata = (video \ "metadata" \ "video")
          (videometadata \ "type").as[String] shouldBe "video"
          (videometadata \ "name").as[String] shouldBe "tutorial"
          (videometadata \ "articlePath").as[String] shouldBe "index.dox"
          (videometadata \ "sourcePackage")
            .as[String] shouldBe "concepts/tutorial.video"
          (videometadata \ "scriptPath").as[String] shouldBe "script.json"
          (videometadata \ "artifact" \ "warehousePath").as[
            String
          ] shouldBe "repository/video/textus/0.1.0/tutorial-0.1.0.mp4"
          (videometadata \ "artifact" \ "publicPath").as[
            String
          ] shouldBe "repository/video/textus/0.1.0/tutorial-0.1.0.mp4"
          (videometadata \ "artifact" \ "sitePublicPath")
            .as[String] shouldBe "videos/tutorial.mp4"
          (videometadata \ "artifact" \ "repositoryPublicPath").as[
            String
          ] shouldBe "repository/video/textus/0.1.0/tutorial-0.1.0.mp4"
          (videometadata \ "rdf" \ "registryPath")
            .as[String] shouldBe "metadata/video/tutorial/0.1.0/rdf"
          (videometadata \ "rdf" \ "manifestPath")
            .as[String] shouldBe "metadata/video/tutorial/0.1.0/manifest"
          (videometadata \ "rdf" \ "latestPath")
            .as[String] shouldBe "metadata/video/tutorial/latest"
          (videometadata \ "rdf" \ "files").toOption shouldBe empty
          (videometadata \ "captions" \ "warehousePath").as[
            String
          ] shouldBe "repository/video/textus/0.1.0/tutorial-0.1.0.srt"
          (videometadata \ "captions" \ "publicPath").as[
            String
          ] shouldBe "repository/video/textus/0.1.0/tutorial-0.1.0.srt"
          (videometadata \ "transcript" \ "warehousePath")
            .as[String] shouldBe "repository/video/textus/0.1.0/tutorial-0.1.0.transcript.json"
          (videometadata \ "transcript" \ "publicPath")
            .as[String] shouldBe "repository/video/textus/0.1.0/tutorial-0.1.0.transcript.json"
          (videometadata \ "narration" \ "providers")
            .as[Vector[String]] shouldBe Vector("voicevox")
          (videometadata \ "narration" \ "executionModes")
            .as[Vector[String]] shouldBe Vector("external-http")
          (videometadata \ "narration" \ "voices" \ 0 \ "id")
            .as[String] shouldBe "99"
          (videometadata \ "narration" \ "audioFormats" \ 0 \ "sampleRate")
            .as[Int] shouldBe 24000
          (videometadata \ "narration" \ "manifests")
            .as[Vector[String]] shouldBe Vector("build/audio/main/manifest.json")
          play.api.libs.json.Json.stringify(
            videometadata.get
          ) should not include ("target/cozy-video")

          And(
            "RDF and artifact registry entries include all published sidecars"
          )
          val rdfentry = entries
            .find(entry =>
              (entry \ "path")
                .as[String] == "metadata/video/tutorial/0.1.0/rdf.json"
            )
            .get
          val rdfmetadata = (rdfentry \ "metadata")
          (rdfmetadata \ "type").as[String] shouldBe "video-rdf"
          (rdfmetadata \ "registryPath")
            .as[String] shouldBe "metadata/video/tutorial/0.1.0/rdf"
          (rdfmetadata \ "files" \ "turtle" \ "warehousePath").as[
            String
          ] shouldBe "repository/video/textus/0.1.0/tutorial-0.1.0.ttl"
          (rdfmetadata \ "files" \ "jsonLd" \ "warehousePath").as[
            String
          ] shouldBe "repository/video/textus/0.1.0/tutorial-0.1.0.jsonld"
          (rdfmetadata \ "files" \ "manifest" \ "warehousePath")
            .as[String] shouldBe "repository/video/textus/0.1.0/tutorial-0.1.0.rdf-manifest.json"

          val latestentry = entries
            .find(entry =>
              (entry \ "path")
                .as[String] == "metadata/video/tutorial/latest.json"
            )
            .get
          (latestentry \ "metadata" \ "video" \ "version")
            .as[String] shouldBe "0.1.0"
          (latestentry \ "metadata" \ "video" \ "rdfPath")
            .as[String] shouldBe "metadata/video/tutorial/0.1.0/rdf"

          val registryentry = entries
            .find(entry =>
              (entry \ "path")
                .as[String] == "metadata/video/tutorial/0.1.0/manifest.json"
            )
            .get
          (registryentry \ "metadata" \ "narration" \ "providers")
            .as[Vector[String]] shouldBe Vector("voicevox")

          val artifactentry = entries
            .find(entry =>
              (entry \ "path")
                .as[String] == "metadata/artifacts/repository/tutorial.json"
            )
            .get
          val artifactfiles =
            (artifactentry \ "metadata" \ "artifact" \ "files")
              .as[Vector[play.api.libs.json.JsObject]]
          val artifacttypes =
            artifactfiles.map(x => (x \ "type").as[String]).toSet
          artifacttypes should contain("video")
          artifacttypes should contain("manifest")
          artifacttypes should contain("turtle")
          artifacttypes should contain("jsonld")
          artifacttypes should contain("rdf-manifest")
          artifacttypes should contain("captions")
          artifacttypes should contain("transcript")

          And("generated artifacts remain outside the .video source package")
          val sourcefiles = Files
            .walk(pkg)
            .iterator()
            .asScala
            .toVector
            .filter(Files.isRegularFile(_))
            .map(_.getFileName.toString)
          sourcefiles should not_contain_where[String](_.endsWith(".mp4"))
          sourcefiles should not_contain_where[String](_.endsWith(".ttl"))
          sourcefiles should not_contain_where[String](_.endsWith(".jsonld"))
          sourcefiles should not_contain_where[String](_.endsWith(".srt"))
          runner.commands should contain_where[RecordingCommand](
            _.args.contains("python3")
          )
          runner.commands should contain_where[RecordingCommand](
            _.args.contains("ffmpeg")
          )
          runner.commands should contain_where[RecordingCommand](
            _.args.contains("ffprobe")
          )

          And("arbitrary legacy package assets survive workspace preparation")
          _read(
            result.workspaceRoot.resolve("source/assets/legacy/nested-overlay.svg")
          ) should include("legacy overlay")

          And(
            "existing release artifacts are protected unless force is supplied"
          )
          val exists = intercept[RuntimeException] {
            CozyVideoPublisher.publish(
              CozyVideoPublisher.PublishVideoConfig(
                pkg,
                publication,
                warehouse,
                None,
                force = false
              ),
              RecordingVoicevoxClient(),
              PublishingRunner()
            )
          }
          exists.getMessage should include("already exists")
        }
      }

      "publish-video writes repository artifacts to a configured repository root" in {
        _with_temp_dir("cozy-video-publisher-repository-root") { dir =>
          Given("a video source package and a direct public repository root")
          val pkg = dir.resolve("src/main/doxsite/concepts/tutorial.video")
          val publication = dir.resolve("src/main/publication")
          val warehouse = dir.resolve("warehouse")
          val repository = dir.resolve("public-repository")
          _write(pkg.resolve("index.dox"), "# Tutorial\n")
          _write(pkg.resolve("script.json"), _script_json)
          _write(
            pkg.resolve("video.yaml"),
            """video:
          |  name: tutorial
          |title: Textus Tutorial
          |version: 0.1.0
          |renderer: simple-java2d
          |toolMode: docker
          |publish:
          |  module: textus
          |  publicPath: videos/tutorial.mp4
          |""".stripMargin
          )

          When(
            "Cozy publishes the video package with an injected repository root"
          )
          val result = CozyVideoPublisher.publish(
            CozyVideoPublisher.PublishVideoConfig(
              pkg,
              publication,
              warehouse,
              None,
              force = false,
              Some(repository)
            ),
            RecordingVoicevoxClient(),
            PublishingRunner()
          )

          Then(
            "the artifact is written directly under the configured repository root"
          )
          val artifact =
            repository.resolve("video/textus/0.1.0/tutorial-0.1.0.mp4")
          result.warehouseArtifact shouldBe artifact.toAbsolutePath.normalize()
          artifact should be_regular_file
          warehouse.resolve(
            "repository/video/textus/0.1.0/tutorial-0.1.0.mp4"
          ) shouldNot exist_path

          And("publication metadata keeps public repository-relative paths")
          val bundle = _read(publication.resolve("tutorial.json"))
          bundle should include(
            "\"warehousePath\" : \"repository/video/textus/0.1.0/tutorial-0.1.0.mp4\""
          )
          bundle should include(
            "\"publicPath\" : \"repository/video/textus/0.1.0/tutorial-0.1.0.mp4\""
          )
          bundle should not include ("repository/repository/video")
        }
      }

      "publish-video metadata is consumed by SmartDox site rendering" in {
        _with_temp_dir("cozy-video-publisher-smartdox") { dir =>
          Given("a .video source package and an empty publication registry")
          val doxsite = dir.resolve("src/main/doxsite")
          val pkg = doxsite.resolve("concepts/tutorial.video")
          val publication = dir.resolve("src/main/publication")
          val warehouse = dir.resolve("warehouse")
          _write(
            pkg.resolve("index.dox"),
            "# Tutorial\n\nThis is a video article.\n"
          )
          _write(pkg.resolve("script.json"), _script_json)
          _write(
            pkg.resolve("video.yaml"),
            """video:
          |  name: tutorial
          |title: Textus Tutorial
          |version: 0.1.0
          |renderer: simple-java2d
          |toolMode: docker
          |publish:
          |  module: textus
          |  publicPath: videos/tutorial.mp4
          |""".stripMargin
          )

          When("Cozy publishes the video package into publication metadata")
          CozyVideoPublisher.publish(
            CozyVideoPublisher.PublishVideoConfig(
              pkg,
              publication,
              warehouse,
              None,
              force = false
            ),
            RecordingVoicevoxClient(),
            PublishingRunner()
          )

          And(
            "SmartDox renders the BoK source with the generated publication metadata"
          )
          val env = org.goldenport.cli.Environment.createJaJp()
          val config =
            org.smartdox.generator.Config(org.goldenport.cli.Config.buildJaJp())
          val ctx =
            org.smartdox.generator.Context(env, config, env.contextFoundation)
          val generator = new org.smartdox.generators.AntoraGenerator(
            ctx,
            org.smartdox.doxsite.DoxSite.Config.default,
            Some(publication.toFile)
          )
          val result = generator.generate(
            org.goldenport.realm.Realm.create(doxsite.toFile)
          )
          val article = result
            .get("antora.d/docs/concepts/modules/ROOT/pages/tutorial.adoc")
            .collect { case m: org.goldenport.realm.Realm.StringData =>
              m.string
            }
            .getOrElse("")

          Then("the generated article embeds the published video metadata")
          article should include("Tutorial")
          article should include("This is a video article.")
          article should include("smartdox-video-publication")
          article should include("<video")
          article should include(
            "src=\"/repository/video/textus/0.1.0/tutorial-0.1.0.mp4\""
          )
          result.get(
            "antora.d/docs/concepts/modules/ROOT/pages/tutorial.video/index.adoc"
          ) shouldBe empty

          And("the .video source package remains free of generated artifacts")
          val sourcefiles = Files
            .walk(pkg)
            .iterator()
            .asScala
            .toVector
            .filter(Files.isRegularFile(_))
            .map(_.getFileName.toString)
          sourcefiles should not_contain_where[String](_.endsWith(".mp4"))
          sourcefiles should not_contain_where[String](_.endsWith(".ttl"))
          sourcefiles should not_contain_where[String](_.endsWith(".jsonld"))
          sourcefiles should not_contain_where[String](_.endsWith(".srt"))
        }
      }

    }

  }

  private def _with_temp_dir(name: String)(body: Path => Unit): Unit = {
    val root = Paths
      .get(sys.props("user.dir"))
      .toAbsolutePath
      .normalize()
      .resolve("target/test-generated/video")
      .resolve(name)
    _delete(root)
    Files.createDirectories(root)
    body(root)
  }

  private def _write(path: Path, text: String): Unit = {
    Files.createDirectories(path.getParent)
    Files.writeString(path, text, StandardCharsets.UTF_8)
  }

  private def _write_bytes(path: Path, bytes: Array[Byte]): Unit = {
    Files.createDirectories(path.getParent)
    Files.write(path, bytes)
  }

  private def _write_review_evidence_fixture(
    dir: Path,
    output: String,
    finalvideo: Path,
    partids: Vector[String] = Vector("intro")
  ): Unit = {
    _write_bytes(finalvideo, Array[Byte](1, 2, 3, 4))
    _write_review_video_manifest(finalvideo)
    val parts = partids.map(id => s"""{"id":"$id","type":"dialogue","script":"script.json"}""").mkString(",")
    _write(
      dir.resolve("video_project.json"),
      s"""{"output":"$output","renderer":{"engine":"remotion","fps":10,"width":100,"height":50},"parts":[$parts]}"""
    )
    _write(dir.resolve("script.json"), _script_json)
    partids.foreach { id =>
      _write(dir.resolve(s"build/audio/$id/manifest.json"), """[{"sceneId":"scene","speaker":null,"file":"01.wav","leadSilence":0.0,"audioDuration":1.0,"targetDuration":1.0,"tailSilence":0.0}]""")
      _write(dir.resolve(s"target/cozy-video/remotion/$id/props.json"), s"""{"partId":"$id","fps":10,"width":100,"height":50,"timing":{"openingFrames":1,"contentFrames":10,"summaryStartFrame":11,"summaryFrames":0,"finalPageStartFrame":11,"finalPageHoldFrames":0,"totalFrames":11},"scenes":[{"id":"scene","startFrame":0,"durationFrames":10,"leadInFrames":0,"sectionTransitionFrames":0}]}""")
    }
  }

  private def _write_review_video_manifest(finalvideo: Path): Unit = {
    val hash = MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(finalvideo)).map("%02x".format(_)).mkString
    _write(
      finalvideo.getParent.resolve("manifest.json"),
      s"""{"status":"validated","outputPath":"${finalvideo.toAbsolutePath.normalize()}","finalVideoSha256":"$hash"}"""
    )
  }

  private def _read(path: Path): String =
    Files.readString(path, StandardCharsets.UTF_8)

  private def _delete(path: Path): Unit =
    if (Files.exists(path)) {
      Files.walk(path).iterator().asScala.toVector.reverse.foreach(Files.delete)
    }

  private def _capture(body: => Unit): String = {
    val out = new ByteArrayOutputStream()
    Console.withOut(out) {
      body
    }
    out.toString(StandardCharsets.UTF_8.name())
  }

}

object CozyVideoSpec {
  private def _decode_renderer(configuration: String): Either[io.circe.Error, CozyVideo.VideoRenderer] =
    parser.decode[CozyVideo.VideoRenderer](configuration)(CozyVideo.VideoRenderer.decoder)

  final case class StubProvider(result: CozyVideo.VideoToolCheck)
      extends CozyVideo.VideoToolProvider {
    def check(context: CozyVideo.VideoToolContext): CozyVideo.VideoToolCheck =
      result
  }

  final case class VoicevoxCall(
      kind: String,
      baseUrl: String,
      text: Option[String] = None,
      speakerId: Option[Int] = None
  )

  final case class RecordingVoicevoxClient(
      speakersJson: Json = Json.arr(
        Json.obj(
          "name" -> Json.fromString("ずんだもん"),
          "styles" -> Json.arr(
            Json.obj("name" -> Json.fromString("ノーマル"), "id" -> Json.fromInt(3))
          )
        )
      ),
      failSpeakers: Boolean = false,
      audioBytes: Array[Byte] = _wav_bytes(0.2)
  ) extends CozyVideo.VoicevoxClient {
    val calls = ArrayBuffer.empty[VoicevoxCall]
    private val _audio_queries = ArrayBuffer.empty[Json]
    def audioQueries: ArrayBuffer[Json] = _audio_queries

    def speakers(baseurl: String): Json = {
      calls += VoicevoxCall("speakers", baseurl)
      if (failSpeakers)
        throw new RuntimeException("VOICEVOX speakers failed")
      speakersJson
    }

    def audioQuery(baseurl: String, text: String, speakerid: Int): Json = {
      calls += VoicevoxCall("audio_query", baseurl, Some(text), Some(speakerid))
      Json.obj(
        "text" -> Json.fromString(text),
        "speaker" -> Json.fromInt(speakerid)
      )
    }

    def synthesis(
        baseurl: String,
        speakerid: Int,
        audioquery: Json
    ): Array[Byte] = {
      calls += VoicevoxCall("synthesis", baseurl, speakerId = Some(speakerid))
      _audio_queries += audioquery
      audioBytes
    }
  }

  final case class RecordingProbe(
      commandResults: Map[Vector[String], CozyVideo.VideoCommandResult] =
        Map.empty,
      httpResults: Map[String, CozyVideo.VideoHttpResult] = Map.empty
  ) extends CozyVideo.VideoToolProbe {
    val commands = ArrayBuffer.empty[Vector[String]]
    val httpGets = ArrayBuffer.empty[String]
    val existsChecks = ArrayBuffer.empty[Path]

    def command(
        args: Vector[String],
        cwd: Path
    ): CozyVideo.VideoCommandResult = {
      commands += args
      commandResults.getOrElse(
        args,
        CozyVideo.VideoCommandResult(127, "", s"missing: ${args.mkString(" ")}")
      )
    }

    def httpGet(uri: URI): CozyVideo.VideoHttpResult = {
      httpGets += uri.toString
      httpResults.getOrElse(
        uri.toString,
        CozyVideo.VideoHttpResult(0, "", Some("missing endpoint"))
      )
    }

    def exists(path: Path): Boolean = {
      existsChecks += path
      Files.exists(path)
    }
  }

  final case class RecordingCommand(args: Vector[String], cwd: Path)

  final case class RecordingRunner(
      result: CozyVideo.VideoCommandResult =
        CozyVideo.VideoCommandResult(0, "ok", "")
  ) extends CozyVideo.VideoProcessRunner {
    val commands = ArrayBuffer.empty[RecordingCommand]

    def run(args: Vector[String], cwd: Path): CozyVideo.VideoCommandResult = {
      commands += RecordingCommand(args, cwd)
      result
    }
  }

  final case class ReviewEvidenceRunner(
      fail: Boolean = false,
      writeOutput: Boolean = true,
      distinctOutput: Boolean = false
  ) extends CozyVideo.VideoProcessRunner {
    val commands = ArrayBuffer.empty[RecordingCommand]

    def run(args: Vector[String], cwd: Path): CozyVideo.VideoCommandResult = {
      commands += RecordingCommand(args, cwd)
      if (fail)
        CozyVideo.VideoCommandResult(1, "", "ffmpeg failed")
      else {
        if (writeOutput)
          Files.write(_command_path(cwd, args, args.last), if (distinctOutput) Array(commands.size.toByte) else Array[Byte](0, 1, 2, 3))
        CozyVideo.VideoCommandResult(0, "ffmpeg ok", "")
      }
    }
  }

  final case class RenderingRunner(
      failTool: Option[String] = None
  ) extends CozyVideo.VideoProcessRunner {
    val commands = ArrayBuffer.empty[RecordingCommand]

    def run(args: Vector[String], cwd: Path): CozyVideo.VideoCommandResult = {
      commands += RecordingCommand(args, cwd)
      if (failTool.exists(args.contains))
        CozyVideo.VideoCommandResult(1, "", s"${failTool.get} failed")
      else {
        if (args.contains("python3"))
          Files.writeString(
            _command_path(
              cwd,
              args.find(_.endsWith("render_frame.py")).get
            ).getParent.resolve("frame.png"),
            "png",
            StandardCharsets.UTF_8
          )
        if (args.contains("ffmpeg"))
          Files.write(_command_path(cwd, args.last), Array[Byte](0, 0, 0, 0))
        CozyVideo.VideoCommandResult(0, "ok", "")
      }
    }
  }

  final case class AssemblyRunner(
      failTool: Option[String] = None,
      invalidProbeJson: Boolean = false
  ) extends CozyVideo.VideoProcessRunner {
    val commands = ArrayBuffer.empty[RecordingCommand]

    def run(args: Vector[String], cwd: Path): CozyVideo.VideoCommandResult = {
      commands += RecordingCommand(args, cwd)
      if (failTool.exists(args.contains))
        CozyVideo.VideoCommandResult(1, "", s"${failTool.get} failed")
      else if (args.contains("ffmpeg")) {
        Files.write(_command_path(cwd, args.last), Array[Byte](0, 0, 0, 0))
        CozyVideo.VideoCommandResult(0, "ffmpeg ok", "")
      } else if (args.contains("ffprobe")) {
        if (invalidProbeJson)
          CozyVideo.VideoCommandResult(0, "not json", "")
        else
          CozyVideo.VideoCommandResult(
            0,
            """{"format":{"duration":"1.000"},"streams":[]}""",
            ""
          )
      } else {
        CozyVideo.VideoCommandResult(0, "ok", "")
      }
    }
  }

  final case class PublishingRunner() extends CozyVideo.VideoProcessRunner {
    val commands = ArrayBuffer.empty[RecordingCommand]

    def run(args: Vector[String], cwd: Path): CozyVideo.VideoCommandResult = {
      commands += RecordingCommand(args, cwd)
      if (args.contains("python3")) {
        val script = args
          .find(_.endsWith("render_frame.py"))
          .map(_command_path(cwd, _))
          .get
        Files.writeString(
          script.getParent.resolve("frame.png"),
          "png",
          StandardCharsets.UTF_8
        )
        CozyVideo.VideoCommandResult(0, "python ok", "")
      } else if (args.contains("ffmpeg")) {
        Files.write(_command_path(cwd, args.last), Array[Byte](0, 0, 0, 0))
        CozyVideo.VideoCommandResult(0, "ffmpeg ok", "")
      } else if (args.contains("ffprobe")) {
        Files.createDirectories(cwd.resolve("build"))
        Files.writeString(
          cwd.resolve("build/captions.srt"),
          "1\n00:00:00,000 --> 00:00:01,000\ncaption\n",
          StandardCharsets.UTF_8
        )
        Files.writeString(
          cwd.resolve("build/transcript.json"),
          """{"schema":"cozy.video.transcript.v1","segments":[]}""",
          StandardCharsets.UTF_8
        )
        CozyVideo.VideoCommandResult(
          0,
          """{"format":{"duration":"1.000"},"streams":[]}""",
          ""
        )
      } else {
        CozyVideo.VideoCommandResult(0, "ok", "")
      }
    }
  }

  final case class TranscriptionRunner(
      failTool: Option[String] = None
  ) extends CozyVideo.VideoProcessRunner {
    val commands = ArrayBuffer.empty[RecordingCommand]

    def run(args: Vector[String], cwd: Path): CozyVideo.VideoCommandResult = {
      commands += RecordingCommand(args, cwd)
      if (failTool.exists(args.contains))
        CozyVideo.VideoCommandResult(1, "", s"${failTool.get} failed")
      else if (args.contains("ffmpeg")) {
        Files.write(_command_path(cwd, args.last), Array[Byte](0, 0, 0, 0))
        CozyVideo.VideoCommandResult(0, "ffmpeg ok", "")
      } else if (args.contains("whisper-cli") && args.contains("--version")) {
        CozyVideo.VideoCommandResult(0, "whisper-cli 1.7.6", "")
      } else if (args.contains("whisper-cli")) {
        val base = _command_path(cwd, args(args.indexOf("-of") + 1))
        Files.createDirectories(base.getParent)
        Files.writeString(
          base.resolveSibling(base.getFileName.toString + ".json"),
          _whisper_json,
          StandardCharsets.UTF_8
        )
        Files.writeString(
          base.resolveSibling(base.getFileName.toString + ".srt"),
          "stub srt",
          StandardCharsets.UTF_8
        )
        CozyVideo.VideoCommandResult(0, "whisper ok", "")
      } else {
        CozyVideo.VideoCommandResult(0, "ok", "")
      }
    }
  }

  final case class ReplayRunner(
      fail: Boolean = false
  ) extends CozyVideo.VideoProcessRunner {
    val commands = ArrayBuffer.empty[RecordingCommand]

    def run(args: Vector[String], cwd: Path): CozyVideo.VideoCommandResult = {
      commands += RecordingCommand(args, cwd)
      if (fail)
        CozyVideo.VideoCommandResult(1, "", "playwright failed")
      else {
        args.find(_.endsWith("replay.mjs")).foreach { script =>
          val workdir = _command_path(cwd, script).getParent
          val props = parser
            .parse(
              Files.readString(
                workdir.resolve("props.json"),
                StandardCharsets.UTF_8
              )
            )
            .toOption
            .get
          props.hcursor.downField("outputPath").as[String].toOption.foreach {
            output =>
              Files.write(_command_path(cwd, output), Array[Byte](1, 2, 3))
          }
        }
        CozyVideo.VideoCommandResult(0, "playwright ok", "")
      }
    }
  }

  private val _whisper_json: String =
    """{
      |  "segments": [
      |    {"start": 0.0, "end": 1.25, "text": "Hello world"},
      |    {"start": 1.25, "end": 2.5, "text": "Second line"}
      |  ]
      |}
      |""".stripMargin

  private def _command_path(cwd: Path, value: String): Path =
    if (value.startsWith("/workspace/"))
      cwd.resolve(value.stripPrefix("/workspace/")).normalize()
    else {
      val path = Paths.get(value).normalize()
      if (path.isAbsolute) path else cwd.resolve(path).normalize()
    }

  private def _command_path(cwd: Path, args: Vector[String], value: String): Path =
    args.sliding(2).collectFirst {
      case Vector("-v", mount) if mount.endsWith(":/review-output:rw") && value.startsWith("/review-output/") =>
        Path.of(mount.stripSuffix(":/review-output:rw")).resolve(value.stripPrefix("/review-output/")).normalize()
    }.getOrElse(_command_path(cwd, value))

  private def _wav_bytes(duration: Double): Array[Byte] = {
    val samplerate = 24000
    val frames = math.max(1, (duration * samplerate).toInt)
    val data = Array.fill(frames * 2)(0.toByte)
    val out = new ByteArrayOutputStream()
    _write_ascii(out, "RIFF")
    _write_int_le(out, 36 + data.length)
    _write_ascii(out, "WAVE")
    _write_ascii(out, "fmt ")
    _write_int_le(out, 16)
    _write_short_le(out, 1)
    _write_short_le(out, 1)
    _write_int_le(out, samplerate)
    _write_int_le(out, samplerate * 2)
    _write_short_le(out, 2)
    _write_short_le(out, 16)
    _write_ascii(out, "data")
    _write_int_le(out, data.length)
    out.write(data)
    out.toByteArray
  }

  private def _write_ascii(out: ByteArrayOutputStream, value: String): Unit =
    out.write(value.getBytes(StandardCharsets.US_ASCII))

  private def _write_int_le(out: ByteArrayOutputStream, value: Int): Unit = {
    out.write(value & 0xff)
    out.write((value >>> 8) & 0xff)
    out.write((value >>> 16) & 0xff)
    out.write((value >>> 24) & 0xff)
  }

  private def _write_short_le(out: ByteArrayOutputStream, value: Int): Unit = {
    out.write(value & 0xff)
    out.write((value >>> 8) & 0xff)
  }

  private def _write_audio_manifest(
      dir: Path,
      scenes: Vector[String],
      combined: Option[String] = None
  ): Unit = {
    Files.createDirectories(dir)
    scenes.zipWithIndex.foreach { case (scene, index) =>
      Files.write(dir.resolve(f"${index + 1}%02d-$scene.wav"), _wav_bytes(0.2))
    }
    combined.foreach { name =>
      Files.write(dir.resolve(name), _wav_bytes(scenes.size.toDouble * 0.2))
    }
    Files.writeString(
      dir.resolve("manifest.json"),
      _audio_manifest_json(scenes),
      StandardCharsets.UTF_8
    )
  }

  private def _audio_manifest_json(scenes: Vector[String]): String =
    scenes.zipWithIndex
      .map { case (scene, index) =>
        s"""{"sceneId":"$scene","speaker":null,"file":"${f"${index + 1}%02d-$scene.wav"}","leadSilence":0.0,"audioDuration":0.2,"targetDuration":1.0,"tailSilence":0.0}"""
      }
      .mkString("[", ",", "]")

  private def _project_json(script: String): String =
    s"""{
       |  "title": "Sample Video",
       |  "parts": [
       |    {"id": "intro", "type": "dialogue", "script": "$script"}
       |  ]
       |}
       |""".stripMargin

  private def _project_json_with_tools(
      script: String,
      whispermodel: String
  ): String =
    s"""{
       |  "title": "Sample Video",
       |  "tools": {
       |    "whisperModel": "$whispermodel"
       |  },
       |  "parts": [
       |    {"id": "intro", "type": "dialogue", "script": "$script"}
       |  ]
       |}
       |""".stripMargin

  private def _project_yaml(script: String): String =
    s"""title: Sample Video
       |parts:
       |  - id: intro
       |    type: dialogue
       |    script: $script
       |""".stripMargin

  private def _project_hocon(script: String): String =
    s"""title = "Sample Video"
       |parts = [{ id = intro, type = dialogue, script = "$script" }]
       |""".stripMargin

  private def _project_xml(script: String): String =
    s"""<project title="Sample Video">
       |  <parts id="intro" type="dialogue">
       |    <script>$script</script>
       |  </parts>
       |</project>
       |""".stripMargin

  private val _script_json: String =
    """{
      |  "title": "Intro Script",
      |  "voice": {"fallbackSpeakerId": 99},
      |  "scenes": [
      |    {"id": "title", "duration": 4.0, "line": "Title"},
      |    {"id": "book", "targetDuration": 9.0, "subscenes": [
      |      {"id": "description", "duration": 5.0, "line": "Description"},
      |      {"id": "summary", "targetDuration": 6.0, "line": "Summary"}
      |    ]}
      |  ]
      |}
      |""".stripMargin

  private val _voicevox_script_json: String =
    """{
      |  "title": "VOICEVOX Script",
      |  "voice": {
      |    "speakerName": "Missing Top Voice",
      |    "styleName": "Plain",
      |    "fallbackSpeakerId": 99,
      |    "volumeScale": 0.8
      |  },
      |  "pronunciations": {
      |    "World": "Cozy"
      |  },
      |  "voiceTextNormalization": {
      |    "removeSpaces": true
      |  },
      |  "characters": {
      |    "hero": {
      |      "voice": {
      |        "speakerName": "Character Voice",
      |        "styleName": "Normal",
      |        "fallbackSpeakerId": 11,
      |        "speedScale": 1.2
      |      }
      |    }
      |  },
      |  "scenes": [
      |    {"id": "intro", "speaker": "hero", "duration": 0.6, "leadSilence": 0.1, "line": "Hello World"},
      |    {"id": "fallback", "duration": 0.4, "line": "Top Line"},
      |    {"id": "silent", "duration": 0.2, "silent": true}
      |  ]
      |}
      |""".stripMargin
}
