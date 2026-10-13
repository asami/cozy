package cozy.modeler

import org.scalacheck.{Gen, Prop, Test}
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import play.api.libs.json.{JsObject, Json}

import CmlSemanticFoundation._
import CmlClassificationMetadata._
import CmlClassificationTopology._

/*
 * @since   Oct. 3, 2026
 * @version Oct. 13, 2026
 * @author  ASAMI, Tomoharu
 */
final class CmlClassificationTopologySpec extends AnyWordSpec with Matchers with GivenWhenThen {
  "Literal integrated Classification topology" should {
    "TP-01 retain all six node kinds and three directed link kinds in catalog order with full core facts" in {
      Given("qualified same-named nodes, independent Term facts and mixed relation order differing from carrier order")
      val fixture = _fixture()
      val expected = _expected(fixture)
      When("the supplier admits the declarations and the topology is built and queried")
      val graph = _admit(fixture)
      val before = CmlClassificationMetadata.canonicalJson(graph)
      val topology = _topology(graph)
      val observed = _observe(topology, fixture)
      val after = CmlClassificationMetadata.canonicalJson(graph)
      Then("the original graph and full literal node and link vectors survive without mutation or extra nodes")
      topology.graph should be theSameInstanceAs graph
      observed shouldBe expected
      topology.nodes shouldBe fixture.nodes
      topology.links shouldBe fixture.links
      graph.catalog.elements shouldBe fixture.records
      graph.catalog.terms shouldBe fixture.terms
      after shouldBe before
    }

    "TP-02 address every node by its full pair and preserve direction and local catalog link order" in {
      Given("all six kinds with same names, case differences and ambiguous authority delimiters")
      val fixture = _fixture()
      val expected = _expected(fixture)
      When("all exact node incoming outgoing and subject-dimension queries are interpreted")
      val topology = _topology(_admit(fixture))
      val observed = _observe(topology, fixture)
      Then("each qualified record and its original directed links are returned independently")
      observed shouldBe expected
      observed.nodes.size shouldBe fixture.nodes.size
      observed.outgoing(_id(fixture.nodes.head)) shouldBe Right(Vector(fixture.links(1), fixture.links(2), fixture.links(3), fixture.links(4), fixture.links(5)))
      observed.incoming(_id(fixture.nodes(3))) shouldBe Right(Vector(fixture.links(1), fixture.links(6)))
    }

    "TP-02 reject unknown relation unrelated and Term-only identities in every node-family query" in {
      Given("an unknown pair, relation identities, an unrelated event, a Term-only collision and wrong-case identities")
      val fixture = _fixture()
      val unknowns = Vector(ModelElementId("model.example", "unknown"), _id(fixture.generalizations.head.element),
        _id(fixture.realizations.head.element), _id(fixture.dimensions.head.element), _id(fixture.records.last),
        ModelElementId("model.example", "term-only"), ModelElementId("MODEL.example", "entity"),
        ModelElementId("model.example", "ENTITY"))
      When("the topology interprets all four node-family operations on these well-formed pairs")
      val topology = _topology(_admit(fixture))
      val results = unknowns.map(id => Vector(topology.node(id), topology.outgoing(id), topology.incoming(id), topology.dimensions(id)))
      Then("every request returns the fixed UnknownNode diagnostic without guessed resolution")
      results.foreach(_.foreach(_ shouldBe Left(_unknown_node)))
    }
  }

  "Independent declared dimensions" should {
    "TP-03 retain two powertype targets and a third distinct dimension over the same subject and target" in {
      Given("three dimension identities for one entity with duplicate member payloads and distinct carrier order")
      val fixture = _fixture()
      val expected = fixture.dimensions
      When("dimension identity and subject queries are interpreted over admitted topology")
      val graph = _admit(fixture)
      val topology = _topology(graph)
      val dimensions = topology.dimensions(_id(fixture.nodes.head))
      val lookups = expected.map(value => topology.dimension(_id(value.element)))
      val links = topology.links
      Then("all dimensions retain their exact projections in declaration order with no member edges")
      dimensions shouldBe Right(expected)
      lookups shouldBe expected.map(Right(_))
      links shouldBe fixture.links
      links.size shouldBe 7
      graph.powertypes.head.members shouldBe Present(Vector(_reference(fixture.nodes(5)), _reference(fixture.nodes(5))))
    }

    "TP-03 reject unknown nodes and other relation kinds as dimension identities" in {
      Given("well-formed unknown, node, generalization and realization identities")
      val fixture = _fixture()
      val ids = Vector(ModelElementId("model.example", "missing"), _id(fixture.nodes.head),
        _id(fixture.generalizations.head.element), _id(fixture.realizations.head.element))
      When("the topology interprets each dimension identity query")
      val topology = _topology(_admit(fixture))
      val results = ids.map(topology.dimension)
      Then("each yields fixed UnknownDimension without target or name based merging")
      results shouldBe Vector.fill(ids.size)(Left(_unknown_dimension))
    }
  }

