package cozy.publication

import java.net.URI
import java.nio.charset.StandardCharsets
import java.nio.file.attribute.BasicFileAttributes
import java.nio.file.{Files, LinkOption, Path}
import java.security.MessageDigest
import cozy.media.CozyMedia
import org.goldenport.RAISE
import org.smartdox.metadata.PublishMetadata.{ImageReference, VideoPresentation, VideoReference, VideoStatus}
import play.api.libs.json.{JsObject, JsString, Json}
import scala.util.control.NonFatal

/*
 * @since   Aug. 12, 2026
 * @version Aug. 12, 2026
 * @author  ASAMI, Tomoharu
 */
private[cozy] object CozyArticleMediaWipBinding {
  final case class Config(
    descriptorFile: Path,
    publicationRoot: Path,
    websiteRoot: Path,
    target: Option[String] = None
  )

  final case class DirectoryEvidence(
    path: Path,
    identity: Path,
    fileKey: AnyRef
  )

  final case class FileEvidence(
    path: Path,
    identity: Path,
    sha256: String,
    size: Long,
    fileKey: AnyRef
  )

  sealed trait Evidence
  final case class InfographicEvidence(destination: FileEvidence) extends Evidence
  final case class VideoEvidence(
    source: FileEvidence,
    production: FileEvidence,
    destination: Path,
    destinationParent: DirectoryEvidence,
    installedDestination: Option[FileEvidence]
  ) extends Evidence

  final case class Candidate(
    resourceId: String,
    locale: String,
    role: CozyArticleMediaIntegrity.Role,
    variant: CozyArticleMediaPublication.Variant,
    integrity: Option[CozyArticleMediaIntegrity.Result],
    evidence: Evidence
  )

  final case class Plan(
    config: Config,
    descriptor: CozyMedia.Descriptor,
    descriptorEvidence: FileEvidence,
    descriptorBytes: Vector[Byte],
    descriptorRoot: DirectoryEvidence,
    projectRoot: DirectoryEvidence,
    context: cozy.config.CozyProjectContext.Context,
    articleIdentity: String,
    publicationProfile: String,
    profileRoot: DirectoryEvidence,
    publicationRoot: DirectoryEvidence,
    websiteRoot: DirectoryEvidence,
    registry: CozyArticleMediaRegistry.WipReadOnlyPlan,
    candidates: Vector[Candidate]
  )

  def plan(config: Config): Plan =
    _plan(config, Map.empty)

  private def _plan(config: Config, admittedinstalled: Map[Path, String]): Plan = {
    val normalizedconfig = _normalize_config(config)
    val descriptorsnapshot = _file_snapshot(normalizedconfig.descriptorFile, "descriptor")
    val mediaplan = CozyMedia.resolvePlan(
      CozyMedia.CommandConfig(normalizedconfig.descriptorFile, target = normalizedconfig.target),
      descriptorsnapshot.bytes
    )
    if (mediaplan == null || mediaplan.descriptor == null || mediaplan.descriptorFile != normalizedconfig.descriptorFile)
      _invalid("Article-media WIP binding descriptor plan is invalid")
    val descriptorroot = _direct_directory(Option(normalizedconfig.descriptorFile.getParent).getOrElse(
      _invalid("Article-media WIP binding descriptor must have a parent directory")
    ), "descriptor root")
    val descriptor = mediaplan.descriptor
    val association = descriptor.articleMedia.getOrElse(
      _invalid("Article-media WIP binding requires top-level articleMedia")
    )
    if (association == null)
      _invalid("Article-media WIP binding top-level articleMedia must be defined")
    val articleidentity = _two_segment_identity(association.articleIdentity)
    val profile = CozyArticleMediaNormalization.requireExactTrimmed(
      association.publicationProfile,
      "Article-media WIP binding publicationProfile"
    )
    val project = mediaplan.context.project.getOrElse(
      _invalid("Article-media WIP binding requires a discovered project authority")
    )
    if (project.kind.map(_.value) != Some("smartdox-site"))
      _invalid("Article-media WIP binding requires project.kind smartdox-site")
    val projectroot = DirectoryEvidence(project.root.path, project.root.identity, project.root.fileKey)
    val configuration = CozyMedia.requireConfiguredPublicationProfile(mediaplan, profile)
    if (configuration.siteKind != "smartdox")
      _invalid(s"Article-media WIP binding requires configured smartdox site-kind: $profile")
    val effectiveprofile = CozyMedia.effectiveProfile(mediaplan, profile)
    if (effectiveprofile.configuration != Some(configuration))
      _invalid(s"Article-media WIP binding configured publication profile evidence is invalid: $profile")
    val profileroot = _direct_directory(effectiveprofile.resolvedRoot, s"profile $profile root")
    val publicationroot = _direct_directory(normalizedconfig.publicationRoot, "publication root")
    val websiteroot = _direct_directory(normalizedconfig.websiteRoot, "website root")
    val selected = _selected_resources(mediaplan.resources, normalizedconfig.target)
    if (selected.isEmpty)
      _invalid("Article-media WIP binding requires at least one articleMedia resource")
    val drafts = selected.map(_candidate(
      _,
      articleidentity,
      profile,
      normalizedconfig.descriptorFile,
      descriptorroot,
      projectroot,
      profileroot,
      websiteroot
    )).sortBy(_.resourceId)
    _validate_duplicate_tuples(articleidentity, drafts)
    val updates = drafts.map { candidate =>
      CozyArticleMediaRegistry.WipRoleUpdate(candidate.articleIdentity, candidate.variant, candidate.integrity)
    }
    val registry = CozyArticleMediaRegistry.validateWipReadOnly(publicationroot.path, updates)
    val candidates = drafts.map { candidate =>
      candidate.evidence match {
        case x: VideoEvidence if registry.videoStates.get(candidate.key).contains(CozyArticleMediaRegistry.WipVideoState.Fresh) && x.installedDestination.nonEmpty =>
          val admitted = admittedinstalled.get(x.destination)
          if (admitted != Some(x.source.sha256) || x.installedDestination.map(_.sha256) != admitted)
            _invalid(s"Article-media WIP fresh destination must be absent: ${candidate.resourceId}")
        case x: VideoEvidence if registry.videoStates.get(candidate.key).contains(CozyArticleMediaRegistry.WipVideoState.ExactRepeat) =>
          val installed = x.installedDestination.getOrElse(
            _invalid(s"Article-media WIP repeat destination is missing: ${candidate.resourceId}")
          )
          if (installed.sha256 != x.source.sha256)
            _invalid(s"Article-media WIP repeat destination digest differs: ${candidate.resourceId}")
        case _ =>
      }
      candidate.toCandidate
    }
    Plan(
      normalizedconfig,
      descriptor,
      descriptorsnapshot.evidence,
      descriptorsnapshot.bytes,
      descriptorroot,
      projectroot,
      mediaplan.context,
      articleidentity,
      profile,
      profileroot,
      publicationroot,
      websiteroot,
      registry,
      candidates
    )
  }

  def revalidate(value: Plan): Plan = {
    if (value == null || value.config == null)
      _invalid("Article-media WIP binding plan must be defined")
    val recomputed = plan(value.config)
    if (recomputed != value)
      _invalid("Article-media WIP binding evidence has changed")
    recomputed
  }

  private[publication] def revalidateInstalled(value: Plan): Plan = {
    if (value == null || value.config == null)
      _invalid("Article-media WIP binding plan must be defined")
    val admitted = value.candidates.collect {
      case candidate if candidate.evidence.isInstanceOf[VideoEvidence] =>
        val evidence = candidate.evidence.asInstanceOf[VideoEvidence]
        evidence.destination -> evidence.source.sha256
    }.toMap
    val recomputed = _plan(value.config, admitted)
    if (_stable_plan(recomputed) != _stable_plan(value))
      _invalid("Article-media WIP binding evidence has changed after installation")
    recomputed
  }

  private def _stable_plan(value: Plan): (Config, CozyMedia.Descriptor, FileEvidence, Vector[Byte], DirectoryEvidence, DirectoryEvidence,
    cozy.config.CozyProjectContext.Context, String, String, DirectoryEvidence, DirectoryEvidence, DirectoryEvidence, Vector[(String, String, CozyArticleMediaIntegrity.Role, CozyArticleMediaPublication.Variant, Option[CozyArticleMediaIntegrity.Result], Any)]) =
    (
      value.config,
      value.descriptor,
      value.descriptorEvidence,
      value.descriptorBytes,
      value.descriptorRoot,
      value.projectRoot,
      value.context,
      value.articleIdentity,
      value.publicationProfile,
      value.profileRoot,
      value.publicationRoot,
      value.websiteRoot,
      value.candidates.map { candidate =>
        val evidence = candidate.evidence match {
          case x: VideoEvidence => (x.source, x.production, x.destination, x.destinationParent)
          case x => x
        }
        (candidate.resourceId, candidate.locale, candidate.role, candidate.variant, candidate.integrity, evidence)
      }
    )

  private final case class FileSnapshot(evidence: FileEvidence, bytes: Vector[Byte])

  private final case class CandidateDraft(
    articleIdentity: String,
    resourceId: String,
    locale: String,
    role: CozyArticleMediaIntegrity.Role,
    variant: CozyArticleMediaPublication.Variant,
    integrity: Option[CozyArticleMediaIntegrity.Result],
    evidence: Evidence
  ) {
    def key: (String, String, String) = (articleIdentity, locale, role.name)
    def toCandidate: Candidate = Candidate(resourceId, locale, role, variant, integrity, evidence)
  }

  private def _normalize_config(config: Config): Config = {
    if (config == null || config.descriptorFile == null || config.publicationRoot == null ||
      config.websiteRoot == null || config.target == null)
      _invalid("Article-media WIP binding configuration must be defined")
    Config(
      _normalized_host_path(config.descriptorFile, "descriptor"),
      _normalized_host_path(config.publicationRoot, "publication root"),
      _normalized_host_path(config.websiteRoot, "website root"),
      config.target.map(CozyArticleMediaNormalization.requireExactTrimmed(_, "Article-media WIP binding target"))
    )
  }

  private def _selected_resources(
    resources: Vector[CozyMedia.ResolvedResource],
    target: Option[String]
  ): Vector[CozyMedia.ResolvedResource] = {
    if (resources == null || resources.exists(_ == null))
      _invalid("Article-media WIP binding resolved resources must be defined")
    target match {
      case None => resources.filter(_.resource.articleMedia.nonEmpty)
      case Some(targetid) => resources.filter(_.resource.id == targetid) match {
        case Vector(resource) if resource.resource.articleMedia.nonEmpty => Vector(resource)
        case Vector(_) => _invalid(s"Article-media WIP binding target has no articleMedia: $targetid")
        case Vector() => _invalid(s"Article-media WIP binding target does not exist: $targetid")
        case _ => _invalid(s"Article-media WIP binding target is ambiguous: $targetid")
      }
    }
  }

  private def _candidate(
    resolved: CozyMedia.ResolvedResource,
    articleidentity: String,
    profile: String,
    descriptorfile: Path,
    descriptorroot: DirectoryEvidence,
    projectroot: DirectoryEvidence,
    profileroot: DirectoryEvidence,
    websiteroot: DirectoryEvidence
  ): CandidateDraft = {
    val resource = Option(resolved).map(_.resource).getOrElse(
      _invalid("Article-media WIP binding resource must be defined")
    )
    val resourceid = CozyArticleMediaNormalization.requireExactTrimmed(resource.id, "Article-media WIP binding resource id")
    val locale = CozyArticleMediaNormalization.normalizeLocale(resource.language.getOrElse(
      _invalid(s"Article-media WIP binding resource language must be defined: $resourceid")
    ))
    val articlemedia = resource.articleMedia.getOrElse(
      _invalid(s"Article-media WIP binding resource has no articleMedia: $resourceid")
    )
    if (articlemedia == null)
      _invalid(s"Article-media WIP binding resource articleMedia must be defined: $resourceid")
    articlemedia.role match {
      case "infographic" =>
        if (resource.kind != "infographic")
          _invalid(s"Article-media WIP binding infographic resource kind is invalid: $resourceid")
        val publicpath = _site_visible_uri(articlemedia.publicPath.getOrElse(
          _invalid(s"Article-media WIP binding infographic publicPath must be defined: $resourceid")
        ))
        val destination = resolved.publications.getOrElse(profile,
          _invalid(s"Article-media WIP binding infographic has no selected publication profile: $resourceid/$profile")
        )
        val evidence = _root_absolute_file_snapshot(profileroot, destination, s"infographic destination for $resourceid")
        val variant = _normalized_variant(articleidentity, CozyArticleMediaPublication.Variant(
          locale = locale,
          infographic = Some(ImageReference(publicpath, articlemedia.mediaType, articlemedia.alt))
        ))
        CandidateDraft(articleidentity, resourceid, locale, CozyArticleMediaIntegrity.Role.Infographic, variant, None, InfographicEvidence(evidence.evidence))
      case "video" =>
        if (resource.kind != "video")
          _invalid(s"Article-media WIP binding video resource kind is invalid: $resourceid")
        val source = _file_snapshot(resolved.output.getOrElse(
          _invalid(s"Article-media WIP binding video output must be defined: $resourceid")
        ), s"video output for $resourceid")
        if (!source.evidence.path.getFileName.toString.endsWith(".mp4"))
          _invalid(s"Article-media WIP binding video output must be an MP4: $resourceid")
        val productionrelative = CozyArticleMediaNormalization.normalizeRelativePath(articlemedia.production.getOrElse(
          _invalid(s"Article-media WIP binding video production must be defined: $resourceid")
        ), s"Article-media WIP binding video production for $resourceid")
        val production = _root_file_snapshot(descriptorroot, productionrelative, s"video production for $resourceid")
        _validate_production(production.bytes, articleidentity, locale, resourceid, source.evidence.sha256)
        val descriptorrelative = _project_relative(projectroot, descriptorfile, "descriptor")
        val productionprojectrelative = _project_relative(projectroot, production.evidence.path, s"video production for $resourceid")
        val destinationrelative = _video_destination(articleidentity, locale)
        val destination = _root_path(websiteroot, destinationrelative, s"video destination for $resourceid")
        val parent = _direct_directory(Option(destination.getParent).getOrElse(
          _invalid(s"Article-media WIP binding video destination parent is missing: $resourceid")
        ), s"video destination parent for $resourceid")
        val installed = _optional_root_file_snapshot(websiteroot, destinationrelative, s"video destination for $resourceid")
        val contenturl = new URI("/" + destinationrelative)
        val variant = _normalized_variant(articleidentity, CozyArticleMediaPublication.Variant(
          locale = locale,
          video = Some(VideoReference(VideoPresentation.SiteHosted, VideoStatus.Published, None, None, Some(contenturl)))
        ))
        val integrity = CozyArticleMediaIntegrity.produce(CozyArticleMediaIntegrity.Input(
          articleIdentity = articleidentity,
          locale = locale,
          role = CozyArticleMediaIntegrity.Role.Video,
          artifact = CozyArticleMediaIntegrity.Artifact(resourceid, source.evidence.sha256),
          publicPath = contenturl,
          repositoryPath = destinationrelative,
          mediaType = "video/mp4",
          sha256 = source.evidence.sha256,
          provenance = CozyArticleMediaIntegrity.WipSiteVideo(descriptorrelative, resourceid, productionprojectrelative),
          publicationState = CozyArticleMediaIntegrity.PublicationState.Published
        ))
        CandidateDraft(articleidentity, resourceid, locale, CozyArticleMediaIntegrity.Role.Video, variant, Some(integrity),
          VideoEvidence(source.evidence, production.evidence, destination, parent, installed.map(_.evidence)))
      case other =>
        _invalid(s"Article-media WIP binding resource role is invalid: $resourceid/$other")
    }
  }

  private def _validate_duplicate_tuples(articleidentity: String, values: Vector[CandidateDraft]): Unit =
    values.groupBy(x => (articleidentity, x.locale, x.role.name)).toVector.sortBy(_._1).collectFirst {
      case (key, candidates) if candidates.size > 1 => key
    }.foreach { case (_, locale, role) =>
      _invalid(s"Duplicate article-media WIP binding candidate: $locale/$role")
    }

  private def _two_segment_identity(value: String): String = {
    val identity = CozyArticleMediaNormalization.normalizeArticleIdentity(value)
    if (identity.split("/", -1).length != 2)
      _invalid(s"Article-media WIP binding identity must have exactly category/article segments: $identity")
    identity
  }

  private def _video_destination(articleidentity: String, locale: String): String = {
    val segments = articleidentity.split("/", -1)
    s"$locale/${segments(0)}/videos/${segments(1)}.mp4"
  }

  private def _validate_production(
    bytes: Vector[Byte],
    articleidentity: String,
    locale: String,
    resourceid: String,
    sourcedigest: String
  ): Unit = {
    val root = try Json.parse(new String(bytes.toArray, StandardCharsets.UTF_8)).asOpt[JsObject] catch {
      case NonFatal(_) => None
    }
    val production = root.getOrElse(_invalid(s"Article-media WIP binding video production must be a JSON object: $resourceid"))
    val category = _required_json_string(production, "category", resourceid)
    val article = _required_json_string(production, "article", resourceid)
    if (_two_segment_identity(s"$category/$article") != articleidentity)
      _invalid(s"Article-media WIP binding video production identity differs: $resourceid")
    if (_required_json_string(production, "language", resourceid) != locale)
      _invalid(s"Article-media WIP binding video production language differs: $resourceid")
    val render = _required_json_object(production, "render", resourceid)
    if (_required_json_string(render, "status", resourceid) != "completed")
      _invalid(s"Article-media WIP binding video render status must be completed: $resourceid")
    val qa = _required_json_object(render, "qa", resourceid)
    if (_required_json_string(qa, "status", resourceid) != "technical-and-visual-qa-passed")
      _invalid(s"Article-media WIP binding video render QA status is invalid: $resourceid")
    val digest = render.value.get("sha256") match {
      case Some(JsString(value)) if value.matches("[0-9a-f]{64}") => value
      case _ => _invalid(s"Article-media WIP binding video render sha256 must be exact lowercase SHA-256: $resourceid")
    }
    if (digest != sourcedigest)
      _invalid(s"Article-media WIP binding video render sha256 differs from output: $resourceid")
  }

  private def _normalized_variant(
    articleidentity: String,
    variant: CozyArticleMediaPublication.Variant
  ): CozyArticleMediaPublication.Variant = {
    val result = CozyArticleMediaPublication.produce(articleidentity, Vector(variant))
    val value = result.publication.variants.head
    CozyArticleMediaPublication.Variant(value.locale, value.infographic, value.video)
  }

  private def _site_visible_uri(value: String): URI = {
    val raw = CozyArticleMediaNormalization.requireExactTrimmed(value, "Article-media WIP binding infographic publicPath")
    val uri = try new URI(raw) catch {
      case NonFatal(_) => _invalid(s"Article-media WIP binding infographic publicPath is invalid: $raw")
    }
    if (uri.toString != raw)
      _invalid(s"Article-media WIP binding infographic publicPath is not exact: $raw")
    CozyArticleMediaNormalization.validateSiteVisibleUri(uri, "Article-media WIP binding infographic publicPath")
    uri
  }

  private def _project_relative(root: DirectoryEvidence, path: Path, label: String): String = {
    val normalized = _normalized_host_path(path, label)
    val identity = try normalized.toRealPath() catch {
      case NonFatal(e) => _invalid(s"Article-media WIP binding $label cannot be resolved: ${e.getMessage}")
    }
    if (!normalized.startsWith(root.path) || !identity.startsWith(root.identity))
      _invalid(s"Article-media WIP binding $label escapes discovered project root")
    val relative = root.path.relativize(normalized).toString.replace('\\', '/')
    CozyArticleMediaNormalization.normalizeRelativePath(relative, s"Article-media WIP binding $label")
  }

  private def _root_path(root: DirectoryEvidence, relative: String, label: String): Path = {
    val path = root.path.resolve(relative).normalize()
    if (!path.startsWith(root.path) || path == root.path)
      _invalid(s"Article-media WIP binding $label escapes its root")
    path
  }

  private def _root_file_snapshot(root: DirectoryEvidence, relative: String, label: String): FileSnapshot = {
    val path = _root_path(root, relative, label)
    _root_absolute_file_snapshot(root, path, label)
  }

  private def _root_absolute_file_snapshot(root: DirectoryEvidence, path: Path, label: String): FileSnapshot = {
    val normalized = _normalized_host_path(path, label)
    if (!normalized.startsWith(root.path) || normalized == root.path)
      _invalid(s"Article-media WIP binding $label escapes its root")
    val snapshot = _file_snapshot(normalized, label)
    if (!snapshot.evidence.identity.startsWith(root.identity))
      _invalid(s"Article-media WIP binding $label real identity escapes its root")
    snapshot
  }

  private def _optional_root_file_snapshot(root: DirectoryEvidence, relative: String, label: String): Option[FileSnapshot] = {
    val path = _root_path(root, relative, label)
    if (!Files.exists(path, LinkOption.NOFOLLOW_LINKS)) None else Some(_root_file_snapshot(root, relative, label))
  }

  private def _file_snapshot(path: Path, label: String): FileSnapshot = {
    val file = _direct_regular_file(path, label)
    val before = _file_attributes(file.path, label)
    val firstbytes = try Files.readAllBytes(file.path) catch {
      case NonFatal(e) => _invalid(s"Article-media WIP binding $label cannot be read: ${e.getMessage}")
    }
    val middle = _file_attributes(file.path, label)
    val secondbytes = try Files.readAllBytes(file.path) catch {
      case NonFatal(e) => _invalid(s"Article-media WIP binding $label cannot be read: ${e.getMessage}")
    }
    val after = _file_attributes(file.path, label)
    if (file.fileKey != before.fileKey() || !_same_file_attributes(before, middle) ||
      !_same_file_attributes(before, after) || firstbytes.length.toLong != before.size() ||
      secondbytes.length.toLong != before.size() || !firstbytes.sameElements(secondbytes))
      _invalid(s"Article-media WIP binding $label changed while being read")
    FileSnapshot(FileEvidence(file.path, file.identity, _sha256(firstbytes), firstbytes.length.toLong, file.fileKey), firstbytes.toVector)
  }

  private def _same_file_attributes(before: BasicFileAttributes, after: BasicFileAttributes): Boolean =
    before.fileKey() == after.fileKey() && before.size() == after.size() &&
      before.lastModifiedTime() == after.lastModifiedTime()

  private def _direct_regular_file(path: Path, label: String): FileEvidence = {
    val lexical = _normalized_host_path(path, label)
    val attributes = _file_attributes(lexical, label)
    val identity = try lexical.toRealPath() catch {
      case NonFatal(e) => _invalid(s"Article-media WIP binding $label cannot be resolved: ${e.getMessage}")
    }
    if (identity != lexical)
      _invalid(s"Article-media WIP binding $label must not use a lexical or symlink alias: $lexical")
    FileEvidence(lexical, identity, "", attributes.size(), attributes.fileKey())
  }

  private def _file_attributes(path: Path, label: String): BasicFileAttributes = {
    if (Files.isSymbolicLink(path) || !Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS))
      _invalid(s"Article-media WIP binding $label must be an existing direct regular non-symlink file: $path")
    val attributes = try Files.readAttributes(path, classOf[BasicFileAttributes], LinkOption.NOFOLLOW_LINKS) catch {
      case NonFatal(e) => _invalid(s"Article-media WIP binding $label attributes cannot be read: ${e.getMessage}")
    }
    if (attributes.fileKey() == null)
      _invalid(s"Article-media WIP binding $label has no stable file identity: $path")
    attributes
  }

  private def _direct_directory(path: Path, label: String): DirectoryEvidence = {
    val lexical = _normalized_host_path(path, label)
    if (Files.isSymbolicLink(lexical) || !Files.isDirectory(lexical, LinkOption.NOFOLLOW_LINKS))
      _invalid(s"Article-media WIP binding $label must be an existing direct non-symlink directory: $lexical")
    val identity = try lexical.toRealPath() catch {
      case NonFatal(e) => _invalid(s"Article-media WIP binding $label cannot be resolved: ${e.getMessage}")
    }
    if (identity != lexical)
      _invalid(s"Article-media WIP binding $label must not use a lexical or symlink alias: $lexical")
    val attributes = try Files.readAttributes(lexical, classOf[BasicFileAttributes], LinkOption.NOFOLLOW_LINKS) catch {
      case NonFatal(e) => _invalid(s"Article-media WIP binding $label attributes cannot be read: ${e.getMessage}")
    }
    if (attributes.fileKey() == null)
      _invalid(s"Article-media WIP binding $label has no stable directory identity: $lexical")
    DirectoryEvidence(lexical, identity, attributes.fileKey())
  }

  private def _normalized_host_path(path: Path, label: String): Path = {
    if (path == null)
      _invalid(s"Article-media WIP binding $label path must be defined")
    path.toAbsolutePath.normalize()
  }

  private def _required_json_object(value: JsObject, field: String, resourceid: String): JsObject =
    value.value.get(field).collect { case x: JsObject => x }.getOrElse(
      _invalid(s"Article-media WIP binding video production $field must be an object: $resourceid")
    )

  private def _required_json_string(value: JsObject, field: String, resourceid: String): String =
    value.value.get(field).collect { case JsString(x) if x.nonEmpty && x == x.trim => x }.getOrElse(
      _invalid(s"Article-media WIP binding video production $field must be a non-empty exact string: $resourceid")
    )

  private def _sha256(bytes: Array[Byte]): String = {
    val digest = MessageDigest.getInstance("SHA-256")
    digest.update(bytes)
    digest.digest().map(x => f"${x & 0xff}%02x").mkString
  }

  private def _invalid(message: String): Nothing =
    RAISE.invalidArgumentFault(message)
}
