package cozy.video

import cozy.media.{CozyVisualPage, CozyVisualPageBinding}
import cozy.runtime.CozyCliArgs
import com.fasterxml.jackson.core.{JsonFactory, JsonParseException, JsonParser => JacksonParser}
import io.circe.Json
import io.circe.parser
import java.nio.charset.StandardCharsets
import java.nio.file.{Files, LinkOption, Path, Paths, StandardCopyOption}
import java.nio.file.attribute.FileTime
import org.goldenport.RAISE
import org.goldenport.cli.spec
import scala.collection.JavaConverters._
import scala.util.control.NonFatal

/*
 * @since   Aug. 26, 2026
 *  version Sep. 30, 2026
 *  version Oct.  1, 2026
 * @version Oct.  3, 2026
 * @author  ASAMI, Tomoharu
 */
private[cozy] object CozyVideoStoryboardReview {
  final case class StoryboardReviewConfig(projectFile: Path, saveDir: Path) {
    def projectRoot: Path = projectFile.getParent
  }
  object StoryboardReviewConfig {
    def create(args: List[String]): StoryboardReviewConfig = {
      val parsed = CozyCliArgs.parseStrict(
        CozyVideoImplementation._p_project_file,
        CozyVideoImplementation._p_save
      )(CozyVideoImplementation._normalize_property_args(args))
      val projectfile = parsed.argument("project-file").map(CozyCliArgs.toPath).getOrElse(
        RAISE.invalidArgumentFault("Missing project file for video storyboard review-evidence")
      )
      StoryboardReviewConfig(projectfile, parsed.requiredPathProperty("save"))
    }
  }

  final case class StoryboardReviewResult(
    projectFile: Path,
    saveDir: Path,
    evidencePath: Path,
    handoffPath: Path
  )

  final case class StoryboardReviewCurrent(
    projectFile: Path,
    evidencePath: Path,
    handoffPath: Path
  )

  private final case class ReviewStoryboard[S](
    sourcepath: Path,
    relativepath: String,
    storyboard: S
  )

  private final case class VisualInput(
    reference: String,
    sourcepath: Path,
    relativepath: String,
    copiedpath: Path
  )

  private final case class VisualPageAsset(
    id: String,
    path: String,
    mediatype: String,
    modifiedat: FileTime
  )

  private final case class VisualPageProof(
    sceneid: String,
    source: String,
    catalog: String,
    pageid: String,
    assets: Vector[VisualPageAsset]
  )

  private final case class EffectiveRenderer(
    partid: String,
    configuration: Json
  )

  private final case class VisualPageReview(
    evidencedirectory: Path,
    pages: Vector[VisualPageProof],
    renderers: Vector[EffectiveRenderer]
  )
}

