#!/usr/bin/env sh
set -eu

cd out.d
mkdir -p src/main/scala/org/goldenport/cncf/cli .cncf

cat > src/main/scala/org/goldenport/cncf/cli/SimpleEntitySyncCommandMain.scala <<'SCALA'
package org.goldenport.cncf.cli

import domain.DomainComponent
import org.goldenport.Consequence
import org.goldenport.cncf.cli.help.CommandProtocolHelp
import org.goldenport.cncf.component.{ComponentCreate, ComponentOrigin}

object SimpleEntitySyncCommandMain {
  private def _splitRuntimeArgs(
    args: Array[String]
  ): (Array[String], Array[String]) = {
    val runtime = Vector.newBuilder[String]
    val command = Vector.newBuilder[String]
    var i = 0
    while (i < args.length) {
      val current = args(i)
      if (current.startsWith("--cncf.") || current.startsWith("-cncf.")) {
        runtime += current
        if (!current.contains("=") && i + 1 < args.length && !args(i + 1).startsWith("-")) {
          runtime += args(i + 1)
          i = i + 1
        }
      } else {
        command += current
      }
      i = i + 1
    }
    (runtime.result().toArray, command.result().toArray)
  }

  def main(args: Array[String]): Unit = {
    val (runtimeArgs, commandArgs) = _splitRuntimeArgs(args)
    val normalized = CommandProtocolHelp.normalizeArgs(commandArgs) match {
      case Left(code) =>
        sys.exit(code)
      case Right(xs) =>
        xs
    }

    val runtime = new CncfRuntime()
    val result = runtime
      .initializeForEmbedding(
        args = runtimeArgs,
        modeHint = Some(RunMode.Command),
        extraComponents = subsystem =>
          DomainComponent.Factory().create(ComponentCreate(subsystem, ComponentOrigin.Main))
      )
      .flatMap { subsystem =>
        runtime
          .parseCommandArgs(subsystem, normalized, RunMode.Command)
          .flatMap(subsystem.execute)
      }

    try {
      result match {
        case Consequence.Success(res) =>
          println(res.show)
          sys.exit(0)
        case Consequence.Failure(conclusion) =>
          println(conclusion.toRecord.toYamlString)
          sys.exit(1)
      }
    } finally {
      runtime.closeEmbedding()
    }
  }
}
SCALA

cat > .cncf/config.conf <<'EOFCONF'
cncf.runtime.mode = command
cncf.datastore.sqlite.path = target/cncf.d/cncf-command.sqlite3
cncf.logging.backend = file
cncf.logging.file.path = target/cncf.d/trace.log
cncf.logging.level = trace
cncf.security.privilege = content_manager
EOFCONF

sbt --batch compile

ID="sys-sys-entity-simple_entity-1773792000000-1eeeeeeeeeeeeeeeee"
DRIVER="org.goldenport.cncf.cli.SimpleEntitySyncCommandMain"
CFG="--cncf.config.file=.cncf/config.conf"
MODE="--textus.runtime.command.execution-mode sync-direct-no-job"

sbt --batch "runMain ${DRIVER} ${CFG} ${MODE} domain.entity.savePerson --id ${ID} --name taro --privilege content_manager"
sbt --batch "runMain ${DRIVER} ${CFG} ${MODE} domain.entity.updatePerson --id ${ID} --nameAttributes.name jiro --privilege content_manager"

if [ ! -f target/cncf.d/cncf-command.sqlite3 ]; then
  echo "SQLite file not found: target/cncf.d/cncf-command.sqlite3" >&2
  exit 1
fi

sqlite3 target/cncf.d/cncf-command.sqlite3 ".tables" | grep -qi "simple_entity"
stored_row="$(sqlite3 target/cncf.d/cncf-command.sqlite3 "select id, name from simple_entity where id = '$ID';")"
printf "%s\n" "$stored_row"
printf "%s\n" "$stored_row" | grep -q "^${ID}|taro$"

echo "SIMPLEENTITY_ACTION_OK"
