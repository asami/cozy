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
import org.goldenport.record.v2.Schema
import arcadia.context.Query
import arcadia.domain._
import arcadia.model._
import cozy.Context

/*
 * @since   Dec. 25, 2022
 *  version Dec. 31, 2022
 *  version Jan. 24, 2023
 *  version Feb. 28, 2023
 *  version Mar. 30, 2023
 *  version Apr. 22, 2023
 *  version Sep. 30, 2023
 * @version Oct.  1, 2023
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

  def getEntitySchema(entitytype: DomainEntityType): Option[Schema] =
    model.getEntityModel.flatMap(_.getSchema(entitytype.name))

  def getEntity(
    entitytype: DomainEntityType,
    id: DomainObjectId
  ): Option[Consequence[Option[EntityDetailModel]]] = {
    val collection = entitytype.name
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
    val collection = q.entityType.name
    val offset = s":offset ${q.offset}"
    val limit = s":limit ${q.limit}"
    val columns = q.columns.fold("")(x => s""":columns "${x.mkString(",")}"""")
    val s = s"(entity-select '${collection} $offset $limit $columns)"
    val expr = kaleidox.applyModelScript(model, s)
    var r = expr match {
      case SVector(xs) => _entity_list_model(q, q.entityType, xs)
      case m: STable => _entity_list_model(q, q.entityType, m.vector.vector)
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
    case m: SEntity => Consequence.success(m.recordWithShortId)
    case SRecord(r) => Consequence.success(r)
    case m => Consequence.internalServerError(s"Unknown entity: $m")
  }

  def createEntity(
    entitytype: DomainEntityType,
    record: IRecord
  ): Option[Consequence[DomainObjectId]] = {
    val script = SList.createAtomQAtomRecord("entity-create", entitytype.name, record)
    val expr = kaleidox.applyModelScript(model, script)
    val r = expr match {
      case m: SEntity => Consequence.success(DomainObjectId(m.entityId.objectId.string))
      case m: SString => Consequence.success(DomainObjectId(m.string))
      case m: SError => Consequence.error(m.conclusion)
      case m => Consequence.internalServerError(s"Unknown entity: $m")
    }
    Some(r)
  }

  def updateEntity(
    entitytype: DomainEntityType,
    id: DomainObjectId,
    data: IRecord
  ): Option[Consequence[Unit]] = {
    val script = SList.createAtomQAtomStringRecord("entity-update", entitytype.name, id.v, data)
    val expr = kaleidox.applyModelScript(model, script)
    val r = expr match {
      case m: SError => Consequence.error(m.conclusion)
      case m => Consequence.unit
    }
    Some(r)
  }

  def deleteEntity(
    entitytype: DomainEntityType,
    id: DomainObjectId
  ): Option[Consequence[Unit]] = {
    val script = SList.createAtomQAtomString("entity-delete", entitytype.name, id.v)
    val expr = kaleidox.applyModelScript(model, script)
    val r = expr match {
      case m: SError => Consequence.error(m.conclusion)
      case m => Consequence.unit
    }
    Some(r)
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
