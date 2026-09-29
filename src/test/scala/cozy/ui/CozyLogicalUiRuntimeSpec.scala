package cozy.ui

import java.time.Instant

import cozy.ui.CozyLogicalUiRuntime._
import cozy.ui.CozyLogicalUiSelection._
import cozy.ui.CozyLogicalUiVocabulary._
import cozyruntime.CozyLogicalUiRuntimeFixtures
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Sep. 29, 2026
 * @version Sep. 29, 2026
 * @author  ASAMI, Tomoharu
 */
final class CozyLogicalUiRuntimeSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  "Cozy Logical UI runtime admission" should {
    "admit all supported typed values, empty collections, scoped ID reuse, visible ordering, and structural equality" in {
      Given("List and Detail models with every Content display value, empty vectors, and IDs reused only across declared scopes")
      val typedlist = Model(
        metadata = CozyLogicalUiRuntimeFixtures.metadata,
        screen = ResourceList(
          id = "typed-list",
          title = "Typed values",
          items = Vector(
            ListItem(
              id = "resource-a",
              fields = Vector(
                Field("shared-field", "Text", ContentRole, TextValue("")),
                Field("number-field", "Number", ContentRole, NumberValue(BigDecimal("7.50"))),
                Field("boolean-field", "Boolean", ContentRole, BooleanValue(true)),
                Field("timestamp-field", "Timestamp", ContentRole, TimestampValue(Instant.parse("2026-09-29T00:00:00Z"))),
                Field("missing-field", "Missing", ContentRole, MissingValue)
              ),
              actions = Vector(Action("shared-action", "Refresh", enabled = true))
            ),
            ListItem(
              id = "resource-b",
              fields = Vector(Field("shared-field", "Text", ContentRole, TextValue("second"))),
              actions = Vector(Action("shared-action", "Refresh", enabled = false))
            )
          ),
          detailTarget = DetailTarget("typed-detail"),
          actions = Vector(Action("shared-action", "Refresh", enabled = true))
        )
      )
      val emptylist = Model(
        CozyLogicalUiRuntimeFixtures.metadata,
        ResourceList("empty-list", "Empty", Vector.empty, DetailTarget("empty-detail"), Vector.empty)
      )
      val detail = Model(
        CozyLogicalUiRuntimeFixtures.metadata,
        ResourceDetail(
          "typed-detail",
          "resource-a",
          "Typed detail",
          Vector(Section("shared-section", None, Vector(Field("shared-field", "Text", ContentRole, MissingValue)))),
          Vector(Action("shared-action", "Refresh", enabled = true))
        )
      )

      When("the public validator admits each complete structural model")
      val typedresult = CozyLogicalUiRuntimeValidation.validate(typedlist)
      val emptyresult = CozyLogicalUiRuntimeValidation.validate(emptylist)
      val detailresult = CozyLogicalUiRuntimeValidation.validate(detail)

      Then("it returns the identical models and preserves collection and field order without normalizing scoped opaque IDs")
      typedresult shouldBe Right(typedlist)
      emptyresult shouldBe Right(emptylist)
      detailresult shouldBe Right(detail)
      typedlist.screen.asInstanceOf[ResourceList].items.map(_.id) shouldBe Vector("resource-a", "resource-b")
      typedlist.screen.asInstanceOf[ResourceList].items.head.fields.map(_.id) shouldBe Vector(
        "shared-field", "number-field", "boolean-field", "timestamp-field", "missing-field"
      )
    }

