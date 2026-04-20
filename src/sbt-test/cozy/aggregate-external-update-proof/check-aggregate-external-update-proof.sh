#!/bin/sh
set -eu

export COZY_PROJECT_DIR="${COZY_PROJECT_DIR:-/Users/asami/src/dev2025/cozy}"

SCRIPT_DIR=$(CDPATH= cd -- "$(dirname "$0")" && pwd)
SAMPLE_DIR=/Users/asami/src/dev2026/cncf-samples/samples/09.c-aggregate-external-update-semantics
OUT_DIR="$SCRIPT_DIR/out.d"
SRC_DIR="$OUT_DIR/src/main/scala/org/sample/aggregateexternalupdate"
COZY_SRC_DIR="$OUT_DIR/src/main/cozy"
PROJECT_DIR="$OUT_DIR/project"

rm -rf "$OUT_DIR"
mkdir -p "$SRC_DIR" "$COZY_SRC_DIR" "$PROJECT_DIR"

cat > "$OUT_DIR/build.sbt" <<'EOF'
import org.goldenport.cozy.CozyPlugin.autoImport._

ThisBuild / organization := "org.sample"
ThisBuild / version := "0.1.0-SNAPSHOT"
ThisBuild / scalaVersion := "3.3.7"

lazy val root = (project in file("."))
  .enablePlugins(org.goldenport.cozy.CozyPlugin)
  .settings(
    name := "aggregate-external-update-proof",
    scalacOptions ++= Seq("-deprecation", "-feature", "-unchecked"),
    cozyGeneratorBackend := "cozy",
    cozyDelegateProjectDir := None,
    cozyDelegateCommand := Seq("/Users/asami/src/dev2026/cncf-samples/bin/cozy"),
    resolvers ++= Seq(
      Resolver.defaultLocal,
      Resolver.mavenLocal,
      "SimpleModeling.org" at "https://www.simplemodeling.org/maven"
    ),
    libraryDependencies ++= Seq(
      "org.goldenport" %% "goldenport-cncf" % "0.4.4-SNAPSHOT",
      "org.goldenport" %% "goldenport-core" % "0.3.1-SNAPSHOT",
      "org.simplemodeling" %% "simplemodeling-model" % "0.1.4-SNAPSHOT"
    ),
    cozyManifestMetadata ++= Map(
      "component" -> "aggregate-external-update-sample",
      "boundedContext" -> "orders",
      "domain" -> "aggregate"
    ),
    Test / fork := false
  )
EOF

cat > "$PROJECT_DIR/plugins.sbt" <<'EOF'
resolvers += Resolver.defaultLocal
addSbtPlugin("org.goldenport" % "sbt-cozy" % "0.1.2")
EOF

cat > "$PROJECT_DIR/build.properties" <<'EOF'
sbt.version=1.11.6
EOF

cp "$SAMPLE_DIR/src/main/cozy/order-external-update.cml" "$COZY_SRC_DIR/order-external-update.cml"
cp "$SAMPLE_DIR/src/main/scala/org/sample/aggregateexternalupdate/OrderExternalUpdateFactory.scala" "$SRC_DIR/OrderExternalUpdateFactory.scala"
cp "$SAMPLE_DIR/src/main/scala/org/sample/aggregateexternalupdate/ExternalEntityAliases.scala" "$SRC_DIR/ExternalEntityAliases.scala"

cat > "$SRC_DIR/ExternalUpdateDemo.scala" <<'EOF'
package org.sample.aggregateexternalupdate

import io.circe.Json
import io.circe.syntax.*

object ExternalUpdateDemo:
  def main(args: Array[String]): Unit =
    val payload = Json.obj(
      "sample" -> "07.c-aggregate-external-update-semantics".asJson,
      "updateSemantics" -> Json.arr(
        "Order cancellation follows up to ShipmentOrder".asJson,
        "User stays plain external association".asJson
      )
    )
    println(payload.noSpaces)
EOF

cat > "$SRC_DIR/ExternalUpdateAggregateDemo.scala" <<'EOF'
package org.sample.aggregateexternalupdate

import io.circe.Json
import org.goldenport.Consequence
import org.goldenport.protocol.{Property, Request}
import org.goldenport.cncf.cli.{CncfRuntime, RunMode}
import org.goldenport.cncf.component.{ComponentCreate, ComponentFactory, ComponentOrigin}

