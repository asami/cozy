package cozy.video

import io.circe.Json
import org.goldenport.RAISE
import CozyVideoModel.{ResolvedEncodingSettings, VideoEncodingPolicy}
import CozyVideoTools.{VideoExecutionConfig, VideoToolMode}

/** Shared video-only encoder policy; selection is intent, not native execution evidence. */
/*
 * @since   Oct. 11, 2026
 * @version Oct. 11, 2026
 * @author  ASAMI, Tomoharu
 */
private[cozy] object CozyVideoEncoder {
  final case class EffectiveEncoder(
    name: String,
    bitrate: Option[String],
    bitrateOrigin: String,
    hardwareRequired: Boolean,
    crf: Option[Int],
    x264Preset: Option[String],
    tune: Option[String]
  ) {
    def arguments: Vector[String] =
      Vector("-c:v", name) ++ (if (hardwareRequired)
        Vector("-b:v", bitrate.get, "-allow_sw", "0", "-require_sw", "0")
      else crf.toVector.flatMap(value => Vector("-crf", value.toString)) ++
        x264Preset.toVector.flatMap(value => Vector("-preset", value)) ++
        tune.toVector.flatMap(value => Vector("-tune", value)))

    def toJson: Json = Json.obj(
      "name" -> Json.fromString(name),
      "bitrate" -> bitrate.map(Json.fromString).getOrElse(Json.Null),
      "bitrateOrigin" -> Json.fromString(bitrateOrigin),
      "hardwareRequired" -> Json.fromBoolean(hardwareRequired),
      "crf" -> crf.map(Json.fromInt).getOrElse(Json.Null),
      "x264Preset" -> x264Preset.map(Json.fromString).getOrElse(Json.Null),
      "tune" -> tune.map(Json.fromString).getOrElse(Json.Null)
    )
  }

  def validateName(value: String): String = {
    val name = value.trim
    if (!Set("libx264", "h264_videotoolbox").contains(name))
      RAISE.invalidArgumentFault(s"Unsupported video encoder: $value")
    name
  }

  def normalizeBitrate(value: Json): String = value.asString match {
    case Some(text) =>
      val trimmed = text.trim
      val pattern = "([1-9][0-9]*)([kM]?)".r
      trimmed match {
        case pattern(digits, suffix) =>
          val scale = suffix match { case "k" => 1000; case "M" => 1000000; case _ => 1 }
          _positive_long(BigInt(digits) * scale)
          trimmed
        case _ => RAISE.invalidArgumentFault(s"Invalid video.encoder.bitrate: $text")
      }
    case None =>
      val integer = value.asNumber.flatMap(_.toBigDecimal).flatMap(_.toBigIntExact)
        .getOrElse(RAISE.invalidArgumentFault("video.encoder.bitrate must be a positive integer or bitrate string"))
      _positive_long(integer)
      integer.toString
  }

  def validateExecution(execution: VideoExecutionConfig): Unit = {
    val name = validateName(execution.encoderName)
    execution.encoderBitrate.foreach(value => normalizeBitrate(Json.fromString(value)))
    if (name == "h264_videotoolbox" && execution.selected("ffmpeg").mode != VideoToolMode.Host)
      RAISE.invalidArgumentFault("h264_videotoolbox requires explicitly selected host FFmpeg")
    if (name == "libx264" && execution.encoderBitrate.isDefined)
      RAISE.invalidArgumentFault("video.encoder.bitrate contradicts libx264 selection")
  }

  def resolve(execution: VideoExecutionConfig, encoding: ResolvedEncodingSettings, stillImage: Boolean = false): EffectiveEncoder =
    _resolve(execution, Some(encoding.policy), encoding.crf, encoding.x264Preset, stillImage)

  def fromProps(execution: VideoExecutionConfig, props: Json): EffectiveEncoder = {
    val cursor = props.hcursor
    val hardware = execution.encoderName == "h264_videotoolbox"
    val policy = cursor.get[String]("encodingPolicy").toOption.flatMap(VideoEncodingPolicy.decode)
    if (hardware && policy.isEmpty)
      RAISE.invalidArgumentFault("Hardware encoding requires valid planned encodingPolicy")
    val crf = if (hardware) 0 else cursor.get[Int]("crf")
      .getOrElse(RAISE.invalidArgumentFault("Missing planned software CRF"))
    val preset = if (hardware) None else cursor.get[Option[String]]("x264Preset").getOrElse(None)
    _resolve(execution, policy, crf, preset, false)
  }

  private def _resolve(execution: VideoExecutionConfig, policy: Option[VideoEncodingPolicy], crf: Int, preset: Option[String], stillimage: Boolean): EffectiveEncoder = {
    validateExecution(execution)
    if (execution.encoderName == "h264_videotoolbox") {
      val bitrate = execution.encoderBitrate.map(value => normalizeBitrate(Json.fromString(value))).getOrElse {
        policy.getOrElse(RAISE.invalidArgumentFault("Hardware encoding requires valid planned encodingPolicy")).name match {
          case "lightweight" => "2M"
          case "standard" => "6M"
          case "quality" => "12M"
          case other => RAISE.invalidArgumentFault(s"Unsupported hardware encodingPolicy: $other")
        }
      }
      EffectiveEncoder(execution.encoderName, Some(bitrate),
        if (execution.encoderBitrate.isDefined) execution.encoderBitrateOrigin else "profile-default",
        true, None, None, None)
    } else EffectiveEncoder(execution.encoderName, None, "profile-default", false, Some(crf), preset,
      if (stillimage) Some("stillimage") else None)
  }

  private def _positive_long(value: BigInt): Unit =
    if (value <= 0 || value > BigInt(Long.MaxValue))
      RAISE.invalidArgumentFault("video.encoder.bitrate must fit a positive signed 64-bit value")
}
