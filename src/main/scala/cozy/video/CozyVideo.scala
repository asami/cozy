package cozy.video

import org.goldenport.RAISE
import org.goldenport.config.StructuredDocumentLoader
import org.goldenport.io.InputSource
import cozy.runtime.CozyCliArgs
import org.goldenport.cli.spec
import io.circe.{Decoder, HCursor, Json}
import java.nio.file.{Files, Path, Paths}

/*
 * @since   Jun. 18, 2026
 * @version Jun. 18, 2026
 * @author  ASAMI, Tomoharu
 */
private[cozy] object CozyVideo {
  final case class InspectConfig(
    projectFile: Path,
    checkTools: Boolean
  ) {
    def projectRoot: Path = projectFile.getParent
  }
  object InspectConfig {
    def create(args: List[String]): InspectConfig = {
      val parsed = CozyCliArgs.parseStrict(_p_project_file, _p_check_tools)(args)
      val projectfile = parsed.argument("project-file").map(CozyCliArgs.toPath).getOrElse(
        RAISE.invalidArgumentFault("Missing project file for video inspect")
      )
      InspectConfig(projectfile, parsed.flag("check-tools"))
    }
  }

  final case class VideoProject(
    name: Option[String],
    title: Option[String],
    output: Option[String],
    renderer: Option[VideoRenderer],
    parts: Vector[VideoPart]
  )
  object VideoProject {
    implicit val decoder: Decoder[VideoProject] = (c: HCursor) =>
      for {
        name <- c.downField("name").as[Option[String]]
        title <- c.downField("title").as[Option[String]]
        output <- c.downField("output").as[Option[String]]
        renderer <- c.downField("renderer").as[Option[VideoRenderer]]
        parts <- c.downField("parts").as[Option[Vector[VideoPart]]]
      } yield VideoProject(name, title, output, renderer, parts.getOrElse(Vector.empty))
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
        characters <- c.downField("characters").as[Option[Map[String, Json]]]
        sections <- c.downField("sections").as[Option[Vector[Json]]]
        scenes <- c.downField("scenes").as[Option[Vector[VideoScene]]]
      } yield VideoScript(title, characters.getOrElse(Map.empty), sections.getOrElse(Vector.empty), scenes.getOrElse(Vector.empty))
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
    subscenes: Vector[VideoScene]
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
            leadSilence = subscene.leadSilence.orElse(leadSilence)
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
      } yield VideoScene(id, speaker, line, narration, caption, duration, targetduration, leadsilence, subscenes.getOrElse(Vector.empty))
  }

  sealed trait VideoToolMode { def label: String }
  object VideoToolMode {
    case object Docker extends VideoToolMode { val label = "docker" }
    case object Host extends VideoToolMode { val label = "host" }
    case object ExternalService extends VideoToolMode { val label = "external-service" }
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
    project: VideoProject
  )

  trait VideoToolProvider {
    def check(context: VideoToolContext): VideoToolCheck
  }

  final case class VideoToolRegistry(providers: Vector[VideoToolProvider]) {
    def checks(context: VideoToolContext): Vector[VideoToolCheck] = providers.map(_.check(context))
  }
  object VideoToolRegistry {
    val default: VideoToolRegistry = VideoToolRegistry(Vector(
      UncheckedToolProvider("docker-toolchain", VideoToolMode.Docker, "Docker toolchain check is not implemented yet.", Some("Future check: docker image simplemodeling/cozy-toolchain:latest")),
      UncheckedToolProvider("voicevox", VideoToolMode.ExternalService, "VOICEVOX HTTP check is not implemented yet.", Some("Future check: video.voicevox.url")),
      UncheckedToolProvider("ffmpeg", VideoToolMode.Host, "ffmpeg/ffprobe check is not implemented yet.", Some("Future check: ffmpeg and ffprobe availability")),
      UncheckedToolProvider("remotion-node", VideoToolMode.Host, "Remotion/Node check is not implemented yet.", Some("Future check: node, npm, and Remotion dependencies")),
      UncheckedToolProvider("playwright", VideoToolMode.Host, "Playwright check is not implemented yet.", Some("Future check: Playwright Chromium availability")),
      UncheckedToolProvider("whisper-cpp", VideoToolMode.Host, "whisper.cpp check is not implemented yet.", Some("Future check: whisper.cpp binary and model data"))
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

  private val _p_project_file = spec.Parameter.argumentFile("project-file")
  private val _p_check_tools = spec.Parameter("check-tools", spec.Parameter.SwitchKind)
  private val _supported_part_types = Set("dialogue", "storyboard", "web-demo")

  def execute(args: List[String]): Boolean = execute(args, VideoToolRegistry.default)

  def execute(args: List[String], tools: VideoToolRegistry): Boolean =
    args match {
      case "video" :: "inspect" :: rest =>
        println(inspect(InspectConfig.create(rest), tools))
        true
      case "video" :: other :: _ =>
        RAISE.invalidArgumentFault(s"Unsupported video command: $other")
      case _ =>
        false
    }

  def inspect(config: InspectConfig, tools: VideoToolRegistry): String = {
    val project = _load_project(config.projectFile)
    val context = VideoToolContext(config.projectFile, config.projectRoot, project)
    _render_inspect(config, context, if (config.checkTools) tools.checks(context) else Vector.empty)
  }

  private def _load_project(path: Path): VideoProject = {
    if (!Files.isRegularFile(path))
      RAISE.invalidArgumentFault(s"Missing video project file: $path")
    StructuredDocumentLoader.loadDocument[VideoProject](InputSource(path.toFile)).take
  }

  private def _load_script(path: Path): Option[VideoScript] =
    if (Files.isRegularFile(path))
      Some(StructuredDocumentLoader.loadDocument[VideoScript](InputSource(path.toFile)).take)
    else
      None

  private def _render_inspect(
    config: InspectConfig,
    context: VideoToolContext,
    checks: Vector[VideoToolCheck]
  ): String = {
    val project = context.project
    val b = Vector.newBuilder[String]
    b += "Cozy Video Inspect"
    b += s"projectFile: ${context.projectFile}"
    b += s"projectRoot: ${context.projectRoot}"
    project.name.foreach(x => b += s"name: $x")
    project.title.foreach(x => b += s"title: $x")
    b += s"output: ${context.projectRoot.resolve(project.output.getOrElse("build/final.mp4")).normalize()}"
    b += s"renderer: ${project.renderer.map(_.summary).getOrElse("engine=legacy")}"
    b += s"parts: ${project.parts.size}"
    project.parts.zipWithIndex.foreach { case (part, index) =>
      b ++= _render_part(context.projectRoot, project, part, index + 1)
    }
    if (config.checkTools) {
      b += "tools:"
      checks.foreach { check =>
        b += s"  - ${check.name}: ${check.status.label} (${check.mode.label}) - ${check.message}"
        check.setupHint.foreach(x => b += s"    setup: $x")
      }
    }
    b.result().mkString("\n") + "\n"
  }

  private def _render_part(
    projectroot: Path,
    project: VideoProject,
    part: VideoPart,
    index: Int
  ): Vector[String] = {
    val id = part.displayId(index)
    val parttype = part.displayType
    val supported = _supported_part_types.contains(parttype)
    val renderer = part.renderer.orElse(project.renderer).map(_.summary).getOrElse("engine=legacy")
    val z = Vector.newBuilder[String]
    z += s"part[$index]: $id"
    z += s"  type: $parttype${if (supported) "" else " (unsupported)"}"
    z += s"  renderer: $renderer"
    part.script match {
      case Some(script) =>
        val scriptpath = projectroot.resolve(script).normalize()
        z += s"  script: $script"
        z += s"  scriptPath: $scriptpath"
        _load_script(scriptpath) match {
          case Some(videoscript) =>
            z += s"  scriptStatus: found"
            videoscript.title.foreach(x => z += s"  scriptTitle: $x")
            z += s"  scenes: ${videoscript.scenes.size}"
            z += s"  expandedScenes: ${videoscript.expandedScenes.size}"
            z += f"  estimatedDuration: ${videoscript.estimatedDuration}%.2f"
          case None =>
            z += s"  scriptStatus: missing"
        }
      case None =>
        z += s"  scriptStatus: none"
    }
    part.steps.foreach { steps =>
      z += s"  steps: $steps"
      z += s"  stepsPath: ${projectroot.resolve(steps).normalize()}"
    }
    part.output.foreach(x => z += s"  output: ${projectroot.resolve(x).normalize()}")
    part.audioDir.foreach(x => z += s"  audioDir: ${projectroot.resolve(x).normalize()}")
    part.recordDir.foreach(x => z += s"  recordDir: ${projectroot.resolve(x).normalize()}")
    z.result()
  }
}
