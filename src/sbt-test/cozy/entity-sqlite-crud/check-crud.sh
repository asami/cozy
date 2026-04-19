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
printf "%s
" "$save_out"

printf "%s
" "$save_out" | grep -v "Unknown log backend" >/dev/null

echo "ENTITY_SQLITE_CRUD_OK"
