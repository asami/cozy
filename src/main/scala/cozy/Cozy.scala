package cozy

import org.goldenport.RAISE
import org.goldenport.cli.{Config => CliConfig, _}
import org.goldenport.value._
import org.goldenport.parser.CommandParser
import org.goldenport.kaleidox.Kaleidox
import org.goldenport.kaleidox.http.HttpHandle
import cozy.archive.CozyArchivePackager
import cozy.publication.{CozyPublicationCompiler, CozySampleDistributor, CozyWarehouseIndexer}
import cozy.runtime.{CozyRuntime, CozySbtBridge}
import cozy.scaffold.CozyScaffold
import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path, Paths}
import scala.collection.JavaConverters._

/*
 * @since   Dec.  4, 2021
 *  version Dec. 19, 2021
 *  version Jan.  1, 2022
 *  version Feb. 28, 2022
 *  version Aug. 20, 2025
 *  version Mar. 17, 2026
 *  version Apr. 29, 2026
 * @version May. 21, 2026
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
    if (!_execute_init(args) && !_execute_car_sbt_project(args) && !_execute_publish_car(args) && !_execute_publish_sar(args) && !_execute_publish_project(args) && !_execute_distribute_samples(args) && !_execute_index_warehouse(args) && !_execute_sbt_bridge(args) && !_execute_package_archive(args))
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
        val replargs = _without_car_scaffold_args(_without_style_args(_without_project_file_policy_args(rest)))
        val modelargs = _without_save_args(replargs)
        val normalizedmodelargs = _normalize_first_positional_path(modelargs)
        val modelpath = normalizedmodelargs.find(!_.startsWith("-")).map(Paths.get(_))
        val projectsave = _project_save_path(style, save)
        if (normalizedmodelargs.exists(!_.startsWith("-"))) {
          val generatedargs = normalizedmodelargs :+ s"--save=${projectsave}"
          val repl = (Vector("modeler-scala") ++ _convert_args(generatedargs)).mkString(" ")
          val c = _operation_call(Array(repl))
          interpreter.execute(c)
          _delete_directory(projectsave.resolve("target"))
          if (!modelpath.exists(_.getFileName.toString.endsWith(".dox"))) {
            _delete_directory(projectsave.resolve("src/main/scala"))
          }
        }
        style match {
          case CozyScaffold.ProjectLayoutStyle.CarOnly =>
            _materialize_car_sbt_project(save, policy, versions, scaffold, modelpath)
          case CozyScaffold.ProjectLayoutStyle.CarSar =>
            _materialize_car_sar_sbt_project(save, policy, versions, scaffold, modelpath)
        }
        true
      case _ =>
        false
    }

  private def _execute_init(args: Array[String]): Boolean =
    _leading_command(args) match {
      case Some(("init", "component" :: rest)) =>
        val init = Cozy.ComponentInitConfig.create(rest)
        val policy = Cozy.ProjectFilePolicy.create(rest)
        val versions = Cozy.CarDependencyVersions.create(rest)
        init.style match {
          case CozyScaffold.ProjectLayoutStyle.CarOnly =>
            _materialize_car_sbt_project(init.save, policy, versions, init.scaffold, None)
            _materialize_car_project_yaml(init.save, policy, versions, init)
          case CozyScaffold.ProjectLayoutStyle.CarSar =>
            _materialize_car_sar_sbt_project(init.save, policy, versions, init.scaffold, None)
            _materialize_car_project_yaml(init.save.resolve("component"), policy, versions, init)
        }
        true
      case Some(("init", other :: _)) =>
        RAISE.invalidArgumentFault(s"Unsupported init target: ${other}")
      case _ =>
        false
    }

  private def _execute_publish_car(args: Array[String]): Boolean =
    _leading_command(args) match {
      case Some(("publish-car", rest)) =>
        CozyCarPublisher.publish(rest)
        true
      case _ =>
        false
    }

  private def _execute_publish_sar(args: Array[String]): Boolean =
    _leading_command(args) match {
      case Some(("publish-sar", rest)) =>
        CozySarPublisher.publish(rest)
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
      case CozyScaffold.ProjectLayoutStyle.CarOnly => save
      case CozyScaffold.ProjectLayoutStyle.CarSar => save.resolve("component")
    }

  private def _materialize_car_project_yaml(
    dir: Path,
    policy: Cozy.ProjectFilePolicy,
    versions: Cozy.CarDependencyVersions,
    init: Cozy.ComponentInitConfig
  ): Unit =
    if (!policy.isSkip)
      _write_project_file(
        dir.resolve("project.yaml"),
        Cozy.carProjectYaml(init, versions),
        policy
      )

  private def _materialize_car_sbt_project(
    dir: Path,
    policy: Cozy.ProjectFilePolicy,
    versions: Cozy.CarDependencyVersions,
    scaffold: Cozy.CarScaffoldConfig,
    modelpath: Option[Path] = None
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
    val samplemodel = cozydir.resolve(scaffold.modelFileName)
    val modelcontent = modelpath.filter(Files.exists(_)).
      map(Files.readString(_, StandardCharsets.UTF_8)).
      getOrElse(Cozy.carSampleCml(scaffold))
    _write_project_file(
      samplemodel,
      modelcontent,
      policy
    )
    val webdir = dir.resolve("src/main/web-inf")
    Files.createDirectories(webdir)
    _write_project_file(
      webdir.resolve("web.yaml"),
      Cozy.carWebAppYaml(scaffold),
      policy
    )
    _write_project_file(
      webdir.resolve("form.yaml"),
      Cozy.carWebDescriptorYaml(modelpath, scaffold),
      policy
    )
    if (modelpath.isEmpty) {
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
    modelpath: Option[Path] = None
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
    val samplemodel = cozydir.resolve(scaffold.modelFileName)
    val modelcontent = modelpath.filter(Files.exists(_)).
      map(Files.readString(_, StandardCharsets.UTF_8)).
      getOrElse(Cozy.carSarSampleCml(scaffold))
    _write_project_file(samplemodel, modelcontent, policy)
    val webdir = componentdir.resolve("src/main/web-inf")
    Files.createDirectories(webdir)
    _write_project_file(
      webdir.resolve("web.yaml"),
      Cozy.carWebAppYaml(scaffold),
      policy
    )
    _write_project_file(
      webdir.resolve("form.yaml"),
      Cozy.carWebDescriptorYaml(modelpath, scaffold),
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
      case CozyScaffold.ProjectFilePolicy.Skip =>
        Unit
      case CozyScaffold.ProjectFilePolicy.Overwrite =>
        _write_text(path, content)
      case CozyScaffold.ProjectFilePolicy.Default =>
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
  type CarDependencyVersions = CozyScaffold.CarDependencyVersions
  object CarDependencyVersions {
    def apply(
      cncfversion: String,
      simplemodelingmodelversion: String,
      cncfcollaboratorapiversion: String
    ): CarDependencyVersions =
      CozyScaffold.CarDependencyVersions(
        cncfversion,
        simplemodelingmodelversion,
        cncfcollaboratorapiversion
      )

    def unapply(value: CarDependencyVersions): Option[(String, String, String)] =
      CozyScaffold.CarDependencyVersions.unapply(value)

    def default: CarDependencyVersions =
      CozyScaffold.CarDependencyVersions.default

    def isOption(p: String): Boolean =
      CozyScaffold.CarDependencyVersions.isOption(p)

    def isInlineOption(p: String): Boolean =
      CozyScaffold.CarDependencyVersions.isInlineOption(p)

    def isFlagOption(p: String): Boolean =
      CozyScaffold.CarDependencyVersions.isFlagOption(p)

    def create(args: List[String]): CarDependencyVersions =
      CozyScaffold.CarDependencyVersions.create(args)
  }

  type ProjectLayoutStyle = CozyScaffold.ProjectLayoutStyle
  object ProjectLayoutStyle {
    def isOption(p: String): Boolean =
      CozyScaffold.ProjectLayoutStyle.isOption(p)

    def isInlineOption(p: String): Boolean =
      CozyScaffold.ProjectLayoutStyle.isInlineOption(p)

    def isFlagOption(p: String): Boolean =
      CozyScaffold.ProjectLayoutStyle.isFlagOption(p)

    def create(args: List[String]): ProjectLayoutStyle =
      CozyScaffold.ProjectLayoutStyle.create(args)
  }

  type ProjectFilePolicy = CozyScaffold.ProjectFilePolicy
  object ProjectFilePolicy {
    def isPolicyOption(p: String): Boolean =
      CozyScaffold.ProjectFilePolicy.isPolicyOption(p)

    def isFlagOption(p: String): Boolean =
      CozyScaffold.ProjectFilePolicy.isFlagOption(p)

    def isInlineOption(p: String): Boolean =
      CozyScaffold.ProjectFilePolicy.isInlineOption(p)

    def create(args: List[String]): ProjectFilePolicy =
      CozyScaffold.ProjectFilePolicy.create(args)
  }

  type CarScaffoldConfig = CozyScaffold.CarScaffoldConfig
  object CarScaffoldConfig {
    def apply(
      componentname: String,
      packagename: String,
      artifactname: String,
      organization: String,
      version: String,
      boundedcontext: String,
      domain: String,
      gitignore: Boolean,
      readme: Boolean,
      tests: Boolean
    ): CarScaffoldConfig =
      CozyScaffold.CarScaffoldConfig(
        componentname,
        packagename,
        artifactname,
        organization,
        version,
        boundedcontext,
        domain,
        gitignore,
        readme,
        tests
      )

    def unapply(value: CarScaffoldConfig): Option[(String, String, String, String, String, String, String, Boolean, Boolean, Boolean)] =
      CozyScaffold.CarScaffoldConfig.unapply(value)

    def isFlagOption(p: String): Boolean =
      CozyScaffold.CarScaffoldConfig.isFlagOption(p)

    def isInlineOption(p: String): Boolean =
      CozyScaffold.CarScaffoldConfig.isInlineOption(p)

    def isSwitchOption(p: String): Boolean =
      CozyScaffold.CarScaffoldConfig.isSwitchOption(p)

    def create(args: List[String], save: Path, style: ProjectLayoutStyle = CozyScaffold.ProjectLayoutStyle.CarOnly): CarScaffoldConfig =
      CozyScaffold.CarScaffoldConfig.create(args, save, style)
  }

  type ComponentInitConfig = CozyScaffold.ComponentInitConfig
  object ComponentInitConfig {
    def apply(
      save: Path,
      style: ProjectLayoutStyle,
      scaffold: CarScaffoldConfig,
      displayname: String
    ): ComponentInitConfig =
      CozyScaffold.ComponentInitConfig(save, style, scaffold, displayname)

    def unapply(value: ComponentInitConfig): Option[(Path, ProjectLayoutStyle, CarScaffoldConfig, String)] =
      CozyScaffold.ComponentInitConfig.unapply(value)

    def create(args: List[String]): ComponentInitConfig =
      CozyScaffold.ComponentInitConfig.create(args)
  }

  private[cozy] def detectSbtVersion(): String = CozyScaffold.detectSbtVersion()
  private[cozy] def appNameFromPath(path: Path): String = CozyScaffold.appNameFromPath(path)
  private[cozy] def carBuildSbt(): String = CozyScaffold.carBuildSbt()
  private[cozy] def carBuildSbt(versions: CarDependencyVersions, scaffold: CarScaffoldConfig): String = CozyScaffold.carBuildSbt(versions, scaffold)
  private[cozy] def carProjectYaml(init: ComponentInitConfig, versions: CarDependencyVersions): String = CozyScaffold.carProjectYaml(init, versions)
  private[cozy] def carSarBuildSbt(scaffold: CarScaffoldConfig, versions: CarDependencyVersions): String = CozyScaffold.carSarBuildSbt(scaffold, versions)
  private[cozy] def carSarReadme(scaffold: CarScaffoldConfig): String = CozyScaffold.carSarReadme(scaffold)
  private[cozy] def carSarSampleCml(scaffold: CarScaffoldConfig): String = CozyScaffold.carSarSampleCml(scaffold)
  private[cozy] def carSarSubsystemDescriptorYaml(appname: String): String = CozyScaffold.carSarSubsystemDescriptorYaml(appname)
  private[cozy] def carSarRepositoryDReadme(appname: String): String = CozyScaffold.carSarRepositoryDReadme(appname)
  private[cozy] def carSarScriptsReadme(appname: String): String = CozyScaffold.carSarScriptsReadme(appname)
  private[cozy] def carPluginsSbt(): String = CozyScaffold.carPluginsSbt()
  private[cozy] def carSampleCml(scaffold: CarScaffoldConfig = CarScaffoldConfig.create(Nil, Paths.get("sample"))): String = CozyScaffold.carSampleCml(scaffold)
  private[cozy] def carWebDescriptorYaml(modelpath: Option[Path] = None, scaffold: CarScaffoldConfig = CarScaffoldConfig.create(Nil, Paths.get("sample"))): String = CozyScaffold.carWebDescriptorYaml(modelpath, scaffold)
  private[cozy] def carWebAppYaml(scaffold: CarScaffoldConfig = CarScaffoldConfig.create(Nil, Paths.get("sample"))): String = CozyScaffold.carWebAppYaml(scaffold)
  private[cozy] def carComponentFactorySource(scaffold: CarScaffoldConfig = CarScaffoldConfig.create(Nil, Paths.get("sample"))): String = CozyScaffold.carComponentFactorySource(scaffold)
  private[cozy] def carGitignore(): String = CozyScaffold.carGitignore()
  private[cozy] def carReadme(scaffold: CarScaffoldConfig): String = CozyScaffold.carReadme(scaffold)
  private[cozy] def carComponentFactorySpecSource(scaffold: CarScaffoldConfig): String = CozyScaffold.carComponentFactorySpecSource(scaffold)
  private[cozy] def carCncfCommonScript(versions: CarDependencyVersions): String = CozyScaffold.carCncfCommonScript(versions)
  private[cozy] def carUpdateRuntimeClasspathScript(): String = CozyScaffold.carUpdateRuntimeClasspathScript()
  private[cozy] def carRunServerScript(): String = CozyScaffold.carRunServerScript()
  private[cozy] def carRunServerDebugScript(): String = CozyScaffold.carRunServerDebugScript()
  private[cozy] def carLauncherScript(): String = CozyScaffold.carLauncherScript()
  private[cozy] val helpText: String = CozyScaffold.helpText

  val CozyServiceClass: CozyRuntime.CozyServiceClass.type = CozyRuntime.CozyServiceClass
  val CozyOperationClass: CozyRuntime.CozyOperationClass.type = CozyRuntime.CozyOperationClass
  val WebOperationClass: CozyRuntime.WebOperationClass.type = CozyRuntime.WebOperationClass

  def build(args: Array[String]): Cozy = CozyRuntime.build(args)
  def main(args: Array[String]): Unit = CozyRuntime.main(args)

  private[cozy] def _save_path(args: List[String]): Option[Path] = CozyRuntime.savePath(args)
  private[cozy] def _cli_path(value: String): Path = CozyRuntime.cliPath(value)
}
