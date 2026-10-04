package cozy.modeler

import org.scalacheck.{Gen, Prop, Test}
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import play.api.libs.json.{JsArray, JsNull, JsNumber, JsObject, JsString, JsValue, Json}
import CmlSemanticFoundation._
import CmlDynamicMetadata.{CausalLinkProjection, ModelReference}
import CmlCausalMetadata._
import CmlCausalTraversal._

/*
 * @since   Oct.  5, 2026
 * @version Oct.  5, 2026
 */
final class CmlCausalTraversalSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  "Causal traversal" should {
    "ordered complete evidence" which {
      "CT-01 retain Graph and every catalog occurrence with the complete ten-kind literal oracle" in {
        Given("literal actor causal aggregate rule collaborator and full model references plus duplicate anonymous owners")
        val supplied = _full()
        val expected = _full_links
        val duplicates = Vector(false, true).map(_duplicates)
        val duplicateexpected = Vector(false, true).map(_duplicate_links)
        When("the literal semantic and peer publications are admitted and traversal is built")
        val graph = _publish(supplied)
        val traversal = CmlCausalTraversal.build(graph).right.get
        val others = duplicates.map(value => CmlCausalTraversal.build(_publish(value)).right.get)
        Then("all nine fields and family order match independent values and anonymous payload occurrences survive")
        (traversal.graph eq graph) shouldBe true
        (traversal.nodes eq graph.catalog.elements) shouldBe true
        traversal.nodes shouldBe supplied.records
        traversal.allLinks shouldBe expected
        others.map(_.allLinks) shouldBe duplicateexpected
        others.map(_.nodes) shouldBe duplicates.map(_.records)
      }

      "CT-02 address exact qualified pairs including noncausal nodes without identity fallback" in {
        Given("same local IDs case composition whitespace delimiter-collision pairs and an anonymous display-name twin")
        val pairs = Vector(ModelElementId("a:b", "c"), ModelElementId("a", "b:c"), ModelElementId("A", "same"),
          ModelElementId("a", "same"), ModelElementId("é", "same"), ModelElementId("e\u0301", "same"),
          ModelElementId("a", "Same"), ModelElementId("a b", "same"))
        val records = pairs.zipWithIndex.map { case (id, index) =>
          _record(id.modelId, id.elementId, Vector("query", "view", "component")(index % 3)).copy(name = "same")
        }
        val anonymous = _query.copy(identity = Absent(NotDeclared, "Anonymous query."), name = "same")
        val supplied = _missing_fixture(records :+ anonymous, Vector(_term))
        When("the Graph is admitted and nodes and both directions are queried with exact pairs")
        val traversal = CmlCausalTraversal.build(_publish(supplied)).right.get
        val nodes = pairs.map(traversal.node)
        val directions = pairs.map(id => (traversal.incoming(id), traversal.outgoing(id)))
        val unknown = traversal.node(ModelElementId("m", "same"))
        Then("each pair denotes its own complete record and noncausal adjacency is available empty")
        nodes shouldBe records.map(Right(_))
        directions shouldBe pairs.map(_ => (Right(Vector.empty), Right(Vector.empty)))
        unknown shouldBe Left(_unknown("node"))
        traversal.nodes shouldBe (records :+ anonymous)
      }

      "CT-03 index every Present Local side independently and exclude resident External endpoints" in {
        Given("literal full links plus direct causal links with exactly one absent endpoint")
        val supplied = _full()
        val expected = _full_links
        val absence = Absent(NotRepresented, "One endpoint unavailable.")
        val left = _cause.copy(source = absence, rule = absence, affectedElements = Present(Vector.empty))
        val right = _reaction.copy(target = absence, rule = absence, affectedElements = Present(Vector.empty))
        val sidedroot = _root(Vector(_operation.copy(references = Present(Vector.empty)), _event, _causeowner, _reactionowner),
          Vector.empty, _peers(Vector(right, left), Vector.empty, Vector.empty, Vector.empty, Vector.empty))
        val sidedexpected = Vector(_direct(_causeowner, Cause, absence, Present(_eventref)),
          _direct(_reactionowner, Reaction, Present(_eventref), absence))
        When("admitted graphs are traversed by Local endpoint pairs in each direction")
        val traversal = CmlCausalTraversal.build(_publish(supplied)).right.get
        val directions = supplied.records.collect { case record if record.identity.isInstanceOf[Present[_]] =>
          val id = record.identity.asInstanceOf[Present[ModelElementId]].value
          (id, traversal.outgoing(id), traversal.incoming(id))
        }
        val sided = _traverse(sidedroot)
        val incoming = sided.incoming(_eventref.identity)
        val outgoing = sided.outgoing(_eventref.identity)
        Then("order multiplicity and the other Local side remain exact while External residency creates no adjacency")
        directions.foreach { case (id, out, in) =>
          out shouldBe Right(expected.filter(_.source == Present(ModelReference(id, Local))))
          in shouldBe Right(expected.filter(_.target == Present(ModelReference(id, Local))))
        }
        incoming shouldBe Right(Vector(sidedexpected(0)))
        outgoing shouldBe Right(Vector(sidedexpected(1)))
        sided.allLinks shouldBe sidedexpected
      }

      "CT-08 preserve declared causal provenance exact context tokens and full filtered reference evidence" in {
        Given("a leading TermTarget followed by Local Query resident External dependency View and duplicate Query references")
        val supplied = _full()
        val expected = _full_links
        When("Causal publication is rendered canonically read and traversed")
        val graph = _publish(supplied)
        val consumed = CmlCausalMetadata.read(Json.parse(CmlCausalMetadata.canonicalJson(graph))).right.get
        val traversal = CmlCausalTraversal.build(consumed).right.get
        Then("complete own sources origin relation profile Japanese label and filtered indices match literal evidence")
        traversal.allLinks shouldBe expected
        traversal.allLinks.filter(_.kind == ModelReferenceLink) shouldBe expected.filter(_.kind == ModelReferenceLink)
        consumed.catalog.elements shouldBe supplied.records
        consumed.catalog.terms shouldBe Vector(_term)
      }
    }

