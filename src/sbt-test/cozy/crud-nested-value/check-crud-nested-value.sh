#!/bin/sh
set -eu

script_dir=$(CDPATH= cd -- "$(dirname "$0")" && pwd)
sample_dir=/Users/asami/src/dev2026/cncf-samples/samples/04.f-crud-nested-value-lab
out_dir="$script_dir/out.d"
cml_file="$sample_dir/src/main/cozy/crud-nested-value.cml"
dbpath="$out_dir/target/cncf.d/crud-nested-value.sqlite"

rm -rf "$out_dir"
"$script_dir/cozy-delegate.sh" modeler-scala "$cml_file" --save="$out_dir"

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

create_help="$(run_command help crud-nested-value-sample.entity.create-person 2>&1)"
printf '%s
' "$create_help" | grep 'crud-nested-value-sample.entity.create-person'
printf '%s
' "$create_help" | grep 'createPerson'

load_help="$(run_command help crud-nested-value-sample.entity.load-person 2>&1)"
printf '%s
' "$load_help" | grep 'crud-nested-value-sample.entity.load-person'
printf '%s
' "$load_help" | grep 'Option\[Person\]'

meta_out="$(run_command crud-nested-value-sample.meta.describe --format yaml 2>&1)"
printf '%s
' "$meta_out" | grep 'runtime_name: entity'
printf '%s
' "$meta_out" | grep 'name: person'

mkdir -p "$out_dir/target/cncf.d"
rm -f "$dbpath"

create_out="$(run_command --textus.runtime.command.execution-mode sync-direct-no-job --cncf.datastore.sqlite.path="$dbpath" crud-nested-value-sample.entity.create-person --name alice --address.street Marunouchi-1-2-3 --address.city Tokyo --address.country.value JP 2>&1)"
printf '%s
' "$create_out" | grep '^id: '
person_id="$(printf '%s
' "$create_out" | awk '/^id: / {print $2}' | tail -n 1)"
[ -n "$person_id" ]

stored_row="$(sqlite3 "$dbpath" "select id, address from person where id = '$person_id';")"
printf '%s
' "$stored_row" | grep "^$person_id|"
printf '%s
' "$stored_row" | grep '"street":"Marunouchi-1-2-3"'
printf '%s
' "$stored_row" | grep '"city":"Tokyo"'
printf '%s
' "$stored_row" | grep '"country":{"value":"JP"}'

echo CRUD_NESTED_VALUE_OK
