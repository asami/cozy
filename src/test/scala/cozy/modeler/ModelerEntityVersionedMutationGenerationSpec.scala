package cozy.modeler

import java.nio.file.Files
import java.nio.file.Paths
import org.scalatest.GivenWhenThen
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Jul. 24, 2026
 * @version Jul. 26, 2026
 * @author  ASAMI, Tomoharu
 */
final class ModelerEntityVersionedMutationGenerationSpec
    extends AnyWordSpec
    with GivenWhenThen
    with ModelerSpecSupport {
  private val _operation_marker =
    "(?m)^    object [A-Za-z0-9]+Operation extends OperationDefinition".r

  "CML Entity CRUD generation" should {
    "keep ordinary CRUD independent of Entity revision representation" in {
      Given("one SimpleEntity and one ordinary Entity without a revision binding")
      val base = Paths.get(sys.props("user.dir")).toAbsolutePath.normalize()
      val input = base.resolve("src/test/resources/modeler/simpleentity-parent.dox")
      val out = base.resolve("target/test-generated/modeler-entity-versioned-mutation")
      delete_recursively(out)

      When("Cozy generates their common managed CRUD adapter")
      val output = run_modeler_scala(input, out)
      val generated = out.resolve(
        "target/scala-3.3.8/src_managed/main/scala/domain/DomainComponent.scala"
      )
      withClue(output) {
        Files.exists(generated) shouldBe true
      }
      val content = Files.readString(generated)

      Then("load operations use the revision-transparent typed Entity boundary")
      List("Person", "Document").foreach { entity =>
        List(s"Load$entity", s"Load${entity}Record").foreach { operation =>
          val invocation =
            s"entity_load[domain.entity.$entity](action.id)"
          val block = _operation_block(content, operation, invocation)
          block should include(invocation)
          block should include("OperationResponse(entity.toRecord())")
          block should not include "cncfRevision"
          block should include("ResponseDefinition(result = List(org.goldenport.schema.DataType.Named(\"Record\")))")
        }
      }

      And("save operations work for both revision-managed and unbound Entities")
      List(
        "SavePerson" -> "entity_save_managed(entity)",
        "SavePersonRecord" -> "entity_save_managed(entity)",
        "SaveDocument" -> "entity_save_managed(entity)",
        "SaveDocumentRecord" -> "entity_save_managed(entity)"
      ).foreach { case (operation, invocation) =>
        val block = _operation_block(content, operation, invocation)
        block should include(invocation)
        block should include("OperationResponse(saved.toRecord())")
        block should not include "cncfRevision"
        block should not include "EntityConcurrencyMetadata"
        block should include("ResponseDefinition(result = List(org.goldenport.schema.DataType.Named(\"Record\")))")
      }

      And("patch-update operations leave managed revision lifecycle to Entity and UnitOfWork")
      List(
        "UpdatePerson" -> "entity_update(id, action.entity)",
        "UpdatePersonRecord" -> "entity_update(id, action.entity)",
        "UpdateDocument" -> "entity_update(id, action.entity)",
        "UpdateDocumentRecord" -> "entity_update(id, action.entity)"
      ).foreach { case (operation, invocation) =>
        val block = _operation_block(content, operation, invocation)
        block should include(invocation)
        block should not include "cncfRevision"
        block should not include "EntityConcurrencyMetadata"
        block should not include "snapshot.token"
        block should include("record <- entity_update(id, action.entity)")
        block should include("OperationResponse(record)")
        block should not include "snapshot.record"
        block should include("ResponseDefinition(result = List(org.goldenport.schema.DataType.Named(\"Record\")))")
      }

      And("no generated ordinary mutation retains retired revision transport")
      content should not include "EntityConcurrencyMetadata"
      content should not include "snapshot.token"
      content should not include "cncfRevision"
    }
  }

  private def _operation_block(
    content: String,
    operation: String,
    required: String
  ): String = {
    val marker = s"object ${operation}Operation extends OperationDefinition"
    val blocks = _operation_marker.findAllMatchIn(content).toVector.map { current =>
      val start = current.start
      val next = _operation_marker
        .findAllMatchIn(content)
        .map(_.start)
        .find(_ > start)
        .getOrElse(content.length)
      content.substring(start, next)
    }
    val block = blocks.find(x => x.startsWith(s"    $marker") && x.contains(required))
    withClue(s"generated operation marker not found: $marker") {
      block should not be empty
    }
    block.get
  }
}
