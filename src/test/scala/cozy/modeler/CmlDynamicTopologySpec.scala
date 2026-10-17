package cozy.modeler

import org.scalacheck.{Gen, Prop, Test}
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import play.api.libs.json.{JsObject, Json}

import CmlSemanticFoundation._
import CmlDynamicMetadata.{ActivityProjection, CausalLinkProjection, DeclaredExpression, DynamicDiagnostic,
  FlowProjection, InvalidShape, ModelReference, StateMachineProjection, StateProjection,
  TransitionProjection, UnknownNode, UnknownProjection, WorkflowProjection}
import CmlDynamicTopology._

/*
 * @since   Oct.  4, 2026
 * @version Oct. 13, 2026
 * @author  ASAMI, Tomoharu
 */
final class CmlDynamicTopologySpec extends AnyWordSpec with Matchers with GivenWhenThen {
  "Literal Dynamic navigation" should {
    "Catalog declaration order" which {
      "DT-01 retain exactly fifteen node kinds and four link kinds with complete qualified core facts" in {
        Given("all fifteen authored node kinds, same names, qualified component collisions and interleaved declarations")
        val fixture = _fixture()
        val expected = _expected(fixture)
        When("the Catalog and supplier are admitted, topology is built and exact nodes and projections are queried")
        val graph = _admit(fixture)
        val topology = _topology(graph)
        val observed = _observe(topology, fixture)
        val excluded = fixture.excluded.map(record => topology.node(_id(record))) ++
          fixture.links.map(link => topology.node(_id(link.element)))
        Then("full source, identity, Presence and order equal the independently authored facts without extra edges")
        topology.graph should be theSameInstanceAs graph
        topology.nodes shouldBe fixture.nodes
        topology.links shouldBe fixture.links
        observed shouldBe expected
        excluded.foreach(_ shouldBe Left(_unknown_node))
        _expect_graph(graph, fixture)
      }
    }

    "Seven exact projection lookup families" which {
      "DT-02 return complete projections and reject wrong-family, missing and same-name identities" in {
        Given("seven populated families, anonymous payloads and independent names, Terms and source facts")
        val fixture = _anonymous_fixture(false)
        val expected = _expected(fixture)
        val familyids = Vector(_id(fixture.workflows.head.element), _id(fixture.activities.head.element),
          _id(fixture.flows.head.element), _id(fixture.machines.head.element), _id(fixture.states.head.element),
          _id(fixture.transitions.head.element), _id(fixture.causal.head.element))
        val unknowns = Vector(ModelElementId("unknown", "shared"), ModelElementId("other.authority", "workflow"),
          ModelElementId("dynamic.example", "shared"), ModelElementId("dynamic.example", "term-only"),
          ModelElementId("dynamic.example", "src/main/cml/topology.cml"), ModelElementId("dynamic.example", "0")) ++
          _query_names.map(name => ModelElementId("dynamic.example", "anonymous-" + name))
        When("every exact family query and every wrong-family or guessed query is interpreted")
        val topology = _topology(_admit(fixture))
        val observed = _observe(topology, fixture)
        val missing = unknowns.map(id => _queries(topology, id))
        val crossfamily = familyids.zipWithIndex.map { case (id, index) =>
          _queries(topology, id).zipWithIndex.filter(_._2 != index).map(_._1)
        }
        val nonnodes = Vector(topology.node(familyids(2)), topology.node(familyids(5)), topology.node(familyids(6)))
        Then("the full typed payloads survive while every unaddressable family reports its fixed coordinate")
        observed shouldBe expected
        (missing ++ crossfamily).foreach(_.foreach { case (name, result) => result shouldBe Left(_unknown_projection(name)) })
        nonnodes.foreach(_ shouldBe Left(_unknown_node))
        _expect_graph(topology.graph, fixture)
      }
    }

    "Local endpoint indexes" which {
      "DT-03 index each present Local endpoint even opposite an absent or External endpoint" in {
        Given("External equal pairs, four absence reasons, partial edges and literal direct context lists")
        val fixture = _boundary_fixture()
        val expected = _expected(fixture)
        val activity = _id(fixture.nodes(7))
        val otheractivity = _id(fixture.nodes(15))
        val state = _id(fixture.nodes(9))
        val operation = _id(fixture.nodes(10))
        val event = _id(fixture.nodes(12))
        When("the admitted topology answers Local incoming and outgoing queries")
        val topology = _topology(_admit(fixture))
        val observed = _observe(topology, fixture)
        val explicit = Vector(topology.outgoing(activity), topology.incoming(otheractivity), topology.incoming(state),
          topology.outgoing(state), topology.outgoing(operation), topology.incoming(event),
          topology.outgoing(event), topology.incoming(_id(fixture.nodes(14))), topology.outgoing(_id(fixture.nodes.head)))
        Then("partial links keep their opposite Presence and External equal pairs create no local index entry")
        observed shouldBe expected
        explicit shouldBe Vector(Right(Vector(fixture.links(4))), Right(Vector(fixture.links(1))),
          Right(Vector(fixture.links(6))), Right(Vector(fixture.links(3))), Right(Vector(fixture.links(2))),
          Right(Vector(fixture.links(7))), Right(Vector.empty), Right(Vector.empty), Right(Vector.empty))
        topology.links shouldBe fixture.links
      }

      "DT-04 retain duplicate pairs, self-links, cycles and distinct literal cause and reaction declarations" in {
        Given("two distinct cause declarations over one operation/event pair, flow cycles and a state self-link")
        val fixture = _fixture()
        val expected = _expected(fixture)
        When("all topology and family lookup results are interpreted")
        val topology = _topology(_admit(fixture))
        val observed = _observe(topology, fixture)
        val causes = topology.outgoing(_id(fixture.nodes(10)))
        val eventlinks = topology.outgoing(_id(fixture.nodes(12)))
        val actionlinks = topology.outgoing(_id(fixture.nodes(14)))
        val states = topology.outgoing(_id(fixture.nodes(9)))
        Then("only the authored directed edges occur, without reachability, event chains or membership enforcement")
        observed shouldBe expected
        causes shouldBe Right(Vector(fixture.links(2), fixture.links(7)))
        eventlinks shouldBe Right(Vector(fixture.links(0)))
        actionlinks shouldBe Right(Vector(fixture.links(5)))
        states shouldBe Right(Vector(fixture.links(3), fixture.links(6)))
        topology.links.map(_.kind) shouldBe Vector(Reaction, Flow, Cause, Transition, Flow, Reaction, Transition, Cause)
      }
    }

    "Anonymous occurrence binding" which {
      "DT-05 consume equal anonymous records once each in carrier order across interleaved link kinds" in {
        Given("equal anonymous Flow, Transition and causal records with distinct payloads and repeated anonymous nodes")
        val fixture = _anonymous_fixture(false)
        val reversed = _anonymous_fixture(true)
        val expected = _expected(fixture)
        val reverseexpected = _expected(reversed)
        When("both independently authored occurrence orders are admitted, built and queried")
        val first = _topology(_admit(fixture))
        val second = _topology(_admit(reversed))
        val observed = _observe(first, fixture)
        val reverseobserved = _observe(second, reversed)
        val guesses = Vector(first.node(ModelElementId("dynamic.example", "anonymous-entity")),
          first.node(ModelElementId("dynamic.example", "anonymous-workflow")))
        val guessedlinks = _queries(first, ModelElementId("dynamic.example", "anonymous-flow"))
        Then("full payload order and multiplicity survive without synthetic identities or deduplication")
        observed shouldBe expected
        reverseobserved shouldBe reverseexpected
        first.nodes shouldBe fixture.nodes
        second.nodes shouldBe reversed.nodes
        first.links shouldBe fixture.links
        second.links shouldBe reversed.links
        first.links(8).element shouldBe first.links(11).element
        first.links(8).source should not equal first.links(11).source
        guesses.foreach(_ shouldBe Left(_unknown_node))
        guessedlinks.foreach { case (name, result) => result shouldBe Left(_unknown_projection(name)) }
      }
    }

    "Safe total queries" which {
      "DT-06 reject null graphs and all malformed identity components without supplied-value echoes" in {
        Given("null IDs/components, empty components, Unicode edge padding and ISO controls in both positions")
        val fixture = _fixture()
        val components = Vector[String](null, "", " leading", "trailing ", "\tpadding", "padding\n",
          "\u00a0padding", "padding\u2007", "\u202fpadding", "padding\u3000",
          "middle\u0000control", "middle\u001fcontrol", "middle\u007fcontrol", "middle\u0085control", "middle\u009fcontrol")
        val ids = Vector[ModelElementId](null) ++ components.flatMap(value =>
          Vector(ModelElementId(value, "known"), ModelElementId("known", value)))
        When("null graph admission and node, incoming, outgoing and all seven typed query families are interpreted")
        val nullgraph = CmlDynamicTopology.build(null)
        val topology = _topology(_admit(fixture))
        val nodequeries = ids.map(id => Vector(topology.node(id), topology.outgoing(id), topology.incoming(id)))
        val projectionqueries = ids.map(id => _queries(topology, id))
        Then("all failures have exact kind, fixed path, punctuation and publication None without throwing")
        nullgraph shouldBe Left(Vector(DynamicDiagnostic(InvalidShape, "graph", "An admitted Dynamic graph is required.", None)))
        nodequeries.foreach(_.foreach(_ shouldBe Left(_malformed("node"))))
        projectionqueries.foreach(_.foreach { case (name, result) => result shouldBe Left(_malformed(name)) })
      }

      "DT-06 distinguish admitted empty and isolated nodes from well-formed missing and non-node identities" in {
        Given("an empty definition, an isolated qualified entity, case differences and unrelated core identities")
        val emptyfixture = Fixture(Vector.empty, Vector.empty, Vector.empty, Vector.empty, Vector.empty,
          Vector.empty, Vector.empty, Vector.empty, Vector.empty, Vector.empty, Vector.empty, Vector.empty, Json.obj())
        val record = _record("entity", "名:𐐀/Case", "A:b/モデル")
        val isolatedfixture = emptyfixture.copy(records = Vector(record), nodes = Vector(record))
        val fixture = _fixture()
        val unknowns = Vector(ModelElementId("missing", "id"), ModelElementId("dynamic.example", "ENTITY"),
          ModelElementId("DYNAMIC.example", "entity")) ++ fixture.excluded.map(_id) ++ fixture.links.map(link => _id(link.element))
        When("the admitted empty, isolated and populated topologies answer exact qualified queries repeatedly")
        val empty = _topology(_admit(emptyfixture))
        val isolated = _topology(_admit(isolatedfixture))
        val populated = _topology(_admit(fixture))
        val known = Vector(isolated.node(_id(record)), isolated.node(_id(record)))
        val noedges = Vector(isolated.outgoing(_id(record)), isolated.incoming(_id(record)))
        val unknown = unknowns.map(id => Vector(populated.node(id), populated.outgoing(id), populated.incoming(id)))
        val emptyqueries = _queries(empty, _id(record))
        val emptynode = empty.node(_id(record))
        Then("qualified Unicode and delimiters stay exact, known nodes have successful empty indexes and unknowns fail")
        empty.nodes shouldBe Vector.empty
        empty.links shouldBe Vector.empty
        isolated.nodes shouldBe Vector(record)
        isolated.links shouldBe Vector.empty
        known shouldBe Vector.fill(2)(Right(record))
        noedges.foreach(_ shouldBe Right(Vector.empty))
        unknown.foreach(_.foreach(_ shouldBe Left(_unknown_node)))
        emptyqueries.foreach { case (name, result) => result shouldBe Left(_unknown_projection(name)) }
        emptynode shouldBe Left(_unknown_node)
      }
    }

    "Unchanged supplier publication" which {
      "DT-07 retain admitted Structure and Classification facts alongside literal Dynamic navigation" in {
        Given("independently authored Structure elements and association, Classification facts and complete Dynamic expectations")
        val fixture = _fixture()
        val expected = _expected(fixture)
        val structureelements = Vector(CmlStructureMetadata.ElementProjection(fixture.nodes(0), CmlStructureMetadata.Entity, _gaps(0)),
          CmlStructureMetadata.ElementProjection(fixture.nodes(1), CmlStructureMetadata.Value, _gaps(1)),
          CmlStructureMetadata.ElementProjection(fixture.nodes(2), CmlStructureMetadata.Aggregate, _gaps(2)))
        val endpoint = CmlStructureMetadata.Endpoint(_gaps(0), _gaps(1), _gaps(2), _gaps(3))
        val semantics = CmlStructureMetadata.RelationSemantics(_gaps(0), _gaps(1), _gaps(2), _gaps(3),
          _gaps(0), _gaps(1), _gaps(2), _gaps(3))
        val relations = Vector(CmlStructureMetadata.RelationProjection(fixture.excluded(2), CmlStructureMetadata.Association,
          endpoint, endpoint, semantics))
        val traits = Vector(CmlClassificationMetadata.TraitProjection(fixture.nodes(3)))
        val powertypes = Vector(CmlClassificationMetadata.PowertypeProjection(fixture.nodes(4),
          Present(Vector(CmlClassificationMetadata.ModelReference(_id(fixture.nodes(5)), Local)))))
        val generalizations = Vector(CmlClassificationMetadata.GeneralizationProjection(fixture.excluded(3),
          Present(CmlClassificationMetadata.ModelReference(_id(fixture.nodes(0)), Local)),
          Present(CmlClassificationMetadata.ModelReference(_id(fixture.nodes(1)), Local))))
        When("all three suppliers publish into one envelope and strict receivers rebuild the topology")
        val catalog = CmlSemanticFoundation.build(fixture.records, fixture.terms).right.get
        val structure = CmlStructureMetadata.build(catalog, structureelements, relations, Json.obj("org.example.peer" -> _peer)).right.get
        val classification = CmlClassificationMetadata.build(catalog, traits, powertypes, generalizations, Vector.empty,
          Vector.empty, structure.envelope.extensions).right.get
        val supplied = fixture.copy(extensions = classification.envelope.extensions)
        val graph = _admit(supplied)
        val canonical = CmlDynamicMetadata.canonicalJson(graph)
        val consumed = _graph(CmlDynamicMetadata.read(Json.parse(canonical)))
        val root = CmlDynamicMetadata.toJson(consumed)
        val readstructure = CmlStructureMetadata.read(root).right.get
        val readclassification = CmlClassificationMetadata.read(root).right.get
        val topology = _topology(consumed)
        val observed = _observe(topology, fixture)
        val rendered = CmlDynamicMetadata.canonicalJson(consumed)
        Then("complete authored predecessor projections remain exact and add no Dynamic edges")
        observed shouldBe expected
        topology.nodes shouldBe fixture.nodes
        topology.links shouldBe fixture.links
        rendered shouldBe canonical
        _expect_graph(consumed, supplied)
        readstructure.elements shouldBe structureelements
        readstructure.relations shouldBe relations
        readclassification.traits shouldBe traits
        readclassification.powertypes shouldBe powertypes
        readclassification.generalizations shouldBe generalizations
        readclassification.traitRealizations shouldBe Vector.empty
        readclassification.powertypeClassifications shouldBe Vector.empty
      }

      "DT-07 rebuild complete topology and query facts through canonical JSON with all absence reasons and opaque peers" in {
        Given("literal expressions, direct context references, Term grounding, source attribution and four authored gaps")
        val fixture = _boundary_fixture()
        val expected = _expected(fixture)
        When("the supplier is rendered, strictly read, rebuilt and queried before a second canonical render")
        val original = _admit(fixture)
        val before = CmlDynamicMetadata.canonicalJson(original)
        val first = _topology(original)
        val read = _graph(CmlDynamicMetadata.read(Json.parse(before)))
        val rebuilt = _topology(read)
        val observed = Vector(_observe(first, fixture), _observe(rebuilt, fixture), _observe(rebuilt, fixture))
        val after = CmlDynamicMetadata.canonicalJson(read)
        val originalafter = CmlDynamicMetadata.canonicalJson(original)
        val root = CmlDynamicMetadata.toJson(read)
        Then("canonical bytes and every full core, carrier, peer, node and link fact remain unchanged")
        observed shouldBe Vector.fill(3)(expected)
        after shouldBe before
        originalafter shouldBe before
        first.graph should be theSameInstanceAs original
        _expect_graph(original, fixture)
        _expect_graph(read, fixture)
        (root \ "extensions" \ "org.example.peer").as[JsObject] shouldBe _peer
        (root \ "extensions" \ "cozy.cml.structure").as[JsObject] shouldBe _structure_peer
        (root \ "extensions" \ "cozy.cml.classification").as[JsObject] shouldBe _classification_peer
      }
    }

    "Generated literal navigation" which {
      "DT-08 preserve complete facts for one hundred non-discarded qualified and independently permuted cases" in {
        Given("valid authorities, Unicode/case/delimiter IDs, endpoint boundaries, Presence and independent declaration permutations")
        val generator = for {
          authority <- Gen.oneOf("dynamic.example", "Case.Name", "a:b", "a", "モデル:α/Case")
          suffix <- Gen.oneOf("名/β", "Name:One", "name:one", "punctuation.!", "𐐀Case", "b:c")
          modes <- Gen.listOfN(16, Gen.choose(0, 5))
          catalogorder <- Gen.pick(31, 0 until 31).map(_.toVector)
          carrierorders <- Gen.listOfN(6, Gen.oneOf(false, true))
          causalorder <- Gen.pick(4, 0 until 4).map(_.toVector)
        } yield _generated_fixture(authority, suffix, modes.toVector, catalogorder, carrierorders.toVector, causalorder)
        When("ScalaCheck interprets the supplier, receiver, topology and all typed queries inside its property action")
        val result = Test.check(Test.Parameters.default.withMinSuccessfulTests(100), Prop.forAll(generator) { fixture =>
          val expected = _expected(fixture)
          val graph = _admit(fixture)
          val canonical = CmlDynamicMetadata.canonicalJson(graph)
          val consumed = _graph(CmlDynamicMetadata.read(Json.parse(canonical)))
          val first = _topology(graph)
          val rebuilt = _topology(consumed)
          val observed = Vector(_observe(first, fixture), _observe(first, fixture), _observe(rebuilt, fixture))
          val after = CmlDynamicMetadata.canonicalJson(consumed)
          observed shouldBe Vector.fill(3)(expected)
          first.nodes shouldBe fixture.nodes
          first.links shouldBe fixture.links
          rebuilt.nodes shouldBe fixture.nodes
          rebuilt.links shouldBe fixture.links
          _expect_graph(graph, fixture)
          _expect_graph(consumed, fixture)
          after shouldBe canonical
          true
        })
        Then("the active property has at least one hundred successes and zero discarded cases")
        result.passed shouldBe true
        result.succeeded should be >= 100
        result.discarded shouldBe 0
      }
    }
  }

