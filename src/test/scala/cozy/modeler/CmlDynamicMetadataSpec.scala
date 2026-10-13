package cozy.modeler

import org.scalacheck.{Gen, Prop, Test}
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import play.api.libs.json.{JsArray, JsBoolean, JsNull, JsObject, JsString, JsValue, Json}
import scala.io.Source

import CmlSemanticFoundation._
import CmlDynamicMetadata._

/*
 * @since   Oct.  4, 2026
 * @version Oct. 13, 2026
 * @author  ASAMI, Tomoharu
 */
final class CmlDynamicMetadataSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  "Dynamic declared facts" should {
    "complete carriers" which {
      "DM-01 retain seven distinct kinds and complete core Term source and opaque peer facts under shared names" in {
        Given("two explicit declarations per dynamic kind, same-named supporting elements and a separate qualified Term")
        val fixture = _fixture()
        When("the admitted producer renders and the strict receiver reads the versioned envelope")
        val graph = _admitted(_publish(fixture))
        val root = CmlDynamicMetadata.toJson(graph)
        val consumed = _admitted(CmlDynamicMetadata.read(root))
        Then("every authored carrier and full core record is preserved without collapsing Workflow into StateMachine")
        _expect_facts(consumed, fixture)
        consumed.catalog.elements.map(_.name).distinct shouldBe Vector("共有")
        Vector(consumed.workflows.head.element.kind, consumed.activities.head.element.kind, consumed.flows.head.element.kind,
          consumed.stateMachines.head.element.kind, consumed.states.head.element.kind, consumed.transitions.head.element.kind,
          consumed.causalLinks.head.element.kind) shouldBe _carrier_kinds
        (root \ "schemaVersion").as[String] shouldBe "cozy.cml.semantic-metadata.v1"
        _child(root).keys shouldBe (_arrays :+ "schemaVersion").toSet
        consumed.envelope.extensions.value("cozy.cml.structure") shouldBe fixture.extensions.value("cozy.cml.structure")
        consumed.envelope.extensions.value("cozy.cml.classification") shouldBe fixture.extensions.value("cozy.cml.classification")
      }

      "DM-02 retain qualified case authority delimiters and Term-domain distinctions without reconstructing identity" in {
        Given("four Workflow records whose qualified pairs distinguish case, authority and delimiter placement")
        val fixture = _with_absence(_fixture(), Absent(NotDeclared, "No link declared."))
        val ids = Vector(ModelElementId("a:b", "c"), ModelElementId("a", "b:c"), ModelElementId("A:b", "c"), ModelElementId("a:b", "C"))
        val records = ids.map(id => _record("workflow", id.elementId, id.modelId))
        val term = TermRecord(TermId("a:b", "c"), _source("a:b"))
        val supplied = fixture.copy(records = fixture.records.filterNot(_.kind == "workflow") ++ records,
          terms = Vector(term), workflows = records.map(record => fixture.workflows.head.copy(element = record)))
        When("the exact qualified records are built rendered and strictly read and looked up")
        val consumed = _admitted(CmlDynamicMetadata.read(_wire(supplied)))
        val lookups = ids.map(consumed.catalog.element)
        val unknown = consumed.catalog.element(ModelElementId("a:b", "unknown"))
        val termlookup = consumed.catalog.term(term.identity)
        Then("qualified pairs remain separate and the Term cannot supply a model record or a fallback")
        lookups shouldBe records.map(Right(_))
        unknown.isLeft shouldBe true
        termlookup shouldBe Right(term)
        consumed.workflows.map(_.element.identity) shouldBe ids.map(Present(_))
      }

      "DM-01 preserve accepted Structure and Classification projections and peers while adding dynamic facts" in {
        Given("the accepted declared Classification resource carrying its unchanged Structure prefix and explicit new Dynamic declarations")
        val fixture = _with_absence(_fixture(), Absent(NotDeclared, "No additional dynamic link declared."))
        When("the predecessor resource is read, Dynamic is added, and all three strict receivers consume the result")
        val baseline = CmlClassificationMetadata.read(_resource("classification-metadata-v1-declared.json")) match {
          case Right(graph) => graph
          case Left(diagnostics) => fail("Expected admitted predecessor fixture: " + diagnostics)
        }
        val baselinestructure = CmlStructureMetadata.read(CmlClassificationMetadata.toJson(baseline)).right.get
        val supplied = fixture.copy(records = baseline.catalog.elements ++ fixture.records.filter(record => _carrier_kinds.contains(record.kind)),
          terms = baseline.catalog.terms ++ fixture.terms, extensions = baseline.envelope.extensions +
            ("org.example.dynamic-peer" -> Json.obj("ordered" -> Json.arr("z", "a", JsNull))))
        val root = _wire(supplied)
        val consumed = _admitted(CmlDynamicMetadata.read(root))
        val classification = CmlClassificationMetadata.read(root).right.get
        val structure = CmlStructureMetadata.read(root).right.get
        Then("full predecessor core facts projections namespaces and opaque peers remain exact")
        _expect_facts(consumed, supplied)
        classification.traits shouldBe baseline.traits
        classification.powertypes shouldBe baseline.powertypes
        classification.generalizations shouldBe baseline.generalizations
        classification.traitRealizations shouldBe baseline.traitRealizations
        classification.powertypeClassifications shouldBe baseline.powertypeClassifications
        structure.elements shouldBe baselinestructure.elements
        structure.relations shouldBe baselinestructure.relations
        baseline.envelope.extensions.fields.foreach { case (key, value) => consumed.envelope.extensions.value(key) shouldBe value }
      }

      "DM-02 preserve equal anonymous occurrence payloads and unaddressability for every carrier kind" in {
        Given("two equal anonymous core occurrences in each of the seven carrier families")
        val fixture = _anonymous(_fixture())
        When("the anonymous declarations are built canonically rendered parsed strictly read and queried")
        val graph = _admitted(_publish(fixture))
        val consumed = _admitted(CmlDynamicMetadata.read(Json.parse(CmlDynamicMetadata.canonicalJson(graph))))
        val unknown = consumed.catalog.element(ModelElementId("dynamic.example", "workflow:0"))
        Then("all fourteen anonymous occurrences and their explicit gaps remain exact with no invented IDs")
        _expect_facts(consumed, fixture)
        consumed.workflows.size shouldBe 2
        consumed.workflows.map(_.element).distinct.size shouldBe 1
        unknown.isLeft shouldBe true
        consumed.catalog.elements.filter(record => _carrier_kinds.contains(record.kind)).map(_.identity).distinct shouldBe
          Vector(Absent(NotRepresented, "No stable dynamic identity represented."))
      }
    }
  }

  "Dynamic admission" should {
    "full-record coverage" which {
      "DM-03 reject missing and excess projections for all seven kinds including equal anonymous records" in {
        Given("complete addressable and anonymous declarations with two required occurrences per carrier")
        val fixtures = Vector(_fixture(), _anonymous(_fixture()))
        val missing = fixtures.flatMap(fixture => _carrier_variants(fixture, 1))
        val excess = fixtures.flatMap(fixture => _carrier_variants(fixture, 3))
        When("the public factory receives each incomplete and excessive multiset")
        val missingresults = missing.map(_publish)
        val excessresults = excess.map(_publish)
        Then("each carrier enforces occurrence coverage with separate missing and duplicate diagnostics")
        missingresults.map(_first(_).kind) shouldBe Vector.fill(14)(MissingProjection)
        excessresults.map(_first(_).kind) shouldBe Vector.fill(14)(DuplicateProjection)
      }

      "DM-03 reject forged identity kind name source and core references on every carrier and both admission routes" in {
        Given("complete declarations and independently authored contradictions of each complete embedded record")
        val fixture = _fixture()
        val changes: Vector[ElementRecord => ElementRecord] = Vector(
          record => record.copy(identity = Present(ModelElementId("dynamic.example", "forged"))),
          record => record.copy(kind = "unrelated"), record => record.copy(name = "forged"),
          record => record.copy(references = Absent(NotDeclared, "Changed core references.")),
          _ => fixture.records.last)
        val forged = changes.flatMap(change => _projection_record_variants(fixture, change))
        When("all factories and strict readers receive the full-record contradictions")
        val root = _wire(fixture)
        val typedresults = forged.map(_publish)
        val wireresults = _arrays.flatMap { array =>
          val record = _at(_child(root), Vector(array, "0", "element")).as[JsObject]
          val altered = Vector(record + ("identity" -> _present_json(Json.obj("modelId" -> "dynamic.example", "elementId" -> "forged"))),
            record + ("kind" -> JsString("unrelated")), record + ("name" -> JsString("forged")),
            record + ("references" -> _absent_json(NotDeclared, "Changed core references.")),
            (root \ "elements").as[Vector[JsValue]].last)
          altered.map(value => CmlDynamicMetadata.read(_replace_child(root, Vector(array, "0", "element"), value)))
        }
        Then("identity or shared name alone never substitutes for exact core binding")
        typedresults.map(_first(_).kind) shouldBe Vector.fill(35)(InvalidCoreBinding)
        wireresults.map(_first(_).kind) shouldBe Vector.fill(35)(InvalidCoreBinding)
      }
    }

    "qualified field targets" which {
      "DM-04 accept each allowed Local kind at every reference carrier including all expression sites" in {
        Given("an independent table of every reference field and its complete permitted Local kind set")
        val fixture = _fixture()
        val slots = _reference_slots
        val supplied = slots.flatMap(slot => slot.kinds.map(kind => slot.update(fixture, _reference(_target_id(kind)))))
        When("each allowed field target is built rendered and strictly read")
        val consumed = supplied.map(value => _admitted(CmlDynamicMetadata.read(_wire(value))))
        Then("every field retains the exact admitted reference without inferred membership or reciprocal links")
        consumed.zip(supplied).foreach { case (graph, expected) => _expect_facts(graph, expected) }
      }

      "DM-04 reject dangling and every wrong Local kind on every reference carrier without fallback" in {
        Given("the same field table plus an unknown pair and all admitted kinds outside each field's permitted set")
        val fixture = _fixture()
        val supplied = _reference_slots.flatMap { slot =>
          val wrong = (_support_kinds ++ _carrier_kinds).distinct.filterNot(slot.kinds.contains).map(kind => _reference(_target_id(kind)))
          (wrong :+ _reference("missing")).map(reference => slot.update(fixture, reference))
        }
        When("typed factories and strict wire admission receive each forbidden Local target")
        val root = _wire(fixture)
        val typedresults = supplied.map(_publish)
        val wireresults = _reference_slots.map(slot => CmlDynamicMetadata.read(
          _replace_child(root, slot.path, _reference_json(_reference("missing")))))
        Then("every failure reports declared Dynamic semantics and never reconstructs a target from its name")
        typedresults.map(_first(_).kind).distinct shouldBe Vector(InvalidDynamicSemantics)
        wireresults.map(_first(_).kind).distinct shouldBe Vector(InvalidDynamicSemantics)
      }

      "DM-04 retain unresolved and locally equal External pairs across all reference carriers" in {
        Given("External references to an unknown pair and to a Local record outside each field's kind set")
        val fixture = _fixture()
        val supplied = _reference_slots.flatMap { slot =>
          Vector("missing", "unrelated").map(id => slot.update(fixture, _reference(id, External)))
        }
        When("each External pair is built and strictly read")
        val consumed = supplied.map(value => _admitted(CmlDynamicMetadata.read(_wire(value))))
        Then("External identity and boundary survive without local resolution or a local type claim")
        consumed.zip(supplied).foreach { case (graph, expected) => _expect_facts(graph, expected) }
      }
    }

    "presence and literal payloads" which {
      "DM-05 retain all four absence branches at every field and within each declared expression" in {
        Given("four explicit reasons and independently assigned coordinate details for every required Presence")
        val fixtures = _reasons.map(reason => _with_absence(_fixture(), Absent(reason, "Authored gap: 条件Aa")))
        val expressionfixtures = _reasons.map { reason =>
          _with_expression(_fixture(), DeclaredExpression("", Absent(reason, "Expression gap: 条件Aa")))
        }
        When("all declarations and expression references are built canonically rendered and strictly read")
        val consumed = (fixtures ++ expressionfixtures).map(value => _admitted(CmlDynamicMetadata.read(_wire(value))))
        Then("every field retains its reason and detail with no omission default or synthesized link")
        consumed.zip(fixtures ++ expressionfixtures).foreach { case (graph, expected) => _expect_facts(graph, expected) }
      }

      "DM-05 preserve explicit empty vectors and arbitrary nonnull purpose and expression literals" in {
        Given("empty and escaped Unicode multiline literal strings plus explicit empty lists for every vector carrier")
        val literals = Vector("", " ", "条件\n\"quote\"\\path\t\u0000")
        val fixtures = literals.map { text =>
          val base = _with_empty_vectors(_fixture())
          _with_expression(base.copy(workflows = base.workflows.map(_.copy(purpose = Present(text)))),
            DeclaredExpression(text, Present(Vector.empty)))
        }
        When("the complete producer builds renders parses and reads each literal assignment")
        val consumed = fixtures.map { fixture =>
          val graph = _admitted(_publish(fixture))
          _admitted(CmlDynamicMetadata.read(Json.parse(CmlDynamicMetadata.canonicalJson(graph))))
        }
        val emptyfixture = _with_empty_vectors(_fixture())
        val emptygraph = _admitted(CmlDynamicMetadata.read(_wire(emptyfixture)))
        Then("literal text remains exact and Present empty vectors stay different from absence")
        _expect_facts(emptygraph, emptyfixture)
        emptygraph.states.head.entryActions shouldBe Present(Vector.empty)
        emptygraph.transitions.head.triggers shouldBe Present(Vector.empty)
        emptygraph.transitions.head.actions shouldBe Present(Vector.empty)
        consumed.zip(fixtures).foreach { case (graph, expected) => _expect_facts(graph, expected) }
        consumed.map(_.workflows.head.purpose) shouldBe literals.map(Present(_))
        consumed.foreach(_.workflows.head.activities shouldBe Present(Vector.empty))
        consumed.zip(literals).foreach { case (graph, text) =>
          graph.states.head.entryActions shouldBe Present(Vector(DeclaredExpression(text, Present(Vector.empty))))
        }
      }

      "DM-05 reject null containers items records presences references IDs expressions and enums as typed values" in {
        Given("admitted core records with independently malformed projection arguments at every construction layer")
        val fixture = _fixture()
        val malformed = _null_variants(fixture)
        When("the public build receives malformed Scala inputs without exception interception")
        val results = malformed.map(_publish)
        val nullcatalog = CmlDynamicMetadata.build(null, fixture.workflows, fixture.activities, fixture.flows,
          fixture.machines, fixture.states, fixture.transitions, fixture.links, fixture.extensions)
        Then("each input returns typed Left and no partial Graph or null/match exception escapes")
        results.foreach(result => _first(result).detail.nonEmpty shouldBe true)
        _first(nullcatalog).kind shouldBe PublicationFailure
      }

      "DM-05 reject invalid qualified components and absence details without trimming Unicode or echoing input" in {
        Given("empty, Unicode-padded and ISO-control literals used in both identity components and absence details")
        val fixture = _fixture()
        val invalid = Vector("", " padded", "padded ", "\u00a0padded", "padded\u2003", "a\u0000b", "a\u0085b")
        val supplied = invalid.flatMap { text =>
          Vector(fixture.copy(flows = fixture.flows.updated(0, fixture.flows.head.copy(source = Present(ModelReference(ModelElementId(text, "id"), External))))),
            fixture.copy(flows = fixture.flows.updated(0, fixture.flows.head.copy(source = Present(ModelReference(ModelElementId("authority", text), External))))),
            fixture.copy(flows = fixture.flows.updated(0, fixture.flows.head.copy(source = Absent(NotDeclared, text)))))
        }
        When("the real factories and strict readers admit these exact literals")
        val root = _wire(fixture)
        val typedresults = supplied.map(_publish)
        val wireresults = invalid.flatMap { text =>
          Vector(CmlDynamicMetadata.read(_replace_child(root, Vector("flows", "0", "source", "value", "identity", "modelId"), JsString(text))),
            CmlDynamicMetadata.read(_replace_child(root, Vector("flows", "0", "source", "value", "identity", "elementId"), JsString(text))),
            CmlDynamicMetadata.read(_replace_child(root, Vector("flows", "0", "source"), _absent_json(NotDeclared, text))))
        }
        Then("all invalid literals fail with safe semantics diagnostics and exact generic details")
        (typedresults ++ wireresults).map(_first(_).kind).distinct shouldBe Vector(InvalidDynamicSemantics)
        (typedresults ++ wireresults).foreach(result => _first(result).detail shouldBe "The declared Dynamic reference or absence is invalid.")
      }
    }
  }

  "Dynamic control and causality" should {
    "literal roles and independent links" which {
      "DM-06 retain every control role and both causal kinds without deriving execution policy" in {
        Given("seven declared activity roles and explicit cause/reaction links with shared endpoints and cyclic flows")
        val fixture = _fixture()
        val roles: Vector[ActivityKind] = Vector(Action, Branch, Merge, Fork, Join, Start, End)
        val supplied = roles.map(role => fixture.copy(activities = fixture.activities.updated(0, fixture.activities.head.copy(activityKind = Present(role)))))
        When("every role assignment is built rendered and strictly read")
        val roots = supplied.map(_wire)
        val consumed = roots.map(root => _admitted(CmlDynamicMetadata.read(root)))
        Then("wire roles are exact lowercase and cycles duplicate endpoints repeated refs and state effects stay authored")
        roots.map(root => _at(_child(root), Vector("activities", "0", "activityKind", "value"))) shouldBe
          Vector("action", "branch", "merge", "fork", "join", "start", "end").map(JsString)
        consumed.zip(supplied).foreach { case (graph, expected) => _expect_facts(graph, expected) }
        consumed.head.causalLinks.map(_.kind) shouldBe Vector(Cause, Reaction)
        consumed.head.causalLinks.map(_.source) shouldBe Vector.fill(2)(Present(_reference("operation")))
        consumed.head.flows.map(_.source) shouldBe Vector(Present(_reference("workflow-activity:0")), Present(_reference("workflow-activity:1")))
        consumed.head.flows.map(_.target) shouldBe Vector(Present(_reference("workflow-activity:1")), Present(_reference("workflow-activity:0")))
        consumed.head.workflows.head.activities shouldBe Present(Vector(_reference("workflow-activity:0"), _reference("workflow-activity:0")))
        consumed.head.causalLinks.size shouldBe 2
      }
    }
  }

  "Dynamic publication and receiver" should {
    "base-first versioned admission" which {
      "DM-07 preserve original typed publication failures before child conflicts or malformed projections" in {
        Given("a malformed extension namespace, null catalog and invalid base root paired with an invalid child")
        val fixture = _fixture()
        val extensions = Json.obj("invalid" -> Json.obj())
        When("the base and Dynamic factories and readers admit the same invalid inputs")
        val catalog = _catalog(fixture)
        val expectedbuild = CmlSemanticMetadata.build(catalog, extensions).left.get
        val actualbuild = _publish(fixture.copy(extensions = extensions, workflows = null))
        val nullbase = CmlSemanticMetadata.build(null, fixture.extensions).left.get
        val nulldynamic = CmlDynamicMetadata.build(null, fixture.workflows, fixture.activities, fixture.flows,
          fixture.machines, fixture.states, fixture.transitions, fixture.links, fixture.extensions)
        val root = _wire(fixture) + ("schemaVersion" -> JsString("unsupported-base"))
        val expectedread = CmlSemanticMetadata.read(root).left.get
        val actualread = CmlDynamicMetadata.read(root)
        Then("each PublicationFailure retains the original typed base value and no child partial success appears")
        actualbuild.left.get.map(_.publication) shouldBe expectedbuild.map(Some(_))
        nulldynamic.left.get.map(_.publication) shouldBe nullbase.map(Some(_))
        actualread.left.get.map(_.publication) shouldBe expectedread.map(Some(_))
        (actualbuild.left.get ++ nulldynamic.left.get ++ actualread.left.get).map(_.kind).distinct shouldBe Vector(PublicationFailure)
      }

      "DM-07 reject own namespace conflict missing child and unknown child version before child shape" in {
        Given("complete declarations, a caller-supplied child and an unknown-version malformed child")
        val fixture = _fixture()
        When("the factory receives its owned key and the reader receives missing or unknown-version children")
        val conflict = _publish(fixture.copy(extensions = fixture.extensions + (namespace -> Json.obj())))
        val root = _wire(fixture)
        val missing = CmlDynamicMetadata.read(root + ("extensions" -> ((root \ "extensions").as[JsObject] - namespace)))
        val version = CmlDynamicMetadata.read(_with_child(root, Json.obj("schemaVersion" -> "unsupported", "hostile\npath" -> JsNull)))
        Then("conflict missing shape and version precedence remain distinct typed failures")
        _first(conflict).kind shouldBe ExtensionConflict
        _first(missing).kind shouldBe InvalidShape
        _first(version) shouldBe DynamicDiagnostic(UnsupportedSchemaVersion, "extensions.cozy.cml.dynamic.schemaVersion",
          "The Dynamic schema version is not supported.", None)
      }
    }

    "recursive exact wire shapes" which {
      "DM-08 reject extra missing null mixed wrong-type and wrong-case values at every typed container" in {
        Given("the independent recursive container field table and complete typed declarations")
        val fixture = _fixture()
        When("a valid envelope is built and each exact typed object or value is malformed before strict reading")
        val root = _wire(fixture)
        val mutations = _shape_mutations(root)
        val results = mutations.map(value => CmlDynamicMetadata.read(value))
        Then("all malformed typed containers fail closed with safe fixed paths and no hostile echo")
        results.foreach { result =>
          val diagnostic = _first(result)
          Set[DynamicDiagnosticKind](InvalidShape, InvalidCoreBinding, PublicationFailure).contains(diagnostic.kind) shouldBe true
          diagnostic.path.contains("hostile") shouldBe false
          diagnostic.detail.contains("hostile") shouldBe false
          diagnostic.detail.contains("/private/") shouldBe false
        }
      }

      "DM-08 reject malformed Presence at every field and nested expression reference without coercion" in {
        Given("every independently listed Presence coordinate and malformed explicit branches")
        val fixture = _fixture()
        val branches = Vector[JsValue](JsNull, JsString("present"), Json.obj(), Json.obj("status" -> "Present", "value" -> Json.arr()),
          Json.obj("status" -> "present"), Json.obj("status" -> "present", "value" -> JsNull),
          Json.obj("status" -> "present", "value" -> Json.arr(), "reason" -> "not-declared"),
          Json.obj("status" -> "absent"), Json.obj("status" -> "absent", "reason" -> "not-declared"),
          Json.obj("status" -> "absent", "detail" -> "gap"),
          Json.obj("status" -> "absent", "reason" -> JsNull, "detail" -> "gap"),
          Json.obj("status" -> "absent", "reason" -> "NOT-DECLARED", "detail" -> "gap"),
          Json.obj("status" -> "absent", "reason" -> "not-declared", "detail" -> JsNull),
          Json.obj("status" -> "absent", "reason" -> "not-declared", "detail" -> "gap", "value" -> Json.arr()))
        When("each required Presence coordinate is replaced and strictly read")
        val root = _wire(fixture)
        val results = _presence_paths.flatMap(path => branches.map(branch => CmlDynamicMetadata.read(_replace_child(root, path, branch))))
        Then("every null omitted mixed or wrong-case branch is a typed shape rejection")
        results.map(_first(_).kind).distinct shouldBe Vector(InvalidShape)
      }

      "DM-08 preserve proper escaping deterministic key sorting and opaque peer arrays while rejecting embedded forgeries" in {
        Given("literal Unicode quoted backslash identities and independent opaque peers with nested array order")
        val fixture = _fixture("作者:\"\\Aa")
        When("the supplier canonically renders parses strictly reads rerenders and rejects each forged embedded record")
        val graph = _admitted(_publish(fixture))
        val text = CmlDynamicMetadata.canonicalJson(graph)
        val root = Json.parse(text).as[JsObject]
        val consumed = _admitted(CmlDynamicMetadata.read(root))
        val canonical = CmlDynamicMetadata.canonicalJson(consumed)
        val record = _at(_child(root), Vector("workflows", "0", "element")).as[JsObject]
        val forgeries = Vector(record + ("hostile\n/private/path" -> JsNull), record - "source", record + ("source" -> JsNull))
        val failures = forgeries.map(value => CmlDynamicMetadata.read(_replace_child(root, Vector("workflows", "0", "element"), value)))
        Then("proper escaping represents exact literals and all embedded contradictions have one safe binding coordinate")
        _expect_facts(consumed, fixture)
        canonical shouldBe text
        text.contains("\\\"") shouldBe true
        text.contains("\\\\") shouldBe true
        root.fields.map(_._1) shouldBe root.fields.map(_._1).sorted
        failures.foreach(result => _first(result) shouldBe DynamicDiagnostic(InvalidCoreBinding,
          "extensions.cozy.cml.dynamic.workflows[0].element", "The embedded Dynamic record does not exactly match admitted core JSON.", None))
      }
    }
  }

  "Dynamic properties" should {
    "independent generated facts" which {
      "DM-09 preserve qualified pairs Presence boundaries and independent Catalog and seven carrier permutations" in {
        Given("active generators for valid Unicode identities, four gaps, two boundaries and eight independent orderings")
        val generator = for {
          authority <- Gen.oneOf("a:b", "A:b", "作者:\"\\", "a/b")
          suffix <- Gen.choose(0, 10000)
          reason <- Gen.oneOf(_reasons)
          boundary <- Gen.oneOf[ReferenceBoundary](Local, External)
          present <- Gen.oneOf(true, false)
          coreorder <- Gen.pick(30, (0 until 30).toVector).map(_.toVector)
          orders <- Gen.listOfN(7, Gen.oneOf(Vector(0, 1), Vector(1, 0)))
        } yield (authority + suffix, reason, boundary, present, coreorder, orders.toVector)
        val property = Prop.forAll(generator) { case (authority, reason, boundary, present, coreorder, orders) =>
          Given("one independent qualified authority, core order, carrier orders and declared Presence/boundary assignment")
          val base = _fixture(authority)
          val reference = ModelReference(ModelElementId(authority, "operation"), boundary)
          val purpose: Presence[String] = if (present) Present("Authored\n\"text\"\\条件") else Absent(reason, "No purpose represented.")
          val changed = base.copy(workflows = base.workflows.updated(0, base.workflows.head.copy(purpose = purpose)),
            links = base.links.updated(0, base.links.head.copy(source = Present(reference), rule = Absent(reason, "No rule represented."))))
          val expected = changed.copy(records = coreorder.map(changed.records),
            workflows = orders(0).map(changed.workflows), activities = orders(1).map(changed.activities), flows = orders(2).map(changed.flows),
            machines = orders(3).map(changed.machines), states = orders(4).map(changed.states),
            transitions = orders(5).map(changed.transitions), links = orders(6).map(changed.links))
          When("the generated declarations are built canonically rendered parsed read rerendered and read again")
          val graph = _admitted(_publish(expected))
          val text = CmlDynamicMetadata.canonicalJson(graph)
          val consumed = _admitted(CmlDynamicMetadata.read(Json.parse(text)))
          val canonical = CmlDynamicMetadata.canonicalJson(consumed)
          val again = _admitted(CmlDynamicMetadata.read(CmlDynamicMetadata.toJson(consumed)))
          Then("the independent expected facts survive every permutation and deterministic round trip")
          _expect_facts(consumed, expected)
          _expect_facts(again, expected)
          canonical shouldBe text
          true
        }
        When("ScalaCheck checks one hundred successful assignments without implications or discarded inputs")
        val result = Test.check(Test.Parameters.default.withMinSuccessfulTests(100), property)
        Then("the active property succeeds at least one hundred times with zero discarded cases")
        result.passed shouldBe true
        result.succeeded should be >= 100
        result.discarded shouldBe 0
      }
    }
  }

  // Fixtures and expectations below author facts only. Helpers that call supplier
  // APIs (_catalog, _publish and _wire) are invoked exclusively under When.
  private final case class Fixture(
    records: Vector[ElementRecord],
    terms: Vector[TermRecord],
    workflows: Vector[WorkflowProjection],
    activities: Vector[ActivityProjection],
    flows: Vector[FlowProjection],
    machines: Vector[StateMachineProjection],
    states: Vector[StateProjection],
    transitions: Vector[TransitionProjection],
    links: Vector[CausalLinkProjection],
    extensions: JsObject
  )

  private final case class ReferenceSlot(array: String, field: String, form: String, kinds: Vector[String]) {
    def path: Vector[String] = {
      val base = Vector(array, "0", field, "value")
      form match {
        case "single" => base
        case "many" => base :+ "0"
        case "expression" => base ++ Vector("references", "value", "0")
        case "expressions" => base ++ Vector("0", "references", "value", "0")
      }
    }

    def update(fixture: Fixture, reference: ModelReference): Fixture = {
      _reference_at(fixture, array, field, reference)
    }
  }

  private val _arrays = Vector("workflows", "activities", "flows", "stateMachines", "states", "transitions", "causalLinks")
  private val _carrier_kinds = Vector("workflow", "workflow-activity", "workflow-flow", "state-machine", "state", "transition", "causal-link")
  private val _support_kinds = Vector("entity", "value", "aggregate", "trait", "powertype", "powertype-kind", "actor",
    "component", "service", "external-system", "operation", "command", "event", "rule", "action", "unrelated")
  private val _reasons: Vector[AbsenceReason] = Vector(NotDeclared, NotRepresented, Unsupported, NotApplicable)
  private val _affected = Vector("entity", "value", "aggregate", "trait", "powertype", "powertype-kind")
  private val _participants = Vector("actor", "entity", "value", "aggregate", "component", "service", "external-system")
  private val _effects = Vector("state", "transition", "state-machine")
  private val _expression_targets = Vector("operation", "command", "event", "rule", "action", "workflow", "workflow-activity", "state-machine", "state", "transition")

  private val _reference_slots = Vector(
    ReferenceSlot("workflows", "activities", "many", Vector("workflow-activity")),
    ReferenceSlot("workflows", "flows", "many", Vector("workflow-flow")),
    ReferenceSlot("workflows", "participants", "many", _participants),
    ReferenceSlot("workflows", "affectedElements", "many", _affected),
    ReferenceSlot("workflows", "stateMachines", "many", Vector("state-machine")),
    ReferenceSlot("workflows", "operations", "many", Vector("operation", "command")),
    ReferenceSlot("workflows", "events", "many", Vector("event")),
    ReferenceSlot("workflows", "rules", "many", Vector("rule")),
    ReferenceSlot("workflows", "stateEffects", "many", _effects),
    ReferenceSlot("activities", "workflow", "single", Vector("workflow")),
    ReferenceSlot("activities", "participants", "many", _participants),
    ReferenceSlot("activities", "affectedElements", "many", _affected),
    ReferenceSlot("activities", "operations", "many", Vector("operation", "command")),
    ReferenceSlot("activities", "events", "many", Vector("event")),
    ReferenceSlot("activities", "rules", "many", Vector("rule")),
    ReferenceSlot("activities", "stateEffects", "many", _effects),
    ReferenceSlot("flows", "workflow", "single", Vector("workflow")),
    ReferenceSlot("flows", "source", "single", Vector("workflow-activity")),
    ReferenceSlot("flows", "target", "single", Vector("workflow-activity")),
    ReferenceSlot("flows", "guard", "expression", _expression_targets),
    ReferenceSlot("stateMachines", "owner", "single", Vector("entity", "value", "aggregate")),
    ReferenceSlot("stateMachines", "affectedElements", "many", _affected),
    ReferenceSlot("stateMachines", "states", "many", Vector("state")),
    ReferenceSlot("stateMachines", "transitions", "many", Vector("transition")),
    ReferenceSlot("stateMachines", "workflows", "many", Vector("workflow")),
    ReferenceSlot("states", "stateMachine", "single", Vector("state-machine")),
    ReferenceSlot("states", "entryActions", "expressions", _expression_targets),
    ReferenceSlot("states", "exitActions", "expressions", _expression_targets),
    ReferenceSlot("transitions", "stateMachine", "single", Vector("state-machine")),
    ReferenceSlot("transitions", "fromState", "single", Vector("state")),
    ReferenceSlot("transitions", "toState", "single", Vector("state")),
    ReferenceSlot("transitions", "triggers", "expressions", _expression_targets),
    ReferenceSlot("transitions", "guard", "expression", _expression_targets),
    ReferenceSlot("transitions", "actions", "expressions", _expression_targets),
    ReferenceSlot("transitions", "activities", "many", Vector("workflow-activity")),
    ReferenceSlot("transitions", "workflows", "many", Vector("workflow")),
    ReferenceSlot("transitions", "affectedElements", "many", _affected),
    ReferenceSlot("transitions", "stateEffects", "many", _effects),
    ReferenceSlot("causalLinks", "source", "single", _expression_targets),
    ReferenceSlot("causalLinks", "target", "single", _expression_targets),
    ReferenceSlot("causalLinks", "rule", "single", Vector("rule")),
    ReferenceSlot("causalLinks", "affectedElements", "many", _affected)
  )

  private val _presence_fields = Vector(
    "workflows" -> Vector("purpose", "activities", "flows", "participants", "affectedElements", "stateMachines", "operations", "events", "rules", "stateEffects"),
    "activities" -> Vector("workflow", "activityKind", "participants", "affectedElements", "operations", "events", "rules", "stateEffects"),
    "flows" -> Vector("workflow", "source", "target", "guard"),
    "stateMachines" -> Vector("owner", "affectedElements", "states", "transitions", "workflows"),
    "states" -> Vector("stateMachine", "entryActions", "exitActions"),
    "transitions" -> Vector("stateMachine", "fromState", "toState", "triggers", "guard", "actions", "activities", "workflows", "affectedElements", "stateEffects"),
    "causalLinks" -> Vector("source", "target", "rule", "affectedElements")
  )
  private val _presence_paths = _presence_fields.flatMap { case (array, fields) => fields.map(field => Vector(array, "0", field)) } ++
    _reference_slots.filter(slot => slot.form == "expression" || slot.form == "expressions").map(_.path.dropRight(2))

  private def _source(authority: String): SourceAttribution = {
    SourceAttribution(authority, "src/main/cml/dynamic.cml", Some(7))
  }

  private def _record(kind: String, id: String, authority: String = "dynamic.example"): ElementRecord = {
    ElementRecord(Present(ModelElementId(authority, id)), kind, "共有", _source(authority), Present(Vector.empty))
  }

  private def _reference(id: String, boundary: ReferenceBoundary = Local, authority: String = "dynamic.example"): ModelReference = {
    ModelReference(ModelElementId(authority, id), boundary)
  }

  private def _target_id(kind: String): String = {
    if (_carrier_kinds.contains(kind)) kind + ":0" else kind
  }

  private def _fixture(authority: String = "dynamic.example"): Fixture = {
    val term = TermRecord(TermId(authority, "workflow:0"), _source(authority))
    val semantic = SemanticReference(TermTarget(term.identity), Local, Present(RelationId("bok.example", "cause")),
      Present(ProfileId("bok.example", "dynamic")), Present("Independent core grounding."),
      Present(LocalizedLabel("共有", Some("ja"))), _source(authority), Declared)
    val records = (_carrier_kinds.flatMap(kind => Vector(0, 1).map(index => _record(kind, kind + ":" + index, authority))) ++
      _support_kinds.map(kind => _record(kind, kind, authority))).updated(0,
        _record("workflow", "workflow:0", authority).copy(references = Present(Vector(semantic))))
    def _single_(id: String): Presence[ModelReference] = { Present(_reference(id, Local, authority)) }
    def _many_(ids: String*): Presence[Vector[ModelReference]] = { Present(ids.toVector.map(id => _reference(id, Local, authority))) }
    val expression = DeclaredExpression("条件\n\"quoted\"\\literal", _many_("event", "rule", "operation", "event"))
    val workflows = Vector(0, 1).map(index => WorkflowProjection(records(index), Present("Purpose\n共有"),
      _many_("workflow-activity:0", "workflow-activity:0"), _many_("workflow-flow:0", "workflow-flow:1"),
      _many_("actor"), _many_("entity"), _many_("state-machine:0"), _many_("operation", "command"),
      _many_("event"), _many_("rule"), _many_("state:0", "transition:0", "state-machine:0")))
    val activities = Vector(0, 1).map(index => ActivityProjection(records(2 + index), _single_("workflow:0"),
      Present(if (index == 0) Branch else Merge), _many_("actor"), _many_("entity"), _many_("operation"),
      _many_("event"), _many_("rule"), _many_("state:0")))
    val flows = Vector(0, 1).map(index => FlowProjection(records(4 + index), _single_("workflow:0"),
      _single_("workflow-activity:" + index), _single_("workflow-activity:" + (1 - index)), Present(expression)))
    val machines = Vector(0, 1).map(index => StateMachineProjection(records(6 + index), _single_("entity"),
      _many_("entity", "trait"), _many_("state:0", "state:1"), _many_("transition:0", "transition:1"), _many_("workflow:0")))
    val states = Vector(0, 1).map(index => StateProjection(records(8 + index), _single_("state-machine:0"),
      Present(Vector(expression)), Present(Vector(expression))))
    val transitions = Vector(0, 1).map(index => TransitionProjection(records(10 + index), _single_("state-machine:0"),
      _single_("state:0"), _single_("state:0"), Present(Vector(expression)), Present(expression), Present(Vector(expression)),
      _many_("workflow-activity:0"), _many_("workflow:0"), _many_("entity"), _many_("state:0")))
    val links = Vector(0, 1).map(index => CausalLinkProjection(records(12 + index), if (index == 0) Cause else Reaction,
      _single_("operation"), _single_("event"), _single_("rule"), _many_("entity")))
    Fixture(records, Vector(term), workflows, activities, flows, machines, states, transitions, links,
      Json.obj("org.example.peer" -> Json.obj("z" -> Json.arr(JsNull, "literal", Json.obj("β" -> "retained")), "a" -> true),
        "cozy.cml.structure" -> Json.obj("opaque" -> "unchanged-structure-peer"),
        "cozy.cml.classification" -> Json.obj("opaque" -> "unchanged-classification-peer")))
  }

  private def _catalog(fixture: Fixture): Catalog = {
    CmlSemanticFoundation.build(fixture.records, fixture.terms) match {
      case Right(catalog) => catalog
      case Left(diagnostics) => fail("Expected admitted fixture Catalog: " + diagnostics)
    }
  }

  private def _publish(fixture: Fixture): Either[Vector[DynamicDiagnostic], Graph] = {
    CmlDynamicMetadata.build(_catalog(fixture), fixture.workflows, fixture.activities, fixture.flows, fixture.machines,
      fixture.states, fixture.transitions, fixture.links, fixture.extensions)
  }

  private def _wire(fixture: Fixture): JsObject = {
    CmlDynamicMetadata.toJson(_admitted(_publish(fixture)))
  }

  private def _resource(name: String): JsValue = {
    val stream = Option(getClass.getResourceAsStream("/cozy/modeler/" + name)).getOrElse(fail("Expected predecessor resource."))
    val source = Source.fromInputStream(stream, "UTF-8")
    try { Json.parse(source.mkString) } finally { source.close() }
  }

  private def _admitted(result: Either[Vector[DynamicDiagnostic], Graph]): Graph = {
    result match {
      case Right(graph) => graph
      case Left(diagnostics) => fail("Expected admitted Dynamic graph: " + diagnostics)
    }
  }

  private def _first(result: Either[Vector[DynamicDiagnostic], Graph]): DynamicDiagnostic = {
    result match {
      case Left(diagnostics) if diagnostics.nonEmpty => diagnostics.head
      case _ => fail("Expected typed Dynamic rejection.")
    }
  }

  private def _expect_facts(graph: Graph, expected: Fixture): Unit = {
    graph.catalog.elements shouldBe expected.records
    graph.catalog.terms shouldBe expected.terms
    graph.workflows shouldBe expected.workflows
    graph.activities shouldBe expected.activities
    graph.flows shouldBe expected.flows
    graph.stateMachines shouldBe expected.machines
    graph.states shouldBe expected.states
    graph.transitions shouldBe expected.transitions
    graph.causalLinks shouldBe expected.links
    (graph.envelope.extensions - namespace) shouldBe expected.extensions
  }

  private def _with_absence(fixture: Fixture, absence: Absent): Fixture = {
    fixture.copy(
      workflows = fixture.workflows.map(value => value.copy(purpose = absence, activities = absence, flows = absence,
        participants = absence, affectedElements = absence, stateMachines = absence, operations = absence,
        events = absence, rules = absence, stateEffects = absence)),
      activities = fixture.activities.map(value => value.copy(workflow = absence, activityKind = absence, participants = absence,
        affectedElements = absence, operations = absence, events = absence, rules = absence, stateEffects = absence)),
      flows = fixture.flows.map(value => value.copy(workflow = absence, source = absence, target = absence, guard = absence)),
      machines = fixture.machines.map(value => value.copy(owner = absence, affectedElements = absence, states = absence,
        transitions = absence, workflows = absence)),
      states = fixture.states.map(value => value.copy(stateMachine = absence, entryActions = absence, exitActions = absence)),
      transitions = fixture.transitions.map(value => value.copy(stateMachine = absence, fromState = absence, toState = absence,
        triggers = absence, guard = absence, actions = absence, activities = absence, workflows = absence,
        affectedElements = absence, stateEffects = absence)),
      links = fixture.links.map(value => value.copy(source = absence, target = absence, rule = absence, affectedElements = absence))
    )
  }

  private def _with_expression(fixture: Fixture, expression: DeclaredExpression): Fixture = {
    fixture.copy(flows = fixture.flows.map(_.copy(guard = Present(expression))),
      states = fixture.states.map(_.copy(entryActions = Present(Vector(expression)), exitActions = Present(Vector(expression)))),
      transitions = fixture.transitions.map(_.copy(triggers = Present(Vector(expression)), guard = Present(expression), actions = Present(Vector(expression)))))
  }

  private def _with_empty_vectors(
    fixture: Fixture,
    references: Vector[ModelReference] = Vector.empty,
    expressions: Vector[DeclaredExpression] = Vector.empty
  ): Fixture = {
    val refs = Present(references)
    val exprs = Present(expressions)
    fixture.copy(workflows = fixture.workflows.map(_.copy(activities = refs, flows = refs, participants = refs,
      affectedElements = refs, stateMachines = refs, operations = refs, events = refs, rules = refs, stateEffects = refs)),
      activities = fixture.activities.map(_.copy(participants = refs, affectedElements = refs, operations = refs,
        events = refs, rules = refs, stateEffects = refs)),
      machines = fixture.machines.map(_.copy(affectedElements = refs, states = refs, transitions = refs, workflows = refs)),
      states = fixture.states.map(_.copy(entryActions = exprs, exitActions = exprs)),
      transitions = fixture.transitions.map(_.copy(triggers = exprs, actions = exprs, activities = refs, workflows = refs,
        affectedElements = refs, stateEffects = refs)), links = fixture.links.map(_.copy(affectedElements = refs)))
  }

  private def _anonymous(fixture: Fixture): Fixture = {
    val absent = _with_absence(fixture, Absent(NotDeclared, "No dynamic payload declared."))
    val records = fixture.records.map { record =>
      if (_carrier_kinds.contains(record.kind)) record.copy(identity = Absent(NotRepresented, "No stable dynamic identity represented."),
        references = Present(Vector.empty)) else record
    }
    def _record_(kind: String): ElementRecord = { records.find(_.kind == kind).get }
    absent.copy(records = records,
      workflows = absent.workflows.map(_.copy(element = _record_("workflow"))),
      activities = absent.activities.map(_.copy(element = _record_("workflow-activity"))),
      flows = absent.flows.map(_.copy(element = _record_("workflow-flow"))),
      machines = absent.machines.map(_.copy(element = _record_("state-machine"))),
      states = absent.states.map(_.copy(element = _record_("state"))),
      transitions = absent.transitions.map(_.copy(element = _record_("transition"))),
      links = absent.links.map(_.copy(element = _record_("causal-link"))))
  }

  private def _carrier_variants(fixture: Fixture, count: Int): Vector[Fixture] = {
    def _items_[A](items: Vector[A]): Vector[A] = {
      count match {
        case -1 => null
        case -2 => Vector(null.asInstanceOf[A])
        case 1 => items.take(1)
        case 3 => items :+ items.head
      }
    }
    Vector(fixture.copy(workflows = _items_(fixture.workflows)), fixture.copy(activities = _items_(fixture.activities)),
      fixture.copy(flows = _items_(fixture.flows)), fixture.copy(machines = _items_(fixture.machines)),
      fixture.copy(states = _items_(fixture.states)), fixture.copy(transitions = _items_(fixture.transitions)), fixture.copy(links = _items_(fixture.links)))
  }

  private def _projection_record_variants(fixture: Fixture, change: ElementRecord => ElementRecord): Vector[Fixture] = {
    Vector(fixture.copy(workflows = fixture.workflows.updated(0, fixture.workflows.head.copy(element = change(fixture.workflows.head.element)))),
      fixture.copy(activities = fixture.activities.updated(0, fixture.activities.head.copy(element = change(fixture.activities.head.element)))),
      fixture.copy(flows = fixture.flows.updated(0, fixture.flows.head.copy(element = change(fixture.flows.head.element)))),
      fixture.copy(machines = fixture.machines.updated(0, fixture.machines.head.copy(element = change(fixture.machines.head.element)))),
      fixture.copy(states = fixture.states.updated(0, fixture.states.head.copy(element = change(fixture.states.head.element)))),
      fixture.copy(transitions = fixture.transitions.updated(0, fixture.transitions.head.copy(element = change(fixture.transitions.head.element)))),
      fixture.copy(links = fixture.links.updated(0, fixture.links.head.copy(element = change(fixture.links.head.element)))))
  }

  private def _null_variants(fixture: Fixture): Vector[Fixture] = {
    val badreferences = Vector(null, ModelReference(null, Local), ModelReference(ModelElementId(null, "id"), Local),
      ModelReference(ModelElementId("authority", null), Local), ModelReference(ModelElementId("authority", "id"), null))
    _carrier_variants(fixture, -1) ++ _carrier_variants(fixture, -2) ++
      _projection_record_variants(fixture, _ => null) ++
      _projection_record_variants(fixture, _.copy(identity = null)) ++
      _projection_record_variants(fixture, _.copy(kind = null)) ++
      _projection_record_variants(fixture, _.copy(name = null)) ++
      _projection_record_variants(fixture, _.copy(source = null)) ++
      _projection_record_variants(fixture, _.copy(references = null)) ++
      _reference_slots.flatMap(slot => badreferences.map(reference => slot.update(fixture, reference))) ++
      Vector(_with_absence(fixture, null), _with_absence(fixture, Absent(null, "No reason.")),
        _with_absence(fixture, Absent(NotDeclared, null)), _with_empty_vectors(fixture, null, null),
        _with_empty_vectors(fixture, Vector(null), Vector(null)), _with_expression(fixture, null),
        _with_expression(fixture, DeclaredExpression(null, Present(Vector.empty))),
        _with_expression(fixture, DeclaredExpression("text", null)),
        _with_expression(fixture, DeclaredExpression("text", Present(null))),
        fixture.copy(workflows = fixture.workflows.map(_.copy(purpose = Present(null)))),
        fixture.copy(activities = fixture.activities.map(_.copy(activityKind = Present(null)))),
        fixture.copy(links = fixture.links.map(_.copy(kind = null))), fixture.copy(extensions = null))
  }

  private def _reference_at(fixture: Fixture, array: String, field: String, reference: ModelReference): Fixture = {
    val single = Present(reference)
    val many = Present(Vector(reference))
    val expression = Present(DeclaredExpression("Authored literal", many))
    val expressions = Present(Vector(DeclaredExpression("Authored literal", many)))
    array match {
      case "workflows" =>
        val value = fixture.workflows.head
        val changed = field match {
          case "activities" => value.copy(activities = many)
          case "flows" => value.copy(flows = many)
          case "participants" => value.copy(participants = many)
          case "affectedElements" => value.copy(affectedElements = many)
          case "stateMachines" => value.copy(stateMachines = many)
          case "operations" => value.copy(operations = many)
          case "events" => value.copy(events = many)
          case "rules" => value.copy(rules = many)
          case "stateEffects" => value.copy(stateEffects = many)
        }
        fixture.copy(workflows = fixture.workflows.updated(0, changed))
      case "activities" =>
        val value = fixture.activities.head
        val changed = field match {
          case "workflow" => value.copy(workflow = single)
          case "participants" => value.copy(participants = many)
          case "affectedElements" => value.copy(affectedElements = many)
          case "operations" => value.copy(operations = many)
          case "events" => value.copy(events = many)
          case "rules" => value.copy(rules = many)
          case "stateEffects" => value.copy(stateEffects = many)
        }
        fixture.copy(activities = fixture.activities.updated(0, changed))
      case "flows" =>
        val value = fixture.flows.head
        val changed = field match {
          case "workflow" => value.copy(workflow = single)
          case "source" => value.copy(source = single)
          case "target" => value.copy(target = single)
          case "guard" => value.copy(guard = expression)
        }
        fixture.copy(flows = fixture.flows.updated(0, changed))
      case "stateMachines" =>
        val value = fixture.machines.head
        val changed = field match {
          case "owner" => value.copy(owner = single)
          case "affectedElements" => value.copy(affectedElements = many)
          case "states" => value.copy(states = many)
          case "transitions" => value.copy(transitions = many)
          case "workflows" => value.copy(workflows = many)
        }
        fixture.copy(machines = fixture.machines.updated(0, changed))
      case "states" =>
        val value = fixture.states.head
        val changed = field match {
          case "stateMachine" => value.copy(stateMachine = single)
          case "entryActions" => value.copy(entryActions = expressions)
          case "exitActions" => value.copy(exitActions = expressions)
        }
        fixture.copy(states = fixture.states.updated(0, changed))
      case "transitions" =>
        val value = fixture.transitions.head
        val changed = field match {
          case "stateMachine" => value.copy(stateMachine = single)
          case "fromState" => value.copy(fromState = single)
          case "toState" => value.copy(toState = single)
          case "triggers" => value.copy(triggers = expressions)
          case "guard" => value.copy(guard = expression)
          case "actions" => value.copy(actions = expressions)
          case "activities" => value.copy(activities = many)
          case "workflows" => value.copy(workflows = many)
          case "affectedElements" => value.copy(affectedElements = many)
          case "stateEffects" => value.copy(stateEffects = many)
        }
        fixture.copy(transitions = fixture.transitions.updated(0, changed))
      case "causalLinks" =>
        val value = fixture.links.head
        val changed = field match {
          case "source" => value.copy(source = single)
          case "target" => value.copy(target = single)
          case "rule" => value.copy(rule = single)
          case "affectedElements" => value.copy(affectedElements = many)
        }
        fixture.copy(links = fixture.links.updated(0, changed))
    }
  }

  private def _child(root: JsObject): JsObject = {
    (root \ "extensions" \ namespace).as[JsObject]
  }

  private def _with_child(root: JsObject, child: JsValue): JsObject = {
    root + ("extensions" -> ((root \ "extensions").as[JsObject] + (namespace -> child)))
  }

  private def _replace_child(root: JsObject, path: Vector[String], replacement: JsValue): JsObject = {
    _with_child(root, _replace_json(_child(root), path, replacement))
  }

  private def _at(value: JsValue, path: Vector[String]): JsValue = {
    path.foldLeft(value) { (current, part) => current match {
      case objectvalue: JsObject => objectvalue.value(part)
      case JsArray(values) => values(part.toInt)
      case _ => fail("Invalid fixture coordinate.")
    }}
  }

  private def _replace_json(value: JsValue, path: Vector[String], replacement: JsValue): JsValue = {
    if (path.isEmpty) replacement else value match {
      case objectvalue: JsObject => objectvalue + (path.head -> _replace_json(objectvalue.value(path.head), path.tail, replacement))
      case JsArray(values) => JsArray(values.updated(path.head.toInt, _replace_json(values(path.head.toInt), path.tail, replacement)))
      case _ => fail("Invalid fixture replacement coordinate.")
    }
  }

  private def _present_json(value: JsValue): JsObject = { Json.obj("status" -> "present", "value" -> value) }

  private def _absent_json(reason: AbsenceReason, detail: String): JsObject = {
    val tag = reason match {
      case NotDeclared => "not-declared"
      case NotRepresented => "not-represented"
      case Unsupported => "unsupported"
      case NotApplicable => "not-applicable"
    }
    Json.obj("status" -> "absent", "reason" -> tag, "detail" -> detail)
  }

  private def _reference_json(reference: ModelReference): JsObject = {
    Json.obj("identity" -> Json.obj("modelId" -> reference.identity.modelId, "elementId" -> reference.identity.elementId),
      "boundary" -> (reference.boundary match { case Local => "local"; case External => "external" }))
  }

  private def _shape_mutations(root: JsObject): Vector[JsValue] = {
    val childpath = Vector("extensions", namespace)
    val projectionfields = _presence_fields.map { case (array, fields) =>
      val expected = if (array == "causalLinks") Vector("element", "kind") ++ fields else "element" +: fields
      (childpath ++ Vector(array, "0"), expected)
    }
    val presencefields = _presence_paths.map(path => (childpath ++ path, Vector("status", "value")))
    val referencefields = _reference_slots.flatMap { slot => Vector(
      (childpath ++ slot.path, Vector("identity", "boundary")),
      (childpath ++ slot.path :+ "identity", Vector("modelId", "elementId")))
    }
    val expressionfields = _reference_slots.filter(slot => slot.form == "expression" || slot.form == "expressions").map { slot =>
      (childpath ++ slot.path.dropRight(3), Vector("text", "references"))
    }
    val embeddedfields = _arrays.map(array => (childpath ++ Vector(array, "0", "element"), Vector("identity", "kind", "name", "source", "references")))
    val objects = Vector((Vector.empty[String], Vector("schemaVersion", "elements", "terms", "extensions")),
      (childpath, "schemaVersion" +: _arrays)) ++ projectionfields ++ presencefields ++ referencefields ++ expressionfields ++ embeddedfields
    val objectmutations = objects.flatMap { case (path, fields) =>
      val value = _at(root, path).as[JsObject]
      Vector(_replace_json(root, path, value + ("hostile\n/private/path" -> JsNull)),
        _replace_json(root, path, JsNull), _replace_json(root, path, JsBoolean(true))) ++
        fields.flatMap(field => Vector(_replace_json(root, path, value - field),
          _replace_json(root, path :+ field, JsNull)))
    }
    val arrays = Vector(Vector("elements"), Vector("terms")) ++ _arrays.map(array => childpath :+ array)
    val arraymutations = arrays.flatMap(path => Vector(_replace_json(root, path, Json.obj()),
      _replace_json(root, path, JsString("array")), _replace_json(root, path :+ "0", JsNull)))
    val scalarcases = Vector(
      Vector("activities", "0", "activityKind", "value") -> JsString("Branch"),
      Vector("activities", "0", "activityKind", "value") -> JsBoolean(true),
      Vector("causalLinks", "0", "kind") -> JsString("Cause"),
      Vector("causalLinks", "0", "kind") -> JsBoolean(true),
      Vector("workflows", "0", "purpose", "value") -> JsBoolean(true),
      Vector("flows", "0", "guard", "value", "text") -> JsBoolean(true)) ++
      _reference_slots.flatMap(slot => Vector((slot.path :+ "boundary") -> JsString("Local"),
        (slot.path ++ Vector("identity", "modelId")) -> JsBoolean(true),
        (slot.path ++ Vector("identity", "elementId")) -> JsBoolean(true)))
    objectmutations ++ arraymutations ++ scalarcases.map { case (path, replacement) => _replace_child(root, path, replacement) } ++ Vector(null)
  }
}
