#!/bin/sh
set -eu

script_dir=$(CDPATH= cd -- "$(dirname "$0")" && pwd)
sample_dir=/Users/asami/src/dev2026/cncf-samples/samples/07.b-event-job-server-client-lab
out_dir="$script_dir/out.d"
cml_file="$sample_dir/src/main/cozy/event.cml"
server_log="$out_dir/server.log"
server_port=19083
server_baseurl="http://localhost:${server_port}"
security_query="cncf.context.securityLevel=content_manager"

cleanup_existing_servers() {
  pids=$(ps -ax | awk '/org\.goldenport\.cncf\.CncfMain --discover=classes server/ && /07\.b-event-job-server-client-lab|event-job-server-client\/out\.d/ {print $1}')
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
    sbt -Dcncf.http.baseurl="${server_baseurl}" --batch "runMain org.goldenport.cncf.CncfMain --discover=classes client $*"
  )
}

run_client_http() {
  (
    cd "$out_dir"
    sbt -Dcncf.http.baseurl="${server_baseurl}" --batch "runMain org.goldenport.cncf.CncfMain --discover=classes client http $*"
  )
}

(
  cd "$out_dir"
  sbt --batch compile
)

emit_help="$(run_command help event-driven.event.emit-event 2>&1)"
printf '%s\n' "$emit_help" | grep 'event-driven.event.emit-event'
printf '%s\n' "$emit_help" | grep 'EmitEventResult'

meta_out="$(run_command event-driven.meta.describe --format yaml 2>&1)"
printf '%s\n' "$meta_out" | grep 'runtime_name: event'
printf '%s\n' "$meta_out" | grep 'name: emitEvent'
printf '%s\n' "$meta_out" | grep 'name: loadEffect'

(
  cd "$out_dir"
  sbt -Dcncf.server.port="${server_port}" -Dcncf.http.baseurl="${server_baseurl}" --batch "runMain org.goldenport.cncf.CncfMain --discover=classes server" >"$server_log" 2>&1 &
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

job_id="$(run_client_http post "/rest/v1/event-driven/event/emit-event?$security_query" name=alpha title=Alpha 2>&1 | awk '/^cncf-job-/ {print $1}' | tail -n 1)"
[ -n "$job_id" ]
printf '%s\n' "$job_id" | grep '^cncf-job-'

await_out="$(run_client_http post "/rest/v1/job-control/job/await-job-result?$security_query" "id=$job_id" 2>&1)"
printf '%s\n' "$await_out" | grep 'outcome: Routed\|"outcome"[[:space:]]*:[[:space:]]*"Routed"'
printf '%s\n' "$await_out" | grep 'dispatched_count: 1\|dispatchedCount: 1\|"dispatched_count"[[:space:]]*:[[:space:]]*1\|"dispatchedCount"[[:space:]]*:[[:space:]]*1'

effect_out="$(run_client_http get "/rest/v1/event-driven/event/load-effect?$security_query" 2>&1)"
printf '%s\n' "$effect_out" | grep 'event_name: item.changed\|"event_name"[[:space:]]*:[[:space:]]*"item.changed"'
printf '%s\n' "$effect_out" | grep 'name: alpha\|"name"[[:space:]]*:[[:space:]]*"alpha"'
printf '%s\n' "$effect_out" | grep 'title: Alpha\|"title"[[:space:]]*:[[:space:]]*"Alpha"'

echo EVENT_JOB_SERVER_CLIENT_OK
