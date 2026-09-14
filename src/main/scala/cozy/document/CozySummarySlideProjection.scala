package cozy.document

import cozy.media.{CozyMedia, CozyMediaPdf, CozyMediaPresentation, CozyVisualPage, CozyVisualPageBinding}
import io.circe.{Json, JsonObject}
import java.io.StringReader
import java.nio.ByteBuffer
import java.nio.charset.{CodingErrorAction, StandardCharsets}
import java.nio.file.{AtomicMoveNotSupportedException, Files, LinkOption, Path, StandardCopyOption}
import java.security.MessageDigest
import org.goldenport.config.StructuredDocumentLoader
import org.goldenport.io.InputSource
import org.yaml.snakeyaml.{LoaderOptions, Yaml}
import org.yaml.snakeyaml.nodes.{MappingNode, ScalarNode}
import scala.collection.mutable
import scala.util.control.NonFatal

/*
 * @since   Sep. 13, 2026
 * @version Sep. 14, 2026
 * @author  ASAMI, Tomoharu
 */
private[cozy] object CozySummarySlideProjection {
  final case class InputProvenance(id: String, identity: String, path: String)
  final case class ProjectionProvenance(
    profile: InputProvenance,
    core: InputProvenance,
    document: InputProvenance,
    summary: InputProvenance,
    media: InputProvenance,
    catalog: InputProvenance,
    binding: InputProvenance,
    locale: String,
    mediaTarget: String
  )
  final case class Projection(
    visualPageSet: CozyVisualPage.ValidatedDocument,
    provenance: ProjectionProvenance,
    mediaDescriptorPath: Path
  )
  final case class WrittenProjection(projection: Projection, outputPath: Path)

  final case class ProjectionFault(code: String, path: String, reason: String)
    extends IllegalArgumentException(s"$code path=$path reason=$reason")

  private final case class InputRecord(id: String, identity: String, path: String)
  private final case class MediaRecord(id: String, identity: String, path: String, summaryslidespdf: String)
  private final case class CatalogRecord(id: String, revision: Int, identity: String, path: String)
  private final case class BindingRecord(id: String, identity: String, profile: String, path: String)
  private final case class MappingItem(diagramitemid: String, role: String)
  private final case class MappingEdge(diagramedgeid: String)
  private final case class Mapping(
    id: String,
    summaryunitid: String,
    logicalpattern: String,
    visualpattern: String,
    items: Vector[MappingItem],
    edges: Vector[MappingEdge],
    emphasisitem: Option[String],
    visualparameters: Vector[(String, Json)]
  )
  private final case class Profile(
    id: String,
    core: InputRecord,
    document: InputRecord,
    summary: InputRecord,
    locale: String,
    media: MediaRecord,
    catalog: CatalogRecord,
    binding: BindingRecord,
    pages: Vector[Mapping]
  )
  private final case class SelectedMedia(
    resource: CozyMedia.Resource,
    root: Path,
    asset: CozyVisualPage.Asset
  )
  private final case class EdgeResolution(
    edge: CozyDocumentDescriptionV2.DiagramEdge,
    fromitem: CozyDocumentDescriptionV2.DiagramItem,
    toitem: CozyDocumentDescriptionV2.DiagramItem,
    relationtype: String
  )
  private final case class LabelIndex(steps: Map[String, String], nodes: Map[String, String])

  private val _schema = "cozy.summary-slide-projection.v1"
  private val _root_fields = Vector("schema", "version", "id", "core", "document", "summary", "locale", "media", "catalog", "binding", "pages")
  private val _id_pattern = "[A-Za-z0-9][A-Za-z0-9._-]*".r
  private val _locale_pattern = "[A-Za-z]{2,8}(?:-[A-Za-z0-9]{1,8})*".r
  private val _identity_pattern = "sha256:[0-9a-f]{64}".r

  def project(projectRoot: Path, profilePath: Path): Projection = {
    val root = _project_root(projectRoot)
    val profilepath = _profile_path(root, profilePath)
    _prepare_projection(root, profilepath)
  }

  def write(projection: Projection, pageSetOutput: Path): WrittenProjection = {
    if (projection == null)
      _fail("SUMMARY_SLIDE_PROJECTION_REQUIRED", "projection", "validated projection is required")
    if (pageSetOutput == null)
      _fail("SUMMARY_SLIDE_PROJECTION_OUTPUT_REQUIRED", "pageSetOutput", "explicit PageSet output is required")
    _atomic_write(pageSetOutput, projection.visualPageSet.canonicalJson + "\n")
    WrittenProjection(projection, pageSetOutput)
  }

  private def _prepare_projection(root: Path, profilepath: Path): Projection = {
    val profilebytes = _read_bytes(profilepath, "profile")
    val profiledata = _profile_json(_decode_utf8(profilebytes, "$.profile"), profilepath)
    val profile = _profile(profiledata._1, profiledata._2)
    val corepath = _bound_file(root, profile.core, "core")
    val documentpath = _bound_file(root, profile.document, "document")
    val summarypath = _bound_file(root, profile.summary, "summary")
    val mediapath = _bound_file(root, InputRecord(profile.media.id, profile.media.identity, profile.media.path), "media")
    val catalogpath = _bound_file(root, InputRecord(profile.catalog.id, profile.catalog.identity, profile.catalog.path), "catalog")
    val bindingpath = _bound_file(root, InputRecord(profile.binding.id, profile.binding.identity, profile.binding.path), "binding")
    val summary = CozyDocumentDescriptionV2.loadSummary(corepath, documentpath, summarypath)
    _validate_source_bindings(profile, summary)
    val selectedmedia = _select_media(profile, _media_descriptor(mediapath), mediapath)
    _validate_catalog_profile(profile)
    val pages = _project_pages(profile, summary, selectedmedia, profilepath, corepath, documentpath, summarypath, mediapath)
    val pageset = CozyVisualPage.PageSet(profile.id, pages)
    val validated = _validate_page_set(pageset, catalogpath, selectedmedia.root)
    _validate_catalog_result(profile, validated)
    val binding = _load_binding(bindingpath, validated)
    _validate_binding_result(profile, binding)
    val projection = Projection(
      validated,
      ProjectionProvenance(
        InputProvenance(profile.id, _identity(profilebytes), _relative(selectedmedia.root, profilepath, "profile")),
        InputProvenance(profile.core.id, profile.core.identity, _relative(selectedmedia.root, corepath, "core")),
        InputProvenance(profile.document.id, profile.document.identity, _relative(selectedmedia.root, documentpath, "document")),
        InputProvenance(profile.summary.id, profile.summary.identity, _relative(selectedmedia.root, summarypath, "summary")),
        InputProvenance(profile.media.id, profile.media.identity, _relative(selectedmedia.root, mediapath, "media")),
        InputProvenance(profile.catalog.id, profile.catalog.identity, _relative(selectedmedia.root, catalogpath, "catalog")),
        InputProvenance(profile.binding.id, profile.binding.identity, _relative(selectedmedia.root, bindingpath, "binding")),
        profile.locale,
        selectedmedia.resource.id
      ),
      mediapath
    )
    projection
  }

  private def _project_root(value: Path): Path = {
    if (value == null) _fail("SUMMARY_SLIDE_PROJECTION_ROOT", "projectRoot", "project root is required")
    val root = try value.toAbsolutePath.normalize() catch { case NonFatal(_) => _fail("SUMMARY_SLIDE_PROJECTION_ROOT", "projectRoot", "project root is invalid") }
    if (Files.isSymbolicLink(root) || !Files.isDirectory(root, LinkOption.NOFOLLOW_LINKS))
      _fail("SUMMARY_SLIDE_PROJECTION_ROOT", "projectRoot", "project root must be an existing direct non-symlink directory")
    root
  }

  private def _profile_path(root: Path, value: Path): Path = {
    if (value == null || value.isAbsolute) _fail("SUMMARY_SLIDE_PROJECTION_PROFILE_PATH", "profilePath", "profile path must be a safe project-relative path")
    val relative = _safe_relative_path(value.toString, "profilePath")
    if (relative.getNameCount == 3 && relative.getName(0).toString == "content" &&
        relative.getName(2).toString == "summary.yaml" &&
        _locale_pattern.pattern.matcher(relative.getName(1).toString).matches())
      _fail("SUMMARY_SLIDE_PROJECTION_PROFILE_PATH", "profilePath", "content/<locale>/summary.yaml is reserved for the v2 Summary authority")
    val path = _safe_file(root, relative.toString, "profilePath")
    val name = path.getFileName.toString
    if (!Set(".yaml", ".yml").exists(name.endsWith))
      _fail("SUMMARY_SLIDE_PROJECTION_PROFILE_PATH", "profilePath", "profile must be a YAML file")
    path
  }

  private def _profile(value: Json, rootfields: Vector[String]): Profile = {
    val fields = _object(value, "$")
    _exact_fields(fields, _root_fields, "$", ordered = true, actualorder = Some(rootfields))
    if (_string(_field(fields, "schema", "$"), "$.schema") != _schema)
      _fail("SUMMARY_SLIDE_PROJECTION_SCHEMA", "$.schema", s"must be exactly ${_schema}")
    if (_integer(_field(fields, "version", "$"), "$.version") != 1)
      _fail("SUMMARY_SLIDE_PROJECTION_VERSION", "$.version", "must be integer 1")
    val pages = _array(_field(fields, "pages", "$"), "$.pages").zipWithIndex.map { case (value, index) => _mapping(value, s"$$.pages[$index]") }
    if (pages.isEmpty) _fail("SUMMARY_SLIDE_PROJECTION_PAGES", "$.pages", "must be a nonempty ordered array")
    _unique(pages.map(_.id), "$.pages", "page id")
    Profile(
      _token(_string(_field(fields, "id", "$"), "$.id"), "$.id"),
      _input_record(_field(fields, "core", "$"), "$.core"),
      _input_record(_field(fields, "document", "$"), "$.document"),
      _input_record(_field(fields, "summary", "$"), "$.summary"),
      _locale(_string(_field(fields, "locale", "$"), "$.locale"), "$.locale"),
      _media_record(_field(fields, "media", "$"), "$.media"),
      _catalog_record(_field(fields, "catalog", "$"), "$.catalog"),
      _binding_record(_field(fields, "binding", "$"), "$.binding"),
      pages
    )
  }

  private def _input_record(value: Json, path: String): InputRecord = {
    val fields = _object(value, path)
    _exact_fields(fields, Vector("id", "identity", "path"), path)
    InputRecord(
      _token(_string(_field(fields, "id", path), s"$path.id"), s"$path.id"),
      _identity_value(_string(_field(fields, "identity", path), s"$path.identity"), s"$path.identity"),
      _safe_path_value(_string(_field(fields, "path", path), s"$path.path"), s"$path.path")
    )
  }

  private def _media_record(value: Json, path: String): MediaRecord = {
    val fields = _object(value, path)
    _exact_fields(fields, Vector("id", "identity", "path", "summarySlidesPdf"), path)
    MediaRecord(
      _token(_string(_field(fields, "id", path), s"$path.id"), s"$path.id"),
      _identity_value(_string(_field(fields, "identity", path), s"$path.identity"), s"$path.identity"),
      _safe_path_value(_string(_field(fields, "path", path), s"$path.path"), s"$path.path"),
      _token(_string(_field(fields, "summarySlidesPdf", path), s"$path.summarySlidesPdf"), s"$path.summarySlidesPdf")
    )
  }

  private def _catalog_record(value: Json, path: String): CatalogRecord = {
    val fields = _object(value, path)
    _exact_fields(fields, Vector("id", "revision", "identity", "path"), path)
    CatalogRecord(
      _token(_string(_field(fields, "id", path), s"$path.id"), s"$path.id"),
      _positive_integer(_field(fields, "revision", path), s"$path.revision"),
      _identity_value(_string(_field(fields, "identity", path), s"$path.identity"), s"$path.identity"),
      _safe_path_value(_string(_field(fields, "path", path), s"$path.path"), s"$path.path")
    )
  }

  private def _binding_record(value: Json, path: String): BindingRecord = {
    val fields = _object(value, path)
    _exact_fields(fields, Vector("id", "identity", "profile", "path"), path)
    val profile = _token(_string(_field(fields, "profile", path), s"$path.profile"), s"$path.profile")
    if (profile != "business") _fail("SUMMARY_SLIDE_PROJECTION_BINDING", s"$path.profile", "must be exactly business")
    BindingRecord(
      _token(_string(_field(fields, "id", path), s"$path.id"), s"$path.id"),
      _identity_value(_string(_field(fields, "identity", path), s"$path.identity"), s"$path.identity"),
      profile,
      _safe_path_value(_string(_field(fields, "path", path), s"$path.path"), s"$path.path")
    )
  }

  private def _mapping(value: Json, path: String): Mapping = {
    val fields = _object(value, path)
    val expected = Vector("id", "summaryUnitId", "logicalPattern", "visualPattern", "items", "edges", "visualParameters")
    val actual = fields.keys.toVector
    if (!(actual.toSet == expected.toSet || actual.toSet == (expected.toSet + "emphasisItem")))
      _fail("SUMMARY_SLIDE_PROJECTION_MAPPING_FIELDS", path, "must contain exactly required mapping fields and optional emphasisItem")
    val items = _array(_field(fields, "items", path), s"$path.items").zipWithIndex.map { case (item, index) => _mapping_item(item, s"$path.items[$index]") }
    if (items.isEmpty) _fail("SUMMARY_SLIDE_PROJECTION_MAPPING_ITEMS", s"$path.items", "must be nonempty")
    _unique(items.map(_.diagramitemid), s"$path.items", "diagram item id")
    val edges = _array(_field(fields, "edges", path), s"$path.edges").zipWithIndex.map { case (item, index) => _mapping_edge(item, s"$path.edges[$index]") }
    _unique(edges.map(_.diagramedgeid), s"$path.edges", "diagram edge id")
    Mapping(
      _token(_string(_field(fields, "id", path), s"$path.id"), s"$path.id"),
      _token(_string(_field(fields, "summaryUnitId", path), s"$path.summaryUnitId"), s"$path.summaryUnitId"),
      _token(_string(_field(fields, "logicalPattern", path), s"$path.logicalPattern"), s"$path.logicalPattern"),
      _token(_string(_field(fields, "visualPattern", path), s"$path.visualPattern"), s"$path.visualPattern"),
      items,
      edges,
      fields("emphasisItem").map(value => _token(_string(value, s"$path.emphasisItem"), s"$path.emphasisItem")),
      _visual_parameters(_field(fields, "visualParameters", path), s"$path.visualParameters")
    )
  }

  private def _mapping_item(value: Json, path: String): MappingItem = {
    val fields = _object(value, path)
    _exact_fields(fields, Vector("diagramItemId", "role"), path)
    MappingItem(
      _token(_string(_field(fields, "diagramItemId", path), s"$path.diagramItemId"), s"$path.diagramItemId"),
      _token(_string(_field(fields, "role", path), s"$path.role"), s"$path.role")
    )
  }

  private def _mapping_edge(value: Json, path: String): MappingEdge = {
    val fields = _object(value, path)
    _exact_fields(fields, Vector("diagramEdgeId"), path)
    MappingEdge(_token(_string(_field(fields, "diagramEdgeId", path), s"$path.diagramEdgeId"), s"$path.diagramEdgeId"))
  }

  private def _visual_parameters(value: Json, path: String): Vector[(String, Json)] = {
    val fields = _object(value, path).toVector
    if (fields.exists(_._1 == "emphasisNode"))
      _fail("SUMMARY_SLIDE_PROJECTION_EMPHASIS", s"$path.emphasisNode", "must be derived by the projector and cannot be profile supplied")
    fields.map { case (name, parameter) =>
      _token(name, s"$path.$name")
      parameter.asString.orElse(parameter.asBoolean.map(_.toString)).getOrElse(
        _fail("SUMMARY_SLIDE_PROJECTION_PARAMETER", s"$path.$name", "must be a string or boolean")
      )
      name -> parameter
    }
  }

  private def _validate_source_bindings(profile: Profile, summary: CozyDocumentDescriptionV2.ValidatedSummary): Unit = {
    val document = summary.document
    if (summary.document.core.core.id != profile.core.id || document.coreIdentity != profile.core.identity)
      _fail("SUMMARY_SLIDE_PROJECTION_CORE", "$.core", "must equal the directly admitted v2 Core id and raw identity")
    if (document.description.id != profile.document.id || document.documentIdentity != profile.document.identity)
      _fail("SUMMARY_SLIDE_PROJECTION_DOCUMENT", "$.document", "must equal the directly admitted v2 Document id and raw identity")
    if (summary.description.id != profile.summary.id || summary.summaryIdentity != profile.summary.identity)
      _fail("SUMMARY_SLIDE_PROJECTION_SUMMARY", "$.summary", "must equal the directly admitted v2 Summary id and raw identity")
    if (summary.description.core.id != profile.core.id || summary.description.core.identity != profile.core.identity ||
        summary.description.document.id != profile.document.id || summary.description.document.identity != profile.document.identity)
      _fail("SUMMARY_SLIDE_PROJECTION_SUMMARY", "$.summary", "must retain the existing Core and Document bindings")
    if (document.description.locale != profile.locale || summary.description.locale != profile.locale)
      _fail("SUMMARY_SLIDE_PROJECTION_LOCALE", "$.locale", "must exactly equal the admitted v2 Document and Summary locale")
  }

  private def _media_descriptor(path: Path): CozyMedia.Descriptor =
    try StructuredDocumentLoader.loadDocument[CozyMedia.Descriptor](InputSource(path.toFile)).take
    catch { case NonFatal(_) => _fail("SUMMARY_SLIDE_PROJECTION_MEDIA", "$.media", "must be an existing readable media descriptor") }

  private def _select_media(profile: Profile, descriptor: CozyMedia.Descriptor, path: Path): SelectedMedia = {
    val root = Option(path.getParent).getOrElse(_fail("SUMMARY_SLIDE_PROJECTION_MEDIA", "$.media.path", "media descriptor must have a parent directory"))
    if (descriptor.schema != "cozy.media.v1" || descriptor.knowledge.id != profile.media.id || descriptor.knowledge.id != profile.core.id)
      _fail("SUMMARY_SLIDE_PROJECTION_MEDIA", "$.media", "media descriptor knowledge must exactly equal the profile and Core id")
    val resources = descriptor.resources.filter(_.id == profile.media.summaryslidespdf)
    if (resources.size != 1) _fail("SUMMARY_SLIDE_PROJECTION_MEDIA_TARGET", "$.media.summarySlidesPdf", "must select exactly one existing media resource")
    val resource = resources.head
    val configuration = resource.summarySlidesPdf.getOrElse(_fail("SUMMARY_SLIDE_PROJECTION_MEDIA_TARGET", "$.media.summarySlidesPdf", "must select a summarySlidesPdf resource"))
    if (resource.kind != "document" || resource.build != "summary-slides-pdf" || !resource.language.exists(Set("ja", "en")) || resource.language != Some(profile.locale) ||
        configuration.contract != "visual-page-v1" || configuration.profile != "business")
      _fail("SUMMARY_SLIDE_PROJECTION_MEDIA_TARGET", "$.media.summarySlidesPdf", "must select the matching visual-page-v1 business summary-slides-pdf resource")
    resource.articleMedia match {
      case Some(media @ CozyMedia.ResourceArticleMedia("summary_slides_pdf", _, Some("application/pdf"), _, _, _)) =>
        _validate_article_media(resource, media, "$.media.summarySlidesPdf.articleMedia")
      case _ => _fail("SUMMARY_SLIDE_PROJECTION_MEDIA_TARGET", "$.media.summarySlidesPdf", "target must declare articleMedia role summary_slides_pdf and application/pdf")
    }
    val presentationprofile = descriptor.profiles.get("business").flatMap(_.presentation).getOrElse(
      _fail("SUMMARY_SLIDE_PROJECTION_MEDIA_TARGET", "$.media.summarySlidesPdf", "business presentation profile is required")
    )
    _validate_renderer(presentationprofile.renderer, "$.media.summarySlidesPdf.profile.renderer")
    _safe_file(root, presentationprofile.template, "$.media.summarySlidesPdf.profile.template")
    if (configuration.catalog != Some(profile.catalog.path) || configuration.binding != Some(profile.binding.path))
      _fail("SUMMARY_SLIDE_PROJECTION_MEDIA_TARGET", "$.media.summarySlidesPdf", "catalog and binding must exactly equal the profile records")
    val articlematches = descriptor.resources.filter(_.id == configuration.articlePdf)
    if (articlematches.size != 1)
      _fail("SUMMARY_SLIDE_PROJECTION_MEDIA_TARGET", "$.media.summarySlidesPdf.articlePdf", "article-PDF dependency must resolve exactly once")
    val article = articlematches.head
    val infographicmatches = descriptor.resources.filter(_.id == configuration.infographic)
    if (infographicmatches.size != 1)
      _fail("SUMMARY_SLIDE_PROJECTION_MEDIA_ASSET", "$.media.summarySlidesPdf.infographic", "infographic dependency must resolve exactly once")
    val infographic = infographicmatches.head
    _validate_media_dependencies(resource, article, infographic, profile.locale)
    val asset = infographic
    val assetmedia = asset.articleMedia.filter(_.role == "infographic").getOrElse(
      _fail("SUMMARY_SLIDE_PROJECTION_MEDIA_ASSET", "$.media.summarySlidesPdf", "configured infographic must be an existing infographic resource")
    )
    val assetpath = _effective_media_file(root, asset, "$.media.summarySlidesPdf.infographic")
    val mediatype = assetmedia.mediaType.filter(value => value.nonEmpty && value == value.trim).getOrElse(
      _fail("SUMMARY_SLIDE_PROJECTION_MEDIA_ASSET", "$.media.summarySlidesPdf", "configured infographic media type is required")
    )
    SelectedMedia(
      resource,
      root,
      CozyVisualPage.Asset(asset.id, _relative(root, assetpath, "infographic"), mediatype, _sha256(assetpath))
    )
  }

  private def _validate_article_media(resource: CozyMedia.Resource, media: CozyMedia.ResourceArticleMedia, path: String): Unit =
    try CozyMediaPdf.validateArticleMedia(resource, media)
    catch {
      case NonFatal(error) =>
        _fail("SUMMARY_SLIDE_PROJECTION_MEDIA_TARGET", path, Option(error.getMessage).filter(_.nonEmpty).getOrElse("articleMedia contract is invalid"))
    }

  private def _validate_renderer(value: CozyMediaPresentation.RendererConfig, path: String): Unit = {
    _trimmed(value.name, s"$path.name")
    _trimmed(value.version, s"$path.version")
    if (value.command.isEmpty || value.command.exists(token => token == null || token.isEmpty || token != token.trim))
      _fail("SUMMARY_SLIDE_PROJECTION_MEDIA_TARGET", s"$path.command", "must be a nonempty exact argv sequence")
  }

  private def _validate_media_dependencies(
    resource: CozyMedia.Resource,
    article: CozyMedia.Resource,
    infographic: CozyMedia.Resource,
    locale: String
  ): Unit = {
    if (article.id == resource.id || infographic.id == resource.id || article.build == "presentation" || infographic.build == "presentation")
      _fail("SUMMARY_SLIDE_PROJECTION_MEDIA_TARGET", "$.media.summarySlidesPdf", "article-PDF and infographic dependencies must be non-presentation resources")
    if (!Set("document", "pdf").contains(article.kind))
      _fail("SUMMARY_SLIDE_PROJECTION_MEDIA_TARGET", "$.media.summarySlidesPdf.articlePdf", "article-PDF dependency must be a document or pdf resource")
    if (article.source.isEmpty && article.output.isEmpty)
      _fail("SUMMARY_SLIDE_PROJECTION_MEDIA_TARGET", "$.media.summarySlidesPdf.articlePdf", "article-PDF dependency must declare a source or output")
    article.articleMedia match {
      case Some(CozyMedia.ResourceArticleMedia("article_pdf", _, Some("application/pdf"), _, _, _)) => ()
      case _ => _fail("SUMMARY_SLIDE_PROJECTION_MEDIA_TARGET", "$.media.summarySlidesPdf.articlePdf", "article-PDF dependency must declare articleMedia role article_pdf and application/pdf")
    }
    if (!Set("image", "infographic").contains(infographic.kind))
      _fail("SUMMARY_SLIDE_PROJECTION_MEDIA_ASSET", "$.media.summarySlidesPdf.infographic", "infographic dependency must be an image or infographic resource")
    infographic.articleMedia match {
      case Some(CozyMedia.ResourceArticleMedia("infographic", _, _, _, _, _)) => ()
      case _ => _fail("SUMMARY_SLIDE_PROJECTION_MEDIA_ASSET", "$.media.summarySlidesPdf.infographic", "infographic dependency must declare articleMedia role infographic")
    }
    if (infographic.language.exists(_ != locale))
      _fail("SUMMARY_SLIDE_PROJECTION_MEDIA_ASSET", "$.media.summarySlidesPdf.infographic", "infographic dependency language must match the target")
  }

  private def _validate_catalog_profile(profile: Profile): Unit = {
    if (!Set(1, 2).contains(profile.catalog.revision))
      _fail("SUMMARY_SLIDE_PROJECTION_CATALOG", "$.catalog.revision", "must identify an admitted fixed Visual Page catalog revision")
    val catalog = CozyVisualPage.fixedCatalog(profile.catalog.revision)
    if (profile.catalog.id != catalog.id || profile.catalog.revision != catalog.revision)
      _fail("SUMMARY_SLIDE_PROJECTION_CATALOG", "$.catalog", "must identify the selected fixed Visual Page catalog")
  }

  private def _validate_page_set(pageset: CozyVisualPage.PageSet, catalogpath: Path, root: Path): CozyVisualPage.ValidatedDocument =
    try CozyVisualPage.validatePageSet(pageset, catalogpath, root)
    catch {
      case fault: CozyVisualPage.VisualPageFault => _fail(fault.code, fault.path, fault.reason)
    }

  private def _load_binding(bindingpath: Path, validated: CozyVisualPage.ValidatedDocument): CozyVisualPageBinding.ValidatedBinding =
    try CozyVisualPageBinding.load(bindingpath, validated)
    catch {
      case fault: CozyVisualPageBinding.BindingFault => _fail(fault.code, fault.path, fault.reason)
    }

  private def _validate_catalog_result(profile: Profile, validated: CozyVisualPage.ValidatedDocument): Unit =
    if (validated.catalog.id != profile.catalog.id || validated.catalog.revision != profile.catalog.revision)
      _fail("SUMMARY_SLIDE_PROJECTION_CATALOG", "$.catalog", "resolved catalog must exactly equal the profile id and revision")

  private def _validate_binding_result(profile: Profile, binding: CozyVisualPageBinding.ValidatedBinding): Unit =
    if (binding.id != profile.binding.id || binding.profile != profile.binding.profile)
      _fail("SUMMARY_SLIDE_PROJECTION_BINDING", "$.binding", "resolved binding must exactly equal the profile id and business profile")

  private def _project_pages(
    profile: Profile,
    summary: CozyDocumentDescriptionV2.ValidatedSummary,
    media: SelectedMedia,
    profilepath: Path,
    corepath: Path,
    documentpath: Path,
    summarypath: Path,
    mediapath: Path
  ): Vector[CozyVisualPage.Page] = {
    val units = summary.description.summary.units
    val byid = units.map(unit => unit.id -> unit).toMap
    val indices = profile.pages.map(mapping => byid.get(mapping.summaryunitid).map(unit => units.indexOf(unit)).getOrElse(
      _fail("SUMMARY_SLIDE_PROJECTION_MAPPING_UNIT", s"$$.pages.${mapping.id}.summaryUnitId", "must resolve exactly once in the bound Summary")
    ))
    if (indices != indices.sorted)
      _fail("SUMMARY_SLIDE_PROJECTION_MAPPING_ORDER", "$.pages", "mappings must remain contiguous in Summary-unit order")
    profile.pages.map(_.summaryunitid).distinct.foreach { unitid =>
      _validate_unit_coverage(unitid, profile.pages.filter(_.summaryunitid == unitid), byid(unitid), summary.document.core)
    }
    profile.pages.map { mapping =>
      _project_page(mapping, byid(mapping.summaryunitid), summary, media, profilepath, corepath, documentpath, summarypath, mediapath, profile.catalog.revision)
    }
  }

  private def _validate_unit_coverage(
    unitid: String,
    mappings: Vector[Mapping],
    unit: CozyDocumentDescriptionV2.SummaryUnit,
    core: CozyDocumentLogicTree.ValidatedCore
  ): Unit = {
    val diagram = unit.diagram.getOrElse(_fail("SUMMARY_SLIDE_PROJECTION_MAPPING_UNIT", s"$$.pages.$unitid", "selected Summary unit must have an authored diagram"))
    val items = diagram.items.map(item => item.id -> item).toMap
    val edges = diagram.edges.map(edge => edge.id -> edge).toMap
    val selectededges = mappings.flatMap(_.edges.map(_.diagramedgeid))
    _unique(selectededges, s"$$.pages.$unitid.edges", "diagram edge id")
    if (selectededges.toSet != edges.keySet || selectededges.size != edges.size)
      _fail("SUMMARY_SLIDE_PROJECTION_MAPPING_COVERAGE", s"$$.pages.$unitid.edges", "must cover every selected Summary-unit edge exactly once")
    if (selectededges != diagram.edges.map(_.id))
      _fail("SUMMARY_SLIDE_PROJECTION_MAPPING_ORDER", s"$$.pages.$unitid.edges", "must preserve the selected Summary-unit edge order")
    val resolutions = edges.values.toVector.map(edge => edge.id -> _resolve_edge(diagram, edge, core, s"$$.pages.$unitid.edges.${edge.id}")).toMap
    val endpointids = resolutions.values.flatMap(value => Vector(value.fromitem.id, value.toitem.id)).toSet
    mappings.foreach { mapping =>
      val selecteditems = mapping.items.map(_.diagramitemid)
      if (selecteditems.exists(!items.contains(_)))
        _fail("SUMMARY_SLIDE_PROJECTION_MAPPING_ITEM", s"$$.pages.${mapping.id}.items", "must resolve in the selected Summary-unit diagram")
      if (mapping.edges.exists(edge => !edges.contains(edge.diagramedgeid)))
        _fail("SUMMARY_SLIDE_PROJECTION_MAPPING_EDGE", s"$$.pages.${mapping.id}.edges", "must resolve in the selected Summary-unit diagram")
      if (mapping.logicalpattern == "standalone" || mapping.visualpattern == "standalone-card") {
        _validate_standalone_mapping(mapping, selecteditems, endpointids)
      } else {
        val pageresolutions = mapping.edges.map(edge => resolutions(edge.diagramedgeid))
        val required = pageresolutions.flatMap(value => Vector(value.fromitem.id, value.toitem.id)).toSet
        if (!required.subsetOf(selecteditems.toSet))
          _fail("SUMMARY_SLIDE_PROJECTION_MAPPING_ENDPOINT", s"$$.pages.${mapping.id}.items", "must contain both exact endpoints of every selected diagram edge")
        if (mapping.edges.nonEmpty) {
          val unrelated = selecteditems.filterNot(required.contains)
          if (unrelated.nonEmpty)
            _fail("SUMMARY_SLIDE_PROJECTION_MAPPING_ITEM", s"$$.pages.${mapping.id}.items", s"contains an item unrelated to its selected edges: ${unrelated.head}")
        }
      }
    }
    items.values.filterNot(item => endpointids.contains(item.id)).foreach { item =>
      val count = mappings.flatMap(_.items).count(_.diagramitemid == item.id)
      if (count != 1)
        _fail("SUMMARY_SLIDE_PROJECTION_MAPPING_ITEM", s"$$.pages.$unitid.items", s"edge-free diagram item must occur exactly once: ${item.id}")
    }
  }

  private def _validate_standalone_mapping(mapping: Mapping, selecteditems: Vector[String], endpointids: Set[String]): Unit = {
    if (mapping.logicalpattern != "standalone" || mapping.visualpattern != "standalone-card")
      _fail("SUMMARY_SLIDE_PROJECTION_MAPPING_PATTERN", s"$$.pages.${mapping.id}", "relationless mapping must select exactly standalone with standalone-card")
    if (selecteditems.size != 1 || mapping.items.head.role != "item")
      _fail("SUMMARY_SLIDE_PROJECTION_MAPPING_ITEM", s"$$.pages.${mapping.id}.items", "relationless mapping must contain exactly one item-role diagram item")
    if (mapping.edges.nonEmpty)
      _fail("SUMMARY_SLIDE_PROJECTION_MAPPING_EDGE", s"$$.pages.${mapping.id}.edges", "relationless mapping must not select a diagram edge")
    if (endpointids.contains(selecteditems.head))
      _fail("SUMMARY_SLIDE_PROJECTION_MAPPING_ITEM", s"$$.pages.${mapping.id}.items", "relationless mapping item must not occur in a declared diagram edge")
    if (mapping.emphasisitem.nonEmpty)
      _fail("SUMMARY_SLIDE_PROJECTION_EMPHASIS", s"$$.pages.${mapping.id}.emphasisItem", "relationless mapping must not select emphasis")
    if (mapping.visualparameters.nonEmpty)
      _fail("SUMMARY_SLIDE_PROJECTION_PARAMETER", s"$$.pages.${mapping.id}.visualParameters", "relationless mapping must not supply visual parameters")
  }

  private def _project_page(
    mapping: Mapping,
    unit: CozyDocumentDescriptionV2.SummaryUnit,
    summary: CozyDocumentDescriptionV2.ValidatedSummary,
    media: SelectedMedia,
    profilepath: Path,
    corepath: Path,
    documentpath: Path,
    summarypath: Path,
    mediapath: Path,
    catalogrevision: Int
  ): CozyVisualPage.Page = {
    val diagram = unit.diagram.getOrElse(_fail("SUMMARY_SLIDE_PROJECTION_MAPPING_UNIT", s"$$.pages.${mapping.id}", "selected Summary unit must have an authored diagram"))
    val items = diagram.items.map(item => item.id -> item).toMap
    val edges = diagram.edges.map(edge => edge.id -> edge).toMap
    val catalog = CozyVisualPage.fixedCatalog(catalogrevision)
    val pattern = catalog.logicalPatterns.find(_.id == mapping.logicalpattern).getOrElse(
      _fail("SUMMARY_SLIDE_PROJECTION_PATTERN", s"$$.pages.${mapping.id}.logicalPattern", "must be an existing catalog logical pattern")
    )
    val visualpattern = catalog.visualPatterns.find(_.id == mapping.visualpattern).getOrElse(
      _fail("SUMMARY_SLIDE_PROJECTION_PATTERN", s"$$.pages.${mapping.id}.visualPattern", "must be an existing catalog visual pattern")
    )
    if (!visualpattern.compatibleLogicalPatterns.contains(pattern.id))
      _fail("SUMMARY_SLIDE_PROJECTION_PATTERN", s"$$.pages.${mapping.id}.visualPattern", "must be compatible with the selected logical pattern")
    val resolutions = mapping.edges.map(edge => _resolve_edge(diagram, edges(edge.diagramedgeid), summary.document.core, s"$$.pages.${mapping.id}.edges.${edge.diagramedgeid}"))
    val mappeditems = mapping.items.map(item => item -> items(item.diagramitemid))
    _validate_roles(mapping, mappeditems, resolutions, pattern, summary.document.core)
    val labels = _labels(summary.document.description.labels)
    val nodes = mappeditems.map { case (mappingitem, item) =>
      val label = item.kind match {
        case "node" => labels.nodes.getOrElse(item.ref, _fail("SUMMARY_SLIDE_PROJECTION_LABEL", s"$$.pages.${mapping.id}.items.${item.id}", "must resolve one existing Document Node label"))
        case "step" => labels.steps.getOrElse(item.ref, _fail("SUMMARY_SLIDE_PROJECTION_LABEL", s"$$.pages.${mapping.id}.items.${item.id}", "must resolve one existing Document Step label"))
        case _ => _fail("SUMMARY_SLIDE_PROJECTION_MAPPING_ITEM", s"$$.pages.${mapping.id}.items.${item.id}", "must be a Node or Step diagram item")
      }
      CozyVisualPage.Node(item.id, mappingitem.role, label, Vector("core", "document", "summary"))
    }
    val relations = resolutions.map { value =>
      CozyVisualPage.Relation(value.edge.id, value.relationtype, value.fromitem.id, value.toitem.id, Vector("core", "summary"))
    }
    val visual = CozyVisualPage.Visual(mapping.visualpattern, _visual_parameters(mapping, unit, items, visualpattern))
    CozyVisualPage.Page(
      mapping.id,
      summary.document.core.core.id,
      summary.description.locale,
      CozyVisualPage.CatalogReference(catalog.id, catalog.revision),
      CozyVisualPage.Logical(mapping.logicalpattern, nodes, relations),
      visual,
      Vector(media.asset),
      Vector(
        CozyVisualPage.SourceBinding("core", _relative(media.root, corepath, "core")),
        CozyVisualPage.SourceBinding("document", _relative(media.root, documentpath, "document")),
        CozyVisualPage.SourceBinding("summary", _relative(media.root, summarypath, "summary")),
        CozyVisualPage.SourceBinding("profile", _relative(media.root, profilepath, "profile")),
        CozyVisualPage.SourceBinding("media", _relative(media.root, mediapath, "media"))
      )
    )
  }

  private def _validate_roles(
    mapping: Mapping,
    mappeditems: Vector[(MappingItem, CozyDocumentDescriptionV2.DiagramItem)],
    resolutions: Vector[EdgeResolution],
    pattern: CozyVisualPage.LogicalPattern,
    core: CozyDocumentLogicTree.ValidatedCore
  ): Unit = {
    if (mapping.logicalpattern == "standalone") {
      mappeditems.foreach { case (mapped, item) =>
        if (mapped.role != "item" || !Set("node", "step").contains(item.kind))
          _fail("SUMMARY_SLIDE_PROJECTION_ROLE", s"$$.pages.${mapping.id}.items.${item.id}", "relationless mapping item must retain the standalone item role")
      }
      return
    }
    val rules = pattern.relationRules.map(rule => rule.relation -> rule).toMap
    mappeditems.foreach { case (mapped, item) =>
      item.kind match {
        case "node" =>
          if (core.nodesById.get(item.ref).map(_.role) != Some(mapped.role))
            _fail("SUMMARY_SLIDE_PROJECTION_ROLE", s"$$.pages.${mapping.id}.items.${item.id}", "Node role must equal its Core role")
        case "step" =>
          val expected = resolutions.flatMap { resolution =>
            val rule = rules.getOrElse(resolution.relationtype,
              _fail("SUMMARY_SLIDE_PROJECTION_ROLE", s"$$.pages.${mapping.id}", "relation is not admitted by the selected logical pattern")
            )
            if (resolution.fromitem.id == item.id) rule.fromRoles
            else if (resolution.toitem.id == item.id) rule.toRoles
            else Vector.empty
          }
          if (expected.isEmpty || expected.exists(_ != mapped.role))
            _fail("SUMMARY_SLIDE_PROJECTION_ROLE", s"$$.pages.${mapping.id}.items.${item.id}", "Step role must be exactly resolved by its selected relation endpoint")
        case _ => _fail("SUMMARY_SLIDE_PROJECTION_ROLE", s"$$.pages.${mapping.id}.items.${item.id}", "must resolve a Node or Step")
      }
    }
  }

  private def _visual_parameters(
    mapping: Mapping,
    unit: CozyDocumentDescriptionV2.SummaryUnit,
    items: Map[String, CozyDocumentDescriptionV2.DiagramItem],
    pattern: CozyVisualPage.VisualPattern
  ): Vector[CozyVisualPage.VisualParameter] = {
    val parameters = mapping.visualparameters.map { case (name, value) =>
      value.asString.map(text => CozyVisualPage.VisualParameter(name, CozyVisualPage.StringParameter(text))).orElse(
        value.asBoolean.map(flag => CozyVisualPage.VisualParameter(name, CozyVisualPage.BooleanParameter(flag)))
      ).getOrElse(_fail("SUMMARY_SLIDE_PROJECTION_PARAMETER", s"$$.pages.${mapping.id}.visualParameters.$name", "must be a string or boolean"))
    }
    mapping.emphasisitem match {
      case Some(itemid) =>
        if (unit.diagram.flatMap(_.focusItem) != Some(itemid) || !mapping.items.exists(_.diagramitemid == itemid))
          _fail("SUMMARY_SLIDE_PROJECTION_EMPHASIS", s"$$.pages.${mapping.id}.emphasisItem", "must exactly equal the selected Summary focus item in this page")
        val item = items.getOrElse(itemid, _fail("SUMMARY_SLIDE_PROJECTION_EMPHASIS", s"$$.pages.${mapping.id}.emphasisItem", "must resolve in the selected Summary diagram"))
        if (item.kind == "node" && pattern.parameters.exists(_.name == "emphasisNode"))
          parameters :+ CozyVisualPage.VisualParameter("emphasisNode", CozyVisualPage.StringParameter(item.id))
        else parameters
      case None => parameters
    }
  }

  private def _resolve_edge(
    diagram: CozyDocumentDescriptionV2.Diagram,
    edge: CozyDocumentDescriptionV2.DiagramEdge,
    core: CozyDocumentLogicTree.ValidatedCore,
    path: String
  ): EdgeResolution = {
    val source = edge.kind match {
      case "relation" =>
        val relation = core.relationsById.getOrElse(edge.ref, _fail("SUMMARY_SLIDE_PROJECTION_EDGE", path, "must resolve the existing Core Relation"))
        ("node", relation.from, relation.to, relation.relationType)
      case "flow-transition" =>
        val transitions = core.depthFirstSteps.flatMap(step => step.flow.transitions.map(transition => transition.id -> transition)).toMap
        val transition = transitions.getOrElse(edge.ref, _fail("SUMMARY_SLIDE_PROJECTION_EDGE", path, "must resolve the existing Core Flow transition"))
        ("step", transition.fromStepId, transition.toStepId, transition.relationType)
      case _ => _fail("SUMMARY_SLIDE_PROJECTION_EDGE", path, "must resolve a Relation or Flow transition")
    }
    val endpoints = if (edge.direction == "forward") (source._2, source._3) else (source._3, source._2)
    val from = diagram.items.find(item => item.kind == source._1 && item.ref == endpoints._1).getOrElse(
      _fail("SUMMARY_SLIDE_PROJECTION_EDGE", path, "must retain the selected Summary forward or inverse source endpoint")
    )
    val to = diagram.items.find(item => item.kind == source._1 && item.ref == endpoints._2).getOrElse(
      _fail("SUMMARY_SLIDE_PROJECTION_EDGE", path, "must retain the selected Summary forward or inverse target endpoint")
    )
    EdgeResolution(edge, from, to, source._4)
  }

  private def _labels(value: CozyDocumentDescriptionV2.Labels): LabelIndex =
    LabelIndex(value.steps.map(item => item.stepRef -> item.text).toMap, value.nodes.map(item => item.nodeRef -> item.text).toMap)

  private def _bound_file(root: Path, record: InputRecord, label: String): Path = {
    val path = _safe_file(root, record.path, s"$$.$label.path")
    if (_sha256_identity(path) != record.identity)
      _fail("SUMMARY_SLIDE_PROJECTION_IDENTITY", s"$$.$label.identity", "must equal the direct raw-byte SHA-256 identity")
    path
  }

  private def _safe_file(root: Path, raw: String, label: String): Path = {
    val relative = _safe_relative_path(raw, label)
    val path = root.resolve(relative).normalize()
    if (!path.startsWith(root)) _fail("SUMMARY_SLIDE_PROJECTION_PATH", label, "must remain below the explicit project root")
    var cursor = root
    var index = 0
    while (index < relative.getNameCount) {
      cursor = cursor.resolve(relative.getName(index))
      if (Files.isSymbolicLink(cursor)) _fail("SUMMARY_SLIDE_PROJECTION_PATH", label, "symlinked ancestors and final files are not admitted")
      if (index < relative.getNameCount - 1 && (!Files.exists(cursor, LinkOption.NOFOLLOW_LINKS) || !Files.isDirectory(cursor, LinkOption.NOFOLLOW_LINKS)))
        _fail("SUMMARY_SLIDE_PROJECTION_PATH", label, "every parent must be an existing direct directory")
      index += 1
    }
    if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS))
      _fail("SUMMARY_SLIDE_PROJECTION_PATH", label, "must be an existing direct regular file")
    path
  }

  private def _safe_relative_path(raw: String, label: String): Path = {
    val value = _safe_path_value(raw, label)
    val path = try Path.of(value) catch { case NonFatal(_) => _fail("SUMMARY_SLIDE_PROJECTION_PATH", label, "path is invalid") }
    if (path.isAbsolute || path.normalize().toString != value || path.getNameCount == 0 || value == ".")
      _fail("SUMMARY_SLIDE_PROJECTION_PATH", label, "path must be normalized and project-relative")
    path
  }

  private def _safe_path_value(value: String, path: String): String = {
    if (value == null || value.isEmpty || value != value.trim || value.contains("\\") || value.exists(Character.isISOControl) ||
        value.startsWith("/") || value.matches("^[A-Za-z][A-Za-z0-9+.-]*:.*"))
      _fail("SUMMARY_SLIDE_PROJECTION_PATH", path, "must be a safe normalized project-relative POSIX path")
    value
  }

  private def _trimmed(value: String, path: String): String =
    if (value == null || value.isEmpty || value != value.trim)
      _fail("SUMMARY_SLIDE_PROJECTION_MEDIA_TARGET", path, "must be a non-empty exact string")
    else value

  private def _effective_media_file(root: Path, resource: CozyMedia.Resource, label: String): Path = {
    val raw = resource.output.orElse {
      if (resource.build == "prebuilt") resource.source else None
    }.getOrElse(_fail("SUMMARY_SLIDE_PROJECTION_MEDIA_ASSET", label, "effective infographic output is required"))
    _safe_file(root, raw, label)
  }

  private def _relative(root: Path, value: Path, label: String): String = {
    if (!value.startsWith(root)) _fail("SUMMARY_SLIDE_PROJECTION_PATH", label, "must be a safe path below the media descriptor project root")
    val relative = root.relativize(value).toString
    _safe_relative_path(relative, label)
    relative
  }

  private def _atomic_write(path: Path, value: String): Unit = {
    val parent = Option(path.getParent).getOrElse(_fail("SUMMARY_SLIDE_PROJECTION_OUTPUT", "output", "output must have a parent directory"))
    val temporary = try {
      Files.createDirectories(parent)
      Files.createTempFile(parent, ".cozy-summary-slide-projection-", ".tmp")
    }
    catch { case NonFatal(_) => _fail("SUMMARY_SLIDE_PROJECTION_OUTPUT", "output", "cannot create an atomic output staging file") }
    try {
      Files.write(temporary, value.getBytes(StandardCharsets.UTF_8))
      try Files.move(temporary, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
      catch { case _: AtomicMoveNotSupportedException => _fail("SUMMARY_SLIDE_PROJECTION_OUTPUT", "output", "atomic replacement is unavailable") }
    } catch {
      case fault: ProjectionFault => throw fault
      case NonFatal(_) => _fail("SUMMARY_SLIDE_PROJECTION_OUTPUT", "output", "cannot atomically replace the PageSet output")
    } finally {
      try Files.deleteIfExists(temporary) catch { case NonFatal(_) => () }
    }
  }

  private def _profile_json(text: String, path: Path): (Json, Vector[String]) =
    try {
      val options = new LoaderOptions()
      options.setAllowDuplicateKeys(false)
      options.setAllowRecursiveKeys(false)
      options.setMaxAliasesForCollections(0)
      val yaml = new Yaml(options)
      _reject_yaml_indirection(yaml.parse(new StringReader(text)), "$.profile")
      val rootfields = _yaml_root_fields(yaml.compose(new StringReader(text)), "$.profile")
      yaml.load(new StringReader(text))
      StructuredDocumentLoader.loadJson(InputSource(text, path.toUri)).take -> rootfields
    } catch {
      case fault: ProjectionFault => throw fault
      case NonFatal(_) => _fail("SUMMARY_SLIDE_PROJECTION_PROFILE", "$.profile", "must be a well-formed strict YAML document")
    }

  private def _yaml_root_fields(value: org.yaml.snakeyaml.nodes.Node, path: String): Vector[String] = value match {
    case mapping: MappingNode =>
      val fields = Vector.newBuilder[String]
      val tuples = mapping.getValue.iterator
      while (tuples.hasNext) {
        tuples.next().getKeyNode match {
          case key: ScalarNode => fields += key.getValue
          case _ => _fail("SUMMARY_SLIDE_PROJECTION_FIELDS", path, "root mapping keys must be scalar strings")
        }
      }
      fields.result()
    case _ => _fail("SUMMARY_SLIDE_PROJECTION_FIELDS", path, "root must be a mapping")
  }

  private def _reject_yaml_indirection(events: java.lang.Iterable[org.yaml.snakeyaml.events.Event], path: String): Unit = {
    val iterator = events.iterator
    while (iterator.hasNext) {
      iterator.next() match {
        case _: org.yaml.snakeyaml.events.AliasEvent => _fail("SUMMARY_SLIDE_PROJECTION_PROFILE", path, "YAML aliases are not admitted")
        case value: org.yaml.snakeyaml.events.NodeEvent if value.getAnchor != null => _fail("SUMMARY_SLIDE_PROJECTION_PROFILE", path, "YAML anchors are not admitted")
        case value: org.yaml.snakeyaml.events.ScalarEvent if value.getTag != null || value.getValue == "<<" => _fail("SUMMARY_SLIDE_PROJECTION_PROFILE", path, "YAML merge keys and explicit tags are not admitted")
        case value: org.yaml.snakeyaml.events.CollectionStartEvent if value.getTag != null => _fail("SUMMARY_SLIDE_PROJECTION_PROFILE", path, "YAML explicit tags are not admitted")
        case _ =>
      }
    }
  }

  private def _read_bytes(path: Path, label: String): Array[Byte] =
    try Files.readAllBytes(path) catch { case NonFatal(_) => _fail("SUMMARY_SLIDE_PROJECTION_READ", s"$$.$label", "cannot read direct source bytes") }

  private def _decode_utf8(bytes: Array[Byte], path: String): String =
    try StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString
    catch { case NonFatal(_) => _fail("SUMMARY_SLIDE_PROJECTION_UTF8", path, "must be valid UTF-8") }

  private def _sha256(path: Path): String = MessageDigest.getInstance("SHA-256").digest(_read_bytes(path, "source")).map(value => f"${value & 0xff}%02x").mkString
  private def _sha256_identity(path: Path): String = "sha256:" + _sha256(path)
  private def _identity(bytes: Array[Byte]): String = "sha256:" + MessageDigest.getInstance("SHA-256").digest(bytes).map(value => f"${value & 0xff}%02x").mkString

  private def _object(value: Json, path: String): JsonObject = value.asObject.getOrElse(_fail("SUMMARY_SLIDE_PROJECTION_TYPE", path, "must be an object"))
  private def _array(value: Json, path: String): Vector[Json] = value.asArray.map(_.toVector).getOrElse(_fail("SUMMARY_SLIDE_PROJECTION_TYPE", path, "must be an array"))
  private def _field(fields: JsonObject, name: String, path: String): Json = fields(name).getOrElse(_fail("SUMMARY_SLIDE_PROJECTION_FIELD", s"$path.$name", "required field is missing"))
  private def _string(value: Json, path: String): String = value.asString.getOrElse(_fail("SUMMARY_SLIDE_PROJECTION_TYPE", path, "must be a string"))
  private def _integer(value: Json, path: String): Int = value.asNumber.flatMap(_.toInt).getOrElse(_fail("SUMMARY_SLIDE_PROJECTION_TYPE", path, "must be an integer"))
  private def _positive_integer(value: Json, path: String): Int = {
    val number = _integer(value, path)
    if (number <= 0) _fail("SUMMARY_SLIDE_PROJECTION_RANGE", path, "must be a positive integer")
    number
  }
  private def _locale(value: String, path: String): String = {
    if (!_locale_pattern.pattern.matcher(value).matches()) _fail("SUMMARY_SLIDE_PROJECTION_LOCALE", path, "must be a declared BCP-47 language tag")
    value
  }
  private def _token(value: String, path: String): String = {
    if (value == null || value.isEmpty || value != value.trim || !_id_pattern.pattern.matcher(value).matches())
      _fail("SUMMARY_SLIDE_PROJECTION_ID", path, "must be a nonempty stable identity token")
    value
  }
  private def _identity_value(value: String, path: String): String = {
    if (!_identity_pattern.pattern.matcher(value).matches()) _fail("SUMMARY_SLIDE_PROJECTION_IDENTITY", path, "must be sha256:<64 lowercase hexadecimal characters>")
    value
  }
  private def _exact_fields(fields: JsonObject, expected: Vector[String], path: String, ordered: Boolean = false, actualorder: Option[Vector[String]] = None): Unit = {
    val actual = actualorder.getOrElse(fields.keys.toVector)
    if (actual.toSet != expected.toSet || (ordered && actual != expected))
      _fail("SUMMARY_SLIDE_PROJECTION_FIELDS", path, s"must contain${if (ordered) " in order" else " exactly"}: ${expected.mkString(", ")}")
  }
  private def _unique(values: Vector[String], path: String, label: String): Unit =
    values.groupBy(identity).collectFirst { case (value, duplicates) if duplicates.size > 1 => value }.foreach { value =>
      _fail("SUMMARY_SLIDE_PROJECTION_DUPLICATE", path, s"duplicate $label: $value")
    }
  private def _fail(code: String, path: String, reason: String): Nothing = throw ProjectionFault(code, path, reason)
}
