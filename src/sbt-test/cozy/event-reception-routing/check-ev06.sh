#!/usr/bin/env sh
set -eu

cd out.d
mkdir -p src/main/scala/domain target/ev06

cat >> build.sbt <<'SBTPATCH'

resolvers += Resolver.defaultLocal
resolvers += Resolver.file("Local Ivy", file(Path.userHome.absolutePath + "/.ivy2/local"))(Resolver.ivyStylePatterns)
resolvers += "Local Maven Repository" at ("file://" + Path.userHome.absolutePath + "/.m2/repository")
libraryDependencies += "org.goldenport" %% "goldenport-cncf" % "0.4.4-SNAPSHOT"
dependencyOverrides += "org.goldenport" %% "goldenport-cncf" % "0.4.4-SNAPSHOT"
SBTPATCH

cat > src/main/scala/domain/EventReceptionProbe.scala <<'SCALA'
package domain

import java.nio.file.{Files, Paths}
import scala.collection.mutable.ArrayBuffer
import org.goldenport.Consequence
import org.goldenport.cncf.component.ComponentFactory
import org.goldenport.cncf.context.{ExecutionContext, SecurityContext}
import org.goldenport.cncf.datastore.DataStore
import org.goldenport.cncf.event.*
import org.goldenport.cncf.unitofwork.CommitRecorder
import org.goldenport.provisional.observation.Taxonomy

object EventReceptionProbe {
  final case class FailureObservation(
    category: String,
    symptom: String,
    facet: String
  ) {
    def show: String = s"category=$category,symptom=$symptom,facet=$facet"
  }

  def main(args: Array[String]): Unit = {
    val component = DomainComponent()
    val defs = component.eventReceptionDefinitions
    if (defs.isEmpty)
      throw new IllegalStateException("No Event definitions were generated in DomainComponent")

    // CML -> ReceptionInput mapping check (name/kind/selectors/actionName/priority)
    val personCreated = defs.find(_.name == "person.created").getOrElse {
      throw new IllegalStateException("Route definition for person.created is missing")
    }
    _assert(personCreated.kind.contains("created"), "kind mapping missing for person.created")
    _assert(personCreated.category.toString == "ActionEvent", s"category mapping mismatch for person.created: ${personCreated.category}")
    _assert(personCreated.selectors.get("source").contains("crm"), "selector mapping missing for person.created")
    _assert(personCreated.actionName.contains("person.sync"), "actionName mapping missing for person.created")
    _assert(personCreated.priority == 0, s"priority mapping mismatch: ${personCreated.priority}")

    val failureLog = ArrayBuffer.empty[(String, FailureObservation)]

    val recorder = new _InMemoryCommitRecorder
    val engine = EventEngine.noop(DataStore.noop(recorder), recorder, EventStore.inMemory)
    val store = engine.eventStore
    val bus = EventBus.default(engine)
    val calls = ArrayBuffer.empty[String]
    val factory = new ComponentFactory()
    val reception = factory.createEventReception(component, bus, new _RecordingDispatcher(calls))

    // Operation-compatible dispatcher exposure check on Cozy side.
    val actionFactoryDispatcher = factory
      .createOperationActionDispatcher(component)
      .asInstanceOf[ActionFactoryDispatcher]
    val parseOnlyFailure = actionFactoryDispatcher.parseValidateAction(
      "help",
      ReceptionDomainEvent("system.test", "test", Map.empty, Map.empty)
    )
    parseOnlyFailure match {
      case Consequence.Success(_) =>
        throw new IllegalStateException("operation dispatcher should fail invalid action format at parse stage")
      case Consequence.Failure(_) =>
        ()
    }

    // Case A: target route
    val a1 = _require_success(
      "A",
      reception.receive(
        ReceptionInput(
          name = "person.created",
          kind = "created",
          attributes = Map("source" -> "crm"),
          persistent = true
        )
      )
    )
    _assert(a1.outcome == ReceptionOutcome.Routed, s"Case A outcome mismatch: ${a1.outcome}")
    _assert(a1.dispatchedCount == 1, s"Case A dispatchedCount mismatch: ${a1.dispatchedCount}")
    _assert(a1.persisted, "Case A should be persisted")
    _assert(calls.toVector == Vector("person.sync"), s"Case A dispatcher mismatch: ${calls.toVector}")
    _assert(
      store.query(EventStore.Query(name = Some("person.created"))).toOption.getOrElse(Vector.empty).size == 1,
      "Case A persisted event count mismatch"
    )

    // Determinism check on the same input
    val a2 = _require_success(
      "A-determinism",
      reception.receive(
        ReceptionInput(
          name = "person.created",
          kind = "created",
          attributes = Map("source" -> "crm"),
          persistent = true
        )
      )
    )
    _assert(a2.outcome == a1.outcome, "Determinism mismatch on outcome")
    _assert(a2.dispatchedCount == a1.dispatchedCount, "Determinism mismatch on dispatchedCount")
    _assert(calls.toVector == Vector("person.sync", "person.sync"), s"Determinism dispatcher mismatch: ${calls.toVector}")

    // persistent=false check
    val beforeNonPersistent = store.query(EventStore.Query(name = Some("person.updated"))).toOption.getOrElse(Vector.empty).size
    val pfalse = _require_success(
      "persistent=false",
      reception.receive(
        ReceptionInput(
          name = "person.updated",
          kind = "updated",
          attributes = Map("source" -> "crm"),
          persistent = false
        )
      )
    )
    _assert(pfalse.outcome == ReceptionOutcome.Routed, s"persistent=false should still route, got ${pfalse.outcome}")
    _assert(!pfalse.persisted, "persistent=false should not persist")
    val afterNonPersistent = store.query(EventStore.Query(name = Some("person.updated"))).toOption.getOrElse(Vector.empty).size
    _assert(beforeNonPersistent == afterNonPersistent, "persistent=false wrote event unexpectedly")

    // Case B: non-target drop (kind mismatch)
    val b = _require_success(
      "B",
      reception.receive(
        ReceptionInput(
          name = "person.created",
          kind = "updated",
          attributes = Map("source" -> "crm")
        )
      )
    )
    _assert(b.outcome == ReceptionOutcome.Dropped, s"Case B outcome mismatch: ${b.outcome}")
    _assert(b.dispatchedCount == 0, s"Case B dispatchedCount mismatch: ${b.dispatchedCount}")
    _assert(b.reason.contains("non-target"), s"Case B reason mismatch: ${b.reason}")

    // Case C: unknown event -> failure with taxonomy
    val c = reception.receive(ReceptionInput(name = "unknown.event", kind = "x"))
    val cobs = _require_failure("C", c)
    _assert(cobs.category == Taxonomy.Category.Operation.name, s"Case C taxonomy.category mismatch: ${cobs.show}")
    _assert(cobs.symptom == Taxonomy.Symptom.Invalid.name, s"Case C taxonomy.symptom mismatch: ${cobs.show}")
    failureLog += "C" -> cobs

    // Case D: subscription mismatch (known route without action)
    val d = reception.receive(ReceptionInput(name = "order.shipped", kind = "shipped"))
    val dobs = _require_failure("D", d)
    failureLog += "D" -> dobs

    // Case E: authorized entry + user privilege -> policy denial failure
    given ExecutionContext = ExecutionContext.test(SecurityContext.Privilege.User)
    val e = reception.receiveAuthorized(ReceptionInput(name = "audit.ingested", kind = "ingested"))
    val eobs = _require_failure("E", e)
    failureLog += "E" -> eobs

    val report = new StringBuilder()
    report.append("EV-06 RESULT\n")
    report.append("mapping: name/kind/selectors/actionName/priority=ok\n")
    report.append("A: routed\n")
    report.append("B: dropped(non-target)\n")
    report.append("C: failure " + cobs.show + "\n")
    report.append("D: failure " + dobs.show + "\n")
    report.append("E: failure " + eobs.show + "\n")
    Files.writeString(Paths.get("target/ev06/event-reception-result.txt"), report.result())

    println("EV06_RECEPTION_OK")
  }

