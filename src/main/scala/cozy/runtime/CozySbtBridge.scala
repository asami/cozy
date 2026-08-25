package cozy.runtime

import org.goldenport.RAISE
import org.goldenport.cli.spec
import cozy.Cozy
import cozy.compatibility.{
  CncfRuntimeDescriptorContract,
  GenerationCompatibilityBoundary
}
import cozy.modeler.GenerationProvenance
import cozy.archive.{ComponentApiDependencyResolver, ComponentApiJarPackager, ComponentReleaseSourceProjection, ComponentSourceArchiveProjection, CozyArchivePackager, CozyCarPublisher, CozyDevelopmentRuntimeManifest, CozySarPublisher, SubcomponentReleasePackaging}
import cozy.config.CozyProjectYamlConfig
import cozy.publication.{CozyPublicationCompiler, CozySampleDistributor, CozyWarehouseIndexer}
import cozy.video.CozyVideoPublisher
import play.api.libs.json._
import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path, Paths}

/*
 * @since   May. 20, 2026
 *  version Jun. 27, 2026
 *  version Aug.  8, 2026
 * @version Aug. 20, 2026
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
      case "rebind-generation-provenance" =>
        _rebind_generation_provenance(request.arguments)
      case "prepare-development-runtime-evidence" =>
        _prepare_development_runtime_evidence(request.arguments)
      case "write-component-source-archive" =>
        _write_component_source_archive(request.arguments)
      case "write-component-release-source" =>
        _write_component_release_source(request.arguments)
      case "package-car" =>
        CozyArchivePackager.buildCar(request.arguments.toList)
      case "component-api-jar" =>
        ComponentApiJarPackager.build(request.arguments.toList)
      case "resolve-component-api-dependencies" =>
        ComponentApiDependencyResolver.resolve(request.arguments.toList)
      case "package-sar" =>
        CozyArchivePackager.buildSar(request.arguments.toList)
      case "package-subcomponent-release" =>
        SubcomponentReleasePackaging.packageRelease(request.arguments.toList)
      case "publish-car" =>
        CozyCarPublisher.publish(request.arguments.toList)
      case "publish-sar" =>
        CozySarPublisher.publish(request.arguments.toList)
      case "publish-subcomponent-release" =>
        SubcomponentReleasePackaging.publishRelease(request.arguments.toList)
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
            val modelerargs = rest ++ _modeler_args(settings)
            val descriptorinvocation = command :: modelerargs
            CncfRuntimeDescriptorContract.requireValidInvocation(
              descriptorinvocation,
              "sbt-bridge"
            )
            val invocation =
              command :: (modelerargs ++ _generation_source_identity_args(modelerargs, settings))
            GenerationCompatibilityBoundary.requireValidInvocation(
              invocation,
              "sbt-bridge"
            )
            GenerationProvenance.requireValidInvocation(invocation, "sbt-bridge")
            cozy.executeDirect(invocation.toArray)
          case "car-sbt-project" =>
            val cozy = Cozy.build(Array.empty)
            val config = _generation_config(settings)
            val invocation = command :: (rest ++ _version_args(config))
            GenerationCompatibilityBoundary.requireValidInvocation(
              invocation,
              "sbt-bridge"
            )
            CncfRuntimeDescriptorContract.requireValidInvocation(invocation, "sbt-bridge")
            cozy.executeDirect(invocation.toArray)
          case other =>
            RAISE.invalidArgumentFault(s"Unsupported sbt-bridge generation command: $other")
        }
      case Nil =>
        RAISE.invalidArgumentFault("Missing sbt-bridge generation arguments")
    }

  private def _rebind_generation_provenance(args: Vector[String]): Unit = {
    val delegatedprovenance = _required_path(args.toList, "delegated-provenance")
    val delegatedoutputroot = _required_path(args.toList, "delegated-output-root")
    val projectroot = _required_path(args.toList, "project-root")
    GenerationProvenance.rebindForPackaging(
      delegatedProvenancePath = delegatedprovenance,
      delegatedOutputRoot = delegatedoutputroot,
      projectRoot = projectroot
    )
  }

  private def _prepare_development_runtime_evidence(args: Vector[String]): Unit =
    CozyDevelopmentRuntimeManifest.write(
      projectRoot = _required_path(args.toList, "project-dir"),
      runtimeClasspathFile = _required_path(args.toList, "runtime-classpath-file"),
      output = _required_path(args.toList, "save")
    )

  private def _write_component_source_archive(args: Vector[String]): Unit =
    ComponentSourceArchiveProjection.write(
      projectRoot = _required_path(args.toList, "project-dir"),
      output = _required_path(args.toList, "save")
    )

  private def _write_component_release_source(args: Vector[String]): Unit = {
    val policy = ComponentReleaseSourceProjection.policy(
      _required_value(args.toList, "mode"),
      _required_value(args.toList, "license")
    )
    val evidence = _release_source_build_evidence(args.toList)
    val projectroot = _required_path(args.toList, "project-dir")
    val output = _required_path(args.toList, "save")
    val mainsources = _json_paths(args.toList, "managed-main-sources")
    val mainroots = _json_paths(args.toList, "managed-main-roots")
    val testsources = _json_paths(args.toList, "managed-test-sources")
    val testroots = _json_paths(args.toList, "managed-test-roots")
    if (_option(args.toList, "verify-existing").contains("true"))
      ComponentReleaseSourceProjection.verify(
        output,
        projectroot,
        policy,
        mainsources,
        mainroots,
        testsources,
        testroots,
        evidence
      )
    else
      ComponentReleaseSourceProjection.stage(
        projectroot,
        output,
        policy,
        mainsources,
        mainroots,
        testsources,
        testroots,
        evidence
      )
  }

  private def _release_source_build_evidence(args: List[String]): ComponentReleaseSourceProjection.BuildEvidence = {
    val json = _option(args, "build-evidence").map { value =>
      try Json.parse(value).as[JsObject]
      catch { case _: Throwable => RAISE.invalidArgumentFault("Invalid release-source build evidence JSON") }
    }.getOrElse(RAISE.invalidArgumentFault("Missing --build-evidence"))
    def _values_(key: String): Vector[String] =
      (json \ key).asOpt[Vector[String]].getOrElse(
        RAISE.invalidArgumentFault(s"Release-source build evidence is missing $key")
      )
    ComponentReleaseSourceProjection.BuildEvidence(
      _values_("compileScalacOptions"),
      _values_("testScalacOptions"),
      _values_("dependencies"),
      _values_("generators")
    )
  }

  private def _json_paths(args: List[String], key: String): Vector[Path] =
    _option(args, key).map { value =>
      try Json.parse(value).as[Vector[String]].map(path => Paths.get(path).toAbsolutePath.normalize())
      catch { case _: Throwable => RAISE.invalidArgumentFault(s"Invalid release-source $key JSON") }
    }.getOrElse(Vector.empty)

  private def _generation_config(settings: Map[String, String]): CozyProjectYamlConfig.Config = {
    val projectdir = settings.get(_sbt_project_dir_setting).
      map(x => Paths.get(x).toAbsolutePath.normalize()).
      getOrElse(Paths.get(".").toAbsolutePath.normalize())
    val generationsettings = settings - _sbt_project_dir_setting
    val defaultfiles = CozyProjectYamlConfig.operationDefaultFiles(projectdir)
    val globalconfigdir = Option(System.getProperty("user.home")).
      map(path => Paths.get(path).toAbsolutePath.normalize().resolve(".cozy"))
    _generation_config(generationsettings, defaultfiles, globalconfigdir)
  }

  private def _generation_config(
    generationsettings: Map[String, String],
    defaultfiles: Vector[Path],
    globalconfigdir: Option[Path]
  ): CozyProjectYamlConfig.Config = {
    val projectconfig = defaultfiles.
      filterNot(file => globalconfigdir.contains(file.getParent)).
      foldLeft(CozyProjectYamlConfig.Config.empty) { (config, file) =>
        config.merge(CozyProjectYamlConfig.load(file))
      }
    val bridgeconfig = CozyProjectYamlConfig.Config(generationsettings, Map.empty)
    _require_generation_config_agreement(projectconfig, bridgeconfig)
    projectconfig.merge(bridgeconfig)
  }

  private def _require_generation_config_agreement(
    projectconfig: CozyProjectYamlConfig.Config,
    bridgeconfig: CozyProjectYamlConfig.Config
  ): Unit =
    Vector(
      "generation.versions.cncf",
      "generation.versions.cozy",
      "runtime.cncf.descriptor",
      "runtime.cncf.descriptor.sha256"
    ).foreach { key =>
      CncfRuntimeDescriptorContract.requireConsistentSourceValues(
        "sbt-bridge",
        key,
        Vector(
          projectconfig.value(key).map("project" -> _),
          bridgeconfig.value(key).map("owning-build-bridge" -> _)
        ).flatten
      )
    }

  private def _version_args(config: CozyProjectYamlConfig.Config): List[String] = {
    val versions = Cozy.CarDependencyVersions.create(Nil, config)
    val base = List(
      "--cncf-version", versions.cncfVersion,
      "--cozy-generator-version",
      config.value("generation.versions.cozy").
        getOrElse(org.simplemodeling.cozy.BuildInfo.version),
      "--simplemodeling-model-version", versions.simpleModelingModelVersion,
      "--cncf-collaborator-api-version", versions.cncfCollaboratorApiVersion
    )
    config.value("runtime.cncf.descriptor").map { path =>
      val descriptorargs = base ++ List("--cncf-runtime-descriptor", path)
      config.value("runtime.cncf.descriptor.sha256").
        map(digest => descriptorargs ++ List("--cncf-runtime-descriptor-sha256", digest)).
        getOrElse(descriptorargs)
    }.getOrElse(base)
  }

  private def _component_api_args(settings: Map[String, String]): List[String] =
    Vector(
      "component.namespace" -> "--component-namespace",
      "component.id" -> "--component-id",
      "component.display-name" -> "--component-display-name",
      "component.version" -> "--component-version"
    ).flatMap { case (key, option) =>
      settings.get(key).map(value => Vector(option, value)).getOrElse(Vector.empty)
    }.toList

  private def _modeler_args(settings: Map[String, String]): List[String] =
    _component_api_args(settings) ++ _version_args(_generation_config(settings))

  private def _generation_source_identity_args(
    args: List[String],
    settings: Map[String, String]
  ): List[String] =
    if (_option(args, "generation-source-identity").nonEmpty)
      Nil
    else {
      val selected =
        _option(args, "cncf-runtime-descriptor").nonEmpty ||
          _option(args, "cncf-runtime-descriptor-sha256").nonEmpty
      if (!selected)
        Nil
      else {
        val identity = settings.get("generation.source.identity").
          map(_.trim).
          filter(_.nonEmpty).
          getOrElse(_project_relative_source_identity(args, settings))
        List(
          "--generation-source-identity",
          GenerationProvenance.requireSourceIdentity(Some(identity), "sbt-bridge")
        )
      }
    }

  private def _project_relative_source_identity(
    args: List[String],
    settings: Map[String, String]
  ): String = {
    val projectdir = settings.get(_sbt_project_dir_setting).
      map(path => Paths.get(path).toAbsolutePath.normalize()).
      getOrElse(Paths.get(".").toAbsolutePath.normalize())
    val source = args.find(!_.startsWith("-")).
      map(path => Paths.get(path)).
      getOrElse(RAISE.invalidArgumentFault(
        "CNCF-aware generation requires a CML source before provenance can be recorded"
      ))
    val normalized =
      if (source.isAbsolute)
        source.toAbsolutePath.normalize()
      else
        projectdir.resolve(source).toAbsolutePath.normalize()
    if (!normalized.startsWith(projectdir))
      RAISE.invalidArgumentFault(
        "CNCF-aware generation outside the sbt project requires generation.source.identity"
      )
    projectdir.relativize(normalized).toString.replace(java.io.File.separatorChar, '/')
  }

  private def _option(args: List[String], name: String): Option[String] = {
    val inlineprefix = s"--$name="
    args.zipWithIndex.collectFirst {
      case (value, _) if value.startsWith(inlineprefix) =>
        value.drop(inlineprefix.length).trim
      case (value, index) if value == s"--$name" && index + 1 < args.length =>
        args(index + 1).trim
    }.filter(_.nonEmpty)
  }

  private[cozy] def _component_api_args_for_test(settings: Map[String, String]): List[String] =
    _component_api_args(settings)

  private[cozy] def _modeler_args_for_settings_for_test(settings: Map[String, String]): List[String] =
    _modeler_args(settings)

  private[cozy] def _generation_source_identity_args_for_test(
    args: List[String],
    settings: Map[String, String]
  ): List[String] =
    _generation_source_identity_args(args, settings)

  private[cozy] def _version_args_for_test(settings: Map[String, String], projectdir: Path): List[String] =
    _version_args(_generation_config(settings + (_sbt_project_dir_setting -> projectdir.toString)))

  private[cozy] def _version_args_for_settings_for_test(settings: Map[String, String]): List[String] =
    _version_args(_generation_config(settings))

  private[cozy] def _version_args_for_default_files_for_test(
    settings: Map[String, String],
    defaultfiles: Vector[Path],
    globalconfigdirectory: Path
  ): List[String] =
    _version_args(_generation_config(
      settings,
      defaultfiles,
      Some(globalconfigdirectory.toAbsolutePath.normalize())
    ))

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

  private def _required_value(args: List[String], key: String): String =
    _option(args, key).getOrElse(RAISE.invalidArgumentFault(s"Missing --${key}"))

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

  private[cozy] def _load_request_for_test(path: Path): BridgeRequestView = {
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

  private[cozy] def _render_success_envelope_for_test(action: String): String =
    Json.prettyPrint(Json.toJson(BridgeResponseEnvelope("v1", "success", "process-exit", action, 0, "Bridge command completed successfully.")))

  private[cozy] def _render_error_envelope_for_test(action: String, message: String): String =
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
