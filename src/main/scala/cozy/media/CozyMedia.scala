package cozy.media

import org.goldenport.RAISE
import org.goldenport.cli.spec
import org.goldenport.config.StructuredDocumentLoader
import org.goldenport.io.InputSource
import org.goldenport.io.StringInputSource
import cozy.config.CozyProjectContext
import cozy.publication.{CozyArticleMediaSiteCommand, CozyArticleMediaWipCommand}
import cozy.runtime.CozyCliArgs
import cozy.video.CozyVideo
import io.circe.{Decoder, HCursor, Json}
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.nio.file.{Files, LinkOption, Path, Paths, StandardCopyOption}
import java.security.MessageDigest
import scala.collection.JavaConverters._
import scala.util.control.NonFatal

/*
 * @since   Jul. 19, 2026
 *  version Jul. 20, 2026
 * @version Aug. 25, 2026
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
    rootEnv: Option[String],
    presentation: Option[CozyMediaPresentation.ProfileConfig] = None
  )
  object Profile {
    implicit val decoder: Decoder[Profile] = (c: HCursor) =>
      for {
        _ <- _require_profile_keys(c)
        root <- c.downField("root").as[Option[String]]
        rootenv <- c.downField("rootEnv").as[Option[String]].flatMap {
          case value @ Some(_) => Right(value)
          case None => c.downField("root-env").as[Option[String]]
        }
        presentation <- _optional_article_media_field[CozyMediaPresentation.ProfileConfig](c, "presentation")
      } yield Profile(root, rootenv, presentation)
  }

  final case class DescriptorArticleMedia(
    articleIdentity: String,
    publicationProfile: String
  )
  object DescriptorArticleMedia {
    implicit val decoder: Decoder[DescriptorArticleMedia] = (c: HCursor) =>
      for {
        _ <- _require_exact_article_media_keys(c, Set("articleIdentity", "publicationProfile"))
        articleidentity <- c.downField("articleIdentity").as[String]
        publicationprofile <- c.downField("publicationProfile").as[String]
      } yield DescriptorArticleMedia(articleidentity, publicationprofile)
  }

  final case class ResourceArticleMedia(
    role: String,
    publicPath: Option[String],
    mediaType: Option[String],
    alt: Option[String],
    production: Option[String]
  )
  object ResourceArticleMedia {
    implicit val decoder: Decoder[ResourceArticleMedia] = (c: HCursor) =>
      for {
        _ <- _require_article_media_object(c)
        role <- c.downField("role").as[String]
        _ <- _require_resource_article_media_keys(c, role)
        publicpath <- _optional_article_media_field[String](c, "publicPath")
        mediatype <- _optional_article_media_field[String](c, "mediaType")
        alt <- _optional_article_media_field[String](c, "alt")
        production <- _optional_article_media_field[String](c, "production")
      } yield ResourceArticleMedia(role, publicpath, mediatype, alt, production)
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
    publications: Map[String, String],
    articleMedia: Option[ResourceArticleMedia] = None,
    presentation: Option[CozyMediaPresentation.ResourceConfig] = None
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
        articlemedia <- _optional_article_media_field[ResourceArticleMedia](c, "articleMedia")
        presentation <- _optional_article_media_field[CozyMediaPresentation.ResourceConfig](c, "presentation")
      } yield Resource(id, kind, language, role, source, output, build.getOrElse("copy"), width, height, project, publications.getOrElse(Map.empty), articlemedia, presentation)
  }

  final case class Descriptor(
    schema: String,
    knowledge: Knowledge,
    languages: Vector[String],
    resources: Vector[Resource],
    profiles: Map[String, Profile],
    articleMedia: Option[DescriptorArticleMedia] = None,
    receipt: Option[CozyMediaReceipt.Config] = None
  )
  object Descriptor {
    implicit val decoder: Decoder[Descriptor] = (c: HCursor) =>
      for {
        schema <- c.downField("schema").as[String]
        knowledge <- c.downField("knowledge").as[Knowledge]
        languages <- c.downField("languages").as[Option[Vector[String]]]
        resources <- c.downField("resources").as[Option[Vector[Resource]]]
        profiles <- c.downField("profiles").as[Option[Map[String, Profile]]]
        articlemedia <- _optional_article_media_field[DescriptorArticleMedia](c, "articleMedia")
        receipt <- _optional_article_media_field[CozyMediaReceipt.Config](c, "receipt")
      } yield Descriptor(schema, knowledge, languages.getOrElse(Vector.empty), resources.getOrElse(Vector.empty).sortBy(_.id), profiles.getOrElse(Map.empty), articlemedia, receipt)
  }

  sealed trait Action { def label: String }
  object Action {
    case object Build extends Action { val label = "build" }
    case object Current extends Action { val label = "current" }
    case object MissingSource extends Action { val label = "missing-source" }
    case object AdoptPrebuilt extends Action { val label = "adopt-prebuilt" }
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

  final case class EffectiveProfile(
    id: String,
    descriptor: Option[Profile],
    configuration: Option[CozyProjectContext.PublicationProfile],
    descriptorroot: Path,
    resolvedroot: Option[Path],
    externalRoot: Boolean
  ) {
    def resolvedRoot: Path = resolvedroot.getOrElse {
      descriptor.flatMap(_.rootEnv).map { name =>
        if (name.isEmpty || name != name.trim)
          RAISE.invalidArgumentFault(s"Media profile $id rootEnv must be a non-empty exact value")
        val value = sys.env.get(name).filter(x => x.nonEmpty && x == x.trim).getOrElse(
          RAISE.invalidArgumentFault(s"Media profile $id requires environment variable: $name")
        )
        try Path.of(value).toAbsolutePath.normalize() catch {
          case NonFatal(_) => RAISE.invalidArgumentFault(s"Media profile $id rootEnv is invalid: $name")
        }
      }.orElse {
        descriptor.flatMap(_.root).map { value =>
          val resolved = _resolve_relative(descriptorroot, value, s"profiles.$id.root")
          if (configuration.nonEmpty && !resolved.startsWith(descriptorroot))
            RAISE.invalidArgumentFault(s"Media profile $id root escapes descriptor root: $value")
          resolved
        }
      }.getOrElse(
        RAISE.invalidArgumentFault(s"Media profile has no resolved root: $id")
      )
    }
    def siteKind: Option[String] = configuration.map(_.siteKind)
    def layer: Option[String] = configuration.map(_.layer)
    def sourcePath: Option[Path] = configuration.map(_.sourcePath)
  }

  final case class Plan(
    descriptorFile: Path,
    descriptorRoot: Path,
    descriptor: Descriptor,
    knowledgeSource: Path,
    resources: Vector[ResolvedResource],
    context: CozyProjectContext.Context,
    profiles: Map[String, EffectiveProfile],
    effectiveProfile: Option[EffectiveProfile]
  ) {
    def profile(id: String): Option[EffectiveProfile] = profiles.get(id)
  }

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
    inputSetSha256: String,
    resource: Resource,
    target: Option[String],
    context: CozyProjectContext.Context,
    effectiveProfile: EffectiveProfile,
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
  private val _build_kinds = Set("copy", "svg-to-png", "prebuilt", "video-project", "presentation")
  private val _property_options = Set("target", "profile")
  private val _publication_layer_names = Vector("built-in", "user", "project-conf", "project-local", "package-conf", "package-local")

  private def _optional_article_media_field[A: Decoder](c: HCursor, field: String): Decoder.Result[Option[A]] =
    c.downField(field).success match {
      case Some(cursor) => cursor.as[A].map(Some(_))
      case None => Right(None)
    }

  private def _require_exact_article_media_keys(c: HCursor, keys: Set[String]): Decoder.Result[Unit] =
    c.value.asObject match {
      case Some(value) if value.keys.toSet == keys => Right(())
      case Some(_) => Left(io.circe.DecodingFailure("articleMedia requires exactly: " + keys.toVector.sorted.mkString(", "), c.history))
      case None => Left(io.circe.DecodingFailure("articleMedia must be an object", c.history))
    }

  private def _require_profile_keys(c: HCursor): Decoder.Result[Unit] =
    c.value.asObject match {
      case Some(value) =>
        val keys = value.keys.toSet
        val allowed = Set("root", "rootEnv", "root-env", "presentation")
        if (keys.subsetOf(allowed) && !(keys.contains("rootEnv") && keys.contains("root-env"))) Right(())
        else Left(io.circe.DecodingFailure("media profile permits only root and rootEnv", c.history))
      case None => Left(io.circe.DecodingFailure("media profile must be an object", c.history))
    }

  private def _require_article_media_object(c: HCursor): Decoder.Result[Unit] =
    c.value.asObject match {
      case Some(_) => Right(())
      case None => Left(io.circe.DecodingFailure("articleMedia must be an object", c.history))
    }

  private def _require_resource_article_media_keys(c: HCursor, role: String): Decoder.Result[Unit] = {
    val keys = c.value.asObject.map(_.keys.toSet).getOrElse(
      return Left(io.circe.DecodingFailure("articleMedia must be an object", c.history))
    )
    role match {
      case "infographic" =>
        val required = Set("role", "publicPath")
        val allowed = required ++ Set("mediaType", "alt")
        if (required.subsetOf(keys) && keys.subsetOf(allowed)) Right(())
        else Left(io.circe.DecodingFailure("articleMedia infographic requires role and publicPath and permits only mediaType and alt", c.history))
      case "video" =>
        val expected = Set("role", "production")
        if (keys == expected) Right(())
        else Left(io.circe.DecodingFailure("articleMedia video requires exactly role and production", c.history))
      case _ => Left(io.circe.DecodingFailure("articleMedia role must be infographic or video", c.history))
    }
  }

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
      case "media" :: "slide" :: "validate" :: rest =>
        println(_slide_validate(CommandConfig.create(rest)))
        true
      case "media" :: "slide" :: "plan" :: rest =>
        println(_slide_plan(CommandConfig.create(rest)))
        true
      case "media" :: "slide" :: "build" :: rest =>
        println(_slide_build(CommandConfig.create(rest), runner))
        true
      case "media" :: "slide" :: "verify" :: rest =>
        println(_slide_verify(CommandConfig.create(rest)))
        true
      case "media" :: "review" :: "align" :: rest =>
        println(CozyMediaReviewState.executeAlign(rest))
        true
      case "media" :: "scaffold" :: "article" :: rest =>
        println(CozyMediaArticleScaffold.execute(rest))
        true
      case "media" :: "register-site" :: rest =>
        println(CozyArticleMediaSiteCommand.execute(rest))
        true
      case "media" :: "register-site-wip" :: rest =>
        println(CozyArticleMediaWipCommand.execute(rest))
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
    ) ++ _context_lines(mediaplan) ++ mediaplan.resources.map { resolved =>
      val resource = resolved.resource
      val output = resolved.output.map(_.toString).getOrElse("-")
      val publications = resolved.publications.toVector.sortBy(_._1).map { case (profile, path) => s"$profile=$path" }.mkString(",")
      s"  - ${resource.id}: kind=${resource.kind}, language=${resource.language.getOrElse("-")}, role=${resource.role.getOrElse("-")}, build=${resource.build}, output=$output, publications=${if (publications.nonEmpty) publications else "-"}"
    }
    lines.mkString("\n")
  }

  def plan(config: CommandConfig): String = {
    val mediaplan = _plan(config)
    val selected = _selected(mediaplan, config.target)
    val profile = mediaplan.effectiveProfile.map(_.id)
    val lines = Vector(
      "Cozy Media Plan",
      s"descriptor: ${mediaplan.descriptorFile}",
      s"knowledge: ${mediaplan.descriptor.knowledge.id}",
      s"profile: ${profile.getOrElse("-")}"
    ) ++ _context_lines(mediaplan) ++ selected.map { resolved =>
      val publications = profile.toVector.flatMap(resolved.publications.get).map(path => s", publish=$path").mkString
      s"  - ${resolved.resource.id}: ${resolved.action.label}${publications}"
    }
    lines.mkString("\n")
  }

  private[cozy] def resolvePlan(config: CommandConfig): Plan = _plan(config)

  private[cozy] def resolvePlan(config: CommandConfig, descriptorBytes: Vector[Byte]): Plan =
    _plan(config, Some(descriptorBytes))

  private[cozy] def effectiveProfile(plan: Plan, id: String): EffectiveProfile =
    Option(plan).flatMap(_.profile(id)).getOrElse {
      val value = Option(plan).getOrElse(RAISE.invalidArgumentFault("Media plan must be defined"))
      _missing_publication_profile(
        value.descriptor,
        value.descriptorFile,
        value.context,
        id,
        s"Undefined media profile: $id"
      )
    }

  private[cozy] def requireConfiguredPublicationProfile(
    plan: Plan,
    id: String
  ): CozyProjectContext.PublicationProfile =
    Option(plan).flatMap(_.context.publicationProfile(id)).getOrElse {
      val value = Option(plan).getOrElse(RAISE.invalidArgumentFault("Media plan must be defined"))
      _missing_publication_profile(
        value.descriptor,
        value.descriptorFile,
        value.context,
        id,
        s"Article-media site binding requires configured publication profile: $id"
      )
    }

  def build(config: CommandConfig, runner: ProcessRunner = ProcessRunner.default): String = {
    val mediaplan = _plan(config)
    val selected = _selected(mediaplan, config.target)
    val plannedidentity = if (config.dryRun) None else Some(CozyMediaReceipt.capture(mediaplan))
    val ordered = selected.sortBy(resolved => if (resolved.resource.build == "presentation") 1 else 0)
    val results = ordered.map { resolved =>
      if (config.dryRun)
        s"${resolved.resource.id}: ${resolved.action.label} (dry-run)"
      else {
        if (config.target.isDefined && resolved.resource.build == "presentation")
          CozyMediaPresentation.requireDependenciesCurrent(mediaplan, resolved)
        _build_resource(mediaplan, resolved, runner)
      }
    }
    if (!config.dryRun) {
      val findings = _verify_plan(mediaplan, selected, None, requirereceipt = false)
      if (findings.nonEmpty)
        RAISE.invalidArgumentFault("Media build structural verification failed:\n" + findings.map(x => s"- $x").mkString("\n"))
      val acceptedidentity = CozyMediaReceipt.capture(mediaplan)
      if (plannedidentity.exists(_.inputSetSha256 != acceptedidentity.inputSetSha256))
        RAISE.invalidArgumentFault("Media receipt inputs changed during selected build; no fresh acceptance evidence was written")
      val reviewstates = selected.filter(_.resource.build == "presentation").map(resolved => CozyMediaReviewState.prepareRefresh(mediaplan, resolved))
      val receipt = CozyMediaReceipt.prepare(mediaplan, selected, acceptedidentity, config.target)
      CozyMediaReceipt.commit(reviewstates, receipt)
    }
    (Vector("Cozy Media Build", s"descriptor: ${mediaplan.descriptorFile}") ++ results.map(x => s"  - $x")).mkString("\n")
  }

  def verify(config: CommandConfig): String = {
    val mediaplan = _plan(config)
    val selected = _selected(mediaplan, config.target)
    val selectedprofile = mediaplan.effectiveProfile.map(_.id)
    val findings = _verify_plan(mediaplan, selected, selectedprofile, requirereceipt = true)
    if (findings.nonEmpty)
      RAISE.invalidArgumentFault("Media verification failed:\n" + findings.map(x => s"- $x").mkString("\n"))
    val profile = selectedprofile.map(x => s" profile=$x").getOrElse("")
    s"Cozy Media Verify\nstatus: valid$profile\nresources: ${selected.size}"
  }

  def publish(config: CommandConfig): String = {
    val mediaplan = _plan(config)
    val selected = _selected(mediaplan, config.target)
    val profile = config.profile.getOrElse(RAISE.invalidArgumentFault("Missing --profile for media publish"))
    val candidates = selected.filter(_.publications.contains(profile))
    if (candidates.isEmpty)
      RAISE.invalidArgumentFault(s"No media resources publish to profile: $profile")
    val findings = _verify_plan(mediaplan, candidates, None, requirereceipt = true)
    if (findings.nonEmpty)
      RAISE.invalidArgumentFault("Media publication preflight failed:\n" + findings.map(x => s"- $x").mkString("\n"))
    val preflight = _prepare_legacy_publications(mediaplan, candidates, profile, config.target, force = true)
    val prepared =
      if (config.dryRun) preflight
      else {
        _create_and_bind_legacy_profile_root(mediaplan, profile)
        _prepare_publications(mediaplan, candidates, profile, config.target, force = true)
      }
    if (!config.dryRun)
      commitPublication(prepared, () => CozyMediaReceipt.requireCurrent(mediaplan, candidates))
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
  ): Vector[PublicationResult] =
    CozyMediaPublicationTransaction.commit(prepared, beforefirstinstall)

  private def _plan(config: CommandConfig, descriptorbytes: Option[Vector[Byte]] = None): Plan = {
    val descriptorfile = config.descriptorFile.toAbsolutePath.normalize()
    if (!_is_direct_regular_file(descriptorfile))
      RAISE.invalidArgumentFault(s"Missing media descriptor: $descriptorfile")
    val descriptorinput = descriptorbytes.map { bytes =>
      StringInputSource(new String(bytes.toArray, StandardCharsets.UTF_8), descriptorfile.toUri)
    }.getOrElse(InputSource(descriptorfile.toFile))
    val descriptor = StructuredDocumentLoader.loadDocument[Descriptor](descriptorinput).take
    val descriptorroot = Option(descriptorfile.getParent).getOrElse(Paths.get(".").toAbsolutePath.normalize())
    val context = CozyProjectContext.resolve(descriptorroot)
    val profiles = _effective_profiles(descriptorroot, descriptor, context)
    _validate_descriptor(descriptor, profiles, descriptorfile, context)
    val selectedprofile = config.profile.orElse(descriptor.articleMedia.map(_.publicationProfile))
    val effectiveprofile = selectedprofile.map { id =>
      profiles.getOrElse(id, _missing_publication_profile(
        descriptor,
        descriptorfile,
        context,
        id,
        _profile_selection_failure_prefix(descriptor, id)
      ))
    }
    val knowledgesource = _resolve_relative(descriptorroot, descriptor.knowledge.source, "knowledge.source")
    val unresolved = descriptor.resources.map { resource =>
      val source = resource.source.map(_resolve_relative(descriptorroot, _, s"resources.${resource.id}.source"))
      val output = resource.output.map(_resolve_relative(descriptorroot, _, s"resources.${resource.id}.output"))
      val project = resource.project.map(_resolve_relative(descriptorroot, _, s"resources.${resource.id}.project"))
      val publications = resource.publications.toVector.flatMap { case (profile, path) =>
        selectedprofile.filter(_ == profile).map { _ =>
          profile -> _publication_path(profiles, profile, path)
        }
      }.toMap
      ResolvedResource(resource, source, output, project, publications, Action.Build)
    }
    val preliminary = Plan(descriptorfile, descriptorroot, descriptor, knowledgesource, unresolved, context, profiles, effectiveprofile)
    preliminary.copy(resources = unresolved.map { resolved =>
      resolved.copy(action = CozyMediaReceipt.action(preliminary, resolved))
    })
  }

  private def _validate_descriptor(
    descriptor: Descriptor,
    profiles: Map[String, EffectiveProfile],
    descriptorfile: Path,
    context: CozyProjectContext.Context
  ): Unit = {
    if (descriptor.schema != _schema)
      RAISE.invalidArgumentFault(s"Unsupported media schema: ${descriptor.schema}. Expected: ${_schema}")
    if (descriptor.knowledge.id.trim.isEmpty)
      RAISE.invalidArgumentFault("Media knowledge.id must not be empty")
    descriptor.receipt.foreach(CozyMediaReceipt.validateConfig)
    descriptor.articleMedia.foreach { articlemedia =>
      _validate_article_media_value(articlemedia.articleIdentity, "Media articleMedia.articleIdentity")
      _validate_article_media_value(articlemedia.publicationProfile, "Media articleMedia.publicationProfile")
    }
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
      resource.articleMedia.foreach {
        case ResourceArticleMedia("infographic", publicpath, _, _, _) =>
          _validate_article_media_value(publicpath.getOrElse(""), s"Media resource ${resource.id} articleMedia.publicPath")
          if (resource.kind != "infographic")
            RAISE.invalidArgumentFault(s"Media resource ${resource.id} articleMedia infographic requires kind: infographic")
        case ResourceArticleMedia("video", _, _, _, production) =>
          _validate_article_media_value(production.getOrElse(""), s"Media resource ${resource.id} articleMedia.production")
          if (resource.kind != "video")
            RAISE.invalidArgumentFault(s"Media resource ${resource.id} articleMedia video requires kind: video")
        case _ =>
          RAISE.invalidArgumentFault(s"Media resource ${resource.id} articleMedia role is invalid")
      }
      resource.publications.keys.foreach { profile =>
        if (!profiles.contains(profile))
          _missing_publication_profile(
            descriptor,
            descriptorfile,
            context,
            profile,
            s"Media resource ${resource.id} uses undefined profile: $profile"
          )
      }
    }
    CozyMediaPresentation.validateDescriptor(descriptor, profiles, Option(descriptorfile.getParent).getOrElse(Paths.get(".").toAbsolutePath.normalize()))
  }

  private def _validate_article_media_value(value: String, label: String): Unit =
    if (value == null || value.trim.isEmpty || value != value.trim)
      RAISE.invalidArgumentFault(s"$label must be a non-empty trimmed string")

  private def _resolve_relative(root: Path, value: String, label: String): Path = {
    val path = Path.of(value)
    if (path.isAbsolute)
      RAISE.invalidArgumentFault(s"$label must be relative: $value")
    root.resolve(path).normalize()
  }

  private def _publication_path(profiles: Map[String, EffectiveProfile], profilename: String, value: String): Path = {
    val profileroot = profiles.getOrElse(profilename, RAISE.invalidArgumentFault(s"Undefined media profile: $profilename")).resolvedRoot
    val path = Path.of(value)
    if (path.isAbsolute)
      RAISE.invalidArgumentFault(s"Media publication path must be relative: $value")
    val destination = profileroot.resolve(path).normalize()
    if (!destination.startsWith(profileroot))
      RAISE.invalidArgumentFault(s"Media publication path escapes profile root: $value")
    destination
  }

  private def _effective_profiles(
    descriptorroot: Path,
    descriptor: Descriptor,
    context: CozyProjectContext.Context
  ): Map[String, EffectiveProfile] =
    (descriptor.profiles.keySet ++ context.publicationProfiles.map(_.id)).toVector.sorted.map { id =>
      id -> _effective_profile(descriptorroot, descriptor.profiles.get(id), context.publicationProfile(id), id)
    }.toMap

  private def _effective_profile(
    descriptorroot: Path,
    descriptorprofile: Option[Profile],
    configuration: Option[CozyProjectContext.PublicationProfile],
    id: String
  ): EffectiveProfile = {
    val profile = descriptorprofile.filter(_ != null)
    if (descriptorprofile.contains(null))
      RAISE.invalidArgumentFault(s"Media profile must be defined: $id")
    val external = profile.flatMap(_.rootEnv)
    val hasdescriptorlocation = external.nonEmpty || profile.flatMap(_.root).nonEmpty
    val root = if (hasdescriptorlocation) None else configuration.map(_.resolvedRoot).orElse(Some(descriptorroot))
    EffectiveProfile(id, profile, configuration, descriptorroot, root, external.nonEmpty)
  }

  private def _context_lines(plan: Plan): Vector[String] = {
    val profile = plan.effectiveProfile.toVector.flatMap { value =>
      Vector(s"profile: ${value.id}", s"profileRoot: ${value.resolvedRoot}") ++ value.layer.map(x => s"profileLayer: $x").toVector ++
        value.sourcePath.map(x => s"profileSource: $x").toVector ++ value.siteKind.map(x => s"profileSiteKind: $x").toVector
    }
    plan.context.project match {
      case Some(project) =>
        Vector(
          s"projectRoot: ${project.root.path}",
          s"projectMarker: ${project.marker.path}"
        ) ++ profile
      case None => Vector("project: legacy/standalone") ++ profile
    }
  }

  private def _missing_publication_profile(
    descriptor: Descriptor,
    descriptorfile: Path,
    context: CozyProjectContext.Context,
    id: String,
    prefix: String
  ): Nothing = {
    val contextlayers = context.layers.map(layer => layer.name -> layer).toMap
    val layers = _publication_layer_names.map { name =>
      contextlayers.get(name).map { layer =>
        val root = layer.root.map(_.path.toString).getOrElse("<absent>")
        val files = layer.files.map(_.path.toString).distinct.sorted
        val profiles = _layer_publication_profile_ids(layer).map { profileid =>
          val source = context.publicationProfile(profileid).filter(_.layer == layer.name).map(_.sourcePath.toString)
          source.map(path => s"$profileid@$path").getOrElse(profileid)
        }
        val profiletext = if (profiles.nonEmpty) profiles.mkString(",") else "none"
        val sourcetext = if (files.nonEmpty) files.mkString(",") else "none"
        s"  ${layer.name}: root=$root; profiles=$profiletext; sources=$sourcetext"
      }.getOrElse(s"  $name: root=<absent>; profiles=none; sources=none")
    }
    val descriptorprofiles = descriptor.profiles.keys.toVector.sorted.map(profileid => s"$profileid@$descriptorfile")
    val descriptortext = if (descriptorprofiles.nonEmpty) descriptorprofiles.mkString(",") else "none"
    val message = (Vector(
      prefix,
      s"requested publication profile: $id",
      "searched publication profile layers:"
    ) ++ layers :+ s"  descriptor-only: source=$descriptorfile; profiles=$descriptortext").mkString("\n")
    RAISE.invalidArgumentFault(message)
  }

  private def _layer_publication_profile_ids(layer: CozyProjectContext.Layer): Vector[String] =
    layer.config.json.toVector.flatMap { json =>
      json.hcursor.downField("media").downField("publication-profiles").focus.toVector.flatMap { value =>
        value.asObject.toVector.flatMap(_.keys.toVector)
      }
    }.distinct.sorted

  private def _profile_selection_failure_prefix(descriptor: Descriptor, id: String): String =
    if (descriptor.articleMedia.exists(_.publicationProfile == id))
      s"Article-media site binding requires configured publication profile: $id"
    else
      s"Undefined media profile: $id"

  private def _prepare_publications(
    plan: Plan,
    candidates: Vector[ResolvedResource],
    profile: String,
    target: Option[String],
    force: Boolean
  ): Vector[PreparedPublication] =
    CozyMediaPublicationTransaction.prepare(plan, candidates, profile, target, force)

  private def _prepare_legacy_publications(
    plan: Plan,
    candidates: Vector[ResolvedResource],
    profile: String,
    target: Option[String],
    force: Boolean
  ): Vector[PreparedPublication] =
    CozyMediaPublicationTransaction.prepareLegacy(plan, candidates, profile, target, force)

  private def _create_and_bind_legacy_profile_root(plan: Plan, profile: String): Path =
    CozyMediaPublicationTransaction.createAndBindLegacyProfileRoot(plan, profile)

  private def _selected(plan: Plan, target: Option[String]): Vector[ResolvedResource] =
    target match {
      case Some(id) =>
        plan.resources.filter(_.resource.id == id) match {
          case xs if xs.nonEmpty => xs
          case _ => RAISE.invalidArgumentFault(s"Unknown media target: $id")
        }
      case None => plan.resources
    }

  private def _presentation_selected(plan: Plan, target: Option[String]): Vector[ResolvedResource] = {
    val selected = _selected(plan, target)
    if (target.isDefined && selected.exists(_.resource.build != "presentation"))
      RAISE.invalidArgumentFault(s"Media slide target is not a presentation resource: ${target.get}")
    val presentations = selected.filter(_.resource.build == "presentation")
    if (presentations.isEmpty)
      RAISE.invalidArgumentFault("Media slide command requires at least one presentation resource")
    presentations
  }

  private def _slide_validate(config: CommandConfig): String = {
    val mediaplan = _plan(config)
    val selected = _presentation_selected(mediaplan, config.target)
    selected.foreach(resolved => CozyMediaSlideIr.validate(mediaplan, resolved, requireAssets = false))
    s"Cozy Media Slide Validate\nstatus: valid\nresources: ${selected.size}"
  }

  private def _slide_plan(config: CommandConfig): String = {
    val mediaplan = _plan(config)
    val selected = _presentation_selected(mediaplan, config.target)
    (Vector("Cozy Media Slide Plan") ++ selected.map(resolved => s"  - ${resolved.resource.id}: ${resolved.action.label}")).mkString("\n")
  }

  private def _slide_build(config: CommandConfig, runner: ProcessRunner): String = {
    val mediaplan = _plan(config)
    val selected = _presentation_selected(mediaplan, config.target)
    if (config.dryRun)
      return (Vector("Cozy Media Slide Build") ++ selected.map(resolved => s"  - ${resolved.resource.id}: ${resolved.action.label} (dry-run)")).mkString("\n")
    val before = CozyMediaReceipt.capture(mediaplan)
    val results = selected.map { resolved =>
      CozyMediaPresentation.requireDependenciesCurrent(mediaplan, resolved)
      CozyMediaPresentation.build(mediaplan, resolved, runner)
    }
    val after = CozyMediaReceipt.capture(mediaplan)
    if (before.inputSetSha256 != after.inputSetSha256)
      RAISE.invalidArgumentFault("Media receipt inputs changed during presentation build; no fresh acceptance evidence was written")
    val reviewstates = selected.map(resolved => CozyMediaReviewState.prepareRefresh(mediaplan, resolved))
    val receipt = CozyMediaReceipt.prepare(mediaplan, selected, after, config.target)
    CozyMediaReceipt.commit(reviewstates, receipt)
    (Vector("Cozy Media Slide Build") ++ results.map(value => s"  - $value")).mkString("\n")
  }

  private def _slide_verify(config: CommandConfig): String = {
    val mediaplan = _plan(config)
    val selected = _presentation_selected(mediaplan, config.target)
    selected.foreach(resolved => CozyMediaPresentation.requireCurrent(mediaplan, resolved, requireReviewState = true))
    s"Cozy Media Slide Verify\nstatus: valid\nresources: ${selected.size}"
  }

  private def _build_resource(plan: Plan, resolved: ResolvedResource, runner: ProcessRunner): String =
    resolved.action match {
      case Action.MissingSource =>
        RAISE.invalidArgumentFault(s"Missing media source for ${resolved.resource.id}")
      case Action.Current =>
        s"${resolved.resource.id}: current"
      case Action.AdoptPrebuilt =>
        CozyMediaReceipt.prebuiltAcceptanceAllowed(plan, resolved)
        s"${resolved.resource.id}: adopted prebuilt ${resolved.source.get}"
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
          case "presentation" =>
            return CozyMediaPresentation.build(plan, resolved, runner)
          case other =>
            RAISE.invalidArgumentFault(s"Unsupported executable media build kind: $other")
        }
        s"${resolved.resource.id}: built $output"
      case Action.DelegateVideo =>
        val project = resolved.project.get
        val result = CozyVideo.build(CozyVideo.BuildConfig(project, dryRun = false, checkTools = false), CozyVideo.VideoToolRegistry.default)
        s"${resolved.resource.id}: delegated to cozy video build ($result)"
    }

  private def _verify_plan(plan: Plan, resources: Vector[ResolvedResource], profile: Option[String], requirereceipt: Boolean): Vector[String] = {
    val knowledge = if (Files.isRegularFile(plan.knowledgeSource)) Vector.empty else Vector(s"missing knowledge source: ${plan.knowledgeSource}")
    knowledge ++ resources.flatMap { resolved =>
      val resource = resolved.resource
      val sourcefindings =
        if (resource.build == "video-project") {
          resolved.project match {
            case Some(path) if _is_direct_regular_file(path) =>
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
      val receiptfindings =
        if (requirereceipt && !CozyMediaReceipt.current(plan, resolved)) Vector(s"${resource.id}: missing or stale cozy.media.receipt.v2 evidence")
        else Vector.empty
      val presentationfindings =
        if (resource.build == "presentation") try {
          if (requirereceipt)
            CozyMediaPresentation.requireCurrent(plan, resolved, requireReviewState = true)
          else
            CozyMediaPresentation.verifyStructural(plan, resolved)
          Vector.empty
        } catch { case NonFatal(e) => Vector(s"${resource.id}: presentation verification failed: ${e.getMessage}") }
        else Vector.empty
      sourcefindings ++ outputfindings ++ publicationfindings ++ receiptfindings ++ presentationfindings
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

  private def _is_direct_regular_file(path: Path): Boolean = path != null && !Files.isSymbolicLink(path) && Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)

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
