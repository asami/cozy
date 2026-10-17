package cozy.modeler

import scala.io.Source
import org.scalacheck.{Gen, Prop, Test}
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import play.api.libs.json._
import CmlSemanticFoundation._
import CmlDynamicMetadata._
import CmlDynamicTopology.{TopologyLink, Topology}

/*
 * @since   Oct.  4, 2026
 * @version Oct. 13, 2026
 * @author  ASAMI, Tomoharu
 */
final class CmlDynamicMetadataFixtureSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  "Fixed Dynamic supplier resources" should {
    "complete declared facts" which {
      "DF-01 preserve all 42 records three Terms and seventeen complete carriers through strict publication" in {
        Given("the declared UTF-8 filename and independent twenty-record seventeen-carrier source oracle")
        val filename = _filename(false)
        val literal = _literal(false)
        When("the accepted prefix and actual resource are read and the independent Catalog is built rendered and consumed")
        val prefix = _prefix()
        val expected = _complete(prefix._2.catalog, literal)
        val result = _exercise(_resource(filename), prefix, expected)
        Then("complete core source identity Presence and canonical wire facts equal independent expectations")
        result shouldBe true
        expected.core.size shouldBe 42
        expected.terms.size shouldBe 3
        Vector(expected.workflows.size, expected.activities.size, expected.flows.size, expected.machines.size,
          expected.states.size, expected.transitions.size, expected.causal.size) shouldBe Vector(1, 5, 5, 1, 2, 1, 2)
      }
      "DF-02 expose exactly 23 full nodes eight literal links and seven qualified lookup families" in {
        Given("independent ordered Flow Transition Cause and Reaction endpoints and twelve new node records")
        val literal = _literal(false)
        When("the actual declared resource supplies topology and every node edge and typed projection query")
        val prefix = _prefix()
        val expected = _complete(prefix._2.catalog, literal)
        val result = _navigation(_right(CmlDynamicMetadata.read(_resource(_filename(false)))), expected)
        Then("Catalog order and full payloads retain Branch Merge declared cycles and Local-only indexes")
        result shouldBe true
      }
    }
    "explicit absence facts" which {
      "DF-03 preserve anonymous Workflow identity and the complete fixed four-reason cycle" in {
        Given("the absence filename and independent complete carriers with projection-plus-field ordinal absences")
        val filename = _filename(true)
        val literal = _literal(true)
        When("the actual absence file is read and independently rebuilt rendered and strictly consumed")
        val prefix = _prefix()
        val expected = _complete(prefix._2.catalog, literal)
        val result = _exercise(_resource(filename), prefix, expected)
        Then("all 42 records three Terms and seventeen projections preserve exact reasons details sources and causal kinds")
        result shouldBe true
        literal.core.head.identity shouldBe Absent(NotRepresented, "No stable Workflow identity is represented.")
      }
      "DF-04 retain anonymous nodes and absent links without Term source name or ordinal identity fallback" in {
        Given("an anonymous Workflow and an independently addressable Term with the same qualified components")
        val literal = _literal(true)
        When("absence topology and all seven projection families node indexes and the separate Term are queried")
        val prefix = _prefix()
        val expected = _complete(prefix._2.catalog, literal)
        val result = _navigation(_right(CmlDynamicMetadata.read(_resource(_filename(true)))), expected)
        Then("23 nodes eight absent links empty known-node indexes and exact unknown Workflow diagnostics remain")
        result shouldBe true
      }
    }
    "predecessor and opaque cohabitation" which {
      "DF-05 preserve the full Classification prefix both predecessor children and all three opaque peers" in {
        Given("both resource filenames and the literal synthetic-caller flow-order peer including its null")
        val filenames = Vector(_filename(false), _filename(true))
        When("both resources pass read rebuild render and strict consumption against the accepted prefix")
        val prefix = _prefix()
        val results = filenames.zipWithIndex.map { case (filename, index) =>
          _exercise(_resource(filename), prefix, _complete(prefix._2.catalog, _literal(index == 1)))
        }
        Then("the first 22 records two Terms all original extension values and the full third peer remain equal")
        results shouldBe Vector(true, true)
      }
    }
  }

  "Fixed Dynamic admission failures" should {
    "closed recursive wire shapes" which {
      "DF-06 reject malformed recursive fields and prioritize unsupported child versions with safe coordinates" in {
        Given("independent single-field mutations and literal expected diagnostic paths across every container")
        val mutations = _shape_mutations
        When("each mutation is applied to actual parsed resource bytes and the strict Dynamic reader interprets it")
        val root = _resource(_filename(false))
        val observed = mutations.map { mutation =>
          CmlDynamicMetadata.read(_change(root, _child_path ++ mutation.path, mutation.update))
        }
        val expected = mutations.map { mutation => Left(Vector(DynamicDiagnostic(mutation.kind,
          _wire_path + mutation.coordinate, mutation.detail, None))) }
        Then("unknown missing extra wrong-type null enum reference identity expression and vector values fail exactly")
        observed shouldBe expected
      }
    }
    "full admitted core binding" which {
      "DF-07 reject embedded-only identity kind and source forgery while canonical core remains accepted" in {
        Given("independent embedded identity kind and source tampering plus a typed forged Workflow record")
        val mutations = Vector(
          Vector("identity", "value", "elementId") -> JsString("workflow:forged"),
          Vector("kind") -> JsString("state-machine"),
          Vector("source", "line") -> JsNumber(99))
        val literal = _literal(false)
        val forged = literal.workflows.head.copy(element = literal.core.head.copy(name = "forged"))
        When("embedded records alone are changed and the typed factory binds a forged projection to the unchanged Catalog")
        val root = _resource(_filename(false))
        val prefix = _prefix()
        val expected = _complete(prefix._2.catalog, literal)
        val observed = mutations.map { case (path, value) =>
          CmlDynamicMetadata.read(_change(root, _child_path ++ Vector("workflows", "0", "element") ++ path, _ => value))
        }
        val catalog = _right(CmlSemanticFoundation.build(expected.core, expected.terms))
        val factory = CmlDynamicMetadata.build(catalog, Vector(forged), expected.activities, expected.flows,
          expected.machines, expected.states, expected.transitions, expected.causal, _extensions(prefix._1))
        val original = _exercise(root, prefix, expected)
        Then("wire and factory diagnostics require complete binding rather than identity name or source-only equality")
        observed shouldBe Vector.fill(3)(Left(Vector(DynamicDiagnostic(InvalidCoreBinding,
          _wire_path + ".workflows[0].element", "The embedded Dynamic record does not exactly match admitted core JSON.", None))))
        factory shouldBe Left(Vector(
          DynamicDiagnostic(InvalidCoreBinding, "workflows[0].element",
            "The Dynamic projection does not bind exactly to an admitted core record.", None),
          DynamicDiagnostic(MissingProjection, "workflows[0]",
            "An admitted Dynamic record lacks its required projection occurrence.", None)))
        original shouldBe true
      }
    }
    "qualified Local and External references" which {
      "DF-08 reject dangling and wrong-kind Local references while retaining corresponding opaque External values" in {
        Given("literal mutation paths across all seven families Cause Reaction and nested expressions")
        val mutations = _reference_mutations
        val literal = _literal(false)
        When("each actual reference is replaced with a Local or External equal or dangling qualified pair")
        val root = _resource(_filename(false))
        val localresults = mutations.map { case (path, id, coordinate) =>
          CmlDynamicMetadata.read(_change(root, _child_path ++ path, _ => _reference_json(_ref(id)))) ==
            Left(Vector(DynamicDiagnostic(InvalidDynamicSemantics, coordinate,
              "The declared Dynamic reference or absence is invalid.", None)))
        }
        val externalresults = mutations.flatMap { case (path, id, _) =>
          Vector(id, "missing:external").map { target =>
            val reference = ModelReference(_id(target), External)
            val changed = _change(root, _child_path ++ path, _ => _reference_json(reference))
            val graph = _right(CmlDynamicMetadata.read(changed))
            val topology = _right(CmlDynamicTopology.build(graph))
            val consumed = _right(CmlDynamicMetadata.read(Json.parse(CmlDynamicMetadata.canonicalJson(graph))))
            val expectedlinks = _external_links(literal, path, reference)
            val indexes = topology.nodes.flatMap { record => record.identity match {
              case Present(identity) => Vector(
                topology.incoming(identity) == Right(expectedlinks.filter(_.target == Present(ModelReference(identity, Local)))),
                topology.outgoing(identity) == Right(expectedlinks.filter(_.source == Present(ModelReference(identity, Local)))))
              case _: Absent => Vector.empty
            }}
            CmlDynamicMetadata.toJson(consumed) == changed && _at(CmlDynamicMetadata.toJson(graph), _child_path ++ path) ==
              _reference_json(reference) && topology.links == expectedlinks && indexes.forall(identity)
          }
        }
        Then("fixed semantic diagnostics reject Local type claims and External payloads survive without indexed endpoints")
        localresults shouldBe Vector.fill(mutations.size)(true)
        externalresults shouldBe Vector.fill(mutations.size * 2)(true)
      }
    }
  }

  "Active fixed-resource properties" should {
    "independent order and object reconstruction" which {
      "DF-09 preserve both fixture modes under 42-record and seven independent carrier permutations" in {
        Given("independent index plans and both literal typed modes with recursive object-key reconstruction")
        val generator = for {
          core <- _permutation(42)
          workflows <- _permutation(1)
          activities <- _permutation(5)
          flows <- _permutation(5)
          machines <- _permutation(1)
          states <- _permutation(2)
          transitions <- _permutation(1)
          causal <- _permutation(2)
          reverse <- Gen.oneOf(false, true)
        } yield (core, workflows, activities, flows, machines, states, transitions, causal, reverse)
        When("ScalaCheck admits at least 100 non-discarded plans and interprets both actual fixture modes in every action")
        val property = Prop.forAll(generator) { plan =>
          Given("one independent permutation plan and literal expected declared and absence facts")
          val (core, workflows, activities, flows, machines, states, transitions, causal, reverse) = plan
          val literals = Vector(_literal(false), _literal(true))
          When("actual resources and the accepted prefix are read rebuilt canonically consumed and navigated")
          val prefix = _prefix()
          val results = literals.zipWithIndex.map { case (literal, index) =>
            val complete = _complete(prefix._2.catalog, literal)
            val expected = complete.copy(core = core.map(complete.core), workflows = workflows.map(complete.workflows),
              activities = activities.map(complete.activities), flows = flows.map(complete.flows), machines = machines.map(complete.machines),
              states = states.map(complete.states), transitions = transitions.map(complete.transitions), causal = causal.map(complete.causal))
            val selections = Vector("workflows" -> workflows, "activities" -> activities, "flows" -> flows,
              "stateMachines" -> machines, "states" -> states, "transitions" -> transitions, "causalLinks" -> causal)
            val permuted = selections.foldLeft(_change(_resource(_filename(index == 1)), Vector("elements"),
              value => JsArray(core.map(value.as[JsArray].value)))) { case (root, (key, order)) =>
              _change(root, _child_path :+ key, value => JsArray(order.map(value.as[JsArray].value)))
            }
            _exercise(_keys(permuted, reverse).as[JsObject], prefix, expected)
          }
          Then("full independent facts wire source Terms peers Catalog-ordered topology and all queries remain equal")
          Prop(results == Vector(true, true))
        }
        val result = Test.check(Test.Parameters.default.withMinSuccessfulTests(100), property)
        Then("the active property succeeds at least 100 times with zero discarded cases")
        result.passed shouldBe true
        result.succeeded should be >= 100
        result.discarded shouldBe 0
      }
    }
  }

  private final case class Expected(
    core: Vector[ElementRecord], terms: Vector[TermRecord], workflows: Vector[WorkflowProjection],
    activities: Vector[ActivityProjection], flows: Vector[FlowProjection], machines: Vector[StateMachineProjection],
    states: Vector[StateProjection], transitions: Vector[TransitionProjection], causal: Vector[CausalLinkProjection]
  )
  private final case class Mutation(path: Vector[String], update: JsValue => JsValue,
    coordinate: String, kind: DynamicDiagnosticKind = InvalidShape,
    detail: String = "The Dynamic value does not match the required v1 shape.")
  private val _wire_path = "extensions.cozy.cml.dynamic"
  private val _child_path = Vector("extensions", "cozy.cml.dynamic")
  private val _peer = "org.example.dynamic-handoff"
  private val _peer_value = Json.obj("producer" -> "synthetic-caller", "declaredFlowOrder" ->
    Json.arr("review-branch", "branch-accept", "branch-reject", "accept-merge", "reject-merge"), "absence" -> JsNull)
  private val _absences = Vector(
    Absent(NotDeclared, "No Dynamic fact is declared."),
    Absent(NotRepresented, "The Dynamic fact is not represented."),
    Absent(Unsupported, "The Dynamic fact is unsupported."),
    Absent(NotApplicable, "The Dynamic fact is not applicable."))

  private def _id(value: String): ModelElementId = { ModelElementId("dynamic.example", value) }
  private def _ref(value: String): ModelReference = { ModelReference(_id(value), Local) }
  private def _refs(values: String*): Presence[Vector[ModelReference]] = { Present(values.toVector.map(_ref)) }
  private def _expression(text: String, reference: String): DeclaredExpression = { DeclaredExpression(text, _refs(reference)) }
  private def _source(line: Int): SourceAttribution = {
    SourceAttribution("dynamic.example", "src/main/cml/dynamic-handoff.cml", Some(line))
  }
  private def _literal(absent: Boolean): Expected = {
    // New Dynamic expectations are independently authored typed data; no reader or supplier is an oracle.
    val declarations = Vector(
      ("workflow", "workflow:fulfillment", 23), ("workflow-activity", "activity:review", 24),
      ("workflow-activity", "activity:branch", 25), ("workflow-activity", "activity:accept", 26),
      ("workflow-activity", "activity:reject", 27), ("workflow-activity", "activity:merge", 28),
      ("workflow-flow", "flow:review-branch", 29), ("workflow-flow", "flow:branch-accept", 30),
      ("workflow-flow", "flow:branch-reject", 31), ("workflow-flow", "flow:accept-merge", 32),
      ("workflow-flow", "flow:reject-merge", 33), ("state-machine", "machine:order", 34),
      ("state", "state:pending", 35), ("state", "state:done", 36), ("transition", "transition:complete", 37),
      ("operation", "operation:complete", 38), ("event", "event:completed", 39), ("rule", "rule:complete", 40),
      ("causal-link", "cause:completed", 41), ("causal-link", "reaction:completed", 42))
    val records = declarations.map { case (kind, id, line) => ElementRecord(
      if (absent && line == 23) Absent(NotRepresented, "No stable Workflow identity is represented.") else Present(_id(id)),
      kind, "共有", _source(line), Absent(NotDeclared, "No semantic references are declared.")) }
    def _presence_[A](projection: Int, field: Int, declared: Presence[A]): Presence[A] = {
      if (absent) _absences((projection + field) % 4) else declared
    }
    val participants = Present(Vector(ModelReference(ModelElementId("external.example", "actor:reviewer"), External)))
    val affected = Present(Vector(ModelReference(ModelElementId("structure.example", "entity:order"), Local),
      ModelReference(ModelElementId("structure.example", "aggregate:order"), Local)))
    val workflow = WorkflowProjection(records(0), _presence_(0, 0, Present("Complete the declared order flow.")),
      _presence_(0, 1, _refs("activity:review", "activity:branch", "activity:accept", "activity:reject", "activity:merge")),
      _presence_(0, 2, _refs("flow:review-branch", "flow:branch-accept", "flow:branch-reject", "flow:accept-merge", "flow:reject-merge")),
      _presence_(0, 3, participants), _presence_(0, 4, affected), _presence_(0, 5, _refs("machine:order")),
      _presence_(0, 6, _refs("operation:complete")), _presence_(0, 7, _refs("event:completed")),
      _presence_(0, 8, _refs("rule:complete")), _presence_(0, 9, _refs("state:done", "transition:complete")))
    val activities = Vector[ActivityKind](Action, Branch, Action, Action, Merge).zipWithIndex.map { case (kind, index) =>
      val ordinal = index + 1
      ActivityProjection(records(index + 1), _presence_(ordinal, 0, Present(_ref("workflow:fulfillment"))),
        _presence_(ordinal, 1, Present(kind)), _presence_(ordinal, 2, participants), _presence_(ordinal, 3, affected),
        _presence_(ordinal, 4, if (index == 2) _refs("operation:complete") else _refs()),
        _presence_(ordinal, 5, if (index == 2) _refs("event:completed") else _refs()),
        _presence_(ordinal, 6, if (index == 1) _refs("rule:complete") else _refs()),
        _presence_(ordinal, 7, if (index == 2) _refs("state:done") else _refs()))
    }
    val endpoints = Vector("review" -> "branch", "branch" -> "accept", "branch" -> "reject", "accept" -> "merge", "reject" -> "merge")
    val flows = endpoints.zipWithIndex.map { case ((source, target), index) =>
      val ordinal = index + 6
      val guard: Presence[DeclaredExpression] = index match {
        case 1 => Present(_expression("approved", "rule:complete"))
        case 2 => Present(_expression("!approved", "rule:complete"))
        case _ => Absent(NotApplicable, "The flow has no declared guard.")
      }
      FlowProjection(records(index + 6), _presence_(ordinal, 0, Present(_ref("workflow:fulfillment"))),
        _presence_(ordinal, 1, Present(_ref("activity:" + source))), _presence_(ordinal, 2, Present(_ref("activity:" + target))),
        _presence_(ordinal, 3, guard))
    }
    val machine = StateMachineProjection(records(11), _presence_(11, 0,
      Present(ModelReference(ModelElementId("structure.example", "aggregate:order"), Local))),
      _presence_(11, 1, affected), _presence_(11, 2, _refs("state:pending", "state:done")),
      _presence_(11, 3, _refs("transition:complete")), _presence_(11, 4, _refs("workflow:fulfillment")))
    val states = Vector(12, 13).map { ordinal => StateProjection(records(ordinal),
      _presence_(ordinal, 0, Present(_ref("machine:order"))),
      _presence_(ordinal, 1, Present(Vector.empty[DeclaredExpression])), _presence_(ordinal, 2, Present(Vector.empty[DeclaredExpression]))) }
    val transition = TransitionProjection(records(14), _presence_(14, 0, Present(_ref("machine:order"))),
      _presence_(14, 1, Present(_ref("state:pending"))), _presence_(14, 2, Present(_ref("state:done"))),
      _presence_(14, 3, Present(Vector(_expression("completed", "event:completed")))),
      _presence_(14, 4, Present(_expression("approved", "rule:complete"))),
      _presence_(14, 5, Present(Vector(_expression("complete()", "operation:complete")))),
      _presence_(14, 6, _refs("activity:accept")), _presence_(14, 7, _refs("workflow:fulfillment")),
      _presence_(14, 8, affected), _presence_(14, 9, _refs("state:done")))
    val causal = Vector(
      CausalLinkProjection(records(18), Cause, _presence_(15, 0, Present(_ref("operation:complete"))),
        _presence_(15, 1, Present(_ref("event:completed"))),
        _presence_(15, 2, Absent(NotApplicable, "The cause has no declared rule.")), _presence_(15, 3, affected)),
      CausalLinkProjection(records(19), Reaction, _presence_(16, 0, Present(_ref("event:completed"))),
        _presence_(16, 1, Present(_ref("activity:accept"))), _presence_(16, 2, Present(_ref("rule:complete"))), _presence_(16, 3, affected)))
    Expected(records, Vector(TermRecord(TermId("dynamic.example", "workflow:fulfillment"), _source(23))),
      Vector(workflow), activities, flows, Vector(machine), states, Vector(transition), causal)
  }
  private def _complete(prefix: Catalog, literal: Expected): Expected = {
    literal.copy(core = prefix.elements ++ literal.core, terms = prefix.terms ++ literal.terms)
  }
  private def _facts(graph: Graph, expected: Expected): Boolean = {
    graph.catalog.elements == expected.core && graph.catalog.terms == expected.terms && graph.workflows == expected.workflows &&
      graph.activities == expected.activities && graph.flows == expected.flows && graph.stateMachines == expected.machines &&
      graph.states == expected.states && graph.transitions == expected.transitions && graph.causalLinks == expected.causal
  }
  private def _extensions(prefix: JsObject): JsObject = {
    (prefix \ "extensions").as[JsObject] + (_peer -> _peer_value)
  }
  private def _exercise(root: JsObject, prefix: (JsObject, CmlClassificationMetadata.Graph), expected: Expected): Boolean = {
    val graph = _right(CmlDynamicMetadata.read(root))
    val catalog = _right(CmlSemanticFoundation.build(expected.core, expected.terms))
    val built = _right(CmlDynamicMetadata.build(catalog, expected.workflows, expected.activities, expected.flows,
      expected.machines, expected.states, expected.transitions, expected.causal, _extensions(prefix._1)))
    val rendered = CmlDynamicMetadata.toJson(graph)
    val canonical = CmlDynamicMetadata.canonicalJson(graph)
    val consumed = _right(CmlDynamicMetadata.read(Json.parse(canonical)))
    val classification = _right(CmlClassificationMetadata.read(root))
    val structure = _right(CmlStructureMetadata.read(root))
    val basestructure = _right(CmlStructureMetadata.read(prefix._1))
    val expectedwire = _expected_wire(expected, prefix)
    val navigation = _navigation(graph, expected)
    _facts(graph, expected) && _facts(built, expected) && _facts(consumed, expected) && root == expectedwire &&
      rendered == expectedwire && CmlDynamicMetadata.toJson(built) == expectedwire &&
      CmlDynamicMetadata.toJson(consumed) == expectedwire && canonical == Json.stringify(_keys(expectedwire, false)) &&
      CmlDynamicMetadata.canonicalJson(built) == canonical && CmlDynamicMetadata.canonicalJson(consumed) == canonical &&
      classification.traits == prefix._2.traits && classification.powertypes == prefix._2.powertypes &&
      classification.generalizations == prefix._2.generalizations && classification.traitRealizations == prefix._2.traitRealizations &&
      classification.powertypeClassifications == prefix._2.powertypeClassifications &&
      structure.elements == basestructure.elements && structure.relations == basestructure.relations &&
      consumed.envelope.extensions == (_extensions(prefix._1) + (namespace -> (expectedwire \ "extensions" \ namespace).get)) && navigation
  }
  private def _navigation(graph: Graph, expected: Expected): Boolean = {
    val topology = _right(CmlDynamicTopology.build(graph))
    // Independent kind admission and typed carrier endpoints, never production topology/query output.
    val prefixkinds = Set("entity", "value", "aggregate", "trait", "powertype", "powertype-kind")
    val newkinds = Set("workflow", "workflow-activity", "state-machine", "state", "operation", "event", "rule")
    val nodes = expected.core.filter(record => prefixkinds(record.kind) || newkinds(record.kind))
    val links = _links(expected)
    val nodequeries = nodes.flatMap { record => record.identity match {
      case Present(id) => Some(topology.node(id) == Right(record) &&
        topology.incoming(id) == Right(links.filter(_.target == Present(ModelReference(id, Local)))) &&
        topology.outgoing(id) == Right(links.filter(_.source == Present(ModelReference(id, Local)))))
      case _: Absent => None
    }}
    val workflowqueries = expected.workflows.map { value => value.element.identity match {
      case Present(id) => topology.workflow(id) == Right(value)
      case _: Absent => topology.workflow(_id("workflow:fulfillment")) == Left(DynamicDiagnostic(UnknownProjection,
        "workflow.identity", "The Dynamic projection identity is unknown or unaddressable.", None))
    }}
    val queries = expected.activities.map(value => topology.activity(_record_id(value.element)) == Right(value)) ++
      expected.flows.map(value => topology.flow(_record_id(value.element)) == Right(value)) ++
      expected.machines.map(value => topology.stateMachine(_record_id(value.element)) == Right(value)) ++
      expected.states.map(value => topology.state(_record_id(value.element)) == Right(value)) ++
      expected.transitions.map(value => topology.transition(_record_id(value.element)) == Right(value)) ++
      expected.causal.map(value => topology.causalLink(_record_id(value.element)) == Right(value))
    val workflowrecord = expected.workflows.head.element
    val workflowquery = topology.node(_id("workflow:fulfillment"))
    val workflowexpected = workflowrecord.identity match {
      case _: Absent => Left(DynamicDiagnostic(UnknownNode, "node.identity",
        "The Dynamic node identity is unknown or unaddressable.", None))
      case _ => Right(workflowrecord)
    }
    topology.nodes == nodes && nodes.size == 23 && topology.links == links && links.size == 8 &&
      nodequeries.forall(identity) && workflowqueries.forall(identity) && queries.forall(identity) &&
      workflowquery == workflowexpected && graph.catalog.term(TermId("dynamic.example", "workflow:fulfillment")) == Right(expected.terms.last)
  }
  private def _links(expected: Expected): Vector[TopologyLink] = {
    val carriers = expected.flows.map(value => TopologyLink(value.element, CmlDynamicTopology.Flow, value.source, value.target)) ++
      expected.transitions.map(value => TopologyLink(value.element, CmlDynamicTopology.Transition, value.fromState, value.toState)) ++
      expected.causal.map(value => TopologyLink(value.element,
        if (value.kind == Cause) CmlDynamicTopology.Cause else CmlDynamicTopology.Reaction, value.source, value.target))
    expected.core.flatMap(record => carriers.filter(_.element == record))
  }
  private def _external_links(literal: Expected, path: Vector[String], reference: ModelReference): Vector[TopologyLink] = {
    val links = _links(literal)
    path match {
      case Vector("flows", "0", "source", "value") => links.updated(0, links(0).copy(source = Present(reference)))
      case Vector("transitions", "0", "toState", "value") => links.updated(5, links(5).copy(target = Present(reference)))
      case Vector("causalLinks", "0", "source", "value") => links.updated(6, links(6).copy(source = Present(reference)))
      case _ => links
    }
  }
  private def _expected_wire(expected: Expected, prefix: (JsObject, CmlClassificationMetadata.Graph)): JsObject = {
    val prefixrecords = prefix._2.catalog.elements.zip((prefix._1 \ "elements").as[Vector[JsValue]]).toMap
    val prefixterms = prefix._2.catalog.terms.zip((prefix._1 \ "terms").as[Vector[JsValue]]).toMap
    def _record_(record: ElementRecord): JsValue = { prefixrecords.getOrElse(record, _record_json(record)) }
    val child = Json.obj("schemaVersion" -> "cozy.cml.dynamic.v1",
      "workflows" -> JsArray(expected.workflows.map { value => Json.obj("element" -> _record_(value.element),
        "purpose" -> _presence_json(value.purpose, JsString), "activities" -> _presence_json(value.activities, _references_json),
        "flows" -> _presence_json(value.flows, _references_json), "participants" -> _presence_json(value.participants, _references_json),
        "affectedElements" -> _presence_json(value.affectedElements, _references_json), "stateMachines" -> _presence_json(value.stateMachines, _references_json),
        "operations" -> _presence_json(value.operations, _references_json), "events" -> _presence_json(value.events, _references_json),
        "rules" -> _presence_json(value.rules, _references_json), "stateEffects" -> _presence_json(value.stateEffects, _references_json)) }),
      "activities" -> JsArray(expected.activities.map { value => Json.obj("element" -> _record_(value.element),
        "workflow" -> _presence_json(value.workflow, _reference_json), "activityKind" -> _presence_json(value.activityKind, _activity_json),
        "participants" -> _presence_json(value.participants, _references_json), "affectedElements" -> _presence_json(value.affectedElements, _references_json),
        "operations" -> _presence_json(value.operations, _references_json), "events" -> _presence_json(value.events, _references_json),
        "rules" -> _presence_json(value.rules, _references_json), "stateEffects" -> _presence_json(value.stateEffects, _references_json)) }),
      "flows" -> JsArray(expected.flows.map { value => Json.obj("element" -> _record_(value.element),
        "workflow" -> _presence_json(value.workflow, _reference_json), "source" -> _presence_json(value.source, _reference_json),
        "target" -> _presence_json(value.target, _reference_json), "guard" -> _presence_json(value.guard, _expression_json)) }),
      "stateMachines" -> JsArray(expected.machines.map { value => Json.obj("element" -> _record_(value.element),
        "owner" -> _presence_json(value.owner, _reference_json), "affectedElements" -> _presence_json(value.affectedElements, _references_json),
        "states" -> _presence_json(value.states, _references_json), "transitions" -> _presence_json(value.transitions, _references_json),
        "workflows" -> _presence_json(value.workflows, _references_json)) }),
      "states" -> JsArray(expected.states.map { value => Json.obj("element" -> _record_(value.element),
        "stateMachine" -> _presence_json(value.stateMachine, _reference_json),
        "entryActions" -> _presence_json(value.entryActions, _expressions_json), "exitActions" -> _presence_json(value.exitActions, _expressions_json)) }),
      "transitions" -> JsArray(expected.transitions.map { value => Json.obj("element" -> _record_(value.element),
        "stateMachine" -> _presence_json(value.stateMachine, _reference_json), "fromState" -> _presence_json(value.fromState, _reference_json),
        "toState" -> _presence_json(value.toState, _reference_json), "triggers" -> _presence_json(value.triggers, _expressions_json),
        "guard" -> _presence_json(value.guard, _expression_json), "actions" -> _presence_json(value.actions, _expressions_json),
        "activities" -> _presence_json(value.activities, _references_json), "workflows" -> _presence_json(value.workflows, _references_json),
        "affectedElements" -> _presence_json(value.affectedElements, _references_json), "stateEffects" -> _presence_json(value.stateEffects, _references_json)) }),
      "causalLinks" -> JsArray(expected.causal.map { value => Json.obj("element" -> _record_(value.element),
        "kind" -> (if (value.kind == Cause) "cause" else "reaction"), "source" -> _presence_json(value.source, _reference_json),
        "target" -> _presence_json(value.target, _reference_json), "rule" -> _presence_json(value.rule, _reference_json),
        "affectedElements" -> _presence_json(value.affectedElements, _references_json)) }))
    Json.obj("schemaVersion" -> "cozy.cml.semantic-metadata.v1", "elements" -> JsArray(expected.core.map(_record_)),
      "terms" -> JsArray(expected.terms.map(record => prefixterms.getOrElse(record, Json.obj(
        "identity" -> Json.obj("vocabularyId" -> record.identity.vocabularyId, "termId" -> record.identity.termId),
        "source" -> _source_json(record.source))))), "extensions" -> (_extensions(prefix._1) + (namespace -> child)))
  }
  private def _record_json(record: ElementRecord): JsObject = {
    Json.obj("identity" -> _presence_json(record.identity, _identity_json), "kind" -> record.kind, "name" -> record.name,
      "source" -> _source_json(record.source), "references" -> Json.obj("status" -> "absent", "reason" -> "not-declared",
        "detail" -> "No semantic references are declared."))
  }
  private def _identity_json(id: ModelElementId): JsObject = { Json.obj("modelId" -> id.modelId, "elementId" -> id.elementId) }
  private def _source_json(source: SourceAttribution): JsObject = {
    Json.obj("authorityId" -> source.authorityId, "path" -> source.path, "line" -> source.line.get)
  }
  private def _presence_json[A](value: Presence[A], render: A => JsValue): JsObject = {
    value match {
      case Present(item) => Json.obj("status" -> "present", "value" -> render(item))
      case Absent(reason, detail) => Json.obj("status" -> "absent", "reason" -> (reason match {
        case NotDeclared => "not-declared"
        case NotRepresented => "not-represented"
        case Unsupported => "unsupported"
        case NotApplicable => "not-applicable"
      }), "detail" -> detail)
    }
  }
  private def _reference_json(value: ModelReference): JsObject = {
    Json.obj("identity" -> _identity_json(value.identity), "boundary" -> (if (value.boundary == Local) "local" else "external"))
  }
  private def _references_json(values: Vector[ModelReference]): JsArray = { JsArray(values.map(_reference_json)) }
  private def _expression_json(value: DeclaredExpression): JsObject = {
    Json.obj("text" -> value.text, "references" -> _presence_json(value.references, _references_json))
  }
  private def _expressions_json(values: Vector[DeclaredExpression]): JsArray = { JsArray(values.map(_expression_json)) }
  private def _activity_json(value: ActivityKind): JsString = {
    JsString(value match {
      case Action => "action"
      case Branch => "branch"
      case Merge => "merge"
      case Fork => "fork"
      case Join => "join"
      case Start => "start"
      case End => "end"
    })
  }

  private val _shape_mutations: Vector[Mutation] = {
    val presence = Vector("flows", "0", "source")
    val reference = presence :+ "value"
    val expression = Vector("flows", "1", "guard", "value")
    val base = Vector(
      Mutation(Vector("schemaVersion"), _ => JsString("hostile/version"), ".schemaVersion", UnsupportedSchemaVersion,
        "The Dynamic schema version is not supported."),
      Mutation(Vector.empty, _ => Json.obj("schemaVersion" -> "hostile/version", "hostile/body" -> JsNull), ".schemaVersion",
        UnsupportedSchemaVersion, "The Dynamic schema version is not supported."),
      Mutation(Vector.empty, value => value.as[JsObject] + ("hostile/key" -> JsNull), ""),
      Mutation(Vector.empty, value => value.as[JsObject] - "activities", ".activities"),
      Mutation(Vector("schemaVersion"), _ => JsNull, ".schemaVersion"),
      Mutation(Vector("workflows"), _ => JsNull, ".workflows"),
      Mutation(Vector("states"), _ => Json.obj(), ".states"),
      Mutation(Vector("flows", "0"), _ => JsNull, ".flows[0]"),
      Mutation(presence, _ => JsNull, ".flows[0].source"),
      Mutation(presence, value => value.as[JsObject] + ("hostile/key" -> JsNull), ".flows[0].source"),
      Mutation(presence, value => value.as[JsObject] - "value", ".flows[0].source.value"),
      Mutation(presence :+ "status", _ => JsString("Present"), ".flows[0].source.status"),
      Mutation(presence, _ => Json.obj("status" -> "absent", "reason" -> "hostile/reason", "detail" -> "safe"), ".flows[0].source.reason"),
      Mutation(presence, _ => Json.obj("status" -> "absent", "reason" -> "not-declared"), ".flows[0].source.detail"),
      Mutation(presence, _ => Json.obj("status" -> "absent", "reason" -> "not-declared", "detail" -> JsNull), ".flows[0].source.detail"),
      Mutation(presence, _ => Json.obj("status" -> "absent", "reason" -> "not-declared", "detail" -> "safe", "value" -> JsNull), ".flows[0].source"),
      Mutation(reference, _ => JsNull, ".flows[0].source.value"),
      Mutation(reference, value => value.as[JsObject] - "boundary", ".flows[0].source.value.boundary"),
      Mutation(reference, value => value.as[JsObject] + ("hostile/key" -> JsNull), ".flows[0].source.value"),
      Mutation(reference :+ "boundary", _ => JsString("LOCAL"), ".flows[0].source.value.boundary"),
      Mutation(reference :+ "identity", _ => JsNull, ".flows[0].source.value.identity"),
      Mutation(reference :+ "identity", value => value.as[JsObject] - "elementId", ".flows[0].source.value.identity.elementId"),
      Mutation(reference :+ "identity", value => value.as[JsObject] + ("hostile/key" -> JsNull), ".flows[0].source.value.identity"),
      Mutation(reference ++ Vector("identity", "modelId"), _ => JsNumber(7), ".flows[0].source.value.identity.modelId"),
      Mutation(expression, _ => JsNull, ".flows[1].guard.value"),
      Mutation(expression, value => value.as[JsObject] - "references", ".flows[1].guard.value.references"),
      Mutation(expression, value => value.as[JsObject] + ("hostile/key" -> JsNull), ".flows[1].guard.value"),
      Mutation(expression :+ "text", _ => JsBoolean(true), ".flows[1].guard.value.text"),
      Mutation(expression ++ Vector("references", "value"), _ => JsNull, ".flows[1].guard.value.references.value"),
      Mutation(Vector("workflows", "0", "activities", "value"), _ => Json.obj(), ".workflows[0].activities.value"),
      Mutation(Vector("workflows", "0", "activities", "value", "0"), _ => JsNull, ".workflows[0].activities.value[0]"),
      Mutation(Vector("activities", "0", "activityKind", "value"), _ => JsString("ACTION"), ".activities[0].activityKind.value"),
      Mutation(Vector("causalLinks", "0", "kind"), _ => JsString("CAUSE"), ".causalLinks[0].kind"),
      Mutation(Vector("transitions", "0", "triggers", "value"), _ => JsString("completed"), ".transitions[0].triggers.value"))
    val projections = Vector("workflows" -> "purpose", "activities" -> "workflow", "flows" -> "guard",
      "stateMachines" -> "owner", "states" -> "stateMachine", "transitions" -> "triggers", "causalLinks" -> "kind")
    base ++ projections.flatMap { case (family, field) => Vector(
      Mutation(Vector(family, "0"), value => value.as[JsObject] - field, "." + family + "[0]." + field),
      Mutation(Vector(family, "0"), value => value.as[JsObject] + ("hostile/key" -> JsNull), "." + family + "[0]")) }
  }
  private val _reference_mutations = Vector(
    (Vector("workflows", "0", "activities", "value", "0"), "missing:activity", "workflows[0].activities.value[0]"),
    (Vector("activities", "0", "workflow", "value"), "state:done", "activities[0].workflow.value"),
    (Vector("flows", "0", "source", "value"), "operation:complete", "flows[0].source.value"),
    (Vector("stateMachines", "0", "states", "value", "0"), "activity:review", "stateMachines[0].states.value[0]"),
    (Vector("states", "0", "stateMachine", "value"), "missing:machine", "states[0].stateMachine.value"),
    (Vector("transitions", "0", "toState", "value"), "rule:complete", "transitions[0].toState.value"),
    (Vector("causalLinks", "0", "source", "value"), "flow:review-branch", "causalLinks[0].source.value"),
    (Vector("causalLinks", "1", "rule", "value"), "event:completed", "causalLinks[1].rule.value"),
    (Vector("flows", "1", "guard", "value", "references", "value", "0"), "flow:review-branch", "flows[1].guard.value.references.value[0]"),
    (Vector("transitions", "0", "triggers", "value", "0", "references", "value", "0"), "missing:event", "transitions[0].triggers.value[0].references.value[0]"),
    (Vector("transitions", "0", "actions", "value", "0", "references", "value", "0"), "flow:accept-merge", "transitions[0].actions.value[0].references.value[0]"))

  private def _record_id(record: ElementRecord): ModelElementId = {
    record.identity match { case Present(id) => id; case _: Absent => fail("An addressable oracle record is required.") }
  }
  private def _permutation(size: Int): Gen[Vector[Int]] = {
    Gen.listOfN(size, Gen.choose(0, Int.MaxValue)).map(values => values.zipWithIndex.sortBy(_._1).map(_._2).toVector)
  }
  private def _at(value: JsValue, path: Vector[String]): JsValue = {
    if (path.isEmpty) value else value match {
      case objectvalue: JsObject => _at(objectvalue.value(path.head), path.tail)
      case JsArray(values) => _at(values(path.head.toInt), path.tail)
      case _ => fail("The authored path requires a container.")
    }
  }
  private def _change(root: JsObject, path: Vector[String], update: JsValue => JsValue): JsObject = {
    def _visit_(value: JsValue, remaining: Vector[String]): JsValue = {
      if (remaining.isEmpty) update(value) else value match {
        case objectvalue: JsObject => objectvalue + (remaining.head -> _visit_(objectvalue.value(remaining.head), remaining.tail))
        case JsArray(values) => JsArray(values.updated(remaining.head.toInt, _visit_(values(remaining.head.toInt), remaining.tail)))
        case _ => fail("The authored mutation path requires a container.")
      }
    }
    _visit_(root, path).as[JsObject]
  }
  private def _keys(value: JsValue, reverse: Boolean): JsValue = {
    value match {
      case objectvalue: JsObject =>
        val fields = objectvalue.fields.sortBy(_._1)
        JsObject((if (reverse) fields.reverse else fields).map { case (key, item) => key -> _keys(item, reverse) })
      case JsArray(values) => JsArray(values.map(_keys(_, reverse)))
      case _ => value
    }
  }
  private def _filename(absent: Boolean): String = {
    if (absent) "dynamic-metadata-v1-absence.json" else "dynamic-metadata-v1-declared.json"
  }
  private def _resource(name: String): JsObject = {
    val stream = getClass.getResourceAsStream("/cozy/modeler/" + name)
    require(stream != null, "The authored fixture resource is required.")
    val source = Source.fromInputStream(stream, "UTF-8")
    try { Json.parse(source.mkString).as[JsObject] } finally { source.close() }
  }
  private def _prefix(): (JsObject, CmlClassificationMetadata.Graph) = {
    val root = _resource("classification-metadata-v1-declared.json")
    root -> _right(CmlClassificationMetadata.read(root))
  }
  private def _right[A, B](value: Either[A, B]): B = {
    value match { case Right(result) => result; case Left(_) => fail("The authored fixture must be admitted before observation.") }
  }
}
