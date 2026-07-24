package cozy.modeler

import java.nio.file.Files
import java.nio.file.Paths
import org.scalatest.GivenWhenThen
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Jul. 24, 2026
 * @version Jul. 25, 2026
 * @author  ASAMI, Tomoharu
 */
final class ModelerEntityVersionedMutationGenerationSpec
    extends AnyWordSpec
    with GivenWhenThen
    with ModelerSpecSupport {
  private val _operation_marker =
    "(?m)^    object [A-Za-z0-9]+Operation extends OperationDefinition".r

  "CML Entity CRUD generation" should {
    "carry the authoritative CNCF revision through every generated mutation" in {
      Given("a SimpleEntity model whose generated CRUD surface can mutate one Entity")
      val base = Paths.get(sys.props("user.dir")).toAbsolutePath.normalize()
      val input = base.resolve("src/test/resources/modeler/simpleentity-parent.dox")
      val out = base.resolve("target/test-generated/modeler-entity-versioned-mutation")
      delete_recursively(out)

      When("Cozy generates the component adapter against the Phase 49 CNCF contract")
      val output = run_modeler_scala(input, out)
      val generated = out.resolve(
        "target/scala-3.3.8/src_managed/main/scala/domain/DomainComponent.scala"
      )
      withClue(output) {
        Files.exists(generated) shouldBe true
      }
      val content = Files.readString(generated)

      Then("load operations expose the authoritative Entity and its reserved revision")
      List("LoadPerson", "LoadPersonRecord").foreach { operation =>
        val block = _operation_block(content, operation, "entity_load_snapshot")
        block should include("entity_load_snapshot[domain.entity.Person](action.id)")
        block should include("upsertSingle(\"cncfRevision\", snapshot.token.print)")
        block should include("ResponseDefinition(result = List(org.goldenport.schema.DataType.Named(\"Record\")))")
      }

      And("save and patch-update operations require and advance the same revision")
      List(
        "SavePerson" -> "entity_save(entity, expectation)",
        "SavePersonRecord" -> "entity_save(entity, expectation)",
        "UpdatePerson" -> "entity_update(id, action.entity, expectation)",
        "UpdatePersonRecord" -> "entity_update(id, action.entity, expectation)"
      ).foreach { case (operation, invocation) =>
        val block = _operation_block(content, operation, invocation)
        block should include("BaseContent.simple(\"cncfRevision\")")
        block should include("exec_from(EntityMutationExpectation.parse(action.cncfRevision))")
        block should include(invocation)
        block should include("upsertSingle(\"cncfRevision\", snapshot.token.print)")
        block should include("ResponseDefinition(result = List(org.goldenport.schema.DataType.Named(\"Record\")))")
      }

      And("no generated ordinary mutation retains the former unversioned calls")
      content should not include("entity_save(entity)\n")
      content should not include("entity_update(id, action.entity)\n")
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
