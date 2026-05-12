package cozy

import org.goldenport.RAISE
import org.goldenport.i18n.I18NString
import org.goldenport.cli.{Config => CliConfig, _}
import org.goldenport.value._
import org.goldenport.parser.CommandParser
import org.goldenport.kaleidox.Kaleidox
import org.goldenport.kaleidox.http.HttpHandle
import org.smartdox.service.operations.{
  HtmlOperationClass,
  PdfOperationClass,
  SiteOperationClass
}
import play.api.libs.json._
import cozy.web.jetty.JettyServer
import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path, Paths}
import java.security.MessageDigest
import java.util.zip.{ZipEntry, ZipOutputStream}
import scala.util.Try
import scala.collection.mutable.ArrayBuffer
import scala.collection.JavaConverters._

/*
 * @since   Dec.  4, 2021
 *  version Dec. 19, 2021
 *  version Jan.  1, 2022
 *  version Feb. 28, 2022
 *  version Aug. 20, 2025
 *  version Mar. 17, 2026
 *  version Apr. 29, 2026
 * @version May. 13, 2026
 * @author  ASAMI, Tomoharu
 */
class Cozy(
  val config: Config,
  val environment: Environment,
  services: Services,
  operations: Operations
) {
  private val _engine = Engine.standard(services, operations)

  def execute(args: Array[String]) = _engine.apply(environment, args)

  def run(args: Array[String]) {
    if (_is_help_request(args))
      println(Cozy.helpText)
    else {
      val call = _operation_call(args)
      if (call.request.arguments.isEmpty || call.request.isInteractive)
        repl(call)
      else if (_is_cli_command(call))
        execute(args)
      else
        executeDirect(args)
    }
  }

  def repl(call: OperationCall) {
    val kal = interpreter
    kal.repl(call)
  }

  def interpreter = environment.appEnvironment match {
    case m: Context => m.kaleidox
    case _ => createInterpreter()
  }

  def createInterpreter(): Kaleidox = {
    val kconfig = org.goldenport.kaleidox.Config.create(environment).
      setModeler(new modeler.Modeler()).
      setPrompt("cozy> ")
    new Kaleidox(kconfig, environment)
  }

  def createHttpHandle(): HttpHandle = {
    val args = Array[String]()
    val kal = interpreter
    val call = _operation_call(args)
    kal.http(call)
  }

  def executeDirect(args: Array[String]): Unit = {
    if (!_execute_car_sbt_project(args) && !_execute_publish_project(args) && !_execute_index_warehouse(args) && !_execute_sbt_bridge(args) && !_execute_package_archive(args))
      _to_repl_commandline(args) match {
        case Some(s) =>
          val c = _operation_call(Array(s))
          interpreter.execute(c)
        case None =>
          execute(args)
      }
  }

  private def _is_cli_command(call: OperationCall): Boolean =
    call.argumentsAsString.headOption.fold(false)(_is_cli_command)

  private def _is_cli_command(name: String): Boolean =
    _engine.commandParser(name) match {
      case _: CommandParser.Found[_] => true
      case _: CommandParser.Candidates[_] => true
      case _: CommandParser.NotFound[_] => false
    }

  private def _is_help_request(args: Array[String]): Boolean =
    args.toList match {
      case "--help" :: Nil => true
      case "-h" :: Nil => true
      case "help" :: Nil => true
      case _ => false
    }

  private def _operation_call(args: Array[String]): OperationCall = {
    val req = spec.Request.empty
    val res = spec.Response()
    val op = spec.Operation("cozy", req, res)
    val request = Request.create(op, args)
    OperationCall(environment, op, request, Response())
  }

  private def _to_repl_commandline(args: Array[String]): Option[String] =
    _leading_command(args).map { case (command, rest) =>
      val normalized = _normalize_repl_args(command, rest)
      val converted = _convert_args(normalized)
      (Vector(command) ++ converted).mkString(" ")
    }

  private def _normalize_repl_args(command: String, args: List[String]): List[String] =
    command match {
      case "modeler-scala" | "modeler-scala-value" => _normalize_first_positional_path(args)
      case _ => args
    }

  private def _normalize_first_positional_path(args: List[String]): List[String] = {
    @annotation.tailrec
    def go(xs: List[String], z: Vector[String], done: Boolean): List[String] = xs match {
      case Nil => z.toList
      case x :: xx if x.startsWith("--save=") =>
        go(xx, z :+ x, done)
      case x :: y :: yy if x == "--save" =>
        go(yy, z :+ x :+ y, done)
      case x :: xx if x.startsWith("-") =>
        go(xx, z :+ x, done)
      case x :: xx if !done =>
        go(xx, z :+ Cozy._cli_path(x).toString, done = true)
      case x :: xx =>
        go(xx, z :+ x, done)
    }
    go(args, Vector.empty, done = false)
  }

  private def _execute_car_sbt_project(args: Array[String]): Boolean =
    _leading_command(args) match {
      case Some(("car-sbt-project", rest)) =>
        val save = _save_path(rest).getOrElse {
          RAISE.invalidArgumentFault("Missing --save for car-sbt-project")
        }
        val policy = Cozy.ProjectFilePolicy.create(rest)
        val versions = Cozy.CarDependencyVersions.create(rest)
        val style = Cozy.ProjectLayoutStyle.create(rest)
        val scaffold = Cozy.CarScaffoldConfig.create(rest, save, style)
        val replArgs = _without_car_scaffold_args(_without_style_args(_without_project_file_policy_args(rest)))
        val modelArgs = _without_save_args(replArgs)
        val normalizedModelArgs = _normalize_first_positional_path(modelArgs)
        val modelPath = normalizedModelArgs.find(!_.startsWith("-")).map(Paths.get(_))
        val projectsave = _project_save_path(style, save)
        if (normalizedModelArgs.exists(!_.startsWith("-"))) {
          val generatedargs = normalizedModelArgs :+ s"--save=${projectsave}"
          val repl = (Vector("modeler-scala") ++ _convert_args(generatedargs)).mkString(" ")
          val c = _operation_call(Array(repl))
          interpreter.execute(c)
          _delete_directory(projectsave.resolve("target"))
          if (!modelPath.exists(_.getFileName.toString.endsWith(".dox"))) {
            _delete_directory(projectsave.resolve("src/main/scala"))
          }
        }
        style match {
          case Cozy.ProjectLayoutStyle.CarOnly =>
            _materialize_car_sbt_project(save, policy, versions, scaffold, modelPath)
          case Cozy.ProjectLayoutStyle.CarSar =>
            _materialize_car_sar_sbt_project(save, policy, versions, scaffold, modelPath)
        }
        true
      case _ =>
        false
    }

  private def _execute_package_archive(args: Array[String]): Boolean =
    _leading_command(args) match {
      case Some(("package-car", rest)) =>
        CozyArchivePackager.buildCar(rest)
        true
      case Some(("package-sar", rest)) =>
        CozyArchivePackager.buildSar(rest)
        true
      case _ =>
        false
    }

  private def _execute_publish_project(args: Array[String]): Boolean =
    _leading_command(args) match {
      case Some(("publish-project", rest)) =>
        CozyPublicationCompiler.publish(rest)
        true
      case _ =>
        false
    }

  private def _execute_index_warehouse(args: Array[String]): Boolean =
    _leading_command(args) match {
      case Some(("index-warehouse", rest)) =>
        CozyWarehouseIndexer.index(rest)
        true
      case _ =>
        false
    }

  private def _execute_sbt_bridge(args: Array[String]): Boolean =
    _leading_command(args) match {
      case Some(("sbt-bridge", rest)) =>
        CozySbtBridge.execute(rest)
        true
      case _ =>
        false
    }

  private def _leading_command(args: Array[String]): Option[(String, List[String])] = {
    val i = args.indexWhere(x => !x.startsWith("-"))
    if (i >= 0)
      Some((args(i), args.drop(i + 1).toList))
    else
      None
  }

  private def _convert_args(args: List[String]): Vector[String] = {
    @annotation.tailrec
    def go(xs: List[String], z: Vector[String]): Vector[String] = xs match {
      case Nil => z
      case x :: xx if x.startsWith(":") =>
        go(xx, z :+ x)
      case x :: xx if x.startsWith("--") =>
        _split_option(x.drop(2)) match {
          case Some((name, value)) =>
            go(xx, z :+ s":$name" :+ _quote(value))
          case None =>
            xx match {
              case y :: yy if !y.startsWith("-") =>
                go(yy, z :+ s":${x.drop(2)}" :+ _quote(y))
              case _ =>
                go(xx, z :+ s":${x.drop(2)}")
            }
        }
      case x :: xx =>
        go(xx, z :+ _quote(x))
    }
    go(args, Vector.empty)
  }

  private def _split_option(p: String): Option[(String, String)] = {
    val i = p.indexOf('=')
    if (i >= 0)
      Some((p.substring(0, i), p.substring(i + 1)))
    else
      None
  }

  private def _quote(p: String): String = {
    val escaped = p.
      replace("\\", "\\\\").
      replace("\"", "\\\"")
    s""""$escaped""""
  }

  private def _save_path(args: List[String]): Option[Path] =
    Cozy._save_path(args)

  private def _without_save_args(args: List[String]): List[String] = args match {
    case Nil => Nil
    case x :: xs if x == "--save" => xs.drop(1)
    case x :: xs if x.startsWith("--save=") => _without_save_args(xs)
    case x :: xs => x :: _without_save_args(xs)
  }

  private def _without_project_file_policy_args(args: List[String]): List[String] =
    args match {
      case Nil => Nil
      case x :: xs if Cozy.CarDependencyVersions.isFlagOption(x) =>
        _without_project_file_policy_args(xs.drop(1))
      case x :: xs if Cozy.ProjectFilePolicy.isFlagOption(x) =>
        _without_project_file_policy_args(xs)
      case x :: xs if Cozy.ProjectLayoutStyle.isFlagOption(x) =>
        _without_project_file_policy_args(xs.drop(1))
      case x :: xs if Cozy.ProjectFilePolicy.isInlineOption(x) || Cozy.CarDependencyVersions.isInlineOption(x) || Cozy.ProjectLayoutStyle.isInlineOption(x) =>
        _without_project_file_policy_args(xs)
      case x :: xs =>
        x :: _without_project_file_policy_args(xs)
    }

  private def _without_style_args(args: List[String]): List[String] =
    args match {
      case Nil => Nil
      case x :: xs if Cozy.ProjectLayoutStyle.isFlagOption(x) =>
        _without_style_args(xs.drop(1))
      case x :: xs if Cozy.ProjectLayoutStyle.isInlineOption(x) =>
        _without_style_args(xs)
      case x :: xs =>
        x :: _without_style_args(xs)
    }

  private def _without_car_scaffold_args(args: List[String]): List[String] =
    args match {
      case Nil => Nil
      case x :: xs if Cozy.CarScaffoldConfig.isFlagOption(x) =>
        _without_car_scaffold_args(xs.drop(1))
      case x :: xs if Cozy.CarScaffoldConfig.isSwitchOption(x) =>
        _without_car_scaffold_args(xs)
      case x :: xs if Cozy.CarScaffoldConfig.isInlineOption(x) =>
        _without_car_scaffold_args(xs)
      case x :: xs =>
        x :: _without_car_scaffold_args(xs)
    }

  private def _project_save_path(style: Cozy.ProjectLayoutStyle, save: Path): Path =
    style match {
      case Cozy.ProjectLayoutStyle.CarOnly => save
      case Cozy.ProjectLayoutStyle.CarSar => save.resolve("component")
    }

  private def _materialize_car_sbt_project(
    dir: Path,
    policy: Cozy.ProjectFilePolicy,
    versions: Cozy.CarDependencyVersions,
    scaffold: Cozy.CarScaffoldConfig,
    modelPath: Option[Path] = None
  ): Unit = {
    if (policy.isSkip)
      return
    Files.createDirectories(dir)
    _write_project_file(
      dir.resolve("build.sbt"),
      Cozy.carBuildSbt(versions, scaffold),
      policy
    )
    val projectdir = dir.resolve("project")
    Files.createDirectories(projectdir)
    _write_project_file(
      projectdir.resolve("build.properties"),
      s"sbt.version=${Cozy.detectSbtVersion()}",
      policy
    )
    _write_project_file(
      projectdir.resolve("plugins.sbt"),
      Cozy.carPluginsSbt(),
      policy
    )
    val cozydir = dir.resolve("src/main/cozy")
    Files.createDirectories(cozydir)
    val sampleModel = cozydir.resolve(scaffold.modelFileName)
    val modelContent = modelPath.filter(Files.exists(_)).
      map(Files.readString(_, StandardCharsets.UTF_8)).
      getOrElse(Cozy.carSampleCml(scaffold))
    _write_project_file(
      sampleModel,
      modelContent,
      policy
    )
    val webdir = dir.resolve("src/main/car/web")
    Files.createDirectories(webdir)
    _write_project_file(
      webdir.resolve("web.yaml"),
      Cozy.carWebDescriptorYaml(modelPath, scaffold),
      policy
    )
    if (modelPath.isEmpty) {
      val impldir = dir.resolve(scaffold.scalaPackageDir("src/main/scala")).resolve("impl")
      Files.createDirectories(impldir)
      _write_project_file(
        impldir.resolve("ComponentFactory.scala"),
        Cozy.carComponentFactorySource(scaffold),
        policy
      )
    }
    if (scaffold.gitignore)
      _write_project_file(dir.resolve(".gitignore"), Cozy.carGitignore(), policy)
    if (scaffold.readme)
      _write_project_file(dir.resolve("README.md"), Cozy.carReadme(scaffold), policy)
    if (scaffold.tests) {
      val testdir = dir.resolve(scaffold.scalaPackageDir("src/test/scala"))
      Files.createDirectories(testdir)
      _write_project_file(
        testdir.resolve("ComponentFactorySpec.scala"),
        Cozy.carComponentFactorySpecSource(scaffold),
        policy
      )
    }
    val bindir = dir.resolve("bin")
    Files.createDirectories(bindir)
    _write_executable_project_file(
      bindir.resolve("launcher"),
      Cozy.carLauncherScript(),
      policy
    )
    val scriptsdir = dir.resolve("scripts")
    Files.createDirectories(scriptsdir)
    _write_project_file(
      scriptsdir.resolve("cncf-common.sh"),
      Cozy.carCncfCommonScript(versions),
      policy
    )
    _write_executable_project_file(
      scriptsdir.resolve("update-runtime-classpath.sh"),
      Cozy.carUpdateRuntimeClasspathScript(),
      policy
    )
    _write_executable_project_file(
      scriptsdir.resolve("run-server.sh"),
      Cozy.carRunServerScript(),
      policy
    )
    _write_executable_project_file(
      scriptsdir.resolve("run-server-debug.sh"),
      Cozy.carRunServerDebugScript(),
      policy
    )
  }

  private def _materialize_car_sar_sbt_project(
    dir: Path,
    policy: Cozy.ProjectFilePolicy,
    versions: Cozy.CarDependencyVersions,
    scaffold: Cozy.CarScaffoldConfig,
    modelPath: Option[Path] = None
  ): Unit = {
    if (policy.isSkip)
      return
    val appname = scaffold.artifactName
    Files.createDirectories(dir)
    _write_project_file(
      dir.resolve("README.md"),
      Cozy.carSarReadme(scaffold),
      policy
    )
    _write_project_file(
      dir.resolve("build.sbt"),
      Cozy.carSarBuildSbt(scaffold, versions),
      policy
    )
    val projectdir = dir.resolve("project")
    Files.createDirectories(projectdir)
    _write_project_file(
      projectdir.resolve("build.properties"),
      s"sbt.version=${Cozy.detectSbtVersion()}",
      policy
    )
    _write_project_file(
      projectdir.resolve("plugins.sbt"),
      Cozy.carPluginsSbt(),
      policy
    )

    val componentdir = dir.resolve("component")
    val cozydir = componentdir.resolve(s"src/main/cozy")
    Files.createDirectories(cozydir)
    val sampleModel = cozydir.resolve(scaffold.modelFileName)
    val modelContent = modelPath.filter(Files.exists(_)).
      map(Files.readString(_, StandardCharsets.UTF_8)).
      getOrElse(Cozy.carSarSampleCml(scaffold))
    _write_project_file(sampleModel, modelContent, policy)
    val webdir = componentdir.resolve("src/main/car/web")
    Files.createDirectories(webdir)
    _write_project_file(
      webdir.resolve("web.yaml"),
      Cozy.carWebDescriptorYaml(modelPath, scaffold),
      policy
    )
    _write_project_file(
      componentdir.resolve("src/main/resources/.keep"),
      "",
      policy
    )
    _write_project_file(
      componentdir.resolve("src/test/scala/.keep"),
      "",
      policy
    )

    val subsystemdir = dir.resolve("subsystem")
    Files.createDirectories(subsystemdir)
    _write_project_file(
      subsystemdir.resolve("subsystem-descriptor.yaml"),
      Cozy.carSarSubsystemDescriptorYaml(appname),
      policy
    )
    _write_project_file(
      subsystemdir.resolve("src/main/resources/.keep"),
      "",
      policy
    )
    _write_project_file(
      subsystemdir.resolve("src/test/scala/.keep"),
      "",
      policy
    )
    val repositorydir = dir.resolve("repository.d")
    Files.createDirectories(repositorydir)
    _write_project_file(
      repositorydir.resolve("README.md"),
      Cozy.carSarRepositoryDReadme(appname),
      policy
    )
    val scriptsdir = subsystemdir.resolve("scripts")
    Files.createDirectories(scriptsdir)
    _write_project_file(
      scriptsdir.resolve("README.md"),
      Cozy.carSarScriptsReadme(appname),
      policy
    )
  }

  private def _write_project_file(
    path: Path,
    content: String,
    policy: Cozy.ProjectFilePolicy
  ): Unit =
    policy match {
      case Cozy.ProjectFilePolicy.Skip =>
        Unit
      case Cozy.ProjectFilePolicy.Overwrite =>
        _write_text(path, content)
      case Cozy.ProjectFilePolicy.Default =>
        if (!Files.exists(path))
          _write_text(path, content)
        else if (Files.readString(path, StandardCharsets.UTF_8) == content)
          Unit
        else
          _write_text(Paths.get(path.toString + ".bak"), content)
    }

  private def _write_executable_project_file(
    path: Path,
    content: String,
    policy: Cozy.ProjectFilePolicy
  ): Unit = {
    _write_project_file(path, content, policy)
    if (Files.exists(path))
      path.toFile.setExecutable(true, false)
  }

  private def _delete_directory(path: Path): Unit =
    if (Files.exists(path)) {
      val stream = Files.walk(path)
      try {
        stream.iterator.asScala.toVector.reverse.foreach(Files.deleteIfExists)
      } finally {
        stream.close()
      }
    }

  private def _write_text(path: Path, content: String): Unit = {
    Option(path.getParent).foreach(Files.createDirectories(_))
    Files.writeString(path, content, StandardCharsets.UTF_8)
  }
}

