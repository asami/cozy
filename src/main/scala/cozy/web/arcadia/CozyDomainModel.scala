package cozy.web.arcadia

import java.io.File
import org.goldenport.kaleidox.Model
import org.goldenport.kaleidox.model.EntityModel
import arcadia.domain._
import cozy.Context

/*
 * @since   Dec. 25, 2022
 * @version Dec. 29, 2022
 * @author  ASAMI, Tomoharu
 */
class CozyDomainModel(
  model: Model
) extends DomainModel {
  import CozyDomainModel._

  private var _entity_model: Option[EntityModel] = None

  def onStart() {
    _entity_model = model.getEntityModel
    _entity_model.foreach(_build)

  }

  private def _build(p: EntityModel) {
    p.classes map {
      case (name, entityclass) => classes.setContent(name, CozyDomainClass(entityclass))
    }
  }
}

object CozyDomainModel {
  case class CozyDomainClass(clazz: EntityModel.EntityClass) extends DomainClass {
  }

  def create(p: Model): CozyDomainModel = {
    val r = new CozyDomainModel(p)
    r.onStart()
    r
  }
}
