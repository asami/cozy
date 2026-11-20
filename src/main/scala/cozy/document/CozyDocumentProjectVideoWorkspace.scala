package cozy.document

import cozy.config.CozyProjectContext
import cozy.video.CozyVideo
import io.circe.Json
import java.net.URI
import java.nio.file.{Files, LinkOption, Path, Paths, StandardCopyOption}
import scala.util.control.NonFatal

/** Runs one explicitly selected dialogue audio or video product in an external run. */
private[cozy] object CozyDocumentProjectVideoWorkspace {
  def build(project: Path, workspace: CozyDocumentProjectWorkRoot.Run, product: String, locale: String): String =
    buildCandidate(project, workspace, product, locale).report

  def buildCandidate(project: Path, workspace: CozyDocumentProjectWorkRoot.Run, product: String, locale: String, validateOnly: Boolean = false): CozyDocumentProjectPackage.Candidate = {
    if (!Set("audio", "video").contains(product) || !Set("ja", "en").contains(locale))
      _failure("video workspace requires audio or video and locale ja or en")
    val descriptor = CozyDocumentProject._load_json(project.resolve("media.yaml"), "media descriptor")
    val resources = descriptor.hcursor.downField("resources").focus.flatMap(_.asArray).getOrElse(_failure("media resources are missing"))
    val resource = resources.find(_.hcursor.get[String]("id").toOption.contains(s"video-$locale"))
      .getOrElse(_failure(s"video resource is missing for $locale"))
    val projectname = resource.hcursor.get[String]("project").toOption.getOrElse(_failure("video project path is missing"))
    val projectfile = _input(project, projectname)
    val video = CozyDocumentProject._load_json(projectfile, "video project")
    val parts = video.hcursor.downField("parts").focus.flatMap(_.asArray).getOrElse(_failure("video parts are missing"))
    if (parts.size != 1 || parts.head.hcursor.get[String]("type").toOption != Some("dialogue"))
      _failure("external video currently requires exactly one dialogue part")
    val part = parts.head.hcursor
    val partid = part.get[String]("id").toOption.getOrElse(_failure("dialogue part id is missing"))
    if (!partid.matches("[A-Za-z0-9][A-Za-z0-9._-]*") || partid == "." || partid == "..")
      _failure("dialogue part id is invalid")
    val scriptname = part.get[String]("script").toOption.getOrElse(_failure("dialogue script is missing"))
    val scriptfile = _input(project, scriptname)
    val script = CozyDocumentProject._load_json(scriptfile, "video dialogue script")
    val audio = _relative(part.get[String]("audioDir").toOption.getOrElse(_failure("dialogue audio directory is missing")))
    val partoutput = _relative(part.get[String]("output").toOption.getOrElse(_failure("dialogue output is missing")))
    val finaloutput = _relative(video.hcursor.get[String]("output").toOption.getOrElse(_failure("video output is missing")))
    if (audio == partoutput || audio == finaloutput || partoutput == finaloutput)
      _failure("video generated paths collide")
    val root = workspace.root.resolve("project.dox")
    val core = Vector(projectfile, scriptfile)
    val assetnames = if (product == "video") _asset_names(video, script) else Vector.empty
    val outputresources = resources.flatMap { item =>
      item.hcursor.get[String]("output").toOption.map(value => project.resolve(_relative(value)).normalize() -> item)
    }
    val resolved = assetnames.map(name => _asset(project, scriptfile, name, outputresources))
    val originalinputs = (core ++ resolved.flatMap(_._1)).distinct
    val generated = resolved.flatMap(_._2).distinct
    val destinations = originalinputs.map(path => project.relativize(path)) ++ generated.map { case (_, path) => project.relativize(path) } ++ Vector(audio, partoutput, finaloutput)
    destinations.combinations(2).foreach { pair =>
      val left = pair.head
      val right = pair(1)
      if (left == right || left.startsWith(right) || right.startsWith(left))
        _failure(s"video input and output paths overlap: $left and $right")
    }
    val profile = if (product == "video") video.hcursor.downField("credits").get[String]("profile").toOption.map(_profile(project, _)) else None
    Files.createDirectory(root)
    originalinputs.foreach(path => _copy(path, root.resolve(project.relativize(path))))
    profile.foreach { path => _copy(path, root.resolve(".cozy/video/credit-profiles").resolve(path.getFileName)) }
    generated.zipWithIndex.foreach { case ((id, output), index) =>
      val dep = Files.createDirectory(workspace.root.resolve(s"video-dependency-$index"))
      val temporary = Files.createDirectory(dep.resolve("tmp"))
      val cache = Files.createDirectory(dep.resolve("cache"))
      CozyDocumentProjectMediaWorkspace.buildCandidate(project, CozyDocumentProjectWorkRoot.Run(dep, temporary, cache), id, locale, validateOnly = validateOnly)
      val candidate = dep.resolve("project.dox").resolve(project.relativize(output))
      if (!validateOnly) {
        if (!Files.isRegularFile(candidate, LinkOption.NOFOLLOW_LINKS)) _failure(s"video dependency was not generated: $id")
        _copy(candidate, root.resolve(project.relativize(output)))
      }
    }
    if (validateOnly) return CozyDocumentProjectPackage.Candidate(root.resolve(project.relativize(projectfile)), "validated video inputs")
    val runner = CozyVideo.VideoProcessRunner.external(workspace)
    val probe = new CozyVideo.VideoToolProbe {
      def command(args: Vector[String], cwd: Path): CozyVideo.VideoCommandResult = runner.run(args, cwd)
      def httpGet(uri: URI): CozyVideo.VideoHttpResult = CozyVideo.VideoToolProbe.default.httpGet(uri)
      def exists(path: Path): Boolean = CozyVideo.VideoToolProbe.default.exists(path)
    }
    val tools = CozyVideo.VideoToolRegistry.production(probe)
    val savedir = if (product == "audio") root.resolve("target/document-project/audio").resolve(locale).resolve(partid) else root.resolve(audio)
    val voiceurl = video.hcursor.downField("tools").get[String]("voicevoxUrl").toOption
    val provider = script.hcursor.downField("narration").get[String]("provider").toOption
    val mode = if (provider.contains("macos-say")) Some("host") else None
    val synthesis = CozyVideo.synthesize(CozyVideo.SynthesizeConfig(root.resolve(project.relativize(scriptfile)), savedir, voicevoxUrl = voiceurl, toolMode = mode), tools, CozyVideo.VoicevoxClient.default, runner)
    val report = if (product == "audio") synthesis
    else {
      val engine = part.downField("renderer").get[String]("engine").toOption.orElse(video.hcursor.downField("renderer").get[String]("engine").toOption).getOrElse(_failure("video renderer is missing"))
      val staged = root.resolve(project.relativize(projectfile))
      val rendered = CozyVideo.render(CozyVideo.RenderConfig(staged, engine, Some(partid), checkTools = true), tools, runner)
      val built = CozyVideo.buildMedia(CozyVideo.BuildConfig(staged, dryRun = false, checkTools = true), tools, runner, Vector.empty)
      Vector(synthesis, rendered, built).mkString("\n")
    }
    val scriptbase = scriptfile.getFileName.toString.replaceFirst("\\.[^.]+$", "")
    val candidate = if (product == "audio") savedir.resolve(s"$scriptbase.wav") else root.resolve(finaloutput)
    if (!Files.isRegularFile(candidate, LinkOption.NOFOLLOW_LINKS)) _failure(s"selected candidate is unavailable: $candidate")
    CozyDocumentProjectPackage.Candidate(candidate, report)
  }

  private def _asset_names(video: Json, script: Json): Vector[String] = {
    val configured = video.hcursor.downField("assets").focus.flatMap(_.asObject).toVector.flatMap(_.values).flatMap(_.hcursor.get[String]("path").toOption)
    val characters = script.hcursor.downField("characters").focus.flatMap(_.asObject).toVector.flatMap(_.values).flatMap { value =>
      Vector("asset", "mouthClosedAsset", "mouthOpenAsset").flatMap(name => value.hcursor.get[String](name).toOption)
    }
    val visuals = script.hcursor.downField("scenes").focus.flatMap(_.asArray).getOrElse(Vector.empty).flatMap(_.hcursor.downField("visual").get[String]("image").toOption)
    (configured ++ characters ++ visuals).distinct
  }

  private def _asset(project: Path, script: Path, value: String, outputs: Vector[(Path, Json)]): (Option[Path], Option[(String, Path)]) = {
    val path = _relative(value)
    val candidates = Vector(script.getParent.resolve(path).normalize(), project.resolve(path).normalize()).distinct
    if (candidates.exists(!_.startsWith(project))) _failure(s"video asset escapes the project: $value")
    val generated = candidates.flatMap(candidate => outputs.collect { case (output, resource) if output == candidate => output -> resource })
    if (generated.size > 1) _failure(s"video asset has ambiguous producers: $value")
    generated.headOption match {
      case Some((output, resource)) =>
        if (resource.hcursor.get[String]("build").toOption != Some("svg-to-png")) _failure(s"unsupported video asset producer: $value")
        val id = resource.hcursor.get[String]("id").toOption.getOrElse(_failure("video asset producer id is missing"))
        _input(project, resource.hcursor.get[String]("source").toOption.getOrElse(_failure(s"video asset source is missing: $id")))
        None -> Some(id -> output)
      case None =>
        val source = candidates.find(path => Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS) && !Files.isSymbolicLink(path))
          .getOrElse(_failure(s"video asset is unavailable: $value"))
        Some(_input(project, project.relativize(source).toString)) -> None
    }
  }

  private def _profile(project: Path, id: String): Path = {
    if (!id.matches("[A-Za-z0-9][A-Za-z0-9_-]*")) _failure("video credit profile id is invalid")
    val names = Vector(s"$id.yaml", s"$id.yml", s"$id.json")
    val paths = CozyProjectContext.resolve(project).layers.reverse.filter { layer =>
      layer.name == "user" || layer.root.exists(value => value.identity.startsWith(project.toRealPath()))
    }.flatMap(_.root.toVector).flatMap { layer =>
      names.map(name => layer.path.resolve("video/credit-profiles").resolve(name))
    }
    paths.find(path => Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS) && !Files.isSymbolicLink(path))
      .getOrElse(_failure(s"video credit profile is unavailable: $id"))
  }

  private def _relative(value: String): Path = {
    val path = try Paths.get(value) catch { case NonFatal(_) => _failure(s"invalid video path: $value") }
    if (value.isEmpty || path.isAbsolute || path.iterator().hasNext == false || path.normalize() != path || value.split('/').contains(".."))
      _failure(s"video path must be project-relative: $value")
    path
  }

  private def _input(root: Path, value: String): Path = {
    val path = root.resolve(_relative(value)).normalize()
    if (Files.isSymbolicLink(path) || !Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS) || !path.toRealPath().startsWith(root.toRealPath()))
      _failure(s"video input is unavailable: $value")
    path
  }

  private def _copy(source: Path, target: Path): Unit = {
    Files.createDirectories(target.getParent)
    Files.copy(source, target, StandardCopyOption.COPY_ATTRIBUTES)
  }

  private def _failure(message: String): Nothing = CozyDocumentProject._failure("DP-OP-001", message)
}