  "Local endpoint indexing and literal unresolved facts" should {
    "TP-04 retain External equal pairs unresolved references all absence reasons partial edges cycles and duplicates" in {
      Given("literal external and absent endpoints alongside duplicate self-links and a local cycle")
      val fixture = _boundary_fixture()
      val expected = _expected(fixture)
      When("the topology indexes declared local endpoints and answers every local query")
      val topology = _topology(_admit(fixture))
      val observed = _observe(topology, fixture)
      val entityout = topology.outgoing(_id(fixture.nodes.head))
      val entityin = topology.incoming(_id(fixture.nodes.head))
      val subjectdims = topology.dimensions(_id(fixture.nodes.head))
      Then("full links preserve boundaries and absence details while local maps exclude external equal pairs")
      observed shouldBe expected
      topology.links shouldBe fixture.links
      entityout shouldBe Right(Vector(fixture.links(1), fixture.links(3), fixture.links(7), fixture.links(9), fixture.links(10), fixture.links(12)))
      entityin shouldBe Right(Vector(fixture.links(0), fixture.links(4), fixture.links(6), fixture.links(8), fixture.links(9), fixture.links(10)))
      subjectdims shouldBe Right(Vector(fixture.dimensions(1)))
      fixture.links.slice(3, 7).map(link => if (link.source.isInstanceOf[Absent]) link.source else link.target) shouldBe
        _absences
    }
  }

  "Anonymous occurrence binding" should {
    "TP-05 retain anonymous nodes and identical full relation records with distinct successive payloads" in {
      Given("two identical anonymous relation records separated by other relation kinds and an anonymous node and dimension")
      val fixture = _anonymous_fixture(reverse = false)
      val reversed = _anonymous_fixture(reverse = true)
      val expected = _expected(fixture)
      val reverseexpected = _expected(reversed)
      When("both admitted carrier occurrence orders are interpreted and all local queries completed")
      val topology = _topology(_admit(fixture))
      val reversetopology = _topology(_admit(reversed))
      val observed = _observe(topology, fixture)
      val reverseobserved = _observe(reversetopology, reversed)
      val guessednode = topology.node(ModelElementId("model.example", "anonymous-node"))
      val guesseddimension = topology.dimension(ModelElementId("model.example", "anonymous-dimension"))
      Then("multiplicity and authored occurrence order survive without manufactured addressable identities")
      topology.nodes shouldBe fixture.nodes
      topology.links shouldBe fixture.links
      reversetopology.links shouldBe reversed.links
      observed shouldBe expected
      reverseobserved shouldBe reverseexpected
      guessednode shouldBe Left(_unknown_node)
      guesseddimension shouldBe Left(_unknown_dimension)
      topology.links.head.element shouldBe topology.links(2).element
      topology.links.head.source should not equal (topology.links(2).source)
    }
  }

