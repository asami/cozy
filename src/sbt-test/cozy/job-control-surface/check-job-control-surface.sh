#!/bin/sh
set -eu

script_dir=$(CDPATH= cd -- "$(dirname "$0")" && pwd)
sample_dir=/Users/asami/src/dev2026/cncf-samples/samples/08.a-job-control-lab
out_dir="$script_dir/out.d"
cml_file="$sample_dir/src/main/cozy/job-control-lab.cml"
server_log="$out_dir/server.log"
server_port=19085
server_baseurl="http://localhost:${server_port}"
security_query="cncf.context.securityLevel=content_manager"
factory_class=org.sample.jobcontrol.impl.JobControlLabComponentFactory
factory_src="$sample_dir/src/main/scala/org/sample/jobcontrol/impl/JobControlLabComponentFactory.scala"
factory_dst_dir="$out_dir/src/main/scala/org/sample/jobcontrol/impl"

cleanup_existing_servers() {
  pids=$(ps -ax | awk '/org\.goldenport\.cncf\.CncfMain --discover=classes server/ && /08\.a-job-control-lab|job-control-surface\/out\.d/ {print $1}')
  if [ -n "$pids" ]; then
    printf '%s\n' "$pids" | xargs kill >/dev/null 2>&1 || true
    sleep 1
  fi
}

rm -rf "$out_dir"
cleanup_existing_servers
"$script_dir/cozy-delegate.sh" modeler-scala "$cml_file" --save="$out_dir"
mkdir -p "$factory_dst_dir"
cp "$factory_src" "$factory_dst_dir/"

run_command() {
  (
    cd "$out_dir"
    sbt --batch "runMain org.goldenport.cncf.CncfMain --component-factory-class $factory_class --discover=classes command $*"
  )
}

run_client() {
  (
    cd "$out_dir"
    sbt -Dcncf.http.baseurl="${server_baseurl}" --batch "runMain org.goldenport.cncf.CncfMain --component-factory-class $factory_class --discover=classes client $*"
  )
}

run_client_http() {
  (
    cd "$out_dir"
    sbt -Dcncf.http.baseurl="${server_baseurl}" --batch "runMain org.goldenport.cncf.CncfMain --component-factory-class $factory_class --discover=classes client http $*"
  )
}

(
  cd "$out_dir"
  sbt --batch compile
)

submit_help="$(run_command help job-control-lab.item.create-item 2>&1)"
printf '%s\n' "$submit_help" | grep 'job-control-lab.item.create-item'

suspend_help="$(run_command help job-control.job-admin.suspend-job 2>&1)"
printf '%s\n' "$suspend_help" | grep 'job-control.job-admin.suspend-job'

resume_help="$(run_command help job-control.job-admin.resume-job 2>&1)"
printf '%s\n' "$resume_help" | grep 'job-control.job-admin.resume-job'

cancel_help="$(run_command help job-control.job-admin.cancel-job 2>&1)"
printf '%s\n' "$cancel_help" | grep 'job-control.job-admin.cancel-job'

event_help="$(run_command help event.event-admin.load-job-events 2>&1)"
printf '%s\n' "$event_help" | grep 'event.event-admin.load-job-events'

meta_out="$(run_command job-control-lab.meta.describe --format yaml 2>&1)"
printf '%s\n' "$meta_out" | grep 'runtime_name: item'
printf '%s\n' "$meta_out" | grep 'name: createItem'
printf '%s\n' "$meta_out" | grep 'kind: COMMAND'