    "reject incompatible contract descriptors and missing provenance with exact stable diagnostics" in {
      Given("one valid model plus unknown schema, version, assurance, authority, driver, and absent-provenance metadata variants")
      val base = CozyLogicalUiRuntimeFixtures.listModel
      val contracts = Vector(
        minimumContract.copy(schema = "cozy.logical-ui-runtime.v2"),
        minimumContract.copy(version = 2),
        minimumContract.copy(assurance = "actual-android-mock"),
        minimumContract.copy(semanticAuthoritySchema = "cozy.logical-ui-semantics.v2"),
        minimumContract.copy(driver = minimumContract.driver.copy(path = "docs/phase/other.md")),
        null.asInstanceOf[ContractDescriptor]
      )
      val contractmodels = contracts.map(contract => base.copy(metadata = base.metadata.copy(contract = contract)))
      val provenancemodels = Vector(
        base.copy(metadata = base.metadata.copy(provenance = null)),
        base.copy(metadata = base.metadata.copy(provenance = SourceProvenance("", "source", "revision"))),
        base.copy(metadata = base.metadata.copy(provenance = SourceProvenance("producer", " source", "revision")))
      )

      When("metadata is admitted before the screen is traversed")
      val contracterrors = contractmodels.map(model => _left(CozyLogicalUiRuntimeValidation.validate(model)))
      val provenanceerrors = provenancemodels.map(model => _left(CozyLogicalUiRuntimeValidation.validate(model)))

      Then("all descriptor variants fail at metadata.contract and provenance variants fail at their exact provenance paths")
      contracterrors.map(_.code) shouldBe Vector.fill(6)("LUI74_CONTRACT_INCOMPATIBLE")
      contracterrors.map(_.path) shouldBe Vector.fill(6)("model.metadata.contract")
      provenanceerrors.map(_.code) shouldBe Vector.fill(3)("LUI74_PROVENANCE_INVALID")
      provenanceerrors.map(_.path) shouldBe Vector(
        "model.metadata.provenance",
        "model.metadata.provenance.producerId",
        "model.metadata.provenance.sourceId"
      )
    }

    "preserve nonblank padded display text while rejecting whitespace-only display text and padded provenance" in {
      Given("valid List and Detail values with padded screen title, field label, optional section title, TitleRole text, and strict provenance variants")
      val base = CozyLogicalUiRuntimeFixtures.listModel
      val list = base.screen.asInstanceOf[ResourceList]
      val detail = CozyLogicalUiRuntimeFixtures.detailFor("resource-a").screen.asInstanceOf[ResourceDetail]
      val paddedtitle = base.copy(screen = list.copy(title = " List title "))
      val paddedlabel = base.copy(screen = list.copy(items = Vector(ListItem("resource-a", Vector(
        Field("field", " Field label ", ContentRole, TextValue("value"))
      ), Vector.empty))))
      val paddedsectiontitle = Model(base.metadata, detail.copy(sections = Vector(
        Section("section", Some(" Section title "), Vector.empty)
      )))
      val paddedtitletext = base.copy(screen = list.copy(items = Vector(ListItem("resource-a", Vector(
        Field("title", "Title", TitleRole, TextValue(" Title text "))
      ), Vector.empty))))
      val whitespaceonly = Vector(
        base.copy(screen = list.copy(title = " \t ")),
        base.copy(screen = list.copy(items = Vector(ListItem("resource-a", Vector(
          Field("field", " ", ContentRole, TextValue("value"))
        ), Vector.empty)))),
        Model(base.metadata, detail.copy(sections = Vector(Section("section", Some(" \n "), Vector.empty)))),
        base.copy(screen = list.copy(items = Vector(ListItem("resource-a", Vector(
          Field("title", "Title", TitleRole, TextValue(" \r "))
        ), Vector.empty))))
      )
      val paddedprovenance = base.copy(metadata = base.metadata.copy(
        provenance = SourceProvenance(" producer", "source", "revision")
      ))

      When("each display-text and provenance variant reaches deterministic admission")
      val paddedresults = Vector(paddedtitle, paddedlabel, paddedsectiontitle, paddedtitletext).map(
        model => CozyLogicalUiRuntimeValidation.validate(model)
      )
      val whitespaceerrors = whitespaceonly.map(model => _left(CozyLogicalUiRuntimeValidation.validate(model)))
      val provenanceerror = _left(CozyLogicalUiRuntimeValidation.validate(paddedprovenance))

      Then("nonblank display text is returned byte-for-byte while whitespace-only display text and padded provenance retain strict diagnostics")
      paddedresults shouldBe Vector(Right(paddedtitle), Right(paddedlabel), Right(paddedsectiontitle), Right(paddedtitletext))
      whitespaceerrors.map(_.code) shouldBe Vector.fill(4)("LUI74_TEXT_INVALID")
      provenanceerror.code shouldBe "LUI74_PROVENANCE_INVALID"
      provenanceerror.path shouldBe "model.metadata.provenance.producerId"
    }

