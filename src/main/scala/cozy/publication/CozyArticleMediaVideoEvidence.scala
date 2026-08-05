package cozy.publication

import java.net.URI
import java.nio.file.{Files, LinkOption, Path}
import java.security.MessageDigest
import org.goldenport.RAISE
import play.api.libs.json.{JsArray, JsObject, JsString, JsValue}

/*
 * @since   Aug.  4, 2026
 * @version Aug.  5, 2026
 * @author  ASAMI, Tomoharu
 */
private[cozy] object CozyArticleMediaVideoEvidence {
  final case class Input(
    articleIdentity: String,
    locale: String,
    videoName: String,
    videoVersion: String,
    snapshot: CozyArticleMediaRegistry.Snapshot,
    repositoryRoot: Path,
    stagedArtifact: Option[Path] = None
  )

  final case class Result(
    integrity: CozyArticleMediaIntegrity.Result,
    artifactPath: Path
  )

  def project(input: Input): Result = {
    val normalized = _normalize_input(input)
    val manifestpath = s"metadata/video/${normalized.videoname}/${normalized.videoversion}/manifest.json"
    val registrypath = s"metadata/artifacts/repository/${normalized.videoname}.json"
    val manifest = _selected_object(normalized.entries, manifestpath, "video manifest")
    val registry = _selected_object(normalized.entries, registrypath, "repository registry")
    val evidence = _validate_evidence(manifest, registry, normalized.videoname, normalized.videoversion)
    val artifactpath = _resolve_artifact(normalized.repositoryroot, evidence.repositorypath, normalized.stagedartifact)
    val actualsha = _sha256(normalized.stagedartifact.getOrElse(artifactpath))
    if (actualsha != evidence.sha256)
      _invalid(s"Article-media video artifact SHA-256 does not match selected evidence: ${evidence.repositorypath}")
    Result(
      integrity = CozyArticleMediaIntegrity.produce(CozyArticleMediaIntegrity.Input(
        articleIdentity = normalized.articleidentity,
        locale = normalized.locale,
        role = CozyArticleMediaIntegrity.Role.Video,
        artifact = CozyArticleMediaIntegrity.Artifact(normalized.videoname, normalized.videoversion),
        publicPath = new URI(s"/${evidence.repositorypublicpath}"),
        repositoryPath = evidence.repositorypath,
        mediaType = "video/mp4",
        sha256 = evidence.sha256,
        provenance = CozyArticleMediaIntegrity.VideoPublication(manifestpath, registrypath),
        publicationState = evidence.publicationstate
      )),
      artifactPath = artifactpath
    )
  }

  private final case class NormalizedInput(
    articleidentity: String,
    locale: String,
    videoname: String,
    videoversion: String,
    entries: Vector[CozyArticleMediaRegistry.Entry],
    repositoryroot: Path,
    stagedartifact: Option[Path]
  )

  private final case class Evidence(
    repositorypath: String,
    repositorypublicpath: String,
    sha256: String,
    publicationstate: CozyArticleMediaIntegrity.PublicationState
  )

  private def _normalize_input(input: Input): NormalizedInput = {
    if (input == null)
      _invalid("Article-media video evidence input must be defined")
    if (input.snapshot == null)
      _invalid("Article-media video evidence snapshot must be defined")
    if (input.repositoryRoot == null)
      _invalid("Article-media video evidence repository root must be defined")
    val entries = Option(input.snapshot.entries).getOrElse(
      _invalid("Article-media video evidence snapshot entries must be defined")
    )
    if (entries.exists(_ == null))
      _invalid("Article-media video evidence snapshot entries must not contain null")
    val articleidentity = CozyArticleMediaNormalization.normalizeArticleIdentity(input.articleIdentity)
    val locale = CozyArticleMediaNormalization.normalizeLocale(input.locale)
    val videoname = _safe_segment(input.videoName, "video name")
    val videoversion = _safe_segment(input.videoVersion, "video version")
    val stagedartifact = Option(input.stagedArtifact).flatten.map(_direct_staged_artifact)
    val repositoryroot = stagedartifact.fold(_existing_repository_root(input.repositoryRoot))(_ => _staged_repository_root(input.repositoryRoot))
    NormalizedInput(articleidentity, locale, videoname, videoversion, entries, repositoryroot, stagedartifact)
  }

  private def _existing_repository_root(value: Path): Path = {
    if (!Files.isDirectory(value))
      _invalid(s"Article-media video evidence repository root must be an existing directory: $value")
    value.toRealPath()
  }

  private def _staged_repository_root(value: Path): Path = {
    val repositoryroot = value.toAbsolutePath.normalize()
    var ancestor = repositoryroot
    while (!Files.exists(ancestor, LinkOption.NOFOLLOW_LINKS)) {
      ancestor = Option(ancestor.getParent).getOrElse(
        _invalid(s"Article-media video evidence repository root has no existing ancestor: $value")
      )
    }
    _direct_directory(ancestor, "repository root nearest existing ancestor")
    val relative = ancestor.relativize(repositoryroot)
    var segment = ancestor
    (0 until relative.getNameCount).foreach { index =>
      segment = segment.resolve(relative.getName(index))
      if (Files.exists(segment, LinkOption.NOFOLLOW_LINKS))
        _direct_directory(segment, "repository root")
    }
    repositoryroot
  }

  private def _direct_directory(value: Path, label: String): Unit = {
    if (Files.isSymbolicLink(value) || !Files.isDirectory(value, LinkOption.NOFOLLOW_LINKS))
      _invalid(s"Article-media video evidence $label must be a direct non-symlink directory: $value")
  }

  private def _safe_segment(value: String, label: String): String = {
    val segment = Option(value).getOrElse("")
    if (segment.isEmpty || segment != segment.trim || segment.contains('/') || segment.contains('\\') ||
      segment.contains('\u0000') || segment == "." || segment == ".." || !segment.matches("[A-Za-z0-9][A-Za-z0-9._-]*"))
      _invalid(s"Article-media video $label must be a safe exact path segment: $value")
    segment
  }

  private def _selected_object(
    entries: Vector[CozyArticleMediaRegistry.Entry],
    path: String,
    label: String
  ): JsObject = {
    val selected = entries.filter(_.path == path)
    if (selected.isEmpty)
      _invalid(s"Article-media video $label is missing from the configured snapshot: $path")
    if (selected.size > 1)
      _invalid(s"Article-media video $label is ambiguous in the configured snapshot: $path")
    Option(selected.head.metadata).flatMap(_.asOpt[JsObject]).getOrElse(
      _invalid(s"Article-media video $label must be a JSON object: $path")
    )
  }

  private def _validate_evidence(
    manifest: JsObject,
    registry: JsObject,
    videoname: String,
    videoversion: String
  ): Evidence = {
    _require_exact(manifest, "schema", "cozy.publish-project.v1", "video manifest")
    _require_exact(manifest, "type", "video-registry-manifest", "video manifest")
    val video = _required_object(manifest, "video", "video manifest")
    _require_exact(video, "name", videoname, "video manifest video")
    _require_exact(video, "version", videoversion, "video manifest video")
    val artifact = _required_object(manifest, "artifact", "video manifest")
    val warehousepath = _repository_path(_required_exact_string(artifact, "warehousePath", "video manifest artifact"), "video manifest artifact.warehousePath")
    if (!warehousepath.endsWith(".mp4"))
      _invalid(s"Article-media video manifest warehousePath must end with .mp4: $warehousepath")
    val repositorypublicpath = _repository_path(_required_exact_string(artifact, "repositoryPublicPath", "video manifest artifact"), "video manifest artifact.repositoryPublicPath")
    val manifestsha = _sha256_string(_required_exact_string(artifact, "sha256", "video manifest artifact"), "video manifest artifact.sha256")

    _require_exact(registry, "schema", "cozy.publish-project.v1", "repository registry")
    _require_exact(registry, "type", "repository-artifact", "repository registry")
    val project = _required_object(registry, "project", "repository registry")
    _require_exact(project, "name", videoname, "repository registry project")
    _require_exact(project, "version", videoversion, "repository registry project")
    _require_exact(project, "kind", "video", "repository registry project")
    val registryartifact = _required_object(registry, "artifact", "repository registry")
    val publicationstate = _publication_state(_required_exact_string(registryartifact, "status", "repository registry artifact"))
    val files = registryartifact.value.get("files") match {
      case Some(JsArray(values)) => values.toVector
      case _ => _invalid("Article-media video repository registry artifact.files must be an array")
    }
    val selected = files.collect {
      case value: JsObject if _string(value, "type").contains("video") &&
        _string(value, "version").contains(videoversion) &&
        _string(value, "warehousePath").contains(warehousepath) => value
    }
    if (selected.isEmpty)
      _invalid("Article-media video repository registry has no selected video artifact file")
    if (selected.size > 1)
      _invalid("Article-media video repository registry has ambiguous selected video artifact files")
    val selectedfile = selected.head
    _require_exact(selectedfile, "extension", "mp4", "repository registry selected video file")
    val registrysha = _sha256_string(_required_exact_string(selectedfile, "sha256", "repository registry selected video file"), "repository registry selected video file.sha256")
    if (registrysha != manifestsha)
      _invalid("Article-media video manifest and repository registry SHA-256 values must match")
    Evidence(warehousepath.stripPrefix("repository/"), repositorypublicpath, manifestsha, publicationstate)
  }

  private def _repository_path(value: String, label: String): String = {
    val normalized = CozyArticleMediaNormalization.normalizeRelativePath(value, label)
    if (!normalized.startsWith("repository/"))
      _invalid(s"$label must begin with repository/: $value")
    normalized
  }

  private def _resolve_artifact(repositoryroot: Path, repositorypath: String, stagedartifact: Option[Path]): Path = {
    val candidate = repositoryroot.resolve(repositorypath).normalize()
    if (!candidate.startsWith(repositoryroot))
      _invalid(s"Article-media video artifact path escapes the configured repository root: $repositorypath")
    stagedartifact match {
      case Some(_) =>
        _validate_staged_target(repositoryroot, candidate, repositorypath)
        candidate
      case None =>
        if (!Files.isRegularFile(candidate))
          _invalid(s"Article-media video artifact must be an existing regular file: $repositorypath")
        val artifactpath = candidate.toRealPath()
        if (!artifactpath.startsWith(repositoryroot))
          _invalid(s"Article-media video artifact real path escapes the configured repository root: $repositorypath")
        artifactpath
    }
  }

  private def _direct_staged_artifact(value: Path): Path = {
    if (value == null || Files.isSymbolicLink(value) || !Files.isRegularFile(value, LinkOption.NOFOLLOW_LINKS))
      _invalid(s"Article-media video staged artifact must be a direct regular non-symlink file: $value")
    value.toRealPath()
  }

  private def _validate_staged_target(repositoryroot: Path, candidate: Path, repositorypath: String): Unit = {
    val relative = repositoryroot.relativize(candidate)
    var parent = repositoryroot
    (0 until (relative.getNameCount - 1)).foreach { index =>
      parent = parent.resolve(relative.getName(index))
      if (Files.exists(parent, LinkOption.NOFOLLOW_LINKS)) {
        if (Files.isSymbolicLink(parent))
          _invalid(s"Article-media video artifact parent must not be a symlink: $repositorypath")
        if (!Files.isDirectory(parent, LinkOption.NOFOLLOW_LINKS))
          _invalid(s"Article-media video artifact parent must be a direct directory: $repositorypath")
      }
    }
    if (Files.exists(candidate, LinkOption.NOFOLLOW_LINKS)) {
      if (Files.isSymbolicLink(candidate))
        _invalid(s"Article-media video artifact target must not be a symlink: $repositorypath")
      if (!Files.isRegularFile(candidate, LinkOption.NOFOLLOW_LINKS))
        _invalid(s"Article-media video artifact target must be a regular file when it exists: $repositorypath")
    }
  }

  private def _required_object(value: JsObject, field: String, label: String): JsObject =
    value.value.get(field).flatMap(_.asOpt[JsObject]).getOrElse(
      _invalid(s"Article-media video $label.$field must be a JSON object")
    )

  private def _required_exact_string(value: JsObject, field: String, label: String): String =
    _string(value, field).filter(x => x.nonEmpty && x == x.trim).getOrElse(
      _invalid(s"Article-media video $label.$field must be a non-empty exact string")
    )

  private def _require_exact(value: JsObject, field: String, expected: String, label: String): Unit =
    if (_string(value, field) != Some(expected))
      _invalid(s"Article-media video $label.$field must be exactly $expected")

  private def _string(value: JsObject, field: String): Option[String] =
    value.value.get(field).collect { case JsString(x) => x }

  private def _sha256_string(value: String, label: String): String = {
    if (!value.matches("[0-9a-f]{64}"))
      _invalid(s"Article-media video $label must be 64 lowercase hexadecimal characters: $value")
    value
  }

  private def _publication_state(value: String): CozyArticleMediaIntegrity.PublicationState =
    value match {
      case "registered" => CozyArticleMediaIntegrity.PublicationState.Registered
      case "published" => CozyArticleMediaIntegrity.PublicationState.Published
      case "withdrawn" => CozyArticleMediaIntegrity.PublicationState.Withdrawn
      case _ => _invalid(s"Article-media video repository registry artifact.status is invalid: $value")
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

  private def _invalid(message: String): Nothing =
    RAISE.invalidArgumentFault(message)
}
