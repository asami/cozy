package cozy.video

import cozy.generation.CozyFileUpdatePolicy
import cozy.media.CozyVisualPage
import io.circe.Json
import io.circe.parser
import java.nio.charset.StandardCharsets
import java.nio.file.{Files, LinkOption, Path, StandardCopyOption}
import java.util.UUID
import org.goldenport.RAISE
import scala.collection.JavaConverters._
import scala.util.control.NonFatal

/*
 * @since   Aug. 26, 2026
 * @version Sep. 29, 2026
 * @author  ASAMI, Tomoharu
 */
private[cozy] trait CozyVideoStoryboardBuild {
  self: CozyVideoImplementation.type =>

  private final case class StoryboardBuildPart(
    partplan: VideoPartPlan,
    sourcepath: Path,
    storyboard: Storyboard,
    storyboardsection: Option[String],
    handoffpath: Path,
    handoff: Json
  )

  private final case class StoryboardBuildMode(
    name: String,
    schema: String,
    outputpath: Path,
    manifestpath: Path
  )

  private val _confirmation_mode = "confirmation"
  private val _final_mode = "final"
  private val _handoff_schema = "cozy.video.storyboard-build-handoff.v2"

  private[video] def _build_storyboard_mode(
    config: BuildConfig,
    plan: VideoPlan,
    tools: VideoToolRegistry,
    runner: VideoProcessRunner
  ): String = {
    val mode = _storyboard_build_mode(config, plan)
    val parts = _storyboard_build_parts(plan)
    val inputs = _declared_input_observations(plan, parts)
    val context = VideoToolContext(plan.projectFile, plan.projectRoot, plan.project, plan.execution)
    val checks =
      if (config.checkTools || (!config.dryRun && plan.execution.toolMode == VideoToolMode.Docker))
        tools.checks(context)
      else
        Vector.empty
    if (config.dryRun)
      _render_storyboard_build_dry_run(config, plan, mode, checks)
    else {
      plan.credits.requireValid()
      _validate_build_tools(plan.execution, checks)
      val decisions = _storyboard_update_decisions(plan, mode, parts, inputs)
      if (decisions.forall(_.isInstanceOf[CozyFileUpdatePolicy.Decision.Reuse]) &&
        _valid_storyboard_mode_cache(plan, mode, parts, runner))
        _render_storyboard_build_cache_hit(plan, mode)
      else decisions.collectFirst { case CozyFileUpdatePolicy.Decision.Unavailable(reason) => reason } match {
        case Some(reason) =>
          _storyboard_build_failure(s"Storyboard ${mode.name} generation input is unavailable: $reason")
        case None =>
          _assemble_storyboard_mode(plan, mode, parts, inputs, checks, runner)
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
          "cozy.video.confirmation.v2",
          confirmationroot.resolve("confirmation.mp4").normalize(),
          confirmationroot.resolve("manifest.json").normalize()
        )
      case value if value == _final_mode =>
        if (plan.outputPath.toAbsolutePath.normalize().startsWith(confirmationroot))
          _storyboard_build_failure(s"Storyboard final output must not be under the confirmation root: ${plan.outputPath}")
        StoryboardBuildMode(
          _final_mode,
          "cozy.video.final.v2",
          plan.outputPath,
          targetroot.resolve(_final_mode).resolve("manifest.json").normalize()
        )
      case value =>
        _storyboard_build_failure(s"Unsupported Storyboard video build mode: $value. Expected confirmation or final.")
    }
  }

  private def _storyboard_build_parts(plan: VideoPlan): Vector[StoryboardBuildPart] = {
    plan.project.parts.zip(plan.parts).collect {
      case (part, partplan) if part.storyboard.isDefined =>
        val sourcepath = _safe_storyboard_build_source(plan.projectRoot, part.storyboard.get, s"Storyboard part ${partplan.id}")
        val result = loadStoryboard(sourcepath)
        if (!result.isValid)
          _storyboard_build_failure(
            s"Storyboard build source is invalid for part ${partplan.id}: ${result.diagnostics.map(_.render).mkString("; ")}"
        )
        val sourcestoryboard = result.storyboard.get
        val storyboardsection = part.storyboardSection
        val storyboard = _select_storyboard_section(sourcestoryboard, storyboardsection)
        val handoffpath = _storyboard_handoff_path(plan.projectRoot, partplan.id)
        val handoffpayload = _storyboard_handoff_payload(
          partplan.id,
          plan.projectRoot,
          sourcepath,
          storyboard,
          storyboardsection
        )
        StoryboardBuildPart(
          partplan,
          sourcepath,
          storyboard,
          storyboardsection,
          handoffpath,
          handoffpayload
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
    projectroot: Path,
    sourcepath: Path,
    storyboard: Storyboard,
    storyboardsection: Option[String]
  ): Json = {
    val canonical = parser.parse(canonicalStoryboardJson(storyboard)).fold(
      error => _storyboard_build_failure(s"Cannot canonicalize Storyboard handoff for part $partid: ${error.getMessage}"),
      identity
    )
    Json.obj(
      "schema" -> Json.fromString(_handoff_schema),
      "status" -> Json.fromString("validated"),
      "partId" -> Json.fromString(partid),
      "sourcePath" -> Json.fromString(_display_path(projectroot, sourcepath)),
      "storyboardSection" -> storyboardsection.map(Json.fromString).getOrElse(Json.Null),
      "storyboard" -> canonical
    )
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

  private def _storyboard_handoffs_json(projectroot: Path, parts: Vector[StoryboardBuildPart]): Json =
    Json.fromValues(parts.map { part =>
      Json.obj(
        "partId" -> Json.fromString(part.partplan.id),
        "path" -> Json.fromString(_display_path(projectroot, part.handoffpath))
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

  private def _storyboard_renderers_json(plan: VideoPlan): Json =
    Json.obj(
      "project" -> _storyboard_renderer_json(plan.project.renderer),
      "parts" -> Json.fromValues(plan.parts.filter(_.renderable).map { part =>
        val configured = plan.project.parts.lift(part.index - 1).getOrElse(
          _storyboard_build_failure(s"Storyboard build part ${part.id} has no configured part definition")
        )
        Json.obj(
          "partId" -> Json.fromString(part.id),
          "configured" -> _storyboard_renderer_json(configured.renderer),
          "effective" -> Json.fromString(part.renderer)
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
        "provenance" -> Json.fromString(asset.provenance)
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
            "path" -> Json.fromString(_display_path(plan.projectRoot, path))
          )
        }
      }
    })

  private[video] def _validated_storyboard_final_manifest(
    plan: VideoPlan,
    finalvideo: Path
  ): Path = {
    val mode = _storyboard_build_mode_for(plan, _final_mode)
    val manifest = mode.manifestpath.toAbsolutePath.normalize()
    if (mode.outputpath.toAbsolutePath.normalize() != finalvideo.toAbsolutePath.normalize())
      _storyboard_build_failure(s"Storyboard final manifest output does not match the planned final video: $manifest")
    _require_direct_target_subtree(plan.projectRoot, manifest.getParent, "Storyboard final manifest directory")
    _require_direct_regular_file(manifest, "Storyboard final manifest")
    val parts = _storyboard_build_parts(plan)
    val inputs = _declared_input_observations(plan, parts)
    val decisions = _storyboard_update_decisions(plan, mode, parts, inputs)
    if (!decisions.forall(_.isInstanceOf[CozyFileUpdatePolicy.Decision.Reuse]) ||
      !_valid_final_manifest(plan, mode, parts, manifest))
      _storyboard_build_failure(s"Storyboard final manifest is incomplete, stale, or noncanonical: $manifest")
    manifest
  }

  private def _prepare_storyboard_mode_targets(plan: VideoPlan, mode: StoryboardBuildMode, parts: Vector[StoryboardBuildPart]): Unit = {
    val targetroot = _target_root(plan.projectRoot)
    val projectroot = plan.projectRoot.toAbsolutePath.normalize()
    _ensure_direct_directory(targetroot, plan.projectRoot, "Storyboard target root")
    _ensure_direct_directory(mode.manifestpath.getParent, targetroot, s"Storyboard ${mode.name} manifest directory")
    _ensure_direct_directory(_storyboard_part_artifact_directory(plan.projectRoot, mode), targetroot, s"Storyboard ${mode.name} part-artifact directory")
    parts.foreach(part => _ensure_direct_directory(part.handoffpath.getParent, targetroot, s"Storyboard handoff ${part.partplan.id} directory"))
    if (mode.name == _confirmation_mode)
      _ensure_direct_directory(mode.outputpath.getParent, targetroot, "Storyboard confirmation output directory")
    else if (mode.outputpath.toAbsolutePath.normalize().startsWith(projectroot))
      _ensure_direct_directory(mode.outputpath.getParent, projectroot, "Storyboard final output directory")
    else
      _ensure_direct_external_directory(mode.outputpath.getParent, projectroot, "Storyboard final output directory")
    plan.credits.profile.foreach { _ =>
      val credits = mode.outputpath.getParent.resolve("credits").normalize()
      if (credits.startsWith(projectroot))
        _ensure_direct_directory(credits, projectroot, s"Storyboard ${mode.name} credits directory")
      else
        _ensure_direct_external_directory(credits, projectroot, s"Storyboard ${mode.name} credits directory")
    }
    plan.parts.filter(_.renderable).foreach { part =>
      val outputpath = part.outputPath.toAbsolutePath.normalize()
      if (outputpath.startsWith(projectroot))
        _require_direct_storyboard_path(projectroot, outputpath, s"Storyboard part ${part.id} output")
      else
        _ensure_direct_external_directory(outputpath.getParent, projectroot, s"Storyboard part ${part.id} output directory")
    }
    _assert_writable_direct_file(mode.manifestpath, s"Storyboard ${mode.name} manifest")
    _assert_writable_direct_file(mode.outputpath, s"Storyboard ${mode.name} output")
  }

  private def _declared_input_observations(plan: VideoPlan, parts: Vector[StoryboardBuildPart]): Vector[CozyFileUpdatePolicy.FileObservation] = {
    val projectfiles = plan.projectContext.layers.flatMap(_.files.map(_.path))
    val partpaths = plan.parts.flatMap(part => Vector(Some(part.outputPath), part.scriptPath, part.stepsPath, part.recordDir).flatten)
    val storyboardpaths = parts.flatMap { part =>
      val references = part.storyboard.scenes.flatMap(scene => scene.diagramRefs ++ scene.assetRefs).map(reference => plan.projectRoot.resolve(reference).normalize())
      part.sourcepath +: references
    }
    val visualpagepaths = parts.flatMap(_visual_page_input_paths)
    val creditpaths = plan.credits.profile.map(_.path).toVector ++ plan.credits.selection.flatMap(_.configPath).toVector ++ plan.credits.evidence.audio.map(_.manifestPath)
    _file_observations((Vector(plan.projectFile) ++ projectfiles ++ partpaths ++ plan.assets.map(_.path) ++ storyboardpaths ++ visualpagepaths ++ creditpaths).distinct)
  }

  private def _visual_page_input_paths(part: StoryboardBuildPart): Vector[Path] =
    part.storyboard.scenes.flatMap {
      case scene if scene.screen.isInstanceOf[StoryboardVisualPageScreen] =>
        val screen = scene.screen.asInstanceOf[StoryboardVisualPageScreen]
        val root = Option(part.sourcepath.getParent).getOrElse(_storyboard_build_failure(s"Storyboard visual-page source has no descriptor directory: ${part.sourcepath}"))
        val source = root.resolve(screen.source).normalize()
        val catalog = root.resolve(screen.catalog).normalize()
        val validated = try CozyVisualPage.load(source, catalog) catch {
          case NonFatal(error) => _storyboard_build_failure(s"Storyboard visual-page input is invalid for part ${part.partplan.id}: ${error.getMessage}")
        }
        val page = validated.document.pages.find(_.id == screen.pageId).getOrElse(
          _storyboard_build_failure(s"Storyboard visual-page ${screen.pageId} is not selected by part ${part.partplan.id}")
        )
        Vector(source, catalog) ++ page.assets.map(asset => _visual_page_asset_path(root, asset.path, part.partplan.id))
      case _ => Vector.empty
    }

  private def _visual_page_asset_path(root: Path, raw: String, partid: String): Path = {
    val path = root.resolve(raw).normalize()
    if (!path.startsWith(root))
      _storyboard_build_failure(s"Storyboard visual-page asset escapes its descriptor directory for part $partid: $raw")
    path
  }

  private def _file_observations(paths: Vector[Path]): Vector[CozyFileUpdatePolicy.FileObservation] =
    paths.map(_.toAbsolutePath.normalize()).distinct.sortBy(_.toString).flatMap(_file_observations_for)

  private def _file_observations_for(path: Path): Vector[CozyFileUpdatePolicy.FileObservation] =
    _unsafe_input_path(path) match {
      case Some(reason) => Vector(CozyFileUpdatePolicy.FileObservation(path.toString, CozyFileUpdatePolicy.FileState.Invalid(reason)))
      case None if (!Files.exists(path, LinkOption.NOFOLLOW_LINKS)) =>
        Vector(CozyFileUpdatePolicy.FileObservation(path.toString, CozyFileUpdatePolicy.FileState.Absent))
      case None if (Files.isSymbolicLink(path)) =>
        Vector(CozyFileUpdatePolicy.FileObservation(path.toString, CozyFileUpdatePolicy.FileState.Invalid("symbolic link")))
      case None if (Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) =>
        Vector(_file_observation(path))
      case None if (Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)) =>
        val entries = try {
          val stream = Files.walk(path)
          try stream.iterator().asScala.toVector.map(_.toAbsolutePath.normalize()).sortBy(_.toString)
          finally stream.close()
        } catch {
          case NonFatal(error) => return Vector(CozyFileUpdatePolicy.FileObservation(path.toString, CozyFileUpdatePolicy.FileState.Invalid(error.getMessage)))
        }
        entries.flatMap { entry =>
          if (Files.isSymbolicLink(entry)) Vector(CozyFileUpdatePolicy.FileObservation(entry.toString, CozyFileUpdatePolicy.FileState.Invalid("symbolic link")))
          else if (Files.isDirectory(entry, LinkOption.NOFOLLOW_LINKS) || Files.isRegularFile(entry, LinkOption.NOFOLLOW_LINKS)) Vector(_file_observation(entry))
          else Vector(CozyFileUpdatePolicy.FileObservation(entry.toString, CozyFileUpdatePolicy.FileState.Invalid("unsupported path")))
        }
      case None =>
        Vector(CozyFileUpdatePolicy.FileObservation(path.toString, CozyFileUpdatePolicy.FileState.Invalid("not a regular file or directory")))
    }

  private def _unsafe_input_path(path: Path): Option[String] = {
    val value = path.toAbsolutePath.normalize()
    var current = value.getRoot
    if (current == null) return Some("not absolute")
    Option(value.getParent).foreach(_.iterator().asScala.foreach { segment =>
      current = current.resolve(segment)
      if (Files.exists(current, LinkOption.NOFOLLOW_LINKS) &&
        (Files.isSymbolicLink(current) || !Files.isDirectory(current, LinkOption.NOFOLLOW_LINKS)))
        return Some(s"unsafe ancestor: $current")
    })
    if (Files.isSymbolicLink(value)) Some("symbolic link") else None
  }

  private def _file_observation(path: Path): CozyFileUpdatePolicy.FileObservation = {
    val time = try Some(Files.getLastModifiedTime(path, LinkOption.NOFOLLOW_LINKS)) catch { case NonFatal(_) => None }
    CozyFileUpdatePolicy.FileObservation(path.toString, CozyFileUpdatePolicy.FileState.Valid(time))
  }

  private def _storyboard_update_decisions(
    plan: VideoPlan,
    mode: StoryboardBuildMode,
    parts: Vector[StoryboardBuildPart],
    inputs: Vector[CozyFileUpdatePolicy.FileObservation]
  ): Vector[CozyFileUpdatePolicy.Decision] =
    _storyboard_generated_products(plan, mode, parts).map { path =>
      CozyFileUpdatePolicy.evaluate(CozyFileUpdatePolicy.Request(
        CozyFileUpdatePolicy.Intent.Generation,
        Some("native-storyboard-assembly"),
        inputs,
        _output_observation(path),
        force = false
      ))
    }

  private def _storyboard_generated_products(plan: VideoPlan, mode: StoryboardBuildMode, parts: Vector[StoryboardBuildPart]): Vector[Path] =
    Vector(mode.outputpath, mode.manifestpath) ++ parts.map(_.handoffpath) ++
      plan.parts.filter(_.renderable).map(part => _storyboard_part_artifact_path(plan.projectRoot, mode, part.id)) ++
      plan.credits.profile.toVector.flatMap(_ => _credit_destination_paths(mode))

  private def _credit_destination_paths(mode: StoryboardBuildMode): Vector[Path] = {
    val directory = mode.outputpath.getParent.resolve("credits")
    Vector(directory.resolve("credits.json"), directory.resolve("credits.md"), directory.resolve("renderer-props.json"))
  }

  private def _preflight_storyboard_mode_destinations(
    plan: VideoPlan,
    mode: StoryboardBuildMode,
    parts: Vector[StoryboardBuildPart],
    inputs: Vector[CozyFileUpdatePolicy.FileObservation]
  ): Unit = {
    val destinations = _storyboard_generated_products(plan, mode, parts).map(_.toAbsolutePath.normalize())
    if (destinations.distinct.size != destinations.size)
      _storyboard_build_failure(s"Storyboard ${mode.name} generation has duplicate destinations")
    val inputpaths = inputs.map(x => Path.of(x.path).toAbsolutePath.normalize()).toSet
    destinations.foreach { destination =>
      inputpaths.find { input =>
        destination == input || (Files.isDirectory(input, LinkOption.NOFOLLOW_LINKS) && destination.startsWith(input))
      }.foreach { input =>
        _storyboard_build_failure(s"Storyboard ${mode.name} generated destination aliases a declared input: $destination ($input)")
      }
      _safe_generated_path(destination).foreach { reason =>
        _storyboard_build_failure(s"Storyboard ${mode.name} generated destination is unsafe: $destination ($reason)")
      }
      if (Files.exists(destination, LinkOption.NOFOLLOW_LINKS) && !_is_direct_regular_file(destination))
        _storyboard_build_failure(s"Storyboard ${mode.name} generated destination is not a direct regular file: $destination")
      _require_atomic_destination_device(plan.projectRoot, destination, mode)
    }
  }

  private def _require_atomic_destination_device(projectroot: Path, destination: Path, mode: StoryboardBuildMode): Unit = {
    val stagingparent = _target_root(projectroot)
    val parent = Option(destination.getParent).getOrElse(
      _storyboard_build_failure(s"Storyboard ${mode.name} generated destination has no parent: $destination")
    )
    try {
      if (Files.getFileStore(stagingparent) != Files.getFileStore(parent))
        _storyboard_build_failure(s"Storyboard ${mode.name} generated destination is not on the staging filesystem required for atomic replacement: $destination")
    } catch {
      case NonFatal(error) => _storyboard_build_failure(s"Storyboard ${mode.name} cannot verify atomic destination filesystem for $destination: ${error.getMessage}")
    }
  }

  private def _output_observation(path: Path): CozyFileUpdatePolicy.FileObservation = {
    val value = path.toAbsolutePath.normalize()
    _safe_generated_path(value) match {
      case Some(reason) => CozyFileUpdatePolicy.FileObservation(value.toString, CozyFileUpdatePolicy.FileState.Invalid(reason))
      case None if (!Files.exists(value, LinkOption.NOFOLLOW_LINKS)) =>
        CozyFileUpdatePolicy.FileObservation(value.toString, CozyFileUpdatePolicy.FileState.Absent)
      case None if (!_is_direct_nonempty_file(value)) =>
        CozyFileUpdatePolicy.FileObservation(value.toString, CozyFileUpdatePolicy.FileState.Invalid("not a direct nonempty regular file"))
      case None => _file_observation(value)
    }
  }

  private def _safe_generated_path(path: Path): Option[String] = {
    val parent = Option(path.getParent).getOrElse(return Some("has no parent"))
    var current = parent.getRoot
    if (current == null) return Some("is not absolute")
    parent.iterator().asScala.foreach { segment =>
      current = current.resolve(segment)
      if (Files.exists(current, LinkOption.NOFOLLOW_LINKS) &&
        (Files.isSymbolicLink(current) || !Files.isDirectory(current, LinkOption.NOFOLLOW_LINKS)))
        return Some(s"has unsafe ancestor: $current")
    }
    if (Files.isSymbolicLink(path)) Some("is a symbolic link") else None
  }

  private def _validate_storyboard_part_outputs(plan: VideoPlan): Unit =
    plan.parts.filter(_.renderable).foreach { part =>
      val path = part.outputPath.toAbsolutePath.normalize()
      if (path.startsWith(plan.projectRoot.toAbsolutePath.normalize()))
        _require_direct_storyboard_path(plan.projectRoot, path, s"Storyboard part ${part.id} output")
      _require_direct_regular_file(path, s"Storyboard part ${part.id} output")
    }

  private def _valid_storyboard_mode_cache(
    plan: VideoPlan,
    mode: StoryboardBuildMode,
    parts: Vector[StoryboardBuildPart],
    runner: VideoProcessRunner
  ): Boolean =
    _valid_final_manifest(plan, mode, parts, mode.manifestpath) && {
      try CozyVideoStoryboardRecordValidation.ffprobeSummaryValid(_ffprobe_summary(_run_build_ffprobe(plan.projectRoot, plan.execution, mode.outputpath, runner)))
      catch { case NonFatal(_) => false }
    }

  private def _valid_final_manifest(plan: VideoPlan, mode: StoryboardBuildMode, parts: Vector[StoryboardBuildPart], manifest: Path): Boolean =
    _is_direct_nonempty_file(mode.outputpath) && _is_direct_nonempty_file(manifest) &&
      _record_contains(manifest, _storyboard_mode_record(plan, mode, parts, None, includenarration = false)) &&
      _valid_stored_narration(plan, manifest, parts) &&
      parts.forall(part => _valid_handoff(plan.projectRoot, part)) &&
      plan.parts.filter(_.renderable).forall(part => _is_direct_nonempty_file(part.outputPath) &&
        _record_contains(_storyboard_part_artifact_path(plan.projectRoot, mode, part.id), _storyboard_part_artifact_record(plan.projectRoot, mode, part))) &&
      _stored_ffprobe_valid(manifest) && _credit_files_current(plan, mode)

  private def _valid_stored_narration(plan: VideoPlan, manifest: Path, parts: Vector[StoryboardBuildPart]): Boolean =
    try parser.parse(Files.readString(manifest, StandardCharsets.UTF_8)).toOption.flatMap(_.hcursor.downField("narration").focus).flatMap(_.asArray).exists { entries =>
      entries.size == parts.size && entries.zip(parts).forall { case (entry, part) =>
        part.partplan.script.exists { script =>
          _valid_stored_narration_shape(entry) &&
            entry.hcursor.get[String]("partId").toOption.contains(part.partplan.id) &&
            entry.hcursor.get[String]("provider").toOption.contains(_resolve_narration_selection(script).provider) &&
            entry.hcursor.downField("narration").focus.contains(script.narration) &&
            script.narration == plan.project.narration &&
            entry.hcursor.downField("voice").focus.contains(script.voice) &&
            script.voice == plan.project.voice &&
            entry.hcursor.downField("voiceTextNormalization").focus.contains(script.voiceTextNormalization) &&
            script.voiceTextNormalization == plan.project.voiceTextNormalization
        }
      }
    } catch { case NonFatal(_) => false }

  private def _valid_stored_narration_shape(entry: Json): Boolean =
    CozyVideoStoryboardRecordValidation.nonemptyString(entry, "partId") && CozyVideoStoryboardRecordValidation.nonemptyString(entry, "provider") &&
      entry.hcursor.downField("narration").focus.exists(!_.isNull) &&
      entry.hcursor.downField("voice").focus.exists(_.isObject) &&
      entry.hcursor.downField("pronunciations").focus.exists(_.isObject) &&
      entry.hcursor.downField("voiceTextNormalization").focus.exists(!_.isNull) &&
      entry.hcursor.downField("storyboardPronunciationNotes").focus.flatMap(_.asArray).exists(_.forall(note =>
        CozyVideoStoryboardRecordValidation.nonemptyString(note, "surface") && CozyVideoStoryboardRecordValidation.nonemptyString(note, "reading")
      ))

  private def _storyboard_mode_record(
    plan: VideoPlan,
    mode: StoryboardBuildMode,
    parts: Vector[StoryboardBuildPart],
    ffprobe: Option[Json],
    includenarration: Boolean = true
  ): Json = {
    val fields = Vector(
      "schema" -> Json.fromString(mode.schema),
      "status" -> Json.fromString("validated"),
      "mode" -> Json.fromString(mode.name),
      "output" -> Json.obj("path" -> Json.fromString(_display_path(plan.projectRoot, mode.outputpath))),
      "creditProfile" -> plan.credits.profileId.map(Json.fromString).getOrElse(Json.Null),
      "buildSettings" -> Json.obj(
        "title" -> plan.project.title.map(Json.fromString).getOrElse(Json.Null),
        "characters" -> _storyboard_characters_json(plan.project.characters),
        "parts" -> _storyboard_part_settings_json(plan),
        "visualEffects" -> _storyboard_effects_json(plan),
        "assets" -> _storyboard_assets_json(plan)
      ),
      "storyboards" -> _storyboard_parts_json(plan.projectRoot, parts),
      "handoffs" -> _storyboard_handoffs_json(plan.projectRoot, parts),
      "partArtifacts" -> _storyboard_part_artifacts_json(plan, mode),
      "encoding" -> _storyboard_encoding_json(plan.encoding),
      "renderers" -> _storyboard_renderers_json(plan),
      "execution" -> _storyboard_execution_json(plan)
    )
    val narration = if (includenarration) Vector("narration" -> _storyboard_narration_json(parts)) else Vector.empty
    Json.obj((fields ++ narration ++ ffprobe.map(value => "ffprobe" -> value)): _*)
  }

  private def _storyboard_parts_json(projectroot: Path, parts: Vector[StoryboardBuildPart]): Json =
    Json.fromValues(parts.map { part =>
      Json.obj(
        "partId" -> Json.fromString(part.partplan.id),
        "sourcePath" -> Json.fromString(_display_path(projectroot, part.sourcepath)),
        "storyboardSection" -> part.storyboardsection.map(Json.fromString).getOrElse(Json.Null)
      )
    })

  private def _storyboard_part_settings_json(plan: VideoPlan): Json =
    Json.fromValues(plan.parts.filter(_.renderable).map { part =>
      val configured = plan.project.parts.lift(part.index - 1).getOrElse(
        _storyboard_build_failure(s"Storyboard build part ${part.id} has no configured part definition")
      )
      Json.obj(
        "partId" -> Json.fromString(part.id),
        "configuredPartType" -> Json.fromString(configured.displayType),
        "effectivePartType" -> Json.fromString(part.partType),
        "scriptPath" -> part.scriptPath.map(path => Json.fromString(_display_path(plan.projectRoot, path))).getOrElse(Json.Null),
        "stepsPath" -> part.stepsPath.map(path => Json.fromString(_display_path(plan.projectRoot, path))).getOrElse(Json.Null),
        "recordDirectory" -> part.recordDir.map(path => Json.fromString(_display_path(plan.projectRoot, path))).getOrElse(Json.Null),
        "outputPath" -> Json.fromString(_display_path(plan.projectRoot, part.outputPath))
      )
    })

  private def _storyboard_part_artifacts_json(plan: VideoPlan, mode: StoryboardBuildMode): Json =
    Json.fromValues(plan.parts.filter(_.renderable).map { part =>
      val path = _storyboard_part_artifact_path(plan.projectRoot, mode, part.id)
      Json.obj(
        "partId" -> Json.fromString(part.id),
        "output" -> Json.obj("path" -> Json.fromString(_display_path(plan.projectRoot, part.outputPath))),
        "manifest" -> Json.obj("path" -> Json.fromString(_display_path(plan.projectRoot, path)))
      )
    })

  private def _storyboard_part_artifact_directory(projectroot: Path, mode: StoryboardBuildMode): Path =
    _target_root(projectroot).resolve(mode.name).resolve("part-artifacts").normalize()

  private def _storyboard_part_artifact_path(projectroot: Path, mode: StoryboardBuildMode, partid: String): Path = {
    if (!Option(partid).getOrElse("").matches("[A-Za-z0-9][A-Za-z0-9_-]*"))
      _storyboard_build_failure(s"Storyboard part id is unsafe for generated artifact manifest: $partid")
    _storyboard_part_artifact_directory(projectroot, mode).resolve(s"$partid.json").normalize()
  }

  private def _storyboard_part_artifact_record(projectroot: Path, mode: StoryboardBuildMode, part: VideoPartPlan): Json =
    Json.obj(
      "schema" -> Json.fromString("cozy.video.storyboard-part-artifact.v2"),
      "status" -> Json.fromString("validated"),
      "mode" -> Json.fromString(mode.name),
      "partId" -> Json.fromString(part.id),
      "output" -> Json.obj("path" -> Json.fromString(_display_path(projectroot, part.outputPath)))
    )

  private def _assemble_storyboard_mode(
    plan: VideoPlan,
    mode: StoryboardBuildMode,
    parts: Vector[StoryboardBuildPart],
    inputs: Vector[CozyFileUpdatePolicy.FileObservation],
    checks: Vector[VideoToolCheck],
    runner: VideoProcessRunner
  ): String = {
    _prepare_storyboard_mode_targets(plan, mode, parts)
    _validate_storyboard_part_outputs(plan)
    _preflight_storyboard_mode_destinations(plan, mode, parts, inputs)
    val staging = _new_staging_root(plan.projectRoot, mode)
    val stageoutput = staging.resolve("final.mp4")
    try {
      val partoutputs = plan.parts.filter(_.renderable).map(_.outputPath)
      val executionparts = _stage_execution_parts(plan, staging, partoutputs)
      val concatlist = staging.resolve("concat.txt")
      _write_ffmpeg_concat_list(plan.projectRoot, plan.execution, concatlist, executionparts)
      _run_build_ffmpeg(plan.projectRoot, plan.execution, concatlist, stageoutput, runner)
      _require_direct_regular_file(stageoutput, s"Staged Storyboard ${mode.name} output")
      val ffprobe = _ffprobe_summary(_run_build_ffprobe(plan.projectRoot, plan.execution, stageoutput, runner))
      _require_valid_ffprobe_summary(ffprobe, s"Staged Storyboard ${mode.name} output")
      val creditfiles = _stage_credit_files(plan, staging)
      val handoffs = parts.map { part =>
        val staged = staging.resolve("handoffs").resolve(s"${part.partplan.id}.json")
        _write_storyboard_json(staged, part.handoff, staging, s"Staged Storyboard handoff ${part.partplan.id}")
        CozyVideoStoryboardArtifacts.Artifact(staged, part.handoffpath)
      }
      val partartifacts = plan.parts.filter(_.renderable).map { part =>
        val staged = staging.resolve("part-artifacts").resolve(s"${part.id}.json")
        _write_storyboard_json(staged, _storyboard_part_artifact_record(plan.projectRoot, mode, part), staging, s"Staged Storyboard ${mode.name} part artifact ${part.id}")
        CozyVideoStoryboardArtifacts.Artifact(staged, _storyboard_part_artifact_path(plan.projectRoot, mode, part.id))
      }
      val stagedmanifest = staging.resolve("manifest.json")
      _write_storyboard_json(stagedmanifest, _storyboard_mode_record(plan, mode, parts, Some(ffprobe)), staging, s"Staged Storyboard ${mode.name} manifest")
      val artifacts = Vector(CozyVideoStoryboardArtifacts.Artifact(stageoutput, mode.outputpath), CozyVideoStoryboardArtifacts.Artifact(stagedmanifest, mode.manifestpath)) ++ handoffs ++ partartifacts ++ _credit_artifacts(creditfiles, mode)
      CozyVideoStoryboardArtifacts.install(staging, artifacts, inputs.map(x => Path.of(x.path)).toSet)
      _delete_staging(staging)
      _render_storyboard_build_cache_miss(plan, mode, parts, checks, ffprobe)
    } catch {
      case NonFatal(error) =>
        _storyboard_build_failure(s"Storyboard ${mode.name} assembly failed; preserved artifacts remain recoverable under $staging: ${error.getMessage}")
    }
  }

  private def _new_staging_root(projectroot: Path, mode: StoryboardBuildMode): Path = {
    val root = _target_root(projectroot).resolve("staging").resolve(mode.name).resolve(s"attempt-${UUID.randomUUID().toString}").normalize()
    _ensure_direct_directory(root, projectroot, s"Storyboard ${mode.name} staging root")
  }

  private def _stage_execution_parts(plan: VideoPlan, staging: Path, partoutputs: Vector[Path]): Vector[Path] =
    plan.execution.toolMode match {
      case VideoToolMode.Docker =>
        val directory = staging.resolve("parts")
        _ensure_direct_directory(directory, staging, "Storyboard Docker staged parts")
        partoutputs.zipWithIndex.map { case (source, index) =>
          val destination = directory.resolve(f"part-${index + 1}%02d.mp4")
          Files.copy(source, destination, StandardCopyOption.COPY_ATTRIBUTES)
          destination
        }
      case _ => partoutputs
    }

  private def _stage_credit_files(plan: VideoPlan, staging: Path): Option[CozyVideoCredits.OutputFiles] =
    plan.credits.profile.map { _ =>
      CozyVideoCredits.write(staging.resolve("credits"), plan.credits)
    }

  private def _credit_artifacts(files: Option[CozyVideoCredits.OutputFiles], mode: StoryboardBuildMode): Vector[CozyVideoStoryboardArtifacts.Artifact] =
    files.toVector.flatMap { value =>
      val destinations = _credit_destination_paths(mode)
      Vector(
        CozyVideoStoryboardArtifacts.Artifact(value.jsonFile, destinations(0)),
        CozyVideoStoryboardArtifacts.Artifact(value.markdownFile, destinations(1)),
        CozyVideoStoryboardArtifacts.Artifact(value.rendererPropsFile, destinations(2))
      )
    }

  private def _credit_files_current(plan: VideoPlan, mode: StoryboardBuildMode): Boolean =
    plan.credits.profile.isEmpty || Vector("credits.json", "credits.md", "renderer-props.json").forall { name =>
      _is_direct_nonempty_file(mode.outputpath.getParent.resolve("credits").resolve(name))
    }

  private def _delete_staging(path: Path): Unit = {
    val stream = Files.walk(path)
    try stream.iterator().asScala.toVector.sortBy(_.getNameCount).reverse.foreach(Files.deleteIfExists)
    finally stream.close()
  }

  private def _record_contains(path: Path, required: Json): Boolean =
    if (!_is_direct_nonempty_file(path)) false
    else try parser.parse(Files.readString(path, StandardCharsets.UTF_8)).toOption.exists(CozyVideoStoryboardRecordValidation.contains(_, required))
    catch { case NonFatal(_) => false }

  private def _valid_handoff(projectroot: Path, part: StoryboardBuildPart): Boolean =
    if (!_is_direct_nonempty_file(part.handoffpath)) false
    else try {
      parser.parse(Files.readString(part.handoffpath, StandardCharsets.UTF_8)).toOption.exists { json =>
        CozyVideoStoryboardRecordValidation.contains(json, Json.obj(
          "schema" -> Json.fromString(_handoff_schema),
          "status" -> Json.fromString("validated"),
          "partId" -> Json.fromString(part.partplan.id),
          "sourcePath" -> Json.fromString(_display_path(projectroot, part.sourcepath)),
          "storyboardSection" -> part.storyboardsection.map(Json.fromString).getOrElse(Json.Null)
        )) && CozyVideoStoryboardRecordValidation.handoffPayloadShape(json)
      }
    } catch { case NonFatal(_) => false }

  private def _stored_ffprobe_valid(manifest: Path): Boolean =
    try parser.parse(Files.readString(manifest, StandardCharsets.UTF_8)).toOption.flatMap(_.hcursor.downField("ffprobe").focus).exists(CozyVideoStoryboardRecordValidation.ffprobeSummaryValid)
    catch { case NonFatal(_) => false }

  private def _require_valid_ffprobe_summary(summary: Json, label: String): Unit =
    if (!CozyVideoStoryboardRecordValidation.ffprobeSummaryValid(summary))
      _storyboard_build_failure(s"$label ffprobe summary has no finite positive duration and video stream")

  private def _render_storyboard_build_dry_run(
    config: BuildConfig,
    plan: VideoPlan,
    mode: StoryboardBuildMode,
    checks: Vector[VideoToolCheck]
  ): String = {
    val base = _render_build_dry_run(config, plan, checks).stripSuffix("\n")
    Vector(
      base,
      s"mode: ${mode.name}",
      s"expectedOutput: ${mode.outputpath}",
      s"expectedManifest: ${mode.manifestpath}"
    ).mkString("\n") + "\n"
  }

  private def _render_storyboard_build_cache_hit(
    plan: VideoPlan,
    mode: StoryboardBuildMode
  ): String = {
    val b = Vector.newBuilder[String]
    b ++= Vector(
      "Cozy Video Storyboard Build",
      s"projectFile: ${plan.projectFile}",
      s"mode: ${mode.name}",
      s"toolMode: ${plan.execution.toolMode.label}",
      s"output: ${mode.outputpath}",
      s"manifest: ${mode.manifestpath}",
      "cache: hit"
    )
    plan.credits.profileId.foreach(x => b += s"creditProfile: $x")
    plan.credits.warnings.foreach { warning =>
      b += s"creditWarning: ${warning.code}: ${warning.message}"
    }
    b.result().mkString("\n") + "\n"
  }

  private def _render_storyboard_build_cache_miss(
    plan: VideoPlan,
    mode: StoryboardBuildMode,
    parts: Vector[StoryboardBuildPart],
    checks: Vector[VideoToolCheck],
    ffprobe: Json
  ): String = {
    val values = Vector(
      "Cozy Video Storyboard Build",
      s"projectFile: ${plan.projectFile}",
      s"mode: ${mode.name}",
      s"toolMode: ${plan.execution.toolMode.label}",
      s"output: ${mode.outputpath}",
      s"manifest: ${mode.manifestpath}",
      s"handoffs: ${parts.map(_.handoffpath).mkString(",")}",
      "cache: miss",
      s"ffprobe: ${ffprobe.noSpaces}"
    ) ++ checks.map(check => s"tool: ${check.name}: ${check.status.label}: ${check.message}") ++
      plan.credits.profileId.toVector.map(value => s"creditProfile: $value") ++
      plan.credits.profile.toVector.map(_ => s"credits: ${_credit_destination_paths(mode).mkString(",")}") ++
      plan.credits.warnings.map(warning => s"creditWarning: ${warning.code}: ${warning.message}")
    values.mkString("\n") + "\n"
  }

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

  private def _is_direct_nonempty_file(path: Path): Boolean =
    try _is_direct_regular_file(path) && _safe_generated_path(path.toAbsolutePath.normalize()).isEmpty && Files.size(path) > 0
    catch { case NonFatal(_) => false }

  private def _require_direct_regular_file(path: Path, label: String): Unit =
    if (!_is_direct_regular_file(path))
      _storyboard_build_failure(s"$label is missing or not a direct regular non-symlink file: $path")

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
