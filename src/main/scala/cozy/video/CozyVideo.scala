package cozy.video

import org.goldenport.RAISE
import org.goldenport.config.StructuredDocumentLoader
import org.goldenport.context.{FaultException, NetworkIoFault, SubsystemIoFault}
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
 * @since   Jun. 18, 2026
 *  version Jun. 19, 2026
 *  version Jul. 20, 2026
 * @version Aug. 11, 2026
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
    assets: Option[CozyVideoAssets.Settings] = None,
    locale: Option[String] = None,
    credits: Option[CozyVideoCredits.Settings] = None
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
        locale <- c.downField("locale").as[Option[String]]
        credits <- c.downField("credits").as[Option[CozyVideoCredits.Settings]]
      } yield VideoProject(name, title, output, renderer, tools, parts.getOrElse(Vector.empty), profile, visualeffects, assets, locale, credits)
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
    silent: Option[Boolean] = None,
    visual: Json = Json.obj(),
    section: Option[String] = None,
    effects: Json = Json.obj()
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
            silent = subscene.silent.orElse(silent),
            visual = if (subscene.visual.asObject.exists(_.nonEmpty)) subscene.visual else visual,
            section = subscene.section.orElse(section),
            effects = if (subscene.effects.asObject.exists(_.nonEmpty)) subscene.effects else effects
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
        visual <- c.downField("visual").as[Option[Json]]
        section <- c.downField("section").as[Option[String]]
        effects <- c.downField("effects").as[Option[Json]]
      } yield VideoScene(id, speaker, line, narration, caption, duration, targetduration, leadsilence, subscenes.getOrElse(Vector.empty), silent, visual.getOrElse(Json.obj()), section, effects.getOrElse(Json.obj()))
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

  private final case class DockerMount(
    hostpath: Path,
    containerpath: String,
    readonly: Boolean
  )

  private final case class ReviewEvidenceDockerMounts(
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
      _with_external_service_failure(operation, "voicevox", baseurl)(body)

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

  final case class VideoReviewEvidenceResult(
    projectFile: Path,
    saveDir: Path,
    finalVideo: Path,
    manifestPath: Path,
    partCount: Int,
    sceneCount: Int,
    frameCount: Int,
    toolMode: VideoToolMode,
    dockerImage: String
  )

  private final case class ReviewEvidencePart(
    part: VideoPartPlan,
    propspath: Path,
    props: Json,
    audiomanifestpath: Option[Path],
    audioentries: Map[String, VideoAudioManifestEntry],
    startframe: Int,
    totalframes: Int,
    fps: Int,
    width: Int,
    height: Int
  )

  private final case class ReviewEvidenceFrame(
    kind: String,
    partid: Option[String],
    sceneid: Option[String],
    absoluteframe: Int,
    fps: Int,
    path: Path
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
    credits: CozyVideoCredits.EffectiveSet,
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
  private val _voicevox_connection_recovery =
    "Start VOICEVOX Engine or set tools.voicevoxUrl / video.voicevox.url. In Docker mode, use host.docker.internal or a compose service URL when needed."
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
      case "video" :: "review-evidence" :: rest =>
        println(reviewEvidence(ReviewEvidenceConfig.create(rest), tools, runner))
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
    val checks =
      if (config.checkTools || (!config.dryRun && plan.execution.toolMode == VideoToolMode.Docker))
        tools.checks(context)
      else
        Vector.empty
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
    val checks =
      if (config.checkTools || plan.execution.toolMode == VideoToolMode.Docker)
        tools.checks(context)
      else
        Vector.empty
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

  def reviewEvidence(config: ReviewEvidenceConfig, tools: VideoToolRegistry): String =
    reviewEvidence(config, tools, VideoProcessRunner.default)

  def reviewEvidence(
    config: ReviewEvidenceConfig,
    tools: VideoToolRegistry,
    runner: VideoProcessRunner
  ): String = {
    val plan = _plan(config.projectFile, config.toolMode, config.dockerImage)
    val context = VideoToolContext(config.projectFile, config.projectRoot, plan.project, plan.execution)
    val checks = if (config.checkTools) tools.checks(context) else Vector.empty
    _validate_review_evidence_tools(plan.execution, checks)
    _render_review_evidence_result(_write_review_evidence(config, plan, runner))
  }

  def verifyCredits(projectfile: Path): Vector[String] = {
    val plan = _plan(projectfile, None, None)
    val diagnostics = plan.credits.errors.map(x => s"${x.code}: ${x.message}")
    val artifactfindings =
      if (plan.credits.profile.isEmpty || !Files.isRegularFile(plan.outputPath))
        Vector.empty
      else {
        val directory = plan.outputPath.getParent.resolve("credits")
        val jsonfile = directory.resolve("credits.json")
        val markdownfile = directory.resolve("credits.md")
        val rendererpropsfile = directory.resolve("renderer-props.json")
        val missing = Vector(jsonfile, markdownfile, rendererpropsfile).filterNot(Files.isRegularFile(_)).map(x => s"missing credit projection: $x") ++
          (if (Files.isRegularFile(plan.manifestPath)) Vector.empty else Vector(s"missing project manifest with credit digest: ${plan.manifestPath}"))
        val digestfindings =
          if (!Files.isRegularFile(jsonfile))
            Vector.empty
          else {
            val actual = parser.parse(Files.readString(jsonfile, StandardCharsets.UTF_8)).toOption.
              flatMap(_.hcursor.get[String]("digest").toOption)
            if (actual.contains(plan.credits.digest)) Vector.empty
            else Vector(s"credit digest mismatch: expected ${plan.credits.digest}, found ${actual.getOrElse("missing")}")
          }
        val projectionfindings = Vector(
          _credit_json_projection_finding(jsonfile, CozyVideoCredits.toJson(plan.credits), "credit JSON"),
          _credit_text_projection_finding(markdownfile, CozyVideoCredits.toMarkdown(plan.credits), "credit Markdown"),
          _credit_json_projection_finding(rendererpropsfile, CozyVideoCredits.toRendererProps(plan.credits), "credit renderer props")
        ).flatten
        val manifestfindings =
          if (!Files.isRegularFile(plan.manifestPath))
            Vector.empty
          else {
            val manifest = parser.parse(Files.readString(plan.manifestPath, StandardCharsets.UTF_8)).toOption
            val digest = manifest.flatMap(_.hcursor.get[String]("creditDigest").toOption)
            val profile = manifest.flatMap(_.hcursor.get[String]("creditProfile").toOption)
            Vector(
              if (digest.contains(plan.credits.digest)) None else Some(s"project manifest credit digest mismatch: expected ${plan.credits.digest}, found ${digest.getOrElse("missing")}"),
              if (profile == plan.credits.profileId) None else Some(s"project manifest credit profile mismatch: expected ${plan.credits.profileId.getOrElse("none")}, found ${profile.getOrElse("missing")}")
            ).flatten
          }
        missing ++ digestfindings ++ projectionfindings ++ manifestfindings
      }
    diagnostics ++ artifactfindings
  }

  private def _credit_json_projection_finding(path: Path, expected: Json, label: String): Option[String] =
    if (!Files.isRegularFile(path))
      None
    else {
      val actual = parser.parse(Files.readString(path, StandardCharsets.UTF_8)).toOption
      if (actual.contains(expected)) None else Some(s"$label does not match the effective credit set: $path")
    }

  private def _credit_text_projection_finding(path: Path, expected: String, label: String): Option[String] =
    if (!Files.isRegularFile(path))
      None
    else if (Files.readString(path, StandardCharsets.UTF_8) == expected)
      None
    else
      Some(s"$label does not match the effective credit set: $path")

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
    dockerImage: String,
    creditWarnings: Vector[CozyVideoCredits.Diagnostic]
  )

  final case class VideoBuildResult(
    projectFile: Path,
    outputPath: Path,
    manifestPath: Path,
    concatListPath: Path,
    partOutputs: Vector[Path],
    toolMode: VideoToolMode,
    dockerImage: String,
    ffprobeSummary: Json,
    creditProfile: Option[String],
    creditFiles: Option[CozyVideoCredits.OutputFiles],
    creditWarnings: Vector[CozyVideoCredits.Diagnostic]
  )

  final case class VideoRdfResult(
    projectFile: Path,
    outputDir: Path,
    turtleFile: Path,
    jsonLdFile: Path,
    manifestFile: Path,
    tripleCount: Int,
    resourceCount: Int,
    creditProfile: Option[String],
    creditDigest: Option[String]
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
      val audioquery = _apply_voice_tuning(
        _with_external_service_failure("audio_query", "voicevox", baseUrl) {
          client.audioQuery(baseUrl, text, speakerid)
        },
        voice
      )
      NarrationAudio(
        _with_external_service_failure("synthesis", "voicevox", baseUrl) {
          client.synthesis(baseUrl, speakerid, audioquery)
        },
        Some(_voicevox_voice_identity(voice, Some(speakerid))),
        Some(speakerid.toString),
        None
      )
    }
  }

  private final class MacosSayNarrationProvider(
    projectroot: Path,
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
          projectroot
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
          projectroot
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
        _delete_tree(workdir)
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
        _delete_tree(workdir)
      }
    }
  }

  private def _delete_tree(path: Path): Unit =
    if (Files.exists(path)) {
      val paths = Files.walk(path)
      try paths.iterator().asScala.toVector.reverse.foreach(Files.deleteIfExists)
      finally paths.close()
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
        val (manifestprovider, manifestexecutionmode, voiceidentity, voiceid, modelidentity) =
          if (_is_silent_scene(scene)) {
            _write_silence_wav(scenewav, 0.01)
            (None, None, None, None, None)
          } else {
            val voice = _voice_for_scene(script, scene)
            val text = _spoken_text(script, scene)
            val audio = provider.synthesize(text, voice)
            _write_wav(scenewav, _normalize_provider_wav(audio.wav, s"${provider.id}:$sceneid"))
            (
              Some(provider.id),
              Some(provider.executionMode),
              audio.voiceIdentity,
              audio.voiceId,
              audio.modelIdentity
            )
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
          manifestprovider,
          manifestexecutionmode,
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

  private def _voicevox_voice_identity(voice: Json, resolvedid: Option[Int] = None): String = {
    val speakername = _json_string(voice, "speakerName").map(_.trim).filter(_.nonEmpty)
    val stylename = _json_string(voice, "styleName").map(_.trim).filter(_.nonEmpty)
    (speakername, stylename) match {
      case (Some(speaker), Some(style)) => s"$speaker/$style"
      case (None, None) =>
        resolvedid.orElse(_json_int(voice, "fallbackSpeakerId")).map(id => s"speaker-id:$id").getOrElse(
          RAISE.invalidArgumentFault("VOICEVOX requires explicit speakerName + styleName or fallbackSpeakerId")
        )
      case _ =>
        RAISE.invalidArgumentFault("VOICEVOX requires explicit speakerName + styleName or fallbackSpeakerId")
    }
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
    val fallback = _json_int(voice, "fallbackSpeakerId")
    val speakername = _json_string(voice, "speakerName").map(_.trim).filter(_.nonEmpty)
    val stylename = _json_string(voice, "styleName").map(_.trim).filter(_.nonEmpty)
    (speakername, stylename) match {
      case (None, None) => fallback.getOrElse(
        RAISE.invalidArgumentFault("VOICEVOX requires explicit speakerName + styleName or fallbackSpeakerId")
      )
      case (Some(_), None) | (None, Some(_)) =>
        RAISE.invalidArgumentFault("VOICEVOX requires explicit speakerName + styleName or fallbackSpeakerId")
      case (Some(speaker), Some(style)) =>
        val speakers = _with_external_service_failure("speakers", "voicevox", baseurl) {
          voicevox.speakers(baseurl)
        }
        val matchid = speakers.asArray.toVector.flatten.flatMap { item =>
          if (_json_string(item, "name").contains(speaker))
            _json_array(item, "styles").toVector.flatten.find(x => _json_string(x, "name").contains(style)).flatMap(_json_int(_, "id"))
          else None
        }.headOption
        matchid.orElse(fallback).getOrElse(
          RAISE.invalidArgumentFault(s"VOICEVOX speaker style not found: $speaker/$style; configure fallbackSpeakerId to permit fallback")
        )
    }
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
      checks.filter(x => required.contains(x.name) && _is_required_dependency_unavailable(x)).headOption.foreach { check =>
        _raise_required_dependency_unavailable(s"render with $renderer", check)
      }
    }

  private def _validate_synthesis_tools(provider: String, checks: Vector[VideoToolCheck]): Unit =
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

  private def _validate_build_tools(execution: VideoExecutionConfig, checks: Vector[VideoToolCheck]): Unit =
    if (checks.nonEmpty) {
      val required =
        execution.toolMode match {
          case VideoToolMode.Docker => Set("docker-toolchain", "docker-image", "textus-toolchain-image")
          case VideoToolMode.Host => Set("ffmpeg")
          case VideoToolMode.ExternalService => Set.empty[String]
        }
      checks.filter(x => required.contains(x.name) && _is_required_dependency_unavailable(x)).headOption.foreach { check =>
        _raise_required_dependency_unavailable("build video", check)
      }
    }

  private def _validate_review_evidence_tools(execution: VideoExecutionConfig, checks: Vector[VideoToolCheck]): Unit =
    if (checks.nonEmpty) {
      val required =
        execution.toolMode match {
          case VideoToolMode.Docker => Set("docker-toolchain", "docker-image", "textus-toolchain-image")
          case VideoToolMode.Host => Set("ffmpeg")
          case VideoToolMode.ExternalService => Set.empty[String]
        }
      checks.filter(x => required.contains(x.name) && _is_required_dependency_unavailable(x)).headOption.foreach { check =>
        _raise_required_dependency_unavailable("extract video review evidence", check)
      }
    }

  private def _is_required_dependency_unavailable(check: VideoToolCheck): Boolean =
    check.status == VideoToolStatus.Missing || check.status == VideoToolStatus.Unchecked

  private def _with_external_service_failure[A](
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

  private def _raise_external_service_connection_unavailable(
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

  private def _raise_required_dependency_unavailable(
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

  private def _write_review_evidence(
    config: ReviewEvidenceConfig,
    plan: VideoPlan,
    runner: VideoProcessRunner
  ): VideoReviewEvidenceResult = {
    val finalvideo = plan.outputPath.toAbsolutePath.normalize()
    if (Files.isSymbolicLink(finalvideo))
      RAISE.invalidArgumentFault(s"Final video for review evidence must not be a symbolic link: $finalvideo")
    if (!Files.isRegularFile(finalvideo, LinkOption.NOFOLLOW_LINKS))
      RAISE.invalidArgumentFault(s"Missing final video for review evidence: $finalvideo")
    val finalhash = _sha256(finalvideo)
    val savedir = config.saveDir.toAbsolutePath.normalize()
    if (savedir.getParent == null)
      RAISE.invalidArgumentFault("Review evidence --save target must not be the filesystem root.")
    if (Files.exists(savedir, LinkOption.NOFOLLOW_LINKS) && Files.isSymbolicLink(savedir))
      RAISE.invalidArgumentFault(s"Review evidence --save target must not be a symbolic link: $savedir")
    if (Files.exists(savedir, LinkOption.NOFOLLOW_LINKS) && !Files.isDirectory(savedir, LinkOption.NOFOLLOW_LINKS))
      RAISE.invalidArgumentFault(s"Review evidence --save target is not a directory: $savedir")
    Files.createDirectories(savedir)
    if (!Files.isDirectory(savedir, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(savedir))
      RAISE.invalidArgumentFault(s"Review evidence --save target is not a directory: $savedir")
    val canonicalfinalvideo = finalvideo.toRealPath()
    val canonicalsavedir = savedir.toRealPath()
    if (canonicalfinalvideo.startsWith(canonicalsavedir))
      RAISE.invalidArgumentFault(s"Review evidence final video must not be inside the --save directory: $canonicalfinalvideo")
    val framesdir = savedir.resolve("frames")
    if (Files.exists(framesdir, LinkOption.NOFOLLOW_LINKS) && Files.isSymbolicLink(framesdir))
      RAISE.invalidArgumentFault(s"Review evidence frames target must not be a symbolic link: $framesdir")
    Files.createDirectories(framesdir)
    if (!Files.isDirectory(framesdir, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(framesdir))
      RAISE.invalidArgumentFault(s"Review evidence frames target is not a directory: $framesdir")
    val manifest = savedir.resolve("review-manifest.json")
    if (Files.exists(manifest, LinkOption.NOFOLLOW_LINKS) && Files.isSymbolicLink(manifest))
      RAISE.invalidArgumentFault(s"Review evidence manifest target must not be a symbolic link: $manifest")
    val videomanifest = _review_validated_video_manifest(plan, finalvideo, finalhash)
    val videomanifesthash = _sha256(videomanifest)
    val dockermounts =
      if (plan.execution.toolMode == VideoToolMode.Docker)
        Some(ReviewEvidenceDockerMounts(
          DockerMount(canonicalfinalvideo, "/review-input/final.mp4", readonly = true),
          DockerMount(canonicalsavedir, "/review-output", readonly = false)
        ))
      else
        None
    val parts = _review_evidence_parts(plan)
    if (parts.isEmpty)
      RAISE.invalidArgumentFault("No renderable Cozy Remotion props found for video review evidence.")
    val renderer = (parts.head.width, parts.head.height, parts.head.fps)
    if (parts.exists(x => (x.width, x.height, x.fps) != renderer))
      RAISE.invalidArgumentFault("Cozy Remotion props disagree on renderer width, height, or fps.")
    val topframes = Vector.newBuilder[Json]
    val partjson = parts.map { evidence =>
      val timing = _review_required_object(evidence.props, "timing", s"Remotion props for ${evidence.part.id}")
      def _timing_int_(name: String): Int = _review_nonnegative_int(timing, name, s"Remotion timing for ${evidence.part.id}")
      val opening = _timing_int_("openingFrames")
      val summary = _timing_int_("summaryFrames")
      val summarystart = _timing_int_("summaryStartFrame")
      val finalpage = _timing_int_("finalPageHoldFrames")
      val finalpagestart = _timing_int_("finalPageStartFrame")
      val scenes = _review_required_array(evidence.props, "scenes", s"Remotion props for ${evidence.part.id}")
      scenes.zipWithIndex.foreach { case (scene, sceneindex) =>
        val sceneid = _review_required_string(scene, "id", s"Remotion scene ${sceneindex + 1} for ${evidence.part.id}")
        val start = _review_nonnegative_int(scene, "startFrame", s"Remotion scene $sceneid")
        val duration = _review_positive_int(scene, "durationFrames", s"Remotion scene $sceneid")
        if (opening.toLong + start.toLong + duration.toLong > evidence.totalframes.toLong)
          RAISE.invalidArgumentFault(s"Remotion scene $sceneid exceeds declared totalFrames.")
      }
      Vector(
        _review_optional_top_frame("opening", evidence, savedir, 0, opening),
        _review_optional_top_frame("summary", evidence, savedir, summarystart, summary),
        _review_optional_top_frame("final-page", evidence, savedir, finalpagestart, finalpage)
      ).flatten.foreach { frame =>
        topframes += _extract_review_frame(plan, finalvideo, savedir, dockermounts, frame, runner)
      }
      val scenejson = scenes.zipWithIndex.map { case (scene, sceneindex) =>
        val sceneid = _review_required_string(scene, "id", s"Remotion scene ${sceneindex + 1} for ${evidence.part.id}")
        val start = _review_nonnegative_int(scene, "startFrame", s"Remotion scene $sceneid")
        val duration = _review_positive_int(scene, "durationFrames", s"Remotion scene $sceneid")
        val leadin = _review_nonnegative_int(scene, "leadInFrames", s"Remotion scene $sceneid")
        val transition = _review_nonnegative_int(scene, "sectionTransitionFrames", s"Remotion scene $sceneid")
        if (leadin >= duration)
          RAISE.invalidArgumentFault(s"Remotion scene $sceneid leadInFrames must be inside durationFrames.")
        val absolute = evidence.startframe + opening + start
        val speech = ReviewEvidenceFrame(
          "speech",
          Some(evidence.part.id),
          Some(sceneid),
          absolute + math.min(duration - 1, leadin),
          evidence.fps,
          savedir.resolve("frames").resolve(_review_scene_frame_name(evidence.part.index, sceneindex, sceneid, "speech"))
        )
        val frames =
          (if (transition > 0) Vector(ReviewEvidenceFrame(
            "transition",
            Some(evidence.part.id),
            Some(sceneid),
            absolute + math.min(duration - 1, math.max(0, transition / 2)),
            evidence.fps,
            savedir.resolve("frames").resolve(_review_scene_frame_name(evidence.part.index, sceneindex, sceneid, "transition"))
          )) else Vector.empty) :+ speech
        val audio = evidence.audioentries.get(sceneid)
        val authored = audio.map { entry =>
          Json.obj(
            "leadSilenceSeconds" -> Json.fromDoubleOrNull(_round3(entry.leadSilence)),
            "audioDurationSeconds" -> Json.fromDoubleOrNull(_round3(entry.audioDuration)),
            "targetDurationSeconds" -> Json.fromDoubleOrNull(_round3(entry.targetDuration)),
            "tailSilenceSeconds" -> Json.fromDoubleOrNull(_round3(entry.tailSilence))
          )
        }.getOrElse(Json.Null)
        Json.obj(
          "index" -> Json.fromInt(sceneindex),
          "order" -> Json.fromInt(sceneindex + 1),
          "partId" -> Json.fromString(evidence.part.id),
          "id" -> Json.fromString(sceneid),
          "speaker" -> _review_optional_string(scene, "speaker"),
          "line" -> _review_optional_string(scene, "line"),
          "text" -> _review_optional_string(scene, "text"),
          "caption" -> _review_optional_string(scene, "caption"),
          "section" -> _review_optional_string(scene, "section"),
          "authoredTiming" -> authored,
          "effectiveTiming" -> Json.obj(
            "durationFrames" -> Json.fromInt(duration),
            "durationSeconds" -> Json.fromDoubleOrNull(_round3(duration.toDouble / evidence.fps)),
            "leadInFrames" -> Json.fromInt(leadin),
            "leadInSeconds" -> Json.fromDoubleOrNull(_round3(leadin.toDouble / evidence.fps)),
            "sectionTransitionFrames" -> Json.fromInt(transition),
            "sectionTransitionSeconds" -> Json.fromDoubleOrNull(_round3(transition.toDouble / evidence.fps))
          ),
          "absoluteTiming" -> Json.obj(
            "startFrame" -> Json.fromInt(absolute),
            "startSeconds" -> Json.fromDoubleOrNull(_round3(absolute.toDouble / evidence.fps)),
            "durationFrames" -> Json.fromInt(duration),
            "durationSeconds" -> Json.fromDoubleOrNull(_round3(duration.toDouble / evidence.fps)),
            "leadInFrames" -> Json.fromInt(leadin),
            "leadInSeconds" -> Json.fromDoubleOrNull(_round3(leadin.toDouble / evidence.fps)),
            "sectionTransitionFrames" -> Json.fromInt(transition),
            "sectionTransitionSeconds" -> Json.fromDoubleOrNull(_round3(transition.toDouble / evidence.fps))
          ),
          "audio" -> audio.map { entry =>
            Json.obj(
              "durationSeconds" -> Json.fromDoubleOrNull(_round3(entry.audioDuration)),
              "tailSilenceSeconds" -> Json.fromDoubleOrNull(_round3(entry.tailSilence))
            )
          }.getOrElse(Json.Null),
          "frames" -> Json.fromValues(frames.map(frame => _extract_review_frame(plan, finalvideo, savedir, dockermounts, frame, runner)))
        )
      }
      Json.obj(
        "index" -> Json.fromInt(evidence.part.index),
        "id" -> Json.fromString(evidence.part.id),
        "startFrame" -> Json.fromInt(evidence.startframe),
        "startSeconds" -> Json.fromDoubleOrNull(_round3(evidence.startframe.toDouble / evidence.fps)),
        "durationFrames" -> Json.fromInt(evidence.totalframes),
        "durationSeconds" -> Json.fromDoubleOrNull(_round3(evidence.totalframes.toDouble / evidence.fps)),
        "propsPath" -> Json.fromString(evidence.propspath.toString),
        "propsSha256" -> Json.fromString(_sha256(evidence.propspath)),
        "audioManifestPath" -> evidence.audiomanifestpath.map(x => Json.fromString(x.toString)).getOrElse(Json.Null),
        "audioManifestSha256" -> evidence.audiomanifestpath.map(x => Json.fromString(_sha256(x))).getOrElse(Json.Null),
        "scenes" -> Json.fromValues(scenejson)
      )
    }
    val topframejson = topframes.result()
    val scenejson = partjson.flatMap { part =>
      part.hcursor.downField("scenes").focus.flatMap(_.asArray).map(_.toVector).getOrElse(Vector.empty)
    }
    val sceneframecount = scenejson.map { scene =>
      scene.hcursor.downField("frames").focus.flatMap(_.asArray).map(_.size).getOrElse(0)
    }.sum
    val json = Json.obj(
      "schema" -> Json.fromString("cozy.video.review-evidence.v1"),
      "status" -> Json.fromString("validated"),
      "projectFile" -> Json.obj(
        "path" -> Json.fromString(plan.projectFile.toString),
        "sha256" -> Json.fromString(_sha256(plan.projectFile))
      ),
      "finalVideo" -> Json.obj(
        "path" -> Json.fromString(finalvideo.toString),
        "sha256" -> Json.fromString(finalhash)
      ),
      "videoManifest" -> Json.obj(
        "path" -> Json.fromString(videomanifest.toString),
        "sha256" -> Json.fromString(videomanifesthash),
        "status" -> Json.fromString("validated")
      ),
      "renderer" -> Json.obj(
        "width" -> Json.fromInt(renderer._1),
        "height" -> Json.fromInt(renderer._2),
        "fps" -> Json.fromInt(renderer._3)
      ),
      "frames" -> Json.fromValues(topframejson),
      "parts" -> Json.fromValues(partjson)
    )
    Files.writeString(manifest, json.spaces2, StandardCharsets.UTF_8)
    if (!Files.isRegularFile(manifest, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(manifest))
      RAISE.invalidArgumentFault(s"Review evidence manifest was not written as a regular file: $manifest")
    VideoReviewEvidenceResult(
      plan.projectFile,
      savedir,
      finalvideo,
      manifest,
      parts.size,
      partjson.map(_.hcursor.downField("scenes").focus.flatMap(_.asArray).map(_.size).getOrElse(0)).sum,
      topframejson.size + sceneframecount,
      plan.execution.toolMode,
      plan.execution.dockerImage
    )
  }

  private def _review_evidence_parts(plan: VideoPlan): Vector[ReviewEvidencePart] = {
    val raw = plan.parts.filter(_.renderable).map { part =>
      val propspath = _remotion_work_dir(plan.projectRoot, part.id).resolve("props.json").normalize()
      if (!Files.isRegularFile(propspath))
        RAISE.invalidArgumentFault(s"Missing current Cozy Remotion props for part ${part.id}: $propspath")
      else {
        val props = parser.parse(Files.readString(propspath, StandardCharsets.UTF_8)).fold(
          e => RAISE.invalidArgumentFault(s"Invalid Cozy Remotion props for part ${part.id}: ${e.getMessage}"),
          identity
        )
        if (_review_required_string(props, "partId", s"Cozy Remotion props for ${part.id}") != part.id)
          RAISE.invalidArgumentFault(s"Cozy Remotion props partId does not match part ${part.id}: $propspath")
        val fps = _review_positive_int(props, "fps", s"Cozy Remotion props for ${part.id}")
        val width = _review_positive_int(props, "width", s"Cozy Remotion props for ${part.id}")
        val height = _review_positive_int(props, "height", s"Cozy Remotion props for ${part.id}")
        val timing = _review_required_object(props, "timing", s"Cozy Remotion props for ${part.id}")
        val totalframes = _review_positive_int(timing, "totalFrames", s"Cozy Remotion timing for ${part.id}")
        val scenes = _review_required_array(props, "scenes", s"Cozy Remotion props for ${part.id}")
        if (scenes.isEmpty)
          RAISE.invalidArgumentFault(s"Cozy Remotion props have no scenes for part ${part.id}: $propspath")
        val (audiopath, audioentries) = _review_audio_manifest(part)
        val propids = scenes.zipWithIndex.map { case (scene, index) =>
          _review_required_string(scene, "id", s"Cozy Remotion scene ${index + 1} for ${part.id}")
        }
        if (propids.distinct.size != propids.size)
          RAISE.invalidArgumentFault(s"Cozy Remotion props have duplicate scene ids for part ${part.id}.")
        if (audioentries.keySet != propids.toSet)
          RAISE.invalidArgumentFault(s"Audio manifest scene ids do not match Cozy Remotion props for part ${part.id}.")
        scenes.zipWithIndex.foreach { case (scene, index) =>
          val sceneid = propids(index)
          val duration = _review_positive_int(scene, "durationFrames", s"Remotion scene $sceneid")
          val leadin = _review_nonnegative_int(scene, "leadInFrames", s"Remotion scene $sceneid")
          val transition = _review_nonnegative_int(scene, "sectionTransitionFrames", s"Remotion scene $sceneid")
          val audio = audioentries(sceneid)
          val expectedduration = _review_timing_frames(
            math.max(audio.targetDuration, audio.leadSilence + audio.audioDuration + audio.tailSilence),
            fps,
            1L,
            transition,
            s"durationFrames for Remotion scene $sceneid"
          )
          val expectedleadin = _review_timing_frames(
            audio.leadSilence,
            fps,
            0L,
            transition,
            s"leadInFrames for Remotion scene $sceneid"
          )
          if (duration.toLong != expectedduration || leadin.toLong != expectedleadin)
            RAISE.invalidArgumentFault(s"Cozy Remotion timing does not match audio manifest for scene $sceneid in part ${part.id}.")
        }
        ReviewEvidencePart(part, propspath, props, audiopath, audioentries, 0, totalframes, fps, width, height)
      }
    }
    var start = 0
    raw.map { part =>
      val result = part.copy(startframe = start)
      start += part.totalframes
      result
    }
  }

  private def _review_audio_manifest(part: VideoPartPlan): (Option[Path], Map[String, VideoAudioManifestEntry]) =
    part.audioDir match {
      case None => (None, Map.empty)
      case Some(directory) =>
        val path = directory.resolve("manifest.json").normalize()
        if (!Files.isRegularFile(path))
          RAISE.invalidArgumentFault(s"Missing audio manifest evidence for part ${part.id}: $path")
        val entries = parser.decode[Vector[VideoAudioManifestEntry]](Files.readString(path, StandardCharsets.UTF_8)).fold(
          e => RAISE.invalidArgumentFault(s"Invalid audio manifest evidence for part ${part.id}: ${e.getMessage}"),
          identity
        )
        if (entries.exists(x => x.leadSilence < 0 || x.audioDuration < 0 || x.targetDuration < 0 || x.tailSilence < 0))
          RAISE.invalidArgumentFault(s"Audio manifest evidence has negative timing for part ${part.id}: $path")
        val byid = entries.map(x => x.sceneId -> x).toMap
        if (byid.size != entries.size || byid.keys.exists(_.trim.isEmpty))
          RAISE.invalidArgumentFault(s"Audio manifest evidence has duplicate or empty scene ids for part ${part.id}: $path")
        (Some(path), byid)
    }

  private def _review_required_object(json: Json, field: String, label: String): Json =
    json.hcursor.downField(field).focus.filter(_.isObject).getOrElse(
      RAISE.invalidArgumentFault(s"Missing or invalid $field in $label.")
    )

  private def _review_required_array(json: Json, field: String, label: String): Vector[Json] =
    json.hcursor.downField(field).focus.flatMap(_.asArray).map(_.toVector).getOrElse(
      RAISE.invalidArgumentFault(s"Missing or invalid $field in $label.")
    )

  private def _review_required_string(json: Json, field: String, label: String): String =
    json.hcursor.get[String](field).toOption.map(_.trim).filter(_.nonEmpty).getOrElse(
      RAISE.invalidArgumentFault(s"Missing or invalid $field in $label.")
    )

  private def _review_optional_string(json: Json, field: String): Json =
    json.hcursor.get[String](field).toOption.map(Json.fromString).getOrElse(Json.Null)

  private def _review_nonnegative_int(json: Json, field: String, label: String): Int = {
    val value = json.hcursor.get[Int](field).toOption.getOrElse(
      RAISE.invalidArgumentFault(s"Missing or invalid $field in $label.")
    )
    if (value < 0)
      RAISE.invalidArgumentFault(s"$field must be non-negative in $label.")
    value
  }

  private def _review_positive_int(json: Json, field: String, label: String): Int = {
    val value = _review_nonnegative_int(json, field, label)
    if (value == 0)
      RAISE.invalidArgumentFault(s"$field must be positive in $label.")
    value
  }

  private def _review_timing_frames(
    seconds: Double,
    fps: Int,
    minimum: Long,
    transition: Int,
    label: String
  ): Long = {
    val scaled = seconds * fps.toDouble
    if (!java.lang.Double.isFinite(scaled) || scaled > Long.MaxValue.toDouble)
      RAISE.invalidArgumentFault(s"$label is outside the supported frame range.")
    val rounded = math.max(minimum, math.round(scaled))
    if (rounded > Long.MaxValue - transition.toLong)
      RAISE.invalidArgumentFault(s"$label is outside the supported frame range.")
    rounded + transition.toLong
  }

  private def _review_validated_video_manifest(plan: VideoPlan, finalvideo: Path, finalhash: String): Path = {
    val manifest = plan.manifestPath.toAbsolutePath.normalize()
    if (Files.isSymbolicLink(manifest) || !Files.isRegularFile(manifest, LinkOption.NOFOLLOW_LINKS))
      RAISE.invalidArgumentFault(s"Missing validated project video manifest for review evidence: $manifest")
    val json = parser.parse(Files.readString(manifest, StandardCharsets.UTF_8)).fold(
      e => RAISE.invalidArgumentFault(s"Invalid project video manifest for review evidence: ${e.getMessage}"),
      identity
    )
    if (_review_required_string(json, "status", "project video manifest") != "validated")
      RAISE.invalidArgumentFault(s"Project video manifest is not validated: $manifest")
    val output = Paths.get(_review_required_string(json, "outputPath", "project video manifest"))
    val manifestoutput = (if (output.isAbsolute) output else plan.projectRoot.resolve(output)).toAbsolutePath.normalize()
    if (manifestoutput != finalvideo)
      RAISE.invalidArgumentFault(s"Project video manifest outputPath does not match final video: $manifest")
    if (_review_required_string(json, "finalVideoSha256", "project video manifest") != finalhash)
      RAISE.invalidArgumentFault(s"Project video manifest finalVideoSha256 does not match final video: $manifest")
    manifest
  }

  private def _review_optional_top_frame(
    kind: String,
    part: ReviewEvidencePart,
    savedir: Path,
    start: Int,
    duration: Int
  ): Option[ReviewEvidenceFrame] =
    if (duration <= 0)
      None
    else {
      if (start < 0 || start + duration > part.totalframes)
        RAISE.invalidArgumentFault(s"Remotion $kind timing is outside totalFrames for part ${part.part.id}.")
      Some(ReviewEvidenceFrame(
        kind,
        Some(part.part.id),
        None,
        part.startframe + start + math.min(duration - 1, duration / 2),
        part.fps,
        savedir.resolve("frames").resolve(f"$kind-part-${part.part.index}%03d-${_review_file_segment(part.part.id)}.png")
      ))
    }

  private def _review_scene_frame_name(partindex: Int, sceneindex: Int, sceneid: String, kind: String): String =
    f"part-$partindex%03d-scene-${sceneindex + 1}%03d-${_review_file_segment(sceneid)}-$kind.png"

  private def _review_file_segment(value: String): String = {
    val normalized = value.trim.replaceAll("[^A-Za-z0-9]+", "-").replaceAll("^-+|-+$", "")
    if (normalized.isEmpty) "scene" else normalized
  }

  private def _extract_review_frame(
    plan: VideoPlan,
    finalvideo: Path,
    savedir: Path,
    dockermounts: Option[ReviewEvidenceDockerMounts],
    frame: ReviewEvidenceFrame,
    runner: VideoProcessRunner
  ): Json = {
    val target = frame.path
    val (inputarg, outputarg) = dockermounts.map { mounts =>
      (mounts.finalvideo.containerpath, s"${mounts.savedir.containerpath}/frames/${target.getFileName}")
    }.getOrElse((finalvideo.toString, target.toString))
    Files.deleteIfExists(target)
    val ffmpegargs = Vector(
      "-y",
      "-ss",
      f"${frame.absoluteframe.toDouble / frame.fps}%.3f",
      "-i",
      inputarg,
      "-frames:v",
      "1",
      outputarg
    )
    val args = dockermounts.map { mounts =>
      _review_execution_command(
        plan.execution,
        "ffmpeg",
        ffmpegargs,
        Vector(mounts.finalvideo, mounts.savedir)
      )
    }.getOrElse(_execution_command(plan.projectRoot, plan.execution, "ffmpeg", ffmpegargs))
    val result = runner.run(args, plan.projectRoot)
    if (!result.isSuccess)
      RAISE.invalidArgumentFault(s"ffmpeg review evidence extraction failed for ${frame.kind}: ${result.stderr.trim}")
    if (!Files.isRegularFile(target, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(target))
      RAISE.invalidArgumentFault(s"ffmpeg review evidence extraction did not create PNG: $target")
    Json.obj(
      "kind" -> Json.fromString(frame.kind),
      "status" -> Json.fromString("validated"),
      "partId" -> frame.partid.map(Json.fromString).getOrElse(Json.Null),
      "sceneId" -> frame.sceneid.map(Json.fromString).getOrElse(Json.Null),
      "absoluteFrame" -> Json.fromInt(frame.absoluteframe),
      "absoluteSeconds" -> Json.fromDoubleOrNull(_round3(frame.absoluteframe.toDouble / frame.fps)),
      "path" -> Json.fromString(_review_relative_path(savedir, target)),
      "sha256" -> Json.fromString(_sha256(target))
    )
  }

  private def _review_relative_path(savedir: Path, path: Path): String =
    savedir.relativize(path).toString.replace('\\', '/')

  private def _build_project(plan: VideoPlan, runner: VideoProcessRunner): VideoBuildResult = {
    plan.credits.requireValid()
    val creditfiles = _write_credit_outputs(plan)
    val partoutputs = plan.parts.filter(_.renderable).map(_.outputPath)
    if (partoutputs.isEmpty)
      RAISE.invalidArgumentFault("No rendered video part outputs found for final assembly.")
    partoutputs.foreach { path =>
      if (!Files.isRegularFile(path))
        RAISE.invalidArgumentFault(s"Missing rendered part output: $path. Run: cozy video render <project-file> --renderer=remotion|simple-java2d")
    }
    val executionparts = _build_execution_parts(plan.projectRoot, plan.execution, partoutputs)
    val executionoutput = _build_execution_output(plan.projectRoot, plan.execution, plan.outputPath)
    val concatlist = _ffmpeg_concat_list_path(plan.projectRoot)
    _write_ffmpeg_concat_list(plan.projectRoot, plan.execution, concatlist, executionparts)
    Files.deleteIfExists(executionoutput)
    _run_build_ffmpeg(plan.projectRoot, plan.execution, concatlist, executionoutput, runner)
    if (!Files.isRegularFile(executionoutput))
      RAISE.invalidArgumentFault(s"ffmpeg concat/mux did not create output: $executionoutput")
    val ffprobe = _run_build_ffprobe(plan.projectRoot, plan.execution, executionoutput, runner)
    val summary = _ffprobe_summary(ffprobe)
    if (executionoutput != plan.outputPath) {
      Option(plan.outputPath.getParent).foreach(Files.createDirectories(_))
      Files.copy(executionoutput, plan.outputPath, StandardCopyOption.REPLACE_EXISTING)
    }
    _write_project_manifest(plan, concatlist, partoutputs, summary, creditfiles)
    VideoBuildResult(
      plan.projectFile,
      plan.outputPath,
      plan.manifestPath,
      concatlist,
      partoutputs,
      plan.execution.toolMode,
      plan.execution.dockerImage,
      summary,
      plan.credits.profileId,
      creditfiles,
      plan.credits.warnings
    )
  }

  private def _ffmpeg_concat_list_path(projectroot: Path): Path =
    projectroot.resolve("target/cozy-video/ffmpeg/concat.txt").normalize()

  private def _build_execution_parts(
    projectroot: Path,
    execution: VideoExecutionConfig,
    partoutputs: Vector[Path]
  ): Vector[Path] =
    execution.toolMode match {
      case VideoToolMode.Docker =>
        val directory = projectroot.resolve("target/cozy-video/ffmpeg/parts").normalize()
        Files.createDirectories(directory)
        partoutputs.zipWithIndex.map { case (source, index) =>
          val staged = directory.resolve(f"part-${index + 1}%02d.mp4")
          Files.copy(source, staged, StandardCopyOption.REPLACE_EXISTING)
          staged
        }
      case _ => partoutputs
    }

  private def _build_execution_output(
    projectroot: Path,
    execution: VideoExecutionConfig,
    output: Path
  ): Path =
    execution.toolMode match {
      case VideoToolMode.Docker => projectroot.resolve("target/cozy-video/ffmpeg/rendered.mp4").normalize()
      case _ => output
    }

  private def _write_credit_outputs(plan: VideoPlan): Option[CozyVideoCredits.OutputFiles] =
    plan.credits.profile.map { _ =>
      CozyVideoCredits.write(plan.outputPath.getParent.resolve("credits"), plan.credits)
    }

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

  private def _review_execution_command(
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

  private def _ffprobe_summary(result: VideoCommandResult): Json =
    parser.parse(result.stdout).fold(
      e => RAISE.invalidArgumentFault(s"ffprobe returned invalid JSON: ${e.getMessage}"),
      identity
    )

  private def _write_project_manifest(
    plan: VideoPlan,
    concatlist: Path,
    partoutputs: Vector[Path],
    ffprobe: Json,
    creditfiles: Option[CozyVideoCredits.OutputFiles]
  ): Unit = {
    Files.createDirectories(plan.manifestPath.getParent)
    val json = Json.obj(
      "status" -> Json.fromString("validated"),
      "projectFile" -> Json.fromString(plan.projectFile.toString),
      "outputPath" -> Json.fromString(plan.outputPath.toString),
      "finalVideoSha256" -> Json.fromString(_sha256(plan.outputPath)),
      "partOutputs" -> Json.fromValues(partoutputs.map(x => Json.fromString(x.toString))),
      "toolMode" -> Json.fromString(plan.execution.toolMode.label),
      "dockerImage" -> Json.fromString(plan.execution.dockerImage),
      "concatListPath" -> Json.fromString(concatlist.toString),
      "creditProfile" -> plan.credits.profileId.map(Json.fromString).getOrElse(Json.Null),
      "creditDigest" -> creditfiles.map(x => Json.fromString(x.digest)).getOrElse(Json.Null),
      "creditsPath" -> creditfiles.map(x => Json.fromString(x.jsonFile.toString)).getOrElse(Json.Null),
      "creditEvidence" -> Json.fromValues(plan.credits.items.flatMap(_.evidence).distinct.sorted.map(Json.fromString)),
      "creditTermsUrls" -> Json.fromValues(plan.credits.items.flatMap(_.item.termsUrl).distinct.sorted.map(Json.fromString)),
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
    plan.credits.requireValid()
    val creditfiles = _write_credit_outputs(plan)
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
      graph.triples.map(_.subject).distinct.size,
      plan.credits.profileId,
      creditfiles.map(_.digest)
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
      "creditProfile" -> result.creditProfile.map(Json.fromString).getOrElse(Json.Null),
      "creditDigest" -> result.creditDigest.map(Json.fromString).getOrElse(Json.Null),
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
        _video_credit_rdf_triples(projectid, plan) ++
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

  private def _video_credit_rdf_triples(projectid: String, plan: VideoPlan): Vector[Rdf.Triple] = {
    val profile = plan.credits.profileId.toVector.flatMap { id =>
      Vector(
        _rdf_literal(projectid, _cv("creditProfile"), id),
        _rdf_literal(projectid, _cv("creditDigest"), plan.credits.digest)
      )
    }
    val items = plan.credits.rdfItems.flatMap { resolved =>
      val item = resolved.item
      val creditid = _video_rdf_resource("credit", item.id)
      Vector(
        _rdf_uri(projectid, _cv("hasCredit"), creditid),
        _rdf_type(creditid, "VideoCredit"),
        _rdf_literal(creditid, Vocabulary.Rdfs.label, item.label(plan.credits.locale).getOrElse(item.id)),
        _rdf_literal(creditid, _cv("creditCategory"), item.category),
        _rdf_literal(creditid, _cv("creditObligation"), item.obligation)
      ) ++ item.creator.map(x => _rdf_literal(creditid, _dcterms("creator"), x)).toVector ++
        item.sourceUrl.map(x => _rdf_uri(creditid, _dcterms("source"), x)).toVector ++
        item.termsUrl.map(x => _rdf_uri(creditid, _dcterms("license"), x)).toVector ++
        resolved.evidence.map(x => _rdf_literal(creditid, _cv("creditEvidence"), x))
    }
    profile ++ items
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
    plan.credits.requireValid()
    _write_credit_outputs(plan)
    val parts = _render_target_parts(config, plan)
    val rendered = parts.map { part =>
      val script = part.script.getOrElse(RAISE.invalidArgumentFault(s"Part is missing a parsed script: ${part.id}"))
      _validate_declarative_diagrams(script)
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
          "credits" -> props.hcursor.downField("credits").focus.getOrElse(Json.Null),
          "creditDigest" -> plan.credits.profileId.map(_ => Json.fromString(plan.credits.digest)).getOrElse(Json.Null),
          "rendererTemplate" -> props.hcursor.downField("rendererTemplate").focus.getOrElse(Json.Null),
          "rendererTemplateSha256" -> props.hcursor.downField("rendererTemplateSha256").focus.getOrElse(Json.Null),
          "rendererTemplateResources" -> props.hcursor.downField("rendererTemplateResources").focus.getOrElse(Json.arr()),
          "recordingPath" -> props.hcursor.downField("recordingPath").focus.getOrElse(Json.Null),
          "timing" -> props.hcursor.downField("timing").focus.getOrElse(Json.obj())
        )
      )
      VideoRenderedPart(part.id, part.outputPath, part.manifestPath, workdir)
    }
    VideoRenderResult(plan.projectFile, rendered, plan.execution.toolMode, plan.execution.dockerImage, plan.credits.warnings)
  }

  private def _render_simple_java2d(
    config: RenderConfig,
    plan: VideoPlan,
    runner: VideoProcessRunner
  ): VideoRenderResult = {
    plan.credits.requireValid()
    _write_credit_outputs(plan)
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
    VideoRenderResult(plan.projectFile, rendered, plan.execution.toolMode, plan.execution.dockerImage, plan.credits.warnings)
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
    val characterdialogue = _is_character_dialogue(part, script, plan.project.renderer)
    val characterwebdemo = part.partType == "web-demo"
    val template = if (characterdialogue) Some(_load_character_dialogue_template()) else None
    val templateid =
      if (characterdialogue) Some(_character_dialogue_template_id)
      else if (characterwebdemo) Some(_character_web_demo_template_id)
      else None
    val recording = if (characterwebdemo) Some(_stage_web_demo_recording(plan.projectRoot, part, workdir)) else None
    Files.writeString(srcdir.resolve("Root.tsx"), if (characterdialogue) _remotion_character_dialogue_root_tsx else if (characterwebdemo) _remotion_character_web_demo_root_tsx else _remotion_root_tsx, StandardCharsets.UTF_8)
    template.foreach { bundled =>
      Files.write(srcdir.resolve(bundled.dialogue.name), bundled.dialogue.bytes)
      Files.write(srcdir.resolve(bundled.diagramlayout.name), bundled.diagramlayout.bytes)
    }
    Files.writeString(srcdir.resolve("render.mjs"), _remotion_render_mjs, StandardCharsets.UTF_8)
    val assets = _copy_remotion_assets(workdir, plan.assets)
    val dialogueassets = if (characterdialogue || characterwebdemo) _copy_character_dialogue_assets(plan.projectRoot, part, script, workdir, stagevisuals = characterdialogue) else CharacterDialogueAssets.empty
    val propsjson = _remotion_props_json(plan, part, script, audio, assets, workdir, characterdialogue, templateid, template.map(_.dialogue.sha256), template.map(_.resources), dialogueassets, recording)
    Files.writeString(workdir.resolve("props.json"), propsjson.spaces2, StandardCharsets.UTF_8)
    Files.writeString(srcdir.resolve("props.ts"), _remotion_props_ts(propsjson), StandardCharsets.UTF_8)
    _copy_remotion_audio(workdir, audio)
    propsjson
  }

  private final case class CharacterDialogueAssets(characters: Json, visuals: Map[String, Json])
  private object CharacterDialogueAssets {
    val empty = CharacterDialogueAssets(Json.obj(), Map.empty)
  }

  private def _is_character_dialogue(part: VideoPartPlan, script: VideoScript, renderer: Option[VideoRenderer]): Boolean = {
    val strategy = _part_renderer_property(part, "strategy").orElse(renderer.flatMap(_.strategy)).map(_.trim.toLowerCase)
    part.partType == "dialogue" && (strategy match {
      case Some("narration-card") | Some("generic") => false
      case Some("character-dialogue") => true
      case _ => script.characters.nonEmpty
    })
  }

  private def _part_renderer_property(part: VideoPartPlan, name: String): Option[String] =
    part.renderer.split(",").toVector.map(_.trim).collectFirst {
      case field if field.startsWith(name + "=") => field.drop(name.length + 1).trim
    }.filter(_.nonEmpty)

  private final case class CharacterDialogueTemplateResource(name: String, bytes: Array[Byte], sha256: String)
  private final case class CharacterDialogueTemplate(dialogue: CharacterDialogueTemplateResource, diagramlayout: CharacterDialogueTemplateResource) {
    def resources: Vector[(String, String)] = Vector(dialogue, diagramlayout).map(x => x.name -> x.sha256)
  }

  private def _load_character_dialogue_template(): CharacterDialogueTemplate = {
    val dialogue = _load_character_dialogue_template_resource("DialogueVideo.jsx", _character_dialogue_template_sha256)
    val diagramlayout = _load_character_dialogue_template_resource("DiagramLayout.js", _character_dialogue_diagram_layout_sha256)
    CharacterDialogueTemplate(dialogue, diagramlayout)
  }

  private def _load_character_dialogue_template_resource(name: String, expectedsha256: String): CharacterDialogueTemplateResource = {
    val resource = s"/cozy/video/remotion/$name"
    val stream = Option(getClass.getResourceAsStream(resource)).getOrElse(
      RAISE.invalidArgumentFault(s"Missing Cozy character-dialogue renderer resource: ${resource.drop(1)}")
    )
    val bytes = try stream.readAllBytes() finally stream.close()
    val actualsha256 = _sha256_bytes(bytes)
    if (actualsha256 != expectedsha256)
      RAISE.invalidArgumentFault(s"Cozy character-dialogue renderer resource digest does not match the bundled template contract: $name")
    CharacterDialogueTemplateResource(name, bytes, actualsha256)
  }

  private def _sha256_bytes(bytes: Array[Byte]): String =
    MessageDigest.getInstance("SHA-256").digest(bytes).map(x => f"${x & 0xff}%02x").mkString

  private def _validate_declarative_diagrams(script: VideoScript): Unit =
    script.expandedScenes.foreach { scene =>
      if (_json_string(scene.visual, "kind").contains("diagram"))
        _validate_declarative_diagram(scene.id.getOrElse("(no id)"), scene.visual)
    }

  private def _validate_declarative_diagram(sceneid: String, visual: Json): Unit = {
    val diagram = visual.hcursor.downField("diagram").focus.getOrElse(
      _diagram_fault(sceneid, "missing-diagram")
    )
    val cursor = diagram.hcursor
    val layout = cursor.get[String]("layout").getOrElse(_diagram_fault(sceneid, "unsupported-layout"))
    if (layout != "flow" && layout != "axis")
      _diagram_fault(sceneid, "unsupported-layout")
    val direction = cursor.get[Option[String]]("direction").getOrElse(_diagram_fault(sceneid, "unsupported-direction")).getOrElse("right")
    if (direction != "right")
      _diagram_fault(sceneid, "unsupported-direction")
    val clearance = cursor.get[Option[Double]]("clearance").getOrElse(_diagram_fault(sceneid, "invalid-clearance")).getOrElse(24.0)
    if (!java.lang.Double.isFinite(clearance) || clearance <= 0)
      _diagram_fault(sceneid, "invalid-clearance")
    val nodes = cursor.get[Vector[Json]]("nodes").getOrElse(_diagram_fault(sceneid, "empty-nodes"))
    if (nodes.isEmpty)
      _diagram_fault(sceneid, "empty-nodes")
    val parsednodes = nodes.map { node =>
      val nodecursor = node.hcursor
      val id = nodecursor.get[String]("id").getOrElse(_diagram_fault(sceneid, "invalid-node-id"))
      if (id.trim.isEmpty)
        _diagram_fault(sceneid, "invalid-node-id")
      val label = nodecursor.get[String]("label").getOrElse(_diagram_fault(sceneid, "invalid-node-label", id))
      if (label.trim.isEmpty)
        _diagram_fault(sceneid, "invalid-node-label", id)
      val role = nodecursor.get[String]("role").getOrElse(_diagram_fault(sceneid, "invalid-node-role", id))
      if (role.trim.isEmpty)
        _diagram_fault(sceneid, "invalid-node-role", id)
      val policy = nodecursor.get[String]("labelPolicy").getOrElse(_diagram_fault(sceneid, "unsupported-label-policy", id))
      if (policy != "atomic" && policy != "balanced")
        _diagram_fault(sceneid, "unsupported-label-policy", id)
      _validate_declarative_diagram_label(sceneid, id, label, policy, if (layout == "flow") 18.0 else 21.0)
      (id, role)
    }
    parsednodes.groupBy(_._1).collectFirst { case (id, values) if values.size > 1 => id }.foreach { id =>
      _diagram_fault(sceneid, "duplicate-node-id", id)
    }
    val nodeids = parsednodes.map(_._1).toSet
    val edges = cursor.get[Option[Vector[Json]]]("edges").getOrElse(_diagram_fault(sceneid, "invalid-edges")).getOrElse(Vector.empty)
    edges.foreach { edge =>
      val edgecursor = edge.hcursor
      val from = edgecursor.get[String]("from").getOrElse(_diagram_fault(sceneid, "invalid-edge"))
      val to = edgecursor.get[String]("to").getOrElse(_diagram_fault(sceneid, "invalid-edge"))
      if (!nodeids.contains(from))
        _diagram_fault(sceneid, "missing-edge-endpoint", from)
      if (!nodeids.contains(to))
        _diagram_fault(sceneid, "missing-edge-endpoint", to)
    }
    if (layout == "axis" && parsednodes.count(_._2 == "axis") != 1)
      _diagram_fault(sceneid, "axis-role-count")
  }

  private def _validate_declarative_diagram_label(sceneid: String, nodeid: String, label: String, policy: String, fontsize: Double): Unit = {
    val maxtextwidth = 204.0
    if (policy == "atomic" && _diagram_label_width(label, fontsize) > maxtextwidth)
      _diagram_fault(sceneid, "impossible-atomic-fit", nodeid)
    if (policy == "balanced") {
      val segments = label.split("(?<=-)|\\s+").filter(_.nonEmpty)
      if (segments.isEmpty || segments.exists(x => _diagram_label_width(x, fontsize) > maxtextwidth))
        _diagram_fault(sceneid, "label-overflow", nodeid)
    }
  }

  private def _diagram_label_width(label: String, fontsize: Double): Double =
    label.toVector.map(x => if (x <= '\u007f') fontsize * 0.56 else fontsize).sum

  private def _diagram_fault(sceneid: String, violation: String, nodeid: String = ""): Nothing = {
    val node = Option(nodeid).filter(_.nonEmpty).map(x => s" node $x").getOrElse("")
    RAISE.invalidArgumentFault(s"Diagram scene $sceneid$node: $violation")
  }

  private def _copy_character_dialogue_assets(projectroot: Path, part: VideoPartPlan, script: VideoScript, workdir: Path, stagevisuals: Boolean = true): CharacterDialogueAssets = {
    val scriptpath = part.scriptPath.getOrElse(RAISE.invalidArgumentFault(s"Part has no script path: ${part.id}"))
    val bases = _character_asset_bases(projectroot, scriptpath)
    val characters = script.characters.toVector.map { case (id, character) =>
      val staged = Vector("asset", "mouthClosedAsset", "mouthOpenAsset").foldLeft(character) { (z, field) =>
        _json_string(z, field).map { authored =>
          z.mapObject(_.add(field, Json.fromString(_stage_character_dialogue_asset(projectroot, workdir, bases, authored, "characters", s"character $id", field))))
        }.getOrElse(z)
      }
      id -> staged
    }
    val visuals = if (stagevisuals) script.expandedScenes.map { scene =>
      val staged = _json_string(scene.visual, "image").map { authored =>
        scene.visual.mapObject(_.add("image", Json.fromString(_stage_character_dialogue_asset(projectroot, workdir, bases, authored, "visuals", s"scene ${scene.id.getOrElse("(no id)")}", "visual.image"))))
      }.getOrElse(scene.visual)
      scene.id.getOrElse("") -> staged
    }.toMap else Map.empty[String, Json]
    CharacterDialogueAssets(Json.obj(characters: _*), visuals)
  }

  private def _stage_web_demo_recording(projectroot: Path, part: VideoPartPlan, workdir: Path): String = {
    val configured = part.recordDir.getOrElse(RAISE.invalidArgumentFault(s"Web-demo part ${part.id} has no record directory"))
    val directory = _project_contained_existing_path(projectroot, configured, s"Web-demo part ${part.id} recordDir")
    val candidates =
      if (Files.isDirectory(directory) && Files.isReadable(directory)) {
        val stream = Files.list(directory)
        try stream.iterator().asScala.flatMap { path =>
          val name = path.getFileName.toString.toLowerCase
          if (name.endsWith(".webm") || name.endsWith(".mp4")) {
            val source = _project_contained_existing_path(projectroot, path, s"Web-demo part ${part.id} recording")
            if (Files.isRegularFile(source) && Files.isReadable(source)) Some(source) else None
          } else None
        }.toVector.sortBy(_.getFileName.toString)
        finally stream.close()
      } else Vector.empty
    if (candidates.size != 1)
      RAISE.invalidArgumentFault(s"Web-demo part ${part.id} requires exactly one readable .webm or .mp4 in $directory")
    val source = candidates.head
    val targetdir = workdir.resolve("public/recording")
    Files.createDirectories(targetdir)
    val target = targetdir.resolve("recording" + _asset_extension(source))
    Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING)
    s"recording/${target.getFileName}"
  }

  private def _character_asset_bases(projectroot: Path, scriptpath: Path): Vector[Path] = {
    val parent = Option(scriptpath.getParent).getOrElse(projectroot).toAbsolutePath.normalize()
    val locale = if (parent.getFileName.toString == "dialogue") Option(parent.getParent).getOrElse(parent) else parent
    Vector(locale, parent, projectroot.toAbsolutePath.normalize()).distinct
  }

  private def _stage_character_dialogue_asset(projectroot: Path, workdir: Path, bases: Vector[Path], authored: String, directory: String, owner: String, field: String): String = {
    val authoredpath = Try(Paths.get(authored)).getOrElse(RAISE.invalidArgumentFault(s"Invalid $field asset for $owner: $authored"))
    if (authoredpath.isAbsolute)
      RAISE.invalidArgumentFault(s"Absolute $field asset is not allowed for $owner: $authored")
    if (authoredpath.iterator().asScala.exists(_.toString == ".."))
      RAISE.invalidArgumentFault(s"Traversal outside admitted asset bases for $owner $field: $authored")
    val project = projectroot.toAbsolutePath.normalize()
    val source = bases.iterator.flatMap { base =>
      val path = base.resolve(authoredpath).normalize()
      if (!path.startsWith(project))
        RAISE.invalidArgumentFault(s"Traversal outside project root for $owner $field: $authored")
      if (Files.exists(path) || Files.isSymbolicLink(path)) {
        val contained = _project_contained_existing_path(projectroot, path, s"$field asset for $owner")
        if (Files.isRegularFile(contained) && Files.isReadable(contained)) Some(contained) else None
      } else None
    }.toVector.headOption.getOrElse(RAISE.invalidArgumentFault(s"Missing or unreadable $field asset for $owner: $authored"))
    val targetdir = workdir.resolve("public").resolve(directory)
    Files.createDirectories(targetdir)
    val target = targetdir.resolve(_file_segment_id(owner.replace(' ', '-'), "asset owner") + "-" + _file_segment_id(field.replace('.', '-'), "asset field") + _asset_extension(source))
    Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING)
    s"$directory/${target.getFileName}"
  }

  private def _resolve_project_relative_path(projectroot: Path, authored: String, owner: String): Path = {
    val authoredpath = Try(Paths.get(authored)).getOrElse(RAISE.invalidArgumentFault(s"Invalid $owner path: $authored"))
    if (authoredpath.isAbsolute)
      RAISE.invalidArgumentFault(s"Absolute $owner path is not allowed: $authored")
    if (authoredpath.iterator().asScala.exists(_.toString == ".."))
      RAISE.invalidArgumentFault(s"Traversal outside project root is not allowed for $owner: $authored")
    val root = projectroot.toAbsolutePath.normalize()
    val path = root.resolve(authoredpath).normalize()
    if (!path.startsWith(root))
      RAISE.invalidArgumentFault(s"Traversal outside project root is not allowed for $owner: $authored")
    path
  }

  private def _project_contained_existing_path(projectroot: Path, path: Path, owner: String): Path = {
    val root = projectroot.toAbsolutePath.normalize()
    val candidate = path.toAbsolutePath.normalize()
    if (!candidate.startsWith(root))
      RAISE.invalidArgumentFault(s"$owner is outside project root: $path")
    val realroot = Try(root.toRealPath()).getOrElse(RAISE.invalidArgumentFault(s"Missing or unreadable project root for $owner: $root"))
    val realcandidate = Try(candidate.toRealPath()).getOrElse(RAISE.invalidArgumentFault(s"Missing or unreadable $owner: $path"))
    if (!realcandidate.startsWith(realroot))
      RAISE.invalidArgumentFault(s"$owner resolves outside project root: $path")
    realcandidate
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
    Option(part.outputPath.getParent).foreach(Files.createDirectories(_))
    val script = workdir.resolve("src/render.mjs")
    val stagedoutput = _remotion_staged_output(workdir)
    Files.deleteIfExists(stagedoutput)
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
    if (!Files.isRegularFile(stagedoutput))
      RAISE.invalidArgumentFault(s"Remotion render did not create staged output for part ${part.id}: $stagedoutput")
    Files.copy(stagedoutput, part.outputPath, StandardCopyOption.REPLACE_EXISTING)
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
    assets: Vector[(CozyVideoAssets.Resolved, Option[String])],
    workdir: Path,
    characterdialogue: Boolean,
    template: Option[String],
    templatedigest: Option[String],
    templateresources: Option[Vector[(String, String)]],
    dialogueassets: CharacterDialogueAssets,
    recording: Option[String]
  ): Json = {
    val renderer = plan.project.renderer
    val fps = renderer.flatMap(_.fps).filter(_ > 0).getOrElse(30)
    val width = renderer.flatMap(_.width).filter(_ > 0).getOrElse(1280)
    val height = renderer.flatMap(_.height).filter(_ > 0).getOrElse(720)
    val effects = CozyVideoEffects.expand(plan.project.visualEffects)
    val effectprofile = _part_renderer_property(part, "effectProfile").orElse(renderer.flatMap(_.effectProfile)).
      map(_.trim).filter(_.nonEmpty).getOrElse("compact")
    val sectionstarteffect = effects.exists(x => x.role == CozyVideoEffects.Role.SectionStart && x.primitives.nonEmpty)
    val sectiontransitionframes =
      if (characterdialogue && effectprofile == "compact" && sectionstarteffect)
        math.max(0, math.round(1.2 * fps).toInt)
      else
        0
    def _section_key_(scene: VideoScene, index: Int): String =
      scene.section.filter(_.nonEmpty).
        orElse(_json_string(scene.effects, "section").filter(_.nonEmpty)).
        orElse(_json_string(scene.visual, "section").filter(_.nonEmpty)).
        getOrElse(s"scene-$index")
    val scenesectiontransitions = script.expandedScenes.indices.map { index =>
      if (index > 0 && _section_key_(script.expandedScenes(index), index) != _section_key_(script.expandedScenes(index - 1), index - 1))
        sectiontransitionframes
      else
        0
    }
    val contentframes = math.max(
      1,
      audio.entries.zip(scenesectiontransitions).map { case (entry, transitionframes) =>
        math.max(1, math.round(_effective_render_duration(entry) * fps).toInt) + transitionframes
      }.sum
    )
    val isfirstpart = plan.parts.filter(_.renderable).headOption.exists(_.id == part.id)
    val openingseconds = _effect_parameter_double(
      effects,
      CozyVideoEffects.Role.Opening,
      "hold",
      "seconds"
    ).getOrElse(0.0)
    val openingframes =
      if (isfirstpart) math.max(0, math.round(openingseconds * fps).toInt)
      else 0
    val sectionframes = if (sectionstarteffect) math.min(contentframes, math.round(1.2 * fps).toInt) else 0
    val isfinalpart = plan.parts.filter(_.renderable).lastOption.exists(_.id == part.id)
    val summaryframes = if (isfinalpart && effects.exists(x => x.role == CozyVideoEffects.Role.Summary && x.primitives.nonEmpty)) math.min(contentframes, math.round(2.4 * fps).toInt) else 0
    val creditframes =
      if (isfinalpart && plan.credits.hasVideoPage)
        math.max(1, math.round(plan.credits.holdSeconds * fps).toInt)
      else
        0
    val holdseconds = _effect_parameter_double(
      effects,
      CozyVideoEffects.Role.FinalPage,
      "hold",
      "seconds"
    ).getOrElse(0.0)
    val finalframes = if (isfinalpart) math.max(0, math.round(holdseconds * fps).toInt) else 0
    val totalframes = openingframes + contentframes + creditframes + finalframes
    var startframe = 0
    val scenes = script.expandedScenes.zip(audio.entries).zip(audio.files).zip(scenesectiontransitions).map {
      case (((scene, entry), file), transitionframes) =>
        val authoreddurationframes = math.max(1, math.round(_effective_render_duration(entry) * fps).toInt)
        val authoredleadframes = math.max(0, math.round(entry.leadSilence * fps).toInt)
        val durationframes = authoreddurationframes + transitionframes
        val leadinframes = authoredleadframes + transitionframes
        val stagedvisual = dialogueassets.visuals.getOrElse(scene.id.getOrElse(""), scene.visual)
        val json =
        Json.obj(
          "id" -> Json.fromString(entry.sceneId),
          "speaker" -> entry.speaker.map(Json.fromString).getOrElse(Json.Null),
          "text" -> Json.fromString(scene.narration.orElse(scene.line).orElse(scene.caption).getOrElse("")),
          "audioPath" -> Json.fromString(s"audio/${file.getFileName}"),
          "duration" -> Json.fromDoubleOrNull(_effective_render_duration(entry) + transitionframes.toDouble / fps),
          "line" -> scene.line.orElse(scene.narration).orElse(scene.caption).map(Json.fromString).getOrElse(Json.Null),
          "caption" -> scene.caption.orElse(scene.line).orElse(scene.narration).map(Json.fromString).getOrElse(Json.Null),
          "visual" -> stagedvisual,
          "section" -> scene.section.map(Json.fromString).getOrElse(Json.Null),
          "effects" -> scene.effects,
          "silent" -> Json.fromBoolean(scene.silent.getOrElse(false)),
          "startFrame" -> Json.fromInt(startframe),
          "durationFrames" -> Json.fromInt(durationframes),
          "leadInFrames" -> Json.fromInt(leadinframes),
          "sectionTransitionFrames" -> Json.fromInt(transitionframes),
          "audioDuration" -> Json.fromDoubleOrNull(entry.audioDuration)
        )
        startframe += durationframes
        json
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
        "tags" -> Json.fromValues(asset.tags.map(Json.fromString)),
        "credits" -> Json.fromValues(asset.credits.map(Json.fromString)),
        "creditObligation" -> asset.creditObligation.map(Json.fromString).getOrElse(Json.Null),
        "status" -> Json.fromString(asset.status)
      )
    }
    Json.obj(
      "partId" -> Json.fromString(part.id),
      "title" -> Json.fromString(plan.project.title.orElse(script.title).getOrElse(part.id)),
      "outputPath" -> Json.fromString(_project_relative(plan.projectRoot, _remotion_staged_output(workdir))),
      "fps" -> Json.fromInt(fps),
      "width" -> Json.fromInt(width),
      "height" -> Json.fromInt(height),
      "durationSeconds" -> Json.fromDoubleOrNull(totalframes.toDouble / fps),
      "scenes" -> Json.fromValues(scenes),
      "characters" -> dialogueassets.characters,
      "sections" -> Json.fromValues(script.sections),
      "rendererTemplate" -> template.map(Json.fromString).getOrElse(Json.Null),
      "rendererTemplateSha256" -> templatedigest.map(Json.fromString).getOrElse(Json.Null),
      "rendererTemplateResources" -> templateresources.map { resources =>
        Json.fromValues(resources.map { case (name, sha256) =>
          Json.obj("path" -> Json.fromString(name), "sha256" -> Json.fromString(sha256))
        })
      }.getOrElse(Json.arr()),
      "recordingPath" -> recording.map(Json.fromString).getOrElse(Json.Null),
      "effectProfile" -> Json.fromString(effectprofile),
      "visualEffects" -> Json.fromValues(effectsjson),
      "assets" -> Json.fromValues(assetsjson),
      "credits" -> CozyVideoCredits.toRendererProps(plan.credits),
      "timing" -> Json.obj(
        "openingFrames" -> Json.fromInt(openingframes),
        "contentFrames" -> Json.fromInt(contentframes),
        "sectionStartFrame" -> Json.fromInt(openingframes),
        "sectionStartFrames" -> Json.fromInt(sectionframes),
        "summaryStartFrame" -> Json.fromInt(openingframes + math.max(0, contentframes - summaryframes)),
        "summaryFrames" -> Json.fromInt(summaryframes),
        "creditPageStartFrame" -> Json.fromInt(openingframes + contentframes),
        "creditPageHoldFrames" -> Json.fromInt(creditframes),
        "finalPageStartFrame" -> Json.fromInt(openingframes + contentframes + creditframes),
        "finalPageHoldFrames" -> Json.fromInt(finalframes),
        "totalFrames" -> Json.fromInt(totalframes)
      )
    )
  }

  private val _character_dialogue_template_id = "cozy-character-dialogue-v1"
  private val _character_dialogue_template_sha256 = "a3d9df7640383fe75848afd995ac78dca391fb6ec8b066362ee02b988b06399a"
  private val _character_dialogue_diagram_layout_sha256 = "9f36dd5c7765af8613cbe3924de5aae14b4b7e8c92d54418e58b95f798e81b56"
  private val _character_web_demo_template_id = "cozy-character-web-demo-v1"

  private def _effective_render_duration(entry: VideoAudioManifestEntry): Double =
    math.max(entry.targetDuration, entry.leadSilence + entry.audioDuration + entry.tailSilence)

  private def _remotion_staged_output(workdir: Path): Path =
    workdir.resolve("rendered.mp4")

  private def _effect_parameter_double(
    effects: Vector[CozyVideoEffects.Expansion],
    role: CozyVideoEffects.Role,
    primitive: String,
    parameter: String
  ): Option[Double] =
    effects.find(_.role == role).toVector.flatMap(_.primitives).
      find(_.name == primitive).
      flatMap(_.parameters.find(_._1 == parameter).map(_._2)).
      flatMap(x => Try(x.toDouble).toOption)

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
      |  name: 'title-card' | 'subtle-motion' | 'flow-line' | 'underline-sweep' | 'summary-layout' | 'fade-rise' | 'spring-pop' | 'end-card' | 'hold';
      |  parameters: Record<string, string>;
      |};
      |
      |type VisualEffect = {
      |  role: 'opening' | 'section-start' | 'summary' | 'final-page';
      |  profile: string;
      |  primitives: Primitive[];
      |};
      |
      |type Asset = {
      |  role: 'opening' | 'section-start' | 'summary' | 'final-page';
      |  path: string | null;
      |  kind: string;
      |  required: boolean;
      |  license: string;
      |  provenance: string;
      |  status: string;
      |};
      |
      |type Timing = {
      |  openingFrames: number;
      |  contentFrames: number;
      |  sectionStartFrame: number;
      |  sectionStartFrames: number;
      |  summaryStartFrame: number;
      |  summaryFrames: number;
      |  creditPageStartFrame: number;
      |  creditPageHoldFrames: number;
      |  finalPageStartFrame: number;
      |  finalPageHoldFrames: number;
      |  totalFrames: number;
      |};
      |
      |type CreditItem = {
      |  id: string;
      |  category: string;
      |  label: string;
      |  creator: string | null;
      |};
      |
      |type Credits = {
      |  title: string;
      |  items: CreditItem[];
      |};
      |
      |type Props = {
      |  partId: string;
      |  title: string;
      |  fps: number;
      |  width: number;
      |  height: number;
      |  durationSeconds: number;
      |  scenes: Scene[];
      |  visualEffects: VisualEffect[];
      |  assets: Asset[];
      |  credits: Credits;
      |  timing: Timing;
      |};
      |
      |const CreditPage: React.FC<{credits: Credits}> = ({credits}) => (
      |  <AbsoluteFill style={{backgroundColor: '#101820', color: '#f4efe6', fontFamily: 'Noto Sans CJK JP, sans-serif', padding: '64px 84px'}}>
      |    <div style={{fontSize: 48, fontWeight: 800, marginBottom: 30}}>{credits.title}</div>
      |    <div style={{display: 'flex', flexDirection: 'column', gap: 18}}>
      |      {credits.items.map((item) => (
      |        <div key={item.id} style={{fontSize: 30, lineHeight: 1.3}}>
      |          <span style={{fontWeight: 700}}>{item.label}</span>
      |          {item.creator ? <span style={{opacity: 0.78}}> — {item.creator}</span> : null}
      |        </div>
      |      ))}
      |    </div>
      |  </AbsoluteFill>
      |);
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
      |const Opening: React.FC<{title: string; effect: VisualEffect; asset?: Asset; durationInFrames: number}> = ({title, effect, asset, durationInFrames}) => {
      |  const frame = useCurrentFrame();
      |  const titleCard = primitive(effect, 'title-card');
      |  const motion = primitive(effect, 'subtle-motion');
      |  const hold = primitive(effect, 'hold');
      |  const configuredScale = Number.parseFloat(motion?.parameters.scale || '1');
      |  const scale = motion ? interpolate(frame, [0, Math.max(1, durationInFrames - 1)], [1, Number.isFinite(configuredScale) ? configuredScale : 1], {extrapolateLeft: 'clamp', extrapolateRight: 'clamp'}) : 1;
      |  return titleCard && hold ? (
      |    <AbsoluteFill style={{backgroundColor: '#101820', color: '#f4efe6', alignItems: 'center', justifyContent: 'center', overflow: 'hidden'}}>
      |      <div style={{position: 'absolute', inset: -16, transform: `scale(${scale})`}}><AssetFrame asset={asset} opacity={1} /></div>
      |    </AbsoluteFill>
      |  ) : null;
      |};
      |
      |const SectionStart: React.FC<{effect: VisualEffect; asset?: Asset; durationInFrames: number}> = ({effect, asset, durationInFrames}) => {
      |  const frame = useCurrentFrame();
      |  const flow = primitive(effect, 'flow-line');
      |  const underline = primitive(effect, 'underline-sweep');
      |  const progress = interpolate(frame, [0, Math.max(1, durationInFrames - 1)], [0, 1], {extrapolateLeft: 'clamp', extrapolateRight: 'clamp'});
      |  const direction = flow?.parameters.direction === 'right-to-left' ? -1 : 1;
      |  return (
      |    <AbsoluteFill style={{backgroundColor: 'transparent', overflow: 'hidden', pointerEvents: 'none'}}>
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
      |      <AssetFrame asset={asset} opacity={1} />
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
      |      <AssetFrame asset={asset} opacity={1} />
      |    </AbsoluteFill>
      |  ) : null;
      |};
      |
      |export const CozyVideo: React.FC<Props> = ({title, scenes, fps, visualEffects, assets, credits, timing}) => {
      |  let start = timing.openingFrames;
      |  const opening = roleEffect(visualEffects, 'opening');
      |  const section = roleEffect(visualEffects, 'section-start');
      |  const summary = roleEffect(visualEffects, 'summary');
      |  const finalPage = roleEffect(visualEffects, 'final-page');
      |  return (
      |    <AbsoluteFill>
      |      {opening && timing.openingFrames > 0 ? <Sequence from={0} durationInFrames={timing.openingFrames}><Opening title={title} effect={opening} asset={roleAsset(assets, 'opening')} durationInFrames={timing.openingFrames} /></Sequence> : null}
      |      {scenes.map((scene) => {
      |        const duration = Math.max(1, Math.round((scene.duration || 1) * fps));
      |        const sequence = <Sequence key={scene.id} from={start} durationInFrames={duration}><SceneCard scene={scene} /></Sequence>;
      |        start += duration;
      |        return sequence;
      |      })}
      |      {section && timing.sectionStartFrames > 0 ? <Sequence from={timing.sectionStartFrame} durationInFrames={timing.sectionStartFrames}><SectionStart effect={section} asset={roleAsset(assets, 'section-start')} durationInFrames={timing.sectionStartFrames} /></Sequence> : null}
      |      {summary && timing.summaryFrames > 0 ? <Sequence from={timing.summaryStartFrame} durationInFrames={timing.summaryFrames}><Summary effect={summary} asset={roleAsset(assets, 'summary')} /></Sequence> : null}
      |      {timing.creditPageHoldFrames > 0 && credits.items.length > 0 ? <Sequence from={timing.creditPageStartFrame} durationInFrames={timing.creditPageHoldFrames}><CreditPage credits={credits} /></Sequence> : null}
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

  private val _remotion_character_dialogue_root_tsx: String =
    """import React from 'react';
      |import {AbsoluteFill, Audio, Composition, Img, Sequence, interpolate, registerRoot, staticFile, useCurrentFrame} from 'remotion';
      |import {DialogueVideo} from './DialogueVideo.jsx';
      |import {cozyVideoProps} from './props';
      |
      |const assetFor = (role) => cozyVideoProps.assets?.find((asset) => asset.role === role);
      |const AssetSurface = ({role}) => {
      |  const asset = assetFor(role);
      |  return asset?.path ? <Img src={staticFile(asset.path)} style={{position: 'absolute', inset: 0, width: '100%', height: '100%', objectFit: 'contain', opacity: 1}} /> : null;
      |};
      |const OpeningSurface = () => {
      |  const frame = useCurrentFrame();
      |  const opening = cozyVideoProps.visualEffects?.find((effect) => effect.role === 'opening');
      |  const scaleValue = Number(opening?.primitives?.find((primitive) => primitive.name === 'subtle-motion')?.parameters?.scale || 1);
      |  const duration = Math.max(1, cozyVideoProps.timing?.openingFrames || 1);
      |  const scale = interpolate(frame, [0, duration - 1], [1, Number.isFinite(scaleValue) ? scaleValue : 1], {extrapolateLeft: 'clamp', extrapolateRight: 'clamp'});
      |  return <div style={{position: 'absolute', inset: -16, transform: `scale(${scale})`}}><AssetSurface role="opening" /></div>;
      |};
      |const CreditPage = ({credits}) => <AbsoluteFill style={{backgroundColor: '#101820', color: '#f4efe6', fontFamily: 'Noto Sans CJK JP, sans-serif', padding: '64px 84px'}}><div style={{fontSize: 48, fontWeight: 800, marginBottom: 30}}>{credits.title}</div><div style={{display: 'flex', flexDirection: 'column', gap: 18}}>{credits.items.map((item) => <div key={item.id} style={{fontSize: 30, lineHeight: 1.3}}><span style={{fontWeight: 700}}>{item.label}</span>{item.creator ? <span style={{opacity: 0.78}}> — {item.creator}</span> : null}</div>)}</div></AbsoluteFill>;
      |
      |const CharacterDialogueVideo = () => {
      |  const props = cozyVideoProps;
      |  const contentStart = props.timing?.openingFrames || 0;
      |  const contentFrames = props.timing?.contentFrames || 1;
      |  return <AbsoluteFill>
      |    {contentStart > 0 ? <Sequence from={0} durationInFrames={contentStart}><OpeningSurface /></Sequence> : null}
      |    <Sequence from={contentStart} durationInFrames={contentFrames}>
      |      <DialogueVideo characters={Object.fromEntries(Object.entries(props.characters || {}).map(([id, character]) => [id, Object.fromEntries(Object.entries(character).map(([key, value]) => (key === 'asset' || key === 'mouthClosedAsset' || key === 'mouthOpenAsset' || key.endsWith('Asset')) && typeof value === 'string' ? [key, staticFile(value)] : [key, value]))]))} sections={props.sections || []} scenes={(props.scenes || []).map((scene) => ({...scene, visual: scene.visual?.image ? {...scene.visual, image: staticFile(scene.visual.image)} : scene.visual}))} fps={props.fps} effectProfile={props.effectProfile} />
      |      {(props.scenes || []).map((scene) => <Sequence key={`audio-${scene.id}`} from={Math.max(0, scene.startFrame + scene.leadInFrames)} durationInFrames={Math.max(1, scene.durationFrames - scene.leadInFrames)}><Audio src={staticFile(scene.audioPath)} /></Sequence>)}
      |    </Sequence>
      |    {props.timing?.summaryFrames > 0 ? <Sequence from={props.timing.summaryStartFrame} durationInFrames={props.timing.summaryFrames}><AssetSurface role="summary" /></Sequence> : null}
      |    {props.timing?.creditPageHoldFrames > 0 && props.credits?.items?.length > 0 ? <Sequence from={props.timing.creditPageStartFrame} durationInFrames={props.timing.creditPageHoldFrames}><CreditPage credits={props.credits} /></Sequence> : null}
      |    {props.timing?.finalPageHoldFrames > 0 ? <Sequence from={props.timing.finalPageStartFrame} durationInFrames={props.timing.finalPageHoldFrames}><AssetSurface role="final-page" /></Sequence> : null}
      |  </AbsoluteFill>;
      |};
      |
      |export const RemotionRoot: React.FC = () => <Composition id="CozyVideo" component={CharacterDialogueVideo} durationInFrames={Math.max(1, cozyVideoProps.timing?.totalFrames || 1)} fps={cozyVideoProps.fps} width={cozyVideoProps.width} height={cozyVideoProps.height} defaultProps={cozyVideoProps} />;
      |registerRoot(RemotionRoot);
      |export default RemotionRoot;
      |""".stripMargin

  private val _remotion_character_web_demo_root_tsx: String =
    """import React from 'react';
      |import {AbsoluteFill, Audio, Composition, Img, Sequence, Video, registerRoot, staticFile, useCurrentFrame} from 'remotion';
      |import {cozyVideoProps} from './props';
      |
      |const roleAsset = (role) => cozyVideoProps.assets?.find((asset) => asset.role === role);
      |const AssetSurface = ({role}) => {
      |  const asset = roleAsset(role);
      |  return asset?.path ? <Img src={staticFile(asset.path)} style={{position: 'absolute', inset: 0, width: '100%', height: '100%', objectFit: 'contain'}} /> : null;
      |};
      |const CreditPage = ({credits}) => <AbsoluteFill style={{backgroundColor: '#101820', color: '#f4efe6', fontFamily: 'Noto Sans CJK JP, sans-serif', padding: '64px 84px'}}><div style={{fontSize: 48, fontWeight: 800, marginBottom: 30}}>{credits.title}</div><div style={{display: 'flex', flexDirection: 'column', gap: 18}}>{credits.items.map((item) => <div key={item.id} style={{fontSize: 30, lineHeight: 1.3}}><span style={{fontWeight: 700}}>{item.label}</span>{item.creator ? <span style={{opacity: 0.78}}> — {item.creator}</span> : null}</div>)}</div></AbsoluteFill>;
      |const numberOr = (value, fallback) => Number.isFinite(Number(value)) ? Number(value) : fallback;
      |const DemoContent = () => {
      |  const frame = useCurrentFrame();
      |  const props = cozyVideoProps;
      |  const scene = (props.scenes || []).find((candidate) => frame >= candidate.startFrame && frame < candidate.startFrame + candidate.durationFrames) || props.scenes?.[0];
      |  const localFrame = Math.max(0, frame - (scene?.startFrame || 0));
      |  const character = scene?.speaker ? props.characters?.[scene.speaker] : null;
      |  const mouthOpen = !scene?.silent && character?.mouthOpenAsset && character?.mouthClosedAsset && localFrame >= (scene?.leadInFrames || 0) && Math.floor(localFrame / 4) % 2 === 0;
      |  const source = mouthOpen ? character.mouthOpenAsset : (character?.mouthClosedAsset || character?.asset);
      |  const side = character?.side === 'right' ? 'right' : 'left';
      |  const width = numberOr(character?.width, 252);
      |  const bottom = numberOr(character?.bottom, 150);
      |  const inset = numberOr(character?.inset, 28);
      |  const maxHeight = numberOr(character?.maxHeight, 430);
      |  return <AbsoluteFill style={{backgroundColor: '#101820', overflow: 'hidden'}}>
      |    {props.recordingPath ? <Video src={staticFile(props.recordingPath)} muted loop style={{position: 'absolute', inset: 0, width: '100%', height: '100%', objectFit: 'contain'}} /> : null}
      |    {source ? <Img src={staticFile(source)} style={{position: 'absolute', bottom, [side]: inset, width, maxHeight, objectFit: 'contain', transform: character?.flipX ? 'scaleX(-1)' : undefined, filter: character?.shadow?.color ? `drop-shadow(${character.shadow.x || 12}px ${character.shadow.y || 18}px ${character.shadow.blur || 0}px ${character.shadow.color})` : undefined}} /> : null}
      |    {scene?.caption || scene?.line ? <div style={{position: 'absolute', left: 72, right: 72, bottom: 18, minHeight: 110, display: 'flex', alignItems: 'center', background: 'rgba(16,18,22,.94)', color: '#fff', borderRadius: 10, padding: '18px 30px 18px 42px', boxSizing: 'border-box', fontSize: 30, fontWeight: 800, lineHeight: 1.34}}>{scene.caption || scene.line}</div> : null}
      |    {(props.scenes || []).map((candidate) => <Sequence key={`audio-${candidate.id}`} from={Math.max(0, candidate.startFrame + candidate.leadInFrames)} durationInFrames={Math.max(1, candidate.durationFrames - candidate.leadInFrames)}><Audio src={staticFile(candidate.audioPath)} /></Sequence>)}
      |  </AbsoluteFill>;
      |};
      |const WebDemoVideo = () => {
      |  const props = cozyVideoProps;
      |  const contentStart = props.timing?.openingFrames || 0;
      |  return <AbsoluteFill>
      |    {contentStart > 0 ? <Sequence from={0} durationInFrames={contentStart}><AssetSurface role="opening" /></Sequence> : null}
      |    <Sequence from={contentStart} durationInFrames={props.timing?.contentFrames || 1}><DemoContent /></Sequence>
      |    {props.timing?.summaryFrames > 0 ? <Sequence from={props.timing.summaryStartFrame} durationInFrames={props.timing.summaryFrames}><AssetSurface role="summary" /></Sequence> : null}
      |    {props.timing?.creditPageHoldFrames > 0 && props.credits?.items?.length > 0 ? <Sequence from={props.timing.creditPageStartFrame} durationInFrames={props.timing.creditPageHoldFrames}><CreditPage credits={props.credits} /></Sequence> : null}
      |    {props.timing?.finalPageHoldFrames > 0 ? <Sequence from={props.timing.finalPageStartFrame} durationInFrames={props.timing.finalPageHoldFrames}><AssetSurface role="final-page" /></Sequence> : null}
      |  </AbsoluteFill>;
      |};
      |export const RemotionRoot: React.FC = () => <Composition id="CozyVideo" component={WebDemoVideo} durationInFrames={Math.max(1, cozyVideoProps.timing?.totalFrames || 1)} fps={cozyVideoProps.fps} width={cozyVideoProps.width} height={cozyVideoProps.height} defaultProps={cozyVideoProps} />;
      |registerRoot(RemotionRoot);
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
    val creditevidence = CozyVideoAssets.creditEvidence(projectroot, project.assets, assets)
    val audiomanifests = parts.flatMap { part =>
      part.audioDir.toVector.flatMap { audiodir =>
        val path = audiodir.resolve("manifest.json").normalize()
        _read_optional_audio_manifest(path, s"audio manifest ${part.id}").map(path -> _).toVector
      }
    }
    val credits = CozyVideoCredits.resolve(
      projectroot,
      project.credits,
      project.locale,
      parts.flatMap(_.script),
      creditevidence,
      audiomanifests
    )
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
    VideoPlan(projectfile, projectroot, project, assets, credits, execution, outputpath, manifestpath, parts, artifacts, commands)
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
    val recorddir = part.recordDir.map(x => _resolve_project_relative_path(projectroot, x, s"Web-demo part $id recordDir")).orElse {
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
    b ++= _render_credit_inspection(plan.credits)
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
    b ++= _render_credit_inspection(plan.credits)
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
    result.creditFiles.foreach { files =>
      result.creditProfile.foreach(x => b += s"creditProfile: $x")
      b += s"creditDigest: ${files.digest}"
      b += s"credits: ${files.jsonFile}"
      b += s"creditMarkdown: ${files.markdownFile}"
      b += s"creditRendererProps: ${files.rendererPropsFile}"
    }
    result.creditWarnings.foreach { warning =>
      b += s"creditWarning: ${warning.code}: ${warning.message}"
    }
    b.result().mkString("\n") + "\n"
  }

  private def _render_credit_inspection(credits: CozyVideoCredits.EffectiveSet): Vector[String] = {
    val b = Vector.newBuilder[String]
    credits.selection.foreach { selection =>
      b += s"creditProfile: ${selection.id}"
      b += s"creditProfileSelectionLayer: ${selection.layer}"
      selection.configPath.foreach(x => b += s"creditProfileSelectionPath: $x")
    }
    credits.profile.foreach { source =>
      b += s"creditProfileSourceLayer: ${source.layer}"
      b += s"creditProfileSourcePath: ${source.path}"
    }
    b += s"creditLocale: ${credits.locale}"
    if (credits.evidence.characterIds.nonEmpty)
      b += s"creditCharacters: ${credits.evidence.characterIds.mkString(", ")}"
    if (credits.evidence.assetTags.nonEmpty)
      b += s"creditAssetTags: ${credits.evidence.assetTags.mkString(", ")}"
    credits.evidence.audio.foreach { audio =>
      b += s"creditAudio: provider=${audio.provider} voice=${audio.voiceIdentity.orElse(audio.voiceId).orElse(audio.modelIdentity).getOrElse("unknown")} manifest=${audio.manifestPath}"
    }
    if (credits.items.nonEmpty) {
      b += "credits:"
      credits.items.foreach { resolved =>
        b += s"  - ${resolved.item.id}: ${resolved.item.obligation} evidence=${resolved.evidence.mkString(",")}"
        resolved.item.sourceUrl.foreach(x => b += s"    source: $x")
        resolved.item.termsUrl.foreach(x => b += s"    terms: $x")
      }
    }
    credits.diagnostics.foreach { diagnostic =>
      b += s"credit${diagnostic.severity.capitalize}: ${diagnostic.code}: ${diagnostic.message}"
    }
    b.result()
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
    result.creditWarnings.foreach { warning =>
      b += s"creditWarning: ${warning.code}: ${warning.message}"
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

  private def _render_review_evidence_result(result: VideoReviewEvidenceResult): String = {
    val b = Vector.newBuilder[String]
    b += "Cozy Video Review Evidence"
    b += s"projectFile: ${result.projectFile}"
    b += s"finalVideo: ${result.finalVideo}"
    b += s"outputDir: ${result.saveDir}"
    b += s"toolMode: ${result.toolMode.label}"
    b += s"dockerImage: ${result.dockerImage}"
    b += s"manifest: ${result.manifestPath}"
    b += s"parts: ${result.partCount}"
    b += s"scenes: ${result.sceneCount}"
    b += s"frames: ${result.frameCount}"
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