  // Fixture data and expected facts below are authored before interpretation.
  private final case class Fixture(
    records: Vector[ElementRecord], terms: Vector[TermRecord], nodes: Vector[ElementRecord], links: Vector[TopologyLink],
    workflows: Vector[WorkflowProjection], activities: Vector[ActivityProjection], flows: Vector[FlowProjection],
    machines: Vector[StateMachineProjection], states: Vector[StateProjection], transitions: Vector[TransitionProjection],
    causal: Vector[CausalLinkProjection], excluded: Vector[ElementRecord], extensions: JsObject
  )

  private final case class Observation(
    nodes: Vector[Either[DynamicDiagnostic, ElementRecord]],
    outgoing: Vector[Either[DynamicDiagnostic, Vector[TopologyLink]]],
    incoming: Vector[Either[DynamicDiagnostic, Vector[TopologyLink]]],
    projections: Vector[Vector[Either[DynamicDiagnostic, Any]]]
  )

  private val _query_names = Vector("workflow", "activity", "flow", "stateMachine", "state", "transition", "causalLink")
  private val _gaps = Vector(Absent(NotDeclared, "No endpoint declared."), Absent(NotRepresented, "Endpoint not represented."),
    Absent(Unsupported, "Endpoint unsupported."), Absent(NotApplicable, "Endpoint not applicable."))
  private val _peer = Json.obj("ordered" -> Json.arr("z", "a", Json.obj("β" -> "literal")), "opaque" -> true)
  private val _structure_peer = Json.obj("opaque" -> "unchanged Structure facts")
  private val _classification_peer = Json.obj("opaque" -> "unchanged Classification facts")
  private val _unknown_node = DynamicDiagnostic(UnknownNode, "node.identity", "The Dynamic node identity is unknown or unaddressable.", None)

