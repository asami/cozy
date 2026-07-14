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
 * @version Jul. 15, 2026
 * @author  ASAMI, Tomoharu
 */
class ModelerServiceOperationSpec extends AnyWordSpec with Matchers with GivenWhenThen with ModelerSpecSupport {
  "CML service and operation generation" should {
    "compile service and operation contracts" which {
      "kaleidox parses and normalizes OPERATION grammar" in {
        Given("a CML service or operation contract fixture")
        val base = Paths.get(sys.props("user.dir")).toAbsolutePath.normalize()
        val input = base.resolve("src/test/resources/modeler/operation-grammar.dox")
        When("Cozy parses or generates the service operation model")
        val model = KaleidoxModel.load(KaleidoxConfig.default.withoutLocation, input.toFile)
        val opmodel = model.takeOperationModel
        val normalized = opmodel.normalizedOperations

        Then("the operation contract is accepted or rejected according to the specification")
        normalized.exists(x => x.name == "createOrder" && x.kind.toString == "Command" && x.inputType == "CreateOrder") shouldBe true
        normalized.exists(x => x.name == "getOrder" && x.kind.toString == "Query" && x.inputType == "GetOrder") shouldBe true
        normalized.exists(x => x.name == "savePerson" && x.inputType == "SavePersonCommand") shouldBe true
        normalized.find(_.name == "createOrder").flatMap(_.precondition) shouldBe Some("Customer can submit a new order.")
        normalized.find(_.name == "createOrder").flatMap(_.postcondition) shouldBe Some("The order creation request is accepted.")
        normalized.find(_.name == "createOrder").exists(_.rules.nonEmpty) shouldBe true
        normalized.size shouldBe 3
      }

      "kaleidox parses OPERATION kind marker grammar (COMMAND/QUERY headings)" in {
        Given("a CML service or operation contract fixture")
        val base = Paths.get(sys.props("user.dir")).toAbsolutePath.normalize()
        val input = base.resolve("src/test/resources/modeler/operation-grammar-kind-marker.dox")
        When("Cozy parses or generates the service operation model")
        val model = KaleidoxModel.load(KaleidoxConfig.default.withoutLocation, input.toFile)
        val opmodel = model.takeOperationModel
        val normalized = opmodel.normalizedOperations

        Then("the operation contract is accepted or rejected according to the specification")
        normalized.exists(x => x.name == "createOrder" && x.kind.toString == "Command" && x.inputType == "CreateOrder") shouldBe true
        normalized.exists(x => x.name == "getOrder" && x.kind.toString == "Query" && x.inputType == "GetOrder") shouldBe true
        normalized.size shouldBe 2
      }

      "modeler-scala rejects top-level OPERATION section" in {
        Given("a CML service or operation contract fixture")
        val base = Paths.get(sys.props("user.dir")).toAbsolutePath.normalize()
        val input = base.resolve("src/test/resources/modeler/operation-grammar.dox")
        val out = base.resolve("target/test-generated/modeler-scala-operation-grammar")
        delete_recursively(out)

        When("Cozy parses or generates the service operation model")
        val output = run_modeler_scala(input, out)
        Then("the operation contract is accepted or rejected according to the specification")
        withClue(s"unexpected output: $output") {
        output should include ("Top-level OPERATION is not supported; define operations under SERVICE.")
      }
      }

      "modeler-scala rejects SERVICE without COMPONENT" in {
        Given("a CML service or operation contract fixture")
        val base = Paths.get(sys.props("user.dir")).toAbsolutePath.normalize()
        val input = base.resolve("target/test-generated/modeler-scala-service-without-component.dox")
        val out = base.resolve("target/test-generated/modeler-scala-service-without-component-out")
        delete_recursively(out)
        write_file(input,
        """# SERVICE
          |
          |## Greeting
          |
          |### OPERATION
          |
          |#### greeting
          |
          |##### TYPE
          |QUERY
          |##### INPUT
          |###### TYPE
          |GreetingQuery
          |##### OUTPUT
          |###### TYPE
          |GreetingResult
          |
          |# QUERY
          |
          |## GreetingQuery
          |
          |# VALUE
          |
          |## GreetingResult
          |""".stripMargin)

        When("Cozy parses or generates the service operation model")
        val output = run_modeler_scala(input, out)
        Then("the operation contract is accepted or rejected according to the specification")
        withClue(s"unexpected output: $output") {
        output should include ("SERVICE requires COMPONENT; services are owned by a component.")
      }
      }

      "modeler-scala rejects partial SERVICE operation contract" in {
        Given("a CML service or operation contract fixture")
        val base = Paths.get(sys.props("user.dir")).toAbsolutePath.normalize()
        val input = base.resolve("target/test-generated/modeler-scala-partial-service-operation.dox")
        val out = base.resolve("target/test-generated/modeler-scala-partial-service-operation-out")
        delete_recursively(out)
        write_file(input,
        """# COMPONENT
          |
          |## Domain
          |
          |### PACKAGE
          |
          |domain
          |
          |# SERVICE
          |
          |## Greeting
          |
          |### OPERATION
          |
          |#### greeting
          |
          |##### TYPE
          |QUERY
          |##### INPUT
          |###### TYPE
          |GreetingQuery
          |##### OUTPUT
          |Greeting result.
          |
          |# QUERY
          |
          |## GreetingQuery
          |
          |# VALUE
          |
          |## GreetingResult
          |""".stripMargin)

        When("Cozy parses or generates the service operation model")
        val output = run_modeler_scala(input, out)
        Then("the operation contract is accepted or rejected according to the specification")
        withClue(s"unexpected output: $output") {
        output should include ("Operation 'greeting' requires OUTPUT TYPE.")
      }
      }

      "modeler-scala accepts SERVICE operation direct property notation with narrative" in {
        Given("a CML service or operation contract fixture")
        val base = Paths.get(sys.props("user.dir")).toAbsolutePath.normalize()
        val input = base.resolve("target/test-generated/modeler-scala-service-operation-direct-property.dox")
        val out = base.resolve("target/test-generated/modeler-scala-service-operation-direct-property-out")
        delete_recursively(out)
        write_file(input,
        """# COMPONENT
          |
          |## Domain
          |
          |### PACKAGE
          |
          |domain
          |
          |# SERVICE
          |
          |## Greeting
          |
          |### OPERATION
          |
          |#### greeting
          |
          |Greeting dashboard query.
          |
          |- type :: QUERY
          |- input :: GreetingQuery
          |- output :: GreetingResult
          |- execution :: async-job
          |
          |This operation is intentionally documented around the property list.
          |
          |# QUERY
          |
          |## GreetingQuery
          |
          |# VALUE
          |
          |## GreetingResult
          |
          |### EXTENDS
          |
          |OperationResult
          |""".stripMargin)

        When("Cozy parses or generates the service operation model")
        val output = run_modeler_scala(input, out)
        Then("the operation contract is accepted or rejected according to the specification")
        withClue(s"unexpected output: $output") {
        output should not include ("requires TYPE")
      }
        withClue(s"unexpected output: $output") {
        output should not include ("is not defined")
      }
        val component = out.resolve("target/scala-3.3.7/src_managed/main/scala/domain/DomainComponent.scala")
        val content = Files.readString(component)
        content should include ("name = \"greeting\"")
        content should include ("""execution = Some("async-job")""")
        content should include ("inputType = \"GreetingQuery\"")
        content should include ("outputType = \"GreetingResult\"")
      }

      "modeler-scala accepts SERVICE operation HOCON direct properties" in {
        Given("a CML service or operation contract fixture")
        val base = Paths.get(sys.props("user.dir")).toAbsolutePath.normalize()
        val input = base.resolve("target/test-generated/modeler-scala-service-operation-hocon-property.dox")
        val out = base.resolve("target/test-generated/modeler-scala-service-operation-hocon-property-out")
        delete_recursively(out)
        write_file(input,
        """# COMPONENT
          |
          |## Domain
          |
          |### PACKAGE
          |
          |domain
          |
          |# SERVICE
          |
          |## Greeting
          |
          |### OPERATION
          |
          |#### greeting
          |
          |type = QUERY
          |input = GreetingQuery
          |result = GreetingResult
          |
          |# QUERY
          |
          |## GreetingQuery
          |
          |# VALUE
          |
          |## GreetingResult
          |
          |### EXTENDS
          |
          |OperationResult
          |""".stripMargin)

        When("Cozy parses or generates the service operation model")
        val output = run_modeler_scala(input, out)
        Then("the operation contract is accepted or rejected according to the specification")
        withClue(s"unexpected output: $output") {
        output should not include ("requires TYPE")
      }
        withClue(s"unexpected output: $output") {
        output should not include ("is not defined")
      }
        val component = out.resolve("target/scala-3.3.7/src_managed/main/scala/domain/DomainComponent.scala")
        val content = Files.readString(component)
        content should include ("inputType = \"GreetingQuery\"")
        content should include ("outputType = \"GreetingResult\"")
      }

      "modeler-scala rejects conflicting direct and section operation properties" in {
        Given("a CML service or operation contract fixture")
        val base = Paths.get(sys.props("user.dir")).toAbsolutePath.normalize()
        val input = base.resolve("target/test-generated/modeler-scala-service-operation-property-conflict.dox")
        val out = base.resolve("target/test-generated/modeler-scala-service-operation-property-conflict-out")
        delete_recursively(out)
        write_file(input,
        """# COMPONENT
          |
          |## Domain
          |
          |### PACKAGE
          |
          |domain
          |
          |# SERVICE
          |
          |## Greeting
          |
          |### OPERATION
          |
          |#### greeting
          |
          |- type :: QUERY
          |- input :: GreetingQuery
          |- output :: GreetingResult
          |
          |##### INPUT
          |
          |###### TYPE
          |
          |OtherGreetingQuery
          |
          |# QUERY
          |
          |## GreetingQuery
          |
          |## OtherGreetingQuery
          |
          |# VALUE
          |
          |## GreetingResult
          |
          |### EXTENDS
          |
          |OperationResult
          |""".stripMargin)

        When("Cozy parses or generates the service operation model")
        val output = run_modeler_scala(input, out)
        Then("the operation contract is accepted or rejected according to the specification")
        withClue(s"unexpected output: $output") {
        output should include ("Operation greeting direct INPUT GreetingQuery conflicts with INPUT section OtherGreetingQuery.")
      }
      }

      "modeler-scala accepts POWERTYPE references as operation input fields" in {
        Given("a CML service or operation contract fixture")
        val base = Paths.get(sys.props("user.dir")).toAbsolutePath.normalize()
        val input = base.resolve("target/test-generated/modeler-scala-powertype-operation-field.dox")
        val out = base.resolve("target/test-generated/modeler-scala-powertype-operation-field-out")
        delete_recursively(out)
        write_file(input,
        """# COMPONENT
          |
          |## Domain
          |
          |### PACKAGE
          |
          |domain
          |
          |# SERVICE
          |
          |## Book
          |
          |### OPERATION
          |
          |#### listBooks
          |
          |- type :: QUERY
          |- input :: ListBooks
          |- output :: ListBooksResult
          |
          |# QUERY
          |
          |## ListBooks
          |
          || name | type | multiplicity |
          || --- | --- | --- |
          || state | BookRecordState | zero-one |
          |
          |# POWERTYPE
          |
          |## BookRecordState
          |
          |package = domain.value
          |
          || name | label |
          || --- | --- |
          || imported | Imported |
          || confirmed | Confirmed |
          |
          |# VALUE
          |
          |## ListBooksResult
          |
          |### EXTENDS
          |
          |OperationResult
          |""".stripMargin)

        When("Cozy parses or generates the service operation model")
        val output = run_modeler_scala(input, out)
        Then("the operation contract is accepted or rejected according to the specification")
        withClue(s"unexpected output: $output") {
        output should not include ("is not defined")
      }
        val component = out.resolve("target/scala-3.3.7/src_managed/main/scala/domain/DomainComponent.scala")
        val powertype = out.resolve("target/scala-3.3.7/src_managed/main/scala/domain/value/BookRecordState.scala")
        val componentcontent = Files.readString(component)
        val powertypecontent = Files.readString(powertype)
        componentcontent should include ("inputType = \"ListBooks\"")
        powertypecontent should include ("case class Builder(value: Option[String] = None")
        powertypecontent should include ("val imported: BookRecordState = BookRecordState(\"imported\")")
      }

      "modeler-scala rejects POWERTYPE narrative sections as operation input fields" in {
        Given("a CML service or operation contract fixture")
        val base = Paths.get(sys.props("user.dir")).toAbsolutePath.normalize()
        val input = base.resolve("target/test-generated/modeler-scala-powertype-narrative-input.dox")
        val out = base.resolve("target/test-generated/modeler-scala-powertype-narrative-input-out")
        delete_recursively(out)
        write_file(input,
        """# COMPONENT
          |
          |## Domain
          |
          |### PACKAGE
          |
          |domain
          |
          |# SERVICE
          |
          |## Book
          |
          |### OPERATION
          |
          |#### listBooks
          |
          |- type :: QUERY
          |- input :: Summary
          |- output :: ListBooksResult
          |
          |# POWERTYPE
          |
          |## SUMMARY
          |
          |Narrative text, not a powertype class.
          |
          |## BookRecordState
          |package = domain.value
          |
          |### imported
          |label = Imported
          |
          |# VALUE
          |
          |## ListBooksResult
          |
          |### EXTENDS
          |
          |OperationResult
          |""".stripMargin)

        When("Cozy parses or generates the service operation model")
        val output = run_modeler_scala(input, out)
        Then("the operation contract is accepted or rejected according to the specification")
        withClue(s"unexpected output: $output") {
        output should include ("Operation 'listBooks' INPUT TYPE 'Summary' is not defined.")
      }
      }

      "modeler-scala rejects SERVICE operation with undefined INPUT TYPE" in {
        Given("a CML service or operation contract fixture")
        val base = Paths.get(sys.props("user.dir")).toAbsolutePath.normalize()
        val input = base.resolve("target/test-generated/modeler-scala-undefined-input-type.dox")
        val out = base.resolve("target/test-generated/modeler-scala-undefined-input-type-out")
        delete_recursively(out)
        write_file(input,
        """# COMPONENT
          |
          |## Domain
          |
          |### PACKAGE
          |
          |domain
          |
          |# SERVICE
          |
          |## Greeting
          |
          |### OPERATION
          |
          |#### greeting
          |
          |##### TYPE
          |
          |QUERY
          |
          |##### INPUT
          |
          |###### TYPE
          |
          |MissingQuery
          |
          |##### OUTPUT
          |
          |###### TYPE
          |
          |GreetingResult
          |
          |# VALUE
          |
          |## GreetingResult
          |
          |### EXTENDS
          |
          |OperationResult
          |""".stripMargin)

        When("Cozy parses or generates the service operation model")
        val output = run_modeler_scala(input, out)
        Then("the operation contract is accepted or rejected according to the specification")
        withClue(s"unexpected output: $output") {
        output should include ("Operation 'greeting' INPUT TYPE 'MissingQuery' is not defined.")
      }
      }

      "modeler-scala rejects SERVICE operation with undefined OUTPUT TYPE" in {
        Given("a CML service or operation contract fixture")
        val base = Paths.get(sys.props("user.dir")).toAbsolutePath.normalize()
        val input = base.resolve("target/test-generated/modeler-scala-undefined-output-type.dox")
        val out = base.resolve("target/test-generated/modeler-scala-undefined-output-type-out")
        delete_recursively(out)
        write_file(input,
        """# COMPONENT
          |
          |## Domain
          |
          |### PACKAGE
          |
          |domain
          |
          |# SERVICE
          |
          |## Greeting
          |
          |### OPERATION
          |
          |#### greeting
          |
          |##### TYPE
          |
          |QUERY
          |
          |##### INPUT
          |
          |###### TYPE
          |
          |GreetingQuery
          |
          |##### OUTPUT
          |
          |###### TYPE
          |
          |MissingResult
          |
          |# QUERY
          |
          |## GreetingQuery
          |""".stripMargin)

        When("Cozy parses or generates the service operation model")
        val output = run_modeler_scala(input, out)
        Then("the operation contract is accepted or rejected according to the specification")
        withClue(s"unexpected output: $output") {
        output should include ("Operation 'greeting' OUTPUT TYPE 'MissingResult' is not defined.")
      }
      }

      "modeler-scala accepts OperationResult as a built-in output type" in {
        Given("a CML service or operation contract fixture")
        val base = Paths.get(sys.props("user.dir")).toAbsolutePath.normalize()
        val input = base.resolve("target/test-generated/modeler-scala-builtin-output-type.dox")
        val out = base.resolve("target/test-generated/modeler-scala-builtin-output-type-out")
        delete_recursively(out)
        write_file(input,
        """# COMPONENT
          |
          |## Domain
          |
          |### PACKAGE
          |
          |domain
          |
          |# SERVICE
          |
          |## Greeting
          |
          |### OPERATION
          |
          |#### greeting
          |
          |##### TYPE
          |
          |QUERY
          |
          |##### INPUT
          |
          |###### TYPE
          |
          |GreetingQuery
          |
          |##### OUTPUT
          |
          |###### TYPE
          |
          |OperationResult
          |
          |# QUERY
          |
          |## GreetingQuery
          |""".stripMargin)

        When("Cozy parses or generates the service operation model")
        val output = run_modeler_scala(input, out)
        Then("the operation contract is accepted or rejected according to the specification")
        withClue(s"unexpected output: $output") {
        output should not include ("is not defined")
      }
      }

      "modeler-scala emits CNCF help source metadata for described service and operation" in {
        Given("a CML service or operation contract fixture")
        val base = Paths.get(sys.props("user.dir")).toAbsolutePath.normalize()
        val input = base.resolve("src/test/resources/modeler/service-help-metadata.dox")
        val out = base.resolve("target/test-generated/modeler-scala-service-help-metadata")
        delete_recursively(out)
        Files.createDirectories(out.getParent)

        When("Cozy parses or generates the service operation model")
        run_modeler_scala(input, out)

        val generated = out.resolve(
        "target/scala-3.3.7/src_managed/main/scala/domain/DomainComponent.scala"
        )
        val content = Files.readString(generated)
        Then("the operation contract is accepted or rejected according to the specification")
        content should include ("""ServiceDefinition.Specification.Builder("address").""")
        content should include ("""def useCaseRecords: Vector[Record] =""")
        content should include (""""name" -> "postal_lookup""")
        content should include ("""// Address service for postal address support.Provides help-visible metadata for CNCF projections.""")
        content should include ("""Use cases:""")
        content should include ("""postal_lookup: Look up an address from a postal code.""")
        content should include ("""OperationDefinition.Specification.Builder("lookupAddress").""")
        content should include ("""// Look up an address by postal code.Returns a normalized address representation.""")
      }

      "modeler-scala emits operationDefinitions from SERVICE scoped operation contract" in {
        Given("a CML service or operation contract fixture")
        val base = Paths.get(sys.props("user.dir")).toAbsolutePath.normalize()
        val input = base.resolve("src/test/resources/modeler/service-operation-contract.dox")
        val out = base.resolve("target/test-generated/modeler-scala-service-operation-contract")
        delete_recursively(out)
        Files.createDirectories(out.getParent)

        When("Cozy parses or generates the service operation model")
        run_modeler_scala(input, out)

        val generated = out.resolve(
        "target/scala-3.3.7/src_managed/main/scala/domain/DomainComponent.scala"
        )
        val content = Files.readString(generated)
        Then("the operation contract is accepted or rejected according to the specification")
        content should include ("""name = "greeting"""")
        content should include ("""kind = "QUERY"""")
        content should include ("""summary = Some("Returns a greeting message for the supplied name.")""")
        content should include ("""entityName = Some("Person")""")
        content should include ("""visibility = Some("public")""")
        content should include ("""inputType = "GreetingQuery"""")
        content should include ("""inputSummary = Some("Greeting query payload.")""")
        content should include ("""inputDescription = Some("Structured query input accepted by greeting.")""")
        content should include ("""outputType = "GreetingResult"""")
        content should include ("""outputSummary = Some("Greeting result payload.")""")
        content should include ("""outputDescription = Some("Structured result returned by greeting.")""")
        content should include ("""inputValueKind = "QUERY_VALUE"""")
        content should include ("""parameters = Vector(org.goldenport.cncf.operation.CmlOperationField(name = "name", datatype = "name", multiplicity = "1", label = Some("Name"))""")
        content should include ("""resultFields = Vector(org.goldenport.cncf.operation.CmlOperationField(name = "message", datatype = "string", multiplicity = "1", confidentiality = Some("internal"))""")
        content should include ("""Precondition: The caller provides a resolvable greeting target.""")
        content should include ("""Postcondition: A greeting result is returned without mutating state.""")
        content should include ("""Rules:""")
      }

      "modeler-scala keeps SERVICE operation contract self-contained" in {
        Given("a CML service or operation contract fixture")
        val base = Paths.get(sys.props("user.dir")).toAbsolutePath.normalize()
        val input = base.resolve("src/test/resources/modeler/service-operation-overlay-entity.dox")
        val out = base.resolve("target/test-generated/modeler-scala-service-operation-overlay-entity")
        delete_recursively(out)
        Files.createDirectories(out.getParent)

        When("Cozy parses or generates the service operation model")
        run_modeler_scala(input, out)

        val generated = out.resolve(
        "target/scala-3.3.7/src_managed/main/scala/domain/DomainComponent.scala"
        )
        val content = Files.readString(generated)
        Then("the operation contract is accepted or rejected according to the specification")
        content should include ("""name = "changePassword"""")
        content should include ("""entityName = Some("Person")""")
        content should include ("""inputType = "ChangePasswordInput"""")
        content should include ("""outputType = "ChangePasswordResult"""")
        content should include ("""Precondition: The current credential presented by the requester is valid.""")
        content should include ("""Postcondition: The stored credential secret is replaced with newly derived secret material.""")
      }

      "modeler-scala applies SERVICE default ENTITY to service operations" in {
        Given("a CML service or operation contract fixture")
        val base = Paths.get(sys.props("user.dir")).toAbsolutePath.normalize()
        val input = base.resolve("src/test/resources/modeler/service-default-entity-contract.dox")
        val out = base.resolve("target/test-generated/modeler-scala-service-default-entity-contract")
        delete_recursively(out)
        Files.createDirectories(out.getParent)

        When("Cozy parses or generates the service operation model")
        run_modeler_scala(input, out)

        val generated = out.resolve(
        "target/scala-3.3.7/src_managed/main/scala/domain/DomainComponent.scala"
        )
        val content = Files.readString(generated)
        Then("the operation contract is accepted or rejected according to the specification")
        content should include ("""name = "greeting"""")
        content should include ("""entityName = Some("Person")""")
        content should include ("""inputType = "GreetingQuery"""")
        content should include ("""outputType = "GreetingResult"""")
      }

      "modeler-scala preserves multiple SERVICE default ENTITY entries" in {
        Given("a CML service or operation contract fixture")
        val base = Paths.get(sys.props("user.dir")).toAbsolutePath.normalize()
        val input = base.resolve("src/test/resources/modeler/service-multiple-entity-contract.dox")
        val out = base.resolve("target/test-generated/modeler-scala-service-multiple-entity-contract")
        delete_recursively(out)
        Files.createDirectories(out.getParent)

        When("Cozy parses or generates the service operation model")
        run_modeler_scala(input, out)

        val generated = out.resolve(
        "target/scala-3.3.7/src_managed/main/scala/domain/DomainComponent.scala"
        )
        val content = Files.readString(generated)
        Then("the operation contract is accepted or rejected according to the specification")
        content should include ("""name = "synchronizeAccount"""")
        content should include ("""entityName = Some("Person")""")
        content should include ("""entityNames = Vector("Person", "Credential")""")
      }

      "modeler-scala applies SERVICE default ACCESS to service operations" in {
        Given("a CML service or operation contract fixture")
        val base = Paths.get(sys.props("user.dir")).toAbsolutePath.normalize()
        val input = base.resolve("src/test/resources/modeler/service-default-access-contract.dox")
        val out = base.resolve("target/test-generated/modeler-scala-service-default-access-contract")
        delete_recursively(out)
        Files.createDirectories(out.getParent)

        When("Cozy parses or generates the service operation model")
        run_modeler_scala(input, out)

        val generated = out.resolve(
        "target/scala-3.3.7/src_managed/main/scala/domain/DomainComponent.scala"
        )
        val content = Files.readString(generated)
        Then("the operation contract is accepted or rejected according to the specification")
        content should include ("""name = "listAccounts"""")
        content should include ("""access = Some(org.goldenport.cncf.operation.CmlOperationAccess(policy = "manager_only"""")
        content should include ("""operationModel = Some("internal-service")""")
      }

      "modeler-scala generates owner_or_manager authorization against the declared entity" in {
        Given("a CML service or operation contract fixture")
        val base = Paths.get(sys.props("user.dir")).toAbsolutePath.normalize()
        val input = base.resolve("src/test/resources/modeler/service-owner-or-manager-access-contract.dox")
        val out = base.resolve("target/test-generated/modeler-scala-service-owner-or-manager-access-contract")
        delete_recursively(out)
        Files.createDirectories(out.getParent)

        When("Cozy parses or generates the service operation model")
        run_modeler_scala(input, out)

        val generated = out.resolve(
        "target/scala-3.3.7/src_managed/main/scala/domain/DomainComponent.scala"
        )
        val content = Files.readString(generated)
        Then("the operation contract is accepted or rejected according to the specification")
        content should include ("""access = Some(org.goldenport.cncf.operation.CmlOperationAccess(policy = "owner_or_manager", resource = Some("UserAccount"), target = Some("userAccountId")""")
        content should not include ("authorizeSimpleEntityOwnerOrManager(")
        content should not include ("entity.Resource")
        content should not include ("entity_load_c[domain.entity.UserAccount]")
      }

      "modeler-scala carries authorization profile fields into CmlOperationAccess" in {
        Given("a CML service or operation contract fixture")
        val base = Paths.get(sys.props("user.dir")).toAbsolutePath.normalize()
        val input = base.resolve("src/test/resources/modeler/service-authorization-profile-access-contract.dox")
        val out = base.resolve("target/test-generated/modeler-scala-service-authorization-profile-access-contract")
        delete_recursively(out)
        Files.createDirectories(out.getParent)

        When("Cozy parses or generates the service operation model")
        run_modeler_scala(input, out)

        val generated = out.resolve(
        "target/scala-3.3.7/src_managed/main/scala/domain/DomainComponent.scala"
        )
        val content = Files.readString(generated)
        Then("the operation contract is accepted or rejected according to the specification")
        content should include ("""name = "searchOrders"""")
        content should include ("""mode = Some("user-permission")""")
        content should include ("""operationModel = Some("business-service")""")
        content should include ("""entityOperationKind = Some("resource")""")
        content should include ("""entityApplicationDomain = Some("business")""")
        content should include ("""relation = Some("customerId=subject.customerId:read,search/list;\naccountId=subject.accountId:read,search/list")""")
        content should include ("""condition = Some("tenantId=subject.tenantId:read,search/list;postStatus=Published:read,search/list")""")
      }

      "modeler-scala keeps generated aggregate/view/entity services alongside custom SERVICE operations" in {
        Given("a CML service or operation contract fixture")
        val base = Paths.get(sys.props("user.dir")).toAbsolutePath.normalize()
        val input = base.resolve("target/test-generated/modeler-scala-service-entity-coexistence.dox")
        val out = base.resolve("target/test-generated/modeler-scala-service-entity-coexistence-out")
        delete_recursively(out)
        write_file(input,
        """# COMPONENT
          |
          |## StructuredKnowledge
          |
          |### PACKAGE
          |
          |org.simplemodeling.textus.mcprag
          |
          |# SERVICE
          |
          |## Knowledge
          |
          |### DESCRIPTION
          |
          |Knowledge groups source registration and explanation operations.
          |
          |### OPERATION
          |
          |#### registerSource
          |
          |##### TYPE
          |
          |COMMAND
          |
          |##### INPUT
          |###### TYPE
          |RegisterSourceRequest
          |
          |##### OUTPUT
          |###### TYPE
          |KnowledgeFragmentResponse
          |
          |#### explain
          |
          |##### TYPE
          |
          |QUERY
          |
          |##### INPUT
          |###### TYPE
          |ExplainKnowledgeRequest
          |
          |##### OUTPUT
          |###### TYPE
          |KnowledgeFragmentResponse
          |
          |# ENTITY
          |
          |## KnowledgeFragment
          |
          |### DESCRIPTION
          |
          |Primary semantic unit exposed to retrieval and explanation.
          |
          |### ATTRIBUTE
          |
          || name | type | multiplicity |
          ||------|------|--------------|
          || id | entityid | 1 |
          || text | string | 1 |
          || sourceType | string | 1 |
          || sourceId | string | 1 |
          |
          |# COMMAND
          |
          |## RegisterSourceRequest
          |
          |### ATTRIBUTE
          |
          || name | type | multiplicity |
          ||------|------|--------------|
          || sourceType | string | 1 |
          || payload | string | 1 |
          |
          |# QUERY
          |
          |## ExplainKnowledgeRequest
          |
          |### ATTRIBUTE
          |
          || name | type | multiplicity |
          ||------|------|--------------|
          || entityType | string | 1 |
          || id | string | 1 |
          |
          |## KnowledgeFragmentResponse
          |
          |### ATTRIBUTE
          |
          || name | type | multiplicity |
          ||------|------|--------------|
          || fragment | KnowledgeFragment | 1 |
          |
          |""".stripMargin)

        When("Cozy parses or generates the service operation model")
        run_modeler_scala(input, out)

        val generated = out.resolve(
        "target/scala-3.3.7/src_managed/main/scala/org/simplemodeling/textus/mcprag/StructuredKnowledgeComponent.scala"
        )
        val content = Files.readString(generated)
        Then("the operation contract is accepted or rejected according to the specification")
        content should include ("""object KnowledgeService extends ServiceDefinition {""")
        content should include ("""object AggregateService extends ServiceDefinition {""")
        content should include ("""object ViewService extends ServiceDefinition {""")
        content should include ("""object EntityService extends ServiceDefinition {""")
        content should include ("""override def aggregateDefinitions: Vector[org.goldenport.cncf.entity.aggregate.AggregateDefinition] = Vector(""")
        content should include ("""override def viewDefinitions: Vector[org.goldenport.cncf.entity.view.ViewDefinition] = Vector(""")
        content should include ("""KnowledgeService,""")
        content should include ("""AggregateService,""")
        content should include ("""ViewService,""")
        content should include ("""EntityService)""")
      }

      "modeler-scala emits operationDefinitions and value classes from SERVICE scoped inline VALUE contract" in {
        Given("a CML service or operation contract fixture")
        val base = Paths.get(sys.props("user.dir")).toAbsolutePath.normalize()
        val input = base.resolve("src/test/resources/modeler/service-operation-contract-inline-value.dox")
        val out = base.resolve("target/test-generated/modeler-scala-service-operation-inline-value-contract")
        delete_recursively(out)
        Files.createDirectories(out.getParent)

        When("Cozy parses or generates the service operation model")
        run_modeler_scala(input, out)

        val generated = out.resolve(
        "target/scala-3.3.7/src_managed/main/scala/domain/DomainComponent.scala"
        )
        val generatedinput = out.resolve(
        "target/scala-3.3.7/src_managed/main/scala/domain/value/GreetingQuery.scala"
        )
        val generatedoutput = out.resolve(
        "target/scala-3.3.7/src_managed/main/scala/domain/value/GreetingResult.scala"
        )
        Then("the operation contract is accepted or rejected according to the specification")
        withClue(s"generated input value not found: $generatedinput") {
        Files.exists(generatedinput) shouldBe true
      }
        withClue(s"generated output value not found: $generatedoutput") {
        Files.exists(generatedoutput) shouldBe true
      }
        val content = Files.readString(generated)
        content should include ("""name = "greeting"""")
        content should include ("""inputType = "GreetingQuery"""")
        content should include ("""outputType = "GreetingResult"""")
        content should include ("""inputValueKind = "QUERY_VALUE"""")
      }

      "modeler-scala supports typical IMPLEMENTATION directives for entity operations" in {
        Given("a CML service or operation contract fixture")
        val base = Paths.get(sys.props("user.dir")).toAbsolutePath.normalize()
        val input = base.resolve("target/test-generated/modeler-implementation-entity.dox")
        val out = base.resolve("target/test-generated/modeler-implementation-entity-out")
        delete_recursively(out)
        write_file(input,
        """# COMPONENT
          |
          |## Demo
          |
          |### PACKAGE
          |
          |domain
          |
          |# SERVICE
          |
          |## Item
          |
          |### OPERATION
          |
          |#### createItem
          |
          |##### TYPE
          |COMMAND
          |##### IMPLEMENTATION
          |entity-create
          |##### INPUT
          |###### TYPE
          |CreateItem
          |##### OUTPUT
          |###### TYPE
          |OperationResult
          |
          |#### loadItem
          |
          |##### TYPE
          |QUERY
          |##### IMPLEMENTATION
          |entity-load
          |##### INPUT
          |###### TYPE
          |LoadItem
          |##### OUTPUT
          |###### TYPE
          |OperationResult
          |
          |#### searchItem
          |
          |##### TYPE
          |QUERY
          |##### IMPLEMENTATION
          |entity-search
          |##### INPUT
          |###### TYPE
          |SearchItem
          |##### OUTPUT
          |###### TYPE
          |OperationResult
          |
          |#### loadItemAggregate
          |
          |##### TYPE
          |QUERY
          |##### IMPLEMENTATION
          |aggregate-load
          |##### INPUT
          |###### TYPE
          |LoadItem
          |##### OUTPUT
          |###### TYPE
          |OperationResult
          |
          |#### searchItemAggregate
          |
          |##### TYPE
          |QUERY
          |##### IMPLEMENTATION
          |aggregate-search
          |##### INPUT
          |###### TYPE
          |SearchItem
          |##### OUTPUT
          |###### TYPE
          |OperationResult
          |
          |#### loadItemView
          |
          |##### TYPE
          |QUERY
          |##### IMPLEMENTATION
          |view-load
          |##### INPUT
          |###### TYPE
          |LoadItem
          |##### OUTPUT
          |###### TYPE
          |OperationResult
          |
          |#### searchItemView
          |
          |##### TYPE
          |QUERY
          |##### IMPLEMENTATION
          |view-search
          |##### INPUT
          |###### TYPE
          |SearchItem
          |##### OUTPUT
          |###### TYPE
          |OperationResult
          |
          |# ENTITY
          |
          |## Item
          |
          |### ATTRIBUTE
          |
          || name  | type   | multiplicity |
          ||-------+--------+--------------|
          || id    | entityid | 1         |
          || name  | name   | 1            |
          || title | string | 1            |
          |
          |### VIEW
          |
          |- VIEWS :: summary, detail
          |
          |# COMMAND
          |
          |## CreateItem
          |
          |### ATTRIBUTE
          |
          || name  | type   | multiplicity |
          ||-------+--------+--------------|
          || name  | name   | 1            |
          || title | string | 1            |
          |
          |# QUERY
          |
          |## LoadItem
          |
          |### ATTRIBUTE
          |
          || name | type     | multiplicity |
          ||------+----------+--------------|
          || id   | entityid | 1            |
          |
          |## SearchItem
          |
          |### ATTRIBUTE
          |
          || name | type | multiplicity |
          ||------+------|--------------|
          || name | name | 0..1         |
          |
          |""".stripMargin
        )

        When("Cozy parses or generates the service operation model")
        run_modeler_scala(input, out)

        val generated = out.resolve(
        "target/scala-3.3.7/src_managed/main/scala/domain/DemoComponent.scala"
        )
        val generatedview = out.resolve(
        "target/scala-3.3.7/src_managed/main/scala/domain/entity/view/Item.scala"
        )
        val generatedviewsummary = out.resolve(
        "target/scala-3.3.7/src_managed/main/scala/domain/entity/view/summary/Item.scala"
        )
        val generatedviewdetail = out.resolve(
        "target/scala-3.3.7/src_managed/main/scala/domain/entity/view/detail/Item.scala"
        )
        Then("the operation contract is accepted or rejected according to the specification")
        withClue(s"generated view file not found: $generatedview") {
        Files.exists(generatedview) shouldBe true
      }
        withClue(s"generated summary view file not found: $generatedviewsummary") {
        Files.exists(generatedviewsummary) shouldBe true
      }
        withClue(s"generated detail view file not found: $generatedviewdetail") {
        Files.exists(generatedviewdetail) shouldBe true
      }
        val content = Files.readString(generated)
        content should include ("""implementation = Some("entity-create")""")
        content should include ("""implementation = Some("entity-load")""")
        content should include ("""implementation = Some("entity-search")""")
        content should include ("""implementation = Some("aggregate-load")""")
        content should include ("""implementation = Some("aggregate-search")""")
        content should include ("""implementation = Some("view-load")""")
        content should include ("""implementation = Some("view-search")""")
        content should include ("""entity <- exec_pure(domain.entity.create.Item.create(action.request.toRecord))""")
        content should include ("""r <- entity_load[domain.entity.Item](id)""")
        content should include ("""fields <- exec_pure(org.goldenport.cncf.entity.runtime.EntityQueryFieldResolver(core.component, "Item"))""")
        content should include ("""r <- entity_search[domain.entity.Item](domain.entity.query.Item.collectionId, fields.rewrite(Query.fromRecord(action.request.toRecord)))""")
        content should include ("""r <- entity_search[domain.entity.Item](domain.entity.query.Item.collectionId, fields.rewrite(Query.withControls(Query(action.q), action.request.toRecord)))""")
        content should include ("""r <- entity_search[domain.entity.Item](domain.entity.query.Item.collectionId, fields.rewrite(Query.withControls(action.q, action.request.toRecord)))""")
        content should include ("""r <- aggregate_load_option[domain.entity.aggregate.Item](id)""")
        content should include ("""r <- aggregate_search[domain.entity.aggregate.Item](domain.entity.query.Item.collectionId.name, Query.fromRecord(action.request.toRecord))""")
        content should include ("""r <- view_load[domain.entity.view.Item](domain.entity.query.Item.collectionId.name, id)""")
        content should include ("""view_search[domain.entity.view.Item](domain.entity.query.Item.collectionId.name, fields.rewrite(Query.fromRecord(action.request.toRecord)))""")
        content should include ("""action_property_string("view").fold(view_search[domain.entity.view.Item](domain.entity.query.Item.collectionId.name, fields.rewrite(Query.fromRecord(action.request.toRecord))))(viewname => view_search[domain.entity.view.Item](domain.entity.query.Item.collectionId.name, viewname, fields.rewrite(Query.fromRecord(action.request.toRecord))))""")
        content should include ("""action.view.fold(view_search[domain.entity.view.Item](domain.entity.query.Item.collectionId.name, fields.rewrite(Query.withControls(Query(action.q), action.request.toRecord))))(viewname => view_search[domain.entity.view.Item](domain.entity.query.Item.collectionId.name, viewname, fields.rewrite(Query.withControls(Query(action.q), action.request.toRecord))))""")
        content should include ("""action_property_string("view").fold(view_search[domain.entity.view.Item](domain.entity.query.Item.collectionId.name, fields.rewrite(Query.withControls(action.q, action.request.toRecord))))(viewname => view_search[domain.entity.view.Item](domain.entity.query.Item.collectionId.name, viewname, fields.rewrite(Query.withControls(action.q, action.request.toRecord))))""")
        content should include ("""view_load[domain.entity.view.summary.Item](domain.entity.query.Item.collectionId.name, "summary", action.id)""")
        content should include ("""view_search[domain.entity.view.summary.Item](domain.entity.query.Item.collectionId.name, "summary", fields.rewrite(Query.withControls(action.q, action.request.toRecord)))""")
        content should include ("""view_load[domain.entity.view.detail.Item](domain.entity.query.Item.collectionId.name, "detail", action.id)""")
        content should include ("""view_search[domain.entity.view.detail.Item](domain.entity.query.Item.collectionId.name, "detail", fields.rewrite(Query.withControls(action.q, action.request.toRecord)))""")
        content should include ("""viewFields = Map("detail" -> Vector("id", "name", "title"), "summary" -> Vector("id", "name", "title"))""")
        content should not include ("""entity.view.create.Item""")
      }

      "modeler-scala supports IMPLEMENTATION blocking-task for command operations" in {
        Given("a CML service or operation contract fixture")
        val base = Paths.get(sys.props("user.dir")).toAbsolutePath.normalize()
        val input = base.resolve("target/test-generated/modeler-implementation-blocking-task.dox")
        val out = base.resolve("target/test-generated/modeler-implementation-blocking-task-out")
        delete_recursively(out)
        write_file(input,
        """# COMPONENT
          |
          |## Demo
          |
          |### PACKAGE
          |
          |domain
          |
          |# SERVICE
          |
          |## Item
          |
          |### OPERATION
          |
          |#### createItem
          |
          |##### TYPE
          |COMMAND
          |##### IMPLEMENTATION
          |blocking-task
          |##### INPUT
          |###### TYPE
          |CreateItem
          |##### OUTPUT
          |###### TYPE
          |OperationResult
          |
          |# COMMAND
          |
          |## CreateItem
          |
          |### ATTRIBUTE
          |
          || name | type | multiplicity |
          ||------+------|--------------|
          || name | name | 1            |
          |
          |""".stripMargin
        )

        When("Cozy parses or generates the service operation model")
        run_modeler_scala(input, out)

        val generated = out.resolve(
        "target/scala-3.3.7/src_managed/main/scala/domain/DemoComponent.scala"
        )
        val content = Files.readString(generated)
        Then("the operation contract is accepted or rejected according to the specification")
        content should include ("""implementation = Some("blocking-task")""")
        content should include ("""Thread.sleep(250L)""")
      }

      "modeler-scala emits event IMPLEMENTATION directives into operation definitions" in {
        Given("a CML service or operation contract fixture")
        val base = Paths.get(sys.props("user.dir")).toAbsolutePath.normalize()
        val input = base.resolve("target/test-generated/modeler-implementation-event.dox")
        val out = base.resolve("target/test-generated/modeler-implementation-event-out")
        delete_recursively(out)
        write_file(input,
        """# COMPONENT
          |
          |## EventDriven
          |
          |### PACKAGE
          |
          |domain
          |
          |# SERVICE
          |
          |## Event
          |
          |### OPERATION
          |
          |#### emitEvent
          |
          |##### TYPE
          |COMMAND
          |##### IMPLEMENTATION
          |event-emit
          |##### INPUT
          |###### TYPE
          |EmitEvent
          |##### OUTPUT
          |###### TYPE
          |OperationResult
          |
          |#### recordEffect
          |
          |##### TYPE
          |COMMAND
          |##### IMPLEMENTATION
          |event-effect-record
          |##### INPUT
          |###### TYPE
          |RecordEffect
          |##### OUTPUT
          |###### TYPE
          |OperationResult
          |
          |#### loadEffect
          |
          |##### TYPE
          |QUERY
          |##### IMPLEMENTATION
          |event-effect-load
          |##### INPUT
          |###### TYPE
          |LoadEffect
          |##### OUTPUT
          |###### TYPE
          |OperationResult
          |
          |# COMMAND
          |
          |## EmitEvent
          |
          |### ATTRIBUTE
          |
          || name | type | multiplicity |
          ||------+------|--------------|
          || name | name | 1            |
          |
          |## RecordEffect
          |
          |### ATTRIBUTE
          |
          || name | type | multiplicity |
          ||------+------|--------------|
          || name | name | 1            |
          |
          |# QUERY
          |
          |## LoadEffect
          |
          |# ENTITY
          |
          |## Item
          |
          |### ATTRIBUTE
          |
          || name | type     | multiplicity |
          ||------+----------+--------------|
          || id   | entityid | 1            |
          || name | name     | 1            |
          |
          |""".stripMargin
        )

        When("Cozy parses or generates the service operation model")
        run_modeler_scala(input, out)

        val generated = out.resolve(
        "target/scala-3.3.7/src_managed/main/scala/domain/EventDrivenComponent.scala"
        )
        val content = Files.readString(generated)
        Then("the operation contract is accepted or rejected according to the specification")
        content should include ("""implementation = Some("event-emit")""")
        content should include ("""implementation = Some("event-effect-record")""")
        content should include ("""implementation = Some("event-effect-load")""")
      }

      "modeler-scala rejects operation kind/input value mismatch" in {
        Given("a CML service or operation contract fixture")
        val base = Paths.get(sys.props("user.dir")).toAbsolutePath.normalize()
        val input = base.resolve("src/test/resources/modeler/operation-grammar-invalid-kind-mismatch.dox")
        val out = base.resolve("target/test-generated/modeler-scala-operation-invalid-kind")
        delete_recursively(out)

        When("Cozy parses or generates the service operation model")
        val output = run_modeler_scala(input, out)
        Then("the operation contract is accepted or rejected according to the specification")
        withClue(s"unexpected output: $output") {
        output should include ("TYPE=COMMAND cannot use query-value input")
      }
      }

    }
  }
}