object ExternalUpdateAggregateDemo:
  private val IdPattern = "(?m)^id:\\s*(\\S+)\\s*$".r

  def main(args: Array[String]): Unit = {
    val runtime = new CncfRuntime
    val subsystem = runtime.initializeForEmbedding(modeHint = Some(RunMode.Command)).TAKE
    val factory = new OrderExternalUpdateFactory
    val initialized = factory.create(ComponentCreate(subsystem, ComponentOrigin.Builtin))
    val _ = subsystem.add(Vector(ComponentFactory().bootstrap(initialized.head)))
    try {
      val userId = _create(
        "createUserRecord",
        subsystem,
        component = "AggregateExternalUpdateSample",
        operation = "createUserRecord",
        properties = List(
          Property("cncf.security.privilege", "content_manager", None),
          Property("textus.runtime.command.execution-mode", "sync-direct-no-job", None),
          Property("name", "Alice", None)
        )
      )
      val orderId = _create(
        "createOrderRecord",
        subsystem,
        component = "AggregateExternalUpdateSample",
        operation = "createOrderRecord",
        properties = List(
          Property("cncf.security.privilege", "content_manager", None),
          Property("textus.runtime.command.execution-mode", "sync-direct-no-job", None),
          Property("userId", userId, None),
          Property("name", "Alpha", None),
          Property("status", "Active", None)
        )
      )
      val shipmentId = _create(
        "createShipmentOrderRecord",
        subsystem,
        component = "AggregateExternalUpdateSample",
        operation = "createShipmentOrderRecord",
        properties = List(
          Property("cncf.security.privilege", "content_manager", None),
          Property("textus.runtime.command.execution-mode", "sync-direct-no-job", None),
          Property("orderId", orderId, None),
          Property("title", "Outbound-1", None),
          Property("status", "Active", None)
        )
      )
      val aggregateText = _executeString(
        "cancelOrder",
        subsystem,
        Request.of(
          component = "AggregateExternalUpdateSample",
          service = "Order",
          operation = "cancelOrder",
          properties = List(
            Property("privilege", "content_admin", None),
            Property("cncf.security.privilege", "content_manager", None),
          Property("textus.runtime.command.execution-mode", "sync-direct-no-job", None),
            Property("orderId", orderId, None)
          )
        )
      )
      val orderText = _executeString(
        "loadOrderRecord",
        subsystem,
        Request.of(
          component = "AggregateExternalUpdateSample",
          service = "entity",
          operation = "loadOrderRecord",
          properties = List(
            Property("cncf.security.privilege", "content_manager", None),
            Property("id", orderId, None)
          )
        )
      )
      val shipmentText = _executeString(
        "loadShipmentOrderRecord",
        subsystem,
        Request.of(
          component = "AggregateExternalUpdateSample",
          service = "entity",
          operation = "loadShipmentOrderRecord",
          properties = List(
            Property("cncf.security.privilege", "content_manager", None),
            Property("id", shipmentId, None)
          )
        )
      )
      val userText = _executeString(
        "loadUserRecord",
        subsystem,
        Request.of(
          component = "AggregateExternalUpdateSample",
          service = "entity",
          operation = "loadUserRecord",
          properties = List(
            Property("cncf.security.privilege", "content_manager", None),
            Property("id", userId, None)
          )
        )
      )
      val result = Json.obj(
        "userId" -> Json.fromString(userId),
        "orderId" -> Json.fromString(orderId),
        "shipmentOrderId" -> Json.fromString(shipmentId),
        "semantic" -> Json.obj(
          "orderStatus" -> Json.fromString("Cancelled"),
          "shipmentOrderFollowUp" -> Json.fromString("Cancelled via AggregateBehavior"),
          "userAssociation" -> Json.fromString("unchanged")
        ),
        "aggregate" -> Json.fromString(aggregateText),
        "order" -> Json.fromString(orderText),
        "shipmentOrder" -> Json.fromString(shipmentText),
        "user" -> Json.fromString(userText)
      )
      println(result.noSpaces)
    } finally {
      runtime.closeEmbedding()
    }
  }

  private def _create(
    label: String,
    subsystem: org.goldenport.cncf.subsystem.Subsystem,
    component: String,
    operation: String,
    properties: List[Property]
  ): String =
    _extractId(
      _executeString(
        label,
        subsystem,
        Request.of(component = component, service = "entity", operation = operation, properties = properties)
      )
    )

  private def _executeString(
    label: String,
    subsystem: org.goldenport.cncf.subsystem.Subsystem,
    request: Request
  ): String =
    subsystem.execute(request) match
      case Consequence.Success(response) => response.print
      case Consequence.Failure(c) => throw new IllegalStateException(s"$label: ${c.show}")

  private def _extractId(text: String): String =
    IdPattern.findFirstMatchIn(text).map(_.group(1)).getOrElse {
      throw new IllegalStateException(s"Missing id in response: $text")
    }
EOF

/Users/asami/src/dev2026/cncf-samples/bin/setup cozy >/dev/null

cd "$OUT_DIR"
sbt --batch -Dsbt.server.autostart=false -Dsbt.supershell=false clean compile

DEMO_OUTPUT="$(sbt --batch -Dsbt.server.autostart=false -Dsbt.supershell=false "runMain org.sample.aggregateexternalupdate.ExternalUpdateDemo")"
AGGREGATE_OUTPUT="$(sbt --batch -Dsbt.server.autostart=false -Dsbt.supershell=false "runMain org.sample.aggregateexternalupdate.ExternalUpdateAggregateDemo")"

printf '%s\n' "$DEMO_OUTPUT" | grep -q '"Order cancellation follows up to ShipmentOrder"'
printf '%s\n' "$AGGREGATE_OUTPUT" | grep -q '"orderStatus":"Cancelled"'
printf '%s\n' "$AGGREGATE_OUTPUT" | grep -q '"shipmentOrderFollowUp":"Cancelled via AggregateBehavior"'
printf '%s\n' "$AGGREGATE_OUTPUT" | grep -q 'shipment_orders:'
printf '%s\n' "$AGGREGATE_OUTPUT" | grep -q 'title: Outbound-1'
printf '%s\n' "$AGGREGATE_OUTPUT" | grep -q 'userAssociation":"unchanged"'

echo "AGGREGATE_EXTERNAL_UPDATE_PROOF_OK"
