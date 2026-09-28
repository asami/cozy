package cozy.modeler

import org.scalacheck.{Gen, Prop, Test}
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import play.api.libs.json.{JsArray, JsBoolean, JsNull, JsNumber, JsObject, JsString, JsValue, Json}

import CmlSemanticFoundation._
import CmlStructureMetadata._

/*
 * @since   Sep. 28, 2026
 * @version Sep. 28, 2026
 * @author  ASAMI, Tomoharu
 */
final class CmlStructureMetadataSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  "CML Structure metadata" should {
    "STR-01 retain six kinds and qualified identities" in {
      Given("an admitted Catalog with six distinct same-named Structure records")
      val graph = _graph()
      When("the typed producer renders and the JSON consumer reconstructs the Graph")
      val result = CmlStructureMetadata.read(CmlStructureMetadata.toJson(graph))
      Then("all static and relation tags remain distinct by their qualified identities")
      _graph_result(result).elements.map(_.kind) shouldBe Vector(Entity, Value, Aggregate)
      _graph_result(result).relations.map(_.kind) shouldBe Vector(Composition, Aggregation, Association)
      _graph_result(result).catalog.elements.map(_.identity) shouldBe Vector(
        Present(ModelElementId("model.example", "entity:item")), Present(ModelElementId("model.example", "value:same")),
        Present(ModelElementId("model.example", "aggregate:same")), Present(ModelElementId("model.example", "composition:same")),
        Present(ModelElementId("model.example", "aggregation:same")), Present(ModelElementId("model.example", "association:same"))
      )
      _graph_result(result).catalog.elements.map(_.source) shouldBe graph.catalog.elements.map(_.source)
      _graph_result(result).catalog.elements.map(_.references) shouldBe graph.catalog.elements.map(_.references)
    }

    "STR-04 round-trip core records and opaque extensions" in {
      Given("a graph with an unrelated opaque extension")
      val graph = _graph(extensions = Json.obj("org.example.opaque" -> Json.obj("any" -> Json.arr(JsNull, "retained"))))
      When("canonical JSON is rendered and read as an actual JsValue")
      val result = CmlStructureMetadata.read(Json.parse(CmlStructureMetadata.canonicalJson(graph)))
      Then("core carriers and the opaque extension survive")
      _graph_result(result).catalog.elements shouldBe graph.catalog.elements
      _graph_result(result).elements shouldBe graph.elements
      _graph_result(result).relations shouldBe graph.relations
      _graph_result(result).envelope.extensions.value("org.example.opaque") shouldBe Json.obj("any" -> Json.arr(JsNull, "retained"))
    }

    "STR-05 reject an embedded core contradiction" in {
      Given("a rendered graph whose embedded projection name is contradicted")
      val root = CmlStructureMetadata.toJson(_graph())
      val broken = _replace_embedded_name(root, "contradiction")
      When("the strict consumer reads the changed JsValue")
      val result = CmlStructureMetadata.read(broken)
      Then("exact core binding fails closed")
      _first(result).kind shouldBe InvalidCoreBinding
      Given("the same admitted core record with a contradicted identity and provenance")
      val changedidentity = _replace_embedded_field(root, "identity", Json.obj("status" -> "present", "value" -> Json.obj("modelId" -> "model.example", "elementId" -> "other")))
      val changedsource = _replace_embedded_field(root, "source", Json.obj("authorityId" -> "model.example", "path" -> "src/main/cml/other.cml", "sha256" -> ("b" * 64), "line" -> 1))
      When("the strict consumer reads each contradicted core binding")
      val contradictions = Vector(changedidentity, changedsource).map(value => _first(CmlStructureMetadata.read(value)).kind)
      Then("every embedded identity or provenance contradiction rejects the exact core binding")
      contradictions shouldBe Vector(InvalidCoreBinding, InvalidCoreBinding)
    }

    "STR-06 reject missing duplicate and wrong-category projections" in {
      Given("a graph source with an omitted supported projection")
      val graph = _graph(); val originalrecords = graph.catalog.elements
      When("the factory receives only a partial projection set")
      val result = CmlStructureMetadata.build(graph.catalog, graph.elements.drop(1), graph.relations, Json.obj())
      Then("coverage fails without changing unrelated core records")
      _first(result).kind shouldBe MissingProjection
      Given("duplicate and wrong-kind supported projections")
      val duplicateprojections = graph.elements :+ graph.elements.head; val wrongkindprojections = graph.elements.updated(0, graph.elements.head.copy(kind = Value))
      When("the factory validates the coverage and declared categories")
      val duplicate = CmlStructureMetadata.build(graph.catalog, duplicateprojections, graph.relations, Json.obj())
      val wrongkind = CmlStructureMetadata.build(graph.catalog, wrongkindprojections, graph.relations, Json.obj())
      val diagnostics = Vector(_first(duplicate).kind, _first(wrongkind).kind)
      Then("duplicate and incompatible projections fail closed while the catalog stays unchanged")
      diagnostics shouldBe Vector(DuplicateProjection, InvalidCoreBinding)
      graph.catalog.elements shouldBe originalrecords
    }

    "STR-07 reject malformed child fields and erased null options" in {
      Given("a rendered graph with a missing child schema version")
      val root = CmlStructureMetadata.toJson(_graph())
      val broken = _structure(root) - "schemaVersion"
      When("the strict consumer reads the malformed Structure extension")
      val result = CmlStructureMetadata.read(_with_structure(root, broken))
      Then("the malformed child shape fails closed")
      _first(result).kind shouldBe InvalidShape
      Given("typed relations carrying erased null Boolean and Option[Int] payloads independently")
      val graph = _graph()
      val erasedboolean: Presence[Boolean] = Present(null).asInstanceOf[Presence[Boolean]]
      val erasedupper = Present(Cardinality(0, null))
      val malformedboolean = graph.relations.head.copy(sourceEndpoint = graph.relations.head.sourceEndpoint.copy(navigable = erasedboolean))
      val malformedupper = graph.relations.head.copy(sourceEndpoint = graph.relations.head.sourceEndpoint.copy(cardinality = erasedupper))
      When("the factory validates the hostile typed carriers")
      val typedresults = Vector(malformedboolean, malformedupper).map(value => CmlStructureMetadata.build(graph.catalog, graph.elements, graph.relations.updated(0, value), Json.obj()))
      Then("the admission boundary returns a typed diagnostic without unboxing null")
      typedresults.map(value => _first(value).kind) shouldBe Vector(InvalidShape, InvalidRelationSemantics)
    }

    "STR-08 reject its namespace collision and preserve opaque peers" in {
      Given("caller extensions containing the owned namespace")
      val graph = _graph()
      val extensions = Json.obj(namespace -> Json.obj(), "org.example.opaque" -> Json.obj("x" -> JsString("y")))
      When("the factory is asked to publish Structure")
      val result = CmlStructureMetadata.build(graph.catalog, graph.elements, graph.relations, extensions)
      Then("the collision is typed rather than overwritten")
      _first(result).kind shouldBe ExtensionConflict
    }

    "STR-08 preserve unrelated opaque peers" in {
      Given("the same graph with an unrelated opaque peer extension")
      val peergraph = _graph(extensions = Json.obj("org.example.opaque" -> Json.obj("x" -> JsString("y"))))
      When("the actual JSON consumer reads the peer-preserving envelope")
      val peerresult = CmlStructureMetadata.read(CmlStructureMetadata.toJson(peergraph))
      Then("the unrelated namespace remains opaque and is not consumed by Structure")
      peerresult match {
        case Right(reconstructed) => reconstructed.envelope.extensions.value("org.example.opaque") shouldBe Json.obj("x" -> JsString("y"))
        case Left(diagnostics) => diagnostics shouldBe Vector.empty
      }
    }

    "STR-09 distinguish an explicit empty graph from an absent namespace" in {
      Given("an explicitly empty admitted Catalog and Structure extension")
      val graph = _graph_for_empty_catalog()
      When("its JSON is consumed and then its namespace is removed")
      val accepted = CmlStructureMetadata.read(CmlStructureMetadata.toJson(graph))
      val absent = CmlStructureMetadata.read(_without_structure(CmlStructureMetadata.toJson(graph)))
      Then("the explicit empty graph succeeds while absence is an error")
      _graph_result(accepted).elements shouldBe Vector.empty
      _first(absent).kind shouldBe InvalidShape
    }

    "STR-10 distinguish validated external and invalid local endpoint references" in {
      Given("a relation with a valid external target and an invalid local target")
      val graph = _graph()
      val invalid = graph.relations.head.copy(sourceEndpoint = _endpoint(Present(ModelReference(ModelElementId("model.example", "missing"), Local))) )
      When("the factory validates the replacement relation")
      val result = CmlStructureMetadata.build(graph.catalog, graph.elements, graph.relations.updated(0, invalid), Json.obj())
      Then("the dangling local target is rejected without changing external reference semantics")
      _first(result).kind shouldBe InvalidRelationSemantics
      graph.relations.head.targetEndpoint.target shouldBe Present(ModelReference(ModelElementId("partner.example", "entity:external"), External))
      Given("external references whose model or element components are blank, padded, or control-bearing")
      val invalidids = Vector(
        ModelElementId("", "external"),
        ModelElementId(" model.example", "external"),
        ModelElementId("model.example\\u0000".replace("\\u0000", "\u0000"), "external"),
        ModelElementId("partner.example", ""),
        ModelElementId("partner.example", " external")
      )
      When("each reference is admitted without requiring a local catalog record")
      val diagnostics = invalidids.map { id =>
        val external = graph.relations.head.copy(targetEndpoint = _endpoint(Present(ModelReference(id, External))))
        _first(CmlStructureMetadata.build(graph.catalog, graph.elements, graph.relations.updated(0, external), Json.obj())).kind
      }
      Then("external identity components are validated before the External branch")
      diagnostics shouldBe Vector(InvalidShape, InvalidShape, InvalidShape, InvalidShape, InvalidShape)
      Given("local references to a relation record and an aggregate boundary targeting an Entity")
      val nonstatic = graph.relations.head.copy(sourceEndpoint = _endpoint(Present(ModelReference(ModelElementId("model.example", "composition:same"), Local))))
      val wrongaggregate = graph.elements.head.copy(aggregateBoundary = Present(AggregateBoundary(Present(ModelReference(ModelElementId("model.example", "entity:item"), Local)), Absent(NotDeclared, "No membership is declared."))))
      When("the typed factory resolves the local reference categories")
      val localdiagnostics = Vector(
        _first(CmlStructureMetadata.build(graph.catalog, graph.elements, graph.relations.updated(0, nonstatic), Json.obj())).kind,
        _first(CmlStructureMetadata.build(graph.catalog, graph.elements.updated(0, wrongaggregate), graph.relations, Json.obj())).kind
      )
      Then("only admitted static Local targets and aggregate targets are accepted")
      localdiagnostics shouldBe Vector(InvalidRelationSemantics, InvalidRelationSemantics)
    }

    "STR-11 preserve false empty and all closed nested absence branches" in {
      Given("declared false, an empty lifecycle vector, and every closed nested absence reason")
      val graph = _graph()
      val relation = graph.relations.head.copy(
        sourceEndpoint = graph.relations.head.sourceEndpoint.copy(
          target = Absent(NotDeclared, "No target is declared."),
          role = Absent(NotRepresented, "No role is represented."),
          cardinality = Absent(Unsupported, "Cardinality is unsupported."),
          navigable = Absent(NotApplicable, "Navigability is not applicable.")
        ),
        semantics = _semantics.copy(aggregateBoundary = Absent(NotRepresented, "No aggregate boundary is represented."))
      )
      val elements = graph.elements.updated(0, graph.elements.head.copy(aggregateBoundary = Absent(Unsupported, "Aggregate membership is unsupported.")))
      When("the graph is rendered and consumed")
      val reconstructed = _graph_result(CmlStructureMetadata.build(graph.catalog, elements, graph.relations.updated(0, relation), Json.obj()))
      val consumed = _graph_result(CmlStructureMetadata.read(CmlStructureMetadata.toJson(reconstructed))).relations.head
      val consumedelement = _graph_result(CmlStructureMetadata.read(CmlStructureMetadata.toJson(reconstructed))).elements.head
      Then("the explicit carriers remain distinguishable")
      consumed.semantics.independentExistence shouldBe Present(false)
      consumed.semantics.lifecyclePropagation shouldBe Present(Vector.empty)
      consumed.sourceEndpoint.role shouldBe Absent(NotRepresented, "No role is represented.")
      consumed.sourceEndpoint.cardinality shouldBe Absent(Unsupported, "Cardinality is unsupported.")
      consumed.sourceEndpoint.target shouldBe Absent(NotDeclared, "No target is declared.")
      consumedelement.aggregateBoundary shouldBe Absent(Unsupported, "Aggregate membership is unsupported.")
      Given("a nested absence with a null reason or padded detail")
      val invalidabsence = relation.copy(sourceEndpoint = relation.sourceEndpoint.copy(
        role = Absent(null, "No role is declared."),
        navigable = Absent(NotDeclared, " padded detail ")
      ))
      When("the factory validates the malformed absence carrier")
      val invalidresult = CmlStructureMetadata.build(graph.catalog, elements, graph.relations.updated(0, invalidabsence), Json.obj())
      Then("the invalid absence is rejected at its fixed nested path")
      _first(invalidresult).kind shouldBe InvalidRelationSemantics
    }

