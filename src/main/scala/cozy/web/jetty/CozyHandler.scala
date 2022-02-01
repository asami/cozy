package cozy.web.jetty

import java.io.IOException
import javax.servlet.ServletException
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse
import javax.servlet.http.Cookie
import org.eclipse.jetty.server.Request
import org.eclipse.jetty.server.Server
import org.eclipse.jetty.server.handler.AbstractHandler
import cozy.Cozy
import org.goldenport.kaleidox.http._
import arcadia.WebEngine

/*
 * @since   Dec.  4, 2021
 *  version Dec. 18, 2021
 * @version Jan. 24, 2022
 * @author  ASAMI, Tomoharu
 */
class CozyHandler(cozy: Cozy) extends AbstractHandler {
  val SERVICE_PATH = "service"
  val WEB_PATH = "web"

  private val _service_engine = cozy.createHttpHandle()
  private val _web_engine: WebEngine = cozy.createWebEngine()

  @throws(classOf[IOException])
  @throws(classOf[ServletException])
  def handle(
    target: String,
    baseRequest: Request,
    request: HttpServletRequest,
    response: HttpServletResponse
  ): Unit = {
    ???
  }

  private def _handle_service(
    target: String,
    baseRequest: Request,
    request: HttpServletRequest,
    response: HttpServletResponse
  ): Unit = {
    val req = ServletHttpRequest(request)
    val res = ServletHttpResponse(response)
    val r = _service_engine.execute(req, res, SERVICE_PATH)
    response.setStatus(r.status)
    response.setContentType(r.mimeType)
    response.setCharacterEncoding(r.charset.name)
    for (h <- r.headers)
      response.setHeader(h.key, h.encodedValue)
    for (c <- r.cookies)
      response.addCookie(new Cookie(c.key, c.value))
    val out = response.getOutputStream()
    r.writeContent(out)
    baseRequest.setHandled(true)
  }

  private def _handle_web(
    target: String,
    baseRequest: Request,
    request: HttpServletRequest,
    response: HttpServletResponse
  ): Unit = {
    ???
  }
}
