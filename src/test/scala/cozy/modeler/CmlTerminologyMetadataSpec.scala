package cozy.modeler

import org.scalacheck.{Gen, Prop, Test}
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import play.api.libs.json.{JsArray, JsBoolean, JsNull, JsNumber, JsObject, JsString, JsValue, Json}
import CmlSemanticFoundation._
import CmlTerminologyMetadata._

/*
 * @since   Oct.  4, 2026
 * @version Oct.  4, 2026
 */
final class CmlTerminologyMetadataSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  "Terminology declarations" should {
    "complete literal correspondence" which {
      "TM-01 preserve the complete catalog envelope and independently ordered projections" in {
        Given("independent full core records references source facts projections and a complete literal wire envelope")
        val supplied = _fixture()
        val expected = _wire(supplied)
        When("the Foundation catalog and Terminology supplier are admitted rendered and read")
        val catalog = CmlSemanticFoundation.build(supplied.records, supplied.terms).right.get
        val graph = CmlTerminologyMetadata.build(catalog, supplied.elements, supplied.projections, supplied.extensions).right.get
        val json = CmlTerminologyMetadata.toJson(graph)
        val canonical = CmlTerminologyMetadata.canonicalJson(graph)
        val parsedcanonical = Json.parse(canonical)
        val consumed = CmlTerminologyMetadata.read(expected).right.get
        val rerendered = CmlTerminologyMetadata.toJson(consumed)
        Then("every core reference and supplied field equals the independent wire and typed declaration")
        (graph.catalog eq catalog) shouldBe true
        graph.envelope.catalog shouldBe catalog
        graph.elements shouldBe supplied.elements
        graph.terms shouldBe supplied.projections
        json shouldBe expected
        parsedcanonical shouldBe expected
        rerendered shouldBe expected
        consumed.catalog.elements shouldBe supplied.records
        consumed.catalog.terms shouldBe supplied.terms
        consumed.elements shouldBe supplied.elements
        consumed.terms shouldBe supplied.projections
        (json \ "schemaVersion").as[String] shouldBe "cozy.cml.semantic-metadata.v1"
      }

      "TM-02 keep Mono Koto term type labels and qualified identity domains independent" in {
        Given("Entity links to Koto Event links to Mono and same-name case Unicode and delimiter-distinct qualified domains")
        val supplied = _fixture()
        val pairs = Vector(("a:b", "c"), ("a", "b:c"), ("A:b", "c"), ("a:b", "C"), ("é", "事"), ("e\u0301", "事"))
        val records = pairs.map { case (authority, id) => _entity.copy(identity = Present(ModelElementId(authority, id)),
          source = _source(authority), references = Present(Vector.empty)) }
        val terms = pairs.map { case (authority, id) => TermRecord(TermId(authority, id), _source(authority)) }
        val distinct = Input(records, terms, records.map(record => ElementProjection(record, Present(Vector.empty), Present(Vector.empty))),
          terms.map(term => _koto.copy(term = term)), Json.obj())
        When("both independent declarations are published consumed and queried through the accepted catalog")
        val original = _exercise(supplied)
        val qualified = _exercise(distinct)
        val modelqueries = pairs.map { case (authority, id) => qualified._1.catalog.element(ModelElementId(authority, id)) }
        val termqueries = pairs.map { case (authority, id) => qualified._1.catalog.term(TermId(authority, id)) }
        Then("full domains stay distinct and literal label order duplicates and preferred labels have separate authority")
        original._1.elements shouldBe supplied.elements
        original._1.terms shouldBe Vector(_mono, _koto)
        original._1.terms(1).bokKind shouldBe Present(Koto)
        original._1.terms(0).bokKind shouldBe Present(Mono)
        original._1.terms(1).termType shouldBe Present("event:Type")
        original._1.terms(0).termType shouldBe Present("entity:Type")
        original._1.terms(1).labels shouldBe Present(Vector(_label, _label, LocalizedLabel("共有", None)))
        original._1.terms(1).preferredLabel shouldBe Present(LocalizedLabel("独立", Some("JA")))
        original._1.elements(1).termReferences shouldBe Present(Vector(_local, _external, _local))
        modelqueries shouldBe records.map(Right(_))
        termqueries shouldBe terms.map(Right(_))
        qualified._2 shouldBe _wire(distinct)
      }
    }

