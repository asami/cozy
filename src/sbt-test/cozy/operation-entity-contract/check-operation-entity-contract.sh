#!/bin/sh
set -eu

script_dir=$(CDPATH= cd -- "$(dirname "$0")" && pwd)
sample_dir=/Users/asami/src/dev2026/cncf-samples/samples/03.b-operation-entity-lab
out_dir="$script_dir/out.d"
cml_file="$sample_dir/src/main/cozy/operation-entity.cml"
factory_class=org.sample.operationentity.OperationEntitySampleFactory
factory_src="$sample_dir/src/main/scala/org/sample/operationentity/OperationEntitySampleFactory.scala"
factory_dst_dir="$out_dir/src/main/scala/org/sample/operationentity"
person_id=major-minor-entity-person-1742198400000-abcd1234

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

(
  cd "$out_dir"
  sbt --batch compile
)

help_out="$(run_command help operation-entity-sample.person-app.get-person-card 2>&1)"
printf '%s\n' "$help_out" | grep 'operation-entity-sample.person-app.get-person-card'
printf '%s\n' "$help_out" | grep 'name: getPersonCard'
printf '%s\n' "$help_out" | grep 'PersonCard'

meta_out="$(run_command operation-entity-sample.meta.describe --format yaml 2>&1)"
printf '%s\n' "$meta_out" | grep 'runtime_name: person-app'
printf '%s\n' "$meta_out" | grep 'kind: QUERY'
printf '%s\n' "$meta_out" | grep 'input_type: PersonLookup'
printf '%s\n' "$meta_out" | grep 'output_type: PersonCard'
printf '%s\n' "$meta_out" | grep 'input_value_kind: QUERY_VALUE'

query_out="$(run_command operation-entity-sample.person-app.get-person-card --person-id "$person_id")"
printf '%s\n' "$query_out" | grep '^name: Alice$'

echo OPERATION_ENTITY_CONTRACT_OK