    "prefer contract diagnostics, then provenance diagnostics, before a null screen diagnostic" in {
      Given("one valid model with incompatible contract and invalid provenance, invalid provenance and null screen, and valid provenance with null screen")
      val base = CozyLogicalUiRuntimeFixtures.listModel
      val incompatiblemodel = base.copy(
        metadata = base.metadata.copy(
          contract = minimumContract.copy(schema = "cozy.logical-ui-runtime.v2"),
          provenance = SourceProvenance("", "source", "revision")
        ),
        screen = null
      )
      val invalidprovenancemodel = base.copy(
        metadata = base.metadata.copy(provenance = SourceProvenance("", "source", "revision")),
        screen = null
      )
      val nullscreenmodel = base.copy(screen = null)

      When("the three multi-invalid variants are admitted")
      val errors = Vector(incompatiblemodel, invalidprovenancemodel, nullscreenmodel).map(
        model => _left(CozyLogicalUiRuntimeValidation.validate(model))
      )

      Then("the stable contract, provenance, and screen precedence is preserved")
      errors.map(_.code) shouldBe Vector(
        "LUI74_CONTRACT_INCOMPATIBLE",
        "LUI74_PROVENANCE_INVALID",
        "LUI74_MODEL_INVALID"
      )
      errors.map(_.path) shouldBe Vector(
        "model.metadata.contract",
        "model.metadata.provenance.producerId",
        "model.screen"
      )
    }

    "reject null structures, Option containers, typed payloads, and incompatible role values without dereferencing them" in {
      Given("a valid List and Detail plus null root, metadata, screen, vectors, nested elements and actions, Options, payloads, and role/value combinations")
      val base = CozyLogicalUiRuntimeFixtures.listModel
      val list = base.screen.asInstanceOf[ResourceList]
      val detail = CozyLogicalUiRuntimeFixtures.detailFor("resource-a").screen.asInstanceOf[ResourceDetail]
      val nullmodels = Vector[Model](
        null,
        Model(null, list),
        Model(base.metadata, null),
        Model(base.metadata, list.copy(items = null)),
        Model(base.metadata, list.copy(items = Vector(null: ListItem))),
        Model(base.metadata, list.copy(items = Vector(ListItem("resource-a", null, Vector.empty)))),
        Model(base.metadata, list.copy(items = Vector(ListItem("resource-a", Vector(null: Field), Vector.empty)))),
        Model(base.metadata, list.copy(items = Vector(ListItem("resource-a", Vector(Field("value", "Value", ContentRole, null)), Vector.empty)))),
        Model(base.metadata, list.copy(items = Vector(ListItem("resource-a", Vector(Field("value", "Value", ContentRole, TextValue(null))), Vector.empty)))),
        Model(base.metadata, list.copy(items = Vector(ListItem("resource-a", Vector(Field("value", "Value", ContentRole, NumberValue(null))), Vector.empty)))),
        Model(base.metadata, list.copy(items = Vector(ListItem("resource-a", Vector(Field("value", "Value", ContentRole, TimestampValue(null))), Vector.empty)))),
        Model(base.metadata, list.copy(items = Vector(ListItem("resource-a", Vector(Field("value", "Value", SubtitleRole, BooleanValue(true))), Vector.empty)))),
        Model(base.metadata, list.copy(actions = null)),
        Model(base.metadata, list.copy(actions = Vector(null: Action))),
        Model(base.metadata, list.copy(items = Vector(ListItem("resource-a", Vector.empty, null)))),
        Model(base.metadata, detail.copy(sections = null)),
        Model(base.metadata, detail.copy(sections = Vector(null: Section))),
        Model(base.metadata, detail.copy(sections = Vector(Section("section", null.asInstanceOf[Option[String]], Vector.empty)))),
        Model(base.metadata, detail.copy(sections = Vector(Section("section", Some(null: String), Vector.empty))))
      )

      When("each malformed value reaches deterministic admission")
      val errors = nullmodels.map(model => _left(CozyLogicalUiRuntimeValidation.validate(model)))

      Then("the boundary returns structured model, element, value, and role diagnostics for every nullable descendant instead of uncaught null failures")
      errors.map(_.code) shouldBe Vector(
        "LUI74_MODEL_INVALID",
        "LUI74_MODEL_INVALID",
        "LUI74_MODEL_INVALID",
        "LUI74_ELEMENT_INVALID",
        "LUI74_ELEMENT_INVALID",
        "LUI74_ELEMENT_INVALID",
        "LUI74_ELEMENT_INVALID",
        "LUI74_VALUE_INVALID",
        "LUI74_VALUE_INVALID",
        "LUI74_VALUE_INVALID",
        "LUI74_VALUE_INVALID",
        "LUI74_ROLE_INVALID",
        "LUI74_ELEMENT_INVALID",
        "LUI74_ELEMENT_INVALID",
        "LUI74_ELEMENT_INVALID",
        "LUI74_ELEMENT_INVALID",
        "LUI74_ELEMENT_INVALID",
        "LUI74_ELEMENT_INVALID",
        "LUI74_ELEMENT_INVALID"
      )
    }

