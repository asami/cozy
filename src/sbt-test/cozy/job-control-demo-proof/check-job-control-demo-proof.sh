#!/bin/sh
set -eu

script_dir=$(CDPATH= cd -- "$(dirname "$0")" && pwd)
sample_dir=/Users/asami/src/dev2026/cncf-samples/samples/08.b-job-control-demo-lab
out_dir="$script_dir/out.d"
cml_file="$sample_dir/src/main/cozy/job-control-lab.cml"
demo_dst_dir="$out_dir/src/main/scala/org/sample/jobcontroldemo"

action_src="$out_dir/src/main/scala/org/sample/jobcontroldemo/JobControlDemo.scala"

rm -rf "$out_dir"
"$script_dir/cozy-delegate.sh" modeler-scala "$cml_file" --save="$out_dir"
mkdir -p "$demo_dst_dir"
cat > "$action_src" <<'SCALA'
package org.sample.jobcontroldemo

import org.goldenport.Consequence
import org.goldenport.protocol.{Property, Request}
import org.goldenport.cncf.component.{Component, ComponentCreate, ComponentFactory, ComponentOrigin}
import org.goldenport.cncf.context.ExecutionContext
import org.goldenport.cncf.job.{ActionId, ActionTask, JobControlCommand, JobControlRequest, JobId, JobRunMode, JobSubmitOption}
import org.goldenport.cncf.subsystem.DefaultSubsystemFactory
import org.goldenport.cncf.action.Action
import org.goldenport.record.Record

object JobControlDemo {
  def main(args: Array[String]): Unit = {
    val subsystem = DefaultSubsystemFactory.default(mode = Some("command"))
    val generatedFactory = new org.sample.jobcontrol.JobControlLabComponent.Factory
    val initialized = generatedFactory.create(ComponentCreate(subsystem, ComponentOrigin.Builtin))
    val component = ComponentFactory().bootstrap(initialized.head)
    val _ = subsystem.add(Vector(component))

    val ctx = ExecutionContext.create(org.goldenport.cncf.context.SecurityContext.Privilege.ApplicationContentManager)

    val suspended = _run_suspend_resume(component, ctx)
    val cancelled = _run_cancel(component, ctx)
    val suspendedJobId = _jobId(suspended)
    val cancelledJobId = _jobId(cancelled)
    val suspendedEvents = _jobEvents(component, suspendedJobId)
    val cancelledEvents = _jobEvents(component, cancelledJobId)

    println(
      s"""{"suspend-job-id":"${suspendedJobId.value}","suspend-status":"${_status(suspended)}","suspend-history":"${_historyKinds(component, suspendedJobId)}","suspend-events":"${_eventNames(suspendedEvents)}","cancel-job-id":"${cancelledJobId.value}","cancel-status":"${_status(cancelled)}","cancel-history":"${_historyKinds(component, cancelledJobId)}","cancel-events":"${_eventNames(cancelledEvents)}"}"""
    )
  }

  private def _run_suspend_resume(component: Component, ctx: ExecutionContext): Record = {
    val jobId = _submit(component, ctx, "suspend-resume")
    _await_status(component, jobId, "Running")
    _control(component, ctx, jobId, JobControlCommand.Suspend)
    _await_status(component, jobId, "Suspended")
    _control(component, ctx, jobId, JobControlCommand.Resume)
    _await_status(component, jobId, "Succeeded")
    _loadJob(component, jobId).getOrElse(throw new IllegalStateException(s"job query not found: ${jobId.value}"))
  }

  private def _run_cancel(component: Component, ctx: ExecutionContext): Record = {
    val jobId = _submit(component, ctx, "cancel")
    _await_status(component, jobId, "Running")
    _control(component, ctx, jobId, JobControlCommand.Cancel)
    _await_status(component, jobId, "Cancelled")
    _loadJob(component, jobId).getOrElse(throw new IllegalStateException(s"job query not found: ${jobId.value}"))
  }

  private def _submit(component: Component, ctx: ExecutionContext, summary: String): JobId = {
    val request = Request.of(
      component = "JobControlLab",
      service = "Item",
      operation = "createItem",
      properties = List(
        Property("name", summary, None),
        Property("title", summary.capitalize, None)
      )
    )
    val action = component.logic.makeOperationRequest(request).flatMap {
      case a: Action => Consequence.success(a)
      case other => Consequence.failure(s"OperationRequest must be Action: ${other.show}")
    }.TAKE
    val task = ActionTask(ActionId.generate(), action, component.actionEngine, Some(component))
    component.logic.submitJob(
      List(task),
      ctx,
      JobSubmitOption(runMode = JobRunMode.Async, requestSummary = Some(summary))
    )
  }

