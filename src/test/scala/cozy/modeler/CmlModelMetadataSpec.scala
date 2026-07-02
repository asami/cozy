package cozy.modeler

import java.nio.charset.StandardCharsets
import java.nio.file.Files
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Jun. 23, 2026
 * @version Jul.  1, 2026
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
