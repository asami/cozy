package cozy.publication

import java.net.URI
import java.nio.charset.StandardCharsets
import java.nio.file.{Files, LinkOption, Path}
import scala.util.control.NonFatal
import cozy.media.{CozyMedia, CozyMediaWarehouse}
import org.goldenport.RAISE
import org.goldenport.config.StructuredDocumentLoader
import org.goldenport.io.InputSource
import org.smartdox.metadata.PublishMetadata.{ImageReference, PdfDocumentReference, VideoPresentation, VideoReference, VideoStatus}
import play.api.libs.json.{JsObject, JsString, Json}

/*
 * @since   Oct.  6, 2026
 * @version Oct.  6, 2026
 * @author  ASAMI, Tomoharu
 */
private[cozy] object CozyArticleMediaWarehouseSiteBinding {
  final case class Config(descriptorFile: Path, warehouseProfile: String, target: Option[String] = None, warehouseRoot: Option[Path] = None)

  sealed abstract class Status(val label: String)
  object Status {
    case object Planned extends Status("Planned")
    case object Registered extends Status("Registered")
    case object Unavailable extends Status("Unavailable")
  }

  final case class Row(
    resourceId: String,
    articleIdentity: String,
    locale: String,
    role: String,
    publicPath: Option[String],
    variant: Option[CozyArticleMediaPublication.Variant],
    status: Status,
    reason: Option[String]
  )

  final case class Plan(descriptorFile: Path, warehouseProfile: String, articleIdentity: String, rows: Vector[Row])

  def plan(config: Config, environment: Map[String, String] = sys.env): Plan = {
    if (config == null || config.descriptorFile == null || config.target == null || config.warehouseRoot == null)
      _invalid("Warehouse site binding configuration must be defined")
    val profile = CozyArticleMediaNormalization.requireExactTrimmed(config.warehouseProfile, "Warehouse site profile")
    val descriptorfile = config.descriptorFile.toAbsolutePath.normalize()
    val command = CozyMedia.CommandConfig(descriptorfile, config.target, Some(profile))
    val warehouse = config.warehouseRoot match {
      case Some(root) => CozyMediaWarehouse.planAtRoot(command, root, environment)
      case None => CozyMediaWarehouse.plan(command, environment)
    }
    val descriptor = StructuredDocumentLoader.loadDocument[CozyMedia.Descriptor](InputSource(descriptorfile.toFile)).take
    val association = descriptor.articleMedia.getOrElse(_invalid("Warehouse site binding requires articleMedia"))
    if (association.publicationProfile != profile)
      _invalid(s"Warehouse site articleMedia.publicationProfile must equal requested profile: $profile")
    val resources = descriptor.resources.map(resource => resource.id -> resource).toMap
    val keys = warehouse.resources.map(item => (item.articleIdentity, item.locale, item.role))
    if (keys.distinct.size != keys.size)
      _invalid("Warehouse site binding has duplicate selected article/locale/role tuples")
    val rows = warehouse.resources.map { item =>
      val resource = resources.getOrElse(item.resourceId, _invalid(s"Warehouse site resource is missing: ${item.resourceId}"))
      val media = resource.articleMedia.getOrElse(_invalid(s"Warehouse site role is missing: ${item.resourceId}"))
      if (item.articleIdentity != association.articleIdentity || resource.language != Some(item.locale) || media.role != item.role)
        _invalid(s"Warehouse site descriptor association differs: ${item.resourceId}")
      val variant = if (item.role == "video")
        _video_variant(descriptorfile.getParent, media, item)
      else if (Files.notExists(item.destination, LinkOption.NOFOLLOW_LINKS))
        None -> Some(s"Canonical destination is absent: ${item.destination}")
      else {
        _require_file(item.destination, "Canonical destination")
        Some(_nonvideo_variant(media, item)) -> None
      }
      val publicpath = if (item.role == "video") variant._1.flatMap(_.video.flatMap(_.watchUrl)).map(_.toString)
        else Some(item.publicPath)
      Row(item.resourceId, item.articleIdentity, item.locale, item.role, publicpath, variant._1,
        if (variant._1.nonEmpty) Status.Planned else Status.Unavailable, variant._2)
    }
    Plan(descriptorfile, profile, association.articleIdentity, rows.sortBy(_.resourceId))
  }

  def render(plan: Plan, publicationRoot: Path, dryRun: Boolean): String = {
    val outcome = if (plan.rows.exists(_.status == Status.Unavailable)) "partial" else "complete"
    val rows = plan.rows.map { row =>
      val status = if (row.status == Status.Unavailable) Status.Unavailable else if (dryRun) Status.Planned else Status.Registered
      s"  - ${row.resourceId}: ${status.label} articleIdentity=${row.articleIdentity} locale=${row.locale} role=${row.role}" +
        row.publicPath.map(value => s" public=$value").getOrElse("") + row.reason.map(value => s" reason=$value").getOrElse("")
    }
    (Vector("Cozy Media Register Site", s"descriptor: ${plan.descriptorFile}", s"publication: $publicationRoot",
      s"warehouse-profile: ${plan.warehouseProfile}", s"mode: ${if (dryRun) "dry-run" else "registered"}",
      s"outcome: $outcome (site registration only; public availability unverified)") ++ rows).mkString("\n")
  }

  private def _nonvideo_variant(media: CozyMedia.ResourceArticleMedia, item: CozyMediaWarehouse.DeploymentItem): CozyArticleMediaPublication.Variant = {
    val uri = new URI(item.publicPath)
    val variant = item.role match {
      case "infographic" => CozyArticleMediaPublication.Variant(item.locale,
        infographic = Some(ImageReference(uri, media.mediaType, media.alt)))
      case "article_pdf" => CozyArticleMediaPublication.Variant(item.locale,
        articlePdf = Some(PdfDocumentReference(uri, "application/pdf", media.label)))
      case "summary_slides_pdf" => CozyArticleMediaPublication.Variant(item.locale,
        summarySlidesPdf = Some(PdfDocumentReference(uri, "application/pdf", media.label)))
      case _ => _invalid(s"Warehouse site role is unsupported: ${item.role}")
    }
    CozyArticleMediaPublication.produce(item.articleIdentity, Vector(variant))
    variant
  }

  private def _video_variant(
    descriptorroot: Path,
    media: CozyMedia.ResourceArticleMedia,
    item: CozyMediaWarehouse.DeploymentItem
  ): (Option[CozyArticleMediaPublication.Variant], Option[String]) = {
    val relative = CozyArticleMediaNormalization.normalizeRelativePath(media.production.getOrElse(
      _invalid(s"Warehouse site video production must be declared: ${item.resourceId}")
    ), "Warehouse site video production")
    val file = descriptorroot.resolve(relative).normalize()
    if (!file.startsWith(descriptorroot) || file == descriptorroot)
      _invalid(s"Warehouse site video production escapes descriptor parent: ${item.resourceId}")
    _require_direct_path(file)
    if (Files.notExists(file, LinkOption.NOFOLLOW_LINKS))
      return None -> Some(s"Video production is absent: $relative")
    _require_file(file, "Video production")
    val json = try Json.parse(Files.readString(file, StandardCharsets.UTF_8)) catch {
      case NonFatal(_) => _invalid(s"Warehouse site video production must contain JSON: ${item.resourceId}")
    }
    val root = json.asOpt[JsObject].getOrElse(_invalid(s"Warehouse site video production must be a JSON object: ${item.resourceId}"))
    val category = _required_string(root, "category", item.resourceId)
    val article = _required_string(root, "article", item.resourceId)
    val language = _required_string(root, "language", item.resourceId)
    if (s"$category/$article" != item.articleIdentity || language != item.locale)
      _invalid(s"Warehouse site video production identity or language differs: ${item.resourceId}")
    val render = _required_object(root, "render", item.resourceId)
    val renderstatus = _required_string(render, "status", item.resourceId)
    val qa = _required_object(render, "qa", item.resourceId)
    val qastatus = _required_string(qa, "status", item.resourceId)
    val youtube = _required_object(root, "youtube", item.resourceId)
    val status = _required_string(youtube, "status", item.resourceId)
    val url = if (status == "published") {
      val value = _youtube_url(_required_string(youtube, "videoUrl", item.resourceId), item.resourceId)
      youtube.value.get("videoId").foreach {
        case JsString(id) if id == value._2 =>
        case _ => _invalid(s"Warehouse site videoId contradicts published URL: ${item.resourceId}")
      }
      Some(value._1)
    } else None
    if (renderstatus != "completed" || qastatus != "technical-and-visual-qa-passed" || status != "published")
      None -> Some(s"Video publication is unfinished: render=$renderstatus qa=$qastatus youtube=$status")
    else {
      val video = VideoReference(VideoPresentation.ExternalLink, VideoStatus.Published, Some("youtube"), url, None)
      val variant = CozyArticleMediaPublication.Variant(item.locale, video = Some(video))
      CozyArticleMediaPublication.produce(item.articleIdentity, Vector(variant))
      Some(variant) -> None
    }
  }

  private def _youtube_url(value: String, resourceid: String): (URI, String) = {
    val uri = try new URI(value) catch {
      case NonFatal(_) => _invalid(s"Warehouse site video URL is invalid: $resourceid")
    }
    if (value != value.trim || uri.toString != value || uri.getScheme != "https" ||
      uri.getRawUserInfo != null || uri.getPort != -1 || uri.getRawFragment != null)
      _invalid(s"Warehouse site video URL must be clean HTTPS YouTube evidence: $resourceid")
    val id = uri.getHost match {
      case "youtu.be" if uri.getRawQuery == null && uri.getRawPath != null && uri.getRawPath.startsWith("/") => uri.getRawPath.drop(1)
      case "www.youtube.com" if uri.getRawPath == "/watch" && uri.getRawQuery != null && uri.getRawQuery.startsWith("v=") => uri.getRawQuery.drop(2)
      case _ => ""
    }
    if (!id.matches("[A-Za-z0-9_-]+"))
      _invalid(s"Warehouse site video URL is not an accepted YouTube form: $resourceid")
    uri -> id
  }

  private def _required_object(value: JsObject, field: String, resourceid: String): JsObject =
    value.value.get(field).collect { case result: JsObject => result }.getOrElse(
      _invalid(s"Warehouse site video production $field must be an object: $resourceid")
    )

  private def _required_string(value: JsObject, field: String, resourceid: String): String =
    value.value.get(field).collect { case JsString(result) if result.nonEmpty && result == result.trim => result }.getOrElse(
      _invalid(s"Warehouse site video production $field must be a non-empty exact string: $resourceid")
    )

  private def _require_direct_path(file: Path): Unit = {
    val segments = file.toAbsolutePath.iterator()
    var ancestor = file.toAbsolutePath.getRoot
    while (segments.hasNext) {
      ancestor = ancestor.resolve(segments.next())
      if (Files.isSymbolicLink(ancestor))
        _invalid(s"Warehouse site production path traverses a symlink: $ancestor")
      if (segments.hasNext && Files.exists(ancestor, LinkOption.NOFOLLOW_LINKS) && !Files.isDirectory(ancestor, LinkOption.NOFOLLOW_LINKS))
        _invalid(s"Warehouse site production ancestor must be a direct directory: $ancestor")
    }
  }

  private def _require_file(file: Path, label: String): Unit =
    if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS) || !Files.isReadable(file))
      _invalid(s"$label must be a direct regular readable file: $file")

  private def _invalid(message: String): Nothing = RAISE.invalidArgumentFault(message)
}
