package cozy.modeler

import org.scalacheck.{Gen, Prop, Test}
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import play.api.libs.json.Json

import CmlSemanticFoundation._
import CmlDynamicMetadata.{DeclaredExpression, ModelReference}
import CmlUseCaseMetadata._
import CmlUseCaseNavigation._

/*
 * @since   Oct.  4, 2026
 * @version Oct. 13, 2026
 * @author  ASAMI, Tomoharu
 */
final class CmlUseCaseNavigationSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  "Use Case navigation" should {
    "complete qualified facts" which {
      "UN-01 retain the original graph catalog and every independently authored projection and core fact" in {
        Given("same-named declarations under distinct authorities with Unicode and delimiter-bearing IDs")
        val fixture = _declared()
        When("the foundation and supplier admit the declarations and navigation indexes the admitted graph")
        val graph = _graph(fixture)
        val navigation = _admitted(CmlUseCaseNavigation.build(graph))
        val actors = fixture.actors.map(value => navigation.actor(_id(value.element)))
        val usecases = fixture.usecases.map(value => navigation.useCase(_id(value.element)))
        val elements = fixture.records.map(value => navigation.element(_id(value)))
        Then("complete values and instance ownership survive without identity aliases")
        (navigation.graph eq graph) shouldBe true
        (navigation.catalog eq graph.catalog) shouldBe true
        navigation.graph.actors shouldBe fixture.actors
        navigation.graph.useCases shouldBe fixture.usecases
        navigation.catalog.elements shouldBe fixture.records
        navigation.catalog.terms shouldBe fixture.terms
        actors shouldBe fixture.actors.map(Right(_))
        usecases shouldBe fixture.usecases.map(Right(_))
        elements shouldBe fixture.records.map(Right(_))
      }

      "UN-02 follow every declared reference family and expression context with ordered literal target facts" in {
        Given("Actor links all participation roles and Use Case workflow domain operation event collaborator and expression declarations")
        val fixture = _declared()
        val expectedactorlinks = Vector(
          LocalTarget(fixture.records(2)), LocalTarget(fixture.records(2)),
          ExternalTarget(ModelReference(ModelElementId("a:b", "case:事"), External)))
        val expectedroles = Vector.fill(6)(LocalTarget(fixture.records.head))
        val expectedfamilies = Vector(4, 5, 6, 7, 8, 9).map(index => LocalTarget(fixture.records(index)))
        When("navigation returns the complete declarations and resolves their authored references")
        val navigation = _admitted(CmlUseCaseNavigation.build(_graph(fixture)))
        val actor = _one(navigation.actor(ModelElementId("a:b", "actor:事")))
        val usecase = _one(navigation.useCase(ModelElementId("a:b", "case:事")))
        val actorlinks = _values(actor.useCases).map(navigation.resolve)
        val roles = _values(usecase.actorReferences).map(value => navigation.resolve(value.target))
        val families = Vector(usecase.realizingWorkflows, usecase.domainElements, usecase.operations,
          usecase.events, usecase.collaborators).flatMap(_values).map(navigation.resolve)
        val actorfamilies = Vector(actor.workflows, actor.domainElements, actor.operations, actor.events)
          .flatMap(_values).map(navigation.resolve)
        val expressions = _expressions(usecase).flatMap(value => _values(value.references)).map(navigation.resolve)
        val generic = navigation.resolve(ModelReference(ModelElementId("a:b", "misc:事"), Local))
        Then("roles duplicates mixed boundaries and all complete core sources remain literal")
        actorlinks shouldBe expectedactorlinks.map(Right(_))
        roles shouldBe expectedroles.map(Right(_))
        families shouldBe expectedfamilies.map(Right(_))
        actorfamilies shouldBe Vector(4, 5, 6, 7).map(index => Right(LocalTarget(fixture.records(index))))
        expressions shouldBe Vector.fill(7)(Right(LocalTarget(fixture.records(10))))
        generic shouldBe Right(LocalTarget(fixture.records(10)))
        usecase.actorReferences shouldBe Present(Vector(Primary, Secondary, Supporting, Stakeholder, Actor, Primary)
          .map(role => ActorParticipation(role, ModelReference(ModelElementId("a:b", "actor:事"), Local))))
        actor shouldBe fixture.actors.head
        usecase shouldBe fixture.usecases.head
      }

      "UN-03 preserve locally equal and missing External pairs as exact opaque supplied values" in {
        Given("one Local pair and External references to that pair and to an unknown authority")
        val fixture = _declared()
        val equal = ModelReference(ModelElementId("a:b", "actor:事"), External)
        val missing = ModelReference(ModelElementId("remote", "unknown"), External)
        When("the admitted navigation resolves all three explicitly declared boundaries")
        val navigation = _admitted(CmlUseCaseNavigation.build(_graph(fixture)))
        val results = Vector(equal, missing, equal.copy(boundary = Local)).map(navigation.resolve)
        Then("External never infers Local existence or family semantics")
        results shouldBe Vector(Right(ExternalTarget(equal)), Right(ExternalTarget(missing)),
          Right(LocalTarget(fixture.records.head)))
      }
    }

