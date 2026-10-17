package cozy.modeler

import scala.collection.immutable.ListMap
import scala.io.Source
import org.scalacheck.{Gen, Prop, Test}
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import play.api.libs.json.{JsArray, JsNull, JsObject, JsString, JsValue, Json}
import CmlSemanticFoundation._
import CmlSemanticPublication._

/*
 * @since   Oct.  5, 2026
 * @version Oct. 13, 2026
 */
final class CmlSemanticPublicationSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  "Semantic publication admission" should {
    "complete typed suppliers" which {
      Vector("declared", "absence").foreach { mode =>
        s"SP-01-$mode retain every independently admitted carrier core Term and source" in {
          Given("an accepted full Causal resource filename and literal source and family-count expectations")
          val filename = "causal-metadata-v1-" + mode + ".json"
          val firstrecord = ElementRecord(Present(ModelElementId("structure.example", "entity:order")),
            "entity", "共有", SourceAttribution("structure.example", "src/main/cml/structure-handoff.cml", Some(1)), Absent(NotDeclared, "No semantic references are declared."))
          When("the actual UTF-8 publication is parsed and all accepted supplier readers independently supply expected facts")
          val raw = _resource(filename)
          val expected = _oracle(raw)
          When("the facade admits the same complete root through read and through an admitted base Envelope")
          val publication = _right(read(raw))
          val built = _right(CmlSemanticPublication.build(expected.envelope))
          val checks = _checks(publication, expected) ++ _checks(built, expected)
          val counts = _counts(publication)
          Then("all complete typed fields and envelopes agree with independent suppliers and literal occurrence counts and source")
          checks should contain only true
          publication.catalog.elements.size shouldBe 75
          publication.catalog.terms.size shouldBe 7
          publication.catalog.elements.head shouldBe firstrecord
          publication.catalog.elements(73) shouldBe publication.catalog.elements(74)
          counts shouldBe Vector(Vector(7, 3), Vector(2, 2, 3, 2, 3), Vector(2, 5, 5, 1, 2, 1, 8),
            Vector(3, 3), Vector(75, 7), Vector(8, 3, 7, 7, 3, 75))
          (built.envelope eq expected.envelope) shouldBe true
          (built.catalog eq expected.envelope.catalog) shouldBe true
          schemaVersion shouldBe "cozy.cml.semantic-metadata.v1"
          contractVersion shouldBe "cozy.cml.semantic-strength.v1"
          registeredNamespaces shouldBe _namespaces
          if (mode == "absence") {
            val carrier = _values(_present(publication.causal).causalLinks)(2)
            carrier.source shouldBe Absent(NotApplicable, "The causal fact is not applicable.")
            carrier.target shouldBe Absent(NotDeclared, "No causal fact is declared.")
            carrier.rule shouldBe Absent(NotRepresented, "The causal fact is not represented.")
            carrier.affectedElements shouldBe Absent(Unsupported, "The causal fact is unsupported.")
          }
        }
      }
    }
    "optional availability" which {
      "SP-02 expose six exact missing-family values and retain an unknown cozy namespace with JSON null" in {
        Given("a literal empty Catalog and an opaque unregistered namespace whose apparent child version grants no capability")
        val opaque = Json.obj("schemaVersion" -> "cozy.cml.structure.v1", "nested" -> Json.arr(JsNull, "literal"))
        val extensions = Json.obj("cozy.cml.future" -> opaque)
        When("the accepted base factory publishes the empty Catalog and the facade admits and renders it")
        val envelope = _right(CmlSemanticMetadata.build(Catalog.empty, extensions))
        val publication = _right(CmlSemanticPublication.build(envelope))
        val output = toJson(publication)
        Then("all fixed typed absences are NotDeclared while the unknown namespaced object and its null remain literal")
        _availability(publication) shouldBe _missing.map(Some(_))
        publication.catalog.elements shouldBe Vector.empty
        publication.catalog.terms shouldBe Vector.empty
        output shouldBe Json.obj("schemaVersion" -> "cozy.cml.semantic-metadata.v1",
          "elements" -> Json.arr(), "terms" -> Json.arr(), "extensions" -> extensions)
      }
      "SP-04-A admit each independently valid empty family and the complete empty-family combination" in {
        Given("a zero-record Catalog and literal expectations for each supplied empty family and absent peers")
        val emptycounts = Vector(Vector(0, 0), Vector(0, 0, 0, 0, 0), Vector(0, 0, 0, 0, 0, 0, 0),
          Vector(0, 0), Vector(0, 0), Vector(0, 0, 0, 0, 0, 0))
        When("unchanged zero-carrier builders independently publish six single families and a consistent complete combination")
        val inputs = _empty_inputs()
        When("the facade reads every empty publication and the complete input supplies independent expected full graphs")
        val publications = inputs.map(raw => _right(read(raw)))
        val complete = _oracle(inputs.last)
        val checks = _checks(publications.last, complete)
        val counts = publications.map(_counts)
        val availability = publications.map(_availability)
        When("the admitted empty Causal graph supplies its unsupported gap and an unknown qualified query")
        val causal = _present(publications(5).causal)
        val traversal = _right(CmlCausalTraversal.build(causal))
        val unknown = traversal.node(ModelElementId("empty.example", "missing"))
        Then("a valid empty Graph remains available and differs from fixed missing-family absences and an unknown query")
        checks should contain only true
        counts.take(6).zipWithIndex.foreach { case (actual, index) =>
          actual shouldBe Vector.tabulate(6)(family => if (family == index) emptycounts(family) else Vector.empty[Int])
          availability(index) shouldBe Vector.tabulate(6)(family =>
            if (family == index) None else Some(_missing(family)))
        }
        counts.last shouldBe emptycounts
        availability.last shouldBe Vector.fill(6)(None)
        causal.causalLinks shouldBe Absent(NotDeclared, "No admitted Dynamic metadata is supplied.")
        causal.capabilityGaps.last shouldBe CmlCausalMetadata.CapabilityGap(None, "causalInference",
          Absent(Unsupported, "No causation is inferred from names, membership, generic relations or expression text."))
        unknown shouldBe Left(CmlCausalMetadata.CausalDiagnostic(CmlCausalMetadata.UnknownElement,
          "node.identity", "The Causal element identity is unknown or unaddressable."))
      }
      "SP-04-B retain valid populated peers without Causal and reject a supplied Causal summary after Dynamic removal" in {
        Given("the literal Causal and Dynamic namespace removal coordinates for a complete accepted publication")
        val causalnamespace = "cozy.cml.causal"
        val dynamicnamespace = "cozy.cml.dynamic"
        When("the full declared resource is parsed and the independent supplier graphs are read")
        val raw = _resource("causal-metadata-v1-declared.json")
        val withoutcausal = raw + ("extensions" -> (_extensions(raw) - causalnamespace))
        val withoutdynamic = raw + ("extensions" -> (_extensions(raw) - dynamicnamespace))
        val expected = _oracle(raw).copy(envelope = _right(CmlSemanticMetadata.read(withoutcausal)))
        When("the facade admits missing Causal while the accepted Causal reader and facade independently reject missing Dynamic")
        val publication = _right(read(withoutcausal))
        val peerchecks = _peer_checks(publication, expected)
        val supplierfailure = CmlCausalMetadata.read(withoutdynamic)
        val failure = read(withoutdynamic)
        Then("missing Causal is explicit without losing populated peer facts and stale supplied Causal never becomes unavailable")
        peerchecks should contain only true
        publication.causal shouldBe _missing(5)
        supplierfailure.isLeft shouldBe true
        failure shouldBe Left(Vector(_supplier(5)))
      }
    }
    "strict deterministic supplier admission" which {
      "SP-03 reject each unknown registered child version and select the first invalid family in fixed order" in {
        Given("the six literal registered namespace paths and fixed safe diagnostics")
        val expected = _labels.indices.map(index => Left(Vector(_supplier(index)))).toVector
        When("the actual complete declared resource is parsed and each known child version is independently mutated")
        val raw = _resource("causal-metadata-v1-declared.json")
        val mutations = _namespaces.map(namespace => _change(raw, Vector("extensions", namespace, "schemaVersion"),
          JsString("hostile-unknown-version")))
        val multiple = _namespaces.foldLeft(raw)((root, namespace) =>
          _change(root, Vector("extensions", namespace, "schemaVersion"), JsString("hostile-unknown-version")))
        When("each mutated publication reaches both read and build and multiple invalid siblings reach read together")
        val observed = mutations.map(read)
        val built = mutations.map(root => CmlSemanticPublication.build(_right(CmlSemanticMetadata.read(root))))
        val first = read(multiple)
        Then("all six exact supplier diagnostics are independent of payload and authored extension order")
        observed shouldBe expected
        built shouldBe expected
        first shouldBe Left(Vector(_supplier(0)))
      }
      "SP-05 reject malformed bases before peers and reject full embedded-record and owned summary mismatches" in {
        Given("literal root version missing-field embedded Structure record and Causal-summary mutation paths")
        val safe = Left(Vector(_publication_failure))
        When("actual full input supplies a malformed base an unknown base version and two independently inconsistent embedded records")
        val raw = _resource("causal-metadata-v1-declared.json")
        val invalidbases = Vector(raw - "schemaVersion", raw - "elements",
          raw + ("schemaVersion" -> JsString("hostile-base-version")), JsString("hostile-root"))
        val structure = _change(raw, Vector("extensions", "cozy.cml.structure", "elements", "0", "element", "name"),
          JsString("hostile-full-record-change"))
        val summary = _change(raw, Vector("extensions", "cozy.cml.causal", "modelReferences", "0", "element", "name"),
          JsString("hostile-owned-summary-change"))
        val invalidboth = structure - "terms"
        When("the facade reads malformed roots and whole-record mismatches without an identity-only shortcut")
        val bases = invalidbases.map(read)
        val familyfailures = Vector(read(structure), read(summary))
        val precedence = read(invalidboth)
        Then("root failures take precedence and exact registered-family failures reveal no hostile input")
        bases shouldBe Vector.fill(4)(safe)
        precedence shouldBe safe
        familyfailures shouldBe Vector(Left(Vector(_supplier(0))), Left(Vector(_supplier(5))))
      }
    }
    "safe untrusted boundary" which {
      "SP-06 distinguish Java null failures from admitted JSON null and enforce the rendering precondition" in {
        Given("literal nested Java-null values null object keys and a valid JSON-null opaque object")
        val nullarray = JsObject(Map("items" -> JsArray(Vector(null: JsValue))))
        val nullkey = JsObject(Map((null: String) -> JsString("hostile-key")))
        val validopaque = Json.obj("items" -> Json.arr(JsNull), "member" -> JsNull)
        When("the actual declared input is parsed and accepted base factories supply boundary envelopes")
        val raw = _resource("causal-metadata-v1-declared.json")
        val unsafe = Vector(nullarray, nullkey).map(child =>
          raw + ("extensions" -> (_extensions(raw) + ("org.example.unsafe" -> child))))
        val unsafeenvelope = _right(CmlSemanticMetadata.build(Catalog.empty, Json.obj("org.example.unsafe" -> nullarray)))
        val validenvelope = _right(CmlSemanticMetadata.build(Catalog.empty, Json.obj("org.example.opaque" -> validopaque)))
        When("untrusted read and build capture Java-null failures while valid JSON null reaches admission and rendering")
        val failures = Vector(read(null)) ++ unsafe.map(read) ++ Vector(CmlSemanticPublication.build(unsafeenvelope))
        val nullenvelope = CmlSemanticPublication.build(null)
        val valid = _right(CmlSemanticPublication.build(validenvelope))
        val output = toJson(valid)
        val nulljson = intercept[IllegalArgumentException](toJson(null))
        val nullcanonical = intercept[IllegalArgumentException](canonicalJson(null))
        Then("fixed diagnostics contain no input and required JSON-null values remain literal without becoming Java-null success")
        failures shouldBe Vector.fill(4)(Left(Vector(_publication_failure)))
        nullenvelope shouldBe Left(Vector(PublicationDiagnostic(InvalidShape, "envelope",
          "An admitted semantic metadata envelope is required.")))
        (output \ "extensions" \ "org.example.opaque").as[JsValue] shouldBe validopaque
        nulljson.getMessage shouldBe "requirement failed: An admitted semantic publication is required."
        nullcanonical.getMessage shouldBe nulljson.getMessage
      }
    }
  }

  "Semantic publication rendering" should {
    "complete canonical retention" which {
      "SP-07 preserve full authored JSON and independently admitted graph facts through canonical reconsumption" in {
        Given("the declared and absence full-resource filenames with authored array order duplicates and required nulls")
        val filenames = Vector("causal-metadata-v1-declared.json", "causal-metadata-v1-absence.json")
        When("both actual resources supply independent base and six-family expected facts")
        val resources = filenames.map(_resource)
        val expected = resources.map(_oracle)
        val expectedcanonical = expected.map(value => CmlSemanticMetadata.canonicalJson(value.envelope))
        When("the facade reads renders canonicalizes parses and reconsumes every complete publication")
        val publications = resources.map(raw => _right(read(raw)))
        val outputs = publications.map(toJson)
        val canonical = publications.map(canonicalJson)
        val parsed = canonical.map(value => Json.parse(value).as[JsObject])
        val reread = parsed.map(value => _right(read(value)))
        val checks = reread.zip(expected).flatMap { case (publication, oracle) => _checks(publication, oracle) }
        Then("full core Terms opaque peers all raw carriers gaps exact strings and typed facts survive unchanged")
        outputs shouldBe resources
        canonical shouldBe expectedcanonical
        parsed shouldBe resources
        checks should contain only true
      }
    }
    "active recursive object-key invariance" which {
      "SP-08 retain both complete modes under seed 42 with at least one hundred successful samples and no discards" in {
        Given("seed 42 a no-discard integer generator and literal mandatory coverage categories")
        val generator = Gen.choose(1, Int.MaxValue)
        val tags = Vector("declared", "absence", "reconstructed-objects", "literal-arrays-and-nulls")
        When("both actual resources are parsed and independently admitted expected graphs are captured before facade actions")
        val resources = Vector(_resource("causal-metadata-v1-declared.json"), _resource("causal-metadata-v1-absence.json"))
        val expected = resources.map(_oracle)
        val counts = scala.collection.mutable.Map(tags.map(_ -> 0): _*)
        val property = Prop.forAll(generator) { salt =>
          Given("a recursive object-key reconstruction salt with the original authored arrays and JSON nulls")
          val reconstructed = resources.map(raw => _reconstruct(raw, salt).as[JsObject])
          When("the facade admits every reconstructed root and its canonical output is parsed and independently re-admitted")
          val publications = reconstructed.map(raw => _right(read(raw)))
          val outputs = publications.map(toJson)
          val canonicalroots = publications.map(value => Json.parse(canonicalJson(value)).as[JsObject])
          val reread = canonicalroots.map(raw => _right(read(raw)))
          val checks = publications.zip(expected).flatMap { case (publication, oracle) => _checks(publication, oracle) } ++
            reread.zip(expected).flatMap { case (publication, oracle) => _checks(publication, oracle) }
          val observed = Vector("declared", "absence") ++ Vector(
            "reconstructed-objects" -> reconstructed.zip(resources).exists { case (after, before) =>
              after.fields.map(_._1) != before.fields.map(_._1)
            },
            "literal-arrays-and-nulls" -> (outputs == resources && canonicalroots == resources)
          ).collect { case (tag, true) => tag }
          observed.foreach(tag => counts.update(tag, counts(tag) + 1))
          Then("independent full typed facts and whole original JSON including arrays duplicates and nulls remain literal")
          checks should contain only true
          reconstructed shouldBe resources
          outputs shouldBe resources
          canonicalroots shouldBe resources
          Prop.collect(observed.sorted.mkString(","))(Prop(checks.forall(identity) && outputs == resources))
        }
        When("ScalaCheck evaluates the active fixed-seed property and captures its actual result and observed coverage")
        val result = Test.check(Test.Parameters.default.withMinSuccessfulTests(100).withWorkers(1)
          .withInitialSeed(org.scalacheck.rng.Seed(42L)), property)
        val coverage = counts.toVector.sortBy(_._1)
        Then("the actual result is passed with at least one hundred successes zero discards and positive coverage in every category")
        info(s"SP-08 succeeded=${result.succeeded} discarded=${result.discarded} passed=${result.passed}")
        info("SP-08 coverage=" + coverage.map { case (tag, count) => s"$tag:$count" }.mkString(","))
        result.passed shouldBe true
        result.succeeded should be >= 100
        result.discarded shouldBe 0
        coverage.map(_._1) shouldBe tags.sorted
        coverage.map(_._2).foreach(count => count should be > 0)
      }
    }
  }

  private val _namespaces = Vector("cozy.cml.structure", "cozy.cml.classification", "cozy.cml.dynamic",
    "cozy.cml.use-case", "cozy.cml.terminology", "cozy.cml.causal")
  private val _labels = Vector("Structure", "Classification", "Dynamic", "Use Case", "Terminology", "Causal")
  private val _missing = Vector(
    Absent(NotDeclared, "No admitted Structure metadata is supplied."),
    Absent(NotDeclared, "No admitted Classification metadata is supplied."),
    Absent(NotDeclared, "No admitted Dynamic metadata is supplied."),
    Absent(NotDeclared, "No admitted Use Case metadata is supplied."),
    Absent(NotDeclared, "No admitted Terminology metadata is supplied."),
    Absent(NotDeclared, "No admitted Causal metadata is supplied.")
  )

  private final case class Oracle(
    envelope: CmlSemanticMetadata.Envelope,
    structure: CmlStructureMetadata.Graph,
    classification: CmlClassificationMetadata.Graph,
    dynamic: CmlDynamicMetadata.Graph,
    usecase: CmlUseCaseMetadata.Graph,
    terminology: CmlTerminologyMetadata.Graph,
    causal: CmlCausalMetadata.Graph
  )

  private def _oracle(raw: JsObject): Oracle = Oracle(
    _right(CmlSemanticMetadata.read(raw)), _right(CmlStructureMetadata.read(raw)),
    _right(CmlClassificationMetadata.read(raw)), _right(CmlDynamicMetadata.read(raw)),
    _right(CmlUseCaseMetadata.read(raw)), _right(CmlTerminologyMetadata.read(raw)),
    _right(CmlCausalMetadata.read(raw)))

  private def _checks(publication: Publication, expected: Oracle): Vector[Boolean] = {
    val causal = _present(publication.causal)
    _peer_checks(publication, expected) ++ Vector(
      causal.causalLinks == expected.causal.causalLinks,
      causal.actorOperations == expected.causal.actorOperations,
      causal.aggregateBoundaries == expected.causal.aggregateBoundaries,
      causal.ruleContexts == expected.causal.ruleContexts,
      causal.collaboratorContexts == expected.causal.collaboratorContexts,
      causal.modelReferences == expected.causal.modelReferences,
      causal.capabilityGaps == expected.causal.capabilityGaps
    ) ++ _envelope_checks(causal.envelope, expected.envelope) ++
      _envelope_checks(publication.envelope, expected.envelope)
  }

  private def _peer_checks(publication: Publication, expected: Oracle): Vector[Boolean] = {
    val structure = _present(publication.structure)
    val classification = _present(publication.classification)
    val dynamic = _present(publication.dynamic)
    val usecase = _present(publication.useCase)
    val terminology = _present(publication.terminology)
    Vector(
      structure.elements == expected.structure.elements, structure.relations == expected.structure.relations,
      classification.traits == expected.classification.traits, classification.powertypes == expected.classification.powertypes,
      classification.generalizations == expected.classification.generalizations,
      classification.traitRealizations == expected.classification.traitRealizations,
      classification.powertypeClassifications == expected.classification.powertypeClassifications,
      dynamic.workflows == expected.dynamic.workflows, dynamic.activities == expected.dynamic.activities,
      dynamic.flows == expected.dynamic.flows, dynamic.stateMachines == expected.dynamic.stateMachines,
      dynamic.states == expected.dynamic.states, dynamic.transitions == expected.dynamic.transitions,
      dynamic.causalLinks == expected.dynamic.causalLinks, usecase.actors == expected.usecase.actors,
      usecase.useCases == expected.usecase.useCases, terminology.elements == expected.terminology.elements,
      terminology.terms == expected.terminology.terms
    ) ++ Vector(structure.envelope, classification.envelope, dynamic.envelope, usecase.envelope, terminology.envelope)
      .flatMap(envelope => _envelope_checks(envelope, expected.envelope))
  }

  private def _envelope_checks(actual: CmlSemanticMetadata.Envelope, expected: CmlSemanticMetadata.Envelope): Vector[Boolean] =
    Vector(actual.catalog.elements == expected.catalog.elements, actual.catalog.terms == expected.catalog.terms,
      CmlSemanticMetadata.toJson(actual) == CmlSemanticMetadata.toJson(expected))

  private def _counts(publication: Publication): Vector[Vector[Int]] = Vector(
    _map_presence(publication.structure)(graph => Vector(graph.elements.size, graph.relations.size)),
    _map_presence(publication.classification)(graph => Vector(graph.traits.size, graph.powertypes.size,
      graph.generalizations.size, graph.traitRealizations.size, graph.powertypeClassifications.size)),
    _map_presence(publication.dynamic)(graph => Vector(graph.workflows.size, graph.activities.size, graph.flows.size,
      graph.stateMachines.size, graph.states.size, graph.transitions.size, graph.causalLinks.size)),
    _map_presence(publication.useCase)(graph => Vector(graph.actors.size, graph.useCases.size)),
    _map_presence(publication.terminology)(graph => Vector(graph.elements.size, graph.terms.size)),
    _map_presence(publication.causal)(graph => Vector(_values(graph.causalLinks).size, _values(graph.actorOperations).size,
      _values(graph.aggregateBoundaries).size, _values(graph.ruleContexts).size, _values(graph.collaboratorContexts).size,
      graph.modelReferences.size)))

  private def _availability(publication: Publication): Vector[Option[Absent]] =
    Vector(publication.structure, publication.classification, publication.dynamic,
      publication.useCase, publication.terminology, publication.causal).map {
      case absence: Absent => Some(absence)
      case Present(_) => None
    }

  private def _empty_inputs(): Vector[JsObject] = {
    val catalog = Catalog.empty
    val extensions = Json.obj()
    val structure = _right(CmlStructureMetadata.build(catalog, Vector.empty, Vector.empty, extensions)).envelope
    val classification = _right(CmlClassificationMetadata.build(catalog, Vector.empty, Vector.empty,
      Vector.empty, Vector.empty, Vector.empty, extensions)).envelope
    val dynamic = _right(CmlDynamicMetadata.build(catalog, Vector.empty, Vector.empty, Vector.empty,
      Vector.empty, Vector.empty, Vector.empty, Vector.empty, extensions)).envelope
    val usecase = _right(CmlUseCaseMetadata.build(catalog, Vector.empty, Vector.empty, extensions)).envelope
    val terminology = _right(CmlTerminologyMetadata.build(catalog, Vector.empty, Vector.empty, extensions)).envelope
    val base = _right(CmlSemanticMetadata.build(catalog, extensions))
    val causal = _right(CmlCausalMetadata.build(base)).envelope
    val peers = Vector(structure, classification, dynamic, usecase, terminology)
    val combined = peers.foldLeft(extensions)((all, envelope) => all ++ envelope.extensions)
    val complete = _right(CmlCausalMetadata.build(_right(CmlSemanticMetadata.build(catalog, combined)))).envelope
    (peers :+ causal :+ complete).map(CmlSemanticMetadata.toJson)
  }

  private def _change(raw: JsObject, path: Vector[String], value: JsValue): JsObject = {
    def _changed_(current: JsValue, remaining: Vector[String]): JsValue =
      if (remaining.isEmpty) value
      else current match {
        case objectvalue: JsObject => objectvalue + (remaining.head -> _changed_(objectvalue.value(remaining.head), remaining.tail))
        case JsArray(values) => JsArray(values.updated(remaining.head.toInt, _changed_(values(remaining.head.toInt), remaining.tail)))
        case _ => throw new IllegalArgumentException("The literal mutation path must address a JSON container.")
      }
    _changed_(raw, path).as[JsObject]
  }

  private def _reconstruct(value: JsValue, salt: Int): JsValue = value match {
    case objectvalue: JsObject =>
      val fields = objectvalue.fields.toVector.sortBy(_._1).reverse
      val offset = if (fields.isEmpty) 0 else salt % fields.size
      val ordered = fields.drop(offset) ++ fields.take(offset)
      JsObject(ListMap(ordered.map { case (key, child) => key -> _reconstruct(child, salt) }: _*))
    case JsArray(values) => JsArray(values.map(child => _reconstruct(child, salt)))
    case scalar => scalar
  }

  private def _map_presence[A](value: Presence[A])(count: A => Vector[Int]): Vector[Int] = value match {
    case Present(graph) => count(graph)
    case _: Absent => Vector.empty
  }

  private def _values[A](value: Presence[Vector[A]]): Vector[A] = value match {
    case Present(values) => values
    case _: Absent => Vector.empty
  }

  private def _present[A](value: Presence[A]): A = value.asInstanceOf[Present[A]].value
  private def _right[A, B](value: Either[A, B]): B = value.right.get
  private def _extensions(value: JsObject): JsObject = (value \ "extensions").as[JsObject]
  private def _supplier(index: Int): PublicationDiagnostic = PublicationDiagnostic(InvalidSupplier,
    "extensions." + _namespaces(index), "The supplied " + _labels(index) + " metadata is not admitted.")
  private def _publication_failure: PublicationDiagnostic = PublicationDiagnostic(
    PublicationFailure, "publication", "The semantic metadata publication is invalid.")

  private def _resource(name: String): JsObject = {
    val source = Source.fromInputStream(getClass.getResourceAsStream("/cozy/modeler/" + name), "UTF-8")
    try Json.parse(source.mkString).as[JsObject] finally source.close()
  }
}
