package cozy.media

import cozy.runtime.CozyCliArgs
import cozy.video.CozyVideo
import com.fasterxml.jackson.core.{JsonFactory, JsonParseException, JsonParser => JacksonParser}
import io.circe.{Json, JsonObject}
import io.circe.parser.parse
import java.nio.charset.StandardCharsets
import java.nio.file.{Files, LinkOption, Path, StandardCopyOption}
import org.goldenport.RAISE
import org.goldenport.cli.spec
import scala.collection.JavaConverters._
import scala.util.control.NonFatal

/*
 * @since   Aug. 27, 2026
 *  version Sep. 30, 2026
 * @version Oct.  1, 2026
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

  private final case class Expected(value: Json, descriptorroot: Path, protectedpaths: Set[Path])
  private final case class Slide(id: String)
  private final case class VisualPage(
    sceneid: String,
    pageid: String,
    assets: Vector[CozyMediaPresentation.VisualPageAssetEvidence],
    inputs: Set[Path]
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
    if (expected.protectedpaths.contains(output))
      _invalid("Cross-media Review output must not replace an input or evidence file")
    _write_atomic(output, expected.value)
    s"Cozy Media Cross Review Build\nstatus: valid\noutput: ${_relative(expected.descriptorroot, output)}"
  }

  def verify(config: VerifyConfig): String = {
    val expected = _expected(config.mediaFile, config.target, config.videoProject)
    val input = _safe_output(expected.descriptorroot, config.crossReview, "--cross-review", createparents = false)
    val actual = _read_json(input, "Cross-media Review")
    _validate_record(actual, expected.value)
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
    val slides = _presentation_slides(review)
    val current = CozyVideo.storyboardReviewCurrent(videoproject)
    val storyboard = _read_json(current.evidencePath, "Storyboard v2 review evidence")
    val handoff = _read_json(current.handoffPath, "Storyboard v2 review handoff")
    val visualpages = _storyboard_visual_pages(storyboard, current)
    _validate_handoff(handoff)
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
      if (visual.assets.map(asset => asset.id -> asset.mediaType) != presentationpage.assets.map(asset => asset.id -> asset.mediaType) ||
          !visual.assets.zip(presentationpage.assets).forall { case (left, right) => java.util.Arrays.equals(Files.readAllBytes(left.path), Files.readAllBytes(right.path)) })
        _invalid(s"Storyboard VisualPage assets differ from presentation: ${visual.pageid}")
    }
    val value = Json.obj(
      "schema" -> Json.fromString(_schema),
      "target" -> Json.fromString(target),
      "slides" -> Json.fromValues(slides.map(slide => Json.obj("id" -> Json.fromString(slide.id)))),
      "visualPages" -> Json.fromValues(visualpages.map(_visual_page_json)),
      "verification" -> Json.obj(
        "status" -> Json.fromString("valid"),
        "semanticApproval" -> Json.fromString("not-recorded"),
        "visualApproval" -> Json.fromString("not-recorded"),
        "audiovisualApproval" -> Json.fromString("not-recorded")
      )
    )
    val root = plan.descriptorRoot.toRealPath()
    val configuration = presentation.resource.presentation.get
    val storyroot = current.projectFile.toAbsolutePath.normalize().getParent
    val visualinputs = storyboard.hcursor.downField("visualInputs").focus.flatMap(_.asArray).toVector.flatten.flatMap { input =>
      Vector(
        _material_path(storyroot, input.hcursor.get[String]("source").toOption.getOrElse(_invalid("Storyboard visual input source is missing")), "Storyboard visual input source"),
        _material_path(current.evidencePath.getParent, input.hcursor.get[String]("path").toOption.getOrElse(_invalid("Storyboard visual input path is missing")), "Storyboard visual input copy")
      )
    }
    val reviewpaths = Vector("visualPageSet", "catalog", "binding", "template", "pptx", "montage").map { name =>
      root.resolve(review.hcursor.downField(name).get[String]("path").toOption.getOrElse(_invalid(s"Presentation review $name path is missing"))).normalize()
    } ++ review.hcursor.downField("slides").focus.flatMap(_.asArray).toVector.flatten.map { slide =>
      root.resolve(slide.hcursor.get[String]("path").toOption.getOrElse(_invalid("Presentation slide path is missing"))).normalize()
    }
    val protectedpaths = Set(plan.descriptorFile.toAbsolutePath.normalize(), plan.knowledgeSource.toAbsolutePath.normalize(), current.projectFile.toAbsolutePath.normalize(), evidence.reviewPath, current.evidencePath, current.handoffPath) ++
      plan.resources.flatMap(resource => resource.source.toVector ++ resource.output.toVector ++ resource.project.toVector).map(_.toAbsolutePath.normalize()) ++
      plan.descriptor.receipt.toVector.flatMap(_.inputs).map(input => root.resolve(input.path).normalize()) ++
      evidence.pages.flatMap(_.assets.map(_.path)) ++ visualpages.flatMap(_.inputs) ++ visualinputs ++ reviewpaths ++
      Vector(configuration.rendererManifest, configuration.reviewState).map(path => root.resolve(path).normalize())
    Expected(value, root, protectedpaths)
  }

  private def _presentation_slides(value: Json): Vector[Slide] = {
    val objectvalue = _object(value, "Presentation VisualPage review manifest")
    _exact_ordered_keys(objectvalue.remove("inputSetSha256"), Vector("schema", "target", "knowledge", "language", "renderer", "visualPageSet", "catalog", "binding", "template", "pptx", "slides", "assets", "montage", "articlePdf", "infographic", "verification"), "Presentation VisualPage review manifest")
    if (_string(objectvalue, "schema", "Presentation VisualPage review manifest") != _presentation_review_schema)
      _invalid("Cross-media Review requires a presentation-review.v2 manifest")
    Vector("visualPageSet", "catalog", "binding").foreach { name =>
      val wrapper = _object(objectvalue(name).getOrElse(_invalid(s"Presentation review $name is missing")), s"Presentation review $name").filterKeys(key => key != "value" && key != "sha256")
      _exact_ordered_keys(wrapper, Vector("path"), s"Presentation review $name")
      _string(wrapper, "path", s"Presentation review $name")
    }
    objectvalue("slides").flatMap(_.asArray).getOrElse(_invalid("Presentation review slides must be an array")).toVector.map { value =>
      val slide = _object(value, "Presentation review slide")
      _exact_ordered_keys(slide.remove("sha256"), Vector("id", "path"), "Presentation review slide")
      Slide(_string(slide, "id", "Presentation review slide"))
    }
  }

  private def _storyboard_visual_pages(
    value: Json,
    current: CozyVideo.StoryboardReviewCurrent
  ): Vector[VisualPage] = {
    val objectvalue = _object(value, "Storyboard v2 review evidence")
    _exact_ordered_keys(objectvalue.remove("storyboardIdentity").remove("identity").remove("evidenceIdentity").remove("visualInputIdentities"), Vector("schema", "status", "source", "scenes", "visualInputs", "visualPages", "effectiveRenderers"), "Storyboard v2 review evidence")
    if (_string(objectvalue, "schema", "Storyboard v2 review evidence") != _storyboard_evidence_schema)
      _invalid("Cross-media Review requires Storyboard v2 review evidence")
    val root = current.projectFile.toAbsolutePath.normalize().getParent
    val source = _material_path(root, _string(objectvalue, "source", "Storyboard v2 review evidence"), "Storyboard source")
    objectvalue("visualPages").flatMap(_.asArray).getOrElse(_invalid("Storyboard v2 visualPages must be an array")).toVector.map { value =>
      val page = _without_visual_page_proofs(_object(value, "Storyboard v2 visual page"))
      _exact_ordered_keys(page, Vector("sceneId", "kind", "source", "catalog", "pageId", "assets"), "Storyboard v2 visual page")
      if (_string(page, "kind", "Storyboard v2 visual page") != "visual-page")
        _invalid("Storyboard v2 Cross-media Review only accepts visual-page screens")
      val pageset = _material_path(source.getParent, _string(page, "source", "Storyboard v2 visual page"), "Storyboard VisualPageSet source")
      val catalog = _material_path(source.getParent, _string(page, "catalog", "Storyboard v2 visual page"), "Storyboard VisualPage catalog")
      val document = CozyVisualPage.load(pageset, catalog).document match {
        case document: CozyVisualPage.PageSet => document
        case _ => _invalid("Storyboard VisualPage source must be a PageSet")
      }
      val selected = document.pages.filter(_.id == _string(page, "pageId", "Storyboard v2 visual page")) match {
        case Vector(selected) => selected
        case _ => _invalid("Storyboard VisualPage pageId must resolve exactly once")
      }
      page("assets").flatMap(_.asArray).getOrElse(_invalid("Storyboard v2 visual page assets must be an array"))
      val assets = selected.assets.map(asset => CozyMediaPresentation.VisualPageAssetEvidence(asset.id, _material_path(pageset.getParent, asset.path, "Storyboard VisualPage asset"), asset.mediaType))
      if (assets.map(_.id).distinct.size != assets.size)
        _invalid("Storyboard v2 visual page asset identifiers must be unique")
      VisualPage(
        _string(page, "sceneId", "Storyboard v2 visual page"),
        _string(page, "pageId", "Storyboard v2 visual page"),
        assets,
        Set(source, pageset, catalog) ++ assets.map(_.path) ++ selected.sources.map(binding => _material_path(pageset.getParent, binding.path, "Storyboard VisualPage source binding"))
      )
    }
  }

  private def _validate_handoff(value: Json): Unit = {
    val objectvalue = _object(value, "Storyboard v2 review handoff")
    _exact_ordered_keys(objectvalue.remove("evidenceIdentity").remove("storyboardIdentity").remove("identity").remove("visualInputIdentities"), Vector("schema", "status", "evidencePath", "visualInputs", "visualPages", "effectiveRenderers"), "Storyboard v2 review handoff")
    if (_string(objectvalue, "schema", "Storyboard v2 review handoff") != _storyboard_handoff_schema)
      _invalid("Cross-media Review requires Storyboard v2 review handoff")
  }

  private def _visual_page_json(value: VisualPage): Json =
    Json.obj(
      "sceneId" -> Json.fromString(value.sceneid),
      "pageId" -> Json.fromString(value.pageid),
      "assets" -> Json.fromValues(value.assets.map(asset => Json.obj("id" -> Json.fromString(asset.id), "mediaType" -> Json.fromString(asset.mediaType))))
    )

  private def _material_path(root: Path, reference: String, label: String): Path =
    try CozyVisualPage._safe_direct_file(root, reference, label) catch {
      case fault: CozyVisualPage.VisualPageFault => _invalid(s"$label is invalid: ${fault.reason}")
    }

  private def _without_visual_page_proofs(value: JsonObject): JsonObject =
    value.filterKeys(name => !Set("visualPageSetIdentity", "catalogIdentity", "logicalIdentity", "visualPageIdentity", "bindingIdentity", "visualPageSet", "catalogValue", "logical", "visualPage", "binding").contains(name))

  private def _validate_record(value: Json, current: Json): Unit = {
    val record = _without_visual_page_proofs(_object(value, "Cross-media Review")).filterKeys(name => !Set("catalog", "presentationReviewIdentity", "storyboardEvidenceIdentity", "storyboardHandoffIdentity", "storyboardIdentity").contains(name))
    _exact_ordered_keys(record, Vector("schema", "target", "slides", "visualPages", "verification"), "Cross-media Review")
    if (_string(record, "schema", "Cross-media Review") != _schema || record("target") != current.hcursor.downField("target").focus)
      _invalid("Cross-media Review schema or target is invalid")
    val slides = record("slides").flatMap(_.asArray).getOrElse(_invalid("Cross-media Review slides must be an array")).map { value =>
      val slide = _object(value, "Cross-media Review slide")
      _exact_ordered_keys(slide.remove("sha256"), Vector("id"), "Cross-media Review slide")
      _string(slide, "id", "Cross-media Review slide")
    }
    val currentids = current.hcursor.downField("slides").focus.flatMap(_.asArray).get.map(_.hcursor.get[String]("id").toOption.get).toSet
    if (slides.distinct.size != slides.size || slides.exists(id => !currentids.contains(id)))
      _invalid("Cross-media Review slide references must be unique and resolve")
    val scenes = record("visualPages").flatMap(_.asArray).getOrElse(_invalid("Cross-media Review visualPages must be an array")).map { value =>
      val page = _without_visual_page_proofs(_object(value, "Cross-media Review visual page"))
      _exact_ordered_keys(page, Vector("sceneId", "pageId", "assets"), "Cross-media Review visual page")
      val pageid = _string(page, "pageId", "Cross-media Review visual page")
      if (!currentids.contains(pageid)) _invalid("Cross-media Review pageId does not resolve")
      val currentassets = current.hcursor.downField("visualPages").focus.flatMap(_.asArray).get.filter(_.hcursor.get[String]("pageId").toOption.contains(pageid)).flatMap(_.hcursor.downField("assets").focus.flatMap(_.asArray).toVector.flatten).flatMap(_.hcursor.get[String]("id").toOption).toSet
      val assets = page("assets").flatMap(_.asArray).getOrElse(_invalid("Cross-media Review assets must be an array")).map { value =>
        val asset = _object(value, "Cross-media Review asset")
        _exact_ordered_keys(asset, Vector("id", "mediaType"), "Cross-media Review asset")
        _string(asset, "mediaType", "Cross-media Review asset")
        _string(asset, "id", "Cross-media Review asset")
      }
      if (assets.distinct.size != assets.size || assets.exists(id => !currentassets.contains(id))) _invalid("Cross-media Review asset IDs must be unique and resolve")
      _string(page, "sceneId", "Cross-media Review visual page")
    }
    val currentsequences = current.hcursor.downField("visualPages").focus.flatMap(_.asArray).get.map(_.hcursor.get[String]("sceneId").toOption.get).toSet
    if (scenes.isEmpty || scenes.distinct.size != scenes.size || scenes.exists(id => !currentsequences.contains(id)))
      _invalid("Cross-media Review scene references must be unique and resolve")
    val verification = _object(record("verification").getOrElse(_invalid("Cross-media Review verification is missing")), "Cross-media Review verification")
    _exact_ordered_keys(verification, Vector("status", "semanticApproval", "visualApproval", "audiovisualApproval"), "Cross-media Review verification")
    if (_string(verification, "status", "Cross-media Review verification") != "valid" || Vector("semanticApproval", "visualApproval", "audiovisualApproval").exists(name => _string(verification, name, "Cross-media Review verification") != "not-recorded"))
      _invalid("Cross-media Review verification values are invalid")
  }

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
    val temporary = Files.createTempFile(parent, ".cozy-cross-review-", ".json")
    try {
      Files.writeString(temporary, value.noSpaces + "\n", StandardCharsets.UTF_8)
      Files.move(temporary, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
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
    val text = Files.readString(path, StandardCharsets.UTF_8)
    _require_no_duplicate_json_fields(text, label)
    parse(text).fold(_ => _invalid(s"$label must be JSON"), identity)
  }

  private def _require_no_duplicate_json_fields(text: String, label: String): Unit = {
    val input = new JsonFactory().enable(JacksonParser.Feature.STRICT_DUPLICATE_DETECTION).createParser(text)
    try {
      try while (input.nextToken() != null) {}
      catch {
        case error: JsonParseException if Option(error.getOriginalMessage).exists(_.contains("Duplicate field")) =>
          _invalid(s"$label contains duplicate JSON object field: ${error.getOriginalMessage}")
        case _: JsonParseException =>
          _invalid(s"$label must be JSON")
      }
    } finally input.close()
  }

  private def _relative(root: Path, path: Path): String = root.relativize(path).toString.replace('\\', '/')
  private def _object(value: Json, label: String): JsonObject = value.asObject.getOrElse(_invalid(s"$label must be an object"))
  private def _exact_ordered_keys(value: JsonObject, expected: Vector[String], label: String): Unit = if (value.keys.toVector != expected) _invalid(s"$label requires exactly ordered fields: ${expected.mkString(", ")}")
  private def _string(value: JsonObject, field: String, label: String): String = value(field).flatMap(_.asString).filter(value => value.nonEmpty && value == value.trim).getOrElse(_invalid(s"$label.$field must be a non-empty exact string"))
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
