package cozy.modeler

import org.scalacheck.{Gen, Prop, Test}
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import play.api.libs.json.{JsArray, JsNull, JsNumber, JsObject, JsString, JsValue, Json}
import CmlSemanticFoundation._
import CmlDynamicMetadata.{CausalLinkProjection, ModelReference}
import CmlCausalMetadata._

/*
 * @since   Oct.  5, 2026
 * @version Oct. 13, 2026
 */
final class CmlCausalMetadataSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  "Causal metadata" should {
    "complete admitted evidence" which {
      "CM-01 retain the supplied catalog and match the independent complete typed and wire oracle" in {
        Given("literal core and Term records, opaque peers and independently ordered admitted peer declarations")
        val supplied = _full()
        val expected = _wire(supplied)
        When("Foundation admits the literal publication and Causal builds renders canonically and consumes the oracle")
        val envelope = CmlSemanticMetadata.read(supplied.root).right.get
        val graph = CmlCausalMetadata.build(envelope).right.get
        val rendered = CmlCausalMetadata.toJson(graph)
        val canonical = Json.parse(CmlCausalMetadata.canonicalJson(graph))
        val consumed = CmlCausalMetadata.read(expected).right.get
        val consumedjson = CmlCausalMetadata.toJson(consumed)
        Then("the complete graph and child equal independent declarations and every unrelated peer survives")
        (graph.catalog eq envelope.catalog) shouldBe true
        (graph.envelope.catalog eq envelope.catalog) shouldBe true
        _expect(graph, supplied)
        _expect(consumed, supplied)
        rendered shouldBe expected
        canonical shouldBe expected
        consumedjson shouldBe expected
        (rendered \ "extensions" \ "cozy.cml.terminology").get shouldBe Json.obj("opaque" -> "Term peer")
      }

      "CM-02 preserve qualified pairs without name source Term relation or profile identity fallback" in {
        Given("case Unicode composition delimiter-collision and same-local-ID pairs with identical display names")
        val pairs = Vector(("a:b", "c"), ("a", "b:c"), ("A", "same"), ("a", "same"),
          ("é", "same"), ("e\u0301", "same"), ("a", "Same"))
        val records = pairs.map { case (authority, id) => _record(authority, id, "query").copy(name = "same") }
        val term = TermRecord(TermId("a", "same"), _source("a"))
        val supplied = _missing_fixture(records, Vector(term))
        When("the literal publication is admitted built read and addressed by the exact pairs")
        val graph = _publish(supplied)
        val consumed = CmlCausalMetadata.read(_wire(supplied)).right.get
        val queries = pairs.map { case (authority, id) => graph.catalog.element(ModelElementId(authority, id)) }
        val unknown = graph.catalog.element(ModelElementId("a:b:c", "same"))
        Then("each qualified identity denotes exactly its own full record with no synthesized alias")
        queries shouldBe records.map(Right(_))
        unknown.isLeft shouldBe true
        _expect(graph, supplied)
        _expect(consumed, supplied)
      }

      "CM-07 retain direct causal assertions separately from actor policy membership and collaborator declarations" in {
        Given("Actor operation duplicates affected Aggregate and Entity rule context and words suggesting additional causes")
        val supplied = _full()
        When("the admitted peer declarations are projected through the Causal builder")
        val graph = _publish(supplied)
        val rendered = CmlCausalMetadata.toJson(graph)
        Then("only the original Cause and Reaction carriers assert direction while every declared context is exact")
        graph.causalLinks shouldBe Present(Vector(_cause, _reaction))
        graph.actorOperations shouldBe Present(Vector(ActorOperations(_actor, Present(Vector(_opref, _opref)))))
        graph.ruleContexts shouldBe Present(Vector(RuleContext(_workflow, Present(Vector(_ruleref))),
          RuleContext(_activity, Present(Vector(_ruleref, _ruleref)))))
        graph.aggregateBoundaries shouldBe supplied.aggregates
        graph.collaboratorContexts shouldBe supplied.collaborators
        (rendered \ "extensions" \ namespace).get shouldBe _child(supplied)
      }

      "CM-08 keep Local Query View and resident External dependency references and full ordered provenance" in {
        Given("generic references with exact relation profile context declared and derived ordered input sources and a Term reference")
        val supplied = _full()
        When("the publication is projected rendered and the independently authored wire is read")
        val graph = _publish(supplied)
        val rendered = CmlCausalMetadata.toJson(graph)
        val consumed = CmlCausalMetadata.read(_wire(supplied)).right.get
        Then("model filtering preserves duplicate references and External boundary without an execution or causation upgrade")
        graph.modelReferences.find(_.element == _operation).get.references shouldBe
          Present(Vector(_queryref, _externalref, _viewref, _queryref))
        consumed.modelReferences shouldBe supplied.references
        graph.causalLinks shouldBe Present(Vector(_cause, _reaction))
        graph.catalog.elements should contain (_component)
        (rendered \ "extensions" \ namespace).get shouldBe _child(supplied)
      }
    }

