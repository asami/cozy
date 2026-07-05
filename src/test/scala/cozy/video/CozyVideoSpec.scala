package cozy.video

import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path, Paths}
import scala.collection.JavaConverters._
import scala.collection.mutable.ArrayBuffer
import java.net.URI
import io.circe.Json
import io.circe.parser
import org.scalatest.GivenWhenThen
import org.scalatest.wordspec.AnyWordSpec
import cozy.CozySpecVocabulary

/*
 * @since   Jun. 18, 2026
 *  version Jun. 24, 2026
 * @version Jul.  6, 2026
 * @author  ASAMI, Tomoharu
 */
final class CozyVideoSpec
    extends AnyWordSpec
    with GivenWhenThen
    with CozySpecVocabulary {
  import CozyVideoSpec._

  "Cozy Video" should {
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

      "video build assembles rendered parts with ffmpeg and validates with ffprobe" in {
        _with_temp_dir("cozy-video-build-final-docker") { dir =>
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

          val out = CozyVideo.build(
            CozyVideo.BuildConfig(
              dir.resolve("video_project.json"),
              dryRun = false,
              checkTools = false
            ),
            CozyVideo.VideoToolRegistry(Vector.empty),
            runner
          )

          ((out.contains("Cozy Video Build")) shouldBe true)
          ((out.contains(
            "output: " + dir.resolve("build/final.mp4").normalize()
          )) shouldBe true)
          ((out.contains(
            "manifest: " + dir.resolve("build/manifest.json").normalize()
          )) shouldBe true)
          ((out.contains("parts: 2")) shouldBe true)
          ((runner.commands.size == 2) shouldBe true)
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
          ((runner.commands(0).args.contains("ffmpeg")) shouldBe true)
          ((runner
            .commands(0)
            .args
            .contains(
              "/workspace/target/cozy-video/ffmpeg/concat.txt"
            )) shouldBe true)
          ((runner
            .commands(0)
            .args
            .contains("/workspace/build/final.mp4")) shouldBe true)
          ((runner.commands(1).args.contains("ffprobe")) shouldBe true)
          ((runner
            .commands(1)
            .args
            .contains("/workspace/build/final.mp4")) shouldBe true)
          val concat = _read(dir.resolve("target/cozy-video/ffmpeg/concat.txt"))
          ((concat.contains(
            "file '/workspace/build/parts/lecture.mp4'"
          )) shouldBe true)
          ((concat.contains(
            "file '/workspace/build/parts/board.mp4'"
          )) shouldBe true)
          ((Files.isRegularFile(dir.resolve("build/final.mp4"))) shouldBe true)
          val manifest = _read(dir.resolve("build/manifest.json"))
          ((manifest.contains("\"outputPath\"")) shouldBe true)
          ((manifest.contains("\"partOutputs\"")) shouldBe true)
          ((manifest.contains("\"concatListPath\"")) shouldBe true)
          ((manifest.contains("\"ffprobe\"")) shouldBe true)
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
            """{"tools": {"voicevoxUrl": "http://script.example"}, "scenes": [{"id": "s1", "duration": 0.2, "line": "A"}]}"""
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
            """{"scenes": [{"id": "s1", "duration": 0.2, "line": "A"}]}"""
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

      "video synthesize fails explicitly for missing script save option and voicevox errors" in {
        _with_temp_dir("cozy-video-synthesize-errors") { dir =>
          _write(
            dir.resolve("script.json"),
            """{"scenes": [{"id": "s1", "duration": 0.2, "line": "A"}]}"""
          )

          val missingsave = intercept[Throwable] {
            CozyVideo.execute(
              List("video", "synthesize", dir.resolve("script.json").toString),
              CozyVideo.VideoToolRegistry(Vector.empty),
              RecordingVoicevoxClient()
            )
          }
          ((missingsave.getMessage.contains("Missing --save")) shouldBe true)

          val missingfile = intercept[Throwable] {
            CozyVideo.synthesize(
              CozyVideo.SynthesizeConfig(
                dir.resolve("missing.json"),
                dir.resolve("audio")
              ),
              RecordingVoicevoxClient()
            )
          }
          ((missingfile.getMessage
            .contains("Missing video script file")) shouldBe true)

          val voicevoxfailure = intercept[Throwable] {
            CozyVideo.synthesize(
              CozyVideo.SynthesizeConfig(
                dir.resolve("script.json"),
                dir.resolve("audio")
              ),
              RecordingVoicevoxClient(failSpeakers = true)
            )
          }
          ((voicevoxfailure.getMessage
            .contains("VOICEVOX speakers failed")) shouldBe true)

          _write(
            dir.resolve("unsafe-scene.json"),
            """{"scenes": [{"id": "../escape", "duration": 0.2, "line": "A"}]}"""
          )
          val unsafescene = intercept[Throwable] {
            CozyVideo.synthesize(
              CozyVideo.SynthesizeConfig(
                dir.resolve("unsafe-scene.json"),
                dir.resolve("audio-unsafe")
              ),
              RecordingVoicevoxClient()
            )
          }
          ((unsafescene.getMessage.contains("Invalid scene id")) shouldBe true)

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
          ((invalidurl.getMessage
            .contains("VOICEVOX speakers failed")) shouldBe true)
        }
      }

    }

    "part rendering" which {
      "video render remotion renders all renderable parts through a runner" in {
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
          val runner = RecordingRunner()

          val out = CozyVideo.render(
            CozyVideo
              .RenderConfig(dir.resolve("video_project.json"), "remotion"),
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
          val runner = RecordingRunner()

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
          val runner = RecordingRunner()
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
      failSpeakers: Boolean = false
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
      _wav_bytes(0.2)
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
