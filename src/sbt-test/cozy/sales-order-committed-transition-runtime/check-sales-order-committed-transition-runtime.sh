#!/usr/bin/env sh
set -eu

cd out.d
mkdir -p src/main/scala/domain .cncf

cat > src/main/scala/domain/SalesOrderCommittedTransitionProbe.scala <<'SCALA'
package domain

import java.nio.file.{Files, Paths}
import java.time.Instant
import org.goldenport.Consequence
import org.goldenport.protocol.{Property, Request}
import org.goldenport.protocol.operation.OperationResponse
import org.goldenport.record.Record
import org.goldenport.cncf.bootstrap.{BootstrapConfig, CncfBootstrap, CncfHandle}
import org.goldenport.cncf.component.{Component, ComponentCreate, ComponentOrigin}
import org.goldenport.cncf.event.{EventRecord, EventStore}
import org.goldenport.cncf.subsystem.Subsystem
import org.simplemodeling.model.datatype.{EntityCollectionId, EntityId}
import domain.impl.ComponentFactory

object SalesOrderCommittedTransitionProbe {
  private final case class EventEvidence(
    eventid: String,
    name: String,
    kind: String,
    payload: Map[String, String],
    createdat: Instant,
    persistent: Boolean,
    status: String,
    lane: String,
    sequence: Long
  )

  private final case class ReplayResult(
    status: String,
    history: String,
    records: Vector[EventEvidence]
  )

  private val _component_name = "org.example.fixture.Sales"
  private val _id = EntityId.bridgeFromParts(
    "major",
    "minor",
    EntityCollectionId("major", "minor", "sales_order"),
    Instant.EPOCH,
    "smr08"
  ).toOption.getOrElse(
    throw new IllegalStateException("failed to construct deterministic SalesOrder fixture id")
  ).print
  private val _sqlite_path = Paths.get("target/cncf.d/cncf-command.sqlite3")

  def main(args: Array[String]): Unit = {
    _delete_database()
    val first = _run_graph()
    _delete_database()
    val second = _run_graph()
    _assert_replay_match(first, second)

    println("SALES_ORDER_COMMITTED_TRANSITION_RUNTIME_OK")
  }

  private def _run_graph(): ReplayResult = {
    val handle = _initialize()
    try {
      _save_draft(handle)
      _submit(handle)
      _approve(handle)
      _suspend(handle)
      _resume(handle)
      _assert_committed_transitions(handle)
      _assert_rejected_reversal_does_not_emit(handle)
      _assert_unbound_generated_mutation_does_not_emit(handle)
      _assert_status(handle, "Approved")
      _assert_history(handle, "Approved")
    } finally {
      handle.close()
    }

    if (!Files.exists(_sqlite_path))
      throw new IllegalStateException(s"sqlite file not found: $_sqlite_path")

    val reopened = _initialize()
    try {
      val status = _assert_status(reopened, "Approved")
      val history = _assert_history(reopened, "Approved")
      val records = _committed_transition_records(reopened)
      ReplayResult(status, history, records.map(_event_evidence))
    } finally {
      reopened.close()
    }
  }

  private def _delete_database(): Unit = {
    Files.deleteIfExists(_sqlite_path)
    Files.deleteIfExists(Paths.get(s"${_sqlite_path.toString}-wal"))
    Files.deleteIfExists(Paths.get(s"${_sqlite_path.toString}-shm"))
  }

  private def _assert_replay_match(first: ReplayResult, second: ReplayResult): Unit = {
    if (first.status != second.status)
      throw new IllegalStateException(s"replay status mismatch: ${first.status} != ${second.status}")
    if (first.history != second.history)
      throw new IllegalStateException(s"replay shallow-history mismatch: ${first.history} != ${second.history}")
    if (first.records != second.records)
      throw new IllegalStateException(s"replay committed-transition evidence mismatch: ${first.records} != ${second.records}")
  }

  private def _initialize(): CncfHandle =
    _take(
      CncfBootstrap.initialize(
        BootstrapConfig(
          cwd = Paths.get("").toAbsolutePath.normalize,
          args = Array("--textus.test.descriptor=runtime-test-descriptor.yaml"),
          extraComponents = _extra_components
        )
      ),
      "initialize"
    )

  private def _save_draft(handle: CncfHandle): Unit = {
    val request = Request.of(
      component = _component_name,
      service = "entity",
      operation = "saveSalesOrder",
      properties = List(
        Property("id", _id, None),
        Property("status", "Draft", None),
        Property("description", "first order", None),
        Property("lifecycleHistory", _history("Pending"), None)
  ) ++ _execution_properties
    )
    val action = _take(
      SalesComponent.EntityService.SaveSalesOrderCommand.create(request),
      "SaveSalesOrderCommand.create"
    )
    _take(handle.executeAction(action), "executeAction(saveSalesOrder)")
  }