  private def _unknown_projection(name: String): DynamicDiagnostic = {
    DynamicDiagnostic(UnknownProjection, name + ".identity", "The Dynamic projection identity is unknown or unaddressable.", None)
  }

  private def _malformed(name: String): DynamicDiagnostic = {
    DynamicDiagnostic(InvalidShape, name + ".identity", "The Dynamic query identity is invalid.", None)
  }

  private def _source(authority: String): SourceAttribution = {
    SourceAttribution(authority, "src/main/cml/topology.cml", Some(11))
  }

  private def _record(kind: String, elementid: String, authority: String = "dynamic.example"): ElementRecord = {
    ElementRecord(Present(ModelElementId(authority, elementid)), kind, "shared", _source(authority), Present(Vector.empty))
  }

  private def _id(record: ElementRecord): ModelElementId = {
    record.identity match {
      case Present(id) => id
      case _: Absent => fail("Only authored present IDs are query inputs.")
    }
  }

  private def _ref(record: ElementRecord, boundary: ReferenceBoundary = Local): Presence[ModelReference] = {
    Present(ModelReference(_id(record), boundary))
  }

  private def _refs(records: ElementRecord*): Presence[Vector[ModelReference]] = {
    Present(records.toVector.map(record => ModelReference(_id(record), Local)))
  }

