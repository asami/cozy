package cozy.runtime

import org.goldenport.RAISE
import org.goldenport.cli.spec
import cozy.Cozy
import cozy.archive.{CozyArchivePackager, CozyCarPublisher, CozySarPublisher}
import cozy.publication.{CozyPublicationCompiler, CozySampleDistributor, CozyWarehouseIndexer}
import play.api.libs.json._
import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path}

/*
 * @since   May. 20, 2026
 * @version Jun.  4, 2026
 * @author  ASAMI, Tomoharu
 */
private[cozy] object CozySbtBridge {
  def execute(args: List[String]): Unit =
    args match {
      case "v1" :: rest =>
        _execute_v1(rest)
      case _ =>
        RAISE.invalidArgumentFault("Missing sbt-bridge version. Expected: sbt-bridge v1 --request <file>")
    }

  private def _execute_v1(args: List[String]): Unit = {
    val requestpath = _required_path(args, "request")
    val request = _load_request(requestpath)
    request.action match {
      case "generate" =>
        _run_generation(request.arguments)
      case "package-car" =>
        CozyArchivePackager.buildCar(request.arguments.toList)
      case "package-sar" =>
        CozyArchivePackager.buildSar(request.arguments.toList)
      case "publish-car" =>
        CozyCarPublisher.publish(request.arguments.toList)
      case "publish-sar" =>
        CozySarPublisher.publish(request.arguments.toList)
      case "publish-project" =>
        CozyPublicationCompiler.publish(request.arguments.toList)
      case "unpublish-project" =>
        CozyPublicationCompiler.unpublish(request.arguments.toList)
      case "distribute-samples" =>
        CozySampleDistributor.distribute(request.arguments.toList)
      case "index-warehouse" =>
        CozyWarehouseIndexer.index(request.arguments.toList)
      case other =>
        RAISE.invalidArgumentFault(s"Unsupported sbt-bridge v1 action: $other")
    }
  }

  private def _run_generation(args: Vector[String]): Unit =
    args.toList match {
      case command :: rest =>
        command match {
          case "modeler-scala" =>
            val cozy = Cozy.build(Array.empty)
            cozy.executeDirect((command :: rest).toArray)
          case other =>
            RAISE.invalidArgumentFault(s"Unsupported sbt-bridge generation command: $other")
        }
      case Nil =>
        RAISE.invalidArgumentFault("Missing sbt-bridge generation arguments")
    }

  private def _load_request(path: Path): BridgeRequest = {
    val text = Files.readString(path, StandardCharsets.UTF_8)
    _parse_request_json(text, Some(path))
  }

  private def _parse_request_json(text: String, path: Option[Path]): BridgeRequest =
    Json.parse(text).validate[BridgeRequest] match {
      case JsSuccess(request, _) =>
        if (request.version != "v1")
          RAISE.invalidArgumentFault(s"Unsupported sbt-bridge request version: ${request.version}")
        request
      case JsError(errors) =>
        val detail = errors.map { case (p, xs) => s"${p.toJsonString}: ${xs.map(_.message).mkString(", ")}" }.mkString("; ")
        val location = path.map(p => s"${p.toAbsolutePath.normalize()} ").getOrElse("")
        RAISE.invalidArgumentFault(s"Invalid sbt-bridge request file: ${location}(${detail})")
    }

  private def _required_path(args: List[String], key: String): Path =
    CozyCliArgs.parse(spec.Parameter.propertyFileOption(key))(args).
      pathProperty(key).
      getOrElse(RAISE.invalidArgumentFault(s"Missing --${key}"))

  private[cozy] final case class BridgeRequestView(
    version: String,
    action: String,
    arguments: Vector[String],
    settings: Map[String, String]
  )

  private[cozy] def loadRequestForTest(path: Path): BridgeRequestView = {
    val req = _load_request(path)
    BridgeRequestView(req.version, req.action, req.arguments, req.settings)
  }

  private case class BridgeResponseEnvelope(
    version: String,
    status: String,
    mode: String,
    action: String,
    exitCode: Int,
    message: String
  )

  private[cozy] def renderSuccessEnvelopeForTest(action: String): String =
    Json.prettyPrint(Json.toJson(BridgeResponseEnvelope("v1", "success", "process-exit", action, 0, "Bridge command completed successfully.")))

  private[cozy] def renderErrorEnvelopeForTest(action: String, message: String): String =
    Json.prettyPrint(Json.toJson(BridgeResponseEnvelope("v1", "error", "process-exit", action, 1, message)))

  private implicit val _bridge_response_envelope_format: Format[BridgeResponseEnvelope] = Json.format[BridgeResponseEnvelope]

  private case class BridgeRequest(
    version: String,
    action: String,
    arguments: Vector[String],
    settings: Map[String, String] = Map.empty
  )
  private implicit val _bridge_request_format: Format[BridgeRequest] = new Format[BridgeRequest] {
    def reads(json: JsValue): JsResult[BridgeRequest] =
      for {
        version <- (json \ "version").validate[String]
        action <- (json \ "action").validate[String]
        arguments <- (json \ "arguments").validate[Vector[String]]
        settings <- (json \ "settings").validateOpt[Map[String, String]]
      } yield BridgeRequest(version, action, arguments, settings.getOrElse(Map.empty))

    def writes(p: BridgeRequest): JsValue = Json.obj(
      "version" -> p.version,
      "action" -> p.action,
      "arguments" -> p.arguments,
      "settings" -> p.settings
    )
  }
}
