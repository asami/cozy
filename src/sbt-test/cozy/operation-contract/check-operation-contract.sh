#!/bin/sh
set -eu

script_dir=$(CDPATH= cd -- "$(dirname "$0")" && pwd)
sample_dir=/Users/asami/src/dev2026/cncf-samples/samples/03-operation
out_dir="$script_dir/out.d"
cml_file="$sample_dir/src/main/cozy/operation-contract.cml"

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

help_out="$(run_command help operation-contract-sample.greeting.greeting 2>&1)"
printf '%s
' "$help_out" | grep 'operation-contract-sample.greeting.greeting'
printf '%s
' "$help_out" | grep 'name: greeting'
printf '%s
' "$help_out" | grep 'GreetingResult'

meta_out="$(run_command operation-contract-sample.meta.describe --format yaml 2>&1)"
printf '%s
' "$meta_out" | grep 'runtime_name: greeting'
printf '%s
' "$meta_out" | grep 'kind: QUERY'
printf '%s
' "$meta_out" | grep 'input_type: GreetingQuery'
printf '%s
' "$meta_out" | grep 'output_type: GreetingResult'

echo OPERATION_CONTRACT_OK
