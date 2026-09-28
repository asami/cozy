package cozy.modeler

import org.scalacheck.{Gen, Prop, Test}
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import CmlSemanticFoundation._

/*
 * @since   Sep. 28, 2026
 * @version Sep. 28, 2026
 * @author  ASAMI, Tomoharu
 */
final class CmlSemanticFoundationSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  "CML semantic foundation" should {
    "address admitted qualified identities" which {
      "resolve an admitted element and its local model and Term references with supplied evidence" in {
        Given("an admitted model element, Term, and source-attributed local references")
        val elementid = ModelElementId("sales", "Order")
        val termid = TermId("bok", "order")
        val ownerid = ModelElementId("sales", "OrderService")
        val ordersource = _source("sales", "src/main/cml/order.cml", 1)
        val ownersource = _source("sales", "src/main/cml/order-service.cml", 4)
        val termsource = _source("bok", "glossary/order.md", 7)
        val relation = RelationId("bok", "describes")
        val profile = ProfileId("profiles", "public")
        val reference = SemanticReference(
          ModelTarget(elementid),
          Local,
          Present(relation),
          Present(profile),
          Present("fulfillment context"),
          Present(LocalizedLabel("Order", Some("en"))),
          ownersource,
          Derived("cml-declaration", Vector(ordersource, termsource))
        )
        val termreference = SemanticReference(
          TermTarget(termid),
          Local,
          Absent(NotApplicable, "no relation classification is admitted"),
          Absent(NotApplicable, "no profile is admitted"),
          Absent(NotRepresented, "no context is admitted"),
          Absent(NotRepresented, "no label is admitted"),
          ownersource,
          Declared
        )
        val elements = Vector(
          ElementRecord(Present(elementid), "entity", "Order", ordersource, Present(Vector.empty)),
          ElementRecord(Present(ownerid), "service", "Order Service", ownersource, Present(Vector(reference, termreference)))
        )

        When("the admitted records are built into a catalog")
        val result = CmlSemanticFoundation.build(elements, Vector(TermRecord(termid, termsource)))

        Then("exact identities resolve the retained records and supplied relationship evidence")
        result shouldBe a[Right[_, _]]
        val catalog = _catalog(result)
        catalog.element(ownerid) shouldBe Right(elements(1))
        catalog.element(elementid) shouldBe Right(elements.head)
        catalog.term(termid) shouldBe Right(TermRecord(termid, termsource))
        elements(1).references shouldBe Present(Vector(reference, termreference))
        reference.relation shouldBe Present(relation)
        reference.profile shouldBe Present(profile)
        reference.context shouldBe Present("fulfillment context")
        reference.preferredLabel shouldBe Present(LocalizedLabel("Order", Some("en")))
        reference.origin shouldBe Derived("cml-declaration", Vector(ordersource, termsource))
      }

      "preserve lookup and target identity when descriptive and provenance values change without guessing a missing identity" in {
        Given("two admitted descriptions of the same qualified identity with different valid provenance")
        val targetid = ModelElementId("sales", "Order")
        val ownerid = ModelElementId("sales", "OrderService")
        val firstsource = _source("sales", "src/main/cml/order.cml", 1)
        val secondsource = _source("sales", "models/order-definition.cml", 88, _digest_b)
        val first = _catalog_with_reference(targetid, ownerid, "Order", "Order", firstsource)
        val second = _catalog_with_reference(targetid, ownerid, "Purchase", "Purchase record", secondsource)

        When("each admitted description is independently built and looked up by identity")
        val firstcatalog = _catalog(first)
        val secondcatalog = _catalog(second)

        Then("both lookups retain the same qualified target while a missing identity is not name-selected")
        _reference_target(firstcatalog, ownerid) shouldBe ModelTarget(targetid)
        _reference_target(secondcatalog, ownerid) shouldBe ModelTarget(targetid)
        secondcatalog.element(ModelElementId("sales", "Purchase")) shouldBe Left(Diagnostic(
          DanglingReference,
          "element",
          "No admitted model element has the requested qualified identity."
        ))
      }

      "keep namespaces, Term identities, and delimiter collisions distinct from display names" in {
        Given("same-named records with same local IDs in different namespaces and a delimiter collision")
        val north = ModelElementId("north", "Order")
        val south = ModelElementId("south", "Order")
        val joinedleft = ModelElementId("alpha:beta", "gamma")
        val joinedright = ModelElementId("alpha", "beta:gamma")
        val termid = TermId("north", "Order")
        val elements = Vector(
          _element(north, "Order", _source("north", "north/order.cml", 1)),
          _element(south, "Order", _source("south", "south/order.cml", 1)),
          _element(joinedleft, "Order", _source("alpha:beta", "left/order.cml", 1)),
          _element(joinedright, "Order", _source("alpha", "right/order.cml", 1))
        )

        When("the qualified records are built without name-based indexes")
        val catalog = _catalog(CmlSemanticFoundation.build(elements, Vector(TermRecord(termid, _source("north", "glossary/order.md", 1)))))

        Then("every qualified identity selects only its admitted record and a Term is not a model element")
        catalog.element(north) shouldBe Right(elements(0))
        catalog.element(south) shouldBe Right(elements(1))
        catalog.element(joinedleft) shouldBe Right(elements(2))
        catalog.element(joinedright) shouldBe Right(elements(3))
        catalog.term(termid) shouldBe Right(TermRecord(termid, _source("north", "glossary/order.md", 1)))
        catalog.element(ModelElementId("north", "Order ")) shouldBe a[Left[_, _]]
      }
    }

