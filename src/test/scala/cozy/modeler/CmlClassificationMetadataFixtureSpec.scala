package cozy.modeler

import scala.io.Source
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalacheck.{Gen, Prop, Test}
import play.api.libs.json._
import CmlSemanticFoundation._
import CmlClassificationMetadata._
import CmlClassificationTopology.{TopologyLink, Generalization, TraitRealization, PowertypeClassification}

/*
 * @since   Oct. 3, 2026
 * @version Oct. 13, 2026
 * @author  ASAMI, Tomoharu
 */
class CmlClassificationMetadataFixtureSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  "Declared supplier resource" should {
    "FM-01 retain every literal catalog and projection fact through the full wire boundary" in {
      Given("the accepted Structure prefix and independently authored Classification source facts")
      val filename = "classification-metadata-v1-declared.json"
      When("the actual UTF-8 declared resource is read, built and canonically consumed")
      val baseline = _baseline()
      val root = _resource(filename)
      val result = _exercise(root, baseline, _expected(baseline, false))
      Then("all 22 records, two Terms, five projections and full writer JSON match their literal oracle")
      result shouldBe true
    }
    "FM-02 expose eleven literal nodes and eight directed links with three independent dimensions" in {
      Given("qualified model identities, a separate colliding Term and an unresolved external supertype")
      val filename = "classification-metadata-v1-declared.json"
      When("the declared resource supplies the topology and every qualified query")
      val baseline = _baseline()
      val result = _topology(_right(CmlClassificationMetadata.read(_resource(filename))),
        _expected(baseline, false))
      Then("catalog order, Local directions, separate domains and each dimension own identity remain exact")
      result shouldBe true
    }
  }
  "Explicit absence resource" should {
    "FM-03 retain anonymous identity and every cyclic absence reason and detail" in {
      Given("one anonymous trait and independently authored absent members and relation endpoints")
      val filename = "classification-metadata-v1-absence.json"
      When("the actual UTF-8 absence resource is read, built and canonically consumed")
      val baseline = _baseline()
      val result = _exercise(_resource(filename), baseline, _expected(baseline, true))
      Then("full catalog, projections and wire preserve all four absence reasons without invented identities")
      result shouldBe true
    }
    "FM-04 keep the anonymous trait visible and dimensions addressable without Local edges" in {
      Given("the anonymous audited trait has a separately addressable Term with matching components")
      val filename = "classification-metadata-v1-absence.json"
      When("the absence resource supplies all topology and dimension queries")
      val baseline = _baseline()
      val result = _topology(_right(CmlClassificationMetadata.read(_resource(filename))),
        _expected(baseline, true))
      Then("eleven nodes and eight absent links remain while known-node indexes are empty")
      result shouldBe true
    }
  }
  "Accepted Structure cohabitation" should {
    "FM-05 retain the original six-record prefix, Term, projections and both opaque peers" in {
      Given("the separately accepted declared Structure fixture is the predecessor oracle")
      val modes = Vector(false, true)
      When("the baseline and both Classification resources pass strict Structure and Classification receivers")
      val baseline = _baseline()
      val basejson = _resource("structure-metadata-v1-declared.json")
      val results = modes.map { absent =>
        val root = _resource(_filename(absent))
        val graph = _right(CmlClassificationMetadata.read(root))
        val rendered = CmlClassificationMetadata.toJson(graph)
        val consumed = _right(CmlClassificationMetadata.read(Json.parse(CmlClassificationMetadata.canonicalJson(graph))))
        val structure = _right(CmlStructureMetadata.read(root))
        structure.elements == baseline.elements && structure.relations == baseline.relations &&
          graph.catalog.elements.take(6) == baseline.catalog.elements && graph.catalog.terms.head == baseline.catalog.terms.head &&
          (root \ "extensions" \ CmlStructureMetadata.namespace).get == (basejson \ "extensions" \ CmlStructureMetadata.namespace).get &&
          (root \ "extensions" \ "org.example.structure-handoff").get == (basejson \ "extensions" \ "org.example.structure-handoff").get &&
          (rendered \ "extensions" \ _peer).get == _peer_value && consumed.envelope.extensions.value(_peer) == _peer_value
      }
      Then("the original full Structure interpretation and opaque values remain unchanged in both modes")
      results shouldBe Vector(true, true)
    }
  }
  "Strict fixture rejection" should {
    "FM-06 reject unsupported versions and recursive child and Presence shape mutations safely" in {
      Given("only the actual fixture's Classification child is mutated with hostile values")
      val filename = "classification-metadata-v1-declared.json"
      When("every malformed variant is passed to the strict reader")
      val root = _resource(filename)
      val variants = _shape_variants(root)
      val results = variants.map { case (value, kind, path) =>
        CmlClassificationMetadata.read(value) == Left(Vector(_diagnostic(kind, path,
          if (kind == UnsupportedSchemaVersion) "The Classification schema version is not supported."
          else "The Classification value does not match the required v1 shape.")))
      }
      Then("each exact typed diagnostic has a fixed path and detail without echoing mutation content")
      results shouldBe Vector.fill(variants.size)(true)
    }
    "FM-07 reject forged embedded kind, source and identity against the unchanged admitted core" in {
      Given("embedded trait records cannot replace catalog authority")
      val recordpath = _child_path ++ Vector("traits", "0", "element")
      When("the reader consumes each single-field forged binding and reconsumes the original")
      val root = _resource("classification-metadata-v1-declared.json")
      val variants = Vector(
        _change(root, recordpath :+ "kind", _ => JsString("powertype")),
        _change(root, recordpath ++ Vector("source", "line"), _ => JsNumber(999)),
        _change(root, recordpath ++ Vector("identity", "value", "elementId"), _ => JsString("forged:hostile")))
      val results = variants.map(CmlClassificationMetadata.read)
      val expected = Left(Vector(_diagnostic(InvalidCoreBinding, _wire_path + ".traits[0].element",
        "The embedded Classification record does not exactly match admitted core JSON.")))
      val original = _exercise(root, _baseline(), _expected(_baseline(), false))
      Then("all forged records fail the exact core binding and the original fixture remains canonical")
      results shouldBe Vector.fill(3)(expected)
      original shouldBe true
    }
    "FM-08 reject dangling and wrong-kind Local references without resolving External targets" in {
      Given("local node, trait, powertype and member targets have distinct admitted kind requirements")
      val mutations = Vector(
        (Vector("generalizations", "0", "subtype", "value"), "missing", "generalizations[0].subtype.value"),
        (Vector("generalizations", "0", "supertype", "value"), "generalization:trait", "generalizations[0].supertype.value"),
        (Vector("traitRealizations", "0", "traitTarget", "value"), "powertype:status", "traitRealizations[0].traitTarget.value"),
        (Vector("powertypeClassifications", "0", "powertype", "value"), "trait:labeled", "powertypeClassifications[0].powertype.value"),
        (Vector("powertypes", "0", "members", "value", "0"), "trait:labeled", "powertypes[0].members.value[0]"))
      When("each actual fixture reference is replaced and the original external fixture is read")
      val root = _resource("classification-metadata-v1-declared.json")
      val results = mutations.map { case (path, id, diagnosticpath) =>
        val value = _change(root, _child_path ++ path, _ => Json.obj(
          "identity" -> Json.obj("modelId" -> "classification.example", "elementId" -> id), "boundary" -> "local"))
        CmlClassificationMetadata.read(value) == Left(Vector(_diagnostic(InvalidClassificationSemantics, diagnosticpath,
          "The declared Classification reference or absence is invalid.")))
      }
      val original = _right(CmlClassificationMetadata.read(root)).generalizations(1).supertype
      Then("each rejection has its exact semantic diagnostic and the unresolved External endpoint stays literal")
      results shouldBe Vector.fill(5)(true)
      original shouldBe Present(ModelReference(ModelElementId("external.example", "entity:base"), External))
    }
  }
  "Active fixture properties" should {
    "FM-09 preserve both modes under independent catalog/carrier permutations and object-key reconstruction" in {
      Given("both actual resources and independent literal facts admit every selected permutation")
      val generator = for {
        absent <- Gen.oneOf(false, true)
        core <- _permutation(22)
        traits <- _permutation(2)
        powertypes <- _permutation(2)
        generalizations <- _permutation(3)
        realizations <- _permutation(2)
        dimensions <- _permutation(3)
        reverse <- Gen.oneOf(false, true)
      } yield (absent, core, traits, powertypes, generalizations, realizations, dimensions, reverse)
      When("ScalaCheck interprets at least 100 non-discarded cases through all receivers and topology queries")
      val baseline = _baseline()
      val roots = Vector(_resource(_filename(false)), _resource(_filename(true)))
      val property = Prop.forAll(generator) { selection =>
        Given("a generated mode and independent full index permutations with reconstructed object keys")
        val (absent, core, traits, powertypes, generalizations, realizations, dimensions, reverse) = selection
        val literal = _expected(baseline, absent)
        val expected = literal.copy(core = core.map(literal.core), traits = traits.map(literal.traits),
          powertypes = powertypes.map(literal.powertypes), generalizations = generalizations.map(literal.generalizations),
          realizations = realizations.map(literal.realizations), dimensions = dimensions.map(literal.dimensions))
        When("the selected actual arrays are permuted and recursively reconstructed before full strict consumption")
        val selections = Vector("traits" -> traits, "powertypes" -> powertypes, "generalizations" -> generalizations,
          "traitRealizations" -> realizations, "powertypeClassifications" -> dimensions)
        val permuted = selections.foldLeft(_change(roots(if (absent) 1 else 0), Vector("elements"),
          value => JsArray(core.map(value.as[JsArray].value)))) { case (root, (key, order)) =>
          _change(root, _child_path :+ key, value => JsArray(order.map(value.as[JsArray].value)))
        }
        val result = _exercise(_keys(permuted, reverse).as[JsObject], baseline, expected)
        Then("independent expected facts, catalog-ordered topology, absence and canonical wire remain exact")
        Prop(result)
      }
      val result = Test.check(Test.Parameters.default.withMinSuccessfulTests(100), property)
      Then("the active semantic property succeeds with at least 100 cases and zero discards")
      result.passed shouldBe true
      result.succeeded should be >= 100
      result.discarded shouldBe 0
    }
  }

  private case class Expected(
    core: Vector[ElementRecord], terms: Vector[TermRecord], traits: Vector[TraitProjection],
    powertypes: Vector[PowertypeProjection], generalizations: Vector[GeneralizationProjection],
    realizations: Vector[TraitRealizationProjection], dimensions: Vector[PowertypeClassificationProjection], audited: ElementRecord
  )
  private val _wire_path = "extensions.cozy.cml.classification"
  private val _child_path = Vector("extensions", "cozy.cml.classification")
  private val _peer = "org.example.classification-handoff"
  private val _peer_value = Json.obj("producer" -> "synthetic-caller",
    "dimensions" -> Json.arr("status", "category", "status-secondary"), "absence" -> JsNull)
  private val _order = ModelElementId("structure.example", "entity:order")
  private val _audited = ModelElementId("classification.example", "trait:audited")
  private val _source_path = "src/main/cml/classification-handoff.cml"
  private val _absences = Vector(
    Absent(NotDeclared, "No Classification reference is declared."),
    Absent(NotRepresented, "The Classification reference is not represented."),
    Absent(Unsupported, "The Classification reference is unsupported."),
    Absent(NotApplicable, "The Classification reference is not applicable."))

  private def _expected(baseline: CmlStructureMetadata.Graph, absent: Boolean): Expected = {
    // Independent literals: never decoded from Classification resource bytes or output.
    val declarations = Vector(
      ("trait", "trait:audited", 7), ("trait", "trait:labeled", 8),
      ("powertype", "powertype:status", 9), ("powertype", "powertype:category", 10),
      ("powertype-kind", "kind:status-open", 11), ("powertype-kind", "kind:status-closed", 12),
      ("powertype-kind", "kind:category-retail", 13), ("powertype-kind", "kind:category-wholesale", 14),
      ("generalization", "generalization:order-aggregate", 15), ("generalization", "generalization:order-external", 16),
      ("generalization", "generalization:trait", 17), ("trait-realization", "trait-realization:order-audited", 18),
      ("trait-realization", "trait-realization:order-labeled", 19), ("powertype-classification", "dimension:status", 20),
      ("powertype-classification", "dimension:category", 21), ("powertype-classification", "dimension:status-secondary", 22))
    val records = declarations.map { case (kind, id, line) => ElementRecord(
      if (absent && id == "trait:audited") Absent(NotRepresented, "No stable trait identity is represented.")
      else Present(ModelElementId("classification.example", id)), kind, "共有",
      SourceAttribution("classification.example", _source_path, Some(line)),
      Absent(NotDeclared, "No semantic references are declared.")) }
    def _endpoint_(ordinal: Int, id: String, model: String = "classification.example", boundary: ReferenceBoundary = Local): Presence[ModelReference] =
      if (absent) _absences(ordinal % 4) else Present(ModelReference(ModelElementId(model, id), boundary))
    val traits = records.take(2).map(TraitProjection)
    val powertypes = Vector(
      PowertypeProjection(records(2), if (absent) _absences(0) else Present(Vector(
        ModelReference(ModelElementId("classification.example", "kind:status-open"), Local),
        ModelReference(ModelElementId("classification.example", "kind:status-closed"), Local)))),
      PowertypeProjection(records(3), if (absent) _absences(1) else Present(Vector(
        ModelReference(ModelElementId("classification.example", "kind:category-retail"), Local),
        ModelReference(ModelElementId("classification.example", "kind:category-wholesale"), Local)))))
    val generalizations = Vector(
      GeneralizationProjection(records(8), _endpoint_(0, "entity:order", "structure.example"), _endpoint_(1, "aggregate:order", "structure.example")),
      GeneralizationProjection(records(9), _endpoint_(1, "entity:order", "structure.example"), _endpoint_(2, "entity:base", "external.example", External)),
      GeneralizationProjection(records(10), _endpoint_(2, "trait:audited"), _endpoint_(3, "trait:labeled")))
    val realizations = Vector(
      TraitRealizationProjection(records(11), _endpoint_(0, "entity:order", "structure.example"), _endpoint_(1, "trait:audited")),
      TraitRealizationProjection(records(12), _endpoint_(1, "entity:order", "structure.example"), _endpoint_(2, "trait:labeled")))
    val dimensions = Vector(
      PowertypeClassificationProjection(records(13), _endpoint_(0, "entity:order", "structure.example"), _endpoint_(1, "powertype:status")),
      PowertypeClassificationProjection(records(14), _endpoint_(1, "entity:order", "structure.example"), _endpoint_(2, "powertype:category")),
      PowertypeClassificationProjection(records(15), _endpoint_(2, "entity:order", "structure.example"), _endpoint_(3, "powertype:status")))
    Expected(baseline.catalog.elements ++ records, baseline.catalog.terms :+ TermRecord(
      TermId("classification.example", "trait:audited"), SourceAttribution("classification.example", _source_path, Some(7))),
      traits, powertypes, generalizations, realizations, dimensions, records(0))
  }
  private def _permutation(size: Int): Gen[Vector[Int]] =
    Gen.listOfN(size, Gen.choose(0, Int.MaxValue)).map(values => values.zipWithIndex.sortBy(_._1).map(_._2).toVector)

  private def _facts(graph: Graph, expected: Expected): Boolean =
    graph.catalog.elements == expected.core && graph.catalog.terms == expected.terms && graph.traits == expected.traits &&
      graph.powertypes == expected.powertypes && graph.generalizations == expected.generalizations &&
      graph.traitRealizations == expected.realizations && graph.powertypeClassifications == expected.dimensions

  private def _exercise(root: JsObject, baseline: CmlStructureMetadata.Graph, expected: Expected): Boolean = {
    val graph = _right(CmlClassificationMetadata.read(root))
    val catalog = _right(CmlSemanticFoundation.build(expected.core, expected.terms))
    val built = _right(CmlClassificationMetadata.build(catalog, expected.traits, expected.powertypes, expected.generalizations,
      expected.realizations, expected.dimensions, root.value("extensions").as[JsObject] - namespace))
    val json = CmlClassificationMetadata.toJson(graph)
    val canonical = CmlClassificationMetadata.canonicalJson(graph)
    val consumed = _right(CmlClassificationMetadata.read(Json.parse(canonical)))
    val structure = _right(CmlStructureMetadata.read(root))
    val topology = _topology(graph, expected)
    _facts(graph, expected) && _facts(built, expected) && _facts(consumed, expected) && json == root &&
      CmlClassificationMetadata.toJson(built) == root && CmlClassificationMetadata.toJson(consumed) == root &&
      CmlClassificationMetadata.canonicalJson(consumed) == canonical && CmlClassificationMetadata.canonicalJson(built) == canonical &&
      structure.elements == baseline.elements && structure.relations == baseline.relations &&
      consumed.envelope.extensions.value(_peer) == _peer_value && topology
  }
  private def _topology(graph: Graph, expected: Expected): Boolean = {
    val topology = _right(CmlClassificationTopology.build(graph))
    val nodekinds = Set("entity", "value", "aggregate", "trait", "powertype", "powertype-kind")
    val nodes = expected.core.filter(value => nodekinds(value.kind))
    val carriers = expected.generalizations.map(value => TopologyLink(value.element, Generalization, value.subtype, value.supertype)) ++
      expected.realizations.map(value => TopologyLink(value.element, TraitRealization, value.subject, value.traitTarget)) ++
      expected.dimensions.map(value => TopologyLink(value.element, PowertypeClassification, value.subject, value.powertype))
    val links = expected.core.flatMap(record => carriers.filter(_.element == record))
    val dimensions = links.filter(_.kind == PowertypeClassification).map(link => expected.dimensions.find(_.element == link.element).get)
    val queries = nodes.flatMap { record => record.identity match {
      case Present(id) => Some(topology.node(id) == Right(record) &&
        topology.outgoing(id) == Right(links.filter(_.source == Present(ModelReference(id, Local)))) &&
        topology.incoming(id) == Right(links.filter(_.target == Present(ModelReference(id, Local)))) &&
        topology.dimensions(id) == Right(dimensions.filter(_.subject == Present(ModelReference(id, Local)))))
      case _: Absent => None
    }}
    val ownids = expected.dimensions.map(value => topology.dimension(value.element.identity.asInstanceOf[Present[ModelElementId]].value) == Right(value))
    val traitquery = topology.node(_audited)
    val termquery = graph.catalog.term(TermId("classification.example", "trait:audited"))
    val externalquery = topology.node(ModelElementId("external.example", "entity:base"))
    val wrongmodel = topology.node(ModelElementId("structure.example", "trait:audited"))
    val orderquery = topology.dimensions(_order)
    val labeledquery = topology.incoming(ModelElementId("classification.example", "trait:labeled"))
    topology.nodes == nodes && nodes.size == 11 && topology.links == links && links.size == 8 &&
      queries.forall(identity) && ownids.forall(identity) && orderquery == Right(dimensions.filter(_.subject == Present(ModelReference(_order, Local)))) &&
      labeledquery == Right(links.filter(_.target == Present(ModelReference(ModelElementId("classification.example", "trait:labeled"), Local)))) &&
      traitquery == (expected.audited.identity match {
        case _: Absent => Left(_diagnostic(UnknownNode, "node.identity", "The Classification node identity is unknown or unaddressable."))
        case _ => Right(expected.audited)
      }) && termquery == Right(expected.terms.last) &&
      externalquery == Left(_diagnostic(UnknownNode, "node.identity", "The Classification node identity is unknown or unaddressable.")) &&
      wrongmodel == externalquery
  }
  private def _shape_variants(root: JsObject): Vector[(JsObject, ClassificationDiagnosticKind, String)] = {
    val child = _child_path
    val prefix = _wire_path
    val base = Vector(
      (_change(root, child :+ "schemaVersion", _ => JsString("hostile/version")), UnsupportedSchemaVersion, prefix + ".schemaVersion"),
      (_change(root, child, value => value.as[JsObject] + ("hostile/key" -> JsNull)), InvalidShape, prefix),
      (_change(root, child, value => value.as[JsObject] - "traits"), InvalidShape, prefix + ".traits"),
      (_change(root, Vector("extensions"), value => value.as[JsObject] - namespace), InvalidShape, prefix))
    val fields = Vector("traits" -> "element", "powertypes" -> "members", "generalizations" -> "subtype",
      "traitRealizations" -> "traitTarget", "powertypeClassifications" -> "powertype")
    val omitted = fields.map { case (key, field) =>
      (_change(root, child ++ Vector(key, "0"), value => value.as[JsObject] - field), InvalidShape, prefix + "." + key + "[0]." + field)
    }
    val presencepath = child ++ Vector("generalizations", "0", "subtype")
    val diagnosticpath = prefix + ".generalizations[0].subtype"
    val presence = Vector(
      (_change(root, presencepath, value => value.as[JsObject] + ("hostile/key" -> JsNull)), InvalidShape, diagnosticpath),
      (_change(root, presencepath, value => value.as[JsObject] - "value"), InvalidShape, diagnosticpath + ".value"),
      (_change(root, presencepath :+ "status", _ => JsString("mixed")), InvalidShape, diagnosticpath + ".status"),
      (_change(root, presencepath, _ => Json.obj("status" -> "absent", "reason" -> "hostile/reason", "detail" -> "hostile/detail")),
        InvalidShape, diagnosticpath + ".reason"),
      (_change(root, presencepath, _ => Json.obj("status" -> "absent", "reason" -> "not-declared")), InvalidShape, diagnosticpath + ".detail"),
      (_change(root, presencepath, _ => Json.obj("status" -> "absent", "reason" -> "not-declared", "detail" -> "Declared absence.", "value" -> JsNull)),
        InvalidShape, diagnosticpath))
    base ++ omitted ++ presence
  }
  private def _change(root: JsObject, path: Vector[String], update: JsValue => JsValue): JsObject = {
    def _visit_(value: JsValue, remaining: Vector[String]): JsValue =
      if (remaining.isEmpty) update(value) else value match {
        case objectvalue: JsObject => objectvalue + (remaining.head -> _visit_(objectvalue.value(remaining.head), remaining.tail))
        case JsArray(values) => JsArray(values.updated(remaining.head.toInt, _visit_(values(remaining.head.toInt), remaining.tail)))
        case _ => throw new IllegalArgumentException("The literal mutation path requires a container.")
      }
    _visit_(root, path).as[JsObject]
  }
  private def _keys(value: JsValue, reverse: Boolean): JsValue = value match {
    case objectvalue: JsObject =>
      val fields = objectvalue.fields.sortBy(_._1)
      JsObject((if (reverse) fields.reverse else fields).map { case (key, item) => key -> _keys(item, reverse) })
    case JsArray(values) => JsArray(values.map(_keys(_, reverse)))
    case _ => value
  }
  private def _diagnostic(kind: ClassificationDiagnosticKind, path: String, detail: String): ClassificationDiagnostic =
    ClassificationDiagnostic(kind, path, detail, None)
  private def _filename(absent: Boolean): String =
    if (absent) "classification-metadata-v1-absence.json" else "classification-metadata-v1-declared.json"
  private def _resource(name: String): JsObject = {
    val stream = getClass.getResourceAsStream("/cozy/modeler/" + name)
    require(stream != null, "The authored fixture resource is required.")
    val source = Source.fromInputStream(stream, "UTF-8")
    try Json.parse(source.mkString).as[JsObject] finally source.close()
  }
  private def _baseline(): CmlStructureMetadata.Graph = _right(CmlStructureMetadata.read(_resource("structure-metadata-v1-declared.json")))
  private def _right[A, B](result: Either[A, B]): B = result match {
    case Right(value) => value
    case Left(_) => fail("The authored fixture must be admitted before observing its semantics.")
  }
}
