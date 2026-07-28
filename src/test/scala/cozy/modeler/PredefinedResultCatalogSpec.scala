package cozy.modeler

import java.nio.file.{Files, Path, Paths}
import scala.collection.JavaConverters._
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Jul. 15, 2026
 * @version Jul. 28, 2026
 * @author  ASAMI, Tomoharu
 */
final class PredefinedResultCatalogSpec extends AnyWordSpec with Matchers with GivenWhenThen with ModelerSpecSupport {
  "The selected CNCF predefined Result catalog" should {
    "provide output validation and result field metadata" in {
      Given("a selected CNCF runtime descriptor and an operation returning IntResult")
      val base = Paths.get(sys.props("user.dir")).toAbsolutePath.normalize()
      val input = base.resolve("target/test-generated/predefined-int-result.cml")
      val out = base.resolve("target/test-generated/predefined-int-result-out")
      delete_recursively(out)
      write_file(input, _service_contract("IntResult"))

      When("Cozy generates the component with the selected runtime catalog")
      val output = run_modeler_scala(input, out)
      val paths = Files.walk(out)
      val component = try {
        paths.iterator().asScala.find { path =>
          Files.isRegularFile(path) && path.getFileName.toString == "DomainComponent.scala"
        }.map(Files.readString).getOrElse(fail("DomainComponent.scala was not generated"))
      } finally {
        paths.close()
      }

      Then("IntResult is accepted and its value field is projected")
      output should not include "is not defined"
      component should include ("outputType = \"IntResult\"")
      component should include ("resultFields = Vector(org.goldenport.cncf.operation.CmlOperationField(name = \"value\", datatype = \"int\", multiplicity = \"1\"")
    }

    "reject raw scalar operation outputs" in {
      Given("an operation that declares int directly as its output")
      val base = Paths.get(sys.props("user.dir")).toAbsolutePath.normalize()
      val input = base.resolve("target/test-generated/raw-scalar-result.cml")
      val out = base.resolve("target/test-generated/raw-scalar-result-out")
      delete_recursively(out)
      write_file(input, _service_contract("int"))

      When("Cozy validates the operation output")
      val output = run_modeler_scala(input, out)

      Then("the scalar is rejected at the operation boundary")
      output should include ("OUTPUT TYPE 'int' is a raw scalar")
    }

    "provide UnitResult without payload fields" in {
      Given("an operation returning the selected runtime UnitResult")
      val base = Paths.get(sys.props("user.dir")).toAbsolutePath.normalize()
      val input = base.resolve("target/test-generated/predefined-unit-result.cml")
      val out = base.resolve("target/test-generated/predefined-unit-result-out")
      delete_recursively(out)
      write_file(input, _service_contract("UnitResult"))

      When("Cozy generates the operation metadata")
      val output = run_modeler_scala(input, out)

      Then("the predefined no-payload Result is accepted")
      output should not include "is not defined"
    }

    "reject a Result name absent from the selected catalog" in {
      Given("an operation returning an unsupported StringResult")
      val base = Paths.get(sys.props("user.dir")).toAbsolutePath.normalize()
      val input = base.resolve("target/test-generated/unknown-predefined-result.cml")
      val out = base.resolve("target/test-generated/unknown-predefined-result-out")
      delete_recursively(out)
      write_file(input, _service_contract("StringResult"))

      When("Cozy validates against the selected CNCF catalog")
      val output = run_modeler_scala(input, out)

      Then("Cozy does not infer an open-ended predefined Result")
      output should include ("OUTPUT TYPE 'StringResult' is not defined")
    }

    "reject a descriptor for a different selected runtime" in {
      Given("a CNCF 0.5.2-SNAPSHOT descriptor selected as CNCF 0.5.1-SNAPSHOT")

      When("Cozy loads the selected runtime catalog")
      val exception = intercept[RuntimeException] {
        PredefinedResultCatalog.loadRuntimeDescriptor(test_cncf_runtime_descriptor, "0.5.1-SNAPSHOT")
      }

      Then("generation fails instead of using metadata from another runtime")
      exception.getMessage should include ("does not match selected runtime 0.5.1-SNAPSHOT")
    }

    "reject an unsupported catalog schema" in {
      Given("a selected runtime descriptor with an unknown predefined Result schema")
      val descriptor = Files.createTempFile("cozy-predefined-result-schema-", ".yaml")
      val source = Files.readString(test_cncf_runtime_descriptor)
      Files.writeString(descriptor, source.replace("cncf.predefined-result.v1", "cncf.predefined-result.v2"))

      try {
        When("Cozy loads the selected runtime catalog")
        val exception = intercept[RuntimeException] {
          PredefinedResultCatalog.loadRuntimeDescriptor(descriptor, "0.5.2-SNAPSHOT")
        }

        Then("generation fails before interpreting an unsupported schema")
        exception.getMessage should include ("Unsupported CNCF predefined Result catalog schema")
      } finally {
        Files.deleteIfExists(descriptor)
      }
    }
  }

  private def _service_contract(outputtype: String): String =
    s"""# COMPONENT
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
       |$outputtype
       |
       |# QUERY
       |
       |## GreetingQuery
       |""".stripMargin
}
