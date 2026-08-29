package cozy

import java.io.{ByteArrayOutputStream, PrintStream}
import java.nio.charset.StandardCharsets

import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Aug. 29, 2026
 * @version Aug. 29, 2026
 * @author  ASAMI, Tomoharu
 */
final class CozyPdfCommandHelpSpec
    extends AnyWordSpec
    with Matchers
    with GivenWhenThen {
  "Cozy PDF command help" should {
    "render one dedicated help text for every PDF help spelling" in {
      Given("the three supported PDF help spellings")
      val spellings = Vector(
        Array("pdf", "--help"),
        Array("pdf", "-h"),
        Array("help", "pdf")
      )

      When("Cozy renders each PDF help request through main")
      val outputs = spellings.map(_capture)

      Then("every spelling produces the same dedicated PDF help")
      outputs.distinct should have size 1
      outputs.head should include("cozy pdf <input>")
      outputs.head should include("--output <file>")
      outputs.head should include("--renderer <name>")
      outputs.head should include("--latex-format <format>")
      outputs.head should include("standard or business")
      outputs.head should include(
        "This is a SmartDox PDF renderer format, not Cozy Media --profile."
      )
      outputs.head should not include("cozy pdf <input> --profile")
    }

    "keep the existing top-level help surface available" in {
      Given("the bare Cozy help spellings")
      val spellings = Vector(
        Array("--help"),
        Array("-h"),
        Array("help")
      )

      When("Cozy renders each top-level help request through main")
      val outputs = spellings.map(_capture)

      Then("the established top-level help remains available")
      outputs.distinct should have size 1
      outputs.head should include("Usage:")
      outputs.head should include("cozy [command] [options]")
      outputs.head should include("pdf <input>")
      outputs.head.linesIterator.count(_.toLowerCase.contains("pdf")) shouldBe 1
    }
  }

  private def _capture(args: Array[String]): String = {
    val output = new ByteArrayOutputStream()
    Console.withOut(
      new PrintStream(output, true, StandardCharsets.UTF_8.name())
    ) {
      Cozy.main(args)
    }
    output.toString(StandardCharsets.UTF_8.name())
  }
}
