package cozy.modeler

import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.goldenport.kaleidox.{Config => KaleidoxConfig, Model => KaleidoxModel}

/*
 * @since   Jul. 15, 2026
 * @version Jul. 15, 2026
 * @author  ASAMI, Tomoharu
 */
final class CmlModelInspectionSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  "CML model inspection" should {
    "inventory string-only Values and Datatypes from the normalized CML model" in {
      Given("CML with string-backed Value and Datatype declarations")
      val source =
        """# VALUE
          |
          |## DisplayName
          |
          |### ATTRIBUTE
          |
          || name  | type   | multiplicity |
          ||-------+--------+--------------|
          || value | string | 1            |
          |
          |# DATATYPE
          |
          |## MessageCode
          |
          |### ATTRIBUTE
          |
          || name  | type   | multiplicity |
          ||-------+--------+--------------|
          || value | string | 1            |
          |""".stripMargin
      val model = KaleidoxModel.parseWitoutLocation(KaleidoxConfig.log.debug, source)

      When("the normalized AST model is inspected")
      val result = CmlModelInspection.from(model)

      Then("both declarations retain their semantic declaration kinds")
      result.stringscalars.map(x => x.kind.name -> x.name) should contain theSameElementsAs Vector(
        "value" -> "DisplayName",
        "datatype" -> "MessageCode"
      )
    }

    "expose normalized entity and Value attributes for CML lint" in {
      Given("CML with entity and Value raw string attributes")
      val source =
        """# ENTITY
          |
          |## Account
          |
          |### ATTRIBUTE
          |
          || name  | type   | multiplicity |
          ||-------+--------+--------------|
          || title | string | 1            |
          |
          |# VALUE
          |
          |## Label
          |
          |### ATTRIBUTE
          |
          || name  | type   | multiplicity |
          ||-------+--------+--------------|
          || text  | string | 1            |
          |""".stripMargin
      val model = KaleidoxModel.parseWitoutLocation(KaleidoxConfig.log.debug, source)

      When("the normalized AST model is inspected")
      val attributes = CmlModelInspection.from(model).attributes

      Then("lint consumers receive both declaration contexts without reparsing text")
      attributes.map(x => (x.kind.name, x.owner, x.name, x.isRawString, x.line > 0)) should contain theSameElementsAs Vector(
        ("entity", "Account", "title", true, true),
        ("value", "Label", "text", true, true)
      )
    }
  }
}
