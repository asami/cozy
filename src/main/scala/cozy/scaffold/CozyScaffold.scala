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
 * @version Jun.  8, 2026
 * @author  ASAMI, Tomoharu
 */
private[cozy] object CozyScaffold {
  private val _default_sbt_version = "1.9.7"
  private val _default_sbt_cozy_version = "0.1.9-SNAPSHOT"

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

    def isOption(p: String): Boolean =
      isInlineOption(p) || isFlagOption(p)

    def isInlineOption(p: String): Boolean =
      false

    def isFlagOption(p: String): Boolean =
      p == "--cncf-version" ||
      p == "--simplemodeling-model-version" ||
      p == "--cncf-collaborator-api-version"

    def create(args: List[String]): CarDependencyVersions = {
      val d = default
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
    packageName: String,
    artifactName: String,
    organization: String,
    version: String,
    boundedContext: String,
    domain: String,
    gitignore: Boolean,
    readme: Boolean,
    tests: Boolean
  ) {
    def componentClassStem: String = componentName

    def modelFileName: String =
      if (isDefault) "sample.cml" else s"${artifactName}.cml"

    def scalaPackageDir(root: String): Path =
      packageName.split("\\.").foldLeft(Paths.get(root))((z, x) => z.resolve(x))

    def serviceLoaderClassName: String =
      s"${packageName}.impl.ComponentFactory"

    def isDefault: Boolean =
      componentName == "Sample" &&
      packageName == "domain" &&
      artifactName == "sample" &&
      organization == "com.example" &&
      version == "0.0.1-SNAPSHOT" &&
      boundedContext == "default" &&
      domain == "default"
  }
  object CarScaffoldConfig {
    private val _value_options = Set(
      "component",
      "package",
      "name",
      "organization",
      "version",
      "bounded-context",
      "domain"
    )
    private val _switch_options = Set("gitignore", "readme", "tests")

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
        _option(args, "package").getOrElse("domain"),
        artifact,
        _option(args, "organization").getOrElse("com.example"),
        _option(args, "version").getOrElse("0.0.1-SNAPSHOT"),
        _option(args, "bounded-context").getOrElse("default"),
        _option(args, "domain").getOrElse("default"),
        args.contains("--gitignore"),
        args.contains("--readme"),
        args.contains("--tests")
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

    private def _kebab(p: String): String =
      p.replaceAll("([a-z0-9])([A-Z])", "$1-$2").toLowerCase(java.util.Locale.ROOT)
  }

  final case class ComponentInitConfig(
    save: Path,
    style: ProjectLayoutStyle,
    scaffold: CarScaffoldConfig,
    displayName: String
  )
  object ComponentInitConfig {
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
      val artifact = _option(args, "name").
        orElse(config.value("project.name")).
        getOrElse {
          val rawcomponent = _option(args, "component-name").
            orElse(_option(args, "component")).
            orElse(config.value("project.component.name")).
            getOrElse("Sample")
          _kebab(_class_name(rawcomponent))
        }
      val rawcomponent = _option(args, "component-name").
        orElse(_option(args, "component")).
        orElse(config.value("cml.component.name")).
        orElse(config.value("cml.component.className")).
        orElse(config.value("project.component.className")).
        orElse(config.value("project.component.name")).
        getOrElse(artifact)
      val component = _class_name(rawcomponent)
      val packagename = _option(args, "package").
        orElse(config.value("cml.package")).
        orElse(config.value("project.scalaPackage")).
        orElse(config.value("project.package")).
        getOrElse("domain")
      val organization = _option(args, "organization").
        orElse(config.value("project.organization")).
        getOrElse("com.example")
      val version = _option(args, "version").
        orElse(config.value("project.component.version")).
        orElse(config.value("project.version")).
        getOrElse("0.0.1-SNAPSHOT")
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
        packagename,
        artifact,
        organization,
        version,
        boundedcontext,
        domain,
        args.contains("--gitignore") || config.boolean("project.scaffold.gitignore").getOrElse(false),
        args.contains("--readme") || config.boolean("project.scaffold.readme").getOrElse(false),
        args.contains("--tests") || config.boolean("project.scaffold.tests").getOrElse(false)
      )
      ComponentInitConfig(save, style, scaffold, displayname)
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
      |import sbt.Keys.*
      |
      |val scala3Version = "3.3.7"
      |val cncfVersion = "${versions.cncfVersion}"
      |
      |lazy val cozyBundleFactoryClassName = settingKey[Option[String]]("Optional Component.BundleFactory implementation class for ServiceLoader discovery.")
      |
      |lazy val root = project
      |  .in(file("."))
      |  .enablePlugins(org.goldenport.cozy.CozyPlugin)
      |  .settings(
      |    organization := "${scaffold.organization}",
      |    name := "${scaffold.artifactName}",
      |    version := "${scaffold.version}",
      |
      |    scalaVersion := scala3Version,
      |    useCoursier := false,
      |
      |    resolvers += Resolver.defaultLocal,
      |    resolvers += Resolver.file("Local Ivy", file(Path.userHome.absolutePath + "/.ivy2/local"))(Resolver.ivyStylePatterns),
      |    resolvers += "Local Maven Repository" at ("file://" + Path.userHome.absolutePath + "/.m2/repository"),
      |    resolvers += "SimpleModeling.org" at "https://www.simplemodeling.org/repository/maven",
      |
      |    libraryDependencies += "org.goldenport" %% "goldenport-cncf" % cncfVersion,
      |    libraryDependencies += "org.scalatest" %% "scalatest" % "3.2.10" % Test,
      |
      |    cozyGeneratorBackend := "cozy",
      |    cozyDelegateProjectDir := None,
      |    cozyDelegateCommand := Seq("cozy"),
      |    cozyCncfVersion := cncfVersion,
      |    cozyManifestMetadata ++= Map(
      |      "component" -> "${scaffold.artifactName}",
      |      "boundedContext" -> "${scaffold.boundedContext}",
      |      "domain" -> "${scaffold.domain}"
      |    ),
      |    publish := {
      |      val _ = cozyPublishCar.value
      |      ()
      |    },
      |    publishLocal := {
      |      val _ = cozyPublishLocalCar.value
      |      ()
      |    },
      |
      |    Compile / sourceGenerators += Def.task {
      |      val out = (Compile / sourceManaged).value / "${scaffold.packageName.split("\\.").mkString("\" / \"")}" / "meta" / "BuildVersion.scala"
      |      val content =
      |        "package ${scaffold.packageName}.meta\\n\\nobject BuildVersion {\\n" +
      |          "  val name: String = \\"" + name.value + "\\"\\n" +
      |          "  val version: String = \\"" + version.value + "\\"\\n" +
      |          "  val scalaVersion: String = \\"" + scalaVersion.value + "\\"\\n" +
      |          "}\\n"
      |      IO.createDirectory(out.getParentFile)
      |      IO.write(out, content)
      |      Seq(out)
      |    }.taskValue,
      |
      |    cozyBundleFactoryClassName := {
      |      val source = baseDirectory.value / "src" / "main" / "scala" / "${scaffold.packageName.split("\\.").mkString("\" / \"")}" / "impl" / "ComponentFactory.scala"
      |      if (source.isFile) Some("${scaffold.serviceLoaderClassName}") else None
      |    },
      |
      |    Compile / resourceGenerators += Def.task {
      |      cozyBundleFactoryClassName.value.map { classname =>
      |        val out = (Compile / resourceManaged).value / "META-INF" / "services" / "org.goldenport.cncf.component.Component$$BundleFactory"
      |        IO.createDirectory(out.getParentFile)
      |        IO.write(out, classname + "\\n")
      |        out
      |      }.toSeq
      |    }.taskValue
      |  )
      |""".stripMargin

  private[cozy] def carProjectYaml(
    init: ComponentInitConfig,
    versions: CarDependencyVersions
  ): String = {
    val scaffold = init.scaffold
    s"""project:
      |  name: ${_yaml_string(scaffold.artifactName)}
      |  title: ${_yaml_string(init.displayName)}
      |  kind: car
      |  organization: ${_yaml_string(scaffold.organization)}
      |  scalaPackage: ${_yaml_string(scaffold.packageName)}
      |  component:
      |    name: ${_yaml_string(scaffold.artifactName)}
      |    className: ${_yaml_string(scaffold.componentName)}
      |    displayName: ${_yaml_string(init.displayName)}
      |    version: ${_yaml_string(scaffold.version)}
      |
      |publication:
      |  source_manifest:
      |    enabled: false
      |
      |packaging:
      |  kind: car
      |  car:
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

  private[cozy] def carSarBuildSbt(scaffold: CarScaffoldConfig, versions: CarDependencyVersions): String =
    s"""import org.goldenport.cozy.CozyPlugin.autoImport._
      |import sbt.Keys.*
      |
      |val scala3Version = "3.3.7"
      |val cncfVersion = "${versions.cncfVersion}"
      |lazy val cozyBundleFactoryClassName = settingKey[Option[String]]("Optional Component.BundleFactory implementation class for ServiceLoader discovery.")
      |
      |lazy val commonSettings = Seq(
      |  organization := "${scaffold.organization}",
      |  version := "${scaffold.version}",
      |  scalaVersion := scala3Version,
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
      |    name := "${scaffold.artifactName}",
      |    publish := {
      |      val _ = (component / cozyPublishCar).value
      |      val _ = (subsystem / cozyPublishSar).value
      |      ()
      |    },
      |    publishLocal := {
      |      val _ = (component / cozyPublishLocalCar).value
      |      val _ = (subsystem / cozyPublishLocalSar).value
      |      ()
      |    }
      |  )
      |
      |lazy val component = project
      |  .in(file("component"))
      |  .enablePlugins(org.goldenport.cozy.CozyPlugin)
      |  .settings(commonSettings)
      |  .settings(
      |    name := "${scaffold.artifactName}",
      |    cozyGeneratorBackend := "cozy",
      |    libraryDependencies ++= Seq(
      |      "org.goldenport" %% "goldenport-cncf" % cncfVersion,
      |      "org.scalatest" %% "scalatest" % "3.2.19" % Test
      |    ),
      |    cozyManifestMetadata ++= Map(
      |      "component" -> "${scaffold.artifactName}",
      |      "boundedContext" -> "${scaffold.boundedContext}",
      |      "domain" -> "${scaffold.domain}"
      |    ),
      |    publish := {
      |      val _ = cozyPublishCar.value
      |      ()
      |    },
      |    publishLocal := {
      |      val _ = cozyPublishLocalCar.value
      |      ()
      |    },
      |    cozyBundleFactoryClassName := None,
      |    Compile / resourceGenerators += Def.task {
      |      cozyBundleFactoryClassName.value.map { classname =>
      |        val out = (Compile / resourceManaged).value / "META-INF" / "services" / "org.goldenport.cncf.component.Component$$BundleFactory"
      |        IO.createDirectory(out.getParentFile)
      |        IO.write(out, classname + "\\n")
      |        out
      |      }.toSeq
      |    }.taskValue,
      |    Test / fork := false
      |  )
      |
      |lazy val subsystem = project
      |  .in(file("subsystem"))
      |  .enablePlugins(org.goldenport.cozy.CozyPlugin)
      |  .settings(commonSettings)
      |  .settings(
      |    name := "${scaffold.artifactName}-subsystem",
      |    cozyPackaging := "sar",
      |    cozySourceDir := baseDirectory.value,
      |    libraryDependencies ++= Seq(
      |      "org.goldenport" %% "goldenport-cncf" % cncfVersion,
      |      "org.scalatest" %% "scalatest" % "3.2.19" % Test
      |    ),
      |    publish := {
      |      val _ = cozyPublishSar.value
      |      ()
      |    },
      |    publishLocal := {
      |      val _ = cozyPublishLocalSar.value
      |      ()
      |    },
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

  private[cozy] def carSarSubsystemDescriptorYaml(appname: String): String =
    s"""subsystem: ${appname}
      |version: 0.0.1-SNAPSHOT
      |components:
      |  - name: ${appname}
      |    version: 0.0.1-SNAPSHOT
      |  - name: textus-user-account
      |    version: 0.1.1-SNAPSHOT
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

  private[cozy] def carSarRepositoryDReadme(appname: String): String =
    s"""# repository.d
      |
      |Development-time packaged dependencies for `${appname}` live here as searchable artifacts.
      |
      |Current expected local setup:
      |- build `textus-user-account` as a CAR
      |- place it here as a symlink
      |- keep `subsystem-descriptor.yaml` coordinates stable
      |
      |Recommended local command:
      |`ln -s /absolute/path/to/textus-user-account-0.1.1-SNAPSHOT.car repository.d/textus-user-account.car`
      |
      |Production distribution is repository-first. `repository.d` is the local development and test search staging path.
      |""".stripMargin

  private[cozy] def carSarScriptsReadme(appname: String): String =
    s"""# subsystem/scripts
      |
      |Local subsystem run helpers belong here.
      |
      |The default ${appname} scaffold expects local packaged dependencies under `repository.d` as the local search staging path.
      |For development, stage `textus-user-account` there as a symlink to the built CAR while keeping `subsystem-descriptor.yaml` on the stable repository coordinate.
      |""".stripMargin

  private[cozy] def carPluginsSbt(): String =
    s"""resolvers += Resolver.defaultLocal
       |addSbtPlugin("org.goldenport" % "sbt-cozy" % "${_default_sbt_cozy_version}")
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
      |#### public-notice
      |
      |#### notice-admin
      |
      |# COMPONENTLET
      |
      |## public-notice
      |
      |- component :: ${scaffold.componentName}
      |- kind :: participant
      |
      |### DESCRIPTION
      |
      |Public notice participant for posting and reading notices and emitting notice.posted.
      |
      |# COMPONENTLET
      |
      |## notice-admin
      |
      |- component :: ${scaffold.componentName}
      |- kind :: participant
      |
      |### DESCRIPTION
      |
      |Notice admin participant for accepting notice.posted and updating Notice state.
      |
      |# SERVICE
      |
      |## Notice
      |
      |### DESCRIPTION
      |
      |Operations for posting and reading notices without login.
      |
      |### OPERATION
      |
      |#### postNotice
      |
      |- type :: COMMAND
      |- input :: PostNotice
      |- output :: PostNoticeResult
      |
      |##### IMPLEMENTATION
      |entity-create
      |
      |##### ENTITY
      |Notice
      |
      |#### searchNotices
      |
      |- type :: QUERY
      |- input :: SearchNotices
      |- output :: SearchNoticesResult
      |
      |##### IMPLEMENTATION
      |entity-search
      |
      |##### ENTITY
      |Notice
      |
      |# ENTITY
      |
      |## Notice
      |
      |### Attribute
      |
      || name          | type     | multiplicity |
      ||---------------+----------+--------------|
      || id            | entityid | 1            |
      || senderName    | string   | 1            |
      || recipientName | string   | ?            |
      || subject       | string   | 1            |
      || body          | string   | 1            |
      |
      |# COMMAND
      |
      |## PostNotice
      |
      |### Attribute
      |
      || name          | type   | multiplicity |
      ||---------------+--------+--------------|
      || senderName    | string | 1            |
      || recipientName | string | ?            |
      || subject       | string | 1            |
      || body          | string | 1            |
      |
      |# QUERY
      |
      |## SearchNotices
      |
      |### Attribute
      |
      || name          | type   | multiplicity |
      ||---------------+--------+--------------|
      || recipientName | string | ?            |
      || text          | string | ?            |
      || offset        | int    | ?            |
      || limit         | int    | ?            |
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
      |  ${scaffold.artifactName}.notice.post-notice: protected
      |  ${scaffold.artifactName}.notice.search-notices: public
      |form:
      |  ${scaffold.artifactName}.notice.post-notice:
      |    enabled: true
      |    successRedirect: /web/$${component}/admin/entities/notice/$${result.id}
      |    stayOnError: true
      |    controls:
      |      body:
      |        type: textarea
      |        required: true
      |  ${scaffold.artifactName}.notice.search-notices:
      |    enabled: true
      |    successRedirect: /web/$${component}/admin/entities/notice
      |    stayOnError: true
      |admin:
      |  entity.notice:
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
      |      ${component}Component.NoticeService,
      |      ${component}Component.AggregateService,
      |      ${component}Component.ViewService,
      |      ${component}Component.EntityService
      |    )
      |
      |  protected final def component_core(
      |    name: String,
      |    componentid: ComponentId
      |  ): Component.Core =
      |    spec_create(name, componentid, shared_services)
      |
      |  override val Notice: ${component}Component.NoticeServiceFactory = DefaultNoticeServiceFactory()
      |  override val aggregate: ${component}Component.AggregateServiceFactory = AggregateServiceFactoryImpl()
      |  override val view: ${component}Component.ViewServiceFactory = ViewServiceFactoryImpl()
      |  override val entity: ${component}Component.EntityServiceFactory = DefaultEntityServiceFactory()
      |}
      |
      |final class ${component}PrimaryComponent extends ${component}Component
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
      |final class DefaultNoticeServiceFactory extends ${component}Component.NoticeServiceFactory {
      |  import ${component}Component.NoticeService.*
      |
      |  override def createPostNoticeActionCall(
      |    core: org.goldenport.cncf.action.ActionCall.Core,
      |    action: PostNotice
      |  ): PostNoticeActionCall =
      |    PostNoticeActionCall(core, action)
      |
      |  override def createSearchNoticesActionCall(
      |    core: org.goldenport.cncf.action.ActionCall.Core,
      |    action: SearchNotices
      |  ): SearchNoticesActionCall =
      |    SearchNoticesActionCall(core, action)
      |  }
      |
      |object DefaultNoticeServiceFactory {
      |  def apply(): DefaultNoticeServiceFactory = new DefaultNoticeServiceFactory()
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
      |Generated Scala sources are written under `target/scala-3.3.7/src_managed/main/scala`.
      |""".stripMargin

  private[cozy] def carComponentFactorySpecSource(scaffold: CarScaffoldConfig): String =
    s"""package ${scaffold.packageName}
      |
      |import org.scalatest.funsuite.AnyFunSuite
      |
      |class ComponentFactorySpec extends AnyFunSuite {
      |  test("ComponentFactory exposes a primary factory") {
      |    val factory = new impl.ComponentFactory()
      |    assert(factory.primaryFactory != null)
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
      |mkdir -p "$(dirname "$CNCF_RUNTIME_CLASSPATH_FILE")"
      |classpath="$(
      |  cd "$PROJECT_ROOT"
      |  sbt --batch 'export Runtime / fullClasspath' | awk '/^\// { print; exit }'
      |)"
      |
      |if [[ -z "$classpath" ]]; then
      |  echo "Failed to resolve Runtime / fullClasspath." >&2
      |  exit 1
      |fi
      |
      |printf '%s\n' "$classpath" > "$CNCF_RUNTIME_CLASSPATH_FILE"
      |printf 'Wrote %s\n' "$CNCF_RUNTIME_CLASSPATH_FILE"
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

  private[cozy] val helpText: String =
    """Usage:
      |  cozy [command] [options]
      |
      |Commands:
      |  help, --help, -h
      |      Show this help and exit.
      |
      |  init component --save <dir> [--config <file>] [--name <artifact>] [--component-name <name>] [--display-name <title>] [--organization <organization>] [--package <package>] [--version <version>] [--kind car|car-sar] [--bounded-context <name>] [--domain <name>] [--gitignore] [--readme] [--tests] [--no-project-files] [--overwrite-project-files]
      |    config keys: project.name, project.organization, project.component.*, project.scaffold.*, cml.package, cml.component.name
      |      Initialize a component project scaffold. Config-file values are read first; CLI options override them.
      |
      |  car-sbt-project [model-file] --save <dir> [--style car|car-sar] [--component <name>] [--package <package>] [--name <artifact>] [--organization <organization>] [--version <version>] [--bounded-context <name>] [--domain <name>] [--gitignore] [--readme] [--tests] [--no-project-files] [--overwrite-project-files]
      |      Generate an sbt project scaffold. `car` creates a single CAR component project.
      |      `car-sar` creates an application root with `component/` and `subsystem/`.
      |      When model-file is omitted, create a scaffold sample model.
      |      By default, existing differing project files are written as .bak files.
      |
      |  bok create --save <dir> [--name <name>] [--url <url>] [--language ja] [--no-project-files] [--overwrite-project-files]
      |      Create a SmartDox category-driven BoK source project scaffold without generated HTML, Arcadia assets, or site-structure.yaml.
      |
      |  bok create-category <category-name> [--project <dir>] [--title <title>] [--description <text>] [--article <slug:title:purpose>] [--term <slug:title:definition>]
      |      Add a category, category index, and optional article or term seeds to a BoK source project.
      |
      |  bok build [<project-dir>] [--strategy wip|draft|preview|production] [--docker-image <image>]
      |      Build BoK HTML under website.d using SmartDox and Antora through the configured Docker image.
      |      The default Docker image is the standard Cozy toolchain image: simplemodeling/cozy-toolchain:latest.
      |
      |  bok update [<project-dir>] [--strategy wip|draft|preview|production] [--docker-image <image>]
      |      Update the BoK output. In this version it runs the same generation flow as bok build.
      |
      |  bok preview [<project-dir>] [--port 8080]
      |      Serve website.d with python3 -m http.server for local preview.
      |
      |  bok commit [<project-dir>]
      |      Run the external command registered at bok.workflow.commit.command. Cozy does not perform built-in git operations.
      |
      |  bok upload [<project-dir>]
      |      Run the external command registered at bok.workflow.upload.command. Cozy does not interpret upload targets or credentials.
      |
      |  modeler-scala <model-file> --save <dir>
      |      Generate Scala sources from a CML/Dox model.
      |
      |  modeler-scala-value <model-file> --save <dir>
      |      Generate value/domain model Scala sources without a component.
      |
      |  package-car --save <file> --main-jar <file> --name <name> --version <version> [--component <component>] [--project-dir <dir>] [--car-dir <dir>] [--entities <spec>]
      |      Build a CAR archive. Project CAR policy comes from --project-dir/project.yaml, --project-dir/conf/cozy/config.yaml, and --project-dir/.cozy/config.yaml.
      |
      |  package-sar --save <file> --source-dir <dir> --name <name> --version <version>
      |      Build a SAR archive.
      |
      |  publish-car <project-dir> --warehouse <dir> --name <artifact> --version <version> [--car <file> | --main-jar <file>]
      |      Publish a CAR archive and CAR catalog, plus derived Maven metadata, to a warehouse.
      |      sbt-cozy cozyPublishLocalCar calls this command with ~/.cncf/local as the warehouse root.
      |
      |  publish-sar <project-dir> --warehouse <dir> --name <artifact> --version <version> [--sar <file> | --source-dir <dir>]
      |      Publish a SAR archive and SAR catalog, plus derived Maven metadata, to a warehouse.
      |      sbt-cozy cozyPublishLocalSar calls this command with ~/.cncf/local as the warehouse root.
      |
      |  publish-project <project-dir> [--save <dir>] [--kind car|sar|sample-single|sample-multi|maven-repository] [--name <slug>] [--title <title>] [--path <path>]
      |      Generate SmartDox site BoK publication registry sources from an sbt project.
      |      Writes or replaces one publication bundle under the registry.
      |
      |  publish-maven-repository <repository-dir> --save <dir> --name <slug> [--title <title>] [--path <path>] [--maven-coordinates <group:artifact,...>]
      |      Generate a SmartDox publication bundle and Maven artifact metadata from a Maven repository directory.
      |
      |  unpublish-project --save <dir> --name <slug>
      |      Remove a publication bundle from a publication registry.
      |
      |  distribute-samples <project-dir> --warehouse <dir> --name <slug> --version <version> [--samples-dir <dir>] [--dry-run]
      |      Zip the sample collection and each sample project under warehouse/repository/download/<publication.path>.
      |      With --dry-run, print planned output paths without writing archives.
      |
      |  index-warehouse <warehouse-dir> --save <dir> --name <slug> [--title <title>] [--repository-artifacts car,sar,zip] [--repository-modules <module,...>] [--download-samples <publication,...>]
      |      Generate publication registry download/repository release metadata by indexing a warehouse.
      |
      |  sbt-bridge v1 --request <file>
      |      Run the sbt-cozy bridge for generation or archive packaging.
      |
      |  web
      |      Start the Cozy web server.
      |
      |With no arguments, cozy starts the interactive REPL.
      |""".stripMargin

}
