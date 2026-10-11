package cozy.video

import io.circe.{Decoder, DecodingFailure, HCursor, Json}
import CozyVideoModel.{ResolvedEncodingSettings, VideoEncodingPolicy, VideoRenderer}

/*
 * @since   Oct. 12, 2026
 * @version Oct. 12, 2026
 * @author  ASAMI, Tomoharu
 */
private[cozy] object CozyVideoQuality {
  sealed trait Profile {
    def name: String
    def fps: Int
    def width: Int
    def height: Int
    def crf: Int
    def x264Preset: Option[String]
    def audioBitrate: String
    def hardwareBitrate: String
    def legacyPolicy: VideoEncodingPolicy
  }

  case object Lightweight extends Profile {
    val name = "lightweight"
    val fps = 12
    val width = 854
    val height = 480
    val crf = 36
    val x264Preset = Some("superfast")
    val audioBitrate = "96k"
    val hardwareBitrate = "1M"
    val legacyPolicy = VideoEncodingPolicy.Lightweight
  }

  case object Standard extends Profile {
    val name = "standard"
    val fps = 18
    val width = 1280
    val height = 720
    val crf = 32
    val x264Preset = None
    val audioBitrate = "128k"
    val hardwareBitrate = "2M"
    val legacyPolicy = VideoEncodingPolicy.Lightweight
  }

  case object Quality extends Profile {
    val name = "quality"
    val fps = 30
    val width = 1280
    val height = 720
    val crf = 23
    val x264Preset = None
    val audioBitrate = "192k"
    val hardwareBitrate = "6M"
    val legacyPolicy = VideoEncodingPolicy.Standard
  }

  case object HighQuality extends Profile {
    val name = "high-quality"
    val fps = 30
    val width = 1920
    val height = 1080
    val crf = 18
    val x264Preset = None
    val audioBitrate = "320k"
    val hardwareBitrate = "12M"
    val legacyPolicy = VideoEncodingPolicy.Quality
  }

  val values: Vector[Profile] = Vector(Lightweight, Standard, Quality, HighQuality)

  def decode(value: String): Option[Profile] = values.find(_.name == value)

  def encodingJson(
    encoding: ResolvedEncodingSettings,
    policyField: String = "policy",
    originsField: String = "origins"
  ): Json = Json.obj(
    policyField -> Json.fromString(encoding.policy.name),
    "fps" -> Json.fromInt(encoding.fps),
    "width" -> Json.fromInt(encoding.width),
    "height" -> Json.fromInt(encoding.height),
    "crf" -> Json.fromInt(encoding.crf),
    "x264Preset" -> encoding.x264Preset.map(Json.fromString).getOrElse(Json.Null),
    "qualityProfile" -> Json.fromString(encoding.effectiveQualityProfile.name),
    "audioBitrate" -> Json.fromString(encoding.audioBitrate),
    originsField -> Json.fromFields(encoding.origins.toVector.sortBy(_._1).map { case (field, source) =>
      field -> Json.fromString(source)
    })
  )

  def audioBitrateFromProps(props: Json): Either[String, String] = {
    val cursor = props.hcursor
    val profile = cursor.downField("qualityProfile").focus
    val admitted = profile match {
      case None => Right(())
      case Some(value) => value.asString.flatMap(decode).map(_ => ()).toRight("Invalid qualityProfile in encoding props")
    }
    admitted.flatMap { _ =>
      cursor.downField("audioBitrate").focus match {
        case Some(value) => normalizeAudioBitrate(value)
        case None if profile.isDefined => Left("Canonical encoding props require audioBitrate")
        case None => Right("320k")
      }
    }
  }

  def fromLegacy(policy: VideoEncodingPolicy): Profile = policy match {
    case VideoEncodingPolicy.Lightweight => Standard
    case VideoEncodingPolicy.Standard => Quality
    case VideoEncodingPolicy.Quality => HighQuality
  }

  def resolve(renderer: Option[VideoRenderer]): ResolvedEncodingSettings =
    resolve(renderer.getOrElse(VideoRenderer(None, None, None, None, None, None, None, None, None)))

  def resolve(renderer: VideoRenderer): ResolvedEncodingSettings = {
    val selected = renderer.qualityProfile.orElse(renderer.policy.map(fromLegacy)).getOrElse(Standard)
    val source = if (renderer.qualityProfile.isDefined) "quality-profile"
      else if (renderer.policy.isDefined) "legacy-policy" else "default"
    val legacy = renderer.qualityProfile.isEmpty && renderer.policy.isDefined
    val origins = Map(
      "qualityProfile" -> source,
      "fps" -> (if (renderer.fps.isDefined) "explicit" else source),
      "width" -> (if (renderer.width.isDefined) "explicit" else source),
      "height" -> (if (renderer.height.isDefined) "explicit" else source),
      "crf" -> (if (renderer.crf.isDefined) "explicit" else source),
      "x264Preset" -> (if (renderer.x264Preset.isDefined) "explicit" else source),
      "audioBitrate" -> (if (renderer.audioBitrate.isDefined) "explicit" else source)
    )
    ResolvedEncodingSettings(
      selected.legacyPolicy,
      renderer.fps.getOrElse(selected.fps),
      renderer.width.getOrElse(selected.width),
      renderer.height.getOrElse(selected.height),
      renderer.crf.getOrElse(selected.crf),
      renderer.x264Preset.orElse(selected.x264Preset),
      Some(selected),
      renderer.audioBitrate.getOrElse(if (legacy) "320k" else selected.audioBitrate),
      origins
    )
  }

  val rendererDecoder: Decoder[VideoRenderer] = (c: HCursor) =>
    for {
      engine <- c.downField("engine").as[Option[String]]
      strategy <- c.downField("strategy").as[Option[String]]
      policy <- c.downField("policy").as[Option[String]].flatMap(_decode_policy(_, c))
      qualityprofile <- _decode_profile(c)
      fps <- c.downField("fps").as[Option[Int]].flatMap(_validate_positive("fps", _, c))
      width <- c.downField("width").as[Option[Int]].flatMap(_validate_positive("width", _, c))
      height <- c.downField("height").as[Option[Int]].flatMap(_validate_positive("height", _, c))
      crf <- c.downField("crf").as[Option[Int]].flatMap(_validate_crf(_, c))
      x264preset <- c.downField("x264Preset").as[Option[String]].flatMap(_validate_x264_preset(_, c))
      effectprofile <- c.downField("effectProfile").as[Option[String]]
      audiobitrate <- _decode_audio_bitrate(c)
    } yield VideoRenderer(engine, strategy, policy, fps, width, height, crf, x264preset, effectprofile, qualityprofile, audiobitrate)

  def normalizeAudioBitrate(value: Json): Either[String, String] = {
    def _positive_long_(number: BigInt, text: String): Either[String, String] =
      if (number <= 0 || number > BigInt(Long.MaxValue))
        Left("renderer.audioBitrate must fit a positive signed 64-bit value")
      else Right(text)

    value.asString match {
      case Some(text) =>
        val trimmed = text.trim
        val pattern = "([1-9][0-9]*)([kM]?)".r
        trimmed match {
          case pattern(digits, suffix) =>
            val scale = suffix match { case "k" => 1000; case "M" => 1000000; case _ => 1 }
            _positive_long_(BigInt(digits) * scale, trimmed)
          case _ => Left(s"Invalid renderer.audioBitrate value: '$text'")
        }
      case None =>
        value.asNumber.flatMap(_.toBigDecimal).flatMap(_.toBigIntExact) match {
          case Some(number) => _positive_long_(number, number.toString)
          case None => Left("renderer.audioBitrate must be a positive integer or bitrate string")
        }
    }
  }

  private def _decode_policy(value: Option[String], cursor: HCursor): Decoder.Result[Option[VideoEncodingPolicy]] =
    value match {
      case Some(name) => VideoEncodingPolicy.decode(name).map(Some(_)).toRight(
        DecodingFailure(s"Invalid renderer.policy value: '$name'", cursor.history)
      )
      case None => Right(None)
    }

  private def _decode_profile(cursor: HCursor): Decoder.Result[Option[Profile]] =
    cursor.downField("qualityProfile").focus match {
      case None => Right(None)
      case Some(_) => cursor.downField("qualityProfile").as[String].flatMap { name =>
        decode(name).map(Some(_)).toRight(DecodingFailure(s"Invalid renderer.qualityProfile value: '$name'", cursor.history))
      }
    }

  private def _decode_audio_bitrate(cursor: HCursor): Decoder.Result[Option[String]] =
    cursor.downField("audioBitrate").focus match {
      case None => Right(None)
      case Some(value) => normalizeAudioBitrate(value).left.map(message => DecodingFailure(message, cursor.history)).map(Some(_))
    }

  private def _validate_positive(field: String, value: Option[Int], cursor: HCursor): Decoder.Result[Option[Int]] =
    value match {
      case Some(v) if v <= 0 => Left(DecodingFailure(s"Invalid renderer.$field value: '$v'", cursor.history))
      case _ => Right(value)
    }

  private def _validate_crf(value: Option[Int], cursor: HCursor): Decoder.Result[Option[Int]] =
    value match {
      case Some(v) if v < 0 => Left(DecodingFailure(s"Invalid renderer.crf value: '$v'", cursor.history))
      case _ => Right(value)
    }

  private def _validate_x264_preset(value: Option[String], cursor: HCursor): Decoder.Result[Option[String]] =
    value match {
      case Some(v) if v.trim.isEmpty || !VideoEncodingPolicy.validX264Presets.contains(v) =>
        Left(DecodingFailure(s"Invalid renderer.x264Preset value: '$v'", cursor.history))
      case _ => Right(value)
    }
}
