#!/bin/sh
set -eu

script_dir=$(CDPATH= cd -- "$(dirname "$0")" && pwd)
sample_dir=/Users/asami/src/dev2026/cncf-samples/samples/02.b-simpleentity-crud-lab
out_dir="$script_dir/out.d"
cml_file="$sample_dir/src/main/cozy/crud.cml"

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

component_help="$(run_command help simple-entity-crud-lab 2>&1)"
printf '%s\n' "$component_help" | grep 'type: component'
printf '%s\n' "$component_help" | grep 'name: SimpleEntityCrudLab'
printf '%s\n' "$component_help" | grep 'createItem'
printf '%s\n' "$component_help" | grep 'listItems'

service_help="$(run_command help simple-entity-crud-lab.item 2>&1)"
printf '%s\n' "$service_help" | grep 'type: service'
printf '%s\n' "$service_help" | grep 'name: Item'
printf '%s\n' "$service_help" | grep 'createItem'
printf '%s\n' "$service_help" | grep 'getItem'
printf '%s\n' "$service_help" | grep 'listItems'

operation_help="$(run_command help simple-entity-crud-lab.item.create-item 2>&1)"
printf '%s\n' "$operation_help" | grep 'type: operation'
printf '%s\n' "$operation_help" | grep 'name: createItem'
printf '%s\n' "$operation_help" | grep 'cli: simple-entity-crud-lab.item.create-item'
printf '%s\n' "$operation_help" | grep 'CreateItemResult'

meta_out="$(run_command simple-entity-crud-lab.meta.describe --format yaml 2>&1)"
printf '%s\n' "$meta_out" | grep 'runtime_name: item'
printf '%s\n' "$meta_out" | grep 'operation_definitions:'
printf '%s\n' "$meta_out" | grep 'name: createItem'
printf '%s\n' "$meta_out" | grep 'name: getItem'
printf '%s\n' "$meta_out" | grep 'name: listItems'

echo SIMPLEENTITY_CRUD_SURFACE_OK