    "identity and explicit availability" which {
      "UN-04 reject all well-formed missing descriptive guessed and wrong-family addresses with complete fixed diagnostics" in {
        Given("separate ModelElementId and same-valued TermId plus literal non-address descriptive facts")
        val fixture = _declared()
        val missing = Vector(
          ModelElementId("A:b", "actor:事"), ModelElementId("a:b", "Actor:事"),
          ModelElementId("a", "b:actor:事"), ModelElementId("a:b", "shared-id"),
          ModelElementId("a:b", "共有"), ModelElementId("a:b", "model/facts.cml"),
          ModelElementId("a:b", "model/facts.cml:7"), ModelElementId("a:b", "actors[0]"),
          ModelElementId("a:b", "term-only"), ModelElementId("é", "actor:事"),
          ModelElementId("e\u0301", "actor:事"))
        When("all query families interpret exact pairs and wrong-family core identities")
        val navigation = _admitted(CmlUseCaseNavigation.build(_graph(fixture)))
        val results = missing.map(id => (navigation.actor(id), navigation.useCase(id), navigation.element(id)))
        val wrongactor = navigation.actor(ModelElementId("a:b", "case:事"))
        val wrongcase = navigation.useCase(ModelElementId("a:b", "actor:事"))
        val term = navigation.catalog.term(TermId("a:b", "actor:事"))
        val model = navigation.actor(ModelElementId("a:b", "actor:事"))
        Then("no case authority delimiter declared ID name source Term or position fallback exists")
        results shouldBe missing.map(_ => (_unknown_actor, _unknown_case, _unknown_element))
        wrongactor shouldBe _unknown_actor
        wrongcase shouldBe _unknown_case
        term shouldBe Right(fixture.terms.head)
        model shouldBe Right(fixture.actors.head)
      }

      "UN-05 retain equal anonymous occurrences distinct payloads all absence reasons empty values and named survivors" in {
        Given("two equal anonymous Actors and Use Cases with different complete payloads and named survivors")
        val fixture = _anonymous()
        When("the supplier builds renders and strictly reads the complete anonymous fixture before navigation")
        val graph = _graph(fixture)
        val json = CmlUseCaseMetadata.toJson(graph)
        val consumed = _admitted(CmlUseCaseMetadata.read(json))
        val navigation = _admitted(CmlUseCaseNavigation.build(consumed))
        val guessed = Vector("anonymous", "actors[2]", "useCases[2]", "anonymous-actor", "anonymous-case")
          .map(value => navigation.element(ModelElementId("a:b", value)))
        val survivor = navigation.actor(ModelElementId("a:b", "actor:事"))
        Then("carrier and Catalog occurrence order preserve every authored gap and empty fact")
        _expect_facts(navigation, fixture)
        navigation.graph.actors.takeRight(2).map(_.element) shouldBe Vector.fill(2)(fixture.actors(2).element)
        navigation.graph.useCases.takeRight(2).map(_.element) shouldBe Vector.fill(2)(fixture.usecases(2).element)
        navigation.graph.actors.takeRight(2).map(_.summary) shouldBe Vector(Present("first anonymous"), Present("second anonymous"))
        navigation.graph.useCases.takeRight(2).map(_.summary) shouldBe Vector(Present("first case"), Present("second case"))
        guessed shouldBe Vector.fill(5)(_unknown_element)
        survivor shouldBe Right(fixture.actors.head)
        navigation.graph.actors(2).actorKind shouldBe Absent(NotDeclared, "Not declared.")
        navigation.graph.actors(2).description shouldBe Absent(NotRepresented, "Not represented.")
        navigation.graph.actors(2).workflows shouldBe Absent(Unsupported, "Unsupported.")
        navigation.graph.actors(2).events shouldBe Absent(NotApplicable, "Not applicable.")
        navigation.graph.actors(2).useCases shouldBe Present(Vector.empty)
        navigation.graph.useCases(2).declaredId shouldBe Present("")
      }
    }

