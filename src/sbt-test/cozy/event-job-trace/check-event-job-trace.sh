#!/bin/sh
set -eu

script_dir=$(CDPATH= cd -- "$(dirname "$0")" && pwd)
sample_dir=/Users/asami/src/dev2026/cncf-samples/samples/05.a-event-job-trace-lab
out_dir="$script_dir/out.d"
cml_file="$sample_dir/src/main/cozy/event.cml"
server_log="$out_dir/server.log"

cleanup_existing_servers() {
  pids=$(ps -ax | awk '/org\.goldenport\.cncf\.CncfMain --discover=classes server/ && /05\.a-event-job-trace-lab|event-job-trace\/out\.d/ {print $1}')
  if [ -n "$pids" ]; then
    printf '%s\n' "$pids" | xargs kill >/dev/null 2>&1 || true
    sleep 1
  fi
}

rm -rf "$out_dir"
cleanup_existing_servers
"$script_dir/cozy-delegate.sh" modeler-scala "$cml_file" --save="$out_dir"

run_command() {
  (
    cd "$out_dir"
    sbt --batch "runMain org.goldenport.cncf.CncfMain --discover=classes command $*"
  )
}

run_client() {
  (
    cd "$out_dir"
    sbt --batch "runMain org.goldenport.cncf.CncfMain --discover=classes client $*"
  )
}

(
  cd "$out_dir"
  sbt --batch compile
)

emit_help="$(run_command help event-driven.event.emit-event 2>&1)"
printf '%s\n' "$emit_help" | grep 'event-driven.event.emit-event'
printf '%s\n' "$emit_help" | grep 'EmitEventResult'

await_help="$(run_command help job-control.job.await-job-result 2>&1)"
printf '%s\n' "$await_help" | grep 'job-control.job.await-job-result'
printf '%s\n' "$await_help" | grep 'arguments:'
printf '%s\n' "$await_help" | grep 'id'

history_help="$(run_command help job-control.job.load-job-history 2>&1)"
printf '%s\n' "$history_help" | grep 'job-control.job.load-job-history'
printf '%s\n' "$history_help" | grep 'id'

meta_out="$(run_command event-driven.meta.describe --format yaml 2>&1)"
printf '%s\n' "$meta_out" | grep 'runtime_name: event'
printf '%s\n' "$meta_out" | grep 'name: emitEvent'
printf '%s\n' "$meta_out" | grep 'name: loadEffect'

(
  cd "$out_dir"
  sbt --batch "runMain org.goldenport.cncf.CncfMain --discover=classes server" >"$server_log" 2>&1 &
  echo $! > server.pid
)
server_pid=$(cat "$out_dir/server.pid")
cleanup() {
  kill "$server_pid" >/dev/null 2>&1 || true
  wait "$server_pid" >/dev/null 2>&1 || true
}
trap cleanup EXIT INT TERM

for _ in $(seq 1 30); do
  if grep -q 'Ember-Server service bound to address' "$server_log"; then
    break
  fi
  sleep 1
done

grep 'Ember-Server service bound to address' "$server_log"

job_id="$(run_client event-driven.event.emit-event --name alpha --title Alpha 2>&1 | awk '/^cncf-job-/ {print $1}' | tail -n 1)"
[ -n "$job_id" ]
printf '%s\n' "$job_id" | grep '^cncf-job-'

await_json="$(run_client job-control.job.await-job-result --id "$job_id" 2>&1 | grep '^{' | tail -n 1)"
printf '%s\n' "$await_json" | grep '"outcome":"Routed"'
printf '%s\n' "$await_json" | grep '"dispatched_count":1\|"dispatchedCount":1'

status_json="$(run_client job-control.job.get-job-status --id "$job_id" 2>&1 | grep '^{' | tail -n 1)"
printf '%s\n' "$status_json" | grep '"status":"Succeeded"'
printf '%s\n' "$status_json" | grep '"result_success":true'
printf '%s\n' "$status_json" | grep '"debug_request_summary":"EventDriven.Event.emitEvent"'
printf '%s\n' "$status_json" | grep '"job.submitted"'
printf '%s\n' "$status_json" | grep '"job.succeeded"'

history_json="$(run_client job-control.job.load-job-history --id "$job_id" 2>&1 | grep '^{' | tail -n 1)"
printf '%s\n' "$history_json" | grep '"total_count":5'
printf '%s\n' "$history_json" | grep '"job.submitted"'
printf '%s\n' "$history_json" | grep '"task.succeeded"'
printf '%s\n' "$history_json" | grep '"job.succeeded"'

effect_json="$(run_client event-driven.event.load-effect 2>&1 | grep '^{' | tail -n 1)"
printf '%s\n' "$effect_json" | grep '"event_name":"item.changed"'
printf '%s\n' "$effect_json" | grep '"name":"alpha"'
printf '%s\n' "$effect_json" | grep '"title":"Alpha"'

echo EVENT_JOB_TRACE_OK