  private def _fixture(authority: String = "dynamic.example", suffix: String = ""): Fixture = {
    val kinds = Vector("entity", "value", "aggregate", "trait", "powertype", "powertype-kind", "workflow",
      "workflow-activity", "state-machine", "state", "operation", "command", "event", "rule", "action")
    val term = TermRecord(TermId(authority, "term-only" + suffix), _source(authority))
    val semantic = SemanticReference(TermTarget(term.identity), Local, Present(RelationId("bok", "declared")),
      Present(ProfileId("bok", "dynamic")), Present("Independent Term fact"), Present(LocalizedLabel("共有", Some("ja"))),
      _source(authority), Declared)
    val initial = kinds.map(kind => _record(kind, kind + suffix, authority)).updated(11,
      _record("command", "operation" + suffix, authority + ":other"))
    val nodes = initial.updated(0, initial.head.copy(references = Present(Vector(semantic)))) ++
      Vector(_record("workflow-activity", "activity:other" + suffix, authority), _record("state", "state:other" + suffix, authority),
        _record("workflow", "workflow:other" + suffix, authority), _record("state-machine", "machine:other" + suffix, authority))
    val expression = DeclaredExpression("条件\n\"quoted\"\\literal", _refs(nodes(10), nodes(12), nodes(13), nodes(12)))
    val workflows = Vector(nodes(6), nodes(17)).map(record => WorkflowProjection(record, Present("Purpose\n共有"),
      _refs(nodes(15), nodes(7), nodes(7)), _gaps(0), _refs(nodes.head), _refs(nodes(3), nodes.head),
      _refs(nodes(18)), _refs(nodes(11), nodes(10)), _refs(nodes(12)), _refs(nodes(13)), _refs(nodes(16))))
    val activities = Vector(nodes(7), nodes(15)).map(record => ActivityProjection(record, _ref(nodes(17)),
      Present(CmlDynamicMetadata.Branch), _refs(nodes.head), _refs(nodes.head), _refs(nodes(10)), _refs(nodes(12)),
      _refs(nodes(13)), _refs(nodes(16))))
    val machines = Vector(nodes(8), nodes(18)).map(record => StateMachineProjection(record, _ref(nodes.head),
      _refs(nodes(3)), _refs(nodes(16), nodes(9), nodes(9)), _gaps(1), _refs(nodes(17))))
    val states = Vector(nodes(9), nodes(16)).map(record => StateProjection(record, _ref(nodes(18)),
      Present(Vector(expression, expression)), _gaps(2)))
    val flowrecords = Vector(_record("workflow-flow", "flow:0" + suffix, authority), _record("workflow-flow", "flow:1" + suffix, authority))
    val transitionrecords = Vector(_record("transition", "transition:0" + suffix, authority), _record("transition", "transition:1" + suffix, authority))
    val causalrecords = Vector.tabulate(4)(index => _record("causal-link", "causal:" + index + suffix, authority))
    val flows = Vector(FlowProjection(flowrecords(0), _ref(nodes(17)), _ref(nodes(7)), _ref(nodes(15)), Present(expression)),
      FlowProjection(flowrecords(1), _ref(nodes(6)), _ref(nodes(15)), _ref(nodes(7)), _gaps(3)))
    val transitions = Vector(TransitionProjection(transitionrecords(0), _ref(nodes(18)), _ref(nodes(9)), _ref(nodes(9)),
      Present(Vector(expression)), Present(expression), Present(Vector(expression)), _refs(nodes(15)), _refs(nodes(17)),
      _refs(nodes.head), _refs(nodes(16))),
      TransitionProjection(transitionrecords(1), _ref(nodes(8)), _ref(nodes(9)), _ref(nodes(16)), _gaps(0), _gaps(1),
        _gaps(2), _refs(nodes(7)), _refs(nodes(6)), _refs(nodes(3)), _gaps(3)))
    val causal = Vector(CausalLinkProjection(causalrecords(0), CmlDynamicMetadata.Cause, _ref(nodes(10)), _ref(nodes(12)),
      _ref(nodes(13)), _refs(nodes.head)),
      CausalLinkProjection(causalrecords(1), CmlDynamicMetadata.Reaction, _ref(nodes(12)), _ref(nodes(14)), _gaps(0), _gaps(1)),
      CausalLinkProjection(causalrecords(2), CmlDynamicMetadata.Cause, _ref(nodes(10)), _ref(nodes(12)), _ref(nodes(13)), _refs(nodes(3))),
      CausalLinkProjection(causalrecords(3), CmlDynamicMetadata.Reaction, _ref(nodes(14)), _ref(nodes(10)), _ref(nodes(13)), _gaps(2)))
    // Expected edges are separate literal declarations, not mapped from supplier carriers.
    val links = Vector(TopologyLink(causalrecords(1), Reaction, _ref(nodes(12)), _ref(nodes(14))),
      TopologyLink(flowrecords(1), Flow, _ref(nodes(15)), _ref(nodes(7))),
      TopologyLink(causalrecords(0), Cause, _ref(nodes(10)), _ref(nodes(12))),
      TopologyLink(transitionrecords(1), Transition, _ref(nodes(9)), _ref(nodes(16))),
      TopologyLink(flowrecords(0), Flow, _ref(nodes(7)), _ref(nodes(15))),
      TopologyLink(causalrecords(3), Reaction, _ref(nodes(14)), _ref(nodes(10))),
      TopologyLink(transitionrecords(0), Transition, _ref(nodes(9)), _ref(nodes(9))),
      TopologyLink(causalrecords(2), Cause, _ref(nodes(10)), _ref(nodes(12))))
    val excluded = Vector("actor", "component", "association", "generalization").map(kind => _record(kind, kind + suffix, authority))
    val records = Vector(nodes(0), links(0).element, nodes(1), links(1).element, nodes(2), links(2).element,
      nodes(3), links(3).element, nodes(4), links(4).element, nodes(5), links(5).element, nodes(6), links(6).element,
      nodes(7), links(7).element) ++ nodes.drop(8) ++ excluded
    Fixture(records, Vector(term), nodes, links, workflows.reverse, activities.reverse, flows.reverse, machines.reverse,
      states.reverse, transitions.reverse, causal.reverse, excluded,
      Json.obj("org.example.peer" -> _peer, "cozy.cml.structure" -> _structure_peer, "cozy.cml.classification" -> _classification_peer))
  }

