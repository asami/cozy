package cozy.video

import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path, Paths}
import scala.collection.JavaConverters._
import scala.collection.mutable.ArrayBuffer
import java.net.URI
import org.scalatest.funsuite.AnyFunSuite

/*
 * @since   Jun. 18, 2026
 * @version Jun. 18, 2026
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
      assert(out.contains("part-manifest: planned " + dir.resolve("01/build/manifest.json").normalize()))
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

  test("video build requires dry-run for VDO-05") {
    _with_temp_dir("cozy-video-build-no-dry-run") { dir =>
      _write(dir.resolve("script.json"), _script_json)
      _write(dir.resolve("video_project.json"), _project_json("script.json"))
      val e = intercept[Throwable] {
        CozyVideo.execute(List("video", "build", dir.resolve("video_project.json").toString), CozyVideo.VideoToolRegistry(Vector.empty))
      }

      assert(e.getMessage.contains("without --dry-run is not implemented yet"))
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

      assert(!out.contains("tools:"))
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
      assert(out.contains("tools:"))
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

      assert(out.contains("Cozy Video Inspect"))
      assert(out.contains("part[1]: intro"))
      assert(build.contains("Cozy Video Build Dry-Run"))
      assert(build.contains("toolMode: host"))
      assert(build.contains("dockerImage: cli-image"))
      assert(help.contains("video inspect <project-file>"))
      assert(help.contains("video build <project-file> --dry-run"))
      assert(help.contains("--check-tools"))
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
}
