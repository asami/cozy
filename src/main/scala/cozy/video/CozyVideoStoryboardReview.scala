package cozy.video

import cozy.media.{CozyVisualPage, CozyVisualPageBinding}
import cozy.runtime.CozyCliArgs
import io.circe.Json
import io.circe.parser
import java.nio.charset.StandardCharsets
import java.nio.file.{Files, LinkOption, Path, Paths, StandardCopyOption}
import java.security.MessageDigest
import org.goldenport.RAISE
import org.goldenport.cli.spec
import scala.collection.JavaConverters._
import scala.util.control.NonFatal

/*
 * @since   Aug. 26, 2026
 * @version Aug. 27, 2026
 * @author  ASAMI, Tomoharu
 */
private[cozy] trait CozyVideoStoryboardReview {
  self: CozyVideoTypes with CozyVideoStoryboard with CozyVideoCommand with CozyVideoPlanning =>

  final case class StoryboardReviewConfig(projectFile: Path, saveDir: Path) {
    def projectRoot: Path = projectFile.getParent
  }
  object StoryboardReviewConfig {
    def create(args: List[String]): StoryboardReviewConfig = {
      val parsed = CozyCliArgs.parseStrict(
        _p_project_file,
        _p_save
      )(_normalize_property_args(args))
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
    handoffPath: Path,
    evidenceIdentity: String,
    storyboardIdentity: String,
    visualInputIdentities: Vector[String]
  )

  final case class StoryboardReviewCurrent(
    projectFile: Path,
    evidencePath: Path,
    handoffPath: Path,
    evidenceIdentity: String,
    handoffIdentity: String,
    storyboardIdentity: String
  )

  private final case class ApprovedStoryboard(
    sourcepath: Path,
    relativepath: String,
    storyboard: Storyboard,
    identity: String
  )

  private final case class VisualInput(
    reference: String,
    sourcepath: Path,
    relativepath: String,
    copiedpath: Path,
    identity: String
  )

  private final case class VisualPageAsset(
    id: String,
    path: String,
    mediatype: String,
    sha256: String
  )

  private final case class VisualPageProof(
    sceneid: String,
    source: String,
    catalog: String,
    pageid: String,
    visualpagesetidentity: String,
    catalogidentity: String,
    logicalidentity: String,
    visualpageidentity: String,
    assets: Vector[VisualPageAsset],
    bindingidentity: String
  )

  private final case class EffectiveRenderer(
    partid: String,
    configuration: Json,
    identity: String
  )

  private final case class VisualPageReview(
    evidencedirectory: Path,
    approvedevidenceidentity: String,
    pages: Vector[VisualPageProof],
    renderers: Vector[EffectiveRenderer]
  )

  private val _review_evidence_schema = "cozy.video.storyboard-review-evidence.v1"
  private val _storyboard_handoff_schema = "cozy.video.storyboard-handoff.v1"
  private val _review_evidence_schema_v2 = "cozy.video.storyboard-review-evidence.v2"
  private val _storyboard_handoff_schema_v2 = "cozy.video.storyboard-handoff.v2"
  private val _storyboard_schema_v1 = "cozy.video.storyboard.v1"
  private val _storyboard_schema_v2 = "cozy.video.storyboard.v2"
  private val _sha256_pattern = "sha256:[0-9a-f]{64}".r

  def storyboardReviewEvidence(config: StoryboardReviewConfig): StoryboardReviewResult =
    _write_storyboard_review_evidence(config)

  def storyboardReview(config: StoryboardReviewConfig): String = {
    val result = storyboardReviewEvidence(config)
    Vector(
      s"evidence: ${result.evidencePath}",
      s"handoff: ${result.handoffPath}",
      s"identity: ${result.evidenceIdentity}",
      s"storyboardIdentity: ${result.storyboardIdentity}"
    ).mkString("\n")
  }

  def storyboardReviewCurrent(projectFile: Path): StoryboardReviewCurrent = {
    val plan = _plan(projectFile, None, None)
    val review = plan.project.storyboardReview.getOrElse(
      _review_failure("STORYBOARD_REVIEW_DECLARATION_MISSING", "video project does not declare storyboardReview")
    )
    val projectroot = _canonical_project_root(plan.projectRoot)
    val approved = _approved_storyboard(projectroot, review)
    if (approved.storyboard.schema != _storyboard_schema_v2)
      _review_failure("STORYBOARD_REVIEW_CROSS_MEDIA_SCHEMA_UNSUPPORTED", "Cross-media Review requires Storyboard v2 visual-page evidence")
    val visualpage = _visual_page_review(projectroot, plan.project, review, approved).getOrElse(
      _review_failure("STORYBOARD_REVIEW_CROSS_MEDIA_VISUAL_PAGE_MISSING", "Cross-media Review requires a Storyboard v2 visual-page declaration")
    )
    _validate_storyboard_review_current(plan)
    val evidencepath = visualpage.evidencedirectory.resolve("review-evidence.json").normalize()
    val handoffpath = visualpage.evidencedirectory.resolve("handoff.json").normalize()
    val evidence = _read_json(evidencepath, "Storyboard v2 review evidence")
    val handoff = _read_json(handoffpath, "Storyboard v2 review handoff")
    StoryboardReviewCurrent(
      plan.projectFile,
      evidencepath,
      handoffpath,
      _identity_field(evidence, "Storyboard v2 review evidence"),
      _identity_field(handoff, "Storyboard v2 review handoff"),
      approved.identity
    )
  }

  private[video] def _validate_storyboard_review_current(plan: VideoPlan): Unit =
    plan.project.storyboardReview.foreach { review =>
      val projectroot = _canonical_project_root(plan.projectRoot)
      val approved = _approved_storyboard(projectroot, review)
      approved.storyboard.schema match {
        case `_storyboard_schema_v1` =>
          if (review.visualPage.isDefined)
            _review_failure("STORYBOARD_REVIEW_VISUAL_PAGE_UNSUPPORTED", "storyboardReview.visualPage is permitted only for Storyboard v2 visual-page screens")
          review.visualStory.foreach { visualstory =>
            val evidencedirectory = _safe_evidence_directory(projectroot, visualstory.evidenceDirectory)
            val inputs = _admitted_visual_inputs(projectroot, evidencedirectory, approved.storyboard, visualstory)
            val approvedevidenceidentity = _approved_visual_story_evidence_identity(visualstory)
            val evidencepath = evidencedirectory.resolve("review-evidence.json").normalize()
            _require_direct_output_file(evidencepath, evidencedirectory, "Storyboard review evidence")
            val evidence = _read_json(evidencepath, "Storyboard review evidence")
            val expected = _review_evidence_payload_v1(approved, inputs, visualreview = true)
            _validate_current_evidence(evidence, expected, approved, inputs, approvedevidenceidentity)
          }
        case `_storyboard_schema_v2` =>
          val visualpage = _visual_page_review(projectroot, plan.project, review, approved)
          val visualstorydirectory = review.visualStory.map(visualstory => _safe_evidence_directory(projectroot, visualstory.evidenceDirectory))
          _validate_evidence_directory_agreement(visualpage.map(_.evidencedirectory), visualstorydirectory)
          val evidencedirectory = visualpage.map(_.evidencedirectory).orElse(visualstorydirectory)
          evidencedirectory.foreach { directory =>
            val inputs = review.visualStory.map(visualstory => _admitted_visual_inputs(projectroot, directory, approved.storyboard, visualstory)).getOrElse(Vector.empty)
            val expected = _review_evidence_payload_v2(approved, inputs, visualpage)
            val evidencepath = directory.resolve("review-evidence.json").normalize()
            _require_direct_output_file(evidencepath, directory, "Storyboard v2 review evidence")
            val evidence = _read_json(evidencepath, "Storyboard v2 review evidence")
            _validate_current_evidence_v2(
              evidence,
              expected,
              approved,
              inputs,
              _approved_evidence_identities(review, visualpage)
            )
            val expectedhandoff = _storyboard_handoff_payload_v2(projectroot, evidencepath, _json_identity(expected), approved, inputs, visualpage)
            val handoffpath = directory.resolve("handoff.json").normalize()
            _require_direct_output_file(handoffpath, directory, "Storyboard v2 review handoff")
            _validate_current_handoff_v2(_read_json(handoffpath, "Storyboard v2 review handoff"), expectedhandoff)
          }
        case schema =>
          _review_failure("STORYBOARD_REVIEW_SCHEMA_UNSUPPORTED", s"Unsupported Storyboard schema: $schema")
      }
    }

  private def _identity_field(value: Json, label: String): String =
    value.hcursor.get[String]("identity").toOption.filter(identity => _sha256_pattern.pattern.matcher(identity).matches).getOrElse(
      _review_failure("STORYBOARD_REVIEW_CROSS_MEDIA_IDENTITY_INVALID", s"$label must contain a SHA-256 identity")
    )

  private[video] def _write_storyboard_review_evidence(config: StoryboardReviewConfig): StoryboardReviewResult = {
    val projectfile = _verified_project_file(config.projectFile)
    val projectroot = projectfile.getParent.toRealPath()
    val project = _load_project(projectfile)
    val review = project.storyboardReview.getOrElse(
      _review_failure("STORYBOARD_REVIEW_DECLARATION_MISSING", "video project does not declare storyboardReview")
    )
    val approved = _approved_storyboard(projectroot, review)
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
    approved: ApprovedStoryboard,
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
    val evidenceidentity = _json_identity(payload)
    val evidencejson = payload.deepMerge(Json.obj("identity" -> Json.fromString(evidenceidentity)))
    val evidencepath = savedir.resolve("review-evidence.json").normalize()
    _write_json(evidencepath, evidencejson, savedir)
    val handoffpath = savedir.resolve("handoff.json").normalize()
    val handofffields = Vector(
      Some("schema" -> Json.fromString(_storyboard_handoff_schema)),
      Some("evidencePath" -> Json.fromString(_display_path(projectroot, evidencepath))),
      Some("evidenceIdentity" -> Json.fromString(evidenceidentity)),
      Some("storyboardIdentity" -> Json.fromString(approved.identity)),
      if (review.visualStory.isDefined) Some(
        "visualInputIdentities" -> Json.fromValues(inputs.map { input =>
          Json.obj(
            "reference" -> Json.fromString(input.reference),
            "identity" -> Json.fromString(input.identity)
          )
        })
      ) else None
    ).flatten
    _write_json(handoffpath, Json.obj(handofffields: _*), savedir)
    visualdirectory.foreach(_ => ())
    StoryboardReviewResult(
      projectfile,
      savedir,
      evidencepath,
      handoffpath,
      evidenceidentity,
      approved.identity,
      inputs.map(_.identity)
    )
  }

  private def _write_storyboard_review_evidence_v2(
    projectfile: Path,
    projectroot: Path,
    project: VideoProject,
    review: StoryboardReview,
    approved: ApprovedStoryboard,
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
    val evidenceidentity = _json_identity(payload)
    val evidencejson = _append_identity_last(payload, evidenceidentity)
    val evidencepath = savedir.resolve("review-evidence.json").normalize()
    _write_json(evidencepath, evidencejson, savedir)
    val handoffpath = savedir.resolve("handoff.json").normalize()
    val handoffpayload = _storyboard_handoff_payload_v2(projectroot, evidencepath, evidenceidentity, approved, inputs, visualpage)
    val handoffidentity = _json_identity(handoffpayload)
    _write_json(handoffpath, _append_identity_last(handoffpayload, handoffidentity), savedir)
    StoryboardReviewResult(
      projectfile,
      savedir,
      evidencepath,
      handoffpath,
      evidenceidentity,
      approved.identity,
      inputs.map(_.identity)
    )
  }

  private def _approved_storyboard(projectroot: Path, review: StoryboardReview): ApprovedStoryboard = {
    val approvedidentity = _validate_identity(review.approvedIdentity, "STORYBOARD_REVIEW_APPROVAL_IDENTITY_INVALID")
    val sourcepath = _safe_project_relative_file(projectroot, review.source, "Storyboard review source")
    val result = loadStoryboard(sourcepath)
    if (!result.isValid)
      _review_failure(
        "STORYBOARD_REVIEW_SOURCE_INVALID",
        result.diagnostics.map(_.render).mkString("; ")
      )
    val storyboard = result.storyboard.get
    val identity = storyboardIdentity(storyboard)
    if (identity != approvedidentity)
      _review_failure(
        "STORYBOARD_REVIEW_APPROVAL_MISMATCH",
        s"current Storyboard identity $identity does not equal approvedIdentity $approvedidentity"
      )
    ApprovedStoryboard(sourcepath, _display_path(projectroot, sourcepath), storyboard, identity)
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
      VisualInput(reference, sourcepath, relative, destination, _file_identity(sourcepath))
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
    if (_file_identity(destination) != input.identity)
      _review_failure("STORYBOARD_REVIEW_INPUT_COPY_CHANGED", s"visual input changed while copying: ${input.reference}")
    input.copy(copiedpath = destination)
  }

  private def _review_evidence_payload_v1(approved: ApprovedStoryboard, inputs: Vector[VisualInput], visualreview: Boolean): Json = {
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
      Some("storyboardIdentity" -> Json.fromString(approved.identity)),
      Some("scenes" -> Json.fromValues(scenejson)),
      if (visualreview)
        Some("visualInputs" -> Json.fromValues(inputs.map { input =>
          Json.obj(
            "reference" -> Json.fromString(input.reference),
            "source" -> Json.fromString(input.relativepath),
            "path" -> Json.fromString(_slash_path(Paths.get("visual-inputs").resolve(_relative_path(input.reference)))),
            "identity" -> Json.fromString(input.identity)
          )
        }))
      else None
    ).flatten
    Json.obj(fields: _*)
  }

  private def _review_evidence_payload_v2(
    approved: ApprovedStoryboard,
    inputs: Vector[VisualInput],
    visualpage: Option[VisualPageReview]
  ): Json =
    Json.obj(
      "schema" -> Json.fromString(_review_evidence_schema_v2),
      "status" -> Json.fromString("validated"),
      "source" -> Json.fromString(approved.relativepath),
      "storyboardIdentity" -> Json.fromString(approved.identity),
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
      "path" -> Json.fromString(_slash_path(Paths.get("visual-inputs").resolve(_relative_path(input.reference)))),
      "identity" -> Json.fromString(input.identity)
    )

  private def _visual_page_proof_json(proof: VisualPageProof): Json =
    Json.obj(
      "sceneId" -> Json.fromString(proof.sceneid),
      "kind" -> Json.fromString("visual-page"),
      "source" -> Json.fromString(proof.source),
      "catalog" -> Json.fromString(proof.catalog),
      "pageId" -> Json.fromString(proof.pageid),
      "visualPageSetIdentity" -> Json.fromString(proof.visualpagesetidentity),
      "catalogIdentity" -> Json.fromString(proof.catalogidentity),
      "logicalIdentity" -> Json.fromString(proof.logicalidentity),
      "visualPageIdentity" -> Json.fromString(proof.visualpageidentity),
      "assets" -> Json.fromValues(proof.assets.map(_visual_page_asset_json)),
      "bindingIdentity" -> Json.fromString(proof.bindingidentity)
    )

  private def _visual_page_asset_json(asset: VisualPageAsset): Json =
    Json.obj(
      "id" -> Json.fromString(asset.id),
      "path" -> Json.fromString(asset.path),
      "mediaType" -> Json.fromString(asset.mediatype),
      "sha256" -> Json.fromString(asset.sha256)
    )

  private def _effective_renderer_json(renderer: EffectiveRenderer): Json =
    Json.obj(
      "partId" -> Json.fromString(renderer.partid),
      "configuration" -> renderer.configuration,
      "identity" -> Json.fromString(renderer.identity)
    )

  private def _storyboard_handoff_payload_v2(
    projectroot: Path,
    evidencepath: Path,
    evidenceidentity: String,
    approved: ApprovedStoryboard,
    inputs: Vector[VisualInput],
    visualpage: Option[VisualPageReview]
  ): Json =
    Json.obj(
      "schema" -> Json.fromString(_storyboard_handoff_schema_v2),
      "status" -> Json.fromString("validated"),
      "evidencePath" -> Json.fromString(_display_path(projectroot, evidencepath)),
      "evidenceIdentity" -> Json.fromString(evidenceidentity),
      "storyboardIdentity" -> Json.fromString(approved.identity),
      "visualInputs" -> Json.fromValues(inputs.map(_visual_input_json)),
      "visualPages" -> Json.fromValues(visualpage.toVector.flatMap(_.pages).map(_visual_page_proof_json)),
      "effectiveRenderers" -> Json.fromValues(visualpage.toVector.flatMap(_.renderers).map(_effective_renderer_json))
    )

  private def _visual_page_review(
    projectroot: Path,
    project: VideoProject,
    review: StoryboardReview,
    approved: ApprovedStoryboard
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
      val approvedevidenceidentity = _validate_identity(
        declaration.approvedEvidenceIdentity,
        "STORYBOARD_REVIEW_VISUAL_PAGE_APPROVED_EVIDENCE_IDENTITY_INVALID"
      )
      val pages = visualscreens.map { case (sceneid, index, screen) =>
        _resolve_visual_page_proof(projectroot, approved, sceneid, index, screen, bindingpath)
      }
      val renderers = _effective_storyboard_renderers(projectroot, project, approved.sourcepath)
      Some(VisualPageReview(evidencedirectory, approvedevidenceidentity, pages, renderers))
    }
  }

  private def _resolve_visual_page_proof(
    projectroot: Path,
    approved: ApprovedStoryboard,
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
    val binding = try CozyVisualPageBinding.load(bindingpath, validated) catch {
      case NonFatal(error) =>
        _review_failure("STORYBOARD_REVIEW_VISUAL_PAGE_BINDING_INVALID", s"screen[$index] binding cannot validate against its resolved VisualPageSet/catalog: ${Option(error.getMessage).getOrElse("invalid binding")}")
    }
    val logicalidentity = validated.logicalIdentities.toMap.getOrElse(
      page.id,
      _review_failure("STORYBOARD_REVIEW_VISUAL_PAGE_LOGICAL_IDENTITY_MISSING", s"screen[$index] selected page has no logical identity: ${page.id}")
    )
    val visualpageidentity = validated.visualPageIdentities.toMap.getOrElse(
      page.id,
      _review_failure("STORYBOARD_REVIEW_VISUAL_PAGE_IDENTITY_MISSING", s"screen[$index] selected page has no visual-page identity: ${page.id}")
    )
    VisualPageProof(
      sceneid,
      screen.source,
      screen.catalog,
      screen.pageId,
      validated.documentIdentity,
      validated.catalogIdentity,
      logicalidentity,
      visualpageidentity,
      page.assets.map(asset => VisualPageAsset(asset.id, asset.path, asset.mediaType, asset.sha256)),
      binding.bindingIdentity
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
          Vector(EffectiveRenderer(part.displayId(index + 1), configuration, _json_identity(configuration)))
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

  private def _approved_visual_story_evidence_identity(visualstory: StoryboardReviewVisualStory): String =
    visualstory.approvedEvidenceIdentity match {
      case Some(value) if _is_identity(value) => value
      case Some(value) =>
        _review_failure("STORYBOARD_REVIEW_APPROVED_EVIDENCE_IDENTITY_INVALID", s"approvedEvidenceIdentity is not a lowercase SHA-256 identity: $value")
      case None =>
        _review_failure("STORYBOARD_REVIEW_APPROVED_EVIDENCE_MISSING", "visualStory.approvedEvidenceIdentity is required before video build")
    }

  private def _approved_evidence_identities(review: StoryboardReview, visualpage: Option[VisualPageReview]): Vector[String] = {
    val visualstory = review.visualStory.map(_approved_visual_story_evidence_identity)
    val page = visualpage.map(_.approvedevidenceidentity)
    (visualstory.toVector ++ page.toVector).distinct
  }

  private def _validate_current_evidence(
    evidence: Json,
    expected: Json,
    approved: ApprovedStoryboard,
    inputs: Vector[VisualInput],
    approvedevidenceidentity: String
  ): Unit = {
    val actualidentity = evidence.hcursor.get[String]("identity").toOption.getOrElse(
      _review_failure("STORYBOARD_REVIEW_EVIDENCE_IDENTITY_MISSING", "review-evidence.json has no identity")
    )
    if (!_is_identity(actualidentity))
      _review_failure("STORYBOARD_REVIEW_EVIDENCE_IDENTITY_INVALID", s"review-evidence.json identity is invalid: $actualidentity")
    val payload = evidence.asObject.map(_.remove("identity")).map(Json.fromJsonObject).getOrElse(
      _review_failure("STORYBOARD_REVIEW_EVIDENCE_MALFORMED", "review-evidence.json must be a JSON object")
    )
    if (evidence.hcursor.get[String]("schema").toOption != Some(_review_evidence_schema))
      _review_failure("STORYBOARD_REVIEW_EVIDENCE_SCHEMA_INVALID", "review-evidence.json schema is not supported")
    if (evidence.hcursor.get[String]("status").toOption != Some("validated"))
      _review_failure("STORYBOARD_REVIEW_EVIDENCE_STATUS_INVALID", "review-evidence.json status is not validated")
    if (_json_identity(payload) != actualidentity)
      _review_failure("STORYBOARD_REVIEW_EVIDENCE_IDENTITY_MISMATCH", "review-evidence.json identity does not match its canonical payload")
    if (payload != expected)
      _review_failure("STORYBOARD_REVIEW_EVIDENCE_STALE", s"review-evidence.json is stale for approved Storyboard ${approved.identity}")
    if (actualidentity != approvedevidenceidentity)
      _review_failure(
        "STORYBOARD_REVIEW_APPROVED_EVIDENCE_MISMATCH",
        s"approvedEvidenceIdentity $approvedevidenceidentity does not match current evidence $actualidentity"
      )
    inputs.foreach { input =>
      _require_direct_output_file(input.copiedpath, input.copiedpath.getParent, s"Storyboard visual input ${input.reference}")
      if (_file_identity(input.copiedpath) != input.identity)
        _review_failure("STORYBOARD_REVIEW_INPUT_STALE", s"visual input changed since evidence generation: ${input.reference}")
    }
  }

  private def _validate_current_evidence_v2(
    evidence: Json,
    expected: Json,
    approved: ApprovedStoryboard,
    inputs: Vector[VisualInput],
    approvedidentities: Vector[String]
  ): Unit = {
    val actualidentity = evidence.hcursor.get[String]("identity").toOption.getOrElse(
      _review_failure("STORYBOARD_REVIEW_EVIDENCE_IDENTITY_MISSING", "review-evidence.json has no identity")
    )
    if (!_is_identity(actualidentity))
      _review_failure("STORYBOARD_REVIEW_EVIDENCE_IDENTITY_INVALID", s"review-evidence.json identity is invalid: $actualidentity")
    val payload = evidence.asObject.map(_.remove("identity")).map(Json.fromJsonObject).getOrElse(
      _review_failure("STORYBOARD_REVIEW_EVIDENCE_MALFORMED", "review-evidence.json must be a JSON object")
    )
    if (evidence.hcursor.get[String]("schema").toOption != Some(_review_evidence_schema_v2))
      _review_failure("STORYBOARD_REVIEW_EVIDENCE_SCHEMA_INVALID", "review-evidence.json schema is not supported for Storyboard v2")
    if (evidence.hcursor.get[String]("status").toOption != Some("validated"))
      _review_failure("STORYBOARD_REVIEW_EVIDENCE_STATUS_INVALID", "review-evidence.json status is not validated")
    if (_json_identity(payload) != actualidentity)
      _review_failure("STORYBOARD_REVIEW_EVIDENCE_IDENTITY_MISMATCH", "review-evidence.json identity does not match its canonical payload")
    if (payload != expected)
      _review_failure("STORYBOARD_REVIEW_EVIDENCE_STALE", s"review-evidence.json is stale for approved Storyboard ${approved.identity}")
    approvedidentities.foreach { approvedidentity =>
      if (actualidentity != approvedidentity)
        _review_failure(
          "STORYBOARD_REVIEW_APPROVED_EVIDENCE_MISMATCH",
          s"approved evidence identity $approvedidentity does not match current evidence $actualidentity"
        )
    }
    inputs.foreach { input =>
      _require_direct_output_file(input.copiedpath, input.copiedpath.getParent, s"Storyboard visual input ${input.reference}")
      if (_file_identity(input.copiedpath) != input.identity)
        _review_failure("STORYBOARD_REVIEW_INPUT_STALE", s"visual input changed since evidence generation: ${input.reference}")
    }
  }

  private def _validate_current_handoff_v2(handoff: Json, expected: Json): Unit = {
    val actualidentity = handoff.hcursor.get[String]("identity").toOption.getOrElse(
      _review_failure("STORYBOARD_REVIEW_HANDOFF_IDENTITY_MISSING", "handoff.json has no identity")
    )
    if (!_is_identity(actualidentity))
      _review_failure("STORYBOARD_REVIEW_HANDOFF_IDENTITY_INVALID", s"handoff.json identity is invalid: $actualidentity")
    val payload = handoff.asObject.map(_.remove("identity")).map(Json.fromJsonObject).getOrElse(
      _review_failure("STORYBOARD_REVIEW_HANDOFF_MALFORMED", "handoff.json must be a JSON object")
    )
    if (handoff.hcursor.get[String]("schema").toOption != Some(_storyboard_handoff_schema_v2))
      _review_failure("STORYBOARD_REVIEW_HANDOFF_SCHEMA_INVALID", "handoff.json schema is not supported for Storyboard v2")
    if (handoff.hcursor.get[String]("status").toOption != Some("validated"))
      _review_failure("STORYBOARD_REVIEW_HANDOFF_STATUS_INVALID", "handoff.json status is not validated")
    if (_json_identity(payload) != actualidentity)
      _review_failure("STORYBOARD_REVIEW_HANDOFF_IDENTITY_MISMATCH", "handoff.json identity does not match its canonical payload")
    if (payload != expected)
      _review_failure("STORYBOARD_REVIEW_HANDOFF_STALE", "handoff.json does not equal the current Storyboard v2 review handoff payload")
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
    try {
      parser.parse(Files.readString(path, StandardCharsets.UTF_8)).fold(
        error => _review_failure("STORYBOARD_REVIEW_EVIDENCE_MALFORMED", s"$label is not valid JSON: ${error.getMessage}"),
        identity
      )
    } catch {
      case NonFatal(error) => _review_failure("STORYBOARD_REVIEW_EVIDENCE_READ_FAILED", s"cannot read $label: ${error.getMessage}")
    }
  }

  private def _validate_identity(value: String, code: String): String = {
    val normalized = Option(value).getOrElse("")
    if (!_is_identity(normalized))
      _review_failure(code, s"invalid SHA-256 identity: $value")
    normalized
  }

  private def _is_identity(value: String): Boolean =
    _sha256_pattern.pattern.matcher(Option(value).getOrElse("")).matches()

  private def _append_identity_last(payload: Json, identity: String): Json =
    Json.obj((payload.asObject.toVector.flatMap(_.toVector) :+ ("identity" -> Json.fromString(identity))): _*)

  private def _json_identity(json: Json): String = {
    val digest = MessageDigest.getInstance("SHA-256")
    "sha256:" + _sha256_hex(digest.digest(json.noSpaces.getBytes(StandardCharsets.UTF_8)))
  }

  private def _file_identity(path: Path): String = {
    val digest = MessageDigest.getInstance("SHA-256")
    val input = Files.newInputStream(path)
    try {
      val buffer = new Array[Byte](8192)
      var count = input.read(buffer)
      while (count >= 0) {
        if (count > 0) digest.update(buffer, 0, count)
        count = input.read(buffer)
      }
    } finally input.close()
    "sha256:" + _sha256_hex(digest.digest())
  }

  private def _sha256_hex(bytes: Array[Byte]): String =
    bytes.map(value => f"${value & 0xff}%02x").mkString

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
