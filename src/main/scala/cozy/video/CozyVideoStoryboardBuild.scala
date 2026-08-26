package cozy.video

import io.circe.Json
import io.circe.parser
import java.nio.charset.StandardCharsets
import java.nio.file.{Files, LinkOption, Path}
import java.security.MessageDigest
import org.goldenport.RAISE
import scala.collection.JavaConverters._
import scala.util.control.NonFatal

/*
 * @since   Aug. 26, 2026
 * @version Aug. 26, 2026
 * @author  ASAMI, Tomoharu
 */
private[cozy] trait CozyVideoStoryboardBuild {
  self: CozyVideoImplementation.type =>

  private final case class StoryboardBuildPart(
    partplan: VideoPartPlan,
    sourcepath: Path,
    storyboard: Storyboard,
    storyboardidentity: String,
    projectedstoryboardidentity: String,
    storyboardsection: Option[String],
    handoffpath: Path,
    handoff: Json,
    handoffidentity: String
  )

  private final case class StoryboardBuildMode(
    name: String,
    schema: String,
    outputpath: Path,
    manifestpath: Path
  )

  private val _confirmation_mode = "confirmation"
  private val _final_mode = "final"
  private val _handoff_schema = "cozy.video.storyboard-build-handoff.v1"

  private[video] def _build_storyboard_mode(
    config: BuildConfig,
    plan: VideoPlan,
    tools: VideoToolRegistry,
    runner: VideoProcessRunner
  ): String = {
    val mode = _storyboard_build_mode(config, plan)
    val parts = _storyboard_build_parts(plan)
    val cacheinput = _storyboard_cache_input(plan, mode, parts)
    if (mode.name == _final_mode) {
      val confirmation = _storyboard_build_mode_for(plan, _confirmation_mode)
      val confirmationcacheinput = _storyboard_cache_input(plan, confirmation, parts)
      _validate_final_confirmation(plan, confirmation, parts, confirmationcacheinput)
    }
    val context = VideoToolContext(plan.projectFile, plan.projectRoot, plan.project, plan.execution)
    val checks =
      if (config.checkTools || (!config.dryRun && plan.execution.toolMode == VideoToolMode.Docker))
        tools.checks(context)
      else
        Vector.empty
    if (config.dryRun)
      _render_storyboard_build_dry_run(config, plan, mode, cacheinput, checks)
    else {
      _validate_build_tools(plan.execution, checks)
      _prepare_storyboard_mode_targets(plan, mode)
      _write_storyboard_handoffs(plan, parts)
      if (_valid_storyboard_mode_cache(plan, mode, parts, cacheinput))
        _render_storyboard_build_cache_hit(plan, mode, cacheinput)
      else {
        val modeplan = plan.copy(outputPath = mode.outputpath, manifestPath = mode.manifestpath)
        _validate_storyboard_part_outputs(plan)
        val result = _build_project(modeplan, runner)
        _require_direct_regular_file(mode.outputpath, s"Storyboard ${mode.name} output")
        _write_storyboard_part_artifacts(plan, mode)
        val manifest = _storyboard_mode_manifest(plan, mode, parts, cacheinput, _file_identity(mode.outputpath))
        _write_storyboard_json(mode.manifestpath, manifest, _target_root(plan.projectRoot), s"Storyboard ${mode.name} manifest")
        _render_storyboard_build_cache_miss(result, mode, cacheinput, parts)
      }
    }
  }

  private def _storyboard_build_mode(config: BuildConfig, plan: VideoPlan): StoryboardBuildMode =
    config.mode.map(_.trim.toLowerCase(java.util.Locale.ROOT)) match {
      case Some(value) if value == _confirmation_mode => _storyboard_build_mode_for(plan, _confirmation_mode)
      case Some(value) if value == _final_mode => _storyboard_build_mode_for(plan, _final_mode)
      case Some(value) =>
        _storyboard_build_failure(s"Unsupported Storyboard video build mode: $value. Expected confirmation or final.")
      case None =>
        _storyboard_build_failure("Storyboard video build requires --mode confirmation or --mode final.")
    }

  private def _storyboard_build_mode_for(plan: VideoPlan, mode: String): StoryboardBuildMode = {
    val targetroot = _target_root(plan.projectRoot)
    val confirmationroot = targetroot.resolve(_confirmation_mode).normalize()
    mode match {
      case value if value == _confirmation_mode =>
        StoryboardBuildMode(
          _confirmation_mode,
          "cozy.video.confirmation.v1",
          confirmationroot.resolve("confirmation.mp4").normalize(),
          confirmationroot.resolve("manifest.json").normalize()
        )
      case value if value == _final_mode =>
        if (plan.outputPath.toAbsolutePath.normalize().startsWith(confirmationroot))
          _storyboard_build_failure(s"Storyboard final output must not be under the confirmation root: ${plan.outputPath}")
        StoryboardBuildMode(
          _final_mode,
          "cozy.video.final.v1",
          plan.outputPath,
          targetroot.resolve(_final_mode).resolve("manifest.json").normalize()
        )
      case value =>
        _storyboard_build_failure(s"Unsupported Storyboard video build mode: $value. Expected confirmation or final.")
    }
  }

  private def _storyboard_build_parts(plan: VideoPlan): Vector[StoryboardBuildPart] = {
    val review = plan.project.storyboardReview.getOrElse(
      _storyboard_build_failure("Storyboard video build requires storyboardReview approval.")
    )
    _validate_storyboard_review_current(plan)
    val reviewpath = _safe_storyboard_build_source(plan.projectRoot, review.source, "storyboardReview.source")
    val approvedidentity = review.approvedIdentity
    plan.project.parts.zip(plan.parts).collect {
      case (part, partplan) if part.storyboard.isDefined =>
        val sourcepath = _safe_storyboard_build_source(plan.projectRoot, part.storyboard.get, s"Storyboard part ${partplan.id}")
        if (sourcepath != reviewpath)
          _storyboard_build_failure(
            s"Storyboard build part ${partplan.id} must select storyboardReview.source: expected ${_display_path(plan.projectRoot, reviewpath)}, found ${_display_path(plan.projectRoot, sourcepath)}"
          )
        val result = loadStoryboard(sourcepath)
        if (!result.isValid)
          _storyboard_build_failure(
            s"Storyboard build source is invalid for part ${partplan.id}: ${result.diagnostics.map(_.render).mkString("; ")}"
          )
        val sourcestoryboard = result.storyboard.get
        val storyboardidentity = storyboardIdentity(sourcestoryboard)
        if (storyboardidentity != approvedidentity)
          _storyboard_build_failure(
            s"Storyboard build part ${partplan.id} identity $storyboardidentity does not equal storyboardReview.approvedIdentity $approvedidentity"
          )
        val storyboardsection = part.storyboardSection
        val storyboard = _select_storyboard_section(sourcestoryboard, storyboardsection)
        val projectedstoryboardidentity = storyboardIdentity(storyboard)
        val handoffpath = _storyboard_handoff_path(plan.projectRoot, partplan.id)
        val handoffpayload = _storyboard_handoff_payload(
          partplan.id,
          storyboard,
          storyboardidentity,
          projectedstoryboardidentity,
          storyboardsection
        )
        val handoffidentity = _json_identity(handoffpayload)
        val handoff = handoffpayload.mapObject(_.add("identity", Json.fromString(handoffidentity)))
        StoryboardBuildPart(
          partplan,
          sourcepath,
          storyboard,
          storyboardidentity,
          projectedstoryboardidentity,
          storyboardsection,
          handoffpath,
          handoff,
          handoffidentity
        )
    }.toVector
  }

  private def _select_storyboard_section(storyboard: Storyboard, storyboardsection: Option[String]): Storyboard =
    storyboardsection match {
      case Some(section) =>
        val scenes = storyboard.scenes.filter(_.section == section)
        if (scenes.isEmpty)
          _storyboard_build_failure(s"Storyboard section $section selects no scenes")
        storyboard.copy(scenes = scenes)
      case None => storyboard
    }

  private def _safe_storyboard_build_source(projectroot: Path, raw: String, label: String): Path = {
    val path = _resolve_project_relative_path(projectroot, raw, label)
    val targetroot = _target_root(projectroot)
    if (path.startsWith(targetroot))
      _storyboard_build_failure(s"$label must not select generated target/cozy-video data: $raw")
    _assert_no_symlink_components(projectroot, path, label)
    _validate_storyboard_source(path)
    val canonical = try path.toRealPath() catch {
      case NonFatal(error) => _storyboard_build_failure(s"$label cannot be resolved: ${error.getMessage}")
    }
    val canonicalroot = try projectroot.toRealPath() catch {
      case NonFatal(error) => _storyboard_build_failure(s"Storyboard project root cannot be resolved: ${error.getMessage}")
    }
    if (!canonical.startsWith(canonicalroot))
      _storyboard_build_failure(s"$label resolves outside the project root: $raw")
    canonical
  }

  private def _storyboard_handoff_path(projectroot: Path, partid: String): Path = {
    if (!Option(partid).getOrElse("").matches("[A-Za-z0-9][A-Za-z0-9_-]*"))
      _storyboard_build_failure(s"Storyboard part id is unsafe for generated handoff: $partid")
    _target_root(projectroot).resolve("storyboard").resolve(partid).resolve("handoff.json").normalize()
  }

  private def _storyboard_handoff_payload(
    partid: String,
    storyboard: Storyboard,
    storyboardidentity: String,
    projectedstoryboardidentity: String,
    storyboardsection: Option[String]
  ): Json = {
    val canonical = parser.parse(canonicalStoryboardJson(storyboard)).fold(
      error => _storyboard_build_failure(s"Cannot canonicalize Storyboard handoff for part $partid: ${error.getMessage}"),
      identity
    )
    Json.obj(
      "schema" -> Json.fromString(_handoff_schema),
      "partId" -> Json.fromString(partid),
      "storyboardIdentity" -> Json.fromString(storyboardidentity),
      "projectedStoryboardIdentity" -> Json.fromString(projectedstoryboardidentity),
      "storyboardSection" -> storyboardsection.map(Json.fromString).getOrElse(Json.Null),
      "storyboard" -> canonical
    )
  }

  private def _storyboard_cache_input(
    plan: VideoPlan,
    mode: StoryboardBuildMode,
    parts: Vector[StoryboardBuildPart]
  ): Json =
    Json.obj(
      "mode" -> Json.fromString(mode.name),
      "parts" -> _storyboard_part_cache_input(plan),
      "title" -> plan.project.title.map(Json.fromString).getOrElse(Json.Null),
      "characters" -> _storyboard_characters_json(plan.project.characters),
      "storyboards" -> _storyboard_parts_json(parts),
      "handoffs" -> _storyboard_handoffs_json(plan.projectRoot, parts),
      "encoding" -> _storyboard_encoding_json(plan.encoding),
      "renderers" -> _storyboard_renderers_json(plan, parts),
      "visualEffects" -> _storyboard_effects_json(plan),
      "narration" -> _storyboard_narration_json(parts),
      "execution" -> _storyboard_execution_json(plan),
      "assets" -> _storyboard_assets_json(plan),
      "storyboardAssets" -> _storyboard_declared_assets_json(plan, parts),
      "creditsIdentity" -> Json.fromString(plan.credits.digest)
    )

  private def _storyboard_part_cache_input(plan: VideoPlan): Json =
    Json.fromValues(plan.parts.filter(_.renderable).map { part =>
      val configured = plan.project.parts.lift(part.index - 1).getOrElse(
        _storyboard_build_failure(s"Storyboard build part ${part.id} has no configured part definition")
      )
      Json.obj(
        "partId" -> Json.fromString(part.id),
        "configuredPartType" -> Json.fromString(configured.displayType),
        "effectivePartType" -> Json.fromString(part.partType),
        "configuredRenderer" -> _storyboard_renderer_json(configured.renderer),
        "effectiveRenderer" -> Json.fromString(part.renderer),
        "script" -> _storyboard_part_input_identity(plan.projectRoot, part.scriptPath, s"Storyboard part ${part.id} script input"),
        "steps" -> _storyboard_part_input_identity(plan.projectRoot, part.stepsPath, s"Storyboard part ${part.id} steps input"),
        "recording" -> _storyboard_part_input_identity(plan.projectRoot, part.recordDir, s"Storyboard part ${part.id} recording input"),
        "output" -> _storyboard_part_output_identity(plan.projectRoot, part.outputPath, s"Storyboard part ${part.id} output")
      )
    })

  private def _storyboard_part_input_identity(projectroot: Path, path: Option[Path], label: String): Json =
    path match {
      case Some(value) =>
        _require_direct_storyboard_path(projectroot, value, label)
        val status =
          if (!Files.exists(value, LinkOption.NOFOLLOW_LINKS))
            "missing"
          else if (Files.isRegularFile(value, LinkOption.NOFOLLOW_LINKS) && !Files.isSymbolicLink(value))
            "file"
          else if (Files.isDirectory(value, LinkOption.NOFOLLOW_LINKS) && !Files.isSymbolicLink(value))
            "directory"
          else
            _storyboard_build_failure(s"$label must be a direct regular file or directory: $value")
        Json.obj(
          "path" -> Json.fromString(_display_path(projectroot, value)),
          "status" -> Json.fromString(status),
          "identity" -> (status match {
            case "file" => Json.fromString(_file_identity(value))
            case "directory" => Json.fromString(_direct_tree_identity(projectroot, value, label))
            case _ => Json.Null
          })
        )
      case None =>
        Json.obj(
          "path" -> Json.Null,
          "status" -> Json.fromString("not-configured"),
          "identity" -> Json.Null
        )
    }

  private def _storyboard_part_output_identity(projectroot: Path, path: Path, label: String): Json = {
    val root = projectroot.toAbsolutePath.normalize()
    val value = path.toAbsolutePath.normalize()
    if (value.startsWith(root))
      _require_direct_storyboard_path(root, value, label, allowmissingparents = true)
    if (!Files.exists(value, LinkOption.NOFOLLOW_LINKS))
      Json.obj(
        "path" -> Json.fromString(_display_path(root, value)),
        "status" -> Json.fromString("missing"),
        "identity" -> Json.Null
      )
    else {
      _require_direct_regular_file(value, label)
      Json.obj(
        "path" -> Json.fromString(_display_path(root, value)),
        "status" -> Json.fromString("file"),
        "identity" -> Json.fromString(_file_identity(value))
      )
    }
  }

  private def _storyboard_characters_json(characters: Map[String, Json]): Json =
    Json.obj(characters.toVector.sortBy(_._1).map { case (id, character) =>
      id -> _canonical_json(character)
    }: _*)

  private def _canonical_json(json: Json): Json =
    json.asArray.map(values => Json.fromValues(values.map(_canonical_json))).orElse(
      json.asObject.map(values => Json.obj(values.toVector.sortBy(_._1).map { case (key, value) =>
        key -> _canonical_json(value)
      }: _*))
    ).getOrElse(json)

  private def _storyboard_parts_json(parts: Vector[StoryboardBuildPart]): Json =
    Json.fromValues(parts.map { part =>
      Json.obj(
        "partId" -> Json.fromString(part.partplan.id),
        "storyboardIdentity" -> Json.fromString(part.storyboardidentity),
        "projectedStoryboardIdentity" -> Json.fromString(part.projectedstoryboardidentity),
        "storyboardSection" -> part.storyboardsection.map(Json.fromString).getOrElse(Json.Null)
      )
    })

  private def _storyboard_handoffs_json(projectroot: Path, parts: Vector[StoryboardBuildPart]): Json =
    Json.fromValues(parts.map { part =>
      Json.obj(
        "partId" -> Json.fromString(part.partplan.id),
        "path" -> Json.fromString(_display_path(projectroot, part.handoffpath)),
        "identity" -> Json.fromString(part.handoffidentity)
      )
    })

  private def _storyboard_encoding_json(encoding: ResolvedEncodingSettings): Json =
    Json.obj(
      "policy" -> Json.fromString(encoding.policy.name),
      "fps" -> Json.fromInt(encoding.fps),
      "width" -> Json.fromInt(encoding.width),
      "height" -> Json.fromInt(encoding.height),
      "crf" -> Json.fromInt(encoding.crf),
      "x264Preset" -> encoding.x264Preset.map(Json.fromString).getOrElse(Json.Null)
    )

  private def _storyboard_renderers_json(plan: VideoPlan, parts: Vector[StoryboardBuildPart]): Json =
    Json.obj(
      "project" -> _storyboard_renderer_json(plan.project.renderer),
      "parts" -> Json.fromValues(parts.map { part =>
        Json.obj(
          "partId" -> Json.fromString(part.partplan.id),
          "configured" -> _storyboard_renderer_json(plan.project.parts(part.partplan.index - 1).renderer),
          "effective" -> Json.fromString(part.partplan.renderer)
        )
      })
    )

  private def _storyboard_renderer_json(renderer: Option[VideoRenderer]): Json =
    renderer match {
      case Some(value) =>
        Json.obj(
          "engine" -> value.engine.map(Json.fromString).getOrElse(Json.Null),
          "strategy" -> value.strategy.map(Json.fromString).getOrElse(Json.Null),
          "policy" -> value.policy.map(x => Json.fromString(x.name)).getOrElse(Json.Null),
          "fps" -> value.fps.map(Json.fromInt).getOrElse(Json.Null),
          "width" -> value.width.map(Json.fromInt).getOrElse(Json.Null),
          "height" -> value.height.map(Json.fromInt).getOrElse(Json.Null),
          "crf" -> value.crf.map(Json.fromInt).getOrElse(Json.Null),
          "x264Preset" -> value.x264Preset.map(Json.fromString).getOrElse(Json.Null),
          "effectProfile" -> value.effectProfile.map(Json.fromString).getOrElse(Json.Null)
        )
      case None => Json.Null
    }

  private def _storyboard_effects_json(plan: VideoPlan): Json =
    Json.fromValues(CozyVideoEffects.expand(plan.project.visualEffects).map { effect =>
      Json.obj(
        "role" -> Json.fromString(effect.role.key),
        "profile" -> Json.fromString(effect.profile),
        "primitives" -> Json.fromValues(effect.primitives.map { primitive =>
          Json.obj(
            "name" -> Json.fromString(primitive.name),
            "parameters" -> Json.fromValues(primitive.parameters.map { case (key, value) =>
              Json.obj("key" -> Json.fromString(key), "value" -> Json.fromString(value))
            })
          )
        })
      )
    })

  private def _storyboard_narration_json(parts: Vector[StoryboardBuildPart]): Json =
    Json.fromValues(parts.map { part =>
      val script = part.partplan.script.getOrElse(
        _storyboard_build_failure(s"Storyboard build part ${part.partplan.id} has no projected narration script")
      )
      val selection = _resolve_narration_selection(script)
      Json.obj(
        "partId" -> Json.fromString(part.partplan.id),
        "provider" -> Json.fromString(selection.provider),
        "narration" -> script.narration,
        "voice" -> script.voice,
        "pronunciations" -> Json.obj(script.pronunciations.toVector.sortBy(_._1).map { case (surface, reading) =>
          surface -> Json.fromString(reading)
        }: _*),
        "storyboardPronunciationNotes" -> Json.fromValues(script.storyboardPronunciationNotes.map { note =>
          Json.obj("surface" -> Json.fromString(note.surface), "reading" -> Json.fromString(note.reading))
        }),
        "voiceTextNormalization" -> script.voiceTextNormalization
      )
    })

  private def _storyboard_execution_json(plan: VideoPlan): Json =
    Json.obj(
      "toolMode" -> Json.fromString(plan.execution.toolMode.label),
      "dockerImage" -> Json.fromString(plan.execution.dockerImage),
      "voicevoxUrl" -> Json.fromString(plan.execution.voicevoxUrl)
    )

  private def _storyboard_assets_json(plan: VideoPlan): Json =
    Json.fromValues(plan.assets.map { asset =>
      Json.obj(
        "role" -> Json.fromString(asset.role.key),
        "status" -> Json.fromString(asset.status),
        "path" -> Json.fromString(_display_path(plan.projectRoot, asset.path)),
        "requestedPath" -> asset.requestedPath.map(path => Json.fromString(_display_path(plan.projectRoot, path))).getOrElse(Json.Null),
        "kind" -> Json.fromString(asset.kind),
        "required" -> Json.fromBoolean(asset.required),
        "license" -> Json.fromString(asset.license),
        "provenance" -> Json.fromString(asset.provenance),
        "contentIdentity" -> _asset_identity(asset.path)
      )
    })

  private def _storyboard_declared_assets_json(plan: VideoPlan, parts: Vector[StoryboardBuildPart]): Json =
    Json.fromValues(parts.flatMap { part =>
      part.storyboard.scenes.flatMap { scene =>
        (scene.diagramRefs ++ scene.assetRefs).map { reference =>
          val path = plan.projectRoot.resolve(reference).normalize()
          Json.obj(
            "partId" -> Json.fromString(part.partplan.id),
            "reference" -> Json.fromString(reference),
            "contentIdentity" -> _asset_identity(path)
          )
        }
      }
    })

  private def _asset_identity(path: Path): Json =
    if (Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS) && !Files.isSymbolicLink(path))
      Json.fromString(_file_identity(path))
    else
      Json.fromString("absent")

  private def _validate_final_confirmation(
    plan: VideoPlan,
    confirmation: StoryboardBuildMode,
    parts: Vector[StoryboardBuildPart],
    cacheinput: Json
  ): Unit = {
    _require_direct_target_subtree(plan.projectRoot, confirmation.outputpath.getParent, "Final Storyboard build confirmation output directory")
    _require_direct_target_subtree(plan.projectRoot, confirmation.manifestpath.getParent, "Final Storyboard build confirmation manifest directory")
    _require_direct_regular_file(
      confirmation.outputpath,
      s"Final Storyboard build requires a direct regular confirmation output"
    )
    _require_direct_regular_file(
      confirmation.manifestpath,
      s"Final Storyboard build requires a direct regular confirmation manifest"
    )
    parts.foreach { part =>
      if (!_direct_json_matches(part.handoffpath, part.handoff))
        _storyboard_build_failure(s"Final Storyboard build confirmation handoff is missing, malformed, or stale: ${part.handoffpath}")
    }
    val expected = _storyboard_mode_manifest(plan, confirmation, parts, cacheinput, _file_identity(confirmation.outputpath))
    if (!_direct_json_matches(confirmation.manifestpath, expected))
      _storyboard_build_failure(s"Final Storyboard build confirmation manifest is malformed or stale: ${confirmation.manifestpath}")
    val identity = expected.hcursor.downField("identity").as[String].getOrElse(
      _storyboard_build_failure(s"Final Storyboard build confirmation manifest has no identity: ${confirmation.manifestpath}")
    )
    plan.project.confirmationReview match {
      case Some(review) if review.approvedIdentity == identity =>
      case Some(review) =>
        _storyboard_build_failure(
          s"Final Storyboard build requires confirmationReview.approvedIdentity to equal confirmation manifest identity $identity, found ${review.approvedIdentity}."
        )
      case None =>
        _storyboard_build_failure(
          s"Final Storyboard build requires confirmationReview.approvedIdentity to equal confirmation manifest identity $identity."
        )
    }
  }

  private[video] def _validated_storyboard_final_manifest(
    plan: VideoPlan,
    finalvideo: Path,
    finalhash: String
  ): Path = {
    val mode = _storyboard_build_mode_for(plan, _final_mode)
    val manifest = mode.manifestpath.toAbsolutePath.normalize()
    if (mode.outputpath.toAbsolutePath.normalize() != finalvideo.toAbsolutePath.normalize())
      _storyboard_build_failure(s"Storyboard final manifest output does not match the planned final video: $manifest")
    _require_direct_target_subtree(plan.projectRoot, manifest.getParent, "Storyboard final manifest directory")
    _require_direct_regular_file(manifest, "Storyboard final manifest")
    val parts = _storyboard_build_parts(plan)
    val cacheinput = _storyboard_cache_input(plan, mode, parts)
    val outputidentity = _file_identity(finalvideo)
    if (outputidentity != s"sha256:$finalhash")
      _storyboard_build_failure(s"Storyboard final video identity does not match review evidence input: $finalvideo")
    val expected = _storyboard_mode_manifest(plan, mode, parts, cacheinput, outputidentity)
    if (!_direct_json_matches(manifest, expected))
      _storyboard_build_failure(s"Storyboard final manifest is incomplete, stale, or noncanonical: $manifest")
    manifest
  }

  private def _prepare_storyboard_mode_targets(plan: VideoPlan, mode: StoryboardBuildMode): Unit = {
    val targetroot = _target_root(plan.projectRoot)
    val projectroot = plan.projectRoot.toAbsolutePath.normalize()
    _ensure_direct_directory(targetroot, plan.projectRoot, "Storyboard target root")
    _ensure_direct_directory(mode.manifestpath.getParent, targetroot, s"Storyboard ${mode.name} manifest directory")
    _ensure_direct_directory(_storyboard_part_artifact_directory(plan.projectRoot, mode), targetroot, s"Storyboard ${mode.name} part-artifact directory")
    if (mode.name == _confirmation_mode)
      _ensure_direct_directory(mode.outputpath.getParent, targetroot, "Storyboard confirmation output directory")
    else if (mode.outputpath.toAbsolutePath.normalize().startsWith(projectroot))
      _ensure_direct_directory(mode.outputpath.getParent, plan.projectRoot, "Storyboard final output directory")
    else
      _ensure_direct_external_directory(mode.outputpath.getParent, projectroot, "Storyboard final output directory")
    plan.parts.filter(_.renderable).foreach { part =>
      val outputpath = part.outputPath.toAbsolutePath.normalize()
      if (!outputpath.startsWith(projectroot))
        _ensure_direct_external_directory(outputpath.getParent, projectroot, s"Storyboard part ${part.id} output directory")
    }
    _assert_writable_direct_file(mode.manifestpath, s"Storyboard ${mode.name} manifest")
    _assert_writable_direct_file(mode.outputpath, s"Storyboard ${mode.name} output")
  }

  private def _write_storyboard_handoffs(plan: VideoPlan, parts: Vector[StoryboardBuildPart]): Unit =
    parts.foreach { part =>
      _write_storyboard_json(part.handoffpath, part.handoff, _target_root(plan.projectRoot), s"Storyboard handoff ${part.partplan.id}")
    }

  private def _validate_storyboard_part_outputs(plan: VideoPlan): Unit =
    plan.parts.filter(_.renderable).foreach { part =>
      val label = s"Storyboard part ${part.id} output"
      val path = part.outputPath.toAbsolutePath.normalize()
      if (path.startsWith(plan.projectRoot.toAbsolutePath.normalize()))
        _require_direct_storyboard_path(plan.projectRoot, path, label)
      _require_direct_regular_file(path, label)
    }

  private def _valid_storyboard_mode_cache(
    plan: VideoPlan,
    mode: StoryboardBuildMode,
    parts: Vector[StoryboardBuildPart],
    cacheinput: Json
  ): Boolean =
    if (!_is_direct_regular_file(mode.outputpath) || !_is_direct_regular_file(mode.manifestpath) ||
      !plan.parts.filter(_.renderable).forall(part => _is_direct_storyboard_part_artifact(plan.projectRoot, mode, part)))
      false
    else {
      val expected = _storyboard_mode_manifest(plan, mode, parts, cacheinput, _file_identity(mode.outputpath))
      _direct_json_matches(mode.manifestpath, expected) && parts.forall(part => _direct_json_matches(part.handoffpath, part.handoff))
    }

  private def _storyboard_mode_manifest(
    plan: VideoPlan,
    mode: StoryboardBuildMode,
    parts: Vector[StoryboardBuildPart],
    cacheinput: Json,
    outputidentity: String
  ): Json = {
    val cacheinputidentity = _json_identity(cacheinput)
    val payload = Json.obj(
      "schema" -> Json.fromString(mode.schema),
      "status" -> Json.fromString("validated"),
      "mode" -> Json.fromString(mode.name),
      "creditProfile" -> plan.credits.profileId.map(Json.fromString).getOrElse(Json.Null),
      "creditDigest" -> Json.fromString(plan.credits.digest),
      "cacheInput" -> cacheinput,
      "cacheInputIdentity" -> Json.fromString(cacheinputidentity),
      "output" -> Json.obj(
        "path" -> Json.fromString(_display_path(plan.projectRoot, mode.outputpath)),
        "sha256" -> Json.fromString(outputidentity)
      ),
      "partArtifacts" -> _storyboard_part_artifacts_json(plan, mode),
      "storyboards" -> _storyboard_parts_json(parts),
      "handoffs" -> _storyboard_handoffs_json(plan.projectRoot, parts),
      "encoding" -> _storyboard_encoding_json(plan.encoding),
      "renderers" -> _storyboard_renderers_json(plan, parts),
      "narration" -> _storyboard_narration_json(parts),
      "execution" -> _storyboard_execution_json(plan)
    )
    payload.mapObject(_.add("identity", Json.fromString(_json_identity(payload))))
  }

  private def _is_direct_storyboard_part_artifact(
    projectroot: Path,
    mode: StoryboardBuildMode,
    part: VideoPartPlan
  ): Boolean =
    _is_direct_regular_file(part.outputPath) &&
      _direct_json_matches(
        _storyboard_part_artifact_path(projectroot, mode, part.id),
        _storyboard_part_artifact_manifest(projectroot, mode, part)
      )

  private def _storyboard_part_artifacts_json(plan: VideoPlan, mode: StoryboardBuildMode): Json =
    Json.fromValues(plan.parts.filter(_.renderable).map { part =>
      _require_direct_regular_file(part.outputPath, s"Storyboard part ${part.id} output")
      val path = _storyboard_part_artifact_path(plan.projectRoot, mode, part.id)
      val manifest = _storyboard_part_artifact_manifest(plan.projectRoot, mode, part)
      _require_direct_target_subtree(plan.projectRoot, path.getParent, s"Storyboard ${mode.name} part ${part.id} artifact manifest directory")
      _require_direct_regular_file(path, s"Storyboard ${mode.name} part ${part.id} artifact manifest")
      if (!_direct_json_matches(path, manifest))
        _storyboard_build_failure(s"Storyboard ${mode.name} part ${part.id} artifact manifest is malformed or stale: $path")
      Json.obj(
        "partId" -> Json.fromString(part.id),
        "output" -> Json.obj(
          "path" -> Json.fromString(_display_path(plan.projectRoot, part.outputPath)),
          "sha256" -> Json.fromString(_file_identity(part.outputPath))
        ),
        "manifest" -> Json.obj(
          "path" -> Json.fromString(_display_path(plan.projectRoot, path)),
          "identity" -> Json.fromString(_json_identity(manifest))
        )
      )
    })

  private def _write_storyboard_part_artifacts(plan: VideoPlan, mode: StoryboardBuildMode): Unit =
    plan.parts.filter(_.renderable).foreach { part =>
      _require_direct_regular_file(part.outputPath, s"Storyboard part ${part.id} output")
      val path = _storyboard_part_artifact_path(plan.projectRoot, mode, part.id)
      val manifest = _storyboard_part_artifact_manifest(plan.projectRoot, mode, part)
      _write_storyboard_json(path, manifest, _target_root(plan.projectRoot), s"Storyboard ${mode.name} part ${part.id} artifact manifest")
    }

  private def _storyboard_part_artifact_directory(projectroot: Path, mode: StoryboardBuildMode): Path =
    _target_root(projectroot).resolve(mode.name).resolve("part-artifacts").normalize()

  private def _storyboard_part_artifact_path(projectroot: Path, mode: StoryboardBuildMode, partid: String): Path = {
    if (!Option(partid).getOrElse("").matches("[A-Za-z0-9][A-Za-z0-9_-]*"))
      _storyboard_build_failure(s"Storyboard part id is unsafe for generated artifact manifest: $partid")
    _storyboard_part_artifact_directory(projectroot, mode).resolve(s"$partid.json").normalize()
  }

  private def _storyboard_part_artifact_manifest(
    projectroot: Path,
    mode: StoryboardBuildMode,
    part: VideoPartPlan
  ): Json = {
    _require_direct_regular_file(part.outputPath, s"Storyboard part ${part.id} output")
    val payload = Json.obj(
      "schema" -> Json.fromString("cozy.video.storyboard-part-artifact.v1"),
      "status" -> Json.fromString("validated"),
      "mode" -> Json.fromString(mode.name),
      "partId" -> Json.fromString(part.id),
      "output" -> Json.obj(
        "path" -> Json.fromString(_display_path(projectroot, part.outputPath)),
        "sha256" -> Json.fromString(_file_identity(part.outputPath))
      )
    )
    payload.mapObject(_.add("identity", Json.fromString(_json_identity(payload))))
  }

  private def _render_storyboard_build_dry_run(
    config: BuildConfig,
    plan: VideoPlan,
    mode: StoryboardBuildMode,
    cacheinput: Json,
    checks: Vector[VideoToolCheck]
  ): String = {
    val base = _render_build_dry_run(config, plan, checks).stripSuffix("\n")
    Vector(
      base,
      s"mode: ${mode.name}",
      s"expectedOutput: ${mode.outputpath}",
      s"expectedManifest: ${mode.manifestpath}",
      s"cacheInputIdentity: ${_json_identity(cacheinput)}"
    ).mkString("\n") + "\n"
  }

  private def _render_storyboard_build_cache_hit(
    plan: VideoPlan,
    mode: StoryboardBuildMode,
    cacheinput: Json
  ): String = {
    val b = Vector.newBuilder[String]
    b ++= Vector(
      "Cozy Video Storyboard Build",
      s"projectFile: ${plan.projectFile}",
      s"mode: ${mode.name}",
      s"output: ${mode.outputpath}",
      s"manifest: ${mode.manifestpath}",
      s"cacheInputIdentity: ${_json_identity(cacheinput)}",
      "cache: hit"
    )
    plan.credits.profileId.foreach(x => b += s"creditProfile: $x")
    plan.credits.warnings.foreach { warning =>
      b += s"creditWarning: ${warning.code}: ${warning.message}"
    }
    b.result().mkString("\n") + "\n"
  }

  private def _render_storyboard_build_cache_miss(
    result: VideoBuildResult,
    mode: StoryboardBuildMode,
    cacheinput: Json,
    parts: Vector[StoryboardBuildPart]
  ): String =
    Vector(
      _render_build_result(result).stripSuffix("\n"),
      "Cozy Video Storyboard Build",
      s"projectFile: ${result.projectFile}",
      s"mode: ${mode.name}",
      s"output: ${result.outputPath}",
      s"manifest: ${result.manifestPath}",
      s"cacheInputIdentity: ${_json_identity(cacheinput)}",
      s"handoffs: ${parts.size}",
      "cache: miss",
      s"ffprobe: ${result.ffprobeSummary.noSpaces}"
    ).mkString("\n") + "\n"

  private def _target_root(projectroot: Path): Path =
    projectroot.toAbsolutePath.normalize().resolve("target").resolve("cozy-video").normalize()

  private def _require_direct_target_subtree(projectroot: Path, directory: Path, label: String): Unit = {
    val root = projectroot.toAbsolutePath.normalize()
    val targetroot = _target_root(root)
    val subtree = directory.toAbsolutePath.normalize()
    if (!subtree.startsWith(targetroot))
      _storyboard_build_failure(s"$label escapes the generated target subtree: $subtree")
    val directories = Vector(root) ++ root.relativize(subtree).iterator().asScala.scanLeft(root) { (current, segment) =>
      current.resolve(segment)
    }.drop(1)
    directories.foreach { path =>
      if (Files.isSymbolicLink(path) || !Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS))
        _storyboard_build_failure(s"$label must use direct project-contained non-symlink directories: $path")
    }
  }

  private def _ensure_direct_directory(path: Path, boundary: Path, label: String): Path = {
    val base = boundary.toAbsolutePath.normalize()
    val directory = path.toAbsolutePath.normalize()
    if (!(directory == base || directory.startsWith(base)))
      _storyboard_build_failure(s"$label escapes its allowed directory: $directory")
    if (Files.isSymbolicLink(base) || !Files.isDirectory(base, LinkOption.NOFOLLOW_LINKS))
      _storyboard_build_failure(s"$label has an invalid project directory boundary: $base")
    var current = base
    base.relativize(directory).iterator().asScala.foreach { segment =>
      current = current.resolve(segment)
      if (Files.exists(current, LinkOption.NOFOLLOW_LINKS)) {
        if (Files.isSymbolicLink(current) || !Files.isDirectory(current, LinkOption.NOFOLLOW_LINKS))
          _storyboard_build_failure(s"$label must not use a symbolic-link or non-directory path: $current")
      } else {
        try Files.createDirectory(current) catch {
          case NonFatal(error) => _storyboard_build_failure(s"Cannot create $label: ${error.getMessage}")
        }
      }
    }
    directory
  }

  private def _ensure_direct_external_directory(path: Path, projectroot: Path, label: String): Path = {
    val root = projectroot.toAbsolutePath.normalize()
    val directory = path.toAbsolutePath.normalize()
    val boundary = _shared_lexical_ancestor(root, directory, label)
    _ensure_direct_directory(directory, boundary, label)
  }

  private def _shared_lexical_ancestor(first: Path, second: Path, label: String): Path = {
    val left = first.toAbsolutePath.normalize()
    val right = second.toAbsolutePath.normalize()
    val leftroot = left.getRoot
    if (leftroot == null || leftroot != right.getRoot)
      _storyboard_build_failure(s"$label has no shared lexical ancestor with the project root: $right")
    val segments = left.iterator().asScala.zip(right.iterator().asScala).takeWhile { case (leftsegment, rightsegment) => leftsegment == rightsegment }.map(_._1)
    segments.foldLeft(leftroot)((current, segment) => current.resolve(segment))
  }

  private def _assert_no_symlink_components(projectroot: Path, path: Path, label: String): Unit = {
    val root = projectroot.toAbsolutePath.normalize()
    val candidate = path.toAbsolutePath.normalize()
    if (!candidate.startsWith(root))
      _storyboard_build_failure(s"$label escapes the project root: $candidate")
    var current = root
    root.relativize(candidate).iterator().asScala.foreach { segment =>
      current = current.resolve(segment)
      if (Files.isSymbolicLink(current))
        _storyboard_build_failure(s"$label must not use a symbolic-link path: $current")
    }
  }

  private def _require_direct_storyboard_path(
    projectroot: Path,
    path: Path,
    label: String,
    allowmissingparents: Boolean = false
  ): Unit = {
    val root = projectroot.toAbsolutePath.normalize()
    val candidate = path.toAbsolutePath.normalize()
    if (!candidate.startsWith(root))
      _storyboard_build_failure(s"$label escapes the project root: $candidate")
    if (Files.isSymbolicLink(root) || !Files.isDirectory(root, LinkOption.NOFOLLOW_LINKS))
      _storyboard_build_failure(s"$label must use a direct project-contained non-symlink directory: $root")
    val parent = Option(candidate.getParent).filter(_.startsWith(root)).getOrElse(root)
    var current = root
    root.relativize(parent).iterator().asScala.foreach { segment =>
      current = current.resolve(segment)
      if (!Files.exists(current, LinkOption.NOFOLLOW_LINKS) && !allowmissingparents)
        _storyboard_build_failure(s"$label must use direct project-contained non-symlink parent directories: $current")
      else if (Files.isSymbolicLink(current) || (Files.exists(current, LinkOption.NOFOLLOW_LINKS) && !Files.isDirectory(current, LinkOption.NOFOLLOW_LINKS)))
        _storyboard_build_failure(s"$label must use direct project-contained non-symlink parent directories: $current")
    }
    if (Files.isSymbolicLink(candidate))
      _storyboard_build_failure(s"$label must not use a symbolic-link path: $candidate")
  }

  private def _direct_tree_identity(projectroot: Path, path: Path, label: String): String = {
    val entries = try {
      val stream = Files.walk(path)
      try stream.iterator().asScala.toVector.sortBy(entry => _display_path(projectroot, entry))
      finally stream.close()
    } catch {
      case NonFatal(error) => _storyboard_build_failure(s"Cannot read $label: ${error.getMessage}")
    }
    val values = entries.map { entry =>
      _require_direct_storyboard_path(projectroot, entry, label)
      val relative = _display_path(projectroot, entry)
      if (Files.isDirectory(entry, LinkOption.NOFOLLOW_LINKS))
        s"directory:$relative"
      else if (Files.isRegularFile(entry, LinkOption.NOFOLLOW_LINKS))
        s"file:$relative:${_file_identity(entry)}"
      else
        _storyboard_build_failure(s"$label contains unsupported input entry: $entry")
    }
    _json_identity(Json.fromValues(values.map(Json.fromString)))
  }

  private def _assert_writable_direct_file(path: Path, label: String): Unit =
    if (Files.exists(path, LinkOption.NOFOLLOW_LINKS) && Files.isSymbolicLink(path))
      _storyboard_build_failure(s"$label must not replace a symbolic link: $path")

  private def _write_storyboard_json(path: Path, json: Json, boundary: Path, label: String): Unit = {
    _ensure_direct_directory(path.getParent, boundary, s"$label parent")
    _assert_writable_direct_file(path, label)
    try Files.writeString(path, json.noSpaces, StandardCharsets.UTF_8) catch {
      case NonFatal(error) => _storyboard_build_failure(s"Cannot write $label: ${error.getMessage}")
    }
    _require_direct_regular_file(path, label)
  }

  private def _is_direct_regular_file(path: Path): Boolean =
    Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS) && !Files.isSymbolicLink(path)

  private def _require_direct_regular_file(path: Path, label: String): Unit =
    if (!_is_direct_regular_file(path))
      _storyboard_build_failure(s"$label is missing or not a direct regular non-symlink file: $path")

  private def _direct_json_matches(path: Path, expected: Json): Boolean =
    if (!_is_direct_regular_file(path))
      false
    else {
      try {
        val text = Files.readString(path, StandardCharsets.UTF_8)
        parser.parse(text).toOption.exists(_ == expected) && text == expected.noSpaces
      } catch {
        case NonFatal(_) => false
      }
    }

  private def _file_identity(path: Path): String =
    "sha256:" + _sha256(path)

  private def _json_identity(json: Json): String = {
    val digest = MessageDigest.getInstance("SHA-256")
    "sha256:" + digest.digest(json.noSpaces.getBytes(StandardCharsets.UTF_8)).map(value => f"${value & 0xff}%02x").mkString
  }

  private def _display_path(projectroot: Path, path: Path): String = {
    val root = projectroot.toAbsolutePath.normalize()
    val value = path.toAbsolutePath.normalize()
    if (value.startsWith(root))
      root.relativize(value).iterator().asScala.map(_.toString).mkString("/")
    else
      value.toString
  }

  private def _storyboard_build_failure(message: String): Nothing =
    RAISE.invalidArgumentFault(message)
}
