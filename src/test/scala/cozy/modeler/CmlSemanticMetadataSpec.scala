package cozy.modeler

import scala.collection.immutable.ListMap
import scala.io.{Codec, Source}
import org.scalacheck.{Gen, Prop, Test}
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import play.api.libs.json.{JsArray, JsNull, JsNumber, JsObject, JsString, JsValue, Json}

import CmlSemanticFoundation._
import CmlSemanticMetadata._

/*
 * @since   Sep. 28, 2026
 * @version Sep. 28, 2026
 * @author  ASAMI, Tomoharu
 */
final class CmlSemanticMetadataSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  "CML semantic metadata" should {
    "serve a JSON-only consumer" which {
      "resolve qualified identities and retained reference evidence without parsing CML" in {
        Given("the checked-in synthetic semantic-metadata v1 JSON fixture")
        val fixture = _fixture
        val itemid = ModelElementId("model.example", "entity:item")
        val otherid = ModelElementId("model.example", "entity:other")
        val termid = TermId("bok.example", "term:item")
        val externalid = ModelElementId("partner.example", "entity:invoice")

        When("a consumer reconstructs its Catalog solely from the JSON value")
        val result = CmlSemanticMetadata.read(fixture)
        val envelope = _envelope(result)
        val other = _element(envelope.catalog.element(otherid))

        Then("same-named elements resolve only by their distinct qualified identities")
        envelope.catalog.element(itemid) shouldBe Right(envelope.catalog.elements.head)
        envelope.catalog.element(otherid) shouldBe Right(other)
        envelope.catalog.term(termid) shouldBe Right(envelope.catalog.terms.head)

        And("the local Term reference retains namespace, context, profile, label, source, and derivation evidence")
        other.references shouldBe a[Present[_]]
        val references = _references(other.references)
        references should have size 2
        references.head.target shouldBe TermTarget(termid)
        references.head.boundary shouldBe Local
        references.head.relation shouldBe Present(RelationId("bok.example", "relation:example"))
        references.head.profile shouldBe Present(ProfileId("bok.example", "profile:example"))
        references.head.context shouldBe Present("注文の文脈")
        references.head.preferredLabel shouldBe Present(LocalizedLabel("項目", Some("ja")))
        references.head.source.path shouldBe "src/main/cozy/example.cml"
        references.head.origin shouldBe Derived("admitted-example", Vector(envelope.catalog.terms.head.source))

        And("the explicit external target remains external without a local-resolution claim")
        references(1).target shouldBe ModelTarget(externalid)
        references(1).boundary shouldBe External
        references(1).origin shouldBe Declared
        envelope.catalog.element(externalid) shouldBe a[Left[_, _]]
      }
    }

    "publish and reconstruct admitted catalogs" which {
      "round-trip descriptions, absence, provenance, references, and lookup through the writer and reader" in {
        Given("a full admitted Catalog with present-empty references, all absence reasons, declared and derived origins")
        val catalog = _complete_catalog()
        val extensions = Json.obj("org.example.projection" -> Json.obj("unknown" -> Json.arr(JsNull, "retained")))
        val envelope = _envelope(CmlSemanticMetadata.build(catalog, extensions))

        When("the Catalog is rendered canonically, parsed as trusted JSON, and read by a consumer")
        val rendered = CmlSemanticMetadata.canonicalJson(envelope)
        val reconstructed = _envelope(CmlSemanticMetadata.read(Json.parse(rendered)))

        Then("authored record order and descriptive strings remain separate from exact qualified lookup")
        reconstructed.catalog.elements shouldBe catalog.elements
        reconstructed.catalog.terms shouldBe catalog.terms
        reconstructed.catalog.elements(0).kind shouldBe ""
        reconstructed.catalog.elements(0).name shouldBe " Item\n項目 "
        reconstructed.catalog.element(ModelElementId("model.example", "entity:item")) shouldBe Right(catalog.elements.head)
        reconstructed.catalog.term(TermId("bok.example", "term:item")) shouldBe Right(catalog.terms.head)

        And("all four explicit absence reasons and optional line/language values retain their supplied meanings")
        val references = _references(reconstructed.catalog.elements(1).references)
        references(0).preferredLabel shouldBe Present(LocalizedLabel("No language", None))
        references(1).preferredLabel shouldBe Present(LocalizedLabel("項目", Some("ja")))
        references(2).relation shouldBe Absent(NotDeclared, "No relation is admitted.")
        references(2).profile shouldBe Absent(NotRepresented, "No profile is represented.")
        references(2).context shouldBe Absent(Unsupported, "No external context is supported.")
        references(2).preferredLabel shouldBe Absent(NotApplicable, "No label applies.")
        reconstructed.catalog.terms.head.source.line shouldBe None
        references(0).source.line shouldBe Some(4)
      }

      "round-trip the canonical empty document and retain typed unknown lookup" in {
        Given("the canonical empty Catalog and an explicitly empty extension object")
        val envelope = _envelope(CmlSemanticMetadata.build(Catalog(), Json.obj()))

        When("the compact canonical document is read back into a Catalog")
        val rendered = CmlSemanticMetadata.canonicalJson(envelope)
        val reconstructed = _envelope(CmlSemanticMetadata.read(Json.parse(rendered)))
        val missing = reconstructed.catalog.element(ModelElementId("model.example", "missing"))

        Then("the document is stable and missing lookup remains a typed dangling-reference result")
        rendered shouldBe CmlSemanticMetadata.canonicalJson(reconstructed)
        reconstructed.catalog.elements shouldBe Vector.empty
        reconstructed.catalog.terms shouldBe Vector.empty
        _lookup_diagnostic(missing).kind shouldBe DanglingReference
      }
    }

