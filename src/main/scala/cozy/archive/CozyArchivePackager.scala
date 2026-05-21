package cozy.archive

import org.goldenport.RAISE
import cozy.config.CozyProjectYamlConfig
import play.api.libs.json._
import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path, Paths}
import java.util.zip.{ZipEntry, ZipOutputStream}
import scala.util.Try
import scala.collection.JavaConverters._

/*
 * @since   May. 20, 2026
 * @version May. 22, 2026
 * @author  ASAMI, Tomoharu
 */
private[cozy] object CozyArchivePackager {
  def buildCar(args: List[String]): Unit = {
    val save = _required_path(args, "save")
    val mainjar = _required_path(args, "main-jar")
    val projectdir = _path(args, "project-dir")
    val config = _project_config(projectdir)
    val libjars = if (_include_dependencies(projectdir, config)) _paths(args, "lib-jars") else Vector.empty
    val spijars = _paths(args, "spi-jars")
    val cardir = _path(args, "car-dir").orElse(_car_dir(projectdir, config))
    val defaultconf = _path(args, "default-conf").orElse(cardir.map(_.resolve("config/default.conf")).filter(Files.isRegularFile(_)))
    val dependencymanifest = _path(args, "dependency-manifest").orElse(_dependency_manifest(projectdir, config))
    val webdir = _path(args, "web-dir").orElse(projectdir.map(_.resolve("src/main/web")).filter(Files.isDirectory(_)))
    val webinfdescriptors = _web_inf_descriptors(args, projectdir, config)
    val assemblydescriptor = _path(args, "assembly-descriptor").orElse(cardir.map(_.resolve("assembly-descriptor.yaml")).filter(Files.isRegularFile(_)))
    val name = _required_value(args, "name")
    val version = _required_value(args, "version")
    val manifestmetadata = config.mapUnder("packaging.car.manifest_metadata")
    val component = _value(args, "component").orElse(manifestmetadata.get("component")).getOrElse(RAISE.invalidArgumentFault("Missing --component"))
    val packagemetadata = _car_package_metadata(manifestmetadata, component)
    val extensionmap = packagemetadata.extensions ++ _string_map(args, "extensions")
    val configmap = _string_map(args, "config")
    val entities = _entity_descriptors(args)
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
        Vector(_write_temp("component-descriptor", _component_descriptor_json(name, version, packagemetadata.component, extensionmap, configmap, entities)) -> "component-descriptor.json"),
      Vector("component", "lib", "spi", "config", "web")
    )
  }

  private def _project_config(projectdir: Option[Path]): CozyProjectYamlConfig.Config =
    projectdir.map { dir =>
      val project = CozyProjectYamlConfig.load(dir.resolve("project.yaml"))
      val local = CozyProjectYamlConfig.load(dir.resolve(".cozy/config.yaml"))
      project.merge(local)
    }.getOrElse(CozyProjectYamlConfig.Config.empty)

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

  private def _dependency_manifest(projectdir: Option[Path], config: CozyProjectYamlConfig.Config): Option[Path] = {
    val provided = config.list("packaging.car.dependencies.provided")
    val shared = config.list("packaging.car.dependencies.shared")
    val local = config.list("packaging.car.dependencies.local")
    val repositories = config.list("packaging.car.dependencies.repositories")
    _validate_component_owned_dependencies(projectdir, config, shared, local)
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
    shared: Vector[String],
    local: Vector[String]
  ): Unit = {
    if (_cncf_runtime_configured(config)) {
      _runtime_catalog(projectdir, config) match {
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

  private def _runtime_catalog(
    projectdir: Option[Path],
    config: CozyProjectYamlConfig.Config
  ): Option[RuntimeCatalog] =
    _runtime_catalog_paths(projectdir, config).collectFirst {
      case path if Files.isRegularFile(path) =>
        val catalog = CozyProjectYamlConfig.load(path)
        RuntimeCatalog(_base_provided_modules(catalog))
    }.filter(_.baseprovidedmodules.nonEmpty).
      orElse(_runtime_catalog_urls(config).flatMap(_runtime_catalog_url).find(_.baseprovidedmodules.nonEmpty))

  private def _runtime_catalog_paths(
    projectdir: Option[Path],
    config: CozyProjectYamlConfig.Config
  ): Vector[Path] = {
    val basedir = projectdir.getOrElse(Paths.get(".").toAbsolutePath.normalize())
    val configured =
      _runtime_catalog_config_values(config).
        filterNot(_is_url).
        map(value => _config_path(basedir, value)).
        toVector
    val cncfprojects =
      _cncf_runtime_project_dirs(projectdir, config).map(_.resolve("target/cncf.d/runtime-catalog.yaml"))
    val local = projectdir.toVector.flatMap { dir =>
      Vector(
        dir.resolve("target/cncf.d/runtime-catalog.yaml"),
        dir.resolve("repository/textus/runtime-catalog.yaml"),
        dir.resolve("src/main/catalog/cncf.yaml")
      )
    }
    (configured ++ cncfprojects ++ local).distinct
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
        RuntimeCatalog(_base_provided_modules(catalog))
      } finally {
        in.close()
      }
    }.toOption

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

  private def _car_package_metadata(metadata: Map[String, String], defaultcomponent: String): CarPackageMetadata = {
    val component = metadata.getOrElse("component", defaultcomponent)
    val componentletnames = _componentlet_names(metadata)
    val reservedkeys = Set("component", "componentlets") ++ metadata.keySet.filter(_.startsWith("componentlet."))
    val passthroughextensions = metadata -- reservedkeys
    val extensions =
      if (componentletnames.isEmpty)
        passthroughextensions
      else
        passthroughextensions + ("componentDescriptorJson" -> _component_descriptor_override_json(component, passthroughextensions, componentletnames, metadata))
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
    s"""{"component":{"name":${_json_string(component)},"kind":"component","isPrimary":"true"},"componentlets":[${componentlets.mkString(",")}],"extensions":${_json_map(extensions)}}"""
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
    cardir.toVector.flatMap(_archive_sources(_))

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
    _component_descriptor_override(extensions).getOrElse {
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

  private def _component_descriptor_override(extensions: Map[String, String]): Option[String] =
    extensions.get("componentDescriptorJson").map(_.trim).filter(_.nonEmpty)

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
    _value(args, key).map(p => Paths.get(p).toAbsolutePath.normalize())

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

  private def _value(args: List[String], key: String): Option[String] = {
    val prefix = s"--${key}="
    args.collectFirst {
      case s if s.startsWith(prefix) => s.substring(prefix.length)
    }.orElse {
      args.sliding(2).collectFirst {
        case List(flag, value) if flag == s"--${key}" => value
      }
    }
  }

  private def _values(args: List[String], key: String): Vector[String] =
    _value(args, key).toVector.flatMap(_.split(",")).map(_.trim).filter(_.nonEmpty)

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
