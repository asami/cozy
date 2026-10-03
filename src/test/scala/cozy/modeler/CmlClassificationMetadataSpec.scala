package cozy.modeler

import org.scalacheck.{Gen, Prop, Test}
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import play.api.libs.json.{JsArray, JsBoolean, JsNull, JsObject, JsString, JsValue, Json}

import CmlSemanticFoundation._
import CmlClassificationMetadata._

/*
 * @since   Oct. 3, 2026
 * @version Oct.  3, 2026
 * @author  ASAMI, Tomoharu
 */
final class CmlClassificationMetadataSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  "Classification declarations" should {
    "CL-01 retain distinct constructs and every independent core source fact under identical display names" in {
      Given("admitted same-named nodes, five Classification kinds, a Term reference and unrelated core facts")
      val graph = _graph()
      When("the typed producer renders and the strict receiver admits its complete envelope")
      val root = CmlClassificationMetadata.toJson(graph)
      val consumed = _graph_result(CmlClassificationMetadata.read(root))
      Then("declarations, full core facts, source attribution and typed endpoints remain exact")
      consumed.traits shouldBe graph.traits
      consumed.powertypes shouldBe graph.powertypes
      consumed.generalizations shouldBe graph.generalizations
      consumed.traitRealizations shouldBe graph.traitRealizations
      consumed.powertypeClassifications shouldBe graph.powertypeClassifications
      consumed.catalog.elements shouldBe graph.catalog.elements
      consumed.catalog.terms shouldBe graph.catalog.terms
      consumed.catalog.elements.map(_.name).distinct shouldBe Vector("共有")
      consumed.catalog.elements.map(_.source) shouldBe graph.catalog.elements.map(_.source)
      consumed.catalog.elements.map(_.references) shouldBe graph.catalog.elements.map(_.references)
      Vector(consumed.traits.head.element.kind, consumed.powertypes.head.element.kind,
        consumed.generalizations.head.element.kind, consumed.traitRealizations.head.element.kind,
        consumed.powertypeClassifications.head.element.kind) shouldBe
        Vector("trait", "powertype", "generalization", "trait-realization", "powertype-classification")
    }

    "CL-02 preserve qualified authority and case distinctions separately from Terms and BoK relation IDs" in {
      Given("three qualified model identities with shared components and a separate Term identity")
      val records = Vector(_record("trait", "Same", "a:b"), _record("trait", "Same", "a"), _record("trait", "same", "a:b"))
      val term = TermRecord(TermId("a:b", "Same"), _source("a:b"))
      val catalog = _catalog(records, Vector(term))
      When("the supplier projects all three declarations and the receiver reads them")
      val graph = _graph_result(CmlClassificationMetadata.build(catalog, records.map(TraitProjection), Vector.empty,
        Vector.empty, Vector.empty, Vector.empty, Json.obj()))
      val consumed = _graph_result(CmlClassificationMetadata.read(CmlClassificationMetadata.toJson(graph)))
      Then("qualified lookup is case-sensitive and never coerces the Term or vocabulary relation domain")
      records.foreach(record => consumed.catalog.element(record.identity.asInstanceOf[Present[ModelElementId]].value) shouldBe Right(record))
      consumed.catalog.element(ModelElementId("a:b", "SAME")).isLeft shouldBe true
      consumed.catalog.term(TermId("a:b", "Same")) shouldBe Right(term)
      consumed.traits.map(_.element.identity) shouldBe records.map(_.identity)
      val reference = graph.catalog.elements.head.references
      consumed.catalog.elements.head.references shouldBe reference
    }

