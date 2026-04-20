#!/bin/sh
set -eu

script_dir=$(CDPATH= cd -- "$(dirname "$0")" && pwd)
sample_dir=/Users/asami/src/dev2026/cncf-samples/samples/04.a-crud-seed-import-lab
out_dir="$script_dir/out.d"
cml_file="$sample_dir/src/main/cozy/crud.cml"

rm -rf "$out_dir"
"$script_dir/cozy-delegate.sh" modeler-scala "$cml_file" --save="$out_dir"

mkdir -p "$out_dir/car.d/meta" "$out_dir/entity.d"
cp "$sample_dir/car.d/meta/component-descriptor.yaml" "$out_dir/car.d/meta/component-descriptor.yaml"
cp "$sample_dir/entity.d/crud.yaml" "$out_dir/entity.d/crud.yaml"

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

load_help="$(run_command help crud.entity.load-item 2>&1)"
printf '%s\n' "$load_help" | grep 'crud.entity.load-item'
printf '%s\n' "$load_help" | grep 'Option\[Item\]'

search_help="$(run_command help crud.entity.search-item-record 2>&1)"
printf '%s\n' "$search_help" | grep 'crud.entity.search-item-record'
printf '%s\n' "$search_help" | grep 'SearchResult\[Item\]'

load_out="$(run_command crud.entity.load-item --id major-minor-entity-item-20260327000000-aaa111 2>&1)"
printf '%s\n' "$load_out" | grep 'id: major-minor-entity-item-20260327000000-aaa111'
printf '%s\n' "$load_out" | grep 'name: alpha'
printf '%s\n' "$load_out" | grep 'title: Alpha'

search_out="$(run_command crud.entity.search-item-record --name alpha --query.include_total true 2>&1)"
printf '%s\n' "$search_out" | grep 'query:'
printf '%s\n' "$search_out" | grep 'name: alpha'
printf '%s\n' "$search_out" | grep 'title: Alpha'
printf '%s\n' "$search_out" | grep 'total_count: 1'
printf '%s\n' "$search_out" | grep 'fetched_count: 1'

meta_out="$(run_command crud.meta.describe --format yaml 2>&1)"
printf '%s\n' "$meta_out" | grep 'runtime_name: entity'
printf '%s\n' "$meta_out" | grep 'operation_definitions:'
printf '%s\n' "$meta_out" | grep 'name: createItem'
printf '%s\n' "$meta_out" | grep 'name: getItem'
printf '%s\n' "$meta_out" | grep 'name: listItems'

echo CRUD_SEED_IMPORT_OK
