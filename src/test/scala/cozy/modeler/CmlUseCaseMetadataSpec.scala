package cozy.modeler

import org.scalacheck.{Gen, Prop, Test}
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import play.api.libs.json.{JsArray, JsBoolean, JsNull, JsObject, JsString, JsValue, Json}
import scala.io.Source

import CmlSemanticFoundation._
import CmlDynamicMetadata.{DeclaredExpression, ModelReference}
import CmlUseCaseMetadata._

/*
 * @since   Oct.  4, 2026
 * @version Oct.  4, 2026
 * @author  ASAMI, Tomoharu
 */
final class CmlUseCaseMetadataSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  "Use Case declared facts" should {
    "complete source declarations" which {
      "UC-01 retain every Actor Use Case participation flow core and separate Term fact with literal wire equality" in {
        Given("two separately qualified same-named Actors and Use Cases with independently authored complete declarations and wire facts")
        val fixture = _fixture()
        val expected = _expected_child(fixture)
        When("the producer admits renders and the strict reader consumes the complete envelope")
        val graph = _admitted(_publish(fixture))
        val root = CmlUseCaseMetadata.toJson(graph)
        val consumed = _admitted(CmlUseCaseMetadata.read(root))
        val canonical = CmlUseCaseMetadata.canonicalJson(graph)
        Then("all source facts and the complete independently authored child agree literally")
        _expect_facts(consumed, fixture)
        _child(root) shouldBe expected
        Json.parse(canonical) shouldBe root
        consumed.catalog.elements.map(_.name).distinct shouldBe Vector("共有")
        consumed.catalog.terms shouldBe Vector(TermRecord(TermId("supplier.example", "use-case:0"), _source("supplier.example")))
        (root \ "schemaVersion").as[String] shouldBe "cozy.cml.semantic-metadata.v1"
      }

      "UC-02 preserve authored roles flows duplicates and exact authority case Unicode and delimiter distinctions" in {
        Given("qualified pairs whose display name and declared ID deliberately do not distinguish them")
        val fixture = _gaps(_fixture())
        val ids = Vector(ModelElementId("a:b", "c"), ModelElementId("a", "b:c"), ModelElementId("A:b", "c"),
          ModelElementId("a:b", "C"), ModelElementId("é", "事"), ModelElementId("e\u0301", "事"))
        val actors = ids.map(id => fixture.actors.head.copy(element = _record("actor", id.elementId, id.modelId)))
        val cases = ids.map(id => fixture.usecases.head.copy(element = _record("use-case", id.elementId + ":use-case", id.modelId), declaredId = Present("same")))
        val supplied = fixture.copy(records = actors.map(_.element) ++ cases.map(_.element), actors = actors, usecases = cases,
          terms = Vector(TermRecord(TermId("a:b", "c"), _source("a:b"))))
        When("qualified declarations and the complete ordered fixture are independently admitted and consumed")
        val consumed = _admitted(CmlUseCaseMetadata.read(_wire(supplied)))
        val ordered = _admitted(CmlUseCaseMetadata.read(_wire(_fixture())))
        val lookups = ids.map(consumed.catalog.element)
        val term = consumed.catalog.term(TermId("a:b", "c"))
        val namefallback = consumed.catalog.element(ModelElementId("a:b", "共有"))
        val declaredfallback = consumed.catalog.element(ModelElementId("a:b", "same"))
        Then("no descriptive fact becomes identity and no role flow or nested duplicate is inferred away")
        _expect_facts(consumed, supplied)
        lookups shouldBe actors.map(value => Right(value.element))
        term shouldBe Right(supplied.terms.head)
        namefallback.isLeft shouldBe true
        declaredfallback.isLeft shouldBe true
        ordered.useCases.head.actorReferences shouldBe Present(Vector(
          ActorParticipation(Primary, _ref("actor:0")), ActorParticipation(Secondary, _ref("actor:0")),
          ActorParticipation(Supporting, _ref("unknown", External)), ActorParticipation(Stakeholder, _ref("actor:0")),
          ActorParticipation(Actor, _ref("actor:0")), ActorParticipation(Primary, _ref("actor:0"))))
        ordered.useCases.head.flows shouldBe Present(_flows())
        ordered.actors.head.useCases shouldBe Present(Vector(_ref("use-case:0"), _ref("use-case:0"), _ref("use-case:0", External)))
      }
    }