    "typed failure boundaries" which {
      "UN-06 separate malformed queries from reference shapes dangling Local and safe successes without supplied-value echoes" in {
        Given("null empty Unicode edge padding and ISO controls in either qualified component")
        val fixture = _declared()
        val badstrings = Vector[String](null, "", " value", "value ", "\u2003value", "value\u2003",
          "\u00a0value", "value\u00a0", "x\u0000y", "x\u0085y")
        val badids = Vector[ModelElementId](null) ++ badstrings.flatMap(value =>
          Vector(ModelElementId(value, "safe"), ModelElementId("safe", value)))
        val shapereferences = Vector[ModelReference](null, ModelReference(null, Local),
          ModelReference(ModelElementId("a:b", "actor:事"), null))
        val malformedreferences = badids.drop(1).map(id => ModelReference(id, External))
        When("navigation builds a null graph and evaluates every hostile query and reference with safe contrasts")
        val nullgraph = CmlUseCaseNavigation.build(null)
        val navigation = _admitted(CmlUseCaseNavigation.build(_graph(fixture)))
        val queries = badids.map(id => (navigation.actor(id), navigation.useCase(id), navigation.element(id)))
        val shapes = shapereferences.map(navigation.resolve)
        val malformed = malformedreferences.map(navigation.resolve)
        val dangling = navigation.resolve(ModelReference(ModelElementId("remote", "unknown"), Local))
        val external = navigation.resolve(ModelReference(ModelElementId("remote", "unknown"), External))
        val safe = navigation.element(ModelElementId("a:b", "actor:事"))
        Then("every expected failure is a complete typed diagnostic containing only a fixed coordinate and detail")
        nullgraph shouldBe Left(Vector(UseCaseDiagnostic(InvalidShape, "graph", "An admitted Use Case graph is required.", None)))
        queries shouldBe badids.map(_ => (Left(_semantic("actor.identity")), Left(_semantic("useCase.identity")),
          Left(_semantic("element.identity"))))
        shapes shouldBe Vector(Left(_shape("resolve.reference")), Left(_shape("resolve.reference")),
          Left(_shape("resolve.reference.boundary")))
        malformed shouldBe malformedreferences.map(_ => Left(_semantic("resolve.reference.identity")))
        dangling shouldBe Left(_semantic("resolve.reference"))
        external shouldBe Right(ExternalTarget(ModelReference(ModelElementId("remote", "unknown"), External)))
        safe shouldBe Right(fixture.records.head)
      }
    }

