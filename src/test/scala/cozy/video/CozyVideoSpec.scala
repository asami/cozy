package cozy.video

import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path, Paths}
import scala.collection.JavaConverters._
import scala.collection.mutable.ArrayBuffer
import java.net.URI
import io.circe.Json
import io.circe.parser
import org.scalatest.funsuite.AnyFunSuite

/*
 * @since   Jun. 18, 2026
 * @version Jun. 19, 2026
 * @author  ASAMI, Tomoharu
 */
final class CozyVideoSpec extends AnyFunSuite {
  import CozyVideoSpec._

  test("video inspect accepts JSON, YAML, HOCON, and XML project files") {
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
        val out = CozyVideo.inspect(CozyVideo.InspectConfig(dir.resolve(name), checkTools = false), CozyVideo.VideoToolRegistry(Vector.empty))

        assert(out.contains("Cozy Video Inspect"))
        assert(out.contains("title: Sample Video"))
        assert(out.contains("parts: 1"))
        assert(out.contains("part[1]: intro"))
        assert(out.contains("type: dialogue"))
        assert(out.contains("scriptStatus: found"))
        assert(out.contains("scenes: 2"))
        assert(out.contains("expandedScenes: 3"))
        assert(out.contains("estimatedDuration: 15.00"))
      }
    }
  }

  test("video inspect reports mixed part plans and unsupported part types") {
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

      val out = CozyVideo.inspect(CozyVideo.InspectConfig(dir.resolve("video_project.json"), checkTools = false), CozyVideo.VideoToolRegistry(Vector.empty))

      assert(out.contains("parts: 4"))
      assert(out.contains("part[1]: lecture"))
      assert(out.contains("type: dialogue"))
      assert(out.contains("renderer: engine=remotion, strategy=slide"))
      assert(out.contains("output: " + dir.resolve("01/build/part.mp4").normalize()))
      assert(out.contains("audioDir: " + dir.resolve("01/build/audio").normalize()))
      assert(out.contains("part[3]: demo"))
      assert(out.contains("type: web-demo"))
      assert(out.contains("steps: steps.json"))
      assert(out.contains("recordDir: " + dir.resolve("03/build/recording").normalize()))
      assert(out.contains("part[4]: future"))
      assert(out.contains("type: future-kind (unsupported)"))
      assert(out.contains("scriptStatus: missing"))
      assert(out.contains("artifacts:"))
      assert(out.contains("project-output: planned " + dir.resolve("build/final.mp4").normalize()))
      assert(out.contains("part-output: planned " + dir.resolve("01/build/part.mp4").normalize()))
      assert(out.contains("part-audio-dir: planned " + dir.resolve("01/build/audio").normalize()))
      assert(out.contains("part-steps: missing-input " + dir.resolve("steps.json").normalize()))
      assert(out.contains("part-record-dir: planned " + dir.resolve("03/build/recording").normalize()))
      assert(out.contains("part-manifest: planned " + dir.resolve("01/build/part.manifest.json").normalize()))
    }
  }

  test("video build dry-run reports artifact and command plans without external tools") {
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

      val out = CozyVideo.build(CozyVideo.BuildConfig(dir.resolve("video_project.json"), dryRun = true, checkTools = false), CozyVideo.VideoToolRegistry.default)

      assert(out.contains("Cozy Video Build Dry-Run"))
      assert(out.contains("artifacts:"))
      assert(out.contains("part-script: missing-input " + dir.resolve("missing.json").normalize()))
      assert(out.contains("commands:"))
      assert(out.contains("part.lecture.parse-script: cozy (host) - parse dialogue script"))
      assert(out.contains("part.lecture.synthesize: voicevox (external-service) - synthesize scene audio"))
      assert(out.contains("part.lecture.prepare-visuals: python-pillow (docker) - docker run --rm"))
      assert(out.contains("part.lecture.render: remotion (docker) - docker run --rm"))
      assert(out.contains("part.board.render: remotion (docker) - docker run --rm"))
      assert(out.contains("part.demo.capture: playwright (docker) - docker run --rm"))
      assert(out.contains("part.demo.synthesize: voicevox (external-service) - synthesize scene audio"))
      assert(out.contains("project.concat: ffmpeg (docker) - docker run --rm"))
      assert(out.contains("project.manifest: cozy (host) - write project manifest"))
      assert(!out.contains("  - part.future.render:"))
    }
  }

  test("video build dry-run can use explicit host tool mode") {
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

      val out = CozyVideo.build(CozyVideo.BuildConfig(dir.resolve("video_project.json"), dryRun = true, checkTools = false, toolMode = Some("host")), CozyVideo.VideoToolRegistry.default)

      assert(out.contains("toolMode: host"))
      assert(out.contains("part.intro.prepare-visuals: python-pillow (host) - prepare dialogue visual helper assets"))
      assert(out.contains("part.intro.render: remotion (host) - render dialogue part"))
      assert(out.contains("project.concat: ffmpeg (host) - concat planned part outputs into final video"))
    }
  }

  test("video build dry-run resolves docker mode and image precedence") {
    _with_temp_dir("cozy-video-build-docker-precedence") { dir =>
      _write(dir.resolve("script.json"), _script_json)
      _write(dir.resolve("conf/cozy/config.yaml"), "video:\n  docker-image: shared-image\n  tool-mode: host\ncozy:\n  docker-image: cozy-image\n")
      _write(dir.resolve(".cozy/config.yaml"), "video:\n  docker-image: local-image\n")
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

      val project = CozyVideo.build(CozyVideo.BuildConfig(dir.resolve("video_project.json"), dryRun = true, checkTools = false), CozyVideo.VideoToolRegistry.default)
      val cli = CozyVideo.build(CozyVideo.BuildConfig(dir.resolve("video_project.json"), dryRun = true, checkTools = false, toolMode = Some("host"), dockerImage = Some("cli-image")), CozyVideo.VideoToolRegistry.default)

      assert(project.contains("toolMode: docker"))
      assert(project.contains("dockerImage: project-image"))
      assert(project.contains("docker run --rm -v '" + dir + ":/workspace' -w /workspace 'project-image' 'remotion'"))
      assert(cli.contains("toolMode: host"))
      assert(cli.contains("dockerImage: cli-image"))
      assert(cli.contains("part.intro.render: remotion (host)"))
    }
  }

  test("video build dry-run reads video config defaults with local override") {
    _with_temp_dir("cozy-video-build-config-defaults") { dir =>
      _write(dir.resolve("script.json"), _script_json)
      _write(dir.resolve("conf/cozy/config.yaml"), "video:\n  docker-image: shared-image\n  tool-mode: host\n")
      _write(dir.resolve(".cozy/config.yaml"), "video:\n  docker-image: local-image\n  tool-mode: docker\n")
      _write(dir.resolve("video_project.json"), _project_json("script.json"))

      val out = CozyVideo.build(CozyVideo.BuildConfig(dir.resolve("video_project.json"), dryRun = true, checkTools = false), CozyVideo.VideoToolRegistry.default)

      assert(out.contains("toolMode: docker"))
      assert(out.contains("dockerImage: local-image"))
    }
  }

  test("video build fails explicitly for invalid tool mode") {
    _with_temp_dir("cozy-video-build-invalid-tool-mode") { dir =>
      _write(dir.resolve("script.json"), _script_json)
      _write(dir.resolve("video_project.json"), _project_json("script.json"))

      val e = intercept[Throwable] {
        CozyVideo.build(CozyVideo.BuildConfig(dir.resolve("video_project.json"), dryRun = true, checkTools = false, toolMode = Some("invalid")), CozyVideo.VideoToolRegistry.default)
      }

      assert(e.getMessage.contains("Invalid video tool mode"))
    }
  }

  test("video build dry-run excludes missing inputs from render and concat plans") {
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

      val out = CozyVideo.build(CozyVideo.BuildConfig(dir.resolve("video_project.json"), dryRun = true, checkTools = false), CozyVideo.VideoToolRegistry.default)

      assert(out.contains("part-script: missing-input " + dir.resolve("missing-dialogue.json").normalize()))
      assert(out.contains("part-steps: missing-input " + dir.resolve("missing-steps.json").normalize()))
      assert(!out.contains("  - part.dialogue-missing.render:"))
      assert(!out.contains("  - part.web-missing-steps.capture:"))
      assert(!out.contains("  - part.web-missing-steps.render:"))
      assert(!out.contains("    inputs: " + dir.resolve("build/parts/dialogue-missing.mp4").normalize()))
      assert(!out.contains("    inputs: " + dir.resolve("build/parts/web-missing-steps.mp4").normalize()))
    }
  }

  test("video build assembles rendered parts with ffmpeg and validates with ffprobe") {
    _with_temp_dir("cozy-video-build-final-docker") { dir =>
      _write(dir.resolve("dialogue.json"), _script_json)
      _write(dir.resolve("storyboard.json"), _script_json)
      _write_bytes(dir.resolve("build/parts/lecture.mp4"), Array[Byte](1, 2, 3))
      _write_bytes(dir.resolve("build/parts/board.mp4"), Array[Byte](4, 5, 6))
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

      val out = CozyVideo.build(CozyVideo.BuildConfig(dir.resolve("video_project.json"), dryRun = false, checkTools = false), CozyVideo.VideoToolRegistry(Vector.empty), runner)

      assert(out.contains("Cozy Video Build"))
      assert(out.contains("output: " + dir.resolve("build/final.mp4").normalize()))
      assert(out.contains("manifest: " + dir.resolve("build/manifest.json").normalize()))
      assert(out.contains("parts: 2"))
      assert(runner.commands.size == 2)
      assert(runner.commands(0).args.take(8) == Vector("docker", "run", "--rm", "-v", s"$dir:/workspace", "-w", "/workspace", "simplemodeling/cozy-toolchain:latest"))
      assert(runner.commands(0).args.contains("ffmpeg"))
      assert(runner.commands(0).args.contains("/workspace/target/cozy-video/ffmpeg/concat.txt"))
      assert(runner.commands(0).args.contains("/workspace/build/final.mp4"))
      assert(runner.commands(1).args.contains("ffprobe"))
      assert(runner.commands(1).args.contains("/workspace/build/final.mp4"))
      val concat = _read(dir.resolve("target/cozy-video/ffmpeg/concat.txt"))
      assert(concat.contains("file '/workspace/build/parts/lecture.mp4'"))
      assert(concat.contains("file '/workspace/build/parts/board.mp4'"))
      assert(Files.isRegularFile(dir.resolve("build/final.mp4")))
      val manifest = _read(dir.resolve("build/manifest.json"))
      assert(manifest.contains("\"outputPath\""))
      assert(manifest.contains("\"partOutputs\""))
      assert(manifest.contains("\"concatListPath\""))
      assert(manifest.contains("\"ffprobe\""))
    }
  }

  test("video build can assemble rendered parts in host mode") {
    _with_temp_dir("cozy-video-build-final-host") { dir =>
      _write(dir.resolve("script.json"), _script_json)
      _write_bytes(dir.resolve("build/parts/intro.mp4"), Array[Byte](1, 2, 3))
      _write(dir.resolve("video_project.json"), _project_json("script.json"))
      val runner = AssemblyRunner()

      val out = CozyVideo.build(CozyVideo.BuildConfig(dir.resolve("video_project.json"), dryRun = false, checkTools = false, toolMode = Some("host")), CozyVideo.VideoToolRegistry(Vector.empty), runner)

      assert(out.contains("toolMode: host"))
      assert(runner.commands.size == 2)
      assert(runner.commands(0).args.head == "ffmpeg")
      assert(runner.commands(1).args.head == "ffprobe")
      assert(!runner.commands.exists(_.args.head == "docker"))
      assert(_read(dir.resolve("target/cozy-video/ffmpeg/concat.txt")).contains("file '" + dir.resolve("build/parts/intro.mp4").normalize() + "'"))
      assert(Files.isRegularFile(dir.resolve("build/manifest.json")))
    }
  }

  test("cozy video build CLI dispatch assembles rendered parts through injected runner") {
    _with_temp_dir("cozy-video-build-final-cli") { dir =>
      _write(dir.resolve("script.json"), _script_json)
      _write_bytes(dir.resolve("build/parts/intro.mp4"), Array[Byte](1, 2, 3))
      _write(dir.resolve("video_project.json"), _project_json("script.json"))
      val runner = AssemblyRunner()

      val out = _capture {
        assert(CozyVideo.execute(
          List("video", "build", dir.resolve("video_project.json").toString, "--tool-mode=host"),
          CozyVideo.VideoToolRegistry(Vector.empty),
          RecordingVoicevoxClient(),
          runner
        ))
      }

      assert(out.contains("Cozy Video Build"))
      assert(runner.commands.size == 2)
      assert(runner.commands(0).args.head == "ffmpeg")
      assert(runner.commands(1).args.head == "ffprobe")
      assert(Files.isRegularFile(dir.resolve("build/final.mp4")))
      assert(Files.isRegularFile(dir.resolve("build/manifest.json")))
    }
  }

  test("video build reports missing part outputs runner failures and tool checks") {
    _with_temp_dir("cozy-video-build-final-errors") { dir =>
      _write(dir.resolve("script.json"), _script_json)
      _write(dir.resolve("video_project.json"), _project_json("script.json"))

      val missingoutput = intercept[Throwable] {
        CozyVideo.build(CozyVideo.BuildConfig(dir.resolve("video_project.json"), dryRun = false, checkTools = false), CozyVideo.VideoToolRegistry(Vector.empty), AssemblyRunner())
      }
      assert(missingoutput.getMessage.contains("Missing rendered part output"))
      assert(missingoutput.getMessage.contains("cozy video render"))

      _write_bytes(dir.resolve("build/parts/intro.mp4"), Array[Byte](1, 2, 3))
      val ffmpegfailure = intercept[Throwable] {
        CozyVideo.build(CozyVideo.BuildConfig(dir.resolve("video_project.json"), dryRun = false, checkTools = false), CozyVideo.VideoToolRegistry(Vector.empty), AssemblyRunner(failTool = Some("ffmpeg")))
      }
      assert(ffmpegfailure.getMessage.contains("ffmpeg concat/mux failed"))

      val ffprobefailure = intercept[Throwable] {
        CozyVideo.build(CozyVideo.BuildConfig(dir.resolve("video_project.json"), dryRun = false, checkTools = false), CozyVideo.VideoToolRegistry(Vector.empty), AssemblyRunner(failTool = Some("ffprobe")))
      }
      assert(ffprobefailure.getMessage.contains("ffprobe validation failed"))

      val invalidprobe = intercept[Throwable] {
        CozyVideo.build(CozyVideo.BuildConfig(dir.resolve("video_project.json"), dryRun = false, checkTools = false), CozyVideo.VideoToolRegistry(Vector.empty), AssemblyRunner(invalidProbeJson = true))
      }
      assert(invalidprobe.getMessage.contains("ffprobe returned invalid JSON"))

      val missingtool = intercept[Throwable] {
        CozyVideo.build(
          CozyVideo.BuildConfig(dir.resolve("video_project.json"), dryRun = false, checkTools = true, toolMode = Some("host")),
          CozyVideo.VideoToolRegistry(Vector(StubProvider(CozyVideo.VideoToolCheck("ffmpeg", CozyVideo.VideoToolMode.Host, CozyVideo.VideoToolStatus.Missing, "missing ffmpeg", Some("install ffmpeg"))))),
          AssemblyRunner()
        )
      }
      assert(missingtool.getMessage.contains("ffmpeg is missing"))
      assert(missingtool.getMessage.contains("install ffmpeg"))
    }
  }

  test("video build fails explicitly for unknown options") {
    _with_temp_dir("cozy-video-build-unknown-option") { dir =>
      _write(dir.resolve("script.json"), _script_json)
      _write(dir.resolve("video_project.json"), _project_json("script.json"))
      val e = intercept[Throwable] {
        CozyVideo.execute(List("video", "build", dir.resolve("video_project.json").toString, "--dry-ran"), CozyVideo.VideoToolRegistry(Vector.empty))
      }

      assert(e.getMessage.contains("dry-ran"))
    }
  }

  test("video rdf generates Turtle JSON-LD and manifest from video artifacts") {
    _with_temp_dir("cozy-video-rdf") { dir =>
      _write(dir.resolve("script.json"), _script_json)
      _write(dir.resolve("video_project.json"), _project_json("script.json"))
      _write_audio_manifest(dir.resolve("build/audio/intro"), Vector("title", "description", "summary"))
      _write(dir.resolve("build/audio/intro/manifest.json"), _audio_manifest_json(Vector("title", "description", "summary")).replace("\"speaker\":null", "\"speaker\":\"narrator\""))
      _write(
        dir.resolve("build/parts/intro.manifest.json"),
        s"""{
           |  "partId": "intro",
           |  "renderer": "simple-java2d",
           |  "outputPath": "${dir.resolve("build/parts/intro.mp4").normalize()}",
           |  "toolMode": "docker",
           |  "dockerImage": "simplemodeling/cozy-toolchain:latest"
           |}
           |""".stripMargin
      )
      _write(
        dir.resolve("build/manifest.json"),
        s"""{
           |  "outputPath": "${dir.resolve("build/final.mp4").normalize()}",
           |  "toolMode": "docker",
           |  "dockerImage": "simplemodeling/cozy-toolchain:latest",
           |  "concatListPath": "${dir.resolve("target/cozy-video/ffmpeg/concat.txt").normalize()}",
           |  "ffprobe": {"format": {"duration": "12.0"}}
           |}
           |""".stripMargin
      )

      val out = CozyVideo.rdf(CozyVideo.RdfConfig(dir.resolve("video_project.json"), dir.resolve("rdf")))

      assert(out.contains("Cozy Video RDF"))
      assert(out.contains("turtle: " + dir.resolve("rdf/video.ttl").normalize()))
      assert(out.contains("jsonld: " + dir.resolve("rdf/video.jsonld").normalize()))
      assert(Files.isRegularFile(dir.resolve("rdf/video.ttl")))
      assert(Files.isRegularFile(dir.resolve("rdf/video.jsonld")))
      assert(Files.isRegularFile(dir.resolve("rdf/manifest.json")))
      val turtle = _read(dir.resolve("rdf/video.ttl"))
      val jsonld = _read(dir.resolve("rdf/video.jsonld"))
      val manifest = _read(dir.resolve("rdf/manifest.json"))
      assert(turtle.contains("@prefix cozy-video: <https://www.simplemodeling.org/ns/cozy/video#> ."))
      assert(turtle.contains("cozy-video:VideoProject"))
      assert(turtle.contains("cozy-video:VideoPart"))
      assert(turtle.contains("cozy-video:VideoScene"))
      assert(turtle.contains("cozy-video:VideoUtterance"))
      assert(turtle.contains("cozy-video:VideoArtifact"))
      assert(turtle.contains("cozy-video:speaker"))
      assert(turtle.contains("cozy-video:audioDuration"))
      assert(turtle.contains("simple-java2d"))
      assert(turtle.contains("ffprobe"))
      assert(jsonld.contains("\"cozy-video\""))
      assert(jsonld.contains("cozy-video:VideoProject"))
      assert(manifest.contains("\"tripleCount\""))
      assert(manifest.contains("\"resourceCount\""))
    }
  }

  test("video rdf records missing manifests without failing") {
    _with_temp_dir("cozy-video-rdf-missing-manifests") { dir =>
      _write(dir.resolve("script.json"), _script_json)
      _write(dir.resolve("video_project.json"), _project_json("script.json"))

      CozyVideo.rdf(CozyVideo.RdfConfig(dir.resolve("video_project.json"), dir.resolve("rdf")))

      val turtle = _read(dir.resolve("rdf/video.ttl"))
      assert(turtle.contains("cozy-video:artifactKind \"audio-manifest\""))
      assert(turtle.contains("cozy-video:artifactKind \"part-manifest\""))
      assert(turtle.contains("cozy-video:artifactKind \"project-manifest\""))
      assert(turtle.contains("cozy-video:status \"missing\""))
      assert(turtle.contains("cozy-video:VideoScene"))
    }
  }

  test("video rdf percent-encodes resource ids for Turtle-safe output") {
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

      CozyVideo.rdf(CozyVideo.RdfConfig(dir.resolve("video_project.json"), dir.resolve("rdf")))

      val turtle = _read(dir.resolve("rdf/video.ttl"))
      val jsonld = _read(dir.resolve("rdf/video.jsonld"))
      assert(turtle.contains("cozy-video:project/Sample%20Video%202026"))
      assert(turtle.contains("cozy-video:part/intro%20slide"))
      assert(turtle.contains("cozy-video:scene/intro%20slide-scene%20%231"))
      assert(jsonld.contains("cozy-video:project/Sample%20Video%202026"))
      assert(jsonld.contains("cozy-video:part/intro%20slide"))
      assert(!turtle.contains("cozy-video:part/intro slide"))
    }
  }

  test("video rdf fails explicitly for invalid manifests inputs and options") {
    _with_temp_dir("cozy-video-rdf-errors") { dir =>
      _write(dir.resolve("script.json"), _script_json)
      _write(dir.resolve("video_project.json"), _project_json("script.json"))
      _write(dir.resolve("build/audio/intro/manifest.json"), "not json")

      val invalidmanifest = intercept[Throwable] {
        CozyVideo.rdf(CozyVideo.RdfConfig(dir.resolve("video_project.json"), dir.resolve("rdf")))
      }
      assert(invalidmanifest.getMessage.contains("Invalid audio manifest intro JSON"))

      val missingsave = intercept[Throwable] {
        CozyVideo.execute(List("video", "rdf", dir.resolve("video_project.json").toString), CozyVideo.VideoToolRegistry(Vector.empty))
      }
      assert(missingsave.getMessage.contains("save"))

      val unknownoption = intercept[Throwable] {
        CozyVideo.execute(List("video", "rdf", dir.resolve("video_project.json").toString, "--save", dir.resolve("rdf").toString, "--unknown"), CozyVideo.VideoToolRegistry(Vector.empty))
      }
      assert(unknownoption.getMessage.contains("unknown"))

      val missingproject = intercept[Throwable] {
        CozyVideo.rdf(CozyVideo.RdfConfig(dir.resolve("missing.json"), dir.resolve("rdf")))
      }
      assert(missingproject.getMessage.contains("Missing video project file"))
    }
  }

  test("video transcribe writes transcript captions narration and manifest in docker mode") {
    _with_temp_dir("cozy-video-transcribe-docker") { dir =>
      val input = dir.resolve("demo.mp4")
      val save = dir.resolve("transcript")
      _write_bytes(input, Array[Byte](1, 2, 3, 4))
      val runner = TranscriptionRunner()

      val out = CozyVideo.transcribe(
        CozyVideo.TranscribeConfig(input, save, projectRootOverride = Some(dir)),
        CozyVideo.VideoToolRegistry(Vector.empty),
        runner
      )

      assert(out.contains("Cozy Video Transcribe"))
      assert(out.contains("toolMode: docker"))
      assert(out.contains("modelPath: /opt/cozy/models/ggml-base.bin"))
      assert(Files.isRegularFile(save.resolve("audio.wav")))
      assert(Files.isRegularFile(save.resolve("transcript.json")))
      assert(Files.isRegularFile(save.resolve("captions.srt")))
      assert(Files.isRegularFile(save.resolve("narration.json")))
      assert(Files.isRegularFile(save.resolve("manifest.json")))
      val transcript = _read(save.resolve("transcript.json"))
      val captions = _read(save.resolve("captions.srt"))
      val narration = _read(save.resolve("narration.json"))
      val manifest = _read(save.resolve("manifest.json"))
      assert(transcript.contains("cozy.video.transcript.v1"))
      assert(transcript.contains("Hello world"))
      assert(captions.contains("00:00:00,000 --> 00:00:01,250"))
      assert(narration.contains("cozy.video.narration-draft.v1"))
      assert(manifest.contains("inputSha256"))
      assert(manifest.contains("whisper-cli 1.7.6"))
      assert(runner.commands.head.args.take(8) == Vector("docker", "run", "--rm", "-v", s"${dir.toAbsolutePath.normalize}:/workspace", "-w", "/workspace", "simplemodeling/cozy-toolchain:latest"))
      assert(runner.commands.exists(_.args.contains("/opt/cozy/models/ggml-base.bin")))
    }
  }

  test("video transcribe resolves config defaults and CLI whisper model in host mode") {
    _with_temp_dir("cozy-video-transcribe-config") { dir =>
      val input = dir.resolve("demo.mp4")
      val save = dir.resolve("transcript")
      val configmodel = dir.resolve("models/config.bin")
      val climodel = dir.resolve("models/cli.bin")
      _write_bytes(input, Array[Byte](1, 2, 3))
      _write(configmodel, "model")
      _write(climodel, "model")
      _write(dir.resolve("conf/cozy/config.yaml"), "video:\n  tool-mode: docker\n  docker-image: conf-image\n")
      _write(dir.resolve(".cozy/config.yaml"), "video:\n  tool-mode: host\ntools:\n  whisperModel: models/config.bin\n")
      val runner = TranscriptionRunner()

      val out = CozyVideo.transcribe(
        CozyVideo.TranscribeConfig.create(
          List("video", "transcribe", input.toString, s"--save=${save.toString}", "--whisper-model=models/cli.bin").drop(2),
          dir
        ),
        CozyVideo.VideoToolRegistry(Vector.empty),
        runner
      )
      assert(out.contains("Cozy Video Transcribe"))

      assert(runner.commands.exists(_.args.headOption.contains("ffmpeg")))
      assert(!runner.commands.exists(_.args.headOption.contains("docker")))
      assert(runner.commands.exists(_.args.contains(climodel.normalize().toString)))
      assert(Files.isRegularFile(save.resolve("manifest.json")))
    }
  }

  test("video transcribe validates inputs options and tool checks") {
    _with_temp_dir("cozy-video-transcribe-failures") { dir =>
      val input = dir.resolve("demo.mp4")
      val save = dir.resolve("transcript")
      val model = dir.resolve("models/model.bin")
      _write_bytes(input, Array[Byte](1, 2, 3))
      _write(model, "model")

      val missinginput = intercept[RuntimeException] {
        CozyVideo.transcribe(CozyVideo.TranscribeConfig(dir.resolve("missing.mp4"), save), CozyVideo.VideoToolRegistry(Vector.empty), TranscriptionRunner())
      }
      assert(missinginput.getMessage.contains("Missing input video"))

      val missingsave = intercept[RuntimeException] {
        CozyVideo.execute(List("video", "transcribe", input.toString), CozyVideo.VideoToolRegistry(Vector.empty))
      }
      assert(missingsave.getMessage.contains("save"))

      val unknown = intercept[RuntimeException] {
        CozyVideo.execute(List("video", "transcribe", input.toString, "--save", save.toString, "--unknown"), CozyVideo.VideoToolRegistry(Vector.empty))
      }
      assert(unknown.getMessage.contains("Unknown option"))

      val invalidmode = intercept[RuntimeException] {
        CozyVideo.transcribe(CozyVideo.TranscribeConfig(input, save, toolMode = Some("remote")), CozyVideo.VideoToolRegistry(Vector.empty), TranscriptionRunner())
      }
      assert(invalidmode.getMessage.contains("Invalid video tool mode"))

      val dockercheck = intercept[RuntimeException] {
        CozyVideo.transcribe(
          CozyVideo.TranscribeConfig(input, save, checkTools = true),
          CozyVideo.VideoToolRegistry(Vector(StubProvider(CozyVideo.VideoToolCheck("docker-image", CozyVideo.VideoToolMode.Docker, CozyVideo.VideoToolStatus.Missing, "missing image", Some("pull image"))))),
          TranscriptionRunner()
        )
      }
      assert(dockercheck.getMessage.contains("docker-image"))
      assert(dockercheck.getMessage.contains("pull image"))

      val hostcheck = intercept[RuntimeException] {
        CozyVideo.transcribe(
          CozyVideo.TranscribeConfig(input, save, checkTools = true, toolMode = Some("host"), whisperModel = Some(model.toString)),
          CozyVideo.VideoToolRegistry(Vector(StubProvider(CozyVideo.VideoToolCheck("whisper-cpp", CozyVideo.VideoToolMode.Host, CozyVideo.VideoToolStatus.Missing, "missing whisper", Some("install whisper"))))),
          TranscriptionRunner()
        )
      }
      assert(hostcheck.getMessage.contains("whisper-cpp"))
      assert(hostcheck.getMessage.contains("install whisper"))

      val ffmpegfailure = intercept[RuntimeException] {
        CozyVideo.transcribe(CozyVideo.TranscribeConfig(input, save, toolMode = Some("host"), whisperModel = Some(model.toString)), CozyVideo.VideoToolRegistry(Vector.empty), TranscriptionRunner(failTool = Some("ffmpeg")))
      }
      assert(ffmpegfailure.getMessage.contains("ffmpeg audio extraction failed"))

      val whisperfailure = intercept[RuntimeException] {
        CozyVideo.transcribe(CozyVideo.TranscribeConfig(input, save, toolMode = Some("host"), whisperModel = Some(model.toString)), CozyVideo.VideoToolRegistry(Vector.empty), TranscriptionRunner(failTool = Some("whisper-cli")))
      }
      assert(whisperfailure.getMessage.contains("whisper.cpp transcription failed"))

      val projectroot = dir.resolve("project-root")
      val projectinput = projectroot.resolve("demo.mp4")
      val projectsave = projectroot.resolve("transcript")
      val outsideinput = dir.resolve("outside/demo.mp4")
      val outsidesave = dir.resolve("outside-transcript")
      _write_bytes(projectinput, Array[Byte](1, 2, 3))
      _write_bytes(outsideinput, Array[Byte](1, 2, 3))
      val inputrunner = TranscriptionRunner()
      val dockerinput = intercept[RuntimeException] {
        CozyVideo.transcribe(CozyVideo.TranscribeConfig(outsideinput, projectsave, projectRootOverride = Some(projectroot)), CozyVideo.VideoToolRegistry(Vector.empty), inputrunner)
      }
      assert(dockerinput.getMessage.contains("Docker transcription requires input video under project root"))
      assert(inputrunner.commands.isEmpty)
      val saverunner = TranscriptionRunner()
      val dockersave = intercept[RuntimeException] {
        CozyVideo.transcribe(CozyVideo.TranscribeConfig(projectinput, outsidesave, projectRootOverride = Some(projectroot)), CozyVideo.VideoToolRegistry(Vector.empty), saverunner)
      }
      assert(dockersave.getMessage.contains("Docker transcription requires --save under project root"))
      assert(saverunner.commands.isEmpty)
    }
  }

  test("video synthesize writes scene wavs combined wav and manifest through VOICEVOX client") {
    _with_temp_dir("cozy-video-synthesize") { dir =>
      val script = dir.resolve("script.json")
      val outdir = dir.resolve("audio")
      _write(script, _voicevox_script_json)
      val voicevox = RecordingVoicevoxClient(
        speakersJson = Json.arr(
          Json.obj(
            "name" -> Json.fromString("Character Voice"),
            "styles" -> Json.arr(Json.obj("name" -> Json.fromString("Normal"), "id" -> Json.fromInt(10)))
          ),
          Json.obj(
            "name" -> Json.fromString("Top Voice"),
            "styles" -> Json.arr(Json.obj("name" -> Json.fromString("Plain"), "id" -> Json.fromInt(20)))
          )
        )
      )

      val result = CozyVideo.synthesize(CozyVideo.SynthesizeConfig(script, outdir, Some("http://voicevox.example")), voicevox)
      val manifest = parser.parse(Files.readString(outdir.resolve("manifest.json"), StandardCharsets.UTF_8)).toOption.flatMap(_.asArray).get

      assert(result.contains("Cozy Video Synthesize"))
      assert(result.contains("scenes: 3"))
      assert(Files.isRegularFile(outdir.resolve("01-intro.wav")))
      assert(Files.isRegularFile(outdir.resolve("01-intro-lead.wav")))
      assert(Files.isRegularFile(outdir.resolve("01-intro-silence.wav")))
      assert(Files.isRegularFile(outdir.resolve("02-fallback.wav")))
      assert(Files.isRegularFile(outdir.resolve("03-silent.wav")))
      assert(Files.isRegularFile(outdir.resolve("script.wav")))
      assert(voicevox.calls.map(_.kind) == Vector("speakers", "audio_query", "synthesis", "speakers", "audio_query", "synthesis"))
      assert(voicevox.calls.collect { case c if c.kind == "audio_query" => c.text } == Vector(Some("HelloCozy"), Some("TopLine")))
      assert(voicevox.calls.collect { case c if c.kind == "audio_query" => c.speakerId } == Vector(Some(10), Some(99)))
      assert(voicevox.audioQueries.exists(_.hcursor.downField("speedScale").as[Double].toOption.contains(1.2)))
      assert(voicevox.audioQueries.exists(_.hcursor.downField("volumeScale").as[Double].toOption.contains(0.8)))
      assert(manifest.size == 3)
      assert(manifest.head.hcursor.downField("sceneId").as[String].toOption.contains("intro"))
      assert(manifest.head.hcursor.downField("leadSilence").as[Double].toOption.contains(0.1))
      assert(manifest(2).hcursor.downField("sceneId").as[String].toOption.contains("silent"))
    }
  }

  test("video synthesize resolves voicevox url from script tools and config") {
    _with_temp_dir("cozy-video-synthesize-url") { dir =>
      val voicevox = RecordingVoicevoxClient()
      _write(dir.resolve("script-tools.json"), """{"tools": {"voicevoxUrl": "http://script.example"}, "scenes": [{"id": "s1", "duration": 0.2, "line": "A"}]}""")
      _write(dir.resolve("conf/cozy/config.yaml"), "video:\n  voicevox:\n    url: http://config.example\n")

      CozyVideo.synthesize(CozyVideo.SynthesizeConfig(dir.resolve("script-tools.json"), dir.resolve("audio1")), voicevox)
      assert(voicevox.calls.head.baseUrl == "http://script.example")

      val configvoicevox = RecordingVoicevoxClient()
      _write(dir.resolve("script-config.json"), """{"scenes": [{"id": "s1", "duration": 0.2, "line": "A"}]}""")
      CozyVideo.synthesize(CozyVideo.SynthesizeConfig(dir.resolve("script-config.json"), dir.resolve("audio2")), configvoicevox)
      assert(configvoicevox.calls.head.baseUrl == "http://config.example")
    }
  }

  test("video synthesize fails explicitly for missing script save option and voicevox errors") {
    _with_temp_dir("cozy-video-synthesize-errors") { dir =>
      _write(dir.resolve("script.json"), """{"scenes": [{"id": "s1", "duration": 0.2, "line": "A"}]}""")

      val missingsave = intercept[Throwable] {
        CozyVideo.execute(List("video", "synthesize", dir.resolve("script.json").toString), CozyVideo.VideoToolRegistry(Vector.empty), RecordingVoicevoxClient())
      }
      assert(missingsave.getMessage.contains("Missing --save"))

      val missingfile = intercept[Throwable] {
        CozyVideo.synthesize(CozyVideo.SynthesizeConfig(dir.resolve("missing.json"), dir.resolve("audio")), RecordingVoicevoxClient())
      }
      assert(missingfile.getMessage.contains("Missing video script file"))

      val voicevoxfailure = intercept[Throwable] {
        CozyVideo.synthesize(CozyVideo.SynthesizeConfig(dir.resolve("script.json"), dir.resolve("audio")), RecordingVoicevoxClient(failSpeakers = true))
      }
      assert(voicevoxfailure.getMessage.contains("VOICEVOX speakers failed"))

      _write(dir.resolve("unsafe-scene.json"), """{"scenes": [{"id": "../escape", "duration": 0.2, "line": "A"}]}""")
      val unsafescene = intercept[Throwable] {
        CozyVideo.synthesize(CozyVideo.SynthesizeConfig(dir.resolve("unsafe-scene.json"), dir.resolve("audio-unsafe")), RecordingVoicevoxClient())
      }
      assert(unsafescene.getMessage.contains("Invalid scene id"))

      val invalidurl = intercept[Throwable] {
        CozyVideo.synthesize(CozyVideo.SynthesizeConfig(dir.resolve("script.json"), dir.resolve("audio-invalid-url"), Some("://bad")), CozyVideo.VoicevoxClient.default)
      }
      assert(invalidurl.getMessage.contains("VOICEVOX speakers failed"))
    }
  }

  test("video render remotion renders all renderable parts through a runner") {
    _with_temp_dir("cozy-video-render-remotion") { dir =>
      _write(dir.resolve("dialogue.json"), _script_json)
      _write(dir.resolve("storyboard.json"), _script_json)
      _write_audio_manifest(dir.resolve("build/audio/lecture"), Vector("title", "description", "summary"))
      _write_audio_manifest(dir.resolve("build/audio/board"), Vector("title", "description", "summary"))
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

      val out = CozyVideo.render(CozyVideo.RenderConfig(dir.resolve("video_project.json"), "remotion"), CozyVideo.VideoToolRegistry(Vector.empty), runner)

      assert(out.contains("Cozy Video Render"))
      assert(out.contains("parts: 2"))
      assert(out.contains("part.lecture: " + dir.resolve("build/parts/lecture.mp4").normalize()))
      assert(out.contains("part.board: " + dir.resolve("build/parts/board.mp4").normalize()))
      assert(runner.commands.size == 2)
      assert(runner.commands.head.args.take(8) == Vector("docker", "run", "--rm", "-v", s"$dir:/workspace", "-w", "/workspace", "simplemodeling/cozy-toolchain:latest"))
      assert(runner.commands.head.args.contains("node"))
      assert(runner.commands.head.args.exists(_.endsWith("target/cozy-video/remotion/lecture/src/render.mjs")))
      assert(Files.isRegularFile(dir.resolve("target/cozy-video/remotion/lecture/package.json")))
      assert(Files.isRegularFile(dir.resolve("target/cozy-video/remotion/lecture/src/Root.tsx")))
      assert(Files.isRegularFile(dir.resolve("target/cozy-video/remotion/lecture/src/render.mjs")))
      assert(Files.isRegularFile(dir.resolve("target/cozy-video/remotion/lecture/src/props.ts")))
      assert(Files.isRegularFile(dir.resolve("target/cozy-video/remotion/lecture/props.json")))
      assert(Files.isRegularFile(dir.resolve("target/cozy-video/remotion/lecture/public/audio/01-title.wav")))
      assert(Files.isRegularFile(dir.resolve("build/parts/lecture.manifest.json")))
      assert(Files.isRegularFile(dir.resolve("build/parts/board.manifest.json")))
      assert(!Files.exists(dir.resolve("build/parts/manifest.json")))
      val root = _read(dir.resolve("target/cozy-video/remotion/lecture/src/Root.tsx"))
      val render = _read(dir.resolve("target/cozy-video/remotion/lecture/src/render.mjs"))
      val props = _read(dir.resolve("target/cozy-video/remotion/lecture/src/props.ts"))
      assert(root.contains("cozyVideoProps"))
      assert(root.contains("staticFile(scene.audioPath)"))
      assert(!root.contains("React.FC<Props> = (props)"))
      assert(!render.contains("--props"))
      assert(props.contains("audio/01-title.wav"))
    }
  }

  test("video render remotion can render one selected part in host mode") {
    _with_temp_dir("cozy-video-render-selected-host") { dir =>
      _write(dir.resolve("dialogue.json"), _script_json)
      _write(dir.resolve("storyboard.json"), _script_json)
      _write_audio_manifest(dir.resolve("build/audio/lecture"), Vector("title", "description", "summary"))
      _write_audio_manifest(dir.resolve("build/audio/board"), Vector("title", "description", "summary"))
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

      val out = CozyVideo.render(CozyVideo.RenderConfig(dir.resolve("video_project.json"), "remotion", part = Some("board"), toolMode = Some("host")), CozyVideo.VideoToolRegistry(Vector.empty), runner)

      assert(out.contains("toolMode: host"))
      assert(out.contains("parts: 1"))
      assert(out.contains("part.board: " + dir.resolve("build/parts/board.mp4").normalize()))
      assert(runner.commands.size == 1)
      assert(runner.commands.head.args.head == "node")
      assert(runner.commands.head.args(1).endsWith("target/cozy-video/remotion/board/src/render.mjs"))
      assert(Files.isRegularFile(dir.resolve("build/parts/board.manifest.json")))
    }
  }

  test("video render simple-java2d renders all renderable parts through python and ffmpeg") {
    _with_temp_dir("cozy-video-render-simple-java2d") { dir =>
      _write(dir.resolve("dialogue.json"), _script_json)
      _write(dir.resolve("storyboard.json"), _script_json)
      _write_audio_manifest(dir.resolve("build/audio/lecture"), Vector("title", "description", "summary"), Some("dialogue.wav"))
      _write_audio_manifest(dir.resolve("build/audio/board"), Vector("title", "description", "summary"), Some("storyboard.wav"))
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

      val out = CozyVideo.render(CozyVideo.RenderConfig(dir.resolve("video_project.json"), "simple-java2d"), CozyVideo.VideoToolRegistry(Vector.empty), runner)

      assert(out.contains("Cozy Video Render"))
      assert(out.contains("parts: 2"))
      assert(out.contains("part.lecture: " + dir.resolve("build/parts/lecture.mp4").normalize()))
      assert(out.contains("simpleJava2dWorkDir: " + dir.resolve("target/cozy-video/simple-java2d/lecture").normalize()))
      assert(runner.commands.size == 4)
      assert(runner.commands(0).args.take(8) == Vector("docker", "run", "--rm", "-v", s"$dir:/workspace", "-w", "/workspace", "simplemodeling/cozy-toolchain:latest"))
      assert(runner.commands(0).args.contains("python3"))
      assert(runner.commands(0).args.exists(_.endsWith("target/cozy-video/simple-java2d/lecture/render_frame.py")))
      assert(runner.commands(1).args.contains("ffmpeg"))
      assert(runner.commands(1).args.contains("/workspace/target/cozy-video/simple-java2d/lecture/frame.png"))
      assert(runner.commands(1).args.contains("/workspace/build/audio/lecture/dialogue.wav"))
      assert(Files.isRegularFile(dir.resolve("target/cozy-video/simple-java2d/lecture/props.json")))
      assert(Files.isRegularFile(dir.resolve("target/cozy-video/simple-java2d/lecture/render_frame.py")))
      assert(Files.isRegularFile(dir.resolve("target/cozy-video/simple-java2d/lecture/frame.png")))
      assert(!_read(dir.resolve("target/cozy-video/simple-java2d/lecture/render_frame.py")).contains("props.get(\"framePath\""))
      assert(Files.isRegularFile(dir.resolve("build/parts/lecture.mp4")))
      assert(Files.isRegularFile(dir.resolve("build/parts/lecture.manifest.json")))
      val manifest = _read(dir.resolve("build/parts/lecture.manifest.json"))
      assert(manifest.contains("\"renderer\" : \"simple-java2d\""))
      assert(manifest.contains("\"framePath\""))
      assert(manifest.contains("\"audioCombinedPath\""))
      assert(manifest.contains("\"simpleJava2dWorkDir\""))
    }
  }

  test("video render simple-java2d can render one selected part in host mode") {
    _with_temp_dir("cozy-video-render-simple-java2d-host") { dir =>
      _write(dir.resolve("dialogue.json"), _script_json)
      _write(dir.resolve("storyboard.json"), _script_json)
      _write_audio_manifest(dir.resolve("build/audio/lecture"), Vector("title", "description", "summary"), Some("dialogue.wav"))
      _write_audio_manifest(dir.resolve("build/audio/board"), Vector("title", "description", "summary"), Some("storyboard.wav"))
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

      val out = CozyVideo.render(CozyVideo.RenderConfig(dir.resolve("video_project.json"), "simple-java2d", part = Some("board"), toolMode = Some("host")), CozyVideo.VideoToolRegistry(Vector.empty), runner)

      assert(out.contains("toolMode: host"))
      assert(out.contains("parts: 1"))
      assert(out.contains("part.board: " + dir.resolve("build/parts/board.mp4").normalize()))
      assert(runner.commands.size == 2)
      assert(runner.commands(0).args.head == "python3")
      assert(runner.commands(1).args.head == "ffmpeg")
      assert(!runner.commands.exists(_.args.head == "docker"))
      assert(Files.isRegularFile(dir.resolve("build/parts/board.manifest.json")))
    }
  }

  test("video render remotion fails for invalid selection renderer inputs and runner failures") {
    _with_temp_dir("cozy-video-render-errors") { dir =>
      _write(dir.resolve("script.json"), _script_json)
      _write_audio_manifest(dir.resolve("build/audio/intro"), Vector("title", "description", "summary"))
      _write(dir.resolve("video_project.json"), _project_json("script.json"))

      val unknownpart = intercept[Throwable] {
        CozyVideo.render(CozyVideo.RenderConfig(dir.resolve("video_project.json"), "remotion", part = Some("missing")), CozyVideo.VideoToolRegistry(Vector.empty), RecordingRunner())
      }
      assert(unknownpart.getMessage.contains("Unknown video part"))

      val unsupportedrenderer = intercept[Throwable] {
        CozyVideo.render(CozyVideo.RenderConfig(dir.resolve("video_project.json"), "simple-java3d"), CozyVideo.VideoToolRegistry(Vector.empty), RecordingRunner())
      }
      assert(unsupportedrenderer.getMessage.contains("Unsupported video renderer"))

      val missingproject = intercept[Throwable] {
        CozyVideo.render(CozyVideo.RenderConfig(dir.resolve("missing.json"), "remotion"), CozyVideo.VideoToolRegistry(Vector.empty), RecordingRunner())
      }
      assert(missingproject.getMessage.contains("Missing video project file"))

      val runnerfailure = intercept[Throwable] {
        CozyVideo.render(CozyVideo.RenderConfig(dir.resolve("video_project.json"), "remotion"), CozyVideo.VideoToolRegistry(Vector.empty), RecordingRunner(result = CozyVideo.VideoCommandResult(1, "", "remotion missing")))
      }
      assert(runnerfailure.getMessage.contains("Remotion render failed"))
      assert(runnerfailure.getMessage.contains("remotion missing"))
    }
  }

  test("video render simple-java2d validates combined audio runner failures and tool checks") {
    _with_temp_dir("cozy-video-render-simple-java2d-errors") { dir =>
      _write(dir.resolve("script.json"), _script_json)
      _write_audio_manifest(dir.resolve("build/audio/intro"), Vector("title", "description", "summary"))
      _write(dir.resolve("video_project.json"), _project_json("script.json"))

      val missingcombined = intercept[Throwable] {
        CozyVideo.render(CozyVideo.RenderConfig(dir.resolve("video_project.json"), "simple-java2d"), CozyVideo.VideoToolRegistry(Vector.empty), RenderingRunner())
      }
      assert(missingcombined.getMessage.contains("Missing combined audio file"))
      assert(missingcombined.getMessage.contains("cozy video synthesize"))

      Files.write(dir.resolve("build/audio/intro/script.wav"), _wav_bytes(0.4))
      val framefailure = intercept[Throwable] {
        CozyVideo.render(CozyVideo.RenderConfig(dir.resolve("video_project.json"), "simple-java2d"), CozyVideo.VideoToolRegistry(Vector.empty), RenderingRunner(failTool = Some("python3")))
      }
      assert(framefailure.getMessage.contains("simple-java2d frame render failed"))

      val ffmpegfailure = intercept[Throwable] {
        CozyVideo.render(CozyVideo.RenderConfig(dir.resolve("video_project.json"), "simple-java2d"), CozyVideo.VideoToolRegistry(Vector.empty), RenderingRunner(failTool = Some("ffmpeg")))
      }
      assert(ffmpegfailure.getMessage.contains("simple-java2d ffmpeg encode failed"))

      val missingtool = intercept[Throwable] {
        CozyVideo.render(
          CozyVideo.RenderConfig(dir.resolve("video_project.json"), "simple-java2d", checkTools = true, toolMode = Some("host")),
          CozyVideo.VideoToolRegistry(Vector(
            StubProvider(CozyVideo.VideoToolCheck("python-pillow", CozyVideo.VideoToolMode.Host, CozyVideo.VideoToolStatus.Available, "ok")),
            StubProvider(CozyVideo.VideoToolCheck("ffmpeg", CozyVideo.VideoToolMode.Host, CozyVideo.VideoToolStatus.Missing, "missing ffmpeg", Some("install ffmpeg")))
          )),
          RenderingRunner()
        )
      }
      assert(missingtool.getMessage.contains("ffmpeg is missing"))
      assert(missingtool.getMessage.contains("install ffmpeg"))
    }
  }

  test("video render remotion validates audio prerequisites and tool checks") {
    _with_temp_dir("cozy-video-render-audio-errors") { dir =>
      _write(dir.resolve("script.json"), _script_json)
      _write(dir.resolve("video_project.json"), _project_json("script.json"))

      val missingmanifest = intercept[Throwable] {
        CozyVideo.render(CozyVideo.RenderConfig(dir.resolve("video_project.json"), "remotion"), CozyVideo.VideoToolRegistry(Vector.empty), RecordingRunner())
      }
      assert(missingmanifest.getMessage.contains("Missing audio manifest"))
      assert(missingmanifest.getMessage.contains("cozy video synthesize"))

      _write(dir.resolve("build/audio/intro/manifest.json"), _audio_manifest_json(Vector("title", "description", "summary")))
      val missingaudio = intercept[Throwable] {
        CozyVideo.render(CozyVideo.RenderConfig(dir.resolve("video_project.json"), "remotion"), CozyVideo.VideoToolRegistry(Vector.empty), RecordingRunner())
      }
      assert(missingaudio.getMessage.contains("Missing audio file"))

      _write_audio_manifest(dir.resolve("build/audio/intro"), Vector("title", "description", "summary"))
      val missingtool = intercept[Throwable] {
        CozyVideo.render(
          CozyVideo.RenderConfig(dir.resolve("video_project.json"), "remotion", checkTools = true, toolMode = Some("host")),
          CozyVideo.VideoToolRegistry(Vector(StubProvider(CozyVideo.VideoToolCheck("remotion-node", CozyVideo.VideoToolMode.Host, CozyVideo.VideoToolStatus.Missing, "missing remotion", Some("install remotion"))))),
          RecordingRunner()
        )
      }
      assert(missingtool.getMessage.contains("remotion-node is missing"))
      assert(missingtool.getMessage.contains("install remotion"))
    }
  }

  test("video inspect fails explicitly for missing project files") {
    _with_temp_dir("cozy-video-missing-project") { dir =>
      val e = intercept[Throwable] {
        CozyVideo.inspect(CozyVideo.InspectConfig(dir.resolve("missing.json"), checkTools = false), CozyVideo.VideoToolRegistry(Vector.empty))
      }

      assert(e.getMessage.contains("Missing video project file"))
    }
  }

  test("video inspect fails explicitly for unknown options") {
    _with_temp_dir("cozy-video-unknown-option") { dir =>
      _write(dir.resolve("script.json"), _script_json)
      _write(dir.resolve("video_project.json"), _project_json("script.json"))
      val e = intercept[Throwable] {
        CozyVideo.execute(List("video", "inspect", dir.resolve("video_project.json").toString, "--check-toolz"), CozyVideo.VideoToolRegistry(Vector.empty))
      }

      assert(e.getMessage.contains("check-toolz"))
    }
  }

  test("video inspect generates unique ids for anonymous subscenes") {
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
        CozyVideo.VideoScene(None, None, Some("one"), None, None, Some(1.0), None, None, Vector.empty),
        CozyVideo.VideoScene(None, None, Some("two"), None, None, Some(1.0), None, None, Vector.empty)
      )
    )

    assert(scene.expanded(7).flatMap(_.id) == Vector("scene-07.subscene-01", "scene-07.subscene-02"))
  }

  test("video inspect does not call tool probes without check-tools") {
    _with_temp_dir("cozy-video-no-tool-probe") { dir =>
      _write(dir.resolve("script.json"), _script_json)
      _write(dir.resolve("video_project.json"), _project_json("script.json"))
      val probe = RecordingProbe()

      val out = CozyVideo.inspect(
        CozyVideo.InspectConfig(dir.resolve("video_project.json"), checkTools = false),
        CozyVideo.VideoToolRegistry.production(probe)
      )

      assert(!out.contains("tool checks:"))
      assert(probe.commands.isEmpty)
      assert(probe.httpGets.isEmpty)
      assert(probe.existsChecks.isEmpty)
    }
  }

  test("video inspect production registry reports deterministic available tools through probe") {
    _with_temp_dir("cozy-video-tool-probe-available") { dir =>
      val chromium = dir.resolve("chromium")
      val whisper = dir.resolve("models/ggml-base.bin")
      _write(dir.resolve("script.json"), _script_json)
      _write(dir.resolve("video_project.json"), _project_json_with_tools("script.json", whisper.toString))
      _write(chromium, "binary")
      _write(whisper, "model")
      val probe = RecordingProbe(
        commandResults = Map(
          Vector("docker", "version", "--format", "{{.Server.Version}}") -> CozyVideo.VideoCommandResult(0, "25.0\n", ""),
          Vector("docker", "image", "inspect", "simplemodeling/cozy-toolchain:latest") -> CozyVideo.VideoCommandResult(0, "[]", ""),
          Vector("ffmpeg", "-version") -> CozyVideo.VideoCommandResult(0, "ffmpeg", ""),
          Vector("ffprobe", "-version") -> CozyVideo.VideoCommandResult(0, "ffprobe", ""),
          Vector("node", "--version") -> CozyVideo.VideoCommandResult(0, "v22.0.0", ""),
          Vector("npm", "--version") -> CozyVideo.VideoCommandResult(0, "10.0.0", ""),
          Vector("node", "-e", "require.resolve('@remotion/renderer')") -> CozyVideo.VideoCommandResult(0, "/node_modules/@remotion/renderer", ""),
          Vector("node", "-e", "require.resolve('playwright')") -> CozyVideo.VideoCommandResult(0, "/node_modules/playwright", ""),
          Vector("node", "-e", "const { chromium } = require('playwright'); console.log(chromium.executablePath())") -> CozyVideo.VideoCommandResult(0, chromium.toString + "\n", ""),
          Vector("whisper-cli", "--help") -> CozyVideo.VideoCommandResult(0, "usage", "")
        ),
        httpResults = Map(
          "http://127.0.0.1:50021/version" -> CozyVideo.VideoHttpResult(200, "0.0.0")
        )
      )

      val out = CozyVideo.inspect(
        CozyVideo.InspectConfig(dir.resolve("video_project.json"), checkTools = true, toolMode = Some("host")),
        CozyVideo.VideoToolRegistry.production(probe)
      )

      assert(out.contains("docker-toolchain: unchecked (docker)"))
      assert(out.contains("docker-image: unchecked (docker)"))
      assert(out.contains("voicevox: available (external-service)"))
      assert(out.contains("ffmpeg: available (host)"))
      assert(out.contains("remotion-node: available (host)"))
      assert(out.contains("playwright: available (host)"))
      assert(out.contains("whisper-cpp: available (host)"))
      assert(out.contains("python-pillow: missing (host)"))
      assert(out.indexOf("docker-toolchain: unchecked") < out.indexOf("docker-image: unchecked"))
      assert(out.indexOf("docker-image: unchecked") < out.indexOf("voicevox: available"))
      assert(!probe.commands.exists(_.take(3) == Vector("docker", "run", "--rm")))
    }
  }

  test("video inspect production registry reports missing and unchecked tools through probe") {
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
          Vector("docker", "version", "--format", "{{.Server.Version}}") -> CozyVideo.VideoCommandResult(1, "", "docker unavailable"),
          Vector("ffmpeg", "-version") -> CozyVideo.VideoCommandResult(0, "ffmpeg", ""),
          Vector("ffprobe", "-version") -> CozyVideo.VideoCommandResult(127, "", "missing"),
          Vector("node", "--version") -> CozyVideo.VideoCommandResult(0, "v22.0.0", ""),
          Vector("npm", "--version") -> CozyVideo.VideoCommandResult(0, "10.0.0", ""),
          Vector("node", "-e", "require.resolve('@remotion/renderer')") -> CozyVideo.VideoCommandResult(1, "", "missing"),
          Vector("node", "-e", "require.resolve('playwright')") -> CozyVideo.VideoCommandResult(1, "", "missing"),
          Vector("node", "-e", "const { chromium } = require('playwright'); console.log(chromium.executablePath())") -> CozyVideo.VideoCommandResult(1, "", "missing"),
          Vector("whisper-cli", "--help") -> CozyVideo.VideoCommandResult(0, "usage", "")
        ),
        httpResults = Map(
          "http://voicevox.example/version" -> CozyVideo.VideoHttpResult(0, "", Some("connection refused"))
        )
      )

      val out = CozyVideo.inspect(
        CozyVideo.InspectConfig(dir.resolve("video_project.json"), checkTools = true, toolMode = Some("host")),
        CozyVideo.VideoToolRegistry.production(probe)
      )

      assert(out.contains("docker-toolchain: unchecked (docker)"))
      assert(out.contains("docker-image: unchecked (docker)"))
      assert(out.contains("voicevox: missing (external-service)"))
      assert(out.contains("setup: Start VOICEVOX Engine or set tools.voicevoxUrl / video.voicevox.url."))
      assert(out.contains("ffmpeg: missing (host)"))
      assert(out.contains("remotion-node: missing (host)"))
      assert(out.contains("playwright: missing (host)"))
      assert(out.contains("whisper-cpp: missing (host)"))
      assert(out.contains("python-pillow: missing (host)"))
      assert(out.contains("whisper.cpp model is missing: " + dir.resolve("models/missing.bin").normalize()))
    }
  }

  test("video inspect production registry reports missing docker image with pull guidance") {
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
          Vector("docker", "version", "--format", "{{.Server.Version}}") -> CozyVideo.VideoCommandResult(0, "25.0\n", ""),
          Vector("docker", "image", "inspect", "example/toolchain:dev") -> CozyVideo.VideoCommandResult(1, "", "No such image"),
          Vector("ffmpeg", "-version") -> CozyVideo.VideoCommandResult(0, "ffmpeg", ""),
          Vector("ffprobe", "-version") -> CozyVideo.VideoCommandResult(0, "ffprobe", "")
        )
      )

      val out = CozyVideo.inspect(
        CozyVideo.InspectConfig(dir.resolve("video_project.json"), checkTools = true),
        CozyVideo.VideoToolRegistry.production(probe)
      )

      assert(out.contains("docker-toolchain: available (docker)"))
      assert(out.contains("docker-image: missing (docker)"))
      assert(out.contains("setup: Run: docker pull example/toolchain:dev"))
      assert(out.contains("cozy-toolchain-image: unchecked (docker)"))
      assert(!probe.commands.exists(_.take(3) == Vector("docker", "run", "--rm")))
    }
  }

  test("video inspect docker mode check-tools focuses on docker image and voicevox") {
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
          Vector("docker", "version", "--format", "{{.Server.Version}}") -> CozyVideo.VideoCommandResult(0, "25.0\n", ""),
          Vector("docker", "image", "inspect", "example/toolchain:dev") -> CozyVideo.VideoCommandResult(0, "[]", ""),
          Vector("docker", "run", "--rm", "example/toolchain:dev", "cozy-toolchain", "check", "video") -> CozyVideo.VideoCommandResult(0, "cozy-toolchain check video: ok\n", "")
        ),
        httpResults = Map(
          "http://voicevox.example/version" -> CozyVideo.VideoHttpResult(0, "", Some("connection refused"))
        )
      )

      val out = CozyVideo.inspect(
        CozyVideo.InspectConfig(dir.resolve("video_project.json"), checkTools = true),
        CozyVideo.VideoToolRegistry.production(probe)
      )

      assert(out.contains("toolMode: docker"))
      assert(out.contains("docker-image: available (docker)"))
      assert(out.contains("cozy-toolchain-image: available (docker)"))
      assert(out.contains("voicevox: missing (external-service)"))
      assert(out.indexOf("docker-image: available") < out.indexOf("cozy-toolchain-image: available"))
      assert(out.indexOf("cozy-toolchain-image: available") < out.indexOf("voicevox: missing"))
      assert(out.contains("host.docker.internal"))
      assert(out.contains("ffmpeg: unchecked (docker)"))
      assert(out.contains("remotion-node: unchecked (docker)"))
      assert(out.contains("playwright: unchecked (docker)"))
      assert(out.contains("whisper-cpp: unchecked (docker)"))
      assert(out.contains("python-pillow: unchecked (docker)"))
      assert(!probe.commands.exists(_.headOption.contains("ffmpeg")))
      assert(!probe.commands.exists(_.headOption.contains("node")))
      assert(!probe.commands.exists(_.headOption.contains("python3")))
    }
  }

  test("video inspect docker mode reports image content check failure without failing inspect") {
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
          Vector("docker", "version", "--format", "{{.Server.Version}}") -> CozyVideo.VideoCommandResult(0, "25.0\n", ""),
          Vector("docker", "image", "inspect", "example/toolchain:dev") -> CozyVideo.VideoCommandResult(0, "[]", ""),
          Vector("docker", "run", "--rm", "example/toolchain:dev", "cozy-toolchain", "check", "video") -> CozyVideo.VideoCommandResult(1, "", "missing command: whisper-cli")
        )
      )

      val out = CozyVideo.inspect(
        CozyVideo.InspectConfig(dir.resolve("video_project.json"), checkTools = true),
        CozyVideo.VideoToolRegistry.production(probe)
      )

      assert(out.contains("Cozy Video Inspect"))
      assert(out.contains("cozy-toolchain-image: missing (docker)"))
      assert(out.contains("missing command: whisper-cli"))
      assert(out.contains("setup: Rebuild the image: docker build -t example/toolchain:dev docker/cozy-toolchain"))
    }
  }

  test("video inspect production registry reports invalid voicevox URL as missing") {
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
        CozyVideo.InspectConfig(dir.resolve("video_project.json"), checkTools = true),
        CozyVideo.VideoToolRegistry.production(probe)
      )

      assert(out.contains("voicevox: missing (external-service)"))
      assert(out.contains("VOICEVOX endpoint URL is invalid"))
      assert(out.contains("setup: Set tools.voicevoxUrl or video.voicevox.url to a valid HTTP URL."))
      assert(probe.httpGets.isEmpty)
    }
  }

  test("video build dry-run can include production tool checks without failing on missing tools") {
    _with_temp_dir("cozy-video-build-check-tools") { dir =>
      _write(dir.resolve("script.json"), _script_json)
      _write(dir.resolve("video_project.json"), _project_json("script.json"))
      val probe = RecordingProbe()

      val out = CozyVideo.build(
        CozyVideo.BuildConfig(dir.resolve("video_project.json"), dryRun = true, checkTools = true),
        CozyVideo.VideoToolRegistry.production(probe)
      )

      assert(out.contains("Cozy Video Build Dry-Run"))
      assert(out.contains("commands:"))
      assert(out.contains("tool checks:"))
      assert(out.contains("docker-toolchain: missing (docker)"))
      assert(out.contains("voicevox: missing (external-service)"))
    }
  }

  test("video inspect supports stubbed tool registry for deterministic SPI tests") {
    _with_temp_dir("cozy-video-stub-tools") { dir =>
      _write(dir.resolve("script.json"), _script_json)
      _write(dir.resolve("video_project.json"), _project_json("script.json"))
      val registry = CozyVideo.VideoToolRegistry(Vector(
        StubProvider(CozyVideo.VideoToolCheck("alpha", CozyVideo.VideoToolMode.Host, CozyVideo.VideoToolStatus.Available, "alpha ok")),
        StubProvider(CozyVideo.VideoToolCheck("beta", CozyVideo.VideoToolMode.Docker, CozyVideo.VideoToolStatus.Missing, "beta missing", Some("install beta"))),
        StubProvider(CozyVideo.VideoToolCheck("gamma", CozyVideo.VideoToolMode.ExternalService, CozyVideo.VideoToolStatus.Unchecked, "gamma skipped"))
      ))

      val out = CozyVideo.inspect(CozyVideo.InspectConfig(dir.resolve("video_project.json"), checkTools = true), registry)

      assert(out.indexOf("alpha: available (host)") < out.indexOf("beta: missing (docker)"))
      assert(out.indexOf("beta: missing (docker)") < out.indexOf("gamma: unchecked (external-service)"))
      assert(out.contains("setup: install beta"))
    }
  }

  test("cozy video inspect is wired through the Cozy CLI and help") {
    _with_temp_dir("cozy-video-cli") { dir =>
      _write(dir.resolve("script.json"), _script_json)
      _write(dir.resolve("video_project.json"), _project_json("script.json"))
      _write_audio_manifest(dir.resolve("build/audio/intro"), Vector("title", "description", "summary"))
      val out = _capture {
        cozy.Cozy.main(Array("video", "inspect", dir.resolve("video_project.json").toString))
      }
      val build = _capture {
        cozy.Cozy.main(Array(
          "video",
          "build",
          dir.resolve("video_project.json").toString,
          "--dry-run",
          "--tool-mode=host",
          "--docker-image=cli-image"
        ))
      }
      val help = _capture {
        cozy.Cozy.main(Array("--help"))
      }
      val runner = RecordingRunner()
      val render = _capture {
        CozyVideo.execute(
          List("video", "render", dir.resolve("video_project.json").toString, "--renderer=remotion"),
          CozyVideo.VideoToolRegistry(Vector.empty),
          RecordingVoicevoxClient(),
          runner
        )
      }
      val rdf = _capture {
        CozyVideo.execute(
          List("video", "rdf", dir.resolve("video_project.json").toString, "--save", dir.resolve("rdf").toString),
          CozyVideo.VideoToolRegistry(Vector.empty)
        )
      }

      assert(out.contains("Cozy Video Inspect"))
      assert(out.contains("part[1]: intro"))
      assert(build.contains("Cozy Video Build Dry-Run"))
      assert(build.contains("toolMode: host"))
      assert(build.contains("dockerImage: cli-image"))
      assert(render.contains("Cozy Video Render"))
      assert(render.contains("part.intro: " + dir.resolve("build/parts/intro.mp4").normalize()))
      assert(runner.commands.nonEmpty)
      assert(rdf.contains("Cozy Video RDF"))
      assert(Files.isRegularFile(dir.resolve("rdf/video.ttl")))
      assert(help.contains("video inspect <project-file>"))
      assert(help.contains("video build <project-file> [--dry-run]"))
      assert(help.contains("video synthesize <script-file> --save <audio-dir>"))
      assert(help.contains("video render <project-file> --renderer=remotion|simple-java2d"))
      assert(help.contains("video transcribe <input-video> --save <dir>"))
      assert(help.contains("video demo-script <input-video> --save <script-file>"))
      assert(help.contains("video replay <script-file>"))
      assert(help.contains("video rdf <project-file> --save <dir>"))
      assert(help.contains("publish-video <slug>.video"))
      assert(help.contains("--check-tools"))
    }
  }

  test("video demo-script generates replay script from selector event log and transcript") {
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

      val out = CozyVideo.demoScript(CozyVideo.DemoScriptConfig(input, save, eventsFile = Some(events), transcriptFile = Some(transcript)))

      assert(out.contains("Cozy Video Demo Script"))
      assert(out.contains("manualReview: true"))
      val json = parser.parse(_read(save)).toOption.get
      assert(json.hcursor.downField("schema").as[String].toOption.contains("cozy.video.replay-script.v1"))
      assert(json.hcursor.downField("manualReview").as[Boolean].toOption.contains(true))
      assert(json.hcursor.downField("viewport").downField("width").as[Int].toOption.contains(1440))
      val steps = json.hcursor.downField("steps").focus.flatMap(_.asArray).get
      assert(steps.map(_.hcursor.downField("kind").as[String].toOption.get).take(5) == Vector("goto", "click", "fill", "press", "wait"))
      assert(steps.exists(_.hcursor.downField("note").as[String].toOption.contains("Open the start page")))
      assert(json.hcursor.downField("sourceSha256").as[String].toOption.exists(_.nonEmpty))
    }
  }

  test("video demo-script produces manual-review drafts for HAR and video-only inputs") {
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

      CozyVideo.demoScript(CozyVideo.DemoScriptConfig(input, dir.resolve("build/har-demo-script.json"), harFile = Some(har)))
      CozyVideo.demoScript(CozyVideo.DemoScriptConfig(input, dir.resolve("build/video-only-demo-script.json")))

      val harjson = parser.parse(_read(dir.resolve("build/har-demo-script.json"))).toOption.get
      val videojson = parser.parse(_read(dir.resolve("build/video-only-demo-script.json"))).toOption.get
      assert(harjson.hcursor.downField("manualReview").as[Boolean].toOption.contains(true))
      assert(harjson.noSpaces.contains("http://example.test/home"))
      assert(videojson.hcursor.downField("manualReview").as[Boolean].toOption.contains(true))
      assert(videojson.noSpaces.contains("Manual review is required"))
    }
  }

  test("video demo-script validates inputs options and missing files") {
    _with_temp_dir("cozy-video-demo-script-errors") { dir =>
      val input = dir.resolve("demo.mp4")
      _write_bytes(input, Array[Byte](1, 2, 3))

      val missinginput = intercept[RuntimeException] {
        CozyVideo.demoScript(CozyVideo.DemoScriptConfig(dir.resolve("missing.mp4"), dir.resolve("build/demo-script.json")))
      }
      assert(missinginput.getMessage.contains("Missing input video"))

      val missingsave = intercept[RuntimeException] {
        CozyVideo.execute(List("video", "demo-script", input.toString), CozyVideo.VideoToolRegistry(Vector.empty))
      }
      assert(missingsave.getMessage.contains("save"))

      val missingevents = intercept[RuntimeException] {
        CozyVideo.demoScript(CozyVideo.DemoScriptConfig(input, dir.resolve("build/demo-script.json"), eventsFile = Some(dir.resolve("missing-events.json"))))
      }
      assert(missingevents.getMessage.contains("Missing selector event log"))

      val unknown = intercept[RuntimeException] {
        CozyVideo.execute(List("video", "demo-script", input.toString, "--save", dir.resolve("build/demo-script.json").toString, "--unknown"), CozyVideo.VideoToolRegistry(Vector.empty))
      }
      assert(unknown.getMessage.contains("unknown"))
    }
  }

  test("video replay dry-runs and executes generated Playwright plans") {
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
        CozyVideo.ReplayConfig(script, dryRun = true, projectRootOverride = Some(dir)),
        CozyVideo.VideoToolRegistry(Vector.empty),
        ReplayRunner()
      )
      val runner = ReplayRunner()
      val execute = CozyVideo.replay(
        CozyVideo.ReplayConfig(script, saveFile = Some(output), toolMode = Some("host"), projectRootOverride = Some(dir)),
        CozyVideo.VideoToolRegistry(Vector.empty),
        runner
      )

      assert(dryrun.contains("Cozy Video Replay Dry-Run"))
      assert(dryrun.contains("'docker' 'run' '--rm'"))
      assert(dryrun.contains("replay.playwright"))
      assert(execute.contains("Cozy Video Replay"))
      assert(execute.contains("toolMode: host"))
      assert(runner.commands.size == 1)
      assert(runner.commands.head.args.head == "node")
      assert(Files.isRegularFile(output))
      assert(Files.isRegularFile(dir.resolve("target/cozy-video/replay/demo-script/manifest.json")))
      val manifest = _read(dir.resolve("target/cozy-video/replay/demo-script/manifest.json"))
      assert(manifest.contains("cozy.video.replay-manifest.v1"))
      assert(manifest.contains("abc123"))
      assert(manifest.contains("replay.playwright"))
    }
  }

  test("video replay validates inputs runner failures and tool checks") {
    _with_temp_dir("cozy-video-replay-errors") { dir =>
      val script = dir.resolve("build/demo-script.json")
      _write(script, """{"schema":"cozy.video.replay-script.v1","steps":[{"kind":"goto","url":"http://example.test/"}]}""")

      val missing = intercept[RuntimeException] {
        CozyVideo.replay(CozyVideo.ReplayConfig(dir.resolve("missing.json"), projectRootOverride = Some(dir)), CozyVideo.VideoToolRegistry(Vector.empty), ReplayRunner())
      }
      assert(missing.getMessage.contains("Missing video replay script file"))

      val unknown = intercept[RuntimeException] {
        CozyVideo.execute(List("video", "replay", script.toString, "--unknown"), CozyVideo.VideoToolRegistry(Vector.empty))
      }
      assert(unknown.getMessage.contains("unknown"))

      val failure = intercept[RuntimeException] {
        CozyVideo.replay(CozyVideo.ReplayConfig(script, toolMode = Some("host"), projectRootOverride = Some(dir)), CozyVideo.VideoToolRegistry(Vector.empty), ReplayRunner(fail = true))
      }
      assert(failure.getMessage.contains("Playwright replay failed"))

      val mp4output = intercept[RuntimeException] {
        CozyVideo.replay(CozyVideo.ReplayConfig(script, saveFile = Some(dir.resolve("build/replay.mp4")), toolMode = Some("host"), projectRootOverride = Some(dir)), CozyVideo.VideoToolRegistry(Vector.empty), ReplayRunner())
      }
      assert(mp4output.getMessage.contains("must use .webm"))

      val hostcheck = intercept[RuntimeException] {
        CozyVideo.replay(
          CozyVideo.ReplayConfig(script, checkTools = true, toolMode = Some("host"), projectRootOverride = Some(dir)),
          CozyVideo.VideoToolRegistry(Vector(StubProvider(CozyVideo.VideoToolCheck("playwright", CozyVideo.VideoToolMode.Host, CozyVideo.VideoToolStatus.Missing, "missing playwright", Some("install playwright"))))),
          ReplayRunner()
        )
      }
      assert(hostcheck.getMessage.contains("playwright"))
      assert(hostcheck.getMessage.contains("install playwright"))
    }
  }

  test("video rdf includes replay script and replay manifest provenance") {
    _with_temp_dir("cozy-video-rdf-replay") { dir =>
      _write(dir.resolve("script.json"), _script_json)
      _write(dir.resolve("video_project.json"), _project_json("script.json"))
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
          |  "dockerImage": "simplemodeling/cozy-toolchain:latest",
          |  "outputVideo": "build/replay.webm"
          |}
          |""".stripMargin
      )

      CozyVideo.rdf(CozyVideo.RdfConfig(dir.resolve("video_project.json"), dir.resolve("rdf")))

      val turtle = _read(dir.resolve("rdf/video.ttl"))
      assert(turtle.contains("cozy-video:VideoReplay"))
      assert(turtle.contains("cozy-video:VideoReplayStep"))
      assert(turtle.contains("cozy-video:sourceSha256 \"abc123\""))
      assert(turtle.contains("cozy-video:selector \"#start\""))
      assert(turtle.contains("cozy-video:artifactKind \"replay-manifest\""))
    }
  }

  test("video publisher resolves .video descriptors from YAML and JSON") {
    _with_temp_dir("cozy-video-publisher-resolve") { dir =>
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
      val yaml = CozyVideoPublisher.resolve(CozyVideoPublisher.PublishVideoConfig(
        yamlpkg,
        dir.resolve("publication"),
        dir.resolve("warehouse"),
        None,
        force = false
      ))

      assert(yaml.name == "intro")
      assert(yaml.title == "Intro Video")
      assert(yaml.version == "0.1.0")
      assert(yaml.articlePath == "index.dox")
      assert(yaml.scriptPath == "script.json")
      assert(yaml.renderer == "simple-java2d")
      assert(yaml.toolMode == "docker")
      assert(yaml.module == "textus")
      assert(yaml.publicPath == "videos/intro.mp4")

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
      val json = CozyVideoPublisher.resolve(CozyVideoPublisher.PublishVideoConfig(
        jsonpkg,
        dir.resolve("publication"),
        dir.resolve("warehouse"),
        Some("0.2.0"),
        force = false
      ))

      assert(json.name == "custom-video")
      assert(json.version == "0.2.0")
      assert(json.articlePath == "article.dox")
      assert(json.scriptPath == "custom-script.json")
      assert(json.renderer == "remotion")
      assert(json.toolMode == "host")
      assert(json.module == "custom-module")
      assert(json.publicPath == "videos/custom.mp4")
    }
  }

  test("video publisher rejects .video.d source packages") {
    _with_temp_dir("cozy-video-publisher-reject") { dir =>
      val pkg = dir.resolve("bad.video.d")
      Files.createDirectories(pkg)

      val e = intercept[RuntimeException] {
        CozyVideoPublisher.resolve(CozyVideoPublisher.PublishVideoConfig(
          pkg,
          dir.resolve("publication"),
          dir.resolve("warehouse"),
          None,
          force = false
        ))
      }

      assert(e.getMessage.contains("*.video.d is reserved"))
    }
  }

  test("publish-video writes warehouse video artifact and publication metadata outside source package") {
    _with_temp_dir("cozy-video-publisher") { dir =>
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

      val result = CozyVideoPublisher.publish(
        CozyVideoPublisher.PublishVideoConfig(pkg, publication, warehouse, None, force = false),
        RecordingVoicevoxClient(),
        runner
      )

      val artifact = warehouse.resolve("repository/video/textus/0.1.0/tutorial-0.1.0.mp4")
      assert(result.warehouseArtifact == artifact.toAbsolutePath.normalize())
      assert(Files.isRegularFile(artifact))
      assert(Files.isRegularFile(artifact.resolveSibling("tutorial-0.1.0.manifest.json")))
      assert(Files.isRegularFile(artifact.resolveSibling("tutorial-0.1.0.ttl")))
      assert(Files.isRegularFile(artifact.resolveSibling("tutorial-0.1.0.jsonld")))
      assert(Files.isRegularFile(publication.resolve("tutorial.json")))

      val bundle = play.api.libs.json.Json.parse(_read(publication.resolve("tutorial.json")))
      val entries = (bundle \ "entries").as[Vector[play.api.libs.json.JsObject]]
      val video = entries.find(entry => (entry \ "path").as[String] == "metadata/videos/tutorial/metadata.json").get
      val videometadata = (video \ "metadata" \ "video")
      assert((videometadata \ "type").as[String] == "video")
      assert((videometadata \ "name").as[String] == "tutorial")
      assert((videometadata \ "articlePath").as[String] == "index.dox")
      assert((videometadata \ "sourcePackage").as[String] == "concepts/tutorial.video")
      assert((videometadata \ "scriptPath").as[String] == "script.json")
      assert((videometadata \ "artifact" \ "warehousePath").as[String] == "repository/video/textus/0.1.0/tutorial-0.1.0.mp4")
      assert((videometadata \ "artifact" \ "publicPath").as[String] == "videos/tutorial.mp4")
      assert((videometadata \ "artifact" \ "repositoryPublicPath").as[String] == "repository/video/textus/0.1.0/tutorial-0.1.0.mp4")
      assert((videometadata \ "rdf" \ "turtle" \ "path").as[String].endsWith("rdf/video.ttl"))

      val sourcefiles = Files.walk(pkg).iterator().asScala.toVector.filter(Files.isRegularFile(_)).map(_.getFileName.toString)
      assert(!sourcefiles.exists(_.endsWith(".mp4")))
      assert(!sourcefiles.exists(_.endsWith(".ttl")))
      assert(!sourcefiles.exists(_.endsWith(".jsonld")))
      assert(!sourcefiles.exists(_.endsWith(".srt")))
      assert(runner.commands.exists(_.args.contains("python3")))
      assert(runner.commands.exists(_.args.contains("ffmpeg")))
      assert(runner.commands.exists(_.args.contains("ffprobe")))

      val exists = intercept[RuntimeException] {
        CozyVideoPublisher.publish(
          CozyVideoPublisher.PublishVideoConfig(pkg, publication, warehouse, None, force = false),
          RecordingVoicevoxClient(),
          PublishingRunner()
        )
      }
      assert(exists.getMessage.contains("already exists"))
    }
  }

  test("cozy toolchain Docker assets define expected BoK PDF and video checks") {
    val root = Paths.get(sys.props("user.dir")).toAbsolutePath.normalize()
    val dockerfile = _read(root.resolve("docker/cozy-toolchain/Dockerfile"))
    val script = _read(root.resolve("docker/cozy-toolchain/cozy-toolchain"))

    assert(dockerfile.contains("asciidoctor-pdf"))
    assert(dockerfile.contains("@antora/cli"))
    assert(dockerfile.contains("ffmpeg"))
    assert(dockerfile.contains("playwright"))
    assert(dockerfile.contains("@remotion/renderer"))
    assert(dockerfile.contains("python3-pil"))
    assert(dockerfile.contains("whisper.cpp"))
    assert(dockerfile.contains("ggml-base.bin"))
    assert(dockerfile.contains("NODE_PATH"))
    assert(script.contains("check_bok"))
    assert(script.contains("check_pdf"))
    assert(script.contains("check_video"))
    assert(script.contains("node_with_global_modules"))
    assert(script.contains("npm root -g"))
    assert(script.contains("fs.existsSync(path)"))
    assert(script.contains("kroki-server"))
    assert(script.contains("exec \"$@\""))
  }

  private def _with_temp_dir(name: String)(body: Path => Unit): Unit = {
    val root = Paths.get(sys.props("user.dir")).toAbsolutePath.normalize().resolve("target/test-generated/video").resolve(name)
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
  final case class StubProvider(result: CozyVideo.VideoToolCheck) extends CozyVideo.VideoToolProvider {
    def check(context: CozyVideo.VideoToolContext): CozyVideo.VideoToolCheck = result
  }

  final case class VoicevoxCall(
    kind: String,
    baseUrl: String,
    text: Option[String] = None,
    speakerId: Option[Int] = None
  )

  final case class RecordingVoicevoxClient(
    speakersJson: Json = Json.arr(Json.obj(
      "name" -> Json.fromString("ずんだもん"),
      "styles" -> Json.arr(Json.obj("name" -> Json.fromString("ノーマル"), "id" -> Json.fromInt(3)))
    )),
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
      Json.obj("text" -> Json.fromString(text), "speaker" -> Json.fromInt(speakerid))
    }

    def synthesis(baseurl: String, speakerid: Int, audioquery: Json): Array[Byte] = {
      calls += VoicevoxCall("synthesis", baseurl, speakerId = Some(speakerid))
      _audio_queries += audioquery
      _wav_bytes(0.2)
    }
  }

  final case class RecordingProbe(
    commandResults: Map[Vector[String], CozyVideo.VideoCommandResult] = Map.empty,
    httpResults: Map[String, CozyVideo.VideoHttpResult] = Map.empty
  ) extends CozyVideo.VideoToolProbe {
    val commands = ArrayBuffer.empty[Vector[String]]
    val httpGets = ArrayBuffer.empty[String]
    val existsChecks = ArrayBuffer.empty[Path]

    def command(args: Vector[String], cwd: Path): CozyVideo.VideoCommandResult = {
      commands += args
      commandResults.getOrElse(args, CozyVideo.VideoCommandResult(127, "", s"missing: ${args.mkString(" ")}"))
    }

    def httpGet(uri: URI): CozyVideo.VideoHttpResult = {
      httpGets += uri.toString
      httpResults.getOrElse(uri.toString, CozyVideo.VideoHttpResult(0, "", Some("missing endpoint")))
    }

    def exists(path: Path): Boolean = {
      existsChecks += path
      Files.exists(path)
    }
  }

  final case class RecordingCommand(args: Vector[String], cwd: Path)

  final case class RecordingRunner(
    result: CozyVideo.VideoCommandResult = CozyVideo.VideoCommandResult(0, "ok", "")
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
          Files.writeString(_command_path(cwd, args.find(_.endsWith("render_frame.py")).get).getParent.resolve("frame.png"), "png", StandardCharsets.UTF_8)
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
          CozyVideo.VideoCommandResult(0, """{"format":{"duration":"1.000"},"streams":[]}""", "")
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
        val script = args.find(_.endsWith("render_frame.py")).map(_command_path(cwd, _)).get
        Files.writeString(script.getParent.resolve("frame.png"), "png", StandardCharsets.UTF_8)
        CozyVideo.VideoCommandResult(0, "python ok", "")
      } else if (args.contains("ffmpeg")) {
        Files.write(_command_path(cwd, args.last), Array[Byte](0, 0, 0, 0))
        CozyVideo.VideoCommandResult(0, "ffmpeg ok", "")
      } else if (args.contains("ffprobe")) {
        CozyVideo.VideoCommandResult(0, """{"format":{"duration":"1.000"},"streams":[]}""", "")
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
        Files.writeString(base.resolveSibling(base.getFileName.toString + ".json"), _whisper_json, StandardCharsets.UTF_8)
        Files.writeString(base.resolveSibling(base.getFileName.toString + ".srt"), "stub srt", StandardCharsets.UTF_8)
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
          val props = parser.parse(Files.readString(workdir.resolve("props.json"), StandardCharsets.UTF_8)).toOption.get
          props.hcursor.downField("outputPath").as[String].toOption.foreach { output =>
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
    else
      {
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

  private def _write_audio_manifest(dir: Path, scenes: Vector[String], combined: Option[String] = None): Unit = {
    Files.createDirectories(dir)
    scenes.zipWithIndex.foreach {
      case (scene, index) =>
        Files.write(dir.resolve(f"${index + 1}%02d-$scene.wav"), _wav_bytes(0.2))
    }
    combined.foreach { name =>
      Files.write(dir.resolve(name), _wav_bytes(scenes.size.toDouble * 0.2))
    }
    Files.writeString(dir.resolve("manifest.json"), _audio_manifest_json(scenes), StandardCharsets.UTF_8)
  }

  private def _audio_manifest_json(scenes: Vector[String]): String =
    scenes.zipWithIndex.map {
      case (scene, index) =>
        s"""{"sceneId":"$scene","speaker":null,"file":"${f"${index + 1}%02d-$scene.wav"}","leadSilence":0.0,"audioDuration":0.2,"targetDuration":1.0,"tailSilence":0.0}"""
    }.mkString("[", ",", "]")

  private def _project_json(script: String): String =
    s"""{
       |  "title": "Sample Video",
       |  "parts": [
       |    {"id": "intro", "type": "dialogue", "script": "$script"}
       |  ]
       |}
       |""".stripMargin

  private def _project_json_with_tools(script: String, whispermodel: String): String =
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