  private def _boundary_fixture(): Fixture = {
    val base = _fixture()
    val nodes = base.nodes
    val flows = base.flows.map { value =>
      if (_id(value.element).elementId == "flow:0") value.copy(source = _ref(nodes(7)), target = _gaps(0))
      else value.copy(source = _ref(nodes(7), External), target = _ref(nodes(15)))
    }
    val transitions = base.transitions.map { value =>
      if (_id(value.element).elementId == "transition:0") value.copy(fromState = _gaps(1), toState = _ref(nodes(9)))
      else value.copy(fromState = _ref(nodes(9)), toState = _ref(nodes(9), External))
    }
    val causal = base.causal.map { value =>
      _id(value.element).elementId match {
        case "causal:0" => value.copy(source = _ref(nodes(10)), target = _gaps(2))
        case "causal:1" => value.copy(source = _ref(nodes(12), External), target = _ref(nodes(14), External))
        case "causal:2" => value.copy(source = _gaps(3), target = _ref(nodes(12)))
        case "causal:3" => value.copy(source = _gaps(0), target = _gaps(1))
      }
    }
    val links = Vector(base.links(0).copy(source = _ref(nodes(12), External), target = _ref(nodes(14), External)),
      base.links(1).copy(source = _ref(nodes(7), External), target = _ref(nodes(15))),
      base.links(2).copy(source = _ref(nodes(10)), target = _gaps(2)),
      base.links(3).copy(source = _ref(nodes(9)), target = _ref(nodes(9), External)),
      base.links(4).copy(source = _ref(nodes(7)), target = _gaps(0)),
      base.links(5).copy(source = _gaps(0), target = _gaps(1)),
      base.links(6).copy(source = _gaps(1), target = _ref(nodes(9))),
      base.links(7).copy(source = _gaps(3), target = _ref(nodes(12))))
    base.copy(flows = flows, transitions = transitions, causal = causal, links = links)
  }

