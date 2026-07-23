#!/usr/bin/env sh
set -eu

mkdir -p out.d/src/main/scala/domain
cp -R generated.d/target/scala-3.3.8/src_managed/main/scala/domain/. out.d/src/main/scala/domain/
cp -R generated.d/src/main/scala/domain/. out.d/src/main/scala/domain/
rm -f out.d/src/main/cozy/sample.cml

cat > out.d/src/main/scala/domain/Main.scala <<'EOF'
package domain

import org.goldenport.cncf.cli.CncfRuntime
import org.goldenport.cncf.component.{Component, ComponentCreate, ComponentOrigin}
import org.goldenport.cncf.subsystem.Subsystem
import domain.impl.ComponentFactory

object Main {
  def main(args: Array[String]): Unit =
    CncfRuntime().run(args, _extraComponents)

  private def _extraComponents(subsystem: Subsystem): Seq[Component] = {
    val params = ComponentCreate(subsystem, ComponentOrigin.Main)
    Vector(ComponentFactory().createPrimary(params))
  }
}
EOF

mkdir -p out.d/.cncf out.d/target/cncf.d
cat > out.d/.cncf/config.conf <<'EOF'
cncf.runtime.mode = command
cncf.datastore.sqlite.path = target/cncf.d/scalar-value-roundtrip.sqlite
cncf.logging.backend = nop
EOF

state_source=out.d/src/main/scala/domain/value/ReviewRunState.scala
priority_source=out.d/src/main/scala/domain/value/ReviewPriority.scala
code_source=out.d/src/main/scala/domain/datatype/ReviewCode.scala
entity_source=out.d/src/main/scala/domain/entity/ReviewDiagnosis.scala

grep 'case m: ReviewRunState => Consequence.success(m)' "$state_source"
grep 'case m: Record => createC(m)' "$state_source"
grep 'ValueReader\[String\].*readC(other).*createC(value).*Consequence.valueInvalid' "$state_source"
grep 'case m: ReviewPriority => Consequence.success(m)' "$priority_source"
grep 'case m: Record => createC(m)' "$priority_source"
grep 'ValueReader\[Int\].*readC(other).*createC(value).*Consequence.valueInvalid' "$priority_source"
grep 'case m: ReviewCode => Consequence.success(m)' "$code_source"
grep 'ValueReader\[String\].*readC(other).*createC(value).*Consequence.valueInvalid' "$code_source"
grep 'case m: domain.value.ReviewRunState => m.toDataStore()' "$entity_source"
grep 'case m: domain.value.ReviewPriority => m.toDataStore()' "$entity_source"
grep 'case m: domain.datatype.ReviewCode => m.toDataStore()' "$entity_source"

cd out.d

dbpath="$(pwd)/target/cncf.d/scalar-value-roundtrip.sqlite"
mode="--textus.runtime.command.execution-mode sync-direct-no-job"
store="--cncf.datastore.sqlite.path=$dbpath"
privilege="--privilege content_manager"
present_id="sys-sys-entity-review_diagnosis-1784800000000-3aaaaaaaaaaaaaaaaaaaaa"
absent_id="sys-sys-entity-review_diagnosis-1784800000001-3bbbbbbbbbbbbbbbbbbbbb"

rm -f "$dbpath"
sbt --batch compile

run_command() {
  sbt --batch "runMain domain.Main command --format yaml ${mode} ${store} $*"
}

present_save="$(run_command ScalarRoundtrip.entity.saveReviewDiagnosis --id "$present_id" --code REVIEW-101 --state running --previousState queued --priority 7 "$privilege" 2>&1)"
printf '%s\n' "$present_save"

absent_save="$(run_command ScalarRoundtrip.entity.saveReviewDiagnosis --id "$absent_id" --code REVIEW-102 --state queued --priority 3 "$privilege" 2>&1)"
printf '%s\n' "$absent_save"

table_name="$(sqlite3 "$dbpath" "select name from sqlite_master where type = 'table' and name in ('review_diagnosis', 'reviewdiagnosis') order by name limit 1;")"
test -n "$table_name"

present_row="$(sqlite3 "$dbpath" "select id, code, state, previousState, priority from ${table_name} where id = '$present_id';")"
printf '%s\n' "$present_row"
printf '%s\n' "$present_row" | grep -q "^${present_id}|REVIEW-101|running|queued|7$"

absent_row="$(sqlite3 "$dbpath" "select id, code, state, coalesce(previousState, '<absent>'), priority from ${table_name} where id = '$absent_id';")"
printf '%s\n' "$absent_row"
printf '%s\n' "$absent_row" | grep -q "^${absent_id}|REVIEW-102|queued|<absent>|3$"

present_load="$(run_command ScalarRoundtrip.entity.loadReviewDiagnosis --id "$present_id" "$privilege" 2>&1)"
printf '%s\n' "$present_load"
printf '%s\n' "$present_load" | grep -q 'code: REVIEW-101'
printf '%s\n' "$present_load" | grep -q 'state:'
printf '%s\n' "$present_load" | grep -q 'value: running'
printf '%s\n' "$present_load" | grep -q 'value: queued'
printf '%s\n' "$present_load" | grep -q 'value: 7'

upsert_out="$(run_command ScalarRoundtrip.entity.saveReviewDiagnosis --id "$present_id" --code REVIEW-101 --state completed --previousState running --priority 9 "$privilege" 2>&1)"
printf '%s\n' "$upsert_out"

updated_row="$(sqlite3 "$dbpath" "select id, code, state, previousState, priority from ${table_name} where id = '$present_id';")"
printf '%s\n' "$updated_row"
printf '%s\n' "$updated_row" | grep -q "^${present_id}|REVIEW-101|completed|running|9$"

updated_load="$(run_command ScalarRoundtrip.entity.loadReviewDiagnosis --id "$present_id" "$privilege" 2>&1)"
printf '%s\n' "$updated_load"
printf '%s\n' "$updated_load" | grep -q 'value: completed'
printf '%s\n' "$updated_load" | grep -q 'value: running'
printf '%s\n' "$updated_load" | grep -q 'value: 9'

sqlite3 "$dbpath" "update ${table_name} set code = 'x' where id = '$present_id';"
invalid_load="$(run_command ScalarRoundtrip.entity.loadReviewDiagnosis --id "$present_id" "$privilege" 2>&1)"
printf '%s\n' "$invalid_load"
printf '%s\n' "$invalid_load" | grep -q 'status=400'
printf '%s\n' "$invalid_load" | grep -q 'value.invalid'
printf '%s\n' "$invalid_load" | grep -q 'value must have length >= 3'

sqlite3 "$dbpath" "update ${table_name} set code = 'REVIEW-101', priority = 'not-an-int' where id = '$present_id';"
malformed_load="$(run_command ScalarRoundtrip.entity.loadReviewDiagnosis --id "$present_id" "$privilege" 2>&1)"
printf '%s\n' "$malformed_load"
printf '%s\n' "$malformed_load" | grep -q 'status=400'
printf '%s\n' "$malformed_load" | grep -q 'value.invalid-datatype:int'

echo ENTITY_SCALAR_VALUE_SQLITE_ROUNDTRIP_OK
