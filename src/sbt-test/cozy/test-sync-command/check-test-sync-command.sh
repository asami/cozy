#!/bin/sh
set -eu

script_dir=$(CDPATH= cd -- "$(dirname "$0")" && pwd)
sample_dir=/Users/asami/src/dev2026/cncf-samples/samples/04.b-test-sync-command-lab
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

help_out="$(run_command help test-sync.item.create-item 2>&1)"
printf '%s\n' "$help_out" | grep 'test-sync.item.create-item'
printf '%s\n' "$help_out" | grep 'CreateItemResult'

meta_out="$(run_command test-sync.meta.describe --format yaml 2>&1)"
printf '%s\n' "$meta_out" | grep 'runtime_name: item'
printf '%s\n' "$meta_out" | grep 'name: createItem'
printf '%s\n' "$meta_out" | grep 'kind: COMMAND'

default_out="$(run_command TestSync.Item.createItem --name beta --title Beta 2>&1)"
printf '%s\n' "$default_out" | grep '^cncf-job-'

default_envelope="$(run_command TestSync.Item.createItem --name beta --title Beta --textus.output.shape envelope --textus.output.format yaml 2>&1)"
printf '%s\n' "$default_envelope" | grep 'interface-shape: job'

sync_out="$(run_command --textus.runtime.command.execution-mode sync-job-async-interface TestSync.Item.createItem --name beta --title Beta 2>&1)"
printf '%s\n' "$sync_out" | grep '^cncf-job-'

sync_envelope="$(run_command --textus.runtime.command.execution-mode sync-job-async-interface TestSync.Item.createItem --name beta --title Beta --textus.output.shape envelope --textus.output.format yaml 2>&1)"
printf '%s\n' "$sync_envelope" | grep 'interface-shape: job'
printf '%s\n' "$sync_envelope" | grep 'requested-mode: sync-job-async-interface'

echo TEST_SYNC_COMMAND_OK
