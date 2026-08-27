package cozy.media

import cozy.runtime.CozyCliArgs
import cozy.video.CozyVideo
import io.circe.{Json, JsonObject}
import io.circe.parser.parse
import java.nio.charset.StandardCharsets
import java.nio.file.{Files, LinkOption, Path, StandardCopyOption}
import java.security.MessageDigest
import org.goldenport.RAISE
import org.goldenport.cli.spec
import scala.collection.JavaConverters._
import scala.util.control.NonFatal

/*
 * @since   Aug. 27, 2026
 * @version Aug. 27, 2026
 * @author  ASAMI, Tomoharu
 */
private[cozy] object CozyMediaCrossReview {
  final case class BuildConfig(mediaFile: Path, target: String, videoProject: Path, save: String)
  object BuildConfig {
    def create(args: List[String]): BuildConfig = {
      val parsed = CozyCliArgs.parseStrict(_p_media_file, _p_target, _p_video_project, _p_save)(_normalize_property_args(args))
      BuildConfig(
        parsed.argument("media-file").map(CozyCliArgs.toPath).getOrElse(_invalid("Missing media descriptor for Cross-media Review")),
        parsed.requiredProperty("target"),
        CozyCliArgs.toPath(parsed.requiredProperty("video-project")),
        parsed.requiredProperty("save")
      )
    }
  }

  final case class VerifyConfig(mediaFile: Path, target: String, videoProject: Path, crossReview: String)
  object VerifyConfig {
    def create(args: List[String]): VerifyConfig = {
      val parsed = CozyCliArgs.parseStrict(_p_media_file, _p_target, _p_video_project, _p_cross_review)(_normalize_property_args(args))
      VerifyConfig(
        parsed.argument("media-file").map(CozyCliArgs.toPath).getOrElse(_invalid("Missing media descriptor for Cross-media Review")),
        parsed.requiredProperty("target"),
        CozyCliArgs.toPath(parsed.requiredProperty("video-project")),
        parsed.requiredProperty("cross-review")
      )
    }
  }

  private final case class Expected(value: Json, descriptorroot: Path, presentationreview: Path)
  private final case class Slide(id: String, sha256: String)
  private final case class VisualPage(
    sceneid: String,
    pageid: String,
    logicalidentity: String,
    visualpageidentity: String,
    assets: Vector[CozyMediaPresentation.VisualPageAssetEvidence]
  )

  private val _schema = "cozy.media.cross-review.v1"
  private val _presentation_review_schema = "cozy.media.presentation-review.v2"
  private val _storyboard_evidence_schema = "cozy.video.storyboard-review-evidence.v2"
  private val _storyboard_handoff_schema = "cozy.video.storyboard-handoff.v2"
  private val _p_media_file = spec.Parameter.argumentFile("media-file")
  private val _p_target = spec.Parameter.property("target")
  private val _p_video_project = spec.Parameter.property("video-project")
  private val _p_save = spec.Parameter.property("save")
  private val _p_cross_review = spec.Parameter.property("cross-review")
  private val _property_options = Set("target", "video-project", "save", "cross-review")

  def build(config: BuildConfig): String = {
    val expected = _expected(config.mediaFile, config.target, config.videoProject)
    val output = _safe_output(expected.descriptorroot, config.save, "--save", createparents = true)
    if (output == expected.presentationreview)
      _invalid("Cross-media Review output must not replace the presentation review manifest")
    val rechecked = _expected(config.mediaFile, config.target, config.videoProject)
    if (expected.value != rechecked.value)
      _invalid("Cross-media Review inputs changed while reconstructing current evidence")
    _write_atomic(output, expected.value)
    s"Cozy Media Cross Review Build\nstatus: valid\noutput: ${_relative(expected.descriptorroot, output)}"
  }

  def verify(config: VerifyConfig): String = {
    val expected = _expected(config.mediaFile, config.target, config.videoProject)
    val input = _safe_output(expected.descriptorroot, config.crossReview, "--cross-review", createparents = false)
    val actual = _read_json(input, "Cross-media Review")
    if (actual != expected.value)
      _invalid("Cross-media Review differs from exact current reconstruction")
    s"Cozy Media Cross Review Verify\nstatus: valid\ninput: ${_relative(expected.descriptorroot, input)}"
  }

  private def _expected(mediafile: Path, target: String, videoproject: Path): Expected = {
    _identity(target, "Cross-media Review target")
    val plan = CozyMedia.resolvePlan(CozyMedia.CommandConfig(mediafile, target = Some(target)))
    val presentation = plan.resources.filter(_.resource.id == target) match {
      case Vector(value) if value.resource.build == "presentation" => value
      case Vector(_) => _invalid(s"Cross-media Review target is not a presentation resource: $target")
      case _ => _invalid(s"Cross-media Review target must resolve exactly once: $target")
    }
    CozyMediaPresentation.requireCurrent(plan, presentation, requireReviewState = false)
    val evidence = CozyMediaPresentation.visualPageEvidence(plan, presentation)
    val review = _read_json(evidence.reviewPath, "Presentation VisualPage review manifest")
    val slides = _presentation_slides(review, evidence)
    val current = CozyVideo.storyboardReviewCurrent(videoproject)
    val storyboard = _read_json(current.evidencePath, "Storyboard v2 review evidence")
    val handoff = _read_json(current.handoffPath, "Storyboard v2 review handoff")
    val visualpages = _storyboard_visual_pages(storyboard, current, evidence)
    _validate_handoff(handoff, current)
    if (slides.map(_.id) != evidence.pages.map(_.id))
      _invalid("Presentation review slide order does not equal the VisualPageSet page order")
    if (slides.map(_.id).distinct.size != slides.size)
      _invalid("Presentation review slide identifiers must be unique")
    if (visualpages.isEmpty)
      _invalid("Cross-media Review requires at least one Storyboard visual-page screen")
    if (visualpages.map(_.sceneid).distinct.size != visualpages.size)
      _invalid("Storyboard Cross-media Review scene identifiers must be unique")
    val pages = evidence.pages.map(page => page.id -> page).toMap
    visualpages.foreach { visual =>
      val presentationpage = pages.getOrElse(visual.pageid, _invalid(s"Storyboard VisualPage is absent from presentation: ${visual.pageid}"))
      if (visual.logicalidentity != presentationpage.logicalIdentity || visual.visualpageidentity != presentationpage.visualPageIdentity)
        _invalid(s"Storyboard VisualPage identity differs from presentation: ${visual.pageid}")
      if (visual.assets != presentationpage.assets)
        _invalid(s"Storyboard VisualPage assets differ from presentation: ${visual.pageid}")
    }
    val value = Json.obj(
      "schema" -> Json.fromString(_schema),
      "target" -> Json.fromString(target),
      "presentationReviewIdentity" -> Json.fromString(_file_identity(evidence.reviewPath)),
      "storyboardEvidenceIdentity" -> Json.fromString(current.evidenceIdentity),
      "storyboardHandoffIdentity" -> Json.fromString(current.handoffIdentity),
      "storyboardIdentity" -> Json.fromString(current.storyboardIdentity),
      "visualPageSetIdentity" -> Json.fromString(evidence.visualPageSetIdentity),
      "catalogIdentity" -> Json.fromString(evidence.catalogIdentity),
      "bindingIdentity" -> Json.fromString(evidence.bindingIdentity),
      "slides" -> Json.fromValues(slides.map(slide => Json.obj("id" -> Json.fromString(slide.id), "sha256" -> Json.fromString(slide.sha256)))),
      "visualPages" -> Json.fromValues(visualpages.map(_visual_page_json)),
      "verification" -> Json.obj(
        "status" -> Json.fromString("valid"),
        "semanticApproval" -> Json.fromString("not-recorded"),
        "visualApproval" -> Json.fromString("not-recorded"),
        "audiovisualApproval" -> Json.fromString("not-recorded")
      )
    )
    Expected(value, plan.descriptorRoot.toRealPath(), evidence.reviewPath)
  }

  private def _presentation_slides(value: Json, evidence: CozyMediaPresentation.VisualPageEvidence): Vector[Slide] = {
    val objectvalue = _object(value, "Presentation VisualPage review manifest")
    _exact_ordered_keys(objectvalue, Vector("schema", "target", "knowledge", "language", "inputSetSha256", "renderer", "visualPageSet", "catalog", "binding", "template", "pptx", "slides", "assets", "montage", "articlePdf", "infographic", "verification"), "Presentation VisualPage review manifest")
    if (_string(objectvalue, "schema", "Presentation VisualPage review manifest") != _presentation_review_schema)
      _invalid("Cross-media Review requires a presentation-review.v2 manifest")
    _semantic_identity(objectvalue("visualPageSet").getOrElse(_invalid("Presentation review VisualPageSet is missing")), evidence.visualPageSetIdentity, "visualPageSet")
    _semantic_identity(objectvalue("catalog").getOrElse(_invalid("Presentation review catalog is missing")), evidence.catalogIdentity, "catalog")
    _semantic_identity(objectvalue("binding").getOrElse(_invalid("Presentation review binding is missing")), evidence.bindingIdentity, "binding")
    objectvalue("slides").flatMap(_.asArray).getOrElse(_invalid("Presentation review slides must be an array")).toVector.map { value =>
      val slide = _object(value, "Presentation review slide")
      _exact_ordered_keys(slide, Vector("id", "path", "sha256"), "Presentation review slide")
      Slide(_string(slide, "id", "Presentation review slide"), _sha256_value(slide, "sha256", "Presentation review slide"))
    }
  }

  private def _storyboard_visual_pages(
    value: Json,
    current: CozyVideo.StoryboardReviewCurrent,
    evidence: CozyMediaPresentation.VisualPageEvidence
  ): Vector[VisualPage] = {
    val objectvalue = _object(value, "Storyboard v2 review evidence")
    _exact_ordered_keys(objectvalue, Vector("schema", "status", "source", "storyboardIdentity", "scenes", "visualInputs", "visualPages", "effectiveRenderers", "identity"), "Storyboard v2 review evidence")
    if (_string(objectvalue, "schema", "Storyboard v2 review evidence") != _storyboard_evidence_schema || _string(objectvalue, "storyboardIdentity", "Storyboard v2 review evidence") != current.storyboardIdentity || _identity_field(objectvalue, "Storyboard v2 review evidence") != current.evidenceIdentity)
      _invalid("Storyboard v2 review evidence identity differs from current project")
    objectvalue("visualPages").flatMap(_.asArray).getOrElse(_invalid("Storyboard v2 visualPages must be an array")).toVector.map { value =>
      val page = _object(value, "Storyboard v2 visual page")
      _exact_ordered_keys(page, Vector("sceneId", "kind", "source", "catalog", "pageId", "visualPageSetIdentity", "catalogIdentity", "logicalIdentity", "visualPageIdentity", "assets", "bindingIdentity"), "Storyboard v2 visual page")
      if (_string(page, "kind", "Storyboard v2 visual page") != "visual-page")
        _invalid("Storyboard v2 Cross-media Review only accepts visual-page screens")
      _identity_matches(page, "visualPageSetIdentity", evidence.visualPageSetIdentity, "Storyboard VisualPageSet")
      _identity_matches(page, "catalogIdentity", evidence.catalogIdentity, "Storyboard catalog")
      _identity_matches(page, "bindingIdentity", evidence.bindingIdentity, "Storyboard binding")
      val assets = page("assets").flatMap(_.asArray).getOrElse(_invalid("Storyboard v2 visual page assets must be an array")).toVector.map { value =>
        val asset = _object(value, "Storyboard v2 visual page asset")
        _exact_ordered_keys(asset, Vector("id", "path", "mediaType", "sha256"), "Storyboard v2 visual page asset")
        CozyMediaPresentation.VisualPageAssetEvidence(_string(asset, "id", "Storyboard v2 visual page asset"), _sha256_value(asset, "sha256", "Storyboard v2 visual page asset"))
      }
      if (assets.map(_.id).distinct.size != assets.size)
        _invalid("Storyboard v2 visual page asset identifiers must be unique")
      VisualPage(
        _string(page, "sceneId", "Storyboard v2 visual page"),
        _string(page, "pageId", "Storyboard v2 visual page"),
        _sha256_identity(page, "logicalIdentity", "Storyboard v2 visual page"),
        _sha256_identity(page, "visualPageIdentity", "Storyboard v2 visual page"),
        assets
      )
    }
  }

  private def _validate_handoff(value: Json, current: CozyVideo.StoryboardReviewCurrent): Unit = {
    val objectvalue = _object(value, "Storyboard v2 review handoff")
    _exact_ordered_keys(objectvalue, Vector("schema", "status", "evidencePath", "evidenceIdentity", "storyboardIdentity", "visualInputs", "visualPages", "effectiveRenderers", "identity"), "Storyboard v2 review handoff")
    if (_string(objectvalue, "schema", "Storyboard v2 review handoff") != _storyboard_handoff_schema || _string(objectvalue, "evidenceIdentity", "Storyboard v2 review handoff") != current.evidenceIdentity || _string(objectvalue, "storyboardIdentity", "Storyboard v2 review handoff") != current.storyboardIdentity || _identity_field(objectvalue, "Storyboard v2 review handoff") != current.handoffIdentity)
      _invalid("Storyboard v2 handoff identity differs from current project")
  }

  private def _visual_page_json(value: VisualPage): Json =
    Json.obj(
      "sceneId" -> Json.fromString(value.sceneid),
      "pageId" -> Json.fromString(value.pageid),
      "logicalIdentity" -> Json.fromString(value.logicalidentity),
      "visualPageIdentity" -> Json.fromString(value.visualpageidentity),
      "assets" -> Json.fromValues(value.assets.map(asset => Json.obj("id" -> Json.fromString(asset.id), "sha256" -> Json.fromString(asset.sha256))))
    )

  private def _semantic_identity(value: Json, expected: String, label: String): Unit = {
    val objectvalue = _object(value, s"Presentation review $label")
    _exact_ordered_keys(objectvalue, Vector("path", "sha256"), s"Presentation review $label")
    if (_sha256_value(objectvalue, "sha256", s"Presentation review $label") != expected.stripPrefix("sha256:"))
      _invalid(s"Presentation review $label identity differs from current VisualPage evidence")
  }

  private def _identity_matches(value: JsonObject, field: String, expected: String, label: String): Unit =
    if (_sha256_identity(value, field, label) != expected)
      _invalid(s"$label identity differs from current VisualPage evidence")

  private def _sha256_identity(value: JsonObject, field: String, label: String): String = {
    val identity = _string(value, field, label)
    if (!identity.matches("sha256:[0-9a-f]{64}")) _invalid(s"$label.$field must be a SHA-256 identity")
    identity
  }

  private def _identity_field(value: JsonObject, label: String): String =
    _sha256_identity(value, "identity", label)

  private def _safe_output(root: Path, raw: String, label: String, createparents: Boolean): Path = {
    if (raw == null || raw.isEmpty || raw != raw.trim || raw.contains('\\') || !raw.endsWith(".json"))
      _invalid(s"$label must be a normalized descriptor-relative .json path")
    val relative = try Path.of(raw) catch { case NonFatal(_) => _invalid(s"$label path is invalid") }
    if (relative.isAbsolute || relative.normalize.toString.replace('\\', '/') != raw || raw.split('/').contains(".."))
      _invalid(s"$label must be a normalized descriptor-relative path")
    val canonicalroot = try root.toRealPath() catch { case NonFatal(_) => _invalid("Cross-media Review descriptor root is unreadable") }
    val path = canonicalroot.resolve(relative).normalize()
    if (!path.startsWith(canonicalroot) || path == canonicalroot)
      _invalid(s"$label escapes the Media Package root")
    _assert_no_symlink_components(canonicalroot, path.getParent, label)
    if (createparents) {
      try Files.createDirectories(path.getParent) catch { case NonFatal(error) => _invalid(s"$label parent cannot be created: ${error.getMessage}") }
      _assert_no_symlink_components(canonicalroot, path.getParent, label)
      if (Files.exists(path, LinkOption.NOFOLLOW_LINKS))
        _invalid(s"$label must designate an absent normalized descriptor-relative .json output")
    }
    if (!createparents && (!Files.exists(path, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(path) || !Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)))
      _invalid(s"$label must be an existing direct regular non-symlink file")
    if (Files.exists(path, LinkOption.NOFOLLOW_LINKS) && (Files.isSymbolicLink(path) || !Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)))
      _invalid(s"$label must be a direct regular non-symlink file")
    path
  }

  private def _assert_no_symlink_components(root: Path, path: Path, label: String): Unit = {
    val target = path.toAbsolutePath.normalize()
    if (!target.startsWith(root)) _invalid(s"$label escapes the Media Package root")
    root.relativize(target).iterator.asScala.foldLeft(root) { (current, segment) =>
      val next = current.resolve(segment)
      if (Files.exists(next, LinkOption.NOFOLLOW_LINKS) && Files.isSymbolicLink(next))
        _invalid(s"$label must not traverse a symbolic link: $next")
      next
    }
  }

  private def _write_atomic(path: Path, value: Json): Unit = {
    val parent = path.getParent
    if (Files.exists(path, LinkOption.NOFOLLOW_LINKS))
      _invalid("Cross-media Review output must designate an absent direct regular file")
    val temporary = Files.createTempFile(parent, ".cozy-cross-review-", ".json")
    try {
      Files.writeString(temporary, value.noSpaces + "\n", StandardCharsets.UTF_8)
      Files.move(temporary, path, StandardCopyOption.ATOMIC_MOVE)
    } catch {
      case NonFatal(error) => _invalid(s"Cross-media Review cannot be written: ${error.getMessage}")
    } finally {
      Files.deleteIfExists(temporary)
    }
    if (Files.isSymbolicLink(path) || !Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS))
      _invalid("Cross-media Review output is not a direct regular file")
  }

  private def _read_json(path: Path, label: String): Json = {
    if (Files.isSymbolicLink(path) || !Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS))
      _invalid(s"$label must be a direct regular file")
    parse(Files.readString(path, StandardCharsets.UTF_8)).fold(_ => _invalid(s"$label must be JSON"), identity)
  }

  private def _file_identity(path: Path): String =
    "sha256:" + MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path)).map(value => f"${value & 0xff}%02x").mkString

  private def _relative(root: Path, path: Path): String = root.relativize(path).toString.replace('\\', '/')
  private def _object(value: Json, label: String): JsonObject = value.asObject.getOrElse(_invalid(s"$label must be an object"))
  private def _exact_ordered_keys(value: JsonObject, expected: Vector[String], label: String): Unit = if (value.keys.toVector != expected) _invalid(s"$label requires exactly ordered fields: ${expected.mkString(", ")}")
  private def _string(value: JsonObject, field: String, label: String): String = value(field).flatMap(_.asString).filter(value => value.nonEmpty && value == value.trim).getOrElse(_invalid(s"$label.$field must be a non-empty exact string"))
  private def _sha256_value(value: JsonObject, field: String, label: String): String = { val hash = _string(value, field, label); if (hash.matches("[0-9a-f]{64}")) hash else _invalid(s"$label.$field must be SHA-256") }
  private def _identity(value: String, label: String): Unit = if (value == null || value.isEmpty || value != value.trim) _invalid(s"$label must be a non-empty exact string")
  private def _invalid(message: String): Nothing = RAISE.invalidArgumentFault(message)

  private def _normalize_property_args(args: List[String]): List[String] =
    args.flatMap {
      case value if value.startsWith("--") && value.contains("=") =>
        val pair = value.drop(2).split("=", 2)
        if (pair.length == 2 && _property_options.contains(pair(0))) List("--" + pair(0), pair(1)) else List(value)
      case value => List(value)
    }
}