    "independent ordering" which {
      "UN-07 preserve complete declared and anonymous facts through independently varied Catalog carrier and query orders in every sample" in {
        Given("two finite independent fixtures and independently generated bounded priorities for each collection")
        val declared = _declared()
        val anonymous = _anonymous()
        val declaredqueries = _queries(declared)
        val anonymousqueries = _queries(anonymous)
        val sizes = Vector(declared.records.size, declared.actors.size, declared.usecases.size, declaredqueries.size,
          anonymous.records.size, anonymous.actors.size, anonymous.usecases.size, anonymousqueries.size)
        val priorities = sizes.foldLeft(Gen.const(Vector.empty[Vector[Int]])) { (generator, size) =>
          for {
            previous <- generator
            next <- Gen.listOfN(size, Gen.choose(0, 31))
          } yield previous :+ next.toVector
        }
        val property = Prop.forAll(priorities) { orders =>
          Given("a generated independent priority vector for every declared and anonymous collection")
          val fixtures = Vector(declared, anonymous)
          val queries = Vector(declaredqueries, anonymousqueries)
          fixtures.zipWithIndex.foreach { case (fixture, index) =>
            val offset = index * 4
            val permuted: Fixture = fixture.copy(records = _permuted(fixture.records, orders(offset)),
              actors = _permuted(fixture.actors, orders(offset + 1)),
              usecases = _permuted(fixture.usecases, orders(offset + 2)))
            val requested = _permuted(queries(index), orders(offset + 3))
            When("the independently reordered Catalog and carriers are admitted rendered strictly read and queried in caller order")
            val graph = _graph(permuted)
            val json = CmlUseCaseMetadata.toJson(graph)
            val consumed = _admitted(CmlUseCaseMetadata.read(json))
            val direct = _admitted(CmlUseCaseNavigation.build(graph))
            val navigation = _admitted(CmlUseCaseNavigation.build(consumed))
            val actors = requested.map(query => navigation.actor(query.id))
            val usecases = requested.map(query => navigation.useCase(query.id))
            val elements = requested.map(query => navigation.element(query.id))
            val locals = requested.map(query => navigation.resolve(ModelReference(query.id, Local)))
            val externals = requested.map(query => navigation.resolve(ModelReference(query.id, External)))
            Then("actual full record orders vary and complete facts multiplicity and per-ID results equal authored expectations")
            permuted.records should not equal fixture.records
            permuted.actors should not equal fixture.actors
            permuted.usecases should not equal fixture.usecases
            requested should not equal queries(index)
            _expect_facts(direct, permuted)
            _expect_facts(navigation, permuted)
            actors shouldBe requested.map(_.actor)
            usecases shouldBe requested.map(_.usecase)
            elements shouldBe requested.map(_.element)
            locals shouldBe requested.map(_.local)
            externals shouldBe requested.map(query => Right(ExternalTarget(ModelReference(query.id, External))))
          }
          true
        }
        When("ScalaCheck runs one hundred successful cases without filtering implication or discards")
        val result = Test.check(Test.Parameters.default.withMinSuccessfulTests(100), property)
        Then("the actual result reports Passed at least one hundred successes and no discarded cases")
        info(result.toString)
        result.status shouldBe Test.Passed
        result.succeeded should be >= 100
        result.discarded shouldBe 0
      }
    }
  }

  private case class Fixture(records: Vector[ElementRecord], terms: Vector[TermRecord],
    actors: Vector[ActorProjection], usecases: Vector[UseCaseProjection])

  private case class Query(id: ModelElementId, actor: Either[UseCaseDiagnostic, ActorProjection],
    usecase: Either[UseCaseDiagnostic, UseCaseProjection], element: Either[UseCaseDiagnostic, ElementRecord],
    local: Either[UseCaseDiagnostic, ReferenceTarget])

  private val _unknown_actor = Left(UseCaseDiagnostic(UnknownProjection, "actor.identity",
    "No admitted Use Case projection has the requested qualified identity.", None))
  private val _unknown_case = Left(UseCaseDiagnostic(UnknownProjection, "useCase.identity",
    "No admitted Use Case projection has the requested qualified identity.", None))
  private val _unknown_element = Left(UseCaseDiagnostic(UnknownElement, "element.identity",
    "No admitted model element has the requested qualified identity.", None))

  private def _source(authority: String): SourceAttribution = {
    SourceAttribution(authority, "model/facts.cml", Some(7))
  }

  private def _record(kind: String, authority: String, component: String): ElementRecord = {
    ElementRecord(Present(ModelElementId(authority, component)), kind, "共有", _source(authority), Present(Vector.empty))
  }

  private def _reference(component: String): ModelReference = {
    ModelReference(ModelElementId("a:b", component), Local)
  }

  private def _actor(record: ElementRecord): ActorProjection = {
    ActorProjection(record, Present("business"), Present("summary\n共有"), Present("literal description"),
      Present(Vector(_reference("case:事"), _reference("case:事"), ModelReference(ModelElementId("a:b", "case:事"), External))),
      Present(Vector(_reference("workflow:事"))), Present(Vector(_reference("operation:事"))),
      Present(Vector(_reference("event:事"))), Present(Vector(_reference("entity:事"))))
  }

  private def _use_case(record: ElementRecord): UseCaseProjection = {
    val expression = DeclaredExpression("literal @directive\n共有", Present(Vector(_reference("misc:事"))))
    val flow = UseCaseFlow(MainFlow, Present("main"), Present(""), Absent(NotDeclared, "Not declared."),
      Present(expression), Present(Vector(expression)), Present(Vector(expression)), Present(Vector(expression)))
    UseCaseProjection(record, Present("shared-id"), Present("summary"), Present("description"), Present("goal"),
      Present(expression), Present(Vector(expression)), Present(Vector(expression)), Present("literal-priority"), Present("literal-status"),
      Present(Vector(Primary, Secondary, Supporting, Stakeholder, Actor, Primary)
        .map(role => ActorParticipation(role, _reference("actor:事")))), Present(Vector(flow)),
      Present(Vector(_reference("entity:事"))), Present(Vector(_reference("operation:事"))), Present(Vector(_reference("event:事"))),
      Present(Vector(_reference("component:事"), _reference("service:事"))), Present(Vector(_reference("workflow:事"))))
  }

  private def _declared(): Fixture = {
    val records = Vector(_record("actor", "a:b", "actor:事"), _record("actor", "a", "actor:事"),
      _record("use-case", "a:b", "case:事"), _record("use-case", "a", "case:事"),
      _record("workflow", "a:b", "workflow:事"), _record("entity", "a:b", "entity:事"),
      _record("operation", "a:b", "operation:事"), _record("event", "a:b", "event:事"),
      _record("component", "a:b", "component:事"), _record("service", "a:b", "service:事"),
      _record("unrestricted-kind", "a:b", "misc:事"))
    val terms = Vector(TermRecord(TermId("a:b", "actor:事"), _source("a:b")),
      TermRecord(TermId("a:b", "term-only"), _source("a:b")))
    Fixture(records, terms, Vector(_actor(records(0)), _actor(records(1))),
      Vector(_use_case(records(2)), _use_case(records(3))))
  }

  private def _anonymous(): Fixture = {
    val fixture = _declared()
    val actorrecord = ElementRecord(Absent(NotDeclared, "Anonymous actor."), "actor", "共有", _source("a:b"),
      Absent(NotRepresented, "Not represented."))
    val caserecord = ElementRecord(Absent(NotRepresented, "Anonymous case."), "use-case", "共有", _source("a:b"), Present(Vector.empty))
    val actor = ActorProjection(actorrecord, Absent(NotDeclared, "Not declared."), Present("first anonymous"),
      Absent(NotRepresented, "Not represented."), Present(Vector.empty), Absent(Unsupported, "Unsupported."),
      Present(Vector.empty), Absent(NotApplicable, "Not applicable."), Present(Vector.empty))
    val usecase = _use_case(caserecord).copy(declaredId = Present(""), summary = Present("first case"),
      description = Absent(NotApplicable, "Not applicable."), goal = Absent(Unsupported, "Unsupported."),
      trigger = Absent(NotRepresented, "Not represented."), flows = Present(Vector.empty))
    fixture.copy(records = fixture.records ++ Vector(actorrecord, actorrecord, caserecord, caserecord),
      actors = fixture.actors ++ Vector(actor, actor.copy(summary = Present("second anonymous"))),
      usecases = fixture.usecases ++ Vector(usecase, usecase.copy(summary = Present("second case"))))
  }

  private def _queries(fixture: Fixture): Vector[Query] = {
    val named = fixture.records.collect { case record @ ElementRecord(Present(id), _, _, _, _) =>
      val actor = fixture.actors.find(_.element == record).map(value => Right(value): Either[UseCaseDiagnostic, ActorProjection])
        .getOrElse(_unknown_actor)
      val usecase = fixture.usecases.find(_.element == record).map(value => Right(value): Either[UseCaseDiagnostic, UseCaseProjection])
        .getOrElse(_unknown_case)
      Query(id, actor, usecase, Right(record), Right(LocalTarget(record)))
    }
    named ++ Vector(ModelElementId("remote", "unknown"), ModelElementId("a:b", "actors[0]"), ModelElementId("a:b", "term-only"))
      .map(id => Query(id, _unknown_actor, _unknown_case, _unknown_element, Left(_semantic("resolve.reference"))))
  }

  private def _permuted[A](values: Vector[A], priorities: Vector[Int]): Vector[A] = {
    val ordered = values.indices.sortBy(index => (priorities(index), index)).map(values).toVector
    if (ordered == values && values.distinct.size > 1) {
      ordered.tail :+ ordered.head
    } else {
      ordered
    }
  }

  private def _graph(fixture: Fixture): Graph = {
    val catalog = _admitted(CmlSemanticFoundation.build(fixture.records, fixture.terms))
    _admitted(CmlUseCaseMetadata.build(catalog, fixture.actors, fixture.usecases, Json.obj()))
  }

  private def _admitted[E, A](result: Either[Vector[E], A]): A = {
    result match {
      case Right(value) => value
      case Left(diagnostics) => fail("Expected admitted fixture: " + diagnostics)
    }
  }

  private def _one[A](result: Either[UseCaseDiagnostic, A]): A = {
    result match {
      case Right(value) => value
      case Left(diagnostic) => fail("Expected declared value: " + diagnostic)
    }
  }

  private def _id(record: ElementRecord): ModelElementId = {
    record.identity match {
      case Present(value) => value
      case _ => fail("The independently authored query requires a named fixture record.")
    }
  }

  private def _values[A](presence: Presence[Vector[A]]): Vector[A] = {
    presence match {
      case Present(values) => values
      case _ => fail("The authored fixture declares this collection.")
    }
  }

  private def _expressions(usecase: UseCaseProjection): Vector[DeclaredExpression] = {
    val trigger = usecase.trigger match {
      case Present(value) => Vector(value)
      case _ => Vector.empty
    }
    trigger ++ _values(usecase.preconditions) ++ _values(usecase.postconditions) ++
      _values(usecase.flows).flatMap { flow =>
        val condition = flow.condition match {
          case Present(value) => Vector(value)
          case _ => Vector.empty
        }
        condition ++ _values(flow.steps) ++ _values(flow.alternates) ++ _values(flow.exceptions)
      }
  }

  private def _expect_facts(navigation: Navigation, fixture: Fixture): Unit = {
    navigation.catalog.elements shouldBe fixture.records
    navigation.catalog.terms shouldBe fixture.terms
    navigation.graph.actors shouldBe fixture.actors
    navigation.graph.useCases shouldBe fixture.usecases
  }

  private def _semantic(path: String): UseCaseDiagnostic = {
    UseCaseDiagnostic(InvalidUseCaseSemantics, path, "The declared Use Case reference or absence is invalid.", None)
  }

  private def _shape(path: String): UseCaseDiagnostic = {
    UseCaseDiagnostic(InvalidShape, path, "The Use Case value does not match the required v1 shape.", None)
  }
}
