package cozy.video

import org.goldenport.RAISE
import org.goldenport.config.StructuredDocumentLoader
import org.goldenport.io.InputSource
import cozy.runtime.CozyCliArgs
import org.goldenport.cli.spec
import io.circe.{Decoder, HCursor, Json}
import java.nio.file.{Files, Path}

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

  final case class BuildConfig(
    projectFile: Path,
    dryRun: Boolean,
    checkTools: Boolean
  ) {
    def projectRoot: Path = projectFile.getParent
  }
  object BuildConfig {
    def create(args: List[String]): BuildConfig = {
      val parsed = CozyCliArgs.parseStrict(_p_project_file, _p_dry_run, _p_check_tools)(args)
      val projectfile = parsed.argument("project-file").map(CozyCliArgs.toPath).getOrElse(
        RAISE.invalidArgumentFault("Missing project file for video build")
      )
      BuildConfig(projectfile, parsed.flag("dry-run"), parsed.flag("check-tools"))
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
    outputPath: Path,
    manifestPath: Path,
    parts: Vector[VideoPartPlan],
    artifacts: Vector[VideoArtifactPlan],
    commands: Vector[VideoCommandPlan]
  )

  private val _p_project_file = spec.Parameter.argumentFile("project-file")
  private val _p_check_tools = spec.Parameter("check-tools", spec.Parameter.SwitchKind)
  private val _p_dry_run = spec.Parameter("dry-run", spec.Parameter.SwitchKind)
  private val _supported_part_types = Set("dialogue", "storyboard", "web-demo")

  def execute(args: List[String]): Boolean = execute(args, VideoToolRegistry.default)

  def execute(args: List[String], tools: VideoToolRegistry): Boolean =
    args match {
      case "video" :: "inspect" :: rest =>
        println(inspect(InspectConfig.create(rest), tools))
        true
      case "video" :: "build" :: rest =>
        println(build(BuildConfig.create(rest), tools))
        true
      case "video" :: other :: _ =>
        RAISE.invalidArgumentFault(s"Unsupported video command: $other")
      case _ =>
        false
    }

  def inspect(config: InspectConfig, tools: VideoToolRegistry): String = {
    val plan = _plan(config.projectFile)
    val context = VideoToolContext(config.projectFile, config.projectRoot, plan.project)
    _render_inspect(config, plan, if (config.checkTools) tools.checks(context) else Vector.empty)
  }

  def build(config: BuildConfig, tools: VideoToolRegistry): String = {
    if (!config.dryRun)
      RAISE.invalidArgumentFault("cozy video build without --dry-run is not implemented yet")
    val plan = _plan(config.projectFile)
    val context = VideoToolContext(config.projectFile, config.projectRoot, plan.project)
    _render_build_dry_run(config, plan, if (config.checkTools) tools.checks(context) else Vector.empty)
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

  private def _plan(projectfile: Path): VideoPlan = {
    val project = _load_project(projectfile)
    val projectroot = projectfile.getParent
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
    val commands =
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
    VideoPlan(projectfile, projectroot, project, outputpath, manifestpath, parts, artifacts, commands)
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
    val manifestpath = outputpath.getParent.resolve("manifest.json").normalize()
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
    parse ++ capture.filter(_ => stepsstatus.forall(_ == "found")) ++ synthesize ++ render ++ manifest
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
