package cozy.web.arcadia

import scala.util.control.NonFatal
import java.io.File
import arcadia.domain._
import cozy.Context

/*
 * @since   Dec. 25, 2022
 *  version Dec. 25, 2022
 * @version Jan.  1, 2023
 * @author  ASAMI, Tomoharu
 */
class DomainModelFactory(
  val ctx: Context
) extends DomainModelSpace.Factory {
  def parse(p: File): Option[DomainModel] = try {
    val a = ctx.loadModel(p)
    Some(DomainModel(CozyDomainModel.create(ctx.kaleidox.createEngine(), a)))
  } catch {
    case NonFatal(e) => None // TODO
  }
}

object DomainModelFactory {
  def create(ctx: Context): DomainModelFactory = new DomainModelFactory(ctx)
}