    "STR-12 return safe deterministic diagnostics for hostile payloads" in {
      Given("a rendered graph with an unknown hostile Structure key")
      val root = CmlStructureMetadata.toJson(_graph())
      val broken = _structure(root) + ("<hostile-key>" -> JsString("payload"))
      When("the strict consumer decodes the payload")
      val result = CmlStructureMetadata.read(_with_structure(root, broken))
      val repeated = CmlStructureMetadata.read(_with_structure(root, broken))
      Then("the diagnostic has a fixed coordinate and does not echo the hostile key")
      val diagnostic = _first(result)
      diagnostic.kind shouldBe InvalidShape
      diagnostic.path shouldBe "extensions.cozy.cml.structure"
      diagnostic.detail.contains("hostile") shouldBe false
      repeated shouldBe result
      Given("a null Catalog supplied directly to the Structure validator")
      val catalog: Catalog = null
      When("the validation boundary receives the missing prerequisite")
      val catalogresult = CmlStructureMetadataValidation.validate(catalog, Vector.empty, Vector.empty)
      Then("it returns a deterministic typed shape diagnostic before dependent lookup")
      catalogresult shouldBe Left(Vector(StructureDiagnostic(InvalidShape, "catalog", "The Structure value does not match the required v1 shape.", None)))
    }

    "STR-13 preserve identity and canonical behavior under ScalaCheck variations" in {
      Given("one hundred generated qualified identity, array-order, source-detail, collision, and object-key insertion variations")
      val variations = for {
        suffix <- Gen.nonEmptyListOf(Gen.oneOf('a', 'b', 'α', '名', ':', '-')).map(_.mkString)
        coreorder <- Gen.oneOf(true, false)
        elementorder <- Gen.oneOf(true, false)
        relationorder <- Gen.oneOf(true, false)
        extensionorder <- Gen.oneOf(true, false)
        displayname <- Gen.oneOf("Same", "別名", "Case-" + suffix)
        line <- Gen.option(Gen.choose(1, 999))
        absencedetail <- Gen.nonEmptyListOf(Gen.oneOf('d', 'e', 't', 'a', 'i', 'l', 'α')).map(_.mkString)
        collisionorder <- Gen.oneOf(true, false)
      } yield (suffix, coreorder, elementorder, relationorder, extensionorder, displayname, line, absencedetail, collisionorder)
      val property = Prop.forAll(variations) {
        case (suffix, coreorder, elementorder, relationorder, extensionorder, displayname, line, absencedetail, collisionorder) =>
          val extensions = _extensions(suffix, extensionorder)
          val graph = _graph(
            firstid = "entity:" + suffix,
            extensions = extensions,
            displayname = displayname,
            sourcepath = "src/main/cml/" + suffix + ".cml",
            sourceline = line,
            absencedetail = absencedetail
          )
          val catalog = if (coreorder) _catalog_from(graph.catalog.elements.reverse) else graph.catalog
          val elements = (if (elementorder) graph.elements.reverse else graph.elements).map(value => _rebind(catalog, value))
          val relations = (if (relationorder) graph.relations.reverse else graph.relations).map(value => _rebind(catalog, value))
          val equivalentextensions = _extensions(suffix, !extensionorder)
          val collisiongraph = _collision_graph(suffix, collisionorder)
          val collisionconsumed = CmlStructureMetadata.read(Json.parse(CmlStructureMetadata.canonicalJson(collisiongraph))) match {
            case Right(reconstructed) => reconstructed.catalog.elements == collisiongraph.catalog.elements && reconstructed.catalog.terms == collisiongraph.catalog.terms && reconstructed.elements == collisiongraph.elements && reconstructed.catalog.element(ModelElementId("a:b", "c" + suffix)).isRight && reconstructed.catalog.element(ModelElementId("a", "b:c" + suffix)).isRight && reconstructed.catalog.term(TermId("a:b", "c" + suffix)).isRight
            case Left(_) => false
          }
          CmlStructureMetadata.build(catalog, elements, relations, extensions) match {
            case Right(published) => CmlStructureMetadata.read(Json.parse(CmlStructureMetadata.canonicalJson(published))) match {
              case Right(reconstructed) =>
                reconstructed.catalog.elements == catalog.elements &&
                  reconstructed.catalog.elements.exists(_.identity == Present(ModelElementId("model.example", "entity:" + suffix))) &&
                  reconstructed.elements.map(_.kind) == elements.map(_.kind) &&
                  reconstructed.relations.map(_.kind) == relations.map(_.kind) &&
                  reconstructed.catalog.elements.forall(_.name == displayname) &&
                  reconstructed.catalog.elements.forall(_.source.path == "src/main/cml/" + suffix + ".cml") &&
                  reconstructed.catalog.elements.forall(_.source.line == line) &&
                  reconstructed.catalog.elements.forall(_.references == Absent(NotDeclared, absencedetail)) &&
                  collisionconsumed &&
                  (CmlStructureMetadata.build(catalog, elements, relations, equivalentextensions) match {
                    case Right(equivalent) => CmlStructureMetadata.canonicalJson(published) == CmlStructureMetadata.canonicalJson(equivalent)
                    case Left(_) => false
                  })
              case Left(_) => false
            }
            case Left(_) => false
          }
      }
      When("the ScalaCheck property is evaluated with at least one hundred successful samples")
      val result = Test.check(Test.Parameters.default.withMinSuccessfulTests(100), property)
      Then("all samples preserve their intended identity through canonical publication")
      result.passed shouldBe true
    }

    "STR-02 keep modeled delimiter collisions out of the Term domain" in {
      Given("two modeled identities whose delimiter-qualified concatenations collide and one Term with the same components")
      val records = Vector(
        ElementRecord(Present(ModelElementId("a:b", "c")), "entity", "Collision", _source("a:b"), Absent(NotDeclared, "No semantic references are declared.")),
        ElementRecord(Present(ModelElementId("a", "b:c")), "value", "Collision", _source("a"), Absent(NotDeclared, "No semantic references are declared."))
      )
      val term = TermRecord(TermId("a:b", "c"), _source("a:b"))
      val catalog = _catalog_from(records, Vector(term))
      val elements = Vector(
        ElementProjection(records(0), Entity, Absent(NotApplicable, "No aggregate boundary applies.")),
        ElementProjection(records(1), Value, Absent(NotApplicable, "No aggregate boundary applies."))
      )
      When("the typed factory publishes and the canonical JSON consumer reconstructs the collision graph")
      val result = CmlStructureMetadata.build(catalog, elements, Vector.empty, Json.obj())
      val published = _graph_result(result)
      val consumed = _graph_result(CmlStructureMetadata.read(Json.parse(CmlStructureMetadata.canonicalJson(published))))
      Then("each namespace and delimiter identity remains independently addressable with exact catalog, term, source, and projection bytes")
      consumed.catalog.elements shouldBe catalog.elements
      consumed.catalog.terms shouldBe catalog.terms
      consumed.elements shouldBe elements
      consumed.catalog.element(ModelElementId("a:b", "c")).isRight shouldBe true
      consumed.catalog.element(ModelElementId("a", "b:c")).isRight shouldBe true
      consumed.catalog.term(TermId("a:b", "c")).isRight shouldBe true
    }

    "STR-03 preserve duplicate anonymous multiplicity and reject missing or extra witnesses" in {
      Given("two identical identity-absent admitted records plus one different-name anonymous record and one projection for each record")
      val base = _graph()
      val anonymous = ElementRecord(Absent(NotRepresented, "Anonymous model declaration."), "value", "anonymous", _source("model.example"), Absent(NotDeclared, "No semantic references are declared.")); val distinctanonymous = anonymous.copy(name = "anonymous-other")
      val witnesses = Vector(anonymous, anonymous, distinctanonymous)
      val catalog = _catalog_from(base.catalog.elements ++ witnesses)
      val projections = base.elements ++ witnesses.map(record => ElementProjection(record, Value, Absent(NotApplicable, "No aggregate boundary applies.")))
      When("the producer admits the complete anonymous multiplicity")
      val accepted = CmlStructureMetadata.build(catalog, projections, base.relations, Json.obj())
      val acceptedgraph = _graph_result(accepted)
      val consumed = _graph_result(CmlStructureMetadata.read(Json.parse(CmlStructureMetadata.canonicalJson(acceptedgraph))))
      Then("both identical anonymous records remain observable without an invented identity after canonical consumption")
      consumed.elements.count(_.element == anonymous) shouldBe 2; consumed.elements.count(_.element == distinctanonymous) shouldBe 1; consumed.elements shouldBe projections; consumed.catalog.elements shouldBe catalog.elements; consumed.catalog.elements.count(_.identity == anonymous.identity) shouldBe witnesses.size
      consumed.catalog.element(ModelElementId("model.example", "anonymous")) match { case Left(diagnostic) => diagnostic.kind shouldBe DanglingReference; case Right(record) => record shouldBe null }
      Given("the same catalog with identical and distinct anonymous projection witnesses removed or added")
      val multiplicitycases: Vector[(String, Vector[ElementProjection], StructureDiagnosticKind)] = Vector(("missing-identical", projections.patch(base.elements.size, Vector.empty, 1), MissingProjection), ("extra-identical", projections.patch(base.elements.size, Vector(projections(base.elements.size)), 0), DuplicateProjection), ("missing-distinct", projections.dropRight(1), MissingProjection), ("extra-distinct", projections :+ projections.last, DuplicateProjection))
      When("the factory validates each multiplicity mismatch")
      val multiplicityresults = multiplicitycases.map { case (_, values, expected) => (_first(CmlStructureMetadata.build(catalog, values, base.relations, Json.obj())).kind, expected) }
      Then("anonymous full-record multiplicity is required exactly")
      multiplicityresults.foreach { case (actual, expected) => actual shouldBe expected }
    }

    "STR-04 retain a nonempty semantic reference with its full provenance" in {
      Given("an admitted graph containing a declared semantic reference with target, boundary, qualifiers, label, source, and origin")
      val graph = _graph_with_reference()
      When("the typed graph is rendered to canonical JSON and consumed through Json.parse")
      val result = CmlStructureMetadata.read(Json.parse(CmlStructureMetadata.canonicalJson(graph)))
      Then("the exact reference and every opaque core carrier survive")
      val reconstructed = _graph_result(result)
      reconstructed.catalog.elements shouldBe graph.catalog.elements
      reconstructed.catalog.elements.head.references shouldBe graph.catalog.elements.head.references
      reconstructed.elements shouldBe graph.elements
      reconstructed.relations shouldBe graph.relations
    }

    "STR-05 reject source, identity, name, and reference contradictions" in {
      Given("a graph with one admitted element record and a nonempty semantic reference")
      val root = CmlStructureMetadata.toJson(_graph_with_reference())
      val changedreference = _replace_embedded_field(root, "references", Json.obj("status" -> "absent", "reason" -> "not-declared", "detail" -> "No semantic references are declared."))
      val changedname = _replace_embedded_name(root, "Contradicted")
      val changedsource = _replace_embedded_source_field(root, "path", JsString("src/main/cml/other.cml"))
      val changedidentity = _replace_embedded_field(root, "identity", Json.obj("status" -> "present", "value" -> Json.obj("modelId" -> "model.example", "elementId" -> "entity:other")))
      When("the actual JSON consumer reads each contradicted embedded record")
      val diagnostics = Vector(changedreference, changedname, changedsource, changedidentity).map(value => _first(CmlStructureMetadata.read(value)).kind)
      Then("every core identity, source, display name, and reference contradiction fails exact binding")
      diagnostics shouldBe Vector(InvalidCoreBinding, InvalidCoreBinding, InvalidCoreBinding, InvalidCoreBinding)
    }

    "STR-06 leave unrelated admitted core kinds untouched" in {
      Given("a complete Structure graph plus an admitted unsupported core kind with no Structure projection")
      val graph = _graph()
      val unrelated = ElementRecord(Present(ModelElementId("model.example", "event:unrelated")), "event", "Unrelated", _source("model.example"), Absent(NotDeclared, "No semantic references are declared."))
      val catalog = _catalog_from(graph.catalog.elements :+ unrelated, Vector(TermRecord(TermId("vocabulary.example", "term:unrelated"), _source("vocabulary.example"))))
      When("the factory publishes only the supported six projections and the canonical consumer reads them")
      val result = CmlStructureMetadata.build(catalog, graph.elements, graph.relations, Json.obj())
      val published = _graph_result(result)
      val reconstructed = _graph_result(CmlStructureMetadata.read(Json.parse(CmlStructureMetadata.canonicalJson(published))))
      Then("the unrelated admitted core record remains present and unprojected after canonical consumption")
      reconstructed.catalog.elements.last shouldBe unrelated
      reconstructed.catalog.terms shouldBe catalog.terms
      reconstructed.elements.map(_.element.kind).contains("event") shouldBe false
      reconstructed.relations.map(_.element.kind).contains("event") shouldBe false
    }

    "STR-07 reject every strict nested wire field and malformed scalar branch" in {
      Given("a canonical Structure document whose nested carriers contain present and absent branches")
      val root = CmlStructureMetadata.toJson(_graph_with_nested_present_boundaries())
      val variants = _strict_wire_variants(root)
      When("the actual JSON consumer reads each missing, extra, unknown, null, and wrong-scalar shape")
      val diagnostics = variants.map { case (label, value, expected) => (label, _first(CmlStructureMetadata.read(value)).kind, expected) }
      Then("every strict child boundary fails closed at a typed diagnostic")
      diagnostics.foreach { case (label, actual, expected) => label.nonEmpty shouldBe true; actual shouldBe expected }
      diagnostics.count(_._2 == UnsupportedSchemaVersion) shouldBe 1
      diagnostics.count(_._2 == InvalidShape) shouldBe variants.count(_._3 == InvalidShape)
    }

