package cozy.video

import org.goldenport.RAISE
import org.goldenport.config.StructuredDocumentLoader
import org.goldenport.context.{FaultException, NetworkIoFault, SubsystemIoFault}
import org.goldenport.io.InputSource
import cozy.config.{CozyProjectContext, CozyProjectYamlConfig}
import cozy.runtime.CozyCliArgs
import org.goldenport.cli.spec
import org.smartdox.semanticweb.{Rdf, RdfRenderer, Vocabulary}
import io.circe.{Decoder, HCursor, Json}
import io.circe.parser
import java.io.ByteArrayOutputStream
import java.net.{URI, URLEncoder}
import java.net.http.{HttpClient, HttpRequest, HttpResponse}
import java.nio.charset.StandardCharsets
import java.nio.file.attribute.BasicFileAttributes
import java.nio.file.{Files, LinkOption, Path, Paths, StandardCopyOption}
import java.security.MessageDigest
import java.time.{Duration => JDuration}
import java.util.concurrent.TimeUnit
import scala.collection.JavaConverters._
import scala.concurrent.{Await, ExecutionContext => ScalaExecutionContext, Future, blocking}
import scala.concurrent.duration._
import scala.util.Try
import scala.util.control.NonFatal

/*
 * @since   Aug. 14, 2026
 * @version Aug. 27, 2026
 * @author  ASAMI, Tomoharu
 */