  private def _anonymous_fixture(reverse: Boolean): Fixture = {
    val base = _fixture()
    def _anonymous_(kind: String): ElementRecord = {
      _record(kind, "anonymous-" + kind).copy(identity = _gaps(1))
    }
    val entity = _anonymous_("entity")
    val workflow = _anonymous_("workflow")
    val activity = _anonymous_("workflow-activity")
    val machine = _anonymous_("state-machine")
    val state = _anonymous_("state")
    val flow = _anonymous_("workflow-flow")
    val transition = _anonymous_("transition")
    val causal = _anonymous_("causal-link")
    val firstflow = FlowProjection(flow, _gaps(0), _ref(base.nodes(7)), _gaps(2), Present(DeclaredExpression("first", _gaps(3))))
    val secondflow = FlowProjection(flow, _gaps(3), _ref(base.nodes(15)), _ref(base.nodes(7)), _gaps(1))
    val firsttransition = base.transitions.head.copy(element = transition, fromState = _ref(base.nodes(9)), toState = _gaps(0))
    val secondtransition = base.transitions.last.copy(element = transition, fromState = _gaps(2), toState = _ref(base.nodes(16)))
    val firstcausal = CausalLinkProjection(causal, CmlDynamicMetadata.Cause, _ref(base.nodes(10)), _ref(base.nodes(12)), _gaps(0), _gaps(1))
    val secondcausal = CausalLinkProjection(causal, CmlDynamicMetadata.Reaction, _ref(base.nodes(14)), _ref(base.nodes(10)), _gaps(2), _gaps(3))
    val flowlinks = Vector(TopologyLink(flow, Flow, _ref(base.nodes(7)), _gaps(2)),
      TopologyLink(flow, Flow, _ref(base.nodes(15)), _ref(base.nodes(7))))
    val transitionlinks = Vector(TopologyLink(transition, Transition, _ref(base.nodes(9)), _gaps(0)),
      TopologyLink(transition, Transition, _gaps(2), _ref(base.nodes(16))))
    val causallinks = Vector(TopologyLink(causal, Cause, _ref(base.nodes(10)), _ref(base.nodes(12))),
      TopologyLink(causal, Reaction, _ref(base.nodes(14)), _ref(base.nodes(10))))
    def _order_[A](values: Vector[A]): Vector[A] = { if (reverse) values.reverse else values }
    val orderedflows = _order_(flowlinks)
    val orderedtransitions = _order_(transitionlinks)
    val orderedcausal = _order_(causallinks)
    val links = base.links ++ Vector(orderedflows(0), orderedcausal(0), orderedtransitions(0), orderedflows(1), orderedtransitions(1), orderedcausal(1))
    val records = base.records ++ Vector(entity, flow, workflow, causal, activity, transition, entity, flow, machine, transition, state, causal)
    base.copy(records = records, nodes = base.nodes ++ Vector(entity, workflow, activity, entity, machine, state), links = links,
      workflows = base.workflows :+ WorkflowProjection(workflow, Present("anonymous purpose"), _gaps(0), _gaps(1), _gaps(2),
        _gaps(3), _gaps(0), _gaps(1), _gaps(2), _gaps(3), _gaps(0)),
      activities = base.activities :+ ActivityProjection(activity, _gaps(0), Present(CmlDynamicMetadata.Merge), _gaps(1),
        _gaps(2), _gaps(3), _gaps(0), _gaps(1), _gaps(2)),
      machines = base.machines :+ StateMachineProjection(machine, _gaps(0), _gaps(1), _gaps(2), _gaps(3), _gaps(0)),
      states = base.states :+ StateProjection(state, _gaps(0), Present(Vector(DeclaredExpression("anonymous action", _gaps(1)))), _gaps(2)),
      flows = base.flows ++ _order_(Vector(firstflow, secondflow)),
      transitions = base.transitions ++ _order_(Vector(firsttransition, secondtransition)),
      causal = base.causal ++ _order_(Vector(firstcausal, secondcausal)))
  }

