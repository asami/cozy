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

create_out="$(run_client crud.entity.create-item --name alpha --title Alpha 2>&1)"
printf '%s
' "$create_out" | grep '^id: '
item_id="$(printf '%s
' "$create_out" | awk '/^id: / {print $2}' | tail -n 1)"
[ -n "$item_id" ]

echo CRUD_SERVER_MEMORY_OK
