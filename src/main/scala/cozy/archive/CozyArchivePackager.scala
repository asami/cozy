package cozy.archive

import org.goldenport.RAISE
import cozy.compatibility.{
  CarMetadataCompatibility,
  GenerationCompatibilityEvidence,
  GenerationCompatibilityBoundary
}
import cozy.config.CozyProjectYamlConfig
import cozy.modeler.GenerationProvenance
import cozy.runtime.CozyCliArgs
import play.api.libs.json._
import org.goldenport.cli.spec
import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path, Paths}
import java.security.MessageDigest
import java.util.zip.{ZipEntry, ZipFile, ZipOutputStream}
import scala.util.Try
import scala.collection.JavaConverters._
import scala.sys.process._

/*
 * @since   May. 20, 2026
 *  version May. 22, 2026
 *  version Jun. 18, 2026
 * @version Aug. 25, 2026
 * @author  ASAMI, Tomoharu
 */
private[cozy] final case class ReleaseSourceVerification(
  managedmainsources: Vector[Path],
  managedmainroots: Vector[Path],
  managedtestsources: Vector[Path],
  managedtestroots: Vector[Path],
  buildevidence: ComponentReleaseSourceProjection.BuildEvidence
)

private[cozy] object CozyArchivePackager {
  private val _model_metadata_schema = "cozy.cml.model-metadata.v1"

  private[cozy] def _write_development_component_descriptor(
    projectroot: Path,
    output: Path
  ): Unit = {
    val root = projectroot.toAbsolutePath.normalize()
    val projectmetadata = CozyProjectYamlConfig.loadProjectMetadata(root)
    val projectconfig = CozyProjectYamlConfig.loadProjectConfig(root)
    val coordinate = CozyComponentReleaseCoordinateCodec.fromProjectMetadata(projectmetadata, "development-component-descriptor")
    val manifestmetadata = projectconfig.mapUnder("packaging.car.manifest_metadata")
    val packagemetadata = _car_package_metadata(manifestmetadata, coordinate)
    _require_project_style_authority_free(projectmetadata)
    val modelmetadata = _development_model_metadata(root)
    val source = root.resolve("src/main/car/component-descriptor.json")
    _component_style_snapshot(modelmetadata) match {
      case Some(snapshot) =>
        if (Files.isRegularFile(source))
          RAISE.invalidArgumentFault("CML component style snapshot cannot be overridden by component-descriptor.json.")
        if (packagemetadata.extensions.contains("componentDescriptorJson"))
          RAISE.invalidArgumentFault("CML component style snapshot cannot be overridden by componentDescriptorJson.")
        _require_mode_free_component_config(projectconfig.mapUnder("project.component.config"))
        _write_text(
          output,
          _component_descriptor_json(
            coordinate,
            packagemetadata.extensions,
            projectconfig.mapUnder("project.component.config"),
            Vector.empty,
            Some(snapshot)
          )
        )
      case None =>
        if (!Files.isRegularFile(source))
          RAISE.invalidArgumentFault("Development CAR requires generated CML model metadata or src/main/car/component-descriptor.json.")
        val text = Files.readString(source, StandardCharsets.UTF_8)
        _validate_canonical_component_descriptor(text, coordinate, "component-descriptor.json")
        _write_text(output, text)
    }
  }

  def buildCar(args: List[String]): Unit =
    _build_car(args, None)

  private[cozy] def _build_car(
    args: List[String],
    evidence: GenerationCompatibilityEvidence,
    executingcozyversion: String
  ): Unit =
    _build_car(args, Some(evidence -> executingcozyversion))

  private def _build_car(
    args: List[String],
    acceptanceoverride: Option[(GenerationCompatibilityEvidence, String)]
  ): Unit = {
    val save = _required_path(args, "save")
    val mainjar = _required_path(args, "main-jar")
    val projectdir = Some(_required_path(args, "project-dir"))
    val config = _project_config(projectdir)
    val projectmetadata = _project_metadata(projectdir)
    val alllibjars = _paths(args, "lib-jars")
    val resolvedjars = (mainjar +: alllibjars).distinct
    val resolvedcncfartifacts = _resolved_cncf_artifacts(resolvedjars)
    val carcontract =
      acceptanceoverride match {
        case Some((evidence, executingcozyversion)) =>
          CarMetadataCompatibility.requireValidCarProject(
            projectmetadata,
            resolvedcncfartifacts,
            CarMetadataCompatibility.GenerationAcceptanceContext(
              evidence,
              executingcozyversion
            )
          )
        case None =>
          CarMetadataCompatibility.requireValidCarProject(
            projectmetadata,
            resolvedcncfartifacts
          )
      }
    val coordinate = CozyComponentReleaseCoordinateCodec.fromProjectMetadata(projectmetadata, "package-car")
    val outputversion = _required_value(args, "version")
    CozyComponentReleaseCoordinateCodec.requireProjection(coordinate.version, outputversion, "version", "package-car")
    val releaseoutput = !_is_snapshot_version(outputversion)
    val generatedproject = _has_cml_sources(projectdir)
    if (releaseoutput && generatedproject) {
      acceptanceoverride match {
        case Some((evidence, executingcozyversion)) =>
          GenerationCompatibilityBoundary.requireAcceptedProjectPair(
            carcontract.cncfCompileTarget.version,
            carcontract.cozyVersion,
            outputversion,
            executingcozyversion,
            evidence,
            "package-car"
          )
        case None =>
          GenerationCompatibilityBoundary.requireAcceptedProjectPair(
            carcontract.cncfCompileTarget.version,
            carcontract.cozyVersion,
            outputversion,
            "package-car"
          )
      }
    }
    _with_generation_provenance(
      projectdir,
      carcontract,
      requireprovenance = releaseoutput && generatedproject
    ) { generationprovenance =>
      val libjars = if (_include_dependencies(projectdir, config)) alllibjars else Vector.empty
      val spijars = _paths(args, "spi-jars")
      _validate_unique_spi_jars(spijars)
      val cardir = _path(args, "car-dir").orElse(_car_dir(projectdir, config))
      val scaladoc = _path(args, "scaladoc-dir").map { path =>
        CozyScaladocStaging.verify(path, projectdir.get)
      }
      val releasesource = (_release_source_policy(projectmetadata), _path(args, "release-source-dir")) match {
        case (None, Some(path)) =>
          RAISE.invalidArgumentFault(
            s"--release-source-dir requires project.yaml packaging.car.release_source policy: $path"
          )
        case (Some(policy), Some(path)) =>
          val verification = _release_source_verification(args)
          Some(ComponentReleaseSourceProjection.verifyForPackaging(
            path,
            projectdir.get,
            verification.managedmainsources,
            verification.managedmainroots,
            verification.managedtestsources,
            verification.managedtestroots,
            verification.buildevidence
          ) match {
            case verified if verified.policy == policy => verified
            case verified =>
              RAISE.invalidArgumentFault(
                s"Release-source policy differs from project.yaml: staged=${verified.policy.mode} current=${policy.mode}"
              )
          })
        case (Some(_), None) =>
          RAISE.invalidArgumentFault(
            "project.yaml declares packaging.car.release_source but no verified --release-source-dir was supplied"
          )
        case (None, None) => None
      }
      _require_no_source_generation_provenance(cardir)
      _require_no_source_runtime_manifest(cardir)
      val componentapidescriptor = _path(args, "component-api-descriptor").orElse(_source_component_api_descriptor(cardir))
      val defaultconf = _path(args, "default-conf").orElse(cardir.map(_.resolve("config/default.conf")).filter(Files.isRegularFile(_)))
      val dependencymanifest = _path(args, "dependency-manifest").orElse(_dependency_manifest(projectdir, config, resolvedjars))
      val webdir = _path(args, "web-dir").orElse(projectdir.map(_.resolve("src/main/web")).filter(Files.isDirectory(_)))
      val webinfdescriptors = _web_inf_descriptors(args, projectdir, config)
      val assemblydescriptor = _path(args, "assembly-descriptor").orElse(cardir.map(_.resolve("assembly-descriptor.yaml")).filter(Files.isRegularFile(_)))
      val modelmetadata = _paths(args, "model-metadata")
      val name = _required_value(args, "name")
      val version = _required_value(args, "version")
      CozyComponentReleaseCoordinateCodec.requireProjection(coordinate.mavenArtifactId, name, "name", "package-car")
      CozyComponentReleaseCoordinateCodec.requireProjection(coordinate.version, version, "version", "package-car")
      val componentapiartifacts = componentapidescriptor.toVector.flatMap(_component_api_artifact_paths(_, coordinate))
      _validate_component_api_artifacts(componentapiartifacts, spijars)
      val manifestmetadata = config.mapUnder("packaging.car.manifest_metadata")
      val component = _value(args, "component").orElse(manifestmetadata.get("component")).getOrElse(RAISE.invalidArgumentFault("Missing --component"))
      if (component != coordinate.id && component != coordinate.qualifiedId)
        CozyComponentReleaseCoordinateCodec.requireProjection(coordinate.qualifiedId, component, "component", "package-car")
      val packagemetadata = _car_package_metadata(manifestmetadata + ("component" -> component), coordinate)
      assemblydescriptor.foreach(_validate_assembly_descriptor(_, coordinate))
      val extensionmap = packagemetadata.extensions ++ _string_map(args, "extensions")
      val configmap = config.mapUnder("project.component.config") ++ _string_map(args, "config")
      val entities = _entity_descriptors(args)
      val componentstylesnapshot = _component_style_snapshot(modelmetadata)
      val cmlprojection = componentstylesnapshot.nonEmpty
      _require_project_style_authority_free(projectmetadata)
      if (cmlprojection && (extensionmap.contains("componentDescriptorJson") || _source_component_descriptor(cardir).nonEmpty))
        RAISE.invalidArgumentFault("CML component style snapshot cannot be overridden by component-descriptor.json or componentDescriptorJson.")
      if (cmlprojection)
        _require_mode_free_component_config(configmap)
      val abidependencies = config.indexedMapsUnder("packaging.car.abi.dependencies").map { dependency =>
        CozyCarAbiManifest.Dependency(
          dependency.getOrElse("namespace", ""),
          dependency.getOrElse("id", ""),
          dependency.getOrElse("abiRange", dependency.getOrElse("abi_range", ""))
        )
      }
      val abimanifest = _path(args, "abi-manifest").orElse(_source_abi_manifest(cardir)).map { path =>
        _validate_abi_manifest_coordinate(path, coordinate)
        path
      }.getOrElse {
        val content =
          if (modelmetadata.nonEmpty)
            CozyCarAbiManifest.create(modelmetadata, coordinate, abidependencies)
          else if (_has_cml_sources(projectdir))
            RAISE.invalidArgumentFault(
              s"CML CAR '${name}' requires generated model metadata when no explicit or source-managed ABI manifest is available."
            )
          else
            _abi_manifest_json(coordinate, entities, abidependencies)
        _write_temp(projectdir, "abi-manifest", content)
      }
      _path(args, "abi-manifest-output").foreach(path => _write_text(path, Files.readString(abimanifest, StandardCharsets.UTF_8)))
      val componentdescriptor = componentstylesnapshot.map { snapshot =>
        _write_temp(projectdir, "component-descriptor", _component_descriptor_json(coordinate, extensionmap, configmap, entities, Some(snapshot)))
      }.getOrElse {
        _component_descriptor_override(extensionmap, coordinate).
          map { text =>
            _write_temp(projectdir, "component-descriptor", text)
          }.
          orElse(_source_component_descriptor(cardir).map { path =>
            val text = Files.readString(path)
            _validate_canonical_component_descriptor(text, coordinate, "component-descriptor.json")
            path
          }).
          getOrElse {
            _write_temp(projectdir, "component-descriptor", _component_descriptor_json(coordinate, extensionmap, configmap, entities))
          }
      }
      def _write_car_(packagedmainjar: Path): Unit = {
        val runtimemanifest = Some(
          RuntimeManifestContext(
            carcontract,
            name,
            version,
            packagemetadata.component,
            coordinate
          )
        )
        _write_archive(
          save,
          projectdir,
          Vector(
            packagedmainjar -> "component/main.jar"
          ) ++
            _car_entries(cardir) ++
            scaladoc.toVector.flatMap(_.archiveEntries) ++
            releasesource.toVector.flatMap(_.archiveEntries) ++
            libjars.map(p => p -> s"lib/${p.getFileName}") ++
            spijars.map(p => p -> s"spi/${p.getFileName}") ++
            defaultconf.toVector.map(_ -> "config/default.conf") ++
            dependencymanifest.toVector.map(_ -> "component-dependencies.yaml") ++
            assemblydescriptor.toVector.map(_ -> "assembly-descriptor.yaml") ++
            componentapidescriptor.toVector.map(_ -> "component-api-descriptor.json") ++
            _web_entries(webdir) ++
            webinfdescriptors ++
            generationprovenance.toVector.map(_ -> "generation-provenance.json") ++
            Vector(abimanifest -> "abi-manifest.json") ++
            Vector(componentdescriptor -> "component-descriptor.json"),
          Vector("component", "lib", "spi", "config", "web"),
          runtimemanifest
        )
      }
      val workroot = _package_work_root(projectdir)
      val temporaryinputs = Vector(Some(abimanifest), Some(componentdescriptor), dependencymanifest).flatten.
        map(_.toAbsolutePath.normalize()).filter(_.startsWith(workroot))
      try {
        componentapidescriptor match {
          case Some(descriptor) =>
            ComponentApiJarPackager._with_implementation_jar(mainjar, descriptor)(_write_car_)
          case None =>
            _write_car_(mainjar)
        }
      } finally {
        temporaryinputs.foreach(Files.deleteIfExists(_))
      }
    }
  }

  private def _with_generation_provenance[A](
    projectdir: Option[Path],
    carcontract: CarMetadataCompatibility.Contract,
    requireprovenance: Boolean
  )(body: Option[Path] => A): A = {
    var snapshot: Option[Path] = None
    try {
      val provenance = projectdir.flatMap { root =>
        val path = root.resolve(GenerationProvenance.METADATA_PATH)
        Option(path).filter(Files.isRegularFile(_)).map { source =>
          val copied = _snapshot_temp(projectdir, "generation-provenance", source)
          snapshot = Some(copied)
          GenerationProvenance.requireValidForPackaging(
            provenancePath = copied,
            projectRoot = root,
            expectedCncfTargetVersion = Some(carcontract.cncfCompileTarget.version),
            expectedCozyGeneratorVersion = Some(carcontract.cozyVersion)
          )
          copied
        }
      }
      if (requireprovenance && provenance.isEmpty)
        RAISE.invalidArgumentFault(
          "Generated release CAR requires target/cozy/generation-provenance.json before archive output."
        )
      body(provenance)
    } finally {
      snapshot.foreach(Files.deleteIfExists(_))
    }
  }

  private def _is_snapshot_version(version: String): Boolean =
    Option(version).exists(
      _.toUpperCase(java.util.Locale.ROOT).contains("SNAPSHOT")
    )

  private def _project_config(projectdir: Option[Path]): CozyProjectYamlConfig.Config =
    projectdir.map(CozyProjectYamlConfig.loadProjectConfig).getOrElse(CozyProjectYamlConfig.Config.empty)

  private def _project_metadata(projectdir: Option[Path]): CozyProjectYamlConfig.Config =
    projectdir.map(CozyProjectYamlConfig.loadProjectMetadata).getOrElse(CozyProjectYamlConfig.Config.empty)

  private def _car_dir(projectdir: Option[Path], config: CozyProjectYamlConfig.Config): Option[Path] =
    projectdir.flatMap { dir =>
      config.value("packaging.car.source_dir").
        map(path => _config_path(dir, path)).
        orElse(Some(dir.resolve("src/main/car").toAbsolutePath.normalize())).
        filter(Files.isDirectory(_))
    }

  private def _config_path(projectdir: Path, value: String): Path = {
    val path = Paths.get(value)
    if (path.isAbsolute)
      path.normalize()
    else
      projectdir.resolve(path).toAbsolutePath.normalize()
  }

  private def _include_dependencies(projectdir: Option[Path], config: CozyProjectYamlConfig.Config): Boolean =
    config.boolean("packaging.car.include_dependencies").getOrElse(projectdir.isEmpty)

  private def _resolved_cncf_artifacts(
    resolvedjars: Vector[Path]
  ): Vector[CarMetadataCompatibility.ResolvedCncfArtifact] =
    resolvedjars.distinct.flatMap { path =>
      _runtime_catalog_jar_config(path).map { config =>
        CarMetadataCompatibility.ResolvedCncfArtifact(
          path.toString,
          config.value("runtime"),
          config.value("module"),
          config.value("version")
        )
      }
    }

  private def _dependency_manifest(
    projectdir: Option[Path],
    config: CozyProjectYamlConfig.Config,
    resolvedjars: Vector[Path]
  ): Option[Path] = {
    val provided = config.list("packaging.car.dependencies.provided")
    val shared = config.list("packaging.car.dependencies.shared")
    val local = config.list("packaging.car.dependencies.local")
    val repositories = config.list("packaging.car.dependencies.repositories")
    _validate_component_owned_dependencies(projectdir, config, resolvedjars, shared, local)
    if (provided.isEmpty && shared.isEmpty && local.isEmpty && repositories.isEmpty)
      None
    else
      Some(_write_temp(projectdir, "component-dependencies", _dependency_manifest_yaml(provided, shared, local, repositories)))
  }

  private def _web_inf_descriptors(
    args: List[String],
    projectdir: Option[Path],
    config: CozyProjectYamlConfig.Config
  ): Vector[(Path, String)] = {
    val descriptors = Vector(
      "web" -> _web_inf_descriptor(args, projectdir, config, "web", "packaging.car.web_descriptor"),
      "form" -> _web_inf_descriptor(args, projectdir, config, "form", "packaging.car.form_descriptor"),
      "admin" -> _web_inf_descriptor(args, projectdir, config, "admin", "packaging.car.admin_descriptor")
    )
    descriptors.flatMap {
      case (name, Some(path)) => Some(path -> s"web/WEB-INF/${name}.yaml")
      case (_, None) => None
    }
  }

  private def _web_inf_descriptor(
    args: List[String],
    projectdir: Option[Path],
    config: CozyProjectYamlConfig.Config,
    name: String,
    key: String
  ): Option[Path] =
    _path(args, s"${name}-descriptor").orElse {
      projectdir.flatMap { dir =>
        config.value(key).
          map(path => _config_path(dir, path)).
          orElse(Some(dir.resolve("src/main/web-inf").resolve(s"${name}.yaml").toAbsolutePath.normalize())).
          filter(Files.isRegularFile(_))
      }
    }

  private def _validate_component_owned_dependencies(
    projectdir: Option[Path],
    config: CozyProjectYamlConfig.Config,
    resolvedjars: Vector[Path],
    shared: Vector[String],
    local: Vector[String]
  ): Unit = {
    if ((shared.nonEmpty || local.nonEmpty) && _cncf_runtime_configured(config)) {
      _runtime_base_catalog(projectdir, config, resolvedjars) match {
        case Some(catalog) =>
          val baseprovided = catalog.baseprovidedmodules
          val overlaps = (shared ++ local).flatMap { coordinate =>
            _coordinate_module(coordinate).filter(baseprovided.contains).map(_ => coordinate)
          }.distinct
          if (overlaps.nonEmpty)
            RAISE.invalidArgumentFault(
              "Component dependencies overlap CNCF base-provided runtime libraries: " +
                overlaps.mkString(", ")
            )
        case None =>
          Console.err.println("[cozy] warning: CNCF runtime catalog is unavailable; skipping base-provided dependency validation")
      }
    }
  }

  private def _cncf_runtime_configured(config: CozyProjectYamlConfig.Config): Boolean =
    config.value("packaging.car.runtime.cncf.minimum").nonEmpty ||
      config.value("packaging.car.runtime.cncf.version").nonEmpty ||
      config.value("packaging.car.runtime.cncf.maximum").nonEmpty ||
      config.list("packaging.car.runtime.cncf.excluded").nonEmpty ||
      config.list("packaging.car.runtime.cncf.tested").nonEmpty

  private final case class RuntimeCatalog(baseprovidedmodules: Set[String])

  private def _runtime_base_catalog(
    projectdir: Option[Path],
    config: CozyProjectYamlConfig.Config,
    resolvedjars: Vector[Path]
  ): Option[RuntimeCatalog] =
    _runtime_catalog_candidates(projectdir, config, resolvedjars).find(_.baseprovidedmodules.nonEmpty)

  private def _runtime_catalog_candidates(
    projectdir: Option[Path],
    config: CozyProjectYamlConfig.Config,
    resolvedjars: Vector[Path]
  ): Vector[RuntimeCatalog] =
    _explicit_runtime_catalog_paths(projectdir, config).collect {
      case path if Files.isRegularFile(path) =>
        val catalog = CozyProjectYamlConfig.load(path)
        _runtime_catalog_from_config(catalog)
    } ++
      _runtime_catalog_urls(config).flatMap(_runtime_catalog_url) ++
      _runtime_catalog_jars(resolvedjars) ++
      _project_runtime_catalog_paths(projectdir, config).collect {
        case path if Files.isRegularFile(path) =>
          val catalog = CozyProjectYamlConfig.load(path)
          _runtime_catalog_from_config(catalog)
      } ++
      _runtime_catalog_commands(config).flatMap(_runtime_catalog_command)

  private def _explicit_runtime_catalog_paths(
    projectdir: Option[Path],
    config: CozyProjectYamlConfig.Config
  ): Vector[Path] = {
    val basedir = projectdir.getOrElse(Paths.get(".").toAbsolutePath.normalize())
    _runtime_catalog_config_values(config).
      filterNot(_is_url).
      map(value => _config_path(basedir, value)).
      toVector
  }

  private def _project_runtime_catalog_paths(
    projectdir: Option[Path],
    config: CozyProjectYamlConfig.Config
  ): Vector[Path] = {
    val cncfprojects =
      _cncf_runtime_project_dirs(projectdir, config).map(_.resolve("target/cncf.d/runtime-catalog.yaml"))
    val local = projectdir.toVector.flatMap { dir =>
      Vector(
        dir.resolve("target/cncf.d/runtime-catalog.yaml"),
        dir.resolve("repository/textus/runtime-catalog.yaml"),
        dir.resolve("src/main/catalog/cncf.yaml")
      )
    }
    (cncfprojects ++ local).distinct
  }

  private def _runtime_catalog_config_values(config: CozyProjectYamlConfig.Config): Vector[String] =
    config.value("packaging.car.runtime.cncf.catalog").toVector ++
      config.value("runtime.catalog.path").toVector

  private def _runtime_catalog_urls(config: CozyProjectYamlConfig.Config): Vector[String] =
    _runtime_catalog_config_values(config).filter(_is_url)

  private def _is_url(value: String): Boolean =
    value.startsWith("http://") || value.startsWith("https://")

  private def _runtime_catalog_url(url: String): Option[RuntimeCatalog] =
    Try {
      val connection = new java.net.URI(url).toURL.openConnection()
      connection.setConnectTimeout(3000)
      connection.setReadTimeout(3000)
      val in = connection.getInputStream
      try {
        val lines = scala.io.Source.fromInputStream(in, "UTF-8").getLines().toVector
        val catalog = CozyProjectYamlConfig.parse(lines)
        _runtime_catalog_from_config(catalog)
      } finally {
        in.close()
      }
    }.toOption

  private def _runtime_catalog_jars(paths: Vector[Path]): Vector[RuntimeCatalog] =
    paths.distinct.flatMap(_runtime_catalog_jar)

  private def _runtime_catalog_jar(path: Path): Option[RuntimeCatalog] =
    _runtime_catalog_jar_config(path).map(_runtime_catalog_from_config)

  private def _runtime_catalog_jar_config(path: Path): Option[CozyProjectYamlConfig.Config] =
    if (Files.isRegularFile(path) && path.getFileName.toString.endsWith(".jar"))
      Try {
        val zip = new ZipFile(path.toFile)
        try {
          Option(zip.getEntry("META-INF/cncf/runtime.yaml")).map { entry =>
            val in = zip.getInputStream(entry)
            try {
              val lines = scala.io.Source.fromInputStream(in, "UTF-8").getLines().toVector
              CozyProjectYamlConfig.parse(lines)
            } finally {
              in.close()
            }
          }
        } finally {
          zip.close()
        }
      }.toOption.flatten
    else
      None

  private def _runtime_catalog_commands(config: CozyProjectYamlConfig.Config): Vector[String] =
    config.value("runtime.cncf.command").toVector ++
      config.value("packaging.car.runtime.cncf.command").toVector

  private def _runtime_catalog_command(command: String): Option[RuntimeCatalog] =
    Try {
      val output = Process(Vector(command, "runtime", "descriptor", "--format", "yaml")).!!
      val catalog = CozyProjectYamlConfig.parse(output.linesIterator.toVector)
      _runtime_catalog_from_config(catalog)
    }.toOption

  private def _runtime_catalog_from_config(config: CozyProjectYamlConfig.Config): RuntimeCatalog =
    RuntimeCatalog(_base_provided_modules(config))

  private def _cncf_runtime_project_dirs(
    projectdir: Option[Path],
    config: CozyProjectYamlConfig.Config
  ): Vector[Path] = {
    val basedir = projectdir.getOrElse(Paths.get(".").toAbsolutePath.normalize())
    val configured =
      config.value("packaging.car.runtime.cncf.project_dir").
        orElse(config.value("packaging.car.runtime.cncf.projectDir")).
        orElse(config.value("runtime.cncf.project_dir")).
        orElse(config.value("runtime.cncf.projectDir")).
        map(value => _config_path(basedir, value)).
        toVector
    val environment =
      Vector("CNCF_RUNTIME_PROJECT_DIR", "CNCF_PROJECT_DIR").
        flatMap(name => sys.env.get(name)).
        map(value => _config_path(basedir, value))
    (configured ++ environment).distinct
  }

  private def _base_provided_modules(config: CozyProjectYamlConfig.Config): Set[String] =
    (
      config.list("baseProvided") ++
        config.list("base_provided") ++
        config.list("runtime.baseProvided") ++
        config.list("runtime.base_provided")
    ).flatMap(_coordinate_module).toSet

  private def _coordinate_module(coordinate: String): Option[String] = {
    val parts = coordinate.split(":").toVector.map(_.trim).filter(_.nonEmpty)
    if (parts.length >= 2)
      Some(s"${parts(0)}:${parts(1)}")
    else
      None
  }

  private def _dependency_manifest_yaml(
    provided: Vector[String],
    shared: Vector[String],
    local: Vector[String],
    repositories: Vector[String]
  ): String = {
    def _section_(name: String, values: Vector[String]): String =
      if (values.isEmpty) ""
      else values.map(v => s"    - ${_yaml_string(v)}\n").mkString(s"  $name:\n", "", "")
    "dependencies:\n" +
      _section_("provided", provided) +
      _section_("shared", shared) +
      _section_("local", local) +
      _section_("repositories", repositories)
  }

  private def _yaml_string(value: String): String = {
    val escaped = value.replace("\\", "\\\\").replace("\"", "\\\"")
    s""""$escaped""""
  }

  private final case class CarPackageMetadata(
    component: String,
    extensions: Map[String, String]
  )

  private def _car_package_metadata(
    metadata: Map[String, String],
    coordinate: CozyComponentReleaseCoordinateCodec.Coordinate
  ): CarPackageMetadata = {
    val component = metadata.getOrElse("component", coordinate.id)
    val componentletnames = _componentlet_names(metadata)
    val reservedkeys = Set("component", "componentlets", "componentDescriptorJson") ++ metadata.keySet.filter(_.startsWith("componentlet."))
    val passthroughextensions = metadata -- reservedkeys
    val extensions =
      if (componentletnames.isEmpty)
        passthroughextensions
      else
        passthroughextensions + ("componentDescriptorJson" -> _component_descriptor_override_json(coordinate, passthroughextensions, componentletnames, metadata))
    CarPackageMetadata(component, extensions)
  }

  private def _componentlet_names(metadata: Map[String, String]): Vector[String] = {
    val fromlist = metadata
      .get("componentlets")
      .toVector
      .flatMap(_.split(",").toVector)
      .map(_.trim)
      .filter(_.nonEmpty)
    val fromkeys = metadata.keysIterator
      .filter(_.startsWith("componentlet."))
      .flatMap { key =>
        key.stripPrefix("componentlet.").split("\\.", 2).headOption
      }
      .toVector
      .map(_.trim)
      .filter(_.nonEmpty)
    (fromlist ++ fromkeys).distinct.sorted.toVector
  }

  private def _component_descriptor_override_json(
    coordinate: CozyComponentReleaseCoordinateCodec.Coordinate,
    extensions: Map[String, String],
    componentletnames: Vector[String],
    metadata: Map[String, String]
  ): String = {
    val componentlets = componentletnames.map { name =>
      val prefix = s"componentlet.$name."
      val fields = metadata.collect {
        case (key, value) if key.startsWith(prefix) =>
          key.stripPrefix(prefix) -> value
      }
      val jsonfields = (Map("name" -> name, "kind" -> fields.getOrElse("kind", "componentlet")) ++ fields).toVector.sortBy(_._1)
      jsonfields.map { case (key, value) => s"${_json_string(key)}:${_json_string(value)}" }.mkString("{", ",", "}")
    }
    s"""{"schemaVersion":3,"component":${Json.stringify(coordinate.componentJson)},"componentlets":[${componentlets.mkString(",")}],"extensions":${_json_map(extensions)}}"""
  }

  def buildSar(args: List[String]): Unit = {
    val save = _required_path(args, "save")
    val sourcedir = _required_path(args, "source-dir")
    val sourcefiles = _values(args, "source-files")
    val extensionjars = _paths(args, "extension-jars")
    val applicationconf = _path(args, "application-conf")
    val subsystemsources = _archive_sources(sourcedir, sourcefiles)
    _write_archive(
      save,
      None,
      subsystemsources ++
        extensionjars.map(p => p -> s"extension/${p.getFileName}") ++
        applicationconf.toVector.map(_ -> "config/application.conf"),
      Vector("extension", "config")
    )
  }

  private def _archive_sources(sourcedir: Path, includes: Vector[String] = Vector.empty): Vector[(Path, String)] = {
    if (!Files.exists(sourcedir))
      Vector.empty
    else {
      val includeset = includes.map(_.replace('\\', '/')).toSet
      val stream = Files.walk(sourcedir)
      try {
        stream.iterator().asScala.toVector.collect {
          case p if Files.isRegularFile(p) =>
            p -> sourcedir.relativize(p).toString.replace('\\', '/')
        }.filter { case (_, rel) =>
          includeset.isEmpty || includeset.contains(rel)
        }.sortBy(_._2)
      } finally {
        stream.close()
      }
    }
  }

  private def _car_entries(cardir: Option[Path]): Vector[(Path, String)] =
    cardir.toVector.flatMap(_archive_sources(_)).filterNot { case (_, rel) =>
      rel == "abi-manifest.json" ||
        rel == "component-descriptor.json" ||
        rel == "component-api-descriptor.json" ||
        rel == "generation-provenance.json" ||
        rel == CozyCarRuntimeManifest.FILE_NAME ||
        _is_historical_abi_manifest(rel)
    }

  private def _require_no_source_generation_provenance(cardir: Option[Path]): Unit =
    cardir.map(_.resolve("generation-provenance.json")).filter(Files.exists(_)).foreach { path =>
      RAISE.invalidArgumentFault(
        s"Source-managed generation provenance is forbidden: $path. Regenerate target/cozy/generation-provenance.json through Cozy."
      )
    }

  private def _require_no_source_runtime_manifest(cardir: Option[Path]): Unit =
    cardir.map(_.resolve(CozyCarRuntimeManifest.FILE_NAME)).filter(Files.exists(_)).foreach { path =>
      RAISE.invalidArgumentFault(
        s"Source-managed CAR runtime manifest is forbidden: $path. Let Cozy generate ${CozyCarRuntimeManifest.FILE_NAME} from the accepted project contract."
      )
    }

  private def _source_abi_manifest(cardir: Option[Path]): Option[Path] =
    cardir.map(_.resolve("abi-manifest.json")).filter(Files.isRegularFile(_))

  private def _has_cml_sources(projectdir: Option[Path]): Boolean =
    projectdir.exists { root =>
      val sourcedir = root.resolve("src/main/cozy")
      if (!Files.isDirectory(sourcedir))
        false
      else {
        val stream = Files.walk(sourcedir)
        try stream.iterator().asScala.exists(path => Files.isRegularFile(path) && path.getFileName.toString.endsWith(".cml"))
        finally stream.close()
      }
    }

  private def _release_source_policy(projectmetadata: CozyProjectYamlConfig.Config): Option[ComponentReleaseSourceProjection.Policy] = {
    val prefix = "packaging.car.release_source"
    val authored = (projectmetadata.authoredkeys ++ projectmetadata.values.keys ++ projectmetadata.lists.keys).filter { key =>
      key == prefix || key.startsWith(prefix + ".")
    }
    if (authored.isEmpty) None
    else {
      val fields = authored.collect { case key if key.startsWith(prefix + ".") => key.substring(prefix.length + 1) }
      val unsupported = fields.filterNot(field => field == "mode" || field == "license")
      if (unsupported.nonEmpty)
        RAISE.invalidArgumentFault(
          s"Unsupported packaging.car.release_source fields: ${unsupported.toVector.sorted.mkString(", ")}"
        )
      val mode = projectmetadata.value(prefix + ".mode").getOrElse("")
      val license = projectmetadata.value(prefix + ".license").getOrElse("")
      Some(ComponentReleaseSourceProjection.policy(mode, license))
    }
  }

  private def _release_source_verification(
    args: List[String]
  ): ReleaseSourceVerification = {
    val managedmainsources = _json_paths(args, "release-source-managed-main-sources")
    val managedmainroots = _json_paths(args, "release-source-managed-main-roots")
    val managedtestsources = _json_paths(args, "release-source-managed-test-sources")
    val managedtestroots = _json_paths(args, "release-source-managed-test-roots")
    val buildevidence = _value(args, "release-source-build-evidence").map { value =>
      val json = Try(Json.parse(value).as[JsObject]).getOrElse(
        RAISE.invalidArgumentFault("Invalid release-source verification build evidence JSON")
      )
      def _values_(key: String): Vector[String] =
        (json \ key).asOpt[Vector[String]].getOrElse(
          RAISE.invalidArgumentFault(s"Release-source verification build evidence is missing $key")
        )
      ComponentReleaseSourceProjection.BuildEvidence(
        _values_("compileScalacOptions"),
        _values_("testScalacOptions"),
        _values_("dependencies"),
        _values_("generators")
      )
    }.getOrElse(
      RAISE.invalidArgumentFault("--release-source-dir requires release-source verification build evidence")
    )
    ReleaseSourceVerification(
      managedmainsources,
      managedmainroots,
      managedtestsources,
      managedtestroots,
      buildevidence
    )
  }

  private def _json_paths(args: List[String], key: String): Vector[Path] =
    _value(args, key).map { value =>
      try Json.parse(value).as[Vector[String]].map(path => Paths.get(path).toAbsolutePath.normalize())
      catch { case _: Throwable => RAISE.invalidArgumentFault(s"Invalid release-source verification $key JSON") }
    }.getOrElse(RAISE.invalidArgumentFault(s"--release-source-dir requires --$key"))

  private def _source_component_descriptor(cardir: Option[Path]): Option[Path] =
    cardir.map(_.resolve("component-descriptor.json")).filter(Files.isRegularFile(_))

  private def _component_style_snapshot(paths: Vector[Path]): Option[JsObject] = {
    val snapshots = paths.flatMap { path =>
      val root = Try(Json.parse(Files.readString(path, StandardCharsets.UTF_8))).getOrElse(
        RAISE.invalidArgumentFault(s"Invalid generated model metadata JSON: $path")
      ).asOpt[JsObject].getOrElse(RAISE.invalidArgumentFault(s"Generated model metadata must be a JSON object: $path"))
      val schema = (root \ "schema").asOpt[String].getOrElse("")
      if (schema != _model_metadata_schema)
        RAISE.invalidArgumentFault(s"CML model metadata $path must declare schema '${_model_metadata_schema}', but was '${schema}'.")
      root.value.get("componentStyle") match {
        case None => None
        case Some(value: JsObject) if value.keys.nonEmpty => Some(value)
        case Some(_) =>
          RAISE.invalidArgumentFault(s"CML model metadata $path must declare one non-empty componentStyle object or omit componentStyle for the legacy route.")
      }
    }
    snapshots match {
      case Vector() => None
      case Vector(snapshot) => Some(snapshot)
      case _ => RAISE.invalidArgumentFault("CML CAR must project exactly one component style snapshot.")
    }
  }

  private def _development_model_metadata(projectroot: Path): Vector[Path] = {
    val target = projectroot.resolve("target/cozy")
    val direct = target.resolve("model-metadata.json")
    val nested = target.resolve("model-metadata")
    val nestedfiles =
      if (Files.isDirectory(nested)) {
        val stream = Files.walk(nested)
        try stream.iterator().asScala.filter(Files.isRegularFile(_)).filter(_.getFileName.toString.endsWith(".json")).toVector.sortBy(_.toString)
        finally stream.close()
      } else Vector.empty
    (Option(direct).filter(path => Files.isRegularFile(path)).toVector ++ nestedfiles).distinct
  }

  private def _require_project_style_authority_free(projectmetadata: CozyProjectYamlConfig.Config): Unit = {
    val forbidden = (projectmetadata.values.keys ++ projectmetadata.lists.keys).filter { key =>
      val normalized = key.toLowerCase(java.util.Locale.ROOT).filter(_.isLetterOrDigit)
      normalized.contains("componentstyle") || normalized.contains("componentcapabilit")
    }.toVector.distinct.sorted
    if (forbidden.nonEmpty)
      RAISE.invalidArgumentFault(s"project.yaml must not declare component style or capability authority: ${forbidden.mkString(", ")}")
  }

  private def _require_mode_free_component_config(config: Map[String, String]): Unit = {
    val forbidden = config.keys.filter { key =>
      val normalized = key.toLowerCase(java.util.Locale.ROOT).filter(_.isLetterOrDigit)
      normalized.contains("operationmode") ||
        normalized.contains("applicationmode") ||
        normalized.contains("componentmode") ||
        normalized.contains("subsystemmode") ||
        normalized.contains("webapplicationmode") ||
        normalized.contains("fixeduser") ||
        normalized.contains("datastore") ||
        normalized.contains("locale") ||
        normalized.contains("timezone")
    }.toVector.distinct.sorted
    if (forbidden.nonEmpty)
      RAISE.invalidArgumentFault(
        s"CML component style snapshot must not declare Component operating, user, formatting, or datastore policy: ${forbidden.mkString(", ")}"
      )
  }

  private def _source_component_api_descriptor(cardir: Option[Path]): Option[Path] =
    cardir.map(_.resolve("component-api-descriptor.json")).filter(Files.isRegularFile(_))

  private def _component_api_artifact_paths(
    path: Path,
    coordinate: CozyComponentReleaseCoordinateCodec.Coordinate
  ): Vector[String] = {
    val json = Json.parse(Files.readString(path, StandardCharsets.UTF_8))
    val schemaversion = (json \ "schemaVersion").asOpt[String]
    if (schemaversion != Some("cncf.component-api.v2"))
      RAISE.invalidArgumentFault(s"component.api.schema.unsupported source=$path actual=${schemaversion.getOrElse("missing")}")
    CozyComponentReleaseCoordinateCodec.requireExact(
      coordinate,
      CozyComponentReleaseCoordinateCodec.readComponent(json, path.toString),
      path.toString
    )
    (json \ "provided").asOpt[Vector[JsObject]].getOrElse(Vector.empty).map { provided =>
      val providedversion = (provided \ "version").asOpt[String].map(_.trim).filter(_.nonEmpty).getOrElse {
        RAISE.invalidArgumentFault(s"component-api-descriptor.json provided API is missing version: ${path}")
      }
      CozyComponentReleaseCoordinateCodec.requireProjection(coordinate.version, providedversion, "provided.version", path.toString)
      val artifactpath = (provided \ "artifactPath").asOpt[String].map(_.trim).filter(_.nonEmpty).getOrElse {
        RAISE.invalidArgumentFault(s"component-api-descriptor.json provided API is missing artifactPath: ${path}")
      }
      val normalized = Paths.get(artifactpath).normalize().toString.replace('\\', '/')
      if (normalized != artifactpath || !artifactpath.startsWith("spi/") || artifactpath.count(_ == '/') != 1)
        RAISE.invalidArgumentFault(s"Component API artifactPath must be a direct child of spi/: ${artifactpath}")
      CozyComponentReleaseCoordinateCodec.requireProjection(coordinate.apiArtifactPath, artifactpath, "artifactPath", path.toString)
      artifactpath
    }.distinct
  }

  private def _validate_unique_spi_jars(spijars: Vector[Path]): Unit = {
    val duplicates = spijars.groupBy(_.getFileName.toString).collect {
      case (name, paths) if paths.size > 1 => s"${name}: ${paths.mkString(", ")}"
    }.toVector.sorted
    if (duplicates.nonEmpty)
      RAISE.invalidArgumentFault(s"CAR SPI JAR names must be unique: ${duplicates.mkString("; ")}")
  }

  private def _validate_component_api_artifacts(artifactpaths: Vector[String], spijars: Vector[Path]): Unit = {
    val packagedpaths = spijars.map(path => s"spi/${path.getFileName}").toSet
    val missing = artifactpaths.filterNot(packagedpaths.contains)
    if (missing.nonEmpty)
      RAISE.invalidArgumentFault(s"Component API descriptor artifacts are missing from CAR SPI JARs: ${missing.mkString(", ")}")
  }

  private def _is_historical_abi_manifest(relative: String): Boolean = {
    val pattern = """^\d+\.\d+\.\d+[^/]*/abi-manifest\.json$""".r
    pattern.pattern.matcher(relative).matches()
  }

  private def _web_entries(webdir: Option[Path]): Vector[(Path, String)] =
    webdir.toVector.flatMap { dir =>
      _archive_sources(dir).filterNot { case (_, rel) =>
        rel == "web.yaml" ||
          rel == "web-descriptor.yaml" ||
          rel == "WEB-INF/web-descriptor.yaml" ||
          rel == "WEB-INF/web.yaml" ||
          rel == "WEB-INF/form.yaml" ||
          rel == "WEB-INF/admin.yaml"
      }.map { case (p, rel) => p -> s"web/${rel}" }
    }

  private def _write_archive(
    archive: Path,
    projectdir: Option[Path],
    entries: Vector[(Path, String)],
    placeholderdirs: Vector[String],
    runtimemanifest: Option[RuntimeManifestContext] = None
  ): Unit = {
    Files.createDirectories(archive.getParent)
    Files.deleteIfExists(archive)
    val workroot = _package_work_root(projectdir)
    Files.createDirectories(workroot)
    val tempdir = Files.createTempDirectory(workroot, "archive-")
    try {
      entries.foreach { case (source, relative) =>
        val dest = tempdir.resolve(relative)
        Option(dest.getParent).foreach(Files.createDirectories(_))
        Files.copy(source, dest, java.nio.file.StandardCopyOption.REPLACE_EXISTING)
      }
      placeholderdirs.foreach { dir =>
        val target = tempdir.resolve(dir)
        if (!Files.exists(target) || _is_empty_dir(target))
          _write_text(target.resolve(".keep"), "")
      }
      runtimemanifest.foreach { context =>
        CozyCarRuntimeManifest.write(
          tempdir,
          context.contract,
          context.name,
          context.version,
          context.component,
          Some(context.coordinate)
        )
      }
      _zip_dir(tempdir, archive)
    } finally {
      _delete_tree(tempdir)
    }
  }

  private final case class RuntimeManifestContext(
    contract: CarMetadataCompatibility.Contract,
    name: String,
    version: String,
    component: String,
    coordinate: CozyComponentReleaseCoordinateCodec.Coordinate
  )

  private def _zip_dir(sourcedir: Path, archive: Path): Unit = {
    val stream = Files.walk(sourcedir)
    try {
      val files = stream.iterator().asScala.toVector.collect {
        case p if Files.isRegularFile(p) =>
          p -> sourcedir.relativize(p).toString.replace('\\', '/')
      }.sortBy(_._2)
      val out = new ZipOutputStream(Files.newOutputStream(archive))
      try {
        files.foreach { case (file, relative) =>
          out.putNextEntry(new ZipEntry(relative))
          Files.copy(file, out)
          out.closeEntry()
        }
      } finally {
        out.close()
      }
    } finally {
      stream.close()
    }
  }

  private def _component_descriptor_json(
    coordinate: CozyComponentReleaseCoordinateCodec.Coordinate,
    extensions: Map[String, String],
    config: Map[String, String],
    entities: Vector[EntityDescriptor],
    componentstylesnapshot: Option[JsObject] = None
  ): String = {
    val effectiveextensions = extensions - "componentDescriptorJson"
    val payload = Json.obj(
      "schemaVersion" -> 3,
      "component" -> coordinate.componentJson,
      "entities" -> Json.parse(_json_entities(entities)),
      "extensions" -> Json.parse(_json_map(effectiveextensions)),
      "config" -> Json.parse(_json_map(config))
    ) ++ componentstylesnapshot.map(value => Json.obj("componentStyle" -> value)).getOrElse(Json.obj())
    Json.prettyPrint(payload) + "\n"
  }

  private def _component_descriptor_override(
    extensions: Map[String, String],
    coordinate: CozyComponentReleaseCoordinateCodec.Coordinate
  ): Option[String] =
    extensions.get("componentDescriptorJson").map(_.trim).filter(_.nonEmpty).map { text =>
      _validate_canonical_component_descriptor(text, coordinate, "componentDescriptorJson")
      text
    }

  private[archive] def _validate_canonical_component_descriptor(
    text: String,
    coordinate: CozyComponentReleaseCoordinateCodec.Coordinate,
    label: String
  ): Unit = {
    val json = Try(Json.parse(text)).getOrElse(RAISE.invalidArgumentFault(s"${label} must be valid JSON."))
    if ((json \ "schemaVersion").asOpt[Int] != Some(3))
      RAISE.invalidArgumentFault(s"component.descriptor.schema.unsupported source=$label")
    CozyComponentReleaseCoordinateCodec.requireExact(
      coordinate,
      CozyComponentReleaseCoordinateCodec.readComponent(json, label),
      label
    )
    _require_canonical_component_descriptor_shape(json, label)
  }

  private def _validate_assembly_descriptor(
    path: Path,
    coordinate: CozyComponentReleaseCoordinateCodec.Coordinate
  ): Unit = {
    val descriptor = CozyProjectYamlConfig.load(path)
    val descriptorversion = descriptor.value("version")
    if (!descriptorversion.contains(coordinate.version))
      RAISE.invalidArgumentFault(
        s"assembly-descriptor.yaml must declare subsystem version '${coordinate.version}' for CAR '${coordinate.dependencyKey}', but declared '${descriptorversion.getOrElse("<missing>")}'."
      )
    val componententry =
      descriptor.indexedMapsUnder("components").find { entry =>
        entry.get("namespace").contains(coordinate.namespace) &&
          entry.get("id").contains(coordinate.id) &&
          entry.get("version").contains(coordinate.version)
      }
    if (componententry.isEmpty)
      RAISE.invalidArgumentFault(
        s"assembly-descriptor.yaml must declare component '${coordinate.dependencyKey}'."
      )
  }

  private def _abi_manifest_json(
    coordinate: CozyComponentReleaseCoordinateCodec.Coordinate,
    entities: Vector[EntityDescriptor],
    dependencies: Vector[CozyCarAbiManifest.Dependency]
  ): String =
    s"""{
       |  "format": "cozy.car.abi-manifest.v2",
       |  "component": ${Json.stringify(coordinate.componentJson)},
       |  "abi": {
       |    "version": 1,
       |    "exports": {
       |      "components": [
       |        {
       |          "namespace": ${_json_string(coordinate.namespace)},
       |          "id": ${_json_string(coordinate.id)}
       |        }
       |      ],
       |      "operations": [],
       |      "entities": ${_abi_json_entities(entities)}
       |    },
       |    "dependencies": ${Json.stringify(CozyCarAbiManifest._normalized_dependencies_json(dependencies))}
       |  }
       |}
       |""".stripMargin

  private def _validate_abi_manifest_coordinate(
    path: Path,
    coordinate: CozyComponentReleaseCoordinateCodec.Coordinate
  ): Unit = {
    val json = Try(Json.parse(Files.readString(path, StandardCharsets.UTF_8))).getOrElse(
      RAISE.invalidArgumentFault(s"Invalid ABI manifest JSON: ${path}")
    )
    CozyCarAbiManifest._validate_source_manifest(json, coordinate, path.toString)
  }

  private[archive] def _require_canonical_component_descriptor_shape(json: JsValue, label: String): Unit = {
    val root = json.asOpt[JsObject].getOrElse(RAISE.invalidArgumentFault(s"$label must be a JSON object."))
    if ((root \ "schemaVersion").asOpt[Int] != Some(3))
      RAISE.invalidArgumentFault(s"component.descriptor.schema.unsupported source=$label")
    if (root.keys.contains("name") || root.keys.contains("version"))
      RAISE.invalidArgumentFault(s"$label schema 3 forbids legacy root name and version fields.")
    val component = root.value.get("component").collect { case value: JsObject => value }.getOrElse(
      RAISE.invalidArgumentFault(s"$label schema 3 requires a component object; string component is forbidden.")
    )
    if (component.keys != Set("namespace", "id", "version"))
      RAISE.invalidArgumentFault(s"$label schema 3 requires exact component namespace, id, and version fields.")
  }

  private final case class EntityDescriptor(
    name: String,
    usagekind: Option[String],
    operationkind: Option[String],
    applicationdomain: Option[String]
  )

  private def _entity_descriptors(args: List[String]): Vector[EntityDescriptor] =
    _value(args, "entities").toVector.flatMap { text =>
      text.split(";").toVector.map(_.trim).filter(_.nonEmpty).map(_entity_descriptor)
    }

  private def _entity_descriptor(text: String): EntityDescriptor = {
    val parts = text.split(":", 2).toVector.map(_.trim)
    val name = parts.headOption.filter(_.nonEmpty).getOrElse(RAISE.invalidArgumentFault(s"Invalid entity descriptor: $text"))
    val kv = parts.drop(1).headOption.toVector.flatMap(_.split(",")).flatMap { entry =>
      entry.split("=", 2).toVector.map(_.trim) match {
        case Vector(k, v) if k.nonEmpty && v.nonEmpty => Some(k -> v)
        case _ => None
      }
    }.toMap
    EntityDescriptor(
      name = name,
      usagekind = kv.get("usageKind").orElse(kv.get("usage_kind")).orElse(kv.get("entityUsage")).orElse(kv.get("entity_usage")),
      operationkind = kv.get("operationKind").orElse(kv.get("operation_kind")).orElse(kv.get("entityOperationKind")).orElse(kv.get("entity_operation_kind")),
      applicationdomain = kv.get("applicationDomain").orElse(kv.get("application_domain")).orElse(kv.get("entityApplicationDomain")).orElse(kv.get("entity_application_domain"))
    )
  }

  private def _json_entities(xs: Vector[EntityDescriptor]): String =
    xs.map(_json_entity).mkString("[", ", ", "]")

  private def _abi_json_entities(xs: Vector[EntityDescriptor]): String =
    xs.map { entity =>
      s"""{"name": ${_json_string(entity.name)}, "fields": []}"""
    }.mkString("[", ", ", "]")

  private def _json_entity(entity: EntityDescriptor): String = {
    val fields = Vector(
      Some("entity" -> entity.name),
      entity.usagekind.map("usageKind" -> _),
      entity.operationkind.map("operationKind" -> _),
      entity.applicationdomain.map("applicationDomain" -> _)
    ).flatten
    fields.map { case (k, v) => s"${_json_string(k)}: ${_json_string(v)}" }.mkString("{", ", ", "}")
  }

  private def _json_map(xs: Map[String, String]): String =
    xs.toVector.sortBy(_._1).map { case (k, v) => s"${_json_string(k)}: ${_json_string(v)}" }.mkString("{", ", ", "}")

  private def _json_string(s: String): String =
    "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\""

  private def _required_path(args: List[String], key: String): Path =
    _path(args, key).getOrElse(RAISE.invalidArgumentFault(s"Missing --${key}"))

  private def _required_value(args: List[String], key: String): String =
    _value(args, key).getOrElse(RAISE.invalidArgumentFault(s"Missing --${key}"))

  private def _path(args: List[String], key: String): Option[Path] =
    _parsed(args).pathProperty(key)

  private def _paths(args: List[String], key: String): Vector[Path] =
    _value(args, key).toVector.flatMap(_.split(",")).map(_.trim).filter(_.nonEmpty).map(p => Paths.get(p).toAbsolutePath.normalize())

  private def _string_map(args: List[String], key: String): Map[String, String] =
    _value(args, key).map(_parse_string_map).getOrElse(Map.empty)

  private def _parse_string_map(value: String): Map[String, String] = {
    val trimmed = value.trim
    if (trimmed.startsWith("{"))
      _parse_string_map_json(trimmed)
    else
      trimmed.split(",").toVector.map(_.trim).filter(_.nonEmpty).flatMap { kv =>
        kv.split("=", 2).toList match {
          case k :: v :: Nil if k.nonEmpty => Some(k -> v)
          case _ => None
        }
      }.toMap
  }

  private def _parse_string_map_json(value: String): Map[String, String] =
    Try(Json.parse(value))
      .toOption
      .collect { case o: JsObject => o }
      .map(_.fields.map { case (k, v) => k -> _json_value_string(v) }.toMap)
      .getOrElse(RAISE.invalidArgumentFault(s"Invalid JSON map argument: $value"))

  private def _json_value_string(value: JsValue): String = value match {
    case JsNull => "null"
    case JsString(s) => s
    case other => Json.stringify(other)
  }

  private def _json_string_value(json: JsValue, key: String): Option[String] =
    (json \ key).asOpt[String].map(_.trim).filter(_.nonEmpty)

  private def _value(args: List[String], key: String): Option[String] = {
    _parsed(args).property(key)
  }

  private def _values(args: List[String], key: String): Vector[String] =
    _value(args, key).toVector.flatMap(_.split(",")).map(_.trim).filter(_.nonEmpty)

  private val _request_parameters = Vector(
    spec.Parameter.propertyFileOption("save"),
    spec.Parameter.propertyFileOption("main-jar"),
    spec.Parameter.propertyFileOption("project-dir"),
    spec.Parameter.propertyFileOption("lib-jars"),
    spec.Parameter.propertyFileOption("spi-jars"),
    spec.Parameter.propertyFileOption("component-api-descriptor"),
    spec.Parameter.propertyFileOption("car-dir"),
    spec.Parameter.propertyFileOption("default-conf"),
    spec.Parameter.propertyFileOption("dependency-manifest"),
    spec.Parameter.propertyFileOption("web-dir"),
    spec.Parameter.propertyFileOption("web-descriptor"),
    spec.Parameter.propertyFileOption("form-descriptor"),
    spec.Parameter.propertyFileOption("admin-descriptor"),
    spec.Parameter.propertyFileOption("assembly-descriptor"),
    spec.Parameter.propertyFileOption("abi-manifest"),
    spec.Parameter.propertyFileOption("abi-manifest-output"),
    spec.Parameter.propertyFileOption("model-metadata"),
    spec.Parameter.propertyFileOption("scaladoc-dir"),
    spec.Parameter.propertyFileOption("release-source-dir"),
    spec.Parameter.property("release-source-managed-main-sources"),
    spec.Parameter.property("release-source-managed-main-roots"),
    spec.Parameter.property("release-source-managed-test-sources"),
    spec.Parameter.property("release-source-managed-test-roots"),
    spec.Parameter.property("release-source-build-evidence"),
    spec.Parameter.propertyFileOption("source-dir"),
    spec.Parameter.property("source-files"),
    spec.Parameter.property("extension-jars"),
    spec.Parameter.property("application-conf"),
    spec.Parameter.property("name"),
    spec.Parameter.property("version"),
    spec.Parameter.property("component"),
    spec.Parameter.property("extensions"),
    spec.Parameter.property("config"),
    spec.Parameter.property("entities")
  )

  private def _parsed(args: List[String]): CozyCliArgs.Parsed =
    CozyCliArgs.parse(_request_parameters: _*)(args)

  private def _write_text(path: Path, text: String): Unit = {
    Option(path.getParent).foreach(Files.createDirectories(_))
    Files.writeString(path, text, StandardCharsets.UTF_8)
  }

  private def _package_work_root(projectdir: Option[Path]): Path =
    projectdir.getOrElse(Paths.get("").toAbsolutePath.normalize()).resolve("target/cozy/work/package-car")

  private def _write_temp(projectdir: Option[Path], prefix: String, text: String): Path = {
    val root = _package_work_root(projectdir)
    Files.createDirectories(root)
    val path = Files.createTempFile(root, s"$prefix-", ".json")
    Files.writeString(path, text, StandardCharsets.UTF_8)
    path.toAbsolutePath.normalize()
  }

  private def _snapshot_temp(projectdir: Option[Path], prefix: String, source: Path): Path = {
    val root = _package_work_root(projectdir)
    Files.createDirectories(root)
    val path = Files.createTempFile(root, s"$prefix-", ".json")
    Files.write(path, Files.readAllBytes(source))
    path.toAbsolutePath.normalize()
  }

  private def _is_empty_dir(path: Path): Boolean =
    !Files.exists(path) || {
      val stream = Files.walk(path)
      try !stream.iterator().hasNext
      finally stream.close()
    }

  private def _delete_tree(path: Path): Unit =
    if (Files.exists(path)) {
      val stream = Files.walk(path)
      try {
        stream.iterator().asScala.toVector.sortBy(_.getNameCount).reverse.foreach(p => Files.deleteIfExists(p))
      } finally {
        stream.close()
      }
  }
}

