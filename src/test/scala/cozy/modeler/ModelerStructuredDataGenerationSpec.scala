package cozy.modeler

import java.nio.file.{Files, Path, Paths}
import org.scalatest.GivenWhenThen
import org.scalatest.wordspec.AnyWordSpec
import org.goldenport.kaleidox.{Config => KaleidoxConfig, Model => KaleidoxModel}
import org.simplemodeling.model.{MBuiltinStructuredDataType, MDataType, MEntity, MObjectAttributeType, MValue}

/*
 * @since   Oct.  5, 2026
 * @version Oct. 19, 2026
 * @author  ASAMI, Tomoharu
 */
final class ModelerStructuredDataGenerationSpec extends AnyWordSpec with GivenWhenThen with ModelerSpecSupport {
  "E7 CML structured and declared store generation" must afterWord(
    "in spec:datastore-logical-type-generation, example:E7, rules:R1,R2,R3,R6, phase:78"
  ) {
    "preserve built-in logical identities" which {
      "project explicit declarations and their multiplicities into semantic attributes" in {
        Given("docs/spec/datastore-logical-type-generation.md R1,R2,R3,R6 E7: CML String, JSON, Record, native scalars and declared ProposalSummary")
        val model = KaleidoxModel.parseWitoutLocation(KaleidoxConfig.log.debug, _fixture)

        When("the existing model builder projects its declared attribute types")
        val simplemodel = Modeler.ModelBuilder(model).build()
        val entity = simplemodel.elements.collectFirst {
          case value: MEntity if value.name == "Proposal" => {
            value
          }
        }.getOrElse(fail("Proposal entity missing"))
        val attributes = entity.attributes.map(x => x.name -> x).toMap

        Then("JSON and Record remain distinct semantic objects, String retains its scalar meaning, and all summary fields resolve to domain.value.ProposalSummary")
        attributes("originalProposal").attributeType shouldBe MDataType.string
        attributes("payload").attributeType shouldBe MBuiltinStructuredDataType.Json
        attributes("evidence").attributeType shouldBe MBuiltinStructuredDataType.SemiStructuredData
        attributes("legacyEvidence").attributeType shouldBe MBuiltinStructuredDataType.SemiStructuredData
        attributes("optionalPayload").attributeType shouldBe MBuiltinStructuredDataType.Json
        attributes("optionalEvidence").attributeType shouldBe MBuiltinStructuredDataType.SemiStructuredData
        attributes("payloads").attributeType shouldBe MBuiltinStructuredDataType.Json
        attributes("evidences").attributeType shouldBe MBuiltinStructuredDataType.SemiStructuredData
        attributes("optionalPayload").multiplicity shouldBe org.simplemodeling.model.MZeroOne
        attributes("optionalEvidence").multiplicity shouldBe org.simplemodeling.model.MZeroOne
        attributes("payloads").multiplicity shouldBe org.simplemodeling.model.MZeroMore
        attributes("evidences").multiplicity shouldBe org.simplemodeling.model.MZeroMore
        attributes("name").attributeType shouldBe MObjectAttributeType(org.simplemodeling.model.MObjectRef.create("org.goldenport.datatype.Name"))
        attributes("quantity").attributeType shouldBe MDataType(org.goldenport.record.v2.XInt)
        attributes("active").attributeType shouldBe MDataType(org.goldenport.record.v2.XBoolean)
        attributes("amount").attributeType shouldBe MDataType(org.goldenport.record.v2.XDecimal)
        Vector("summary", "optionalSummary", "summaries").foreach { field =>
          attributes(field).attributeType shouldBe MObjectAttributeType(
            org.simplemodeling.model.MObjectRef.create("domain.value.ProposalSummary"))
        }
        attributes("summary").multiplicity shouldBe org.simplemodeling.model.MOne
        attributes("optionalSummary").multiplicity shouldBe org.simplemodeling.model.MZeroOne
        attributes("summaries").multiplicity shouldBe org.simplemodeling.model.MZeroMore
        val summary = simplemodel.elements.collectFirst {
          case value: MValue if value.name == "ProposalSummary" => { value }
        }.getOrElse(fail("ProposalSummary value missing"))
        summary.attributes.map(x => x.name -> x.attributeType).toMap shouldBe Map(
          "caption" -> MDataType.string, "rank" -> MDataType(org.goldenport.record.v2.XInt))
      }
    }

    "generate actual Scala products" which {
      "emit canonical application and schema identities deterministically through the existing CLI support" in {
        Given("docs/spec/datastore-logical-type-generation.md R1,R2,R3,R6 E7: target-owned CML with declared ProposalSummary and explicit runtime descriptor/version options")
        val base = Paths.get(sys.props("user.dir")).toAbsolutePath.normalize()
        val work = base.resolve("target/test-generated/structured-data-generation")
        val input = work.resolve("proposal.cml")
        val firstout = work.resolve("first")
        val secondout = work.resolve("second")
        delete_recursively(work)
        write_file(input, _fixture)

        When("Cozy generates the same input twice through run_modeler_scala")
        val firstlog = run_modeler_scala(input, firstout)
        val secondlog = run_modeler_scala(input, secondout)
        val first = _source_products(firstout, firstlog)
        val second = _source_products(secondout, secondlog)
        val entity = first("domain/entity/Proposal.scala")
        val source = first.values.mkString("\n")

        Then("the seven selected actual entity family, component and ProposalSummary products are identical")
        first shouldBe second
        first.size shouldBe 7
        entity should include("originalProposal: String")
        entity should include("payload: Json")
        entity should include("evidence: Record")
        entity should include("legacyEvidence: Record")
        entity should include("optionalPayload: Option[Json]")
        entity should include("optionalEvidence: Option[Record]")
        entity should include("payloads: Vector[Json]")
        entity should include("evidences: Vector[Record]")
        entity should include("datatype = org.goldenport.schema.XString")
        entity should include("datatype = org.goldenport.schema.XJson")
        entity should include("datatype = org.goldenport.schema.XSemiStructuredData")
        entity should include("org.goldenport.schema.Multiplicity.ZeroOne")
        entity should include("org.goldenport.schema.Multiplicity.ZeroMore")
        source should include("io.circe.Json")
        source should include("org.goldenport.record.Record")
        source should include("summon[org.goldenport.convert.ValueReader[io.circe.Json]].readC")
        source should include("_record_get_vector_as_c[io.circe.Json]")
        source should include("_record_get_as_c[scala.Vector[org.goldenport.record.Record]](record, INPUT_KEYS_EVIDENCES)")
        source should not include "DataType.Named(\"json\")"
        source should not include "DataType.Named(\"record\")"
        source should not include "Json.createC"
        source should not include "Json.fromRecord"

        And("Persistent, Create and Update independently emit complete declared mappings and typed logical hooks")
        Vector("domain/entity/Proposal.scala", "domain/entity/create/Proposal.scala", "domain/entity/update/Proposal.scala").foreach { product =>
          val generated = first(product)
          generated should include("override def storeRowMapping: Option[EntityStoreRowMapping]")
          generated should include("Consequence.success(e._logical_store_record)")
          _store_field(generated, "originalProposal") should include("EntityStoreFieldBinding.string")
          _store_field(generated, "payload") should include("EntityStoreFieldBinding.json")
          _store_field(generated, "evidence") should include("EntityStoreFieldBinding.semiStructuredData")
          _store_field(generated, "optionalPayload") should include("Multiplicity.ZeroOne")
          _store_field(generated, "optionalEvidence") should include("Multiplicity.ZeroOne")
          _store_field(generated, "payloads") should include("array(EntityStoreFieldBinding.json)")
          _store_field(generated, "evidences") should include("array(EntityStoreFieldBinding.semiStructuredData)")
          _store_field(generated, "name") should include("nativeScalar[org.goldenport.datatype.Name]")
          _store_field(generated, "quantity") should include("nativeScalar[Int](org.goldenport.schema.XInt, EntityStoreScalarRepresentation.Number")
          _store_field(generated, "active") should include("nativeScalar[Boolean](org.goldenport.schema.XBoolean, EntityStoreScalarRepresentation.Boolean")
          _store_field(generated, "amount") should include("nativeScalar[BigDecimal](org.goldenport.schema.XDecimal, EntityStoreScalarRepresentation.Number")
          _store_field(generated, "summary") should include("application[domain.value.ProposalSummary]")
          _store_field(generated, "optionalSummary") should include("Multiplicity.ZeroOne")
          _store_field(generated, "summaries") should include("array(EntityStoreFieldBinding.application[domain.value.ProposalSummary]")
          Vector("ClassTag", "io.circe.Encoder", "io.circe.Decoder").foreach { capture =>
            _store_field(generated, "summary") should include(capture + "[domain.value.ProposalSummary]")
          }
          val logical = _logical_store_record(generated)
          Vector("originalProposal", "payload", "evidence", "legacyEvidence", "optionalPayload", "optionalEvidence",
            "payloads", "evidences", "name", "quantity", "active", "amount", "summary", "optionalSummary", "summaries").foreach { field =>
            logical should include(s""""$field", org.goldenport.record.Field.Value.Single($field)""")
          }
          Vector("Record.dataAuto", "_to_external_value", "_to_data_store_value", "toChangesRecord").foreach { conversion =>
            logical should not include conversion
          }
        }
        entity should include("decodeAdmittedLogicalStoreRecord(r: Record): Consequence[Proposal] = { createC(r) }")
        entity should include("_record_get_vector_as_c[domain.value.ProposalSummary]")
        first("domain/value/ProposalSummary.scala") should include("given org.goldenport.convert.ValueReader[ProposalSummary]")
        first("domain/value/ProposalSummary.scala") should include(
          "org.goldenport.convert.ValueReader.recordReader[ProposalSummary]((record: Record) => createC(record)).readC(value)")
        first("domain/entity/query/Proposal.scala") should not include "_store_row_mapping"

        And("both update request schemas select each declared structured field independently")
        val component = first("domain/DomainComponent.scala")
        Vector("UpdateProposal", "UpdateProposalRecord").foreach { operation =>
          val block = _operation_block(component, operation)
          Vector("payload", "optionalPayload", "payloads").foreach { field =>
            _request_field(block, field) should include("datatype = org.goldenport.schema.XJson,")
          }
          Vector("evidence", "legacyEvidence", "optionalEvidence", "evidences").foreach { field =>
            _request_field(block, field) should include("datatype = org.goldenport.schema.XSemiStructuredData,")
          }
          _request_field(block, "originalProposal") should include("datatype = org.goldenport.schema.XString,")
          Vector("optionalPayload", "optionalEvidence").foreach { field =>
            _request_field(block, field) should include("multiplicity = org.goldenport.schema.Multiplicity.ZeroOne")
          }
          Vector("payloads", "evidences").foreach { field =>
            _request_field(block, field) should include("multiplicity = org.goldenport.schema.Multiplicity.ZeroMore")
          }
        }
      }
    }
  }

