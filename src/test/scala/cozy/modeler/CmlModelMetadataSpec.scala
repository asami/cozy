package cozy.modeler

import java.nio.charset.StandardCharsets
import java.nio.file.Files
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import play.api.libs.json.{JsValue, Json}

/*
 * @since   Jun. 23, 2026
 * @version Jul. 15, 2026
 * @author  ASAMI, Tomoharu
 */
class CmlModelMetadataSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  "CML model metadata" should {
    "write source provenance without leaking local absolute paths" in {
      Given("a CML source file under a CAR project")
      val dir = Files.createTempDirectory("cozy-cml-model-metadata")
      val source = dir.resolve("src/main/cozy/sample.cml")
      Files.createDirectories(source.getParent)
      Files.writeString(
        source,
        """# ENTITY
          |
          |## KnowledgeItem
          |
          |### BRIEF
          |
          |Knowledge item brief.
          |
          |### DESCRIPTION
          |
          |Knowledge item description.
          |""".stripMargin,
        StandardCharsets.UTF_8
      )
      val json = dir.resolve("target/model-metadata.json")
      val yaml = dir.resolve("target/model-metadata.yaml")

      When("Cozy writes machine-readable model metadata for a public catalog")
      CmlModelMetadata.write(source, json, yaml, "src/main/cozy/sample.cml", "concept")

      Then("the public source path is project-relative and the local workspace path is not leaked")
      val metadata = Files.readString(json)
      metadata should include ("cozy.cml.model-metadata.v1")
      metadata should include ("\"path\" : \"src/main/cozy/sample.cml\"")
      metadata should not include (dir.toAbsolutePath.normalize().toString)

      And("CML elements expose canonical public schema keys for glossary integration")
      metadata should include ("\"kind\" : \"entity\"")
      metadata should include ("\"name\" : \"KnowledgeItem\"")
      metadata should include ("\"termId\" : \"concept:knowledge-item\"")
      metadata should include ("\"glossaryPath\" : \"glossary/concept/knowledge-item.html\"")
      metadata should include ("\"cozyVersion\"")
      metadata should include ("\"rdfCandidates\"")
      Files.readString(yaml) should include ("termId: \"concept:knowledge-item\"")
      Files.readString(yaml) should include ("fields: []")
    }

    "preserve entity and operation type field contracts" in {
      Given("a CML source with entity, command, query, and value attributes")
      val dir = Files.createTempDirectory("cozy-cml-model-metadata-fields")
      val source = dir.resolve("src/main/cozy/sample.cml")
      Files.createDirectories(source.getParent)
      Files.writeString(
        source,
        """# ENTITY
          |
          |## Notice
          |
          |### ATTRIBUTE
          |
          || name | type | multiplicity |
          ||------+------|--------------|
          || id | entityid | 1 |
          || title | string | ? |
          |
          |# COMMAND
          |
          |## CreateNotice
          |
          |### ATTRIBUTE
          |
          || name | type | multiplicity |
          ||------+------|--------------|
          || title | string | 1 |
          |
          |# QUERY
          |
          |## GetNotice
          |
          |### ATTRIBUTE
          |
          || name | type | multiplicity |
          ||------+------|--------------|
          || id | string | 1 |
          |
          |# VALUE
          |
          |## NoticeResult
          |
          |### ATTRIBUTE
          |
          || name | type | multiplicity |
          ||------+------|--------------|
          || notice | Notice | ? |
          |""".stripMargin,
        StandardCharsets.UTF_8
      )
      val json = dir.resolve("target/model-metadata.json")
      val yaml = dir.resolve("target/model-metadata.yaml")

      When("Cozy writes the canonical model metadata")
      CmlModelMetadata.write(source, json, yaml, "src/main/cozy/sample.cml", "concept")

      Then("every model kind and its field type, multiplicity, and required state remain explicit")
      val elements = (Json.parse(Files.readString(json)) \ "modelElements").as[Seq[JsValue]]
      elements.map(x => (x \ "kind").as[String]) should contain allOf ("entity", "command", "query", "value")
      val entity = elements.find(x => (x \ "name").as[String] == "Notice").get
      val entityfields = (entity \ "fields").as[Seq[JsValue]]
      entityfields.map(x => (x \ "name").as[String]) shouldBe Seq("id", "title")
      (entityfields.head \ "type").as[String] shouldBe "entityid"
      (entityfields.head \ "required").as[Boolean] shouldBe true
      (entityfields(1) \ "multiplicity").as[String] shouldBe "?"
      (entityfields(1) \ "required").as[Boolean] shouldBe false
      Files.readString(yaml) should include ("type: \"entityid\"")
    }

