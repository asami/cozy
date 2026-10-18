package cozy.modeler

import scala.collection.immutable.ListMap
import scala.io.Source
import org.scalacheck.{Gen, Prop, Test}
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import play.api.libs.json.{JsArray, JsNull, JsNumber, JsObject, JsString, JsValue, Json}
import CmlSemanticFoundation._
import CmlSemanticPublication.{Publication, PublicationDiagnostic, InvalidSupplier, PublicationFailure}

/*
 * @since   Oct.  5, 2026
 * @version Oct. 18, 2026
 */
final class CmlSemanticPublicationCompatibilitySpec extends AnyWordSpec with Matchers with GivenWhenThen {
  import CmlSemanticPublicationCompatibilitySpec._

  "Semantic publication compatibility" should {
    "unchanged complete publication and legacy separation" which {
      "PC-01 preserve both full wires and independently admitted suppliers through read build and canonical reconsumption" in {
        Given("the declared and absence UTF-8 filenames and the unchanged outer and six child versions")
        val filenames = Vector("causal-metadata-v1-declared.json", "causal-metadata-v1-absence.json")
        val versions = Vector("cozy.cml.structure.v1", "cozy.cml.classification.v1", "cozy.cml.dynamic.v1",
          "cozy.cml.use-case.v1", "cozy.cml.terminology.v1", "cozy.cml.causal.v1")
        When("actual input is parsed and the base and all six unchanged strict suppliers independently admit expected full facts")
        val roots = filenames.map(_resource)
        val expected = roots.map(_oracle)
        val canonicalexpected = roots.map(_canonical)
        When("the facade reads builds renders canonicalizes parses and reads the same complete inputs")
        val read = roots.map(root => _right(CmlSemanticPublication.read(root)))
        val built = expected.map(value => _right(CmlSemanticPublication.build(value.envelope)))
        val outputs = read.map(CmlSemanticPublication.toJson)
        val buildoutputs = built.map(CmlSemanticPublication.toJson)
        val canonical = read.map(CmlSemanticPublication.canonicalJson)
        val buildcanonical = built.map(CmlSemanticPublication.canonicalJson)
        val parsed = canonical.map(text => Json.parse(text).as[JsObject])
        val reread = parsed.map(root => _right(CmlSemanticPublication.read(root)))
        val checks = Vector(read, built, reread).flatMap(_.zip(expected).flatMap {
          case (publication, oracle) => _checks(publication, oracle)
        })
        Then("complete catalog Terms carrier fields gaps and every envelope retain the original wire without a legacy upgrade")
        outputs shouldBe roots
        buildoutputs shouldBe roots
        canonical shouldBe canonicalexpected
        buildcanonical shouldBe canonicalexpected
        parsed shouldBe roots
        checks should contain only true
        CmlSemanticPublication.schemaVersion shouldBe "cozy.cml.semantic-metadata.v1"
        CmlSemanticPublication.contractVersion shouldBe "cozy.cml.semantic-strength.v1"
        CmlSemanticPublication.registeredNamespaces shouldBe _namespaces
        roots.foreach { root =>
          root.keys shouldBe Set("schemaVersion", "elements", "terms", "extensions")
          root.keys should not contain "contractVersion"
          _namespaces.map(namespace => (_extensions(root) \ namespace \ "schemaVersion").as[String]) shouldBe versions
        }
        read.foreach { publication =>
          publication.catalog.elements.size shouldBe 75
          publication.catalog.terms.size shouldBe 7
        }
      }
    }
    "opaque unknown peer isolation" which {
      "PC-02 retain literal unknown objects without core capability or registration override" in {
        Given("both full filenames an independently empty core and the literal future peer with forged core apparent causal version arrays and nulls")
        val filenames = Vector("causal-metadata-v1-declared.json", "causal-metadata-v1-absence.json")
        val opaque = _opaque_literal
        val empty = _empty_wire(Vector.empty)
        val unknownelement = ModelElementId("cozy.cml.future", "forged-label-alias")
        val unknownterm = TermId("cozy.cml.future", "forged-label-alias")
        When("actual input and empty base supply independently admitted original and literal-extended expected families")
        val roots = filenames.map(_resource) :+ empty
        val original = roots.map(_oracle)
        val extended = roots.map(root => root + ("extensions" -> (_extensions(root) + ("cozy.cml.future" -> opaque))))
        val expected = extended.map(_oracle)
        val canonicalexpected = extended.map(_canonical)
        When("facade admission rendering canonical parsing re-admission and qualified lookup consume each extended publication")
        val publications = extended.map(root => _right(CmlSemanticPublication.read(root)))
        val built = expected.map(value => _right(CmlSemanticPublication.build(value.envelope)))
        val outputs = publications.map(CmlSemanticPublication.toJson)
        val canonical = publications.map(CmlSemanticPublication.canonicalJson)
        val parsed = canonical.map(text => Json.parse(text).as[JsObject])
        val reread = parsed.map(root => _right(CmlSemanticPublication.read(root)))
        val checks = Vector(publications, built, reread).flatMap(_.zip(expected).flatMap {
          case (publication, oracle) => _checks(publication, oracle)
        })
        val elementlookups = publications.map(_.catalog.element(unknownelement))
        val termlookups = publications.map(_.catalog.term(unknownterm))
        Then("the entire peer survives while all original typed carriers and core remain unchanged and empty availability stays absent")
        outputs shouldBe extended
        built.map(CmlSemanticPublication.toJson) shouldBe extended
        canonical shouldBe canonicalexpected
        parsed shouldBe extended
        checks should contain only true
        expected.zip(original).foreach { case (after, before) =>
          _carrier_checks(after, before) should contain only true
          after.envelope.catalog.elements shouldBe before.envelope.catalog.elements
          after.envelope.catalog.terms shouldBe before.envelope.catalog.terms
          after.envelope.extensions shouldBe before.envelope.extensions + ("cozy.cml.future" -> opaque)
        }
        _availability(publications.last) shouldBe _missing.map(Some(_))
        elementlookups shouldBe Vector.fill(3)(Left(_element_failure))
        termlookups shouldBe Vector.fill(3)(Left(_term_failure))
      }
    }
    "independently valid availability" which {
      "PC-03 distinguish eight empty publications missing suppliers explicit field gaps and unknown queries" in {
        Given("all missing six single-family and all-admitted literal empty wires with exact availability and causal gap details")
        val masks = Vector(Vector.empty[Int]) ++ (0 until 6).map(index => Vector(index)).toVector ++ Vector((0 until 6).toVector)
        val expectedroots = masks.map(_empty_wire)
        val availability = masks.map(mask => _missing.zipWithIndex.map {
          case (absence, index) => if (mask.contains(index)) None else Some(absence)
        })
        val absentfields = Vector(_missing(2), _missing(3), _missing(0), _missing(2), _missing(3))
        val gapfields = Vector("causalLinks", "actorOperations", "aggregateBoundaries", "ruleContexts", "collaboratorContexts")
        val unsupported = CmlCausalMetadata.CapabilityGap(None, "causalInference", Absent(Unsupported,
          "No causation is inferred from names, membership, generic relations or expression text."))
        val expectedgaps = gapfields.zip(absentfields).map { case (field, absence) =>
          CmlCausalMetadata.CapabilityGap(None, field, absence)
        } :+ unsupported
        When("literal wires independently admit expected complete typed facts and unchanged builders publish only their actual accepted extensions")
        val expected = expectedroots.map(_oracle)
        val inputs = _empty_inputs()
        When("facade read build render and canonical reconsumption admit each independently built publication")
        val publications = inputs.map(envelope => _right(CmlSemanticPublication.read(CmlSemanticMetadata.toJson(envelope))))
        val built = inputs.map(envelope => _right(CmlSemanticPublication.build(envelope)))
        val outputs = publications.map(CmlSemanticPublication.toJson)
        val canonical = publications.map(CmlSemanticPublication.canonicalJson)
        val parsed = canonical.map(text => Json.parse(text).as[JsObject])
        val reread = parsed.map(root => _right(CmlSemanticPublication.read(root)))
        val checks = Vector(publications, built, reread).flatMap(_.zip(expected).flatMap {
          case (publication, oracle) => _checks(publication, oracle)
        })
        val causalonly = _present(publications(6).causal)
        val complete = _present(publications(7).causal)
        val unknowns = Vector(causalonly, complete).map(graph =>
          _right(CmlCausalTraversal.build(graph)).node(ModelElementId("empty.example", "missing")))
        Then("all full empty typed fields and envelopes match literal wires and missing available-empty field-absent and unknown remain distinct")
        outputs shouldBe expectedroots
        built.map(CmlSemanticPublication.toJson) shouldBe expectedroots
        canonical shouldBe expectedroots.map(_canonical)
        parsed shouldBe expectedroots
        checks should contain only true
        publications.map(_availability) shouldBe availability
        causalonly.causalLinks shouldBe absentfields(0)
        causalonly.actorOperations shouldBe absentfields(1)
        causalonly.aggregateBoundaries shouldBe absentfields(2)
        causalonly.ruleContexts shouldBe absentfields(3)
        causalonly.collaboratorContexts shouldBe absentfields(4)
        causalonly.modelReferences shouldBe Vector.empty
        causalonly.capabilityGaps shouldBe expectedgaps
        complete.causalLinks shouldBe Present(Vector.empty)
        complete.actorOperations shouldBe Present(Vector.empty)
        complete.aggregateBoundaries shouldBe Present(Vector.empty)
        complete.ruleContexts shouldBe Present(Vector.empty)
        complete.collaboratorContexts shouldBe Present(Vector.empty)
        complete.modelReferences shouldBe Vector.empty
        complete.capabilityGaps shouldBe Vector(unsupported)
        unknowns shouldBe Vector.fill(2)(Left(_unknown_node))
      }
    }
    "independent strict peers and safe precedence" which {
      "PC-04 reject full embedded mismatches and every unknown child version without relying on the narrower Causal gate" in {
        Given("both resource filenames three full embedded mutation paths and six known-version paths with fixed safe diagnostics")
        val filenames = Vector("causal-metadata-v1-declared.json", "causal-metadata-v1-absence.json")
        val embedded = Vector(
          (1, Vector("extensions", "cozy.cml.classification", "traits", "0", "element", "name"), JsString("hostile-embedded-record")),
          (4, Vector("extensions", "cozy.cml.terminology", "elements", "0", "element", "name"), JsString("hostile-embedded-record")),
          (4, Vector("extensions", "cozy.cml.terminology", "terms", "0", "term", "source", "line"), JsNumber(BigDecimal(999))))
        val opaque = _opaque_literal
        val failures = embedded.map { case (index, _, _) => Left(Vector(_supplier(index))) }
        When("both actual roots independently admit original Causal facts and complete mutated base envelopes")
        val roots = filenames.map(_resource)
        val expectedcausal = roots.map(root => _right(CmlCausalMetadata.read(root)))
        val mutated = roots.map(root => embedded.map { case (_, path, value) => _change(root, path, value) })
        val bases = mutated.map(_.map(root => _right(CmlSemanticMetadata.read(root))))
        When("unchanged Causal and strict peer readers consume each whole embedded mutation before final facade read and build")
        val causal = mutated.map(_.map(CmlCausalMetadata.read))
        val strict = mutated.map(values => Vector(CmlClassificationMetadata.read(values(0)).isLeft,
          CmlTerminologyMetadata.read(values(1)).isLeft, CmlTerminologyMetadata.read(values(2)).isLeft))
        val reads = mutated.map(_.map(CmlSemanticPublication.read))
        val builds = bases.map(_.map(CmlSemanticPublication.build))
        val opaquevalid = roots.map(root => root + ("extensions" -> (_extensions(root) + ("cozy.cml.future" -> opaque))))
        val validopaque = opaquevalid.map(root => _right(CmlSemanticPublication.read(root)))
        val invalidopaque = mutated.map(_.map(root => root + ("extensions" -> (_extensions(root) + ("cozy.cml.future" -> opaque)))))
        val opaqueresults = invalidopaque.map(_.map(CmlSemanticPublication.read))
        val opaquebuilds = invalidopaque.map(_.map(root => CmlSemanticPublication.build(_right(CmlSemanticMetadata.read(root)))))
        val versions = roots.map(_version_mutations)
        val versionreads = versions.map(_.map(CmlSemanticPublication.read))
        val versionbuilds = versions.map(_.map(root => CmlSemanticPublication.build(_right(CmlSemanticMetadata.read(root)))))
        val multiple = roots.map(root => _namespaces.foldLeft(root)((value, namespace) =>
          _change(value, Vector("extensions", namespace, "schemaVersion"), JsString("hostile-unknown-version"))))
        val reordered = multiple.map(root => _reconstruct_root(root, 42))
        val first = (multiple ++ reordered).map(CmlSemanticPublication.read)
        val firstbuilds = (multiple ++ reordered).map(root => CmlSemanticPublication.build(_right(CmlSemanticMetadata.read(root))))
        val precedence = mutated.map(values => CmlSemanticPublication.read(values.head - "terms"))
        Then("Causal retains original complete facts and mutated envelopes while independent final admission reports exact safe failures")
        strict.foreach(_ shouldBe Vector(true, true, true))
        reads.foreach(_ shouldBe failures)
        builds.foreach(_ shouldBe failures)
        opaqueresults.foreach(_ shouldBe failures)
        opaquebuilds.foreach(_ shouldBe failures)
        validopaque.map(CmlSemanticPublication.toJson) shouldBe opaquevalid
        causal.zipWithIndex.foreach { case (results, mode) =>
          results.zipWithIndex.foreach { case (result, index) =>
            result.isRight shouldBe true
            val graph = _right(result)
            _causal_fields(graph) shouldBe _causal_fields(expectedcausal(mode))
            _envelope_checks(graph.envelope, bases(mode)(index)) should contain only true
            CmlCausalMetadata.toJson(graph) shouldBe mutated(mode)(index)
          }
        }
        versionreads.foreach(_ shouldBe (0 until 6).map(index => Left(Vector(_supplier(index)))).toVector)
        versionbuilds.foreach(_ shouldBe (0 until 6).map(index => Left(Vector(_supplier(index)))).toVector)
        first shouldBe Vector.fill(4)(Left(Vector(_supplier(0))))
        firstbuilds shouldBe first
        precedence shouldBe Vector.fill(2)(Left(Vector(_publication_failure)))
      }
    }
    "qualified identity provenance occurrence and stopping" which {
      "PC-05 retain full literal model Term reference anonymous records and resident External boundaries" in {
        Given("both complete filenames and independent full Q01 records references Term projection ordered derived sources and anonymous causal occurrences")
        val filenames = Vector("causal-metadata-v1-declared.json", "causal-metadata-v1-absence.json")
        val modela = ModelElementId("terminology.a", "shared")
        val modelb = ModelElementId("terminology.b", "shared")
        val terma = TermId("terminology.a", "shared")
        val sourcea = _terminology_source("terminology.a", 50)
        val sourceb = _terminology_source("terminology.b", 51)
        val local = _literal_reference(TermTarget(terma), Local, sourcea, "a-local", _literal_derived)
        val external = _literal_reference(TermTarget(terma), External, sourcea, "a-external", _literal_derived)
        val model = _literal_reference(ModelTarget(modelb), Local, sourcea, "a-model", Declared)
        val elementa = ElementRecord(Present(modela), "entity", "共有", sourcea, Present(Vector(local, local, external, model)))
        val elementb = ElementRecord(Present(modelb), "entity", "共有", sourceb, Present(Vector(
          _literal_reference(TermTarget(TermId("terminology.b", "shared")), Local, sourceb, "b-local", _literal_derived),
          _literal_reference(TermTarget(TermId("external.terminology", "shared")), External, sourceb, "b-foreign", _literal_derived))))
        val term = TermRecord(terma, SourceAttribution("terminology.a", "glossary/terminology.dox", Some(60)))
        val termprojection = CmlTerminologyMetadata.TermProjection(term, Present("event-type"), Present(CmlTerminologyMetadata.Koto),
          Present(Vector(LocalizedLabel("共有", Some("ja")), LocalizedLabel("Shared", Some("en")),
            LocalizedLabel("共有", Some("ja")), LocalizedLabel("無言語", None))), Present(LocalizedLabel("優先外", Some("ja"))))
        val elementprojection = CmlTerminologyMetadata.ElementProjection(elementa, Present(Vector(local, local, external)),
          Present(Vector(model, local, local)))
        val anonymoussource = _terminology_source("terminology.anonymous", 54)
        val anonymousreference = _literal_reference(TermTarget(terma), Local, anonymoussource, "anonymous-local", _literal_derived)
        val anonymouselement = ElementRecord(Absent(NotRepresented, "No stable Terminology element identity is represented."),
          "event", "共有", anonymoussource, Present(Vector(anonymousreference, anonymousreference)))
        val forward = Vector(local, local, external).map(reference => CmlTerminologyNavigation.TermLink(elementa, reference))
        val reverse = Vector.fill(2)(CmlTerminologyNavigation.TermLink(elementa, local)) ++
          Vector.fill(4)(CmlTerminologyNavigation.TermLink(anonymouselement, anonymousreference))
        val anonymouscausal = ElementRecord(Absent(NotRepresented, "No stable causal carrier identity is represented."),
          "causal-link", "共有", SourceAttribution("causal.example", "fixtures/causal-model.cml", Some(19)), Present(Vector.empty))
        val absentbuyer = ElementRecord(Absent(NotRepresented, "No stable causal Actor identity is represented."), "actor", "共有",
          SourceAttribution("causal.example", "fixtures/causal-model.cml", Some(1)), Absent(NotDeclared, "No causal fact is declared."))
        val wrongelements = Vector(ModelElementId("Terminology.a", "shared"), ModelElementId("shared", "共有"),
          ModelElementId("terminology.a", "共有"))
        val wrongterms = Vector(TermId("Terminology.a", "shared"), TermId("shared", "共有"), TermId("terminology.a", "共有"))
        When("actual UTF-8 inputs supply independent full expected base and supplier facts before publication or navigation")
        val roots = filenames.map(_resource)
        val expected = roots.map(_oracle)
        When("the facade catalog Terminology navigation and Causal traversal execute qualified component requests")
        val publications = roots.map(root => _right(CmlSemanticPublication.read(root)))
        val navigation = publications.map(publication => _right(CmlTerminologyNavigation.build(_present(publication.terminology))))
        val modelrecords = publications.map(publication => Vector(publication.catalog.element(modela), publication.catalog.element(modelb)))
        val termrecords = publications.map(_.catalog.term(terma))
        val elementprojections = navigation.map(_.element(modela))
        val termprojections = navigation.map(_.term(terma))
        val links = navigation.map(nav => _right(nav.links(modela)))
        val resolutions = navigation.zip(links).map { case (nav, value) => _present(value).map(nav.resolve) }
        val reverseobserved = navigation.map(_.linksForTerm(terma))
        val elementfailures = publications.map(publication => wrongelements.map(publication.catalog.element))
        val termfailures = publications.map(publication => wrongterms.map(publication.catalog.term))
        val traversal = _right(CmlCausalTraversal.build(_present(publications(1).causal)))
        val buyer = traversal.node(ModelElementId("causal.example", "actor:buyer"))
        val checks = publications.zip(expected).flatMap { case (publication, oracle) => _checks(publication, oracle) }
        val declaredanonymous = _present(publications.head.dynamic).causalLinks.filter(_.element == anonymouscausal)
        Then("full exact records retain separate domains authorities ordered sources duplicate links anonymous occurrences and External stopping")
        checks should contain only true
        modelrecords.foreach(_ shouldBe Vector(Right(elementa), Right(elementb)))
        termrecords shouldBe Vector.fill(2)(Right(term))
        elementprojections shouldBe Vector.fill(2)(Right(elementprojection))
        termprojections shouldBe Vector.fill(2)(Right(termprojection))
        links shouldBe Vector.fill(2)(Present(forward))
        resolutions.foreach(_ shouldBe Vector(Right(CmlTerminologyNavigation.LocalTarget(forward(0), termprojection)),
          Right(CmlTerminologyNavigation.LocalTarget(forward(1), termprojection)), Right(CmlTerminologyNavigation.ExternalTarget(forward(2)))))
        reverseobserved shouldBe Vector.fill(2)(Right(reverse))
        elementfailures.foreach(_ shouldBe Vector.fill(3)(Left(_element_failure)))
        termfailures.foreach(_ shouldBe Vector.fill(3)(Left(_term_failure)))
        publications.foreach { publication =>
          publication.catalog.elements.takeRight(2) shouldBe Vector(anonymouscausal, anonymouscausal)
        }
        declaredanonymous.map(_.kind) shouldBe Vector(CmlDynamicMetadata.Cause, CmlDynamicMetadata.Reaction)
        declaredanonymous(0) should not be declaredanonymous(1)
        publications(1).catalog.elements should contain (absentbuyer)
        buyer shouldBe Left(_unknown_node)
      }
    }
    "active canonical and adversarial reconstruction" which {
      "PC-06 execute both full opaque-extended modes and all six version adversaries in every seed 42 sample" taggedAs org.scalatest.tagobjects.Slow in {
        Given("both full filenames literal future peer seed 42 no-discard salts and six required observed coverage tags")
        val filenames = Vector("causal-metadata-v1-declared.json", "causal-metadata-v1-absence.json")
        val opaque = _opaque_literal
        val generator = Gen.choose(1, Int.MaxValue)
        val tags = Vector("declared", "absence", "recursive-object-reconstruction", "literal-arrays-and-nulls",
          "opaque-retention", "adversarial-all-six-families")
        val counts = scala.collection.mutable.Map(tags.map(_ -> 0): _*)
        val failures = (0 until 6).map(index => Left(Vector(_supplier(index)))).toVector
        When("actual roots are parsed extended literally and independently admitted by the base and six strict suppliers before facade actions")
        val original = filenames.map(_resource)
        val roots = original.map(root => root + ("extensions" -> (_extensions(root) + ("cozy.cml.future" -> opaque))))
        val expected = roots.map(_oracle)
        val canonicalexpected = roots.map(_canonical)
        val property = Prop.forAll(generator) { salt =>
          Given("a concrete positive reconstruction salt and both unchanged full modes plus the independent literal peer")
          val current = salt
          When("recursive construction changes root and nested object key order without changing arrays scalars duplicates or nulls")
          val reconstructed = roots.map(root => _reconstruct_root(root, current))
          When("facade read build render canonical parse re-read and all six known version adversaries execute on both reconstructed roots")
          val publications = reconstructed.map(root => _right(CmlSemanticPublication.read(root)))
          val built = expected.map(value => _right(CmlSemanticPublication.build(value.envelope)))
          val outputs = publications.map(CmlSemanticPublication.toJson)
          val canonical = publications.map(CmlSemanticPublication.canonicalJson)
          val parsed = canonical.map(text => Json.parse(text).as[JsObject])
          val reread = parsed.map(root => _right(CmlSemanticPublication.read(root)))
          val checks = Vector(publications, built, reread).flatMap(_.zip(expected).flatMap {
            case (publication, oracle) => _checks(publication, oracle)
          })
          val mutations = reconstructed.map(_version_mutations)
          val reads = mutations.map(_.map(CmlSemanticPublication.read))
          val builds = mutations.map(_.map(root => CmlSemanticPublication.build(_right(CmlSemanticMetadata.read(root)))))
          val changed = reconstructed.zip(roots).forall { case (after, before) => after.fields.map(_._1) != before.fields.map(_._1) }
          val retained = outputs == roots && parsed == roots && built.map(CmlSemanticPublication.toJson) == roots
          val opaqueretained = outputs.forall(root => (_extensions(root) \ "cozy.cml.future").as[JsValue] == opaque)
          val adversarial = reads.forall(_ == failures) && builds.forall(_ == failures)
          val observed = Vector("declared" -> (retained && checks.forall(identity)), "absence" -> (retained && checks.forall(identity)),
            "recursive-object-reconstruction" -> changed, "literal-arrays-and-nulls" -> retained,
            "opaque-retention" -> opaqueretained, "adversarial-all-six-families" -> adversarial).collect { case (tag, true) => tag }
          Then("whole independent raw canonical and typed expectations and exact safe adversarial diagnostics constrain every executed sample")
          reconstructed shouldBe roots
          outputs shouldBe roots
          parsed shouldBe roots
          canonical shouldBe canonicalexpected
          checks should contain only true
          changed shouldBe true
          reads.foreach(_ shouldBe failures)
          builds.foreach(_ shouldBe failures)
          observed.foreach(tag => counts.update(tag, counts(tag) + 1))
          Prop.collect(observed.sorted.mkString(","))(Prop(retained && checks.forall(identity) &&
            canonical == canonicalexpected && changed && opaqueretained && adversarial))
        }
        When("actual ScalaCheck Test.check evaluates the fixed seed property with one worker and captures result and observed counts")
        val result = Test.check(Test.Parameters.default.withMinSuccessfulTests(100).withWorkers(1)
          .withInitialSeed(org.scalacheck.rng.Seed(42L)), property)
        val coverage = counts.toVector.sortBy(_._1)
        Then("actual successes discards passed status and each positive executed category are reported and required")
        info(s"PC-06 succeeded=${result.succeeded} discarded=${result.discarded} passed=${result.passed}")
        info("PC-06 coverage=" + coverage.map { case (tag, count) => s"$tag:$count" }.mkString(","))
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
  private def _missing: Vector[Absent] = _labels.map(label => Absent(NotDeclared, "No admitted " + label + " metadata is supplied."))

  private def _oracle(raw: JsObject): Oracle = {
    val envelope = _right(CmlSemanticMetadata.read(raw))
    def _family_[A, B](index: Int, reader: JsValue => Either[A, B]): Presence[B] =
      if (_extensions(raw).keys.contains(_namespaces(index))) Present(_right(reader(raw))) else _missing(index)
    Oracle(envelope, _family_(0, CmlStructureMetadata.read), _family_(1, CmlClassificationMetadata.read),
      _family_(2, CmlDynamicMetadata.read), _family_(3, CmlUseCaseMetadata.read),
      _family_(4, CmlTerminologyMetadata.read), _family_(5, CmlCausalMetadata.read))
  }

  private def _checks(publication: Publication, expected: Oracle): Vector[Boolean] = {
    val actual = Oracle(publication.envelope, publication.structure, publication.classification, publication.dynamic,
      publication.useCase, publication.terminology, publication.causal)
    _carrier_checks(actual, expected) ++ _envelope_checks(publication.envelope, expected.envelope) ++
      _family_envelopes(actual).zip(_family_envelopes(expected)).flatMap { case (after, before) => _envelope_checks(after, before) }
  }

  private def _carrier_checks(actual: Oracle, expected: Oracle): Vector[Boolean] = Vector(
    _facts(actual.structure)(graph => (graph.elements, graph.relations)) ==
      _facts(expected.structure)(graph => (graph.elements, graph.relations)),
    _facts(actual.classification)(graph => (graph.traits, graph.powertypes, graph.generalizations,
      graph.traitRealizations, graph.powertypeClassifications)) ==
      _facts(expected.classification)(graph => (graph.traits, graph.powertypes, graph.generalizations,
        graph.traitRealizations, graph.powertypeClassifications)),
    _facts(actual.dynamic)(graph => (graph.workflows, graph.activities, graph.flows, graph.stateMachines,
      graph.states, graph.transitions, graph.causalLinks)) ==
      _facts(expected.dynamic)(graph => (graph.workflows, graph.activities, graph.flows, graph.stateMachines,
        graph.states, graph.transitions, graph.causalLinks)),
    _facts(actual.usecase)(graph => (graph.actors, graph.useCases)) == _facts(expected.usecase)(graph => (graph.actors, graph.useCases)),
    _facts(actual.terminology)(graph => (graph.elements, graph.terms)) == _facts(expected.terminology)(graph => (graph.elements, graph.terms)),
    _facts(actual.causal)(_causal_fields) == _facts(expected.causal)(_causal_fields))

  private def _facts[A, B](value: Presence[A])(fields: A => B): Presence[B] = value match {
    case Present(graph) => Present(fields(graph))
    case absence: Absent => absence
  }

  private def _family_envelopes(value: Oracle): Vector[CmlSemanticMetadata.Envelope] =
    _envelope(value.structure)(_.envelope) ++ _envelope(value.classification)(_.envelope) ++
      _envelope(value.dynamic)(_.envelope) ++ _envelope(value.usecase)(_.envelope) ++
      _envelope(value.terminology)(_.envelope) ++ _envelope(value.causal)(_.envelope)

  private def _envelope[A](value: Presence[A])(get: A => CmlSemanticMetadata.Envelope): Vector[CmlSemanticMetadata.Envelope] = value match {
    case Present(graph) => Vector(get(graph))
    case _: Absent => Vector.empty
  }

  private def _envelope_checks(actual: CmlSemanticMetadata.Envelope, expected: CmlSemanticMetadata.Envelope): Vector[Boolean] =
    Vector(actual.catalog.elements == expected.catalog.elements, actual.catalog.terms == expected.catalog.terms,
      actual.extensions == expected.extensions, CmlSemanticMetadata.toJson(actual) == CmlSemanticMetadata.toJson(expected))

  private type CausalFields = (Presence[Vector[CmlDynamicMetadata.CausalLinkProjection]],
    Presence[Vector[CmlCausalMetadata.ActorOperations]], Presence[Vector[CmlCausalMetadata.AggregateMembership]],
    Presence[Vector[CmlCausalMetadata.RuleContext]], Presence[Vector[CmlCausalMetadata.CollaboratorContext]],
    Vector[CmlCausalMetadata.ModelReferences], Vector[CmlCausalMetadata.CapabilityGap])

  private def _causal_fields(graph: CmlCausalMetadata.Graph): CausalFields =
    (graph.causalLinks, graph.actorOperations, graph.aggregateBoundaries, graph.ruleContexts,
      graph.collaboratorContexts, graph.modelReferences, graph.capabilityGaps)

  private def _availability(publication: Publication): Vector[Option[Absent]] =
    Vector(publication.structure, publication.classification, publication.dynamic, publication.useCase, publication.terminology, publication.causal).map {
      case absence: Absent => Some(absence)
      case Present(_) => None
    }

  private def _empty_inputs(): Vector[CmlSemanticMetadata.Envelope] = {
    val catalog = Catalog.empty
    val extensions = Json.obj()
    val base = _right(CmlSemanticMetadata.build(catalog, extensions))
    val structure = _right(CmlStructureMetadata.build(catalog, Vector.empty, Vector.empty, extensions)).envelope
    val classification = _right(CmlClassificationMetadata.build(catalog, Vector.empty, Vector.empty, Vector.empty, Vector.empty, Vector.empty, extensions)).envelope
    val dynamic = _right(CmlDynamicMetadata.build(catalog, Vector.empty, Vector.empty, Vector.empty, Vector.empty, Vector.empty, Vector.empty, Vector.empty, extensions)).envelope
    val usecase = _right(CmlUseCaseMetadata.build(catalog, Vector.empty, Vector.empty, extensions)).envelope
    val terminology = _right(CmlTerminologyMetadata.build(catalog, Vector.empty, Vector.empty, extensions)).envelope
    val causal = _right(CmlCausalMetadata.build(base)).envelope
    val peers = Vector(structure, classification, dynamic, usecase, terminology)
    val combined = peers.foldLeft(extensions)((all, envelope) => all ++ envelope.extensions)
    val complete = _right(CmlCausalMetadata.build(_right(CmlSemanticMetadata.build(catalog, combined)))).envelope
    Vector(base) ++ peers ++ Vector(causal, complete)
  }

  // Pure test-owned literal wire constructors, independent of every supplier builder.
  private def _empty_wire(mask: Vector[Int]): JsObject = {
    val children = Vector(
      Json.obj("schemaVersion" -> "cozy.cml.structure.v1", "elements" -> Json.arr(), "relations" -> Json.arr()),
      Json.obj("schemaVersion" -> "cozy.cml.classification.v1", "traits" -> Json.arr(), "powertypes" -> Json.arr(),
        "generalizations" -> Json.arr(), "traitRealizations" -> Json.arr(), "powertypeClassifications" -> Json.arr()),
      Json.obj("schemaVersion" -> "cozy.cml.dynamic.v1", "workflows" -> Json.arr(), "activities" -> Json.arr(), "flows" -> Json.arr(),
        "stateMachines" -> Json.arr(), "states" -> Json.arr(), "transitions" -> Json.arr(), "causalLinks" -> Json.arr()),
      Json.obj("schemaVersion" -> "cozy.cml.use-case.v1", "actors" -> Json.arr(), "useCases" -> Json.arr()),
      Json.obj("schemaVersion" -> "cozy.cml.terminology.v1", "elements" -> Json.arr(), "terms" -> Json.arr()),
      _empty_causal_child(mask.size == 6))
    Json.obj("schemaVersion" -> "cozy.cml.semantic-metadata.v1", "elements" -> Json.arr(), "terms" -> Json.arr(),
      "extensions" -> JsObject(mask.map(index => _namespaces(index) -> children(index))))
  }

  private def _empty_causal_child(complete: Boolean): JsObject = {
    val fields = Vector("causalLinks", "actorOperations", "aggregateBoundaries", "ruleContexts", "collaboratorContexts")
    val labels = Vector("Dynamic", "Use Case", "Structure", "Dynamic", "Use Case")
    val missing = labels.map(label => Json.obj("status" -> "absent", "reason" -> "not-declared",
      "detail" -> ("No admitted " + label + " metadata is supplied.")))
    val present = Json.obj("status" -> "present", "value" -> Json.arr())
    val gaps = if (complete) Vector.empty else fields.zip(missing).map { case (field, absence) =>
      Json.obj("owner" -> JsNull, "field" -> field, "absence" -> absence)
    }
    val unsupported = Json.obj("owner" -> JsNull, "field" -> "causalInference", "absence" -> Json.obj(
      "status" -> "absent", "reason" -> "unsupported",
      "detail" -> "No causation is inferred from names, membership, generic relations or expression text."))
    JsObject(fields.zipWithIndex.map { case (field, index) => field -> (if (complete) present else missing(index)) }) ++
      Json.obj("schemaVersion" -> "cozy.cml.causal.v1", "modelReferences" -> Json.arr(), "capabilityGaps" -> JsArray(gaps :+ unsupported))
  }

  private def _opaque_literal: JsObject = Json.obj("schemaVersion" -> "cozy.cml.causal.v1",
    "elements" -> Json.arr(Json.obj("identity" -> "forged-label-alias")),
    "nested" -> Json.obj("z" -> Json.arr(JsNull, Json.obj("schemaVersion" -> "hostile-child-version", "a" -> JsNull)),
      "a" -> Json.obj("capability" -> true, "name" -> "共有")), "terms" -> Json.arr(JsNull))

  private def _terminology_source(authority: String, line: Int): SourceAttribution =
    SourceAttribution(authority, "src/main/cml/terminology-handoff.cml", Some(line))

  private def _literal_derived: Derived = Derived("rule:literal", Vector(
    _terminology_source("input.a", 70), _terminology_source("input.b", 71)))

  private def _literal_reference(target: SemanticTarget, boundary: ReferenceBoundary, source: SourceAttribution,
    label: String, origin: ReferenceOrigin): SemanticReference = SemanticReference(target, boundary,
    Present(RelationId("relations.example", "synonym-candidate")), Present(ProfileId("profiles.example", "view:literal")),
    Present("Terminology " + label + "."), Present(LocalizedLabel("参照 " + label, None)), source, origin)

  private def _canonical(value: JsValue): String = value match {
    case objectvalue: JsObject => objectvalue.fields.toVector.sortBy(_._1).map { case (key, child) =>
      Json.stringify(JsString(key)) + ":" + _canonical(child)
    }.mkString("{", ",", "}")
    case JsArray(values) => values.map(_canonical).mkString("[", ",", "]")
    case scalar => Json.stringify(scalar)
  }

  private def _reconstruct_root(raw: JsObject, salt: Int): JsObject = {
    val nested = _reconstruct(raw, salt).as[JsObject]
    val original = raw.fields.map(_._1)
    val ordered = if (nested.fields.map(_._1) == original) nested.fields.reverse else nested.fields
    JsObject(ListMap(ordered: _*))
  }

  private def _reconstruct(value: JsValue, salt: Int): JsValue = value match {
    case objectvalue: JsObject =>
      val fields = objectvalue.fields.toVector.sortBy(_._1)
      val offset = if (fields.isEmpty) 0 else Math.floorMod(salt, fields.size)
      val ordered = (fields.drop(offset) ++ fields.take(offset)).reverse
      JsObject(ListMap(ordered.map { case (key, child) => key -> _reconstruct(child, salt ^ key.hashCode) }: _*))
    case JsArray(values) => JsArray(values.map(child => _reconstruct(child, salt)))
    case scalar => scalar
  }

  private def _version_mutations(raw: JsObject): Vector[JsObject] = _namespaces.map(namespace =>
    _change(raw, Vector("extensions", namespace, "schemaVersion"), JsString("hostile-unknown-version")))

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

  private def _supplier(index: Int): PublicationDiagnostic = PublicationDiagnostic(InvalidSupplier,
    "extensions." + _namespaces(index), "The supplied " + _labels(index) + " metadata is not admitted.")
  private def _publication_failure: PublicationDiagnostic = PublicationDiagnostic(PublicationFailure,
    "publication", "The semantic metadata publication is invalid.")
  private def _element_failure: Diagnostic = Diagnostic(DanglingReference, "element", "No admitted model element has the requested qualified identity.")
  private def _term_failure: Diagnostic = Diagnostic(DanglingReference, "term", "No admitted Term has the requested qualified identity.")
  private def _unknown_node: CmlCausalMetadata.CausalDiagnostic = CmlCausalMetadata.CausalDiagnostic(CmlCausalMetadata.UnknownElement,
    "node.identity", "The Causal element identity is unknown or unaddressable.")
  private def _present[A](value: Presence[A]): A = value.asInstanceOf[Present[A]].value
  private def _right[A, B](value: Either[A, B]): B = value.right.get
  private def _extensions(raw: JsObject): JsObject = (raw \ "extensions").as[JsObject]

  private def _resource(name: String): JsObject = {
    val source = Source.fromInputStream(getClass.getResourceAsStream("/cozy/modeler/" + name), "UTF-8")
    try Json.parse(source.mkString).as[JsObject] finally source.close()
  }
}

private object CmlSemanticPublicationCompatibilitySpec {
  private final case class Oracle(
    envelope: CmlSemanticMetadata.Envelope,
    structure: Presence[CmlStructureMetadata.Graph],
    classification: Presence[CmlClassificationMetadata.Graph],
    dynamic: Presence[CmlDynamicMetadata.Graph],
    usecase: Presence[CmlUseCaseMetadata.Graph],
    terminology: Presence[CmlTerminologyMetadata.Graph],
    causal: Presence[CmlCausalMetadata.Graph]
  )
}
