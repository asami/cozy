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
 * @version Aug. 14, 2026
 * @author  ASAMI, Tomoharu
 */
private[cozy] trait CozyVideoRenderWorkspace {
  self: CozyVideoTypes with CozyVideoRuntime with CozyVideoCommand with CozyVideoNarration with CozyVideoToolValidation with CozyVideoTranscription with CozyVideoReviewEvidence with CozyVideoBuildReplay with CozyVideoRdf with CozyVideoRenderTemplates with CozyVideoPlanning with CozyVideoPresentation =>
  private[video] def _render_remotion(
    config: RenderConfig,
    plan: VideoPlan,
    runner: VideoProcessRunner
  ): VideoRenderResult = {
    plan.credits.requireValid()
    _write_credit_outputs(plan)
    val parts = _render_target_parts(config, plan)
    val rendered = parts.map { part =>
      val script = part.script.getOrElse(RAISE.invalidArgumentFault(s"Part is missing a parsed script: ${part.id}"))
      _validate_declarative_diagrams(script)
      val audiodir = part.audioDir.getOrElse(RAISE.invalidArgumentFault(s"Part has no audio directory: ${part.id}"))
      val audio = _load_audio_input(part.id, audiodir, script)
      val workdir = _remotion_work_dir(plan.projectRoot, part.id)
      val props = _write_remotion_workspace(plan, part, script, audio, workdir)
      _run_remotion(plan.projectRoot, plan.execution, part, workdir, runner)
      _write_part_manifest(
        plan,
        part,
        audio,
        "remotion",
        workdir,
        "remotionWorkDir",
        Vector(
          "profile" -> plan.project.profile.map(Json.fromString).getOrElse(Json.Null),
          "visualEffects" -> props.hcursor.downField("visualEffects").focus.getOrElse(Json.arr()),
          "assets" -> props.hcursor.downField("assets").focus.getOrElse(Json.arr()),
          "credits" -> props.hcursor.downField("credits").focus.getOrElse(Json.Null),
          "creditDigest" -> plan.credits.profileId.map(_ => Json.fromString(plan.credits.digest)).getOrElse(Json.Null),
          "rendererTemplate" -> props.hcursor.downField("rendererTemplate").focus.getOrElse(Json.Null),
          "rendererTemplateSha256" -> props.hcursor.downField("rendererTemplateSha256").focus.getOrElse(Json.Null),
          "rendererTemplateResources" -> props.hcursor.downField("rendererTemplateResources").focus.getOrElse(Json.arr()),
          "recordingPath" -> props.hcursor.downField("recordingPath").focus.getOrElse(Json.Null),
          "timing" -> props.hcursor.downField("timing").focus.getOrElse(Json.obj())
        )
      )
      VideoRenderedPart(part.id, part.outputPath, part.manifestPath, workdir)
    }
    VideoRenderResult(plan.projectFile, rendered, plan.execution.toolMode, plan.execution.dockerImage, plan.credits.warnings)
  }

  private[video] def _render_simple_java2d(
    config: RenderConfig,
    plan: VideoPlan,
    runner: VideoProcessRunner
  ): VideoRenderResult = {
    plan.credits.requireValid()
    _write_credit_outputs(plan)
    val parts = _render_target_parts(config, plan)
    val rendered = parts.map { part =>
      val script = part.script.getOrElse(RAISE.invalidArgumentFault(s"Part is missing a parsed script: ${part.id}"))
      val audiodir = part.audioDir.getOrElse(RAISE.invalidArgumentFault(s"Part has no audio directory: ${part.id}"))
      val audio = _load_simple_java2d_input(part.id, audiodir, part, script)
      val workdir = _simple_java2d_work_dir(plan.projectRoot, part.id)
      val frame = workdir.resolve("frame.png")
      _write_simple_java2d_workspace(plan, part, script, audio, workdir)
      _run_simple_java2d_frame(plan.projectRoot, plan.execution, part, workdir, runner)
      if (!Files.isRegularFile(frame))
        RAISE.invalidArgumentFault(s"simple-java2d frame render did not create frame: $frame")
      _run_simple_java2d_ffmpeg(plan.projectRoot, plan.execution, part, frame, audio.combinedFile, runner)
      if (!Files.isRegularFile(part.outputPath))
        RAISE.invalidArgumentFault(s"simple-java2d ffmpeg encode did not create output: ${part.outputPath}")
      _write_part_manifest(
        plan,
        part,
        audio.audio,
        "simple-java2d",
        workdir,
        "simpleJava2dWorkDir",
        Vector(
          "framePath" -> Json.fromString(frame.toString),
          "audioCombinedPath" -> Json.fromString(audio.combinedFile.toString)
        )
      )
      VideoRenderedPart(part.id, part.outputPath, part.manifestPath, workdir, "simpleJava2dWorkDir")
    }
    VideoRenderResult(plan.projectFile, rendered, plan.execution.toolMode, plan.execution.dockerImage, plan.credits.warnings)
  }

  private[video] def _render_target_parts(config: RenderConfig, plan: VideoPlan): Vector[VideoPartPlan] = {
    val candidates = config.part match {
      case Some(id) =>
        val part = plan.parts.find(_.id == id).getOrElse(
          RAISE.invalidArgumentFault(s"Unknown video part: $id")
        )
        Vector(part)
      case None =>
        plan.parts.filter(_.renderable)
    }
    if (candidates.isEmpty)
      RAISE.invalidArgumentFault("No renderable video parts found.")
    candidates.foreach { part =>
      if (!part.renderable)
        RAISE.invalidArgumentFault(s"Video part is not renderable: ${part.id}")
    }
    candidates
  }

  private[video] def _load_audio_input(partid: String, audiodir: Path, script: VideoScript): VideoAudioInput = {
    val manifest = audiodir.resolve("manifest.json").normalize()
    if (!Files.isRegularFile(manifest))
      RAISE.invalidArgumentFault(s"Missing audio manifest for part $partid: $manifest. Run: cozy video synthesize <script-file> --save <audio-dir>")
    val entries = parser.decode[Vector[VideoAudioManifestEntry]](Files.readString(manifest, StandardCharsets.UTF_8)).fold(
      e => RAISE.invalidArgumentFault(s"Invalid audio manifest for part $partid: ${e.getMessage}"),
      identity
    )
    val scenes = script.expandedScenes
    if (entries.size != scenes.size)
      RAISE.invalidArgumentFault(s"Audio manifest scene count does not match script for part $partid: ${entries.size} != ${scenes.size}")
    val files = entries.map { entry =>
      val path = audiodir.resolve(entry.file).normalize()
      if (!Files.isRegularFile(path))
        RAISE.invalidArgumentFault(s"Missing audio file for part $partid: $path")
      path
    }
    VideoAudioInput(manifest, entries, files)
  }

  private[video] def _load_simple_java2d_input(partid: String, audiodir: Path, part: VideoPartPlan, script: VideoScript): VideoSimpleJava2dInput = {
    val audio = _load_audio_input(partid, audiodir, script)
    val scriptpath = part.scriptPath.getOrElse(RAISE.invalidArgumentFault(s"Part has no script path: $partid"))
    val combined = audiodir.resolve(s"${_basename(scriptpath)}.wav").normalize()
    if (!Files.isRegularFile(combined))
      RAISE.invalidArgumentFault(s"Missing combined audio file for part $partid: $combined. Run: cozy video synthesize <script-file> --save <audio-dir>")
    VideoSimpleJava2dInput(audio, combined)
  }

  private[video] def _remotion_work_dir(projectroot: Path, partid: String): Path =
    projectroot.resolve("target/cozy-video/remotion").resolve(_file_segment_id(partid, "part id")).normalize()

  private[video] def _simple_java2d_work_dir(projectroot: Path, partid: String): Path =
    projectroot.resolve("target/cozy-video/simple-java2d").resolve(_file_segment_id(partid, "part id")).normalize()

  private[video] def _write_remotion_workspace(
    plan: VideoPlan,
    part: VideoPartPlan,
    script: VideoScript,
    audio: VideoAudioInput,
    workdir: Path
  ): Json = {
    val srcdir = workdir.resolve("src")
    Files.createDirectories(srcdir)
    Files.writeString(workdir.resolve("package.json"), _remotion_package_json, StandardCharsets.UTF_8)
    val characterdialogue = _is_character_dialogue(part, script, plan.project.renderer)
    val characterwebdemo = part.partType == "web-demo"
    val template = if (characterdialogue) Some(_load_character_dialogue_template()) else None
    val templateid =
      if (characterdialogue) Some(_character_dialogue_template_id)
      else if (characterwebdemo) Some(_character_web_demo_template_id)
      else None
    val recording = if (characterwebdemo) Some(_stage_web_demo_recording(plan.projectRoot, part, workdir)) else None
    Files.writeString(srcdir.resolve("Root.tsx"), if (characterdialogue) _remotion_character_dialogue_root_tsx else if (characterwebdemo) _remotion_character_web_demo_root_tsx else _remotion_root_tsx, StandardCharsets.UTF_8)
    template.foreach { bundled =>
      Files.write(srcdir.resolve(bundled.dialogue.name), bundled.dialogue.bytes)
      Files.write(srcdir.resolve(bundled.diagramlayout.name), bundled.diagramlayout.bytes)
    }
    Files.writeString(srcdir.resolve("render.mjs"), _remotion_render_mjs, StandardCharsets.UTF_8)
    val assets = _copy_remotion_assets(workdir, plan.assets)
    val dialogueassets = if (characterdialogue || characterwebdemo) _copy_character_dialogue_assets(plan.projectRoot, part, script, workdir, stagevisuals = characterdialogue) else CharacterDialogueAssets.empty
    val propsjson = _remotion_props_json(plan, part, script, audio, assets, workdir, characterdialogue, templateid, template.map(_.dialogue.sha256), template.map(_.resources), dialogueassets, recording)
    Files.writeString(workdir.resolve("props.json"), propsjson.spaces2, StandardCharsets.UTF_8)
    Files.writeString(srcdir.resolve("props.ts"), _remotion_props_ts(propsjson), StandardCharsets.UTF_8)
    _copy_remotion_audio(workdir, audio)
    propsjson
  }

  private[video] final case class CharacterDialogueAssets(characters: Json, visuals: Map[String, Json])
  private[video] object CharacterDialogueAssets {
    val empty = CharacterDialogueAssets(Json.obj(), Map.empty)
  }

  private[video] def _is_character_dialogue(part: VideoPartPlan, script: VideoScript, renderer: Option[VideoRenderer]): Boolean = {
    val strategy = _part_renderer_property(part, "strategy").orElse(renderer.flatMap(_.strategy)).map(_.trim.toLowerCase)
    part.partType == "dialogue" && (strategy match {
      case Some("narration-card") | Some("generic") => false
      case Some("character-dialogue") => true
      case _ => script.characters.nonEmpty
    })
  }

  private[video] def _part_renderer_property(part: VideoPartPlan, name: String): Option[String] =
    part.renderer.split(",").toVector.map(_.trim).collectFirst {
      case field if field.startsWith(name + "=") => field.drop(name.length + 1).trim
    }.filter(_.nonEmpty)

  private[video] final case class CharacterDialogueTemplateResource(name: String, bytes: Array[Byte], sha256: String)
  private[video] final case class CharacterDialogueTemplate(dialogue: CharacterDialogueTemplateResource, diagramlayout: CharacterDialogueTemplateResource) {
    def resources: Vector[(String, String)] = Vector(dialogue, diagramlayout).map(x => x.name -> x.sha256)
  }

  private[video] def _load_character_dialogue_template(): CharacterDialogueTemplate = {
    val dialogue = _load_character_dialogue_template_resource("DialogueVideo.jsx", _character_dialogue_template_sha256)
    val diagramlayout = _load_character_dialogue_template_resource("DiagramLayout.js", _character_dialogue_diagram_layout_sha256)
    CharacterDialogueTemplate(dialogue, diagramlayout)
  }

  private[video] def _load_character_dialogue_template_resource(name: String, expectedsha256: String): CharacterDialogueTemplateResource = {
    val resource = s"/cozy/video/remotion/$name"
    val stream = Option(getClass.getResourceAsStream(resource)).getOrElse(
      RAISE.invalidArgumentFault(s"Missing Cozy character-dialogue renderer resource: ${resource.drop(1)}")
    )
    val bytes = try stream.readAllBytes() finally stream.close()
    val actualsha256 = _sha256_bytes(bytes)
    if (actualsha256 != expectedsha256)
      RAISE.invalidArgumentFault(s"Cozy character-dialogue renderer resource digest does not match the bundled template contract: $name")
    CharacterDialogueTemplateResource(name, bytes, actualsha256)
  }

  private[video] def _sha256_bytes(bytes: Array[Byte]): String =
    MessageDigest.getInstance("SHA-256").digest(bytes).map(x => f"${x & 0xff}%02x").mkString

  private[video] def _validate_declarative_diagrams(script: VideoScript): Unit =
    script.expandedScenes.foreach { scene =>
      if (_json_string(scene.visual, "kind").contains("diagram"))
        _validate_declarative_diagram(scene.id.getOrElse("(no id)"), scene.visual)
    }

  private[video] def _validate_declarative_diagram(sceneid: String, visual: Json): Unit = {
    val diagram = visual.hcursor.downField("diagram").focus.getOrElse(
      _diagram_fault(sceneid, "missing-diagram")
    )
    val cursor = diagram.hcursor
    val layout = cursor.get[String]("layout").getOrElse(_diagram_fault(sceneid, "unsupported-layout"))
    if (layout != "flow" && layout != "axis")
      _diagram_fault(sceneid, "unsupported-layout")
    val direction = cursor.get[Option[String]]("direction").getOrElse(_diagram_fault(sceneid, "unsupported-direction")).getOrElse("right")
    if (direction != "right")
      _diagram_fault(sceneid, "unsupported-direction")
    val clearance = cursor.get[Option[Double]]("clearance").getOrElse(_diagram_fault(sceneid, "invalid-clearance")).getOrElse(24.0)
    if (!java.lang.Double.isFinite(clearance) || clearance <= 0)
      _diagram_fault(sceneid, "invalid-clearance")
    val nodes = cursor.get[Vector[Json]]("nodes").getOrElse(_diagram_fault(sceneid, "empty-nodes"))
    if (nodes.isEmpty)
      _diagram_fault(sceneid, "empty-nodes")
    val parsednodes = nodes.map { node =>
      val nodecursor = node.hcursor
      val id = nodecursor.get[String]("id").getOrElse(_diagram_fault(sceneid, "invalid-node-id"))
      if (id.trim.isEmpty)
        _diagram_fault(sceneid, "invalid-node-id")
      val label = nodecursor.get[String]("label").getOrElse(_diagram_fault(sceneid, "invalid-node-label", id))
      if (label.trim.isEmpty)
        _diagram_fault(sceneid, "invalid-node-label", id)
      val role = nodecursor.get[String]("role").getOrElse(_diagram_fault(sceneid, "invalid-node-role", id))
      if (role.trim.isEmpty)
        _diagram_fault(sceneid, "invalid-node-role", id)
      val policy = nodecursor.get[String]("labelPolicy").getOrElse(_diagram_fault(sceneid, "unsupported-label-policy", id))
      if (policy != "atomic" && policy != "balanced")
        _diagram_fault(sceneid, "unsupported-label-policy", id)
      _validate_declarative_diagram_label(sceneid, id, label, policy, if (layout == "flow") 18.0 else 21.0)
      (id, role)
    }
    parsednodes.groupBy(_._1).collectFirst { case (id, values) if values.size > 1 => id }.foreach { id =>
      _diagram_fault(sceneid, "duplicate-node-id", id)
    }
    val nodeids = parsednodes.map(_._1).toSet
    val edges = cursor.get[Option[Vector[Json]]]("edges").getOrElse(_diagram_fault(sceneid, "invalid-edges")).getOrElse(Vector.empty)
    edges.foreach { edge =>
      val edgecursor = edge.hcursor
      val from = edgecursor.get[String]("from").getOrElse(_diagram_fault(sceneid, "invalid-edge"))
      val to = edgecursor.get[String]("to").getOrElse(_diagram_fault(sceneid, "invalid-edge"))
      if (!nodeids.contains(from))
        _diagram_fault(sceneid, "missing-edge-endpoint", from)
      if (!nodeids.contains(to))
        _diagram_fault(sceneid, "missing-edge-endpoint", to)
    }
    if (layout == "axis" && parsednodes.count(_._2 == "axis") != 1)
      _diagram_fault(sceneid, "axis-role-count")
  }

  private[video] def _validate_declarative_diagram_label(sceneid: String, nodeid: String, label: String, policy: String, fontsize: Double): Unit = {
    val maxtextwidth = 204.0
    if (policy == "atomic" && _diagram_label_width(label, fontsize) > maxtextwidth)
      _diagram_fault(sceneid, "impossible-atomic-fit", nodeid)
    if (policy == "balanced") {
      val segments = label.split("(?<=-)|\\s+").filter(_.nonEmpty)
      if (segments.isEmpty || segments.exists(x => _diagram_label_width(x, fontsize) > maxtextwidth))
        _diagram_fault(sceneid, "label-overflow", nodeid)
    }
  }

  private[video] def _diagram_label_width(label: String, fontsize: Double): Double =
    label.toVector.map(x => if (x <= '\u007f') fontsize * 0.56 else fontsize).sum

  private[video] def _diagram_fault(sceneid: String, violation: String, nodeid: String = ""): Nothing = {
    val node = Option(nodeid).filter(_.nonEmpty).map(x => s" node $x").getOrElse("")
    RAISE.invalidArgumentFault(s"Diagram scene $sceneid$node: $violation")
  }

  private[video] def _copy_character_dialogue_assets(projectroot: Path, part: VideoPartPlan, script: VideoScript, workdir: Path, stagevisuals: Boolean = true): CharacterDialogueAssets = {
    val scriptpath = part.scriptPath.getOrElse(RAISE.invalidArgumentFault(s"Part has no script path: ${part.id}"))
    val bases = _character_asset_bases(projectroot, scriptpath)
    val characters = script.characters.toVector.map { case (id, character) =>
      val staged = Vector("asset", "mouthClosedAsset", "mouthOpenAsset").foldLeft(character) { (z, field) =>
        _json_string(z, field).map { authored =>
          z.mapObject(_.add(field, Json.fromString(_stage_character_dialogue_asset(projectroot, workdir, bases, authored, "characters", s"character $id", field))))
        }.getOrElse(z)
      }
      id -> staged
    }
    val visuals = if (stagevisuals) script.expandedScenes.map { scene =>
      val staged = _json_string(scene.visual, "image").map { authored =>
        scene.visual.mapObject(_.add("image", Json.fromString(_stage_character_dialogue_asset(projectroot, workdir, bases, authored, "visuals", s"scene ${scene.id.getOrElse("(no id)")}", "visual.image"))))
      }.getOrElse(scene.visual)
      scene.id.getOrElse("") -> staged
    }.toMap else Map.empty[String, Json]
    CharacterDialogueAssets(Json.obj(characters: _*), visuals)
  }

  private[video] def _stage_web_demo_recording(projectroot: Path, part: VideoPartPlan, workdir: Path): String = {
    val configured = part.recordDir.getOrElse(RAISE.invalidArgumentFault(s"Web-demo part ${part.id} has no record directory"))
    val directory = _project_contained_existing_path(projectroot, configured, s"Web-demo part ${part.id} recordDir")
    val candidates =
      if (Files.isDirectory(directory) && Files.isReadable(directory)) {
        val stream = Files.list(directory)
        try stream.iterator().asScala.flatMap { path =>
          val name = path.getFileName.toString.toLowerCase
          if (name.endsWith(".webm") || name.endsWith(".mp4")) {
            val source = _project_contained_existing_path(projectroot, path, s"Web-demo part ${part.id} recording")
            if (Files.isRegularFile(source) && Files.isReadable(source)) Some(source) else None
          } else None
        }.toVector.sortBy(_.getFileName.toString)
        finally stream.close()
      } else Vector.empty
    if (candidates.size != 1)
      RAISE.invalidArgumentFault(s"Web-demo part ${part.id} requires exactly one readable .webm or .mp4 in $directory")
    val source = candidates.head
    val targetdir = workdir.resolve("public/recording")
    Files.createDirectories(targetdir)
    val target = targetdir.resolve("recording" + _asset_extension(source))
    Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING)
    s"recording/${target.getFileName}"
  }

  private[video] def _character_asset_bases(projectroot: Path, scriptpath: Path): Vector[Path] = {
    val parent = Option(scriptpath.getParent).getOrElse(projectroot).toAbsolutePath.normalize()
    val locale = if (parent.getFileName.toString == "dialogue") Option(parent.getParent).getOrElse(parent) else parent
    Vector(locale, parent, projectroot.toAbsolutePath.normalize()).distinct
  }

  private[video] def _stage_character_dialogue_asset(projectroot: Path, workdir: Path, bases: Vector[Path], authored: String, directory: String, owner: String, field: String): String = {
    val authoredpath = Try(Paths.get(authored)).getOrElse(RAISE.invalidArgumentFault(s"Invalid $field asset for $owner: $authored"))
    if (authoredpath.isAbsolute)
      RAISE.invalidArgumentFault(s"Absolute $field asset is not allowed for $owner: $authored")
    if (authoredpath.iterator().asScala.exists(_.toString == ".."))
      RAISE.invalidArgumentFault(s"Traversal outside admitted asset bases for $owner $field: $authored")
    val project = projectroot.toAbsolutePath.normalize()
    val source = bases.iterator.flatMap { base =>
      val path = base.resolve(authoredpath).normalize()
      if (!path.startsWith(project))
        RAISE.invalidArgumentFault(s"Traversal outside project root for $owner $field: $authored")
      if (Files.exists(path) || Files.isSymbolicLink(path)) {
        val contained = _project_contained_existing_path(projectroot, path, s"$field asset for $owner")
        if (Files.isRegularFile(contained) && Files.isReadable(contained)) Some(contained) else None
      } else None
    }.toVector.headOption.getOrElse(RAISE.invalidArgumentFault(s"Missing or unreadable $field asset for $owner: $authored"))
    val targetdir = workdir.resolve("public").resolve(directory)
    Files.createDirectories(targetdir)
    val target = targetdir.resolve(_file_segment_id(owner.replace(' ', '-'), "asset owner") + "-" + _file_segment_id(field.replace('.', '-'), "asset field") + _asset_extension(source))
    Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING)
    s"$directory/${target.getFileName}"
  }

  private[video] def _resolve_project_relative_path(projectroot: Path, authored: String, owner: String): Path = {
    val authoredpath = Try(Paths.get(authored)).getOrElse(RAISE.invalidArgumentFault(s"Invalid $owner path: $authored"))
    if (authoredpath.isAbsolute)
      RAISE.invalidArgumentFault(s"Absolute $owner path is not allowed: $authored")
    if (authoredpath.iterator().asScala.exists(_.toString == ".."))
      RAISE.invalidArgumentFault(s"Traversal outside project root is not allowed for $owner: $authored")
    val root = projectroot.toAbsolutePath.normalize()
    val path = root.resolve(authoredpath).normalize()
    if (!path.startsWith(root))
      RAISE.invalidArgumentFault(s"Traversal outside project root is not allowed for $owner: $authored")
    path
  }

  private[video] def _project_contained_existing_path(projectroot: Path, path: Path, owner: String): Path = {
    val root = projectroot.toAbsolutePath.normalize()
    val candidate = path.toAbsolutePath.normalize()
    if (!candidate.startsWith(root))
      RAISE.invalidArgumentFault(s"$owner is outside project root: $path")
    val realroot = Try(root.toRealPath()).getOrElse(RAISE.invalidArgumentFault(s"Missing or unreadable project root for $owner: $root"))
    val realcandidate = Try(candidate.toRealPath()).getOrElse(RAISE.invalidArgumentFault(s"Missing or unreadable $owner: $path"))
    if (!realcandidate.startsWith(realroot))
      RAISE.invalidArgumentFault(s"$owner resolves outside project root: $path")
    realcandidate
  }

  private[video] def _copy_remotion_audio(workdir: Path, audio: VideoAudioInput): Unit = {
    val audiodir = workdir.resolve("public/audio")
    Files.createDirectories(audiodir)
    audio.files.foreach { file =>
      Files.copy(file, audiodir.resolve(file.getFileName), StandardCopyOption.REPLACE_EXISTING)
    }
  }

  private[video] def _copy_remotion_assets(
    workdir: Path,
    assets: Vector[CozyVideoAssets.Resolved]
  ): Vector[(CozyVideoAssets.Resolved, Option[String])] = {
    val assetdir = workdir.resolve("public/assets")
    assets.map { asset =>
      if (Files.isRegularFile(asset.path) && Files.isReadable(asset.path)) {
        Files.createDirectories(assetdir)
        val target = assetdir.resolve(asset.role.key + _asset_extension(asset.path))
        Files.copy(asset.path, target, StandardCopyOption.REPLACE_EXISTING)
        asset -> Some(s"assets/${target.getFileName}")
      } else {
        asset -> None
      }
    }
  }

  private[video] def _asset_extension(path: Path): String = {
    val name = path.getFileName.toString
    val index = name.lastIndexOf('.')
    if (index >= 0 && name.substring(index).matches("\\.[A-Za-z0-9]+"))
      name.substring(index).toLowerCase
    else
      ""
  }

  private[video] def _write_simple_java2d_workspace(
    plan: VideoPlan,
    part: VideoPartPlan,
    script: VideoScript,
    audio: VideoSimpleJava2dInput,
    workdir: Path
  ): Unit = {
    Files.createDirectories(workdir)
    val propsjson = _simple_java2d_props_json(plan, part, script, audio, workdir)
    Files.writeString(workdir.resolve("props.json"), propsjson.spaces2, StandardCharsets.UTF_8)
    Files.writeString(workdir.resolve("render_frame.py"), _simple_java2d_render_frame_py, StandardCharsets.UTF_8)
  }

  private[video] def _run_simple_java2d_frame(
    projectroot: Path,
    execution: VideoExecutionConfig,
    part: VideoPartPlan,
    workdir: Path,
    runner: VideoProcessRunner
  ): Unit = {
    val script = workdir.resolve("render_frame.py")
    val args =
      execution.toolMode match {
        case VideoToolMode.Docker =>
          Vector(
            "docker",
            "run",
            "--rm",
            "-v",
            s"${projectroot}:/workspace",
            "-w",
            "/workspace",
            execution.dockerImage,
            "python3",
            _docker_path(projectroot, script)
          )
        case VideoToolMode.Host =>
          Vector("python3", script.toString)
        case VideoToolMode.ExternalService =>
          RAISE.invalidArgumentFault("simple-java2d render cannot use external-service tool mode")
      }
    val result = runner.run(args, projectroot)
    if (!result.isSuccess)
      RAISE.invalidArgumentFault(s"simple-java2d frame render failed for part ${part.id}: ${result.stderr.trim}")
  }

  private[video] def _run_simple_java2d_ffmpeg(
    projectroot: Path,
    execution: VideoExecutionConfig,
    part: VideoPartPlan,
    frame: Path,
    audiofile: Path,
    runner: VideoProcessRunner
  ): Unit = {
    Files.createDirectories(part.outputPath.getParent)
    val baseargs = Vector(
      "-y",
      "-loop",
      "1",
      "-i",
      frame.toString,
      "-i",
      audiofile.toString,
      "-c:v",
      "libx264",
      "-tune",
      "stillimage",
      "-c:a",
      "aac",
      "-shortest",
      "-pix_fmt",
      "yuv420p",
      part.outputPath.toString
    )
    val args =
      execution.toolMode match {
        case VideoToolMode.Docker =>
          Vector(
            "docker",
            "run",
            "--rm",
            "-v",
            s"${projectroot}:/workspace",
            "-w",
            "/workspace",
            execution.dockerImage,
            "ffmpeg"
          ) ++ baseargs.map(x => if (x.startsWith(projectroot.toString)) _docker_path(projectroot, Path.of(x)) else x)
        case VideoToolMode.Host =>
          Vector("ffmpeg") ++ baseargs
        case VideoToolMode.ExternalService =>
          RAISE.invalidArgumentFault("simple-java2d ffmpeg encode cannot use external-service tool mode")
      }
    val result = runner.run(args, projectroot)
    if (!result.isSuccess)
      RAISE.invalidArgumentFault(s"simple-java2d ffmpeg encode failed for part ${part.id}: ${result.stderr.trim}")
  }

  private[video] def _run_remotion(
    projectroot: Path,
    execution: VideoExecutionConfig,
    part: VideoPartPlan,
    workdir: Path,
    runner: VideoProcessRunner
  ): Unit = {
    Option(part.outputPath.getParent).foreach(Files.createDirectories(_))
    val script = workdir.resolve("src/render.mjs")
    val stagedoutput = _remotion_staged_output(workdir)
    Files.deleteIfExists(stagedoutput)
    val args =
      execution.toolMode match {
        case VideoToolMode.Docker =>
          Vector(
            "docker",
            "run",
            "--rm",
            "-v",
            s"${projectroot}:/workspace",
            "-w",
            "/workspace",
            execution.dockerImage,
            "node",
            _docker_path(projectroot, script)
          )
        case VideoToolMode.Host =>
          Vector("node", script.toString)
        case VideoToolMode.ExternalService =>
          RAISE.invalidArgumentFault("Remotion render cannot use external-service tool mode")
      }
    val result = runner.run(args, projectroot)
    if (!result.isSuccess)
      RAISE.invalidArgumentFault(s"Remotion render failed for part ${part.id}: ${result.stderr.trim}")
    if (!Files.isRegularFile(stagedoutput))
      RAISE.invalidArgumentFault(s"Remotion render did not create staged output for part ${part.id}: $stagedoutput")
    Files.copy(stagedoutput, part.outputPath, StandardCopyOption.REPLACE_EXISTING)
  }

  private[video] def _write_part_manifest(
    plan: VideoPlan,
    part: VideoPartPlan,
    audio: VideoAudioInput,
    renderer: String,
    workdir: Path,
    workdirfield: String,
    extra: Vector[(String, Json)] = Vector.empty
  ): Unit = {
    Files.createDirectories(part.manifestPath.getParent)
    val fields = Vector(
      "partId" -> Json.fromString(part.id),
      "renderer" -> Json.fromString(renderer),
      "scriptPath" -> Json.fromString(part.scriptPath.map(_.toString).getOrElse("")),
      "audioManifestPath" -> Json.fromString(audio.manifestPath.toString),
      "outputPath" -> Json.fromString(part.outputPath.toString),
      "sceneCount" -> Json.fromInt(part.script.map(_.expandedScenes.size).getOrElse(0)),
      "estimatedDuration" -> Json.fromDoubleOrNull(part.estimatedDuration.getOrElse(0.0)),
      "toolMode" -> Json.fromString(plan.execution.toolMode.label),
      "dockerImage" -> Json.fromString(plan.execution.dockerImage),
      workdirfield -> Json.fromString(workdir.toString)
    ) ++ extra
    val json = Json.obj(fields: _*)
    Files.writeString(part.manifestPath, json.spaces2, StandardCharsets.UTF_8)
  }
}
