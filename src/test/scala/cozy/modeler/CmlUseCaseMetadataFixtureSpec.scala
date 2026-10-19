package cozy.modeler

import scala.io.Source
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalacheck.{Gen, Prop, Test}
import play.api.libs.json.{JsArray, JsBoolean, JsNull, JsNumber, JsObject, JsString, JsValue, Json}

import CmlSemanticFoundation._
import CmlDynamicMetadata.{DeclaredExpression, ModelReference}
import CmlUseCaseMetadata._
import CmlUseCaseNavigation.{ExternalTarget, LocalTarget}

/*
 * @since   Oct.  4, 2026
 * @version Oct. 18, 2026
 * @author  ASAMI, Tomoharu
 */
class CmlUseCaseMetadataFixtureSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  import CmlUseCaseMetadataFixtureSpec._

  "Fixed Use Case handoff" should {
    "complete declared facts" which {
      "UF-01 retain every literal core source Term carrier and independent canonical wire value" in {
        Given("the declared filename and independent seven-record four-projection literal declarations")
        val filename = _filename(false)
        val literal = _literal(false)
        When("actual UTF-8 bytes and the accepted Dynamic prefix are read and the independent graph is built")
        val prefix = _prefix()
        val expected = _complete(prefix, literal)
        val result = _exercise(_resource(filename), prefix, expected)
        Then("49 full records four Terms all fields and the four opaque peers survive every consumption route")
        result should contain only true
        expected.core.size shouldBe 49
        expected.terms.size shouldBe 4
        literal.actors.size shouldBe 2
        literal.usecases.size shouldBe 2
        literal.usecases(1).summary shouldBe Present("")
        literal.usecases(1).trigger shouldBe Present(DeclaredExpression("", Present(Vector.empty)))
        literal.usecases(1).flows shouldBe Present(Vector.empty)
        literal.actors(1).workflows shouldBe Present(Vector.empty)
      }
      "UF-02 follow declared Actor UseCase Workflow Dynamic and static facts through four independent admissions" in {
        Given("complete literal Use Case references and the accepted prefix's complete Dynamic and static carriers")
        val literal = _literal(false)
        When("all four peers independently admit the same actual envelope and consumers query the declared references")
        val prefix = _prefix()
        val expected = _complete(prefix, literal)
        val result = _consumers(_resource(_filename(false)), prefix, expected)
        Then("full roles flows expression context static records and 23 nodes eight links equal independent facts")
        result should contain only true
      }
    }
    "explicit unavailability" which {
      "UF-03 preserve every cyclic reason detail full embedded record and the unchanged declared Dynamic prefix" in {
        Given("the absence filename and independently authored complete eight-field and sixteen-field cycles")
        val literal = _literal(true)
        When("the absence resource is strictly read independently built rendered and canonically reconsumed")
        val prefix = _prefix()
        val result = _exercise(_resource(_filename(true)), prefix, _complete(prefix, literal))
        Then("all complete expected values retain explicit gaps including two anonymous core identities")
        result should contain only true
        literal.core(0).identity shouldBe _anonymous
        literal.core(2).identity shouldBe _anonymous
        literal.actors(0).actorKind shouldBe _absences(0)
        literal.actors(1).domainElements shouldBe _absences(0)
        literal.usecases(0).realizingWorkflows shouldBe _absences(1)
        literal.usecases(1).realizingWorkflows shouldBe _absences(2)
      }
      "UF-04 keep named model and Term domains distinct and anonymous occurrences unaddressable" in {
        Given("both independent modes and well-formed descriptive name declaredId source and ordinal guesses")
        val literals = Vector(_literal(false), _literal(true))
        val guesses = Vector("共有", "UC-COMPLETE", "src/main/cml/use-case-handoff.cml", "45", "0")
        When("both resources are admitted and exact named anonymous Term and guessed identity queries run")
        val prefix = _prefix()
        val results = literals.zipWithIndex.map { case (literal, index) =>
          val expected = _complete(prefix, literal)
          val raw = _resource(_filename(index == 1))
          val graph = _right(CmlUseCaseMetadata.read(raw))
          val navigation = _right(CmlUseCaseNavigation.build(graph))
          val guessed = guesses.flatMap { guess => Vector(
            navigation.actor(_id(guess)) == Left(_unknown_projection("actor.identity")),
            navigation.useCase(_id(guess)) == Left(_unknown_projection("useCase.identity")),
            navigation.element(_id(guess)) == Left(_unknown_element))
          }
          _queries(navigation, expected) ++ guessed ++ _consumers(raw, prefix, expected)
        }
        Then("Term success cannot rescue an anonymous model query and named absent outbound fields create no links")
        results.flatten should contain only true
      }
    }
    "predecessor and opaque cohabitation" which {
      "UF-05 preserve the complete accepted prefix while independently admitting publication and every peer" in {
        Given("both literal modes the six accepted extensions and the fourth peer's ordered values and null")
        val literals = Vector(_literal(false), _literal(true))
        When("the two resources pass separate Foundation publication Use Case Dynamic Structure and Classification gates")
        val prefix = _prefix()
        val results = literals.zipWithIndex.flatMap { case (literal, index) =>
          val expected = _complete(prefix, literal)
          val raw = _resource(_filename(index == 1))
          val graph = _right(CmlUseCaseMetadata.read(raw))
          val rendered = CmlUseCaseMetadata.toJson(graph)
          val built = _build(prefix, expected)
          val foundation = _right(CmlSemanticFoundation.build(graph.catalog.elements, graph.catalog.terms))
          val publication = _right(CmlSemanticMetadata.read(raw))
          val republished = _right(CmlSemanticMetadata.build(foundation, _extensions(raw)))
          Vector(raw, rendered, CmlUseCaseMetadata.toJson(built), CmlSemanticMetadata.toJson(republished)).flatMap { value =>
            val elements = (value \ "elements").as[Vector[JsValue]]
            val terms = (value \ "terms").as[Vector[JsValue]]
            Vector(elements.take(42) == (prefix.raw \ "elements").as[Vector[JsValue]],
              terms.take(3) == (prefix.raw \ "terms").as[Vector[JsValue]],
              prefix.dynamic.catalog.elements == foundation.elements.take(42),
              prefix.dynamic.catalog.terms == foundation.terms.take(3),
              publication.catalog.elements == expected.core,
              _extensions(value).value(_peer) == _peer_value) ++
              _extensions(prefix.raw).fields.map { case (key, item) => _extensions(value).value(key) == item } ++
              _peers(value, prefix, expected)
          }
        }
        Then("all first 42 records three Terms predecessor carrier vectors and four opaque peers remain complete")
        results should contain only true
      }
    }
  }

  "Fixed Use Case admission failures" should {
    "closed recursive shapes" which {
      "UF-06 report literal safe diagnostics for single mutations with base admission and version precedence" in {
        Given("independent mutation coordinates and complete child-local and publication diagnostic literals")
        val mutations = _shape_mutations
        val publication = UseCaseDiagnostic(PublicationFailure, "extensions[3]",
          "The foundation metadata publication is invalid.", Some(CmlSemanticMetadata.PublicationDiagnostic(
            CmlSemanticMetadata.InvalidExtension, "extensions[3]", "The extension namespace is invalid.", None)))
        When("each single mutation changes actual parsed declared bytes before strict Use Case admission")
        val raw = _resource(_filename(false))
        val observed = mutations.map { mutation =>
          CmlUseCaseMetadata.read(_change(raw, _child_path ++ mutation.path, mutation.update))
        }
        val missing = CmlUseCaseMetadata.read(_change(raw, Vector("extensions"), value => value.as[JsObject] - namespace))
        val nullchild = CmlUseCaseMetadata.read(_change(raw, _child_path, _ => JsNull))
        Then("closed fields vectors Presence references identities expressions participation flows and full core fail exactly")
        observed shouldBe mutations.map { mutation => Left(Vector(UseCaseDiagnostic(mutation.kind,
          _wire_path + mutation.coordinate, mutation.detail, None))) }
        missing shouldBe Left(Vector(_diagnostic(InvalidShape, _wire_path, _shape_detail)))
        nullchild shouldBe Left(Vector(publication))
      }
    }
    "complete core occurrence binding" which {
      "UF-07 reject every embedded forgery and missing or duplicate Actor and UseCase occurrence in both modes" in {
        Given("independent identity kind name source and reference-Presence forgeries with exact multiset failures")
        val literals = Vector(_literal(false), _literal(true))
        val wirechanges = Vector(
          Vector("identity") -> _presence_json(Present(_id("forged:identity")), _identity_json),
          Vector("kind") -> JsString("component"),
          Vector("name") -> JsString("forged"),
          Vector("source", "line") -> JsNumber(99),
          Vector("references") -> Json.obj("status" -> "present", "value" -> Json.arr()))
        When("only embedded records or projection occurrences change while each admitted Catalog stays intact")
        val prefix = _prefix()
        val results = literals.zipWithIndex.flatMap { case (literal, mode) =>
          val expected = _complete(prefix, literal)
          val raw = _resource(_filename(mode == 1))
          val catalog = _right(CmlSemanticFoundation.build(expected.core, expected.terms))
          Vector("actors", "useCases").flatMap { family =>
            val record = if (family == "actors") expected.actors.head.element else expected.usecases.head.element
            val forgedrecords = Vector(record.copy(identity = Present(_id("forged:identity"))),
              record.copy(kind = "component"), record.copy(name = "forged"),
              record.copy(source = record.source.copy(line = Some(99))), record.copy(references = Present(Vector.empty)))
            val wire = wirechanges.map { case (path, value) =>
              CmlUseCaseMetadata.read(_change(raw, _child_path ++ Vector(family, "0", "element") ++ path, _ => value)) ==
                Left(Vector(_diagnostic(InvalidCoreBinding, _wire_path + "." + family + "[0].element", _embedded_detail)))
            }
            val typed = forgedrecords.map { forged =>
              val actors = if (family == "actors") expected.actors.updated(0, expected.actors.head.copy(element = forged)) else expected.actors
              val usecases = if (family == "useCases") expected.usecases.updated(0, expected.usecases.head.copy(element = forged)) else expected.usecases
              CmlUseCaseMetadata.build(catalog, actors, usecases, _peer_extensions(prefix)) == Left(Vector(
                _diagnostic(InvalidCoreBinding, family + "[0].element", _binding_detail),
                _diagnostic(MissingProjection, family + "[0]", _missing_detail)))
            }
            val occurrences = Vector(false, true).flatMap { duplicate =>
              val diagnostic = if (duplicate) _diagnostic(DuplicateProjection, family + "[0]", _duplicate_detail)
                else _diagnostic(MissingProjection, family + "[0]", _missing_detail)
              val changed = _change(raw, _child_path :+ family, value => {
                val values = value.as[Vector[JsValue]]
                JsArray(if (duplicate) values :+ values.head else values.tail)
              })
              val actors = if (family != "actors") expected.actors else if (duplicate) expected.actors :+ expected.actors.head else expected.actors.tail
              val usecases = if (family != "useCases") expected.usecases else if (duplicate) expected.usecases :+ expected.usecases.head else expected.usecases.tail
              Vector(CmlUseCaseMetadata.read(changed) == Left(Vector(diagnostic)),
                CmlUseCaseMetadata.build(catalog, actors, usecases, _peer_extensions(prefix)) == Left(Vector(diagnostic)))
            }
            wire ++ typed ++ occurrences
          } ++ _exercise(raw, prefix, expected)
        }
        Then("read and factory require every complete record occurrence and preserve both original valid modes")
        results should contain only true
      }
    }
    "qualified Local and External references" which {
      "UF-08 reject wrong-kind or missing Local claims and preserve exact External alternatives at every reference context" in {
        Given("independent wrong-family or missing pairs for all family fields and every declared nested expression context")
        val literal = _literal(false)
        val mutations = _reference_mutations
        When("actual wire and independently authored typed references are replaced and read built rendered and navigated")
        val prefix = _prefix()
        val expected = _complete(prefix, literal)
        val raw = _resource(_filename(false))
        val results = mutations.flatMap { mutation =>
          val local = ModelReference(mutation.wrong, Local)
          val changed = _change(raw, _child_path ++ mutation.path, _ => _reference_json(local))
          val typed = _replace_reference(expected, mutation.path, local)
          val catalog = _right(CmlSemanticFoundation.build(expected.core, expected.terms))
          val failure = Left(Vector(_diagnostic(InvalidUseCaseSemantics, mutation.coordinate, _semantic_detail)))
          val rejected = Vector(CmlUseCaseMetadata.read(changed) == failure,
            CmlUseCaseMetadata.build(catalog, typed.actors, typed.usecases, _peer_extensions(prefix)) == failure)
          val alternatives = Vector(mutation.wrong, _id("missing:external"), _dynamic_id("workflow:fulfillment")).flatMap { id =>
            val reference = ModelReference(id, External)
            val externalraw = _change(raw, _child_path ++ mutation.path, _ => _reference_json(reference))
            val externalexpected = _replace_reference(expected, mutation.path, reference)
            val graph = _right(CmlUseCaseMetadata.read(externalraw))
            val navigation = _right(CmlUseCaseNavigation.build(graph))
            _exercise(externalraw, prefix, externalexpected) ++ Vector(
              navigation.resolve(reference) == Right(ExternalTarget(reference)),
              navigation.resolve(ModelReference(_dynamic_id("workflow:fulfillment"), Local)) ==
                Right(LocalTarget(_record(expected.core, _dynamic_id("workflow:fulfillment")))))
          }
          rejected ++ alternatives
        }
        Then("exact factory-coordinate diagnostics reject Local claims while opaque equal wrong-kind and missing External pairs survive")
        results should contain only true
      }
    }
  }

  "Active fixed-resource properties" should {
    "independent observable order and recursive key reconstruction" which {
      "UF-09 preserve both modes under six independent Catalog Actor and UseCase priority plans" taggedAs org.scalatest.tagobjects.Slow in {
        Given("six bounded independent priority vectors and literal declared and absence modes without discarded plans")
        val generator = for {
          declaredcore <- _priorities(49)
          declaredactors <- _priorities(2)
          declaredusecases <- _priorities(2)
          absentcore <- _priorities(49)
          absentactors <- _priorities(2)
          absentusecases <- _priorities(2)
          reverse <- Gen.oneOf(false, true)
        } yield (Vector((declaredcore, declaredactors, declaredusecases), (absentcore, absentactors, absentusecases)), reverse)
        When("ScalaCheck executes at least 100 samples and each action admits both actual resources and independent factories")
        val property = Prop.forAll(generator) { plan =>
          Given("one independent two-mode plan and complete literal fixture expectations")
          val literals = Vector(_literal(false), _literal(true))
          When("actual raw arrays are permuted keys rebuilt and consumer queries run in each chosen Catalog order")
          val prefix = _prefix()
          val results = literals.zipWithIndex.flatMap { case (literal, index) =>
            val complete = _complete(prefix, literal)
            val (corepriorities, actorpriorities, usecasepriorities) = plan._1(index)
            val coreorder = _order(complete.core, corepriorities)
            val actororder = _order(complete.actors, actorpriorities)
            val usecaseorder = _order(complete.usecases, usecasepriorities)
            val expected = complete.copy(core = coreorder.map(complete.core), actors = actororder.map(complete.actors),
              usecases = usecaseorder.map(complete.usecases))
            val raw = _resource(_filename(index == 1))
            val corepermuted = _change(raw, Vector("elements"), value => JsArray(coreorder.map(value.as[Vector[JsValue]])))
            val actorpermuted = _change(corepermuted, _child_path :+ "actors", value => JsArray(actororder.map(value.as[Vector[JsValue]])))
            val permuted = _change(actorpermuted, _child_path :+ "useCases", value => JsArray(usecaseorder.map(value.as[Vector[JsValue]])))
            val reconstructed = _keys(permuted, plan._2).as[JsObject]
            Vector(expected.core != complete.core, expected.actors != complete.actors, expected.usecases != complete.usecases,
              expected.terms == complete.terms) ++ _exercise(reconstructed, prefix, expected) ++ _consumers(reconstructed, prefix, expected)
          }
          Then("complete facts independent wire qualified queries and Catalog-ordered Dynamic nodes and links remain correct")
          results should contain only (true)
          Prop(results.forall(identity))
        }
        val result = Test.check(Test.Parameters.default.withMinSuccessfulTests(100), property)
        Then("the actual property reports Passed at least 100 successes and no discards")
        info(result.toString)
        result.status shouldBe Test.Passed
        result.succeeded should be >= 100
        result.discarded shouldBe 0
      }
    }
  }

  private val _wire_path = "extensions.cozy.cml.use-case"
  private val _child_path = Vector("extensions", namespace)
  private val _peer = "org.example.use-case-handoff"
  private val _peer_value = Json.obj("producer" -> "synthetic-caller",
    "declaredFlowOrder" -> Json.arr("main", "alternate", "exception"), "absence" -> JsNull)
  private val _anonymous = Absent(NotRepresented, "No stable Use Case or Actor identity is represented.")
  private val _absences = Vector(
    Absent(NotDeclared, "No Use Case fact is declared."),
    Absent(NotRepresented, "The Use Case fact is not represented."),
    Absent(Unsupported, "The Use Case fact is unsupported."),
    Absent(NotApplicable, "The Use Case fact is not applicable."))
  private val _shape_detail = "The Use Case value does not match the required v1 shape."
  private val _semantic_detail = "The declared Use Case reference or absence is invalid."
  private val _embedded_detail = "The embedded Use Case record does not exactly match admitted core JSON."
  private val _binding_detail = "The Use Case projection does not bind exactly to an admitted core record."
  private val _missing_detail = "An admitted Use Case or Actor record lacks its required projection occurrence."
  private val _duplicate_detail = "A Use Case or Actor record has excess projection occurrences."

  private def _id(value: String): ModelElementId = { ModelElementId("usecase.example", value) }
  private def _dynamic_id(value: String): ModelElementId = { ModelElementId("dynamic.example", value) }
  private def _ref(model: String, value: String): ModelReference = { ModelReference(ModelElementId(model, value), Local) }
  private def _external(value: String): ModelReference = { ModelReference(ModelElementId("external.example", value), External) }
  private def _refs(model: String, values: String*): Presence[Vector[ModelReference]] = {
    Present(values.toVector.map(_ref(model, _)))
  }
  private def _expression(text: String, references: ModelReference*): DeclaredExpression = {
    DeclaredExpression(text, Present(references.toVector))
  }
  private def _source(line: Int): SourceAttribution = {
    SourceAttribution("usecase.example", "src/main/cml/use-case-handoff.cml", Some(line))
  }
  private def _literal(absent: Boolean): Expected = {
    // Only the accepted prefix is reader-backed; every new expected fact is authored here.
    val declarations = Vector("actor" -> "actor:requester", "actor" -> "actor:auditor",
      "use-case" -> "use-case:complete-order", "use-case" -> "use-case:observe-order",
      "component" -> "component:partner", "service" -> "service:fulfillment", "external-system" -> "external-system:payment")
    val records = declarations.zipWithIndex.map { case ((kind, id), index) =>
      ElementRecord(if (absent && (index == 0 || index == 2)) _anonymous else Present(_id(id)),
        kind, "共有", _source(index + 43), Absent(NotDeclared, "No semantic references are declared."))
    }
    def _presence_[A](projection: Int, field: Int, declared: Presence[A]): Presence[A] = {
      if (absent) _absences((projection + field) % 4) else declared
    }
    val domain = _refs("structure.example", "entity:order", "aggregate:order")
    val actors = Vector(
      ActorProjection(records(0), _presence_(0, 0, Present("human")), _presence_(0, 1, Present("Request an order.")),
        _presence_(0, 2, Present("Submit the declared order use case.")), _presence_(0, 3, _refs("usecase.example", "use-case:complete-order")),
        _presence_(0, 4, _refs("dynamic.example", "workflow:fulfillment")), _presence_(0, 5, _refs("dynamic.example", "operation:complete")),
        _presence_(0, 6, _refs("dynamic.example", "event:completed")), _presence_(0, 7, domain)),
      ActorProjection(records(1), _presence_(1, 0, Present("system")), _presence_(1, 1, Present("Observe an order.")),
        _presence_(1, 2, Present("Observe the declared order use case.")), _presence_(1, 3, _refs("usecase.example", "use-case:observe-order")),
        _presence_(1, 4, _refs("dynamic.example")), _presence_(1, 5, _refs("dynamic.example")),
        _presence_(1, 6, _refs("dynamic.example")), _presence_(1, 7, _refs("structure.example"))))
    val flows = Vector(
      UseCaseFlow(MainFlow, Present("main"), Present("Complete the order."), Present("Submit and complete the declared order."),
        Absent(NotApplicable, "The main flow has no declared condition."), Present(Vector(
          _expression("Requester submits the order.", _ref("usecase.example", "actor:requester")),
          _expression("Complete the order.", _ref("dynamic.example", "operation:complete")))), Present(Vector.empty), Present(Vector.empty)),
      UseCaseFlow(AlternateFlow, Present("alternate"), Present("Reject the order."), Present("Reject and review the declared order."),
        Present(_expression("rejected", _ref("dynamic.example", "rule:complete"))),
        Present(Vector(_expression("Reject the order.", _ref("dynamic.example", "activity:reject")))),
        Present(Vector(_expression("Review the order again.", _ref("dynamic.example", "activity:review")))), Present(Vector.empty)),
      UseCaseFlow(ExceptionFlow, Present("exception"), Present("Handle payment failure."),
        Present("Notify failure and keep the declared order pending."), Present(_expression("payment unavailable", _external("event:unavailable"))),
        Present(Vector(_expression("Notify payment failure.", _ref("dynamic.example", "event:completed")))), Present(Vector.empty),
        Present(Vector(_expression("Keep the order pending.", _ref("dynamic.example", "state:pending"))))))
    val roles = Vector(ActorParticipation(Primary, _ref("usecase.example", "actor:requester")),
      ActorParticipation(Secondary, _ref("usecase.example", "actor:auditor")), ActorParticipation(Supporting, _external("actor:payment")),
      ActorParticipation(Stakeholder, _ref("usecase.example", "actor:requester")), ActorParticipation(Actor, _ref("usecase.example", "actor:auditor")))
    val complete = UseCaseProjection(records(2), _presence_(2, 0, Present("UC-COMPLETE")),
      _presence_(2, 1, Present("Complete order.")), _presence_(2, 2, Present("Complete the declared order use case.")),
      _presence_(2, 3, Present("Complete the declared order flow.")),
      _presence_(2, 4, Present(_expression("Order submitted.", _ref("dynamic.example", "event:completed")))),
      _presence_(2, 5, Present(Vector(_expression("Order is pending.", _ref("dynamic.example", "state:pending"))))),
      _presence_(2, 6, Present(Vector(_expression("Order is complete.", _ref("dynamic.example", "state:done"))))),
      _presence_(2, 7, Present("high")), _presence_(2, 8, Present("approved")), _presence_(2, 9, Present(roles)),
      _presence_(2, 10, Present(flows)), _presence_(2, 11, domain),
      _presence_(2, 12, _refs("dynamic.example", "operation:complete")), _presence_(2, 13, _refs("dynamic.example", "event:completed")),
      _presence_(2, 14, Present(Vector(_ref("usecase.example", "component:partner"), _ref("usecase.example", "service:fulfillment"),
        _ref("usecase.example", "external-system:payment"), _external("component:partner")))),
      _presence_(2, 15, Present(Vector(_ref("dynamic.example", "workflow:fulfillment"), _external("workflow:fulfillment")))))
    val observe = UseCaseProjection(records(3), _presence_(3, 0, Present("")), _presence_(3, 1, Present("")),
      _presence_(3, 2, Present("")), _presence_(3, 3, Present("")), _presence_(3, 4, Present(_expression(""))),
      _presence_(3, 5, Present(Vector.empty)), _presence_(3, 6, Present(Vector.empty)), _presence_(3, 7, Present("")),
      _presence_(3, 8, Present("")), _presence_(3, 9, Present(Vector.empty)), _presence_(3, 10, Present(Vector.empty)),
      _presence_(3, 11, Present(Vector.empty)), _presence_(3, 12, Present(Vector.empty)), _presence_(3, 13, Present(Vector.empty)),
      _presence_(3, 14, Present(Vector.empty)), _presence_(3, 15, Present(Vector.empty)))
    Expected(records, Vector(TermRecord(TermId("usecase.example", "use-case:complete-order"), _source(45))),
      actors, Vector(complete, observe))
  }

  private def _complete(prefix: Prefix, literal: Expected): Expected = {
    literal.copy(core = prefix.dynamic.catalog.elements ++ literal.core, terms = prefix.dynamic.catalog.terms ++ literal.terms)
  }
  private def _build(prefix: Prefix, expected: Expected): Graph = {
    val catalog = _right(CmlSemanticFoundation.build(expected.core, expected.terms))
    _right(CmlUseCaseMetadata.build(catalog, expected.actors, expected.usecases, _peer_extensions(prefix)))
  }
  private def _exercise(raw: JsObject, prefix: Prefix, expected: Expected): Vector[Boolean] = {
    val wire = _expected_wire(prefix, expected)
    val built = _build(prefix, expected)
    val read = _right(CmlUseCaseMetadata.read(raw))
    val canonical = Json.stringify(_keys(wire, false))
    val consumed = _right(CmlUseCaseMetadata.read(Json.parse(CmlUseCaseMetadata.canonicalJson(read))))
    Vector(raw == wire) ++ Vector(built, read, consumed).flatMap { graph =>
      Vector(graph.catalog.elements == expected.core, graph.catalog.terms == expected.terms,
        graph.actors == expected.actors, graph.useCases == expected.usecases,
        CmlUseCaseMetadata.toJson(graph) == wire, CmlUseCaseMetadata.canonicalJson(graph) == canonical,
        graph.envelope.extensions == _extensions(wire)) ++ _queries(_right(CmlUseCaseNavigation.build(graph)), expected)
    }
  }
  private def _queries(navigation: CmlUseCaseNavigation.Navigation, expected: Expected): Vector[Boolean] = {
    val actors = expected.actors.flatMap { value => value.element.identity match {
      case Present(id) => Vector(navigation.actor(id) == Right(value))
      case _: Absent => Vector.empty
    }}
    val usecases = expected.usecases.flatMap { value => value.element.identity match {
      case Present(id) => Vector(navigation.useCase(id) == Right(value))
      case _: Absent => Vector.empty
    }}
    val core = expected.core.flatMap { value => value.identity match {
      case Present(id) => Vector(navigation.element(id) == Right(value))
      case _: Absent => Vector.empty
    }}
    val anonymous = if (expected.core.exists(record => record.kind == "actor" && record.identity == _anonymous)) {
      Vector(navigation.actor(_id("actor:requester")) == Left(_unknown_projection("actor.identity")),
        navigation.useCase(_id("use-case:complete-order")) == Left(_unknown_projection("useCase.identity")),
        navigation.element(_id("actor:requester")) == Left(_unknown_element),
        navigation.element(_id("use-case:complete-order")) == Left(_unknown_element))
    } else Vector.empty
    val references = _all_references(expected).map { reference =>
      val target = if (reference.boundary == External) ExternalTarget(reference)
        else LocalTarget(_record(expected.core, reference.identity))
      navigation.resolve(reference) == Right(target)
    }
    actors ++ usecases ++ core ++ anonymous ++ references ++ Vector(
      navigation.catalog.term(TermId("usecase.example", "use-case:complete-order")) == Right(expected.terms.last),
      navigation.resolve(_external("workflow:fulfillment")) == Right(ExternalTarget(_external("workflow:fulfillment"))),
      navigation.resolve(ModelReference(_dynamic_id("workflow:fulfillment"), External)) ==
        Right(ExternalTarget(ModelReference(_dynamic_id("workflow:fulfillment"), External))),
      navigation.resolve(ModelReference(_dynamic_id("workflow:fulfillment"), Local)) ==
        Right(LocalTarget(_record(expected.core, _dynamic_id("workflow:fulfillment")))))
  }
  private def _all_references(expected: Expected): Vector[ModelReference] = {
    def _expression_refs_(value: DeclaredExpression): Vector[ModelReference] = { _values(value.references) }
    val actorrefs = expected.actors.flatMap { actor =>
      Vector(actor.useCases, actor.workflows, actor.operations, actor.events, actor.domainElements).flatMap(_values)
    }
    val usecaserefs = expected.usecases.flatMap { usecase =>
      Vector(usecase.domainElements, usecase.operations, usecase.events, usecase.collaborators, usecase.realizingWorkflows).flatMap(_values) ++
        _values(usecase.actorReferences).map(_.target) ++ _optional(usecase.trigger).flatMap(_expression_refs_) ++
        (_values(usecase.preconditions) ++ _values(usecase.postconditions)).flatMap(_expression_refs_) ++
        _values(usecase.flows).flatMap { flow =>
          (_optional(flow.condition) ++ _values(flow.steps) ++ _values(flow.alternates) ++ _values(flow.exceptions)).flatMap(_expression_refs_)
        }
    }
    actorrefs ++ usecaserefs
  }
  private def _peers(raw: JsObject, prefix: Prefix, expected: Expected): Vector[Boolean] = {
    val dynamic = _right(CmlDynamicMetadata.read(raw))
    val structure = _right(CmlStructureMetadata.read(raw))
    val classification = _right(CmlClassificationMetadata.read(raw))
    Vector(dynamic.catalog.elements == expected.core, dynamic.catalog.terms == expected.terms,
      dynamic.workflows == prefix.dynamic.workflows, dynamic.activities == prefix.dynamic.activities,
      dynamic.flows == prefix.dynamic.flows, dynamic.stateMachines == prefix.dynamic.stateMachines,
      dynamic.states == prefix.dynamic.states, dynamic.transitions == prefix.dynamic.transitions,
      dynamic.causalLinks == prefix.dynamic.causalLinks,
      structure.catalog.elements == expected.core, structure.elements == prefix.structure.elements, structure.relations == prefix.structure.relations,
      classification.catalog.elements == expected.core, classification.traits == prefix.classification.traits,
      classification.powertypes == prefix.classification.powertypes, classification.generalizations == prefix.classification.generalizations,
      classification.traitRealizations == prefix.classification.traitRealizations,
      classification.powertypeClassifications == prefix.classification.powertypeClassifications)
  }
  private def _consumers(raw: JsObject, prefix: Prefix, expected: Expected): Vector[Boolean] = {
    val graph = _right(CmlUseCaseMetadata.read(raw))
    val navigation = _right(CmlUseCaseNavigation.build(graph))
    val dynamic = _right(CmlDynamicMetadata.read(raw))
    val structure = _right(CmlStructureMetadata.read(raw))
    val classification = _right(CmlClassificationMetadata.read(raw))
    val topology = _right(CmlDynamicTopology.build(dynamic))
    val nodekinds = Set("entity", "value", "aggregate", "trait", "powertype", "powertype-kind", "workflow",
      "workflow-activity", "state-machine", "state", "operation", "command", "event", "rule", "action")
    val nodes = expected.core.filter(record => nodekinds(record.kind))
    val carriers = prefix.dynamic.flows.map(value => CmlDynamicTopology.TopologyLink(value.element,
      CmlDynamicTopology.Flow, value.source, value.target)) ++ prefix.dynamic.transitions.map(value =>
      CmlDynamicTopology.TopologyLink(value.element, CmlDynamicTopology.Transition, value.fromState, value.toState)) ++
      prefix.dynamic.causalLinks.map { value => CmlDynamicTopology.TopologyLink(value.element,
        if (value.kind == CmlDynamicMetadata.Cause) CmlDynamicTopology.Cause else CmlDynamicTopology.Reaction, value.source, value.target) }
    val links = expected.core.flatMap(record => carriers.filter(_.element == record))
    val nodequeries = nodes.flatMap { record => record.identity match {
      case Present(id) => Vector(topology.node(id) == Right(record),
        topology.incoming(id) == Right(links.filter(_.target == Present(ModelReference(id, Local)))),
        topology.outgoing(id) == Right(links.filter(_.source == Present(ModelReference(id, Local)))))
      case _: Absent => Vector.empty
    }}
    val projectionqueries = prefix.dynamic.workflows.map(value => topology.workflow(_record_id(value.element)) == Right(value)) ++
      prefix.dynamic.activities.map(value => topology.activity(_record_id(value.element)) == Right(value)) ++
      prefix.dynamic.flows.map(value => topology.flow(_record_id(value.element)) == Right(value)) ++
      prefix.dynamic.stateMachines.map(value => topology.stateMachine(_record_id(value.element)) == Right(value)) ++
      prefix.dynamic.states.map(value => topology.state(_record_id(value.element)) == Right(value)) ++
      prefix.dynamic.transitions.map(value => topology.transition(_record_id(value.element)) == Right(value)) ++
      prefix.dynamic.causalLinks.map(value => topology.causalLink(_record_id(value.element)) == Right(value))
    val actor = expected.actors.find(_.element.source.line == Some(43)).getOrElse(fail("The requester oracle is required."))
    val usecase = expected.usecases.find(_.element.source.line == Some(45)).getOrElse(fail("The complete-order oracle is required."))
    val followed = _values(actor.useCases).flatMap { reference =>
      val resolved = _right(navigation.resolve(reference))
      val queried = _right(navigation.useCase(reference.identity))
      Vector(resolved == LocalTarget(usecase.element), queried == usecase) ++ _values(queried.realizingWorkflows).flatMap { workflowref =>
        if (workflowref.boundary == External) Vector(navigation.resolve(workflowref) == Right(ExternalTarget(workflowref))) else {
          val expectedworkflow = prefix.dynamic.workflows.head
          val target = _right(navigation.resolve(workflowref))
          val workflow = _right(topology.workflow(workflowref.identity))
          val references = Vector(workflow.activities, workflow.flows, workflow.stateMachines, workflow.operations,
            workflow.events, workflow.rules, workflow.stateEffects).flatMap(_values)
          Vector(target == LocalTarget(expectedworkflow.element), workflow == expectedworkflow) ++ references.map { item =>
            navigation.resolve(item) == Right(LocalTarget(_record(expected.core, item.identity)))
          }
        }
      }
    }
    val staticrefs = _values(usecase.domainElements) ++ _values(prefix.dynamic.workflows.head.affectedElements)
    val staticqueries = staticrefs.map { reference =>
      val record = _record(expected.core, reference.identity)
      val projection = prefix.structure.elements.find(_.element == record).getOrElse(fail("The full static oracle projection is required."))
      navigation.element(reference.identity) == Right(record) && navigation.resolve(reference) == Right(LocalTarget(record)) &&
        structure.elements.find(_.element == record).contains(projection)
    }
    _queries(navigation, expected) ++ _peers(raw, prefix, expected) ++ nodequeries ++ projectionqueries ++ followed ++ staticqueries ++
      Vector(topology.nodes == nodes, nodes.size == 23, topology.links == links, links.size == 8,
        classification.traits == prefix.classification.traits, classification.powertypes == prefix.classification.powertypes,
        classification.generalizations == prefix.classification.generalizations,
        classification.traitRealizations == prefix.classification.traitRealizations,
        classification.powertypeClassifications == prefix.classification.powertypeClassifications)
  }

  private def _expected_wire(prefix: Prefix, expected: Expected): JsObject = {
    val prefixrecords = prefix.dynamic.catalog.elements.zip((prefix.raw \ "elements").as[Vector[JsValue]]).toMap
    val prefixterms = prefix.dynamic.catalog.terms.zip((prefix.raw \ "terms").as[Vector[JsValue]]).toMap
    def _record_(record: ElementRecord): JsValue = { prefixrecords.getOrElse(record, _record_json(record)) }
    val child = Json.obj("schemaVersion" -> "cozy.cml.use-case.v1",
      "actors" -> JsArray(expected.actors.map(_actor_json)), "useCases" -> JsArray(expected.usecases.map(_use_case_json)))
    Json.obj("schemaVersion" -> "cozy.cml.semantic-metadata.v1", "elements" -> JsArray(expected.core.map(_record_)),
      "terms" -> JsArray(expected.terms.map { term => prefixterms.getOrElse(term, Json.obj(
        "identity" -> Json.obj("vocabularyId" -> term.identity.vocabularyId, "termId" -> term.identity.termId),
        "source" -> _source_json(term.source))) }), "extensions" -> (_peer_extensions(prefix) + (namespace -> child)))
  }
  private def _actor_json(value: ActorProjection): JsObject = {
    Json.obj("element" -> _record_json(value.element), "actorKind" -> _presence_json(value.actorKind, JsString),
      "summary" -> _presence_json(value.summary, JsString), "description" -> _presence_json(value.description, JsString),
      "useCases" -> _presence_json(value.useCases, _references_json), "workflows" -> _presence_json(value.workflows, _references_json),
      "operations" -> _presence_json(value.operations, _references_json), "events" -> _presence_json(value.events, _references_json),
      "domainElements" -> _presence_json(value.domainElements, _references_json))
  }
  private def _use_case_json(value: UseCaseProjection): JsObject = {
    Json.obj("element" -> _record_json(value.element), "declaredId" -> _presence_json(value.declaredId, JsString),
      "summary" -> _presence_json(value.summary, JsString), "description" -> _presence_json(value.description, JsString),
      "goal" -> _presence_json(value.goal, JsString), "trigger" -> _presence_json(value.trigger, _expression_json),
      "preconditions" -> _presence_json(value.preconditions, _expressions_json), "postconditions" -> _presence_json(value.postconditions, _expressions_json),
      "priority" -> _presence_json(value.priority, JsString), "lifecycleStatus" -> _presence_json(value.lifecycleStatus, JsString),
      "actorReferences" -> _presence_json(value.actorReferences, (values: Vector[ActorParticipation]) => JsArray(values.map { item =>
        Json.obj("role" -> (item.role match {
          case Actor => "actor"
          case Primary => "primary"
          case Secondary => "secondary"
          case Supporting => "supporting"
          case Stakeholder => "stakeholder"
        }), "target" -> _reference_json(item.target))
      })), "flows" -> _presence_json(value.flows, (values: Vector[UseCaseFlow]) => JsArray(values.map(_flow_json))),
      "domainElements" -> _presence_json(value.domainElements, _references_json), "operations" -> _presence_json(value.operations, _references_json),
      "events" -> _presence_json(value.events, _references_json), "collaborators" -> _presence_json(value.collaborators, _references_json),
      "realizingWorkflows" -> _presence_json(value.realizingWorkflows, _references_json))
  }
  private def _flow_json(value: UseCaseFlow): JsObject = {
    Json.obj("kind" -> (value.kind match { case MainFlow => "main"; case AlternateFlow => "alternate"; case ExceptionFlow => "exception" }),
      "name" -> _presence_json(value.name, JsString), "summary" -> _presence_json(value.summary, JsString),
      "description" -> _presence_json(value.description, JsString), "condition" -> _presence_json(value.condition, _expression_json),
      "steps" -> _presence_json(value.steps, _expressions_json), "alternates" -> _presence_json(value.alternates, _expressions_json),
      "exceptions" -> _presence_json(value.exceptions, _expressions_json))
  }
  private def _record_json(value: ElementRecord): JsObject = {
    Json.obj("identity" -> _presence_json(value.identity, _identity_json), "kind" -> value.kind, "name" -> value.name,
      "source" -> _source_json(value.source), "references" -> Json.obj("status" -> "absent", "reason" -> "not-declared",
        "detail" -> "No semantic references are declared."))
  }
  private def _identity_json(value: ModelElementId): JsObject = { Json.obj("modelId" -> value.modelId, "elementId" -> value.elementId) }
  private def _source_json(value: SourceAttribution): JsObject = {
    Json.obj("authorityId" -> value.authorityId, "path" -> value.path, "line" -> value.line.get)
  }
  private def _presence_json[A](value: Presence[A], render: A => JsValue): JsObject = {
    value match {
      case Present(item) => Json.obj("status" -> "present", "value" -> render(item))
      case Absent(reason, detail) => Json.obj("status" -> "absent", "reason" -> (reason match {
        case NotDeclared => "not-declared"
        case NotRepresented => "not-represented"
        case Unsupported => "unsupported"
        case NotApplicable => "not-applicable"
      }), "detail" -> detail)
    }
  }
  private def _reference_json(value: ModelReference): JsObject = {
    Json.obj("identity" -> _identity_json(value.identity), "boundary" -> (if (value.boundary == Local) "local" else "external"))
  }
  private def _references_json(values: Vector[ModelReference]): JsArray = { JsArray(values.map(_reference_json)) }
  private def _expression_json(value: DeclaredExpression): JsObject = {
    Json.obj("text" -> value.text, "references" -> _presence_json(value.references, _references_json))
  }
  private def _expressions_json(values: Vector[DeclaredExpression]): JsArray = { JsArray(values.map(_expression_json)) }

  private val _shape_mutations: Vector[Mutation] = {
    val actor = Vector("actors", "0")
    val usecase = Vector("useCases", "0")
    val presence = actor :+ "workflows"
    val reference = presence ++ Vector("value", "0")
    val identity = reference :+ "identity"
    val expression = usecase ++ Vector("trigger", "value")
    val participation = usecase ++ Vector("actorReferences", "value", "0")
    val flow = usecase ++ Vector("flows", "value", "0")
    val steps = flow ++ Vector("steps", "value")
    val base = Vector(
      Mutation(Vector("schemaVersion"), _ => JsString("hostile/version"), ".schemaVersion", UnsupportedSchemaVersion,
        "The Use Case schema version is not supported."),
      Mutation(Vector.empty, _ => Json.obj("schemaVersion" -> "hostile/version", "hostile/body" -> JsNull), ".schemaVersion",
        UnsupportedSchemaVersion, "The Use Case schema version is not supported."),
      Mutation(Vector.empty, value => value.as[JsObject] - "actors", ".actors"),
      Mutation(Vector.empty, value => value.as[JsObject] + ("hostile/key" -> JsNull), ""),
      Mutation(Vector("actors"), _ => JsNull, ".actors"),
      Mutation(Vector("useCases"), _ => JsNull, ".useCases"),
      Mutation(actor, _ => JsNull, ".actors[0]"),
      Mutation(usecase, _ => JsNull, ".useCases[0]"),
      Mutation(actor, value => value.as[JsObject] - "summary", ".actors[0].summary"),
      Mutation(actor, value => value.as[JsObject] + ("hostile/key" -> JsNull), ".actors[0]"),
      Mutation(actor :+ "summary", _ => JsNull, ".actors[0].summary"),
      Mutation(usecase, value => value.as[JsObject] - "goal", ".useCases[0].goal"),
      Mutation(usecase, value => value.as[JsObject] + ("hostile/key" -> JsNull), ".useCases[0]"),
      Mutation(usecase :+ "goal", _ => JsNull, ".useCases[0].goal"),
      Mutation(participation, _ => JsNull, ".useCases[0].actorReferences.value[0]"),
      Mutation(participation, value => value.as[JsObject] + ("hostile/key" -> JsNull), ".useCases[0].actorReferences.value[0]"),
      Mutation(participation, value => value.as[JsObject] - "role", ".useCases[0].actorReferences.value[0].role"),
      Mutation(participation, value => value.as[JsObject] - "target", ".useCases[0].actorReferences.value[0].target"),
      Mutation(participation :+ "role", _ => JsString("PRIMARY"), ".useCases[0].actorReferences.value[0].role"),
      Mutation(flow, _ => JsNull, ".useCases[0].flows.value[0]"),
      Mutation(flow, value => value.as[JsObject] + ("hostile/key" -> JsNull), ".useCases[0].flows.value[0]"),
      Mutation(flow, value => value.as[JsObject] - "description", ".useCases[0].flows.value[0].description"),
      Mutation(flow :+ "kind", _ => JsString("MAIN"), ".useCases[0].flows.value[0].kind"),
      Mutation(presence, value => value.as[JsObject] - "value", ".actors[0].workflows.value"),
      Mutation(presence, value => value.as[JsObject] + ("hostile/key" -> JsNull), ".actors[0].workflows"),
      Mutation(presence, _ => JsNull, ".actors[0].workflows"),
      Mutation(presence :+ "status", _ => JsString("Present"), ".actors[0].workflows.status"),
      Mutation(presence, _ => Json.obj("status" -> "absent", "reason" -> "unknown", "detail" -> "safe"), ".actors[0].workflows.reason"),
      Mutation(presence, _ => Json.obj("status" -> "absent", "reason" -> "not-declared"), ".actors[0].workflows.detail"),
      Mutation(presence, _ => Json.obj("status" -> "absent", "reason" -> "not-declared", "detail" -> JsNull), ".actors[0].workflows.detail"),
      Mutation(presence, _ => Json.obj("status" -> "absent", "reason" -> "not-declared", "detail" -> "safe", "value" -> JsNull), ".actors[0].workflows"),
      Mutation(reference, _ => JsNull, ".actors[0].workflows.value[0]"),
      Mutation(reference, value => value.as[JsObject] + ("hostile/key" -> JsNull), ".actors[0].workflows.value[0]"),
      Mutation(reference, value => value.as[JsObject] - "boundary", ".actors[0].workflows.value[0].boundary"),
      Mutation(reference :+ "boundary", _ => JsString("LOCAL"), ".actors[0].workflows.value[0].boundary"),
      Mutation(identity, _ => JsNull, ".actors[0].workflows.value[0].identity"),
      Mutation(identity, value => value.as[JsObject] + ("hostile/key" -> JsNull), ".actors[0].workflows.value[0].identity"),
      Mutation(identity, value => value.as[JsObject] - "elementId", ".actors[0].workflows.value[0].identity.elementId"),
      Mutation(identity :+ "modelId", _ => JsNumber(7), ".actors[0].workflows.value[0].identity.modelId"),
      Mutation(expression, _ => JsNull, ".useCases[0].trigger.value"),
      Mutation(expression, value => value.as[JsObject] + ("hostile/key" -> JsNull), ".useCases[0].trigger.value"),
      Mutation(expression, value => value.as[JsObject] - "references", ".useCases[0].trigger.value.references"),
      Mutation(expression :+ "text", _ => JsBoolean(true), ".useCases[0].trigger.value.text"),
      Mutation(expression ++ Vector("references", "value"), _ => JsNull, ".useCases[0].trigger.value.references.value"),
      Mutation(expression ++ Vector("references", "value", "0"), _ => JsNull, ".useCases[0].trigger.value.references.value[0]"),
      Mutation(steps, _ => JsString("steps"), ".useCases[0].flows.value[0].steps.value"),
      Mutation(steps :+ "0", _ => JsNull, ".useCases[0].flows.value[0].steps.value[0]"),
      Mutation(steps ++ Vector("0", "text"), _ => JsBoolean(true), ".useCases[0].flows.value[0].steps.value[0].text"),
      Mutation(actor :+ "element", _ => JsNull, ".actors[0].element", InvalidCoreBinding, _embedded_detail),
      Mutation(usecase :+ "element", _ => JsNull, ".useCases[0].element", InvalidCoreBinding, _embedded_detail))
    base
  }

  private val _reference_mutations: Vector[ReferenceMutation] = {
    val actor = Vector("actors", "0")
    val usecase = Vector("useCases", "0")
    val family = Vector(
      (actor :+ "useCases", "actors[0].useCases", _dynamic_id("workflow:fulfillment")),
      (actor :+ "workflows", "actors[0].workflows", _id("use-case:complete-order")),
      (actor :+ "operations", "actors[0].operations", _dynamic_id("event:completed")),
      (actor :+ "events", "actors[0].events", _dynamic_id("operation:complete")),
      (actor :+ "domainElements", "actors[0].domainElements", _dynamic_id("workflow:fulfillment")),
      (usecase :+ "domainElements", "useCases[0].domainElements", _dynamic_id("workflow:fulfillment")),
      (usecase :+ "operations", "useCases[0].operations", _dynamic_id("event:completed")),
      (usecase :+ "events", "useCases[0].events", _dynamic_id("operation:complete")),
      (usecase :+ "collaborators", "useCases[0].collaborators", _dynamic_id("workflow:fulfillment")),
      (usecase :+ "realizingWorkflows", "useCases[0].realizingWorkflows", _dynamic_id("operation:complete")))
    val familycases = family.map { case (path, coordinate, wrong) => ReferenceMutation(path ++ Vector("value", "0"), coordinate + ".value[0]", wrong) }
    val participation = ReferenceMutation(usecase ++ Vector("actorReferences", "value", "0", "target"),
      "useCases[0].actorReferences.value[0].target", _dynamic_id("workflow:fulfillment"))
    val expressions = Vector(
      (usecase ++ Vector("trigger", "value"), "useCases[0].trigger.value"),
      (usecase ++ Vector("preconditions", "value", "0"), "useCases[0].preconditions.value[0]"),
      (usecase ++ Vector("postconditions", "value", "0"), "useCases[0].postconditions.value[0]")) ++
      Vector((0, "steps"), (1, "condition"), (1, "steps"), (1, "alternates"), (2, "condition"), (2, "steps"), (2, "exceptions")).map {
        case (index, field) =>
          val path = usecase ++ Vector("flows", "value", index.toString, field, "value")
          val coordinate = "useCases[0].flows.value[" + index + "]." + field + ".value"
          if (field == "condition") path -> coordinate else (path :+ "0") -> (coordinate + "[0]")
      }
    val secondstep = ReferenceMutation(usecase ++ Vector("flows", "value", "0", "steps", "value", "1", "references", "value", "0"),
      "useCases[0].flows.value[0].steps.value[1].references.value[0]", _id("missing:expression"))
    familycases ++ Vector(participation, secondstep) ++ expressions.map { case (path, coordinate) =>
      ReferenceMutation(path ++ Vector("references", "value", "0"), coordinate + ".references.value[0]", _id("missing:expression"))
    }
  }
  private def _replace_reference(expected: Expected, path: Vector[String], reference: ModelReference): Expected = {
    def _refs_(value: Presence[Vector[ModelReference]]): Presence[Vector[ModelReference]] = {
      Present(_present(value).updated(0, reference))
    }
    def _expression_(value: DeclaredExpression): DeclaredExpression = { value.copy(references = _refs_(value.references)) }
    if (path.head == "actors") {
      val actor = expected.actors.head
      val changed = path(2) match {
        case "useCases" => actor.copy(useCases = _refs_(actor.useCases))
        case "workflows" => actor.copy(workflows = _refs_(actor.workflows))
        case "operations" => actor.copy(operations = _refs_(actor.operations))
        case "events" => actor.copy(events = _refs_(actor.events))
        case "domainElements" => actor.copy(domainElements = _refs_(actor.domainElements))
        case _ => fail("The authored Actor reference site is required.")
      }
      expected.copy(actors = expected.actors.updated(0, changed))
    } else {
      val usecase = expected.usecases.head
      val changed = path(2) match {
        case "domainElements" => usecase.copy(domainElements = _refs_(usecase.domainElements))
        case "operations" => usecase.copy(operations = _refs_(usecase.operations))
        case "events" => usecase.copy(events = _refs_(usecase.events))
        case "collaborators" => usecase.copy(collaborators = _refs_(usecase.collaborators))
        case "realizingWorkflows" => usecase.copy(realizingWorkflows = _refs_(usecase.realizingWorkflows))
        case "actorReferences" =>
          val values = _present(usecase.actorReferences)
          usecase.copy(actorReferences = Present(values.updated(0, values.head.copy(target = reference))))
        case "trigger" => usecase.copy(trigger = Present(_expression_(_present(usecase.trigger))))
        case "preconditions" =>
          val values = _present(usecase.preconditions)
          usecase.copy(preconditions = Present(values.updated(0, _expression_(values.head))))
        case "postconditions" =>
          val values = _present(usecase.postconditions)
          usecase.copy(postconditions = Present(values.updated(0, _expression_(values.head))))
        case "flows" =>
          val values = _present(usecase.flows)
          val index = path(4).toInt
          val flow = values(index)
          def _expressions_(value: Presence[Vector[DeclaredExpression]]): Presence[Vector[DeclaredExpression]] = {
            val items = _present(value)
            val itemindex = path(7).toInt
            Present(items.updated(itemindex, _expression_(items(itemindex))))
          }
          val changedflow = path(5) match {
            case "condition" => flow.copy(condition = Present(_expression_(_present(flow.condition))))
            case "steps" => flow.copy(steps = _expressions_(flow.steps))
            case "alternates" => flow.copy(alternates = _expressions_(flow.alternates))
            case "exceptions" => flow.copy(exceptions = _expressions_(flow.exceptions))
            case _ => fail("The authored flow reference site is required.")
          }
          usecase.copy(flows = Present(values.updated(index, changedflow)))
        case _ => fail("The authored Use Case reference site is required.")
      }
      expected.copy(usecases = expected.usecases.updated(0, changed))
    }
  }

  private def _values[A](value: Presence[Vector[A]]): Vector[A] = {
    value match { case Present(items) => items; case _: Absent => Vector.empty }
  }
  private def _optional[A](value: Presence[A]): Vector[A] = {
    value match { case Present(item) => Vector(item); case _: Absent => Vector.empty }
  }
  private def _present[A](value: Presence[A]): A = {
    value match { case Present(item) => item; case _: Absent => fail("The authored mutation requires a present payload.") }
  }
  private def _record(records: Vector[ElementRecord], id: ModelElementId): ElementRecord = {
    records.find(_.identity == Present(id)).getOrElse(fail("The independently authored Local target is required."))
  }
  private def _record_id(record: ElementRecord): ModelElementId = { _present(record.identity) }
  private def _diagnostic(kind: UseCaseDiagnosticKind, path: String, detail: String): UseCaseDiagnostic = {
    UseCaseDiagnostic(kind, path, detail, None)
  }
  private def _unknown_projection(path: String): UseCaseDiagnostic = {
    _diagnostic(UnknownProjection, path, "No admitted Use Case projection has the requested qualified identity.")
  }
  private def _unknown_element: UseCaseDiagnostic = {
    _diagnostic(UnknownElement, "element.identity", "No admitted model element has the requested qualified identity.")
  }
  private def _priorities(size: Int): Gen[Vector[Int]] = { Gen.listOfN(size, Gen.choose(0, 1023)).map(_.toVector) }
  private def _order[A](values: Vector[A], priorities: Vector[Int]): Vector[Int] = {
    val selected = priorities.zipWithIndex.sortBy { case (priority, index) => (priority, index) }.map(_._2)
    if (selected.map(values) == values) selected.tail :+ selected.head else selected
  }
  private def _change(root: JsObject, path: Vector[String], update: JsValue => JsValue): JsObject = {
    def _visit_(value: JsValue, remaining: Vector[String]): JsValue = {
      if (remaining.isEmpty) update(value) else value match {
        case objectvalue: JsObject => objectvalue + (remaining.head -> _visit_(objectvalue.value(remaining.head), remaining.tail))
        case JsArray(values) => JsArray(values.updated(remaining.head.toInt, _visit_(values(remaining.head.toInt), remaining.tail)))
        case _ => fail("The authored mutation path requires a container.")
      }
    }
    _visit_(root, path).as[JsObject]
  }
  private def _keys(value: JsValue, reverse: Boolean): JsValue = {
    value match {
      case objectvalue: JsObject =>
        val fields = objectvalue.fields.sortBy(_._1)
        JsObject((if (reverse) fields.reverse else fields).map { case (key, item) => key -> _keys(item, reverse) })
      case JsArray(values) => JsArray(values.map(_keys(_, reverse)))
      case _ => value
    }
  }
  private def _extensions(value: JsObject): JsObject = { (value \ "extensions").as[JsObject] }
  private def _peer_extensions(prefix: Prefix): JsObject = { _extensions(prefix.raw) + (_peer -> _peer_value) }
  private def _filename(absent: Boolean): String = {
    if (absent) "use-case-metadata-v1-absence.json" else "use-case-metadata-v1-declared.json"
  }
  private def _resource(name: String): JsObject = {
    val stream = getClass.getResourceAsStream("/cozy/modeler/" + name)
    require(stream != null, "The authored UTF-8 fixture resource is required.")
    val source = Source.fromInputStream(stream, "UTF-8")
    try { Json.parse(source.mkString).as[JsObject] } finally { source.close() }
  }
  private def _prefix(): Prefix = {
    val raw = _resource("dynamic-metadata-v1-declared.json")
    Prefix(raw, _right(CmlDynamicMetadata.read(raw)), _right(CmlStructureMetadata.read(raw)),
      _right(CmlClassificationMetadata.read(raw)))
  }
  private def _right[A, B](value: Either[A, B]): B = {
    value match { case Right(result) => result; case Left(_) => fail("The authored fixture prerequisite must be admitted before observation.") }
  }
}

private object CmlUseCaseMetadataFixtureSpec {
  private final case class Expected(core: Vector[ElementRecord], terms: Vector[TermRecord],
    actors: Vector[ActorProjection], usecases: Vector[UseCaseProjection])
  private final case class Prefix(raw: JsObject, dynamic: CmlDynamicMetadata.Graph,
    structure: CmlStructureMetadata.Graph, classification: CmlClassificationMetadata.Graph)
  private final case class Mutation(path: Vector[String], update: JsValue => JsValue, coordinate: String,
    kind: UseCaseDiagnosticKind = InvalidShape, detail: String = "The Use Case value does not match the required v1 shape.")
  private final case class ReferenceMutation(path: Vector[String], coordinate: String, wrong: ModelElementId)
}
