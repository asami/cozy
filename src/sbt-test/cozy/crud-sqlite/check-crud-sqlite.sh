#!/bin/sh
set -eu

script_dir=$(CDPATH= cd -- "$(dirname "$0")" && pwd)
sample_dir=/Users/asami/src/dev2026/cncf-samples/samples/04.c-crud-sqlite-lab
out_dir="$script_dir/out.d"
cml_file="$sample_dir/src/main/cozy/crud.cml"
dbpath="$out_dir/target/cncf.d/crud-sqlite.sqlite"

rm -rf "$out_dir"
"$script_dir/cozy-delegate.sh" modeler-scala "$cml_file" --save="$out_dir"

mkdir -p "$out_dir/entity.d"
cp "$sample_dir/entity.d/crud.yaml" "$out_dir/entity.d/crud.yaml"

run_command() {
  (
    cd "$out_dir"
    sbt --batch "runMain org.goldenport.cncf.CncfMain --discover=classes command $*"
  )
}

(
  cd "$out_dir"
  mkdir -p target/cncf.d
  sbt --batch compile
)

load_help="$(run_command help crud.entity.load-item 2>&1)"
printf '%s\n' "$load_help" | grep 'crud.entity.load-item'
printf '%s\n' "$load_help" | grep 'Option\[Item\]'

search_help="$(run_command help crud.entity.search-item-record 2>&1)"
printf '%s\n' "$search_help" | grep 'crud.entity.search-item-record'
printf '%s\n' "$search_help" | grep 'SearchResult\[Item\]'

seed_load="$(run_command --cncf.datastore.sqlite.path=$dbpath crud.entity.load-item --id major-minor-entity-item-20260328000000-aaa111 2>&1)"
printf '%s\n' "$seed_load" | grep 'name: alpha'
printf '%s\n' "$seed_load" | grep 'title: Alpha'

seed_search="$(run_command --cncf.datastore.sqlite.path=$dbpath crud.entity.search-item-record --name alpha --query.include_total true 2>&1)"
printf '%s\n' "$seed_search" | grep 'query:'
printf '%s\n' "$seed_search" | grep 'name: alpha'
printf '%s\n' "$seed_search" | grep 'title: Alpha'
printf '%s\n' "$seed_search" | grep 'total_count: 1'
printf '%s\n' "$seed_search" | grep 'fetched_count: 1'

created_id=$(
  run_command \
    --textus.runtime.command.execution-mode sync-direct-no-job \
    --cncf.datastore.sqlite.path=$dbpath \
    crud.entity.create-item \
    --name delta \
    --title Delta 2>&1 \
    | awk '/^id: / {print $2}'
)

created_load="$(run_command --cncf.datastore.sqlite.path=$dbpath crud.entity.load-item --id $created_id 2>&1)"
printf '%s\n' "$created_load" | grep "id: $created_id"
printf '%s\n' "$created_load" | grep 'name: delta'
printf '%s\n' "$created_load" | grep 'title: Delta'

stored_row="$(sqlite3 "$dbpath" "select id, title from item where id = '$created_id';")"
printf '%s\n' "$stored_row" | grep "^$created_id|Delta$"

meta_out="$(run_command crud.meta.describe --format yaml 2>&1)"
printf '%s\n' "$meta_out" | grep 'runtime_name: entity'
printf '%s\n' "$meta_out" | grep 'operation_definitions:'
printf '%s\n' "$meta_out" | grep 'name: createItem'
printf '%s\n' "$meta_out" | grep 'name: getItem'
printf '%s\n' "$meta_out" | grep 'name: listItems'

echo CRUD_SQLITE_OK
