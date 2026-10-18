package cozy.modeler

import scala.collection.immutable.ListMap
import scala.io.Source
import org.scalacheck.{Gen, Prop, Test}
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import play.api.libs.json.{JsArray, JsBoolean, JsNull, JsNumber, JsObject, JsString, JsValue, Json}
import CmlSemanticFoundation._
import CmlDynamicMetadata.{CausalLinkProjection, ModelReference}
import CmlCausalTraversal.{TraversalLink, Cause, Reaction, AffectedSubject, RuleInput, RuleOutput, ModelReferenceLink}

/*
 * @since   Oct.  5, 2026
 * @version Oct. 18, 2026
 */
final class CmlSemanticPublicationFixtureSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  import CmlSemanticPublicationFixtureSpec._

  "A JSON-only semantic publication consumer" should {
    "execute the complete fixed question matrix" which {
      "SF-01 answer all seventeen ordered declared and absence questions" in {
        Given("semantic-publication-v1-declared-questions.json and semantic-publication-v1-absence-questions.json with the fixed v1 matrix")
        val modes = Vector("declared", "absence")
        val orders = Vector(Vector("Q01", "Q02", "Q03", "Q04", "Q05", "Q06", "Q07", "Q08"),
          Vector("Q01", "Q02", "Q05", "Q06", "Q08", "A01", "A02", "A03", "A04"))
        When("actual UTF-8 question and publication resources are parsed, independent suppliers are admitted, and consumer APIs are built")
        val session = _baseline_session
        When("each literal question executes its finite complete semantic answer path in authored order")
        val answers = modes.flatMap(mode => session.questions(mode).map(question =>
          (mode, question, _answer(session, mode, question))))
        val publications = modes.map(mode => _publication_checks(session.contexts(mode)))
        Then("the exact schema, inputs, evidence and orders agree with the matrix and every complete answer agrees with its literal oracle")
        modes.zip(orders).foreach { case (mode, order) =>
          _fixture_checks(session.fixtures(mode), mode) should contain only true
          session.questions(mode).map(question => (question \ "id").as[String]) shouldBe order
        }
        answers.size shouldBe 17
        answers.foreach { case (mode, question, answer) => _expect_answer(mode, question, answer) }
        publications.foreach(checks => checks should contain only true)
      }
    }
    "follow qualified cross-view paths" which {
      "SF-02 Q01 preserve complete Term links, duplicate occurrences and External stopping in both modes" in {
        Given("both literal question filenames, Q01, terminology.a/shared ModelElementId and its distinct TermId")
        val questionid = "Q01"
        val modes = Vector("declared", "absence")
        When("both actual complete publications and question fixtures independently admit suppliers before navigation is built")
        val session = _baseline_session
        When("element, term, links, linksForTerm and resolve execute the complete Q01 requests")
        val answers = modes.map(mode => (mode, _question(session, mode, questionid),
          _answer(session, mode, _question(session, mode, questionid))))
        Then("full projections and ordered forward and reverse links match literal sources and independent typed objects")
        answers.foreach { case (mode, question, answer) => _expect_answer(mode, question, answer) }
      }
      "SF-03 Q02 retain full UseCase Actor Operation declarations and the External payment role" in {
        Given("both literal question filenames, Q02 and usecase.example/use-case:complete-order")
        val questionid = "Q02"
        val modes = Vector("declared", "absence")
        When("both actual complete roots independently admit suppliers and build Use Case navigation")
        val session = _baseline_session
        When("useCase, actor, resolve and element follow the authored roles through dynamic.example/operation:complete")
        val answers = modes.map(mode => (mode, _question(session, mode, questionid),
          _answer(session, mode, _question(session, mode, questionid))))
        Then("complete goals conditions flows realizations actors and the line 38 operation agree with the independent literal oracle")
        answers.foreach { case (mode, question, answer) => _expect_answer(mode, question, answer) }
      }
      "SF-04 Q03 follow the direct Cause and contextual affected Structure without aggregate causation" in {
        Given("semantic-publication-v1-declared-questions.json Q03 and causal.example/command:place-order")
        val mode = "declared"
        val questionid = "Q03"
        When("the actual publication and literal questions are parsed and independent suppliers and Causal traversal are built")
        val session = _baseline_session
        When("consequences, affecting, node and resolve retain the placement carrier and continue through full Structure projections")
        val question = _question(session, mode, questionid)
        val answer = _answer(session, mode, question)
        Then("full direct and contextual link evidence, affected order and member boundary agree while aggregate consequences stay Present empty")
        _expect_answer(mode, question, answer)
      }
      "SF-05 Q04 retain direct Reaction, rule context, confirmation Cause and subsequent stock Cause" in {
        Given("semantic-publication-v1-declared-questions.json Q04 and the qualified order-placed reserve-stock and stock-reserved identities")
        val mode = "declared"
        val questionid = "Q04"
        When("the actual complete publication independently admits supplier records before Causal traversal is built")
        val session = _baseline_session
        When("finite consequences, causes, outgoing, node and resolve requests execute the complete Reaction and rule-context path")
        val question = _question(session, mode, questionid)
        val answer = _answer(session, mode, question)
        Then("all nine link fields and complete carriers match independent evidence without policy execution or transitive causation")
        _expect_answer(mode, question, answer)
      }
      "SF-06 Q05 preserve full Workflow StateMachine states transition and static Structure in both modes" in {
        Given("both literal question filenames Q05 and dynamic.example/workflow:fulfillment")
        val modes = Vector("declared", "absence")
        val questionid = "Q05"
        When("actual UTF-8 roots independently admit Dynamic and Structure before topology construction")
        val session = _baseline_session
        When("workflow, stateMachine, state, transition and node continue through the Local owner and ordered affected Structure")
        val answers = modes.map(mode => (mode, _question(session, mode, questionid),
          _answer(session, mode, _question(session, mode, questionid))))
        Then("all projection fields, Presence values, source lines and member:主/root:宣言 boundaries agree with literal and typed expectations")
        answers.foreach { case (mode, question, answer) => _expect_answer(mode, question, answer) }
      }
      "SF-07 Q06 keep three independent Classification dimensions and complete target powertypes" in {
        Given("both literal question filenames Q06 and structure.example/entity:order")
        val modes = Vector("declared", "absence")
        val questionid = "Q06"
        When("actual roots and fixtures independently admit Classification before topology construction")
        val session = _baseline_session
        When("dimensions, dimension and node observe status, category and status-secondary in declaration order")
        val answers = modes.map(mode => (mode, _question(session, mode, questionid),
          _answer(session, mode, _question(session, mode, questionid))))
        Then("complete declarations subject records sources and target powertypes agree without merging the two status-like dimensions")
        answers.foreach { case (mode, question, answer) => _expect_answer(mode, question, answer) }
      }
      "SF-08 Q07 stop resident and nonresident External references and retain ordered generic context" in {
        Given("semantic-publication-v1-declared-questions.json Q07 and the qualified component event query and view")
        val mode = "declared"
        val questionid = "Q07"
        When("actual roots independently admit full core references and construct Causal traversal")
        val session = _baseline_session
        When("outgoing, node, resolve, modelReferences and causes execute the finite generic-reference requests")
        val question = _question(session, mode, questionid)
        val answer = _answer(session, mode, question)
        Then("full nine-field links retain reference provenance and duplicates while Query and View causes remain Present empty")
        _expect_answer(mode, question, answer)
      }
    }
    "retain explicit unsupported strength and complete publications" which {
      "SF-09 Q08 A01 A02 A03 A04 preserve full gaps unaddressability inherited answers and anonymous occurrences" in {
        Given("both literal question filenames Q08 plus absence A01 A02 A03 A04 with exact reasons details and occurrence indexes 73 and 74")
        val requested = Vector("declared" -> "Q08", "absence" -> "Q08", "absence" -> "A01",
          "absence" -> "A02", "absence" -> "A03", "absence" -> "A04")
        When("both actual roots independently admit complete suppliers and construct the unchanged consumer APIs")
        val session = _baseline_session
        When("each finite gap question executes node consequences and retained cross-view requests with full ordered anonymous payloads")
        val answers = requested.map { case (mode, id) =>
          val question = _question(session, mode, id)
          (mode, question, _answer(session, mode, question))
        }
        Then("entire gap owners fields and absences, UnknownElement diagnostic, retained answers and available Dynamic agree with literal evidence")
        answers.foreach { case (mode, question, answer) => _expect_answer(mode, question, answer) }
      }
      "SF-10 retain the full Envelope catalog Terms six typed graphs and five opaque peers in both JSON-only modes" in {
        Given("causal-metadata-v1-declared.json and causal-metadata-v1-absence.json with 75 core occurrences seven Terms and five opaque peers")
        val modes = Vector("declared", "absence")
        When("actual UTF-8 resources are parsed and all unchanged suppliers independently admit full expected objects")
        val session = _baseline_session
        When("the publication facade renders every full input and typed peer and envelope observations are captured")
        val checks = modes.map(mode => _publication_checks(session.contexts(mode)))
        val outputs = modes.map(mode => CmlSemanticPublication.toJson(session.contexts(mode).publication))
        Then("all full carrier and source fields agree independently and original arrays duplicates opaque JSON and nulls survive")
        checks.foreach(values => values should contain only true)
        outputs shouldBe modes.map(mode => session.contexts(mode).raw)
        modes.foreach { mode =>
          val context = session.contexts(mode)
          context.publication.catalog.elements.size shouldBe 75
          context.publication.catalog.terms.size shouldBe 7
          context.publication.envelope.extensions.keys.diff(CmlSemanticPublication.registeredNamespaces.toSet).size shouldBe 5
        }
      }
    }
    "consume invariant answers under object reconstruction and execution permutation" which {
      "SF-11 report actual seed 42 results and coverage for every mandatory mode and question occurrence" taggedAs org.scalatest.tagobjects.Slow in {
        Given("both literal question filenames seed 42 a no-discard generator and the seventeen mandatory occurrence tags")
        val generator = Gen.choose(1, Int.MaxValue)
        val occurrencekeys = Vector("declared/Q01", "declared/Q02", "declared/Q03", "declared/Q04",
          "declared/Q05", "declared/Q06", "declared/Q07", "declared/Q08", "absence/Q01", "absence/Q02",
          "absence/Q05", "absence/Q06", "absence/Q08", "absence/A01", "absence/A02", "absence/A03", "absence/A04")
        val tags = occurrencekeys ++ Vector("declared", "absence", "recursive-object-reconstruction", "question-execution-permutation")
        When("actual complete inputs and literal question oracles are read independently before any property consumer actions")
        val baseline = _baseline_session
        val counts = scala.collection.mutable.Map(tags.map(_ -> 0): _*)
        val executionorders = scala.collection.mutable.Set.empty[Vector[String]]
        When("ScalaCheck recursively reconstructs every object and separately permutes finite question execution for both inputs in each sample")
        val property = Prop.forAll(generator) { salt =>
          val contexts = baseline.contexts.map { case (mode, context) =>
            mode -> _context(_reconstruct(context.raw, salt).as[JsObject])
          }
          val fixtures = baseline.fixtures.map { case (mode, fixture) =>
            mode -> _reconstruct(fixture, salt + 1).as[JsObject]
          }
          val session = Session(contexts, fixtures)
          val execution = _execution_order(occurrencekeys, salt)
          executionorders += execution
          val answers = execution.map { key =>
            val parts = key.split("/")
            val mode = parts(0)
            val question = _question(session, mode, parts(1))
            val answer = _answer(session, mode, question)
            counts.update(key, counts(key) + 1)
            _answer_matches(question, answer)
          }
          tags.filterNot(occurrencekeys.contains).foreach(tag => counts.update(tag, counts(tag) + 1))
          answers.forall(identity) && contexts.forall { case (mode, context) =>
            _publication_checks(context).forall(identity) &&
              CmlSemanticPublication.toJson(context.publication) == baseline.contexts(mode).raw &&
              _fixture_checks(fixtures(mode), mode).forall(identity) && fixtures(mode) == baseline.fixtures(mode)
          }
        }
        val parameters = Test.Parameters.default.withMinSuccessfulTests(100)
          .withInitialSeed(org.scalacheck.rng.Seed(42L))
        val result = Test.check(parameters, property)
        val coverage = counts.toVector.sortBy(_._1)
        Then("observed passed successes zero discards coverage and execution variation prove all complete literal answers remained invariant")
        info("SF-11 Test.Result=" + result.toString)
        info(s"SF-11 succeeded=${result.succeeded} discarded=${result.discarded} passed=${result.passed}")
        info("SF-11 coverage=" + coverage.map { case (tag, count) => s"$tag:$count" }.mkString(","))
        info("SF-11 distinct execution orders=" + executionorders.size)
        result.passed shouldBe true
        result.succeeded should be >= 100
        result.discarded shouldBe 0
        coverage.map(_._1) shouldBe tags.sorted
        coverage.map(_._2).foreach(count => count shouldBe result.succeeded)
        executionorders.size should be > 1
      }
    }
  }

  // Only the immutable classpath baseline is shared. The property still builds
  // fresh contexts from each reconstructed input before executing its questions.
  private lazy val _baseline_session: Session = {
    val modes = Vector("declared", "absence")
    val fixtures = modes.map(mode => mode -> _resource("semantic-publication-v1-" + mode + "-questions.json")).toMap
    val contexts = modes.map(mode => mode -> _context(_resource("causal-metadata-v1-" + mode + ".json"))).toMap
    Session(contexts, fixtures)
  }

  private def _context(raw: JsObject): Context = {
    val oracle = Oracle(_right(CmlSemanticMetadata.read(raw)), _right(CmlStructureMetadata.read(raw)),
      _right(CmlClassificationMetadata.read(raw)), _right(CmlDynamicMetadata.read(raw)),
      _right(CmlUseCaseMetadata.read(raw)), _right(CmlTerminologyMetadata.read(raw)), _right(CmlCausalMetadata.read(raw)))
    val publication = _right(CmlSemanticPublication.read(raw))
    Context(raw, oracle, publication,
      _right(CmlTerminologyNavigation.build(_present(publication.terminology))),
      _right(CmlUseCaseNavigation.build(_present(publication.useCase))),
      _right(CmlDynamicTopology.build(_present(publication.dynamic))),
      _right(CmlClassificationTopology.build(_present(publication.classification))),
      _right(CmlCausalTraversal.build(_present(publication.causal))))
  }

  private def _question(session: Session, mode: String, id: String): JsObject =
    session.questions(mode).find(question => (question \ "id").as[String] == id).get

  private def _expect_answer(mode: String, question: JsObject, answer: Answer): Unit = {
    withClue(mode + "/" + (question \ "id").as[String] + " complete literal answer: ") {
      answer.expected shouldBe (question \ "expected").as[JsObject]
    }
    answer.checks.foreach(check => withClue(check.label + ": ") { check.equal shouldBe true })
  }

  private def _answer_matches(question: JsObject, answer: Answer): Boolean =
    answer.expected == (question \ "expected").as[JsObject] && answer.checks.nonEmpty && answer.checks.forall(_.equal)

  private def _answer(session: Session, mode: String, question: JsObject): Answer = {
    val context = session.contexts(mode)
    val facts = (question \ "expected" \ "sourceFacts").as[JsObject]
    val answer = (question \ "id").as[String] match {
      case "Q01" => _term_answer(context, facts)
      case "Q02" => _use_case_answer(context, facts)
      case "Q03" => _placement_answer(context, facts)
      case "Q04" => _reaction_answer(context, facts)
      case "Q05" => _workflow_answer(context, facts)
      case "Q06" => _classification_answer(context, facts)
      case "Q07" => _reference_answer(context, facts)
      case "Q08" => _unsupported_answer(context)
      case "A01" => _anonymous_actor_answer(context, facts)
      case "A02" => _field_gaps_answer(context, facts)
      case "A03" => _retained_answer(session)
      case "A04" => _anonymous_carriers_answer(context, facts)
      case unknown => throw new IllegalArgumentException("The fixed question ID is not admitted: " + unknown)
    }
    answer
  }

  private def _term_answer(context: Context, facts: JsObject): Answer = {
    val elementid = ModelElementId("terminology.a", "shared")
    val termid = TermId("terminology.a", "shared")
    val expected = _selected(context, context.oracle.terminology.elements, (facts \ "elementProjection").get,
      (value: CmlTerminologyMetadata.ElementProjection) => value.element)
    val expectedterm = context.oracle.terminology.terms.find(value => value.term.identity == termid).get
    val element = _right(context.terminology.element(elementid))
    val term = _right(context.terminology.term(termid))
    val forward = _right(context.terminology.links(elementid))
    val expectedforward = expected.termReferences match {
      case Present(references) => Present(references.map(reference => CmlTerminologyNavigation.TermLink(expected.element, reference)))
      case absence: Absent => absence
    }
    val reverse = _right(context.terminology.linksForTerm(termid))
    val expectedreverse = context.oracle.envelope.catalog.elements.flatMap { record =>
      _values(record.references).collect { case reference if reference.boundary == Local && reference.target == TermTarget(termid) =>
        CmlTerminologyNavigation.TermLink(record, reference)
      }
    }
    val resolutions = _values(forward).map(context.terminology.resolve)
    val expectedresolutions = _values(expectedforward).map { link =>
      if (link.reference.boundary == External) Right(CmlTerminologyNavigation.ExternalTarget(link))
      else Right(CmlTerminologyNavigation.LocalTarget(link, expectedterm))
    }
    val indexes = (facts \ "coreOccurrenceIndexes").as[Vector[Int]]
    val anonymouscores = indexes.map(index => context.publication.catalog.elements(index))
    val expectedanonymous = (facts \ "anonymousElementProjections").as[Vector[JsValue]].map(value =>
      _selected(context, context.oracle.terminology.elements, value, (projection: CmlTerminologyMetadata.ElementProjection) => projection.element))
    val anonymous = anonymouscores.map(record => _present(context.publication.terminology).elements.find(_.element == record).get)
    val reverseresolutions = reverse.map(context.terminology.resolve)
    val expectedreverseresolutions = expectedreverse.map(link => Right(CmlTerminologyNavigation.LocalTarget(link, expectedterm)))
    val noalias = _values(forward).forall(_.reference.target == TermTarget(termid)) &&
      !context.terminology.allLinks.exists(link => link.element == element.element && link.reference.target.isInstanceOf[ModelTarget])
    Answer(Json.obj("elementProjection" -> _term_element_json(context, element),
      "termProjection" -> _term_projection_json(context, term),
      "anonymousElementProjections" -> JsArray(anonymous.map(value => _term_element_json(context, value))),
      "coreOccurrenceIndexes" -> JsArray(indexes.map(JsNumber(_))),
      "anonymousCoreOccurrences" -> JsArray(anonymouscores.map(_record_json))),
      Json.obj("forwardBoundaries" -> JsArray(_values(forward).map(link => JsString(_boundary_name(link.reference.boundary)))),
        "reverseLocalOccurrences" -> reverse.size, "reverseAnonymousOccurrences" -> reverse.count(_.element.identity.isInstanceOf[Absent]),
        "preserveCompleteTermLink" -> (forward == expectedforward && reverse == expectedreverse),
        "externalResolution" -> (resolutions.last match { case Right(_: CmlTerminologyNavigation.ExternalTarget) => "ExternalTarget"; case _ => "unexpected" }),
        "noModelElementTermAlias" -> noalias),
      Vector(_same("full element projection", element, expected), _same("full Term projection", term, expectedterm),
        _same("ordered full forward TermLinks", forward, expectedforward), _same("ordered full reverse TermLinks", reverse, expectedreverse),
        _same("complete forward resolution objects", resolutions, expectedresolutions),
        _same("complete reverse resolution objects", reverseresolutions, expectedreverseresolutions),
        _same("ordered anonymous projections", anonymous, expectedanonymous),
        _same("ordered anonymous full records", anonymouscores, expectedanonymous.map(_.element))))
  }

  private def _use_case_answer(context: Context, facts: JsObject): Answer = {
    val expected = _selected(context, context.oracle.usecase.useCases, (facts \ "useCaseProjection").get,
      (value: CmlUseCaseMetadata.UseCaseProjection) => value.element)
    val usecase = _right(context.usecase.useCase(ModelElementId("usecase.example", "use-case:complete-order")))
    val actors = Vector("actor:requester", "actor:auditor").map(id => _right(context.usecase.actor(ModelElementId("usecase.example", id))))
    val expectedactors = (facts \ "actorProjections").as[Vector[JsValue]].map(value =>
      _selected(context, context.oracle.usecase.actors, value, (projection: CmlUseCaseMetadata.ActorProjection) => projection.element))
    val roles = _values(usecase.actorReferences)
    val resolutions = roles.map(role => context.usecase.resolve(role.target))
    val expectedresolutions = _values(expected.actorReferences).map { role =>
      if (role.target.boundary == External) Right(CmlUseCaseNavigation.ExternalTarget(role.target))
      else Right(CmlUseCaseNavigation.LocalTarget(_right(context.oracle.envelope.catalog.element(role.target.identity))))
    }
    val operationreference = _values(actors.head.operations).head
    val operationresolution = context.usecase.resolve(operationreference)
    val operation = _right(context.usecase.element(operationreference.identity))
    val expectedoperation = _expected_record(context, (facts \ "operationCore").get)
    Answer(Json.obj("useCaseProjection" -> _use_case_json(context, usecase),
      "actorProjections" -> JsArray(actors.map(value => _actor_json(context, value))), "operationCore" -> _record_json(operation)),
      Json.obj("roles" -> JsArray(roles.map(role => JsString(_role_name(role.role)))),
        "operation" -> _id_json(_id(operation)), "operationSourceLine" -> operation.source.line.get,
        "externalResolution" -> (resolutions(2) match { case Right(_: CmlUseCaseNavigation.ExternalTarget) => "ExternalTarget"; case _ => "unexpected" }),
        "claimsExecution" -> false),
      Vector(_same("full UseCase projection", usecase, expected), _same("full Actor projections", actors, expectedactors),
        _same("all five complete role resolutions", resolutions, expectedresolutions),
        _same("full operation core", operation, expectedoperation),
        _same("operation resolution retains full core", operationresolution, Right(CmlUseCaseNavigation.LocalTarget(expectedoperation))),
        _same("declared requester resolution", resolutions.head, Right(CmlUseCaseNavigation.LocalTarget(expectedactors.head.element))),
        _same("qualified Dynamic operation identity", _id(operation), ModelElementId("dynamic.example", "operation:complete"))))
  }
  private def _role_name(value: CmlUseCaseMetadata.ActorRole): String = value match {
    case CmlUseCaseMetadata.Primary => "primary"
    case CmlUseCaseMetadata.Secondary => "secondary"
    case CmlUseCaseMetadata.Supporting => "supporting"
    case CmlUseCaseMetadata.Stakeholder => "stakeholder"
    case CmlUseCaseMetadata.Actor => "actor"
  }

  private def _workflow_answer(context: Context, facts: JsObject): Answer = {
    val workflow = _right(context.dynamic.workflow(ModelElementId("dynamic.example", "workflow:fulfillment")))
    val expectedworkflow = _selected(context, context.oracle.dynamic.workflows, (facts \ "workflowProjection").get,
      (value: CmlDynamicMetadata.WorkflowProjection) => value.element)
    val machinereference = _values(workflow.stateMachines).head
    val machine = _right(context.dynamic.stateMachine(machinereference.identity))
    val expectedmachine = _selected(context, context.oracle.dynamic.stateMachines, (facts \ "stateMachineProjection").get,
      (value: CmlDynamicMetadata.StateMachineProjection) => value.element)
    val states = _values(machine.states).map(reference => _right(context.dynamic.state(reference.identity)))
    val expectedstates = (facts \ "stateProjections").as[Vector[JsValue]].map(value =>
      _selected(context, context.oracle.dynamic.states, value, (projection: CmlDynamicMetadata.StateProjection) => projection.element))
    val transition = _right(context.dynamic.transition(_values(machine.transitions).head.identity))
    val expectedtransition = _selected(context, context.oracle.dynamic.transitions, (facts \ "transitionProjection").get,
      (value: CmlDynamicMetadata.TransitionProjection) => value.element)
    val affected = _values(machine.affectedElements).map(reference => _right(context.dynamic.node(reference.identity)))
    val ownerreference = _present(machine.owner)
    val owner = _right(context.dynamic.node(ownerreference.identity))
    val structure = affected.map(record => _present(context.publication.structure).elements.find(_.element == record).get)
    val expectedstructure = (facts \ "structureProjections").as[Vector[JsValue]].map(value =>
      _selected(context, context.oracle.structure.elements, value, (projection: CmlStructureMetadata.ElementProjection) => projection.element))
    val nodes = Vector(workflow.element, machine.element) ++ states.map(_.element) :+ transition.element
    val queriednodes = nodes.map { record =>
      if (record == transition.element) {
        _right(context.causal.node(_id(record)))
      } else {
        _right(context.dynamic.node(_id(record)))
      }
    }
    val expectednodes = Vector(expectedworkflow.element, expectedmachine.element) ++ expectedstates.map(_.element) :+ expectedtransition.element
    Answer(Json.obj("workflowProjection" -> _workflow_json(context, workflow), "stateMachineProjection" -> _machine_json(context, machine),
      "stateProjections" -> JsArray(states.map(value => _state_json(context, value))),
      "transitionProjection" -> _transition_json(context, transition), "structureProjections" -> _structure_json(context, structure)),
      Json.obj("machineOwner" -> _id_json(_id(owner)), "affectedOrder" -> JsArray(affected.map(record => _id_json(_id(record)))),
        "workflowSourceLine" -> workflow.element.source.line.get, "machineSourceLine" -> machine.element.source.line.get),
      Vector(_same("full Workflow projection", workflow, expectedworkflow), _same("full StateMachine projection", machine, expectedmachine),
        _same("ordered full state projections", states, expectedstates), _same("full transition projection", transition, expectedtransition),
        _same("complete Dynamic nodes and transition carrier", queriednodes, expectednodes), _same("affected full Structure projections", structure, expectedstructure),
        _same("complete static owner", owner, expectedstructure(1).element),
        _same("machine reference is Local", machinereference.boundary, Local), _same("static owner reference is Local", ownerreference.boundary, Local),
        _same("full affected static records", affected, expectedstructure.map(_.element))))
  }

  private def _classification_answer(context: Context, facts: JsObject): Answer = {
    val subjectid = ModelElementId("structure.example", "entity:order")
    val dimensions = _right(context.classification.dimensions(subjectid))
    val expecteddimensions = (facts \ "dimensions").as[Vector[JsValue]].map(value =>
      _selected(context, context.oracle.classification.powertypeClassifications, value,
        (projection: CmlClassificationMetadata.PowertypeClassificationProjection) => projection.element))
    val individual = Vector("dimension:status", "dimension:category", "dimension:status-secondary")
      .map(id => _right(context.classification.dimension(ModelElementId("classification.example", id))))
    val subject = _right(context.classification.node(subjectid))
    val expectedsubject = _expected_record(context, (facts \ "subjectCore").get)
    val targetreferences = dimensions.map(value => _present(value.powertype))
    val targetcores = targetreferences.map(reference => _right(context.classification.node(reference.identity)))
    val targets = targetcores.distinct.map(record => _present(context.publication.classification).powertypes.find(_.element == record).get)
    val expectedtargets = (facts \ "powertypes").as[Vector[JsValue]].map(value =>
      _selected(context, context.oracle.classification.powertypes, value, (projection: CmlClassificationMetadata.PowertypeProjection) => projection.element))
    Answer(Json.obj("dimensions" -> _dimensions_json(context, dimensions), "powertypes" -> _powertypes_json(context, targets),
      "subjectCore" -> _record_json(subject)),
      Json.obj("dimensionOrder" -> JsArray(dimensions.map(value => _id_json(_id(value.element)))),
        "targetOrder" -> JsArray(targetreferences.map(reference => _id_json(reference.identity))),
        "sourceLines" -> JsArray(dimensions.map(value => JsNumber(value.element.source.line.get))),
        "mergedDimensions" -> (dimensions.size != individual.size || dimensions.map(_.element.identity).distinct.size != 3)),
      Vector(_same("complete ordered Classification dimensions", dimensions, expecteddimensions),
        _same("complete individual dimension queries", individual, expecteddimensions),
        _same("complete subject node", subject, expectedsubject), _same("complete target Powertype projections", targets, expectedtargets),
        _same("ordered full target nodes", targetcores, Vector(expectedtargets(0).element, expectedtargets(1).element, expectedtargets(0).element)),
        _same("three Local classification targets", targetreferences.map(_.boundary), Vector(Local, Local, Local))))
  }

  private def _placement_answer(context: Context, facts: JsObject): Answer = {
    val commandid = ModelElementId("causal.example", "command:place-order")
    val aggregateid = ModelElementId("causal.example", "aggregate:order")
    val entityid = ModelElementId("causal.example", "entity:order-item")
    val expectedcarrier = _selected(context, context.oracle.dynamic.causalLinks, (facts \ "causalCarrier").get,
      (value: CausalLinkProjection) => value.element)
    val consequences = _right(context.causal.consequences(commandid))
    val direct = _values(consequences)
    val actualcarrier = _values(context.causal.graph.causalLinks).find(_.element == direct.head.owner).get
    val affectedlinks = _right(context.causal.outgoing(commandid)).filter(link => link.owner == actualcarrier.element && link.kind == AffectedSubject)
    val expectedlinks = _carrier_links(expectedcarrier)
    val expectedaffected = expectedlinks.filter(_.kind == AffectedSubject)
    val subjectliterals = (facts \ "subjectCores").as[Vector[JsValue]]
    val (cores, corechecks) = _queried_cores(context, subjectliterals)
    val affectedcores = affectedlinks.map(link => _right(context.causal.resolve(link)) match {
      case CmlCausalTraversal.LocalTarget(_, record) => record
      case _ => throw new IllegalArgumentException("The declared affected Structure is Local.")
    })
    val structure = affectedcores.map(record => _present(context.publication.structure).elements.find(_.element == record).get)
    val expectedstructure = (facts \ "structureProjections").as[Vector[JsValue]].map(value =>
      _selected(context, context.oracle.structure.elements, value, (projection: CmlStructureMetadata.ElementProjection) => projection.element))
    val affecting = Vector(aggregateid, entityid).map(id => _right(context.causal.affecting(id)))
    val expectedaffecting = Vector(aggregateid, entityid).map(id => Present(context.oracle.dynamic.causalLinks.filter(carrier =>
      _values(carrier.affectedElements).exists(reference => reference.boundary == Local && reference.identity == id))))
    val aggregateconsequences = _right(context.causal.consequences(aggregateid))
    val membership = _present(_present(structure(1).aggregateBoundary).membership)
    Answer(Json.obj("causalCarrier" -> _carrier_json(actualcarrier), "subjectCores" -> JsArray(cores.map(_record_json)),
      "structureProjections" -> _structure_json(context, structure)),
      Json.obj("directKind" -> _kind_name(direct.head.kind), "directOwner" -> _id_json(_id(direct.head.owner)),
        "affectedOrder" -> JsArray(affectedcores.map(record => _id_json(_id(record)))),
        "aggregateConsequences" -> _presence_json(aggregateconsequences, (links: Vector[TraversalLink]) => JsArray(links.map(_link_json))),
        "membership" -> membership, "aggregateCausation" -> _values(aggregateconsequences).nonEmpty),
      corechecks ++ Vector(_same("full placement carrier", actualcarrier, expectedcarrier),
        _same("full affected Structure", structure, expectedstructure),
        _same("full affecting carriers for both subjects", affecting, expectedaffecting),
        _same("aggregate has available empty direct consequences", aggregateconsequences, Present(Vector.empty[TraversalLink]))) ++
        _link_checks(context, direct, expectedlinks.filter(_.kind == Cause), "placement direct Cause") ++
        _link_checks(context, affectedlinks, expectedaffected, "separate affected subjects"))
  }

  private def _reaction_answer(context: Context, facts: JsObject): Answer = {
    val expectedcarriers = (facts \ "causalCarriers").as[Vector[JsValue]].map(value =>
      _selected(context, context.oracle.dynamic.causalLinks, value, (projection: CausalLinkProjection) => projection.element))
    val carriernodes = expectedcarriers.map(value => _right(context.causal.node(_id(value.element))))
    val carriers = carriernodes.map(record => _values(context.causal.graph.causalLinks).find(_.element == record).get)
    val (cores, corechecks) = _queried_cores(context, (facts \ "subjectCores").as[Vector[JsValue]])
    val eventid = ModelElementId("causal.example", "event:order-placed")
    val operationid = ModelElementId("causal.example", "operation:reserve-stock")
    val ruleid = ModelElementId("causal.example", "rule:reserve-stock")
    val stockid = ModelElementId("causal.example", "event:stock-reserved")
    val incoming = _values(_right(context.causal.causes(eventid)))
    val reaction = _values(_right(context.causal.consequences(eventid)))
    val subsequent = _values(_right(context.causal.consequences(operationid)))
    val rulelinks = _right(context.causal.outgoing(eventid)).filter(_.kind == RuleInput) ++
      _right(context.causal.outgoing(ruleid)).filter(_.kind == RuleOutput)
    val expectedincoming = expectedcarriers.take(2).flatMap(_carrier_links).filter(_.kind == Cause)
    val expectedreaction = _carrier_links(expectedcarriers(2)).filter(_.kind == Reaction)
    val expectedsubsequent = _carrier_links(expectedcarriers(3)).filter(_.kind == Cause)
    val expectedrulelinks = _carrier_links(expectedcarriers(2)).filter(link => link.kind == RuleInput || link.kind == RuleOutput)
    val queries = Vector(eventid, ModelElementId("causal.example", "reaction:reservation"), operationid,
      ruleid, ModelElementId("causal.example", "cause:stock-event"), stockid)
    Answer(Json.obj("causalCarriers" -> JsArray(carriers.map(_carrier_json)), "subjectCores" -> JsArray(cores.map(_record_json))),
      Json.obj("incomingOwners" -> JsArray(incoming.map(link => _id_json(_id(link.owner)))),
        "reactionOwner" -> _id_json(_id(reaction.head.owner)), "subsequentCauseOwner" -> _id_json(_id(subsequent.head.owner)),
        "ruleKinds" -> JsArray(rulelinks.map(link => JsString(_kind_name(link.kind)))),
        "ruleEvidenceIndexes" -> JsArray(rulelinks.map(link => JsNumber(link.evidenceIndex))),
        "claimsPolicyExecution" -> _values(_right(context.causal.consequences(ruleid))).nonEmpty,
        "claimsTransitiveCause" -> reaction.exists(link => link.target == Present(ModelReference(stockid, Local)))),
      corechecks ++ Vector(_same("complete four causal carriers", carriers, expectedcarriers),
        _same("complete carrier nodes", carriernodes, expectedcarriers.map(_.element))) ++
        _link_checks(context, incoming, expectedincoming, "two direct incoming Causes") ++
        _link_checks(context, reaction, expectedreaction, "direct reservation Reaction") ++
        _link_checks(context, subsequent, expectedsubsequent, "subsequent stock-event Cause") ++
        _link_checks(context, rulelinks, expectedrulelinks, "separate rule input and output context") ++
        queries.flatMap(id => _finite_causal_checks(context, id)))
  }

  private def _reference_answer(context: Context, facts: JsObject): Answer = {
    val componentid = ModelElementId("causal.example", "component:inventory")
    val eventid = ModelElementId("causal.example", "event:order-placed")
    val queryid = ModelElementId("causal.example", "query:availability")
    val viewid = ModelElementId("causal.example", "view:order-summary")
    val (cores, corechecks) = _queried_cores(context, (facts \ "subjectCores").as[Vector[JsValue]])
    val references = Vector(componentid, eventid).map { id =>
      val record = _right(context.causal.node(id))
      context.causal.graph.modelReferences.find(_.element == record).get
    }
    val expectedreferences = (facts \ "modelReferences").as[Vector[JsValue]].map(value =>
      _selected(context, context.oracle.causal.modelReferences, value, (projection: CmlCausalMetadata.ModelReferences) => projection.element))
    val outgoing = Vector(componentid, eventid).map(id => _right(context.causal.outgoing(id)))
    val generic = outgoing.map(_.filter(_.kind == ModelReferenceLink))
    val expectedgeneric = expectedreferences.map(value => _reference_links(value.element, _values(value.references)))
    val eventresolutions = generic(1).map(context.causal.resolve)
    val componentresolutions = generic.head.map(context.causal.resolve)
    val querycauses = _right(context.causal.causes(queryid))
    val viewcauses = _right(context.causal.causes(viewid))
    Answer(Json.obj("modelReferences" -> JsArray(references.map(_model_references_json)), "subjectCores" -> JsArray(cores.map(_record_json))),
      Json.obj("eventReferenceBoundaries" -> JsArray(_values(references(1).references).map(reference => JsString(_boundary_name(reference.boundary)))),
        "externalResolution" -> (eventresolutions(2) match { case Right(_: CmlCausalTraversal.ExternalTarget) => "ExternalTarget"; case _ => "unexpected" }),
        "queryCauses" -> _presence_json(querycauses, (links: Vector[TraversalLink]) => JsArray(links.map(_link_json))),
        "viewCauses" -> _presence_json(viewcauses, (links: Vector[TraversalLink]) => JsArray(links.map(_link_json))),
        "claimsReadModelUpdate" -> (_values(querycauses).nonEmpty || _values(viewcauses).nonEmpty)),
      corechecks ++ Vector(_same("complete model-reference projections", references, expectedreferences),
        _same("nonresident External dependency resolution", componentresolutions, expectedgeneric.head.map(_expected_resolution(context, _))),
        _same("Query causes are available empty", querycauses, Present(Vector.empty[TraversalLink])),
        _same("View causes are available empty", viewcauses, Present(Vector.empty[TraversalLink]))) ++
        generic.zip(expectedgeneric).flatMap { case (actual, expected) => _link_checks(context, actual, expected, "generic references") } ++
        Vector(componentid, eventid, queryid, viewid).flatMap(id => _finite_causal_checks(context, id)))
  }

  // Finite expected requests use independent full supplier objects. These
  // constructions do not call navigation, topology or traversal to get answers.
  private def _carrier_links(carrier: CausalLinkProjection): Vector[TraversalLink] = {
    val owner = carrier.element
    val kind = carrier.kind match { case CmlDynamicMetadata.Cause => Cause; case CmlDynamicMetadata.Reaction => Reaction }
    val direct = TraversalLink(owner, kind, carrier.source, carrier.target, owner.source, Declared, "causalLinks", 0, None)
    val affected = _values(carrier.affectedElements).zipWithIndex.map { case (reference, index) =>
      TraversalLink(owner, AffectedSubject, carrier.source, Present(reference), owner.source,
        Derived("cozy.cml.causal.affected-subject.v1", Vector(owner.source)), "causalLinks.affectedElements", index, None)
    }
    val rules = carrier.rule match {
      case Present(reference) => Vector(
        TraversalLink(owner, RuleInput, carrier.source, Present(reference), owner.source,
          Derived("cozy.cml.causal.rule-input.v1", Vector(owner.source)), "causalLinks.rule", 0, None),
        TraversalLink(owner, RuleOutput, Present(reference), carrier.target, owner.source,
          Derived("cozy.cml.causal.rule-output.v1", Vector(owner.source)), "causalLinks.rule", 1, None))
      case _: Absent => Vector.empty
    }
    Vector(direct) ++ affected ++ rules
  }
  private def _reference_links(owner: ElementRecord, references: Vector[SemanticReference]): Vector[TraversalLink] = {
    val source = owner.identity match { case Present(identity) => Present(ModelReference(identity, Local)); case absence: Absent => absence }
    references.filter(_.target.isInstanceOf[ModelTarget]).zipWithIndex.map { case (reference, index) =>
      val ModelTarget(identity) = reference.target
      TraversalLink(owner, ModelReferenceLink, source, Present(ModelReference(identity, reference.boundary)),
        reference.source, reference.origin, "modelReferences.references", index, Some(reference))
    }
  }
  private def _finite_expected_links(context: Context): Vector[TraversalLink] = {
    // Only the fixed questions' direct causal and generic reference evidence is
    // requested here. Other family contexts are outside these finite requests.
    val remaining = scala.collection.mutable.ArrayBuffer(context.oracle.dynamic.causalLinks: _*)
    context.oracle.envelope.catalog.elements.flatMap { owner =>
      val index = remaining.indexWhere(_.element == owner)
      val causal = if (index < 0) Vector.empty[TraversalLink] else _carrier_links(remaining.remove(index))
      causal ++ _reference_links(owner, _values(owner.references))
    }
  }
  private def _local_endpoint(value: Presence[ModelReference], id: ModelElementId): Boolean = value match {
    case Present(ModelReference(identity, Local)) => identity == id
    case _ => false
  }
  private def _finite_causal_checks(context: Context, id: ModelElementId): Vector[SemanticCheck] = {
    val expected = _finite_expected_links(context)
    val outgoing = _right(context.causal.outgoing(id))
    val expectedoutgoing = expected.filter(link => _local_endpoint(link.source, id))
    val expectedincoming = expected.filter(link => _local_endpoint(link.target, id))
    val consequences = _right(context.causal.consequences(id))
    val causes = _right(context.causal.causes(id))
    val expectednode = _right(context.oracle.envelope.catalog.element(id))
    Vector(_same("complete finite node " + id, _right(context.causal.node(id)), expectednode),
      _same("complete finite consequences " + id, consequences, Present(expectedoutgoing.filter(_is_direct))),
      _same("complete finite causes " + id, causes, Present(expectedincoming.filter(_is_direct)))) ++
      _link_checks(context, outgoing, expectedoutgoing, "complete finite outgoing " + id)
  }
  private def _is_direct(link: TraversalLink): Boolean = link.kind == Cause || link.kind == Reaction
  private def _expected_resolution(context: Context, link: TraversalLink): Either[CmlCausalMetadata.CausalDiagnostic, CmlCausalTraversal.ReferenceTarget] =
    link.target match {
      case Present(ModelReference(identity, Local)) => Right(CmlCausalTraversal.LocalTarget(link,
        _right(context.oracle.envelope.catalog.element(identity))))
      case Present(ModelReference(_, External)) => Right(CmlCausalTraversal.ExternalTarget(link))
      case absence: Absent => Right(CmlCausalTraversal.AbsentTarget(link, absence))
    }
  private def _link_checks(context: Context, actual: Vector[TraversalLink], expected: Vector[TraversalLink], label: String): Vector[SemanticCheck] =
    Vector(_same(label + " all nine typed fields", actual, expected),
      _same(label + " complete wire observations", actual.map(_link_json), expected.map(_link_json)),
      _same(label + " full resolved objects", actual.map(context.causal.resolve), expected.map(_expected_resolution(context, _))))
  private def _kind_name(kind: CmlCausalTraversal.LinkKind): String = kind match {
    case Cause => "Cause"
    case Reaction => "Reaction"
    case AffectedSubject => "AffectedSubject"
    case RuleInput => "RuleInput"
    case RuleOutput => "RuleOutput"
    case ModelReferenceLink => "ModelReferenceLink"
    case CmlCausalTraversal.ActorOperation => "ActorOperation"
    case CmlCausalTraversal.WorkflowRule => "WorkflowRule"
    case CmlCausalTraversal.AggregateMember => "AggregateMember"
    case CmlCausalTraversal.Collaborator => "Collaborator"
  }
  private def _link_json(link: TraversalLink): JsObject = Json.obj("owner" -> _record_json(link.owner), "kind" -> _kind_name(link.kind),
    "source" -> _presence_json(link.source, _model_reference_json), "target" -> _presence_json(link.target, _model_reference_json),
    "sourceAttribution" -> _source_json(link.sourceAttribution), "origin" -> _origin_json(link.origin),
    "evidenceField" -> link.evidenceField, "evidenceIndex" -> link.evidenceIndex,
    "semanticReference" -> link.semanticReference.fold[JsValue](JsNull)(_reference_json))

  private def _unsupported_answer(context: Context): Answer = {
    val gap = context.causal.graph.capabilityGaps.find(value => value.owner.isEmpty && value.field == "causalInference").get
    val expected = CmlCausalMetadata.CapabilityGap(None, "causalInference", Absent(Unsupported,
      "No causation is inferred from names, membership, generic relations or expression text."))
    val aggregate = _right(context.causal.consequences(ModelElementId("causal.example", "aggregate:order")))
    val query = _right(context.causal.causes(ModelElementId("causal.example", "query:availability")))
    val view = _right(context.causal.causes(ModelElementId("causal.example", "view:order-summary")))
    val unsupported = gap.absence.reason == Unsupported
    Answer(Json.obj("unsupportedGap" -> _gap_json(gap)),
      Json.obj("causalInference" -> _absence_name(gap.absence.reason), "inferFromNames" -> !unsupported,
        "inferFromMembership" -> _values(aggregate).nonEmpty,
        "inferFromGenericRelations" -> (_values(query).nonEmpty || _values(view).nonEmpty), "claimsSourceCurrentness" -> false),
      Vector(_same("full global unsupported gap", gap, expected),
        _same("full literal source snapshot catalog", context.publication.catalog.elements, context.oracle.envelope.catalog.elements),
        _same("membership does not produce direct consequences", aggregate, Present(Vector.empty[TraversalLink])),
        _same("generic Query reference does not produce causes", query, Present(Vector.empty[TraversalLink])),
        _same("generic View reference does not produce causes", view, Present(Vector.empty[TraversalLink]))))
  }

  private def _anonymous_actor_answer(context: Context, facts: JsObject): Answer = {
    val expectedactor = _expected_record(context, (facts \ "actorCore").get)
    val actor = context.publication.catalog.elements.find(_ == expectedactor).get
    val operations = _values(context.causal.graph.actorOperations).find(_.element == actor).get
    val expectedoperations = context.oracle.causal.actorOperations match {
      case Present(values) => values.find(_.element == expectedactor).get
      case _: Absent => throw new IllegalArgumentException("The absence fixture retains the Use Case supplier.")
    }
    val diagnostic = context.causal.node(ModelElementId("causal.example", "actor:buyer"))
    val expecteddiagnostic = Left(CmlCausalMetadata.CausalDiagnostic(CmlCausalMetadata.UnknownElement,
      "node.identity", "The Causal element identity is unknown or unaddressable."))
    val observed = diagnostic.left.get
    Answer(Json.obj("actorCore" -> _record_json(actor), "actorOperationCarrier" -> _actor_operations_json(operations)),
      Json.obj("diagnostic" -> Json.obj("reason" -> observed.kind.toString, "field" -> observed.path, "detail" -> observed.detail),
        "identityRecovered" -> diagnostic.isRight),
      Vector(_same("full unaddressable Actor record", actor, expectedactor), _same("full Actor operations absence", operations, expectedoperations),
        _same("exact unknown-node diagnostic", diagnostic, expecteddiagnostic),
        _same("identity has no stable recovery", actor.identity, Absent(NotRepresented, "No stable causal Actor identity is represented.")),
        _same("core references retain full absence", actor.references, Absent(NotDeclared, "No causal fact is declared."))))
  }

  private def _field_gaps_answer(context: Context, facts: JsObject): Answer = {
    val owner = _right(context.causal.node(ModelElementId("causal.example", "cause:placement")))
    val carrier = _values(context.causal.graph.causalLinks).find(_.element == owner).get
    val expectedcarrier = _selected(context, context.oracle.dynamic.causalLinks, (facts \ "causalCarrier").get,
      (value: CausalLinkProjection) => value.element)
    val gaps = context.causal.graph.capabilityGaps.filter(_.owner.contains(owner))
    val expectedgaps = context.oracle.causal.capabilityGaps.filter(_.owner.contains(expectedcarrier.element))
    val fields = Vector(carrier.source, carrier.target, carrier.rule, carrier.affectedElements)
    val reasons = fields.map { case Absent(reason, _) => _absence_name(reason); case Present(_) => "present" }
    val direct = context.causal.allLinks.filter(link => link.owner == owner && _is_direct(link))
    val expecteddirect = _carrier_links(expectedcarrier).filter(_is_direct)
    Answer(Json.obj("causalCarrier" -> _carrier_json(carrier), "capabilityGaps" -> JsArray(gaps.map(_gap_json))),
      Json.obj("reasons" -> JsArray(reasons.map(JsString)), "capabilityGapFields" -> JsArray(gaps.map(gap => JsString(gap.field))),
        "syntheticEmptySuccess" -> fields.exists(_.isInstanceOf[Present[_]])),
      Vector(_same("complete four-field carrier absence", carrier, expectedcarrier),
        _same("complete ordered gaps with full owner", gaps, expectedgaps),
        _same("complete carrier owner node", owner, expectedcarrier.element)) ++
        _link_checks(context, direct, expecteddirect, "full absent direct target"))
  }

  private def _retained_answer(session: Session): Answer = {
    val ids = Vector("Q01", "Q02", "Q05", "Q06")
    val answers = ids.map { id =>
      val declaredquestion = _question(session, "declared", id)
      val absencequestion = _question(session, "absence", id)
      val declared = _answer(session, "declared", declaredquestion)
      val absence = _answer(session, "absence", absencequestion)
      (id, declaredquestion, absencequestion, declared, absence)
    }
    val retained = answers.collect { case (id, declaredquestion, absencequestion, declared, absence)
      if declared.expected == absence.expected && _answer_matches(declaredquestion, declared) && _answer_matches(absencequestion, absence) => id }
    val absence = session.contexts("absence").publication
    val available = Vector(absence.terminology, absence.useCase, absence.dynamic, absence.classification).forall(_.isInstanceOf[Present[_]])
    Answer(Json.obj("retainedQuestionIds" -> JsArray(ids.map(JsString))),
      Json.obj("completeAnswersEqualDeclared" -> JsArray(retained.map(JsString)), "globalFamilyUnavailable" -> !available),
      answers.flatMap { case (id, declaredquestion, absencequestion, declared, inherited) =>
        Vector(_same(id + " complete inherited answer", inherited.expected, declared.expected),
          SemanticCheck(id + " declared literal and typed answer", _answer_matches(declaredquestion, declared)),
          SemanticCheck(id + " absence literal and typed answer", _answer_matches(absencequestion, inherited))) ++ declared.checks ++ inherited.checks
      })
  }

  private def _anonymous_carriers_answer(context: Context, facts: JsObject): Answer = {
    val command = _right(context.causal.node(ModelElementId("causal.example", "command:place-order")))
    val expectedcommand = _expected_record(context, (facts \ "commandCore").get)
    val consequences = _right(context.causal.consequences(_id(command)))
    val cores = Vector(73, 74).map(index => context.publication.catalog.elements(index))
    val expectedcores = (facts \ "anonymousCoreOccurrences").as[Vector[JsValue]].map(value => _expected_record(context, value))
    val carriers = _values(context.causal.graph.causalLinks).filter(value => cores.contains(value.element))
    val expectedcarriers = context.oracle.dynamic.causalLinks.filter(value => expectedcores.contains(value.element))
    val gaps = context.causal.graph.capabilityGaps.filter(gap => gap.owner.exists(cores.contains))
    val expectedgaps = context.oracle.causal.capabilityGaps.filter(gap => gap.owner.exists(expectedcores.contains))
    val gap = context.causal.graph.capabilityGaps.find(value => value.owner.isEmpty && value.field == "causalInference").get
    val expectedgap = context.oracle.causal.capabilityGaps.find(value => value.owner.isEmpty && value.field == "causalInference").get
    val workflow = _right(context.dynamic.workflow(ModelElementId("dynamic.example", "workflow:fulfillment")))
    val expectedworkflow = _selected(context, context.oracle.dynamic.workflows, (facts \ "availableWorkflowProjection").get,
      (value: CmlDynamicMetadata.WorkflowProjection) => value.element)
    val direct = context.causal.allLinks.filter(link => cores.contains(link.owner) && _is_direct(link))
    val expecteddirect = expectedcarriers.flatMap(_carrier_links).filter(_is_direct)
    Answer(Json.obj("commandCore" -> _record_json(command), "anonymousCoreOccurrences" -> JsArray(cores.map(_record_json)),
      "anonymousCausalCarriers" -> JsArray(carriers.map(_carrier_json)), "anonymousCapabilityGaps" -> JsArray(gaps.map(_gap_json)),
      "unsupportedGap" -> _gap_json(gap), "availableWorkflowProjection" -> _workflow_json(context, workflow)),
      Json.obj("consequences" -> _presence_json(consequences, (links: Vector[TraversalLink]) => JsArray(links.map(_link_json))),
        "dynamicAvailable" -> context.publication.dynamic.isInstanceOf[Present[_]], "anonymousCoreEqual" -> (cores(0) == cores(1)),
        "anonymousCarrierKinds" -> JsArray(carriers.map(value => JsString(value.kind match {
          case CmlDynamicMetadata.Cause => "cause"; case CmlDynamicMetadata.Reaction => "reaction"
        }))), "identityRecovered" -> cores.exists(_.identity.isInstanceOf[Present[_]])),
      Vector(_same("complete available command core", command, expectedcommand),
        _same("direct consequences remain available empty", consequences, Present(Vector.empty[TraversalLink])),
        _same("ordered full equal anonymous core occurrences", cores, expectedcores),
        _same("ordered distinct full anonymous carrier payloads", carriers, expectedcarriers),
        _same("all ordered anonymous gaps", gaps, expectedgaps), _same("complete global gap", gap, expectedgap),
        _same("full available Workflow", workflow, expectedworkflow)) ++
        _link_checks(context, direct, expecteddirect, "ordered anonymous direct occurrences"))
  }

  private def _same[A](label: String, actual: A, expected: A): SemanticCheck = SemanticCheck(label, actual == expected)
  private def _present[A](value: Presence[A]): A = value match {
    case Present(present) => present
    case absence: Absent => throw new IllegalArgumentException("An admitted available value is required: " + absence)
  }
  private def _values[A](value: Presence[Vector[A]]): Vector[A] = value match {
    case Present(values) => values
    case _: Absent => Vector.empty
  }
  private def _right[A, B](value: Either[A, B]): B = value.right.get
  private def _id(record: ElementRecord): ModelElementId = _present(record.identity)
  private def _model_id(value: JsValue): ModelElementId =
    ModelElementId((value \ "modelId").as[String], (value \ "elementId").as[String])
  private def _expected_record(context: Context, value: JsValue): ElementRecord = {
    val records = (context.raw \ "elements").as[Vector[JsValue]].zip(context.oracle.envelope.catalog.elements)
    records.find(_._1 == value).get._2
  }
  private def _selected[A](context: Context, values: Vector[A], value: JsValue, record: A => ElementRecord): A = {
    val owner = _expected_record(context, (value \ "element").get)
    values.find(projection => record(projection) == owner).get
  }
  private def _queried_cores(context: Context, literals: Vector[JsValue]): (Vector[ElementRecord], Vector[SemanticCheck]) = {
    val expected = literals.map(value => _expected_record(context, value))
    val actual = expected.map(record => _right(context.causal.node(_id(record))))
    (actual, Vector(_same("complete queried core records", actual, expected)))
  }

  // Encoding is observation only. Every expected block remains a literal resource
  // selected from predecessor source facts, never an output from these encoders.
  private def _presence_json[A](value: Presence[A], encode: A => JsValue): JsObject = value match {
    case Present(present) => Json.obj("status" -> "present", "value" -> encode(present))
    case Absent(reason, detail) => Json.obj("status" -> "absent", "reason" -> _absence_name(reason), "detail" -> detail)
  }
  private def _absence_name(reason: AbsenceReason): String = reason match {
    case NotDeclared => "not-declared"
    case NotRepresented => "not-represented"
    case Unsupported => "unsupported"
    case NotApplicable => "not-applicable"
  }
  private def _boundary_name(boundary: ReferenceBoundary): String = boundary match {
    case Local => "local"
    case External => "external"
  }
  private def _id_json(value: ModelElementId): JsObject = Json.obj("modelId" -> value.modelId, "elementId" -> value.elementId)
  private def _term_id_json(value: TermId): JsObject = Json.obj("vocabularyId" -> value.vocabularyId, "termId" -> value.termId)
  private def _source_json(value: SourceAttribution): JsObject = Json.obj("authorityId" -> value.authorityId,
    "path" -> value.path, "line" -> value.line.fold[JsValue](JsNull)(line => JsNumber(line)))
  private def _origin_json(value: ReferenceOrigin): JsObject = value match {
    case Declared => Json.obj("kind" -> "declared")
    case Derived(ruleid, sources) => Json.obj("kind" -> "derived", "ruleId" -> ruleid, "sources" -> JsArray(sources.map(_source_json)))
  }
  private def _label_json(value: LocalizedLabel): JsObject = Json.obj("text" -> value.text,
    "language" -> value.language.fold[JsValue](JsNull)(JsString))
  private def _reference_json(value: SemanticReference): JsObject = {
    val target = value.target match {
      case ModelTarget(identity) => Json.obj("kind" -> "model-element", "identity" -> _id_json(identity))
      case TermTarget(identity) => Json.obj("kind" -> "term", "identity" -> _term_id_json(identity))
    }
    Json.obj("target" -> target, "boundary" -> _boundary_name(value.boundary),
      "relation" -> _presence_json(value.relation, (relation: RelationId) =>
        Json.obj("vocabularyId" -> relation.vocabularyId, "relationId" -> relation.relationId)),
      "profile" -> _presence_json(value.profile, (profile: ProfileId) =>
        Json.obj("vocabularyId" -> profile.vocabularyId, "profileId" -> profile.profileId)),
      "context" -> _presence_json(value.context, JsString), "preferredLabel" -> _presence_json(value.preferredLabel, _label_json),
      "source" -> _source_json(value.source), "origin" -> _origin_json(value.origin))
  }
  private def _record_json(value: ElementRecord): JsObject = Json.obj(
    "identity" -> _presence_json(value.identity, _id_json), "kind" -> value.kind, "name" -> value.name,
    "source" -> _source_json(value.source),
    "references" -> _presence_json(value.references, (references: Vector[SemanticReference]) => JsArray(references.map(_reference_json))))
  private def _model_reference_json(value: ModelReference): JsObject =
    Json.obj("identity" -> _id_json(value.identity), "boundary" -> _boundary_name(value.boundary))
  private def _carrier_json(value: CausalLinkProjection): JsObject = Json.obj("element" -> _record_json(value.element),
    "kind" -> (value.kind match { case CmlDynamicMetadata.Cause => "cause"; case CmlDynamicMetadata.Reaction => "reaction" }),
    "source" -> _presence_json(value.source, _model_reference_json), "target" -> _presence_json(value.target, _model_reference_json),
    "rule" -> _presence_json(value.rule, _model_reference_json),
    "affectedElements" -> _presence_json(value.affectedElements, (references: Vector[ModelReference]) => JsArray(references.map(_model_reference_json))))
  private def _gap_json(value: CmlCausalMetadata.CapabilityGap): JsObject = Json.obj(
    "owner" -> value.owner.fold[JsValue](JsNull)(_record_json), "field" -> value.field,
    "absence" -> _presence_json(value.absence, (absent: Nothing) => absent))
  private def _model_references_json(value: CmlCausalMetadata.ModelReferences): JsObject = Json.obj(
    "element" -> _record_json(value.element),
    "references" -> _presence_json(value.references, (references: Vector[SemanticReference]) => JsArray(references.map(_reference_json))))
  private def _actor_operations_json(value: CmlCausalMetadata.ActorOperations): JsObject = Json.obj(
    "element" -> _record_json(value.element),
    "operations" -> _presence_json(value.operations, (references: Vector[ModelReference]) => JsArray(references.map(_model_reference_json))))
  private def _aggregate_boundary_json(value: CmlStructureMetadata.AggregateBoundary): JsObject = Json.obj(
    "aggregate" -> _presence_json(value.aggregate, (reference: CmlStructureMetadata.ModelReference) =>
      Json.obj("identity" -> _id_json(reference.identity), "boundary" -> _boundary_name(reference.boundary))),
    "membership" -> _presence_json(value.membership, JsString))
  private def _causal_json(graph: CmlCausalMetadata.Graph): JsObject = Json.obj(
    "schemaVersion" -> "cozy.cml.causal.v1",
    "causalLinks" -> _presence_json(graph.causalLinks, (values: Vector[CausalLinkProjection]) => JsArray(values.map(_carrier_json))),
    "actorOperations" -> _presence_json(graph.actorOperations, (values: Vector[CmlCausalMetadata.ActorOperations]) => JsArray(values.map(_actor_operations_json))),
    "aggregateBoundaries" -> _presence_json(graph.aggregateBoundaries, (values: Vector[CmlCausalMetadata.AggregateMembership]) => JsArray(values.map(value =>
      Json.obj("element" -> _record_json(value.element), "aggregateBoundary" -> _presence_json(value.aggregateBoundary, _aggregate_boundary_json))))),
    "ruleContexts" -> _presence_json(graph.ruleContexts, (values: Vector[CmlCausalMetadata.RuleContext]) => JsArray(values.map(value =>
      Json.obj("element" -> _record_json(value.element),
        "rules" -> _presence_json(value.rules, (references: Vector[ModelReference]) => JsArray(references.map(_model_reference_json))))))),
    "collaboratorContexts" -> _presence_json(graph.collaboratorContexts, (values: Vector[CmlCausalMetadata.CollaboratorContext]) => JsArray(values.map(value =>
      Json.obj("element" -> _record_json(value.element),
        "collaborators" -> _presence_json(value.collaborators, (references: Vector[ModelReference]) => JsArray(references.map(_model_reference_json))))))),
    "modelReferences" -> JsArray(graph.modelReferences.map(_model_references_json)), "capabilityGaps" -> JsArray(graph.capabilityGaps.map(_gap_json)))
  private def _record_map(context: Context): Map[ElementRecord, JsValue] =
    context.publication.catalog.elements.map(record => record -> (_record_json(record): JsValue)).toMap
  private def _term_element_json(context: Context, value: CmlTerminologyMetadata.ElementProjection): JsObject =
    CmlTerminologyProjectionCodec.elementJson(CmlTerminologyProjectionCodec.correspondence(context.publication.envelope), value)
  private def _term_projection_json(context: Context, value: CmlTerminologyMetadata.TermProjection): JsObject =
    CmlTerminologyProjectionCodec.termJson(CmlTerminologyProjectionCodec.correspondence(context.publication.envelope), value)
  private def _actor_json(context: Context, value: CmlUseCaseMetadata.ActorProjection): JsObject =
    CmlUseCaseProjectionCodec.actorJson(_record_map(context), value)
  private def _use_case_json(context: Context, value: CmlUseCaseMetadata.UseCaseProjection): JsObject =
    CmlUseCaseProjectionCodec.useCaseJson(_record_map(context), value)
  private def _workflow_json(context: Context, value: CmlDynamicMetadata.WorkflowProjection): JsObject =
    CmlDynamicWorkflowCodec.workflowJson(_record_map(context), value)
  private def _machine_json(context: Context, value: CmlDynamicMetadata.StateMachineProjection): JsObject =
    CmlDynamicStateMachineCodec.stateMachineJson(_record_map(context), value)
  private def _state_json(context: Context, value: CmlDynamicMetadata.StateProjection): JsObject =
    CmlDynamicStateMachineCodec.stateJson(_record_map(context), value)
  private def _transition_json(context: Context, value: CmlDynamicMetadata.TransitionProjection): JsObject =
    CmlDynamicStateMachineCodec.transitionJson(_record_map(context), value)
  private def _structure_json(context: Context, values: Vector[CmlStructureMetadata.ElementProjection]): JsArray =
    (CmlStructureMetadataCodec.structureJson(context.publication.catalog, values, Vector.empty) \ "elements").as[JsArray]
  private def _dimensions_json(context: Context, values: Vector[CmlClassificationMetadata.PowertypeClassificationProjection]): JsArray =
    (CmlClassificationMetadataCodec.classificationJson(context.publication.envelope, Vector.empty,
      Vector.empty, Vector.empty, Vector.empty, values) \ "powertypeClassifications").as[JsArray]
  private def _powertypes_json(context: Context, values: Vector[CmlClassificationMetadata.PowertypeProjection]): JsArray =
    (CmlClassificationMetadataCodec.classificationJson(context.publication.envelope, Vector.empty,
      values, Vector.empty, Vector.empty, Vector.empty) \ "powertypes").as[JsArray]

  private def _publication_checks(context: Context): Vector[Boolean] = {
    val expected = context.oracle
    val publication = context.publication
    val structure = _present(publication.structure)
    val classification = _present(publication.classification)
    val dynamic = _present(publication.dynamic)
    val usecase = _present(publication.useCase)
    val terminology = _present(publication.terminology)
    val causal = _present(publication.causal)
    val peers = Vector(structure.envelope, classification.envelope, dynamic.envelope, usecase.envelope, terminology.envelope, causal.envelope)
    val extensions = publication.envelope.extensions
    Vector(
      structure.elements == expected.structure.elements, structure.relations == expected.structure.relations,
      classification.traits == expected.classification.traits, classification.powertypes == expected.classification.powertypes,
      classification.generalizations == expected.classification.generalizations,
      classification.traitRealizations == expected.classification.traitRealizations,
      classification.powertypeClassifications == expected.classification.powertypeClassifications,
      dynamic.workflows == expected.dynamic.workflows, dynamic.activities == expected.dynamic.activities,
      dynamic.flows == expected.dynamic.flows, dynamic.stateMachines == expected.dynamic.stateMachines,
      dynamic.states == expected.dynamic.states, dynamic.transitions == expected.dynamic.transitions,
      dynamic.causalLinks == expected.dynamic.causalLinks, usecase.actors == expected.usecase.actors,
      usecase.useCases == expected.usecase.useCases, terminology.elements == expected.terminology.elements,
      terminology.terms == expected.terminology.terms, causal.causalLinks == expected.causal.causalLinks,
      causal.actorOperations == expected.causal.actorOperations, causal.aggregateBoundaries == expected.causal.aggregateBoundaries,
      causal.ruleContexts == expected.causal.ruleContexts, causal.collaboratorContexts == expected.causal.collaboratorContexts,
      causal.modelReferences == expected.causal.modelReferences, causal.capabilityGaps == expected.causal.capabilityGaps,
      publication.catalog.elements == expected.envelope.catalog.elements, publication.catalog.terms == expected.envelope.catalog.terms,
      CmlSemanticPublication.toJson(publication) == context.raw,
      CmlStructureMetadataCodec.structureJson(publication.catalog, structure.elements, structure.relations) == extensions.value(CmlStructureMetadata.namespace),
      CmlClassificationMetadataCodec.classificationJson(publication.envelope, classification.traits, classification.powertypes,
        classification.generalizations, classification.traitRealizations, classification.powertypeClassifications) == extensions.value(CmlClassificationMetadata.namespace),
      CmlDynamicMetadataCodec.dynamicJson(publication.envelope, dynamic.workflows, dynamic.activities, dynamic.flows,
        dynamic.stateMachines, dynamic.states, dynamic.transitions, dynamic.causalLinks) == extensions.value(CmlDynamicMetadata.namespace),
      CmlUseCaseMetadataCodec.useCaseJson(publication.envelope, usecase.actors, usecase.useCases) == extensions.value(CmlUseCaseMetadata.namespace),
      CmlTerminologyMetadataCodec.terminologyJson(CmlTerminologyProjectionCodec.correspondence(publication.envelope),
        terminology.elements, terminology.terms) == extensions.value(CmlTerminologyMetadata.namespace),
      _causal_json(causal) == extensions.value(CmlCausalMetadata.namespace),
      publication.catalog.terms.map(value => Json.obj("identity" -> _term_id_json(value.identity), "source" -> _source_json(value.source))) ==
        (context.raw \ "terms").as[Vector[JsValue]]
    ) ++ peers.flatMap(envelope => Vector(envelope.catalog.elements == expected.envelope.catalog.elements,
      envelope.catalog.terms == expected.envelope.catalog.terms, CmlSemanticMetadata.toJson(envelope) == context.raw)) ++
      Vector(publication.catalog.elements.map(_record_json) == (context.raw \ "elements").as[Vector[JsValue]])
  }

  private def _execution_order(keys: Vector[String], salt: Int): Vector[String] = {
    val rotated = keys.drop(salt % keys.size) ++ keys.take(salt % keys.size)
    if ((salt & 1) == 0) rotated.reverse else rotated
  }
  private def _reconstruct(value: JsValue, salt: Int): JsValue = value match {
    case objectvalue: JsObject =>
      val fields = objectvalue.fields.toVector.sortBy(_._1)
      val offset = Math.floorMod(salt, fields.size.max(1))
      val rotated = fields.drop(offset) ++ fields.take(offset)
      val ordered = if ((salt & 1) == 0) rotated.reverse else rotated
      JsObject(ListMap(ordered.map { case (key, child) => key -> _reconstruct(child, salt + key.length) }: _*))
    case JsArray(values) => JsArray(values.map(child => _reconstruct(child, salt)))
    case scalar => scalar
  }

  private val _matrix: Map[String, Vector[(String, JsObject, JsArray)]] = Map(
    "declared" -> Vector(
      ("Q01", Json.parse("""{"element":{"modelId":"terminology.a","elementId":"shared"},"term":{"vocabularyId":"terminology.a","termId":"shared"}}""").as[JsObject],
        Json.parse("""[{"namespace":"cozy.cml.terminology","field":"termReferences","owner":{"modelId":"terminology.a","elementId":"shared"}}]""").as[JsArray]),
      ("Q02", Json.parse("""{"useCase":{"modelId":"usecase.example","elementId":"use-case:complete-order"}}""").as[JsObject],
        Json.parse("""[{"namespace":"cozy.cml.use-case","field":"actorReferences","owner":{"modelId":"usecase.example","elementId":"use-case:complete-order"}}]""").as[JsArray]),
      ("Q03", Json.parse("""{"operation":{"modelId":"causal.example","elementId":"command:place-order"}}""").as[JsObject],
        Json.parse("""[{"namespace":"cozy.cml.causal","field":"causalLinks","owner":{"modelId":"causal.example","elementId":"cause:placement"}}]""").as[JsArray]),
      ("Q04", Json.parse("""{"event":{"modelId":"causal.example","elementId":"event:order-placed"},"operation":{"modelId":"causal.example","elementId":"operation:reserve-stock"},"rule":{"modelId":"causal.example","elementId":"rule:reserve-stock"}}""").as[JsObject],
        Json.parse("""[{"namespace":"cozy.cml.causal","field":"causalLinks","owner":{"modelId":"causal.example","elementId":"reaction:reservation"}}]""").as[JsArray]),
      ("Q05", Json.parse("""{"workflow":{"modelId":"dynamic.example","elementId":"workflow:fulfillment"}}""").as[JsObject],
        Json.parse("""[{"namespace":"cozy.cml.dynamic","field":"stateMachines","owner":{"modelId":"dynamic.example","elementId":"workflow:fulfillment"}}]""").as[JsArray]),
      ("Q06", Json.parse("""{"subject":{"modelId":"structure.example","elementId":"entity:order"}}""").as[JsObject],
        Json.parse("""[{"namespace":"cozy.cml.classification","field":"powertypeClassifications","owner":{"modelId":"structure.example","elementId":"entity:order"}}]""").as[JsArray]),
      ("Q07", Json.parse("""{"component":{"modelId":"causal.example","elementId":"component:inventory"},"event":{"modelId":"causal.example","elementId":"event:order-placed"},"query":{"modelId":"causal.example","elementId":"query:availability"},"view":{"modelId":"causal.example","elementId":"view:order-summary"}}""").as[JsObject],
        Json.parse("""[{"namespace":"cozy.cml.causal","field":"modelReferences.references","owner":{"modelId":"causal.example","elementId":"event:order-placed"}}]""").as[JsArray]),
      ("Q08", Json.parse("""{"capability":"causalInference"}""").as[JsObject],
        Json.parse("""[{"namespace":"cozy.cml.causal","field":"causalInference","owner":null}]""").as[JsArray])
    ),
    "absence" -> Vector(
      ("Q01", Json.parse("""{"element":{"modelId":"terminology.a","elementId":"shared"},"term":{"vocabularyId":"terminology.a","termId":"shared"}}""").as[JsObject],
        Json.parse("""[{"namespace":"cozy.cml.terminology","field":"termReferences","owner":{"modelId":"terminology.a","elementId":"shared"}}]""").as[JsArray]),
      ("Q02", Json.parse("""{"useCase":{"modelId":"usecase.example","elementId":"use-case:complete-order"}}""").as[JsObject],
        Json.parse("""[{"namespace":"cozy.cml.use-case","field":"actorReferences","owner":{"modelId":"usecase.example","elementId":"use-case:complete-order"}}]""").as[JsArray]),
      ("Q05", Json.parse("""{"workflow":{"modelId":"dynamic.example","elementId":"workflow:fulfillment"}}""").as[JsObject],
        Json.parse("""[{"namespace":"cozy.cml.dynamic","field":"stateMachines","owner":{"modelId":"dynamic.example","elementId":"workflow:fulfillment"}}]""").as[JsArray]),
      ("Q06", Json.parse("""{"subject":{"modelId":"structure.example","elementId":"entity:order"}}""").as[JsObject],
        Json.parse("""[{"namespace":"cozy.cml.classification","field":"powertypeClassifications","owner":{"modelId":"structure.example","elementId":"entity:order"}}]""").as[JsArray]),
      ("Q08", Json.parse("""{"capability":"causalInference"}""").as[JsObject],
        Json.parse("""[{"namespace":"cozy.cml.causal","field":"causalInference","owner":null}]""").as[JsArray]),
      ("A01", Json.parse("""{"actor":{"modelId":"causal.example","elementId":"actor:buyer"}}""").as[JsObject],
        Json.parse("""[{"namespace":"cozy.cml.causal","field":"node.identity","owner":{"modelId":"causal.example","elementId":"actor:buyer"}}]""").as[JsArray]),
      ("A02", Json.parse("""{"carrier":{"modelId":"causal.example","elementId":"cause:placement"}}""").as[JsObject],
        Json.parse("""[{"namespace":"cozy.cml.causal","field":"causalLinks","owner":{"modelId":"causal.example","elementId":"cause:placement"}}]""").as[JsArray]),
      ("A03", Json.parse("""{"retainedQuestionIds":["Q01","Q02","Q05","Q06"]}""").as[JsObject],
        Json.parse("""[{"namespace":"cozy.cml.causal","field":"causalLinks","owner":{"modelId":"causal.example","elementId":"command:place-order"}}]""").as[JsArray]),
      ("A04", Json.parse("""{"command":{"modelId":"causal.example","elementId":"command:place-order"},"anonymousOccurrenceIndexes":[73,74]}""").as[JsObject],
        Json.parse("""[{"namespace":"cozy.cml.causal","field":"causalLinks","owner":{"modelId":"causal.example","elementId":"command:place-order"}}]""").as[JsArray])
    )
  )

  private def _fixture_checks(fixture: JsObject, mode: String): Vector[Boolean] = {
    val questions = (fixture \ "questions").as[Vector[JsObject]]
    val matrix = _matrix(mode)
    Vector(fixture.keys == Set("schemaVersion", "publicationResource", "mode", "questions"),
      (fixture \ "schemaVersion").as[String] == "cozy.cml.semantic-questions.v1",
      (fixture \ "publicationResource").as[String] == "causal-metadata-v1-" + mode + ".json",
      (fixture \ "mode").as[String] == mode, questions.size == matrix.size,
      questions.map(question => (question \ "id").as[String]) == matrix.map(_._1)) ++
      questions.zip(matrix).flatMap { case (question, (id, inputs, evidence)) => Vector(
        question.keys == Set("id", "question", "inputs", "expected", "evidence"),
        (question \ "id").as[String] == id, (question \ "question").as[String].nonEmpty,
        (question \ "inputs").as[JsObject] == inputs, (question \ "evidence").as[JsArray] == evidence,
        (question \ "expected").as[JsObject].keys == Set("sourceFacts", "answerAssertions")) }
  }

  private def _resource(name: String): JsObject = {
    val source = Source.fromInputStream(getClass.getResourceAsStream("/cozy/modeler/" + name), "UTF-8")
    try Json.parse(source.mkString).as[JsObject] finally source.close()
  }
}

