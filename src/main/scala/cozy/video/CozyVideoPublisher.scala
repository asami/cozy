package cozy.video

import org.goldenport.RAISE
import org.goldenport.config.StructuredDocumentLoader
import org.goldenport.io.InputSource
import org.goldenport.cli.spec
import cozy.publication.CozyPublicationCompiler
import cozy.publication.CozyArticleMediaNormalization
import cozy.runtime.CozyCliArgs
import io.circe.{Decoder, HCursor, Json => CJson}
import org.smartdox.metadata.PublishMetadata.VideoStatus
import play.api.libs.json.{Json => PJson, JsArray, JsNull, JsNumber, JsObject, JsString, JsValue}
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.charset.StandardCharsets
import java.nio.file.{AtomicMoveNotSupportedException, Files, LinkOption, Path, Paths, StandardCopyOption, StandardOpenOption}
import java.security.MessageDigest
import scala.collection.JavaConverters._
import scala.collection.mutable.ArrayBuffer
import scala.util.control.NonFatal

/*
 * @since   Jun. 19, 2026
 *  version Jul. 20, 2026
 * @version Aug. 19, 2026
 * @author  ASAMI, Tomoharu
 */
private[cozy] object CozyVideoPublisher {
  private val _schema = "cozy.publish-project.v1"
  private val _slug_pattern = "^[a-z0-9][a-z0-9-]*$".r
  private val _descriptor_names = Vector("video.yaml", "video.yml", "video.json")

  final case class PublishVideoConfig(
    packageDir: Path,
    saveDir: Path,
    warehouseDir: Path,
    version: Option[String],
    force: Boolean,
    repositoryDir: Option[Path] = None
  )
  object PublishVideoConfig {
    def create(args: List[String]): PublishVideoConfig = {
      val parsed = CozyCliArgs.parseStrict(_p_video_package, _p_save, _p_warehouse, _p_version, _p_force)(_normalize_property_args(args))
      val packagedir = parsed.argument("video-package-dir").map(CozyCliArgs.toPath).getOrElse(
        RAISE.invalidArgumentFault("Missing video package directory for publish-video")
      )
      PublishVideoConfig(
        packagedir,
        parsed.requiredPathProperty("save"),
        parsed.requiredPathProperty("warehouse"),
        parsed.property("version"),
        parsed.flag("force")
      )
    }
  }

  final case class VideoDescriptor(
    video: Option[VideoDescriptorVideo],
    title: Option[String],
    version: Option[String],
    article: Option[String],
    script: Option[String],
    renderer: Option[CozyVideo.VideoRenderer],
    toolMode: Option[String],
    publish: Option[VideoDescriptorPublish],
    profile: Option[String],
    visualEffects: Option[CozyVideoEffects.Settings],
    assets: Option[CozyVideoAssets.Settings],
    locale: Option[String],
    credits: Option[CozyVideoCredits.Settings],
    parts: Vector[CozyVideo.VideoPart]
  )
  object VideoDescriptor {
    implicit val decoder: Decoder[VideoDescriptor] = (c: HCursor) =>
      for {
        video <- c.downField("video").as[Option[VideoDescriptorVideo]]
        title <- c.downField("title").as[Option[String]]
        version <- c.downField("version").as[Option[String]]
        article <- c.downField("article").as[Option[String]]
        script <- c.downField("script").as[Option[String]]
        renderer <- _renderer(c)
        toolmode <- c.downField("toolMode").as[Option[String]].flatMap {
          case Some(s) => Right(Some(s))
          case None => c.downField("tool-mode").as[Option[String]]
        }
        publish <- c.downField("publish").as[Option[VideoDescriptorPublish]]
        profile <- c.downField("profile").as[Option[String]]
        visualeffects <- c.downField("visualEffects").as[Option[CozyVideoEffects.Settings]].flatMap {
          case value @ Some(_) => Right(value)
          case None => c.downField("visual-effects").as[Option[CozyVideoEffects.Settings]]
        }
        assets <- c.downField("assets").as[Option[CozyVideoAssets.Settings]]
        locale <- c.downField("locale").as[Option[String]]
        credits <- c.downField("credits").as[Option[CozyVideoCredits.Settings]]
        parts <- c.downField("parts").as[Option[Vector[CozyVideo.VideoPart]]]
      } yield VideoDescriptor(video, title, version, article, script, renderer, toolmode, publish, profile, visualeffects, assets, locale, credits, parts.getOrElse(Vector.empty))

    private def _renderer(c: HCursor): Decoder.Result[Option[CozyVideo.VideoRenderer]] =
      c.downField("renderer").focus match {
        case Some(json) if json.isNull => Right(None)
        case Some(json) if json.isString => Right(json.asString.map(_legacy_renderer))
        case Some(json) => json.as[CozyVideo.VideoRenderer](CozyVideo.VideoRenderer.decoder).map(Some(_))
        case None => Right(None)
      }

    private def _legacy_renderer(engine: String): CozyVideo.VideoRenderer =
      CozyVideo.VideoRenderer(Some(engine), None, None, None, None, None, None, None, None)
  }

  final case class VideoDescriptorVideo(name: Option[String])
  object VideoDescriptorVideo {
    implicit val decoder: Decoder[VideoDescriptorVideo] = (c: HCursor) =>
      c.downField("name").as[Option[String]].map(VideoDescriptorVideo.apply)
  }

  final case class VideoDescriptorArticleMedia(
    articleIdentity: String,
    locale: String,
    status: String
  )
  object VideoDescriptorArticleMedia {
    implicit val decoder: Decoder[VideoDescriptorArticleMedia] = (c: HCursor) =>
      for {
        fields <- c.keys.map(_.toSet).toRight(io.circe.DecodingFailure("Video publish.articleMedia must be an object", c.history))
        _ <- if (fields == Set("articleIdentity", "locale", "status")) Right(()) else Left(io.circe.DecodingFailure("Video publish.articleMedia must contain exactly articleIdentity, locale, and status", c.history))
        articleidentity <- c.downField("articleIdentity").as[String]
        locale <- c.downField("locale").as[String]
        status <- c.downField("status").as[String]
      } yield VideoDescriptorArticleMedia(articleidentity, locale, status)
  }

  final case class VideoDescriptorPublish(
    module: Option[String],
    publicPath: Option[String],
    articleMedia: Option[VideoDescriptorArticleMedia] = None
  )
  object VideoDescriptorPublish {
    implicit val decoder: Decoder[VideoDescriptorPublish] = (c: HCursor) =>
      for {
        module <- c.downField("module").as[Option[String]]
        publicpath <- c.downField("publicPath").as[Option[String]].flatMap {
          case Some(s) => Right(Some(s))
          case None => c.downField("public-path").as[Option[String]]
        }
        articlemedia <- _article_media(c)
      } yield VideoDescriptorPublish(module, publicpath, articlemedia)

    private def _article_media(c: HCursor): Decoder.Result[Option[VideoDescriptorArticleMedia]] =
      c.downField("articleMedia").focus match {
        case None => Right(None)
        case Some(value) if value.isNull => Left(io.circe.DecodingFailure("Video publish.articleMedia must be an object", c.history))
        case Some(_) => c.downField("articleMedia").as[VideoDescriptorArticleMedia].map(Some(_))
      }
  }

  final case class ArticleMediaBinding(
    articleIdentity: String,
    locale: String,
    status: VideoStatus
  )

  final case class ResolvedVideoPackage(
    packageDir: Path,
    slug: String,
    descriptorFile: Path,
    descriptor: VideoDescriptor,
    name: String,
    title: String,
    version: String,
    article: Path,
    articlePath: String,
    script: Path,
    scriptPath: String,
    renderer: String,
    toolMode: String,
    module: String,
    publicPath: String,
    assets: Vector[CozyVideoAssets.Resolved],
    articleMedia: Option[ArticleMediaBinding] = None,
    rendererConfig: Option[CozyVideo.VideoRenderer] = None
  ) {
    def workspaceRoot: Path =
      packageDir.getParent.resolve("target/cozy-video/publish").resolve(name).resolve(version).normalize()
    def warehousePath: String =
      s"repository/video/${module}/${version}/${name}-${version}.mp4"
    def repositoryPublicPath: String =
      warehousePath
  }

  final case class PublishVideoResult(
    video: ResolvedVideoPackage,
    workspaceRoot: Path,
    projectFile: Path,
    warehouseArtifact: Path,
    publicationBundle: Path,
    metadataPublication: Option[CozyPublicationCompiler.MetadataPublication] = None
  )

  final case class DestinationDisposition(
    state: String,
    forceRequired: Boolean,
    forceRequested: Boolean
  )

  /* Immutable, canonical evidence for every package-owned input that the
   * publisher copies into a workspace or interprets while assembling the
   * project.  The explicit state also makes an optional semantic asset's
   * absent/present transition observable without discovering outside the
   * resolved package root. */
  final case class ProducerSource(
    path: String,
    state: String,
    sha256: Option[String]
  )

  final case class ProducerSourceEvidence(sources: Vector[ProducerSource])

  /* The command-wide preflight value: resolution has completed, its final
   * repository destination has been examined, but neither rendering nor
   * artifact/registry mutation has begun. */
  final case class PublicationPlan(
    config: PublishVideoConfig,
    video: ResolvedVideoPackage,
    target: Path,
    destination: DestinationDisposition,
    producerSources: ProducerSourceEvidence
  )

  private[cozy] final case class PreparedPublication(
    result: PublishVideoResult,
    sourceArtifact: Path,
    sidecars: Vector[(Path, Path)],
    force: Boolean
  )

  private final case class ArtifactAdmission(
    source: Path,
    target: Path,
    sourcehash: String,
    destinationhash: Option[String],
    disposition: ArtifactDisposition
  )

  private final case class StagedArtifactAdmission(
    admission: ArtifactAdmission,
    temporary: Path
  )

  private sealed trait ArtifactDisposition
  private object ArtifactDisposition {
    case object Create extends ArtifactDisposition
    case object Reuse extends ArtifactDisposition
    case object Replace extends ArtifactDisposition
  }

  def publish(args: List[String]): Unit =
    publish(PublishVideoConfig.create(args), CozyVideo.VoicevoxClient.default, CozyVideo.VideoProcessRunner.default)

  def publish(
    config: PublishVideoConfig,
    voicevox: CozyVideo.VoicevoxClient,
    runner: CozyVideo.VideoProcessRunner
  ): PublishVideoResult = {
    val plan = planPublication(config)
    val prepared = preparePublication(renderPublication(plan, voicevox, runner))
    val result = admitPublication(prepared)
    val publication = result.metadataPublication.getOrElse(
      RAISE.invalidArgumentFault("Prepared video publication metadata plan is missing")
    )
    CozyPublicationCompiler.publishMetadata(publication, config.saveDir)
    result.copy(publicationBundle = config.saveDir.resolve(s"${result.video.name}.json"))
  }

  private[cozy] def planPublication(config: PublishVideoConfig): PublicationPlan = {
    if (config == null)
      RAISE.invalidArgumentFault("Video publication configuration must be defined")
    val video = resolve(config)
    val target = _repository_artifact_path(config, video.warehousePath)
    PublicationPlan(
      config,
      video,
      target,
      _destination_disposition(target, config.force),
      _producer_source_evidence(video)
    )
  }

  /* Re-check the resolved destination immediately before either dry-run
   * reporting or rendering.  Planning never creates a directory or hashes an
   * output that has not been rendered. */
  private[cozy] def validatePublicationPlan(plan: PublicationPlan): PublicationPlan = {
    if (plan == null || plan.config == null || plan.video == null || plan.target == null ||
      plan.destination == null || plan.producerSources == null)
      RAISE.invalidArgumentFault("Video publication plan must be defined")
    val target = _repository_artifact_path(plan.config, plan.video.warehousePath)
    if (target != plan.target.toAbsolutePath.normalize())
      RAISE.invalidArgumentFault("Video publication plan target does not match its resolved package")
    val destination = _destination_disposition(target, plan.config.force)
    if (destination.forceRequired && !destination.forceRequested)
      RAISE.invalidArgumentFault(s"Video warehouse artifact already exists: $target. Use --force to replace it.")
    val evidence = _producer_source_evidence(plan.video)
    if (evidence != plan.producerSources)
      RAISE.invalidArgumentFault(s"Video publication producer sources changed after planning: ${plan.video.packageDir}")
    plan.copy(target = target, destination = destination)
  }

  /* Rendering is deliberately separated from artifact admission.  The
   * command-wide registry transaction performs admission only after every
   * video and infographic plan has passed its non-mutating preflight. */
  private[cozy] def renderPublication(
    plan: PublicationPlan,
    voicevox: CozyVideo.VoicevoxClient,
    runner: CozyVideo.VideoProcessRunner
  ): (PublicationPlan, Path, Path) = {
    if (plan == null || plan.config == null || plan.video == null)
      RAISE.invalidArgumentFault("Video publication plan must be defined")
    if (voicevox == null || runner == null)
      RAISE.invalidArgumentFault("Video publication renderer dependencies must be defined")
    val validated = validatePublicationPlan(plan)
    val video = validated.video
    val workspace = _prepare_workspace(video)
    val projectfile = _write_video_project(video, workspace)
    _run_video_pipeline(video, projectfile, voicevox, runner)
    (validated, workspace, projectfile)
  }

  private[cozy] def preparePublication(
    rendered: (PublicationPlan, Path, Path)
  ): PreparedPublication = {
    if (rendered == null || rendered._1 == null || rendered._2 == null || rendered._3 == null)
      RAISE.invalidArgumentFault("Rendered video publication must be defined")
    val plan = rendered._1
    val video = plan.video
    val source = rendered._2.resolve("build/final.mp4").normalize()
    if (!Files.isRegularFile(source))
      RAISE.invalidArgumentFault(s"Video build did not create final output: $source")
    val target = validatePublicationPlan(plan).target
    val sidecars = _sidecars(video, rendered._2, target)
    val result = _prepare_metadata(plan.config, video, rendered._2, rendered._3, target, source, sidecars)
    PreparedPublication(result, source, sidecars, plan.config.force)
  }

  private[cozy] def admitPublication(prepared: PreparedPublication): PublishVideoResult =
    admitPublication(prepared, () => ())

  private[cozy] def admitPublication(
    prepared: PreparedPublication,
    beforeFirstInstall: () => Unit
  ): PublishVideoResult = {
    if (prepared == null || prepared.result == null || prepared.sourceArtifact == null || prepared.result.warehouseArtifact == null)
      RAISE.invalidArgumentFault("Prepared video artifact admission must be defined")
    if (beforeFirstInstall == null)
      RAISE.invalidArgumentFault("Video publication beforeFirstInstall callback must not be null")
    val admissions = _artifact_admissions(prepared)
    val temporaries = ArrayBuffer.empty[Path]
    try {
      val staged = _stage_artifact_admissions(admissions, temporaries)
      beforeFirstInstall()
      _install_artifact_admissions(staged, temporaries)
      prepared.result
    } catch {
      case e: AtomicMoveNotSupportedException =>
        RAISE.invalidArgumentFault(s"Video publication requires atomic move: ${e.getMessage}")
      case e: java.io.IOException =>
        RAISE.invalidArgumentFault(s"Video publication artifact staging or installation failed: ${e.getMessage}")
      case e: UnsupportedOperationException =>
        RAISE.invalidArgumentFault(s"Video publication artifact installation is unsupported: ${e.getMessage}")
    } finally {
      temporaries.foreach { path =>
        try Files.deleteIfExists(path)
        catch {
          case NonFatal(_) => ()
        }
      }
    }
  }

  @deprecated("Use planPublication, renderPublication, preparePublication, and admitPublication", "0.1.0")
  private[cozy] def preparePublication(
    config: PublishVideoConfig,
    voicevox: CozyVideo.VoicevoxClient,
    runner: CozyVideo.VideoProcessRunner
  ): PublishVideoResult = {
    admitPublication(preparePublication(renderPublication(planPublication(config), voicevox, runner)))
  }

  def resolve(config: PublishVideoConfig): ResolvedVideoPackage = {
    val packagedir = config.packageDir.toAbsolutePath.normalize()
    _validate_package_dir(packagedir)
    val descriptorfile = _descriptor_file(packagedir)
    val descriptor = StructuredDocumentLoader.loadDocument[VideoDescriptor](InputSource(descriptorfile.toFile)).take
    val slug = packagedir.getFileName.toString.stripSuffix(".video")
    val name = _validate_slug(descriptor.video.flatMap(_.name).getOrElse(slug), "video.name")
    val title = descriptor.title.map(_.trim).filter(_.nonEmpty).getOrElse(name)
    val version = _validate_version(config.version.orElse(descriptor.version).map(_.trim).filter(_.nonEmpty).getOrElse("0.0.0-SNAPSHOT"))
    val articlepath = _relative_path(descriptor.article.getOrElse("index.dox"), "article")
    val scriptpath = _relative_path(
      descriptor.script.orElse(descriptor.parts.flatMap(_.script).headOption).getOrElse("script.json"),
      "script"
    )
    val article = packagedir.resolve(articlepath).normalize()
    val script = packagedir.resolve(scriptpath).normalize()
    if (!Files.isRegularFile(article))
      RAISE.invalidArgumentFault(s"Missing video article source: $article")
    if (!Files.isRegularFile(script))
      RAISE.invalidArgumentFault(s"Missing video script source: $script")
    val rendererconfig = descriptor.renderer.getOrElse(
      CozyVideo.VideoRenderer(Some("simple-java2d"), None, None, None, None, None, None, None, None)
    )
    val renderer = rendererconfig.engine.map(_.trim).filter(_.nonEmpty).getOrElse("simple-java2d")
    if (renderer != "remotion" && renderer != "simple-java2d")
      RAISE.invalidArgumentFault(s"Unsupported video renderer in descriptor: $renderer")
    val toolmode = descriptor.toolMode.map(_.trim).filter(_.nonEmpty).getOrElse("docker")
    CozyVideo.VideoToolMode.parse(toolmode)
    val publish = descriptor.publish.getOrElse(VideoDescriptorPublish(None, None))
    val module = _validate_slug(publish.module.getOrElse(name), "publish.module")
    val publicpath = _validate_public_path(publish.publicPath.getOrElse(s"videos/${name}.mp4"))
    val articlemedia = _article_media_binding(publish.articleMedia)
    ResolvedVideoPackage(
      packagedir,
      slug,
      descriptorfile,
      descriptor,
      name,
      title,
      version,
      article,
      articlepath,
      script,
      scriptpath,
      renderer,
      toolmode,
      module,
      publicpath,
      CozyVideoAssets.resolve(packagedir, descriptor.assets),
      articlemedia,
      Some(rendererconfig.copy(engine = Some(renderer)))
    )
  }

  private def _article_media_binding(value: Option[VideoDescriptorArticleMedia]): Option[ArticleMediaBinding] =
    value.map { raw =>
      if (raw == null)
        RAISE.invalidArgumentFault("Video publish.articleMedia must be defined")
      val identity = CozyArticleMediaNormalization.normalizeArticleIdentity(raw.articleIdentity)
      val locale = CozyArticleMediaNormalization.normalizeLocale(raw.locale)
      val status = raw.status match {
        case "draft" => VideoStatus.Draft
        case "published" => VideoStatus.Published
        case "withdrawn" => VideoStatus.Withdrawn
        case _ => RAISE.invalidArgumentFault(s"Video publish.articleMedia.status must be exactly draft, published, or withdrawn: ${raw.status}")
      }
      ArticleMediaBinding(identity, locale, status)
    }

  private def _producer_source_evidence(video: ResolvedVideoPackage): ProducerSourceEvidence = {
    val packagedir = video.packageDir.toAbsolutePath.normalize()
    val root = _package_source_root(packagedir)
    val primary = Vector(
      _producer_source_file(root, _package_relative_producer_path(packagedir, root, video.descriptorFile, "descriptor"), "descriptor"),
      _producer_source_file(root, video.articlePath, "article")
    )
    val scripts = (Vector(video.scriptPath) ++ video.descriptor.parts.flatMap { part =>
      part.script.toVector ++ part.steps.toVector
    }).distinct.sorted.map(_producer_source_file(root, _, "video script or steps"))
    val trees = Vector("assets", "conf/cozy", ".cozy").flatMap(_producer_source_tree(root, _))
    val resolved = video.assets.map(_resolved_producer_source(packagedir, root, _))
    val semantic = video.descriptor.assets.toVector.flatMap(_.semantic.toVector.sortBy(_._1)).map { case (id, entry) =>
      _semantic_producer_source(root, id, entry)
    }
    ProducerSourceEvidence(_unique_producer_sources(primary ++ scripts ++ trees ++ resolved ++ semantic))
  }

  private def _package_source_root(packagedir: Path): Path = {
    val root = Option(packagedir).map(_.toAbsolutePath.normalize()).getOrElse(
      RAISE.invalidArgumentFault("Video package root must be defined")
    )
    if (Files.isSymbolicLink(root) || !Files.isDirectory(root, LinkOption.NOFOLLOW_LINKS))
      RAISE.invalidArgumentFault(s"Video package root must be a direct non-symlink directory: $root")
    root.toRealPath()
  }

  private def _package_relative_producer_path(
    packagedir: Path,
    root: Path,
    source: Path,
    label: String
  ): String = {
    val lexicalroot = Option(packagedir).map(_.toAbsolutePath.normalize()).getOrElse(
      RAISE.invalidArgumentFault("Video lexical package root must be defined")
    )
    val realroot = Option(root).map(_.toAbsolutePath.normalize()).getOrElse(
      RAISE.invalidArgumentFault("Video real package root must be defined")
    )
    val candidate = Option(source).map(_.toAbsolutePath.normalize()).getOrElse(
      RAISE.invalidArgumentFault(s"Video $label producer source must be defined")
    )
    val relative =
      if (candidate.startsWith(lexicalroot))
        lexicalroot.relativize(candidate)
      else if (Files.exists(candidate, LinkOption.NOFOLLOW_LINKS)) {
        if (Files.isSymbolicLink(candidate))
          RAISE.invalidArgumentFault(s"Video $label producer source must not be a symbolic link: $candidate")
        val resolved = candidate.toRealPath()
        if (!resolved.startsWith(realroot))
          RAISE.invalidArgumentFault(s"Video $label producer source escapes its package: $candidate")
        realroot.relativize(resolved)
      } else
        RAISE.invalidArgumentFault(s"Video $label producer source escapes its package: $candidate")
    _relative_path(relative.toString, s"$label producer source")
  }

  private def _producer_source_file(root: Path, value: String, label: String): ProducerSource = {
    val relative = _relative_path(value, label)
    val candidate = root.resolve(relative).normalize()
    if (!candidate.startsWith(root))
      RAISE.invalidArgumentFault(s"Video $label source escapes its package: $value")
    _validate_direct_source_path(root, candidate, label)
    if (!Files.isRegularFile(candidate, LinkOption.NOFOLLOW_LINKS))
      RAISE.invalidArgumentFault(s"Video $label source must be a direct regular file: $candidate")
    val resolved = candidate.toRealPath()
    if (!resolved.startsWith(root))
      RAISE.invalidArgumentFault(s"Video $label source real path escapes its package: $value")
    ProducerSource(root.relativize(candidate).toString.replace('\\', '/'), "file", Some(_sha256(candidate)))
  }

  private def _semantic_producer_source(root: Path, id: String, entry: CozyVideoAssets.Entry): ProducerSource = {
    val relative = _relative_path(entry.path, s"semantic video asset $id")
    val candidate = root.resolve(relative).normalize()
    if (!candidate.startsWith(root))
      RAISE.invalidArgumentFault(s"Semantic video asset escapes its package: $id=${entry.path}")
    if (Files.exists(candidate, LinkOption.NOFOLLOW_LINKS))
      _producer_source_file(root, relative, s"semantic video asset $id")
    else if (entry.required)
      RAISE.invalidArgumentFault(s"Missing semantic video asset: $candidate")
    else
      ProducerSource(relative, "optional-missing", None)
  }

  private def _resolved_producer_source(packagedir: Path, root: Path, asset: CozyVideoAssets.Resolved): ProducerSource = {
    val candidate = asset.path.toAbsolutePath.normalize()
    val relative = _package_relative_producer_path(packagedir, root, candidate, s"resolved publication asset ${asset.role.key}")
    if (Files.exists(candidate, LinkOption.NOFOLLOW_LINKS))
      _producer_source_file(root, relative, s"resolved publication asset ${asset.role.key}")
    else if (asset.required)
      RAISE.invalidArgumentFault(s"Missing resolved video publication asset: $candidate")
    else
      ProducerSource(relative.replace('\\', '/'), "optional-missing", None)
  }

  private def _producer_source_tree(root: Path, relative: String): Vector[ProducerSource] = {
    val directory = root.resolve(relative).normalize()
    if (!directory.startsWith(root))
      RAISE.invalidArgumentFault(s"Video producer source tree escapes its package: $relative")
    if (!Files.exists(directory, LinkOption.NOFOLLOW_LINKS))
      Vector(ProducerSource(relative, "absent", None))
    else if (Files.isSymbolicLink(directory))
      RAISE.invalidArgumentFault(s"Video producer source tree must not be a symbolic link: $directory")
    else if (!Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS))
      Vector(ProducerSource(relative, "not-directory", None))
    else {
      val stream = Files.walk(directory)
      try stream.iterator().asScala.toVector.sortBy(_.toString).map { path =>
        val normalized = path.toAbsolutePath.normalize()
        if (Files.isSymbolicLink(normalized))
          RAISE.invalidArgumentFault(s"Video producer source tree must not contain symbolic links: $normalized")
        if (Files.isDirectory(normalized, LinkOption.NOFOLLOW_LINKS))
          ProducerSource(root.relativize(normalized).toString.replace('\\', '/'), "directory", None)
        else if (Files.isRegularFile(normalized, LinkOption.NOFOLLOW_LINKS))
          _producer_source_file(root, root.relativize(normalized).toString, "producer source tree")
        else
          RAISE.invalidArgumentFault(s"Video producer source tree must contain only direct files and directories: $normalized")
      }
      finally stream.close()
    }
  }

  private def _validate_direct_source_path(root: Path, candidate: Path, label: String): Unit = {
    val relative = root.relativize(candidate)
    var parent = root
    (0 until relative.getNameCount - 1).foreach { index =>
      parent = parent.resolve(relative.getName(index))
      if (Files.isSymbolicLink(parent) || !Files.isDirectory(parent, LinkOption.NOFOLLOW_LINKS))
        RAISE.invalidArgumentFault(s"Video $label source parent must be a direct non-symlink directory: $parent")
    }
    if (Files.isSymbolicLink(candidate))
      RAISE.invalidArgumentFault(s"Video $label source must not be a symbolic link: $candidate")
  }

  private def _unique_producer_sources(values: Vector[ProducerSource]): Vector[ProducerSource] = {
    val grouped = values.groupBy(_.path).toVector.sortBy(_._1)
    grouped.map { case (path, sources) =>
      val distinct = sources.distinct
      if (distinct.size != 1)
        RAISE.invalidArgumentFault(s"Video producer source identity is ambiguous: $path")
      distinct.head
    }
  }

  private def _run_video_pipeline(
    video: ResolvedVideoPackage,
    projectfile: Path,
    voicevox: CozyVideo.VoicevoxClient,
    runner: CozyVideo.VideoProcessRunner
  ): Unit = {
    val scripts =
      if (video.descriptor.parts.nonEmpty)
        video.descriptor.parts.zipWithIndex.flatMap { case (part, index) =>
          part.script.map(x => part.displayId(index + 1) -> _relative_path(x, "part script"))
        }
      else
        Vector("main" -> video.scriptPath)
    scripts.foreach { case (id, scriptpath) =>
      val script = video.workspaceRoot.resolve("source").resolve(scriptpath).normalize()
      val audiodir = video.workspaceRoot.resolve("build/audio").resolve(id).normalize()
      CozyVideo.synthesize(CozyVideo.SynthesizeConfig(script, audiodir), voicevox)
    }
    CozyVideo.render(
      CozyVideo.RenderConfig(projectfile, video.renderer, checkTools = false, toolMode = Some(video.toolMode)),
      CozyVideo.VideoToolRegistry(Vector.empty),
      runner
    )
    CozyVideo.build(
      CozyVideo.BuildConfig(projectfile, dryRun = false, checkTools = false, toolMode = Some(video.toolMode)),
      CozyVideo.VideoToolRegistry(Vector.empty),
      runner
    )
    CozyVideo.rdf(CozyVideo.RdfConfig(projectfile, video.workspaceRoot.resolve("rdf"), toolMode = Some(video.toolMode)))
  }

  private def _prepare_workspace(video: ResolvedVideoPackage): Path = {
    val workspace = video.workspaceRoot
    _delete_directory(workspace)
    Files.createDirectories(workspace.resolve("source"))
    _copy(video.article, workspace.resolve("source").resolve(video.articlePath))
    _copy(video.descriptorFile, workspace.resolve("source").resolve(video.descriptorFile.getFileName.toString))
    val sourcepaths = (Vector(video.scriptPath) ++ video.descriptor.parts.flatMap(part => part.script.toVector ++ part.steps.toVector)).distinct
    sourcepaths.foreach { value =>
      val relative = _relative_path(value, "video part source")
      val source = video.packageDir.resolve(relative).normalize()
      if (!Files.isRegularFile(source))
        RAISE.invalidArgumentFault(s"Missing video part source: $source")
      _copy(source, workspace.resolve("source").resolve(relative))
    }
    val assets = video.packageDir.resolve("assets")
    if (Files.isDirectory(assets))
      _copy_directory(assets, workspace.resolve("source/assets"))
    val projectconfig = video.packageDir.resolve("conf/cozy")
    if (Files.isDirectory(projectconfig))
      _copy_directory(projectconfig, workspace.resolve("conf/cozy"))
    val localconfig = video.packageDir.resolve(".cozy")
    if (Files.isDirectory(localconfig))
      _copy_directory(localconfig, workspace.resolve(".cozy"))
    video.assets.foreach { asset =>
      if (Files.isRegularFile(asset.path))
        _copy(asset.path, _publication_asset_path(workspace, asset))
    }
    video.descriptor.assets.toVector.flatMap(_.semantic.values).foreach { entry =>
      val relative = _relative_path(entry.path, "semantic video asset")
      val source = video.packageDir.resolve(relative).normalize()
      if (Files.isRegularFile(source))
        _copy(source, workspace.resolve("source").resolve(relative))
      else if (entry.required)
        RAISE.invalidArgumentFault(s"Missing semantic video asset: $source")
    }
    workspace
  }

  private def _write_video_project(video: ResolvedVideoPackage, workspace: Path): Path = {
    val projectfile = workspace.resolve("video_project.json")
    val parts =
      if (video.descriptor.parts.nonEmpty)
        video.descriptor.parts.zipWithIndex.map { case (part, index) =>
          val id = part.displayId(index + 1)
          JsObject(Vector(
            Some("id" -> PJson.toJson(id)),
            Some("type" -> PJson.toJson(part.displayType)),
            part.script.map(x => "script" -> PJson.toJson(s"source/${_relative_path(x, "part script")}")),
            part.steps.map(x => "steps" -> PJson.toJson(s"source/${_relative_path(x, "part steps")}")),
            Some("audioDir" -> PJson.toJson(s"build/audio/$id")),
            Some("output" -> PJson.toJson(s"build/parts/$id.mp4"))
          ).flatten)
        }
      else
        Vector(PJson.obj(
          "id" -> "main",
          "type" -> "dialogue",
          "script" -> s"source/${video.scriptPath}",
          "audioDir" -> "build/audio/main",
          "output" -> "build/parts/main.mp4"
        ))
    val base = PJson.obj(
      "name" -> video.name,
      "title" -> video.title,
      "output" -> "build/final.mp4",
      "tools" -> PJson.obj(
        "toolMode" -> video.toolMode
      ),
      "renderer" -> _renderer_json(video),
      "parts" -> JsArray(parts)
    )
    val profile = video.descriptor.profile.map(x => PJson.obj("profile" -> x)).getOrElse(PJson.obj())
    val effects = video.descriptor.visualEffects.map(x => PJson.obj("visualEffects" -> _visual_effects_json(x))).getOrElse(PJson.obj())
    val assets = video.descriptor.assets.map(_ => PJson.obj("assets" -> _assets_json(video, workspace))).getOrElse(PJson.obj())
    val locale = video.descriptor.locale.map(x => PJson.obj("locale" -> x)).getOrElse(PJson.obj())
    val credits = video.descriptor.credits.map(x => PJson.obj("credits" -> _credits_json(x))).getOrElse(PJson.obj())
    val json = base ++ profile ++ effects ++ assets ++ locale ++ credits
    Files.writeString(projectfile, PJson.prettyPrint(json) + "\n", StandardCharsets.UTF_8)
    projectfile
  }

  private def _renderer_json(video: ResolvedVideoPackage): JsObject = {
    val renderer = video.rendererConfig.getOrElse(
      CozyVideo.VideoRenderer(Some(video.renderer), None, None, None, None, None, None, None, None)
    )
    JsObject(Vector(
      Some("engine" -> PJson.toJson(video.renderer)),
      renderer.strategy.map("strategy" -> PJson.toJson(_)),
      renderer.policy.map(x => "policy" -> PJson.toJson(x.name)),
      renderer.fps.map("fps" -> PJson.toJson(_)),
      renderer.width.map("width" -> PJson.toJson(_)),
      renderer.height.map("height" -> PJson.toJson(_)),
      renderer.crf.map("crf" -> PJson.toJson(_)),
      renderer.x264Preset.map("x264Preset" -> PJson.toJson(_)),
      renderer.effectProfile.map("effectProfile" -> PJson.toJson(_))
    ).flatten)
  }

  private def _visual_effects_json(settings: CozyVideoEffects.Settings): JsObject =
    JsObject(Vector(
      settings.opening.map("opening" -> PJson.toJson(_)),
      settings.sectionStart.map("sectionStart" -> PJson.toJson(_)),
      settings.summary.map("summary" -> PJson.toJson(_)),
      settings.finalPage.map("finalPage" -> PJson.toJson(_))
    ).flatten)

  private def _assets_json(video: ResolvedVideoPackage, workspace: Path): JsObject =
    JsObject(video.assets.map { asset =>
      asset.role.key -> PJson.obj(
        "path" -> _project_relative(workspace, _publication_asset_path(workspace, asset)),
        "kind" -> asset.kind,
        "required" -> asset.required,
        "license" -> asset.license,
        "provenance" -> asset.provenance,
        "tags" -> JsArray(asset.tags.map(PJson.toJson(_))),
        "credits" -> JsArray(asset.credits.map(PJson.toJson(_))),
        "creditObligation" -> asset.creditObligation.fold[JsValue](JsNull)(PJson.toJson(_))
      )
    } ++ video.descriptor.assets.toVector.flatMap(_.semantic.toVector.sortBy(_._1).map { case (id, entry) =>
      id -> _semantic_asset_json(entry)
    }))

  private def _semantic_asset_json(entry: CozyVideoAssets.Entry): JsObject =
    JsObject(Vector(
      Some("path" -> PJson.toJson("source/" + _relative_path(entry.path, "semantic asset"))),
      entry.kind.map("kind" -> PJson.toJson(_)),
      Some("required" -> PJson.toJson(entry.required)),
      entry.license.map("license" -> PJson.toJson(_)),
      entry.provenance.map("provenance" -> PJson.toJson(_)),
      Some("tags" -> JsArray(entry.tags.map(PJson.toJson(_)))),
      Some("credits" -> JsArray(entry.credits.map(PJson.toJson(_)))),
      entry.creditObligation.map("creditObligation" -> PJson.toJson(_))
    ).flatten)

  private def _credits_json(settings: CozyVideoCredits.Settings): JsObject =
    JsObject(Vector(
      settings.profile.map("profile" -> PJson.toJson(_)),
      Some("include" -> JsArray(settings.include.map(PJson.toJson(_)))),
      Some("exclude" -> JsArray(settings.exclude.map(PJson.toJson(_)))),
      Some("presentation" -> JsObject(Vector(
        Some("enabled" -> PJson.toJson(settings.presentationEnabled))
      ).flatten))
    ).flatten)

  private def _publication_asset_path(workspace: Path, asset: CozyVideoAssets.Resolved): Path =
    workspace.resolve("source/assets").resolve(asset.role.key + _asset_extension(asset.path)).normalize()

  private def _asset_extension(path: Path): String = {
    val name = path.getFileName.toString
    val index = name.lastIndexOf('.')
    if (index >= 0 && name.substring(index).matches("\\.[A-Za-z0-9]+"))
      name.substring(index).toLowerCase
    else
      ""
  }

  private def _project_relative(root: Path, path: Path): String =
    root.toAbsolutePath.normalize().relativize(path.toAbsolutePath.normalize()).toString

  private def _artifact_admissions(prepared: PreparedPublication): Vector[ArtifactAdmission] = {
    val main = prepared.sourceArtifact.toAbsolutePath.normalize() -> prepared.result.warehouseArtifact.toAbsolutePath.normalize()
    val candidates = main +: Option(prepared.sidecars).getOrElse(
      RAISE.invalidArgumentFault("Prepared video artifact sidecars must be defined")
    ).flatMap { case (source, target) =>
      if (source == null || target == null)
        RAISE.invalidArgumentFault("Prepared video artifact sidecar paths must be defined")
      val normalizedsource = source.toAbsolutePath.normalize()
      if (Files.exists(normalizedsource, LinkOption.NOFOLLOW_LINKS))
        Some(normalizedsource -> target.toAbsolutePath.normalize())
      else
        None
    }
    if (candidates.isEmpty)
      RAISE.invalidArgumentFault("Prepared video artifact candidates must not be empty")
    val parent = Option(candidates.head._2.getParent).getOrElse(
      RAISE.invalidArgumentFault(s"Video artifact target has no parent: ${candidates.head._2}")
    )
    _validate_artifact_parent(parent)
    val targets = candidates.map(_._2)
    if (targets.distinct.size != targets.size)
      RAISE.invalidArgumentFault("Video publication artifact destinations must be unique")
    candidates.map { case (source, target) =>
      if (Option(target.getParent).forall(_ != parent))
        RAISE.invalidArgumentFault(s"Video publication artifacts must share one repository parent: $target")
      _validate_direct_artifact_source(source)
      val destinationhash = _artifact_destination_hash(target)
      val sourcehash = _sha256(source)
      val disposition = destinationhash match {
        case None => ArtifactDisposition.Create
        case Some(hash) if hash == sourcehash => ArtifactDisposition.Reuse
        case Some(_) if prepared.force => ArtifactDisposition.Replace
        case Some(_) => RAISE.invalidArgumentFault(s"Video publication artifact differs; use --force to replace: $target")
      }
      ArtifactAdmission(source, target, sourcehash, destinationhash, disposition)
    }
  }

  private def _validate_direct_artifact_source(source: Path): Unit =
    if (source == null || Files.isSymbolicLink(source) || !Files.isRegularFile(source, LinkOption.NOFOLLOW_LINKS))
      RAISE.invalidArgumentFault(s"Video publication artifact source must be a direct regular non-symlink file: $source")

  private def _artifact_destination_hash(target: Path): Option[String] = {
    if (!Files.exists(target, LinkOption.NOFOLLOW_LINKS))
      None
    else if (Files.isSymbolicLink(target) || !Files.isRegularFile(target, LinkOption.NOFOLLOW_LINKS))
      RAISE.invalidArgumentFault(s"Video publication artifact destination must be absent or a direct regular non-symlink file: $target")
    else
      Some(_sha256(target))
  }

  private def _validate_artifact_parent(parent: Path): Unit = {
    val normalized = Option(parent).map(_.toAbsolutePath.normalize()).getOrElse(
      RAISE.invalidArgumentFault("Video publication artifact parent must be defined")
    )
    var ancestor = normalized
    while (!Files.exists(ancestor, LinkOption.NOFOLLOW_LINKS)) {
      ancestor = Option(ancestor.getParent).getOrElse(
        RAISE.invalidArgumentFault(s"Video publication artifact parent has no existing ancestor: $normalized")
      )
    }
    if (Files.isSymbolicLink(ancestor) || !Files.isDirectory(ancestor, LinkOption.NOFOLLOW_LINKS))
      RAISE.invalidArgumentFault(s"Video publication artifact parent must have a direct directory ancestor: $normalized")
    val relative = ancestor.relativize(normalized)
    var current = ancestor
    (0 until relative.getNameCount).foreach { index =>
      current = current.resolve(relative.getName(index))
      if (Files.exists(current, LinkOption.NOFOLLOW_LINKS) &&
        (Files.isSymbolicLink(current) || !Files.isDirectory(current, LinkOption.NOFOLLOW_LINKS)))
        RAISE.invalidArgumentFault(s"Video publication artifact parent must be a direct directory: $normalized")
    }
  }

  private def _stage_artifact_admissions(
    admissions: Vector[ArtifactAdmission],
    temporaries: ArrayBuffer[Path]
  ): Vector[StagedArtifactAdmission] = {
    admissions.collect {
      case admission if admission.disposition != ArtifactDisposition.Reuse =>
        val parent = admission.target.getParent
        Files.createDirectories(parent)
        _validate_artifact_parent(parent)
        val temporary = Files.createTempFile(parent, s".${admission.target.getFileName}.cozy-video.", ".tmp")
        temporaries += temporary
        _copy_and_force_artifact(admission.source, temporary)
        if (_sha256(temporary) != admission.sourcehash)
          RAISE.invalidArgumentFault(s"Video temporary publication hash differs: $temporary")
        StagedArtifactAdmission(admission, temporary)
    }
  }

  private def _copy_and_force_artifact(source: Path, temporary: Path): Unit = {
    val input = FileChannel.open(source, StandardOpenOption.READ)
    val output = FileChannel.open(temporary, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING)
    try {
      val buffer = ByteBuffer.allocate(8192)
      while (input.read(buffer) >= 0) {
        buffer.flip()
        while (buffer.hasRemaining)
          output.write(buffer)
        buffer.clear()
      }
      output.force(true)
    } finally {
      try input.close()
      finally output.close()
    }
  }

  private def _install_artifact_admissions(
    staged: Vector[StagedArtifactAdmission],
    temporaries: ArrayBuffer[Path]
  ): Unit = {
    staged.foreach { artifact =>
      try {
        artifact.admission.disposition match {
          case ArtifactDisposition.Create =>
            Files.createLink(artifact.admission.target, artifact.temporary)
            Files.delete(artifact.temporary)
          case ArtifactDisposition.Replace =>
            Files.move(
              artifact.temporary,
              artifact.admission.target,
              StandardCopyOption.ATOMIC_MOVE,
              StandardCopyOption.REPLACE_EXISTING
            )
          case ArtifactDisposition.Reuse => ()
        }
        temporaries -= artifact.temporary
      } catch {
        case e: AtomicMoveNotSupportedException =>
          RAISE.invalidArgumentFault(s"Video publication requires atomic move: ${e.getMessage}")
        case e: java.io.IOException =>
          RAISE.invalidArgumentFault(s"Video publication artifact installation failed: ${e.getMessage}")
        case e: UnsupportedOperationException =>
          RAISE.invalidArgumentFault(s"Video publication hard-link installation is unsupported: ${e.getMessage}")
      }
    }
  }

  private def _sidecars(video: ResolvedVideoPackage, workspace: Path, target: Path): Vector[(Path, Path)] =
    Vector(
      workspace.resolve("build/manifest.json") -> target.resolveSibling(s"${video.name}-${video.version}.manifest.json"),
      workspace.resolve("rdf/video.ttl") -> target.resolveSibling(s"${video.name}-${video.version}.ttl"),
      workspace.resolve("rdf/video.jsonld") -> target.resolveSibling(s"${video.name}-${video.version}.jsonld"),
      workspace.resolve("rdf/manifest.json") -> target.resolveSibling(s"${video.name}-${video.version}.rdf-manifest.json"),
      workspace.resolve("build/captions.srt") -> target.resolveSibling(s"${video.name}-${video.version}.srt"),
      workspace.resolve("build/transcript.json") -> target.resolveSibling(s"${video.name}-${video.version}.transcript.json")
    )

  private def _repository_artifact_path(config: PublishVideoConfig, warehousepath: String): Path = {
    if (config == null || config.warehouseDir == null || warehousepath == null)
      RAISE.invalidArgumentFault("Video warehouse artifact configuration must be defined")
    val (root, relative) = config.repositoryDir match {
      case Some(repositorydir) if warehousepath.startsWith("repository/") =>
        Option(repositorydir).getOrElse(RAISE.invalidArgumentFault("Video repository directory must be defined")) -> warehousepath.stripPrefix("repository/")
      case Some(repositorydir) if warehousepath == "repository" =>
        Option(repositorydir).getOrElse(RAISE.invalidArgumentFault("Video repository directory must be defined")) -> ""
      case _ => config.warehouseDir -> warehousepath
    }
    val normalizedroot = root.toAbsolutePath.normalize()
    val normalizedrelative = _safe_repository_relative_path(relative)
    val target = normalizedroot.resolve(normalizedrelative).normalize()
    if (!target.startsWith(normalizedroot) || target == normalizedroot)
      RAISE.invalidArgumentFault(s"Video warehouse artifact target escapes the configured repository: $warehousepath")
    _validate_existing_target_path(normalizedroot, target)
    target
  }

  private def _destination_disposition(target: Path, force: Boolean): DestinationDisposition = {
    if (target == null)
      RAISE.invalidArgumentFault("Video warehouse artifact target must be defined")
    if (!Files.exists(target, LinkOption.NOFOLLOW_LINKS))
      DestinationDisposition("missing", forceRequired = false, forceRequested = force)
    else if (Files.isSymbolicLink(target))
      RAISE.invalidArgumentFault(s"Video warehouse artifact target must not be a symbolic link: $target")
    else if (!Files.isRegularFile(target, LinkOption.NOFOLLOW_LINKS))
      RAISE.invalidArgumentFault(s"Video warehouse artifact target must be a direct regular file when it exists: $target")
    else
      DestinationDisposition("existing-regular-file", forceRequired = true, forceRequested = force)
  }

  private def _safe_repository_relative_path(value: String): Path = {
    val path = Option(value).getOrElse("")
    if (path.isEmpty || path.startsWith("/") || path.startsWith("\\") || path.contains('\\') || path.contains('\u0000'))
      RAISE.invalidArgumentFault(s"Video warehouse artifact path must be a safe relative path: $value")
    val normalized = Paths.get(path).normalize()
    if (normalized.isAbsolute || normalized.toString.isEmpty || normalized.iterator().asScala.exists(x => x.toString == ".." || x.toString == "."))
      RAISE.invalidArgumentFault(s"Video warehouse artifact path must be a safe normalized relative path: $value")
    normalized
  }

  private def _validate_existing_target_path(root: Path, target: Path): Unit = {
    _validate_existing_repository_root(root)
    val relative = root.relativize(target)
    var current = root
    (0 until relative.getNameCount - 1).foreach { index =>
      current = current.resolve(relative.getName(index))
      if (Files.exists(current, LinkOption.NOFOLLOW_LINKS) &&
        (Files.isSymbolicLink(current) || !Files.isDirectory(current, LinkOption.NOFOLLOW_LINKS)))
        RAISE.invalidArgumentFault(s"Video warehouse artifact parent must be a direct directory: $target")
    }
  }

  private def _validate_existing_repository_root(root: Path): Unit = {
    var ancestor = root
    while (!Files.exists(ancestor, LinkOption.NOFOLLOW_LINKS)) {
      ancestor = Option(ancestor.getParent).getOrElse(
        RAISE.invalidArgumentFault(s"Video repository root has no existing ancestor: $root")
      )
    }
    if (Files.isSymbolicLink(ancestor) || !Files.isDirectory(ancestor, LinkOption.NOFOLLOW_LINKS))
      RAISE.invalidArgumentFault(s"Video repository root nearest existing ancestor must be a direct directory: $root")
    val relative = ancestor.relativize(root)
    var current = ancestor
    (0 until relative.getNameCount).foreach { index =>
      current = current.resolve(relative.getName(index))
      if (Files.exists(current, LinkOption.NOFOLLOW_LINKS) &&
        (Files.isSymbolicLink(current) || !Files.isDirectory(current, LinkOption.NOFOLLOW_LINKS)))
        RAISE.invalidArgumentFault(s"Video repository root must be a direct directory when it exists: $root")
    }
  }

  private def _prepare_metadata(
    config: PublishVideoConfig,
    video: ResolvedVideoPackage,
    workspace: Path,
    projectfile: Path,
    artifact: Path,
    artifactsource: Path,
    sidecars: Vector[(Path, Path)]
  ): PublishVideoResult = {
    val encoding = _publication_encoding(workspace)
    val narration = _video_narration_json(workspace)
    val videojson = _video_metadata_json(video, artifact, artifactsource, sidecars, narration, encoding)
    val artifactjson = _video_artifact_json(video, artifact, artifactsource, sidecars)
    val catalogjson = PJson.obj(
      "schema" -> _schema,
      "type" -> "catalog-video",
      "video" -> PJson.obj(
        "name" -> video.name,
        "title" -> video.title,
        "version" -> video.version,
        "metadata" -> s"metadata/videos/${video.name}/metadata",
        "articlePath" -> video.articlePath,
        "publicPath" -> video.publicPath
      )
    )
    val publication = CozyPublicationCompiler.metadataPublication(
      video.name,
      None,
      video.packageDir,
      Vector(
        s"metadata/catalog/videos/${video.name}.json" -> catalogjson,
        s"metadata/videos/${video.name}/metadata.json" -> videojson,
        s"metadata/artifacts/repository/${video.name}.json" -> artifactjson,
        s"${_video_registry_root(video)}/manifest.json" -> _video_registry_manifest_json(video, artifact, artifactsource, sidecars, narration, encoding),
        s"${_video_registry_root(video)}/rdf.json" -> _video_registry_rdf_json(video, sidecars),
        s"metadata/video/${video.name}/latest.json" -> _video_latest_json(video)
      )
    )
    PublishVideoResult(video, workspace, projectfile, artifact, config.saveDir.resolve(s"${video.name}.json"), Some(publication))
  }

  private def _publication_encoding(workspace: Path): JsObject = {
    val manifest = workspace.resolve("build/manifest.json").normalize()
    if (!Files.isRegularFile(manifest))
      RAISE.invalidArgumentFault(s"Video build manifest is missing after successful build: $manifest")
    val document = try {
      PJson.parse(Files.readString(manifest, StandardCharsets.UTF_8))
    } catch {
      case NonFatal(e) =>
        RAISE.invalidArgumentFault(s"Video build manifest is malformed: $manifest: ${e.getMessage}")
    }
    val encoding = document.asOpt[JsObject].flatMap(_.value.get("encoding")) match {
      case Some(value: JsObject) => value
      case _ => RAISE.invalidArgumentFault(s"Video build manifest encoding is missing or malformed: $manifest")
    }
    encoding.value.get("policy") match {
      case Some(JsString(value)) if Set("lightweight", "standard", "quality").contains(value) => ()
      case _ => RAISE.invalidArgumentFault(s"Video build manifest encoding.policy is missing or malformed: $manifest")
    }
    Vector("fps", "width", "height").foreach { field =>
      encoding.value.get(field) match {
        case Some(JsNumber(value)) if value.isWhole && value.isValidInt && value.toInt > 0 => ()
        case _ => RAISE.invalidArgumentFault(s"Video build manifest encoding.$field is missing or malformed: $manifest")
      }
    }
    encoding.value.get("crf") match {
      case Some(JsNumber(value)) if value.isWhole && value.isValidInt && value.toInt >= 0 => ()
      case _ => RAISE.invalidArgumentFault(s"Video build manifest encoding.crf is missing or malformed: $manifest")
    }
    encoding.value.get("x264Preset") match {
      case None | Some(JsNull) => ()
      case Some(JsString(value)) if Set("superfast", "veryfast", "faster", "fast", "medium", "slow", "slower", "veryslow", "placebo").contains(value) => ()
      case _ => RAISE.invalidArgumentFault(s"Video build manifest encoding.x264Preset is malformed: $manifest")
    }
    encoding
  }

  private def _video_metadata_json(
    video: ResolvedVideoPackage,
    artifact: Path,
    artifactsource: Path,
    sidecars: Vector[(Path, Path)],
    narration: Option[JsObject],
    encoding: JsObject
  ): JsValue = {
    val rdf = Some(_video_rdf_reference_json(video))
    val captions = _video_repository_sidecar_json(video, sidecars, "captions")
    val transcript = _video_repository_sidecar_json(video, sidecars, "transcript")
    val optional = Vector(
      rdf.map("rdf" -> _),
      captions.map("captions" -> _),
      transcript.map("transcript" -> _),
      narration.map("narration" -> _)
    ).flatten
    val base = PJson.obj(
      "schema" -> _schema,
      "type" -> "video-publication",
      "video" -> (PJson.obj(
        "type" -> "video",
        "name" -> video.name,
        "title" -> video.title,
        "version" -> video.version,
        "articlePath" -> video.articlePath,
        "sourcePackage" -> _source_package_path(video.packageDir),
        "descriptorPath" -> video.descriptorFile.getFileName.toString,
        "descriptorSha256" -> _sha256(video.descriptorFile),
        "scriptPath" -> video.scriptPath,
        "scriptSha256" -> _sha256(video.script),
        "renderer" -> video.renderer,
        "toolMode" -> video.toolMode,
        "encoding" -> encoding,
        "publish" -> PJson.obj(
          "module" -> video.module,
          "publicPath" -> video.publicPath
        ),
        "artifact" -> PJson.obj(
          "warehousePath" -> video.warehousePath,
          "publicPath" -> video.repositoryPublicPath,
          "sitePublicPath" -> video.publicPath,
          "repositoryPublicPath" -> video.repositoryPublicPath,
          "file" -> artifact.toString,
          "sha256" -> _sha256(artifactsource),
          "size" -> Files.size(artifactsource)
        )
      ) ++ JsObject(optional))
    )
    base
  }

  private def _video_registry_manifest_json(
    video: ResolvedVideoPackage,
    artifact: Path,
    artifactsource: Path,
    sidecars: Vector[(Path, Path)],
    narration: Option[JsObject],
    encoding: JsObject
  ): JsValue = {
    val base = PJson.obj(
      "schema" -> _schema,
      "type" -> "video-registry-manifest",
      "video" -> PJson.obj(
        "name" -> video.name,
        "title" -> video.title,
        "version" -> video.version,
        "metadataPath" -> s"metadata/videos/${video.name}/metadata",
        "rdfPath" -> s"${_video_registry_root(video)}/rdf",
        "latestPath" -> s"metadata/video/${video.name}/latest"
      ),
      "encoding" -> encoding,
      "artifact" -> PJson.obj(
        "warehousePath" -> video.warehousePath,
        "publicPath" -> video.repositoryPublicPath,
        "sitePublicPath" -> video.publicPath,
        "repositoryPublicPath" -> video.repositoryPublicPath,
        "size" -> Files.size(artifactsource),
        "sha256" -> _sha256(artifactsource)
      ),
      "sidecars" -> _video_sidecars_json(video, sidecars)
    )
    narration.map(x => base + ("narration" -> x)).getOrElse(base)
  }

  private def _video_narration_json(workspace: Path): Option[JsObject] = {
    val audioroot = workspace.resolve("build/audio").normalize()
    val manifests =
      if (Files.isDirectory(audioroot))
        Files.walk(audioroot).iterator().asScala.toVector.
          filter(path => Files.isRegularFile(path) && path.getFileName.toString == "manifest.json").
          sortBy(_.toString)
      else
        Vector.empty
    val entries = manifests.flatMap { path =>
      PJson.parse(Files.readString(path, StandardCharsets.UTF_8)).asOpt[JsArray].toVector.flatMap(_.value)
    }
    if (entries.isEmpty)
      None
    else {
      def _strings_(name: String): Vector[String] =
        entries.flatMap(x => (x \ name).asOpt[String]).map(_.trim).filter(_.nonEmpty).distinct.sorted

      val voices = entries.flatMap { entry =>
        val identity = (entry \ "voiceIdentity").asOpt[String]
        val id = (entry \ "voiceId").asOpt[String]
        val model = (entry \ "modelIdentity").asOpt[String]
        if (identity.isEmpty && id.isEmpty && model.isEmpty)
          None
        else
          Some(PJson.obj(
            "identity" -> identity,
            "id" -> id,
            "model" -> model
          ))
      }.distinct.sortBy(PJson.stringify)
      val formats = entries.flatMap { entry =>
        for {
          samplerate <- (entry \ "sampleRate").asOpt[Int]
          channels <- (entry \ "channels").asOpt[Int]
          bitspersample <- (entry \ "bitsPerSample").asOpt[Int]
        } yield PJson.obj(
          "sampleRate" -> samplerate,
          "channels" -> channels,
          "bitsPerSample" -> bitspersample
        )
      }.distinct.sortBy(PJson.stringify)
      Some(PJson.obj(
        "providers" -> _strings_("provider"),
        "executionModes" -> _strings_("executionMode"),
        "voices" -> voices,
        "audioFormats" -> formats,
        "manifests" -> manifests.map(path => workspace.relativize(path).toString)
      ))
    }
  }

  private def _video_registry_rdf_json(video: ResolvedVideoPackage, sidecars: Vector[(Path, Path)]): JsValue =
    PJson.obj(
      "schema" -> _schema,
      "type" -> "video-rdf",
      "video" -> PJson.obj(
        "name" -> video.name,
        "version" -> video.version
      ),
      "registryPath" -> s"${_video_registry_root(video)}/rdf",
      "files" -> _video_rdf_files_json(video, sidecars)
    )

  private def _video_latest_json(video: ResolvedVideoPackage): JsValue =
    PJson.obj(
      "schema" -> _schema,
      "type" -> "video-latest",
      "video" -> PJson.obj(
        "name" -> video.name,
        "version" -> video.version,
        "metadataPath" -> s"metadata/videos/${video.name}/metadata",
        "manifestPath" -> s"${_video_registry_root(video)}/manifest",
        "rdfPath" -> s"${_video_registry_root(video)}/rdf"
      )
    )

  private def _video_rdf_reference_json(video: ResolvedVideoPackage): JsObject =
    PJson.obj(
      "registryPath" -> s"${_video_registry_root(video)}/rdf",
      "manifestPath" -> s"${_video_registry_root(video)}/manifest",
      "latestPath" -> s"metadata/video/${video.name}/latest"
    )

  private def _video_artifact_json(
    video: ResolvedVideoPackage,
    artifact: Path,
    artifactsource: Path,
    sidecars: Vector[(Path, Path)]
  ): JsValue =
    PJson.obj(
      "schema" -> _schema,
      "type" -> "repository-artifact",
      "project" -> PJson.obj(
        "name" -> video.name,
        "title" -> video.title,
        "kind" -> "video",
        "version" -> video.version
      ),
      "artifact" -> PJson.obj(
        "layer" -> "repository",
        "status" -> "published",
        "kinds" -> PJson.arr(PJson.obj(
          "type" -> "video",
          "versions" -> PJson.arr(video.version),
          "latestRelease" -> video.version
        )),
        "files" -> JsArray(
          PJson.obj(
            "layer" -> "repository",
            "type" -> "video",
            "module" -> video.module,
            "artifactId" -> video.module,
            "version" -> video.version,
            "extension" -> "mp4",
            "warehousePath" -> video.warehousePath,
            "publicPath" -> video.repositoryPublicPath,
            "sitePublicPath" -> video.publicPath,
            "name" -> artifact.getFileName.toString,
            "expected" -> false,
            "size" -> Files.size(artifactsource),
            "sha256" -> _sha256(artifactsource)
          ) +: _video_repository_sidecar_files(video, sidecars)
        )
      )
    )

  private def _video_repository_sidecar_files(video: ResolvedVideoPackage, sidecars: Vector[(Path, Path)]): Vector[JsObject] =
    sidecars.flatMap { case (source, target) =>
      _video_repository_sidecar_json(video, source, target, _sidecar_kind(target))
    }

  private def _video_repository_sidecar_json(video: ResolvedVideoPackage, sidecars: Vector[(Path, Path)], kind: String): Option[JsObject] =
    sidecars.collectFirst { case (source, target) if _sidecar_kind(target) == kind && Files.isRegularFile(source) =>
      _video_repository_file_json(video, source, target, kind)
    }

  private def _video_repository_sidecar_json(video: ResolvedVideoPackage, source: Path, target: Path, kind: String): Option[JsObject] =
    if (Files.isRegularFile(source))
      Some(_video_repository_file_json(video, source, target, kind))
    else
      None

  private def _video_rdf_files_json(video: ResolvedVideoPackage, sidecars: Vector[(Path, Path)]): JsObject =
    JsObject(Vector(
      _video_repository_sidecar_json(video, sidecars, "turtle").map("turtle" -> _),
      _video_repository_sidecar_json(video, sidecars, "jsonld").map("jsonLd" -> _),
      _video_repository_sidecar_json(video, sidecars, "rdf-manifest").map("manifest" -> _)
    ).flatten)

  private def _video_repository_file_json(video: ResolvedVideoPackage, source: Path, target: Path, kind: String): JsObject = {
    val extension = target.getFileName.toString.split('.').lastOption.getOrElse("")
    PJson.obj(
      "layer" -> "repository",
      "type" -> kind,
      "module" -> video.module,
      "artifactId" -> video.module,
      "version" -> video.version,
      "extension" -> extension,
      "warehousePath" -> s"repository/video/${video.module}/${video.version}/${target.getFileName}",
      "publicPath" -> s"repository/video/${video.module}/${video.version}/${target.getFileName}",
      "sitePublicPath" -> video.publicPath,
      "name" -> target.getFileName.toString,
      "expected" -> false,
      "size" -> Files.size(source),
      "sha256" -> _sha256(source)
    )
  }

  private def _sidecar_kind(path: Path): String = {
    val name = path.getFileName.toString
    if (name.endsWith(".rdf-manifest.json")) "rdf-manifest"
    else if (name.endsWith(".manifest.json")) "manifest"
    else if (name.endsWith(".ttl")) "turtle"
    else if (name.endsWith(".jsonld")) "jsonld"
    else if (name.endsWith(".srt")) "captions"
    else if (name.endsWith(".transcript.json")) "transcript"
    else RAISE.invalidArgumentFault(s"Unsupported video sidecar: $path")
  }

  private def _video_sidecars_json(video: ResolvedVideoPackage, sidecars: Vector[(Path, Path)]): JsObject =
    PJson.obj(
      "repository" -> JsArray(_video_repository_sidecar_files(video, sidecars)),
      "rdf" -> _video_rdf_reference_json(video)
    )

  private def _video_registry_root(video: ResolvedVideoPackage): String =
    s"metadata/video/${video.name}/${video.version}"

  private def _descriptor_file(packagedir: Path): Path = {
    val files = _descriptor_names.map(packagedir.resolve).filter(Files.isRegularFile(_))
    files match {
      case Vector(x) => x
      case Vector() => RAISE.invalidArgumentFault(s"Missing video descriptor. Expected one of: ${_descriptor_names.mkString(", ")}")
      case xs => RAISE.invalidArgumentFault(s"Multiple video descriptors found: ${xs.map(_.getFileName.toString).mkString(", ")}")
    }
  }

  private def _validate_package_dir(path: Path): Unit = {
    val name = path.getFileName.toString
    if (name.endsWith(".video.d"))
      RAISE.invalidArgumentFault(s"*.video.d is reserved for generated/work directories, not video source packages: $path")
    if (!name.endsWith(".video"))
      RAISE.invalidArgumentFault(s"Video source package directory must end with .video: $path")
    if (!Files.isDirectory(path))
      RAISE.invalidArgumentFault(s"Video source package directory does not exist: $path")
  }

  private def _relative_path(value: String, label: String): String = {
    val path = Paths.get(value).normalize()
    if (path.isAbsolute || path.startsWith("..") || value.contains("\u0000"))
      RAISE.invalidArgumentFault(s"Invalid video $label path: $value")
    path.toString.replace('\\', '/')
  }

  private def _validate_slug(value: String, label: String): String = {
    val name = value.trim
    _slug_pattern.findFirstIn(name) match {
      case Some(x) if x == name => name
      case _ => RAISE.invalidArgumentFault(s"Invalid ${label}: ${value}. Expected ${_slug_pattern.regex}")
    }
  }

  private def _validate_version(value: String): String = {
    val version = Option(value).getOrElse("")
    if (version.isEmpty || version != version.trim || version.contains('/') || version.contains('\\') ||
      version.contains('\u0000') || version == "." || version == "..")
      RAISE.invalidArgumentFault(s"Video version must be a non-empty safe exact segment: $value")
    version
  }

  private def _validate_public_path(value: String): String = {
    val path = _relative_path(value, "publish.publicPath")
    if (path.startsWith("repository/") || path.startsWith("metadata/"))
      RAISE.invalidArgumentFault(s"Video publicPath must be a site projection path, not a reserved root: $value")
    path
  }

  private def _source_package_path(packagedir: Path): String = {
    val normalized = packagedir.toAbsolutePath.normalize()
    val names = normalized.iterator().asScala.toVector.map(_.toString)
    val marker = Vector("src", "main", "doxsite")
    val index = names.sliding(marker.length).zipWithIndex.collectFirst {
      case (xs, i) if xs == marker => i + marker.length
    }
    index match {
      case Some(i) if i < names.length =>
        names.drop(i).mkString("/")
      case _ =>
        normalized.getFileName.toString
    }
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

  private val _property_options = Set("save", "warehouse", "version")
  private val _p_video_package = spec.Parameter.argumentFile("video-package-dir")
  private val _p_save = spec.Parameter.propertyFileOption("save")
  private val _p_warehouse = spec.Parameter.propertyFileOption("warehouse")
  private val _p_version = spec.Parameter.property("version")
  private val _p_force = spec.Parameter("force", spec.Parameter.SwitchKind)

  private def _copy(source: Path, target: Path): Unit = {
    Files.createDirectories(target.getParent)
    Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING)
  }

  private def _copy_directory(source: Path, target: Path): Unit = {
    val stream = Files.walk(source)
    try {
      stream.iterator().asScala.foreach { path =>
        val destination = target.resolve(source.relativize(path))
        if (Files.isDirectory(path))
          Files.createDirectories(destination)
        else
          _copy(path, destination)
      }
    } finally {
      stream.close()
    }
  }

  private def _delete_directory(path: Path): Unit =
    if (Files.exists(path)) {
      val stream = Files.walk(path)
      try {
        stream.iterator().asScala.toVector.reverse.foreach(Files.deleteIfExists)
      } finally {
        stream.close()
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
}
