package cozy.modeler

import org.scalacheck.{Gen, Prop, Test}
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import play.api.libs.json.Json
import CmlSemanticFoundation._
import CmlTerminologyMetadata._
import CmlTerminologyNavigation._

/*
 * @since   Oct.  4, 2026
 * @version Oct.  4, 2026
 */
final class CmlTerminologyNavigationSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  "Terminology navigation" should {
    "qualified identities and complete endpoints" which {
      "TN-01 retain qualified scopes independent classifications and original instances through typed and wire admission" in {
        Given("same-named Entity and Event records vocabulary peers coincident Model and Term pairs and independent projections")
        val supplied = _fixture()
        val expected = _expected(supplied)
        val pairs = Vector(("a:b", "c"), ("a", "b:c"), ("A:b", "c"), ("a:b", "C"),
          ("é", "事"), ("e\u0301", "事"), ("語 彙", "項:目"))
        val records = pairs.map { case (authority, id) => ElementRecord(Present(ModelElementId(authority, id)),
          "Policy", "共有", _source(authority), Present(Vector.empty)) }
        val terms = pairs.map { case (authority, id) => TermRecord(TermId(authority, id), _source(authority)) }
        val literal = _input(records, terms)
        When("Foundation and Terminology are built published read and both graphs navigated")
        val graph = _admit(supplied)
        val navigation = CmlTerminologyNavigation.build(graph).right.get
        val observed = _observe(navigation, supplied)
        val consumed = CmlTerminologyMetadata.read(CmlTerminologyMetadata.toJson(graph)).right.get
        val wirenavigation = CmlTerminologyNavigation.build(consumed).right.get
        val wireobserved = _observe(wirenavigation, supplied)
        val literalobserved = _observe(CmlTerminologyNavigation.build(_admit(literal)).right.get, literal)
        Then("full projections and literal qualified pairs remain distinct without classification or label inference")
        (navigation.graph eq graph) shouldBe true
        (navigation.catalog eq graph.catalog) shouldBe true
        (wirenavigation.graph eq consumed) shouldBe true
        (wirenavigation.catalog eq consumed.catalog) shouldBe true
        observed shouldBe expected
        wireobserved shouldBe expected
        literalobserved shouldBe _expected(literal)
        supplied.elements.find(_.element == supplied.records.head).get.element.kind shouldBe "Entity"
        supplied.projections.find(_.term.identity == TermId("v:A", "shared")).get.bokKind shouldBe Present(Koto)
        supplied.records(1).kind shouldBe "Event"
        supplied.projections.find(_.term.identity == TermId("v:B", "shared")).get.bokKind shouldBe Present(Mono)
      }

      "TN-01 admit an empty catalog graph and navigation with no invented links" in {
        Given("empty raw declarations and empty expected navigation facts")
        val supplied = _input(Vector.empty, Vector.empty)
        When("all three immutable suppliers are built and navigation is observed")
        val graph = _admit(supplied)
        val navigation = CmlTerminologyNavigation.build(graph).right.get
        val observed = _observe(navigation, supplied)
        Then("empty declarations expose empty facts and retain the original graph and catalog")
        observed shouldBe _expected(supplied)
        navigation.allLinks shouldBe Vector.empty
        (navigation.graph eq graph) shouldBe true
        (navigation.catalog eq graph.catalog) shouldBe true
      }

      "TN-02 resolve full Local and External payloads and equivalent copied links without synonym substitution" in {
        Given("registered and unregistered External pairs opaque synonym candidates complete provenance and independent Term labels")
        val supplied = _fixture()
        val links = _raw_links(supplied)
        val copies = links.map(link => link.copy(element = link.element.copy(source = link.element.source.copy()),
          reference = link.reference.copy(source = link.reference.source.copy())))
        val expected = _raw_targets(supplied)
        When("the admitted graph resolves every authored link and equivalent full-value copy")
        val navigation = CmlTerminologyNavigation.build(_admit(supplied)).right.get
        val actual = links.map(navigation.resolve)
        val copied = copies.map(navigation.resolve)
        Then("Local returns its exact supplied projection External retains its boundary and copied values are members")
        actual shouldBe expected
        copied shouldBe expected
        actual.head shouldBe Right(LocalTarget(links.head,
          supplied.projections.find(_.term.identity == TermId("v:A", "shared")).get))
        actual(1) shouldBe Right(ExternalTarget(links(1)))
        actual(3) shouldBe Right(ExternalTarget(links(3)))
        links.head.reference.relation shouldBe Present(RelationId("relations:候補", "synonym-candidate"))
        links.head.reference.profile shouldBe Present(ProfileId("profiles:scope", "view:literal"))
        links.head.reference.origin shouldBe Derived("rule:literal", Vector(_source("input:A"), _source("input:B")))
      }
    }

