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
cncf.logging.backend = file
cncf.logging.file.path = target/cncf.d/trace.log
cncf.logging.level = trace
EOF
rm -f target/cncf.d/cncf-command.sqlite3

id="sys-sys-entity-person-1773749500000-3aaaaaaaaaaaaaaaaaaaaa"

sbt --batch compile

save_out=$(sbt --batch "run command --format yaml domain.entity.savePerson --id $id --name taro" 2>&1)
printf "%s\n" "$save_out"

load1_out=$(sbt --batch "run command --format yaml domain.entity.loadPerson --id $id" 2>&1)
printf "%s\n" "$load1_out"
printf "%s\n" "$load1_out" | grep -q "name: taro"

update_out=$(sbt --batch "run command --format yaml domain.entity.updatePerson --id $id --name jiro" 2>&1)
printf "%s\n" "$update_out"

load2_out=$(sbt --batch "run command --format yaml domain.entity.loadPerson --id $id" 2>&1)
printf "%s\n" "$load2_out"
printf "%s\n" "$load2_out" | grep -q "name: jiro"

delete_out=$(sbt --batch "run command --format yaml domain.entity.deletePerson --id $id" 2>&1)
printf "%s\n" "$delete_out"

load3_out=$(sbt --batch "run command --format yaml domain.entity.loadPerson --id $id" 2>&1)
printf "%s\n" "$load3_out"
printf "%s\n" "$load3_out" | grep -q "code: 404"

test -f target/cncf.d/cncf-command.sqlite3
