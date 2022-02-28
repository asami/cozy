package cozy

import org.goldenport.RAISE
import org.goldenport.i18n.I18NString
import org.goldenport.cli.{Config => CliConfig, Environment}
import org.goldenport.value._

/*
 * @since   Dec.  4, 2021
 * @version Feb.  6, 2022
 * @author  ASAMI, Tomoharu
 */
case class Config(
  cliConfig: CliConfig,
  isLocation: Boolean = true
) {
  def properties = cliConfig.properties.config
}

object Config {
  def create(env: Environment): Config = {
    Config(
      env.config
    )
  }
}
