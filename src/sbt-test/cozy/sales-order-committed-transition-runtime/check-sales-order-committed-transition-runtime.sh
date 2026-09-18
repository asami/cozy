#!/usr/bin/env sh
set -eu

export CNCF_VERSION="${CNCF_VERSION:-0.5.3-SNAPSHOT}"

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
import org.goldenport.cncf.event.EventStore
import org.goldenport.cncf.subsystem.Subsystem
import org.simplemodeling.model.datatype.{EntityCollectionId, EntityId}
import domain.impl.ComponentFactory

object SalesOrderCommittedTransitionProbe {
  private val ComponentName = "org.example.fixture.Sales"
  private val Id = EntityId.bridgeFromParts(
    "major",
    "minor",
    EntityCollectionId("major", "minor", "sales_order"),
    Instant.EPOCH,
    "smr08"
  ).toOption.getOrElse(
    throw new IllegalStateException("failed to construct deterministic SalesOrder fixture id")
  ).print
  private val SqlitePath = Paths.get("target/cncf.d/cncf-command.sqlite3")

  def main(args: Array[String]): Unit = {
    Files.deleteIfExists(SqlitePath)

    val first = _initialize()
    try {
      _saveDraft(first)
      _submit(first)
      _approve(first)
      _suspend(first)
      _resume(first)
      _assertCommittedTransitions(first)
      _assertRejectedReversalDoesNotEmit(first)
      _assertStatus(first, "Approved")
      _assertHistory(first, "Approved")
    } finally {
      first.close()
    }

    if (!Files.exists(SqlitePath))
      throw new IllegalStateException(s"sqlite file not found: $SqlitePath")

    val reopened = _initialize()
    try {
      _assertStatus(reopened, "Approved")
      _assertHistory(reopened, "Approved")
    } finally {
      reopened.close()
    }

    println("SALES_ORDER_COMMITTED_TRANSITION_RUNTIME_OK")
  }

  private def _initialize(): CncfHandle =
    _take(
      CncfBootstrap.initialize(
        BootstrapConfig(
          cwd = Paths.get("").toAbsolutePath.normalize,
          args = Array("--textus.test.descriptor=runtime-test-descriptor.yaml"),
          extraComponents = _extraComponents
        )
      ),
      "initialize"
    )

