package cozy

import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Aug. 29, 2026
 * @version Aug. 29, 2026
 * @author  ASAMI, Tomoharu
 */
final class CozyPdfCommandContractSpec
    extends AnyWordSpec
    with Matchers
    with GivenWhenThen {
  "Cozy PDF option validation" should {
    "reject media profiles before resolving the input document" in {
      Given("a nonexistent PDF input and both media-profile spellings")
      val arguments = Vector(
        Array("pdf", "missing-input.dox", "--profile", "business"),
        Array("pdf", "missing-input.dox", "--profile=business")
      )

      When("Cozy receives each public PDF invocation")
      val messages = arguments.map(args => _failure(args).getMessage)

      Then("both spellings produce the frozen profile namespace diagnostic")
      messages shouldBe Vector.fill(arguments.size)(
        "cozy pdf does not accept --profile. Use --latex-format standard|business; Cozy Media --profile is a separate namespace."
      )
    }

    "reject unsupported latex formats before resolving the input document" in {
      Given("a nonexistent PDF input and separate or equals-form unsupported formats")
      val arguments = Vector(
        Array("pdf", "missing-input.dox", "--latex-format", "poster"),
        Array("pdf", "missing-input.dox", "--latex-format=poster")
      )

      When("Cozy receives each public PDF invocation")
      val messages = arguments.map(args => _failure(args).getMessage)

      Then("both spellings produce the frozen unsupported-format diagnostic")
      messages shouldBe Vector.fill(arguments.size)(
        "Unsupported Cozy PDF --latex-format 'poster'. Supported canonical formats: standard, business."
      )
    }

    "reject a missing latex format value deterministically" in {
      Given("a nonexistent PDF input and trailing or immediately-following-option latex-format forms without a value")
      val arguments = Vector(
        Array("pdf", "missing-input.dox", "--latex-format"),
        Array("pdf", "missing-input.dox", "--latex-format", "--output", "result.pdf")
      )

      When("Cozy receives each public PDF invocation")
      val failures = arguments.map(_failure)

      Then("both missing-value forms report the frozen diagnostic before input resolution")
      failures.map(_.getMessage) shouldBe Vector.fill(arguments.size)(
        "Missing value for Cozy PDF --latex-format. Supported canonical formats: standard, business."
      )
    }

    "accept canonical and compatibility latex formats after normalization" in {
      Given("canonical formats and the three preserved SmartDox aliases")
      val values = Vector(
        "standard",
        "business",
        " DEFAULT ",
        " Business-Document ",
        "\tbusiness-doc\t"
      )

      When("Cozy validates each effective PDF option")
      val outcomes = values.map(value => scala.util.Try(
        CozyOperationConfig._validate_pdf_options(List("--latex-format", value))
      ).isSuccess)

      Then("every normalized value is accepted without a media-profile mapping")
      outcomes shouldBe Vector.fill(values.size)(true)
    }
  }

  private def _failure(args: Array[String]): RuntimeException =
    intercept[RuntimeException](Cozy.main(args))
}