(
  cd "$out_dir"
  sbt -Dcncf.server.port="${server_port}" -Dcncf.http.baseurl="${server_baseurl}" --batch "runMain org.goldenport.cncf.CncfMain --component-factory-class $factory_class --discover=classes server" >"$server_log" 2>&1 &
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

for _ in $(seq 1 30); do
  http_status="$(curl -sS -o /dev/null -w '%{http_code}' "${server_baseurl}/web" 2>/dev/null || true)"
  if [ "$http_status" != "000" ]; then
    break
  fi
  sleep 1
done
[ "$http_status" != "000" ]

create_out="$(run_client job-control-lab.item.create-item --name suspend-resume --title SuspendResume 2>&1)"
outer_job_id="$(printf '%s\n' "$create_out" | awk '/^cncf-job-/ {print $1}' | tail -n 1)"
[ -n "$outer_job_id" ]
printf '%s\n' "$outer_job_id" | grep '^cncf-job-'

submit_result="$(run_client_http post "/rest/v1/job-control/job/await-job-result?$security_query" "id=$outer_job_id" 2>&1)"
job_id="$(printf '%s\n' "$submit_result" | grep -o 'cncf-job-[A-Za-z0-9_-]*' | tail -n 1)"
[ -n "$job_id" ]
printf '%s\n' "$job_id" | grep '^cncf-job-'

suspend_out="$(run_client_http post "/rest/v1/job-control/job-admin/suspend-job?$security_query" "id=$job_id" 2>&1)"
printf '%s\n' "$suspend_out" | grep 'status: Suspended\|"status"[[:space:]]*:[[:space:]]*"Suspended"'

status_out="$(run_client_http get "/rest/v1/job-control/job/get-job-status?id=$job_id&$security_query" 2>&1)"
printf '%s\n' "$status_out" | grep 'job.suspended'

resume_out="$(run_client_http post "/rest/v1/job-control/job-admin/resume-job?$security_query" "id=$job_id" 2>&1)"
printf '%s\n' "$resume_out" | grep 'status: Running\|"status"[[:space:]]*:[[:space:]]*"Running"'

await_out=''
item_result_pattern='id: .*entity-item-\|"id"[[:space:]]*:[[:space:]]*".*entity-item-'
for _ in $(seq 1 10); do
  await_out="$(run_client_http post "/rest/v1/job-control/job/await-job-result?$security_query" "id=$job_id" 2>&1)"
  if printf '%s\n' "$await_out" | grep -q "$item_result_pattern"; then
    break
  fi
  sleep 1
done
printf '%s\n' "$await_out" | grep "$item_result_pattern"

history_out="$(run_client_http get "/rest/v1/job-control/job/load-job-history?id=$job_id&$security_query" 2>&1)"
printf '%s\n' "$history_out" | grep 'job.suspended'
printf '%s\n' "$history_out" | grep 'job.resumed'
printf '%s\n' "$history_out" | grep 'job.succeeded'

events_out="$(run_command event.event-admin.load-job-events --id "$job_id" --privilege content_admin 2>&1)"
printf '%s\n' "$events_out" | grep 'job_id:'
printf '%s\n' "$events_out" | grep "$job_id"

cancel_out="$(run_client job-control-lab.item.create-item --name cancel --title Cancel 2>&1)"
cancel_outer_job_id="$(printf '%s\n' "$cancel_out" | awk '/^cncf-job-/ {print $1}' | tail -n 1)"
[ -n "$cancel_outer_job_id" ]
printf '%s\n' "$cancel_outer_job_id" | grep '^cncf-job-'

cancel_submit_result="$(run_client_http post "/rest/v1/job-control/job/await-job-result?$security_query" "id=$cancel_outer_job_id" 2>&1)"
cancel_job_id="$(printf '%s\n' "$cancel_submit_result" | grep -o 'cncf-job-[A-Za-z0-9_-]*' | tail -n 1)"
[ -n "$cancel_job_id" ]
printf '%s\n' "$cancel_job_id" | grep '^cncf-job-'

cancel_result="$(run_client_http post "/rest/v1/job-control/job-admin/cancel-job?$security_query" "id=$cancel_job_id" 2>&1)"
printf '%s\n' "$cancel_result" | grep 'status: Cancelled\|"status"[[:space:]]*:[[:space:]]*"Cancelled"'

cancel_history="$(run_client_http get "/rest/v1/job-control/job/load-job-history?id=$cancel_job_id&$security_query" 2>&1)"
printf '%s\n' "$cancel_history" | grep 'job.cancelled'

cancel_events_out="$(run_command event.event-admin.load-job-events --id "$cancel_job_id" --privilege content_admin 2>&1)"
printf '%s\n' "$cancel_events_out" | grep 'job_id:'
printf '%s\n' "$cancel_events_out" | grep "$cancel_job_id"

echo JOB_CONTROL_SURFACE_OK
