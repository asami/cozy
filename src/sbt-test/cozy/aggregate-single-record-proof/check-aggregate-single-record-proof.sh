#!/bin/sh
set -eu

script_dir=$(CDPATH= cd -- "$(dirname "$0")" && pwd)
sample_dir=/Users/asami/src/dev2026/cncf-samples/samples/09.a-aggregate-single-record-lab
out_dir="$script_dir/out.d"
cml_file="$sample_dir/src/main/cozy/order-single-record-aggregate.cml"
demo_dst_dir="$out_dir/src/main/scala/org/sample/aggregatesinglerecord"
single_record_src="$demo_dst_dir/SingleRecordAggregateDemo.scala"
datastore_src="$demo_dst_dir/SingleRecordAggregateDatastoreDemo.scala"

rm -rf "$out_dir"
"$script_dir/cozy-delegate.sh" modeler-scala "$cml_file" --save="$out_dir"
mkdir -p "$demo_dst_dir"
cat > "$single_record_src" <<'SCALA'
package org.sample.aggregatesinglerecord

import io.circe.syntax.*
import io.circe.Json
import org.goldenport.record.Record
import org.goldenport.datatype.Name
import org.sample.aggregatesinglerecord.value.OrderLine
import org.sample.aggregatesinglerecord.entity.Order
import org.simplemodeling.model.datatype.EntityId

object SingleRecordAggregateDemo {
  def main(args: Array[String]): Unit = {
    val line1 = OrderLine.create(Name("Widget"), 2)
    val line2 = OrderLine.create(Name("Cable"), 1)
    val order = Order.Builder()
      .withId(EntityId.parse("major-minor-entity-order-20260330000000-aaa111").TAKE)
      .withName(Name("Alpha"))
      .withStatus("Active")
      .withLines(Vector(line1, line2))
      .build()
    val record = order.toRecord()
    val restored = Order.createC(record).TAKE
    val payload = Map[String, io.circe.Json](
      "pattern" -> "single-record-aggregate".asJson,
      "entity" -> "Order".asJson,
      "value-object" -> "OrderLine".asJson,
      "record" -> _recordJson(record),
      "restored" -> _recordJson(restored.toRecord()),
      "line-count" -> restored.lines.size.asJson
    )
    println(payload.asJson.noSpaces)
  }

  private def _recordJson(p: Record): Json =
    Json.obj(p.asMap.iterator.map { case (k, v) => k -> _json(v) }.toSeq: _*)

  private def _json(v: Any): Json = v match {
    case null => Json.Null
    case m: String => m.asJson
    case m: Int => m.asJson
    case m: Long => m.asJson
    case m: Double => m.asJson
    case m: Float => m.toDouble.asJson
    case m: BigDecimal => m.asJson
    case m: BigInt => m.asJson
    case m: Boolean => m.asJson
    case m: Record => _recordJson(m)
    case m: Seq[?] => Json.arr(m.iterator.map(_json).toSeq: _*)
    case m: Array[?] => Json.arr(m.iterator.map(_json).toSeq: _*)
    case m: Map[?, ?] => Json.obj(m.iterator.map { case (k, value) => k.toString -> _json(value) }.toSeq: _*)
    case m: org.goldenport.text.Presentable => m.print.asJson
    case other => other.toString.asJson
  }
}
SCALA
cat > "$datastore_src" <<'SCALA'
package org.sample.aggregatesinglerecord

import cats.~>
import io.circe.Json
import io.circe.syntax.*
import org.sample.aggregatesinglerecord.value.OrderLine
import org.goldenport.Consequence
import org.goldenport.cncf.context.{CorrelationId, DataStoreContext, EntityStoreContext, ExecutionContext, ObservabilityContext, RuntimeContext, ScopeContext, ScopeKind, TraceId}
import org.goldenport.cncf.datastore.DataStoreSpace
import org.goldenport.cncf.entity.{EntityStore, EntityStoreSpace}
import org.goldenport.cncf.entity.EntityPersistentCreate
import org.goldenport.cncf.http.FakeHttpDriver
import org.goldenport.cncf.unitofwork.{UnitOfWork, UnitOfWorkOp}
import org.goldenport.datatype.Name
import org.goldenport.record.Record
import org.sample.aggregatesinglerecord.entity.Order
import org.sample.aggregatesinglerecord.entity.Order.given
import org.simplemodeling.model.datatype.EntityId

