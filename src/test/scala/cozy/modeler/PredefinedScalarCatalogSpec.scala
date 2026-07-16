package cozy.modeler

import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Jul. 15, 2026
 * @version Jul. 16, 2026
 * @author  ASAMI, Tomoharu
 */
final class PredefinedScalarCatalogSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  "CML predefined scalar catalog" should {
    "classify every accepted semantic scalar as localized or intentionally nonlocalized" in {
      Given("the complete accepted semantic scalar catalog")
      val localizednames = Vector(
        "label", "title", "headline", "brief", "summary", "lead",
        "abstract", "remarks", "description", "text"
      )
      val nonlocalizednames = Vector(
        "name", "identifier", "token", "url", "uri", "urn", "locale",
        "timezone", "ip-address", "email", "phone"
      )

      When("entries are partitioned by their semantic locality")
      val localized = PredefinedScalarCatalog.entries.filter(_.localized).map(_.name)
      val nonlocalized = PredefinedScalarCatalog.entries.filterNot(_.localized).map(_.name)

      Then("user-visible descriptive and narrative roles are locale-aware")
      localized shouldBe localizednames

      And("identity, protocol, locator, and technical scalar roles are nonlocalized")
      nonlocalized shouldBe nonlocalizednames

      And("no accepted catalog entry remains outside the locality decision")
      (localized ++ nonlocalized).toSet shouldBe PredefinedScalarCatalog.entries.map(_.name).toSet
    }

    "define one default per-value length range for every semantic text role" in {
      Given("the accepted nonlocalized name and locale-aware descriptive text roles")
      val expected = Vector(
        "name" -> (1, 256),
        "label" -> (1, 256),
        "title" -> (1, 256),
        "headline" -> (1, 512),
        "brief" -> (1, 512),
        "summary" -> (1, 2048),
        "lead" -> (1, 2048),
        "abstract" -> (1, 2048),
        "remarks" -> (1, 2048),
        "description" -> (1, 8192),
        "text" -> (1, 8192)
      )

      When("their catalog ranges are read")
      val ranges = expected.map { case (name, _) =>
        val entry = PredefinedScalarCatalog.get(name).get
        name -> (entry.minlength.get, entry.maxlength.get)
      }

      Then("each present scalar or locale entry inherits its role-specific default")
      ranges shouldBe expected
    }

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

    "define plain narrative text as a locale-aware bounded scalar" in {
      Given("the canonical CML text name")

      When("its predefined scalar entry is resolved")
      val entry = PredefinedScalarCatalog.get("text").get

      Then("plain and multi-locale input share the I18nText contract")
      entry.runtimeclassname shouldBe Some("org.goldenport.datatype.I18nText")
      entry.localized shouldBe true
      entry.minlength shouldBe Some(1)
      entry.maxlength shouldBe Some(8192)
      entry.attributegroup shouldBe None
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
