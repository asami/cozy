package cozy.web.arcadia

import scala.xml._
import org.fusesource.scalate.TemplateSource
import arcadia.context._
import arcadia.view._
import arcadia.view.ViewEngine.Bindings

/*
 * @since   Sep. 10, 2022
 *  version Sep. 25, 2022
 * @version May.  3, 2025
 * @author  ASAMI, Tomoharu
 */
class DoxTemplateEngine(val platform: PlatformContext) extends TemplateEngine {
  def isAccept(p: TemplateSource): Boolean = p.templateType.fold(false) {
    case "dox" => true
    case _ => false
  }
  def shutdown(): Unit = {}
  def layoutAsNodes(template: TemplateSource, bindings: Bindings): NodeSeq =
    ???
}

object DoxTemplateEngine extends TemplateEngine.Factory {
  def create(platform: PlatformContext) = new DoxTemplateEngine(platform)
}
