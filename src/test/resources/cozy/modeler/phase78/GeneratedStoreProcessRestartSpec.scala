package cozy.modeler.phase78

import java.nio.file.{Files, Path}
import java.util.concurrent.TimeUnit
import domain.entity.Proposal
import domain.value.ProposalSummary
import io.circe.Json
import org.goldenport.Consequence
import org.goldenport.cncf.EntityIdFixtureBridge
import org.goldenport.cncf.context.ExecutionContext
import org.goldenport.cncf.datastore.DataStore
import org.goldenport.cncf.datastore.sql.SqlDataStore
import org.goldenport.cncf.entity.EntityStore
import org.goldenport.record.Record
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.simplemodeling.model.datatype.EntityId

/*
 * @since   Oct. 18, 2026
 * @version Oct. 18, 2026
 */
final class GeneratedStoreProcessRestartSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  import GeneratedStoreProcessRestartSpec.*

  "Cozy-generated durable persistence" should {
    "cross an operating-system process boundary" which {
      "reconstructs the declared Proposal after the writer terminates and a fresh reader starts" in {
        Given("an empty test-owned SQLite path and fixed logical Proposal literals authored independently of stored output")
        val root = Files.createDirectories(Path.of("target/phase78-process-restart"))
        val directory = Files.createTempDirectory(root, "restart-").toAbsolutePath
        val database = directory.resolve("application.db")
        val parentpid = ProcessHandle.current().pid()
        val entitykey = "processrestart"
        Files.exists(database) shouldBe false

        And("a writer JVM saves that generated Proposal, closes its provider and fully terminates")
        val writer = _run_child("writer", database, entitykey, parentpid, directory.resolve("writer.log"))
        withClue(writer.output) {
          writer.exitcode shouldBe 0
          writer.output should include(s"WRITER_SAVED pid=${writer.pid} parent=$parentpid")
        }

        When("an independent fresh reader JVM loads the same database and generated entity identity")
        val reader = _run_child("reader", database, entitykey, parentpid, directory.resolve("reader.log"))

        Then("both children exit successfully with distinct PIDs and the reader confirms all independently expected logical values")
        writer.pid should not be parentpid
        reader.pid should not be parentpid
        reader.pid should not be writer.pid
        withClue(reader.output) {
          reader.exitcode shouldBe 0
          reader.output should include(s"READER_READBACK_OK pid=${reader.pid} parent=$parentpid")
        }
        info(s"Writer PID ${writer.pid} terminated before reader PID ${reader.pid}; parent PID $parentpid")
        info(writer.output)
        info(reader.output)
      }
    }
  }
}

object GeneratedStoreProcessRestartSpec extends Matchers {
  private case class ChildResult(pid: Long, exitcode: Int, output: String)

  private def _run_child(mode: String, database: Path, entitykey: String, parentpid: Long, logfile: Path): ChildResult = {
    val javaexe = Path.of(System.getProperty("java.home"), "bin", "java").toString
    val classpath = System.getProperty("java.class.path")
    classpath.nonEmpty shouldBe true
    val process = new ProcessBuilder(
      javaexe, "-cp", classpath, "cozy.modeler.phase78.GeneratedStoreProcessMain",
      mode, database.toString, entitykey, parentpid.toString)
      .redirectErrorStream(true)
      .redirectOutput(logfile.toFile)
      .start()
    try {
      if (!process.waitFor(60, TimeUnit.SECONDS)) {
        _stop_child(process)
        fail(s"$mode JVM ${process.pid()} exceeded 60 seconds:\n${Files.readString(logfile)}")
      }
      ChildResult(process.pid(), process.exitValue(), Files.readString(logfile))
    } finally _stop_child(process)
  }

  private def _stop_child(process: Process): Unit = {
    if (process.isAlive) {
      process.destroy()
      if (!process.waitFor(5, TimeUnit.SECONDS)) {
        process.destroyForcibly()
        process.waitFor(5, TimeUnit.SECONDS) shouldBe true
      }
    }
  }
}