    "direct causal strength and availability" which {
      "CT-04 follow a direct chain and cycle by separate queries without inferred closure or policy execution" in {
        Given("operation1 to event1 to operation2 to event2 and a final direct cycle with declared rule context")
        val op2 = _record("m", "operation2", "operation")
        val event2 = _record("m", "event2", "event")
        val owner2 = _record("m", "cause2", "causal-link")
        val cycleowner = _record("m", "cycle", "causal-link")
        val op2ref = ModelReference(ModelElementId("m", "operation2"), Local)
        val event2ref = ModelReference(ModelElementId("m", "event2"), Local)
        val first = _cause.copy(affectedElements = Present(Vector.empty))
        val second = _reaction.copy(target = Present(op2ref))
        val third = first.copy(element = owner2, source = Present(op2ref), target = Present(event2ref))
        val cycle = second.copy(element = cycleowner, source = Present(event2ref), target = Present(_opref))
        val records = Vector(_operation.copy(references = Present(Vector.empty)), _event, op2, event2, _rule,
          _causeowner, _reactionowner, owner2, cycleowner)
        val root = _root(records, Vector.empty, _peers(Vector(cycle, third, second, first), Vector.empty, Vector.empty, Vector.empty, Vector.empty))
        val direct = Vector(_direct(_causeowner, Cause, Present(_opref), Present(_eventref)),
          _direct(_reactionowner, Reaction, Present(_eventref), Present(op2ref)),
          _direct(owner2, Cause, Present(op2ref), Present(event2ref)), _direct(cycleowner, Reaction, Present(event2ref), Present(_opref)))
        When("the consumer follows each admitted node with separate consequences and causes requests")
        val traversal = _traverse(root)
        val outgoing = Vector(_opref, _eventref, op2ref, event2ref).map(ref => traversal.consequences(ref.identity))
        val incoming = Vector(_eventref, op2ref, event2ref, _opref).map(ref => traversal.causes(ref.identity))
        val rulecauses = traversal.causes(_ruleref.identity)
        val ruleconsequences = traversal.consequences(_ruleref.identity)
        Then("only each literal direct step is returned and rule context adds no causal assertion")
        outgoing shouldBe direct.map(link => Right(Present(Vector(link))))
        incoming shouldBe direct.map(link => Right(Present(Vector(link))))
        rulecauses shouldBe Right(Present(Vector.empty))
        ruleconsequences shouldBe Right(Present(Vector.empty))
      }

      "CT-05 return each complete matching carrier occurrence once despite duplicate affected references" in {
        Given("equal anonymous carrier duplicates with duplicate Local subjects and a resident External subject")
        val owner = _causeowner.copy(identity = Absent(NotDeclared, "Anonymous carrier."))
        val subject = ModelReference(ModelElementId("m", "aggregate"), Local)
        val carrier = _cause.copy(element = owner, affectedElements = Present(Vector(subject, subject, subject.copy(boundary = External))))
        val external = carrier.copy(element = _reactionowner, affectedElements = Present(Vector(subject.copy(boundary = External))))
        val records = Vector(_operation.copy(references = Present(Vector.empty)), _event, _rule, _aggregate, owner, _reactionowner, owner)
        val membership = AggregateMembership(_aggregate, Present(CmlStructureMetadata.AggregateBoundary(
          Present(CmlStructureMetadata.ModelReference(subject.identity, Local)), Present("root"))))
        val root = _root(records, Vector.empty, _peers(Vector(external, carrier, carrier), Vector.empty, Vector(membership), Vector.empty, Vector.empty))
        When("the explicit subject join and direct aggregate adjacency are queried")
        val traversal = _traverse(root)
        val affecting = traversal.affecting(subject.identity)
        val consequences = traversal.consequences(subject.identity)
        val causes = traversal.causes(subject.identity)
        Then("full equal carriers survive twice and neither membership nor External affected entries assert causation")
        affecting shouldBe Right(Present(Vector(carrier, carrier)))
        consequences shouldBe Right(Present(Vector.empty))
        causes shouldBe Right(Present(Vector.empty))
      }

      "CT-06 distinguish missing Dynamic from supplied empty after validating addressability" in {
        Given("one unconnected Query with either missing peers or complete empty peer objects")
        val missingroot = _root(Vector(_query), Vector.empty, Json.obj())
        val emptyroot = _root(Vector(_query), Vector.empty, _empty_peers)
        val id = ModelElementId("m", "query")
        val absence = Absent(NotDeclared, "No admitted Dynamic metadata is supplied.")
        When("all causal queries and unknown queries are interpreted on each admitted Graph")
        val traversals = Vector(missingroot, emptyroot).map(_traverse)
        val values = traversals.map(t => (t.causes(id), t.consequences(id), t.affecting(id), t.incoming(id), t.outgoing(id)))
        val unknown = traversals.map(t => _queries(t, ModelElementId("m", "unknown")))
        Then("global absence differs from available empty and unknown identity precedes availability")
        values(0) shouldBe (Right(absence), Right(absence), Right(absence), Right(Vector.empty), Right(Vector.empty))
        values(1) shouldBe (Right(Present(Vector.empty)), Right(Present(Vector.empty)), Right(Present(Vector.empty)), Right(Vector.empty), Right(Vector.empty))
        unknown.foreach(_.foreach { case (api, result) => result shouldBe Left(_unknown(api)) })
        traversals.map(_.allLinks) shouldBe Vector(Vector.empty, Vector.empty)
      }

      "CT-07 preserve four exact absences anonymous sources nested and outer aggregate targets and retained gaps" in {
        Given("four literal absence fixtures and separate anonymous Actor and outer absent Structure declarations")
        val supplied = _absences.map(_absence_fixture)
        val expected = supplied.zip(_absences).map { case (fixture, absence) => Vector(
          _direct(fixture.records(0), Cause, absence, absence),
          _derived(fixture.records(2), AggregateMember, absence, absence, "aggregate-member", "aggregateBoundaries.aggregate", 0)) }
        val absence = Absent(NotApplicable, "No owner identity or boundary.")
        val actor = _actor.copy(identity = absence)
        val entity = _entity.copy(identity = absence)
        val extraexpected = Vector(_derived(actor, ActorOperation, absence, Present(_opref), "actor-operation", "actorOperations.operations", 0),
          _derived(entity, AggregateMember, absence, absence, "aggregate-member", "aggregateBoundaries.aggregate", 0))
        val root = _root(Vector(actor, entity, _operation.copy(references = Present(Vector.empty))), Vector.empty,
          _peers(Vector.empty, Vector(ActorOperations(actor, Present(Vector(_opref)))), Vector(AggregateMembership(entity, absence)), Vector.empty, Vector.empty))
        When("each graph is admitted traversed and every emitted target is resolved")
        val graphs = supplied.map(_publish)
        val traversals = graphs.map(graph => CmlCausalTraversal.build(graph).right.get)
        val resolutions = traversals.map(t => t.allLinks.map(t.resolve))
        val extra = _traverse(root)
        val extraresolutions = extra.allLinks.map(extra.resolve)
        val extraadjacency = extra.incoming(_opref.identity)
        Then("every reason detail and gap survives without fabricated references or anonymous identities")
        traversals.map(_.allLinks) shouldBe expected
        graphs.map(_.capabilityGaps) shouldBe supplied.map(_.gaps)
        resolutions shouldBe expected.zip(_absences).map { case (links, absent) => links.map(link => Right(AbsentTarget(link, absent))) }
        extra.allLinks shouldBe extraexpected
        extraresolutions shouldBe Vector(Right(LocalTarget(extraexpected(0), _operation.copy(references = Present(Vector.empty)))),
          Right(AbsentTarget(extraexpected(1), absence)))
        extraadjacency shouldBe Right(Vector(extraexpected(0)))
        extra.graph.capabilityGaps should contain (CapabilityGap(Some(entity), "aggregateBoundaries.aggregateBoundary", absence))
      }
    }

