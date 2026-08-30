package cozy.publication

import java.net.URI
import org.goldenport.RAISE
import org.smartdox.metadata.PublishMetadata
import org.smartdox.metadata.PublishMetadata.{ArticleMediaPublication, ArticleMediaVariant, ImageReference, VideoPresentation, VideoReference, VideoStatus}

/*
 * @since   Aug.  4, 2026
 * @version Aug. 30, 2026
 * @author  ASAMI, Tomoharu
 */
private[cozy] object CozyArticleMediaAssociation {
  final case class Key(
    articleIdentity: String,
    locale: String,
    role: CozyArticleMediaIntegrity.Role
  )

  final case class Correlation(
    key: Key,
    publicPath: URI,
    integrity: CozyArticleMediaIntegrity.Result,
    projectable: Boolean = true
  )

  final case class StructuralMedia(
    key: Key,
    publicPath: URI,
    integrity: Option[CozyArticleMediaIntegrity.Result],
    projectable: Boolean
  )

  final case class Inspection(
    metadata: PublishMetadata,
    media: Vector[StructuralMedia],
    integrityResults: Vector[CozyArticleMediaIntegrity.Result]
  ) {
    def resolve(articleIdentity: String, locale: String): Option[ArticleMediaVariant] =
      metadata.resolveArticleMedia(articleIdentity, locale)
  }

  final case class Result(
    metadata: PublishMetadata,
    correlations: Vector[Correlation],
    integrityResults: Vector[CozyArticleMediaIntegrity.Result]
  ) {
    def resolve(articleIdentity: String, locale: String): Option[ArticleMediaVariant] =
      metadata.resolveArticleMedia(articleIdentity, locale)
  }

  def validate(
    metadata: PublishMetadata,
    integrityResults: Vector[CozyArticleMediaIntegrity.Result]
  ): Result =
    associate(metadata, integrityResults)

  def associate(
    metadata: PublishMetadata,
    integrityResults: Vector[CozyArticleMediaIntegrity.Result]
  ): Result = {
    val inspection = inspect(metadata, integrityResults)
    val correlations = inspection.media.map { medium =>
      val integrity = medium.integrity.getOrElse(
        _invalid(s"Missing article-media integrity: ${medium.key.articleIdentity} [${medium.key.locale}, ${medium.key.role.name}]")
      )
      if (medium.projectable && medium.key.role == CozyArticleMediaIntegrity.Role.Video &&
        integrity.record.publicationState != CozyArticleMediaIntegrity.PublicationState.Published)
        _invalid(s"Published site-hosted article-media video requires published Cozy integrity: ${medium.key.articleIdentity} [${medium.key.locale}]")
      Correlation(medium.key, medium.publicPath, integrity, medium.projectable)
    }
    Result(
      metadata = inspection.metadata,
      correlations = correlations,
      integrityResults = inspection.integrityResults
    )
  }

  def inspect(
    metadata: PublishMetadata,
    integrityResults: Vector[CozyArticleMediaIntegrity.Result]
  ): Inspection = {
    if (metadata == null)
      _invalid("Article-media association metadata must be defined")
    val integrities = _normalize_integrities(integrityResults)
    val integritymap = integrities.map(x => x._1 -> x._2).toMap
    val publications = _native_publications(metadata)
    val media = publications.flatMap { publication =>
      publication.publication.variants.flatMap { variant =>
        _media_for_variant(publication.articleidentity, variant, integritymap)
      }
    }.sortBy(x => (x.key.articleIdentity, x.key.locale, x.key.role.name))
    Inspection(
      metadata = metadata,
      media = media,
      integrityResults = integrities.sortBy(x => (x._1.articleIdentity, x._1.locale, x._1.role.name)).map(_._2)
    )
  }

  def inspectPublication(
    publicationResult: CozyArticleMediaPublication.Result,
    integrityResults: Vector[CozyArticleMediaIntegrity.Result]
  ): Vector[StructuralMedia] = {
    val publication = _normalize_publication_result(publicationResult)
    val integritymap = _normalize_integrities(integrityResults).map(x => x._1 -> x._2).toMap
    publication.variants.flatMap { variant =>
      _media_for_variant(publication.articleIdentity, variant, integritymap)
    }.sortBy(x => (x.key.articleIdentity, x.key.locale, x.key.role.name))
  }

  private final case class NativePublication(
    articleidentity: String,
    publication: ArticleMediaPublication
  )

  private def _native_publications(metadata: PublishMetadata): Vector[NativePublication] = {
    val publications = metadata.articleMedia.publications.map { publication =>
      if (publication == null)
        _invalid("Article-media association native publication must be defined")
      NativePublication(
        articleidentity = CozyArticleMediaNormalization.normalizeArticleIdentity(publication.articleIdentity),
        publication = publication
      )
    }
    publications.groupBy(_.articleidentity).toVector.sortBy(_._1).collectFirst {
      case (articleidentity, xs) if xs.size > 1 => articleidentity
    }.foreach(x => _invalid(s"Duplicate native article-media publication identity: $x"))
    publications
  }

  private def _normalize_integrities(
    integrityresults: Vector[CozyArticleMediaIntegrity.Result]
  ): Vector[(Key, CozyArticleMediaIntegrity.Result)] = {
    if (integrityresults == null)
      _invalid("Article-media association integrity results must be defined")
    val normalized = integrityresults.map { integrity =>
      if (integrity == null || integrity.record == null)
        _invalid("Article-media association integrity result must be defined")
      val canonical = _canonical_integrity(integrity)
      _integrity_key(canonical) -> canonical
    }
    normalized.groupBy(_._1).toVector.sortBy { case (key, _) => (key.articleIdentity, key.locale, key.role.name) }.collectFirst {
      case (key, xs) if xs.size > 1 => key
    }.foreach(key => _invalid(s"Duplicate article-media integrity key: ${key.articleIdentity} [${key.locale}, ${key.role.name}]"))
    normalized
  }

  private def _canonical_integrity(
    integrity: CozyArticleMediaIntegrity.Result
  ): CozyArticleMediaIntegrity.Result = {
    val record = integrity.record
    val canonical = CozyArticleMediaIntegrity.produce(CozyArticleMediaIntegrity.Input(
      articleIdentity = record.articleIdentity,
      locale = record.locale,
      role = record.role,
      artifact = record.artifact,
      publicPath = record.publicPath,
      repositoryPath = record.repositoryPath,
      mediaType = record.mediaType,
      sha256 = record.sha256,
      provenance = record.provenance,
      publicationState = record.publicationState
    ))
    if (integrity != canonical)
      _invalid("Article-media association integrity result must be canonical")
    canonical
  }

  private def _normalize_publication_result(
    result: CozyArticleMediaPublication.Result
  ): ArticleMediaPublication = {
    if (result == null || result.publication == null || result.metadata == null)
      _invalid("Article-media association publication result must be defined")
    val variants = Option(result.publication.variants).getOrElse(
      _invalid("Article-media association publication result variants must be defined")
    ).map { variant =>
      if (variant == null || variant.infographic == null || variant.video == null || variant.articlePdf == null || variant.summarySlidesPdf == null)
        _invalid("Article-media association publication result variant must be defined")
      CozyArticleMediaPublication.Variant(
        locale = variant.locale,
        infographic = variant.infographic,
        video = variant.video,
        articlePdf = variant.articlePdf,
        summarySlidesPdf = variant.summarySlidesPdf
      )
    }
    val canonical = CozyArticleMediaPublication.produce(result.publication.articleIdentity, variants)
    if (result != canonical)
      _invalid("Article-media association publication result must be canonical")
    canonical.publication
  }

  private def _integrity_key(integrity: CozyArticleMediaIntegrity.Result): Key = {
    val record = integrity.record
    val role = _integrity_role(record.role)
    Key(
      articleIdentity = CozyArticleMediaNormalization.normalizeArticleIdentity(record.articleIdentity),
      locale = CozyArticleMediaNormalization.normalizeLocale(record.locale),
      role = role
    )
  }

  private def _media_for_variant(
    articleidentity: String,
    variant: ArticleMediaVariant,
    integritymap: Map[Key, CozyArticleMediaIntegrity.Result]
  ): Vector[StructuralMedia] = {
    if (variant == null)
      _invalid("Article-media association native variant must be defined")
    val locale = CozyArticleMediaNormalization.normalizeLocale(variant.locale)
    val infographic = variant.infographic.toVector.map { image =>
      if (image == null)
        _invalid("Article-media association native infographic must be defined")
      _inspect_media(articleidentity, locale, CozyArticleMediaIntegrity.Role.Infographic, image.publicPath, integritymap, projectable = true, required = false)
    }
    val video = variant.video.toVector.flatMap { reference =>
      if (reference == null)
        _invalid("Article-media association native video must be defined")
      reference.presentation match {
        case VideoPresentation.SiteHosted =>
          reference.contentUrl.toVector.map { publicpath =>
            _inspect_media(
              articleidentity,
              locale,
              CozyArticleMediaIntegrity.Role.Video,
              publicpath,
              integritymap,
              projectable = reference.status == VideoStatus.Published,
              required = true
            )
          }
        case VideoPresentation.ExternalLink => Vector.empty
        case _ => _invalid("Article-media association native video presentation is unsupported")
      }
    }
    infographic ++ video
  }

  private def _inspect_media(
    articleidentity: String,
    locale: String,
    role: CozyArticleMediaIntegrity.Role,
    publicpath: URI,
    integritymap: Map[Key, CozyArticleMediaIntegrity.Result],
    projectable: Boolean,
    required: Boolean
  ): StructuralMedia = {
    if (publicpath == null)
      _invalid(s"Article-media association public path must be defined: $articleidentity [$locale, ${role.name}]")
    val key = Key(articleidentity, locale, role)
    val integrity = integritymap.get(key)
    if (required && integrity.isEmpty)
      _invalid(s"Missing article-media integrity: $articleidentity [$locale, ${role.name}]")
    integrity.foreach { value =>
      val integritypath = value.record.publicPath
      if (integritypath == null || publicpath.toString != integritypath.toString)
        _invalid(s"Article-media public path does not match integrity: $articleidentity [$locale, ${role.name}]")
    }
    StructuralMedia(key, publicpath, integrity, projectable)
  }

  private def _integrity_role(role: CozyArticleMediaIntegrity.Role): CozyArticleMediaIntegrity.Role =
    role match {
      case CozyArticleMediaIntegrity.Role.Infographic | CozyArticleMediaIntegrity.Role.Video => role
      case _ => _invalid("Article-media association integrity role is invalid")
    }

  private def _invalid(message: String): Nothing =
    RAISE.invalidArgumentFault(message)
}
