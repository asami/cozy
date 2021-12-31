package cozy.web.jetty

import javax.servlet.ServletException
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse
import org.eclipse.jetty.server.Request
import org.eclipse.jetty.server.Server
import org.eclipse.jetty.server.handler.AbstractHandler
import cozy.Cozy

/*
 * @since   Dec.  4, 2021
 * @version Dec.  4, 2021
 * @author  ASAMI, Tomoharu
 */
object JettyServer {
  def main(args: Array[String]): Unit = {
    val cozy = Cozy.build(args)
    run(cozy)
  }

  def run(cozy: Cozy): Unit = {
    val server = new Server(8080)
    server.setHandler(new CozyHandler(cozy))
    server.start()
    server.join()
  }
}
