package cozy.modeler

import java.io.ByteArrayOutputStream
import java.io.PrintStream
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Paths
import scala.collection.JavaConverters._
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.goldenport.kaleidox.{Config => KaleidoxConfig, Model => KaleidoxModel}
import org.goldenport.record.v2.{CFormat, CMaxLength, CMinLength, CRegex}

/*
 * @since   Jun. 23, 2026
 * @version Jun. 23, 2026
 * @author  ASAMI, Tomoharu
 */
class ModelerDiagnosticsSpec extends AnyWordSpec with Matchers with GivenWhenThen with ModelerSpecSupport {
  "CML modeler diagnostics" should {
    "report invalid models clearly" which {
      "modeler rejects date-time as an attribute type" in {
        Given("an invalid CML source or diagnostic fixture")
        val base = Paths.get(sys.props("user.dir")).toAbsolutePath.normalize()
        val input = base.resolve("target/test-generated/invalid-date-time-type.dox")
        Files.createDirectories(input.getParent)
        Files.write(input,
        """# COMPONENT
          |
          |## InvalidDateTimeType
          |
          |# ENTITY
          |
          |## Event
          |
          |### Attribute
          |
          || name      | type      | multiplicity |
          ||-----------+-----------+--------------|
          || occurredAt| date-time | 1            |
          |""".stripMargin.getBytes(StandardCharsets.UTF_8))
        val out = base.resolve("target/test-generated/invalid-date-time-type-out")
        delete_recursively(out)

        val stdout = new ByteArrayOutputStream()
        val stderr = new ByteArrayOutputStream()
        Console.withOut(new PrintStream(stdout)) {
        Console.withErr(new PrintStream(stderr)) {
          When("Cozy validates the modeler input")
          cozy.Cozy.main(Array("modeler-scala", input.toString, "--save", out.toString.toString))
        }
      }

        val diagnostic = stdout.toString(StandardCharsets.UTF_8.name()) + stderr.toString(StandardCharsets.UTF_8.name())
        Then("the diagnostic output reports the expected failure contract")
        diagnostic should include ("Unknown CML attribute type: date-time")
        Files.exists(out) shouldBe false
      }

      "modeler linkage diagnostics resolves event and action references" in {
        Given("an invalid CML source or diagnostic fixture")
        val base = Paths.get(sys.props("user.dir")).toAbsolutePath.normalize()
        val input = base.resolve("src/test/resources/modeler/event-routing-subscription.dox")
        When("Cozy validates the modeler input")
        val model = KaleidoxModel.load(KaleidoxConfig.default.withoutLocation, input.toFile)
        val linkage = new cozy.modeler.Modeler().linkageDiagnostics(model)

        Then("the diagnostic output reports the expected failure contract")
        linkage.exists(e =>
        e.sectionPath == "SUBSCRIPTION/person-sync/eventName" &&
        e.target == "person.created" &&
        e.resolved &&
        e.facet == "event"
        ) shouldBe true
        linkage.exists(e =>
        e.sectionPath == "SUBSCRIPTION/person-sync/actionName" &&
        e.target == "person.sync" &&
        e.resolved &&
        e.facet == "action"
        ) shouldBe true
      }

      "modeler-scala emits default component metadata when COMPONENT section is missing" in {
        Given("an invalid CML source or diagnostic fixture")
        val base = Paths.get(sys.props("user.dir")).toAbsolutePath.normalize()
        val input = base.resolve("src/test/resources/modeler/component-subsystem-default-component.dox")
        val out = base.resolve("target/test-generated/modeler-scala-component-subsystem-default-component")
        delete_recursively(out)
        Files.createDirectories(out.getParent)

        When("Cozy validates the modeler input")
        cozy.Cozy.main(Array("modeler-scala", input.toString, "--save", out.toString.toString))

        val generated = out.resolve(
        "target/scala-3.3.7/src_managed/main/scala/domain/DomainComponent.scala"
        )
        val content = Files.readString(generated)
        Then("the diagnostic output reports the expected failure contract")
        content should include ("def componentDefinitionRecords: Vector[Record] = Vector(")
        content should include ("\"name\" -> \"domain\"")
        content should include ("\"coordinates\" -> Vector.empty")
        content should include ("\"componentlets\" -> Vector(\"audit_sink\")")
        content should include ("\"extension_points\" -> Vector(\"observability\")")
        content should include ("\"extension_bindings\" -> Record.empty")
      }

      "modeler-scala rejects invalid subsystem component coordinate" in {
        Given("an invalid CML source or diagnostic fixture")
        val base = Paths.get(sys.props("user.dir")).toAbsolutePath.normalize()
        val input = base.resolve("src/test/resources/modeler/component-subsystem-invalid-coordinate.dox")
        val out = base.resolve("target/test-generated/modeler-scala-component-subsystem-invalid-coordinate")
        delete_recursively(out)

        When("Cozy validates the modeler input")
        val output = run_modeler_scala(input, out)
        Then("the diagnostic output reports the expected failure contract")
        withClue(s"unexpected output: $output") {
        output should include ("invalid coordinate")
      }
      }

      "modeler-scala reports missing on in StateMachine transition" in {
        Given("an invalid CML source or diagnostic fixture")
        val base = Paths.get(sys.props("user.dir")).toAbsolutePath.normalize()
        val input = base.resolve("target/test-generated/modeler-errors/missing-on.dox")
        val out = base.resolve("target/test-generated/modeler-scala-statemachine-invalid-missing-on")
        delete_recursively(out)
        write_file(
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

        When("Cozy validates the modeler input")
        val output = run_modeler_scala(input, out)
        Then("the diagnostic output reports the expected failure contract")
        withClue(s"unexpected output: $output") {
        output should include ("transition requires on")
      }
        withClue(s"unexpected output: $output") {
        output should not include ("URI is not absolute")
      }
      }

      "modeler-scala reports unknown transition target in StateMachine" in {
        Given("an invalid CML source or diagnostic fixture")
        val base = Paths.get(sys.props("user.dir")).toAbsolutePath.normalize()
        val input = base.resolve("target/test-generated/modeler-errors/unknown-target.dox")
        val out = base.resolve("target/test-generated/modeler-scala-statemachine-invalid-unknown-target")
        delete_recursively(out)
        write_file(
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

        When("Cozy validates the modeler input")
        val output = run_modeler_scala(input, out)
        Then("the diagnostic output reports the expected failure contract")
        withClue(s"unexpected output: $output") {
        output should include ("target PublishedX is not defined")
      }
        withClue(s"unexpected output: $output") {
        output should not include ("URI is not absolute")
      }
      }

      "modeler-scala reports undeclared event in StateMachine" in {
        Given("an invalid CML source or diagnostic fixture")
        val base = Paths.get(sys.props("user.dir")).toAbsolutePath.normalize()
        val input = base.resolve("target/test-generated/modeler-errors/unknown-event.dox")
        val out = base.resolve("target/test-generated/modeler-scala-statemachine-invalid-unknown-event")
        delete_recursively(out)
        write_file(
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

        When("Cozy validates the modeler input")
        val output = run_modeler_scala(input, out)
        Then("the diagnostic output reports the expected failure contract")
        withClue(s"unexpected output: $output") {
        output should include ("undeclared event publish")
      }
        withClue(s"unexpected output: $output") {
        output should not include ("URI is not absolute")
      }
      }

      "modeler-scala rejects composition relationship without parent id field" in {
        Given("an invalid CML source or diagnostic fixture")
        val base = Paths.get(sys.props("user.dir")).toAbsolutePath.normalize()
        val input = base.resolve("target/test-generated/modeler-scala-invalid-composition-relationship.dox")
        val out = base.resolve("target/test-generated/modeler-scala-invalid-composition-relationship-out")
        delete_recursively(out)
        write_file(input,
        """# COMPONENT
          |
          |## Domain
          |
          |# ENTITY
          |
          |## SalesOrder
          |
          |### ATTRIBUTE
          |
          || name | type     | multiplicity |
          ||------+----------+--------------|
          || id   | entityid | 1            |
          |
          |## SalesOrderLine
          |
          |### ATTRIBUTE
          |
          || name | type     | multiplicity |
          ||------+----------+--------------|
          || id   | entityid | 1            |
          |
          |# RELATIONSHIP
          |
          |## SalesOrder.lines
          |
          |### KIND
          |
          |composition
          |
          |### SOURCE
          |
          |SalesOrder
          |
          |### TARGET
          |
          |SalesOrderLine
          |
          |### STORAGE
          |
          |child-parent-id-field
          |
          |# SERVICE
          |
          |## OrderService
          |""".stripMargin
        )

        When("Cozy validates the modeler input")
        val output = run_modeler_scala(input, out)
        Then("the diagnostic output reports the expected failure contract")
        withClue(s"unexpected output: $output") {
        output should include ("requires PARENT ID FIELD")
      }
      }

      "modeler-scala rejects embedded value object relationship without value field" in {
        Given("an invalid CML source or diagnostic fixture")
        val base = Paths.get(sys.props("user.dir")).toAbsolutePath.normalize()
        val input = base.resolve("target/test-generated/modeler-scala-invalid-embedded-value-field.dox")
        val out = base.resolve("target/test-generated/modeler-scala-invalid-embedded-value-field-out")
        delete_recursively(out)
        write_file(input,
        """# COMPONENT
          |
          |## Domain
          |
          |# ENTITY
          |
          |## SalesOrder
          |
          |### ATTRIBUTE
          |
          || name            | type            | multiplicity |
          ||-----------------+-----------------+--------------|
          || id              | entityid        | 1            |
          || shippingAddress | ShippingAddress | ?            |
          |
          |# VALUE
          |
          |## ShippingAddress
          |
          |### ATTRIBUTE
          |
          || name  | type   | multiplicity |
          ||-------+--------+--------------|
          || line1 | string | 1            |
          |
          |# RELATIONSHIP
          |
          |## SalesOrder.shippingAddress
          |
          |### KIND
          |
          |composition
          |
          |### SOURCE
          |
          |SalesOrder
          |
          |### TARGET
          |
          |ShippingAddress
          |
          |### STORAGE
          |
          |embedded-value-object
          |
          |# SERVICE
          |
          |## OrderService
          |""".stripMargin
        )

        When("Cozy validates the modeler input")
        val output = run_modeler_scala(input, out)
        Then("the diagnostic output reports the expected failure contract")
        withClue(s"unexpected output: $output") {
        output should include ("requires VALUE FIELD")
      }
      }

      "modeler-scala rejects embedded value object relationship with unknown target value" in {
        Given("an invalid CML source or diagnostic fixture")
        val base = Paths.get(sys.props("user.dir")).toAbsolutePath.normalize()
        val input = base.resolve("target/test-generated/modeler-scala-invalid-embedded-target-value.dox")
        val out = base.resolve("target/test-generated/modeler-scala-invalid-embedded-target-value-out")
        delete_recursively(out)
        write_file(input,
        """# COMPONENT
          |
          |## Domain
          |
          |# ENTITY
          |
          |## SalesOrder
          |
          |### ATTRIBUTE
          |
          || name | type     | multiplicity |
          ||------+----------+--------------|
          || id   | entityid | 1            |
          |
          |# RELATIONSHIP
          |
          |## SalesOrder.shippingAddress
          |
          |### KIND
          |
          |composition
          |
          |### SOURCE
          |
          |SalesOrder
          |
          |### TARGET
          |
          |ShippingAddress
          |
          |### STORAGE
          |
          |embedded-value-object
          |
          |### VALUE FIELD
          |
          |shippingAddress
          |
          |# SERVICE
          |
          |## OrderService
          |""".stripMargin
        )

        When("Cozy validates the modeler input")
        val output = run_modeler_scala(input, out)
        Then("the diagnostic output reports the expected failure contract")
        withClue(s"unexpected output: $output") {
        output should include ("must reference a VALUE")
      }
      }

      "modeler-scala rejects embedded value object relationship when value field is missing on source entity" in {
        Given("an invalid CML source or diagnostic fixture")
        val base = Paths.get(sys.props("user.dir")).toAbsolutePath.normalize()
        val input = base.resolve("target/test-generated/modeler-scala-invalid-embedded-missing-source-field.dox")
        val out = base.resolve("target/test-generated/modeler-scala-invalid-embedded-missing-source-field-out")
        delete_recursively(out)
        write_file(input,
        """# COMPONENT
          |
          |## Domain
          |
          |# ENTITY
          |
          |## SalesOrder
          |
          |### ATTRIBUTE
          |
          || name | type     | multiplicity |
          ||------+----------+--------------|
          || id   | entityid | 1            |
          |
          |# VALUE
          |
          |## ShippingAddress
          |
          |### ATTRIBUTE
          |
          || name  | type   | multiplicity |
          ||-------+--------+--------------|
          || line1 | string | 1            |
          |
          |# RELATIONSHIP
          |
          |## SalesOrder.shippingAddress
          |
          |### KIND
          |
          |composition
          |
          |### SOURCE
          |
          |SalesOrder
          |
          |### TARGET
          |
          |ShippingAddress
          |
          |### STORAGE
          |
          |embedded-value-object
          |
          |### VALUE FIELD
          |
          |shippingAddress
          |
          |# SERVICE
          |
          |## OrderService
          |""".stripMargin
        )

        When("Cozy validates the modeler input")
        val output = run_modeler_scala(input, out)
        Then("the diagnostic output reports the expected failure contract")
        withClue(s"unexpected output: $output") {
        output should include ("is not an ATTRIBUTE of SOURCE")
      }
      }

      "modeler-scala rejects embedded value object relationship when value field type differs from target value" in {
        Given("an invalid CML source or diagnostic fixture")
        val base = Paths.get(sys.props("user.dir")).toAbsolutePath.normalize()
        val input = base.resolve("target/test-generated/modeler-scala-invalid-embedded-wrong-source-field-type.dox")
        val out = base.resolve("target/test-generated/modeler-scala-invalid-embedded-wrong-source-field-type-out")
        delete_recursively(out)
        write_file(input,
        """# COMPONENT
          |
          |## Domain
          |
          |# ENTITY
          |
          |## SalesOrder
          |
          |### ATTRIBUTE
          |
          || name            | type   | multiplicity |
          ||-----------------+--------+--------------|
          || id              | entityid | 1          |
          || shippingAddress | string | ?            |
          |
          |# VALUE
          |
          |## ShippingAddress
          |
          |### ATTRIBUTE
          |
          || name  | type   | multiplicity |
          ||-------+--------+--------------|
          || line1 | string | 1            |
          |
          |# RELATIONSHIP
          |
          |## SalesOrder.shippingAddress
          |
          |### KIND
          |
          |composition
          |
          |### SOURCE
          |
          |SalesOrder
          |
          |### TARGET
          |
          |ShippingAddress
          |
          |### STORAGE
          |
          |embedded-value-object
          |
          |### VALUE FIELD
          |
          |shippingAddress
          |
          |# SERVICE
          |
          |## OrderService
          |""".stripMargin
        )

        When("Cozy validates the modeler input")
        val output = run_modeler_scala(input, out)
        Then("the diagnostic output reports the expected failure contract")
        withClue(s"unexpected output: $output") {
        output should include ("must have VALUE type")
      }
      }

      "modeler-scala rejects child binding against embedded value object relationship" in {
        Given("an invalid CML source or diagnostic fixture")
        val base = Paths.get(sys.props("user.dir")).toAbsolutePath.normalize()
        val input = base.resolve("target/test-generated/modeler-scala-invalid-embedded-child-binding.dox")
        val out = base.resolve("target/test-generated/modeler-scala-invalid-embedded-child-binding-out")
        delete_recursively(out)
        write_file(input,
        """# COMPONENT
          |
          |## Domain
          |
          |# ENTITY
          |
          |## SalesOrder
          |
          |### ATTRIBUTE
          |
          || name            | type            | multiplicity |
          ||-----------------+-----------------+--------------|
          || id              | entityid        | 1            |
          || shippingAddress | ShippingAddress | ?            |
          |
          |# VALUE
          |
          |## ShippingAddress
          |
          |### ATTRIBUTE
          |
          || name  | type   | multiplicity |
          ||-------+--------+--------------|
          || line1 | string | 1            |
          |
          |## RegisterOrder
          |
          |### ATTRIBUTE
          |
          || name            | type            | multiplicity |
          ||-----------------+-----------------+--------------|
          || shippingAddress | ShippingAddress | ?            |
          |
          |## RegisterOrderResult
          |
          |### ATTRIBUTE
          |
          || name      | type     | multiplicity |
          ||-----------+----------+--------------|
          || entity_id | entityid | 1            |
          |
          |# RELATIONSHIP
          |
          |## SalesOrder.shippingAddress
          |
          |### KIND
          |
          |composition
          |
          |### SOURCE
          |
          |SalesOrder
          |
          |### TARGET
          |
          |ShippingAddress
          |
          |### STORAGE
          |
          |embedded-value-object
          |
          |### VALUE FIELD
          |
          |shippingAddress
          |
          |# SERVICE
          |
          |## OrderService
          |
          |### OPERATION
          |
          |#### registerOrder
          |
          |##### TYPE
          |
          |COMMAND
          |
          |##### INPUT
          |
          |###### TYPE
          |
          |RegisterOrder
          |
          |##### OUTPUT
          |
          |###### TYPE
          |
          |RegisterOrderResult
          |
          |##### CHILD ENTITY BINDING
          |
          |###### RELATIONSHIP
          |
          |SalesOrder.shippingAddress
          |
          |###### INPUT
          |
          |shippingAddress
          |""".stripMargin
        )

        When("Cozy validates the modeler input")
        val output = run_modeler_scala(input, out)
        Then("the diagnostic output reports the expected failure contract")
        withClue(s"unexpected output: $output") {
        output should include ("requires child-parent-id-field storage")
      }
      }

      "modeler-scala rejects RELATIONSHIP heading without required blank line" in {
        Given("an invalid CML source or diagnostic fixture")
        val base = Paths.get(sys.props("user.dir")).toAbsolutePath.normalize()
        val input = base.resolve("target/test-generated/modeler-scala-relationship-missing-blank-line.dox")
        val out = base.resolve("target/test-generated/modeler-scala-relationship-missing-blank-line-out")
        delete_recursively(out)
        write_file(input,
        """# COMPONENT
          |
          |## Domain
          |
          |# ENTITY
          |
          |## SalesOrder
          |
          |### ATTRIBUTE
          |
          || name | type     | multiplicity |
          ||------+----------+--------------|
          || id   | entityid | 1            |
          |
          |## SalesOrderLine
          |
          |### ATTRIBUTE
          |
          || name    | type     | multiplicity |
          ||---------+----------+--------------|
          || id      | entityid | 1            |
          || orderId | entityid | 1            |
          |
          |# RELATIONSHIP
          |## SalesOrder.lines
          |
          |### KIND
          |
          |composition
          |
          |### SOURCE
          |
          |SalesOrder
          |
          |### TARGET
          |
          |SalesOrderLine
          |
          |### STORAGE
          |
          |child-parent-id-field
          |
          |### PARENT ID FIELD
          |
          |orderId
          |
          |# SERVICE
          |
          |## OrderService
          |""".stripMargin
        )

        When("Cozy validates the modeler input")
        val output = run_modeler_scala(input, out)
        Then("the diagnostic output reports the expected failure contract")
        withClue(s"unexpected output: $output") {
        output should include ("requires a blank line after the section heading")
      }
      }

    }
  }
}