    "qualified identity and full-value membership" which {
      "CT-09 resolve Local External and equivalent immutable copies using complete membership" in {
        Given("literal full links and independently altered owner source name provenance evidence and semantic values")
        val supplied = _full()
        val expected = _full_links
        val link = expected.head
        val copies = expected.map(value => value.copy(owner = value.owner.copy()))
        val foreign = Vector(link.copy(owner = link.owner.copy(name = "foreign")),
          link.copy(owner = link.owner.copy(source = link.owner.source.copy(path = "model/foreign.cml"))),
          link.copy(sourceAttribution = link.sourceAttribution.copy(line = Some(99))),
          link.copy(origin = Declared), link.copy(evidenceIndex = 77), link.copy(evidenceField = "foreign"),
          link.copy(semanticReference = Some(_queryref)))
        val resolutions = expected.map { value => value.target match {
          case Present(ModelReference(id, Local)) => Right(LocalTarget(value, supplied.records.find(_.identity == Present(id)).get))
          case Present(ModelReference(_, External)) => Right(ExternalTarget(value))
          case absence: Absent => Right(AbsentTarget(value, absence))
        } }
        When("every independent link copy and foreign value is resolved against the admitted Graph")
        val traversal = CmlCausalTraversal.build(_publish(supplied)).right.get
        val original = expected.map(traversal.resolve)
        val copied = copies.map(traversal.resolve)
        val rejected = foreign.map(traversal.resolve)
        Then("Local resolves the complete exact record External stays external and only full members are admitted")
        original shouldBe resolutions
        copied shouldBe resolutions
        rejected shouldBe foreign.map(_ => Left(_unknown_link))
      }

      "CT-10 reject invalid exact pairs safely across all six ID APIs without normalization" in {
        Given("null components empty values Unicode edge whitespace controls and well-formed unknown pairs")
        val invalid = Vector[ModelElementId](null, ModelElementId(null, "query"), ModelElementId("m", null),
          ModelElementId("", "query"), ModelElementId("m", ""), ModelElementId(" m", "query"), ModelElementId("m", "query "),
          ModelElementId("\u00a0m", "query"), ModelElementId("m", "query\u00a0"), ModelElementId("\u2003m", "query"),
          ModelElementId("m", "query\u2003"), ModelElementId("m\nsecret", "query"), ModelElementId("m", "qu\u007fery"))
        val unknown = Vector(ModelElementId("M", "query"), ModelElementId("m", "Query"), ModelElementId("m", "display"),
          ModelElementId("m", "model/source.cml"), ModelElementId("v", "term"))
        val root = _root(Vector(_query), Vector(_term), _empty_peers)
        When("the admitted traversal interprets all six requests for every literal pair")
        val traversal = _traverse(root)
        val invalidresults = invalid.map(id => _queries(traversal, id))
        val unknownresults = unknown.map(id => _queries(traversal, id))
        val knownresults = _queries(traversal, ModelElementId("m", "query"))
        Then("kind path and detail are fixed without reflecting malicious IDs and known empty-node queries succeed")
        invalidresults.foreach(_.foreach { case (api, result) => result shouldBe Left(CausalDiagnostic(InvalidShape,
          api + ".identity", "The Causal query identity is invalid.")) })
        unknownresults.foreach(_.foreach { case (api, result) => result shouldBe Left(_unknown(api)) })
        knownresults.map(_._2) shouldBe Vector(Right(_query), Right(Vector.empty), Right(Vector.empty),
          Right(Present(Vector.empty)), Right(Present(Vector.empty)), Right(Present(Vector.empty)))
      }

      "CT-11 reject all nonnull malformed or foreign fields before nested dereference" in {
        Given("independent mutations of all nine link fields including null malformed nested references and hostile evidence")
        val link = _full_links.head
        val malformed = Vector(link.copy(owner = null), link.copy(kind = null), link.copy(source = null), link.copy(target = null),
          link.copy(sourceAttribution = null), link.copy(origin = null), link.copy(evidenceField = null), link.copy(evidenceIndex = -1),
          link.copy(semanticReference = null), link.copy(semanticReference = Some(null)), link.copy(source = Present(null)),
          link.copy(target = Present(ModelReference(null, null))), link.copy(owner = link.owner.copy(identity = Present(null))),
          link.copy(origin = Derived(null, null)), link.copy(origin = Derived("hostile\nsecret", Vector(null))),
          link.copy(sourceAttribution = SourceAttribution(null, "../secret", null, null)), link.copy(evidenceField = "hostile\nsecret"),
          link.copy(kind = Reaction), link.copy(target = Present(_eventref)))
        When("null Graph null link and every nonnull mutation are presented to the public boundary")
        val nullgraph = CmlCausalTraversal.build(null)
        val traversal = CmlCausalTraversal.build(_publish(_full())).right.get
        val nulllink = traversal.resolve(null)
        val results = malformed.map(traversal.resolve)
        Then("null has its exact shape diagnostic and all other foreign values use the safe membership diagnostic")
        nullgraph shouldBe Left(Vector(CausalDiagnostic(InvalidShape, "graph", "An admitted Causal graph is required.")))
        nulllink shouldBe Left(CausalDiagnostic(InvalidShape, "resolve.link", "The Causal traversal link is invalid."))
        results shouldBe malformed.map(_ => Left(_unknown_link))
      }
    }

