package cozy.media

import org.apache.pdfbox.pdmodel.PDDocument
import org.goldenport.RAISE
import io.circe.{Decoder, HCursor, Json, JsonObject}
import io.circe.parser.parse
import java.nio.charset.StandardCharsets
import java.nio.file.{AtomicMoveNotSupportedException, Files, LinkOption, Path, StandardCopyOption}
import java.security.MessageDigest
import scala.util.control.NonFatal

/*
 * @since   Aug. 29, 2026
 * @version Aug. 29, 2026
 * @author  ASAMI, Tomoharu
 */
private[cozy] object CozyMediaSummarySlidesPdf {
  final case class Config(
    contract: String,
    profile: String,
    catalog: Option[String],
    binding: Option[String],
    slideImages: String,
    montage: String,
    rendererManifest: String,
    articlePdf: String,
    infographic: String,
    pptx: Option[String]
  )
  object Config {
    private val _common_fields = Set("contract", "profile", "slideImages", "montage", "rendererManifest", "articlePdf", "infographic")
    private val _visual_page_fields = _common_fields ++ Set("catalog", "binding")

    implicit val decoder: Decoder[Config] = (c: HCursor) =>
      c.value.asObject match {
        case Some(value) =>
          val keys = value.keys.toSet
          val haspptx = keys.contains("pptx")
          val base = keys -- Set("pptx")
          if (!Set(_common_fields, _visual_page_fields).contains(base))
            Left(io.circe.DecodingFailure("summarySlidesPdf requires one closed slide-ir-v1 or visual-page-v1 grammar", c.history))
          else for {
            contract <- c.downField("contract").as[String]
            profile <- c.downField("profile").as[String]
            catalog <- c.downField("catalog").as[Option[String]]
            binding <- c.downField("binding").as[Option[String]]
            slideimages <- c.downField("slideImages").as[String]
            montage <- c.downField("montage").as[String]
            renderermanifest <- c.downField("rendererManifest").as[String]
            articlepdf <- c.downField("articlePdf").as[String]
            infographic <- c.downField("infographic").as[String]
            pptx <- c.downField("pptx").as[Option[String]]
            _ <- _grammar(contract, base, haspptx, c)
          } yield Config(contract, profile, catalog, binding, slideimages, montage, renderermanifest, articlepdf, infographic, pptx)
        case None =>
          Left(io.circe.DecodingFailure("summarySlidesPdf must be an object", c.history))
      }

    private def _grammar(contract: String, fields: Set[String], haspptx: Boolean, c: HCursor): Decoder.Result[Unit] =
      contract match {
        case "slide-ir-v1" if fields == _common_fields => Right(())
        case "visual-page-v1" if fields == _visual_page_fields => Right(())
        case "slide-ir-v1" | "visual-page-v1" =>
          Left(io.circe.DecodingFailure(s"summarySlidesPdf $contract has incompatible fields${if (haspptx) " including pptx" else ""}", c.history))
        case _ => Left(io.circe.DecodingFailure("summarySlidesPdf.contract must be slide-ir-v1 or visual-page-v1", c.history))
      }
  }

  private sealed trait Authority {
    def _source: Path
    def _page_ids: Vector[String]
    def _assets_by_page: Vector[Vector[(String, String)]]
  }
  private final case class SlideIrAuthority(source: Path, document: CozyMediaSlideIr.Document, assetsbypage: Vector[Vector[(String, String)]]) extends Authority {
    def _source: Path = source
    def _page_ids: Vector[String] = document.slides.map(_.id)
    def _assets_by_page: Vector[Vector[(String, String)]] = assetsbypage
  }
  private final case class VisualPageAuthority(
    source: Path,
    catalog: Path,
    binding: Path,
    document: CozyVisualPage.ValidatedDocument,
    bindingdocument: CozyVisualPageBinding.ValidatedBinding,
    assetsbypage: Vector[Vector[(String, String)]]
  ) extends Authority {
    def _source: Path = source
    def _page_ids: Vector[String] = document.document.pages.map(_.id)
    def _assets_by_page: Vector[Vector[(String, String)]] = assetsbypage
  }

  private val _slide_ir_contract = "slide-ir-v1"
  private val _visual_page_contract = "visual-page-v1"
  private val _slide_ir_schema = "cozy.summary-slides.render.v1"
  private val _visual_page_schema = "cozy.summary-slides.render.v2"
  private val _pdf_header = "%PDF-".getBytes(StandardCharsets.US_ASCII)
  private val _png_header = Array[Byte](0x89.toByte, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a)

  private[cozy] def validateGeneratedPathCollisions(descriptor: CozyMedia.Descriptor, root: Path): Unit = {
    val generated = descriptor.resources.filter(_.build == "summary-slides-pdf").flatMap { resource =>
      val configuration = resource.summarySlidesPdf.getOrElse(_invalid(s"Summary-slides PDF resource requires summarySlidesPdf configuration: ${resource.id}"))
      Vector(
        resource.output.map(value => _output_path(root, value, s"Summary-slides PDF output ${resource.id}")),
        Some(_output_path(root, configuration.slideImages, s"Summary-slides PDF slideImages ${resource.id}")),
        Some(_output_path(root, configuration.montage, s"Summary-slides PDF montage ${resource.id}")),
        Some(_output_path(root, configuration.rendererManifest, s"Summary-slides PDF rendererManifest ${resource.id}")),
        configuration.pptx.map(value => _output_path(root, value, s"Summary-slides PDF PPTX ${resource.id}"))
      ).flatten.map(path => resource.id -> path)
    }
    _reject_overlap(generated, "Summary-slides PDF generated paths")
  }

  def validateDescriptor(descriptor: CozyMedia.Descriptor, resource: CozyMedia.Resource, root: Path): Unit = {
    val configuration = resource.summarySlidesPdf.getOrElse(_invalid(s"Summary-slides PDF resource requires summarySlidesPdf configuration: ${resource.id}"))
    if (resource.kind != "document" || !resource.language.exists(Set("ja", "en")) || resource.source.isEmpty || resource.output.isEmpty)
      _invalid(s"Summary-slides PDF resource requires document kind, language ja or en, source, and output: ${resource.id}")
    resource.articleMedia match {
      case Some(CozyMedia.ResourceArticleMedia("summary_slides_pdf", _, Some("application/pdf"), _, _, _)) => ()
      case _ => _invalid(s"Summary-slides PDF resource requires articleMedia.role summary_slides_pdf and application/pdf: ${resource.id}")
    }
    if (configuration.profile != "business")
      _invalid(s"Summary-slides PDF profile must be exactly business: ${resource.id}")
    val profile = descriptor.profiles.get(configuration.profile).flatMap(_.presentation).getOrElse(
      _invalid(s"Summary-slides PDF resource has no configured presentation profile: ${resource.id}")
    )
    _validate_renderer(profile.renderer, s"Summary-slides PDF profile ${configuration.profile}")
    val source = _direct_input(root, resource.source.get, s"Summary-slides PDF source ${resource.id}")
    val template = _direct_input(root, profile.template, s"Summary-slides PDF template ${resource.id}")
    val output = _output_path(root, resource.output.get, s"Summary-slides PDF output ${resource.id}")
    val images = _output_path(root, configuration.slideImages, s"Summary-slides PDF slideImages ${resource.id}")
    val montage = _output_path(root, configuration.montage, s"Summary-slides PDF montage ${resource.id}")
    val manifest = _output_path(root, configuration.rendererManifest, s"Summary-slides PDF rendererManifest ${resource.id}")
    val pptx = configuration.pptx.map(value => _output_path(root, value, s"Summary-slides PDF PPTX ${resource.id}"))
    val article = _dependency(descriptor, configuration.articlePdf, resource)
    val infographic = _dependency(descriptor, configuration.infographic, resource)
    _validate_dependencies(article, infographic, resource)
    val generated = Vector(
      "pdf" -> output,
      "slideImages" -> images,
      "montage" -> montage,
      "rendererManifest" -> manifest
    ) ++ pptx.map("pptx" -> _)
    _reject_overlap(generated, s"Summary-slides PDF generated paths ${resource.id}")
    val dependencies = Vector("source" -> source, "template" -> template) ++
      _dependency_paths(root, article, s"Summary-slides PDF articlePdf dependency ${resource.id}") ++
      _dependency_paths(root, infographic, s"Summary-slides PDF infographic dependency ${resource.id}")
    val consumed = configuration.contract match {
      case value if value == _slide_ir_contract =>
        val document = CozyMediaSlideIr.load(source)
        _validate_slide_ir(descriptor, resource, configuration, document)
        document.assetIds.distinct.flatMap { id =>
          val dependency = _dependency(descriptor, id, resource)
          _dependency_paths(root, dependency, s"Summary-slides PDF Slide-IR asset $id ${resource.id}")
        }
      case value if value == _visual_page_contract =>
        val catalog = _direct_input(root, configuration.catalog.getOrElse(_invalid(s"Summary-slides PDF catalog is missing: ${resource.id}")), s"Summary-slides PDF catalog ${resource.id}")
        val binding = _direct_input(root, configuration.binding.getOrElse(_invalid(s"Summary-slides PDF binding is missing: ${resource.id}")), s"Summary-slides PDF binding ${resource.id}")
        val visual = _validate_visual_page(descriptor, resource, configuration, source, catalog, binding)
        val assetdependencies = visual.document.pages.flatMap(_.assets.map(_.id)).distinct.flatMap { id =>
          val dependency = _dependency(descriptor, id, resource)
          _dependency_paths(root, dependency, s"Summary-slides PDF Visual Page asset $id ${resource.id}")
        }
        Vector("catalog" -> catalog, "binding" -> binding) ++ assetdependencies ++
          _visual_page_consumed_paths(root, source, visual, s"Summary-slides PDF Visual Page ${resource.id}")
      case _ => _invalid(s"Summary-slides PDF contract is invalid: ${resource.id}")
    }
    _reject_generated_overlap(generated, dependencies ++ consumed, s"Summary-slides PDF generated paths ${resource.id}")
  }

  def action(plan: CozyMedia.Plan, resolved: CozyMedia.ResolvedResource, initial: CozyMedia.Action): CozyMedia.Action =
    initial match {
      case CozyMedia.Action.Current if current(plan, resolved) => CozyMedia.Action.Current
      case CozyMedia.Action.Current => CozyMedia.Action.Build
      case value => value
    }

  def current(plan: CozyMedia.Plan, resolved: CozyMedia.ResolvedResource): Boolean =
    try {
      if (!CozyMediaReceipt.current(plan, resolved)) false
      else {
        requireDependenciesCurrent(plan, resolved)
        verifyStructural(plan, resolved)
        true
      }
    } catch { case NonFatal(_) => false }

  def requireDependenciesCurrent(plan: CozyMedia.Plan, resolved: CozyMedia.ResolvedResource): Unit = {
    val configuration = _configuration(resolved)
    Vector(_dependency(plan, configuration.articlePdf, resolved), _dependency(plan, configuration.infographic, resolved)).foreach { dependency =>
      if (!CozyMediaReceipt.current(plan, dependency))
        _invalid(s"Summary-slides PDF target requires current dependency evidence: ${dependency.resource.id}")
    }
  }

  def build(plan: CozyMedia.Plan, resolved: CozyMedia.ResolvedResource, runner: CozyMedia.ProcessRunner): String = {
    val configuration = _configuration(resolved)
    val authority = _authority(plan, resolved)
    val profile = _profile(plan, configuration, resolved)
    val template = _direct_input(plan.descriptorRoot, profile.template, s"Summary-slides PDF template ${resolved.resource.id}")
    val output = resolved.output.getOrElse(_invalid(s"Summary-slides PDF output is missing: ${resolved.resource.id}"))
    _require_output_target(plan, output, s"Summary-slides PDF output ${resolved.resource.id}")
    val images = _output_path(plan.descriptorRoot, configuration.slideImages, s"Summary-slides PDF slideImages ${resolved.resource.id}")
    val montage = _output_path(plan.descriptorRoot, configuration.montage, s"Summary-slides PDF montage ${resolved.resource.id}")
    val manifest = _output_path(plan.descriptorRoot, configuration.rendererManifest, s"Summary-slides PDF rendererManifest ${resolved.resource.id}")
    val pptx = configuration.pptx.map(value => _output_path(plan.descriptorRoot, value, s"Summary-slides PDF PPTX ${resolved.resource.id}"))
    _prepare_output_parent(images)
    Files.createDirectories(images)
    _prepare_output_parent(montage)
    _prepare_output_parent(manifest)
    pptx.foreach(_prepare_output_parent)
    _prepare_output_parent(output)
    val parent = Option(output.getParent).getOrElse(_invalid(s"Summary-slides PDF output requires a parent: $output"))
    val staged = Files.createTempFile(parent, ".cozy-summary-slides-pdf-", ".pdf")
    Files.deleteIfExists(staged)
    val before = _snapshot(_input_paths(plan, resolved, authority, template))
    val receiptbefore = CozyMediaReceipt.capture(plan).inputSetSha256
    try {
      val command = _renderer_command(plan, resolved, configuration, authority, template, staged, images, montage, manifest, pptx)
      val exit = runner.run(command, plan.descriptorRoot)
      if (exit != 0) _invalid(s"Summary-slides PDF renderer failed for ${resolved.resource.id}: exit=$exit")
      _verify(plan, resolved, authority, template, staged)
      if (before != _snapshot(_input_paths(plan, resolved, authority, template)))
        _invalid(s"Summary-slides PDF inputs changed during build: ${resolved.resource.id}")
      if (receiptbefore != CozyMediaReceipt.capture(plan).inputSetSha256)
        _invalid("Media receipt inputs changed during summary-slides PDF build; no fresh acceptance evidence was written")
      _replace(staged, output)
      s"${resolved.resource.id}: rendered $output"
    } finally {
      try Files.deleteIfExists(staged) catch { case NonFatal(_) => () }
    }
  }

  def verifyStructural(plan: CozyMedia.Plan, resolved: CozyMedia.ResolvedResource): Unit = {
    val configuration = _configuration(resolved)
    val authority = _authority(plan, resolved)
    val profile = _profile(plan, configuration, resolved)
    val template = _direct_input(plan.descriptorRoot, profile.template, s"Summary-slides PDF template ${resolved.resource.id}")
    val output = resolved.output.getOrElse(_invalid(s"Summary-slides PDF output is missing: ${resolved.resource.id}"))
    if (!_direct_regular_file(output)) _invalid(s"Summary-slides PDF output must be a direct regular PDF: $output")
    _verify(plan, resolved, authority, template, output)
  }

  private def _authority(plan: CozyMedia.Plan, resolved: CozyMedia.ResolvedResource): Authority = {
    val resource = resolved.resource
    val configuration = _configuration(resolved)
    val source = _direct_input(plan.descriptorRoot, resource.source.getOrElse(_invalid(s"Summary-slides PDF source is missing: ${resource.id}")), s"Summary-slides PDF source ${resource.id}")
    configuration.contract match {
      case value if value == _slide_ir_contract =>
        val document = CozyMediaSlideIr.load(source)
        _validate_slide_ir(plan.descriptor, resource, configuration, document)
        val assets = document.slides.map(slide => slide.elements.flatMap(_.asset).distinct.sorted.map(id => id -> _sha256(_asset_path(plan, id, resource))))
        SlideIrAuthority(source, document, assets)
      case value if value == _visual_page_contract =>
        val catalog = _direct_input(plan.descriptorRoot, configuration.catalog.getOrElse(_invalid(s"Summary-slides PDF catalog is missing: ${resource.id}")), s"Summary-slides PDF catalog ${resource.id}")
        val binding = _direct_input(plan.descriptorRoot, configuration.binding.getOrElse(_invalid(s"Summary-slides PDF binding is missing: ${resource.id}")), s"Summary-slides PDF binding ${resource.id}")
        val visual = _validate_visual_page(plan.descriptor, resource, configuration, source, catalog, binding)
        val assets = visual.document.pages.map(page => page.assets.map { asset =>
          val actual = _sha256(_asset_path(plan, asset.id, resource))
          if (actual != asset.sha256) _invalid(s"Summary-slides PDF Visual Page asset differs from declared resource: ${asset.id}")
          asset.id -> actual
        })
        val bindingdocument = CozyVisualPageBinding.load(binding, visual)
        VisualPageAuthority(source, catalog, binding, visual, bindingdocument, assets)
      case _ => _invalid(s"Summary-slides PDF contract is invalid: ${resource.id}")
    }
  }

  private def _validate_slide_ir(descriptor: CozyMedia.Descriptor, resource: CozyMedia.Resource, configuration: Config, document: CozyMediaSlideIr.Document): Unit = {
    if (document.knowledge != descriptor.knowledge.id || document.language != resource.language.getOrElse(""))
      _invalid(s"Summary-slides PDF Slide-IR knowledge or language differs: ${resource.id}")
    if (!document.assetIds.contains(configuration.infographic))
      _invalid(s"Summary-slides PDF Slide-IR must reference the configured infographic asset: ${resource.id}")
    _validate_asset_ids(descriptor, resource, document.assetIds)
  }

  private def _validate_visual_page(
    descriptor: CozyMedia.Descriptor,
    resource: CozyMedia.Resource,
    configuration: Config,
    source: Path,
    catalog: Path,
    binding: Path
  ): CozyVisualPage.ValidatedDocument = {
    val visual = CozyVisualPage.load(source, catalog)
    visual.document match {
      case _: CozyVisualPage.PageSet => ()
      case _ => _invalid(s"Summary-slides PDF Visual Page source must be cozy.visual-page-set.v1: ${resource.id}")
    }
    if (visual.document.pages.exists(page => page.knowledge != descriptor.knowledge.id || page.language != resource.language.getOrElse("")))
      _invalid(s"Summary-slides PDF Visual Page knowledge or language differs: ${resource.id}")
    if (!visual.document.pages.flatMap(_.assets.map(_.id)).contains(configuration.infographic))
      _invalid(s"Summary-slides PDF Visual Page must reference the configured infographic asset: ${resource.id}")
    _validate_asset_ids(descriptor, resource, visual.document.pages.flatMap(_.assets.map(_.id)).distinct)
    val bindingdocument = CozyVisualPageBinding.load(binding, visual)
    if (bindingdocument.profile != configuration.profile)
      _invalid(s"Summary-slides PDF Visual Page binding profile differs: ${resource.id}")
    visual
  }

  private def _validate_asset_ids(descriptor: CozyMedia.Descriptor, resource: CozyMedia.Resource, ids: Vector[String]): Unit =
    ids.foreach { id =>
      val dependency = descriptor.resources.find(_.id == id).getOrElse(_invalid(s"Summary-slides PDF asset is not a declared resource: $id"))
      if (dependency.id == resource.id || Set("presentation", "summary-slides-pdf").contains(dependency.build))
        _invalid(s"Summary-slides PDF asset must be a non-presentation dependency: $id")
    }

  private def _validate_dependencies(article: CozyMedia.Resource, infographic: CozyMedia.Resource, resource: CozyMedia.Resource): Unit = {
    if (article.id == resource.id || infographic.id == resource.id || article.build == "presentation" || infographic.build == "presentation")
      _invalid(s"Summary-slides PDF dependencies must be non-presentation resources: ${resource.id}")
    if (!Set("document", "pdf").contains(article.kind) || article.articleMedia.map(_.role) != Some("article_pdf"))
      _invalid(s"Summary-slides PDF articlePdf dependency must be an article_pdf document resource: ${resource.id}")
    if (!Set("image", "infographic").contains(infographic.kind) || infographic.articleMedia.map(_.role) != Some("infographic"))
      _invalid(s"Summary-slides PDF infographic dependency must be an infographic resource: ${resource.id}")
    if (infographic.language.exists(_ != resource.language.getOrElse("")))
      _invalid(s"Summary-slides PDF infographic dependency language must match: ${resource.id}")
  }

  private def _renderer_command(
    plan: CozyMedia.Plan,
    resolved: CozyMedia.ResolvedResource,
    configuration: Config,
    authority: Authority,
    template: Path,
    staged: Path,
    images: Path,
    montage: Path,
    manifest: Path,
    pptx: Option[Path]
  ): Vector[String] = {
    val profile = _profile(plan, configuration, resolved)
    val source = authority match {
      case _: SlideIrAuthority => Vector("--slide-ir", authority._source.toString)
      case visual: VisualPageAuthority => Vector("--visual-page-set", visual.source.toString, "--catalog", visual.catalog.toString, "--binding", visual.binding.toString)
    }
    profile.renderer.command ++ Vector("render", "--media", plan.descriptorFile.toString, "--target", resolved.resource.id, "--profile", configuration.profile) ++
      source ++ Vector("--template", template.toString, "--pdf", staged.toString, "--slide-images", images.toString, "--montage", montage.toString, "--manifest", manifest.toString) ++
      pptx.toVector.flatMap(value => Vector("--pptx", value.toString))
  }

  private def _verify(plan: CozyMedia.Plan, resolved: CozyMedia.ResolvedResource, authority: Authority, template: Path, pdf: Path): Unit = {
    val configuration = _configuration(resolved)
    val manifest = _direct_input(plan.descriptorRoot, configuration.rendererManifest, s"Summary-slides PDF rendererManifest ${resolved.resource.id}")
    val renderer = _renderer_manifest(manifest)
    val pagecount = _pdf_page_count(pdf)
    authority match {
      case slide: SlideIrAuthority => _verify_slide_ir_manifest(plan, resolved, configuration, slide, template, pdf, pagecount, renderer)
      case visual: VisualPageAuthority => _verify_visual_page_manifest(plan, resolved, configuration, visual, template, pdf, pagecount, renderer)
    }
  }

  private def _verify_slide_ir_manifest(plan: CozyMedia.Plan, resolved: CozyMedia.ResolvedResource, configuration: Config, authority: SlideIrAuthority, template: Path, pdf: Path, pagecount: Int, renderer: JsonObject): Unit = {
    _exact_ordered_keys(renderer, Vector("schema", "target", "profile", "renderer", "slideIrSha256", "templateSha256", "pdf", "slides", "montage") ++ configuration.pptx.map(_ => "pptx"), "Summary-slides PDF Slide-IR renderer manifest")
    _verify_renderer_identity(plan, resolved, configuration, renderer, _slide_ir_schema)
    if (_sha256_value(renderer, "slideIrSha256", "Summary-slides PDF Slide-IR renderer manifest") != _sha256(authority.source) || _sha256_value(renderer, "templateSha256", "Summary-slides PDF Slide-IR renderer manifest") != _sha256(template))
      _invalid("Summary-slides PDF Slide-IR renderer manifest input hashes differ")
    val pdfhash = _verify_pdf(renderer, pdf, pagecount, authority.document.slides.size)
    val slides = _array(renderer, "slides", "Summary-slides PDF Slide-IR renderer manifest")
    if (slides.map(value => _string(_object(value, "Summary-slides PDF Slide-IR slide"), "id", "Summary-slides PDF Slide-IR slide")) != authority._page_ids)
      _invalid("Summary-slides PDF Slide-IR renderer slide order differs")
    slides.zip(authority.document.slides.zip(authority._assets_by_page)).foreach { case (value, (slide, assets)) =>
      val entry = _object(value, "Summary-slides PDF Slide-IR slide")
      _exact_keys(entry, Set("id", "title", "path", "sha256", "pdfSha256", "assets"), "Summary-slides PDF Slide-IR slide")
      if (_string(entry, "title", "Summary-slides PDF Slide-IR slide") != slide.title || _sha256_value(entry, "pdfSha256", "Summary-slides PDF Slide-IR slide") != pdfhash)
        _invalid(s"Summary-slides PDF Slide-IR slide differs: ${slide.id}")
      _verify_slide_image(plan, resolved, configuration, entry, slide.id)
      _verify_assets(entry, assets, "Summary-slides PDF Slide-IR slide")
    }
    _verify_montage(plan, resolved, configuration, renderer, pdfhash)
    _verify_pptx(plan, resolved, configuration, renderer)
  }

  private def _verify_visual_page_manifest(plan: CozyMedia.Plan, resolved: CozyMedia.ResolvedResource, configuration: Config, authority: VisualPageAuthority, template: Path, pdf: Path, pagecount: Int, renderer: JsonObject): Unit = {
    _exact_ordered_keys(renderer, Vector("schema", "target", "profile", "renderer", "visualPageSetSha256", "catalogSha256", "bindingSha256", "templateSha256", "pdf", "slides", "montage") ++ configuration.pptx.map(_ => "pptx"), "Summary-slides PDF Visual Page renderer manifest")
    _verify_renderer_identity(plan, resolved, configuration, renderer, _visual_page_schema)
    if (_sha256_value(renderer, "visualPageSetSha256", "Summary-slides PDF Visual Page renderer manifest") != _semantic_sha256(authority.document.documentIdentity) ||
        _sha256_value(renderer, "catalogSha256", "Summary-slides PDF Visual Page renderer manifest") != _semantic_sha256(authority.document.catalogIdentity) ||
        _sha256_value(renderer, "bindingSha256", "Summary-slides PDF Visual Page renderer manifest") != _semantic_sha256(authority.bindingdocument.bindingIdentity) ||
        _sha256_value(renderer, "templateSha256", "Summary-slides PDF Visual Page renderer manifest") != _sha256(template))
      _invalid("Summary-slides PDF Visual Page renderer manifest input hashes differ")
    val pdfhash = _verify_pdf(renderer, pdf, pagecount, authority.document.document.pages.size)
    val slides = _array(renderer, "slides", "Summary-slides PDF Visual Page renderer manifest")
    if (slides.map(value => _string(_object(value, "Summary-slides PDF Visual Page slide"), "id", "Summary-slides PDF Visual Page slide")) != authority._page_ids)
      _invalid("Summary-slides PDF Visual Page renderer page order differs")
    slides.zip(authority.document.document.pages.zip(authority._assets_by_page)).foreach { case (value, (page, assets)) =>
      val entry = _object(value, "Summary-slides PDF Visual Page slide")
      _exact_keys(entry, Set("id", "path", "sha256", "pdfSha256", "assets"), "Summary-slides PDF Visual Page slide")
      if (_sha256_value(entry, "pdfSha256", "Summary-slides PDF Visual Page slide") != pdfhash)
        _invalid(s"Summary-slides PDF Visual Page slide differs: ${page.id}")
      _verify_slide_image(plan, resolved, configuration, entry, page.id)
      _verify_assets(entry, assets, "Summary-slides PDF Visual Page slide")
    }
    _verify_montage(plan, resolved, configuration, renderer, pdfhash)
    _verify_pptx(plan, resolved, configuration, renderer)
  }

  private def _verify_renderer_identity(plan: CozyMedia.Plan, resolved: CozyMedia.ResolvedResource, configuration: Config, renderer: JsonObject, schema: String): Unit = {
    if (_string(renderer, "schema", "Summary-slides PDF renderer manifest") != schema || _string(renderer, "target", "Summary-slides PDF renderer manifest") != resolved.resource.id || _string(renderer, "profile", "Summary-slides PDF renderer manifest") != configuration.profile)
      _invalid("Summary-slides PDF renderer manifest identity differs from descriptor")
    val expected = _profile(plan, configuration, resolved).renderer
    val actual = _object(renderer("renderer").getOrElse(_invalid("Summary-slides PDF renderer identity is missing")), "Summary-slides PDF renderer identity")
    _exact_keys(actual, Set("name", "version"), "Summary-slides PDF renderer identity")
    if (_string(actual, "name", "Summary-slides PDF renderer identity") != expected.name || _string(actual, "version", "Summary-slides PDF renderer identity") != expected.version)
      _invalid("Summary-slides PDF renderer identity differs from profile")
  }

  private def _verify_pdf(renderer: JsonObject, pdf: Path, pagecount: Int, expectedcount: Int): String = {
    val value = _object(renderer("pdf").getOrElse(_invalid("Summary-slides PDF renderer PDF evidence is missing")), "Summary-slides PDF renderer PDF")
    _exact_ordered_keys(value, Vector("sha256", "pageCount"), "Summary-slides PDF renderer PDF")
    val hash = _sha256_value(value, "sha256", "Summary-slides PDF renderer PDF")
    if (hash != _sha256(pdf) || _int(value, "pageCount", "Summary-slides PDF renderer PDF") != pagecount || pagecount != expectedcount)
      _invalid("Summary-slides PDF renderer PDF hash or page count differs")
    hash
  }

  private def _verify_slide_image(plan: CozyMedia.Plan, resolved: CozyMedia.ResolvedResource, configuration: Config, entry: JsonObject, id: String): Unit = {
    val images = _output_path(plan.descriptorRoot, configuration.slideImages, s"Summary-slides PDF slideImages ${resolved.resource.id}")
    val path = _serialized_path(plan, _string(entry, "path", "Summary-slides PDF renderer slide"), "Summary-slides PDF renderer slide path")
    if (!path.startsWith(images) || path == images || !_png(path) || _sha256_value(entry, "sha256", "Summary-slides PDF renderer slide") != _sha256(path))
      _invalid(s"Summary-slides PDF slide image differs: $id")
  }

  private def _verify_assets(entry: JsonObject, expected: Vector[(String, String)], label: String): Unit = {
    val assets = _array(entry, "assets", label).map { value =>
      val asset = _object(value, s"$label asset")
      _exact_keys(asset, Set("id", "sha256"), s"$label asset")
      _string(asset, "id", s"$label asset") -> _sha256_value(asset, "sha256", s"$label asset")
    }
    if (assets != expected || assets.map(_._1).distinct.size != assets.size)
      _invalid(s"$label assets differ")
  }

  private def _verify_montage(plan: CozyMedia.Plan, resolved: CozyMedia.ResolvedResource, configuration: Config, renderer: JsonObject, pdfhash: String): Unit = {
    val value = _object(renderer("montage").getOrElse(_invalid("Summary-slides PDF renderer montage is missing")), "Summary-slides PDF renderer montage")
    _exact_keys(value, Set("path", "sha256", "pdfSha256"), "Summary-slides PDF renderer montage")
    val path = _serialized_path(plan, _string(value, "path", "Summary-slides PDF renderer montage"), "Summary-slides PDF renderer montage path")
    val configured = _output_path(plan.descriptorRoot, configuration.montage, s"Summary-slides PDF montage ${resolved.resource.id}")
    if (path != configured || !_png(path) || _sha256_value(value, "sha256", "Summary-slides PDF renderer montage") != _sha256(path) || _sha256_value(value, "pdfSha256", "Summary-slides PDF renderer montage") != pdfhash)
      _invalid("Summary-slides PDF renderer montage differs")
  }

  private def _verify_pptx(plan: CozyMedia.Plan, resolved: CozyMedia.ResolvedResource, configuration: Config, renderer: JsonObject): Unit =
    configuration.pptx match {
      case Some(value) =>
        val entry = _object(renderer("pptx").getOrElse(_invalid("Summary-slides PDF renderer PPTX is missing")), "Summary-slides PDF renderer PPTX")
        _exact_keys(entry, Set("path", "sha256"), "Summary-slides PDF renderer PPTX")
        val path = _serialized_path(plan, _string(entry, "path", "Summary-slides PDF renderer PPTX"), "Summary-slides PDF renderer PPTX path")
        val configured = _output_path(plan.descriptorRoot, value, s"Summary-slides PDF PPTX ${resolved.resource.id}")
        if (path != configured || !_direct_regular_file(path) || _sha256_value(entry, "sha256", "Summary-slides PDF renderer PPTX") != _sha256(path))
          _invalid("Summary-slides PDF renderer PPTX differs")
      case None => if (renderer.contains("pptx")) _invalid("Summary-slides PDF renderer PPTX must not be present without descriptor configuration")
    }

  private def _input_paths(plan: CozyMedia.Plan, resolved: CozyMedia.ResolvedResource, authority: Authority, template: Path): Vector[Path] = {
    val configuration = _configuration(resolved)
    val dependencies = Vector(_dependency(plan, configuration.articlePdf, resolved), _dependency(plan, configuration.infographic, resolved)).flatMap(_dependency_input_paths)
    val authorityinputs = authority match {
      case _: SlideIrAuthority => Vector(authority._source)
      case visual: VisualPageAuthority =>
        Vector(visual.source, visual.catalog, visual.binding) ++
          _visual_page_consumed_paths(plan.descriptorRoot, visual.source, visual.document, s"Summary-slides PDF Visual Page ${resolved.resource.id}").map(_._2)
    }
    val assetdependencies = authority._assets_by_page.flatten.map(_._1).distinct.flatMap { id =>
      _dependency_input_paths(_dependency(plan, id, resolved))
    }
    (Vector(plan.descriptorFile, template) ++ authorityinputs ++ dependencies ++ assetdependencies).distinct
  }

  private def _snapshot(paths: Vector[Path]): Vector[(Path, String)] =
    paths.sortBy(_.toString).map(path => path -> _sha256(_require_direct(path, "Summary-slides PDF input")))

  private def _configuration(resolved: CozyMedia.ResolvedResource): Config =
    resolved.resource.summarySlidesPdf.getOrElse(_invalid(s"Summary-slides PDF resource requires configuration: ${resolved.resource.id}"))

  private def _profile(plan: CozyMedia.Plan, configuration: Config, resolved: CozyMedia.ResolvedResource): CozyMediaPresentation.ProfileConfig =
    plan.descriptor.profiles.get(configuration.profile).flatMap(_.presentation).getOrElse(_invalid(s"Summary-slides PDF presentation profile is not configured: ${resolved.resource.id}"))

  private def _dependency(plan: CozyMedia.Plan, id: String, resolved: CozyMedia.ResolvedResource): CozyMedia.ResolvedResource =
    plan.resources.find(_.resource.id == id).filter(_.resource.id != resolved.resource.id).getOrElse(_invalid(s"Summary-slides PDF dependency is not declared: $id"))

  private def _dependency(descriptor: CozyMedia.Descriptor, id: String, resource: CozyMedia.Resource): CozyMedia.Resource =
    descriptor.resources.find(_.id == id).filter(_.id != resource.id).getOrElse(_invalid(s"Summary-slides PDF dependency is not declared: $id"))

  private def _dependency_paths(root: Path, dependency: CozyMedia.Resource, label: String): Vector[(String, Path)] = {
    val paths = Vector(
      dependency.source.map(value => s"$label source" -> _path(root, value, s"$label source")),
      dependency.output.map(value => s"$label output" -> _path(root, value, s"$label output"))
    ).flatten
    if (paths.isEmpty) _invalid(s"$label has no output or source")
    paths
  }

  private def _dependency_input_paths(dependency: CozyMedia.ResolvedResource): Vector[Path] =
    Vector(dependency.source, dependency.output).flatten

  private def _visual_page_consumed_paths(root: Path, source: Path, document: CozyVisualPage.ValidatedDocument, label: String): Vector[(String, Path)] = {
    val sourceroot = Option(source.getParent).getOrElse(_invalid(s"$label source requires a parent"))
    document.document.pages.zipWithIndex.flatMap { case (page, pageindex) =>
      page.assets.map(asset => s"$label page[$pageindex] asset ${asset.id}" -> _visual_page_path(root, sourceroot, asset.path, s"$label page[$pageindex] asset ${asset.id}")) ++
        page.sources.map(binding => s"$label page[$pageindex] source-binding ${binding.id}" -> _visual_page_path(root, sourceroot, binding.path, s"$label page[$pageindex] source-binding ${binding.id}"))
    }
  }

  private def _visual_page_path(root: Path, sourceroot: Path, value: String, label: String): Path =
    _require_direct(_descriptor_relative_path(root, sourceroot.resolve(value).normalize(), label), label)

  private def _descriptor_relative_path(root: Path, path: Path, label: String): Path = {
    val descriptorroot = root.toAbsolutePath.normalize()
    val normalized = path.toAbsolutePath.normalize()
    if (!normalized.startsWith(descriptorroot)) _invalid(s"$label escapes descriptor root")
    val relative = descriptorroot.relativize(normalized).toString.replace('\\', '/')
    _path(descriptorroot, relative, label)
  }

  private def _asset_path(plan: CozyMedia.Plan, id: String, resource: CozyMedia.Resource): Path = {
    val dependency = _dependency(plan, id, plan.resources.find(_.resource.id == resource.id).getOrElse(_invalid(s"Summary-slides PDF resource is not resolved: ${resource.id}")))
    _require_direct(_output(dependency), s"Summary-slides PDF asset $id")
  }

  private def _output(resolved: CozyMedia.ResolvedResource): Path =
    resolved.output.orElse(if (resolved.resource.build == "prebuilt") resolved.source else None).getOrElse(_invalid(s"Summary-slides PDF dependency has no output: ${resolved.resource.id}"))

  private def _renderer_manifest(path: Path): JsonObject =
    parse(Files.readString(path, StandardCharsets.UTF_8)).fold(_ => _invalid("Summary-slides PDF renderer manifest must be JSON"), _.asObject.getOrElse(_invalid("Summary-slides PDF renderer manifest must be an object")))

  private def _pdf_page_count(path: Path): Int = {
    if (!_direct_regular_file(path) || !_has_header(path, _pdf_header))
      _invalid(s"Summary-slides PDF renderer did not create a direct regular PDF output: $path")
    val document = try PDDocument.load(path.toFile) catch { case NonFatal(_) => _invalid(s"Summary-slides PDF output cannot be parsed by PDFBox: $path") }
    try document.getNumberOfPages
    finally document.close()
  }

  private def _has_header(path: Path, header: Array[Byte]): Boolean = {
    val stream = Files.newInputStream(path)
    try {
      val bytes = new Array[Byte](header.length)
      stream.read(bytes) == header.length && bytes.sameElements(header)
    } finally stream.close()
  }

  private def _png(path: Path): Boolean =
    _direct_regular_file(path) && _has_header(path, _png_header)

  private def _replace(source: Path, target: Path): Unit =
    try Files.move(source, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
    catch { case _: AtomicMoveNotSupportedException => _invalid(s"Summary-slides PDF requires same-parent ATOMIC_MOVE: $target") }

  private def _path(root: Path, value: String, label: String): Path = {
    val exact = _identity(value, label)
    if (exact.exists(character => Character.isISOControl(character)) || exact.contains('\\') || exact.contains(':') || exact.startsWith("/") || exact.split('/').exists(segment => segment.isEmpty || segment == "." || segment == ".."))
      _invalid(s"$label must be a safe descriptor-relative forward-slash path")
    val relative = try Path.of(exact) catch { case NonFatal(_) => _invalid(s"$label is not a valid path") }
    if (relative.isAbsolute || relative.normalize().toString.replace('\\', '/') != exact)
      _invalid(s"$label must use normalized descriptor-relative spelling")
    val path = root.resolve(relative).normalize()
    if (!path.startsWith(root)) _invalid(s"$label escapes descriptor root")
    _check_no_symlink_ancestors(root, relative, label)
    path
  }

  private def _direct_input(root: Path, value: String, label: String): Path =
    _require_direct(_path(root, value, label), label)

  private def _output_path(root: Path, value: String, label: String): Path = {
    val path = _path(root, value, label)
    if (Files.exists(path, LinkOption.NOFOLLOW_LINKS) && !_direct_regular_file(path) && !Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS))
      _invalid(s"$label must not be a symbolic link or special file")
    path
  }

  private def _serialized_path(plan: CozyMedia.Plan, value: String, label: String): Path =
    _require_direct(_path(plan.descriptorRoot, value, label), label)

  private def _require_output_target(plan: CozyMedia.Plan, path: Path, label: String): Unit = {
    _path(plan.descriptorRoot, _relative(plan, path), label)
    if (Files.exists(path, LinkOption.NOFOLLOW_LINKS) && !_direct_regular_file(path))
      _invalid(s"$label must be a direct regular file when it exists")
  }

  private def _prepare_output_parent(path: Path): Unit = {
    val parent = Option(path.getParent).getOrElse(_invalid(s"Summary-slides PDF output requires a parent: $path"))
    Files.createDirectories(parent)
    var current = path.getRoot
    val iterator = parent.iterator()
    while (iterator.hasNext) {
      val part = iterator.next()
      current = current.resolve(part)
      if (Files.isSymbolicLink(current)) _invalid(s"Summary-slides PDF output parent must not be a symlink: $current")
    }
  }

  private def _check_no_symlink_ancestors(root: Path, relative: Path, label: String): Unit = {
    var current = root
    val iterator = relative.iterator()
    while (iterator.hasNext) {
      current = current.resolve(iterator.next())
      if (Files.isSymbolicLink(current)) _invalid(s"$label must not resolve through a symlinked ancestor")
    }
  }

  private def _relative(plan: CozyMedia.Plan, path: Path): String = {
    val relative = plan.descriptorRoot.relativize(path.toAbsolutePath.normalize()).toString.replace('\\', '/')
    if (relative.isEmpty || relative.startsWith("../") || relative == "..") _invalid("Summary-slides PDF path escapes descriptor root")
    relative
  }

  private def _require_direct(path: Path, label: String): Path =
    if (_direct_regular_file(path)) path else _invalid(s"$label must be an existing direct regular non-symlink file: $path")

  private def _direct_regular_file(path: Path): Boolean =
    path != null && !Files.isSymbolicLink(path) && Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)

  private def _reject_overlap(values: Vector[(String, Path)], label: String): Unit =
    values.combinations(2).foreach { pair =>
      val left = pair.head
      val right = pair(1)
      if (_overlap(left._2, right._2)) _invalid(s"$label overlap: ${left._1} and ${right._1}")
    }

  private def _reject_generated_overlap(generated: Vector[(String, Path)], protectedpaths: Vector[(String, Path)], label: String): Unit =
    generated.foreach { generatedvalue =>
      protectedpaths.foreach { protectedvalue =>
        if (_overlap(generatedvalue._2, protectedvalue._2))
          _invalid(s"$label overlaps input: ${generatedvalue._1} and ${protectedvalue._1}")
      }
    }

  private def _overlap(left: Path, right: Path): Boolean =
    left == right || left.startsWith(right) || right.startsWith(left)

  private def _validate_renderer(value: CozyMediaPresentation.RendererConfig, label: String): Unit = {
    _identity(value.name, s"$label renderer name")
    _identity(value.version, s"$label renderer version")
    if (value.command.isEmpty || value.command.exists(token => token == null || token.isEmpty || token != token.trim))
      _invalid(s"$label renderer command must be non-empty exact argv tokens")
  }

  private def _sha256(path: Path): String = {
    val digest = MessageDigest.getInstance("SHA-256")
    val stream = Files.newInputStream(path)
    try {
      val buffer = new Array[Byte](8192)
      var size = stream.read(buffer)
      while (size >= 0) {
        if (size > 0) digest.update(buffer, 0, size)
        size = stream.read(buffer)
      }
    } finally stream.close()
    digest.digest().map(x => f"${x & 0xff}%02x").mkString
  }

  private def _semantic_sha256(value: String): String =
    value match {
      case hash if hash.matches("sha256:[0-9a-f]{64}") => hash.drop("sha256:".length)
      case _ => _invalid("Summary-slides PDF Visual Page canonical identity must be SHA-256")
    }

  private def _array(value: JsonObject, field: String, label: String): Vector[Json] =
    value(field).flatMap(_.asArray).getOrElse(_invalid(s"$label.$field must be an array")).toVector

  private def _object(value: Json, label: String): JsonObject =
    value.asObject.getOrElse(_invalid(s"$label must be an object"))

  private def _string(value: JsonObject, field: String, label: String): String =
    value(field).flatMap(_.asString).filter(x => x.nonEmpty && x == x.trim).getOrElse(_invalid(s"$label.$field must be a non-empty exact string"))

  private def _int(value: JsonObject, field: String, label: String): Int =
    value(field).flatMap(_.asNumber).flatMap(_.toInt).filter(_ >= 1).getOrElse(_invalid(s"$label.$field must be a positive integer"))

  private def _sha256_value(value: JsonObject, field: String, label: String): String = {
    val hash = _string(value, field, label)
    if (hash.matches("[0-9a-f]{64}")) hash else _invalid(s"$label.$field must be SHA-256")
  }

  private def _exact_keys(value: JsonObject, expected: Set[String], label: String): Unit =
    if (value.keys.toSet != expected) _invalid(s"$label requires exactly: ${expected.toVector.sorted.mkString(", ")}")

  private def _exact_ordered_keys(value: JsonObject, expected: Vector[String], label: String): Unit =
    if (value.keys.toVector != expected) _invalid(s"$label requires exactly ordered fields: ${expected.mkString(", ")}")

  private def _identity(value: String, label: String): String =
    if (value == null || value.isEmpty || value != value.trim) _invalid(s"$label must be a non-empty exact trimmed string") else value

  private def _invalid(message: String): Nothing = RAISE.invalidArgumentFault(message)
}