    "fail closed on schema and wire shape" which {
      "reject incompatible versions and malformed core values with typed deterministic diagnostics" in {
        Given("a valid fixture root plus unsupported, missing, extra, scalar, presence, tag, option, and extension alternatives")
        val root = _object(_fixture)
        val unsupported = root + ("schemaVersion" -> JsString("cozy.cml.semantic-metadata.v2"))
        val legacy = root + ("schemaVersion" -> JsString("cozy.cml.model-metadata.v1"))
        val missingschema = root - "schemaVersion"
        val nonschema = root + ("schemaVersion" -> JsNumber(BigDecimal(1)))
        val schemafirst = (root + ("schemaVersion" -> JsString("cozy.cml.semantic-metadata.v2"))) + ("elements" -> JsString("bad-catalog"))
        val missing = root - "terms"
        val extra = root ++ Json.obj("z-extra" -> JsNull, "a-extra" -> JsNull)
        val wrongarray = root + ("elements" -> JsString("not-an-array"))
        val wrongterms = root + ("terms" -> JsString("not-an-array"))
        val wrongrecord = root + ("elements" -> JsArray(Vector(JsString("not-an-element-record"))))
        val missingkind = _with_element(root, 0)(_ - "kind")
        val wrongkind = _with_element(root, 0)(_ + ("kind" -> JsNumber(BigDecimal(1))))
        val missingname = _with_element(root, 0)(_ - "name")
        val wrongname = _with_element(root, 0)(_ + ("name" -> JsNull))
        val missingreferences = _with_element(root, 0)(_ - "references")
        val extranested = _with_element(root, 0)(_ + ("unexpected" -> JsNull))
        val nullpresence = _with_element(root, 1)(element => element + ("identity" -> JsNull))
        val mixedpresence = _with_reference(root, 0) { reference =>
          val relation = _object(_field(reference, "relation"))
          reference + ("relation" -> (relation + ("reason" -> JsString("not-declared"))))
        }
        val unknownstatus = _with_reference(root, 0) { reference =>
          val relation = _object(_field(reference, "relation"))
          reference + ("relation" -> (relation + ("status" -> JsString("unknown"))))
        }
        val unknownreason = _with_reference(root, 1) { reference =>
          val relation = _object(_field(reference, "relation"))
          reference + ("relation" -> (relation + ("reason" -> JsString("unknown"))))
        }
        val unknowntarget = _with_reference(root, 0) { reference =>
          val target = _object(_field(reference, "target"))
          reference + ("target" -> (target + ("kind" -> JsString("unknown"))))
        }
        val unknownboundary = _with_reference(root, 0)(reference => reference + ("boundary" -> JsString("unknown")))
        val unknownorigin = _with_reference(root, 0) { reference =>
          val origin = _object(_field(reference, "origin"))
          reference + ("origin" -> (origin + ("kind" -> JsString("unknown"))))
        }
        val incompletesource = _with_element(root, 0) { element =>
          val source = _object(_field(element, "source"))
          element + ("source" -> (source - "line"))
        }
        val incompletelabel = _with_reference(root, 0) { reference =>
          val presence = _object(_field(reference, "preferredLabel"))
          val label = _object(_field(presence, "value"))
          reference + ("preferredLabel" -> (presence + ("value" -> (label - "text"))))
        }
        val incompleteidentity = _with_element(root, 0) { element =>
          val presence = _object(_field(element, "identity"))
          val identity = _object(_field(presence, "value"))
          element + ("identity" -> (presence + ("value" -> (identity - "modelId"))))
        }
        val wrongidentityshape = _with_element(root, 0) { element =>
          val presence = _object(_field(element, "identity"))
          element + ("identity" -> (presence + ("value" -> JsString("not-an-identity"))))
        }
        val wrongsourceshape = _with_element(root, 0)(_ + ("source" -> JsString("not-a-source")))
        val wronglabelshape = _with_reference(root, 0)(_ + ("preferredLabel" -> JsString("not-a-presence")))
        val wronglanguage = _with_reference(root, 0) { reference =>
          val presence = _object(_field(reference, "preferredLabel"))
          val label = _object(_field(presence, "value"))
          reference + ("preferredLabel" -> (presence + ("value" -> (label + ("language" -> JsNumber(BigDecimal(1)))))))
        }
        val fractionalline = _with_element(root, 0) { element =>
          val source = _object(_field(element, "source"))
          element + ("source" -> (source + ("line" -> JsNumber(BigDecimal("1.5")))))
        }
        val outofrangeline = _with_element(root, 0) { element =>
          val source = _object(_field(element, "source"))
          element + ("source" -> (source + ("line" -> JsNumber(BigDecimal(Int.MaxValue) + 1))))
        }
        val malformedextension = root + ("extensions" -> Json.obj("not-a-namespace" -> Json.obj()))
        val nonobjectextension = root + ("extensions" -> Json.obj("org.example.projection" -> JsString("not-an-object")))
        val cases = Vector(
          unsupported -> UnsupportedSchemaVersion,
          legacy -> UnsupportedSchemaVersion,
          missingschema -> InvalidShape,
          nonschema -> InvalidShape,
          schemafirst -> UnsupportedSchemaVersion,
          missing -> InvalidShape,
          extra -> InvalidShape,
          wrongarray -> InvalidShape,
          wrongterms -> InvalidShape,
          wrongrecord -> InvalidShape,
          missingkind -> InvalidShape,
          wrongkind -> InvalidShape,
          missingname -> InvalidShape,
          wrongname -> InvalidShape,
          missingreferences -> InvalidShape,
          extranested -> InvalidShape,
          nullpresence -> InvalidShape,
          mixedpresence -> InvalidShape,
          unknownstatus -> InvalidShape,
          unknownreason -> InvalidShape,
          unknowntarget -> InvalidShape,
          unknownboundary -> InvalidShape,
          unknownorigin -> InvalidShape,
          incompletesource -> InvalidShape,
          incompletelabel -> InvalidShape,
          incompleteidentity -> InvalidShape,
          wrongidentityshape -> InvalidShape,
          wrongsourceshape -> InvalidShape,
          wronglabelshape -> InvalidShape,
          wronglanguage -> InvalidShape,
          fractionalline -> InvalidShape,
          outofrangeline -> InvalidShape,
          malformedextension -> InvalidExtension,
          nonobjectextension -> InvalidExtension
        )

        When("each supplied JsValue is read without coercion, defaulting, or legacy migration")
        val outcomes = cases.map { case (value, expected) => (_first_diagnostic(CmlSemanticMetadata.read(value)), expected) } ++ Vector(
          _first_diagnostic(CmlSemanticMetadata.read(JsArray(Vector.empty))) -> InvalidShape,
          _first_diagnostic(CmlSemanticMetadata.read(null)) -> InvalidShape
        )

        Then("the first failure is typed, contains only a logical coordinate, and does not throw")
        outcomes.foreach { case (diagnostic, expected) =>
          diagnostic.kind shouldBe expected
          diagnostic.foundation shouldBe None
        }
        _first_diagnostic(CmlSemanticMetadata.read(extra)).path shouldBe "root"
        _first_diagnostic(CmlSemanticMetadata.read(schemafirst)).kind shouldBe UnsupportedSchemaVersion
        _first_diagnostic(CmlSemanticMetadata.read(_with_element(_with_element(root, 0)(_ + ("kind" -> JsNull)), 1)(_ + ("name" -> JsNull)))).path shouldBe "elements[0].kind"
      }

      "route shape-valid invalid catalog evidence through the accepted foundation validator" in {
        Given("shape-valid JSON with invalid identity, duplicate identity, dangling local target, and invalid source/reference evidence")
        val root = _object(_fixture)
        val invalididentity = _with_element(root, 0) { element =>
          val presence = _object(_field(element, "identity"))
          val identity = _object(_field(presence, "value"))
          element + ("identity" -> (presence + ("value" -> (identity + ("elementId" -> JsString(""))))))
        }
        val duplicate = _with_element(root, 1) { element =>
          val first = _object(_array_values(_field(root, "elements")).head)
          element + ("identity" -> _field(first, "identity"))
        }
        val duplicateterm = {
          val terms = _array_values(_field(root, "terms"))
          root + ("terms" -> JsArray(terms :+ terms.head))
        }
        val dangling = _with_reference(root, 0) { reference =>
          val target = _object(_field(reference, "target"))
          val identity = _object(_field(target, "identity"))
          reference + ("target" -> (target + ("identity" -> (identity + ("termId" -> JsString("term:missing"))))))
        }
        val danglingmodel = _with_reference(root, 1)(reference => reference + ("boundary" -> JsString("local")))
        val invalidauthority = _with_element(root, 0) { element =>
          val source = _object(_field(element, "source"))
          element + ("source" -> (source + ("authorityId" -> JsString("other.example"))))
        }
        val invalidsource = _with_element(root, 0) { element =>
          val source = _object(_field(element, "source"))
          element + ("source" -> (source + ("path" -> JsString("/workspace/secret.cml"))))
        }
        val invaliddigest = _with_element(root, 0) { element =>
          val source = _object(_field(element, "source"))
          element + ("source" -> (source + ("sha256" -> JsString("not-a-digest"))))
        }
        val invalidline = _with_element(root, 0) { element =>
          val source = _object(_field(element, "source"))
          element + ("source" -> (source + ("line" -> JsNumber(BigDecimal(0)))))
        }
        val invalidreference = _with_reference(root, 0) { reference =>
          val relation = _object(_field(reference, "relation"))
          val relationid = _object(_field(relation, "value"))
          val origin = _object(_field(reference, "origin"))
          reference ++ Json.obj(
            "relation" -> (relation + ("value" -> (relationid + ("relationId" -> JsString(""))))),
            "origin" -> (origin ++ Json.obj("ruleId" -> JsString(""), "sources" -> JsArray()))
          )
        }
        val invalidprofile = _with_reference(root, 0) { reference =>
          val profile = _object(_field(reference, "profile"))
          val profileid = _object(_field(profile, "value"))
          reference + ("profile" -> (profile + ("value" -> (profileid + ("profileId" -> JsString(""))))))
        }
        val invalidcontext = _with_reference(root, 0) { reference =>
          val context = _object(_field(reference, "context"))
          reference + ("context" -> (context + ("value" -> JsString(""))))
        }
        val invalidlabel = _with_reference(root, 0) { reference =>
          val presence = _object(_field(reference, "preferredLabel"))
          val label = _object(_field(presence, "value"))
          reference + ("preferredLabel" -> (presence + ("value" -> (label + ("text" -> JsString(""))))))
        }
        val invalidderivedsource = _with_reference(root, 0) { reference =>
          val origin = _object(_field(reference, "origin"))
          val sources = _array_values(_field(origin, "sources"))
          val source = _object(sources.head)
          reference + ("origin" -> (origin + ("sources" -> JsArray((source + ("sha256" -> JsString("not-a-digest"))) +: sources.tail))))
        }
        val invalidabsence = _with_reference(root, 1) { reference =>
          val relation = _object(_field(reference, "relation"))
          reference + ("relation" -> (relation + ("detail" -> JsString(""))))
        }
        val cases = Vector(
          invalididentity -> Vector(Diagnostic(InvalidIdentity, "elements[0].identity.elementId", "Identifier component must be nonempty, unpadded, and free of ISO control characters.")),
          duplicate -> Vector(Diagnostic(DuplicateIdentity, "elements[1].identity", "An admitted qualified identity must occur exactly once.")),
          duplicateterm -> Vector(Diagnostic(DuplicateIdentity, "terms[1].identity", "An admitted qualified identity must occur exactly once.")),
          dangling -> Vector(Diagnostic(DanglingReference, "elements[1].references[0].target", "The local Term target is not admitted.")),
          danglingmodel -> Vector(Diagnostic(DanglingReference, "elements[1].references[1].target", "The local model target is not admitted.")),
          invalidauthority -> Vector(Diagnostic(InvalidProvenance, "elements[0].source.authorityId", "The element source authority must equal its model identity authority.")),
          invalidsource -> Vector(Diagnostic(InvalidProvenance, "elements[0].source.path", "Source path must be a canonical project-relative POSIX path.")),
          invaliddigest -> Vector(Diagnostic(InvalidProvenance, "elements[0].source.sha256", "Source digest must be exactly 64 lowercase hexadecimal characters.")),
          invalidline -> Vector(Diagnostic(InvalidProvenance, "elements[0].source.line", "A supplied source line must be positive.")),
          invalidreference -> Vector(
            Diagnostic(InvalidIdentity, "elements[1].references[0].relation.relationId", "Identifier component must be nonempty, unpadded, and free of ISO control characters."),
            Diagnostic(InvalidReference, "elements[1].references[0].origin.ruleId", "Derived reference rule must be nonempty, unpadded, and free of ISO control characters."),
            Diagnostic(InvalidReference, "elements[1].references[0].origin.sources", "A derived reference requires at least one admitted input source.")
          ),
          invalidprofile -> Vector(Diagnostic(InvalidIdentity, "elements[1].references[0].profile.profileId", "Identifier component must be nonempty, unpadded, and free of ISO control characters.")),
          invalidcontext -> Vector(Diagnostic(InvalidReference, "elements[1].references[0].context", "Context must be nonempty, unpadded, and free of ISO control characters.")),
          invalidlabel -> Vector(Diagnostic(InvalidReference, "elements[1].references[0].preferredLabel.text", "Localized label text must be nonempty, unpadded, and free of ISO control characters.")),
          invalidderivedsource -> Vector(Diagnostic(InvalidProvenance, "elements[1].references[0].origin.sources[0].sha256", "Source digest must be exactly 64 lowercase hexadecimal characters.")),
          invalidabsence -> Vector(Diagnostic(InvalidReference, "elements[1].references[1].relation.detail", "Absence detail must be nonempty, unpadded, and free of ISO control characters."))
        )

        When("the strict reader reaches the foundation factory after complete shape decoding")
        val outcomes = cases.map { case (value, expected) => (_foundation_diagnostics(CmlSemanticMetadata.read(value)), expected) }

        Then("all returned values retain InvalidSemanticCatalog and the exact typed foundation failure")
        outcomes.foreach { case (diagnostics, expected) =>
          diagnostics.map(_.kind) shouldBe Vector.fill(expected.size)(InvalidSemanticCatalog)
          diagnostics.map(_.path) shouldBe expected.map(_.path)
          diagnostics.map(_.foundation) shouldBe expected.map(Some(_))
          diagnostics.map(_.detail) shouldBe Vector.fill(expected.size)("The decoded catalog violates an admitted semantic invariant.")
        }
      }
    }