    "explicit availability and ordered occurrences" which {
      "CM-03A distinguish missing families from admitted present empty vectors" in {
        Given("an empty core with either missing peers or three complete present empty peer objects")
        val missing = _missing_fixture(Vector.empty, Vector.empty)
        val empty = missing.copy(root = _root(Vector.empty, Vector.empty, _empty_peers),
          links = Present(Vector.empty), actors = Present(Vector.empty), aggregates = Present(Vector.empty),
          rules = Present(Vector.empty), collaborators = Present(Vector.empty), gaps = Vector(_inference))
        When("both literal publication envelopes are admitted projected and consumed")
        val graphs = Vector(missing, empty).map(_publish)
        val reads = Vector(missing, empty).map(value => CmlCausalMetadata.read(_wire(value)).right.get)
        val wires = graphs.map(CmlCausalMetadata.toJson)
        Then("missing family gaps have owner None and present empty is an available capability")
        _expect(graphs(0), missing)
        _expect(graphs(1), empty)
        _expect(reads(0), missing)
        _expect(reads(1), empty)
        wires shouldBe Vector(_wire(missing), _wire(empty))
      }

      "CM-03B copy all four absence reasons and enumerate exact core causal and context field gaps" in {
        Given("four independent literal declarations with every selected field absent and nested aggregate absence")
        val supplied = _absences.map(_absence_fixture)
        When("each declaration is admitted projected rendered and the independent expected publication is read")
        val graphs = supplied.map(_publish)
        val reads = supplied.map(value => CmlCausalMetadata.read(_wire(value)).right.get)
        val wires = graphs.map(CmlCausalMetadata.toJson)
        Then("all owners coordinates reasons details and deterministic family order match the complete gap oracle")
        graphs.zip(supplied).foreach { case (graph, expected) => _expect(graph, expected) }
        reads.zip(supplied).foreach { case (graph, expected) => _expect(graph, expected) }
        wires shouldBe supplied.map(_wire)
      }

      "CM-03C emit only the outer aggregate boundary gap when that boundary is absent" in {
        Given("a literal Entity with an absent boundary and admitted empty other families")
        val record = _record("m", "entity", "entity")
        val absence = Absent(NotRepresented, "No aggregate boundary represented.")
        val extension = _empty_peers + ("cozy.cml.structure" -> Json.obj("schemaVersion" -> "cozy.cml.structure.v1",
          "elements" -> Json.arr(Json.obj("element" -> _element_json(record), "kind" -> "entity", "aggregateBoundary" -> _presence(absence, (value: JsValue) => value))),
          "relations" -> Json.arr()))
        val supplied = Fixture(_root(Vector(record), Vector.empty, extension), Vector(record), Vector.empty,
          Present(Vector.empty), Present(Vector.empty), Present(Vector(AggregateMembership(record, absence))),
          Present(Vector.empty), Present(Vector.empty), Vector(ModelReferences(record, Present(Vector.empty))),
          Vector(CapabilityGap(Some(record), "aggregateBoundaries.aggregateBoundary", absence), _inference))
        When("the literal Entity publication is admitted and projected")
        val graph = _publish(supplied)
        val consumed = CmlCausalMetadata.read(_wire(supplied)).right.get
        Then("no nested aggregate or membership absence is fabricated")
        _expect(graph, supplied)
        _expect(consumed, supplied)
      }

      "CM-04 consume equal anonymous occurrences once and preserve independently ordered payloads and full-source bindings" in {
        Given("two equal anonymous causal owners and one unequal owner with the same name and a different source")
        val supplied = _duplicates(false)
        val reversed = _duplicates(true)
        When("both complete literal peer arrays are admitted built and read")
        val graphs = Vector(supplied, reversed).map(_publish)
        val reads = Vector(supplied, reversed).map(value => CmlCausalMetadata.read(_wire(value)).right.get)
        val wires = graphs.map(CmlCausalMetadata.toJson)
        Then("anonymous occurrence payload order is retained and unequal complete owners bind independently")
        graphs.zip(Vector(supplied, reversed)).foreach { case (graph, expected) => _expect(graph, expected) }
        reads.zip(Vector(supplied, reversed)).foreach { case (graph, expected) => _expect(graph, expected) }
        wires shouldBe Vector(_wire(supplied), _wire(reversed))
      }
    }

