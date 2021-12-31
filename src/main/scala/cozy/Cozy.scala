package cozy

import org.goldenport.RAISE
import org.goldenport.i18n.I18NString
import org.goldenport.cli.{Config => CliConfig, _}
import org.goldenport.value._
import org.goldenport.kaleidox.Kaleidox
import org.goldenport.kaleidox.http.HttpHandle
import org.smartdox.service.operations.HtmlOperationClass

/*
 * @since   Dec.  4, 2021
 *  version Dec. 19, 2021
 * @version Jan.  1, 2022
 * @author  ASAMI, Tomoharu
 */
class Cozy(
  config: Config,
  environment: Environment,
  services: Services,
  operations: Operations
) {
  private val _engine = Engine.standard(services, operations)

  def execute(args: Array[String]) = _engine.apply(environment, args)

  def run(args: Array[String]) {
    execute(args)
  }

  def repl(call: OperationCall) {
    val kal = createInterpreter()
    kal.repl(call)
  }

  def createHttpHandle(): HttpHandle = {
    val args = Array[String]()
    val kal = createInterpreter()
    val req = spec.Request.empty
    val res = spec.Response()
    val op = spec.Operation("cozy", req, res)
    val call = OperationCall.create(op, args)
    kal.http(call)
  }

  def createInterpreter(): Kaleidox = {
    val kconfig = org.goldenport.kaleidox.Config.create(environment).
      setModeler(new modeler.Modeler())
    new Kaleidox(kconfig, environment)
  }
}

object Cozy {
  case object CozyServiceClass extends ServiceClass {
    val name = "cozy"
    val defaultOperation = Some(CozyOperationClass)
    val operations = Operations(
      CozyOperationClass,
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

  def build(args: Array[String]): Cozy = {
    val env0 = Environment.create(args)
    val config = Config.create(env0)
    val kaleidox = _create(env0)
    val context = new Context(env0, config, kaleidox)
    val env = env0.withAppEnvironment(context)
    val services = Services(
      CozyServiceClass
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