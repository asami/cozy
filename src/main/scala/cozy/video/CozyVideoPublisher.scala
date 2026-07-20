package cozy.video

import org.goldenport.RAISE
import org.goldenport.config.StructuredDocumentLoader
import org.goldenport.io.InputSource
import org.goldenport.cli.spec
import cozy.publication.CozyPublicationCompiler
import cozy.runtime.CozyCliArgs
import io.circe.{Decoder, HCursor, Json => CJson}
import play.api.libs.json.{Json => PJson, JsArray, JsNull, JsObject, JsValue}
import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path, Paths, StandardCopyOption}
import java.security.MessageDigest
import scala.collection.JavaConverters._

/*
 * @since   Jun. 19, 2026
 * @version Jul. 20, 2026
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
    renderer: Option[String],
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

    private def _renderer(c: HCursor): Decoder.Result[Option[String]] =
      c.downField("renderer").focus match {
        case Some(json) if json.isString => Right(json.asString)
        case Some(json) => Right(json.hcursor.downField("engine").as[String].toOption)
        case None => Right(None)
      }
  }

  final case class VideoDescriptorVideo(name: Option[String])
  object VideoDescriptorVideo {
    implicit val decoder: Decoder[VideoDescriptorVideo] = (c: HCursor) =>
      c.downField("name").as[Option[String]].map(VideoDescriptorVideo.apply)
  }

  final case class VideoDescriptorPublish(module: Option[String], publicPath: Option[String])
  object VideoDescriptorPublish {
    implicit val decoder: Decoder[VideoDescriptorPublish] = (c: HCursor) =>
      for {
        module <- c.downField("module").as[Option[String]]
        publicpath <- c.downField("publicPath").as[Option[String]].flatMap {
          case Some(s) => Right(Some(s))
          case None => c.downField("public-path").as[Option[String]]
        }
      } yield VideoDescriptorPublish(module, publicpath)
  }

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
    assets: Vector[CozyVideoAssets.Resolved]
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
    publicationBundle: Path
  )

  def publish(args: List[String]): Unit =
    publish(PublishVideoConfig.create(args), CozyVideo.VoicevoxClient.default, CozyVideo.VideoProcessRunner.default)

  def publish(
    config: PublishVideoConfig,
    voicevox: CozyVideo.VoicevoxClient,
    runner: CozyVideo.VideoProcessRunner
  ): PublishVideoResult = {
    val video = resolve(config)
    val workspace = _prepare_workspace(video)
    val projectfile = _write_video_project(video, workspace)
    _run_video_pipeline(video, projectfile, voicevox, runner)
    val artifact = _copy_artifact(video, workspace, config)
    _publish_metadata(config, video, workspace, projectfile, artifact)
  }

  def resolve(config: PublishVideoConfig): ResolvedVideoPackage = {
    val packagedir = config.packageDir.toAbsolutePath.normalize()
    _validate_package_dir(packagedir)
    val descriptorfile = _descriptor_file(packagedir)
    val descriptor = StructuredDocumentLoader.loadDocument[VideoDescriptor](InputSource(descriptorfile.toFile)).take
    val slug = packagedir.getFileName.toString.stripSuffix(".video")
    val name = _validate_slug(descriptor.video.flatMap(_.name).getOrElse(slug), "video.name")
    val title = descriptor.title.map(_.trim).filter(_.nonEmpty).getOrElse(name)
    val version = config.version.orElse(descriptor.version).map(_.trim).filter(_.nonEmpty).getOrElse("0.0.0-SNAPSHOT")
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
    val renderer = descriptor.renderer.map(_.trim).filter(_.nonEmpty).getOrElse("simple-java2d")
    if (renderer != "remotion" && renderer != "simple-java2d")
      RAISE.invalidArgumentFault(s"Unsupported video renderer in descriptor: $renderer")
    val toolmode = descriptor.toolMode.map(_.trim).filter(_.nonEmpty).getOrElse("docker")
    CozyVideo.VideoToolMode.parse(toolmode)
    val publish = descriptor.publish.getOrElse(VideoDescriptorPublish(None, None))
    val module = _validate_slug(publish.module.getOrElse(name), "publish.module")
    val publicpath = _validate_public_path(publish.publicPath.getOrElse(s"videos/${name}.mp4"))
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
      CozyVideoAssets.resolve(packagedir, descriptor.assets)
    )
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
      "renderer" -> PJson.obj(
        "engine" -> video.renderer
      ),
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
      Some("exclude" -> JsArray(settings.exclude.map(PJson.toJson(_))))
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

  private def _copy_artifact(video: ResolvedVideoPackage, workspace: Path, config: PublishVideoConfig): Path = {
    val source = workspace.resolve("build/final.mp4").normalize()
    if (!Files.isRegularFile(source))
      RAISE.invalidArgumentFault(s"Video build did not create final output: $source")
    val target = _repository_artifact_path(config, video.warehousePath)
    if (Files.exists(target) && !config.force)
      RAISE.invalidArgumentFault(s"Video warehouse artifact already exists: $target. Use --force to replace it.")
    Files.createDirectories(target.getParent)
    Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING)
    _copy_sidecar_if_present(workspace.resolve("build/manifest.json"), target.resolveSibling(s"${video.name}-${video.version}.manifest.json"))
    _copy_sidecar_if_present(workspace.resolve("rdf/video.ttl"), target.resolveSibling(s"${video.name}-${video.version}.ttl"))
    _copy_sidecar_if_present(workspace.resolve("rdf/video.jsonld"), target.resolveSibling(s"${video.name}-${video.version}.jsonld"))
    _copy_sidecar_if_present(workspace.resolve("rdf/manifest.json"), target.resolveSibling(s"${video.name}-${video.version}.rdf-manifest.json"))
    _copy_sidecar_if_present(workspace.resolve("build/captions.srt"), target.resolveSibling(s"${video.name}-${video.version}.srt"))
    _copy_sidecar_if_present(workspace.resolve("build/transcript.json"), target.resolveSibling(s"${video.name}-${video.version}.transcript.json"))
    target
  }

  private def _repository_artifact_path(config: PublishVideoConfig, warehousepath: String): Path =
    config.repositoryDir match {
      case Some(repositorydir) if warehousepath == "repository" =>
        repositorydir.toAbsolutePath.normalize()
      case Some(repositorydir) if warehousepath.startsWith("repository/") =>
        repositorydir.resolve(warehousepath.stripPrefix("repository/")).toAbsolutePath.normalize()
      case _ =>
        config.warehouseDir.resolve(warehousepath).toAbsolutePath.normalize()
    }

  private def _publish_metadata(
    config: PublishVideoConfig,
    video: ResolvedVideoPackage,
    workspace: Path,
    projectfile: Path,
    artifact: Path
  ): PublishVideoResult = {
    val narration = _video_narration_json(workspace)
    val videojson = _video_metadata_json(video, artifact, narration)
    val artifactjson = _video_artifact_json(video, artifact)
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
    CozyPublicationCompiler.publishMetadata(
      config.saveDir,
      video.name,
      None,
      video.packageDir,
      Vector(
        s"metadata/catalog/videos/${video.name}.json" -> catalogjson,
        s"metadata/videos/${video.name}/metadata.json" -> videojson,
        s"metadata/artifacts/repository/${video.name}.json" -> artifactjson,
        s"${_video_registry_root(video)}/manifest.json" -> _video_registry_manifest_json(video, artifact, narration),
        s"${_video_registry_root(video)}/rdf.json" -> _video_registry_rdf_json(video, artifact),
        s"metadata/video/${video.name}/latest.json" -> _video_latest_json(video)
      )
    )
    PublishVideoResult(video, workspace, projectfile, artifact, config.saveDir.resolve(s"${video.name}.json"))
  }

  private def _video_metadata_json(
    video: ResolvedVideoPackage,
    artifact: Path,
    narration: Option[JsObject]
  ): JsValue = {
    val rdf = Some(_video_rdf_reference_json(video))
    val captions = _video_repository_sidecar_json(video, artifact.resolveSibling(s"${video.name}-${video.version}.srt"), "captions")
    val transcript = _video_repository_sidecar_json(video, artifact.resolveSibling(s"${video.name}-${video.version}.transcript.json"), "transcript")
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
          "sha256" -> _sha256(artifact),
          "size" -> Files.size(artifact)
        )
      ) ++ JsObject(optional))
    )
    base
  }

  private def _video_registry_manifest_json(
    video: ResolvedVideoPackage,
    artifact: Path,
    narration: Option[JsObject]
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
      "artifact" -> PJson.obj(
        "warehousePath" -> video.warehousePath,
        "publicPath" -> video.repositoryPublicPath,
        "sitePublicPath" -> video.publicPath,
        "repositoryPublicPath" -> video.repositoryPublicPath,
        "size" -> Files.size(artifact),
        "sha256" -> _sha256(artifact)
      ),
      "sidecars" -> _video_sidecars_json(video, artifact)
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
      def strings(name: String): Vector[String] =
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
        "providers" -> strings("provider"),
        "executionModes" -> strings("executionMode"),
        "voices" -> voices,
        "audioFormats" -> formats,
        "manifests" -> manifests.map(path => workspace.relativize(path).toString)
      ))
    }
  }

  private def _video_registry_rdf_json(video: ResolvedVideoPackage, artifact: Path): JsValue =
    PJson.obj(
      "schema" -> _schema,
      "type" -> "video-rdf",
      "video" -> PJson.obj(
        "name" -> video.name,
        "version" -> video.version
      ),
      "registryPath" -> s"${_video_registry_root(video)}/rdf",
      "files" -> _video_rdf_files_json(video, artifact)
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

  private def _video_artifact_json(video: ResolvedVideoPackage, artifact: Path): JsValue =
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
            "size" -> Files.size(artifact),
            "sha256" -> _sha256(artifact)
          ) +: _video_repository_sidecar_files(video, artifact)
        )
      )
    )

  private def _video_repository_sidecar_files(video: ResolvedVideoPackage, artifact: Path): Vector[JsObject] =
    Vector(
      "manifest" -> artifact.resolveSibling(s"${video.name}-${video.version}.manifest.json"),
      "turtle" -> artifact.resolveSibling(s"${video.name}-${video.version}.ttl"),
      "jsonld" -> artifact.resolveSibling(s"${video.name}-${video.version}.jsonld"),
      "rdf-manifest" -> artifact.resolveSibling(s"${video.name}-${video.version}.rdf-manifest.json"),
      "captions" -> artifact.resolveSibling(s"${video.name}-${video.version}.srt"),
      "transcript" -> artifact.resolveSibling(s"${video.name}-${video.version}.transcript.json")
    ).flatMap { case (kind, path) =>
      _video_repository_sidecar_json(video, path, kind)
    }

  private def _video_repository_sidecar_json(video: ResolvedVideoPackage, path: Path, kind: String): Option[JsObject] =
    if (Files.isRegularFile(path))
      Some(_video_repository_file_json(video, path, kind))
    else
      None

  private def _video_rdf_files_json(video: ResolvedVideoPackage, artifact: Path): JsObject =
    JsObject(Vector(
      _video_repository_sidecar_json(video, artifact.resolveSibling(s"${video.name}-${video.version}.ttl"), "turtle").map("turtle" -> _),
      _video_repository_sidecar_json(video, artifact.resolveSibling(s"${video.name}-${video.version}.jsonld"), "jsonLd").map("jsonLd" -> _),
      _video_repository_sidecar_json(video, artifact.resolveSibling(s"${video.name}-${video.version}.rdf-manifest.json"), "manifest").map("manifest" -> _)
    ).flatten)

  private def _video_repository_file_json(video: ResolvedVideoPackage, path: Path, kind: String): JsObject = {
    val extension = path.getFileName.toString.split('.').lastOption.getOrElse("")
    PJson.obj(
      "layer" -> "repository",
      "type" -> kind,
      "module" -> video.module,
      "artifactId" -> video.module,
      "version" -> video.version,
      "extension" -> extension,
      "warehousePath" -> s"repository/video/${video.module}/${video.version}/${path.getFileName}",
      "publicPath" -> s"repository/video/${video.module}/${video.version}/${path.getFileName}",
      "sitePublicPath" -> video.publicPath,
      "name" -> path.getFileName.toString,
      "expected" -> false,
      "size" -> Files.size(path),
      "sha256" -> _sha256(path)
    )
  }

  private def _video_sidecars_json(video: ResolvedVideoPackage, artifact: Path): JsObject =
    PJson.obj(
      "repository" -> JsArray(_video_repository_sidecar_files(video, artifact)),
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

  private def _copy_sidecar_if_present(source: Path, target: Path): Unit =
    if (Files.isRegularFile(source))
      _copy(source, target)

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
