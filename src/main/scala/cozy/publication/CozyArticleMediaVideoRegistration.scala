package cozy.publication

import java.nio.file.{Files, Path}
import org.goldenport.RAISE
import org.smartdox.metadata.PublishMetadata.{VideoPresentation, VideoReference, VideoStatus}
import cozy.video.CozyVideoPublisher

/*
 * @since   Aug.  4, 2026
 * @version Aug.  5, 2026
 * @author  ASAMI, Tomoharu
 */
private[cozy] object CozyArticleMediaVideoRegistration {
  final case class Input(
    publication: CozyVideoPublisher.PublishVideoResult,
    snapshot: CozyArticleMediaRegistry.Snapshot,
    repositoryRoot: Path
  )

  final case class Result(
    roleUpdate: CozyArticleMediaRegistry.RoleUpdate,
    evidence: CozyArticleMediaVideoEvidence.Result
  )

  def prepare(input: Input): Option[Result] = {
    if (input == null)
      _invalid("Article-media video registration input must be defined")
    val publication = Option(input.publication).getOrElse(
      _invalid("Article-media video registration publication result must be defined")
    )
    _prepare(publication, input.snapshot, input.repositoryRoot, None)
  }

  private[publication] def prepare(
    publication: CozyVideoPublisher.PreparedPublication,
    snapshot: CozyArticleMediaRegistry.Snapshot,
    repositoryRoot: Path
  ): Option[Result] = {
    if (publication == null || publication.result == null)
      _invalid("Article-media video registration prepared publication must be defined")
    _prepare(publication.result, snapshot, repositoryRoot, Some(publication.sourceArtifact))
  }

  private def _prepare(
    publication: CozyVideoPublisher.PublishVideoResult,
    snapshot: CozyArticleMediaRegistry.Snapshot,
    repositoryroot: Path,
    stagedartifact: Option[Path]
  ): Option[Result] = {
    val video = Option(publication.video).getOrElse(
      _invalid("Article-media video registration publication video must be defined")
    )
    val binding = Option(video.articleMedia).flatten.map(_validated_binding).getOrElse(return None)
    val evidence = CozyArticleMediaVideoEvidence.project(CozyArticleMediaVideoEvidence.Input(
      articleIdentity = binding.articleIdentity,
      locale = binding.locale,
      videoName = video.name,
      videoVersion = video.version,
      snapshot = snapshot,
      repositoryRoot = repositoryroot,
      stagedArtifact = stagedartifact
    ))
    val artifact = stagedartifact.fold(_direct_artifact(publication.warehouseArtifact))(_ => _planned_artifact(publication.warehouseArtifact))
    if (artifact != evidence.artifactPath)
      _invalid("Article-media video registration evidence artifact does not match the publication result warehouse artifact")
    val record = evidence.integrity.record
    if (record.articleIdentity != binding.articleIdentity || record.locale != binding.locale ||
      record.role != CozyArticleMediaIntegrity.Role.Video || record.artifact.identity != video.name ||
      record.artifact.version != video.version)
      _invalid("Article-media video registration evidence is not bound to the resolved video result")
    if (binding.status == VideoStatus.Published &&
      record.publicationState != CozyArticleMediaIntegrity.PublicationState.Published)
      _invalid("Published SmartDox article-media video requires published Cozy publication evidence")
    val strict = CozyArticleMediaPublication.Variant(
      locale = binding.locale,
      video = Some(VideoReference(
        presentation = VideoPresentation.SiteHosted,
        status = binding.status,
        provider = None,
        watchUrl = None,
        contentUrl = Some(record.publicPath)
      ))
    )
    Some(Result(
      roleUpdate = CozyArticleMediaRegistry.RoleUpdate(binding.articleIdentity, strict, evidence.integrity),
      evidence = evidence
    ))
  }

  private def _direct_artifact(value: Path): Path = {
    if (value == null)
      _invalid("Article-media video registration publication warehouse artifact must be defined")
    if (Files.isSymbolicLink(value) || !Files.isRegularFile(value))
      _invalid(s"Article-media video registration publication warehouse artifact must be a direct regular file: $value")
    value.toRealPath()
  }

  private def _planned_artifact(value: Path): Path = {
    if (value == null)
      _invalid("Article-media video registration publication warehouse artifact must be defined")
    value.toAbsolutePath.normalize()
  }

  private def _validated_binding(value: CozyVideoPublisher.ArticleMediaBinding): CozyVideoPublisher.ArticleMediaBinding = {
    if (value == null)
      _invalid("Article-media video registration binding must be defined")
    val identity = CozyArticleMediaNormalization.normalizeArticleIdentity(value.articleIdentity)
    val locale = CozyArticleMediaNormalization.normalizeLocale(value.locale)
    if (identity != value.articleIdentity || locale != value.locale)
      _invalid("Article-media video registration binding must already be normalized")
    value.status match {
      case VideoStatus.Draft | VideoStatus.Published | VideoStatus.Withdrawn => value
      case _ => _invalid("Article-media video registration binding SmartDox status is invalid")
    }
  }

  private def _invalid(message: String): Nothing =
    RAISE.invalidArgumentFault(message)
}
