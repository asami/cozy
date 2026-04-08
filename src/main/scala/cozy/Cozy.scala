package cozy

import org.goldenport.RAISE
import org.goldenport.i18n.I18NString
import org.goldenport.cli.{Config => CliConfig, _}
import org.goldenport.value._
import org.goldenport.parser.CommandParser
import org.goldenport.kaleidox.Kaleidox
import org.goldenport.kaleidox.http.HttpHandle
import org.smartdox.service.operations.HtmlOperationClass
import cozy.web.jetty.JettyServer
import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path, Paths}
import scala.util.Try

/*
 * @since   Dec.  4, 2021
 *  version Dec. 19, 2021
 *  version Jan.  1, 2022
 *  version Feb. 28, 2022
 *  version Aug. 20, 2025
 *  version Mar. 17, 2026
 * @version Apr.  8, 2026
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
    val call = _operation_call(args)
    if (call.request.arguments.isEmpty || call.request.isInteractive)
      repl(call)
    else if (_is_cli_command(call))
      execute(args)
    else
      executeDirect(args)
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
    if (!_execute_car_sbt_project(args))
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
        val repl = (Vector("modeler-scala") ++ _convert_args(rest)).mkString(" ")
        val c = _operation_call(Array(repl))
        interpreter.execute(c)
        _materialize_car_sbt_project(save)
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

  private def _save_path(args: List[String]): Option[Path] = {
    @annotation.tailrec
    def go(xs: List[String]): Option[Path] = xs match {
      case Nil => None
      case x :: xx if x.startsWith("--save=") =>
        Some(Paths.get(x.drop("--save=".length)).toAbsolutePath.normalize())
      case "--save" :: value :: _ =>
        Some(Paths.get(value).toAbsolutePath.normalize())
      case _ :: xx =>
        go(xx)
    }
    go(args)
  }

  private def _materialize_car_sbt_project(dir: Path): Unit = {
    Files.createDirectories(dir)
    Files.writeString(
      dir.resolve("build.sbt"),
      Cozy.carBuildSbt(),
      StandardCharsets.UTF_8
    )
    val projectdir = dir.resolve("project")
    Files.createDirectories(projectdir)
    Files.writeString(
      projectdir.resolve("build.properties"),
      s"sbt.version=${Cozy.detectSbtVersion()}",
      StandardCharsets.UTF_8
    )
  }
}

object Cozy {
  private val DefaultSbtVersion = "1.9.7"

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

  private[cozy] def carBuildSbt(): String =
    """val scala3Version = "3.3.7"
      |
      |lazy val root = project
      |  .in(file("."))
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
      |    libraryDependencies += "org.goldenport" %% "goldenport-cncf" % "0.4.2-SNAPSHOT",
      |    libraryDependencies += "org.simplemodeling" %% "simplemodeling-model" % "0.1.2-SNAPSHOT",
      |    libraryDependencies += "org.goldenport" % "cncf-collaborator-api" % "0.1.0-SNAPSHOT",
      |
      |    dependencyOverrides ++= Seq(
      |      "org.goldenport" % "cncf-collaborator-api" % "0.1.0-SNAPSHOT",
      |      "org.scala-lang.modules" %% "scala-xml" % "2.1.0",
      |      "org.scala-lang.modules" %% "scala-parser-combinators" % "2.3.0"
      |    ),
      |
      |    Compile / unmanagedSourceDirectories += (Compile / sourceManaged).value
      |  )
      |""".stripMargin

  case object CozyServiceClass extends ServiceClass {
    val name = "cozy"
    val defaultOperation = Some(CozyOperationClass)
    val operations = Operations(
      CozyOperationClass,
      WebOperationClass,
      HtmlOperationClass
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
    val config = Config.create(env0)
    val kaleidox = _create(env0)
    val context = new Context(env0, config, kaleidox)
    val env = env0.withAppEnvironment(context)
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
}
