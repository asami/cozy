#!/bin/sh
set -eu

script_dir=$(CDPATH= cd -- "$(dirname "$0")" && pwd)
sample_dir=/Users/asami/src/dev2026/cncf-samples/samples/05-event-driven
out_dir="$script_dir/out.d"
cml_file="$sample_dir/src/main/cozy/event.cml"
demo_dst_dir="$out_dir/src/main/scala/org/sample/eventdriven"

rm -rf "$out_dir"
"$script_dir/cozy-delegate.sh" modeler-scala "$cml_file" --save="$out_dir"
mkdir -p "$demo_dst_dir"
cat > "$demo_dst_dir/EventFlowDemo.scala" <<'SCALA'
package org.sample.eventdriven

import org.goldenport.Consequence
import org.goldenport.configuration.{Configuration, ConfigurationTrace, ResolvedConfiguration}
import org.goldenport.protocol.{Property, Request}
import org.goldenport.protocol.operation.OperationResponse
import org.goldenport.cncf.action.Action
import org.goldenport.cncf.cli.RunMode
import org.goldenport.cncf.component.{Component, ComponentCreate, ComponentFactory, ComponentOrigin}
import org.goldenport.cncf.context.ExecutionContext
import org.goldenport.cncf.job.JobId
import org.goldenport.cncf.path.AliasResolver
import org.goldenport.cncf.subsystem.Subsystem

object EventFlowDemo {
  private val emptyConfiguration =
    ResolvedConfiguration(
      Configuration.empty,
      ConfigurationTrace.empty
    )

  def main(args: Array[String]): Unit = {
    val subsystem = Subsystem(
      name = "event-flow-demo",
      configuration = emptyConfiguration,
      aliasResolver = AliasResolver.empty,
      runMode = RunMode.Command
    )
    val generatedFactory = new EventDrivenComponent.Factory
    val initialized = generatedFactory.create(ComponentCreate(subsystem, ComponentOrigin.Builtin))
    val component = ComponentFactory().bootstrap(initialized.head)
    val _ = subsystem.add(Vector(component))

    val emitResponse = _execute(
      component,
      Request.of(
        component = "EventDriven",
        service = "Event",
        operation = "emitEvent",
        properties = List(
          Property("name", "alpha", None),
          Property("title", "Alpha", None)
        )
      )
    )
    emitResponse match {
      case Consequence.Success(OperationResponse.Scalar(jobIdValue)) =>
        val jobId = jobIdValue.toString
        JobId.parse(jobId) match {
          case Consequence.Success(parsedJobId) =>
            val _ = component.logic.awaitJobResult(parsedJobId)
          case Consequence.Failure(conclusion) =>
            throw new IllegalStateException(conclusion.show)
        }
      case Consequence.Success(_) =>
        ()
      case Consequence.Failure(c) =>
        throw new IllegalStateException(c.show)
    }

    val loadResponse = _execute(
      component,
      Request.of(
        component = "EventDriven",
        service = "Event",
        operation = "loadEffect"
      )
    )
    loadResponse match {
      case Consequence.Success(OperationResponse.RecordResponse(record)) =>
        println(record.toJsonString)
      case Consequence.Success(response) =>
        println(response.show)
      case Consequence.Failure(c) =>
        throw new IllegalStateException(c.show)
    }
  }

  private def _execute(
    component: Component,
    request: Request
  ): Consequence[OperationResponse] =
    component.logic.makeOperationRequest(request).flatMap {
      case action: Action =>
        component.logic.executeAction(action, ExecutionContext.create())
      case m =>
        Consequence.failure(s"OperationRequest must be Action: ${m.show}")
    }
}
SCALA

run_command() {
  (
    cd "$out_dir"
    sbt --batch "runMain org.goldenport.cncf.CncfMain --discover=classes command $*"
  )
}

(
  cd "$out_dir"
  sbt --batch compile
)

component_help="$(run_command help event-driven 2>&1)"
printf '%s\n' "$component_help" | grep 'name: EventDriven'
printf '%s\n' "$component_help" | grep 'emitEvent'
printf '%s\n' "$component_help" | grep 'loadEffect'

emit_help="$(run_command help event-driven.event.emit-event 2>&1)"
printf '%s\n' "$emit_help" | grep 'event-driven.event.emit-event'
printf '%s\n' "$emit_help" | grep 'EmitEventResult'

load_help="$(run_command help event-driven.event.load-effect 2>&1)"
printf '%s\n' "$load_help" | grep 'event-driven.event.load-effect'
printf '%s\n' "$load_help" | grep 'LoadEffectResult'

probe_out="$(cd "$out_dir" && sbt --batch "runMain org.sample.eventdriven.EventFlowDemo" 2>&1)"
printf '%s\n' "$probe_out" | grep '"cncf"'
printf '%s\n' "$probe_out" | grep '"item.changed"'
printf '%s\n' "$probe_out" | grep '"name":"alpha"'
printf '%s\n' "$probe_out" | grep '"title":"Alpha"'

echo EVENT_DRIVEN_SURFACE_OK