    "make admitted absence observable" which {
      "retain every closed absence reason separately from an explicitly empty reference vector and Present false" in {
        Given("identified records containing each admitted absence reason and one unidentified legacy record")
        val source = _source("sales", "src/main/cml/order.cml", 2)
        val absentidentity = ElementRecord(
          Absent(NotDeclared, "the legacy declaration has no admitted identity"),
          "legacy",
          "Order",
          source,
          Present(Vector.empty)
        )
        val notdeclaredid = ModelElementId("sales", "NotDeclared")
        val notrepresentedid = ModelElementId("sales", "NotRepresented")
        val unsupportedid = ModelElementId("sales", "Unsupported")
        val notapplicableid = ModelElementId("sales", "NotApplicable")
        val emptyid = ModelElementId("sales", "Order")
        val notdeclared = ElementRecord(
          Present(notdeclaredid), "entity", "Not Declared", source,
          Absent(NotDeclared, "the relation is not declared")
        )
        val notrepresented = ElementRecord(
          Present(notrepresentedid), "entity", "Not Represented", source,
          Absent(NotRepresented, "the relation is not represented")
        )
        val unsupported = ElementRecord(
          Present(unsupportedid), "entity", "Unsupported", source,
          Absent(Unsupported, "the relation is unsupported")
        )
        val notapplicable = ElementRecord(
          Present(notapplicableid), "entity", "Not Applicable", source,
          Absent(NotApplicable, "the relation is not applicable")
        )
        val emptyreferences = _element(emptyid, "Order", source)

        When("the records and generic presence values are retained by a catalog")
        val catalog = _catalog(CmlSemanticFoundation.build(Vector(
          absentidentity,
          notdeclared,
          notrepresented,
          unsupported,
          notapplicable,
          emptyreferences
        ), Vector.empty))
        val falsevalue: Presence[Boolean] = Present(false)

        Then("all absence reasons survive catalog lookup and differ from present false and an admitted empty vector")
        catalog.elements.head.identity shouldBe Absent(NotDeclared, "the legacy declaration has no admitted identity")
        _catalog_record(catalog.element(notdeclaredid)).references shouldBe Absent(NotDeclared, "the relation is not declared")
        _catalog_record(catalog.element(notrepresentedid)).references shouldBe Absent(NotRepresented, "the relation is not represented")
        _catalog_record(catalog.element(unsupportedid)).references shouldBe Absent(Unsupported, "the relation is unsupported")
        _catalog_record(catalog.element(notapplicableid)).references shouldBe Absent(NotApplicable, "the relation is not applicable")
        catalog.element(emptyid) shouldBe Right(emptyreferences)
        emptyreferences.references shouldBe Present(Vector.empty)
        falsevalue shouldBe Present(false)
        falsevalue should not equal Absent(Unsupported, "the relation is unsupported")
      }
    }