  private def _require_success[A](label: String, c: Consequence[A]): A = c match {
    case Consequence.Success(v) => v
    case Consequence.Failure(conclusion) =>
      throw new IllegalStateException(s"$label expected success but failed: ${conclusion.show}")
  }

  private def _require_failure[A](label: String, c: Consequence[A]): FailureObservation = c match {
    case Consequence.Success(v) =>
      throw new IllegalStateException(s"$label expected failure but succeeded: $v")
    case Consequence.Failure(conclusion) =>
      val t = conclusion.observation.taxonomy
      val facet = conclusion.observation.cause.descriptor.facets.headOption.map(_.toString).getOrElse("none")
      FailureObservation(t.category.name, t.symptom.name, facet)
  }

  private def _assert(p: Boolean, message: String): Unit =
    if (!p)
      throw new IllegalStateException(message)

  private final class _RecordingDispatcher(
    calls: ArrayBuffer[String]
  ) extends ActionCallDispatcher {
    def dispatchAction(actionName: String, event: DomainEvent): Consequence[Unit] = {
      val _ = event
      calls += actionName
      Consequence.unit
    }
  }

  private final class _InMemoryCommitRecorder extends CommitRecorder {
    private val _entries = ArrayBuffer.empty[String]
    def entries: Vector[String] = _entries.toVector
    def record(entry: String): Unit = _entries += entry
  }
}
SCALA

sbt --batch compile

set +e
probe_out=$(sbt --batch "runMain domain.EventReceptionProbe" 2>&1)
probe_status=$?
set -e
printf "%s\n" "$probe_out"
[ "$probe_status" -eq 0 ]
printf "%s\n" "$probe_out" | grep -q "EV06_RECEPTION_OK"
[ -f target/ev06/event-reception-result.txt ]
grep -q "A: routed" target/ev06/event-reception-result.txt
grep -q "B: dropped(non-target)" target/ev06/event-reception-result.txt
grep -q "C: failure" target/ev06/event-reception-result.txt
grep -q "D: failure" target/ev06/event-reception-result.txt
grep -q "E: failure" target/ev06/event-reception-result.txt
