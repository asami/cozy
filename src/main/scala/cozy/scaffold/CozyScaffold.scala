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
 * @version Aug. 25, 2026
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
    carBuildSbt(CarDependencyVersions.default, CarScaffoldConfig.create(Nil, Paths.get("sample")))

  private[cozy] def carBuildSbt(
    versions: CarDependencyVersions,
    scaffold: CarScaffoldConfig
  ): String =
    s"""import org.goldenport.cozy.CozyPlugin.autoImport._
      |import org.goldenport.cozy.CozyProjectIdentityEvidence
      |import sbt.Keys.*
      |
      |lazy val projectIdentityEvidence = settingKey[CozyProjectIdentityEvidence]("Admitted project.yaml component identity evidence")
      |
      |lazy val root = project
      |  .in(file("."))
      |  .enablePlugins(org.goldenport.cozy.CozyPlugin)
      |  .settings(
      |    projectIdentityEvidence := ProjectYamlBuild.admitted(cozyProjectMetadata.value, scalaBinaryVersion.value),
      |    organization := ProjectYamlBuild.organization(projectIdentityEvidence.value, cozyProjectMetadata.value),
      |    moduleName := ProjectYamlBuild.moduleName(projectIdentityEvidence.value, cozyProjectMetadata.value),
      |    name := moduleName.value,
      |    version := ProjectYamlBuild.version(projectIdentityEvidence.value, cozyProjectMetadata.value),
      |    scalaVersion := ProjectYamlBuild.requiredValue(cozyProjectMetadata.value, "build.scalaVersion"),
      |    useCoursier := false,
      |
      |    resolvers += Resolver.defaultLocal,
      |    resolvers += Resolver.file("Local Ivy", file(Path.userHome.absolutePath + "/.ivy2/local"))(Resolver.ivyStylePatterns),
      |    resolvers += "Local Maven Repository" at ("file://" + Path.userHome.absolutePath + "/.m2/repository"),
      |    resolvers += "SimpleModeling.org" at "https://www.simplemodeling.org/repository/maven",
      |    libraryDependencies ++= ProjectYamlBuild.dependencies(cozyProjectMetadata.value),
      |
      |    cozyGeneratorBackend := "cozy",
      |    cozyDelegateProjectDir := None,
      |    cozyCarName := ProjectYamlBuild.carBaseName(projectIdentityEvidence.value, moduleName.value, version.value),
      |    cozyManifestMetadata ++= ProjectYamlBuild.manifestMetadata(projectIdentityEvidence.value, cozyProjectMetadata.value)
      |  )
      |""".stripMargin

  private[cozy] def carProjectYamlBuildScala(): String =
    """import org.goldenport.cozy.{CozyProjectConfig, CozyProjectIdentityContract, CozyProjectIdentityEvidence}
      |import sbt._
      |
      |object ProjectYamlBuild {
      |  def load(file: File): CozyProjectConfig =
      |    CozyProjectConfig.load(file)
      |
      |  def requiredValue(config: CozyProjectConfig, path: String): String =
      |    config.value(path).getOrElse(sys.error(s"$path is required in project.yaml"))
      |
      |  def admitted(config: CozyProjectConfig, scalaBinaryVersion: String): CozyProjectIdentityEvidence =
      |    CozyProjectIdentityContract.requireAdmitted(config, scalaBinaryVersion)
      |
      |  def organization(evidence: CozyProjectIdentityEvidence, config: CozyProjectConfig): String =
      |    evidence.organization.getOrElse(requiredValue(config, "project.organization"))
      |
      |  def moduleName(evidence: CozyProjectIdentityEvidence, config: CozyProjectConfig): String =
      |    evidence.moduleName.getOrElse(requiredValue(config, "project.name"))
      |
      |  def version(evidence: CozyProjectIdentityEvidence, config: CozyProjectConfig): String =
      |    if (evidence.shape == "canonical") evidence.effectiveVersion
      |    else requiredValue(config, "project.component.version")
      |
      |  def carBaseName(evidence: CozyProjectIdentityEvidence, moduleName: String, version: String): String =
      |    evidence.carBaseName.getOrElse(s"$moduleName-$version")
      |
      |  def manifestMetadata(evidence: CozyProjectIdentityEvidence, config: CozyProjectConfig): Map[String, String] =
      |    if (evidence.shape == "canonical") evidence.manifestMetadata
      |    else config.mapUnder("packaging.car.manifest_metadata") ++
      |      Map("component" -> requiredValue(config, "project.component.name"))
      |
      |  def dependencies(config: CozyProjectConfig): Seq[ModuleID] =
      |    _dependencies(config, "compile", None) ++
      |      _dependencies(config, "test", Some(Test))
      |
      |  private def _dependencies(
      |    config: CozyProjectConfig,
      |    scope: String,
      |    configuration: Option[Configuration]
      |  ): Seq[ModuleID] =
      |    config.list(s"build.dependencies.$scope").map { coordinate =>
      |      val module = _module(coordinate)
      |      configuration.fold(module)(module % _)
      |    }
      |
      |  private def _module(coordinate: String): ModuleID =
      |    coordinate.split(":", -1).toList match {
      |      case organization :: "" :: artifact :: version :: Nil =>
      |        organization %% artifact % version
      |      case organization :: artifact :: version :: Nil =>
      |        organization % artifact % version
      |      case _ =>
      |        sys.error(
      |          s"Invalid project.yaml dependency '$coordinate'; expected organization:artifact:version or organization::artifact:version"
      |        )
      |    }
      |}
      |""".stripMargin

  private[cozy] def carProjectYaml(
    init: ComponentInitConfig,
    versions: CarDependencyVersions
  ): String = {
    val scaffold = init.scaffold
    val projectmetadata = init.projectidentity.fold {
      s"""  name: ${_yaml_string(scaffold.artifactName)}
        |  title: ${_yaml_string(init.displayName)}
        |  kind: car
        |  organization: ${_yaml_string(scaffold.organization)}
        |  scalaPackage: ${_yaml_string(scaffold.packageName)}
        |  component:
        |    name: ${_yaml_string(scaffold.artifactName)}
        |    className: ${_yaml_string(scaffold.componentName)}
        |    displayName: ${_yaml_string(init.displayName)}
        |    version: ${_yaml_string(scaffold.version)}
        |""".stripMargin
    } { identity =>
      val projection = identity.projection
      s"""  namespace: ${_yaml_string(identity.namespace.value())}
        |  id: ${_yaml_string(identity.localId.value())}
        |  kind: car
        |  component:
        |    displayName: ${_yaml_string(init.displayName)}
        |    version: ${_yaml_string(scaffold.version)}
        |  identity:
        |    qualified: ${_yaml_string(projection.qualifiedId())}
        |    organization: ${_yaml_string(projection.mavenGroupId())}
        |    artifact: ${_yaml_string(projection.mavenArtifactId())}
        |    jvmPackage: ${_yaml_string(projection.jvmPackage())}
        |    generatedClass: ${_yaml_string(projection.generatedClassName())}
        |    path: ${_yaml_string(projection.pathSegment())}
        |""".stripMargin
    }
    s"""project:
      |${projectmetadata}
      |build:
      |  scalaVersion: ${_yaml_string(_default_scala_version)}
      |  cozyVersion: ${_yaml_string(_default_cozy_version)}
      |  dependencies:
      |    compile:
      |      - ${_yaml_string(s"org.goldenport::goldenport-cncf:${versions.cncfVersion}")}
      |    test:
      |      - "org.scalatest::scalatest:3.2.19"
      |
      |publication:
      |  source_manifest:
      |    enabled: false
      |
      |packaging:
      |  kind: car
      |  car:
      |    abi:
      |      dependencies: []
      |    manifest_metadata:
      |      boundedContext: ${_yaml_string(scaffold.boundedContext)}
      |      domain: ${_yaml_string(scaffold.domain)}
      |    runtime:
      |      cncf:
      |        minimum: ${_yaml_string(versions.cncfVersion)}
      |        excluded: []
      |        tested:
      |          - ${_yaml_string(versions.cncfVersion)}
      |
      |warehouse:
      |  repository_artifacts:
      |    include:
      |      - car
      |    modules:
      |      - ${_yaml_string(scaffold.artifactName)}
      |""".stripMargin
  }

  private def _yaml_string(value: String): String =
    "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\""

  private def _json_string(value: String): String =
    "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\""

  private[cozy] def carSarBuildSbt(scaffold: CarScaffoldConfig, versions: CarDependencyVersions): String =
    s"""import org.goldenport.cozy.CozyPlugin.autoImport._
      |import org.goldenport.cozy.CozyProjectIdentityEvidence
      |import sbt.Keys.*
      |
      |lazy val componentMetadata = ProjectYamlBuild.load(file("component/project.yaml"))
      |lazy val componentIdentityEvidence = settingKey[CozyProjectIdentityEvidence]("Admitted component project.yaml identity evidence")
      |
      |lazy val commonSettings = Seq(
      |  componentIdentityEvidence := ProjectYamlBuild.admitted(componentMetadata, scalaBinaryVersion.value),
      |  organization := ProjectYamlBuild.organization(componentIdentityEvidence.value, componentMetadata),
      |  version := ProjectYamlBuild.version(componentIdentityEvidence.value, componentMetadata),
      |  scalaVersion := ProjectYamlBuild.requiredValue(componentMetadata, "build.scalaVersion"),
      |  useCoursier := false,
      |  resolvers += Resolver.defaultLocal,
      |  resolvers += Resolver.file("Local Ivy", file(Path.userHome.absolutePath + "/.ivy2/local"))(Resolver.ivyStylePatterns),
      |  resolvers += "Local Maven Repository" at ("file://" + Path.userHome.absolutePath + "/.m2/repository"),
      |  resolvers += "SimpleModeling.org" at "https://www.simplemodeling.org/repository/maven"
      |)
      |
      |lazy val root = project
      |  .in(file("."))
      |  .aggregate(component, subsystem)
      |  .settings(commonSettings)
      |  .settings(
      |    moduleName := ProjectYamlBuild.moduleName(componentIdentityEvidence.value, componentMetadata),
      |    name := moduleName.value,
      |    publish := {
      |      val componentpublication = (component / publish).value
      |      val subsystempublication = (subsystem / publish).value
      |      val _ = (componentpublication, subsystempublication)
      |      ()
      |    },
      |    publishLocal := {
      |      val componentpublication = (component / publishLocal).value
      |      val subsystempublication = (subsystem / publishLocal).value
      |      val _ = (componentpublication, subsystempublication)
      |      ()
      |    }
      |  )
      |
      |lazy val component = project
      |  .in(file("component"))
      |  .enablePlugins(org.goldenport.cozy.CozyPlugin)
      |  .settings(commonSettings)
      |  .settings(
      |    moduleName := ProjectYamlBuild.moduleName(componentIdentityEvidence.value, componentMetadata),
      |    name := moduleName.value,
      |    cozyGeneratorBackend := "cozy",
      |    libraryDependencies ++= ProjectYamlBuild.dependencies(componentMetadata),
      |    cozyCarName := ProjectYamlBuild.carBaseName(componentIdentityEvidence.value, moduleName.value, version.value),
      |    cozyManifestMetadata ++= ProjectYamlBuild.manifestMetadata(componentIdentityEvidence.value, componentMetadata),
      |    Test / fork := false
      |  )
      |
      |lazy val subsystem = project
      |  .in(file("subsystem"))
      |  .enablePlugins(org.goldenport.cozy.CozyPlugin)
      |  .settings(commonSettings)
      |  .settings(
      |    moduleName := ProjectYamlBuild.moduleName(componentIdentityEvidence.value, componentMetadata) + "-subsystem",
      |    name := moduleName.value,
      |    cozyPackaging := "sar",
      |    cozySourceDir := baseDirectory.value,
      |    libraryDependencies ++= ProjectYamlBuild.dependencies(componentMetadata),
      |    Test / fork := false
      |  )
      |
      |addCommandAlias("cozyGenerateApp", "component/cozyGenerate")
      |addCommandAlias("cozyBuildAppCAR", "component/cozyBuildCAR")
      |addCommandAlias("cozyBuildAppSAR", "subsystem/cozyBuildSAR")
      |""".stripMargin

  private[cozy] def carSarReadme(scaffold: CarScaffoldConfig): String =
    s"""# ${scaffold.artifactName}
      |
      |Generated Cozy application scaffold.
      |
      |Directories:
      |- `component/`: Cozy/CML source, generated component code, web assets, CAR packaging
      |- `subsystem/`: subsystem descriptor, external component bindings, SAR packaging
      |
      |Typical workflow:
      |- `sbt component/cozyGenerate`
      |- `sbt component/cozyBuildCAR`
      |- `sbt subsystem/cozyBuildSAR`
      |""".stripMargin

  private[cozy] def carSarSampleCml(scaffold: CarScaffoldConfig): String =
    carSampleCml(scaffold)

  private[cozy] def carSarSubsystemDescriptorYaml(scaffold: CarScaffoldConfig): String =
    s"""subsystem: ${scaffold.artifactName}
      |version: ${scaffold.version}
      |components:
      |  - name: ${scaffold.artifactName}
      |    version: ${scaffold.version}
      |  - name: textus-user-account
      |    version: ${_default_textus_user_account_version}
      |#security:
      |#  authentication:
      |#    convention: enabled
      |#    fallback_privilege: disabled
      |#    providers:
      |#      - name: user-account
      |#        component: textus-user-account
      |#        kind: human
      |#        enabled: true
      |#        priority: 100
      |#        schemes:
      |#          - bearer
      |#        default: true
      |""".stripMargin

  private[cozy] def carSarRepositoryDReadme(appName: String): String =
    s"""# repository.d
      |
      |Development-time packaged dependencies for `${appName}` live here as searchable artifacts.
      |
      |Current expected local setup:
      |- build `textus-user-account` as a CAR
      |- place it here as a symlink
      |- keep `subsystem-descriptor.yaml` coordinates stable
      |
      |Recommended local command:
      |`ln -s /absolute/path/to/textus-user-account-${_default_textus_user_account_version}.car repository.d/textus-user-account.car`
      |
      |Production distribution is repository-first. `repository.d` is the local development and test search staging path.
      |""".stripMargin

  private[cozy] def carSarScriptsReadme(appName: String): String =
    s"""# subsystem/scripts
      |
      |Local subsystem run helpers belong here.
      |
      |The default ${appName} scaffold expects local packaged dependencies under `repository.d` as the local search staging path.
      |For development, stage `textus-user-account` there as a symlink to the built CAR while keeping `subsystem-descriptor.yaml` on the stable repository coordinate.
      |""".stripMargin

  private[cozy] def carPluginsSbt(): String =
    s"""resolvers += "SimpleModeling.org" at "https://www.simplemodeling.org/repository/maven"
       |resolvers += Resolver.defaultLocal
       |
       |val sbtCozyVersion = sys.props.getOrElse("sbt.cozy.version", sys.env.getOrElse("SBT_COZY_VERSION", "${_default_sbt_cozy_version}"))
       |addSbtPlugin("org.goldenport" % "sbt-cozy" % sbtCozyVersion)
       |""".stripMargin

  private[cozy] def carSampleCml(scaffold: CarScaffoldConfig = CarScaffoldConfig.create(Nil, Paths.get("sample"))): String =
    s"""# COMPONENT
      |
      |## ${scaffold.componentName}
      |
      |### PACKAGE
      |
      |${scaffold.packageName}
      |
      |### DESCRIPTION
      |
      |${scaffold.componentName} CAR bundle root.
      |
      |### COMPONENTLET
      |
      |#### public-${scaffold.serviceSlug}
      |
      |#### ${scaffold.serviceSlug}-admin
      |
      |# COMPONENTLET
      |
      |## public-${scaffold.serviceSlug}
      |
      |- component :: ${scaffold.componentName}
      |- kind :: participant
      |
      |### DESCRIPTION
      |
      |Public ${scaffold.serviceName} participant.
      |
      |# COMPONENTLET
      |
      |## ${scaffold.serviceSlug}-admin
      |
      |- component :: ${scaffold.componentName}
      |- kind :: participant
      |
      |### DESCRIPTION
      |
      |Administrative ${scaffold.serviceName} participant.
      |
      |# SERVICE
      |
      |## ${scaffold.serviceName}
      |
      |### DESCRIPTION
      |
      |Operations for ${scaffold.serviceName}.
      |
      |### OPERATION
      |
      |#### ${scaffold.commandOperationMethodName}
      |
      |##### TYPE
      |
      |COMMAND
      |
      |##### INPUT
      |
      |###### TYPE
      |${scaffold.commandOperationClassStem}
      |
      |##### OUTPUT
      |
      |###### TYPE
      |${scaffold.commandOperationClassStem}Result
      |
      |##### IMPLEMENTATION
      |entity-create
      |
      |##### ENTITY
      |${scaffold.entityName}
      |
      |#### ${scaffold.queryOperationMethodName}
      |
      |##### TYPE
      |
      |QUERY
      |
      |##### INPUT
      |
      |###### TYPE
      |${scaffold.queryOperationClassStem}
      |
      |##### OUTPUT
      |
      |###### TYPE
      |${scaffold.queryOperationClassStem}Result
      |
      |##### IMPLEMENTATION
      |entity-search
      |
      |##### ENTITY
      |${scaffold.entityName}
      |
      |# ENTITY
      |
      |## ${scaffold.entityName}
      |
      |### Attribute
      |
      || name        | type     | multiplicity |
      ||-------------+----------+--------------|
      || id          | entityid | 1            |
      || name        | name     | 1            |
      || description | ${scaffold.entityName}Description | ?            |
      |
      |# VALUE
      |
      |## ${scaffold.commandOperationClassStem}
      |
      |- input-kind :: COMMAND
      |
      |### Attribute
      |
      || name        | type   | multiplicity |
      ||-------------+--------+--------------|
      || name        | name   | 1            |
      || description | ${scaffold.entityName}Description | ?            |
      |
      |## ${scaffold.queryOperationClassStem}
      |
      |- input-kind :: QUERY
      |
      |### Attribute
      |
      || name   | type   | multiplicity |
      ||--------+--------+--------------|
      || text   | ${scaffold.entityName}SearchText | ?            |
      || offset | int    | ?            |
      || limit  | int    | ?            |
      |
      |## ${scaffold.entityName}Description
      |
      |### Attribute
      |
      || name  | type   | multiplicity |
      ||-------+--------+--------------|
      || value | string | 1            |
      |
      |## ${scaffold.entityName}SearchText
      |
      |### Attribute
      |
      || name  | type   | multiplicity |
      ||-------+--------+--------------|
      || value | string | 1            |
      |
      |## ${scaffold.commandOperationClassStem}Result
      |
      |### Attribute
      |
      || name | type     | multiplicity |
      ||------+----------+--------------|
      || id   | entityid | 1            |
      || name | name     | 1            |
      |
      |## ${scaffold.queryOperationClassStem}Result
      |
      |### Attribute
      |
      || name  | type                   | multiplicity |
      ||-------+------------------------+--------------|
      || items | ${scaffold.entityName} | *            |
      |""".stripMargin

  private[cozy] def carWebDescriptorYaml(
    modelpath: Option[Path] = None,
    scaffold: CarScaffoldConfig = CarScaffoldConfig.create(Nil, Paths.get("sample"))
  ): String =
    modelpath.flatMap(_car_web_descriptor_yaml_from_cml).getOrElse(_default_car_web_descriptor_yaml(scaffold))

  private[cozy] def carWebAppYaml(
    scaffold: CarScaffoldConfig = CarScaffoldConfig.create(Nil, Paths.get("sample"))
  ): String =
    s"""apps:
      |  - name: ${scaffold.artifactName}
      |    path: /web/${scaffold.artifactName}
      |    kind: static-form
      |    root: /web/${scaffold.artifactName}
      |    route: /web/{component}/${scaffold.artifactName}
      |""".stripMargin

  private def _default_car_web_descriptor_yaml(scaffold: CarScaffoldConfig): String =
    s"""expose:
      |  ${scaffold.artifactName}.${scaffold.serviceSlug}.${scaffold.commandOperationSlug}: protected
      |  ${scaffold.artifactName}.${scaffold.serviceSlug}.${scaffold.queryOperationSlug}: public
      |form:
      |  ${scaffold.artifactName}.${scaffold.serviceSlug}.${scaffold.commandOperationSlug}:
      |    enabled: true
      |    successRedirect: /web/$${component}/admin/entities/${scaffold.entitySlug}/$${result.id}
      |    stayOnError: true
      |    controls:
      |      description:
      |        type: textarea
      |  ${scaffold.artifactName}.${scaffold.serviceSlug}.${scaffold.queryOperationSlug}:
      |    enabled: true
      |    successRedirect: /web/$${component}/admin/entities/${scaffold.entitySlug}
      |    stayOnError: true
      |admin:
      |  entity.${scaffold.entitySlug}:
      |    totalCount: optional
      |""".stripMargin

  private def _car_web_descriptor_yaml_from_cml(path: Path): Option[String] =
    if (!Files.exists(path))
      None
    else {
      val lines = Files.readAllLines(path, StandardCharsets.UTF_8).asScala.toVector
      val start = lines.indexWhere(line => _is_web_heading(line))
      if (start < 0)
        None
      else {
        val body = lines.drop(start + 1).takeWhile(line => !_is_top_level_heading(line))
        val text = body.dropWhile(_.trim.isEmpty).reverse.dropWhile(_.trim.isEmpty).reverse.mkString("\n")
        Option(text).filter(_.trim.nonEmpty).map(_ + "\n")
      }
    }

  private def _is_web_heading(line: String): Boolean =
    line.trim.matches("#+\\s+WEB\\s*")

  private def _is_top_level_heading(line: String): Boolean =
    line.trim.matches("#\\s+.+")

  private[cozy] def carComponentFactorySource(
    scaffold: CarScaffoldConfig = CarScaffoldConfig.create(Nil, Paths.get("sample"))
  ): String = {
    val component = scaffold.componentClassStem
    val service = scaffold.serviceClassStem
    val command = scaffold.commandOperationClassStem
    val query = scaffold.queryOperationClassStem
    val mcpreadyservices =
      if (scaffold.mcpReadyService) s"Set(${_scala_string(service)})" else "Set.empty"
    s"""package ${scaffold.packageName}.impl
      |
      |import ${scaffold.packageName}.${component}Component
      |import org.goldenport.cncf.component.{Component, ComponentCreate, ComponentId}
      |
      |final class ComponentFactory extends Component.BundleFactory {
      |  def primaryFactory: Component.PrimaryComponentFactory =
      |    ${component}PrimaryFactory
      |
      |  override def componentletFactories: Vector[Component.ComponentletFactory] =
      |    Vector.empty
      |}
      |
      |abstract class ${component}ParticipantFactoryBase extends ${component}Component.Factory {
      |  protected final val shared_services =
      |    Vector(
      |      ${component}Component.${service}Service,
      |      ${component}Component.AggregateService,
      |      ${component}Component.ViewService,
      |      ${component}Component.EntityService
      |    )
      |
      |  protected final def component_core(
      |    name: String,
      |    componentId: ComponentId
      |  ): Component.Core =
      |    spec_create(name, componentId, shared_services)
      |
      |  override val ${service}: ${component}Component.${service}ServiceFactory = Default${service}ServiceFactory()
      |  override val aggregate: ${component}Component.AggregateServiceFactory = AggregateServiceFactoryImpl()
      |  override val view: ${component}Component.ViewServiceFactory = ViewServiceFactoryImpl()
      |  override val entity: ${component}Component.EntityServiceFactory = DefaultEntityServiceFactory()
      |}
      |
      |final class ${component}PrimaryComponent extends ${component}Component {
      |  override def mcpReadyServices: Set[String] =
      |    ${mcpreadyservices}
      |}
      |
      |object ${component}PrimaryFactory extends ${component}ParticipantFactoryBase with Component.PrimaryComponentFactory {
      |  override protected def create_Component(params: ComponentCreate): Component =
      |    new ${component}PrimaryComponent()
      |
      |  override protected def create_Core(
      |    params: ComponentCreate,
      |    comp: Component
      |  ): Component.Core =
      |    component_core(${component}Component.name, ${component}Component.componentId)
      |}
      |
      |final class Default${service}ServiceFactory extends ${component}Component.${service}ServiceFactory {
      |  import ${component}Component.${service}Service.*
      |
      |  override def create${command}ActionCall(
      |    core: org.goldenport.cncf.action.ActionCall.Core,
      |    action: ${command}
      |  ): ${command}ActionCall =
      |    ${command}ActionCall(core, action)
      |
      |  override def create${query}ActionCall(
      |    core: org.goldenport.cncf.action.ActionCall.Core,
      |    action: ${query}
      |  ): ${query}ActionCall =
      |    ${query}ActionCall(core, action)
      |  }
      |
      |object Default${service}ServiceFactory {
      |  def apply(): Default${service}ServiceFactory = new Default${service}ServiceFactory()
      |  }
      |
      |final class DefaultEntityServiceFactory extends ${component}Component.EntityServiceFactory
      |
      |object DefaultEntityServiceFactory {
      |  def apply(): DefaultEntityServiceFactory = new DefaultEntityServiceFactory()
      |  }
      |
      |final class AggregateServiceFactoryImpl extends ${component}Component.AggregateServiceFactory
      |
      |object AggregateServiceFactoryImpl {
      |  def apply(): AggregateServiceFactoryImpl = new AggregateServiceFactoryImpl()
      |}
      |
      |final class ViewServiceFactoryImpl extends ${component}Component.ViewServiceFactory
      |
      |object ViewServiceFactoryImpl {
      |  def apply(): ViewServiceFactoryImpl = new ViewServiceFactoryImpl()
      |}
      |""".stripMargin
  }

  private def _scala_string(value: String): String =
    "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\""

  private[cozy] def carGitignore(): String =
    """target/
      |.bsp/
      |.metals/
      |.scala-build/
      |.idea/
      |.DS_Store
      |""".stripMargin

  private[cozy] def carReadme(scaffold: CarScaffoldConfig): String =
    s"""# ${scaffold.componentName}
      |
      |Generated Cozy CAR component project.
      |
      |Component:
      |- artifact: `${scaffold.artifactName}`
      |- package: `${scaffold.packageName}`
      |- version: `${scaffold.version}`
      |
      |Typical workflow:
      |- `sbt cozyGenerate`
      |- `sbt compile`
      |- `sbt cozyBuildCAR`
      |
      |Generated Scala sources are written under `target/scala-${_default_scala_version}/src_managed/main/scala`.
      |""".stripMargin

  private[cozy] def carComponentFactorySpecSource(scaffold: CarScaffoldConfig): String =
    s"""package ${scaffold.packageName}
      |
      |import org.scalatest.GivenWhenThen
      |import org.scalatest.matchers.should.Matchers
      |import org.scalatest.wordspec.AnyWordSpec
      |
      |final class ComponentFactorySpec extends AnyWordSpec with Matchers with GivenWhenThen {
      |  private val _e1 = afterWord(
      |    "in spec:generated-component-factory, example:E1, rule:COZY-SCAFFOLD-R1, phase:56, slice:CID-03B"
      |  )
      |
      |  "ComponentFactory" should _e1 {
      |    "expose a primary component factory" in {
      |      Given("Spec: generated-component-factory; Rules: COZY-SCAFFOLD-R1; Example: E1; a newly constructed generated component bundle factory")
      |      val factory = new impl.ComponentFactory()
      |
      |      When("the primary factory is requested")
      |      val primary = factory.primaryFactory
      |
      |      Then("the generated primary component boundary is available")
      |      primary should not be null
      |    }
      |  }
      |}
      |""".stripMargin

  private[cozy] def carCncfCommonScript(versions: CarDependencyVersions): String =
    s"""#!/usr/bin/env bash
       |set -euo pipefail
       |
       |SCRIPT_DIR="$$(cd "$$(dirname "$${BASH_SOURCE[0]}")" && pwd)"
       |PROJECT_ROOT="$$(cd "$$SCRIPT_DIR/.." && pwd)"
       |
      |CNCF_MAIN_CLASS="$${CNCF_MAIN_CLASS:-org.goldenport.cncf.CncfMain}"
       |CNCF_SAMPLES_ROOT="$${CNCF_SAMPLES_ROOT:-}"
       |if [[ -z "$$CNCF_SAMPLES_ROOT" ]]; then
       |  if [[ -n "$${CNCF_BIN:-}" ]]; then
       |    CNCF_SAMPLES_ROOT="$$(cd "$$(dirname "$$CNCF_BIN")/.." && pwd)"
       |  elif [[ -d "/Users/asami/src/dev2026/cncf-samples/versions" ]]; then
       |    CNCF_SAMPLES_ROOT="/Users/asami/src/dev2026/cncf-samples"
       |  fi
       |fi
       |CNCF_VERSION_FILE="$${CNCF_VERSION_FILE:-$${CNCF_SAMPLES_ROOT:+$$CNCF_SAMPLES_ROOT/versions/cncf-version.conf}}"
       |if [[ -z "$${CNCF_VERSION:-}" ]]; then
       |  if [[ -n "$$CNCF_VERSION_FILE" && -f "$$CNCF_VERSION_FILE" ]]; then
       |    CNCF_VERSION="$$(tr -d '[:space:]' < "$$CNCF_VERSION_FILE")"
       |  else
       |    CNCF_VERSION="${versions.cncfVersion}"
       |  fi
       |fi
       |export CNCF_SAMPLES_ROOT
       |export CNCF_VERSION
       |CNCF_SERVER_PORT="$${CNCF_SERVER_PORT:-19532}"
       |CNCF_HTTP_BASEURL="$${CNCF_HTTP_BASEURL:-http://127.0.0.1:$$CNCF_SERVER_PORT}"
       |CNCF_LAUNCHER="$${CNCF_LAUNCHER:-$$PROJECT_ROOT/bin/launcher}"
       |CNCF_LAUNCHER_CACHE="$${CNCF_LAUNCHER_CACHE:-$$PROJECT_ROOT/.cache/coursier}"
       |CNCF_RUNTIME_CLASSPATH_FILE="$${CNCF_RUNTIME_CLASSPATH_FILE:-$$PROJECT_ROOT/target/cncf.d/runtime-classpath.txt}"
       |SIMPLEMODELING_REPOSITORY="$${SIMPLEMODELING_REPOSITORY:-https://www.simplemodeling.org/repository/maven}"
       |
       |CNCF_COMMON_ARGS=(--discover=classes)
       |""".stripMargin

  private[cozy] def carUpdateRuntimeClasspathScript(): String =
    """#!/usr/bin/env bash
      |set -euo pipefail
      |
      |SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
      |# shellcheck source=cncf-common.sh
      |source "$SCRIPT_DIR/cncf-common.sh"
      |
      |cd "$PROJECT_ROOT"
      |exec sbt --batch cozyPrepareRuntime
      |""".stripMargin

  private[cozy] def carRunServerScript(): String =
    """#!/usr/bin/env bash
      |set -euo pipefail
      |
      |SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
      |# shellcheck source=cncf-common.sh
      |source "$SCRIPT_DIR/cncf-common.sh"
      |
      |if [[ ! -s "$CNCF_RUNTIME_CLASSPATH_FILE" ]]; then
      |  echo "Runtime classpath is not prepared." >&2
      |  echo "Run: $SCRIPT_DIR/update-runtime-classpath.sh" >&2
      |  exit 1
      |fi
      |
      |runtime_classpath="$(
      |  "$CNCF_LAUNCHER" \
      |    --dependency "org.goldenport:goldenport-cncf_3:$CNCF_VERSION" \
      |    --main-class "$CNCF_MAIN_CLASS" \
      |    --repository "$SIMPLEMODELING_REPOSITORY" \
      |    --cache "$CNCF_LAUNCHER_CACHE" \
      |    --resolve-only
      |)"
      |sample_classpath="$(cat "$CNCF_RUNTIME_CLASSPATH_FILE")"
      |
      |exec java \
      |  -Dcncf.server.port="$CNCF_SERVER_PORT" \
      |  -Dcncf.http.baseurl="$CNCF_HTTP_BASEURL" \
      |  -cp "$runtime_classpath:$sample_classpath" \
      |  "$CNCF_MAIN_CLASS" \
      |  "${CNCF_COMMON_ARGS[@]}" \
      |  server
      |""".stripMargin

  private[cozy] def carRunServerDebugScript(): String =
    """#!/usr/bin/env bash
      |set -euo pipefail
      |
      |SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
      |# shellcheck source=cncf-common.sh
      |source "$SCRIPT_DIR/cncf-common.sh"
      |
      |DEBUG_PORT="${DEBUG_PORT:-5005}"
      |
      |cd "$PROJECT_ROOT"
      |exec sbt \
      |  -J-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=*:"$DEBUG_PORT" \
      |  "runMain $CNCF_MAIN_CLASS ${CNCF_COMMON_ARGS[*]} server"
      |""".stripMargin

  private[cozy] def carLauncherScript(): String =
    """#!/usr/bin/env bash
      |set -eo pipefail
      |
      |SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
      |REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
      |
      |usage() {
      |  cat <<'EOF'
      |Usage:
      |  bin/launcher --dependency <org:name:version> --main-class <fqcn> [options] [-- app-args...]
      |  bin/launcher --dependency-file <path> --main-class <fqcn> [options] [-- app-args...]
      |
      |Options:
      |  --config <path>            Additional launcher config file loaded after default config files.
      |  --dependency <coord>        Root dependency coordinate. Repeatable.
      |  --dependency-file <path>    Newline-separated dependency coordinates.
      |  --main-class <fqcn>         Main class passed to java.
      |  --repository <repo>         Extra coursier repository. Repeatable.
      |  --classpath-file <path>     Use an existing classpath file instead of resolving dependencies.
      |  --cache <path>              Override coursier cache directory.
      |  --scala-version <version>   Pass through to coursier for Scala dependencies.
      |  --java <path>               Java executable. Default: java
      |  --java-opt <arg>            JVM option. Repeatable.
      |  --fetch-opt <arg>           Extra option forwarded to 'cs fetch'. Repeatable.
      |  --resolve-only              Print resolved classpath and exit.
      |  -h, --help                  Show this help.
      |
      |Defaults:
      |  - repositories: ivy2Local, central, file://$HOME/.m2/repository
      |  - cache: coursier default unless --cache is specified
      |  - config: $HOME/.cozy/launcher.yaml, then conf/cozy/launcher.yaml, then .cozy/launcher.yaml
      |EOF
      |}
      |
      |require_value() {
      |  local name="$1"
      |  local value="${2:-}"
      |  if [[ -z "$value" ]]; then
      |    echo "Missing required value: $name" >&2
      |    exit 2
      |  fi
      |}
      |
      |find_coursier() {
      |  if command -v cs >/dev/null 2>&1; then
      |    command -v cs
      |    return
      |  fi
      |  if command -v coursier >/dev/null 2>&1; then
      |    command -v coursier
      |    return
      |  fi
      |  echo "coursier command not found. Install coursier or put cs on PATH." >&2
      |  exit 3
      |}
      |
      |java_bin="${JAVA_CMD:-java}"
      |main_class=""
      |cache_dir=""
      |classpath_file=""
      |scala_version=""
      |resolve_only="0"
      |config_file="${COZY_LAUNCHER_CONFIG:-}"
      |config_file_set="0"
      |
      |declare -a dependencies=()
      |declare -a dependency_files=()
      |declare -a repositories=("ivy2Local" "central" "file://${HOME}/.m2/repository")
      |repositories_from_config="0"
      |declare -a java_opts=("-Dlogback.statusListenerClass=ch.qos.logback.core.status.NopStatusListener")
      |declare -a fetch_opts=()
      |declare -a app_args=()
      |
      |normalize_config_key() {
      |  printf '%s' "$1" | tr '[:upper:]_-' '[:lower:]..'
      |}
      |
      |trim_config_value() {
      |  local value="$1"
      |  value="${value#"${value%%[![:space:]]*}"}"
      |  value="${value%"${value##*[![:space:]]}"}"
      |  if [[ "$value" == \"*\" && "$value" == *\" ]]; then
      |    value="${value:1:${#value}-2}"
      |  elif [[ "$value" == \'*\' && "$value" == *\' ]]; then
      |    value="${value:1:${#value}-2}"
      |  fi
      |  printf '%s' "$value"
      |}
      |
      |append_config_repository() {
      |  if [[ "$repositories_from_config" == "0" ]]; then
      |    repositories=()
      |    repositories_from_config="1"
      |  fi
      |  repositories+=("$1")
      |}
      |
      |apply_launcher_config_value() {
      |  local key="$1"
      |  local value="$2"
      |  [[ -z "$value" ]] && return
      |  case "$key" in
      |    launcher.dependency|launcher.dependencies|cozy.launcher.dependency|cozy.launcher.dependencies)
      |      dependencies+=("$value")
      |      ;;
      |    launcher.dependency.file|launcher.dependency.files|cozy.launcher.dependency.file|cozy.launcher.dependency.files)
      |      dependency_files+=("$value")
      |      ;;
      |    launcher.main.class|cozy.launcher.main.class)
      |      main_class="$value"
      |      ;;
      |    launcher.classpath|launcher.classpath.file|cozy.launcher.classpath|cozy.launcher.classpath.file)
      |      classpath_file="$value"
      |      ;;
      |    launcher.repository|launcher.repositories|cozy.launcher.repository|cozy.launcher.repositories)
      |      append_config_repository "$value"
      |      ;;
      |    launcher.cache|cozy.launcher.cache)
      |      cache_dir="$value"
      |      ;;
      |    launcher.scala.version|cozy.launcher.scala.version)
      |      scala_version="$value"
      |      ;;
      |    launcher.java|cozy.launcher.java)
      |      java_bin="$value"
      |      ;;
      |    launcher.java.opt|launcher.java.opts|launcher.java.option|launcher.java.options|cozy.launcher.java.opt|cozy.launcher.java.opts|cozy.launcher.java.option|cozy.launcher.java.options)
      |      java_opts+=("$value")
      |      ;;
      |    launcher.fetch.opt|launcher.fetch.opts|launcher.fetch.option|launcher.fetch.options|cozy.launcher.fetch.opt|cozy.launcher.fetch.opts|cozy.launcher.fetch.option|cozy.launcher.fetch.options)
      |      fetch_opts+=("$value")
      |      ;;
      |  esac
      |}
      |
      |load_launcher_config() {
      |  local file="$1"
      |  [[ -f "$file" ]] || return 0
      |  local section0=""
      |  local section2=""
      |  local list_key=""
      |  local raw line indent trimmed key rest full_key value
      |  while IFS= read -r raw || [[ -n "$raw" ]]; do
      |    line="${raw%%#*}"
      |    [[ -z "${line//[[:space:]]/}" ]] && continue
      |    indent="${line%%[! ]*}"
      |    indent="${#indent}"
      |    trimmed="$(trim_config_value "$line")"
      |    if [[ "$trimmed" == "- "* ]]; then
      |      value="$(trim_config_value "${trimmed:2}")"
      |      if [[ -n "$list_key" ]]; then
      |        apply_launcher_config_value "$list_key" "$value"
      |      fi
      |      continue
      |    fi
      |    [[ "$trimmed" == *:* ]] || continue
      |    key="$(normalize_config_key "${trimmed%%:*}")"
      |    rest="$(trim_config_value "${trimmed#*:}")"
      |    case "$indent" in
      |      0)
      |        section0="$key"
      |        section2=""
      |        if [[ -n "$rest" ]]; then
      |          apply_launcher_config_value "$key" "$rest"
      |          list_key=""
      |        else
      |          list_key="$key"
      |        fi
      |        ;;
      |      2)
      |        section2="$key"
      |        full_key="$section0.$key"
      |        if [[ -n "$rest" ]]; then
      |          apply_launcher_config_value "$full_key" "$rest"
      |          list_key=""
      |        else
      |          list_key="$full_key"
      |        fi
      |        ;;
      |      *)
      |        if [[ -n "$section2" ]]; then
      |          full_key="$section0.$section2.$key"
      |        else
      |          full_key="$section0.$key"
      |        fi
      |        if [[ -n "$rest" ]]; then
      |          apply_launcher_config_value "$full_key" "$rest"
      |          list_key=""
      |        else
      |          list_key="$full_key"
      |        fi
      |        ;;
      |    esac
      |  done < "$file"
      |}
      |
      |declare -a config_scan_args=("$@")
      |config_scan_index=0
      |while [[ $config_scan_index -lt ${#config_scan_args[@]} ]]; do
      |  arg="${config_scan_args[$config_scan_index]}"
      |  case "$arg" in
      |    --config)
      |      next_index=$((config_scan_index + 1))
      |      config_file="${config_scan_args[$next_index]:-}"
      |      config_file_set="1"
      |      config_scan_index=$((config_scan_index + 2))
      |      ;;
      |    --config=*)
      |      config_file="${arg#--config=}"
      |      config_file_set="1"
      |      config_scan_index=$((config_scan_index + 1))
      |      ;;
      |    --)
      |      break
      |      ;;
      |    *)
      |      config_scan_index=$((config_scan_index + 1))
      |      ;;
      |  esac
      |done
      |
      |if [[ "$config_file_set" == "1" ]]; then
      |  require_value "--config" "$config_file"
      |fi
      |if [[ "$config_file_set" == "1" && ! -f "$config_file" ]]; then
      |  echo "Config file not found: $config_file" >&2
      |  exit 2
      |fi
      |load_launcher_config "$HOME/.cozy/launcher.yaml"
      |load_launcher_config "$PWD/conf/cozy/launcher.yaml"
      |load_launcher_config "$PWD/.cozy/launcher.yaml"
      |if [[ "$REPO_ROOT" != "$PWD" ]]; then
      |  load_launcher_config "$REPO_ROOT/conf/cozy/launcher.yaml"
      |  load_launcher_config "$REPO_ROOT/.cozy/launcher.yaml"
      |fi
      |if [[ -n "$config_file" ]]; then
      |  load_launcher_config "$config_file"
      |fi
      |
      |while [[ $# -gt 0 ]]; do
      |  case "$1" in
      |    --config)
      |      config_file="${2:-}"
      |      config_file_set="1"
      |      shift 2
      |      ;;
      |    --config=*)
      |      config_file="${1#--config=}"
      |      config_file_set="1"
      |      shift
      |      ;;
      |    --dependency)
      |      dependencies+=("${2:-}")
      |      shift 2
      |      ;;
      |    --dependency-file)
      |      dependency_files+=("${2:-}")
      |      shift 2
      |      ;;
      |    --main-class)
      |      main_class="${2:-}"
      |      shift 2
      |      ;;
      |    --repository)
      |      repositories+=("${2:-}")
      |      shift 2
      |      ;;
      |    --classpath-file)
      |      classpath_file="${2:-}"
      |      shift 2
      |      ;;
      |    --classpath-file=*)
      |      classpath_file="${1#--classpath-file=}"
      |      shift
      |      ;;
      |    --cache)
      |      cache_dir="${2:-}"
      |      shift 2
      |      ;;
      |    --scala-version)
      |      scala_version="${2:-}"
      |      shift 2
      |      ;;
      |    --java)
      |      java_bin="${2:-}"
      |      shift 2
      |      ;;
      |    --java-opt)
      |      java_opts+=("${2:-}")
      |      shift 2
      |      ;;
      |    --java-opt=*)
      |      java_opts+=("${1#--java-opt=}")
      |      shift
      |      ;;
      |    --fetch-opt)
      |      fetch_opts+=("${2:-}")
      |      shift 2
      |      ;;
      |    --fetch-opt=*)
      |      fetch_opts+=("${1#--fetch-opt=}")
      |      shift
      |      ;;
      |    --resolve-only)
      |      resolve_only="1"
      |      shift
      |      ;;
      |    -h|--help)
      |      usage
      |      exit 0
      |      ;;
      |    --)
      |      shift
      |      app_args=("$@")
      |      break
      |      ;;
      |    *)
      |      echo "Unknown argument: $1" >&2
      |      usage >&2
      |      exit 1
      |      ;;
      |  esac
      |done
      |
      |require_value "--main-class" "$main_class"
      |if [[ -n "$classpath_file" ]]; then
      |  require_value "--classpath-file" "$classpath_file"
      |  if [[ ! -f "$classpath_file" ]]; then
      |    echo "Classpath file not found: $classpath_file" >&2
      |    exit 2
      |  fi
      |fi
      |if [[ -z "$classpath_file" && ${#dependencies[@]} -eq 0 && ${#dependency_files[@]} -eq 0 ]]; then
      |  echo "Specify at least one --dependency or --dependency-file." >&2
      |  exit 2
      |fi
      |
      |for dep in "${dependencies[@]}"; do
      |  require_value "--dependency" "$dep"
      |done
      |for dep_file in "${dependency_files[@]}"; do
      |  require_value "--dependency-file" "$dep_file"
      |  if [[ ! -f "$dep_file" ]]; then
      |    echo "Dependency file not found: $dep_file" >&2
      |    exit 2
      |  fi
      |done
      |
      |if ! command -v "$java_bin" >/dev/null 2>&1; then
      |  echo "Java command not found: $java_bin" >&2
      |  exit 3
      |fi
      |
      |if [[ -n "$classpath_file" ]]; then
      |  classpath="$(tr '\n' ':' < "$classpath_file" | sed 's/:$//')"
      |else
      |  coursier_bin="$(find_coursier)"
      |
      |  declare -a fetch_cmd=("$coursier_bin" "fetch" "--classpath")
      |  for repo in "${repositories[@]}"; do
      |    fetch_cmd+=("--repository" "$repo")
      |  done
      |  if [[ -n "$cache_dir" ]]; then
      |    fetch_cmd+=("--cache" "$cache_dir")
      |  fi
      |  if [[ -n "$scala_version" ]]; then
      |    fetch_cmd+=("--scala-version" "$scala_version")
      |  fi
      |  for opt in "${fetch_opts[@]}"; do
      |    fetch_cmd+=("$opt")
      |  done
      |  for dep_file in "${dependency_files[@]}"; do
      |    fetch_cmd+=("--dependency-file" "$dep_file")
      |  done
      |  for dep in "${dependencies[@]}"; do
      |    fetch_cmd+=("$dep")
      |  done
      |
      |  classpath="$("${fetch_cmd[@]}")"
      |fi
      |
      |if [[ "$resolve_only" == "1" ]]; then
      |  printf '%s\n' "$classpath"
      |  exit 0
      |fi
      |
      |exec "$java_bin" "${java_opts[@]}" -cp "$classpath" "$main_class" "${app_args[@]}"
      |""".stripMargin

  private[cozy] val helpText: String = CozyHelpText._text

}
