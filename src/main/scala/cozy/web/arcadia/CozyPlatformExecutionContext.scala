package cozy.web.arcadia

import java.util.Locale
import java.net.URI
import java.nio.charset.Charset
import org.joda.time._
import org.goldenport.RAISE
import org.goldenport.record.v3.IRecord
import org.goldenport.record.v2.Conclusion
import org.goldenport.record.v2.{Schema, Column}
import org.goldenport.values.PathName
import arcadia._
import arcadia.context.PlatformExecutionContext
import arcadia.context.{Request, Response}
import arcadia.context.Query
import arcadia.controller._
import arcadia.view._
import arcadia.model._
import arcadia.domain._
import arcadia.rule._

/*
 * @since   Feb.  5, 2022
 *  version Feb. 28, 2022
 *  version Mar. 20, 2022
 *  version May.  2, 2022
 *  version Nov. 27, 2022
 *  version Mar. 30, 2023
 * @version Apr. 17, 2023
 * @author  ASAMI, Tomoharu
 */
class CozyPlatformExecutionContext(
  val platformContext: CozyPlatformContext,
  query: IRecord,
  form: IRecord
) extends PlatformExecutionContext {
  def locale: Locale = platformContext.locale
  def dateTimeContext = platformContext.dateTimeContext
  def formatContext = platformContext.formatContext
  // def dataTime: DateTime = platformContext.dateTime
  // def timezone: DateTimeZone = platformContext.timezone

  def isLogined: Boolean = false // TODO

  def getOperationName: Option[String] = None

  def getPathName: Option[PathName] = ???

  def getLogicalUri: Option[URI] = ???

  def charsetInputFile: Charset = platformContext.charsetInputFile
  def charsetOutputFile: Charset = platformContext.charsetOutputFile
  def charsetConsole: Charset = platformContext.charsetConsole

  def getImplicitIndexBase: Option[String] = ???

  // def getMimetypeBySuffix(suffix: String): Option[MimeType]

  def getEntitySchema(name: String): Option[Schema] = None

  def getDefaultPropertyColumn(name: String): Option[Column] = None

  def getEntity(
    entity: DomainEntityType,
    id: DomainObjectId
  ): Option[EntityDetailModel] = None

  def readEntityList(p: Query): EntityListModel = ???

  def createEntity(entitytype: DomainEntityType, data: IRecord): DomainObjectId = ???

  def updateEntity(entitytype: DomainEntityType, id: DomainObjectId, data: IRecord): Unit = ???
  def deleteEntity(entitytype: DomainEntityType, id: DomainObjectId): Unit = ???

  def fetchString(urn: UrnSource): Option[String] = ???

  def fetchBadge(urn: UrnSource): Option[Badge] = ???

  def assets: String = ???

  def controllerUri: URI = ???

  def getIdInRequest: Option[DomainObjectId] = ???

  def inputQueryParameters: IRecord = query

  def inputFormParameters: IRecord = form

  def getFormParameter(key: String): Option[String] = ???

  def get(uri: String, query: Map[String, Any] = Map.empty, form: Map[String, Any] = Map.empty): arcadia.context.Response = ???
  def post(uri: String, query: Map[String, Any] = Map.empty, form: Map[String, Any] = Map.empty): arcadia.context.Response = ???
  def put(uri: String, query: Map[String, Any] = Map.empty, form: Map[String, Any] = Map.empty): arcadia.context.Response = ???
  def delete(uri: String, query: Map[String, Any] = Map.empty, form: Map[String, Any] = Map.empty): arcadia.context.Response = ???

  def invoke(op: InvokePlatformCommand): arcadia.context.Response = arcadia.context.Response.notFound()

  def invoke(op: InvokeOperationCommand): arcadia.context.Response = arcadia.context.Response.notFound()

  def execute(cmd: ExecuteScriptCommand): arcadia.context.Response = {
    val service = new CozyScriptService(platformContext, platformContext.httpHandle)
    service.execute(cmd)
  }

  def login(username: String, password: String): Either[Conclusion, arcadia.context.Session] = RAISE.notImplementedYetDefect

  override def resetPassword(
    token: String,
    password: String,
    passwordconfirm: Option[String]
  ): Either[Conclusion, Unit] = ???

  override def getResetPasswordRule: Option[ResetPasswordRule] = ???
}
