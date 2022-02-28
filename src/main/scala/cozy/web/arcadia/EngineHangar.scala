package cozy.web.arcadia

import java.net.URL
import com.typesafe.config.{Config => Hocon}
import scala.collection.concurrent.TrieMap
import arcadia._
import arcadia.context._
import cozy.Context

/*
 * @since   Feb.  6, 2022
 * @version Feb. 28, 2022
 * @author  ASAMI, Tomoharu
 */
class EngineHangar(
  val platformContext: CozyPlatformContext,
  val config: Hocon
) extends {
  private lazy val _arcadia = {
    Arcadia.make(platformContext, config).take
  }

  private val _web_engines = new TrieMap[String, Engine]()

  def apply(name: String): Engine = _web_engines.get(name) getOrElse {
    val a = _arcadia.engine(name)
    val r = new Engine(platformContext, a)
    _web_engines += (name -> r)
    r
  }
}

object EngineHangar {
  def create(cozy: Context): EngineHangar = {
    val pc = new CozyPlatformContext(cozy)
    new EngineHangar(pc, cozy.config.properties)
  }
}