  private def _control(component: Component, ctx: ExecutionContext, jobId: JobId, command: JobControlCommand): Unit = {
    given ExecutionContext = ctx
    component.logic.controlJob(jobId, JobControlRequest(command)) match {
      case Consequence.Success(_) => ()
      case other => throw new IllegalStateException(other.show)
    }
  }

  private def _loadJob(component: Component, jobId: JobId): Option[Record] =
    component.jobEngine.query(jobId).map(_job_record)

  private def _historyKinds(component: Component, jobId: JobId): String =
    component.jobEngine.queryTimeline(jobId) match {
      case Some(history) => history.events.map(_.kind).mkString("[", ", ", "]")
      case None => throw new IllegalStateException(s"job history not found: ${jobId.value}")
    }

  private def _job_record(model: org.goldenport.cncf.job.JobQueryReadModel): Record =
    Record.data(
      "job-id" -> model.jobId.value,
      "status" -> model.status.toString,
      "persistence" -> model.persistence.toString,
      "origin" -> model.origin.toString,
      "created-at" -> model.createdAt.toString,
      "updated-at" -> model.updatedAt.toString,
      "result-success" -> model.resultSummary.success,
      "result-message" -> model.resultSummary.message.getOrElse(""),
      "result" -> model.result.map(_.print).getOrElse(""),
      "tasks" -> model.tasks.tasks.map { task =>
        Record.data(
          "task-id" -> task.taskId.value,
          "status" -> task.status.toString,
          "success" -> task.result.success,
          "message" -> task.result.message.getOrElse("")
        )
      },
      "timeline" -> model.timeline.events.map { event =>
        Record.data(
          "timestamp" -> event.occurredAt.toString,
          "kind" -> event.kind,
          "task-id" -> event.taskId.map(_.value).getOrElse(""),
          "parent-task-id" -> event.parentTaskId.map(_.value).getOrElse(""),
          "note" -> event.note.getOrElse("")
        )
      }
    )

  private def _await_status(component: Component, jobId: JobId, expected: String, timeoutMillis: Long = 3000L, pollMillis: Long = 10L): Unit = {
    val deadline = System.currentTimeMillis() + timeoutMillis
    var current = _loadJob(component, jobId).flatMap(_.getString("status"))
    while (current != Some(expected) && System.currentTimeMillis() < deadline) {
      Thread.sleep(pollMillis)
      current = _loadJob(component, jobId).flatMap(_.getString("status"))
    }
    if (current != Some(expected))
      throw new IllegalStateException(s"job status not reached: ${jobId.value} expected=$expected actual=${current.getOrElse("<none>")}")
  }

  private def _jobEvents(component: Component, jobId: JobId): Vector[String] =
    component.eventStore match {
      case Some(store) =>
        store.query(org.goldenport.cncf.event.EventStore.Query()).toOption.getOrElse(Vector.empty)
          .filter(r => r.payload.get("job-id").contains(jobId.value) || r.attributes.get("job-id").contains(jobId.value))
          .map(_.name)
      case None => Vector.empty
    }

  private def _eventNames(events: Vector[String]): String = events.mkString("[", ", ", "]")
  private def _jobId(record: Record): JobId = JobId.parse(record.getString("job-id").getOrElse(sys.error("job-id is required"))).TAKE
  private def _status(record: Record): String = record.getString("status").getOrElse("")
}
SCALA

(
  cd "$out_dir"
  sbt --batch compile
)

result="$(cd "$out_dir" && sbt --batch 'runMain org.sample.jobcontroldemo.JobControlDemo' 2>&1 | grep '^{' | tail -n 1)"
printf '%s\n' "$result" | grep '"suspend-status":"Succeeded"'
printf '%s\n' "$result" | grep '"cancel-status":"Cancelled"'
printf '%s\n' "$result" | grep 'job.suspended'
printf '%s\n' "$result" | grep 'job.resumed'
printf '%s\n' "$result" | grep 'job.cancelled'

echo JOB_CONTROL_DEMO_PROOF_OK