    "reject invalid admitted input" which {
      "report typed diagnostics for duplicates, dangling locals, malformed identifiers, and invalid provenance" in {
        Given("records containing duplicate identities and distinct malformed identity, source, and local-target evidence")
        val identity = ModelElementId("sales", "Order")
        val source = _source("sales", "src/main/cml/order.cml", 2)
        val duplicate = _element(identity, "Duplicate", source)
        val malformed = _element(ModelElementId("sales", " bad "), "Bad", source)
        val invalidsource = _element(ModelElementId("inventory", "Stock"), "Stock", SourceAttribution(
          "other",
          "/workspace/stock.cml",
          "ABC",
          Some(0)
        ))
        val danglingreference = SemanticReference(
          ModelTarget(ModelElementId("sales", "Missing")),
          Local,
          Present(RelationId("bok", "rel\u0001")),
          Absent(NotApplicable, "no profile"),
          Present(" context "),
          Present(LocalizedLabel("", Some(" en "))),
          source,
          Derived("", Vector.empty)
        )
        val owner = ElementRecord(Present(ModelElementId("sales", "Owner")), "service", "Owner", source, Present(Vector(danglingreference)))

        When("all supplied records are validated before catalog publication")
        val result = CmlSemanticFoundation.build(Vector(_element(identity, "Order", source), duplicate, malformed, invalidsource, owner), Vector(
          TermRecord(TermId("bok", "order"), _source("bok", "glossary/order.md", 1)),
          TermRecord(TermId("bok", "order"), _source("bok", "glossary/order-copy.md", 2))
        ))

        Then("the result contains typed identity, provenance, reference, duplicate, and dangling diagnostics")
        result shouldBe a[Left[_, _]]
        val diagnostics = _diagnostics(result)
        diagnostics.map(_.kind) should contain allOf (InvalidIdentity, InvalidProvenance, InvalidReference, DuplicateIdentity, DanglingReference)
        diagnostics.map(_.path) should contain ("elements[3].source.path")
        diagnostics.map(_.detail).mkString(" ") should not include "/workspace"
      }

      "reject an unregistered Local Term target without treating it as an external target" in {
        Given("an admitted owner with a Local reference to an unregistered qualified Term")
        val ownerid = ModelElementId("sales", "Order")
        val missingtermid = TermId("bok", "unregistered-order")
        val source = _source("sales", "src/main/cml/order.cml", 2)
        val reference = _reference(TermTarget(missingtermid), source, Declared)
        val owner = ElementRecord(Present(ownerid), "entity", "Order", source, Present(Vector(reference)))

        When("the catalog validates local targets against the admitted Term index")
        val result = CmlSemanticFoundation.build(Vector(owner), Vector.empty)

        Then("the missing Local Term is reported as a typed dangling reference")
        result shouldBe a[Left[_, _]]
        _diagnostics(result).map(_.kind) should contain (DanglingReference)
        _diagnostics(result).map(_.path) should contain ("elements[0].references[0].target")
      }

      "report table-driven malformed source and null string evidence as typed diagnostics" in {
        Given("in-memory records for every forbidden source form plus null identifier, provenance, and reference strings")
        val validsource = _source("sales", "src/main/cml/order.cml", 2)
        val invalidsources = Vector(
          ("drive path", SourceAttribution("sales", "C:/order.cml", _digest_a, Some(1)), "elements[0].source.path"),
          ("URI path", SourceAttribution("sales", "file://order.cml", _digest_a, Some(1)), "elements[0].source.path"),
          ("backslash path", SourceAttribution("sales", "src\\order.cml", _digest_a, Some(1)), "elements[0].source.path"),
          ("empty segment", SourceAttribution("sales", "src//order.cml", _digest_a, Some(1)), "elements[0].source.path"),
          ("dot segment", SourceAttribution("sales", "src/./order.cml", _digest_a, Some(1)), "elements[0].source.path"),
          ("dot-dot segment", SourceAttribution("sales", "src/../order.cml", _digest_a, Some(1)), "elements[0].source.path"),
          ("control path", SourceAttribution("sales", "src/ord\u0001er.cml", _digest_a, Some(1)), "elements[0].source.path"),
          ("uppercase digest", SourceAttribution("sales", "src/order.cml", "A" * 64, Some(1)), "elements[0].source.sha256"),
          ("short digest", SourceAttribution("sales", "src/order.cml", "a" * 63, Some(1)), "elements[0].source.sha256"),
          ("zero line", SourceAttribution("sales", "src/order.cml", _digest_a, Some(0)), "elements[0].source.line"),
          ("authority mismatch", SourceAttribution("other", "src/order.cml", _digest_a, Some(1)), "elements[0].source.authorityId")
        )
        val nullidentity = _element(ModelElementId(null, "Order"), "Order", validsource)
        val nullprovenance = _element(ModelElementId("sales", "Provenance"), "Provenance", SourceAttribution(null, null, null, None))
        val invalidreference = SemanticReference(
          ModelTarget(ModelElementId("external", "Order")),
          External,
          Present(RelationId("bok", null)),
          Present(ProfileId(null, " profile ")),
          Present(null),
          Present(LocalizedLabel(null, Some(null))),
          _source("other", "src/main/cml/reference.cml", 3),
          Derived(null, Vector.empty)
        )
        val absentdetail = ElementRecord(
          Present(ModelElementId("sales", "AbsentDetail")),
          "entity",
          "Absent Detail",
          validsource,
          Absent(NotDeclared, null)
        )
        val invalidreferenceowner = ElementRecord(
          Present(ModelElementId("sales", "ReferenceOwner")),
          "entity",
          "Reference Owner",
          validsource,
          Present(Vector(invalidreference))
        )

        When("each table case is built and every supplied string field is validated")
        val sourceoutcomes = invalidsources.map { case (_, invalidsource, expectedpath) =>
          (_diagnostics(CmlSemanticFoundation.build(Vector(_element(ModelElementId("sales", "Order"), "Order", invalidsource)), Vector.empty)), expectedpath)
        }
        val nulldiagnostics = _diagnostics(CmlSemanticFoundation.build(Vector(nullidentity, nullprovenance, absentdetail, invalidreferenceowner), Vector.empty))

        Then("each source form and null string is rejected through typed logical-field diagnostics")
        sourceoutcomes.foreach { case (diagnostics, expectedpath) =>
          diagnostics.map(_.kind) should contain (InvalidProvenance)
          diagnostics.map(_.path) should contain (expectedpath)
        }
        nulldiagnostics.map(_.kind) should contain allOf (InvalidIdentity, InvalidProvenance, InvalidReference)
        nulldiagnostics.map(_.path) should contain allOf (
          "elements[0].identity.modelId",
          "elements[1].source.authorityId",
          "elements[1].source.path",
          "elements[1].source.sha256",
          "elements[2].references.detail",
          "elements[3].references[0].relation.relationId",
          "elements[3].references[0].profile.vocabularyId",
          "elements[3].references[0].profile.profileId",
          "elements[3].references[0].context",
          "elements[3].references[0].preferredLabel.text",
          "elements[3].references[0].preferredLabel.language",
          "elements[3].references[0].source.authorityId",
          "elements[3].references[0].origin.ruleId"
        )
      }
    }