    "isolate extensions and direct construction" which {
      "reject null namespace keys before sorting without throwing" in {
        Given("an admitted Catalog and null namespace keys alone or in either mixed insertion order")
        val catalog = _complete_catalog()
        val extensions = Vector(
          JsObject(ListMap[String, JsValue]((null, Json.obj()))),
          JsObject(ListMap[String, JsValue]((null, Json.obj()), "org.example.projection" -> Json.obj())),
          JsObject(ListMap[String, JsValue]("org.example.projection" -> Json.obj(), (null, Json.obj())))
        )

        When("the facade builder validates each directly supplied extension object")
        val results = extensions.map(extension => CmlSemanticMetadata.build(catalog, extension))

        Then("every call returns the exact typed logical diagnostic rather than throwing or producing an envelope")
        results.foreach { result =>
          _diagnostics(result) shouldBe Vector(PublicationDiagnostic(
            InvalidExtension,
            "extensions[0]",
            "The extension namespace is invalid.",
            None
          ))
        }
      }

      "reject null descriptions and invalid extension namespaces without leaking supplied paths" in {
        Given("an otherwise admitted Catalog with null descriptive fields and an invalid namespace token")
        val source = SourceAttribution("model.example", "src/main/cozy/example.cml", _zero_digest, Some(1))
        val record = ElementRecord(Present(ModelElementId("model.example", "entity:item")), null, null, source, Present(Vector.empty))
        val nullcatalog = _catalog(CmlSemanticFoundation.build(Vector(record), Vector.empty))
        val validcatalog = _complete_catalog()
        val invalidextensions = JsObject(ListMap("/Users/asami/private" -> Json.obj()))

        When("the facade builder is invoked without a constructor or copy bypass")
        val nullresult = CmlSemanticMetadata.build(nullcatalog, Json.obj())
        val extensionresult = CmlSemanticMetadata.build(validcatalog, invalidextensions)

        Then("both failures are typed and the extension diagnostic remains logical rather than echoing the hostile key")
        _diagnostics(nullresult).map(_.path) should contain allOf ("elements[0].kind", "elements[0].name")
        val extensiondiagnostic = _diagnostics(extensionresult).head
        extensiondiagnostic.kind shouldBe InvalidExtension
        extensiondiagnostic.path shouldBe "extensions[0]"
        extensiondiagnostic.detail should not include "/Users/asami/private"
      }

      "retain unknown namespaced payload without enabling a missing core semantic claim" in {
        Given("a fixture with arbitrary unfamiliar nested extension data")
        val envelope = _envelope(CmlSemanticMetadata.read(_fixture))
        val unknown = ModelElementId("model.example", "projection:missing")

        When("the envelope is rendered and read without interpreting its extension namespace")
        val reconstructed = _envelope(CmlSemanticMetadata.read(Json.parse(CmlSemanticMetadata.canonicalJson(envelope))))

        Then("opaque nested content remains available while core lookup still rejects a missing identity")
        reconstructed.extensions shouldBe envelope.extensions
        _lookup_diagnostic(reconstructed.catalog.element(unknown)).kind shouldBe DanglingReference
      }
    }

