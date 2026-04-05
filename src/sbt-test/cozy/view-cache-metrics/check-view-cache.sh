#!/usr/bin/env sh
set -eu

cd out.d
mkdir -p src/main/scala/domain

cat > build.sbt <<'SBT'
ThisBuild / scalaVersion := "3.3.7"

resolvers += "GitHab releases 2020" at "https://raw.github.com/asami/maven-repository/2020/releases"
resolvers += "GitHab releases" at "https://raw.github.com/asami/maven-repository/2025/releases"
resolvers += Resolver.defaultLocal
resolvers += Resolver.file("Local Ivy", file(Path.userHome.absolutePath + "/.ivy2/local"))(Resolver.ivyStylePatterns)
resolvers += "Local Maven Repository" at ("file://" + Path.userHome.absolutePath + "/.m2/repository")

useCoursier := false

Compile / unmanagedSourceDirectories := Seq(
  (Compile / sourceDirectory).value / "scala" / "domain"
)

Compile / managedSourceDirectories := Nil

libraryDependencies ++= Seq(
  "org.goldenport" %% "goldenport-cncf" % "0.4.2-SNAPSHOT",
  "org.simplemodeling" %% "simplemodeling-model" % "0.1.2-SNAPSHOT",
  "org.goldenport" % "cncf-collaborator-api" % "0.1.0-SNAPSHOT"
)

dependencyOverrides ++= Seq(
  "org.goldenport" % "cncf-collaborator-api" % "0.1.0-SNAPSHOT",
  "org.scala-lang.modules" %% "scala-xml" % "2.1.0",
  "org.scala-lang.modules" %% "scala-parser-combinators" % "2.3.0"
)
SBT

cat > src/main/scala/domain/ViewCacheProbe.scala <<'SCALA'
package domain

import org.goldenport.Consequence
import org.goldenport.record.Record
import org.goldenport.cncf.directive.Query
import org.goldenport.cncf.entity.view.{Browser, ViewBuilder, ViewCollection}
import org.goldenport.cncf.metrics.EntityAccessMetricsRegistry
import org.simplemodeling.model.datatype.{EntityCollectionId, EntityId}

object ViewCacheProbe {
  private final case class PersonSummary(
    id: String,
    name: String,
    city: String,
    title: String
  )

  private val queryChunkSize = 2

  private val people = Vector(
    PersonSummary("tokyo-sales-entity-person-1742198400000-aa01", "Alice", "Tokyo", "Reader"),
    PersonSummary("tokyo-sales-entity-person-1742198400000-aa02", "Bella", "Tokyo", "Analyst"),
    PersonSummary("tokyo-sales-entity-person-1742198400000-aa03", "Chloe", "Tokyo", "Planner"),
    PersonSummary("tokyo-sales-entity-person-1742198400000-aa04", "Diana", "Tokyo", "Designer"),
    PersonSummary("tokyo-sales-entity-person-1742198400000-aa05", "Emma", "Tokyo", "Lead"),
    PersonSummary("tokyo-sales-entity-person-1742198400000-bb01", "Bob", "Osaka", "Editor")
  )

  def main(args: Array[String]): Unit = {
    EntityAccessMetricsRegistry.shared.clear()
    val browser = _browser()

    _query(browser, 0, 2)
    _query(browser, 1, 2)
    _query(browser, 2, 2)
    _smallQuery(browser)
    _smallQuery(browser)

    val metrics = EntityAccessMetricsRegistry.shared.snapshot().map(x => x.name -> x.count).toMap

    _require(metrics.get("view.query.chunk.miss").contains(2L), s"unexpected chunk miss metrics: $metrics")
    _require(metrics.get("view.query.chunk.hit").contains(2L), s"unexpected chunk hit metrics: $metrics")
    _require(metrics.get("view.query.small.miss").contains(1L), s"unexpected small miss metrics: $metrics")
    _require(metrics.get("view.query.small.hit").contains(1L), s"unexpected small hit metrics: $metrics")

    println("VIEW_CACHE_OK")
  }

  private def _browser(): Browser[PersonSummary] = {
    val cid = EntityCollectionId("test", "1", "person")
    val builder = new ViewBuilder[PersonSummary] {
      def build(id: EntityId): Consequence[PersonSummary] =
        people.find(_.id == id.value) match {
          case Some(v) => Consequence.success(v)
          case None => Consequence.failure("not found")
        }
    }
    val collection = new ViewCollection[PersonSummary](
      builder = builder,
      queryChunkSize = queryChunkSize,
      metricsName = "person-summary",
      metricsRegistry = Some(EntityAccessMetricsRegistry.shared)
    )
    Browser.from(collection, q => {
      val filtered = people.filter(v => Query.matches(q, v))
      Consequence.success(Query.sliceValues(filtered, q.offset, q.limit))
    })
  }

  private def _query(browser: Browser[PersonSummary], offset: Int, limit: Int): Unit =
    browser.query(Query.plan(Record.data("city" -> "Tokyo"), limit = Some(limit), offset = Some(offset))).take

  private def _smallQuery(browser: Browser[PersonSummary]): Unit =
    browser.query(Query(Record.data("city" -> "Osaka"))).take

  private def _require(p: Boolean, msg: String): Unit =
    if (!p) throw new IllegalStateException(msg)
}
SCALA

sbt --batch compile

set +e
probe_out=$(sbt --batch "runMain domain.ViewCacheProbe" 2>&1)
probe_status=$?
set -e
printf "%s\n" "$probe_out"
[ "$probe_status" -eq 0 ]
printf "%s\n" "$probe_out" | grep -q "VIEW_CACHE_OK"
