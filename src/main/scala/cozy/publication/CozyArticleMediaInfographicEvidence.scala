package cozy.publication

import java.net.URI
import java.nio.file.{Files, Path}
import java.security.MessageDigest
import cozy.media.CozyMedia
import org.goldenport.RAISE
import org.goldenport.config.StructuredDocumentLoader
import org.goldenport.io.InputSource
import play.api.libs.json.{JsArray, JsObject, JsString, Json}
import scala.util.Try
import scala.util.control.NonFatal

/*
 * @since   Aug.  4, 2026
 * @version Aug.  4, 2026
 * @author  ASAMI, Tomoharu
 */
private[cozy] object CozyArticleMediaInfographicEvidence {
  sealed abstract class PublicationOutcome

  object PublicationOutcome {
    case object Registered extends PublicationOutcome
    case object Published extends PublicationOutcome
    case object Withdrawn extends PublicationOutcome
  }

  final case class PublicationResult(
    profile: String,
    root: Path,
    destination: Path,
    outcome: PublicationOutcome
  )

  final case class Input(
    projectRoot: Path,
    descriptorFile: Path,
    buildManifest: Path,
    articleIdentity: String,
    locale: String,
    version: String,
    publication: PublicationResult,
    repositoryRoot: Path
  )

  final case class Result(
    integrity: CozyArticleMediaIntegrity.Result,
    artifactPath: Path
  )

  def project(input: Input): Result = {
    val normalized = _normalize_input(input)
    val descriptor = _load_descriptor(normalized.descriptorfile)
    val selected = _select_resource(descriptor, normalized)
    val publicationpath = _publication_path(selected.resource, normalized.profile)
    val profileroot = _profile_root(descriptor, normalized.descriptorroot, normalized.profile)
    if (profileroot.real != normalized.repositoryroot.real || profileroot.real != normalized.publicationroot.real)
      _invalid("Article-media infographic configured, profile, and publication roots must be identical")
    val expecteddestination = profileroot.lexical.resolve(publicationpath).normalize()
    if (!expecteddestination.startsWith(profileroot.lexical))
      _invalid(s"Article-media infographic publication path escapes configured root: $publicationpath")
    val artifactpath = _validate_destination(normalized.publicationdestination, expecteddestination, profileroot.real, publicationpath)
    _validate_png(artifactpath, publicationpath)
    val buildpath = _build_path(selected.resource, normalized.descriptorroot)
    val buildoutput = _resolve_build_output(normalized.descriptorroot, buildpath)
    val manifestsha = _manifest_sha(normalized.buildmanifest, normalized.articleidentity, selected.resourceid, buildpath)
    _validate_png(buildoutput, buildpath)
    val buildsha = _sha256(buildoutput)
    val destinationsha = _sha256(artifactpath)
    if (manifestsha != buildsha || buildsha != destinationsha)
      _invalid(s"Article-media infographic manifest, build output, and published destination SHA-256 values must match: $publicationpath")
    Result(
      integrity = CozyArticleMediaIntegrity.produce(CozyArticleMediaIntegrity.Input(
        articleIdentity = normalized.articleidentity,
        locale = normalized.locale,
        role = CozyArticleMediaIntegrity.Role.Infographic,
        artifact = CozyArticleMediaIntegrity.Artifact(selected.resourceid, normalized.version),
        publicPath = new URI(s"/$publicationpath"),
        repositoryPath = publicationpath,
        mediaType = "image/png",
        sha256 = manifestsha,
        provenance = CozyArticleMediaIntegrity.MediaPackage(
          normalized.descriptorprovenance,
          selected.resourceid,
          normalized.manifestprovenance
        ),
        publicationState = _publication_state(normalized.outcome)
      )),
      artifactPath = artifactpath
    )
  }

  private final case class NormalizedInput(
    projectroot: Path,
    descriptorfile: Path,
    descriptorroot: Path,
    buildmanifest: Path,
    articleidentity: String,
    locale: String,
    version: String,
    profile: String,
    publicationroot: RootIdentity,
    publicationdestination: Path,
    outcome: PublicationOutcome,
    repositoryroot: RootIdentity,
    descriptorprovenance: String,
    manifestprovenance: String
  )

  private final case class RootIdentity(lexical: Path, real: Path)

  private final case class SelectedResource(resource: CozyMedia.Resource, resourceid: String)

  private def _normalize_input(input: Input): NormalizedInput = {
    if (input == null)
      _invalid("Article-media infographic evidence input must be defined")
    if (input.projectRoot == null || input.descriptorFile == null || input.buildManifest == null || input.repositoryRoot == null)
      _invalid("Article-media infographic evidence input paths must be defined")
    if (input.publication == null)
      _invalid("Article-media infographic publication result must be defined")
    if (input.publication.root == null || input.publication.destination == null)
      _invalid("Article-media infographic publication result paths must be defined")
    val projectlexical = input.projectRoot.toAbsolutePath.normalize()
    val projectroot = _directory_identity(input.projectRoot, "project root").real
    val repositoryroot = _directory_identity(input.repositoryRoot, "repository root")
    val descriptorlexical = input.descriptorFile.toAbsolutePath.normalize()
    if (!descriptorlexical.startsWith(projectlexical) || !Files.isRegularFile(descriptorlexical))
      _invalid(s"Article-media infographic descriptor must be an existing regular file inside project root: ${input.descriptorFile}")
    val descriptorfile = descriptorlexical.toRealPath()
    if (!descriptorfile.startsWith(projectroot))
      _invalid(s"Article-media infographic descriptor real path escapes project root: ${input.descriptorFile}")
    val descriptorroot = Option(descriptorlexical.getParent).getOrElse(
      _invalid("Article-media infographic descriptor must have a parent directory")
    )
    val expectedmanifest = descriptorroot.resolve("target/cozy-media/manifest.json").toAbsolutePath.normalize()
    val manifestlexical = input.buildManifest.toAbsolutePath.normalize()
    if (manifestlexical != expectedmanifest)
      _invalid(s"Article-media infographic build manifest must be exactly $expectedmanifest")
    if (!manifestlexical.startsWith(projectlexical) || !Files.isRegularFile(manifestlexical))
      _invalid(s"Article-media infographic build manifest must be an existing regular file inside project root: ${input.buildManifest}")
    val buildmanifest = manifestlexical.toRealPath()
    if (!buildmanifest.startsWith(projectroot))
      _invalid(s"Article-media infographic build manifest real path escapes project root: ${input.buildManifest}")
    val articleidentity = CozyArticleMediaNormalization.normalizeArticleIdentity(input.articleIdentity)
    val locale = CozyArticleMediaNormalization.normalizeLocale(input.locale)
    val version = CozyArticleMediaNormalization.requireExactTrimmed(input.version, "Article-media infographic version")
    val profile = CozyArticleMediaNormalization.requireExactTrimmed(input.publication.profile, "Article-media infographic publication profile")
    val outcome = _outcome(input.publication.outcome)
    val publicationroot = _directory_identity(input.publication.root, "publication result root")
    val publicationdestination = input.publication.destination.toAbsolutePath.normalize()
    NormalizedInput(
      projectroot,
      descriptorfile,
      descriptorroot,
      buildmanifest,
      articleidentity,
      locale,
      version,
      profile,
      publicationroot,
      publicationdestination,
      outcome,
      repositoryroot,
      _project_relative(projectlexical, descriptorlexical, "descriptor"),
      _project_relative(projectlexical, manifestlexical, "build manifest")
    )
  }

  private def _load_descriptor(path: Path): CozyMedia.Descriptor = {
    val descriptor = Try(StructuredDocumentLoader.loadDocument[CozyMedia.Descriptor](InputSource(path.toFile)).take).getOrElse(
      _invalid("Article-media infographic descriptor is malformed or invalid")
    )
    if (descriptor == null || descriptor.knowledge == null || descriptor.resources == null || descriptor.profiles == null)
      _invalid("Article-media infographic descriptor knowledge, resources, and profiles must be defined")
    if (descriptor.schema != "cozy.media.v1")
      _invalid(s"Article-media infographic descriptor schema must be cozy.media.v1: ${descriptor.schema}")
    if (descriptor.resources.isEmpty)
      _invalid("Article-media infographic descriptor resources must be defined")
    descriptor
  }

  private def _select_resource(descriptor: CozyMedia.Descriptor, input: NormalizedInput): SelectedResource = {
    val knowledgeid = CozyArticleMediaNormalization.normalizeArticleIdentity(descriptor.knowledge.id)
    if (knowledgeid != input.articleidentity)
      _invalid("Article-media infographic descriptor knowledge.id must equal article identity")
    if (descriptor.resources.exists(_ == null))
      _invalid("Article-media infographic descriptor resources must not contain null")
    val resourceids = descriptor.resources.map(resource =>
      CozyArticleMediaNormalization.requireExactTrimmed(resource.id, "Article-media infographic resource id")
    )
    if (resourceids.distinct.size != resourceids.size)
      _invalid("Article-media infographic descriptor resource ids must be unique")
    val candidates = descriptor.resources.filter(x => x.kind == "image" && x.role.contains("detailed-infographic"))
    candidates.foreach { resource =>
      val language = resource.language.getOrElse(_invalid("Article-media infographic candidate resource language must be defined"))
      CozyArticleMediaNormalization.normalizeLocale(language)
    }
    val selected = candidates.filter(x => CozyArticleMediaNormalization.normalizeLocale(x.language.get) == input.locale)
    if (selected.isEmpty)
      _invalid(s"Article-media infographic descriptor has no exact ${input.locale} detailed-infographic image resource")
    if (selected.size > 1)
      _invalid(s"Article-media infographic descriptor has ambiguous ${input.locale} detailed-infographic image resources")
    val resource = selected.head
    SelectedResource(resource, CozyArticleMediaNormalization.requireExactTrimmed(resource.id, "Article-media infographic resource id"))
  }

  private def _publication_path(resource: CozyMedia.Resource, profile: String): String = {
    if (resource.publications == null)
      _invalid("Article-media infographic resource publications must be defined")
    val value = resource.publications.getOrElse(profile, _invalid(s"Article-media infographic resource has no publication for profile: $profile"))
    val path = CozyArticleMediaNormalization.normalizeRelativePath(value, "Article-media infographic publication destination")
    if (path.contains('?') || path.contains('#') || !path.endsWith(".png"))
      _invalid(s"Article-media infographic publication destination must be an unambiguous lowercase .png path: $value")
    path
  }

  private def _profile_root(descriptor: CozyMedia.Descriptor, descriptorroot: Path, profile: String): RootIdentity = {
    val selected = descriptor.profiles.getOrElse(profile, _invalid(s"Article-media infographic descriptor has no selected profile: $profile"))
    if (selected == null)
      _invalid(s"Article-media infographic descriptor profile must be defined: $profile")
    val root = selected.rootEnv match {
      case Some(name) =>
        val envname = CozyArticleMediaNormalization.requireExactTrimmed(name, s"Article-media infographic profile $profile rootEnv")
        val value = sys.env.get(envname).filter(_.trim.nonEmpty).getOrElse(
          _invalid(s"Article-media infographic profile $profile rootEnv is missing: $envname")
        )
        _directory_identity(_path(value, s"profile $profile rootEnv"), s"profile $profile rootEnv")
      case None =>
        selected.root match {
          case Some(value) =>
            val relative = _profile_relative_path(value, profile)
            val candidate = descriptorroot.resolve(relative).normalize()
            _directory_identity(candidate, s"profile $profile root")
          case None => _directory_identity(descriptorroot, s"profile $profile default root")
        }
    }
    root
  }

  private def _build_path(resource: CozyMedia.Resource, descriptorroot: Path): String = {
    val value =
      if (resource.build == "prebuilt") resource.source.getOrElse(
        _invalid("Article-media infographic prebuilt resource source must be defined")
      )
      else resource.output.getOrElse(
        _invalid("Article-media infographic resource output must be defined")
      )
    val relative = CozyArticleMediaNormalization.normalizeRelativePath(value, "Article-media infographic selected build path")
    val candidate = descriptorroot.resolve(relative).normalize()
    if (!candidate.startsWith(descriptorroot))
      _invalid(s"Article-media infographic selected build path escapes descriptor root: $value")
    relative
  }

  private def _resolve_build_output(descriptorroot: Path, buildpath: String): Path = {
    val lexicalroot = descriptorroot.toAbsolutePath.normalize()
    val realroot = lexicalroot.toRealPath()
    val candidate = lexicalroot.resolve(buildpath).normalize()
    if (!candidate.startsWith(lexicalroot))
      _invalid(s"Article-media infographic selected build path escapes descriptor root: $buildpath")
    if (!Files.isRegularFile(candidate))
      _invalid(s"Article-media infographic selected build output must be an existing regular file: $buildpath")
    val real = candidate.toRealPath()
    if (!real.startsWith(realroot))
      _invalid(s"Article-media infographic selected build output real path escapes descriptor root: $buildpath")
    real
  }

  private def _validate_destination(destination: Path, expected: Path, root: Path, relative: String): Path = {
    if (destination != expected)
      _invalid(s"Article-media infographic publication result destination must equal declared destination: $relative")
    if (!Files.isRegularFile(destination))
      _invalid(s"Article-media infographic published destination must be an existing regular file: $relative")
    val real = destination.toRealPath()
    if (!real.startsWith(root))
      _invalid(s"Article-media infographic published destination real path escapes configured root: $relative")
    real
  }

  private def _validate_png(path: Path, relative: String): Unit = {
    val bytes = Files.readAllBytes(path)
    if (bytes.length < 24 ||
      bytes(0) != 0x89.toByte || bytes(1) != 0x50.toByte || bytes(2) != 0x4e.toByte || bytes(3) != 0x47.toByte ||
      bytes(4) != 0x0d.toByte || bytes(5) != 0x0a.toByte || bytes(6) != 0x1a.toByte || bytes(7) != 0x0a.toByte ||
      bytes(8) != 0 || bytes(9) != 0 || bytes(10) != 0 || bytes(11) != 13 ||
      bytes(12) != 0x49.toByte || bytes(13) != 0x48.toByte || bytes(14) != 0x44.toByte || bytes(15) != 0x52.toByte ||
      _png_dimension(bytes, 16) <= 0 || _png_dimension(bytes, 20) <= 0)
      _invalid(s"Article-media infographic published destination must be a structurally valid PNG: $relative")
  }

  private def _manifest_sha(path: Path, articleidentity: String, resourceid: String, buildpath: String): String = {
    val json = Try(Json.parse(Files.readString(path))).getOrElse(
      _invalid("Article-media infographic build manifest must contain valid JSON")
    )
    val manifest = json.asOpt[JsObject].getOrElse(
      _invalid("Article-media infographic build manifest must be a JSON object")
    )
    _require_exact(manifest, "schema", "cozy.media.v1", "build manifest")
    val knowledge = _required_exact_string(manifest, "knowledge", "build manifest")
    if (CozyArticleMediaNormalization.normalizeArticleIdentity(knowledge) != articleidentity)
      _invalid("Article-media infographic build manifest knowledge must equal article identity")
    val resources = manifest.value.get("resources") match {
      case Some(JsArray(values)) => values.toVector
      case _ => _invalid("Article-media infographic build manifest resources must be an array")
    }
    val entries = resources.map(_.asOpt[JsObject].getOrElse(
      _invalid("Article-media infographic build manifest resources entries must be JSON objects")
    ))
    val selected = entries.filter(x => _string(x, "id").contains(resourceid))
    if (selected.isEmpty)
      _invalid(s"Article-media infographic build manifest has no selected resource: $resourceid")
    if (selected.size > 1)
      _invalid(s"Article-media infographic build manifest has ambiguous selected resource: $resourceid")
    val entry = selected.head
    val manifestpath = CozyArticleMediaNormalization.normalizeRelativePath(
      _required_exact_string(entry, "path", "build manifest selected resource"),
      "Article-media infographic build manifest selected resource path"
    )
    if (manifestpath != buildpath)
      _invalid(s"Article-media infographic build manifest path must equal selected build path: $buildpath")
    _sha256_string(_required_exact_string(entry, "sha256", "build manifest selected resource"))
  }

  private def _directory_identity(path: Path, label: String): RootIdentity = {
    val lexical = path.toAbsolutePath.normalize()
    if (!Files.isDirectory(lexical))
      _invalid(s"Article-media infographic $label must be an existing directory: $path")
    RootIdentity(lexical, lexical.toRealPath())
  }

  private def _profile_relative_path(value: String, profile: String): Path = {
    val raw = CozyArticleMediaNormalization.requireExactTrimmed(value, s"Article-media infographic profile $profile root")
    if (raw.contains('\\') || raw.contains('\u0000') || raw.startsWith("/") || raw.matches("[A-Za-z]:[\\\\/].*"))
      _invalid(s"Article-media infographic profile $profile root must be a relative path: $value")
    val path = _path(raw, s"profile $profile root")
    if (path.isAbsolute)
      _invalid(s"Article-media infographic profile $profile root must be a relative path: $value")
    path
  }

  private def _path(value: String, label: String): Path =
    try Path.of(value)
    catch {
      case NonFatal(_) => _invalid(s"Article-media infographic $label is not a valid path")
    }

  private def _png_dimension(bytes: Array[Byte], offset: Int): Long =
    ((bytes(offset).toLong & 0xffL) << 24) |
      ((bytes(offset + 1).toLong & 0xffL) << 16) |
      ((bytes(offset + 2).toLong & 0xffL) << 8) |
      (bytes(offset + 3).toLong & 0xffL)

  private def _project_relative(root: Path, path: Path, label: String): String = {
    if (!path.startsWith(root))
      _invalid(s"Article-media infographic $label must be inside project root")
    CozyArticleMediaNormalization.normalizeRelativePath(root.relativize(path).toString.replace('\\', '/'), s"Article-media infographic $label provenance")
  }

  private def _outcome(value: PublicationOutcome): PublicationOutcome =
    value match {
      case PublicationOutcome.Registered | PublicationOutcome.Published | PublicationOutcome.Withdrawn => value
      case _ => _invalid("Article-media infographic publication outcome is invalid")
    }

  private def _publication_state(value: PublicationOutcome): CozyArticleMediaIntegrity.PublicationState =
    value match {
      case PublicationOutcome.Registered => CozyArticleMediaIntegrity.PublicationState.Registered
      case PublicationOutcome.Published => CozyArticleMediaIntegrity.PublicationState.Published
      case PublicationOutcome.Withdrawn => CozyArticleMediaIntegrity.PublicationState.Withdrawn
      case _ => _invalid("Article-media infographic publication outcome is invalid")
    }

  private def _required_exact_string(value: JsObject, field: String, label: String): String =
    _string(value, field).filter(x => x.nonEmpty && x == x.trim).getOrElse(
      _invalid(s"Article-media infographic $label.$field must be a non-empty exact string")
    )

  private def _require_exact(value: JsObject, field: String, expected: String, label: String): Unit =
    if (_string(value, field) != Some(expected))
      _invalid(s"Article-media infographic $label.$field must be exactly $expected")

  private def _string(value: JsObject, field: String): Option[String] =
    value.value.get(field).collect { case JsString(x) => x }

  private def _sha256_string(value: String): String = {
    if (!value.matches("[0-9a-f]{64}"))
      _invalid(s"Article-media infographic build manifest SHA-256 must be 64 lowercase hexadecimal characters: $value")
    value
  }

  private def _sha256(path: Path): String = {
    val digest = MessageDigest.getInstance("SHA-256")
    val stream = Files.newInputStream(path)
    val buffer = new Array[Byte](8192)
    try {
      var size = stream.read(buffer)
      while (size >= 0) {
        if (size > 0)
          digest.update(buffer, 0, size)
        size = stream.read(buffer)
      }
    } finally stream.close()
    digest.digest().map(x => f"${x & 0xff}%02x").mkString
  }

  private def _invalid(message: String): Nothing =
    RAISE.invalidArgumentFault(message)
}