  private def _submit(handle: CncfHandle): Unit =
    _update(handle, "Pending", "submitted order", "Pending", "submit")

  private def _approve(handle: CncfHandle): Unit =
    _update(handle, "Approved", "approved order", "Approved", "approve")

  private def _suspend(handle: CncfHandle): Unit = {
    _update(handle, "Suspended", "suspended order", "Approved", "suspend")
    _assert_history(handle, "Approved")
  }

  private def _resume(handle: CncfHandle): Unit =
    _update(handle, "Approved", "resumed order", "Approved", "resume")

  private def _update(
    handle: CncfHandle,
    status: String,
    description: String,
    historyleaf: String,
    label: String
  ): Unit = {
    val request = Request.of(
      component = _component_name,
      service = "entity",
      operation = "updateSalesOrder",
      properties = List(
        Property("id", _id, None),
        Property("status", status, None),
        Property("description", description, None),
        Property("lifecycleHistory", _history(historyleaf), None)
      ) ++ _execution_properties
    )
    val action = _take(
      SalesComponent.EntityService.UpdateSalesOrderCommand.create(request),
      s"UpdateSalesOrderCommand.create($label)"
    )
    _take(handle.executeAction(action), s"executeAction(updateSalesOrder $label)")
  }

  private def _assert_committed_transitions(handle: CncfHandle): Unit = {
    val records = _committed_transition_records(handle)
    val targets = Vector("Review/Pending", "Review/Approved", "Suspended", "Review")
    if (records.size != targets.size)
      throw new IllegalStateException(s"expected ${targets.size} committed-transition records but got ${records.size}")
    records.zipWithIndex.foreach { case (record, index) =>
      val target = targets(index)
      if (record.payload.get("entity.id").map(_.toString) != Some(_id))
        throw new IllegalStateException(s"unexpected committed entity identity: ${record.payload}")
      if (record.payload.get("transition.target").map(_.toString) != Some(target))
        throw new IllegalStateException(s"unexpected committed transition target: ${record.payload}")
      if (index == 3) {
        if (record.payload.get("transition.target.kind").map(_.toString) != Some("shallow-history"))
          throw new IllegalStateException(s"history transition lost its target kind: ${record.payload}")
        if (record.payload.get("transition.target.fallback").map(_.toString) != Some("Review/Pending"))
          throw new IllegalStateException(s"history transition lost its declared fallback: ${record.payload}")
      }
      if (record.payload.get("transition.trigger").map(_.toString) != Some("operation:entity.updateSalesOrder"))
        throw new IllegalStateException(s"unexpected committed transition trigger: ${record.payload}")
      if (record.payload.get("operation.id").map(_.toString) != Some(s"$_component_name.entity.updateSalesOrder"))
        throw new IllegalStateException(s"unexpected committed transition operation identity: ${record.payload}")
      if (record.payload.get("transaction.id").map(_.toString).forall(_.trim.isEmpty))
        throw new IllegalStateException(s"missing committed transition transaction identity: ${record.payload}")
    }
  }

  private def _committed_transition_records(handle: CncfHandle): Vector[EventRecord] =
    _take(
      handle.subsystem.eventStore.query(EventStore.Query(kind = Some("committed-transition"))),
      "query committed-transition"
    )

  private def _event_evidence(record: EventRecord): EventEvidence =
    EventEvidence(
      eventid = record.id.print,
      name = record.name,
      kind = record.kind,
      payload = record.payload.map { case (key, value) => key -> value.toString },
      createdat = record.createdAt,
      persistent = record.persistent,
      status = record.status.value,
      lane = record.lane.value,
      sequence = record.sequence
    )

  private def _assert_rejected_reversal_does_not_emit(handle: CncfHandle): Unit = {
    val request = Request.of(
      component = _component_name,
      service = "entity",
      operation = "updateSalesOrder",
      properties = List(
        Property("id", _id, None),
        Property("status", "Draft", None),
        Property("description", "must not persist", None)
      ) ++ _execution_properties
    )
    val action = _take(
      SalesComponent.EntityService.UpdateSalesOrderCommand.create(request),
      "UpdateSalesOrderCommand.create(rejected reversal)"
    )
    _expect_failure(handle.executeAction(action), "executeAction(updateSalesOrder rejected reversal)")
    val records = _take(
      handle.subsystem.eventStore.query(EventStore.Query(kind = Some("committed-transition"))),
      "query committed-transition after rejected reversal"
    )
    if (records.size != 4)
      throw new IllegalStateException(s"rejected reversal changed committed-transition count: ${records.size}")
  }