/** Finite child entry point; the reader never writes or reconstructs values from writer output. */
object GeneratedStoreProcessMain extends Matchers {
  def main(args: Array[String]): Unit = {
    args.length shouldBe 4
    val mode = args(0)
    val database = Path.of(args(1))
    val id = EntityIdFixtureBridge.fromParts("test", args(2), Proposal.collectionId, entropy = args(2))
    val expected = _expected(id)
    val parentpid = args(3).toLong
    val process = ProcessHandle.current()
    process.parent().isPresent shouldBe true
    process.parent().get().pid() shouldBe parentpid
    process.pid() should not be parentpid

    mode match {
      case "writer" =>
        Files.exists(database) shouldBe false
        val provider = _sql(database)
        given ExecutionContext = _context(provider)
        try _take(EntityStore.standard().create(_take(domain.entity.create.Proposal.createC(_logical(id)))))
        finally _take(provider.closeC())
        println(s"WRITER_SAVED pid=${process.pid()} parent=$parentpid")
      case "reader" =>
        Files.exists(database) shouldBe true
        val provider = _sql(database)
        given ExecutionContext = _context(provider)
        try {
          val raw = _take(provider.load(
            DataStore.CollectionId.EntityStore(Proposal.collectionId), DataStore.EntryId(id))).get
          val restored = _take(EntityStore.standard().load[Proposal](id)).get

          // Physical text is separate from the generated EntityPersistent logical codec route.
          raw.getString("originalProposal") shouldBe Some(expected.originalProposal)
          Vector("payload", "evidence", "payloads", "evidences", "summary", "summaries").foreach { field =>
            raw.getAny(field).get shouldBe a[String]
          }
          restored shouldBe expected
          restored.originalProposal shouldBe " \n{  \"original\": [1, null, {}], \"caption\": \"雪\" }\t  "
          restored.payload shouldBe expected.payload
          restored.evidence shouldBe expected.evidence
          restored.optionalPayload shouldBe expected.optionalPayload
          restored.optionalEvidence shouldBe expected.optionalEvidence
          restored.payloads shouldBe expected.payloads
          restored.evidences shouldBe expected.evidences
          restored.summary shouldBe ProposalSummary("雪", 2)
          restored.optionalSummary shouldBe Some(ProposalSummary("optional", 1))
          restored.summaries shouldBe Vector(ProposalSummary("repeated", 3), ProposalSummary("", 0))
          restored.name.value shouldBe "generated"
          restored.quantity shouldBe 7
          restored.active shouldBe true
          restored.amount shouldBe BigDecimal("12345678901234567890.123456789")
        } finally _take(provider.closeC())
        println(s"READER_READBACK_OK pid=${process.pid()} parent=$parentpid")
      case other => fail(s"Unknown fixture child mode: $other")
    }
  }

  private def _expected(id: EntityId): Proposal = _take(Proposal.createC(_logical(id)))

  private def _logical(id: EntityId): Record = {
    val payload = Json.obj(
      "empty" -> Json.obj(),
      "nested" -> Json.arr(Json.Null, Json.obj("snow" -> Json.fromString("雪")), Json.arr()),
      "precise" -> Json.fromBigDecimal(BigDecimal("12345678901234567890.123456789")))
    val evidence = Record.createFull(Vector(
      "empty" -> Record.empty,
      "null" -> null,
      "nested" -> Record.createFull(Vector("caption" -> "雪", "empty" -> Record.empty))))
    Record.createFull(Vector(
      "id" -> id.value,
      "originalProposal" -> " \n{  \"original\": [1, null, {}], \"caption\": \"雪\" }\t  ",
      "payload" -> payload,
      "evidence" -> evidence,
      "optionalPayload" -> Json.Null,
      "optionalEvidence" -> Record.empty,
      "payloads" -> Vector(Json.Null, Json.arr(), payload),
      "evidences" -> Vector(Record.empty, evidence),
      "name" -> "generated", "quantity" -> 7, "active" -> true,
      "amount" -> BigDecimal("12345678901234567890.123456789"),
      "summary" -> ProposalSummary("雪", 2),
      "optionalSummary" -> ProposalSummary("optional", 1),
      "summaries" -> Vector(ProposalSummary("repeated", 3), ProposalSummary("", 0))))
  }

  private def _sql(database: Path): SqlDataStore = SqlDataStore.jdbc(s"jdbc:sqlite:$database")

  private def _context(provider: DataStore): ExecutionContext = {
    val context = ExecutionContext.create()
    context.dataStoreSpace.useDataStore(provider)
    context
  }

  private def _take[A](result: Consequence[A]): A = result match {
    case Consequence.Success(value) => value
    case Consequence.Failure(conclusion) =>
      fail((conclusion.show +: conclusion.causes.map(_.display)).mkString("\n"))
  }
}
