package cozy

import org.goldenport.RAISE
import org.goldenport.config.ConfigLoader
import org.goldenport.i18n.I18NString
import org.goldenport.io.InputSource
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
import io.circe.{Json => CJson}
import cozy.web.jetty.JettyServer
import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path, Paths, StandardCopyOption}
import java.security.MessageDigest
import java.util.zip.{ZipEntry, ZipOutputStream}
import scala.util.Try
import scala.collection.mutable.ArrayBuffer
import scala.collection.JavaConverters._
import scala.sys.process._

/*
 * @since   Dec.  4, 2021
 *  version Dec. 19, 2021
 *  version Jan.  1, 2022
 *  version Feb. 28, 2022
 *  version Aug. 20, 2025
 *  version Mar. 17, 2026
 *  version Apr. 29, 2026
 * @version May. 20, 2026
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
    if (!_execute_car_sbt_project(args) && !_execute_publish_project(args) && !_execute_distribute_samples(args) && !_execute_index_warehouse(args) && !_execute_sbt_bridge(args) && !_execute_package_archive(args))
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
      case Some(("publish-maven-repository", rest)) =>
        CozyPublicationCompiler.publishMavenRepository(rest)
        true
      case Some(("unpublish-project", rest)) =>
        CozyPublicationCompiler.unpublish(rest)
        true
      case _ =>
        false
    }

  private def _execute_distribute_samples(args: Array[String]): Boolean =
    _leading_command(args) match {
      case Some(("distribute-samples", rest)) =>
        CozySampleDistributor.distribute(rest)
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
  private val _default_sbt_version = "1.9.7"
  private val _default_sbt_cozy_version = "0.1.5-SNAPSHOT"

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
      _value_options.exists(x => p.startsWith(s"--${x}="))

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
      |def sampleVersion(envName: String, filename: String, fallback: String): String =
      |  sys.env.get(envName)
      |    .orElse {
      |      sys.env.get("CNCF_SAMPLES_ROOT").flatMap { root =>
      |        val versionFile = file(root) / "versions" / filename
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
      |    useCoursier := false,
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
      |def sampleVersion(envName: String, filename: String, fallback: String): String =
      |  sys.env.get(envName)
      |    .orElse {
      |      sys.env.get("TEXTUS_SAMPLES_ROOT")
      |        .orElse(sys.env.get("CNCF_SAMPLES_ROOT"))
      |        .flatMap { root =>
      |          val versionFile = file(root) / "versions" / filename
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
      |  useCoursier := false,
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
      |  package-car --save=<file> --main-jar=<file> --name=<name> --version=<version> [--component=<component>] [--project-dir=<dir>] [--car-dir=<dir>] [--entities=<spec>]
      |      Build a CAR archive. Project CAR policy comes from --project-dir/project.yaml and --project-dir/.cozy/config.yaml.
      |
      |  package-sar --save=<file> --source-dir=<dir> --name=<name> --version=<version>
      |      Build a SAR archive.
      |
      |  publish-project <project-dir> [--save=<dir>] [--kind=car|sar|sample-single|sample-multi|maven-repository] [--name=<slug>] [--title=<title>] [--path=<path>]
      |      Generate SmartDox site BoK publication registry sources from an sbt project.
      |      Writes or replaces one publication bundle under the registry.
      |
      |  publish-maven-repository <repository-dir> --save=<dir> --name=<slug> [--title=<title>] [--path=<path>] [--maven-coordinates=<group:artifact,...>]
      |      Generate a SmartDox publication bundle and Maven artifact metadata from a Maven repository directory.
      |
      |  unpublish-project --save=<dir> --name=<slug>
      |      Remove a publication bundle from a publication registry.
      |
      |  distribute-samples <project-dir> --warehouse=<dir> --name=<slug> --version=<version> [--samples-dir=<dir>] [--dry-run]
      |      Zip the sample collection and each sample project under warehouse/repository/download/<publication.path>.
      |      With --dry-run, print planned output paths without writing archives.
      |
      |  index-warehouse <warehouse-dir> --save=<dir> --name=<slug> [--title=<title>] [--repository-artifacts=car,sar,zip] [--repository-modules=<module,...>] [--download-samples=<publication,...>]
      |      Generate publication registry download/repository release metadata by indexing a warehouse.
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
    val mainjar = _required_path(args, "main-jar")
    val projectdir = _path(args, "project-dir")
    val config = _project_config(projectdir)
    val libjars = if (_include_dependencies(projectdir, config)) _paths(args, "lib-jars") else Vector.empty
    val spijars = _paths(args, "spi-jars")
    val cardir = _path(args, "car-dir").orElse(_car_dir(projectdir, config))
    val defaultconf = _path(args, "default-conf").orElse(cardir.map(_.resolve("config/default.conf")).filter(Files.isRegularFile(_)))
    val dependencymanifest = _path(args, "dependency-manifest").orElse(_dependency_manifest(projectdir, config))
    val webdir = _path(args, "web-dir").orElse(projectdir.map(_.resolve("src/main/web")).filter(Files.isDirectory(_)))
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
    }.filter(_.baseprovidedmodules.nonEmpty)

  private def _runtime_catalog_paths(
    projectdir: Option[Path],
    config: CozyProjectYamlConfig.Config
  ): Vector[Path] = {
    val configured =
      config.value("packaging.car.runtime.cncf.catalog").
        orElse(config.value("runtime.catalog.path")).
        map(value => _config_path(projectdir.getOrElse(Paths.get(".").toAbsolutePath.normalize()), value)).
        toVector
    val local = projectdir.toVector.flatMap { dir =>
      Vector(
        dir.resolve("repository/textus/runtime-catalog.yaml"),
        dir.resolve("src/main/catalog/cncf.yaml")
      )
    }
    (configured ++ local).distinct
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
    val extensionJars = _paths(args, "extension-jars")
    val applicationConf = _path(args, "application-conf")
    val subsystemsources = _archive_sources(sourcedir, sourcefiles)
    _write_archive(
      save,
      subsystemsources ++
        extensionJars.map(p => p -> s"extension/${p.getFileName}") ++
        applicationConf.toVector.map(_ -> "config/application.conf"),
      Vector("extension", "config")
    )
  }

  private def _archive_sources(sourcedir: Path, includes: Vector[String] = Vector.empty): Vector[(Path, String)] = {
    if (!Files.exists(sourcedir))
      Vector.empty
    else {
      val includeSet = includes.map(_.replace('\\', '/')).toSet
      val stream = Files.walk(sourcedir)
      try {
        stream.iterator().asScala.toVector.collect {
          case p if Files.isRegularFile(p) =>
            p -> sourcedir.relativize(p).toString.replace('\\', '/')
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
  final case class Config(
    values: Map[String, String],
    lists: Map[String, Vector[String]],
    json: Option[CJson] = None
  ) {
    def value(path: String): Option[String] = values.get(path).map(_.trim).filter(_.nonEmpty)
    def list(path: String): Vector[String] = lists.getOrElse(path, Vector.empty).map(_.trim).filter(_.nonEmpty)
    def mapUnder(path: String): Map[String, String] = {
      val prefix = path + "."
      values.collect {
        case (key, value) if key.startsWith(prefix) && value.trim.nonEmpty =>
          key.substring(prefix.length) -> value.trim
      }
    }
    def boolean(path: String): Option[Boolean] =
      value(path).map(_.toLowerCase(java.util.Locale.ROOT)).collect {
        case "true" | "yes" | "on" => true
        case "false" | "no" | "off" => false
      }
    def descriptiveAttributes: DescriptiveAttributes =
      json.map { root =>
        val top = DescriptiveAttributes.fromJson(root)
        val project = root.hcursor.downField("project").focus.map(DescriptiveAttributes.fromJson).getOrElse(DescriptiveAttributes.empty)
        val publication = root.hcursor.downField("publication").focus.map(DescriptiveAttributes.fromJson).getOrElse(DescriptiveAttributes.empty)
        top.orElse(project).orElse(publication)
      }.getOrElse(DescriptiveAttributes.empty)
    def publicationPageJsons: Vector[CJson] =
      json.flatMap(_.hcursor.downField("publication").downField("pages").focus).
        flatMap(_.asArray).
        getOrElse(Vector.empty)
    def merge(overrideconfig: Config): Config =
      Config(
        values ++ overrideconfig.values,
        lists ++ overrideconfig.lists,
        overrideconfig.json.orElse(json)
      )
  }
  object Config {
    val empty: Config = Config(Map.empty, Map.empty)
  }

  def load(path: Path): Config =
    if (Files.isRegularFile(path))
      parse(Files.readAllLines(path, StandardCharsets.UTF_8).asScala.toVector)
    else
      Config.empty

  def loadPublic(path: Path): Config =
    if (Files.isRegularFile(path)) {
      val json = ConfigLoader.loadConfig[CJson](InputSource(path.toFile)).take
      Config(_flatten_json(json), Map.empty, Some(json))
    } else {
      Config.empty
    }

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

  private def _flatten_json(json: CJson): Map[String, String] =
    _flatten_json("", json)

  private def _flatten_json(prefix: String, json: CJson): Map[String, String] =
    json.asObject.map { obj =>
      obj.toMap.flatMap {
        case (k, v) =>
          val key = if (prefix.isEmpty) k else s"${prefix}.${k}"
          v.asString.map(key -> _).toMap ++ _flatten_json(key, v)
      }
    }.getOrElse(Map.empty)

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

private object CozyPublicationPaths {
  private val _slug_segment_pattern = "^[a-z0-9][a-z0-9-]*$".r
  private val _reserved_publication_roots = Set("metadata", "repository")

  def validatePublicationPath(value: String): String = {
    val path = value.trim.stripPrefix("/").stripSuffix("/")
    if (path.isEmpty || path.split('/').exists(segment => _slug_segment_pattern.findFirstIn(segment).forall(_ != segment)))
      RAISE.invalidArgumentFault(s"Invalid publication path: ${value}. Expected slash-separated slug segments")
    else if (_reserved_publication_roots.contains(path.split('/').headOption.getOrElse("")))
      RAISE.invalidArgumentFault(s"Invalid publication path: ${value}. Reserved top-level path: ${path.split('/').head}")
    else
      path
  }

  def downloadBase(publicationname: String, publicationpath: Option[String]): String =
    publicationpath.map(validatePublicationPath).getOrElse(s"samples/${publicationname}")

  def collectionDownloadPath(publicationname: String, publicationpath: Option[String], version: String): String =
    s"repository/download/${downloadBase(publicationname, publicationpath)}/${version}/${publicationname}-${version}.zip"

  def sampleDownloadPath(publicationname: String, publicationpath: Option[String], samplename: String, version: String): String =
    s"repository/download/${downloadBase(publicationname, publicationpath)}/${version}/${samplename}/${samplename}-${version}.zip"
}

private object CozyPublicationCompiler {
  private val _schema = "cozy.publish-project.v1"
  private val _valid_kinds = Set("car", "sar", "sample-single", "sample-multi", "maven-repository")
  private val _default_excluded_segments = Set(
    "target",
    ".git",
    ".bsp",
    ".bloop",
    ".metals",
    ".idea",
    ".cache",
    ".vscode",
    ".scala-build",
    "car.d",
    "component.d",
    "component-repository.d",
    "tmprepo",
    "repository.d"
  )
  private val _slug_pattern = "^[a-z0-9][a-z0-9-]*$".r

  final case class ProjectMetadata(
    name: String,
    title: String,
    kind: String,
    publicationPath: Option[String],
    descriptiveAttributes: DescriptiveAttributes,
    summary: Option[String],
    description: Option[String],
    organization: String,
    version: String,
    scalaVersion: String,
    sbtVersion: String
  )
  final case class SourceFile(path: String, size: Long, sha256: String)
  final case class ArtifactFile(
    layer: String,
    artifactType: String,
    module: Option[String],
    sampleName: Option[String],
    version: String,
    extension: String,
    path: String,
    name: String
  )
  final case class SamplePublication(
    name: String,
    title: String,
    descriptiveAttributes: DescriptiveAttributes,
    summary: Option[String],
    description: Option[String],
    directory: String,
    version: String,
    root: Path,
    files: Vector[SourceFile]
  )
  final case class Publication(
    project: ProjectMetadata,
    pages: Vector[PublicationPage],
    sourceManifestEnabled: Boolean,
    sourcefiles: Vector[SourceFile],
    samples: Vector[SamplePublication],
    repositoryModules: Vector[String]
  )
  final case class PublicationPage(
    path: String,
    title: DescriptiveAttributes.Text,
    descriptiveAttributes: DescriptiveAttributes
  )

  def publish(args: List[String]): Unit = {
    val projectdir = _project_dir(args)
    if (!Files.isDirectory(projectdir))
      RAISE.invalidArgumentFault(s"Project directory does not exist: ${projectdir}")
    if (!Files.isRegularFile(projectdir.resolve("build.sbt")))
      RAISE.invalidArgumentFault(s"Not an sbt project directory: ${projectdir}")

    val config = CozyProjectYamlConfig.load(projectdir.resolve(".cozy/config.yaml"))
    val savedir = _publication_output(projectdir, args, config)
    val publication = _compile(projectdir, savedir, args, config)
    _write(publication, savedir, projectdir)
  }

  private def _compile(projectdir: Path, savedir: Path, args: List[String], config: CozyProjectYamlConfig.Config): Publication = {
    val buildsbt = Files.readString(projectdir.resolve("build.sbt"), StandardCharsets.UTF_8)
    val publicmetadata = _public_metadata(projectdir)
    val cliname = _value(args, "name")
    val publicname = _metadata_value(publicmetadata, "name")
    val configname = config.value("publication.name")
    val rawname = cliname.orElse(publicname).orElse(configname).orElse(_sbt_setting(buildsbt, "name"))
    val name = rawname match {
      case Some(x) if cliname.nonEmpty || publicname.nonEmpty || configname.nonEmpty => _validate_name(x, "publication name")
      case Some(x) => _slugify(x)
      case None => _slugify(projectdir.getFileName.toString)
    }
    if (name.isEmpty)
      RAISE.invalidArgumentFault("Publication name is empty after slug normalization")
    val title = _value(args, "title").orElse(_metadata_value(publicmetadata, "title")).orElse(config.value("publication.title")).orElse(_sbt_setting(buildsbt, "name")).getOrElse(name)
    val publicationpath = _value(args, "path").orElse(_metadata_value(publicmetadata, "path")).orElse(config.value("publication.path")).map(_validate_publication_path)
    val descriptiveattributes = _descriptive_attributes(projectdir, args, publicmetadata, config)
    val summary = descriptiveattributes.summary.default
    val description = descriptiveattributes.description.default
    val organization = _value(args, "organization").orElse(_sbt_setting(buildsbt, "organization")).getOrElse("")
    val version = _value(args, "version").orElse(_sbt_setting(buildsbt, "version")).getOrElse("")
    val scalaversion = _value(args, "scala-version").orElse(_sbt_setting(buildsbt, "scalaVersion")).getOrElse("")
    val sbtversion = _value(args, "sbt-version").orElse(_sbt_version(projectdir)).getOrElse("")
    val samplesdir = _config_path(projectdir, config.value("publication.samples_dir")).getOrElse(projectdir.resolve("samples"))
    val kind = _value(args, "kind").orElse(_metadata_value(publicmetadata, "kind")).orElse(config.value("publication.kind")).map(_.trim).filter(_.nonEmpty).getOrElse(_detect_kind(projectdir, buildsbt, samplesdir))
    if (!_valid_kinds.contains(kind))
      RAISE.invalidArgumentFault(s"Invalid --kind: ${kind}. Expected one of: ${_valid_kinds.toVector.sorted.mkString(", ")}")
    val sourcemanifestenabled = config.boolean("publication.source_manifest.enabled").getOrElse(true)
    val excludes = _default_excluded_segments ++ config.list("publication.source_manifest.excludes")

    val project = ProjectMetadata(
      name = name,
      title = title,
      kind = kind,
      publicationPath = publicationpath,
      descriptiveAttributes = descriptiveattributes,
      summary = summary,
      description = description,
      organization = organization,
      version = version,
      scalaVersion = scalaversion,
      sbtVersion = sbtversion
    )
    val sourcefiles =
      if (sourcemanifestenabled) _source_manifest(projectdir, savedir, excludes)
      else Vector.empty
    val samples =
      if (kind == "sample-multi")
        _sample_publications(samplesdir, savedir, project.version, excludes)
      else
        Vector.empty

    val repositoryModules = config.list("warehouse.repository_artifacts.modules") match {
      case Vector() => Vector(project.name)
      case xs => xs.toVector
    }

    Publication(project, _publication_pages(publicmetadata), sourcemanifestenabled, sourcefiles, samples, repositoryModules)
  }

  private def _publication_pages(metadata: CozyProjectYamlConfig.Config): Vector[PublicationPage] =
    metadata.publicationPageJsons.flatMap { json =>
      val path = json.hcursor.downField("path").as[String].toOption.map(_.trim).filter(_.nonEmpty)
      path.map { p =>
        PublicationPage(
          path = _validate_publication_path(p),
          title = DescriptiveAttributes.textFromJson(json, "title"),
          descriptiveAttributes = DescriptiveAttributes.fromJson(json)
        )
      }
    }

  def unpublish(args: List[String]): Unit = {
    val savedir = _required_path(args, "save")
    val name = _value(args, "name").map(_validate_name(_, "publication name")).getOrElse(RAISE.invalidArgumentFault("Missing --name"))
    PublicationRegistry.remove(savedir, name)
  }

  def publishMavenRepository(args: List[String]): Unit = {
    val repositorydir = _project_dir(args)
    if (!Files.isDirectory(repositorydir))
      RAISE.invalidArgumentFault(s"Maven repository directory does not exist: ${repositorydir}")
    val savedir = _required_path(args, "save")
    val name = _value(args, "name").map(_validate_name(_, "publication name")).getOrElse("maven-repository")
    val title = _value(args, "title").getOrElse("Maven Repository")
    val publicationpath = _value(args, "path").map(_validate_publication_path)
    val coordinates = _value(args, "maven-coordinates")
    val metadata = Json.obj(
      "schema" -> _schema,
      "type" -> "project-metadata",
      "project" -> Json.obj(
        "name" -> name,
        "title" -> title,
        "kind" -> "maven-repository"
      ),
      "publication" -> (Json.obj() ++ publicationpath.map(x => Json.obj("path" -> x)).getOrElse(Json.obj()))
    )
    PublicationRegistry.publishMetadata(
      root = savedir,
      name = name,
      publicationPath = publicationpath,
      projectdir = repositorydir,
      entries = Vector(s"metadata/projects/${name}/metadata.json" -> metadata)
    )
    CozyWarehouseIndexer.publishMaven(
      warehouseDir = repositorydir,
      savedir = savedir,
      name = name,
      title = title,
      coordinates = coordinates
    )
  }

  def registerMetadata(root: Path, name: String, entries: Vector[(String, JsValue)]): Unit =
    PublicationRegistry.registerMetadata(root, name, entries.map {
      case (path, json) => PublicationBundleEntry(path, _publication_bundle_key(path), json)
    })

  private def _write(publication: Publication, savedir: Path, projectdir: Path): Unit = {
    val name = publication.project.name
    _delete_legacy_placeholder(savedir.resolve(s"repository/artifacts/${name}"))
    _delete_legacy_placeholder(savedir.resolve(s"maven/artifacts/${name}"))
    _delete_legacy_placeholder(savedir.resolve(s"download/artifacts/${name}"))
    val staging = Files.createTempDirectory("cozy-publication-")
    try {
      _write_publication_files(publication, staging)
      PublicationRegistry.publish(savedir, publication, projectdir, staging)
    } finally {
      _delete_directory(staging)
    }
  }

  private def _write_publication_files(publication: Publication, savedir: Path): Unit = {
    val name = publication.project.name
    _delete_legacy_placeholder(savedir.resolve(s"repository/artifacts/${name}"))
    _delete_legacy_placeholder(savedir.resolve(s"maven/artifacts/${name}"))
    _delete_legacy_placeholder(savedir.resolve(s"download/artifacts/${name}"))
    val metadatadir = savedir.resolve("metadata")
    _write_pair(metadatadir.resolve(s"catalog/projects/${name}"), _catalog_project_yaml(publication), _catalog_project_json(publication))
    _write_pair(metadatadir.resolve(s"catalog/samples/${name}"), _catalog_sample_yaml(publication), _catalog_sample_json(publication))
    _write_pair(metadatadir.resolve(s"projects/${name}/metadata"), _project_metadata_yaml(publication), _project_metadata_json(publication))
    _write_pair(metadatadir.resolve(s"samples/${name}/metadata"), _sample_metadata_yaml(publication), _sample_metadata_json(publication))
    _write_publication_pages(publication, metadatadir)
    publication.samples.foreach(_write_sample(publication.project, metadatadir, _))
    _write_repository_artifact(publication, metadatadir)
    _write_download_artifact(publication, metadatadir)
    if (publication.sourceManifestEnabled)
      _write_pair(metadatadir.resolve(s"source-manifest/${name}"), _source_manifest_yaml(publication), _source_manifest_json(publication))
  }

  private def _delete_legacy_placeholder(base: Path): Unit = {
    val yaml = Paths.get(base.toString + ".yaml")
    val json = Paths.get(base.toString + ".json")
    if (_is_legacy_placeholder(yaml) || _is_legacy_placeholder(json)) {
      Files.deleteIfExists(yaml)
      Files.deleteIfExists(json)
    }
  }

  private def _is_legacy_placeholder(path: Path): Boolean =
    Files.isRegularFile(path) && Files.readString(path, StandardCharsets.UTF_8).contains("placeholder")

  private def _write_pair(base: Path, yaml: String, json: JsValue): Unit = {
    _write_text(Paths.get(base.toString + ".yaml"), yaml)
    _write_text(Paths.get(base.toString + ".json"), Json.prettyPrint(json) + "\n")
  }

  private def _catalog_project_yaml(p: Publication): String =
    _yaml_header("catalog-project") +
      s"""project:
         |  name: ${_yaml_string(p.project.name)}
         |  title: ${_yaml_string(p.project.title)}
         |  kind: ${_yaml_string(p.project.kind)}
         |  metadata: ${_yaml_string(s"metadata/projects/${p.project.name}/metadata")}
         |""".stripMargin

  private def _catalog_project_json(p: Publication): JsValue =
    Json.obj(
      "schema" -> _schema,
      "type" -> "catalog-project",
      "project" -> Json.obj(
        "name" -> p.project.name,
        "title" -> p.project.title,
        "kind" -> p.project.kind,
        "metadata" -> s"metadata/projects/${p.project.name}/metadata"
      )
    )

  private def _catalog_sample_yaml(p: Publication): String =
    _yaml_header("catalog-sample") +
      s"""sample:
         |  name: ${_yaml_string(p.project.name)}
         |  title: ${_yaml_string(p.project.title)}
         |  kind: ${_yaml_string(p.project.kind)}
         |  metadata: ${_yaml_string(s"metadata/samples/${p.project.name}/metadata")}
         |${_catalog_sample_download_yaml(p)}""".stripMargin

  private def _catalog_sample_json(p: Publication): JsValue = {
    val sample = Json.obj(
      "name" -> p.project.name,
      "title" -> p.project.title,
      "kind" -> p.project.kind,
      "metadata" -> s"metadata/samples/${p.project.name}/metadata"
    )
    Json.obj(
      "schema" -> _schema,
      "type" -> "catalog-sample",
      "sample" -> (if (p.samples.nonEmpty) sample + ("download" -> _sample_collection_download_json(p.project)) else sample)
    )
  }

  private def _project_metadata_yaml(p: Publication): String =
    _yaml_header("project-metadata") +
      _project_yaml(p.project) +
      _publication_yaml(p)

  private def _project_metadata_json(p: Publication): JsValue =
    Json.obj(
      "schema" -> _schema,
      "type" -> "project-metadata",
      "project" -> _project_json(p.project),
      "publication" -> _publication_json(p)
    )

  private def _sample_metadata_yaml(p: Publication): String =
    _yaml_header("sample-metadata") +
      _project_yaml(p.project) +
      _publication_yaml(p) +
      _sample_collection_download_yaml(p) +
      _sample_refs_yaml(p)

  private def _sample_metadata_json(p: Publication): JsValue = {
    val base = Json.obj(
      "schema" -> _schema,
      "type" -> "sample-metadata",
      "project" -> _project_json(p.project),
      "publication" -> _publication_json(p),
      "samples" -> JsArray(p.samples.map(sample => _sample_ref_json(p.project, sample)))
    )
    if (p.samples.nonEmpty)
      base + ("download" -> _sample_collection_download_json(p.project))
    else
      base
  }

  private def _catalog_sample_download_yaml(p: Publication): String =
    if (p.samples.nonEmpty)
      s"""  download:
         |    artifact: ${_yaml_string(s"metadata/artifacts/download/${p.project.name}")}
         |    types:
         |      - "sample-collection-zip"
         |      - "sample-zip"
         |""".stripMargin
    else
      ""

  private def _sample_collection_download_yaml(p: Publication): String =
    if (p.samples.nonEmpty)
      s"""download:
         |  artifact: ${_yaml_string(s"metadata/artifacts/download/${p.project.name}")}
         |  types:
         |    - "sample-collection-zip"
         |    - "sample-zip"
         |""".stripMargin
    else
      ""

  private def _sample_collection_download_json(project: ProjectMetadata): JsObject =
    Json.obj(
      "artifact" -> s"metadata/artifacts/download/${project.name}",
      "types" -> Json.arr("sample-collection-zip", "sample-zip")
    )

  private def _publication_pages_yaml(p: Publication): String =
    _yaml_header("publication-pages") +
      s"""publication:
         |  name: ${_yaml_string(p.project.name)}
         |  title: ${_yaml_string(p.project.title)}
         |${p.project.publicationPath.map(x => s"  path: ${_yaml_string(x)}\n").getOrElse("")}pages:
         |${p.pages.map(_publication_page_yaml).mkString}""".stripMargin

  private def _publication_page_yaml(p: PublicationPage): String =
    s"""  - path: ${_yaml_string(p.path)}
       |${_text_yaml("title", p.title, 4)}${_descriptive_yaml(p.descriptiveAttributes, 4)}""".stripMargin

  private def _publication_pages_json(p: Publication): JsValue =
    Json.obj(
      "schema" -> _schema,
      "type" -> "publication-pages",
      "publication" -> (Json.obj(
        "name" -> p.project.name,
        "title" -> p.project.title
      ) ++ p.project.publicationPath.map(x => Json.obj("path" -> x)).getOrElse(Json.obj())),
      "pages" -> JsArray(p.pages.map(_publication_page_json))
    )

  private def _publication_page_json(p: PublicationPage): JsObject =
    Json.obj("path" -> p.path) ++
      _text_json("title", p.title) ++
      _descriptive_json(p.descriptiveAttributes)

  private def _sample_refs_yaml(p: Publication): String =
    if (p.samples.nonEmpty)
      s"""samples:
         |${p.samples.map(sample => _sample_ref_yaml(p.project, sample)).mkString}""".stripMargin
    else
      ""

  private def _write_repository_artifact(publication: Publication, savedir: Path): Unit =
    publication.project.kind match {
      case "car" | "sar" =>
        _write_pair(
          savedir.resolve(s"artifacts/repository/${publication.project.name}"),
          _repository_artifact_yaml(publication),
          _repository_artifact_json(publication)
        )
      case _ =>
        Unit
    }

  private def _write_download_artifact(publication: Publication, savedir: Path): Unit =
    if (publication.samples.nonEmpty)
      _write_pair(
        savedir.resolve(s"artifacts/download/${publication.project.name}"),
        _download_artifact_yaml(publication),
        _download_artifact_json(publication)
      )

  private def _write_publication_pages(publication: Publication, savedir: Path): Unit =
    if (publication.pages.nonEmpty)
      _write_pair(
        savedir.resolve(s"publication-pages/${publication.project.name}"),
        _publication_pages_yaml(publication),
        _publication_pages_json(publication)
      )

  private def _repository_artifact_yaml(p: Publication): String =
    _yaml_header("repository-artifact") +
      _project_yaml(p.project) +
      s"""artifact:
         |  layer: "repository"
         |  status: "planned"
         |  kinds:
         |${_repository_kind_yaml(p)}
         |  files:
         |${_repository_files(p).map(_artifact_file_yaml).mkString}""".stripMargin

  private def _repository_artifact_json(p: Publication): JsValue =
    Json.obj(
      "schema" -> _schema,
      "type" -> "repository-artifact",
      "project" -> _project_json(p.project),
      "artifact" -> Json.obj(
        "layer" -> "repository",
        "status" -> "planned",
        "kinds" -> Json.arr(Json.obj(
          "type" -> p.project.kind,
          "versions" -> Json.arr(_artifact_version(p.project.version)),
          "latestRelease" -> _artifact_version(p.project.version)
        )),
        "files" -> JsArray(_repository_files(p).map(_artifact_file_json))
      )
    )

  private def _download_artifact_yaml(p: Publication): String =
    _yaml_header("download-artifact") +
      _project_yaml(p.project) +
      {
        val files = _sample_collection_zip_file(p.project) +: p.samples.map(sample => _sample_zip_file(p.project, sample))
      s"""artifact:
         |  layer: "download"
         |  status: "planned"
         |  kinds:
         |    - type: "sample-collection-zip"
         |      latest_release: ${_yaml_string(_artifact_version(p.project.version))}
         |      versions: [${_yaml_string(_artifact_version(p.project.version))}]
         |    - type: "sample-zip"
         |      latest_release: ${_yaml_string(_artifact_version(p.project.version))}
         |      versions: [${p.samples.map(_.version).distinct.sorted.map(_yaml_string).mkString(", ")}]
         |  files:
         |${files.map(_artifact_file_yaml).mkString}""".stripMargin
      }

  private def _download_artifact_json(p: Publication): JsValue =
    {
      val files = _sample_collection_zip_file(p.project) +: p.samples.map(sample => _sample_zip_file(p.project, sample))
    Json.obj(
      "schema" -> _schema,
      "type" -> "download-artifact",
      "project" -> _project_json(p.project),
      "artifact" -> Json.obj(
        "layer" -> "download",
        "status" -> "planned",
        "kinds" -> Json.arr(
          Json.obj(
            "type" -> "sample-collection-zip",
            "versions" -> Json.arr(_artifact_version(p.project.version)),
            "latestRelease" -> _artifact_version(p.project.version)
          ),
          Json.obj(
            "type" -> "sample-zip",
            "versions" -> p.samples.map(_.version).distinct.sorted,
            "latestRelease" -> _artifact_version(p.project.version)
          )
        ),
        "files" -> JsArray(files.map(_artifact_file_json))
      )
    )
    }

  private def _repository_kind_yaml(p: Publication): String =
    s"""    - type: ${_yaml_string(p.project.kind)}
       |      latest_release: ${_yaml_string(_artifact_version(p.project.version))}
       |      versions: [${_yaml_string(_artifact_version(p.project.version))}]
       |""".stripMargin

  private def _write_sample(project: ProjectMetadata, metadatadir: Path, sample: SamplePublication): Unit = {
    val itembase = metadatadir.resolve(s"samples/${project.name}/items/${sample.name}/${sample.version}")
    _write_pair(itembase.resolve("metadata"), _sample_item_yaml(project, sample), _sample_item_json(project, sample))
    _copy_sample_files(itembase.resolve("files"), sample)
    _write_text(metadatadir.resolve(s"samples/${project.name}/items/${sample.name}/latest.json"), Json.prettyPrint(Json.obj(
      "schema" -> _schema,
      "type" -> "sample-latest",
      "project" -> _project_json(project),
      "sample" -> _sample_json(sample),
      "latest" -> Json.obj(
        "version" -> sample.version,
        "metadata" -> s"metadata/samples/${project.name}/items/${sample.name}/${sample.version}/metadata"
      )
    )) + "\n")
  }

  private def _sample_ref_yaml(project: ProjectMetadata, p: SamplePublication): String =
    s"""    - name: ${_yaml_string(p.name)}
       |      title: ${_yaml_string(p.title)}
       |${_descriptive_yaml(p.descriptiveAttributes, 6)}      version: ${_yaml_string(p.version)}
       |      directory: ${_yaml_string(p.directory)}
       |      metadata: ${_yaml_string(s"metadata/samples/${project.name}/items/${p.name}/${p.version}/metadata")}
       |""".stripMargin

  private def _sample_ref_json(project: ProjectMetadata, p: SamplePublication): JsValue =
    Json.obj(
      "name" -> p.name,
      "title" -> p.title,
      "version" -> p.version,
      "directory" -> p.directory,
      "metadata" -> s"metadata/samples/${project.name}/items/${p.name}/${p.version}/metadata"
    ) ++ _descriptive_json(p.descriptiveAttributes)

  private def _sample_item_yaml(project: ProjectMetadata, sample: SamplePublication): String =
    _yaml_header("sample-item") +
      _project_yaml(project) +
      s"""sample:
         |  name: ${_yaml_string(sample.name)}
         |  title: ${_yaml_string(sample.title)}
         |${_descriptive_yaml(sample.descriptiveAttributes, 2)}  version: ${_yaml_string(sample.version)}
         |  directory: ${_yaml_string(sample.directory)}
         |  files_path: ${_yaml_string(s"metadata/samples/${project.name}/items/${sample.name}/${sample.version}/files")}
         |  download:
         |    artifact: ${_yaml_string(s"metadata/artifacts/download/${project.name}")}
         |    type: "sample-zip"
         |""".stripMargin

  private def _sample_item_json(project: ProjectMetadata, sample: SamplePublication): JsValue =
    Json.obj(
      "schema" -> _schema,
      "type" -> "sample-item",
      "project" -> _project_json(project),
      "sample" -> (_sample_json(sample) +
        ("filesPath" -> JsString(s"metadata/samples/${project.name}/items/${sample.name}/${sample.version}/files")) +
        ("download" -> _sample_download_json(project, sample)))
    )

  private def _sample_json(p: SamplePublication): JsObject =
    Json.obj(
      "name" -> p.name,
      "title" -> p.title,
      "version" -> p.version,
      "directory" -> p.directory
    ) ++ _descriptive_json(p.descriptiveAttributes)

  private def _sample_download_json(project: ProjectMetadata, sample: SamplePublication): JsObject =
    Json.obj(
      "artifact" -> s"metadata/artifacts/download/${project.name}",
      "type" -> "sample-zip"
    )

  private def _repository_files(p: Publication): Vector[ArtifactFile] =
    p.repositoryModules.map { module =>
      val version = _artifact_version(p.project.version)
      ArtifactFile(
        layer = "repository",
        artifactType = p.project.kind,
        module = Some(module),
        sampleName = None,
        version = version,
        extension = p.project.kind,
        path = s"repository/${p.project.kind}/${module}/${version}/${module}-${version}.${p.project.kind}",
        name = s"${module}-${version}.${p.project.kind}"
      )
    }

  private def _sample_zip_file(project: ProjectMetadata, sample: SamplePublication): ArtifactFile =
    ArtifactFile(
      layer = "download",
      artifactType = "sample-zip",
      module = Some(project.name),
      sampleName = Some(sample.name),
      version = sample.version,
      extension = "zip",
      path = CozyPublicationPaths.sampleDownloadPath(project.name, project.publicationPath, sample.name, sample.version),
      name = s"${sample.name}-${sample.version}.zip"
    )

  private def _sample_collection_zip_file(project: ProjectMetadata): ArtifactFile = {
    val version = _artifact_version(project.version)
    ArtifactFile(
      layer = "download",
      artifactType = "sample-collection-zip",
      module = Some(project.name),
      sampleName = None,
      version = version,
      extension = "zip",
      path = CozyPublicationPaths.collectionDownloadPath(project.name, project.publicationPath, version),
      name = s"${project.name}-${version}.zip"
    )
  }

  private def _artifact_file_yaml(p: ArtifactFile): String =
    s"""    - warehouse_path: ${_yaml_string(p.path)}
       |      public_path: ${_yaml_string(_public_artifact_path(p.path))}
       |      name: ${_yaml_string(p.name)}
       |      version: ${_yaml_string(p.version)}
       |      type: ${_yaml_string(p.artifactType)}
       |      module: ${_yaml_string(p.module.getOrElse(""))}
       |      sample: ${_yaml_string(p.sampleName.getOrElse(""))}
       |      extension: ${_yaml_string(p.extension)}
       |      expected: true
       |""".stripMargin

  private def _artifact_file_json(p: ArtifactFile): JsValue =
    Json.obj(
      "layer" -> p.layer,
      "type" -> p.artifactType,
      "module" -> p.module,
      "artifactId" -> p.module,
      "sampleName" -> p.sampleName,
      "version" -> p.version,
      "extension" -> p.extension,
      "warehousePath" -> p.path,
      "publicPath" -> _public_artifact_path(p.path),
      "name" -> p.name,
      "expected" -> true
    )

  private def _public_artifact_path(warehousePath: String): String =
    if (warehousePath.startsWith("maven/"))
      s"repository/${warehousePath}"
    else if (warehousePath.startsWith("repository/"))
      warehousePath
    else if (warehousePath.startsWith("download/"))
      s"repository/${warehousePath}"
    else
      warehousePath

  private def _artifact_version(version: String): String =
    Option(version).map(_.trim).filter(_.nonEmpty).getOrElse("0.0.0-SNAPSHOT")

  private def _source_manifest_yaml(p: Publication): String =
    _yaml_header("source-manifest") +
      _project_yaml(p.project) +
      s"""files:
         |${p.sourcefiles.map(_source_file_yaml).mkString}""".stripMargin

  private def _source_manifest_json(p: Publication): JsValue =
    Json.obj(
      "schema" -> _schema,
      "type" -> "source-manifest",
      "project" -> _project_json(p.project),
      "files" -> JsArray(p.sourcefiles.map(_source_file_json))
    )

  private def _yaml_header(kind: String): String =
    s"""schema: ${_yaml_string(_schema)}
       |type: ${_yaml_string(kind)}
       |""".stripMargin

  private def _project_yaml(p: ProjectMetadata): String =
    s"""project:
       |  name: ${_yaml_string(p.name)}
         |  title: ${_yaml_string(p.title)}
         |  kind: ${_yaml_string(p.kind)}
         |${_descriptive_yaml(p.descriptiveAttributes)}
         |  organization: ${_yaml_string(p.organization)}
       |  version: ${_yaml_string(p.version)}
       |  scala_version: ${_yaml_string(p.scalaVersion)}
       |  sbt_version: ${_yaml_string(p.sbtVersion)}
       |""".stripMargin

  private def _publication_yaml(p: Publication): String = {
    val source = if (p.sourceManifestEnabled) s"  source_manifest: metadata/source-manifest/${p.project.name}\n" else ""
    val path = p.project.publicationPath.map(x => s"  path: ${_yaml_string(x)}\n").getOrElse("")
    s"""publication:
       |${source}${path}""".stripMargin
  }

  private def _publication_json(p: Publication): JsValue = {
    val source =
      if (p.sourceManifestEnabled) Json.obj("sourceManifest" -> s"metadata/source-manifest/${p.project.name}")
      else Json.obj()
    p.project.publicationPath match {
      case Some(path) => source + ("path" -> JsString(path))
      case None => source
    }
  }

  private def _source_file_yaml(p: SourceFile): String =
    s"""  - path: ${_yaml_string(p.path)}
       |    size: ${p.size}
       |    sha256: ${_yaml_string(p.sha256)}
       |""".stripMargin

  private def _source_file_json(p: SourceFile): JsValue =
    Json.obj(
      "path" -> p.path,
      "size" -> p.size,
      "sha256" -> p.sha256
    )

  private def _project_json(p: ProjectMetadata): JsValue =
    Json.obj(
      "name" -> p.name,
      "title" -> p.title,
      "kind" -> p.kind,
      "organization" -> p.organization,
      "version" -> p.version,
      "scalaVersion" -> p.scalaVersion,
      "sbtVersion" -> p.sbtVersion
    ) ++ _descriptive_json(p.descriptiveAttributes)

  private def _descriptive_yaml(p: DescriptiveAttributes, indent: Int = 2): String =
    DescriptiveAttributes.Fields.map(name => _text_yaml(name, p.field(name), indent)).mkString

  private def _text_yaml(name: String, text: DescriptiveAttributes.Text, indent: Int): String = {
    val sp = " " * indent
    val default = text.default.map(x => s"${sp}${name}: ${_yaml_string(x)}\n").getOrElse("")
    val i18n =
      if (text.i18n.isEmpty)
        ""
      else
        s"${sp}${name}_i18n:\n" + text.i18n.toVector.sortBy(_._1).map {
          case (k, v) => s"${sp}  ${k}: ${_yaml_string(v)}\n"
        }.mkString
    default + i18n
  }

  private def _descriptive_json(p: DescriptiveAttributes): JsObject =
    DescriptiveAttributes.Fields.foldLeft(Json.obj()) { (z, name) =>
      z ++ _text_json(name, p.field(name))
    }

  private def _text_json(name: String, text: DescriptiveAttributes.Text): JsObject = {
    val default = text.default.map(x => Json.obj(name -> x)).getOrElse(Json.obj())
    val i18n =
      if (text.i18n.isEmpty)
        Json.obj()
      else
        Json.obj(s"${name}_i18n" -> JsObject(text.i18n.toVector.sortBy(_._1).map {
          case (k, v) => k -> JsString(v)
        }))
    default ++ i18n
  }

  private def _source_manifest(projectdir: Path, savedir: Path, excludes: Set[String]): Vector[SourceFile] = {
    val save = savedir.toAbsolutePath.normalize()
    val stream = Files.walk(projectdir)
    try {
      stream.iterator().asScala.toVector.collect {
        case p if Files.isRegularFile(p) && !_excluded(projectdir, p, save, excludes) =>
          val rel = projectdir.relativize(p).toString.replace('\\', '/')
          SourceFile(rel, Files.size(p), _sha256(p))
      }.sortBy(_.path)
    } finally {
      stream.close()
    }
  }

  private def _sample_publications(samplesdir: Path, savedir: Path, projectversion: String, excludes: Set[String]): Vector[SamplePublication] =
    _validate_unique_sample_names(_sample_dirs(samplesdir).map(dir => dir -> _validate_name(_slugify(dir.getFileName.toString), "sample name"))).map { case (dir, name) =>
      val buildsbt = Files.readString(dir.resolve("build.sbt"), StandardCharsets.UTF_8)
      val metadata = _public_metadata(dir)
      val rawName = dir.getFileName.toString
      val title = _metadata_value(metadata, "title").orElse(_sbt_setting(buildsbt, "name")).getOrElse(rawName)
      val version = Option(projectversion).map(_.trim).filter(_.nonEmpty).getOrElse("0.0.0-SNAPSHOT")
      val descriptiveattributes = metadata.descriptiveAttributes.
        orElse(_descriptive_attributes(_readme_summary(dir), _readme_description(dir)))
      SamplePublication(
        name = name,
        title = title,
        descriptiveAttributes = descriptiveattributes,
        summary = descriptiveattributes.summary.default,
        description = descriptiveattributes.description.default,
        directory = samplesdir.relativize(dir).toString.replace('\\', '/'),
        version = version,
        root = dir,
        files = _source_manifest(dir, savedir, excludes)
      )
    }

  private def _validate_unique_sample_names(samples: Vector[(Path, String)]): Vector[(Path, String)] = {
    val duplicates = samples.groupBy(_._2).collect {
      case (name, xs) if xs.size > 1 =>
        s"${name}: ${xs.map(_._1.getFileName.toString).sorted.mkString(", ")}"
    }.toVector.sorted
    if (duplicates.nonEmpty)
      RAISE.invalidArgumentFault(s"Duplicate sample slug(s): ${duplicates.mkString("; ")}")
    samples
  }

  private def _sample_dirs(samplesdir: Path): Vector[Path] =
    if (!Files.isDirectory(samplesdir))
      Vector.empty
    else
      Option(samplesdir.toFile.listFiles()).toVector.flatten.
        filter(f => f.isDirectory && new java.io.File(f, "build.sbt").isFile).
        map(_.toPath.toAbsolutePath.normalize()).
        sortBy(_.getFileName.toString)

  private def _copy_sample_files(targetDir: Path, sample: SamplePublication): Unit = {
    _delete_directory(targetDir)
    sample.files.foreach { f =>
      val source = sample.root.resolve(f.path)
      val target = targetDir.resolve(f.path)
      Option(target.getParent).foreach(Files.createDirectories(_))
      Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING)
    }
  }

  private def _excluded(projectdir: Path, path: Path, savedir: Path, excludes: Set[String]): Boolean = {
    val abs = path.toAbsolutePath.normalize()
    val rel = projectdir.relativize(path).toString.replace('\\', '/')
    val segments = rel.split('/').toVector
    val normalizedExcludes = excludes.map(_.trim.stripPrefix("/").stripSuffix("/")).filter(_.nonEmpty)
    val excludedByName = normalizedExcludes.exists(x => !x.contains("/") && segments.contains(x))
    val excludedByPath = normalizedExcludes.exists(x => x.contains("/") && (rel == x || rel.startsWith(x + "/")))
    excludedByName || excludedByPath || abs.startsWith(savedir)
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

  private def _delete_directory(path: Path): Unit =
    if (Files.exists(path)) {
      val stream = Files.walk(path)
      try {
        stream.iterator().asScala.toVector.reverse.foreach(Files.deleteIfExists)
      } finally {
        stream.close()
      }
    }

  private def _detect_kind(projectdir: Path, buildsbt: String, samplesdir: Path): String = {
    val childbuilds = Option(projectdir.toFile.listFiles()).toVector.flatten.count(f => f.isDirectory && new java.io.File(f, "build.sbt").isFile)
    val samplebuilds = Option(samplesdir.toFile.listFiles()).toVector.flatten.count(f => f.isDirectory && new java.io.File(f, "build.sbt").isFile)
    if (_contains_sar_marker(projectdir, buildsbt))
      "sar"
    else if (_contains_car_marker(projectdir, buildsbt))
      "car"
    else if (samplebuilds > 1 || childbuilds > 1 || _project_definition_count(buildsbt) > 1)
      "sample-multi"
    else
      "sample-single"
  }

  private def _contains_sar_marker(projectdir: Path, buildsbt: String): Boolean =
    buildsbt.contains("cozyPackaging := \"sar\"") ||
      Files.isRegularFile(projectdir.resolve("subsystem-descriptor.yaml")) ||
      Files.isRegularFile(projectdir.resolve("subsystem-descriptor.yml"))

  private def _contains_car_marker(projectdir: Path, buildsbt: String): Boolean =
    buildsbt.contains("CozyPlugin") &&
      (buildsbt.contains("cozyPackaging := \"car\"") ||
        Files.isDirectory(projectdir.resolve("src/main/car")) ||
        Files.isDirectory(projectdir.resolve("src/main/cozy")))

  private def _project_definition_count(buildsbt: String): Int =
    "(?m)^\\s*lazy\\s+val\\s+\\w+\\s*=\\s*\\(?project\\b".r.findAllIn(buildsbt).length

  private def _sbt_setting(buildsbt: String, key: String): Option[String] = {
    val pattern = ("""(?m)^\s*(?:ThisBuild\s*/\s*)?""" + java.util.regex.Pattern.quote(key) + """\s*:=\s*"([^"]+)""").r
    pattern.findFirstMatchIn(buildsbt).map(_.group(1).trim).filter(_.nonEmpty)
  }

  private def _sbt_version(projectdir: Path): Option[String] = {
    val path = projectdir.resolve("project/build.properties")
    if (!Files.isRegularFile(path))
      None
    else
      Files.readAllLines(path, StandardCharsets.UTF_8).asScala.collectFirst {
        case line if line.trim.startsWith("sbt.version=") =>
          line.trim.substring("sbt.version=".length).trim
      }.filter(_.nonEmpty)
  }

  private def _public_metadata(projectdir: Path): CozyProjectYamlConfig.Config =
    Vector("project.yaml", "project.yml", "project.json", "project.conf", "project.hocon").
      map(projectdir.resolve).
      find(Files.isRegularFile(_)).
      map(CozyProjectYamlConfig.loadPublic).
      getOrElse(CozyProjectYamlConfig.Config.empty)

  private def _metadata_value(metadata: CozyProjectYamlConfig.Config, key: String): Option[String] =
    metadata.value(key).
      orElse(metadata.value(s"project.${key}")).
      orElse(metadata.value(s"publication.${key}"))

  private def _descriptive_attributes(
    projectdir: Path,
    args: List[String],
    metadata: CozyProjectYamlConfig.Config,
    config: CozyProjectYamlConfig.Config
  ): DescriptiveAttributes = {
    val cli = _descriptive_attributes(_value(args, "summary"), _value(args, "description"))
    val readme = _descriptive_attributes(_readme_summary(projectdir), _readme_description(projectdir))
    val conf = _descriptive_attributes(config.value("publication.summary"), config.value("publication.description"))
    cli.orElse(metadata.descriptiveAttributes).orElse(readme).orElse(conf)
  }

  private def _descriptive_attributes(summary: Option[String], description: Option[String]): DescriptiveAttributes =
    DescriptiveAttributes(
      summary = DescriptiveAttributes.Text(summary),
      description = DescriptiveAttributes.Text(description)
    )

  private def _readme_summary(projectdir: Path): Option[String] =
    _readme_lines(projectdir).find(line => line.nonEmpty && !line.startsWith("#")).map(_trim_sentence)

  private def _readme_description(projectdir: Path): Option[String] =
    _readme_lines(projectdir).filter(line => line.nonEmpty && !line.startsWith("#")).take(3).mkString("\n") match {
      case "" => None
      case x => Some(x)
    }

  private def _readme_lines(projectdir: Path): Vector[String] = {
    val candidates = Vector("README.md", "README.adoc", "README.txt").map(projectdir.resolve)
    candidates.find(Files.isRegularFile(_)) match {
      case Some(path) =>
        Files.readAllLines(path, StandardCharsets.UTF_8).asScala.toVector.map(_.trim)
      case None =>
        Vector.empty
    }
  }

  private def _trim_sentence(value: String): String =
    if (value.length <= 160)
      value
    else
      value.take(157).trim + "..."

  private def _project_dir(args: List[String]): Path =
    _value(args, "project").orElse(_positional_args(args).headOption).
      map(p => Paths.get(p).toAbsolutePath.normalize()).
      getOrElse(RAISE.invalidArgumentFault("Missing project directory for publish-project"))

  private def _required_path(args: List[String], key: String): Path =
    _value(args, key).
      map(p => Paths.get(p).toAbsolutePath.normalize()).
      getOrElse(RAISE.invalidArgumentFault(s"Missing --${key}"))

  private def _publication_output(projectdir: Path, args: List[String], config: CozyProjectYamlConfig.Config): Path =
    _value(args, "save").
      map(p => Paths.get(p).toAbsolutePath.normalize()).
      orElse(_config_path(projectdir, config.value("publication.output"))).
      getOrElse(projectdir.resolve("target/publication").toAbsolutePath.normalize())

  private def _config_path(projectdir: Path, value: Option[String]): Option[Path] =
    value.map { p =>
      val path = Paths.get(p)
      if (path.isAbsolute)
        path.normalize()
      else
        projectdir.resolve(path).toAbsolutePath.normalize()
    }

  private def _positional_args(args: List[String]): Vector[String] = {
    val optionNamesWithValue = Set("project", "save", "kind", "name", "title", "path", "summary", "description", "organization", "version", "scala-version", "sbt-version")
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

  private def _validate_publication_path(value: String): String = {
    CozyPublicationPaths.validatePublicationPath(value)
  }

  private def _validate_name(value: String, label: String): String = {
    val name = value.trim
    _slug_pattern.findFirstIn(name) match {
      case Some(x) if x == name => name
      case _ => RAISE.invalidArgumentFault(s"Invalid ${label}: ${value}. Expected ${_slug_pattern.regex}")
    }
  }

  private def _slugify(value: String): String =
    value.toLowerCase(java.util.Locale.ROOT).
      replaceAll("[^a-z0-9]+", "-").
      stripPrefix("-").
      stripSuffix("-")

  private def _yaml_string(value: String): String =
    "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\""

  private def _optional_yaml(key: String, value: Option[String], indent: Int = 2): String =
    value.map(x => " " * indent + s"${key}: ${_yaml_string(x)}\n").getOrElse("")

  private def _write_text(path: Path, text: String): Unit = {
    Option(path.getParent).foreach(Files.createDirectories(_))
    Files.writeString(path, text, StandardCharsets.UTF_8)
  }

  private final case class PublicationBundleEntry(
    path: String,
    key: String,
    metadata: JsValue
  )
  private final case class PublicationBundle(
    publication: String,
    publicationPath: Option[String],
    sourceRepository: String,
    sourcePath: String,
    sourceCommit: Option[String],
    entries: Vector[PublicationBundleEntry]
  )

  private def _publication_bundle_key(path: String): String = {
    val stripped = path.replaceFirst("""\.[^.]+$""", "")
    if (stripped.startsWith("metadata/"))
      stripped.substring("metadata/".length)
    else
      stripped
  }

  private object PublicationRegistry {
    def publish(root: Path, publication: Publication, projectdir: Path, staging: Path): Unit = {
      val name = publication.project.name
      val bundle = PublicationBundle(
        publication = name,
        publicationPath = publication.project.publicationPath,
        sourceRepository = _source_repository(projectdir),
        sourcePath = _source_path(projectdir),
        sourceCommit = _source_commit(projectdir),
        entries = _bundle_entries(staging)
      )
      _check_collisions(root, bundle)
      _write_bundle(root, bundle)
    }

    def publishMetadata(
      root: Path,
      name: String,
      publicationPath: Option[String],
      projectdir: Path,
      entries: Vector[(String, JsValue)]
    ): Unit = {
      val bundle = PublicationBundle(
        publication = name,
        publicationPath = publicationPath,
        sourceRepository = _source_repository(projectdir),
        sourcePath = _source_path(projectdir),
        sourceCommit = _source_commit(projectdir),
        entries = entries.map {
          case (path, json) => PublicationBundleEntry(path, _publication_bundle_key(path), json)
        }.map(_validate_entry).distinct.sortBy(_.path)
      )
      _check_collisions(root, bundle)
      _write_bundle(root, bundle)
    }

    def registerMetadata(root: Path, name: String, entries: Vector[PublicationBundleEntry]): Unit = {
      val old = _load_bundle(root, name).getOrElse(RAISE.invalidArgumentFault(s"Publication bundle not found: ${name}"))
      val replacepaths = entries.map(_.path).toSet
      val bundle = old.copy(
        entries = (old.entries.filterNot(x => replacepaths.contains(x.path)) ++ entries.map(_validate_entry)).distinct.sortBy(_.path)
      )
      _check_collisions(root, bundle)
      _write_bundle(root, bundle)
    }

    def remove(root: Path, name: String): Unit = {
      val path = _bundle_path(root, name)
      if (!Files.isRegularFile(path))
        RAISE.invalidArgumentFault(s"Publication bundle not found: ${name}")
      Files.deleteIfExists(path)
    }

    private def _load_bundle(root: Path, name: String): Option[PublicationBundle] = {
      val jsonpath = _bundle_path(root, name)
      if (Files.isRegularFile(jsonpath)) {
        val json = Json.parse(Files.readString(jsonpath, StandardCharsets.UTF_8))
        if ((json \ "type").asOpt[String].contains("publication-bundle"))
          Some(PublicationBundle(
            publication = (json \ "publication" \ "name").asOpt[String].getOrElse(name),
            publicationPath = (json \ "publication" \ "path").asOpt[String],
            sourceRepository = (json \ "sourceRepository").asOpt[String].getOrElse(""),
            sourcePath = (json \ "sourcePath").asOpt[String].getOrElse(""),
            sourceCommit = (json \ "sourceCommit").asOpt[String],
            entries = (json \ "entries").asOpt[Vector[JsObject]].getOrElse(Vector.empty).map { entry =>
              PublicationBundleEntry(
                path = _validate_relative_metadata_path((entry \ "path").as[String]),
                key = (entry \ "key").asOpt[String].getOrElse(_logical_key((entry \ "path").as[String])),
                metadata = (entry \ "metadata").as[JsValue]
              )
            }
          ))
        else
          None
      } else {
        None
      }
    }

    private def _all_bundles(root: Path): Vector[PublicationBundle] =
      if (!Files.isDirectory(root))
        Vector.empty
      else {
        val stream = Files.list(root)
        try {
          stream.iterator().asScala.toVector.filter(_.getFileName.toString.endsWith(".json")).flatMap { path =>
            _load_bundle(root, path.getFileName.toString.stripSuffix(".json"))
          }
        } finally {
          stream.close()
        }
      }

    private def _check_collisions(root: Path, bundle: PublicationBundle): Unit = {
      val mine = bundle.entries.map(_.path).toSet
      val collisions = _all_bundles(root).filterNot(_.publication == bundle.publication).flatMap { other =>
        other.entries.map(_.path).filter(mine.contains).map(path => s"${path} (${other.publication})")
      }
      if (collisions.nonEmpty)
        RAISE.invalidArgumentFault(s"Publication registry path collision for ${bundle.publication}: ${collisions.sorted.mkString(", ")}")
      bundle.publicationPath.foreach { path =>
        val pathcollisions = _all_bundles(root).filterNot(_.publication == bundle.publication).filter(_.publicationPath.contains(path)).map(_.publication)
        if (pathcollisions.nonEmpty)
          RAISE.invalidArgumentFault(s"Publication registry publication.path collision for ${bundle.publication}: ${path} (${pathcollisions.sorted.mkString(", ")})")
      }
    }

    private def _write_bundle(root: Path, bundle: PublicationBundle): Unit = {
      Files.createDirectories(root)
      _write_text(_bundle_path(root, bundle.publication), Json.prettyPrint(_bundle_json(bundle)) + "\n")
    }

    private def _bundle_json(p: PublicationBundle): JsValue =
      Json.obj(
        "schema" -> _schema,
        "type" -> "publication-bundle",
        "publication" -> (Json.obj(
          "name" -> p.publication
        ) ++ p.publicationPath.map(x => Json.obj("path" -> x)).getOrElse(Json.obj())),
        "sourceRepository" -> p.sourceRepository,
        "sourcePath" -> p.sourcePath,
        "sourceCommit" -> p.sourceCommit,
        "entries" -> JsArray(p.entries.sortBy(_.path).map(_entry_json))
      )

    private def _entry_json(p: PublicationBundleEntry): JsValue =
      Json.obj(
        "path" -> p.path,
        "key" -> p.key,
        "metadata" -> p.metadata
      )

    private def _bundle_entries(staging: Path): Vector[PublicationBundleEntry] =
      _relative_files(staging).filter(x => x.endsWith(".json") && _is_public_metadata_entry(x)).map { rel =>
        val path = _validate_relative_metadata_path(rel)
        PublicationBundleEntry(
          path = path,
          key = _logical_key(path),
          metadata = Json.parse(Files.readString(staging.resolve(path), StandardCharsets.UTF_8))
        )
      }.sortBy(_.path)

    private def _is_public_metadata_entry(path: String): Boolean =
      path.startsWith("metadata/") && !path.contains("/files/")

    private def _validate_entry(p: PublicationBundleEntry): PublicationBundleEntry =
      p.copy(path = _validate_relative_metadata_path(p.path), key = if (p.key.trim.isEmpty) _logical_key(p.path) else p.key.trim)

    private def _bundle_path(root: Path, name: String): Path =
      root.resolve(s"${name}.json")

    private def _relative_files(root: Path): Vector[String] =
      if (!Files.exists(root))
        Vector.empty
      else {
        val stream = Files.walk(root)
        try {
          stream.iterator().asScala.toVector.filter(Files.isRegularFile(_)).map { path =>
            root.relativize(path).toString.replace('\\', '/')
          }.map(_validate_relative_path).distinct.sorted
        } finally {
          stream.close()
        }
      }

    private def _validate_relative_metadata_path(path: String): String = {
      val rel = _validate_relative_path(path)
      if (!rel.startsWith("metadata/"))
        RAISE.invalidArgumentFault(s"Publication bundle entry must be under metadata/: ${path}")
      rel
    }

    private def _validate_relative_path(path: String): String = {
      val normalized = Paths.get(path).normalize()
      if (normalized.isAbsolute || normalized.startsWith("..") || path.contains("\u0000"))
        RAISE.invalidArgumentFault(s"Invalid publication bundle path: ${path}")
      normalized.toString.replace('\\', '/')
    }

    private def _logical_key(path: String): String = {
      val stripped = path.replaceFirst("""\.[^.]+$""", "")
      if (stripped.startsWith("metadata/"))
        stripped.substring("metadata/".length)
      else
        stripped
    }

    private def _source_repository(projectdir: Path): String =
      _git_toplevel(projectdir).map(_.getFileName.toString).getOrElse(projectdir.getFileName.toString)

    private def _source_path(projectdir: Path): String = {
      val normalized = projectdir.toAbsolutePath.normalize()
      _git_toplevel(projectdir).filter(root => normalized.startsWith(root)).map { root =>
        val relative = root.relativize(normalized).toString.replace('\\', '/')
        if (relative.isEmpty) "." else relative
      }.getOrElse(".")
    }

    private def _source_commit(projectdir: Path): Option[String] =
      Try(Process(Seq("git", "-C", projectdir.toString, "rev-parse", "HEAD")).!!.trim).toOption.filter(_.nonEmpty)

    private def _git_toplevel(projectdir: Path): Option[Path] =
      Try(Process(Seq("git", "-C", projectdir.toString, "rev-parse", "--show-toplevel")).!!.trim).toOption.
        filter(_.nonEmpty).map(x => Paths.get(x).toAbsolutePath.normalize())
  }
}

private object CozySampleDistributor {
  private val _default_excluded_segments = Set(
    "target",
    ".git",
    ".bsp",
    ".bloop",
    ".metals",
    ".idea",
    ".cache",
    ".vscode",
    ".scala-build",
    "car.d",
    "component.d",
    "component-repository.d",
    "tmprepo"
  )
  private val _slug_pattern = "^[a-z0-9][a-z0-9-]*$".r
  final case class PlannedArchive(kind: String, sampleName: Option[String], path: Path)

  def distribute(args: List[String]): Unit = {
    val projectdir = _project_dir(args)
    if (!Files.isDirectory(projectdir))
      RAISE.invalidArgumentFault(s"Project directory does not exist: ${projectdir}")
    val config = CozyProjectYamlConfig.load(projectdir.resolve(".cozy/config.yaml"))
    val projectmetadata = CozyProjectYamlConfig.load(projectdir.resolve("project.yaml"))
    val warehouseDir = _value(args, "warehouse").
      map(p => Paths.get(p).toAbsolutePath.normalize()).
      orElse(_config_path(projectdir, config.value("warehouse.repository"))).
      getOrElse(RAISE.invalidArgumentFault("Missing --warehouse for distribute-samples"))
    val name = _value(args, "name").orElse(config.value("publication.name")).map(_validate_name).getOrElse(RAISE.invalidArgumentFault("Missing --name for distribute-samples"))
    val version = _value(args, "version").orElse(_project_version(projectdir)).getOrElse(RAISE.invalidArgumentFault("Missing --version for distribute-samples"))
    val publicationpath = _value(args, "path").orElse(projectmetadata.value("project.path")).orElse(config.value("publication.path")).map(CozyPublicationPaths.validatePublicationPath)
    val samplesdir = _value(args, "samples-dir").orElse(config.value("publication.samples_dir")).
      map(p => _config_path(projectdir, Some(p)).get).
      getOrElse(projectdir.resolve("samples"))
    val excludes = _default_excluded_segments ++ config.list("publication.source_manifest.excludes")
    val samples = _sample_dirs(samplesdir)
    if (samples.isEmpty)
      RAISE.invalidArgumentFault(s"No sample projects found under: ${samplesdir}")
    val samplePairs = _validate_unique_sample_names(samples.map(sample => sample -> _validate_name(_slugify(sample.getFileName.toString))))
    val archives = _planned_archives(warehouseDir, name, publicationpath, version, samplePairs)
    if (_flag(args, "dry-run")) {
      _print_plan(warehouseDir, archives)
      return
    }
    _zip_sample_collection(samplesdir, archives.head.path, excludes)
    samplePairs.foreach { case (sample, sampleName) =>
      val out = archives.find(_.sampleName.contains(sampleName)).map(_.path).
        getOrElse(warehouseDir.resolve(CozyPublicationPaths.sampleDownloadPath(name, publicationpath, sampleName, version)))
      _zip_dir(sample, out, excludes)
    }
  }

  private def _planned_archives(
    warehouseDir: Path,
    name: String,
    publicationPath: Option[String],
    version: String,
    samples: Vector[(Path, String)]
  ): Vector[PlannedArchive] =
    PlannedArchive(
      "sample-collection-zip",
      None,
      warehouseDir.resolve(CozyPublicationPaths.collectionDownloadPath(name, publicationPath, version))
    ) +: samples.map { case (_, sampleName) =>
      PlannedArchive(
        "sample-zip",
        Some(sampleName),
        warehouseDir.resolve(CozyPublicationPaths.sampleDownloadPath(name, publicationPath, sampleName, version))
      )
    }

  private def _print_plan(warehouseDir: Path, archives: Vector[PlannedArchive]): Unit = {
    println("distribute-samples dry-run")
    archives.foreach { archive =>
      val warehousePath = warehouseDir.relativize(archive.path).toString.replace('\\', '/')
      val sample = archive.sampleName.map(x => s" sample=${x}").getOrElse("")
      println(s"${archive.kind}${sample} warehousePath=${warehousePath} file=${archive.path}")
    }
  }

  private def _zip_sample_collection(samplesdir: Path, out: Path, excludes: Set[String]): Unit =
    _zip_dir(samplesdir, out, excludes)

  private def _zip_dir(sourcedir: Path, out: Path, excludes: Set[String]): Unit = {
    val parent = Option(out.getParent).getOrElse(Paths.get("."))
    Files.createDirectories(parent)
    val tmp = Files.createTempFile(parent, out.getFileName.toString, ".tmp")
    val zip = new ZipOutputStream(Files.newOutputStream(tmp))
    try {
      _sample_files(sourcedir, excludes).foreach { file =>
        val rel = sourcedir.relativize(file).toString.replace('\\', '/')
        val entry = new ZipEntry(rel)
        entry.setTime(0L)
        zip.putNextEntry(entry)
        Files.copy(file, zip)
        zip.closeEntry()
      }
    } finally {
      zip.close()
    }
    _replace_file(tmp, out)
  }

  private def _replace_file(tmp: Path, out: Path): Unit =
    try {
      Files.move(tmp, out, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
    } catch {
      case _: java.nio.file.AtomicMoveNotSupportedException =>
        Files.move(tmp, out, StandardCopyOption.REPLACE_EXISTING)
      case e: Throwable =>
        Files.deleteIfExists(tmp)
        throw e
    }

  private def _sample_files(sampleDir: Path, excludes: Set[String]): Vector[Path] = {
    val stream = Files.walk(sampleDir)
    try {
      stream.iterator().asScala.toVector.collect {
        case p if Files.isRegularFile(p) && !_excluded(sampleDir, p, excludes) => p
      }.sortBy(p => sampleDir.relativize(p).toString.replace('\\', '/'))
    } finally {
      stream.close()
    }
  }

  private def _excluded(root: Path, path: Path, excludes: Set[String]): Boolean = {
    val rel = root.relativize(path).toString.replace('\\', '/')
    val segments = rel.split('/').toVector
    val normalizedExcludes = excludes.map(_.trim.stripPrefix("/").stripSuffix("/")).filter(_.nonEmpty)
    val excludedByName = normalizedExcludes.exists(x => !x.contains("/") && segments.contains(x))
    val excludedByPath = normalizedExcludes.exists(x => x.contains("/") && (rel == x || rel.startsWith(x + "/")))
    excludedByName || excludedByPath
  }

  private def _sample_dirs(samplesdir: Path): Vector[Path] =
    if (!Files.isDirectory(samplesdir))
      Vector.empty
    else
      Option(samplesdir.toFile.listFiles()).toVector.flatten.
        filter(f => f.isDirectory && new java.io.File(f, "build.sbt").isFile).
        map(_.toPath.toAbsolutePath.normalize()).
        sortBy(_.getFileName.toString)

  private def _validate_unique_sample_names(samples: Vector[(Path, String)]): Vector[(Path, String)] = {
    val duplicates = samples.groupBy(_._2).collect {
      case (name, xs) if xs.size > 1 =>
        s"${name}: ${xs.map(_._1.getFileName.toString).sorted.mkString(", ")}"
    }.toVector.sorted
    if (duplicates.nonEmpty)
      RAISE.invalidArgumentFault(s"Duplicate sample slug(s): ${duplicates.mkString("; ")}")
    samples
  }

  private def _project_version(projectdir: Path): Option[String] =
    if (Files.isRegularFile(projectdir.resolve("build.sbt")))
      _sbt_setting(Files.readString(projectdir.resolve("build.sbt"), StandardCharsets.UTF_8), "version")
    else
      None

  private def _sbt_setting(buildsbt: String, key: String): Option[String] = {
    val pattern = ("""(?m)^\s*(?:ThisBuild\s*/\s*)?""" + java.util.regex.Pattern.quote(key) + """\s*:=\s*"([^"]+)""").r
    pattern.findFirstMatchIn(buildsbt).map(_.group(1).trim).filter(_.nonEmpty)
  }

  private def _project_dir(args: List[String]): Path =
    _value(args, "project").orElse(_positional_args(args).headOption).
      map(p => Paths.get(p).toAbsolutePath.normalize()).
      getOrElse(RAISE.invalidArgumentFault("Missing project directory for distribute-samples"))

  private def _positional_args(args: List[String]): Vector[String] = {
    val optionNamesWithValue = Set("project", "warehouse", "name", "path", "version", "samples-dir")
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

  private def _flag(args: List[String], key: String): Boolean =
    args.exists(_ == s"--${key}") ||
      _value(args, key).exists(x => x.equalsIgnoreCase("true") || x == "1" || x.equalsIgnoreCase("yes"))

  private def _config_path(projectdir: Path, value: Option[String]): Option[Path] =
    value.map { p =>
      val path = Paths.get(p)
      if (path.isAbsolute)
        path.normalize()
      else
        projectdir.resolve(path).toAbsolutePath.normalize()
    }

  private def _validate_name(value: String): String = {
    val name = value.trim
    _slug_pattern.findFirstIn(name) match {
      case Some(x) if x == name => name
      case _ => RAISE.invalidArgumentFault(s"Invalid name: ${value}. Expected ${_slug_pattern.regex}")
    }
  }

  private def _slugify(value: String): String =
    value.toLowerCase(java.util.Locale.ROOT).
      replaceAll("[^a-z0-9]+", "-").
      stripPrefix("-").
      stripSuffix("-")
}

private object CozyWarehouseIndexer {
  private val _schema = "cozy.publish-project.v1"
  private val _slug_pattern = "^[a-z0-9][a-z0-9-]*$".r
  private val _checksum_extensions = Set("sha1", "md5")
  private val _artifact_extensions = Set("jar", "pom", "car", "sar", "zip")

  final case class MavenCoordinate(groupId: String, artifactId: String) {
    def path: String = groupId.replace('.', '/') + "/" + artifactId
    def key: String = s"${groupId}:${artifactId}"
  }
  final case class IndexedFile(
    layer: String,
    artifactType: String,
    groupId: Option[String],
    artifactId: Option[String],
    sampleName: Option[String],
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
  final case class DownloadArtifact(kind: String, versions: Vector[String], files: Vector[IndexedFile])
  final case class IndexResult(name: String, title: String, maven: Vector[MavenArtifact], repository: Vector[RepositoryArtifact], download: Vector[DownloadArtifact])

  def index(args: List[String]): Unit = {
    val warehouseDir = _warehouse_dir(args)
    if (!Files.isDirectory(warehouseDir))
      RAISE.invalidArgumentFault(s"Warehouse directory does not exist: ${warehouseDir}")
    val savedir = _required_path(args, "save")
    val name = _value(args, "name").map(_validate_name).getOrElse(RAISE.invalidArgumentFault("Missing --name"))
    val title = _value(args, "title").getOrElse(name)
    val repositoryKinds = _csv(args, "repository-artifacts").map(_.toLowerCase(java.util.Locale.ROOT)).filter(_.nonEmpty)
    val repositoryModules = _csv(args, "repository-modules").filter(_.nonEmpty) match {
      case Vector() => Vector(name)
      case xs => xs
    }
    val downloadSamples = _csv(args, "download-samples").filter(_.nonEmpty) match {
      case Vector() => Vector(name)
      case xs => xs
    }
    val downloadpublicationpaths = downloadSamples.map(publication => publication -> _publication_path(savedir, publication)).toMap
    val result = IndexResult(
      name = name,
      title = title,
      maven = Vector.empty,
      repository = Vector.empty,
      download = Vector(_index_download_samples(warehouseDir, downloadSamples, downloadpublicationpaths))
    )
    _check_repository_consistency(warehouseDir, savedir, name, repositoryKinds, repositoryModules)
    _check_download_consistency(warehouseDir, savedir, name, downloadSamples, downloadpublicationpaths)
    _write_release(result, savedir)
  }

  def publishMaven(
    warehouseDir: Path,
    savedir: Path,
    name: String,
    title: String,
    coordinates: Option[String]
  ): Unit = {
    if (!Files.isDirectory(warehouseDir))
      RAISE.invalidArgumentFault(s"Warehouse directory does not exist: ${warehouseDir}")
    val configured = _csv(coordinates).map(_coordinate)
    val coordinateList = if (configured.nonEmpty) configured else _discover_maven_coordinates(warehouseDir)
    val result = IndexResult(
      name = name,
      title = title,
      maven = coordinateList.map(_index_maven(warehouseDir, _)),
      repository = Vector.empty,
      download = Vector.empty
    )
    _write_maven(result, savedir)
  }

  private def _write_release(p: IndexResult, savedir: Path): Unit =
    CozyPublicationCompiler.registerMetadata(savedir, p.name, Vector(
      s"metadata/releases/${p.name}.json" -> _release_json(p)
    ))

  private def _write_maven(p: IndexResult, savedir: Path): Unit =
    CozyPublicationCompiler.registerMetadata(savedir, p.name, Vector(
      s"metadata/artifacts/maven/${p.name}.json" -> _maven_json(p),
      s"metadata/releases/${p.name}.json" -> _release_json(p)
    ))

  private def _check_repository_consistency(
    warehouseDir: Path,
    savedir: Path,
    name: String,
    repositoryKinds: Vector[String],
    repositoryModules: Vector[String]
  ): Unit = {
    _expected_paths(savedir, name, s"metadata/artifacts/repository/${name}.json") match {
      case xs if xs.nonEmpty =>
        _check_paths_exist(warehouseDir, "repository", xs)
      case _ =>
        val expected = for {
          kind <- repositoryKinds
          module <- repositoryModules
        } yield warehouseDir.resolve("repository").resolve(kind).resolve(module)
        val existing = expected.exists(Files.exists(_))
        if (existing)
          RAISE.invalidArgumentFault(s"Warehouse repository artifacts exist for ${name}, but publication registry metadata/artifacts/repository/${name}.json is missing")
    }
  }

  private def _check_download_consistency(
    warehouseDir: Path,
    savedir: Path,
    name: String,
    downloadSamples: Vector[String],
    publicationPaths: Map[String, Option[String]]
  ): Unit = {
    _expected_paths(savedir, name, s"metadata/artifacts/download/${name}.json") match {
      case xs if xs.nonEmpty =>
        _check_paths_exist(warehouseDir, "download", xs)
      case _ =>
        val existing = downloadSamples.exists { publication =>
          _download_scan_bases(publication, publicationPaths.getOrElse(publication, None)).exists { base =>
            Files.exists(warehouseDir.resolve("repository/download").resolve(base)) ||
              Files.exists(warehouseDir.resolve("download").resolve(base))
          }
        }
        if (existing)
          RAISE.invalidArgumentFault(s"Warehouse download artifacts exist for ${name}, but publication registry metadata/artifacts/download/${name}.json is missing")
    }
  }

  private def _expected_paths(savedir: Path, name: String, path: String): Vector[String] =
    _bundle_entry(savedir, name, path) match {
      case None => Vector.empty
      case Some(json) =>
      (json \ "artifact" \ "files").asOpt[Vector[JsObject]].toVector.flatten.flatMap { x =>
        (x \ "warehousePath").asOpt[String].orElse((x \ "path").asOpt[String])
      }.filter(_.nonEmpty).distinct.sorted
    }

  private def _publication_path(savedir: Path, publication: String): Option[String] = {
    Vector(
      s"metadata/samples/${publication}/metadata.json",
      s"metadata/projects/${publication}/metadata.json"
    ).flatMap(path => _bundle_entry(savedir, publication, path)).
      flatMap(json => (json \ "publication" \ "path").asOpt[String]).
      headOption.map(CozyPublicationPaths.validatePublicationPath)
  }

  private def _bundle_entry(savedir: Path, name: String, path: String): Option[JsValue] = {
    val bundle = savedir.resolve(s"${name}.json")
    if (!Files.isRegularFile(bundle))
      None
    else {
      val json = Json.parse(Files.readString(bundle, StandardCharsets.UTF_8))
      (json \ "entries").asOpt[Vector[JsObject]].getOrElse(Vector.empty).collectFirst {
        case entry if (entry \ "path").asOpt[String].contains(path) => (entry \ "metadata").as[JsValue]
      }
    }
  }

  private def _check_paths_exist(warehouseDir: Path, layer: String, paths: Vector[String]): Unit = {
    val missing = paths.filterNot(path => Files.isRegularFile(warehouseDir.resolve(path)))
    if (missing.nonEmpty)
      RAISE.invalidArgumentFault(s"Missing ${layer} artifact(s) in warehouse: ${missing.mkString(", ")}")
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
                sampleName = None,
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

  private def _discover_maven_coordinates(warehouseDir: Path): Vector[MavenCoordinate] = {
    val root = warehouseDir.resolve("maven")
    if (!Files.isDirectory(root))
      Vector.empty
    else {
      val stream = Files.walk(root)
      try {
        stream.iterator().asScala.toVector.collect {
          case p if Files.isRegularFile(p) && _is_artifact_file(p) =>
            val rel = root.relativize(p).iterator().asScala.toVector.map(_.toString)
            rel match {
              case xs if xs.size >= 4 =>
                val groupId = xs.dropRight(3).mkString(".")
                val artifactId = xs(xs.size - 3)
                Some(MavenCoordinate(groupId, artifactId))
              case _ =>
                None
            }
        }.flatten.distinct.sortBy(_.key)
      } finally {
        stream.close()
      }
    }
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
                sampleName = None,
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

  private def _index_download_samples(
    warehouseDir: Path,
    publications: Vector[String],
    publicationPaths: Map[String, Option[String]]
  ): DownloadArtifact = {
    val files = publications.flatMap { publication =>
      val indexed = _download_scan_bases(publication, publicationPaths.getOrElse(publication, None)).flatMap { base =>
        _index_download_sample_base(warehouseDir, publication, warehouseDir.resolve("repository/download").resolve(base)) ++
          _index_download_sample_base(warehouseDir, publication, warehouseDir.resolve("download").resolve(base))
      }
      _prefer_first_download_files(indexed)
    }.sortBy(_.path)
    DownloadArtifact("sample-zip", _sort_versions(files.map(_.version).distinct), files)
  }

  private def _download_scan_bases(publication: String, publicationPath: Option[String]): Vector[String] = {
    val canonical = CozyPublicationPaths.downloadBase(publication, publicationPath)
    // Transitional compatibility only. Remove the legacy samples/<publication>
    // scan after existing warehouses have migrated to publication.path-based URLs.
    Vector(canonical, s"samples/${publication}").distinct
  }

  private def _index_download_sample_base(warehouseDir: Path, publication: String, dir: Path): Vector[IndexedFile] =
    if (Files.isDirectory(dir)) {
      val stream = Files.walk(dir)
      try {
        stream.iterator().asScala.toVector.collect {
          case p if Files.isRegularFile(p) && p.getFileName.toString.toLowerCase(java.util.Locale.ROOT).endsWith(".zip") =>
            val rel = dir.relativize(p).iterator().asScala.toVector.map(_.toString)
            val collectionArchive = rel.size == 2
            val filename = p.getFileName.toString
            val (sample, version) =
              if (collectionArchive) {
                None -> rel.headOption.getOrElse(_infer_version(filename, "zip").getOrElse("unknown"))
              } else {
                _download_sample_and_version(rel, filename)
              }
            _indexed_file(
              warehouseDir,
              p,
              layer = "download",
              artifactType = if (collectionArchive) "sample-collection-zip" else "sample-zip",
              groupId = None,
              artifactId = Some(publication),
              sampleName = sample,
              version = version,
              classifier = None
            )
        }
      } finally {
        stream.close()
      }
    } else {
      Vector.empty
    }

  private def _prefer_first_download_files(files: Vector[IndexedFile]): Vector[IndexedFile] =
    files.foldLeft(Vector.empty[IndexedFile]) { (z, file) =>
      val key = (file.artifactType, file.sampleName, file.version, file.name)
      if (z.exists(x => (x.artifactType, x.sampleName, x.version, x.name) == key))
        z
      else
        z :+ file
    }

  private def _download_sample_and_version(rel: Vector[String], filename: String): (Option[String], String) =
    rel match {
      case Vector(version, sample, _) if filename == s"${sample}-${version}.zip" =>
        Some(sample) -> version
      // Legacy download layout: <sample>/<version>/<sample>-<version>.zip.
      // Keep this only while index-warehouse accepts pre-migration warehouses.
      case Vector(sample, version, _) =>
        Some(sample) -> version
      case _ =>
        rel.headOption -> _infer_version(filename, "zip").getOrElse("unknown")
    }

  private def _indexed_file(
    warehouseDir: Path,
    path: Path,
    layer: String,
    artifactType: String,
    groupId: Option[String],
    artifactId: Option[String],
    sampleName: Option[String],
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
      sampleName = sampleName,
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
      "schema" -> _schema,
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
      "schema" -> _schema,
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

  private def _download_yaml(p: IndexResult): String =
    _yaml_header("download-artifact") +
      _project_yaml(p) +
      s"""artifact:
         |  layer: "download"
         |  status: ${_yaml_string(if (p.download.exists(_.files.nonEmpty)) "available" else "missing")}
         |  kinds:
         |${p.download.map(_download_kind_yaml).mkString}
         |  files:
         |${p.download.flatMap(_.files).map(_file_yaml).mkString}""".stripMargin

  private def _download_json(p: IndexResult): JsValue =
    Json.obj(
      "schema" -> _schema,
      "type" -> "download-artifact",
      "project" -> _project_json(p),
      "artifact" -> Json.obj(
        "layer" -> "download",
        "status" -> (if (p.download.exists(_.files.nonEmpty)) "available" else "missing"),
        "kinds" -> JsArray(p.download.map { x =>
          Json.obj(
            "type" -> x.kind,
            "versions" -> x.versions,
            "latestRelease" -> _latest_release(x.versions)
          )
        }),
        "files" -> JsArray(p.download.flatMap(_.files).map(_file_json))
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
      "schema" -> _schema,
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
    _sort_versions((p.maven.flatMap(_.versions) ++ p.repository.flatMap(_.versions) ++ p.download.flatMap(_.versions)).filter(_ != "unknown").distinct)

  private def _release_artifacts(version: String, p: IndexResult): Vector[JsValue] =
    p.maven.flatMap(_.files).filter(_.version == version).map { f =>
      Json.obj(
        "layer" -> "maven",
        "groupId" -> f.groupId,
        "artifactId" -> f.artifactId,
        "warehousePath" -> f.path,
        "publicPath" -> _public_artifact_path(f.path)
      )
    } ++ p.repository.flatMap(_.files).filter(_.version == version).map { f =>
      Json.obj(
        "layer" -> "repository",
        "type" -> f.artifactType,
        "module" -> f.artifactId,
        "warehousePath" -> f.path,
        "publicPath" -> _public_artifact_path(f.path)
      )
    } ++ p.download.flatMap(_.files).filter(_.version == version).map { f =>
      Json.obj(
        "layer" -> "download",
        "type" -> f.artifactType,
        "publication" -> f.artifactId,
        "sample" -> f.sampleName,
        "warehousePath" -> f.path,
        "publicPath" -> _public_artifact_path(f.path)
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

  private def _download_kind_yaml(p: DownloadArtifact): String =
    s"""    - type: ${_yaml_string(p.kind)}
       |      latest_release: ${_yaml_string(_latest_release(p.versions).getOrElse(""))}
       |      versions: [${p.versions.map(_yaml_string).mkString(", ")}]
       |""".stripMargin

  private def _file_yaml(p: IndexedFile): String =
    s"""    - warehouse_path: ${_yaml_string(p.path)}
       |      public_path: ${_yaml_string(_public_artifact_path(p.path))}
       |      name: ${_yaml_string(p.name)}
      |      version: ${_yaml_string(p.version)}
      |      type: ${_yaml_string(p.artifactType)}
      |      sample: ${_yaml_string(p.sampleName.getOrElse(""))}
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
      "sampleName" -> p.sampleName,
      "version" -> p.version,
      "classifier" -> p.classifier,
      "extension" -> p.extension,
      "warehousePath" -> p.path,
      "publicPath" -> _public_artifact_path(p.path),
      "name" -> p.name,
      "size" -> p.size,
      "sha256" -> p.sha256,
      "sha1" -> p.sha1,
      "md5" -> p.md5
    )

  private def _public_artifact_path(warehousePath: String): String =
    if (warehousePath.startsWith("maven/"))
      s"repository/${warehousePath}"
    else if (warehousePath.startsWith("repository/"))
      warehousePath
    else if (warehousePath.startsWith("download/"))
      s"repository/${warehousePath}"
    else
      warehousePath

  private def _yaml_header(kind: String): String =
    s"""schema: ${_yaml_string(_schema)}
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

  private def _csv(value: Option[String]): Vector[String] =
    value.toVector.flatMap(_.split(',')).map(_.trim).filter(_.nonEmpty)

  private def _positional_args(args: List[String]): Vector[String] = {
    val optionNamesWithValue = Set("warehouse", "save", "name", "title", "maven-coordinates", "repository-artifacts", "repository-modules", "download-samples")
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
    _artifact_extensions.contains(ext) && !_checksum_extensions.contains(ext)
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
    _slug_pattern.findFirstIn(name) match {
      case Some(x) if x == name => name
      case _ => RAISE.invalidArgumentFault(s"Invalid publication name: ${value}. Expected ${_slug_pattern.regex}")
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
        _execute_v1(rest)
      case _ =>
        RAISE.invalidArgumentFault("Missing sbt-bridge version. Expected: sbt-bridge v1 --request=<file>")
    }

  private def _execute_v1(args: List[String]): Unit = {
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
