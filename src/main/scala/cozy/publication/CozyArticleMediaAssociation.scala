package cozy.publication

import java.net.URI
import org.goldenport.RAISE
import org.smartdox.metadata.PublishMetadata
import org.smartdox.metadata.PublishMetadata.{ArticleMediaPublication, ArticleMediaVariant, ImageReference, VideoPresentation, VideoReference, VideoStatus}

/*
 * @since   Aug.  4, 2026
 * @version Aug.  4, 2026
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
    integrity: CozyArticleMediaIntegrity.Result
  )

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
    if (metadata == null)
      _invalid("Article-media association metadata must be defined")
    val integrities = _normalize_integrities(integrityResults)
    val integritymap = integrities.map(x => x._1 -> x._2).toMap
    val publications = _native_publications(metadata)
    val correlations = publications.flatMap { publication =>
      publication.publication.variants.flatMap { variant =>
        _correlations_for_variant(publication.articleidentity, variant, integritymap)
      }
    }.sortBy(x => (x.key.articleIdentity, x.key.locale, x.key.role.name))
    Result(
      metadata = metadata,
      correlations = correlations,
      integrityResults = integrities.sortBy(x => (x._1.articleIdentity, x._1.locale, x._1.role.name)).map(_._2)
    )
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
      _integrity_key(integrity) -> integrity
    }
    normalized.groupBy(_._1).toVector.sortBy { case (key, _) => (key.articleIdentity, key.locale, key.role.name) }.collectFirst {
      case (key, xs) if xs.size > 1 => key
    }.foreach(key => _invalid(s"Duplicate article-media integrity key: ${key.articleIdentity} [${key.locale}, ${key.role.name}]"))
    normalized
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

  private def _correlations_for_variant(
    articleidentity: String,
    variant: ArticleMediaVariant,
    integritymap: Map[Key, CozyArticleMediaIntegrity.Result]
  ): Vector[Correlation] = {
    if (variant == null)
      _invalid("Article-media association native variant must be defined")
    val locale = CozyArticleMediaNormalization.normalizeLocale(variant.locale)
    val infographic = variant.infographic.toVector.map { image =>
      if (image == null)
        _invalid("Article-media association native infographic must be defined")
      _correlate(articleidentity, locale, CozyArticleMediaIntegrity.Role.Infographic, image.publicPath, integritymap)
    }
    val video = variant.video.toVector.flatMap { reference =>
      if (reference == null)
        _invalid("Article-media association native video must be defined")
      reference.presentation match {
        case VideoPresentation.SiteHosted =>
          reference.contentUrl.toVector.map { publicpath =>
            val correlation = _correlate(articleidentity, locale, CozyArticleMediaIntegrity.Role.Video, publicpath, integritymap)
            if (reference.status == VideoStatus.Published && correlation.integrity.record.publicationState != CozyArticleMediaIntegrity.PublicationState.Published)
              _invalid(s"Published site-hosted article-media video requires published Cozy integrity: $articleidentity [$locale]")
            correlation
          }
        case VideoPresentation.ExternalLink => Vector.empty
        case _ => _invalid("Article-media association native video presentation is unsupported")
      }
    }
    infographic ++ video
  }

  private def _correlate(
    articleidentity: String,
    locale: String,
    role: CozyArticleMediaIntegrity.Role,
    publicpath: URI,
    integritymap: Map[Key, CozyArticleMediaIntegrity.Result]
  ): Correlation = {
    if (publicpath == null)
      _invalid(s"Article-media association public path must be defined: $articleidentity [$locale, ${role.name}]")
    val key = Key(articleidentity, locale, role)
    val integrity = integritymap.getOrElse(key,
      _invalid(s"Missing article-media integrity: $articleidentity [$locale, ${role.name}]")
    )
    val integritypath = integrity.record.publicPath
    if (integritypath == null || publicpath.toString != integritypath.toString)
      _invalid(s"Article-media public path does not match integrity: $articleidentity [$locale, ${role.name}]")
    Correlation(key, publicpath, integrity)
  }

  private def _integrity_role(role: CozyArticleMediaIntegrity.Role): CozyArticleMediaIntegrity.Role =
    role match {
      case CozyArticleMediaIntegrity.Role.Infographic | CozyArticleMediaIntegrity.Role.Video => role
      case _ => _invalid("Article-media association integrity role is invalid")
    }

  private def _invalid(message: String): Nothing =
    RAISE.invalidArgumentFault(message)
}
