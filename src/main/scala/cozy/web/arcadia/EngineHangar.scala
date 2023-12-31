package cozy.web.arcadia

import java.net.URL
import com.typesafe.config.{Config => Hocon}
import scala.collection.concurrent.TrieMap
import arcadia._
import arcadia.context._
import arcadia.view.TemplateEngineHangar
import cozy.Cozy
import cozy.Context
import cozy.modeler.{DomainModelFactory => MDomainModelFactory}

/*
 * @since   Feb.  6, 2022
 *  version Feb. 28, 2022
 *  version Sep. 25, 2022
 *  version Oct. 30, 2022
 *  version Dec. 25, 2022
 *  version Jan.  1, 2023
 * @version Dec. 28, 2023
 * @author  ASAMI, Tomoharu
 */
class EngineHangar(
  val platformContext: CozyPlatformContext,
  val config: Hocon,
  val webengineconfig: WebEngine.Config
) extends {
  private lazy val _arcadia = {
    Arcadia.make(platformContext, webengineconfig, config).take
  }

  private val _web_engines = new TrieMap[String, Engine]()

  def apply(name: String): Engine = _web_engines.get(name) getOrElse {
    val a = _arcadia.engine(name)
    val r = new Engine(platformContext, a, name)
    _web_engines += (name -> r)
    r
  }
}

object EngineHangar {
  def create(ctx: Context): EngineHangar = {
    val pc = new CozyPlatformContext(ctx)
    val services = List(CozyScriptService.create(ctx, pc))
    val webengineconfig = WebEngine.Config(
      TemplateEngineHangar.Factory(DoxTemplateEngine),
      MDomainModelFactory.create(ctx),
      services
    )
    new EngineHangar(pc, ctx.config.properties, webengineconfig)
  }
}
