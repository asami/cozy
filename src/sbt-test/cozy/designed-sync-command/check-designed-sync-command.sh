#!/bin/sh
set -eu

script_dir=$(CDPATH= cd -- "$(dirname "$0")" && pwd)
sample_dir=/Users/asami/src/dev2026/cncf-samples/samples/04.a-designed-sync-command-lab
out_dir="$script_dir/out.d"
cml_file="$sample_dir/src/main/cozy/cqrs.cml"

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

help_out="$(run_command help designed-sync.item.create-item 2>&1)"
printf '%s\n' "$help_out" | grep 'designed-sync.item.create-item'
printf '%s\n' "$help_out" | grep 'CreateItemResult'

meta_out="$(run_command designed-sync.meta.describe --format yaml 2>&1)"
printf '%s\n' "$meta_out" | grep 'runtime_name: item'
printf '%s\n' "$meta_out" | grep 'name: createItem'
printf '%s\n' "$meta_out" | grep 'kind: COMMAND'
printf '%s\n' "$meta_out" | grep 'input_type: CreateItem'
printf '%s\n' "$meta_out" | grep 'output_type: CreateItemResult'

create_out="$(run_command designed-sync.item.create-item --name beta --title Beta 2>&1)"
printf '%s\n' "$create_out" | grep '^name: beta$'
printf '%s\n' "$create_out" | grep '^title: Beta$'

if printf '%s\n' "$create_out" | grep -q '^cncf-job-'; then
  exit 1
fi

echo DESIGNED_SYNC_COMMAND_OK
