#!/usr/bin/env sh
set -eu

cd out.d
mkdir -p src/main/scala/domain src/main/scala/domain/impl src/main/scala/domain/testsupport .cncf

cat > src/main/scala/domain/impl/ComponentFactory.scala <<'SCALA'
package domain.impl

import cats.*
import cats.implicits.*
import cats.syntax.all.*
import scala.collection.concurrent.TrieMap
import org.goldenport.Consequence
import org.goldenport.protocol.operation.OperationResponse
import org.simplemodeling.model.datatype.EntityId
import org.goldenport.cncf.directive.{Query, SearchResult}
import org.goldenport.cncf.action.ActionCall
import org.goldenport.cncf.unitofwork.ExecUowM
import org.goldenport.record.Record
import domain.DomainComponent

class ComponentFactory() extends DomainComponent.Factory {
  override val entity = ComponentFactory.EntityServiceFactoryEx()
}

object ComponentFactory {
  private val _memory = TrieMap.empty[EntityId, domain.Person]
  @volatile private var _lastSearchResultSize: Int = -1

  def memorySize: Int = _memory.size
  def lastSearchResultSize: Int = _lastSearchResultSize

  final case class EntityServiceFactoryEx() extends DomainComponent.EntityServiceFactory {
    import DomainComponent.EntityService.*

    override def createSavePersonActionCall(
      core: ActionCall.Core,
      action: SavePersonCommand
    ): SavePersonActionCall =
      SavePersonMemoryActionCall(core, action)

    override def createSearchPersonRecordActionCall(
      core: ActionCall.Core,
      action: SearchPersonRecordQuery
    ): SearchPersonRecordActionCall =
      SearchPersonRecordMemoryActionCall(core, action)
  }

  final case class SavePersonMemoryActionCall(
    core: ActionCall.Core,
    override val action: DomainComponent.EntityService.SavePersonCommand
  ) extends DomainComponent.EntityService.SavePersonActionCall {
    protected def build_Program: ExecUowM[OperationResponse] = {
      for {
        entity <- exec_pure(domain.Person.create(action.entity.toRecord()))
        _ <- entity_save(entity)
        _ <- exec_pure {
          _memory.put(entity.id, entity)
          Consequence.unit
        }
      } yield {
        OperationResponse.void
      }
    }
  }

  final case class SearchPersonRecordMemoryActionCall(
    core: ActionCall.Core,
    override val action: DomainComponent.EntityService.SearchPersonRecordQuery
  ) extends DomainComponent.EntityService.SearchPersonRecordActionCall {
    protected def build_Program: ExecUowM[OperationResponse] = {
      val queryRecord = action.q.query
      val query = Query(
        Record.data(queryRecord.asMap.toVector.collect {
          case (k, v) if k == "id" || k == "name" || k == "age" => k -> v
        }*)
      )
      val qid = queryRecord.asMap.get("id").map(_.toString)
      val qname = queryRecord.asMap.get("name").map(_.toString)
      for {
        result <- exec_pure {
          val values = _memory.values.toVector
          val filtered = values.filter { v =>
            qid.forall(_ == v.id.print) &&
            qname.forall(n => v.name.toString == n)
          }
          val sorted = Query.sortValues(filtered, query.sort)
          val sliced = Query.sliceValues(sorted, query.offset, query.limit)
          _lastSearchResultSize = sliced.size
          Consequence.success(
            SearchResult(
              query = query,
              data = sliced,
              totalCount = Some(filtered.size),
              offset = query.offset,
              limit = query.limit,
              fetchedCount = sliced.size
            )
          )
        }
      } yield {
        OperationResponse.create(result)
      }
    }
  }
}
SCALA

cp ../EmbeddedCncfTestSupport.scala.txt src/main/scala/domain/testsupport/EmbeddedCncfTestSupport.scala

cat > src/main/scala/domain/SearchMemoryProbe.scala <<'SCALA'
package domain

import java.nio.file.{Files, Paths}
import org.goldenport.cncf.component.{Component, ComponentCreate, ComponentOrigin}
import org.goldenport.cncf.context.SecurityContext
import org.goldenport.cncf.subsystem.Subsystem
import domain.impl.ComponentFactory
import domain.testsupport.EmbeddedCncfTestSupport

object SearchMemoryProbe {
  def main(args: Array[String]): Unit = {
    val id = "sys-sys-entity-person-1773772200000-7ddddddddddddddddddddd"
    val sqlitePath = Paths.get("target/cncf.d/cncf-command.sqlite3")

    EmbeddedCncfTestSupport.withHandle(
      extraComponents = _extraComponents,
      privilege = SecurityContext.Privilege.User
    ) { handle =>
      handle.executeOrThrow(Array("domain.entity.savePerson", "--id", id, "--name", "taro"))

      if (!Files.exists(sqlitePath))
        throw new IllegalStateException(s"sqlite file not found: $sqlitePath")
      if (ComponentFactory.memorySize <= 0)
        throw new IllegalStateException("memory cache was not updated by savePerson")

      Files.deleteIfExists(sqlitePath)

      handle.executeOrThrow(Array("domain.entity.searchPersonRecord", "--name", "taro"))

      if (ComponentFactory.lastSearchResultSize <= 0)
        throw new IllegalStateException("search did not hit in-memory records")
    }

    println("SEARCH_MEMORY_OK")
  }

  private def _extraComponents(subsystem: Subsystem): Seq[Component] = {
    val params = ComponentCreate(subsystem, ComponentOrigin.Main)
    ComponentFactory().create(params)
  }
}
SCALA

cat > .cncf/config.conf <<'EOFCONF'
cncf.runtime.mode = command
cncf.datastore.sqlite.path = target/cncf.d/cncf-command.sqlite3
cncf.logging.backend = file
cncf.logging.file.path = target/cncf.d/trace.log
cncf.logging.level = trace
EOFCONF

rm -f target/cncf.d/cncf-command.sqlite3

sbt --batch compile

set +e
probe_out=$(sbt --batch "runMain domain.SearchMemoryProbe" 2>&1)
probe_status=$?
set -e
printf "%s\n" "$probe_out"
[ "$probe_status" -eq 0 ]
printf "%s\n" "$probe_out" | grep -q "SEARCH_MEMORY_OK"
