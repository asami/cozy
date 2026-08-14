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
private[cozy] trait CozyVideoReviewEvidence {
  self: CozyVideoTypes with CozyVideoRuntime with CozyVideoCommand with CozyVideoNarration with CozyVideoToolValidation with CozyVideoTranscription with CozyVideoBuildReplay with CozyVideoRdf with CozyVideoRenderWorkspace with CozyVideoRenderTemplates with CozyVideoPlanning with CozyVideoPresentation =>
  private[video] def _write_review_evidence(
    config: ReviewEvidenceConfig,
    plan: VideoPlan,
    runner: VideoProcessRunner
  ): VideoReviewEvidenceResult = {
    val finalvideo = plan.outputPath.toAbsolutePath.normalize()
    if (Files.isSymbolicLink(finalvideo))
      RAISE.invalidArgumentFault(s"Final video for review evidence must not be a symbolic link: $finalvideo")
    if (!Files.isRegularFile(finalvideo, LinkOption.NOFOLLOW_LINKS))
      RAISE.invalidArgumentFault(s"Missing final video for review evidence: $finalvideo")
    val finalhash = _sha256(finalvideo)
    val savedir = config.saveDir.toAbsolutePath.normalize()
    if (savedir.getParent == null)
      RAISE.invalidArgumentFault("Review evidence --save target must not be the filesystem root.")
    if (Files.exists(savedir, LinkOption.NOFOLLOW_LINKS) && Files.isSymbolicLink(savedir))
      RAISE.invalidArgumentFault(s"Review evidence --save target must not be a symbolic link: $savedir")
    if (Files.exists(savedir, LinkOption.NOFOLLOW_LINKS) && !Files.isDirectory(savedir, LinkOption.NOFOLLOW_LINKS))
      RAISE.invalidArgumentFault(s"Review evidence --save target is not a directory: $savedir")
    Files.createDirectories(savedir)
    if (!Files.isDirectory(savedir, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(savedir))
      RAISE.invalidArgumentFault(s"Review evidence --save target is not a directory: $savedir")
    val canonicalfinalvideo = finalvideo.toRealPath()
    val canonicalsavedir = savedir.toRealPath()
    if (canonicalfinalvideo.startsWith(canonicalsavedir))
      RAISE.invalidArgumentFault(s"Review evidence final video must not be inside the --save directory: $canonicalfinalvideo")
    val framesdir = savedir.resolve("frames")
    if (Files.exists(framesdir, LinkOption.NOFOLLOW_LINKS) && Files.isSymbolicLink(framesdir))
      RAISE.invalidArgumentFault(s"Review evidence frames target must not be a symbolic link: $framesdir")
    Files.createDirectories(framesdir)
    if (!Files.isDirectory(framesdir, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(framesdir))
      RAISE.invalidArgumentFault(s"Review evidence frames target is not a directory: $framesdir")
    val manifest = savedir.resolve("review-manifest.json")
    if (Files.exists(manifest, LinkOption.NOFOLLOW_LINKS) && Files.isSymbolicLink(manifest))
      RAISE.invalidArgumentFault(s"Review evidence manifest target must not be a symbolic link: $manifest")
    val videomanifest = _review_validated_video_manifest(plan, finalvideo, finalhash)
    val videomanifesthash = _sha256(videomanifest)
    val dockermounts =
      if (plan.execution.toolMode == VideoToolMode.Docker)
        Some(ReviewEvidenceDockerMounts(
          DockerMount(canonicalfinalvideo, "/review-input/final.mp4", readonly = true),
          DockerMount(canonicalsavedir, "/review-output", readonly = false)
        ))
      else
        None
    val parts = _review_evidence_parts(plan)
    if (parts.isEmpty)
      RAISE.invalidArgumentFault("No renderable Cozy Remotion props found for video review evidence.")
    val renderer = (parts.head.width, parts.head.height, parts.head.fps)
    if (parts.exists(x => (x.width, x.height, x.fps) != renderer))
      RAISE.invalidArgumentFault("Cozy Remotion props disagree on renderer width, height, or fps.")
    val topframes = Vector.newBuilder[Json]
    val partjson = parts.map { evidence =>
      val timing = _review_required_object(evidence.props, "timing", s"Remotion props for ${evidence.part.id}")
      def _timing_int_(name: String): Int = _review_nonnegative_int(timing, name, s"Remotion timing for ${evidence.part.id}")
      val opening = _timing_int_("openingFrames")
      val summary = _timing_int_("summaryFrames")
      val summarystart = _timing_int_("summaryStartFrame")
      val finalpage = _timing_int_("finalPageHoldFrames")
      val finalpagestart = _timing_int_("finalPageStartFrame")
      val scenes = _review_required_array(evidence.props, "scenes", s"Remotion props for ${evidence.part.id}")
      scenes.zipWithIndex.foreach { case (scene, sceneindex) =>
        val sceneid = _review_required_string(scene, "id", s"Remotion scene ${sceneindex + 1} for ${evidence.part.id}")
        val start = _review_nonnegative_int(scene, "startFrame", s"Remotion scene $sceneid")
        val duration = _review_positive_int(scene, "durationFrames", s"Remotion scene $sceneid")
        if (opening.toLong + start.toLong + duration.toLong > evidence.totalframes.toLong)
          RAISE.invalidArgumentFault(s"Remotion scene $sceneid exceeds declared totalFrames.")
      }
      Vector(
        _review_optional_top_frame("opening", evidence, savedir, 0, opening),
        _review_optional_top_frame("summary", evidence, savedir, summarystart, summary),
        _review_optional_top_frame("final-page", evidence, savedir, finalpagestart, finalpage)
      ).flatten.foreach { frame =>
        topframes += _extract_review_frame(plan, finalvideo, savedir, dockermounts, frame, runner)
      }
      val scenejson = scenes.zipWithIndex.map { case (scene, sceneindex) =>
        val sceneid = _review_required_string(scene, "id", s"Remotion scene ${sceneindex + 1} for ${evidence.part.id}")
        val start = _review_nonnegative_int(scene, "startFrame", s"Remotion scene $sceneid")
        val duration = _review_positive_int(scene, "durationFrames", s"Remotion scene $sceneid")
        val leadin = _review_nonnegative_int(scene, "leadInFrames", s"Remotion scene $sceneid")
        val transition = _review_nonnegative_int(scene, "sectionTransitionFrames", s"Remotion scene $sceneid")
        if (leadin >= duration)
          RAISE.invalidArgumentFault(s"Remotion scene $sceneid leadInFrames must be inside durationFrames.")
        val absolute = evidence.startframe + opening + start
        val speech = ReviewEvidenceFrame(
          "speech",
          Some(evidence.part.id),
          Some(sceneid),
          absolute + math.min(duration - 1, leadin),
          evidence.fps,
          savedir.resolve("frames").resolve(_review_scene_frame_name(evidence.part.index, sceneindex, sceneid, "speech"))
        )
        val frames =
          (if (transition > 0) Vector(ReviewEvidenceFrame(
            "transition",
            Some(evidence.part.id),
            Some(sceneid),
            absolute + math.min(duration - 1, math.max(0, transition / 2)),
            evidence.fps,
            savedir.resolve("frames").resolve(_review_scene_frame_name(evidence.part.index, sceneindex, sceneid, "transition"))
          )) else Vector.empty) :+ speech
        val audio = evidence.audioentries.get(sceneid)
        val authored = audio.map { entry =>
          Json.obj(
            "leadSilenceSeconds" -> Json.fromDoubleOrNull(_round3(entry.leadSilence)),
            "audioDurationSeconds" -> Json.fromDoubleOrNull(_round3(entry.audioDuration)),
            "targetDurationSeconds" -> Json.fromDoubleOrNull(_round3(entry.targetDuration)),
            "tailSilenceSeconds" -> Json.fromDoubleOrNull(_round3(entry.tailSilence))
          )
        }.getOrElse(Json.Null)
        Json.obj(
          "index" -> Json.fromInt(sceneindex),
          "order" -> Json.fromInt(sceneindex + 1),
          "partId" -> Json.fromString(evidence.part.id),
          "id" -> Json.fromString(sceneid),
          "speaker" -> _review_optional_string(scene, "speaker"),
          "line" -> _review_optional_string(scene, "line"),
          "text" -> _review_optional_string(scene, "text"),
          "caption" -> _review_optional_string(scene, "caption"),
          "section" -> _review_optional_string(scene, "section"),
          "authoredTiming" -> authored,
          "effectiveTiming" -> Json.obj(
            "durationFrames" -> Json.fromInt(duration),
            "durationSeconds" -> Json.fromDoubleOrNull(_round3(duration.toDouble / evidence.fps)),
            "leadInFrames" -> Json.fromInt(leadin),
            "leadInSeconds" -> Json.fromDoubleOrNull(_round3(leadin.toDouble / evidence.fps)),
            "sectionTransitionFrames" -> Json.fromInt(transition),
            "sectionTransitionSeconds" -> Json.fromDoubleOrNull(_round3(transition.toDouble / evidence.fps))
          ),
          "absoluteTiming" -> Json.obj(
            "startFrame" -> Json.fromInt(absolute),
            "startSeconds" -> Json.fromDoubleOrNull(_round3(absolute.toDouble / evidence.fps)),
            "durationFrames" -> Json.fromInt(duration),
            "durationSeconds" -> Json.fromDoubleOrNull(_round3(duration.toDouble / evidence.fps)),
            "leadInFrames" -> Json.fromInt(leadin),
            "leadInSeconds" -> Json.fromDoubleOrNull(_round3(leadin.toDouble / evidence.fps)),
            "sectionTransitionFrames" -> Json.fromInt(transition),
            "sectionTransitionSeconds" -> Json.fromDoubleOrNull(_round3(transition.toDouble / evidence.fps))
          ),
          "audio" -> audio.map { entry =>
            Json.obj(
              "durationSeconds" -> Json.fromDoubleOrNull(_round3(entry.audioDuration)),
              "tailSilenceSeconds" -> Json.fromDoubleOrNull(_round3(entry.tailSilence))
            )
          }.getOrElse(Json.Null),
          "frames" -> Json.fromValues(frames.map(frame => _extract_review_frame(plan, finalvideo, savedir, dockermounts, frame, runner)))
        )
      }
      Json.obj(
        "index" -> Json.fromInt(evidence.part.index),
        "id" -> Json.fromString(evidence.part.id),
        "startFrame" -> Json.fromInt(evidence.startframe),
        "startSeconds" -> Json.fromDoubleOrNull(_round3(evidence.startframe.toDouble / evidence.fps)),
        "durationFrames" -> Json.fromInt(evidence.totalframes),
        "durationSeconds" -> Json.fromDoubleOrNull(_round3(evidence.totalframes.toDouble / evidence.fps)),
        "propsPath" -> Json.fromString(evidence.propspath.toString),
        "propsSha256" -> Json.fromString(_sha256(evidence.propspath)),
        "audioManifestPath" -> evidence.audiomanifestpath.map(x => Json.fromString(x.toString)).getOrElse(Json.Null),
        "audioManifestSha256" -> evidence.audiomanifestpath.map(x => Json.fromString(_sha256(x))).getOrElse(Json.Null),
        "scenes" -> Json.fromValues(scenejson)
      )
    }
    val topframejson = topframes.result()
    val scenejson = partjson.flatMap { part =>
      part.hcursor.downField("scenes").focus.flatMap(_.asArray).map(_.toVector).getOrElse(Vector.empty)
    }
    val sceneframecount = scenejson.map { scene =>
      scene.hcursor.downField("frames").focus.flatMap(_.asArray).map(_.size).getOrElse(0)
    }.sum
    val json = Json.obj(
      "schema" -> Json.fromString("cozy.video.review-evidence.v1"),
      "status" -> Json.fromString("validated"),
      "projectFile" -> Json.obj(
        "path" -> Json.fromString(plan.projectFile.toString),
        "sha256" -> Json.fromString(_sha256(plan.projectFile))
      ),
      "finalVideo" -> Json.obj(
        "path" -> Json.fromString(finalvideo.toString),
        "sha256" -> Json.fromString(finalhash)
      ),
      "videoManifest" -> Json.obj(
        "path" -> Json.fromString(videomanifest.toString),
        "sha256" -> Json.fromString(videomanifesthash),
        "status" -> Json.fromString("validated")
      ),
      "renderer" -> Json.obj(
        "width" -> Json.fromInt(renderer._1),
        "height" -> Json.fromInt(renderer._2),
        "fps" -> Json.fromInt(renderer._3)
      ),
      "frames" -> Json.fromValues(topframejson),
      "parts" -> Json.fromValues(partjson)
    )
    Files.writeString(manifest, json.spaces2, StandardCharsets.UTF_8)
    if (!Files.isRegularFile(manifest, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(manifest))
      RAISE.invalidArgumentFault(s"Review evidence manifest was not written as a regular file: $manifest")
    VideoReviewEvidenceResult(
      plan.projectFile,
      savedir,
      finalvideo,
      manifest,
      parts.size,
      partjson.map(_.hcursor.downField("scenes").focus.flatMap(_.asArray).map(_.size).getOrElse(0)).sum,
      topframejson.size + sceneframecount,
      plan.execution.toolMode,
      plan.execution.dockerImage
    )
  }

  private[video] def _review_evidence_parts(plan: VideoPlan): Vector[ReviewEvidencePart] = {
    val raw = plan.parts.filter(_.renderable).map { part =>
      val propspath = _remotion_work_dir(plan.projectRoot, part.id).resolve("props.json").normalize()
      if (!Files.isRegularFile(propspath))
        RAISE.invalidArgumentFault(s"Missing current Cozy Remotion props for part ${part.id}: $propspath")
      else {
        val props = parser.parse(Files.readString(propspath, StandardCharsets.UTF_8)).fold(
          e => RAISE.invalidArgumentFault(s"Invalid Cozy Remotion props for part ${part.id}: ${e.getMessage}"),
          identity
        )
        if (_review_required_string(props, "partId", s"Cozy Remotion props for ${part.id}") != part.id)
          RAISE.invalidArgumentFault(s"Cozy Remotion props partId does not match part ${part.id}: $propspath")
        val fps = _review_positive_int(props, "fps", s"Cozy Remotion props for ${part.id}")
        val width = _review_positive_int(props, "width", s"Cozy Remotion props for ${part.id}")
        val height = _review_positive_int(props, "height", s"Cozy Remotion props for ${part.id}")
        val timing = _review_required_object(props, "timing", s"Cozy Remotion props for ${part.id}")
        val totalframes = _review_positive_int(timing, "totalFrames", s"Cozy Remotion timing for ${part.id}")
        val scenes = _review_required_array(props, "scenes", s"Cozy Remotion props for ${part.id}")
        if (scenes.isEmpty)
          RAISE.invalidArgumentFault(s"Cozy Remotion props have no scenes for part ${part.id}: $propspath")
        val (audiopath, audioentries) = _review_audio_manifest(part)
        val propids = scenes.zipWithIndex.map { case (scene, index) =>
          _review_required_string(scene, "id", s"Cozy Remotion scene ${index + 1} for ${part.id}")
        }
        if (propids.distinct.size != propids.size)
          RAISE.invalidArgumentFault(s"Cozy Remotion props have duplicate scene ids for part ${part.id}.")
        if (audioentries.keySet != propids.toSet)
          RAISE.invalidArgumentFault(s"Audio manifest scene ids do not match Cozy Remotion props for part ${part.id}.")
        scenes.zipWithIndex.foreach { case (scene, index) =>
          val sceneid = propids(index)
          val duration = _review_positive_int(scene, "durationFrames", s"Remotion scene $sceneid")
          val leadin = _review_nonnegative_int(scene, "leadInFrames", s"Remotion scene $sceneid")
          val transition = _review_nonnegative_int(scene, "sectionTransitionFrames", s"Remotion scene $sceneid")
          val audio = audioentries(sceneid)
          val expectedduration = _review_timing_frames(
            math.max(audio.targetDuration, audio.leadSilence + audio.audioDuration + audio.tailSilence),
            fps,
            1L,
            transition,
            s"durationFrames for Remotion scene $sceneid"
          )
          val expectedleadin = _review_timing_frames(
            audio.leadSilence,
            fps,
            0L,
            transition,
            s"leadInFrames for Remotion scene $sceneid"
          )
          if (duration.toLong != expectedduration || leadin.toLong != expectedleadin)
            RAISE.invalidArgumentFault(s"Cozy Remotion timing does not match audio manifest for scene $sceneid in part ${part.id}.")
        }
        ReviewEvidencePart(part, propspath, props, audiopath, audioentries, 0, totalframes, fps, width, height)
      }
    }
    var start = 0
    raw.map { part =>
      val result = part.copy(startframe = start)
      start += part.totalframes
      result
    }
  }

  private[video] def _review_audio_manifest(part: VideoPartPlan): (Option[Path], Map[String, VideoAudioManifestEntry]) =
    part.audioDir match {
      case None => (None, Map.empty)
      case Some(directory) =>
        val path = directory.resolve("manifest.json").normalize()
        if (!Files.isRegularFile(path))
          RAISE.invalidArgumentFault(s"Missing audio manifest evidence for part ${part.id}: $path")
        val entries = parser.decode[Vector[VideoAudioManifestEntry]](Files.readString(path, StandardCharsets.UTF_8)).fold(
          e => RAISE.invalidArgumentFault(s"Invalid audio manifest evidence for part ${part.id}: ${e.getMessage}"),
          identity
        )
        if (entries.exists(x => x.leadSilence < 0 || x.audioDuration < 0 || x.targetDuration < 0 || x.tailSilence < 0))
          RAISE.invalidArgumentFault(s"Audio manifest evidence has negative timing for part ${part.id}: $path")
        val byid = entries.map(x => x.sceneId -> x).toMap
        if (byid.size != entries.size || byid.keys.exists(_.trim.isEmpty))
          RAISE.invalidArgumentFault(s"Audio manifest evidence has duplicate or empty scene ids for part ${part.id}: $path")
        (Some(path), byid)
    }

  private[video] def _review_required_object(json: Json, field: String, label: String): Json =
    json.hcursor.downField(field).focus.filter(_.isObject).getOrElse(
      RAISE.invalidArgumentFault(s"Missing or invalid $field in $label.")
    )

  private[video] def _review_required_array(json: Json, field: String, label: String): Vector[Json] =
    json.hcursor.downField(field).focus.flatMap(_.asArray).map(_.toVector).getOrElse(
      RAISE.invalidArgumentFault(s"Missing or invalid $field in $label.")
    )

  private[video] def _review_required_string(json: Json, field: String, label: String): String =
    json.hcursor.get[String](field).toOption.map(_.trim).filter(_.nonEmpty).getOrElse(
      RAISE.invalidArgumentFault(s"Missing or invalid $field in $label.")
    )

  private[video] def _review_optional_string(json: Json, field: String): Json =
    json.hcursor.get[String](field).toOption.map(Json.fromString).getOrElse(Json.Null)

  private[video] def _review_nonnegative_int(json: Json, field: String, label: String): Int = {
    val value = json.hcursor.get[Int](field).toOption.getOrElse(
      RAISE.invalidArgumentFault(s"Missing or invalid $field in $label.")
    )
    if (value < 0)
      RAISE.invalidArgumentFault(s"$field must be non-negative in $label.")
    value
  }

  private[video] def _review_positive_int(json: Json, field: String, label: String): Int = {
    val value = _review_nonnegative_int(json, field, label)
    if (value == 0)
      RAISE.invalidArgumentFault(s"$field must be positive in $label.")
    value
  }

  private[video] def _review_timing_frames(
    seconds: Double,
    fps: Int,
    minimum: Long,
    transition: Int,
    label: String
  ): Long = {
    val scaled = seconds * fps.toDouble
    if (!java.lang.Double.isFinite(scaled) || scaled > Long.MaxValue.toDouble)
      RAISE.invalidArgumentFault(s"$label is outside the supported frame range.")
    val rounded = math.max(minimum, math.round(scaled))
    if (rounded > Long.MaxValue - transition.toLong)
      RAISE.invalidArgumentFault(s"$label is outside the supported frame range.")
    rounded + transition.toLong
  }

  private[video] def _review_validated_video_manifest(plan: VideoPlan, finalvideo: Path, finalhash: String): Path = {
    val manifest = plan.manifestPath.toAbsolutePath.normalize()
    if (Files.isSymbolicLink(manifest) || !Files.isRegularFile(manifest, LinkOption.NOFOLLOW_LINKS))
      RAISE.invalidArgumentFault(s"Missing validated project video manifest for review evidence: $manifest")
    val json = parser.parse(Files.readString(manifest, StandardCharsets.UTF_8)).fold(
      e => RAISE.invalidArgumentFault(s"Invalid project video manifest for review evidence: ${e.getMessage}"),
      identity
    )
    if (_review_required_string(json, "status", "project video manifest") != "validated")
      RAISE.invalidArgumentFault(s"Project video manifest is not validated: $manifest")
    val output = Paths.get(_review_required_string(json, "outputPath", "project video manifest"))
    val manifestoutput = (if (output.isAbsolute) output else plan.projectRoot.resolve(output)).toAbsolutePath.normalize()
    if (manifestoutput != finalvideo)
      RAISE.invalidArgumentFault(s"Project video manifest outputPath does not match final video: $manifest")
    if (_review_required_string(json, "finalVideoSha256", "project video manifest") != finalhash)
      RAISE.invalidArgumentFault(s"Project video manifest finalVideoSha256 does not match final video: $manifest")
    manifest
  }

  private[video] def _review_optional_top_frame(
    kind: String,
    part: ReviewEvidencePart,
    savedir: Path,
    start: Int,
    duration: Int
  ): Option[ReviewEvidenceFrame] =
    if (duration <= 0)
      None
    else {
      if (start < 0 || start + duration > part.totalframes)
        RAISE.invalidArgumentFault(s"Remotion $kind timing is outside totalFrames for part ${part.part.id}.")
      Some(ReviewEvidenceFrame(
        kind,
        Some(part.part.id),
        None,
        part.startframe + start + math.min(duration - 1, duration / 2),
        part.fps,
        savedir.resolve("frames").resolve(f"$kind-part-${part.part.index}%03d-${_review_file_segment(part.part.id)}.png")
      ))
    }

  private[video] def _review_scene_frame_name(partindex: Int, sceneindex: Int, sceneid: String, kind: String): String =
    f"part-$partindex%03d-scene-${sceneindex + 1}%03d-${_review_file_segment(sceneid)}-$kind.png"

  private[video] def _review_file_segment(value: String): String = {
    val normalized = value.trim.replaceAll("[^A-Za-z0-9]+", "-").replaceAll("^-+|-+$", "")
    if (normalized.isEmpty) "scene" else normalized
  }

  private[video] def _extract_review_frame(
    plan: VideoPlan,
    finalvideo: Path,
    savedir: Path,
    dockermounts: Option[ReviewEvidenceDockerMounts],
    frame: ReviewEvidenceFrame,
    runner: VideoProcessRunner
  ): Json = {
    val target = frame.path
    val (inputarg, outputarg) = dockermounts.map { mounts =>
      (mounts.finalvideo.containerpath, s"${mounts.savedir.containerpath}/frames/${target.getFileName}")
    }.getOrElse((finalvideo.toString, target.toString))
    Files.deleteIfExists(target)
    val ffmpegargs = Vector(
      "-y",
      "-ss",
      f"${frame.absoluteframe.toDouble / frame.fps}%.3f",
      "-i",
      inputarg,
      "-frames:v",
      "1",
      outputarg
    )
    val args = dockermounts.map { mounts =>
      _review_execution_command(
        plan.execution,
        "ffmpeg",
        ffmpegargs,
        Vector(mounts.finalvideo, mounts.savedir)
      )
    }.getOrElse(_execution_command(plan.projectRoot, plan.execution, "ffmpeg", ffmpegargs))
    val result = runner.run(args, plan.projectRoot)
    if (!result.isSuccess)
      RAISE.invalidArgumentFault(s"ffmpeg review evidence extraction failed for ${frame.kind}: ${result.stderr.trim}")
    if (!Files.isRegularFile(target, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(target))
      RAISE.invalidArgumentFault(s"ffmpeg review evidence extraction did not create PNG: $target")
    Json.obj(
      "kind" -> Json.fromString(frame.kind),
      "status" -> Json.fromString("validated"),
      "partId" -> frame.partid.map(Json.fromString).getOrElse(Json.Null),
      "sceneId" -> frame.sceneid.map(Json.fromString).getOrElse(Json.Null),
      "absoluteFrame" -> Json.fromInt(frame.absoluteframe),
      "absoluteSeconds" -> Json.fromDoubleOrNull(_round3(frame.absoluteframe.toDouble / frame.fps)),
      "path" -> Json.fromString(_review_relative_path(savedir, target)),
      "sha256" -> Json.fromString(_sha256(target))
    )
  }

  private[video] def _review_relative_path(savedir: Path, path: Path): String =
    savedir.relativize(path).toString.replace('\\', '/')
}