    "STR-10 preserve external IDs and reject malformed or local references" in {
      Given("valid external model IDs containing Unicode, case, and delimiters, plus invalid null, empty, padded, and control components")
      val graph = _graph()
      val validids = Vector(ModelElementId("Partner.Example", "External:名/Case"), ModelElementId("partner.example", "entity:外部"))
      When("typed and actual-wire external references are admitted and canonicalized")
      val typedgraphs = validids.map { identity =>
        val relation = graph.relations.head.copy(targetEndpoint = _endpoint(Present(ModelReference(identity, External))))
        val built = _graph_result(CmlStructureMetadata.build(graph.catalog, graph.elements, graph.relations.updated(0, relation), Json.obj()))
        _graph_result(CmlStructureMetadata.read(Json.parse(CmlStructureMetadata.canonicalJson(built))))
      }
      val wiregraphs = validids.map { identity =>
        val value = _replace_relation_target_identity(CmlStructureMetadata.toJson(graph), identity)
        val admitted = _graph_result(CmlStructureMetadata.read(value))
        _graph_result(CmlStructureMetadata.read(Json.parse(CmlStructureMetadata.canonicalJson(admitted))))
      }
      Then("each external reference round-trips exactly without a local definition or ghost core record")
      (typedgraphs ++ wiregraphs).zip(validids ++ validids).foreach { case (value, identity) =>
        value.relations.head.targetEndpoint.target shouldBe Present(ModelReference(identity, External))
        value.elements shouldBe graph.elements
        value.relations.head.copy(targetEndpoint = graph.relations.head.targetEndpoint) shouldBe graph.relations.head
        value.relations.tail shouldBe graph.relations.tail
        value.catalog.elements shouldBe graph.catalog.elements
        value.catalog.element(identity) match {
          case Left(diagnostic) => diagnostic.kind shouldBe DanglingReference
          case Right(record) => record shouldBe null
        }
      }
      Given("all null, empty, leading-whitespace, trailing-whitespace, and control variants for both external ID components")
      val invalidids = Vector[ModelElementId](
        ModelElementId(null, "external"), ModelElementId("", "external"), ModelElementId(" model.example", "external"),
        ModelElementId("model.example ", "external"), ModelElementId("model.example\u0000".replace("\\u0000", "\u0000"), "external"),
        ModelElementId("partner.example", null), ModelElementId("partner.example", ""), ModelElementId("partner.example", " external"),
        ModelElementId("partner.example", "external "), ModelElementId("partner.example", "external\u0000".replace("\\u0000", "\u0000"))
      )
      When("each malformed reference crosses typed and actual-wire admission")
      val typedinvalid = invalidids.map { identity =>
        val relation = graph.relations.head.copy(targetEndpoint = _endpoint(Present(ModelReference(identity, External))))
        _first(CmlStructureMetadata.build(graph.catalog, graph.elements, graph.relations.updated(0, relation), Json.obj())).kind
      }
      val wireinvalid = invalidids.map(identity => _first(CmlStructureMetadata.read(_replace_relation_target_identity(CmlStructureMetadata.toJson(graph), identity))).kind)
      val nulltyped = Vector(
        graph.relations.head.copy(targetEndpoint = graph.relations.head.targetEndpoint.copy(target = Present(null).asInstanceOf[Presence[ModelReference]])),
        graph.relations.head.copy(targetEndpoint = graph.relations.head.targetEndpoint.copy(target = Present(ModelReference(null, External)).asInstanceOf[Presence[ModelReference]])),
        graph.relations.head.copy(targetEndpoint = graph.relations.head.targetEndpoint.copy(target = Present(ModelReference(ModelElementId("partner.example", "external"), null)).asInstanceOf[Presence[ModelReference]]))
      ).map(value => _first(CmlStructureMetadata.build(graph.catalog, graph.elements, graph.relations.updated(0, value), Json.obj())).kind)
      val nullwire = Vector(
        _replace_relation_target_value(CmlStructureMetadata.toJson(graph), JsNull),
        _replace_relation_target_reference_field(CmlStructureMetadata.toJson(graph), "identity", JsNull),
        _replace_relation_target_reference_field(CmlStructureMetadata.toJson(graph), "boundary", JsNull)
      ).map(value => _first(CmlStructureMetadata.read(value)).kind)
      Then("invalid components and null reference paths are rejected before any External local-definition exception")
      (typedinvalid ++ wireinvalid) shouldBe Vector.fill(invalidids.size * 2)(InvalidShape)
      nulltyped shouldBe Vector.fill(3)(InvalidShape)
      nullwire shouldBe Vector.fill(3)(InvalidShape)
    }

    "STR-11 retain all four absence reasons at every nested carrier" in {
      Given("a graph with present aggregate-boundary carriers and four valid absence reason/detail pairs")
      val graph = _graph_with_nested_present_boundaries()
      val absences: Vector[Absent] = Vector(
        Absent(NotDeclared, "No value is declared."),
        Absent(NotRepresented, "No value is represented."),
        Absent(Unsupported, "This value is unsupported."),
        Absent(NotApplicable, "This value is not applicable.")
      )
      val carriers: Vector[(String, (Graph, Absent) => (Vector[ElementProjection], Vector[RelationProjection]), Graph => Any, Vector[String])] = Vector(
        ("source.target", (value, absence) => _relation_vectors(value, relation => relation.copy(sourceEndpoint = relation.sourceEndpoint.copy(target = absence))), value => value.relations.head.sourceEndpoint.target, Vector("relations", "[0]", "sourceEndpoint", "target")),
        ("source.role", (value, absence) => _relation_vectors(value, relation => relation.copy(sourceEndpoint = relation.sourceEndpoint.copy(role = absence))), value => value.relations.head.sourceEndpoint.role, Vector("relations", "[0]", "sourceEndpoint", "role")),
        ("source.cardinality", (value, absence) => _relation_vectors(value, relation => relation.copy(sourceEndpoint = relation.sourceEndpoint.copy(cardinality = absence))), value => value.relations.head.sourceEndpoint.cardinality, Vector("relations", "[0]", "sourceEndpoint", "cardinality")),
        ("source.navigable", (value, absence) => _relation_vectors(value, relation => relation.copy(sourceEndpoint = relation.sourceEndpoint.copy(navigable = absence))), value => value.relations.head.sourceEndpoint.navigable, Vector("relations", "[0]", "sourceEndpoint", "navigable")),
        ("target.target", (value, absence) => _relation_vectors(value, relation => relation.copy(targetEndpoint = relation.targetEndpoint.copy(target = absence))), value => value.relations.head.targetEndpoint.target, Vector("relations", "[0]", "targetEndpoint", "target")),
        ("target.role", (value, absence) => _relation_vectors(value, relation => relation.copy(targetEndpoint = relation.targetEndpoint.copy(role = absence))), value => value.relations.head.targetEndpoint.role, Vector("relations", "[0]", "targetEndpoint", "role")),
        ("target.cardinality", (value, absence) => _relation_vectors(value, relation => relation.copy(targetEndpoint = relation.targetEndpoint.copy(cardinality = absence))), value => value.relations.head.targetEndpoint.cardinality, Vector("relations", "[0]", "targetEndpoint", "cardinality")),
        ("target.navigable", (value, absence) => _relation_vectors(value, relation => relation.copy(targetEndpoint = relation.targetEndpoint.copy(navigable = absence))), value => value.relations.head.targetEndpoint.navigable, Vector("relations", "[0]", "targetEndpoint", "navigable")),
        ("semantics.ownership", (value, absence) => _relation_vectors(value, relation => relation.copy(semantics = relation.semantics.copy(ownership = absence))), value => value.relations.head.semantics.ownership, Vector("relations", "[0]", "semantics", "ownership")),
        ("semantics.independentExistence", (value, absence) => _relation_vectors(value, relation => relation.copy(semantics = relation.semantics.copy(independentExistence = absence))), value => value.relations.head.semantics.independentExistence, Vector("relations", "[0]", "semantics", "independentExistence")),
        ("semantics.createPolicy", (value, absence) => _relation_vectors(value, relation => relation.copy(semantics = relation.semantics.copy(createPolicy = absence))), value => value.relations.head.semantics.createPolicy, Vector("relations", "[0]", "semantics", "createPolicy")),
        ("semantics.deletePolicy", (value, absence) => _relation_vectors(value, relation => relation.copy(semantics = relation.semantics.copy(deletePolicy = absence))), value => value.relations.head.semantics.deletePolicy, Vector("relations", "[0]", "semantics", "deletePolicy")),
        ("semantics.reassignment", (value, absence) => _relation_vectors(value, relation => relation.copy(semantics = relation.semantics.copy(reassignment = absence))), value => value.relations.head.semantics.reassignment, Vector("relations", "[0]", "semantics", "reassignment")),
        ("semantics.reparenting", (value, absence) => _relation_vectors(value, relation => relation.copy(semantics = relation.semantics.copy(reparenting = absence))), value => value.relations.head.semantics.reparenting, Vector("relations", "[0]", "semantics", "reparenting")),
        ("semantics.lifecyclePropagation", (value, absence) => _relation_vectors(value, relation => relation.copy(semantics = relation.semantics.copy(lifecyclePropagation = absence))), value => value.relations.head.semantics.lifecyclePropagation, Vector("relations", "[0]", "semantics", "lifecyclePropagation")),
        ("semantics.aggregateBoundary", (value, absence) => _relation_vectors(value, relation => relation.copy(semantics = relation.semantics.copy(aggregateBoundary = absence))), value => value.relations.head.semantics.aggregateBoundary, Vector("relations", "[0]", "semantics", "aggregateBoundary")),
        ("element.aggregateBoundary", (value, absence) => _element_vectors(value, element => element.copy(aggregateBoundary = absence)), value => value.elements.head.aggregateBoundary, Vector("elements", "[0]", "aggregateBoundary")),
        ("element.aggregate", (value, absence) => _element_vectors(value, element => element.copy(aggregateBoundary = Present(AggregateBoundary(absence, Present("member"))))), value => value.elements.head.aggregateBoundary.asInstanceOf[Present[AggregateBoundary]].value.aggregate, Vector("elements", "[0]", "aggregateBoundary", "value", "aggregate")),
        ("element.membership", (value, absence) => _element_vectors(value, element => element.copy(aggregateBoundary = Present(AggregateBoundary(Present(ModelReference(ModelElementId("model.example", "aggregate:same"), Local)), absence)))), value => value.elements.head.aggregateBoundary.asInstanceOf[Present[AggregateBoundary]].value.membership, Vector("elements", "[0]", "aggregateBoundary", "value", "membership")),
        ("semantics.boundary.aggregate", (value, absence) => _relation_vectors(value, relation => relation.copy(semantics = relation.semantics.copy(aggregateBoundary = Present(AggregateBoundary(absence, Present("member")))))), value => value.relations.head.semantics.aggregateBoundary.asInstanceOf[Present[AggregateBoundary]].value.aggregate, Vector("relations", "[0]", "semantics", "aggregateBoundary", "value", "aggregate")),
        ("semantics.boundary.membership", (value, absence) => _relation_vectors(value, relation => relation.copy(semantics = relation.semantics.copy(aggregateBoundary = Present(AggregateBoundary(Present(ModelReference(ModelElementId("model.example", "aggregate:same"), Local)), absence))))), value => value.relations.head.semantics.aggregateBoundary.asInstanceOf[Present[AggregateBoundary]].value.membership, Vector("relations", "[0]", "semantics", "aggregateBoundary", "value", "membership"))
      )
      When("each typed absence carrier is built, canonicalized, parsed, and consumed")
      val observed = carriers.flatMap { case (_, change, readvalue, _) =>
        absences.map { absence =>
          val (elements, relations) = change(graph, absence)
          val published = _graph_result(CmlStructureMetadata.build(graph.catalog, elements, relations, Json.obj()))
          val consumed = _graph_result(CmlStructureMetadata.read(Json.parse(CmlStructureMetadata.canonicalJson(published))))
          (readvalue(consumed), absence)
        }
      }
      Then("every nested absence retains its exact reason and detail, including Present(false) and Present(Vector.empty)")
      observed.foreach { case (actual, expected) => actual shouldBe expected }
      graph.relations.head.semantics.independentExistence shouldBe Present(false)
      graph.relations.head.semantics.lifecyclePropagation shouldBe Present(Vector.empty)
      Given("the same reachable carriers with null reasons or null, empty, padded, and control-bearing details")
      val invaliddetails = Vector[String](null, "", " padded detail", "padded detail ", "bad\u0000detail".replace("\\u0000", "\u0000"))
      val invalidpayloads: Vector[(AbsenceReason, String)] = Vector((null, "valid absence detail")) ++ absences.flatMap(absence => invaliddetails.map(detail => (absence.reason, detail)))
      When("each invalid carrier crosses typed and actual-wire admission independently")
      val invalidresults = carriers.flatMap { case (_, change, _, route) => invalidpayloads.map { case (reason, detail) =>
        val absence = Absent(reason, detail)
        val (elements, relations) = change(graph, absence)
        val typed = _first(CmlStructureMetadata.build(graph.catalog, elements, relations, Json.obj())).kind
        val wire = _first(CmlStructureMetadata.read(_wire_absence(CmlStructureMetadata.toJson(graph), route, reason, detail))).kind
        (typed, wire, if (reason == null || detail == null) InvalidShape else InvalidRelationSemantics)
      }}
      Then("every invalid reason or detail is rejected with its exact typed and wire category")
      invalidresults.foreach { case (typed, wire, expectedwire) => typed shouldBe InvalidRelationSemantics; wire shouldBe expectedwire }
    }

