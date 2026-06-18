package cozy.video

import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path, Paths}
import scala.collection.JavaConverters._
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

  test("video inspect default tool providers are non-executing unchecked checks") {
    _with_temp_dir("cozy-video-default-tools") { dir =>
      _write(dir.resolve("script.json"), _script_json)
      _write(dir.resolve("video_project.json"), _project_json("script.json"))

      val out = CozyVideo.inspect(CozyVideo.InspectConfig(dir.resolve("video_project.json"), checkTools = true), CozyVideo.VideoToolRegistry.default)

      assert(out.contains("tools:"))
      assert(out.contains("docker-toolchain: unchecked (docker)"))
      assert(out.contains("voicevox: unchecked (external-service)"))
      assert(out.contains("ffmpeg: unchecked (host)"))
      assert(out.contains("remotion-node: unchecked (host)"))
      assert(out.contains("playwright: unchecked (host)"))
      assert(out.contains("whisper-cpp: unchecked (host)"))
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
      val help = _capture {
        cozy.Cozy.main(Array("--help"))
      }

      assert(out.contains("Cozy Video Inspect"))
      assert(out.contains("part[1]: intro"))
      assert(help.contains("video inspect <project-file>"))
      assert(help.contains("--check-tools"))
    }
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

  private def _project_json(script: String): String =
    s"""{
       |  "title": "Sample Video",
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
