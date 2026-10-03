package cozy.modeler

import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.{Files, Path, Paths}
import org.goldenport.kaleidox.{Config => KaleidoxConfig, Model => KaleidoxModel}
import org.goldenport.realm.Realm
import org.scalacheck.{Gen, Prop, Test}
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import play.api.libs.json.{JsNull, JsNumber, JsObject, JsValue, Json}

/*
 * @since   Oct.  3, 2026
 * @version Oct.  3, 2026
 * @author  ASAMI, Tomoharu
 */
/** Interface-driven contract for the lossless, collection-selected Workflow v5 producer. */
final class WorkflowSemanticOutcomeAbiGenerationSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  private val _base = Paths.get(sys.props("user.dir")).toAbsolutePath.normalize()
  private val _root = "target/scala-3.3.8/src_managed/main/scala/domain/statemachine/workflow"
  private val _abi_path = s"${_root}/StateMachineWorkflowAbi.scala"
  private val _bootstrap_path = s"${_root}/StateMachineWorkflowComponentFactoryBootstrap.scala"
  private val _fixture_path = _base.resolve("src/test/resources/modeler/workflow-semantic-outcome.cml")
  private val _baseline = _base.resolve("src/test/resources/modeler/workflow-semantic-outcome-v4-baseline")
  private val _schema = "cozy.cml.statemachine-workflow-abi.v5"
  private val _bootstrap_schema = "cozy.cml.statemachine-workflow-bootstrap.v5"
  private val _escaped = "quote\"slash\\line\nreturn\rtab\tcontrol\u0001\u0085審査"
  private val _revision_fields = "participant = review-capability\noutcome = NEEDS_REVISION\nrole = review\nfrom = Reviewing\nto = Revising\non = needsRevision"
  private val _input_fields = "action = ReviewChange\noutcome = NEEDS_INPUT\nrole = review\nfrom = Reviewing\nto = AwaitingInput\non = needsInput"
  private val _rejected_fields = "action = ReviewChange\noutcome = REJECTED\nrole = review\nfrom = Reviewing\nto = Rejected\non = rejected"
  private val _record_fields = Vector("identity", "version", "source", "states", "actions", "requiredSpi", "invocationPolicies",
    "lifecyclePolicy", "waits", "failurePolicies", "executionSafetyPolicy", "semanticOutcomes")

  "Workflow semantic outcome generated ABI" should {
    "project actual public producer inputs" which {
      "retain all four complete v5 products across repeated normal and value routes" in {
        Given("the accepted typed ReviewWorkflow source with three explicit outcomes and independent older policies")
        val source = Files.readString(_fixture_path)
        val workflow = _workflow(source)
        val member = "ReviewWorkflowStateMachineWorkflow1"
        val paths = _paths(member)
        val outputs = Vector("modeler-scala" -> _base.resolve("target/test-generated/workflow-semantic-outcome-normal"),
          "modeler-scala-value" -> _base.resolve("target/test-generated/workflow-semantic-outcome-value"))

        When("each public route generates twice and its actual sources receive a typed consumer probe")
        val products = outputs.flatMap { case (command, out) =>
          _run(command, _fixture_path, out)
          val first = _product_bytes(out, paths)
          _run(command, _fixture_path, out)
          val second = _product_bytes(out, paths)
          _write(out.resolve(s"${_root}/WorkflowSemanticOutcomeAbiConsumerProbe.scala"), _consumer_probe(Vector(member)))
          Vector(first, second)
        }
        val strings = _products(outputs.head._2, paths)
        val realm = StateMachineWorkflowAbiGenerator.generate(Vector(workflow))
        val canonical = StateMachineWorkflowAbiGenerator.canonicalJson(Vector(workflow))
        val json = Json.parse(canonical).as[JsObject]
        val record = (json \ "workflows").as[Vector[JsObject]].head

        Then("routes, repetition and canonical projection agree byte for byte with complete ordered semantic data")
        products.foreach(_ shouldBe products.head)
        paths.filterNot(_ == StateMachineWorkflowAbiGenerator.metadataPath).foreach(path => strings(path) shouldBe _realm_string(realm, path))
        strings(StateMachineWorkflowAbiGenerator.metadataPath) shouldBe canonical
        json.fields.map(_._1).toVector shouldBe Vector("schemaVersion", "workflows")
        json shouldBe Json.obj("schemaVersion" -> _schema, "workflows" -> Vector(_expected_record(workflow)))
        _expect_projection(record, workflow, strings(paths(2)))
        _expect_shared(strings(_abi_path))
        strings(_bootstrap_path) shouldBe _expected_bootstrap(Vector(member))
        workflow.semanticOutcomes shouldBe Vector(
          WorkflowSemanticOutcomeDeclaration("revision-needed", WorkflowInvocationTarget.Participant("review-capability"), "ReviewChange",
            WorkflowSemanticOutcome.NeedsRevision, WorkflowSemanticOutcomeTransition("review", "ReviewLifecycle", "Reviewing", "Revising", "needsRevision"),
            WorkflowSourceIdentity(Some(_line(source, "#### revision-needed")))),
          WorkflowSemanticOutcomeDeclaration("input-needed", WorkflowInvocationTarget.Action("ReviewChange"), "ReviewChange",
            WorkflowSemanticOutcome.NeedsInput, WorkflowSemanticOutcomeTransition("review", "ReviewLifecycle", "Reviewing", "AwaitingInput", "needsInput"),
            WorkflowSourceIdentity(Some(_line(source, "#### input-needed")))),
          WorkflowSemanticOutcomeDeclaration("review-rejected", WorkflowInvocationTarget.Action("ReviewChange"), "ReviewChange",
            WorkflowSemanticOutcome.Rejected, WorkflowSemanticOutcomeTransition("review", "ReviewLifecycle", "Reviewing", "Rejected", "rejected"),
            WorkflowSourceIdentity(Some(_line(source, "#### review-rejected")))))
        workflow.invocationPolicies.head.retry shouldBe Some(WorkflowRetryPolicy(3, 250L))
        workflow.invocationPolicies.head.executionTimeoutMillis shouldBe Some(30000L)
        workflow.lifecyclePolicy.get.deadlineMillis shouldBe Some(60000L)
        workflow.lifecyclePolicy.get.cancellation shouldBe Some(WorkflowCancellationMode.Cooperative)
        workflow.waits.head.trigger shouldBe WorkflowWaitTrigger.Signal("review.input")
        workflow.failurePolicies.head.defaultClassification shouldBe WorkflowFailureClassification.Permanent
        workflow.failurePolicies.head.rules.head.classification shouldBe WorkflowFailureClassification.Retryable
        workflow.executionSafetyPolicy.get.duplicateProtection shouldBe WorkflowDuplicateProtection.NotRequired
        workflow.compositeStateMachine.actions.head.metadata.get.idempotency shouldBe CompositeStateMachineIdempotency.NotRequired
        StateMachineWorkflowAbiGenerator.semanticOutcomeSchemaVersion shouldBe _schema
        StateMachineWorkflowAbiGenerator.semanticOutcomeBootstrapSchemaVersion shouldBe _bootstrap_schema
        StateMachineWorkflowAbiGenerator.executionSafetySchemaVersion shouldBe "cozy.cml.statemachine-workflow-abi.v4"
        StateMachineWorkflowAbiGenerator.executionSafetyBootstrapSchemaVersion shouldBe "cozy.cml.statemachine-workflow-bootstrap.v4"
      }

      "preserve the four independently retained original v4 byte vectors on both repeated routes" in {
        Given("the actual original safety fixture and immutable outputs captured before any Phase69 edit")
        val input = _base.resolve("src/test/resources/modeler/workflow-failure-execution-safety-policy.cml")
        val paths = _paths("SafetyWorkflowStateMachineWorkflow1")
        val expected = paths.map(path => path -> Files.readAllBytes(_baseline.resolve(Paths.get(path).getFileName)).toVector).toMap
        val outputs = Vector("modeler-scala", "modeler-scala-value").map(command =>
          command -> _base.resolve(s"target/test-generated/workflow-semantic-outcome-v4-$command"))

        When("each public route projects the unchanged original source twice")
        val products = outputs.flatMap { case (command, out) =>
          _run(command, input, out)
          val first = _product_bytes(out, paths)
          _run(command, input, out)
          Vector(first, _product_bytes(out, paths))
        }

        Then("all actual bytes equal the independent resources without regenerating expectations")
        products.foreach(_ shouldBe expected)
        products.distinct.size shouldBe 1
      }
    }

    "select schemas without manufacturing metadata" which {
      "retain every absent-outcome tier and select v5 from any actual declaration" in {
        Given("actual v1-v4 fixtures, empty input, accepted source minus only outcomes and outcome-only real CML")
        val absent = Vector("workflow-invocation-policy-legacy.cml", "workflow-invocation-policy.cml",
          "workflow-lifecycle-policy.cml", "workflow-failure-execution-safety-policy.cml").zipWithIndex.map { case (name, index) =>
          (Vector(_workflow(_resource(name))), index + 1)
        } ++ Vector(Vector.empty[WorkflowDefinition] -> 1, Vector(_workflow(_source())) -> 4)
        val outcomeonly = _workflow(_outcome_only_source)
        val selected = Vector(_workflow(_resource("workflow-semantic-outcome.cml")), outcomeonly)

        When("each collection selects its shared Scala, descriptor, sidecar and bootstrap tier")
        val old = absent.map { case (workflows, tier) =>
          (workflows, tier, StateMachineWorkflowAbiGenerator.generate(workflows),
            Json.parse(StateMachineWorkflowAbiGenerator.canonicalJson(workflows)).as[JsObject])
        }
        val current = selected.map(workflow => (workflow, StateMachineWorkflowAbiGenerator.generate(Vector(workflow)), _record(workflow)))

        Then("absence keeps the old field and type shape while outcomes select all v5 declaration tiers")
        old.foreach { case (workflows, tier, realm, json) =>
          val schema = s"cozy.cml.statemachine-workflow-abi.v$tier"
          val abi = _realm_string(realm, _abi_path)
          (json \ "schemaVersion").as[String] shouldBe schema
          abi should include(s"val VERSION: String = ${_literal(schema)}")
          abi should not include "SemanticOutcome"
          abi should not include "semanticOutcomes"
          _realm_string(realm, _bootstrap_path) should include(s"cozy.cml.statemachine-workflow-bootstrap.v$tier")
          val fields = _record_fields.take(if (tier == 1) 6 else if (tier == 2) 7 else if (tier == 3) 9 else 11)
          (json \ "workflows").as[Vector[JsObject]].foreach(_.fields.map(_._1).toVector shouldBe fields)
          workflows.zipWithIndex.foreach { case (workflow, index) =>
            val descriptor = _realm_string(realm, s"${_root}/${workflow.identity}StateMachineWorkflow${index + 1}.scala")
            descriptor should include(s"schemaVersion = ${_literal(schema)}")
            descriptor should not include "semanticOutcomes"
          }
          (abi.contains("InvocationPolicy"), abi.contains("LifecyclePolicy"), abi.contains("ExecutionSafetyPolicy")) shouldBe
            ((tier >= 2, tier >= 3, tier >= 4))
        }
        current.foreach { case (workflow, realm, record) =>
          _expect_shared(_realm_string(realm, _abi_path))
          _expect_projection(record, workflow, _realm_string(realm, s"${_root}/${workflow.identity}StateMachineWorkflow1.scala"))
          _realm_string(realm, _bootstrap_path) shouldBe _expected_bootstrap(Vector(s"${workflow.identity}StateMachineWorkflow1"))
        }
        val only = current.last._3
        Vector("invocationPolicies", "waits", "failurePolicies").foreach(field => only.value(field) shouldBe Json.arr())
        Vector("lifecyclePolicy", "executionSafetyPolicy").foreach(field => only.value(field) shouldBe JsNull)
      }

      "preserve all seven nonempty outcome subsets in selected source order without retry" in {
        Given("every nonempty ordered subset of the three accepted outcome entries without invocation policy")
        val fields = Vector(_revision_fields, _input_fields, _rejected_fields)
        val selections = (1 to 7).toVector.map(mask => fields.indices.filter(index => (mask & (1 << index)) != 0).toVector)
        val sources = selections.map(indices => _without_invocation(_source(_section(indices.map(index => _entry(s"entry-$index", fields(index))): _*))))

        When("each subset is admitted from real CML and projected into Scala and decoded JSON")
        val projections = sources.map(source => {
          val workflow = _workflow(source)
          (workflow, _record(workflow), _descriptor(workflow))
        })

        Then("single values and every combination retain their declared order and do not require a retry policy")
        projections.zip(selections).foreach { case ((workflow, record, descriptor), indices) =>
          workflow.semanticOutcomes.map(_.outcome.canonicalValue) shouldBe indices.map(Vector("NEEDS_REVISION", "NEEDS_INPUT", "REJECTED"))
          workflow.invocationPolicies shouldBe Vector.empty
          _expect_projection(record, workflow, descriptor)
          descriptor should include("invocationPolicies = Vector.empty")
        }
      }
    }

    "retain canonical target and ordinary edge evidence" which {
      "preserve declared Action and capability aliases independently of canonical Action identity" in {
        Given("real outcome source variants with Action and required-capability aliases")
        val targets = Vector("action = REVIEW_change" -> WorkflowInvocationTarget.Action("REVIEW_change"),
          "participant = REVIEW_CAPABILITY" -> WorkflowInvocationTarget.Participant("REVIEW_CAPABILITY"))
        val sources = targets.map { case (target, _) => _source(_section(_entry("only", _input_fields.replace("action = ReviewChange", target)))) }

        When("existing source normalization resolves aliases and the ABI projects each admitted declaration")
        val projections = sources.map(source => { val workflow = _workflow(source); (workflow, _record(workflow), _descriptor(workflow)) })

        Then("Scala and JSON retain the declared kind and spelling alongside canonical ReviewChange")
        projections.zip(targets).foreach { case ((workflow, record, descriptor), (_, target)) =>
          workflow.semanticOutcomes.head.target shouldBe target
          workflow.semanticOutcomes.head.actionIdentity shouldBe "ReviewChange"
          _expect_projection(record, workflow, descriptor)
        }
      }

      "retain multiple eligible targets sharing one outcome and an actual Judgment target" in {
        Given("two real Actions sharing NEEDS_INPUT and a Judgment declaration on the existing admission fixture")
        val multiple = _source(_section(_entry("review-input", _input_fields),
          _entry("supply-input", _input_fields.replace("action = ReviewChange", "participant = input-capability"))))
        val judgment = _resource("candidate-admission-workflow.cml").replace("# STATE-MACHINE",
          _section(_entry("judgment-input", "action = judge-payment\noutcome = NEEDS_INPUT\nrole = payment\nfrom = Awaiting\nto = Paid\non = captured")) + "# STATE-MACHINE")
        val unchanged = _workflow(_resource("candidate-admission-workflow.cml"))

        When("real source admission and canonical projection retain each target's own declaration")
        val values = Vector(multiple, judgment).map(source => { val workflow = _workflow(source); (workflow, _record(workflow), _descriptor(workflow)) })

        Then("shared outcomes keep source order and Judgment metadata leaves deterministic Admission unchanged")
        values.head._1.semanticOutcomes.map(_.identity) shouldBe Vector("review-input", "supply-input")
        values.head._1.semanticOutcomes.map(_.actionIdentity) shouldBe Vector("ReviewChange", "SupplyInput")
        values.head._1.semanticOutcomes.map(_.outcome) shouldBe Vector.fill(2)(WorkflowSemanticOutcome.NeedsInput)
        values.last._1.semanticOutcomes.head.actionIdentity shouldBe "judge-payment"
        values.last._1.compositeStateMachine.actions shouldBe unchanged.compositeStateMachine.actions
        values.last._1.compositeStateMachine.actions.last.candidateAdmission.get shouldBe a[CompositeStateMachineAdmissionAction]
        values.foreach { case (workflow, record, descriptor) => _expect_projection(record, workflow, descriptor) }
      }

      "retain actual Entity-qualified machine references and explicitly authored self-loops" in {
        Given("an Entity-contained ReviewLifecycle and a valid revision edge whose from and to are equal")
        val qualified = _entity_source(_resource("workflow-semantic-outcome.cml"))
        val selfloop = _source(_section(_entry("revision-loop", _revision_fields)))
          .replace("to = Revising\non = needsRevision", "to = Reviewing\non = needsRevision")

        When("real model resolution admits each graph and projection copies the immutable edge")
        val values = Vector(qualified, selfloop).map(source => { val workflow = _workflow(source); (workflow, _record(workflow), _descriptor(workflow)) })

        Then("qualification and canonical edge names survive without terminality or Action-placement inference")
        values.head._1.semanticOutcomes.map(_.transition.stateMachine).distinct shouldBe Vector("ReviewRecord.ReviewLifecycle")
        values.last._1.semanticOutcomes.head.transition shouldBe WorkflowSemanticOutcomeTransition("review", "ReviewLifecycle", "Reviewing", "Reviewing", "needsRevision")
        values.last._1.compositeStateMachine.constituentActions shouldBe Vector.empty
        values.foreach { case (workflow, record, descriptor) => _expect_projection(record, workflow, descriptor) }
      }
    }

    "preserve independent JSON and Scala scalar meanings" which {
      "emit complete ordered outcome shapes and exact literals including nullable source lines" in {
        Given("real accepted input metadata copied with a missing outcome source line")
        val original = _workflow(_resource("workflow-semantic-outcome.cml"))
        val declaration = original.semanticOutcomes(1).copy(source = WorkflowSourceIdentity(None))
        val workflow = original.copy(semanticOutcomes = Vector(declaration))
        val expected = Json.obj("identity" -> "input-needed", "target" -> Json.obj("kind" -> "ACTION", "identity" -> "ReviewChange"),
          "actionIdentity" -> "ReviewChange", "outcome" -> "NEEDS_INPUT",
          "transition" -> Json.obj("role" -> "review", "stateMachine" -> "ReviewLifecycle", "from" -> "Reviewing", "to" -> "AwaitingInput", "on" -> "needsInput"),
          "source" -> Json.obj("line" -> JsNull))
        val literal = "StateMachineWorkflowAbi.SemanticOutcomeDeclaration(\"input-needed\", StateMachineWorkflowAbi.InvocationTarget.Action(\"ReviewChange\"), \"ReviewChange\", StateMachineWorkflowAbi.SemanticOutcome.NeedsInput, StateMachineWorkflowAbi.SemanticOutcomeTransition(\"review\", \"ReviewLifecycle\", \"Reviewing\", \"AwaitingInput\", \"needsInput\"), StateMachineWorkflowAbi.SourceIdentity(None))"

        When("canonical JSON is independently decoded and the actual Scala descriptor is projected")
        val record = _record(workflow)
        val descriptor = _descriptor(workflow)
        val projected = (record \ "semanticOutcomes").as[Vector[JsObject]].head

        Then("all field names, order, values and exact Scala literals follow the public contract")
        projected shouldBe expected
        _expect_outcome_order(projected)
        descriptor should include(s"semanticOutcomes = Vector($literal)")
        _expect_projection(record, workflow, descriptor)
      }

      "retain every escaped new string field, source null and inherited Long bounds in an actual compiler input" in {
        Given("a bounded copy of accepted real IR with opaque controls, Unicode, nullable sources and exact old scalars")
        val workflow = _edge_workflow(_workflow(_resource("workflow-semantic-outcome.cml")))
        val out = _base.resolve("target/test-generated/workflow-semantic-outcome-compile")

        When("the actual edge descriptor and JSON are generated, decoded and written for the mixed compiler group")
        val realm = StateMachineWorkflowAbiGenerator.generate(Vector.fill(6)(_workflow(_resource("workflow-semantic-outcome.cml"))) :+ workflow)
        val descriptor = _realm_string(realm, s"${_root}/OutcomeEdgeWorkflowStateMachineWorkflow7.scala")
        val json = StateMachineWorkflowAbiGenerator.canonicalJson(Vector(workflow))
        val record = (Json.parse(json) \ "workflows").as[Vector[JsObject]].head
        _write(out.resolve(s"${_root}/OutcomeEdgeWorkflowStateMachineWorkflow7.scala"), descriptor)

        Then("every new string retains its exact opaque value and inherited policy numbers remain lossless")
        _expect_projection(record, workflow, descriptor)
        val projected = (record \ "semanticOutcomes").as[Vector[JsObject]].head
        (projected \ "identity").as[String] shouldBe _escaped + "identity"
        (projected \ "target" \ "identity").as[String] shouldBe _escaped + "target"
        (projected \ "actionIdentity").as[String] shouldBe _escaped + "action"
        Vector("role", "stateMachine", "from", "to", "on").foreach(field => (projected \ "transition" \ field).as[String] shouldBe _escaped + field)
        (projected \ "source" \ "line").get shouldBe JsNull
        descriptor should include("\"quote\\\"slash\\\\line\\nreturn\\rtab\\tcontrol\\u0001\\u0085審査identity\"")
        descriptor should include("StateMachineWorkflowAbi.SourceIdentity(None)")
        descriptor should include("StateMachineWorkflowAbi.RetryPolicy(2147483647, 9223372036854775807L)")
        descriptor should include("StateMachineWorkflowAbi.RetryPolicy(3, 0L)")
        (record \ "invocationPolicies").as[Vector[JsObject]].map(value => (value \ "retry" \ "fixedRetryDelayMillis").get) shouldBe
          Vector(JsNumber(BigDecimal(Long.MaxValue)), JsNumber(BigDecimal(0)))
        (record \ "lifecyclePolicy" \ "deadlineMillis").get shouldBe JsNumber(BigDecimal(Long.MaxValue))
        (record \ "executionSafetyPolicy").get shouldBe JsNull
        (record \ "waits").get shouldBe Json.arr()
        (record \ "failurePolicies").get shouldBe Json.arr()
      }

      "sample nonempty opaque strings across the closed outcomes and both declared target kinds" in {
        Given("accepted immutable IR and nonempty strings containing controls, quotes, slashes and Unicode")
        val original = _workflow(_resource("workflow-semantic-outcome.cml"))
        val strings = Gen.nonEmptyListOf(Gen.oneOf('a', 'Z', ' ', '"', '\\', '\n', '\r', '\t', '\u0001', '\u0085', '審')).map(_.mkString)
        val outcomes = Gen.oneOf[WorkflowSemanticOutcome](WorkflowSemanticOutcome.NeedsInput, WorkflowSemanticOutcome.NeedsRevision, WorkflowSemanticOutcome.Rejected)
        val targets = Gen.oneOf(true, false)

        When("at least forty in-memory samples are decoded independently and Scala projection is repeated")
        val property = Prop.forAll(strings, outcomes, targets) { (opaque, outcome, participant) =>
          val target = if (participant) WorkflowInvocationTarget.Participant(opaque) else WorkflowInvocationTarget.Action(opaque)
          val declaration = original.semanticOutcomes.head.copy(identity = opaque, target = target, actionIdentity = opaque,
            outcome = outcome, transition = WorkflowSemanticOutcomeTransition(opaque, opaque, opaque, opaque, opaque), source = WorkflowSourceIdentity(None))
          val workflow = original.copy(semanticOutcomes = Vector(declaration))
          val record = _record(workflow)
          val first = _descriptor(workflow)
          val second = _descriptor(workflow)
          record == _expected_record(workflow) && first == second && first.contains(_outcome_literal(declaration)) &&
            (record \ "semanticOutcomes").as[Vector[JsObject]].head == _outcome_json(declaration)
        }
        val result = Test.check(Test.Parameters.default.withMinSuccessfulTests(60), property)

        Then("all sampled opaque fields and alternatives preserve decoded values and deterministic exact Scala literals")
        result.passed shouldBe true
        result.succeeded should be >= 40
      }
    }

    "project a shared ordered collection and actual typed consumers" which {
      "save the exact seven-member mixed v5 group with complete old records and empty outcomes" in {
        Given("actual v1-v4 source members, accepted ReviewWorkflow, real outcome-only source and the immutable edge copy")
        val legacy = _workflow(_resource("workflow-invocation-policy-legacy.cml")).copy(identity = "LegacyWorkflow")
        val policy = _workflow(_resource("workflow-invocation-policy.cml")).copy(identity = "PolicyWorkflow")
        val lifecycle = _workflow(_resource("workflow-lifecycle-policy.cml")).copy(identity = "LifecycleWorkflow")
        val safety = _workflow(_resource("workflow-failure-execution-safety-policy.cml"))
        val review = _workflow(_resource("workflow-semantic-outcome.cml"))
        val only = _workflow(_outcome_only_source)
        val edge = _edge_workflow(review)
        val workflows = Vector(legacy, policy, lifecycle, safety, review, only, edge)
        val names = Vector("LegacyWorkflow", "PolicyWorkflow", "LifecycleWorkflow", "SafetyWorkflow", "ReviewWorkflow", "OutcomeOnlyWorkflow", "OutcomeEdgeWorkflow")
        val objects = names.zipWithIndex.map { case (name, index) => s"${name}StateMachineWorkflow${index + 1}" }
        val paths = Vector(_abi_path, _bootstrap_path) ++ objects.map(name => s"${_root}/$name.scala")
        val out = _base.resolve("target/test-generated/workflow-semantic-outcome-compile")

        When("the real collection generates twice and its Realm sources, canonical JSON and typed probe are saved")
        val first = StateMachineWorkflowAbiGenerator.generate(workflows)
        val second = StateMachineWorkflowAbiGenerator.generate(workflows)
        val json = StateMachineWorkflowAbiGenerator.canonicalJson(workflows)
        val repeated = StateMachineWorkflowAbiGenerator.canonicalJson(workflows)
        val decoded = Json.parse(json).as[JsObject]
        val records = (decoded \ "workflows").as[Vector[JsObject]]
        val oldrecords = workflows.take(4).map(workflow => _record(workflow))
        paths.foreach(path => _write(out.resolve(path), _realm_string(first, path)))
        _write(out.resolve(StateMachineWorkflowAbiGenerator.metadataPath), json)
        _write(out.resolve(s"${_root}/WorkflowSemanticOutcomeAbiConsumerProbe.scala"), _consumer_probe(objects))

        Then("every member uses one v5 schema with deterministic order, complete inherited data and exact declared outcomes")
        json shouldBe repeated
        decoded shouldBe Json.obj("schemaVersion" -> _schema, "workflows" -> workflows.map(_expected_record))
        paths.foreach(path => _realm_string(first, path) shouldBe _realm_string(second, path))
        _expect_shared(_realm_string(first, _abi_path))
        _realm_string(first, _bootstrap_path) shouldBe _expected_bootstrap(objects)
        records.map(value => (value \ "identity").as[String]) shouldBe names
        records.zip(workflows).zip(objects).foreach { case ((record, workflow), objectname) =>
          _expect_projection(record, workflow, _realm_string(first, s"${_root}/$objectname.scala"))
        }
        records.take(4).zip(oldrecords).foreach { case (current, old) =>
          old.fields.foreach { case (field, value) => current.value(field) shouldBe value }
          (current \ "semanticOutcomes").get shouldBe Json.arr()
        }
        records.take(4).zip(objects).foreach { case (_, objectname) =>
          _realm_string(first, s"${_root}/$objectname.scala") should include("semanticOutcomes = Vector.empty")
        }
        Vector("invocationPolicies", "waits", "failurePolicies").foreach(field => records(5).value(field) shouldBe Json.arr())
        Vector("lifecyclePolicy", "executionSafetyPolicy").foreach(field => records(5).value(field) shouldBe JsNull)
      }

      "materialize typed Scala3 calls and readers against every actual generated output group" in {
        Given("the normal, value and seven-member group identities selected for separate actual Scala3 compilation")
        val groups = Vector(
          "workflow-semantic-outcome-normal" -> Vector("ReviewWorkflowStateMachineWorkflow1"),
          "workflow-semantic-outcome-value" -> Vector("ReviewWorkflowStateMachineWorkflow1"),
          "workflow-semantic-outcome-compile" -> Vector("LegacyWorkflowStateMachineWorkflow1", "PolicyWorkflowStateMachineWorkflow2",
            "LifecycleWorkflowStateMachineWorkflow3", "SafetyWorkflowStateMachineWorkflow4", "ReviewWorkflowStateMachineWorkflow5",
            "OutcomeOnlyWorkflowStateMachineWorkflow6", "OutcomeEdgeWorkflowStateMachineWorkflow7"))

        When("typed old and new construction, exhaustive pattern readers and direct descriptor/bootstrap consumers are authored")
        val probes = groups.map { case (group, objects) =>
          val probe = _consumer_probe(objects)
          _write(_base.resolve(s"target/test-generated/$group/${_root}/WorkflowSemanticOutcomeAbiConsumerProbe.scala"), probe)
          (objects, probe)
        }

        Then("each probe binds actual members and bootstrap and exercises all old positional and named labels plus v5 fields")
        probes.foreach { case (objects, probe) =>
          objects.foreach(objectname => probe should include(s"$objectname.workflow"))
          probe should include("StateMachineWorkflowComponentFactoryBootstrap.componentFactoryMetadata")
          (7 to 12).foreach(arity => probe should include(s"val positional$arity: WorkflowDescriptor"))
          probe should include("val positional13: WorkflowDescriptor")
          probe should include("val namedV4: WorkflowDescriptor")
          probe should include("val namedV5: WorkflowDescriptor")
          probe should include("case SemanticOutcome.NeedsInput")
          probe should include("case SemanticOutcome.NeedsRevision")
          probe should include("case SemanticOutcome.Rejected")
          probe should include("case InvocationTarget.Action")
          probe should include("case InvocationTarget.Participant")
          probe should include("value.transition.stateMachine")
          probe should include("value.source.line")
        }
      }
    }
  }

  private def _expect_projection(record: JsObject, workflow: WorkflowDefinition, descriptor: String): Unit = {
    record.fields.map(_._1).toVector shouldBe _record_fields
    record shouldBe _expected_record(workflow)
    descriptor should include(s"schemaVersion = ${_literal(_schema)}")
    descriptor should include(s"identity = ${_literal(workflow.identity)}")
    descriptor should include(s"version = ${_literal(workflow.version)}")
    descriptor should include(s"source = StateMachineWorkflowAbi.WorkflowSourceCorrelation(${_source_literal(workflow.source.root)}, ${_source_literal(workflow.source.definition)})")
    workflow.compositeStateMachine.states.foreach(state =>
      descriptor should include(s"StateMachineWorkflowAbi.State(${_literal(state.name)}, ${_source_literal(WorkflowSourceIdentity(state.source.line))})"))
    workflow.compositeStateMachine.actions.foreach { action =>
      val binding = action.inputBinding.map(value => s"Some(${_literal(value)})").getOrElse("None")
      descriptor should include(s"StateMachineWorkflowAbi.Action(${_literal(action.identity)}, ${_literal(action.kind)}, ${_operation_literal(action.operation, action.source.line)}, $binding, ${_source_literal(WorkflowSourceIdentity(action.source.line))})")
    }
    workflow.requiredOperations.foreach { required =>
      val input = required.action.operation.inputType.map(value => s"Some(StateMachineWorkflowAbi.StateMachineInputTypeReference(${_literal(value)}))").getOrElse("None")
      val result = required.action.operation.outputType.map(value => s"Some(StateMachineWorkflowAbi.StateMachineResultTypeReference(${_literal(value)}))").getOrElse("None")
      descriptor should include(s"StateMachineWorkflowAbi.WorkflowRequiredOperationDescriptor(StateMachineWorkflowAbi.StateMachineRequiredOperationIdentity(${_literal(required.capability)}), ${_literal(required.action.identity)}, StateMachineWorkflowAbi.StateMachineOperationIdentity(${_literal(required.action.operation.service)}, ${_literal(required.action.operation.name)}), $input, $result, ${_source_literal(required.source)}, ${_source_literal(WorkflowSourceIdentity(required.action.source.line))})")
    }
    descriptor should include("StateMachineWorkflowAbi.ComponentFactoryMetadata(workflow)")
    descriptor should include(s"semanticOutcomes = ${_vector_literal(workflow.semanticOutcomes.map(_outcome_literal))}")
    (record \ "semanticOutcomes").as[Vector[JsObject]].foreach(_expect_outcome_order)
    workflow.invocationPolicies.foreach { policy =>
      val retry = policy.retry.map(value => s"Some(StateMachineWorkflowAbi.RetryPolicy(${value.maximumAttempts}, ${value.fixedRetryDelayMillis}L))").getOrElse("None")
      val timeout = policy.executionTimeoutMillis.map(value => s"Some(${value}L)").getOrElse("None")
      descriptor should include(s"StateMachineWorkflowAbi.InvocationPolicy(${_literal(policy.identity)}, ${_target_literal(policy.target)}, ${_literal(policy.actionIdentity)}, $retry, $timeout, ${_source_literal(policy.source)})")
      policy.retry.foreach(retry => descriptor should include(s"StateMachineWorkflowAbi.RetryPolicy(${retry.maximumAttempts}, ${retry.fixedRetryDelayMillis}L)"))
      policy.executionTimeoutMillis.foreach(timeout => descriptor should include(s"Some(${timeout}L)"))
    }
    workflow.lifecyclePolicy.foreach { policy =>
      val deadline = policy.deadlineMillis.map(value => s"Some(${value}L)").getOrElse("None")
      val cancellation = policy.cancellation.map(_ => "Some(StateMachineWorkflowAbi.CancellationMode.Cooperative)").getOrElse("None")
      descriptor should include(s"StateMachineWorkflowAbi.LifecyclePolicy($deadline, $cancellation, ${_source_literal(policy.source)})")
    }
    workflow.waits.foreach { wait =>
      val trigger = wait.trigger match {
        case WorkflowWaitTrigger.Timer(delay) => s"StateMachineWorkflowAbi.WaitTrigger.Timer(${delay}L)"
        case WorkflowWaitTrigger.Signal(identity) => s"StateMachineWorkflowAbi.WaitTrigger.Signal(${_literal(identity)})"
      }
      descriptor should include(s"StateMachineWorkflowAbi.WaitDeclaration(${_literal(wait.identity)}, ${_literal(wait.state)}, ${_literal(wait.stateIdentity)}, $trigger, ${_source_literal(wait.source)})")
    }
    workflow.failurePolicies.foreach { policy =>
      val rules = policy.rules.map(rule => s"StateMachineWorkflowAbi.FailureRule(${_literal(rule.identity)}, ${_classification_literal(rule.classification)}, ${_source_literal(rule.source)})")
      descriptor should include(s"StateMachineWorkflowAbi.FailurePolicy(${_literal(policy.identity)}, ${_target_literal(policy.target)}, ${_literal(policy.actionIdentity)}, ${_classification_literal(policy.defaultClassification)}, ${_vector_literal(rules)}, ${_source_literal(policy.source)})")
    }
    workflow.executionSafetyPolicy.foreach { policy =>
      val protection = policy.duplicateProtection match {
        case WorkflowDuplicateProtection.NotRequired => "StateMachineWorkflowAbi.DuplicateProtection.NotRequired"
        case WorkflowDuplicateProtection.Required(key) => s"StateMachineWorkflowAbi.DuplicateProtection.Required(${_literal(key)})"
      }
      descriptor should include(s"StateMachineWorkflowAbi.ExecutionSafetyPolicy(${_literal(policy.logicalExecutionIdentityRef)}, $protection, ${_source_literal(policy.source)})")
    }
    if (workflow.invocationPolicies.isEmpty) descriptor should include("invocationPolicies = Vector.empty")
    if (workflow.lifecyclePolicy.isEmpty) descriptor should include("lifecyclePolicy = None")
    if (workflow.waits.isEmpty) descriptor should include("waits = Vector.empty")
    if (workflow.failurePolicies.isEmpty) descriptor should include("failurePolicies = Vector.empty")
    if (workflow.executionSafetyPolicy.isEmpty) descriptor should include("executionSafetyPolicy = None")
  }

  private def _expect_outcome_order(value: JsObject): Unit = {
    value.fields.map(_._1).toVector shouldBe Vector("identity", "target", "actionIdentity", "outcome", "transition", "source")
    (value \ "target").as[JsObject].fields.map(_._1).toVector shouldBe Vector("kind", "identity")
    (value \ "transition").as[JsObject].fields.map(_._1).toVector shouldBe Vector("role", "stateMachine", "from", "to", "on")
    (value \ "source").as[JsObject].fields.map(_._1).toVector shouldBe Vector("line")
  }

  private def _outcome_json(value: WorkflowSemanticOutcomeDeclaration): JsObject = Json.obj(
    "identity" -> value.identity, "target" -> _target_json(value.target), "actionIdentity" -> value.actionIdentity,
    "outcome" -> _outcome_token(value.outcome), "transition" -> Json.obj("role" -> value.transition.role,
      "stateMachine" -> value.transition.stateMachine, "from" -> value.transition.from, "to" -> value.transition.to, "on" -> value.transition.on),
    "source" -> _source_json(value.source))

  private def _outcome_token(value: WorkflowSemanticOutcome): String = value match {
    case WorkflowSemanticOutcome.NeedsInput => "NEEDS_INPUT"
    case WorkflowSemanticOutcome.NeedsRevision => "NEEDS_REVISION"
    case WorkflowSemanticOutcome.Rejected => "REJECTED"
  }

  private def _outcome_literal(value: WorkflowSemanticOutcomeDeclaration): String = {
    val alternative = value.outcome match {
      case WorkflowSemanticOutcome.NeedsInput => "NeedsInput"
      case WorkflowSemanticOutcome.NeedsRevision => "NeedsRevision"
      case WorkflowSemanticOutcome.Rejected => "Rejected"
    }
    val edge = value.transition
    val transition = s"StateMachineWorkflowAbi.SemanticOutcomeTransition(${_literal(edge.role)}, ${_literal(edge.stateMachine)}, ${_literal(edge.from)}, ${_literal(edge.to)}, ${_literal(edge.on)})"
    s"StateMachineWorkflowAbi.SemanticOutcomeDeclaration(${_literal(value.identity)}, ${_target_literal(value.target)}, ${_literal(value.actionIdentity)}, StateMachineWorkflowAbi.SemanticOutcome.$alternative, $transition, ${_source_literal(value.source)})"
  }

  private def _expect_shared(source: String): Unit = {
    source should include(s"val VERSION: String = ${_literal(_schema)}")
    source should include("enum SemanticOutcome(val canonicalValue: String)")
    Vector("NeedsInput" -> "NEEDS_INPUT", "NeedsRevision" -> "NEEDS_REVISION", "Rejected" -> "REJECTED").foreach { case (name, token) =>
      source should include(s"case $name extends SemanticOutcome(${_literal(token)})")
    }
    source should include("final case class SemanticOutcomeTransition(role: String, stateMachine: String, from: String, to: String, on: String)")
    source should include("final case class SemanticOutcomeDeclaration(identity: String, target: InvocationTarget, actionIdentity: String, outcome: SemanticOutcome, transition: SemanticOutcomeTransition, source: SourceIdentity)")
    source should include("executionSafetyPolicy: Option[ExecutionSafetyPolicy] = None, semanticOutcomes: Vector[SemanticOutcomeDeclaration] = Vector.empty")
    Vector("InvocationPolicy", "LifecyclePolicy", "WaitDeclaration", "FailurePolicy", "ExecutionSafetyPolicy").foreach(name => source should include(name))
    source.indexOf("final case class ExecutionSafetyPolicy") should be < source.indexOf("enum SemanticOutcome")
    source.indexOf("enum SemanticOutcome") should be < source.indexOf("final case class WorkflowDescriptor")
    source should include("final case class Completed(result: StateMachineOperationResult) extends ActionExecution")
    source should include("final case class Suspended(continuation: Continuation) extends ActionExecution")
    source should include("final case class Failed(failure: StateMachineOperationFailure) extends ActionExecution")
  }

  private def _edge_workflow(original: WorkflowDefinition): WorkflowDefinition = {
    val absent = WorkflowSourceIdentity(None)
    val invocation = original.invocationPolicies.head
    val declaration = original.semanticOutcomes.head.copy(identity = _escaped + "identity", target = WorkflowInvocationTarget.Participant(_escaped + "target"),
      actionIdentity = _escaped + "action", transition = WorkflowSemanticOutcomeTransition(_escaped + "role", _escaped + "stateMachine",
        _escaped + "from", _escaped + "to", _escaped + "on"), source = absent)
    original.copy(identity = "OutcomeEdgeWorkflow", source = WorkflowSourceCorrelation(absent, absent), semanticOutcomes = Vector(declaration),
      invocationPolicies = Vector(invocation.copy(retry = Some(WorkflowRetryPolicy(Int.MaxValue, Long.MaxValue)), executionTimeoutMillis = Some(Long.MaxValue), source = absent),
        invocation.copy(identity = "zero-delay", retry = Some(WorkflowRetryPolicy(3, 0L)), executionTimeoutMillis = None, source = absent)),
      lifecyclePolicy = Some(WorkflowLifecyclePolicy(Some(Long.MaxValue), None, absent)), waits = Vector.empty,
      failurePolicies = Vector.empty, executionSafetyPolicy = None)
  }

  private def _source(metadata: String = ""): String = {
    val source = _resource("workflow-semantic-outcome.cml")
    val start = source.indexOf("### SEMANTIC-OUTCOME")
    val end = source.indexOf("# STATE-MACHINE", start)
    source.substring(0, start) + metadata + "\n" + source.substring(end)
  }

  private def _outcome_only_source: String = {
    val source = _source(_section(_entry("only-input", _input_fields)))
    val start = source.indexOf("### INVOCATION-POLICY")
    val end = source.indexOf("### SEMANTIC-OUTCOME", start)
    (source.substring(0, start) + source.substring(end)).replace("## ReviewWorkflow", "## OutcomeOnlyWorkflow")
  }

  private def _without_invocation(source: String): String = {
    val start = source.indexOf("### INVOCATION-POLICY")
    val end = source.indexOf("### LIFECYCLE", start)
    source.substring(0, start) + source.substring(end)
  }

  private def _entity_source(source: String): String = {
    val start = source.indexOf("# STATE-MACHINE")
    val end = source.indexOf("# SERVICE", start)
    val body = source.substring(start, end).linesIterator.drop(1).map(line => if (line.startsWith("#")) "##" + line else line).mkString("\n")
    val entity = "# ENTITY\n\n## ReviewRecord\n\n### Attribute\n| name | type | multiplicity |\n|------+------|--------------|\n| id | entityid | 1 |\n\n### StateMachine\n" + body + "\n\n"
    (source.substring(0, start) + entity + source.substring(end)).replace("state-machine = ReviewLifecycle", "state-machine = ReviewRecord.ReviewLifecycle")
  }

  private def _section(entries: String*): String = "### SEMANTIC-OUTCOME\n\n" + entries.mkString("\n") + "\n"
  private def _entry(identity: String, fields: String): String = s"#### $identity\n$fields\n\n"
  private def _resource(name: String): String = Files.readString(_base.resolve(s"src/test/resources/modeler/$name"))
  private def _workflow(source: String): WorkflowDefinition = {
    val model = KaleidoxModel.parse(KaleidoxConfig.default, source)
    if (model.errors.nonEmpty) throw new IllegalArgumentException(s"CML fixture must parse without errors: ${model.errors.mkString(" | ")}")
    Modeler.ModelBuilder(model)
    CompositeStateMachineCml.workflowDefinitions(model).head
  }
  private def _record(workflow: WorkflowDefinition): JsObject =
    (Json.parse(StateMachineWorkflowAbiGenerator.canonicalJson(Vector(workflow))) \ "workflows").as[Vector[JsObject]].head
  private def _descriptor(workflow: WorkflowDefinition): String =
    _realm_string(StateMachineWorkflowAbiGenerator.generate(Vector(workflow)), s"${_root}/${workflow.identity}StateMachineWorkflow1.scala")
  private def _paths(member: String): Vector[String] = Vector(_abi_path, _bootstrap_path, s"${_root}/$member.scala", StateMachineWorkflowAbiGenerator.metadataPath)
  private def _run(command: String, input: Path, out: Path): Unit = cozy.Cozy.main(Array(command, input.toString, "--save", out.toString))
  private def _products(out: Path, paths: Vector[String]): Map[String, String] = paths.map(path => path -> Files.readString(out.resolve(path))).toMap
  private def _product_bytes(out: Path, paths: Vector[String]): Map[String, Vector[Byte]] = paths.map(path => path -> Files.readAllBytes(out.resolve(path)).toVector).toMap
  private def _realm_string(realm: Realm, path: String): String =
    realm.get(path).collect { case value: Realm.StringData => value.string }.getOrElse(fail(s"Missing generated artifact: $path"))
  private def _write(path: Path, value: String): Unit = {
    Files.createDirectories(path.getParent)
    Files.writeString(path, value, UTF_8)
    ()
  }
  private def _line(source: String, heading: String): Int = source.linesIterator.toVector.indexOf(heading) + 1
  private def _source_json(source: WorkflowSourceIdentity): JsObject = _line_json(source.line)
  private def _line_json(line: Option[Int]): JsObject = Json.obj("line" -> line.map[JsValue](value => JsNumber(BigDecimal(value))).getOrElse[JsValue](JsNull))
  private def _source_literal(source: WorkflowSourceIdentity): String = s"StateMachineWorkflowAbi.SourceIdentity(${source.line.map(value => s"Some($value)").getOrElse("None")})"
  private def _string_json(value: Option[String]): JsValue = value.map[JsValue](Json.toJson(_)).getOrElse(JsNull)
  private def _long_json(value: Option[Long]): JsValue = value.map[JsValue](number => JsNumber(BigDecimal(number))).getOrElse(JsNull)
  private def _target_json(target: WorkflowInvocationTarget): JsObject = Json.obj("kind" -> (target match {
    case _: WorkflowInvocationTarget.Action => "ACTION"
    case _: WorkflowInvocationTarget.Participant => "PARTICIPANT"
  }), "identity" -> target.identity)
  private def _target_literal(target: WorkflowInvocationTarget): String = target match {
    case WorkflowInvocationTarget.Action(identity) => s"StateMachineWorkflowAbi.InvocationTarget.Action(${_literal(identity)})"
    case WorkflowInvocationTarget.Participant(identity) => s"StateMachineWorkflowAbi.InvocationTarget.Participant(${_literal(identity)})"
  }
  private def _classification_literal(value: WorkflowFailureClassification): String = value match {
    case WorkflowFailureClassification.Retryable => "StateMachineWorkflowAbi.FailureClassification.Retryable"
    case WorkflowFailureClassification.Permanent => "StateMachineWorkflowAbi.FailureClassification.Permanent"
  }
  private def _vector_literal(values: Vector[String]): String = if (values.isEmpty) "Vector.empty" else values.mkString("Vector(", ", ", ")")
  private def _literal(value: String): String = {
    val encoded = Json.stringify(Json.toJson(value))
    encoded.flatMap(character => if (Character.isISOControl(character)) f"\\u${character.toInt}%04x" else character.toString)
  }
  private def _expected_bootstrap(objects: Vector[String]): String =
    "package domain.statemachine.workflow\n\nobject StateMachineWorkflowComponentFactoryBootstrap {\n  val schemaVersion: String = " + _literal(_bootstrap_schema) +
      "\n  val componentFactoryMetadata: Vector[StateMachineWorkflowAbi.ComponentFactoryMetadata] = " +
      objects.map(name => s"$name.componentFactoryMetadata").mkString("Vector(", ", ", ")") + "\n}\n"

  private def _expected_old_record(workflow: WorkflowDefinition): JsObject = Json.obj(
    "identity" -> workflow.identity, "version" -> workflow.version,
    "source" -> Json.obj("root" -> _source_json(workflow.source.root), "definition" -> _source_json(workflow.source.definition)),
    "states" -> workflow.compositeStateMachine.states.map(state => Json.obj("name" -> state.name, "source" -> _line_json(state.source.line))),
    "actions" -> workflow.compositeStateMachine.actions.map(action => Json.obj("identity" -> action.identity, "kind" -> action.kind,
      "operation" -> _operation_json(action.operation, action.source.line), "inputBinding" -> _string_json(action.inputBinding), "source" -> _line_json(action.source.line))),
    "requiredSpi" -> workflow.requiredOperations.map(required => Json.obj("capability" -> required.capability,
      "actionIdentity" -> required.action.identity, "operation" -> _operation_json(required.action.operation, required.action.source.line), "source" -> _source_json(required.source))),
    "invocationPolicies" -> workflow.invocationPolicies.map(policy => Json.obj("identity" -> policy.identity, "target" -> _target_json(policy.target),
      "actionIdentity" -> policy.actionIdentity, "retry" -> policy.retry.map[JsValue](retry => Json.obj("maximumAttempts" -> retry.maximumAttempts,
        "fixedRetryDelayMillis" -> JsNumber(BigDecimal(retry.fixedRetryDelayMillis)))).getOrElse[JsValue](JsNull),
      "executionTimeoutMillis" -> _long_json(policy.executionTimeoutMillis), "source" -> _source_json(policy.source))),
    "lifecyclePolicy" -> workflow.lifecyclePolicy.map[JsValue](policy => Json.obj("deadlineMillis" -> _long_json(policy.deadlineMillis),
      "cancellation" -> _string_json(policy.cancellation.map(_.canonicalValue)), "source" -> _source_json(policy.source))).getOrElse[JsValue](JsNull),
    "waits" -> workflow.waits.map(wait => Json.obj("identity" -> wait.identity, "state" -> wait.state, "stateIdentity" -> wait.stateIdentity,
      "trigger" -> (wait.trigger match {
        case WorkflowWaitTrigger.Timer(delay) => Json.obj("kind" -> "TIMER", "delayMillis" -> JsNumber(BigDecimal(delay)))
        case WorkflowWaitTrigger.Signal(identity) => Json.obj("kind" -> "SIGNAL", "identity" -> identity)
      }), "source" -> _source_json(wait.source))),
    "failurePolicies" -> workflow.failurePolicies.map(policy => Json.obj("identity" -> policy.identity, "target" -> _target_json(policy.target),
      "actionIdentity" -> policy.actionIdentity, "defaultClassification" -> policy.defaultClassification.canonicalValue,
      "rules" -> policy.rules.map(rule => Json.obj("identity" -> rule.identity, "classification" -> rule.classification.canonicalValue, "source" -> _source_json(rule.source))),
      "source" -> _source_json(policy.source))),
    "executionSafetyPolicy" -> workflow.executionSafetyPolicy.map[JsValue](policy => Json.obj("logicalExecutionIdentityRef" -> policy.logicalExecutionIdentityRef,
      "duplicateProtection" -> (policy.duplicateProtection match {
        case WorkflowDuplicateProtection.NotRequired => Json.obj("kind" -> "NOT_REQUIRED", "idempotencyKeyRef" -> JsNull)
        case WorkflowDuplicateProtection.Required(key) => Json.obj("kind" -> "REQUIRED", "idempotencyKeyRef" -> key)
      }), "source" -> _source_json(policy.source))).getOrElse[JsValue](JsNull))

  private def _operation_json(operation: CompositeStateMachineOperation, line: Option[Int]): JsObject =
    Json.obj("service" -> operation.service, "name" -> operation.name, "inputType" -> _string_json(operation.inputType),
      "resultType" -> _string_json(operation.outputType), "source" -> _line_json(line))

  private def _operation_literal(operation: CompositeStateMachineOperation, line: Option[Int]): String = {
    val input = operation.inputType.map(value => s"Some(${_literal(value)})").getOrElse("None")
    val result = operation.outputType.map(value => s"Some(${_literal(value)})").getOrElse("None")
    s"StateMachineWorkflowAbi.Operation(${_literal(operation.service)}, ${_literal(operation.name)}, $input, $result, ${_source_literal(WorkflowSourceIdentity(line))})"
  }


  private def _expected_record(workflow: WorkflowDefinition): JsObject =
    _expected_old_record(workflow) ++ Json.obj("semanticOutcomes" -> workflow.semanticOutcomes.map(_outcome_json))

  private def _consumer_probe(objects: Vector[String]): String = s"""package domain.statemachine.workflow
    |
    |import StateMachineWorkflowAbi._
    |
    |object WorkflowSemanticOutcomeAbiConsumerProbe {
    |  val source: WorkflowSourceCorrelation = WorkflowSourceCorrelation(root = SourceIdentity(line = None), definition = SourceIdentity(line = Some(1)))
    |  val positional7: WorkflowDescriptor = WorkflowDescriptor("v1", "legacy", "v1", source, Vector.empty, Vector.empty, Vector.empty)
    |  val positional8: WorkflowDescriptor = WorkflowDescriptor("v2", "invocation", "v2", source, Vector.empty, Vector.empty, Vector.empty, Vector.empty)
    |  val positional9: WorkflowDescriptor = WorkflowDescriptor("v3", "deadline", "v3", source, Vector.empty, Vector.empty, Vector.empty, Vector.empty, None)
    |  val positional10: WorkflowDescriptor = WorkflowDescriptor("v3", "wait", "v3", source, Vector.empty, Vector.empty, Vector.empty, Vector.empty, None, Vector.empty)
    |  val positional11: WorkflowDescriptor = WorkflowDescriptor("v4", "failure", "v4", source, Vector.empty, Vector.empty, Vector.empty, Vector.empty, None, Vector.empty, Vector.empty)
    |  val positional12: WorkflowDescriptor = WorkflowDescriptor("v4", "safety", "v4", source, Vector.empty, Vector.empty, Vector.empty, Vector.empty, None, Vector.empty, Vector.empty, None)
    |  val namedV4: WorkflowDescriptor = WorkflowDescriptor(schemaVersion = "v4", identity = "named", version = "old", source = source, states = Vector.empty, actions = Vector.empty, requiredOperations = Vector.empty, invocationPolicies = Vector.empty, lifecyclePolicy = None, waits = Vector.empty, failurePolicies = Vector.empty, executionSafetyPolicy = None)
    |  val oldDefaults: Vector[Vector[SemanticOutcomeDeclaration]] = Vector(positional7, positional8, positional9, positional10, positional11, positional12, namedV4).map(_.semanticOutcomes)
    |  val oldDefaultsAreEmpty: Boolean = oldDefaults.forall(_.isEmpty)
    |  val transition: SemanticOutcomeTransition = SemanticOutcomeTransition(role = "review", stateMachine = "ReviewRecord.ReviewLifecycle", from = "Reviewing", to = "Revising", on = "needsRevision")
    |  val revision: SemanticOutcomeDeclaration = SemanticOutcomeDeclaration(identity = "revision", target = InvocationTarget.Participant(identity = "review-capability"), actionIdentity = "ReviewChange", outcome = SemanticOutcome.NeedsRevision, transition = transition, source = SourceIdentity(line = None))
    |  val input: SemanticOutcomeDeclaration = SemanticOutcomeDeclaration("input", InvocationTarget.Action("ReviewChange"), "ReviewChange", SemanticOutcome.NeedsInput, transition, SourceIdentity(Some(2)))
    |  val rejected: SemanticOutcomeDeclaration = SemanticOutcomeDeclaration("rejected", InvocationTarget.Action("ReviewChange"), "ReviewChange", SemanticOutcome.Rejected, transition, SourceIdentity(None))
    |  val positional13: WorkflowDescriptor = WorkflowDescriptor(VERSION, "new", "v5", source, Vector.empty, Vector.empty, Vector.empty, Vector.empty, None, Vector.empty, Vector.empty, None, Vector(revision, input, rejected))
    |  val namedV5: WorkflowDescriptor = WorkflowDescriptor(schemaVersion = VERSION, identity = "named-v5", version = "new", source = source, states = Vector.empty, actions = Vector.empty, requiredOperations = Vector.empty, invocationPolicies = Vector.empty, lifecyclePolicy = None, waits = Vector.empty, failurePolicies = Vector.empty, executionSafetyPolicy = None, semanticOutcomes = Vector(revision, input, rejected))
    |  val ordered: Vector[WorkflowDescriptor] = ${objects.map(name => s"$name.workflow").mkString("Vector(", ", ", ")")}
    |  val directMetadata: Vector[ComponentFactoryMetadata] = ${objects.map(name => s"$name.componentFactoryMetadata").mkString("Vector(", ", ", ")")}
    |  val bootstrap: Vector[ComponentFactoryMetadata] = StateMachineWorkflowComponentFactoryBootstrap.componentFactoryMetadata
    |  val bootstrapWorkflows: Vector[WorkflowDescriptor] = bootstrap.map(_.workflow)
    |  val bootstrapMatches: Boolean = bootstrap == directMetadata && bootstrapWorkflows == ordered
    |  val actualOutcomes: Vector[SemanticOutcomeDeclaration] = ordered.flatMap(_.semanticOutcomes)
    |  val actualIdentities: Vector[String] = actualOutcomes.map(_.identity)
    |  val actualActions: Vector[String] = actualOutcomes.map(_.actionIdentity)
    |  val actualCanonicalValues: Vector[String] = actualOutcomes.map(_.outcome.canonicalValue)
    |  val actualTargets: Vector[(String, String)] = actualOutcomes.map(value => targetData(value.target))
    |  val actualOutcomeValues: Vector[String] = actualOutcomes.map(value => outcomeData(value.outcome))
    |  val actualTransitions: Vector[(String, String, String, String, String)] = actualOutcomes.map(value => (value.transition.role, value.transition.stateMachine, value.transition.from, value.transition.to, value.transition.on))
    |  val actualSources: Vector[Option[Int]] = actualOutcomes.map(value => value.source.line)
    |  val sourceRoots: Vector[Option[Int]] = ordered.map(_.source.root.line)
    |  val sourceDefinitions: Vector[Option[Int]] = ordered.map(_.source.definition.line)
    |  val policies: Vector[InvocationPolicy] = ordered.flatMap(_.invocationPolicies)
    |  val retryDelays: Vector[Long] = policies.flatMap(_.retry).map(_.fixedRetryDelayMillis)
    |  val retryAttempts: Vector[Int] = policies.flatMap(_.retry).map(_.maximumAttempts)
    |  val timeouts: Vector[Long] = policies.flatMap(_.executionTimeoutMillis)
    |  val deadlines: Vector[Long] = ordered.flatMap(_.lifecyclePolicy).flatMap(_.deadlineMillis)
    |  val waits: Vector[WaitDeclaration] = ordered.flatMap(_.waits)
    |  val failures: Vector[FailurePolicy] = ordered.flatMap(_.failurePolicies)
    |  val safety: Vector[ExecutionSafetyPolicy] = ordered.flatMap(_.executionSafetyPolicy)
    |  val actualStates: Vector[State] = ordered.flatMap(_.states)
    |  val actualActionsData: Vector[Action] = ordered.flatMap(_.actions)
    |  val actualRequired: Vector[WorkflowRequiredOperationDescriptor] = ordered.flatMap(_.requiredOperations)
    |  def outcomeData(outcome: SemanticOutcome): String = outcome match {
    |    case SemanticOutcome.NeedsInput => "NEEDS_INPUT"
    |    case SemanticOutcome.NeedsRevision => "NEEDS_REVISION"
    |    case SemanticOutcome.Rejected => "REJECTED"
    |  }
    |  def targetData(target: InvocationTarget): (String, String) = target match {
    |    case InvocationTarget.Action(identity) => ("ACTION", identity)
    |    case InvocationTarget.Participant(identity) => ("PARTICIPANT", identity)
    |  }
    |}
    |""".stripMargin
}