private object CmlSemanticPublicationFixtureSpec {
  private final case class Oracle(
    envelope: CmlSemanticMetadata.Envelope,
    structure: CmlStructureMetadata.Graph,
    classification: CmlClassificationMetadata.Graph,
    dynamic: CmlDynamicMetadata.Graph,
    usecase: CmlUseCaseMetadata.Graph,
    terminology: CmlTerminologyMetadata.Graph,
    causal: CmlCausalMetadata.Graph
  )
  private final case class Context(
    raw: JsObject,
    oracle: Oracle,
    publication: CmlSemanticPublication.Publication,
    terminology: CmlTerminologyNavigation.Navigation,
    usecase: CmlUseCaseNavigation.Navigation,
    dynamic: CmlDynamicTopology.Topology,
    classification: CmlClassificationTopology.Topology,
    causal: CmlCausalTraversal.Traversal
  )
  private final case class Session(contexts: Map[String, Context], fixtures: Map[String, JsObject]) {
    def questions(mode: String): Vector[JsObject] = (fixtures(mode) \ "questions").as[Vector[JsObject]]
  }
  private final case class SemanticCheck(label: String, equal: Boolean)
  private final case class Answer(sourcefacts: JsObject, assertions: JsObject, checks: Vector[SemanticCheck]) {
    def expected: JsObject = Json.obj("sourceFacts" -> sourcefacts, "answerAssertions" -> assertions)
  }
}
