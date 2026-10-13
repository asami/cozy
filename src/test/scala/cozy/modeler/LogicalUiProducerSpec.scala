package cozy.modeler

import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.matchers.should.Matchers
import org.scalatest.GivenWhenThen
import org.scalacheck.{Gen, Prop, Test}
import cozy.ui.CozyLogicalUiRuntime.{SourceProvenance, ContentRole, TitleRole, SubtitleRole, DescriptionRole, StatusRole}
import LogicalUiProducer._
import LogicalUiProducer.DiagnosticCode._
import LogicalUiCncfAbiProjection._

/*
 * @since   Oct.  4, 2026
 * @version Oct.  4, 2026
 * @author  ASAMI, Tomoharu
 */
class LogicalUiProducerSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  private val _reference = LogicalUiProducerSpec.reference
  private val _list = _reference.definitions.head.screen.asInstanceOf[ListScreen]
  private val _detail = _reference.definitions(1).screen.asInstanceOf[DetailScreen]
  private val _field = _list.itemFields.head
  private val _kinds = Vector(StaticValueKind.Text, StaticValueKind.Number, StaticValueKind.Boolean,
    StaticValueKind.Timestamp, StaticValueKind.Missing)
  private val _opaque = Gen.nonEmptyListOf(Gen.alphaNumChar).map(_.mkString)
  private val _display = _opaque.map(value => "  日本語 \"" + value + "\" \\ \n\t\r\b\f  ")
  private val _parameters = Test.Parameters.default.withMinSuccessfulTests(100)

  private def _list_component(screen: ListScreen): SourceComponent =
    _reference.copy(definitions = _reference.definitions.updated(0, _reference.definitions.head.copy(screen = screen)))

  private def _detail_component(screen: DetailScreen): SourceComponent =
    _reference.copy(definitions = _reference.definitions.updated(1, _reference.definitions(1).copy(screen = screen)))

  private def _field_component(field: Field): SourceComponent = _list_component(_list.copy(itemFields = Vector(field)))

  private def _definition_component(definition: Definition): SourceComponent =
    _reference.copy(definitions = _reference.definitions.updated(0, definition))

  private def _diagnostic(result: Either[Diagnostic, ProjectedComponent]): Diagnostic = result match {
    case Left(diagnostic) => diagnostic
    case Right(_) => fail("expected a structured static rejection")
  }

  "Logical UI producer" should {
    "project the complete fixed reference" which {
      "preserves every consumer field and the independently declared revisions" in {
        Given("the explicit ESS2-01 static component and independently constructed consumer shape")
        val component = _reference
        val expected = LogicalUiProducerSpec.expected
        When("the entire component is admitted")
        val result = LogicalUiCncfAbiProjection.admit(component)
        Then("the complete descriptor, provenance, identities, bindings and List/Detail shape match")
        result shouldBe Right(expected)
      }

      "maps all shared roles and all five static kinds to closed constructor references" in {
        Given("all five roles in one container, with all five kinds on its content field")
        val roles = Vector(ContentRole, TitleRole, SubtitleRole, DescriptionRole, StatusRole)
        val fields = roles.zipWithIndex.map { case (role, index) =>
          Field(s"field$index", s"Label $index", role, ValueBinding("shared",
            if (role == ContentRole) _kinds else Vector(StaticValueKind.Text)))
        }
        val component = _list_component(_list.copy(itemFields = fields))
        When("the static component is projected")
        val result = LogicalUiCncfAbiProjection.admit(component)
        Then("the exact companion-qualified constructors retain declaration order")
        val projected = result.right.get.definitions.head.screen.asInstanceOf[ProjectedListDefinition].itemFields
        projected.map(_.role.constructorReference) shouldBe Vector("Content", "Title", "Subtitle", "Description", "Status")
          .map("GeneratedLogicalUiAbi.PresentationRole." + _)
        projected.head.value.allowedKinds.map(_.constructorReference) shouldBe
          Vector("Text", "Number", "Boolean", "Timestamp", "Missing").map("GeneratedLogicalUiAbi.ValueKind." + _)
      }

      "admits empty nested vectors and retains unreferenced Detail definitions" in {
        Given("an empty List template, an empty target Detail and an unreferenced empty Detail")
        val definitions = Vector(
          Definition("list", "r1", "view", ListScreen("List", Vector.empty, DetailTarget("detail"), Vector.empty, Vector.empty)),
          Definition("detail", "r2", "view", DetailScreen("Detail", Vector.empty, Vector.empty)),
          Definition("unused", "r3", "view", DetailScreen("Unused", Vector(Section("empty", Some("  Section  "), Vector.empty)), Vector.empty)))
        val component = _reference.copy(definitions = definitions)
        When("the complete component is interpreted")
        val result = LogicalUiCncfAbiProjection.admit(component)
        Then("all definitions and the padded optional title survive")
        result.right.get.definitions.map(_.screenId) shouldBe Vector("list", "detail", "unused")
        result.right.get.definitions(2).screen.asInstanceOf[ProjectedDetailDefinition].sections shouldBe
          Vector(ProjectedSectionDefinition("empty", Some("  Section  "), Vector.empty))
      }

      "admits scoped field, section, action and binding reuse" in {
        Given("equal field and action IDs in distinct scopes and a shared binding across all scopes")
        val field = _field.copy(value = ValueBinding("shared", Vector(StaticValueKind.Text)))
        val action = Action("same", "Action", "shared")
        val component = _list_component(_list.copy(itemFields = Vector(field), actions = Vector(action), itemActions = Vector(action)))
        val detail = DetailScreen("Detail", Vector(Section("a", None, Vector(field)), Section("b", None, Vector(field))), Vector(action))
        val complete = component.copy(definitions = component.definitions.updated(1, component.definitions(1).copy(screen = detail)))
        When("the complete component is admitted")
        val result = LogicalUiCncfAbiProjection.admit(complete)
        Then("reuse across field containers and separate action vectors is legal")
        result.isRight shouldBe true
        result.right.get.definitions(1).screen.asInstanceOf[ProjectedDetailDefinition].sections.map(_.fields.head.id) shouldBe
          Vector(field.id, field.id)
      }
    }

    "reject invalid static input before projection" which {
      // Each row is an independent semantic scenario with its own adjacent G/W/T clauses.
      val invalid = Vector[(String, SourceComponent, DiagnosticCode, String)](
        ("null source", null, ElementInvalid, "logicalUi"),
        ("missing owner", _reference.copy(owner = null), ComponentInvalid, "logicalUi.owner"),
        ("null definitions", _reference.copy(definitions = null), ElementInvalid, "logicalUi.definitions"),
        ("empty definitions", _reference.copy(definitions = Vector.empty), DefinitionsMissing, "logicalUi.definitions"),
        ("null definition", _reference.copy(definitions = Vector(null)), ElementInvalid, "logicalUi.definitions[0]"),
        ("null screen", _definition_component(_reference.definitions.head.copy(screen = null)), ElementInvalid, "logicalUi.definitions[0].screen"),
        ("null field vector", _list_component(_list.copy(itemFields = null)), ElementInvalid, "logicalUi.definitions[0].screen.itemFields"),
        ("null field", _list_component(_list.copy(itemFields = Vector(null))), ElementInvalid, "logicalUi.definitions[0].screen.itemFields[0]"),
        ("null screen actions", _list_component(_list.copy(actions = null)), ElementInvalid, "logicalUi.definitions[0].screen.actions"),
        ("null item actions", _list_component(_list.copy(itemActions = null)), ElementInvalid, "logicalUi.definitions[0].screen.itemActions"),
        ("null action", _list_component(_list.copy(actions = Vector(null))), ElementInvalid, "logicalUi.definitions[0].screen.actions[0]"),
        ("null item action", _list_component(_list.copy(itemActions = Vector(null))), ElementInvalid, "logicalUi.definitions[0].screen.itemActions[0]"),
        ("null Detail actions", _detail_component(_detail.copy(actions = null)), ElementInvalid, "logicalUi.definitions[1].screen.actions"),
        ("null Detail action", _detail_component(_detail.copy(actions = Vector(null))), ElementInvalid, "logicalUi.definitions[1].screen.actions[0]"),
        ("null section vector", _detail_component(_detail.copy(sections = null)), ElementInvalid, "logicalUi.definitions[1].screen.sections"),
        ("null section", _detail_component(_detail.copy(sections = Vector(null))), ElementInvalid, "logicalUi.definitions[1].screen.sections[0]"),
        ("null title Option", _detail_component(_detail.copy(sections = Vector(Section("s", null, Vector.empty)))), ElementInvalid, "logicalUi.definitions[1].screen.sections[0].title"),
        ("Some null title", _detail_component(_detail.copy(sections = Vector(Section("s", Some(null), Vector.empty)))), ElementInvalid, "logicalUi.definitions[1].screen.sections[0].title"),
        ("null section fields", _detail_component(_detail.copy(sections = Vector(Section("s", None, null)))), ElementInvalid, "logicalUi.definitions[1].screen.sections[0].fields"),
        ("null value binding", _field_component(_field.copy(value = null)), ElementInvalid, "logicalUi.definitions[0].screen.itemFields[0].value"),
        ("null kind vector", _field_component(_field.copy(value = _field.value.copy(allowedKinds = null))), ElementInvalid, "logicalUi.definitions[0].screen.itemFields[0].value.allowedKinds"),
        ("empty kinds", _field_component(_field.copy(value = _field.value.copy(allowedKinds = Vector.empty))), ValueKindInvalid, "logicalUi.definitions[0].screen.itemFields[0].value.allowedKinds"),
        ("null kind", _field_component(_field.copy(value = _field.value.copy(allowedKinds = Vector(null)))), ValueKindInvalid, "logicalUi.definitions[0].screen.itemFields[0].value.allowedKinds[0]"),
        ("repeated kind", _field_component(_field.copy(value = _field.value.copy(allowedKinds = Vector(StaticValueKind.Text, StaticValueKind.Text)))), ValueKindInvalid, "logicalUi.definitions[0].screen.itemFields[0].value.allowedKinds[1]"),
        ("null role", _field_component(_field.copy(role = null)), RoleInvalid, "logicalUi.definitions[0].screen.itemFields[0].role"),
        ("noncontent Number", _field_component(_field.copy(value = _field.value.copy(allowedKinds = Vector(StaticValueKind.Number)))), RoleInvalid, "logicalUi.definitions[0].screen.itemFields[0].role"),
        ("noncontent Text plus Missing", _field_component(_field.copy(value = _field.value.copy(allowedKinds = Vector(StaticValueKind.Text, StaticValueKind.Missing)))), RoleInvalid, "logicalUi.definitions[0].screen.itemFields[0].role"),
        ("duplicate fields", _list_component(_list.copy(itemFields = Vector(_field, _field))), DuplicateId, "logicalUi.definitions[0].screen.itemFields[1].id"),
        ("duplicate noncontent role", _list_component(_list.copy(itemFields = Vector(_field, _field.copy(id = "other")))), DuplicateRole, "logicalUi.definitions[0].screen.itemFields[1].role"),
        ("duplicate sections", _detail_component(_detail.copy(sections = Vector(_detail.sections.head, _detail.sections.head))), DuplicateId, "logicalUi.definitions[1].screen.sections[1].id"),
        ("duplicate Detail fields", _detail_component(_detail.copy(sections = Vector(Section("s", None, Vector(_field, _field))))), DuplicateId, "logicalUi.definitions[1].screen.sections[0].fields[1].id"),
        ("duplicate Detail role", _detail_component(_detail.copy(sections = Vector(Section("s", None, Vector(_field, _field.copy(id = "other")))))), DuplicateRole, "logicalUi.definitions[1].screen.sections[0].fields[1].role"),
        ("duplicate Detail actions", _detail_component(_detail.copy(actions = Vector(Action("a", "A", "b"), Action("a", "B", "b")))), DuplicateId, "logicalUi.definitions[1].screen.actions[1].id"),
        ("duplicate screen actions", _list_component(_list.copy(actions = Vector(Action("a", "A", "b"), Action("a", "B", "b")))), DuplicateId, "logicalUi.definitions[0].screen.actions[1].id"),
        ("duplicate item actions", _list_component(_list.copy(itemActions = Vector(Action("a", "A", "b"), Action("a", "B", "b")))), DuplicateId, "logicalUi.definitions[0].screen.itemActions[1].id"),
        ("duplicate screens", _reference.copy(definitions = Vector(_reference.definitions(1), _reference.definitions(1))), DuplicateId, "logicalUi.definitions[1].screenId"),
        ("missing provenance", _reference.copy(provenance = null), ProvenanceInvalid, "logicalUi.provenance"),
        ("padded provenance", _reference.copy(provenance = _reference.provenance.copy(revision = " padded ")), ProvenanceInvalid, "logicalUi.provenance.revision"),
        ("missing target", _list_component(_list.copy(detailTarget = null)), DetailTargetInvalid, "logicalUi.definitions[0].screen.detailTarget"),
        ("null target ID", _list_component(_list.copy(detailTarget = DetailTarget(null))), DetailTargetInvalid, "logicalUi.definitions[0].screen.detailTarget.screenId"),
        ("blank target ID", _list_component(_list.copy(detailTarget = DetailTarget(""))), DetailTargetInvalid, "logicalUi.definitions[0].screen.detailTarget.screenId"),
        ("padded target ID", _list_component(_list.copy(detailTarget = DetailTarget(" capture-detail "))), DetailTargetInvalid, "logicalUi.definitions[0].screen.detailTarget.screenId"),
        ("self target", _list_component(_list.copy(detailTarget = DetailTarget("collecting-resources"))), DetailTargetInvalid, "logicalUi.definitions[0].screen.detailTarget.screenId"),
        ("unresolved target", _list_component(_list.copy(detailTarget = DetailTarget("elsewhere"))), DetailTargetInvalid, "logicalUi.definitions[0].screen.detailTarget.screenId"),
        ("List target", _reference.copy(definitions = Vector(_reference.definitions.head,
          _reference.definitions.head.copy(screenId = "capture-detail"))), DetailTargetInvalid, "logicalUi.definitions[0].screen.detailTarget.screenId")
      )
      invalid.foreach { case (name, component, code, path) =>
        name in {
          Given("a static component with " + name)
          val input = component
          When("the pure gate interprets the complete component")
          val diagnostic = _diagnostic(LogicalUiCncfAbiProjection.admit(input))
          Then("the closed diagnostic retains the exact first path and a static reason")
          diagnostic.code shouldBe code
          diagnostic.path shouldBe path
          diagnostic.reason.trim should not be empty
        }
      }

      Vector(null, "", " ", " padded ").zipWithIndex.foreach { case (value, index) =>
        s"reject opaque scalar family $index without normalization" in {
          Given("the same invalid opaque value in each identity, revision and binding position")
          val definitions = Vector(
            _reference.definitions.head.copy(screenId = value),
            _reference.definitions.head.copy(definitionRevision = value),
            _reference.definitions.head.copy(viewBindingId = value))
          val components = definitions.map(_definition_component) ++ Vector(
            _field_component(_field.copy(id = value)),
            _field_component(_field.copy(value = _field.value.copy(bindingId = value))),
            _detail_component(_detail.copy(sections = Vector(Section(value, None, Vector.empty)))),
            _list_component(_list.copy(actions = Vector(Action(value, "Label", "binding")))),
            _list_component(_list.copy(actions = Vector(Action("action", "Label", value)))))
          When("each complete component is interpreted")
          val diagnostics = components.map(component => _diagnostic(LogicalUiCncfAbiProjection.admit(component)))
          Then("every opaque position fails as an ID rather than trimmed success")
          diagnostics.map(_.code) shouldBe Vector.fill(8)(IdInvalid)
        }

        s"reject provenance scalar family $index" in {
          Given("invalid producer, source and revision provenance members")
          val sources = Vector(SourceProvenance(value, "s", "r"), SourceProvenance("p", value, "r"), SourceProvenance("p", "s", value))
          When("the three provenance interpretations are admitted")
          val diagnostics = sources.map(source => _diagnostic(LogicalUiCncfAbiProjection.admit(_reference.copy(provenance = source))))
          Then("all provenance members retain their specialized code and path")
          diagnostics.map(_.code) shouldBe Vector.fill(3)(ProvenanceInvalid)
          diagnostics.map(_.path) shouldBe Vector("producerId", "sourceId", "revision").map("logicalUi.provenance." + _)
        }
      }

      Vector(null, "", " \t\n ").zipWithIndex.foreach { case (value, index) =>
        s"reject blank display scalar family $index" in {
          Given("blank titles, field labels, optional section titles and action labels")
          val components = Vector(
            _list_component(_list.copy(title = value)), _detail_component(_detail.copy(title = value)),
            _field_component(_field.copy(label = value)),
            _list_component(_list.copy(actions = Vector(Action("a", value, "b"))))) ++
            (if (value == null) Vector.empty else Vector(_detail_component(_detail.copy(sections = Vector(Section("s", Some(value), Vector.empty))))))
          When("each display-text position is interpreted")
          val diagnostics = components.map(component => _diagnostic(LogicalUiCncfAbiProjection.admit(component)))
          Then("blank display values fail without replacing their bytes")
          diagnostics.map(_.code) shouldBe Vector.fill(components.size)(TextInvalid)
        }
      }

      Vector("domain" -> "lower", "domain" -> "Bad_Name", "domain" -> "", "domain" -> null,
        "" -> "Domain", (null: String) -> "Domain", " domain" -> "Domain", "a..b" -> "Domain",
        "a.b_" -> "Domain", "a.1b" -> "Domain", "a.class" -> "Domain", "a.given" -> "Domain",
        "a.enum" -> "Domain", "a.extension" -> "Domain").zipWithIndex.foreach { case ((namespace, stem), index) =>
        s"reject invalid namespace or component stem $index" in {
          Given("an explicitly declared invalid Scala namespace or component stem")
          val component = _reference.copy(owner = ComponentOwner(stem, namespace))
          When("the owner is interpreted before definitions")
          val diagnostic = _diagnostic(LogicalUiCncfAbiProjection.admit(component))
          Then("the producer refuses to infer or sanitize the owner")
          diagnostic.code shouldBe ComponentInvalid
          diagnostic.path should startWith("logicalUi.owner.")
        }
      }
    }

    "admit a component batch atomically" which {
      "preserves absence as an empty vector and diagnoses null batch shapes" in {
        Given("an absent, null and null-element component batch")
        val batches = Vector(Vector.empty[SourceComponent], null, Vector(null))
        When("the batch gate interprets each input")
        val results = batches.map(LogicalUiCncfAbiProjection.admitAll)
        Then("absence is empty success while null shapes are typed rejections")
        results.head shouldBe Right(Vector.empty)
        results(1).left.get.code shouldBe ElementInvalid
        results(2).left.get shouldBe Diagnostic(ElementInvalid, "logicalUi[0]", "null source component")
      }

      "returns one indexed error without exposing an admitted prefix" in {
        Given("a valid component followed by a second component with an invalid screen identity")
        val later = _definition_component(_reference.definitions.head.copy(screenId = " padded "))
          .copy(owner = ComponentOwner("Other", "other"))
        val batch = Vector(_reference, later)
        When("the complete batch is interpreted")
        val result = LogicalUiCncfAbiProjection.admitAll(batch)
        Then("the batch is Left with the second root index")
        result.left.get.code shouldBe IdInvalid
        result.left.get.path shouldBe "logicalUi[1].definitions[0].screenId"
      }

      "rejects repeated component roots after their complete local checks" in {
        Given("two individually valid roots declaring the same component")
        val batch = Vector(_reference, _reference)
        When("the complete batch is admitted")
        val result = LogicalUiCncfAbiProjection.admitAll(batch)
        Then("the second root is a component error")
        result.left.get.code shouldBe ComponentInvalid
        result.left.get.path shouldBe "logicalUi[1].owner.componentName"
      }

      "does not resolve a target through another component" in {
        Given("a List whose Detail exists only in a distinct component")
        val first = _reference.copy(definitions = Vector(_reference.definitions.head))
        val second = _reference.copy(owner = ComponentOwner("Other", "other"), definitions = Vector(_reference.definitions(1)))
        When("the two components are interpreted together")
        val result = LogicalUiCncfAbiProjection.admitAll(Vector(first, second))
        Then("the List remains unresolved inside its owning component")
        result.left.get.code shouldBe DetailTargetInvalid
        result.left.get.path shouldBe "logicalUi[0].definitions[0].screen.detailTarget.screenId"
      }
    }

    "preserve static semantics for generated families" which {
      "preserves opaque identities and padded display bytes" in {
        Given("nonempty generated opaque identities and padded Unicode, quotes, backslashes and controls")
        val samples = Gen.zip(_opaque, _display)
        When("100 generated complete definitions are projected")
        val result = Test.check(_parameters, Prop.forAll(samples) { case (identity, text) =>
          val field = Field(identity, text, ContentRole, ValueBinding(identity, Vector(StaticValueKind.Text)))
          val component = _reference.copy(provenance = SourceProvenance(identity, identity, identity), definitions = Vector(
            Definition(identity, identity, identity, DetailScreen(text, Vector(Section(identity, Some(text), Vector(field))),
              Vector(Action(identity, text, identity))))))
          LogicalUiCncfAbiProjection.admit(component).exists { projected =>
            val definition = projected.definitions.head
            val screen = definition.screen.asInstanceOf[ProjectedDetailDefinition]
            definition.screenId == identity && definition.definitionRevision == identity && definition.viewBindingId == identity &&
              definition.provenance == ProjectedSourceProvenance(identity, identity, identity) && screen.title == text &&
              screen.sections.head.title.contains(text) && screen.sections.head.fields.head.label == text &&
              screen.sections.head.fields.head.value.bindingId == identity && screen.actions.head.label == text
          }
        })
        Then("every admitted scalar retains its exact supplied value")
        result.passed shouldBe true
      }

      "preserves every supported unique content-kind subset in supplied order" in {
        Given("random nonempty subsets and permutations of the five supported kinds")
        val samples = Gen.someOf(_kinds).suchThat(_.nonEmpty).flatMap(values => Gen.pick(values.size, values))
        When("100 content-kind declarations are projected")
        val result = Test.check(_parameters, Prop.forAll(samples) { kinds =>
          val component = _field_component(_field.copy(role = ContentRole, value = ValueBinding("binding", kinds.toVector)))
          LogicalUiCncfAbiProjection.admit(component).exists { projected =>
            val values = projected.definitions.head.screen.asInstanceOf[ProjectedListDefinition].itemFields.head.value.allowedKinds
            values.map(_.constructorReference.split('.').last.toLowerCase) == kinds.map(_.tag).toVector
          }
        })
        Then("all nonempty supported orders are admitted without sorting")
        result.passed shouldBe true
      }

      "preserves definition, section, field and action declaration order" in {
        Given("generated ordered distinct identities for every static vector")
        val samples = Gen.choose(2, 12).flatMap(size => Gen.listOfN(size, _opaque).map(_.zipWithIndex.map {
          case (value, index) => index.toString + ":" + value
        }.toVector))
        When("100 nested components with those declaration orders are projected")
        val result = Test.check(_parameters, Prop.forAll(samples) { identities =>
          val fields = identities.map(id => Field(id, "Label", ContentRole, ValueBinding(id, Vector(StaticValueKind.Text))))
          val actions = identities.map(id => Action(id, "Label", id))
          val sections = identities.map(id => Section(id, None, fields))
          val definitions = identities.map(id => Definition(id, "r", "v", DetailScreen("Title", sections, actions)))
          LogicalUiCncfAbiProjection.admit(_reference.copy(definitions = definitions)).exists { projected =>
            projected.definitions.map(_.screenId) == identities && projected.definitions.forall { definition =>
              val screen = definition.screen.asInstanceOf[ProjectedDetailDefinition]
              screen.sections.map(_.id) == identities && screen.sections.forall(_.fields.map(_.id) == identities) &&
                screen.actions.map(_.id) == identities
            }
          }
        })
        Then("all vector orders remain exact")
        result.passed shouldBe true
      }

      "admits generated reuse only across declared scopes" in {
        Given("generated identities shared across field and action scopes, with one binding reused everywhere")
        val samples = _opaque
        When("100 components containing scoped reuse are interpreted")
        val result = Test.check(_parameters, Prop.forAll(samples) { identity =>
          val field = Field(identity, "Label", TitleRole, ValueBinding(identity, Vector(StaticValueKind.Text)))
          val action = Action(identity, "Action", identity)
          val list = _list.copy(itemFields = Vector(field), actions = Vector(action), itemActions = Vector(action))
          val detail = _detail.copy(sections = Vector(Section("a", None, Vector(field)), Section("b", None, Vector(field))), actions = Vector(action))
          LogicalUiCncfAbiProjection.admit(_reference.copy(definitions = Vector(
            _reference.definitions.head.copy(screen = list), _reference.definitions(1).copy(screen = detail)))).isRight
        })
        Then("cross-scope reuse remains legal for every generated identity")
        result.passed shouldBe true
      }

      "retains the first diagnostic despite arbitrary later invalid definitions" in {
        Given("a fixed first ID violation followed by generated later null or malformed definitions")
        val samples = Gen.listOf(Gen.oneOf[Definition](null, _reference.definitions(1).copy(screenId = " padded "),
          _reference.definitions(1).copy(screen = null)))
        When("100 complete vectors are interpreted")
        val result = Test.check(_parameters, Prop.forAll(samples) { later =>
          val component = _reference.copy(definitions = _reference.definitions.head.copy(screenId = "") +: later.toVector)
          LogicalUiCncfAbiProjection.admit(component).left.toOption.exists(diagnostic =>
            diagnostic.code == IdInvalid && diagnostic.path == "logicalUi.definitions[0].screenId")
        })
        Then("later failures cannot replace the first structured rejection")
        result.passed shouldBe true
      }
    }
  }
}

