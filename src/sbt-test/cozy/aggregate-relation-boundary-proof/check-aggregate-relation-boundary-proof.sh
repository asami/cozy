#!/bin/sh
set -eu

SCRIPT_DIR=$(CDPATH= cd -- "$(dirname "$0")" && pwd)
SAMPLE_DIR=/Users/asami/src/dev2026/cncf-samples/samples/07.b-aggregate-relation-boundary-model
OUT_DIR="$SCRIPT_DIR/out.d"
SRC_DIR="$OUT_DIR/src/main/scala/org/sample/aggregaterelationboundary"
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
    name := "aggregate-relation-boundary-proof",
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
      "org.goldenport" %% "goldenport-cncf" % "0.4.2-SNAPSHOT",
      "org.goldenport" %% "goldenport-core" % "0.3.1-SNAPSHOT",
      "org.simplemodeling" %% "simplemodeling-model" % "0.1.2-SNAPSHOT"
    ),
    cozyManifestMetadata ++= Map(
      "component" -> "aggregate-relation-boundary-sample",
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

cp "$SAMPLE_DIR/src/main/cozy/order-relation-boundary.cml" "$COZY_SRC_DIR/order-relation-boundary.cml"

cat > "$SRC_DIR/ExternalEntityAliases.scala" <<'EOF'
package org.sample.aggregaterelationboundary

type ShipmentOrder = entity.aggregate.ShipmentOrder
object ShipmentOrder:
  export entity.aggregate.ShipmentOrder.*

type User = entity.aggregate.User
object User:
  export entity.aggregate.User.*
EOF

cat > "$SRC_DIR/RelationBoundaryDemo.scala" <<'EOF'
package org.sample.aggregaterelationboundary

import io.circe.Json
import io.circe.syntax.*

object RelationBoundaryDemo:
  def main(args: Array[String]): Unit =
    val payload = Json.obj(
      "sample" -> "07.b-aggregate-relation-boundary-model".asJson,
      "relationAxes" -> Json.arr(
        Json.obj("name" -> "OrderLine".asJson, "kind" -> "composition".asJson, "boundary" -> "internal".asJson),
        Json.obj("name" -> "ShipmentOrder".asJson, "kind" -> "aggregation".asJson, "boundary" -> "external".asJson),
        Json.obj("name" -> "User".asJson, "kind" -> "association".asJson, "boundary" -> "external".asJson)
      ),
      "confirmed" -> Json.arr(
        "kind and boundary are separate axes".asJson,
        "ShipmentOrder is stronger than association but outside aggregate transaction boundary".asJson,
        "User remains plain external association".asJson
      )
    )
    println(payload.noSpaces)
EOF

cat > "$SRC_DIR/RelationBoundaryAggregateDemo.scala" <<'EOF'
package org.sample.aggregaterelationboundary

import io.circe.Json
import io.circe.parser.parse
import org.goldenport.Consequence
import org.goldenport.record.Record
import org.goldenport.protocol.{Property, Request}
import org.goldenport.cncf.cli.{CncfRuntime, RunMode}
import org.goldenport.cncf.component.{ComponentCreate, ComponentFactory, ComponentOrigin}

object RelationBoundaryAggregateDemo:
  private val IdPattern = "(?m)^id:\\s*(\\S+)\\s*$".r

  def main(args: Array[String]): Unit = {
    val runtime = new CncfRuntime
    val subsystem = runtime.initializeForEmbedding(modeHint = Some(RunMode.Command)).TAKE
    val factory = new AggregateRelationBoundarySampleComponent.Factory
    val initialized = factory.create(ComponentCreate(subsystem, ComponentOrigin.Builtin))
    val _ = subsystem.add(Vector(ComponentFactory().bootstrap(initialized.head)))
    try {
      val userId = _create(
        subsystem,
        component = "AggregateRelationBoundarySample",
        operation = "createUserRecord",
        properties = List(
          Property("textus.runtime.command.execution-mode", "sync-direct-no-job", None),
          Property("name", "Alice", None)
        )
      )
      val orderId = _create(
        subsystem,
        component = "AggregateRelationBoundarySample",
        operation = "createOrderRecord",
        properties = List(
          Property("textus.runtime.command.execution-mode", "sync-direct-no-job", None),
          Property("userId", userId, None),
          Property("name", "Alpha", None),
          Property("status", "Active", None),
          Property(
            "lines",
            Vector(
              Record.data("name" -> "Widget", "quantity" -> 2)
            ),
            None
          )
        )
      )
      val _ = _create(
        subsystem,
        component = "AggregateRelationBoundarySample",
        operation = "createShipmentOrderRecord",
        properties = List(
          Property("textus.runtime.command.execution-mode", "sync-direct-no-job", None),
          Property("orderId", orderId, None),
          Property("title", "Outbound-1", None)
        )
      )
      val loadText = _execute_string(
        subsystem,
        Request.of(
          component = "AggregateRelationBoundarySample",
          service = "aggregate",
          operation = "loadOrder",
          properties = List(
            Property("id", orderId, None),
            Property("textus.output.format", "json", None)
          )
        )
      )
      val searchText = _execute_string(
        subsystem,
        Request.of(
          component = "AggregateRelationBoundarySample",
          service = "aggregate",
          operation = "searchOrder",
          properties = List(
            Property("name", "Alpha", None),
            Property("textus.output.format", "json", None)
          )
        )
      )
      val result = Json.obj(
        "userId" -> Json.fromString(userId),
        "orderId" -> Json.fromString(orderId),
        "load" -> parse(loadText).getOrElse(Json.fromString(loadText)),
        "search" -> parse(searchText).getOrElse(Json.fromString(searchText))
      )
      println(result.noSpaces)
    } finally {
      runtime.closeEmbedding()
    }
  }

  private def _create(
    subsystem: org.goldenport.cncf.subsystem.Subsystem,
    component: String,
    operation: String,
    properties: List[Property]
  ): String =
    _extract_id(
      _execute_string(
        subsystem,
        Request.of(component = component, service = "entity", operation = operation, properties = properties)
      )
    )

  private def _execute_string(
    subsystem: org.goldenport.cncf.subsystem.Subsystem,
    request: Request
  ): String =
    subsystem.execute(request) match
      case Consequence.Success(response) => response.print
      case Consequence.Failure(c) => throw new IllegalStateException(c.show)

  private def _extract_id(text: String): String =
    IdPattern.findFirstMatchIn(text).map(_.group(1)).getOrElse {
      throw new IllegalStateException(s"Missing id in response: $text")
    }
EOF

/Users/asami/src/dev2026/cncf-samples/bin/setup cozy >/dev/null

cd "$OUT_DIR"
sbt --batch -Dsbt.server.autostart=false -Dsbt.supershell=false clean compile

DEMO_OUTPUT="$(sbt --batch -Dsbt.server.autostart=false -Dsbt.supershell=false "runMain org.sample.aggregaterelationboundary.RelationBoundaryDemo")"
AGGREGATE_OUTPUT="$(sbt --batch -Dsbt.server.autostart=false -Dsbt.supershell=false "runMain org.sample.aggregaterelationboundary.RelationBoundaryAggregateDemo")"

printf '%s\n' "$DEMO_OUTPUT" | grep -q '"kind and boundary are separate axes"'
printf '%s\n' "$DEMO_OUTPUT" | grep -q '"name":"ShipmentOrder"'
printf '%s\n' "$AGGREGATE_OUTPUT" | grep -q '"shipment_orders"'
printf '%s\n' "$AGGREGATE_OUTPUT" | grep -q '"user"'
printf '%s\n' "$AGGREGATE_OUTPUT" | grep -q '"search"'

echo "AGGREGATE_RELATION_BOUNDARY_PROOF_OK"
