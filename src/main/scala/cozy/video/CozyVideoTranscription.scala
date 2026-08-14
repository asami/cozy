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
private[cozy] trait CozyVideoTranscription {
  self: CozyVideoTypes with CozyVideoRuntime with CozyVideoCommand with CozyVideoNarration with CozyVideoToolValidation with CozyVideoReviewEvidence with CozyVideoBuildReplay with CozyVideoRdf with CozyVideoRenderWorkspace with CozyVideoRenderTemplates with CozyVideoPlanning with CozyVideoPresentation =>
  private[video] def _transcribe_video(
    config: TranscribeConfig,
    execution: VideoTranscribeExecution,
    runner: VideoProcessRunner
  ): VideoTranscriptionResult = {
    val input = config.inputVideo.toAbsolutePath.normalize()
    if (!Files.isRegularFile(input))
      RAISE.invalidArgumentFault(s"Missing input video for transcription: $input")
    val savedir = config.saveDir.toAbsolutePath.normalize()
    _validate_transcribe_docker_paths(config.projectRoot, execution, input, savedir)
    Files.createDirectories(savedir)
    val audio = savedir.resolve("audio.wav").normalize()
    val transcript = savedir.resolve("transcript.json").normalize()
    val captions = savedir.resolve("captions.srt").normalize()
    val narration = savedir.resolve("narration.json").normalize()
    val manifest = savedir.resolve("manifest.json").normalize()
    val whisperbase = savedir.resolve("whisper").normalize()
    _run_transcribe_ffmpeg(config.projectRoot, execution, input, audio, runner)
    val whisperversion = _run_whisper_version(config.projectRoot, execution, runner)
    _run_whisper(config.projectRoot, execution, audio, whisperbase, runner)
    val whisperjson = whisperbase.resolveSibling(whisperbase.getFileName.toString + ".json")
    val segments = _read_whisper_segments(whisperjson)
    Files.writeString(transcript, _transcript_json(input, segments).spaces2, StandardCharsets.UTF_8)
    Files.writeString(captions, _srt_text(segments), StandardCharsets.UTF_8)
    Files.writeString(narration, _narration_json(segments).spaces2, StandardCharsets.UTF_8)
    Files.writeString(manifest, _transcription_manifest(config, execution, input, audio, transcript, captions, narration, whisperversion, segments.size).spaces2, StandardCharsets.UTF_8)
    VideoTranscriptionResult(input, savedir, audio, transcript, captions, narration, manifest, execution.toolMode, execution.dockerImage, execution.modelPath, segments.size)
  }

  private[video] def _validate_transcribe_docker_paths(
    projectroot: Path,
    execution: VideoTranscribeExecution,
    input: Path,
    savedir: Path
  ): Unit =
    if (execution.toolMode == VideoToolMode.Docker) {
      if (!input.startsWith(projectroot))
        RAISE.invalidArgumentFault(s"Docker transcription requires input video under project root $projectroot: $input")
      if (!savedir.startsWith(projectroot))
        RAISE.invalidArgumentFault(s"Docker transcription requires --save under project root $projectroot: $savedir")
    }

  private[video] def _run_transcribe_ffmpeg(
    projectroot: Path,
    execution: VideoTranscribeExecution,
    input: Path,
    audio: Path,
    runner: VideoProcessRunner
  ): Unit = {
    val args = _execution_command(projectroot, execution.toVideoExecutionConfig, "ffmpeg", Vector(
      "-y",
      "-i",
      _execution_path(projectroot, execution.toVideoExecutionConfig, input),
      "-vn",
      "-ac",
      "1",
      "-ar",
      "16000",
      _execution_path(projectroot, execution.toVideoExecutionConfig, audio)
    ))
    val result = runner.run(args, projectroot)
    if (!result.isSuccess)
      RAISE.invalidArgumentFault(s"ffmpeg audio extraction failed: ${result.stderr.trim}")
    if (!Files.isRegularFile(audio))
      RAISE.invalidArgumentFault(s"ffmpeg audio extraction did not create output: $audio")
  }

  private[video] def _run_whisper_version(projectroot: Path, execution: VideoTranscribeExecution, runner: VideoProcessRunner): Option[String] = {
    val result = runner.run(_execution_command(projectroot, execution.toVideoExecutionConfig, "whisper-cli", Vector("--version")), projectroot)
    if (result.isSuccess) Some(result.text).filter(_.nonEmpty) else None
  }

  private[video] def _run_whisper(
    projectroot: Path,
    execution: VideoTranscribeExecution,
    audio: Path,
    outputbase: Path,
    runner: VideoProcessRunner
  ): Unit = {
    val args = _execution_command(projectroot, execution.toVideoExecutionConfig, "whisper-cli", Vector(
      "-m",
      _execution_model_path(projectroot, execution),
      "-f",
      _execution_path(projectroot, execution.toVideoExecutionConfig, audio),
      "-oj",
      "-osrt",
      "-of",
      _execution_path(projectroot, execution.toVideoExecutionConfig, outputbase)
    ))
    val result = runner.run(args, projectroot)
    if (!result.isSuccess)
      RAISE.invalidArgumentFault(s"whisper.cpp transcription failed: ${result.stderr.trim}")
  }

  private[video] def _execution_model_path(projectroot: Path, execution: VideoTranscribeExecution): String =
    execution.toolMode match {
      case VideoToolMode.Docker => execution.modelPath
      case VideoToolMode.Host => execution.modelPath
      case VideoToolMode.ExternalService => RAISE.invalidArgumentFault("whisper model path cannot use external-service tool mode")
    }

  private[video] def _read_whisper_segments(path: Path): Vector[VideoTranscriptSegment] = {
    if (!Files.isRegularFile(path))
      RAISE.invalidArgumentFault(s"whisper.cpp did not create JSON transcript: $path")
    val json = parser.parse(Files.readString(path, StandardCharsets.UTF_8)).fold(
      e => RAISE.invalidArgumentFault(s"whisper.cpp returned invalid JSON: ${e.getMessage}"),
      identity
    )
    val segments = _json_array(json, "segments").map { xs =>
      xs.zipWithIndex.map {
        case (x, i) =>
          VideoTranscriptSegment(
            i + 1,
            _json_double(x, "start").getOrElse(0.0),
            _json_double(x, "end").getOrElse(0.0),
            _json_string(x, "text").getOrElse("").trim
          )
      }
    }.orElse {
      _json_array(json, "transcription").map { xs =>
        xs.zipWithIndex.map {
          case (x, i) =>
            val timestamps = x.hcursor.downField("timestamps").focus.getOrElse(Json.obj())
            VideoTranscriptSegment(
              i + 1,
              _parse_timestamp_seconds(_json_string(timestamps, "from").getOrElse("0")),
              _parse_timestamp_seconds(_json_string(timestamps, "to").getOrElse("0")),
              _json_string(x, "text").getOrElse("").trim
            )
        }
      }
    }.getOrElse(Vector.empty)
    if (segments.isEmpty)
      RAISE.invalidArgumentFault(s"whisper.cpp JSON contains no transcript segments: $path")
    segments
  }

  private[video] def _parse_timestamp_seconds(value: String): Double = {
    val normalized = value.trim.replace(',', '.')
    val parts = normalized.split(":").toVector
    try {
      parts match {
        case Vector(h, m, s) => h.toDouble * 3600 + m.toDouble * 60 + s.toDouble
        case Vector(m, s) => m.toDouble * 60 + s.toDouble
        case Vector(s) => s.toDouble
        case _ => 0.0
      }
    } catch {
      case NonFatal(_) => 0.0
    }
  }

  private[video] def _transcript_json(input: Path, segments: Vector[VideoTranscriptSegment]): Json =
    Json.obj(
      "schema" -> Json.fromString("cozy.video.transcript.v1"),
      "source" -> Json.fromString(input.toString),
      "segments" -> Json.fromValues(segments.map(_transcript_segment_json))
    )

  private[video] def _transcript_segment_json(segment: VideoTranscriptSegment): Json =
    Json.obj(
      "index" -> Json.fromInt(segment.index),
      "start" -> Json.fromDoubleOrNull(_round3(segment.start)),
      "end" -> Json.fromDoubleOrNull(_round3(segment.end)),
      "text" -> Json.fromString(segment.text)
    )

  private[video] def _narration_json(segments: Vector[VideoTranscriptSegment]): Json =
    Json.obj(
      "schema" -> Json.fromString("cozy.video.narration-draft.v1"),
      "scenes" -> Json.fromValues(segments.map { segment =>
        Json.obj(
          "id" -> Json.fromString(f"segment-${segment.index}%03d"),
          "narration" -> Json.fromString(segment.text),
          "start" -> Json.fromDoubleOrNull(_round3(segment.start)),
          "end" -> Json.fromDoubleOrNull(_round3(segment.end))
        )
      })
    )

  private[video] def _srt_text(segments: Vector[VideoTranscriptSegment]): String =
    segments.map { segment =>
      Vector(
        segment.index.toString,
        s"${_srt_timestamp(segment.start)} --> ${_srt_timestamp(segment.end)}",
        segment.text,
        ""
      ).mkString("\n")
    }.mkString("\n")

  private[video] def _srt_timestamp(seconds: Double): String = {
    val millis = math.max(0L, math.round(seconds * 1000))
    val h = millis / 3600000
    val m = (millis % 3600000) / 60000
    val s = (millis % 60000) / 1000
    val ms = millis % 1000
    f"$h%02d:$m%02d:$s%02d,$ms%03d"
  }

  private[video] def _transcription_manifest(
    config: TranscribeConfig,
    execution: VideoTranscribeExecution,
    input: Path,
    audio: Path,
    transcript: Path,
    captions: Path,
    narration: Path,
    whisperversion: Option[String],
    segmentcount: Int
  ): Json =
    Json.obj(
      "schema" -> Json.fromString("cozy.video.transcription-manifest.v1"),
      "inputVideo" -> Json.fromString(input.toString),
      "inputSha256" -> Json.fromString(_sha256(input)),
      "audioPath" -> Json.fromString(audio.toString),
      "transcriptPath" -> Json.fromString(transcript.toString),
      "captionsPath" -> Json.fromString(captions.toString),
      "narrationPath" -> Json.fromString(narration.toString),
      "toolMode" -> Json.fromString(execution.toolMode.label),
      "dockerImage" -> Json.fromString(execution.dockerImage),
      "modelPath" -> Json.fromString(execution.modelPath),
      "whisperVersion" -> whisperversion.map(Json.fromString).getOrElse(Json.Null),
      "segmentCount" -> Json.fromInt(segmentcount),
      "commands" -> Json.fromValues(Vector(
        Json.obj("name" -> Json.fromString("extract-audio"), "tool" -> Json.fromString("ffmpeg")),
        Json.obj("name" -> Json.fromString("transcribe"), "tool" -> Json.fromString("whisper-cli"))
      ))
    )

  private[video] def _sha256(path: Path): String = {
    val digest = MessageDigest.getInstance("SHA-256")
    val in = Files.newInputStream(path)
    try {
      val buffer = new Array[Byte](8192)
      var n = in.read(buffer)
      while (n >= 0) {
        if (n > 0)
          digest.update(buffer, 0, n)
        n = in.read(buffer)
      }
    } finally {
      in.close()
    }
    digest.digest().map("%02x".format(_)).mkString
  }
}
