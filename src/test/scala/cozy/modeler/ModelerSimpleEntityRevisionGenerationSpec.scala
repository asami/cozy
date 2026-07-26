package cozy.modeler

import java.nio.file.{Files, Path, Paths}
import cozy.scaffold.CozyScaffold
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Jul. 25, 2026
 * @version Jul. 27, 2026
 * @author  ASAMI, Tomoharu
 */
final class ModelerSimpleEntityRevisionGenerationSpec
    extends AnyWordSpec
    with Matchers
    with GivenWhenThen
    with ModelerSpecSupport {
  "CML SimpleEntity generation" should {
    "carry embedded revision only on generated Entity outputs" in {
      Given("a CML Entity extending the standard SimpleEntity model")
      val base = Paths.get(sys.props("user.dir")).toAbsolutePath.normalize()
      val input = base.resolve("src/test/resources/modeler/simpleentity-parent.dox")
      val output = base.resolve("target/test-generated/modeler-simpleentity-revision")
      delete_recursively(output)

      When("Cozy generates the complete Scala Entity family")
      val commandoutput = run_modeler_scala(input, output)
      val sourceroot = output.resolve(
        "target/scala-3.3.8/src_managed/main/scala/domain/entity"
      )

      Then("all Entity output variants implement one embedded revision")
      _output_paths.foreach { relative =>
        val source = _source(sourceroot.resolve(relative), commandoutput)
        source should include(
          "override val id: EntityId, override val revision: EntityRevision"
        )
        _occurrences(source, "override val revision: EntityRevision") shouldBe 1
        source should include("\"revision\" -> _to_external_value(revision)")
      }

      And("create, update, and query variants do not accept revision")
      _input_paths.foreach { relative =>
        val source = _source(sourceroot.resolve(relative), commandoutput)
        source should not include "revision: EntityRevision"
        source should not include "Update[EntityRevision]"
        source should not include "Condition[EntityRevision]"
        source should not include "\"revision\" ->"
      }

      And("generated operation inputs never accept the managed revision")
      val component = _source(
        output.resolve(
          "target/scala-3.3.8/src_managed/main/scala/domain/DomainComponent.scala"
        ),
        commandoutput
      )
      component should not include "CmlOperationField(name = \"revision\""
      component should not include "BaseContent.simple(\"revision\")"
      component should not include "entity: _root_.domain.entity.aggregate.Person"
      component should include("entity: _root_.domain.entity.create.Person")
      component should include("entity: _root_.domain.entity.update.Person")
      component should not include "BaseContent.simple(\"cncfRevision\")"
      component should not include "EntityConcurrencyMetadata"
      component should not include "snapshot.token"
      component should include(
        "revisionModelKind = Some(org.goldenport.cncf.entity.EntityRevisionModelKind.SimpleEntity)"
      )
      component should include(
        "revisionRepresentation = Some(org.goldenport.cncf.entity.EntityRevisionRepresentation.Embedded)"
      )

      And("an ordinary Entity is classified without inventing embedded revision")
      component should include(
        "revisionModelKind = Some(org.goldenport.cncf.entity.EntityRevisionModelKind.NonSimpleEntity)"
      )
      _occurrences(
        component,
        "revisionRepresentation = Some(org.goldenport.cncf.entity.EntityRevisionRepresentation.Embedded)"
      ) shouldBe 1
      component should include("revisionRepresentation = None")
    }

    "select the model version that defines embedded SimpleEntity revision" in {
      Given("the default generated CAR dependency versions")

      When("Cozy resolves its SimpleModeling model dependency")
      val version = CozyScaffold.CarDependencyVersions.default.simpleModelingModelVersion

      Then("new generated projects consume the revision-aware model")
      version shouldBe "0.2.0"
    }
  }

  private def _source(path: Path, commandoutput: String): String = {
    withClue(commandoutput) {
      Files.exists(path) shouldBe true
    }
    Files.readString(path)
  }

  private def _occurrences(source: String, token: String): Int =
    source.sliding(token.length).count(_ == token)

  private val _output_paths = Vector(
    Paths.get("Person.scala"),
    Paths.get("read/Person.scala"),
    Paths.get("operation/Person.scala"),
    Paths.get("aggregate/Person.scala"),
    Paths.get("view/Person.scala"),
    Paths.get("view/summary/Person.scala"),
    Paths.get("view/detail/Person.scala")
  )

  private val _input_paths = Vector(
    Paths.get("create/Person.scala"),
    Paths.get("update/Person.scala"),
    Paths.get("query/Person.scala")
  )
}
