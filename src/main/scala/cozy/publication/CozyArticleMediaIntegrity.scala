package cozy.publication

import java.net.URI
import scala.collection.immutable.ListMap
import org.goldenport.RAISE
import play.api.libs.json.{JsObject, JsString, JsValue}

/*
 * @since   Aug.  4, 2026
 * @version Aug. 12, 2026
 * @author  ASAMI, Tomoharu
 */
private[cozy] object CozyArticleMediaIntegrity {
  sealed abstract class Role(val name: String)

  object Role {
    case object Infographic extends Role("infographic")
    case object Video extends Role("video")
  }

  sealed abstract class PublicationState(val name: String)

  object PublicationState {
    case object Registered extends PublicationState("registered")
    case object Published extends PublicationState("published")
    case object Withdrawn extends PublicationState("withdrawn")
  }

  final case class Artifact(identity: String, version: String)

  sealed abstract class Provenance {
    def role: Role
  }

  final case class VideoPublication(
    videoManifest: String,
    repositoryRegistry: String
  ) extends Provenance {
    override val role: Role = Role.Video
  }

  final case class MediaPackage(
    descriptor: String,
    resourceId: String,
    buildManifest: String
  ) extends Provenance {
    override val role: Role = Role.Infographic
  }

  final case class WipSiteVideo(
    descriptor: String,
    resourceId: String,
    production: String
  ) extends Provenance {
    override val role: Role = Role.Video
  }

  final case class Input(
    articleIdentity: String,
    locale: String,
    role: Role,
    artifact: Artifact,
    publicPath: URI,
    repositoryPath: String,
    mediaType: String,
    sha256: String,
    provenance: Provenance,
    publicationState: PublicationState
  )

  final case class Record(
    articleIdentity: String,
    locale: String,
    role: Role,
    artifact: Artifact,
    publicPath: URI,
    repositoryPath: String,
    mediaType: String,
    sha256: String,
    provenance: Provenance,
    publicationState: PublicationState
  )

  final case class Result(
    entryPath: String,
    metadata: JsObject,
    record: Record
  )

  def produce(input: Input): Result = {
    val record = _normalize_record(input)
    Result(
      entryPath = s"metadata/article-media-integrity/${record.articleIdentity}/${record.locale}/${record.role.name}.json",
      metadata = _record_json(record),
      record = record
    )
  }

  private def _normalize_record(input: Input): Record = {
    if (input == null)
      _invalid("Article-media integrity input must be defined")
    val identity = CozyArticleMediaNormalization.normalizeArticleIdentity(input.articleIdentity)
    val locale = CozyArticleMediaNormalization.normalizeLocale(input.locale)
    val role = _normalize_role(input.role)
    val artifact = _normalize_artifact(input.artifact)
    CozyArticleMediaNormalization.validateSiteVisibleUri(input.publicPath, "Article-media integrity publicPath")
    val repositorypath = CozyArticleMediaNormalization.normalizeRelativePath(input.repositoryPath, "Article-media integrity repositoryPath")
    val mediatype = _normalize_media_type(input.mediaType, role)
    val digest = _normalize_digest(input.sha256)
    val provenance = _normalize_provenance(input.provenance, role)
    val state = _normalize_publication_state(input.publicationState)
    Record(identity, locale, role, artifact, input.publicPath, repositorypath, mediatype, digest, provenance, state)
  }

  private def _normalize_role(role: Role): Role =
    role match {
      case Role.Infographic | Role.Video => role
      case _ => _invalid("Article-media integrity role must be infographic or video")
    }

  private def _normalize_artifact(artifact: Artifact): Artifact = {
    if (artifact == null)
      _invalid("Article-media integrity artifact must be defined")
    Artifact(
      identity = CozyArticleMediaNormalization.requireTrimmed(artifact.identity, "Article-media integrity artifact identity"),
      version = CozyArticleMediaNormalization.requireTrimmed(artifact.version, "Article-media integrity artifact version")
    )
  }

  private def _normalize_digest(sha256: String): String = {
    val digest = Option(sha256).getOrElse("")
    if (!digest.matches("[0-9a-f]{64}"))
      _invalid(s"Article-media integrity sha256 must be 64 lowercase hexadecimal characters: $sha256")
    digest
  }

  private def _normalize_media_type(mediatype: String, role: Role): String = {
    val normalized = Option(mediatype).getOrElse("")
    if (normalized.isEmpty || normalized != normalized.trim)
      _invalid(s"Article-media integrity mediaType must be exact and trimmed: $mediatype")
    val accepted = role match {
      case Role.Video => normalized == "video/mp4"
      case Role.Infographic => normalized == "image/png"
      case _ => false
    }
    if (!accepted)
      _invalid(s"Article-media integrity mediaType is unsupported for role ${role.name}: $normalized")
    normalized
  }

  private def _normalize_provenance(provenance: Provenance, role: Role): Provenance = {
    if (provenance == null)
      _invalid("Article-media integrity provenance must be defined")
    if (provenance.role != role)
      _invalid(s"Article-media integrity provenance role must match record role: ${role.name}")
    provenance match {
      case x: VideoPublication =>
        VideoPublication(
          videoManifest = CozyArticleMediaNormalization.normalizeRelativePath(x.videoManifest, "Article-media integrity videoManifest"),
          repositoryRegistry = CozyArticleMediaNormalization.normalizeRelativePath(x.repositoryRegistry, "Article-media integrity repositoryRegistry")
        )
      case x: MediaPackage =>
        MediaPackage(
          descriptor = CozyArticleMediaNormalization.normalizeRelativePath(x.descriptor, "Article-media integrity descriptor"),
          resourceId = CozyArticleMediaNormalization.requireExactTrimmed(x.resourceId, "Article-media integrity resourceId"),
          buildManifest = CozyArticleMediaNormalization.normalizeRelativePath(x.buildManifest, "Article-media integrity buildManifest")
        )
      case x: WipSiteVideo =>
        WipSiteVideo(
          descriptor = CozyArticleMediaNormalization.normalizeRelativePath(x.descriptor, "Article-media integrity descriptor"),
          resourceId = CozyArticleMediaNormalization.requireExactTrimmed(x.resourceId, "Article-media integrity resourceId"),
          production = CozyArticleMediaNormalization.normalizeRelativePath(x.production, "Article-media integrity production")
        )
      case _ => _invalid("Article-media integrity provenance must be video-publication, media-package, or wip-site-video")
    }
  }

  private def _normalize_publication_state(publicationstate: PublicationState): PublicationState =
    publicationstate match {
      case PublicationState.Registered | PublicationState.Published | PublicationState.Withdrawn => publicationstate
      case _ => _invalid("Article-media integrity publicationState is invalid")
    }

  private def _record_json(record: Record): JsObject =
    _json_object(
      "schema" -> JsString("cozy.article-media-integrity.v1"),
      "articleIdentity" -> JsString(record.articleIdentity),
      "locale" -> JsString(record.locale),
      "role" -> JsString(record.role.name),
      "artifact" -> _artifact_json(record.artifact),
      "publicPath" -> JsString(record.publicPath.toString),
      "repositoryPath" -> JsString(record.repositoryPath),
      "mediaType" -> JsString(record.mediaType),
      "sha256" -> JsString(record.sha256),
      "provenance" -> _provenance_json(record.provenance),
      "publicationState" -> JsString(record.publicationState.name)
    )

  private def _artifact_json(artifact: Artifact): JsObject =
    _json_object(
      "identity" -> JsString(artifact.identity),
      "version" -> JsString(artifact.version)
    )

  private def _provenance_json(provenance: Provenance): JsObject =
    provenance match {
      case x: VideoPublication =>
        _json_object(
          "kind" -> JsString("video-publication"),
          "videoManifest" -> JsString(x.videoManifest),
          "repositoryRegistry" -> JsString(x.repositoryRegistry)
        )
      case x: MediaPackage =>
        _json_object(
          "kind" -> JsString("media-package"),
          "descriptor" -> JsString(x.descriptor),
          "resourceId" -> JsString(x.resourceId),
          "buildManifest" -> JsString(x.buildManifest)
        )
      case x: WipSiteVideo =>
        _json_object(
          "kind" -> JsString("wip-site-video"),
          "descriptor" -> JsString(x.descriptor),
          "resourceId" -> JsString(x.resourceId),
          "production" -> JsString(x.production)
        )
      case _ => _invalid("Article-media integrity provenance must be video-publication, media-package, or wip-site-video")
    }

  private def _json_object(fields: (String, JsValue)*): JsObject =
    JsObject(ListMap(fields: _*))

  private def _invalid(message: String): Nothing =
    RAISE.invalidArgumentFault(message)
}
