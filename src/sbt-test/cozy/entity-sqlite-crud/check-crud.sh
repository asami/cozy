#!/usr/bin/env sh
set -eu

cd out.d
mkdir -p src/main/scala/domain .cncf
cat > src/main/scala/domain/Main.scala <<'EOF'
package domain

import org.goldenport.cncf.cli.CncfRuntime
import org.goldenport.cncf.component.{Component, ComponentCreate, ComponentOrigin}
import org.goldenport.cncf.subsystem.Subsystem
import domain.impl.ComponentFactory

object Main {
  def main(args: Array[String]): Unit = {
    CncfRuntime().run(args, _extraComponents)
  }

  private def _extraComponents(subsystem: Subsystem): Seq[Component] = {
    val params = ComponentCreate(subsystem, ComponentOrigin.Main)
    ComponentFactory().create(params)
  }
}
EOF
cat > .cncf/config.conf <<'EOF'
cncf.runtime.mode = command
cncf.datastore.sqlite.path = target/cncf.d/cncf-command.sqlite3
cncf.logging.backend = nop
cncf.logging.file.path = target/cncf.d/trace.log
cncf.logging.level = trace
EOF
mkdir -p target/cncf.d
rm -f target/cncf.d/cncf-command.sqlite3

id="sys-sys-entity-person-1773749500000-3aaaaaaaaaaaaaaaaaaaaa"
MODE="--textus.runtime.command.execution-mode sync-direct-no-job"
SEC="--privilege content_manager"
DB="$(pwd)/target/cncf.d/cncf-command.sqlite3"
STORE="--cncf.datastore.sqlite.path=$DB"

sbt --batch compile

save_out=$(sbt --batch "runMain org.goldenport.cncf.CncfMain --discover=classes command --format yaml ${MODE} ${STORE} domain.entity.savePerson --id $id --name taro ${SEC}" 2>&1)
printf "%s\n" "$save_out"

printf "%s\n" "$save_out" | grep -v "Unknown log backend" >/dev/null

if [ ! -f "$DB" ]; then
  echo "SQLite file not found: $DB" >&2
  exit 1
fi

tables="$(sqlite3 "$DB" ".tables")"
printf "%s\n" "$tables"

stored_row=""
for table in person simple_entity; do
  if printf "%s\n" "$tables" | grep -Eq "(^|[[:space:]])${table}($|[[:space:]])"; then
    candidate="$(sqlite3 "$DB" "select id, name from ${table} where id = '$id';")"
    if [ -n "$candidate" ]; then
      stored_row="$candidate"
      break
    fi
  fi
done

printf "%s\n" "$stored_row"
printf "%s\n" "$stored_row" | grep -q "^${id}|taro$"

load_out=$(sbt --batch "runMain org.goldenport.cncf.CncfMain --discover=classes command --format yaml ${MODE} ${STORE} domain.entity.loadPerson --id $id ${SEC}" 2>&1)
printf "%s\n" "$load_out"
printf "%s\n" "$load_out" | grep -q "name: taro"

update_out=$(sbt --batch "runMain org.goldenport.cncf.CncfMain --discover=classes command --format yaml ${MODE} ${STORE} domain.entity.updatePerson --id $id --name jiro ${SEC}" 2>&1)
printf "%s\n" "$update_out"

updated_row=""
for table in person simple_entity; do
  if printf "%s\n" "$tables" | grep -Eq "(^|[[:space:]])${table}($|[[:space:]])"; then
    candidate="$(sqlite3 "$DB" "select id, name from ${table} where id = '$id';")"
    if [ -n "$candidate" ]; then
      updated_row="$candidate"
      break
    fi
  fi
done

printf "%s\n" "$updated_row"
printf "%s\n" "$updated_row" | grep -q "^${id}|jiro$"

load_updated_out=$(sbt --batch "runMain org.goldenport.cncf.CncfMain --discover=classes command --format yaml ${MODE} ${STORE} domain.entity.loadPerson --id $id ${SEC}" 2>&1)
printf "%s\n" "$load_updated_out"
printf "%s\n" "$load_updated_out" | grep -q "name: jiro"

delete_out=$(sbt --batch "runMain org.goldenport.cncf.CncfMain --discover=classes command --format yaml ${MODE} ${STORE} domain.entity.deletePerson --id $id ${SEC}" 2>&1)
printf "%s\n" "$delete_out"

deleted_row=""
for table in person simple_entity; do
  if printf "%s\n" "$tables" | grep -Eq "(^|[[:space:]])${table}($|[[:space:]])"; then
    candidate="$(sqlite3 "$DB" "select id, name, aliveness, post_status from ${table} where id = '$id';")"
    if [ -n "$candidate" ]; then
      deleted_row="$candidate"
      break
    fi
  fi
done

printf "%s\n" "$deleted_row"
printf "%s\n" "$deleted_row" | grep -q "^${id}|jiro|dead|archived$"

load_deleted_out=$(sbt --batch "runMain org.goldenport.cncf.CncfMain --discover=classes command --format yaml ${MODE} ${STORE} domain.entity.loadPerson --id $id ${SEC}" 2>&1)
printf "%s\n" "$load_deleted_out"
printf "%s\n" "$load_deleted_out" | grep -q "code: 404"
printf "%s\n" "$load_deleted_out" | grep -q "symptom: not-found"

echo "ENTITY_SQLITE_CRUD_OK"