    "preserve nominal scalar identity underlying type and constraints from the CML AST" in {
      Given("a constrained plain DATATYPE declaration")
      val dir = Files.createTempDirectory("cozy-cml-model-metadata-nominal-scalar")
      val source = dir.resolve("src/main/cozy/sample.cml")
      Files.createDirectories(source.getParent)
      Files.writeString(
        source,
        """# DATATYPE
          |
          |## LoginName
          |
          |### ATTRIBUTE
          |
          || name  | type   | multiplicity | min-length | max-length | pattern  |
          ||-------+--------+--------------+------------+------------+----------|
          || value | string | 1            | 5          | 32         | ^user.+$ |
          |""".stripMargin,
        StandardCharsets.UTF_8
      )
      val json = dir.resolve("target/model-metadata.json")
      val yaml = dir.resolve("target/model-metadata.yaml")

      When("Cozy writes public model metadata")
      CmlModelMetadata.write(source, json, yaml, "src/main/cozy/sample.cml", "concept")

      Then("the AST-backed datatype contract identifies its nominal scalar representation")
      val element = (Json.parse(Files.readString(json)) \ "modelElements").as[Seq[JsValue]].head
      (element \ "kind").as[String] shouldBe "datatype"
      (element \ "name").as[String] shouldBe "LoginName"
      (element \ "representation").as[String] shouldBe "nominal-scalar"
      (element \ "underlyingType").as[String] shouldBe "string"
      (element \ "constraints").as[Seq[String]] should contain theSameElementsAs Seq(
        "min-length=5",
        "max-length=32",
        "pattern=^user.+$"
      )

      And("the scalar value field keeps the same underlying and constraint contract")
      val field = (element \ "fields").as[Seq[JsValue]].head
      (field \ "name").as[String] shouldBe "value"
      (field \ "type").as[String] shouldBe "string"
      (field \ "constraints").as[Seq[String]] should contain theSameElementsAs Seq(
        "min-length=5",
        "max-length=32",
        "pattern=^user.+$"
      )
      Files.readString(yaml) should include ("representation: \"nominal-scalar\"")
      Files.readString(yaml) should include ("underlyingType: \"string\"")
    }

    "extract operation summary and description from operation child sections" in {
      Given("a CML source file with operation SUMMARY and DESCRIPTION sections")
      val dir = Files.createTempDirectory("cozy-cml-model-metadata-operation")
      val source = dir.resolve("src/main/cozy/sample.cml")
      Files.createDirectories(source.getParent)
      Files.writeString(
        source,
        """# COMPONENT
          |
          |## Sample
          |
          |### SUMMARY
          |
          |Sample component.
          |
          |# SERVICE
          |
          |## Knowledge
          |
          |### SUMMARY
          |
          |Knowledge service.
          |
          |### OPERATION
          |
          |#### ingestKnowledge
          |
          |##### SUMMARY
          |
          |Ingest a knowledge item.
          |
          |##### DESCRIPTION
          |
          |Ingests authored or imported knowledge content.
          |
          |##### TYPE
          |
          |COMMAND
          |
          |##### INPUT
          |
          |###### TYPE
          |
          |IngestKnowledge
          |
          |##### OUTPUT
          |
          |###### TYPE
          |
          |IngestKnowledgeResult
          |""".stripMargin,
        StandardCharsets.UTF_8
      )
      val json = dir.resolve("target/model-metadata.json")
      val yaml = dir.resolve("target/model-metadata.yaml")

      When("Cozy writes model metadata")
      CmlModelMetadata.write(source, json, yaml, "src/main/cozy/sample.cml", "concept")

      Then("the operation descriptive fields are present in the surface metadata")
      val metadata = Files.readString(json)
      metadata should include ("\"name\" : \"ingestKnowledge\"")
      metadata should include ("\"summary\" : \"Ingest a knowledge item.\"")
      metadata should include ("\"description\" : \"Ingests authored or imported knowledge content.\"")
      metadata should include ("\"operationType\" : \"COMMAND\"")
      metadata should include ("\"inputType\" : \"IngestKnowledge\"")
      metadata should include ("\"outputType\" : \"IngestKnowledgeResult\"")
      Files.readString(yaml) should include ("summary: \"Ingest a knowledge item.\"")
    }
  }
}
