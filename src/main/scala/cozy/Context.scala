package cozy

import java.io.File
import java.net.URL
import org.goldenport.RAISE
import org.goldenport.i18n.I18NString
import org.goldenport.cli.{Config => CliConfig, Environment}
import org.goldenport.cli.OperationCall
import org.goldenport.cli.spec
import org.goldenport.recorder.{ForwardRecorder, Recorder}
import org.goldenport.kaleidox.Kaleidox
import org.goldenport.kaleidox.Model
import org.goldenport.kaleidox.http.HttpHandle
import arcadia.context.PlatformContext.Mode

/*
 * @since   Dec.  4, 2021
 *  version Dec. 18, 2021
 *  version Feb. 28, 2022
 *  version Mar.  6, 2022
 *  version Dec. 25, 2022
 * @version Jan. 29, 2023
 * @author  ASAMI, Tomoharu
 */
case class Context(
  val environment: Environment,
  val config: Config,
  val kaleidox: Kaleidox
) extends Environment.AppEnvironment with ForwardRecorder {
  protected def forward_Recorder: Recorder = recorder

  def recorder = environment.recorder
  def isPlatformWindows: Boolean = environment.isPlatformWindows

  def mode: Mode = config.mode

  def createHttpHandle(): HttpHandle = {
    val args = Array[String]()
    val req = spec.Request.empty
    val res = spec.Response()
    val op = spec.Operation("cozy", req, res)
    val call = OperationCall.create(op, args)
    kaleidox.http(call)
  }

  def getAppResource(path: String): Option[URL] = environment.getAppResource(this, path)

  def getClassResource(o: Object, path: String): Option[URL] = environment.getClassResource(o, path)

  def loadModel(p: File): Model = Model.load(kaleidox.config, p)
}