/** Independent static reference data, shared with the ordinary CML semantic specification. */
private[modeler] object LogicalUiProducerSpec {
  val reference: SourceComponent = {
    val title = Field("title", "Title", TitleRole, ValueBinding("ess.title", Vector(StaticValueKind.Text)))
    val stage = Field("stage", "Capture stage", StatusRole, ValueBinding("ess.stage", Vector(StaticValueKind.Text)))
    val description = Field("description", "Description", DescriptionRole, ValueBinding("ess.description", Vector(StaticValueKind.Text)))
    SourceComponent(ComponentOwner("Domain", "domain"), SourceProvenance("cozy.logical-ui-generated.v1", "ess2-01", "ess2-01-cml-1"), Vector(
      Definition("collecting-resources", "ess-definition-1", "ess.reference",
        ListScreen("Collecting Resources", Vector(title, stage), DetailTarget("capture-detail"), Vector.empty, Vector.empty)),
      Definition("capture-detail", "ess-definition-1", "ess.reference",
        DetailScreen("Capture Detail", Vector(Section("main", None, Vector(title, stage, description))),
          Vector(Action("request-review", "Request Review", "ess.request-review"))))))
  }

  val expected: ProjectedComponent = {
    val contract = ProjectedSemanticContract("cozy.logical-ui-runtime.v1", 1, "cozy.logical-ui-semantics.v1", "provisional-scenario",
      ProjectedDriverReference("KnowledgeHubProject/nict-editing-studio-app", "48eb43b8eb839137e17ef4c944b3db4a73d0cec3", "docs/phase/phase-2.md", "resource-list-detail"))
    val provenance = ProjectedSourceProvenance("cozy.logical-ui-generated.v1", "ess2-01", "ess2-01-cml-1")
    val title = ProjectedFieldDefinition("title", "Title", ProjectedPresentationRole.Title, ProjectedValueDefinition("ess.title", Vector(ProjectedValueKind.Text)))
    val stage = ProjectedFieldDefinition("stage", "Capture stage", ProjectedPresentationRole.Status, ProjectedValueDefinition("ess.stage", Vector(ProjectedValueKind.Text)))
    val description = ProjectedFieldDefinition("description", "Description", ProjectedPresentationRole.Description, ProjectedValueDefinition("ess.description", Vector(ProjectedValueKind.Text)))
    ProjectedComponent(ComponentOwner("Domain", "domain"), Vector(
      ProjectedDefinition("cncf.logical-ui-generated.v1", 1, contract, provenance, "collecting-resources", "ess-definition-1", "ess.reference",
        ProjectedListDefinition("Collecting Resources", Vector(title, stage), ProjectedDetailTarget("capture-detail"), Vector.empty, Vector.empty)),
      ProjectedDefinition("cncf.logical-ui-generated.v1", 1, contract, provenance, "capture-detail", "ess-definition-1", "ess.reference",
        ProjectedDetailDefinition("Capture Detail", Vector(ProjectedSectionDefinition("main", None, Vector(title, stage, description))),
          Vector(ProjectedActionDefinition("request-review", "Request Review", "ess.request-review"))))))
  }
}
