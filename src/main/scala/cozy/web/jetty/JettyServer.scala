package cozy.web.jetty

import javax.servlet.ServletException
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse
import org.eclipse.jetty.server.Request
import org.eclipse.jetty.server.Server
import org.eclipse.jetty.server.handler.AbstractHandler
import cozy.Cozy
import cozy.Context

/*
 * @since   Dec.  4, 2021
 * @version Feb. 28, 2022
 * @author  ASAMI, Tomoharu
 */
object JettyServer {
  def main(args: Array[String]): Unit = {
    val cozy = Cozy.build(args)
    run(cozy)
  }

  def run(cozy: Cozy): Unit = {
    val ctx = cozy.environment.toAppEnvironment[Context]
    run(ctx)
  }

  def run(ctx: Context): Unit = {
    val server = new Server(8080)
    server.setHandler(new CozyHandler(ctx))
    server.start()
    server.join()
  }
}
