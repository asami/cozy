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

/*
 * @since   Dec.  4, 2021
 *  version Dec. 19, 2021
 *  version Jan.  1, 2022
 *  version Feb. 28, 2022
 *  version Aug. 20, 2025
 * @version Mar. 17, 2026
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
}

object Cozy {
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