    "CL-02 retain equal anonymous declaration multiplicities without generated identities" in {
      Given("two equal anonymous traits and an anonymous generalization with absent endpoints")
      val identity = Absent(NotRepresented, "No authored model identity.")
      val record = _record("trait", "anonymous").copy(identity = identity)
      val relation = _record("generalization", "anonymous-relation").copy(identity = identity)
      val endpoint = Absent(NotDeclared, "No endpoint is declared.")
      val catalog = _catalog(Vector(record, record, relation))
      When("the producer preserves every occurrence through canonical JSON and strict reading")
      val graph = _graph_result(CmlClassificationMetadata.build(catalog, Vector(TraitProjection(record), TraitProjection(record)),
        Vector.empty, Vector(GeneralizationProjection(relation, endpoint, endpoint)), Vector.empty, Vector.empty, Json.obj()))
      val consumed = _graph_result(CmlClassificationMetadata.read(Json.parse(CmlClassificationMetadata.canonicalJson(graph))))
      Then("both equal traits remain anonymous and the relation carries its absent identity and endpoints")
      consumed.catalog.elements shouldBe Vector(record, record, relation)
      consumed.traits shouldBe Vector(TraitProjection(record), TraitProjection(record))
      consumed.generalizations shouldBe graph.generalizations
      consumed.catalog.element(ModelElementId("model.example", "anonymous")).isLeft shouldBe true
    }
  }

  "Exact core coverage" should {
    "CL-03 reject missing and duplicate occurrences in every projection category" in {
      Given("a complete admitted graph and one missing or excess occurrence in each category")
      val graph = _graph()
      When("the public factory admits each incomplete or duplicated projection set")
      val missing = Vector(
        _rebuild(graph, traits = Some(Vector.empty)), _rebuild(graph, powertypes = Some(graph.powertypes.tail)),
        _rebuild(graph, generalizations = Some(Vector.empty)), _rebuild(graph, realizations = Some(Vector.empty)),
        _rebuild(graph, classifications = Some(graph.powertypeClassifications.tail)))
      val duplicated = Vector(
        _rebuild(graph, traits = Some(graph.traits ++ graph.traits)),
        _rebuild(graph, powertypes = Some(graph.powertypes :+ graph.powertypes.head)),
        _rebuild(graph, generalizations = Some(graph.generalizations ++ graph.generalizations)),
        _rebuild(graph, realizations = Some(graph.traitRealizations ++ graph.traitRealizations)),
        _rebuild(graph, classifications = Some(graph.powertypeClassifications :+ graph.powertypeClassifications.head)))
      Then("missing and duplicate coverage have distinct typed diagnostics without catalog mutation")
      missing.map(_first(_).kind) shouldBe Vector.fill(5)(MissingProjection)
      duplicated.map(_first(_).kind) shouldBe Vector.fill(5)(DuplicateProjection)
      graph.catalog.elements.size shouldBe 13
    }

    "CL-03 bind anonymous coverage by the complete record multiset" in {
      Given("two identical anonymous records admitted with two matching projections")
      val record = _record("trait", "hidden").copy(identity = Absent(Unsupported, "No supported identity."))
      val catalog = _catalog(Vector(record, record))
      val graph = _graph_result(CmlClassificationMetadata.build(catalog, Vector.fill(2)(TraitProjection(record)),
        Vector.empty, Vector.empty, Vector.empty, Vector.empty, Json.obj()))
      When("the factory receives one occurrence, three occurrences, or a forged anonymous fact")
      val missing = _rebuild(graph, traits = Some(graph.traits.take(1)))
      val duplicate = _rebuild(graph, traits = Some(graph.traits :+ graph.traits.head))
      val forged = _rebuild(graph, traits = Some(graph.traits.updated(0, TraitProjection(record.copy(name = "contradiction")))))
      Then("multiplicity and full anonymous-record binding reject independently")
      _first(missing).kind shouldBe MissingProjection
      _first(duplicate).kind shouldBe DuplicateProjection
      _first(forged).kind shouldBe InvalidCoreBinding
    }

    "CL-03 reject forged identity kind name provenance and semantic references in factory and wire records" in {
      Given("an admitted trait and five distinct full-record contradictions")
      val graph = _graph()
      val record = graph.traits.head.element
      val forged = Vector(record.copy(identity = Present(ModelElementId("model.example", "other"))),
        record.copy(kind = "powertype"), record.copy(name = "different"),
        record.copy(source = record.source.copy(sha256 = "b" * 64)),
        record.copy(references = Absent(NotDeclared, "Changed core reference facts.")))
      val root = CmlClassificationMetadata.toJson(graph)
      val embedded = _at(_child(root), Vector("traits", "0", "element")).as[JsObject]
      val wire = Vector(
        embedded + ("identity" -> Json.obj("status" -> "present", "value" -> Json.obj("modelId" -> "model.example", "elementId" -> "other"))),
        embedded + ("kind" -> JsString("powertype")), embedded + ("name" -> JsString("different")),
        embedded + ("source" -> ((embedded \ "source").as[JsObject] + ("sha256" -> JsString("b" * 64)))),
        embedded + ("references" -> Json.obj("status" -> "absent", "reason" -> "not-declared", "detail" -> "Changed core reference facts.")))
      When("the real factory and strict reader receive the contradicted records")
      val typedresults = forged.map(value => _rebuild(graph, traits = Some(Vector(TraitProjection(value)))))
      val wireresults = wire.map(value => CmlClassificationMetadata.read(_replace_child(root, Vector("traits", "0", "element"), value)))
      Then("every contradiction fails full binding even when a display name or identity still matches")
      typedresults.map(_first(_).kind) shouldBe Vector.fill(5)(InvalidCoreBinding)
      wireresults.map(_first(_).kind) shouldBe Vector.fill(5)(InvalidCoreBinding)
    }
  }

  "Declared reference targets" should {
    "CL-04 admit every node kind for subjects and endpoints while keeping target-specific kinds" in {
      Given("all six admitted node kinds and a declared generalization, realization and dimension")
      val graph = _graph()
      val nodes = graph.catalog.elements.filter(value => Set("entity", "value", "aggregate", "trait", "powertype", "powertype-kind")(value.kind))
      When("each node is used as a local subject and both generalization endpoints")
      val published = nodes.map { node =>
        val reference = Present(ModelReference(node.identity.asInstanceOf[Present[ModelElementId]].value, Local))
        _graph_result(_rebuild(graph,
          generalizations = Some(Vector(graph.generalizations.head.copy(subtype = reference, supertype = reference))),
          realizations = Some(Vector(graph.traitRealizations.head.copy(subject = reference))),
          classifications = Some(graph.powertypeClassifications.updated(0, graph.powertypeClassifications.head.copy(subject = reference)))))
      }
      val received = published.map(value => _graph_result(CmlClassificationMetadata.read(CmlClassificationMetadata.toJson(value))))
      Then("self-links and every valid local node survive strict round trips without runtime inheritance rules")
      published.zip(received).foreach { case (value, consumed) =>
        consumed.generalizations shouldBe value.generalizations
        consumed.traitRealizations shouldBe value.traitRealizations
        consumed.powertypeClassifications shouldBe value.powertypeClassifications
      }
      graph.powertypes.head.members shouldBe Present(Vector(_reference("member"), _reference("member")))
    }

    "CL-04 reject dangling non-node and wrong-kind local targets on every carrier" in {
      Given("a complete graph and local references to an unknown pair or a relation declaration")
      val graph = _graph()
      val invalidnodes = Vector(_reference("missing"), _reference("generalization"), _reference("unrelated"))
      When("the factory receives invalid endpoints, subjects, trait targets, powertype targets and members")
      val nodefailures = invalidnodes.flatMap { reference =>
        val present = Present(reference)
        Vector(_rebuild(graph, generalizations = Some(Vector(graph.generalizations.head.copy(subtype = present)))),
          _rebuild(graph, generalizations = Some(Vector(graph.generalizations.head.copy(supertype = present)))),
          _rebuild(graph, realizations = Some(Vector(graph.traitRealizations.head.copy(subject = present)))),
          _rebuild(graph, classifications = Some(graph.powertypeClassifications.updated(0, graph.powertypeClassifications.head.copy(subject = present)))))
      }
      val wrongkinds = Vector("entity", "value", "aggregate", "member", "generalization", "missing").flatMap { id =>
        Vector(_rebuild(graph, realizations = Some(Vector(graph.traitRealizations.head.copy(traitTarget = Present(_reference(id)))))),
          _rebuild(graph, classifications = Some(graph.powertypeClassifications.updated(0, graph.powertypeClassifications.head.copy(powertype = Present(_reference(id)))))))
      } ++ Vector("entity", "trait", "powertype", "generalization", "missing").map { id =>
        _rebuild(graph, powertypes = Some(graph.powertypes.updated(0, graph.powertypes.head.copy(members = Present(Vector(_reference(id)))))))
      }
      Then("every forbidden local target returns Classification semantics diagnostics")
      (nodefailures ++ wrongkinds).map(_first(_).kind).distinct shouldBe Vector(InvalidClassificationSemantics)
    }

    "CL-04 preserve external unresolved and same-pair local/external distinctions without local type claims" in {
      Given("external references to unknown identities and locally admitted records of the wrong target kind")
      val graph = _graph()
      val external = Vector(_reference("missing", External), _reference("generalization", External), _reference("entity", External))
      When("external endpoints, targets and members are published and read")
      val published = external.map { reference =>
        val present = Present(reference)
        _graph_result(_rebuild(graph,
          powertypes = Some(graph.powertypes.updated(0, graph.powertypes.head.copy(members = Present(Vector(reference, _reference("member"), _reference("member", External)))))),
          generalizations = Some(Vector(graph.generalizations.head.copy(subtype = present, supertype = present))),
          realizations = Some(Vector(graph.traitRealizations.head.copy(subject = present, traitTarget = present))),
          classifications = Some(graph.powertypeClassifications.updated(0, graph.powertypeClassifications.head.copy(subject = present, powertype = present)))))
      }
      val received = published.map(value => _graph_result(CmlClassificationMetadata.read(CmlClassificationMetadata.toJson(value))))
      Then("both components and the explicit boundary survive with no local-resolution or type assertion")
      published.zip(received).foreach { case (value, consumed) =>
        consumed.powertypes shouldBe value.powertypes
        consumed.generalizations shouldBe value.generalizations
        consumed.traitRealizations shouldBe value.traitRealizations
        consumed.powertypeClassifications shouldBe value.powertypeClassifications
      }
    }
  }

  "Presence and malformed factory arguments" should {
    "CL-05 preserve every absence reason at every carrier alongside explicit empty members and multiple generalizations" in {
      Given("all four reasons with coordinate-specific Unicode details and a second generalization")
      val graph = _graph()
      val extra = _record("generalization", "generalization-2")
      val catalog = _catalog(graph.catalog.elements :+ extra, graph.catalog.terms)
      When("each absence is used on members and all six endpoint carriers, then strictly read")
      val published = _reasons.map { reason =>
        def _absent_(coordinate: String): Absent = Absent(reason, "Absent " + coordinate + ": 条件Aa")
        _graph_result(CmlClassificationMetadata.build(catalog, graph.traits,
          Vector(graph.powertypes.head.copy(members = _absent_("members")), graph.powertypes(1).copy(members = Present(Vector.empty))),
          Vector(graph.generalizations.head.copy(subtype = _absent_("subtype"), supertype = _absent_("supertype")),
            GeneralizationProjection(extra, Present(_reference("value")), Present(_reference("entity")))),
          Vector(graph.traitRealizations.head.copy(subject = _absent_("trait.subject"), traitTarget = _absent_("traitTarget"))),
          graph.powertypeClassifications.updated(0, graph.powertypeClassifications.head.copy(subject = _absent_("dimension.subject"), powertype = _absent_("powertype"))),
          Json.obj()))
      }
      val received = published.map(value => _graph_result(CmlClassificationMetadata.read(Json.parse(CmlClassificationMetadata.canonicalJson(value)))))
      Then("all absence details and reasons, the empty present vector and both generalizations remain literal")
      published.zip(received).foreach { case (value, consumed) =>
        consumed.powertypes shouldBe value.powertypes
        consumed.powertypes(1).members shouldBe Present(Vector.empty)
        consumed.generalizations shouldBe value.generalizations
        consumed.generalizations.size shouldBe 2
        consumed.traitRealizations shouldBe value.traitRealizations
        consumed.powertypeClassifications shouldBe value.powertypeClassifications
      }
    }

    "CL-05 return typed failures for null factory containers records presences references identities and members" in {
      Given("an admitted graph and malformed Scala arguments at every construction layer")
      val graph = _graph()
      val traitrecord = graph.traits.head.element
      When("the public factory receives each null or malformed argument without exception interception")
      val results = Vector(
        CmlClassificationMetadata.build(null, graph.traits, graph.powertypes, graph.generalizations, graph.traitRealizations, graph.powertypeClassifications, Json.obj()),
        _rebuild(graph, extensions = Some(null)),
        _rebuild(graph, traits = Some(null)), _rebuild(graph, powertypes = Some(null)),
        _rebuild(graph, generalizations = Some(null)), _rebuild(graph, realizations = Some(null)), _rebuild(graph, classifications = Some(null)),
        _rebuild(graph, traits = Some(Vector(null))), _rebuild(graph, powertypes = Some(Vector(null))),
        _rebuild(graph, generalizations = Some(Vector(null))), _rebuild(graph, realizations = Some(Vector(null))),
        _rebuild(graph, classifications = Some(Vector(null))),
        _rebuild(graph, traits = Some(Vector(TraitProjection(null)))),
        _rebuild(graph, traits = Some(Vector(TraitProjection(traitrecord.copy(kind = null))))),
        _rebuild(graph, traits = Some(Vector(TraitProjection(traitrecord.copy(identity = null))))),
        _rebuild(graph, traits = Some(Vector(TraitProjection(traitrecord.copy(source = null))))),
        _rebuild(graph, traits = Some(Vector(TraitProjection(traitrecord.copy(references = null))))),
        _rebuild(graph, generalizations = Some(Vector(graph.generalizations.head.copy(subtype = null)))),
        _rebuild(graph, generalizations = Some(Vector(graph.generalizations.head.copy(subtype = Present(null))))),
        _rebuild(graph, generalizations = Some(Vector(graph.generalizations.head.copy(subtype = Present(ModelReference(null, Local)))))),
        _rebuild(graph, generalizations = Some(Vector(graph.generalizations.head.copy(subtype = Present(ModelReference(ModelElementId(null, "id"), Local)))))),
        _rebuild(graph, generalizations = Some(Vector(graph.generalizations.head.copy(subtype = Present(ModelReference(ModelElementId("model.example", null), Local)))))),
        _rebuild(graph, generalizations = Some(Vector(graph.generalizations.head.copy(subtype = Present(ModelReference(_reference("entity").identity, null)))))),
        _rebuild(graph, powertypes = Some(graph.powertypes.updated(0, graph.powertypes.head.copy(members = null)))),
        _rebuild(graph, powertypes = Some(graph.powertypes.updated(0, graph.powertypes.head.copy(members = Present(null))))),
        _rebuild(graph, powertypes = Some(graph.powertypes.updated(0, graph.powertypes.head.copy(members = Present(Vector(null)))))),
        _rebuild(graph, generalizations = Some(Vector(graph.generalizations.head.copy(subtype = Absent(null, "No reason."))))),
        _rebuild(graph, generalizations = Some(Vector(graph.generalizations.head.copy(subtype = Absent(NotDeclared, null)))))
      )
      Then("no partial Graph or thrown null/match failure escapes and each result has a typed diagnostic")
      results.foreach(result => _first(result).detail.nonEmpty shouldBe true)
      results.take(2).map(_first(_).kind) shouldBe Vector(PublicationFailure, PublicationFailure)
    }

    "CL-05 reject empty Unicode-padded and ISO-control identifiers and absence details on factory and wire routes" in {
      Given("invalid literal components and details including non-ASCII edge whitespace and ISO controls")
      val graph = _graph()
      val root = CmlClassificationMetadata.toJson(graph)
      val invalid = Vector("", " padded", "padded ", "\u00a0padded", "padded\u2003", "a\u0000b", "a\u0085b")
      When("the real factory and reader receive these values in both identity components and absence details")
      val results = invalid.flatMap { text =>
        Vector(_rebuild(graph, generalizations = Some(Vector(graph.generalizations.head.copy(subtype = Present(ModelReference(ModelElementId(text, "id"), External)))))),
          _rebuild(graph, generalizations = Some(Vector(graph.generalizations.head.copy(subtype = Present(ModelReference(ModelElementId("authority", text), External)))))),
          _rebuild(graph, generalizations = Some(Vector(graph.generalizations.head.copy(subtype = Absent(NotDeclared, text))))),
          CmlClassificationMetadata.read(_replace_child(root, Vector("generalizations", "0", "subtype"), Json.obj("status" -> "absent", "reason" -> "not-declared", "detail" -> text))),
          CmlClassificationMetadata.read(_replace_child(root, Vector("generalizations", "0", "subtype", "value", "identity", "modelId"), JsString(text))),
          CmlClassificationMetadata.read(_replace_child(root, Vector("generalizations", "0", "subtype", "value", "identity", "elementId"), JsString(text))))
      }
      Then("foundation-equivalent literal validation rejects without trimming or echoing authored input")
      results.map(_first(_).kind).distinct shouldBe Vector(InvalidClassificationSemantics)
      results.foreach(result => _first(result).detail shouldBe "The declared Classification reference or absence is invalid.")
    }
  }

  "Powertype dimensions" should {
    "CL-06 retain different targets and separate relation IDs for a shared subject-target pair plus anonymous dimensions" in {
      Given("three authored dimensions including two with identical subject-target pairs and one anonymous dimension")
      val graph = _graph()
      val anonymous = _record("powertype-classification", "anonymous").copy(identity = Absent(NotApplicable, "No addressable dimension identity."))
      val projection = PowertypeClassificationProjection(anonymous, Absent(NotRepresented, "Subject is not represented."), Present(_reference("powertype")))
      val catalog = _catalog(graph.catalog.elements :+ anonymous, graph.catalog.terms)
      When("the producer retains all four declarations and the JSON receiver reads them")
      val published = _graph_result(CmlClassificationMetadata.build(catalog, graph.traits, graph.powertypes, graph.generalizations,
        graph.traitRealizations, graph.powertypeClassifications :+ projection, Json.obj()))
      val consumed = _graph_result(CmlClassificationMetadata.read(CmlClassificationMetadata.toJson(published)))
      Then("dimensions remain independent by their own identities without name or pair deduplication")
      consumed.powertypeClassifications shouldBe graph.powertypeClassifications :+ projection
      consumed.powertypeClassifications.take(3).map(_.element.identity).distinct.size shouldBe 3
      consumed.powertypeClassifications(0).subject shouldBe consumed.powertypeClassifications(2).subject
      consumed.powertypeClassifications(0).powertype shouldBe consumed.powertypeClassifications(2).powertype
      consumed.powertypeClassifications(1).powertype should not be consumed.powertypeClassifications(0).powertype
      consumed.powertypeClassifications.last.element.identity shouldBe anonymous.identity
    }
  }

  "Publication compatibility" should {
    "CL-07 preserve immutable graphs empty catalogs and opaque peers through deterministic round trips" in {
      Given("a complete graph with an opaque peer and an explicit empty graph over an empty catalog")
      val graph = _graph()
      val empty = _graph_result(CmlClassificationMetadata.build(Catalog(), Vector.empty, Vector.empty,
        Vector.empty, Vector.empty, Vector.empty, Json.obj("org.example.peer" -> _peer)))
      val original = graph.catalog.elements
      When("both graphs are rendered parsed admitted and rendered again")
      val consumed = Vector(graph, empty).map(value => _graph_result(CmlClassificationMetadata.read(Json.parse(CmlClassificationMetadata.canonicalJson(value)))))
      Then("canonical rendering is stable and peer facts and original catalog remain exact")
      consumed.zip(Vector(graph, empty)).foreach { case (actual, expected) =>
        CmlClassificationMetadata.canonicalJson(actual) shouldBe CmlClassificationMetadata.canonicalJson(expected)
        actual.envelope.extensions.value("org.example.peer") shouldBe _peer
        actual.catalog.elements shouldBe expected.catalog.elements
      }
      graph.catalog.elements shouldBe original
      consumed(1).traits shouldBe Vector.empty
      consumed(1).powertypeClassifications shouldBe Vector.empty
      (_child(CmlClassificationMetadata.toJson(empty)) \ "schemaVersion").as[String] shouldBe schemaVersion
    }

    "CL-07 reject own namespace conflicts and check child version before unknown fields" in {
      Given("caller extensions already owning Classification and a wire child with unknown version and hostile extra key")
      val graph = _graph()
      val root = CmlClassificationMetadata.toJson(graph)
      val future = _child(root) + ("schemaVersion" -> JsString("future")) + ("hostile\nkey" -> JsNull)
      When("the public factory and reader process these conflicting boundaries")
      val conflict = _rebuild(graph, extensions = Some(Json.obj(namespace -> Json.obj())))
      val unknown = CmlClassificationMetadata.read(_with_child(root, future))
      val missing = CmlClassificationMetadata.read(root + ("extensions" -> (graph.envelope.extensions - namespace)))
      Then("fixed exact diagnostic kinds paths and details report conflict version and missing child")
      _first(conflict) shouldBe ClassificationDiagnostic(ExtensionConflict, "extensions", "The Classification extension namespace is already supplied by the caller.", None)
      _first(unknown) shouldBe ClassificationDiagnostic(UnsupportedSchemaVersion, "extensions.cozy.cml.classification.schemaVersion", "The Classification schema version is not supported.", None)
      _first(missing) shouldBe ClassificationDiagnostic(InvalidShape, "extensions.cozy.cml.classification", "The Classification value does not match the required v1 shape.", None)
    }

    "CL-07 wrap original typed base publication and foundation diagnostics" in {
      Given("an invalid foundation identity in wire JSON and an invalid caller extension")
      val graph = _graph()
      val root = CmlClassificationMetadata.toJson(graph)
      val invalid = _replace_json(root, Vector("elements", "0", "identity", "value", "elementId"), JsString(" padded")).as[JsObject]
      val base = CmlSemanticMetadata.read(invalid).left.toOption.get
      val extensions = Json.obj("bad" -> Json.obj())
      val basepublication = CmlSemanticMetadata.build(graph.catalog, extensions).left.toOption.get
      When("Classification delegates to the unchanged base reader and publisher")
      val received = CmlClassificationMetadata.read(invalid)
      val built = _rebuild(graph, extensions = Some(extensions))
      Then("PublicationFailure retains every original publication kind path detail and foundation diagnostic")
      received.left.toOption.get.map(_.publication) shouldBe base.map(Some(_))
      received.left.toOption.get.map(_.kind).distinct shouldBe Vector(PublicationFailure)
      built.left.toOption.get.map(_.publication) shouldBe basepublication.map(Some(_))
      _first(received).publication.get.foundation.get.kind shouldBe InvalidIdentity
    }
  }

  "Strict recursive child shape" should {
    "CL-08 reject extra omitted null mistyped and nonobject values at every typed child container" in {
      Given("every typed object coordinate in a valid child including present and absent branches")
      val root = CmlClassificationMetadata.toJson(_graph())
      val containers = Vector(Vector.empty[String], Vector("traits", "0"), Vector("powertypes", "0"),
        Vector("generalizations", "0"), Vector("traitRealizations", "0"), Vector("powertypeClassifications", "0"),
        Vector("powertypes", "0", "members"), Vector("generalizations", "0", "subtype"),
        Vector("generalizations", "0", "supertype"), Vector("traitRealizations", "0", "subject"),
        Vector("traitRealizations", "0", "traitTarget"), Vector("powertypeClassifications", "0", "subject"),
        Vector("powertypeClassifications", "0", "powertype"), Vector("powertypes", "1", "members"),
        Vector("powertypes", "0", "members", "value", "0"), Vector("powertypes", "0", "members", "value", "0", "identity"),
        Vector("generalizations", "0", "subtype", "value"), Vector("generalizations", "0", "subtype", "value", "identity"),
        Vector("traitRealizations", "0", "traitTarget", "value"), Vector("traitRealizations", "0", "traitTarget", "value", "identity"),
        Vector("powertypeClassifications", "0", "powertype", "value"), Vector("powertypeClassifications", "0", "powertype", "value", "identity"))
      When("the receiver is given independent unknown missing null and mistyped field mutations and nonobjects")
      val mutations = containers.flatMap { path =>
        val value = _at(_child(root), path).as[JsObject]
        val fieldmutations = value.fields.toVector.flatMap { case (key, _) =>
          val payloadkind = if (key == "element") InvalidCoreBinding else InvalidShape
          Vector((value - key, InvalidShape), (value + (key -> JsNull), payloadkind), (value + (key -> JsBoolean(false)), payloadkind))
        }
        val containerkind = if (path.isEmpty) PublicationFailure else InvalidShape
        (Vector[(JsValue, ClassificationDiagnosticKind)]((value + ("hostile\nunknown" -> JsNull), InvalidShape),
          (JsNull, containerkind), (JsArray(Vector.empty), containerkind)) ++ fieldmutations)
          .map { case (replacement, kind) => (CmlClassificationMetadata.read(_replace_child(root, path, replacement)), kind) }
      }
      Then("every malformed typed container fails closed with fixed logical shape diagnostics")
      mutations.foreach { case (result, kind) =>
        val diagnostic = _first(result)
        diagnostic.kind shouldBe kind
        diagnostic.path.startsWith("extensions") shouldBe true
        diagnostic.path.contains("hostile") shouldBe false
        diagnostic.detail shouldBe (kind match {
          case PublicationFailure => "The foundation metadata publication is invalid."
          case InvalidCoreBinding => "The embedded Classification record does not exactly match admitted core JSON."
          case _ => "The Classification value does not match the required v1 shape."
        })
      }
    }

    "CL-08 reject mixed presence branches wrong enums and malformed array members" in {
      Given("valid JSON with exact typed arrays and closed Presence and boundary enums")
      val root = CmlClassificationMetadata.toJson(_graph())
      val present = _at(_child(root), Vector("generalizations", "0", "subtype")).as[JsObject]
      val absent = _at(_child(root), Vector("powertypes", "1", "members")).as[JsObject]
      val mutations = Vector(
        Vector("generalizations", "0", "subtype") -> (present + ("reason" -> JsString("not-declared"))),
        Vector("powertypes", "1", "members") -> (absent + ("value" -> Json.arr())),
        Vector("generalizations", "0", "subtype", "status") -> JsString("Present"),
        Vector("powertypes", "1", "members", "reason") -> JsString("NotDeclared"),
        Vector("powertypes", "1", "members", "reason") -> JsString("unknown"),
        Vector("generalizations", "0", "subtype", "value", "boundary") -> JsString("LOCAL"),
        Vector("powertypes", "0", "members", "value") -> Json.obj(),
        Vector("powertypes", "0", "members", "value", "0") -> JsNull,
        Vector("powertypes", "0", "members", "value", "0", "identity") -> Json.obj("vocabularyId" -> "model.example", "termId" -> "member"))
      When("the strict reader consumes each branch enum or array contradiction")
      val results = mutations.map { case (path, replacement) => CmlClassificationMetadata.read(_replace_child(root, path, replacement)) }
      Then("no coercion null default or Term-to-model conversion is admitted")
      results.map(_first(_).kind) shouldBe Vector.fill(mutations.size)(InvalidShape)
    }

    "CL-08 reject recursive embedded-core forgeries without echoing hostile fields" in {
      Given("a valid child embedded record with identity source and semantic-reference containers")
      val root = CmlClassificationMetadata.toJson(_graph())
      val record = _at(_child(root), Vector("traits", "0", "element")).as[JsObject]
      val mutations = Vector(record + ("hostile\npath" -> JsNull), record - "source", record + ("source" -> JsNull),
        record + ("identity" -> Json.obj("status" -> "present", "value" -> Json.obj("modelId" -> "evil", "elementId" -> "id"))),
        record + ("references" -> Json.obj("status" -> "present", "value" -> Json.arr(Json.obj("hostile" -> true)))))
      When("the strict reader compares each complete embedded value against admitted canonical core facts")
      val results = mutations.map(value => CmlClassificationMetadata.read(_replace_child(root, Vector("traits", "0", "element"), value)))
      Then("all embedded forgeries yield safe full-record binding diagnostics")
      results.foreach(result => _first(result) shouldBe ClassificationDiagnostic(InvalidCoreBinding,
        "extensions.cozy.cml.classification.traits[0].element", "The embedded Classification record does not exactly match admitted core JSON.", None))
    }

    "CL-08 retain Unicode quotes backslashes and embedded whitespace with proper JSON escaping" in {
      Given("valid literals with Unicode delimiters quotes backslashes and interior whitespace")
      val graph = _graph("作者:\"\\Aa", "理由 \"quoted\" \\ 条件Aa", "表示 \"quoted\" \\ 名")
      When("canonical JSON is parsed and strictly read")
      val text = CmlClassificationMetadata.canonicalJson(graph)
      val consumed = _graph_result(CmlClassificationMetadata.read(Json.parse(text)))
      Then("escaped text represents the exact admitted source identity and absence facts")
      consumed.catalog.elements shouldBe graph.catalog.elements
      consumed.catalog.terms shouldBe graph.catalog.terms
      consumed.powertypes shouldBe graph.powertypes
      consumed.generalizations shouldBe graph.generalizations
      CmlClassificationMetadata.canonicalJson(consumed) shouldBe text
      text.contains("\\\"") shouldBe true
      text.contains("\\\\") shouldBe true
    }
  }

  "Classification properties" should {
    "CL-09 preserve all facts under non-discarded qualified identity absence and independent-order permutations" in {
      Given("active generators for qualified Unicode IDs, four absence reasons, literal details and five projection orders")
      val generator = for {
        authority <- Gen.oneOf("a:b", "A:b", "作者:\"\\", "a/b")
        suffix <- Gen.choose(0, 10000)
        reason <- Gen.oneOf(_reasons)
        detail <- Gen.oneOf("Absent 条件Aa", "理由 \"quote\" \\ detail", "No declaration: Aa")
        coreorder <- Gen.pick(16, (0 until 16).toVector).map(_.toVector)
        traitorder <- Gen.pick(2, Vector(0, 1)).map(_.toVector)
        powertypeorder <- Gen.pick(2, Vector(0, 1)).map(_.toVector)
        generalizationorder <- Gen.pick(2, Vector(0, 1)).map(_.toVector)
        realizationorder <- Gen.pick(2, Vector(0, 1)).map(_.toVector)
        dimensionorder <- Gen.pick(3, Vector(0, 1, 2)).map(_.toVector)
        boundary <- Gen.oneOf(Local, External)
      } yield (authority + suffix, reason, detail, coreorder, traitorder, powertypeorder, generalizationorder, realizationorder, dimensionorder, boundary)
      val property = Prop.forAll(generator) { case (authority, reason, detail, coreorder, traitorder, powertypeorder, generalizationorder, realizationorder, dimensionorder, boundary) =>
        val base = _graph(authority, detail)
        val traitrecord = _record("trait", "trait-2", authority)
        val generalizationrecord = _record("generalization", "generalization-2", authority)
        val realizationrecord = _record("trait-realization", "realization-2", authority)
        val records = base.catalog.elements ++ Vector(traitrecord, generalizationrecord, realizationrecord)
        val catalog = _catalog(coreorder.map(records), base.catalog.terms)
        val powertypes = powertypeorder.map(index => base.powertypes(index).copy(members =
          if (index == 1) Absent(reason, detail) else base.powertypes(index).members))
        val traits = traitorder.map(base.traits :+ TraitProjection(traitrecord))
        val generalizations = generalizationorder.map(base.generalizations :+ GeneralizationProjection(generalizationrecord,
          Present(_reference("aggregate", boundary, authority)), Present(_reference("entity", Local, authority))))
        val realizations = realizationorder.map(base.traitRealizations :+ TraitRealizationProjection(realizationrecord,
          Absent(reason, detail), Present(_reference("trait-2", Local, authority))))
        val dimensions = dimensionorder.map(base.powertypeClassifications)
        val graph = _graph_result(CmlClassificationMetadata.build(catalog, traits, powertypes, generalizations,
          realizations, dimensions, Json.obj("org.example.peer" -> _peer)))
        val text = CmlClassificationMetadata.canonicalJson(graph)
        CmlClassificationMetadata.read(Json.parse(text)) match {
          case Left(_) => false
          case Right(consumed) => consumed.catalog.elements == catalog.elements && consumed.catalog.terms == catalog.terms &&
            consumed.catalog.elements.map(_.source) == catalog.elements.map(_.source) &&
            consumed.catalog.elements.map(_.references) == catalog.elements.map(_.references) &&
            consumed.traits == traits && consumed.powertypes == powertypes && consumed.generalizations == generalizations &&
            consumed.traitRealizations == realizations && consumed.powertypeClassifications == dimensions &&
            consumed.envelope.extensions.value("org.example.peer") == _peer &&
            CmlClassificationMetadata.canonicalJson(consumed) == text &&
            CmlClassificationMetadata.read(CmlClassificationMetadata.toJson(consumed)).isRight
        }
      }
      When("ScalaCheck runs one hundred successful cases without implication or discarded inputs")
      val result = Test.check(Test.Parameters.default.withMinSuccessfulTests(100), property)
      Then("every generated ordering and literal assignment retains the entire graph and canonical representation")
      result.passed shouldBe true
      result.succeeded should be >= 100
      result.discarded shouldBe 0
    }
  }

  private val _reasons: Vector[AbsenceReason] = Vector(NotDeclared, NotRepresented, Unsupported, NotApplicable)
  private val _peer: JsObject = Json.obj("z" -> Json.arr(JsNull, "retained", Json.obj("β" -> "value")), "a" -> true)

  private def _source(authority: String): SourceAttribution =
    SourceAttribution(authority, "src/main/cml/classification.cml", "a" * 64, Some(7))

  private def _record(kind: String, id: String, authority: String = "model.example", name: String = "共有"): ElementRecord =
    ElementRecord(Present(ModelElementId(authority, id)), kind, name, _source(authority), Present(Vector.empty))

  private def _reference(id: String, boundary: ReferenceBoundary = Local, authority: String = "model.example"): ModelReference =
    ModelReference(ModelElementId(authority, id), boundary)

  private def _graph(authority: String = "model.example", detail: String = "No members declared.", name: String = "共有"): Graph = {
    val absence = Absent(NotDeclared, detail)
    val semantic = SemanticReference(TermTarget(TermId(authority, "entity")), Local,
      Present(RelationId("bok.example", "entity")), Present(ProfileId("bok.example", "profile")),
      Present("Independent core reference fact."), Present(LocalizedLabel("共有", Some("ja"))), _source(authority), Declared)
    val kinds = Vector("entity", "value", "aggregate", "trait", "powertype", "powertype-kind", "generalization", "trait-realization",
      "powertype-classification", "powertype-classification", "powertype", "powertype-classification", "event")
    val ids = Vector("entity", "value", "aggregate", "trait", "powertype", "member", "generalization", "realization",
      "dimension-1", "dimension-2", "powertype-2", "dimension-3", "unrelated")
    val records = kinds.zip(ids).map { case (kind, id) => _record(kind, id, authority, name) }.updated(0,
      _record("entity", "entity", authority, name).copy(references = Present(Vector(semantic))))
    val catalog = _catalog(records, Vector(TermRecord(TermId(authority, "entity"), _source(authority))))
    def _local_(id: String): Presence[ModelReference] = Present(_reference(id, Local, authority))
    val powertypes = Vector(PowertypeProjection(records(4), Present(Vector(_reference("member", Local, authority), _reference("member", Local, authority)))),
      PowertypeProjection(records(10), absence))
    val dimensions = Vector(PowertypeClassificationProjection(records(8), _local_("entity"), _local_("powertype")),
      PowertypeClassificationProjection(records(9), _local_("entity"), _local_("powertype-2")),
      PowertypeClassificationProjection(records(11), _local_("entity"), _local_("powertype")))
    _graph_result(CmlClassificationMetadata.build(catalog, Vector(TraitProjection(records(3))), powertypes,
      Vector(GeneralizationProjection(records(6), _local_("entity"), _local_("aggregate"))),
      Vector(TraitRealizationProjection(records(7), _local_("entity"), _local_("trait"))), dimensions,
      Json.obj("org.example.peer" -> _peer)))
  }

  private def _catalog(records: Vector[ElementRecord], terms: Vector[TermRecord] = Vector.empty): Catalog =
    CmlSemanticFoundation.build(records, terms) match {
      case Right(catalog) => catalog
      case Left(diagnostics) => fail("Fixture admission failed: " + diagnostics)
    }

  private def _rebuild(
    graph: Graph,
    traits: Option[Vector[TraitProjection]] = None,
    powertypes: Option[Vector[PowertypeProjection]] = None,
    generalizations: Option[Vector[GeneralizationProjection]] = None,
    realizations: Option[Vector[TraitRealizationProjection]] = None,
    classifications: Option[Vector[PowertypeClassificationProjection]] = None,
    extensions: Option[JsObject] = None
  ): Either[Vector[ClassificationDiagnostic], Graph] =
    CmlClassificationMetadata.build(graph.catalog, traits.getOrElse(graph.traits), powertypes.getOrElse(graph.powertypes),
      generalizations.getOrElse(graph.generalizations), realizations.getOrElse(graph.traitRealizations),
      classifications.getOrElse(graph.powertypeClassifications), extensions.getOrElse(graph.envelope.extensions - namespace))

  private def _graph_result(result: Either[Vector[ClassificationDiagnostic], Graph]): Graph = result match {
    case Right(graph) => graph
    case Left(diagnostics) => fail("Expected admitted Classification graph: " + diagnostics)
  }

  private def _first(result: Either[Vector[ClassificationDiagnostic], Graph]): ClassificationDiagnostic = result match {
    case Left(diagnostics) if diagnostics.nonEmpty => diagnostics.head
    case _ => fail("Expected typed Classification rejection.")
  }

  private def _child(root: JsObject): JsObject = (root \ "extensions" \ namespace).as[JsObject]

  private def _with_child(root: JsObject, child: JsValue): JsObject =
    root + ("extensions" -> ((root \ "extensions").as[JsObject] + (namespace -> child)))

  private def _replace_child(root: JsObject, path: Vector[String], replacement: JsValue): JsObject =
    _with_child(root, _replace_json(_child(root), path, replacement))

  private def _at(value: JsValue, path: Vector[String]): JsValue =
    path.foldLeft(value) { (current, part) => current match {
      case objectvalue: JsObject => objectvalue.value(part)
      case JsArray(values) => values(part.toInt)
      case _ => fail("Invalid fixture coordinate.")
    }}

  private def _replace_json(value: JsValue, path: Vector[String], replacement: JsValue): JsValue =
    if (path.isEmpty) replacement else value match {
      case objectvalue: JsObject => objectvalue + (path.head -> _replace_json(objectvalue.value(path.head), path.tail, replacement))
      case JsArray(values) => JsArray(values.updated(path.head.toInt, _replace_json(values(path.head.toInt), path.tail, replacement)))
      case _ => fail("Invalid fixture replacement coordinate.")
    }
}
