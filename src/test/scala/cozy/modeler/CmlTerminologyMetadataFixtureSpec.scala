package cozy.modeler

import scala.io.Source
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalacheck.{Gen, Prop, Test}
import play.api.libs.json.{JsArray, JsBoolean, JsNull, JsNumber, JsObject, JsString, JsValue, Json}
import CmlSemanticFoundation._
import CmlTerminologyMetadata._
import CmlTerminologyNavigation.{TermLink, LocalTarget, ExternalTarget}

/*
 * @since   Oct.  4, 2026
 * @version Oct. 13, 2026
 */
final class CmlTerminologyMetadataFixtureSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  "Fixed Terminology handoff" should {
    "complete independent declarations" which {
      "TF-01 retain the entire declared resource through typed publication read and canonical reconsumption" in {
        Given("the declared UTF-8 filename and six complete literal occurrences with three independent Terms")
        val filename = _filename(false)
        val literal = _literal(false)
        When("the actual resource and independent accepted predecessor are admitted and the declarations are published")
        val prefix = _prefix()
        val expected = _complete(prefix, literal)
        val observed = _exercise(_resource(filename), prefix, expected)
        Then("all 55 records seven Terms full declarations sources duplicates and complete canonical wire equal the oracle")
        observed should contain only true
        expected.records.size shouldBe 55
        expected.terms.size shouldBe 7
        expected.elements.size shouldBe 55
        expected.projections.size shouldBe 7
        literal.records(4) shouldBe literal.records(5)
        literal.elements(4) shouldBe literal.elements(5)
        literal.projections.head.bokKind shouldBe Present(Koto)
        literal.projections(1).bokKind shouldBe Present(Mono)
      }
      "TF-02 retain complete explicit gaps and anonymous present-empty references in the absence resource" in {
        Given("the absence filename and exact four link gaps cyclic supplemental gaps and Unsupported groups")
        val filename = _filename(true)
        val literal = _literal(true)
        When("actual UTF-8 JSON and the independent predecessor support build read render and canonical reconsumption")
        val prefix = _prefix()
        val expected = _complete(prefix, literal)
        val observed = _exercise(_resource(filename), prefix, expected)
        Then("every full prefix carrier and supplied gap survives with no identity recovery or invented outbound link")
        observed should contain only true
        literal.records.take(4).map(_.references) shouldBe _link_gaps
        literal.records.drop(4).map(_.references) shouldBe Vector.fill(2)(Present(Vector.empty))
        literal.elements.map(_.conceptualGroups) shouldBe Vector.fill(6)(_group_gap)
        literal.projections.zipWithIndex.foreach { case (projection, index) =>
          Vector(projection.termType, projection.bokKind, projection.labels, projection.preferredLabel) shouldBe
            Vector.tabulate(4)(field => _fact_gaps((index + field) % 4))
        }
      }
    }
    "JSON-only semantic questions" which {
      "TF-03 answer qualified model Term and Local External questions using only admitted resource JSON" in {
        Given("complete raw expected Entity Koto Event Mono labels candidate context and supplied provenance")
        val literal = _literal(false)
        val local = TermLink(literal.records(0), _ra)
        val external = TermLink(literal.records(0), _rae)
        val event = TermLink(literal.records(2), _event_ref)
        When("the resource alone supplies the Terminology graph and navigation for each semantic question")
        val graph = _right(CmlTerminologyMetadata.read(_resource(_filename(false))))
        val navigation = _right(CmlTerminologyNavigation.build(graph))
        val element = navigation.element(ModelElementId("terminology.a", "shared"))
        val term = navigation.term(_ta)
        val first = navigation.resolve(local)
        val retained = navigation.resolve(external)
        val eventtarget = navigation.resolve(event)
        val eventelement = navigation.element(ModelElementId("terminology.a", "event:shared"))
        Then("full independent carriers retain supplied classifications labels opaque candidates and ordered derived sources")
        element shouldBe Right(literal.elements(0))
        term shouldBe Right(literal.projections(0))
        first shouldBe Right(LocalTarget(local, literal.projections(0)))
        retained shouldBe Right(ExternalTarget(external))
        eventtarget shouldBe Right(LocalTarget(event, literal.projections(1)))
        eventelement shouldBe Right(literal.elements(2))
        literal.records(2).kind shouldBe "event"
        literal.projections(0).preferredLabel shouldBe Present(LocalizedLabel("優先外", Some("ja")))
        literal.projections(0).labels shouldBe Present(Vector(LocalizedLabel("共有", Some("ja")),
          LocalizedLabel("Shared", Some("en")), LocalizedLabel("共有", Some("ja")), LocalizedLabel("無言語", None)))
      }
      "TF-04 preserve ordered duplicate reverse links and exact gaps without descriptive aliases" in {
        Given("both raw modes and safe unknown pairs equivalent copies and a well-formed nonmember link")
        val literals = Vector(_literal(false), _literal(true))
        val guesses = Vector("共有", "glossary/terminology.dox", "50", "unused")
        When("both actual resources are read and full qualified navigation membership and reverse questions are captured")
        val prefix = _prefix()
        val observed = literals.zipWithIndex.map { case (literal, index) =>
          val expected = _complete(prefix, literal)
          val navigation = _right(CmlTerminologyNavigation.build(_right(CmlTerminologyMetadata.read(_resource(_filename(index == 1))))))
          val links = _links(expected)
          val copied = TermLink(literals.head.records(0).copy(), _ra.copy())
          val foreign = copied.copy(reference = _ra.copy(context = Present("Altered nonmember.")))
          val equivalentexternal = copied.copy(reference = _rae.copy())
          val membership = if (index == 0) Vector(navigation.resolve(copied) == Right(LocalTarget(copied, literal.projections(0))),
            navigation.resolve(equivalentexternal) == Right(ExternalTarget(equivalentexternal))) else Vector.empty
          _queries(navigation, expected) ++ membership ++ Vector(
            navigation.resolve(foreign) == Left(_diagnostic(UnknownLink, "resolve.link", _unknown_link_detail)),
            navigation.term(_tf) == Left(_unknown_term("term.identity")),
            navigation.linksForTerm(_tf) == Left(_unknown_term("linksForTerm.identity")),
            links.size == (if (index == 0) 11 else 0),
            links.count(link => link.reference.boundary == Local && link.reference.target == TermTarget(_ta)) == (if (index == 0) 6 else 0),
            links.count(link => link.reference.boundary == Local && link.reference.target == TermTarget(_tb)) == (if (index == 0) 2 else 0),
            links.count(_.reference.boundary == External) == (if (index == 0) 3 else 0)) ++ guesses.flatMap { guess => Vector(
            navigation.element(ModelElementId("terminology.a", guess)) == Left(_unknown_element("element.identity")),
            navigation.links(ModelElementId("terminology.a", guess)) == Left(_unknown_element("links.identity"))) }
        }
        Then("anonymous occurrences each retain two complete links declared reverse order and unused Terms stay valid and empty")
        observed.flatten should contain only true
      }
    }
    "independent peer compatibility" which {
      "TF-05 admit the same expanded resource under every peer and continue the accepted Use Case Dynamic static questions" in {
        Given("both literal modes the complete accepted predecessor and exactly two new Structure Entity projections")
        val literals = Vector(_literal(false), _literal(true))
        When("Structure Classification Dynamic and Use Case separately admit each resource and qualified predecessor questions run")
        val prefix = _prefix()
        val observed = literals.zipWithIndex.flatMap { case (literal, index) =>
          val expected = _complete(prefix, literal)
          val raw = _resource(_filename(index == 1))
          _peers(raw, prefix, expected) ++ _predecessor_questions(raw, prefix)
        }
        Then("full 55-record seven-Term catalogs complete carrier vectors and all eight prior extension values match independent facts")
        observed should contain only true
      }
    }
  }

  "Fixed Terminology admission" should {
    "recursive strict shapes" which {
      "TF-06 report exact safe single-mutation diagnostics and preserve original base-first publication failures" in {
        Given("literal mutation paths kinds details and publication attributions independent of actual reader results")
        val mutations = _shape_mutations
        val publication = _publication(CmlSemanticMetadata.InvalidExtension, "extensions[3]", "The extension namespace is invalid.")
        val basefailure = _publication(CmlSemanticMetadata.InvalidShape, "schemaVersion", "The semantic-metadata value does not match the required v1 shape.")
        When("each mutation is applied to the appropriate parsed real resource before strict admission")
        val declared = _resource(_filename(false))
        val absence = _resource(_filename(true))
        val observed = mutations.map { mutation => CmlTerminologyMetadata.read(_change(
          if (mutation.absent) absence else declared, _child_path ++ mutation.path, mutation.update)) }
        val missing = CmlTerminologyMetadata.read(_change(declared, Vector("extensions"), value => value.as[JsObject] - namespace))
        val nullchild = CmlTerminologyMetadata.read(_change(declared, _child_path, _ => JsNull))
        val nonobject = CmlTerminologyMetadata.read(_change(declared, _child_path, _ => JsString("hostile")))
        val invalidbase = CmlTerminologyMetadata.read((declared - "schemaVersion") + ("extensions" -> Json.obj(namespace -> Json.obj("schemaVersion" -> "contradiction"))))
        Then("fixed coordinates never echo input and unsupported child versions precede contradictory fields after base admission")
        observed shouldBe mutations.map(mutation => Left(Vector(_diagnostic(mutation.kind, mutation.coordinate, mutation.detail))))
        missing shouldBe Left(Vector(_diagnostic(InvalidShape, "extensions.cozy.cml.terminology", _shape_detail)))
        nullchild shouldBe Left(Vector(publication))
        nonobject shouldBe Left(Vector(publication))
        invalidbase shouldBe Left(Vector(basefailure))
      }
    }
    "full occurrence core and reference authority" which {
      "TF-07 require complete Element Term anonymous occurrences exact Term filters and bounded complete groups" in {
        Given("both raw modes and literal missing excess full-field forgeries and contradictory filter or group declarations")
        val literals = Vector(_literal(false), _literal(true))
        val cases = literals.map(_failure_cases)
        val copies = literals.map(literal => literal.copy(elements = literal.elements.map(projection => projection.copy(
          conceptualGroups = projection.conceptualGroups match { case Present(refs) => Present(refs.map(_.copy())); case gap: Absent => gap }))))
        When("independent raw mutations reach both the typed factory and strict wire reader with the catalog held fixed")
        val prefix = _prefix()
        val observed = literals.zipWithIndex.flatMap { case (literal, index) =>
          val supplied = _complete(prefix, literal)
          val catalog = _right(CmlSemanticFoundation.build(supplied.records, supplied.terms))
          val failures = cases(index).flatMap { failure =>
            val changed = _complete(prefix, failure.input)
            Vector(CmlTerminologyMetadata.build(catalog, changed.elements, changed.projections, _peer_extensions(prefix, supplied)) == Left(failure.typederrors),
              CmlTerminologyMetadata.read(_wire(prefix, changed)) == Left(failure.wireerrors))
          }
          val copied = _complete(prefix, copies(index))
          failures ++ _exercise(_wire(prefix, copied), prefix, copied) ++ _exercise(_resource(_filename(index == 1)), prefix, supplied)
        }
        Then("missing equal anonymous occurrences excessive groups and absent versus present-empty contradictions fail literally")
        observed should contain only true
      }
    }
    "independent canonical reconstruction" which {
      "TF-08 reconstruct every object key order while preserving arrays complete facts and required nulls" in {
        Given("both complete raw modes and a test-owned recursive key sort with authored array order")
        val literals = Vector(_literal(false), _literal(true))
        When("real resource objects are recursively reversed parsed read rendered and canonically reconsumed")
        val prefix = _prefix()
        val observed = literals.zipWithIndex.flatMap { case (literal, index) =>
          val rebuilt = Json.parse(Json.stringify(_keys(_resource(_filename(index == 1)), true))).as[JsObject]
          _exercise(rebuilt, prefix, _complete(prefix, literal))
        }
        Then("complete independent wire and semantic queries retain duplicate arrays source line null and label language null")
        observed should contain only true
      }
    }
  }

  "Active fixed-resource properties" should {
    "independent occurrence permutations" which {
      "TF-09 preserve both modes in every non-discarded sample with observable full-vector coverage" in {
        Given("seed 42 and independent remaining-index permutations for references catalogs Terms and both projection arrays")
        val literals = Vector(_literal(false), _literal(true))
        val tags = Vector("declared", "absence", "references-reordered", "records-reordered", "terms-reordered", "elements-reordered", "projections-reordered")
        val generator = for { plans <- Gen.listOfN(2, _plan); reverse <- Gen.oneOf(false, true) } yield (plans.toVector, reverse)
        When("ScalaCheck admits both real permuted resources and independent factories and captures every qualified peer query")
        val prefix = _prefix()
        val resources = Vector(_resource(_filename(false)), _resource(_filename(true)))
        val counts = scala.collection.mutable.Map(tags.map(_ -> 0): _*)
        val property = Prop.forAll(generator) { case (plans, reverse) =>
          Given("one complete raw two-mode declaration and occurrence plan independent of supplier outputs")
          val originals = literals.map(_complete(prefix, _))
          val changed = originals.zip(plans).map { case (original, plan) => _ordered(original, plan) }
          val coverage = Vector("declared", "absence") ++ Vector(
            "references-reordered" -> originals.zip(plans).exists { case (original, plan) => _referred(original, plan).records.map(_.references) != original.records.map(_.references) },
            "records-reordered" -> originals.zip(plans).exists { case (original, plan) => plan.records.map(original.records) != original.records },
            "terms-reordered" -> originals.zip(changed).exists { case (before, after) => before.terms != after.terms },
            "elements-reordered" -> originals.zip(plans).exists { case (original, plan) => plan.elements.map(original.elements) != original.elements },
            "projections-reordered" -> originals.zip(changed).exists { case (before, after) => before.projections != after.projections })
            .collect { case (tag, true) => tag }
          When("actual core references embedded records filters peer additions and independent array orders are reconstructed and consumed")
          val observed = resources.indices.flatMap { index =>
            val raw = _keys(_ordered_resource(resources(index), plans(index)), reverse).as[JsObject]
            _exercise(raw, prefix, changed(index)) ++ _peers(raw, prefix, changed(index)) ++ _predecessor_questions(raw, prefix)
          }
          counts.keys.toVector.foreach(tag => if (coverage.contains(tag)) { counts.update(tag, counts(tag) + 1) })
          Then("complete wire canonical carriers gaps anonymous multiplicity and all Local External queries equal raw expectations")
          observed should contain only true
          Prop.collect(coverage.sorted.mkString(","))(Prop(observed.forall(identity)))
        }
        val result = Test.check(Test.Parameters.default.withMinSuccessfulTests(100).withWorkers(1).withInitialSeed(42L), property)
        val coverage = counts.toVector.sortBy(_._1)
        Then("actual property evidence reports at least 100 successes zero discards and every required collected value-change tag")
        info(s"TF-09 succeeded=${result.succeeded} discarded=${result.discarded} passed=${result.passed}")
        info("TF-09 coverage=" + coverage.map { case (tag, count) => s"$tag:$count" }.mkString(","))
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

  private final case class Input(records: Vector[ElementRecord], terms: Vector[TermRecord],
    elements: Vector[ElementProjection], projections: Vector[TermProjection])
  private final case class Prefix(raw: JsObject, structure: CmlStructureMetadata.Graph,
    classification: CmlClassificationMetadata.Graph, dynamic: CmlDynamicMetadata.Graph, usecase: CmlUseCaseMetadata.Graph)
  private final case class Plan(refs: Vector[Vector[Int]], records: Vector[Int], terms: Vector[Int], elements: Vector[Int], projections: Vector[Int])
  private final case class Failure(input: Input, typederrors: Vector[TerminologyDiagnostic], wireerrors: Vector[TerminologyDiagnostic])
  private final case class Mutation(path: Vector[String], update: JsValue => JsValue, coordinate: String,
    kind: TerminologyDiagnosticKind = InvalidShape, detail: String = "The Terminology value does not match the required v1 shape.", absent: Boolean = false)
  private val _ta = TermId("terminology.a", "shared")
  private val _tb = TermId("terminology.b", "shared")
  private val _tu = TermId("terminology.a", "unused")
  private val _tf = TermId("external.terminology", "shared")
  private val _child_path = Vector("extensions", namespace)
  private val _group_gap = Absent(Unsupported, "The conceptual group is unsupported.")
  private val _baseline_group = Absent(NotRepresented, "No conceptual group is represented.")
  private val _baseline_fact = Absent(NotRepresented, "No Terminology fact is represented.")
  private val _link_gaps = Vector(Absent(NotDeclared, "No Terminology link is declared."),
    Absent(NotRepresented, "The Terminology link is not represented."), Absent(Unsupported, "The Terminology link is unsupported."),
    Absent(NotApplicable, "The Terminology link is not applicable."))
  private val _fact_gaps = Vector(Absent(NotDeclared, "No Terminology fact is declared."),
    Absent(NotRepresented, "The Terminology fact is not represented."), Absent(Unsupported, "The Terminology fact is unsupported."),
    Absent(NotApplicable, "The Terminology fact is not applicable."))
  private val _shape_detail = "The Terminology value does not match the required v1 shape."
  private val _semantic_detail = "The declared Terminology reference, label, classification or absence is invalid."
  private val _binding_detail = "The Terminology projection does not bind exactly to an admitted core record."
  private val _embedded_detail = "The embedded Terminology record does not exactly match admitted core JSON."
  private val _reference_detail = "The Terminology reference does not exactly match an admitted core reference."
  private val _missing_detail = "An admitted core record lacks its required Terminology projection occurrence."
  private val _duplicate_detail = "A core record has excess Terminology projection occurrences."
  private val _unknown_link_detail = "The Terminology link is not admitted by this graph."
  private def _source(owner: String, line: Int): SourceAttribution = { SourceAttribution(owner, "src/main/cml/terminology-handoff.cml", Some(line)) }
  private def _reference(owner: String, line: Int, target: SemanticTarget, boundary: ReferenceBoundary, token: String): SemanticReference = {
    SemanticReference(target, boundary, Present(RelationId("relations.example", "synonym-candidate")),
      Present(ProfileId("profiles.example", "view:literal")), Present("Terminology " + token + "."),
      Present(LocalizedLabel("参照 " + token, None)), _source(owner, line),
      Derived("rule:literal", Vector(_source("input.a", 70), _source("input.b", 71))))
  }
  private val _ra = _reference("terminology.a", 50, TermTarget(_ta), Local, "a-local")
  private val _rae = _reference("terminology.a", 50, TermTarget(_ta), External, "a-external")
  private val _model_ref = _reference("terminology.a", 50, ModelTarget(ModelElementId("terminology.b", "shared")), Local, "a-model").copy(origin = Declared)
  private val _rb = _reference("terminology.b", 51, TermTarget(_tb), Local, "b-local")
  private val _foreign_ref = _reference("terminology.b", 51, TermTarget(_tf), External, "b-foreign")
  private val _event_ref = _reference("terminology.a", 52, TermTarget(_tb), Local, "event-local")
  private val _event_external = _reference("terminology.a", 52, TermTarget(_tb), External, "event-external")
  private val _anonymous_ref = _reference("terminology.anonymous", 54, TermTarget(_ta), Local, "anonymous-local")
  private def _literal(absent: Boolean): Input = {
    val references: Vector[Presence[Vector[SemanticReference]]] = if (absent) _link_gaps ++ Vector.fill(2)(Present(Vector.empty)) else
      Vector(Present(Vector(_ra, _ra, _rae, _model_ref)), Present(Vector(_rb, _foreign_ref)),
        Present(Vector(_event_ref, _event_external)), Present(Vector.empty), Present(Vector(_anonymous_ref, _anonymous_ref)), Present(Vector(_anonymous_ref, _anonymous_ref)))
    val identities: Vector[Presence[ModelElementId]] = Vector(Present(ModelElementId("terminology.a", "shared")),
      Present(ModelElementId("terminology.b", "shared")), Present(ModelElementId("terminology.a", "event:shared")),
      Present(ModelElementId("terminology.b", "event:shared"))) ++ Vector.fill(2)(Absent(NotRepresented, "No stable Terminology element identity is represented."))
    val owners = Vector("terminology.a", "terminology.b", "terminology.a", "terminology.b", "terminology.anonymous", "terminology.anonymous")
    val records = identities.indices.map(index => ElementRecord(identities(index), if (index < 2) "entity" else "event", "共有",
      _source(owners(index), if (index == 5) 54 else index + 50), references(index))).toVector
    val groups: Vector[Presence[Vector[SemanticReference]]] = if (absent) Vector.fill(6)(_group_gap) else Vector(
      Present(Vector(_model_ref, _ra, _ra)), Present(Vector.empty), Present(Vector(_event_ref)), Present(Vector.empty), _group_gap, _group_gap)
    val terms = Vector(TermRecord(_ta, SourceAttribution("terminology.a", "glossary/terminology.dox", Some(60))),
      TermRecord(_tb, SourceAttribution("terminology.b", "glossary/terminology.dox", Some(61))),
      TermRecord(_tu, SourceAttribution("terminology.a", "glossary/terminology.dox", None)))
    val projections = if (absent) terms.zipWithIndex.map { case (term, index) => TermProjection(term, _fact_gaps(index),
      _fact_gaps((index + 1) % 4), _fact_gaps((index + 2) % 4), _fact_gaps((index + 3) % 4)) } else Vector(
      TermProjection(terms(0), Present("event-type"), Present(Koto), Present(Vector(LocalizedLabel("共有", Some("ja")),
        LocalizedLabel("Shared", Some("en")), LocalizedLabel("共有", Some("ja")), LocalizedLabel("無言語", None))), Present(LocalizedLabel("優先外", Some("ja")))),
      TermProjection(terms(1), Present("entity-type"), Present(Mono), Present(Vector(LocalizedLabel("共有", Some("ja")), LocalizedLabel("Shared", None))),
        Present(LocalizedLabel("Preferred B", Some("en")))),
      TermProjection(terms(2), Present("opaque-unused"), Absent(NotApplicable, "The unused Term has no BoK classification."),
        Present(Vector.empty), Absent(NotDeclared, "No preferred label is declared.")))
    Input(records, terms, records.zip(groups).map { case (record, group) => ElementProjection(record, _term_filter(record.references), group) }, projections)
  }
  private def _term_filter(value: Presence[Vector[SemanticReference]]): Presence[Vector[SemanticReference]] = { value match {
    case Present(refs) => Present(refs.filter(_.target.isInstanceOf[TermTarget]))
    case gap: Absent => gap
  } }
  private def _complete(prefix: Prefix, literal: Input): Input = {
    Input(prefix.usecase.catalog.elements ++ literal.records, prefix.usecase.catalog.terms ++ literal.terms,
      prefix.usecase.catalog.elements.map(record => ElementProjection(record, record.references, _baseline_group)) ++ literal.elements,
      prefix.usecase.catalog.terms.map(term => TermProjection(term, _baseline_fact, _baseline_fact, _baseline_fact, _baseline_fact)) ++ literal.projections)
  }
  private def _new_structure(supplied: Input): Vector[CmlStructureMetadata.ElementProjection] = {
    Vector("terminology.a", "terminology.b").map(owner => CmlStructureMetadata.ElementProjection(
      supplied.records.find(_.identity == Present(ModelElementId(owner, "shared"))).get, CmlStructureMetadata.Entity,
      Absent(NotDeclared, "No aggregate boundary is declared.")))
  }
  private def _peer_extensions(prefix: Prefix, supplied: Input): JsObject = {
    val extensions = _extensions(prefix.raw)
    val structure = extensions.value(CmlStructureMetadata.namespace).as[JsObject]
    val additions = _new_structure(supplied).map(value => Json.obj("element" -> _element_json(value.element), "kind" -> "entity",
      "aggregateBoundary" -> _presence_json(value.aggregateBoundary, (_: CmlStructureMetadata.AggregateBoundary) => JsNull)))
    extensions + (CmlStructureMetadata.namespace -> (structure + ("elements" -> JsArray((structure \ "elements").as[Vector[JsValue]] ++ additions))))
  }
  private def _wire(prefix: Prefix, supplied: Input): JsObject = {
    val child = Json.obj("schemaVersion" -> "cozy.cml.terminology.v1", "elements" -> JsArray(supplied.elements.map(_projection_json)),
      "terms" -> JsArray(supplied.projections.map(_term_projection_json)))
    prefix.raw + ("elements" -> JsArray(supplied.records.map(_element_json))) + ("terms" -> JsArray(supplied.terms.map(_term_json))) +
      ("extensions" -> (_peer_extensions(prefix, supplied) + (namespace -> child)))
  }
  private def _exercise(raw: JsObject, prefix: Prefix, supplied: Input): Vector[Boolean] = {
    val wire = _wire(prefix, supplied)
    val canonical = Json.stringify(_keys(wire, false))
    val catalog = _right(CmlSemanticFoundation.build(supplied.records, supplied.terms))
    val built = _right(CmlTerminologyMetadata.build(catalog, supplied.elements, supplied.projections, _peer_extensions(prefix, supplied)))
    val read = _right(CmlTerminologyMetadata.read(raw))
    val consumed = _right(CmlTerminologyMetadata.read(Json.parse(CmlTerminologyMetadata.canonicalJson(read))))
    Vector(raw == wire, supplied.records.count(_.identity.isInstanceOf[Absent]) == 2) ++ Vector(built, read, consumed).flatMap { graph =>
      Vector(graph.catalog.elements == supplied.records, graph.catalog.terms == supplied.terms, graph.elements == supplied.elements,
        graph.terms == supplied.projections, graph.envelope.extensions == _extensions(wire), CmlTerminologyMetadata.toJson(graph) == wire,
        CmlTerminologyMetadata.canonicalJson(graph) == canonical) ++ _queries(_right(CmlTerminologyNavigation.build(graph)), supplied)
    }
  }
  private def _links(supplied: Input): Vector[TermLink] = { supplied.records.flatMap { record => record.references match {
    case Present(refs) => refs.filter(_.target.isInstanceOf[TermTarget]).map(TermLink(record, _))
    case _: Absent => Vector.empty
  } } }
  private def _queries(navigation: CmlTerminologyNavigation.Navigation, supplied: Input): Vector[Boolean] = {
    val links = _links(supplied)
    val elements = supplied.elements.flatMap { projection => projection.element.identity match {
      case Present(id) => Vector(navigation.element(id) == Right(projection), navigation.links(id) == Right(projection.termReferences match {
        case Present(refs) => Present(refs.map(TermLink(projection.element, _)))
        case gap: Absent => gap
      }))
      case _: Absent => Vector.empty
    } }
    elements ++ supplied.projections.flatMap { projection => Vector(navigation.term(projection.term.identity) == Right(projection),
      navigation.linksForTerm(projection.term.identity) == Right(links.filter(link => link.reference.boundary == Local && link.reference.target == TermTarget(projection.term.identity)))) } ++
      links.map { link => navigation.resolve(link.copy(element = link.element.copy(), reference = link.reference.copy())) == Right(
        if (link.reference.boundary == External) ExternalTarget(link) else LocalTarget(link,
          supplied.projections.find(_.term.identity == link.reference.target.asInstanceOf[TermTarget].identity).get)) } ++
      Vector(navigation.allLinks == links, navigation.linksForTerm(_tu) == Right(Vector.empty),
        links.count(_.element.identity.isInstanceOf[Absent]) == (if (links.isEmpty) 0 else 4))
  }
  private def _peers(raw: JsObject, prefix: Prefix, supplied: Input): Vector[Boolean] = {
    val structure = _right(CmlStructureMetadata.read(raw))
    val classification = _right(CmlClassificationMetadata.read(raw))
    val dynamic = _right(CmlDynamicMetadata.read(raw))
    val usecase = _right(CmlUseCaseMetadata.read(raw))
    val catalogs = Vector(structure.catalog, classification.catalog, dynamic.catalog, usecase.catalog)
    catalogs.flatMap(catalog => Vector(catalog.elements == supplied.records, catalog.terms == supplied.terms)) ++ Vector(
      structure.elements == prefix.structure.elements ++ _new_structure(supplied), structure.elements.size == 5, structure.relations == prefix.structure.relations,
      classification.traits == prefix.classification.traits, classification.powertypes == prefix.classification.powertypes,
      classification.generalizations == prefix.classification.generalizations, classification.traitRealizations == prefix.classification.traitRealizations,
      classification.powertypeClassifications == prefix.classification.powertypeClassifications,
      dynamic.workflows == prefix.dynamic.workflows, dynamic.activities == prefix.dynamic.activities, dynamic.flows == prefix.dynamic.flows,
      dynamic.stateMachines == prefix.dynamic.stateMachines, dynamic.states == prefix.dynamic.states, dynamic.transitions == prefix.dynamic.transitions,
      dynamic.causalLinks == prefix.dynamic.causalLinks, usecase.actors == prefix.usecase.actors, usecase.useCases == prefix.usecase.useCases,
      _extensions(raw) - namespace == _peer_extensions(prefix, supplied), _extensions(raw).keys.size == 9)
  }
  private def _predecessor_questions(raw: JsObject, prefix: Prefix): Vector[Boolean] = {
    val navigation = _right(CmlUseCaseNavigation.build(_right(CmlUseCaseMetadata.read(raw))))
    val topology = _right(CmlDynamicTopology.build(_right(CmlDynamicMetadata.read(raw))))
    val structure = _right(CmlStructureMetadata.read(raw))
    val actorid = ModelElementId("usecase.example", "actor:requester")
    val usecaseid = ModelElementId("usecase.example", "use-case:complete-order")
    val workflowid = ModelElementId("dynamic.example", "workflow:fulfillment")
    val actor = prefix.usecase.actors.find(_.element.identity == Present(actorid)).get
    val usecase = prefix.usecase.useCases.find(_.element.identity == Present(usecaseid)).get
    val workflow = prefix.dynamic.workflows.find(_.element.identity == Present(workflowid)).get
    Vector(navigation.actor(actorid) == Right(actor), navigation.useCase(usecaseid) == Right(usecase), topology.workflow(workflowid) == Right(workflow),
      navigation.resolve(CmlDynamicMetadata.ModelReference(usecaseid, Local)) == Right(CmlUseCaseNavigation.LocalTarget(usecase.element)),
      navigation.resolve(CmlDynamicMetadata.ModelReference(workflowid, Local)) == Right(CmlUseCaseNavigation.LocalTarget(workflow.element))) ++
      Vector("entity:order", "aggregate:order").flatMap { id =>
        val identity = ModelElementId("structure.example", id)
        val projection = prefix.structure.elements.find(_.element.identity == Present(identity)).get
        Vector(navigation.element(identity) == Right(projection.element),
          navigation.resolve(CmlDynamicMetadata.ModelReference(identity, Local)) == Right(CmlUseCaseNavigation.LocalTarget(projection.element)),
          structure.elements.find(_.element.identity == Present(identity)) == Some(projection))
      }
  }

  // Every renderer below is test-owned and operates only on raw declarations.
  private def _presence_json[A](value: Presence[A], render: A => JsValue): JsObject = { value match {
    case Present(payload) => Json.obj("status" -> "present", "value" -> render(payload))
    case Absent(reason, detail) => Json.obj("status" -> "absent", "reason" -> (reason match {
      case NotDeclared => "not-declared"; case NotRepresented => "not-represented"; case Unsupported => "unsupported"; case NotApplicable => "not-applicable"
    }), "detail" -> detail)
  } }
  private def _model_json(value: ModelElementId): JsObject = { Json.obj("modelId" -> value.modelId, "elementId" -> value.elementId) }
  private def _term_id_json(value: TermId): JsObject = { Json.obj("vocabularyId" -> value.vocabularyId, "termId" -> value.termId) }
  private def _source_json(value: SourceAttribution): JsObject = { Json.obj("authorityId" -> value.authorityId, "path" -> value.path,
    "line" -> value.line.fold[JsValue](JsNull)(line => JsNumber(line))) }
  private def _label_json(value: LocalizedLabel): JsObject = { Json.obj("text" -> value.text, "language" -> value.language.fold[JsValue](JsNull)(JsString)) }
  private def _reference_json(value: SemanticReference): JsObject = {
    val target = value.target match {
      case ModelTarget(id) => Json.obj("kind" -> "model-element", "identity" -> _model_json(id))
      case TermTarget(id) => Json.obj("kind" -> "term", "identity" -> _term_id_json(id))
    }
    val origin = value.origin match {
      case Declared => Json.obj("kind" -> "declared")
      case Derived(ruleid, sources) => Json.obj("kind" -> "derived", "ruleId" -> ruleid, "sources" -> JsArray(sources.map(_source_json)))
    }
    Json.obj("target" -> target, "boundary" -> (if (value.boundary == Local) "local" else "external"),
      "relation" -> _presence_json(value.relation, (id: RelationId) => Json.obj("vocabularyId" -> id.vocabularyId, "relationId" -> id.relationId)),
      "profile" -> _presence_json(value.profile, (id: ProfileId) => Json.obj("vocabularyId" -> id.vocabularyId, "profileId" -> id.profileId)),
      "context" -> _presence_json(value.context, JsString), "preferredLabel" -> _presence_json(value.preferredLabel, _label_json),
      "source" -> _source_json(value.source), "origin" -> origin)
  }
  private def _references_json(value: Vector[SemanticReference]): JsArray = { JsArray(value.map(_reference_json)) }
  private def _element_json(value: ElementRecord): JsObject = { Json.obj("identity" -> _presence_json(value.identity, _model_json),
    "kind" -> value.kind, "name" -> value.name, "source" -> _source_json(value.source), "references" -> _presence_json(value.references, _references_json)) }
  private def _term_json(value: TermRecord): JsObject = { Json.obj("identity" -> _term_id_json(value.identity), "source" -> _source_json(value.source)) }
  private def _projection_json(value: ElementProjection): JsObject = { Json.obj("element" -> _element_json(value.element),
    "termReferences" -> _presence_json(value.termReferences, _references_json), "conceptualGroups" -> _presence_json(value.conceptualGroups, _references_json)) }
  private def _term_projection_json(value: TermProjection): JsObject = { Json.obj("term" -> _term_json(value.term),
    "termType" -> _presence_json(value.termType, JsString), "bokKind" -> _presence_json(value.bokKind, (kind: BokKind) => JsString(kind match { case Mono => "mono"; case Koto => "koto" })),
    "labels" -> _presence_json(value.labels, (labels: Vector[LocalizedLabel]) => JsArray(labels.map(_label_json))), "preferredLabel" -> _presence_json(value.preferredLabel, _label_json)) }
  private def _keys(value: JsValue, reverse: Boolean): JsValue = { value match {
    case objectvalue: JsObject =>
      val fields = objectvalue.fields.toVector.sortBy(_._1)
      JsObject((if (reverse) fields.reverse else fields).map { case (key, item) => key -> _keys(item, reverse) })
    case JsArray(items) => JsArray(items.map(_keys(_, reverse)))
    case other => other
  } }
  private def _change(value: JsValue, path: Vector[String], update: JsValue => JsValue): JsValue = {
    if (path.isEmpty) { update(value) } else { value match {
      case objectvalue: JsObject => objectvalue + (path.head -> _change(objectvalue.value(path.head), path.tail, update))
      case JsArray(items) => JsArray(items.updated(path.head.toInt, _change(items(path.head.toInt), path.tail, update)))
      case _ => throw new IllegalArgumentException("The test-owned mutation path is invalid.")
    } }
  }
  private def _extensions(value: JsValue): JsObject = { (value \ "extensions").as[JsObject] }
  private def _right[A, B](value: Either[A, B]): B = { value.right.get }
  private def _filename(absent: Boolean): String = { "terminology-metadata-v1-" + (if (absent) "absence" else "declared") + ".json" }
  private def _resource(name: String): JsObject = {
    val source = Source.fromInputStream(getClass.getResourceAsStream("/cozy/modeler/" + name), "UTF-8")
    try { Json.parse(source.mkString).as[JsObject] } finally { source.close() }
  }
  private def _prefix(): Prefix = {
    val raw = _resource("use-case-metadata-v1-declared.json")
    Prefix(raw, _right(CmlStructureMetadata.read(raw)), _right(CmlClassificationMetadata.read(raw)),
      _right(CmlDynamicMetadata.read(raw)), _right(CmlUseCaseMetadata.read(raw)))
  }
  private def _diagnostic(kind: TerminologyDiagnosticKind, path: String, detail: String): TerminologyDiagnostic = { TerminologyDiagnostic(kind, path, detail, None) }
  private def _publication(kind: CmlSemanticMetadata.PublicationDiagnosticKind, path: String, detail: String): TerminologyDiagnostic = {
    TerminologyDiagnostic(PublicationFailure, path, "The foundation metadata publication is invalid.",
      Some(CmlSemanticMetadata.PublicationDiagnostic(kind, path, detail, None)))
  }
  private def _unknown_element(path: String): TerminologyDiagnostic = { _diagnostic(UnknownElement, path, "No admitted Terminology element has the requested qualified identity.") }
  private def _unknown_term(path: String): TerminologyDiagnostic = { _diagnostic(UnknownTerm, path, "No admitted Terminology Term has the requested qualified identity.") }

  private val _shape_mutations: Vector[Mutation] = {
    val child = "extensions.cozy.cml.terminology"
    val element = Vector("elements", "49")
    val term = Vector("terms", "4")
    val reference = element ++ Vector("termReferences", "value", "0")
    Vector(
      Mutation(Vector.empty, value => value.as[JsObject] - "schemaVersion", "schemaVersion"),
      Mutation(Vector.empty, value => value.as[JsObject] - "elements", "elements"),
      Mutation(Vector.empty, value => value.as[JsObject] - "terms", "terms"),
      Mutation(Vector.empty, value => value.as[JsObject] + ("hostile" -> JsNull), child),
      Mutation(Vector("schemaVersion"), _ => JsNull, "schemaVersion"),
      Mutation(Vector("schemaVersion"), _ => JsNumber(1), "schemaVersion"),
      Mutation(Vector.empty, _ => Json.obj("schemaVersion" -> "cozy.cml.terminology.V1", "elements" -> JsNull, "hostile" -> JsNull),
        "schemaVersion", UnsupportedSchemaVersion, "The Terminology schema version is not supported."),
      Mutation(Vector("elements"), _ => JsNull, "elements"), Mutation(Vector("terms"), _ => JsString("wrong"), "terms"),
      Mutation(element, _ => JsNull, "elements[49]"), Mutation(term, _ => JsBoolean(false), "terms[4]"),
      Mutation(element, value => value.as[JsObject] - "conceptualGroups", "elements[49].conceptualGroups"),
      Mutation(term, value => value.as[JsObject] + ("extra" -> JsNull), "terms[4]"),
      Mutation(element :+ "termReferences", _ => JsNull, "elements[49].termReferences"),
      Mutation(element :+ "termReferences", _ => Json.obj("value" -> Json.arr()), "elements[49].termReferences.status"),
      Mutation(element ++ Vector("termReferences", "status"), _ => JsString("Present"), "elements[49].termReferences.status"),
      Mutation(element :+ "termReferences", _ => Json.obj("status" -> "present"), "elements[49].termReferences.value"),
      Mutation(element ++ Vector("termReferences", "value"), _ => JsNull, "elements[49].termReferences"),
      Mutation(element :+ "termReferences", value => value.as[JsObject] + ("reason" -> JsString("unsupported")), "elements[49].termReferences"),
      Mutation(element ++ Vector("termReferences", "reason"), _ => JsString("NotDeclared"), "elements[49].termReferences.reason", absent = true),
      Mutation(element ++ Vector("termReferences", "detail"), _ => JsNull, "elements[49].termReferences.detail", absent = true),
      Mutation(element :+ "conceptualGroups", _ => Json.obj("status" -> "absent", "reason" -> "unsupported"), "elements[49].conceptualGroups.detail"),
      Mutation(term ++ Vector("termType", "value"), _ => JsNumber(1), "terms[4].termType"),
      Mutation(term ++ Vector("bokKind", "value"), _ => JsString("Koto"), "terms[4].bokKind"),
      Mutation(term ++ Vector("bokKind", "value"), _ => JsNull, "terms[4].bokKind"),
      Mutation(term ++ Vector("labels", "value"), _ => JsNull, "terms[4].labels"),
      Mutation(term ++ Vector("labels", "value", "0"), value => value.as[JsObject] - "language", "terms[4].labels[0].language"),
      Mutation(term ++ Vector("labels", "value", "0", "language"), _ => JsNumber(1), "terms[4].labels[0].language"),
      Mutation(term ++ Vector("labels", "value", "0"), value => value.as[JsObject] + ("extra" -> JsNull), "terms[4].labels[0]"),
      Mutation(term :+ "labels", value => value.as[JsObject] + ("detail" -> JsString("Mixed branch.")), "terms[4].labels"),
      Mutation(term ++ Vector("preferredLabel", "value"), _ => JsNull, "terms[4].preferredLabel"),
      Mutation(element ++ Vector("element", "source", "line"), _ => JsNumber(99), "elements[49].element", InvalidCoreBinding, _embedded_detail),
      Mutation(term ++ Vector("term", "source", "line"), _ => JsNull, "terms[4].term", InvalidCoreBinding, _embedded_detail)) ++
      Vector(Vector("target", "identity", "vocabularyId") -> JsString("forged"), Vector("boundary") -> JsString("external"),
        Vector("relation", "value", "relationId") -> JsString("forged"), Vector("profile", "value", "profileId") -> JsString("forged"),
        Vector("context", "value") -> JsString("forged"), Vector("preferredLabel", "value", "language") -> JsString("en"),
        Vector("source", "line") -> JsNumber(99), Vector("origin", "ruleId") -> JsString("forged"),
        Vector("origin", "sources", "0", "line") -> JsNumber(99)).map { case (path, value) =>
          Mutation(reference ++ path, _ => value, "elements[49].termReferences[0]", InvalidCoreBinding, _reference_detail)
        }
  }
  private def _failure_cases(supplied: Input): Vector[Failure] = {
    val record = supplied.records.head
    val projection = supplied.elements.head
    val forged = Vector(record.copy(identity = Present(ModelElementId("terminology.a", "forged"))), record.copy(kind = "forged"), record.copy(name = "forged"),
      record.copy(source = record.source.copy(line = Some(99))), record.copy(references = if (record.references == _link_gaps.head) Present(Vector.empty) else _link_gaps.head))
    val corecases = forged.map(value => Failure(supplied.copy(elements = supplied.elements.updated(0, projection.copy(element = value))),
      Vector(_diagnostic(InvalidCoreBinding, "elements[49].element", _binding_detail), _diagnostic(MissingProjection, "elements", _missing_detail)),
      Vector(_diagnostic(InvalidCoreBinding, "elements[49].element", _embedded_detail))))
    val term = supplied.terms.head
    val termcases = Vector(term.copy(identity = TermId("terminology.a", "forged")), term.copy(source = term.source.copy(line = Some(99)))).map(value =>
      Failure(supplied.copy(projections = supplied.projections.updated(0, supplied.projections.head.copy(term = value))),
        Vector(_diagnostic(InvalidCoreBinding, "terms[4].term", _binding_detail), _diagnostic(MissingProjection, "terms", _missing_detail)),
        Vector(_diagnostic(InvalidCoreBinding, "terms[4].term", _embedded_detail))))
    val occurrences = Vector((supplied.copy(elements = supplied.elements.tail), "elements", MissingProjection, _missing_detail),
      (supplied.copy(elements = supplied.elements :+ supplied.elements.head), "elements[55].element", DuplicateProjection, _duplicate_detail),
      (supplied.copy(elements = supplied.elements.dropRight(1)), "elements", MissingProjection, _missing_detail),
      (supplied.copy(elements = supplied.elements :+ supplied.elements.last), "elements[55].element", DuplicateProjection, _duplicate_detail),
      (supplied.copy(projections = supplied.projections.tail), "terms", MissingProjection, _missing_detail),
      (supplied.copy(projections = supplied.projections :+ supplied.projections.head), "terms[7].term", DuplicateProjection, _duplicate_detail)).map { case (changed, path, kind, detail) =>
        val errors = Vector(_diagnostic(kind, path, detail))
        Failure(changed, errors, errors)
      }
    val filters: Vector[Presence[Vector[SemanticReference]]] = projection.termReferences match {
      case Present(refs) => Vector(Present(refs.drop(1)), Present(refs.reverse), Present(refs.updated(0, _rae)))
      case _: Absent => Vector(Present(Vector.empty))
    }
    val contradictions = filters.map(value => (projection.copy(termReferences = value), "termReferences")) ++
      (if (projection.element.references.isInstanceOf[Present[_]]) Vector(
        projection.copy(conceptualGroups = Present(Vector(_ra, _ra, _ra))) -> "conceptualGroups") else Vector.empty)
    val failures = contradictions.map { case (value, field) =>
      val changed = supplied.copy(elements = supplied.elements.updated(0, value))
      val error = Vector(_diagnostic(InvalidTerminologySemantics, "elements[49]." + field, _semantic_detail))
      Failure(changed, error, error)
    }
    val foreign = projection.copy(conceptualGroups = Present(Vector(_model_ref.copy(context = Present("Non-core group.")))))
    val changed = supplied.copy(elements = supplied.elements.updated(0, foreign))
    val replaced = projection.copy(termReferences = Present(Vector(_ra.copy(context = Present("Non-core filter.")))))
    corecases ++ termcases ++ occurrences ++ failures ++ Vector(Failure(changed,
      Vector(_diagnostic(InvalidTerminologySemantics, "elements[49].conceptualGroups", _semantic_detail)),
      Vector(_diagnostic(InvalidCoreBinding, "elements[49].conceptualGroups[0]", _reference_detail))),
      Failure(supplied.copy(elements = supplied.elements.updated(0, replaced)),
        Vector(_diagnostic(InvalidTerminologySemantics, "elements[49].termReferences", _semantic_detail)),
        Vector(_diagnostic(InvalidCoreBinding, "elements[49].termReferences[0]", _reference_detail))))
  }
  private def _permutation(size: Int): Gen[Vector[Int]] = {
    def _select_(remaining: Vector[Int]): Gen[Vector[Int]] = {
      if (remaining.isEmpty) { Gen.const(Vector.empty[Int]) } else {
        for { index <- Gen.choose(0, remaining.size - 1); rest <- _select_(remaining.patch(index, Vector.empty[Int], 1)) } yield remaining(index) +: rest
      }
    }
    _select_(Vector.tabulate(size)(identity))
  }
  private def _plan: Gen[Plan] = {
    for { first <- _permutation(4); second <- _permutation(2); third <- _permutation(2); fourth <- _permutation(0)
      fifth <- _permutation(2); sixth <- _permutation(2); records <- _permutation(55); terms <- _permutation(7)
      elements <- _permutation(55); projections <- _permutation(7) } yield Plan(Vector(first, second, third, fourth, fifth, sixth), records, terms, elements, projections)
  }
  private def _referred(original: Input, plan: Plan): Input = {
    val records = original.records.zipWithIndex.map { case (record, index) =>
      if (index < 49) { record } else { record.copy(references = record.references match {
        case Present(refs) => Present(if (refs.isEmpty) refs else plan.refs(index - 49).map(refs))
        case gap: Absent => gap
      }) }
    }
    val elements = original.elements.zipWithIndex.map { case (projection, index) =>
      projection.copy(element = records(index), termReferences = _term_filter(records(index).references))
    }
    original.copy(records = records, elements = elements)
  }
  private def _ordered(original: Input, plan: Plan): Input = {
    val referred = _referred(original, plan)
    referred.copy(records = plan.records.map(referred.records), terms = plan.terms.map(referred.terms),
      elements = plan.elements.map(referred.elements), projections = plan.projections.map(referred.projections))
  }
  private def _ordered_resource(raw: JsObject, plan: Plan): JsValue = {
    val referred = plan.refs.zipWithIndex.foldLeft[JsValue](raw) { case (value, (order, index)) =>
      val ordinal = (index + 49).toString
      val record = (value \ "elements").as[Vector[JsValue]].apply(index + 49).as[JsObject]
      val references = (record \ "references").as[JsObject]
      val changed = if ((references \ "status").as[String] == "present") {
        val values = (references \ "value").as[Vector[JsValue]]
        references + ("value" -> JsArray(if (values.isEmpty) values else order.map(values)))
      } else { references }
      val filtered = if ((changed \ "status").as[String] == "present") {
        changed + ("value" -> JsArray((changed \ "value").as[Vector[JsValue]].filter(item => (item \ "target" \ "kind").as[String] == "term")))
      } else { changed }
      val embedded = record + ("references" -> changed)
      val core = _change(value, Vector("elements", ordinal), _ => embedded)
      val element = _change(core, _child_path ++ Vector("elements", ordinal, "element"), _ => embedded)
      val projection = _change(element, _child_path ++ Vector("elements", ordinal, "termReferences"), _ => filtered)
      if (index < 2) { _change(projection, Vector("extensions", CmlStructureMetadata.namespace, "elements", (index + 3).toString, "element"), _ => embedded) }
      else { projection }
    }
    Vector(Vector("elements") -> plan.records, Vector("terms") -> plan.terms,
      (_child_path :+ "elements") -> plan.elements, (_child_path :+ "terms") -> plan.projections).foldLeft(referred) { case (value, (path, order)) =>
      _change(value, path, items => JsArray(order.map(items.as[Vector[JsValue]])))
    }
  }
}
