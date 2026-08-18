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
 * @version Aug. 19, 2026
 * @author  ASAMI, Tomoharu
 */
private[cozy] trait CozyVideoRdf {
  self: CozyVideoTypes with CozyVideoRuntime with CozyVideoCommand with CozyVideoNarration with CozyVideoToolValidation with CozyVideoTranscription with CozyVideoReviewEvidence with CozyVideoBuildReplay with CozyVideoRenderWorkspace with CozyVideoRenderTemplates with CozyVideoPlanning with CozyVideoPresentation =>
  private[video] def _write_video_rdf(config: RdfConfig, plan: VideoPlan): VideoRdfResult = {
    plan.credits.requireValid()
    val creditfiles = _write_credit_outputs(plan)
    Files.createDirectories(config.saveDir)
    val graph = _video_rdf_graph(plan)
    val turtle = RdfRenderer.toTurtle(graph, _video_rdf_context)
    val jsonld = RdfRenderer.toJsonLD(graph, RdfRenderer.JsonLDProfile.BoK, userContext = _video_rdf_context)
    val turtlefile = config.saveDir.resolve("video.ttl").normalize()
    val jsonldfile = config.saveDir.resolve("video.jsonld").normalize()
    val manifestfile = config.saveDir.resolve("manifest.json").normalize()
    Files.writeString(turtlefile, turtle, StandardCharsets.UTF_8)
    Files.writeString(jsonldfile, jsonld, StandardCharsets.UTF_8)
    val result = VideoRdfResult(
      plan.projectFile,
      config.saveDir,
      turtlefile,
      jsonldfile,
      manifestfile,
      graph.triples.size,
      graph.triples.map(_.subject).distinct.size,
      plan.encoding,
      plan.credits.profileId,
      creditfiles.map(_.digest)
    )
    Files.writeString(manifestfile, _video_rdf_manifest(result).spaces2, StandardCharsets.UTF_8)
    result
  }

  private[video] def _video_rdf_manifest(result: VideoRdfResult): Json =
    Json.obj(
      "projectFile" -> Json.fromString(result.projectFile.toString),
      "namespace" -> Json.fromString(_video_rdf_namespace),
      "turtleFile" -> Json.fromString(result.turtleFile.toString),
      "jsonLdFile" -> Json.fromString(result.jsonLdFile.toString),
      "encoding" -> _encoding_json(result.encoding),
      "creditProfile" -> result.creditProfile.map(Json.fromString).getOrElse(Json.Null),
      "creditDigest" -> result.creditDigest.map(Json.fromString).getOrElse(Json.Null),
      "tripleCount" -> Json.fromInt(result.tripleCount),
      "resourceCount" -> Json.fromInt(result.resourceCount)
    )

  private[video] def _video_rdf_graph(plan: VideoPlan): Rdf.Graph = {
    val projectid = _video_rdf_resource("project", _project_rdf_slug(plan))
    val projectmanifest = _read_optional_json_manifest(plan.manifestPath, "project manifest")
    val projecttriples =
      Vector(
        _rdf_type(projectid, "VideoProject"),
        _rdf_literal(projectid, Vocabulary.Rdfs.label, plan.project.title.orElse(plan.project.name).getOrElse(_basename(plan.projectFile))),
        _rdf_literal(projectid, _schema("name"), plan.project.name.getOrElse(_basename(plan.projectFile))),
        _rdf_literal(projectid, _dcterms("source"), plan.projectFile.toString),
        _rdf_literal(projectid, _cv("path"), plan.projectFile.toString),
        _rdf_literal(projectid, _cv("outputPath"), plan.outputPath.toString),
        _rdf_literal(projectid, _cv("toolMode"), plan.execution.toolMode.label),
        _rdf_literal(projectid, _cv("dockerImage"), plan.execution.dockerImage)
      ) ++ plan.project.title.map(x => _rdf_literal(projectid, _schema("headline"), x)).toVector ++
        _video_encoding_rdf_triples(projectid, plan.encoding) ++
        _video_profile_rdf_triples(projectid, plan) ++
        _video_credit_rdf_triples(projectid, plan) ++
        _artifact_link_triples(projectid, _video_rdf_resource("artifact", "project-output"), "project-output", plan.outputPath, "planned", "project.concat") ++
        _artifact_link_triples(projectid, _video_rdf_resource("artifact", "project-manifest"), "project-manifest", plan.manifestPath, _rdf_file_status(plan.manifestPath), "project.manifest") ++
        projectmanifest.toVector.flatMap(json => _json_field_triples(projectid, json, Vector("toolMode", "dockerImage", "concatListPath"), _cv)) ++
        projectmanifest.flatMap(_.hcursor.downField("ffprobe").focus).toVector.map(json => _rdf_literal(projectid, _cv("ffprobe"), json.noSpaces))

    val parttriples = plan.parts.flatMap(part => _video_part_rdf_triples(projectid, part))
    val futureartifacts = Vector(
      plan.projectRoot.resolve("build/transcript.json").normalize() -> "transcript",
      plan.projectRoot.resolve("build/captions.srt").normalize() -> "caption",
      plan.projectRoot.resolve("build/demo-script.json").normalize() -> "replay-script"
    ).filter(x => Files.exists(x._1)).flatMap {
      case (path, kind) =>
        _artifact_link_triples(projectid, _video_rdf_resource("artifact", kind), kind, path, "present", "future.video")
    }
    val replayscript = plan.projectRoot.resolve("build/demo-script.json").normalize()
    val replaytriples =
      if (Files.isRegularFile(replayscript))
        _replay_rdf_triples(projectid, plan.projectRoot, replayscript)
      else
        Vector.empty
    Rdf.Graph(projecttriples ++ parttriples ++ futureartifacts ++ replaytriples)
  }

  private[video] def _video_encoding_rdf_triples(
    projectid: String,
    encoding: ResolvedEncodingSettings
  ): Vector[Rdf.Triple] =
    Vector(
      _rdf_literal(projectid, _cv("encodingPolicy"), encoding.policy.name),
      _rdf_literal(projectid, _cv("fps"), encoding.fps.toString, Some(_xsd_namespace + "integer")),
      _rdf_literal(projectid, _cv("width"), encoding.width.toString, Some(_xsd_namespace + "integer")),
      _rdf_literal(projectid, _cv("height"), encoding.height.toString, Some(_xsd_namespace + "integer")),
      _rdf_literal(projectid, _cv("crf"), encoding.crf.toString, Some(_xsd_namespace + "integer"))
    ) ++ encoding.x264Preset.toVector.map(x => _rdf_literal(projectid, _cv("x264Preset"), x))

  private[video] def _video_profile_rdf_triples(projectid: String, plan: VideoPlan): Vector[Rdf.Triple] = {
    val profile = plan.project.profile.map(x => _rdf_literal(projectid, _cv("compositionProfile"), x)).toVector
    val effects = CozyVideoEffects.expand(plan.project.visualEffects).flatMap { effect =>
      val effectid = _video_rdf_resource("visual-effect", s"${_project_rdf_slug(plan)}-${effect.role.key}")
      Vector(
        _rdf_uri(projectid, _cv("hasVisualEffect"), effectid),
        _rdf_type(effectid, "VideoVisualEffect"),
        _rdf_literal(effectid, Vocabulary.Rdfs.label, effect.profile),
        _rdf_literal(effectid, _cv("visualEffectRole"), effect.role.key),
        _rdf_literal(effectid, _cv("visualEffectProfile"), effect.profile)
      ) ++ effect.primitives.map(x => _rdf_literal(effectid, _cv("visualEffectPrimitive"), x.display))
    }
    val assets = plan.assets.flatMap { asset =>
      val assetid = _video_rdf_resource("visual-asset", s"${_project_rdf_slug(plan)}-${asset.role.key}")
      Vector(
        _rdf_uri(projectid, _cv("hasVisualAsset"), assetid),
        _rdf_type(assetid, "VideoVisualAsset"),
        _rdf_literal(assetid, Vocabulary.Rdfs.label, asset.role.key),
        _rdf_literal(assetid, _cv("visualAssetRole"), asset.role.key),
        _rdf_literal(assetid, _cv("path"), asset.displayPath(plan.projectRoot)),
        _rdf_literal(assetid, _cv("assetKind"), asset.kind),
        _rdf_literal(assetid, _dcterms("license"), asset.license),
        _rdf_literal(assetid, _cv("provenance"), asset.provenance),
        _rdf_literal(assetid, _cv("status"), asset.status)
      )
    }
    profile ++ effects ++ assets
  }

  private[video] def _video_credit_rdf_triples(projectid: String, plan: VideoPlan): Vector[Rdf.Triple] = {
    val profile = plan.credits.profileId.toVector.flatMap { id =>
      Vector(
        _rdf_literal(projectid, _cv("creditProfile"), id),
        _rdf_literal(projectid, _cv("creditDigest"), plan.credits.digest)
      )
    }
    val items = plan.credits.rdfItems.flatMap { resolved =>
      val item = resolved.item
      val creditid = _video_rdf_resource("credit", item.id)
      Vector(
        _rdf_uri(projectid, _cv("hasCredit"), creditid),
        _rdf_type(creditid, "VideoCredit"),
        _rdf_literal(creditid, Vocabulary.Rdfs.label, item.label(plan.credits.locale).getOrElse(item.id)),
        _rdf_literal(creditid, _cv("creditCategory"), item.category),
        _rdf_literal(creditid, _cv("creditObligation"), item.obligation)
      ) ++ item.creator.map(x => _rdf_literal(creditid, _dcterms("creator"), x)).toVector ++
        item.sourceUrl.map(x => _rdf_uri(creditid, _dcterms("source"), x)).toVector ++
        item.termsUrl.map(x => _rdf_uri(creditid, _dcterms("license"), x)).toVector ++
        resolved.evidence.map(x => _rdf_literal(creditid, _cv("creditEvidence"), x))
    }
    profile ++ items
  }

  private[video] def _replay_rdf_triples(projectid: String, projectroot: Path, scriptpath: Path): Vector[Rdf.Triple] = {
    val script = _load_replay_script(scriptpath)
    val replayid = _video_rdf_resource("replay", _basename(scriptpath))
    val manifestpath = _replay_work_dir(projectroot, scriptpath).resolve("manifest.json").normalize()
    val manifest = _read_optional_json_manifest(manifestpath, "replay manifest")
    val basetriples =
      Vector(
        _rdf_uri(projectid, _cv("hasReplay"), replayid),
        _rdf_type(replayid, "VideoReplay"),
        _rdf_literal(replayid, Vocabulary.Rdfs.label, _basename(scriptpath)),
        _rdf_literal(replayid, _cv("manualReview"), script.manualReview.toString, Some(_xsd_namespace + "boolean")),
        _rdf_literal(replayid, _cv("stepCount"), script.steps.size.toString, Some(_xsd_namespace + "integer"))
      ) ++ script.sourceVideo.map(x => _rdf_literal(replayid, _cv("sourceVideo"), x)).toVector ++
        script.sourceSha256.map(x => _rdf_literal(replayid, _cv("sourceSha256"), x)).toVector ++
        _artifact_link_triples(replayid, _video_rdf_resource("artifact", "replay-manifest"), "replay-manifest", manifestpath, _rdf_file_status(manifestpath), "replay.playwright") ++
        manifest.toVector.flatMap(json => _json_field_triples(replayid, json, Vector("toolMode", "dockerImage", "outputVideo"), _cv))
    val steptriples = script.steps.zipWithIndex.flatMap {
      case (step, index) =>
        val stepid = _video_rdf_resource("replay-step", s"${_basename(scriptpath)}-${index + 1}")
        Vector(
          _rdf_uri(replayid, _cv("hasReplayStep"), stepid),
          _rdf_type(stepid, "VideoReplayStep"),
          _rdf_literal(stepid, Vocabulary.Rdfs.label, s"step-${index + 1}"),
          _rdf_literal(stepid, _cv("stepOrder"), (index + 1).toString, Some(_xsd_namespace + "integer")),
          _rdf_literal(stepid, _cv("stepKind"), step.kind),
          _rdf_literal(stepid, _cv("manualReview"), step.manualReview.toString, Some(_xsd_namespace + "boolean"))
        ) ++ step.url.map(x => _rdf_literal(stepid, _cv("url"), x)).toVector ++
          step.selector.map(x => _rdf_literal(stepid, _cv("selector"), x)).toVector ++
          step.text.map(x => _rdf_literal(stepid, _schema("text"), x)).toVector ++
          step.timestampMs.map(x => _rdf_literal(stepid, _cv("timestampMs"), x.toString, Some(_xsd_namespace + "integer"))).toVector ++
          step.note.map(x => _rdf_literal(stepid, _cv("note"), x)).toVector
    }
    basetriples ++ steptriples
  }

  private[video] def _video_part_rdf_triples(projectid: String, part: VideoPartPlan): Vector[Rdf.Triple] = {
    val partid = _video_rdf_resource("part", part.id)
    val partmanifest = _read_optional_json_manifest(part.manifestPath, s"part manifest ${part.id}")
    val audiomanifestpath = part.audioDir.map(_.resolve("manifest.json").normalize())
    val audioentries = audiomanifestpath.flatMap(path => _read_optional_audio_manifest(path, s"audio manifest ${part.id}"))
    val basetriples =
      Vector(
        _rdf_uri(projectid, _cv("hasPart"), partid),
        _rdf_type(partid, "VideoPart"),
        _rdf_literal(partid, Vocabulary.Rdfs.label, part.id),
        _rdf_literal(partid, _cv("partType"), part.partType),
        _rdf_literal(partid, _cv("renderer"), part.renderer),
        _rdf_literal(partid, _cv("supported"), part.supported.toString, Some(_xsd_namespace + "boolean")),
        _rdf_literal(partid, _cv("outputPath"), part.outputPath.toString)
      ) ++ part.scriptPath.toVector.flatMap { path =>
        _artifact_link_triples(partid, _video_rdf_resource("artifact", s"script-${part.id}"), "script", path, part.scriptStatus, s"part.${part.id}.input")
      } ++ audiomanifestpath.toVector.flatMap { path =>
        _artifact_link_triples(partid, _video_rdf_resource("artifact", s"audio-manifest-${part.id}"), "audio-manifest", path, _rdf_file_status(path), s"part.${part.id}.synthesize")
      } ++
        _artifact_link_triples(partid, _video_rdf_resource("artifact", s"part-output-${part.id}"), "part-output", part.outputPath, "planned", s"part.${part.id}.render") ++
        _artifact_link_triples(partid, _video_rdf_resource("artifact", s"part-manifest-${part.id}"), "part-manifest", part.manifestPath, _rdf_file_status(part.manifestPath), s"part.${part.id}.manifest") ++
        partmanifest.toVector.flatMap(json => _json_field_triples(partid, json, Vector("renderer", "outputPath", "toolMode", "dockerImage"), _cv))
    val scenetriples = part.script.toVector.flatMap { script =>
      script.expandedScenes.zipWithIndex.flatMap {
        case (scene, index) =>
          val sceneid = scene.id.getOrElse(f"scene-${index + 1}%02d")
          val matchingaudio = audioentries.flatMap(_.find(_.sceneId == sceneid))
          _video_scene_rdf_triples(partid, part, scene, sceneid, index + 1, matchingaudio)
      }
    }
    basetriples ++ scenetriples
  }

  private[video] def _video_scene_rdf_triples(
    partid: String,
    part: VideoPartPlan,
    scene: VideoScene,
    sceneid: String,
    index: Int,
    audio: Option[VideoAudioManifestEntry]
  ): Vector[Rdf.Triple] = {
    val sceneuri = _video_rdf_resource("scene", s"${part.id}-$sceneid")
    val utteranceuri = _video_rdf_resource("utterance", s"${part.id}-$sceneid")
    val text = scene.narration.orElse(scene.line).orElse(scene.caption)
    val basetriples = Vector(
      _rdf_uri(partid, _cv("hasScene"), sceneuri),
      _rdf_type(sceneuri, "VideoScene"),
      _rdf_literal(sceneuri, Vocabulary.Rdfs.label, sceneid),
      _rdf_literal(sceneuri, _cv("sceneId"), sceneid),
      _rdf_literal(sceneuri, _cv("sceneOrder"), index.toString, Some(_xsd_namespace + "integer")),
      _rdf_literal(sceneuri, _schema("duration"), scene.durationSeconds.toString, Some(_xsd_namespace + "double")),
      _rdf_uri(sceneuri, _cv("hasUtterance"), utteranceuri),
      _rdf_type(utteranceuri, "VideoUtterance"),
      _rdf_literal(utteranceuri, Vocabulary.Rdfs.label, sceneid)
    ) ++ scene.speaker.map(x => _rdf_literal(utteranceuri, _cv("speaker"), x)).toVector ++
      text.map(x => _rdf_literal(utteranceuri, _schema("text"), x)).toVector
    val audiotriples = audio.toVector.flatMap { entry =>
      val audioartifact = _video_rdf_resource("artifact", s"audio-${part.id}-${entry.sceneId}")
      val audiofile = part.audioDir.map(_.resolve(entry.file).normalize()).getOrElse(Path.of(entry.file))
      Vector(
        _rdf_literal(utteranceuri, _cv("audioDuration"), entry.audioDuration.toString, Some(_xsd_namespace + "double")),
        _rdf_literal(utteranceuri, _cv("targetDuration"), entry.targetDuration.toString, Some(_xsd_namespace + "double")),
        _rdf_literal(utteranceuri, _cv("leadSilence"), entry.leadSilence.toString, Some(_xsd_namespace + "double")),
        _rdf_literal(utteranceuri, _cv("tailSilence"), entry.tailSilence.toString, Some(_xsd_namespace + "double")),
        _rdf_uri(utteranceuri, _schema("encoding"), audioartifact)
      ) ++ entry.speaker.map(x => _rdf_literal(utteranceuri, _cv("speaker"), x)).toVector ++
        entry.provider.map(x => _rdf_literal(utteranceuri, _cv("narrationProvider"), x)).toVector ++
        entry.executionMode.map(x => _rdf_literal(utteranceuri, _cv("narrationExecutionMode"), x)).toVector ++
        entry.voiceIdentity.map(x => _rdf_literal(utteranceuri, _cv("voiceIdentity"), x)).toVector ++
        entry.voiceId.map(x => _rdf_literal(utteranceuri, _cv("voiceId"), x)).toVector ++
        entry.modelIdentity.map(x => _rdf_literal(utteranceuri, _cv("modelIdentity"), x)).toVector ++
        entry.sampleRate.map(x => _rdf_literal(audioartifact, _cv("sampleRate"), x.toString, Some(_xsd_namespace + "integer"))).toVector ++
        entry.channels.map(x => _rdf_literal(audioartifact, _cv("channels"), x.toString, Some(_xsd_namespace + "integer"))).toVector ++
        entry.bitsPerSample.map(x => _rdf_literal(audioartifact, _cv("bitsPerSample"), x.toString, Some(_xsd_namespace + "integer"))).toVector ++
        _artifact_triples(audioartifact, "audio", audiofile, _rdf_file_status(audiofile), s"part.${part.id}.synthesize")
    }
    basetriples ++ audiotriples
  }

  private[video] def _artifact_link_triples(
    owner: String,
    artifactid: String,
    kind: String,
    path: Path,
    status: String,
    producer: String
  ): Vector[Rdf.Triple] =
    Vector(_rdf_uri(owner, _cv("hasArtifact"), artifactid)) ++ _artifact_triples(artifactid, kind, path, status, producer)

  private[video] def _artifact_triples(
    artifactid: String,
    kind: String,
    path: Path,
    status: String,
    producer: String
  ): Vector[Rdf.Triple] = {
    val executionid = _video_rdf_resource("tool-execution", producer)
    Vector(
      _rdf_type(artifactid, "VideoArtifact"),
      _rdf_literal(artifactid, Vocabulary.Rdfs.label, kind),
      _rdf_literal(artifactid, _cv("artifactKind"), kind),
      _rdf_literal(artifactid, _cv("path"), path.toString),
      _rdf_literal(artifactid, _cv("status"), status),
      _rdf_uri(artifactid, _prov("wasGeneratedBy"), executionid),
      _rdf_type(executionid, "VideoToolExecution"),
      _rdf_literal(executionid, Vocabulary.Rdfs.label, producer),
      _rdf_literal(executionid, _cv("stepName"), producer)
    )
  }

  private[video] def _read_optional_json_manifest(path: Path, label: String): Option[Json] =
    if (Files.isRegularFile(path))
      Some(parser.parse(Files.readString(path, StandardCharsets.UTF_8)).fold(
        e => RAISE.invalidArgumentFault(s"Invalid $label JSON: ${e.getMessage}"),
        identity
      ))
    else
      None

  private[video] def _read_optional_audio_manifest(path: Path, label: String): Option[Vector[VideoAudioManifestEntry]] =
    if (Files.isRegularFile(path))
      Some(parser.decode[Vector[VideoAudioManifestEntry]](Files.readString(path, StandardCharsets.UTF_8)).fold(
        e => RAISE.invalidArgumentFault(s"Invalid $label JSON: ${e.getMessage}"),
        identity
      ))
    else
      None

  private[video] def _json_field_triples(
    subject: String,
    json: Json,
    fields: Vector[String],
    predicate: String => String
  ): Vector[Rdf.Triple] =
    fields.flatMap { name =>
      json.hcursor.downField(name).focus.flatMap(_json_scalar_text).map(value => _rdf_literal(subject, predicate(name), value))
    }

  private[video] def _json_scalar_text(json: Json): Option[String] =
    json.asString.
      orElse(json.asNumber.map(_.toString)).
      orElse(json.asBoolean.map(_.toString))

  private[video] def _rdf_file_status(path: Path): String =
    if (Files.exists(path)) "present" else "missing"

  private[video] def _rdf_type(subject: String, localtype: String): Rdf.Triple =
    _rdf_uri(subject, Vocabulary.Rdf.`type`, _cv(localtype))

  private[video] def _rdf_uri(subject: String, predicate: String, obj: String): Rdf.Triple =
    Rdf.Triple(Rdf.Node.Uri(subject), Rdf.Node.Uri(predicate), Rdf.Node.Uri(obj))

  private[video] def _rdf_literal(subject: String, predicate: String, value: String, datatype: Option[String] = None): Rdf.Triple =
    Rdf.Triple(Rdf.Node.Uri(subject), Rdf.Node.Uri(predicate), Rdf.Node.Literal(value, datatype))

  private[video] def _cv(local: String): String =
    _video_rdf_namespace + local

  private[video] def _schema(local: String): String =
    _schema_namespace + local

  private[video] def _dcterms(local: String): String =
    _dcterms_namespace + local

  private[video] def _prov(local: String): String =
    _prov_namespace + local

  private[video] def _video_rdf_resource(kind: String, id: String): String =
    _video_rdf_namespace + _rdf_segment_id(kind, "rdf resource kind") + "/" + _rdf_segment_id(id, s"rdf $kind")

  private[video] def _project_rdf_slug(plan: VideoPlan): String =
    plan.project.name.getOrElse(_basename(plan.projectFile))

  private[video] def _rdf_segment_id(value: String, label: String): String = {
    val normalized = value.trim
    if (normalized.isEmpty || normalized == "." || normalized == ".." || normalized.indexOf(0.toChar) >= 0)
      RAISE.invalidArgumentFault(s"Invalid $label for RDF resource id: $value")
    val bytes = normalized.getBytes(StandardCharsets.UTF_8)
    val b = new StringBuilder
    bytes.foreach { byte =>
      val c = byte & 0xff
      if (
        (c >= 'A' && c <= 'Z') ||
        (c >= 'a' && c <= 'z') ||
        (c >= '0' && c <= '9') ||
        c == '-' || c == '.' || c == '_' || c == '~'
      ) {
        b.append(c.toChar)
      } else {
        b.append('%')
        b.append(_rdf_hex_digits.charAt((c >> 4) & 0x0f))
        b.append(_rdf_hex_digits.charAt(c & 0x0f))
      }
    }
    b.toString
  }
}
