package cozy.modeler

import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Jul. 15, 2026
 * @version Jul. 15, 2026
 * @author  ASAMI, Tomoharu
 */
final class PredefinedScalarCatalogSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  "CML predefined scalar catalog" should {
    "bind localized descriptive roles to their canonical runtime families" in {
      Given("the accepted title and descriptive attribute roles")
      val roles = Vector("title", "headline", "summary", "description")

      When("their catalog entries are resolved")
      val entries = roles.map(x => x -> PredefinedScalarCatalog.get(x).get)

      Then("each role has a locale-aware runtime type and attribute group")
      entries.map { case (name, entry) => name -> (entry.runtimeclassname, entry.localized, entry.attributegroup) } shouldBe Vector(
        "title" -> (Some("org.goldenport.datatype.I18nTitle"), true, Some("NameAttributes")),
        "headline" -> (Some("org.goldenport.datatype.I18nBrief"), true, Some("DescriptiveAttributes")),
        "summary" -> (Some("org.goldenport.datatype.I18nSummary"), true, Some("DescriptiveAttributes")),
        "description" -> (Some("org.goldenport.datatype.I18nDescription"), true, Some("DescriptiveAttributes"))
      )
    }

    "define low-ambiguity account scalar aliases and value ranges" in {
      Given("email, phone, locale, timezone, and IP address CML names")
      val names = Vector("email", "e164", "locale", "time-zone", "ip_address")

      When("the names are normalized through the catalog")
      val entries = names.map(x => PredefinedScalarCatalog.get(x).get)

      Then("every name resolves to one canonical scalar contract")
      entries.map(_.name) shouldBe Vector("email", "phone", "locale", "timezone", "ip-address")
      entries.forall(x => x.minlength.isDefined && x.maxlength.isDefined) shouldBe true
      entries.last.runtimeclassname shouldBe Some("org.goldenport.datatype.IpAddress")
    }

    "suggest predefined types for nominal driver wrapper names" in {
      Given("driver-specific datatype names that only add a prefix")
      val names = Vector("UserAccountTitle", "UserAccountEmailAddress", "UserAccountPhoneNumber", "UserAccountIpAddress")

      When("the names are classified for migration diagnostics")
      val suggestions = names.map(x => PredefinedScalarCatalog.suggestion(x).map(_.name))

      Then("the corresponding predefined scalar names are selected")
      suggestions shouldBe Vector(Some("title"), Some("email"), Some("phone"), Some("ip-address"))
    }
  }
}
