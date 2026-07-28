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
import java.util.zip.{ZipEntry, ZipFile, ZipOutputStream}
import scala.util.Try
import scala.collection.JavaConverters._
import scala.sys.process._

/*
 * @since   May. 20, 2026
 *  version May. 22, 2026
 *  version Jun. 18, 2026
 * @version Jul. 28, 2026
 * @author  ASAMI, Tomoharu
 */
private[cozy] object CozyArchivePackager {
  def buildCar(args: List[String]): Unit =
    _build_car(args, None)

  private[cozy] def buildCar(
    args: List[String],
    evidence: GenerationCompatibilityEvidence,
    executingCozyVersion: String
  ): Unit =
    _build_car(args, Some(evidence -> executingCozyVersion))

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
    val outputversion = _required_value(args, "version")
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
      val componentapiartifacts = componentapidescriptor.toVector.flatMap(_component_api_artifact_paths(_, name, version))
      _validate_component_api_artifacts(componentapiartifacts, spijars)
      val manifestmetadata = config.mapUnder("packaging.car.manifest_metadata")
      val component = _value(args, "component").orElse(manifestmetadata.get("component")).getOrElse(RAISE.invalidArgumentFault("Missing --component"))
      val packagemetadata = _car_package_metadata(manifestmetadata, component, version)
      assemblydescriptor.foreach(_validate_assembly_descriptor(_, name, version, component))
      val extensionmap = packagemetadata.extensions ++ _string_map(args, "extensions")
      val configmap = config.mapUnder("project.component.config") ++ _string_map(args, "config")
      val entities = _entity_descriptors(args)
      val abidependencies = config.indexedMapsUnder("packaging.car.abi.dependencies").map { dependency =>
        CozyCarAbiManifest.Dependency(
          dependency.getOrElse("name", ""),
          dependency.getOrElse("abiRange", dependency.getOrElse("abi_range", ""))
        )
      }
      val abimanifest = _path(args, "abi-manifest").orElse(_source_abi_manifest(cardir)).map { path =>
        _validate_abi_manifest_coordinate(path, name, version)
        path
      }.getOrElse {
        val content =
          if (modelmetadata.nonEmpty)
            CozyCarAbiManifest.create(modelmetadata, name, version, packagemetadata.component, abidependencies)
          else if (_has_cml_sources(projectdir))
            RAISE.invalidArgumentFault(
              s"CML CAR '${name}' requires generated model metadata when no explicit or source-managed ABI manifest is available."
            )
          else
            _abi_manifest_json(name, version, packagemetadata.component, entities)
        _write_temp("abi-manifest", content)
      }
      _path(args, "abi-manifest-output").foreach(path => _write_text(path, Files.readString(abimanifest, StandardCharsets.UTF_8)))
      val componentdescriptor = _component_descriptor_override(extensionmap, name, version, packagemetadata.component).
        map(_write_temp("component-descriptor", _)).
        orElse(_source_component_descriptor(cardir).map { path =>
          _validate_component_descriptor(Files.readString(path), name, version, packagemetadata.component, "component-descriptor.json")
          path
        }).
        getOrElse {
          _write_temp("component-descriptor", _component_descriptor_json(name, version, packagemetadata.component, extensionmap, configmap, entities))
      }
      def _write_car_(packagedmainjar: Path): Unit = {
        val runtimemanifest = Some(
          RuntimeManifestContext(
            carcontract,
            name,
            version,
            packagemetadata.component
          )
        )
        _write_archive(
          save,
          Vector(
            packagedmainjar -> "component/main.jar"
          ) ++
            _car_entries(cardir) ++
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
      componentapidescriptor match {
        case Some(descriptor) =>
          ComponentApiJarPackager.withImplementationJar(mainjar, descriptor)(_write_car_)
        case None =>
          _write_car_(mainjar)
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
          val copied = _snapshot_temp("generation-provenance", source)
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
      Some(_write_temp("component-dependencies", _dependency_manifest_yaml(provided, shared, local, repositories)))
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
    defaultcomponent: String,
    version: String
  ): CarPackageMetadata = {
    val component = metadata.getOrElse("component", defaultcomponent)
    val componentletnames = _componentlet_names(metadata)
    val reservedkeys = Set("component", "componentlets") ++ metadata.keySet.filter(_.startsWith("componentlet."))
    val passthroughextensions = metadata -- reservedkeys
    val extensions =
      if (componentletnames.isEmpty)
        passthroughextensions
      else
        passthroughextensions + ("componentDescriptorJson" -> _component_descriptor_override_json(component, version, passthroughextensions, componentletnames, metadata))
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
    component: String,
    version: String,
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
    s"""{"component":{"name":${_json_string(component)},"version":${_json_string(version)},"kind":"component","isPrimary":"true"},"componentlets":[${componentlets.mkString(",")}],"extensions":${_json_map(extensions)}}"""
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

  private def _source_component_descriptor(cardir: Option[Path]): Option[Path] =
    cardir.map(_.resolve("component-descriptor.json")).filter(Files.isRegularFile(_))

  private def _source_component_api_descriptor(cardir: Option[Path]): Option[Path] =
    cardir.map(_.resolve("component-api-descriptor.json")).filter(Files.isRegularFile(_))

  private def _component_api_artifact_paths(path: Path, name: String, version: String): Vector[String] = {
    val json = Json.parse(Files.readString(path, StandardCharsets.UTF_8))
    val schemaversion = (json \ "schemaVersion").asOpt[String]
    val componentname = (json \ "component" \ "name").asOpt[String]
    val componentversion = (json \ "component" \ "version").asOpt[String]
    if (schemaversion != Some("cncf.component-api.v1"))
      RAISE.invalidArgumentFault(s"component-api-descriptor.json must use schema cncf.component-api.v1: ${path}")
    if (componentname != Some(name) || componentversion != Some(version))
      RAISE.invalidArgumentFault(
        s"component-api-descriptor.json declares ${componentname.getOrElse("<missing>")}:${componentversion.getOrElse("<missing>")}, but package-car is building ${name}:${version}."
      )
    (json \ "provided").asOpt[Vector[JsObject]].getOrElse(Vector.empty).map { provided =>
      val artifactpath = (provided \ "artifactPath").asOpt[String].map(_.trim).filter(_.nonEmpty).getOrElse {
        RAISE.invalidArgumentFault(s"component-api-descriptor.json provided API is missing artifactPath: ${path}")
      }
      val normalized = Paths.get(artifactpath).normalize().toString.replace('\\', '/')
      if (normalized != artifactpath || !artifactpath.startsWith("spi/") || artifactpath.count(_ == '/') != 1)
        RAISE.invalidArgumentFault(s"Component API artifactPath must be a direct child of spi/: ${artifactpath}")
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
    entries: Vector[(Path, String)],
    placeholderdirs: Vector[String],
    runtimemanifest: Option[RuntimeManifestContext] = None
  ): Unit = {
    Files.createDirectories(archive.getParent)
    Files.deleteIfExists(archive)
    val tempdir = Files.createTempDirectory("cozy-package-")
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
          context.component
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
    component: String
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
    name: String,
    version: String,
    component: String,
    extensions: Map[String, String],
    config: Map[String, String],
    entities: Vector[EntityDescriptor]
  ): String =
    _component_descriptor_override(extensions, name, version, component).getOrElse {
    val effectiveextensions = extensions - "componentDescriptorJson"
      s"""{
         |  "name": ${_json_string(name)},
         |  "version": ${_json_string(version)},
         |  "component": ${_json_string(component)},
         |  "entities": ${_json_entities(entities)},
         |  "extensions": ${_json_map(effectiveextensions)},
         |  "config": ${_json_map(config)}
         |}
         |""".stripMargin
    }

  private def _component_descriptor_override(
    extensions: Map[String, String],
    name: String,
    version: String,
    component: String
  ): Option[String] =
    extensions.get("componentDescriptorJson").map(_.trim).filter(_.nonEmpty).map { text =>
      _validate_component_descriptor(text, name, version, component, "componentDescriptorJson")
      text
    }

  private def _validate_component_descriptor(
    text: String,
    name: String,
    version: String,
    component: String,
    label: String
  ): Unit = {
    val json = Try(Json.parse(text)).getOrElse(RAISE.invalidArgumentFault(s"${label} must be valid JSON."))
    val componentjson = (json \ "component").toOption.collect { case o: JsObject => o }.getOrElse(Json.obj())
    val descriptorname =
      _json_string_value(json, "name").orElse(_json_string_value(componentjson, "name"))
    val descriptorversion =
      _json_string_value(json, "version").orElse(_json_string_value(componentjson, "version"))
    val descriptorcomponent =
      _json_string_value(json, "component")
        .orElse(_json_string_value(json, "componentName"))
        .orElse(_json_string_value(componentjson, "component"))
        .orElse(_json_string_value(componentjson, "componentName"))
        .orElse(descriptorname)
    if (!descriptorname.exists(value => value == name || value == component))
      RAISE.invalidArgumentFault(s"${label} must declare CAR name '${name}' or component name '${component}'.")
    if (!descriptorversion.contains(version))
      RAISE.invalidArgumentFault(s"${label} must declare CAR version '${version}'.")
    if (!descriptorcomponent.contains(component))
      RAISE.invalidArgumentFault(s"${label} must declare component '${component}'.")
  }

  private def _validate_assembly_descriptor(
    path: Path,
    name: String,
    version: String,
    component: String
  ): Unit = {
    val descriptor = CozyProjectYamlConfig.load(path)
    val descriptorversion = descriptor.value("version")
    if (!descriptorversion.contains(version))
      RAISE.invalidArgumentFault(
        s"assembly-descriptor.yaml must declare subsystem version '${version}' for CAR '${name}', but declared '${descriptorversion.getOrElse("<missing>")}'."
      )
    val componententry =
      descriptor.indexedMapsUnder("components").find(_.get("name").contains(component))
    val componentversion = componententry.flatMap(_.get("version"))
    if (!componentversion.contains(version))
      RAISE.invalidArgumentFault(
        s"assembly-descriptor.yaml must declare component '${component}' at CAR version '${version}', but declared '${componentversion.getOrElse("<missing>")}'."
      )
  }

  private def _abi_manifest_json(
    name: String,
    version: String,
    component: String,
    entities: Vector[EntityDescriptor]
  ): String =
    s"""{
       |  "format": "cozy.car.abi-manifest.v1",
       |  "car": {
       |    "name": ${_json_string(name)},
       |    "version": ${_json_string(version)}
       |  },
       |  "abi": {
       |    "version": 1,
       |    "exports": {
       |      "components": [
       |        {
       |          "name": ${_json_string(component)}
       |        }
       |      ],
       |      "operations": [],
       |      "entities": ${_abi_json_entities(entities)}
       |    },
       |    "dependencies": []
       |  }
       |}
       |""".stripMargin

  private def _validate_abi_manifest_coordinate(
    path: Path,
    name: String,
    version: String
  ): Unit = {
    val text = Files.readString(path, StandardCharsets.UTF_8)
    val json = Try(Json.parse(text)).getOrElse(RAISE.invalidArgumentFault(s"Invalid ABI manifest JSON: ${path}"))
    val car = (json \ "car").getOrElse(Json.obj())
    val manifestname = _json_string_value(car, "name").orElse(_json_string_value(json, "name")).getOrElse("unknown")
    val manifestversion = _json_string_value(car, "version").orElse(_json_string_value(json, "version")).getOrElse("0.0.0")
    if (manifestname != name || manifestversion != version)
      RAISE.invalidArgumentFault(s"ABI manifest ${path} declares ${manifestname}:${manifestversion}, but package-car is building ${name}:${version}.")
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

  private def _write_temp(prefix: String, text: String): Path = {
    val path = Files.createTempFile(prefix, ".json")
    Files.writeString(path, text, StandardCharsets.UTF_8)
    path.toAbsolutePath.normalize()
  }

  private def _snapshot_temp(prefix: String, source: Path): Path = {
    val path = Files.createTempFile(prefix, ".json")
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
