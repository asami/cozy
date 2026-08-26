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
 * @version Aug. 26, 2026
 * @author  ASAMI, Tomoharu
 */
private[cozy] trait CozyVideoCommand {
  self: CozyVideoTypes with CozyVideoStoryboard with CozyVideoStoryboardReview with CozyVideoStoryboardBuild with CozyVideoRuntime with CozyVideoNarration with CozyVideoToolValidation with CozyVideoTranscription with CozyVideoReviewEvidence with CozyVideoBuildReplay with CozyVideoRdf with CozyVideoRenderWorkspace with CozyVideoRenderTemplates with CozyVideoPlanning with CozyVideoPresentation =>
  def execute(args: List[String]): Boolean = execute(args, VideoToolRegistry.default)

  def execute(args: List[String], tools: VideoToolRegistry): Boolean =
    execute(args, tools, VoicevoxClient.default, VideoProcessRunner.default)

  def execute(args: List[String], tools: VideoToolRegistry, voicevox: VoicevoxClient): Boolean =
    execute(args, tools, voicevox, VideoProcessRunner.default)

  def execute(args: List[String], tools: VideoToolRegistry, voicevox: VoicevoxClient, runner: VideoProcessRunner): Boolean =
    args match {
      case "video" :: "storyboard" :: "validate" :: rest =>
        println(storyboardValidate(StoryboardValidateConfig.create(rest)))
        true
      case "video" :: "storyboard" :: "inspect" :: rest =>
        println(storyboardInspect(StoryboardInspectConfig.create(rest)))
        true
      case "video" :: "storyboard" :: "convert" :: rest =>
        println(storyboardConvert(StoryboardConvertConfig.create(rest)))
        true
      case "video" :: "storyboard" :: "review-evidence" :: rest =>
        println(storyboardReview(StoryboardReviewConfig.create(rest)))
        true
      case "video" :: "storyboard" :: other :: _ =>
        RAISE.invalidArgumentFault(s"Unsupported video storyboard command: $other")
      case "video" :: "storyboard" :: Nil =>
        RAISE.invalidArgumentFault("Missing video storyboard command: validate, inspect, convert, or review-evidence")
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
    val context = VideoToolContext(plan.projectFile, plan.projectRoot, plan.project, plan.execution, providers)
    _render_inspect(config, plan, if (config.checkTools) tools.checks(context) else Vector.empty)
  }

  def build(config: BuildConfig, tools: VideoToolRegistry): String =
    build(config, tools, VideoProcessRunner.default)

  def build(config: BuildConfig, tools: VideoToolRegistry, runner: VideoProcessRunner): String = {
    val plan = _plan(config.projectFile, config.toolMode, config.dockerImage)
    if (plan.project.parts.exists(_.storyboard.isDefined))
      _build_storyboard_mode(config, plan, tools, runner)
    else {
      if (plan.project.storyboardReview.isDefined)
        _validate_storyboard_review_current(plan)
      val context = VideoToolContext(plan.projectFile, plan.projectRoot, plan.project, plan.execution)
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
    val context = VideoToolContext(plan.projectFile, plan.projectRoot, plan.project, plan.execution)
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
    val context = VideoToolContext(plan.projectFile, plan.projectRoot, plan.project, plan.execution)
    val checks = if (config.checkTools) tools.checks(context) else Vector.empty
    _validate_review_evidence_tools(plan.execution, checks)
    _render_review_evidence_result(_write_review_evidence(config, plan, runner))
  }

  def verifyCredits(projectfile: Path): Vector[String] = {
    val plan = _plan(projectfile, None, None)
    val manifestpath =
      if (plan.project.parts.exists(_.storyboard.isDefined))
        plan.projectRoot.resolve("target").resolve("cozy-video").resolve("final").resolve("manifest.json")
      else
        plan.manifestPath
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
          (if (Files.isRegularFile(manifestpath)) Vector.empty else Vector(s"missing project manifest with credit digest: $manifestpath"))
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
          if (!Files.isRegularFile(manifestpath))
            Vector.empty
          else {
            val manifest = parser.parse(Files.readString(manifestpath, StandardCharsets.UTF_8)).toOption
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

  private[video] def _credit_json_projection_finding(path: Path, expected: Json, label: String): Option[String] =
    if (!Files.isRegularFile(path))
      None
    else {
      val actual = parser.parse(Files.readString(path, StandardCharsets.UTF_8)).toOption
      if (actual.contains(expected)) None else Some(s"$label does not match the effective credit set: $path")
    }

  private[video] def _credit_text_projection_finding(path: Path, expected: String, label: String): Option[String] =
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

  private[video] def _load_project(path: Path): VideoProject = {
    val projectfile = _verified_project_file(path)
    val project = StructuredDocumentLoader.loadDocument[VideoProject](InputSource(projectfile.toFile)).take
    CozyVideoEffects.expand(project.visualEffects)
    project
  }

  private[video] def _verified_project_file(path: Path): Path = {
    val source = Option(path).getOrElse(
      RAISE.invalidArgumentFault("Video project descriptor path must be defined")
    )
    val projectfile = source.toAbsolutePath.normalize()
    if (!Files.exists(projectfile, LinkOption.NOFOLLOW_LINKS))
      RAISE.invalidArgumentFault(s"Missing video project file: $source")
    if (Files.isSymbolicLink(projectfile) || !Files.isRegularFile(projectfile, LinkOption.NOFOLLOW_LINKS))
      RAISE.invalidArgumentFault(
        s"Video project descriptor must be an existing direct regular non-symlink file (source: $source, normalized: $projectfile)"
      )
    val attributes = try Files.readAttributes(projectfile, classOf[BasicFileAttributes], LinkOption.NOFOLLOW_LINKS) catch {
      case NonFatal(e) => RAISE.invalidArgumentFault(s"Video project descriptor attributes cannot be read (source: $source): ${e.getMessage}")
    }
    if (attributes.fileKey() == null)
      RAISE.invalidArgumentFault(s"Video project descriptor has no stable direct file identity (source: $source, normalized: $projectfile)")
    val identity = try projectfile.toRealPath() catch {
      case NonFatal(e) => RAISE.invalidArgumentFault(s"Video project descriptor canonical identity cannot be read (source: $source): ${e.getMessage}")
    }
    if (Files.isSymbolicLink(identity) || !Files.isRegularFile(identity, LinkOption.NOFOLLOW_LINKS))
      RAISE.invalidArgumentFault(
        s"Video project descriptor canonical identity must be a direct regular non-symlink file (source: $source, normalized: $projectfile, identity: $identity)"
      )
    val identityattributes = try Files.readAttributes(identity, classOf[BasicFileAttributes], LinkOption.NOFOLLOW_LINKS) catch {
      case NonFatal(e) => RAISE.invalidArgumentFault(s"Video project descriptor canonical attributes cannot be read (source: $source, identity: $identity): ${e.getMessage}")
    }
    if (identityattributes.fileKey() == null || !_same_file_attributes(attributes, identityattributes))
      RAISE.invalidArgumentFault(s"Video project descriptor changed while being checked (source: $source, normalized: $projectfile, identity: $identity)")
    val after = try Files.readAttributes(identity, classOf[BasicFileAttributes], LinkOption.NOFOLLOW_LINKS) catch {
      case NonFatal(e) => RAISE.invalidArgumentFault(s"Video project descriptor changed while being checked (source: $source): ${e.getMessage}")
    }
    if (!_same_file_attributes(identityattributes, after))
      RAISE.invalidArgumentFault(s"Video project descriptor changed while being checked (source: $source, identity: $identity)")
    val sourceafter = try Files.readAttributes(projectfile, classOf[BasicFileAttributes], LinkOption.NOFOLLOW_LINKS) catch {
      case NonFatal(e) => RAISE.invalidArgumentFault(s"Video project descriptor changed while being checked (source: $source): ${e.getMessage}")
    }
    if (!_same_file_attributes(attributes, sourceafter))
      RAISE.invalidArgumentFault(s"Video project descriptor changed while being checked (source: $source, normalized: $projectfile)")
    identity
  }

  private[video] def _same_file_attributes(before: BasicFileAttributes, after: BasicFileAttributes): Boolean =
    before.fileKey() == after.fileKey() &&
      before.size() == after.size() &&
      before.lastModifiedTime() == after.lastModifiedTime()

  private[video] def _load_script(path: Path): Option[VideoScript] =
    if (Files.isRegularFile(path))
      Some(StructuredDocumentLoader.loadDocument[VideoScript](InputSource(path.toFile)).take)
    else
      None

  private[video] def _load_required_script(path: Path): VideoScript = {
    if (!Files.isRegularFile(path))
      RAISE.invalidArgumentFault(s"Missing video script file: $path")
    StructuredDocumentLoader.loadDocument[VideoScript](InputSource(path.toFile)).take
  }

  private[video] def _load_replay_script(path: Path): VideoReplayScript = {
    if (!Files.isRegularFile(path))
      RAISE.invalidArgumentFault(s"Missing video replay script file: $path")
    StructuredDocumentLoader.loadDocument[VideoReplayScript](InputSource(path.toFile)).take
  }

  private[video] def _write_demo_script(config: DemoScriptConfig): VideoDemoScriptResult = {
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

  private[video] def _require_regular_file(path: Path, label: String): Unit =
    if (!Files.isRegularFile(path))
      RAISE.invalidArgumentFault(s"Missing $label: $path")

  private[video] def _demo_script_json(
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

  private[video] def _event_log_steps(json: Json): Vector[VideoReplayStep] = {
    val cursor = json.hcursor
    val rawsteps = cursor.downField("steps").focus.flatMap(_.asArray).getOrElse(json.asArray.getOrElse(Vector.empty))
    rawsteps.map(_event_log_step)
  }

  private[video] def _event_log_step(json: Json): VideoReplayStep = {
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

  private[video] def _event_log_viewport(json: Json): Option[VideoReplayViewport] =
    json.hcursor.downField("viewport").focus.map { viewport =>
      VideoReplayViewport(_json_int(viewport, "width").getOrElse(1280), _json_int(viewport, "height").getOrElse(720))
    }

  private[video] def _har_initial_url(json: Json): Option[String] =
    json.hcursor.downField("log").downField("entries").focus.flatMap(_.asArray).flatMap { entries =>
      entries.toVector.flatMap { entry =>
        val request = entry.hcursor.downField("request")
        val url = request.downField("url").as[String].toOption
        val resourcetype = entry.hcursor.downField("_resourceType").as[String].toOption
        val method = request.downField("method").as[String].toOption
        url.filter(_ => resourcetype.contains("document") || method.contains("GET"))
      }.headOption
    }

  private[video] def _read_transcript_segments(path: Path): Vector[VideoTranscriptSegment] = {
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

  private[video] def _replay_execution(config: ReplayConfig): VideoExecutionConfig = {
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
}