    "preserve reference boundaries and origins" which {
      "retain an explicit External target without claiming local resolution" in {
        Given("an element with an external model target absent from the local catalog")
        val ownerid = ModelElementId("sales", "Order")
        val externalid = ModelElementId("partner", "Invoice")
        val source = _source("sales", "src/main/cml/order.cml", 2)
        val external = SemanticReference(
          ModelTarget(externalid),
          External,
          Absent(NotDeclared, "no relation is admitted"),
          Absent(NotDeclared, "no profile is admitted"),
          Absent(NotDeclared, "no context is admitted"),
          Absent(NotDeclared, "no label is admitted"),
          source,
          Declared
        )

        When("the catalog validates the explicitly external reference")
        val catalog = _catalog(CmlSemanticFoundation.build(Vector(ElementRecord(Present(ownerid), "entity", "Order", source, Present(Vector(external)))), Vector.empty))

        Then("the target is retained as external while its local lookup remains unresolved")
        _reference_target(catalog, ownerid) shouldBe ModelTarget(externalid)
        catalog.element(externalid) shouldBe a[Left[_, _]]
      }

      "distinguish declared and derived origin evidence and reject incomplete derivation evidence" in {
        Given("declared and derived references with source evidence plus incomplete derivation alternatives")
        val targetid = ModelElementId("sales", "Order")
        val ownerid = ModelElementId("sales", "Owner")
        val source = _source("sales", "src/main/cml/owner.cml", 3)
        val input = _source("bok", "glossary/order.md", 5)
        val declared = _reference(ModelTarget(targetid), source, Declared)
        val derived = _reference(ModelTarget(targetid), source, Derived("projection-rule", Vector(input)))
        val invalidrule = _reference(ModelTarget(targetid), source, Derived("", Vector(input)))
        val invalidsources = _reference(ModelTarget(targetid), source, Derived("projection-rule", Vector(SourceAttribution("bok", "glossary/order.md", "bad", None))))

        When("complete and incomplete origin evidence is passed to build")
        val valid = CmlSemanticFoundation.build(Vector(_element(targetid, "Order", source), ElementRecord(Present(ownerid), "service", "Owner", source, Present(Vector(declared, derived)))), Vector.empty)
        val invalid = CmlSemanticFoundation.build(Vector(_element(targetid, "Order", source), ElementRecord(Present(ownerid), "service", "Owner", source, Present(Vector(invalidrule, invalidsources)))), Vector.empty)

        Then("declared and derived forms are retained while missing rule or invalid input source is rejected")
        _catalog(valid).element(ownerid) shouldBe Right(ElementRecord(Present(ownerid), "service", "Owner", source, Present(Vector(declared, derived))))
        _diagnostics(invalid).map(_.kind) should contain (InvalidReference)
        _diagnostics(invalid).map(_.kind) should contain (InvalidProvenance)
      }
    }