  private def _assert_unbound_generated_mutation_does_not_emit(handle: CncfHandle): Unit = {
    val request = Request.of(
      component = _component_name,
      service = "entity",
      operation = "saveSalesOrder",
      properties = List(
        Property("id", _id, None),
        Property("status", "Approved", None),
        Property("description", "unbound generated mutation must not transition", None),
        Property("lifecycleHistory", _history("Approved"), None)
      ) ++ _execution_properties
    )
    val action = _take(
      SalesComponent.EntityService.SaveSalesOrderCommand.create(request),
      "SaveSalesOrderCommand.create(unbound generated mutation)"
    )
    _take(handle.executeAction(action), "executeAction(saveSalesOrder unbound generated mutation)")
    val records = _take(
      handle.subsystem.eventStore.query(EventStore.Query(kind = Some("committed-transition"))),
      "query committed-transition after unbound generated mutation"
    )
    if (records.size != 4)
      throw new IllegalStateException(s"unbound generated mutation changed committed-transition count: ${records.size}")
  }

  private def _assert_status(handle: CncfHandle, expected: String): String = {
    val request = Request.of(
      component = _component_name,
      service = "entity",
      operation = "loadSalesOrder",
      arguments = List.empty,
      properties = Property("id", _id, None) :: _execution_properties
    )
    val action = _take(
      SalesComponent.EntityService.LoadSalesOrderQuery.create(request),
      "LoadSalesOrderQuery.create"
    )
    _take(handle.executeAction(action), "executeAction(loadSalesOrder)") match {
      case OperationResponse.RecordResponse(record) =>
        val actual = record.getAny("status") match {
          case Some(status: Record) => status.getString("value")
          case other =>
            throw new IllegalStateException(s"unexpected persisted status representation: $other")
        }
        if (actual != Some(expected))
          throw new IllegalStateException(s"unexpected persisted status: $actual")
        actual.get
      case other =>
        throw new IllegalStateException(s"unexpected load response: ${other.show}")
    }
  }

  private def _assert_history(handle: CncfHandle, expected: String): String = {
    val request = Request.of(
      component = _component_name,
      service = "entity",
      operation = "loadSalesOrder",
      arguments = List.empty,
      properties = Property("id", _id, None) :: _execution_properties
    )
    val action = _take(
      SalesComponent.EntityService.LoadSalesOrderQuery.create(request),
      "LoadSalesOrderQuery.create(history)"
    )
    _take(handle.executeAction(action), "executeAction(loadSalesOrder history)") match {
      case OperationResponse.RecordResponse(record) =>
        val actual = record.getAny("lifecycleHistory") match {
          case Some(history: Record) => history.getString("Review")
          case other =>
            throw new IllegalStateException(s"unexpected persisted history representation: $other")
        }
        if (actual != Some(expected))
          throw new IllegalStateException(s"unexpected persisted history: $actual")
        actual.get
      case other =>
        throw new IllegalStateException(s"unexpected load response: ${other.show}")
    }
  }

  private def _history(leaf: String): Record =
    Record.data("Review" -> leaf)

  private def _extra_components(subsystem: Subsystem): Seq[Component] = {
    val params = ComponentCreate(subsystem, ComponentOrigin.Main)
    Vector(ComponentFactory().createPrimary(params))
  }

  private def _execution_properties: List[Property] = List(
    Property("cncf.security.privilege", "content_manager", None),
    Property("textus.runtime.command.execution-mode", "sync-direct-no-job", None)
  )

  private def _take[A](c: Consequence[A], label: String): A = c match {
    case Consequence.Success(value) => value
    case Consequence.Failure(conclusion) =>
      throw new IllegalStateException(s"$label failed: ${conclusion.show}")
  }

  private def _expect_failure[A](c: Consequence[A], label: String): Unit = c match {
    case Consequence.Success(_) =>
      throw new IllegalStateException(s"$label unexpectedly succeeded")
    case Consequence.Failure(_) => ()
  }
}
SCALA

cat > .cncf/config.conf <<'EOFCONF'
cncf.runtime.mode = command
cncf.datastore.sqlite.path = target/cncf.d/cncf-command.sqlite3
cncf.logging.backend = file
cncf.logging.file.path = target/cncf.d/trace.log
cncf.logging.level = trace
EOFCONF

sbt --batch compile

set +e
probe_out=$(sbt --batch "runMain domain.SalesOrderCommittedTransitionProbe" 2>&1)
probe_status=$?
set -e
printf "%s\n" "$probe_out"
[ "$probe_status" -eq 0 ]
printf "%s\n" "$probe_out" | grep -q "SALES_ORDER_COMMITTED_TRANSITION_RUNTIME_OK"
