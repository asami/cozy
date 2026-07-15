package cozy.runtime

import org.goldenport.RAISE
import org.goldenport.cli.spec
import cozy.Cozy
import cozy.archive.{ComponentApiDependencyResolver, ComponentApiJarPackager, CozyArchivePackager, CozyCarPublisher, CozySarPublisher}
import cozy.config.CozyProjectYamlConfig
import cozy.publication.{CozyPublicationCompiler, CozySampleDistributor, CozyWarehouseIndexer}
import cozy.video.CozyVideoPublisher
import play.api.libs.json._
import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path, Paths}

/*
 * @since   May. 20, 2026
 *  version Jun. 27, 2026
 * @version Jul. 15, 2026
 * @author  ASAMI, Tomoharu
 */
private[cozy] object CozySbtBridge {
  private val _sbt_project_dir_setting = "sbt.project_dir"

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
        _run_generation(request.arguments, request.settings)
      case "package-car" =>
        CozyArchivePackager.buildCar(request.arguments.toList)
      case "component-api-jar" =>
        ComponentApiJarPackager.build(request.arguments.toList)
      case "resolve-component-api-dependencies" =>
        ComponentApiDependencyResolver.resolve(request.arguments.toList)
      case "package-sar" =>
        CozyArchivePackager.buildSar(request.arguments.toList)
      case "publish-car" =>
        CozyCarPublisher.publish(request.arguments.toList)
      case "publish-sar" =>
        CozySarPublisher.publish(request.arguments.toList)
      case "publish-project" =>
        CozyPublicationCompiler.publish(request.arguments.toList)
      case "publish-video" =>
        CozyVideoPublisher.publish(request.arguments.toList)
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

  private def _run_generation(args: Vector[String], settings: Map[String, String]): Unit =
    args.toList match {
      case command :: rest =>
        command match {
          case "modeler-scala" =>
            val cozy = Cozy.build(Array.empty)
            cozy.executeDirect((command :: (rest ++ _modeler_args(settings))).toArray)
          case "car-sbt-project" =>
            val cozy = Cozy.build(Array.empty)
            val config = _generation_config(settings)
            cozy.executeDirect((command :: (rest ++ _version_args(config))).toArray)
          case other =>
            RAISE.invalidArgumentFault(s"Unsupported sbt-bridge generation command: $other")
        }
      case Nil =>
        RAISE.invalidArgumentFault("Missing sbt-bridge generation arguments")
    }

  private def _generation_config(settings: Map[String, String]): CozyProjectYamlConfig.Config = {
    val projectdir = settings.get(_sbt_project_dir_setting).
      map(x => Paths.get(x).toAbsolutePath.normalize()).
      getOrElse(Paths.get(".").toAbsolutePath.normalize())
    val generationsettings = settings - _sbt_project_dir_setting
    CozyProjectYamlConfig.loadOperationDefaults(projectdir).merge(CozyProjectYamlConfig.Config(generationsettings, Map.empty))
  }

  private def _version_args(config: CozyProjectYamlConfig.Config): List[String] = {
    val versions = Cozy.CarDependencyVersions.create(Nil, config)
    val base = List(
      "--cncf-version", versions.cncfVersion,
      "--simplemodeling-model-version", versions.simpleModelingModelVersion,
      "--cncf-collaborator-api-version", versions.cncfCollaboratorApiVersion
    )
    config.value("runtime.cncf.descriptor").map(path => base ++ List("--cncf-runtime-descriptor", path)).getOrElse(base)
  }

  private def _component_api_args(settings: Map[String, String]): List[String] =
    Vector(
      "component.module" -> "--component-module",
      "component.version" -> "--component-version"
    ).flatMap { case (key, option) =>
      settings.get(key).map(value => Vector(option, value)).getOrElse(Vector.empty)
    }.toList

  private def _modeler_args(settings: Map[String, String]): List[String] =
    _component_api_args(settings) ++ _version_args(_generation_config(settings))

  private[cozy] def componentApiArgsForTest(settings: Map[String, String]): List[String] =
    _component_api_args(settings)

  private[cozy] def modelerArgsForSettingsForTest(settings: Map[String, String]): List[String] =
    _modeler_args(settings)

  private[cozy] def versionArgsForTest(settings: Map[String, String], projectdir: Path): List[String] =
    _version_args(_generation_config(settings + (_sbt_project_dir_setting -> projectdir.toString)))

  private[cozy] def versionArgsForSettingsForTest(settings: Map[String, String]): List[String] =
    _version_args(_generation_config(settings))

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
    CozyCliArgs.parse(spec.Parameter.propertyFileOption(key))(_normalize_property(args, key)).
      pathProperty(key).
      getOrElse(RAISE.invalidArgumentFault(s"Missing --${key}"))

  private def _normalize_property(args: List[String], key: String): List[String] = {
    val prefix = s"--$key="
    args.flatMap {
      case x if x.startsWith(prefix) =>
        List(s"--$key", x.drop(prefix.length))
      case x =>
        List(x)
    }
  }

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
