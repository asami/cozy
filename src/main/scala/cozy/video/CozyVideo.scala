package cozy.video

import org.goldenport.RAISE
import org.goldenport.config.StructuredDocumentLoader
import org.goldenport.io.InputSource
import cozy.config.CozyProjectYamlConfig
import cozy.runtime.CozyCliArgs
import org.goldenport.cli.spec
import io.circe.{Decoder, HCursor, Json}
import io.circe.parser
import java.io.ByteArrayOutputStream
import java.net.{URI, URLEncoder}
import java.net.http.{HttpClient, HttpRequest, HttpResponse}
import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path, Paths, StandardCopyOption}
import java.time.{Duration => JDuration}
import java.util.concurrent.TimeUnit
import scala.collection.JavaConverters._
import scala.concurrent.{Await, ExecutionContext => ScalaExecutionContext, Future, blocking}
import scala.concurrent.duration._
import scala.util.control.NonFatal

/*
 * @since   Jun. 18, 2026
 * @version Jun. 18, 2026
 * @author  ASAMI, Tomoharu
 */
private[cozy] object CozyVideo {
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
    voicevoxUrl: Option[String] = None
  ) {
    def projectRoot: Path =
      Option(scriptFile.getParent).getOrElse(Paths.get(".").toAbsolutePath.normalize())
  }
  object SynthesizeConfig {
    def create(args: List[String]): SynthesizeConfig = {
      val parsed = CozyCliArgs.parseStrict(_p_script_file, _p_save, _p_voicevox_url)(_normalize_property_args(args))
      val scriptfile = parsed.argument("script-file").map(CozyCliArgs.toPath).getOrElse(
        RAISE.invalidArgumentFault("Missing script file for video synthesize")
      )
      SynthesizeConfig(scriptfile, parsed.requiredPathProperty("save"), parsed.property("voicevox-url"))
    }
  }

  final case class VideoProject(
    name: Option[String],
    title: Option[String],
    output: Option[String],
    renderer: Option[VideoRenderer],
    tools: Option[VideoToolSettings],
    parts: Vector[VideoPart]
  )
  object VideoProject {
    implicit val decoder: Decoder[VideoProject] = (c: HCursor) =>
      for {
        name <- c.downField("name").as[Option[String]]
        title <- c.downField("title").as[Option[String]]
        output <- c.downField("output").as[Option[String]]
        renderer <- c.downField("renderer").as[Option[VideoRenderer]]
        tools <- c.downField("tools").as[Option[VideoToolSettings]]
        parts <- c.downField("parts").as[Option[Vector[VideoPart]]]
      } yield VideoProject(name, title, output, renderer, tools, parts.getOrElse(Vector.empty))
  }

  final case class VideoToolSettings(
    toolMode: Option[String],
    dockerImage: Option[String],
    voicevoxUrl: Option[String],
    whisperModel: Option[String]
  ) {
    def dockerImageOrDefault: String =
      dockerImage.getOrElse(VideoToolSettings.DEFAULT_DOCKER_IMAGE)
    def voicevoxUrlOrDefault: String =
      voicevoxUrl.getOrElse(VideoToolSettings.DEFAULT_VOICEVOX_URL)
    def whisperModelPath(projectroot: Path): Option[Path] =
      whisperModel.map { x =>
        val path = Path.of(x)
        if (path.isAbsolute) path.normalize() else projectroot.resolve(path).normalize()
      }
  }
  object VideoToolSettings {
    val DEFAULT_DOCKER_IMAGE = "simplemodeling/cozy-toolchain:latest"
    val DEFAULT_VOICEVOX_URL = "http://127.0.0.1:50021"

    implicit val decoder: Decoder[VideoToolSettings] = (c: HCursor) =>
      for {
        toolmode <- c.downField("toolMode").as[Option[String]].flatMap {
          case Some(s) => Right(Some(s))
          case None => c.downField("tool-mode").as[Option[String]]
        }
        dockerimage <- c.downField("dockerImage").as[Option[String]].flatMap {
          case Some(s) => Right(Some(s))
          case None => c.downField("docker-image").as[Option[String]]
        }
        voicevoxurl <- c.downField("voicevoxUrl").as[Option[String]].flatMap {
          case Some(s) => Right(Some(s))
          case None => c.downField("voicevox-url").as[Option[String]]
        }
        whispermodel <- c.downField("whisperModel").as[Option[String]].flatMap {
          case Some(s) => Right(Some(s))
          case None => c.downField("whisper-model").as[Option[String]]
        }
      } yield VideoToolSettings(toolmode, dockerimage, voicevoxurl, whispermodel)
  }

  final case class VideoPart(
    id: Option[String],
    partType: Option[String],
    script: Option[String],
    steps: Option[String],
    output: Option[String],
    audioDir: Option[String],
    recordDir: Option[String],
    renderer: Option[VideoRenderer]
  ) {
    def displayId(index: Int): String = id.getOrElse(f"part-$index%02d")
    def displayType: String = partType.getOrElse("unknown")
  }
  object VideoPart {
    implicit val decoder: Decoder[VideoPart] = (c: HCursor) =>
      for {
        id <- c.downField("id").as[Option[String]]
        parttype <- c.downField("type").as[Option[String]].flatMap {
          case Some(s) => Right(Some(s))
          case None => c.downField("kind").as[Option[String]]
        }
        script <- c.downField("script").as[Option[String]]
        steps <- c.downField("steps").as[Option[String]]
        output <- c.downField("output").as[Option[String]]
        audiodir <- c.downField("audioDir").as[Option[String]]
        recorddir <- c.downField("recordDir").as[Option[String]]
        renderer <- c.downField("renderer").as[Option[VideoRenderer]]
      } yield VideoPart(id, parttype, script, steps, output, audiodir, recorddir, renderer)
  }

  final case class VideoRenderer(
    engine: Option[String],
    strategy: Option[String],
    fps: Option[Int],
    width: Option[Int],
    height: Option[Int],
    crf: Option[Int],
    x264Preset: Option[String],
    effectProfile: Option[String]
  ) {
    def engineOrDefault: String = engine.getOrElse("legacy")
    def strategyOrPolicy: Option[String] = strategy
    def summary: String = {
      val xs = Vector(
        Some(s"engine=${engineOrDefault}"),
        strategyOrPolicy.map(x => s"strategy=$x"),
        fps.map(x => s"fps=$x"),
        width.map(x => s"width=$x"),
        height.map(x => s"height=$x"),
        crf.map(x => s"crf=$x"),
        x264Preset.map(x => s"x264Preset=$x"),
        effectProfile.map(x => s"effectProfile=$x")
      ).flatten
      xs.mkString(", ")
    }
  }
  object VideoRenderer {
    implicit val decoder: Decoder[VideoRenderer] = (c: HCursor) =>
      for {
        engine <- c.downField("engine").as[Option[String]]
        strategy <- c.downField("strategy").as[Option[String]].flatMap {
          case Some(s) => Right(Some(s))
          case None => c.downField("policy").as[Option[String]]
        }
        fps <- c.downField("fps").as[Option[Int]]
        width <- c.downField("width").as[Option[Int]]
        height <- c.downField("height").as[Option[Int]]
        crf <- c.downField("crf").as[Option[Int]]
        x264preset <- c.downField("x264Preset").as[Option[String]]
        effectprofile <- c.downField("effectProfile").as[Option[String]]
      } yield VideoRenderer(engine, strategy, fps, width, height, crf, x264preset, effectprofile)
  }

  final case class VideoScript(
    title: Option[String],
    tools: Option[VideoToolSettings],
    voice: Json,
    pronunciations: Map[String, String],
    voiceTextNormalization: Json,
    characters: Map[String, Json],
    sections: Vector[Json],
    scenes: Vector[VideoScene]
  ) {
    def expandedScenes: Vector[VideoScene] = scenes.zipWithIndex.flatMap {
      case (scene, index) => scene.expanded(index + 1)
    }
    def estimatedDuration: Double = expandedScenes.map(_.durationSeconds).sum
  }
  object VideoScript {
    implicit val decoder: Decoder[VideoScript] = (c: HCursor) =>
      for {
        title <- c.downField("title").as[Option[String]]
        tools <- c.downField("tools").as[Option[VideoToolSettings]]
        voice <- c.downField("voice").as[Option[Json]]
        pronunciations <- c.downField("pronunciations").as[Option[Map[String, String]]]
        voicetextnormalization <- c.downField("voiceTextNormalization").as[Option[Json]]
        characters <- c.downField("characters").as[Option[Map[String, Json]]]
        sections <- c.downField("sections").as[Option[Vector[Json]]]
        scenes <- c.downField("scenes").as[Option[Vector[VideoScene]]]
      } yield VideoScript(
        title,
        tools,
        voice.getOrElse(Json.obj()),
        pronunciations.getOrElse(Map.empty),
        voicetextnormalization.getOrElse(Json.obj()),
        characters.getOrElse(Map.empty),
        sections.getOrElse(Vector.empty),
        scenes.getOrElse(Vector.empty)
      )
  }

  final case class VideoScene(
    id: Option[String],
    speaker: Option[String],
    line: Option[String],
    narration: Option[String],
    caption: Option[String],
    duration: Option[Double],
    targetDuration: Option[Double],
    leadSilence: Option[Double],
    subscenes: Vector[VideoScene],
    silent: Option[Boolean] = None
  ) {
    def durationSeconds: Double = duration.orElse(targetDuration).getOrElse(8.0)
    def expanded(index: Int): Vector[VideoScene] =
      if (subscenes.isEmpty)
        Vector(if (id.isDefined) this else copy(id = Some(f"scene-$index%02d")))
      else
        subscenes.zipWithIndex.map { case (subscene, subindex) =>
          val parentid = id.getOrElse(f"scene-$index%02d")
          val subid = subscene.id.getOrElse(f"subscene-${subindex + 1}%02d")
          subscene.copy(
            id = Some(if (subid.startsWith(s"$parentid.")) subid else s"$parentid.$subid"),
            speaker = subscene.speaker.orElse(speaker),
            line = subscene.line.orElse(line),
            narration = subscene.narration.orElse(narration),
            caption = subscene.caption.orElse(caption),
            leadSilence = subscene.leadSilence.orElse(leadSilence),
            silent = subscene.silent.orElse(silent)
          )
        }
  }
  object VideoScene {
    implicit lazy val decoder: Decoder[VideoScene] = (c: HCursor) =>
      for {
        id <- c.downField("id").as[Option[String]]
        speaker <- c.downField("speaker").as[Option[String]]
        line <- c.downField("line").as[Option[String]]
        narration <- c.downField("narration").as[Option[String]]
        caption <- c.downField("caption").as[Option[String]]
        duration <- c.downField("duration").as[Option[Double]]
        targetduration <- c.downField("targetDuration").as[Option[Double]]
        leadsilence <- c.downField("leadSilence").as[Option[Double]]
        subscenes <- c.downField("subscenes").as[Option[Vector[VideoScene]]]
        silent <- c.downField("silent").as[Option[Boolean]]
      } yield VideoScene(id, speaker, line, narration, caption, duration, targetduration, leadsilence, subscenes.getOrElse(Vector.empty), silent)
  }

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
    execution: VideoExecutionConfig
  ) {
    def settings: VideoToolSettings = project.tools.getOrElse(VideoToolSettings(None, None, None, None))
  }

  final case class VideoExecutionConfig(
    toolMode: VideoToolMode,
    dockerImage: String,
    voicevoxUrl: String
  )
  object VideoExecutionConfig {
    def create(
      projectroot: Path,
      project: VideoProject,
      toolmode: Option[String],
      dockerimage: Option[String]
    ): VideoExecutionConfig = {
      val config = CozyProjectYamlConfig.loadOperationDefaults(projectroot)
      val projecttools = project.tools.getOrElse(VideoToolSettings(None, None, None, None))
      val resolvedmode = toolmode.
        orElse(projecttools.toolMode).
        orElse(config.value("video.tool-mode")).
        getOrElse("docker")
      val resolvedimage = dockerimage.
        orElse(projecttools.dockerImage).
        orElse(config.value("video.docker-image")).
        orElse(config.value("cozy.docker-image")).
        getOrElse(VideoToolSettings.DEFAULT_DOCKER_IMAGE)
      val resolvedvoicevox = projecttools.voicevoxUrl.
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
      CozyToolchainImageProvider(probe),
      VoicevoxProvider(probe),
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

  private object DefaultVideoToolProbe extends VideoToolProbe {
    private implicit val _ec: ScalaExecutionContext = ScalaExecutionContext.global
    private val _process_timeout = 5.seconds
    private val _http_timeout = JDuration.ofSeconds(3)

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

  private object DefaultVoicevoxClient extends VoicevoxClient {
    private val _timeout = JDuration.ofSeconds(30)
    private val _client = HttpClient.newBuilder().connectTimeout(JDuration.ofSeconds(5)).build()

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

    private def _with_voicevox_failure[A](operation: String, baseurl: String)(body: => A): A =
      try {
        body
      } catch {
        case e: InterruptedException =>
          Thread.currentThread().interrupt()
          RAISE.invalidArgumentFault(s"VOICEVOX $operation failed for $baseurl: ${e.getMessage}")
        case NonFatal(e) =>
          RAISE.invalidArgumentFault(s"VOICEVOX $operation failed for $baseurl: ${e.getMessage}")
      }

    private def _request_json(uri: URI, method: String, body: Option[Json]): Json = {
      val text = new String(_request_bytes(uri, method, body), StandardCharsets.UTF_8)
      parser.parse(text).fold(
        e => RAISE.invalidArgumentFault(s"VOICEVOX returned invalid JSON from $uri: ${e.getMessage}"),
        identity
      )
    }

    private def _request_bytes(uri: URI, method: String, body: Option[Json]): Array[Byte] = {
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

    private def _uri(baseurl: String, path: String): URI =
      URI.create(baseurl.stripSuffix("/") + path)

    private def _encode(value: String): String =
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

  final case class CozyToolchainImageProvider(probe: VideoToolProbe) extends VideoToolProvider {
    def check(context: VideoToolContext): VideoToolCheck = {
      val image = context.execution.dockerImage
      if (context.execution.toolMode == VideoToolMode.Host)
        return VideoToolCheck(
          "cozy-toolchain-image",
          VideoToolMode.Docker,
          VideoToolStatus.Unchecked,
          s"Cozy toolchain image content is not required in host tool mode: $image."
        )
      val docker = probe.command(Vector("docker", "version", "--format", "{{.Server.Version}}"), context.projectRoot)
      if (!docker.isSuccess)
        return VideoToolCheck(
          "cozy-toolchain-image",
          VideoToolMode.Docker,
          VideoToolStatus.Unchecked,
          s"Cozy toolchain image content was not checked because Docker is unavailable: $image.",
          Some("Start Docker, then run: docker pull " + image)
        )
      val inspect = probe.command(Vector("docker", "image", "inspect", image), context.projectRoot)
      if (!inspect.isSuccess)
        return VideoToolCheck(
          "cozy-toolchain-image",
          VideoToolMode.Docker,
          VideoToolStatus.Unchecked,
          s"Cozy toolchain image content was not checked because the image is unavailable: $image.",
          Some("Run: docker pull " + image)
        )
      val result = probe.command(Vector("docker", "run", "--rm", image, "cozy-toolchain", "check", "video"), context.projectRoot)
      if (result.isSuccess)
        VideoToolCheck(
          "cozy-toolchain-image",
          VideoToolMode.Docker,
          VideoToolStatus.Available,
          s"Cozy toolchain video dependencies are available in Docker image: $image."
        )
      else
        VideoToolCheck(
          "cozy-toolchain-image",
          VideoToolMode.Docker,
          VideoToolStatus.Missing,
          _message(s"Cozy toolchain video dependency check failed in Docker image: $image.", result),
          Some("Rebuild the image: docker build -t " + image + " docker/cozy-toolchain")
        )
    }
  }

  final case class VoicevoxProvider(probe: VideoToolProbe) extends VideoToolProvider {
    def check(context: VideoToolContext): VideoToolCheck = {
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

  private def _first_success(results: Vector[VideoCommandResult]): Option[VideoCommandResult] =
    results.find(_.isSuccess)

  private def _message(prefix: String, result: VideoCommandResult): String = {
    val detail = result.text
    if (detail.isEmpty) prefix else s"$prefix $detail"
  }

  private def _docker_managed_check(name: String, label: String, image: String): VideoToolCheck =
    VideoToolCheck(
      name,
      VideoToolMode.Docker,
      VideoToolStatus.Unchecked,
      s"$label are expected to be provided by Docker image: $image.",
      Some("Validate the image contents in VDO-06C; pull with: docker pull " + image)
    )

  sealed trait VideoArtifactStatus { def label: String }
  object VideoArtifactStatus {
    case object Planned extends VideoArtifactStatus { val label = "planned" }
    case object Input extends VideoArtifactStatus { val label = "input" }
    case object MissingInput extends VideoArtifactStatus { val label = "missing-input" }
  }

  final case class VideoArtifactPlan(
    kind: String,
    path: Path,
    producerStep: String,
    status: VideoArtifactStatus
  )

  final case class VideoCommandPlan(
    stepName: String,
    toolName: String,
    mode: VideoToolMode,
    preview: String,
    inputs: Vector[Path],
    outputs: Vector[Path]
  )

  final case class VideoPartPlan(
    index: Int,
    id: String,
    partType: String,
    supported: Boolean,
    renderer: String,
    scriptName: Option[String],
    scriptPath: Option[Path],
    scriptStatus: String,
    script: Option[VideoScript],
    stepsName: Option[String],
    stepsPath: Option[Path],
    stepsStatus: Option[String],
    outputPath: Path,
    audioDir: Option[Path],
    recordDir: Option[Path],
    manifestPath: Path,
    artifacts: Vector[VideoArtifactPlan],
    commands: Vector[VideoCommandPlan]
  ) {
    def estimatedDuration: Option[Double] = script.map(_.estimatedDuration)
    def renderable: Boolean =
      supported && scriptStatus == "found" && (partType != "web-demo" || stepsStatus.contains("found"))
  }

  final case class VideoPlan(
    projectFile: Path,
    projectRoot: Path,
    project: VideoProject,
    execution: VideoExecutionConfig,
    outputPath: Path,
    manifestPath: Path,
    parts: Vector[VideoPartPlan],
    artifacts: Vector[VideoArtifactPlan],
    commands: Vector[VideoCommandPlan]
  )

  private val _p_project_file = spec.Parameter.argumentFile("project-file")
  private val _p_script_file = spec.Parameter.argumentFile("script-file")
  private val _p_check_tools = spec.Parameter("check-tools", spec.Parameter.SwitchKind)
  private val _p_dry_run = spec.Parameter("dry-run", spec.Parameter.SwitchKind)
  private val _p_save = spec.Parameter.property("save")
  private val _p_renderer = spec.Parameter.property("renderer")
  private val _p_part = spec.Parameter.property("part")
  private val _p_tool_mode = spec.Parameter.property("tool-mode")
  private val _p_docker_image = spec.Parameter.property("docker-image")
  private val _p_voicevox_url = spec.Parameter.property("voicevox-url")
  private val _supported_part_types = Set("dialogue", "storyboard", "web-demo")
  private val _docker_managed_tools = Set("remotion", "playwright", "ffmpeg", "ffprobe", "node", "npm", "whisper-cpp", "python-pillow")
  private val _property_options = Set("tool-mode", "docker-image", "save", "voicevox-url", "renderer", "part")
  private val _default_sample_rate = 24000

  def execute(args: List[String]): Boolean = execute(args, VideoToolRegistry.default)

  def execute(args: List[String], tools: VideoToolRegistry): Boolean =
    execute(args, tools, VoicevoxClient.default, VideoProcessRunner.default)

  def execute(args: List[String], tools: VideoToolRegistry, voicevox: VoicevoxClient): Boolean =
    execute(args, tools, voicevox, VideoProcessRunner.default)

  def execute(args: List[String], tools: VideoToolRegistry, voicevox: VoicevoxClient, runner: VideoProcessRunner): Boolean =
    args match {
      case "video" :: "inspect" :: rest =>
        println(inspect(InspectConfig.create(rest), tools))
        true
      case "video" :: "build" :: rest =>
        println(build(BuildConfig.create(rest), tools))
        true
      case "video" :: "synthesize" :: rest =>
        println(synthesize(SynthesizeConfig.create(rest), voicevox))
        true
      case "video" :: "render" :: rest =>
        println(render(RenderConfig.create(rest), tools, runner))
        true
      case "video" :: other :: _ =>
        RAISE.invalidArgumentFault(s"Unsupported video command: $other")
      case _ =>
        false
    }

  def inspect(config: InspectConfig, tools: VideoToolRegistry): String = {
    val plan = _plan(config.projectFile, config.toolMode, config.dockerImage)
    val context = VideoToolContext(config.projectFile, config.projectRoot, plan.project, plan.execution)
    _render_inspect(config, plan, if (config.checkTools) tools.checks(context) else Vector.empty)
  }

  def build(config: BuildConfig, tools: VideoToolRegistry): String = {
    if (!config.dryRun)
      RAISE.invalidArgumentFault("cozy video build without --dry-run is not implemented yet")
    val plan = _plan(config.projectFile, config.toolMode, config.dockerImage)
    val context = VideoToolContext(config.projectFile, config.projectRoot, plan.project, plan.execution)
    _render_build_dry_run(config, plan, if (config.checkTools) tools.checks(context) else Vector.empty)
  }

  def synthesize(config: SynthesizeConfig, voicevox: VoicevoxClient): String = {
    val script = _load_required_script(config.scriptFile)
    val voicevoxurl = _resolve_voicevox_url(config.projectRoot, script, config.voicevoxUrl)
    val result = _synthesize_script(config.scriptFile, script, config.saveDir, voicevoxurl, voicevox)
    _render_synthesis_result(result)
  }

  def render(config: RenderConfig, tools: VideoToolRegistry, runner: VideoProcessRunner): String = {
    if (config.renderer != "remotion")
      RAISE.invalidArgumentFault(s"Unsupported video renderer: ${config.renderer}. VDO-08 supports only remotion.")
    val plan = _plan(config.projectFile, config.toolMode, config.dockerImage)
    val context = VideoToolContext(config.projectFile, config.projectRoot, plan.project, plan.execution)
    val checks = if (config.checkTools) tools.checks(context) else Vector.empty
    _validate_render_tools(plan.execution, checks)
    val result = _render_remotion(config, plan, runner)
    _render_render_result(result)
  }

  private def _load_project(path: Path): VideoProject = {
    if (!Files.isRegularFile(path))
      RAISE.invalidArgumentFault(s"Missing video project file: $path")
    StructuredDocumentLoader.loadDocument[VideoProject](InputSource(path.toFile)).take
  }

  private def _normalize_property_args(args: List[String]): List[String] =
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

  private def _load_script(path: Path): Option[VideoScript] =
    if (Files.isRegularFile(path))
      Some(StructuredDocumentLoader.loadDocument[VideoScript](InputSource(path.toFile)).take)
    else
      None

  private def _load_required_script(path: Path): VideoScript = {
    if (!Files.isRegularFile(path))
      RAISE.invalidArgumentFault(s"Missing video script file: $path")
    StructuredDocumentLoader.loadDocument[VideoScript](InputSource(path.toFile)).take
  }

  private def _resolve_voicevox_url(projectroot: Path, script: VideoScript, cliurl: Option[String]): String = {
    val config = CozyProjectYamlConfig.loadOperationDefaults(projectroot)
    val scripttools = script.tools.getOrElse(VideoToolSettings(None, None, None, None))
    cliurl.
      orElse(scripttools.voicevoxUrl).
      orElse(config.value("video.voicevox.url")).
      getOrElse(VideoToolSettings.DEFAULT_VOICEVOX_URL)
  }

  final case class VideoAudioManifestEntry(
    sceneId: String,
    speaker: Option[String],
    file: String,
    leadSilence: Double,
    audioDuration: Double,
    targetDuration: Double,
    tailSilence: Double
  )
  object VideoAudioManifestEntry {
    implicit val decoder: Decoder[VideoAudioManifestEntry] = (c: HCursor) =>
      for {
        sceneid <- c.downField("sceneId").as[String]
        speaker <- c.downField("speaker").as[Option[String]]
        file <- c.downField("file").as[String]
        leadsilence <- c.downField("leadSilence").as[Double]
        audioduration <- c.downField("audioDuration").as[Double]
        targetduration <- c.downField("targetDuration").as[Double]
        tailsilence <- c.downField("tailSilence").as[Double]
      } yield VideoAudioManifestEntry(sceneid, speaker, file, leadsilence, audioduration, targetduration, tailsilence)
  }

  final case class VideoSynthesisResult(
    scriptFile: Path,
    outputDir: Path,
    voicevoxUrl: String,
    combinedFile: Path,
    manifestFile: Path,
    entries: Vector[VideoAudioManifestEntry]
  )

  final case class VideoRenderedPart(
    id: String,
    outputPath: Path,
    manifestPath: Path,
    remotionWorkDir: Path
  )

  final case class VideoRenderResult(
    projectFile: Path,
    parts: Vector[VideoRenderedPart],
    toolMode: VideoToolMode,
    dockerImage: String
  )

  final case class VideoAudioInput(
    manifestPath: Path,
    entries: Vector[VideoAudioManifestEntry],
    files: Vector[Path]
  )

  trait VideoProcessRunner {
    def run(args: Vector[String], cwd: Path): VideoCommandResult
  }
  object VideoProcessRunner {
    val default: VideoProcessRunner = DefaultVideoProcessRunner
  }

  private object DefaultVideoProcessRunner extends VideoProcessRunner {
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

  private final case class WaveData(
    sampleRate: Int,
    channels: Int,
    bitsPerSample: Int,
    data: Array[Byte]
  ) {
    def durationSeconds: Double =
      if (sampleRate <= 0 || channels <= 0 || bitsPerSample <= 0)
        0.0
      else
        data.length.toDouble / (sampleRate.toDouble * channels.toDouble * (bitsPerSample.toDouble / 8.0))
  }

  private def _synthesize_script(
    scriptfile: Path,
    script: VideoScript,
    savedir: Path,
    voicevoxurl: String,
    voicevox: VoicevoxClient
  ): VideoSynthesisResult = {
    Files.createDirectories(savedir)
    val speakerids = scala.collection.mutable.Map.empty[String, Int]
    val concatparts = scala.collection.mutable.ArrayBuffer.empty[Path]
    val entries = script.expandedScenes.zipWithIndex.map {
      case (scene, index) =>
        val sceneid = scene.id.getOrElse(f"scene-${index + 1}%02d")
        val fileid = _scene_file_id(sceneid)
        val scenewav = _audio_output_file(savedir, f"${index + 1}%02d-$fileid.wav")
        val leadsilence = math.max(0.0, scene.leadSilence.getOrElse(0.0))
        val voice = _voice_for_scene(script, scene)
        if (_is_silent_scene(scene)) {
          _write_silence_wav(scenewav, 0.01)
        } else {
          val cachekey = voice.noSpaces
          val speakerid = speakerids.getOrElseUpdate(cachekey, _resolve_speaker_id(voicevoxurl, voice, voicevox))
          val text = _spoken_text(script, scene)
          val audioquery = _apply_voice_tuning(voicevox.audioQuery(voicevoxurl, text, speakerid), voice)
          Files.write(scenewav, voicevox.synthesis(voicevoxurl, speakerid, audioquery))
        }
        val audioduration = _wav_duration(scenewav)
        val targetduration = scene.durationSeconds
        if (leadsilence > 0) {
          val leadwav = _audio_output_file(savedir, f"${index + 1}%02d-$fileid-lead.wav")
          _write_silence_wav(leadwav, leadsilence)
          concatparts += leadwav
        }
        concatparts += scenewav
        val tailsilence = math.max(0.0, targetduration - leadsilence - audioduration)
        if (tailsilence > 0) {
          val silencewav = _audio_output_file(savedir, f"${index + 1}%02d-$fileid-silence.wav")
          _write_silence_wav(silencewav, tailsilence)
          concatparts += silencewav
        }
        VideoAudioManifestEntry(sceneid, scene.speaker, scenewav.getFileName.toString, _round3(leadsilence), _round3(audioduration), targetduration, _round3(tailsilence))
    }
    val combined = _audio_output_file(savedir, s"${_basename(scriptfile)}.wav")
    _concatenate_wavs(concatparts.toVector, combined)
    val manifest = _audio_output_file(savedir, "manifest.json")
    Files.writeString(manifest, _manifest_json(entries).spaces2, StandardCharsets.UTF_8)
    VideoSynthesisResult(scriptfile, savedir, voicevoxurl, combined, manifest, entries)
  }

  private def _scene_file_id(sceneid: String): String = {
    _file_segment_id(sceneid, "scene id")
  }

  private def _audio_output_file(savedir: Path, filename: String): Path = {
    val path = savedir.resolve(filename).normalize()
    val root = savedir.toAbsolutePath.normalize()
    val absolute = path.toAbsolutePath.normalize()
    if (!absolute.startsWith(root))
      RAISE.invalidArgumentFault(s"Audio output path escapes --save directory: $filename")
    path
  }

  private def _voice_for_scene(script: VideoScript, scene: VideoScene): Json =
    scene.speaker.flatMap { speaker =>
      script.characters.get(speaker).flatMap(_.hcursor.downField("voice").focus)
    }.getOrElse(script.voice)

  private def _resolve_speaker_id(baseurl: String, voice: Json, voicevox: VoicevoxClient): Int = {
    val fallback = _json_int(voice, "fallbackSpeakerId").getOrElse(3)
    val speakername = _json_string(voice, "speakerName").getOrElse("ずんだもん")
    val stylename = _json_string(voice, "styleName").getOrElse("ノーマル")
    val speakers = voicevox.speakers(baseurl).asArray.getOrElse(Vector.empty)
    speakers.foreach { speaker =>
      if (_json_string(speaker, "name").contains(speakername)) {
        _json_array(speaker, "styles").foreach { styles =>
          styles.foreach { style =>
            if (_json_string(style, "name").contains(stylename))
              return _json_int(style, "id").getOrElse(fallback)
          }
        }
      }
    }
    fallback
  }

  private def _spoken_text(script: VideoScript, scene: VideoScene): String = {
    val raw = scene.narration.orElse(scene.line).orElse(scene.caption).getOrElse(
      RAISE.invalidArgumentFault(s"Scene has no narration/line/caption text: ${scene.id.getOrElse("(no id)")}")
    )
    val normalized = _apply_voice_text_normalization(raw, script.voiceTextNormalization)
    script.pronunciations.foldLeft(normalized) {
      case (z, (source, spoken)) => z.replace(source, spoken)
    }
  }

  private def _apply_voice_text_normalization(text: String, options: Json): String = {
    if (_json_boolean(options, "removeSpaces").getOrElse(false))
      text.replaceAll("\\s+", "")
    else if (_json_boolean(options, "removeAsciiJapaneseSpaces").getOrElse(false)) {
      val japanese = "\\u3040-\\u30ff\\u3400-\\u9fff"
      val ascii = "A-Za-z0-9"
      text.
        replaceAll(s"([$japanese])\\s+([$ascii])", "$1$2").
        replaceAll(s"([$ascii])\\s+([$japanese])", "$1$2")
    } else {
      text
    }
  }

  private def _is_silent_scene(scene: VideoScene): Boolean =
    scene.silent.getOrElse(false) || scene.narration.orElse(scene.line).orElse(scene.caption).isEmpty

  private def _apply_voice_tuning(audioquery: Json, voice: Json): Json =
    Vector("speedScale", "pitchScale", "intonationScale", "volumeScale", "prePhonemeLength", "postPhonemeLength").foldLeft(audioquery) { (z, name) =>
      _json_double(voice, name).map(value => z.deepMerge(Json.obj(name -> Json.fromDoubleOrNull(value)))).getOrElse(z)
    }

  private def _write_silence_wav(path: Path, duration: Double): Unit = {
    val frames = math.max(0, (duration * _default_sample_rate).toInt)
    _write_wav(path, WaveData(_default_sample_rate, 1, 16, Array.fill(frames * 2)(0.toByte)))
  }

  private def _wav_duration(path: Path): Double =
    _read_wav(path).durationSeconds

  private def _concatenate_wavs(parts: Vector[Path], output: Path): Unit = {
    if (parts.isEmpty) {
      _write_silence_wav(output, 0.01)
    } else {
      val waves = parts.map(_read_wav)
      val head = waves.head
      waves.tail.foreach { wave =>
        if (wave.sampleRate != head.sampleRate || wave.channels != head.channels || wave.bitsPerSample != head.bitsPerSample)
          RAISE.invalidArgumentFault(s"Cannot concatenate wav with different params: $output")
      }
      val out = new ByteArrayOutputStream()
      waves.foreach(x => out.write(x.data))
      _write_wav(output, head.copy(data = out.toByteArray))
    }
  }

  private def _read_wav(path: Path): WaveData = {
    val bytes = Files.readAllBytes(path)
    if (bytes.length < 44 || _ascii(bytes, 0, 4) != "RIFF" || _ascii(bytes, 8, 4) != "WAVE")
      RAISE.invalidArgumentFault(s"Invalid WAV file: $path")
    var offset = 12
    var samplerate = 0
    var channels = 0
    var bitspersample = 0
    var data = Array.emptyByteArray
    while (offset + 8 <= bytes.length) {
      val id = _ascii(bytes, offset, 4)
      val size = _read_int_le(bytes, offset + 4)
      val start = offset + 8
      if (start + size <= bytes.length) {
        id match {
          case "fmt " =>
            channels = _read_short_le(bytes, start + 2)
            samplerate = _read_int_le(bytes, start + 4)
            bitspersample = _read_short_le(bytes, start + 14)
          case "data" =>
            data = bytes.slice(start, start + size)
          case _ =>
        }
      }
      offset = start + size + (size % 2)
    }
    if (samplerate == 0 || channels == 0 || bitspersample == 0 || data.isEmpty)
      RAISE.invalidArgumentFault(s"Invalid WAV file: $path")
    WaveData(samplerate, channels, bitspersample, data)
  }

  private def _write_wav(path: Path, wave: WaveData): Unit = {
    Files.createDirectories(path.getParent)
    val out = new ByteArrayOutputStream()
    _write_ascii(out, "RIFF")
    _write_int_le(out, 36 + wave.data.length)
    _write_ascii(out, "WAVE")
    _write_ascii(out, "fmt ")
    _write_int_le(out, 16)
    _write_short_le(out, 1)
    _write_short_le(out, wave.channels)
    _write_int_le(out, wave.sampleRate)
    _write_int_le(out, wave.sampleRate * wave.channels * wave.bitsPerSample / 8)
    _write_short_le(out, wave.channels * wave.bitsPerSample / 8)
    _write_short_le(out, wave.bitsPerSample)
    _write_ascii(out, "data")
    _write_int_le(out, wave.data.length)
    out.write(wave.data)
    Files.write(path, out.toByteArray)
  }

  private def _manifest_json(entries: Vector[VideoAudioManifestEntry]): Json =
    Json.fromValues(entries.map { entry =>
      Json.obj(
        "sceneId" -> Json.fromString(entry.sceneId),
        "speaker" -> entry.speaker.map(Json.fromString).getOrElse(Json.Null),
        "file" -> Json.fromString(entry.file),
        "leadSilence" -> Json.fromDoubleOrNull(entry.leadSilence),
        "audioDuration" -> Json.fromDoubleOrNull(entry.audioDuration),
        "targetDuration" -> Json.fromDoubleOrNull(entry.targetDuration),
        "tailSilence" -> Json.fromDoubleOrNull(entry.tailSilence)
      )
    })

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

  private def _read_int_le(bytes: Array[Byte], offset: Int): Int =
    (bytes(offset) & 0xff) |
      ((bytes(offset + 1) & 0xff) << 8) |
      ((bytes(offset + 2) & 0xff) << 16) |
      ((bytes(offset + 3) & 0xff) << 24)

  private def _read_short_le(bytes: Array[Byte], offset: Int): Int =
    (bytes(offset) & 0xff) | ((bytes(offset + 1) & 0xff) << 8)

  private def _ascii(bytes: Array[Byte], offset: Int, length: Int): String =
    new String(bytes, offset, length, StandardCharsets.US_ASCII)

  private def _json_string(json: Json, name: String): Option[String] =
    json.hcursor.downField(name).as[String].toOption

  private def _json_int(json: Json, name: String): Option[Int] =
    json.hcursor.downField(name).as[Int].toOption

  private def _json_double(json: Json, name: String): Option[Double] =
    json.hcursor.downField(name).as[Double].toOption

  private def _json_boolean(json: Json, name: String): Option[Boolean] =
    json.hcursor.downField(name).as[Boolean].toOption

  private def _json_array(json: Json, name: String): Option[Vector[Json]] =
    json.hcursor.downField(name).focus.flatMap(_.asArray)

  private def _basename(path: Path): String = {
    val name = path.getFileName.toString
    val index = name.lastIndexOf('.')
    if (index <= 0) name else name.substring(0, index)
  }

  private def _round3(value: Double): Double =
    BigDecimal(value).setScale(3, BigDecimal.RoundingMode.HALF_UP).toDouble

  private def _validate_render_tools(execution: VideoExecutionConfig, checks: Vector[VideoToolCheck]): Unit =
    if (checks.nonEmpty) {
      val required =
        execution.toolMode match {
          case VideoToolMode.Docker => Set("docker-toolchain", "docker-image", "cozy-toolchain-image")
          case VideoToolMode.Host => Set("remotion-node")
          case VideoToolMode.ExternalService => Set.empty[String]
        }
      checks.filter(x => required.contains(x.name) && x.status == VideoToolStatus.Missing).headOption.foreach { check =>
        val hint = check.setupHint.map(x => s" $x").getOrElse("")
        RAISE.invalidArgumentFault(s"Cannot render with Remotion: ${check.name} is missing.${hint}")
      }
    }

  private def _render_remotion(
    config: RenderConfig,
    plan: VideoPlan,
    runner: VideoProcessRunner
  ): VideoRenderResult = {
    val parts = _render_target_parts(config, plan)
    val rendered = parts.map { part =>
      val script = part.script.getOrElse(RAISE.invalidArgumentFault(s"Part is missing a parsed script: ${part.id}"))
      val audiodir = part.audioDir.getOrElse(RAISE.invalidArgumentFault(s"Part has no audio directory: ${part.id}"))
      val audio = _load_audio_input(part.id, audiodir, script)
      val workdir = _remotion_work_dir(plan.projectRoot, part.id)
      _write_remotion_workspace(plan.projectRoot, part, script, audio, workdir)
      _run_remotion(plan.projectRoot, plan.execution, part, workdir, runner)
      _write_part_manifest(plan, part, audio, workdir)
      VideoRenderedPart(part.id, part.outputPath, part.manifestPath, workdir)
    }
    VideoRenderResult(plan.projectFile, rendered, plan.execution.toolMode, plan.execution.dockerImage)
  }

  private def _render_target_parts(config: RenderConfig, plan: VideoPlan): Vector[VideoPartPlan] = {
    val candidates = config.part match {
      case Some(id) =>
        val part = plan.parts.find(_.id == id).getOrElse(
          RAISE.invalidArgumentFault(s"Unknown video part: $id")
        )
        Vector(part)
      case None =>
        plan.parts.filter(_.renderable)
    }
    if (candidates.isEmpty)
      RAISE.invalidArgumentFault("No renderable video parts found.")
    candidates.foreach { part =>
      if (!part.renderable)
        RAISE.invalidArgumentFault(s"Video part is not renderable: ${part.id}")
    }
    candidates
  }

  private def _load_audio_input(partid: String, audiodir: Path, script: VideoScript): VideoAudioInput = {
    val manifest = audiodir.resolve("manifest.json").normalize()
    if (!Files.isRegularFile(manifest))
      RAISE.invalidArgumentFault(s"Missing audio manifest for part $partid: $manifest. Run: cozy video synthesize <script-file> --save <audio-dir>")
    val entries = parser.decode[Vector[VideoAudioManifestEntry]](Files.readString(manifest, StandardCharsets.UTF_8)).fold(
      e => RAISE.invalidArgumentFault(s"Invalid audio manifest for part $partid: ${e.getMessage}"),
      identity
    )
    val scenes = script.expandedScenes
    if (entries.size != scenes.size)
      RAISE.invalidArgumentFault(s"Audio manifest scene count does not match script for part $partid: ${entries.size} != ${scenes.size}")
    val files = entries.map { entry =>
      val path = audiodir.resolve(entry.file).normalize()
      if (!Files.isRegularFile(path))
        RAISE.invalidArgumentFault(s"Missing audio file for part $partid: $path")
      path
    }
    VideoAudioInput(manifest, entries, files)
  }

  private def _remotion_work_dir(projectroot: Path, partid: String): Path =
    projectroot.resolve("target/cozy-video/remotion").resolve(_file_segment_id(partid, "part id")).normalize()

  private def _write_remotion_workspace(
    projectroot: Path,
    part: VideoPartPlan,
    script: VideoScript,
    audio: VideoAudioInput,
    workdir: Path
  ): Unit = {
    val srcdir = workdir.resolve("src")
    Files.createDirectories(srcdir)
    Files.writeString(workdir.resolve("package.json"), _remotion_package_json, StandardCharsets.UTF_8)
    Files.writeString(srcdir.resolve("Root.tsx"), _remotion_root_tsx, StandardCharsets.UTF_8)
    Files.writeString(srcdir.resolve("render.mjs"), _remotion_render_mjs, StandardCharsets.UTF_8)
    val propsjson = _remotion_props_json(projectroot, part, script, audio)
    Files.writeString(workdir.resolve("props.json"), propsjson.spaces2, StandardCharsets.UTF_8)
    Files.writeString(srcdir.resolve("props.ts"), _remotion_props_ts(propsjson), StandardCharsets.UTF_8)
    _copy_remotion_audio(workdir, audio)
  }

  private def _copy_remotion_audio(workdir: Path, audio: VideoAudioInput): Unit = {
    val audiodir = workdir.resolve("public/audio")
    Files.createDirectories(audiodir)
    audio.files.foreach { file =>
      Files.copy(file, audiodir.resolve(file.getFileName), StandardCopyOption.REPLACE_EXISTING)
    }
  }

  private def _run_remotion(
    projectroot: Path,
    execution: VideoExecutionConfig,
    part: VideoPartPlan,
    workdir: Path,
    runner: VideoProcessRunner
  ): Unit = {
    Files.createDirectories(part.outputPath.getParent)
    val script = workdir.resolve("src/render.mjs")
    val args =
      execution.toolMode match {
        case VideoToolMode.Docker =>
          Vector(
            "docker",
            "run",
            "--rm",
            "-v",
            s"${projectroot}:/workspace",
            "-w",
            "/workspace",
            execution.dockerImage,
            "node",
            _docker_path(projectroot, script)
          )
        case VideoToolMode.Host =>
          Vector("node", script.toString)
        case VideoToolMode.ExternalService =>
          RAISE.invalidArgumentFault("Remotion render cannot use external-service tool mode")
      }
    val result = runner.run(args, projectroot)
    if (!result.isSuccess)
      RAISE.invalidArgumentFault(s"Remotion render failed for part ${part.id}: ${result.stderr.trim}")
  }

  private def _write_part_manifest(
    plan: VideoPlan,
    part: VideoPartPlan,
    audio: VideoAudioInput,
    workdir: Path
  ): Unit = {
    Files.createDirectories(part.manifestPath.getParent)
    val json = Json.obj(
      "partId" -> Json.fromString(part.id),
      "renderer" -> Json.fromString("remotion"),
      "scriptPath" -> Json.fromString(part.scriptPath.map(_.toString).getOrElse("")),
      "audioManifestPath" -> Json.fromString(audio.manifestPath.toString),
      "outputPath" -> Json.fromString(part.outputPath.toString),
      "sceneCount" -> Json.fromInt(part.script.map(_.expandedScenes.size).getOrElse(0)),
      "estimatedDuration" -> Json.fromDoubleOrNull(part.estimatedDuration.getOrElse(0.0)),
      "toolMode" -> Json.fromString(plan.execution.toolMode.label),
      "dockerImage" -> Json.fromString(plan.execution.dockerImage),
      "remotionWorkDir" -> Json.fromString(workdir.toString)
    )
    Files.writeString(part.manifestPath, json.spaces2, StandardCharsets.UTF_8)
  }

  private def _remotion_props_json(projectroot: Path, part: VideoPartPlan, script: VideoScript, audio: VideoAudioInput): Json = {
    val scenes = script.expandedScenes.zip(audio.entries).zip(audio.files).map {
      case ((scene, entry), file) =>
        Json.obj(
          "id" -> Json.fromString(entry.sceneId),
          "speaker" -> entry.speaker.map(Json.fromString).getOrElse(Json.Null),
          "text" -> Json.fromString(scene.narration.orElse(scene.line).orElse(scene.caption).getOrElse("")),
          "audioPath" -> Json.fromString(s"audio/${file.getFileName}"),
          "duration" -> Json.fromDoubleOrNull(entry.targetDuration)
        )
    }
    Json.obj(
      "partId" -> Json.fromString(part.id),
      "outputPath" -> Json.fromString(_project_relative(projectroot, part.outputPath)),
      "fps" -> Json.fromInt(30),
      "width" -> Json.fromInt(1280),
      "height" -> Json.fromInt(720),
      "durationSeconds" -> Json.fromDoubleOrNull(script.estimatedDuration),
      "scenes" -> Json.fromValues(scenes)
    )
  }

  private def _remotion_props_ts(json: Json): String =
    s"""export const cozyVideoProps = ${json.spaces2};
       |""".stripMargin

  private def _project_relative(projectroot: Path, path: Path): String = {
    val root = projectroot.toAbsolutePath.normalize()
    val normalized = path.toAbsolutePath.normalize()
    if (normalized.startsWith(root))
      root.relativize(normalized).toString
    else
      normalized.toString
  }

  private def _file_segment_id(value: String, label: String): String = {
    val normalized = value.trim
    if (normalized.isEmpty || normalized == "." || normalized == ".." || normalized.contains("/") || normalized.contains("\\") || normalized.indexOf(0.toChar) >= 0)
      RAISE.invalidArgumentFault(s"Invalid $label for file name: $value")
    normalized
  }

  private val _remotion_package_json: String =
    """{
      |  "type": "module",
      |  "private": true,
      |  "scripts": {
      |    "render": "node src/render.mjs"
      |  }
      |}
      |""".stripMargin

  private val _remotion_render_mjs: String =
    """import {execFileSync} from 'node:child_process';
      |import fs from 'node:fs';
      |import path from 'node:path';
      |import {fileURLToPath} from 'node:url';
      |
      |const scriptDir = path.dirname(fileURLToPath(import.meta.url));
      |const workDir = path.resolve(scriptDir, '..');
      |const projectRoot = process.env.COZY_PROJECT_ROOT || process.cwd();
      |const propsPath = path.join(workDir, 'props.json');
      |const props = JSON.parse(fs.readFileSync(propsPath, 'utf8'));
      |const entry = path.join(scriptDir, 'Root.tsx');
      |const output = path.resolve(projectRoot, props.outputPath);
      |fs.mkdirSync(path.dirname(output), {recursive: true});
      |execFileSync('remotion', ['render', entry, 'CozyVideo', output, '--overwrite'], {
      |  cwd: projectRoot,
      |  stdio: 'inherit',
      |  env: {...process.env, NODE_PATH: process.env.NODE_PATH || '/usr/local/lib/node_modules'}
      |});
      |""".stripMargin

  private val _remotion_root_tsx: String =
    """import React from 'react';
      |import {AbsoluteFill, Audio, Composition, Sequence, registerRoot, staticFile} from 'remotion';
      |import {cozyVideoProps} from './props';
      |
      |type Scene = {
      |  id: string;
      |  text: string;
      |  audioPath: string;
      |  duration: number;
      |};
      |
      |type Props = {
      |  partId: string;
      |  fps: number;
      |  width: number;
      |  height: number;
      |  durationSeconds: number;
      |  scenes: Scene[];
      |};
      |
      |const SceneCard: React.FC<{scene: Scene}> = ({scene}) => (
      |  <AbsoluteFill style={{backgroundColor: '#101820', color: '#f4efe6', fontFamily: 'Noto Sans CJK JP, sans-serif', alignItems: 'center', justifyContent: 'center', padding: 80}}>
      |    <div style={{fontSize: 56, lineHeight: 1.25, textAlign: 'center'}}>{scene.text || scene.id}</div>
      |    <div style={{position: 'absolute', bottom: 48, right: 64, fontSize: 24, opacity: 0.7}}>{scene.id}</div>
      |    <Audio src={staticFile(scene.audioPath)} />
      |  </AbsoluteFill>
      |);
      |
      |export const CozyVideo: React.FC<Props> = ({scenes, fps}) => {
      |  let start = 0;
      |  return (
      |    <AbsoluteFill>
      |      {scenes.map((scene) => {
      |        const duration = Math.max(1, Math.round((scene.duration || 1) * fps));
      |        const sequence = <Sequence key={scene.id} from={start} durationInFrames={duration}><SceneCard scene={scene} /></Sequence>;
      |        start += duration;
      |        return sequence;
      |      })}
      |    </AbsoluteFill>
      |  );
      |};
      |
      |export const RemotionRoot: React.FC = () => (
      |  <Composition
      |    id="CozyVideo"
      |    component={CozyVideo}
      |    durationInFrames={Math.max(1, Math.round((cozyVideoProps.durationSeconds || 1) * cozyVideoProps.fps))}
      |    fps={cozyVideoProps.fps}
      |    width={cozyVideoProps.width}
      |    height={cozyVideoProps.height}
      |    defaultProps={cozyVideoProps}
      |  />
      |);
      |
      |registerRoot(RemotionRoot);
      |
      |export default RemotionRoot;
      |""".stripMargin

  private def _plan(projectfile: Path, toolmode: Option[String], dockerimage: Option[String]): VideoPlan = {
    val project = _load_project(projectfile)
    val projectroot = projectfile.getParent
    val execution = VideoExecutionConfig.create(projectroot, project, toolmode, dockerimage)
    val outputpath = projectroot.resolve(project.output.getOrElse("build/final.mp4")).normalize()
    val manifestpath = projectroot.resolve("build/manifest.json").normalize()
    val parts = project.parts.zipWithIndex.map {
      case (part, index) => _part_plan(projectroot, project, part, index + 1)
    }
    val artifacts = Vector(
      VideoArtifactPlan("project-output", outputpath, "project.concat", VideoArtifactStatus.Planned),
      VideoArtifactPlan("project-manifest", manifestpath, "project.manifest", VideoArtifactStatus.Planned)
    ) ++ parts.flatMap(_.artifacts)
    val partoutputs = parts.filter(_.renderable).map(_.outputPath)
    val rawcommands =
      parts.flatMap(_.commands) ++
        Vector(
          VideoCommandPlan(
            "project.concat",
            "ffmpeg",
            VideoToolMode.Host,
            "concat planned part outputs into final video",
            partoutputs,
            Vector(outputpath)
          ),
          VideoCommandPlan(
            "project.manifest",
            "cozy",
            VideoToolMode.Host,
            "write project manifest",
            Vector(outputpath),
            Vector(manifestpath)
          )
        )
    val commands = rawcommands.map(_resolve_command(projectroot, execution, _))
    VideoPlan(projectfile, projectroot, project, execution, outputpath, manifestpath, parts, artifacts, commands)
  }

  private def _part_plan(
    projectroot: Path,
    project: VideoProject,
    part: VideoPart,
    index: Int
  ): VideoPartPlan = {
    val id = part.displayId(index)
    val parttype = part.displayType
    val supported = _supported_part_types.contains(parttype)
    val renderer = part.renderer.orElse(project.renderer).map(_.summary).getOrElse("engine=legacy")
    val outputpath = projectroot.resolve(part.output.getOrElse(s"build/parts/$id.mp4")).normalize()
    val audiodir = part.audioDir.map(x => projectroot.resolve(x).normalize()).orElse {
      if (supported) Some(projectroot.resolve(s"build/audio/$id").normalize()) else None
    }
    val recorddir = part.recordDir.map(x => projectroot.resolve(x).normalize()).orElse {
      if (parttype == "web-demo") Some(projectroot.resolve(s"build/record/$id").normalize()) else None
    }
    val manifestpath = outputpath.getParent.resolve(s"${_basename(outputpath)}.manifest.json").normalize()
    val scriptpath = part.script.map(x => projectroot.resolve(x).normalize())
    val script = scriptpath.flatMap(_load_script)
    val scriptstatus = scriptpath match {
      case Some(path) if Files.isRegularFile(path) => "found"
      case Some(_) => "missing"
      case None => "none"
    }
    val stepspath = part.steps.map(x => projectroot.resolve(x).normalize())
    val stepsstatus = stepspath.map { path =>
      if (Files.isRegularFile(path)) "found" else "missing"
    }
    val artifacts = _part_artifacts(id, scriptpath, scriptstatus, stepspath, stepsstatus, outputpath, audiodir, recorddir, manifestpath)
    val commands =
      if (!supported)
        Vector.empty
      else
        _part_commands(id, parttype, renderer, scriptpath, scriptstatus, stepspath, stepsstatus, outputpath, audiodir, recorddir, manifestpath)
    VideoPartPlan(index, id, parttype, supported, renderer, part.script, scriptpath, scriptstatus, script, part.steps, stepspath, stepsstatus, outputpath, audiodir, recorddir, manifestpath, artifacts, commands)
  }

  private def _part_artifacts(
    id: String,
    scriptpath: Option[Path],
    scriptstatus: String,
    stepspath: Option[Path],
    stepsstatus: Option[String],
    outputpath: Path,
    audiodir: Option[Path],
    recorddir: Option[Path],
    manifestpath: Path
  ): Vector[VideoArtifactPlan] = {
    val scriptartifact = scriptpath.map { path =>
      VideoArtifactPlan("part-script", path, s"part.$id.input", _input_status(scriptstatus))
    }
    val stepsartifact = stepspath.map { path =>
      VideoArtifactPlan("part-steps", path, s"part.$id.input", _input_status(stepsstatus.getOrElse("missing")))
    }
    Vector(
      scriptartifact,
      stepsartifact,
      audiodir.map(path => VideoArtifactPlan("part-audio-dir", path, s"part.$id.synthesize", VideoArtifactStatus.Planned)),
      recorddir.map(path => VideoArtifactPlan("part-record-dir", path, s"part.$id.capture", VideoArtifactStatus.Planned)),
      Some(VideoArtifactPlan("part-output", outputpath, s"part.$id.render", VideoArtifactStatus.Planned)),
      Some(VideoArtifactPlan("part-manifest", manifestpath, s"part.$id.manifest", VideoArtifactStatus.Planned))
    ).flatten
  }

  private def _part_commands(
    id: String,
    parttype: String,
    renderer: String,
    scriptpath: Option[Path],
    scriptstatus: String,
    stepspath: Option[Path],
    stepsstatus: Option[String],
    outputpath: Path,
    audiodir: Option[Path],
    recorddir: Option[Path],
    manifestpath: Path
  ): Vector[VideoCommandPlan] = {
    val parse = scriptpath.map { path =>
      VideoCommandPlan(
        s"part.$id.parse-script",
        "cozy",
        VideoToolMode.Host,
        s"parse $parttype script",
        Vector(path),
        Vector.empty
      )
    }.toVector
    val capture =
      if (parttype == "web-demo")
        stepspath.map { path =>
          VideoCommandPlan(
            s"part.$id.capture",
            "playwright",
            VideoToolMode.Host,
            "plan web-demo replay/capture",
            Vector(path),
            recorddir.toVector
          )
        }.toVector
      else
        Vector.empty
    val renderable = scriptstatus == "found" && (parttype != "web-demo" || stepsstatus.contains("found"))
    val visualhelper =
      if (renderable)
        Vector(VideoCommandPlan(
          s"part.$id.prepare-visuals",
          "python-pillow",
          VideoToolMode.Host,
          s"prepare $parttype visual helper assets",
          scriptpath.toVector,
          Vector.empty
        ))
      else
        Vector.empty
    val synthesize =
      if (renderable)
        audiodir.map { path =>
          VideoCommandPlan(
            s"part.$id.synthesize",
            "voicevox",
            VideoToolMode.ExternalService,
            "synthesize scene audio",
            scriptpath.toVector,
            Vector(path)
          )
        }.toVector
      else
        Vector.empty
    val render =
      if (renderable)
        Vector(VideoCommandPlan(
          s"part.$id.render",
          _renderer_tool(renderer),
          VideoToolMode.Host,
          s"render $parttype part",
          scriptpath.toVector ++ audiodir.toVector ++ recorddir.toVector,
          Vector(outputpath)
        ))
      else
        Vector.empty
    val manifest =
      if (renderable)
        Vector(VideoCommandPlan(
          s"part.$id.manifest",
          "cozy",
          VideoToolMode.Host,
          "write part manifest",
          Vector(outputpath),
          Vector(manifestpath)
        ))
      else
        Vector.empty
    parse ++ capture.filter(_ => stepsstatus.forall(_ == "found")) ++ visualhelper ++ synthesize ++ render ++ manifest
  }

  private def _input_status(status: String): VideoArtifactStatus =
    status match {
      case "found" => VideoArtifactStatus.Input
      case _ => VideoArtifactStatus.MissingInput
    }

  private def _renderer_tool(renderer: String): String =
    if (renderer.contains("engine=remotion"))
      "remotion"
    else
      "ffmpeg"

  private def _resolve_command(
    projectroot: Path,
    execution: VideoExecutionConfig,
    command: VideoCommandPlan
  ): VideoCommandPlan =
    execution.toolMode match {
      case VideoToolMode.Docker if _docker_managed_tools.contains(command.toolName) =>
        command.copy(
          mode = VideoToolMode.Docker,
          preview = _docker_preview(projectroot, execution.dockerImage, command)
        )
      case _ =>
        command
    }

  private def _docker_preview(projectroot: Path, image: String, command: VideoCommandPlan): String = {
    val inputargs = command.inputs.map(path => s"--input ${_shell_quote(_docker_path(projectroot, path))}")
    val outputargs = command.outputs.map(path => s"--output ${_shell_quote(_docker_path(projectroot, path))}")
    val args = (inputargs ++ outputargs).mkString(" ")
    val suffix = if (args.isEmpty) "" else " " + args
    s"docker run --rm -v ${_shell_quote(s"${projectroot}:/workspace")} -w /workspace ${_shell_quote(image)} ${_shell_quote(command.toolName)}$suffix # ${command.preview}"
  }

  private def _shell_quote(value: String): String =
    "'" + value.replace("'", "'\"'\"'") + "'"

  private def _docker_path(projectroot: Path, path: Path): String = {
    val normalized = path.toAbsolutePath.normalize()
    if (normalized.startsWith(projectroot))
      "/workspace/" + projectroot.relativize(normalized).toString
    else
      normalized.toString
  }

  private def _render_inspect(
    config: InspectConfig,
    plan: VideoPlan,
    checks: Vector[VideoToolCheck]
  ): String = {
    val project = plan.project
    val b = Vector.newBuilder[String]
    b += "Cozy Video Inspect"
    b += s"projectFile: ${plan.projectFile}"
    b += s"projectRoot: ${plan.projectRoot}"
    project.name.foreach(x => b += s"name: $x")
    project.title.foreach(x => b += s"title: $x")
    b += s"toolMode: ${plan.execution.toolMode.label}"
    b += s"dockerImage: ${plan.execution.dockerImage}"
    b += s"output: ${plan.outputPath}"
    b += s"renderer: ${project.renderer.map(_.summary).getOrElse("engine=legacy")}"
    b += s"parts: ${project.parts.size}"
    plan.parts.foreach { part =>
      b ++= _render_part(part)
    }
    b ++= _render_artifacts(plan.artifacts)
    if (config.checkTools) {
      b ++= _render_tool_checks(checks)
    }
    b.result().mkString("\n") + "\n"
  }

  private def _render_build_dry_run(
    config: BuildConfig,
    plan: VideoPlan,
    checks: Vector[VideoToolCheck]
  ): String = {
    val b = Vector.newBuilder[String]
    b += "Cozy Video Build Dry-Run"
    b += s"projectFile: ${plan.projectFile}"
    b += s"projectRoot: ${plan.projectRoot}"
    b += s"toolMode: ${plan.execution.toolMode.label}"
    b += s"dockerImage: ${plan.execution.dockerImage}"
    b += s"output: ${plan.outputPath}"
    b += s"parts: ${plan.parts.size}"
    b ++= _render_artifacts(plan.artifacts)
    b += "commands:"
    plan.commands.foreach { command =>
      b += s"  - ${command.stepName}: ${command.toolName} (${command.mode.label}) - ${command.preview}"
      if (command.inputs.nonEmpty)
        b += s"    inputs: ${command.inputs.mkString(", ")}"
      if (command.outputs.nonEmpty)
        b += s"    outputs: ${command.outputs.mkString(", ")}"
    }
    if (config.checkTools) {
      b ++= _render_tool_checks(checks)
    }
    b.result().mkString("\n") + "\n"
  }

  private def _render_synthesis_result(result: VideoSynthesisResult): String = {
    val b = Vector.newBuilder[String]
    b += "Cozy Video Synthesize"
    b += s"scriptFile: ${result.scriptFile}"
    b += s"outputDir: ${result.outputDir}"
    b += s"voicevoxUrl: ${result.voicevoxUrl}"
    b += s"scenes: ${result.entries.size}"
    b += s"combined: ${result.combinedFile}"
    b += s"manifest: ${result.manifestFile}"
    result.entries.foreach { entry =>
      b += f"  - ${entry.sceneId}: ${entry.file} audio=${entry.audioDuration}%.3f target=${entry.targetDuration}%.3f lead=${entry.leadSilence}%.3f tail=${entry.tailSilence}%.3f"
    }
    b.result().mkString("\n") + "\n"
  }

  private def _render_render_result(result: VideoRenderResult): String = {
    val b = Vector.newBuilder[String]
    b += "Cozy Video Render"
    b += s"projectFile: ${result.projectFile}"
    b += s"toolMode: ${result.toolMode.label}"
    b += s"dockerImage: ${result.dockerImage}"
    b += s"parts: ${result.parts.size}"
    result.parts.foreach { part =>
      b += s"  - part.${part.id}: ${part.outputPath}"
      b += s"    manifest: ${part.manifestPath}"
      b += s"    remotionWorkDir: ${part.remotionWorkDir}"
    }
    b.result().mkString("\n") + "\n"
  }

  private def _render_part(part: VideoPartPlan): Vector[String] = {
    val z = Vector.newBuilder[String]
    z += s"part[${part.index}]: ${part.id}"
    z += s"  type: ${part.partType}${if (part.supported) "" else " (unsupported)"}"
    z += s"  renderer: ${part.renderer}"
    part.scriptPath match {
      case Some(path) =>
        z += s"  script: ${part.scriptName.getOrElse(path.getFileName.toString)}"
        z += s"  scriptPath: $path"
        z += s"  scriptStatus: ${part.scriptStatus}"
        part.script.foreach { videoscript =>
          videoscript.title.foreach(x => z += s"  scriptTitle: $x")
          z += s"  scenes: ${videoscript.scenes.size}"
          z += s"  expandedScenes: ${videoscript.expandedScenes.size}"
          z += f"  estimatedDuration: ${videoscript.estimatedDuration}%.2f"
        }
      case None =>
        z += s"  scriptStatus: none"
    }
    part.stepsPath.foreach { path =>
      z += s"  steps: ${part.stepsName.getOrElse(path.getFileName.toString)}"
      z += s"  stepsPath: $path"
      part.stepsStatus.foreach(x => z += s"  stepsStatus: $x")
    }
    z += s"  output: ${part.outputPath}"
    part.audioDir.foreach(x => z += s"  audioDir: $x")
    part.recordDir.foreach(x => z += s"  recordDir: $x")
    z.result()
  }

  private def _render_artifacts(artifacts: Vector[VideoArtifactPlan]): Vector[String] = {
    val b = Vector.newBuilder[String]
    b += "artifacts:"
    artifacts.foreach { artifact =>
      b += s"  - ${artifact.kind}: ${artifact.status.label} ${artifact.path}"
      b += s"    producer: ${artifact.producerStep}"
    }
    b.result()
  }

  private def _render_tool_checks(checks: Vector[VideoToolCheck]): Vector[String] = {
    val b = Vector.newBuilder[String]
    b += "tools:"
    checks.foreach { check =>
      b += s"  - ${check.name}: ${check.status.label} (${check.mode.label}) - ${check.message}"
      check.setupHint.foreach(x => b += s"    setup: $x")
    }
    b.result()
  }
}
