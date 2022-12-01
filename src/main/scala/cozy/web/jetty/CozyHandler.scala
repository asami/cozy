package cozy.web.jetty

import java.io.IOException
import javax.servlet.ServletException
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse
import javax.servlet.http.Cookie
import org.eclipse.jetty.server.Request
import org.eclipse.jetty.server.Server
import org.eclipse.jetty.server.handler.AbstractHandler
import org.goldenport.kaleidox.http._
import org.goldenport.context.StatusCode
import org.goldenport.io.IoUtils
import org.goldenport.values.PathName
import arcadia._
import cozy.Cozy
import cozy.Context
import cozy.web.arcadia._

/*
 * @since   Dec.  4, 2021
 *  version Dec. 18, 2021
 *  version Jan. 24, 2022
 *  version Feb. 28, 2022
 *  version Mar.  6, 2022
 *  version Oct. 30, 2022
 * @version Nov. 28, 2022
 * @author  ASAMI, Tomoharu
 */
class CozyHandler(ctx: Context) extends AbstractHandler {
  val SERVICE_PATH = "service"
  val WEB_PATH = "web"
  val FAVICON_PATH = "favicon.ico"

  private val _service_engine = ctx.createHttpHandle()

  private val _web_engines = EngineHangar.create(ctx)

  @throws(classOf[IOException])
  @throws(classOf[ServletException])
  def handle(
    target: String,
    baseRequest: Request,
    request: HttpServletRequest,
    response: HttpServletResponse
  ): Unit = {
    val pn = PathName(target)
    pn.headOption.map {
      case SERVICE_PATH => _handle_service(pn.tail, baseRequest, request, response)
      case WEB_PATH => _handle_web(pn.tail, baseRequest, request, response)
      case FAVICON_PATH => _handle_favicon(baseRequest, request, response)
      case _ => ???
    }.getOrElse(???)
  }

  private def _handle_service(
    target: PathName,
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
    target: PathName,
    baseRequest: Request,
    request: HttpServletRequest,
    response: HttpServletResponse
  ): Unit = {
    val name = target.head // TODO
    val cmd = target.tailOption match {
      case None => IndexCommand()
      case Some(s) => s match {
        case m if m.length == 1 && m.firstComponentBody.toLowerCase == "index" => IndexCommand(m)
        case m => MaterialCommand(m)
      }
    }
    _web_engines(name).execute(cmd, request, response)
    baseRequest.setHandled(true)
  }

  private def _handle_favicon(
    baseRequest: Request,
    request: HttpServletRequest,
    response: HttpServletResponse
  ): Unit = {
    response.setStatus(StatusCode.Ok.code)
    response.setContentType(MimeType.image_xicon.name)
    val url = ctx.getAppResource("favicon/favicon.ico")
    url.foreach(IoUtils.write(response.getOutputStream(), _))
    baseRequest.setHandled(true)
  }
}