  private def _store_field(source: String, field: String): String = {
    source.linesIterator.find(_.contains(s"""EntityStoreField("$field","""))
      .getOrElse(fail(s"declared store field missing: $field"))
  }

  private def _logical_store_record(source: String): String = {
    val start = source.indexOf("private def _logical_store_record: Record = {")
    start should be >= 0
    val end = source.indexOf("\n  }", start)
    end should be > start
    source.substring(start, end)
  }

  private def _operation_block(source: String, operation: String): String = {
    val marker = s"object ${operation}Operation extends OperationDefinition"
    val start = source.indexOf(marker)
    withClue(s"generated operation marker missing: $marker") {
      start should be >= 0
    }
    val next = "object [A-Za-z0-9]+Operation extends OperationDefinition".r
      .findAllMatchIn(source).map(_.start).find(_ > start).getOrElse(source.length)
    source.substring(start, next)
  }

  private def _request_field(block: String, field: String): String = {
    val request = block.linesIterator.find(_.contains("request = RequestDefinition(")).getOrElse(fail("request schema missing"))
    request.split("ParameterDefinition\\(content = ").find(
      _.startsWith(s"""org.goldenport.value.BaseContent.simple("$field")""")
    ).getOrElse(fail(s"request field missing: $field"))
  }

  private def _source_products(out: Path, log: String): Map[String, String] = {
    val sourcebase = out.resolve("target/scala-3.3.8/src_managed/main/scala")
    val selected = Vector(
      "domain/DomainComponent.scala",
      "domain/entity/Proposal.scala",
      "domain/entity/read/Proposal.scala",
      "domain/entity/create/Proposal.scala",
      "domain/entity/update/Proposal.scala",
      "domain/entity/query/Proposal.scala",
      "domain/value/ProposalSummary.scala"
    )
    selected.map { relative =>
      val path = sourcebase.resolve(relative)
      withClue(s"Generated source missing: $path\n$log") {
        Files.exists(path) shouldBe true
      }
      relative -> Files.readString(path)
    }.toMap
  }

  private val _fixture = """# ENTITY
    |
    |## Proposal
    |
    |### ATTRIBUTE
    |
    || name             | type                | multiplicity |
    ||------------------+---------------------+--------------|
    || id               | entityid            | 1            |
    || originalProposal | String              | 1            |
    || payload          | XJson               | 1            |
    || evidence         | XSemiStructuredData | 1            |
    || legacyEvidence   | record              | 1            |
    || optionalPayload  | Json                | ?            |
    || optionalEvidence | Record              | ?            |
    || payloads         | XJson               | *            |
    || evidences        | XSemiStructuredData | *            |
    || name             | name                | 1            |
    || quantity         | int                 | 1            |
    || active           | boolean             | 1            |
    || amount           | decimal             | 1            |
    || summary          | ProposalSummary     | 1            |
    || optionalSummary  | ProposalSummary     | ?            |
    || summaries        | ProposalSummary     | *            |
    |
    |# VALUE
    |
    |## ProposalSummary
    |
    |### ATTRIBUTE
    |
    || name    | type   | multiplicity |
    ||---------+--------+--------------|
    || caption | string | 1            |
    || rank    | int    | 1            |
    |""".stripMargin
}
