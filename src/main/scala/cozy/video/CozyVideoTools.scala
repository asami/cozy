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
private[cozy] trait CozyVideoTools {
  self: CozyVideoConfig with CozyVideoModel =>
  sealed trait VideoToolMode { def label: String }
  object VideoToolMode {
    case object Docker extends VideoToolMode { val label = "docker" }
    case object Host extends VideoToolMode { val label = "host" }
    case object ExternalService extends VideoToolMode { val label = "external-service" }

    def parse(value: String): VideoToolMode =
      value.trim.toLowerCase(java.util.Locale.ROOT) match {
        case "docker" => Docker
        case "host" => Host
        case other => RAISE.invalidArgumentFault(s"Invalid video tool mode: $other")
      }
  }

  sealed trait VideoToolStatus { def label: String }
  object VideoToolStatus {
    case object Available extends VideoToolStatus { val label = "available" }
    case object Missing extends VideoToolStatus { val label = "missing" }
    case object Unchecked extends VideoToolStatus { val label = "unchecked" }
    case object Unsupported extends VideoToolStatus { val label = "unsupported" }
  }

  final case class VideoToolCheck(
    name: String,
    mode: VideoToolMode,
    status: VideoToolStatus,
    message: String,
    setupHint: Option[String] = None
  )

  final case class VideoToolContext(
    projectFile: Path,
    projectRoot: Path,
    project: VideoProject,
    execution: VideoExecutionConfig,
    narrationProviders: Set[String] = Set.empty
  ) {
    def settings: VideoToolSettings = project.tools.getOrElse(VideoToolSettings(None, None, None, None))
  }

  final case class VideoExecutionConfig(
    toolMode: VideoToolMode,
    dockerImage: String,
    voicevoxUrl: String
  )

  private[video] final case class DockerMount(
    hostpath: Path,
    containerpath: String,
    readonly: Boolean
  )

  private[video] final case class ReviewEvidenceDockerMounts(
    finalvideo: DockerMount,
    savedir: DockerMount
  )

  object VideoExecutionConfig {
    def create(
      projectroot: Path,
      project: VideoProject,
      toolmode: Option[String],
      dockerimage: Option[String]
    ): VideoExecutionConfig =
      create(projectroot, project.tools, toolmode, dockerimage, None)

    def create(
      projectroot: Path,
      tools: Option[VideoToolSettings],
      toolmode: Option[String],
      dockerimage: Option[String],
      voicevoxurl: Option[String]
    ): VideoExecutionConfig = {
      val config = CozyProjectYamlConfig.loadOperationDefaults(projectroot)
      val scripttools = tools.getOrElse(VideoToolSettings(None, None, None, None))
      val resolvedmode = toolmode.
        orElse(scripttools.toolMode).
        orElse(config.value("video.tool-mode")).
        getOrElse("docker")
      val resolvedimage = dockerimage.
        orElse(scripttools.dockerImage).
        orElse(config.value("video.docker-image")).
        orElse(config.value("cozy.docker-image")).
        getOrElse(VideoToolSettings.DEFAULT_DOCKER_IMAGE)
      val resolvedvoicevox = voicevoxurl.
        orElse(scripttools.voicevoxUrl).
        orElse(config.value("video.voicevox.url")).
        getOrElse(VideoToolSettings.DEFAULT_VOICEVOX_URL)
      VideoExecutionConfig(VideoToolMode.parse(resolvedmode), resolvedimage, resolvedvoicevox)
    }
  }

  trait VideoToolProvider {
    def check(context: VideoToolContext): VideoToolCheck
  }

  final case class VideoToolRegistry(providers: Vector[VideoToolProvider]) {
    def checks(context: VideoToolContext): Vector[VideoToolCheck] = providers.map(_.check(context))
  }
  object VideoToolRegistry {
    val default: VideoToolRegistry = production(VideoToolProbe.default)

    def production(probe: VideoToolProbe): VideoToolRegistry = VideoToolRegistry(Vector(
      DockerToolchainProvider(probe),
      DockerImageProvider(probe),
      TextusToolchainImageProvider(probe),
      VoicevoxProvider(probe),
      MacosSayProvider(probe),
      PiperProvider(probe),
      FfmpegProvider(probe),
      RemotionNodeProvider(probe),
      PlaywrightProvider(probe),
      WhisperCppProvider(probe),
      PythonPillowProvider(probe)
    ))
  }

  final case class UncheckedToolProvider(
    name: String,
    mode: VideoToolMode,
    message: String,
    setupHint: Option[String]
  ) extends VideoToolProvider {
    def check(context: VideoToolContext): VideoToolCheck =
      VideoToolCheck(name, mode, VideoToolStatus.Unchecked, message, setupHint)
  }

  final case class VideoCommandResult(
    exitCode: Int,
    stdout: String,
    stderr: String
  ) {
    def isSuccess: Boolean = exitCode == 0
    def text: String = Vector(stdout.trim, stderr.trim).filter(_.nonEmpty).mkString(" ")
  }

  final case class VideoHttpResult(
    statusCode: Int,
    body: String,
    error: Option[String] = None
  ) {
    def isSuccess: Boolean = statusCode >= 200 && statusCode < 300 && error.isEmpty
  }

  trait VideoToolProbe {
    def command(args: Vector[String], cwd: Path): VideoCommandResult
    def httpGet(uri: URI): VideoHttpResult
    def exists(path: Path): Boolean
  }
  object VideoToolProbe {
    val default: VideoToolProbe = DefaultVideoToolProbe
  }

  private[video] object DefaultVideoToolProbe extends VideoToolProbe {
    private[video] implicit val _ec: ScalaExecutionContext = ScalaExecutionContext.global
    private[video] val _process_timeout = 30.seconds
    private[video] val _http_timeout = JDuration.ofSeconds(3)

    def command(args: Vector[String], cwd: Path): VideoCommandResult =
      try {
        val builder = new ProcessBuilder(args.asJava)
        builder.directory(cwd.toFile)
        val process = builder.start()
        val stdout = Future(blocking(new String(process.getInputStream.readAllBytes())))
        val stderr = Future(blocking(new String(process.getErrorStream.readAllBytes())))
        val finished = process.waitFor(_process_timeout.toMillis, TimeUnit.MILLISECONDS)
        if (!finished) {
          process.destroyForcibly()
          VideoCommandResult(124, "", s"Timed out: ${args.mkString(" ")}")
        } else {
          VideoCommandResult(
            process.exitValue(),
            Await.result(stdout, _process_timeout),
            Await.result(stderr, _process_timeout)
          )
        }
      } catch {
        case NonFatal(e) => VideoCommandResult(127, "", e.getMessage)
      }

    def httpGet(uri: URI): VideoHttpResult =
      try {
        val client = HttpClient.newBuilder().connectTimeout(_http_timeout).build()
        val request = HttpRequest.newBuilder(uri).timeout(_http_timeout).GET().build()
        val response = client.send(request, HttpResponse.BodyHandlers.ofString())
        VideoHttpResult(response.statusCode(), response.body())
      } catch {
        case NonFatal(e) => VideoHttpResult(0, "", Some(e.getMessage))
      }

    def exists(path: Path): Boolean = Files.exists(path)
  }

  trait VoicevoxClient {
    def speakers(baseurl: String): Json
    def audioQuery(baseurl: String, text: String, speakerid: Int): Json
    def synthesis(baseurl: String, speakerid: Int, audioquery: Json): Array[Byte]
  }
  object VoicevoxClient {
    val default: VoicevoxClient = DefaultVoicevoxClient
  }

  final case class NarrationAudio(
    wav: Array[Byte],
    voiceIdentity: Option[String],
    voiceId: Option[String],
    modelIdentity: Option[String]
  )

  trait NarrationProvider {
    def id: String
    def executionMode: String
    def synthesize(text: String, voice: Json): NarrationAudio
  }

  private[video] object DefaultVoicevoxClient extends VoicevoxClient {
    private[video] val _timeout = JDuration.ofSeconds(30)
    private[video] val _client = HttpClient.newBuilder().connectTimeout(JDuration.ofSeconds(5)).build()

    def speakers(baseurl: String): Json =
      _with_voicevox_failure("speakers", baseurl) {
        _request_json(_uri(baseurl, "/speakers"), "GET", None)
      }

    def audioQuery(baseurl: String, text: String, speakerid: Int): Json =
      _with_voicevox_failure("audio_query", baseurl) {
        _request_json(_uri(baseurl, s"/audio_query?text=${_encode(text)}&speaker=$speakerid"), "POST", None)
      }

    def synthesis(baseurl: String, speakerid: Int, audioquery: Json): Array[Byte] =
      _with_voicevox_failure("synthesis", baseurl) {
        _request_bytes(_uri(baseurl, s"/synthesis?speaker=$speakerid"), "POST", Some(audioquery))
      }

    private[video] def _with_voicevox_failure[A](operation: String, baseurl: String)(body: => A): A =
      _with_external_service_failure(operation, "voicevox", baseurl)(body)

    private[video] def _request_json(uri: URI, method: String, body: Option[Json]): Json = {
      val text = new String(_request_bytes(uri, method, body), StandardCharsets.UTF_8)
      parser.parse(text).fold(
        e => RAISE.invalidArgumentFault(s"VOICEVOX returned invalid JSON from $uri: ${e.getMessage}"),
        identity
      )
    }

    private[video] def _request_bytes(uri: URI, method: String, body: Option[Json]): Array[Byte] = {
      val builder = HttpRequest.newBuilder(uri).timeout(_timeout)
      val request =
        body match {
          case Some(json) =>
            builder.header("Content-Type", "application/json").method(method, HttpRequest.BodyPublishers.ofString(json.noSpaces, StandardCharsets.UTF_8)).build()
          case None =>
            builder.method(method, HttpRequest.BodyPublishers.noBody()).build()
        }
      val response = _client.send(request, HttpResponse.BodyHandlers.ofByteArray())
      if (response.statusCode() >= 200 && response.statusCode() < 300)
        response.body()
      else
        RAISE.invalidArgumentFault(s"VOICEVOX HTTP $method $uri failed: ${response.statusCode()}")
    }

    private[video] def _uri(baseurl: String, path: String): URI =
      URI.create(baseurl.stripSuffix("/") + path)

    private[video] def _encode(value: String): String =
      URLEncoder.encode(value, StandardCharsets.UTF_8.name())
  }

  final case class DockerToolchainProvider(probe: VideoToolProbe) extends VideoToolProvider {
    def check(context: VideoToolContext): VideoToolCheck = {
      if (context.execution.toolMode == VideoToolMode.Host)
        return VideoToolCheck(
          "docker-toolchain",
          VideoToolMode.Docker,
          VideoToolStatus.Unchecked,
          "Docker daemon is not required in host tool mode."
        )
      val result = probe.command(Vector("docker", "version", "--format", "{{.Server.Version}}"), context.projectRoot)
      if (result.isSuccess)
        VideoToolCheck("docker-toolchain", VideoToolMode.Docker, VideoToolStatus.Available, "Docker daemon is reachable.")
      else
        VideoToolCheck(
          "docker-toolchain",
          VideoToolMode.Docker,
          VideoToolStatus.Missing,
          _message("Docker daemon is not reachable.", result),
          Some("Install/start Docker Desktop or a compatible Docker daemon.")
        )
    }
  }

  final case class DockerImageProvider(probe: VideoToolProbe) extends VideoToolProvider {
    def check(context: VideoToolContext): VideoToolCheck = {
      val image = context.execution.dockerImage
      if (context.execution.toolMode == VideoToolMode.Host)
        return VideoToolCheck(
          "docker-image",
          VideoToolMode.Docker,
          VideoToolStatus.Unchecked,
          s"Docker image is not required in host tool mode: $image."
        )
      val docker = probe.command(Vector("docker", "version", "--format", "{{.Server.Version}}"), context.projectRoot)
      if (!docker.isSuccess)
        VideoToolCheck(
          "docker-image",
          VideoToolMode.Docker,
          VideoToolStatus.Unchecked,
          s"Docker image was not checked because Docker is unavailable: $image.",
          Some("Start Docker, then run: docker pull " + image)
        )
      else {
        val result = probe.command(Vector("docker", "image", "inspect", image), context.projectRoot)
        if (result.isSuccess)
          VideoToolCheck("docker-image", VideoToolMode.Docker, VideoToolStatus.Available, s"Docker image is available: $image.")
        else
          VideoToolCheck(
            "docker-image",
            VideoToolMode.Docker,
            VideoToolStatus.Missing,
            _message(s"Docker image is missing: $image.", result),
            Some("Run: docker pull " + image)
          )
      }
    }
  }

  final case class TextusToolchainImageProvider(probe: VideoToolProbe) extends VideoToolProvider {
    def check(context: VideoToolContext): VideoToolCheck = {
      val image = context.execution.dockerImage
      if (context.execution.toolMode == VideoToolMode.Host)
        return VideoToolCheck(
          "textus-toolchain-image",
          VideoToolMode.Docker,
          VideoToolStatus.Unchecked,
          s"Textus toolchain image content is not required in host tool mode: $image."
        )
      val docker = probe.command(Vector("docker", "version", "--format", "{{.Server.Version}}"), context.projectRoot)
      if (!docker.isSuccess)
        return VideoToolCheck(
          "textus-toolchain-image",
          VideoToolMode.Docker,
          VideoToolStatus.Unchecked,
          s"Textus toolchain image content was not checked because Docker is unavailable: $image.",
          Some("Start Docker, then run: docker pull " + image)
        )
      val inspect = probe.command(Vector("docker", "image", "inspect", image), context.projectRoot)
      if (!inspect.isSuccess)
        return VideoToolCheck(
          "textus-toolchain-image",
          VideoToolMode.Docker,
          VideoToolStatus.Unchecked,
          s"Textus toolchain image content was not checked because the image is unavailable: $image.",
          Some("Run: docker pull " + image)
        )
      val result = probe.command(Vector("docker", "run", "--rm", image, "textus-toolchain", "check", "video"), context.projectRoot)
      if (result.isSuccess)
        VideoToolCheck(
          "textus-toolchain-image",
          VideoToolMode.Docker,
          VideoToolStatus.Available,
          s"Textus toolchain video dependencies are available in Docker image: $image."
        )
      else
        VideoToolCheck(
          "textus-toolchain-image",
          VideoToolMode.Docker,
          VideoToolStatus.Missing,
          _message(s"Textus toolchain video dependency check failed in Docker image: $image.", result),
          Some("Rebuild and publish the Textus toolchain image in textus-toolchain-runner, then run: docker pull " + image)
        )
    }
  }

  final case class VoicevoxProvider(probe: VideoToolProbe) extends VideoToolProvider {
    def check(context: VideoToolContext): VideoToolCheck =
      if (context.narrationProviders.nonEmpty && !context.narrationProviders.contains("voicevox"))
        VideoToolCheck(
          "voicevox",
          VideoToolMode.ExternalService,
          VideoToolStatus.Unchecked,
          "VOICEVOX was not checked because it is not selected by this video plan."
        )
      else {
        val url = context.execution.voicevoxUrl.stripSuffix("/") + "/version"
        try {
          val result = probe.httpGet(URI.create(url))
          if (result.isSuccess)
            VideoToolCheck("voicevox", VideoToolMode.ExternalService, VideoToolStatus.Available, s"VOICEVOX endpoint is reachable: $url.")
          else
            VideoToolCheck(
              "voicevox",
              VideoToolMode.ExternalService,
              VideoToolStatus.Missing,
              result.error.map(e => s"VOICEVOX endpoint is not reachable: $url ($e)").getOrElse(s"VOICEVOX endpoint returned HTTP ${result.statusCode}: $url."),
              Some("Start VOICEVOX Engine or set tools.voicevoxUrl / video.voicevox.url. In Docker mode, use host.docker.internal or a compose service URL when needed.")
            )
        } catch {
          case NonFatal(e) =>
            VideoToolCheck(
              "voicevox",
              VideoToolMode.ExternalService,
              VideoToolStatus.Missing,
              s"VOICEVOX endpoint URL is invalid: $url (${e.getMessage})",
              Some("Set tools.voicevoxUrl or video.voicevox.url to a valid HTTP URL.")
            )
        }
      }
  }

  final case class MacosSayProvider(probe: VideoToolProbe) extends VideoToolProvider {
    def check(context: VideoToolContext): VideoToolCheck =
      if (context.narrationProviders.nonEmpty && !context.narrationProviders.contains("macos-say"))
        VideoToolCheck(
          "macos-say",
          VideoToolMode.Host,
          VideoToolStatus.Unchecked,
          "macOS say was not checked because it is not selected by this video plan."
        )
      else if (context.execution.toolMode != VideoToolMode.Host)
        VideoToolCheck(
          "macos-say",
          VideoToolMode.Host,
          VideoToolStatus.Unsupported,
          "macOS say narration requires host tool mode.",
          Some("Use --tool-mode=host or select a portable Docker narration provider.")
        )
      else if (!sys.props.getOrElse("os.name", "").toLowerCase(java.util.Locale.ROOT).contains("mac"))
        VideoToolCheck(
          "macos-say",
          VideoToolMode.Host,
          VideoToolStatus.Unsupported,
          "macOS say narration is available only on macOS hosts.",
          Some("Select a portable Docker narration provider on this host.")
        )
      else {
        val say = probe.command(Vector("say", "-v", "?"), context.projectRoot)
        val ffmpeg = probe.command(Vector("ffmpeg", "-version"), context.projectRoot)
        if (say.isSuccess && ffmpeg.isSuccess)
          VideoToolCheck(
            "macos-say",
            VideoToolMode.Host,
            VideoToolStatus.Available,
            "macOS say and ffmpeg are available for host narration."
          )
        else {
          val missing = Vector(
            if (!say.isSuccess) Some("say") else None,
            if (!ffmpeg.isSuccess) Some("ffmpeg") else None
          ).flatten.mkString(", ")
          VideoToolCheck(
            "macos-say",
            VideoToolMode.Host,
            VideoToolStatus.Missing,
            s"macOS say narration tools are unavailable: $missing.",
            Some("Use macOS /usr/bin/say and install ffmpeg, then rerun with --check-tools.")
          )
        }
      }
  }

  final case class PiperProvider(probe: VideoToolProbe) extends VideoToolProvider {
    def check(context: VideoToolContext): VideoToolCheck =
      if (context.narrationProviders.nonEmpty && !context.narrationProviders.contains("piper"))
        VideoToolCheck(
          "piper",
          VideoToolMode.Docker,
          VideoToolStatus.Unchecked,
          "Piper was not checked because it is not selected by this video plan."
        )
      else if (context.execution.toolMode != VideoToolMode.Docker)
        VideoToolCheck(
          "piper",
          VideoToolMode.Docker,
          VideoToolStatus.Unsupported,
          "Piper narration requires Docker tool mode.",
          Some("Use --tool-mode=docker and a Textus toolchain image containing the Piper runtime and models.")
        )
      else {
        val image = context.execution.dockerImage
        val docker = probe.command(Vector("docker", "version", "--format", "{{.Server.Version}}"), context.projectRoot)
        if (!docker.isSuccess)
          VideoToolCheck(
            "piper",
            VideoToolMode.Docker,
            VideoToolStatus.Missing,
            _message("Docker is unavailable for Piper narration.", docker),
            Some("Install or start Docker, then rerun with --check-tools.")
          )
        else {
          val inspect = probe.command(Vector("docker", "image", "inspect", image), context.projectRoot)
          if (!inspect.isSuccess)
            VideoToolCheck(
              "piper",
              VideoToolMode.Docker,
              VideoToolStatus.Missing,
              _message(s"The Piper toolchain image is unavailable: $image.", inspect),
              Some("Build or pull a Textus toolchain image containing Piper, then rerun with --check-tools.")
            )
          else {
            val result = probe.command(
              Vector("docker", "run", "--rm", "--network=none", image, "textus-toolchain", "check", "tts"),
              context.projectRoot
            )
            if (result.isSuccess)
              VideoToolCheck(
                "piper",
                VideoToolMode.Docker,
                VideoToolStatus.Available,
                s"Portable Piper narration is available in Docker image: $image."
              )
            else
              VideoToolCheck(
                "piper",
                VideoToolMode.Docker,
                VideoToolStatus.Missing,
                _message(s"Piper runtime or model validation failed in Docker image: $image.", result),
                Some("Rebuild the Textus toolchain snapshot image and verify: textus-toolchain check tts")
              )
          }
        }
      }
  }

  final case class FfmpegProvider(probe: VideoToolProbe) extends VideoToolProvider {
    def check(context: VideoToolContext): VideoToolCheck = {
      if (context.execution.toolMode == VideoToolMode.Docker)
        return _docker_managed_check("ffmpeg", "ffmpeg/ffprobe", context.execution.dockerImage)
      val ffmpeg = probe.command(Vector("ffmpeg", "-version"), context.projectRoot)
      val ffprobe = probe.command(Vector("ffprobe", "-version"), context.projectRoot)
      if (ffmpeg.isSuccess && ffprobe.isSuccess)
        VideoToolCheck("ffmpeg", VideoToolMode.Host, VideoToolStatus.Available, "ffmpeg and ffprobe are available.")
      else
        VideoToolCheck(
          "ffmpeg",
          VideoToolMode.Host,
          VideoToolStatus.Missing,
          s"ffmpeg/ffprobe check failed: ffmpeg=${ffmpeg.exitCode}, ffprobe=${ffprobe.exitCode}.",
          Some("Install ffmpeg and ffprobe, or use the Cozy Docker toolchain.")
        )
    }
  }

  final case class RemotionNodeProvider(probe: VideoToolProbe) extends VideoToolProvider {
    def check(context: VideoToolContext): VideoToolCheck = {
      if (context.execution.toolMode == VideoToolMode.Docker)
        return _docker_managed_check("remotion-node", "Node/npm and Remotion dependencies", context.execution.dockerImage)
      val node = probe.command(Vector("node", "--version"), context.projectRoot)
      val npm = probe.command(Vector("npm", "--version"), context.projectRoot)
      val remotion = probe.command(Vector("node", "-e", "require.resolve('@remotion/renderer')"), context.projectRoot)
      if (node.isSuccess && npm.isSuccess && remotion.isSuccess)
        VideoToolCheck("remotion-node", VideoToolMode.Host, VideoToolStatus.Available, "Node, npm, and @remotion/renderer are available.")
      else
        VideoToolCheck(
          "remotion-node",
          VideoToolMode.Host,
          VideoToolStatus.Missing,
          s"Remotion host check failed: node=${node.exitCode}, npm=${npm.exitCode}, remotion=${remotion.exitCode}.",
          Some("Install Node/npm and project Remotion dependencies, or use the Cozy Docker toolchain.")
        )
    }
  }

  final case class PlaywrightProvider(probe: VideoToolProbe) extends VideoToolProvider {
    def check(context: VideoToolContext): VideoToolCheck = {
      if (context.execution.toolMode == VideoToolMode.Docker)
        return _docker_managed_check("playwright", "Playwright Chromium", context.execution.dockerImage)
      val module = probe.command(Vector("node", "-e", "require.resolve('playwright')"), context.projectRoot)
      val executable = probe.command(Vector("node", "-e", "const { chromium } = require('playwright'); console.log(chromium.executablePath())"), context.projectRoot)
      val path = executable.stdout.trim
      val chromium = executable.isSuccess && path.nonEmpty && probe.exists(Path.of(path))
      if (module.isSuccess && chromium)
        VideoToolCheck("playwright", VideoToolMode.Host, VideoToolStatus.Available, "Playwright and Chromium are available.")
      else
        VideoToolCheck(
          "playwright",
          VideoToolMode.Host,
          VideoToolStatus.Missing,
          s"Playwright check failed: module=${module.exitCode}, chromium=${if (chromium) "available" else "missing"}.",
          Some("Install Playwright and Chromium, or use the Cozy Docker toolchain. Hint: npx playwright install chromium")
        )
    }
  }

  final case class WhisperCppProvider(probe: VideoToolProbe) extends VideoToolProvider {
    def check(context: VideoToolContext): VideoToolCheck = {
      if (context.execution.toolMode == VideoToolMode.Docker)
        return _docker_managed_check("whisper-cpp", "whisper.cpp binary and model data", context.execution.dockerImage)
      val binary = _first_success(Vector("whisper-cli", "whisper.cpp").map(x => probe.command(Vector(x, "--help"), context.projectRoot)))
      val model = context.settings.whisperModelPath(context.projectRoot)
      (binary, model) match {
        case (Some(_), Some(path)) if probe.exists(path) =>
          VideoToolCheck("whisper-cpp", VideoToolMode.Host, VideoToolStatus.Available, s"whisper.cpp binary and model are available: $path.")
        case (Some(_), Some(path)) =>
          VideoToolCheck(
            "whisper-cpp",
            VideoToolMode.Host,
            VideoToolStatus.Missing,
            s"whisper.cpp model is missing: $path.",
            Some("Place the transcription model at tools.whisperModel or use the Cozy Docker toolchain.")
          )
        case (Some(_), None) =>
          VideoToolCheck(
            "whisper-cpp",
            VideoToolMode.Host,
            VideoToolStatus.Unchecked,
            "whisper.cpp binary is available, but transcription model data is not configured.",
            Some("Set tools.whisperModel or use the Cozy Docker toolchain.")
          )
        case (None, _) =>
          VideoToolCheck(
            "whisper-cpp",
            VideoToolMode.Host,
            VideoToolStatus.Missing,
            "whisper.cpp binary is missing.",
            Some("Install whisper.cpp, provide whisper-cli on PATH, or use the Cozy Docker toolchain.")
          )
      }
    }
  }

  final case class PythonPillowProvider(probe: VideoToolProbe) extends VideoToolProvider {
    def check(context: VideoToolContext): VideoToolCheck = {
      if (context.execution.toolMode == VideoToolMode.Docker)
        return _docker_managed_check("python-pillow", "Python and Pillow helper rendering", context.execution.dockerImage)
      val python = probe.command(Vector("python3", "--version"), context.projectRoot)
      val pillow = probe.command(Vector("python3", "-c", "import PIL; print(PIL.__version__)"), context.projectRoot)
      if (python.isSuccess && pillow.isSuccess)
        VideoToolCheck("python-pillow", VideoToolMode.Host, VideoToolStatus.Available, "Python and Pillow are available.")
      else
        VideoToolCheck(
          "python-pillow",
          VideoToolMode.Host,
          VideoToolStatus.Missing,
          s"Python/Pillow check failed: python=${python.exitCode}, pillow=${pillow.exitCode}.",
          Some("Install Python 3 and Pillow, or use the Cozy Docker toolchain.")
        )
    }
  }

  private[video] def _first_success(results: Vector[VideoCommandResult]): Option[VideoCommandResult] =
    results.find(_.isSuccess)

  private[video] def _message(prefix: String, result: VideoCommandResult): String = {
    val detail = result.text
    if (detail.isEmpty) prefix else s"$prefix $detail"
  }

  private[video] def _docker_managed_check(name: String, label: String, image: String): VideoToolCheck =
    VideoToolCheck(
      name,
      VideoToolMode.Docker,
      VideoToolStatus.Unchecked,
      s"$label are expected to be provided by Docker image: $image.",
      Some("Validate the image contents in VDO-06C; pull with: docker pull " + image)
    )

  private[video] def _with_external_service_failure[A](
    operation: String,
    dependency: String,
    endpoint: String,
    recovery: String = _voicevox_connection_recovery
  )(body: => A): A =
    try {
      body
    } catch {
      case e: FaultException => throw e
      case e: InterruptedException =>
        Thread.currentThread().interrupt()
        _raise_external_service_connection_unavailable(operation, dependency, endpoint, e.getMessage, recovery)
      case NonFatal(e) =>
        _raise_external_service_connection_unavailable(operation, dependency, endpoint, e.getMessage, recovery)
    }

  private[video] def _raise_external_service_connection_unavailable(
    operation: String,
    dependency: String,
    endpoint: String,
    cause: String,
    recovery: String
  ): Nothing =
    _raise_required_dependency_unavailable(
      operation,
      VideoToolCheck(
        dependency,
        VideoToolMode.ExternalService,
        VideoToolStatus.Missing,
        cause,
        Some(recovery)
      ),
      Some(endpoint)
    )

  private[video] def _raise_required_dependency_unavailable(
    operation: String,
    check: VideoToolCheck,
    endpoint: Option[String] = None
  ): Nothing = {
    val category =
      check.mode match {
        case VideoToolMode.ExternalService => "External service connection unavailable"
        case VideoToolMode.Docker | VideoToolMode.Host => "Required execution dependency unavailable"
      }
    val availability =
      check.status match {
        case VideoToolStatus.Missing => "is missing"
        case VideoToolStatus.Unchecked => "is unchecked"
        case _ => "is unavailable"
      }
    val location = endpoint.map(x => s" endpoint=$x;").getOrElse("")
    val recovery = check.setupHint.map(x => s" Recovery: $x").getOrElse("")
    val message = s"$category for $operation: dependency=${check.name} $availability;$location cause=${check.message}.$recovery"
    check.mode match {
      case VideoToolMode.ExternalService => NetworkIoFault(message).RAISE
      case VideoToolMode.Docker | VideoToolMode.Host => SubsystemIoFault(message).RAISE
    }
  }

}