    "STR-07 reject hostile typed nulls and malformed Presence branches" in {
      Given("a valid graph and independently erased null catalog, extension, projection, carrier, identity, option, vector, and branch values")
      val graph = _graph()
      val nullcatalog: Catalog = null
      val nullexensions: JsObject = null
      val nullelements: Vector[ElementProjection] = null
      val nullrelations: Vector[RelationProjection] = null
      val nullelement: ElementProjection = null
      val nullrelation: RelationProjection = null
      val nullkind: ElementKind = null
      val nullrelationkind: RelationKind = null
      val nullendpoint: Endpoint = null
      val nullsemantics: RelationSemantics = null
      val nullpresence: Presence[String] = null
      val nullreference: ModelReference = null
      val nullidentity: ModelElementId = null
      val nullboundary: ReferenceBoundary = null
      val nullaggregateboundary: Presence[AggregateBoundary] = null
      val nullcardinality: Cardinality = null
      val nulloption: Option[Int] = null
      val nulllifecycle: Vector[String] = null
      val nullmember: Vector[String] = Vector(null)
      val nullboolean: Presence[Boolean] = Present(null).asInstanceOf[Presence[Boolean]]
      val nullrefpresence: Presence[ModelReference] = Present(nullreference).asInstanceOf[Presence[ModelReference]]
      val nullcardinalitypresence: Presence[Cardinality] = Present(nullcardinality).asInstanceOf[Presence[Cardinality]]
      val nulltextpresence: Presence[String] = Present(null).asInstanceOf[Presence[String]]
      val nullvectorpresence: Presence[Vector[String]] = Present(nulllifecycle).asInstanceOf[Presence[Vector[String]]]
      val nullmemberpresence: Presence[Vector[String]] = Present(nullmember)
      val nullupper = Present(Cardinality(0, nulloption))
      val someupper: Option[Int] = Some(null).asInstanceOf[Option[Int]]
      val someupperpresence = Present(Cardinality(0, someupper))
      When("each hostile typed value crosses the admitted factory boundary")
      val results = Vector(
        ("catalog", () => CmlStructureMetadata.build(nullcatalog, graph.elements, graph.relations, Json.obj()), PublicationFailure),
        ("extensions", () => CmlStructureMetadata.build(graph.catalog, graph.elements, graph.relations, nullexensions), PublicationFailure),
        ("elements", () => CmlStructureMetadata.build(graph.catalog, nullelements, graph.relations, Json.obj()), InvalidShape),
        ("relations", () => CmlStructureMetadata.build(graph.catalog, graph.elements, nullrelations, Json.obj()), InvalidShape),
        ("element-projection", () => CmlStructureMetadata.build(graph.catalog, graph.elements.updated(0, nullelement), graph.relations, Json.obj()), InvalidShape),
        ("element-record", () => CmlStructureMetadata.build(graph.catalog, graph.elements.updated(0, graph.elements.head.copy(element = null)), graph.relations, Json.obj()), InvalidCoreBinding),
        ("relation-projection", () => CmlStructureMetadata.build(graph.catalog, graph.elements, graph.relations.updated(0, nullrelation), Json.obj()), InvalidShape),
        ("relation-record", () => CmlStructureMetadata.build(graph.catalog, graph.elements, graph.relations.updated(0, graph.relations.head.copy(element = null)), Json.obj()), InvalidCoreBinding),
        ("element-kind", () => CmlStructureMetadata.build(graph.catalog, graph.elements.updated(0, graph.elements.head.copy(kind = nullkind)), graph.relations, Json.obj()), InvalidCoreBinding),
        ("relation-kind", () => CmlStructureMetadata.build(graph.catalog, graph.elements, graph.relations.updated(0, graph.relations.head.copy(kind = nullrelationkind)), Json.obj()), InvalidCoreBinding),
        ("endpoint", () => CmlStructureMetadata.build(graph.catalog, graph.elements, graph.relations.updated(0, graph.relations.head.copy(sourceEndpoint = nullendpoint)), Json.obj()), InvalidShape),
        ("semantics", () => CmlStructureMetadata.build(graph.catalog, graph.elements, graph.relations.updated(0, graph.relations.head.copy(semantics = nullsemantics)), Json.obj()), InvalidShape),
        ("presence", () => CmlStructureMetadata.build(graph.catalog, graph.elements, graph.relations.updated(0, graph.relations.head.copy(sourceEndpoint = graph.relations.head.sourceEndpoint.copy(role = nulltextpresence))), Json.obj()), InvalidRelationSemantics),
        ("presence-null", () => CmlStructureMetadata.build(graph.catalog, graph.elements, graph.relations.updated(0, graph.relations.head.copy(sourceEndpoint = graph.relations.head.sourceEndpoint.copy(role = nullpresence))), Json.obj()), InvalidRelationSemantics),
        ("reference", () => CmlStructureMetadata.build(graph.catalog, graph.elements, graph.relations.updated(0, graph.relations.head.copy(sourceEndpoint = graph.relations.head.sourceEndpoint.copy(target = nullrefpresence))), Json.obj()), InvalidShape),
        ("identity", () => CmlStructureMetadata.build(graph.catalog, graph.elements, graph.relations.updated(0, graph.relations.head.copy(sourceEndpoint = graph.relations.head.sourceEndpoint.copy(target = Present(ModelReference(nullidentity, Local))))), Json.obj()), InvalidShape),
        ("boundary", () => CmlStructureMetadata.build(graph.catalog, graph.elements, graph.relations.updated(0, graph.relations.head.copy(sourceEndpoint = graph.relations.head.sourceEndpoint.copy(target = Present(ModelReference(ModelElementId("model.example", "entity:item"), nullboundary))))), Json.obj()), InvalidShape),
        ("cardinality", () => CmlStructureMetadata.build(graph.catalog, graph.elements, graph.relations.updated(0, graph.relations.head.copy(sourceEndpoint = graph.relations.head.sourceEndpoint.copy(cardinality = nullcardinalitypresence))), Json.obj()), InvalidRelationSemantics),
        ("option", () => CmlStructureMetadata.build(graph.catalog, graph.elements, graph.relations.updated(0, graph.relations.head.copy(sourceEndpoint = graph.relations.head.sourceEndpoint.copy(cardinality = nullupper))), Json.obj()), InvalidRelationSemantics),
        ("some-null-option", () => CmlStructureMetadata.build(graph.catalog, graph.elements, graph.relations.updated(0, graph.relations.head.copy(sourceEndpoint = graph.relations.head.sourceEndpoint.copy(cardinality = someupperpresence))), Json.obj()), InvalidRelationSemantics),
        ("boolean", () => CmlStructureMetadata.build(graph.catalog, graph.elements, graph.relations.updated(0, graph.relations.head.copy(sourceEndpoint = graph.relations.head.sourceEndpoint.copy(navigable = nullboolean))), Json.obj()), InvalidShape),
        ("lifecycle", () => CmlStructureMetadata.build(graph.catalog, graph.elements, graph.relations.updated(0, graph.relations.head.copy(semantics = graph.relations.head.semantics.copy(lifecyclePropagation = nullvectorpresence))), Json.obj()), InvalidRelationSemantics),
        ("lifecycle-member", () => CmlStructureMetadata.build(graph.catalog, graph.elements, graph.relations.updated(0, graph.relations.head.copy(semantics = graph.relations.head.semantics.copy(lifecyclePropagation = nullmemberpresence))), Json.obj()), InvalidRelationSemantics),
        ("element-aggregateBoundary", () => CmlStructureMetadata.build(graph.catalog, graph.elements.updated(0, graph.elements.head.copy(aggregateBoundary = nullaggregateboundary)), graph.relations, Json.obj()), InvalidShape),
        ("element-present-aggregateBoundary", () => CmlStructureMetadata.build(graph.catalog, graph.elements.updated(0, graph.elements.head.copy(aggregateBoundary = Present(null).asInstanceOf[Presence[AggregateBoundary]])), graph.relations, Json.obj()), InvalidShape)
      )
      val evaluated = results.map { case (label, action, expected) => (label, action(), action(), expected) }
      Then("each hostile value returns a typed diagnostic without unboxing null or accepting a malformed branch")
      evaluated.foreach { case (_, first, repeated, expected) => val diagnostic = _first(first); diagnostic.kind shouldBe expected; first shouldBe repeated; diagnostic.path.nonEmpty shouldBe true; diagnostic.detail.nonEmpty shouldBe true }
    }

    "STR-12 repeat hostile and null input diagnostics without throwing or echoing payloads" in {
      Given("null roots and repeated hostile top-level and Structure payloads")
      val hostile = Json.obj("<hostile-value>" -> JsString("secret"))
      val repeatedbranches = _strict_wire_variants(CmlStructureMetadata.toJson(_graph_with_nested_present_boundaries())).collect {
        case (label, value, _) if label.contains("unknown") || label.contains("version") || label.contains("mixed") => value
      }
      val values = Vector[JsValue](null, hostile, _with_structure(CmlStructureMetadata.toJson(_graph()), _structure(CmlStructureMetadata.toJson(_graph())) + ("<hostile-value>" -> JsString("secret")))) ++ repeatedbranches
      When("the consumer receives each payload twice")
      val results = values.map(value => Vector(CmlStructureMetadata.read(value), CmlStructureMetadata.read(value)))
      val safe = results.map { pair => (pair.head, pair.last, _first(pair.head).detail, _first(pair.last).detail) }
      Then("each result is deterministic and its diagnostic detail never echoes hostile content")
      safe.foreach { case (first, last, detail, repeatedetail) =>
        first shouldBe last
        detail shouldBe repeatedetail
        detail.contains("hostile") shouldBe false
        detail.contains("secret") shouldBe false
      }
    }

    "STR-14 preserve directed endpoint payloads for every relation kind" in {
      Given("three relation projections with contrasting local static and external endpoint payloads")
      val graph = _graph(extensions = Json.obj("org.example.peer" -> Json.obj("marker" -> JsString("keep"))))
      val sourceids = Vector(ModelElementId("model.example", "entity:item"), ModelElementId("model.example", "value:same"), ModelElementId("model.example", "aggregate:same"))
      val targetids = Vector(ModelElementId("Partner.Example", "External:名/Case"), ModelElementId("partner.example", "external:α/CASE"), ModelElementId("PARTNER.example", "External:Case"))
      val relations = graph.relations.zipWithIndex.map { case (relationvalue, relationindex) =>
        val sourceendpoint = _endpoint(Present(ModelReference(sourceids(relationindex), Local))).copy(
          role = Present("Source:名/Case"), cardinality = Present(Cardinality(relationindex + 1, Some(relationindex + 2))), navigable = Present(true)
        )
        val targetendpoint = _endpoint(Present(ModelReference(targetids(relationindex), External))).copy(
          role = Present("target:α/CASE"), cardinality = Present(Cardinality(0, None)), navigable = Present(false)
        )
        relationvalue.copy(sourceEndpoint = sourceendpoint, targetEndpoint = targetendpoint)
      }
      When("the typed producer renders and the canonical JSON consumer reconstructs each relation")
      val published = _graph_result(CmlStructureMetadata.build(graph.catalog, graph.elements, relations, Json.obj("org.example.peer" -> Json.obj("marker" -> JsString("keep")))))
      val wire = (0 until 3).map(index => _wire_relation(CmlStructureMetadata.toJson(published), index))
      val consumed = _graph_result(CmlStructureMetadata.read(Json.parse(CmlStructureMetadata.canonicalJson(published))))
      Then("sourceEndpoint and targetEndpoint fields retain their independent literals and boundaries")
      wire.map(value => value.value("kind")) shouldBe Vector(JsString("composition"), JsString("aggregation"), JsString("association"))
      wire.map(value => value.value("sourceEndpoint").asInstanceOf[JsObject].value("target")) shouldBe Vector(
        _wire_reference_presence(Present(ModelReference(ModelElementId("model.example", "entity:item"), Local))),
        _wire_reference_presence(Present(ModelReference(ModelElementId("model.example", "value:same"), Local))),
        _wire_reference_presence(Present(ModelReference(ModelElementId("model.example", "aggregate:same"), Local)))
      )
      wire.map(value => value.value("targetEndpoint").asInstanceOf[JsObject].value("target")) shouldBe Vector(
        _wire_reference_presence(Present(ModelReference(ModelElementId("Partner.Example", "External:名/Case"), External))),
        _wire_reference_presence(Present(ModelReference(ModelElementId("partner.example", "external:α/CASE"), External))),
        _wire_reference_presence(Present(ModelReference(ModelElementId("PARTNER.example", "External:Case"), External)))
      )
      wire.map(value => value.value("sourceEndpoint").asInstanceOf[JsObject].value("role")) shouldBe Vector.fill(3)(_wire_present_text("Source:名/Case"))
      wire.map(value => value.value("targetEndpoint").asInstanceOf[JsObject].value("role")) shouldBe Vector.fill(3)(_wire_present_text("target:α/CASE"))
      wire.map(value => value.value("sourceEndpoint").asInstanceOf[JsObject].value("cardinality")) shouldBe Vector(
        _wire_present_cardinality(Cardinality(1, Some(2))), _wire_present_cardinality(Cardinality(2, Some(3))), _wire_present_cardinality(Cardinality(3, Some(4)))
      )
      wire.map(value => value.value("targetEndpoint").asInstanceOf[JsObject].value("cardinality")) shouldBe Vector.fill(3)(_wire_present_cardinality(Cardinality(0, None)))
      wire.map(value => value.value("sourceEndpoint").asInstanceOf[JsObject].value("navigable")) shouldBe Vector.fill(3)(_wire_present_boolean(true))
      wire.map(value => value.value("targetEndpoint").asInstanceOf[JsObject].value("navigable")) shouldBe Vector.fill(3)(_wire_present_boolean(false))
      consumed.elements shouldBe published.elements
      consumed.relations shouldBe published.relations
      consumed.catalog.elements shouldBe published.catalog.elements
      consumed.catalog.elements.map(_.source) shouldBe published.catalog.elements.map(_.source)
      consumed.envelope.extensions.value("org.example.peer") shouldBe Json.obj("marker" -> JsString("keep"))
    }