  "Empty and total topology queries" should {
    "TP-06 distinguish empty graphs and known nodes without declarations from unknown nodes" in {
      Given("an empty catalog definition and one known isolated entity definition")
      val emptyfixture = Fixture(Vector.empty, Vector.empty, Vector.empty, Vector.empty, Vector.empty, Vector.empty,
        Vector.empty, Vector.empty, Vector.empty, Vector.empty)
      val record = _record("entity", "isolated")
      val isolated = emptyfixture.copy(records = Vector(record), nodes = Vector(record))
      When("the definitions are admitted built and their empty or missing query results interpreted")
      val empty = _topology(_admit(emptyfixture))
      val topology = _topology(_admit(isolated))
      val known = Vector(topology.outgoing(_id(record)), topology.incoming(_id(record)), topology.dimensions(_id(record)))
      val unknown = empty.node(_id(record))
      Then("empty vectors are successful local results and no node is invented in the empty graph")
      empty.nodes shouldBe Vector.empty
      empty.links shouldBe Vector.empty
      topology.nodes shouldBe Vector(record)
      topology.links shouldBe Vector.empty
      known.foreach(_ shouldBe Right(Vector.empty))
      unknown shouldBe Left(_unknown_node)
    }

    "TP-06 reject null graph and malformed identity components in all five operations without throwing" in {
      Given("null, empty, Unicode padded and ISO control identity components in both positions")
      val fixture = _fixture()
      val components = Vector[String](null, "", " leading", "trailing ", "\u00a0padding", "padding\u2007",
        "\u202fpadding", "padding\u3000", "middle\u0000control", "middle\u007fcontrol", "middle\u0085control")
      val ids = Vector[ModelElementId](null) ++ components.flatMap(value =>
        Vector(ModelElementId(value, "entity"), ModelElementId("model.example", value)))
      When("null graph admission and every malformed query are interpreted")
      val nullgraph = CmlClassificationTopology.build(null)
      val topology = _topology(_admit(fixture))
      val nodequeries = ids.map(id => Vector(topology.node(id), topology.outgoing(id), topology.incoming(id), topology.dimensions(id)))
      val dimensionqueries = ids.map(topology.dimension)
      Then("fixed typed shape diagnostics precede lookup and never contain supplied values")
      nullgraph shouldBe Left(Vector(ClassificationDiagnostic(InvalidShape, "graph", "An admitted Classification graph is required.", None)))
      nodequeries.foreach(_.foreach(_ shouldBe Left(_malformed("node.identity"))))
      dimensionqueries.foreach(_ shouldBe Left(_malformed("dimension.identity")))
    }

    "TP-06 rebuild and repeat every local query deterministically without changing the graph or bytes" in {
      Given("the same literal admitted graph definition and independent expected results")
      val fixture = _fixture()
      val expected = _expected(fixture)
      When("one graph is built twice and each topology is queried repeatedly between canonical renders")
      val graph = _admit(fixture)
      val before = CmlClassificationMetadata.canonicalJson(graph)
      val first = _topology(graph)
      val second = _topology(graph)
      val observations = Vector(_observe(first, fixture), _observe(first, fixture), _observe(second, fixture))
      val after = CmlClassificationMetadata.canonicalJson(graph)
      Then("all exact results and the original catalog and canonical envelope are unchanged")
      observations shouldBe Vector.fill(3)(expected)
      first.graph should be theSameInstanceAs graph
      second.graph should be theSameInstanceAs graph
      graph.catalog.elements shouldBe fixture.records
      after shouldBe before
    }
  }

  "Unchanged metadata receiver compatibility" should {
    "TP-07 rebuild exact topology after canonical render parse and strict read retaining peer and typed core facts" in {
      Given("independent full record and edge expectations with typed Term references and opaque peer metadata")
      val fixture = _fixture()
      val expected = _expected(fixture)
      When("the actual metadata writer parser and strict reader complete before topology build and queries")
      val original = _admit(fixture)
      val canonical = CmlClassificationMetadata.canonicalJson(original)
      val parsed = Json.parse(canonical)
      val consumed = _graph_result(CmlClassificationMetadata.read(parsed))
      val topology = _topology(consumed)
      val observed = _observe(topology, fixture)
      val rendered = CmlClassificationMetadata.canonicalJson(consumed)
      val root = CmlClassificationMetadata.toJson(consumed)
      val peer = (root \ "extensions" \ "org.example.peer").as[JsObject]
      Then("the topology and full catalog match literal expectations and the unchanged envelope retains peers")
      observed shouldBe expected
      rendered shouldBe canonical
      consumed.catalog.elements shouldBe fixture.records
      consumed.catalog.terms shouldBe fixture.terms
      consumed.catalog.elements.head.references shouldBe fixture.nodes.head.references
      peer shouldBe _peer
    }
  }

