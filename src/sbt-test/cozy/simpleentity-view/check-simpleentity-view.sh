#!/bin/sh
set -eu

script_dir=$(CDPATH= cd -- "$(dirname "$0")" && pwd)
sample_dir=/Users/asami/src/dev2026/cncf-samples/samples/10.b-simpleentity-view-lab
out_dir="$script_dir/out.d"
cml_file="$sample_dir/src/main/cozy/simpleentity-view.cml"

rm -rf "$out_dir"
"$script_dir/cozy-delegate.sh" modeler-scala "$cml_file" --save="$out_dir"

mkdir -p "$out_dir/car.d/meta" "$out_dir/entity.d"
cp "$sample_dir/car.d/meta/component-descriptor.yaml" "$out_dir/car.d/meta/component-descriptor.yaml"
cp "$sample_dir/entity.d/simpleentity-view.yaml" "$out_dir/entity.d/simpleentity-view.yaml"

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

help_out="$(run_command help simple-entity-view-sample.view.load-person 2>&1)"
printf '%s\n' "$help_out" | grep 'simple-entity-view-sample.view.load-person'

load_out="$(run_command simple-entity-view-sample.view.load-person --id tokyo-sales-entity-person-1742198400000-abcd1234 2>&1)"
printf '%s\n' "$load_out" | grep 'name: Alice'
printf '%s\n' "$load_out" | grep 'title: Reader'
printf '%s\n' "$load_out" | grep 'city: Tokyo'

meta_out="$(run_command simple-entity-view-sample.meta.describe --format yaml 2>&1)"
printf '%s\n' "$meta_out" | grep 'runtime_name: view'
printf '%s\n' "$meta_out" | grep 'rebuildable: false'

echo SIMPLEENTITY_VIEW_OK
