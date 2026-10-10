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
 *  version Aug. 19, 2026
 * @version Oct.  8, 2026
 * @author  ASAMI, Tomoharu
 */
private[cozy] object CozyVideoBuildReplay {
  private[video] final case class MediaDuration(numerator: BigInt, denominator: BigInt) {
    def +(other: MediaDuration): MediaDuration =
      MediaDuration(numerator * other.denominator + other.numerator * denominator, denominator * other.denominator)
    def <=(other: MediaDuration): Boolean = numerator * other.denominator <= other.numerator * denominator
    def sampleFloor: BigInt = numerator * 48000 / denominator
    def decimal: String = new java.math.BigDecimal(numerator.bigInteger)
      .divide(new java.math.BigDecimal(denominator.bigInteger), 18, java.math.RoundingMode.HALF_UP).toPlainString
  }
  private[video] final case class PartMediaTimeline(duration: MediaDuration, hasaudio: Boolean)
}

private[cozy] trait CozyVideoBuildReplay {
  self: CozyVideoTypes with CozyVideoRuntime with CozyVideoCommand with CozyVideoNarration with CozyVideoToolValidation with CozyVideoTranscription with CozyVideoReviewEvidence with CozyVideoRdf with CozyVideoRenderWorkspace with CozyVideoRenderTemplates with CozyVideoPlanning with CozyVideoPresentation =>
  import CozyVideoBuildReplay.{MediaDuration, PartMediaTimeline}
  private[video] def _build_project(plan: VideoPlan, runner: VideoProcessRunner): VideoBuildResult = {
    plan.credits.requireValid()
    val partoutputs = plan.parts.filter(_.renderable).map(_.outputPath)
    if (partoutputs.isEmpty)
      RAISE.invalidArgumentFault("No rendered video part outputs found for final assembly.")
    partoutputs.foreach { path =>
      if (!Files.isRegularFile(path))
        RAISE.invalidArgumentFault(s"Missing rendered part output: $path. Run: cozy video render <project-file> --renderer=remotion|simple-java2d")
    }
    val workspace = _assembly_workspace(plan.projectRoot)
    val concatlist = _ffmpeg_concat_list_path(workspace)
    _write_ffmpeg_concat_list(plan.projectRoot, plan.execution, concatlist, partoutputs)
    _run_build_ffmpeg(plan.projectRoot, plan.execution, concatlist, partoutputs, plan.outputPath, runner)
    if (!Files.isRegularFile(plan.outputPath))
      RAISE.invalidArgumentFault(s"ffmpeg concat/mux did not create output: ${plan.outputPath}")
    val ffprobe = _run_build_ffprobe(plan.projectRoot, plan.execution, plan.outputPath, runner)
    val summary = _ffprobe_summary(ffprobe)
    _require_final_media_presentation(summary, plan.outputPath)
    val creditfiles = _write_credit_outputs(plan)
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

  private def _assembly_workspace(projectroot: Path): Path = {
    val stageparent = projectroot.resolve("target/cozy-video/ffmpeg").toAbsolutePath.normalize()
    Files.createDirectories(stageparent)
    Files.createTempDirectory(stageparent, "assembly-").toAbsolutePath.normalize()
  }

  private[video] def _ffmpeg_concat_list_path(workspace: Path): Path =
    workspace.resolve("concat.txt")

  private[video] def _write_credit_outputs(plan: VideoPlan): Option[CozyVideoCredits.OutputFiles] =
    plan.credits.profile.map { _ =>
      CozyVideoCredits.write(plan.outputPath.getParent.resolve("credits"), plan.credits)
    }

  private[video] def _write_ffmpeg_concat_list(
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

  private[video] def _ffmpeg_concat_escape(value: String): String =
    value.replace("\\", "\\\\").replace("'", "\\'")

  private[video] def _run_build_ffmpeg(
    projectroot: Path,
    execution: VideoExecutionConfig,
    concatlist: Path,
    executionparts: Vector[Path],
    output: Path,
    runner: VideoProcessRunner
  ): Unit = {
    Files.createDirectories(output.getParent)
    val timelines = if (executionparts.size > 1) executionparts.map { input =>
      val summary = try _ffprobe_summary(_run_build_ffprobe(projectroot, execution, input, runner))
      catch { case NonFatal(error) => RAISE.invalidArgumentFault(s"Input media probe failed for $input: ${error.getMessage}") }
      _part_media_timeline(summary, input)
    } else Vector.empty
    if (!timelines.exists(_.hasaudio)) {
      _run_assembly_stage(projectroot, execution, runner, output, "final copy mux", Vector(
        "-y", "-copyts", "-f", "concat", "-safe", "0", "-i", _execution_path(projectroot, execution, concatlist),
        "-c", "copy", "-movie_timescale", "48000", _execution_path(projectroot, execution, output)), executionparts)
    } else {
      val directory = concatlist.getParent.resolve("presentation")
      Files.createDirectories(directory)
      val allocations = _media_sample_allocations(timelines, executionparts)
      val videos = executionparts.zipWithIndex.map { case (input, index) =>
        val staged = directory.resolve(f"video-${index + 1}%02d.mp4")
        _run_assembly_stage(projectroot, execution, runner, staged, s"video extraction from $input", Vector(
          "-y", "-copyts", "-i", _execution_path(projectroot, execution, input), "-map", "0:v:0", "-an", "-c:v", "copy",
          "-movie_timescale", "48000", _execution_path(projectroot, execution, staged)), Vector(input))
        staged
      }
      val waves = executionparts.zip(timelines).zip(allocations).zipWithIndex.map { case (((input, timeline), samples), index) =>
        val staged = directory.resolve(f"audio-${index + 1}%02d.wav")
        val source = if (timeline.hasaudio) Vector("-i", _execution_path(projectroot, execution, input), "-map", "0:a:0")
          else Vector("-f", "lavfi", "-i", "anullsrc=r=48000:cl=stereo")
        _run_assembly_stage(projectroot, execution, runner, staged, s"PCM allocation for $input", Vector("-y") ++ source ++ Vector(
          "-vn", "-af", s"aresample=48000:first_pts=0,apad,atrim=end_sample=$samples,asetpts=PTS-STARTPTS",
          "-c:a", "pcm_s16le", "-ar", "48000", "-ac", "2", _execution_path(projectroot, execution, staged)), Vector(input))
        staged
      }
      val videolist = directory.resolve("video-concat.txt")
      val videolines = videos.zip(timelines).map { case (video, timeline) =>
        s"file '${_ffmpeg_concat_escape(_execution_path(projectroot, execution, video))}'\nduration ${timeline.duration.decimal}"
      }
      Files.writeString(videolist, videolines.mkString("", "\n", "\n"), StandardCharsets.UTF_8)
      val audiolist = directory.resolve("pcm-concat.txt")
      _write_ffmpeg_concat_list(projectroot, execution, audiolist, waves)
      val combined = directory.resolve("combined.wav")
      _run_assembly_stage(projectroot, execution, runner, combined, "lossless PCM concatenation", Vector(
        "-y", "-f", "concat", "-safe", "0", "-i", _execution_path(projectroot, execution, audiolist),
        "-c:a", "copy", _execution_path(projectroot, execution, combined)))
      _run_assembly_stage(projectroot, execution, runner, output, "final PCM/AAC mux", Vector(
        "-y", "-copyts", "-f", "concat", "-safe", "0", "-i", _execution_path(projectroot, execution, videolist),
        "-i", _execution_path(projectroot, execution, combined), "-map", "0:v:0", "-map", "1:a:0",
        "-c:v", "copy", "-c:a", "aac", "-b:a", "320k", "-cutoff", "18000", "-ar", "48000", "-ac", "2",
        "-movie_timescale", "48000", "-movflags", "+faststart", _execution_path(projectroot, execution, output)))
    }
  }

  private def _run_assembly_stage(
    projectroot: Path, execution: VideoExecutionConfig, runner: VideoProcessRunner,
    output: Path, context: String, args: Vector[String], inputs: Vector[Path] = Vector.empty
  ): Unit = {
    val mounts = _selected_path_mounts(projectroot, inputs, Vector(output))
    val result = runner.run(_execution_command(projectroot, execution, "ffmpeg", args, mounts = mounts), projectroot)
    Files.writeString(output.resolveSibling(s"${output.getFileName}.stderr.txt"), result.stderr, StandardCharsets.UTF_8)
    if (!result.isSuccess)
      RAISE.invalidArgumentFault(s"ffmpeg concat/mux failed: $context: ${result.stderr.trim}")
    if (!Files.isRegularFile(output))
      RAISE.invalidArgumentFault(s"ffmpeg concat/mux did not create output: $output ($context); ${result.stderr.trim}")
  }

  private def _media_sample_allocations(timelines: Vector[PartMediaTimeline], inputs: Vector[Path]): Vector[Long] = {
    var cumulative = MediaDuration(0, 1)
    var previous = BigInt(0)
    timelines.zip(inputs).map { case (timeline, input) =>
      cumulative = cumulative + timeline.duration
      val total = cumulative.sampleFloor
      val count = total - previous
      if (count <= 0 || !count.isValidLong)
        _media_timeline_failure(input, "video duration cannot allocate a positive Long-representable 48k sample count")
      previous = total
      count.toLong
    }
  }

  private def _media_timeline_failure(input: Path, message: String): Nothing =
    RAISE.invalidArgumentFault(s"Invalid media presentation for $input: $message. Rerender this part before final assembly.")

  private def _media_field(stream: Json, name: String): Option[String] =
    stream.hcursor.downField(name).focus.flatMap(value => value.asString.orElse(value.asNumber.map(_.toString)))

  private def _media_decimal(value: String, input: Path, label: String): MediaDuration = {
    val decimal = Try(BigDecimal(value).bigDecimal).getOrElse(_media_timeline_failure(input, s"invalid $label: $value"))
    val scale = decimal.scale
    if (scale >= 0) MediaDuration(BigInt(decimal.unscaledValue), BigInt(10).pow(scale))
    else MediaDuration(BigInt(decimal.unscaledValue) * BigInt(10).pow(-scale), 1)
  }

  private def _media_time_base(stream: Json, input: Path): MediaDuration = {
    val text = _media_field(stream, "time_base").getOrElse(_media_timeline_failure(input, "missing time_base"))
    val fields = text.split("/", -1)
    val rational = if (fields.length == 2) Try(MediaDuration(BigInt(fields(0)), BigInt(fields(1)))).toOption else None
    rational.filter(value => value.numerator > 0 && value.denominator > 0)
      .getOrElse(_media_timeline_failure(input, s"invalid time_base: $text"))
  }

  private def _media_duration(stream: Json, input: Path): MediaDuration = {
    val duration = _media_field(stream, "duration_ts") match {
      case Some(text) =>
        val ticks = Try(BigInt(text)).getOrElse(_media_timeline_failure(input, s"invalid duration_ts: $text"))
        val base = _media_time_base(stream, input)
        MediaDuration(ticks * base.numerator, base.denominator)
      case None => _media_decimal(_media_field(stream, "duration").getOrElse(_media_timeline_failure(input, "missing stream duration")), input, "duration")
    }
    if (duration.numerator <= 0) _media_timeline_failure(input, "stream duration must be positive")
    duration
  }

  private def _media_origin(stream: Json, input: Path): MediaDuration =
    _media_field(stream, "start_pts") match {
      case Some(text) =>
        val ticks = Try(BigInt(text)).getOrElse(_media_timeline_failure(input, s"invalid start_pts: $text"))
        val base = _media_time_base(stream, input)
        MediaDuration(ticks * base.numerator, base.denominator)
      case None => _media_decimal(_media_field(stream, "start_time").getOrElse(_media_timeline_failure(input, "missing stream presentation origin")), input, "start_time")
    }

  private def _media_streams(summary: Json, kind: String): Vector[Json] =
    summary.hcursor.get[Vector[Json]]("streams").toOption.getOrElse(Vector.empty)
      .filter(_.hcursor.get[String]("codec_type").toOption.contains(kind))

  private def _part_media_timeline(summary: Json, input: Path): PartMediaTimeline = {
    val videos = _media_streams(summary, "video")
    if (videos.size != 1) _media_timeline_failure(input, "expected exactly one usable video stream")
    val video = videos.head
    val duration = _media_duration(video, input)
    if (_media_origin(video, input).numerator != 0) _media_timeline_failure(input, "video presentation must start at zero")
    val audios = _media_streams(summary, "audio")
    audios.foreach { audio =>
      val origin = _media_origin(audio, input)
      if (origin.numerator < 0) _media_timeline_failure(input, "audio presentation origin must be nonnegative")
      val end = origin + _media_duration(audio, input)
      if (!(end <= (duration + MediaDuration(1, 48000))))
        _media_timeline_failure(input, "audio presentation overruns video by more than one 48k sample")
    }
    PartMediaTimeline(duration, audios.nonEmpty)
  }

  private[video] def _require_final_media_presentation(summary: Json, output: Path): Unit = {
    if (!CozyVideoStoryboardRecordValidation.ffprobeSummaryValid(summary))
      RAISE.invalidArgumentFault(s"Final output $output ffprobe summary has no finite positive duration and video stream")
    if (_media_streams(summary, "audio").nonEmpty) _part_media_timeline(summary, output)
  }

  private[video] def _run_build_ffprobe(
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
    ), mounts = _selected_path_mounts(projectroot, Vector(output)))
    val result = runner.run(args, projectroot)
    if (!result.isSuccess)
      RAISE.invalidArgumentFault(s"ffprobe validation failed: ${result.stderr.trim}")
    result
  }

  private[video] def _ffprobe_summary(result: VideoCommandResult): Json =
    parser.parse(result.stdout).fold(
      e => RAISE.invalidArgumentFault(s"ffprobe returned invalid JSON: ${e.getMessage}"),
      identity
    )

  private[video] def _write_project_manifest(
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
      "partOutputs" -> Json.fromValues(partoutputs.map(x => Json.fromString(x.toString))),
      "toolMode" -> Json.fromString(plan.execution.toolMode.label),
      "dockerImage" -> Json.fromString(plan.execution.dockerImage),
      "concatListPath" -> Json.fromString(concatlist.toString),
      "encoding" -> _encoding_json(plan.encoding),
      "creditProfile" -> plan.credits.profileId.map(Json.fromString).getOrElse(Json.Null),
      "creditsPath" -> creditfiles.map(x => Json.fromString(x.jsonFile.toString)).getOrElse(Json.Null),
      "creditEvidence" -> Json.fromValues(plan.credits.items.flatMap(_.evidence).distinct.sorted.map(Json.fromString)),
      "creditTermsUrls" -> Json.fromValues(plan.credits.items.flatMap(_.item.termsUrl).distinct.sorted.map(Json.fromString)),
      "ffprobe" -> ffprobe
    )
    Files.writeString(plan.manifestPath, json.spaces2, StandardCharsets.UTF_8)
  }

  private[video] def _encoding_json(encoding: ResolvedEncodingSettings): Json =
    Json.obj(
      "policy" -> Json.fromString(encoding.policy.name),
      "fps" -> Json.fromInt(encoding.fps),
      "width" -> Json.fromInt(encoding.width),
      "height" -> Json.fromInt(encoding.height),
      "crf" -> Json.fromInt(encoding.crf),
      "x264Preset" -> encoding.x264Preset.map(Json.fromString).getOrElse(Json.Null)
    )

  private[video] def _replay_script(
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

  private[video] def _validate_replay_output(output: Option[Path]): Unit =
    output.foreach { path =>
      if (!path.getFileName.toString.toLowerCase(java.util.Locale.ROOT).endsWith(".webm"))
        RAISE.invalidArgumentFault(s"Playwright replay recording output must use .webm: $path")
    }

  private[video] def _validate_replay_docker_paths(
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

  private[video] def _replay_work_dir(projectroot: Path, scriptfile: Path): Path =
    projectroot.resolve("target/cozy-video/replay").resolve(_file_segment_id(_basename(scriptfile), "replay script stem")).normalize()

  private[video] def _write_replay_workspace(
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

  private[video] def _replay_command(
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

  private[video] def _replay_command_preview(projectroot: Path, execution: VideoExecutionConfig, workdir: Path): String =
    _replay_command_args(projectroot, execution, workdir).map(_shell_quote).mkString(" ")

  private[video] def _replay_command_args(projectroot: Path, execution: VideoExecutionConfig, workdir: Path): Vector[String] = {
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

  private[video] val _playwright_replay_mjs: String =
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

  private[video] def _write_replay_manifest(
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
}
