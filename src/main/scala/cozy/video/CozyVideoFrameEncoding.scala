package cozy.video

import io.circe.{Json, parser}
import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path, Paths}
import org.goldenport.RAISE
import scala.util.Try
import CozyVideoTools.VideoExecutionConfig

/** Ordinary schema/I/O admission for the renderer-to-encoder handoff. */
/*
 * @since   Oct. 10, 2026
 * @version Oct. 12, 2026
 * @author  ASAMI, Tomoharu
 */
private[cozy] object CozyVideoFrameEncoding {
  final case class FrameAudio(
    width: Int, height: Int, fps: Int, frameCount: Int,
    frames: Vector[Path], audioPath: Path
  )

  def load(workspace: Path, props: Json): FrameAudio = {
    val manifest = workspace.resolve("frame-audio.json")
    if (!Files.isRegularFile(manifest)) RAISE.invalidArgumentFault(s"Missing Cozy frame/audio handoff: $manifest")
    val json = parser.parse(Files.readString(manifest, StandardCharsets.UTF_8)).fold(
      error => RAISE.invalidArgumentFault(s"Invalid Cozy frame/audio JSON: ${error.getMessage}"), identity)
    val cursor = json.hcursor
    if (cursor.get[String]("schema").toOption != Some("cozy.video.frame-audio.v1"))
      RAISE.invalidArgumentFault("Unsupported Cozy frame/audio handoff schema")
    def _positive_(field: String): Int = {
      val value = cursor.get[Int](field).getOrElse(RAISE.invalidArgumentFault(s"Invalid frame/audio $field"))
      if (value <= 0) RAISE.invalidArgumentFault(s"Nonpositive frame/audio $field")
      value
    }
    val width = _positive_("width")
    val height = _positive_("height")
    val fps = _positive_("fps")
    val count = _positive_("frameCount")
    Vector("width" -> width, "height" -> height, "fps" -> fps).foreach { case (field, value) =>
      if (props.hcursor.get[Int](field).toOption != Some(value)) RAISE.invalidArgumentFault(s"Frame/audio $field differs from planned composition")
    }
    if (props.hcursor.downField("timing").get[Int]("totalFrames").toOption != Some(count))
      RAISE.invalidArgumentFault("Frame/audio count differs from planned complete composition")
    val names = cursor.get[Vector[String]]("frames").getOrElse(RAISE.invalidArgumentFault("Missing ordered PNG frame paths"))
    if (names.size != count || names.distinct.size != count)
      RAISE.invalidArgumentFault("Incomplete or duplicate PNG frames in Cozy handoff")
    val frames = names.zipWithIndex.map { case (name, index) =>
      val path = _safe_file(workspace, name)
      val filename = path.getFileName.toString
      val pattern = "frame-([0-9]+)\\.png".r
      filename match {
        case pattern(number) if Try(number.toInt).toOption.contains(index) =>
        case _ => RAISE.invalidArgumentFault(s"Unordered or invalid PNG frame index: $name, expected $index")
      }
      path
    }
    val audio = _safe_file(workspace, cursor.get[String]("audioPath").getOrElse(RAISE.invalidArgumentFault("Missing PCM audio path")))
    if (!audio.getFileName.toString.endsWith(".wav")) RAISE.invalidArgumentFault("Composition PCM audio must be WAV")
    val wave = CozyVideoImplementation._read_wav(audio)
    if (wave.audioformat != 1 || wave.bitspersample != 16 || wave.durationSeconds <= 0)
      RAISE.invalidArgumentFault("Composition audio must contain positive 16-bit PCM")
    FrameAudio(width, height, fps, count, frames, audio)
  }

  def arguments(handoff: FrameAudio, workspace: Path, projectRoot: Path, execution: VideoExecutionConfig, props: Json, output: Path): Vector[String] = {
    val runtime = CozyVideoImplementation
    val list = workspace.resolve("encode-frames.txt")
    val rows = handoff.frames.map { frame =>
      val path = runtime._execution_path(projectRoot, execution, frame)
      s"file '${path.replace("'", "'\\''")}'\nduration ${BigDecimal(1) / BigDecimal(handoff.fps)}"
    }
    // Repeating the last frame gives its duration to the concat demuxer; -frames:v
    // keeps the encoded output at exactly N, including the complete final card.
    val last = runtime._execution_path(projectRoot, execution, handoff.frames.last).replace("'", "'\\''")
    Files.writeString(list, rows.mkString("", "\n", s"\nfile '$last'\n"), StandardCharsets.UTF_8)
    val samples = BigInt(handoff.frameCount) * 48000 / handoff.fps
    val encoder = CozyVideoEncoder.fromProps(execution, props)
    val audiobitrate = CozyVideoQuality.audioBitrateFromProps(props).fold(RAISE.invalidArgumentFault(_), identity)
    val args = Vector("-y", "-f", "concat", "-safe", "0", "-r", handoff.fps.toString, "-i", runtime._execution_path(projectRoot, execution, list),
      "-i", runtime._execution_path(projectRoot, execution, handoff.audioPath),
      "-map", "0:v:0", "-map", "1:a:0", "-r", handoff.fps.toString, "-frames:v", handoff.frameCount.toString
      ) ++ encoder.arguments ++ Vector(
      "-pix_fmt", "yuv420p", "-af", s"aresample=48000:first_pts=0,apad,atrim=end_sample=$samples,asetpts=PTS-STARTPTS",
      "-ar", "48000", "-ac", "2", "-c:a", "aac", "-b:a", audiobitrate, "-cutoff", "18000",
      "-movie_timescale", "48000", "-video_track_timescale", (handoff.fps * 1000L).toString,
      "-movflags", "+faststart", runtime._execution_path(projectRoot, execution, output))
    runtime._execution_command(projectRoot, execution, "ffmpeg", args,
      mounts = runtime._selected_path_mounts(projectRoot, handoff.frames ++ Vector(handoff.audioPath, list), Vector(output)))
  }

  private def _safe_file(workspace: Path, name: String): Path = {
    val relative = Try(Paths.get(name)).getOrElse(RAISE.invalidArgumentFault(s"Invalid handoff path: $name"))
    if (name.isEmpty || relative.isAbsolute || name.contains("\\") || name.split("/").contains(".."))
      RAISE.invalidArgumentFault(s"Unsafe relative handoff path: $name")
    val root = workspace.toRealPath()
    val path = workspace.resolve(relative).normalize()
    if (!Files.isRegularFile(path) || !Files.isReadable(path) || !path.toRealPath().startsWith(root))
      RAISE.invalidArgumentFault(s"Missing or unsafe handoff file: $name")
    path.toAbsolutePath.normalize()
  }
}
