#!/bin/sh
set -eu

script_dir=$(CDPATH= cd -- "$(dirname "$0")" && pwd)
sample_dir=/Users/asami/src/dev2026/cncf-samples/samples/06-cqrs
out_dir="$script_dir/out.d"
cml_file="$sample_dir/src/main/cozy/cqrs.cml"
server_log="$out_dir/server.log"
item_id="org-sample-entity-item-$(date +%Y%m%d%H%M%S)-scripted111"

cleanup_existing_servers() {
  pids=$(ps -ax | awk '/org\.goldenport\.cncf\.CncfMain --discover=classes server/ && /04-cqrs|cqrs-split\/out\.d/ {print $1}')
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

command_help="$(run_command help cqrs.item.create-item 2>&1)"
printf '%s\n' "$command_help" | grep 'cqrs.item.create-item'
printf '%s\n' "$command_help" | grep 'CreateItemResult'

entity_help="$(run_command help cqrs.entity.create-item-record 2>&1)"
printf '%s\n' "$entity_help" | grep 'cqrs.entity.create-item-record'
printf '%s\n' "$entity_help" | grep 'returns:'

meta_out="$(run_command cqrs.meta.describe --format yaml 2>&1)"
printf '%s\n' "$meta_out" | grep 'runtime_name: item'
printf '%s\n' "$meta_out" | grep 'name: createItem'
printf '%s\n' "$meta_out" | grep 'kind: COMMAND'
printf '%s\n' "$meta_out" | grep 'name: getItem'
printf '%s\n' "$meta_out" | grep 'kind: QUERY'

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

job_id="$(run_client cqrs.entity.create-item-record --id "$item_id" --name gamma --title Gamma 2>&1 | awk '/^cncf-job-/ {print $1}' | tail -n 1)"
[ -n "$job_id" ]
printf '%s\n' "$job_id" | grep '^cncf-job-'

await_json="$(run_client job-control.job.await-job-result --id "$job_id" 2>&1 | grep '^{' | tail -n 1)"
printf '%s\n' "$await_json" | grep '"id"'
printf '%s\n' "$await_json" | grep "$item_id"


load_json="$(run_client cqrs.entity.load-item --id "$item_id" 2>&1 | grep '^{' | tail -n 1)"
printf '%s\n' "$load_json" | grep "\"id\":\"$item_id\""
printf '%s\n' "$load_json" | grep '"name":"gamma"'
printf '%s\n' "$load_json" | grep '"title":"Gamma"'
echo CQRS_SPLIT_OK
