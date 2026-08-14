package cozy.video

import org.goldenport.RAISE
import org.goldenport.config.StructuredDocumentLoader
import org.goldenport.context.{FaultException, NetworkIoFault, SubsystemIoFault}
import org.goldenport.io.InputSource
import cozy.config.{CozyProjectContext, CozyProjectYamlConfig}
import cozy.runtime.CozyCliArgs
import org.goldenport.cli.spec
import org.smartdox.semanticweb.{Rdf, RdfRenderer, Vocabulary}
import io.circe.{Decoder, HCursor, Json}
import io.circe.parser
import java.io.ByteArrayOutputStream
import java.net.{URI, URLEncoder}
import java.net.http.{HttpClient, HttpRequest, HttpResponse}
import java.nio.charset.StandardCharsets
import java.nio.file.attribute.BasicFileAttributes
import java.nio.file.{Files, LinkOption, Path, Paths, StandardCopyOption}
import java.security.MessageDigest
import java.time.{Duration => JDuration}
import java.util.concurrent.TimeUnit
import scala.collection.JavaConverters._
import scala.concurrent.{Await, ExecutionContext => ScalaExecutionContext, Future, blocking}
import scala.concurrent.duration._
import scala.util.Try
import scala.util.control.NonFatal

/*
 * @since   Aug. 14, 2026
 * @version Aug. 14, 2026
 * @author  ASAMI, Tomoharu
 */