  private def _generated_fixture(
    authority: String, suffix: String, modes: Vector[Int], catalogorder: Vector[Int],
    carrierorders: Vector[Boolean], causalorder: Vector[Int]
  ): Fixture = {
    val base = _fixture(authority, suffix)
    def _endpoint_(record: ElementRecord, mode: Int): Presence[ModelReference] = {
      mode match {
        case 0 => _ref(record)
        case 1 => _ref(record, External)
        case index => _gaps(index - 2)
      }
    }
    // Endpoint plans are authored independently and then supplied to both input and expected facts.
    val endpointrecords = Vector((base.nodes(12), base.nodes(14)), (base.nodes(15), base.nodes(7)),
      (base.nodes(10), base.nodes(12)), (base.nodes(9), base.nodes(16)), (base.nodes(7), base.nodes(15)),
      (base.nodes(14), base.nodes(10)), (base.nodes(9), base.nodes(9)), (base.nodes(10), base.nodes(12)))
    val endpoints = endpointrecords.zipWithIndex.map { case ((source, target), index) =>
      (_endpoint_(source, modes(index * 2)), _endpoint_(target, modes(index * 2 + 1)))
    }
    val links = base.links.zip(endpoints).map { case (link, (source, target)) => link.copy(source = source, target = target) }
    val flows = base.flows.map { value =>
      val endpoints = if (_id(value.element).elementId == "flow:0" + suffix) (links(4).source, links(4).target)
        else (links(1).source, links(1).target)
      value.copy(source = endpoints._1, target = endpoints._2)
    }
    val transitions = base.transitions.map { value =>
      val endpoints = if (_id(value.element).elementId == "transition:0" + suffix) (links(6).source, links(6).target)
        else (links(3).source, links(3).target)
      value.copy(fromState = endpoints._1, toState = endpoints._2)
    }
    val causal = Vector(2, 0, 7, 5).map { index =>
      val declaration = base.causal.find(_.element == links(index).element).get
      declaration.copy(source = endpoints(index)._1, target = endpoints(index)._2)
    }
    val declarations = base.nodes ++ links.map(_.element) ++ base.excluded
    val records = catalogorder.map(declarations)
    val nodes = catalogorder.collect { case index if index < 19 => base.nodes(index) }
    val orderedlinks = catalogorder.collect { case index if index >= 19 && index < 27 => links(index - 19) }
    def _order_[A](values: Vector[A], index: Int): Vector[A] = { if (carrierorders(index)) values.reverse else values }
    base.copy(records = records, nodes = nodes, links = orderedlinks, workflows = _order_(base.workflows, 0),
      activities = _order_(base.activities, 1), flows = _order_(flows, 2), machines = _order_(base.machines, 3),
      states = _order_(base.states, 4), transitions = _order_(transitions, 5), causal = causalorder.map(causal))
  }

