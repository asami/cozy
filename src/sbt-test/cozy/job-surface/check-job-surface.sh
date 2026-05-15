#!/bin/sh
set -eu

script_dir=$(CDPATH= cd -- "$(dirname "$0")" && pwd)
sample_dir=/Users/asami/src/dev2026/cncf-samples/samples/08-job
out_dir="$script_dir/out.d"
cml_file="$sample_dir/src/main/cozy/job.cml"
server_log="$out_dir/server.log"
server_port=19082
server_baseurl="http://localhost:${server_port}"
runtime_classpath=

cleanup_existing_servers() {
  pids=$(ps -ax | awk '/org\.goldenport\.cncf\.CncfMain --discover=classes server/ && /06-job|job-surface\/out\.d/ {print $1}')
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
    java -cp "$runtime_classpath" org.goldenport.cncf.CncfMain --discover=classes command "$@"
  )
}

run_client() {
  (
    cd "$out_dir"
    java \
      -Dcncf.http.baseurl="${server_baseurl}" \
      -cp "$runtime_classpath" \
      org.goldenport.cncf.CncfMain \
      --discover=classes \
      client \
      "$@"
  )
}

(
  cd "$out_dir"
  sbt --batch compile
)

runtime_classpath="$(
  (
    cd "$out_dir"
    sbt --batch 'show Runtime / fullClasspath'
  ) | awk '
      /Attributed\(/ {
        line = $0
        gsub(/\033\[[0-9;]*[A-Za-z]/, "", line)
        sub(/^.*Attributed\(/, "", line)
        sub(/\).*$/, "", line)
        if (line != "") print line
      }
    ' | paste -sd: -
)"
[ -n "$runtime_classpath" ]

component_help="$(run_command help job-sample 2>&1)"
printf '%s\n' "$component_help" | grep 'job-sample'
printf '%s\n' "$component_help" | grep 'createItem'

command_help="$(run_command help job-sample.item.create-item 2>&1)"
printf '%s\n' "$command_help" | grep 'job-sample.item.create-item'
printf '%s\n' "$command_help" | grep 'CreateItemResult'

job_help="$(run_command help job-control.job 2>&1)"
printf '%s\n' "$job_help" | grep 'job-control.job'
printf '%s\n' "$job_help" | grep 'await_job_result'
printf '%s\n' "$job_help" | grep 'get_job_result'
printf '%s\n' "$job_help" | grep 'get_job_status'
printf '%s\n' "$job_help" | grep 'load_job_history'

meta_out="$(run_command job-sample.meta.describe --format yaml 2>&1)"
printf '%s\n' "$meta_out" | grep 'runtime_name: item'
printf '%s\n' "$meta_out" | grep 'name: createItem'
printf '%s\n' "$meta_out" | grep 'kind: COMMAND'

(
  cd "$out_dir"
  exec java \
    -Dcncf.server.port="${server_port}" \
    -Dcncf.http.baseurl="${server_baseurl}" \
    -cp "$runtime_classpath" \
    org.goldenport.cncf.CncfMain \
    --discover=classes \
    server
) >"$server_log" 2>&1 &
server_pid=$!
echo "$server_pid" > "$out_dir/server.pid"
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

create_out="$(run_client job-sample.item.create-item --name alpha --title Alpha 2>&1)"
job_id="$(printf '%s\n' "$create_out" | awk '/^cncf-job-/ {print $1}' | tail -n 1)"
if [ -z "$job_id" ]; then
  printf '%s\n' "$create_out"
fi
[ -n "$job_id" ]
printf '%s\n' "$job_id" | grep '^cncf-job-'

await_out="$(run_client job-control.job.await-job-result --id "$job_id" 2>&1)"
printf '%s\n' "$await_out" | grep 'name: alpha'
printf '%s\n' "$await_out" | grep 'title: Alpha'

result_out="$(run_client job-control.job.get-job-result --id "$job_id" 2>&1)"
printf '%s\n' "$result_out" | grep 'name: alpha'
printf '%s\n' "$result_out" | grep 'title: Alpha'

status_out="$(run_client job-control.job.get-job-status --id "$job_id" 2>&1)"
printf '%s\n' "$status_out" | grep 'status: Succeeded'
printf '%s\n' "$status_out" | grep 'result_success: true'
printf '%s\n' "$status_out" | grep 'debug_request_summary: JobSample.Item.createItem'
printf '%s\n' "$status_out" | grep 'job.submitted'
printf '%s\n' "$status_out" | grep 'job.succeeded'

history_out="$(run_client job-control.job.load-job-history --id "$job_id" 2>&1)"
printf '%s\n' "$history_out" | grep 'total_count: 8'
printf '%s\n' "$history_out" | grep 'job.submitted'
printf '%s\n' "$history_out" | grep 'job.async.queued'
printf '%s\n' "$history_out" | grep 'task.succeeded'
printf '%s\n' "$history_out" | grep 'task.transaction.committed'
printf '%s\n' "$history_out" | grep 'job.succeeded'

echo JOB_SURFACE_OK