    "explicit availability" which {
      "UC-04 preserve all four reasons details explicit empty values and equal anonymous occurrence payloads" in {
        Given("complete gaps and equal anonymous core occurrences with independently retained payload order")
        val fixture = _anonymous(_gaps(_fixture()))
        val empty = _empty(_fixture())
        When("both availability modes are built rendered and strictly received and anonymous IDs are queried")
        val consumed = _admitted(CmlUseCaseMetadata.read(_wire(fixture)))
        val empties = _admitted(CmlUseCaseMetadata.read(_wire(empty)))
        val unknown = consumed.catalog.element(ModelElementId("supplier.example", "actor:0"))
        Then("absence remains distinct from empty and anonymous equal records retain separate occurrences")
        _expect_facts(consumed, fixture)
        _expect_facts(empties, empty)
        consumed.actors.map(_.element).distinct shouldBe Vector(fixture.actors.head.element)
        consumed.useCases.map(_.element).distinct shouldBe Vector(fixture.usecases.head.element)
        consumed.actors.map(_.summary) shouldBe Vector(_gap(1), _gap(2))
        consumed.useCases.map(_.element.identity) shouldBe Vector.fill(2)(Absent(NotRepresented, "No stable identity represented."))
        unknown.isLeft shouldBe true
        empties.actors.head.actorKind shouldBe Present("")
        empties.useCases.head.trigger shouldBe Present(DeclaredExpression("", Present(Vector.empty)))
        empties.useCases.head.flows shouldBe Present(Vector.empty)
      }

      "UC-04 retain explicit gaps at every flow field and expression-reference site without synthesizing flow content" in {
        Given("each absence reason independently assigned to flow Presence fields and nested expression references")
        val fixture = _fixture()
        val variants = (0 until 4).map { ordinal =>
          val gap = _gap(ordinal)
          val expression = DeclaredExpression("Literal remains declared.", gap)
          val flows = Vector(MainFlow, AlternateFlow, ExceptionFlow).map(kind => UseCaseFlow(kind, gap, gap, gap, gap, gap, gap, gap))
          fixture.copy(usecases = fixture.usecases.zipWithIndex.map { case (value, index) => value.copy(
            trigger = Present(expression), preconditions = Present(Vector(expression)), postconditions = Present(Vector(expression)),
            flows = Present(if (index == 0) flows else _flows().map(_.copy(condition = Present(expression),
              steps = Present(Vector(expression)), alternates = Present(Vector(expression)), exceptions = Present(Vector(expression)))))) })
        }
        val emptyflows = Vector(MainFlow, AlternateFlow, ExceptionFlow).map(kind => UseCaseFlow(kind, Present(""), Present(""), Present(""),
          Present(DeclaredExpression("", Present(Vector.empty))), Present(Vector.empty), Present(Vector.empty), Present(Vector.empty)))
        val empty = fixture.copy(usecases = fixture.usecases.map(_.copy(flows = Present(emptyflows))))
        When("each nested availability declaration is admitted rendered and strictly consumed")
        val results = variants.map(value => _admitted(CmlUseCaseMetadata.read(_wire(value))))
        val empties = _admitted(CmlUseCaseMetadata.read(_wire(empty)))
        Then("direct flow kinds and every declared text or gap remain exact with no invented steps or references")
        results.zip(variants).foreach { case (graph, expected) => _expect_facts(graph, expected) }
        _expect_facts(empties, empty)
        empties.useCases.head.flows shouldBe Present(emptyflows)
      }
    }
  }

  "Use Case reference admission" should {
    "every declared reference site" which {
      "UC-03 accept every permitted Local kind and retain unknown or locally equal External pairs at every site" in {
        Given("an independent field table covering Actor links participation and every expression location")
        val fixture = _fixture()
        val locals = _slots.flatMap(slot => slot.kinds.map(kind => slot.update(fixture, _ref(_target_id(kind)))))
        val externals = _slots.flatMap(slot => Vector(_ref("unknown", External), _ref("entity:target", External)).map(slot.update(fixture, _)))
        When("each declared target variant is admitted rendered and strictly read")
        val results = (locals ++ externals).map(supplied => _admitted(CmlUseCaseMetadata.read(_wire(supplied))))
        Then("all and only declared references remain literal including External pairs matching Local records of another kind")
        results.zip(locals ++ externals).foreach { case (graph, expected) => _expect_facts(graph, expected) }
      }

      "UC-03 reject dangling and every forbidden Local kind through both typed and strict wire routes" in {
        Given("every reference site with all catalog kinds outside its permitted set and an unknown qualified pair")
        val fixture = _fixture()
        val variants = _slots.flatMap { slot =>
          val wrong = _kinds.filterNot(slot.kinds.contains).map(kind => _ref(_target_id(kind))) :+ _ref("unknown")
          wrong.map(reference => (slot, reference))
        }
        When("the factory and reader interpret each forbidden target without name or Term fallback")
        val root = _wire(fixture)
        val typed = variants.map { case (slot, reference) => _first(_publish(slot.update(fixture, reference))) }
        val decoded = variants.map { case (slot, reference) =>
          _first(CmlUseCaseMetadata.read(_replace_child(root, slot.path, _reference_wire(reference))))
        }
        Then("each failure has typed semantics a fixed field coordinate safe detail and no publication diagnostic")
        typed.zip(variants).foreach { case (diagnostic, (slot, _)) =>
          _expect_failure(diagnostic, InvalidUseCaseSemantics, _coordinate(slot.path), _semantic_detail)
        }
        decoded.zip(variants).foreach { case (diagnostic, (slot, _)) =>
          _expect_failure(diagnostic, InvalidUseCaseSemantics, _coordinate(slot.path), _semantic_detail)
        }
      }

      "UC-03 refuse to coerce a separate Term into a model expression target" in {
        Given("a Term whose qualified pair has no corresponding model record")
        val fixture = _fixture().copy(terms = Vector(TermRecord(TermId("supplier.example", "term-only"), _source("supplier.example"))))
        val supplied = _slots.filter(_.expression).map(_.update(fixture, _ref("term-only")))
        When("all expression sites declare that Term-only pair as a Local model reference")
        val results = supplied.map(value => _first(_publish(value)))
        Then("a vocabulary identity cannot satisfy a model-element requirement")
        results.foreach(diagnostic => diagnostic.kind shouldBe InvalidUseCaseSemantics)
      }
    }
  }

  "Use Case core admission" should {
    "complete occurrence bindings" which {
      "UC-05 reject missing and excess Actor and Use Case occurrences in declared and anonymous modes on both routes" in {
        Given("complete declared and anonymous Catalog multisets")
        val fixtures = Vector(_fixture(), _anonymous(_gaps(_fixture())))
        When("both factories and readers receive incomplete or excessive per-kind arrays")
        val results = fixtures.flatMap { fixture =>
          val root = _wire(fixture)
          Vector("actors", "useCases").flatMap { array =>
            val values = (_child(root) \ array).as[Vector[JsValue]]
            Vector(1 -> MissingProjection, 3 -> DuplicateProjection).flatMap { case (count, kind) =>
              val supplied = if (array == "actors") fixture.copy(actors = if (count == 1) fixture.actors.take(1) else fixture.actors :+ fixture.actors.head)
                else fixture.copy(usecases = if (count == 1) fixture.usecases.take(1) else fixture.usecases :+ fixture.usecases.head)
              val changed = if (count == 1) values.take(1) else values :+ values.head
              Vector(_first(_publish(supplied)), _first(CmlUseCaseMetadata.read(_replace_child(root, Vector(array), JsArray(changed)))))
                .map(diagnostic => (diagnostic, kind, array))
            }
          }
        }
        Then("full-record multiplicity is required independently of addressability or carrier order")
        results.foreach { case (diagnostic, kind, array) =>
          diagnostic.kind shouldBe kind
          diagnostic.path should startWith(array + "[")
          diagnostic.publication shouldBe None
          diagnostic.detail shouldBe (if (kind == MissingProjection) _missing_detail else _duplicate_detail)
        }
      }

      "UC-05 reject forged full identity kind name source and core references for both projection families" in {
        Given("independent changes to each full core field while retaining the other fields")
        val fixture = _fixture()
        val changes: Vector[ElementRecord => ElementRecord] = Vector(
          record => record.copy(identity = Present(ModelElementId("supplier.example", "forged"))),
          record => record.copy(kind = "entity"), record => record.copy(name = "forged"),
          record => record.copy(source = record.source.copy(path = "other.cml")),
          record => record.copy(references = Absent(NotDeclared, "Different reference declaration.")))
        When("each contradiction is offered to typed binding and strict embedded-core correspondence")
        val root = _wire(fixture)
        val results = Vector("actors", "useCases").flatMap { array => changes.flatMap { change =>
          val record = if (array == "actors") fixture.actors.head.element else fixture.usecases.head.element
          val changed = change(record)
          val supplied = if (array == "actors") fixture.copy(actors = fixture.actors.updated(0, fixture.actors.head.copy(element = changed)))
            else fixture.copy(usecases = fixture.usecases.updated(0, fixture.usecases.head.copy(element = changed)))
          Vector((_first(_publish(supplied)), array + "[0].element", _binding_detail),
            (_first(CmlUseCaseMetadata.read(_replace_child(root, Vector(array, "0", "element"), _core_wire(changed)))),
              _wire_coordinate(Vector(array, "0", "element")), _wire_binding_detail))
        }}
        Then("an identity name or partial record cannot substitute for the complete admitted core record")
        results.foreach { case (diagnostic, path, detail) => _expect_failure(diagnostic, InvalidCoreBinding, path, detail) }
      }
    }

    "malformed typed declarations" which {
      "UC-05 reject null arguments projections full-record members Presence vectors and nested items without exceptions" in {
        Given("typed null variants at every required field and nested collection")
        val fixture = _fixture()
        val variants = _null_variants(fixture)
        When("the facade receives malformed declarations and null top-level catalog or extensions")
        val results = variants.map(value => _first(_publish(value)))
        val catalog = _catalog(fixture)
        val top = Vector(CmlUseCaseMetadata.build(null, fixture.actors, fixture.usecases, fixture.extensions),
          CmlUseCaseMetadata.build(catalog, fixture.actors, fixture.usecases, null)).map(_first)
        Then("every expected input failure is typed with fixed coordinates and publication failures retain their original cause")
        results.foreach { diagnostic =>
          Set[UseCaseDiagnosticKind](InvalidShape, InvalidCoreBinding, InvalidUseCaseSemantics) should contain(diagnostic.kind)
          diagnostic.publication shouldBe None
          diagnostic.path should fullyMatch regex "(actors|useCases)(\\[[0-9]+\\])?(\\.[A-Za-z]+|\\[[0-9]+\\])*"
        }
        top.map(_.kind) shouldBe Vector(PublicationFailure, PublicationFailure)
        top.map(_.path) shouldBe Vector("catalog", "extensions")
        top.foreach(diagnostic => diagnostic.publication.isDefined shouldBe true)
      }
    }
  }

  "Use Case envelope admission" should {
    "unchanged peers" which {
      "UC-06 preserve admitted Foundation Structure Classification Dynamic and every original opaque peer" in {
        Given("the accepted Dynamic resource as an unchanged-prefix oracle and separate new declarations with explicit gaps")
        val fixture = _gaps(_fixture())
        val addedpeer = Json.obj("ordered" -> Json.arr("z", "a", JsNull), "empty" -> "", "flag" -> false)
        When("the predecessor resource and its peers are admitted independently and Use Case metadata is added")
        val baselinejson = _resource("dynamic-metadata-v1-declared.json")
        val baseline = CmlDynamicMetadata.read(baselinejson).right.get
        val oldstructure = CmlStructureMetadata.read(baselinejson).right.get
        val oldclassification = CmlClassificationMetadata.read(baselinejson).right.get
        val supplied = fixture.copy(records = baseline.catalog.elements ++ fixture.records.filter(record => Set("actor", "use-case")(record.kind)),
          terms = baseline.catalog.terms ++ fixture.terms, extensions = baseline.envelope.extensions + ("org.example.use-case-peer" -> addedpeer))
        val root = _wire(supplied)
        val consumed = _admitted(CmlUseCaseMetadata.read(root))
        val foundation = CmlSemanticMetadata.read(root).right.get
        val dynamic = CmlDynamicMetadata.read(root).right.get
        val structure = CmlStructureMetadata.read(root).right.get
        val classification = CmlClassificationMetadata.read(root).right.get
        Then("every predecessor typed fact and original opaque peer survives the additive child")
        _expect_facts(consumed, supplied)
        foundation.catalog.elements.take(baseline.catalog.elements.size) shouldBe baseline.catalog.elements
        foundation.catalog.terms.take(baseline.catalog.terms.size) shouldBe baseline.catalog.terms
        dynamic.workflows shouldBe baseline.workflows
        dynamic.activities shouldBe baseline.activities
        dynamic.flows shouldBe baseline.flows
        dynamic.stateMachines shouldBe baseline.stateMachines
        dynamic.states shouldBe baseline.states
        dynamic.transitions shouldBe baseline.transitions
        dynamic.causalLinks shouldBe baseline.causalLinks
        structure.elements shouldBe oldstructure.elements
        structure.relations shouldBe oldstructure.relations
        classification.traits shouldBe oldclassification.traits
        classification.powertypes shouldBe oldclassification.powertypes
        classification.generalizations shouldBe oldclassification.generalizations
        classification.traitRealizations shouldBe oldclassification.traitRealizations
        classification.powertypeClassifications shouldBe oldclassification.powertypeClassifications
        baseline.envelope.extensions.fields.foreach { case (key, value) => consumed.envelope.extensions.value(key) shouldBe value }
        consumed.envelope.extensions.value("org.example.use-case-peer") shouldBe addedpeer
      }

      "UC-06 admit opaque false empty and malformed semantic peers and reject an own namespace only after base admission" in {
        Given("caller peers with opaque semantics an own namespace conflict and an invalid outer schema")
        val fixture = _fixture()
        val conflict = fixture.copy(extensions = fixture.extensions + (namespace -> Json.obj()))
        When("base publication and the Use Case facade interpret those declarations")
        val consumed = _admitted(CmlUseCaseMetadata.read(_wire(fixture)))
        val conflicted = _first(_publish(conflict))
        val basefailure = CmlSemanticMetadata.build(null, conflict.extensions).left.get.head
        val wrapped = _first(CmlUseCaseMetadata.build(null, fixture.actors, fixture.usecases, conflict.extensions))
        val root = _wire(fixture) + ("schemaVersion" -> JsString("invalid-outer"))
        val original = CmlSemanticMetadata.read(root).left.get.head
        val readfailure = _first(CmlUseCaseMetadata.read(root))
        val nulloriginal = CmlSemanticMetadata.read(null).left.get.head
        val nullread = _first(CmlUseCaseMetadata.read(null))
        Then("peer opacity survives own publication and outer failure retains the exact typed diagnostic")
        fixture.extensions.fields.foreach { case (key, value) => consumed.envelope.extensions.value(key) shouldBe value }
        _expect_failure(conflicted, ExtensionConflict, "extensions", "The Use Case extension namespace is already supplied by the caller.")
        wrapped.kind shouldBe PublicationFailure
        wrapped.publication shouldBe Some(basefailure)
        wrapped.detail shouldBe "The foundation metadata publication is invalid."
        readfailure.kind shouldBe PublicationFailure
        readfailure.publication shouldBe Some(original)
        readfailure.path shouldBe original.path
        nullread.kind shouldBe PublicationFailure
        nullread.publication shouldBe Some(nulloriginal)
      }
    }

    "closed recursive wire forms" which {
      "UC-07 reject missing extra null wrong-type and mixed fields at every nested schema container with safe coordinates" in {
        Given("a literal complete envelope and independent recursive mutations of every child container")
        val fixture = _fixture()
        When("the strict reader admits each closed-object and required-type contradiction")
        val root = _wire(fixture)
        val variants = _shape_variants(root)
        val results = variants.map { case (value, path, kind) => (_first(CmlUseCaseMetadata.read(value)), path, kind) }
        Then("diagnostics identify fixed schema positions without echoing supplied keys or values")
        results.foreach { case (diagnostic, path, kind) =>
          _expect_failure(diagnostic, kind, path, if (kind == InvalidCoreBinding) _wire_binding_detail else _shape_detail)
          diagnostic.toString should not include _hostile
        }
      }

      "UC-07 check child version before contradictory child keys and require the child after base publication" in {
        Given("unsupported versions and missing or non-object own children")
        val fixture = _fixture()
        When("the receiver consumes unsupported child versions alongside hostile fields and wrong arrays")
        val root = _wire(fixture)
        val badversion = _with_child(root, Json.obj("schemaVersion" -> "future", _hostile -> JsNull, "actors" -> false))
        val unsupported = _first(CmlUseCaseMetadata.read(badversion))
        val missing = _first(CmlUseCaseMetadata.read(root + ("extensions" -> ((root \ "extensions").as[JsObject] - namespace))))
        val wrong = Vector(JsNull, JsBoolean(false), Json.arr()).map { value =>
          val malformed = _with_child(root, value)
          (_first(CmlUseCaseMetadata.read(malformed)), CmlSemanticMetadata.read(malformed).left.get.head)
        }
        Then("version-first failure takes precedence and the own object remains mandatory")
        _expect_failure(unsupported, UnsupportedSchemaVersion, _prefix + ".schemaVersion", "The Use Case schema version is not supported.")
        _expect_failure(missing, InvalidShape, _prefix, _shape_detail)
        wrong.foreach { case (diagnostic, original) =>
          diagnostic.kind shouldBe PublicationFailure
          diagnostic.path shouldBe original.path
          diagnostic.detail shouldBe "The foundation metadata publication is invalid."
          diagnostic.publication shouldBe Some(original)
        }
      }
    }

    "literal text and identity boundaries" which {
      "UC-08 retain empty multiline control and escaped Unicode literal text while rejecting padded controlled or Unicode-space IDs and gap details" in {
        Given("literal content with newline tab quotes slash emoji and combining Unicode alongside invalid identity-like strings")
        val text = "\n\t\"\\😀e\u0301\u0000"
        val fixture = _fixture()
        val literal = fixture.copy(actors = fixture.actors.map(_.copy(actorKind = Present(""), summary = Present(text), description = Present(text))),
          usecases = fixture.usecases.map(_.copy(declaredId = Present(text), summary = Present(""), goal = Present(text),
            trigger = Present(DeclaredExpression(text, Present(Vector.empty))))))
        val invalid = Vector("", " padded", "padded ", "\u00a0value", "value\u2007", "\u202fvalue", "a\u0000b", "a\u0085b")
        When("literal declarations round trip and every reference site receives invalid qualified components")
        val consumed = _admitted(CmlUseCaseMetadata.read(_wire(literal)))
        val root = _wire(fixture)
        val badrefs = _slots.flatMap { slot => invalid.flatMap { value =>
          Vector(ModelReference(ModelElementId(value, "actor:0"), External), ModelReference(ModelElementId("supplier.example", value), External))
            .map(reference => (slot, reference))
        }}
        val typed = badrefs.map { case (slot, reference) => _first(_publish(slot.update(fixture, reference))) }
        val wire = badrefs.map { case (slot, reference) => _first(CmlUseCaseMetadata.read(_replace_child(root, slot.path, _reference_wire(reference)))) }
        val gaps = invalid.map(detail => _first(_publish(fixture.copy(actors = fixture.actors.updated(0, fixture.actors.head.copy(summary = Absent(NotDeclared, detail)))))))
        val wiregaps = invalid.map(detail => _first(CmlUseCaseMetadata.read(_replace_child(root, Vector("actors", "0", "summary"), _absence_wire(NotDeclared, detail)))))
        Then("literal source is lossless but malformed identity and absence details never acquire defaults or normalization")
        _expect_facts(consumed, literal)
        (typed ++ wire).zip(badrefs ++ badrefs).foreach { case (diagnostic, (slot, _)) =>
          _expect_failure(diagnostic, InvalidUseCaseSemantics, _coordinate(slot.path) + ".identity", _semantic_detail)
        }
        (gaps ++ wiregaps).foreach(diagnostic => _expect_failure(diagnostic, InvalidUseCaseSemantics, "actors[0].summary.detail", _semantic_detail))
      }

      "UC-08 reject malformed role flow boundary reference and expression values without echoing hostile text" in {
        Given("case-sensitive enum contradictions and hostile reference and absence text")
        val fixture = _fixture()
        When("strict wire and typed constructors receive unknown case null enums and malformed reference payloads")
        val root = _wire(fixture)
        val cases = Vector(
          Vector("useCases", "0", "actorReferences", "value", "0", "role") -> JsString("Primary"),
          Vector("useCases", "0", "actorReferences", "value", "0", "role") -> JsString(_hostile),
          Vector("useCases", "0", "flows", "value", "0", "kind") -> JsString("Main"),
          Vector("useCases", "0", "flows", "value", "0", "kind") -> JsString(_hostile),
          Vector("actors", "0", "useCases", "value", "0", "boundary") -> JsString("Local"))
        val wire = cases.map { case (path, value) => (_first(CmlUseCaseMetadata.read(_replace_child(root, path, value))), _wire_coordinate(path)) }
        val malformed = _slots.flatMap { slot => Vector(null, ModelReference(null, Local), ModelReference(ModelElementId(null, "x"), Local),
          ModelReference(ModelElementId("supplier.example", "x"), null), ModelReference(ModelElementId("supplier.example", _hostile), External))
          .map(reference => _first(_publish(slot.update(fixture, reference)))) }
        val gap = _first(_publish(fixture.copy(actors = fixture.actors.updated(0, fixture.actors.head.copy(summary = Absent(NotDeclared, _hostile))))))
        Then("all failures are typed safe and retain no publication state")
        wire.foreach { case (diagnostic, path) => _expect_failure(diagnostic, InvalidShape, path, _shape_detail) }
        (malformed :+ gap).foreach { diagnostic =>
          Set[UseCaseDiagnosticKind](InvalidShape, InvalidUseCaseSemantics) should contain(diagnostic.kind)
          diagnostic.publication shouldBe None
          diagnostic.toString should not include _hostile
        }
      }
    }
  }

  "Use Case immutable declarations" should {
    "independent ordering properties" which {
      "UC-09 preserve complete facts carrier multiplicity Catalog order and canonical JSON under bounded independent permutations" in {
        Given("independent bounded Catalog and carrier permutations and a recursive JSON object reconstruction generator in declared and anonymous gap modes")
        val fixtures = Vector(_fixture(), _anonymous(_gaps(_fixture())))
        val generator = for {
          declaredpriorities <- Gen.listOfN(fixtures(0).records.size, Gen.choose(0, 1000000))
          anonymouspriorities <- Gen.listOfN(fixtures(1).records.size, Gen.choose(0, 1000000))
          actororder <- Gen.oneOf(Vector(0, 1), Vector(1, 0))
          caseorder <- Gen.oneOf(Vector(0, 1), Vector(1, 0))
          seed <- Gen.choose(0, 1000000)
        } yield (declaredpriorities, anonymouspriorities, actororder, caseorder, seed)
        When("one hundred generated independent declarations in both modes are built and consumed after object-key reconstruction")
        val property = Prop.forAll(generator) { case (declaredpriorities, anonymouspriorities, actororder, caseorder, seed) =>
          val priorities = Vector(declaredpriorities.toVector, anonymouspriorities.toVector)
          Prop(fixtures.indices.forall { mode =>
            Given("one generated nonidentity Catalog permutation of independently authored typed facts in " +
              (if (mode == 0) "declared mode" else "anonymous gap mode"))
            val fixture = fixtures(mode)
            val indices = fixture.records.indices.toVector
            val ordered = indices.sortBy(index => (priorities(mode)(index), index))
            val recordorder = if (ordered.map(fixture.records) == fixture.records) ordered.tail :+ ordered.head else ordered
            val supplied = fixture.copy(records = recordorder.map(fixture.records), actors = actororder.map(fixture.actors), usecases = caseorder.map(fixture.usecases))
            When("the producer renders and the reader receives recursively reconstructed object keys")
            val graph = _admitted(_publish(supplied))
            val root = CmlUseCaseMetadata.toJson(graph)
            val canonical = CmlUseCaseMetadata.canonicalJson(graph)
            val reconstructed = _rekey(root, seed)
            val consumed = _admitted(CmlUseCaseMetadata.read(reconstructed))
            val receivedcanonical = CmlUseCaseMetadata.canonicalJson(consumed)
            Then("all authored facts order multiplicity and canonical publication are preserved with an observably reordered Catalog")
            _expect_facts(consumed, supplied)
            consumed.actors shouldBe actororder.map(fixture.actors)
            consumed.useCases shouldBe caseorder.map(fixture.usecases)
            consumed.catalog.elements shouldBe recordorder.map(fixture.records)
            consumed.catalog.elements should not be fixture.records
            receivedcanonical shouldBe canonical
            consumed.catalog.terms == supplied.terms && receivedcanonical == canonical
          })
        }
        val result = Test.check(Test.Parameters.default.withMinSuccessfulTests(100), property)
        Then("the active property succeeds without discarded cases")
        info("Use Case permutation property: " + result)
        result.passed shouldBe true
        result.succeeded shouldBe 100
        result.discarded shouldBe 0
      }
    }
  }

  private final case class Fixture(records: Vector[ElementRecord], terms: Vector[TermRecord], actors: Vector[ActorProjection],
    usecases: Vector[UseCaseProjection], extensions: JsObject)
  private final case class Slot(path: Vector[String], kinds: Vector[String], expression: Boolean, update: (Fixture, ModelReference) => Fixture)

  private val _prefix = "extensions.cozy.cml.use-case"
  private val _hostile = "hostile\n/private/secret\u0000"
  private val _shape_detail = "The Use Case value does not match the required v1 shape."
  private val _semantic_detail = "The declared Use Case reference or absence is invalid."
  private val _binding_detail = "The Use Case projection does not bind exactly to an admitted core record."
  private val _wire_binding_detail = "The embedded Use Case record does not exactly match admitted core JSON."
  private val _missing_detail = "An admitted Use Case or Actor record lacks its required projection occurrence."
  private val _duplicate_detail = "A Use Case or Actor record has excess projection occurrences."
  private val _domain_kinds = Vector("entity", "value", "aggregate", "trait", "powertype", "powertype-kind")
  private val _collaborator_kinds = Vector("actor", "use-case", "component", "service", "external-system", "query", "view")
  private val _kinds = Vector("actor", "use-case", "workflow", "operation", "command", "event") ++ _domain_kinds ++
    Vector("component", "service", "external-system", "query", "view", "state", "rule", "workflow-activity", "unrelated")
  private val _actor_fields = Vector("actorKind", "summary", "description", "useCases", "workflows", "operations", "events", "domainElements")
  private val _case_fields = Vector("declaredId", "summary", "description", "goal", "trigger", "preconditions", "postconditions", "priority",
    "lifecycleStatus", "actorReferences", "flows", "domainElements", "operations", "events", "collaborators", "realizingWorkflows")
  private val _flow_fields = Vector("name", "summary", "description", "condition", "steps", "alternates", "exceptions")

  private def _source(authority: String): SourceAttribution = {
    SourceAttribution(authority, "src/main/cml/use-case-spec.cml", "c" * 64, Some(7))
  }

  private def _record(kind: String, id: String, authority: String = "supplier.example"): ElementRecord = {
    ElementRecord(Present(ModelElementId(authority, id)), kind, "共有", _source(authority), Absent(NotDeclared, "No core references declared."))
  }

  private def _ref(id: String, boundary: ReferenceBoundary = Local): ModelReference = {
    ModelReference(ModelElementId("supplier.example", id), boundary)
  }

  private def _target_id(kind: String): String = {
    if (kind == "actor" || kind == "use-case") kind + ":0" else kind + ":target"
  }

  private def _expression(): DeclaredExpression = {
    DeclaredExpression("Authored\n\"事😀\"\\text", Present(Vector(_ref("entity:target"), _ref("entity:target"), _ref("unknown", External))))
  }

  private def _flows(): Vector[UseCaseFlow] = {
    Vector(MainFlow, AlternateFlow, ExceptionFlow).map(kind => UseCaseFlow(kind, Present("literal name"), Present("Flow summary."),
      Present("Flow description."), Present(_expression()), Present(Vector(_expression(), _expression())),
      Present(Vector(_expression())), Present(Vector(_expression()))))
  }

  private def _fixture(): Fixture = {
    val actorrecords = Vector(_record("actor", "actor:0"), _record("actor", "actor:0", "other.example"))
    val caserecords = Vector(_record("use-case", "use-case:0"), _record("use-case", "use-case:0", "other.example"))
    val actors = actorrecords.zipWithIndex.map { case (record, index) => ActorProjection(record, Present(if (index == 0) "human" else "system"),
      Present("Actor summary."), Present("Actor description."), Present(Vector(_ref("use-case:0"), _ref("use-case:0"), _ref("use-case:0", External))),
      Present(Vector(_ref("workflow:target"))), Present(Vector(_ref("operation:target"), _ref("command:target"))),
      Present(Vector(_ref("event:target"))), Present(_domain_kinds.map(kind => _ref(_target_id(kind))))) }
    val participants = Vector(Primary, Secondary, Supporting, Stakeholder, Actor, Primary).map(role =>
      ActorParticipation(role, if (role == Supporting) _ref("unknown", External) else _ref("actor:0")))
    val usecases = caserecords.map(record => UseCaseProjection(record, Present("UC-SAME"), Present("Use Case summary."),
      Present("Use Case description."), Present("Complete declared goal."), Present(_expression()), Present(Vector(_expression())),
      Present(Vector(_expression())), Present("high"), Present("approved"), Present(participants), Present(_flows()),
      Present(_domain_kinds.map(kind => _ref(_target_id(kind)))), Present(Vector(_ref("operation:target"), _ref("command:target"))),
      Present(Vector(_ref("event:target"))), Present(_collaborator_kinds.map(kind => _ref(_target_id(kind)))),
      Present(Vector(_ref("workflow:target"), _ref("workflow:target", External)))))
    Fixture(actorrecords ++ caserecords ++ _kinds.filterNot(kind => kind == "actor" || kind == "use-case").map(kind => _record(kind, _target_id(kind))),
      Vector(TermRecord(TermId("supplier.example", "use-case:0"), _source("supplier.example"))), actors, usecases,
      Json.obj("cozy.cml.structure" -> Json.obj("unvalidated" -> false), "cozy.cml.classification" -> Json.obj(),
        "cozy.cml.dynamic" -> Json.obj("opaque" -> ""), "org.example.literal" -> Json.obj("flag" -> false, "values" -> Json.arr(), "gap" -> JsNull)))
  }

  private def _gap(index: Int): Absent = {
    Vector(Absent(NotDeclared, "No fact declared."), Absent(NotRepresented, "Fact not represented."),
      Absent(Unsupported, "Fact unsupported."), Absent(NotApplicable, "Fact not applicable."))(index % 4)
  }

  private def _gaps(fixture: Fixture): Fixture = {
    fixture.copy(actors = fixture.actors.zipWithIndex.map { case (value, index) => ActorProjection(value.element,
      _gap(index), _gap(index + 1), _gap(index + 2), _gap(index + 3), _gap(index + 4), _gap(index + 5), _gap(index + 6), _gap(index + 7)) },
      usecases = fixture.usecases.zipWithIndex.map { case (value, index) => UseCaseProjection(value.element,
        _gap(index + 2), _gap(index + 3), _gap(index + 4), _gap(index + 5), _gap(index + 6), _gap(index + 7), _gap(index + 8), _gap(index + 9),
        _gap(index + 10), _gap(index + 11), _gap(index + 12), _gap(index + 13), _gap(index + 14), _gap(index + 15), _gap(index + 16), _gap(index + 17)) })
  }

  private def _anonymous(fixture: Fixture): Fixture = {
    val actor = fixture.actors.head.element.copy(identity = Absent(NotRepresented, "No stable identity represented."))
    val usecase = fixture.usecases.head.element.copy(identity = Absent(NotRepresented, "No stable identity represented."))
    fixture.copy(records = fixture.records.filterNot(record => Set("actor", "use-case")(record.kind)) ++ Vector(actor, actor, usecase, usecase),
      actors = fixture.actors.map(_.copy(element = actor)), usecases = fixture.usecases.map(_.copy(element = usecase)))
  }

  private def _empty(fixture: Fixture): Fixture = {
    fixture.copy(actors = fixture.actors.map(value => ActorProjection(value.element, Present(""), Present(""), Present(""),
      Present(Vector.empty), Present(Vector.empty), Present(Vector.empty), Present(Vector.empty), Present(Vector.empty))),
      usecases = fixture.usecases.map(value => UseCaseProjection(value.element, Present(""), Present(""), Present(""), Present(""),
        Present(DeclaredExpression("", Present(Vector.empty))), Present(Vector.empty), Present(Vector.empty), Present(""), Present(""),
        Present(Vector.empty), Present(Vector.empty), Present(Vector.empty), Present(Vector.empty), Present(Vector.empty), Present(Vector.empty), Present(Vector.empty))))
  }

  private def _catalog(fixture: Fixture): Catalog = {
    CmlSemanticFoundation.build(fixture.records, fixture.terms) match {
      case Right(catalog) => catalog
      case Left(diagnostics) => fail("Expected admitted source catalog: " + diagnostics)
    }
  }

  private def _publish(fixture: Fixture): Either[Vector[UseCaseDiagnostic], Graph] = {
    CmlUseCaseMetadata.build(_catalog(fixture), fixture.actors, fixture.usecases, fixture.extensions)
  }

  private def _admitted(result: Either[Vector[UseCaseDiagnostic], Graph]): Graph = {
    result match {
      case Right(graph) => graph
      case Left(diagnostics) => fail("Expected admitted Use Case declarations: " + diagnostics)
    }
  }

  private def _wire(fixture: Fixture): JsObject = {
    CmlUseCaseMetadata.toJson(_admitted(_publish(fixture)))
  }

  private def _first(result: Either[Vector[UseCaseDiagnostic], Graph]): UseCaseDiagnostic = {
    result match {
      case Left(diagnostics) if diagnostics.nonEmpty => diagnostics.head
      case _ => fail("Expected typed Use Case rejection.")
    }
  }

  private def _expect_facts(graph: Graph, fixture: Fixture): Unit = {
    graph.catalog.elements shouldBe fixture.records
    graph.catalog.terms shouldBe fixture.terms
    graph.actors shouldBe fixture.actors
    graph.useCases shouldBe fixture.usecases
    fixture.extensions.fields.foreach { case (key, value) => graph.envelope.extensions.value(key) shouldBe value }
  }

  private def _expect_failure(diagnostic: UseCaseDiagnostic, kind: UseCaseDiagnosticKind, path: String, detail: String): Unit = {
    diagnostic.kind shouldBe kind
    diagnostic.path shouldBe path
    diagnostic.detail shouldBe detail
    diagnostic.publication shouldBe None
  }

  private def _resource(name: String): JsValue = {
    val stream = Option(getClass.getResourceAsStream("/cozy/modeler/" + name)).getOrElse(fail("Missing predecessor resource."))
    val source = Source.fromInputStream(stream, "UTF-8")
    try { Json.parse(source.mkString) } finally { source.close() }
  }

  private def _coordinate(path: Vector[String]): String = {
    path.foldLeft("") { (coordinate, part) => if (part.forall(_.isDigit)) coordinate + "[" + part + "]"
      else if (coordinate.isEmpty) part else coordinate + "." + part }
  }

  private def _wire_coordinate(path: Vector[String]): String = {
    if (path.isEmpty) _prefix else _prefix + "." + _coordinate(path)
  }

  private def _child(root: JsObject): JsObject = { (root \ "extensions" \ namespace).as[JsObject] }

  private def _with_child(root: JsObject, child: JsValue): JsObject = {
    root + ("extensions" -> ((root \ "extensions").as[JsObject] + (namespace -> child)))
  }

  private def _at(value: JsValue, path: Vector[String]): JsValue = {
    path.foldLeft(value) { (current, part) => current match {
      case objectvalue: JsObject => objectvalue.value(part)
      case JsArray(values) => values(part.toInt)
      case _ => fail("Invalid independent fixture coordinate.")
    }}
  }

  private def _replace(value: JsValue, path: Vector[String], replacement: JsValue): JsValue = {
    if (path.isEmpty) replacement else value match {
      case objectvalue: JsObject => objectvalue + (path.head -> _replace(objectvalue.value(path.head), path.tail, replacement))
      case JsArray(values) => JsArray(values.updated(path.head.toInt, _replace(values(path.head.toInt), path.tail, replacement)))
      case _ => fail("Invalid independent fixture replacement.")
    }
  }

  private def _replace_child(root: JsObject, path: Vector[String], replacement: JsValue): JsObject = {
    _with_child(root, _replace(_child(root), path, replacement))
  }

  private def _rekey(value: JsValue, seed: Int): JsValue = {
    value match {
      case objectvalue: JsObject =>
        val fields = objectvalue.fields.toVector
        val shift = if (fields.isEmpty) 0 else seed % fields.size
        JsObject((fields.drop(shift) ++ fields.take(shift)).reverse.map { case (key, child) => key -> _rekey(child, seed / 2 + 1) })
      case JsArray(values) => JsArray(values.map(child => _rekey(child, seed / 2 + 1)))
      case other => other
    }
  }

  private def _present_wire(value: JsValue): JsObject = { Json.obj("status" -> "present", "value" -> value) }

  private def _absence_wire(reason: AbsenceReason, detail: String): JsObject = {
    val tag = reason match {
      case NotDeclared => "not-declared"
      case NotRepresented => "not-represented"
      case Unsupported => "unsupported"
      case NotApplicable => "not-applicable"
    }
    Json.obj("status" -> "absent", "reason" -> tag, "detail" -> detail)
  }

  private def _reference_wire(reference: ModelReference): JsObject = {
    Json.obj("identity" -> Json.obj("modelId" -> reference.identity.modelId, "elementId" -> reference.identity.elementId),
      "boundary" -> (reference.boundary match { case Local => "local"; case External => "external" }))
  }

  private def _core_wire(record: ElementRecord): JsObject = {
    val identity = record.identity match {
      case Present(id) => _present_wire(Json.obj("modelId" -> id.modelId, "elementId" -> id.elementId))
      case Absent(reason, detail) => _absence_wire(reason, detail)
    }
    val references = record.references match {
      case Absent(reason, detail) => _absence_wire(reason, detail)
      case Present(_) => _present_wire(Json.arr())
    }
    Json.obj("identity" -> identity, "kind" -> record.kind, "name" -> record.name,
      "source" -> Json.obj("authorityId" -> record.source.authorityId, "path" -> record.source.path,
        "sha256" -> record.source.sha256, "line" -> record.source.line), "references" -> references)
  }

  private def _literal_refs(ids: Vector[String]): JsObject = {
    _present_wire(JsArray(ids.map(id => Json.obj("identity" -> Json.obj("modelId" -> "supplier.example", "elementId" -> id), "boundary" -> "local"))))
  }

  private def _literal_expression(): JsObject = {
    Json.obj("text" -> "Authored\n\"事😀\"\\text", "references" -> _present_wire(Json.arr(
      Json.obj("identity" -> Json.obj("modelId" -> "supplier.example", "elementId" -> "entity:target"), "boundary" -> "local"),
      Json.obj("identity" -> Json.obj("modelId" -> "supplier.example", "elementId" -> "entity:target"), "boundary" -> "local"),
      Json.obj("identity" -> Json.obj("modelId" -> "supplier.example", "elementId" -> "unknown"), "boundary" -> "external"))))
  }

  private def _expected_child(fixture: Fixture): JsObject = {
    val domain = _literal_refs(Vector("entity:target", "value:target", "aggregate:target", "trait:target", "powertype:target", "powertype-kind:target"))
    val operations = _literal_refs(Vector("operation:target", "command:target"))
    val actors = fixture.actors.zipWithIndex.map { case (value, index) => Json.obj("element" -> _core_wire(value.element),
      "actorKind" -> _present_wire(JsString(if (index == 0) "human" else "system")), "summary" -> _present_wire(JsString("Actor summary.")),
      "description" -> _present_wire(JsString("Actor description.")), "useCases" -> _present_wire(Json.arr(
        Json.obj("identity" -> Json.obj("modelId" -> "supplier.example", "elementId" -> "use-case:0"), "boundary" -> "local"),
        Json.obj("identity" -> Json.obj("modelId" -> "supplier.example", "elementId" -> "use-case:0"), "boundary" -> "local"),
        Json.obj("identity" -> Json.obj("modelId" -> "supplier.example", "elementId" -> "use-case:0"), "boundary" -> "external"))),
      "workflows" -> _literal_refs(Vector("workflow:target")), "operations" -> operations, "events" -> _literal_refs(Vector("event:target")), "domainElements" -> domain) }
    val flows = Vector("main", "alternate", "exception").map(kind => Json.obj("kind" -> kind,
      "name" -> _present_wire(JsString("literal name")), "summary" -> _present_wire(JsString("Flow summary.")),
      "description" -> _present_wire(JsString("Flow description.")), "condition" -> _present_wire(_literal_expression()),
      "steps" -> _present_wire(Json.arr(_literal_expression(), _literal_expression())),
      "alternates" -> _present_wire(Json.arr(_literal_expression())), "exceptions" -> _present_wire(Json.arr(_literal_expression()))))
    val participants = Vector("primary", "secondary", "supporting", "stakeholder", "actor", "primary").map(role => Json.obj("role" -> role,
      "target" -> Json.obj("identity" -> Json.obj("modelId" -> "supplier.example", "elementId" -> (if (role == "supporting") "unknown" else "actor:0")),
        "boundary" -> (if (role == "supporting") "external" else "local"))))
    val cases = fixture.usecases.map(value => Json.obj("element" -> _core_wire(value.element),
      "declaredId" -> _present_wire(JsString("UC-SAME")), "summary" -> _present_wire(JsString("Use Case summary.")),
      "description" -> _present_wire(JsString("Use Case description.")), "goal" -> _present_wire(JsString("Complete declared goal.")),
      "trigger" -> _present_wire(_literal_expression()), "preconditions" -> _present_wire(Json.arr(_literal_expression())),
      "postconditions" -> _present_wire(Json.arr(_literal_expression())), "priority" -> _present_wire(JsString("high")),
      "lifecycleStatus" -> _present_wire(JsString("approved")), "actorReferences" -> _present_wire(JsArray(participants)),
      "flows" -> _present_wire(JsArray(flows)), "domainElements" -> domain, "operations" -> operations, "events" -> _literal_refs(Vector("event:target")),
      "collaborators" -> _literal_refs(Vector("actor:0", "use-case:0", "component:target", "service:target", "external-system:target", "query:target", "view:target")),
      "realizingWorkflows" -> _present_wire(Json.arr(
        Json.obj("identity" -> Json.obj("modelId" -> "supplier.example", "elementId" -> "workflow:target"), "boundary" -> "local"),
        Json.obj("identity" -> Json.obj("modelId" -> "supplier.example", "elementId" -> "workflow:target"), "boundary" -> "external")))))
    Json.obj("schemaVersion" -> "cozy.cml.use-case.v1", "actors" -> JsArray(actors), "useCases" -> JsArray(cases))
  }

  private val _slots: Vector[Slot] = {
    val actorfields = Vector("useCases" -> Vector("use-case"), "workflows" -> Vector("workflow"), "operations" -> Vector("operation", "command"),
      "events" -> Vector("event"), "domainElements" -> _domain_kinds)
    val casefields = Vector("domainElements" -> _domain_kinds, "operations" -> Vector("operation", "command"), "events" -> Vector("event"),
      "collaborators" -> _collaborator_kinds, "realizingWorkflows" -> Vector("workflow"))
    val references = actorfields.map { case (field, kinds) => _slot(Vector("actors", "0", field, "value", "0"), kinds, false) } ++
      casefields.map { case (field, kinds) => _slot(Vector("useCases", "0", field, "value", "0"), kinds, false) } ++
      (0 until 6).map(index => _slot(Vector("useCases", "0", "actorReferences", "value", index.toString, "target"), Vector("actor"), false))
    val expressions = Vector(Vector("useCases", "0", "trigger", "value"), Vector("useCases", "0", "preconditions", "value", "0"),
      Vector("useCases", "0", "postconditions", "value", "0")) ++ (0 until 3).flatMap { index =>
      val prefix = Vector("useCases", "0", "flows", "value", index.toString)
      Vector(prefix ++ Vector("condition", "value")) ++ Vector("steps", "alternates", "exceptions").map(field => prefix ++ Vector(field, "value", "0"))
    }
    references ++ expressions.map(path => _slot(path ++ Vector("references", "value", "0"), _kinds, true))
  }

  private def _slot(path: Vector[String], kinds: Vector[String], expression: Boolean): Slot = {
    Slot(path, kinds, expression, (fixture, reference) => _reference_at(fixture, path, reference))
  }

  private def _reference_at(fixture: Fixture, path: Vector[String], reference: ModelReference): Fixture = {
    val many = Present(Vector(reference))
    val expression = DeclaredExpression("Authored literal", many)
    if (path.head == "actors") _presence_at(fixture, "actors", path(2), many) else {
      val value = fixture.usecases.head
      path(2) match {
        case "actorReferences" =>
          val original = value.actorReferences.asInstanceOf[Present[Vector[ActorParticipation]]].value
          val index = path(4).toInt
          fixture.copy(usecases = fixture.usecases.updated(0, value.copy(actorReferences = Present(original.updated(index, original(index).copy(target = reference))))))
        case "flows" =>
          val original = value.flows.asInstanceOf[Present[Vector[UseCaseFlow]]].value
          val index = path(4).toInt
          val flow = original(index)
          val changed = path(5) match {
            case "condition" => flow.copy(condition = Present(expression))
            case "steps" => flow.copy(steps = Present(Vector(expression)))
            case "alternates" => flow.copy(alternates = Present(Vector(expression)))
            case "exceptions" => flow.copy(exceptions = Present(Vector(expression)))
          }
          fixture.copy(usecases = fixture.usecases.updated(0, value.copy(flows = Present(original.updated(index, changed)))))
        case "trigger" => _presence_at(fixture, "useCases", "trigger", Present(expression))
        case "preconditions" | "postconditions" => _presence_at(fixture, "useCases", path(2), Present(Vector(expression)))
        case field => _presence_at(fixture, "useCases", field, many)
      }
    }
  }

  private def _presence_at(fixture: Fixture, array: String, field: String, presence: Presence[_]): Fixture = {
    val text = presence.asInstanceOf[Presence[String]]
    val references = presence.asInstanceOf[Presence[Vector[ModelReference]]]
    val expression = presence.asInstanceOf[Presence[DeclaredExpression]]
    val expressions = presence.asInstanceOf[Presence[Vector[DeclaredExpression]]]
    if (array == "actors") {
      val value = fixture.actors.head
      val changed = field match {
        case "actorKind" => value.copy(actorKind = text)
        case "summary" => value.copy(summary = text)
        case "description" => value.copy(description = text)
        case "useCases" => value.copy(useCases = references)
        case "workflows" => value.copy(workflows = references)
        case "operations" => value.copy(operations = references)
        case "events" => value.copy(events = references)
        case "domainElements" => value.copy(domainElements = references)
      }
      fixture.copy(actors = fixture.actors.updated(0, changed))
    } else {
      val value = fixture.usecases.head
      val changed = field match {
        case "declaredId" => value.copy(declaredId = text)
        case "summary" => value.copy(summary = text)
        case "description" => value.copy(description = text)
        case "goal" => value.copy(goal = text)
        case "trigger" => value.copy(trigger = expression)
        case "preconditions" => value.copy(preconditions = expressions)
        case "postconditions" => value.copy(postconditions = expressions)
        case "priority" => value.copy(priority = text)
        case "lifecycleStatus" => value.copy(lifecycleStatus = text)
        case "actorReferences" => value.copy(actorReferences = presence.asInstanceOf[Presence[Vector[ActorParticipation]]])
        case "flows" => value.copy(flows = presence.asInstanceOf[Presence[Vector[UseCaseFlow]]])
        case "domainElements" => value.copy(domainElements = references)
        case "operations" => value.copy(operations = references)
        case "events" => value.copy(events = references)
        case "collaborators" => value.copy(collaborators = references)
        case "realizingWorkflows" => value.copy(realizingWorkflows = references)
      }
      fixture.copy(usecases = fixture.usecases.updated(0, changed))
    }
  }

  private def _flow_at(fixture: Fixture, field: String, presence: Presence[_]): Fixture = {
    val value = fixture.usecases.head
    val flow = _flows().head
    val changed = field match {
      case "name" => flow.copy(name = presence.asInstanceOf[Presence[String]])
      case "summary" => flow.copy(summary = presence.asInstanceOf[Presence[String]])
      case "description" => flow.copy(description = presence.asInstanceOf[Presence[String]])
      case "condition" => flow.copy(condition = presence.asInstanceOf[Presence[DeclaredExpression]])
      case "steps" => flow.copy(steps = presence.asInstanceOf[Presence[Vector[DeclaredExpression]]])
      case "alternates" => flow.copy(alternates = presence.asInstanceOf[Presence[Vector[DeclaredExpression]]])
      case "exceptions" => flow.copy(exceptions = presence.asInstanceOf[Presence[Vector[DeclaredExpression]]])
    }
    fixture.copy(usecases = fixture.usecases.updated(0, value.copy(flows = Present(Vector(changed)))))
  }

  private def _null_variants(fixture: Fixture): Vector[Fixture] = {
    val recordchanges: Vector[ElementRecord => ElementRecord] = Vector(_ => null, _.copy(identity = null), _.copy(kind = null),
      _.copy(name = null), _.copy(source = null), _.copy(references = null))
    val records = recordchanges.flatMap(change => Vector(
      fixture.copy(actors = fixture.actors.updated(0, fixture.actors.head.copy(element = change(fixture.actors.head.element)))),
      fixture.copy(usecases = fixture.usecases.updated(0, fixture.usecases.head.copy(element = change(fixture.usecases.head.element))))))
    val presences = _actor_fields.flatMap(field => Vector(null, Absent(null, "No reason."), Absent(NotDeclared, null)).map(_presence_at(fixture, "actors", field, _))) ++
      _case_fields.flatMap(field => Vector(null, Absent(null, "No reason."), Absent(NotDeclared, null)).map(_presence_at(fixture, "useCases", field, _))) ++
      _flow_fields.map(field => _flow_at(fixture, field, null))
    val referencevectors = Vector("useCases", "workflows", "operations", "events", "domainElements").map(field => _presence_at(fixture, "actors", field, Present(null))) ++
      Vector("domainElements", "operations", "events", "collaborators", "realizingWorkflows").map(field => _presence_at(fixture, "useCases", field, Present(null)))
    val vectors = Vector("preconditions", "postconditions", "actorReferences", "flows").flatMap(field =>
      Vector(_presence_at(fixture, "useCases", field, Present(null)), _presence_at(fixture, "useCases", field, Present(Vector(null))))) ++
      Vector("steps", "alternates", "exceptions").flatMap(field => Vector(_flow_at(fixture, field, Present(null)), _flow_at(fixture, field, Present(Vector(null)))))
    val expressions = Vector(null, DeclaredExpression(null, Present(Vector.empty)), DeclaredExpression("text", null),
      DeclaredExpression("text", Present(null)), DeclaredExpression("text", Present(Vector(null)))).flatMap(expression => Vector(
        _presence_at(fixture, "useCases", "trigger", Present(expression)),
        _presence_at(fixture, "useCases", "preconditions", Present(Vector(expression))),
        _presence_at(fixture, "useCases", "postconditions", Present(Vector(expression))),
        _flow_at(fixture, "condition", Present(expression)), _flow_at(fixture, "steps", Present(Vector(expression))),
        _flow_at(fixture, "alternates", Present(Vector(expression))), _flow_at(fixture, "exceptions", Present(Vector(expression)))))
    val strings = Vector("actorKind", "summary", "description").map(field => _presence_at(fixture, "actors", field, Present(null))) ++
      Vector("declaredId", "summary", "description", "goal", "priority", "lifecycleStatus").map(field => _presence_at(fixture, "useCases", field, Present(null))) ++
      Vector("name", "summary", "description").map(field => _flow_at(fixture, field, Present(null)))
    records ++ presences ++ referencevectors ++ vectors ++ expressions ++ strings ++ _slots.map(_.update(fixture, null)) ++ Vector(
      fixture.copy(actors = null), fixture.copy(usecases = null), fixture.copy(actors = Vector(null)), fixture.copy(usecases = Vector(null)),
      _presence_at(fixture, "useCases", "actorReferences", Present(Vector(ActorParticipation(null, _ref("actor:0"))))),
      _presence_at(fixture, "useCases", "flows", Present(Vector(_flows().head.copy(kind = null)))))
  }

  private def _objects(value: JsValue, path: Vector[String]): Vector[(Vector[String], JsObject)] = {
    value match {
      case objectvalue: JsObject => Vector(path -> objectvalue) ++ objectvalue.fields.toVector.flatMap { case (key, child) => _objects(child, path :+ key) }
      case JsArray(values) => values.zipWithIndex.toVector.flatMap { case (child, index) => _objects(child, path :+ index.toString) }
      case _ => Vector.empty
    }
  }

  private def _shape_variants(root: JsObject): Vector[(JsObject, String, UseCaseDiagnosticKind)] = {
    val child = _child(root)
    val objects = _objects(child, Vector.empty)
    val changed = objects.flatMap { case (path, value) =>
      val coreindex = path.indexOf("element")
      val corepath = if (coreindex >= 0) path.take(coreindex + 1) else path
      val corekind = if (coreindex >= 0) InvalidCoreBinding else InvalidShape
      val replacements = if (path.isEmpty) Vector(value + (_hostile -> JsNull)) else Vector(value + (_hostile -> JsNull), JsNull, JsBoolean(false))
      val whole = replacements.map(replacement =>
        (_replace_child(root, path, replacement), _wire_coordinate(corepath), corekind: UseCaseDiagnosticKind))
      val fields = value.fields.toVector.flatMap { case (key, original) =>
        val fieldpath = path :+ key
        val fieldcore = coreindex >= 0 || key == "element"
        val fieldcoordinate = if (fieldcore) _wire_coordinate(if (coreindex >= 0) corepath else fieldpath) else _wire_coordinate(fieldpath)
        val fieldkind: UseCaseDiagnosticKind = if (fieldcore) InvalidCoreBinding else InvalidShape
        val missingcoordinate = if (coreindex >= 0) _wire_coordinate(corepath) else _wire_coordinate(fieldpath)
        val missingkind: UseCaseDiagnosticKind = if (coreindex >= 0) InvalidCoreBinding else InvalidShape
        val wrong = original match {
          case _: JsString => JsBoolean(false)
          case _ => JsString(_hostile)
        }
        Vector((_replace_child(root, path, value - key), missingcoordinate, missingkind),
          (_replace_child(root, fieldpath, JsNull), fieldcoordinate, fieldkind),
          (_replace_child(root, fieldpath, wrong), fieldcoordinate, fieldkind))
      }
      whole ++ fields
    }
    val presencepaths = objects.filter { case (_, value) => value.value.get("status").contains(JsString("present")) }.map(_._1)
    val mixed = presencepaths.map(path => (_replace_child(root, path, _at(child, path).as[JsObject] + ("reason" -> JsString("not-declared"))),
      _wire_coordinate(if (path.contains("element")) path.take(path.indexOf("element") + 1) else path),
      (if (path.contains("element")) InvalidCoreBinding else InvalidShape): UseCaseDiagnosticKind))
    val absences = Vector("actors", "useCases").flatMap { array =>
      val fields = if (array == "actors") _actor_fields else _case_fields
      fields.flatMap { field =>
        val path = Vector(array, "0", field)
        val absent = _absence_wire(NotDeclared, "No fact declared.")
        val variants = Vector((absent + ("value" -> JsNull), path), (absent - "detail", path :+ "detail"),
          (absent - "reason", path :+ "reason"), (absent + ("reason" -> JsString("NotDeclared")), path :+ "reason"),
          (absent + ("status" -> JsString("Absent")), path :+ "status"), (absent + ("detail" -> JsNull), path :+ "detail"))
        variants.map { case (value, coordinate) => (_replace_child(root, path, value), _wire_coordinate(coordinate), InvalidShape: UseCaseDiagnosticKind) }
      }
    }
    val arrayitems = Vector("actors", "useCases").map(array => Vector(array, "0")) ++ _slots.filterNot(_.expression).map(_.path).filterNot(_.last == "target") ++
      Vector(Vector("useCases", "0", "actorReferences", "value", "0"), Vector("useCases", "0", "flows", "value", "0"),
        Vector("useCases", "0", "preconditions", "value", "0"), Vector("useCases", "0", "postconditions", "value", "0"))
    changed ++ mixed ++ absences ++ arrayitems.map(path => (_replace_child(root, path, JsNull), _wire_coordinate(path), InvalidShape: UseCaseDiagnosticKind))
  }
}
