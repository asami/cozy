package cozy.modeler

import java.nio.file.Files
import java.nio.file.Paths
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Jul. 15, 2026
 * @version Jul. 15, 2026
 * @author  ASAMI, Tomoharu
 */
class ModelerLocalOperationValueSpec extends AnyWordSpec with Matchers with GivenWhenThen with ModelerSpecSupport {
  "CML operation-local Values" should {
    "generate local Value contracts" which {
      "generate anonymous input and output Values with deterministic names and metadata" in {
        Given("a query operation whose INPUT and OUTPUT contain direct ATTRIBUTE schemas")
        val (input, out) = _prepare("anonymous", _anonymous_contract)

        When("Cozy normalizes and generates the operation-local Values")
        cozy.Cozy.main(Array("modeler-scala", input.toString, "--save", out.toString))

        Then("the generated names, value kind, parameters, and result fields use one normalized contract")
        val component = Files.readString(out.resolve("target/scala-3.3.7/src_managed/main/scala/domain/DomainComponent.scala"))
        component should include ("""inputType = "SearchNotificationsQuery"""")
        component should include ("""outputType = "SearchNotificationsResult"""")
        component should include ("""inputValueKind = "QUERY_VALUE"""")
        component should include ("""name = "text"""")
        component should include ("""name = "total"""")
        Files.exists(out.resolve("target/scala-3.3.7/src_managed/main/scala/domain/value/SearchNotificationsQuery.scala")) shouldBe true
        Files.exists(out.resolve("target/scala-3.3.7/src_managed/main/scala/domain/value/SearchNotificationsResult.scala")) shouldBe true
      }

      "generate explicitly named input and output Values" in {
        Given("a command operation whose local Value names are declared canonically")
        val (input, out) = _prepare("named", _named_contract)

        When("Cozy normalizes and generates the operation-local Values")
        cozy.Cozy.main(Array("modeler-scala", input.toString, "--save", out.toString))

        Then("the explicit local names are used consistently")
        val component = Files.readString(out.resolve("target/scala-3.3.7/src_managed/main/scala/domain/DomainComponent.scala"))
        component should include ("""inputType = "NotificationRegistration"""")
        component should include ("""outputType = "NotificationReceipt"""")
        component should include ("""inputValueKind = "COMMAND_VALUE"""")
        component should include ("""parameters = Vector(org.goldenport.cncf.operation.CmlOperationField(name = "id"""")
        component should include ("""resultFields = Vector(org.goldenport.cncf.operation.CmlOperationField(name = "id"""")
        Files.exists(out.resolve("target/scala-3.3.7/src_managed/main/scala/domain/value/NotificationRegistration.scala")) shouldBe true
        Files.exists(out.resolve("target/scala-3.3.7/src_managed/main/scala/domain/value/NotificationReceipt.scala")) shouldBe true
      }
    }

    "reject reference and local-definition conflicts" which {
      "reject INPUT TYPE combined with a local input definition" in {
        Given("an INPUT that declares both TYPE and ATTRIBUTE")
        val (input, out) = _prepare("input-type-conflict", _input_type_conflict_contract)

        When("Cozy validates the operation-local Value contract")
        val output = run_modeler_scala(input, out)

        Then("the conflicting structural definitions are rejected")
        output should include ("Operation searchNotifications INPUT TYPE cannot be combined with a local VALUE definition")
      }

      "reject OUTPUT TYPE combined with a local output definition" in {
        Given("an OUTPUT that declares both TYPE and ATTRIBUTE")
        val (input, out) = _prepare("output-type-conflict", _output_type_conflict_contract)

        When("Cozy validates the operation-local Value contract")
        val output = run_modeler_scala(input, out)

        Then("the conflicting structural definitions are rejected")
        output should include ("Operation searchNotifications OUTPUT TYPE cannot be combined with a local VALUE definition")
      }

      "reject a direct input reference combined with a local input definition" in {
        Given("an operation whose direct input property repeats its local Value name")
        val (input, out) = _prepare("direct-input-conflict", _direct_input_conflict_contract)

        When("Cozy validates the operation-local Value contract")
        val output = run_modeler_scala(input, out)

        Then("the direct reference does not hide the duplicate local declaration")
        output should include ("Operation registerNotification INPUT direct type reference NotificationRegistration cannot be combined with a local VALUE definition")
        output should include ("Service Notification. [")
      }

      "reject a direct output reference combined with a local output definition" in {
        Given("an operation whose direct output property repeats its local Result name")
        val (input, out) = _prepare("direct-output-conflict", _direct_output_conflict_contract)

        When("Cozy validates the operation-local Value contract")
        val output = run_modeler_scala(input, out)

        Then("the direct reference does not hide the duplicate local declaration")
        output should include ("Operation registerNotification OUTPUT direct type reference NotificationReceipt cannot be combined with a local VALUE definition")
        output should include ("Service Notification. [")
      }
    }

    "validate local input kind ownership" which {
      "reject input-kind on a local input Value" in {
        Given("an operation-local input that redundantly declares input-kind")
        val (input, out) = _prepare("local-input-kind", _local_input_kind_contract)

        When("Cozy validates the operation-local Value contract")
        val output = run_modeler_scala(input, out)

        Then("the enclosing operation remains the only local input-kind authority")
        output should include ("Operation 'searchNotifications' local INPUT must not declare input-kind")
      }

      "reject a legacy local input parent that conflicts with the operation kind" in {
        Given("a COMMAND operation whose nested input Value extends QueryAction")
        val (input, out) = _prepare("legacy-kind-mismatch", _legacy_kind_mismatch_contract)

        When("Cozy validates the operation-local Value contract")
        val output = run_modeler_scala(input, out)

        Then("the compatibility metadata is validated rather than ignored")
        output should include ("Operation 'registerNotification' TYPE=COMMAND cannot use query-value input")
      }
    }

    "protect projected Value identities" which {
      "reject duplicate operation-local Value names" in {
        Given("two operations that define the same local Value name")
        val (input, out) = _prepare("duplicate-local-name", _duplicate_local_name_contract)

        When("Cozy validates the operation-local Value contract")
        val output = run_modeler_scala(input, out)

        Then("Cozy reports both declaration owners for the colliding identity")
        output should include ("Operation-local VALUE 'NotificationRequest' is defined more than once")
        output should include ("service 'Notification', operation 'registerNotification' INPUT")
        output should include ("service 'Notification', operation 'retryNotification' INPUT")
      }

      "reject a local Value name that collides with a top-level Value" in {
        Given("an operation-local input with the same name as a reusable top-level Value")
        val (input, out) = _prepare("top-level-name-conflict", _top_level_name_conflict_contract)

        When("Cozy validates the operation-local Value contract")
        val output = run_modeler_scala(input, out)

        Then("the local and reusable identities report both declaration contexts")
        output should include ("Operation-local VALUE 'NotificationRegistration' conflicts with a top-level VALUE definition")
        output should include ("service 'Notification', operation 'registerNotification' INPUT")
        output should include ("top-level VALUE 'NotificationRegistration'")
      }
    }
  }

  private def _prepare(name: String, contract: String): (java.nio.file.Path, java.nio.file.Path) = {
    val base = Paths.get(sys.props("user.dir")).toAbsolutePath.normalize()
    val input = base.resolve(s"target/test-generated/local-operation-value/$name.cml")
    val out = base.resolve(s"target/test-generated/local-operation-value/$name-out")
    delete_recursively(out)
    write_file(input, contract)
    input -> out
  }

  private val _anonymous_contract =
    """# COMPONENT
      |
      |## Domain
      |
      |### PACKAGE
      |domain
      |
      |# SERVICE
      |
      |## Notification
      |
      |### OPERATION
      |
      |#### searchNotifications
      |
      |##### TYPE
      |QUERY
      |
      |##### INPUT
      |
      |###### ATTRIBUTE
      || name | type   | multiplicity |
      ||------+--------+--------------|
      || text | string | ?            |
      |
      |##### OUTPUT
      |
      |###### ATTRIBUTE
      || name  | type | multiplicity |
      ||-------+------+--------------|
      || total | int  | 1            |
      |""".stripMargin

  private val _named_contract =
    """# COMPONENT
      |
      |## Domain
      |
      |### PACKAGE
      |domain
      |
      |# SERVICE
      |
      |## Notification
      |
      |### OPERATION
      |
      |#### registerNotification
      |
      |##### TYPE
      |COMMAND
      |
      |##### INPUT
      |
      |###### VALUE
      |NotificationRegistration
      |
      |###### ATTRIBUTE
      || name  | type   | multiplicity |
      ||-------+--------+--------------|
      || id    | string | 1            |
      |
      |##### OUTPUT
      |
      |###### VALUE
      |NotificationReceipt
      |
      |###### ATTRIBUTE
      || name      | type   | multiplicity |
      ||-----------+--------+--------------|
      || id        | string | 1            |
      |""".stripMargin

  private val _input_type_conflict_contract =
    _anonymous_contract.replace(
      "##### INPUT\n\n###### ATTRIBUTE",
      "##### INPUT\n\n###### TYPE\nExistingQuery\n\n###### ATTRIBUTE"
    )

  private val _local_input_kind_contract =
    _anonymous_contract.replace(
      "##### INPUT\n\n###### ATTRIBUTE",
      "##### INPUT\n- input-kind :: QUERY\n\n###### ATTRIBUTE"
    )

  private val _output_type_conflict_contract =
    _anonymous_contract.replace(
      "##### OUTPUT\n\n###### ATTRIBUTE",
      "##### OUTPUT\n\n###### TYPE\nExistingResult\n\n###### ATTRIBUTE"
    )

  private val _direct_input_conflict_contract =
    _named_contract.replace(
      "#### registerNotification\n\n##### TYPE",
      "#### registerNotification\n\n- input :: NotificationRegistration\n\n##### TYPE"
    )

  private val _direct_output_conflict_contract =
    _named_contract.replace(
      "#### registerNotification\n\n##### TYPE",
      "#### registerNotification\n\n- output :: NotificationReceipt\n\n##### TYPE"
    )

  private val _legacy_kind_mismatch_contract =
    """# COMPONENT
      |
      |## Domain
      |
      |### PACKAGE
      |domain
      |
      |# SERVICE
      |
      |## Notification
      |
      |### OPERATION
      |
      |#### registerNotification
      |
      |##### TYPE
      |COMMAND
      |
      |##### INPUT
      |
      |###### VALUE
      |
      |####### NotificationRequest
      |
      |######## EXTENDS
      |QueryAction
      |
      |######## ATTRIBUTE
      || name  | type   | multiplicity |
      ||-------+--------+--------------|
      || title | string | 1            |
      |
      |##### OUTPUT
      |###### TYPE
      |OperationResult
      |""".stripMargin

  private val _duplicate_local_name_contract =
    """# COMPONENT
      |
      |## Domain
      |
      |### PACKAGE
      |domain
      |
      |# SERVICE
      |
      |## Notification
      |
      |### OPERATION
      |
      |#### registerNotification
      |##### TYPE
      |COMMAND
      |##### INPUT
      |###### VALUE
      |NotificationRequest
      |###### ATTRIBUTE
      || name  | type   | multiplicity |
      ||-------+--------+--------------|
      || title | string | 1            |
      |##### OUTPUT
      |###### TYPE
      |OperationResult
      |
      |#### retryNotification
      |##### TYPE
      |COMMAND
      |##### INPUT
      |###### VALUE
      |NotificationRequest
      |###### ATTRIBUTE
      || name | type   | multiplicity |
      ||------+--------+--------------|
      || id   | string | 1            |
      |##### OUTPUT
      |###### TYPE
      |OperationResult
      |""".stripMargin

  private val _top_level_name_conflict_contract =
    _named_contract.replace(
      "# SERVICE",
      """# VALUE
        |
        |## NotificationRegistration
        |- input-kind :: COMMAND
        |
        |### ATTRIBUTE
        || name | type   | multiplicity |
        ||------+--------+--------------|
        || id   | string | 1            |
        |
        |# SERVICE""".stripMargin
    )
}
