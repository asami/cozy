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
 * @version Apr. 23, 2026
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
    if (!_execute_car_sbt_project(args) && !_execute_sbt_bridge(args) && !_execute_package_archive(args))
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
      val converted = _convert_args(rest)
      (Vector(command) ++ converted).mkString(" ")
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
        val replArgs = _without_style_args(_without_project_file_policy_args(rest))
        val modelArgs = _without_save_args(replArgs)
        val modelPath = modelArgs.find(!_.startsWith("-")).map(Paths.get(_))
        val projectsave = _project_save_path(style, save)
        if (modelArgs.exists(!_.startsWith("-"))) {
          val generatedargs = modelArgs :+ s"--save=${projectsave}"
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
            _materialize_car_sbt_project(save, policy, versions, modelPath)
          case Cozy.ProjectLayoutStyle.CarSar =>
            _materialize_car_sar_sbt_project(save, policy, versions, modelPath)
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

  private def _project_save_path(style: Cozy.ProjectLayoutStyle, save: Path): Path =
    style match {
      case Cozy.ProjectLayoutStyle.CarOnly => save
      case Cozy.ProjectLayoutStyle.CarSar => save.resolve("component")
    }

  private def _materialize_car_sbt_project(
    dir: Path,
    policy: Cozy.ProjectFilePolicy,
    versions: Cozy.CarDependencyVersions,
    modelPath: Option[Path] = None
  ): Unit = {
    if (policy.isSkip)
      return
    Files.createDirectories(dir)
    _write_project_file(
      dir.resolve("build.sbt"),
      Cozy.carBuildSbt(versions),
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
    val sampleModel = cozydir.resolve("sample.cml")
    val modelContent = modelPath.filter(Files.exists(_)).
      map(Files.readString(_, StandardCharsets.UTF_8)).
      getOrElse(Cozy.carSampleCml())
    _write_project_file(
      sampleModel,
      modelContent,
      policy
    )
    val webdir = dir.resolve("src/main/web")
    Files.createDirectories(webdir)
    _write_project_file(
      webdir.resolve("web.yaml"),
      Cozy.carWebDescriptorYaml(modelPath),
      policy
    )
    if (modelPath.isEmpty) {
      val impldir = dir.resolve("src/main/scala/domain/impl")
      Files.createDirectories(impldir)
      _write_project_file(
        impldir.resolve("ComponentFactory.scala"),
        Cozy.carComponentFactorySource(),
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
    modelPath: Option[Path] = None
  ): Unit = {
    if (policy.isSkip)
      return
    val appname = Cozy.appNameFromPath(dir)
    Files.createDirectories(dir)
    _write_project_file(
      dir.resolve("README.md"),
      Cozy.carSarReadme(appname),
      policy
    )
    _write_project_file(
      dir.resolve("build.sbt"),
      Cozy.carSarBuildSbt(appname, versions),
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
    val sampleModel = cozydir.resolve(s"${appname}.cml")
    val modelContent = modelPath.filter(Files.exists(_)).
      map(Files.readString(_, StandardCharsets.UTF_8)).
      getOrElse(Cozy.carSarSampleCml(appname))
    _write_project_file(sampleModel, modelContent, policy)
    val webdir = componentdir.resolve("src/main/web")
    Files.createDirectories(webdir)
    _write_project_file(
      webdir.resolve("web.yaml"),
      Cozy.carWebDescriptorYaml(modelPath),
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
    val componentddir = subsystemdir.resolve("component.d")
    Files.createDirectories(componentddir)
    _write_project_file(
      componentddir.resolve("README.md"),
      Cozy.carSarComponentDReadme(appname),
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
  private val DefaultSbtCozyVersion = "0.1.3-SNAPSHOT"

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
    carBuildSbt(CarDependencyVersions.default)

  private[cozy] def carBuildSbt(versions: CarDependencyVersions): String =
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
      |lazy val packageCar = taskKey[File]("Create versioned CAR archive.")
      |
      |lazy val root = project
      |  .in(file("."))
      |  .enablePlugins(org.goldenport.cozy.CozyPlugin)
      |  .settings(
      |    organization := "com.example",
      |    name := "sample",
      |    version := "0.0.1-SNAPSHOT",
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
      |      "component" -> "sample-component",
      |      "boundedContext" -> "default",
      |      "domain" -> "default"
      |    ),
      |
      |    packageCar := {
      |      val out = target.value / "car" / s"$${name.value}-$${version.value}.car"
      |      val sourcedir = baseDirectory.value / "car.d"
      |      val pairs =
      |        if (sourcedir.exists())
      |          sbt.Path.allSubpaths(sourcedir).toSeq
      |        else
      |          Seq.empty
      |      IO.createDirectory(out.getParentFile)
      |      IO.zip(pairs, out, None)
      |      streams.value.log.info(s"CAR archive: $${out.getAbsolutePath}")
      |      out
      |    },
      |
      |    Compile / sourceGenerators += Def.task {
      |      val out = (Compile / sourceManaged).value / "domain" / "meta" / "BuildVersion.scala"
      |      val content =
      |        "package domain.meta\\n\\nobject BuildVersion {\\n" +
      |          "  val name: String = \\"" + name.value + "\\"\\n" +
      |          "  val version: String = \\"" + version.value + "\\"\\n" +
      |          "  val scalaVersion: String = \\"" + scalaVersion.value + "\\"\\n" +
      |          "}\\n"
      |      IO.write(out, content)
      |      Seq(out)
      |    }.taskValue
      |  )
      |""".stripMargin

  private[cozy] def carSarBuildSbt(appname: String, versions: CarDependencyVersions): String =
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
      |
      |lazy val commonSettings = Seq(
      |  organization := "com.example",
      |  version := "0.0.1-SNAPSHOT",
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
      |    name := "${appname}",
      |    publish / skip := true
      |  )
      |
      |lazy val component = project
      |  .in(file("component"))
      |  .enablePlugins(org.goldenport.cozy.CozyPlugin)
      |  .settings(commonSettings)
      |  .settings(
      |    name := "${appname}",
      |    cozyGeneratorBackend := "cozy",
      |    libraryDependencies ++= Seq(
      |      "org.goldenport" %% "goldenport-cncf" % cncfVersion,
      |      "org.simplemodeling" %% "simplemodeling-model" % simpleModelingModelVersion,
      |      "org.goldenport" % "cncf-collaborator-api" % cncfCollaboratorApiVersion,
      |      "org.scalatest" %% "scalatest" % "3.2.19" % Test
      |    ),
      |    cozyManifestMetadata ++= Map(
      |      "component" -> "${appname}",
      |      "boundedContext" -> "default",
      |      "domain" -> "${appname}"
      |    ),
      |    Test / fork := false
      |  )
      |
      |lazy val subsystem = project
      |  .in(file("subsystem"))
      |  .enablePlugins(org.goldenport.cozy.CozyPlugin)
      |  .settings(commonSettings)
      |  .settings(
      |    name := "${appname}-subsystem",
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

  private[cozy] def carSarReadme(appname: String): String =
    s"""# ${appname}
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

  private[cozy] def carSarSampleCml(appname: String): String =
    carSampleCml()

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

  private[cozy] def carSarComponentDReadme(appname: String): String =
    s"""# component.d
      |
      |Development-time packaged dependencies for `${appname}` live here.
      |
      |Current expected local setup:
      |- build `textus-user-account` as a CAR
      |- place it here as a symlink
      |- keep `subsystem-descriptor.yaml` coordinates stable
      |
      |Recommended local command:
      |`ln -s /absolute/path/to/textus-user-account-0.1.1-SNAPSHOT.car component.d/textus-user-account.car`
      |
      |Production distribution is repository-first. `component.d` is only the local development and test staging path.
      |""".stripMargin

  private[cozy] def carSarScriptsReadme(appname: String): String =
    s"""# subsystem/scripts
      |
      |Local subsystem run helpers belong here.
      |
      |The default ${appname} scaffold expects local packaged dependencies under `subsystem/component.d`.
      |For development, stage `textus-user-account` there as a symlink to the built CAR while keeping `subsystem-descriptor.yaml` on the stable repository coordinate.
      |""".stripMargin

  private[cozy] def carPluginsSbt(): String =
    s"""resolvers += Resolver.defaultLocal
       |addSbtPlugin("org.goldenport" % "sbt-cozy" % "${DefaultSbtCozyVersion}")
       |""".stripMargin

  private[cozy] def carSampleCml(): String =
    """# COMPONENT
      |
      |## Sample
      |
      |### PACKAGE
      |
      |domain
      |
      |### DESCRIPTION
      |
      |Sample CAR bundle root for the notice-board app.
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
      |- component :: Sample
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
      |- component :: Sample
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
      |##### IN
      |
      |Notice post payload.
      |
      |##### OUT
      |
      |Posted notice.
      |
      |#### searchNotices
      |
      |##### IN
      |
      |Notice search query.
      |
      |##### OUT
      |
      |Matching notices.
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
      |
      |# OPERATION
      |
      |## postNotice
      |
      |### TYPE
      |COMMAND
      |
      |### IMPLEMENTATION
      |entity-create
      |
      |### INPUT
      |PostNotice
      |
      |### OUTPUT
      |PostNoticeResult
      |
      |## searchNotices
      |
      |### TYPE
      |QUERY
      |
      |### IMPLEMENTATION
      |entity-search
      |
      |### INPUT
      |SearchNotices
      |
      |### OUTPUT
      |SearchNoticesResult
      |""".stripMargin

  private[cozy] def carWebDescriptorYaml(modelPath: Option[Path] = None): String =
    modelPath.flatMap(_car_web_descriptor_yaml_from_cml).getOrElse(_default_car_web_descriptor_yaml)

  private def _default_car_web_descriptor_yaml: String =
    """expose:
      |  sample.notice.post-notice: protected
      |  sample.notice.search-notices: public
      |form:
      |  sample.notice.post-notice:
      |    enabled: true
      |    successRedirect: /web/${component}/admin/entities/notice/${result.id}
      |    stayOnError: true
      |    controls:
      |      body:
      |        type: textarea
      |        required: true
      |  sample.notice.search-notices:
      |    enabled: true
      |    successRedirect: /web/${component}/admin/entities/notice
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

  private[cozy] def carComponentFactorySource(): String =
    """package domain.impl
      |
      |import domain.SampleComponent
      |import org.goldenport.cncf.component.{Component, ComponentCreate, ComponentId}
      |
      |final class ComponentFactory extends Component.BundleFactory {
      |  def primaryFactory: Component.PrimaryComponentFactory =
      |    SamplePrimaryFactory
      |
      |  override def componentletFactories: Vector[Component.ComponentletFactory] =
      |    Vector.empty
      |}
      |
      |abstract class SampleParticipantFactoryBase extends SampleComponent.Factory {
      |  protected final val sharedServices =
      |    Vector(
      |      SampleComponent.NoticeService,
      |      SampleComponent.AggregateService,
      |      SampleComponent.ViewService,
      |      SampleComponent.EntityService
      |    )
      |
      |  protected final def componentCore(
      |    name: String,
      |    componentId: ComponentId
      |  ): Component.Core =
      |    spec_create(name, componentId, sharedServices)
      |
      |  override val Notice: SampleComponent.NoticeServiceFactory = DefaultNoticeServiceFactory()
      |  override val aggregate: SampleComponent.AggregateServiceFactory = AggregateServiceFactoryImpl()
      |  override val view: SampleComponent.ViewServiceFactory = ViewServiceFactoryImpl()
      |  override val entity: SampleComponent.EntityServiceFactory = DefaultEntityServiceFactory()
      |}
      |
      |final class SamplePrimaryComponent extends SampleComponent
      |
      |object SamplePrimaryFactory extends SampleParticipantFactoryBase with Component.PrimaryComponentFactory {
      |  protected def create_Component(params: ComponentCreate): Component =
      |    new SamplePrimaryComponent()
      |
      |  override protected def create_Core(
      |    params: ComponentCreate,
      |    comp: Component
      |  ): Component.Core =
      |    componentCore(SampleComponent.name, SampleComponent.componentId)
      |}
      |
      |final class DefaultNoticeServiceFactory extends SampleComponent.NoticeServiceFactory {
      |  import SampleComponent.NoticeService.*
      |
      |  override def createPostNoticeActionCall(
      |    core: org.goldenport.cncf.action.ActionCall.Core,
      |    action: PostNoticeCommand
      |  ): PostNoticeActionCall =
      |    PostNoticeActionCall(core, action)
      |
      |  override def createSearchNoticesActionCall(
      |    core: org.goldenport.cncf.action.ActionCall.Core,
      |    action: SearchNoticesQuery
      |  ): SearchNoticesActionCall =
      |    SearchNoticesActionCall(core, action)
      |  }
      |
      |object DefaultNoticeServiceFactory {
      |  def apply(): DefaultNoticeServiceFactory = new DefaultNoticeServiceFactory()
      |  }
      |
      |final class DefaultEntityServiceFactory extends SampleComponent.EntityServiceFactory
      |
      |object DefaultEntityServiceFactory {
      |  def apply(): DefaultEntityServiceFactory = new DefaultEntityServiceFactory()
      |  }
      |
      |final class AggregateServiceFactoryImpl extends SampleComponent.AggregateServiceFactory
      |
      |object AggregateServiceFactoryImpl {
      |  def apply(): AggregateServiceFactoryImpl = new AggregateServiceFactoryImpl()
      |}
      |
      |final class ViewServiceFactoryImpl extends SampleComponent.ViewServiceFactory
      |
      |object ViewServiceFactoryImpl {
      |  def apply(): ViewServiceFactoryImpl = new ViewServiceFactoryImpl()
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
      |  car-sbt-project [model-file] --save=<dir> [--style=car|car-sar] [--no-project-files] [--overwrite-project-files]
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
      |  package-car --save=<file> --main-jar=<file> --name=<name> --version=<version> --component=<component> [--entities=<spec>]
      |      Build a CAR archive.
      |
      |  package-sar --save=<file> --source-dir=<dir> --name=<name> --version=<version>
      |      Build a SAR archive.
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
    val defaultConf = _path(args, "default-conf")
    val docsDir = _path(args, "docs-dir")
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
        libJars.map(p => p -> s"lib/${p.getFileName}") ++
        spiJars.map(p => p -> s"spi/${p.getFileName}") ++
        defaultConf.toVector.map(_ -> "config/default.conf") ++
        _docs_entries(docsDir) ++
        Vector(_write_temp("component-descriptor", _component_descriptor_json(name, version, component, extensionMap, configMap, entities)) -> "component-descriptor.json"),
      Vector("component", "lib", "spi", "config", "docs")
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

  private def _docs_entries(docsDir: Option[Path]): Vector[(Path, String)] =
    docsDir.toVector.flatMap { dir =>
      _archive_sources(dir).map { case (p, rel) => p -> s"docs/${rel}" }
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
        Files.copy(source, dest)
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