object Cozy {
  private val DefaultSbtVersion = "1.9.7"
  private val DefaultSbtCozyVersion = "0.1.5-SNAPSHOT"

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
      p.startsWith("--cncf-version=") ||
      p.startsWith("--simplemodeling-model-version=") ||
      p.startsWith("--cncf-collaborator-api-version=")

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
      val prefix = s"--${key}="
      args.collectFirst {
        case s if s.startsWith(prefix) => s.substring(prefix.length)
      }.orElse {
        args.sliding(2).collectFirst {
          case List(flag, value) if flag == s"--${key}" => value
        }
      }.filter(_.nonEmpty)
    }
  }

  sealed trait ProjectLayoutStyle
  object ProjectLayoutStyle {
    case object CarOnly extends ProjectLayoutStyle
    case object CarSar extends ProjectLayoutStyle

    def isOption(p: String): Boolean =
      isInlineOption(p) || isFlagOption(p)

    def isInlineOption(p: String): Boolean =
      p.startsWith("--style=")

    def isFlagOption(p: String): Boolean =
      p == "--style"

    def create(args: List[String]): ProjectLayoutStyle =
      _option(args).map {
        case "car" => CarOnly
        case "car-sar" => CarSar
        case other => RAISE.invalidArgumentFault(s"Unsupported project style: ${other}")
      }.getOrElse(CarOnly)

    private def _option(args: List[String]): Option[String] = {
      args.collectFirst {
        case s if s.startsWith("--style=") => s.substring("--style=".length)
      }.orElse {
        args.sliding(2).collectFirst {
          case List(flag, value) if flag == "--style" => value
        }
      }.filter(_.nonEmpty)
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
    private val valueOptions = Set(
      "component",
      "package",
      "name",
      "organization",
      "version",
      "bounded-context",
      "domain"
    )
    private val switchOptions = Set("gitignore", "readme", "tests")

    def isFlagOption(p: String): Boolean =
      valueOptions.exists(x => p == s"--${x}")

    def isInlineOption(p: String): Boolean =
      valueOptions.exists(x => p.startsWith(s"--${x}="))

    def isSwitchOption(p: String): Boolean =
      switchOptions.exists(x => p == s"--${x}")

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
      val prefix = s"--${key}="
      args.collectFirst {
        case s if s.startsWith(prefix) => s.substring(prefix.length)
      }.orElse {
        args.sliding(2).collectFirst {
          case List(flag, value) if flag == s"--${key}" => value
        }
      }.map(_.trim).filter(_.nonEmpty)
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

  private[cozy] def detectSbtVersion(): String = {
    val path = Paths.get("project/build.properties")
    if (Files.exists(path))
      Try(Files.readAllLines(path, StandardCharsets.UTF_8)).
        toOption.
        flatMap(_.toArray.collectFirst {
          case s: String if s.startsWith("sbt.version=") => s.substring("sbt.version=".length).trim
        }).
        filter(_.nonEmpty).
        getOrElse(DefaultSbtVersion)
    else
      DefaultSbtVersion
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
      |def sampleVersion(envName: String, fileName: String, fallback: String): String =
      |  sys.env.get(envName)
      |    .orElse {
      |      sys.env.get("CNCF_SAMPLES_ROOT").flatMap { root =>
      |        val versionFile = file(root) / "versions" / fileName
      |        if (versionFile.isFile)
      |          Some(IO.read(versionFile).trim).filter(_.nonEmpty)
      |        else
      |          None
      |      }
      |    }
      |    .getOrElse(fallback)
      |
      |val cncfVersion = sampleVersion("CNCF_VERSION", "cncf-version.conf", "${versions.cncfVersion}")
      |val simpleModelingModelVersion = sampleVersion("SIMPLEMODELING_MODEL_VERSION", "simplemodeling-model-version.conf", "${versions.simpleModelingModelVersion}")
      |val cncfCollaboratorApiVersion = "${versions.cncfCollaboratorApiVersion}"
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
      |
      |    resolvers += Resolver.defaultLocal,
      |    resolvers += Resolver.file("Local Ivy", file(Path.userHome.absolutePath + "/.ivy2/local"))(Resolver.ivyStylePatterns),
      |    resolvers += "Local Maven Repository" at ("file://" + Path.userHome.absolutePath + "/.m2/repository"),
      |    resolvers += "SimpleModeling.org" at "https://www.simplemodeling.org/maven",
      |
      |    libraryDependencies += "com.novocode" % "junit-interface" % "0.11" % "test",
      |    libraryDependencies += "org.typelevel" %% "cats-core" % "2.7.0",
      |    libraryDependencies += "org.typelevel" %% "cats-kernel-laws" % "2.7.0",
      |    libraryDependencies += "org.typelevel" %% "cats-free" % "2.7.0",
      |    libraryDependencies += "org.typelevel" %% "cats-effect" % "3.3.0",
      |    libraryDependencies += "org.typelevel" %% "kittens" % "3.5.0",
      |    libraryDependencies += "org.scalatest" %% "scalatest" % "3.2.10" % "test",
      |    libraryDependencies += "org.typelevel" %% "cats-testkit" % "2.7.0" % "test",
      |    libraryDependencies += "org.typelevel" %% "discipline-core" % "1.3.0" % "test",
      |    libraryDependencies += "org.typelevel" %% "discipline-scalatest" % "2.1.5" % "test",
      |    libraryDependencies += "org.typelevel" %% "spire" % "0.18.0",
      |    libraryDependencies += "io.circe" %% "circe-core" % "0.14.3",
      |    libraryDependencies += "io.circe" %% "circe-generic" % "0.14.3",
      |    libraryDependencies += "io.circe" %% "circe-parser" % "0.14.3",
      |    libraryDependencies += "org.goldenport" %% "goldenport-cncf" % cncfVersion,
      |    libraryDependencies += "org.simplemodeling" %% "simplemodeling-model" % simpleModelingModelVersion,
      |    libraryDependencies += "org.goldenport" % "cncf-collaborator-api" % cncfCollaboratorApiVersion,
      |
      |    dependencyOverrides ++= Seq(
      |      "org.goldenport" % "cncf-collaborator-api" % cncfCollaboratorApiVersion,
      |      "org.scala-lang.modules" %% "scala-xml" % "2.1.0",
      |      "org.scala-lang.modules" %% "scala-parser-combinators" % "2.3.0"
      |    ),
      |
      |    cozyGeneratorBackend := "cozy",
      |    cozyDelegateProjectDir := None,
      |    cozyDelegateCommand := Seq("cozy"),
      |    cozyCncfVersion := cncfVersion,
      |    cozySimpleModelingModelVersion := simpleModelingModelVersion,
      |    cozyCncfCollaboratorApiVersion := cncfCollaboratorApiVersion,
      |    cozyManifestMetadata ++= Map(
      |      "component" -> "${scaffold.artifactName}",
      |      "boundedContext" -> "${scaffold.boundedContext}",
      |      "domain" -> "${scaffold.domain}"
      |    ),
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

  private[cozy] def carSarBuildSbt(scaffold: CarScaffoldConfig, versions: CarDependencyVersions): String =
    s"""import org.goldenport.cozy.CozyPlugin.autoImport._
      |import sbt.Keys.*
      |
      |val scala3Version = "3.3.7"
      |def sampleVersion(envName: String, fileName: String, fallback: String): String =
      |  sys.env.get(envName)
      |    .orElse {
      |      sys.env.get("TEXTUS_SAMPLES_ROOT")
      |        .orElse(sys.env.get("CNCF_SAMPLES_ROOT"))
      |        .flatMap { root =>
      |          val versionFile = file(root) / "versions" / fileName
      |          if (versionFile.isFile)
      |            Some(IO.read(versionFile).trim).filter(_.nonEmpty)
      |          else
      |            None
      |        }
      |    }
      |    .getOrElse(fallback)
      |
      |val cncfVersion = sampleVersion("CNCF_VERSION", "cncf-version.conf", "${versions.cncfVersion}")
      |val simpleModelingModelVersion = sampleVersion("SIMPLEMODELING_MODEL_VERSION", "simplemodeling-model-version.conf", "${versions.simpleModelingModelVersion}")
      |val cncfCollaboratorApiVersion = sampleVersion("CNCF_COLLABORATOR_API_VERSION", "cncf-collaborator-api-version.conf", "${versions.cncfCollaboratorApiVersion}")
      |lazy val cozyBundleFactoryClassName = settingKey[Option[String]]("Optional Component.BundleFactory implementation class for ServiceLoader discovery.")
      |
      |lazy val commonSettings = Seq(
      |  organization := "${scaffold.organization}",
      |  version := "${scaffold.version}",
      |  scalaVersion := scala3Version,
      |  resolvers += Resolver.defaultLocal,
      |  resolvers += Resolver.file("Local Ivy", file(Path.userHome.absolutePath + "/.ivy2/local"))(Resolver.ivyStylePatterns),
      |  resolvers += "Local Maven Repository" at ("file://" + Path.userHome.absolutePath + "/.m2/repository"),
      |  resolvers += "SimpleModeling.org" at "https://www.simplemodeling.org/maven"
      |)
      |
      |lazy val root = project
      |  .in(file("."))
      |  .aggregate(component, subsystem)
      |  .settings(commonSettings)
      |  .settings(
      |    name := "${scaffold.artifactName}",
      |    publish / skip := true
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
      |      "org.simplemodeling" %% "simplemodeling-model" % simpleModelingModelVersion,
      |      "org.goldenport" % "cncf-collaborator-api" % cncfCollaboratorApiVersion,
      |      "org.scalatest" %% "scalatest" % "3.2.19" % Test
      |    ),
      |    cozyManifestMetadata ++= Map(
      |      "component" -> "${scaffold.artifactName}",
      |      "boundedContext" -> "${scaffold.boundedContext}",
      |      "domain" -> "${scaffold.domain}"
      |    ),
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
       |addSbtPlugin("org.goldenport" % "sbt-cozy" % "${DefaultSbtCozyVersion}")
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
      |##### TYPE
      |COMMAND
      |
      |##### IMPLEMENTATION
      |entity-create
      |
      |##### ENTITY
      |Notice
      |
      |##### INPUT
      |
      |###### TYPE
      |PostNotice
      |
      |##### OUTPUT
      |
      |###### TYPE
      |PostNoticeResult
      |
      |#### searchNotices
      |
      |##### TYPE
      |QUERY
      |
      |##### IMPLEMENTATION
      |entity-search
      |
      |##### ENTITY
      |Notice
      |
      |##### INPUT
      |
      |###### TYPE
      |SearchNotices
      |
      |##### OUTPUT
      |
      |###### TYPE
      |SearchNoticesResult
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
    modelPath: Option[Path] = None,
    scaffold: CarScaffoldConfig = CarScaffoldConfig.create(Nil, Paths.get("sample"))
  ): String =
    modelPath.flatMap(_car_web_descriptor_yaml_from_cml).getOrElse(_default_car_web_descriptor_yaml(scaffold))

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
      |  protected final val sharedServices =
      |    Vector(
      |      ${component}Component.NoticeService,
      |      ${component}Component.AggregateService,
      |      ${component}Component.ViewService,
      |      ${component}Component.EntityService
      |    )
      |
      |  protected final def componentCore(
      |    name: String,
      |    componentId: ComponentId
      |  ): Component.Core =
      |    spec_create(name, componentId, sharedServices)
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
      |    componentCore(${component}Component.name, ${component}Component.componentId)
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
       |SIMPLEMODELING_REPOSITORY="$${SIMPLEMODELING_REPOSITORY:-https://www.simplemodeling.org/maven}"
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
      |  --dependency <coord>        Root dependency coordinate. Repeatable.
      |  --dependency-file <path>    Newline-separated dependency coordinates.
      |  --main-class <fqcn>         Main class passed to java.
      |  --repository <repo>         Extra coursier repository. Repeatable.
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
      |scala_version=""
      |resolve_only="0"
      |
      |declare -a dependencies=()
      |declare -a dependency_files=()
      |declare -a repositories=("ivy2Local" "central" "file://${HOME}/.m2/repository")
      |declare -a java_opts=()
      |declare -a fetch_opts=()
      |declare -a app_args=()
      |
      |while [[ $# -gt 0 ]]; do
      |  case "$1" in
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
      |if [[ ${#dependencies[@]} -eq 0 && ${#dependency_files[@]} -eq 0 ]]; then
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
      |coursier_bin="$(find_coursier)"
      |
      |declare -a fetch_cmd=("$coursier_bin" "fetch" "--classpath")
      |for repo in "${repositories[@]}"; do
      |  fetch_cmd+=("--repository" "$repo")
      |done
      |if [[ -n "$cache_dir" ]]; then
      |  fetch_cmd+=("--cache" "$cache_dir")
      |fi
      |if [[ -n "$scala_version" ]]; then
      |  fetch_cmd+=("--scala-version" "$scala_version")
      |fi
      |for opt in "${fetch_opts[@]}"; do
      |  fetch_cmd+=("$opt")
      |done
      |for dep_file in "${dependency_files[@]}"; do
      |  fetch_cmd+=("--dependency-file" "$dep_file")
      |done
      |for dep in "${dependencies[@]}"; do
      |  fetch_cmd+=("$dep")
      |done
      |
      |classpath="$("${fetch_cmd[@]}")"
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
      |  car-sbt-project [model-file] --save=<dir> [--style=car|car-sar] [--component=<name>] [--package=<package>] [--name=<artifact>] [--organization=<organization>] [--version=<version>] [--bounded-context=<name>] [--domain=<name>] [--gitignore] [--readme] [--tests] [--no-project-files] [--overwrite-project-files]
      |      Generate an sbt project scaffold. `car` creates a single CAR component project.
      |      `car-sar` creates an application root with `component/` and `subsystem/`.
      |      When model-file is omitted, create a scaffold sample model.
      |      By default, existing differing project files are written as .bak files.
      |
      |  modeler-scala <model-file> --save=<dir>
      |      Generate Scala sources from a CML/Dox model.
      |
      |  modeler-scala-value <model-file> --save=<dir>
      |      Generate value/domain model Scala sources without a component.
      |
      |  package-car --save=<file> --main-jar=<file> --name=<name> --version=<version> --component=<component> [--car-dir=<dir>] [--entities=<spec>]
      |      Build a CAR archive. Additional CAR-root files come from --car-dir, typically src/main/car.
      |
      |  package-sar --save=<file> --source-dir=<dir> --name=<name> --version=<version>
      |      Build a SAR archive.
      |
      |  publish-project <project-dir> [--save=<dir>] [--kind=car|sar|sample-single|sample-multi] [--name=<slug>] [--title=<title>] [--path=<path>]
      |      Generate SmartDox site BoK publication sources from an sbt project.
      |      Writes deterministic YAML/JSON metadata and a source manifest under publish.d.
      |
      |  index-warehouse <warehouse-dir> --save=<dir> --name=<slug> [--title=<title>] [--maven-coordinates=<group:artifact,...>] [--repository-artifacts=car,sar,zip] [--repository-modules=<module,...>]
      |      Generate publish.d artifact and release metadata by indexing a warehouse.
      |
      |  sbt-bridge v1 --request=<file>
      |      Run the sbt-cozy bridge for generation or archive packaging.
      |
      |  web
      |      Start the Cozy web server.
      |
      |With no arguments, cozy starts the interactive REPL.
      |""".stripMargin

  case object CozyServiceClass extends ServiceClass {
    val name = "cozy"
    val defaultOperation = Some(CozyOperationClass)
    val operations = Operations(
      CozyOperationClass,
      WebOperationClass,
      HtmlOperationClass,
      PdfOperationClass,
      SiteOperationClass
    )
  }

  case object CozyOperationClass extends OperationClassWithOperation {
    val request = spec.Request.empty
    val response = spec.Response.empty
    val specification = spec.Operation("cozy", request, response)
    
    def apply(env: Environment, req: Request): Response = {
      val ctx = env.toAppEnvironment[Context]
      val kaleidox = ctx.kaleidox
      val args = Array[String]() // TODO
      val req = spec.Request.empty
      val res = spec.Response()
      val op = spec.Operation("cozy", req, res)
      val call = OperationCall.create(op, args)
      kaleidox.repl(call)
      VoidResponse
    }
  }

  case object WebOperationClass extends OperationClassWithOperation {
    val request = spec.Request.empty
    val response = spec.Response.empty
    val specification = spec.Operation("web", request, response)
    
    def apply(env: Environment, req: Request): Response = {
      val ctx = env.toAppEnvironment[Context]
      JettyServer.run(ctx)
      VoidResponse
    }
  }

  def build(args: Array[String]): Cozy = {
    val env0 = Environment.create("cozy", args)
    val env1 = _apply_save_output_directory(env0, args)
    val config = Config.create(env1)
    val kaleidox = _create(env1)
    val context = new Context(env1, config, kaleidox)
    val env = env1.withAppEnvironment(context)
    val services = Services(
      CozyServiceClass,
      modeler.ModelerServiceClass
    )
    new Cozy(config, env, services, CozyServiceClass.operations)
  }

  private def _create(environment: Environment) = {
    val kconfig = org.goldenport.kaleidox.Config.create(environment).
      setModeler(new modeler.Modeler()).
      setPrompt("cozy> ")
    new Kaleidox(kconfig, environment)
  }

  def main(args: Array[String]) {
    val cozy = build(args)
    cozy.run(args)
  }

  private def _apply_save_output_directory(env: Environment, args: Array[String]): Environment =
    _save_path(args.toList).
      map(path => env.copy(config = env.config.copy(projectDirectory = Some(path.toFile)))).
      getOrElse(env)

  private def _save_path(args: List[String]): Option[Path] = {
    @annotation.tailrec
    def go(xs: List[String]): Option[Path] = xs match {
      case Nil => None
      case x :: xx if x.startsWith("--save=") =>
        Some(_cli_path(x.drop("--save=".length)))
      case "--save" :: value :: _ =>
        Some(_cli_path(value))
      case _ :: xx =>
        go(xx)
    }
    go(args)
  }

  private def _cli_path(value: String): Path = {
    val path = Paths.get(value)
    if (path.isAbsolute)
      path.normalize()
    else
      _cli_base_directory.resolve(path).normalize()
  }

  private def _cli_base_directory: Path =
    sys.env.get("COZY_INVOCATION_DIR").
      filter(_.nonEmpty).
      map(Paths.get(_)).
      getOrElse(Paths.get(sys.props("user.dir"))).
      toAbsolutePath.
      normalize()
}

private object CozyArchivePackager {
  def buildCar(args: List[String]): Unit = {
    val save = _required_path(args, "save")
    val mainJar = _required_path(args, "main-jar")
    val libJars = _paths(args, "lib-jars")
    val spiJars = _paths(args, "spi-jars")
    val carDir = _path(args, "car-dir")
    val defaultConf = _path(args, "default-conf")
    val webDir = _path(args, "web-dir")
    val assemblyDescriptor = _path(args, "assembly-descriptor")
    val name = _required_value(args, "name")
    val version = _required_value(args, "version")
    val component = _required_value(args, "component")
    val extensionMap = _string_map(args, "extensions")
    val configMap = _string_map(args, "config")
    val entities = _entity_descriptors(args)
    _write_archive(
      save,
      Vector(
        mainJar -> "component/main.jar"
      ) ++
        _car_entries(carDir) ++
        libJars.map(p => p -> s"lib/${p.getFileName}") ++
        spiJars.map(p => p -> s"spi/${p.getFileName}") ++
        defaultConf.toVector.map(_ -> "config/default.conf") ++
        assemblyDescriptor.toVector.map(_ -> "assembly-descriptor.yaml") ++
        _web_entries(webDir) ++
        Vector(_write_temp("component-descriptor", _component_descriptor_json(name, version, component, extensionMap, configMap, entities)) -> "component-descriptor.json"),
      Vector("component", "lib", "spi", "config", "web")
    )
  }

  def buildSar(args: List[String]): Unit = {
    val save = _required_path(args, "save")
    val sourceDir = _required_path(args, "source-dir")
    val sourceFiles = _values(args, "source-files")
    val extensionJars = _paths(args, "extension-jars")
    val applicationConf = _path(args, "application-conf")
    val subsystemSources = _archive_sources(sourceDir, sourceFiles)
    _write_archive(
      save,
      subsystemSources ++
        extensionJars.map(p => p -> s"extension/${p.getFileName}") ++
        applicationConf.toVector.map(_ -> "config/application.conf"),
      Vector("extension", "config")
    )
  }

  private def _archive_sources(sourceDir: Path, includes: Vector[String] = Vector.empty): Vector[(Path, String)] = {
    if (!Files.exists(sourceDir))
      Vector.empty
    else {
      val includeSet = includes.map(_.replace('\\', '/')).toSet
      val stream = Files.walk(sourceDir)
      try {
        stream.iterator().asScala.toVector.collect {
          case p if Files.isRegularFile(p) =>
            p -> sourceDir.relativize(p).toString.replace('\\', '/')
        }.filter { case (_, rel) =>
          includeSet.isEmpty || includeSet.contains(rel)
        }.sortBy(_._2)
      } finally {
        stream.close()
      }
    }
  }

  private def _car_entries(carDir: Option[Path]): Vector[(Path, String)] =
    carDir.toVector.flatMap(_archive_sources(_))

  private def _web_entries(webDir: Option[Path]): Vector[(Path, String)] =
    webDir.toVector.flatMap { dir =>
      _archive_sources(dir).filterNot { case (_, rel) =>
        rel == "web.yaml" || rel == "web-descriptor.yaml"
      }.map { case (p, rel) => p -> s"web/${rel}" }
    }

  private def _write_archive(
    archive: Path,
    entries: Vector[(Path, String)],
    placeholderDirs: Vector[String]
  ): Unit = {
    Files.createDirectories(archive.getParent)
    Files.deleteIfExists(archive)
    val tempDir = Files.createTempDirectory("cozy-package-")
    try {
      entries.foreach { case (source, relative) =>
        val dest = tempDir.resolve(relative)
        Option(dest.getParent).foreach(Files.createDirectories(_))
        Files.copy(source, dest, java.nio.file.StandardCopyOption.REPLACE_EXISTING)
      }
      placeholderDirs.foreach { dir =>
        val target = tempDir.resolve(dir)
        if (!Files.exists(target) || _is_empty_dir(target))
          _write_text(target.resolve(".keep"), "")
      }
      _zip_dir(tempDir, archive)
    } finally {
      _delete_tree(tempDir)
    }
  }

  private def _zip_dir(sourceDir: Path, archive: Path): Unit = {
    val stream = Files.walk(sourceDir)
    try {
      val files = stream.iterator().asScala.toVector.collect {
        case p if Files.isRegularFile(p) =>
          p -> sourceDir.relativize(p).toString.replace('\\', '/')
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
      val effectiveExtensions = extensions - "componentDescriptorJson"
      s"""{
         |  "name": ${_json_string(name)},
         |  "version": ${_json_string(version)},
         |  "component": ${_json_string(component)},
         |  "entities": ${_json_entities(entities)},
         |  "extensions": ${_json_map(effectiveExtensions)},
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

private object CozyProjectYamlConfig {
  final case class Config(values: Map[String, String], lists: Map[String, Vector[String]]) {
    def value(path: String): Option[String] = values.get(path).map(_.trim).filter(_.nonEmpty)
    def list(path: String): Vector[String] = lists.getOrElse(path, Vector.empty).map(_.trim).filter(_.nonEmpty)
  }
  object Config {
    val empty: Config = Config(Map.empty, Map.empty)
  }

  def load(path: Path): Config =
    if (Files.isRegularFile(path))
      parse(Files.readAllLines(path, StandardCharsets.UTF_8).asScala.toVector)
    else
      Config.empty

  def parse(lines: Vector[String]): Config = {
    var stack = Vector.empty[(Int, String)]
    var values = Map.empty[String, String]
    var lists = Map.empty[String, Vector[String]]

    def currentPath: String = stack.map(_._2).mkString(".")
    def appendList(value: String): Unit = {
      val key = currentPath
      if (key.nonEmpty)
        lists = lists.updated(key, lists.getOrElse(key, Vector.empty) :+ _unquote(value))
    }

    lines.foreach { raw =>
      val withoutComment = _strip_comment(raw)
      if (withoutComment.trim.nonEmpty) {
        val indent = withoutComment.takeWhile(_ == ' ').length
        val trimmed = withoutComment.trim
        if (trimmed.startsWith("- ")) {
          stack = stack.dropRight(stack.reverse.takeWhile(_._1 >= indent).length)
          appendList(trimmed.substring(2).trim)
        } else {
          val n = trimmed.indexOf(':')
          if (n >= 0) {
            val key = trimmed.substring(0, n).trim
            val rest = trimmed.substring(n + 1).trim
            stack = stack.dropRight(stack.reverse.takeWhile(_._1 >= indent).length)
            if (rest.isEmpty) {
              stack = stack :+ (indent -> key)
            } else {
              val path = (stack.map(_._2) :+ key).mkString(".")
              values = values.updated(path, _unquote(rest))
            }
          }
        }
      }
    }
    Config(values, lists)
  }

  private def _strip_comment(s: String): String = {
    val trimmed = s.trim
    if (trimmed.startsWith("#"))
      ""
    else
      s
  }

  private def _unquote(s: String): String = {
    val t = s.trim
    if (t.length >= 2 && ((t.head == '"' && t.last == '"') || (t.head == '\'' && t.last == '\'')))
      t.substring(1, t.length - 1)
    else
      t
  }
}

private object CozyPublicationCompiler {
  private val Schema = "cozy.publish-project.v1"
  private val ValidKinds = Set("car", "sar", "sample-single", "sample-multi")
  private val DefaultExcludedSegments = Set("target", ".git", ".bsp", ".bloop", ".metals", ".idea", ".cache", ".vscode", "repository.d")
  private val SlugPattern = "^[a-z0-9][a-z0-9-]*$".r

  final case class ProjectMetadata(
    name: String,
    title: String,
    kind: String,
    publicationPath: Option[String],
    organization: String,
    version: String,
    scalaVersion: String,
    sbtVersion: String
  )
  final case class SourceFile(path: String, size: Long, sha256: String)
  final case class Publication(project: ProjectMetadata, sourceFiles: Vector[SourceFile])

  def publish(args: List[String]): Unit = {
    val projectDir = _project_dir(args)
    if (!Files.isDirectory(projectDir))
      RAISE.invalidArgumentFault(s"Project directory does not exist: ${projectDir}")
    if (!Files.isRegularFile(projectDir.resolve("build.sbt")))
      RAISE.invalidArgumentFault(s"Not an sbt project directory: ${projectDir}")

    val config = CozyProjectYamlConfig.load(projectDir.resolve(".cozy/config.yaml"))
    val saveDir = _publication_output(projectDir, args, config)
    val publication = _compile(projectDir, saveDir, args, config)
    _write(publication, saveDir)
  }

  private def _compile(projectDir: Path, saveDir: Path, args: List[String], config: CozyProjectYamlConfig.Config): Publication = {
    val buildSbt = Files.readString(projectDir.resolve("build.sbt"), StandardCharsets.UTF_8)
    val rawName = _value(args, "name").orElse(config.value("publication.name")).orElse(_sbt_setting(buildSbt, "name"))
    val name = rawName match {
      case Some(x) if _explicit_name(args, config) => _validate_name(x, "publication name")
      case Some(x) => _slugify(x)
      case None => _slugify(projectDir.getFileName.toString)
    }
    if (name.isEmpty)
      RAISE.invalidArgumentFault("Publication name is empty after slug normalization")
    val title = _value(args, "title").orElse(config.value("publication.title")).orElse(_sbt_setting(buildSbt, "name")).getOrElse(name)
    val publicationPath = _value(args, "path").orElse(config.value("publication.path")).map(_validate_publication_path)
    val organization = _value(args, "organization").orElse(_sbt_setting(buildSbt, "organization")).getOrElse("")
    val version = _value(args, "version").orElse(_sbt_setting(buildSbt, "version")).getOrElse("")
    val scalaVersion = _value(args, "scala-version").orElse(_sbt_setting(buildSbt, "scalaVersion")).getOrElse("")
    val sbtVersion = _value(args, "sbt-version").orElse(_sbt_version(projectDir)).getOrElse("")
    val samplesDir = _config_path(projectDir, config.value("publication.samples_dir")).getOrElse(projectDir.resolve("samples"))
    val kind = _value(args, "kind").orElse(config.value("publication.kind")).map(_.trim).filter(_.nonEmpty).getOrElse(_detect_kind(projectDir, buildSbt, samplesDir))
    if (!ValidKinds.contains(kind))
      RAISE.invalidArgumentFault(s"Invalid --kind: ${kind}. Expected one of: ${ValidKinds.toVector.sorted.mkString(", ")}")
    val excludes = DefaultExcludedSegments ++ config.list("publication.source_manifest.excludes")

    Publication(
      ProjectMetadata(
        name = name,
        title = title,
        kind = kind,
        publicationPath = publicationPath,
        organization = organization,
        version = version,
        scalaVersion = scalaVersion,
        sbtVersion = sbtVersion
      ),
      _source_manifest(projectDir, saveDir, excludes)
    )
  }

  private def _write(publication: Publication, saveDir: Path): Unit = {
    val name = publication.project.name
    _write_pair(saveDir.resolve(s"catalog/projects/${name}"), _catalog_project_yaml(publication), _catalog_project_json(publication))
    _write_pair(saveDir.resolve(s"catalog/samples/${name}"), _catalog_sample_yaml(publication), _catalog_sample_json(publication))
    _write_pair(saveDir.resolve(s"samples/${name}/metadata"), _sample_metadata_yaml(publication), _sample_metadata_json(publication))
    _write_pair(saveDir.resolve(s"repository/artifacts/${name}"), _artifact_yaml(publication, "repository"), _artifact_json(publication, "repository"))
    _write_pair(saveDir.resolve(s"maven/artifacts/${name}"), _artifact_yaml(publication, "maven"), _artifact_json(publication, "maven"))
    _write_pair(saveDir.resolve(s"source-manifest/${name}"), _source_manifest_yaml(publication), _source_manifest_json(publication))
  }

  private def _write_pair(base: Path, yaml: String, json: JsValue): Unit = {
    _write_text(Paths.get(base.toString + ".yaml"), yaml)
    _write_text(Paths.get(base.toString + ".json"), Json.prettyPrint(json) + "\n")
  }

  private def _catalog_project_yaml(p: Publication): String =
    _yaml_header("catalog-project") +
      _project_yaml(p.project) +
      _publication_yaml(p.project)

  private def _catalog_project_json(p: Publication): JsValue =
    Json.obj(
      "schema" -> Schema,
      "type" -> "catalog-project",
      "project" -> _project_json(p.project),
      "publication" -> _publication_json(p.project)
    )

  private def _catalog_sample_yaml(p: Publication): String =
    _yaml_header("catalog-sample") +
      _project_yaml(p.project) +
      s"""sample:
         |  name: ${_yaml_string(p.project.name)}
         |  project_name: ${_yaml_string(p.project.name)}
         |  kind: ${_yaml_string(p.project.kind)}
         |""".stripMargin

  private def _catalog_sample_json(p: Publication): JsValue =
    Json.obj(
      "schema" -> Schema,
      "type" -> "catalog-sample",
      "project" -> _project_json(p.project),
      "sample" -> Json.obj(
        "name" -> p.project.name,
        "projectName" -> p.project.name,
        "kind" -> p.project.kind
      )
    )

  private def _sample_metadata_yaml(p: Publication): String =
    _yaml_header("sample-metadata") +
      _project_yaml(p.project) +
      _publication_yaml(p.project)

  private def _sample_metadata_json(p: Publication): JsValue =
    Json.obj(
      "schema" -> Schema,
      "type" -> "sample-metadata",
      "project" -> _project_json(p.project),
      "publication" -> _publication_json(p.project)
    )

  private def _artifact_yaml(p: Publication, layer: String): String =
    _yaml_header(s"${layer}-artifact") +
      _project_yaml(p.project) +
      s"""artifact:
         |  layer: ${_yaml_string(layer)}
         |  status: placeholder
         |  files: []
         |""".stripMargin

  private def _artifact_json(p: Publication, layer: String): JsValue =
    Json.obj(
      "schema" -> Schema,
      "type" -> s"${layer}-artifact",
      "project" -> _project_json(p.project),
      "artifact" -> Json.obj(
        "layer" -> layer,
        "status" -> "placeholder",
        "files" -> Json.arr()
      )
    )

  private def _source_manifest_yaml(p: Publication): String =
    _yaml_header("source-manifest") +
      _project_yaml(p.project) +
      s"""files:
         |${p.sourceFiles.map(_source_file_yaml).mkString}""".stripMargin

  private def _source_manifest_json(p: Publication): JsValue =
    Json.obj(
      "schema" -> Schema,
      "type" -> "source-manifest",
      "project" -> _project_json(p.project),
      "files" -> JsArray(p.sourceFiles.map { f =>
        Json.obj(
          "path" -> f.path,
          "size" -> f.size,
          "sha256" -> f.sha256
        )
      })
    )

  private def _yaml_header(kind: String): String =
    s"""schema: ${_yaml_string(Schema)}
       |type: ${_yaml_string(kind)}
       |""".stripMargin

  private def _project_yaml(p: ProjectMetadata): String =
    s"""project:
       |  name: ${_yaml_string(p.name)}
       |  title: ${_yaml_string(p.title)}
       |  kind: ${_yaml_string(p.kind)}
       |  organization: ${_yaml_string(p.organization)}
       |  version: ${_yaml_string(p.version)}
       |  scala_version: ${_yaml_string(p.scalaVersion)}
       |  sbt_version: ${_yaml_string(p.sbtVersion)}
       |""".stripMargin

  private def _publication_yaml(p: ProjectMetadata): String = {
    val path = p.publicationPath.map(x => s"  path: ${_yaml_string(x)}\n").getOrElse("")
    s"""publication:
       |  source_manifest: source-manifest/${p.name}
       |${path}""".stripMargin
  }

  private def _publication_json(p: ProjectMetadata): JsValue = {
    val base = Json.obj("sourceManifest" -> s"source-manifest/${p.name}")
    p.publicationPath match {
      case Some(path) => base + ("path" -> JsString(path))
      case None => base
    }
  }

  private def _source_file_yaml(p: SourceFile): String =
    s"""  - path: ${_yaml_string(p.path)}
       |    size: ${p.size}
       |    sha256: ${_yaml_string(p.sha256)}
       |""".stripMargin

  private def _project_json(p: ProjectMetadata): JsValue =
    Json.obj(
      "name" -> p.name,
      "title" -> p.title,
      "kind" -> p.kind,
      "organization" -> p.organization,
      "version" -> p.version,
      "scalaVersion" -> p.scalaVersion,
      "sbtVersion" -> p.sbtVersion
    )

  private def _source_manifest(projectDir: Path, saveDir: Path, excludes: Set[String]): Vector[SourceFile] = {
    val save = saveDir.toAbsolutePath.normalize()
    val stream = Files.walk(projectDir)
    try {
      stream.iterator().asScala.toVector.collect {
        case p if Files.isRegularFile(p) && !_excluded(projectDir, p, save, excludes) =>
          val rel = projectDir.relativize(p).toString.replace('\\', '/')
          SourceFile(rel, Files.size(p), _sha256(p))
      }.sortBy(_.path)
    } finally {
      stream.close()
    }
  }

  private def _excluded(projectDir: Path, path: Path, saveDir: Path, excludes: Set[String]): Boolean = {
    val abs = path.toAbsolutePath.normalize()
    val rel = projectDir.relativize(path).toString.replace('\\', '/')
    val segments = rel.split('/').toVector
    val normalizedExcludes = excludes.map(_.trim.stripPrefix("/").stripSuffix("/")).filter(_.nonEmpty)
    val excludedByName = normalizedExcludes.exists(x => !x.contains("/") && segments.contains(x))
    val excludedByPath = normalizedExcludes.exists(x => x.contains("/") && (rel == x || rel.startsWith(x + "/")))
    excludedByName || excludedByPath || abs.startsWith(saveDir)
  }

  private def _sha256(path: Path): String = {
    val digest = MessageDigest.getInstance("SHA-256")
    val in = Files.newInputStream(path)
    val buffer = new Array[Byte](8192)
    try {
      var n = in.read(buffer)
      while (n >= 0) {
        if (n > 0)
          digest.update(buffer, 0, n)
        n = in.read(buffer)
      }
    } finally {
      in.close()
    }
    digest.digest().map(b => f"${b & 0xff}%02x").mkString
  }

  private def _detect_kind(projectDir: Path, buildSbt: String, samplesDir: Path): String = {
    val childBuilds = Option(projectDir.toFile.listFiles()).toVector.flatten.count(f => f.isDirectory && new java.io.File(f, "build.sbt").isFile)
    val sampleBuilds = Option(samplesDir.toFile.listFiles()).toVector.flatten.count(f => f.isDirectory && new java.io.File(f, "build.sbt").isFile)
    if (_contains_sar_marker(projectDir, buildSbt))
      "sar"
    else if (_contains_car_marker(projectDir, buildSbt))
      "car"
    else if (sampleBuilds > 1 || childBuilds > 1 || _project_definition_count(buildSbt) > 1)
      "sample-multi"
    else
      "sample-single"
  }

  private def _contains_sar_marker(projectDir: Path, buildSbt: String): Boolean =
    buildSbt.contains("cozyPackaging := \"sar\"") ||
      Files.isRegularFile(projectDir.resolve("subsystem-descriptor.yaml")) ||
      Files.isRegularFile(projectDir.resolve("subsystem-descriptor.yml"))

  private def _contains_car_marker(projectDir: Path, buildSbt: String): Boolean =
    buildSbt.contains("CozyPlugin") &&
      (buildSbt.contains("cozyPackaging := \"car\"") ||
        Files.isDirectory(projectDir.resolve("src/main/car")) ||
        Files.isDirectory(projectDir.resolve("src/main/cozy")))

  private def _project_definition_count(buildSbt: String): Int =
    "(?m)^\\s*lazy\\s+val\\s+\\w+\\s*=\\s*\\(?project\\b".r.findAllIn(buildSbt).length

  private def _sbt_setting(buildSbt: String, key: String): Option[String] = {
    val pattern = ("""(?m)^\s*(?:ThisBuild\s*/\s*)?""" + java.util.regex.Pattern.quote(key) + """\s*:=\s*"([^"]+)""").r
    pattern.findFirstMatchIn(buildSbt).map(_.group(1).trim).filter(_.nonEmpty)
  }

  private def _sbt_version(projectDir: Path): Option[String] = {
    val path = projectDir.resolve("project/build.properties")
    if (!Files.isRegularFile(path))
      None
    else
      Files.readAllLines(path, StandardCharsets.UTF_8).asScala.collectFirst {
        case line if line.trim.startsWith("sbt.version=") =>
          line.trim.substring("sbt.version=".length).trim
      }.filter(_.nonEmpty)
  }

  private def _project_dir(args: List[String]): Path =
    _value(args, "project").orElse(_positional_args(args).headOption).
      map(p => Paths.get(p).toAbsolutePath.normalize()).
      getOrElse(RAISE.invalidArgumentFault("Missing project directory for publish-project"))

  private def _publication_output(projectDir: Path, args: List[String], config: CozyProjectYamlConfig.Config): Path =
    _value(args, "save").
      map(p => Paths.get(p).toAbsolutePath.normalize()).
      orElse(_config_path(projectDir, config.value("publication.output"))).
      getOrElse(projectDir.resolve("target/publish.d").toAbsolutePath.normalize())

  private def _config_path(projectDir: Path, value: Option[String]): Option[Path] =
    value.map { p =>
      val path = Paths.get(p)
      if (path.isAbsolute)
        path.normalize()
      else
        projectDir.resolve(path).toAbsolutePath.normalize()
    }

  private def _positional_args(args: List[String]): Vector[String] = {
    val optionNamesWithValue = Set("project", "save", "kind", "name", "title", "path", "organization", "version", "scala-version", "sbt-version")
    val b = Vector.newBuilder[String]
    var skipNext = false
    args.foreach { arg =>
      if (skipNext) {
        skipNext = false
      } else if (arg.startsWith("--")) {
        val key = arg.drop(2).takeWhile(_ != '=')
        if (!arg.contains("=") && optionNamesWithValue.contains(key))
          skipNext = true
      } else {
        b += arg
      }
    }
    b.result()
  }

  private def _value(args: List[String], key: String): Option[String] = {
    val prefix = s"--${key}="
    args.collectFirst {
      case s if s.startsWith(prefix) => s.substring(prefix.length)
    }.orElse {
      args.sliding(2).collectFirst {
        case List(flag, value) if flag == s"--${key}" => value
      }
    }.map(_.trim).filter(_.nonEmpty)
  }

  private def _explicit_name(args: List[String], config: CozyProjectYamlConfig.Config): Boolean =
    _value(args, "name").nonEmpty || config.value("publication.name").nonEmpty

  private def _validate_publication_path(value: String): String = {
    val path = value.trim.stripPrefix("/").stripSuffix("/")
    if (path.isEmpty || path.split('/').exists(segment => SlugPattern.findFirstIn(segment).forall(_ != segment)))
      RAISE.invalidArgumentFault(s"Invalid publication path: ${value}. Expected slash-separated slug segments")
    else
      path
  }

  private def _validate_name(value: String, label: String): String = {
    val name = value.trim
    SlugPattern.findFirstIn(name) match {
      case Some(x) if x == name => name
      case _ => RAISE.invalidArgumentFault(s"Invalid ${label}: ${value}. Expected ${SlugPattern.regex}")
    }
  }

  private def _slugify(value: String): String =
    value.toLowerCase(java.util.Locale.ROOT).
      replaceAll("[^a-z0-9]+", "-").
      stripPrefix("-").
      stripSuffix("-")

  private def _yaml_string(value: String): String =
    "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\""

  private def _write_text(path: Path, text: String): Unit = {
    Option(path.getParent).foreach(Files.createDirectories(_))
    Files.writeString(path, text, StandardCharsets.UTF_8)
  }
}

private object CozyWarehouseIndexer {
  private val Schema = "cozy.publish-project.v1"
  private val SlugPattern = "^[a-z0-9][a-z0-9-]*$".r
  private val ChecksumExtensions = Set("sha1", "md5")
  private val ArtifactExtensions = Set("jar", "pom", "car", "sar", "zip")

  final case class MavenCoordinate(groupId: String, artifactId: String) {
    def path: String = groupId.replace('.', '/') + "/" + artifactId
    def key: String = s"${groupId}:${artifactId}"
  }
  final case class IndexedFile(
    layer: String,
    artifactType: String,
    groupId: Option[String],
    artifactId: Option[String],
    version: String,
    classifier: Option[String],
    extension: String,
    path: String,
    name: String,
    size: Long,
    sha256: String,
    sha1: Option[String],
    md5: Option[String]
  )
  final case class MavenArtifact(coordinate: MavenCoordinate, versions: Vector[String], latestRelease: Option[String], files: Vector[IndexedFile])
  final case class RepositoryArtifact(kind: String, versions: Vector[String], files: Vector[IndexedFile])
  final case class IndexResult(name: String, title: String, maven: Vector[MavenArtifact], repository: Vector[RepositoryArtifact])

  def index(args: List[String]): Unit = {
    val warehouseDir = _warehouse_dir(args)
    if (!Files.isDirectory(warehouseDir))
      RAISE.invalidArgumentFault(s"Warehouse directory does not exist: ${warehouseDir}")
    val saveDir = _required_path(args, "save")
    val name = _value(args, "name").map(_validate_name).getOrElse(RAISE.invalidArgumentFault("Missing --name"))
    val title = _value(args, "title").getOrElse(name)
    val coordinates = _csv(args, "maven-coordinates").map(_coordinate)
    val repositoryKinds = _csv(args, "repository-artifacts").map(_.toLowerCase(java.util.Locale.ROOT)).filter(_.nonEmpty)
    val repositoryModules = _csv(args, "repository-modules").filter(_.nonEmpty) match {
      case Vector() => Vector(name)
      case xs => xs
    }
    val result = IndexResult(
      name = name,
      title = title,
      maven = coordinates.map(_index_maven(warehouseDir, _)),
      repository = repositoryKinds.map(_index_repository(warehouseDir, _, repositoryModules))
    )
    _write(result, saveDir)
  }

  private def _write(p: IndexResult, saveDir: Path): Unit = {
    _write_pair(saveDir.resolve(s"maven/artifacts/${p.name}"), _maven_yaml(p), _maven_json(p))
    _write_pair(saveDir.resolve(s"repository/artifacts/${p.name}"), _repository_yaml(p), _repository_json(p))
    _write_pair(saveDir.resolve(s"releases/${p.name}"), _release_yaml(p), _release_json(p))
  }

  private def _index_maven(warehouseDir: Path, coordinate: MavenCoordinate): MavenArtifact = {
    val artifactDir = warehouseDir.resolve("maven").resolve(coordinate.path)
    val files =
      if (Files.isDirectory(artifactDir)) {
        val stream = Files.walk(artifactDir)
        try {
          stream.iterator().asScala.toVector.collect {
            case p if Files.isRegularFile(p) && _is_artifact_file(p) =>
              val version = artifactDir.relativize(p).iterator().asScala.toVector.headOption.map(_.toString).getOrElse("")
              val parsed = _parse_maven_file(coordinate.artifactId, version, p.getFileName.toString)
              _indexed_file(
                warehouseDir,
                p,
                layer = "maven",
                artifactType = parsed._2,
                groupId = Some(coordinate.groupId),
                artifactId = Some(coordinate.artifactId),
                version = version,
                classifier = parsed._1
              )
          }.sortBy(_.path)
        } finally {
          stream.close()
        }
      } else {
        Vector.empty
      }
    val versions = _sort_versions(files.map(_.version).distinct)
    MavenArtifact(coordinate, versions, _latest_release(versions), files)
  }

  private def _index_repository(warehouseDir: Path, kind: String, modules: Vector[String]): RepositoryArtifact = {
    val files = modules.flatMap { module =>
      val artifactDir = warehouseDir.resolve("repository").resolve(kind).resolve(module)
      if (Files.isDirectory(artifactDir)) {
        val stream = Files.walk(artifactDir)
        try {
          stream.iterator().asScala.toVector.collect {
            case p if Files.isRegularFile(p) && p.getFileName.toString.toLowerCase(java.util.Locale.ROOT).endsWith(s".${kind}") =>
              _indexed_file(
                warehouseDir,
                p,
                layer = "repository",
                artifactType = kind,
                groupId = None,
                artifactId = Some(module),
                version = _infer_repository_version(warehouseDir, p, kind).getOrElse("unknown"),
                classifier = None
              )
          }
        } finally {
          stream.close()
        }
      } else {
        Vector.empty
      }
    }.sortBy(_.path)
    RepositoryArtifact(kind, _sort_versions(files.map(_.version).distinct), files)
  }

  private def _indexed_file(
    warehouseDir: Path,
    path: Path,
    layer: String,
    artifactType: String,
    groupId: Option[String],
    artifactId: Option[String],
    version: String,
    classifier: Option[String]
  ): IndexedFile = {
    val rel = warehouseDir.relativize(path).toString.replace('\\', '/')
    val extension = path.getFileName.toString.reverse.takeWhile(_ != '.').reverse
    IndexedFile(
      layer = layer,
      artifactType = artifactType,
      groupId = groupId,
      artifactId = artifactId,
      version = version,
      classifier = classifier,
      extension = extension,
      path = rel,
      name = path.getFileName.toString,
      size = Files.size(path),
      sha256 = _sha256(path),
      sha1 = _sidecar(path, "sha1"),
      md5 = _sidecar(path, "md5")
    )
  }

  private def _maven_yaml(p: IndexResult): String =
    _yaml_header("maven-artifact") +
      _project_yaml(p) +
      s"""artifact:
         |  layer: "maven"
         |  status: ${_yaml_string(if (p.maven.exists(_.files.nonEmpty)) "available" else "missing")}
         |  coordinates:
         |${p.maven.map(_maven_coordinate_yaml).mkString}
         |  files:
         |${p.maven.flatMap(_.files).map(_file_yaml).mkString}""".stripMargin

  private def _maven_json(p: IndexResult): JsValue =
    Json.obj(
      "schema" -> Schema,
      "type" -> "maven-artifact",
      "project" -> _project_json(p),
      "artifact" -> Json.obj(
        "layer" -> "maven",
        "status" -> (if (p.maven.exists(_.files.nonEmpty)) "available" else "missing"),
        "coordinates" -> JsArray(p.maven.map { x =>
          Json.obj(
            "groupId" -> x.coordinate.groupId,
            "artifactId" -> x.coordinate.artifactId,
            "versions" -> x.versions,
            "latestRelease" -> x.latestRelease
          )
        }),
        "files" -> JsArray(p.maven.flatMap(_.files).map(_file_json))
      )
    )

  private def _repository_yaml(p: IndexResult): String =
    _yaml_header("repository-artifact") +
      _project_yaml(p) +
      s"""artifact:
         |  layer: "repository"
         |  status: ${_yaml_string(if (p.repository.exists(_.files.nonEmpty)) "available" else "missing")}
         |  kinds:
         |${p.repository.map(_repository_kind_yaml).mkString}
         |  files:
         |${p.repository.flatMap(_.files).map(_file_yaml).mkString}""".stripMargin

  private def _repository_json(p: IndexResult): JsValue =
    Json.obj(
      "schema" -> Schema,
      "type" -> "repository-artifact",
      "project" -> _project_json(p),
      "artifact" -> Json.obj(
        "layer" -> "repository",
        "status" -> (if (p.repository.exists(_.files.nonEmpty)) "available" else "missing"),
        "kinds" -> JsArray(p.repository.map { x =>
          Json.obj(
            "type" -> x.kind,
            "versions" -> x.versions,
            "latestRelease" -> _latest_release(x.versions)
          )
        }),
        "files" -> JsArray(p.repository.flatMap(_.files).map(_file_json))
      )
    )

  private def _release_yaml(p: IndexResult): String =
    _yaml_header("release-history") +
      _project_yaml(p) +
      s"""release:
         |  name: ${_yaml_string(p.name)}
         |  latest: ${_yaml_string(_latest_release(_release_versions_ascending(p)).getOrElse(""))}
         |  versions:
         |${_release_versions(p).map(v => _release_version_yaml(v, p)).mkString}""".stripMargin

  private def _release_json(p: IndexResult): JsValue =
    Json.obj(
      "schema" -> Schema,
      "type" -> "release-history",
      "project" -> _project_json(p),
      "release" -> Json.obj(
        "name" -> p.name,
        "latest" -> _latest_release(_release_versions_ascending(p)),
        "versions" -> JsArray(_release_versions(p).map { v =>
          Json.obj(
            "version" -> v,
            "artifacts" -> JsArray(_release_artifacts(v, p))
          )
        })
      )
    )

  private def _release_versions(p: IndexResult): Vector[String] =
    _release_versions_ascending(p).reverse

  private def _release_versions_ascending(p: IndexResult): Vector[String] =
    _sort_versions((p.maven.flatMap(_.versions) ++ p.repository.flatMap(_.versions)).filter(_ != "unknown").distinct)

  private def _release_artifacts(version: String, p: IndexResult): Vector[JsValue] =
    p.maven.flatMap(_.files).filter(_.version == version).map { f =>
      Json.obj(
        "layer" -> "maven",
        "groupId" -> f.groupId,
        "artifactId" -> f.artifactId,
        "path" -> f.path
      )
    } ++ p.repository.flatMap(_.files).filter(_.version == version).map { f =>
      Json.obj(
        "layer" -> "repository",
        "type" -> f.artifactType,
        "module" -> f.artifactId,
        "path" -> f.path
      )
    }

  private def _release_version_yaml(version: String, p: IndexResult): String =
    s"""    - version: ${_yaml_string(version)}
       |      artifacts:
       |${_release_artifacts(version, p).map(x => s"        - ${Json.stringify(x)}\n").mkString}""".stripMargin

  private def _maven_coordinate_yaml(p: MavenArtifact): String =
    s"""    - group_id: ${_yaml_string(p.coordinate.groupId)}
       |      artifact_id: ${_yaml_string(p.coordinate.artifactId)}
       |      latest_release: ${_yaml_string(p.latestRelease.getOrElse(""))}
       |      versions: [${p.versions.map(_yaml_string).mkString(", ")}]
       |""".stripMargin

  private def _repository_kind_yaml(p: RepositoryArtifact): String =
    s"""    - type: ${_yaml_string(p.kind)}
       |      latest_release: ${_yaml_string(_latest_release(p.versions).getOrElse(""))}
       |      versions: [${p.versions.map(_yaml_string).mkString(", ")}]
       |""".stripMargin

  private def _file_yaml(p: IndexedFile): String =
    s"""    - path: ${_yaml_string(p.path)}
       |      name: ${_yaml_string(p.name)}
       |      version: ${_yaml_string(p.version)}
       |      type: ${_yaml_string(p.artifactType)}
       |      extension: ${_yaml_string(p.extension)}
       |      classifier: ${_yaml_string(p.classifier.getOrElse(""))}
       |      size: ${p.size}
       |      sha256: ${_yaml_string(p.sha256)}
       |      sha1: ${_yaml_string(p.sha1.getOrElse(""))}
       |      md5: ${_yaml_string(p.md5.getOrElse(""))}
       |""".stripMargin

  private def _file_json(p: IndexedFile): JsValue =
    Json.obj(
      "layer" -> p.layer,
      "type" -> p.artifactType,
      "groupId" -> p.groupId,
      "artifactId" -> p.artifactId,
      "version" -> p.version,
      "classifier" -> p.classifier,
      "extension" -> p.extension,
      "path" -> p.path,
      "downloadPath" -> p.path,
      "name" -> p.name,
      "size" -> p.size,
      "sha256" -> p.sha256,
      "sha1" -> p.sha1,
      "md5" -> p.md5
    )

  private def _yaml_header(kind: String): String =
    s"""schema: ${_yaml_string(Schema)}
       |type: ${_yaml_string(kind)}
       |""".stripMargin

  private def _project_yaml(p: IndexResult): String =
    s"""project:
       |  name: ${_yaml_string(p.name)}
       |  title: ${_yaml_string(p.title)}
       |""".stripMargin

  private def _project_json(p: IndexResult): JsValue =
    Json.obj("name" -> p.name, "title" -> p.title)

  private def _write_pair(base: Path, yaml: String, json: JsValue): Unit = {
    _write_text(Paths.get(base.toString + ".yaml"), yaml)
    _write_text(Paths.get(base.toString + ".json"), Json.prettyPrint(json) + "\n")
  }

  private def _warehouse_dir(args: List[String]): Path =
    _value(args, "warehouse").orElse(_positional_args(args).headOption).
      map(p => Paths.get(p).toAbsolutePath.normalize()).
      getOrElse(RAISE.invalidArgumentFault("Missing warehouse directory for index-warehouse"))

  private def _required_path(args: List[String], key: String): Path =
    _value(args, key).
      map(p => Paths.get(p).toAbsolutePath.normalize()).
      getOrElse(RAISE.invalidArgumentFault(s"Missing --${key}"))

  private def _value(args: List[String], key: String): Option[String] = {
    val prefix = s"--${key}="
    args.collectFirst {
      case s if s.startsWith(prefix) => s.substring(prefix.length)
    }.orElse {
      args.sliding(2).collectFirst {
        case List(flag, value) if flag == s"--${key}" => value
      }
    }.map(_.trim).filter(_.nonEmpty)
  }

  private def _csv(args: List[String], key: String): Vector[String] =
    _value(args, key).toVector.flatMap(_.split(',')).map(_.trim).filter(_.nonEmpty)

  private def _positional_args(args: List[String]): Vector[String] = {
    val optionNamesWithValue = Set("warehouse", "save", "name", "title", "maven-coordinates", "repository-artifacts", "repository-modules")
    val b = Vector.newBuilder[String]
    var skipNext = false
    args.foreach { arg =>
      if (skipNext) {
        skipNext = false
      } else if (arg.startsWith("--")) {
        val key = arg.drop(2).takeWhile(_ != '=')
        if (!arg.contains("=") && optionNamesWithValue.contains(key))
          skipNext = true
      } else {
        b += arg
      }
    }
    b.result()
  }

  private def _coordinate(s: String): MavenCoordinate =
    s.split(':').toVector match {
      case Vector(groupId, artifactId) if groupId.nonEmpty && artifactId.nonEmpty =>
        MavenCoordinate(groupId, artifactId)
      case _ =>
        RAISE.invalidArgumentFault(s"Invalid Maven coordinate: ${s}. Expected groupId:artifactId")
    }

  private def _is_artifact_file(path: Path): Boolean = {
    val name = path.getFileName.toString
    val ext = name.reverse.takeWhile(_ != '.').reverse.toLowerCase(java.util.Locale.ROOT)
    ArtifactExtensions.contains(ext) && !ChecksumExtensions.contains(ext)
  }

  private def _parse_maven_file(artifactId: String, version: String, name: String): (Option[String], String) = {
    val ext = name.reverse.takeWhile(_ != '.').reverse
    val base = name.stripSuffix("." + ext)
    val prefix = s"${artifactId}-${version}"
    val classifier =
      if (base == prefix)
        None
      else if (base.startsWith(prefix + "-"))
        Some(base.substring(prefix.length + 1))
      else
        None
    classifier -> ext
  }

  private def _infer_version(name: String, kind: String): Option[String] = {
    val base = name.stripSuffix("." + kind)
    "([0-9]+(?:\\.[0-9A-Za-z-]+)+)(?:[-_].*)?$".r.findFirstMatchIn(base).map(_.group(1))
  }

  private def _infer_repository_version(warehouseDir: Path, path: Path, kind: String): Option[String] = {
    val rel = warehouseDir.relativize(path).iterator().asScala.toVector.map(_.toString)
    rel.reverse.drop(1).find(_looks_like_version).orElse(_infer_version(path.getFileName.toString, kind))
  }

  private def _looks_like_version(value: String): Boolean =
    value.matches("[0-9]+(?:\\.[0-9A-Za-z-]+)+(?:-[0-9A-Za-z.-]+)?")

  private def _latest_release(versions: Vector[String]): Option[String] =
    versions.reverse.find(!_.toUpperCase(java.util.Locale.ROOT).contains("SNAPSHOT")).orElse(versions.lastOption)

  private def _sort_versions(xs: Vector[String]): Vector[String] =
    xs.sortBy(x => x.split("[.-]").toVector.map(part => f"${Try(part.toInt).getOrElse(0)}%08d:$part").mkString("|"))

  private def _sidecar(path: Path, ext: String): Option[String] = {
    val p = Paths.get(path.toString + "." + ext)
    if (Files.isRegularFile(p))
      Some(Files.readString(p, StandardCharsets.UTF_8).trim.split("\\s+").headOption.getOrElse(""))
    else
      None
  }

  private def _sha256(path: Path): String = {
    val digest = MessageDigest.getInstance("SHA-256")
    val in = Files.newInputStream(path)
    val buffer = new Array[Byte](8192)
    try {
      var n = in.read(buffer)
      while (n >= 0) {
        if (n > 0)
          digest.update(buffer, 0, n)
        n = in.read(buffer)
      }
    } finally {
      in.close()
    }
    digest.digest().map(b => f"${b & 0xff}%02x").mkString
  }

  private def _validate_name(value: String): String = {
    val name = value.trim
    SlugPattern.findFirstIn(name) match {
      case Some(x) if x == name => name
      case _ => RAISE.invalidArgumentFault(s"Invalid publication name: ${value}. Expected ${SlugPattern.regex}")
    }
  }

  private def _yaml_string(value: String): String =
    "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\""

  private def _write_text(path: Path, text: String): Unit = {
    Option(path.getParent).foreach(Files.createDirectories(_))
    Files.writeString(path, text, StandardCharsets.UTF_8)
  }
}

private[cozy] object CozySbtBridge {
  def execute(args: List[String]): Unit =
    args match {
      case "v1" :: rest =>
        executeV1(rest)
      case _ =>
        RAISE.invalidArgumentFault("Missing sbt-bridge version. Expected: sbt-bridge v1 --request=<file>")
    }

  private def executeV1(args: List[String]): Unit = {
    val requestPath = _required_path(args, "request")
    val request = _load_request(requestPath)
    request.action match {
      case "generate" =>
        _run_generation(request.arguments)
      case "package-car" =>
        CozyArchivePackager.buildCar(request.arguments.toList)
      case "package-sar" =>
        CozyArchivePackager.buildSar(request.arguments.toList)
      case "publish-project" =>
        CozyPublicationCompiler.publish(request.arguments.toList)
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

  private def _required_path(args: List[String], key: String): Path = {
    val prefix = s"--${key}="
    args.collectFirst {
      case s if s.startsWith(prefix) => Paths.get(s.substring(prefix.length)).toAbsolutePath.normalize()
    }.orElse {
      args.sliding(2).collectFirst {
        case List(flag, value) if flag == s"--${key}" => Paths.get(value).toAbsolutePath.normalize()
      }
    }.getOrElse(RAISE.invalidArgumentFault(s"Missing --${key}"))
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