    "independent permutation properties" which {
      "CT-12 preserve complete qualified occurrence adjacency carrier and resolution oracles through canonical read" in {
        Given("total literal generators for exact model pairs anonymous payload order independent array order and recursive keys")
        val choices = for { n <- Gen.choose(1, 100000); reverse <- Gen.oneOf(true, false);
          catalog <- Gen.oneOf(true, false); peer <- Gen.oneOf(true, false); keys <- Gen.oneOf(true, false) } yield (n, reverse, catalog, peer, keys)
        When("ScalaCheck admits publishes canonically reads and queries each independently declared complete case")
        val property = Prop.forAll(choices) { case (n, reverse, catalog, peer, keys) =>
          val (root, records, expected, carriers, firstid, secondid) = _property_case(n, reverse, catalog, peer)
          val input = if (keys) _permute_keys(root) else root
          val graph = CmlCausalMetadata.build(CmlSemanticMetadata.read(input).right.get).right.get
          val canonical = Json.parse(CmlCausalMetadata.canonicalJson(graph))
          val consumed = CmlCausalMetadata.read(if (keys) _permute_keys(canonical) else canonical).right.get
          val traversal = CmlCausalTraversal.build(consumed).right.get
          val directions = Vector(firstid, secondid).forall { id =>
            traversal.node(id) == Right(records.find(_.identity == Present(id)).get) &&
              traversal.outgoing(id) == Right(expected.filter(_.source == Present(ModelReference(id, Local)))) &&
              traversal.incoming(id) == Right(expected.filter(_.target == Present(ModelReference(id, Local)))) &&
              traversal.causes(id) == Right(Present(expected.filter(link => link.target == Present(ModelReference(id, Local)) && (link.kind == Cause || link.kind == Reaction)))) &&
              traversal.consequences(id) == Right(Present(expected.filter(link => link.source == Present(ModelReference(id, Local)) && (link.kind == Cause || link.kind == Reaction))))
          }
          val resolutions = expected.map { link => link.target match {
            case Present(ModelReference(id, Local)) => Right(LocalTarget(link, records.find(_.identity == Present(id)).get))
            case Present(ModelReference(_, External)) => Right(ExternalTarget(link))
            case absence: Absent => Right(AbsentTarget(link, absence))
          } }
          traversal.nodes == records && traversal.allLinks == expected && consumed.causalLinks == Present(carriers) && directions &&
            traversal.affecting(ModelElementId("m", "aggregate")) == Right(Present(carriers)) &&
            expected.map(link => traversal.resolve(link.copy(owner = link.owner.copy()))) == resolutions &&
            traversal.incoming(ModelElementId("m", "component")) == Right(Vector.empty)
        }
        val result = Test.check(Test.Parameters.default.withMinSuccessfulTests(100), property)
        info(s"CT-12 ScalaCheck succeeded=${result.succeeded} discarded=${result.discarded} passed=${result.passed}")
        Then("the actual property result passes at least one hundred cases with zero discards")
        result.passed shouldBe true
        result.succeeded should be >= 100
        result.discarded shouldBe 0
      }
    }
  }

  private def _queries(traversal: Traversal, id: ModelElementId): Vector[(String, Either[CausalDiagnostic, Any])] = Vector(
    "node" -> traversal.node(id), "outgoing" -> traversal.outgoing(id), "incoming" -> traversal.incoming(id),
    "causes" -> traversal.causes(id), "consequences" -> traversal.consequences(id), "affecting" -> traversal.affecting(id))

  private def _unknown(api: String): CausalDiagnostic = CausalDiagnostic(UnknownElement, api + ".identity",
    "The Causal element identity is unknown or unaddressable.")

  private def _unknown_link: CausalDiagnostic = CausalDiagnostic(UnknownLink, "resolve.link",
    "The Causal traversal link is not admitted by this graph.")

  private def _traverse(root: JsValue): Traversal = CmlCausalTraversal.build(
    CmlCausalMetadata.build(CmlSemanticMetadata.read(root).right.get).right.get).right.get

  // These constructors author expected complete values from literal declarations;
  // they never consume traversal or publication output.
  private def _direct(owner: ElementRecord, kind: LinkKind, source: Presence[ModelReference], target: Presence[ModelReference]): TraversalLink =
    TraversalLink(owner, kind, source, target, owner.source, Declared, "causalLinks", 0, None)

  private def _derived(owner: ElementRecord, kind: LinkKind, source: Presence[ModelReference], target: Presence[ModelReference],
    token: String, field: String, index: Int): TraversalLink = TraversalLink(owner, kind, source, target, owner.source,
      Derived("cozy.cml.causal." + token + ".v1", Vector(owner.source)), field, index, None)

  private def _model_link(owner: ElementRecord, reference: SemanticReference, identity: ModelElementId, index: Int): TraversalLink =
    TraversalLink(owner, ModelReferenceLink, owner.identity match {
      case Present(id) => Present(ModelReference(id, Local))
      case absence: Absent => absence
    }, Present(ModelReference(identity, reference.boundary)), reference.source, reference.origin,
      "modelReferences.references", index, Some(reference))

  private def _full_links: Vector[TraversalLink] = Vector(
    _derived(_actor, ActorOperation, Present(ModelReference(ModelElementId("m", "actor"), Local)), Present(_opref), "actor-operation", "actorOperations.operations", 0),
    _derived(_actor, ActorOperation, Present(ModelReference(ModelElementId("m", "actor"), Local)), Present(_opref), "actor-operation", "actorOperations.operations", 1),
    _model_link(_operation, _queryref, ModelElementId("m", "query"), 0),
    _model_link(_operation, _externalref, ModelElementId("m", "component"), 1),
    _model_link(_operation, _viewref, ModelElementId("m", "view"), 2),
    _model_link(_operation, _queryref, ModelElementId("m", "query"), 3),
    _derived(_aggregate, AggregateMember, Present(ModelReference(ModelElementId("m", "aggregate"), Local)), Present(ModelReference(ModelElementId("m", "aggregate"), Local)), "aggregate-member", "aggregateBoundaries.aggregate", 0),
    _derived(_entity, AggregateMember, Present(ModelReference(ModelElementId("m", "entity"), Local)), Present(ModelReference(ModelElementId("m", "aggregate"), Local)), "aggregate-member", "aggregateBoundaries.aggregate", 0),
    _derived(_workflow, WorkflowRule, Present(ModelReference(ModelElementId("m", "workflow"), Local)), Present(_ruleref), "workflow-rule", "ruleContexts.rules", 0),
    _derived(_activity, WorkflowRule, Present(ModelReference(ModelElementId("m", "activity"), Local)), Present(_ruleref), "workflow-rule", "ruleContexts.rules", 0),
    _derived(_activity, WorkflowRule, Present(ModelReference(ModelElementId("m", "activity"), Local)), Present(_ruleref), "workflow-rule", "ruleContexts.rules", 1),
    _derived(_usecase, Collaborator, Present(ModelReference(ModelElementId("m", "usecase"), Local)), Present(ModelReference(ModelElementId("m", "query"), Local)), "collaborator", "collaboratorContexts.collaborators", 0),
    _derived(_usecase, Collaborator, Present(ModelReference(ModelElementId("m", "usecase"), Local)), Present(ModelReference(ModelElementId("m", "component"), External)), "collaborator", "collaboratorContexts.collaborators", 1),
    _direct(_causeowner, Cause, Present(_opref), Present(_eventref)),
    _derived(_causeowner, AffectedSubject, Present(_opref), Present(ModelReference(ModelElementId("m", "aggregate"), Local)), "affected-subject", "causalLinks.affectedElements", 0),
    _derived(_causeowner, AffectedSubject, Present(_opref), Present(ModelReference(ModelElementId("m", "entity"), Local)), "affected-subject", "causalLinks.affectedElements", 1),
    _derived(_causeowner, RuleInput, Present(_opref), Present(_ruleref), "rule-input", "causalLinks.rule", 0),
    _derived(_causeowner, RuleOutput, Present(_ruleref), Present(_eventref), "rule-output", "causalLinks.rule", 1),
    _direct(_reactionowner, Reaction, Present(_eventref), Present(_opref)),
    _derived(_reactionowner, RuleInput, Present(_eventref), Present(_ruleref), "rule-input", "causalLinks.rule", 0),
    _derived(_reactionowner, RuleOutput, Present(_ruleref), Present(_opref), "rule-output", "causalLinks.rule", 1))

  private def _duplicate_links(reverse: Boolean): Vector[TraversalLink] = {
    val owner = _causeowner.copy(identity = Absent(NotDeclared, "Anonymous owner."), name = "same", references = Present(Vector.empty))
    val different = owner.copy(source = _source("m").copy(path = "model/other.cml"))
    val first = Vector(_direct(owner, Cause, Present(_opref), Present(_eventref)))
    val second = Vector(_direct(owner, Reaction, Present(_eventref), Present(_opref)),
      _derived(owner, RuleInput, Present(_eventref), Present(_ruleref), "rule-input", "causalLinks.rule", 0),
      _derived(owner, RuleOutput, Present(_ruleref), Present(_opref), "rule-output", "causalLinks.rule", 1))
    val third = Vector(_direct(different, Cause, Present(_opref), Present(_eventref)),
      _derived(different, RuleInput, Present(_opref), Present(_ruleref), "rule-input", "causalLinks.rule", 0),
      _derived(different, RuleOutput, Present(_ruleref), Present(_eventref), "rule-output", "causalLinks.rule", 1))
    if (reverse) second ++ third ++ first else first ++ third ++ second
  }

  private def _property_case(n: Int, reverse: Boolean, catalog: Boolean, peer: Boolean):
    (JsObject, Vector[ElementRecord], Vector[TraversalLink], Vector[CausalLinkProjection], ModelElementId, ModelElementId) = {
    val firstid = ModelElementId("m:" + n, "same")
    val secondid = ModelElementId("m", "same")
    val firstref = ModelReference(firstid, Local)
    val secondref = ModelReference(secondid, Local)
    val semantic = _externalref.copy(source = _source(firstid.modelId))
    val firstrecord = _record(firstid.modelId, firstid.elementId, "operation").copy(references = Present(Vector(semantic)))
    val secondrecord = _record(secondid.modelId, secondid.elementId, "operation")
    val owner = _causeowner.copy(identity = Absent(NotDeclared, "Anonymous property owner."), name = "same")
    val different = owner.copy(source = owner.source.copy(path = "model/other.cml"))
    val subject = ModelReference(ModelElementId("m", "aggregate"), Local)
    val affected = Present(Vector(subject, subject, subject.copy(boundary = External)))
    val first = CausalLinkProjection(owner, CmlDynamicMetadata.Cause, Present(firstref), Present(_eventref), Present(_ruleref), affected)
    val second = CausalLinkProjection(owner, CmlDynamicMetadata.Reaction, Present(_eventref), Present(secondref), Present(_ruleref), affected)
    val third = CausalLinkProjection(different, CmlDynamicMetadata.Cause, Present(secondref), Present(_eventref), Present(_ruleref), affected)
    val anonymous = if (reverse) Vector(second, first) else Vector(first, second)
    val carriers = Vector(anonymous.head, third, anonymous.last)
    val records = (if (catalog) Vector(secondrecord, firstrecord) else Vector(firstrecord, secondrecord)) ++
      Vector(_event, _rule, _aggregate, _component, owner, different, owner)
    val peers = _peers(if (peer) Vector(third) ++ anonymous else anonymous :+ third, Vector.empty,
      Vector(AggregateMembership(_aggregate, Absent(NotDeclared, "No boundary."))), Vector.empty, Vector.empty)
    def _expected_(record: ElementRecord, kind: LinkKind, source: ModelReference, target: ModelReference): Vector[TraversalLink] = Vector(
      _direct(record, kind, Present(source), Present(target)),
      _derived(record, AffectedSubject, Present(source), Present(subject), "affected-subject", "causalLinks.affectedElements", 0),
      _derived(record, AffectedSubject, Present(source), Present(subject), "affected-subject", "causalLinks.affectedElements", 1),
      _derived(record, AffectedSubject, Present(source), Present(subject.copy(boundary = External)), "affected-subject", "causalLinks.affectedElements", 2),
      _derived(record, RuleInput, Present(source), Present(_ruleref), "rule-input", "causalLinks.rule", 0),
      _derived(record, RuleOutput, Present(_ruleref), Present(target), "rule-output", "causalLinks.rule", 1))
    val firstexpected = _expected_(owner, Cause, firstref, _eventref)
    val secondexpected = _expected_(owner, Reaction, _eventref, secondref)
    val expected = Vector(_model_link(firstrecord, semantic, ModelElementId("m", "component"), 0),
      _derived(_aggregate, AggregateMember, Present(subject), Absent(NotDeclared, "No boundary."), "aggregate-member", "aggregateBoundaries.aggregate", 0)) ++
      (if (reverse) secondexpected else firstexpected) ++ _expected_(different, Cause, secondref, _eventref) ++
      (if (reverse) firstexpected else secondexpected)
    (_root(records, Vector.empty, peers), records, expected, carriers, firstid, secondid)
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
    Present(Vector(_termref, _queryref, _externalref, _viewref, _queryref)))
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

  private def _publish(supplied: Fixture): Graph =
    CmlCausalMetadata.build(CmlSemanticMetadata.read(supplied.root).right.get).right.get

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

  private def _source(authority: String): SourceAttribution = SourceAttribution(authority, "model/source.cml", "a" * 64, Some(7))
  private def _record(authority: String, id: String, kind: String): ElementRecord =
    ElementRecord(Present(ModelElementId(authority, id)), kind, "display", _source(authority), Present(Vector.empty))
  private def _semantic(target: SemanticTarget, boundary: ReferenceBoundary, origin: ReferenceOrigin): SemanticReference =
    SemanticReference(target, boundary, Present(RelationId("v", "dependency:literal")), Present(ProfileId("v", "profile:literal")),
      Present("Declared query/view/dependency context."), Present(LocalizedLabel("表示", Some("ja"))), _source("m"), origin)

  private def _element_json(value: ElementRecord): JsObject = Json.obj("identity" -> _presence(value.identity, _identity_json),
    "kind" -> value.kind, "name" -> value.name, "source" -> _source_json(value.source),
    "references" -> _presence(value.references, (references: Vector[SemanticReference]) => JsArray(references.map(_semantic_json))))
  private def _source_json(value: SourceAttribution): JsObject = Json.obj("authorityId" -> value.authorityId, "path" -> value.path,
    "sha256" -> value.sha256, "line" -> value.line.fold[JsValue](JsNull)(line => JsNumber(BigDecimal(line))))
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
  private def _permute_keys(value: JsValue): JsValue = value match {
    case obj: JsObject => JsObject(obj.fields.reverse.map { case (key, child) => key -> _permute_keys(child) })
    case JsArray(values) => JsArray(values.map(_permute_keys))
    case scalar => scalar
  }
}
