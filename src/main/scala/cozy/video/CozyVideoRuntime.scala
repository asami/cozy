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
 * @version Oct. 10, 2026
 * @author  ASAMI, Tomoharu
 */
private[cozy] object CozyVideoRuntime {
  import CozyVideoImplementation._

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
}

private[cozy] trait CozyVideoRuntime {
  self: CozyVideoTypes with CozyVideoCommand with CozyVideoNarration with CozyVideoToolValidation with CozyVideoTranscription with CozyVideoReviewEvidence with CozyVideoBuildReplay with CozyVideoRdf with CozyVideoRenderWorkspace with CozyVideoRenderTemplates with CozyVideoPlanning with CozyVideoPresentation =>

  private[video] type WaveData = CozyVideoRuntime.WaveData
  private[video] lazy val WaveData = CozyVideoRuntime.WaveData
  private[video] type NarrationSelection = CozyVideoRuntime.NarrationSelection
  private[video] lazy val NarrationSelection = CozyVideoRuntime.NarrationSelection
  trait VideoProcessRunner {
    def run(args: Vector[String], cwd: Path): VideoCommandResult
    def temporaryRoot: Option[Path] = None
  }
  object VideoProcessRunner {
    val default: VideoProcessRunner = DefaultVideoProcessRunner

    private[cozy] def external(workspace: cozy.document.CozyDocumentProjectWorkRoot.Run): VideoProcessRunner =
      new VideoProcessRunner {
        override def temporaryRoot: Option[Path] = Some(workspace.temporary)
        def run(args: Vector[String], cwd: Path): VideoCommandResult = {
          implicit val ec: ScalaExecutionContext = ScalaExecutionContext.global
          try {
            val directory = cwd.toRealPath()
            if (!directory.startsWith(workspace.root.toRealPath()))
              RAISE.invalidArgumentFault(s"Video child working directory escapes its work run: $directory")
            val temporary = workspace.temporary.toString
            if (temporary.contains('"') || temporary.contains('\n') || temporary.contains('\r'))
              RAISE.invalidArgumentFault("Video temporary path cannot be represented in JAVA_TOOL_OPTIONS")
            val builder = new ProcessBuilder(args.asJava).directory(directory.toFile).redirectErrorStream(false)
            val environment = builder.environment()
            environment.put("TMPDIR", temporary)
            environment.put("TMP", temporary)
            environment.put("TEMP", temporary)
            environment.put("XDG_CACHE_HOME", workspace.cache.toString)
            environment.put("UV_CACHE_DIR", workspace.cache.resolve("uv").toString)
            environment.put("npm_config_cache", workspace.cache.resolve("npm").toString)
            val existing = Option(environment.get("JAVA_TOOL_OPTIONS")).filter(_.nonEmpty).map(_ + " ").getOrElse("")
            environment.put("JAVA_TOOL_OPTIONS", existing + "-Djava.io.tmpdir=\"" + temporary + "\"")
            val process = builder.start()
            val stdout = Future(blocking(new String(process.getInputStream.readAllBytes(), StandardCharsets.UTF_8)))
            val stderr = Future(blocking(new String(process.getErrorStream.readAllBytes(), StandardCharsets.UTF_8)))
            val exit = process.waitFor()
            val result = VideoCommandResult(exit, Await.result(stdout, 30.seconds), Await.result(stderr, 30.seconds))
            val logs = workspace.root.resolve("logs")
            Files.createDirectories(logs)
            val id = java.util.UUID.randomUUID().toString
            Files.writeString(logs.resolve(s"video-$id.stdout.log"), result.stdout, StandardCharsets.UTF_8)
            Files.writeString(logs.resolve(s"video-$id.stderr.log"), result.stderr, StandardCharsets.UTF_8)
            result
          } catch {
            case NonFatal(error) => VideoCommandResult(127, "", Option(error.getMessage).getOrElse(error.getClass.getSimpleName))
          }
        }
      }
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

  private[video] def _execution_path(projectroot: Path, execution: VideoExecutionConfig, path: Path, tool: String = "ffmpeg"): String =
    execution.selected(tool).mode match {
      case VideoToolMode.Docker => _docker_path(projectroot, path)
      case _ => path.toString
    }

  private[video] def _execution_command(
    projectroot: Path,
    execution: VideoExecutionConfig,
    tool: String,
    args: Vector[String],
    networkdisabled: Boolean = false,
    mounts: Vector[DockerMount] = Vector.empty
  ): Vector[String] = {
    val selection = execution.selected(tool)
    selection.mode match {
      case VideoToolMode.Docker =>
        Vector(
          "docker",
          "run",
          "--rm"
        ) ++ (if (networkdisabled) Vector("--network=none") else Vector.empty) ++ Vector(
          "-v",
          s"${projectroot.toAbsolutePath.normalize()}:/workspace",
          "-w",
          "/workspace"
        ) ++ mounts.flatMap { mount =>
          val access = if (mount.readonly) "ro" else "rw"
          Vector("-v", s"${mount.hostpath}:${mount.containerpath}:$access")
        } ++ Vector(
          selection.dockerImage,
          selection.executable
        ) ++ args
      case VideoToolMode.Host =>
        Vector(selection.executable) ++ args
      case VideoToolMode.ExternalService =>
        RAISE.invalidArgumentFault(s"$tool cannot use external-service tool mode")
    }
  }

  private[video] def _review_execution_command(
    execution: VideoExecutionConfig,
    tool: String,
    args: Vector[String],
    mounts: Vector[DockerMount]
  ): Vector[String] = {
    val selection = execution.selected(tool)
    selection.mode match {
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
          selection.dockerImage,
          selection.executable
        ) ++ args
      case VideoToolMode.Host =>
        Vector(selection.executable) ++ args
      case VideoToolMode.ExternalService =>
        RAISE.invalidArgumentFault(s"$tool cannot use external-service tool mode")
    }
  }

  private[video] def _docker_path(projectroot: Path, path: Path): String = {
    val root = projectroot.toAbsolutePath.normalize()
    val normalized = path.toAbsolutePath.normalize()
    if (normalized.startsWith(root))
      "/workspace/" + root.relativize(normalized).toString
    else
      normalized.toString
  }

  private[video] def _selected_path_mounts(projectroot: Path, inputs: Vector[Path], outputs: Vector[Path] = Vector.empty): Vector[DockerMount] = {
    val root = projectroot.toAbsolutePath.normalize()
    val parents = outputs.map(_.toAbsolutePath.normalize().getParent).distinct
    parents.foreach(Files.createDirectories(_))
    val externalparents = parents.filterNot(_.startsWith(root))
    val files = inputs.map(_.toAbsolutePath.normalize()).distinct
      .filterNot(path => path.startsWith(root) || externalparents.exists(path.startsWith(_)))
    files.map(path => DockerMount(path, path.toString, readonly = true)) ++
      externalparents.map(path => DockerMount(path, path.toString, readonly = false))
  }
}
