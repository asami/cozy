package cozy.modeler

import org.goldenport.cli.{Config => CliConfig, _}
import cozy._

/*
 * @since   Aug. 20, 2025
 * @version Aug. 20, 2025
 * @author  ASAMI, Tomoharu
 */
case object ModelerServiceClass extends ServiceClass {
  val name = "modeler"
  val defaultOperation = None // XXX

  val operations = Operations(
    ClassOperationClass,
    ProjectOperationClass
  )

  case object ClassOperationClass extends OperationClassWithOperation {
    val request = spec.Request.empty
    val response = spec.Response.empty
    val specification = spec.Operation("cozy", request, response)
    
    def apply(env: Environment, req: Request): Response = {
      val ctx = env.toAppEnvironment[Context]
      ???
      VoidResponse
    }
  }

  case object ProjectOperationClass extends OperationClassWithOperation {
    val request = spec.Request.empty
    val response = spec.Response.empty
    val specification = spec.Operation("cozy", request, response)
    
    def apply(env: Environment, req: Request): Response = {
      val ctx = env.toAppEnvironment[Context]
      ???
      VoidResponse
    }
  }
}
