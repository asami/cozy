package cozy.modeler

import java.nio.file.Files
import java.nio.file.Paths
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Jul. 15, 2026
 * @version Jul. 16, 2026
 * @author  ASAMI, Tomoharu
 */
class ModelerOperationCompatibilitySpec extends AnyWordSpec with Matchers with GivenWhenThen with ModelerSpecSupport {
  "CML operation compatibility forms" should {
    "normalize legacy top-level COMMAND and QUERY inputs" in {
      Given("a component whose service operations reference legacy top-level COMMAND and QUERY definitions")
      val base = Paths.get(sys.props("user.dir")).toAbsolutePath.normalize()
      val input = base.resolve("target/test-generated/operation-compatibility/top-level.cml")
      val out = base.resolve("target/test-generated/operation-compatibility/top-level-out")
      val canonicalinput = base.resolve("target/test-generated/operation-compatibility/top-level-canonical.cml")
      val canonicalout = base.resolve("target/test-generated/operation-compatibility/top-level-canonical-out")
      delete_recursively(out)
      delete_recursively(canonicalout)
      write_file(input, _top_level_contract)
      write_file(canonicalinput, _canonical_top_level_contract)

      When("Cozy generates the component operation metadata")
      run_modeler_scala(input, out)
      run_modeler_scala(canonicalinput, canonicalout)

      Then("the legacy definitions use the same command and query Value metadata as canonical inputs")
      val component = Files.readString(_component_path(out))
      component should include ("""name = "createGreeting"""")
      component should include ("""inputType = "CreateGreeting"""")
      component should include ("""inputValueKind = "COMMAND_VALUE"""")
      component should include ("""name = "searchGreetings"""")
      component should include ("""inputType = "SearchGreetings"""")
      component should include ("""inputValueKind = "QUERY_VALUE"""")

      And("compatibility syntax produces the same generated component contract as canonical Values")
      component shouldBe Files.readString(_component_path(canonicalout))
    }

    "normalize legacy service-scoped named inline Values" in {
      Given("a service operation with named inline input and output Values")
      val base = Paths.get(sys.props("user.dir")).toAbsolutePath.normalize()
      val input = base.resolve("src/test/resources/modeler/service-operation-contract-inline-value.dox")
      val out = base.resolve("target/test-generated/operation-compatibility/inline-out")
      delete_recursively(out)

      When("Cozy generates the component operation metadata")
      run_modeler_scala(input, out)

      Then("the inline definitions retain their names, fields, and query Value kind")
      val component = Files.readString(_component_path(out))
      component should include ("""name = "greeting"""")
      component should include ("""inputType = "GreetingQuery"""")
      component should include ("""outputType = "GreetingResult"""")
      component should include ("""inputValueKind = "QUERY_VALUE"""")
      Files.exists(out.resolve("target/scala-3.3.8/src_managed/main/scala/domain/value/GreetingQuery.scala")) shouldBe true
      Files.exists(out.resolve("target/scala-3.3.8/src_managed/main/scala/domain/value/GreetingResult.scala")) shouldBe true
    }
  }

  private def _component_path(out: java.nio.file.Path): java.nio.file.Path =
    out.resolve("target/scala-3.3.8/src_managed/main/scala/domain/DomainComponent.scala")

  private val _top_level_contract =
    """# COMPONENT
      |
      |## Domain
      |
      |### PACKAGE
      |
      |domain
      |
      |# COMMAND
      |
      |## CreateGreeting
      |
      |### ATTRIBUTE
      |
      || name | type | multiplicity |
      ||------+------|--------------|
      || name | name | 1            |
      |
      |# QUERY
      |
      |## SearchGreetings
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

  private val _canonical_top_level_contract =
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
}
