package cozy.web.arcadia

import play.api.libs.json.{JsValue, JsNull}
import org.goldenport.RAISE
import org.goldenport.context.StatusCode
import org.goldenport.kaleidox.http._
import org.goldenport.record.v3.IRecord
import org.goldenport.record.http.Request.Method
import org.goldenport.values.PathName
import org.goldenport.sexpr.SExpr
import org.goldenport.sexpr.SError
import org.goldenport.sexpr.{SList, SVector, STable}
import org.goldenport.kaleidox.Kaleidox
import org.goldenport.kaleidox.Expression
import org.goldenport.kaleidox.LispExpression
import arcadia._
import arcadia.context._
import arcadia.service.Service
import arcadia.model.Transfer
import arcadia.domain.DomainEntityType
import cozy.Context

/*
 * @since   Oct. 23, 2022
 *  version Oct. 30, 2022
 * @version Nov.  7, 2022
 * @author  ASAMI, Tomoharu
 */
class CozyScriptService(
  val platform: PlatformContext,
  val handle: HttpHandle
) extends Service {
  import CozyScriptService._

  def invoke(op: InvokeOperationCommand): Response = invoke_engine(op)

  protected def invoke_handle(op: InvokeOperationCommand): Response = {
    val req = ArcadiaHttpRequest(op.request)
    val res = handle.execute(req, HttpResponse.ok)
    KaleidoxResponse(res)
  }

  protected def invoke_engine(op: InvokeOperationCommand): Response = {
    val req = ArcadiaHttpRequest(op.request)
    val (report, rs, universe) = handle.eval(req)
    CozyResponse(rs)
  }
}

object CozyScriptService {
  def create(ctx: Context, platform: PlatformContext) =
    new CozyScriptService(platform, ctx.createHttpHandle())

  case class ArcadiaHttpRequest(request: Request) extends HttpRequest {
    def pathname: PathName = PathName(request.operationName)
    def method: Method = Method(request.method)
    def queryWhole: IRecord = request.query
    def formWhole: IRecord = request.form
  }

  case class KaleidoxResponse(response: HttpResponse) extends Response {
    def code: Int = response.status
    def mime: String = MimeType.APPLICATION_JSON.name
    def entityType: Option[DomainEntityType] = None
    // TODO response.content
    def getString: Option[String] = response.getValue.flatMap(x =>
      if (getRecord.nonEmpty || getRecords.nonEmpty)
        None
      else
        Some(x.print)
    )
    lazy val getRecord: Option[IRecord] = response.getValue.collect {
      case m: STable => RAISE.notImplementedYetDefect
    }
    lazy val getRecords: Option[List[IRecord]] = response.getValue.collect {
      case m: SList => RAISE.notImplementedYetDefect
      case m: SVector => RAISE.notImplementedYetDefect
    }
    def transfer: Option[Transfer] = None
    def json: JsValue = JsNull
  }

  sealed trait CozyResponse extends Response {
    def code: Int = StatusCode.Ok.code
    def mime: String = MimeType.APPLICATION_JSON.name
    def entityType: Option[DomainEntityType] = None
    def getString: Option[String] = None
    def getRecord: Option[IRecord] = None
    def getRecords: Option[List[IRecord]] = None
    def transfer: Option[Transfer] = None
    def json: JsValue = JsNull
  }
  object CozyResponse {
    case object Ok extends CozyResponse {
    }

    case class Table(result: STable) extends CozyResponse {
      override def getRecord: Option[IRecord] = RAISE.notImplementedYetDefect
    }

    case class Sequence(result: Vector[SExpr]) extends CozyResponse {
      override def getRecords: Option[List[IRecord]] = RAISE.notImplementedYetDefect
    }

    case class Value(result: SExpr) extends CozyResponse {
      override def getString: Option[String] = Some(result.print)
    }

    case class Error(error: SError) extends CozyResponse {
      override def code: Int = error.conclusion.code
      override def getString: Option[String] = Some(error.conclusion.message)
    }

    case class Panic(ps: Seq[Expression]) extends CozyResponse {
      override def code: Int = StatusCode.InternalServerError.code
    }

    def apply(ps: Seq[Expression]): CozyResponse = ps match {
      case Seq() => Ok
      case Seq(x) => x match {
        case LispExpression(s) => s match {
          case m: SError => Error(m)
          case m: STable => Table(m)
          case m: SList => Sequence(m.vector)
          case m: SVector => Sequence(m.vector)
          case m => Value(m)
        }
        case _ => Panic(ps)
      }
      case m: Seq[Expression] => Panic(m)
    }
  }
}
