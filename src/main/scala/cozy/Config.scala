package cozy

import org.goldenport.RAISE
import org.goldenport.i18n.I18NString
import org.goldenport.cli.{Config => CliConfig, Environment}
import org.goldenport.value._

/*
 * @since   Dec.  4, 2021
 * @version Dec.  4, 2021
 * @author  ASAMI, Tomoharu
 */
case class Config(
  cliConfig: CliConfig,
  isLocation: Boolean = true
) {
}

object Config {
  def create(env: Environment): Config = {
    Config(
      env.config
    )
  }
}
