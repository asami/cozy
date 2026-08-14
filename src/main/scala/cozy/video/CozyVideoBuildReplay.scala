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
private[cozy] trait CozyVideoBuildReplay {
  self: CozyVideoTypes with CozyVideoRuntime with CozyVideoCommand with CozyVideoNarration with CozyVideoToolValidation with CozyVideoTranscription with CozyVideoReviewEvidence with CozyVideoRdf with CozyVideoRenderWorkspace with CozyVideoRenderTemplates with CozyVideoPlanning with CozyVideoPresentation =>
  private[video] def _build_project(plan: VideoPlan, runner: VideoProcessRunner): VideoBuildResult = {
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

  private[video] def _ffmpeg_concat_list_path(projectroot: Path): Path =
    projectroot.resolve("target/cozy-video/ffmpeg/concat.txt").normalize()

  private[video] def _build_execution_parts(
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

  private[video] def _build_execution_output(
    projectroot: Path,
    execution: VideoExecutionConfig,
    output: Path
  ): Path =
    execution.toolMode match {
      case VideoToolMode.Docker => projectroot.resolve("target/cozy-video/ffmpeg/rendered.mp4").normalize()
      case _ => output
    }

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
    ))
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
}