    "STR-15 preserve literal policies roles and memberships without normalization" in {
      Given("all three relation kinds with distinct Unicode, case-sensitive, spaced, and unusual admitted literals")
      val base = _graph()
      val aggregateidentity = ModelElementId("model.example", "aggregate:same")
      val elementboundary = Present(AggregateBoundary(Present(ModelReference(aggregateidentity, Local)), Present("Element Member 名/Case")))
      val elements = base.elements.map(value => value.copy(aggregateBoundary = elementboundary))
      val relations = base.relations.zipWithIndex.map { case (relationvalue, relationindex) =>
        val sourceendpoint = relationvalue.sourceEndpoint.copy(role = Present("Source Role α/Case " + relationindex))
        val targetendpoint = relationvalue.targetEndpoint.copy(role = Present("Target Role 名/CASE " + relationindex))
        val boundary = Present(AggregateBoundary(Present(ModelReference(aggregateidentity, Local)), Present("Relation Member α/Case " + relationindex)))
        val semantics = RelationSemantics(
          Present("Owner 名/Case " + relationindex), Present(relationindex % 2 == 0), Present("Create Policy α " + relationindex),
          Present("DELETE policy/Case " + relationindex), Present(true), Present(false), Present(Vector("Lifecycle 名 " + relationindex, "Case/Token")), boundary
        )
        relationvalue.copy(sourceEndpoint = sourceendpoint, targetEndpoint = targetendpoint, semantics = semantics)
      }
      When("the producer renders concrete wire values and the consumer reads canonical JSON")
      val published = _graph_result(CmlStructureMetadata.build(base.catalog, elements, relations, Json.obj()))
      val wire = (0 until 3).map(index => _wire_relation(CmlStructureMetadata.toJson(published), index))
      val consumed = _graph_result(CmlStructureMetadata.read(Json.parse(CmlStructureMetadata.canonicalJson(published))))
      Then("each literal remains exact at its independently named wire field and typed carrier")
      wire.map(value => value.value("sourceEndpoint").asInstanceOf[JsObject].value("role")) shouldBe Vector(
        _wire_present_text("Source Role α/Case 0"), _wire_present_text("Source Role α/Case 1"), _wire_present_text("Source Role α/Case 2")
      )
      wire.map(value => value.value("targetEndpoint").asInstanceOf[JsObject].value("role")) shouldBe Vector(
        _wire_present_text("Target Role 名/CASE 0"), _wire_present_text("Target Role 名/CASE 1"), _wire_present_text("Target Role 名/CASE 2")
      )
      wire.map(value => value.value("semantics").asInstanceOf[JsObject].value("ownership")) shouldBe Vector(
        _wire_present_text("Owner 名/Case 0"), _wire_present_text("Owner 名/Case 1"), _wire_present_text("Owner 名/Case 2")
      )
      wire.map(value => value.value("semantics").asInstanceOf[JsObject].value("createPolicy")) shouldBe Vector(
        _wire_present_text("Create Policy α 0"), _wire_present_text("Create Policy α 1"), _wire_present_text("Create Policy α 2")
      )
      wire.map(value => value.value("semantics").asInstanceOf[JsObject].value("deletePolicy")) shouldBe Vector(
        _wire_present_text("DELETE policy/Case 0"), _wire_present_text("DELETE policy/Case 1"), _wire_present_text("DELETE policy/Case 2")
      )
      wire.map(value => value.value("semantics").asInstanceOf[JsObject].value("lifecyclePropagation")) shouldBe Vector(
        _wire_present_strings(Vector("Lifecycle 名 0", "Case/Token")), _wire_present_strings(Vector("Lifecycle 名 1", "Case/Token")), _wire_present_strings(Vector("Lifecycle 名 2", "Case/Token"))
      )
      wire.map(value => value.value("semantics").asInstanceOf[JsObject].value("aggregateBoundary")) shouldBe Vector(
        _wire_present_boundary(AggregateBoundary(Present(ModelReference(aggregateidentity, Local)), Present("Relation Member α/Case 0"))),
        _wire_present_boundary(AggregateBoundary(Present(ModelReference(aggregateidentity, Local)), Present("Relation Member α/Case 1"))),
        _wire_present_boundary(AggregateBoundary(Present(ModelReference(aggregateidentity, Local)), Present("Relation Member α/Case 2")))
      )
      val elementwire = (0 until 3).map(index => _wire_element(CmlStructureMetadata.toJson(published), index))
      elementwire.map(value => value.value("aggregateBoundary")) shouldBe Vector.fill(3)(_wire_present_boundary(AggregateBoundary(Present(ModelReference(aggregateidentity, Local)), Present("Element Member 名/Case"))))
      consumed.elements shouldBe published.elements
      consumed.relations shouldBe published.relations
      Given("the same admitted literal graph and independently invalid text carriers")
      val invalidvalues = Vector[String](null, "", " leading", "trailing ", "bad\u0000text".replace("\\u0000", "\u0000"))
      val root = CmlStructureMetadata.toJson(published)
      val wireinputs = invalidvalues.flatMap(value => _invalid_literal_wires(root, value))
      When("each null, empty, padded, or control-bearing literal crosses typed and actual-wire admission")
      val typedinvalid = invalidvalues.flatMap(value => _invalid_literal_results(published, value)).map(value => _first(value).kind)
      val wireinvalid = wireinputs.map(value => _first(CmlStructureMetadata.read(value)).kind)
      Then("typed strings fail as InvalidRelationSemantics and wire null versus text failures stay distinct")
      typedinvalid shouldBe Vector.fill(invalidvalues.size * 7)(InvalidRelationSemantics)
      wireinvalid shouldBe invalidvalues.flatMap(value => Vector.fill(7)(if (value == null) InvalidShape else InvalidRelationSemantics))
    }

    "STR-16 retain all independent Boolean carriers across every relation kind" in {
      Given("all 32 assignments of TWO endpoint navigation plus THREE independent semantic Boolean carriers")
      val base = _graph()
      val assignments = for {
        relationindex <- 0 until 3
        bits <- 0 until 32
      } yield (relationindex, bits)
      When("each Boolean assignment is published, rendered as concrete JSON, and consumed")
      val observed = assignments.map { case (relationindex, bits) =>
        val sourcevalue = (bits & 1) != 0
        val targetvalue = (bits & 2) != 0
        val independentvalue = (bits & 4) != 0
        val reassignmentvalue = (bits & 8) != 0
        val reparentingvalue = (bits & 16) != 0
        val original = base.relations(relationindex)
        val relationvalue = original.copy(
          sourceEndpoint = original.sourceEndpoint.copy(navigable = Present(sourcevalue)),
          targetEndpoint = original.targetEndpoint.copy(navigable = Present(targetvalue)),
          semantics = original.semantics.copy(independentExistence = Present(independentvalue), reassignment = Present(reassignmentvalue), reparenting = Present(reparentingvalue))
        )
        val published = _rebuild(base, relations = base.relations.updated(relationindex, relationvalue))
        val wire = _wire_relation(CmlStructureMetadata.toJson(published), relationindex)
        val sourcewire = wire.value("sourceEndpoint").asInstanceOf[JsObject]
        val targetwire = wire.value("targetEndpoint").asInstanceOf[JsObject]
        val semanticswire = wire.value("semantics").asInstanceOf[JsObject]
        val consumed = _graph_result(CmlStructureMetadata.read(Json.parse(CmlStructureMetadata.canonicalJson(published)))).relations(relationindex)
        (sourcewire.value("navigable"), targetwire.value("navigable"), semanticswire.value("independentExistence"), semanticswire.value("reassignment"), semanticswire.value("reparenting"), consumed, sourcevalue, targetvalue, independentvalue, reassignmentvalue, reparentingvalue)
      }
      Then("true and false remain Present independently without opposite-end copying or absence conversion")
      observed.foreach { case (sourcewire, targetwire, independentwire, reassignmentwire, reparentingwire, consumed, sourceexpected, targetexpected, independentexpected, reassignmentexpected, reparentingexpected) =>
        sourcewire shouldBe _wire_present_boolean(sourceexpected)
        targetwire shouldBe _wire_present_boolean(targetexpected)
        independentwire shouldBe _wire_present_boolean(independentexpected)
        reassignmentwire shouldBe _wire_present_boolean(reassignmentexpected)
        reparentingwire shouldBe _wire_present_boolean(reparentingexpected)
        consumed.sourceEndpoint.navigable shouldBe Present(sourceexpected)
        consumed.targetEndpoint.navigable shouldBe Present(targetexpected)
        consumed.semantics.independentExistence shouldBe Present(independentexpected)
        consumed.semantics.reassignment shouldBe Present(reassignmentexpected)
        consumed.semantics.reparenting shouldBe Present(reparentingexpected)
      }
      observed.size shouldBe 96
    }

    "STR-17 preserve cardinality bounds and reject overflow at both endpoints" in {
      Given("six valid finite or explicitly unbounded cardinalities")
      val base = _graph()
      val validcardinalities = Vector(Cardinality(0, Some(0)), Cardinality(1, Some(1)), Cardinality(2, Some(7)), Cardinality(0, None), Cardinality(Int.MaxValue, Some(Int.MaxValue)), Cardinality(Int.MaxValue, None))
      val endpointnames = Vector("sourceEndpoint", "targetEndpoint")
      When("each endpoint cardinality is published with the opposite endpoint unchanged")
      val validobservations = for {
        relationindex <- 0 until 3
        endpointname <- endpointnames
        cardinality <- validcardinalities
      } yield {
        val relationvalue = _replace_endpoint_cardinality(base.relations(relationindex), endpointname, Present(cardinality))
        val published = _rebuild(base, relations = base.relations.updated(relationindex, relationvalue))
        val wireendpoint = _wire_relation_endpoint(CmlStructureMetadata.toJson(published), relationindex, endpointname)
        val consumed = _graph_result(CmlStructureMetadata.read(Json.parse(CmlStructureMetadata.canonicalJson(published)))).relations(relationindex)
        val actualendpoint = _relation_endpoint(consumed, endpointname)
        val oppositename = if (endpointname == "sourceEndpoint") "targetEndpoint" else "sourceEndpoint"
        (wireendpoint.value("cardinality"), actualendpoint.cardinality, cardinality, _relation_endpoint(consumed, oppositename), _relation_endpoint(base.relations(relationindex), oppositename))
      }
      Then("finite upper values and explicit null unbounded values round-trip as the selected typed cardinality")
      validobservations.foreach { case (wirecardinality, typedcardinality, expectedcardinality, opposite, expectedopposite) =>
        wirecardinality shouldBe _wire_present_cardinality(expectedcardinality)
        typedcardinality shouldBe Present(expectedcardinality)
        opposite shouldBe expectedopposite
      }
      Given("the same admitted graph with one invalid ordering or reversed-bound cardinality changed at a time")
      val invalidcardinalities = Vector(Cardinality(-1, Some(0)), Cardinality(1, Some(0)), Cardinality(0, Some(-1)))
      val typedinputs = for {
        relationindex <- 0 until 3
        endpointname <- endpointnames
        cardinality <- invalidcardinalities
      } yield (relationindex, endpointname, cardinality)
      val root = CmlStructureMetadata.toJson(base)
      val numericinvalids = Vector("2147483648", "-2147483649", "0.5").map(value => JsNumber(scala.math.BigDecimal(value)))
      val semanticwireinputs = typedinputs
      val overflowwireinputs = for {
        relationindex <- 0 until 3
        endpointname <- endpointnames
        fieldname <- Vector("lower", "upper")
        numericvalue <- numericinvalids
      } yield (relationindex, endpointname, fieldname, numericvalue)
      When("invalid typed bounds and out-of-range or fractional actual-wire numbers are consumed")
      val typedinvalid = typedinputs.map { case (relationindex, endpointname, cardinality) =>
        val relations = base.relations.updated(relationindex, _replace_endpoint_cardinality(base.relations(relationindex), endpointname, Present(cardinality)))
        _first(CmlStructureMetadata.build(base.catalog, base.elements, relations, Json.obj())).kind
      }
      val semanticwire = semanticwireinputs.map { case (relationindex, endpointname, cardinality) =>
        val value = _wire_presence_value(root, Vector("relations", "[" + relationindex + "]", endpointname, "cardinality"), _wire_cardinality_object(cardinality))
        _first(CmlStructureMetadata.read(value)).kind
      }
      val overflowwire = overflowwireinputs.map { case (relationindex, endpointname, fieldname, numericvalue) =>
        val value = _wire_cardinality_field(root, relationindex, endpointname, fieldname, numericvalue)
        _first(CmlStructureMetadata.read(value)).kind
      }
      Then("semantic ordering failures and shape failures remain typed without overflow")
      typedinvalid shouldBe Vector.fill(18)(InvalidRelationSemantics)
      semanticwire shouldBe Vector.fill(18)(InvalidRelationSemantics)
      overflowwire shouldBe Vector.fill(36)(InvalidShape)
    }

    "STR-18 preserve lifecycle order duplicates and explicit empty vectors" in {
      Given("four admitted lifecycle vectors including empty, ordered duplicates, and case-sensitive Unicode values")
      val base = _graph()
      val lifecyclevalues = Vector(Vector.empty[String], Vector("Create:α"), Vector("Delete", "Create", "Delete"), Vector("Case", "case", "名"))
      When("each lifecycle vector is rendered on each relation kind and consumed")
      val observations = for {
        relationindex <- 0 until 3
        lifecyclevalue <- lifecyclevalues
      } yield {
        val semantics = base.relations(relationindex).semantics.copy(lifecyclePropagation = Present(lifecyclevalue))
        val relationvalue = base.relations(relationindex).copy(semantics = semantics)
        val published = _rebuild(base, relations = base.relations.updated(relationindex, relationvalue))
        val wire = _wire_relation(CmlStructureMetadata.toJson(published), relationindex).value("semantics").asInstanceOf[JsObject].value("lifecyclePropagation")
        val consumed = _graph_result(CmlStructureMetadata.read(Json.parse(CmlStructureMetadata.canonicalJson(published)))).relations(relationindex).semantics.lifecyclePropagation
        (wire, consumed, lifecyclevalue)
      }
      Then("empty remains Present and every emitted array preserves order and duplicates")
      observations.foreach { case (wire, consumed, expected) => wire shouldBe _wire_present_strings(expected); consumed shouldBe Present(expected) }
      Given("the same admitted graph with one lifecycle member changed at a time")
      val invalidvalues = Vector[String](null, "", " leading", "trailing ", "bad\u0000member".replace("\\u0000", "\u0000"))
      val typedinputs = for {
        relationindex <- 0 until 3
        invalidvalue <- invalidvalues
      } yield (relationindex, invalidvalue)
      val wireinputs = invalidvalues.flatMap(value => (0 until 3).map(index => _wire_lifecycle_value(CmlStructureMetadata.toJson(base), index, if (value == null) JsArray(Vector(JsNull)) else JsArray(Vector(JsString(value))))))
      When("each null, empty, padded, or control-bearing member crosses typed and actual-wire admission")
      val typedinvalid = typedinputs.map { case (relationindex, invalidvalue) =>
        val semantics = base.relations(relationindex).semantics.copy(lifecyclePropagation = Present(Vector(invalidvalue)))
        val relations = base.relations.updated(relationindex, base.relations(relationindex).copy(semantics = semantics))
        _first(CmlStructureMetadata.build(base.catalog, base.elements, relations, Json.obj())).kind
      }
      val wireinvalid = wireinputs.map(value => _first(CmlStructureMetadata.read(value)).kind)
      Then("typed invalid members use relation semantics diagnostics while null wire members remain shape failures")
      typedinvalid shouldBe Vector.fill(15)(InvalidRelationSemantics)
      wireinvalid shouldBe invalidvalues.flatMap(value => Vector.fill(3)(if (value == null) InvalidShape else InvalidRelationSemantics))
    }