    "reject duplicate IDs and roles in their declared scopes while preserving opaque Unicode and case-sensitive IDs" in {
      Given("valid Unicode/case-distinct items plus duplicate item, section, field, action, and title-role fixtures and invalid ordinary IDs")
      val base = CozyLogicalUiRuntimeFixtures.listModel
      val list = base.screen.asInstanceOf[ResourceList]
      val detail = CozyLogicalUiRuntimeFixtures.detailFor("resource-a").screen.asInstanceOf[ResourceDetail]
      val validopaque = Model(
        base.metadata,
        list.copy(items = Vector(
          ListItem("資源:β", Vector.empty, Vector.empty),
          ListItem("Resource", Vector.empty, Vector.empty),
          ListItem("resource", Vector.empty, Vector.empty)
        ))
      )
      val duplicateitems = base.copy(screen = list.copy(items = Vector(
        ListItem("resource-a", Vector.empty, Vector.empty),
        ListItem("resource-a", Vector.empty, Vector.empty)
      )))
      val duplicatesections = Model(base.metadata, detail.copy(sections = Vector(
        Section("section", None, Vector.empty),
        Section("section", None, Vector.empty)
      )))
      val duplicatefields = base.copy(screen = list.copy(items = Vector(ListItem("resource-a", Vector(
        Field("field", "First", ContentRole, TextValue("one")),
        Field("field", "Second", ContentRole, TextValue("two"))
      ), Vector.empty))))
      val duplicateactions = base.copy(screen = list.copy(actions = Vector(
        Action("action", "First", enabled = true),
        Action("action", "Second", enabled = false)
      )))
      val duplicateroles = base.copy(screen = list.copy(items = Vector(ListItem("resource-a", Vector(
        Field("title-one", "First title", TitleRole, TextValue("one")),
        Field("title-two", "Second title", TitleRole, TextValue("two"))
      ), Vector.empty))))
      val invalidids = Vector(
        base.copy(screen = list.copy(id = "")),
        base.copy(screen = list.copy(id = " resource-list"))
      )

      When("the models are validated in declaration order")
      val duplicateerrors = Vector(duplicateitems, duplicatesections, duplicatefields, duplicateactions, duplicateroles).map(
        model => _left(CozyLogicalUiRuntimeValidation.validate(model))
      )
      val invalididerrors = invalidids.map(model => _left(CozyLogicalUiRuntimeValidation.validate(model)))
      val opaqueresult = CozyLogicalUiRuntimeValidation.validate(validopaque)

      Then("only duplicate values in each declared scope fail while case, Unicode, and internal punctuation remain opaque")
      duplicateerrors.map(_.code) shouldBe Vector(
        "LUI74_DUPLICATE_ID",
        "LUI74_DUPLICATE_ID",
        "LUI74_DUPLICATE_ID",
        "LUI74_DUPLICATE_ID",
        "LUI74_DUPLICATE_ROLE"
      )
      invalididerrors.map(_.code) shouldBe Vector("LUI74_ID_INVALID", "LUI74_ID_INVALID")
      opaqueresult shouldBe Right(validopaque)
    }
  }

  "Cozy Logical UI runtime selection" should {
    "reject invalid targets and selections without mutating the supplied model" in {
      Given("a valid List, self, absent, and malformed non-null DetailTargets, empty and unknown item IDs, a null intent, and an admitted Detail")
      val base = CozyLogicalUiRuntimeFixtures.listModel
      val list = base.screen.asInstanceOf[ResourceList]
      val selftarget = base.copy(screen = list.copy(detailTarget = DetailTarget(list.id)))
      val absenttarget = base.copy(screen = list.copy(detailTarget = null))
      val malformedtargets = Vector("", " ", " resource-detail", "resource-detail ").map { screenid =>
        base.copy(screen = list.copy(detailTarget = DetailTarget(screenid)))
      }
      val detail = CozyLogicalUiRuntimeFixtures.detailFor("resource-a")

      When("validation and selection are requested with each invalid boundary input")
      val selferror = _left(CozyLogicalUiRuntimeValidation.validate(selftarget))
      val absenterror = _left(CozyLogicalUiRuntimeValidation.validate(absenttarget))
      val malformederrors = malformedtargets.map(model => _left(CozyLogicalUiRuntimeValidation.validate(model)))
      val emptyerror = _left(select(base, "", CompactIntent))
      val unknownerror = _left(select(base, "resource-missing", CompactIntent))
      val intenterror = _left(select(base, "resource-a", null.asInstanceOf[AdaptiveIntent]))
      val detailerror = _left(select(detail, "resource-a", CompactIntent))

      Then("the errors identify the detail-target, selection, intent, and Detail-screen boundaries and the input List stays structurally equal")
      selferror.code shouldBe "LUI74_DETAIL_TARGET_INVALID"
      absenterror.code shouldBe "LUI74_DETAIL_TARGET_INVALID"
      malformederrors shouldBe Vector.fill(4)(
        RuntimeError("LUI74_DETAIL_TARGET_INVALID", "model.screen.detailTarget.screenId", "missing, invalid, or self DetailTarget")
      )
      emptyerror.code shouldBe "LUI74_SELECTION_INVALID"
      unknownerror.code shouldBe "LUI74_SELECTION_INVALID"
      intenterror.code shouldBe "LUI74_ADAPTIVE_INTENT_INVALID"
      detailerror.code shouldBe "LUI74_SELECTION_SCREEN_INVALID"
      base shouldBe CozyLogicalUiRuntimeFixtures.listModel
    }
  }

  "Cozy Logical UI shared vocabulary compatibility" should {
    "expose runtime values as the exact legacy semantic singleton aliases" in {
      Given("the existing package-local semantic aliases and public runtime List/Detail/Action values")
      val list = CozyLogicalUiRuntimeFixtures.listModel.screen.asInstanceOf[ResourceList]
      val detail = CozyLogicalUiRuntimeFixtures.detailFor("resource-a").screen.asInstanceOf[ResourceDetail]
      val action = list.actions.head

      When("the public vocabulary and legacy aliases are read by existing and runtime consumers")
      val legacybrowse = CozyLogicalUiSemantics.BrowsePurpose
      val legacydetail = CozyLogicalUiSemantics.DetailDisplay
      val legacycommand = CozyLogicalUiSemantics.CommandPattern

      Then("all values are the same singleton objects with unchanged IDs and runtime purpose/display/pattern classifications")
      legacybrowse should be theSameInstanceAs BrowsePurpose
      legacydetail should be theSameInstanceAs DetailDisplay
      legacycommand should be theSameInstanceAs CommandPattern
      list.purpose should be theSameInstanceAs BrowsePurpose
      detail.display should be theSameInstanceAs DetailDisplay
      action.pattern should be theSameInstanceAs CommandPattern
      Vector(BrowsePurpose.id, DetailDisplay.id, CommandPattern.id) shouldBe Vector("browse", "detail", "command")
    }
  }

  private def _left[A](value: Either[RuntimeError, A]): RuntimeError = value match {
    case Left(error) => error
    case Right(_) => fail("Expected a closed Logical UI runtime failure")
  }
}
