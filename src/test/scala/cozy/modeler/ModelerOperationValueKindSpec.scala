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
class ModelerOperationValueKindSpec extends AnyWordSpec with Matchers with GivenWhenThen with ModelerSpecSupport {
  "CML top-level operation input Value" should {
    "normalize explicit command and query input kinds" in {
      Given("top-level Values with explicit COMMAND and QUERY input-kind properties")
      val base = Paths.get(sys.props("user.dir")).toAbsolutePath.normalize()
      val input = base.resolve("target/test-generated/operation-value-kind/valid.cml")
      val out = base.resolve("target/test-generated/operation-value-kind/valid-out")
      delete_recursively(out)
      write_file(input, _valid_contract)

      When("Cozy generates the component operation definitions")
      cozy.Cozy.main(Array("modeler-scala", input.toString, "--save", out.toString))

      Then("the AST properties select stable command/query metadata")
      val generated = out.resolve("target/scala-3.3.7/src_managed/main/scala/domain/DomainComponent.scala")
      val content = Files.readString(generated)
      content should include ("""name = "createGreeting"""")
      content should include ("""inputType = "CreateGreeting"""")
      content should include ("""inputValueKind = "COMMAND_VALUE"""")
      content should include ("""name = "searchGreetings"""")
      content should include ("""inputType = "SearchGreetings"""")
      content should include ("""inputValueKind = "QUERY_VALUE"""")
    }

    "reject a referenced top-level Value without input-kind" in {
      Given("an operation whose top-level input Value omits input-kind")
      val output = _run_invalid("missing", _invalid_contract(None, "COMMAND"))

      Then("Cozy identifies the input Value and required property")
      output should include ("Operation 'submitGreeting' INPUT Value 'GreetingInput' requires input-kind=COMMAND|QUERY")
    }

    "reject an unsupported input-kind" in {
      Given("a top-level input Value with an unsupported input-kind")
      val output = _run_invalid("unsupported", _invalid_contract(Some("EVENT"), "COMMAND"))

      Then("Cozy reports the unsupported property value")
      output should include ("Top-level VALUE 'GreetingInput' input-kind must be COMMAND or QUERY: EVENT")
    }

    "reject operation and input Value kind mismatch" in {
      Given("a command operation referencing a query input Value")
      val output = _run_invalid("mismatch", _invalid_contract(Some("QUERY"), "COMMAND"))

      Then("Cozy reports the normalized kind mismatch")
      output should include ("Operation 'submitGreeting' TYPE=COMMAND cannot use query-value input")
    }
  }

  private def _run_invalid(name: String, contract: String): String = {
    val base = Paths.get(sys.props("user.dir")).toAbsolutePath.normalize()
    val input = base.resolve(s"target/test-generated/operation-value-kind/$name.cml")
    val out = base.resolve(s"target/test-generated/operation-value-kind/$name-out")
    delete_recursively(out)
    write_file(input, contract)
    When("Cozy validates the operation contract")
    run_modeler_scala(input, out)
  }

  private val _valid_contract =
    """# COMPONENT
      |
      |## Domain
      |
      |### PACKAGE
      |
      |domain
      |
      |# VALUE
      |
      |## CreateGreeting
      |- input-kind :: COMMAND
      |
      |### ATTRIBUTE
      |
      || name | type | multiplicity |
      ||------+------|--------------|
      || name | name | 1            |
      |
      |## SearchGreetings
      |- input-kind :: QUERY
      |
      |### ATTRIBUTE
      |
      || name | type | multiplicity |
      ||------+------|--------------|
      || name | name | ?            |
      |
      |# SERVICE
      |
      |## Greeting
      |
      |### OPERATION
      |
      |#### createGreeting
      |
      |##### TYPE
      |COMMAND
      |##### INPUT
      |###### TYPE
      |CreateGreeting
      |##### OUTPUT
      |###### TYPE
      |OperationResult
      |
      |#### searchGreetings
      |
      |##### TYPE
      |QUERY
      |##### INPUT
      |###### TYPE
      |SearchGreetings
      |##### OUTPUT
      |###### TYPE
      |OperationResult
      |""".stripMargin

  private def _invalid_contract(inputkind: Option[String], operationkind: String): String = {
    val property = inputkind.map(x => s"- input-kind :: $x\n").getOrElse("")
    s"""# COMPONENT
       |
       |## Domain
       |
       |### PACKAGE
       |
       |domain
       |
       |# VALUE
       |
       |## GreetingInput
       |$property
       |### ATTRIBUTE
       |
       || name | type | multiplicity |
       ||------+------|--------------|
       || name | name | 1            |
       |
       |# SERVICE
       |
       |## Greeting
       |
       |### OPERATION
       |
       |#### submitGreeting
       |
       |##### TYPE
       |$operationkind
       |##### INPUT
       |###### TYPE
       |GreetingInput
       |##### OUTPUT
       |###### TYPE
       |OperationResult
       |""".stripMargin
  }
}
