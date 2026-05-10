#!/bin/sh
set -eu

script_dir=$(CDPATH= cd -- "$(dirname "$0")" && pwd)
sample_dir=/Users/asami/src/dev2026/cncf-samples/samples/06-cqrs
out_dir="$script_dir/out.d"
cml_file="$sample_dir/src/main/cozy/cqrs.cml"
server_log="$out_dir/server.log"
item_id="org-sample-entity-item-$(date +%s)000-scripted111"
demo_dst_dir="$out_dir/src/main/scala/org/sample/cqrs"
demo_src="$demo_dst_dir/CqrsSplitDemo.scala"

cleanup_existing_servers() {
  pids=$(ps -ax | awk '/org\.goldenport\.cncf\.CncfMain --discover=classes server/ && /04-cqrs|cqrs-split\/out\.d/ {print $1}')
  if [ -n "$pids" ]; then
    printf '%s\n' "$pids" | xargs kill >/dev/null 2>&1 || true
    sleep 1
  fi
}

rm -rf "$out_dir"
cleanup_existing_servers
"$script_dir/cozy-delegate.sh" modeler-scala "$cml_file" --save="$out_dir"
mkdir -p "$demo_dst_dir"
cat > "$demo_src" <<'SCALA'
package org.sample.cqrs

import io.circe.Json
import org.goldenport.Consequence
import org.goldenport.protocol.{Property, Request}
import org.goldenport.cncf.cli.{CncfRuntime, RunMode}
import org.goldenport.cncf.component.{ComponentCreate, ComponentFactory, ComponentOrigin}

object CqrsSplitDemo {
  def main(args: Array[String]): Unit = {
    val itemId = args.headOption.getOrElse(sys.error("missing item id"))
    val runtime = new CncfRuntime
    val subsystem = runtime.initializeForEmbedding(modeHint = Some(RunMode.Command)).TAKE
    val component = ComponentFactory().bootstrap(
      new CqrsComponent.Factory().createPrimary(ComponentCreate(subsystem, ComponentOrigin.Builtin))
    )
    val _ = subsystem.add(Vector(component))
    try {
      val created = _executeString(
        subsystem,
        Request.of(
          component = "Cqrs",
          service = "entity",
          operation = "createItemRecord",
          properties = List(
            Property("textus.runtime.command.execution-mode", "sync-direct-no-job", None),
            Property("cncf.security.privilege", "content_manager", None),
            Property("id", itemId, None),
            Property("name", "gamma", None),
            Property("title", "Gamma", None)
          )
        )
      )
      val loaded = _executeString(
        subsystem,
        Request.of(
          component = "Cqrs",
          service = "entity",
          operation = "loadItem",
          properties = List(
            Property("cncf.security.privilege", "content_manager", None),
            Property("id", itemId, None)
          )
        )
      )
      println(Json.obj("created" -> Json.fromString(created), "loaded" -> Json.fromString(loaded)).noSpaces)
    } finally {
      runtime.closeEmbedding()
    }
  }

  private def _executeString(
    subsystem: org.goldenport.cncf.subsystem.Subsystem,
    request: Request
  ): String =
    subsystem.execute(request) match {
      case Consequence.Success(response) => response.print
      case Consequence.Failure(c) => throw new IllegalStateException(c.show)
    }
}
SCALA

run_command() {
  (
    cd "$out_dir"
    sbt --batch "runMain org.goldenport.cncf.CncfMain --discover=classes command $*"
  )
}

run_client() {
  (
    cd "$out_dir"
    sbt --batch "runMain org.goldenport.cncf.CncfMain --discover=classes client $*"
  )
}

(
  cd "$out_dir"
  sbt --batch compile
)

command_help="$(run_command help cqrs.item.create-item 2>&1)"
printf '%s\n' "$command_help" | grep 'cqrs.item.create-item'
printf '%s\n' "$command_help" | grep 'CreateItemResult'

entity_help="$(run_command help cqrs.entity.create-item-record 2>&1)"
printf '%s\n' "$entity_help" | grep 'cqrs.entity.create-item-record'
printf '%s\n' "$entity_help" | grep 'returns:'

meta_out="$(run_command cqrs.meta.describe --format yaml 2>&1)"
printf '%s\n' "$meta_out" | grep 'runtime_name: item'
printf '%s\n' "$meta_out" | grep 'name: createItem'
printf '%s\n' "$meta_out" | grep 'kind: COMMAND'
printf '%s\n' "$meta_out" | grep 'name: getItem'
printf '%s\n' "$meta_out" | grep 'kind: QUERY'

result="$(cd "$out_dir" && sbt --batch "runMain org.sample.cqrs.CqrsSplitDemo $item_id" 2>&1 | grep '^{' | tail -n 1)"
printf '%s\n' "$result" | grep "\"created\":\"id: $item_id"
printf '%s\n' "$result" | grep "\"loaded\":\"id: $item_id"
printf '%s\n' "$result" | grep 'name: gamma'
printf '%s\n' "$result" | grep 'title: Gamma'
echo CQRS_SPLIT_OK