    "authored order multiplicity and availability" which {
      "TN-03 expose equal anonymous Event occurrences repeatedly without address-like lookup aliases" in {
        Given("two equal anonymous Events with repeated complete Local Term references and reversed projection arrays")
        val base = _fixture()
        val anonymous = base.records.last
        val supplied = _input(Vector(anonymous, anonymous), base.terms)
        val expectedlinks = Vector.fill(4)(TermLink(anonymous, _ref("anonymous", TermId("v:A", "shared"), Local)))
        val candidates = Vector(ModelElementId("anonymous", "共有"), ModelElementId("anonymous", "src/model.cml"),
          ModelElementId("anonymous", "anonymous"))
        When("the anonymous graph is built navigated reversed and queried for fabricated identities")
        val navigation = CmlTerminologyNavigation.build(_admit(supplied)).right.get
        val reverse = navigation.linksForTerm(TermId("v:A", "shared"))
        val unknown = candidates.map(navigation.element)
        val linksunknown = candidates.map(navigation.links)
        Then("four full occurrences remain visible and no name path or authority becomes an anonymous identity")
        navigation.allLinks shouldBe expectedlinks
        reverse shouldBe Right(expectedlinks)
        unknown shouldBe candidates.map(_ => Left(_unknown_element("element.identity")))
        linksunknown shouldBe candidates.map(_ => Left(_unknown_element("links.identity")))
      }

      "TN-04 preserve catalog and reference order duplicates and exact Local-only reverse links" in {
        Given("multiple addressable families two anonymous occurrences repeated boundaries and distinct vocabularies")
        val supplied = _fixture()
        val expected = _expected(supplied)
        val opposite = supplied.copy(elements = supplied.elements.reverse, projections = supplied.projections.reverse)
        When("both independently ordered projection declarations are admitted and all public queries are observed")
        val actual = _observe(CmlTerminologyNavigation.build(_admit(supplied)).right.get, supplied)
        val reordered = _observe(CmlTerminologyNavigation.build(_admit(opposite)).right.get, opposite)
        Then("complete raw vectors govern all links element links reverse links and resolved endpoint carriers")
        actual shouldBe expected
        reordered shouldBe _expected(opposite)
        reordered.alllinks shouldBe expected.alllinks
        actual.reverse.head shouldBe Right(expected.alllinks.filter(link =>
          link.reference.boundary == Local && link.reference.target == TermTarget(supplied.terms.head.identity)))
        actual.reverse.last shouldBe Right(Vector.empty)
      }

      "TN-05 preserve all four explicit gaps present empty model-only and known unreferenced Terms" in {
        Given("each absence reason and detail plus present-empty and model-only core reference declarations")
        val base = _fixture()
        val gaps = _gaps.zipWithIndex.map { case (gap, ordinal) => base.records.head.copy(
          identity = Present(ModelElementId("model:A", "gap:" + ordinal)), references = gap) }
        val empty = base.records(3)
        val modelonly = base.records(2).copy(references = Present(Vector(
          base.records(2).references.asInstanceOf[Present[Vector[SemanticReference]]].value.head.copy(boundary = External))))
        val supplied = _input(gaps ++ Vector(empty, modelonly), base.terms)
        val ids = supplied.records.map(_.identity.asInstanceOf[Present[ModelElementId]].value)
        When("the declared graph answers element link and Term reverse queries")
        val navigation = CmlTerminologyNavigation.build(_admit(supplied)).right.get
        val links = ids.map(navigation.links)
        val reverse = supplied.terms.map(term => navigation.linksForTerm(term.identity))
        Then("reasoned absence is literal while both empty modes and known unused Terms return empty values")
        links shouldBe _gaps.map(gap => Right(gap)) ++ Vector(Right(Present(Vector.empty)), Right(Present(Vector.empty)))
        reverse shouldBe supplied.terms.map(_ => Right(Vector.empty))
        navigation.allLinks shouldBe Vector.empty
      }
    }