    "strict safe admission" which {
      "CM-05 reject each malformed consumed supplier while preserving opaque unrelated objects" in {
        Given("each known peer with a hostile unknown field and unrelated object peers with opaque payloads")
        val supplied = _full()
        val names = Vector("cozy.cml.dynamic", "cozy.cml.use-case", "cozy.cml.structure")
        val labels = Vector("Dynamic", "Use Case", "Structure")
        val invalid = names.map { name =>
          val peers = (supplied.root \ "extensions").as[JsObject]
          supplied.root + ("extensions" -> (peers + (name -> Json.obj("schemaVersion" -> "bad", "hostile\nkey" -> "secret"))))
        }
        val opaque = supplied.copy(root = supplied.root + ("extensions" -> ((supplied.root \ "extensions").as[JsObject] +
          ("org.example.other" -> Json.obj("hostile\nkey" -> Json.arr(JsNull, "secret"))))))
        When("Foundation admits the objects and the Causal builder or reader admits the known suppliers")
        val builds = invalid.map(root => CmlCausalMetadata.build(CmlSemanticMetadata.read(root).right.get))
        val reads = invalid.map(root => CmlCausalMetadata.read(_with_child(root, _child(supplied))))
        val valid = _publish(opaque)
        val validjson = CmlCausalMetadata.toJson(valid)
        Then("each consumed failure uses a fixed safe supplier coordinate and no absence downgrade")
        val expected = names.zip(labels).map { case (name, label) => Vector(CausalDiagnostic(InvalidSupplier,
          "extensions." + name, "The supplied " + label + " metadata is not admitted.")) }
        builds.map(_.left.get) shouldBe expected
        reads.map(_.left.get) shouldBe expected
        validjson shouldBe _wire(opaque)
      }

      "CM-06A enforce own shape and schema precedence" in {
        Given("missing nonobject missing-schema nonstring-schema and unsupported-version owned children")
        val supplied = _full()
        val roots = Vector(supplied.root, _with_child(supplied.root, JsNull),
          _with_child(supplied.root, Json.obj()), _with_child(supplied.root, Json.obj("schemaVersion" -> JsNull)),
          _with_child(supplied.root, Json.obj("schemaVersion" -> "unknown", "hostile\nkey" -> "secret")))
        When("the reader admits the five literal publication variants")
        val results = roots.map(CmlCausalMetadata.read)
        Then("fixed owned coordinates distinguish shape failures from unsupported versions")
        results.map(_.left.get) shouldBe Vector(Vector(_shape("extensions.cozy.cml.causal")),
          Vector(_shape("extensions.cozy.cml.causal")), Vector(_shape("extensions.cozy.cml.causal.schemaVersion")),
          Vector(_shape("extensions.cozy.cml.causal.schemaVersion")), Vector(CausalDiagnostic(UnsupportedSchemaVersion,
            "extensions.cozy.cml.causal.schemaVersion", "The Causal schema version is unsupported.")))
      }

      "CM-06B reject every missing extra wrong-shape or tampered derived field and peer disagreement" in {
        Given("a complete literal child and independent mutations of its fields nested owners references absences counts and order")
        val supplied = _full()
        val child = _child(supplied)
        val missing = child.fields.map { case (name, _) => child - name }.filter(_.value.contains("schemaVersion"))
        val wrong = child.fields.filter(_._1 != "schemaVersion").map { case (name, _) => child + (name -> JsNull) }
        val paths = Vector(Vector("causalLinks", "value", "0", "element", "name"),
          Vector("modelReferences", "1", "references", "value", "1", "origin", "sources", "0", "path"),
          Vector("capabilityGaps", "0", "absence", "detail"),
          Vector("actorOperations", "value", "0", "operations", "value", "0", "boundary"))
        val nested = paths.map(path => _replace(child, path, JsString("forged")))
        val links = (child \ "causalLinks" \ "value").as[Vector[JsValue]]
        val counts = Vector(_replace(child, Vector("causalLinks", "value"), JsArray(links :+ links.head)),
          _replace(child, Vector("causalLinks", "value"), JsArray(links.reverse)))
        val mutations = (missing ++ wrong ++ nested ++ counts :+ (child + ("hostile\nkey" -> JsNull)))
          .map(value => _with_child(supplied.root, value))
        val divergent = _replace(_wire(supplied), Vector("extensions", "cozy.cml.dynamic", "causalLinks", "0", "kind"), JsString("cause"))
        When("the strict reader admits each mutated literal summary against its peer evidence")
        val results = (mutations :+ divergent).map(CmlCausalMetadata.read)
        Then("every mutation fails exact structural correspondence at the fixed child path without exposing payload text")
        results.map(_.left.get).distinct shouldBe Vector(Vector(CausalDiagnostic(InvalidProjection,
          "extensions.cozy.cml.causal", "The Causal projection does not match its admitted semantic evidence.")))
      }

      "CM-09 reject null malformed publication and builder-owned namespace conflicts with safe fixed diagnostics" in {
        Given("null values malicious source and namespace strings a nested null JSON value and a supplied owned child")
        val supplied = _full()
        val malicious = _replace(supplied.root, Vector("elements", "0", "source", "path"), JsString("../hostile-secret"))
        val invalidkey = supplied.root + ("extensions" -> Json.obj("hostile\nkey" -> Json.obj()))
        val nestednull = supplied.root + ("extensions" -> Json.obj("org.example.opaque" -> JsObject(Seq("secret" -> null))))
        When("the untrusted inputs are read and an already supplied owned namespace is built")
        val nullbuild = CmlCausalMetadata.build(null)
        val reads = Vector[JsValue](null, JsNull, Json.arr(), malicious, invalidkey, nestednull).map(CmlCausalMetadata.read)
        val conflict = CmlCausalMetadata.build(CmlSemanticMetadata.read(_wire(supplied)).right.get)
        val nullrender = intercept[IllegalArgumentException](CmlCausalMetadata.toJson(null))
        val nullcanonical = intercept[IllegalArgumentException](CmlCausalMetadata.canonicalJson(null))
        Then("untrusted failures are values with fixed safe messages and null internal rendering remains DbC")
        nullbuild shouldBe Left(Vector(CausalDiagnostic(InvalidShape, "envelope", "An admitted semantic metadata envelope is required.")))
        reads.map(_.left.get).distinct shouldBe Vector(Vector(CausalDiagnostic(PublicationFailure,
          "publication", "The semantic metadata publication is invalid.")))
        conflict shouldBe Left(Vector(CausalDiagnostic(ExtensionConflict, "extensions",
          "The Causal extension namespace is already supplied by the caller.")))
        nullrender.getMessage should include ("An admitted Causal graph is required.")
        nullcanonical.getMessage should include ("An admitted Causal graph is required.")
      }
    }