    "provide a canonical empty catalog" which {
      "return structured dangling-reference diagnostics for unknown model elements and Terms" in {
        Given("the canonical zero-argument catalog")
        val catalog = Catalog()
        val elementid = ModelElementId("sales", "Missing")
        val termid = TermId("bok", "missing")

        When("unknown identities are looked up without name fallback")
        val elementresult = catalog.element(elementid)
        val termresult = catalog.term(termid)

        Then("both lookups are structured dangling-reference results and the catalog remains empty")
        catalog shouldBe Catalog.empty
        catalog.elements shouldBe Vector.empty
        catalog.terms shouldBe Vector.empty
        elementresult shouldBe a[Left[_, _]]
        termresult shouldBe a[Left[_, _]]
        _diagnostics_from_lookup(elementresult).kind shouldBe DanglingReference
        _diagnostics_from_lookup(termresult).kind shouldBe DanglingReference
      }
    }

    "retain identity semantics under generated admissible variations" which {
      "keep identity lookup and reference targets invariant under descriptive and provenance variation" in {
        Given("one hundred generated valid descriptive names, labels, paths, lines, and digests")
        val variations = Gen.chooseNum(1, 100)

        When("ScalaCheck builds and looks up each independently varied admitted catalog")
        val property = Prop.forAll(variations) { variation =>
          val targetid = ModelElementId("sales", "Order")
          val ownerid = ModelElementId("sales", "Owner")
          val source = _source("sales", s"models/$variation/order.cml", variation, _digest_for(variation))
          val catalog = CmlSemanticFoundation.build(Vector(
            _element(targetid, s"Order $variation", source),
            ElementRecord(Present(ownerid), "service", s"Owner $variation", source, Present(Vector(SemanticReference(
              ModelTarget(targetid),
              Local,
              Absent(NotDeclared, "no relation"),
              Absent(NotDeclared, "no profile"),
              Present(s"context $variation"),
              Present(LocalizedLabel(s"label $variation", Some("en"))),
              source,
              Declared
            ))))
          ), Vector.empty)
          catalog match {
            case Right(value) => value.element(ownerid) == Right(value.elements(1)) && _reference_target(value, ownerid) == ModelTarget(targetid)
            case Left(_) => false
          }
        }
        val check = Test.check(Test.Parameters.default.withMinSuccessfulTests(100), property)

        Then("every generated catalog preserves exact identity lookup and reference targeting")
        check.passed shouldBe true
      }

      "index qualified identities independently of authored order without selecting a duplicate winner" in {
        Given("one hundred generated order choices for namespace-local and delimiter-collision identities")
        val orderings = Gen.chooseNum(0, 99)

        When("ScalaCheck builds reordered valid records and duplicate candidates")
        val property = Prop.forAll(orderings) { variation =>
          val left = ModelElementId("alpha:beta", "gamma")
          val right = ModelElementId("alpha", "beta:gamma")
          val north = ModelElementId("north", "Order")
          val south = ModelElementId("south", "Order")
          val records = Vector(
            _element(left, "Order", _source("alpha:beta", "left/order.cml", 1)),
            _element(right, "Order", _source("alpha", "right/order.cml", 1)),
            _element(north, "Order", _source("north", "north/order.cml", 1)),
            _element(south, "Order", _source("south", "south/order.cml", 1))
          )
          val ordered = if (variation % 2 == 0) records else records.reverse
          val valid = CmlSemanticFoundation.build(ordered, Vector.empty)
          val duplicate = CmlSemanticFoundation.build(ordered :+ _element(north, "Order", _source("north", "north/copy.cml", 2)), Vector.empty)
          valid match {
            case Right(catalog) =>
              catalog.element(left) == Right(records(0)) &&
                catalog.element(right) == Right(records(1)) &&
                catalog.element(north).isRight &&
                catalog.element(south).isRight &&
                _diagnostics(duplicate).exists(_.kind == DuplicateIdentity)
            case Left(_) => false
          }
        }
        val check = Test.check(Test.Parameters.default.withMinSuccessfulTests(100), property)

        Then("each ordering retains distinct qualified pairs and rejects duplicate identity selection")
        check.passed shouldBe true
      }
    }
  }

  private val _digest_a = "a" * 64
  private val _digest_b = "b" * 64

  private def _source(authority: String, path: String, line: Int, digest: String = _digest_a): SourceAttribution =
    SourceAttribution(authority, path, digest, Some(line))

  private def _element(identity: ModelElementId, name: String, source: SourceAttribution): ElementRecord =
    ElementRecord(Present(identity), "entity", name, source, Present(Vector.empty))

  private def _reference(target: SemanticTarget, source: SourceAttribution, origin: ReferenceOrigin): SemanticReference =
    SemanticReference(
      target,
      Local,
      Absent(NotDeclared, "no relation is admitted"),
      Absent(NotDeclared, "no profile is admitted"),
      Absent(NotDeclared, "no context is admitted"),
      Absent(NotDeclared, "no label is admitted"),
      source,
      origin
    )

  private def _catalog_with_reference(
    targetid: ModelElementId,
    ownerid: ModelElementId,
    targetname: String,
    ownername: String,
    source: SourceAttribution
  ): Either[Vector[Diagnostic], Catalog] =
    CmlSemanticFoundation.build(Vector(
      _element(targetid, targetname, source),
      ElementRecord(Present(ownerid), "service", ownername, source, Present(Vector(_reference(ModelTarget(targetid), source, Declared))))
    ), Vector.empty)

  private def _catalog(value: Either[Vector[Diagnostic], Catalog]): Catalog =
    value match {
      case Right(catalog) => catalog
      case Left(diagnostics) => fail(s"Expected an admitted catalog but received: $diagnostics")
    }

  private def _diagnostics(value: Either[Vector[Diagnostic], Catalog]): Vector[Diagnostic] =
    value match {
      case Left(diagnostics) => diagnostics
      case Right(_) => Vector.empty
    }

  private def _diagnostics_from_lookup[A](value: Either[Diagnostic, A]): Diagnostic =
    value match {
      case Left(diagnostic) => diagnostic
      case Right(_) => fail("Expected a structured dangling-reference diagnostic.")
    }

  private def _reference_target(catalog: Catalog, ownerid: ModelElementId): SemanticTarget =
    _catalog_record(catalog.element(ownerid)).references match {
      case Present(references) => references.head.target
      case _: Absent => fail("Expected the owner to retain an admitted reference.")
    }

  private def _catalog_record(value: Either[Diagnostic, ElementRecord]): ElementRecord =
    value match {
      case Right(record) => record
      case Left(diagnostic) => fail(s"Expected an admitted element but received: $diagnostic")
    }

  private def _digest_for(value: Int): String =
    ("0123456789abcdef" * 4).take(63) + (value % 16).toHexString
}