    "STR-19 preserve every present outer aggregate-boundary variant" in {
      Given("four closed absence reasons, local and external aggregate references, and nested membership branches")
      val base = _graph()
      val variants = _aggregate_boundary_variants()
      When("each of fourteen boundary variants is applied to every element and relation projection")
      val observations = for {
        projectionindex <- 0 until 3
        boundaryvalue <- variants
      } yield {
        val elementvalues = base.elements.updated(projectionindex, base.elements(projectionindex).copy(aggregateBoundary = boundaryvalue))
        val elementpublished = _graph_result(CmlStructureMetadata.build(base.catalog, elementvalues, base.relations, Json.obj()))
        val elementwire = _wire_element(CmlStructureMetadata.toJson(elementpublished), projectionindex).value("aggregateBoundary").asInstanceOf[JsObject]
        val elementconsumed = _graph_result(CmlStructureMetadata.read(Json.parse(CmlStructureMetadata.canonicalJson(elementpublished)))).elements(projectionindex).aggregateBoundary
        val relationsemantics = base.relations(projectionindex).semantics.copy(aggregateBoundary = boundaryvalue)
        val relationvalues = base.relations.updated(projectionindex, base.relations(projectionindex).copy(semantics = relationsemantics))
        val relationpublished = _graph_result(CmlStructureMetadata.build(base.catalog, base.elements, relationvalues, Json.obj()))
        val relationwire = _wire_relation(CmlStructureMetadata.toJson(relationpublished), projectionindex).value("semantics").asInstanceOf[JsObject].value("aggregateBoundary").asInstanceOf[JsObject]
        val relationconsumed = _graph_result(CmlStructureMetadata.read(Json.parse(CmlStructureMetadata.canonicalJson(relationpublished)))).relations(projectionindex).semantics.aggregateBoundary
        (elementwire, elementconsumed, relationwire, relationconsumed, boundaryvalue, elementpublished.catalog.elements, relationpublished.catalog.elements)
      }
      Then("outer and nested presence, details, and external boundaries remain exact without catalog mutation")
      observations.foreach { case (elementwire, elementconsumed, relationwire, relationconsumed, expected, elementcatalog, relationcatalog) =>
        elementwire shouldBe _wire_present_boundary(expected.asInstanceOf[Present[AggregateBoundary]].value)
        relationwire shouldBe _wire_present_boundary(expected.asInstanceOf[Present[AggregateBoundary]].value)
        elementconsumed shouldBe expected
        relationconsumed shouldBe expected
        elementcatalog shouldBe base.catalog.elements
        relationcatalog shouldBe base.catalog.elements
        expected match {
          case Present(boundaryvalue) => boundaryvalue.aggregate match {
            case Present(ModelReference(identity, External)) =>
              elementcatalog.find(_.identity == Present(identity)) shouldBe None
            case _ => ()
          }
          case _ => ()
        }
      }
      variants.size shouldBe 14
      (observations.size * 2) shouldBe 84
    }