  "Qualified topology permutation properties" should {
    "TP-08 preserve literal identity order boundaries dimensions and metadata for one hundred non-discarded cases" in {
      Given("valid Unicode punctuation and case identities with independent catalog and three carrier permutations")
      val generator = for {
        authority <- Gen.oneOf("model.example", "モデル:α/Case", "a:b", "a", "Case.Name")
        suffix <- Gen.oneOf("名/β", "Name:One", "name:one", "punctuation.!", "𐐀Case")
        endpoints <- Gen.listOfN(14, Gen.choose(0, 5))
        catalogorder <- Gen.listOfN(18, Gen.choose(0, 1000000)).map(ranks => (0 until 18).toVector.sortBy(index => (ranks(index), index)))
        generalizationorder <- Gen.oneOf(Vector(0, 1), Vector(1, 0))
        realizationorder <- Gen.oneOf(Vector(0, 1), Vector(1, 0))
        dimensionorder <- Gen.oneOf(Vector(0, 1, 2), Vector(0, 2, 1), Vector(1, 0, 2), Vector(1, 2, 0), Vector(2, 0, 1), Vector(2, 1, 0))
      } yield (authority, suffix, endpoints.toVector, catalogorder, generalizationorder, realizationorder, dimensionorder)
      val property = Prop.forAll(generator) { case (authority, suffix, endpoints, catalogorder, generalizationorder, realizationorder, dimensionorder) =>
        Given("one authored qualified graph with literal endpoint assignments and independent permutations")
        val fixture = _permuted_fixture(authority, suffix, endpoints, catalogorder, generalizationorder, realizationorder, dimensionorder)
        val expected = _expected(fixture)
        When("admission builds and queries twice and the canonical metadata is parsed strictly read and queried")
        val graph = _admit(fixture)
        val before = CmlClassificationMetadata.canonicalJson(graph)
        val first = _topology(graph)
        val second = _topology(graph)
        val firstresults = _observe(first, fixture)
        val secondresults = _observe(second, fixture)
        val consumed = _graph_result(CmlClassificationMetadata.read(Json.parse(before)))
        val roundtrip = _topology(consumed)
        val roundresults = _observe(roundtrip, fixture)
        val after = CmlClassificationMetadata.canonicalJson(graph)
        val roundbytes = CmlClassificationMetadata.canonicalJson(consumed)
        Then("every full record link local result and independent dimension equals authored expectations")
        firstresults shouldBe expected
        secondresults shouldBe expected
        roundresults shouldBe expected
        graph.catalog.elements shouldBe fixture.records
        consumed.catalog.elements shouldBe fixture.records
        consumed.catalog.terms shouldBe fixture.terms
        first.graph should be theSameInstanceAs graph
        after shouldBe before
        roundbytes shouldBe before
        Prop.passed
      }
      When("ScalaCheck interprets at least one hundred valid cases with no implication or discarded input")
      val result = Test.check(Test.Parameters.default.withMinSuccessfulTests(100), property)
      Then("all generated literal topology and receiver contracts succeed with zero discards")
      result.passed shouldBe true
      result.succeeded should be >= 100
      result.discarded shouldBe 0
    }
  }

  private final case class Fixture(
    records: Vector[ElementRecord], nodes: Vector[ElementRecord], links: Vector[TopologyLink],
    traits: Vector[TraitProjection], powertypes: Vector[PowertypeProjection],
    generalizations: Vector[GeneralizationProjection], realizations: Vector[TraitRealizationProjection],
    dimensions: Vector[PowertypeClassificationProjection], carrier: Vector[PowertypeClassificationProjection], terms: Vector[TermRecord]
  )
  private final case class Observation(
    nodevector: Vector[ElementRecord], linkvector: Vector[TopologyLink],
    nodes: Map[ModelElementId, Either[ClassificationDiagnostic, ElementRecord]],
    outgoing: Map[ModelElementId, Either[ClassificationDiagnostic, Vector[TopologyLink]]],
    incoming: Map[ModelElementId, Either[ClassificationDiagnostic, Vector[TopologyLink]]],
    dimensions: Map[ModelElementId, Either[ClassificationDiagnostic, Vector[PowertypeClassificationProjection]]],
    dimension: Map[ModelElementId, Either[ClassificationDiagnostic, PowertypeClassificationProjection]]
  )

