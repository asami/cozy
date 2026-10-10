package cozy.video

import cozy.config.CozyProjectYamlConfig
import io.circe.Json
import java.nio.file.Path
import org.goldenport.RAISE
import CozyVideoTools.{VideoExecutionConfig, VideoToolMode}
import CozyVideoModel.VideoToolSettings

/** Finite, explicitly authored operating settings; availability is not resolution. */
/*
 * @since   Oct. 10, 2026
 * @version Oct. 11, 2026
 * @author  ASAMI, Tomoharu
 */
private[cozy] object CozyVideoOperatingConfig {
  final case class ToolSelection(
    identity: String,
    mode: VideoToolMode,
    executable: String,
    dockerImage: String,
    origins: Map[String, String]
  ) {
    def toJson: Json = Json.obj(
      "mode" -> Json.fromString(mode.label),
      "executable" -> Json.fromString(executable),
      "dockerImage" -> Json.fromString(dockerImage),
      "origins" -> Json.obj(origins.toVector.sortBy(_._1).map { case (key, value) => key -> Json.fromString(value) }: _*)
    )
  }

  val commands: Map[String, String] = Map(
    "remotion" -> "node", "ffmpeg" -> "ffmpeg", "ffprobe" -> "ffprobe",
    "playwright" -> "node", "python-pillow" -> "python3", "whisper-cpp" -> "whisper-cli",
    "piper" -> "textus-toolchain", "macos-say" -> "say"
  )

  def identity(command: String): String = command match {
    case "node" | "remotion-node" => "remotion"
    case "python3" => "python-pillow"
    case "whisper-cli" => "whisper-cpp"
    case "textus-toolchain" => "piper"
    case "say" => "macos-say"
    case other => other
  }

  def defaultSelection(tool: String, mode: VideoToolMode, image: String): ToolSelection = {
    val id = identity(tool)
    val selectedmode = id match {
      case "macos-say" => VideoToolMode.Host
      case "piper" => VideoToolMode.Docker
      case _ => mode
    }
    ToolSelection(id, selectedmode, commands.getOrElse(id, tool), image,
      Map("mode" -> "legacy", "executable" -> "tool-default", "docker-image" -> "legacy"))
  }

  def resolve(
    projectRoot: Path,
    descriptor: VideoToolSettings,
    cliMode: Option[String],
    cliImage: Option[String],
    baseline: VideoExecutionConfig
  ): VideoExecutionConfig = {
    val layers = CozyProjectYamlConfig.operationDefaultFiles(projectRoot).map(path => path.toString -> CozyProjectYamlConfig.load(path))
    layers.foreach { case (source, config) => _validate_layer(source, config) }
    def _field_(key: String): Option[(String, String)] =
      layers.reverseIterator.flatMap { case (source, config) => config.values.get(key).map(_ -> s"$source:$key") }.toVector.headOption
    def _legacy_(key: String, fallback: String): (String, String) = _field_(key).getOrElse(fallback -> "tool-default")
    val selections = commands.map { case (id, command) =>
      val portable = id != "macos-say" && id != "piper"
      val defaultmode = if (id == "macos-say") "host" else "docker"
      val mode = cliMode.filter(_ => portable).map(_ -> "cli:--tool-mode")
        .orElse(_field_(s"video.tools.$id.mode"))
        .orElse(descriptor.toolMode.filter(_ => portable).map(_ -> "descriptor:tools.toolMode"))
        .getOrElse(if (portable) _legacy_("video.tool-mode", defaultmode) else defaultmode -> "tool-default")
      val image = cliImage.filter(_ => id != "macos-say").map(_ -> "cli:--docker-image")
        .orElse(_field_(s"video.tools.$id.docker-image"))
        .orElse(descriptor.dockerImage.map(_ -> "descriptor:tools.dockerImage"))
        .orElse(_field_("video.docker-image"))
        .orElse(_field_("cozy.docker-image"))
        .getOrElse(VideoToolSettings.DEFAULT_DOCKER_IMAGE -> "tool-default")
      val executable = _field_(s"video.tools.$id.executable").getOrElse(command -> "tool-default")
      val parsedmode = VideoToolMode.parse(_nonblank(mode._1, s"$id.mode"))
      if ((id == "macos-say" && parsedmode != VideoToolMode.Host) || (id == "piper" && parsedmode != VideoToolMode.Docker))
        RAISE.invalidArgumentFault(s"Unsupported explicitly selected mode for $id: ${parsedmode.label}")
      id -> ToolSelection(id, parsedmode, _executable(executable._1, id), _nonblank(image._1, s"$id.docker-image"),
        Map("mode" -> mode._2, "executable" -> executable._2, "docker-image" -> image._2))
    }
    val encoder = _field_("video.encoder.name")
    val bitrate = layers.reverseIterator.flatMap { case (source, config) =>
      config.json.flatMap(_.hcursor.downField("video").downField("encoder").downField("bitrate").focus)
        .map(value => CozyVideoEncoder.normalizeBitrate(value) -> s"$source:video.encoder.bitrate")
    }.toVector.headOption
    val resolved = baseline.copy(selections = selections,
      encoderName = encoder.map(value => CozyVideoEncoder.validateName(value._1)).getOrElse("libx264"),
      encoderOrigin = encoder.map(_._2).getOrElse("tool-default"), encoderExplicit = encoder.isDefined,
      encoderBitrate = bitrate.map(_._1), encoderBitrateOrigin = bitrate.map(_._2).getOrElse("profile-default"))
    CozyVideoEncoder.validateExecution(resolved)
    resolved
  }

  private def _nonblank(value: String, field: String): String = {
    if (value.trim.isEmpty) RAISE.invalidArgumentFault(s"Blank explicitly authored video setting: $field")
    value.trim
  }

  private def _executable(value: String, tool: String): String = {
    _nonblank(value, s"$tool.executable")
    if (value.exists(Character.isISOControl)) RAISE.invalidArgumentFault(s"Control character in $tool executable")
    value
  }

  private def _validate_layer(source: String, config: CozyProjectYamlConfig.Config): Unit = {
    config.json.flatMap(_.hcursor.downField("video").downField("tools").focus).foreach { tools =>
      val entries = tools.asObject.getOrElse(RAISE.invalidArgumentFault(s"video.tools must be an object: $source"))
      entries.toVector.foreach { case (id, settings) =>
        if (!commands.contains(id)) RAISE.invalidArgumentFault(s"Unknown video tool $id: $source")
        val fields = settings.asObject.getOrElse(RAISE.invalidArgumentFault(s"video.tools.$id must be an object: $source"))
        fields.toVector.foreach { case (field, value) =>
          if (!Set("mode", "executable", "docker-image").contains(field))
            RAISE.invalidArgumentFault(s"Unknown video tool field $id.$field: $source")
          val text = value.asString.getOrElse(RAISE.invalidArgumentFault(s"video.tools.$id.$field must be a string: $source"))
          _nonblank(text, s"$id.$field")
          if (field == "mode") VideoToolMode.parse(text)
          if (field == "executable") _executable(text, id)
        }
        val mode = fields("mode").flatMap(_.asString).map(VideoToolMode.parse)
        if (mode.contains(VideoToolMode.Host) && fields.contains("docker-image"))
          RAISE.invalidArgumentFault(s"Contradictory same-layer host mode and Docker image for $id: $source")
        if ((id == "macos-say" && mode.exists(_ != VideoToolMode.Host)) || (id == "piper" && mode.exists(_ != VideoToolMode.Docker)))
          RAISE.invalidArgumentFault(s"Unsupported mode for $id: $source")
      }
    }
    config.json.flatMap(_.hcursor.downField("video").downField("encoder").focus).foreach { encoder =>
      val fields = encoder.asObject.getOrElse(RAISE.invalidArgumentFault(s"video.encoder must be an object: $source"))
      fields.toVector.foreach { case (field, value) =>
        field match {
          case "name" =>
            val name = value.asString.getOrElse(RAISE.invalidArgumentFault(s"video.encoder.name must be a string: $source"))
            CozyVideoEncoder.validateName(_nonblank(name, "video.encoder.name"))
          case "bitrate" => CozyVideoEncoder.normalizeBitrate(value)
          case _ => RAISE.invalidArgumentFault(s"Unsupported video.encoder.$field: $source")
        }
      }
    }
  }
}
