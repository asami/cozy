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
 * @version Aug. 18, 2026
 * @author  ASAMI, Tomoharu
 */
private[cozy] trait CozyVideoModel {
  self: CozyVideoTools =>
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
    val DEFAULT_FPS = 18
    val DEFAULT_WIDTH = 1280
    val DEFAULT_HEIGHT = 720
    val DEFAULT_CRF = 32

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

  private[video] final case class ReviewEvidencePart(
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

  private[video] final case class ReviewEvidenceFrame(
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
    projectContext: CozyProjectContext.Context,
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

  private[video] def _normalize_replay_kind(value: String): String =
    value.trim.toLowerCase(java.util.Locale.ROOT) match {
      case "navigate" => "goto"
      case "input" => "fill"
      case "keydown" => "press"
      case "pause" => "wait"
      case "goto" | "click" | "fill" | "press" | "wait" | "note" | "screenshot" => value.trim.toLowerCase(java.util.Locale.ROOT)
      case other => other
    }
}


private[cozy] trait CozyVideoTypes extends CozyVideoConfig with CozyVideoModel with CozyVideoTools