  private val _unknown_node = ClassificationDiagnostic(UnknownNode, "node.identity", "The Classification node identity is unknown or unaddressable.", None)
  private val _unknown_dimension = ClassificationDiagnostic(UnknownDimension, "dimension.identity", "The Classification dimension identity is unknown or unaddressable.", None)
  private val _peer = Json.obj("z" -> Json.arr("retained", Json.obj("β" -> "value")), "a" -> true)
  private val _absences = Vector(Absent(NotDeclared, "No endpoint declared."), Absent(NotRepresented, "Endpoint not represented."),
    Absent(Unsupported, "Endpoint unsupported."), Absent(NotApplicable, "Endpoint not applicable."))

  private def _malformed(path: String): ClassificationDiagnostic =
    ClassificationDiagnostic(InvalidShape, path, "The Classification query identity is invalid.", None)

  private def _source(authority: String): SourceAttribution =
    SourceAttribution(authority, "src/main/cml/classification.cml", Some(7))

  private def _record(kind: String, id: String, authority: String = "model.example"): ElementRecord =
    ElementRecord(Present(ModelElementId(authority, id)), kind, "共有", _source(authority), Present(Vector.empty))

  private def _id(record: ElementRecord): ModelElementId = record.identity match {
    case Present(id) => id
    case _: Absent => fail("Only explicitly addressable fixture records have identity queries.")
  }

  private def _reference(record: ElementRecord, boundary: ReferenceBoundary = Local): ModelReference =
    ModelReference(_id(record), boundary)

  private def _local(record: ElementRecord): Presence[ModelReference] = Present(_reference(record))

  private def _fixture(authority: String = "model.example", suffix: String = ""): Fixture = {
    val semantic = SemanticReference(TermTarget(TermId(authority, "entity" + suffix)), Local,
      Present(RelationId("bok.example", "classifies")), Present(ProfileId("bok.example", "profile")),
      Present("Independent typed core reference."), Present(LocalizedLabel("共有", Some("ja"))), _source(authority), Declared)
    val nodes = Vector(_record("entity", "entity" + suffix, authority).copy(references = Present(Vector(semantic))),
      _record("value", "value" + suffix, authority), _record("aggregate", "aggregate" + suffix, authority),
      _record("trait", "trait" + suffix, authority), _record("powertype", "powertype" + suffix, authority),
      _record("powertype-kind", "member" + suffix, authority), _record("powertype", "powertype-2" + suffix, authority),
      _record("entity", "same" + suffix, "a:b"), _record("entity", "b:same" + suffix, "a"), _record("entity", "Same" + suffix, "a:b"))
    val g1 = GeneralizationProjection(_record("generalization", "g1" + suffix, authority), _local(nodes(0)), _local(nodes(2)))
    val g2 = GeneralizationProjection(_record("generalization", "g2" + suffix, authority), _local(nodes(1)), _local(nodes(0)))
    val r1 = TraitRealizationProjection(_record("trait-realization", "r1" + suffix, authority), _local(nodes(0)), _local(nodes(3)))
    val r2 = TraitRealizationProjection(_record("trait-realization", "r2" + suffix, authority), _local(nodes(2)), _local(nodes(3)))
    val d1 = PowertypeClassificationProjection(_record("powertype-classification", "d1" + suffix, authority), _local(nodes(0)), _local(nodes(4)))
    val d2 = PowertypeClassificationProjection(_record("powertype-classification", "d2" + suffix, authority), _local(nodes(0)), _local(nodes(6)))
    val d3 = PowertypeClassificationProjection(_record("powertype-classification", "d3" + suffix, authority), _local(nodes(0)), _local(nodes(4)))
    val links = Vector(TopologyLink(g2.element, Generalization, g2.subtype, g2.supertype),
      TopologyLink(r1.element, TraitRealization, r1.subject, r1.traitTarget),
      TopologyLink(d2.element, PowertypeClassification, d2.subject, d2.powertype),
      TopologyLink(g1.element, Generalization, g1.subtype, g1.supertype),
      TopologyLink(d1.element, PowertypeClassification, d1.subject, d1.powertype),
      TopologyLink(d3.element, PowertypeClassification, d3.subject, d3.powertype),
      TopologyLink(r2.element, TraitRealization, r2.subject, r2.traitTarget))
    // The unrelated core record is interleaved with declarations in property permutations.
    val records = nodes ++ links.map(_.element) :+ _record("event", "unrelated" + suffix, authority)
    Fixture(records, nodes, links, Vector(TraitProjection(nodes(3))),
      Vector(PowertypeProjection(nodes(4), Present(Vector(_reference(nodes(5)), _reference(nodes(5))))), PowertypeProjection(nodes(6), _absences.head)),
      Vector(g1, g2), Vector(r2, r1), Vector(d2, d1, d3), Vector(d3, d1, d2),
      Vector(TermRecord(TermId(authority, "entity" + suffix), _source(authority)), TermRecord(TermId(authority, "term-only"), _source(authority))))
  }

