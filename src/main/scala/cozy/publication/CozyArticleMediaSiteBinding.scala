package cozy.publication

import java.net.URI
import java.nio.charset.StandardCharsets
import java.nio.file.{Files, LinkOption, Path}
import java.security.MessageDigest
import cozy.media.CozyMedia
import org.goldenport.RAISE
import org.smartdox.metadata.PublishMetadata.{ImageReference, VideoPresentation, VideoReference, VideoStatus}
import play.api.libs.json.{JsObject, JsString, Json}
import scala.util.control.NonFatal

/*
 * @since   Aug. 11, 2026
 * @version Aug. 11, 2026
 * @author  ASAMI, Tomoharu
 */
private[cozy] object CozyArticleMediaSiteBinding {
  final case class Config(
    descriptorFile: Path,
    target: Option[String] = None
  )

  sealed trait Role {
    def serializedName: String
  }
  object Role {
    case object Infographic extends Role {
      val serializedName = "infographic"
    }
    case object Video extends Role {
      val serializedName = "video"
    }
  }

  final case class FileEvidence(
    path: Path,
    identity: Path,
    sha256: String,
    size: Long
  )

  sealed trait Evidence {
    def file: FileEvidence
  }
  final case class InfographicEvidence(destination: FileEvidence) extends Evidence {
    def file: FileEvidence = destination
  }
  final case class VideoEvidence(production: FileEvidence) extends Evidence {
    def file: FileEvidence = production
  }

  final case class Candidate(
    resourceId: String,
    locale: String,
    role: Role,
    variant: CozyArticleMediaPublication.Variant,
    evidence: Evidence
  )

  final case class Plan(
    config: Config,
    descriptor: CozyMedia.Descriptor,
    descriptorEvidence: FileEvidence,
    descriptorBytes: Vector[Byte],
    descriptorRoot: Path,
    descriptorRootIdentity: Path,
    articleIdentity: String,
    publicationProfile: String,
    profileRoot: Path,
    profileRootIdentity: Path,
    candidates: Vector[Candidate]
  )

  def plan(config: Config): Plan = {
    if (config == null || config.descriptorFile == null || config.target == null)
      _invalid("Article-media site binding configuration must be defined")
    val descriptorfile = _normalized_host_path(config.descriptorFile, "descriptor")
    _direct_regular_file_identity(descriptorfile, "descriptor")
    val mediaplan = CozyMedia.resolvePlan(CozyMedia.CommandConfig(descriptorfile, target = config.target))
    if (mediaplan == null || mediaplan.descriptor == null || mediaplan.descriptorFile != descriptorfile)
      _invalid("Article-media site binding descriptor plan is invalid")
    val descriptorroot = Option(descriptorfile.getParent).getOrElse(
      _invalid("Article-media site binding descriptor must have a parent directory")
    )
    val descriptorrootidentity = _direct_directory_identity(descriptorroot, "descriptor root")
    val descriptorsnapshot = _file_snapshot(descriptorfile, "descriptor")
    val descriptor = mediaplan.descriptor
    if (descriptor.resources == null || descriptor.profiles == null)
      _invalid("Article-media site binding descriptor must define resources and profiles")
    val association = descriptor.articleMedia.getOrElse(
      _invalid("Article-media site binding requires top-level articleMedia")
    )
    if (association == null)
      _invalid("Article-media site binding top-level articleMedia must be defined")
    val articleidentity = CozyArticleMediaNormalization.normalizeArticleIdentity(association.articleIdentity)
    val profile = CozyArticleMediaNormalization.requireExactTrimmed(
      association.publicationProfile,
      "Article-media site binding publicationProfile"
    )
    val profileroot = _profile_root(descriptorroot, descriptor, profile)
    val selected = _selected_resources(descriptor, config.target)
    if (selected.isEmpty)
      _invalid("Article-media site binding requires at least one articleMedia resource")
    val candidates = selected.map(_candidate(
      _,
      articleidentity,
      profile,
      DirectoryIdentity(descriptorroot, descriptorrootidentity),
      profileroot
    )).sortBy(_.resourceId)
    val duplicates = candidates.groupBy(x => (articleidentity, x.locale, x.role.serializedName)).collect {
      case (key, values) if values.size > 1 => key
    }.toVector.sortBy(x => (x._1, x._2, x._3))
    duplicates.headOption.foreach { case (_, locale, role) =>
      _invalid(s"Duplicate article-media site binding candidate: $locale/$role")
    }
    Plan(
      config = config,
      descriptor = descriptor,
      descriptorEvidence = descriptorsnapshot.evidence,
      descriptorBytes = descriptorsnapshot.bytes,
      descriptorRoot = descriptorroot,
      descriptorRootIdentity = descriptorrootidentity,
      articleIdentity = articleidentity,
      publicationProfile = profile,
      profileRoot = profileroot.lexical,
      profileRootIdentity = profileroot.identity,
      candidates = candidates
    )
  }

  def revalidate(value: Plan): Plan = {
    if (value == null || value.config == null)
      _invalid("Article-media site binding plan must be defined")
    val recomputed = plan(value.config)
    if (recomputed != value)
      _invalid("Article-media site binding evidence has changed")
    recomputed
  }

  private final case class FileSnapshot(evidence: FileEvidence, bytes: Vector[Byte])

  private final case class FileIdentity(lexical: Path, identity: Path)

  private final case class DirectoryIdentity(lexical: Path, identity: Path)

  private def _selected_resources(
    descriptor: CozyMedia.Descriptor,
    target: Option[String]
  ): Vector[CozyMedia.Resource] = {
    val resources = Option(descriptor.resources).getOrElse(
      _invalid("Article-media site binding descriptor resources must be defined")
    )
    if (resources.exists(_ == null))
      _invalid("Article-media site binding descriptor resources must not contain null")
    target match {
      case None => resources.filter(_.articleMedia.nonEmpty)
      case Some(value) =>
        val targetid = CozyArticleMediaNormalization.requireExactTrimmed(value, "Article-media site binding target")
        resources.filter(_.id == targetid) match {
          case Vector(resource) if resource.articleMedia.nonEmpty => Vector(resource)
          case Vector(_) => _invalid(s"Article-media site binding target has no articleMedia: $targetid")
          case Vector() => _invalid(s"Article-media site binding target does not exist: $targetid")
          case _ => _invalid(s"Article-media site binding target is ambiguous: $targetid")
        }
    }
  }

  private def _candidate(
    resource: CozyMedia.Resource,
    articleidentity: String,
    profile: String,
    descriptorroot: DirectoryIdentity,
    profileroot: DirectoryIdentity
  ): Candidate = {
    val resourceid = CozyArticleMediaNormalization.requireExactTrimmed(
      resource.id,
      "Article-media site binding resource id"
    )
    val language = resource.language.getOrElse(
      _invalid(s"Article-media site binding resource language must be defined: $resourceid")
    )
    val locale = CozyArticleMediaNormalization.normalizeLocale(language)
    val articlemedia = resource.articleMedia.getOrElse(
      _invalid(s"Article-media site binding resource has no articleMedia: $resourceid")
    )
    if (articlemedia == null)
      _invalid(s"Article-media site binding resource articleMedia must be defined: $resourceid")
    articlemedia.role match {
      case "infographic" =>
        if (resource.kind != "infographic")
          _invalid(s"Article-media site binding infographic resource kind is invalid: $resourceid")
        val publicpath = _site_visible_uri(articlemedia.publicPath.getOrElse(
          _invalid(s"Article-media site binding infographic publicPath must be defined: $resourceid")
        ))
        val publication = _publication_destination(resource, resourceid, profile)
        val destination = _root_file_snapshot(profileroot, publication, s"infographic destination for $resourceid")
        val variant = _normalized_variant(articleidentity, CozyArticleMediaPublication.Variant(
          locale = locale,
          infographic = Some(ImageReference(publicpath, articlemedia.mediaType, articlemedia.alt))
        ))
        Candidate(resourceid, locale, Role.Infographic, variant, InfographicEvidence(destination.evidence))
      case "video" =>
        if (resource.kind != "video")
          _invalid(s"Article-media site binding video resource kind is invalid: $resourceid")
        val production = _relative_path(articlemedia.production.getOrElse(
          _invalid(s"Article-media site binding video production must be defined: $resourceid")
        ), s"Article-media site binding video production for $resourceid")
        val evidence = _root_file_snapshot(descriptorroot, production, s"video production for $resourceid")
        val video = _production_video(
          evidence.bytes,
          articleidentity,
          locale,
          resourceid
        )
        val variant = _normalized_variant(articleidentity, CozyArticleMediaPublication.Variant(
          locale = locale,
          video = Some(video)
        ))
        Candidate(resourceid, locale, Role.Video, variant, VideoEvidence(evidence.evidence))
      case other =>
        _invalid(s"Article-media site binding resource role is invalid: $resourceid/$other")
    }
  }

  private def _publication_destination(resource: CozyMedia.Resource, resourceid: String, profile: String): String = {
    val publications = Option(resource.publications).getOrElse(
      _invalid(s"Article-media site binding resource publications must be defined: $resourceid")
    )
    val value = publications.getOrElse(profile,
      _invalid(s"Article-media site binding resource has no selected publication profile: $resourceid/$profile")
    )
    _relative_path(value, s"Article-media site binding infographic publication for $resourceid")
  }

  private def _profile_root(
    descriptorroot: Path,
    descriptor: CozyMedia.Descriptor,
    profile: String
  ): DirectoryIdentity = {
    val selected = Option(descriptor.profiles).flatMap(_.get(profile)).getOrElse(
      _invalid(s"Article-media site binding publication profile does not exist: $profile")
    )
    if (selected == null)
      _invalid(s"Article-media site binding publication profile must be defined: $profile")
    selected.rootEnv match {
      case Some(name) =>
        val envname = CozyArticleMediaNormalization.requireExactTrimmed(
          name,
          s"Article-media site binding profile $profile rootEnv"
        )
        val value = sys.env.get(envname).filter(x => x.nonEmpty && x == x.trim).getOrElse(
          _invalid(s"Article-media site binding profile $profile rootEnv is missing: $envname")
        )
        _direct_directory(try Path.of(value) catch {
          case NonFatal(_) => _invalid(s"Article-media site binding profile $profile rootEnv is invalid")
        }, s"profile $profile rootEnv")
      case None =>
        selected.root match {
          case Some(value) =>
            val relative = _relative_path(value, s"Article-media site binding profile $profile root")
            val path = descriptorroot.resolve(relative).normalize()
            if (!path.startsWith(descriptorroot))
              _invalid(s"Article-media site binding profile $profile root escapes descriptor root")
            _direct_directory(path, s"profile $profile root")
          case None => _direct_directory(descriptorroot, s"profile $profile default root")
        }
    }
  }

  private def _production_video(
    bytes: Vector[Byte],
    articleidentity: String,
    locale: String,
    resourceid: String
  ): VideoReference = {
    val json = try Json.parse(new String(bytes.toArray, StandardCharsets.UTF_8)) catch {
      case NonFatal(_) => _invalid(s"Article-media site binding video production must contain JSON: $resourceid")
    }
    val root = json.asOpt[JsObject].getOrElse(
      _invalid(s"Article-media site binding video production must be a JSON object: $resourceid")
    )
    val category = _required_json_string(root, "category", resourceid)
    val article = _required_json_string(root, "article", resourceid)
    val language = _required_json_string(root, "language", resourceid)
    val productionidentity = CozyArticleMediaNormalization.normalizeArticleIdentity(s"$category/$article")
    if (productionidentity != articleidentity)
      _invalid(s"Article-media site binding video production identity differs: $resourceid")
    if (language != locale)
      _invalid(s"Article-media site binding video production language differs: $resourceid")
    val render = _required_json_object(root, "render", resourceid)
    if (_required_json_string(render, "status", resourceid) != "completed")
      _invalid(s"Article-media site binding video render status must be completed: $resourceid")
    val qa = _required_json_object(render, "qa", resourceid)
    if (_required_json_string(qa, "status", resourceid) != "technical-and-visual-qa-passed")
      _invalid(s"Article-media site binding video render QA status is invalid: $resourceid")
    val youtube = _required_json_object(root, "youtube", resourceid)
    if (_required_json_string(youtube, "status", resourceid) != "published")
      _invalid(s"Article-media site binding video YouTube status must be published: $resourceid")
    val url = _youtube_url(_required_json_string(youtube, "videoUrl", resourceid), resourceid)
    VideoReference(
      VideoPresentation.ExternalLink,
      VideoStatus.Published,
      Some("youtube"),
      Some(url),
      None
    )
  }

  private def _required_json_object(value: JsObject, field: String, resourceid: String): JsObject =
    value.value.get(field).collect { case x: JsObject => x }.getOrElse(
      _invalid(s"Article-media site binding video production $field must be an object: $resourceid")
    )

  private def _required_json_string(value: JsObject, field: String, resourceid: String): String =
    value.value.get(field).collect { case JsString(x) if x.nonEmpty && x == x.trim => x }.getOrElse(
      _invalid(s"Article-media site binding video production $field must be a non-empty exact string: $resourceid")
    )

  private def _youtube_url(value: String, resourceid: String): URI = {
    val uri = try new URI(value) catch {
      case NonFatal(_) => _invalid(s"Article-media site binding video URL is invalid: $resourceid")
    }
    if (value != value.trim || uri.toString != value || uri.getScheme != "https" ||
      uri.getRawUserInfo != null || uri.getPort != -1 || uri.getRawFragment != null)
      _invalid(s"Article-media site binding video URL is not clean HTTPS YouTube evidence: $resourceid")
    val videoid = uri.getHost match {
      case "youtu.be" if uri.getRawQuery == null && uri.getRawPath != null && uri.getRawPath.startsWith("/") =>
        val value = uri.getRawPath.drop(1)
        if (value.contains("/")) "" else value
      case "www.youtube.com" if uri.getRawPath == "/watch" && uri.getRawQuery != null && uri.getRawQuery.startsWith("v=") =>
        val value = uri.getRawQuery.drop(2)
        if (uri.getRawQuery == s"v=$value") value else ""
      case _ => ""
    }
    if (!videoid.matches("[A-Za-z0-9_-]+"))
      _invalid(s"Article-media site binding video URL is not an accepted YouTube form: $resourceid")
    uri
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
    val raw = CozyArticleMediaNormalization.requireExactTrimmed(value, "Article-media site binding infographic publicPath")
    val uri = try new URI(raw) catch {
      case NonFatal(_) => _invalid(s"Article-media site binding infographic publicPath is invalid: $raw")
    }
    if (uri.toString != raw)
      _invalid(s"Article-media site binding infographic publicPath is not exact: $raw")
    CozyArticleMediaNormalization.validateSiteVisibleUri(uri, "Article-media site binding infographic publicPath")
    uri
  }

  private def _relative_path(value: String, label: String): String =
    CozyArticleMediaNormalization.normalizeRelativePath(value, label)

  private def _root_file_snapshot(root: DirectoryIdentity, relative: String, label: String): FileSnapshot = {
    val path = root.lexical.resolve(relative).normalize()
    if (!path.startsWith(root.lexical) || path == root.lexical)
      _invalid(s"Article-media site binding $label escapes its root")
    val snapshot = _file_snapshot(path, label)
    if (!snapshot.evidence.identity.startsWith(root.identity))
      _invalid(s"Article-media site binding $label real identity escapes its root")
    snapshot
  }

  private def _file_snapshot(path: Path, label: String): FileSnapshot = {
    val file = _direct_regular_file_identity(path, label)
    val bytes = try Files.readAllBytes(file.lexical) catch {
      case NonFatal(e) => _invalid(s"Article-media site binding $label cannot be read: ${e.getMessage}")
    }
    FileSnapshot(
      FileEvidence(file.lexical, file.identity, _sha256(bytes), bytes.length.toLong),
      bytes.toVector
    )
  }

  private def _direct_regular_file_identity(path: Path, label: String): FileIdentity = {
    val lexical = _normalized_host_path(path, label)
    if (Files.isSymbolicLink(lexical) || !Files.isRegularFile(lexical, LinkOption.NOFOLLOW_LINKS))
      _invalid(s"Article-media site binding $label must be an existing direct regular non-symlink file: $lexical")
    val identity = try lexical.toRealPath() catch {
      case NonFatal(e) => _invalid(s"Article-media site binding $label cannot be resolved: ${e.getMessage}")
    }
    if (identity != lexical)
      _invalid(s"Article-media site binding $label must not use a lexical or symlink alias: $lexical")
    FileIdentity(lexical, identity)
  }

  private def _direct_directory(path: Path, label: String): DirectoryIdentity = {
    val lexical = _normalized_host_path(path, label)
    if (Files.isSymbolicLink(lexical) || !Files.isDirectory(lexical, LinkOption.NOFOLLOW_LINKS))
      _invalid(s"Article-media site binding $label must be an existing direct non-symlink directory: $lexical")
    val identity = try lexical.toRealPath() catch {
      case NonFatal(e) => _invalid(s"Article-media site binding $label cannot be resolved: ${e.getMessage}")
    }
    if (identity != lexical)
      _invalid(s"Article-media site binding $label must not use a lexical or symlink alias: $lexical")
    DirectoryIdentity(lexical, identity)
  }

  private def _direct_directory_identity(path: Path, label: String): Path =
    _direct_directory(path, label).identity

  private def _normalized_host_path(path: Path, label: String): Path = {
    if (path == null)
      _invalid(s"Article-media site binding $label path must be defined")
    path.toAbsolutePath.normalize()
  }

  private def _sha256(bytes: Array[Byte]): String = {
    val digest = MessageDigest.getInstance("SHA-256")
    digest.update(bytes)
    digest.digest().map(x => f"${x & 0xff}%02x").mkString
  }

  private def _invalid(message: String): Nothing =
    RAISE.invalidArgumentFault(message)
}
