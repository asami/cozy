package cozy.modeler

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.Comparator
import org.scalatest.funsuite.AnyFunSuite

/*
 * @since   May. 17, 2025
 * @version Mar. 19, 2026
 * @author  ASAMI, Tomoharu
 */
class ModelerGenerationSpec extends AnyFunSuite {
  test("modeler-scala generates DomainComponent") {
    val base = Paths.get(sys.props("user.dir")).toAbsolutePath.normalize()
    val input = base.resolve("src/test/resources/modeler/test.dox")
    val out = base.resolve("target/test-generated/modeler-scala")
    _delete_recursively(out)
    Files.createDirectories(out.getParent)

    cozy.Cozy.main(Array("modeler-scala", input.toString, s"--save=${out.toString}"))

    val generated = out.resolve(
      "target/scala-3.3.7/src_managed/main/scala/domain/DomainComponent.scala"
    )
    assert(Files.exists(generated), s"generated file not found: $generated")
    val content = Files.readString(generated)
    assert(content.contains("object DomainComponent"))
    assert(!content.contains("exec_from("))
    assert(!content.contains("collectionId: EntityCollectionId = ???"))
  }

  test("modeler-scala expands attributes from SimpleEntity parent") {
    val base = Paths.get(sys.props("user.dir")).toAbsolutePath.normalize()
    val input = base.resolve("src/test/resources/modeler/simpleentity-parent.dox")
    val out = base.resolve("target/test-generated/modeler-scala-simpleentity-parent")
    _delete_recursively(out)
    Files.createDirectories(out.getParent)

    cozy.Cozy.main(Array("modeler-scala", input.toString, s"--save=${out.toString}"))

    val generated = out.resolve(
      "target/scala-3.3.7/src_managed/main/scala/domain/Person.scala"
    )
    assert(Files.exists(generated), s"generated file not found: $generated")
    val content = Files.readString(generated)
    assert(content.contains("extends EntityPersistable"))
    assert(content.contains("case class Person(id: EntityId, name: Name, age: Option[Age])"))
    assert(content.contains("PROP_ID"))
    assert(content.contains("PROP_NAME"))
    assert(content.contains("PROP_AGE"))

    val generatedCreate = out.resolve(
      "target/scala-3.3.7/src_managed/main/scala/domain/create/Person.scala"
    )
    assert(Files.exists(generatedCreate), s"generated file not found: $generatedCreate")
    val createContent = Files.readString(generatedCreate)
    assert(createContent.contains("case class Person(id: Option[EntityId], name: Option[Name], age: Option[Age])"))
  }

  test("modeler-scala generates toDataStore with db column names") {
    val base = Paths.get(sys.props("user.dir")).toAbsolutePath.normalize()
    val input = base.resolve("src/test/resources/modeler/db-column-options.dox")
    val out = base.resolve("target/test-generated/modeler-scala-db-column-options")
    _delete_recursively(out)
    Files.createDirectories(out.getParent)

    cozy.Cozy.main(Array("modeler-scala", input.toString, s"--save=${out.toString}"))

    val generated = out.resolve(
      "target/scala-3.3.7/src_managed/main/scala/domain/Person.scala"
    )
    assert(Files.exists(generated), s"generated file not found: $generated")
    val content = Files.readString(generated)
    assert(content.contains("def toRecord(): Record"))
    assert(content.contains("\"external_id\" -> _to_external_value(id)"))
    assert(content.contains("\"display_name_ext\" -> _to_external_value(displayName)"))
    assert(content.contains("def toDataStore(): Record"))
    assert(content.contains("\"person_id\" -> _to_data_store_value(id)"))
    assert(content.contains("\"person_display_name\" -> _to_data_store_value(displayName)"))
    assert(content.contains("\"age\" -> _to_data_store_value(age)"))
    assert(content.contains("INPUT_KEYS_DISPLAY_NAME"))
    assert(content.contains("\"displayName\""))
    assert(content.contains("\"display_name\""))
    assert(content.contains("def schema(): Schema"))
    assert(content.contains("val schema: org.goldenport.schema.Schema = org.goldenport.schema.Schema("))
    assert(content.contains("org.goldenport.model.value.BaseContent.simple(\"displayName\")"))

    val generatedCreate = out.resolve(
      "target/scala-3.3.7/src_managed/main/scala/domain/create/Person.scala"
    )
    assert(Files.exists(generatedCreate), s"generated file not found: $generatedCreate")
    val createContent = Files.readString(generatedCreate)
    assert(createContent.contains("val schema: org.goldenport.schema.Schema = domain.Person.schema"))

    val generatedQuery = out.resolve(
      "target/scala-3.3.7/src_managed/main/scala/domain/query/Person.scala"
    )
    assert(Files.exists(generatedQuery), s"generated file not found: $generatedQuery")
    val queryContent = Files.readString(generatedQuery)
    assert(queryContent.contains("val schema: org.goldenport.schema.Schema = domain.Person.schema"))

    val generatedUpdate = out.resolve(
      "target/scala-3.3.7/src_managed/main/scala/domain/update/Person.scala"
    )
    assert(Files.exists(generatedUpdate), s"generated file not found: $generatedUpdate")
    val updateContent = Files.readString(generatedUpdate)
    assert(updateContent.contains("val schema: org.goldenport.schema.Schema = domain.Person.schema"))
  }

  private def _delete_recursively(path: Path): Unit = {
    if (Files.exists(path)) {
      val stream = Files.walk(path)
      try
        stream.sorted(Comparator.reverseOrder()).forEach(p => Files.deleteIfExists(p))
      finally
        stream.close()
    }
  }
}
