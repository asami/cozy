package cozy.runtime

import org.goldenport.cli._
import org.goldenport.cli.spec
import org.goldenport.kaleidox.Kaleidox
import org.goldenport.value._
import org.smartdox.service.operations.{HtmlOperationClass, PdfOperationClass, SiteOperationClass}
import cozy.{Config, Context, Cozy}
import cozy.modeler
import cozy.web.jetty.JettyServer
import java.nio.file.{Path, Paths}

/*
 * @since   May. 20, 2026
 * @version Jun.  4, 2026
 * @author  ASAMI, Tomoharu
 */
private[cozy] object CozyRuntime {
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
    savePath(args.toList).
      map(path => env.copy(config = env.config.copy(projectDirectory = Some(path.toFile)))).
      getOrElse(env)

  private[cozy] def savePath(args: List[String]): Option[Path] = {
    val parsed = CozyCliArgs.parse(spec.Parameter.propertyFileOption("save"))(args)
    parsed.property("save").map(cliPath).orElse(_legacy_save_path(args))
  }

  private def _legacy_save_path(args: List[String]): Option[Path] = {
    @annotation.tailrec
    def go(xs: List[String]): Option[Path] = xs match {
      case Nil => None
      case x :: xx if x.startsWith("--save=") =>
        Some(cliPath(x.drop("--save=".length)))
      case _ :: xx =>
        go(xx)
    }
    go(args)
  }

  private[cozy] def cliPath(value: String): Path = {
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