    "render deterministically under generated admitted variations" which {
      "round-trip Unicode delimiter identities with provenance absence and reference variation through actual lookup" in {
        Given("one hundred generated valid namespace, identity, provenance, absence, and reference variations")
        val variations = Gen.chooseNum(1, 100)

        When("each generated Catalog is written, parsed, read, and resolved by exact qualified identity")
        val property = Prop.forAll(variations) { variation =>
          val modelid = s"model.example:$variation"
          val targetid = ModelElementId(modelid, s"entity:項目:$variation")
          val ownerid = ModelElementId(modelid, s"entity:owner:$variation")
          val termid = TermId("bok.example", s"term:項目:$variation")
          val source = SourceAttribution(modelid, s"src/main/cozy/$variation/example.cml", _digest_for(variation), Some(variation))
          val termsource = SourceAttribution("bok.example", s"glossary/$variation/example.dox", _one_digest, None)
          val absence = _absence_reason_for(variation)
          val boundary = if (variation % 2 == 0) Local else External
          val relation = if (variation % 3 == 0)
            Present(RelationId("bok.example", s"relation:$variation"))
          else
            Absent(absence, s"No relation is admitted for $variation.")
          val profile = if (variation % 3 == 1)
            Present(ProfileId("bok.example", s"profile:$variation"))
          else
            Absent(absence, s"No profile is represented for $variation.")
          val context = if (variation % 3 == 2)
            Present(s"文脈 $variation")
          else
            Absent(absence, s"No context is admitted for $variation.")
          val label = if (variation % 2 == 0)
            Present(LocalizedLabel(s"項目 $variation", Some("ja")))
          else
            Absent(absence, s"No label is admitted for $variation.")
          val origin = if (variation % 2 == 0)
            Declared
          else
            Derived(s"admitted-rule-$variation", Vector(termsource))
          val reference = SemanticReference(
            TermTarget(termid),
            boundary,
            relation,
            profile,
            context,
            label,
            source,
            origin
          )
          CmlSemanticFoundation.build(Vector(
            ElementRecord(Present(targetid), "entity", s"項目 $variation", source, Present(Vector.empty)),
            ElementRecord(Present(ownerid), "service", s"Owner $variation", source, Present(Vector(reference)))
          ), Vector(TermRecord(termid, termsource))) match {
            case Right(catalog) =>
              CmlSemanticMetadata.build(catalog, Json.obj("org.example.projection" -> Json.obj("value" -> variation))) match {
                case Right(envelope) => CmlSemanticMetadata.read(Json.parse(CmlSemanticMetadata.canonicalJson(envelope))) match {
                  case Right(read) =>
                    read.catalog.elements == catalog.elements &&
                      read.catalog.terms == catalog.terms &&
                      read.catalog.element(ownerid) == Right(catalog.elements(1)) &&
                      read.catalog.term(termid) == Right(TermRecord(termid, termsource)) &&
                      _references(read.catalog.elements(1).references).head == reference
                  case Left(_) => false
                }
                case Left(_) => false
              }
            case Left(_) => false
          }
        }
        val check = Test.check(Test.Parameters.default.withMinSuccessfulTests(100), property)

        Then("every successful trial retains qualified lookup rather than names, delimiters, or source paths")
        check.passed shouldBe true
      }

      "canonicalize extension key insertion order without changing core identity or reference resolution" in {
        Given("one hundred generated extension insertion orders over the same admitted Catalog")
        val variations = Gen.chooseNum(1, 100)
        val catalog = _complete_catalog()
        val ownerid = ModelElementId("model.example", "entity:other")

        When("each pair of opaque extension maps is built and rendered through the canonical writer")
        val property = Prop.forAll(variations) { variation =>
          val firstnested = JsObject(ListMap(
            "z-inner" -> Json.obj("z-leaf" -> variation, "a-leaf" -> JsNull),
            "a-inner" -> JsArray(Vector(JsNumber(BigDecimal(variation)), JsNull))
          ))
          val secondnested = JsObject(ListMap(
            "a-inner" -> JsArray(Vector(JsNumber(BigDecimal(variation)), JsNull)),
            "z-inner" -> JsObject(ListMap("a-leaf" -> JsNull, "z-leaf" -> JsNumber(BigDecimal(variation))))
          ))
          val first = JsObject(ListMap(
            "org.example.projection" -> JsObject(ListMap("z-outer" -> firstnested, "a-outer" -> Json.arr(variation, JsNull))),
            "com.example.future" -> Json.obj("opaque" -> s"$variation")
          ))
          val second = JsObject(ListMap(
            "com.example.future" -> Json.obj("opaque" -> s"$variation"),
            "org.example.projection" -> JsObject(ListMap("a-outer" -> Json.arr(variation, JsNull), "z-outer" -> secondnested))
          ))
          (CmlSemanticMetadata.build(catalog, first), CmlSemanticMetadata.build(catalog, second)) match {
            case (Right(left), Right(right)) =>
              CmlSemanticMetadata.canonicalJson(left) == CmlSemanticMetadata.canonicalJson(right) &&
                (CmlSemanticMetadata.read(Json.parse(CmlSemanticMetadata.canonicalJson(left))) match {
                  case Right(read) =>
                    read.catalog.element(ownerid) == Right(read.catalog.elements(1)) &&
                      _references(read.catalog.elements(1).references)(1).target == TermTarget(TermId("bok.example", "term:item")) &&
                      read.catalog.term(TermId("bok.example", "term:item")) == Right(read.catalog.terms.head)
                  case Left(_) => false
                })
            case _ => false
          }
        }
        val check = Test.check(Test.Parameters.default.withMinSuccessfulTests(100), property)

        Then("every successful trial has identical compact JSON while opaque data leaves core semantics unchanged")
        check.passed shouldBe true
      }
    }
  }

  private val _zero_digest = "0" * 64
  private val _one_digest = "1" * 64

  private def _fixture: JsValue =
    Option(getClass.getResourceAsStream("/cozy/modeler/semantic-metadata-v1.json")) match {
      case Some(stream) =>
        try Json.parse(Source.fromInputStream(stream)(Codec.UTF8).mkString)
        finally stream.close()
      case None => fail("The semantic-metadata v1 fixture is required by this specification.")
    }

  private def _complete_catalog(): Catalog = {
    val targetid = ModelElementId("model.example", "entity:item")
    val ownerid = ModelElementId("model.example", "entity:other")
    val termid = TermId("bok.example", "term:item")
    val modelsourcetarget = SourceAttribution("model.example", "src/main/cozy/example.cml", _zero_digest, Some(2))
    val modelsourceowner = SourceAttribution("model.example", "src/main/cozy/example.cml", _zero_digest, Some(4))
    val termsource = SourceAttribution("bok.example", "glossary/example.dox", _one_digest, None)
    val localmodel = SemanticReference(
      ModelTarget(targetid), Local,
      Absent(NotDeclared, "No relation is admitted."),
      Absent(NotRepresented, "No profile is represented."),
      Present("Context with whitespace inside"),
      Present(LocalizedLabel("No language", None)),
      modelsourceowner,
      Declared
    )
    val localterm = SemanticReference(
      TermTarget(termid), Local,
      Present(RelationId("bok.example", "relation:example")),
      Present(ProfileId("bok.example", "profile:example")),
      Present("注文の文脈"),
      Present(LocalizedLabel("項目", Some("ja"))),
      modelsourceowner,
      Derived("admitted-example", Vector(termsource))
    )
    val external = SemanticReference(
      ModelTarget(ModelElementId("partner.example", "entity:invoice")), External,
      Absent(NotDeclared, "No relation is admitted."),
      Absent(NotRepresented, "No profile is represented."),
      Absent(Unsupported, "No external context is supported."),
      Absent(NotApplicable, "No label applies."),
      modelsourceowner,
      Declared
    )
    _catalog(CmlSemanticFoundation.build(Vector(
      ElementRecord(Present(targetid), "", " Item\n項目 ", modelsourcetarget, Present(Vector.empty)),
      ElementRecord(Present(ownerid), "entity", "Item", modelsourceowner, Present(Vector(localmodel, localterm, external)))
    ), Vector(TermRecord(termid, termsource))))
  }

  private def _with_element(root: JsObject, elementindex: Int)(change: JsObject => JsObject): JsObject = {
    val elements = _array_values(_field(root, "elements"))
    root + ("elements" -> JsArray(elements.zipWithIndex.map { case (value, index) =>
      if (index == elementindex) change(_object(value)) else value
    }))
  }

  private def _with_reference(root: JsObject, referenceindex: Int)(change: JsObject => JsObject): JsObject =
    _with_element(root, 1) { element =>
      val presence = _object(_field(element, "references"))
      val references = _array_values(_field(presence, "value"))
      element + ("references" -> (presence + ("value" -> JsArray(references.zipWithIndex.map { case (value, index) =>
        if (index == referenceindex) change(_object(value)) else value
      }))))
    }

  private def _envelope(value: Either[Vector[PublicationDiagnostic], Envelope]): Envelope =
    value match {
      case Right(envelope) => envelope
      case Left(diagnostics) => fail(s"Expected a valid metadata envelope but received: $diagnostics")
    }

  private def _catalog(value: Either[Vector[Diagnostic], Catalog]): Catalog =
    value match {
      case Right(catalog) => catalog
      case Left(diagnostics) => fail(s"Expected an admitted Catalog but received: $diagnostics")
    }

  private def _element(value: Either[Diagnostic, ElementRecord]): ElementRecord =
    value match {
      case Right(record) => record
      case Left(diagnostic) => fail(s"Expected an admitted element but received: $diagnostic")
    }

  private def _references(value: Presence[Vector[SemanticReference]]): Vector[SemanticReference] =
    value match {
      case Present(references) => references
      case _: Absent => fail("Expected explicitly present references.")
    }

  private def _lookup_diagnostic(value: Either[Diagnostic, ElementRecord]): Diagnostic =
    value match {
      case Left(diagnostic) => diagnostic
      case Right(_) => fail("Expected a typed dangling-reference diagnostic.")
    }

  private def _diagnostics(value: Either[Vector[PublicationDiagnostic], Envelope]): Vector[PublicationDiagnostic] =
    value match {
      case Left(diagnostics) => diagnostics
      case Right(_) => Vector.empty
    }

  private def _first_diagnostic(value: Either[Vector[PublicationDiagnostic], Envelope]): PublicationDiagnostic =
    _diagnostics(value) match {
      case diagnostic +: _ => diagnostic
      case Vector() => fail("Expected a typed publication diagnostic.")
    }

  private def _foundation_diagnostics(value: Either[Vector[PublicationDiagnostic], Envelope]): Vector[PublicationDiagnostic] =
    _diagnostics(value).filter(_.kind == InvalidSemanticCatalog)

  private def _object(value: JsValue): JsObject =
    value match {
      case objectvalue: JsObject => objectvalue
      case _ => fail("Expected a trusted fixture object.")
    }

  private def _field(value: JsObject, name: String): JsValue =
    value.value.get(name) match {
      case Some(field) => field
      case None => fail(s"Expected trusted fixture field: $name")
    }

  private def _array_values(value: JsValue): Vector[JsValue] =
    value match {
      case JsArray(values) => values.toVector
      case _ => fail("Expected a trusted fixture array.")
    }

  private def _absence_reason_for(value: Int): AbsenceReason =
    value % 4 match {
      case 0 => NotDeclared
      case 1 => NotRepresented
      case 2 => Unsupported
      case _ => NotApplicable
    }

  private def _digest_for(value: Int): String =
    ("0123456789abcdef" * 4).take(63) + (value % 16).toHexString
}
