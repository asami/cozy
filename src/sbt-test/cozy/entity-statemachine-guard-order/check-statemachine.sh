#!/usr/bin/env sh
set -eu

cd out.d
mkdir -p src/main/scala/domain src/main/scala/org/goldenport/datatype .cncf

cat > src/main/scala/org/goldenport/datatype/Statemachine.scala <<'SCALA'
package org.goldenport.datatype

import org.goldenport.Consequence
import org.goldenport.convert.ValueReader
import io.circe.Codec

final case class Statemachine(value: String) derives Codec.AsObject

object Statemachine {
  def parse(value: String): Consequence[Statemachine] =
    Consequence.success(Statemachine(Option(value).getOrElse("")))

  implicit val valueReader: ValueReader[Statemachine] = new ValueReader[Statemachine] {
    override def readC(value: Any): Consequence[Statemachine] = value match {
      case m: Statemachine => Consequence.success(m)
      case s: String => parse(s)
      case other => parse(Option(other).map(_.toString).getOrElse(""))
    }
  }
}
SCALA

cat > src/main/scala/domain/StateMachineGuardOrderProbe.scala <<'SCALA'
package domain

import java.nio.file.{Files, Paths}
import org.goldenport.Consequence
import org.goldenport.protocol.{Argument, Request}
import org.goldenport.protocol.operation.OperationResponse
import org.goldenport.cncf.bootstrap.{BootstrapConfig, CncfBootstrap}
import org.goldenport.cncf.component.{Component, ComponentCreate, ComponentOrigin}
import org.goldenport.cncf.subsystem.Subsystem
import domain.impl.ComponentFactory

object StateMachineGuardOrderProbe {
  def main(args: Array[String]): Unit = {
    val id = "sys-sys-entity-person-1773810000000-1bbbbbbbbbbbbbbbbbbb"
    val sqlitePath = Paths.get("target/cncf.d/cncf-command.sqlite3")
    Files.deleteIfExists(sqlitePath)

    val handle = _take(
      CncfBootstrap.initialize(
        BootstrapConfig(
          cwd = Paths.get("").toAbsolutePath.normalize,
          args = Array.empty[String],
          extraComponents = _extraComponents
        )
      ),
      "initialize"
    )

    try {
      val saveRequest = Request.of(
        component = "domain",
        service = "entity",
        operation = "savePerson",
        arguments = List(
          Argument("id", id),
          Argument("name", "taro"),
          Argument("lifecycle", "draft")
        )
      )
      val saveAction = _take(
        DomainComponent.EntityService.SavePersonCommand.create(saveRequest),
        "SavePersonCommand.create"
      )
      _take(handle.executeAction(saveAction), "executeAction(savePerson)")

      val updateRequest = Request.of(
        component = "domain",
        service = "entity",
        operation = "updatePerson",
        arguments = List(
          Argument("id", id),
          Argument("name", "jiro")
        )
      )
      val updateAction = _take(
        DomainComponent.EntityService.UpdatePersonCommand.create(updateRequest),
        "UpdatePersonCommand.create"
      )
      _take(handle.executeAction(updateAction), "executeAction(updatePerson)")

      val loadRequest = Request.of(
        component = "domain",
        service = "entity",
        operation = "loadPerson",
        arguments = List(
          Argument("id", id)
        )
      )
      val loadAction = _take(
        DomainComponent.EntityService.LoadPersonQuery.create(loadRequest),
        "LoadPersonQuery.create"
      )
      val loadResponse = _take(handle.executeAction(loadAction), "executeAction(loadPerson)")
      _assert_name(loadResponse, "jiro")

      if (!Files.exists(sqlitePath))
        throw new IllegalStateException(s"sqlite file not found: $sqlitePath")
    } finally {
      handle.close()
    }

    println("STATEMACHINE_GUARD_ORDER_OK")
  }

  private def _extraComponents(subsystem: Subsystem): Seq[Component] = {
    val params = ComponentCreate(subsystem, ComponentOrigin.Main)
    ComponentFactory().create(params)
  }

  private def _assert_name(response: OperationResponse, expected: String): Unit =
    response match {
      case OperationResponse.RecordResponse(record) =>
        val actual = record.getString("name").getOrElse("")
        if (actual != expected)
          throw new IllegalStateException(s"Unexpected name: actual='$actual', expected='$expected'")
      case m =>
        throw new IllegalStateException(s"Unexpected response type: ${m.show}")
    }

  private def _take[A](c: Consequence[A], label: String): A = c match {
    case Consequence.Success(v) => v
    case Consequence.Failure(conclusion) =>
      throw new IllegalStateException(s"$label failed: ${conclusion.show}")
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
probe_out=$(sbt --batch "runMain domain.StateMachineGuardOrderProbe" 2>&1)
probe_status=$?
set -e
printf "%s\n" "$probe_out"
[ "$probe_status" -eq 0 ]
printf "%s\n" "$probe_out" | grep -q "STATEMACHINE_GUARD_ORDER_OK"
