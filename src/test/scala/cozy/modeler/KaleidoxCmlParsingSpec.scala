package cozy.modeler

import java.io.ByteArrayOutputStream
import java.io.PrintStream
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Paths
import scala.collection.JavaConverters._
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.goldenport.kaleidox.{Config => KaleidoxConfig, Model => KaleidoxModel}
import org.goldenport.record.v2.{CFormat, CMaxLength, CMinLength, CRegex}

/*
 * @since   Jun. 23, 2026
 * @version Jul. 15, 2026
 * @author  ASAMI, Tomoharu
 */
class KaleidoxCmlParsingSpec extends AnyWordSpec with Matchers with GivenWhenThen with ModelerSpecSupport {
  "Kaleidox CML parsing" should {
    "parse CML grammar into normalized models" which {
      "kaleidox parses POWERTYPE section and ignores narrative subsection" in {
        Given("a CML fixture that exercises parser grammar")
        val base = Paths.get(sys.props("user.dir")).toAbsolutePath.normalize()
        val input = base.resolve("src/test/resources/modeler/powertype-literate.dox")
        When("Kaleidox parses the source model")
        val model = KaleidoxModel.load(KaleidoxConfig.default.withoutLocation, input.toFile)
        val powertype = model.takePowertypeModel
        val divisions = model.divisions.map(_.name).mkString(",")
        val errors = model.errors.map(_.toString).mkString("|")
        Then("the parsed model exposes the expected normalized semantics")
        withClue(s"CountryCode missing; actual keys=${powertype.classes.keys.mkString(",")}, divisions=$divisions, errors=$errors") {
        powertype.classes should contain key "CountryCode"
      }
        withClue(s"AddressType missing; actual keys=${powertype.classes.keys.mkString(",")}, divisions=$divisions, errors=$errors") {
        powertype.classes should contain key "AddressType"
      }
        powertype.classes.contains("Overview") shouldBe false
        powertype.classes("CountryCode").packageName shouldBe "domain.value"
        powertype.classes("AddressType").packageName shouldBe "domain.value"
        powertype.classes("CountryCode").kinds.map(_.name) shouldBe Vector("JP", "US")
        powertype.classes("CountryCode").kinds.head.value shouldBe Some(81)
        powertype.classes("CountryCode").kinds(1).value shouldBe Some(2)
        powertype.classes("AddressType").kinds.head.value shouldBe Some(1)
        powertype.classes("AddressType").kinds(1).value shouldBe Some(2)
      }

      "kaleidox accepts STATE-MACHINE top-level alias" in {
        Given("a CML fixture that exercises parser grammar")
        val base = Paths.get(sys.props("user.dir")).toAbsolutePath.normalize()
        val input = base.resolve("src/test/resources/modeler/statemachine-division-alias.dox")
        When("Kaleidox parses the source model")
        val model = KaleidoxModel.load(KaleidoxConfig.default.withoutLocation, input.toFile)
        val sm = model.takeStateMachineModel
        val divisions = model.divisions.map(_.name).mkString(",")
        val errors = model.errors.map(_.toString).mkString("|")
        val lifecycle = sm.getClass("lifecycle").getOrElse {
        fail(s"state machine lifecycle is missing; actual keys=${sm.classes.keys.mkString(",")}, divisions=$divisions, errors=$errors")
      }
        Then("the parsed model exposes the expected normalized semantics")
        lifecycle.states.exists(_.name == "Draft") shouldBe true
        lifecycle.states.exists(_.name == "Published") shouldBe true
        lifecycle.states.find(_.name == "Draft").exists(_.value == 10) shouldBe true
        lifecycle.states.find(_.name == "Published").exists(_.value == 2) shouldBe true
      }

      "kaleidox parses POWERTYPE from .cml and ignores reserved/free narrative sections" in {
        Given("a CML fixture that exercises parser grammar")
        val base = Paths.get(sys.props("user.dir")).toAbsolutePath.normalize()
        val input = base.resolve("src/test/resources/modeler/powertype-literate.cml")
        When("Kaleidox parses the source model")
        val model = KaleidoxModel.load(KaleidoxConfig.default.withoutLocation, input.toFile)
        val powertype = model.takePowertypeModel
        Then("the parsed model exposes the expected normalized semantics")
        powertype.classes should contain key "CountryCode"
        powertype.classes should contain key "AddressType"
        powertype.classes("CountryCode").kinds.head.value shouldBe Some(81)
        powertype.classes("CountryCode").kinds(1).value shouldBe Some(2)
        powertype.classes.contains("SUMMARY") shouldBe false
        powertype.classes.contains("Overview") shouldBe false
      }

      "kaleidox parses STATEMACHINE from .cml and ignores reserved/free narrative sections" in {
        Given("a CML fixture that exercises parser grammar")
        val base = Paths.get(sys.props("user.dir")).toAbsolutePath.normalize()
        val input = base.resolve("src/test/resources/modeler/statemachine-literate.cml")
        When("Kaleidox parses the source model")
        val model = KaleidoxModel.load(KaleidoxConfig.default.withoutLocation, input.toFile)
        val sm = model.takeStateMachineModel
        val lifecycle = sm.getClass("lifecycle").getOrElse {
        fail(s"state machine lifecycle is missing; actual keys=${sm.classes.keys.mkString(",")}")
      }
        Then("the parsed model exposes the expected normalized semantics")
        lifecycle.states.exists(_.name == "Draft") shouldBe true
        lifecycle.states.exists(_.name == "Published") shouldBe true
        lifecycle.states.find(_.name == "Draft").exists(_.value == 10) shouldBe true
        lifecycle.states.find(_.name == "Published").exists(_.value == 2) shouldBe true
        sm.getClass("SUMMARY") shouldBe empty
        sm.getClass("Overview") shouldBe empty
      }

      "kaleidox normalizes attribute constraint metadata to record constraints" in {
        Given("a CML fixture that exercises parser grammar")
        val base = Paths.get(sys.props("user.dir")).toAbsolutePath.normalize()
        val input = base.resolve("src/test/resources/modeler/constraint-metadata.dox")
        When("Kaleidox parses the source model")
        val model = KaleidoxModel.load(KaleidoxConfig.default.withoutLocation, input.toFile)
        val schema = model.takeEntityModel.get("CountryCode").getOrElse {
        fail("Entity CountryCode is missing")
      }.schema
        val column = schema.columns.find(_.name == "value").getOrElse {
        fail("Column value is missing")
      }
        Then("the parsed model exposes the expected normalized semantics")
        column.constraints.exists(_.isInstanceOf[CMinLength]) shouldBe true
        column.constraints.exists(_.isInstanceOf[CMaxLength]) shouldBe true
        column.constraints.exists(_.isInstanceOf[CRegex]) shouldBe true
        column.constraints.exists(_.isInstanceOf[CFormat]) shouldBe true
      }

      "kaleidox carries CML attribute labels into entity schema columns" in {
        Given("a CML fixture that exercises parser grammar")
        val base = Paths.get(sys.props("user.dir")).toAbsolutePath.normalize()
        val input = base.resolve("src/test/resources/modeler/db-column-options.dox")
        When("Kaleidox parses the source model")
        val model = KaleidoxModel.load(KaleidoxConfig.default.withoutLocation, input.toFile)
        val schema = model.takeEntityModel.get("Person").getOrElse {
        fail("Entity Person is missing")
      }.schema
        val displayname = schema.columns.find(_.name == "displayName").getOrElse {
        fail("Column displayName is missing")
      }
        val body = schema.columns.find(_.name == "body").getOrElse {
        fail("Column body is missing")
      }

        Then("the parsed model exposes the expected normalized semantics")
        displayname.i18nLabel.map(_.c) shouldBe Some("Display Name")
        body.i18nLabel.map(_.c) shouldBe Some("Body")
      }

      "modeler-scala emits WebValidationHints from CML constraint metadata" in {
        Given("a CML fixture that exercises parser grammar")
        val base = Paths.get(sys.props("user.dir")).toAbsolutePath.normalize()
        val input = base.resolve("src/test/resources/modeler/constraint-metadata.dox")
        val out = base.resolve("target/test-generated/modeler-scala-constraint-metadata")
        delete_recursively(out)
        Files.createDirectories(out.getParent)

        When("Kaleidox parses the source model")
        cozy.Cozy.main(Array("modeler-scala", input.toString, "--save", out.toString.toString))

        val generated = out.resolve(
        "target/scala-3.3.8/src_managed/main/scala/domain/entity/CountryCode.scala"
        )
        val content = Files.readString(generated)
        Then("the parsed model exposes the expected normalized semantics")
        content should include (
        """validation = org.goldenport.schema.WebValidationHints(minLength = Some(2), maxLength = Some(2), pattern = Some("^[A-Z]{2}$"))"""
        )
      }

      "kaleidox accepts extended format values for CFormat constraints" in {
        Given("a CML fixture that exercises parser grammar")
        val base = Paths.get(sys.props("user.dir")).toAbsolutePath.normalize()
        val input = base.resolve("src/test/resources/modeler/constraint-format-extended.dox")
        When("Kaleidox parses the source model")
        val model = KaleidoxModel.load(KaleidoxConfig.default.withoutLocation, input.toFile)
        val schema = model.takeEntityModel.get("ContactProfile").getOrElse {
        fail("Entity ContactProfile is missing")
      }.schema
        val createdat = schema.columns.find(_.name == "created_at").getOrElse {
        fail("Column created_at is missing")
      }
        val phonenumber = schema.columns.find(_.name == "phone_number").getOrElse {
        fail("Column phone_number is missing")
      }
        val createdatformats = createdat.constraints.collect { case CFormat(f) => f.toLowerCase }
        val phoneformats = phonenumber.constraints.collect { case CFormat(f) => f.toLowerCase }
        Then("the parsed model exposes the expected normalized semantics")
        createdatformats should contain ("date-time")
        phoneformats should contain ("phone")
      }

      "kaleidox parses Event metadata in Entity Event section" in {
        Given("a CML fixture that exercises parser grammar")
        val base = Paths.get(sys.props("user.dir")).toAbsolutePath.normalize()
        val input = base.resolve("src/test/resources/modeler/event-metadata.dox")
        When("Kaleidox parses the source model")
        val model = KaleidoxModel.load(KaleidoxConfig.default.withoutLocation, input.toFile)
        val entity = model.takeEntityModel.get("Person").getOrElse {
        fail("Entity Person is missing")
      }
        val events = entity.schemaClass.events
        Then("the parsed model exposes the expected normalized semantics")
        events.size shouldBe 3

        val created = events.find(_.name == "person.created").getOrElse {
        fail("person.created event is missing")
      }
        created.category shouldBe "ActionEvent"
        created.kind shouldBe Some("created")
        created.selectors.get("source") shouldBe Some("crm")
        created.actionName shouldBe Some("person.sync")
        created.priority shouldBe 0

        val updated = events.find(_.name == "person.updated").getOrElse {
        fail("person.updated event is missing")
      }
        updated.category shouldBe "ActionEvent"
        updated.kind shouldBe Some("updated")
        updated.selectors.get("source") shouldBe Some("crm")
        updated.actionName shouldBe Some("person.sync")
        updated.priority shouldBe 1

        val shipped = events.find(_.name == "order.shipped").getOrElse {
        fail("order.shipped event is missing")
      }
        shipped.category shouldBe "NonActionEvent"
        shipped.kind shouldBe Some("shipped")
        shipped.selectors shouldBe empty
        shipped.actionName shouldBe empty
        shipped.priority shouldBe 0
      }

      "kaleidox parses Event metadata in YAML section body" in {
        Given("a CML fixture that exercises parser grammar")
        val base = Paths.get(sys.props("user.dir")).toAbsolutePath.normalize()
        val input = base.resolve("src/test/resources/modeler/event-metadata-yaml-body.dox")
        When("Kaleidox parses the source model")
        val model = KaleidoxModel.load(KaleidoxConfig.default.withoutLocation, input.toFile)
        val entity = model.takeEntityModel.get("Person").getOrElse {
        fail("Entity Person is missing")
      }
        val created = entity.schemaClass.events.find(_.name == "person.created").getOrElse {
        fail("person.created event is missing")
      }
        Then("the parsed model exposes the expected normalized semantics")
        created.category shouldBe "ActionEvent"
        created.kind shouldBe Some("created")
        created.selectors.get("source") shouldBe Some("crm")
        created.actionName shouldBe Some("person.sync")
        created.priority shouldBe 2
      }

      "kaleidox parses Aggregate/View metadata in Entity section" in {
        Given("a CML fixture that exercises parser grammar")
        val base = Paths.get(sys.props("user.dir")).toAbsolutePath.normalize()
        val input = base.resolve("src/test/resources/modeler/aggregate-view-metadata.dox")
        When("Kaleidox parses the source model")
        val model = KaleidoxModel.load(KaleidoxConfig.default.withoutLocation, input.toFile)
        val entity = model.takeEntityModel.get("Person").getOrElse {
        fail("Entity Person is missing")
      }

        val aggregate = entity.aggregate.getOrElse {
        fail("Aggregate is missing")
      }
        Then("the parsed model exposes the expected normalized semantics")
        aggregate.creates should not be empty
        aggregate.creates.head.name shouldBe "createPerson"
        aggregate.creates.head.events should contain ("person.created")
        aggregate.creates.head.initialState shouldBe Some("Active")
        aggregate.creates.head.implementation shouldBe Some("pattern:create")
        aggregate.commands should not be empty
        val renameperson = aggregate.commands.find(_.name == "renamePerson").getOrElse {
        fail("renamePerson command is missing")
      }
        renameperson.events should contain ("person.renamed")
        renameperson.implementation shouldBe Some("pattern:copy-update")
        aggregate.state.exists(_.name == "name") shouldBe true
        aggregate.invariants.exists(_.name == "nameRequired") shouldBe true

        val view = entity.view.getOrElse {
        fail("View is missing")
      }
        view.attributes.exists(_.name == "id") shouldBe true
        view.attributes.exists(_.name == "name") shouldBe true
        view.queries.exists(_.name == "searchPublished") shouldBe true
        view.viewNames shouldBe Vector("summary", "detail")
        view.sourceEvents should contain ("person.created")
        view.rebuildable shouldBe Some(true)
      }

      "kaleidox parses COMPONENT/SUBSYSTEM grammar" in {
        Given("a CML fixture that exercises parser grammar")
        val base = Paths.get(sys.props("user.dir")).toAbsolutePath.normalize()
        val input = base.resolve("src/test/resources/modeler/component-subsystem-grammar.dox")
        When("Kaleidox parses the source model")
        val model = KaleidoxModel.load(KaleidoxConfig.default.withoutLocation, input.toFile)
        val cs = model.takeComponentSubsystemModel

        Then("the parsed model exposes the expected normalized semantics")
        cs.components should not be empty
        val component = cs.components.find(_.name == "person").getOrElse {
        fail("component 'person' is missing")
      }
        component.coordinates.exists(_.asString == "org.simplemodeling.car:person-service:0.1.0") shouldBe true
        component.componentlets should contain ("person_core")
        component.extensionPoints should contain ("transport")
        component.extensionBindings.get("transport") shouldBe Some("grpc")

        val subsystem = cs.subsystems.find(_.name == "identity").getOrElse {
        fail("subsystem 'identity' is missing")
      }
        subsystem.components.exists(_.asString == "org.simplemodeling.car:person-service:0.1.0") shouldBe true
        subsystem.extensionBindings.get("transport") shouldBe Some("http")
        subsystem.config.get("profile") shouldBe Some("prod")
      }

      "kaleidox parses component package override" in {
        Given("a CML fixture that exercises parser grammar")
        val base = Paths.get(sys.props("user.dir")).toAbsolutePath.normalize()
        val input = base.resolve("src/test/resources/modeler/component-subsystem-component-package.dox")
        When("Kaleidox parses the source model")
        val model = KaleidoxModel.load(KaleidoxConfig.default.withoutLocation, input.toFile)
        val cs = model.takeComponentSubsystemModel

        val component = cs.components.find(_.name == "domain").getOrElse {
        fail("component 'domain' is missing")
      }
        Then("the parsed model exposes the expected normalized semantics")
        component.packageName shouldBe Some("textus.user.account")
      }

    }
  }
}
