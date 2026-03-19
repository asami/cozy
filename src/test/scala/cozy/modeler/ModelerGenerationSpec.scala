package cozy.modeler

import java.io.ByteArrayOutputStream
import java.io.PrintStream
import java.nio.charset.StandardCharsets
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
    assert(content.contains("extends Component with CollectionTransitionRuleProvider"))
    assert(content.contains("override def stateMachineTransitionRules: Vector[CollectionTransitionRule[Any]] = Vector.empty"))
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

  test("modeler-scala parses StateMachine CML heading syntax") {
    val base = Paths.get(sys.props("user.dir")).toAbsolutePath.normalize()
    val input = base.resolve("src/test/resources/modeler/statemachine-cml.dox")
    val out = base.resolve("target/test-generated/modeler-scala-statemachine-cml")
    _delete_recursively(out)
    Files.createDirectories(out.getParent)

    cozy.Cozy.main(Array("modeler-scala", input.toString, s"--save=${out.toString}"))

    val generated = out.resolve(
      "target/scala-3.3.7/src_managed/main/scala/domain/DomainComponent.scala"
    )
    assert(Files.exists(generated), s"generated file not found: $generated")
    val content = Files.readString(generated)
    assert(content.contains("override def stateMachineTransitionRules: Vector[CollectionTransitionRule[Any]] = Vector("))
    assert(content.contains("eventName = \"publish\""))
    assert(content.contains("guard = Some(StateMachineRuleBuilder.guardExpression[Any](\"event.amount > 0\")"))
    assert(content.contains("StateMachineRuleBuilder.updateRule[Any]("))
  }

  test("modeler-scala maps identifier guard to guardRef") {
    val base = Paths.get(sys.props("user.dir")).toAbsolutePath.normalize()
    val input = base.resolve("src/test/resources/modeler/statemachine-cml-guard-ref.dox")
    val out = base.resolve("target/test-generated/modeler-scala-statemachine-cml-guard-ref")
    _delete_recursively(out)
    Files.createDirectories(out.getParent)

    cozy.Cozy.main(Array("modeler-scala", input.toString, s"--save=${out.toString}"))

    val generated = out.resolve(
      "target/scala-3.3.7/src_managed/main/scala/domain/DomainComponent.scala"
    )
    assert(Files.exists(generated), s"generated file not found: $generated")
    val content = Files.readString(generated)
    assert(content.contains("eventName = \"publish\""))
    assert(content.contains("guard = Some(StateMachineRuleBuilder.guardRef[Any](\"paymentConfirmed\", stateMachineGuardResolver))"))
  }

  test("modeler-scala reports missing on in StateMachine transition") {
    val base = Paths.get(sys.props("user.dir")).toAbsolutePath.normalize()
    val input = base.resolve("target/test-generated/modeler-errors/missing-on.dox")
    val out = base.resolve("target/test-generated/modeler-scala-statemachine-invalid-missing-on")
    _delete_recursively(out)
    _write(
      input,
      """# Entity
        |
        |## Person
        |
        |### Attribute
        |
        || name | type     | multiplicity |
        ||------+----------+--------------|
        || id   | entityid | 1            |
        || name | name     | 1            |
        |
        |### StateMachine
        |
        |#### lifecycle
        |
        |##### State
        |
        |###### Draft
        |
        |####### Transition
        |- to :: Published
        |- guard :: paymentConfirmed
        |
        |###### Published
        |""".stripMargin
    )

    val output = _run_modeler_scala(input, out)
    assert(output.contains("transition requires on"), s"unexpected output: $output")
    assert(!output.contains("URI is not absolute"), s"unexpected output: $output")
  }

  test("modeler-scala reports unknown transition target in StateMachine") {
    val base = Paths.get(sys.props("user.dir")).toAbsolutePath.normalize()
    val input = base.resolve("target/test-generated/modeler-errors/unknown-target.dox")
    val out = base.resolve("target/test-generated/modeler-scala-statemachine-invalid-unknown-target")
    _delete_recursively(out)
    _write(
      input,
      """# Entity
        |
        |## Person
        |
        |### Attribute
        |
        || name | type     | multiplicity |
        ||------+----------+--------------|
        || id   | entityid | 1            |
        || name | name     | 1            |
        |
        |### StateMachine
        |
        |#### lifecycle
        |
        |##### State
        |
        |###### Draft
        |
        |####### Transition
        |- to :: PublishedX
        |- on :: publish
        |
        |###### Published
        |
        |##### Event
        |
        |###### publish
        |""".stripMargin
    )

    val output = _run_modeler_scala(input, out)
    assert(output.contains("target PublishedX is not defined"), s"unexpected output: $output")
    assert(!output.contains("URI is not absolute"), s"unexpected output: $output")
  }

  test("modeler-scala reports undeclared event in StateMachine") {
    val base = Paths.get(sys.props("user.dir")).toAbsolutePath.normalize()
    val input = base.resolve("target/test-generated/modeler-errors/unknown-event.dox")
    val out = base.resolve("target/test-generated/modeler-scala-statemachine-invalid-unknown-event")
    _delete_recursively(out)
    _write(
      input,
      """# Entity
        |
        |## Person
        |
        |### Attribute
        |
        || name | type     | multiplicity |
        ||------+----------+--------------|
        || id   | entityid | 1            |
        || name | name     | 1            |
        |
        |### StateMachine
        |
        |#### lifecycle
        |
        |##### State
        |
        |###### Draft
        |
        |####### Transition
        |- to :: Published
        |- on :: publish
        |
        |###### Published
        |
        |##### Event
        |
        |###### approve
        |""".stripMargin
    )

    val output = _run_modeler_scala(input, out)
    assert(output.contains("undeclared event publish"), s"unexpected output: $output")
    assert(!output.contains("URI is not absolute"), s"unexpected output: $output")
  }

  private def _run_modeler_scala(input: Path, out: Path): String = {
    Files.createDirectories(out.getParent)
    val outBuffer = new ByteArrayOutputStream
    val errBuffer = new ByteArrayOutputStream
    val outPs = new PrintStream(outBuffer, true, StandardCharsets.UTF_8.name())
    val errPs = new PrintStream(errBuffer, true, StandardCharsets.UTF_8.name())
    try {
      Console.withOut(outPs) {
        Console.withErr(errPs) {
          cozy.Cozy.main(Array("modeler-scala", input.toString, s"--save=${out.toString}"))
        }
      }
    } finally {
      outPs.close()
      errPs.close()
    }
    outBuffer.toString(StandardCharsets.UTF_8.name()) + "\n" + errBuffer.toString(StandardCharsets.UTF_8.name())
  }

  private def _write(path: Path, content: String): Unit = {
    Files.createDirectories(path.getParent)
    Files.writeString(path, content)
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
