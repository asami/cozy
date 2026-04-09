#!/bin/sh
set -eu

script_dir=$(CDPATH= cd -- "$(dirname "$0")" && pwd)
sample_dir=/Users/asami/src/dev2026/cncf-samples/samples/07-aggregate
out_dir="$script_dir/out.d"
cml_file="$sample_dir/src/main/cozy/order-aggregate.cml"
impl_src="$sample_dir/src/main/scala/org/sample/aggregate/impl/AggregateSampleComponentFactory.scala"
impl_dst_dir="$out_dir/src/main/scala/org/sample/aggregate/impl"
demo_dst_dir="$out_dir/src/main/scala/org/sample/aggregate"
demo_src="$demo_dst_dir/OrderAggregateDemo.scala"

rm -rf "$out_dir"
"$script_dir/cozy-delegate.sh" modeler-scala "$cml_file" --save="$out_dir"
mkdir -p "$impl_dst_dir" "$demo_dst_dir"
cp "$impl_src" "$impl_dst_dir/AggregateSampleComponentFactory.scala"
cat > "$demo_src" <<'SCALA'
package org.sample.aggregate

import io.circe.Json
import org.goldenport.Consequence
import org.goldenport.protocol.{Property, Request}
import org.goldenport.cncf.cli.CncfRuntime
import org.goldenport.cncf.cli.RunMode
import org.goldenport.cncf.component.{ComponentCreate, ComponentFactory, ComponentOrigin}

object OrderAggregateDemo {
  private val IdPattern = "(?m)^id:\\s*(\\S+)\\s*$".r

  def main(args: Array[String]): Unit = {
    val runtime = new CncfRuntime
    val subsystem = runtime.initializeForEmbedding(modeHint = Some(RunMode.Command)).TAKE
    val customFactory = new impl.AggregateSampleComponentFactory
    val initialized = customFactory.create(ComponentCreate(subsystem, ComponentOrigin.Builtin))
    val _ = subsystem.add(Vector(ComponentFactory().bootstrap(initialized.head)))
    try {
      val orderId = _createOrder(subsystem)
      val addLineText = _addLine(subsystem, orderId, "Widget", 2)
      val invalidAddLine = _executeFailureString(
        subsystem,
        Request.of(
          component = "AggregateSample",
          service = "Order",
          operation = "addLine",
          properties = List(
            Property("textus.runtime.command.execution-mode", "sync-direct-no-job", None),
            Property("orderId", orderId, None),
            Property("lineName", "Broken", None),
            Property("quantity", "0", None)
          )
        )
      )
      val loadText = _executeString(
        subsystem,
        Request.of(
          component = "AggregateSample",
          service = "Order",
          operation = "loadOrderAggregate",
          properties = List(Property("id", orderId, None))
        )
      )
      println(
        Json.obj(
          "orderId" -> Json.fromString(orderId),
          "addLine" -> Json.fromString(addLineText),
          "invalidAddLine" -> Json.fromString(invalidAddLine),
          "load" -> Json.fromString(loadText)
        ).noSpaces
      )
    } finally {
      runtime.closeEmbedding()
    }
  }

  private def _createOrder(subsystem: org.goldenport.cncf.subsystem.Subsystem): String = {
    val text = _executeString(
      subsystem,
      Request.of(
        component = "AggregateSample",
        service = "entity",
        operation = "createOrderRecord",
        properties = List(
          Property("textus.runtime.command.execution-mode", "sync-direct-no-job", None),
          Property("name", "Alpha", None),
          Property("status", "Active", None)
        )
      )
    )
    _extractId(text)
  }

  private def _addLine(
    subsystem: org.goldenport.cncf.subsystem.Subsystem,
    orderId: String,
    lineName: String,
    quantity: Int
  ): String =
    _executeString(
      subsystem,
      Request.of(
        component = "AggregateSample",
        service = "Order",
        operation = "addLine",
        properties = List(
          Property("textus.runtime.command.execution-mode", "sync-direct-no-job", None),
          Property("orderId", orderId, None),
          Property("lineName", lineName, None),
          Property("quantity", quantity.toString, None)
        )
      )
    )

  private def _executeString(
    subsystem: org.goldenport.cncf.subsystem.Subsystem,
    request: Request
  ): String =
    subsystem.execute(request) match {
      case Consequence.Success(response) => response.print
      case Consequence.Failure(c) => throw new IllegalStateException(c.show)
    }

  private def _executeFailureString(
    subsystem: org.goldenport.cncf.subsystem.Subsystem,
    request: Request
  ): String =
    subsystem.execute(request) match {
      case Consequence.Success(response) =>
        throw new IllegalStateException(s"Expected failure but got success: ${response.print}")
      case Consequence.Failure(c) =>
        c.show
    }

  private def _extractId(text: String): String =
    IdPattern.findFirstMatchIn(text).map(_.group(1)).getOrElse {
      throw new IllegalStateException(s"Missing id in response: $text")
    }
}
SCALA

(
  cd "$out_dir"
  sbt --batch compile
)

result="$(cd "$out_dir" && sbt --batch 'runMain org.sample.aggregate.OrderAggregateDemo' 2>&1 | grep '^{' | tail -n 1)"
printf '%s\n' "$result" | grep '"orderId":"major-minor-entity-order-'
printf '%s\n' "$result" | grep '"invalidAddLine"'
printf '%s\n' "$result" | grep 'quantityPositive'
printf '%s\n' "$result" | grep '"load":"id: '
printf '%s\n' "$result" | grep 'lines:'

echo AGGREGATE_DEMO_PROOF_OK
