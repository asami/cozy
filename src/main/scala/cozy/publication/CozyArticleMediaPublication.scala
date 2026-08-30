package cozy.publication

import java.net.URI
import scala.collection.immutable.ListMap
import org.goldenport.RAISE
import org.smartdox.metadata.PublishMetadata
import org.smartdox.metadata.PublishMetadata.{ArticleMediaPublication, ArticleMediaVariant, ImageReference, PdfDocumentReference, VideoPresentation, VideoReference, VideoStatus}
import play.api.libs.json.{JsObject, JsString, JsValue}

/*
 * @since   Aug.  4, 2026
 * @version Aug. 30, 2026
 * @author  ASAMI, Tomoharu
 */
private[cozy] object CozyArticleMediaPublication {
  final case class Variant(
    locale: String,
    infographic: Option[ImageReference] = None,
    video: Option[VideoReference] = None,
    articlePdf: Option[PdfDocumentReference] = None,
    summarySlidesPdf: Option[PdfDocumentReference] = None
  )

  final case class Result(
    entryPath: String,
    metadata: JsObject,
    publication: ArticleMediaPublication
  )

  def produce(articleIdentity: String, variants: Vector[Variant]): Result = {
    if (variants == null)
      _invalid("Article-media variants must not be null")
    val identity = CozyArticleMediaNormalization.normalizeArticleIdentity(articleIdentity)
    val normalizedvariants = variants.map(_normalize_variant).sortBy(_.locale)
    if (normalizedvariants.isEmpty)
      _invalid("Article-media publication must define variants")
    if (normalizedvariants.map(_.locale).distinct.size != normalizedvariants.size)
      _invalid(s"Duplicate article-media locale variant: $identity")
    val publication = ArticleMediaPublication(identity, normalizedvariants)
    Result(
      entryPath = s"metadata/article-media/$identity.json",
      metadata = _publication_json(publication),
      publication = publication
    )
  }

  private def _normalize_variant(variant: Variant): ArticleMediaVariant = {
    if (variant == null || variant.infographic == null || variant.video == null || variant.articlePdf == null || variant.summarySlidesPdf == null)
      _invalid("Article-media variant must not be null")
    val locale = CozyArticleMediaNormalization.normalizeLocale(variant.locale)
    val infographic = variant.infographic.map(_normalize_infographic)
    val video = variant.video.map(_normalize_video)
    val articlepdf = variant.articlePdf.map(_normalize_article_pdf)
    val summaryslidespdf = variant.summarySlidesPdf.map(_normalize_summary_slides_pdf)
    ArticleMediaVariant(locale, infographic, video, articlepdf, summaryslidespdf)
  }

  private def _normalize_article_pdf(pdfreference: PdfDocumentReference): PdfDocumentReference =
    _normalize_pdf(pdfreference, "article_pdf")

  private def _normalize_summary_slides_pdf(pdfreference: PdfDocumentReference): PdfDocumentReference =
    _normalize_pdf(pdfreference, "summary_slides_pdf")

  private def _normalize_pdf(pdfreference: PdfDocumentReference, role: String): PdfDocumentReference = {
    if (pdfreference == null)
      _invalid(s"Article-media $role must not be null")
    CozyArticleMediaNormalization.validateSiteVisibleUri(pdfreference.publicPath, s"Article-media $role public_path")
    if (pdfreference.mediaType != "application/pdf")
      _invalid(s"Article-media $role media_type must be application/pdf")
    val label = pdfreference.label match {
      case None => None
      case Some(value) if value != null && value.trim.nonEmpty => Some(value)
      case _ => _invalid(s"Article-media $role label must be nonblank")
    }
    pdfreference.copy(mediaType = "application/pdf", label = label)
  }

  private def _normalize_infographic(infographic: ImageReference): ImageReference = {
    if (infographic == null)
      _invalid("Article-media infographic must not be null")
    CozyArticleMediaNormalization.validateSiteVisibleUri(infographic.publicPath, "Article-media infographic public_path")
    infographic.copy(
      mediaType = _optional_string(infographic.mediaType),
      alt = _optional_string(infographic.alt)
    )
  }

  private def _normalize_video(video: VideoReference): VideoReference = {
    if (video == null)
      _invalid("Article-media video must not be null")
    video.presentation match {
      case VideoPresentation.ExternalLink | VideoPresentation.SiteHosted =>
      case _ => _invalid(s"Unsupported article-media video presentation: ${video.presentation}")
    }
    video.status match {
      case VideoStatus.Draft | VideoStatus.Published | VideoStatus.Withdrawn =>
      case _ => _invalid(s"Unsupported article-media video status: ${video.status}")
    }
    video.watchUrl.foreach(_validate_absolute_uri(_, "Article-media video watch_url"))
    video.contentUrl.foreach(CozyArticleMediaNormalization.validateSiteVisibleUri(_, "Article-media video content_url"))
    if (video.status == VideoStatus.Published) {
      video.presentation match {
        case VideoPresentation.ExternalLink if video.watchUrl.isEmpty =>
          _invalid("Published external article-media video requires watch_url")
        case VideoPresentation.SiteHosted if video.contentUrl.isEmpty =>
          _invalid("Published site-hosted article-media video requires content_url")
        case _ =>
      }
    }
    video.copy(provider = _optional_string(video.provider))
  }

  private def _validate_absolute_uri(uri: URI, label: String): Unit = {
    if (uri == null)
      _invalid(s"$label must be an absolute URI")
    val value = Option(uri).map(_.toString).getOrElse("")
    if (value.isEmpty || value != value.trim || !uri.isAbsolute)
      _invalid(s"$label must be an absolute URI: $value")
  }

  private def _optional_string(value: Option[String]): Option[String] =
    value.flatMap(x => Option(x).map(_.trim).filter(_.nonEmpty))

  private def _publication_json(publication: ArticleMediaPublication): JsObject =
    _json_object(
      "type" -> JsString("article-media-publication"),
      "article" -> _json_object("identity" -> JsString(publication.articleIdentity)),
      "variants" -> _json_object(publication.variants.sortBy(_.locale).map(variant => variant.locale -> _variant_json(variant)): _*)
    )

  private def _variant_json(variant: ArticleMediaVariant): JsObject = {
    val fields = variant.infographic.map(x => Vector("infographic" -> _infographic_json(x))).getOrElse(Vector.empty) ++
      variant.articlePdf.map(x => Vector("article_pdf" -> _pdf_json(x))).getOrElse(Vector.empty) ++
      variant.summarySlidesPdf.map(x => Vector("summary_slides_pdf" -> _pdf_json(x))).getOrElse(Vector.empty) ++
      variant.video.map(x => Vector("video" -> _video_json(x))).getOrElse(Vector.empty)
    _json_object(fields: _*)
  }

  private def _infographic_json(infographic: ImageReference): JsObject = {
    val fields = Vector("public_path" -> JsString(infographic.publicPath.toString)) ++
      infographic.mediaType.map(x => "media_type" -> JsString(x)) ++
      infographic.alt.map(x => "alt" -> JsString(x))
    _json_object(fields: _*)
  }

  private def _pdf_json(pdf: PdfDocumentReference): JsObject = {
    val fields = Vector(
      "public_path" -> JsString(pdf.publicPath.toString),
      "media_type" -> JsString(pdf.mediaType)
    ) ++ pdf.label.map(x => "label" -> JsString(x))
    _json_object(fields: _*)
  }

  private def _video_json(video: VideoReference): JsObject = {
    val fields = Vector(
      "presentation" -> JsString(video.presentation.name),
      "status" -> JsString(video.status.name)
    ) ++
      video.provider.map(x => "provider" -> JsString(x)) ++
      video.watchUrl.map(x => "watch_url" -> JsString(x.toString)) ++
      video.contentUrl.map(x => "content_url" -> JsString(x.toString))
    _json_object(fields: _*)
  }

  private def _json_object(fields: (String, JsValue)*): JsObject =
    JsObject(ListMap(fields: _*))

  private def _invalid(message: String): Nothing =
    RAISE.invalidArgumentFault(message)
}
