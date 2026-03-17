#!/usr/bin/env sh
set -eu

cd out.d
mkdir -p src/main/scala/domain .cncf

cat > src/main/scala/domain/SimpleEntityActionProbe.scala <<'SCALA'
package domain

import java.nio.file.{Files, Paths}
import org.goldenport.Consequence
import org.goldenport.protocol.{Argument, Request}
import org.goldenport.protocol.operation.OperationResponse
import org.goldenport.cncf.bootstrap.{BootstrapConfig, CncfBootstrap}
import org.goldenport.cncf.component.{Component, ComponentCreate, ComponentOrigin}
import org.goldenport.cncf.subsystem.Subsystem
import domain.impl.ComponentFactory

object SimpleEntityActionProbe {
  def main(args: Array[String]): Unit = {
    val id = "sys-sys-entity-simple_entity-1773792000000-1eeeeeeeeeeeeeeeee"
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
        operation = "saveSimpleEntity",
        arguments = List(
          Argument("id", id),
          Argument("name", "taro")
        )
      )
      val saveAction = _take(
        DomainComponent.EntityService.SaveSimpleEntityCommand.create(saveRequest),
        "SaveSimpleEntityCommand.create"
      )
      _take(handle.executeAction(saveAction), "executeAction(saveSimpleEntity)")

      if (!Files.exists(sqlitePath))
        throw new IllegalStateException(s"sqlite file not found: $sqlitePath")

      val loadRequest = Request.of(
        component = "domain",
        service = "entity",
        operation = "loadSimpleEntity",
        arguments = List(
          Argument("id", id)
        )
      )
      val loadAction = _take(
        DomainComponent.EntityService.LoadSimpleEntityQuery.create(loadRequest),
        "LoadSimpleEntityQuery.create"
      )
      val loadResponse = _take(handle.executeAction(loadAction), "executeAction(loadSimpleEntity)")
      _assert_name(loadResponse, "taro")
    } finally {
      handle.close()
    }

    println("SIMPLEENTITY_ACTION_OK")
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
probe_out=$(sbt --batch "runMain domain.SimpleEntityActionProbe" 2>&1)
probe_status=$?
set -e
printf "%s\n" "$probe_out"
[ "$probe_status" -eq 0 ]
printf "%s\n" "$probe_out" | grep -q "SIMPLEENTITY_ACTION_OK"