/*
 * The staged Scaladoc manifest is deliberately checked in Cozy as well as by
 * sbt-cozy.  The bridge transports a directory, not a second disclosure or
 * resource-selection policy; this admission is the sole CAR consumer of it.
 */
private[cozy] object CozyScaladocStaging {
  val Schema = "cozy.component-scaladoc.v1"
  val ManifestFileName = "scaladoc-manifest.json"

  final case class Entry(path: String, sha256: String)

  final case class Verified(directory: Path, manifest: Path, entries: Vector[Entry]) {
    def archiveEntries: Vector[(Path, String)] =
      entries.map(entry => directory.resolve(entry.path) -> s"scaladoc/${entry.path}") :+
        (manifest -> s"scaladoc/$ManifestFileName")
  }

  def verify(stagingdir: Path, projectdir: Path): Verified = {
    val directory = stagingdir.toAbsolutePath.normalize()
    val projectroot = projectdir.toAbsolutePath.normalize()
    _require_directory(directory, "Scaladoc staging directory")
    val manifest = directory.resolve(ManifestFileName)
    _require_regular_file(manifest, "Scaladoc manifest")
    val parsed = _parse_manifest(manifest)
    if (parsed.schema != Schema)
      _invalid(s"Scaladoc manifest schema is unsupported: ${parsed.schema}")
    if (Files.readString(manifest, StandardCharsets.UTF_8) != _render(parsed))
      _invalid(s"Scaladoc manifest is not canonical: $manifest")
    if (parsed.sourceDigest != sourceDigest(projectroot))
      _invalid(s"Scaladoc source-input digest differs from current Scala sources: $manifest")
    _require_manifest_entries(parsed.entries, directory)
    val actual = _actual_entries(directory)
    if (actual.map(_.path) != parsed.entries.map(_.path))
      _invalid(s"Scaladoc staging entries differ from its manifest: $directory")
    actual.zip(parsed.entries).foreach { case (actualentry, declared) =>
      if (actualentry.sha256 != declared.sha256)
        _invalid(s"Scaladoc staged entry digest differs from its manifest: ${declared.path}")
    }
    if (parsed.contentDigest != _inventory_digest(parsed.entries))
      _invalid(s"Scaladoc staged-content digest differs from its manifest: $manifest")
    if (!parsed.entries.exists(_.path == "index.html"))
      _invalid(s"Scaladoc staging is missing index.html: $directory")
    if (!parsed.entries.exists(entry => _is_search_or_symbol_evidence(entry.path)))
      _invalid(s"Scaladoc staging is missing generated search or symbol evidence: $directory")
    _require_no_source_disclosure(directory, parsed.entries)
    Verified(directory, manifest, parsed.entries)
  }

  def sourceDigest(projectdir: Path): String = {
    val root = projectdir.toAbsolutePath.normalize()
    val sourcedir = root.resolve("src/main/scala")
    if (!Files.exists(sourcedir))
      _inventory_digest(Vector.empty)
    else {
      _require_directory(sourcedir, "Scala source directory")
      val stream = Files.walk(sourcedir)
      try {
        val entries = stream.iterator().asScala.toVector.collect {
          case path if path != sourcedir && Files.isSymbolicLink(path) =>
            _invalid(s"Scala source input must not be a symbolic link: $path")
          case path if Files.isRegularFile(path) && path.getFileName.toString.endsWith(".scala") =>
            val relative = root.relativize(path).toString.replace('\\', '/')
            Entry(relative, _sha256(Files.readAllBytes(path)))
        }.sortBy(_.path)
        _inventory_digest(entries)
      } finally {
        stream.close()
      }
    }
  }

  private final case class Manifest(
    schema: String,
    sourceDigest: String,
    contentDigest: String,
    entries: Vector[Entry]
  )

  private def _parse_manifest(path: Path): Manifest = {
    val json = Try(Json.parse(Files.readString(path, StandardCharsets.UTF_8))).getOrElse(
      _invalid(s"Scaladoc manifest is invalid JSON: $path")
    )
    val schema = (json \ "schema").asOpt[String].getOrElse(_invalid(s"Scaladoc manifest is missing schema: $path"))
    val source = (json \ "sourceDigest").asOpt[String].getOrElse(_invalid(s"Scaladoc manifest is missing sourceDigest: $path"))
    val content = (json \ "contentDigest").asOpt[String].getOrElse(_invalid(s"Scaladoc manifest is missing contentDigest: $path"))
    val entries = (json \ "entries").asOpt[Vector[JsObject]].getOrElse(
      _invalid(s"Scaladoc manifest is missing entries: $path")
    ).map { entry =>
      Entry(
        (entry \ "path").asOpt[String].getOrElse(_invalid(s"Scaladoc manifest entry is missing path: $path")),
        (entry \ "sha256").asOpt[String].getOrElse(_invalid(s"Scaladoc manifest entry is missing sha256: $path"))
      )
    }
    Manifest(schema, source, content, entries)
  }

  private def _require_manifest_entries(entries: Vector[Entry], directory: Path): Unit = {
    if (entries.isEmpty)
      _invalid(s"Scaladoc manifest has no staged entries: $directory")
    if (entries.map(_.path) != entries.map(_.path).sorted || entries.map(_.path).distinct.size != entries.size)
      _invalid(s"Scaladoc manifest entries are not uniquely sorted: $directory")
    entries.foreach { entry =>
      if (!_is_safe_relative_path(entry.path) || entry.path == ManifestFileName)
        _invalid(s"Scaladoc manifest contains an unsafe staged path: ${entry.path}")
      if (!_is_sha256(entry.sha256))
        _invalid(s"Scaladoc manifest contains an invalid SHA-256 digest: ${entry.path}")
    }
  }

  private def _actual_entries(directory: Path): Vector[Entry] = {
    val stream = Files.walk(directory)
    try {
      stream.iterator().asScala.toVector.collect {
        case path if path != directory && Files.isSymbolicLink(path) =>
          _invalid(s"Scaladoc staging must not contain a symbolic link: $path")
        case path if Files.isRegularFile(path) =>
          val relative = directory.relativize(path).toString.replace('\\', '/')
          if (!_is_safe_relative_path(relative))
            _invalid(s"Scaladoc staging contains an unsafe path: $relative")
          if (relative == ManifestFileName) None else Some(Entry(relative, _sha256(Files.readAllBytes(path))))
      }.flatten.sortBy(_.path)
    } finally {
      stream.close()
    }
  }

  private def _require_no_source_disclosure(directory: Path, entries: Vector[Entry]): Unit = {
    entries.foreach { entry =>
      val path = entry.path.toLowerCase(java.util.Locale.ROOT)
      if (path == "src-html" || path.startsWith("src-html/"))
        _invalid(s"Scaladoc source-page material is forbidden: ${entry.path}")
      if (_is_text_path(path)) {
        val text = Files.readString(directory.resolve(entry.path), StandardCharsets.UTF_8)
        if (text.toLowerCase(java.util.Locale.ROOT).contains("src-html") || _source_link.findFirstIn(text).nonEmpty)
          _invalid(s"Scaladoc source link or source-page material is forbidden: ${entry.path}")
      }
    }
  }

  private def _render(manifest: Manifest): String = {
    val entries = manifest.entries.map { entry =>
      s"""{"path":${_quote(entry.path)},"sha256":${_quote(entry.sha256)}}"""
    }.mkString("[", ",", "]")
    s"""{"schema":${_quote(manifest.schema)},"sourceDigest":${_quote(manifest.sourceDigest)},"contentDigest":${_quote(manifest.contentDigest)},"entries":$entries}"""
  }

  private def _inventory_digest(entries: Vector[Entry]): String =
    _sha256(entries.map(entry => s"${entry.path}\t${entry.sha256}\n").mkString.getBytes(StandardCharsets.UTF_8))

  private def _sha256(bytes: Array[Byte]): String =
    MessageDigest.getInstance("SHA-256").digest(bytes).map(byte => f"${byte & 0xff}%02x").mkString

  private def _require_directory(path: Path, label: String): Unit = {
    if (Files.isSymbolicLink(path) || !Files.isDirectory(path))
      _invalid(s"$label is missing, unsafe, or not a directory: $path")
  }

  private def _require_regular_file(path: Path, label: String): Unit = {
    if (Files.isSymbolicLink(path) || !Files.isRegularFile(path))
      _invalid(s"$label is missing, unsafe, or not a regular file: $path")
  }

  private def _is_safe_relative_path(path: String): Boolean = {
    val normalized = path.replace('\\', '/')
    normalized == path &&
      normalized.nonEmpty &&
      !normalized.startsWith("/") &&
      !normalized.endsWith("/") &&
      normalized.split('/').forall(segment => segment.nonEmpty && segment != "." && segment != "..")
  }

  private def _is_search_or_symbol_evidence(path: String): Boolean = {
    val lower = path.toLowerCase(java.util.Locale.ROOT)
    (lower.contains("search") || lower.contains("index")) &&
      (lower.endsWith(".js") || lower.endsWith(".json"))
  }

  private def _is_text_path(path: String): Boolean =
    Vector(".css", ".html", ".js", ".json").exists(path.endsWith)

  private val _source_link = "(?is)<a\\b[^>]*\\bhref\\s*=\\s*[\"'][^\"']+[\"'][^>]*>\\s*source\\s*</a>".r
  private val _sha256_pattern = "^[0-9a-f]{64}$".r

  private def _is_sha256(value: String): Boolean = _sha256_pattern.pattern.matcher(value).matches()

  private def _quote(value: String): String =
    "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\""

  private def _invalid(message: String): Nothing = RAISE.invalidArgumentFault(message)
}
