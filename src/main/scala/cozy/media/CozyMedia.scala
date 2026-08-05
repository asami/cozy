package cozy.media

import org.goldenport.RAISE
import org.goldenport.cli.spec
import org.goldenport.config.StructuredDocumentLoader
import org.goldenport.io.InputSource
import cozy.runtime.CozyCliArgs
import cozy.video.CozyVideo
import io.circe.{Decoder, HCursor, Json}
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.nio.channels.FileChannel
import java.nio.file.{AtomicMoveNotSupportedException, Files, LinkOption, Path, Paths, StandardCopyOption, StandardOpenOption}
import java.security.MessageDigest
import scala.collection.JavaConverters._
import scala.collection.mutable.ArrayBuffer
import scala.util.control.NonFatal

/*
 * @since   Jul. 19, 2026
 *  version Jul. 20, 2026
 * @version Aug.  5, 2026
 * @author  ASAMI, Tomoharu
 */
private[cozy] object CozyMedia {
  final case class Knowledge(
    id: String,
    knowledgeType: Option[String],
    source: String,
    sourceType: Option[String]
  )
  object Knowledge {
    implicit val decoder: Decoder[Knowledge] = (c: HCursor) =>
      for {
        id <- c.downField("id").as[String]
        knowledgetype <- c.downField("type").as[Option[String]]
        source <- c.downField("source").as[String]
        sourcetype <- c.downField("sourceType").as[Option[String]].flatMap {
          case value @ Some(_) => Right(value)
          case None => c.downField("source-type").as[Option[String]]
        }
      } yield Knowledge(id, knowledgetype, source, sourcetype)
  }

  final case class Profile(
    root: Option[String],
    rootEnv: Option[String]
  )
  object Profile {
    implicit val decoder: Decoder[Profile] = (c: HCursor) =>
      for {
        root <- c.downField("root").as[Option[String]]
        rootenv <- c.downField("rootEnv").as[Option[String]].flatMap {
          case value @ Some(_) => Right(value)
          case None => c.downField("root-env").as[Option[String]]
        }
      } yield Profile(root, rootenv)
  }

  final case class Resource(
    id: String,
    kind: String,
    language: Option[String],
    role: Option[String],
    source: Option[String],
    output: Option[String],
    build: String,
    width: Option[Int],
    height: Option[Int],
    project: Option[String],
    publications: Map[String, String]
  )
  object Resource {
    implicit val decoder: Decoder[Resource] = (c: HCursor) =>
      for {
        id <- c.downField("id").as[String]
        kind <- c.downField("kind").as[String]
        language <- c.downField("language").as[Option[String]]
        role <- c.downField("role").as[Option[String]]
        source <- c.downField("source").as[Option[String]]
        output <- c.downField("output").as[Option[String]]
        build <- c.downField("build").as[Option[String]]
        width <- c.downField("width").as[Option[Int]]
        height <- c.downField("height").as[Option[Int]]
        project <- c.downField("project").as[Option[String]]
        publications <- c.downField("publications").as[Option[Map[String, String]]]
      } yield Resource(id, kind, language, role, source, output, build.getOrElse("copy"), width, height, project, publications.getOrElse(Map.empty))
  }

  final case class Descriptor(
    schema: String,
    knowledge: Knowledge,
    languages: Vector[String],
    resources: Vector[Resource],
    profiles: Map[String, Profile]
  )
  object Descriptor {
    implicit val decoder: Decoder[Descriptor] = (c: HCursor) =>
      for {
        schema <- c.downField("schema").as[String]
        knowledge <- c.downField("knowledge").as[Knowledge]
        languages <- c.downField("languages").as[Option[Vector[String]]]
        resources <- c.downField("resources").as[Option[Vector[Resource]]]
        profiles <- c.downField("profiles").as[Option[Map[String, Profile]]]
      } yield Descriptor(schema, knowledge, languages.getOrElse(Vector.empty), resources.getOrElse(Vector.empty).sortBy(_.id), profiles.getOrElse(Map.empty))
  }

  sealed trait Action { def label: String }
  object Action {
    case object Build extends Action { val label = "build" }
    case object Current extends Action { val label = "current" }
    case object MissingSource extends Action { val label = "missing-source" }
    case object DelegateVideo extends Action { val label = "delegate-video" }
  }

  final case class ResolvedResource(
    resource: Resource,
    source: Option[Path],
    output: Option[Path],
    project: Option[Path],
    publications: Map[String, Path],
    action: Action
  )

  final case class Plan(
    descriptorFile: Path,
    descriptorRoot: Path,
    descriptor: Descriptor,
    knowledgeSource: Path,
    resources: Vector[ResolvedResource]
  )

  trait ProcessRunner {
    def run(command: Vector[String], workingdirectory: Path): Int
  }
  object ProcessRunner {
    val default: ProcessRunner = new ProcessRunner {
      def run(command: Vector[String], workingdirectory: Path): Int = {
        val process = new ProcessBuilder(command: _*).
          directory(workingdirectory.toFile).
          inheritIO().
          start()
        process.waitFor()
      }
    }
  }

  final case class CommandConfig(
    descriptorFile: Path,
    target: Option[String] = None,
    profile: Option[String] = None,
    dryRun: Boolean = false
  )
  object CommandConfig {
    def create(args: List[String], requireProfile: Boolean = false): CommandConfig = {
      val parsed = CozyCliArgs.parseStrict(_p_media_file, _p_target, _p_profile, _p_dry_run)(_normalize_property_args(args))
      val descriptorfile = parsed.argument("media-file").map(CozyCliArgs.toPath).getOrElse(
        RAISE.invalidArgumentFault("Missing media descriptor")
      )
      val profile = parsed.property("profile")
      if (requireProfile && profile.isEmpty)
        RAISE.invalidArgumentFault("Missing --profile for media publish")
      CommandConfig(descriptorfile, parsed.property("target"), profile, parsed.flag("dry-run"))
    }
  }

  sealed trait DestinationState
  object DestinationState {
    case object Absent extends DestinationState
    final case class Existing(sha256: String) extends DestinationState
  }

  sealed trait PublicationDisposition {
    def label: String
  }
  object PublicationDisposition {
    case object Create extends PublicationDisposition {
      val label = "create"
    }
    case object Reuse extends PublicationDisposition {
      val label = "reuse"
    }
    case object Replace extends PublicationDisposition {
      val label = "replace"
    }
  }

  sealed trait PublicationOutcome {
    def label: String
  }
  object PublicationOutcome {
    case object Created extends PublicationOutcome {
      val label = "created"
    }
    case object Reused extends PublicationOutcome {
      val label = "reused"
    }
    case object Replaced extends PublicationOutcome {
      val label = "replaced"
    }
  }

  final case class PreparedPublication(
    descriptorFile: Path,
    descriptorRoot: Path,
    descriptor: Descriptor,
    descriptorSha256: String,
    resource: Resource,
    target: Option[String],
    profile: String,
    profileRoot: Path,
    profileRootIdentity: Path,
    publishablePath: Path,
    destination: Path,
    destinationIdentity: Path,
    sourceSha256: String,
    destinationState: DestinationState,
    force: Boolean,
    disposition: PublicationDisposition
  )

  final case class PublicationResult(
    prepared: PreparedPublication,
    outcome: PublicationOutcome
  )

  private val _p_media_file = spec.Parameter.argumentFile("media-file")
  private val _p_target = spec.Parameter.property("target")
  private val _p_profile = spec.Parameter.property("profile")
  private val _p_dry_run = spec.Parameter("dry-run", spec.Parameter.SwitchKind)
  private val _schema = "cozy.media.v1"
  private val _build_kinds = Set("copy", "svg-to-png", "prebuilt", "video-project")
  private val _property_options = Set("target", "profile")

  def execute(args: List[String]): Boolean = execute(args, ProcessRunner.default)

  def execute(args: List[String], runner: ProcessRunner): Boolean =
    args match {
      case "media" :: "inspect" :: rest =>
        println(inspect(CommandConfig.create(rest)))
        true
      case "media" :: "plan" :: rest =>
        println(plan(CommandConfig.create(rest)))
        true
      case "media" :: "build" :: rest =>
        println(build(CommandConfig.create(rest), runner))
        true
      case "media" :: "verify" :: rest =>
        println(verify(CommandConfig.create(rest)))
        true
      case "media" :: "publish" :: rest =>
        println(publish(CommandConfig.create(rest, requireProfile = true)))
        true
      case "media" :: other :: _ =>
        RAISE.invalidArgumentFault(s"Unsupported media command: $other")
      case _ =>
        false
    }

  def inspect(config: CommandConfig): String = {
    val mediaplan = _plan(config)
    val lines = Vector(
      "Cozy Media Inspect",
      s"descriptor: ${mediaplan.descriptorFile}",
      s"schema: ${mediaplan.descriptor.schema}",
      s"knowledge: ${mediaplan.descriptor.knowledge.id}",
      s"knowledgeSource: ${mediaplan.knowledgeSource}",
      s"languages: ${mediaplan.descriptor.languages.mkString(", ")}",
      s"resources: ${mediaplan.resources.size}"
    ) ++ mediaplan.resources.map { resolved =>
      val resource = resolved.resource
      val output = resolved.output.map(_.toString).getOrElse("-")
      s"  - ${resource.id}: kind=${resource.kind}, language=${resource.language.getOrElse("-")}, role=${resource.role.getOrElse("-")}, build=${resource.build}, output=$output"
    }
    lines.mkString("\n")
  }

  def plan(config: CommandConfig): String = {
    val mediaplan = _plan(config)
    val selected = _selected(mediaplan, config.target)
    val lines = Vector(
      "Cozy Media Plan",
      s"descriptor: ${mediaplan.descriptorFile}",
      s"knowledge: ${mediaplan.descriptor.knowledge.id}",
      s"profile: ${config.profile.getOrElse("-")}"
    ) ++ selected.map { resolved =>
      val publications = config.profile.toVector.flatMap(resolved.publications.get).map(path => s", publish=$path").mkString
      s"  - ${resolved.resource.id}: ${resolved.action.label}${publications}"
    }
    lines.mkString("\n")
  }

  def build(config: CommandConfig, runner: ProcessRunner = ProcessRunner.default): String = {
    val mediaplan = _plan(config)
    val selected = _selected(mediaplan, config.target)
    val results = selected.map { resolved =>
      if (config.dryRun)
        s"${resolved.resource.id}: ${resolved.action.label} (dry-run)"
      else
        _build_resource(mediaplan, resolved, runner)
    }
    if (!config.dryRun)
      _write_manifest(mediaplan)
    (Vector("Cozy Media Build", s"descriptor: ${mediaplan.descriptorFile}") ++ results.map(x => s"  - $x")).mkString("\n")
  }

  def verify(config: CommandConfig): String = {
    val mediaplan = _plan(config)
    val selected = _selected(mediaplan, config.target)
    val findings = _verify_plan(mediaplan, selected, config.profile)
    if (findings.nonEmpty)
      RAISE.invalidArgumentFault("Media verification failed:\n" + findings.map(x => s"- $x").mkString("\n"))
    val profile = config.profile.map(x => s" profile=$x").getOrElse("")
    s"Cozy Media Verify\nstatus: valid$profile\nresources: ${selected.size}"
  }

  def publish(config: CommandConfig): String = {
    val mediaplan = _plan(config)
    val selected = _selected(mediaplan, config.target)
    val profile = config.profile.getOrElse(RAISE.invalidArgumentFault("Missing --profile for media publish"))
    val candidates = selected.filter(_.publications.contains(profile))
    if (candidates.isEmpty)
      RAISE.invalidArgumentFault(s"No media resources publish to profile: $profile")
    val findings = _verify_plan(mediaplan, candidates, None)
    if (findings.nonEmpty)
      RAISE.invalidArgumentFault("Media publication preflight failed:\n" + findings.map(x => s"- $x").mkString("\n"))
    val preflight = _prepare_legacy_publications(mediaplan, candidates, profile, config.target, force = true)
    val prepared =
      if (config.dryRun) preflight
      else {
        _create_and_bind_legacy_profile_root(mediaplan.descriptorRoot, mediaplan.descriptor, profile)
        _prepare_publications(mediaplan, candidates, profile, config.target, force = true)
      }
    if (!config.dryRun)
      commitPublication(prepared)
    val results =
      if (config.dryRun)
        candidates.map { resolved =>
          val publishable = resolved.output.orElse(resolved.source).getOrElse(
            RAISE.invalidArgumentFault(s"Media resource has no publishable file: ${resolved.resource.id}")
          )
          val destination = resolved.publications.getOrElse(profile,
            RAISE.invalidArgumentFault(s"Media resource has no publication for profile $profile: ${resolved.resource.id}")
          )
          s"${resolved.resource.id}: $publishable -> $destination (dry-run)"
        }
      else prepared.map { publication =>
        s"${publication.resource.id}: ${publication.publishablePath} -> ${publication.destination}"
      }
    (Vector("Cozy Media Publish", s"profile: $profile") ++ results.map(x => s"  - $x")).mkString("\n")
  }

  def preparePublication(config: CommandConfig, force: Boolean = false): Vector[PreparedPublication] = {
    val mediaplan = _plan(config)
    val selected = _selected(mediaplan, config.target)
    val profile = config.profile.getOrElse(RAISE.invalidArgumentFault("Missing --profile for media publish"))
    val candidates = selected.filter(_.publications.contains(profile))
    if (candidates.isEmpty)
      RAISE.invalidArgumentFault(s"No media resources publish to profile: $profile")
    _prepare_publications(mediaplan, candidates, profile, config.target, force)
  }

  def commitPublication(prepared: Vector[PreparedPublication]): Vector[PublicationResult] =
    _commit_publication(prepared, () => ())

  private[cozy] def commitPublication(
    prepared: Vector[PreparedPublication],
    beforeFirstInstall: () => Unit
  ): Vector[PublicationResult] = {
    if (beforeFirstInstall == null)
      RAISE.invalidArgumentFault("Media publication beforeFirstInstall callback must not be null")
    _commit_publication(prepared, beforeFirstInstall)
  }

  private def _commit_publication(
    prepared: Vector[PreparedPublication],
    beforefirstinstall: () => Unit
  ): Vector[PublicationResult] = {
    val publications = Option(prepared).getOrElse(
      RAISE.invalidArgumentFault("Prepared media publications must not be null")
    )
    _validate_prepared_publications(publications)
    val ordered = _ordered_publications(publications)
    val temporaries = ArrayBuffer.empty[Path]
    try {
      beforefirstinstall()
      ordered.map { publication =>
        val outcome = publication.disposition match {
          case PublicationDisposition.Reuse =>
            PublicationOutcome.Reused
          case PublicationDisposition.Create =>
            _install_publication(publication, replace = false, temporaries)
            PublicationOutcome.Created
          case PublicationDisposition.Replace =>
            _install_publication(publication, replace = true, temporaries)
            PublicationOutcome.Replaced
        }
        PublicationResult(publication, outcome)
      }
    } finally {
      temporaries.foreach { path =>
        try Files.deleteIfExists(path)
        catch {
          case NonFatal(_) => ()
        }
      }
    }
  }

  private def _plan(config: CommandConfig): Plan = {
    val descriptorfile = config.descriptorFile.toAbsolutePath.normalize()
    if (!_is_direct_regular_file(descriptorfile))
      RAISE.invalidArgumentFault(s"Missing media descriptor: $descriptorfile")
    val descriptor = StructuredDocumentLoader.loadDocument[Descriptor](InputSource(descriptorfile.toFile)).take
    _validate_descriptor(descriptor)
    val descriptorroot = Option(descriptorfile.getParent).getOrElse(Paths.get(".").toAbsolutePath.normalize())
    val knowledgesource = _resolve_relative(descriptorroot, descriptor.knowledge.source, "knowledge.source")
    val resources = descriptor.resources.map { resource =>
      val source = resource.source.map(_resolve_relative(descriptorroot, _, s"resources.${resource.id}.source"))
      val output = resource.output.map(_resolve_relative(descriptorroot, _, s"resources.${resource.id}.output"))
      val project = resource.project.map(_resolve_relative(descriptorroot, _, s"resources.${resource.id}.project"))
      val publications = resource.publications.toVector.flatMap { case (profile, path) =>
        config.profile.filter(_ == profile).map { _ =>
          profile -> _publication_path(descriptorroot, descriptor, profile, path)
        }
      }.toMap
      val action = _action(resource, source, output, project)
      ResolvedResource(resource, source, output, project, publications, action)
    }
    Plan(descriptorfile, descriptorroot, descriptor, knowledgesource, resources)
  }

  private def _validate_descriptor(descriptor: Descriptor): Unit = {
    if (descriptor.schema != _schema)
      RAISE.invalidArgumentFault(s"Unsupported media schema: ${descriptor.schema}. Expected: ${_schema}")
    if (descriptor.knowledge.id.trim.isEmpty)
      RAISE.invalidArgumentFault("Media knowledge.id must not be empty")
    val ids = descriptor.resources.map(_.id)
    if (ids.distinct.size != ids.size)
      RAISE.invalidArgumentFault("Media resource ids must be unique")
    descriptor.resources.foreach { resource =>
      if (!_build_kinds.contains(resource.build))
        RAISE.invalidArgumentFault(s"Unsupported media build kind for ${resource.id}: ${resource.build}")
      if (resource.build == "video-project" && resource.project.isEmpty)
        RAISE.invalidArgumentFault(s"Media video-project resource requires project: ${resource.id}")
      if (resource.build == "video-project" && resource.output.isEmpty)
        RAISE.invalidArgumentFault(s"Media video-project resource requires output: ${resource.id}")
      if (resource.build == "prebuilt" && resource.source.isEmpty)
        RAISE.invalidArgumentFault(s"Media prebuilt resource requires source: ${resource.id}")
      if (!Set("video-project", "prebuilt").contains(resource.build) && (resource.source.isEmpty || resource.output.isEmpty))
        RAISE.invalidArgumentFault(s"Media resource requires source and output: ${resource.id}")
      resource.publications.keys.foreach { profile =>
        if (!descriptor.profiles.contains(profile))
          RAISE.invalidArgumentFault(s"Media resource ${resource.id} uses undefined profile: $profile")
      }
    }
  }

  private def _resolve_relative(root: Path, value: String, label: String): Path = {
    val path = Path.of(value)
    if (path.isAbsolute)
      RAISE.invalidArgumentFault(s"$label must be relative: $value")
    root.resolve(path).normalize()
  }

  private def _publication_path(root: Path, descriptor: Descriptor, profilename: String, value: String): Path = {
    val profileroot = _profile_root(root, descriptor, profilename)
    val path = Path.of(value)
    if (path.isAbsolute)
      RAISE.invalidArgumentFault(s"Media publication path must be relative: $value")
    val destination = profileroot.resolve(path).normalize()
    if (!destination.startsWith(profileroot))
      RAISE.invalidArgumentFault(s"Media publication path escapes profile root: $value")
    destination
  }

  private def _profile_root(root: Path, descriptor: Descriptor, profilename: String): Path = {
    val profile = descriptor.profiles.getOrElse(profilename, RAISE.invalidArgumentFault(s"Undefined media profile: $profilename"))
    profile.rootEnv.map { name =>
      sys.env.get(name).map(Path.of(_).toAbsolutePath.normalize()).getOrElse(
        RAISE.invalidArgumentFault(s"Media profile $profilename requires environment variable: $name")
      )
    }.orElse {
      profile.root.map(_resolve_relative(root, _, s"profiles.$profilename.root"))
    }.getOrElse(root)
  }

  private def _prepare_publications(
    plan: Plan,
    candidates: Vector[ResolvedResource],
    profile: String,
    target: Option[String],
    force: Boolean
  ): Vector[PreparedPublication] = {
    if (!_is_direct_regular_file(plan.descriptorFile))
      RAISE.invalidArgumentFault(s"Media descriptor must be a direct regular non-symlink file: ${plan.descriptorFile}")
    val descriptorhash = _sha256(plan.descriptorFile)
    val profileroot = _bind_profile_root(_profile_root(plan.descriptorRoot, plan.descriptor, profile))
    val publications = candidates.map { resolved =>
      val publishable = resolved.output.orElse(resolved.source).getOrElse(
        RAISE.invalidArgumentFault(s"Media resource has no publishable file: ${resolved.resource.id}")
      )
      if (!_is_direct_regular_file(publishable))
        RAISE.invalidArgumentFault(s"Media publishable source must be a direct regular non-symlink file: $publishable")
      val destination = resolved.publications.getOrElse(profile,
        RAISE.invalidArgumentFault(s"Media resource has no publication for profile $profile: ${resolved.resource.id}")
      )
      val destinationidentity = _destination_identity(destination, profileroot)
      val sourcehash = _sha256(publishable)
      val destinationstate = _destination_state(destination)
      val disposition = _publication_disposition(sourcehash, destinationstate, force, destination)
      PreparedPublication(
        plan.descriptorFile,
        plan.descriptorRoot,
        plan.descriptor,
        descriptorhash,
        resolved.resource,
        target,
        profile,
        profileroot,
        profileroot,
        publishable,
        destination,
        destinationidentity,
        sourcehash,
        destinationstate,
        force,
        disposition
      )
    }
    _validate_unique_destinations(publications)
    _ordered_publications(publications)
  }

  private def _prepare_legacy_publications(
    plan: Plan,
    candidates: Vector[ResolvedResource],
    profile: String,
    target: Option[String],
    force: Boolean
  ): Vector[PreparedPublication] = {
    val profileroot = _profile_root(plan.descriptorRoot, plan.descriptor, profile)
    if (Files.exists(profileroot, LinkOption.NOFOLLOW_LINKS))
      _prepare_publications(plan, candidates, profile, target, force)
    else {
      val destinations = candidates.map { resolved =>
        val publishable = resolved.output.orElse(resolved.source).getOrElse(
          RAISE.invalidArgumentFault(s"Media resource has no publishable file: ${resolved.resource.id}")
        )
        if (!_is_direct_regular_file(publishable))
          RAISE.invalidArgumentFault(s"Media publishable source must be a direct regular non-symlink file: $publishable")
        val destination = resolved.publications.getOrElse(profile,
          RAISE.invalidArgumentFault(s"Media resource has no publication for profile $profile: ${resolved.resource.id}")
        )
        _validate_lexical_destination(destination, profileroot)
        destination
      }
      destinations.groupBy(identity).collectFirst { case (destination, values) if values.size > 1 => destination }.foreach { destination =>
        RAISE.invalidArgumentFault(s"Media publication destinations must be unique: $destination")
      }
      Vector.empty
    }
  }

  private def _ordered_publications(publications: Vector[PreparedPublication]): Vector[PreparedPublication] =
    publications.sortBy(publication => (
      publication.descriptorFile.toString,
      publication.resource.id,
      publication.profile,
      publication.destination.toString
    ))

  private def _validate_prepared_publications(publications: Vector[PreparedPublication]): Unit = {
    if (publications.isEmpty)
      RAISE.invalidArgumentFault("Prepared media publications must not be empty")
    publications.foreach(_validate_prepared_publication)
    _validate_unique_destinations(publications)
    publications.groupBy(publication => (publication.descriptorFile, publication.profile, publication.target, publication.force)).foreach {
      case ((descriptorfile, profile, target, force), values) =>
        val plan = _plan(CommandConfig(descriptorfile, target = target, profile = Some(profile)))
        val candidates = _selected(plan, target).filter(_.publications.contains(profile))
        if (candidates.isEmpty)
          RAISE.invalidArgumentFault(s"Prepared media publication candidate set has changed: $descriptorfile")
        val reconstructed = _prepare_publications(plan, candidates, profile, target, force)
        if (_ordered_publications(values) != reconstructed)
          RAISE.invalidArgumentFault(s"Prepared media publication candidate vector has changed: $descriptorfile")
    }
  }

  private def _validate_prepared_publication(publication: PreparedPublication): Unit = {
    if (publication == null)
      RAISE.invalidArgumentFault("Prepared media publication must not be null")
    if (publication.descriptorFile == null || publication.descriptorRoot == null || publication.descriptor == null || publication.resource == null)
      RAISE.invalidArgumentFault("Prepared media publication descriptor evidence must not be null")
    if (publication.profile == null || publication.profile.trim.isEmpty || publication.profileRoot == null || publication.profileRootIdentity == null || publication.publishablePath == null || publication.destination == null || publication.destinationIdentity == null)
      RAISE.invalidArgumentFault("Prepared media publication path evidence must not be null or empty")
    if (publication.destinationState == null || publication.disposition == null)
      RAISE.invalidArgumentFault("Prepared media publication state must not be null")
    if (publication.resource.id == null || publication.resource.id.trim.isEmpty)
      RAISE.invalidArgumentFault("Prepared media publication resource id must not be empty")
    if (publication.descriptor.schema == null || publication.descriptor.knowledge == null || publication.descriptor.resources == null || publication.descriptor.profiles == null)
      RAISE.invalidArgumentFault("Prepared media publication descriptor structure must not be null")
    if (publication.resource.publications == null || publication.resource.language == null || publication.resource.role == null || publication.resource.source == null || publication.resource.output == null)
      RAISE.invalidArgumentFault("Prepared media publication resource structure must not be null")
    if (publication.descriptor.schema != _schema || !publication.descriptor.resources.contains(publication.resource) || !publication.descriptor.profiles.contains(publication.profile) || publication.descriptor.profiles.get(publication.profile).contains(null))
      RAISE.invalidArgumentFault("Prepared media publication descriptor evidence is invalid")
    if (publication.descriptorFile != publication.descriptorFile.toAbsolutePath.normalize() || publication.descriptorRoot != publication.descriptorRoot.toAbsolutePath.normalize() || Option(publication.descriptorFile.getParent).forall(_ != publication.descriptorRoot))
      RAISE.invalidArgumentFault("Prepared media publication descriptor paths are invalid")
    if (!_is_direct_regular_file(publication.descriptorFile) || !_is_sha256(publication.descriptorSha256) || _sha256(publication.descriptorFile) != publication.descriptorSha256)
      RAISE.invalidArgumentFault(s"Prepared media descriptor has changed: ${publication.descriptorFile}")
    val expectedroot = _bind_profile_root(_profile_root(publication.descriptorRoot, publication.descriptor, publication.profile))
    if (expectedroot != publication.profileRoot || expectedroot != publication.profileRootIdentity)
      RAISE.invalidArgumentFault("Prepared media publication profile root has changed")
    val expectedpublishable = publication.resource.output.orElse(publication.resource.source).map(
      _resolve_relative(publication.descriptorRoot, _, s"resources.${publication.resource.id}.publishable")
    ).getOrElse(RAISE.invalidArgumentFault(s"Prepared media publication has no publishable path: ${publication.resource.id}"))
    if (expectedpublishable != publication.publishablePath)
      RAISE.invalidArgumentFault("Prepared media publication source path is invalid")
    val expecteddestination = publication.resource.publications.get(publication.profile).map(
      _publication_path(publication.descriptorRoot, publication.descriptor, publication.profile, _)
    ).getOrElse(RAISE.invalidArgumentFault(s"Prepared media publication has no profile destination: ${publication.resource.id}"))
    if (expecteddestination != publication.destination)
      RAISE.invalidArgumentFault("Prepared media publication destination path is invalid")
    if (_destination_identity(publication.destination, publication.profileRoot) != publication.destinationIdentity)
      RAISE.invalidArgumentFault("Prepared media publication destination identity has changed")
    if (!_is_direct_regular_file(publication.publishablePath))
      RAISE.invalidArgumentFault(s"Prepared media source is no longer a direct regular non-symlink file: ${publication.publishablePath}")
    if (!_is_sha256(publication.sourceSha256) || _sha256(publication.publishablePath) != publication.sourceSha256)
      RAISE.invalidArgumentFault(s"Prepared media source has changed: ${publication.publishablePath}")
    val actualstate = _destination_state(publication.destination)
    if (actualstate != publication.destinationState)
      RAISE.invalidArgumentFault(s"Prepared media destination has changed: ${publication.destination}")
    val expected = _publication_disposition(publication.sourceSha256, publication.destinationState, publication.force, publication.destination)
    if (expected != publication.disposition)
      RAISE.invalidArgumentFault(s"Prepared media disposition is invalid: ${publication.destination}")
  }

  private def _validate_unique_destinations(publications: Vector[PreparedPublication]): Unit = {
    val duplicates = publications.groupBy { publication =>
      if (publication == null || publication.destination == null || publication.destinationIdentity == null)
        RAISE.invalidArgumentFault("Prepared media publication destination must not be null")
      publication.destinationIdentity
    }.collect {
      case (destination, values) if values.size > 1 => destination
    }.toVector.sortBy(_.toString)
    duplicates.headOption.foreach(destination =>
      RAISE.invalidArgumentFault(s"Media publication destinations must be unique: $destination")
    )
  }

  private def _destination_identity(destination: Path, profileroot: Path): Path = {
    val normalizedroot = _bind_profile_root(profileroot)
    val normalizeddestination = destination.toAbsolutePath.normalize()
    if (normalizeddestination != destination || !normalizeddestination.startsWith(normalizedroot) || normalizeddestination == normalizedroot)
      RAISE.invalidArgumentFault(s"Media publication path escapes profile root: $destination")
    val components = normalizedroot.relativize(normalizeddestination).iterator.asScala.toVector
    components.dropRight(1).foldLeft(normalizedroot) { (current, component) =>
      val next = current.resolve(component)
      if (Files.exists(next, LinkOption.NOFOLLOW_LINKS))
        _validate_direct_directory(next, "Media publication destination parent")
      next
    }
    normalizedroot.resolve(normalizedroot.relativize(normalizeddestination)).normalize()
  }

  private def _validate_lexical_destination(destination: Path, profileroot: Path): Unit = {
    val normalizedroot = profileroot.toAbsolutePath.normalize()
    val normalizeddestination = destination.toAbsolutePath.normalize()
    if (normalizedroot != profileroot || normalizeddestination != destination || !normalizeddestination.startsWith(normalizedroot) || normalizeddestination == normalizedroot)
      RAISE.invalidArgumentFault(s"Media publication path escapes profile root: $destination")
  }

  private def _bind_profile_root(profileroot: Path): Path = {
    val normalizedroot = Option(profileroot).map(_.toAbsolutePath.normalize()).getOrElse(
      RAISE.invalidArgumentFault("Media publication profile root must not be null")
    )
    _validate_direct_directory(normalizedroot, "Media publication profile root")
    normalizedroot
  }

  private def _validate_direct_directory(path: Path, label: String): Unit = {
    if (Files.isSymbolicLink(path) || !Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS))
      RAISE.invalidArgumentFault(s"$label must be an existing direct non-symlink directory: $path")
    val realpath =
      try path.toRealPath()
      catch {
        case e: java.io.IOException => RAISE.invalidArgumentFault(s"$label cannot be resolved: ${e.getMessage}")
      }
    if (realpath != path.toAbsolutePath.normalize())
      RAISE.invalidArgumentFault(s"$label must not be a lexical or real-path alias: $path")
  }

  private def _destination_state(destination: Path): DestinationState = {
    if (!Files.exists(destination, LinkOption.NOFOLLOW_LINKS))
      DestinationState.Absent
    else if (_is_direct_regular_file(destination))
      DestinationState.Existing(_sha256(destination))
    else
      RAISE.invalidArgumentFault(s"Media publication destination must be absent or a direct regular non-symlink file: $destination")
  }

  private def _publication_disposition(
    sourcehash: String,
    destinationstate: DestinationState,
    force: Boolean,
    destination: Path
  ): PublicationDisposition =
    destinationstate match {
      case DestinationState.Absent => PublicationDisposition.Create
      case DestinationState.Existing(destinationhash) if sourcehash == destinationhash => PublicationDisposition.Reuse
      case DestinationState.Existing(_) if force => PublicationDisposition.Replace
      case DestinationState.Existing(_) =>
        RAISE.invalidArgumentFault(s"Media publication destination differs; use force to replace: $destination")
    }

  private def _is_direct_regular_file(path: Path): Boolean =
    path != null && !Files.isSymbolicLink(path) && Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)

  private def _is_sha256(value: String): Boolean =
    value != null && value.matches("[0-9a-f]{64}")

  private def _create_and_bind_legacy_profile_root(root: Path, descriptor: Descriptor, profile: String): Path = {
    val profileroot = _profile_root(root, descriptor, profile)
    if (Files.exists(profileroot, LinkOption.NOFOLLOW_LINKS))
      _bind_profile_root(profileroot)
    else {
      val normalizedroot = profileroot.toAbsolutePath.normalize()
      val ancestry = Iterator.iterate(normalizedroot)(_.getParent).takeWhile(_ != null).toVector.reverse
      var current = ancestry.head
      _validate_direct_directory(current, "Media legacy publication root ancestor")
      ancestry.tail.foreach { component =>
        current = component
        if (!Files.exists(current, LinkOption.NOFOLLOW_LINKS)) {
          try Files.createDirectory(current)
          catch {
            case _: java.nio.file.FileAlreadyExistsException => ()
          }
        }
        _validate_direct_directory(current, "Media legacy publication root")
      }
      _bind_profile_root(normalizedroot)
    }
  }

  private def _ensure_destination_parent(publication: PreparedPublication): Path = {
    val root = _bind_profile_root(publication.profileRoot)
    if (root != publication.profileRootIdentity)
      RAISE.invalidArgumentFault("Prepared media publication profile root identity has changed")
    val relative = root.relativize(publication.destination)
    val components = relative.iterator.asScala.toVector
    if (components.isEmpty)
      RAISE.invalidArgumentFault(s"Media publication destination has no leaf: ${publication.destination}")
    val parent = components.dropRight(1).foldLeft(root) { (current, component) =>
      val next = current.resolve(component)
      if (!Files.exists(next, LinkOption.NOFOLLOW_LINKS)) {
        try Files.createDirectory(next)
        catch {
          case _: java.nio.file.FileAlreadyExistsException => ()
        }
      }
      _validate_direct_directory(next, "Media publication destination parent")
      next
    }
    if (_destination_identity(publication.destination, root) != publication.destinationIdentity)
      RAISE.invalidArgumentFault("Prepared media publication destination identity has changed")
    parent
  }

  private def _install_publication(
    publication: PreparedPublication,
    replace: Boolean,
    temporaries: ArrayBuffer[Path]
  ): Unit = {
    val parent = _ensure_destination_parent(publication)
    _destination_state(publication.destination)
    val name = publication.destination.getFileName.toString
    val temporary = Files.createTempFile(parent, s".$name.", ".cozy-media.tmp")
    temporaries += temporary
    try {
      _copy_and_force(publication.publishablePath, temporary)
      if (_sha256(temporary) != publication.sourceSha256)
        RAISE.invalidArgumentFault(s"Media temporary publication hash differs: $temporary")
      if (replace)
        _atomic_move_replace(temporary, publication.destination)
      else
        _atomic_create(temporary, publication.destination)
      temporaries -= temporary
    } catch {
      case e: AtomicMoveNotSupportedException =>
        RAISE.invalidArgumentFault(s"Media publication requires atomic move: ${e.getMessage}")
      case e: java.io.IOException =>
        RAISE.invalidArgumentFault(s"Media publication failed: ${e.getMessage}")
      case e: UnsupportedOperationException =>
        RAISE.invalidArgumentFault(s"Media publication hard-link installation is unsupported: ${e.getMessage}")
    }
  }

  private def _copy_and_force(source: Path, temporary: Path): Unit = {
    val input = Files.newInputStream(source)
    val channel = FileChannel.open(temporary, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING)
    try {
      val bytes = new Array[Byte](8192)
      var size = input.read(bytes)
      while (size >= 0) {
        if (size > 0) {
          val buffer = ByteBuffer.wrap(bytes, 0, size)
          while (buffer.hasRemaining)
            channel.write(buffer)
        }
        size = input.read(bytes)
      }
      channel.force(true)
    } finally {
      try input.close()
      finally channel.close()
    }
  }

  private def _atomic_move_replace(temporary: Path, destination: Path): Unit =
    Files.move(temporary, destination, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)

  private def _atomic_create(temporary: Path, destination: Path): Unit = {
    Files.createLink(destination, temporary)
    Files.delete(temporary)
  }

  private def _action(resource: Resource, source: Option[Path], output: Option[Path], project: Option[Path]): Action =
    resource.build match {
      case "video-project" =>
        if (project.exists(Files.isRegularFile(_))) Action.DelegateVideo else Action.MissingSource
      case "prebuilt" =>
        if (source.exists(Files.isRegularFile(_))) Action.Current else Action.MissingSource
      case _ if !source.exists(Files.isRegularFile(_)) =>
        Action.MissingSource
      case _ if output.exists(Files.isRegularFile(_)) && output.get.toFile.lastModified() >= source.get.toFile.lastModified() =>
        Action.Current
      case _ =>
        Action.Build
    }

  private def _selected(plan: Plan, target: Option[String]): Vector[ResolvedResource] =
    target match {
      case Some(id) =>
        plan.resources.filter(_.resource.id == id) match {
          case xs if xs.nonEmpty => xs
          case _ => RAISE.invalidArgumentFault(s"Unknown media target: $id")
        }
      case None => plan.resources
    }

  private def _build_resource(plan: Plan, resolved: ResolvedResource, runner: ProcessRunner): String =
    resolved.action match {
      case Action.MissingSource =>
        RAISE.invalidArgumentFault(s"Missing media source for ${resolved.resource.id}")
      case Action.Current =>
        s"${resolved.resource.id}: current"
      case Action.Build =>
        val source = resolved.source.get
        val output = resolved.output.get
        Option(output.getParent).foreach(Files.createDirectories(_))
        resolved.resource.build match {
          case "copy" =>
            Files.copy(source, output, StandardCopyOption.REPLACE_EXISTING)
          case "svg-to-png" =>
            val code = runner.run(Vector("rsvg-convert", source.toString, "-o", output.toString), plan.descriptorRoot)
            if (code != 0)
              RAISE.invalidArgumentFault(s"rsvg-convert failed for ${resolved.resource.id}: exit=$code")
          case other =>
            RAISE.invalidArgumentFault(s"Unsupported executable media build kind: $other")
        }
        s"${resolved.resource.id}: built $output"
      case Action.DelegateVideo =>
        val project = resolved.project.get
        val result = CozyVideo.build(CozyVideo.BuildConfig(project, dryRun = false, checkTools = false), CozyVideo.VideoToolRegistry.default)
        s"${resolved.resource.id}: delegated to cozy video build ($result)"
    }

  private def _verify_plan(plan: Plan, resources: Vector[ResolvedResource], profile: Option[String]): Vector[String] = {
    val knowledge = if (Files.isRegularFile(plan.knowledgeSource)) Vector.empty else Vector(s"missing knowledge source: ${plan.knowledgeSource}")
    knowledge ++ resources.flatMap { resolved =>
      val resource = resolved.resource
      val sourcefindings =
        if (resource.build == "video-project") {
          resolved.project match {
            case Some(path) if Files.isRegularFile(path) =>
              try CozyVideo.verifyCredits(path).map(x => s"${resource.id}: $x")
              catch {
                case NonFatal(e) => Vector(s"${resource.id}: video credit verification failed: ${e.getMessage}")
              }
            case _ => Vector(s"${resource.id}: missing video project")
          }
        } else if (resolved.source.exists(Files.isRegularFile(_))) Vector.empty
        else Vector(s"${resource.id}: missing source")
      val outputfindings = resolved.output.orElse(if (resource.build == "prebuilt") resolved.source else None) match {
        case Some(path) if Files.isRegularFile(path) => _dimension_findings(resource, path)
        case Some(path) => Vector(s"${resource.id}: missing output $path")
        case None => Vector(s"${resource.id}: missing output declaration")
      }
      val publishsource = resolved.output.orElse(if (resource.build == "prebuilt") resolved.source else None)
      val publicationfindings = profile.toVector.flatMap { name =>
        resolved.publications.get(name) match {
          case Some(path) if !Files.isRegularFile(path) => Vector(s"${resource.id}: missing publication $path")
          case Some(path) if publishsource.exists(output => _sha256(output) != _sha256(path)) => Vector(s"${resource.id}: publication differs from output $path")
          case Some(_) => Vector.empty
          case None => Vector.empty
        }
      }
      sourcefindings ++ outputfindings ++ publicationfindings
    }
  }

  private def _dimension_findings(resource: Resource, path: Path): Vector[String] =
    (resource.width, resource.height) match {
      case (Some(width), Some(height)) if path.getFileName.toString.toLowerCase(java.util.Locale.ROOT).endsWith(".png") =>
        _png_dimensions(path) match {
          case Some((actualwidth, actualheight)) if actualwidth == width && actualheight == height => Vector.empty
          case Some((actualwidth, actualheight)) => Vector(s"${resource.id}: expected ${width}x${height}, found ${actualwidth}x${actualheight}")
          case None => Vector(s"${resource.id}: invalid PNG $path")
        }
      case _ => Vector.empty
    }

  private def _png_dimensions(path: Path): Option[(Int, Int)] = {
    val bytes = Files.readAllBytes(path)
    val signature = Array[Byte](0x89.toByte, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a)
    if (bytes.length < 24 || !bytes.take(8).sameElements(signature)) None
    else {
      val buffer = ByteBuffer.wrap(bytes, 16, 8)
      Some(buffer.getInt -> buffer.getInt)
    }
  }

  private def _write_manifest(plan: Plan): Unit = {
    val entries = plan.resources.flatMap { resolved =>
      resolved.output.orElse(if (resolved.resource.build == "prebuilt") resolved.source else None).filter(Files.isRegularFile(_)).map { output =>
        Json.obj(
          "id" -> Json.fromString(resolved.resource.id),
          "path" -> Json.fromString(plan.descriptorRoot.relativize(output).toString),
          "sha256" -> Json.fromString(_sha256(output))
        )
      }
    }
    val json = Json.obj(
      "schema" -> Json.fromString(_schema),
      "knowledge" -> Json.fromString(plan.descriptor.knowledge.id),
      "resources" -> Json.fromValues(entries)
    )
    val path = plan.descriptorRoot.resolve("target/cozy-media/manifest.json")
    Files.createDirectories(path.getParent)
    Files.writeString(path, json.spaces2 + "\n", StandardCharsets.UTF_8)
  }

  private def _sha256(path: Path): String = {
    val digest = MessageDigest.getInstance("SHA-256")
    val stream = Files.newInputStream(path)
    try {
      val buffer = new Array[Byte](8192)
      var size = stream.read(buffer)
      while (size >= 0) {
        if (size > 0) digest.update(buffer, 0, size)
        size = stream.read(buffer)
      }
    } finally stream.close()
    digest.digest().map(x => f"${x & 0xff}%02x").mkString
  }

  private def _normalize_property_args(args: List[String]): List[String] =
    args.flatMap {
      case x if x.startsWith("--") && x.contains("=") =>
        val keyvalue = x.drop(2).split("=", 2)
        if (keyvalue.length == 2 && _property_options.contains(keyvalue(0)))
          List("--" + keyvalue(0), keyvalue(1))
        else
          List(x)
      case x =>
        List(x)
    }
}
