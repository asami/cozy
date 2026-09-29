package cozy.modeler

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path, Paths}
import scala.collection.JavaConverters._
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Sep. 28, 2026
 * @version Sep. 28, 2026
 * @author  ASAMI, Tomoharu
 */
final class LibraryGenerationSpec
    extends AnyWordSpec
    with Matchers
    with GivenWhenThen
    with ModelerSpecSupport {
  private val _base = Paths.get(sys.props("user.dir")).toAbsolutePath.normalize()

  "Library generation" should {
    "generate the SimpleModeling library CML fixtures without CNCF runtime output" in {
      Given("the copied Address and user-profile library CML inputs")
      val out = _base.resolve("target/test-generated/library-generation")
      delete_recursively(out)
      Files.createDirectories(out.getParent)
      val inputs = Vector("address.cml", "user-profile-values.cml").map(
        _base.resolve("src/test/resources/modeler/library").resolve
      )

      When("the native value modeler receives the explicit library target")
      inputs.foreach(_generate(_, out))

      Then("all value declarations are emitted as pure model source with model metadata")
      val files = _scala_files(out)
      files.map(_.getFileName.toString) should contain allElementsOf Vector(
        "Address.scala", "CountryCode.scala", "PostalCode.scala", "Region.scala",
        "Locality.scala", "SubLocality.scala", "StreetAddress.scala", "ExtendedAddress.scala",
        "IdentityPresentation.scala", "PersonalProfile.scala", "OrganizationSupport.scala"
      )
      files.foreach { file =>
        val text = Files.readString(file, StandardCharsets.UTF_8)
        text should include("package org.simplemodeling.model.value")
        text should not include "org.goldenport.cncf"
        text should not include "LogicalActionCompiler"
        text should not include "Component"
      }
      files.map(_.getFileName.toString) should not contain "CompositeStateMachineActionProgramBootstrap.scala"
      Files.isRegularFile(out.resolve("target/cozy/model-metadata.json")) shouldBe true
      Files.exists(out.resolve("target/cozy/generation-provenance.json")) shouldBe false
    }

    "accept pure datatype, powertype, and plain state-machine categories" in {
      Given("existing pure fixtures and an inline datatype")
      val out = _base.resolve("target/test-generated/library-generation-pure-types")
      delete_recursively(out)
      Files.createDirectories(out)
      val datatype = out.resolve("CurrencyCode.cml")
      Files.writeString(
        datatype,
        """# DATATYPE
          |
          |## CurrencyCode
          |
          |### ATTRIBUTE
          |
          || name  | type   | multiplicity |
          ||-------|--------|--------------|
          || value | string | 1            |
          |""".stripMargin,
        StandardCharsets.UTF_8
      )

      When("each is generated using the library target")
      _generate(datatype, out)
      _generate(_base.resolve("src/test/resources/modeler/powertype-literate.cml"), out)
      _generate(_base.resolve("src/test/resources/modeler/statemachine-division-alias.dox"), out)

      Then("all pure declaration families remain available")
      val names = _scala_files(out).map(_.getFileName.toString)
      names should contain("CurrencyCode.scala")
      names should contain("CountryCode.scala")
      names should contain("lifecycle.scala")
    }

    "reject a runtime target before it can silently lose executable behavior" in {
      Given("a library request that carries a CNCF runtime version and a composite state-machine model")
      val args = List(
        "--generation-target", "library",
        "--cozy-generator-version", org.simplemodeling.cozy.BuildInfo.version,
        "--cncf-version", "0.5.2"
      )

      When("the target is admitted")
      val error = intercept[Exception] {
        ModelGenerationTarget.requireInvocation("modeler-scala-value", args)
      }
      val runtimeout = _base.resolve("target/test-generated/library-generation-runtime-rejection")
      delete_recursively(runtimeout)
      val runtimeerror = intercept[Exception] {
        _generate(
          _base.resolve("src/test/resources/modeler/order-payment-shipment-action-program.cml"),
          runtimeout
        )
      }

      Then("the contradiction has a deterministic library diagnostic")
      error.getMessage should include("LIBRARY_GENERATION_RUNTIME_CONTRADICTION")
      runtimeerror.getMessage should include("LIBRARY_GENERATION_UNSUPPORTED_RUNTIME")
      runtimeerror.getMessage should include("composite-state-machine/action")
    }

    "reject a missing or conflicting explicit target instead of falling back to CNCF" in {
      Given("malformed target options")
      val missing = List("--generation-target")
      val conflicting = List(
        "--generation-target", "cncf",
        "--generation-target", "library"
      )

      When("command intake selects the generation target")
      val missingerror = intercept[Exception] {
        ModelGenerationTarget.requireInvocation("modeler-scala-value", missing)
      }
      val conflict = intercept[Exception] {
        ModelGenerationTarget.requireInvocation("modeler-scala-value", conflicting)
      }

      Then("both malformed requests stop before generation")
      missingerror.getMessage should include("LIBRARY_GENERATION_INVALID_TARGET")
      conflict.getMessage should include("LIBRARY_GENERATION_INVALID_TARGET")
    }
  }

  private def _generate(input: Path, out: Path): Unit =
    cozy.Cozy.main(Array(
      "modeler-scala-value", input.toString,
      "--save", out.toString,
      "--generation-target", "library",
      "--cozy-generator-version", org.simplemodeling.cozy.BuildInfo.version
    ))

  private def _scala_files(out: Path): Vector[Path] = {
    val stream = Files.walk(out)
    try stream.iterator().asScala.filter(path => path.toString.endsWith(".scala")).toVector
    finally stream.close()
  }
}
