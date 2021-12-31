package cozy

import org.goldenport.RAISE
import org.goldenport.i18n.I18NString
import org.goldenport.cli.{Config => CliConfig, Environment}
import org.goldenport.recorder.{ForwardRecorder, Recorder}
import org.goldenport.kaleidox.Kaleidox

/*
 * @since   Dec.  4, 2021
 * @version Dec. 18, 2021
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
}
