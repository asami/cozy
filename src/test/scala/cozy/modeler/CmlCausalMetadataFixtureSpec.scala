package cozy.modeler

import scala.io.Source
import org.scalacheck.{Gen, Prop, Test}
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import play.api.libs.json.{JsArray, JsNull, JsNumber, JsObject, JsString, JsValue, Json}
import CmlSemanticFoundation._
import CmlDynamicMetadata.{CausalLinkProjection, DeclaredExpression, ModelReference, WorkflowProjection}
import CmlCausalMetadata._
import CmlCausalTraversal._

/*
 * @since   Oct.  5, 2026
 * @version Oct.  5, 2026
 */
final class CmlCausalMetadataFixtureSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  "Fixed Causal supplier handoff" should {
    "complete independent publication" which {
      "CF-01 retain both complete resources through publication read and canonical reconsumption" in {
        Given("two UTF-8 filenames and twenty independently authored full records and peer declarations per mode")
        val literals = Vector(false, true).map(_literal)
        When("the accepted predecessor and actual resources are read and the literal peers and Causal publication are admitted")
        val prefix = _resource("terminology-metadata-v1-declared.json")
        val observed = literals.zipWithIndex.map { case (literal, index) =>
          val expected = _oracle(_expanded(prefix, literal))
          val actual = _resource(_filename(index == 1))
          val checks = _exercise(actual, expected) ++ _typed_publication(prefix, literal, expected)
          (expected, checks)
        }
        Then("all seventy-five records seven Terms complete families gaps nine-field links and canonical wire equal independent values")
        observed.foreach { case (expected, checks) =>
          checks should contain only true
          expected.records.size shouldBe 75
          expected.terms.size shouldBe 7
          expected.causal.size shouldBe 8
          expected.records(73) shouldBe expected.records(74)
          expected.causal.takeRight(2).map(_.kind) shouldBe Vector(CmlDynamicMetadata.Cause, CmlDynamicMetadata.Reaction)
        }
      }
    }
    "JSON-only grounded questions" which {
      "CF-02 follow buyer command event rule operation and affected subjects without upgrading context" in {
        Given("qualified causal.example pairs and independently authored placement confirmation reservation and stock carriers")
        val literal = _literal(false)
        When("only the actual declared resource supplies traversal and each direct or contextual question is captured")
        val expected = _oracle(_expanded(_resource("terminology-metadata-v1-declared.json"), literal))
        val traversal = _traverse(_resource(_filename(false)))
        val answers = _questions(traversal, expected)
        val buyer = traversal.outgoing(_id("actor:buyer"))
        val aggregate = traversal.affecting(_id("aggregate:order"))
        val entity = traversal.affecting(_id("entity:order-item"))
        val aggregatecauses = traversal.consequences(_id("aggregate:order"))
        val rule = traversal.outgoing(_id("rule:reserve-stock"))
        val workflow = traversal.outgoing(_id("workflow:fulfillment"))
        Then("the exact declared chain full subject carriers and rule context retain their distinct strengths and duplicate actor operations")
        answers should contain only true
        buyer shouldBe Right(expected.links.filter(_.kind == ActorOperation).filter(_.owner == literal.records.head))
        aggregate shouldBe Right(Present(literal.causal.take(3)))
        entity shouldBe Right(Present(Vector(literal.causal(0), literal.causal(3))))
        aggregatecauses shouldBe Right(Present(Vector.empty))
        rule shouldBe Right(expected.links.filter(_.source == Present(_ref("rule:reserve-stock"))))
        workflow shouldBe Right(expected.links.filter(_.source == Present(_ref("workflow:fulfillment"))))
      }
      "CF-03 preserve full generic references duplicate provenance and resident External boundaries" in {
        Given("literal dependency read-model references and four ordered collaborator declarations")
        val literal = _literal(false)
        When("actual declared JSON supplies complete links resolutions and Local incoming queries")
        val expected = _oracle(_expanded(_resource("terminology-metadata-v1-declared.json"), literal))
        val traversal = _traverse(_resource(_filename(false)))
        val selected = expected.links.filter(link => Vector(literal.records(5), literal.records(10), literal.records(17)).contains(link.owner))
          .filter(link => link.kind == ModelReferenceLink || link.kind == Collaborator)
        val actual = traversal.allLinks.filter(link => Vector(literal.records(5), literal.records(10), literal.records(17)).contains(link.owner))
          .filter(link => link.kind == ModelReferenceLink || link.kind == Collaborator)
        val resolved = selected.map(traversal.resolve)
        val incoming = traversal.incoming(_id("component:inventory"))
        val querycauses = traversal.causes(_id("query:availability"))
        val viewcauses = traversal.causes(_id("view:order-summary"))
        Then("all nine fields and complete semantic references remain literal while External targets stay external even when resident")
        actual shouldBe selected
        resolved shouldBe selected.map(_resolution(_, expected.records))
        incoming shouldBe Right(expected.links.filter(_.target == Present(_ref("component:inventory"))))
        querycauses shouldBe Right(Present(Vector.empty))
        viewcauses shouldBe Right(Present(Vector.empty))
        selected.filter(_.kind == ModelReferenceLink).flatMap(_.semanticReference) shouldBe
          _values(literal.records(5).references) ++ _values(literal.records(10).references)
        selected.count(_.target == Present(_ref("component:inventory", External))) shouldBe 1
      }
      "CF-04 retain cyclic gaps anonymous occurrences and declared present-empty prefix facts" in {
        Given("the exact absence cycles missing buyer identity and two equal anonymous cores with different causal payloads")
        val literal = _literal(true)
        When("only actual absence JSON supplies Graph traversal peer declarations and addressability answers")
        val expected = _oracle(_expanded(_resource("terminology-metadata-v1-declared.json"), literal))
        val raw = _resource(_filename(true))
        val graph = _right(CmlCausalMetadata.read(raw))
        val traversal = _right(CmlCausalTraversal.build(graph))
        val checks = _exercise(raw, expected) ++ _peers(raw, expected)
        val anonymous = expected.links.filter(link => link.owner == literal.records(18))
        val resolutions = anonymous.map(traversal.resolve)
        val buyer = traversal.node(_id("actor:buyer"))
        val command = traversal.consequences(_id("command:place-order"))
        Then("every full gap and anonymous payload survives without name source index or identity recovery")
        checks should contain only true
        graph.capabilityGaps shouldBe expected.gaps
        graph.causalLinks shouldBe Present(expected.causal)
        resolutions shouldBe anonymous.map(link => Right(AbsentTarget(link, link.target.asInstanceOf[Absent])))
        buyer shouldBe Left(_unknown("node"))
        command shouldBe Right(Present(Vector.empty))
        literal.records.take(18).map(_.references) shouldBe Vector.tabulate(18)(index => _gaps(index % 4))
        literal.records.drop(18).map(_.references) shouldBe Vector.fill(2)(Present(Vector.empty))
        literal.causal.zipWithIndex.foreach { case (carrier, index) =>
          Vector(carrier.source, carrier.target, carrier.rule, carrier.affectedElements) shouldBe
            Vector.tabulate(4)(field => _gaps((index + 3 + field) % 4))
        }
      }
    }
    "complete peer compatibility" which {
      "CF-05 retain the entire predecessor and all five accepted peer interpretations in both resources" in {
        Given("the unchanged fifty-five-record seven-Term prefix all peer arrays sources labels and opaque namespaces")
        val literals = Vector(false, true).map(_literal)
        When("each actual resource is independently consumed by all five peers and accepted predecessor navigation is queried")
        val prefix = _resource("terminology-metadata-v1-declared.json")
        val observed = literals.zipWithIndex.flatMap { case (literal, index) =>
          val expected = _oracle(_expanded(prefix, literal))
          val raw = _resource(_filename(index == 1))
          _peers(raw, expected) ++ _prefix_questions(raw, prefix) ++ Vector(
            (raw \ "elements").as[Vector[JsValue]].take(55) == (prefix \ "elements").as[Vector[JsValue]],
            (raw \ "terms").as[JsValue] == (prefix \ "terms").as[JsValue],
            _extensions(prefix).fields.forall { case (key, child) =>
              if (_families.contains(key)) child.as[JsObject].fields.forall { case (field, value) => value match {
                case JsArray(items) => (raw \ "extensions" \ key \ field).as[Vector[JsValue]].take(items.size) == items
                case _ => (raw \ "extensions" \ key \ field).as[JsValue] == value
              } } else (raw \ "extensions" \ key).as[JsValue] == child
            })
        }
        Then("complete typed peers catalogs Terms and each prefix carrier remain unchanged with only prescribed appended declarations")
        observed should contain only true
      }
    }
  }

  "Fixed Causal handoff safety" should {
    "strict evidence ownership" which {
      "CF-06 reject single wire mutations with exact independent safe diagnostics" in {
        Given("literal missing version family provenance endpoint duplicate gap and hostile peer mutation coordinates")
        val mutations = _mutations
        When("each mutation reaches strict admission of parsed actual declared or absence JSON")
        val resources = Vector(_resource(_filename(false)), _resource(_filename(true)))
        val observed = mutations.map { mutation =>
          CmlCausalMetadata.read(_change(resources(if (mutation.absent) 1 else 0), mutation.path, mutation.update))
        }
        val invalidbase = CmlCausalMetadata.read(resources.head - "schemaVersion")
        val nullvalue = CmlCausalMetadata.read(null)
        Then("fixed kind path and detail match independently authored diagnostics and never reflect hostile payloads")
        observed shouldBe mutations.map(mutation => Left(Vector(mutation.diagnostic)))
        invalidbase shouldBe Left(Vector(_diagnostic(PublicationFailure, "publication", "The semantic metadata publication is invalid.")))
        nullvalue shouldBe invalidbase
      }
    }
    "finite membership and inference limits" which {
      "CF-07 accept full copies reject foreign links and follow an explicit cycle one direct step at a time" in {
        Given("literal confirmation changed to placed-to-command and fixed safe unknown-link and identity diagnostics")
        val literal = _literal(false)
        val cycle = literal.copy(causal = literal.causal.updated(1, literal.causal(1).copy(
          source = Present(_ref("event:order-placed")), target = Present(_ref("command:place-order")))))
        When("actual declared JSON supplies membership while the independently altered cycle is admitted published read and queried finitely")
        val prefix = _resource("terminology-metadata-v1-declared.json")
        val expected = _oracle(_expanded(prefix, literal))
        val traversal = _traverse(_resource(_filename(false)))
        val link = expected.links.find(_.kind == ActorOperation).get
        val copy = link.copy(owner = link.owner.copy())
        val foreign = copy.copy(evidenceField = "hostile foreign context")
        val membership = Vector(traversal.resolve(copy), traversal.resolve(foreign), traversal.resolve(null))
        val cycleexpected = _oracle(_expanded(prefix, cycle))
        val cyclegraph = _right(CmlCausalMetadata.build(_right(CmlSemanticMetadata.read(_without_child(cycleexpected.wire)))))
        val cycleraw = Json.parse(CmlCausalMetadata.canonicalJson(cyclegraph)).as[JsObject]
        val cycletraversal = _traverse(cycleraw)
        val bounded = Vector("command:place-order", "event:order-placed", "command:place-order", "event:order-placed")
          .map(name => cycletraversal.consequences(_id(name)))
        val checks = _exercise(cycleraw, cycleexpected) ++ _questions(cycletraversal, cycleexpected)
        val unknown = Vector("共有", "fixtures/causal-model.cml", "19", "unrepresented").map(name => traversal.node(_id(name)))
        Then("full value copies resolve foreign values fail and the cycle never invents transitive self causal membership or read-model edges")
        membership shouldBe Vector(_resolution(copy, expected.records), Left(_diagnostic(UnknownLink, "resolve.link",
          "The Causal traversal link is not admitted by this graph.")), Left(_diagnostic(InvalidShape, "resolve.link", "The Causal traversal link is invalid.")))
        bounded shouldBe Vector("command:place-order", "event:order-placed", "command:place-order", "event:order-placed")
          .map(name => Right(Present(_direct_links(cycleexpected.links.filter(_.source == Present(_ref(name)))))))
        checks should contain only true
        unknown shouldBe Vector.fill(4)(Left(_unknown("node")))
      }
    }
  }

  "Active fixed-resource properties" should {
    "independent two-mode permutations" which {
      "CF-08 preserve complete values and grounded questions in both actual modes for every sample" in {
        Given("seed 42 independent catalog Term peer reference and recursive-key permutation plans without discards")
        val literals = Vector(false, true).map(_literal)
        val tags = Vector("declared", "absence", "elements-reordered", "terms-reordered", "projections-reordered", "references-reordered", "records-reordered")
        val generator = Gen.listOfN(2, Gen.listOfN(12, Gen.choose(0, Int.MaxValue))).map(_.map(_.toVector).toVector)
        When("both actual UTF-8 resources and the accepted predecessor are loaded and ScalaCheck captures independent complete reconstruction")
        val prefix = _resource("terminology-metadata-v1-declared.json")
        val resources = Vector(_resource(_filename(false)), _resource(_filename(true)))
        val originals = literals.map(_expanded(prefix, _))
        val counts = scala.collection.mutable.Map(tags.map(_ -> 0): _*)
        val property = Prop.forAll(generator) { plans =>
          Given("two independent raw declaration plans retaining full references and embedded record correspondence")
          val expectedraw = originals.zip(plans).map { case (raw, plan) => _permuted(raw, plan) }
          val actualraw = resources.zip(plans).map { case (raw, plan) => _permuted(_without_child(raw), plan) }
          When("each independently reconstructed expected child is supplied to actual build read canonical and traversal questions")
          val expected = expectedraw.map(_oracle)
          val observed = actualraw.zip(expected).flatMap { case (raw, oracle) =>
            val supplied = raw + ("extensions" -> (_extensions(raw) + (namespace -> (oracle.wire \ "extensions" \ namespace).as[JsValue])))
            _exercise(supplied, oracle) ++ _peers(supplied, oracle)
          }
          val changed = Vector("declared", "absence") ++ Vector(
            "elements-reordered" -> originals.zip(expectedraw).exists { case (before, after) => (before \ "elements").as[JsValue] != (after \ "elements").as[JsValue] },
            "terms-reordered" -> originals.zip(expectedraw).exists { case (before, after) => (before \ "terms").as[JsValue] != (after \ "terms").as[JsValue] },
            "projections-reordered" -> originals.zip(expectedraw).exists { case (before, after) => _families.exists(key =>
              _extensions(before).value(key).as[JsObject].fields.exists { case (field, value) => value.isInstanceOf[JsArray] && value != (_extensions(after).value(key) \ field).as[JsValue] }) },
            "references-reordered" -> originals.zip(expectedraw).exists { case (before, after) =>
              val old = (before \ "elements").as[Vector[JsObject]].map(value => (value - "references") -> (value \ "references").as[JsValue]).toMap
              (after \ "elements").as[Vector[JsObject]].exists(value => old(value - "references") != (value \ "references").as[JsValue]) },
            "records-reordered" -> originals.zip(expectedraw).exists { case (before, after) => Json.stringify(before) != Json.stringify(after) &&
              before.fields.map(_._1) != after.fields.map(_._1) }).collect { case (tag, true) => tag }
          changed.foreach(tag => counts.update(tag, counts(tag) + 1))
          Then("full independent core peers gaps canonical wire nine-field links and qualified questions match for both modes")
          observed should contain only true
          Prop.collect(changed.sorted.mkString(","))(Prop(observed.forall(identity)))
        }
        val result = Test.check(Test.Parameters.default.withMinSuccessfulTests(100)
          .withWorkers(1).withInitialSeed(org.scalacheck.rng.Seed(42L)), property)
        val coverage = counts.toVector.sortBy(_._1)
        Then("the actual property receipt reports at least one hundred successes zero discards pass and seven positive observed categories")
        info(s"CF-08 succeeded=${result.succeeded} discarded=${result.discarded} passed=${result.passed}")
        info("CF-08 coverage=" + coverage.map { case (tag, count) => s"$tag:$count" }.mkString(","))
        result.passed shouldBe true
        result.succeeded should be >= 100
        result.discarded shouldBe 0
        coverage.map(_._1) shouldBe tags.sorted
        coverage.foreach { case (_, count) => count should be > 0 }
        counts("declared") shouldBe result.succeeded
        counts("absence") shouldBe result.succeeded
      }
    }
  }

  private final case class Literal(records: Vector[ElementRecord], actor: CmlUseCaseMetadata.ActorProjection,
    usecase: CmlUseCaseMetadata.UseCaseProjection, workflow: WorkflowProjection, causal: Vector[CausalLinkProjection],
    structure: Vector[CmlStructureMetadata.ElementProjection], terminology: Vector[CmlTerminologyMetadata.ElementProjection])
  private final case class Oracle(wire: JsObject, records: Vector[ElementRecord], terms: Vector[TermRecord],
    structure: CmlStructureMetadata.Graph, classification: CmlClassificationMetadata.Graph, dynamic: CmlDynamicMetadata.Graph,
    usecase: CmlUseCaseMetadata.Graph, terminology: CmlTerminologyMetadata.Graph, causal: Vector[CausalLinkProjection],
    actors: Vector[ActorOperations], aggregates: Vector[AggregateMembership], rules: Vector[RuleContext],
    collaborators: Vector[CollaboratorContext], references: Vector[ModelReferences], gaps: Vector[CapabilityGap], links: Vector[TraversalLink])
  private final case class Mutation(path: Vector[String], update: JsValue => JsValue, diagnostic: CausalDiagnostic, absent: Boolean = false)
  private val _families = Vector("cozy.cml.structure", "cozy.cml.classification", "cozy.cml.dynamic", "cozy.cml.use-case", "cozy.cml.terminology")
  private val _gaps = Vector(Absent(NotDeclared, "No causal fact is declared."), Absent(NotRepresented, "The causal fact is not represented."),
    Absent(Unsupported, "The causal fact is unsupported."), Absent(NotApplicable, "The causal fact is not applicable."))
  private val _inference_gap = Absent(Unsupported, "No causation is inferred from names, membership, generic relations or expression text.")
  private val _ids = Vector("actor:buyer", "command:place-order", "operation:confirm-order", "aggregate:order", "entity:order-item",
    "event:order-placed", "rule:reserve-stock", "operation:reserve-stock", "event:stock-reserved", "workflow:fulfillment",
    "component:inventory", "query:availability", "view:order-summary", "cause:placement", "cause:confirmation", "reaction:reservation",
    "cause:stock-event", "use-case:place-order")
  private def _id(name: String): ModelElementId = ModelElementId("causal.example", name)
  private def _ref(name: String, boundary: ReferenceBoundary = Local): ModelReference = ModelReference(_id(name), boundary)
  private def _refs(names: String*): Presence[Vector[ModelReference]] = Present(names.toVector.map(_ref(_)))
  private def _source(index: Int): SourceAttribution = SourceAttribution("causal.example", "fixtures/causal-model.cml", "a" * 64, Some(math.min(index + 1, 19)))
  private def _semantic(name: String, dependency: Boolean, boundary: ReferenceBoundary = Local, derived: Boolean = false): SemanticReference = {
    val index = if (dependency) 10 else 5
    SemanticReference(ModelTarget(if (dependency) ModelElementId("causal.external", name) else _id(name)), boundary,
      Present(RelationId("causal-relations", if (dependency) "depends-on" else "read-model")),
      Present(ProfileId("causal-profiles", if (dependency) "dependency" else "read-model")),
      Present(if (dependency) "declared dependency" else "declared read-model reference"),
      Absent(NotDeclared, "No preferred causal label is declared."), _source(index),
      if (derived) Derived("literal.causal-reference.v1", Vector(_source(index), _source(10))) else Declared)
  }
  private def _literal(absent: Boolean): Literal = {
    def _fact_[A](ordinal: Int, field: Int, declared: Presence[A]): Presence[A] = if (absent) _gaps((ordinal + field) % 4) else declared
    val kinds = Vector("actor", "command", "operation", "aggregate", "entity", "event", "rule", "operation", "event", "workflow",
      "component", "query", "view", "causal-link", "causal-link", "causal-link", "causal-link", "use-case", "causal-link", "causal-link")
    val view = _semantic("view:order-summary", false, derived = true)
    val records = kinds.zipWithIndex.map { case (kind, index) =>
      val identity: Presence[ModelElementId] = if (index >= 18) Absent(NotRepresented, "No stable causal carrier identity is represented.")
        else if (absent && index == 0) Absent(NotRepresented, "No stable causal Actor identity is represented.") else Present(_id(_ids(index)))
      val references: Presence[Vector[SemanticReference]] = if (absent && index < 18) _gaps(index % 4)
        else if (!absent && index == 5) Present(Vector(view, _semantic("query:availability", false), _semantic("component:inventory", false, External), view))
        else if (!absent && index == 10) Present(Vector(_semantic("component:inventory", true, External))) else Present(Vector.empty)
      ElementRecord(identity, kind, "共有", _source(index), references)
    }
    val actor = CmlUseCaseMetadata.ActorProjection(records(0), _fact_(0, 0, Present("human")), _fact_(0, 1, Present("共有")),
      _fact_(0, 2, Present("declared buyer")), _fact_(0, 3, _refs("use-case:place-order")), _fact_(0, 4, _refs("workflow:fulfillment")),
      _fact_(0, 5, _refs("command:place-order", "operation:confirm-order", "command:place-order")),
      _fact_(0, 6, _refs("event:order-placed")), _fact_(0, 7, _refs("aggregate:order")))
    val usecase = CmlUseCaseMetadata.UseCaseProjection(records(17), _fact_(1, 0, Present("UC-CAUSAL-01")), _fact_(1, 1, Present("共有")),
      _fact_(1, 2, Present("declared placement")), _fact_(1, 3, Present("Place an order")),
      _fact_(1, 4, Present(DeclaredExpression("literal trigger; never parsed", _refs("command:place-order")))),
      _fact_(1, 5, Present(Vector.empty)), _fact_(1, 6, Present(Vector.empty)), _fact_(1, 7, Present("normal")), _fact_(1, 8, Present("declared")),
      _fact_(1, 9, Present(Vector(CmlUseCaseMetadata.ActorParticipation(CmlUseCaseMetadata.Primary, _ref("actor:buyer"))))),
      _fact_(1, 10, Present(Vector.empty)), _fact_(1, 11, _refs("aggregate:order", "entity:order-item")),
      _fact_(1, 12, _refs("command:place-order", "operation:confirm-order")), _fact_(1, 13, _refs("event:order-placed", "event:stock-reserved")),
      _fact_(1, 14, Present(Vector(_ref("component:inventory"), _ref("query:availability"), _ref("view:order-summary"),
        ModelReference(ModelElementId("causal.external", "component:inventory"), External)))), _fact_(1, 15, _refs("workflow:fulfillment")))
    val workflow = WorkflowProjection(records(9), _fact_(2, 0, Present("Fulfill declared orders")), _fact_(2, 1, Present(Vector.empty)),
      _fact_(2, 2, Present(Vector.empty)), _fact_(2, 3, _refs("actor:buyer", "component:inventory")),
      _fact_(2, 4, _refs("aggregate:order", "entity:order-item")), _fact_(2, 5, Present(Vector.empty)),
      _fact_(2, 6, _refs("command:place-order", "operation:confirm-order", "operation:reserve-stock")),
      _fact_(2, 7, _refs("event:order-placed", "event:stock-reserved")), _fact_(2, 8, _refs("rule:reserve-stock")), _fact_(2, 9, Present(Vector.empty)))
    val pairs = Vector((13, "command:place-order", "event:order-placed"), (14, "operation:confirm-order", "event:order-placed"),
      (15, "event:order-placed", "operation:reserve-stock"), (16, "operation:reserve-stock", "event:stock-reserved"),
      (18, "command:place-order", "event:order-placed"), (19, "event:order-placed", "operation:reserve-stock"))
    val affected = Vector(_refs("aggregate:order", "entity:order-item"), _refs("aggregate:order"), _refs("aggregate:order"),
      _refs("entity:order-item"), _refs(), _refs())
    val causal = pairs.zipWithIndex.map { case ((owner, left, right), index) =>
      def _field_[A](field: Int, declared: Presence[A]): Presence[A] =
        if (absent) _gaps((index + 3 + field) % 4) else if (index >= 4) _gaps((index + field) % 4) else declared
      CausalLinkProjection(records(owner), if (index == 2 || index == 5) CmlDynamicMetadata.Reaction else CmlDynamicMetadata.Cause,
        _field_(0, Present(_ref(left))), _field_(1, Present(_ref(right))),
        _field_(2, if (index == 2) Present(_ref("rule:reserve-stock")) else Absent(NotDeclared, "No causal rule is declared.")), _field_(3, affected(index)))
    }
    val structure = Vector(CmlStructureMetadata.ElementProjection(records(3), CmlStructureMetadata.Aggregate,
      _fact_(9, 0, Absent(NotApplicable, "The Aggregate has no containing Aggregate."))),
      CmlStructureMetadata.ElementProjection(records(4), CmlStructureMetadata.Entity, _fact_(10, 0, Present(CmlStructureMetadata.AggregateBoundary(
        Present(CmlStructureMetadata.ModelReference(_id("aggregate:order"), Local)), Present("member"))))))
    val terminology = records.map(record => CmlTerminologyMetadata.ElementProjection(record, record.references match {
      case Present(_) => Present(Vector.empty)
      case gap: Absent => gap
    }, Absent(Unsupported, "No causal conceptual group is supplied.")))
    Literal(records, actor, usecase, workflow, causal, structure, terminology)
  }

  // Literal encoders are independent of every Causal producer, reader and traversal.
  private def _presence[A](value: Presence[A], render: A => JsValue): JsObject = value match {
    case Present(payload) => Json.obj("status" -> "present", "value" -> render(payload))
    case Absent(reason, detail) => Json.obj("status" -> "absent", "reason" -> (reason match {
      case NotDeclared => "not-declared"; case NotRepresented => "not-represented"; case Unsupported => "unsupported"; case NotApplicable => "not-applicable"
    }), "detail" -> detail)
  }
  private def _id_json(value: ModelElementId): JsObject = Json.obj("modelId" -> value.modelId, "elementId" -> value.elementId)
  private def _source_json(value: SourceAttribution): JsObject = Json.obj("authorityId" -> value.authorityId, "path" -> value.path,
    "sha256" -> value.sha256, "line" -> value.line.fold[JsValue](JsNull)(line => JsNumber(line)))
  private def _ref_json(value: ModelReference): JsObject = Json.obj("identity" -> _id_json(value.identity), "boundary" -> (if (value.boundary == Local) "local" else "external"))
  private def _refs_json(value: Presence[Vector[ModelReference]]): JsObject = _presence(value, (items: Vector[ModelReference]) => JsArray(items.map(_ref_json)))
  private def _semantic_json(value: SemanticReference): JsObject = {
    val target = value.target match {
      case ModelTarget(id) => Json.obj("kind" -> "model-element", "identity" -> _id_json(id))
      case TermTarget(id) => Json.obj("kind" -> "term", "identity" -> Json.obj("vocabularyId" -> id.vocabularyId, "termId" -> id.termId))
    }
    val origin = value.origin match {
      case Declared => Json.obj("kind" -> "declared")
      case Derived(ruleid, sources) => Json.obj("kind" -> "derived", "ruleId" -> ruleid, "sources" -> JsArray(sources.map(_source_json)))
    }
    Json.obj("target" -> target, "boundary" -> (if (value.boundary == Local) "local" else "external"),
      "relation" -> _presence(value.relation, (id: RelationId) => Json.obj("vocabularyId" -> id.vocabularyId, "relationId" -> id.relationId)),
      "profile" -> _presence(value.profile, (id: ProfileId) => Json.obj("vocabularyId" -> id.vocabularyId, "profileId" -> id.profileId)),
      "context" -> _presence(value.context, JsString), "preferredLabel" -> _presence(value.preferredLabel, (label: LocalizedLabel) =>
        Json.obj("text" -> label.text, "language" -> label.language.fold[JsValue](JsNull)(JsString))), "source" -> _source_json(value.source), "origin" -> origin)
  }
  private def _semantics_json(value: Presence[Vector[SemanticReference]]): JsObject = _presence(value, (items: Vector[SemanticReference]) => JsArray(items.map(_semantic_json)))
  private def _record_json(value: ElementRecord): JsObject = Json.obj("identity" -> _presence(value.identity, _id_json), "kind" -> value.kind,
    "name" -> value.name, "source" -> _source_json(value.source), "references" -> _semantics_json(value.references))
  private def _projection_json(record: ElementRecord, fields: (String, JsValue)*): JsObject = JsObject(Vector("element" -> _record_json(record)) ++ fields)
  private def _causal_json(value: CausalLinkProjection): JsObject = _projection_json(value.element,
    "kind" -> JsString(if (value.kind == CmlDynamicMetadata.Cause) "cause" else "reaction"), "source" -> _presence(value.source, _ref_json),
    "target" -> _presence(value.target, _ref_json), "rule" -> _presence(value.rule, _ref_json), "affectedElements" -> _refs_json(value.affectedElements))
  private def _expanded(prefix: JsObject, literal: Literal): JsObject = {
    val actor = literal.actor; val usecase = literal.usecase; val workflow = literal.workflow
    val actorjson = _projection_json(actor.element, "actorKind" -> _presence(actor.actorKind, JsString), "summary" -> _presence(actor.summary, JsString),
      "description" -> _presence(actor.description, JsString), "useCases" -> _refs_json(actor.useCases), "workflows" -> _refs_json(actor.workflows),
      "operations" -> _refs_json(actor.operations), "events" -> _refs_json(actor.events), "domainElements" -> _refs_json(actor.domainElements))
    def _expression_(value: DeclaredExpression): JsValue = Json.obj("text" -> value.text, "references" -> _refs_json(value.references))
    def _empty_[A](value: Presence[Vector[A]]): JsValue = _presence(value, (_: Vector[A]) => Json.arr())
    val usecasejson = _projection_json(usecase.element, "declaredId" -> _presence(usecase.declaredId, JsString), "summary" -> _presence(usecase.summary, JsString),
      "description" -> _presence(usecase.description, JsString), "goal" -> _presence(usecase.goal, JsString), "trigger" -> _presence(usecase.trigger, _expression_),
      "preconditions" -> _empty_(usecase.preconditions), "postconditions" -> _empty_(usecase.postconditions), "priority" -> _presence(usecase.priority, JsString),
      "lifecycleStatus" -> _presence(usecase.lifecycleStatus, JsString), "actorReferences" -> _presence(usecase.actorReferences,
        (items: Vector[CmlUseCaseMetadata.ActorParticipation]) => JsArray(items.map(item => Json.obj("role" -> "primary", "target" -> _ref_json(item.target))))),
      "flows" -> _empty_(usecase.flows), "domainElements" -> _refs_json(usecase.domainElements), "operations" -> _refs_json(usecase.operations),
      "events" -> _refs_json(usecase.events), "collaborators" -> _refs_json(usecase.collaborators), "realizingWorkflows" -> _refs_json(usecase.realizingWorkflows))
    val workflowjson = _projection_json(workflow.element, "purpose" -> _presence(workflow.purpose, JsString), "activities" -> _refs_json(workflow.activities),
      "flows" -> _refs_json(workflow.flows), "participants" -> _refs_json(workflow.participants), "affectedElements" -> _refs_json(workflow.affectedElements),
      "stateMachines" -> _refs_json(workflow.stateMachines), "operations" -> _refs_json(workflow.operations), "events" -> _refs_json(workflow.events),
      "rules" -> _refs_json(workflow.rules), "stateEffects" -> _refs_json(workflow.stateEffects))
    val structurejson = literal.structure.map(value => _projection_json(value.element,
      "kind" -> JsString(if (value.kind == CmlStructureMetadata.Aggregate) "aggregate" else "entity"),
      "aggregateBoundary" -> _presence(value.aggregateBoundary, (boundary: CmlStructureMetadata.AggregateBoundary) => Json.obj(
        "aggregate" -> _presence(boundary.aggregate, (ref: CmlStructureMetadata.ModelReference) => _ref_json(ModelReference(ref.identity, ref.boundary))),
        "membership" -> _presence(boundary.membership, JsString)))))
    val terminologyjson = literal.terminology.map(value => _projection_json(value.element,
      "termReferences" -> _semantics_json(value.termReferences), "conceptualGroups" -> _semantics_json(value.conceptualGroups)))
    val additions = Vector(("cozy.cml.use-case", "actors", Vector(actorjson)), ("cozy.cml.use-case", "useCases", Vector(usecasejson)),
      ("cozy.cml.dynamic", "workflows", Vector(workflowjson)), ("cozy.cml.dynamic", "causalLinks", literal.causal.map(_causal_json)),
      ("cozy.cml.structure", "elements", structurejson), ("cozy.cml.terminology", "elements", terminologyjson))
    val extensions = additions.foldLeft(_extensions(prefix)) { case (ext, (key, field, values)) =>
      val child = ext.value(key).as[JsObject]
      ext + (key -> (child + (field -> JsArray((child \ field).as[Vector[JsValue]] ++ values))))
    } + ("org.example.causal-handoff" -> Json.obj("producer" -> "synthetic-caller", "causalStrength" -> "declared-only", "absence" -> JsNull))
    prefix + ("elements" -> JsArray((prefix \ "elements").as[Vector[JsValue]] ++ literal.records.map(_record_json))) + ("extensions" -> extensions)
  }

  // Occurrence rank, not identity/name/source, binds each supplied family to core order.
  private def _order[A](records: Vector[ElementRecord], values: Vector[A], element: A => ElementRecord): Vector[A] =
    records.zipWithIndex.flatMap { case (record, index) =>
      values.filter(value => element(value) == record).lift(records.take(index).count(_ == record))
    }
  private def _raw_order(records: Vector[JsValue], values: Vector[JsObject]): Vector[JsObject] =
    records.zipWithIndex.flatMap { case (record, index) => values.filter(value => (value \ "element").as[JsValue] == record).lift(records.take(index).count(_ == record)) }
  private def _model_filter(value: Presence[Vector[SemanticReference]]): Presence[Vector[SemanticReference]] = value match {
    case Present(items) => Present(items.filter(_.target.isInstanceOf[ModelTarget]))
    case gap: Absent => gap
  }
  private def _oracle(raw: JsObject): Oracle = {
    val structure = _right(CmlStructureMetadata.read(raw)); val classification = _right(CmlClassificationMetadata.read(raw))
    val dynamic = _right(CmlDynamicMetadata.read(raw)); val usecase = _right(CmlUseCaseMetadata.read(raw))
    val terminology = _right(CmlTerminologyMetadata.read(raw)); val records = dynamic.catalog.elements
    val causal = _order(records, dynamic.causalLinks, (value: CausalLinkProjection) => value.element)
    val actors = _order(records, usecase.actors.map(value => ActorOperations(value.element, value.operations)), (value: ActorOperations) => value.element)
    val aggregates = _order(records, structure.elements.map(value => AggregateMembership(value.element, value.aggregateBoundary)), (value: AggregateMembership) => value.element)
    val rules = _order(records, dynamic.workflows.map(value => RuleContext(value.element, value.rules)) ++ dynamic.activities.map(value => RuleContext(value.element, value.rules)), (value: RuleContext) => value.element)
    val collaborators = _order(records, usecase.useCases.map(value => CollaboratorContext(value.element, value.collaborators)), (value: CollaboratorContext) => value.element)
    val references = records.map(record => ModelReferences(record, _model_filter(record.references)))
    def _gap_(owner: ElementRecord, field: String, value: Presence[_]): Vector[CapabilityGap] = value match {
      case absence: Absent => Vector(CapabilityGap(Some(owner), field, absence)); case _ => Vector.empty
    }
    val gaps = records.flatMap(record => _gap_(record, "identity", record.identity) ++ _gap_(record, "modelReferences.references", record.references)) ++
      causal.flatMap(value => Vector("source" -> value.source, "target" -> value.target, "rule" -> value.rule, "affectedElements" -> value.affectedElements)
        .flatMap { case (field, presence) => _gap_(value.element, "causalLinks." + field, presence) }) ++
      actors.flatMap(value => _gap_(value.element, "actorOperations.operations", value.operations)) ++
      aggregates.flatMap(value => _gap_(value.element, "aggregateBoundaries.aggregateBoundary", value.aggregateBoundary) ++ (value.aggregateBoundary match {
        case Present(boundary) => _gap_(value.element, "aggregateBoundaries.aggregate", boundary.aggregate) ++ _gap_(value.element, "aggregateBoundaries.membership", boundary.membership)
        case _ => Vector.empty
      })) ++ rules.flatMap(value => _gap_(value.element, "ruleContexts.rules", value.rules)) ++
      collaborators.flatMap(value => _gap_(value.element, "collaboratorContexts.collaborators", value.collaborators)) ++ Vector(CapabilityGap(None, "causalInference", _inference_gap))
    val wire = raw + ("extensions" -> (_extensions(raw) + (namespace -> _child(raw))))
    val facts = Oracle(wire, records, dynamic.catalog.terms, structure, classification, dynamic, usecase, terminology,
      causal, actors, aggregates, rules, collaborators, references, gaps, Vector.empty)
    facts.copy(links = _links(facts))
  }
  private def _child(raw: JsObject): JsObject = {
    val records = (raw \ "elements").as[Vector[JsValue]]; val ext = _extensions(raw)
    def _array_(key: String, field: String): Vector[JsObject] = (ext \ key \ field).as[Vector[JsObject]]
    def _family_(values: Vector[JsObject], field: String): JsObject = Json.obj("status" -> "present", "value" -> JsArray(_raw_order(records,
      values.map(value => Json.obj("element" -> (value \ "element").as[JsValue], field -> (value \ field).as[JsValue])))))
    val references = records.map { record =>
      val value = (record \ "references").as[JsObject]
      val filtered = if ((value \ "status").as[String] == "absent") value else value + ("value" -> JsArray((value \ "value").as[Vector[JsValue]]
        .filter(item => (item \ "target" \ "kind").as[String] == "model-element")))
      Json.obj("element" -> record, "references" -> filtered)
    }
    val child = Json.obj("schemaVersion" -> "cozy.cml.causal.v1", "causalLinks" -> Json.obj("status" -> "present",
      "value" -> JsArray(_raw_order(records, _array_("cozy.cml.dynamic", "causalLinks")))),
      "actorOperations" -> _family_(_array_("cozy.cml.use-case", "actors"), "operations"),
      "aggregateBoundaries" -> _family_(_array_("cozy.cml.structure", "elements"), "aggregateBoundary"),
      "ruleContexts" -> _family_(_array_("cozy.cml.dynamic", "workflows") ++ _array_("cozy.cml.dynamic", "activities"), "rules"),
      "collaboratorContexts" -> _family_(_array_("cozy.cml.use-case", "useCases"), "collaborators"), "modelReferences" -> JsArray(references))
    def _gap_(owner: JsValue, field: String, value: JsValue): Vector[JsValue] =
      if ((value \ "status").as[String] == "absent") Vector(Json.obj("owner" -> owner, "field" -> field, "absence" -> value)) else Vector.empty
    val coregaps = records.flatMap(record => _gap_(record, "identity", (record \ "identity").as[JsValue]) ++
      _gap_(record, "modelReferences.references", (record \ "references").as[JsValue]))
    val fields = Vector("causalLinks" -> Vector("source", "target", "rule", "affectedElements"), "actorOperations" -> Vector("operations"),
      "aggregateBoundaries" -> Vector("aggregateBoundary"), "ruleContexts" -> Vector("rules"), "collaboratorContexts" -> Vector("collaborators"))
    val familygaps = fields.flatMap { case (family, names) => (child \ family \ "value").as[Vector[JsValue]].flatMap { value =>
      val owner = (value \ "element").as[JsValue]
      val direct = names.flatMap(field => _gap_(owner, family + "." + field, (value \ field).as[JsValue]))
      val nested = if (family == "aggregateBoundaries" && (value \ "aggregateBoundary" \ "status").as[String] == "present")
        Vector("aggregate", "membership").flatMap(field => _gap_(owner, family + "." + field, (value \ "aggregateBoundary" \ "value" \ field).as[JsValue])) else Vector.empty
      direct ++ nested
    } }
    child + ("capabilityGaps" -> JsArray(coregaps ++ familygaps :+ Json.obj("owner" -> JsNull, "field" -> "causalInference", "absence" -> _presence(_inference_gap, (_: Nothing) => JsNull))))
  }

  private def _links(expected: Oracle): Vector[TraversalLink] = expected.records.zipWithIndex.flatMap { case (owner, ordinal) =>
    def _occurrence_[A](values: Vector[A], element: A => ElementRecord): Vector[A] =
      values.filter(value => element(value) == owner).lift(expected.records.take(ordinal).count(_ == owner)).toVector
    val source: Presence[ModelReference] = owner.identity match { case Present(id) => Present(ModelReference(id, Local)); case gap: Absent => gap }
    def _context_(kind: LinkKind, left: Presence[ModelReference], right: Presence[ModelReference], token: String, field: String, index: Int): TraversalLink =
      TraversalLink(owner, kind, left, right, owner.source, Derived("cozy.cml.causal." + token + ".v1", Vector(owner.source)), field, index, None)
    val causal = _occurrence_(expected.causal, (value: CausalLinkProjection) => value.element).flatMap { carrier =>
      val direct = TraversalLink(owner, if (carrier.kind == CmlDynamicMetadata.Cause) Cause else Reaction,
        carrier.source, carrier.target, owner.source, Declared, "causalLinks", 0, None)
      val affected = _values(carrier.affectedElements).zipWithIndex.map { case (target, index) =>
        _context_(AffectedSubject, carrier.source, Present(target), "affected-subject", "causalLinks.affectedElements", index)
      }
      val rules = carrier.rule match {
        case Present(rule) => Vector(_context_(RuleInput, carrier.source, Present(rule), "rule-input", "causalLinks.rule", 0),
          _context_(RuleOutput, Present(rule), carrier.target, "rule-output", "causalLinks.rule", 1))
        case _ => Vector.empty
      }
      Vector(direct) ++ affected ++ rules
    }
    causal ++ _occurrence_(expected.actors, (value: ActorOperations) => value.element).flatMap(value => _values(value.operations).zipWithIndex.map {
      case (target, index) => _context_(ActorOperation, source, Present(target), "actor-operation", "actorOperations.operations", index)
    }) ++ _occurrence_(expected.aggregates, (value: AggregateMembership) => value.element).map { value =>
      val target: Presence[ModelReference] = value.aggregateBoundary match {
        case Present(boundary) => boundary.aggregate match { case Present(ref) => Present(ModelReference(ref.identity, ref.boundary)); case gap: Absent => gap }
        case gap: Absent => gap
      }
      _context_(AggregateMember, source, target, "aggregate-member", "aggregateBoundaries.aggregate", 0)
    } ++ _occurrence_(expected.rules, (value: RuleContext) => value.element).flatMap(value => _values(value.rules).zipWithIndex.map {
      case (target, index) => _context_(WorkflowRule, source, Present(target), "workflow-rule", "ruleContexts.rules", index)
    }) ++ _occurrence_(expected.collaborators, (value: CollaboratorContext) => value.element).flatMap(value => _values(value.collaborators).zipWithIndex.map {
      case (target, index) => _context_(Collaborator, source, Present(target), "collaborator", "collaboratorContexts.collaborators", index)
    }) ++ _values(_model_filter(owner.references)).zipWithIndex.map { case (reference, index) =>
      val ModelTarget(id) = reference.target
      TraversalLink(owner, ModelReferenceLink, source, Present(ModelReference(id, reference.boundary)), reference.source, reference.origin,
        "modelReferences.references", index, Some(reference))
    }
  }
  private def _graph_checks(graph: Graph, expected: Oracle): Vector[Boolean] = Vector(graph.catalog.elements == expected.records,
    graph.catalog.terms == expected.terms, graph.causalLinks == Present(expected.causal), graph.actorOperations == Present(expected.actors),
    graph.aggregateBoundaries == Present(expected.aggregates), graph.ruleContexts == Present(expected.rules),
    graph.collaboratorContexts == Present(expected.collaborators), graph.modelReferences == expected.references, graph.capabilityGaps == expected.gaps)
  private def _exercise(raw: JsObject, expected: Oracle): Vector[Boolean] = {
    val envelope = _right(CmlSemanticMetadata.read(_without_child(raw)))
    val built = _right(CmlCausalMetadata.build(envelope)); val read = _right(CmlCausalMetadata.read(raw))
    val rendered = CmlCausalMetadata.toJson(read); val canonical = CmlCausalMetadata.canonicalJson(read)
    val consumed = _right(CmlCausalMetadata.read(Json.parse(canonical)))
    val traversal = _right(CmlCausalTraversal.build(read))
    Vector(raw == expected.wire, rendered == expected.wire, CmlCausalMetadata.toJson(built) == expected.wire,
      canonical == Json.stringify(_keys(expected.wire, 0)), (built.catalog eq envelope.catalog), traversal.nodes == expected.records,
      traversal.allLinks == expected.links) ++ Vector(built, read, consumed).flatMap(_graph_checks(_, expected)) ++ _questions(traversal, expected)
  }
  private def _direct_links(values: Vector[TraversalLink]): Vector[TraversalLink] = values.filter(link => link.kind == Cause || link.kind == Reaction)
  private def _resolution(link: TraversalLink, records: Vector[ElementRecord]): Either[CausalDiagnostic, ReferenceTarget] = link.target match {
    case Present(ModelReference(id, Local)) => Right(LocalTarget(link, records.find(_.identity == Present(id)).get))
    case Present(ModelReference(_, External)) => Right(ExternalTarget(link))
    case gap: Absent => Right(AbsentTarget(link, gap))
  }
  private def _questions(traversal: Traversal, expected: Oracle): Vector[Boolean] = {
    val nodes = expected.records.collect { case record if record.identity.isInstanceOf[Present[_]] => record.identity.asInstanceOf[Present[ModelElementId]].value -> record }
    nodes.flatMap { case (id, record) =>
      val outgoing = expected.links.filter(_.source == Present(ModelReference(id, Local)))
      val incoming = expected.links.filter(_.target == Present(ModelReference(id, Local)))
      val affected = expected.causal.filter(value => _values(value.affectedElements).contains(ModelReference(id, Local)))
      Vector(traversal.node(id) == Right(record), traversal.outgoing(id) == Right(outgoing), traversal.incoming(id) == Right(incoming),
        traversal.causes(id) == Right(Present(_direct_links(incoming))), traversal.consequences(id) == Right(Present(_direct_links(outgoing))),
        traversal.affecting(id) == Right(Present(affected)))
    } ++ expected.links.map(link => traversal.resolve(link) == _resolution(link, expected.records))
  }
  private def _peers(raw: JsObject, expected: Oracle): Vector[Boolean] = {
    val structure = _right(CmlStructureMetadata.read(raw)); val classification = _right(CmlClassificationMetadata.read(raw))
    val dynamic = _right(CmlDynamicMetadata.read(raw)); val usecase = _right(CmlUseCaseMetadata.read(raw)); val terminology = _right(CmlTerminologyMetadata.read(raw))
    Vector(structure.elements == expected.structure.elements, structure.relations == expected.structure.relations,
      classification.traits == expected.classification.traits, classification.powertypes == expected.classification.powertypes,
      classification.generalizations == expected.classification.generalizations, classification.traitRealizations == expected.classification.traitRealizations,
      classification.powertypeClassifications == expected.classification.powertypeClassifications, dynamic.workflows == expected.dynamic.workflows,
      dynamic.activities == expected.dynamic.activities, dynamic.flows == expected.dynamic.flows, dynamic.stateMachines == expected.dynamic.stateMachines,
      dynamic.states == expected.dynamic.states, dynamic.transitions == expected.dynamic.transitions, dynamic.causalLinks == expected.dynamic.causalLinks,
      usecase.actors == expected.usecase.actors, usecase.useCases == expected.usecase.useCases, terminology.elements == expected.terminology.elements,
      terminology.terms == expected.terminology.terms) ++ Vector(structure.envelope, classification.envelope, dynamic.envelope, usecase.envelope, terminology.envelope)
      .flatMap(envelope => Vector(envelope.catalog.elements == expected.records, envelope.catalog.terms == expected.terms, CmlSemanticMetadata.toJson(envelope) == expected.wire))
  }
  private def _typed_publication(prefix: JsObject, literal: Literal, expected: Oracle): Vector[Boolean] = {
    val old = _oracle(_expanded(prefix, literal))
    val catalog = _right(CmlSemanticFoundation.build(expected.records, expected.terms)); val ext = _extensions(_without_child(expected.wire))
    val structure = _right(CmlStructureMetadata.build(catalog, old.structure.elements, old.structure.relations, ext - CmlStructureMetadata.namespace))
    val dynamic = _right(CmlDynamicMetadata.build(catalog, old.dynamic.workflows, old.dynamic.activities, old.dynamic.flows,
      old.dynamic.stateMachines, old.dynamic.states, old.dynamic.transitions, old.dynamic.causalLinks, ext - CmlDynamicMetadata.namespace))
    val usecase = _right(CmlUseCaseMetadata.build(catalog, old.usecase.actors, old.usecase.useCases, ext - CmlUseCaseMetadata.namespace))
    val terminology = _right(CmlTerminologyMetadata.build(catalog, old.terminology.elements, old.terminology.terms, ext - CmlTerminologyMetadata.namespace))
    val envelopes = Vector(structure.envelope, dynamic.envelope, usecase.envelope, terminology.envelope)
    val causal = envelopes.map(value => _right(CmlCausalMetadata.build(value)))
    Vector(old.records.takeRight(20) == literal.records, old.usecase.actors.last == literal.actor, old.usecase.useCases.last == literal.usecase,
      old.dynamic.workflows.last == literal.workflow, old.dynamic.causalLinks.takeRight(6) == literal.causal,
      old.structure.elements.takeRight(2) == literal.structure, old.terminology.elements.takeRight(20) == literal.terminology) ++
      envelopes.map(value => CmlSemanticMetadata.toJson(value) == _without_child(expected.wire)) ++ causal.flatMap(_graph_checks(_, expected)) ++
      causal.map(value => CmlCausalMetadata.toJson(value) == expected.wire)
  }
  private def _prefix_questions(raw: JsObject, prefix: JsObject): Vector[Boolean] = {
    val old = _right(CmlUseCaseMetadata.read(prefix)); val navigation = _right(CmlUseCaseNavigation.build(_right(CmlUseCaseMetadata.read(raw))))
    val dynamic = _right(CmlDynamicMetadata.read(prefix)); val topology = _right(CmlDynamicTopology.build(_right(CmlDynamicMetadata.read(raw))))
    old.actors.collect { case actor if actor.element.identity.isInstanceOf[Present[_]] =>
      navigation.actor(actor.element.identity.asInstanceOf[Present[ModelElementId]].value) == Right(actor)
    } ++ old.useCases.map(usecase => navigation.useCase(usecase.element.identity.asInstanceOf[Present[ModelElementId]].value) == Right(usecase)) ++
      dynamic.workflows.map(workflow => topology.workflow(workflow.element.identity.asInstanceOf[Present[ModelElementId]].value) == Right(workflow))
  }

  // Plans vary independent arrays; full embedded core replacements keep strict peer bindings coherent.
  private def _permuted(raw: JsObject, plan: Vector[Int]): JsObject = {
    def _ordered_[A](items: Vector[A], salt: Int): Vector[A] = items.zipWithIndex.sortBy { case (_, index) =>
      (java.lang.Integer.rotateLeft(plan(salt % plan.size) ^ (index * 0x9e3779b9), index % 31), index)
    }.map(_._1)
    val originals = (raw \ "elements").as[Vector[JsObject]]
    val replacements = originals.zipWithIndex.map { case (record, index) =>
      val refs = (record \ "references").as[JsObject]
      val changed = if ((refs \ "status").as[String] == "present") refs + ("value" -> JsArray(_ordered_((refs \ "value").as[Vector[JsValue]], index + 3))) else refs
      record -> (record + ("references" -> changed))
    }.toMap
    def _replace_(value: JsValue): JsValue = value match {
      case record: JsObject if replacements.contains(record) => replacements(record)
      case objectvalue: JsObject =>
        val changed = JsObject(objectvalue.fields.map { case (key, child) => key -> _replace_(child) })
        if (changed.keys.contains("termReferences")) {
          val references = (changed \ "element" \ "references").as[JsObject]
          val filtered = if ((references \ "status").as[String] == "absent") references else references + ("value" -> JsArray(
            (references \ "value").as[Vector[JsValue]].filter(item => (item \ "target" \ "kind").as[String] == "term")))
          changed + ("termReferences" -> filtered)
        } else changed
      case JsArray(items) =>
        val changed = items.map(_replace_).toVector
        if (changed.nonEmpty && changed.forall {
          case ref: JsObject => ref.keys == Set("identity", "boundary")
          case _ => false
        }) JsArray(_ordered_(changed, items.size + 7)) else JsArray(changed)
      case other => other
    }
    val replaced = _replace_(raw).as[JsObject]
    val ext = _families.zipWithIndex.foldLeft(_extensions(replaced)) { case (extensions, (key, index)) =>
      val child = extensions.value(key).as[JsObject]
      extensions + (key -> JsObject(child.fields.zipWithIndex.map { case ((field, value), ordinal) => field -> (value match {
        case JsArray(items) => JsArray(_ordered_(items.toVector, index + ordinal + 2)); case other => other
      }) }))
    }
    val changed = replaced + ("elements" -> JsArray(_ordered_((replaced \ "elements").as[Vector[JsValue]], 0))) +
      ("terms" -> JsArray(_ordered_((replaced \ "terms").as[Vector[JsValue]], 1))) + ("extensions" -> ext)
    _keys(changed, plan.last).as[JsObject]
  }
  private def _keys(value: JsValue, salt: Int): JsValue = value match {
    case objectvalue: JsObject =>
      val fields = objectvalue.fields.toVector.sortBy(_._1)
      val ordered = if (salt == 0) fields else fields.drop(Math.floorMod(salt, math.max(1, fields.size))) ++ fields.take(Math.floorMod(salt, math.max(1, fields.size)))
      JsObject(ordered.map { case (key, child) => key -> _keys(child, salt) })
    case JsArray(items) => JsArray(items.map(_keys(_, salt)))
    case other => other
  }
  private def _mutations: Vector[Mutation] = {
    val child = Vector("extensions", namespace); val path = "extensions.cozy.cml.causal"
    val shape = _diagnostic(InvalidShape, path, "The Causal value does not match the required v1 shape.")
    val projection = _diagnostic(InvalidProjection, path, "The Causal projection does not match its admitted semantic evidence.")
    Vector(Mutation(Vector("extensions"), value => value.as[JsObject] - namespace, shape), Mutation(child, _ => Json.obj(), shape.copy(path = path + ".schemaVersion")),
      Mutation(child, _ => JsString("hostile"), shape), Mutation(child :+ "schemaVersion", _ => JsNull, shape.copy(path = path + ".schemaVersion")),
      Mutation(child :+ "schemaVersion", _ => JsString("hostile version"), _diagnostic(UnsupportedSchemaVersion, path + ".schemaVersion", "The Causal schema version is unsupported.")),
      Mutation(child, value => value.as[JsObject] + ("hostile" -> JsNull), projection), Mutation(child, value => value.as[JsObject] - "actorOperations", projection),
      Mutation(child ++ Vector("causalLinks", "value", "2", "source", "value", "boundary"), _ => JsString("external"), projection),
      Mutation(child ++ Vector("modelReferences", "60", "references", "value", "0", "origin", "sources", "0", "line"), _ => JsNumber(999), projection),
      Mutation(child ++ Vector("modelReferences", "60", "references", "value"), value => JsArray(value.as[Vector[JsValue]].dropRight(1)), projection),
      Mutation(child ++ Vector("causalLinks", "value", "2", "source", "detail"), _ => JsString("hostile gap"), projection, absent = true),
      Mutation(child ++ Vector("capabilityGaps", "0", "absence", "detail"), _ => JsString("hostile gap"), projection, absent = true)) ++
      Vector("cozy.cml.dynamic" -> "Dynamic", "cozy.cml.use-case" -> "Use Case", "cozy.cml.structure" -> "Structure").map { case (key, name) =>
        Mutation(Vector("extensions", key), _ => Json.obj("schemaVersion" -> "hostile", "payload" -> "secret"),
          _diagnostic(InvalidSupplier, "extensions." + key, "The supplied " + name + " metadata is not admitted."))
      }
  }
  private def _change(value: JsValue, path: Vector[String], update: JsValue => JsValue): JsValue =
    if (path.isEmpty) update(value) else value match {
      case objectvalue: JsObject => objectvalue + (path.head -> _change(objectvalue.value(path.head), path.tail, update))
      case JsArray(items) => JsArray(items.updated(path.head.toInt, _change(items(path.head.toInt), path.tail, update)))
      case _ => throw new IllegalArgumentException("The literal mutation coordinate is invalid.")
    }
  private def _values[A](value: Presence[Vector[A]]): Vector[A] = value match { case Present(items) => items; case _: Absent => Vector.empty }
  private def _extensions(value: JsObject): JsObject = (value \ "extensions").as[JsObject]
  private def _without_child(value: JsObject): JsObject = value + ("extensions" -> (_extensions(value) - namespace))
  private def _right[A, B](value: Either[A, B]): B = value.right.get
  private def _filename(absent: Boolean): String = "causal-metadata-v1-" + (if (absent) "absence" else "declared") + ".json"
  private def _resource(name: String): JsObject = {
    val source = Source.fromInputStream(getClass.getResourceAsStream("/cozy/modeler/" + name), "UTF-8")
    try Json.parse(source.mkString).as[JsObject] finally source.close()
  }
  private def _traverse(raw: JsObject): Traversal = _right(CmlCausalTraversal.build(_right(CmlCausalMetadata.read(raw))))
  private def _diagnostic(kind: CausalDiagnosticKind, path: String, detail: String): CausalDiagnostic = CausalDiagnostic(kind, path, detail)
  private def _unknown(api: String): CausalDiagnostic = _diagnostic(UnknownElement, api + ".identity", "The Causal element identity is unknown or unaddressable.")
}
