package cozy.web.arcadia

import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse
import org.goldenport.Platform
import org.goldenport.RAISE
import org.goldenport.values.PathName
import org.goldenport.util.StringUtils
import org.goldenport.record.v2._
import org.goldenport.record.v2.util.SchemaBuilder
import org.goldenport.record.v2.util.SchemaBuilder._
import org.goldenport.kaleidox.http.ServletHttpRequest
import arcadia._
import arcadia.context.PlatformContext
import arcadia.view._
import arcadia.domain._

/*
 * @since   Jan. 23, 2022
 *  version Feb. 27, 2022
 *  version Mar.  6, 2022
 *  version May. 23, 2022
 *  version Aug. 29, 2022
 *  version Sep. 25, 2022
 * @version May.  3, 2025
 * @author  ASAMI, Tomoharu
 */
class Engine(
  val platform: CozyPlatformContext,
  val engine: WebEngine,
  val name: String
) {
  def execute(
    pn: PathName,
    request: HttpServletRequest,
    response: HttpServletResponse
  ): Unit = {
    execute(MaterialCommand(pn), request, response)
  }

  def execute(
    cmd: Command,
    request: HttpServletRequest,
    response: HttpServletResponse
  ): Unit = {
    val req = ServletHttpRequest(request)
    val r = _execute(req, cmd)
    response.setStatus(r.code)
    response.setContentType(r.mimetype.name)
    val charset = r.charset orElse {
      r match {
        case m: BinaryContent => None
        case _ => Some(Platform.charset.UTF8.name)
      }
    }
    charset.foreach(response.setCharacterEncoding)
    for (h <- r.httpHeader)
      response.setHeader(h._1, h._2)
    // for (c <- r.cookies)
    //   response.addCookie(new Cookie(c.key, c.value))
    r match {
      case m: RedirectContent =>
        val pn = req.pathname.v
        val path = if (pn.endsWith(name))
          StringUtils.concatPath(name, m.uri.toASCIIString)
        else 
          m.uri.toASCIIString
        response.sendRedirect(path)
      case _ => // do nothing
    }
    val out = response.getOutputStream()
//    println(r.asInstanceOf[XmlContent].toHtmlString)
    r.writeClose(out)
  }

  private def _execute(req: ServletHttpRequest, cmd: Command): Content = {
    val parcel = _parcel(req, cmd)
    engine.apply(parcel)
  }

  private def _parcel(
    req: ServletHttpRequest,
    cmd: Command
  ) = {
    val query = req.queryWhole
    val form = req.formWhole
    val ctx = new CozyPlatformExecutionContext(
      platform,
      query,
      form
    )
    val formatter = FormatterContext.default // TODO customizable
    val strategy = RenderStrategy(
      ctx.locale,
      PlainTheme,
      _schema_rule,
      _web_rule(engine),
      Partials.empty,
      Components.empty,
      None,
      RenderContext.empty.withEpilogue.withFormatter(formatter),
      None
    )
    val session = _session(ctx)
    Parcel(
      Some(cmd),
      None,
      Map.empty,
      None,
      None,
      None,
      Some(strategy),
      session,
      Some(ctx),
      None,
      None
    )
  }

  private val _schema_rule: SchemaRule = {
    val base = SchemaBuilder.create(
      CLT(PROP_DOMAIN_OBJECT_ID, "ID", XString), // TODO
      CLejT(PROP_DOMAIN_OBJECT_TITLE, "Title", "タイトル", XString),
      CLejT(PROP_DOMAIN_OBJECT_IMAGE_PRIMARY, "Image", "画像", XImageLink),
      CLejT(PROP_DOMAIN_OBJECT_CONTENT, "Content", "内容", XText)
    )
    val list = base.replaceColumn(PROP_DOMAIN_OBJECT_CONTENT,
      CLejT(PROP_DOMAIN_OBJECT_SUMMARY, "Summary", "概要", XString).toColumn).
      replaceColumn(PROP_DOMAIN_OBJECT_IMAGE_PRIMARY,
        CLejT(PROP_DOMAIN_OBJECT_IMAGE_ICON, "Icon", "アイコン", XImageLink).toColumn)
    val detail = base
    val create = base.removeColumn("id")
    val update = base
    val delete = base
    val medialist = list.removeColumn("id")
    val mediadetail = detail.removeColumn("id")
    val mediacreate = create
    val mediaupdate = update.removeColumn("id")
    val mediadelete = delete.removeColumn("id")
    val consolelist = list
    val consoledetail = detail
    val consolecreate = create
    val consoleupdate = update
    val consoledelete = delete
    val rule = OperationScreenEntityUsageSchemaRule.create(
      MediaOperationMode -> ScreenEntityUsageSchemaRule.create(
        EntityUsageSchemaRule.create(
          UsageSchemaRule.create(
            DetailUsage -> mediadelete,
            ListUsage -> medialist,
            CreateUsage -> mediacreate,
            UpdateUsage -> mediaupdate,
            DeleteUsage -> mediadelete
          )
        )
      ),
      ConsoleOperationMode -> ScreenEntityUsageSchemaRule.create(
        EntityUsageSchemaRule.create(
          UsageSchemaRule.create(
            DetailUsage -> consoledelete,
            ListUsage -> consolelist,
            CreateUsage -> consolecreate,
            UpdateUsage -> consoleupdate,
            DeleteUsage -> consoledelete
          )
        )
      )
    )
    SchemaRule(rule)
  }

  private def _web_rule(engine: WebEngine): WebApplicationRule = _web_rule(engine.application.name)

  private def _web_rule(name: String): WebApplicationRule = WebApplicationRule.empty

  private def _session(ctx: CozyPlatformExecutionContext) =
    if (ctx.isLogined)
      Some(arcadia.context.Session(None))
    else
      None
}
