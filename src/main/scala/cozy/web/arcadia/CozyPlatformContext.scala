package cozy.web.arcadia

import java.io.File
import java.nio.charset.Charset
import org.goldenport.RAISE
import arcadia.context.PlatformContext
import cozy.Context

/*
 * @since   Feb.  5, 2022
 * @version Feb. 28, 2022
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

  def charsetInputFile: Charset = environment.charsetInputFile
  def charsetOutputFile: Charset = environment.charsetOutputFile
  def charsetConsole: Charset = environment.charsetConsole
}
