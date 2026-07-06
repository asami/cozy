#!/bin/sh
set -eu


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

val cncfVersion = sys.props.getOrElse("cncf.version", sys.env.getOrElse("CNCF_VERSION", "0.4.13"))
val simplemodelingModelVersion = sys.props.getOrElse(
  "simplemodeling.model.version",
  sys.env.getOrElse("SIMPLEMODELING_MODEL_VERSION", "0.1.7")
)

lazy val root = (project in file("."))
  .enablePlugins(org.goldenport.cozy.CozyPlugin)
  .settings(
    name := "aggregate-external-update-proof",
    scalacOptions ++= Seq("-deprecation", "-feature", "-unchecked"),
    cozyGeneratorBackend := "cozy",
    cozyDelegateProjectDir := None,
    cozyDelegateCommand := Seq("cozy"),
    resolvers ++= Seq(
      Resolver.defaultLocal,
      Resolver.mavenLocal,
      "SimpleModeling.org" at "https://www.simplemodeling.org/repository/maven"
    ),
    libraryDependencies ++= Seq(
      "org.goldenport" %% "goldenport-cncf" % cncfVersion,
      "org.goldenport" %% "goldenport-core" % "0.3.7",
      "org.simplemodeling" %% "simplemodeling-model" % simplemodelingModelVersion
    ),
    dependencyOverrides ++= Seq(
      "org.simplemodeling" %% "simplemodeling-model" % simplemodelingModelVersion
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
resolvers += "SimpleModeling.org" at "https://www.simplemodeling.org/repository/maven"
resolvers += Resolver.defaultLocal
val sbtCozyVersion = sys.props.getOrElse("sbt.cozy.version", sys.env.getOrElse("SBT_COZY_VERSION", "0.1.11"))
addSbtPlugin("org.goldenport" % "sbt-cozy" % sbtCozyVersion)
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
import org.simplemodeling.model.datatype.{EntityCollectionId, EntityId}

object ExternalUpdateAggregateDemo:
  private val IdPattern = "(?m)^id:\\s*(\\S+)\\s*$".r

  def main(args: Array[String]): Unit = {
    val runtime = new CncfRuntime
    val subsystem = runtime.initializeForEmbedding(modeHint = Some(RunMode.Command)).TAKE
    val factory = new OrderExternalUpdateFactory
    val component = ComponentFactory().bootstrap(
      factory.createPrimary(ComponentCreate(subsystem, ComponentOrigin.Builtin))
    )
    val _ = subsystem.add(Vector(component))
    try {
      val userId = _create(
        "createUserRecord",
        subsystem,
        component = "AggregateExternalUpdateSample",
        operation = "createUserRecord",
        collection = org.sample.aggregateexternalupdate.entity.User.collectionId,
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
        collection = org.sample.aggregateexternalupdate.entity.Order.collectionId,
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
        collection = org.sample.aggregateexternalupdate.entity.ShipmentOrder.collectionId,
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
        "userId" -> Json.fromString(userId.print),
        "orderId" -> Json.fromString(orderId.print),
        "shipmentOrderId" -> Json.fromString(shipmentId.print),
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
    collection: EntityCollectionId,
    properties: List[Property]
  ): EntityId =
    _extractId(
      _executeString(
        label,
        subsystem,
        Request.of(component = component, service = "entity", operation = operation, properties = properties)
      ),
      collection
    )

  private def _executeString(
    label: String,
    subsystem: org.goldenport.cncf.subsystem.Subsystem,
    request: Request
  ): String =
    subsystem.execute(request) match
      case Consequence.Success(response) => response.print
      case Consequence.Failure(c) => throw new IllegalStateException(s"$label: ${c.show}")

  private def _extractId(text: String, collection: EntityCollectionId): EntityId =
    IdPattern.findFirstMatchIn(text)
      .map(_.group(1))
      .map { raw =>
        EntityId.parse(raw).map(_.copy(collection = collection)).TAKE
      }
      .getOrElse {
        throw new IllegalStateException(s"Missing id in response: $text")
      }
EOF

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
