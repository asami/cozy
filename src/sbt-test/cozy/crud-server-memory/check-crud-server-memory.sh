#!/bin/sh
set -eu

script_dir=$(CDPATH= cd -- "$(dirname "$0")" && pwd)
sample_dir=/Users/asami/src/dev2026/cncf-samples/samples/04.d-crud-server-memory-lab
out_dir="$script_dir/out.d"
cml_file="$sample_dir/src/main/cozy/crud.cml"
server_log="$out_dir/server.log"

rm -rf "$out_dir"
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

create_help="$(run_command help crud.entity.create-item 2>&1)"
printf '%s
' "$create_help" | grep 'crud.entity.create-item'
printf '%s
' "$create_help" | grep 'CreateItemResult'

await_help="$(run_command help job-control.job.await-job-result 2>&1)"
printf '%s
' "$await_help" | grep 'job-control.job.await-job-result'
printf '%s
' "$await_help" | grep 'arguments:'

meta_out="$(run_command crud.meta.describe --format yaml 2>&1)"
printf '%s
' "$meta_out" | grep 'runtime_name: entity'
printf '%s
' "$meta_out" | grep 'name: createItem'
printf '%s
' "$meta_out" | grep 'name: getItem'
printf '%s
' "$meta_out" | grep 'name: listItems'

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

job_id="$(run_client crud.entity.create-item --name alpha --title Alpha 2>&1 | awk '/^cncf-job-/ {print $1}' | tail -n 1)"
[ -n "$job_id" ]
printf '%s
' "$job_id" | grep '^cncf-job-'

await_json="$(run_client job-control.job.await-job-result --id "$job_id" 2>&1 | grep '^{' | tail -n 1)"
printf '%s
' "$await_json" | grep '"id"'
item_id="$(printf '%s
' "$await_json" | python3 -c 'import json,sys; print(json.loads(sys.stdin.read())["id"])')"

load_json="$(run_client crud.entity.load-item --id "$item_id" 2>&1 | grep '^{' | tail -n 1)"
printf '%s
' "$load_json" | grep '"id"'
printf '%s
' "$load_json" | grep '"name":"alpha"'
printf '%s
' "$load_json" | grep '"title":"Alpha"'

echo CRUD_SERVER_MEMORY_OK
