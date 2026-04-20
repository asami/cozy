#!/bin/sh
set -eu

script_dir=$(CDPATH= cd -- "$(dirname "$0")" && pwd)
sample_dir=/Users/asami/src/dev2026/cncf-samples/samples/04.e-crud-explicit-sync-lab
out_dir="$script_dir/out.d"
cml_file="$sample_dir/src/main/cozy/crud.cml"
dbpath="$out_dir/target/cncf.d/crud-explicit-sync.sqlite"
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
  mkdir -p target/cncf.d
  sbt --batch compile
)

create_help="$(run_command help crud.entity.create-item 2>&1)"
printf '%s
' "$create_help" | grep 'crud.entity.create-item'
printf '%s
' "$create_help" | grep 'CreateItemResult'

load_help="$(run_command help crud.entity.load-item 2>&1)"
printf '%s
' "$load_help" | grep 'crud.entity.load-item'
printf '%s
' "$load_help" | grep 'Option\[Item\]'

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

client_job_id="$(run_client crud.entity.create-item --name alpha --title Alpha 2>&1 | awk '/^cncf-job-/ {print $1}' | tail -n 1)"
[ -n "$client_job_id" ]
printf '%s
' "$client_job_id" | grep '^cncf-job-'

client_await_json="$(run_client job-control.job.await-job-result --id "$client_job_id" 2>&1 | grep '^{' | tail -n 1)"
printf '%s
' "$client_await_json" | grep '"id"'
client_item_id="$(printf '%s
' "$client_await_json" | python3 -c 'import json,sys; print(json.loads(sys.stdin.read())["id"])')"
[ -n "$client_item_id" ]

client_load_json="$(run_client crud.entity.load-item --id "$client_item_id" 2>&1 | grep '^{' | tail -n 1)"
printf '%s
' "$client_load_json" | grep "$client_item_id"
printf '%s
' "$client_load_json" | grep '"name":"alpha"'
printf '%s
' "$client_load_json" | grep '"title":"Alpha"'

rm -f "$dbpath"
create_out="$(
  run_command \
    --textus.runtime.command.execution-mode sync-direct-no-job \
    --cncf.datastore.sqlite.path="$dbpath" \
    crud.entity.create-item \
    --name alpha \
    --title Alpha 2>&1
)"
printf '%s
' "$create_out" | grep '^id: '
item_id="$(printf '%s
' "$create_out" | awk '/^id: / {print $2}' | tail -n 1)"
[ -n "$item_id" ]

stored_row="$(sqlite3 "$dbpath" "select id, title from item where id = '$item_id';")"
printf '%s
' "$stored_row" | grep "^$item_id|Alpha$"

echo CRUD_EXPLICIT_SYNC_OK
