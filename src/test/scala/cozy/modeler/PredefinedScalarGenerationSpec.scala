package cozy.modeler

import java.nio.file.{Files, Path, Paths}
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Jul. 15, 2026
 * @version Jul. 16, 2026
 * @author  ASAMI, Tomoharu
 */
final class PredefinedScalarGenerationSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  "CML predefined scalar generation" should {
    "project semantic types and default value ranges into entity code and schema" in {
      Given("an entity using localized descriptive roles and low-ambiguity account scalars")
      val base = Paths.get(sys.props("user.dir")).toAbsolutePath.normalize()
      val input = base.resolve("src/test/resources/modeler/predefined-scalars.cml")
      val output = base.resolve("target/test-generated/predefined-scalars-spec")
      _delete_recursively(output)

      When("Cozy generates the Scala 3.3.8 source family")
      cozy.Cozy.main(Array("modeler-scala", input.toString, "--save", output.toString))

      val generated = output.resolve(
        "target/scala-3.3.8/src_managed/main/scala/domain/entity/Profile.scala"
      )
      val content = Files.readString(generated)

      Then("the entity uses canonical runtime values instead of nominal string wrappers")
      content should include("email: EmailAddress")
      content should include("phone: Option[PhoneNumber]")
      content should include("ipAddress: Option[IpAddress]")
      content should include("locale: Option[Locale]")
      content should include("timeZone: Option[TimeZone]")

      And("default domain ranges are projected to Web schema and generated construction validation")
      content should include("WebValidationHints(minLength = Some(1), maxLength = Some(256))")
      content should include("WebValidationHints(minLength = Some(1), maxLength = Some(512))")
      content should include("WebValidationHints(minLength = Some(1), maxLength = Some(2048))")
      content should include("WebValidationHints(minLength = Some(1), maxLength = Some(8192))")
      content should include("WebValidationHints(minLength = Some(3), maxLength = Some(254))")
      content should include("WebValidationHints(minLength = Some(2), maxLength = Some(45))")
      content should include("_text_constraint_values(nameAttributes.title)")
      content should include("_text_constraint_values(descriptiveAttributes.headline)")
      content should include("_text_constraint_values(descriptiveAttributes.summary)")
      content should include("_text_constraint_values(descriptiveAttributes.description)")
      content should include("email must be valid email values")
      content should include("phone must be valid phone values")
      content should not include "unsupported constraint: format:"
    }
  }

  private def _delete_recursively(p: Path): Unit =
    if (Files.exists(p)) {
      val stream = Files.walk(p)
      try {
        stream.sorted(java.util.Comparator.reverseOrder()).forEach(Files.delete(_))
      } finally {
        stream.close()
      }
    }
}