object SingleRecordAggregateDatastoreDemo {
  def main(args: Array[String]): Unit = {
    val line1 = OrderLine.create(Name("Widget"), 2)
    val line2 = OrderLine.create(Name("Cable"), 1)
    val order = Order.Builder()
      .withId(EntityId.parse("major-minor-entity-order-20260330000000-bbb222").TAKE)
      .withName(Name("Datastore"))
      .withStatus("Active")
      .withLines(Vector(line1, line2))
      .build()

    val datastorespace = DataStoreSpace.default()
    val entitystorespace = new EntityStoreSpace().addEntityStore(EntityStore.standard())
    given ExecutionContext = _executionContext(datastorespace, entitystorespace)
    given EntityPersistentCreate[Order] = new EntityPersistentCreate[Order] {
      def id(e: Order): Option[EntityId] = Some(e.id)
      def toRecord(e: Order): Record = e.toDataStore()
      def collection(e: Order) = summon[org.goldenport.cncf.entity.EntityPersistent[Order]].id(e).collection
    }

    val _ = entitystorespace.create(UnitOfWorkOp.EntityStoreCreate(order, summon)).TAKE
    val loaded = entitystorespace.load(UnitOfWorkOp.EntityStoreLoad(order.id, summon)).TAKE.getOrElse {
      sys.error("saved order not found")
    }

    val payload = Json.obj(
      "pattern" -> "single-record-aggregate-datastore".asJson,
      "saved" -> _recordJson(order.toRecord()),
      "loaded" -> _recordJson(loaded.toRecord()),
      "line-count" -> loaded.lines.size.asJson
    )
    println(payload.noSpaces)
  }

  private def _executionContext(
    datastorespace: DataStoreSpace,
    entitystorespace: EntityStoreSpace
  ): ExecutionContext = {
    val observability = ObservabilityContext(
      traceId = TraceId("test", "single_record_aggregate_datastore"),
      spanId = None,
      correlationId = Some(CorrelationId("test", "single_record_aggregate_datastore"))
    )
    val driver = FakeHttpDriver.okText("nop")
    lazy val context: ExecutionContext = ExecutionContext.create(runtime)
    lazy val runtime: RuntimeContext = new RuntimeContext(
      core = ScopeContext.Core(
        kind = ScopeKind.Runtime,
        name = "single-record-aggregate-datastore-runtime",
        parent = None,
        observabilityContext = observability,
        httpDriverOption = Some(driver),
        datastore = Some(DataStoreContext(datastorespace)),
        entitystore = Some(EntityStoreContext(entitystorespace))
      ),
      unitOfWorkSupplier = () => new UnitOfWork(context),
      unitOfWorkInterpreterFn = new (UnitOfWorkOp ~> Consequence) {
        def apply[A](fa: UnitOfWorkOp[A]): Consequence[A] = {
          val _ = fa
          throw new UnsupportedOperationException("unitOfWorkInterpreter is not used in datastore demo")
        }
      },
      commitAction = uow => { val _ = uow.commit(); () },
      abortAction = uow => { val _ = uow.rollback(); () },
      disposeAction = _ => (),
      token = "single-record-aggregate-datastore-runtime-context"
    )
    context
  }

  private def _recordJson(p: Record): Json =
    Json.obj(p.asMap.iterator.map { case (k, v) => k -> _json(v) }.toSeq: _*)

  private def _json(v: Any): Json = v match {
    case null => Json.Null
    case m: String => m.asJson
    case m: Int => m.asJson
    case m: Long => m.asJson
    case m: Double => m.asJson
    case m: Float => m.toDouble.asJson
    case m: BigDecimal => m.asJson
    case m: BigInt => m.asJson
    case m: Boolean => m.asJson
    case m: Record => _recordJson(m)
    case m: Seq[?] => Json.arr(m.iterator.map(_json).toSeq: _*)
    case m: Array[?] => Json.arr(m.iterator.map(_json).toSeq: _*)
    case m: Map[?, ?] => Json.obj(m.iterator.map { case (k, value) => k.toString -> _json(value) }.toSeq: _*)
    case m: org.goldenport.text.Presentable => m.print.asJson
    case other => other.toString.asJson
  }
}
SCALA

(
  cd "$out_dir"
  sbt --batch compile
)

single_result="$(cd "$out_dir" && sbt --batch 'runMain org.sample.aggregatesinglerecord.SingleRecordAggregateDemo' 2>&1 | grep '^{' | tail -n 1)"
datastore_result="$(cd "$out_dir" && sbt --batch 'runMain org.sample.aggregatesinglerecord.SingleRecordAggregateDatastoreDemo' 2>&1 | grep '^{' | tail -n 1)"

printf '%s\n' "$single_result" | grep '"pattern":"single-record-aggregate"'
printf '%s\n' "$single_result" | grep '"line-count":2'
printf '%s\n' "$single_result" | grep '"value-object":"OrderLine"'
printf '%s\n' "$datastore_result" | grep '"pattern":"single-record-aggregate-datastore"'
printf '%s\n' "$datastore_result" | grep '"line-count":2'
printf '%s\n' "$datastore_result" | grep '"loaded"'

echo AGGREGATE_SINGLE_RECORD_PROOF_OK