    "explicit availability and occurrence counts" which {
      "TM-04 retain every absence branch at every supplemental field and present empty values" in {
        Given("all four reasons with literal details on core references groups and every Term presence")
        val modes = _gaps.map { gap =>
          val record = _entity.copy(references = gap)
          Input(Vector(record), Vector(_term), Vector(ElementProjection(record, gap, gap)),
            Vector(TermProjection(_term, gap, gap, gap, gap)), Json.obj())
        }
        val empty = Input(Vector(_entity.copy(references = Present(Vector.empty))), Vector(_term),
          Vector(ElementProjection(_entity.copy(references = Present(Vector.empty)), Present(Vector.empty), Present(Vector.empty))),
          Vector(_koto.copy(labels = Present(Vector.empty), preferredLabel = Present(LocalizedLabel("無言語", None)))), Json.obj())
        When("each availability declaration is independently admitted rendered and read")
        val results = modes.map(_exercise)
        val empties = _exercise(empty)
        Then("absence reasons details empty vectors and explicit language None remain distinguishable")
        results.zip(modes).foreach { case (result, supplied) =>
          result._1.elements shouldBe supplied.elements
          result._1.terms shouldBe supplied.projections
          result._2 shouldBe _wire(supplied)
        }
        empties._1.terms shouldBe empty.projections
        empties._2 shouldBe _wire(empty)
      }

      "TM-04 consume each equal anonymous core occurrence with its authored payload order" in {
        Given("two equal anonymous records of an arbitrary core kind and two independently different group gaps")
        val supplied = Input(Vector(_anonymous, _anonymous), Vector.empty,
          Vector(ElementProjection(_anonymous, Present(Vector.empty), _gaps(2)),
            ElementProjection(_anonymous, Present(Vector.empty), _gaps(0))), Vector.empty, Json.obj())
        When("the anonymous multiset is published and read and its synthetic identity is queried")
        val result = _exercise(supplied)
        val unknown = result._1.catalog.element(ModelElementId("model:A", "共有"))
        Then("the two occurrences remain observable with no inferred stable ID")
        result._1.catalog.elements shouldBe Vector(_anonymous, _anonymous)
        result._1.elements shouldBe supplied.elements
        result._2 shouldBe _wire(supplied)
        unknown.isLeft shouldBe true
      }
    }
  }

  "Terminology reference declarations" should {
    "exact filters and explicit bounded groups" which {
      "TM-03 retain ordered repeated Local External Term references and selected full model or Term groups" in {
        Given("a mixed ordered core reference sequence a model-only element and explicit reversed group selection")
        val supplied = _fixture()
        val modelonly = _event.copy(references = Present(Vector(_model)))
        val variants = supplied.copy(records = Vector(_entity, modelonly),
          elements = Vector(ElementProjection(modelonly, Present(Vector.empty), Present(Vector(_model))),
            ElementProjection(_entity, Present(Vector(_local, _external, _local)), Present(Vector(_external, _model, _local, _local)))))
        When("the complete declarations are built rendered and read")
        val result = _exercise(variants)
        Then("model-only filtering is present empty and group order preserves both target domains and repeated counts")
        result._1.elements shouldBe variants.elements
        result._2 shouldBe _wire(variants)
      }

      "TM-03 reject alterations omissions additions reordering and groups exceeding admitted multiplicity" in {
        Given("independent contradicted filters and group selections at one admitted element")
        val supplied = _fixture()
        val filters = Vector(Vector(_external, _local, _local), Vector(_local, _external),
          Vector(_local, _external, _local, _local), Vector(_local.copy(boundary = External), _external, _local))
        val groups = Vector(Vector(_local, _local, _local), Vector(_model.copy(context = Present("forged"))))
        val absentrecord = _entity.copy(references = _gaps(0))
        val absent = Input(Vector(absentrecord), Vector(_term),
          Vector(ElementProjection(absentrecord, _gaps(0), Present(Vector.empty))), Vector(_koto), Json.obj())
        val variants = filters.map(refs => supplied.copy(elements = supplied.elements.updated(1,
          supplied.elements(1).copy(termReferences = Present(refs))))) ++
          groups.map(refs => supplied.copy(elements = supplied.elements.updated(1,
            supplied.elements(1).copy(conceptualGroups = Present(refs))))) ++
          Vector(absent.copy(elements = Vector(absent.elements.head.copy(conceptualGroups = Present(Vector(_local))))),
            absent.copy(elements = Vector(absent.elements.head.copy(termReferences = _gaps(1)))))
        When("the typed and independently authored wire factories interpret every contradiction and the valid empty group")
        val typed = variants.map(value => _publish(value).left.get)
        val wire = variants.map(value => CmlTerminologyMetadata.read(_wire(value)).left.get)
        val valid = _exercise(absent)
        Then("every contradiction fails and an explicitly empty group is allowed on absent core references")
        typed.foreach(_.exists(_.kind == InvalidTerminologySemantics) shouldBe true)
        wire.foreach(_.exists(value => value.kind == InvalidTerminologySemantics || value.kind == InvalidCoreBinding) shouldBe true)
        valid._1.elements shouldBe absent.elements
      }
    }
  }

  "Terminology admission" should {
    "complete core binding and typed failures" which {
      "TM-05 reject missing excess foreign and every full-record mutation" in {
        Given("complete-core mutations at identity kind name source and references plus full Term identity and source mutations")
        val supplied = _fixture()
        val mutations = Vector(_entity.copy(identity = Present(ModelElementId("model:A", "foreign"))),
          _entity.copy(kind = "forged"), _entity.copy(name = "forged"), _entity.copy(source = _source("model:A").copy(line = Some(99))),
          _entity.copy(references = Present(Vector(_local))))
        val variants = mutations.map(record => supplied.copy(elements = supplied.elements.updated(1,
          supplied.elements(1).copy(element = record)))) ++ Vector(
          supplied.copy(elements = supplied.elements.drop(1)), supplied.copy(elements = supplied.elements :+ supplied.elements.head),
          supplied.copy(projections = supplied.projections.drop(1)), supplied.copy(projections = supplied.projections :+ _koto),
          supplied.copy(projections = supplied.projections.updated(1, _koto.copy(term = _term.copy(identity = TermId("bok:A", "foreign"))))),
          supplied.copy(projections = supplied.projections.updated(1, _koto.copy(term = _term.copy(source = _source("bok:A").copy(path = "other.dox"))))))
        When("typed factories and strict readers interpret full-record binding and multiset contradictions")
        val typed = variants.map(value => _publish(value).left.get)
        val wire = variants.map(value => CmlTerminologyMetadata.read(_wire(value)).left.get)
        Then("the exact full-value boundary detects binding missing and excess occurrence failures")
        typed.take(5).foreach(_.head.kind shouldBe InvalidCoreBinding)
        typed.drop(5).map(_.head.kind) shouldBe Vector(MissingProjection, DuplicateProjection, MissingProjection,
          DuplicateProjection, InvalidCoreBinding, InvalidCoreBinding)
        wire.take(5).foreach(_.head.kind shouldBe InvalidCoreBinding)
        wire.drop(5).map(_.head.kind) shouldBe typed.drop(5).map(_.head.kind)
      }

      "TM-05 turn null projections presences payloads and recursively malformed items into deterministic typed diagnostics" in {
        Given("null vectors items core records every supplemental presence payload label enum and malformed full references")
        val supplied = _fixture()
        val elementvariants = Vector[Vector[ElementProjection]](null, Vector(null),
          supplied.elements.updated(1, supplied.elements(1).copy(element = null)),
          supplied.elements.updated(1, supplied.elements(1).copy(termReferences = null)),
          supplied.elements.updated(1, supplied.elements(1).copy(termReferences = Present(null))),
          supplied.elements.updated(1, supplied.elements(1).copy(conceptualGroups = null)),
          supplied.elements.updated(1, supplied.elements(1).copy(conceptualGroups = Present(null)))) ++
          Vector(null, _local.copy(target = null), _local.copy(target = TermTarget(null)), _local.copy(boundary = null), _local.copy(relation = null),
            _local.copy(relation = Present(RelationId(null, "relation"))),
            _local.copy(profile = Present(null)), _local.copy(context = Present(null)), _local.copy(preferredLabel = Present(null)),
            _local.copy(source = null), _local.copy(origin = null), _local.copy(origin = Derived("rule", Vector(null)))).flatMap { ref =>
            Vector(supplied.elements.updated(1, supplied.elements(1).copy(termReferences = Present(Vector(ref)))),
              supplied.elements.updated(1, supplied.elements(1).copy(conceptualGroups = Present(Vector(ref)))))
          }
        val termvariants = Vector[Vector[TermProjection]](null, Vector(null)) ++ Vector(
          _koto.copy(term = null), _koto.copy(termType = null), _koto.copy(termType = Present(null)),
          _koto.copy(bokKind = null), _koto.copy(bokKind = Present(null)), _koto.copy(labels = null),
          _koto.copy(labels = Present(null)), _koto.copy(labels = Present(Vector(null))),
          _koto.copy(labels = Present(Vector(LocalizedLabel("label", null)))),
          _koto.copy(labels = Present(Vector(LocalizedLabel("label", Some(null))))),
          _koto.copy(preferredLabel = null), _koto.copy(preferredLabel = Present(null)),
          _koto.copy(preferredLabel = Present(LocalizedLabel(null, None))),
          _koto.copy(preferredLabel = Present(LocalizedLabel("label", null))),
          _koto.copy(preferredLabel = Present(LocalizedLabel("label", Some(null)))),
          _koto.copy(termType = Absent(null, "gap"))).map(value => supplied.projections.updated(1, value))
        val variants = elementvariants.map(values => supplied.copy(elements = values)) ++
          termvariants.map(values => supplied.copy(projections = values))
        When("the null and malformed declarations are interpreted twice by the typed factory")
        val first = variants.map(_publish)
        val second = variants.map(_publish)
        Then("every user input fails deterministically with typed safe diagnostics and no exception")
        first.foreach(_.isLeft shouldBe true)
        first shouldBe second
        first.flatMap(_.left.get).foreach(value => value.publication shouldBe None)
      }

      "TM-05 keep null Graph rendering as an explicit internal precondition" in {
        Given("a null internal Graph rather than a user factory declaration")
        val graph: Graph = null
        When("both render operations receive the null internal Graph")
        val jsonfailure = intercept[IllegalArgumentException](CmlTerminologyMetadata.toJson(graph))
        val canonicalfailure = intercept[IllegalArgumentException](CmlTerminologyMetadata.canonicalJson(graph))
        Then("both operations identify the admitted Graph precondition")
        jsonfailure.getMessage shouldBe "requirement failed: An admitted Terminology graph is required."
        canonicalfailure.getMessage shouldBe jsonfailure.getMessage
      }
    }

    "base first admission and extension ownership" which {
      "TM-06 retain original publication diagnostics before child conflict or malformed own declarations" in {
        Given("a null catalog null extensions malformed base wire and a conflicting own child")
        val supplied = _fixture()
        val conflict = supplied.extensions + (namespace -> Json.obj("hostile" -> true))
        val malformed = _wire(supplied) + ("schemaVersion" -> JsString("bad"))
        When("the accepted base and Terminology facade interpret the identical invalid input")
        val basecatalog = CmlSemanticMetadata.build(null, conflict).left.get
        val childcatalog = CmlTerminologyMetadata.build(null, null, null, conflict).left.get
        val catalog = CmlSemanticFoundation.build(supplied.records, supplied.terms).right.get
        val baseextensions = CmlSemanticMetadata.build(catalog, null).left.get
        val childextensions = CmlTerminologyMetadata.build(catalog, null, null, null).left.get
        val baseread = CmlSemanticMetadata.read(malformed).left.get
        val childread = CmlTerminologyMetadata.read(malformed).left.get
        val nullread = CmlTerminologyMetadata.read(null)
        val conflicted = CmlTerminologyMetadata.build(catalog, null, null, conflict).left.get
        Then("only publication failure preserves the original typed cause and the own conflict has precedence over projection validation")
        childcatalog.map(_.publication) shouldBe basecatalog.map(Some(_))
        childextensions.map(_.publication) shouldBe baseextensions.map(Some(_))
        childread.map(_.publication) shouldBe baseread.map(Some(_))
        (childcatalog ++ childextensions ++ childread).foreach(_.kind shouldBe PublicationFailure)
        nullread.isLeft shouldBe true
        conflicted shouldBe Vector(TerminologyDiagnostic(ExtensionConflict, "extensions", _conflict_detail, None))
      }

      "TM-06 preserve opaque peer namespaces through the unchanged base envelope" in {
        Given("independent opaque Structure Classification Dynamic Use Case and vendor child objects")
        val peers = Vector("cozy.cml.structure", "cozy.cml.classification", "cozy.cml.dynamic", "cozy.cml.use-case", "vendor.child")
        val extensions = JsObject(peers.map(key => key -> Json.obj("opaque" -> Json.arr("literal", JsNull, 1))))
        val supplied = _fixture().copy(extensions = extensions)
        When("the base is independently admitted and the Terminology supplier publishes and receives the envelope")
        val result = _exercise(supplied)
        val base = CmlSemanticMetadata.read(result._2).right.get
        val missing = CmlTerminologyMetadata.read(_wire(supplied) + ("extensions" -> extensions)).left.get
        Then("every peer object survives without a semantic certification claim and an own child is required")
        (base.extensions - namespace) shouldBe extensions
        (result._1.envelope.extensions - namespace) shouldBe extensions
        missing shouldBe Vector(TerminologyDiagnostic(InvalidShape, "extensions.cozy.cml.terminology", _shape_detail, None))
      }
    }

    "recursive wire ownership" which {
      "TM-07 reject every closed-container mutation with safe fixed coordinates and version precedence" in {
        Given("an independently authored mutation table for child projections presence labels enum embedded core and references")
        val supplied = _fixture()
        val root = _wire(supplied)
        val rows = _shape_mutations
        val hostile = "secret\nattacker.path"
        val versions = Vector(_replace_child(root, Vector("schemaVersion"), JsString("unsupported")),
          _replace_child(root, Vector("schemaVersion"), JsString("unsupported")) + ("extensions" ->
            Json.obj(namespace -> Json.obj("schemaVersion" -> "unsupported", hostile -> true))))
        When("the strict reader interprets every recursive contradiction and unsupported child version")
        val results = rows.map { case (path, value, kind, coordinate) =>
          (CmlTerminologyMetadata.read(_replace_child(root, path, value)).left.get.head, kind, coordinate)
        }
        val versionresults = versions.map(value => CmlTerminologyMetadata.read(value).left.get.head)
        Then("owned containers fail closed with schema-derived paths fixed details and no input echo")
        results.foreach { case (diagnostic, kind, coordinate) =>
          diagnostic.kind shouldBe kind
          diagnostic.path shouldBe coordinate
          diagnostic.publication shouldBe None
          diagnostic.detail shouldBe (if (kind == InvalidShape) _shape_detail else if (coordinate.endsWith(".element") || coordinate.endsWith(".term")) _wire_core_detail else _wire_ref_detail)
          diagnostic.path should not include hostile
          diagnostic.detail should not include hostile
        }
        versionresults.foreach(_ shouldBe TerminologyDiagnostic(UnsupportedSchemaVersion, "schemaVersion", _version_detail, None))
      }

      "TM-08 reject malformed supplied text detail language and identity-like values without normalization" in {
        Given("empty null Unicode edge whitespace and ISO controls alongside valid literal case Unicode delimiters and provenance")
        val supplied = _fixture()
        val invalid = Vector[String]("", null, " padded", "padded\u00a0", "\u2007padded", "bad\u0000text", "bad\u0085text")
        val variants = invalid.flatMap { text =>
          Vector(supplied.copy(projections = supplied.projections.updated(1, _koto.copy(termType = Present(text)))),
            supplied.copy(projections = supplied.projections.updated(1, _koto.copy(labels = Present(Vector(LocalizedLabel(text, None)))))),
            supplied.copy(projections = supplied.projections.updated(1, _koto.copy(preferredLabel = Present(LocalizedLabel("label", Some(text)))))),
            supplied.copy(projections = supplied.projections.updated(1, _koto.copy(bokKind = Absent(NotDeclared, text)))),
            supplied.copy(elements = supplied.elements.updated(1, supplied.elements(1).copy(conceptualGroups = Absent(Unsupported, text)))))
        }
        val identities = invalid.map(text => _entity.copy(identity = Present(ModelElementId(text, "id"))))
        When("typed and wire declarations and independently malformed Foundation identities are interpreted")
        val typed = variants.map(_publish)
        val wire = variants.filter(value => !value.projections.exists(_.termType == Present(null)) &&
          !value.projections.exists(_.labels == Present(Vector(LocalizedLabel(null, None)))) &&
          !value.projections.exists(_.preferredLabel == Present(LocalizedLabel("label", Some(null)))) &&
          !value.projections.exists(_.bokKind == Absent(NotDeclared, null)) &&
          !value.elements.exists(_.conceptualGroups == Absent(Unsupported, null))).map(value => CmlTerminologyMetadata.read(_wire(value)))
        val core = identities.map(record => CmlSemanticFoundation.build(Vector(record), supplied.terms))
        val valid = _exercise(supplied)
        Then("invalid text fails and valid supplied context relation profile source origin and separate labels remain literal")
        typed.foreach(_.left.get.exists(_.kind == InvalidTerminologySemantics) shouldBe true)
        wire.foreach(_.left.get.exists(_.kind == InvalidTerminologySemantics) shouldBe true)
        core.foreach(_.isLeft shouldBe true)
        valid._1.elements shouldBe supplied.elements
        valid._2 shouldBe _wire(supplied)
      }
    }
  }

  "Terminology permutation properties" should {
    "independent catalog projection reference and object order" which {
      "TM-09 preserve declared and anonymous absence facts in at least one hundred non-discarded samples" in {
        Given("a generator independently permuting core records Terms projections reference occurrences groups and recursively reconstructed object keys")
        val requiredtags = Vector("declared", "absence", "references-reordered", "records-reordered", "terms-reordered",
          "elements-reordered", "projections-reordered")
        val generator = for {
          mode <- Gen.oneOf(false, true)
          referenceorder <- _permutation(_references.size)
          refs = referenceorder.map(_references)
          count <- Gen.choose(0, refs.size)
          groups <- Gen.pick(count, refs).map(_.toVector)
          ordinal <- Gen.choose(0, 3)
          supplied = _generated(mode, refs, groups, ordinal)
          recordorder <- _permutation(supplied.records.size)
          records = recordorder.map(supplied.records)
          termorder <- _permutation(supplied.terms.size)
          terms = termorder.map(supplied.terms)
          elementorder <- _permutation(supplied.elements.size)
          elements = elementorder.map(supplied.elements)
          projectionorder <- _permutation(supplied.projections.size)
          projections = projectionorder.map(supplied.projections)
          rotation <- Gen.choose(0, 10000)
          tags = Vector(if (mode) "declared" else "absence") ++ Vector(
            (mode && refs != _references, "references-reordered"),
            (records != supplied.records, "records-reordered"),
            (terms != supplied.terms, "terms-reordered"),
            (elements != supplied.elements, "elements-reordered"),
            (projections != supplied.projections, "projections-reordered")).collect { case (true, tag) => tag }
        } yield (supplied.copy(records = records, terms = terms, elements = elements, projections = projections), rotation, tags)
        When("ScalaCheck interprets every generated complete typed and independent wire declaration without implications or precondition discards")
        val checked = Test.check(Test.Parameters.default.withMinSuccessfulTests(100).withInitialSeed(42L), Prop.forAll(generator) { case (supplied, rotation, tags) =>
          val expected = _wire(supplied)
          val catalog = CmlSemanticFoundation.build(supplied.records, supplied.terms).right.get
          val graph = CmlTerminologyMetadata.build(catalog, supplied.elements, supplied.projections, supplied.extensions).right.get
          val published = CmlTerminologyMetadata.toJson(graph)
          val read = CmlTerminologyMetadata.read(_reconstruct(expected, rotation)).right.get
          val canonical = CmlTerminologyMetadata.canonicalJson(read)
          val result = published == expected && read.catalog.elements == supplied.records && read.catalog.terms == supplied.terms &&
            read.elements == supplied.elements && read.terms == supplied.projections && Json.parse(canonical) == expected
          tags.foldLeft(Prop(result)) { (property, tag) => Prop.collect(tag)(property) }
        })
        val coverage: Map[String, Int] = requiredtags.map { tag =>
          tag -> checked.freqMap.getCounts.collect { case (labels, count) if labels.contains(tag) => count }.sum
        }.toMap
        info(s"TM-09 ScalaCheck succeeded=${checked.succeeded} discarded=${checked.discarded} passed=${checked.passed}")
        info(s"TM-09 coverage=${coverage.toVector.sortBy(_._1).map { case (tag, count) => s"$tag=$count" }.mkString(",")}")
        Then("the property succeeds at least one hundred times discards zero samples and observes both modes and every meaningful reorder dimension")
        checked.passed shouldBe true
        checked.succeeded should be >= 100
        checked.discarded shouldBe 0
        requiredtags.foreach(tag => coverage(tag) should be > 0)
      }
    }
  }

  private final case class Input(
    records: Vector[ElementRecord], terms: Vector[TermRecord], elements: Vector[ElementProjection],
    projections: Vector[TermProjection], extensions: JsObject
  )
  private val _gaps = Vector(Absent(NotDeclared, "Not declared literally."), Absent(NotRepresented, "Not represented literally."),
    Absent(Unsupported, "Unsupported literally."), Absent(NotApplicable, "Not applicable literally."))
  private val _label = LocalizedLabel("共有:É", Some("ja-JP"))
  private val _term = TermRecord(TermId("bok:A", "a:b"), _source("bok:A"))
  private val _other_term = TermRecord(TermId("bok:a", "a:b"), _source("bok:a"))
  private val _local = SemanticReference(TermTarget(_term.identity), Local, Present(RelationId("relation:B", "candidate:共有")),
    Present(ProfileId("profile:C", "Context:É")), Present("literal:共有"), Present(LocalizedLabel("参照", None)),
    _source("model:A"), Derived("rule:É", Vector(_source("supplier:X"), _source("supplier:X"))))
  private val _external = _local.copy(boundary = External)
  private val _event_ref = _local.copy(target = TermTarget(_other_term.identity), origin = Declared)
  private val _model = _local.copy(target = ModelTarget(ModelElementId("model:A", "Event:1")), origin = Declared)
  private val _references = Vector(_local, _model, _external, _local)
  private val _entity = ElementRecord(Present(ModelElementId("model:A", "Entity:1")), "entity", "共有", _source("model:A"), Present(_references))
  private val _event = ElementRecord(Present(ModelElementId("model:A", "Event:1")), "event", "共有", _source("model:A"), Present(Vector(_event_ref)))
  private val _anonymous = ElementRecord(Absent(NotRepresented, "No stable ID supplied."), "arbitrary", "共有", _source("model:A"), Present(Vector.empty))
  private val _koto = TermProjection(_term, Present("event:Type"), Present(Koto),
    Present(Vector(_label, _label, LocalizedLabel("共有", None))), Present(LocalizedLabel("独立", Some("JA"))))
  private val _mono = TermProjection(_other_term, Present("entity:Type"), Present(Mono), Present(Vector.empty), _gaps(3))
  private val _shape_detail = "The Terminology value does not match the required v1 shape."
  private val _version_detail = "The Terminology schema version is not supported."
  private val _wire_core_detail = "The embedded Terminology record does not exactly match admitted core JSON."
  private val _wire_ref_detail = "The Terminology reference does not exactly match an admitted core reference."
  private val _conflict_detail = "The Terminology extension namespace is already supplied by the caller."

  private def _source(authority: String): SourceAttribution = {
    SourceAttribution(authority, "models/literal.cml", "a" * 64, Some(7))
  }

  private def _fixture(): Input = {
    Input(Vector(_entity, _event), Vector(_term, _other_term),
      Vector(ElementProjection(_event, Present(Vector(_event_ref)), _gaps(2)),
        ElementProjection(_entity, Present(Vector(_local, _external, _local)), Present(Vector(_model, _local)))),
      Vector(_mono, _koto), Json.obj("vendor.literal" -> Json.obj("fact" -> "quote:\"\\共有")))
  }

  private def _publish(supplied: Input): Either[Vector[TerminologyDiagnostic], Graph] = {
    val catalog = CmlSemanticFoundation.build(supplied.records, supplied.terms).right.get
    CmlTerminologyMetadata.build(catalog, supplied.elements, supplied.projections, supplied.extensions)
  }

  private def _exercise(supplied: Input): (Graph, JsObject) = {
    val graph = _publish(supplied).right.get
    val json = CmlTerminologyMetadata.toJson(graph)
    (CmlTerminologyMetadata.read(json).right.get, json)
  }

  // Independent wire oracle: these helpers render raw authored declarations only.
  private def _wire(supplied: Input): JsObject = {
    val child = Json.obj("schemaVersion" -> "cozy.cml.terminology.v1",
      "elements" -> JsArray(supplied.elements.map(value => Json.obj("element" -> _element_wire(value.element),
        "termReferences" -> _presence_wire(value.termReferences, (refs: Vector[SemanticReference]) => JsArray(refs.map(_reference_wire))),
        "conceptualGroups" -> _presence_wire(value.conceptualGroups, (refs: Vector[SemanticReference]) => JsArray(refs.map(_reference_wire)))))),
      "terms" -> JsArray(supplied.projections.map(value => Json.obj("term" -> _term_wire(value.term),
        "termType" -> _presence_wire(value.termType, JsString),
        "bokKind" -> _presence_wire(value.bokKind, (kind: BokKind) => JsString(if (kind == Mono) "mono" else "koto")),
        "labels" -> _presence_wire(value.labels, (labels: Vector[LocalizedLabel]) => JsArray(labels.map(_label_wire))),
        "preferredLabel" -> _presence_wire(value.preferredLabel, _label_wire)))))
    Json.obj("schemaVersion" -> "cozy.cml.semantic-metadata.v1", "elements" -> JsArray(supplied.records.map(_element_wire)),
      "terms" -> JsArray(supplied.terms.map(_term_wire)), "extensions" -> (supplied.extensions + ("cozy.cml.terminology" -> child)))
  }

  private def _element_wire(value: ElementRecord): JsObject = {
    Json.obj("identity" -> _presence_wire(value.identity, _model_id_wire), "kind" -> value.kind, "name" -> value.name,
      "source" -> _source_wire(value.source), "references" -> _presence_wire(value.references,
        (refs: Vector[SemanticReference]) => JsArray(refs.map(_reference_wire))))
  }

  private def _term_wire(value: TermRecord): JsObject = {
    Json.obj("identity" -> Json.obj("vocabularyId" -> value.identity.vocabularyId, "termId" -> value.identity.termId), "source" -> _source_wire(value.source))
  }

  private def _model_id_wire(value: ModelElementId): JsObject = {
    Json.obj("modelId" -> value.modelId, "elementId" -> value.elementId)
  }

  private def _source_wire(value: SourceAttribution): JsObject = {
    Json.obj("authorityId" -> value.authorityId, "path" -> value.path, "sha256" -> value.sha256,
      "line" -> value.line.fold[JsValue](JsNull)(line => JsNumber(BigDecimal(line))))
  }

  private def _label_wire(value: LocalizedLabel): JsObject = {
    Json.obj("text" -> value.text, "language" -> value.language.fold[JsValue](JsNull)(JsString))
  }

  private def _reference_wire(value: SemanticReference): JsObject = {
    val target = value.target match {
      case ModelTarget(id) => Json.obj("kind" -> "model-element", "identity" -> _model_id_wire(id))
      case TermTarget(id) => Json.obj("kind" -> "term", "identity" -> Json.obj("vocabularyId" -> id.vocabularyId, "termId" -> id.termId))
    }
    val origin = value.origin match {
      case Declared => Json.obj("kind" -> "declared")
      case Derived(rule, sources) => Json.obj("kind" -> "derived", "ruleId" -> rule, "sources" -> JsArray(sources.map(_source_wire)))
    }
    Json.obj("target" -> target, "boundary" -> (if (value.boundary == Local) "local" else "external"),
      "relation" -> _presence_wire(value.relation, (id: RelationId) => Json.obj("vocabularyId" -> id.vocabularyId, "relationId" -> id.relationId)),
      "profile" -> _presence_wire(value.profile, (id: ProfileId) => Json.obj("vocabularyId" -> id.vocabularyId, "profileId" -> id.profileId)),
      "context" -> _presence_wire(value.context, JsString), "preferredLabel" -> _presence_wire(value.preferredLabel, _label_wire),
      "source" -> _source_wire(value.source), "origin" -> origin)
  }

  private def _presence_wire[A](value: Presence[A], render: A => JsValue): JsObject = {
    value match {
      case Present(payload) => Json.obj("status" -> "present", "value" -> render(payload))
      case Absent(reason, detail) =>
        val tag = reason match {
          case NotDeclared => "not-declared"
          case NotRepresented => "not-represented"
          case Unsupported => "unsupported"
          case NotApplicable => "not-applicable"
        }
        Json.obj("status" -> "absent", "reason" -> tag, "detail" -> detail)
    }
  }

  private def _replace_child(root: JsObject, path: Vector[String], value: JsValue): JsObject = {
    val extensions = (root \ "extensions").as[JsObject]
    root + ("extensions" -> (extensions + (namespace -> _replace(extensions.value(namespace), path, value))))
  }

  private def _replace(root: JsValue, path: Vector[String], value: JsValue): JsValue = {
    if (path.isEmpty) value else root match {
      case objectvalue: JsObject => objectvalue + (path.head -> _replace(objectvalue.value(path.head), path.tail, value))
      case JsArray(values) => JsArray(values.updated(path.head.toInt, _replace(values(path.head.toInt), path.tail, value)))
      case _ => throw new IllegalArgumentException("The authored mutation path is invalid.")
    }
  }

  private def _shape_mutations: Vector[(Vector[String], JsValue, TerminologyDiagnosticKind, String)] = {
    val supplied = _fixture()
    val child = (_wire(supplied) \ "extensions" \ namespace).as[JsObject]
    val element = (child \ "elements").as[Vector[JsObject]].apply(1)
    val term = (child \ "terms").as[Vector[JsObject]].apply(1)
    val presence = (term \ "labels").as[JsObject]
    val label = _label_wire(_label)
    val rows = Vector(
      (Vector.empty[String], child - "elements", InvalidShape, "elements"),
      (Vector.empty[String], child + ("secret\nattacker.path" -> JsBoolean(true)), InvalidShape, "extensions.cozy.cml.terminology"),
      (Vector("elements"), JsNull, InvalidShape, "elements"),
      (Vector("terms"), JsString("wrong"), InvalidShape, "terms"),
      (Vector("elements", "1"), element - "termReferences", InvalidShape, "elements[1].termReferences"),
      (Vector("elements", "1"), element + ("extra" -> JsNull), InvalidShape, "elements[1]"),
      (Vector("terms", "1"), term - "preferredLabel", InvalidShape, "terms[1].preferredLabel"),
      (Vector("terms", "1"), JsNull, InvalidShape, "terms[1]"),
      (Vector("terms", "1", "labels"), presence + ("reason" -> JsString("unsupported")), InvalidShape, "terms[1].labels"),
      (Vector("terms", "1", "labels"), presence - "value", InvalidShape, "terms[1].labels.value"),
      (Vector("terms", "1", "labels", "status"), JsString("Present"), InvalidShape, "terms[1].labels.status"),
      (Vector("terms", "1", "labels", "value"), JsNull, InvalidShape, "terms[1].labels"),
      (Vector("terms", "1", "labels", "value", "0"), label - "language", InvalidShape, "terms[1].labels[0].language"),
      (Vector("terms", "1", "labels", "value", "0"), label + ("extra" -> JsNull), InvalidShape, "terms[1].labels[0]"),
      (Vector("terms", "1", "labels", "value", "0", "text"), JsNumber(1), InvalidShape, "terms[1].labels[0].text"),
      (Vector("terms", "1", "preferredLabel", "value", "language"), JsBoolean(true), InvalidShape, "terms[1].preferredLabel.language"),
      (Vector("terms", "1", "bokKind", "value"), JsString("Mono"), InvalidShape, "terms[1].bokKind"),
      (Vector("terms", "1", "bokKind", "value"), Json.obj("kind" -> "mono"), InvalidShape, "terms[1].bokKind"),
      (Vector("elements", "0", "conceptualGroups", "reason"), JsString("Unsupported"), InvalidShape, "elements[0].conceptualGroups.reason"),
      (Vector("elements", "0", "conceptualGroups", "detail"), JsNull, InvalidShape, "elements[0].conceptualGroups.detail"),
      (Vector("elements", "0", "conceptualGroups"), Json.obj("status" -> "absent", "reason" -> "unsupported", "detail" -> "gap", "value" -> Json.arr()), InvalidShape, "elements[0].conceptualGroups"))
    val core = (element \ "element").as[JsObject]
    val termcore = (term \ "term").as[JsObject]
    val ref = _reference_wire(_local)
    val coremutations = Vector(core - "source", core + ("extra" -> JsNull), core + ("identity" -> JsNull), core + ("kind" -> JsNumber(1)))
    val refmutations = Vector(ref - "origin", ref + ("extra" -> JsNull), ref + ("target" -> Json.obj("kind" -> "Term")),
      ref + ("relation" -> Json.obj("status" -> "present", "value" -> Json.obj("relationId" -> "changed"))),
      ref + ("source" -> JsNull), ref + ("profile" -> JsNull), ref + ("boundary" -> JsString("Local")),
      ref + ("context" -> JsNull), ref + ("preferredLabel" -> JsNull), ref + ("origin" -> Json.obj("kind" -> "unknown")))
    val childfields = Vector("schemaVersion", "elements", "terms").map { name =>
      (Vector.empty[String], child - name, InvalidShape, name)
    }
    val projectionfields = Vector("element", "termReferences", "conceptualGroups").map { name =>
      (Vector("elements", "1"), element - name, InvalidShape, "elements[1]." + name)
    } ++ Vector("term", "termType", "bokKind", "labels", "preferredLabel").map { name =>
      (Vector("terms", "1"), term - name, InvalidShape, "terms[1]." + name)
    }
    val presences = Vector("termType", "bokKind", "labels", "preferredLabel").flatMap { name =>
      Vector((Vector("terms", "1", name), JsNull, InvalidShape, "terms[1]." + name),
        (Vector("terms", "1", name), Json.obj("status" -> "unknown"), InvalidShape, "terms[1]." + name + ".status"),
        (Vector("terms", "1", name), Json.obj("status" -> "absent", "reason" -> "unsupported"), InvalidShape, "terms[1]." + name + ".detail"),
        (Vector("terms", "1", name), Json.obj("status" -> "present", "value" -> JsNull, "extra" -> true), InvalidShape, "terms[1]." + name))
    }
    rows ++ childfields ++ projectionfields ++ presences ++ Vector(
      (Vector("schemaVersion"), JsNull, InvalidShape, "schemaVersion"),
      (Vector("schemaVersion"), JsNumber(1), InvalidShape, "schemaVersion"),
      (Vector("terms", "1", "termType", "value"), JsNumber(1), InvalidShape, "terms[1].termType"),
      (Vector("terms", "1", "preferredLabel", "value"), JsNull, InvalidShape, "terms[1].preferredLabel"),
      (Vector("elements", "1", "termReferences"), JsNull, InvalidShape, "elements[1].termReferences"),
      (Vector("elements", "1", "conceptualGroups"), JsNull, InvalidShape, "elements[1].conceptualGroups")) ++
      coremutations.map(value => (Vector("elements", "1", "element"), value, InvalidCoreBinding, "elements[1].element")) ++
      Vector((Vector("terms", "1", "term"), termcore - "identity", InvalidCoreBinding, "terms[1].term")) ++
      refmutations.map(value => (Vector("elements", "1", "termReferences", "value", "0"), value, InvalidCoreBinding, "elements[1].termReferences[0]"))
  }

  private def _permutation(size: Int): Gen[Vector[Int]] = {
    val indices = (0 until size).toVector
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
    _select_(indices)
  }

  private def _generated(mode: Boolean, refs: Vector[SemanticReference], groups: Vector[SemanticReference], ordinal: Int): Input = {
    if (mode) {
      val record = _entity.copy(references = Present(refs))
      val filtered = refs.collect { case ref if ref.target == TermTarget(_term.identity) => ref }
      _fixture().copy(records = Vector(record, _event), elements = Vector(
        ElementProjection(record, Present(filtered), Present(groups)), ElementProjection(_event, Present(Vector(_event_ref)), _gaps(ordinal))))
    } else {
      val record = _entity.copy(references = _gaps(ordinal))
      Input(Vector(record, _anonymous, _anonymous), Vector(_term),
        Vector(ElementProjection(_anonymous, Present(Vector.empty), _gaps(0)), ElementProjection(record, _gaps(ordinal), Present(Vector.empty)),
          ElementProjection(_anonymous, Present(Vector.empty), _gaps(2))),
        Vector(TermProjection(_term, _gaps(ordinal), _gaps(ordinal), _gaps(ordinal), _gaps(ordinal))), Json.obj())
    }
  }

  private def _reconstruct(value: JsValue, rotation: Int): JsValue = {
    value match {
      case objectvalue: JsObject =>
        val fields = objectvalue.fields.toVector.reverse
        val offset = if (fields.isEmpty) 0 else rotation % fields.size
        JsObject((fields.drop(offset) ++ fields.take(offset)).map { case (key, item) => key -> _reconstruct(item, rotation + 1) })
      case JsArray(values) => JsArray(values.map(_reconstruct(_, rotation + 1)))
      case scalar => scalar
    }
  }
}
