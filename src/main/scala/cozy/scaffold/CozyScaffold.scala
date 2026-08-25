package cozy.scaffold

import org.goldenport.RAISE
import org.goldenport.value._
import cozy.config.CozyProjectYamlConfig
import cozy.runtime.CozyCliArgs
import org.goldenport.cli.spec
import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path, Paths}
import scala.util.Try
import scala.collection.JavaConverters._

/*
 * @since   May. 20, 2026
 *  version May. 25, 2026
 *  version Jun. 27, 2026
 *  version Jul. 29, 2026
 * @version Aug. 26, 2026
 * @author  ASAMI, Tomoharu
 */
private[cozy] object CozyScaffold {
  private val _default_sbt_version = org.simplemodeling.cozy.BuildInfo.scaffoldSbtVersion
  private val _default_sbt_cozy_version = org.simplemodeling.cozy.BuildInfo.sbtCozyVersion
  private val _default_scaffold_version = org.simplemodeling.cozy.BuildInfo.scaffoldInitialVersion
  private val _default_scala_version = org.simplemodeling.cozy.BuildInfo.scaffoldScalaVersion
  private val _default_cozy_version = org.simplemodeling.cozy.BuildInfo.scaffoldCozyVersion
  private val _default_textus_user_account_version =
    org.simplemodeling.cozy.BuildInfo.scaffoldTextusUserAccountVersion

  case class CarDependencyVersions(
    cncfVersion: String,
    simpleModelingModelVersion: String,
    cncfCollaboratorApiVersion: String
  )
  object CarDependencyVersions {
    def default: CarDependencyVersions = CarDependencyVersions(
      org.simplemodeling.cozy.BuildInfo.cncfVersion,
      org.simplemodeling.cozy.BuildInfo.simpleModelingModelVersion,
      org.simplemodeling.cozy.BuildInfo.cncfCollaboratorApiVersion
    )

    def default(config: CozyProjectYamlConfig.Config): CarDependencyVersions = {
      val d = default
      CarDependencyVersions(
        config.value("generation.versions.cncf").getOrElse(d.cncfVersion),
        config.value("generation.versions.simplemodeling_model").getOrElse(d.simpleModelingModelVersion),
        config.value("generation.versions.cncf_collaborator_api").getOrElse(d.cncfCollaboratorApiVersion)
      )
    }

    def isOption(p: String): Boolean =
      isInlineOption(p) || isFlagOption(p)

    def isInlineOption(p: String): Boolean =
      false

    def isFlagOption(p: String): Boolean =
      p == "--cncf-version" ||
      p == "--simplemodeling-model-version" ||
      p == "--cncf-collaborator-api-version"

    def create(args: List[String]): CarDependencyVersions = {
      create(args, CozyProjectYamlConfig.loadOperationDefaults(Paths.get(".").toAbsolutePath.normalize()))
    }

    def create(args: List[String], config: CozyProjectYamlConfig.Config): CarDependencyVersions = {
      val d = default(config)
      CarDependencyVersions(
        _option(args, "cncf-version").getOrElse(d.cncfVersion),
        _option(args, "simplemodeling-model-version").getOrElse(d.simpleModelingModelVersion),
        _option(args, "cncf-collaborator-api-version").getOrElse(d.cncfCollaboratorApiVersion)
      )
    }

    private def _option(args: List[String], key: String): Option[String] = {
      CozyCliArgs.parse(spec.Parameter.property(key))(args).property(key)
    }
  }

  sealed trait ProjectLayoutStyle
  object ProjectLayoutStyle {
    case object CarOnly extends ProjectLayoutStyle
    case object CarSar extends ProjectLayoutStyle

    def isOption(p: String): Boolean =
      isInlineOption(p) || isFlagOption(p)

    def isInlineOption(p: String): Boolean =
      false

    def isFlagOption(p: String): Boolean =
      p == "--style"

    def create(args: List[String]): ProjectLayoutStyle =
      _option(args).map {
        case "car" => CarOnly
        case "car-sar" => CarSar
        case other => RAISE.invalidArgumentFault(s"Unsupported project style: ${other}")
      }.getOrElse(CarOnly)

    private def _option(args: List[String]): Option[String] = {
      CozyCliArgs.parse(spec.Parameter.property("style"))(args).property("style")
    }
  }

  sealed trait ProjectFilePolicy {
    def isSkip: Boolean = this == ProjectFilePolicy.Skip
  }
  object ProjectFilePolicy {
    case object Default extends ProjectFilePolicy
    case object Skip extends ProjectFilePolicy
    case object Overwrite extends ProjectFilePolicy

    def isPolicyOption(p: String): Boolean =
      isInlineOption(p) || isFlagOption(p)

    def isFlagOption(p: String): Boolean =
      p == "--no-project-files" ||
      p == "--no-scaffold-files" ||
      p == "--overwrite-project-files" ||
      p == "--force-project-files"

    def isInlineOption(p: String): Boolean =
      false

    def create(args: List[String]): ProjectFilePolicy =
      if (args.exists(x => x == "--no-project-files" || x == "--no-scaffold-files"))
        Skip
      else if (args.exists(x => x == "--overwrite-project-files" || x == "--force-project-files"))
        Overwrite
      else
        Default
  }

  final case class CarScaffoldConfig(
    componentName: String,
    serviceName: String,
    entityName: String,
    commandOperationName: String,
    queryOperationName: String,
    packageName: String,
    artifactName: String,
    organization: String,
    version: String,
    boundedContext: String,
    domain: String,
    gitignore: Boolean,
    readme: Boolean,
    tests: Boolean,
    mcpReadyService: Boolean = false
  ) {
    def componentClassStem: String = componentName
    def serviceClassStem: String = serviceName
    def entityClassStem: String = entityName
    def commandOperationClassStem: String = commandOperationName
    def queryOperationClassStem: String = queryOperationName
    def commandOperationMethodName: String = CarScaffoldConfig._lower_camel(commandOperationName)
    def queryOperationMethodName: String = CarScaffoldConfig._lower_camel(queryOperationName)

    def serviceSlug: String = CarScaffoldConfig.kebab(serviceName)
    def entitySlug: String = CarScaffoldConfig.kebab(entityName)
    def commandOperationSlug: String = CarScaffoldConfig.kebab(commandOperationName)
    def queryOperationSlug: String = CarScaffoldConfig.kebab(queryOperationName)
    def modelFileName: String =
      if (isDefault) "sample.cml" else s"${artifactName}.cml"

    def scalaPackageDir(root: String): Path =
      packageName.split("\\.").foldLeft(Paths.get(root))((z, x) => z.resolve(x))

    def serviceLoaderClassName: String =
      s"${packageName}.impl.ComponentFactory"

    def isDefault: Boolean =
      componentName == "Sample" &&
      serviceName == "Notice" &&
      entityName == "Notice" &&
      commandOperationName == "PostNotice" &&
      queryOperationName == "SearchNotices" &&
      packageName == "domain" &&
      artifactName == "sample" &&
      organization == "com.example" &&
      version == _default_scaffold_version &&
      boundedContext == "default" &&
      domain == "default"
  }
  object CarScaffoldConfig {
    private def _lower_camel(value: String): String =
      value.headOption.map(_.toLower).fold(value)(x => s"$x${value.drop(1)}")

    private val _value_options = Set(
      "component",
      "service-name",
      "entity",
      "entity-name",
      "command-operation",
      "query-operation",
      "package",
      "name",
      "organization",
      "version",
      "bounded-context",
      "domain"
    )
    private val _switch_options = Set("gitignore", "readme", "tests", "mcp-ready-service")

    def isFlagOption(p: String): Boolean =
      _value_options.exists(x => p == s"--${x}")

    def isInlineOption(p: String): Boolean =
      false

    def isSwitchOption(p: String): Boolean =
      _switch_options.exists(x => p == s"--${x}")

    def create(
      args: List[String],
      save: Path,
      style: ProjectLayoutStyle = ProjectLayoutStyle.CarOnly
    ): CarScaffoldConfig = {
      val component = _option(args, "component").map(_class_name).getOrElse("Sample")
      val service = _option(args, "service-name").map(_class_name).getOrElse("Notice")
      val entity = _option(args, "entity").orElse(_option(args, "entity-name")).map(_class_name).getOrElse("Notice")
      val commandoperation = _option(args, "command-operation").map(_class_name).getOrElse("PostNotice")
      val queryoperation = _option(args, "query-operation").map(_class_name).getOrElse("SearchNotices")
      val artifact = _option(args, "name").orElse {
        if (component != "Sample")
          Some(_kebab(component))
        else
          style match {
            case ProjectLayoutStyle.CarOnly => Some("sample")
            case ProjectLayoutStyle.CarSar => Some(appNameFromPath(save))
          }
      }.getOrElse("sample")
      CarScaffoldConfig(
        component,
        service,
        entity,
        commandoperation,
        queryoperation,
        _option(args, "package").getOrElse("domain"),
        artifact,
        _option(args, "organization").getOrElse("com.example"),
        _option(args, "version").getOrElse(_default_scaffold_version),
        _option(args, "bounded-context").getOrElse("default"),
        _option(args, "domain").getOrElse("default"),
        args.contains("--gitignore"),
        args.contains("--readme"),
        args.contains("--tests"),
        args.contains("--mcp-ready-service")
      )
    }

    private def _option(args: List[String], key: String): Option[String] = {
      CozyCliArgs.parse(spec.Parameter.property(key))(args).property(key)
    }

    private def _class_name(p: String): String =
      p.split("[^A-Za-z0-9]+").toVector.filter(_.nonEmpty).map { x =>
        x.head.toUpper + x.drop(1)
      }.mkString match {
        case "" => "Sample"
        case x => x
      }

    private[scaffold] def kebab(p: String): String =
      p.replaceAll("([a-z0-9])([A-Z])", "$1-$2").toLowerCase(java.util.Locale.ROOT)

    private def _kebab(p: String): String =
      kebab(p)
  }

  final case class ComponentInitConfig(
    save: Path,
    style: ProjectLayoutStyle,
    scaffold: CarScaffoldConfig,
    displayName: String,
    private[cozy] val projectidentity: Option[CozyProjectYamlConfig.ProjectIdentity] = None
  )
  object ComponentInitConfig {
    def unapply(config: ComponentInitConfig): Option[(Path, ProjectLayoutStyle, CarScaffoldConfig, String)] =
      Some((config.save, config.style, config.scaffold, config.displayName))

    def create(args: List[String]): ComponentInitConfig = {
      val save = _path_option(args, "save").getOrElse {
        RAISE.invalidArgumentFault("Missing --save for init component")
      }
      val config = _path_option(args, "config") match {
        case Some(path) =>
          if (!Files.isRegularFile(path))
            RAISE.invalidArgumentFault(s"Config file not found: ${path}")
          else
            CozyProjectYamlConfig.load(path)
        case None =>
          CozyProjectYamlConfig.Config.empty
      }
      val kind = _option(args, "kind").
        orElse(config.value("project.component.kind")).
        orElse(config.value("project.kind")).
        getOrElse("car")
      val style = kind match {
        case "car" => ProjectLayoutStyle.CarOnly
        case "car-sar" => ProjectLayoutStyle.CarSar
        case other => RAISE.invalidArgumentFault(s"Unsupported component init kind: ${other}")
      }
      val projectidentity = config.projectIdentity match {
        case Right(identity) => identity
        case Left(error) => RAISE.invalidArgumentFault(error.toString)
      }
      val artifact = projectidentity.map(_.projection.mavenArtifactId()).getOrElse {
        _option(args, "name").
          orElse(config.value("project.name")).
          getOrElse {
            val rawcomponent = _option(args, "component-name").
              orElse(_option(args, "component")).
              orElse(config.value("project.component.name")).
              getOrElse("Sample")
            _kebab(_class_name(rawcomponent))
          }
      }
      val rawcomponent = _option(args, "component-name").
        orElse(_option(args, "component")).
        orElse(config.value("cml.component.name")).
        orElse(config.value("cml.component.className")).
        orElse(config.value("project.component.className")).
        orElse(config.value("project.component.name")).
        getOrElse(artifact)
      val component = projectidentity.map(_.localId.value()).getOrElse(_class_name(rawcomponent))
      val service = _option(args, "service-name").
        orElse(config.value("cml.service.name")).
        map(_class_name).
        getOrElse("Notice")
      val entity = _option(args, "entity").
        orElse(_option(args, "entity-name")).
        orElse(config.value("cml.entity.name")).
        map(_class_name).
        getOrElse("Notice")
      val commandoperation = _option(args, "command-operation").
        orElse(config.value("cml.operation.command")).
        map(_class_name).
        getOrElse("PostNotice")
      val queryoperation = _option(args, "query-operation").
        orElse(config.value("cml.operation.query")).
        map(_class_name).
        getOrElse("SearchNotices")
      val packagename = projectidentity.map(_.projection.jvmPackage()).getOrElse {
        _option(args, "package").
          orElse(config.value("cml.package")).
          orElse(config.value("project.scalaPackage")).
          orElse(config.value("project.package")).
          getOrElse("domain")
      }
      val organization = projectidentity.map(_.projection.mavenGroupId()).getOrElse {
        _option(args, "organization").
          orElse(config.value("project.organization")).
          getOrElse("com.example")
      }
      val version = _option(args, "version").
        orElse(config.value("project.component.version")).
        orElse(config.value("project.version")).
        getOrElse(_default_scaffold_version)
      val displayname = _option(args, "display-name").
        orElse(config.value("project.component.displayName")).
        orElse(config.value("project.title")).
        getOrElse(_display_name(artifact))
      val boundedcontext = _option(args, "bounded-context").
        orElse(config.value("project.boundedContext")).
        getOrElse("default")
      val domain = _option(args, "domain").
        orElse(config.value("project.domain")).
        getOrElse("default")
      val scaffold = CarScaffoldConfig(
        component,
        service,
        entity,
        commandoperation,
        queryoperation,
        packagename,
        artifact,
        organization,
        version,
        boundedcontext,
        domain,
        args.contains("--gitignore") || config.boolean("project.scaffold.gitignore").getOrElse(false),
        args.contains("--readme") || config.boolean("project.scaffold.readme").getOrElse(false),
        args.contains("--tests") || config.boolean("project.scaffold.tests").getOrElse(false),
        args.contains("--mcp-ready-service") || config.boolean("cml.service.mcpReady").getOrElse(false)
      )
      ComponentInitConfig(save, style, scaffold, displayname, projectidentity)
    }

    private def _option(args: List[String], key: String): Option[String] = {
      CozyCliArgs.parse(spec.Parameter.property(key))(args).property(key)
    }

    private def _path_option(args: List[String], key: String): Option[Path] =
      CozyCliArgs.parse(spec.Parameter.propertyFileOption(key))(args).pathProperty(key)

    private def _class_name(p: String): String =
      p.split("[^A-Za-z0-9]+").toVector.filter(_.nonEmpty).map { x =>
        x.head.toUpper + x.drop(1)
      }.mkString match {
        case "" => "Sample"
        case x => x
      }

    private def _kebab(p: String): String =
      p.replaceAll("([a-z0-9])([A-Z])", "$1-$2").toLowerCase(java.util.Locale.ROOT)

    private def _display_name(p: String): String =
      p.split("[^A-Za-z0-9]+").toVector.filter(_.nonEmpty).map { x =>
        x.head.toUpper + x.drop(1)
      }.mkString(" ") match {
        case "" => "Sample"
        case x => x
      }
  }

  private[cozy] def detectSbtVersion(): String = {
    val path = Paths.get("project/build.properties")
    if (Files.exists(path))
      Try(Files.readAllLines(path, StandardCharsets.UTF_8)).
        toOption.
        flatMap(_.toArray.collectFirst {
          case s: String if s.startsWith("sbt.version=") => s.substring("sbt.version=".length).trim
        }).
        filter(_.nonEmpty).
        getOrElse(_default_sbt_version)
    else
      _default_sbt_version
  }

  private[cozy] def appNameFromPath(path: Path): String = {
    val name = Option(path.getFileName).map(_.toString).getOrElse("sample")
    name.trim match {
      case "" => "sample"
      case x => x
    }
  }

  private[cozy] def carBuildSbt(): String =
    CozyScaffoldComponentTemplates.carBuildSbt(
      CarDependencyVersions.default,
      CarScaffoldConfig.create(Nil, Paths.get("sample"))
    )

  private[cozy] def carBuildSbt(
    versions: CarDependencyVersions,
    scaffold: CarScaffoldConfig
  ): String =
    CozyScaffoldComponentTemplates.carBuildSbt(versions, scaffold)

  private[cozy] def carProjectYamlBuildScala(): String =
    CozyScaffoldComponentTemplates.carProjectYamlBuildScala()

  private[cozy] def carProjectYaml(
    init: ComponentInitConfig,
    versions: CarDependencyVersions
  ): String =
    CozyScaffoldComponentTemplates.carProjectYaml(init, versions)

  private[cozy] def carSarBuildSbt(scaffold: CarScaffoldConfig, versions: CarDependencyVersions): String =
    CozyScaffoldComponentTemplates.carSarBuildSbt(scaffold, versions)

  private[cozy] def carSarReadme(scaffold: CarScaffoldConfig): String =
    CozyScaffoldComponentTemplates.carSarReadme(scaffold)

  private[cozy] def carSarSampleCml(scaffold: CarScaffoldConfig): String =
    CozyScaffoldComponentTemplates.carSarSampleCml(scaffold)

  private[cozy] def carSarSubsystemDescriptorYaml(scaffold: CarScaffoldConfig): String =
    CozyScaffoldComponentTemplates.carSarSubsystemDescriptorYaml(scaffold)

  private[cozy] def carSarRepositoryDReadme(appName: String): String =
    CozyScaffoldComponentTemplates.carSarRepositoryDReadme(appName)

  private[cozy] def carSarScriptsReadme(appName: String): String =
    CozyScaffoldComponentTemplates.carSarScriptsReadme(appName)

  private[cozy] def carPluginsSbt(): String =
    CozyScaffoldComponentTemplates.carPluginsSbt()

  private[cozy] def carSampleCml(scaffold: CarScaffoldConfig = CarScaffoldConfig.create(Nil, Paths.get("sample"))): String =
    CozyScaffoldComponentTemplates.carSampleCml(scaffold)

  private[cozy] def carWebDescriptorYaml(
    modelpath: Option[Path] = None,
    scaffold: CarScaffoldConfig = CarScaffoldConfig.create(Nil, Paths.get("sample"))
  ): String =
    CozyScaffoldComponentTemplates.carWebDescriptorYaml(modelpath, scaffold)

  private[cozy] def carWebAppYaml(
    scaffold: CarScaffoldConfig = CarScaffoldConfig.create(Nil, Paths.get("sample"))
  ): String =
    CozyScaffoldComponentTemplates.carWebAppYaml(scaffold)

  private[cozy] def carComponentFactorySource(
    scaffold: CarScaffoldConfig = CarScaffoldConfig.create(Nil, Paths.get("sample"))
  ): String =
    CozyScaffoldComponentTemplates.carComponentFactorySource(scaffold)

  private[cozy] def carGitignore(): String =
    CozyScaffoldComponentTemplates.carGitignore()

  private[cozy] def carReadme(scaffold: CarScaffoldConfig): String =
    CozyScaffoldComponentTemplates.carReadme(scaffold)

  private[cozy] def carComponentFactorySpecSource(scaffold: CarScaffoldConfig): String =
    CozyScaffoldComponentTemplates.carComponentFactorySpecSource(scaffold)

  private[cozy] def carCncfCommonScript(versions: CarDependencyVersions): String =
    CozyScaffoldRuntimeScriptTemplates.carCncfCommonScript(versions)

  private[cozy] def carUpdateRuntimeClasspathScript(): String =
    CozyScaffoldRuntimeScriptTemplates.carUpdateRuntimeClasspathScript()

  private[cozy] def carRunServerScript(): String =
    CozyScaffoldRuntimeScriptTemplates.carRunServerScript()

  private[cozy] def carRunServerDebugScript(): String =
    CozyScaffoldRuntimeScriptTemplates.carRunServerDebugScript()

  private[cozy] def carLauncherScript(): String =
    CozyScaffoldRuntimeScriptTemplates.carLauncherScript()
  private[cozy] val helpText: String = CozyHelpText._text

}
