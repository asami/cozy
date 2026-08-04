package cozy.publication

import java.nio.file.{Files, LinkOption, NoSuchFileException, Path}
import java.nio.file.attribute.BasicFileAttributes
import java.security.MessageDigest
import org.goldenport.RAISE
import org.smartdox.metadata.PublishMetadata

/*
 * @since   Aug.  4, 2026
 * @version Aug.  4, 2026
 * @author  ASAMI, Tomoharu
 */
private[cozy] object CozyArticleMediaPolicy {
  sealed abstract class Strategy

  object Strategy {
    case object Preview extends Strategy
    case object Production extends Strategy
  }

  sealed abstract class DiagnosticKind

  object DiagnosticKind {
    case object MissingMedia extends DiagnosticKind
    case object UnavailableMedia extends DiagnosticKind
  }

  final case class Diagnostic(
    key: CozyArticleMediaAssociation.Key,
    publicationState: Option[CozyArticleMediaIntegrity.PublicationState],
    kind: DiagnosticKind
  )

  final case class Result(
    metadata: PublishMetadata,
    structural: CozyArticleMediaAssociation.Inspection,
    stagedCorrelations: Vector[CozyArticleMediaAssociation.Correlation],
    omittedKeys: Vector[CozyArticleMediaAssociation.Key],
    diagnostics: Vector[Diagnostic]
  )

  final case class RegistrationResult(
    videoEvidence: Vector[CozyArticleMediaVideoEvidence.Result],
    infographicEvidence: Vector[CozyArticleMediaInfographicEvidence.Result],
    upsertResult: CozyArticleMediaRegistry.UpsertResult
  )

  final case class VideoRegistration(
    articleIdentity: String,
    locale: String,
    videoName: String,
    videoVersion: String
  )

  private final case class RootIdentity(lexical: Path, real: Path)

  def evaluate(
    strategy: Strategy,
    metadata: PublishMetadata,
    integrityResults: Vector[CozyArticleMediaIntegrity.Result],
    artifactRepositoryRoot: Path
  ): Result = {
    _strategy(strategy)
    val root = _artifact_root(artifactRepositoryRoot)
    val structural = CozyArticleMediaAssociation.inspect(metadata, integrityResults)
    structural.integrityResults.foreach(integrity => _validate_record_containment(integrity.record, root))
    val staged = Vector.newBuilder[CozyArticleMediaAssociation.Correlation]
    val omitted = Vector.newBuilder[CozyArticleMediaAssociation.Key]
    val diagnostics = Vector.newBuilder[Diagnostic]
    structural.media.foreach { medium =>
      if (medium.projectable) {
        medium.integrity match {
          case None =>
            if (medium.key.role == CozyArticleMediaIntegrity.Role.Infographic) {
              omitted += medium.key
              strategy match {
                case Strategy.Preview =>
                  diagnostics += Diagnostic(medium.key, None, DiagnosticKind.MissingMedia)
                case Strategy.Production =>
                case _ => _invalid("Article-media policy strategy is invalid")
              }
            } else {
              _invalid(s"Missing article-media integrity: ${medium.key.articleIdentity} [${medium.key.locale}, ${medium.key.role.name}]")
            }
          case Some(integrity) =>
            integrity.record.publicationState match {
              case CozyArticleMediaIntegrity.PublicationState.Published =>
                if (_published_artifact_is_available(integrity.record, root))
                  staged += CozyArticleMediaAssociation.Correlation(medium.key, medium.publicPath, integrity, projectable = true)
                else {
                  strategy match {
                    case Strategy.Preview =>
                      omitted += medium.key
                      diagnostics += Diagnostic(medium.key, Some(CozyArticleMediaIntegrity.PublicationState.Published), DiagnosticKind.UnavailableMedia)
                    case Strategy.Production =>
                      _require_published_artifact(integrity.record, root)
                    case _ => _invalid("Article-media policy strategy is invalid")
                  }
                }
              case state @ (CozyArticleMediaIntegrity.PublicationState.Registered | CozyArticleMediaIntegrity.PublicationState.Withdrawn) =>
                strategy match {
                  case Strategy.Preview =>
                    omitted += medium.key
                    diagnostics += Diagnostic(medium.key, Some(state), DiagnosticKind.UnavailableMedia)
                  case Strategy.Production =>
                    _invalid(s"Article-media production requires published Cozy integrity: ${medium.key.articleIdentity} [${medium.key.locale}, ${medium.key.role.name}]")
                  case _ =>
                    _invalid("Article-media policy strategy is invalid")
                }
              case _ =>
                _invalid("Article-media integrity publication state is invalid")
            }
        }
      }
    }
    Result(
      metadata = structural.metadata,
      structural = structural,
      stagedCorrelations = staged.result().sortBy(x => _key(x.key)),
      omittedKeys = omitted.result().sortBy(_key),
      diagnostics = diagnostics.result().sortBy(x => (_key(x.key), _diagnostic_kind(x.kind)))
    )
  }

  def register(
    publicationRoot: Path,
    artifactRepositoryRoot: Path,
    bundleName: String,
    publicationResult: CozyArticleMediaPublication.Result,
    videoRegistrations: Vector[VideoRegistration],
    infographicInputs: Vector[CozyArticleMediaInfographicEvidence.Input]
  ): RegistrationResult = {
    if (publicationRoot == null)
      _invalid("Article-media policy publication root must be defined")
    if (publicationResult == null)
      _invalid("Article-media policy publication result must be defined")
    val artifactroot = _artifact_root(artifactRepositoryRoot)
    val snapshot = CozyArticleMediaRegistry.load(publicationRoot)
    snapshot.bundleDigests.getOrElse(bundleName,
      _invalid(s"Article-media policy configured bundle digest is missing: $bundleName")
    )
    val videos = _video_evidence(videoRegistrations, snapshot, artifactroot)
    val infographics = _infographic_evidence(infographicInputs, artifactroot)
    val integrities = (videos.map(_.integrity) ++ infographics.map(_.integrity)).sortBy(x => _key(_integrity_key(x)))
    _reject_duplicate_integrities(integrities)
    _preflight_publication(publicationResult, integrities)
    val upsert = CozyArticleMediaRegistry.upsert(publicationRoot, bundleName, publicationResult, integrities, snapshot.bundleDigests)
    RegistrationResult(videos, infographics, upsert)
  }

  private def _video_evidence(
    values: Vector[VideoRegistration],
    snapshot: CozyArticleMediaRegistry.Snapshot,
    root: RootIdentity
  ): Vector[CozyArticleMediaVideoEvidence.Result] = {
    if (values == null)
      _invalid("Article-media policy video registrations must be defined")
    values.map { value =>
      if (value == null)
        _invalid("Article-media policy video registration must be defined")
      val evidence = CozyArticleMediaVideoEvidence.project(CozyArticleMediaVideoEvidence.Input(
        value.articleIdentity,
        value.locale,
        value.videoName,
        value.videoVersion,
        snapshot,
        root.lexical
      ))
      _evidence_integrity(evidence, CozyArticleMediaIntegrity.Role.Video, "video", root)
      evidence
    }.sortBy(x => _key(_integrity_key(x.integrity)))
  }

  private def _infographic_evidence(
    values: Vector[CozyArticleMediaInfographicEvidence.Input],
    root: RootIdentity
  ): Vector[CozyArticleMediaInfographicEvidence.Result] = {
    if (values == null)
      _invalid("Article-media policy infographic inputs must be defined")
    values.map { value =>
      if (value == null)
        _invalid("Article-media policy infographic input must be defined")
      val evidence = CozyArticleMediaInfographicEvidence.project(value)
      _evidence_integrity(evidence, CozyArticleMediaIntegrity.Role.Infographic, "infographic", root)
      evidence
    }.sortBy(x => _key(_integrity_key(x.integrity)))
  }

  private def _evidence_integrity(
    evidence: CozyArticleMediaVideoEvidence.Result,
    role: CozyArticleMediaIntegrity.Role,
    label: String,
    root: RootIdentity
  ): Unit = {
    if (evidence == null || evidence.integrity == null || evidence.artifactPath == null)
      _invalid(s"Article-media policy $label evidence must be defined")
    _validate_evidence_integrity(evidence.integrity, role, label)
    _validate_evidence_artifact(evidence.artifactPath, evidence.integrity.record, label, root)
  }

  private def _evidence_integrity(
    evidence: CozyArticleMediaInfographicEvidence.Result,
    role: CozyArticleMediaIntegrity.Role,
    label: String,
    root: RootIdentity
  ): Unit = {
    if (evidence == null || evidence.integrity == null || evidence.artifactPath == null)
      _invalid(s"Article-media policy $label evidence must be defined")
    _validate_evidence_integrity(evidence.integrity, role, label)
    _validate_evidence_artifact(evidence.artifactPath, evidence.integrity.record, label, root)
  }

  private def _validate_evidence_integrity(
    integrity: CozyArticleMediaIntegrity.Result,
    role: CozyArticleMediaIntegrity.Role,
    label: String
  ): Unit = {
    if (integrity.record == null || integrity.metadata == null)
      _invalid(s"Article-media policy $label evidence integrity must be defined")
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
      _invalid(s"Article-media policy $label evidence integrity must be canonical")
    if (canonical.record.role != role)
      _invalid(s"Article-media policy $label evidence integrity role is invalid")
  }

  private def _validate_evidence_artifact(
    artifactpath: Path,
    record: CozyArticleMediaIntegrity.Record,
    label: String,
    root: RootIdentity
  ): Unit = {
    if (!artifactpath.isAbsolute || artifactpath != artifactpath.toAbsolutePath.normalize())
      _invalid(s"Article-media policy $label evidence artifact path must be an absolute normalized path")
    val candidate = _validate_record_containment(record, root)
    if (!Files.isRegularFile(artifactpath, LinkOption.NOFOLLOW_LINKS))
      _invalid(s"Article-media policy $label evidence artifact path must be an existing direct regular file")
    val artifactreal = artifactpath.toRealPath()
    val candidatereal = candidate.toRealPath()
    if (artifactpath != artifactreal || artifactreal != candidatereal || !artifactreal.startsWith(root.real))
      _invalid(s"Article-media policy $label evidence artifact path must equal the configured repository artifact")
    if (_sha256(artifactreal) != record.sha256)
      _invalid(s"Article-media policy $label evidence artifact SHA-256 does not match canonical integrity")
  }

  private def _preflight_publication(
    publicationresult: CozyArticleMediaPublication.Result,
    integrities: Vector[CozyArticleMediaIntegrity.Result]
  ): Unit = {
    val media = CozyArticleMediaAssociation.inspectPublication(publicationresult, integrities)
    val mediakeys = media.map(_.key).toSet
    integrities.map(_integrity_key).filterNot(mediakeys.contains).sortBy(_key).headOption.foreach { key =>
      _invalid(s"Article-media policy evidence has no matching native media: ${key.articleIdentity} [${key.locale}, ${key.role.name}]")
    }
    media.foreach { medium =>
      if (medium.key.role == CozyArticleMediaIntegrity.Role.Video && medium.integrity.isEmpty)
        _invalid(s"Missing article-media integrity: ${medium.key.articleIdentity} [${medium.key.locale}, ${medium.key.role.name}]")
    }
  }

  private def _reject_duplicate_integrities(values: Vector[CozyArticleMediaIntegrity.Result]): Unit =
    values.groupBy(_integrity_key).toVector.sortBy(x => _key(x._1)).collectFirst {
      case (key, duplicates) if duplicates.size > 1 => key
    }.foreach { key =>
      _invalid(s"Duplicate article-media policy evidence integrity key: ${key.articleIdentity} [${key.locale}, ${key.role.name}]")
    }

  private def _artifact_root(value: Path): RootIdentity = {
    if (value == null)
      _invalid("Article-media policy artifact repository root must be defined")
    val lexical = value.toAbsolutePath.normalize()
    if (!Files.isDirectory(lexical))
      _invalid(s"Article-media policy artifact repository root must be an existing directory: $value")
    RootIdentity(lexical, lexical.toRealPath())
  }

  private def _validate_record_containment(
    record: CozyArticleMediaIntegrity.Record,
    root: RootIdentity
  ): Path = {
    if (record == null)
      _invalid("Article-media policy integrity record must be defined")
    val repositorypath = CozyArticleMediaNormalization.normalizeRelativePath(record.repositoryPath, "Article-media policy repositoryPath")
    if (repositorypath != record.repositoryPath)
      _invalid(s"Article-media policy repositoryPath must be canonical: ${record.repositoryPath}")
    val candidate = root.lexical.resolve(repositorypath).normalize()
    if (!candidate.startsWith(root.lexical))
      _invalid(s"Article-media policy repository path escapes the configured root: $repositorypath")
    _validate_existing_path_segments(root, candidate, repositorypath)
    candidate
  }

  private def _validate_existing_path_segments(root: RootIdentity, candidate: Path, repositorypath: String): Unit = {
    var current = root.lexical
    val segments = root.lexical.relativize(candidate).iterator()
    while (segments.hasNext) {
      current = current.resolve(segments.next())
      if (Files.exists(current, LinkOption.NOFOLLOW_LINKS)) {
        val real = current.toRealPath()
        if (!real.startsWith(root.real))
          _invalid(s"Article-media policy repository real path escapes the configured root: $repositorypath")
      }
    }
  }

  private def _require_published_artifact(
    record: CozyArticleMediaIntegrity.Record,
    root: RootIdentity
  ): Unit = {
    try {
      val candidate = _validate_record_containment(record, root)
      if (!Files.readAttributes(candidate, classOf[BasicFileAttributes]).isRegularFile)
        _invalid(s"Article-media policy published artifact must be an existing regular file: ${record.repositoryPath}")
      val artifact = candidate.toRealPath()
      if (!artifact.startsWith(root.real))
        _invalid(s"Article-media policy published artifact real path escapes the configured root: ${record.repositoryPath}")
      if (_sha256(artifact) != record.sha256)
        _invalid(s"Article-media policy published artifact SHA-256 does not match canonical integrity: ${record.repositoryPath}")
    } catch {
      case _: NoSuchFileException =>
        _invalid(s"Article-media policy published artifact must be an existing regular file: ${record.repositoryPath}")
    }
  }

  private def _published_artifact_is_available(
    record: CozyArticleMediaIntegrity.Record,
    root: RootIdentity
  ): Boolean = {
    try {
      val candidate = _validate_record_containment(record, root)
      if (!Files.readAttributes(candidate, classOf[BasicFileAttributes]).isRegularFile)
        _invalid(s"Article-media policy published artifact must be a regular file: ${record.repositoryPath}")
      val artifact = candidate.toRealPath()
      if (!artifact.startsWith(root.real))
        _invalid(s"Article-media policy published artifact real path escapes the configured root: ${record.repositoryPath}")
      _sha256(artifact) == record.sha256
    } catch {
      case _: NoSuchFileException => false
    }
  }

  private def _sha256(path: Path): String = {
    val digest = MessageDigest.getInstance("SHA-256")
    val in = Files.newInputStream(path)
    val buffer = new Array[Byte](8192)
    try {
      var n = in.read(buffer)
      while (n >= 0) {
        if (n > 0)
          digest.update(buffer, 0, n)
        n = in.read(buffer)
      }
    } finally {
      in.close()
    }
    digest.digest().map(b => f"${b & 0xff}%02x").mkString
  }

  private def _integrity_key(integrity: CozyArticleMediaIntegrity.Result): CozyArticleMediaAssociation.Key = {
    val record = integrity.record
    CozyArticleMediaAssociation.Key(
      articleIdentity = CozyArticleMediaNormalization.normalizeArticleIdentity(record.articleIdentity),
      locale = CozyArticleMediaNormalization.normalizeLocale(record.locale),
      role = record.role
    )
  }

  private def _key(value: CozyArticleMediaAssociation.Key): (String, String, String) =
    (value.articleIdentity, value.locale, value.role.name)

  private def _diagnostic_kind(value: DiagnosticKind): String =
    value match {
      case DiagnosticKind.MissingMedia => "missing-media"
      case DiagnosticKind.UnavailableMedia => "unavailable-media"
      case _ => _invalid("Article-media policy diagnostic kind is invalid")
    }

  private def _strategy(value: Strategy): Strategy =
    value match {
      case Strategy.Preview | Strategy.Production => value
      case _ => _invalid("Article-media policy strategy is invalid")
    }

  private def _invalid(message: String): Nothing =
    RAISE.invalidArgumentFault(message)
}
