package cozy.video

import cozy.generation.CozyFileUpdatePolicy
import io.circe.Json
import io.circe.parser
import java.nio.charset.StandardCharsets
import java.nio.file.{Files, LinkOption, Path}
import org.goldenport.RAISE
import scala.collection.JavaConverters._
import scala.util.control.NonFatal

/*
 * @since   Oct.  4, 2026
 * @version Oct.  4, 2026
 * @author  ASAMI, Tomoharu
 */
private[cozy] object CozyVideoMediaBuild {
  import CozyVideoImplementation._
  private final case class MediaPart(part: VideoPartPlan, engine: String, inputs: Vector[Path])
  private final case class MediaGraph(plan: VideoPlan, core: Vector[Path], parts: Vector[MediaPart]) {
    def inputs: Vector[Path] = (core ++ parts.flatMap(_.inputs)).distinct
  }
}

private[cozy] trait CozyVideoMediaBuild {
  self: CozyVideoImplementation.type =>
  import CozyVideoMediaBuild.{MediaGraph, MediaPart}

  private[cozy] def mediaProducerInputs(projectFile: Path): Vector[Path] =
    _media_graph(_plan(projectFile, None, None), Vector.empty).inputs

  private[cozy] def mediaCurrentnessFailure(projectFile: Path, additionalInputs: Vector[Path]): Option[String] =
    try {
      val graph = _media_graph(_plan(projectFile, None, None), additionalInputs)
      val parts = _media_render_parts(graph)
      if (parts.nonEmpty) Some(s"Video render inputs or credit artifacts require production: ${parts.map(_.part.id).mkString(", ")}")
      else if (!_media_final_reusable(graph)) Some(s"Video final assembly requires production: ${graph.plan.outputPath}")
      else None
    } catch { case NonFatal(error) => Some(error.getMessage) }

  private[cozy] def buildMedia(
    config: BuildConfig,
    tools: VideoToolRegistry,
    runner: VideoProcessRunner,
    additionalInputs: Vector[Path]
  ): String = {
    val graph = _media_graph(_plan(config.projectFile, config.toolMode, config.dockerImage), additionalInputs)
    val selected = _media_render_parts(graph)
    val assemble = selected.nonEmpty || !_media_final_reusable(graph)
    if (config.dryRun)
      return s"Cozy Video Media Build (dry-run)\nrender: ${selected.map(_.part.id).mkString(", ")}\nassemble: $assemble"
    val plan = graph.plan
    val products = if (assemble) _media_products(plan) else _media_credit_paths(plan)
    // Preflight every producer destination before renderers can write any output.
    CozyVideoStoryboardArtifacts.preflightProduction(plan.projectRoot, _media_products(plan), graph.inputs.toSet)
    val before = _media_observations(graph.inputs)
    CozyVideoStoryboardArtifacts.preserveDuringProduction(plan.projectRoot, products, graph.inputs.toSet) {
      selected.foreach { selectedpart =>
        val renderconfig = RenderConfig(plan.projectFile, selectedpart.engine, Some(selectedpart.part.id),
          config.checkTools, config.toolMode, config.dockerImage)
        render(renderconfig, tools, runner)
        _media_probe(plan, selectedpart.part.outputPath, runner)
      }
      val result = if (assemble) {
        val built = build(config.copy(mode = if (_media_native(plan)) Some("final") else config.mode), tools, runner)
        _media_probe(plan, plan.outputPath, runner)
        built
      } else s"Cozy Video Media Build\nstatus: reused\noutput: ${plan.outputPath}"
      CozyVideoCredits.write(plan.outputPath.getParent.resolve("credits"), plan.credits)
      val aftergraph = _media_graph(_plan(config.projectFile, config.toolMode, config.dockerImage), additionalInputs)
      if (_media_observations(aftergraph.inputs) != before)
        RAISE.invalidArgumentFault(s"Video producer inputs changed during Media build: ${plan.projectFile}")
      val findings = verifyCredits(plan.projectFile)
      if (findings.nonEmpty)
        RAISE.invalidArgumentFault(findings.mkString("Video Media build credit verification failed: ", "; ", ""))
      result
    }
  }

  private def _media_graph(plan: VideoPlan, additionalinputs: Vector[Path]): MediaGraph = {
    plan.credits.requireValid()
    if (plan.parts.isEmpty) RAISE.invalidArgumentFault(s"Video Media producer has no declared parts: ${plan.projectFile}")
    val core = (Vector(plan.projectFile) ++ plan.projectContext.layers.flatMap(_.files.map(_.path)) ++
      plan.credits.profile.map(_.path).toVector ++ plan.credits.selection.flatMap(_.configPath).toVector ++ additionalinputs ++
      _media_configured_assets(plan) ++ (if (_media_native(plan)) _media_storyboard_inputs(plan) else Vector.empty)).distinct
    core.foreach(_media_require_input)
    val parts = plan.parts.map { part =>
      if (!part.renderable)
        RAISE.invalidArgumentFault(s"Video Media producer part ${part.id} has missing/unsupported script or steps: ${part.scriptPath.orElse(part.stepsPath).getOrElse(plan.projectFile)}")
      val configured = plan.project.parts(part.index - 1)
      val engine = configured.renderer.orElse(plan.project.renderer).map(_.engineOrDefault).getOrElse("legacy")
      if (!Set("remotion", "simple-java2d").contains(engine))
        RAISE.invalidArgumentFault(s"Video Media producer is unavailable for part ${part.id}: renderer=$engine; select remotion or simple-java2d")
      val script = part.script.get
      val audiodir = part.audioDir.getOrElse(RAISE.invalidArgumentFault(s"Missing audio directory for Video part ${part.id}"))
      _media_require_local(plan.projectRoot, audiodir.resolve("manifest.json"))
      val audio = _load_audio_input(part.id, audiodir, script)
      val combined = if (engine == "simple-java2d") Vector(_load_simple_java2d_input(part.id, audiodir, part, script).combinedFile) else Vector.empty
      val characters = if (engine == "remotion" && (_is_character_dialogue(part, script, plan.project.renderer) || part.partType == "web-demo")) {
        val bases = _character_asset_bases(plan.projectRoot, part.scriptPath.get)
        val characterpaths = script.characters.toVector.flatMap { case (id, character) =>
          Vector("asset", "mouthClosedAsset", "mouthOpenAsset").flatMap { field =>
            _json_string(character, field).map(authored => _resolve_character_dialogue_asset(plan.projectRoot, bases, authored, s"character $id", field))
          }
        }
        val visuals = if (_is_character_dialogue(part, script, plan.project.renderer)) script.expandedScenes.flatMap { scene =>
          _json_string(scene.visual, "image").map(authored => _resolve_character_dialogue_asset(plan.projectRoot, bases, authored,
            s"scene ${scene.id.getOrElse("(no id)")}", "visual.image"))
        } else Vector.empty
        characterpaths ++ visuals
      } else Vector.empty
      val recording = if (part.partType == "web-demo") Vector(_web_demo_recording_path(plan.projectRoot, part)) else Vector.empty
      val local = (part.scriptPath.toVector ++ part.stepsPath.toVector ++ Vector(audio.manifestPath) ++ audio.files ++ combined ++ characters ++ recording).distinct
      local.foreach(path => _media_require_local(plan.projectRoot, path))
      (audio.files ++ combined).foreach { path =>
        try _read_wav(path)
        catch { case NonFatal(error) => RAISE.invalidArgumentFault(s"Invalid required audio WAV for Video part ${part.id}: $path: ${error.getMessage}") }
      }
      if (engine == "remotion") _validate_declarative_diagrams(script)
      MediaPart(part, engine, (core ++ local).distinct)
    }
    MediaGraph(plan, core, parts)
  }

  private def _media_configured_assets(plan: VideoPlan): Vector[Path] = {
    val resolved = plan.assets.filterNot(_.status == "optional-missing").map(_.path)
    val semantic = plan.project.assets.toVector.flatMap(_.semantic.toVector).flatMap { case (id, entry) =>
      val path = _resolve_project_relative_path(plan.projectRoot, entry.path, s"video asset $id")
      if (Files.exists(path, LinkOption.NOFOLLOW_LINKS)) Some(path) else None
    }
    (resolved ++ semantic).distinct.map { path => _media_require_local(plan.projectRoot, path); path }
  }

  private def _media_render_parts(graph: MediaGraph): Vector[MediaPart] = {
    val companionsvalid = _media_credits_valid(graph.plan) && _media_manifest_valid(graph.plan)
    graph.parts.filter { selected =>
      !companionsvalid || !_media_part_valid(graph.plan, selected) || !_media_reuse(selected.inputs, selected.part.outputPath)
    }
  }

  private def _media_part_valid(plan: VideoPlan, selected: MediaPart): Boolean =
    _media_nonempty(selected.part.outputPath) && _media_json(selected.part.manifestPath).exists { manifest =>
      manifest.hcursor.get[String]("partId").toOption.contains(selected.part.id) &&
        manifest.hcursor.get[String]("renderer").toOption.contains(selected.engine) &&
        manifest.hcursor.get[Int]("sceneCount").toOption.contains(selected.part.script.get.expandedScenes.size) &&
        (selected.engine != "remotion" || manifest.hcursor.downField("credits").focus.exists(CozyVideoCredits.validRendererProps(_, plan.credits)))
    }

  private def _media_final_reusable(graph: MediaGraph): Boolean = {
    val plan = graph.plan
    val valid = if (_media_native(plan)) _media_storyboard_final_valid(plan) else _media_manifest_valid(plan)
    valid && _media_nonempty(plan.outputPath) && _media_credits_valid(plan) &&
      _media_reuse((graph.core ++ graph.parts.map(_.part.outputPath)).distinct, plan.outputPath)
  }

  private def _media_manifest_valid(plan: VideoPlan): Boolean =
    if (_media_native(plan)) _media_storyboard_final_records_valid(plan)
    else _media_json(plan.manifestPath).exists { manifest =>
      manifest.hcursor.get[String]("status").toOption.contains("validated") &&
        manifest.hcursor.downField("creditProfile").focus.contains(plan.credits.profileId.map(Json.fromString).getOrElse(Json.Null)) &&
        manifest.hcursor.downField("ffprobe").focus.exists(CozyVideoStoryboardRecordValidation.ffprobeSummaryValid)
    }

  private def _media_reuse(inputs: Vector[Path], output: Path): Boolean =
    CozyFileUpdatePolicy.evaluate(CozyFileUpdatePolicy.Request(CozyFileUpdatePolicy.Intent.Generation,
      Some("video-media-producer"), _media_observations(inputs), _media_observation(output), force = false))
      .isInstanceOf[CozyFileUpdatePolicy.Decision.Reuse]

  private def _media_observations(paths: Vector[Path]): Vector[CozyFileUpdatePolicy.FileObservation] =
    paths.map(_.toAbsolutePath.normalize()).distinct.sortBy(_.toString).map(_media_observation)

  private def _media_observation(path: Path): CozyFileUpdatePolicy.FileObservation = {
    import CozyFileUpdatePolicy._
    val state = if (!Files.exists(path, LinkOption.NOFOLLOW_LINKS)) FileState.Absent
      else if (Files.isSymbolicLink(path) || !Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) FileState.Invalid("not a direct regular file")
      else FileState.Valid(try Some(Files.getLastModifiedTime(path, LinkOption.NOFOLLOW_LINKS)) catch { case NonFatal(_) => None })
    FileObservation(path.toAbsolutePath.normalize().toString, state)
  }

  private def _media_require_local(root: Path, path: Path): Unit = {
    if (!path.toAbsolutePath.normalize().startsWith(root.toAbsolutePath.normalize()))
      RAISE.invalidArgumentFault(s"Video producer input escapes the selected project root: $path")
    _media_require_input(path)
  }

  private def _media_require_input(path: Path): Unit = {
    val normalized = path.toAbsolutePath.normalize()
    var current = normalized.getRoot
    normalized.iterator().asScala.foreach { segment =>
      current = current.resolve(segment)
      if (Files.isSymbolicLink(current)) RAISE.invalidArgumentFault(s"Video producer input has an unsafe symbolic-link path: $current")
    }
    if (!Files.isRegularFile(normalized, LinkOption.NOFOLLOW_LINKS) || !Files.isReadable(normalized))
      RAISE.invalidArgumentFault(s"Missing or invalid required Video producer input: $normalized")
  }

  private def _media_native(plan: VideoPlan): Boolean = plan.project.parts.exists(_.storyboard.isDefined)
  private def _media_nonempty(path: Path): Boolean =
    Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS) && !Files.isSymbolicLink(path) && Files.size(path) > 0

  private def _media_json(path: Path): Option[Json] =
    if (!_media_nonempty(path)) None
    else try parser.parse(Files.readString(path, StandardCharsets.UTF_8)).toOption catch { case NonFatal(_) => None }

  private def _media_credit_paths(plan: VideoPlan): Vector[Path] =
    Vector("credits.json", "credits.md", "renderer-props.json").map(plan.outputPath.getParent.resolve("credits").resolve(_))

  private def _media_credits_valid(plan: VideoPlan): Boolean = {
    val paths = _media_credit_paths(plan)
    _media_json(paths(0)).exists(CozyVideoCredits.validJson(_, plan.credits)) &&
      Files.isRegularFile(paths(1), LinkOption.NOFOLLOW_LINKS) && !Files.isSymbolicLink(paths(1)) &&
      CozyVideoCredits.validMarkdown(Files.readString(paths(1), StandardCharsets.UTF_8), plan.credits) &&
      _media_json(paths(2)).exists(CozyVideoCredits.validRendererProps(_, plan.credits))
  }

  private def _media_products(plan: VideoPlan): Vector[Path] =
    (plan.parts.flatMap(part => Vector(part.outputPath, part.manifestPath)) ++ _media_credit_paths(plan) ++
      (if (_media_native(plan)) _media_storyboard_products(plan) else Vector(plan.outputPath, plan.manifestPath))).distinct

  private def _media_probe(plan: VideoPlan, output: Path, runner: VideoProcessRunner): Unit = {
    val summary = _ffprobe_summary(_run_build_ffprobe(plan.projectRoot, plan.execution, output, runner))
    if (!CozyVideoStoryboardRecordValidation.ffprobeSummaryValid(summary))
      RAISE.invalidArgumentFault(s"Video Media producer output has no finite positive duration and video stream: $output")
  }
}
