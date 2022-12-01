package cozy.web.arcadia

import java.io.File
import java.nio.charset.Charset
import org.goldenport.RAISE
import org.goldenport.kaleidox.http.HttpHandle
import arcadia.context.PlatformContext
import cozy.Context

/*
 * @since   Feb.  5, 2022
 *  version Feb. 28, 2022
 *  version May.  2, 2022
 * @version Nov. 27, 2022
 * @author  ASAMI, Tomoharu
 */
class CozyPlatformContext(
  val cozy: Context
) extends PlatformContext {
  def environment = cozy.environment

  def createTempDirectory = {
    new File(environment.tmpDirectory, s"cozy${environment.dateTimeContext.timestamp}.d")
  }

  def getDevelopDirectory = RAISE.notImplementedYetDefect

  def locale = environment.locale
  def dateTimeContext = environment.dateTimeContext
  def formatContext = environment.formatContext

  def charsetInputFile: Charset = environment.charsetInputFile
  def charsetOutputFile: Charset = environment.charsetOutputFile
  def charsetConsole: Charset = environment.charsetConsole

  def httpHandle: HttpHandle = cozy.createHttpHandle()
}
