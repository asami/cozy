package cozy.media

import java.nio.ByteBuffer
import java.nio.charset.{CodingErrorAction, StandardCharsets}
import java.nio.file.{AtomicMoveNotSupportedException, Files, LinkOption, Path, Paths, StandardCopyOption}
import java.security.MessageDigest

import com.fasterxml.jackson.core.{JsonFactory, JsonToken}

import scala.collection.mutable
import scala.util.control.NonFatal

import cozy.video.CozyVideoImplementation

import CozyExplanationProjectionCodec._

/*
 * @since   Aug. 28, 2026
 * @version Aug. 28, 2026
 * @author  ASAMI, Tomoharu
 */
private[cozy] object CozyExplanationProjection {
  final case class PresentationStepMapping(stepId: String, pageIds: Vector[String])
  final case class VideoStepMapping(stepId: String, sceneIds: Vector[String])
  final case class PresentationMapping(stepMappings: Vector[PresentationStepMapping], identity: String)
  final case class VideoMapping(stepMappings: Vector[VideoStepMapping], identity: String)
  final case class ProjectionMap(
    compositionIdentity: String,
    planIdentity: String,
    explanationCatalog: CozyExplanation.CatalogSelector,
    presentationCatalog: CozyExplanation.CatalogSelector,
    presentation: PresentationMapping,
    video: VideoMapping,
    identity: String
  )
  final case class VisualPageSetSelector(id: String, identity: String)
  final case class StoryboardSelector(identity: String)
  final case class ProjectionPresentation(
    visualPageSet: VisualPageSetSelector,
    stepMappings: Vector[PresentationStepMapping],
    identity: String
  )
  final case class ProjectionVideo(
    storyboard: StoryboardSelector,
    stepMappings: Vector[VideoStepMapping],
    identity: String
  )
  final case class ProjectionReceipt(
    compositionIdentity: String,
    planIdentity: String,
    projectionMapIdentity: String,
    explanationCatalogIdentity: String,
    presentationLogicalCatalogIdentity: String,
    presentationCatalogIdentity: String,
    visualPageSetIdentity: String,
    storyboardIdentity: String,
    presentationMappingIdentity: String,
    videoMappingIdentity: String,
    identity: String
  )
  final case class Projection(
    compositionIdentity: String,
    planIdentity: String,
    projectionMapIdentity: String,
    explanationCatalog: CozyExplanation.CatalogSelector,
    presentationCatalog: CozyExplanation.CatalogSelector,
    presentation: ProjectionPresentation,
    video: ProjectionVideo,
    receipt: ProjectionReceipt,
    identity: String
  )
  final case class ValidatedProjectionMap(
    projectionMap: ProjectionMap,
    canonicalJson: String,
    plan: CozyExplanation.ValidatedPlan
  )
  final case class ValidatedProjection(
    projection: Projection,
    canonicalJson: String,
    projectionMap: ValidatedProjectionMap
  )

  private final case class MediaClosure(
    visualpageset: CozyVisualPage.ValidatedDocument,
    visualpagesetpath: Path,
    storyboard: CozyVideoImplementation.Storyboard,
    storyboardidentity: String,
    storyboardpath: Path
  )
  private[media] final case class CommandConfig(
    input: Option[Path],
    projectionmap: Option[Path],
    composition: Option[Path],
    plan: Option[Path],
    explanationcatalog: Option[Path],
    presentationcatalog: Option[Path],
    visualpageset: Option[Path],
    storyboard: Option[Path],
    save: Option[Path],
    bindings: CozyExplanation.ResourceBindings
  )

  private[media] val _projection_map_schema = "cozy.explanation-projection-map.v1"
  private[media] val _projection_schema = "cozy.explanation-projection.v1"
  private[media] val _token_pattern = "[A-Za-z0-9][A-Za-z0-9._-]*".r
  private[media] val _identity_pattern = "sha256:[0-9a-f]{64}".r

  def parseProjectionMapJson(text: String): ProjectionMap =
    _parse_projection_map(_parse_json(text, "$"), "$")

  def parseProjectionJson(text: String): Projection =
    _parse_projection(_parse_json(text, "$"), "$")

  def canonicalProjectionMapJson(value: ProjectionMap): String =
    _canonical(_projection_map_value(value, includeidentity = true))

  def projectionMapIdentity(value: ProjectionMap): String =
    _identity(_projection_map_value(value, includeidentity = false))

  def canonicalProjectionJson(value: Projection): String =
    _canonical(_projection_value(value, includeidentity = true))

  def projectionIdentity(value: Projection): String =
    _identity(_projection_value(value, includeidentity = false))

  def presentationMappingIdentity(value: Vector[PresentationStepMapping]): String =
    _identity(_presentation_mapping_value(value, includeidentity = false, identity = ""))

  def videoMappingIdentity(value: Vector[VideoStepMapping]): String =
    _identity(_video_mapping_value(value, includeidentity = false, identity = ""))

  def loadProjectionMap(
    input: Path,
    composition: Path,
    plan: Path,
    explanationCatalog: Path,
    presentationCatalog: Path,
    bindings: CozyExplanation.ResourceBindings = CozyExplanation.ResourceBindings()
  ): ValidatedProjectionMap = {
    val path = _direct_input(input, "projection-map")
    val validatedplan = _load_current_plan(plan, composition, explanationCatalog, presentationCatalog, bindings)
    val value = _parse_projection_map(_parse_json(_read_utf8(path, "projection-map"), "projection-map"), "$")
    _validate_projection_map(value, validatedplan)
    ValidatedProjectionMap(value, canonicalProjectionMapJson(value), validatedplan)
  }

  private def _load_current_plan(
    plan: Path,
    composition: Path,
    explanationcatalog: Path,
    presentationcatalog: Path,
    bindings: CozyExplanation.ResourceBindings
  ): CozyExplanation.ValidatedPlan = try {
    CozyExplanation.loadPlan(plan, composition, explanationcatalog, presentationcatalog, bindings)
  } catch {
    case fault: CozyExplanation.ExplanationFault if Set(
      "EXPLANATION_CATALOG_MISMATCH", "EXPLANATION_EXPANSION_INCOMPLETE", "EXPLANATION_ASSET_STALE", "EXPLANATION_REFERENCE_INVALID"
    ).contains(fault.code) => _stale(fault.path, fault.reason)
  }

  def loadProjection(
    input: Path,
    composition: Path,
    plan: Path,
    projectionMap: Path,
    explanationCatalog: Path,
    presentationCatalog: Path,
    visualPageSet: Path,
    storyboard: Path,
    bindings: CozyExplanation.ResourceBindings = CozyExplanation.ResourceBindings()
  ): ValidatedProjection = {
    val path = _direct_input(input, "projection")
    val validatedmap = loadProjectionMap(projectionMap, composition, plan, explanationCatalog, presentationCatalog, bindings)
    val media = _load_media(visualPageSet, storyboard, presentationCatalog, validatedmap.plan)
    val value = _parse_projection(_parse_json(_read_utf8(path, "projection"), "projection"), "$")
    _validate_projection(value, validatedmap, media, composition, explanationCatalog, presentationCatalog, bindings)
    ValidatedProjection(value, canonicalProjectionJson(value), validatedmap)
  }

  def execute(args: List[String]): String = args match {
    case "validate" :: rest => _execute_document("validate", _command_config(rest, requiresave = false))
    case "inspect" :: rest => _execute_document("inspect", _command_config(rest, requiresave = false))
    case "convert" :: rest => _execute_document("convert", _command_config(rest, requiresave = true))
    case "project" :: rest => _execute_project(_command_config(rest, requiresave = true))
    case "verify-projection" :: rest => _execute_verify(_command_config(rest, requiresave = false))
    case Nil => _fail("EXPLANATION_COMMAND_INVALID", "$command", "missing explanation action")
    case action :: _ => _fail("EXPLANATION_COMMAND_INVALID", "$command", s"unsupported explanation projection action: $action")
  }

  def isProjectionDocumentCommand(args: List[String]): Boolean = {
    _input_argument(args).flatMap { input =>
      try {
        val path = _direct_input(Paths.get(input), "input")
        val fields = _object(_parse_json(_read_utf8(path, "input"), "input"), "$")
        _field_optional(fields, "schema").collect {
          case CozyExplanation.JsonString(schema) => schema
        }
      } catch {
        case NonFatal(_) => None
      }
    }.exists(schema => schema == _projection_map_schema || schema == _projection_schema)
  }

  private def _execute_document(action: String, config: CommandConfig): String = {
    val input = config.input.getOrElse(_missing_input())
    _schema_kind(input) match {
      case "projection-map" =>
        _reject_media_companions(config, action, "ProjectionMap")
        val validated = _load_command_map(input, config)
        action match {
          case "validate" => _map_summary(validated)
          case "inspect" => _map_inspection(validated)
          case "convert" =>
            _atomic_write(config.save.getOrElse(_missing_save()), validated.canonicalJson + "\n")
            _map_summary(validated)
        }
      case "projection" =>
        val validated = _load_command_projection(input, config)
        action match {
          case "validate" => _projection_summary(validated)
          case "inspect" => _projection_inspection(validated)
          case "convert" =>
            _atomic_write(config.save.getOrElse(_missing_save()), validated.canonicalJson + "\n")
            _projection_summary(validated)
        }
    }
  }

  private def _execute_project(config: CommandConfig): String = {
    if (config.input.nonEmpty)
      _fail("EXPLANATION_COMMAND_INVALID", "$command.input", "project accepts ProjectionMap only through --projection-map")
    val map = config.projectionmap.getOrElse(_missing_companion("--projection-map"))
    val composition = config.composition.getOrElse(_missing_companion("--composition"))
    val plan = config.plan.getOrElse(_missing_companion("--plan"))
    val explanationcatalog = config.explanationcatalog.getOrElse(_missing_companion("--explanation-catalog"))
    val presentationcatalog = config.presentationcatalog.getOrElse(_missing_companion("--presentation-catalog"))
    val visualpageset = config.visualpageset.getOrElse(_missing_companion("--visual-page-set"))
    val storyboard = config.storyboard.getOrElse(_missing_companion("--storyboard"))
    val validatedmap = loadProjectionMap(map, composition, plan, explanationcatalog, presentationcatalog, config.bindings)
    val media = _load_media(visualpageset, storyboard, presentationcatalog, validatedmap.plan)
    _validate_media_agreement(
      validatedmap,
      media,
      config.composition.getOrElse(_missing_companion("--composition")),
      config.explanationcatalog.getOrElse(_missing_companion("--explanation-catalog")),
      config.presentationcatalog.getOrElse(_missing_companion("--presentation-catalog")),
      config.bindings
    )
    val projection = _projection_from(validatedmap, media)
    _atomic_write(config.save.getOrElse(_missing_save()), canonicalProjectionJson(projection) + "\n")
    _projection_summary(ValidatedProjection(projection, canonicalProjectionJson(projection), validatedmap))
  }

  private def _execute_verify(config: CommandConfig): String = {
    val input = config.input.getOrElse(_missing_input())
    val validated = _load_command_projection(input, config)
    _projection_summary(validated)
  }

  private def _load_command_map(input: Path, config: CommandConfig): ValidatedProjectionMap = {
    if (config.projectionmap.nonEmpty || config.visualpageset.nonEmpty || config.storyboard.nonEmpty)
      _fail("EXPLANATION_COMMAND_INVALID", "$command", "ProjectionMap operations do not accept Projection or media companions")
    loadProjectionMap(
      input,
      config.composition.getOrElse(_missing_companion("--composition")),
      config.plan.getOrElse(_missing_companion("--plan")),
      config.explanationcatalog.getOrElse(_missing_companion("--explanation-catalog")),
      config.presentationcatalog.getOrElse(_missing_companion("--presentation-catalog")),
      config.bindings
    )
  }

  private def _load_command_projection(input: Path, config: CommandConfig): ValidatedProjection =
    loadProjection(
      input,
      config.composition.getOrElse(_missing_companion("--composition")),
      config.plan.getOrElse(_missing_companion("--plan")),
      config.projectionmap.getOrElse(_missing_companion("--projection-map")),
      config.explanationcatalog.getOrElse(_missing_companion("--explanation-catalog")),
      config.presentationcatalog.getOrElse(_missing_companion("--presentation-catalog")),
      config.visualpageset.getOrElse(_missing_companion("--visual-page-set")),
      config.storyboard.getOrElse(_missing_companion("--storyboard")),
      config.bindings
    )

  private def _reject_media_companions(config: CommandConfig, action: String, label: String): Unit =
    if (config.visualpageset.nonEmpty || config.storyboard.nonEmpty)
      _fail("EXPLANATION_COMMAND_INVALID", "$command", s"$action $label accepts no media companions")

  private def _map_summary(validated: ValidatedProjectionMap): String = Vector(
    s"schema: ${_projection_map_schema}",
    "version: 1",
    s"identity: ${validated.projectionMap.identity}",
    s"planIdentity: ${validated.projectionMap.planIdentity}",
    s"presentationMappings: ${validated.projectionMap.presentation.stepMappings.size}",
    s"videoMappings: ${validated.projectionMap.video.stepMappings.size}"
  ).mkString("\n")

  private def _map_inspection(validated: ValidatedProjectionMap): String =
    (_map_summary(validated).split("\n").toVector ++
      validated.projectionMap.presentation.stepMappings.map(mapping =>
        s"presentation: ${mapping.stepId} pages=${mapping.pageIds.mkString(",")}") ++
      validated.projectionMap.video.stepMappings.map(mapping =>
        s"video: ${mapping.stepId} scenes=${mapping.sceneIds.mkString(",")}")
    ).mkString("\n")

  private def _projection_summary(validated: ValidatedProjection): String = Vector(
    s"schema: ${_projection_schema}",
    "version: 1",
    s"identity: ${validated.projection.identity}",
    s"projectionMapIdentity: ${validated.projection.projectionMapIdentity}",
    s"visualPageSetIdentity: ${validated.projection.presentation.visualPageSet.identity}",
    s"storyboardIdentity: ${validated.projection.video.storyboard.identity}"
  ).mkString("\n")

  private def _projection_inspection(validated: ValidatedProjection): String =
    (_projection_summary(validated).split("\n").toVector ++
      validated.projection.presentation.stepMappings.map(mapping =>
        s"presentation: ${mapping.stepId} pages=${mapping.pageIds.mkString(",")}") ++
      validated.projection.video.stepMappings.map(mapping =>
        s"video: ${mapping.stepId} scenes=${mapping.sceneIds.mkString(",")}") ++
      Vector(s"receiptIdentity: ${validated.projection.receipt.identity}")
    ).mkString("\n")

  private def _validate_projection_map(value: ProjectionMap, plan: CozyExplanation.ValidatedPlan): Unit = {
    val expected = plan.plan
    if (value.identity != projectionMapIdentity(value))
      _stale("$.identity", "ProjectionMap identity does not match its canonical content")
    if (value.compositionIdentity != expected.compositionIdentity)
      _stale("$.compositionIdentity", "ProjectionMap Composition selector is not current")
    if (value.planIdentity != expected.identity)
      _stale("$.planIdentity", "ProjectionMap Plan selector is not current")
    if (value.explanationCatalog != expected.explanationCatalog)
      _stale("$.explanationCatalog", "ProjectionMap explanation catalog selector does not match the named catalog")
    if (value.presentationCatalog != expected.presentationCatalog)
      _stale("$.presentationCatalog", "ProjectionMap presentation catalog selector does not match the named catalog")
    if (value.presentation.identity != presentationMappingIdentity(value.presentation.stepMappings))
      _stale("$.presentation.identity", "presentation mapping identity does not match its canonical content")
    if (value.video.identity != videoMappingIdentity(value.video.stepMappings))
      _stale("$.video.identity", "video mapping identity does not match its canonical content")
    _validate_presentation_mapping(value.presentation.stepMappings, expected.steps, "$.presentation.stepMappings")
    _validate_video_mapping(value.video.stepMappings, expected.steps, "$.video.stepMappings")
  }

  private def _validate_presentation_mapping(
    mappings: Vector[PresentationStepMapping],
    steps: Vector[CozyExplanation.PlanStep],
    path: String
  ): Unit = {
    if (mappings.map(_.stepId) != steps.map(_.id))
      _stale(path, "presentation mappings must contain each Plan step exactly once in Plan order")
    mappings.zipWithIndex.foreach { case (mapping, index) =>
      if (mapping.pageIds.isEmpty) _stale(s"$path[$index].pageIds", "each Plan step requires nonempty page IDs")
      _unique(mapping.pageIds, s"$path[$index].pageIds", "page ID")
    }
  }

  private def _validate_video_mapping(
    mappings: Vector[VideoStepMapping],
    steps: Vector[CozyExplanation.PlanStep],
    path: String
  ): Unit = {
    if (mappings.map(_.stepId) != steps.map(_.id))
      _stale(path, "video mappings must contain each Plan step exactly once in Plan order")
    mappings.zipWithIndex.foreach { case (mapping, index) =>
      if (mapping.sceneIds.isEmpty) _stale(s"$path[$index].sceneIds", "each Plan step requires nonempty scene IDs")
      _unique(mapping.sceneIds, s"$path[$index].sceneIds", "scene ID")
    }
  }

  private def _validate_projection(
    value: Projection,
    map: ValidatedProjectionMap,
    media: MediaClosure,
    composition: Path,
    explanationcatalog: Path,
    presentationcatalog: Path,
    bindings: CozyExplanation.ResourceBindings
  ): Unit = {
    if (value.identity != projectionIdentity(value))
      _stale("$.identity", "Projection identity does not match its canonical content")
    _validate_media_agreement(map, media, composition, explanationcatalog, presentationcatalog, bindings)
    val expected = _projection_from(map, media)
    if (canonicalProjectionJson(value) != canonicalProjectionJson(expected))
      _stale("$", "Projection does not exactly preserve the current ProjectionMap and media closure")
    if (value.receipt.identity != _receipt_identity(value.receipt))
      _stale("$.receipt.identity", "Projection receipt identity does not match its canonical content")
  }

  private def _load_media(
    visualpageset: Path,
    storyboard: Path,
    presentationcatalog: Path,
    plan: CozyExplanation.ValidatedPlan
  ): MediaClosure = {
    val visualpath = _projection_input(visualpageset, "visual-page-set")
    val storyboardpath = _projection_input(storyboard, "storyboard")
    val pages = try CozyVisualPage.load(visualpath, presentationcatalog) catch {
      case NonFatal(error) => _stale("$command.visualPageSet", Option(error.getMessage).getOrElse("named VisualPageSet cannot be validated"))
    }
    pages.document match {
      case _: CozyVisualPage.PageSet => ()
      case _ => _stale("$command.visualPageSet", "named VisualPageSet must be cozy.visual-page-set.v1")
    }
    if (pages.catalogIdentity != plan.presentationCatalog.catalogIdentity)
      _stale("$.receipt.presentationCatalogIdentity", "named VisualPageSet full presentation catalog identity is not current")
    if (pages.logicalCatalogIdentity != plan.plan.presentationCatalog.identity)
      _stale("$.presentationCatalog.identity", "named VisualPageSet logical presentation catalog identity is not current")
    val result = CozyVideoImplementation.loadStoryboard(storyboardpath)
    if (!result.isValid)
      _stale("$command.storyboard", result.diagnostics.map(_.render).mkString("; "))
    val board = result.storyboard.getOrElse(_stale("$command.storyboard", "named Storyboard is missing"))
    if (board.schema != "cozy.video.storyboard.v2" || board.version != 2)
      _stale("$command.storyboard", "named Storyboard must be cozy.video.storyboard.v2")
    val closure = MediaClosure(pages, visualpath, board, CozyVideoImplementation.storyboardIdentity(board), storyboardpath)
    _validate_storyboard_visual_screens(closure, visualpath, _projection_input(presentationcatalog, "presentation-catalog"))
    closure
  }

  private def _validate_media_agreement(
    map: ValidatedProjectionMap,
    media: MediaClosure,
    composition: Path,
    explanationcatalog: Path,
    presentationcatalog: Path,
    bindings: CozyExplanation.ResourceBindings
  ): Unit = {
    val validatedcomposition = try {
      CozyExplanation.loadComposition(composition, explanationcatalog, presentationcatalog, bindings)
    } catch {
      case fault: CozyExplanation.ExplanationFault => _stale(fault.path, fault.reason)
    }
    val sourcedeclarations = validatedcomposition.composition.sources.map(value => value.id -> value).toMap
    val assetdeclarations = validatedcomposition.composition.assets.map(value => value.id -> value).toMap
    val pages = media.visualpageset.document.asInstanceOf[CozyVisualPage.PageSet].pages
    _unique(pages.map(_.id), "$.visualPageSet.pages", "VisualPageSet page ID")
    val pageindex = pages.map(page => page.id -> page).toMap
    val scenes = media.storyboard.scenes
    _unique(scenes.map(_.id), "$.storyboard.scenes", "Storyboard scene ID")
    val sceneindex = scenes.map(scene => scene.id -> scene).toMap
    map.plan.plan.steps.zipWithIndex.foreach { case (step, index) =>
      val pagemapping = map.projectionMap.presentation.stepMappings(index)
      val scenemapping = map.projectionMap.video.stepMappings(index)
      pagemapping.pageIds.foreach { pageid =>
        val page = pageindex.getOrElse(pageid, _stale(s"$$.presentation.stepMappings[$index].pageIds", s"mapped page ID does not resolve in the named VisualPageSet: $pageid"))
        if (page.logical != step.logical)
          _stale(s"$$.presentation.stepMappings[$index].pageIds", s"mapped page logical value does not equal Plan step ${step.id}")
        val sourceids = (step.sourceRefs ++ step.claims.flatMap(_.sourceRefs) ++ step.logical.nodes.flatMap(_.sourceRefs) ++ step.logical.relations.flatMap(_.sourceRefs)).toSet
        val assetids = (step.assetRefs ++ step.claims.flatMap(_.assetRefs)).toSet
        val pagesources = page.sources.map(value => value.id -> value).toMap
        val pageassets = page.assets.map(value => value.id -> value).toMap
        sourceids.find(id => !pagesources.contains(id)).foreach { id =>
          _stale(s"$$.presentation.stepMappings[$index].pageIds", s"mapped page is missing source provenance: $id")
        }
        assetids.find(id => !pageassets.contains(id)).foreach { id =>
          _stale(s"$$.presentation.stepMappings[$index].pageIds", s"mapped page is missing asset provenance: $id")
        }
        sourceids.foreach { id =>
          val declaration = sourcedeclarations.getOrElse(id,
            _stale(s"$$.presentation.stepMappings[$index].pageIds", s"Plan source does not resolve in the current Composition: $id"))
          val p36source = _descriptor_resource(media.visualpagesetpath, pagesources(id).path,
            s"$$.presentation.stepMappings[$index].pageIds.$pageid.sources.$id.path")
          val p37source = _projection_input(bindings.sources.getOrElse(id,
            _stale(s"$$.presentation.stepMappings[$index].pageIds.$pageid.sources.$id", "Composition source binding is missing")), s"source.$id")
          if (!java.util.Arrays.equals(Files.readAllBytes(p36source), Files.readAllBytes(p37source)))
            _stale(s"$$.presentation.stepMappings[$index].pageIds.$pageid.sources.$id", "P36 source bytes do not agree with the explicit P37 source binding")
          if (_sha256_bytes(Files.readAllBytes(p37source)) != declaration.sha256)
            _stale(s"$$.presentation.stepMappings[$index].pageIds.$pageid.sources.$id", "P37 source bytes do not agree with the current SourceDeclaration")
        }
        assetids.foreach { id =>
          val declaration = assetdeclarations.getOrElse(id,
            _stale(s"$$.presentation.stepMappings[$index].pageIds", s"Plan asset does not resolve in the current Composition: $id"))
          val p36asset = pageassets(id)
          val p36path = _descriptor_resource(media.visualpagesetpath, p36asset.path,
            s"$$.presentation.stepMappings[$index].pageIds.$pageid.assets.$id.path")
          val p37path = _projection_input(bindings.assets.getOrElse(id,
            _stale(s"$$.presentation.stepMappings[$index].pageIds.$pageid.assets.$id", "Composition asset binding is missing")), s"asset.$id")
          if (p36asset.mediaType != declaration.mediaType)
            _stale(s"$$.presentation.stepMappings[$index].pageIds.$pageid.assets.$id.mediaType", "P36 asset mediaType does not agree with the current P37 AssetDeclaration")
          if (p36asset.sha256 != declaration.sha256)
            _stale(s"$$.presentation.stepMappings[$index].pageIds.$pageid.assets.$id.sha256", "P36 asset digest does not agree with the current P37 AssetDeclaration")
          if (!java.util.Arrays.equals(Files.readAllBytes(p36path), Files.readAllBytes(p37path)))
            _stale(s"$$.presentation.stepMappings[$index].pageIds.$pageid.assets.$id", "P36 asset bytes do not agree with the explicit P37 asset binding")
        }
      }
      scenemapping.sceneIds.foreach { sceneid =>
        val scene = sceneindex.getOrElse(sceneid, _stale(s"$$.video.stepMappings[$index].sceneIds", s"mapped scene ID does not resolve in the named Storyboard: $sceneid"))
        scene.screen match {
          case screen: CozyVideoImplementation.StoryboardVisualPageScreen =>
            if (!pagemapping.pageIds.contains(screen.pageId))
              _stale(s"$$.video.stepMappings[$index].sceneIds", s"mapped Storyboard pageId does not belong to Plan step ${step.id}")
          case _ =>
            _stale(s"$$.video.stepMappings[$index].sceneIds", "mapped Storyboard scene must contain a v2 visual-page screen")
        }
      }
    }
  }

  private def _descriptor_resource(descriptor: Path, reference: String, path: String): Path = {
    if (!_safe_relative_reference(reference))
      _stale(path, "P36 resource reference must be a safe descriptor-relative path")
    val root = Option(descriptor.getParent).getOrElse(_stale(path, "VisualPageSet parent directory is required"))
    val resolved = root.resolve(reference).normalize()
    if (!resolved.startsWith(root) || !Files.isRegularFile(resolved, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(resolved))
      _stale(path, "P36 resource reference must resolve to an existing direct regular descriptor-relative file")
    resolved
  }

  private def _validate_storyboard_visual_screens(media: MediaClosure, visualpageset: Path, presentationcatalog: Path): Unit = {
    val pages = media.visualpageset.document.asInstanceOf[CozyVisualPage.PageSet].pages.map(_.id).toSet
    media.storyboard.scenes.zipWithIndex.foreach { case (scene, index) =>
      scene.screen match {
        case screen: CozyVideoImplementation.StoryboardVisualPageScreen =>
          _require_literal_reference(media.storyboardpath, screen.source, visualpageset, s"$$.storyboard.scenes[$index].screen.source")
          _require_literal_reference(media.storyboardpath, screen.catalog, presentationcatalog, s"$$.storyboard.scenes[$index].screen.catalog")
          if (!pages.contains(screen.pageId))
            _stale(s"$$.storyboard.scenes[$index].screen.pageId", "Storyboard visual-page screen does not resolve in the named VisualPageSet")
        case _ => ()
      }
    }
  }

  private def _require_literal_reference(storyboard: Path, value: String, expected: Path, path: String): Unit = {
    if (!_safe_relative_reference(value))
      _stale(path, "Storyboard visual-page reference must be a safe descriptor-relative path")
    val root = Option(storyboard.getParent).getOrElse(_stale(path, "Storyboard parent directory is required"))
    val resolved = root.resolve(value).normalize()
    if (!resolved.startsWith(root) || resolved != expected)
      _stale(path, "Storyboard visual-page reference does not resolve to the explicitly named input")
  }

  private def _projection_from(map: ValidatedProjectionMap, media: MediaClosure): Projection = {
    val projectionmap = map.projectionMap
    val pageset = media.visualpageset.document.asInstanceOf[CozyVisualPage.PageSet]
    val presentation = ProjectionPresentation(
      VisualPageSetSelector(pageset.id, media.visualpageset.documentIdentity),
      projectionmap.presentation.stepMappings,
      projectionmap.presentation.identity
    )
    val video = ProjectionVideo(
      StoryboardSelector(media.storyboardidentity),
      projectionmap.video.stepMappings,
      projectionmap.video.identity
    )
    val provisionalreceipt = ProjectionReceipt(
      projectionmap.compositionIdentity,
      projectionmap.planIdentity,
      projectionmap.identity,
      projectionmap.explanationCatalog.identity,
      projectionmap.presentationCatalog.identity,
      media.visualpageset.catalogIdentity,
      media.visualpageset.documentIdentity,
      media.storyboardidentity,
      presentation.identity,
      video.identity,
      ""
    )
    val receipt = provisionalreceipt.copy(identity = _receipt_identity(provisionalreceipt))
    val provisional = Projection(
      projectionmap.compositionIdentity,
      projectionmap.planIdentity,
      projectionmap.identity,
      projectionmap.explanationCatalog,
      projectionmap.presentationCatalog,
      presentation,
      video,
      receipt,
      ""
    )
    provisional.copy(identity = projectionIdentity(provisional))
  }


  private[media] def _command_config(args: List[String], requiresave: Boolean): CommandConfig = {
    var input = Option.empty[Path]
    var projectionmap = Option.empty[Path]
    var composition = Option.empty[Path]
    var plan = Option.empty[Path]
    var explanationcatalog = Option.empty[Path]
    var presentationcatalog = Option.empty[Path]
    var visualpageset = Option.empty[Path]
    var storyboard = Option.empty[Path]
    var save = Option.empty[Path]
    var sources = Map.empty[String, Path]
    var assets = Map.empty[String, Path]
    var rest = args
    while (rest.nonEmpty) {
      rest match {
        case option :: value :: tail if _value_options.contains(option) =>
          option match {
            case "--projection-map" => projectionmap = _single_path(projectionmap, value, "$command.projectionMap", option)
            case "--composition" => composition = _single_path(composition, value, "$command.composition", option)
            case "--plan" => plan = _single_path(plan, value, "$command.plan", option)
            case "--explanation-catalog" => explanationcatalog = _single_path(explanationcatalog, value, "$command.explanationCatalog", option)
            case "--presentation-catalog" => presentationcatalog = _single_path(presentationcatalog, value, "$command.presentationCatalog", option)
            case "--visual-page-set" => visualpageset = _single_path(visualpageset, value, "$command.visualPageSet", option)
            case "--storyboard" => storyboard = _single_path(storyboard, value, "$command.storyboard", option)
            case "--save" =>
              if (!requiresave) _fail("EXPLANATION_COMMAND_INVALID", "$command.save", "--save is supported only by convert or project")
              save = _single_path(save, value, "$command.save", option)
            case "--source" => sources = _add_binding(sources, value, "$command.source")
            case "--asset" => assets = _add_binding(assets, value, "$command.asset")
          }
          rest = tail
        case option :: tail if option.startsWith("--") && option.contains("=") =>
          val index = option.indexOf('=')
          val name = option.take(index)
          val value = option.drop(index + 1)
          if (!_value_options.contains(name)) _fail("EXPLANATION_COMMAND_INVALID", "$command", s"unsupported option: $name")
          name match {
            case "--projection-map" => projectionmap = _single_path(projectionmap, value, "$command.projectionMap", name)
            case "--composition" => composition = _single_path(composition, value, "$command.composition", name)
            case "--plan" => plan = _single_path(plan, value, "$command.plan", name)
            case "--explanation-catalog" => explanationcatalog = _single_path(explanationcatalog, value, "$command.explanationCatalog", name)
            case "--presentation-catalog" => presentationcatalog = _single_path(presentationcatalog, value, "$command.presentationCatalog", name)
            case "--visual-page-set" => visualpageset = _single_path(visualpageset, value, "$command.visualPageSet", name)
            case "--storyboard" => storyboard = _single_path(storyboard, value, "$command.storyboard", name)
            case "--save" =>
              if (!requiresave) _fail("EXPLANATION_COMMAND_INVALID", "$command.save", "--save is supported only by convert or project")
              save = _single_path(save, value, "$command.save", name)
            case "--source" => sources = _add_binding(sources, value, "$command.source")
            case "--asset" => assets = _add_binding(assets, value, "$command.asset")
          }
          rest = tail
        case option :: _ if option.startsWith("--") =>
          _fail("EXPLANATION_COMMAND_INVALID", "$command", s"unsupported or valueless option: $option")
        case value :: tail =>
          if (input.nonEmpty) _fail("EXPLANATION_COMMAND_INVALID", "$command.input", "exactly one direct input file is required")
          input = Some(_cli_path(value, "$command.input"))
          rest = tail
      }
    }
    if (requiresave && save.isEmpty) _missing_save()
    CommandConfig(input, projectionmap, composition, plan, explanationcatalog, presentationcatalog, visualpageset, storyboard, save, CozyExplanation.ResourceBindings(sources, assets))
  }

  private[media] val _value_options = Set(
    "--projection-map", "--composition", "--plan", "--explanation-catalog", "--presentation-catalog",
    "--visual-page-set", "--storyboard", "--save", "--source", "--asset"
  )

  private[media] def _single_path(current: Option[Path], value: String, path: String, option: String): Option[Path] = {
    if (current.nonEmpty) _fail("EXPLANATION_COMMAND_INVALID", path, s"$option may appear once")
    Some(_cli_path(value, path))
  }

  private[media] def _add_binding(bindings: Map[String, Path], value: String, path: String): Map[String, Path] = {
    val index = Option(value).map(_.indexOf('='))
    if (index.isEmpty || index.get <= 0 || index.get == value.length - 1)
      _fail("EXPLANATION_REFERENCE_INVALID", path, "binding must be exactly <id>=<file>")
    val id = value.take(index.get)
    val file = value.drop(index.get + 1)
    _token(id, s"$path.id")
    if (bindings.contains(id)) _fail("EXPLANATION_REFERENCE_INVALID", path, s"duplicate binding id: $id")
    bindings + (id -> _cli_path(file, path))
  }

  private[media] def _input_argument(args: List[String]): Option[String] = {
    var rest = args
    while (rest.nonEmpty) {
      rest match {
        case option :: _ :: tail if _value_options.contains(option) => rest = tail
        case option :: tail if option.startsWith("--") && option.contains("=") => rest = tail
        case option :: _ if option.startsWith("--") => return None
        case value :: _ => return Some(value)
      }
    }
    None
  }

  private[media] def _schema_kind(input: Path): String = {
    val path = _direct_input(input, "input")
    val fields = _object(_parse_json(_read_utf8(path, "input"), "input"), "$")
    _string(_field(fields, "schema", "$"), "$.schema") match {
      case `_projection_map_schema` => "projection-map"
      case `_projection_schema` => "projection"
      case schema => _fail("EXPLANATION_SCHEMA_INVALID", "$.schema", s"unsupported explanation projection schema: $schema")
    }
  }

  private[media] def _parse_json(text: String, source: String): CozyExplanation.JsonValue = {
    if (text == null || text.isEmpty) _fail("EXPLANATION_SCHEMA_INVALID", source, "JSON input is required")
    val parser = new JsonFactory().createParser(text)
    try {
      if (parser.nextToken() == null) _fail("EXPLANATION_SCHEMA_INVALID", source, "JSON input is required")
      val value = _parse_json_value(parser, source)
      if (parser.nextToken() != null) _fail("EXPLANATION_SCHEMA_INVALID", source, "trailing JSON tokens are not admitted")
      value
    } catch {
      case fault: CozyExplanation.ExplanationFault => throw fault
      case NonFatal(error) => _fail("EXPLANATION_SCHEMA_INVALID", source, Option(error.getMessage).getOrElse("invalid JSON"))
    } finally parser.close()
  }

  private[media] def _parse_json_value(parser: com.fasterxml.jackson.core.JsonParser, source: String): CozyExplanation.JsonValue = parser.getCurrentToken match {
    case JsonToken.START_OBJECT =>
      val fields = Vector.newBuilder[(String, CozyExplanation.JsonValue)]
      val seen = mutable.Set.empty[String]
      while (parser.nextToken() != JsonToken.END_OBJECT) {
        if (parser.getCurrentToken != JsonToken.FIELD_NAME) _fail("EXPLANATION_SCHEMA_INVALID", source, "JSON object member name is required")
        val name = parser.getCurrentName
        if (seen.contains(name)) _fail("EXPLANATION_DUPLICATE_FIELD", source, s"duplicate JSON field: $name")
        seen += name
        if (parser.nextToken() == null) _fail("EXPLANATION_SCHEMA_INVALID", source, s"missing JSON value for field: $name")
        fields += name -> _parse_json_value(parser, source)
      }
      CozyExplanation.JsonObject(fields.result())
    case JsonToken.START_ARRAY =>
      val values = Vector.newBuilder[CozyExplanation.JsonValue]
      while (parser.nextToken() != JsonToken.END_ARRAY) values += _parse_json_value(parser, source)
      CozyExplanation.JsonArray(values.result())
    case JsonToken.VALUE_STRING => CozyExplanation.JsonString(parser.getText)
    case JsonToken.VALUE_TRUE => CozyExplanation.JsonBoolean(true)
    case JsonToken.VALUE_FALSE => CozyExplanation.JsonBoolean(false)
    case JsonToken.VALUE_NUMBER_INT =>
      try CozyExplanation.JsonNumber(parser.getLongValue) catch { case NonFatal(_) => _fail("EXPLANATION_SCHEMA_INVALID", source, "integer is outside supported range") }
    case JsonToken.VALUE_NUMBER_FLOAT => _fail("EXPLANATION_SCHEMA_INVALID", source, "non-finite or floating-point JSON numbers are not admitted")
    case JsonToken.VALUE_NULL => _fail("EXPLANATION_SCHEMA_INVALID", source, "JSON null is not admitted")
    case _ => _fail("EXPLANATION_SCHEMA_INVALID", source, s"unsupported JSON token: ${parser.getCurrentToken}")
  }

  private[media] def _canonical(value: CozyExplanation.JsonValue): String = value match {
    case CozyExplanation.JsonObject(fields) => fields.map { case (name, member) => _quote(name) + ":" + _canonical(member) }.mkString("{", ",", "}")
    case CozyExplanation.JsonArray(values) => values.map(_canonical).mkString("[", ",", "]")
    case CozyExplanation.JsonString(text) => _quote(text)
    case CozyExplanation.JsonNumber(number) => number.toString
    case CozyExplanation.JsonBoolean(boolean) => boolean.toString
  }

  private[media] def _quote(value: String): String = {
    val escaped = Option(value).getOrElse("").flatMap {
      case '"' => "\\\""
      case '\\' => "\\\\"
      case '\b' => "\\b"
      case '\f' => "\\f"
      case '\n' => "\\n"
      case '\r' => "\\r"
      case '\t' => "\\t"
      case character if character < ' ' => "\\u%04x".format(character.toInt)
      case character => character.toString
    }
    "\"" + escaped + "\""
  }

  private[media] def _identity(value: CozyExplanation.JsonValue): String =
    "sha256:" + _sha256_bytes(_canonical(value).getBytes(StandardCharsets.UTF_8))

  private[media] def _sha256_bytes(bytes: Array[Byte]): String =
    MessageDigest.getInstance("SHA-256").digest(bytes).map(value => f"${value & 0xff}%02x").mkString

  private[media] def _object(value: CozyExplanation.JsonValue, path: String): Vector[(String, CozyExplanation.JsonValue)] = value match {
    case CozyExplanation.JsonObject(fields) => fields
    case _ => _fail("EXPLANATION_SCHEMA_INVALID", path, "must be a JSON object")
  }

  private[media] def _array(value: CozyExplanation.JsonValue, path: String): Vector[CozyExplanation.JsonValue] = value match {
    case CozyExplanation.JsonArray(values) => values
    case _ => _fail("EXPLANATION_SCHEMA_INVALID", path, "must be a JSON array")
  }

  private[media] def _string(value: CozyExplanation.JsonValue, path: String): String = value match {
    case CozyExplanation.JsonString(text) => text
    case _ => _fail("EXPLANATION_SCHEMA_INVALID", path, "must be a JSON string")
  }

  private[media] def _field(fields: Vector[(String, CozyExplanation.JsonValue)], name: String, path: String): CozyExplanation.JsonValue =
    _field_optional(fields, name).getOrElse(_fail("EXPLANATION_SCHEMA_INVALID", s"$path.$name", "required field is missing"))

  private[media] def _field_optional(fields: Vector[(String, CozyExplanation.JsonValue)], name: String): Option[CozyExplanation.JsonValue] =
    fields.find(_._1 == name).map(_._2)

  private[media] def _exact_fields(fields: Vector[(String, CozyExplanation.JsonValue)], expected: Vector[String], path: String): Unit = {
    val actual = fields.map(_._1)
    actual.find(name => !expected.contains(name)).foreach(name => _fail("EXPLANATION_UNKNOWN_FIELD", s"$path.$name", "unknown field is not admitted"))
    expected.find(name => !actual.contains(name)).foreach(name => _fail("EXPLANATION_SCHEMA_INVALID", s"$path.$name", "required field is missing"))
  }

  private[media] def _schema_version(fields: Vector[(String, CozyExplanation.JsonValue)], schema: String, path: String): Unit = {
    if (_string(_field(fields, "schema", path), s"$path.schema") != schema)
      _fail("EXPLANATION_SCHEMA_INVALID", s"$path.schema", s"must be exactly $schema")
    _field(fields, "version", path) match {
      case CozyExplanation.JsonNumber(1) => ()
      case _ => _fail("EXPLANATION_SCHEMA_INVALID", s"$path.version", "must be integer 1")
    }
  }

  private[media] def _positive_int(value: CozyExplanation.JsonValue, path: String): Int = value match {
    case CozyExplanation.JsonNumber(number) if number > 0 && number <= Int.MaxValue => number.toInt
    case CozyExplanation.JsonNumber(_) => _fail("EXPLANATION_SCHEMA_INVALID", path, "must be a positive 32-bit integer")
    case _ => _fail("EXPLANATION_SCHEMA_INVALID", path, "must be a JSON integer")
  }

  private[media] def _token(value: String, path: String): String = {
    if (value == null || !_token_pattern.pattern.matcher(value).matches)
      _fail("EXPLANATION_SCHEMA_INVALID", path, "must be a nonempty stable token")
    value
  }

  private[media] def _identity_text(value: String, path: String): String = {
    if (value == null || !_identity_pattern.pattern.matcher(value).matches)
      _fail("EXPLANATION_SCHEMA_INVALID", path, "must be sha256:<64-lowercase-hex>")
    value
  }

  private[media] def _identity_field(fields: Vector[(String, CozyExplanation.JsonValue)], name: String, path: String): String =
    _identity_text(_string(_field(fields, name, path), s"$path.$name"), s"$path.$name")

  private[media] def _target_ids(value: CozyExplanation.JsonValue, path: String): Vector[String] = {
    val targets = _array(value, path).zipWithIndex.map { case (item, index) =>
      _token(_string(item, s"$path[$index]"), s"$path[$index]")
    }
    if (targets.isEmpty) _fail("EXPLANATION_SCHEMA_INVALID", path, "must be a nonempty ordered target array")
    _unique(targets, path, "target ID")
    targets
  }

  private[media] def _unique(values: Vector[String], path: String, label: String): Unit =
    values.groupBy(identity).collectFirst { case (value, duplicates) if duplicates.size > 1 => value }.foreach { value =>
      _fail("EXPLANATION_SCHEMA_INVALID", path, s"duplicate $label: $value")
    }

  private[media] def _safe_relative_reference(value: String): Boolean =
    value.nonEmpty &&
      !value.startsWith("/") &&
      !value.contains("\\") &&
      !value.contains(":") &&
      !value.contains("?") &&
      !value.contains("#") &&
      !value.exists(Character.isISOControl) &&
      value.split("/", -1).forall(segment => _token_pattern.pattern.matcher(segment).matches && segment != "." && segment != "..")

  private[media] def _direct_input(path: Path, label: String): Path = {
    if (path == null) _fail("EXPLANATION_REFERENCE_INVALID", label, "direct file path is required")
    val normalized = try path.toAbsolutePath.normalize() catch { case NonFatal(_) => _fail("EXPLANATION_REFERENCE_INVALID", label, "direct file path is invalid") }
    if (!Files.isRegularFile(normalized, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(normalized))
      _fail("EXPLANATION_REFERENCE_INVALID", label, "must be an existing direct regular non-symlink file")
    normalized
  }

  private[media] def _projection_input(path: Path, label: String): Path = try {
    _direct_input(path, label)
  } catch {
    case fault: CozyExplanation.ExplanationFault => _stale(label, fault.reason)
  }

  private[media] def _read_utf8(path: Path, label: String): String = try {
    val decoder = StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT)
    decoder.decode(ByteBuffer.wrap(Files.readAllBytes(path))).toString
  } catch {
    case fault: CozyExplanation.ExplanationFault => throw fault
    case NonFatal(error) => _fail("EXPLANATION_SCHEMA_INVALID", label, Option(error.getMessage).getOrElse("cannot read UTF-8 file"))
  }

  private[media] def _atomic_write(path: Path, text: String): Unit = {
    val output = try path.toAbsolutePath.normalize() catch { case NonFatal(_) => _fail("EXPLANATION_COMMAND_INVALID", "$command.save", "output path is invalid") }
    val parent = Option(output.getParent).getOrElse(_fail("EXPLANATION_COMMAND_INVALID", "$command.save", "output parent is required"))
    if (!Files.isDirectory(parent, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(parent))
      _fail("EXPLANATION_COMMAND_INVALID", "$command.save", "output parent must be an existing direct directory")
    if (Files.exists(output, LinkOption.NOFOLLOW_LINKS) && Files.isSymbolicLink(output))
      _fail("EXPLANATION_COMMAND_INVALID", "$command.save", "output must not be a symbolic link")
    val temporary = Files.createTempFile(parent, ".cozy-explanation-projection-", ".tmp")
    try {
      Files.write(temporary, text.getBytes(StandardCharsets.UTF_8))
      try Files.move(temporary, output, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
      catch {
        case _: AtomicMoveNotSupportedException => _fail("EXPLANATION_COMMAND_INVALID", "$command.save", "filesystem does not support same-directory atomic output replacement")
      }
    } catch {
      case fault: CozyExplanation.ExplanationFault => throw fault
      case NonFatal(error) => _fail("EXPLANATION_COMMAND_INVALID", "$command.save", Option(error.getMessage).getOrElse("cannot write output"))
    } finally {
      try Files.deleteIfExists(temporary) catch { case NonFatal(_) => () }
    }
  }

  private[media] def _cli_path(value: String, path: String): Path = {
    if (value == null || value.isEmpty || value != value.trim) _fail("EXPLANATION_COMMAND_INVALID", path, "path must be nonempty and trimmed")
    try Paths.get(value) catch { case NonFatal(_) => _fail("EXPLANATION_COMMAND_INVALID", path, "path is invalid") }
  }

  private[media] def _missing_companion(option: String): Nothing =
    _fail("EXPLANATION_COMMAND_INVALID", "$command", s"missing required $option")

  private[media] def _missing_input(): Nothing =
    _fail("EXPLANATION_COMMAND_INVALID", "$command.input", "missing direct input file")

  private[media] def _missing_save(): Nothing =
    _fail("EXPLANATION_COMMAND_INVALID", "$command.save", "selected operation requires exactly one --save")

  private[media] def _stale(path: String, reason: String): Nothing =
    _fail("EXPLANATION_PROJECTION_STALE", path, reason)

  private[media] def _fail(code: String, path: String, reason: String): Nothing =
    throw CozyExplanation.ExplanationFault(code, path, reason)
}
