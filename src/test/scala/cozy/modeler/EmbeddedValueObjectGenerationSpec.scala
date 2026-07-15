package cozy.modeler

import java.nio.file.Paths
import java.nio.file.Files
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Mar. 30, 2026
 *  version Apr. 20, 2026
 *  version May. 24, 2026
 *  version Jun. 23, 2026
 * @version Jul. 15, 2026
 * @author  ASAMI, Tomoharu
 */
class EmbeddedValueObjectGenerationSpec extends AnyWordSpec with Matchers with GivenWhenThen with ModelerSpecSupport {
  "Embedded value object generation" should {
    "generate collection attributes from the 09.a sample" in {
      Given("the 09.a aggregate single record sample CML")
      val input = Paths.get("/Users/asami/src/dev2026/cncf-samples/samples/09.a-aggregate-single-record-lab/src/main/cozy/order-single-record-aggregate.cml")
      val out = Paths.get(sys.props("user.dir")).toAbsolutePath.normalize()
        .resolve("target/test-generated/modeler-scala-embedded-value-object")
      delete_recursively(out)

      When("Cozy generates Scala code for the sample")
      cozy.Cozy.main(Array("modeler-scala", input.toString, "--save", out.toString.toString))

      Then("the generated entity and value files exist")
      val generatedentity = out.resolve(
        "target/scala-3.3.8/src_managed/main/scala/org/sample/aggregatesinglerecord/entity/Order.scala"
      )
      val generatedvalue = out.resolve(
        "target/scala-3.3.8/src_managed/main/scala/org/sample/aggregatesinglerecord/value/OrderLine.scala"
      )
      withClue(s"generated entity file not found: $generatedentity") {
        Files.exists(generatedentity) shouldBe true
      }
      withClue(s"generated value file not found: $generatedvalue") {
        Files.exists(generatedvalue) shouldBe true
      }

      And("the collection value object mapping is preserved")
      val entitycontent = Files.readString(generatedentity)
      val valuecontent = Files.readString(generatedvalue)

      entitycontent should include ("lines: Vector[OrderLine]")
      entitycontent should include ("case m: org.goldenport.record.RecordPresentable => m.toRecord()")
      entitycontent should include ("_record_get_vector_as_c[org.sample.aggregatesinglerecord.value.OrderLine](record, INPUT_KEYS_LINES).flatMap {")
      valuecontent should include ("case class OrderLine(name: Name")
      valuecontent should include ("quantity: Int) extends org.goldenport.record.RecordPresentable")
      valuecontent should not include "name entries must have length >= 1"
      valuecontent should not include "name entries must have length <= 256"
      valuecontent should include ("given org.goldenport.convert.ValueReader[OrderLine]")
      valuecontent should include ("case m: Record => createC(m)")
    }

    "generate single and optional embedded value object attributes" in {
      Given("a model with required and optional embedded value object fields")
      val out = Paths.get(sys.props("user.dir")).toAbsolutePath.normalize()
        .resolve("target/test-generated/modeler-scala-embedded-value-object-single")
      delete_recursively(out)
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

      When("Cozy generates Scala code")
      cozy.Cozy.main(Array("modeler-scala", input.toString, "--save", out.toString.toString))

      Then("the generated entity preserves required and optional value object semantics")
      val generatedentity = out.resolve(
        "target/scala-3.3.8/src_managed/main/scala/org/sample/singlevalueobject/entity/Order.scala"
      )
      withClue(s"generated entity file not found: $generatedentity") {
        Files.exists(generatedentity) shouldBe true
      }

      val entitycontent = Files.readString(generatedentity)
      entitycontent should include ("primaryLine: OrderLine")
      entitycontent should include ("optionalLine: Option[OrderLine]")
      entitycontent should include ("_record_get_as_c[OrderLine](record, INPUT_KEYS_PRIMARY_LINE).flatMap {")
      entitycontent should include ("_record_get_as_c[org.sample.singlevalueobject.value.OrderLine](record, INPUT_KEYS_OPTIONAL_LINE).map(_ orElse optionalLine)")
    }

    "avoid duplicate builder overloads for optional string attributes" in {
      Given("an entity with an optional string attribute")
      val out = Paths.get(sys.props("user.dir")).toAbsolutePath.normalize()
        .resolve("target/test-generated/modeler-scala-optional-string-attribute")
      delete_recursively(out)
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

      When("Cozy generates Scala code")
      cozy.Cozy.main(Array("modeler-scala", input.toString, "--save", out.toString.toString))

      Then("the generated builder contains one overload for each supported optional input shape")
      val generatedentity = out.resolve(
        "target/scala-3.3.8/src_managed/main/scala/org/sample/optionalstring/entity/Person.scala"
      )
      withClue(s"generated entity file not found: $generatedentity") {
        Files.exists(generatedentity) shouldBe true
      }

      val entitycontent = Files.readString(generatedentity)
      entitycontent should include ("nickname: Option[String]")
      entitycontent should include ("def withNickname(nickname: String): Person.Builder")
      entitycontent should include ("def withNickname(nickname: Option[String]): Person.Builder")
      entitycontent should not include ("String.parse(nickname)")

      And("the string overload is emitted only once")
      entitycontent.indexOf("def withNickname(nickname: String): Person.Builder") shouldBe
        entitycontent.lastIndexOf("def withNickname(nickname: String): Person.Builder")
    }
  }
}