  private def _boundary_fixture(): Fixture = {
    val base = _fixture()
    val entity = _local(base.nodes(0))
    val value = _local(base.nodes(1))
    val external = Present(_reference(base.nodes(0), External))
    val unresolved = Present(ModelReference(ModelElementId("partner.example", "unknown"), External))
    val endpoints = Vector((external, entity), (entity, unresolved), (external, unresolved),
      (entity, _absences(0)), (_absences(1), entity), (value, _absences(2)), (_absences(3), entity),
      (entity, value), (value, entity), (entity, entity), (entity, entity))
    val gens = endpoints.zipWithIndex.map { case ((source, target), index) =>
      GeneralizationProjection(_record("generalization", "boundary-g" + index), source, target)
    }
    val dims = Vector(PowertypeClassificationProjection(_record("powertype-classification", "external-d"), external, _local(base.nodes(4))),
      PowertypeClassificationProjection(_record("powertype-classification", "absent-target-d"), entity, _absences(0)))
    val links = gens.map(value => TopologyLink(value.element, Generalization, value.subtype, value.supertype)) ++
      dims.map(value => TopologyLink(value.element, PowertypeClassification, value.subject, value.powertype))
    base.copy(records = base.nodes ++ links.map(_.element), links = links, generalizations = gens.reverse,
      realizations = Vector.empty, dimensions = dims, carrier = dims.reverse)
  }

  private def _anonymous_fixture(reverse: Boolean): Fixture = {
    val base = _fixture()
    val anonymous = _record("entity", "anonymous-node").copy(identity = _absences(1))
    val relation = _record("generalization", "anonymous-g").copy(identity = _absences(2))
    val first = GeneralizationProjection(relation, _local(base.nodes(0)), _local(base.nodes(1)))
    val second = GeneralizationProjection(relation, _local(base.nodes(1)), _absences(3))
    val gens = if (reverse) Vector(second, first) else Vector(first, second)
    val realization = base.realizations(1)
    val dimension = base.dimensions.head.copy(element = base.dimensions.head.element.copy(identity = _absences(0)))
    val links = Vector(TopologyLink(relation, Generalization, gens(0).subtype, gens(0).supertype),
      TopologyLink(realization.element, TraitRealization, realization.subject, realization.traitTarget),
      TopologyLink(relation, Generalization, gens(1).subtype, gens(1).supertype),
      TopologyLink(dimension.element, PowertypeClassification, dimension.subject, dimension.powertype))
    base.copy(records = base.nodes ++ Vector(anonymous) ++ links.map(_.element), nodes = base.nodes :+ anonymous,
      links = links, generalizations = gens, realizations = Vector(realization), dimensions = Vector(dimension), carrier = Vector(dimension))
  }

