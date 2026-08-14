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
private[cozy] trait CozyVideoRuntime {
  self: CozyVideoTypes with CozyVideoCommand with CozyVideoNarration with CozyVideoToolValidation with CozyVideoTranscription with CozyVideoReviewEvidence with CozyVideoBuildReplay with CozyVideoRdf with CozyVideoRenderWorkspace with CozyVideoRenderTemplates with CozyVideoPlanning with CozyVideoPresentation =>
  trait VideoProcessRunner {
    def run(args: Vector[String], cwd: Path): VideoCommandResult
  }
  object VideoProcessRunner {
    val default: VideoProcessRunner = DefaultVideoProcessRunner
  }

  private[video] object DefaultVideoProcessRunner extends VideoProcessRunner {
    def run(args: Vector[String], cwd: Path): VideoCommandResult = {
      implicit val ec: ScalaExecutionContext = ScalaExecutionContext.global
      try {
        val process = new ProcessBuilder(args.asJava).
          directory(cwd.toFile).
          redirectErrorStream(false).
          start()
        val stdoutf = Future(blocking(new String(process.getInputStream.readAllBytes(), StandardCharsets.UTF_8)))
        val stderrf = Future(blocking(new String(process.getErrorStream.readAllBytes(), StandardCharsets.UTF_8)))
        val exit = process.waitFor()
        VideoCommandResult(exit, Await.result(stdoutf, 30.seconds), Await.result(stderrf, 30.seconds))
      } catch {
        case NonFatal(e) => VideoCommandResult(127, "", e.getMessage)
      }
    }
  }

  private[video] final case class WaveData(
    audioformat: Int,
    samplerate: Int,
    channels: Int,
    bitspersample: Int,
    data: Array[Byte]
  ) {
    def durationSeconds: Double =
      if (samplerate <= 0 || channels <= 0 || bitspersample <= 0)
        0.0
      else
        data.length.toDouble / (samplerate.toDouble * channels.toDouble * (bitspersample.toDouble / 8.0))
  }

  private[video] final case class NarrationSelection(
    provider: String,
    diagnostics: Vector[String]
  )

  private[video] def _execution_path(projectroot: Path, execution: VideoExecutionConfig, path: Path): String =
    execution.toolMode match {
      case VideoToolMode.Docker => _docker_path(projectroot, path)
      case _ => path.toString
    }

  private[video] def _execution_command(
    projectroot: Path,
    execution: VideoExecutionConfig,
    tool: String,
    args: Vector[String],
    networkdisabled: Boolean = false
  ): Vector[String] =
    execution.toolMode match {
      case VideoToolMode.Docker =>
        Vector(
          "docker",
          "run",
          "--rm"
        ) ++ (if (networkdisabled) Vector("--network=none") else Vector.empty) ++ Vector(
          "-v",
          s"${projectroot}:/workspace",
          "-w",
          "/workspace",
          execution.dockerImage,
          tool
        ) ++ args
      case VideoToolMode.Host =>
        Vector(tool) ++ args
      case VideoToolMode.ExternalService =>
        RAISE.invalidArgumentFault(s"$tool cannot use external-service tool mode")
    }

  private[video] def _review_execution_command(
    execution: VideoExecutionConfig,
    tool: String,
    args: Vector[String],
    mounts: Vector[DockerMount]
  ): Vector[String] =
    execution.toolMode match {
      case VideoToolMode.Docker =>
        Vector(
          "docker",
          "run",
          "--rm",
          "--network=none"
        ) ++ mounts.flatMap { mount =>
          val access = if (mount.readonly) "ro" else "rw"
          Vector("-v", s"${mount.hostpath}:${mount.containerpath}:$access")
        } ++ Vector(
          execution.dockerImage,
          tool
        ) ++ args
      case VideoToolMode.Host =>
        Vector(tool) ++ args
      case VideoToolMode.ExternalService =>
        RAISE.invalidArgumentFault(s"$tool cannot use external-service tool mode")
    }

  private[video] def _docker_path(projectroot: Path, path: Path): String = {
    val normalized = path.toAbsolutePath.normalize()
    if (normalized.startsWith(projectroot))
      "/workspace/" + projectroot.relativize(normalized).toString
    else
      normalized.toString
  }
}
