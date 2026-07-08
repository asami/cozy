package cozy.archive

import org.goldenport.RAISE
import cozy.config.CozyProjectYamlConfig
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
 * @version Jul.  8, 2026
 * @author  ASAMI, Tomoharu
 */
private[cozy] object CozyArchivePackager {
  def buildCar(args: List[String]): Unit = {
    val save = _required_path(args, "save")
    val mainjar = _required_path(args, "main-jar")
    val projectdir = _path(args, "project-dir")
    val config = _project_config(projectdir)
    val alllibjars = _paths(args, "lib-jars")
    val validationjars = mainjar +: alllibjars
    _validate_cncf_runtime_metadata(projectdir, config, validationjars)
    val libjars = if (_include_dependencies(projectdir, config)) alllibjars else Vector.empty
    val spijars = _paths(args, "spi-jars")
    val cardir = _path(args, "car-dir").orElse(_car_dir(projectdir, config))
    val defaultconf = _path(args, "default-conf").orElse(cardir.map(_.resolve("config/default.conf")).filter(Files.isRegularFile(_)))
    val dependencymanifest = _path(args, "dependency-manifest").orElse(_dependency_manifest(projectdir, config, validationjars))
    val webdir = _path(args, "web-dir").orElse(projectdir.map(_.resolve("src/main/web")).filter(Files.isDirectory(_)))
    val webinfdescriptors = _web_inf_descriptors(args, projectdir, config)
    val assemblydescriptor = _path(args, "assembly-descriptor").orElse(cardir.map(_.resolve("assembly-descriptor.yaml")).filter(Files.isRegularFile(_)))
    val name = _required_value(args, "name")
    val version = _required_value(args, "version")
    val manifestmetadata = config.mapUnder("packaging.car.manifest_metadata")
    val component = _value(args, "component").orElse(manifestmetadata.get("component")).getOrElse(RAISE.invalidArgumentFault("Missing --component"))
    val packagemetadata = _car_package_metadata(manifestmetadata, component, version)
    val extensionmap = packagemetadata.extensions ++ _string_map(args, "extensions")
    val configmap = config.mapUnder("project.component.config") ++ _string_map(args, "config")
    val entities = _entity_descriptors(args)
    val abimanifest = _path(args, "abi-manifest").orElse(_source_abi_manifest(cardir)).map { path =>
      _validate_abi_manifest_coordinate(path, name, version)
      path
    }.getOrElse {
      _write_temp("abi-manifest", _abi_manifest_json(name, version, packagemetadata.component, entities))
    }
    val componentdescriptor = _component_descriptor_override(extensionmap, name, version, packagemetadata.component).
      map(_write_temp("component-descriptor", _)).
      orElse(_source_component_descriptor(cardir).map { path =>
        _validate_component_descriptor(Files.readString(path), name, version, packagemetadata.component, "component-descriptor.json")
        path
      }).
      getOrElse {
        _write_temp("component-descriptor", _component_descriptor_json(name, version, packagemetadata.component, extensionmap, configmap, entities))
      }
    _write_archive(
      save,
      Vector(
        mainjar -> "component/main.jar"
      ) ++
        _car_entries(cardir) ++
        libjars.map(p => p -> s"lib/${p.getFileName}") ++
        spijars.map(p => p -> s"spi/${p.getFileName}") ++
        defaultconf.toVector.map(_ -> "config/default.conf") ++
        dependencymanifest.toVector.map(_ -> "component-dependencies.yaml") ++
        assemblydescriptor.toVector.map(_ -> "assembly-descriptor.yaml") ++
        _web_entries(webdir) ++
        webinfdescriptors ++
        Vector(abimanifest -> "abi-manifest.json") ++
        Vector(componentdescriptor -> "component-descriptor.json"),
      Vector("component", "lib", "spi", "config", "web")
    )
  }

  private def _project_config(projectdir: Option[Path]): CozyProjectYamlConfig.Config =
    projectdir.map(CozyProjectYamlConfig.loadProjectConfig).getOrElse(CozyProjectYamlConfig.Config.empty)

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

  private def _validate_cncf_runtime_metadata(
    projectdir: Option[Path],
    config: CozyProjectYamlConfig.Config,
    validationjars: Vector[Path]
  ): Unit = {
    val minimum = config.value("packaging.car.runtime.cncf.minimum")
    val maximum = config.value("packaging.car.runtime.cncf.maximum")
    val excluded = config.list("packaging.car.runtime.cncf.excluded")
    val tested = config.list("packaging.car.runtime.cncf.tested")
    if (minimum.nonEmpty || maximum.nonEmpty || excluded.nonEmpty || tested.nonEmpty) {
      _resolved_cncf_runtime_version(projectdir, config, validationjars) match {
        case Some(version) =>
          minimum.foreach { x =>
            if (_compare_version(version, x) < 0)
              RAISE.invalidArgumentFault(
                s"Resolved CNCF runtime version '${version}' is below project.yaml packaging.car.runtime.cncf.minimum '${x}'"
              )
          }
          maximum.foreach { x =>
            if (_compare_version(version, x) > 0)
              RAISE.invalidArgumentFault(
                s"Resolved CNCF runtime version '${version}' exceeds project.yaml packaging.car.runtime.cncf.maximum '${x}'"
              )
          }
          if (excluded.contains(version))
            RAISE.invalidArgumentFault(
              s"Resolved CNCF runtime version '${version}' is listed in project.yaml packaging.car.runtime.cncf.excluded"
            )
          if (tested.nonEmpty && !tested.contains(version))
            RAISE.invalidArgumentFault(
              s"project.yaml packaging.car.runtime.cncf.tested must include resolved CNCF runtime version '${version}'"
            )
        case None =>
          Console.err.println("[cozy] warning: CNCF runtime version is unavailable; skipping runtime metadata compatibility validation")
      }
    }
  }

  private def _resolved_cncf_runtime_version(
    projectdir: Option[Path],
    config: CozyProjectYamlConfig.Config,
    validationjars: Vector[Path]
  ): Option[String] =
    config.value("packaging.car.runtime.cncf.version").
      orElse(config.value("runtime.cncf.version")).
      orElse(_runtime_catalog(projectdir, config, validationjars).flatMap(_.version))

  private def _dependency_manifest(
    projectdir: Option[Path],
    config: CozyProjectYamlConfig.Config,
    validationjars: Vector[Path]
  ): Option[Path] = {
    val provided = config.list("packaging.car.dependencies.provided")
    val shared = config.list("packaging.car.dependencies.shared")
    val local = config.list("packaging.car.dependencies.local")
    val repositories = config.list("packaging.car.dependencies.repositories")
    _validate_component_owned_dependencies(projectdir, config, validationjars, shared, local)
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
    validationjars: Vector[Path],
    shared: Vector[String],
    local: Vector[String]
  ): Unit = {
    if ((shared.nonEmpty || local.nonEmpty) && _cncf_runtime_configured(config)) {
      _runtime_base_catalog(projectdir, config, validationjars) match {
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

  private final case class RuntimeCatalog(version: Option[String], baseprovidedmodules: Set[String]) {
    def hasRuntimeData: Boolean =
      version.nonEmpty || baseprovidedmodules.nonEmpty
  }

  private def _runtime_catalog(
    projectdir: Option[Path],
    config: CozyProjectYamlConfig.Config,
    validationjars: Vector[Path]
  ): Option[RuntimeCatalog] =
    _runtime_catalog_candidates(projectdir, config, validationjars).find(_.hasRuntimeData)

  private def _runtime_base_catalog(
    projectdir: Option[Path],
    config: CozyProjectYamlConfig.Config,
    validationjars: Vector[Path]
  ): Option[RuntimeCatalog] =
    _runtime_catalog_candidates(projectdir, config, validationjars).find(_.baseprovidedmodules.nonEmpty)

  private def _runtime_catalog_candidates(
    projectdir: Option[Path],
    config: CozyProjectYamlConfig.Config,
    validationjars: Vector[Path]
  ): Vector[RuntimeCatalog] =
    _explicit_runtime_catalog_paths(projectdir, config).collect {
      case path if Files.isRegularFile(path) =>
        val catalog = CozyProjectYamlConfig.load(path)
        _runtime_catalog_from_config(catalog)
    } ++
      _runtime_catalog_urls(config).flatMap(_runtime_catalog_url) ++
      _runtime_catalog_jars(validationjars) ++
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
    if (Files.isRegularFile(path) && path.getFileName.toString.endsWith(".jar"))
      Try {
        val zip = new ZipFile(path.toFile)
        try {
          Option(zip.getEntry("META-INF/cncf/runtime.yaml")).map { entry =>
            val in = zip.getInputStream(entry)
            try {
              val lines = scala.io.Source.fromInputStream(in, "UTF-8").getLines().toVector
              val catalog = CozyProjectYamlConfig.parse(lines)
              _runtime_catalog_from_config(catalog)
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
    RuntimeCatalog(_cncf_runtime_version(config), _base_provided_modules(config))

  private def _cncf_runtime_version(config: CozyProjectYamlConfig.Config): Option[String] =
    config.value("version").
      orElse(config.value("runtime.version")).
      orElse(config.value("cncf.version")).
      orElse(config.value("module").flatMap(_coordinate_version))

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

  private def _coordinate_version(coordinate: String): Option[String] = {
    val parts = coordinate.split(":").toVector.map(_.trim).filter(_.nonEmpty)
    if (parts.length >= 3)
      Some(parts.last)
    else
      None
  }

  private def _compare_version(left: String, right: String): Int = {
    def _parts_(value: String): Vector[String] =
      value.split("[.\\-+_]").toVector.map(_.trim).filter(_.nonEmpty)
    def _number_(value: String): Option[BigInt] =
      if (value.forall(_.isDigit)) Some(BigInt(value)) else None
    def _compare_part_(l: String, r: String): Int =
      (_number_(l), _number_(r)) match {
        case (Some(a), Some(b)) => a.compare(b)
        case (Some(_), None) => 1
        case (None, Some(_)) => -1
        case (None, None) => l.compareToIgnoreCase(r)
      }
    def _remaining_(parts: Vector[String], index: Int): Int =
      parts.drop(index).find(_.nonEmpty).map { x =>
        _number_(x) match {
          case Some(n) => n.signum
          case None => -1
        }
      }.getOrElse(0)

    val lparts = _parts_(left)
    val rparts = _parts_(right)
    val size = math.max(lparts.length, rparts.length)
    (0 until size).foldLeft(0) {
      case (0, index) if index >= lparts.length => -_remaining_(rparts, index)
      case (0, index) if index >= rparts.length => _remaining_(lparts, index)
      case (0, index) => _compare_part_(lparts(index), rparts(index))
      case (r, _) => r
    }
  }

  private def _dependency_manifest_yaml(
    provided: Vector[String],
    shared: Vector[String],
    local: Vector[String],
    repositories: Vector[String]
  ): String = {
    def section(name: String, values: Vector[String]): String =
      if (values.isEmpty) ""
      else values.map(v => s"    - ${_yaml_string(v)}\n").mkString(s"  $name:\n", "", "")
    "dependencies:\n" +
      section("provided", provided) +
      section("shared", shared) +
      section("local", local) +
      section("repositories", repositories)
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
        _is_historical_abi_manifest(rel)
    }

  private def _source_abi_manifest(cardir: Option[Path]): Option[Path] =
    cardir.map(_.resolve("abi-manifest.json")).filter(Files.isRegularFile(_))

  private def _source_component_descriptor(cardir: Option[Path]): Option[Path] =
    cardir.map(_.resolve("component-descriptor.json")).filter(Files.isRegularFile(_))

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
    placeholderdirs: Vector[String]
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
      _zip_dir(tempdir, archive)
    } finally {
      _delete_tree(tempdir)
    }
  }

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
      _json_string_value(componentjson, "name").orElse(_json_string_value(json, "name"))
    val descriptorversion =
      _json_string_value(componentjson, "version").orElse(_json_string_value(json, "version"))
    val descriptorcomponent =
      _json_string_value(componentjson, "component")
        .orElse(_json_string_value(componentjson, "componentName"))
        .orElse(_json_string_value(json, "component"))
        .orElse(_json_string_value(json, "componentName"))
        .orElse(descriptorname)
    if (!descriptorname.contains(name))
      RAISE.invalidArgumentFault(s"${label} must declare CAR name '${name}'.")
    if (!descriptorversion.contains(version))
      RAISE.invalidArgumentFault(s"${label} must declare CAR version '${version}'.")
    if (!descriptorcomponent.contains(component))
      RAISE.invalidArgumentFault(s"${label} must declare component '${component}'.")
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
    usageKind: Option[String],
    operationKind: Option[String],
    applicationDomain: Option[String]
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
      usageKind = kv.get("usageKind").orElse(kv.get("usage_kind")).orElse(kv.get("entityUsage")).orElse(kv.get("entity_usage")),
      operationKind = kv.get("operationKind").orElse(kv.get("operation_kind")).orElse(kv.get("entityOperationKind")).orElse(kv.get("entity_operation_kind")),
      applicationDomain = kv.get("applicationDomain").orElse(kv.get("application_domain")).orElse(kv.get("entityApplicationDomain")).orElse(kv.get("entity_application_domain"))
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
      entity.usageKind.map("usageKind" -> _),
      entity.operationKind.map("operationKind" -> _),
      entity.applicationDomain.map("applicationDomain" -> _)
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
    spec.Parameter.propertyFileOption("car-dir"),
    spec.Parameter.propertyFileOption("default-conf"),
    spec.Parameter.propertyFileOption("dependency-manifest"),
    spec.Parameter.propertyFileOption("web-dir"),
    spec.Parameter.propertyFileOption("web-descriptor"),
    spec.Parameter.propertyFileOption("form-descriptor"),
    spec.Parameter.propertyFileOption("admin-descriptor"),
    spec.Parameter.propertyFileOption("assembly-descriptor"),
    spec.Parameter.propertyFileOption("abi-manifest"),
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