  private def _permuted_fixture(authority: String, suffix: String, assignments: Vector[Int], catalogorder: Vector[Int],
    generalizationorder: Vector[Int], realizationorder: Vector[Int], dimensionorder: Vector[Int]): Fixture = {
    val base = _fixture(authority, suffix)
    def _endpoint_(record: ElementRecord, choice: Int): Presence[ModelReference] = choice match {
      case 0 => _local(record)
      case 1 => Present(_reference(record, External))
      case index => _absences(index - 2)
    }
    val gens = base.generalizations.zipWithIndex.map { case (value, index) =>
      value.copy(subtype = _endpoint_(base.nodes(index), assignments(index * 2)),
        supertype = _endpoint_(base.nodes(2), assignments(index * 2 + 1)))
    }
    val reals = base.realizations.zipWithIndex.map { case (value, index) =>
      value.copy(subject = _endpoint_(if (index == 0) base.nodes(2) else base.nodes(0), assignments(4 + index * 2)),
        traitTarget = _endpoint_(base.nodes(3), assignments(5 + index * 2)))
    }
    val dims = base.dimensions.zipWithIndex.map { case (value, index) =>
      value.copy(subject = _endpoint_(base.nodes(0), assignments(8 + index * 2)),
        powertype = _endpoint_(if (index == 0) base.nodes(6) else base.nodes(4), assignments(9 + index * 2)))
    }
    // Literal slots independently define the mixed catalog order: G2, R1, D2, G1, D1, D3, R2.
    val slots = Vector(TopologyLink(gens(1).element, Generalization, gens(1).subtype, gens(1).supertype),
      TopologyLink(reals(1).element, TraitRealization, reals(1).subject, reals(1).traitTarget),
      TopologyLink(dims(0).element, PowertypeClassification, dims(0).subject, dims(0).powertype),
      TopologyLink(gens(0).element, Generalization, gens(0).subtype, gens(0).supertype),
      TopologyLink(dims(1).element, PowertypeClassification, dims(1).subject, dims(1).powertype),
      TopologyLink(dims(2).element, PowertypeClassification, dims(2).subject, dims(2).powertype),
      TopologyLink(reals(0).element, TraitRealization, reals(0).subject, reals(0).traitTarget))
    val records = catalogorder.map(base.records(_))
    val links = catalogorder.collect { case index if index >= 10 && index < 17 => slots(index - 10) }
    val nodes = catalogorder.collect { case index if index < 10 => base.nodes(index) }
    val ordered = catalogorder.collect { case 12 => dims(0); case 14 => dims(1); case 15 => dims(2) }
    base.copy(records = records, nodes = nodes, links = links, generalizations = generalizationorder.map(gens(_)),
      realizations = realizationorder.map(reals(_)), dimensions = ordered, carrier = dimensionorder.map(dims(_)))
  }

  private def _admit(fixture: Fixture): Graph = {
    val catalog = CmlSemanticFoundation.build(fixture.records, fixture.terms) match {
      case Right(value) => value
      case Left(diagnostics) => fail("Literal catalog admission failed: " + diagnostics)
    }
    _graph_result(CmlClassificationMetadata.build(catalog, fixture.traits, fixture.powertypes, fixture.generalizations,
      fixture.realizations, fixture.carrier, Json.obj("org.example.peer" -> _peer)))
  }

  private def _graph_result(result: Either[Vector[ClassificationDiagnostic], Graph]): Graph = result match {
    case Right(graph) => graph
    case Left(diagnostics) => fail("Literal Classification admission failed: " + diagnostics)
  }

  private def _topology(graph: Graph): Topology = CmlClassificationTopology.build(graph) match {
    case Right(topology) => topology
    case Left(diagnostics) => fail("Admitted topology construction failed: " + diagnostics)
  }

  private def _addressable(records: Vector[ElementRecord]): Vector[ModelElementId] = records.flatMap(_.identity match {
    case Present(id) => Some(id)
    case _: Absent => None
  })

  private def _observe(topology: Topology, fixture: Fixture): Observation = {
    val ids = _addressable(fixture.nodes)
    val dimensionids = _addressable(fixture.dimensions.map(_.element))
    Observation(topology.nodes, topology.links, ids.map(id => id -> topology.node(id)).toMap,
      ids.map(id => id -> topology.outgoing(id)).toMap, ids.map(id => id -> topology.incoming(id)).toMap,
      ids.map(id => id -> topology.dimensions(id)).toMap, dimensionids.map(id => id -> topology.dimension(id)).toMap)
  }

  private def _expected(fixture: Fixture): Observation = {
    val ids = _addressable(fixture.nodes)
    val records = fixture.nodes.collect { case record if record.identity.isInstanceOf[Present[_]] => _id(record) -> Right(record) }.toMap
    Observation(fixture.nodes, fixture.links, records,
      ids.map(id => id -> Right(fixture.links.filter(_.source == Present(ModelReference(id, Local))))).toMap,
      ids.map(id => id -> Right(fixture.links.filter(_.target == Present(ModelReference(id, Local))))).toMap,
      ids.map(id => id -> Right(fixture.dimensions.filter(_.subject == Present(ModelReference(id, Local))))).toMap,
      fixture.dimensions.collect { case value if value.element.identity.isInstanceOf[Present[_]] => _id(value.element) -> Right(value) }.toMap)
  }
}
