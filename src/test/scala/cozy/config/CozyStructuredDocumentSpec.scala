package cozy.config

import java.net.URI
import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path, Paths}
import scala.collection.JavaConverters._
import io.circe.Decoder
import org.goldenport.config.StructuredDocumentLoader
import org.goldenport.io.StringInputSource
import org.scalatest.funsuite.AnyFunSuite

/*
 * @since   Jun. 18, 2026
 * @version Jun. 18, 2026
 * @author  ASAMI, Tomoharu
 */
final class CozyStructuredDocumentSpec extends AnyFunSuite {
  import CozyStructuredDocumentSpec._

  test("video project document can be written as JSON, YAML, HOCON, or XML") {
    val expected = VideoProject(
      name = "sample-video",
      title = Some("Sample Video"),
      parts = Vector(VideoPart("intro", "dialogue", "scripts/intro.json"))
    )

    assert(_load_project("video_project.json", _project_json) == expected)
    assert(_load_project("video_project.yaml", _project_yaml) == expected)
    assert(_load_project("video_project.conf", _project_hocon) == expected)
    assert(_load_project("video_project.xml", _project_xml) == expected)
  }

  test("video script document can be written as JSON, YAML, HOCON, or XML") {
    val expected = VideoScript(
      scenes = Vector(
        VideoScene("s1", Some("zundamon"), Some("Hello")),
        VideoScene("s2", None, Some("World"))
      )
    )

    assert(_load_script("script.json", _script_json) == expected)
    assert(_load_script("script.yaml", _script_yaml) == expected)
    assert(_load_script("script.conf", _script_hocon) == expected)
    assert(_load_script("script.xml", _script_xml) == expected)
  }



  test("project metadata and operation defaults accept structured document formats") {
    val root = Paths.get(sys.props("user.dir")).toAbsolutePath.normalize().resolve("target/test-generated/structured-config")
    _delete(root)
    Files.createDirectories(root.resolve("conf/cozy"))
    Files.createDirectories(root.resolve(".cozy"))
    Files.writeString(
      root.resolve("project.xml"),
      """<project name="sample-video" title="Sample Video">
        |  <publication>
        |    <path>textus/samples/tutorial</path>
        |  </publication>
        |</project>
        |""".stripMargin,
      StandardCharsets.UTF_8
    )
    Files.writeString(
      root.resolve("conf/cozy/config.conf"),
      """bok {
        |  docker-image = shared-image
        |}
        |publication {
        |  tags = [alpha, beta]
        |}
        |""".stripMargin,
      StandardCharsets.UTF_8
    )
    Files.writeString(
      root.resolve(".cozy/config.json"),
      """{
        |  "bok": {"docker-image": "local-image"},
        |  "publication": {"tags": ["gamma"]}
        |}
        |""".stripMargin,
      StandardCharsets.UTF_8
    )

    val files = CozyProjectYamlConfig.operationDefaultFiles(root).filter(_.startsWith(root)).map(root.relativize(_).toString).toSet
    val config = CozyProjectYamlConfig.loadProjectConfig(root)

    assert(files.contains("conf/cozy/config.conf"))
    assert(files.contains(".cozy/config.json"))
    assert(config.value("name").contains("sample-video"))
    assert(config.value("title").contains("Sample Video"))
    assert(config.value("publication.path").contains("textus/samples/tutorial"))
    assert(config.value("bok.docker-image").contains("local-image"))
    assert(config.list("publication.tags") == Vector("gamma"))
  }

  private def _load_project(name: String, text: String): VideoProject =
    StructuredDocumentLoader.loadDocument[VideoProject](_source(name, text)).take

  private def _load_script(name: String, text: String): VideoScript =
    StructuredDocumentLoader.loadDocument[VideoScript](_source(name, text)).take

  private def _source(name: String, text: String): StringInputSource =
    StringInputSource(text, new URI(s"memory:///$name"))

  private def _delete(path: Path): Unit =
    if (Files.exists(path)) {
      Files.walk(path).iterator().asScala.toVector.reverse.foreach(Files.delete)
    }
}

object CozyStructuredDocumentSpec {
  final case class VideoProject(
    name: String,
    title: Option[String],
    parts: Vector[VideoPart]
  )
  object VideoProject {
    implicit val decoder: Decoder[VideoProject] = Decoder.forProduct3("name", "title", "parts")(VideoProject.apply)
  }

  final case class VideoPart(
    id: String,
    kind: String,
    script: String
  )
  object VideoPart {
    implicit val decoder: Decoder[VideoPart] = Decoder.forProduct3("id", "kind", "script")(VideoPart.apply)
  }

  final case class VideoScript(
    scenes: Vector[VideoScene]
  )
  object VideoScript {
    implicit val decoder: Decoder[VideoScript] = Decoder.forProduct1("scenes")(VideoScript.apply)
  }

  final case class VideoScene(
    id: String,
    speaker: Option[String],
    line: Option[String]
  )
  object VideoScene {
    implicit val decoder: Decoder[VideoScene] = Decoder.forProduct3("id", "speaker", "line")(VideoScene.apply)
  }

  private val _project_json: String =
    """{
      |  "name": "sample-video",
      |  "title": "Sample Video",
      |  "parts": [
      |    {"id": "intro", "kind": "dialogue", "script": "scripts/intro.json"}
      |  ]
      |}
      |""".stripMargin

  private val _project_yaml: String =
    """name: sample-video
      |title: Sample Video
      |parts:
      |  - id: intro
      |    kind: dialogue
      |    script: scripts/intro.json
      |""".stripMargin

  private val _project_hocon: String =
    """name = sample-video
      |title = "Sample Video"
      |parts = [{ id = intro, kind = dialogue, script = "scripts/intro.json" }]
      |""".stripMargin

  private val _project_xml: String =
    """<project name="sample-video" title="Sample Video">
      |  <parts id="intro">
      |    <kind>dialogue</kind>
      |    <script>scripts/intro.json</script>
      |  </parts>
      |</project>
      |""".stripMargin

  private val _script_json: String =
    """{
      |  "scenes": [
      |    {"id": "s1", "speaker": "zundamon", "line": "Hello"},
      |    {"id": "s2", "line": "World"}
      |  ]
      |}
      |""".stripMargin

  private val _script_yaml: String =
    """scenes:
      |  - id: s1
      |    speaker: zundamon
      |    line: Hello
      |  - id: s2
      |    line: World
      |""".stripMargin

  private val _script_hocon: String =
    """scenes = [
      |  { id = s1, speaker = zundamon, line = Hello },
      |  { id = s2, line = World }
      |]
      |""".stripMargin

  private val _script_xml: String =
    """<script>
      |  <scenes id="s1" speaker="zundamon">
      |    <line>Hello</line>
      |  </scenes>
      |  <scenes id="s2">
      |    <line>World</line>
      |  </scenes>
      |</script>
      |""".stripMargin
}