private[cozy] trait CozyVideoStoryboardReview {
  self: CozyVideoTypes with CozyVideoStoryboard with CozyVideoCommand with CozyVideoPlanning =>

  import CozyVideoStoryboardReview.{VisualInput, VisualPageAsset, VisualPageProof, EffectiveRenderer, VisualPageReview}

  type StoryboardReviewConfig = CozyVideoStoryboardReview.StoryboardReviewConfig
  lazy val StoryboardReviewConfig = CozyVideoStoryboardReview.StoryboardReviewConfig
  type StoryboardReviewResult = CozyVideoStoryboardReview.StoryboardReviewResult
  lazy val StoryboardReviewResult = CozyVideoStoryboardReview.StoryboardReviewResult
  type StoryboardReviewCurrent = CozyVideoStoryboardReview.StoryboardReviewCurrent
  lazy val StoryboardReviewCurrent = CozyVideoStoryboardReview.StoryboardReviewCurrent

  private type ReviewStoryboard = CozyVideoStoryboardReview.ReviewStoryboard[Storyboard]

  private val _review_evidence_schema = "cozy.video.storyboard-review-evidence.v1"
  private val _storyboard_handoff_schema = "cozy.video.storyboard-handoff.v1"
  private val _review_evidence_schema_v2 = "cozy.video.storyboard-review-evidence.v2"
  private val _storyboard_handoff_schema_v2 = "cozy.video.storyboard-handoff.v2"
  private val _storyboard_schema_v1 = "cozy.video.storyboard.v1"
  private val _storyboard_schema_v2 = "cozy.video.storyboard.v2"

  def storyboardReviewEvidence(config: StoryboardReviewConfig): StoryboardReviewResult =
    _write_storyboard_review_evidence(config)

  def storyboardReview(config: StoryboardReviewConfig): String = {
    val result = storyboardReviewEvidence(config)
    Vector(
      s"evidence: ${result.evidencePath}",
      s"handoff: ${result.handoffPath}"
    ).mkString("\n")
  }

  def storyboardReviewCurrent(projectFile: Path): StoryboardReviewCurrent = {
    val plan = _plan(projectFile, None, None)
    val review = plan.project.storyboardReview.getOrElse(
      _review_failure("STORYBOARD_REVIEW_DECLARATION_MISSING", "video project does not declare storyboardReview")
    )
    val projectroot = _canonical_project_root(plan.projectRoot)
    val approved = _review_storyboard(projectroot, review)
    if (approved.storyboard.schema != _storyboard_schema_v2)
      _review_failure("STORYBOARD_REVIEW_CROSS_MEDIA_SCHEMA_UNSUPPORTED", "Cross-media Review requires Storyboard v2 visual-page evidence")
    val visualpage = _visual_page_review(projectroot, plan.project, review, approved).getOrElse(
      _review_failure("STORYBOARD_REVIEW_CROSS_MEDIA_VISUAL_PAGE_MISSING", "Cross-media Review requires a Storyboard v2 visual-page declaration")
    )
    _validate_storyboard_review_current(plan)
    val evidencepath = visualpage.evidencedirectory.resolve("review-evidence.json").normalize()
    val handoffpath = visualpage.evidencedirectory.resolve("handoff.json").normalize()
    StoryboardReviewCurrent(plan.projectFile, evidencepath, handoffpath)
  }

  private[video] def _validate_storyboard_review_current(plan: VideoPlan): Unit =
    plan.project.storyboardReview.foreach { review =>
      val projectroot = _canonical_project_root(plan.projectRoot)
      val approved = _review_storyboard(projectroot, review)
      approved.storyboard.schema match {
        case `_storyboard_schema_v1` =>
          if (review.visualPage.isDefined)
            _review_failure("STORYBOARD_REVIEW_VISUAL_PAGE_UNSUPPORTED", "storyboardReview.visualPage is permitted only for Storyboard v2 visual-page screens")
          review.visualStory.foreach { visualstory =>
            val evidencedirectory = _safe_evidence_directory(projectroot, visualstory.evidenceDirectory)
            val inputs = _admitted_visual_inputs(projectroot, evidencedirectory, approved.storyboard, visualstory)
            val evidencepath = evidencedirectory.resolve("review-evidence.json").normalize()
            _require_direct_output_file(evidencepath, evidencedirectory, "Storyboard review evidence")
            val evidence = _read_json(evidencepath, "Storyboard review evidence")
            _validate_current_evidence(evidence, inputs, evidencedirectory)
          }
        case `_storyboard_schema_v2` =>
          val visualpage = _visual_page_review(projectroot, plan.project, review, approved)
          val visualstorydirectory = review.visualStory.map(visualstory => _safe_evidence_directory(projectroot, visualstory.evidenceDirectory))
          _validate_evidence_directory_agreement(visualpage.map(_.evidencedirectory), visualstorydirectory)
          val evidencedirectory = visualpage.map(_.evidencedirectory).orElse(visualstorydirectory)
          evidencedirectory.foreach { directory =>
            val inputs = review.visualStory.map(visualstory => _admitted_visual_inputs(projectroot, directory, approved.storyboard, visualstory)).getOrElse(Vector.empty)
            val evidencepath = directory.resolve("review-evidence.json").normalize()
            _require_direct_output_file(evidencepath, directory, "Storyboard v2 review evidence")
            val evidence = _read_json(evidencepath, "Storyboard v2 review evidence")
            _validate_current_evidence_v2(evidence, inputs, directory)
            val handoffpath = directory.resolve("handoff.json").normalize()
            _require_direct_output_file(handoffpath, directory, "Storyboard v2 review handoff")
            _validate_current_handoff_v2(_read_json(handoffpath, "Storyboard v2 review handoff"))
          }
        case schema =>
          _review_failure("STORYBOARD_REVIEW_SCHEMA_UNSUPPORTED", s"Unsupported Storyboard schema: $schema")
      }
    }

  private[video] def _write_storyboard_review_evidence(config: StoryboardReviewConfig): StoryboardReviewResult = {
    val projectfile = _verified_project_file(config.projectFile)
    val projectroot = projectfile.getParent.toRealPath()
    val project = _load_project(projectfile)
    val review = project.storyboardReview.getOrElse(
      _review_failure("STORYBOARD_REVIEW_DECLARATION_MISSING", "video project does not declare storyboardReview")
    )
    val approved = _review_storyboard(projectroot, review)
    approved.storyboard.schema match {
      case `_storyboard_schema_v1` =>
        if (review.visualPage.isDefined)
          _review_failure("STORYBOARD_REVIEW_VISUAL_PAGE_UNSUPPORTED", "storyboardReview.visualPage is permitted only for Storyboard v2 visual-page screens")
        _write_storyboard_review_evidence_v1(projectfile, projectroot, review, approved, config)
      case `_storyboard_schema_v2` =>
        _write_storyboard_review_evidence_v2(projectfile, projectroot, project, review, approved, config)
      case schema =>
        _review_failure("STORYBOARD_REVIEW_SCHEMA_UNSUPPORTED", s"Unsupported Storyboard schema: $schema")
    }
  }

  private def _write_storyboard_review_evidence_v1(
    projectfile: Path,
    projectroot: Path,
    review: StoryboardReview,
    approved: ReviewStoryboard,
    config: StoryboardReviewConfig
  ): StoryboardReviewResult = {
    val expectedvisualdirectory = review.visualStory.map { visualstory =>
      _safe_evidence_directory(projectroot, visualstory.evidenceDirectory)
    }
    val savedir = _prepare_save_directory(config.saveDir)
    val inputs = review.visualStory match {
      case Some(visualstory) =>
        val evidencedirectory = expectedvisualdirectory.get
        if (savedir != evidencedirectory)
          _review_failure(
            "STORYBOARD_REVIEW_SAVE_MISMATCH",
            s"--save must equal visualStory.evidenceDirectory: expected $evidencedirectory, found $savedir"
          )
        _admitted_visual_inputs(projectroot, savedir, approved.storyboard, visualstory).map { input =>
          _copy_visual_input(input, savedir)
        }
      case None =>
        Vector.empty
    }
    val visualdirectory = review.visualStory.map(_ => _ensure_direct_directory(savedir.resolve("visual-inputs").normalize(), savedir, "Storyboard visual-inputs"))
    val payload = _review_evidence_payload_v1(approved, inputs, review.visualStory.isDefined)
    val evidencepath = savedir.resolve("review-evidence.json").normalize()
    _write_json(evidencepath, payload, savedir)
    val handoffpath = savedir.resolve("handoff.json").normalize()
    val handofffields = Vector(
      Some("schema" -> Json.fromString(_storyboard_handoff_schema)),
      Some("evidencePath" -> Json.fromString(_display_path(projectroot, evidencepath)))
    ).flatten
    _write_json(handoffpath, Json.obj(handofffields: _*), savedir)
    visualdirectory.foreach(_ => ())
    StoryboardReviewResult(
      projectfile,
      savedir,
      evidencepath,
      handoffpath
    )
  }

  private def _write_storyboard_review_evidence_v2(
    projectfile: Path,
    projectroot: Path,
    project: VideoProject,
    review: StoryboardReview,
    approved: ReviewStoryboard,
    config: StoryboardReviewConfig
  ): StoryboardReviewResult = {
    val visualpage = _visual_page_review(projectroot, project, review, approved)
    val visualstorydirectory = review.visualStory.map(visualstory => _safe_evidence_directory(projectroot, visualstory.evidenceDirectory))
    _validate_evidence_directory_agreement(visualpage.map(_.evidencedirectory), visualstorydirectory)
    val expecteddirectory = visualpage.map(_.evidencedirectory).orElse(visualstorydirectory)
    val savedir = _prepare_save_directory(config.saveDir)
    expecteddirectory.foreach { directory =>
      if (savedir != directory)
        _review_failure(
          "STORYBOARD_REVIEW_SAVE_MISMATCH",
          s"--save must equal the declared v2 evidenceDirectory: expected $directory, found $savedir"
        )
    }
    val inputs = review.visualStory.map { visualstory =>
      _admitted_visual_inputs(projectroot, savedir, approved.storyboard, visualstory).map { input =>
        _copy_visual_input(input, savedir)
      }
    }.getOrElse(Vector.empty)
    review.visualStory.foreach(_ => _ensure_direct_directory(savedir.resolve("visual-inputs").normalize(), savedir, "Storyboard visual-inputs"))
    val payload = _review_evidence_payload_v2(approved, inputs, visualpage)
    val evidencepath = savedir.resolve("review-evidence.json").normalize()
    _write_json(evidencepath, payload, savedir)
    val handoffpath = savedir.resolve("handoff.json").normalize()
    val handoffpayload = _storyboard_handoff_payload_v2(projectroot, evidencepath, inputs, visualpage)
    _write_json(handoffpath, handoffpayload, savedir)
    StoryboardReviewResult(
      projectfile,
      savedir,
      evidencepath,
      handoffpath
    )
  }

  private def _review_storyboard(projectroot: Path, review: StoryboardReview): ReviewStoryboard = {
    val sourcepath = _safe_project_relative_file(projectroot, review.source, "Storyboard review source")
    val result = loadStoryboard(sourcepath)
    if (!result.isValid)
      _review_failure(
        "STORYBOARD_REVIEW_SOURCE_INVALID",
        result.diagnostics.map(_.render).mkString("; ")
      )
    val storyboard = result.storyboard.get
    CozyVideoStoryboardReview.ReviewStoryboard(sourcepath, _display_path(projectroot, sourcepath), storyboard)
  }

  private def _admitted_visual_inputs(
    projectroot: Path,
    evidencedirectory: Path,
    storyboard: Storyboard,
    visualstory: StoryboardReviewVisualStory
  ): Vector[VisualInput] = {
    if (visualstory.inputRefs.distinct.size != visualstory.inputRefs.size)
      _review_failure("STORYBOARD_REVIEW_INPUT_DUPLICATE", "visualStory.inputRefs must not contain duplicates")
    val admitted = storyboard.scenes.flatMap(scene => scene.diagramRefs ++ scene.assetRefs).toSet
    visualstory.inputRefs.zipWithIndex.map { case (reference, index) =>
      if (reference.trim.isEmpty || !admitted.contains(reference))
        _review_failure(
          "STORYBOARD_REVIEW_INPUT_UNDECLARED",
          s"visualStory.inputRefs[$index] is not an exact Storyboard diagramRefs/assetRefs member: $reference"
        )
      val sourcepath = _safe_project_relative_file(
        projectroot,
        reference,
        s"Storyboard visual input $reference",
        symlinkfailurecode = "STORYBOARD_REVIEW_INPUT_UNSAFE"
      )
      val relative = _display_path(projectroot, sourcepath)
      val destination = evidencedirectory.resolve("visual-inputs").resolve(_relative_path(reference)).normalize()
      _require_contained_path(evidencedirectory, destination, s"Storyboard visual input destination $reference")
      VisualInput(reference, sourcepath, relative, destination)
    }
  }

  private def _copy_visual_input(input: VisualInput, savedir: Path): VisualInput = {
    val visualinputs = _ensure_direct_directory(savedir.resolve("visual-inputs").normalize(), savedir, "Storyboard visual-inputs")
    val destination = input.copiedpath
    _require_contained_path(savedir, destination, s"Storyboard visual input destination ${input.reference}")
    _ensure_direct_directory(destination.getParent, savedir, s"Storyboard visual input parent ${input.reference}")
    if (Files.exists(destination, LinkOption.NOFOLLOW_LINKS) && Files.isSymbolicLink(destination))
      _review_failure("STORYBOARD_REVIEW_INPUT_DESTINATION_SYMLINK", s"visual input destination is a symbolic link: $destination")
    try {
      Files.copy(input.sourcepath, destination, StandardCopyOption.REPLACE_EXISTING)
    } catch {
      case NonFatal(error) =>
        _review_failure("STORYBOARD_REVIEW_INPUT_COPY_FAILED", s"cannot copy ${input.reference}: ${error.getMessage}")
    }
    if (!Files.isRegularFile(destination, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(destination))
      _review_failure("STORYBOARD_REVIEW_INPUT_COPY_INVALID", s"copied visual input is not a direct regular file: $destination")
    input.copy(copiedpath = destination)
  }

  private def _review_evidence_payload_v1(approved: ReviewStoryboard, inputs: Vector[VisualInput], visualreview: Boolean): Json = {
    val scenejson = approved.storyboard.scenes.map { scene =>
      Json.obj(
        "section" -> Json.fromString(scene.section),
        "speaker" -> Json.fromString(scene.speaker),
        "role" -> Json.fromString(scene.role),
        "narration" -> Json.fromString(scene.narration),
        "screen" -> Json.obj(
          "heading" -> Json.fromString(scene.screen.heading),
          "content" -> Json.fromString(scene.screen.content)
        ),
        "caption" -> Json.fromString(scene.caption),
        "duration" -> Json.fromBigDecimal(scene.duration),
        "leadSilence" -> Json.fromBigDecimal(scene.leadSilence),
        "transition" -> Json.fromString(scene.transition),
        "direction" -> Json.fromString(scene.direction)
      )
    }
    val fields = Vector(
      Some("schema" -> Json.fromString(_review_evidence_schema)),
      Some("status" -> Json.fromString("validated")),
      Some("source" -> Json.fromString(approved.relativepath)),
      Some("scenes" -> Json.fromValues(scenejson)),
      if (visualreview)
        Some("visualInputs" -> Json.fromValues(inputs.map { input =>
          Json.obj(
            "reference" -> Json.fromString(input.reference),
            "source" -> Json.fromString(input.relativepath),
            "path" -> Json.fromString(_slash_path(Paths.get("visual-inputs").resolve(_relative_path(input.reference))))
          )
        }))
      else None
    ).flatten
    Json.obj(fields: _*)
  }

  private def _review_evidence_payload_v2(
    approved: ReviewStoryboard,
    inputs: Vector[VisualInput],
    visualpage: Option[VisualPageReview]
  ): Json =
    Json.obj(
      "schema" -> Json.fromString(_review_evidence_schema_v2),
      "status" -> Json.fromString("validated"),
      "source" -> Json.fromString(approved.relativepath),
      "scenes" -> Json.fromValues(approved.storyboard.scenes.map(_review_scene_json_v2)),
      "visualInputs" -> Json.fromValues(inputs.map(_visual_input_json)),
      "visualPages" -> Json.fromValues(visualpage.toVector.flatMap(_.pages).map(_visual_page_proof_json)),
      "effectiveRenderers" -> Json.fromValues(visualpage.toVector.flatMap(_.renderers).map(_effective_renderer_json))
    )

  private def _review_scene_json_v2(scene: StoryboardScene): Json =
    Json.obj(
      "section" -> Json.fromString(scene.section),
      "speaker" -> Json.fromString(scene.speaker),
      "role" -> Json.fromString(scene.role),
      "narration" -> Json.fromString(scene.narration),
      "screen" -> _screen_json_v2(scene.screen),
      "caption" -> Json.fromString(scene.caption),
      "duration" -> Json.fromBigDecimal(scene.duration),
      "leadSilence" -> Json.fromBigDecimal(scene.leadSilence),
      "transition" -> Json.fromString(scene.transition),
      "direction" -> Json.fromString(scene.direction)
    )

  private def _screen_json_v2(screen: StoryboardScreenValue): Json = screen match {
    case StoryboardTextScreen(heading, content) =>
      Json.obj(
        "kind" -> Json.fromString("text"),
        "heading" -> Json.fromString(heading),
        "content" -> Json.fromString(content)
      )
    case StoryboardVisualPageScreen(source, catalog, pageid) =>
      Json.obj(
        "kind" -> Json.fromString("visual-page"),
        "source" -> Json.fromString(source),
        "catalog" -> Json.fromString(catalog),
        "pageId" -> Json.fromString(pageid)
      )
    case StoryboardScreen(heading, content) =>
      _review_failure("STORYBOARD_REVIEW_SCREEN_V2_INVALID", s"Storyboard v2 screen is not tagged: heading=$heading content=$content")
  }

  private def _visual_input_json(input: VisualInput): Json =
    Json.obj(
      "reference" -> Json.fromString(input.reference),
      "source" -> Json.fromString(input.relativepath),
      "path" -> Json.fromString(_slash_path(Paths.get("visual-inputs").resolve(_relative_path(input.reference))))
    )

  private def _visual_page_proof_json(proof: VisualPageProof): Json =
    Json.obj(
      "sceneId" -> Json.fromString(proof.sceneid),
      "kind" -> Json.fromString("visual-page"),
      "source" -> Json.fromString(proof.source),
      "catalog" -> Json.fromString(proof.catalog),
      "pageId" -> Json.fromString(proof.pageid),
      "assets" -> Json.fromValues(proof.assets.map(_visual_page_asset_json))
    )

  private def _visual_page_asset_json(asset: VisualPageAsset): Json =
    Json.obj(
      "id" -> Json.fromString(asset.id),
      "path" -> Json.fromString(asset.path),
      "mediaType" -> Json.fromString(asset.mediatype),
      "modifiedAt" -> Json.fromString(asset.modifiedat.toString)
    )

  private def _effective_renderer_json(renderer: EffectiveRenderer): Json =
    Json.obj(
      "partId" -> Json.fromString(renderer.partid),
      "configuration" -> renderer.configuration
    )

  private def _storyboard_handoff_payload_v2(
    projectroot: Path,
    evidencepath: Path,
    inputs: Vector[VisualInput],
    visualpage: Option[VisualPageReview]
  ): Json =
    Json.obj(
      "schema" -> Json.fromString(_storyboard_handoff_schema_v2),
      "status" -> Json.fromString("validated"),
      "evidencePath" -> Json.fromString(_display_path(projectroot, evidencepath)),
      "visualInputs" -> Json.fromValues(inputs.map(_visual_input_json)),
      "visualPages" -> Json.fromValues(visualpage.toVector.flatMap(_.pages).map(_visual_page_proof_json)),
      "effectiveRenderers" -> Json.fromValues(visualpage.toVector.flatMap(_.renderers).map(_effective_renderer_json))
    )

  private def _visual_page_review(
    projectroot: Path,
    project: VideoProject,
    review: StoryboardReview,
    approved: ReviewStoryboard
  ): Option[VisualPageReview] = {
    val visualscreens = approved.storyboard.scenes.zipWithIndex.collect {
      case (scene, index) if scene.screen.isInstanceOf[StoryboardVisualPageScreen] =>
        (scene.id, index, scene.screen.asInstanceOf[StoryboardVisualPageScreen])
    }
    if (approved.storyboard.schema != _storyboard_schema_v2) {
      if (review.visualPage.isDefined)
        _review_failure("STORYBOARD_REVIEW_VISUAL_PAGE_UNSUPPORTED", "storyboardReview.visualPage is permitted only for Storyboard v2 visual-page screens")
      None
    } else if (visualscreens.isEmpty) {
      if (review.visualPage.isDefined)
        _review_failure("STORYBOARD_REVIEW_VISUAL_PAGE_UNDECLARED", "storyboardReview.visualPage requires at least one Storyboard v2 visual-page screen")
      None
    } else {
      val declaration = review.visualPage.getOrElse(
        _review_failure("STORYBOARD_REVIEW_VISUAL_PAGE_MISSING", "Storyboard v2 visual-page screens require storyboardReview.visualPage")
      )
      val bindingpath = _safe_literal_project_relative_file(
        projectroot,
        declaration.binding,
        "Storyboard visualPage binding",
        symlinkfailurecode = "STORYBOARD_REVIEW_VISUAL_PAGE_BINDING_UNSAFE"
      )
      val evidencedirectory = _safe_literal_evidence_directory(projectroot, declaration.evidenceDirectory)
      val pages = visualscreens.map { case (sceneid, index, screen) =>
        _resolve_visual_page_proof(projectroot, approved, sceneid, index, screen, bindingpath)
      }
      val renderers = _effective_storyboard_renderers(projectroot, project, approved.sourcepath)
      Some(VisualPageReview(evidencedirectory, pages, renderers))
    }
  }

  private def _resolve_visual_page_proof(
    projectroot: Path,
    approved: ReviewStoryboard,
    sceneid: String,
    index: Int,
    screen: StoryboardVisualPageScreen,
    bindingpath: Path
  ): VisualPageProof = {
    val storyboardroot = Option(approved.sourcepath.getParent).getOrElse(
      _review_failure("STORYBOARD_REVIEW_VISUAL_PAGE_SOURCE_INVALID", "Storyboard source has no parent directory")
    )
    val sourcepath = _safe_storyboard_relative_file(projectroot, storyboardroot, screen.source, s"Storyboard visual-page screen[$index] source")
    val catalogpath = _safe_storyboard_relative_file(projectroot, storyboardroot, screen.catalog, s"Storyboard visual-page screen[$index] catalog")
    val validated = try CozyVisualPage.load(sourcepath, catalogpath) catch {
      case NonFatal(error) =>
        _review_failure("STORYBOARD_REVIEW_VISUAL_PAGE_RESOLUTION_INVALID", s"screen[$index] cannot resolve its literal VisualPageSet/catalog: ${Option(error.getMessage).getOrElse("invalid input")}")
    }
    val pageset = validated.document match {
      case value: CozyVisualPage.PageSet => value
      case _ =>
        _review_failure("STORYBOARD_REVIEW_VISUAL_PAGE_SET_REQUIRED", s"screen[$index] source must be a cozy.visual-page-set.v1 document")
    }
    val matches = pageset.pages.filter(_.id == screen.pageId)
    val page = matches match {
      case Vector(value) => value
      case Vector() =>
        _review_failure("STORYBOARD_REVIEW_VISUAL_PAGE_NOT_FOUND", s"screen[$index] pageId does not resolve: ${screen.pageId}")
      case _ =>
        _review_failure("STORYBOARD_REVIEW_VISUAL_PAGE_DUPLICATE", s"screen[$index] pageId resolves more than once: ${screen.pageId}")
    }
    try CozyVisualPageBinding.load(bindingpath, validated) catch {
      case NonFatal(error) =>
        _review_failure("STORYBOARD_REVIEW_VISUAL_PAGE_BINDING_INVALID", s"screen[$index] binding cannot validate against its resolved VisualPageSet/catalog: ${Option(error.getMessage).getOrElse("invalid binding")}")
    }
    VisualPageProof(
      sceneid,
      screen.source,
      screen.catalog,
      screen.pageId,
      page.assets.map { asset =>
        val assetpath = CozyVisualPage._safe_direct_file(sourcepath.getParent, asset.path, s"asset.${asset.id}.path")
        VisualPageAsset(asset.id, asset.path, asset.mediaType, Files.getLastModifiedTime(assetpath, LinkOption.NOFOLLOW_LINKS))
      }
    )
  }

  private def _effective_storyboard_renderers(
    projectroot: Path,
    project: VideoProject,
    storyboardpath: Path
  ): Vector[EffectiveRenderer] = {
    val selected = project.parts.zipWithIndex.flatMap { case (part, index) =>
      part.storyboard.toVector.flatMap { storyboard =>
        val sourcepath = _safe_project_relative_file(projectroot, storyboard, s"Storyboard part ${part.displayId(index + 1)} source")
        if (sourcepath == storyboardpath) {
          val renderer = part.renderer.orElse(project.renderer).getOrElse(
            _review_failure("STORYBOARD_REVIEW_EFFECTIVE_RENDERER_MISSING", s"Storyboard part ${part.displayId(index + 1)} has no selected effective VideoRenderer")
          )
          val configuration = _video_renderer_json(renderer)
          Vector(EffectiveRenderer(part.displayId(index + 1), configuration))
        } else Vector.empty
      }
    }.toVector.sortBy(_.partid)
    if (selected.isEmpty)
      _review_failure("STORYBOARD_REVIEW_STORYBOARD_PART_MISSING", "No project part selects storyboardReview.source for Storyboard v2 visual-page review")
    selected
  }

  private def _video_renderer_json(renderer: VideoRenderer): Json =
    Json.obj(
      "engine" -> renderer.engine.map(Json.fromString).getOrElse(Json.Null),
      "strategy" -> renderer.strategy.map(Json.fromString).getOrElse(Json.Null),
      "policy" -> renderer.policy.map(value => Json.fromString(value.name)).getOrElse(Json.Null),
      "fps" -> renderer.fps.map(Json.fromInt).getOrElse(Json.Null),
      "width" -> renderer.width.map(Json.fromInt).getOrElse(Json.Null),
      "height" -> renderer.height.map(Json.fromInt).getOrElse(Json.Null),
      "crf" -> renderer.crf.map(Json.fromInt).getOrElse(Json.Null),
      "x264Preset" -> renderer.x264Preset.map(Json.fromString).getOrElse(Json.Null),
      "effectProfile" -> renderer.effectProfile.map(Json.fromString).getOrElse(Json.Null)
    )

  private def _safe_storyboard_relative_file(projectroot: Path, storyboardroot: Path, raw: String, label: String): Path = {
    val relative = _safe_relative_path(raw, label)
    if (raw != _slash_path(relative))
      _review_failure("STORYBOARD_REVIEW_VISUAL_PAGE_PATH_INVALID", s"$label must be a normalized literal relative path: $raw")
    val root = storyboardroot.toAbsolutePath.normalize()
    val candidate = root.resolve(relative).normalize()
    _require_contained_path(root, candidate, label)
    _require_contained_path(projectroot, candidate, label)
    _assert_no_symlink_components(projectroot, candidate, label, "STORYBOARD_REVIEW_VISUAL_PAGE_PATH_UNSAFE")
    if (!Files.exists(candidate, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(candidate) || !Files.isRegularFile(candidate, LinkOption.NOFOLLOW_LINKS))
      _review_failure("STORYBOARD_REVIEW_VISUAL_PAGE_PATH_UNSAFE", s"$label must be an existing direct regular non-symlink file: $candidate")
    val canonical = try candidate.toRealPath() catch {
      case NonFatal(error) => _review_failure("STORYBOARD_REVIEW_VISUAL_PAGE_PATH_UNREADABLE", s"$label cannot be resolved: ${error.getMessage}")
    }
    val canonicalprojectroot = _canonical_project_root(projectroot)
    if (!canonical.startsWith(canonicalprojectroot))
      _review_failure("STORYBOARD_REVIEW_VISUAL_PAGE_PATH_OUTSIDE_PROJECT", s"$label resolves outside the project root: $raw")
    canonical
  }

  private def _safe_literal_project_relative_file(
    projectroot: Path,
    raw: String,
    label: String,
    symlinkfailurecode: String
  ): Path = {
    val relative = _safe_relative_path(raw, label)
    if (raw != _slash_path(relative))
      _review_failure("STORYBOARD_REVIEW_VISUAL_PAGE_PATH_INVALID", s"$label must be a normalized literal project-relative path: $raw")
    _safe_project_relative_file(projectroot, raw, label, symlinkfailurecode)
  }

  private def _safe_literal_evidence_directory(projectroot: Path, raw: String): Path = {
    val relative = _safe_relative_path(raw, "Storyboard visualPage evidenceDirectory")
    if (raw != _slash_path(relative))
      _review_failure("STORYBOARD_REVIEW_VISUAL_PAGE_PATH_INVALID", s"Storyboard visualPage evidenceDirectory must be a normalized literal project-relative path: $raw")
    _safe_evidence_directory(projectroot, raw)
  }

  private def _validate_evidence_directory_agreement(visualpage: Option[Path], visualstory: Option[Path]): Unit =
    (visualpage, visualstory) match {
      case (Some(left), Some(right)) if left != right =>
        _review_failure("STORYBOARD_REVIEW_EVIDENCE_DIRECTORY_CONFLICT", s"visualPage.evidenceDirectory and visualStory.evidenceDirectory must be identical: $left != $right")
      case _ => ()
    }

  private def _validate_current_evidence(evidence: Json, inputs: Vector[VisualInput], directory: Path): Unit = {
    _validate_review_document(evidence, _review_evidence_schema, "EVIDENCE")
    if (evidence.hcursor.downField("visualInputs").focus.flatMap(_.asArray).isEmpty)
      _review_failure("STORYBOARD_REVIEW_EVIDENCE_MALFORMED", "visual-story evidence requires visualInputs")
    _validate_visual_copies(inputs, directory)
  }

  private def _validate_current_evidence_v2(evidence: Json, inputs: Vector[VisualInput], directory: Path): Unit = {
    _validate_review_document(evidence, _review_evidence_schema_v2, "EVIDENCE")
    _visual_page_references(evidence)
    _validate_visual_copies(inputs, directory)
  }

  private def _validate_current_handoff_v2(handoff: Json): Unit = {
    _validate_review_document(handoff, _storyboard_handoff_schema_v2, "HANDOFF")
    _visual_page_references(handoff)
  }

  private def _validate_review_document(value: Json, schema: String, label: String): Unit = {
    if (value.asObject.isEmpty)
      _review_failure(s"STORYBOARD_REVIEW_${label}_MALFORMED", "review document must be a JSON object")
    if (value.hcursor.get[String]("schema").toOption != Some(schema))
      _review_failure(s"STORYBOARD_REVIEW_${label}_SCHEMA_INVALID", "review document schema is not supported")
    if (value.hcursor.get[String]("status").toOption != Some("validated"))
      _review_failure(s"STORYBOARD_REVIEW_${label}_STATUS_INVALID", "review document status is not validated")
    val evidence = label == "EVIDENCE"
    val v2 = schema.endsWith(".v2")
    val required = Set("schema") ++ (if (evidence || v2) Set("status") else Set.empty[String]) ++
      (if (evidence) Set("source", "scenes") else Set("evidencePath")) ++
      (if (v2) Set("visualInputs", "visualPages", "effectiveRenderers") else Set.empty[String])
    val allowed = required ++ (if (evidence && !v2) Set("visualInputs") else Set.empty[String])
    val fields = _known_review_fields(value.asObject.get, allowed)
    if (!required.subsetOf(fields.keys.toSet))
      _review_failure(s"STORYBOARD_REVIEW_${label}_MALFORMED", "review document required fields are missing or unknown")
    val reference = if (evidence) "source" else "evidencePath"
    if (!fields(reference).flatMap(_.asString).exists(text => text.nonEmpty && text == text.trim))
      _review_failure(s"STORYBOARD_REVIEW_${label}_MALFORMED", s"$reference must be a non-empty exact string")
    Vector("scenes", "visualInputs", "visualPages", "effectiveRenderers").filter(fields.contains).foreach { name =>
      if (fields(name).flatMap(_.asArray).isEmpty)
        _review_failure(s"STORYBOARD_REVIEW_${label}_MALFORMED", s"$name must be an array")
    }
    _validate_review_rows(Json.fromJsonObject(fields), v2, label)
  }

  private def _known_review_fields(fields: io.circe.JsonObject, names: Set[String]): io.circe.JsonObject =
    fields.filterKeys(names.contains)

  private def _validate_review_rows(value: Json, v2: Boolean, label: String): Unit = {
    def _invalid_(message: String): Nothing = _review_failure(s"STORYBOARD_REVIEW_${label}_MALFORMED", message)
    def _rows_(name: String): Vector[Json] = value.hcursor.downField(name).focus.flatMap(_.asArray).toVector.flatten
    def _fields_(row: Json, names: Set[String]): io.circe.JsonObject = {
      val fields = _known_review_fields(row.asObject.getOrElse(_invalid_("review entry must be an object")), names)
      if (fields.keys.toSet != names) _invalid_(s"review entry requires fields: ${names.toVector.sorted.mkString(", ")}")
      fields
    }
    def _string_(fields: io.circe.JsonObject, name: String): String =
      fields(name).flatMap(_.asString).getOrElse(_invalid_(s"$name must be a string"))
    def _reference_(fields: io.circe.JsonObject, name: String): String = {
      val text = _string_(fields, name)
      if (text.isEmpty || text != text.trim) _invalid_(s"$name must be a non-empty exact string")
      text
    }
    def _unique_(values: Vector[String], name: String): Unit =
      if (values.distinct.size != values.size) _invalid_(s"$name must be unique")

    if (value.hcursor.downField("scenes").focus.isDefined && _rows_("scenes").isEmpty)
      _invalid_("scenes must not be empty")
    _rows_("scenes").foreach { row =>
      val strings = Set("section", "speaker", "role", "narration", "caption", "transition", "direction")
      val fields = _fields_(row, strings ++ Set("screen", "duration", "leadSilence"))
      strings.foreach(name => _string_(fields, name))
      Vector("duration", "leadSilence").foreach { name =>
        if (fields(name).flatMap(_.asNumber).flatMap(_.toBigDecimal).isEmpty) _invalid_(s"$name must be a number")
      }
      val screen = fields("screen").get
      val visual = v2 && screen.hcursor.get[String]("kind").toOption.contains("visual-page")
      val names = if (visual) Set("kind", "source", "catalog", "pageId") else Set("heading", "content") ++ (if (v2) Set("kind") else Set.empty[String])
      val screenfields = _fields_(screen, names)
      names.foreach(name => _string_(screenfields, name))
      if (v2 && !Set("text", "visual-page").contains(_string_(screenfields, "kind"))) _invalid_("screen kind is invalid")
    }
    val references = _rows_("visualInputs").map { row =>
      val fields = _fields_(row, Set("reference", "source", "path"))
      Vector("reference", "source", "path").foreach(name => _safe_relative_path(_reference_(fields, name), name))
      _reference_(fields, "reference")
    }
    _unique_(references, "visual input references")
    val parts = _rows_("effectiveRenderers").map { row =>
      val fields = _fields_(row, Set("partId", "configuration"))
      val configuration = fields("configuration").get
      val names = Set("engine", "strategy", "policy", "fps", "width", "height", "crf", "x264Preset", "effectProfile")
      val knownconfiguration = Json.fromJsonObject(_fields_(configuration, names))
      if (knownconfiguration.as[VideoRenderer].isLeft) _invalid_("renderer configuration is invalid")
      _reference_(fields, "partId")
    }
    _unique_(parts, "renderer part IDs")
  }

  private def _validate_visual_copies(inputs: Vector[VisualInput], directory: Path): Unit =
    inputs.foreach { input =>
      _require_direct_output_file(input.copiedpath, directory, s"Storyboard visual input ${input.reference}")
    }

  private def _visual_page_references(value: Json): Vector[Json] = {
    def _invalid_(message: String): Nothing = _review_failure("STORYBOARD_REVIEW_VISUAL_PAGE_PROOF_INVALID", message)
    def _exact_string_(value: Option[Json]): Boolean = value.flatMap(_.asString).exists(text => text.nonEmpty && text == text.trim)
    val references = value.hcursor.downField("visualPages").focus.flatMap(_.asArray).getOrElse(_invalid_("visualPages must be an array")).toVector.map { page =>
      val names = Vector("sceneId", "kind", "source", "catalog", "pageId", "assets")
      val fields = _known_review_fields(page.asObject.getOrElse(_invalid_("visualPages entry must be an object")), names.toSet)
      if (fields.keys.toVector != names || !names.dropRight(1).forall(name => _exact_string_(fields(name))))
        _invalid_("visualPages entry requires exact sceneId,kind,source,catalog,pageId strings and assets")
      if (fields("kind").flatMap(_.asString) != Some("visual-page"))
        _invalid_("visualPages kind must be visual-page")
      val assets = fields("assets").flatMap(_.asArray).getOrElse(_invalid_("visualPages.assets must be an array"))
      assets.foreach { asset =>
        val assetnames = Vector("id", "path", "mediaType", "modifiedAt")
        val assetfields = _known_review_fields(asset.asObject.getOrElse(_invalid_("visualPages asset must be an object")), assetnames.toSet)
        if (assetfields.keys.toVector != assetnames || !assetnames.forall(name => _exact_string_(assetfields(name))))
          _invalid_("visualPages asset requires id,path,mediaType,modifiedAt strings")
      }
      if (assets.map(_.hcursor.get[String]("id").toOption).distinct.size != assets.size)
        _invalid_("visualPages asset IDs must be unique")
      Json.fromJsonObject(fields.remove("assets"))
    }
    if (references.map(_.hcursor.get[String]("sceneId").toOption).distinct.size != references.size)
      _invalid_("visualPages scene IDs must be unique")
    references
  }

  private def _safe_project_relative_file(
    projectroot: Path,
    raw: String,
    label: String,
    symlinkfailurecode: String = "STORYBOARD_REVIEW_SYMLINK_COMPONENT"
  ): Path = {
    val relative = _safe_relative_path(raw, label)
    val normalizedroot = projectroot.toAbsolutePath.normalize()
    val candidate = normalizedroot.resolve(relative).normalize()
    _require_contained_path(normalizedroot, candidate, label)
    _assert_no_symlink_components(normalizedroot, candidate, label, symlinkfailurecode)
    if (!Files.exists(candidate, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(candidate) || !Files.isRegularFile(candidate, LinkOption.NOFOLLOW_LINKS))
      _review_failure("STORYBOARD_REVIEW_INPUT_UNSAFE", s"$label must be an existing direct regular non-symlink file: $candidate")
    val canonicalroot = try normalizedroot.toRealPath() catch {
      case NonFatal(error) => _review_failure("STORYBOARD_REVIEW_PROJECT_ROOT_INVALID", s"project root cannot be resolved: ${error.getMessage}")
    }
    val canonical = try candidate.toRealPath() catch {
      case NonFatal(error) => _review_failure("STORYBOARD_REVIEW_INPUT_UNREADABLE", s"$label cannot be resolved: ${error.getMessage}")
    }
    if (Files.isSymbolicLink(canonical) || !Files.isRegularFile(canonical, LinkOption.NOFOLLOW_LINKS) || !canonical.startsWith(canonicalroot))
      _review_failure("STORYBOARD_REVIEW_INPUT_OUTSIDE_PROJECT", s"$label resolves outside the project root: $canonical")
    canonical
  }

  private def _safe_evidence_directory(projectroot: Path, raw: String): Path = {
    val relative = _safe_relative_path(raw, "Storyboard visual evidenceDirectory")
    val canonicalprojectroot = _canonical_project_root(projectroot)
    val targetroot = canonicalprojectroot.resolve("target").resolve("cozy-video").normalize()
    val directory = canonicalprojectroot.resolve(relative).normalize()
    _require_contained_path(targetroot, directory, "Storyboard visual evidenceDirectory")
    if (directory == targetroot)
      _review_failure("STORYBOARD_REVIEW_EVIDENCE_DIRECTORY_INVALID", "visualStory.evidenceDirectory must be below target/cozy-video")
    _assert_no_symlink_components(canonicalprojectroot, directory, "Storyboard visual evidenceDirectory")
    directory
  }

  private def _canonical_project_root(path: Path): Path =
    try path.toAbsolutePath.normalize().toRealPath() catch {
      case NonFatal(error) => _review_failure("STORYBOARD_REVIEW_PROJECT_ROOT_INVALID", s"project root cannot be resolved: ${error.getMessage}")
    }

  private def _prepare_save_directory(path: Path): Path = {
    val save = Option(path).map(_.toAbsolutePath.normalize()).getOrElse(
      _review_failure("STORYBOARD_REVIEW_SAVE_INVALID", "--save directory must be defined")
    )
    if (save.getParent == null)
      _review_failure("STORYBOARD_REVIEW_SAVE_INVALID", "--save directory must not be the filesystem root")
    if (Files.exists(save, LinkOption.NOFOLLOW_LINKS) && Files.isSymbolicLink(save))
      _review_failure("STORYBOARD_REVIEW_SAVE_SYMLINK", s"--save directory must not be a symbolic link: $save")
    if (Files.exists(save, LinkOption.NOFOLLOW_LINKS) && !Files.isDirectory(save, LinkOption.NOFOLLOW_LINKS))
      _review_failure("STORYBOARD_REVIEW_SAVE_NOT_DIRECTORY", s"--save is not a directory: $save")
    _assert_no_save_symlink_components(save, "Storyboard review --save")
    try Files.createDirectories(save) catch {
      case NonFatal(error) => _review_failure("STORYBOARD_REVIEW_SAVE_CREATE_FAILED", s"cannot create --save directory: ${error.getMessage}")
    }
    _assert_no_save_symlink_components(save, "Storyboard review --save")
    if (!Files.isDirectory(save, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(save))
      _review_failure("STORYBOARD_REVIEW_DIRECTORY_INVALID", s"Storyboard review --save must be a direct directory: $save")
    try save.toRealPath() catch {
      case NonFatal(error) => _review_failure("STORYBOARD_REVIEW_SAVE_UNREADABLE", s"cannot resolve --save directory: ${error.getMessage}")
    }
  }

  private def _ensure_direct_directory(path: Path, boundary: Path, label: String): Path = {
    val normalized = path.toAbsolutePath.normalize()
    _require_contained_path(boundary.toAbsolutePath.normalize(), normalized, label)
    _assert_no_symlink_components(boundary.toAbsolutePath.normalize(), normalized, label)
    if (Files.exists(normalized, LinkOption.NOFOLLOW_LINKS) && (Files.isSymbolicLink(normalized) || !Files.isDirectory(normalized, LinkOption.NOFOLLOW_LINKS)))
      _review_failure("STORYBOARD_REVIEW_DIRECTORY_INVALID", s"$label must be a direct directory: $normalized")
    try Files.createDirectories(normalized) catch {
      case NonFatal(error) => _review_failure("STORYBOARD_REVIEW_DIRECTORY_CREATE_FAILED", s"cannot create $label: ${error.getMessage}")
    }
    _assert_no_symlink_components(boundary.toAbsolutePath.normalize(), normalized, label)
    if (!Files.isDirectory(normalized, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(normalized))
      _review_failure("STORYBOARD_REVIEW_DIRECTORY_INVALID", s"$label must be a direct directory: $normalized")
    normalized
  }

  private def _require_direct_output_file(path: Path, boundary: Path, label: String): Unit = {
    val normalized = path.toAbsolutePath.normalize()
    _require_contained_path(boundary.toAbsolutePath.normalize(), normalized, label)
    _assert_no_symlink_components(boundary.toAbsolutePath.normalize(), normalized.getParent, label)
    if (Files.isSymbolicLink(normalized) || !Files.isRegularFile(normalized, LinkOption.NOFOLLOW_LINKS))
      _review_failure("STORYBOARD_REVIEW_EVIDENCE_MISSING", s"$label is missing or not a direct regular file: $normalized")
  }

  private def _write_json(path: Path, json: Json, boundary: Path): Unit = {
    val normalized = path.toAbsolutePath.normalize()
    _require_contained_path(boundary.toAbsolutePath.normalize(), normalized, "Storyboard review output")
    _ensure_direct_directory(normalized.getParent, boundary, "Storyboard review output parent")
    if (Files.exists(normalized, LinkOption.NOFOLLOW_LINKS) && Files.isSymbolicLink(normalized))
      _review_failure("STORYBOARD_REVIEW_OUTPUT_SYMLINK", s"output must not be a symbolic link: $normalized")
    try Files.writeString(normalized, json.noSpaces, StandardCharsets.UTF_8) catch {
      case NonFatal(error) => _review_failure("STORYBOARD_REVIEW_OUTPUT_WRITE_FAILED", s"cannot write $normalized: ${error.getMessage}")
    }
    if (!Files.isRegularFile(normalized, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(normalized))
      _review_failure("STORYBOARD_REVIEW_OUTPUT_INVALID", s"output is not a direct regular file: $normalized")
  }

  private def _read_json(path: Path, label: String): Json = {
    val text = try Files.readString(path, StandardCharsets.UTF_8) catch {
      case NonFatal(error) => _review_failure("STORYBOARD_REVIEW_EVIDENCE_READ_FAILED", s"cannot read $label: ${error.getMessage}")
    }
    _require_no_duplicate_json_fields(text, label)
    parser.parse(text).fold(
      error => _review_failure("STORYBOARD_REVIEW_EVIDENCE_MALFORMED", s"$label is not valid JSON: ${error.getMessage}"),
      identity
    )
  }

  private def _require_no_duplicate_json_fields(text: String, label: String): Unit = {
    val input = new JsonFactory().enable(JacksonParser.Feature.STRICT_DUPLICATE_DETECTION).createParser(text)
    try {
      try while (input.nextToken() != null) {}
      catch {
        case error: JsonParseException if Option(error.getOriginalMessage).exists(_.contains("Duplicate field")) =>
          _review_failure(
            "STORYBOARD_REVIEW_EVIDENCE_DUPLICATE_FIELD",
            s"$label contains duplicate JSON object field: ${error.getOriginalMessage}"
          )
        case error: JsonParseException =>
          _review_failure("STORYBOARD_REVIEW_EVIDENCE_MALFORMED", s"$label is not valid JSON: ${error.getOriginalMessage}")
      }
    } finally input.close()
  }

  private def _safe_relative_path(raw: String, label: String): Path = {
    val value = Option(raw).map(_.trim).getOrElse("")
    if (value.isEmpty)
      _review_failure("STORYBOARD_REVIEW_PATH_INVALID", s"$label must be nonempty")
    val path = try Paths.get(value) catch {
      case NonFatal(error) => _review_failure("STORYBOARD_REVIEW_PATH_INVALID", s"$label is not a valid path: ${error.getMessage}")
    }
    if (path.isAbsolute || path.normalize().startsWith(Paths.get("..")))
      _review_failure("STORYBOARD_REVIEW_PATH_INVALID", s"$label must be project-relative: $raw")
    path.normalize()
  }

  private def _relative_path(raw: String): Path =
    Paths.get(raw).normalize()

  private def _display_path(root: Path, path: Path): String = {
    val normalizedroot = root.toAbsolutePath.normalize()
    val normalized = path.toAbsolutePath.normalize()
    if (normalized.startsWith(normalizedroot))
      _slash_path(normalizedroot.relativize(normalized))
    else
      _slash_path(normalized)
  }

  private def _slash_path(path: Path): String =
    path.iterator().asScala.map(_.toString).mkString("/")

  private def _require_contained_path(boundary: Path, path: Path, label: String): Unit = {
    val base = boundary.toAbsolutePath.normalize()
    val candidate = path.toAbsolutePath.normalize()
    if (!(candidate == base || candidate.startsWith(base)))
      _review_failure("STORYBOARD_REVIEW_PATH_OUTSIDE_BOUNDARY", s"$label escapes its allowed directory: $candidate")
  }

  private def _assert_no_symlink_components(
    boundary: Path,
    path: Path,
    label: String,
    failurecode: String = "STORYBOARD_REVIEW_SYMLINK_COMPONENT"
  ): Unit = {
    if (path == null)
      return
    val base = boundary.toAbsolutePath.normalize()
    val candidate = path.toAbsolutePath.normalize()
    _require_contained_path(base, candidate, label)
    val relative = base.relativize(candidate)
    var current = base
    relative.iterator().asScala.foreach { component =>
      current = current.resolve(component.toString)
      if (Files.exists(current, LinkOption.NOFOLLOW_LINKS) && Files.isSymbolicLink(current))
        _review_failure(failurecode, s"$label contains a symbolic-link path component: $current")
    }
  }

  private def _assert_no_save_symlink_components(path: Path, label: String): Unit = {
    val save = path.toAbsolutePath.normalize()
    var current = save.getRoot
    save.iterator().asScala.foreach { component =>
      current = current.resolve(component.toString)
      if (Files.exists(current, LinkOption.NOFOLLOW_LINKS) && Files.isSymbolicLink(current) && !_is_platform_temp_alias(current))
        _review_failure("STORYBOARD_REVIEW_SYMLINK_COMPONENT", s"$label contains a symbolic-link path component: $current")
    }
  }

  private def _is_platform_temp_alias(path: Path): Boolean = {
    val alias = Paths.get("/var")
    val physical = Paths.get("/private/var")
    try {
      path.toAbsolutePath.normalize() == alias &&
      alias.toRealPath() == physical.toRealPath()
    } catch {
      case NonFatal(_) => false
    }
  }

  private def _review_failure(code: String, message: String): Nothing =
    RAISE.invalidArgumentFault(s"$code: $message")
}
