package cozy.media

import org.goldenport.RAISE
import org.goldenport.config.StructuredDocumentLoader
import org.goldenport.io.InputSource
import io.circe.{Decoder, HCursor, Json, JsonObject}
import io.circe.parser.parse
import java.io.ByteArrayInputStream
import java.nio.charset.StandardCharsets
import java.nio.file.{Files, LinkOption, Path}
import java.security.MessageDigest
import java.util.zip.ZipFile
import javax.xml.XMLConstants
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.{Document, Node}
import org.xml.sax.{EntityResolver, InputSource => SaxInputSource, SAXException}
import scala.collection.JavaConverters._
import scala.util.control.NonFatal

/*
 * @since   Aug. 25, 2026
 * @version Aug. 25, 2026
 * @author  ASAMI, Tomoharu
 */
private[cozy] object CozyMediaPresentation {
  final case class RendererConfig(name: String, version: String, command: Vector[String])
  object RendererConfig {
    implicit val decoder: Decoder[RendererConfig] = (c: HCursor) =>
      for {
        _ <- _require_exact_keys(c, Set("name", "version", "command"), "presentation renderer")
        name <- c.downField("name").as[String]
        version <- c.downField("version").as[String]
        command <- c.downField("command").as[Vector[String]]
      } yield RendererConfig(name, version, command)
  }

  final case class ProfileConfig(template: String, renderer: RendererConfig)
  object ProfileConfig {
    implicit val decoder: Decoder[ProfileConfig] = (c: HCursor) =>
      for {
        _ <- _require_exact_keys(c, Set("template", "renderer"), "presentation profile")
        template <- c.downField("template").as[String]
        renderer <- c.downField("renderer").as[RendererConfig]
      } yield ProfileConfig(template, renderer)
  }

  final case class ResourceConfig(
    contract: Option[String],
    profile: String,
    catalog: Option[String],
    binding: Option[String],
    slideImages: String,
    montage: String,
    rendererManifest: String,
    reviewManifest: String,
    reviewState: String,
    articlePdf: String,
    infographic: String
  )
  object ResourceConfig {
    private val _legacy_fields = Set("profile", "slideImages", "montage", "rendererManifest", "reviewManifest", "reviewState", "articlePdf", "infographic")
    private val _visual_page_fields = Set("contract", "profile", "catalog", "binding", "slideImages", "montage", "rendererManifest", "reviewManifest", "reviewState", "articlePdf", "infographic")

    implicit val decoder: Decoder[ResourceConfig] = (c: HCursor) =>
      c.value.asObject match {
        case Some(value) if value.keys.toSet == _legacy_fields =>
          _decode(c, None, None, None)
        case Some(value) if value.keys.toSet == _visual_page_fields =>
          for {
            contract <- c.downField("contract").as[String]
            catalog <- c.downField("catalog").as[String]
            binding <- c.downField("binding").as[String]
            decoded <- _decode(c, Some(contract), Some(catalog), Some(binding))
          } yield decoded
        case Some(_) =>
          Left(io.circe.DecodingFailure("presentation resource requires one closed legacy or visual-page-v1 grammar", c.history))
        case None =>
          Left(io.circe.DecodingFailure("presentation resource must be an object", c.history))
      }

    private def _decode(c: HCursor, contract: Option[String], catalog: Option[String], binding: Option[String]): Decoder.Result[ResourceConfig] =
      for {
        profile <- c.downField("profile").as[String]
        slideimages <- c.downField("slideImages").as[String]
        montage <- c.downField("montage").as[String]
        renderermanifest <- c.downField("rendererManifest").as[String]
        reviewmanifest <- c.downField("reviewManifest").as[String]
        reviewstate <- c.downField("reviewState").as[String]
        articlepdf <- c.downField("articlePdf").as[String]
        infographic <- c.downField("infographic").as[String]
      } yield ResourceConfig(contract, profile, catalog, binding, slideimages, montage, renderermanifest, reviewmanifest, reviewstate, articlepdf, infographic)
  }

  final case class VisualPageAssetEvidence(id: String, sha256: String)
  final case class VisualPagePageEvidence(
    id: String,
    logicalIdentity: String,
    visualPageIdentity: String,
    assets: Vector[VisualPageAssetEvidence]
  )
  final case class VisualPageEvidence(
    reviewPath: Path,
    visualPageSetIdentity: String,
    catalogIdentity: String,
    bindingIdentity: String,
    pages: Vector[VisualPagePageEvidence]
  )

  private val _renderer_schema = "cozy.presentation.render.v1"
  private val _review_schema = "cozy.media.presentation-review.v1"
  private val _visual_page_contract = "visual-page-v1"
  private val _visual_page_renderer_schema = "cozy.presentation.render.v2"
  private val _visual_page_review_schema = "cozy.media.presentation-review.v2"

  def validateDescriptor(descriptor: CozyMedia.Descriptor, profiles: Map[String, CozyMedia.EffectiveProfile], root: Path): Unit = {
    descriptor.resources.foreach { resource =>
      if (resource.build == "presentation") {
        val language = resource.language.getOrElse(_invalid(s"Presentation resource requires language: ${resource.id}"))
        _identity(language, s"Presentation resource ${resource.id} language")
        if (resource.kind != "presentation" || resource.source.isEmpty || resource.output.isEmpty || resource.presentation.isEmpty)
          _invalid(s"Presentation resource requires kind, language, source, output, and presentation configuration: ${resource.id}")
        val configuration = resource.presentation.get
        _validate_resource_config(configuration, resource.id)
        if (configuration.profile != "business")
          _invalid(s"Presentation resource profile must be exactly business: ${resource.id}")
        val profile = descriptor.profiles.get(configuration.profile).flatMap(_.presentation).getOrElse(
          _invalid(s"Presentation resource has no configured presentation profile: ${resource.id}")
        )
        val template = _descriptor_path(root, profile.template, s"Presentation template ${resource.id}")
        if (!_direct_regular_file(template))
          _invalid(s"Presentation template must be a direct regular non-symlink file: $template")
        if (!resource.publications.contains(configuration.profile))
          _invalid(s"Presentation resource requires publication for presentation profile: ${resource.id}")
        _validate_renderer(profile.renderer, s"Presentation profile ${configuration.profile}")
        val article = descriptor.resources.find(_.id == configuration.articlePdf).getOrElse(_invalid(s"Presentation article PDF dependency is not declared: ${resource.id}"))
        val infographic = descriptor.resources.find(_.id == configuration.infographic).getOrElse(_invalid(s"Presentation infographic dependency is not declared: ${resource.id}"))
        if (article.id == resource.id || infographic.id == resource.id || article.build == "presentation" || infographic.build == "presentation")
          _invalid(s"Presentation dependencies must be non-presentation resources: ${resource.id}")
        if (!Set("document", "pdf").contains(article.kind) || article.output.isEmpty || !article.publications.contains(configuration.profile))
          _invalid(s"Presentation articlePdf dependency must be a published document/PDF resource: ${resource.id}")
        if (!Set("image", "infographic").contains(infographic.kind))
          _invalid(s"Presentation infographic dependency must be an image/infographic resource: ${resource.id}")
        if (infographic.language.exists(_ != resource.language.get))
          _invalid(s"Presentation infographic dependency language must match: ${resource.id}")
        if (_is_visual_page(configuration))
          _visual_page_descriptor(root, descriptor, resource)
        _validate_receipt(descriptor, resource, profile, root)
      } else if (resource.presentation.nonEmpty) {
        _invalid(s"Only presentation build resources may declare presentation configuration: ${resource.id}")
      }
    }
    val generated = descriptor.resources.filter(_.build == "presentation").flatMap { resource =>
      val configuration = resource.presentation.get
      Vector(
        resource.output.map(value => _descriptor_path(root, value, s"Presentation output ${resource.id}")),
        Some(_descriptor_path(root, configuration.slideImages, s"Presentation slideImages ${resource.id}")),
        Some(_descriptor_path(root, configuration.montage, s"Presentation montage ${resource.id}")),
        Some(_descriptor_path(root, configuration.rendererManifest, s"Presentation rendererManifest ${resource.id}")),
        Some(_descriptor_path(root, configuration.reviewManifest, s"Presentation reviewManifest ${resource.id}")),
        Some(_descriptor_path(root, configuration.reviewState, s"Presentation reviewState ${resource.id}"))
      ).flatten.map(path => resource.id -> path)
    }
    _reject_path_overlap(generated, "Presentation generated paths")
    descriptor.resources.filter(_.build == "presentation").foreach { resource =>
      val configuration = resource.presentation.get
      val owned = generated.collect { case (id, path) if id == resource.id => path }
      val profile = descriptor.profiles.get(configuration.profile).flatMap(_.presentation).getOrElse(_invalid(s"Presentation profile is missing: ${resource.id}"))
      val dependencies = Vector(resource.source, Some(profile.template)).flatten.map(value => _descriptor_path(root, value, s"Presentation dependency ${resource.id}")) ++
        descriptor.resources.filter(value => value.id == configuration.articlePdf || value.id == configuration.infographic).flatMap(_.output).map(value => _descriptor_path(root, value, s"Presentation dependency ${resource.id}"))
      owned.foreach { generatedpath => dependencies.foreach { dependency => if (_overlap(generatedpath, dependency)) _invalid(s"Presentation generated path overlaps dependency: ${resource.id}") } }
    }
  }

  def build(plan: CozyMedia.Plan, resolved: CozyMedia.ResolvedResource, runner: CozyMedia.ProcessRunner): String = {
    if (_is_visual_page(_configuration(resolved)))
      _build_visual_page(plan, resolved, runner)
    else
      _build_legacy(plan, resolved, runner)
  }

  private def _build_legacy(plan: CozyMedia.Plan, resolved: CozyMedia.ResolvedResource, runner: CozyMedia.ProcessRunner): String = {
    val configuration = _configuration(resolved)
    val profile = _profile(plan, configuration, resolved)
    val source = resolved.source.getOrElse(_invalid(s"Presentation source is missing: ${resolved.resource.id}"))
    val output = resolved.output.getOrElse(_invalid(s"Presentation output is missing: ${resolved.resource.id}"))
    val template = _path(plan, profile.template, s"Presentation template ${resolved.resource.id}")
    if (!_direct_regular_file(template)) _invalid(s"Presentation template must be a direct regular non-symlink file: $template")
    CozyMediaSlideIr.validate(plan, resolved, requireAssets = true)
    val images = _path(plan, configuration.slideImages, s"Presentation slideImages ${resolved.resource.id}")
    val montage = _path(plan, configuration.montage, s"Presentation montage ${resolved.resource.id}")
    val manifest = _path(plan, configuration.rendererManifest, s"Presentation rendererManifest ${resolved.resource.id}")
    Option(output.getParent).foreach(Files.createDirectories(_))
    Files.createDirectories(images)
    Option(montage.getParent).foreach(Files.createDirectories(_))
    Option(manifest.getParent).foreach(Files.createDirectories(_))
    val command = profile.renderer.command ++ Vector(
      "render", "--media", plan.descriptorFile.toString, "--target", resolved.resource.id,
      "--profile", configuration.profile, "--slide-ir", source.toString, "--template", template.toString,
      "--pptx", output.toString, "--slide-images", images.toString, "--montage", montage.toString,
      "--manifest", manifest.toString
    )
    val exit = runner.run(command, plan.descriptorRoot)
    if (exit != 0) _invalid(s"Presentation renderer failed for ${resolved.resource.id}: exit=$exit")
    _artifacts(plan, resolved, requirecurrentassets = true)
    writeReviewManifest(plan, resolved)
    s"${resolved.resource.id}: rendered $output"
  }

  private final case class VisualPageInput(
    source: Path,
    catalog: Path,
    bindingpath: Path,
    document: CozyVisualPage.ValidatedDocument,
    binding: CozyVisualPageBinding.ValidatedBinding
  )

  private def _build_visual_page(plan: CozyMedia.Plan, resolved: CozyMedia.ResolvedResource, runner: CozyMedia.ProcessRunner): String = {
    val configuration = _configuration(resolved)
    val profile = _profile(plan, configuration, resolved)
    val visual = _visual_page_input(plan, resolved)
    val output = resolved.output.getOrElse(_invalid(s"Presentation output is missing: ${resolved.resource.id}"))
    val template = _path(plan, profile.template, s"Presentation template ${resolved.resource.id}")
    if (!_direct_regular_file(template)) _invalid(s"Presentation template must be a direct regular non-symlink file: $template")
    val images = _path(plan, configuration.slideImages, s"Presentation slideImages ${resolved.resource.id}")
    val montage = _path(plan, configuration.montage, s"Presentation montage ${resolved.resource.id}")
    val manifest = _path(plan, configuration.rendererManifest, s"Presentation rendererManifest ${resolved.resource.id}")
    Option(output.getParent).foreach(Files.createDirectories(_))
    Files.createDirectories(images)
    Option(montage.getParent).foreach(Files.createDirectories(_))
    Option(manifest.getParent).foreach(Files.createDirectories(_))
    val command = profile.renderer.command ++ Vector(
      "render", "--media", plan.descriptorFile.toString, "--target", resolved.resource.id,
      "--profile", configuration.profile, "--visual-page-set", visual.source.toString,
      "--catalog", visual.catalog.toString, "--binding", visual.bindingpath.toString,
      "--template", template.toString, "--pptx", output.toString,
      "--slide-images", images.toString, "--montage", montage.toString, "--manifest", manifest.toString
    )
    val exit = runner.run(command, plan.descriptorRoot)
    if (exit != 0) _invalid(s"Presentation renderer failed for ${resolved.resource.id}: exit=$exit")
    _visual_page_artifacts(plan, resolved, visual, requirecurrentassets = true)
    _write_visual_page_review_manifest(plan, resolved)
    s"${resolved.resource.id}: rendered $output"
  }

  private def _visual_page_descriptor(root: Path, descriptor: CozyMedia.Descriptor, resource: CozyMedia.Resource): Unit = {
    val configuration = resource.presentation.getOrElse(_invalid(s"Presentation resource requires configuration: ${resource.id}"))
    val source = _direct_descriptor_input(root, resource.source.getOrElse(_invalid(s"Presentation source is missing: ${resource.id}")), s"Presentation VisualPageSet ${resource.id}")
    val catalog = _direct_descriptor_input(root, configuration.catalog.getOrElse(_invalid(s"Presentation VisualPage catalog is missing: ${resource.id}")), s"Presentation VisualPage catalog ${resource.id}")
    val binding = _direct_descriptor_input(root, configuration.binding.getOrElse(_invalid(s"Presentation VisualPage binding is missing: ${resource.id}")), s"Presentation VisualPage binding ${resource.id}")
    val document = _load_visual_page_set(source, catalog, resource, descriptor)
    val resolvedbinding = CozyVisualPageBinding.load(binding, document)
    if (resolvedbinding.profile != configuration.profile)
      _invalid(s"Presentation VisualPage binding profile differs: ${resource.id}")
  }

  private def _visual_page_input(plan: CozyMedia.Plan, resolved: CozyMedia.ResolvedResource): VisualPageInput = {
    val configuration = _configuration(resolved)
    val source = _direct_descriptor_input(plan.descriptorRoot, resolved.resource.source.getOrElse(_invalid(s"Presentation source is missing: ${resolved.resource.id}")), s"Presentation VisualPageSet ${resolved.resource.id}")
    val catalog = _direct_descriptor_input(plan.descriptorRoot, configuration.catalog.getOrElse(_invalid(s"Presentation VisualPage catalog is missing: ${resolved.resource.id}")), s"Presentation VisualPage catalog ${resolved.resource.id}")
    val bindingpath = _direct_descriptor_input(plan.descriptorRoot, configuration.binding.getOrElse(_invalid(s"Presentation VisualPage binding is missing: ${resolved.resource.id}")), s"Presentation VisualPage binding ${resolved.resource.id}")
    val document = _load_visual_page_set(source, catalog, resolved.resource, plan.descriptor)
    val binding = CozyVisualPageBinding.load(bindingpath, document)
    if (binding.profile != configuration.profile)
      _invalid(s"Presentation VisualPage binding profile differs: ${resolved.resource.id}")
    VisualPageInput(source, catalog, bindingpath, document, binding)
  }

  private def _load_visual_page_set(source: Path, catalog: Path, resource: CozyMedia.Resource, descriptor: CozyMedia.Descriptor): CozyVisualPage.ValidatedDocument = {
    val document = CozyVisualPage.load(source, catalog)
    document.document match {
      case _: CozyVisualPage.PageSet => ()
      case _ => _invalid(s"Presentation VisualPage source must be cozy.visual-page-set.v1: ${resource.id}")
    }
    val language = resource.language.getOrElse(_invalid(s"Presentation resource requires language: ${resource.id}"))
    if (document.document.pages.exists(page => page.knowledge != descriptor.knowledge.id || page.language != language))
      _invalid(s"Presentation VisualPageSet knowledge or language differs: ${resource.id}")
    document
  }

  def artifacts(plan: CozyMedia.Plan, resolved: CozyMedia.ResolvedResource): Vector[CozyMediaReceipt.Artifact] =
    _artifacts(plan, resolved, requirecurrentassets = false)

  def visualPageEvidence(plan: CozyMedia.Plan, resolved: CozyMedia.ResolvedResource): VisualPageEvidence = {
    val configuration = _configuration(resolved)
    if (!_is_visual_page(configuration))
      _invalid(s"Cross-media Review requires a visual-page-v1 presentation resource: ${resolved.resource.id}")
    val visual = _visual_page_input(plan, resolved)
    val logicalidentities = visual.document.logicalIdentities.toMap
    val visualidentities = visual.document.visualPageIdentities.toMap
    val pages = visual.document.document.pages.map { page =>
      VisualPagePageEvidence(
        page.id,
        logicalidentities.getOrElse(page.id, _invalid(s"Presentation VisualPage logical identity is missing: ${page.id}")),
        visualidentities.getOrElse(page.id, _invalid(s"Presentation VisualPage identity is missing: ${page.id}")),
        page.assets.map(asset => VisualPageAssetEvidence(asset.id, asset.sha256))
      )
    }
    VisualPageEvidence(
      _path(plan, configuration.reviewManifest, s"Presentation VisualPage reviewManifest ${resolved.resource.id}"),
      visual.document.documentIdentity,
      visual.document.catalogIdentity,
      visual.binding.bindingIdentity,
      pages
    )
  }

  def currentArtifactEvidence(plan: CozyMedia.Plan, resolved: CozyMedia.ResolvedResource, entry: CozyMediaReceipt.ManifestEntry): Boolean =
    try {
      val actual = artifacts(plan, resolved)
      entry.artifacts.nonEmpty && actual == entry.artifacts
    } catch { case NonFatal(_) => false }

  def verifyStructural(plan: CozyMedia.Plan, resolved: CozyMedia.ResolvedResource): Unit = {
    _artifacts(plan, resolved, requirecurrentassets = false)
    _verify_review_manifest(plan, resolved)
  }

  def requireCurrent(plan: CozyMedia.Plan, resolved: CozyMedia.ResolvedResource, requireReviewState: Boolean): Unit = {
    if (!CozyMediaReceipt.current(plan, resolved))
      _invalid(s"Presentation resource lacks current receipt evidence: ${resolved.resource.id}")
    _verify_review_manifest(plan, resolved)
    val configuration = _configuration(resolved)
    val article = _dependency(plan, configuration.articlePdf, resolved)
    val infographic = _dependency(plan, configuration.infographic, resolved)
    val presentationReceipt = _receipt_entry(plan, resolved).receipt.getOrElse(_invalid("Presentation receipt is missing"))
    Vector(article, infographic).foreach { dependency =>
      if (!CozyMediaReceipt.current(plan, dependency)) _invalid(s"Presentation dependency lacks current receipt evidence: ${dependency.resource.id}")
      val receipt = _receipt_entry(plan, dependency).receipt.getOrElse(_invalid("Presentation dependency receipt is missing"))
      if (receipt.inputSetSha256 != presentationReceipt.inputSetSha256)
        _invalid(s"Presentation dependency receipt generation differs: ${dependency.resource.id}")
    }
    if (requireReviewState) CozyMediaReviewState.requireAligned(plan, resolved)
  }

  def requireDependenciesCurrent(plan: CozyMedia.Plan, resolved: CozyMedia.ResolvedResource): Unit = {
    val configuration = _configuration(resolved)
    Vector(_dependency(plan, configuration.articlePdf, resolved), _dependency(plan, configuration.infographic, resolved)).foreach { dependency =>
      if (!CozyMediaReceipt.current(plan, dependency))
        _invalid(s"Presentation target requires current dependency evidence: ${dependency.resource.id}")
    }
  }

  def writeReviewManifest(plan: CozyMedia.Plan, resolved: CozyMedia.ResolvedResource): Unit = {
    if (_is_visual_page(_configuration(resolved))) {
      _write_visual_page_review_manifest(plan, resolved)
      return
    }
    val configuration = _configuration(resolved)
    val document = CozyMediaSlideIr.validate(plan, resolved, requireAssets = true)
    val output = resolved.output.getOrElse(_invalid("Presentation output is missing"))
    val template = _template(plan, resolved)
    val artifacts = _artifacts(plan, resolved, requirecurrentassets = true)
    val renderer = _profile(plan, configuration, resolved).renderer
    val article = _dependency(plan, configuration.articlePdf, resolved)
    val infographic = _dependency(plan, configuration.infographic, resolved)
    val path = _path(plan, configuration.reviewManifest, s"Presentation reviewManifest ${resolved.resource.id}")
    val json = _review_json(plan, resolved, document, output, template, artifacts, renderer, article, infographic)
    Option(path.getParent).foreach(Files.createDirectories(_))
    Files.writeString(path, json.spaces2 + "\n", StandardCharsets.UTF_8)
  }

  private def _review_json(plan: CozyMedia.Plan, resolved: CozyMedia.ResolvedResource, document: CozyMediaSlideIr.Document, output: Path, template: Path, artifacts: Vector[CozyMediaReceipt.Artifact], renderer: RendererConfig, article: CozyMedia.ResolvedResource, infographic: CozyMedia.ResolvedResource): Json =
    Json.obj(
      "schema" -> Json.fromString(_review_schema),
      "target" -> Json.fromString(resolved.resource.id),
      "knowledge" -> Json.fromString(plan.descriptor.knowledge.id),
      "language" -> Json.fromString(resolved.resource.language.get),
      "inputSetSha256" -> Json.fromString(CozyMediaReceipt.capture(plan).inputSetSha256),
      "renderer" -> Json.obj("name" -> Json.fromString(renderer.name), "version" -> Json.fromString(renderer.version)),
      "slideIr" -> _identity_json(plan, resolved.source.get),
      "template" -> _identity_json(plan, template),
      "pptx" -> _identity_json(plan, output),
      "slides" -> Json.fromValues(document.slides.map(slide => Json.obj("id" -> Json.fromString(slide.id), "title" -> Json.fromString(slide.title), "path" -> Json.fromString(_artifact(artifacts, s"slide:${slide.id}").path), "sha256" -> Json.fromString(_artifact(artifacts, s"slide:${slide.id}").sha256)))),
      "assets" -> Json.fromValues(document.assetIds.sorted.map(id => Json.obj("id" -> Json.fromString(id), "sha256" -> Json.fromString(_sha256(_asset_path(plan, id)))))),
      "montage" -> _artifact_json(_artifact(artifacts, "montage")),
      "articlePdf" -> Json.obj("id" -> Json.fromString(article.resource.id), "sha256" -> Json.fromString(_sha256(_output(article)))),
      "infographic" -> Json.obj("id" -> Json.fromString(infographic.resource.id), "sha256" -> Json.fromString(_sha256(_output(infographic)))),
      "verification" -> Json.obj("status" -> Json.fromString("valid"), "findings" -> Json.arr())
    )

  private def _write_visual_page_review_manifest(plan: CozyMedia.Plan, resolved: CozyMedia.ResolvedResource): Unit = {
    val configuration = _configuration(resolved)
    val visual = _visual_page_input(plan, resolved)
    val output = resolved.output.getOrElse(_invalid("Presentation output is missing"))
    val template = _template(plan, resolved)
    val artifacts = _visual_page_artifacts(plan, resolved, visual, requirecurrentassets = true)
    val renderer = _profile(plan, configuration, resolved).renderer
    val article = _dependency(plan, configuration.articlePdf, resolved)
    val infographic = _dependency(plan, configuration.infographic, resolved)
    val path = _path(plan, configuration.reviewManifest, s"Presentation reviewManifest ${resolved.resource.id}")
    val json = _visual_page_review_json(plan, resolved, visual, output, template, artifacts, renderer, article, infographic)
    Option(path.getParent).foreach(Files.createDirectories(_))
    Files.writeString(path, json.spaces2 + "\n", StandardCharsets.UTF_8)
  }

  private def _visual_page_review_json(plan: CozyMedia.Plan, resolved: CozyMedia.ResolvedResource, visual: VisualPageInput, output: Path, template: Path, artifacts: Vector[CozyMediaReceipt.Artifact], renderer: RendererConfig, article: CozyMedia.ResolvedResource, infographic: CozyMedia.ResolvedResource): Json =
    Json.obj(
      "schema" -> Json.fromString(_visual_page_review_schema),
      "target" -> Json.fromString(resolved.resource.id),
      "knowledge" -> Json.fromString(plan.descriptor.knowledge.id),
      "language" -> Json.fromString(resolved.resource.language.get),
      "inputSetSha256" -> Json.fromString(CozyMediaReceipt.capture(plan).inputSetSha256),
      "renderer" -> Json.obj("name" -> Json.fromString(renderer.name), "version" -> Json.fromString(renderer.version)),
      "visualPageSet" -> _semantic_identity_json(plan, visual.source, visual.document.documentIdentity),
      "catalog" -> _semantic_identity_json(plan, visual.catalog, visual.document.catalogIdentity),
      "binding" -> _semantic_identity_json(plan, visual.bindingpath, visual.binding.bindingIdentity),
      "template" -> _identity_json(plan, template),
      "pptx" -> _identity_json(plan, output),
      "slides" -> Json.fromValues(visual.document.document.pages.map(page => Json.obj(
        "id" -> Json.fromString(page.id),
        "path" -> Json.fromString(_artifact(artifacts, s"slide:${page.id}").path),
        "sha256" -> Json.fromString(_artifact(artifacts, s"slide:${page.id}").sha256)
      ))),
      "assets" -> Json.fromValues(_visual_assets(visual).map(asset => Json.obj("id" -> Json.fromString(asset.id), "sha256" -> Json.fromString(asset.sha256)))),
      "montage" -> _artifact_json(_artifact(artifacts, "montage")),
      "articlePdf" -> Json.obj("id" -> Json.fromString(article.resource.id), "sha256" -> Json.fromString(_sha256(_output(article)))),
      "infographic" -> Json.obj("id" -> Json.fromString(infographic.resource.id), "sha256" -> Json.fromString(_sha256(_output(infographic)))),
      "verification" -> Json.obj("status" -> Json.fromString("valid"), "findings" -> Json.arr())
    )

  def reviewSnapshot(plan: CozyMedia.Plan, resolved: CozyMedia.ResolvedResource, acceptedAt: String): CozyMediaReviewState.Snapshot = {
    _verify_review_manifest(plan, resolved)
    val configuration = _configuration(resolved)
    val review = _path(plan, configuration.reviewManifest, "Presentation review manifest")
    val artifactvalues = artifacts(plan, resolved)
    val artifactset = Json.fromValues(artifactvalues.sortBy(_.id).map(_artifact_json)).noSpaces.getBytes(StandardCharsets.UTF_8)
    CozyMediaReviewState.Snapshot(CozyMediaReceipt.capture(plan).inputSetSha256, _sha256(review), _sha256_bytes(artifactset), acceptedAt)
  }

  private def _artifacts(plan: CozyMedia.Plan, resolved: CozyMedia.ResolvedResource, requirecurrentassets: Boolean): Vector[CozyMediaReceipt.Artifact] = {
    if (_is_visual_page(_configuration(resolved)))
      return _visual_page_artifacts(plan, resolved, _visual_page_input(plan, resolved), requirecurrentassets)
    val configuration = _configuration(resolved)
    val document = CozyMediaSlideIr.validate(plan, resolved, requireAssets = requirecurrentassets)
    val output = resolved.output.getOrElse(_invalid(s"Presentation output is missing: ${resolved.resource.id}"))
    if (!_pptx(output)) _invalid(s"Presentation output is not a valid PPTX package: $output")
    val manifest = _path(plan, configuration.rendererManifest, s"Presentation rendererManifest ${resolved.resource.id}")
    if (!_direct_regular_file(manifest)) _invalid(s"Presentation renderer manifest is missing: $manifest")
    val renderer = _renderer_manifest(manifest)
    val template = _template(plan, resolved)
    _verify_renderer_manifest(plan, resolved, document, template, output, renderer)
    val pptxhash = _sha256(output)
    val imagepath = _path(plan, configuration.slideImages, s"Presentation slideImages ${resolved.resource.id}")
    val slides = renderer("slides").flatMap(_.asArray).get.toVector.map { value =>
      val objectvalue = _object(value, "Presentation renderer slide")
      val id = _string(objectvalue, "id", "Presentation renderer slide")
      val path = _serialized_path(plan, _string(objectvalue, "path", "Presentation renderer slide"), "Presentation renderer slide path")
      if (!path.startsWith(imagepath) || path == imagepath || !_png(path)) _invalid(s"Presentation slide image is invalid: $path")
      val hash = _sha256(path)
      if (hash != _sha256_value(objectvalue, "sha256", "Presentation renderer slide")) _invalid(s"Presentation slide image hash differs: $id")
      CozyMediaReceipt.Artifact(s"slide:$id", "slide-image", _relative(plan, path), hash, Some(pptxhash))
    }
    val montagevalue = _object(renderer("montage").get, "Presentation renderer montage")
    val montage = _serialized_path(plan, _string(montagevalue, "path", "Presentation renderer montage"), "Presentation montage path")
    if (!_png(montage)) _invalid(s"Presentation montage is invalid: $montage")
    val montagehash = _sha256(montage)
    if (montagehash != _sha256_value(montagevalue, "sha256", "Presentation renderer montage")) _invalid("Presentation montage hash differs")
    val review = _path(plan, configuration.reviewManifest, s"Presentation reviewManifest ${resolved.resource.id}")
    val ids = slides.map(_.id)
    if (ids.distinct.size != ids.size || ids.exists(id => Set("montage", "renderer-manifest", "review-manifest").contains(id))) _invalid("Presentation artifact ids must be collision-free")
    val paths = slides.map(_.path) ++ Vector(_relative(plan, montage), _relative(plan, manifest)) ++ (if (_direct_regular_file(review)) Vector(_relative(plan, review)) else Vector.empty)
    if (paths.distinct.size != paths.size) _invalid("Presentation artifact paths must be unique")
    val base = slides ++ Vector(
      CozyMediaReceipt.Artifact("montage", "montage", _relative(plan, montage), montagehash, Some(pptxhash)),
      CozyMediaReceipt.Artifact("renderer-manifest", "renderer-manifest", _relative(plan, manifest), _sha256(manifest), None)
    )
    if (_direct_regular_file(review)) base :+ CozyMediaReceipt.Artifact("review-manifest", "review-manifest", _relative(plan, review), _sha256(review), None) else base
  }

  private def _visual_page_artifacts(plan: CozyMedia.Plan, resolved: CozyMedia.ResolvedResource, visual: VisualPageInput, requirecurrentassets: Boolean): Vector[CozyMediaReceipt.Artifact] = {
    val configuration = _configuration(resolved)
    val output = resolved.output.getOrElse(_invalid(s"Presentation output is missing: ${resolved.resource.id}"))
    if (!_pptx(output)) _invalid(s"Presentation output is not a valid PPTX package: $output")
    val manifest = _path(plan, configuration.rendererManifest, s"Presentation rendererManifest ${resolved.resource.id}")
    if (!_direct_regular_file(manifest)) _invalid(s"Presentation renderer manifest is missing: $manifest")
    val renderer = _renderer_manifest(manifest)
    val template = _template(plan, resolved)
    _verify_visual_page_renderer_manifest(plan, resolved, visual, template, output, renderer)
    val pptxhash = _sha256(output)
    val imagepath = _path(plan, configuration.slideImages, s"Presentation slideImages ${resolved.resource.id}")
    val slides = renderer("slides").flatMap(_.asArray).getOrElse(_invalid("Presentation renderer slides must be an array")).toVector.map { value =>
      val objectvalue = _object(value, "Presentation renderer slide")
      val id = _string(objectvalue, "id", "Presentation renderer slide")
      val path = _serialized_path(plan, _string(objectvalue, "path", "Presentation renderer slide"), "Presentation renderer slide path")
      if (!path.startsWith(imagepath) || path == imagepath || !_png(path)) _invalid(s"Presentation slide image is invalid: $path")
      val hash = _sha256(path)
      if (hash != _sha256_value(objectvalue, "sha256", "Presentation renderer slide")) _invalid(s"Presentation slide image hash differs: $id")
      CozyMediaReceipt.Artifact(s"slide:$id", "slide-image", _relative(plan, path), hash, Some(pptxhash))
    }
    val montagevalue = _object(renderer("montage").getOrElse(_invalid("Presentation renderer montage is missing")), "Presentation renderer montage")
    val montage = _serialized_path(plan, _string(montagevalue, "path", "Presentation renderer montage"), "Presentation montage path")
    if (!_png(montage)) _invalid(s"Presentation montage is invalid: $montage")
    val montagehash = _sha256(montage)
    if (montagehash != _sha256_value(montagevalue, "sha256", "Presentation renderer montage")) _invalid("Presentation montage hash differs")
    val review = _path(plan, configuration.reviewManifest, s"Presentation reviewManifest ${resolved.resource.id}")
    val ids = slides.map(_.id)
    if (ids.distinct.size != ids.size || ids.exists(id => Set("montage", "renderer-manifest", "review-manifest").contains(id))) _invalid("Presentation artifact ids must be collision-free")
    val paths = slides.map(_.path) ++ Vector(_relative(plan, montage), _relative(plan, manifest)) ++ (if (_direct_regular_file(review)) Vector(_relative(plan, review)) else Vector.empty)
    if (paths.distinct.size != paths.size) _invalid("Presentation artifact paths must be unique")
    val base = slides ++ Vector(
      CozyMediaReceipt.Artifact("montage", "montage", _relative(plan, montage), montagehash, Some(pptxhash)),
      CozyMediaReceipt.Artifact("renderer-manifest", "renderer-manifest", _relative(plan, manifest), _sha256(manifest), None)
    )
    if (_direct_regular_file(review)) base :+ CozyMediaReceipt.Artifact("review-manifest", "review-manifest", _relative(plan, review), _sha256(review), None) else base
  }

  private def _verify_renderer_manifest(plan: CozyMedia.Plan, resolved: CozyMedia.ResolvedResource, document: CozyMediaSlideIr.Document, template: Path, output: Path, renderer: JsonObject): Unit = {
    _exact_keys(renderer, Set("schema", "target", "profile", "renderer", "slideIrSha256", "templateSha256", "pptx", "slides", "montage"), "Presentation renderer manifest")
    if (_string(renderer, "schema", "Presentation renderer manifest") != _renderer_schema || _string(renderer, "target", "Presentation renderer manifest") != resolved.resource.id || _string(renderer, "profile", "Presentation renderer manifest") != _configuration(resolved).profile)
      _invalid("Presentation renderer manifest identity differs from descriptor")
    val profile = _profile(plan, _configuration(resolved), resolved)
    val rendereridentity = _object(renderer("renderer").get, "Presentation renderer identity")
    _exact_keys(rendereridentity, Set("name", "version"), "Presentation renderer identity")
    if (_string(rendereridentity, "name", "Presentation renderer identity") != profile.renderer.name || _string(rendereridentity, "version", "Presentation renderer identity") != profile.renderer.version)
      _invalid("Presentation renderer identity differs from profile")
    if (_sha256_value(renderer, "slideIrSha256", "Presentation renderer manifest") != _sha256(resolved.source.get) || _sha256_value(renderer, "templateSha256", "Presentation renderer manifest") != _sha256(template))
      _invalid("Presentation renderer manifest input hashes differ")
    val pptx = _object(renderer("pptx").get, "Presentation renderer PPTX")
    _exact_keys(pptx, Set("path", "sha256"), "Presentation renderer PPTX")
    if (_serialized_path(plan, _string(pptx, "path", "Presentation renderer PPTX"), "Presentation PPTX path") != output || _sha256_value(pptx, "sha256", "Presentation renderer PPTX") != _sha256(output))
      _invalid("Presentation renderer PPTX differs from configured output")
    val slides = renderer("slides").flatMap(_.asArray).getOrElse(_invalid("Presentation renderer slides must be an array")).toVector
    val ids = slides.map(value => _string(_object(value, "Presentation renderer slide"), "id", "Presentation renderer slide"))
    if (ids != document.slides.map(_.id) || ids.distinct.size != ids.size) _invalid("Presentation renderer slide order or identities differ from slide IR")
    slides.zip(document.slides).foreach { case (value, slide) =>
      val entry = _object(value, "Presentation renderer slide")
      _exact_keys(entry, Set("id", "title", "path", "sha256", "pptxSha256", "assets"), "Presentation renderer slide")
      if (_string(entry, "title", "Presentation renderer slide") != slide.title || _sha256_value(entry, "pptxSha256", "Presentation renderer slide") != _sha256(output)) _invalid(s"Presentation renderer slide differs: ${slide.id}")
      val assets = entry("assets").flatMap(_.asArray).getOrElse(_invalid("Presentation renderer slide assets must be an array")).toVector
      val expected = slide.elements.flatMap(_.asset).distinct.sorted
      val actual = assets.map { item =>
        val asset = _object(item, "Presentation renderer asset")
        _exact_keys(asset, Set("id", "sha256"), "Presentation renderer asset")
        val id = _string(asset, "id", "Presentation renderer asset")
        if (_sha256_value(asset, "sha256", "Presentation renderer asset") != _sha256(_asset_path(plan, id))) _invalid(s"Presentation renderer asset hash differs: $id")
        id
      }.sorted
      if (actual != expected || actual.distinct.size != actual.size) _invalid(s"Presentation renderer slide assets differ: ${slide.id}")
    }
    val montage = _object(renderer("montage").get, "Presentation renderer montage")
    _exact_keys(montage, Set("path", "sha256", "pptxSha256"), "Presentation renderer montage")
    if (_serialized_path(plan, _string(montage, "path", "Presentation renderer montage"), "Presentation montage path") != _path(plan, _configuration(resolved).montage, "Presentation montage") || _sha256_value(montage, "pptxSha256", "Presentation renderer montage") != _sha256(output)) _invalid("Presentation renderer montage differs")
    _verify_pptx(output, document, plan)
  }

  private def _verify_visual_page_renderer_manifest(plan: CozyMedia.Plan, resolved: CozyMedia.ResolvedResource, visual: VisualPageInput, template: Path, output: Path, renderer: JsonObject): Unit = {
    _exact_ordered_keys(renderer, Vector("schema", "target", "profile", "renderer", "visualPageSetSha256", "catalogSha256", "bindingSha256", "templateSha256", "pptx", "slides", "montage"), "Presentation VisualPage renderer manifest")
    if (_string(renderer, "schema", "Presentation VisualPage renderer manifest") != _visual_page_renderer_schema || _string(renderer, "target", "Presentation VisualPage renderer manifest") != resolved.resource.id || _string(renderer, "profile", "Presentation VisualPage renderer manifest") != _configuration(resolved).profile)
      _invalid("Presentation VisualPage renderer manifest identity differs from descriptor")
    val profile = _profile(plan, _configuration(resolved), resolved)
    val rendereridentity = _object(renderer("renderer").getOrElse(_invalid("Presentation VisualPage renderer identity is missing")), "Presentation VisualPage renderer identity")
    _exact_keys(rendereridentity, Set("name", "version"), "Presentation VisualPage renderer identity")
    if (_string(rendereridentity, "name", "Presentation VisualPage renderer identity") != profile.renderer.name || _string(rendereridentity, "version", "Presentation VisualPage renderer identity") != profile.renderer.version)
      _invalid("Presentation VisualPage renderer identity differs from profile")
    if (_sha256_value(renderer, "visualPageSetSha256", "Presentation VisualPage renderer manifest") != _semantic_sha256(visual.document.documentIdentity) ||
        _sha256_value(renderer, "catalogSha256", "Presentation VisualPage renderer manifest") != _semantic_sha256(visual.document.catalogIdentity) ||
        _sha256_value(renderer, "bindingSha256", "Presentation VisualPage renderer manifest") != _semantic_sha256(visual.binding.bindingIdentity) ||
        _sha256_value(renderer, "templateSha256", "Presentation VisualPage renderer manifest") != _sha256(template))
      _invalid("Presentation VisualPage renderer manifest input hashes differ")
    val pptx = _object(renderer("pptx").getOrElse(_invalid("Presentation VisualPage renderer PPTX is missing")), "Presentation VisualPage renderer PPTX")
    _exact_keys(pptx, Set("path", "sha256"), "Presentation VisualPage renderer PPTX")
    if (_serialized_path(plan, _string(pptx, "path", "Presentation VisualPage renderer PPTX"), "Presentation VisualPage PPTX path") != output || _sha256_value(pptx, "sha256", "Presentation VisualPage renderer PPTX") != _sha256(output))
      _invalid("Presentation VisualPage renderer PPTX differs from configured output")
    val pages = visual.document.document.pages
    val slides = renderer("slides").flatMap(_.asArray).getOrElse(_invalid("Presentation VisualPage renderer slides must be an array")).toVector
    val ids = slides.map(value => _string(_object(value, "Presentation VisualPage renderer slide"), "id", "Presentation VisualPage renderer slide"))
    if (ids != pages.map(_.id) || ids.distinct.size != ids.size) _invalid("Presentation VisualPage renderer page order or identities differ from VisualPageSet")
    slides.zip(pages).foreach { case (value, page) =>
      val entry = _object(value, "Presentation VisualPage renderer slide")
      _exact_ordered_keys(entry, Vector("id", "path", "sha256", "pptxSha256", "assets"), "Presentation VisualPage renderer slide")
      if (_sha256_value(entry, "pptxSha256", "Presentation VisualPage renderer slide") != _sha256(output)) _invalid(s"Presentation VisualPage renderer slide differs: ${page.id}")
      val assets = entry("assets").flatMap(_.asArray).getOrElse(_invalid("Presentation VisualPage renderer slide assets must be an array")).toVector
      val expected = page.assets.map(asset => asset.id -> asset.sha256)
      val actual = assets.map { item =>
        val asset = _object(item, "Presentation VisualPage renderer asset")
        _exact_ordered_keys(asset, Vector("id", "sha256"), "Presentation VisualPage renderer asset")
        _string(asset, "id", "Presentation VisualPage renderer asset") -> _sha256_value(asset, "sha256", "Presentation VisualPage renderer asset")
      }
      if (actual != expected || actual.map(_._1).distinct.size != actual.size) _invalid(s"Presentation VisualPage renderer slide assets differ: ${page.id}")
    }
    val montage = _object(renderer("montage").getOrElse(_invalid("Presentation VisualPage renderer montage is missing")), "Presentation VisualPage renderer montage")
    _exact_keys(montage, Set("path", "sha256", "pptxSha256"), "Presentation VisualPage renderer montage")
    if (_serialized_path(plan, _string(montage, "path", "Presentation VisualPage renderer montage"), "Presentation montage path") != _path(plan, _configuration(resolved).montage, "Presentation montage") || _sha256_value(montage, "pptxSha256", "Presentation VisualPage renderer montage") != _sha256(output)) _invalid("Presentation VisualPage renderer montage differs")
    _verify_visual_page_pptx(output, pages)
  }

  private def _verify_visual_page_pptx(path: Path, pages: Vector[CozyVisualPage.Page]): Unit = {
    val zip = try new ZipFile(path.toFile) catch { case NonFatal(_) => _invalid(s"Presentation PPTX cannot be opened: $path") }
    try {
      val entries = zip.entries.asScala.toVector.filterNot(_.isDirectory)
      val names = entries.map(_.getName).toSet
      if (names.size != entries.size || !Set("[Content_Types].xml", "ppt/presentation.xml", "ppt/_rels/presentation.xml.rels").subsetOf(names)) _invalid("Presentation PPTX lacks required OOXML entries")
      val contenttypes = _xml(zip.getInputStream(zip.getEntry("[Content_Types].xml")).readAllBytes(), "content types")
      if (contenttypes.getDocumentElement.getLocalName != "Types") _invalid("Presentation PPTX content types are invalid")
      val presentation = _xml(zip.getInputStream(zip.getEntry("ppt/presentation.xml")).readAllBytes(), "presentation")
      val relationids = _nodes(presentation, "http://schemas.openxmlformats.org/presentationml/2006/main", "sldId").map(node => _attribute(node, "http://schemas.openxmlformats.org/officeDocument/2006/relationships", "id", "Presentation slide relation"))
      if (relationids.size != pages.size || relationids.distinct.size != relationids.size) _invalid("Presentation PPTX slide relation count differs from VisualPageSet")
      val relations = _relationships(zip, "ppt/_rels/presentation.xml.rels")
      relations.values.foreach { case (_, target) => _package_target("ppt", target, "ppt/") }
      val sliderelationids = relations.collect { case (id, (kind, _)) if kind == "http://schemas.openxmlformats.org/officeDocument/2006/relationships/slide" => id }.toSet
      if (sliderelationids != relationids.toSet) _invalid("Presentation PPTX has unreferenced slide relationships")
      val ordered = relationids.map { id =>
        val relation = relations.getOrElse(id, _invalid(s"Presentation PPTX lacks slide relation: $id"))
        if (relation._1 != "http://schemas.openxmlformats.org/officeDocument/2006/relationships/slide") _invalid("Presentation PPTX contains non-slide presentation relation")
        _package_target("ppt", relation._2, "ppt/slides/")
      }
      val expectednames = (1 to pages.size).map(index => s"ppt/slides/slide$index.xml").toVector
      if (ordered != expectednames || entries.filter(_.getName.matches("ppt/slides/slide[0-9]+\\.xml")).map(_.getName).toSet != expectednames.toSet) _invalid("Presentation PPTX slide parts must be contiguous and relationship ordered")
      val referencedmedia = ordered.zip(pages).flatMap { case (name, page) =>
        val entry = zip.getEntry(name)
        val slidexml = _xml(zip.getInputStream(entry).readAllBytes(), s"slide ${page.id}")
        if (_drawingml_text(slidexml).trim.isEmpty) _invalid(s"Presentation PPTX slide text is structurally incomplete: ${page.id}")
        val embeds = _nodes(slidexml, "http://schemas.openxmlformats.org/drawingml/2006/main", "blip").map(node => _attribute(node, "http://schemas.openxmlformats.org/officeDocument/2006/relationships", "embed", "Presentation image relation"))
        if (embeds.distinct.size != embeds.size) _invalid(s"Presentation PPTX has duplicate image relations: ${page.id}")
        val number = name.stripPrefix("ppt/slides/slide").stripSuffix(".xml")
        val sliderels = _relationships(zip, s"ppt/slides/_rels/slide$number.xml.rels")
        sliderels.values.foreach { case (_, target) => _package_target("ppt/slides", target, "ppt/") }
        val imagerelationids = sliderels.collect { case (id, (kind, _)) if kind == "http://schemas.openxmlformats.org/officeDocument/2006/relationships/image" => id }.toSet
        if (imagerelationids != embeds.toSet) _invalid(s"Presentation PPTX has unreferenced image relationships: ${page.id}")
        val images = embeds.map { id =>
          val relation = sliderels.getOrElse(id, _invalid(s"Presentation PPTX lacks slide image relation: $id"))
          if (relation._1 != "http://schemas.openxmlformats.org/officeDocument/2006/relationships/image") _invalid("Presentation PPTX slide relation is not an image")
          _package_target("ppt/slides", relation._2, "ppt/media/")
        }
        val expectedhashes = page.assets.map(_.sha256).sorted
        val actualhashes = images.map(image => if (!names.contains(image)) _invalid(s"Presentation PPTX image is missing: $image") else _sha256_bytes(zip.getInputStream(zip.getEntry(image)).readAllBytes())).sorted
        if (actualhashes != expectedhashes) _invalid(s"Presentation PPTX slide media differs: ${page.id}")
        images
      }
      val packagedmedia = entries.filter(_.getName.startsWith("ppt/media/")).map(_.getName).toSet
      if (referencedmedia.toSet != packagedmedia) _invalid("Presentation PPTX has orphan media entries")
    } finally zip.close()
  }

  private def _verify_pptx(path: Path, document: CozyMediaSlideIr.Document, plan: CozyMedia.Plan): Unit = {
    val zip = try new ZipFile(path.toFile) catch { case NonFatal(_) => _invalid(s"Presentation PPTX cannot be opened: $path") }
    try {
      val entries = zip.entries.asScala.toVector.filterNot(_.isDirectory)
      val names = entries.map(_.getName).toSet
      if (names.size != entries.size || !Set("[Content_Types].xml", "ppt/presentation.xml", "ppt/_rels/presentation.xml.rels").subsetOf(names)) _invalid("Presentation PPTX lacks required OOXML entries")
      val contenttypes = _xml(zip.getInputStream(zip.getEntry("[Content_Types].xml")).readAllBytes(), "content types")
      if (contenttypes.getDocumentElement.getLocalName != "Types") _invalid("Presentation PPTX content types are invalid")
      val presentation = _xml(zip.getInputStream(zip.getEntry("ppt/presentation.xml")).readAllBytes(), "presentation")
      val relationids = _nodes(presentation, "http://schemas.openxmlformats.org/presentationml/2006/main", "sldId").map(node => _attribute(node, "http://schemas.openxmlformats.org/officeDocument/2006/relationships", "id", "Presentation slide relation"))
      if (relationids.size != document.slides.size || relationids.distinct.size != relationids.size) _invalid("Presentation PPTX slide relation count differs from slide IR")
      val relations = _relationships(zip, "ppt/_rels/presentation.xml.rels")
      relations.values.foreach { case (_, target) => _package_target("ppt", target, "ppt/") }
      val sliderelationids = relations.collect { case (id, (kind, _)) if kind == "http://schemas.openxmlformats.org/officeDocument/2006/relationships/slide" => id }.toSet
      if (sliderelationids != relationids.toSet) _invalid("Presentation PPTX has unreferenced slide relationships")
      val ordered = relationids.map { id =>
        val relation = relations.getOrElse(id, _invalid(s"Presentation PPTX lacks slide relation: $id"))
        if (relation._1 != "http://schemas.openxmlformats.org/officeDocument/2006/relationships/slide") _invalid("Presentation PPTX contains non-slide presentation relation")
        _package_target("ppt", relation._2, "ppt/slides/")
      }
      val expectednames = (1 to document.slides.size).map(index => s"ppt/slides/slide$index.xml").toVector
      if (ordered != expectednames || entries.filter(_.getName.matches("ppt/slides/slide[0-9]+\\.xml")).map(_.getName).toSet != expectednames.toSet) _invalid("Presentation PPTX slide parts must be contiguous and relationship ordered")
      val referencedmedia = ordered.zip(document.slides).flatMap { case (name, slide) =>
        val entry = zip.getEntry(name)
        val slidexml = _xml(zip.getInputStream(entry).readAllBytes(), s"slide ${slide.id}")
        val text = _drawingml_text(slidexml)
        if (text.trim.isEmpty || !text.contains(slide.title)) _invalid(s"Presentation PPTX slide text is structurally incomplete: ${slide.id}")
        val embeds = _nodes(slidexml, "http://schemas.openxmlformats.org/drawingml/2006/main", "blip").map(node => _attribute(node, "http://schemas.openxmlformats.org/officeDocument/2006/relationships", "embed", "Presentation image relation"))
        if (embeds.distinct.size != embeds.size) _invalid(s"Presentation PPTX has duplicate image relations: ${slide.id}")
        val number = name.stripPrefix("ppt/slides/slide").stripSuffix(".xml")
        val sliderels = _relationships(zip, s"ppt/slides/_rels/slide$number.xml.rels")
        sliderels.values.foreach { case (_, target) => _package_target("ppt/slides", target, "ppt/") }
        val imagerelationids = sliderels.collect { case (id, (kind, _)) if kind == "http://schemas.openxmlformats.org/officeDocument/2006/relationships/image" => id }.toSet
        if (imagerelationids != embeds.toSet) _invalid(s"Presentation PPTX has unreferenced image relationships: ${slide.id}")
        val images = embeds.map { id =>
          val relation = sliderels.getOrElse(id, _invalid(s"Presentation PPTX lacks slide image relation: $id"))
          if (relation._1 != "http://schemas.openxmlformats.org/officeDocument/2006/relationships/image") _invalid("Presentation PPTX slide relation is not an image")
          _package_target("ppt/slides", relation._2, "ppt/media/")
        }
        val expectedhashes = slide.elements.flatMap(_.asset).distinct.sorted.map(id => _sha256(_asset_path(plan, id)))
        val actualhashes = images.map(image => if (!names.contains(image)) _invalid(s"Presentation PPTX image is missing: $image") else _sha256_bytes(zip.getInputStream(zip.getEntry(image)).readAllBytes())).sorted
        if (actualhashes != expectedhashes) _invalid(s"Presentation PPTX slide media differs: ${slide.id}")
        images
      }
      val packagedmedia = entries.filter(_.getName.startsWith("ppt/media/")).map(_.getName).toSet
      if (referencedmedia.toSet != packagedmedia) _invalid("Presentation PPTX has orphan media entries")
    } finally zip.close()
  }

  private def _verify_review_manifest(plan: CozyMedia.Plan, resolved: CozyMedia.ResolvedResource): Unit = {
    if (_is_visual_page(_configuration(resolved))) {
      _verify_visual_page_review_manifest(plan, resolved)
      return
    }
    val path = _path(plan, _configuration(resolved).reviewManifest, "Presentation review manifest")
    if (!_direct_regular_file(path)) _invalid("Presentation deterministic review manifest is missing")
    val objectvalue = parse(Files.readString(path, StandardCharsets.UTF_8)).fold(_ => _invalid("Presentation review manifest must be JSON"), _.asObject.getOrElse(_invalid("Presentation review manifest must be an object")))
    val required = Set("schema", "target", "knowledge", "language", "inputSetSha256", "renderer", "slideIr", "template", "pptx", "slides", "assets", "montage", "articlePdf", "infographic", "verification")
    _exact_keys(objectvalue, required, "Presentation review manifest")
    val configuration = _configuration(resolved)
    val document = CozyMediaSlideIr.validate(plan, resolved, requireAssets = true)
    val output = resolved.output.getOrElse(_invalid("Presentation output is missing"))
    val expected = _review_json(plan, resolved, document, output, _template(plan, resolved), _artifacts(plan, resolved, requirecurrentassets = true), _profile(plan, configuration, resolved).renderer, _dependency(plan, configuration.articlePdf, resolved), _dependency(plan, configuration.infographic, resolved))
    if (Json.fromJsonObject(objectvalue) != expected)
      _invalid("Presentation review manifest differs from current deterministic reconstruction")
  }

  private def _verify_visual_page_review_manifest(plan: CozyMedia.Plan, resolved: CozyMedia.ResolvedResource): Unit = {
    val path = _path(plan, _configuration(resolved).reviewManifest, "Presentation VisualPage review manifest")
    if (!_direct_regular_file(path)) _invalid("Presentation deterministic VisualPage review manifest is missing")
    val objectvalue = parse(Files.readString(path, StandardCharsets.UTF_8)).fold(_ => _invalid("Presentation VisualPage review manifest must be JSON"), _.asObject.getOrElse(_invalid("Presentation VisualPage review manifest must be an object")))
    _exact_ordered_keys(objectvalue, Vector("schema", "target", "knowledge", "language", "inputSetSha256", "renderer", "visualPageSet", "catalog", "binding", "template", "pptx", "slides", "assets", "montage", "articlePdf", "infographic", "verification"), "Presentation VisualPage review manifest")
    val configuration = _configuration(resolved)
    val visual = _visual_page_input(plan, resolved)
    val output = resolved.output.getOrElse(_invalid("Presentation output is missing"))
    val expected = _visual_page_review_json(plan, resolved, visual, output, _template(plan, resolved), _visual_page_artifacts(plan, resolved, visual, requirecurrentassets = true), _profile(plan, configuration, resolved).renderer, _dependency(plan, configuration.articlePdf, resolved), _dependency(plan, configuration.infographic, resolved))
    if (Json.fromJsonObject(objectvalue) != expected)
      _invalid("Presentation VisualPage review manifest differs from current deterministic reconstruction")
  }

  private def _validate_receipt(descriptor: CozyMedia.Descriptor, resource: CozyMedia.Resource, profile: ProfileConfig, root: Path): Unit = {
    val receipt = descriptor.receipt.getOrElse(_invalid(s"Presentation descriptor requires receipt configuration: ${resource.id}"))
    val producer = receipt.producer.getOrElse(_invalid(s"Presentation descriptor requires receipt producer: ${resource.id}"))
    val configuration = resource.presentation.get
    if (producer.profile != configuration.profile || producer.renderer.name != profile.renderer.name || producer.renderer.version != profile.renderer.version)
      _invalid(s"Presentation receipt producer differs from presentation profile: ${resource.id}")
    val source = resource.source.get
    if (_is_visual_page(configuration)) {
      if (!receipt.inputs.exists(input => input.role == "visual-page-set" && input.path == source && input.normalization == "structured-document")) _invalid(s"Presentation receipt requires structured-document VisualPageSet input: ${resource.id}")
      if (!receipt.inputs.exists(input => input.role == "catalog" && configuration.catalog.contains(input.path) && input.normalization == "structured-document")) _invalid(s"Presentation receipt requires structured-document catalog input: ${resource.id}")
      if (!receipt.inputs.exists(input => input.role == "binding" && configuration.binding.contains(input.path) && input.normalization == "bytes")) _invalid(s"Presentation receipt requires bytes binding input: ${resource.id}")
    } else if (!receipt.inputs.exists(input => input.role == "slide-ir" && input.path == source && input.normalization == "structured-document")) _invalid(s"Presentation receipt requires structured-document slide IR input: ${resource.id}")
    if (!receipt.inputs.exists(input => input.role == "template" && input.path == profile.template && input.normalization == "bytes")) _invalid(s"Presentation receipt requires bytes template input: ${resource.id}")
  }

  private def _validate_resource_config(value: ResourceConfig, label: String): Unit = {
    Vector(value.profile, value.articlePdf, value.infographic).foreach(_identity(_, s"Presentation resource $label configuration"))
    value.contract match {
      case None =>
        if (value.catalog.nonEmpty || value.binding.nonEmpty)
          _invalid(s"Presentation legacy resource may not declare catalog or binding: $label")
      case Some(contract) =>
        if (contract != _visual_page_contract)
          _invalid(s"Presentation resource contract must be exactly ${_visual_page_contract}: $label")
        Vector(value.catalog, value.binding).foreach(_.getOrElse(_invalid(s"Presentation VisualPage resource requires catalog and binding: $label")))
    }
    Vector(value.slideImages, value.montage, value.rendererManifest, value.reviewManifest, value.reviewState).foreach { path =>
      val exact = _identity(path, s"Presentation resource $label path")
      if (Path.of(exact).isAbsolute || exact.split('/').contains("..") || exact.contains('\\'))
        _invalid(s"Presentation resource $label path must be descriptor-relative forward-slash spelling")
    }
  }

  private def _validate_renderer(value: RendererConfig, label: String): Unit = {
    _identity(value.name, s"$label renderer name"); _identity(value.version, s"$label renderer version")
    if (value.command.isEmpty || value.command.exists(token => token == null || token.isEmpty || token != token.trim)) _invalid(s"$label renderer command must be non-empty exact argv tokens")
  }

  private def _configuration(resolved: CozyMedia.ResolvedResource): ResourceConfig = resolved.resource.presentation.getOrElse(_invalid(s"Presentation resource requires configuration: ${resolved.resource.id}"))
  private def _is_visual_page(configuration: ResourceConfig): Boolean = configuration.contract.nonEmpty
  private def _profile(plan: CozyMedia.Plan, configuration: ResourceConfig, resolved: CozyMedia.ResolvedResource): ProfileConfig = plan.descriptor.profiles.get(configuration.profile).flatMap(_.presentation).getOrElse(_invalid(s"Presentation profile is not configured: ${resolved.resource.id}"))
  private def _template(plan: CozyMedia.Plan, resolved: CozyMedia.ResolvedResource): Path = _path(plan, _profile(plan, _configuration(resolved), resolved).template, "Presentation template")
  private def _dependency(plan: CozyMedia.Plan, id: String, resolved: CozyMedia.ResolvedResource): CozyMedia.ResolvedResource = plan.resources.find(_.resource.id == id).filter(_.resource.id != resolved.resource.id).getOrElse(_invalid(s"Presentation dependency is not declared: $id"))
  private def _asset_path(plan: CozyMedia.Plan, id: String): Path =
    plan.resources.find(_.resource.id == id).flatMap(resolved => resolved.output.orElse(if (resolved.resource.build == "prebuilt") resolved.source else None)).getOrElse(
      _invalid(s"Presentation asset has no output: $id")
    )
  private def _output(resolved: CozyMedia.ResolvedResource): Path = resolved.output.orElse(if (resolved.resource.build == "prebuilt") resolved.source else None).getOrElse(_invalid(s"Media resource has no output: ${resolved.resource.id}"))
  private def _path(plan: CozyMedia.Plan, value: String, label: String): Path = { val path = plan.descriptorRoot.resolve(_identity(value, label)).normalize(); if (!path.startsWith(plan.descriptorRoot)) _invalid(s"$label escapes descriptor root"); path }
  private def _descriptor_path(root: Path, value: String, label: String): Path = { val path = root.resolve(_identity(value, label)).normalize(); if (!path.startsWith(root)) _invalid(s"$label escapes descriptor root"); path }
  private def _direct_descriptor_input(root: Path, value: String, label: String): Path = {
    val exact = _identity(value, label)
    if (exact.exists(character => Character.isISOControl(character)) || exact.contains('\\') || exact.contains(':') || exact.startsWith("/") || exact.split('/').exists(segment => segment.isEmpty || segment == "." || segment == ".."))
      _invalid(s"$label must be a safe descriptor-relative forward-slash path")
    val relative = try Path.of(exact) catch { case NonFatal(_) => _invalid(s"$label is not a valid path") }
    if (relative.isAbsolute) _invalid(s"$label must be descriptor-relative")
    val normalized = relative.normalize()
    if (normalized.toString.replace('\\', '/') != exact) _invalid(s"$label must use normalized descriptor-relative spelling")
    val path = root.resolve(normalized).normalize()
    val iterator = normalized.iterator()
    var current = root
    while (iterator.hasNext) {
      current = current.resolve(iterator.next())
      if (Files.isSymbolicLink(current)) _invalid(s"$label must not resolve through a symlinked ancestor")
    }
    if (!path.startsWith(root) || !_direct_regular_file(path)) _invalid(s"$label must be an existing direct regular non-symlink file")
    path
  }
  private def _serialized_path(plan: CozyMedia.Plan, value: String, label: String): Path = { if (value.contains('\\') || value.startsWith("/") || value.split('/').contains("..")) _invalid(s"$label must be descriptor-relative forward-slash path"); _path(plan, value, label) }
  private def _relative(plan: CozyMedia.Plan, path: Path): String = { val relative = plan.descriptorRoot.relativize(path).toString.replace('\\', '/'); if (relative.startsWith("../") || relative == "..") _invalid("Presentation artifact escapes descriptor root"); relative }
  private def _renderer_manifest(path: Path): JsonObject = parse(Files.readString(path, StandardCharsets.UTF_8)).fold(_ => _invalid("Presentation renderer manifest must be JSON"), _.asObject.getOrElse(_invalid("Presentation renderer manifest must be an object")))
  private def _receipt_entry(plan: CozyMedia.Plan, resolved: CozyMedia.ResolvedResource): CozyMediaReceipt.ManifestEntry = CozyMediaReceipt.manifest(plan.descriptorRoot.resolve("target/cozy-media/manifest.json")).flatMap(_.resources.find(_.id == resolved.resource.id)).getOrElse(_invalid(s"Media receipt entry is missing: ${resolved.resource.id}"))
  private def _artifact(values: Vector[CozyMediaReceipt.Artifact], id: String): CozyMediaReceipt.Artifact = values.find(_.id == id).getOrElse(_invalid(s"Presentation artifact is missing: $id"))
  private def _artifact_json(value: CozyMediaReceipt.Artifact): Json = Json.fromFields(Vector("id" -> Json.fromString(value.id), "role" -> Json.fromString(value.role), "path" -> Json.fromString(value.path), "sha256" -> Json.fromString(value.sha256)) ++ value.sourceSha256.map(x => "sourceSha256" -> Json.fromString(x)).toVector)
  private def _identity_json(plan: CozyMedia.Plan, path: Path): Json = Json.obj("path" -> Json.fromString(_relative(plan, path)), "sha256" -> Json.fromString(_sha256(path)))
  private def _semantic_identity_json(plan: CozyMedia.Plan, path: Path, identity: String): Json = Json.obj("path" -> Json.fromString(_relative(plan, path)), "sha256" -> Json.fromString(_semantic_sha256(identity)))
  private def _visual_assets(visual: VisualPageInput): Vector[CozyVisualPage.Asset] = visual.document.document.pages.flatMap(_.assets).distinct.sortBy(asset => (asset.id, asset.sha256))
  private def _pptx(path: Path): Boolean = _direct_regular_file(path) && path.getFileName.toString.toLowerCase(java.util.Locale.ROOT).endsWith(".pptx")
  private def _png(path: Path): Boolean = { val bytes = if (_direct_regular_file(path)) Files.readAllBytes(path) else Array.emptyByteArray; bytes.length > 8 && bytes.take(8).sameElements(Array[Byte](0x89.toByte, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a)) }
  private def _xml(bytes: Array[Byte], label: String): Document = try {
    val factory = DocumentBuilderFactory.newInstance()
    factory.setNamespaceAware(true)
    factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true)
    factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
    factory.setFeature("http://xml.org/sax/features/external-general-entities", false)
    factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false)
    factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false)
    factory.setXIncludeAware(false)
    factory.setExpandEntityReferences(false)
    try factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "")
    catch { case _: IllegalArgumentException => () }
    try factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "")
    catch { case _: IllegalArgumentException => () }
    val builder = factory.newDocumentBuilder()
    builder.setEntityResolver(new EntityResolver {
      def resolveEntity(publicId: String, systemId: String): SaxInputSource =
        throw new SAXException("External XML entity resolution is forbidden")
    })
    builder.parse(new ByteArrayInputStream(bytes))
  } catch { case NonFatal(_) => _invalid(s"Presentation PPTX $label XML is invalid or unsafe") }

  private def _nodes(document: Document, namespace: String, name: String): Vector[Node] = {
    val values = document.getElementsByTagNameNS(namespace, name)
    (0 until values.getLength).map(values.item).toVector
  }

  private def _attribute(node: Node, namespace: String, name: String, label: String): String =
    Option(if (namespace == null) node.getAttributes.getNamedItem(name) else node.getAttributes.getNamedItemNS(namespace, name)).map(_.getNodeValue).filter(value => value.nonEmpty && value == value.trim).getOrElse(_invalid(s"$label is missing"))

  private def _drawingml_text(document: Document): String =
    _nodes(document, "http://schemas.openxmlformats.org/drawingml/2006/main", "t").map(_.getTextContent).mkString(" ")

  private def _relationships(zip: ZipFile, name: String): Map[String, (String, String)] = {
    val entry = Option(zip.getEntry(name)).getOrElse(_invalid(s"Presentation PPTX lacks relationship part: $name"))
    val document = _xml(zip.getInputStream(entry).readAllBytes(), name)
    if (document.getDocumentElement.getLocalName != "Relationships") _invalid(s"Presentation PPTX relationship part is invalid: $name")
    val rows = _nodes(document, "http://schemas.openxmlformats.org/package/2006/relationships", "Relationship").map { node =>
      val id = _attribute(node, null, "Id", "Presentation relationship")
      val kind = _attribute(node, null, "Type", "Presentation relationship")
      val target = _attribute(node, null, "Target", "Presentation relationship")
      if (Option(node.getAttributes.getNamedItem("TargetMode")).exists(_.getNodeValue != "Internal")) _invalid("Presentation relationship target must be internal")
      if (target.startsWith("/") || target.contains('\\') || target.contains(':')) _invalid("Presentation relationship target must be a safe internal package target")
      id -> (kind -> target)
    }
    if (rows.map(_._1).distinct.size != rows.size) _invalid(s"Presentation PPTX has duplicate relationship IDs: $name")
    rows.toMap
  }

  private def _package_target(base: String, target: String, requiredPrefix: String): String = {
    if (target.isEmpty || target.startsWith("/") || target.contains('\\')) _invalid("Presentation relationship target is unsafe")
    val resolved = java.nio.file.Paths.get(base).resolve(target).normalize().toString.replace('\\', '/')
    if (!resolved.startsWith(requiredPrefix) || resolved.split('/').contains("..")) _invalid("Presentation relationship target escapes its permitted package area")
    resolved
  }

  private def _overlap(left: Path, right: Path): Boolean = left == right || left.startsWith(right) || right.startsWith(left)

  private def _reject_path_overlap(values: Vector[(String, Path)], label: String): Unit =
    values.combinations(2).foreach { pair => if (_overlap(pair.head._2, pair(1)._2)) _invalid(s"$label overlap: ${pair.head._1} and ${pair(1)._1}") }
  private def _sha256(path: Path): String = _sha256_bytes(Files.readAllBytes(path))
  private def _sha256_bytes(bytes: Array[Byte]): String = MessageDigest.getInstance("SHA-256").digest(bytes).map(x => f"${x & 0xff}%02x").mkString
  private def _semantic_sha256(identity: String): String = identity match { case value if value.matches("sha256:[0-9a-f]{64}") => value.drop("sha256:".length); case _ => _invalid("Presentation VisualPage canonical identity must be SHA-256") }
  private def _sha256_value(value: JsonObject, field: String, label: String): String = { val hash = _string(value, field, label); if (hash.matches("[0-9a-f]{64}")) hash else _invalid(s"$label.$field must be SHA-256") }
  private def _object(value: Json, label: String): JsonObject = value.asObject.getOrElse(_invalid(s"$label must be an object"))
  private def _exact_keys(value: JsonObject, expected: Set[String], label: String): Unit = if (value.keys.toSet != expected) _invalid(s"$label requires exactly: ${expected.toVector.sorted.mkString(", ")}")
  private def _exact_ordered_keys(value: JsonObject, expected: Vector[String], label: String): Unit = if (value.keys.toVector != expected) _invalid(s"$label requires exactly ordered fields: ${expected.mkString(", ")}")
  private def _require_exact_keys(c: HCursor, expected: Set[String], label: String): Decoder.Result[Unit] = c.value.asObject match { case Some(value) if value.keys.toSet == expected => Right(()); case Some(_) => Left(io.circe.DecodingFailure(s"$label requires exactly: ${expected.toVector.sorted.mkString(", ")}", c.history)); case None => Left(io.circe.DecodingFailure(s"$label must be an object", c.history)) }
  private def _string(value: JsonObject, field: String, label: String): String = value(field).flatMap(_.asString).filter(x => x.nonEmpty && x == x.trim).getOrElse(_invalid(s"$label.$field must be a non-empty exact string"))
  private def _identity(value: String, label: String): String = if (value == null || value.isEmpty || value != value.trim) _invalid(s"$label must be a non-empty exact trimmed string") else value
  private def _direct_regular_file(path: Path): Boolean = path != null && !Files.isSymbolicLink(path) && Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)
  private def _invalid(message: String): Nothing = RAISE.invalidArgumentFault(message)
}
