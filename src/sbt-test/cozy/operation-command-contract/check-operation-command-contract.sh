#!/bin/sh
set -eu

script_dir=$(CDPATH= cd -- "$(dirname "$0")" && pwd)
sample_dir=/Users/asami/src/dev2026/cncf-samples/samples/03.a-operation-command-lab
out_dir="$script_dir/out.d"
cml_file="$sample_dir/src/main/cozy/operation-command-contract.cml"
factory_class=org.sample.operationcommand.OperationCommandContractSampleFactory
factory_src="$sample_dir/src/main/scala/org/sample/operationcommand/OperationCommandContractSampleFactory.scala"
factory_dst_dir="$out_dir/src/main/scala/org/sample/operationcommand"

rm -rf "$out_dir"
"$script_dir/cozy-delegate.sh" modeler-scala "$cml_file" --save="$out_dir"
mkdir -p "$factory_dst_dir"
cp "$factory_src" "$factory_dst_dir/"

run_command() {
  (
    cd "$out_dir"
    sbt --batch "runMain org.goldenport.cncf.CncfMain --component-factory-class $factory_class command $*"
  )
}

run_client() {
  (
    cd "$out_dir"
    sbt --batch "runMain org.goldenport.cncf.CncfMain --component-factory-class $factory_class client $*"
  )
}

(
  cd "$out_dir"
  sbt --batch compile
)

help_out="$(run_command help operation-command-contract-sample.greeting.submit-greeting 2>&1)"
printf '%s
' "$help_out" | grep 'operation-command-contract-sample.greeting.submit-greeting'
printf '%s
' "$help_out" | grep 'name: submitGreeting'
printf '%s
' "$help_out" | grep 'GreetingAccepted'

meta_out="$(run_command operation-command-contract-sample.meta.describe --format yaml 2>&1)"
printf '%s
' "$meta_out" | grep 'runtime_name: greeting'
printf '%s
' "$meta_out" | grep 'kind: COMMAND'
printf '%s
' "$meta_out" | grep 'input_type: GreetingCommand'
printf '%s
' "$meta_out" | grep 'output_type: GreetingAccepted'
printf '%s
' "$meta_out" | grep 'input_value_kind: COMMAND_VALUE'

(
  cd "$out_dir"
  sbt --batch "runMain org.goldenport.cncf.CncfMain --component-factory-class $factory_class server" > target/server.log 2>&1 &
  server_pid=$!
  trap 'kill "$server_pid" >/dev/null 2>&1 || true' EXIT INT TERM
  sleep 2
  submit_out="$(run_client operation-command-contract-sample.greeting.submit-greeting --name Alice)"
  job_id="$(printf '%s
' "$submit_out" | grep '^cncf-job-' | tail -n 1)"
  printf '%s
' "$job_id" | grep '^cncf-job-'
  await_out="$(run_client job-control.job.await-job-result --id "$job_id")"
  printf '%s
' "$await_out" | grep '"status":"accepted"'
  printf '%s
' "$await_out" | grep '"name":"Alice"'
)

echo OPERATION_COMMAND_CONTRACT_OK