    "STR-20 preserve mixed relation payloads under active property generation" in {
      Given("compositional generators for independent endpoints, semantic policies, cardinalities, lifecycle, references, and nested boundaries")
      val base = _graph(extensions = Json.obj("org.example.peer" -> Json.obj("marker" -> JsString("retain"))))
      val payloads = Gen.listOfN(3, _relation_payload_generator())
      val property = Prop.forAll(payloads) { generated =>
        val relations = base.relations.zip(generated.toVector).map { case (relationvalue, (sourceendpoint, targetendpoint, semantics)) =>
          relationvalue.copy(sourceEndpoint = sourceendpoint, targetEndpoint = targetendpoint, semantics = semantics)
        }
        CmlStructureMetadata.build(base.catalog, base.elements, relations, base.envelope.extensions - namespace) match {
          case Left(_) => false
          case Right(published) =>
            val canonical = CmlStructureMetadata.canonicalJson(published)
            CmlStructureMetadata.read(Json.parse(canonical)) match {
              case Left(_) => false
              case Right(consumed) =>
                consumed.elements == published.elements &&
                  consumed.relations == published.relations &&
                  consumed.catalog.elements == published.catalog.elements &&
                  consumed.catalog.terms == published.catalog.terms &&
                  consumed.envelope.extensions == published.envelope.extensions &&
                  CmlStructureMetadata.canonicalJson(consumed) == canonical
            }
        }
      }
      When("ScalaCheck evaluates the property with at least one hundred successful samples")
      val result = Test.check(Test.Parameters.default.withMinSuccessfulTests(100), property)
      Then("every admitted mixed graph survives typed publication, canonical JSON, strict consumption, and re-rendering")
      result.passed shouldBe true
    }
  }

  private def _graph(
    modelid: String = "model.example",
    firstid: String = "entity:item",
    extensions: JsObject = Json.obj(),
    displayname: String = "Same",
    sourcepath: String = "src/main/cml/example.cml",
    sourceline: Option[Int] = Some(1),
    absencedetail: String = "No semantic references are declared."
  ): Graph = {
    val catalog = _catalog(modelid, firstid, displayname, sourcepath, sourceline, absencedetail)
    val records = catalog.elements
    val elements = Vector(
      ElementProjection(records(0), Entity, Absent(NotApplicable, "No aggregate boundary applies.")),
      ElementProjection(records(1), Value, Absent(NotApplicable, "No aggregate boundary applies.")),
      ElementProjection(records(2), Aggregate, Absent(NotDeclared, "No aggregate boundary is declared."))
    )
    val endpoint = _endpoint(Present(ModelReference(ModelElementId(modelid, firstid), Local)))
    val external = _endpoint(Present(ModelReference(ModelElementId("partner.example", "entity:external"), External)))
    val relations = Vector(RelationProjection(records(3), Composition, endpoint, external, _semantics), RelationProjection(records(4), Aggregation, endpoint, endpoint, _semantics), RelationProjection(records(5), Association, endpoint, endpoint, _semantics))
    _graph_result(CmlStructureMetadata.build(catalog, elements, relations, extensions))
  }

  private def _graph_for_empty_catalog(): Graph =
    _graph_result(CmlStructureMetadata.build(Catalog(), Vector.empty, Vector.empty, Json.obj()))

  private def _graph_with_reference(): Graph = {
    val base = _graph()
    val reference = SemanticReference(
      ModelTarget(ModelElementId("model.example", "value:same")),
      Local,
      Present(RelationId("vocabulary.example", "relates-to")),
      Present(ProfileId("vocabulary.example", "semantic")),
      Present("declared semantic target"),
      Present(LocalizedLabel("Target label", Some("ja"))),
      _source("model.example", "src/main/cml/reference.cml", Some(21)),
      Derived("rule.reference", Vector(_source("model.example", "src/main/cml/reference-source.cml", Some(22))))
    )
    val records = base.catalog.elements.updated(0, base.catalog.elements.head.copy(references = Present(Vector(reference))))
    val catalog = _catalog_from(records)
    val elements = base.elements.map(value => value.copy(element = catalog.elements(base.catalog.elements.indexOf(value.element))))
    val relations = base.relations.map(value => value.copy(element = catalog.elements(base.catalog.elements.indexOf(value.element))))
    _graph_result(CmlStructureMetadata.build(catalog, elements, relations, Json.obj()))
  }

  private def _graph_with_nested_present_boundaries(): Graph = {
    val base = _graph()
    val boundary = AggregateBoundary(
      Present(ModelReference(ModelElementId("model.example", "aggregate:same"), Local)),
      Present("member")
    )
    val elements = base.elements.updated(0, base.elements.head.copy(aggregateBoundary = Present(boundary)))
    val semantics = base.relations.head.semantics.copy(aggregateBoundary = Present(boundary))
    val relations = base.relations.updated(0, base.relations.head.copy(semantics = semantics))
    _rebuild(base, elements, relations)
  }

  private def _catalog(modelid: String, firstid: String, displayname: String, sourcepath: String, sourceline: Option[Int], absencedetail: String): Catalog = {
    val records = Vector("entity", "value", "aggregate", "composition", "aggregation", "association").zipWithIndex.map { case (kind, index) =>
      val identity = if (index == 0) firstid else kind + ":same"
      ElementRecord(Present(ModelElementId(modelid, identity)), kind, displayname, _source(modelid, sourcepath, sourceline), Absent(NotDeclared, absencedetail))
    }
    _catalog_from(records)
  }

  private def _catalog_from(records: Vector[ElementRecord], terms: Vector[TermRecord] = Vector.empty): Catalog = CmlSemanticFoundation.build(records, terms) match {
    case Right(catalog) => catalog
    case Left(diagnostics) => throw new IllegalStateException(diagnostics.toString)
  }

  private def _collision_graph(suffix: String, reversed: Boolean): Graph = {
    val left = if (reversed) ModelElementId("a", "b:c" + suffix) else ModelElementId("a:b", "c" + suffix)
    val right = if (reversed) ModelElementId("a:b", "c" + suffix) else ModelElementId("a", "b:c" + suffix)
    val records = Vector(ElementRecord(Present(left), "entity", "Collision", _source(left.modelId), Absent(NotDeclared, "No semantic references are declared.")), ElementRecord(Present(right), "value", "Collision", _source(right.modelId), Absent(NotDeclared, "No semantic references are declared.")))
    val catalog = _catalog_from(records, Vector(TermRecord(TermId("a:b", "c" + suffix), _source("a:b"))))
    val elements = Vector(ElementProjection(records(0), Entity, Absent(NotApplicable, "No aggregate boundary applies.")), ElementProjection(records(1), Value, Absent(NotApplicable, "No aggregate boundary applies.")))
    _graph_result(CmlStructureMetadata.build(catalog, elements, Vector.empty, Json.obj()))
  }

  private def _extensions(suffix: String, reversed: Boolean): JsObject =
    if (reversed)
      Json.obj("org.example.b" -> Json.obj("z" -> JsString(suffix)), "org.example.a" -> Json.obj("z" -> JsString(suffix)))
    else
      Json.obj("org.example.a" -> Json.obj("z" -> JsString(suffix)), "org.example.b" -> Json.obj("z" -> JsString(suffix)))

  private def _source(authority: String, path: String = "src/main/cml/example.cml", line: Option[Int] = Some(1)): SourceAttribution = SourceAttribution(authority, path, "a" * 64, line)
  private def _endpoint(target: Presence[ModelReference]): Endpoint = Endpoint(target, Absent(NotDeclared, "No role is declared."), Present(Cardinality(0, None)), Present(false))
  private def _semantics: RelationSemantics = RelationSemantics(Absent(NotDeclared, "No ownership is declared."), Present(false), Absent(NotDeclared, "No create policy is declared."), Absent(NotDeclared, "No delete policy is declared."), Absent(NotDeclared, "No reassignment policy is declared."), Absent(NotDeclared, "No reparenting policy is declared."), Present(Vector.empty), Absent(NotDeclared, "No aggregate boundary is declared."))
  private def _rebuild(value: Graph, elements: Vector[ElementProjection] = null, relations: Vector[RelationProjection] = null): Graph = {
    val selectedelements = if (elements == null) value.elements else elements
    val selectedrelations = if (relations == null) value.relations else relations
    _graph_result(CmlStructureMetadata.build(value.catalog, selectedelements, selectedrelations, value.envelope.extensions - namespace))
  }
  private def _rebind(catalog: Catalog, value: ElementProjection): ElementProjection = value.element.identity match { case Present(identity) => catalog.element(identity) match { case Right(record) => value.copy(element = record); case Left(_) => value }; case _ => value }
  private def _rebind(catalog: Catalog, value: RelationProjection): RelationProjection = value.element.identity match { case Present(identity) => catalog.element(identity) match { case Right(record) => value.copy(element = record); case Left(_) => value }; case _ => value }
  private def _graph_result(result: Either[Vector[StructureDiagnostic], Graph]): Graph = result match { case Right(graph) => graph; case Left(diagnostics) => throw new IllegalStateException(diagnostics.toString) }
  private def _first(result: Either[Vector[StructureDiagnostic], Graph]): StructureDiagnostic = result match { case Left(diagnostics) => diagnostics.head; case Right(_) => throw new IllegalStateException("Expected a Structure diagnostic.") }
  private def _structure(root: JsObject): JsObject = root.value("extensions").asInstanceOf[JsObject].value(namespace).asInstanceOf[JsObject]
  private def _with_structure(root: JsObject, structure: JsObject): JsObject = root + ("extensions" -> (root.value("extensions").asInstanceOf[JsObject] + (namespace -> structure)))
  private def _without_structure(root: JsObject): JsObject = root + ("extensions" -> (root.value("extensions").asInstanceOf[JsObject] - namespace))
  private def _replace_embedded_name(root: JsObject, name: String): JsObject = { val structure = _structure(root); val items = structure.value("elements").asInstanceOf[play.api.libs.json.JsArray].value; val first = items.head.asInstanceOf[JsObject]; val record = first.value("element").asInstanceOf[JsObject] + ("name" -> JsString(name)); _with_structure(root, structure + ("elements" -> play.api.libs.json.JsArray(items.updated(0, first + ("element" -> record))))) }
  private def _replace_embedded_field(root: JsObject, name: String, value: JsValue): JsObject = { val structure = _structure(root); val items = structure.value("elements").asInstanceOf[play.api.libs.json.JsArray].value; val first = items.head.asInstanceOf[JsObject]; val record = first.value("element").asInstanceOf[JsObject] + (name -> value); _with_structure(root, structure + ("elements" -> play.api.libs.json.JsArray(items.updated(0, first + ("element" -> record))))) }
  private def _replace_embedded_source_field(root: JsObject, name: String, value: JsValue): JsObject = { val structure = _structure(root); val items = structure.value("elements").asInstanceOf[JsArray].value; val first = items.head.asInstanceOf[JsObject]; val record = first.value("element").asInstanceOf[JsObject]; val source = record.value("source").asInstanceOf[JsObject] + (name -> value); _with_structure(root, structure + ("elements" -> JsArray(items.updated(0, first + ("element" -> (record + ("source" -> source))))))) }
  private def _replace_structure_element(root: JsObject, change: JsObject => JsObject): JsObject = { val structure = _structure(root); val values = structure.value("elements").asInstanceOf[JsArray].value; val first = values.head.asInstanceOf[JsObject]; _with_structure(root, structure + ("elements" -> JsArray(values.updated(0, change(first))))) }
  private def _replace_structure_relation(root: JsObject, change: JsObject => JsObject): JsObject = { val structure = _structure(root); val values = structure.value("relations").asInstanceOf[JsArray].value; val first = values.head.asInstanceOf[JsObject]; _with_structure(root, structure + ("relations" -> JsArray(values.updated(0, change(first))))) }
  private def _replace_relation_endpoint(root: JsObject, name: String, change: JsObject => JsObject): JsObject = _replace_structure_relation(root, relation => relation + (name -> change(relation.value(name).asInstanceOf[JsObject])))
  private def _replace_relation_semantics(root: JsObject, change: JsObject => JsObject): JsObject = _replace_structure_relation(root, relation => relation + ("semantics" -> change(relation.value("semantics").asInstanceOf[JsObject])))
  private def _replace_element_boundary(root: JsObject, change: JsObject => JsObject): JsObject = _replace_structure_element(root, element => element + ("aggregateBoundary" -> change(element.value("aggregateBoundary").asInstanceOf[JsObject])))
  private def _replace_boundary_value(root: JsObject, change: JsObject => JsObject): JsObject = _replace_element_boundary(root, element => element + ("value" -> change(element.value("value").asInstanceOf[JsObject])))
  private def _replace_endpoint_field(root: JsObject, name: String, change: JsObject => JsObject): JsObject = _replace_relation_endpoint(root, "sourceEndpoint", endpoint => endpoint + (name -> change(endpoint.value(name).asInstanceOf[JsObject])))
  private def _replace_model_reference(root: JsObject, change: JsObject => JsObject): JsObject = _replace_relation_endpoint(root, "sourceEndpoint", endpoint => { val target = endpoint.value("target").asInstanceOf[JsObject]; val present = target.value("value").asInstanceOf[JsObject]; endpoint + ("target" -> (target + ("value" -> change(present)))) })
  private def _replace_model_identity(root: JsObject, change: JsObject => JsObject): JsObject = _replace_model_reference(root, reference => { val identity = reference.value("identity").asInstanceOf[JsObject]; reference + ("identity" -> change(identity)) })
  private def _replace_cardinality(root: JsObject, change: JsObject => JsObject): JsObject = _replace_endpoint_field(root, "cardinality", cardinalitypresence => { val cardinality = cardinalitypresence.value("value").asInstanceOf[JsObject]; cardinalitypresence + ("value" -> change(cardinality)) })
  private def _replace_relation_target_identity(root: JsObject, identity: ModelElementId): JsObject = _replace_relation_endpoint(root, "targetEndpoint", endpoint => { val target = endpoint.value("target").asInstanceOf[JsObject]; val present = target.value("value").asInstanceOf[JsObject]; val reference = present.value("identity").asInstanceOf[JsObject]; val changed = reference + ("modelId" -> _json_text(identity.modelId)) + ("elementId" -> _json_text(identity.elementId)); endpoint + ("target" -> (target + ("value" -> (present + ("identity" -> changed))))) })
  private def _replace_relation_target_value(root: JsObject, value: JsValue): JsObject = _replace_relation_endpoint(root, "targetEndpoint", endpoint => { val target = endpoint.value("target").asInstanceOf[JsObject]; endpoint + ("target" -> (target + ("value" -> value))) })
  private def _replace_relation_target_reference_field(root: JsObject, name: String, value: JsValue): JsObject = _replace_relation_endpoint(root, "targetEndpoint", endpoint => { val target = endpoint.value("target").asInstanceOf[JsObject]; val present = target.value("value").asInstanceOf[JsObject]; endpoint + ("target" -> (target + ("value" -> (present + (name -> value))))) })
  private def _relation_vectors(value: Graph, change: RelationProjection => RelationProjection): (Vector[ElementProjection], Vector[RelationProjection]) = (value.elements, value.relations.updated(0, change(value.relations.head)))
  private def _element_vectors(value: Graph, change: ElementProjection => ElementProjection): (Vector[ElementProjection], Vector[RelationProjection]) = (value.elements.updated(0, change(value.elements.head)), value.relations)
  private def _absence_text(value: AbsenceReason): JsValue = value match { case NotDeclared => JsString("not-declared"); case NotRepresented => JsString("not-represented"); case Unsupported => JsString("unsupported"); case NotApplicable => JsString("not-applicable"); case _ => JsNull }
  private def _wire_absence(root: JsObject, route: Vector[String], reason: AbsenceReason, detail: String): JsObject = _with_structure(root, _replace_path(_structure(root), route, Json.obj("status" -> JsString("absent"), "reason" -> _absence_text(reason), "detail" -> _json_text(detail))).asInstanceOf[JsObject])
  private def _replace_path(value: JsValue, path: Vector[String], replacement: JsValue): JsValue = path match {
    case Vector() => replacement
    case head +: tail if head.startsWith("[") =>
      val index = head.drop(1).dropRight(1).toInt
      val values = value.asInstanceOf[JsArray].value
      JsArray(values.updated(index, _replace_path(values(index), tail, replacement)))
    case head +: tail =>
      val objectvalue = value.asInstanceOf[JsObject]
      objectvalue + (head -> _replace_path(objectvalue.value(head), tail, replacement))
  }
  private def _json_text(value: String): JsValue = if (value == null) JsNull else JsString(value)
  private def _wire_relation(root: JsObject, relationindex: Int): JsObject = _structure(root).value("relations").asInstanceOf[JsArray].value(relationindex).asInstanceOf[JsObject]
  private def _wire_element(root: JsObject, elementindex: Int): JsObject = _structure(root).value("elements").asInstanceOf[JsArray].value(elementindex).asInstanceOf[JsObject]
  private def _wire_relation_endpoint(root: JsObject, relationindex: Int, endpointname: String): JsObject = _wire_relation(root, relationindex).value(endpointname).asInstanceOf[JsObject]
  private def _wire_present_text(value: String): JsObject = Json.obj("status" -> JsString("present"), "value" -> _json_text(value))
  private def _wire_present_boolean(value: Boolean): JsObject = Json.obj("status" -> JsString("present"), "value" -> JsBoolean(value))
  private def _wire_cardinality_object(value: Cardinality): JsObject = {
    val upper: JsValue = value.upper.map[JsValue](number => JsNumber(number)).getOrElse(JsNull)
    Json.obj("lower" -> JsNumber(value.lower), "upper" -> upper)
  }
  private def _wire_present_cardinality(value: Cardinality): JsObject = Json.obj("status" -> JsString("present"), "value" -> _wire_cardinality_object(value))
  private def _wire_present_strings(value: Vector[String]): JsObject = Json.obj("status" -> JsString("present"), "value" -> JsArray(value.map(JsString)))
  private def _wire_text_presence(value: Presence[String]): JsObject = value match {
    case Present(text) => _wire_present_text(text)
    case Absent(reason, detail) => Json.obj("status" -> JsString("absent"), "reason" -> _absence_text(reason), "detail" -> _json_text(detail))
    case _ => JsObject.empty
  }
  private def _wire_reference_presence(value: Presence[ModelReference]): JsObject = value match {
    case Present(reference) => Json.obj(
      "status" -> JsString("present"),
      "value" -> Json.obj("identity" -> Json.obj("modelId" -> _json_text(reference.identity.modelId), "elementId" -> _json_text(reference.identity.elementId)), "boundary" -> JsString(reference.boundary match { case Local => "local"; case External => "external" }))
    )
    case Absent(reason, detail) => Json.obj("status" -> JsString("absent"), "reason" -> _absence_text(reason), "detail" -> _json_text(detail))
    case _ => JsObject.empty
  }
  private def _wire_present_boundary(value: AggregateBoundary): JsObject = Json.obj(
    "status" -> JsString("present"),
    "value" -> Json.obj("aggregate" -> _wire_reference_presence(value.aggregate), "membership" -> _wire_text_presence(value.membership))
  )
  private def _wire_presence_value(root: JsObject, route: Vector[String], value: JsValue): JsObject = _with_structure(root, _replace_path(_structure(root), route, Json.obj("status" -> JsString("present"), "value" -> value)).asInstanceOf[JsObject])
  private def _wire_presence_text(root: JsObject, route: Vector[String], value: String): JsObject = _wire_presence_value(root, route, _json_text(value))
  private def _wire_lifecycle_value(root: JsObject, relationindex: Int, value: JsValue): JsObject = _wire_presence_value(root, Vector("relations", "[" + relationindex + "]", "semantics", "lifecyclePropagation"), value)
  private def _wire_cardinality_field(root: JsObject, relationindex: Int, endpointname: String, fieldname: String, value: JsValue): JsObject = _with_structure(root, _replace_path(_structure(root), Vector("relations", "[" + relationindex + "]", endpointname, "cardinality", "value", fieldname), value).asInstanceOf[JsObject])
  private def _relation_endpoint(value: RelationProjection, endpointname: String): Endpoint = endpointname match {
    case "sourceEndpoint" => value.sourceEndpoint
    case _ => value.targetEndpoint
  }
  private def _replace_endpoint_cardinality(value: RelationProjection, endpointname: String, cardinality: Presence[Cardinality]): RelationProjection = endpointname match {
    case "sourceEndpoint" => value.copy(sourceEndpoint = value.sourceEndpoint.copy(cardinality = cardinality))
    case _ => value.copy(targetEndpoint = value.targetEndpoint.copy(cardinality = cardinality))
  }
  private def _invalid_literal_results(value: Graph, invalidvalue: String): Vector[Either[Vector[StructureDiagnostic], Graph]] = {
    val relationvalue = value.relations.head
    val elementboundary = value.elements.head.aggregateBoundary.asInstanceOf[Present[AggregateBoundary]].value
    val relationboundary = relationvalue.semantics.aggregateBoundary.asInstanceOf[Present[AggregateBoundary]].value
    Vector(
      CmlStructureMetadata.build(value.catalog, value.elements, value.relations.updated(0, relationvalue.copy(sourceEndpoint = relationvalue.sourceEndpoint.copy(role = Present(invalidvalue)))), value.envelope.extensions - namespace),
      CmlStructureMetadata.build(value.catalog, value.elements, value.relations.updated(0, relationvalue.copy(targetEndpoint = relationvalue.targetEndpoint.copy(role = Present(invalidvalue)))), value.envelope.extensions - namespace),
      CmlStructureMetadata.build(value.catalog, value.elements, value.relations.updated(0, relationvalue.copy(semantics = relationvalue.semantics.copy(ownership = Present(invalidvalue)))), value.envelope.extensions - namespace),
      CmlStructureMetadata.build(value.catalog, value.elements, value.relations.updated(0, relationvalue.copy(semantics = relationvalue.semantics.copy(createPolicy = Present(invalidvalue)))), value.envelope.extensions - namespace),
      CmlStructureMetadata.build(value.catalog, value.elements, value.relations.updated(0, relationvalue.copy(semantics = relationvalue.semantics.copy(deletePolicy = Present(invalidvalue)))), value.envelope.extensions - namespace),
      CmlStructureMetadata.build(value.catalog, value.elements.updated(0, value.elements.head.copy(aggregateBoundary = Present(elementboundary.copy(membership = Present(invalidvalue))))), value.relations, value.envelope.extensions - namespace),
      CmlStructureMetadata.build(value.catalog, value.elements, value.relations.updated(0, relationvalue.copy(semantics = relationvalue.semantics.copy(aggregateBoundary = Present(relationboundary.copy(membership = Present(invalidvalue)))))), value.envelope.extensions - namespace)
    )
  }
  private def _invalid_literal_wires(root: JsObject, invalidvalue: String): Vector[JsObject] = Vector(
    _wire_presence_text(root, Vector("relations", "[0]", "sourceEndpoint", "role"), invalidvalue),
    _wire_presence_text(root, Vector("relations", "[0]", "targetEndpoint", "role"), invalidvalue),
    _wire_presence_text(root, Vector("relations", "[0]", "semantics", "ownership"), invalidvalue),
    _wire_presence_text(root, Vector("relations", "[0]", "semantics", "createPolicy"), invalidvalue),
    _wire_presence_text(root, Vector("relations", "[0]", "semantics", "deletePolicy"), invalidvalue),
    _wire_presence_text(root, Vector("elements", "[0]", "aggregateBoundary", "value", "membership"), invalidvalue),
    _wire_presence_text(root, Vector("relations", "[0]", "semantics", "aggregateBoundary", "value", "membership"), invalidvalue)
  )
  private def _absence_name(value: AbsenceReason): String = value match {
    case NotDeclared => "not-declared"
    case NotRepresented => "not-represented"
    case Unsupported => "unsupported"
    case NotApplicable => "not-applicable"
  }
  private def _absence_samples(): Vector[Absent] = Vector(
    Absent(NotDeclared, "not declared"), Absent(NotRepresented, "not represented"), Absent(Unsupported, "unsupported"), Absent(NotApplicable, "not applicable")
  )
  private def _aggregate_boundary_variants(): Vector[Presence[AggregateBoundary]] = {
    val localaggregate: Presence[ModelReference] = Present(ModelReference(ModelElementId("model.example", "aggregate:same"), Local))
    val externalaggregate: Presence[ModelReference] = Present(ModelReference(ModelElementId("Partner.Example", "Aggregate:名/Case"), External))
    val reasons = Vector[AbsenceReason](NotDeclared, NotRepresented, Unsupported, NotApplicable)
    val labels = Vector("not-declared", "not-represented", "unsupported", "not-applicable")
    Vector(
      Present(AggregateBoundary(localaggregate, Present("Member 名/Case"))),
      Present(AggregateBoundary(externalaggregate, Present("External Member α/CASE")))
    ) ++ reasons.zip(labels).map { case (reason, label) => Present(AggregateBoundary(Absent(reason, "aggregate " + label), Present("Member " + label))) } ++
      reasons.zip(labels).map { case (reason, label) => Present(AggregateBoundary(localaggregate, Absent(reason, "membership " + label))) } ++
      reasons.zip(labels).map { case (reason, label) => Present(AggregateBoundary(Absent(reason, "aggregate " + label), Absent(reason, "membership " + label))) }
  }
  private def _presence_text_generator(): Gen[Presence[String]] = {
    val present = Gen.oneOf("Role α/Case", "Policy 名/CASE", "Lifecycle value", "Token with spaces")
    val absent = Gen.oneOf(_absence_samples())
    Gen.frequency((3, present.map(value => Present(value): Presence[String])), (2, absent.map(value => value: Presence[String])))
  }
  private def _presence_boolean_generator(): Gen[Presence[Boolean]] = {
    val present = Gen.oneOf(true, false).map(value => Present(value): Presence[Boolean])
    val absent = Gen.oneOf(_absence_samples()).map(value => value: Presence[Boolean])
    Gen.frequency((3, present), (2, absent))
  }
  private def _presence_cardinality_generator(): Gen[Presence[Cardinality]] = {
    val finite = Gen.oneOf(Cardinality(0, Some(0)), Cardinality(1, Some(2)), Cardinality(2, Some(7)), Cardinality(Int.MaxValue, Some(Int.MaxValue)))
    val unbounded = Gen.const(Cardinality(0, None))
    val absent = Gen.oneOf(_absence_samples()).map(value => value: Presence[Cardinality])
    Gen.frequency((3, finite.map(value => Present(value): Presence[Cardinality])), (2, unbounded.map(value => Present(value): Presence[Cardinality])), (2, absent))
  }
  private def _presence_reference_generator(): Gen[Presence[ModelReference]] = {
    val present = Gen.oneOf(
      ModelReference(ModelElementId("model.example", "entity:item"), Local),
      ModelReference(ModelElementId("model.example", "value:same"), Local),
      ModelReference(ModelElementId("model.example", "aggregate:same"), Local),
      ModelReference(ModelElementId("Partner.Example", "External:名/Case"), External)
    ).map(value => Present(value): Presence[ModelReference])
    val absent = Gen.oneOf(_absence_samples()).map(value => value: Presence[ModelReference])
    Gen.frequency((3, present), (2, absent))
  }
  private def _aggregate_reference_generator(): Gen[Presence[ModelReference]] = {
    val present = Gen.oneOf(
      ModelReference(ModelElementId("model.example", "aggregate:same"), Local),
      ModelReference(ModelElementId("Partner.Example", "Aggregate:名/Case"), External)
    ).map(value => Present(value): Presence[ModelReference])
    val absent = Gen.oneOf(_absence_samples()).map(value => value: Presence[ModelReference])
    Gen.frequency((3, present), (2, absent))
  }
  private def _lifecycle_generator(): Gen[Presence[Vector[String]]] = {
    val present = Gen.oneOf(Vector.empty[String], Vector("Create:α"), Vector("Delete", "Create", "Delete"), Vector("Case", "case", "名")).map(value => Present(value): Presence[Vector[String]])
    val absent = Gen.oneOf(_absence_samples()).map(value => value: Presence[Vector[String]])
    Gen.frequency((3, present), (2, absent))
  }
  private def _aggregate_boundary_generator(): Gen[AggregateBoundary] = for {
    aggregate <- _aggregate_reference_generator()
    membership <- _presence_text_generator()
  } yield AggregateBoundary(aggregate, membership)
  private def _presence_boundary_generator(): Gen[Presence[AggregateBoundary]] = {
    val present = _aggregate_boundary_generator().map(value => Present(value): Presence[AggregateBoundary])
    val absent = Gen.oneOf(_absence_samples()).map(value => value: Presence[AggregateBoundary])
    Gen.frequency((3, present), (2, absent))
  }
  private def _endpoint_generator(): Gen[Endpoint] = for {
    target <- _presence_reference_generator()
    role <- _presence_text_generator()
    cardinality <- _presence_cardinality_generator()
    navigable <- _presence_boolean_generator()
  } yield Endpoint(target, role, cardinality, navigable)
  private def _relation_payload_generator(): Gen[(Endpoint, Endpoint, RelationSemantics)] = for {
    source <- _endpoint_generator()
    target <- _endpoint_generator()
    ownership <- _presence_text_generator()
    independent <- _presence_boolean_generator()
    create <- _presence_text_generator()
    delete <- _presence_text_generator()
    reassignment <- _presence_boolean_generator()
    reparenting <- _presence_boolean_generator()
    lifecycle <- _lifecycle_generator()
    boundary <- _presence_boundary_generator()
  } yield (source, target, RelationSemantics(ownership, independent, create, delete, reassignment, reparenting, lifecycle, boundary))
  private def _strict_wire_variants(root: JsObject): Vector[(String, JsObject, StructureDiagnosticKind)] = {
    val structure = _structure(root)
    val relation = structure.value("relations").asInstanceOf[JsArray].value.head.asInstanceOf[JsObject]
    val endpoint = relation.value("sourceEndpoint").asInstanceOf[JsObject]
    val targetpresence = endpoint.value("target").asInstanceOf[JsObject]
    val reference = targetpresence.value("value").asInstanceOf[JsObject]
    val identity = reference.value("identity").asInstanceOf[JsObject]
    val cardinalitypresence = endpoint.value("cardinality").asInstanceOf[JsObject]
    val cardinality = cardinalitypresence.value("value").asInstanceOf[JsObject]
    val present = endpoint.value("navigable").asInstanceOf[JsObject]
    val absent = endpoint.value("role").asInstanceOf[JsObject]
    Vector(
      ("structure.schemaVersion missing", _with_structure(root, structure - "schemaVersion"), InvalidShape),
      ("structure.elements missing", _with_structure(root, structure - "elements"), InvalidShape),
      ("structure.relations missing", _with_structure(root, structure - "relations"), InvalidShape),
      ("structure unknown", _with_structure(root, structure + ("unknown" -> JsNull)), InvalidShape),
      ("structure version unknown", _with_structure(root, structure + ("schemaVersion" -> JsString("unknown"))), UnsupportedSchemaVersion),
      ("element.element missing", _replace_structure_element(root, value => value - "element"), InvalidShape),
      ("element.kind missing", _replace_structure_element(root, value => value - "kind"), InvalidShape),
      ("element.aggregateBoundary missing", _replace_structure_element(root, value => value - "aggregateBoundary"), InvalidShape),
      ("element unknown", _replace_structure_element(root, value => value + ("unknown" -> JsNull)), InvalidShape),
      ("relation.element missing", _replace_structure_relation(root, value => value - "element"), InvalidShape),
      ("relation.kind missing", _replace_structure_relation(root, value => value - "kind"), InvalidShape),
      ("relation.sourceEndpoint missing", _replace_structure_relation(root, value => value - "sourceEndpoint"), InvalidShape),
      ("relation.targetEndpoint missing", _replace_structure_relation(root, value => value - "targetEndpoint"), InvalidShape),
      ("relation.semantics missing", _replace_structure_relation(root, value => value - "semantics"), InvalidShape),
      ("relation unknown", _replace_structure_relation(root, value => value + ("unknown" -> JsNull)), InvalidShape),
      ("endpoint.target missing", _replace_relation_endpoint(root, "sourceEndpoint", value => value - "target"), InvalidShape),
      ("endpoint.role missing", _replace_relation_endpoint(root, "sourceEndpoint", value => value - "role"), InvalidShape),
      ("endpoint.cardinality missing", _replace_relation_endpoint(root, "sourceEndpoint", value => value - "cardinality"), InvalidShape),
      ("endpoint.navigable missing", _replace_relation_endpoint(root, "sourceEndpoint", value => value - "navigable"), InvalidShape),
      ("endpoint unknown", _replace_relation_endpoint(root, "sourceEndpoint", value => value + ("unknown" -> JsNull)), InvalidShape),
      ("semantics.ownership missing", _replace_relation_semantics(root, value => value - "ownership"), InvalidShape),
      ("semantics.independentExistence missing", _replace_relation_semantics(root, value => value - "independentExistence"), InvalidShape),
      ("semantics.createPolicy missing", _replace_relation_semantics(root, value => value - "createPolicy"), InvalidShape),
      ("semantics.deletePolicy missing", _replace_relation_semantics(root, value => value - "deletePolicy"), InvalidShape),
      ("semantics.reassignment missing", _replace_relation_semantics(root, value => value - "reassignment"), InvalidShape),
      ("semantics.reparenting missing", _replace_relation_semantics(root, value => value - "reparenting"), InvalidShape),
      ("semantics.lifecyclePropagation missing", _replace_relation_semantics(root, value => value - "lifecyclePropagation"), InvalidShape),
      ("semantics.aggregateBoundary missing", _replace_relation_semantics(root, value => value - "aggregateBoundary"), InvalidShape),
      ("semantics unknown", _replace_relation_semantics(root, value => value + ("unknown" -> JsNull)), InvalidShape),
      ("boundary.aggregate missing", _replace_boundary_value(root, value => value - "aggregate"), InvalidShape),
      ("boundary.membership missing", _replace_boundary_value(root, value => value - "membership"), InvalidShape),
      ("boundary unknown", _replace_boundary_value(root, value => value + ("unknown" -> JsNull)), InvalidShape),
      ("modelReference.identity missing", _replace_model_reference(root, value => value - "identity"), InvalidShape),
      ("modelReference.boundary missing", _replace_model_reference(root, value => value - "boundary"), InvalidShape),
      ("modelReference unknown", _replace_model_reference(root, value => value + ("unknown" -> JsNull)), InvalidShape),
      ("cardinality.lower missing", _replace_cardinality(root, value => value - "lower"), InvalidShape),
      ("cardinality.upper missing", _replace_cardinality(root, value => value - "upper"), InvalidShape),
      ("cardinality unknown", _replace_cardinality(root, value => value + ("unknown" -> JsNull)), InvalidShape),
      ("present.status missing", _replace_endpoint_field(root, "navigable", value => value - "status"), InvalidShape),
      ("present.value missing", _replace_endpoint_field(root, "navigable", value => value - "value"), InvalidShape),
      ("present unknown", _replace_endpoint_field(root, "navigable", value => value + ("unknown" -> JsNull)), InvalidShape),
      ("absent.status missing", _replace_endpoint_field(root, "role", value => value - "status"), InvalidShape),
      ("absent.reason missing", _replace_endpoint_field(root, "role", value => value - "reason"), InvalidShape),
      ("absent.detail missing", _replace_endpoint_field(root, "role", value => value - "detail"), InvalidShape),
      ("absent unknown", _replace_endpoint_field(root, "role", value => value + ("unknown" -> JsNull)), InvalidShape),
      ("identity.modelId missing", _replace_model_identity(root, value => value - "modelId"), InvalidShape),
      ("identity.elementId missing", _replace_model_identity(root, value => value - "elementId"), InvalidShape),
      ("identity unknown", _replace_model_identity(root, value => value + ("unknown" -> JsNull)), InvalidShape),
      ("kind unknown", _replace_structure_element(root, value => value + ("kind" -> JsString("unknown"))), InvalidShape),
      ("relation kind unknown", _replace_structure_relation(root, value => value + ("kind" -> JsString("unknown"))), InvalidShape),
      ("target null", _replace_relation_endpoint(root, "sourceEndpoint", value => value + ("target" -> JsNull)), InvalidShape),
      ("cardinality lower scalar", _replace_cardinality(root, value => value + ("lower" -> JsString("0"))), InvalidShape),
      ("cardinality upper scalar", _replace_cardinality(root, value => value + ("upper" -> JsString("none"))), InvalidShape),
      ("navigable null", _replace_endpoint_field(root, "navigable", value => value + ("value" -> JsNull)), InvalidShape),
      ("presence mixed", _replace_endpoint_field(root, "navigable", value => value + ("status" -> JsString("mixed"))), InvalidShape),
      ("absence unknown reason", _replace_endpoint_field(root, "role", value => value + ("reason" -> JsString("unknown"))), InvalidShape),
      ("aggregateBoundary null", _replace_structure_element(root, value => value + ("aggregateBoundary" -> JsNull)), InvalidShape)
    )
  }
}
