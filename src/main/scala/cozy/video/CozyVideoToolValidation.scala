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
import java.time.{Duration => JDuration}
import java.util.concurrent.TimeUnit
import scala.collection.JavaConverters._
import scala.concurrent.{Await, ExecutionContext => ScalaExecutionContext, Future, blocking}
import scala.concurrent.duration._
import scala.util.Try
import scala.util.control.NonFatal

/*
 * @since   Aug. 14, 2026
 * @version Oct. 11, 2026
 * @author  ASAMI, Tomoharu
 */
private[cozy] trait CozyVideoToolValidation {
  self: CozyVideoTypes with CozyVideoRuntime with CozyVideoCommand with CozyVideoNarration with CozyVideoTranscription with CozyVideoReviewEvidence with CozyVideoBuildReplay with CozyVideoRdf with CozyVideoRenderWorkspace with CozyVideoRenderTemplates with CozyVideoPlanning with CozyVideoPresentation =>
  private[video] def _validate_render_tools(renderer: String, execution: VideoExecutionConfig, checks: Vector[VideoToolCheck]): Unit =
    if (checks.nonEmpty) {
      val selected = renderer match {
        case "remotion" => Set("remotion") ++ (if (execution.externalFrameEncoding) Set("ffmpeg") else Set.empty[String])
        case "simple-java2d" => Set("python-pillow", "ffmpeg")
      }
      val required = _required_tool_checks(execution, selected)
      checks.filter(x => required.contains(x.name) && _is_required_dependency_unavailable(x)).headOption.foreach { check =>
        _raise_required_dependency_unavailable(s"render with $renderer", check)
      }
    }

  private[video] def _validate_synthesis_tools(provider: String, checks: Vector[VideoToolCheck]): Unit =
    if (checks.nonEmpty) {
      checks.find(_.name == provider) match {
        case Some(check) if check.status == VideoToolStatus.Available =>
        case Some(check) =>
          _raise_required_dependency_unavailable("synthesize narration", check)
        case None =>
          val mode = provider match {
            case "voicevox" => VideoToolMode.ExternalService
            case "macos-say" => VideoToolMode.Host
            case "piper" => VideoToolMode.Docker
            case other => RAISE.illegalStateFault(s"Unsupported narration provider after selection: $other")
          }
          _raise_required_dependency_unavailable(
            "synthesize narration",
            VideoToolCheck(
              provider,
              mode,
              VideoToolStatus.Unchecked,
              s"No tool check is registered for provider $provider."
            )
          )
      }
    }

  private[video] def _validate_build_tools(execution: VideoExecutionConfig, checks: Vector[VideoToolCheck]): Unit =
    if (checks.nonEmpty) {
      val required = _required_tool_checks(execution, Set("ffmpeg", "ffprobe"))
      checks.filter(x => required.contains(x.name) && _is_required_dependency_unavailable(x)).headOption.foreach { check =>
        _raise_required_dependency_unavailable("build video", check)
      }
    }

  private[video] def _validate_review_evidence_tools(execution: VideoExecutionConfig, checks: Vector[VideoToolCheck]): Unit =
    if (checks.nonEmpty) {
      val required = _required_tool_checks(execution, Set("ffmpeg"))
      checks.filter(x => required.contains(x.name) && _is_required_dependency_unavailable(x)).headOption.foreach { check =>
        _raise_required_dependency_unavailable("extract video review evidence", check)
      }
    }

  private[video] def _is_required_dependency_unavailable(check: VideoToolCheck): Boolean =
    check.status == VideoToolStatus.Missing || check.status == VideoToolStatus.Unchecked || check.status == VideoToolStatus.Unsupported

  private def _required_tool_checks(execution: VideoExecutionConfig, tools: Set[String]): Set[String] = {
    val names = tools.map { case "remotion" => "remotion-node"; case other => other }
    names ++ (if (tools.exists(tool => execution.selected(tool).mode == VideoToolMode.Docker)) Set("docker-toolchain") else Set.empty[String])
  }

  private[video] def _validate_transcribe_tools(execution: VideoTranscribeExecution, checks: Vector[VideoToolCheck]): Unit =
    if (checks.nonEmpty) {
      val resolved = execution.toVideoExecutionConfig
      val required = Set("ffmpeg") ++
        (if (resolved.selected("whisper-cpp").mode == VideoToolMode.Host) Set("whisper-cpp") else Set.empty[String]) ++
        (if (Set("ffmpeg", "whisper-cpp").exists(tool => resolved.selected(tool).mode == VideoToolMode.Docker))
          Set("docker-toolchain", "docker-image") else Set.empty[String])
      checks.filter(x => required.contains(x.name) && _is_required_dependency_unavailable(x)).headOption.foreach { check =>
        val hint = check.setupHint.map(x => s" $x").getOrElse("")
        RAISE.invalidArgumentFault(s"Cannot transcribe video: ${check.name} is not available.${hint}")
      }
    }

  private[video] def _validate_replay_tools(execution: VideoExecutionConfig, checks: Vector[VideoToolCheck]): Unit =
    if (checks.nonEmpty) {
      val required = _required_tool_checks(execution, Set("playwright"))
      checks.filter(x => required.contains(x.name) && _is_required_dependency_unavailable(x)).headOption.foreach { check =>
        val hint = check.setupHint.map(x => s" $x").getOrElse("")
        RAISE.invalidArgumentFault(s"Cannot replay video demo: ${check.name} is not available.${hint}")
      }
    }

  private[video] def _transcribe_execution(config: TranscribeConfig): VideoTranscribeExecution = {
    val resolved = VideoExecutionConfig.create(config.projectRoot, None, config.toolMode, config.dockerImage, None)
    val defaults = CozyProjectYamlConfig.loadOperationDefaults(config.projectRoot)
    val hostmodel = _resolve_whisper_model(config.projectRoot, config.whisperModel.
      orElse(defaults.value("tools.whisperModel")).
      orElse(defaults.value("tools.whisper-model")).
      orElse(defaults.value("video.whisper.model")).
      orElse(defaults.value("video.whisper-model")))
    val modelpath =
      resolved.selected("whisper-cpp").mode match {
        case VideoToolMode.Docker => _docker_whisper_model
        case VideoToolMode.Host =>
          hostmodel.map(_.toString).getOrElse(
            RAISE.invalidArgumentFault("Host transcription requires --whisper-model, tools.whisperModel, or video.whisper.model.")
          )
        case VideoToolMode.ExternalService =>
          RAISE.invalidArgumentFault("Transcription does not support external-service tool mode.")
      }
    VideoTranscribeExecution(resolved.toolMode, resolved.dockerImage, modelpath, hostmodel,
      selections = resolved.selections.filter { case (tool, _) => tool == "ffmpeg" || tool == "whisper-cpp" })
  }

  private[video] def _resolve_whisper_model(projectroot: Path, value: Option[String]): Option[Path] =
    value.map { x =>
      val path = Path.of(x)
      if (path.isAbsolute) path.normalize() else projectroot.resolve(path).normalize()
    }
}