    "safe query and full-value membership boundaries" which {
      "TN-06 distinguish unknown qualified identities External-only targets and well-formed altered or foreign links" in {
        Given("valid unknown IDs and full-value mutations of every element and reference field")
        val supplied = _fixture()
        val link = _raw_links(supplied).head
        val forged = _foreign_links(link)
        val foreignrecord = supplied.records.head.copy(source = _source("model:A").copy(path = "foreign/model.cml"))
        val foreign = _input(supplied.records.updated(0, foreignrecord), supplied.terms)
        val foreignlink = _raw_links(foreign).head
        val modelid = ModelElementId("model:unknown", "shared")
        val termids = Vector(TermId("v:unknown", "shared"), TermId("remote:v", "remote"))
        val expectedrejected = (forged :+ foreignlink).map { candidate =>
          _raw_links(supplied).zip(_raw_targets(supplied)).find { case (rawlink, _) => rawlink == candidate }
            .map { case (_, target) => target }.getOrElse(Left(_unknown_link))
        }
        When("both graphs are admitted and the first navigation receives unknown queries and complete nonmember values")
        val navigation = CmlTerminologyNavigation.build(_admit(supplied)).right.get
        val other = CmlTerminologyNavigation.build(_admit(foreign)).right.get
        val modelresult = navigation.element(modelid)
        val linksresult = navigation.links(modelid)
        val termresults = termids.map(navigation.term)
        val reverseresults = termids.map(navigation.linksForTerm)
        val rejected = (forged :+ foreignlink).map(navigation.resolve)
        val ownforeign = other.resolve(foreignlink)
        Then("fixed unknown diagnostics follow full membership even for structurally valid unregistered Local targets")
        modelresult shouldBe Left(_unknown_element("element.identity"))
        linksresult shouldBe Left(_unknown_element("links.identity"))
        termresults shouldBe termids.map(_ => Left(_unknown_term("term.identity")))
        reverseresults shouldBe termids.map(_ => Left(_unknown_term("linksForTerm.identity")))
        rejected shouldBe expectedrejected
        ownforeign shouldBe Right(LocalTarget(foreignlink, foreign.projections.find(_.term.identity == TermId("v:A", "shared")).get))
      }

      "TN-07 reject null and malformed qualified components safely at each query coordinate" in {
        Given("null empty Unicode edge spaces and ISO controls in both components of each query family")
        val supplied = _fixture()
        val texts = Vector(null, "", " leading", "trailing ", "\u00a0secret", "secret\u00a0",
          "\u3000secret", "secret\u3000", "secret\u0000", "secret\u0085")
        val modelids = Vector(null: ModelElementId) ++ texts.flatMap(text =>
          Vector(ModelElementId(text, "ok"), ModelElementId("ok", text)))
        val termids = Vector(null: TermId) ++ texts.flatMap(text => Vector(TermId(text, "ok"), TermId("ok", text)))
        When("an admitted navigation receives every malformed identity and null graph construction")
        val navigation = CmlTerminologyNavigation.build(_admit(supplied)).right.get
        val elements = modelids.map(navigation.element)
        val links = modelids.map(navigation.links)
        val terms = termids.map(navigation.term)
        val reverse = termids.map(navigation.linksForTerm)
        val nullgraph = CmlTerminologyNavigation.build(null)
        Then("typed diagnostics contain fixed coordinates details and no supplied secret or throwable cause")
        elements shouldBe modelids.map(_ => Left(_invalid_id("element.identity")))
        links shouldBe modelids.map(_ => Left(_invalid_id("links.identity")))
        terms shouldBe termids.map(_ => Left(_invalid_id("term.identity")))
        reverse shouldBe termids.map(_ => Left(_invalid_id("linksForTerm.identity")))
        nullgraph shouldBe Left(Vector(TerminologyDiagnostic(InvalidShape, "graph", "An admitted Terminology graph is required.", None)))
      }

      "TN-07 reject malformed full record reference source label origin and presence trees without throwing" in {
        Given("pure raw null and malformed mutations throughout both the full element and its independent Term reference")
        val supplied = _fixture()
        val link = _raw_links(supplied).head
        val malformed = _malformed_links(link)
        When("an admitted navigation resolves each malformed tree")
        val navigation = CmlTerminologyNavigation.build(_admit(supplied)).right.get
        val results = malformed.map(navigation.resolve)
        Then("every malformed tree fails the fixed shape boundary before membership or unsafe dereferencing")
        results shouldBe malformed.map(_ => Left(TerminologyDiagnostic(InvalidShape, "resolve.link",
          "The Terminology value does not match the required v1 shape.", None)))
      }
    }

