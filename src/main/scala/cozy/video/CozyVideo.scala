package cozy.video

import org.goldenport.RAISE
import org.goldenport.config.StructuredDocumentLoader
import org.goldenport.io.InputSource
import cozy.config.CozyProjectYamlConfig
import cozy.runtime.CozyCliArgs
import org.goldenport.cli.spec
import org.smartdox.semanticweb.{Rdf, RdfRenderer, Vocabulary}
import io.circe.{Decoder, HCursor, Json}
import io.circe.parser
import java.io.ByteArrayOutputStream
import java.net.{URI, URLEncoder}
import java.net.http.{HttpClient, HttpRequest, HttpResponse}
import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path, Paths, StandardCopyOption}
import java.security.MessageDigest
import java.time.{Duration => JDuration}
import java.util.concurrent.TimeUnit
import scala.collection.JavaConverters._
import scala.concurrent.{Await, ExecutionContext => ScalaExecutionContext, Future, blocking}
import scala.concurrent.duration._
import scala.util.Try
import scala.util.control.NonFatal

/*
 * @since   Jun. 18, 2026
 *  version Jun. 19, 2026
 * @version Jul. 20, 2026
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

    private def _validate_transcribe_options(args: List[String]): Unit = {
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

  final case class VideoProject(
    name: Option[String],
    title: Option[String],
    output: Option[String],
    renderer: Option[VideoRenderer],
    tools: Option[VideoToolSettings],
    parts: Vector[VideoPart],
    profile: Option[String] = None,
    visualEffects: Option[CozyVideoEffects.Settings] = None,
    assets: Option[CozyVideoAssets.Settings] = None
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
        profile <- c.downField("profile").as[Option[String]]
        visualeffects <- c.downField("visualEffects").as[Option[CozyVideoEffects.Settings]].flatMap {
          case value @ Some(_) => Right(value)
          case None => c.downField("visual-effects").as[Option[CozyVideoEffects.Settings]]
        }
        assets <- c.downField("assets").as[Option[CozyVideoAssets.Settings]]
      } yield VideoProject(name, title, output, renderer, tools, parts.getOrElse(Vector.empty), profile, visualeffects, assets)
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
    val DEFAULT_DOCKER_IMAGE = "ghcr.io/asami/textus-toolchain:latest"
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
    narration: Json,
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
        narration = c.downField("narration").focus.getOrElse(Json.obj())
        voice <- c.downField("voice").as[Option[Json]]
        pronunciations <- c.downField("pronunciations").as[Option[Map[String, String]]]
        voicetextnormalization <- c.downField("voiceTextNormalization").as[Option[Json]]
        characters <- c.downField("characters").as[Option[Map[String, Json]]]
        sections <- c.downField("sections").as[Option[Vector[Json]]]
        scenes <- c.downField("scenes").as[Option[Vector[VideoScene]]]
      } yield VideoScript(
        title,
        tools,
        narration,
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

  final case class VideoReplayViewport(width: Int, height: Int)
  object VideoReplayViewport {
    val default = VideoReplayViewport(1280, 720)

    implicit val decoder: Decoder[VideoReplayViewport] = (c: HCursor) =>
      for {
        width <- c.downField("width").as[Option[Int]]
        height <- c.downField("height").as[Option[Int]]
      } yield VideoReplayViewport(width.getOrElse(default.width), height.getOrElse(default.height))
  }

  final case class VideoReplayStep(
    kind: String,
    url: Option[String] = None,
    selector: Option[String] = None,
    text: Option[String] = None,
    key: Option[String] = None,
    delayMs: Option[Int] = None,
    timestampMs: Option[Int] = None,
    note: Option[String] = None,
    manualReview: Boolean = false
  )
  object VideoReplayStep {
    implicit val decoder: Decoder[VideoReplayStep] = (c: HCursor) =>
      for {
        rawkind <- c.downField("kind").as[Option[String]].flatMap {
          case Some(s) => Right(Some(s))
          case None => c.downField("type").as[Option[String]]
        }
        url <- c.downField("url").as[Option[String]]
        selector <- c.downField("selector").as[Option[String]]
        text <- c.downField("text").as[Option[String]]
        key <- c.downField("key").as[Option[String]]
        delayms <- c.downField("delayMs").as[Option[Int]].flatMap {
          case Some(n) => Right(Some(n))
          case None => c.downField("durationMs").as[Option[Int]]
        }
        timestampms <- c.downField("timestampMs").as[Option[Int]]
        note <- c.downField("note").as[Option[String]]
        manualreview <- c.downField("manualReview").as[Option[Boolean]]
      } yield VideoReplayStep(_normalize_replay_kind(rawkind.getOrElse("note")), url, selector, text, key, delayms, timestampms, note, manualreview.getOrElse(false))

    def toJson(step: VideoReplayStep): Json =
      Json.obj(
        Vector(
          Some("kind" -> Json.fromString(step.kind)),
          step.url.map(x => "url" -> Json.fromString(x)),
          step.selector.map(x => "selector" -> Json.fromString(x)),
          step.text.map(x => "text" -> Json.fromString(x)),
          step.key.map(x => "key" -> Json.fromString(x)),
          step.delayMs.map(x => "delayMs" -> Json.fromInt(x)),
          step.timestampMs.map(x => "timestampMs" -> Json.fromInt(x)),
          step.note.map(x => "note" -> Json.fromString(x)),
          if (step.manualReview) Some("manualReview" -> Json.fromBoolean(true)) else None
        ).flatten: _*
      )
  }

  final case class VideoReplayScript(
    schema: Option[String],
    sourceVideo: Option[String],
    sourceSha256: Option[String],
    manualReview: Boolean,
    sources: Json,
    viewport: VideoReplayViewport,
    steps: Vector[VideoReplayStep]
  )
  object VideoReplayScript {
    implicit val decoder: Decoder[VideoReplayScript] = (c: HCursor) =>
      for {
        schema <- c.downField("schema").as[Option[String]]
        sourcevideo <- c.downField("sourceVideo").as[Option[String]]
        sourcesha256 <- c.downField("sourceSha256").as[Option[String]]
        manualreview <- c.downField("manualReview").as[Option[Boolean]]
        sources <- c.downField("sources").as[Option[Json]]
        viewport <- c.downField("viewport").as[Option[VideoReplayViewport]]
        steps <- c.downField("steps").as[Option[Vector[VideoReplayStep]]]
      } yield VideoReplayScript(schema, sourcevideo, sourcesha256, manualreview.getOrElse(false), sources.getOrElse(Json.obj()), viewport.getOrElse(VideoReplayViewport.default), steps.getOrElse(Vector.empty))
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

  private object DefaultVideoToolProbe extends VideoToolProbe {
    private implicit val _ec: ScalaExecutionContext = ScalaExecutionContext.global
    private val _process_timeout = 30.seconds
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

  final case class VideoTranscriptSegment(
    index: Int,
    start: Double,
    end: Double,
    text: String
  )

  final case class VideoTranscriptionResult(
    inputVideo: Path,
    saveDir: Path,
    audioPath: Path,
    transcriptPath: Path,
    captionsPath: Path,
    narrationPath: Path,
    manifestPath: Path,
    toolMode: VideoToolMode,
    dockerImage: String,
    modelPath: String,
    segmentCount: Int
  )

  final case class VideoTranscribeExecution(
    toolMode: VideoToolMode,
    dockerImage: String,
    modelPath: String,
    hostWhisperModel: Option[Path]
  ) {
    def toVideoExecutionConfig: VideoExecutionConfig =
      VideoExecutionConfig(toolMode, dockerImage, VideoToolSettings.DEFAULT_VOICEVOX_URL)
  }

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
    assets: Vector[CozyVideoAssets.Resolved],
    execution: VideoExecutionConfig,
    outputPath: Path,
    manifestPath: Path,
    parts: Vector[VideoPartPlan],
    artifacts: Vector[VideoArtifactPlan],
    commands: Vector[VideoCommandPlan]
  )

  private val _p_project_file = spec.Parameter.argumentFile("project-file")
  private val _p_input_video = spec.Parameter.argumentFile("input-video")
  private val _p_script_file = spec.Parameter.argumentFile("script-file")
  private val _p_check_tools = spec.Parameter("check-tools", spec.Parameter.SwitchKind)
  private val _p_dry_run = spec.Parameter("dry-run", spec.Parameter.SwitchKind)
  private val _p_save = spec.Parameter.property("save")
  private val _p_renderer = spec.Parameter.property("renderer")
  private val _p_part = spec.Parameter.property("part")
  private val _p_tool_mode = spec.Parameter.property("tool-mode")
  private val _p_docker_image = spec.Parameter.property("docker-image")
  private val _p_voicevox_url = spec.Parameter.property("voicevox-url")
  private val _p_whisper_model = spec.Parameter.property("whisper-model")
  private val _p_events = spec.Parameter.property("events")
  private val _p_har = spec.Parameter.property("har")
  private val _p_trace = spec.Parameter.property("trace")
  private val _p_transcript = spec.Parameter.property("transcript")
  private val _supported_part_types = Set("dialogue", "storyboard", "web-demo")
  private val _supported_renderers = Set("remotion", "simple-java2d")
  private val _docker_managed_tools = Set("remotion", "playwright", "ffmpeg", "ffprobe", "node", "npm", "whisper-cpp", "python-pillow")
  private val _docker_whisper_model = "/opt/textus/models/ggml-base.bin"
  private val _default_piper_model = "en_US-ljspeech-medium"
  private val _property_options = Set("tool-mode", "docker-image", "save", "voicevox-url", "renderer", "part", "whisper-model", "events", "har", "trace", "transcript")
  private val _default_sample_rate = 24000
  private val _default_audio_channels = 1
  private val _default_audio_bits_per_sample = 16
  private val _video_rdf_namespace = "https://www.simplemodeling.org/ns/cozy/video#"
  private val _schema_namespace = "https://schema.org/"
  private val _dcterms_namespace = "http://purl.org/dc/terms/"
  private val _prov_namespace = "http://www.w3.org/ns/prov#"
  private val _xsd_namespace = "http://www.w3.org/2001/XMLSchema#"
  private val _rdf_hex_digits = "0123456789ABCDEF"
  private val _video_rdf_context: Map[String, Any] = Map(
    "rdf" -> Vocabulary.Rdf.namespace,
    "rdfs" -> Vocabulary.Rdfs.namespace,
    "cozy-video" -> _video_rdf_namespace,
    "schema" -> _schema_namespace,
    "dcterms" -> _dcterms_namespace,
    "prov" -> _prov_namespace,
    "xsd" -> _xsd_namespace
  )

  def execute(args: List[String]): Boolean = execute(args, VideoToolRegistry.default)

  def execute(args: List[String], tools: VideoToolRegistry): Boolean =
    execute(args, tools, VoicevoxClient.default, VideoProcessRunner.default)

  def execute(args: List[String], tools: VideoToolRegistry, voicevox: VoicevoxClient): Boolean =
    execute(args, tools, voicevox, VideoProcessRunner.default)

  def execute(args: List[String], tools: VideoToolRegistry, voicevox: VoicevoxClient, runner: VideoProcessRunner): Boolean =
    args match {
      case "video" :: "scaffold" :: rest =>
        println(CozyVideoScaffold.scaffold(CozyVideoScaffold.Config.create(rest)))
        true
      case "video" :: "inspect" :: rest =>
        println(inspect(InspectConfig.create(rest), tools))
        true
      case "video" :: "build" :: rest =>
        println(build(BuildConfig.create(rest), tools, runner))
        true
      case "video" :: "synthesize" :: rest =>
        println(synthesize(SynthesizeConfig.create(rest), tools, voicevox, runner))
        true
      case "video" :: "render" :: rest =>
        println(render(RenderConfig.create(rest), tools, runner))
        true
      case "video" :: "transcribe" :: rest =>
        println(transcribe(TranscribeConfig.create(rest), tools, runner))
        true
      case "video" :: "demo-script" :: rest =>
        println(demoScript(DemoScriptConfig.create(rest)))
        true
      case "video" :: "replay" :: rest =>
        println(replay(ReplayConfig.create(rest), tools, runner))
        true
      case "video" :: "rdf" :: rest =>
        println(rdf(RdfConfig.create(rest)))
        true
      case "video" :: other :: _ =>
        RAISE.invalidArgumentFault(s"Unsupported video command: $other")
      case _ =>
        false
    }

  def inspect(config: InspectConfig, tools: VideoToolRegistry): String = {
    val plan = _plan(config.projectFile, config.toolMode, config.dockerImage)
    val providers = _plan_narration_providers(plan)
    val context = VideoToolContext(config.projectFile, config.projectRoot, plan.project, plan.execution, providers)
    _render_inspect(config, plan, if (config.checkTools) tools.checks(context) else Vector.empty)
  }

  def build(config: BuildConfig, tools: VideoToolRegistry): String =
    build(config, tools, VideoProcessRunner.default)

  def build(config: BuildConfig, tools: VideoToolRegistry, runner: VideoProcessRunner): String = {
    val plan = _plan(config.projectFile, config.toolMode, config.dockerImage)
    val context = VideoToolContext(config.projectFile, config.projectRoot, plan.project, plan.execution)
    val checks = if (config.checkTools) tools.checks(context) else Vector.empty
    if (config.dryRun)
      _render_build_dry_run(config, plan, checks)
    else {
      _validate_build_tools(plan.execution, checks)
      _render_build_result(_build_project(plan, runner))
    }
  }

  def synthesize(config: SynthesizeConfig, voicevox: VoicevoxClient): String = {
    synthesize(config, VideoToolRegistry.default, voicevox, VideoProcessRunner.default)
  }

  def synthesize(config: SynthesizeConfig, tools: VideoToolRegistry, voicevox: VoicevoxClient): String = {
    synthesize(config, tools, voicevox, VideoProcessRunner.default)
  }

  def synthesize(
    config: SynthesizeConfig,
    tools: VideoToolRegistry,
    voicevox: VoicevoxClient,
    runner: VideoProcessRunner
  ): String = {
    val script = _load_required_script(config.scriptFile)
    val selection = _resolve_narration_selection(script)
    val execution = VideoExecutionConfig.create(
      config.projectRoot,
      script.tools,
      config.toolMode,
      config.dockerImage,
      config.voicevoxUrl
    )
    val provider = selection.provider match {
      case "voicevox" =>
        new VoicevoxNarrationProvider(execution.voicevoxUrl, voicevox)
      case "macos-say" =>
        if (execution.toolMode != VideoToolMode.Host)
          RAISE.invalidArgumentFault(
            "Narration provider macos-say requires host tool mode. Use --tool-mode=host."
          )
        new MacosSayNarrationProvider(config.projectRoot, runner)
      case "piper" =>
        if (execution.toolMode != VideoToolMode.Docker)
          RAISE.invalidArgumentFault(
            "Narration provider piper requires Docker tool mode. Use --tool-mode=docker."
          )
        new PiperNarrationProvider(config.projectRoot, execution, runner)
      case unsupported =>
        RAISE.invalidArgumentFault(
          s"Unsupported narration provider: $unsupported. Supported providers: voicevox, macos-say, piper."
        )
    }
    val checks =
      if (config.checkTools) {
        val project = VideoProject(None, script.title, None, None, script.tools, Vector.empty)
        val context = VideoToolContext(config.scriptFile, config.projectRoot, project, execution, Set(provider.id))
        tools.checks(context)
      } else {
        Vector.empty
      }
    _validate_synthesis_tools(provider.id, checks)
    val result = _synthesize_script(config.scriptFile, script, config.saveDir, provider, selection.diagnostics, execution, checks)
    _render_synthesis_result(result)
  }

  def render(config: RenderConfig, tools: VideoToolRegistry, runner: VideoProcessRunner): String = {
    if (!_supported_renderers.contains(config.renderer))
      RAISE.invalidArgumentFault(s"Unsupported video renderer: ${config.renderer}. Supported renderers: remotion, simple-java2d.")
    val plan = _plan(config.projectFile, config.toolMode, config.dockerImage)
    CozyVideoEffects.validate(config.renderer, CozyVideoEffects.expand(plan.project.visualEffects))
    val context = VideoToolContext(config.projectFile, config.projectRoot, plan.project, plan.execution)
    val checks = if (config.checkTools) tools.checks(context) else Vector.empty
    _validate_render_tools(config.renderer, plan.execution, checks)
    val result =
      config.renderer match {
        case "remotion" => _render_remotion(config, plan, runner)
        case "simple-java2d" => _render_simple_java2d(config, plan, runner)
      }
    _render_render_result(result)
  }

  def transcribe(config: TranscribeConfig, tools: VideoToolRegistry, runner: VideoProcessRunner): String = {
    val execution = _transcribe_execution(config)
    val project = VideoProject(
      name = Some("transcription"),
      title = Some("Video Transcription"),
      output = None,
      renderer = None,
      tools = Some(VideoToolSettings(
        Some(execution.toolMode.label),
        Some(execution.dockerImage),
        None,
        execution.hostWhisperModel.map(_.toString)
      )),
      parts = Vector.empty
    )
    val context = VideoToolContext(config.inputVideo, config.projectRoot, project, execution.toVideoExecutionConfig)
    val checks = if (config.checkTools) tools.checks(context) else Vector.empty
    _validate_transcribe_tools(execution, checks)
    _render_transcription_result(_transcribe_video(config, execution, runner))
  }

  def rdf(config: RdfConfig): String = {
    val plan = _plan(config.projectFile, config.toolMode, config.dockerImage)
    val result = _write_video_rdf(config, plan)
    _render_rdf_result(result)
  }

  def demoScript(config: DemoScriptConfig): String =
    _render_demo_script_result(_write_demo_script(config))

  def replay(config: ReplayConfig, tools: VideoToolRegistry, runner: VideoProcessRunner): String = {
    val script = _load_replay_script(config.scriptFile)
    val execution = _replay_execution(config)
    val project = VideoProject(
      name = Some("replay"),
      title = Some("Video Replay"),
      output = None,
      renderer = None,
      tools = Some(VideoToolSettings(Some(execution.toolMode.label), Some(execution.dockerImage), None, None)),
      parts = Vector.empty
    )
    val context = VideoToolContext(config.scriptFile, config.projectRoot, project, execution)
    val checks = if (config.checkTools) tools.checks(context) else Vector.empty
    _validate_replay_tools(execution, checks)
    _render_replay_result(_replay_script(config, script, execution, runner))
  }

  private def _load_project(path: Path): VideoProject = {
    if (!Files.isRegularFile(path))
      RAISE.invalidArgumentFault(s"Missing video project file: $path")
    val project = StructuredDocumentLoader.loadDocument[VideoProject](InputSource(path.toFile)).take
    CozyVideoEffects.expand(project.visualEffects)
    project
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

  private def _load_replay_script(path: Path): VideoReplayScript = {
    if (!Files.isRegularFile(path))
      RAISE.invalidArgumentFault(s"Missing video replay script file: $path")
    StructuredDocumentLoader.loadDocument[VideoReplayScript](InputSource(path.toFile)).take
  }

  private def _write_demo_script(config: DemoScriptConfig): VideoDemoScriptResult = {
    val input = config.inputVideo.toAbsolutePath.normalize()
    if (!Files.isRegularFile(input))
      RAISE.invalidArgumentFault(s"Missing input video for demo-script: $input")
    val save = config.saveFile.toAbsolutePath.normalize()
    config.eventsFile.foreach(path => _require_regular_file(path, "selector event log"))
    config.harFile.foreach(path => _require_regular_file(path, "HAR file"))
    config.traceFile.foreach(path => _require_regular_file(path, "Playwright trace file"))
    config.transcriptFile.foreach(path => _require_regular_file(path, "transcript file"))
    val eventjson = config.eventsFile.map(path => StructuredDocumentLoader.loadJson(InputSource(path.toFile)).take)
    val harjson = config.harFile.map(path => StructuredDocumentLoader.loadJson(InputSource(path.toFile)).take)
    val transcript = config.transcriptFile.map(_read_transcript_segments)
    val eventsteps = eventjson.toVector.flatMap(_event_log_steps)
    val harsteps =
      if (eventsteps.isEmpty)
        harjson.flatMap(_har_initial_url).map(url => VideoReplayStep("goto", url = Some(url), manualReview = true)).toVector
      else
        Vector.empty
    val transcriptsteps = transcript.toVector.flatten.map { segment =>
      VideoReplayStep(
        "note",
        timestampMs = Some(math.round(segment.start * 1000).toInt),
        note = Some(segment.text),
        manualReview = true
      )
    }
    val sourcedsteps = eventsteps ++ harsteps ++ transcriptsteps
    val steps =
      if (sourcedsteps.nonEmpty)
        sourcedsteps
      else
        Vector(VideoReplayStep("note", note = Some("Recorded video only. Manual review is required to reconstruct browser operations."), manualReview = true))
    val manualreview = eventsteps.isEmpty || steps.exists(_.manualReview)
    val viewport = eventjson.flatMap(_event_log_viewport).getOrElse(VideoReplayViewport.default)
    val json = _demo_script_json(config, input, viewport, manualreview, steps)
    Option(save.getParent).foreach(Files.createDirectories(_))
    Files.writeString(save, json.spaces2, StandardCharsets.UTF_8)
    VideoDemoScriptResult(input, save, manualreview, steps)
  }

  private def _require_regular_file(path: Path, label: String): Unit =
    if (!Files.isRegularFile(path))
      RAISE.invalidArgumentFault(s"Missing $label: $path")

  private def _demo_script_json(
    config: DemoScriptConfig,
    input: Path,
    viewport: VideoReplayViewport,
    manualreview: Boolean,
    steps: Vector[VideoReplayStep]
  ): Json =
    Json.obj(
      "schema" -> Json.fromString("cozy.video.replay-script.v1"),
      "sourceVideo" -> Json.fromString(input.toString),
      "sourceSha256" -> Json.fromString(_sha256(input)),
      "manualReview" -> Json.fromBoolean(manualreview),
      "sources" -> Json.obj(
        Vector(
          config.eventsFile.map(path => "events" -> Json.fromString(path.toString)),
          config.harFile.map(path => "har" -> Json.fromString(path.toString)),
          config.traceFile.map(path => "trace" -> Json.fromString(path.toString)),
          config.transcriptFile.map(path => "transcript" -> Json.fromString(path.toString))
        ).flatten: _*
      ),
      "viewport" -> Json.obj(
        "width" -> Json.fromInt(viewport.width),
        "height" -> Json.fromInt(viewport.height)
      ),
      "steps" -> Json.fromValues(steps.map(VideoReplayStep.toJson))
    )

  private def _event_log_steps(json: Json): Vector[VideoReplayStep] = {
    val cursor = json.hcursor
    val rawsteps = cursor.downField("steps").focus.flatMap(_.asArray).getOrElse(json.asArray.getOrElse(Vector.empty))
    rawsteps.map(_event_log_step)
  }

  private def _event_log_step(json: Json): VideoReplayStep = {
    val kind = _normalize_replay_kind(_json_string(json, "kind").orElse(_json_string(json, "type")).orElse(_json_string(json, "event")).getOrElse("note"))
    VideoReplayStep(
      kind,
      url = _json_string(json, "url"),
      selector = _json_string(json, "selector"),
      text = _json_string(json, "text").orElse(_json_string(json, "value")),
      key = _json_string(json, "key"),
      delayMs = _json_int(json, "delayMs").orElse(_json_int(json, "durationMs")),
      timestampMs = _json_int(json, "timestampMs"),
      note = _json_string(json, "note"),
      manualReview = _json_boolean(json, "manualReview").getOrElse(false)
    )
  }

  private def _event_log_viewport(json: Json): Option[VideoReplayViewport] =
    json.hcursor.downField("viewport").focus.map { viewport =>
      VideoReplayViewport(_json_int(viewport, "width").getOrElse(1280), _json_int(viewport, "height").getOrElse(720))
    }

  private def _normalize_replay_kind(value: String): String =
    value.trim.toLowerCase(java.util.Locale.ROOT) match {
      case "navigate" => "goto"
      case "input" => "fill"
      case "keydown" => "press"
      case "pause" => "wait"
      case "goto" | "click" | "fill" | "press" | "wait" | "note" | "screenshot" => value.trim.toLowerCase(java.util.Locale.ROOT)
      case other => other
    }

  private def _har_initial_url(json: Json): Option[String] =
    json.hcursor.downField("log").downField("entries").focus.flatMap(_.asArray).flatMap { entries =>
      entries.toVector.flatMap { entry =>
        val request = entry.hcursor.downField("request")
        val url = request.downField("url").as[String].toOption
        val resourcetype = entry.hcursor.downField("_resourceType").as[String].toOption
        val method = request.downField("method").as[String].toOption
        url.filter(_ => resourcetype.contains("document") || method.contains("GET"))
      }.headOption
    }

  private def _read_transcript_segments(path: Path): Vector[VideoTranscriptSegment] = {
    val json = parser.parse(Files.readString(path, StandardCharsets.UTF_8)).fold(
      e => RAISE.invalidArgumentFault(s"Invalid transcript JSON: ${e.getMessage}"),
      identity
    )
    _json_array(json, "segments").getOrElse(Vector.empty).zipWithIndex.map {
      case (segment, index) =>
        VideoTranscriptSegment(
          _json_int(segment, "index").getOrElse(index + 1),
          _json_double(segment, "start").getOrElse(0.0),
          _json_double(segment, "end").getOrElse(0.0),
          _json_string(segment, "text").getOrElse("").trim
        )
    }.filter(_.text.nonEmpty)
  }

  private def _replay_execution(config: ReplayConfig): VideoExecutionConfig = {
    val defaults = CozyProjectYamlConfig.loadOperationDefaults(config.projectRoot)
    val mode = config.toolMode.
      orElse(defaults.value("video.tool-mode")).
      getOrElse("docker")
    val dockerimage = config.dockerImage.
      orElse(defaults.value("video.docker-image")).
      orElse(defaults.value("cozy.docker-image")).
      getOrElse(VideoToolSettings.DEFAULT_DOCKER_IMAGE)
    VideoExecutionConfig(VideoToolMode.parse(mode), dockerimage, VideoToolSettings.DEFAULT_VOICEVOX_URL)
  }

  final case class VideoAudioManifestEntry(
    sceneId: String,
    speaker: Option[String],
    file: String,
    leadSilence: Double,
    audioDuration: Double,
    targetDuration: Double,
    tailSilence: Double,
    provider: Option[String] = None,
    executionMode: Option[String] = None,
    voiceIdentity: Option[String] = None,
    voiceId: Option[String] = None,
    modelIdentity: Option[String] = None,
    sampleRate: Option[Int] = None,
    channels: Option[Int] = None,
    bitsPerSample: Option[Int] = None
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
        provider <- c.downField("provider").as[Option[String]]
        executionmode <- c.downField("executionMode").as[Option[String]]
        voiceidentity <- c.downField("voiceIdentity").as[Option[String]]
        voiceid <- c.downField("voiceId").as[Option[String]]
        modelidentity <- c.downField("modelIdentity").as[Option[String]]
        samplerate <- c.downField("sampleRate").as[Option[Int]]
        channels <- c.downField("channels").as[Option[Int]]
        bitspersample <- c.downField("bitsPerSample").as[Option[Int]]
      } yield VideoAudioManifestEntry(
        sceneid,
        speaker,
        file,
        leadsilence,
        audioduration,
        targetduration,
        tailsilence,
        provider,
        executionmode,
        voiceidentity,
        voiceid,
        modelidentity,
        samplerate,
        channels,
        bitspersample
      )
  }

  final case class VideoSynthesisResult(
    scriptFile: Path,
    outputDir: Path,
    provider: String,
    executionMode: String,
    diagnostics: Vector[String],
    toolMode: VideoToolMode,
    dockerImage: String,
    combinedFile: Path,
    manifestFile: Path,
    entries: Vector[VideoAudioManifestEntry],
    toolChecks: Vector[VideoToolCheck] = Vector.empty
  )

  final case class VideoRenderedPart(
    id: String,
    outputPath: Path,
    manifestPath: Path,
    workDir: Path,
    workDirLabel: String = "remotionWorkDir"
  )

  final case class VideoRenderResult(
    projectFile: Path,
    parts: Vector[VideoRenderedPart],
    toolMode: VideoToolMode,
    dockerImage: String
  )

  final case class VideoBuildResult(
    projectFile: Path,
    outputPath: Path,
    manifestPath: Path,
    concatListPath: Path,
    partOutputs: Vector[Path],
    toolMode: VideoToolMode,
    dockerImage: String,
    ffprobeSummary: Json
  )

  final case class VideoRdfResult(
    projectFile: Path,
    outputDir: Path,
    turtleFile: Path,
    jsonLdFile: Path,
    manifestFile: Path,
    tripleCount: Int,
    resourceCount: Int
  )

  final case class VideoDemoScriptResult(
    inputVideo: Path,
    scriptFile: Path,
    manualReview: Boolean,
    steps: Vector[VideoReplayStep]
  )

  final case class VideoReplayResult(
    scriptFile: Path,
    manifestPath: Path,
    outputVideo: Option[Path],
    toolMode: VideoToolMode,
    dockerImage: String,
    dryRun: Boolean,
    commands: Vector[VideoCommandPlan]
  )

  final case class VideoAudioInput(
    manifestPath: Path,
    entries: Vector[VideoAudioManifestEntry],
    files: Vector[Path]
  )

  final case class VideoSimpleJava2dInput(
    audio: VideoAudioInput,
    combinedFile: Path
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

  private final case class NarrationSelection(
    provider: String,
    diagnostics: Vector[String]
  )

  private final class VoicevoxNarrationProvider(
    baseUrl: String,
    client: VoicevoxClient
  ) extends NarrationProvider {
    private val _speaker_ids = scala.collection.mutable.Map.empty[String, Int]

    val id = "voicevox"
    val executionMode = "external-http"

    def synthesize(text: String, voice: Json): NarrationAudio = {
      val cachekey = voice.noSpaces
      val speakerid = _speaker_ids.getOrElseUpdate(cachekey, _resolve_speaker_id(baseUrl, voice, client))
      val audioquery = _apply_voice_tuning(client.audioQuery(baseUrl, text, speakerid), voice)
      NarrationAudio(
        client.synthesis(baseUrl, speakerid, audioquery),
        Some(_voicevox_voice_identity(voice)),
        Some(speakerid.toString),
        None
      )
    }
  }

  private final class MacosSayNarrationProvider(
    projectRoot: Path,
    runner: VideoProcessRunner
  ) extends NarrationProvider {
    val id = "macos-say"
    val executionMode = "host"

    def synthesize(text: String, voice: Json): NarrationAudio = {
      val voicename = _json_string(voice, "voiceName").
        orElse(_json_string(voice, "name")).
        orElse(_json_string(voice, "speakerName")).
        getOrElse("Samantha")
      val rate = _json_int(voice, "rate").getOrElse(180)
      if (rate <= 0)
        RAISE.invalidArgumentFault(s"macos-say voice rate must be positive: $rate")
      val workdir = Files.createTempDirectory("cozy-macos-say-")
      val source = workdir.resolve("narration.aiff")
      val wav = workdir.resolve("narration.wav")
      try {
        val sayresult = runner.run(
          Vector("say", "-v", voicename, "-r", rate.toString, "-o", source.toString, text),
          projectRoot
        )
        if (!sayresult.isSuccess)
          RAISE.invalidArgumentFault(s"macOS say narration failed: ${sayresult.text}")
        val ffmpegresult = runner.run(
          Vector(
            "ffmpeg",
            "-y",
            "-i",
            source.toString,
            "-ar",
            _default_sample_rate.toString,
            "-ac",
            _default_audio_channels.toString,
            "-sample_fmt",
            "s16",
            wav.toString
          ),
          projectRoot
        )
        if (!ffmpegresult.isSuccess)
          RAISE.invalidArgumentFault(s"macOS say audio normalization failed: ${ffmpegresult.text}")
        if (!Files.isRegularFile(wav))
          RAISE.invalidArgumentFault(s"macOS say audio normalization did not create WAV output: $wav")
        NarrationAudio(
          Files.readAllBytes(wav),
          Some(voicename),
          None,
          Some("macos-say")
        )
      } finally {
        Files.walk(workdir).iterator().asScala.toVector.reverse.foreach(Files.deleteIfExists)
      }
    }
  }

  private final class PiperNarrationProvider(
    projectroot: Path,
    execution: VideoExecutionConfig,
    runner: VideoProcessRunner
  ) extends NarrationProvider {
    val id = "piper"
    val executionMode = "docker"

    def synthesize(text: String, voice: Json): NarrationAudio = {
      val modelid = _json_string(voice, "model").
        orElse(_json_string(voice, "modelIdentity")).
        map(_.trim).
        filter(_.nonEmpty).
        getOrElse(_default_piper_model)
      val workroot = projectroot.resolve("target/cozy-video/piper").normalize()
      Files.createDirectories(workroot)
      val workdir = Files.createTempDirectory(workroot, "narration-")
      val input = workdir.resolve("narration.txt")
      val output = workdir.resolve("narration.wav")
      try {
        Files.writeString(input, text, StandardCharsets.UTF_8)
        val command = _execution_command(
          projectroot,
          execution,
          "textus-toolchain",
          Vector(
            "piper-synthesize",
            "--model",
            modelid,
            "--input",
            _execution_path(projectroot, execution, input),
            "--output",
            _execution_path(projectroot, execution, output)
          ),
          networkdisabled = true
        )
        val result = runner.run(command, projectroot)
        if (!result.isSuccess)
          RAISE.invalidArgumentFault(s"Piper narration failed for model $modelid: ${result.text}")
        if (!Files.isRegularFile(output))
          RAISE.invalidArgumentFault(s"Piper narration did not create WAV output for model $modelid.")
        NarrationAudio(
          Files.readAllBytes(output),
          Some(modelid),
          None,
          Some(modelid)
        )
      } finally {
        Files.walk(workdir).iterator().asScala.toVector.reverse.foreach(Files.deleteIfExists)
      }
    }
  }

  private def _synthesize_script(
    scriptfile: Path,
    script: VideoScript,
    savedir: Path,
    provider: NarrationProvider,
    diagnostics: Vector[String],
    execution: VideoExecutionConfig,
    toolchecks: Vector[VideoToolCheck]
  ): VideoSynthesisResult = {
    Files.createDirectories(savedir)
    val concatparts = scala.collection.mutable.ArrayBuffer.empty[Path]
    val entries = script.expandedScenes.zipWithIndex.map {
      case (scene, index) =>
        val sceneid = scene.id.getOrElse(f"scene-${index + 1}%02d")
        val fileid = _scene_file_id(sceneid)
        val scenewav = _audio_output_file(savedir, f"${index + 1}%02d-$fileid.wav")
        val leadsilence = math.max(0.0, scene.leadSilence.getOrElse(0.0))
        val voice = _voice_for_scene(script, scene)
        var voiceidentity: Option[String] = Some(_narration_voice_identity(provider.id, voice))
        var voiceid: Option[String] = None
        var modelidentity: Option[String] = None
        if (_is_silent_scene(scene)) {
          _write_silence_wav(scenewav, 0.01)
        } else {
          val text = _spoken_text(script, scene)
          val audio = provider.synthesize(text, voice)
          voiceidentity = audio.voiceIdentity
          voiceid = audio.voiceId
          modelidentity = audio.modelIdentity
          _write_wav(scenewav, _normalize_provider_wav(audio.wav, s"${provider.id}:$sceneid"))
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
        VideoAudioManifestEntry(
          sceneid,
          scene.speaker,
          scenewav.getFileName.toString,
          _round3(leadsilence),
          _round3(audioduration),
          targetduration,
          _round3(tailsilence),
          Some(provider.id),
          Some(provider.executionMode),
          voiceidentity,
          voiceid,
          modelidentity,
          Some(_default_sample_rate),
          Some(_default_audio_channels),
          Some(_default_audio_bits_per_sample)
        )
    }
    val combined = _audio_output_file(savedir, s"${_basename(scriptfile)}.wav")
    _concatenate_wavs(concatparts.toVector, combined)
    val manifest = _audio_output_file(savedir, "manifest.json")
    Files.writeString(manifest, _manifest_json(entries).spaces2, StandardCharsets.UTF_8)
    VideoSynthesisResult(
      scriptfile,
      savedir,
      provider.id,
      provider.executionMode,
      diagnostics,
      execution.toolMode,
      execution.dockerImage,
      combined,
      manifest,
      entries,
      toolchecks
    )
  }

  private def _plan_narration_providers(plan: VideoPlan): Set[String] =
    plan.parts.flatMap(_.script.map(_resolve_narration_selection(_).provider)).toSet

  private def _resolve_narration_selection(script: VideoScript): NarrationSelection = {
    val canonical = _canonical_narration_provider(script.narration)
    val legacy = (
      _json_string(script.voice, "engine").toVector ++
        script.characters.values.toVector.flatMap(x => x.hcursor.downField("voice").focus.flatMap(_json_string(_, "engine")))
    ).map(_.trim.toLowerCase).filter(_.nonEmpty).distinct.sorted
    if (legacy.size > 1)
      RAISE.invalidArgumentFault(
        s"Conflicting legacy voice.engine providers: ${legacy.mkString(", ")}. Set one narration.provider for the script."
      )
    val legacyprovider = legacy.headOption
    (canonical, legacyprovider) match {
      case (Some(current), Some(old)) if current != old =>
        RAISE.invalidArgumentFault(
          s"Conflicting narration provider settings: narration.provider=$current, voice.engine=$old."
        )
      case (Some(current), Some(_)) =>
        NarrationSelection(current, Vector(_legacy_voice_engine_diagnostic))
      case (Some(current), None) =>
        NarrationSelection(current, Vector.empty)
      case (None, Some(old)) =>
        NarrationSelection(old, Vector(_legacy_voice_engine_diagnostic))
      case (None, None) =>
        NarrationSelection("voicevox", Vector.empty)
    }
  }

  private def _canonical_narration_provider(narration: Json): Option[String] = {
    if (!narration.isObject)
      RAISE.invalidArgumentFault("narration must be an object containing provider.")
    narration.hcursor.downField("provider").focus.map { value =>
      val provider = value.asString.map(_.trim.toLowerCase).getOrElse(
        RAISE.invalidArgumentFault("narration.provider must be a non-empty string.")
      )
      if (provider.isEmpty)
        RAISE.invalidArgumentFault("narration.provider must be a non-empty string.")
      provider
    }
  }

  private val _legacy_voice_engine_diagnostic =
    "Deprecated voice.engine authoring detected; use narration.provider instead."

  private def _narration_voice_identity(provider: String, voice: Json): String =
    provider match {
      case "voicevox" => _voicevox_voice_identity(voice)
      case _ => _json_string(voice, "name").orElse(_json_string(voice, "voice")).getOrElse("default")
    }

  private def _voicevox_voice_identity(voice: Json): String = {
    val speakername = _json_string(voice, "speakerName").getOrElse("ずんだもん")
    val stylename = _json_string(voice, "styleName").getOrElse("ノーマル")
    s"$speakername/$stylename"
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
    CozyVideoPronunciations.default.applyTo(normalized, script.pronunciations)
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
    _write_wav(path, WaveData(1, _default_sample_rate, _default_audio_channels, _default_audio_bits_per_sample, Array.fill(frames * 2)(0.toByte)))
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
        if (
          wave.audioformat != head.audioformat ||
          wave.samplerate != head.samplerate ||
          wave.channels != head.channels ||
          wave.bitspersample != head.bitspersample
        )
          RAISE.invalidArgumentFault(s"Cannot concatenate wav with different params: $output")
      }
      val out = new ByteArrayOutputStream()
      waves.foreach(x => out.write(x.data))
      _write_wav(output, head.copy(data = out.toByteArray))
    }
  }

  private def _read_wav(path: Path): WaveData = {
    _read_wav_bytes(Files.readAllBytes(path), path.toString)
  }

  private def _read_wav_bytes(bytes: Array[Byte], label: String): WaveData = {
    if (bytes.length < 44 || _ascii(bytes, 0, 4) != "RIFF" || _ascii(bytes, 8, 4) != "WAVE")
      RAISE.invalidArgumentFault(s"Invalid WAV file: $label")
    var offset = 12
    var audioformat = 0
    var samplerate = 0
    var channels = 0
    var bitspersample = 0
    var data = Array.emptyByteArray
    while (offset + 8 <= bytes.length) {
      val id = _ascii(bytes, offset, 4)
      val size = _read_int_le(bytes, offset + 4)
      if (size < 0)
        RAISE.invalidArgumentFault(s"Invalid WAV file: $label")
      val start = offset + 8
      val end = start.toLong + size.toLong
      if (end > bytes.length.toLong)
        RAISE.invalidArgumentFault(s"Invalid WAV file: $label")
      id match {
        case "fmt " =>
          if (size < 16)
            RAISE.invalidArgumentFault(s"Invalid WAV file: $label")
          audioformat = _read_short_le(bytes, start)
          channels = _read_short_le(bytes, start + 2)
          samplerate = _read_int_le(bytes, start + 4)
          bitspersample = _read_short_le(bytes, start + 14)
        case "data" =>
          data = bytes.slice(start, end.toInt)
        case _ =>
      }
      val next = end + (size & 1)
      offset = math.min(next, bytes.length.toLong).toInt
    }
    if (audioformat <= 0 || samplerate <= 0 || channels <= 0 || bitspersample <= 0 || data.isEmpty)
      RAISE.invalidArgumentFault(s"Invalid WAV file: $label")
    WaveData(audioformat, samplerate, channels, bitspersample, data)
  }

  private def _normalize_provider_wav(bytes: Array[Byte], label: String): WaveData = {
    val source = _read_wav_bytes(bytes, label)
    if (source.audioformat != 1)
      RAISE.invalidArgumentFault(s"Unsupported WAV encoding for $label: format=${source.audioformat}. Use PCM WAV.")
    val bytespersample = source.bitspersample / 8
    if (!Set(8, 16, 24, 32).contains(source.bitspersample) || bytespersample * 8 != source.bitspersample)
      RAISE.invalidArgumentFault(
        s"Unsupported PCM bit depth for $label: ${source.bitspersample}. Supported bit depths: 8, 16, 24, 32."
      )
    val framesize = bytespersample * source.channels
    if (source.data.length % framesize != 0)
      RAISE.invalidArgumentFault(s"Invalid PCM frame alignment for $label.")
    val sourceframes = source.data.length / framesize
    val mono = Array.tabulate(sourceframes) { frame =>
      var sum = 0.0
      var channel = 0
      while (channel < source.channels) {
        sum += _pcm_sample(source.data, (frame * source.channels + channel) * bytespersample, source.bitspersample)
        channel += 1
      }
      sum / source.channels.toDouble
    }
    val targetframes = math.max(1, math.round(sourceframes.toDouble * _default_sample_rate.toDouble / source.samplerate.toDouble).toInt)
    val data = new Array[Byte](targetframes * 2)
    var frame = 0
    while (frame < targetframes) {
      val position = frame.toDouble * source.samplerate.toDouble / _default_sample_rate.toDouble
      val lower = math.min(sourceframes - 1, position.toInt)
      val upper = math.min(sourceframes - 1, lower + 1)
      val fraction = position - lower.toDouble
      val sample = mono(lower) + (mono(upper) - mono(lower)) * fraction
      val encoded = math.round(math.max(-1.0, math.min(1.0, sample)) * 32767.0).toInt
      data(frame * 2) = (encoded & 0xff).toByte
      data(frame * 2 + 1) = ((encoded >>> 8) & 0xff).toByte
      frame += 1
    }
    WaveData(1, _default_sample_rate, _default_audio_channels, _default_audio_bits_per_sample, data)
  }

  private def _pcm_sample(data: Array[Byte], offset: Int, bitspersample: Int): Double =
    bitspersample match {
      case 8 => ((data(offset) & 0xff) - 128).toDouble / 128.0
      case 16 => _read_short_le(data, offset).toShort.toDouble / 32768.0
      case 24 =>
        val raw = (data(offset) & 0xff) | ((data(offset + 1) & 0xff) << 8) | ((data(offset + 2) & 0xff) << 16)
        val signed = if ((raw & 0x800000) != 0) raw | 0xff000000 else raw
        signed.toDouble / 8388608.0
      case 32 => _read_int_le(data, offset).toDouble / 2147483648.0
    }

  private def _write_wav(path: Path, wave: WaveData): Unit = {
    Files.createDirectories(path.getParent)
    val out = new ByteArrayOutputStream()
    _write_ascii(out, "RIFF")
    _write_int_le(out, 36 + wave.data.length)
    _write_ascii(out, "WAVE")
    _write_ascii(out, "fmt ")
    _write_int_le(out, 16)
    _write_short_le(out, wave.audioformat)
    _write_short_le(out, wave.channels)
    _write_int_le(out, wave.samplerate)
    _write_int_le(out, wave.samplerate * wave.channels * wave.bitspersample / 8)
    _write_short_le(out, wave.channels * wave.bitspersample / 8)
    _write_short_le(out, wave.bitspersample)
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
        "tailSilence" -> Json.fromDoubleOrNull(entry.tailSilence),
        "provider" -> entry.provider.map(Json.fromString).getOrElse(Json.Null),
        "executionMode" -> entry.executionMode.map(Json.fromString).getOrElse(Json.Null),
        "voiceIdentity" -> entry.voiceIdentity.map(Json.fromString).getOrElse(Json.Null),
        "voiceId" -> entry.voiceId.map(Json.fromString).getOrElse(Json.Null),
        "modelIdentity" -> entry.modelIdentity.map(Json.fromString).getOrElse(Json.Null),
        "sampleRate" -> entry.sampleRate.map(Json.fromInt).getOrElse(Json.Null),
        "channels" -> entry.channels.map(Json.fromInt).getOrElse(Json.Null),
        "bitsPerSample" -> entry.bitsPerSample.map(Json.fromInt).getOrElse(Json.Null)
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

  private def _validate_render_tools(renderer: String, execution: VideoExecutionConfig, checks: Vector[VideoToolCheck]): Unit =
    if (checks.nonEmpty) {
      val required =
        execution.toolMode match {
          case VideoToolMode.Docker => Set("docker-toolchain", "docker-image", "textus-toolchain-image")
          case VideoToolMode.Host =>
            renderer match {
              case "remotion" => Set("remotion-node")
              case "simple-java2d" => Set("python-pillow", "ffmpeg")
            }
          case VideoToolMode.ExternalService => Set.empty[String]
        }
      checks.filter(x => required.contains(x.name) && x.status == VideoToolStatus.Missing).headOption.foreach { check =>
        val hint = check.setupHint.map(x => s" $x").getOrElse("")
        RAISE.invalidArgumentFault(s"Cannot render with $renderer: ${check.name} is missing.${hint}")
      }
    }

  private def _validate_synthesis_tools(provider: String, checks: Vector[VideoToolCheck]): Unit =
    if (checks.nonEmpty) {
      checks.find(_.name == provider) match {
        case Some(check) if check.status == VideoToolStatus.Available =>
        case Some(check) =>
          val hint = check.setupHint.map(x => s" $x").getOrElse("")
          RAISE.invalidArgumentFault(s"Cannot synthesize narration: ${check.name} is not available.${hint}")
        case None =>
          RAISE.invalidArgumentFault(s"Cannot synthesize narration: no tool check is registered for provider $provider.")
      }
    }

  private def _validate_build_tools(execution: VideoExecutionConfig, checks: Vector[VideoToolCheck]): Unit =
    if (checks.nonEmpty) {
      val required =
        execution.toolMode match {
          case VideoToolMode.Docker => Set("docker-toolchain", "docker-image", "textus-toolchain-image")
          case VideoToolMode.Host => Set("ffmpeg")
          case VideoToolMode.ExternalService => Set.empty[String]
        }
      checks.filter(x => required.contains(x.name) && x.status == VideoToolStatus.Missing).headOption.foreach { check =>
        val hint = check.setupHint.map(x => s" $x").getOrElse("")
        RAISE.invalidArgumentFault(s"Cannot build video: ${check.name} is missing.${hint}")
      }
    }

  private def _validate_transcribe_tools(execution: VideoTranscribeExecution, checks: Vector[VideoToolCheck]): Unit =
    if (checks.nonEmpty) {
      val required =
        execution.toolMode match {
          case VideoToolMode.Docker => Set("docker-toolchain", "docker-image", "textus-toolchain-image")
          case VideoToolMode.Host => Set("ffmpeg", "whisper-cpp")
          case VideoToolMode.ExternalService => Set.empty[String]
        }
      checks.filter(x => required.contains(x.name) && (x.status == VideoToolStatus.Missing || x.status == VideoToolStatus.Unchecked)).headOption.foreach { check =>
        val hint = check.setupHint.map(x => s" $x").getOrElse("")
        RAISE.invalidArgumentFault(s"Cannot transcribe video: ${check.name} is not available.${hint}")
      }
    }

  private def _validate_replay_tools(execution: VideoExecutionConfig, checks: Vector[VideoToolCheck]): Unit =
    if (checks.nonEmpty) {
      val required =
        execution.toolMode match {
          case VideoToolMode.Docker => Set("docker-toolchain", "docker-image", "textus-toolchain-image")
          case VideoToolMode.Host => Set("playwright")
          case VideoToolMode.ExternalService => Set.empty[String]
        }
      checks.filter(x => required.contains(x.name) && (x.status == VideoToolStatus.Missing || x.status == VideoToolStatus.Unchecked)).headOption.foreach { check =>
        val hint = check.setupHint.map(x => s" $x").getOrElse("")
        RAISE.invalidArgumentFault(s"Cannot replay video demo: ${check.name} is not available.${hint}")
      }
    }

  private def _transcribe_execution(config: TranscribeConfig): VideoTranscribeExecution = {
    val defaults = CozyProjectYamlConfig.loadOperationDefaults(config.projectRoot)
    val mode = config.toolMode.
      orElse(defaults.value("video.tool-mode")).
      getOrElse("docker")
    val dockerimage = config.dockerImage.
      orElse(defaults.value("video.docker-image")).
      orElse(defaults.value("cozy.docker-image")).
      getOrElse(VideoToolSettings.DEFAULT_DOCKER_IMAGE)
    val toolmode = VideoToolMode.parse(mode)
    val hostmodel = _resolve_whisper_model(config.projectRoot, config.whisperModel.
      orElse(defaults.value("tools.whisperModel")).
      orElse(defaults.value("tools.whisper-model")).
      orElse(defaults.value("video.whisper.model")).
      orElse(defaults.value("video.whisper-model")))
    val modelpath =
      toolmode match {
        case VideoToolMode.Docker => _docker_whisper_model
        case VideoToolMode.Host =>
          hostmodel.map(_.toString).getOrElse(
            RAISE.invalidArgumentFault("Host transcription requires --whisper-model, tools.whisperModel, or video.whisper.model.")
          )
        case VideoToolMode.ExternalService =>
          RAISE.invalidArgumentFault("Transcription does not support external-service tool mode.")
      }
    VideoTranscribeExecution(toolmode, dockerimage, modelpath, hostmodel)
  }

  private def _resolve_whisper_model(projectroot: Path, value: Option[String]): Option[Path] =
    value.map { x =>
      val path = Path.of(x)
      if (path.isAbsolute) path.normalize() else projectroot.resolve(path).normalize()
    }

  private def _transcribe_video(
    config: TranscribeConfig,
    execution: VideoTranscribeExecution,
    runner: VideoProcessRunner
  ): VideoTranscriptionResult = {
    val input = config.inputVideo.toAbsolutePath.normalize()
    if (!Files.isRegularFile(input))
      RAISE.invalidArgumentFault(s"Missing input video for transcription: $input")
    val savedir = config.saveDir.toAbsolutePath.normalize()
    _validate_transcribe_docker_paths(config.projectRoot, execution, input, savedir)
    Files.createDirectories(savedir)
    val audio = savedir.resolve("audio.wav").normalize()
    val transcript = savedir.resolve("transcript.json").normalize()
    val captions = savedir.resolve("captions.srt").normalize()
    val narration = savedir.resolve("narration.json").normalize()
    val manifest = savedir.resolve("manifest.json").normalize()
    val whisperbase = savedir.resolve("whisper").normalize()
    _run_transcribe_ffmpeg(config.projectRoot, execution, input, audio, runner)
    val whisperversion = _run_whisper_version(config.projectRoot, execution, runner)
    _run_whisper(config.projectRoot, execution, audio, whisperbase, runner)
    val whisperjson = whisperbase.resolveSibling(whisperbase.getFileName.toString + ".json")
    val segments = _read_whisper_segments(whisperjson)
    Files.writeString(transcript, _transcript_json(input, segments).spaces2, StandardCharsets.UTF_8)
    Files.writeString(captions, _srt_text(segments), StandardCharsets.UTF_8)
    Files.writeString(narration, _narration_json(segments).spaces2, StandardCharsets.UTF_8)
    Files.writeString(manifest, _transcription_manifest(config, execution, input, audio, transcript, captions, narration, whisperversion, segments.size).spaces2, StandardCharsets.UTF_8)
    VideoTranscriptionResult(input, savedir, audio, transcript, captions, narration, manifest, execution.toolMode, execution.dockerImage, execution.modelPath, segments.size)
  }

  private def _validate_transcribe_docker_paths(
    projectroot: Path,
    execution: VideoTranscribeExecution,
    input: Path,
    savedir: Path
  ): Unit =
    if (execution.toolMode == VideoToolMode.Docker) {
      if (!input.startsWith(projectroot))
        RAISE.invalidArgumentFault(s"Docker transcription requires input video under project root $projectroot: $input")
      if (!savedir.startsWith(projectroot))
        RAISE.invalidArgumentFault(s"Docker transcription requires --save under project root $projectroot: $savedir")
    }

  private def _run_transcribe_ffmpeg(
    projectroot: Path,
    execution: VideoTranscribeExecution,
    input: Path,
    audio: Path,
    runner: VideoProcessRunner
  ): Unit = {
    val args = _execution_command(projectroot, execution.toVideoExecutionConfig, "ffmpeg", Vector(
      "-y",
      "-i",
      _execution_path(projectroot, execution.toVideoExecutionConfig, input),
      "-vn",
      "-ac",
      "1",
      "-ar",
      "16000",
      _execution_path(projectroot, execution.toVideoExecutionConfig, audio)
    ))
    val result = runner.run(args, projectroot)
    if (!result.isSuccess)
      RAISE.invalidArgumentFault(s"ffmpeg audio extraction failed: ${result.stderr.trim}")
    if (!Files.isRegularFile(audio))
      RAISE.invalidArgumentFault(s"ffmpeg audio extraction did not create output: $audio")
  }

  private def _run_whisper_version(projectroot: Path, execution: VideoTranscribeExecution, runner: VideoProcessRunner): Option[String] = {
    val result = runner.run(_execution_command(projectroot, execution.toVideoExecutionConfig, "whisper-cli", Vector("--version")), projectroot)
    if (result.isSuccess) Some(result.text).filter(_.nonEmpty) else None
  }

  private def _run_whisper(
    projectroot: Path,
    execution: VideoTranscribeExecution,
    audio: Path,
    outputbase: Path,
    runner: VideoProcessRunner
  ): Unit = {
    val args = _execution_command(projectroot, execution.toVideoExecutionConfig, "whisper-cli", Vector(
      "-m",
      _execution_model_path(projectroot, execution),
      "-f",
      _execution_path(projectroot, execution.toVideoExecutionConfig, audio),
      "-oj",
      "-osrt",
      "-of",
      _execution_path(projectroot, execution.toVideoExecutionConfig, outputbase)
    ))
    val result = runner.run(args, projectroot)
    if (!result.isSuccess)
      RAISE.invalidArgumentFault(s"whisper.cpp transcription failed: ${result.stderr.trim}")
  }

  private def _execution_model_path(projectroot: Path, execution: VideoTranscribeExecution): String =
    execution.toolMode match {
      case VideoToolMode.Docker => execution.modelPath
      case VideoToolMode.Host => execution.modelPath
      case VideoToolMode.ExternalService => RAISE.invalidArgumentFault("whisper model path cannot use external-service tool mode")
    }

  private def _read_whisper_segments(path: Path): Vector[VideoTranscriptSegment] = {
    if (!Files.isRegularFile(path))
      RAISE.invalidArgumentFault(s"whisper.cpp did not create JSON transcript: $path")
    val json = parser.parse(Files.readString(path, StandardCharsets.UTF_8)).fold(
      e => RAISE.invalidArgumentFault(s"whisper.cpp returned invalid JSON: ${e.getMessage}"),
      identity
    )
    val segments = _json_array(json, "segments").map { xs =>
      xs.zipWithIndex.map {
        case (x, i) =>
          VideoTranscriptSegment(
            i + 1,
            _json_double(x, "start").getOrElse(0.0),
            _json_double(x, "end").getOrElse(0.0),
            _json_string(x, "text").getOrElse("").trim
          )
      }
    }.orElse {
      _json_array(json, "transcription").map { xs =>
        xs.zipWithIndex.map {
          case (x, i) =>
            val timestamps = x.hcursor.downField("timestamps").focus.getOrElse(Json.obj())
            VideoTranscriptSegment(
              i + 1,
              _parse_timestamp_seconds(_json_string(timestamps, "from").getOrElse("0")),
              _parse_timestamp_seconds(_json_string(timestamps, "to").getOrElse("0")),
              _json_string(x, "text").getOrElse("").trim
            )
        }
      }
    }.getOrElse(Vector.empty)
    if (segments.isEmpty)
      RAISE.invalidArgumentFault(s"whisper.cpp JSON contains no transcript segments: $path")
    segments
  }

  private def _parse_timestamp_seconds(value: String): Double = {
    val normalized = value.trim.replace(',', '.')
    val parts = normalized.split(":").toVector
    try {
      parts match {
        case Vector(h, m, s) => h.toDouble * 3600 + m.toDouble * 60 + s.toDouble
        case Vector(m, s) => m.toDouble * 60 + s.toDouble
        case Vector(s) => s.toDouble
        case _ => 0.0
      }
    } catch {
      case NonFatal(_) => 0.0
    }
  }

  private def _transcript_json(input: Path, segments: Vector[VideoTranscriptSegment]): Json =
    Json.obj(
      "schema" -> Json.fromString("cozy.video.transcript.v1"),
      "source" -> Json.fromString(input.toString),
      "segments" -> Json.fromValues(segments.map(_transcript_segment_json))
    )

  private def _transcript_segment_json(segment: VideoTranscriptSegment): Json =
    Json.obj(
      "index" -> Json.fromInt(segment.index),
      "start" -> Json.fromDoubleOrNull(_round3(segment.start)),
      "end" -> Json.fromDoubleOrNull(_round3(segment.end)),
      "text" -> Json.fromString(segment.text)
    )

  private def _narration_json(segments: Vector[VideoTranscriptSegment]): Json =
    Json.obj(
      "schema" -> Json.fromString("cozy.video.narration-draft.v1"),
      "scenes" -> Json.fromValues(segments.map { segment =>
        Json.obj(
          "id" -> Json.fromString(f"segment-${segment.index}%03d"),
          "narration" -> Json.fromString(segment.text),
          "start" -> Json.fromDoubleOrNull(_round3(segment.start)),
          "end" -> Json.fromDoubleOrNull(_round3(segment.end))
        )
      })
    )

  private def _srt_text(segments: Vector[VideoTranscriptSegment]): String =
    segments.map { segment =>
      Vector(
        segment.index.toString,
        s"${_srt_timestamp(segment.start)} --> ${_srt_timestamp(segment.end)}",
        segment.text,
        ""
      ).mkString("\n")
    }.mkString("\n")

  private def _srt_timestamp(seconds: Double): String = {
    val millis = math.max(0L, math.round(seconds * 1000))
    val h = millis / 3600000
    val m = (millis % 3600000) / 60000
    val s = (millis % 60000) / 1000
    val ms = millis % 1000
    f"$h%02d:$m%02d:$s%02d,$ms%03d"
  }

  private def _transcription_manifest(
    config: TranscribeConfig,
    execution: VideoTranscribeExecution,
    input: Path,
    audio: Path,
    transcript: Path,
    captions: Path,
    narration: Path,
    whisperversion: Option[String],
    segmentcount: Int
  ): Json =
    Json.obj(
      "schema" -> Json.fromString("cozy.video.transcription-manifest.v1"),
      "inputVideo" -> Json.fromString(input.toString),
      "inputSha256" -> Json.fromString(_sha256(input)),
      "audioPath" -> Json.fromString(audio.toString),
      "transcriptPath" -> Json.fromString(transcript.toString),
      "captionsPath" -> Json.fromString(captions.toString),
      "narrationPath" -> Json.fromString(narration.toString),
      "toolMode" -> Json.fromString(execution.toolMode.label),
      "dockerImage" -> Json.fromString(execution.dockerImage),
      "modelPath" -> Json.fromString(execution.modelPath),
      "whisperVersion" -> whisperversion.map(Json.fromString).getOrElse(Json.Null),
      "segmentCount" -> Json.fromInt(segmentcount),
      "commands" -> Json.fromValues(Vector(
        Json.obj("name" -> Json.fromString("extract-audio"), "tool" -> Json.fromString("ffmpeg")),
        Json.obj("name" -> Json.fromString("transcribe"), "tool" -> Json.fromString("whisper-cli"))
      ))
    )

  private def _sha256(path: Path): String = {
    val digest = MessageDigest.getInstance("SHA-256")
    val in = Files.newInputStream(path)
    try {
      val buffer = new Array[Byte](8192)
      var n = in.read(buffer)
      while (n >= 0) {
        if (n > 0)
          digest.update(buffer, 0, n)
        n = in.read(buffer)
      }
    } finally {
      in.close()
    }
    digest.digest().map("%02x".format(_)).mkString
  }

  private def _build_project(plan: VideoPlan, runner: VideoProcessRunner): VideoBuildResult = {
    val partoutputs = plan.parts.filter(_.renderable).map(_.outputPath)
    if (partoutputs.isEmpty)
      RAISE.invalidArgumentFault("No rendered video part outputs found for final assembly.")
    partoutputs.foreach { path =>
      if (!Files.isRegularFile(path))
        RAISE.invalidArgumentFault(s"Missing rendered part output: $path. Run: cozy video render <project-file> --renderer=remotion|simple-java2d")
    }
    val concatlist = _ffmpeg_concat_list_path(plan.projectRoot)
    _write_ffmpeg_concat_list(plan.projectRoot, plan.execution, concatlist, partoutputs)
    _run_build_ffmpeg(plan.projectRoot, plan.execution, concatlist, plan.outputPath, runner)
    if (!Files.isRegularFile(plan.outputPath))
      RAISE.invalidArgumentFault(s"ffmpeg concat/mux did not create output: ${plan.outputPath}")
    val ffprobe = _run_build_ffprobe(plan.projectRoot, plan.execution, plan.outputPath, runner)
    val summary = _ffprobe_summary(ffprobe)
    _write_project_manifest(plan, concatlist, partoutputs, summary)
    VideoBuildResult(plan.projectFile, plan.outputPath, plan.manifestPath, concatlist, partoutputs, plan.execution.toolMode, plan.execution.dockerImage, summary)
  }

  private def _ffmpeg_concat_list_path(projectroot: Path): Path =
    projectroot.resolve("target/cozy-video/ffmpeg/concat.txt").normalize()

  private def _write_ffmpeg_concat_list(
    projectroot: Path,
    execution: VideoExecutionConfig,
    path: Path,
    partoutputs: Vector[Path]
  ): Unit = {
    Files.createDirectories(path.getParent)
    val lines = partoutputs.map { part =>
      val value =
        execution.toolMode match {
          case VideoToolMode.Docker => _docker_path(projectroot, part)
          case _ => part.toString
        }
      s"file '${_ffmpeg_concat_escape(value)}'"
    }
    Files.writeString(path, lines.mkString("", "\n", "\n"), StandardCharsets.UTF_8)
  }

  private def _ffmpeg_concat_escape(value: String): String =
    value.replace("\\", "\\\\").replace("'", "\\'")

  private def _run_build_ffmpeg(
    projectroot: Path,
    execution: VideoExecutionConfig,
    concatlist: Path,
    output: Path,
    runner: VideoProcessRunner
  ): Unit = {
    Files.createDirectories(output.getParent)
    val concatarg = _execution_path(projectroot, execution, concatlist)
    val outputarg = _execution_path(projectroot, execution, output)
    val args = _execution_command(projectroot, execution, "ffmpeg", Vector(
      "-y",
      "-f",
      "concat",
      "-safe",
      "0",
      "-i",
      concatarg,
      "-c",
      "copy",
      outputarg
    ))
    val result = runner.run(args, projectroot)
    if (!result.isSuccess)
      RAISE.invalidArgumentFault(s"ffmpeg concat/mux failed: ${result.stderr.trim}")
  }

  private def _run_build_ffprobe(
    projectroot: Path,
    execution: VideoExecutionConfig,
    output: Path,
    runner: VideoProcessRunner
  ): VideoCommandResult = {
    val outputarg = _execution_path(projectroot, execution, output)
    val args = _execution_command(projectroot, execution, "ffprobe", Vector(
      "-v",
      "error",
      "-print_format",
      "json",
      "-show_format",
      "-show_streams",
      outputarg
    ))
    val result = runner.run(args, projectroot)
    if (!result.isSuccess)
      RAISE.invalidArgumentFault(s"ffprobe validation failed: ${result.stderr.trim}")
    result
  }

  private def _execution_path(projectroot: Path, execution: VideoExecutionConfig, path: Path): String =
    execution.toolMode match {
      case VideoToolMode.Docker => _docker_path(projectroot, path)
      case _ => path.toString
    }

  private def _execution_command(
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

  private def _ffprobe_summary(result: VideoCommandResult): Json =
    parser.parse(result.stdout).fold(
      e => RAISE.invalidArgumentFault(s"ffprobe returned invalid JSON: ${e.getMessage}"),
      identity
    )

  private def _write_project_manifest(plan: VideoPlan, concatlist: Path, partoutputs: Vector[Path], ffprobe: Json): Unit = {
    Files.createDirectories(plan.manifestPath.getParent)
    val json = Json.obj(
      "projectFile" -> Json.fromString(plan.projectFile.toString),
      "outputPath" -> Json.fromString(plan.outputPath.toString),
      "partOutputs" -> Json.fromValues(partoutputs.map(x => Json.fromString(x.toString))),
      "toolMode" -> Json.fromString(plan.execution.toolMode.label),
      "dockerImage" -> Json.fromString(plan.execution.dockerImage),
      "concatListPath" -> Json.fromString(concatlist.toString),
      "ffprobe" -> ffprobe
    )
    Files.writeString(plan.manifestPath, json.spaces2, StandardCharsets.UTF_8)
  }

  private def _replay_script(
    config: ReplayConfig,
    script: VideoReplayScript,
    execution: VideoExecutionConfig,
    runner: VideoProcessRunner
  ): VideoReplayResult = {
    val scriptfile = config.scriptFile.toAbsolutePath.normalize()
    val output = config.saveFile.map(_.toAbsolutePath.normalize())
    _validate_replay_output(output)
    _validate_replay_docker_paths(config.projectRoot, execution, scriptfile, output)
    val workdir = _replay_work_dir(config.projectRoot, scriptfile)
    val manifest = workdir.resolve("manifest.json").normalize()
    val command = _replay_command(config.projectRoot, execution, scriptfile, workdir, output)
    if (config.dryRun)
      VideoReplayResult(scriptfile, manifest, output, execution.toolMode, execution.dockerImage, dryRun = true, Vector(command))
    else {
      _write_replay_workspace(config.projectRoot, scriptfile, script, workdir, output)
      val result = runner.run(_replay_command_args(config.projectRoot, execution, workdir), config.projectRoot)
      if (!result.isSuccess)
        RAISE.invalidArgumentFault(s"Playwright replay failed: ${result.stderr.trim}")
      output.foreach { path =>
        if (!Files.isRegularFile(path))
          RAISE.invalidArgumentFault(s"Playwright replay did not create output video: $path")
      }
      _write_replay_manifest(scriptfile, script, manifest, output, execution, command)
      VideoReplayResult(scriptfile, manifest, output, execution.toolMode, execution.dockerImage, dryRun = false, Vector(command))
    }
  }

  private def _validate_replay_output(output: Option[Path]): Unit =
    output.foreach { path =>
      if (!path.getFileName.toString.toLowerCase(java.util.Locale.ROOT).endsWith(".webm"))
        RAISE.invalidArgumentFault(s"Playwright replay recording output must use .webm: $path")
    }

  private def _validate_replay_docker_paths(
    projectroot: Path,
    execution: VideoExecutionConfig,
    scriptfile: Path,
    output: Option[Path]
  ): Unit =
    if (execution.toolMode == VideoToolMode.Docker) {
      if (!scriptfile.startsWith(projectroot))
        RAISE.invalidArgumentFault(s"Docker replay requires script file under project root $projectroot: $scriptfile")
      output.foreach { path =>
        if (!path.startsWith(projectroot))
          RAISE.invalidArgumentFault(s"Docker replay requires --save under project root $projectroot: $path")
      }
    }

  private def _replay_work_dir(projectroot: Path, scriptfile: Path): Path =
    projectroot.resolve("target/cozy-video/replay").resolve(_file_segment_id(_basename(scriptfile), "replay script stem")).normalize()

  private def _write_replay_workspace(
    projectroot: Path,
    scriptfile: Path,
    script: VideoReplayScript,
    workdir: Path,
    output: Option[Path]
  ): Unit = {
    Files.createDirectories(workdir)
    val props = Json.obj(
      "scriptPath" -> Json.fromString(_project_relative(projectroot, scriptfile)),
      "outputPath" -> output.map(path => Json.fromString(_project_relative(projectroot, path))).getOrElse(Json.Null),
      "viewport" -> Json.obj("width" -> Json.fromInt(script.viewport.width), "height" -> Json.fromInt(script.viewport.height)),
      "manualReview" -> Json.fromBoolean(script.manualReview),
      "steps" -> Json.fromValues(script.steps.map(VideoReplayStep.toJson))
    )
    Files.writeString(workdir.resolve("props.json"), props.spaces2, StandardCharsets.UTF_8)
    Files.writeString(workdir.resolve("replay.mjs"), _playwright_replay_mjs, StandardCharsets.UTF_8)
  }

  private def _replay_command(
    projectroot: Path,
    execution: VideoExecutionConfig,
    scriptfile: Path,
    workdir: Path,
    output: Option[Path]
  ): VideoCommandPlan =
    VideoCommandPlan(
      "replay.playwright",
      "playwright",
      execution.toolMode,
      _replay_command_preview(projectroot, execution, workdir),
      Vector(scriptfile),
      output.toVector
    )

  private def _replay_command_preview(projectroot: Path, execution: VideoExecutionConfig, workdir: Path): String =
    _replay_command_args(projectroot, execution, workdir).map(_shell_quote).mkString(" ")

  private def _replay_command_args(projectroot: Path, execution: VideoExecutionConfig, workdir: Path): Vector[String] = {
    val script = workdir.resolve("replay.mjs").normalize()
    execution.toolMode match {
      case VideoToolMode.Docker =>
        Vector("docker", "run", "--rm", "-v", s"${projectroot}:/workspace", "-w", "/workspace", execution.dockerImage, "node", _docker_path(projectroot, script))
      case VideoToolMode.Host =>
        Vector("node", script.toString)
      case VideoToolMode.ExternalService =>
        RAISE.invalidArgumentFault("Playwright replay cannot use external-service tool mode")
    }
  }

  private val _playwright_replay_mjs: String =
    """import fs from 'node:fs';
      |import path from 'node:path';
      |import {fileURLToPath} from 'node:url';
      |import {chromium} from 'playwright';
      |
      |const workDir = path.dirname(fileURLToPath(import.meta.url));
      |const projectRoot = process.cwd();
      |const props = JSON.parse(fs.readFileSync(path.join(workDir, 'props.json'), 'utf8'));
      |const recordDir = path.join(workDir, 'video');
      |const contextOptions = {
      |  viewport: props.viewport || {width: 1280, height: 720}
      |};
      |if (props.outputPath) {
      |  fs.mkdirSync(recordDir, {recursive: true});
      |  contextOptions.recordVideo = {dir: recordDir, size: contextOptions.viewport};
      |}
      |const browser = await chromium.launch({headless: true});
      |const context = await browser.newContext(contextOptions);
      |const page = await context.newPage();
      |
      |for (const step of props.steps || []) {
      |  if (step.kind === 'goto' && step.url) await page.goto(step.url);
      |  else if (step.kind === 'click' && step.selector) await page.click(step.selector);
      |  else if (step.kind === 'fill' && step.selector) await page.fill(step.selector, step.text || '');
      |  else if (step.kind === 'press' && step.selector && step.key) await page.press(step.selector, step.key);
      |  else if (step.kind === 'wait') await page.waitForTimeout(Number(step.delayMs || 250));
      |  else if (step.kind === 'screenshot') await page.screenshot({path: path.join(workDir, `${Date.now()}.png`)});
      |}
      |
      |await context.close();
      |await browser.close();
      |
      |if (props.outputPath) {
      |  const files = fs.readdirSync(recordDir).filter((x) => x.endsWith('.webm') || x.endsWith('.mp4')).sort();
      |  if (files.length === 0) throw new Error('Playwright did not produce a recorded video');
      |  const output = path.resolve(projectRoot, props.outputPath);
      |  fs.mkdirSync(path.dirname(output), {recursive: true});
      |  fs.copyFileSync(path.join(recordDir, files[0]), output);
      |}
      |""".stripMargin

  private def _write_replay_manifest(
    scriptfile: Path,
    script: VideoReplayScript,
    manifest: Path,
    output: Option[Path],
    execution: VideoExecutionConfig,
    command: VideoCommandPlan
  ): Unit = {
    Files.createDirectories(manifest.getParent)
    val json = Json.obj(
      "schema" -> Json.fromString("cozy.video.replay-manifest.v1"),
      "scriptFile" -> Json.fromString(scriptfile.toString),
      "sourceVideo" -> script.sourceVideo.map(Json.fromString).getOrElse(Json.Null),
      "sourceSha256" -> script.sourceSha256.map(Json.fromString).getOrElse(Json.Null),
      "manualReview" -> Json.fromBoolean(script.manualReview),
      "outputVideo" -> output.map(path => Json.fromString(path.toString)).getOrElse(Json.Null),
      "toolMode" -> Json.fromString(execution.toolMode.label),
      "dockerImage" -> Json.fromString(execution.dockerImage),
      "stepCount" -> Json.fromInt(script.steps.size),
      "commands" -> Json.fromValues(Vector(Json.obj(
        "name" -> Json.fromString(command.stepName),
        "tool" -> Json.fromString(command.toolName),
        "preview" -> Json.fromString(command.preview)
      )))
    )
    Files.writeString(manifest, json.spaces2, StandardCharsets.UTF_8)
  }

  private def _write_video_rdf(config: RdfConfig, plan: VideoPlan): VideoRdfResult = {
    Files.createDirectories(config.saveDir)
    val graph = _video_rdf_graph(plan)
    val turtle = RdfRenderer.toTurtle(graph, _video_rdf_context)
    val jsonld = RdfRenderer.toJsonLD(graph, RdfRenderer.JsonLDProfile.BoK, userContext = _video_rdf_context)
    val turtlefile = config.saveDir.resolve("video.ttl").normalize()
    val jsonldfile = config.saveDir.resolve("video.jsonld").normalize()
    val manifestfile = config.saveDir.resolve("manifest.json").normalize()
    Files.writeString(turtlefile, turtle, StandardCharsets.UTF_8)
    Files.writeString(jsonldfile, jsonld, StandardCharsets.UTF_8)
    val result = VideoRdfResult(
      plan.projectFile,
      config.saveDir,
      turtlefile,
      jsonldfile,
      manifestfile,
      graph.triples.size,
      graph.triples.map(_.subject).distinct.size
    )
    Files.writeString(manifestfile, _video_rdf_manifest(result).spaces2, StandardCharsets.UTF_8)
    result
  }

  private def _video_rdf_manifest(result: VideoRdfResult): Json =
    Json.obj(
      "projectFile" -> Json.fromString(result.projectFile.toString),
      "namespace" -> Json.fromString(_video_rdf_namespace),
      "turtleFile" -> Json.fromString(result.turtleFile.toString),
      "jsonLdFile" -> Json.fromString(result.jsonLdFile.toString),
      "tripleCount" -> Json.fromInt(result.tripleCount),
      "resourceCount" -> Json.fromInt(result.resourceCount)
    )

  private def _video_rdf_graph(plan: VideoPlan): Rdf.Graph = {
    val projectid = _video_rdf_resource("project", _project_rdf_slug(plan))
    val projectmanifest = _read_optional_json_manifest(plan.manifestPath, "project manifest")
    val projecttriples =
      Vector(
        _rdf_type(projectid, "VideoProject"),
        _rdf_literal(projectid, Vocabulary.Rdfs.label, plan.project.title.orElse(plan.project.name).getOrElse(_basename(plan.projectFile))),
        _rdf_literal(projectid, _schema("name"), plan.project.name.getOrElse(_basename(plan.projectFile))),
        _rdf_literal(projectid, _dcterms("source"), plan.projectFile.toString),
        _rdf_literal(projectid, _cv("path"), plan.projectFile.toString),
        _rdf_literal(projectid, _cv("outputPath"), plan.outputPath.toString),
        _rdf_literal(projectid, _cv("toolMode"), plan.execution.toolMode.label),
        _rdf_literal(projectid, _cv("dockerImage"), plan.execution.dockerImage)
      ) ++ plan.project.title.map(x => _rdf_literal(projectid, _schema("headline"), x)).toVector ++
        _video_profile_rdf_triples(projectid, plan) ++
        _artifact_link_triples(projectid, _video_rdf_resource("artifact", "project-output"), "project-output", plan.outputPath, "planned", "project.concat") ++
        _artifact_link_triples(projectid, _video_rdf_resource("artifact", "project-manifest"), "project-manifest", plan.manifestPath, _rdf_file_status(plan.manifestPath), "project.manifest") ++
        projectmanifest.toVector.flatMap(json => _json_field_triples(projectid, json, Vector("toolMode", "dockerImage", "concatListPath"), _cv)) ++
        projectmanifest.flatMap(_.hcursor.downField("ffprobe").focus).toVector.map(json => _rdf_literal(projectid, _cv("ffprobe"), json.noSpaces))

    val parttriples = plan.parts.flatMap(part => _video_part_rdf_triples(projectid, part))
    val futureartifacts = Vector(
      plan.projectRoot.resolve("build/transcript.json").normalize() -> "transcript",
      plan.projectRoot.resolve("build/captions.srt").normalize() -> "caption",
      plan.projectRoot.resolve("build/demo-script.json").normalize() -> "replay-script"
    ).filter(x => Files.exists(x._1)).flatMap {
      case (path, kind) =>
        _artifact_link_triples(projectid, _video_rdf_resource("artifact", kind), kind, path, "present", "future.video")
    }
    val replayscript = plan.projectRoot.resolve("build/demo-script.json").normalize()
    val replaytriples =
      if (Files.isRegularFile(replayscript))
        _replay_rdf_triples(projectid, plan.projectRoot, replayscript)
      else
        Vector.empty
    Rdf.Graph(projecttriples ++ parttriples ++ futureartifacts ++ replaytriples)
  }

  private def _video_profile_rdf_triples(projectid: String, plan: VideoPlan): Vector[Rdf.Triple] = {
    val profile = plan.project.profile.map(x => _rdf_literal(projectid, _cv("compositionProfile"), x)).toVector
    val effects = CozyVideoEffects.expand(plan.project.visualEffects).flatMap { effect =>
      val effectid = _video_rdf_resource("visual-effect", s"${_project_rdf_slug(plan)}-${effect.role.key}")
      Vector(
        _rdf_uri(projectid, _cv("hasVisualEffect"), effectid),
        _rdf_type(effectid, "VideoVisualEffect"),
        _rdf_literal(effectid, Vocabulary.Rdfs.label, effect.profile),
        _rdf_literal(effectid, _cv("visualEffectRole"), effect.role.key),
        _rdf_literal(effectid, _cv("visualEffectProfile"), effect.profile)
      ) ++ effect.primitives.map(x => _rdf_literal(effectid, _cv("visualEffectPrimitive"), x.display))
    }
    val assets = plan.assets.flatMap { asset =>
      val assetid = _video_rdf_resource("visual-asset", s"${_project_rdf_slug(plan)}-${asset.role.key}")
      Vector(
        _rdf_uri(projectid, _cv("hasVisualAsset"), assetid),
        _rdf_type(assetid, "VideoVisualAsset"),
        _rdf_literal(assetid, Vocabulary.Rdfs.label, asset.role.key),
        _rdf_literal(assetid, _cv("visualAssetRole"), asset.role.key),
        _rdf_literal(assetid, _cv("path"), asset.displayPath(plan.projectRoot)),
        _rdf_literal(assetid, _cv("assetKind"), asset.kind),
        _rdf_literal(assetid, _dcterms("license"), asset.license),
        _rdf_literal(assetid, _cv("provenance"), asset.provenance),
        _rdf_literal(assetid, _cv("status"), asset.status)
      )
    }
    profile ++ effects ++ assets
  }

  private def _replay_rdf_triples(projectid: String, projectroot: Path, scriptpath: Path): Vector[Rdf.Triple] = {
    val script = _load_replay_script(scriptpath)
    val replayid = _video_rdf_resource("replay", _basename(scriptpath))
    val manifestpath = _replay_work_dir(projectroot, scriptpath).resolve("manifest.json").normalize()
    val manifest = _read_optional_json_manifest(manifestpath, "replay manifest")
    val basetriples =
      Vector(
        _rdf_uri(projectid, _cv("hasReplay"), replayid),
        _rdf_type(replayid, "VideoReplay"),
        _rdf_literal(replayid, Vocabulary.Rdfs.label, _basename(scriptpath)),
        _rdf_literal(replayid, _cv("manualReview"), script.manualReview.toString, Some(_xsd_namespace + "boolean")),
        _rdf_literal(replayid, _cv("stepCount"), script.steps.size.toString, Some(_xsd_namespace + "integer"))
      ) ++ script.sourceVideo.map(x => _rdf_literal(replayid, _cv("sourceVideo"), x)).toVector ++
        script.sourceSha256.map(x => _rdf_literal(replayid, _cv("sourceSha256"), x)).toVector ++
        _artifact_link_triples(replayid, _video_rdf_resource("artifact", "replay-manifest"), "replay-manifest", manifestpath, _rdf_file_status(manifestpath), "replay.playwright") ++
        manifest.toVector.flatMap(json => _json_field_triples(replayid, json, Vector("toolMode", "dockerImage", "outputVideo"), _cv))
    val steptriples = script.steps.zipWithIndex.flatMap {
      case (step, index) =>
        val stepid = _video_rdf_resource("replay-step", s"${_basename(scriptpath)}-${index + 1}")
        Vector(
          _rdf_uri(replayid, _cv("hasReplayStep"), stepid),
          _rdf_type(stepid, "VideoReplayStep"),
          _rdf_literal(stepid, Vocabulary.Rdfs.label, s"step-${index + 1}"),
          _rdf_literal(stepid, _cv("stepOrder"), (index + 1).toString, Some(_xsd_namespace + "integer")),
          _rdf_literal(stepid, _cv("stepKind"), step.kind),
          _rdf_literal(stepid, _cv("manualReview"), step.manualReview.toString, Some(_xsd_namespace + "boolean"))
        ) ++ step.url.map(x => _rdf_literal(stepid, _cv("url"), x)).toVector ++
          step.selector.map(x => _rdf_literal(stepid, _cv("selector"), x)).toVector ++
          step.text.map(x => _rdf_literal(stepid, _schema("text"), x)).toVector ++
          step.timestampMs.map(x => _rdf_literal(stepid, _cv("timestampMs"), x.toString, Some(_xsd_namespace + "integer"))).toVector ++
          step.note.map(x => _rdf_literal(stepid, _cv("note"), x)).toVector
    }
    basetriples ++ steptriples
  }

  private def _video_part_rdf_triples(projectid: String, part: VideoPartPlan): Vector[Rdf.Triple] = {
    val partid = _video_rdf_resource("part", part.id)
    val partmanifest = _read_optional_json_manifest(part.manifestPath, s"part manifest ${part.id}")
    val audiomanifestpath = part.audioDir.map(_.resolve("manifest.json").normalize())
    val audioentries = audiomanifestpath.flatMap(path => _read_optional_audio_manifest(path, s"audio manifest ${part.id}"))
    val basetriples =
      Vector(
        _rdf_uri(projectid, _cv("hasPart"), partid),
        _rdf_type(partid, "VideoPart"),
        _rdf_literal(partid, Vocabulary.Rdfs.label, part.id),
        _rdf_literal(partid, _cv("partType"), part.partType),
        _rdf_literal(partid, _cv("renderer"), part.renderer),
        _rdf_literal(partid, _cv("supported"), part.supported.toString, Some(_xsd_namespace + "boolean")),
        _rdf_literal(partid, _cv("outputPath"), part.outputPath.toString)
      ) ++ part.scriptPath.toVector.flatMap { path =>
        _artifact_link_triples(partid, _video_rdf_resource("artifact", s"script-${part.id}"), "script", path, part.scriptStatus, s"part.${part.id}.input")
      } ++ audiomanifestpath.toVector.flatMap { path =>
        _artifact_link_triples(partid, _video_rdf_resource("artifact", s"audio-manifest-${part.id}"), "audio-manifest", path, _rdf_file_status(path), s"part.${part.id}.synthesize")
      } ++
        _artifact_link_triples(partid, _video_rdf_resource("artifact", s"part-output-${part.id}"), "part-output", part.outputPath, "planned", s"part.${part.id}.render") ++
        _artifact_link_triples(partid, _video_rdf_resource("artifact", s"part-manifest-${part.id}"), "part-manifest", part.manifestPath, _rdf_file_status(part.manifestPath), s"part.${part.id}.manifest") ++
        partmanifest.toVector.flatMap(json => _json_field_triples(partid, json, Vector("renderer", "outputPath", "toolMode", "dockerImage"), _cv))
    val scenetriples = part.script.toVector.flatMap { script =>
      script.expandedScenes.zipWithIndex.flatMap {
        case (scene, index) =>
          val sceneid = scene.id.getOrElse(f"scene-${index + 1}%02d")
          val matchingaudio = audioentries.flatMap(_.find(_.sceneId == sceneid))
          _video_scene_rdf_triples(partid, part, scene, sceneid, index + 1, matchingaudio)
      }
    }
    basetriples ++ scenetriples
  }

  private def _video_scene_rdf_triples(
    partid: String,
    part: VideoPartPlan,
    scene: VideoScene,
    sceneid: String,
    index: Int,
    audio: Option[VideoAudioManifestEntry]
  ): Vector[Rdf.Triple] = {
    val sceneuri = _video_rdf_resource("scene", s"${part.id}-$sceneid")
    val utteranceuri = _video_rdf_resource("utterance", s"${part.id}-$sceneid")
    val text = scene.narration.orElse(scene.line).orElse(scene.caption)
    val basetriples = Vector(
      _rdf_uri(partid, _cv("hasScene"), sceneuri),
      _rdf_type(sceneuri, "VideoScene"),
      _rdf_literal(sceneuri, Vocabulary.Rdfs.label, sceneid),
      _rdf_literal(sceneuri, _cv("sceneId"), sceneid),
      _rdf_literal(sceneuri, _cv("sceneOrder"), index.toString, Some(_xsd_namespace + "integer")),
      _rdf_literal(sceneuri, _schema("duration"), scene.durationSeconds.toString, Some(_xsd_namespace + "double")),
      _rdf_uri(sceneuri, _cv("hasUtterance"), utteranceuri),
      _rdf_type(utteranceuri, "VideoUtterance"),
      _rdf_literal(utteranceuri, Vocabulary.Rdfs.label, sceneid)
    ) ++ scene.speaker.map(x => _rdf_literal(utteranceuri, _cv("speaker"), x)).toVector ++
      text.map(x => _rdf_literal(utteranceuri, _schema("text"), x)).toVector
    val audiotriples = audio.toVector.flatMap { entry =>
      val audioartifact = _video_rdf_resource("artifact", s"audio-${part.id}-${entry.sceneId}")
      val audiofile = part.audioDir.map(_.resolve(entry.file).normalize()).getOrElse(Path.of(entry.file))
      Vector(
        _rdf_literal(utteranceuri, _cv("audioDuration"), entry.audioDuration.toString, Some(_xsd_namespace + "double")),
        _rdf_literal(utteranceuri, _cv("targetDuration"), entry.targetDuration.toString, Some(_xsd_namespace + "double")),
        _rdf_literal(utteranceuri, _cv("leadSilence"), entry.leadSilence.toString, Some(_xsd_namespace + "double")),
        _rdf_literal(utteranceuri, _cv("tailSilence"), entry.tailSilence.toString, Some(_xsd_namespace + "double")),
        _rdf_uri(utteranceuri, _schema("encoding"), audioartifact)
      ) ++ entry.speaker.map(x => _rdf_literal(utteranceuri, _cv("speaker"), x)).toVector ++
        entry.provider.map(x => _rdf_literal(utteranceuri, _cv("narrationProvider"), x)).toVector ++
        entry.executionMode.map(x => _rdf_literal(utteranceuri, _cv("narrationExecutionMode"), x)).toVector ++
        entry.voiceIdentity.map(x => _rdf_literal(utteranceuri, _cv("voiceIdentity"), x)).toVector ++
        entry.voiceId.map(x => _rdf_literal(utteranceuri, _cv("voiceId"), x)).toVector ++
        entry.modelIdentity.map(x => _rdf_literal(utteranceuri, _cv("modelIdentity"), x)).toVector ++
        entry.sampleRate.map(x => _rdf_literal(audioartifact, _cv("sampleRate"), x.toString, Some(_xsd_namespace + "integer"))).toVector ++
        entry.channels.map(x => _rdf_literal(audioartifact, _cv("channels"), x.toString, Some(_xsd_namespace + "integer"))).toVector ++
        entry.bitsPerSample.map(x => _rdf_literal(audioartifact, _cv("bitsPerSample"), x.toString, Some(_xsd_namespace + "integer"))).toVector ++
        _artifact_triples(audioartifact, "audio", audiofile, _rdf_file_status(audiofile), s"part.${part.id}.synthesize")
    }
    basetriples ++ audiotriples
  }

  private def _artifact_link_triples(
    owner: String,
    artifactid: String,
    kind: String,
    path: Path,
    status: String,
    producer: String
  ): Vector[Rdf.Triple] =
    Vector(_rdf_uri(owner, _cv("hasArtifact"), artifactid)) ++ _artifact_triples(artifactid, kind, path, status, producer)

  private def _artifact_triples(
    artifactid: String,
    kind: String,
    path: Path,
    status: String,
    producer: String
  ): Vector[Rdf.Triple] = {
    val executionid = _video_rdf_resource("tool-execution", producer)
    Vector(
      _rdf_type(artifactid, "VideoArtifact"),
      _rdf_literal(artifactid, Vocabulary.Rdfs.label, kind),
      _rdf_literal(artifactid, _cv("artifactKind"), kind),
      _rdf_literal(artifactid, _cv("path"), path.toString),
      _rdf_literal(artifactid, _cv("status"), status),
      _rdf_uri(artifactid, _prov("wasGeneratedBy"), executionid),
      _rdf_type(executionid, "VideoToolExecution"),
      _rdf_literal(executionid, Vocabulary.Rdfs.label, producer),
      _rdf_literal(executionid, _cv("stepName"), producer)
    )
  }

  private def _read_optional_json_manifest(path: Path, label: String): Option[Json] =
    if (Files.isRegularFile(path))
      Some(parser.parse(Files.readString(path, StandardCharsets.UTF_8)).fold(
        e => RAISE.invalidArgumentFault(s"Invalid $label JSON: ${e.getMessage}"),
        identity
      ))
    else
      None

  private def _read_optional_audio_manifest(path: Path, label: String): Option[Vector[VideoAudioManifestEntry]] =
    if (Files.isRegularFile(path))
      Some(parser.decode[Vector[VideoAudioManifestEntry]](Files.readString(path, StandardCharsets.UTF_8)).fold(
        e => RAISE.invalidArgumentFault(s"Invalid $label JSON: ${e.getMessage}"),
        identity
      ))
    else
      None

  private def _json_field_triples(
    subject: String,
    json: Json,
    fields: Vector[String],
    predicate: String => String
  ): Vector[Rdf.Triple] =
    fields.flatMap { name =>
      json.hcursor.downField(name).focus.flatMap(_json_scalar_text).map(value => _rdf_literal(subject, predicate(name), value))
    }

  private def _json_scalar_text(json: Json): Option[String] =
    json.asString.
      orElse(json.asNumber.map(_.toString)).
      orElse(json.asBoolean.map(_.toString))

  private def _rdf_file_status(path: Path): String =
    if (Files.exists(path)) "present" else "missing"

  private def _rdf_type(subject: String, localtype: String): Rdf.Triple =
    _rdf_uri(subject, Vocabulary.Rdf.`type`, _cv(localtype))

  private def _rdf_uri(subject: String, predicate: String, obj: String): Rdf.Triple =
    Rdf.Triple(Rdf.Node.Uri(subject), Rdf.Node.Uri(predicate), Rdf.Node.Uri(obj))

  private def _rdf_literal(subject: String, predicate: String, value: String, datatype: Option[String] = None): Rdf.Triple =
    Rdf.Triple(Rdf.Node.Uri(subject), Rdf.Node.Uri(predicate), Rdf.Node.Literal(value, datatype))

  private def _cv(local: String): String =
    _video_rdf_namespace + local

  private def _schema(local: String): String =
    _schema_namespace + local

  private def _dcterms(local: String): String =
    _dcterms_namespace + local

  private def _prov(local: String): String =
    _prov_namespace + local

  private def _video_rdf_resource(kind: String, id: String): String =
    _video_rdf_namespace + _rdf_segment_id(kind, "rdf resource kind") + "/" + _rdf_segment_id(id, s"rdf $kind")

  private def _project_rdf_slug(plan: VideoPlan): String =
    plan.project.name.getOrElse(_basename(plan.projectFile))

  private def _rdf_segment_id(value: String, label: String): String = {
    val normalized = value.trim
    if (normalized.isEmpty || normalized == "." || normalized == ".." || normalized.indexOf(0.toChar) >= 0)
      RAISE.invalidArgumentFault(s"Invalid $label for RDF resource id: $value")
    val bytes = normalized.getBytes(StandardCharsets.UTF_8)
    val b = new StringBuilder
    bytes.foreach { byte =>
      val c = byte & 0xff
      if (
        (c >= 'A' && c <= 'Z') ||
        (c >= 'a' && c <= 'z') ||
        (c >= '0' && c <= '9') ||
        c == '-' || c == '.' || c == '_' || c == '~'
      ) {
        b.append(c.toChar)
      } else {
        b.append('%')
        b.append(_rdf_hex_digits.charAt((c >> 4) & 0x0f))
        b.append(_rdf_hex_digits.charAt(c & 0x0f))
      }
    }
    b.toString
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
      val props = _write_remotion_workspace(plan, part, script, audio, workdir)
      _run_remotion(plan.projectRoot, plan.execution, part, workdir, runner)
      _write_part_manifest(
        plan,
        part,
        audio,
        "remotion",
        workdir,
        "remotionWorkDir",
        Vector(
          "profile" -> plan.project.profile.map(Json.fromString).getOrElse(Json.Null),
          "visualEffects" -> props.hcursor.downField("visualEffects").focus.getOrElse(Json.arr()),
          "assets" -> props.hcursor.downField("assets").focus.getOrElse(Json.arr()),
          "timing" -> props.hcursor.downField("timing").focus.getOrElse(Json.obj())
        )
      )
      VideoRenderedPart(part.id, part.outputPath, part.manifestPath, workdir)
    }
    VideoRenderResult(plan.projectFile, rendered, plan.execution.toolMode, plan.execution.dockerImage)
  }

  private def _render_simple_java2d(
    config: RenderConfig,
    plan: VideoPlan,
    runner: VideoProcessRunner
  ): VideoRenderResult = {
    val parts = _render_target_parts(config, plan)
    val rendered = parts.map { part =>
      val script = part.script.getOrElse(RAISE.invalidArgumentFault(s"Part is missing a parsed script: ${part.id}"))
      val audiodir = part.audioDir.getOrElse(RAISE.invalidArgumentFault(s"Part has no audio directory: ${part.id}"))
      val audio = _load_simple_java2d_input(part.id, audiodir, part, script)
      val workdir = _simple_java2d_work_dir(plan.projectRoot, part.id)
      val frame = workdir.resolve("frame.png")
      _write_simple_java2d_workspace(plan, part, script, audio, workdir)
      _run_simple_java2d_frame(plan.projectRoot, plan.execution, part, workdir, runner)
      if (!Files.isRegularFile(frame))
        RAISE.invalidArgumentFault(s"simple-java2d frame render did not create frame: $frame")
      _run_simple_java2d_ffmpeg(plan.projectRoot, plan.execution, part, frame, audio.combinedFile, runner)
      if (!Files.isRegularFile(part.outputPath))
        RAISE.invalidArgumentFault(s"simple-java2d ffmpeg encode did not create output: ${part.outputPath}")
      _write_part_manifest(
        plan,
        part,
        audio.audio,
        "simple-java2d",
        workdir,
        "simpleJava2dWorkDir",
        Vector(
          "framePath" -> Json.fromString(frame.toString),
          "audioCombinedPath" -> Json.fromString(audio.combinedFile.toString)
        )
      )
      VideoRenderedPart(part.id, part.outputPath, part.manifestPath, workdir, "simpleJava2dWorkDir")
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

  private def _load_simple_java2d_input(partid: String, audiodir: Path, part: VideoPartPlan, script: VideoScript): VideoSimpleJava2dInput = {
    val audio = _load_audio_input(partid, audiodir, script)
    val scriptpath = part.scriptPath.getOrElse(RAISE.invalidArgumentFault(s"Part has no script path: $partid"))
    val combined = audiodir.resolve(s"${_basename(scriptpath)}.wav").normalize()
    if (!Files.isRegularFile(combined))
      RAISE.invalidArgumentFault(s"Missing combined audio file for part $partid: $combined. Run: cozy video synthesize <script-file> --save <audio-dir>")
    VideoSimpleJava2dInput(audio, combined)
  }

  private def _remotion_work_dir(projectroot: Path, partid: String): Path =
    projectroot.resolve("target/cozy-video/remotion").resolve(_file_segment_id(partid, "part id")).normalize()

  private def _simple_java2d_work_dir(projectroot: Path, partid: String): Path =
    projectroot.resolve("target/cozy-video/simple-java2d").resolve(_file_segment_id(partid, "part id")).normalize()

  private def _write_remotion_workspace(
    plan: VideoPlan,
    part: VideoPartPlan,
    script: VideoScript,
    audio: VideoAudioInput,
    workdir: Path
  ): Json = {
    val srcdir = workdir.resolve("src")
    Files.createDirectories(srcdir)
    Files.writeString(workdir.resolve("package.json"), _remotion_package_json, StandardCharsets.UTF_8)
    Files.writeString(srcdir.resolve("Root.tsx"), _remotion_root_tsx, StandardCharsets.UTF_8)
    Files.writeString(srcdir.resolve("render.mjs"), _remotion_render_mjs, StandardCharsets.UTF_8)
    val assets = _copy_remotion_assets(workdir, plan.assets)
    val propsjson = _remotion_props_json(plan, part, script, audio, assets)
    Files.writeString(workdir.resolve("props.json"), propsjson.spaces2, StandardCharsets.UTF_8)
    Files.writeString(srcdir.resolve("props.ts"), _remotion_props_ts(propsjson), StandardCharsets.UTF_8)
    _copy_remotion_audio(workdir, audio)
    propsjson
  }

  private def _copy_remotion_audio(workdir: Path, audio: VideoAudioInput): Unit = {
    val audiodir = workdir.resolve("public/audio")
    Files.createDirectories(audiodir)
    audio.files.foreach { file =>
      Files.copy(file, audiodir.resolve(file.getFileName), StandardCopyOption.REPLACE_EXISTING)
    }
  }

  private def _copy_remotion_assets(
    workdir: Path,
    assets: Vector[CozyVideoAssets.Resolved]
  ): Vector[(CozyVideoAssets.Resolved, Option[String])] = {
    val assetdir = workdir.resolve("public/assets")
    assets.map { asset =>
      if (Files.isRegularFile(asset.path) && Files.isReadable(asset.path)) {
        Files.createDirectories(assetdir)
        val target = assetdir.resolve(asset.role.key + _asset_extension(asset.path))
        Files.copy(asset.path, target, StandardCopyOption.REPLACE_EXISTING)
        asset -> Some(s"assets/${target.getFileName}")
      } else {
        asset -> None
      }
    }
  }

  private def _asset_extension(path: Path): String = {
    val name = path.getFileName.toString
    val index = name.lastIndexOf('.')
    if (index >= 0 && name.substring(index).matches("\\.[A-Za-z0-9]+"))
      name.substring(index).toLowerCase
    else
      ""
  }

  private def _write_simple_java2d_workspace(
    plan: VideoPlan,
    part: VideoPartPlan,
    script: VideoScript,
    audio: VideoSimpleJava2dInput,
    workdir: Path
  ): Unit = {
    Files.createDirectories(workdir)
    val propsjson = _simple_java2d_props_json(plan, part, script, audio, workdir)
    Files.writeString(workdir.resolve("props.json"), propsjson.spaces2, StandardCharsets.UTF_8)
    Files.writeString(workdir.resolve("render_frame.py"), _simple_java2d_render_frame_py, StandardCharsets.UTF_8)
  }

  private def _run_simple_java2d_frame(
    projectroot: Path,
    execution: VideoExecutionConfig,
    part: VideoPartPlan,
    workdir: Path,
    runner: VideoProcessRunner
  ): Unit = {
    val script = workdir.resolve("render_frame.py")
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
            "python3",
            _docker_path(projectroot, script)
          )
        case VideoToolMode.Host =>
          Vector("python3", script.toString)
        case VideoToolMode.ExternalService =>
          RAISE.invalidArgumentFault("simple-java2d render cannot use external-service tool mode")
      }
    val result = runner.run(args, projectroot)
    if (!result.isSuccess)
      RAISE.invalidArgumentFault(s"simple-java2d frame render failed for part ${part.id}: ${result.stderr.trim}")
  }

  private def _run_simple_java2d_ffmpeg(
    projectroot: Path,
    execution: VideoExecutionConfig,
    part: VideoPartPlan,
    frame: Path,
    audiofile: Path,
    runner: VideoProcessRunner
  ): Unit = {
    Files.createDirectories(part.outputPath.getParent)
    val baseargs = Vector(
      "-y",
      "-loop",
      "1",
      "-i",
      frame.toString,
      "-i",
      audiofile.toString,
      "-c:v",
      "libx264",
      "-tune",
      "stillimage",
      "-c:a",
      "aac",
      "-shortest",
      "-pix_fmt",
      "yuv420p",
      part.outputPath.toString
    )
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
            "ffmpeg"
          ) ++ baseargs.map(x => if (x.startsWith(projectroot.toString)) _docker_path(projectroot, Path.of(x)) else x)
        case VideoToolMode.Host =>
          Vector("ffmpeg") ++ baseargs
        case VideoToolMode.ExternalService =>
          RAISE.invalidArgumentFault("simple-java2d ffmpeg encode cannot use external-service tool mode")
      }
    val result = runner.run(args, projectroot)
    if (!result.isSuccess)
      RAISE.invalidArgumentFault(s"simple-java2d ffmpeg encode failed for part ${part.id}: ${result.stderr.trim}")
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
    if (!Files.isRegularFile(part.outputPath))
      RAISE.invalidArgumentFault(s"Remotion render did not create output for part ${part.id}: ${part.outputPath}")
  }

  private def _write_part_manifest(
    plan: VideoPlan,
    part: VideoPartPlan,
    audio: VideoAudioInput,
    renderer: String,
    workdir: Path,
    workdirfield: String,
    extra: Vector[(String, Json)] = Vector.empty
  ): Unit = {
    Files.createDirectories(part.manifestPath.getParent)
    val fields = Vector(
      "partId" -> Json.fromString(part.id),
      "renderer" -> Json.fromString(renderer),
      "scriptPath" -> Json.fromString(part.scriptPath.map(_.toString).getOrElse("")),
      "audioManifestPath" -> Json.fromString(audio.manifestPath.toString),
      "outputPath" -> Json.fromString(part.outputPath.toString),
      "sceneCount" -> Json.fromInt(part.script.map(_.expandedScenes.size).getOrElse(0)),
      "estimatedDuration" -> Json.fromDoubleOrNull(part.estimatedDuration.getOrElse(0.0)),
      "toolMode" -> Json.fromString(plan.execution.toolMode.label),
      "dockerImage" -> Json.fromString(plan.execution.dockerImage),
      workdirfield -> Json.fromString(workdir.toString)
    ) ++ extra
    val json = Json.obj(fields: _*)
    Files.writeString(part.manifestPath, json.spaces2, StandardCharsets.UTF_8)
  }

  private def _remotion_props_json(
    plan: VideoPlan,
    part: VideoPartPlan,
    script: VideoScript,
    audio: VideoAudioInput,
    assets: Vector[(CozyVideoAssets.Resolved, Option[String])]
  ): Json = {
    val renderer = plan.project.renderer
    val fps = renderer.flatMap(_.fps).filter(_ > 0).getOrElse(30)
    val width = renderer.flatMap(_.width).filter(_ > 0).getOrElse(1280)
    val height = renderer.flatMap(_.height).filter(_ > 0).getOrElse(720)
    val effects = CozyVideoEffects.expand(plan.project.visualEffects)
    val contentframes = math.max(1, audio.entries.map(x => math.max(1, math.round(x.targetDuration * fps).toInt)).sum)
    val sectionframes = if (effects.exists(x => x.role == CozyVideoEffects.Role.SectionStart && x.primitives.nonEmpty)) math.min(contentframes, math.round(1.2 * fps).toInt) else 0
    val summaryframes = if (effects.exists(x => x.role == CozyVideoEffects.Role.Summary && x.primitives.nonEmpty)) math.min(contentframes, math.round(2.4 * fps).toInt) else 0
    val isfinalpart = plan.parts.filter(_.renderable).lastOption.exists(_.id == part.id)
    val holdseconds = effects.find(_.role == CozyVideoEffects.Role.FinalPage).toVector.flatMap(_.primitives).
      find(_.name == "hold").flatMap(_.parameters.find(_._1 == "seconds").map(_._2)).flatMap(x => Try(x.toDouble).toOption).getOrElse(0.0)
    val finalframes = if (isfinalpart) math.max(0, math.round(holdseconds * fps).toInt) else 0
    val totalframes = contentframes + finalframes
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
    val effectsjson = effects.map { expansion =>
      Json.obj(
        "role" -> Json.fromString(expansion.role.key),
        "profile" -> Json.fromString(expansion.profile),
        "primitives" -> Json.fromValues(expansion.primitives.map { primitive =>
          Json.obj(
            "name" -> Json.fromString(primitive.name),
            "parameters" -> Json.obj(primitive.parameters.map { case (key, value) => key -> Json.fromString(value) }: _*)
          )
        })
      )
    }
    val assetsjson = assets.map { case (asset, publicpath) =>
      Json.obj(
        "role" -> Json.fromString(asset.role.key),
        "path" -> publicpath.map(Json.fromString).getOrElse(Json.Null),
        "kind" -> Json.fromString(asset.kind),
        "required" -> Json.fromBoolean(asset.required),
        "license" -> Json.fromString(asset.license),
        "provenance" -> Json.fromString(asset.provenance),
        "status" -> Json.fromString(asset.status)
      )
    }
    Json.obj(
      "partId" -> Json.fromString(part.id),
      "outputPath" -> Json.fromString(_project_relative(plan.projectRoot, part.outputPath)),
      "fps" -> Json.fromInt(fps),
      "width" -> Json.fromInt(width),
      "height" -> Json.fromInt(height),
      "durationSeconds" -> Json.fromDoubleOrNull(totalframes.toDouble / fps),
      "scenes" -> Json.fromValues(scenes),
      "visualEffects" -> Json.fromValues(effectsjson),
      "assets" -> Json.fromValues(assetsjson),
      "timing" -> Json.obj(
        "contentFrames" -> Json.fromInt(contentframes),
        "sectionStartFrames" -> Json.fromInt(sectionframes),
        "summaryStartFrame" -> Json.fromInt(math.max(0, contentframes - summaryframes)),
        "summaryFrames" -> Json.fromInt(summaryframes),
        "finalPageStartFrame" -> Json.fromInt(contentframes),
        "finalPageHoldFrames" -> Json.fromInt(finalframes),
        "totalFrames" -> Json.fromInt(totalframes)
      )
    )
  }

  private def _remotion_props_ts(json: Json): String =
    s"""export const cozyVideoProps = ${json.spaces2};
       |""".stripMargin

  private def _simple_java2d_props_json(plan: VideoPlan, part: VideoPartPlan, script: VideoScript, audio: VideoSimpleJava2dInput, workdir: Path): Json = {
    val firstscene = script.expandedScenes.headOption
    val text = firstscene.flatMap(scene => scene.narration.orElse(scene.line).orElse(scene.caption).orElse(scene.id)).getOrElse(part.id)
    Json.obj(
      "partId" -> Json.fromString(part.id),
      "title" -> Json.fromString(script.title.orElse(plan.project.title).getOrElse(part.id)),
      "text" -> Json.fromString(text),
      "sceneCount" -> Json.fromInt(script.expandedScenes.size),
      "estimatedDuration" -> Json.fromDoubleOrNull(script.estimatedDuration),
      "width" -> Json.fromInt(1280),
      "height" -> Json.fromInt(720),
      "framePath" -> Json.fromString(workdir.resolve("frame.png").toString),
      "audioCombinedPath" -> Json.fromString(audio.combinedFile.toString),
      "outputPath" -> Json.fromString(part.outputPath.toString)
    )
  }

  private val _simple_java2d_render_frame_py: String =
    """import json
      |from pathlib import Path
      |from PIL import Image, ImageDraw, ImageFont
      |
      |work_dir = Path(__file__).resolve().parent
      |props = json.loads((work_dir / "props.json").read_text(encoding="utf-8"))
      |width = int(props.get("width", 1280))
      |height = int(props.get("height", 720))
      |image = Image.new("RGB", (width, height), "#101820")
      |draw = ImageDraw.Draw(image)
      |
      |def font(size):
      |    for path in [
      |        "/usr/share/fonts/opentype/noto/NotoSansCJK-Regular.ttc",
      |        "/usr/share/fonts/opentype/ipafont-gothic/ipag.ttf",
      |        "/System/Library/Fonts/ヒラギノ角ゴシック W3.ttc",
      |    ]:
      |        try:
      |            return ImageFont.truetype(path, size=size)
      |        except Exception:
      |            pass
      |    return ImageFont.load_default()
      |
      |title_font = font(54)
      |body_font = font(42)
      |meta_font = font(24)
      |draw.rectangle((0, 0, width, height), fill="#101820")
      |draw.text((72, 72), str(props.get("title", "")), fill="#f4efe6", font=title_font)
      |draw.text((72, 170), str(props.get("text", "")), fill="#f4efe6", font=body_font)
      |meta = f"part={props.get('partId', '')} scenes={props.get('sceneCount', 0)} duration={float(props.get('estimatedDuration', 0.0)):.2f}s"
      |draw.text((72, height - 88), meta, fill="#c8d0d6", font=meta_font)
      |image.save(str(work_dir / "frame.png"))
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
      |const publicDir = path.join(workDir, 'public');
      |const output = path.resolve(projectRoot, props.outputPath);
      |fs.mkdirSync(path.dirname(output), {recursive: true});
      |execFileSync('remotion', ['render', entry, 'CozyVideo', output, '--overwrite', `--public-dir=${publicDir}`], {
      |  cwd: projectRoot,
      |  stdio: 'inherit',
      |  env: {
      |    ...process.env,
      |    NODE_PATH: process.env.NODE_PATH || '/usr/local/lib/node_modules',
      |    NODE_OPTIONS: [process.env.NODE_OPTIONS, '--dns-result-order=ipv4first'].filter(Boolean).join(' ')
      |  }
      |});
      |""".stripMargin

  private val _remotion_root_tsx: String =
    """import React from 'react';
      |import {AbsoluteFill, Audio, Composition, Img, Sequence, interpolate, registerRoot, spring, staticFile, useCurrentFrame, useVideoConfig} from 'remotion';
      |import {cozyVideoProps} from './props';
      |
      |type Scene = {
      |  id: string;
      |  text: string;
      |  audioPath: string;
      |  duration: number;
      |};
      |
      |type Primitive = {
      |  name: 'flow-line' | 'underline-sweep' | 'summary-layout' | 'fade-rise' | 'spring-pop' | 'end-card' | 'hold';
      |  parameters: Record<string, string>;
      |};
      |
      |type VisualEffect = {
      |  role: 'section-start' | 'summary' | 'final-page';
      |  profile: string;
      |  primitives: Primitive[];
      |};
      |
      |type Asset = {
      |  role: 'section-start' | 'summary' | 'final-page';
      |  path: string | null;
      |  kind: string;
      |  required: boolean;
      |  license: string;
      |  provenance: string;
      |  status: string;
      |};
      |
      |type Timing = {
      |  contentFrames: number;
      |  sectionStartFrames: number;
      |  summaryStartFrame: number;
      |  summaryFrames: number;
      |  finalPageStartFrame: number;
      |  finalPageHoldFrames: number;
      |  totalFrames: number;
      |};
      |
      |type Props = {
      |  partId: string;
      |  fps: number;
      |  width: number;
      |  height: number;
      |  durationSeconds: number;
      |  scenes: Scene[];
      |  visualEffects: VisualEffect[];
      |  assets: Asset[];
      |  timing: Timing;
      |};
      |
      |const roleEffect = (effects: VisualEffect[], role: VisualEffect['role']) => effects.find((effect) => effect.role === role);
      |const roleAsset = (assets: Asset[], role: Asset['role']) => assets.find((asset) => asset.role === role);
      |const primitive = (effect: VisualEffect | undefined, name: Primitive['name']) => effect?.primitives.find((item) => item.name === name);
      |
      |const AssetFrame: React.FC<{asset?: Asset; opacity?: number}> = ({asset, opacity = 1}) =>
      |  asset?.path ? <Img src={staticFile(asset.path)} style={{position: 'absolute', inset: 0, width: '100%', height: '100%', objectFit: 'cover', opacity}} /> : null;
      |
      |const SceneCard: React.FC<{scene: Scene}> = ({scene}) => (
      |  <AbsoluteFill style={{backgroundColor: '#101820', color: '#f4efe6', fontFamily: 'Noto Sans CJK JP, sans-serif', alignItems: 'center', justifyContent: 'center', padding: 80}}>
      |    <div style={{fontSize: 56, lineHeight: 1.25, textAlign: 'center'}}>{scene.text || scene.id}</div>
      |    <div style={{position: 'absolute', bottom: 48, right: 64, fontSize: 24, opacity: 0.7}}>{scene.id}</div>
      |    <Audio src={staticFile(scene.audioPath)} />
      |  </AbsoluteFill>
      |);
      |
      |const SectionStart: React.FC<{effect: VisualEffect; asset?: Asset; durationInFrames: number}> = ({effect, asset, durationInFrames}) => {
      |  const frame = useCurrentFrame();
      |  const flow = primitive(effect, 'flow-line');
      |  const underline = primitive(effect, 'underline-sweep');
      |  const progress = interpolate(frame, [0, Math.max(1, durationInFrames - 1)], [0, 1], {extrapolateLeft: 'clamp', extrapolateRight: 'clamp'});
      |  const direction = flow?.parameters.direction === 'right-to-left' ? -1 : 1;
      |  return (
      |    <AbsoluteFill style={{backgroundColor: '#edf4f1', overflow: 'hidden'}}>
      |      <AssetFrame asset={asset} opacity={0.34} />
      |      {flow ? <div style={{position: 'absolute', top: '45%', left: direction > 0 ? 0 : undefined, right: direction < 0 ? 0 : undefined, width: `${Math.round(progress * 100)}%`, height: 14, background: '#2f6f68'}} /> : null}
      |      {underline ? <div style={{position: 'absolute', left: '18%', bottom: '29%', width: `${Math.round(progress * 64)}%`, height: 7, background: '#d89b45'}} /> : null}
      |    </AbsoluteFill>
      |  );
      |};
      |
      |const Summary: React.FC<{effect: VisualEffect; asset?: Asset}> = ({effect, asset}) => {
      |  const frame = useCurrentFrame();
      |  const {fps} = useVideoConfig();
      |  const layout = primitive(effect, 'summary-layout');
      |  const fade = primitive(effect, 'fade-rise');
      |  const pop = primitive(effect, 'spring-pop');
      |  const rise = fade?.parameters.target === 'overview' ? interpolate(frame, [0, fps * 0.5], [36, 0], {extrapolateRight: 'clamp'}) : 0;
      |  const scale = pop?.parameters.target === 'conclusion' ? spring({frame, fps, config: {damping: 14, stiffness: 120}}) : 1;
      |  return (
      |    <AbsoluteFill style={{backgroundColor: '#e7f1ee', color: '#173f3b', fontFamily: 'Noto Sans CJK JP, sans-serif', padding: 72}}>
      |      <AssetFrame asset={asset} opacity={0.22} />
      |      {layout?.parameters.mode === 'single-page' ? <div style={{fontSize: 30, letterSpacing: 3, transform: `translateY(${rise}px)`, opacity: interpolate(frame, [0, fps * 0.4], [0, 1], {extrapolateRight: 'clamp'})}}>OVERVIEW</div> : null}
      |      {pop ? <div style={{marginTop: 'auto', fontSize: 68, fontWeight: 800, transform: `scale(${scale})`, transformOrigin: 'left bottom'}}>CONCLUSION</div> : null}
      |    </AbsoluteFill>
      |  );
      |};
      |
      |const FinalPage: React.FC<{effect: VisualEffect; asset?: Asset}> = ({effect, asset}) => {
      |  const frame = useCurrentFrame();
      |  const {fps} = useVideoConfig();
      |  const card = primitive(effect, 'end-card');
      |  const fade = primitive(effect, 'fade-rise');
      |  const hold = primitive(effect, 'hold');
      |  const opacity = fade?.parameters.target === 'end-card' ? interpolate(frame, [0, fps * 0.45], [0, 1], {extrapolateRight: 'clamp'}) : 1;
      |  return card && hold ? (
      |    <AbsoluteFill style={{backgroundColor: '#101820', color: '#f4efe6', alignItems: 'center', justifyContent: 'center', opacity}}>
      |      <AssetFrame asset={asset} opacity={0.42} />
      |      <div style={{fontSize: 70, fontWeight: 800, zIndex: 1}}>END</div>
      |    </AbsoluteFill>
      |  ) : null;
      |};
      |
      |export const CozyVideo: React.FC<Props> = ({scenes, fps, visualEffects, assets, timing}) => {
      |  let start = 0;
      |  const section = roleEffect(visualEffects, 'section-start');
      |  const summary = roleEffect(visualEffects, 'summary');
      |  const finalPage = roleEffect(visualEffects, 'final-page');
      |  return (
      |    <AbsoluteFill>
      |      {scenes.map((scene) => {
      |        const duration = Math.max(1, Math.round((scene.duration || 1) * fps));
      |        const sequence = <Sequence key={scene.id} from={start} durationInFrames={duration}><SceneCard scene={scene} /></Sequence>;
      |        start += duration;
      |        return sequence;
      |      })}
      |      {section && timing.sectionStartFrames > 0 ? <Sequence from={0} durationInFrames={timing.sectionStartFrames}><SectionStart effect={section} asset={roleAsset(assets, 'section-start')} durationInFrames={timing.sectionStartFrames} /></Sequence> : null}
      |      {summary && timing.summaryFrames > 0 ? <Sequence from={timing.summaryStartFrame} durationInFrames={timing.summaryFrames}><Summary effect={summary} asset={roleAsset(assets, 'summary')} /></Sequence> : null}
      |      {finalPage && timing.finalPageHoldFrames > 0 ? <Sequence from={timing.finalPageStartFrame} durationInFrames={timing.finalPageHoldFrames}><FinalPage effect={finalPage} asset={roleAsset(assets, 'final-page')} /></Sequence> : null}
      |    </AbsoluteFill>
      |  );
      |};
      |
      |export const RemotionRoot: React.FC = () => (
      |  <Composition
      |    id="CozyVideo"
      |    component={CozyVideo}
      |    durationInFrames={Math.max(1, cozyVideoProps.timing?.totalFrames || Math.round((cozyVideoProps.durationSeconds || 1) * cozyVideoProps.fps))}
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
    val assets = CozyVideoAssets.resolve(projectroot, project.assets)
    val execution = VideoExecutionConfig.create(projectroot, project, toolmode, dockerimage)
    val outputpath = projectroot.resolve(project.output.getOrElse("build/final.mp4")).normalize()
    val manifestpath = outputpath.getParent.resolve("manifest.json").normalize()
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
    VideoPlan(projectfile, projectroot, project, assets, execution, outputpath, manifestpath, parts, artifacts, commands)
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
    val narrationproviders = _plan_narration_providers(plan).toVector.sorted
    if (narrationproviders.nonEmpty)
      b += s"narrationProviders: ${narrationproviders.mkString(", ")}"
    project.profile.foreach(x => b += s"profile: $x")
    val effects = CozyVideoEffects.expand(project.visualEffects)
    if (effects.nonEmpty) {
      b += "visualEffects:"
      effects.foreach(effect => b += s"  - ${effect.role.key}: ${effect.profile} => ${effect.display}")
      val renderer = project.renderer.map(_.engineOrDefault).getOrElse("legacy")
      val capability = CozyVideoEffects.capability(renderer, effects)
      b += s"visualEffectRenderer: $renderer"
      b += s"visualEffectCapability: ${capability.status}"
      if (capability.unsupported.nonEmpty)
        b += s"unsupportedVisualEffectPrimitives: ${capability.unsupported.mkString(", ")}"
    }
    if (plan.assets.nonEmpty) {
      b += "assets:"
      plan.assets.foreach { asset =>
        b += s"  - ${asset.role.key}: ${asset.status} path=${asset.displayPath(plan.projectRoot)} kind=${asset.kind} required=${asset.required}"
        b += s"    license: ${asset.license}"
        b += s"    provenance: ${asset.provenance}"
        asset.requestedDisplayPath(plan.projectRoot).foreach(x => b += s"    requestedPath: $x")
      }
    }
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

  private def _render_build_result(result: VideoBuildResult): String = {
    val b = Vector.newBuilder[String]
    b += "Cozy Video Build"
    b += s"projectFile: ${result.projectFile}"
    b += s"toolMode: ${result.toolMode.label}"
    b += s"dockerImage: ${result.dockerImage}"
    b += s"output: ${result.outputPath}"
    b += s"manifest: ${result.manifestPath}"
    b += s"concatList: ${result.concatListPath}"
    b += s"parts: ${result.partOutputs.size}"
    result.partOutputs.foreach { path =>
      b += s"  - partOutput: $path"
    }
    b += s"ffprobe: ${result.ffprobeSummary.noSpaces}"
    b.result().mkString("\n") + "\n"
  }

  private def _render_synthesis_result(result: VideoSynthesisResult): String = {
    val b = Vector.newBuilder[String]
    b += "Cozy Video Synthesize"
    b += s"scriptFile: ${result.scriptFile}"
    b += s"outputDir: ${result.outputDir}"
    b += s"provider: ${result.provider}"
    b += s"executionMode: ${result.executionMode}"
    b += s"toolMode: ${result.toolMode.label}"
    b += s"dockerImage: ${result.dockerImage}"
    result.diagnostics.foreach(x => b += s"warning: $x")
    if (result.toolChecks.nonEmpty)
      b ++= _render_tool_checks(result.toolChecks)
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
      b += s"    ${part.workDirLabel}: ${part.workDir}"
    }
    b.result().mkString("\n") + "\n"
  }

  private def _render_transcription_result(result: VideoTranscriptionResult): String = {
    val b = Vector.newBuilder[String]
    b += "Cozy Video Transcribe"
    b += s"inputVideo: ${result.inputVideo}"
    b += s"outputDir: ${result.saveDir}"
    b += s"toolMode: ${result.toolMode.label}"
    b += s"dockerImage: ${result.dockerImage}"
    b += s"modelPath: ${result.modelPath}"
    b += s"audio: ${result.audioPath}"
    b += s"transcript: ${result.transcriptPath}"
    b += s"captions: ${result.captionsPath}"
    b += s"narration: ${result.narrationPath}"
    b += s"manifest: ${result.manifestPath}"
    b += s"segments: ${result.segmentCount}"
    b.result().mkString("\n") + "\n"
  }

  private def _render_demo_script_result(result: VideoDemoScriptResult): String = {
    val b = Vector.newBuilder[String]
    b += "Cozy Video Demo Script"
    b += s"inputVideo: ${result.inputVideo}"
    b += s"scriptFile: ${result.scriptFile}"
    b += s"manualReview: ${result.manualReview}"
    b += s"steps: ${result.steps.size}"
    result.steps.foreach { step =>
      b += s"  - ${step.kind}${step.selector.map(x => s" selector=$x").getOrElse("")}${step.url.map(x => s" url=$x").getOrElse("")}"
    }
    b.result().mkString("\n") + "\n"
  }

  private def _render_replay_result(result: VideoReplayResult): String = {
    val b = Vector.newBuilder[String]
    b += (if (result.dryRun) "Cozy Video Replay Dry-Run" else "Cozy Video Replay")
    b += s"scriptFile: ${result.scriptFile}"
    b += s"toolMode: ${result.toolMode.label}"
    b += s"dockerImage: ${result.dockerImage}"
    result.outputVideo.foreach(x => b += s"outputVideo: $x")
    b += s"manifest: ${result.manifestPath}"
    b += "commands:"
    result.commands.foreach { command =>
      b += s"  - ${command.stepName}: ${command.toolName} (${command.mode.label}) - ${command.preview}"
      if (command.inputs.nonEmpty)
        b += s"    inputs: ${command.inputs.mkString(", ")}"
      if (command.outputs.nonEmpty)
        b += s"    outputs: ${command.outputs.mkString(", ")}"
    }
    b.result().mkString("\n") + "\n"
  }

  private def _render_rdf_result(result: VideoRdfResult): String = {
    val b = Vector.newBuilder[String]
    b += "Cozy Video RDF"
    b += s"projectFile: ${result.projectFile}"
    b += s"outputDir: ${result.outputDir}"
    b += s"turtle: ${result.turtleFile}"
    b += s"jsonld: ${result.jsonLdFile}"
    b += s"manifest: ${result.manifestFile}"
    b += s"triples: ${result.tripleCount}"
    b += s"resources: ${result.resourceCount}"
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
          z += s"  narrationProvider: ${_resolve_narration_selection(videoscript).provider}"
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
    b += "tool checks:"
    checks.foreach { check =>
      b += s"  - ${check.name}: ${check.status.label} (${check.mode.label}) - ${check.message}"
      check.setupHint.foreach(x => b += s"    setup: $x")
    }
    b.result()
  }
}