private[cozy] trait CozyVideoConfig {

  final case class InspectConfig(
    projectFile: Path,
    checkTools: Boolean,
    toolMode: Option[String] = None,
    dockerImage: Option[String] = None
  ) {
    def projectRoot: Path = projectFile.getParent
  }
  object InspectConfig {
    def create(args: List[String]): InspectConfig = {
      val parsed = CozyCliArgs.parseStrict(_p_project_file, _p_check_tools, _p_tool_mode, _p_docker_image)(_normalize_property_args(args))
      val projectfile = parsed.argument("project-file").map(CozyCliArgs.toPath).getOrElse(
        RAISE.invalidArgumentFault("Missing project file for video inspect")
      )
      InspectConfig(projectfile, parsed.flag("check-tools"), parsed.property("tool-mode"), parsed.property("docker-image"))
    }
  }

  final case class BuildConfig(
    projectFile: Path,
    dryRun: Boolean,
    checkTools: Boolean,
    toolMode: Option[String] = None,
    dockerImage: Option[String] = None
  ) {
    def projectRoot: Path = projectFile.getParent
  }
  object BuildConfig {
    def create(args: List[String]): BuildConfig = {
      val parsed = CozyCliArgs.parseStrict(_p_project_file, _p_dry_run, _p_check_tools, _p_tool_mode, _p_docker_image)(_normalize_property_args(args))
      val projectfile = parsed.argument("project-file").map(CozyCliArgs.toPath).getOrElse(
        RAISE.invalidArgumentFault("Missing project file for video build")
      )
      BuildConfig(projectfile, parsed.flag("dry-run"), parsed.flag("check-tools"), parsed.property("tool-mode"), parsed.property("docker-image"))
    }
  }

  final case class RenderConfig(
    projectFile: Path,
    renderer: String,
    part: Option[String] = None,
    checkTools: Boolean = false,
    toolMode: Option[String] = None,
    dockerImage: Option[String] = None
  ) {
    def projectRoot: Path = projectFile.getParent
  }
  object RenderConfig {
    def create(args: List[String]): RenderConfig = {
      val parsed = CozyCliArgs.parseStrict(_p_project_file, _p_renderer, _p_part, _p_check_tools, _p_tool_mode, _p_docker_image)(_normalize_property_args(args))
      val projectfile = parsed.argument("project-file").map(CozyCliArgs.toPath).getOrElse(
        RAISE.invalidArgumentFault("Missing project file for video render")
      )
      RenderConfig(
        projectfile,
        parsed.requiredProperty("renderer"),
        parsed.property("part"),
        parsed.flag("check-tools"),
        parsed.property("tool-mode"),
        parsed.property("docker-image")
      )
    }
  }

  final case class SynthesizeConfig(
    scriptFile: Path,
    saveDir: Path,
    voicevoxUrl: Option[String] = None,
    checkTools: Boolean = false,
    toolMode: Option[String] = None,
    dockerImage: Option[String] = None
  ) {
    def projectRoot: Path =
      Option(scriptFile.getParent).getOrElse(Paths.get(".").toAbsolutePath.normalize())
  }
  object SynthesizeConfig {
    def create(args: List[String]): SynthesizeConfig = {
      val parsed = CozyCliArgs.parseStrict(
        _p_script_file,
        _p_save,
        _p_voicevox_url,
        _p_check_tools,
        _p_tool_mode,
        _p_docker_image
      )(_normalize_property_args(args))
      val scriptfile = parsed.argument("script-file").map(CozyCliArgs.toPath).getOrElse(
        RAISE.invalidArgumentFault("Missing script file for video synthesize")
      )
      SynthesizeConfig(
        scriptfile,
        parsed.requiredPathProperty("save"),
        parsed.property("voicevox-url"),
        parsed.flag("check-tools"),
        parsed.property("tool-mode"),
        parsed.property("docker-image")
      )
    }
  }

  final case class RdfConfig(
    projectFile: Path,
    saveDir: Path,
    toolMode: Option[String] = None,
    dockerImage: Option[String] = None
  ) {
    def projectRoot: Path = projectFile.getParent
  }
  object RdfConfig {
    def create(args: List[String]): RdfConfig = {
      val parsed = CozyCliArgs.parseStrict(_p_project_file, _p_save, _p_tool_mode, _p_docker_image)(_normalize_property_args(args))
      val projectfile = parsed.argument("project-file").map(CozyCliArgs.toPath).getOrElse(
        RAISE.invalidArgumentFault("Missing project file for video rdf")
      )
      RdfConfig(projectfile, parsed.requiredPathProperty("save"), parsed.property("tool-mode"), parsed.property("docker-image"))
    }
  }

  final case class ReviewEvidenceConfig(
    projectFile: Path,
    saveDir: Path,
    checkTools: Boolean = false,
    toolMode: Option[String] = None,
    dockerImage: Option[String] = None
  ) {
    def projectRoot: Path = projectFile.getParent
  }
  object ReviewEvidenceConfig {
    def create(args: List[String]): ReviewEvidenceConfig = {
      val parsed = CozyCliArgs.parseStrict(
        _p_project_file,
        _p_save,
        _p_check_tools,
        _p_tool_mode,
        _p_docker_image
      )(_normalize_property_args(args))
      val projectfile = parsed.argument("project-file").map(CozyCliArgs.toPath).getOrElse(
        RAISE.invalidArgumentFault("Missing project file for video review-evidence")
      )
      ReviewEvidenceConfig(
        projectfile,
        parsed.requiredPathProperty("save"),
        parsed.flag("check-tools"),
        parsed.property("tool-mode"),
        parsed.property("docker-image")
      )
    }
  }

  final case class TranscribeConfig(
    inputVideo: Path,
    saveDir: Path,
    checkTools: Boolean = false,
    toolMode: Option[String] = None,
    dockerImage: Option[String] = None,
    whisperModel: Option[String] = None,
    projectRootOverride: Option[Path] = None
  ) {
    def projectRoot: Path =
      projectRootOverride.getOrElse(Paths.get(sys.props("user.dir"))).toAbsolutePath.normalize()
  }
  object TranscribeConfig {
    def create(args: List[String]): TranscribeConfig =
      create(args, Paths.get(sys.props("user.dir")).toAbsolutePath.normalize())

    def create(args: List[String], projectroot: Path): TranscribeConfig = {
      _validate_transcribe_options(args)
      val parsed = CozyCliArgs.parseStrict(_p_input_video, _p_save, _p_check_tools, _p_tool_mode, _p_docker_image, _p_whisper_model)(_normalize_property_args(args))
      val inputvideo = parsed.argument("input-video").map(CozyCliArgs.toPath).getOrElse(
        RAISE.invalidArgumentFault("Missing input video for video transcribe")
      )
      TranscribeConfig(
        inputvideo,
        parsed.requiredPathProperty("save"),
        parsed.flag("check-tools"),
        parsed.property("tool-mode"),
        parsed.property("docker-image"),
        parsed.property("whisper-model"),
        Some(projectroot)
      )
    }

    private[video] def _validate_transcribe_options(args: List[String]): Unit = {
      val options = Set("save", "check-tools", "tool-mode", "docker-image", "whisper-model")
      args.foreach {
        case x if x.startsWith("--") =>
          val name = x.drop(2).takeWhile(_ != '=')
          if (!options.contains(name))
            RAISE.invalidArgumentFault(s"Unknown option: --$name")
        case _ =>
      }
    }
  }

  final case class DemoScriptConfig(
    inputVideo: Path,
    saveFile: Path,
    eventsFile: Option[Path] = None,
    harFile: Option[Path] = None,
    traceFile: Option[Path] = None,
    transcriptFile: Option[Path] = None
  )
  object DemoScriptConfig {
    def create(args: List[String]): DemoScriptConfig = {
      val parsed = CozyCliArgs.parseStrict(_p_input_video, _p_save, _p_events, _p_har, _p_trace, _p_transcript)(_normalize_property_args(args))
      val inputvideo = parsed.argument("input-video").map(CozyCliArgs.toPath).getOrElse(
        RAISE.invalidArgumentFault("Missing input video for video demo-script")
      )
      DemoScriptConfig(
        inputvideo,
        parsed.requiredPathProperty("save"),
        parsed.property("events").map(Path.of(_)),
        parsed.property("har").map(Path.of(_)),
        parsed.property("trace").map(Path.of(_)),
        parsed.property("transcript").map(Path.of(_))
      )
    }
  }

  final case class ReplayConfig(
    scriptFile: Path,
    saveFile: Option[Path] = None,
    dryRun: Boolean = false,
    checkTools: Boolean = false,
    toolMode: Option[String] = None,
    dockerImage: Option[String] = None,
    projectRootOverride: Option[Path] = None
  ) {
    def projectRoot: Path =
      projectRootOverride.getOrElse(Paths.get(sys.props("user.dir"))).toAbsolutePath.normalize()
  }
  object ReplayConfig {
    def create(args: List[String]): ReplayConfig =
      create(args, Paths.get(sys.props("user.dir")).toAbsolutePath.normalize())

    def create(args: List[String], projectroot: Path): ReplayConfig = {
      val parsed = CozyCliArgs.parseStrict(_p_script_file, _p_save, _p_dry_run, _p_check_tools, _p_tool_mode, _p_docker_image)(_normalize_property_args(args))
      val scriptfile = parsed.argument("script-file").map(CozyCliArgs.toPath).getOrElse(
        RAISE.invalidArgumentFault("Missing script file for video replay")
      )
      ReplayConfig(
        scriptfile,
        parsed.property("save").map(Path.of(_)),
        parsed.flag("dry-run"),
        parsed.flag("check-tools"),
        parsed.property("tool-mode"),
        parsed.property("docker-image"),
        Some(projectroot)
      )
    }
  }

  private[video] val _p_project_file = spec.Parameter.argumentFile("project-file")
  private[video] val _p_input_video = spec.Parameter.argumentFile("input-video")
  private[video] val _p_script_file = spec.Parameter.argumentFile("script-file")
  private[video] val _p_check_tools = spec.Parameter("check-tools", spec.Parameter.SwitchKind)
  private[video] val _p_dry_run = spec.Parameter("dry-run", spec.Parameter.SwitchKind)
  private[video] val _p_save = spec.Parameter.property("save")
  private[video] val _p_renderer = spec.Parameter.property("renderer")
  private[video] val _p_part = spec.Parameter.property("part")
  private[video] val _p_tool_mode = spec.Parameter.property("tool-mode")
  private[video] val _p_docker_image = spec.Parameter.property("docker-image")
  private[video] val _p_voicevox_url = spec.Parameter.property("voicevox-url")
  private[video] val _p_whisper_model = spec.Parameter.property("whisper-model")
  private[video] val _p_events = spec.Parameter.property("events")
  private[video] val _p_har = spec.Parameter.property("har")
  private[video] val _p_trace = spec.Parameter.property("trace")
  private[video] val _p_transcript = spec.Parameter.property("transcript")
  private[video] val _supported_part_types = Set("dialogue", "storyboard", "web-demo")
  private[video] val _supported_renderers = Set("remotion", "simple-java2d")
  private[video] val _docker_managed_tools = Set("remotion", "playwright", "ffmpeg", "ffprobe", "node", "npm", "whisper-cpp", "python-pillow")
  private[video] val _docker_whisper_model = "/opt/textus/models/ggml-base.bin"
  private[video] val _default_piper_model = "en_US-ljspeech-medium"
  private[video] val _voicevox_connection_recovery =
    "Start VOICEVOX Engine or set tools.voicevoxUrl / video.voicevox.url. In Docker mode, use host.docker.internal or a compose service URL when needed."
  private[video] val _property_options = Set("tool-mode", "docker-image", "save", "voicevox-url", "renderer", "part", "whisper-model", "events", "har", "trace", "transcript")
  private[video] val _default_sample_rate = 24000
  private[video] val _default_audio_channels = 1
  private[video] val _default_audio_bits_per_sample = 16
  private[video] val _video_rdf_namespace = "https://www.simplemodeling.org/ns/cozy/video#"
  private[video] val _schema_namespace = "https://schema.org/"
  private[video] val _dcterms_namespace = "http://purl.org/dc/terms/"
  private[video] val _prov_namespace = "http://www.w3.org/ns/prov#"
  private[video] val _xsd_namespace = "http://www.w3.org/2001/XMLSchema#"
  private[video] val _rdf_hex_digits = "0123456789ABCDEF"
  private[video] val _video_rdf_context: Map[String, Any] = Map(
    "rdf" -> Vocabulary.Rdf.namespace,
    "rdfs" -> Vocabulary.Rdfs.namespace,
    "cozy-video" -> _video_rdf_namespace,
    "schema" -> _schema_namespace,
    "dcterms" -> _dcterms_namespace,
    "prov" -> _prov_namespace,
    "xsd" -> _xsd_namespace
  )

  private[video] def _normalize_property_args(args: List[String]): List[String] =
    args.flatMap {
      case x if x.startsWith("--") && x.contains("=") =>
        val keyvalue = x.drop(2).split("=", 2)
        if (keyvalue.length == 2 && _property_options.contains(keyvalue(0)))
          List("--" + keyvalue(0), keyvalue(1))
        else
          List(x)
      case x =>
        List(x)
    }
}