    "independent permutation properties" which {
      "CM-10 verify qualified pair carrier occurrence and recursive object-key permutations without discards" in {
        Given("independent generated qualified pairs anonymous payload order peer order and recursive key-order choices")
        val choices = for { n <- Gen.choose(1, 100000); reverse <- Gen.oneOf(true, false);
          keys <- Gen.oneOf(true, false); peer <- Gen.oneOf(true, false) } yield (n, reverse, keys, peer)
        When("ScalaCheck projects and consumes each independently authored publication oracle")
        val property = Prop.forAll(choices) { case (n, reverse, keys, peer) =>
          val duplicate = _duplicates(reverse)
          val pair = _record("m:" + n, "id", "query")
          val other = _record("m", n + ":id", "query")
          val records = duplicate.records ++ Vector(pair, other)
          val references = duplicate.references ++ Vector(ModelReferences(pair, Present(Vector.empty)), ModelReferences(other, Present(Vector.empty)))
          val links = duplicate.links.asInstanceOf[Present[Vector[CausalLinkProjection]]].value
          val peerlinks = if (peer) Vector(links(0), links(2), links(1)) else Vector(links(1), links(0), links(2))
          val extensions = (duplicate.root \ "extensions").as[JsObject]
          val dynamic = (extensions \ "cozy.cml.dynamic").as[JsObject] + ("causalLinks" -> JsArray(peerlinks.map(_causal_json)))
          val root = duplicate.root + ("elements" -> JsArray(records.map(_element_json))) +
            ("extensions" -> (extensions + ("cozy.cml.dynamic" -> dynamic)))
          val supplied = duplicate.copy(root = root, records = records, references = references)
          val expected = _wire(supplied)
          val input = if (keys) _permute_keys(root) else root
          val publication = if (keys) _permute_keys(expected) else expected
          val graph = CmlCausalMetadata.build(CmlSemanticMetadata.read(input).right.get).right.get
          val consumed = CmlCausalMetadata.read(publication).right.get
          CmlCausalMetadata.toJson(graph) == expected && CmlCausalMetadata.toJson(consumed) == expected &&
            graph.causalLinks == supplied.links && consumed.capabilityGaps == supplied.gaps &&
            graph.catalog.element(ModelElementId("m:" + n, "id")) == Right(pair) &&
            graph.catalog.element(ModelElementId("m", n + ":id")) == Right(other)
        }
        val result = Test.check(Test.Parameters.default.withMinSuccessfulTests(100), property)
        info(s"CM-10 ScalaCheck succeeded=${result.succeeded} discarded=${result.discarded} passed=${result.passed}")
        Then("at least one hundred independent cases pass and none is discarded")
        result.passed shouldBe true
        result.succeeded should be >= 100
        result.discarded shouldBe 0
      }
    }
  }

  private final case class Fixture(root: JsObject, records: Vector[ElementRecord], terms: Vector[TermRecord],
    links: Presence[Vector[CausalLinkProjection]], actors: Presence[Vector[ActorOperations]],
    aggregates: Presence[Vector[AggregateMembership]], rules: Presence[Vector[RuleContext]],
    collaborators: Presence[Vector[CollaboratorContext]], references: Vector[ModelReferences], gaps: Vector[CapabilityGap])

  private val _absences = Vector(Absent(NotDeclared, "Not declared."), Absent(NotRepresented, "Not represented."),
    Absent(Unsupported, "Unsupported."), Absent(NotApplicable, "Not applicable."))
  private val _inference = CapabilityGap(None, "causalInference", Absent(Unsupported,
    "No causation is inferred from names, membership, generic relations or expression text."))
  private val _actor = _record("m", "actor", "actor")
  private val _query = _record("m", "query", "query")
  private val _view = _record("m", "view", "view")
  private val _component = _record("m", "component", "component")
  private val _term = TermRecord(TermId("v", "term"), _source("v"))
  private val _queryref = _semantic(ModelTarget(ModelElementId("m", "query")), Local, Declared)
  private val _externalref = _semantic(ModelTarget(ModelElementId("m", "component")), External,
    Derived("literal-rule", Vector(_source("input-b"), _source("input-a"), _source("input-b"))))
  private val _viewref = _semantic(ModelTarget(ModelElementId("m", "view")), Local, Declared)
  private val _termref = _semantic(TermTarget(_term.identity), External, Declared)
  private val _operation = _record("m", "operation", "operation").copy(references =
    Present(Vector(_queryref, _externalref, _termref, _viewref, _queryref)))
  private val _event = _record("m", "event", "event")
  private val _aggregate = _record("m", "aggregate", "aggregate")
  private val _entity = _record("m", "entity", "entity")
  private val _rule = _record("m", "rule", "rule")
  private val _workflow = _record("m", "workflow", "workflow")
  private val _activity = _record("m", "activity", "workflow-activity")
  private val _usecase = _record("m", "usecase", "use-case")
  private val _causeowner = _record("m", "cause", "causal-link")
  private val _reactionowner = _record("m", "reaction", "causal-link")
  private val _opref = ModelReference(ModelElementId("m", "operation"), Local)
  private val _eventref = ModelReference(ModelElementId("m", "event"), Local)
  private val _ruleref = ModelReference(ModelElementId("m", "rule"), Local)
  private val _cause = CausalLinkProjection(_causeowner, CmlDynamicMetadata.Cause, Present(_opref), Present(_eventref),
    Present(_ruleref), Present(Vector(ModelReference(ModelElementId("m", "aggregate"), Local), ModelReference(ModelElementId("m", "entity"), Local))))
  private val _reaction = CausalLinkProjection(_reactionowner, CmlDynamicMetadata.Reaction, Present(_eventref), Present(_opref),
    Present(_ruleref), Present(Vector.empty))

  private def _full(): Fixture = {
    val records = Vector(_actor, _operation, _event, _aggregate, _entity, _rule, _workflow, _activity,
      _usecase, _causeowner, _reactionowner, _query, _view, _component)
    val actor = ActorOperations(_actor, Present(Vector(_opref, _opref)))
    val aggregate = AggregateMembership(_aggregate, Present(CmlStructureMetadata.AggregateBoundary(
      Present(CmlStructureMetadata.ModelReference(ModelElementId("m", "aggregate"), Local)), Present("root"))))
    val entity = AggregateMembership(_entity, Present(CmlStructureMetadata.AggregateBoundary(
      Present(CmlStructureMetadata.ModelReference(ModelElementId("m", "aggregate"), Local)), Present("member"))))
    val workflow = RuleContext(_workflow, Present(Vector(_ruleref)))
    val activity = RuleContext(_activity, Present(Vector(_ruleref, _ruleref)))
    val collaborator = CollaboratorContext(_usecase, Present(Vector(ModelReference(ModelElementId("m", "query"), Local),
      ModelReference(ModelElementId("m", "component"), External))))
    val extensions = _peers(Vector(_reaction, _cause), Vector(actor), Vector(entity, aggregate),
      Vector(workflow, activity), Vector(collaborator)) ++ Json.obj("cozy.cml.terminology" -> Json.obj("opaque" -> "Term peer"),
      "cozy.cml.classification" -> Json.obj("opaque" -> Json.arr(1, 1)), "org.example.opaque" -> Json.obj("ordered" -> Json.arr("b", "a", "b")))
    Fixture(_root(records, Vector(_term), extensions), records, Vector(_term), Present(Vector(_cause, _reaction)),
      Present(Vector(actor)), Present(Vector(aggregate, entity)), Present(Vector(workflow, activity)), Present(Vector(collaborator)),
      records.map(record => ModelReferences(record, if (record == _operation) Present(Vector(_queryref, _externalref, _viewref, _queryref)) else Present(Vector.empty))),
      Vector(_inference))
  }

  private def _missing_fixture(records: Vector[ElementRecord], terms: Vector[TermRecord]): Fixture = {
    val dynamic = Absent(NotDeclared, "No admitted Dynamic metadata is supplied.")
    val usecase = Absent(NotDeclared, "No admitted Use Case metadata is supplied.")
    val structure = Absent(NotDeclared, "No admitted Structure metadata is supplied.")
    Fixture(_root(records, terms, Json.obj()), records, terms, dynamic, usecase, structure, dynamic, usecase,
      records.map(record => ModelReferences(record, record.references)), Vector(CapabilityGap(None, "causalLinks", dynamic),
        CapabilityGap(None, "actorOperations", usecase), CapabilityGap(None, "aggregateBoundaries", structure),
        CapabilityGap(None, "ruleContexts", dynamic), CapabilityGap(None, "collaboratorContexts", usecase)) :+ _inference)
  }

  private def _absence_fixture(absence: Absent): Fixture = {
    val owner = _causeowner.copy(identity = absence, references = absence)
    val actorrecord = _actor.copy(identity = absence, references = absence)
    val entityrecord = _entity.copy(identity = absence, references = absence)
    val workflowrecord = _workflow.copy(identity = absence, references = absence)
    val activityrecord = _activity.copy(identity = absence, references = absence)
    val usecaserecord = _usecase.copy(identity = absence, references = absence)
    val records = Vector(owner, actorrecord, entityrecord, workflowrecord, activityrecord, usecaserecord)
    val link = CausalLinkProjection(owner, CmlDynamicMetadata.Cause, absence, absence, absence, absence)
    val actor = ActorOperations(actorrecord, absence)
    val aggregate = AggregateMembership(entityrecord, Present(CmlStructureMetadata.AggregateBoundary(absence, absence)))
    val rules = Vector(RuleContext(workflowrecord, absence), RuleContext(activityrecord, absence))
    val collaborator = CollaboratorContext(usecaserecord, absence)
    val gaps = records.flatMap(record => Vector(CapabilityGap(Some(record), "identity", absence), CapabilityGap(Some(record), "modelReferences.references", absence))) ++
      Vector("causalLinks.source", "causalLinks.target", "causalLinks.rule", "causalLinks.affectedElements").map(field => CapabilityGap(Some(owner), field, absence)) ++
      Vector(CapabilityGap(Some(actorrecord), "actorOperations.operations", absence),
        CapabilityGap(Some(entityrecord), "aggregateBoundaries.aggregate", absence), CapabilityGap(Some(entityrecord), "aggregateBoundaries.membership", absence),
        CapabilityGap(Some(workflowrecord), "ruleContexts.rules", absence), CapabilityGap(Some(activityrecord), "ruleContexts.rules", absence),
        CapabilityGap(Some(usecaserecord), "collaboratorContexts.collaborators", absence), _inference)
    Fixture(_root(records, Vector.empty, _peers(Vector(link), Vector(actor), Vector(aggregate), rules, Vector(collaborator))), records, Vector.empty,
      Present(Vector(link)), Present(Vector(actor)), Present(Vector(aggregate)), Present(rules), Present(Vector(collaborator)),
      records.map(record => ModelReferences(record, absence)), gaps)
  }

  private def _duplicates(reverse: Boolean): Fixture = {
    val absence = Absent(NotDeclared, "Anonymous owner.")
    val record = _causeowner.copy(identity = absence, name = "same", references = Present(Vector.empty))
    val different = record.copy(source = _source("m").copy(path = "model/other.cml"))
    val first = _cause.copy(element = record, rule = Absent(NotApplicable, "No rule."), affectedElements = Present(Vector.empty))
    val second = _reaction.copy(element = record)
    val third = _cause.copy(element = different, affectedElements = Present(Vector.empty))
    val anonymous = if (reverse) Vector(second, first) else Vector(first, second)
    val links = anonymous :+ third
    val records = Vector(_operation.copy(references = Present(Vector.empty)), record, _event, different, record, _rule)
    val expectedlinks = Vector(anonymous.head, third, anonymous.last)
    val gaps = Vector(CapabilityGap(Some(record), "identity", absence), CapabilityGap(Some(different), "identity", absence),
      CapabilityGap(Some(record), "identity", absence), CapabilityGap(Some(record), "causalLinks.rule", Absent(NotApplicable, "No rule.")), _inference)
    Fixture(_root(records, Vector.empty, _peers(Vector(third) ++ anonymous, Vector.empty, Vector.empty, Vector.empty, Vector.empty)),
      records, Vector.empty, Present(expectedlinks), Present(Vector.empty), Present(Vector.empty), Present(Vector.empty), Present(Vector.empty),
      records.map(record => ModelReferences(record, Present(Vector.empty))), gaps)
  }

  private def _publish(supplied: Fixture): Graph = CmlCausalMetadata.build(CmlSemanticMetadata.read(supplied.root).right.get).right.get

  private def _expect(graph: Graph, supplied: Fixture): Unit = {
    graph.catalog.elements shouldBe supplied.records
    graph.catalog.terms shouldBe supplied.terms
    graph.causalLinks shouldBe supplied.links
    graph.actorOperations shouldBe supplied.actors
    graph.aggregateBoundaries shouldBe supplied.aggregates
    graph.ruleContexts shouldBe supplied.rules
    graph.collaboratorContexts shouldBe supplied.collaborators
    graph.modelReferences shouldBe supplied.references
    graph.capabilityGaps shouldBe supplied.gaps
  }

  private def _wire(supplied: Fixture): JsObject = _with_child(supplied.root, _child(supplied))

  private def _child(supplied: Fixture): JsObject = Json.obj("schemaVersion" -> "cozy.cml.causal.v1",
    "causalLinks" -> _presence(supplied.links, (values: Vector[CausalLinkProjection]) => JsArray(values.map(_causal_json))),
    "actorOperations" -> _presence(supplied.actors, (values: Vector[ActorOperations]) => JsArray(values.map(value => _carrier(value.element, "operations", _refs_presence(value.operations))))),
    "aggregateBoundaries" -> _presence(supplied.aggregates, (values: Vector[AggregateMembership]) => JsArray(values.map(_aggregate_carrier))),
    "ruleContexts" -> _presence(supplied.rules, (values: Vector[RuleContext]) => JsArray(values.map(value => _carrier(value.element, "rules", _refs_presence(value.rules))))),
    "collaboratorContexts" -> _presence(supplied.collaborators, (values: Vector[CollaboratorContext]) => JsArray(values.map(value => _carrier(value.element, "collaborators", _refs_presence(value.collaborators))))),
    "modelReferences" -> JsArray(supplied.references.map(value => _carrier(value.element, "references", _presence(value.references,
      (references: Vector[SemanticReference]) => JsArray(references.map(_semantic_json)))))),
    "capabilityGaps" -> JsArray(supplied.gaps.map(value => Json.obj("owner" -> value.owner.fold[JsValue](JsNull)(_element_json),
      "field" -> value.field, "absence" -> _presence(value.absence, (json: JsValue) => json)))))

  private def _root(records: Vector[ElementRecord], terms: Vector[TermRecord], extensions: JsObject): JsObject = Json.obj(
    "schemaVersion" -> "cozy.cml.semantic-metadata.v1", "elements" -> JsArray(records.map(_element_json)),
    "terms" -> JsArray(terms.map(value => Json.obj("identity" -> Json.obj("vocabularyId" -> value.identity.vocabularyId,
      "termId" -> value.identity.termId), "source" -> _source_json(value.source)))), "extensions" -> extensions)

  private def _peers(links: Vector[CausalLinkProjection], actors: Vector[ActorOperations], aggregates: Vector[AggregateMembership],
    rules: Vector[RuleContext], collaborators: Vector[CollaboratorContext]): JsObject = Json.obj(
    "cozy.cml.dynamic" -> Json.obj("schemaVersion" -> "cozy.cml.dynamic.v1", "causalLinks" -> JsArray(links.map(_causal_json)),
      "workflows" -> JsArray(rules.filter(_.element.kind == "workflow").map(_workflow_json)),
      "activities" -> JsArray(rules.filter(_.element.kind == "workflow-activity").map(_activity_json)),
      "flows" -> Json.arr(), "stateMachines" -> Json.arr(), "states" -> Json.arr(), "transitions" -> Json.arr()),
    "cozy.cml.use-case" -> Json.obj("schemaVersion" -> "cozy.cml.use-case.v1", "actors" -> JsArray(actors.map(_actor_json)),
      "useCases" -> JsArray(collaborators.map(_usecase_json))),
    "cozy.cml.structure" -> Json.obj("schemaVersion" -> "cozy.cml.structure.v1", "elements" -> JsArray(aggregates.map(value =>
      _aggregate_carrier(value) + ("kind" -> JsString(value.element.kind)))), "relations" -> Json.arr()))

  private def _empty_peers: JsObject = _peers(Vector.empty, Vector.empty, Vector.empty, Vector.empty, Vector.empty)

  private def _workflow_json(value: RuleContext): JsObject = {
    val declared = value.element == _workflow
    _carrier(value.element, "rules", _refs_presence(value.rules)) ++ Json.obj(
      "purpose" -> _presence(Present("Policy expression text causes no extra edge."), JsString),
      "activities" -> (if (declared) _refs_presence(Present(Vector(ModelReference(ModelElementId("m", "activity"), Local)))) else _empty_refs),
      "flows" -> _empty_refs, "participants" -> _empty_refs, "affectedElements" -> _empty_refs,
      "stateMachines" -> _empty_refs, "operations" -> (if (declared) _refs_presence(Present(Vector(_opref))) else _empty_refs),
      "events" -> (if (declared) _refs_presence(Present(Vector(_eventref))) else _empty_refs), "stateEffects" -> _empty_refs)
  }

  private def _activity_json(value: RuleContext): JsObject = _carrier(value.element, "rules", _refs_presence(value.rules)) ++ Json.obj(
    "workflow" -> _presence(Absent(NotDeclared, "No workflow."), (json: JsValue) => json), "activityKind" -> _presence(Present("action"), JsString),
    "participants" -> _empty_refs, "affectedElements" -> _empty_refs, "operations" -> _empty_refs, "events" -> _empty_refs, "stateEffects" -> _empty_refs)

  private def _actor_json(value: ActorOperations): JsObject = _carrier(value.element, "operations", _refs_presence(value.operations)) ++ Json.obj(
    "actorKind" -> _unset, "summary" -> _unset, "description" -> _unset, "useCases" -> _empty_refs, "workflows" -> _empty_refs,
    "events" -> _empty_refs, "domainElements" -> _empty_refs)

  private def _usecase_json(value: CollaboratorContext): JsObject = _carrier(value.element, "collaborators", _refs_presence(value.collaborators)) ++ Json.obj(
    "declaredId" -> _unset, "summary" -> _unset, "description" -> _unset, "goal" -> _presence(Present("Infer no causes from this goal."), JsString),
    "trigger" -> _unset, "preconditions" -> _empty_refs, "postconditions" -> _empty_refs, "priority" -> _unset, "lifecycleStatus" -> _unset,
    "actorReferences" -> _empty_refs, "flows" -> _empty_refs, "domainElements" -> _empty_refs, "operations" -> _empty_refs,
    "events" -> _empty_refs, "realizingWorkflows" -> _empty_refs)

  private def _causal_json(value: CausalLinkProjection): JsObject = Json.obj("element" -> _element_json(value.element),
    "kind" -> (if (value.kind == CmlDynamicMetadata.Cause) "cause" else "reaction"),
    "source" -> _presence(value.source, _model_json), "target" -> _presence(value.target, _model_json),
    "rule" -> _presence(value.rule, _model_json), "affectedElements" -> _refs_presence(value.affectedElements))

  private def _aggregate_carrier(value: AggregateMembership): JsObject = _carrier(value.element, "aggregateBoundary",
    _presence(value.aggregateBoundary, (boundary: CmlStructureMetadata.AggregateBoundary) => Json.obj(
      "aggregate" -> _presence(boundary.aggregate, (reference: CmlStructureMetadata.ModelReference) =>
        Json.obj("identity" -> _identity_json(reference.identity), "boundary" -> _boundary(reference.boundary))),
      "membership" -> _presence(boundary.membership, JsString))))

  private def _source(authority: String): SourceAttribution = SourceAttribution(authority, "model/source.cml", Some(7))
  private def _record(authority: String, id: String, kind: String): ElementRecord =
    ElementRecord(Present(ModelElementId(authority, id)), kind, "display", _source(authority), Present(Vector.empty))
  private def _semantic(target: SemanticTarget, boundary: ReferenceBoundary, origin: ReferenceOrigin): SemanticReference =
    SemanticReference(target, boundary, Present(RelationId("v", "dependency:literal")), Present(ProfileId("v", "profile:literal")),
      Present("Declared query/view/dependency context."), Present(LocalizedLabel("表示", Some("ja"))), _source("m"), origin)

  private def _element_json(value: ElementRecord): JsObject = Json.obj("identity" -> _presence(value.identity, _identity_json),
    "kind" -> value.kind, "name" -> value.name, "source" -> _source_json(value.source),
    "references" -> _presence(value.references, (references: Vector[SemanticReference]) => JsArray(references.map(_semantic_json))))
  private def _source_json(value: SourceAttribution): JsObject = Json.obj("authorityId" -> value.authorityId, "path" -> value.path,
    "line" -> value.line.fold[JsValue](JsNull)(line => JsNumber(BigDecimal(line))))
  private def _identity_json(value: ModelElementId): JsObject = Json.obj("modelId" -> value.modelId, "elementId" -> value.elementId)
  private def _model_json(value: ModelReference): JsObject = Json.obj("identity" -> _identity_json(value.identity), "boundary" -> _boundary(value.boundary))
  private def _boundary(value: ReferenceBoundary): String = if (value == Local) "local" else "external"
  private def _semantic_json(value: SemanticReference): JsObject = {
    val target = value.target match {
      case ModelTarget(identity) => Json.obj("kind" -> "model-element", "identity" -> _identity_json(identity))
      case TermTarget(identity) => Json.obj("kind" -> "term", "identity" -> Json.obj("vocabularyId" -> identity.vocabularyId, "termId" -> identity.termId))
    }
    val origin = value.origin match {
      case Declared => Json.obj("kind" -> "declared")
      case Derived(ruleid, sources) => Json.obj("kind" -> "derived", "ruleId" -> ruleid, "sources" -> JsArray(sources.map(_source_json)))
    }
    Json.obj("target" -> target, "boundary" -> _boundary(value.boundary),
      "relation" -> _presence(value.relation, (id: RelationId) => Json.obj("vocabularyId" -> id.vocabularyId, "relationId" -> id.relationId)),
      "profile" -> _presence(value.profile, (id: ProfileId) => Json.obj("vocabularyId" -> id.vocabularyId, "profileId" -> id.profileId)),
      "context" -> _presence(value.context, JsString), "preferredLabel" -> _presence(value.preferredLabel,
        (label: LocalizedLabel) => Json.obj("text" -> label.text, "language" -> label.language.fold[JsValue](JsNull)(JsString))),
      "source" -> _source_json(value.source), "origin" -> origin)
  }
  private def _carrier(record: ElementRecord, field: String, value: JsValue): JsObject = Json.obj("element" -> _element_json(record), field -> value)
  private def _refs_presence(value: Presence[Vector[ModelReference]]): JsObject = _presence(value, (values: Vector[ModelReference]) => JsArray(values.map(_model_json)))
  private def _empty_refs: JsObject = Json.obj("status" -> "present", "value" -> Json.arr())
  private def _unset: JsObject = Json.obj("status" -> "absent", "reason" -> "not-declared", "detail" -> "Not declared.")
  private def _presence[A](value: Presence[A], render: A => JsValue): JsObject = value match {
    case Present(item) => Json.obj("status" -> "present", "value" -> render(item))
    case Absent(reason, detail) => Json.obj("status" -> "absent", "reason" -> (reason match {
      case NotDeclared => "not-declared"
      case NotRepresented => "not-represented"
      case Unsupported => "unsupported"
      case NotApplicable => "not-applicable"
    }), "detail" -> detail)
  }
  private def _with_child(root: JsObject, child: JsValue): JsObject = root + ("extensions" -> ((root \ "extensions").as[JsObject] + (namespace -> child)))
  private def _shape(path: String): CausalDiagnostic = CausalDiagnostic(InvalidShape, path, "The Causal value does not match the required v1 shape.")
  private def _replace(value: JsValue, path: Vector[String], replacement: JsValue): JsValue = {
    if (path.isEmpty) replacement
    else value match {
      case obj: JsObject => obj + (path.head -> _replace(obj.value(path.head), path.tail, replacement))
      case JsArray(values) => JsArray(values.updated(path.head.toInt, _replace(values(path.head.toInt), path.tail, replacement)))
      case _ => fail("The literal mutation coordinate must address an existing container.")
    }
  }
  private def _permute_keys(value: JsValue): JsValue = value match {
    case obj: JsObject => JsObject(obj.fields.reverse.map { case (key, child) => key -> _permute_keys(child) })
    case JsArray(values) => JsArray(values.map(_permute_keys))
    case scalar => scalar
  }
}