private[cozy] trait CozyVideoPlanning {
  self: CozyVideoImplementation.type =>
  private[video] def _plan(projectfile: Path, toolmode: Option[String], dockerimage: Option[String]): VideoPlan = {
    val verifiedprojectfile = _verified_project_file(projectfile)
    val project = _load_project(verifiedprojectfile)
    val projectroot = verifiedprojectfile.getParent
    val projectcontext = CozyProjectContext.resolve(projectroot)
    val encoding = VideoRenderer.resolveEncoding(project.renderer)
    val assets = CozyVideoAssets.resolve(projectroot, project.assets)
    val execution = VideoExecutionConfig.create(projectroot, project, toolmode, dockerimage)
    val outputpath = projectroot.resolve(project.output.getOrElse("build/final.mp4")).normalize()
    val manifestpath = outputpath.getParent.resolve("manifest.json").normalize()
    val parts = project.parts.zipWithIndex.map {
      case (part, index) => _part_plan(projectroot, project, part, index + 1)
    }
    val creditevidence = CozyVideoAssets.creditEvidence(projectroot, project.assets, assets)
    val audiomanifests = parts.flatMap { part =>
      part.audioDir.toVector.flatMap { audiodir =>
        val path = audiodir.resolve("manifest.json").normalize()
        _read_optional_audio_manifest(path, s"audio manifest ${part.id}").map(path -> _).toVector
      }
    }
    val credits = CozyVideoCredits.resolve(
      projectcontext,
      project.credits,
      project.locale,
      parts.flatMap(_.script),
      creditevidence,
      audiomanifests
    )
    val artifacts = Vector(
      VideoArtifactPlan("project-output", outputpath, "project.concat", VideoArtifactStatus.Planned),
      VideoArtifactPlan("project-manifest", manifestpath, "project.manifest", VideoArtifactStatus.Planned)
    ) ++ parts.flatMap(_.artifacts)
    val partoutputs = parts.filter(_.renderable).map(_.outputPath)
    val rawcommands =
      parts.flatMap(_.commands) ++
        Vector(
          VideoCommandPlan(
            "project.concat",
            "ffmpeg",
            VideoToolMode.Host,
            "concat planned part outputs into final video",
            partoutputs,
            Vector(outputpath)
          ),
          VideoCommandPlan(
            "project.manifest",
            "cozy",
            VideoToolMode.Host,
            "write project manifest",
            Vector(outputpath),
            Vector(manifestpath)
          )
        )
    val commands = rawcommands.map(_resolve_command(projectroot, execution, _))
    VideoPlan(verifiedprojectfile, projectroot, projectcontext, project, encoding, assets, credits, execution, outputpath, manifestpath, parts, artifacts, commands)
  }

  private[video] def _part_plan(
    projectroot: Path,
    project: VideoProject,
    part: VideoPart,
    index: Int
  ): VideoPartPlan = {
    val id = part.displayId(index)
    val parttype = part.displayType
    val supported = _supported_part_types.contains(parttype)
    val renderer = part.renderer.orElse(project.renderer).map(_.summary).getOrElse("engine=legacy")
    val outputpath = projectroot.resolve(part.output.getOrElse(s"build/parts/$id.mp4")).normalize()
    val audiodir = part.audioDir.map(x => projectroot.resolve(x).normalize()).orElse {
      if (supported) Some(projectroot.resolve(s"build/audio/$id").normalize()) else None
    }
    val recorddir = part.recordDir.map(x => _resolve_project_relative_path(projectroot, x, s"Web-demo part $id recordDir")).orElse {
      if (parttype == "web-demo") Some(projectroot.resolve(s"build/record/$id").normalize()) else None
    }
    val manifestpath = outputpath.getParent.resolve(s"${_basename(outputpath)}.manifest.json").normalize()
    val storyboardpath = part.storyboard.map(x => _resolve_project_relative_path(projectroot, x, s"Storyboard part $id storyboard"))
    val legacyscriptpath = part.script.map(x => projectroot.resolve(x).normalize())
    val scriptpath = storyboardpath.orElse(legacyscriptpath)
    val script = storyboardpath match {
      case Some(path) if Files.exists(path, LinkOption.NOFOLLOW_LINKS) =>
        _validate_storyboard_source(projectroot, path)
        Some(_load_storyboard_script(path, part.storyboardSection, project))
      case Some(_) => None
      case None => legacyscriptpath.flatMap(_load_script)
    }
    val scriptstatus = scriptpath match {
      case Some(path) if Files.isRegularFile(path) => "found"
      case Some(_) => "missing"
      case None => "none"
    }
    val stepspath = part.steps.map(x => projectroot.resolve(x).normalize())
    val stepsstatus = stepspath.map { path =>
      if (Files.isRegularFile(path)) "found" else "missing"
    }
    val artifacts = _part_artifacts(id, scriptpath, scriptstatus, stepspath, stepsstatus, outputpath, audiodir, recorddir, manifestpath)
    val commands =
      if (!supported)
        Vector.empty
      else
        _part_commands(id, parttype, renderer, scriptpath, scriptstatus, stepspath, stepsstatus, outputpath, audiodir, recorddir, manifestpath)
    VideoPartPlan(index, id, parttype, supported, renderer, part.storyboard.orElse(part.script), scriptpath, scriptstatus, script, part.steps, stepspath, stepsstatus, outputpath, audiodir, recorddir, manifestpath, artifacts, commands)
  }

  private[video] def _load_storyboard_script(path: Path, storyboardsection: Option[String], project: VideoProject): VideoScript = {
    val result = loadStoryboard(path)
    if (!result.isValid)
      throw new IllegalArgumentException(result.diagnostics.map(_.render).mkString("\n"))
    val storyboard = result.storyboard.get
    val selectedscenes = storyboardsection match {
      case Some(section) =>
        val scenes = storyboard.scenes.filter(_.section == section)
        if (scenes.isEmpty)
          RAISE.invalidArgumentFault(s"Storyboard section $section selects no scenes")
        scenes
      case None => storyboard.scenes
    }
    _storyboard_video_script(storyboard.copy(scenes = selectedscenes), project)
  }

  private[video] def _validate_storyboard_source(projectroot: Path, path: Path): Unit = {
    val root = projectroot.toAbsolutePath.normalize()
    val source = path.toAbsolutePath.normalize()
    if (!source.startsWith(root))
      RAISE.invalidArgumentFault(s"Storyboard source escapes the project root: $path")
    _validate_storyboard_source(source)
    var current = root
    root.relativize(source).iterator().asScala.foreach { segment =>
      current = current.resolve(segment)
      if (Files.exists(current, LinkOption.NOFOLLOW_LINKS) && Files.isSymbolicLink(current))
        RAISE.invalidArgumentFault(s"Storyboard source must not use a symbolic-link path: $current")
    }
  }

  private[video] def _validate_storyboard_source(path: Path): Unit = {
    if (Files.isSymbolicLink(path))
      RAISE.invalidArgumentFault(s"Storyboard source must not be a symbolic link: $path")
    if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS))
      RAISE.invalidArgumentFault(s"Storyboard source must be a direct regular file: $path")
  }

  private[video] def _storyboard_video_script(storyboard: Storyboard, project: VideoProject): VideoScript = {
    val storyboardpronunciationnotes = storyboard.scenes.flatMap(_.pronunciationNotes)
    val conflicting = storyboardpronunciationnotes.groupBy(_.surface).toVector.sortBy(_._1).collectFirst {
      case (surface, notes) if notes.map(_.reading).distinct.size > 1 => surface
    }
    conflicting.foreach { surface =>
      RAISE.invalidArgumentFault(s"Storyboard pronunciation surface has conflicting readings: $surface")
    }
    val pronunciations = storyboardpronunciationnotes.map(note => note.surface -> note.reading).toMap
    VideoScript(
      project.title,
      None,
      project.narration,
      project.voice,
      pronunciations,
      project.voiceTextNormalization,
      project.characters,
      Vector.empty,
      storyboard.scenes.map(_storyboard_video_scene),
      storyboardpronunciationnotes
    )
  }

  private[video] def _storyboard_video_scene(scene: StoryboardScene): VideoScene =
    VideoScene(
      Some(scene.id),
      Some(scene.speaker),
      Some(scene.narration),
      Some(scene.narration),
      Some(scene.caption),
      Some(scene.duration.toDouble),
      None,
      Some(scene.leadSilence.toDouble),
      Vector.empty,
      None,
      Json.obj(
        "order" -> Json.fromInt(scene.order),
        "role" -> Json.fromString(scene.role),
        "screen" -> _storyboard_screen_json(scene.screen),
        "duration" -> Json.fromBigDecimal(scene.duration),
        "leadSilence" -> Json.fromBigDecimal(scene.leadSilence),
        "direction" -> Json.fromString(scene.direction),
        "productionInserts" -> Json.fromValues(scene.productionInserts.map { insert =>
          Json.obj(
            "id" -> Json.fromString(insert.id),
            "kind" -> Json.fromString(insert.kind),
            "value" -> Json.fromString(insert.value)
          )
        }),
        "diagramRefs" -> Json.fromValues(scene.diagramRefs.map(Json.fromString)),
        "assetRefs" -> Json.fromValues(scene.assetRefs.map(Json.fromString)),
        "pronunciationNotes" -> Json.fromValues(scene.pronunciationNotes.map { note =>
          Json.obj(
            "surface" -> Json.fromString(note.surface),
            "reading" -> Json.fromString(note.reading)
          )
        })
      ),
      Some(scene.section),
      Json.obj("transition" -> Json.fromString(scene.transition))
    )

  private[video] def _storyboard_screen_json(screen: StoryboardScreenValue): Json = screen match {
    case StoryboardScreen(heading, content) =>
      Json.obj(
        "heading" -> Json.fromString(heading),
        "content" -> Json.fromString(content)
      )
    case StoryboardTextScreen(heading, content) =>
      Json.obj(
        "kind" -> Json.fromString("text"),
        "heading" -> Json.fromString(heading),
        "content" -> Json.fromString(content)
      )
    case StoryboardVisualPageScreen(source, catalog, pageid) =>
      Json.obj(
        "kind" -> Json.fromString("visual-page"),
        "source" -> Json.fromString(source),
        "catalog" -> Json.fromString(catalog),
        "pageId" -> Json.fromString(pageid)
      )
  }

  private[video] def _part_artifacts(
    id: String,
    scriptpath: Option[Path],
    scriptstatus: String,
    stepspath: Option[Path],
    stepsstatus: Option[String],
    outputpath: Path,
    audiodir: Option[Path],
    recorddir: Option[Path],
    manifestpath: Path
  ): Vector[VideoArtifactPlan] = {
    val scriptartifact = scriptpath.map { path =>
      VideoArtifactPlan("part-script", path, s"part.$id.input", _input_status(scriptstatus))
    }
    val stepsartifact = stepspath.map { path =>
      VideoArtifactPlan("part-steps", path, s"part.$id.input", _input_status(stepsstatus.getOrElse("missing")))
    }
    Vector(
      scriptartifact,
      stepsartifact,
      audiodir.map(path => VideoArtifactPlan("part-audio-dir", path, s"part.$id.synthesize", VideoArtifactStatus.Planned)),
      recorddir.map(path => VideoArtifactPlan("part-record-dir", path, s"part.$id.capture", VideoArtifactStatus.Planned)),
      Some(VideoArtifactPlan("part-output", outputpath, s"part.$id.render", VideoArtifactStatus.Planned)),
      Some(VideoArtifactPlan("part-manifest", manifestpath, s"part.$id.manifest", VideoArtifactStatus.Planned))
    ).flatten
  }

  private[video] def _part_commands(
    id: String,
    parttype: String,
    renderer: String,
    scriptpath: Option[Path],
    scriptstatus: String,
    stepspath: Option[Path],
    stepsstatus: Option[String],
    outputpath: Path,
    audiodir: Option[Path],
    recorddir: Option[Path],
    manifestpath: Path
  ): Vector[VideoCommandPlan] = {
    val parse = scriptpath.map { path =>
      VideoCommandPlan(
        s"part.$id.parse-script",
        "cozy",
        VideoToolMode.Host,
        s"parse $parttype script",
        Vector(path),
        Vector.empty
      )
    }.toVector
    val capture =
      if (parttype == "web-demo")
        stepspath.map { path =>
          VideoCommandPlan(
            s"part.$id.capture",
            "playwright",
            VideoToolMode.Host,
            "plan web-demo replay/capture",
            Vector(path),
            recorddir.toVector
          )
        }.toVector
      else
        Vector.empty
    val renderable = scriptstatus == "found" && (parttype != "web-demo" || stepsstatus.contains("found"))
    val visualhelper =
      if (renderable)
        Vector(VideoCommandPlan(
          s"part.$id.prepare-visuals",
          "python-pillow",
          VideoToolMode.Host,
          s"prepare $parttype visual helper assets",
          scriptpath.toVector,
          Vector.empty
        ))
      else
        Vector.empty
    val synthesize =
      if (renderable)
        audiodir.map { path =>
          VideoCommandPlan(
            s"part.$id.synthesize",
            "voicevox",
            VideoToolMode.ExternalService,
            "synthesize scene audio",
            scriptpath.toVector,
            Vector(path)
          )
        }.toVector
      else
        Vector.empty
    val render =
      if (renderable)
        Vector(VideoCommandPlan(
          s"part.$id.render",
          _renderer_tool(renderer),
          VideoToolMode.Host,
          s"render $parttype part",
          scriptpath.toVector ++ audiodir.toVector ++ recorddir.toVector,
          Vector(outputpath)
        ))
      else
        Vector.empty
    val manifest =
      if (renderable)
        Vector(VideoCommandPlan(
          s"part.$id.manifest",
          "cozy",
          VideoToolMode.Host,
          "write part manifest",
          Vector(outputpath),
          Vector(manifestpath)
        ))
      else
        Vector.empty
    parse ++ capture.filter(_ => stepsstatus.forall(_ == "found")) ++ visualhelper ++ synthesize ++ render ++ manifest
  }

  private[video] def _input_status(status: String): VideoArtifactStatus =
    status match {
      case "found" => VideoArtifactStatus.Input
      case _ => VideoArtifactStatus.MissingInput
    }

  private[video] def _renderer_tool(renderer: String): String =
    if (renderer.contains("engine=remotion"))
      "remotion"
    else
      "ffmpeg"

  private[video] def _resolve_command(
    projectroot: Path,
    execution: VideoExecutionConfig,
    command: VideoCommandPlan
  ): VideoCommandPlan =
    execution.toolMode match {
      case VideoToolMode.Docker if _docker_managed_tools.contains(command.toolName) =>
        command.copy(
          mode = VideoToolMode.Docker,
          preview = _docker_preview(projectroot, execution.dockerImage, command)
        )
      case _ =>
        command
    }

  private[video] def _docker_preview(projectroot: Path, image: String, command: VideoCommandPlan): String = {
    val inputargs = command.inputs.map(path => s"--input ${_shell_quote(_docker_path(projectroot, path))}")
    val outputargs = command.outputs.map(path => s"--output ${_shell_quote(_docker_path(projectroot, path))}")
    val args = (inputargs ++ outputargs).mkString(" ")
    val suffix = if (args.isEmpty) "" else " " + args
    s"docker run --rm -v ${_shell_quote(s"${projectroot}:/workspace")} -w /workspace ${_shell_quote(image)} ${_shell_quote(command.toolName)}$suffix # ${command.preview}"
  }

  private[video] def _shell_quote(value: String): String =
    "'" + value.replace("'", "'\"'\"'") + "'"
}