  private def _expected(fixture: Fixture): Observation = {
    val addressable = fixture.nodes.collect { case record @ ElementRecord(Present(_), _, _, _, _) => record }
    val outgoing = addressable.map { record =>
      Right(fixture.links.filter(link => link.source == Present(ModelReference(_id(record), Local))))
    }
    val incoming = addressable.map { record =>
      Right(fixture.links.filter(link => link.target == Present(ModelReference(_id(record), Local))))
    }
    def _present_[A](values: Vector[A], record: A => ElementRecord): Vector[Either[DynamicDiagnostic, Any]] = {
      values.collect { case value if record(value).identity.isInstanceOf[Present[_]] => Right(value) }
    }
    Observation(addressable.map(Right(_)), outgoing, incoming,
      Vector(_present_(fixture.workflows, (value: WorkflowProjection) => value.element),
        _present_(fixture.activities, (value: ActivityProjection) => value.element),
        _present_(fixture.flows, (value: FlowProjection) => value.element),
        _present_(fixture.machines, (value: StateMachineProjection) => value.element),
        _present_(fixture.states, (value: StateProjection) => value.element),
        _present_(fixture.transitions, (value: TransitionProjection) => value.element),
        _present_(fixture.causal, (value: CausalLinkProjection) => value.element)))
  }

  // All interpreting helpers are called only beneath When or in its property action.
  private def _admit(fixture: Fixture): CmlDynamicMetadata.Graph = {
    val catalog = CmlSemanticFoundation.build(fixture.records, fixture.terms) match {
      case Right(value) => value
      case Left(diagnostics) => fail("Expected admitted Catalog: " + diagnostics)
    }
    _graph(CmlDynamicMetadata.build(catalog, fixture.workflows, fixture.activities, fixture.flows, fixture.machines,
      fixture.states, fixture.transitions, fixture.causal, fixture.extensions))
  }

  private def _graph(result: Either[Vector[DynamicDiagnostic], CmlDynamicMetadata.Graph]): CmlDynamicMetadata.Graph = {
    result match {
      case Right(value) => value
      case Left(diagnostics) => fail("Expected admitted Dynamic graph: " + diagnostics)
    }
  }

  private def _topology(graph: CmlDynamicMetadata.Graph): Topology = {
    CmlDynamicTopology.build(graph) match {
      case Right(value) => value
      case Left(diagnostics) => fail("Expected admitted topology: " + diagnostics)
    }
  }

  private def _queries(topology: Topology, id: ModelElementId): Vector[(String, Either[DynamicDiagnostic, Any])] = {
    _query_names.zip(Vector(topology.workflow(id), topology.activity(id), topology.flow(id), topology.stateMachine(id),
      topology.state(id), topology.transition(id), topology.causalLink(id)))
  }

  private def _observe(topology: Topology, fixture: Fixture): Observation = {
    val ids = fixture.nodes.collect { case ElementRecord(Present(id), _, _, _, _) => id }
    def _lookups_[A](values: Vector[A], record: A => ElementRecord, query: ModelElementId => Either[DynamicDiagnostic, Any]): Vector[Either[DynamicDiagnostic, Any]] = {
      values.collect { case value if record(value).identity.isInstanceOf[Present[_]] => query(_id(record(value))) }
    }
    Observation(ids.map(topology.node), ids.map(topology.outgoing), ids.map(topology.incoming),
      Vector(_lookups_(fixture.workflows, (value: WorkflowProjection) => value.element, topology.workflow),
        _lookups_(fixture.activities, (value: ActivityProjection) => value.element, topology.activity),
        _lookups_(fixture.flows, (value: FlowProjection) => value.element, topology.flow),
        _lookups_(fixture.machines, (value: StateMachineProjection) => value.element, topology.stateMachine),
        _lookups_(fixture.states, (value: StateProjection) => value.element, topology.state),
        _lookups_(fixture.transitions, (value: TransitionProjection) => value.element, topology.transition),
        _lookups_(fixture.causal, (value: CausalLinkProjection) => value.element, topology.causalLink)))
  }

  private def _expect_graph(graph: CmlDynamicMetadata.Graph, fixture: Fixture): Unit = {
    graph.catalog.elements shouldBe fixture.records
    graph.catalog.terms shouldBe fixture.terms
    graph.workflows shouldBe fixture.workflows
    graph.activities shouldBe fixture.activities
    graph.flows shouldBe fixture.flows
    graph.stateMachines shouldBe fixture.machines
    graph.states shouldBe fixture.states
    graph.transitions shouldBe fixture.transitions
    graph.causalLinks shouldBe fixture.causal
    (graph.envelope.extensions - CmlDynamicMetadata.namespace) shouldBe fixture.extensions
  }
}