    "independent authored occurrence permutations" which {
      "TN-08 retain raw ordered semantics across both modes and independently permuted input arrays" in {
        Given("occurrence-index permutations independent across references records Terms and supplemental projections with seed 42")
        val generator = for {
          declared <- Gen.oneOf(true, false)
          gap <- Gen.choose(0, 3)
          refs <- _permutation(5)
          records <- _permutation(6)
          terms <- _permutation(3)
          elements <- _permutation(6)
          projections <- _permutation(3)
        } yield (declared, gap, refs, records, terms, elements, projections)
        val tags = Vector("declared", "absence", "references-reordered", "records-reordered", "terms-reordered",
          "elements-reordered", "projections-reordered")
        When("one hundred generated authored declarations are admitted and every navigation operation is observed")
        val counts = scala.collection.mutable.Map(tags.map(_ -> 0): _*)
        val property = Prop.forAll(generator) { case (declared, gap, refs, records, terms, elements, projections) =>
          val base = _fixture()
          val firstrefs = base.records.head.references.asInstanceOf[Present[Vector[SemanticReference]]].value
          val changedrefs = refs.map(firstrefs)
          val first = base.records.head.copy(references = if (declared) Present(changedrefs) else _gaps(gap))
          val rawrecords = base.records.updated(0, first)
          val raw = _input(rawrecords, base.terms)
          val supplied = raw.copy(records = records.map(raw.records), terms = terms.map(raw.terms),
            elements = elements.map(raw.elements), projections = projections.map(raw.projections))
          val observed = _observe(CmlTerminologyNavigation.build(_admit(supplied)).right.get, supplied)
          val expected = _expected(supplied)
          val coverage = Vector(if (declared) "declared" else "absence") ++
            (if (declared && changedrefs != firstrefs) Vector("references-reordered") else Vector.empty) ++
            (if (supplied.records != raw.records) Vector("records-reordered") else Vector.empty) ++
            (if (supplied.terms != raw.terms) Vector("terms-reordered") else Vector.empty) ++
            (if (supplied.elements != raw.elements) Vector("elements-reordered") else Vector.empty) ++
            (if (supplied.projections != raw.projections) Vector("projections-reordered") else Vector.empty)
          coverage.foreach(tag => counts.update(tag, counts(tag) + 1))
          Prop.collect(coverage.sorted.mkString(","))(Prop(observed == expected))
        }
        val result = Test.check(Test.Parameters.default.withMinSuccessfulTests(100).withWorkers(1)
          .withInitialSeed(42L), property)
        val coverage = counts.toVector.sortBy(_._1)
        Then("the property passes without discards and every required mode and actual value reorder has positive coverage")
        info(s"TN-08 succeeded=${result.succeeded} discarded=${result.discarded} passed=${result.passed}")
        info("TN-08 coverage=" + coverage.map { case (tag, count) => s"$tag:$count" }.mkString(","))
        result.passed shouldBe true
        result.succeeded should be >= 100
        result.discarded shouldBe 0
        coverage.map(_._1) shouldBe tags.sorted
        coverage.foreach { case (_, count) => count should be > 0 }
      }
    }
  }

  // Input and expected values are raw declarations. No supplier action occurs here.
  private final case class Input(records: Vector[ElementRecord], terms: Vector[TermRecord],
    elements: Vector[ElementProjection], projections: Vector[TermProjection])

  private final case class Observed(alllinks: Vector[TermLink],
    elements: Vector[Either[TerminologyDiagnostic, ElementProjection]],
    terms: Vector[Either[TerminologyDiagnostic, TermProjection]],
    links: Vector[Either[TerminologyDiagnostic, Presence[Vector[TermLink]]]],
    reverse: Vector[Either[TerminologyDiagnostic, Vector[TermLink]]],
    targets: Vector[Either[TerminologyDiagnostic, ReferenceTarget]])

  private def _source(authority: String): SourceAttribution = {
    SourceAttribution(authority, "src/model.cml", "a" * 64, Some(7))
  }

  private def _ref(authority: String, target: TermId, boundary: ReferenceBoundary): SemanticReference = {
    SemanticReference(TermTarget(target), boundary,
      Present(RelationId("relations:候補", "synonym-candidate")), Present(ProfileId("profiles:scope", "view:literal")),
      Present("literal context: 文脈"), Present(LocalizedLabel("参照ラベル", Some("JA"))), _source(authority),
      Derived("rule:literal", Vector(_source("input:A"), _source("input:B"))))
  }

  private val _gaps: Vector[Absent] = Vector(Absent(NotDeclared, "No declaration."),
    Absent(NotRepresented, "Representation gap."), Absent(Unsupported, "Unsupported capability."),
    Absent(NotApplicable, "Not applicable here."))

  private def _fixture(): Input = {
    val aid = TermId("v:A", "shared")
    val bid = TermId("v:B", "shared")
    val terms = Vector(TermRecord(aid, _source("v:A")), TermRecord(bid, _source("v:B")),
      TermRecord(TermId("v:C", "lonely"), _source("v:C")))
    val local = _ref("model:A", aid, Local)
    val model = local.copy(target = ModelTarget(ModelElementId("model:B", "shared")), origin = Declared)
    val entity = ElementRecord(Present(ModelElementId("model:A", "shared")), "Entity", "共有", _source("model:A"),
      Present(Vector(local, local.copy(boundary = External), local, _ref("model:A", TermId("remote:v", "remote"), External), model)))
    val eventref = _ref("model:B", bid, Local)
    val event = ElementRecord(Present(ModelElementId("model:B", "shared")), "Event", "共有", _source("model:B"),
      Present(Vector(eventref, eventref.copy(boundary = External), _ref("model:B", aid, Local), eventref)))
    val policy = ElementRecord(Present(ModelElementId("v:A", "shared")), "Policy", "共有", _source("v:A"),
      Present(Vector(model.copy(source = _source("v:A")))))
    val empty = ElementRecord(Present(ModelElementId("model:A", "empty")), "Task", "共有", _source("model:A"), Present(Vector.empty))
    val anonymousref = _ref("anonymous", aid, Local)
    val anonymous = ElementRecord(_gaps.head, "Event", "共有", _source("anonymous"), Present(Vector(anonymousref, anonymousref)))
    _input(Vector(entity, event, policy, empty, anonymous, anonymous), terms)
  }

  private def _input(records: Vector[ElementRecord], terms: Vector[TermRecord]): Input = {
    val elements = records.reverse.map { record =>
      val refs = record.references match {
        case Present(values) => Present(values.filter(_.target.isInstanceOf[TermTarget]))
        case gap: Absent => gap
      }
      ElementProjection(record, refs, _gaps(2))
    }
    val projections = terms.reverse.map { term =>
      TermProjection(term, Present("opaque:" + term.identity.vocabularyId),
        Present(if (term.identity.vocabularyId == "v:B") Mono else Koto),
        Present(Vector(LocalizedLabel("共有", None), LocalizedLabel("共有", None), LocalizedLabel("別名", Some("ja")))),
        Present(LocalizedLabel("独立 preferred", Some("EN"))))
    }
    Input(records, terms, elements, projections)
  }

  // These interpretation helpers are called only inside When/property actions.
  private def _admit(input: Input): Graph = {
    val catalog = CmlSemanticFoundation.build(input.records, input.terms).right.get
    CmlTerminologyMetadata.build(catalog, input.elements, input.projections, Json.obj()).right.get
  }

  private def _observe(navigation: Navigation, input: Input): Observed = {
    val ids = input.records.flatMap(record => record.identity match {
      case Present(identity) => Vector(identity)
      case _: Absent => Vector.empty
    })
    Observed(navigation.allLinks, ids.map(navigation.element), input.terms.map(term => navigation.term(term.identity)),
      ids.map(navigation.links), input.terms.map(term => navigation.linksForTerm(term.identity)),
      _raw_links(input).map(navigation.resolve))
  }

  private def _raw_links(input: Input): Vector[TermLink] = {
    input.records.flatMap { record => record.references match {
      case Present(refs) => refs.collect { case ref if ref.target.isInstanceOf[TermTarget] => TermLink(record, ref) }
      case _: Absent => Vector.empty
    } }
  }

  private def _raw_targets(input: Input): Vector[Either[TerminologyDiagnostic, ReferenceTarget]] = {
    _raw_links(input).map { link =>
      if (link.reference.boundary == External) {
        Right(ExternalTarget(link))
      } else {
        val id = link.reference.target.asInstanceOf[TermTarget].identity
        Right(LocalTarget(link, input.projections.find(_.term.identity == id).get))
      }
    }
  }

  private def _expected(input: Input): Observed = {
    val addressable = input.records.filter(_.identity.isInstanceOf[Present[_]])
    val links = _raw_links(input)
    Observed(links, addressable.map(record => Right(input.elements.find(_.element == record).get)),
      input.terms.map(term => Right(input.projections.find(_.term == term).get)), addressable.map { record =>
        Right(record.references match {
          case Present(refs) => Present(refs.collect { case ref if ref.target.isInstanceOf[TermTarget] => TermLink(record, ref) })
          case gap: Absent => gap
        })
      }, input.terms.map(term => Right(links.filter(link => link.reference.boundary == Local &&
        link.reference.target == TermTarget(term.identity)))), _raw_targets(input))
  }

  private def _foreign_links(link: TermLink): Vector[TermLink] = {
    val element = link.element
    val ref = link.reference
    val source = ref.source
    val elements = Vector(element.copy(identity = Present(ModelElementId("foreign", "shared"))),
      element.copy(kind = ""), element.copy(name = ""), element.copy(source = source.copy(authorityId = "foreign")),
      element.copy(source = source.copy(path = "other/model.cml")), element.copy(source = source.copy(sha256 = "b" * 64)),
      element.copy(source = source.copy(line = None)), element.copy(references = Present(Vector(ref))),
      element.copy(identity = _gaps(1)))
    val refs = Vector(ref.copy(target = TermTarget(TermId("missing:v", "unregistered"))), ref.copy(boundary = External),
      ref.copy(relation = Present(RelationId("other", "synonym-candidate"))),
      ref.copy(relation = Present(RelationId("relations:候補", "other"))),
      ref.copy(profile = Present(ProfileId("other", "view:literal"))),
      ref.copy(profile = Present(ProfileId("profiles:scope", "other"))),
      ref.copy(context = Present("other context")), ref.copy(preferredLabel = Present(LocalizedLabel("other", Some("JA")))),
      ref.copy(preferredLabel = Present(LocalizedLabel("参照ラベル", None))),
      ref.copy(source = source.copy(authorityId = "foreign")), ref.copy(source = source.copy(path = "foreign/ref.cml")),
      ref.copy(source = source.copy(sha256 = "b" * 64)), ref.copy(source = source.copy(line = Some(8))),
      ref.copy(origin = Declared), ref.copy(origin = Derived("other:rule", Vector(_source("input:A")))),
      ref.copy(origin = Derived("rule:literal", Vector(_source("input:B"), _source("input:A")))))
    elements.map(value => link.copy(element = value)) ++ refs.map(value => link.copy(reference = value))
  }

  private def _bad_sources(source: SourceAttribution): Vector[SourceAttribution] = {
    Vector(null, source.copy(authorityId = null), source.copy(authorityId = ""), source.copy(authorityId = "\u00a0bad"),
      source.copy(path = null), source.copy(path = ""), source.copy(path = "/absolute"), source.copy(path = "http:remote"),
      source.copy(path = "a\\b"), source.copy(path = "a//b"), source.copy(path = "a/./b"), source.copy(path = "a/../b"),
      source.copy(path = "a/"), source.copy(path = "a\u0000b"), source.copy(sha256 = null), source.copy(sha256 = "A" * 64),
      source.copy(sha256 = "a" * 63), source.copy(sha256 = "g" * 64), source.copy(line = null),
      source.copy(line = Some(0)), source.copy(line = Some(-1)))
  }

  private def _bad_refs(ref: SemanticReference): Vector[SemanticReference] = {
    val malformedgaps = Vector(Absent(null, "gap"), Absent(NotDeclared, null), Absent(NotDeclared, ""),
      Absent(NotDeclared, "\u3000gap"), Absent(NotDeclared, "gap\u0085"))
    val labels = Vector(null, LocalizedLabel(null, None), LocalizedLabel("", None), LocalizedLabel("\u00a0label", None),
      LocalizedLabel("label", null), LocalizedLabel("label", Some(null)), LocalizedLabel("label", Some("")),
      LocalizedLabel("label", Some("ja\u3000")), LocalizedLabel("label\u0000", None))
    val origins = Vector(null, Derived(null, Vector(ref.source)), Derived("", Vector(ref.source)),
      Derived("\u00a0rule", Vector(ref.source)), Derived("rule", null), Derived("rule", Vector.empty), Derived("rule", Vector(null))) ++
      _bad_sources(ref.source).map(source => Derived("rule", Vector(source)))
    Vector(null, ref.copy(target = null), ref.copy(target = TermTarget(null)),
      ref.copy(target = TermTarget(TermId(null, "ok"))), ref.copy(target = TermTarget(TermId("ok", ""))),
      ref.copy(target = ModelTarget(null)), ref.copy(target = ModelTarget(ModelElementId("ok", "\u00a0bad"))),
      ref.copy(boundary = null), ref.copy(relation = null), ref.copy(relation = Present(null)),
      ref.copy(relation = Present(RelationId(null, "ok"))), ref.copy(relation = Present(RelationId("ok", ""))),
      ref.copy(profile = null), ref.copy(profile = Present(null)), ref.copy(profile = Present(ProfileId(null, "ok"))),
      ref.copy(profile = Present(ProfileId("ok", "bad\u3000"))), ref.copy(context = null), ref.copy(context = Present(null)),
      ref.copy(context = Present("")), ref.copy(context = Present("secret\u0085")), ref.copy(preferredLabel = null)) ++
      malformedgaps.flatMap(gap => Vector(ref.copy(relation = gap), ref.copy(profile = gap), ref.copy(context = gap),
        ref.copy(preferredLabel = gap))) ++ labels.map(label => ref.copy(preferredLabel = Present(label))) ++
      _bad_sources(ref.source).map(source => ref.copy(source = source)) ++ origins.map(origin => ref.copy(origin = origin))
  }

  private def _malformed_links(link: TermLink): Vector[TermLink] = {
    val element = link.element
    val ref = link.reference
    val elements = Vector(null, element.copy(kind = null), element.copy(name = null), element.copy(identity = null),
      element.copy(identity = Present(null)), element.copy(identity = Present(ModelElementId(null, "ok"))),
      element.copy(identity = Present(ModelElementId("ok", ""))), element.copy(identity = Absent(null, "gap")),
      element.copy(identity = Absent(NotDeclared, null)), element.copy(identity = Absent(NotDeclared, "bad\u00a0")),
      element.copy(references = null), element.copy(references = Present(null)), element.copy(references = Present(Vector(null))),
      element.copy(references = Absent(null, "gap")), element.copy(references = Absent(Unsupported, null)),
      element.copy(references = Absent(Unsupported, ""))) ++
      _bad_sources(element.source).map(source => element.copy(source = source)) ++
      _bad_refs(ref).map(value => element.copy(references = Present(Vector(value))))
    Vector(null: TermLink) ++ elements.map(value => link.copy(element = value)) ++
      _bad_refs(ref).map(value => link.copy(reference = value)) ++
      Vector(link.copy(reference = ref.copy(target = ModelTarget(ModelElementId("model:B", "shared")))))
  }

  private def _permutation(size: Int): Gen[Vector[Int]] = {
    def _select_(remaining: Vector[Int]): Gen[Vector[Int]] = {
      if (remaining.isEmpty) {
        Gen.const(Vector.empty[Int])
      } else {
        for {
          index <- Gen.choose(0, remaining.size - 1)
          rest <- _select_(remaining.patch(index, Vector.empty[Int], 1))
        } yield remaining(index) +: rest
      }
    }
    _select_((0 until size).toVector)
  }

  private def _invalid_id(path: String): TerminologyDiagnostic = {
    TerminologyDiagnostic(InvalidTerminologySemantics, path,
      "The declared Terminology reference, label, classification or absence is invalid.", None)
  }

  private def _unknown_element(path: String): TerminologyDiagnostic = {
    TerminologyDiagnostic(UnknownElement, path, "No admitted Terminology element has the requested qualified identity.", None)
  }

  private def _unknown_term(path: String): TerminologyDiagnostic = {
    TerminologyDiagnostic(UnknownTerm, path, "No admitted Terminology Term has the requested qualified identity.", None)
  }

  private val _unknown_link: TerminologyDiagnostic = TerminologyDiagnostic(UnknownLink, "resolve.link",
    "The Terminology link is not admitted by this graph.", None)
}