  private def _saveDraft(handle: CncfHandle): Unit = {
    val request = Request.of(
      component = ComponentName,
      service = "entity",
      operation = "saveSalesOrder",
      properties = List(
        Property("id", Id, None),
        Property("status", "Draft", None),
        Property("description", "first order", None),
        Property("lifecycleHistory", _history("Pending"), None)
      ) ++ _executionProperties
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
    _assertHistory(handle, "Approved")
  }

  private def _resume(handle: CncfHandle): Unit =
    _update(handle, "Approved", "resumed order", "Approved", "resume")

  private def _update(
    handle: CncfHandle,
    status: String,
    description: String,
    historyLeaf: String,
    label: String
  ): Unit = {
    val request = Request.of(
      component = ComponentName,
      service = "entity",
      operation = "updateSalesOrder",
      properties = List(
        Property("id", Id, None),
        Property("status", status, None),
        Property("description", description, None),
        Property("lifecycleHistory", _history(historyLeaf), None)
      ) ++ _executionProperties
    )
    val action = _take(
      SalesComponent.EntityService.UpdateSalesOrderCommand.create(request),
      s"UpdateSalesOrderCommand.create($label)"
    )
    _take(handle.executeAction(action), s"executeAction(updateSalesOrder $label)")
  }

  private def _assertCommittedTransitions(handle: CncfHandle): Unit = {
    val records = _take(
      handle.subsystem.eventStore.query(EventStore.Query(kind = Some("committed-transition"))),
      "query committed-transition"
    )
    val targets = Vector("Review/Pending", "Review/Approved", "Suspended", "Review")
    if (records.size != targets.size)
      throw new IllegalStateException(s"expected ${targets.size} committed-transition records but got ${records.size}")
    records.zipWithIndex.foreach { case (record, index) =>
      val target = targets(index)
      if (record.payload.get("entity.id").map(_.toString) != Some(Id))
        throw new IllegalStateException(s"unexpected committed entity identity: ${record.payload}")
      if (record.payload.get("transition.target").map(_.toString) != Some(target))
        throw new IllegalStateException(s"unexpected committed transition target: ${record.payload}")
      if (index == 3) {
        if (record.payload.get("transition.target.kind").map(_.toString) != Some("shallow-history"))
          throw new IllegalStateException(s"history transition lost its target kind: ${record.payload}")
        if (record.payload.get("transition.target.fallback").map(_.toString) != Some("Review/Pending"))
          throw new IllegalStateException(s"history transition lost its declared fallback: ${record.payload}")
      }
      if (record.payload.get("transition.trigger").map(_.toString) != Some("update"))
        throw new IllegalStateException(s"unexpected committed transition trigger: ${record.payload}")
      if (record.payload.get("operation.id").map(_.toString).forall(_.trim.isEmpty))
        throw new IllegalStateException(s"missing committed transition operation identity: ${record.payload}")
      if (record.payload.get("transaction.id").map(_.toString).forall(_.trim.isEmpty))
        throw new IllegalStateException(s"missing committed transition transaction identity: ${record.payload}")
    }
  }

  private def _assertRejectedReversalDoesNotEmit(handle: CncfHandle): Unit = {
    val request = Request.of(
      component = ComponentName,
      service = "entity",
      operation = "updateSalesOrder",
      properties = List(
        Property("id", Id, None),
        Property("status", "Draft", None),
        Property("description", "must not persist", None)
      ) ++ _executionProperties
    )
    val action = _take(
      SalesComponent.EntityService.UpdateSalesOrderCommand.create(request),
      "UpdateSalesOrderCommand.create(rejected reversal)"
    )
    _expectFailure(handle.executeAction(action), "executeAction(updateSalesOrder rejected reversal)")
    val records = _take(
      handle.subsystem.eventStore.query(EventStore.Query(kind = Some("committed-transition"))),
      "query committed-transition after rejected reversal"
    )
    if (records.size != 4)
      throw new IllegalStateException(s"rejected reversal changed committed-transition count: ${records.size}")
  }

  private def _assertStatus(handle: CncfHandle, expected: String): Unit = {
    val request = Request.of(
      component = ComponentName,
      service = "entity",
      operation = "loadSalesOrder",
      arguments = List.empty,
      properties = Property("id", Id, None) :: _executionProperties
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
      case other =>
        throw new IllegalStateException(s"unexpected load response: ${other.show}")
    }
  }

  private def _assertHistory(handle: CncfHandle, expected: String): Unit = {
    val request = Request.of(
      component = ComponentName,
      service = "entity",
      operation = "loadSalesOrder",
      arguments = List.empty,
      properties = Property("id", Id, None) :: _executionProperties
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
      case other =>
        throw new IllegalStateException(s"unexpected load response: ${other.show}")
    }
  }

  private def _history(leaf: String): Record =
    Record.data("Review" -> leaf)

  private def _extraComponents(subsystem: Subsystem): Seq[Component] = {
    val params = ComponentCreate(subsystem, ComponentOrigin.Main)
    Vector(ComponentFactory().createPrimary(params))
  }

  private def _executionProperties: List[Property] = List(
    Property("cncf.security.privilege", "content_manager", None),
    Property("textus.runtime.command.execution-mode", "sync-direct-no-job", None)
  )

  private def _take[A](c: Consequence[A], label: String): A = c match {
    case Consequence.Success(value) => value
    case Consequence.Failure(conclusion) =>
      throw new IllegalStateException(s"$label failed: ${conclusion.show}")
  }

  private def _expectFailure[A](c: Consequence[A], label: String): Unit = c match {
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
