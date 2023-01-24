package cozy.web.arcadia

import scalaz._, Scalaz._
import java.io.File
import org.goldenport.context.Consequence
import org.goldenport.kaleidox.{Engine => KaleidoxEngine}
import org.goldenport.kaleidox.Model
import org.goldenport.kaleidox.model.EntityModel
import org.goldenport.sexpr._
import org.goldenport.record.v3.Record
import org.goldenport.record.v3.IRecord
import arcadia.context.Query
import arcadia.domain._
import arcadia.model._
import cozy.Context

/*
 * @since   Dec. 25, 2022
 *  version Dec. 31, 2022
 * @version Jan. 24, 2023
 * @author  ASAMI, Tomoharu
 */
class CozyDomainModelSpace(
  kaleidox: KaleidoxEngine,
  model: Model
) extends DomainModelSpace {
  import CozyDomainModel._

  private var _entity_model: Option[EntityModel] = None

  def onStart() {
    kaleidox.setup(model)
    _entity_model = model.getEntityModel
    _entity_model.foreach(_build)
  }

  private def _build(p: EntityModel) {
    p.classes map {
      case (name, entityclass) => classes.setContent(name, CozyDomainClass(entityclass))
    }
  }

  def getEntity(
    entitytype: DomainEntityType,
    id: DomainObjectId
  ): Option[Consequence[Option[EntityDetailModel]]] = {
    val collection = entitytype.v
    val s = s"(entity-get '${collection} ${id})"
    val expr = kaleidox.applyModelScript(model, s)
    val r = expr match {
      case m: SError => Consequence.error(m.conclusion)
      case m: SEntity => Consequence.success(Some(_entity_model(entitytype, m)))
      case m => Consequence.internalServerError(s"Unknown entity: $m")
    }
    Some(r)
  }

  private def _entity_model(entitytype: DomainEntityType, p: SEntity): EntityDetailModel = {
    val rec = p.record
    EntityDetailModel(entitytype, rec)
  }

  def readEntityList(q: Query): Option[Consequence[EntityListModel]] = {
    val collection = q.entityType.v
    val s = s"(entity-query '${collection})" // entity-select
    val expr = kaleidox.applyModelScript(model, s)
    var r = expr match {
      case SVector(xs) => _entity_list_model(q, q.entityType, xs)
      case m: SError => Consequence.error(m.conclusion)
      case m => Consequence.internalServerError(s"Unknown entity: $m")
    }
    Some(r)
  }

  private def _entity_list_model(
    q: Query,
    klass: DomainEntityType,
    ps: Seq[SExpr]
  ): Consequence[EntityListModel] = {
    for {
      xs <- ps.toList.traverse(_entity_record)
    } yield {
      val xfer = Transfer.create(q, xs)
      EntityListModel(klass, xs, xfer)
    }
  }

  private def _entity_record(p: SExpr): Consequence[IRecord] = p match {
    case m: SEntity => Consequence.success(m.record)
    case m => Consequence.internalServerError(s"Unknown entity: $m")
  }
}

object CozyDomainModel {
  case class CozyDomainClass(clazz: EntityModel.EntityClass) extends DomainClass {
  }

  def create(engine: KaleidoxEngine, p: Model): CozyDomainModelSpace = {
    val r = new CozyDomainModelSpace(engine, p)
    r.onStart()
    r
  }
}
