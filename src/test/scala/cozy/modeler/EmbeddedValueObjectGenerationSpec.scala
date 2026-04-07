package cozy.modeler

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.Comparator
import org.scalatest.funsuite.AnyFunSuite

/*
 * @since   Mar. 30, 2026
 * @version Apr.  7, 2026
 * @author  ASAMI, Tomoharu
 */
class EmbeddedValueObjectGenerationSpec extends AnyFunSuite {
  test("modeler-scala generates embedded value object collection attributes from 07.a sample") {
    val input = Paths.get("/Users/asami/src/dev2026/cncf-samples/samples/07.a-aggregate-single-record-lab/src/main/cozy/order-single-record-aggregate.cml")
    val out = Paths.get(sys.props("user.dir")).toAbsolutePath.normalize()
      .resolve("target/test-generated/modeler-scala-embedded-value-object")
    _delete_recursively(out)

    cozy.Cozy.main(Array("modeler-scala", input.toString, s"--save=${out.toString}"))

    val generatedEntity = out.resolve(
      "target/scala-3.3.7/src_managed/main/scala/org/sample/aggregatesinglerecord/entity/Order.scala"
    )
    val generatedValue = out.resolve(
      "target/scala-3.3.7/src_managed/main/scala/domain/value/OrderLine.scala"
    )
    assert(Files.exists(generatedEntity), s"generated entity file not found: $generatedEntity")
    assert(Files.exists(generatedValue), s"generated value file not found: $generatedValue")

    val entityContent = Files.readString(generatedEntity)
    val valueContent = Files.readString(generatedValue)

    assert(entityContent.contains("lines: Vector[OrderLine]"))
    assert(entityContent.contains("case m: org.goldenport.record.RecordPresentable => m.toRecord()"))
    assert(entityContent.contains("_record_get_vector_of_record_c(record, INPUT_KEYS_LINES)((r: Record) => domain.value.OrderLine.createC(r))"))
    assert(valueContent.contains("case class OrderLine(name: Name, quantity: Int) extends org.goldenport.record.RecordPresentable"))
    assert(valueContent.contains("given org.goldenport.convert.ValueReader[OrderLine]"))
  }

  test("modeler-scala generates single and optional embedded value object attributes") {
    val out = Paths.get(sys.props("user.dir")).toAbsolutePath.normalize()
      .resolve("target/test-generated/modeler-scala-embedded-value-object-single")
    _delete_recursively(out)
    val input = out.resolve("single-record-object-attributes.cml")
    Files.createDirectories(out)
    Files.writeString(
      input,
      """# COMPONENT
        |
        |## SingleRecordObjectAttributes
        |
        |### PACKAGE
        |
        |org.sample.singlevalueobject
        |
        |# VALUE
        |
        |## OrderLine
        |
        |### ATTRIBUTE
        |
        || name | type | multiplicity |
        ||------|------|--------------|
        || name | name | 1 |
        || quantity | int | 1 |
        |
        |# ENTITY
        |
        |## Order
        |
        |### ATTRIBUTE
        |
        || name | type | multiplicity |
        ||------|------|--------------|
        || id | entityid | 1 |
        || name | name | 1 |
        || primaryLine | OrderLine | 1 |
        || optionalLine | OrderLine | ? |
        |""".stripMargin
    )

    cozy.Cozy.main(Array("modeler-scala", input.toString, s"--save=${out.toString}"))

    val generatedEntity = out.resolve(
      "target/scala-3.3.7/src_managed/main/scala/org/sample/singlevalueobject/entity/Order.scala"
    )
    assert(Files.exists(generatedEntity), s"generated entity file not found: $generatedEntity")

    val entityContent = Files.readString(generatedEntity)
    assert(entityContent.contains("primaryLine: OrderLine"))
    assert(entityContent.contains("optionalLine: Option[OrderLine]"))
    assert(entityContent.contains("_record_get_as_c[OrderLine](record, INPUT_KEYS_PRIMARY_LINE).flatMap {"))
    assert(entityContent.contains("_record_get_as_c[domain.value.OrderLine](record, INPUT_KEYS_OPTIONAL_LINE).map(_ orElse optionalLine)"))
  }


  test("modeler-scala does not generate duplicate builder overloads for optional string attributes") {
    val out = Paths.get(sys.props("user.dir")).toAbsolutePath.normalize()
      .resolve("target/test-generated/modeler-scala-optional-string-attribute")
    _delete_recursively(out)
    val input = out.resolve("optional-string-attribute.cml")
    Files.createDirectories(out)
    Files.writeString(
      input,
      """# COMPONENT
        |
        |## OptionalStringAttribute
        |
        |### PACKAGE
        |
        |org.sample.optionalstring
        |
        |# ENTITY
        |
        |## Person
        |
        |### ATTRIBUTE
        |
        || name | type | multiplicity |
        ||------|------|--------------|
        || id | entityid | 1 |
        || name | name | 1 |
        || nickname | string | ? |
        |""".stripMargin
    )

    cozy.Cozy.main(Array("modeler-scala", input.toString, s"--save=${out.toString}"))

    val generatedEntity = out.resolve(
      "target/scala-3.3.7/src_managed/main/scala/org/sample/optionalstring/entity/Person.scala"
    )
    assert(Files.exists(generatedEntity), s"generated entity file not found: $generatedEntity")

    val entityContent = Files.readString(generatedEntity)
    assert(entityContent.contains("nickname: Option[String]"))
    assert(entityContent.contains("def withNickname(nickname: String): Person.Builder"))
    assert(entityContent.contains("def withNickname(nickname: Option[String]): Person.Builder"))
    assert(!entityContent.contains("String.parse(nickname)"))
    assert(entityContent.indexOf("def withNickname(nickname: String): Person.Builder") ==
      entityContent.lastIndexOf("def withNickname(nickname: String): Person.Builder"))
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
