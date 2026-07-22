#!/bin/sh
set -eu

script_dir=$(CDPATH= cd -- "$(dirname "$0")" && pwd)
out_dir="$script_dir/out.d"
compile_dir="$script_dir/compile.d"
source_dir="$compile_dir/src/main/scala/generated"

generated=$(find "$out_dir/target" -type f -name 'EvaluationComponent.scala' -print | head -n 1)
test -n "$generated"
grep 'CmlOperationEvaluationDeclaration' "$generated"
grep 'EvaluationAdmissionRequirement.Required' "$generated"
grep 'OperationEvaluationOutcome.Cancellation' "$generated"
grep 'OperationEvaluationName.unsafe("execution-plan")' "$generated"

rm -rf "$source_dir"
mkdir -p "$source_dir"
find "$out_dir" -type f -name '*.scala' -exec cp '{}' "$source_dir" ';'

(
  cd "$compile_dir"
  sbt --batch compile
)

echo OPERATION_EVALUATION_CONTRACT_OK
