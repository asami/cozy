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
final class WorkflowExecutionSafetyAbiGenerationSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  private val _base = Paths.get(sys.props("user.dir")).toAbsolutePath.normalize()
  private val _root = "target/scala-3.3.8/src_managed/main/scala/domain/statemachine/workflow"
  private val _abi_path = s"${_root}/StateMachineWorkflowAbi.scala"
  private val _bootstrap_path = s"${_root}/StateMachineWorkflowComponentFactoryBootstrap.scala"
  private val _fixture_path = _base.resolve("src/test/resources/modeler/workflow-failure-execution-safety-policy.cml")
  private val _baseline = _base.resolve("src/test/resources/modeler/workflow-execution-safety-v3-baseline")
  private val _schema = "cozy.cml.statemachine-workflow-abi.v4"
  private val _bootstrap_schema = "cozy.cml.statemachine-workflow-bootstrap.v4"
  private val _escaped = "quote\"slash\\line\nreturn\rtab\tcontrol\u0001\u0085決済"
  private val _required = "### EXECUTION-SAFETY\n\nlogical-execution-identity-ref = order-run\nduplicate-protection = REQUIRED\nidempotency-key-ref = order-start\n\n"
  private val _not_required = "### EXECUTION-SAFETY\n\nlogical-execution-identity-ref = order-run\nduplicate-protection = NOT_REQUIRED\n\n"
  private val _default_only = "### FAILURE-POLICY\n\n#### capture-failure\n\naction = capture-payment\ndefault-classification = PERMANENT\n\n"

  "Workflow failure and execution safety generated ABI" should {
    "project actual public producer inputs" which {
      "preserve four complete v4 products across both repeated public routes and write typed consumers" in {
        Given("the accepted full safety CML with independent Action idempotency, invocation budget, lifecycle and waits")
        val source = Files.readString(_fixture_path)
        val workflow = _workflow(source)
        val paths = _paths("SafetyWorkflowStateMachineWorkflow1")
        val normalout = _base.resolve("target/test-generated/workflow-execution-safety-normal")
        val valueout = _base.resolve("target/test-generated/workflow-execution-safety-value")

        When("both public Scala entry points project the tracked source twice and author a consumer alongside their actual products")
        _run("modeler-scala", _fixture_path, normalout)
        val normalfirst = _products(normalout, paths)
        _run("modeler-scala", _fixture_path, normalout)
        val normalsecond = _products(normalout, paths)
        _run("modeler-scala-value", _fixture_path, valueout)
        val valuefirst = _products(valueout, paths)
        _run("modeler-scala-value", _fixture_path, valueout)
        val valuesecond = _products(valueout, paths)
        Vector(normalout, valueout).foreach(out =>
          _write(out.resolve(s"${_root}/WorkflowExecutionSafetyAbiConsumerProbe.scala"),
            _consumer_probe("SafetyWorkflowStateMachineWorkflow1", Vector("SafetyWorkflowStateMachineWorkflow1"))))
        val realm = StateMachineWorkflowAbiGenerator.generate(Vector(workflow))
        val canonical = StateMachineWorkflowAbiGenerator.canonicalJson(Vector(workflow))
        val json = Json.parse(canonical).as[JsObject]
        val record = (json \ "workflows").as[Vector[JsObject]].head

        Then("all four products are identical across routes, repetition and canonical projection, with ordered lossless metadata")
        normalfirst shouldBe normalsecond
        normalfirst shouldBe valuefirst
        valuefirst shouldBe valuesecond
        paths.filterNot(_ == StateMachineWorkflowAbiGenerator.metadataPath).foreach(path =>
          normalfirst(path) shouldBe _realm_string(realm, path))
        normalfirst(StateMachineWorkflowAbiGenerator.metadataPath) shouldBe canonical
        json.fields.map(_._1).toVector shouldBe Vector("schemaVersion", "workflows")
        (json \ "schemaVersion").as[String] shouldBe _schema
        _expect_projection(record, workflow, normalfirst(paths(2)))
        _expect_shared(normalfirst(_abi_path))
        normalfirst(_bootstrap_path) shouldBe _expected_bootstrap(Vector("SafetyWorkflowStateMachineWorkflow1"))
        workflow.version shouldBe "workflow-safety-v4"
        workflow.source shouldBe WorkflowSourceCorrelation(
          WorkflowSourceIdentity(Some(_line(source, "# WORKFLOW"))), WorkflowSourceIdentity(Some(_line(source, "## SafetyWorkflow"))))
        workflow.failurePolicies shouldBe Vector(WorkflowFailurePolicy("capture-failure",
          WorkflowInvocationTarget.Participant("capture-payment-capability"), "capture-payment", WorkflowFailureClassification.Permanent,
          Vector(
            WorkflowFailureRule("transient.payment", WorkflowFailureClassification.Retryable, WorkflowSourceIdentity(Some(_line(source, "##### transient.payment")))),
            WorkflowFailureRule("payment.rejected", WorkflowFailureClassification.Permanent, WorkflowSourceIdentity(Some(_line(source, "##### payment.rejected"))))),
          WorkflowSourceIdentity(Some(_line(source, "#### capture-failure")))))
        workflow.executionSafetyPolicy shouldBe Some(WorkflowExecutionSafetyPolicy("order-run", WorkflowDuplicateProtection.Required("order-start"),
          WorkflowSourceIdentity(Some(_line(source, "### EXECUTION-SAFETY")))))
        workflow.invocationPolicies.head.source.line shouldBe Some(_line(source, "#### capture-control"))
        workflow.lifecyclePolicy.get.source.line shouldBe Some(_line(source, "### LIFECYCLE"))
        workflow.waits.map(_.source.line) shouldBe Vector(Some(_line(source, "#### payment-timer")), Some(_line(source, "#### completion-signal")))
        workflow.invocationPolicies.head.retry shouldBe Some(WorkflowRetryPolicy(3, 250L))
        workflow.invocationPolicies.head.executionTimeoutMillis shouldBe Some(30000L)
        workflow.lifecyclePolicy.get.deadlineMillis shouldBe Some(60000L)
        workflow.waits.map(_.trigger) shouldBe Vector(WorkflowWaitTrigger.Timer(250L), WorkflowWaitTrigger.Signal("payment.completed"))
        workflow.compositeStateMachine.actions.head.metadata.get.idempotency shouldBe CompositeStateMachineIdempotency.Required("payment-action-key")
        StateMachineWorkflowAbiGenerator.executionSafetySchemaVersion shouldBe _schema
        StateMachineWorkflowAbiGenerator.executionSafetyBootstrapSchemaVersion shouldBe _bootstrap_schema
      }

      "retain the independently captured original v3 products byte for byte on both repeated routes" in {
        Given("the original lifecycle CML and four immutable original producer output resources captured before the v4 edit")
        val input = _base.resolve("src/test/resources/modeler/workflow-lifecycle-policy.cml")
        val paths = _paths("LifecycleWorkflowStateMachineWorkflow1")
        val expected = paths.map(path => path -> Files.readAllBytes(_baseline.resolve(Paths.get(path).getFileName)).toVector).toMap
        val outputs = Vector("modeler-scala", "modeler-scala-value").map(command =>
          command -> _base.resolve(s"target/test-generated/workflow-execution-safety-v3-$command"))

        When("each public producer projects the original lifecycle fixture twice")
        val products = outputs.flatMap { case (command, out) =>
          _run(command, input, out)
          val first = _product_bytes(out, paths)
          _run(command, input, out)
          Vector(first, _product_bytes(out, paths))
        }

        Then("every actual byte vector equals the independent original resource without expected-byte regeneration")
        products.foreach(_ shouldBe expected)
        products.distinct.size shouldBe 1
      }
    }

    "retain optional and ordered source meanings" which {
      Vector(
        ("failure only with an explicit default and no rules", _default_only, true, false, false),
        ("safety only with REQUIRED protection", _required, false, true, true),
        ("safety only with NOT_REQUIRED protection", _not_required, false, true, false),
        ("combined default-only and REQUIRED metadata", _default_only + _required, true, true, true)
      ).foreach { case (description, metadata, hasfailure, hassafety, required) =>
        s"project $description with explicit absence for every undeclared inherited field" in {
          Given(s"real accepted CML containing $description and no invocation, lifecycle or wait sections")
          val workflow = _workflow(_source(metadata, false))

          When("the independent optional metadata selects v4 Scala and canonical JSON")
          val realm = StateMachineWorkflowAbiGenerator.generate(Vector(workflow))
          val json = Json.parse(StateMachineWorkflowAbiGenerator.canonicalJson(Vector(workflow)))
          val record = (json \ "workflows").as[Vector[JsObject]].head
          val descriptor = _realm_string(realm, s"${_root}/SafetyWorkflowStateMachineWorkflow1.scala")

          Then("either trigger selects the full v4 schema without manufacturing policies, rules, keys or older metadata")
          (json \ "schemaVersion").as[String] shouldBe _schema
          workflow.failurePolicies.nonEmpty shouldBe hasfailure
          workflow.failurePolicies.flatMap(_.rules) shouldBe Vector.empty
          workflow.executionSafetyPolicy.nonEmpty shouldBe hassafety
          _expect_projection(record, workflow, descriptor)
          _expect_shared(_realm_string(realm, _abi_path))
          _realm_string(realm, _bootstrap_path) shouldBe _expected_bootstrap(Vector("SafetyWorkflowStateMachineWorkflow1"))
          (record \ "invocationPolicies").get shouldBe Json.arr()
          (record \ "lifecyclePolicy").get shouldBe JsNull
          (record \ "waits").get shouldBe Json.arr()
          if (!hasfailure) (record \ "failurePolicies").get shouldBe Json.arr()
          if (!hassafety) (record \ "executionSafetyPolicy").get shouldBe JsNull
          if (hassafety && !required) (record \ "executionSafetyPolicy" \ "duplicateProtection" \ "idempotencyKeyRef").get shouldBe JsNull
          descriptor should include("invocationPolicies = Vector.empty")
          descriptor should include("lifecyclePolicy = None")
          descriptor should include("waits = Vector.empty")
        }
      }

      Vector("Action" -> "action = CAPTURE_payment", "Participant" -> "participant = CAPTURE_payment_CAPABILITY").foreach { case (description, target) =>
        s"preserve declared $description aliases separately from canonical Action identity" in {
          Given(s"a real default-only failure policy with a normalized $description alias")
          val workflow = _workflow(_source(_default_only.replace("action = capture-payment", target)))

          When("the admitted target is projected without further alias resolution")
          val record = _record(workflow)
          val descriptor = _realm_string(StateMachineWorkflowAbiGenerator.generate(Vector(workflow)), s"${_root}/SafetyWorkflowStateMachineWorkflow1.scala")

          Then("the declared target spelling and kind coexist with canonical capture-payment and an empty rule vector")
          workflow.failurePolicies.head.target.identity shouldBe target.substring(target.indexOf("= ") + 2)
          workflow.failurePolicies.head.actionIdentity shouldBe "capture-payment"
          workflow.failurePolicies.head.rules shouldBe Vector.empty
          _expect_projection(record, workflow, descriptor)
        }
      }

      "preserve multiple real policies, ordered rules and distinct case-sensitive opaque identities" in {
        Given("two valid logical Actions with ordered policies and exact Unicode, punctuation and case-sensitive failure rule headings")
        val metadata = """### FAILURE-POLICY
          |
          |#### refund-first
          |
          |action = REFUND_payment
          |default-classification = RETRYABLE
          |
          |##### payment.rejected
          |
          |classification = PERMANENT
          |
          |##### Payment.Rejected
          |
          |classification = RETRYABLE
          |
          |##### 決済 / "理由" \v1
          |
          |classification = PERMANENT
          |
          |#### capture-second
          |
          |participant = CAPTURE_payment_CAPABILITY
          |default-classification = PERMANENT
          |
          |##### payment.rejected
          |
          |classification = RETRYABLE
          |
          |""".stripMargin
        val source = _source(metadata).replace("### REQUIRED-OPERATION\n", "##### refund-payment\n\nkind = OPERATION\noperation = capturePayment\ninput = payment.subject\n\n### REQUIRED-OPERATION\n")
        val workflow = _workflow(source)

        When("the real ordered declarations are lowered into both actual ABI products")
        val record = _record(workflow)
        val descriptor = _realm_string(StateMachineWorkflowAbiGenerator.generate(Vector(workflow)), s"${_root}/SafetyWorkflowStateMachineWorkflow1.scala")

        Then("all policies and rules preserve explicit defaults, classifications, aliases, identities and actual heading locations")
        workflow.failurePolicies.map(_.identity) shouldBe Vector("refund-first", "capture-second")
        workflow.failurePolicies.map(_.actionIdentity) shouldBe Vector("refund-payment", "capture-payment")
        workflow.failurePolicies.map(_.defaultClassification) shouldBe Vector(WorkflowFailureClassification.Retryable, WorkflowFailureClassification.Permanent)
        workflow.failurePolicies.head.rules.map(_.identity) shouldBe Vector("payment.rejected", "Payment.Rejected", "決済 / \"理由\" \\v1")
        workflow.failurePolicies.head.rules.map(_.classification) shouldBe Vector(WorkflowFailureClassification.Permanent, WorkflowFailureClassification.Retryable, WorkflowFailureClassification.Permanent)
        workflow.failurePolicies.map(_.source.line) shouldBe Vector(Some(_line(source, "#### refund-first")), Some(_line(source, "#### capture-second")))
        workflow.failurePolicies.head.rules.map(_.source.line) shouldBe workflow.failurePolicies.head.rules.map(rule => Some(_line(source, s"##### ${rule.identity}")))
        _expect_projection(record, workflow, descriptor)
      }
    }

    "preserve lossless programmatic scalar edges" which {
      "retain escaped strings, nullable sources, exact Long maxima and zero inherited retry delay" in {
        Given("accepted real IR copied with strings outside heading grammar, absent source locations and inherited maximum Long bounds")
        val workflow = _edge_workflow(_workflow(Files.readString(_fixture_path)))

        When("the immutable edge model is projected into actual Scala literals and independently decoded JSON")
        val json = StateMachineWorkflowAbiGenerator.canonicalJson(Vector(workflow))
        val record = (Json.parse(json) \ "workflows").as[Vector[JsObject]].head
        val descriptor = _realm_string(StateMachineWorkflowAbiGenerator.generate(Vector(workflow)), s"${_root}/SafetyEdgeWorkflowStateMachineWorkflow1.scala")

        Then("each exact opaque identity and reference, source null and decimal Long is retained without normalization")
        _expect_projection(record, workflow, descriptor)
        (record \ "failurePolicies").as[Vector[JsObject]].head.value("identity") shouldBe Json.toJson(_escaped + "policy")
        (record \ "executionSafetyPolicy" \ "logicalExecutionIdentityRef").as[String] shouldBe _escaped + "logical"
        (record \ "executionSafetyPolicy" \ "duplicateProtection" \ "idempotencyKeyRef").as[String] shouldBe _escaped + "key"
        (record \ "source" \ "root" \ "line").get shouldBe JsNull
        (record \ "source" \ "definition" \ "line").get shouldBe JsNull
        (record \ "executionSafetyPolicy" \ "source" \ "line").get shouldBe JsNull
        descriptor should include("\"quote\\\"slash\\\\line\\nreturn\\rtab\\tcontrol\\u0001\\u0085決済logical\"")
        descriptor should include("StateMachineWorkflowAbi.SourceIdentity(None)")
        descriptor should include("9223372036854775807L")
        descriptor should include("StateMachineWorkflowAbi.RetryPolicy(3, 0L)")
        json should include("9223372036854775807")
        (record \ "lifecyclePolicy" \ "deadlineMillis").get shouldBe JsNumber(BigDecimal(Long.MaxValue))
        (record \ "waits").as[Vector[JsObject]].head.value("trigger") shouldBe Json.obj("kind" -> "TIMER", "delayMillis" -> JsNumber(BigDecimal(Long.MaxValue)))
        (record \ "invocationPolicies").as[Vector[JsObject]].map(x => (x \ "retry" \ "fixedRetryDelayMillis").get) shouldBe
          Vector(JsNumber(BigDecimal(Long.MaxValue)), JsNumber(BigDecimal(0)))
      }

      "preserve nonempty opaque strings and both closed classifications under ScalaCheck" in {
        Given("accepted real IR and arbitrary nonempty opaque strings containing printable, escaped, control and Unicode characters")
        val original = _workflow(Files.readString(_fixture_path))
        val strings = Gen.nonEmptyListOf(Gen.oneOf('a', 'Z', '.', ' ', '"', '\\', '\n', '\r', '\t', '\u0001', '\u0085', '決')).map(_.mkString)
        val classifications = Gen.oneOf(WorkflowFailureClassification.Retryable, WorkflowFailureClassification.Permanent)

        When("ScalaCheck projects each sample in memory and independently decodes the canonical JSON")
        val property = Prop.forAll(strings, classifications) { (opaque, classification) =>
          val policy = original.failurePolicies.head.copy(identity = opaque, target = WorkflowInvocationTarget.Participant(opaque),
            actionIdentity = opaque, defaultClassification = classification,
            rules = Vector(WorkflowFailureRule(opaque, classification, WorkflowSourceIdentity(None))), source = WorkflowSourceIdentity(None))
          val workflow = original.copy(failurePolicies = Vector(policy), executionSafetyPolicy = Some(
            WorkflowExecutionSafetyPolicy(opaque, WorkflowDuplicateProtection.Required(opaque), WorkflowSourceIdentity(None))))
          val record = _record(workflow)
          val projected = (record \ "failurePolicies").as[Vector[JsObject]].head
          val rule = (projected \ "rules").as[Vector[JsObject]].head
          (projected \ "identity").as[String] == opaque && (projected \ "target" \ "identity").as[String] == opaque &&
            (projected \ "actionIdentity").as[String] == opaque && (projected \ "defaultClassification").as[String] == classification.canonicalValue &&
            (rule \ "identity").as[String] == opaque && (rule \ "classification").as[String] == classification.canonicalValue &&
            (record \ "executionSafetyPolicy" \ "logicalExecutionIdentityRef").as[String] == opaque &&
            (record \ "executionSafetyPolicy" \ "duplicateProtection" \ "idempotencyKeyRef").as[String] == opaque
        }
        val result = Test.check(Test.Parameters.default.withMinSuccessfulTests(60), property)

        Then("all generated samples retain exact decoded values and classifications without filesystem side effects")
        result.passed shouldBe true
      }
    }

    "select one schema for a complete ordered mixed collection" which {
      "retain seven complete members and author actual Realm products and a typed Scala 3 consumer" in {
        Given("parsed original v1, v2, v3 and safety CML with the frozen seven-member order and bounded immutable copies")
        val legacy = _workflow(_resource("workflow-invocation-policy-legacy.cml")).copy(identity = "LegacyWorkflow")
        val policy = _workflow(_resource("workflow-invocation-policy.cml")).copy(identity = "PolicyWorkflow")
        val lifecycle = _workflow(_resource("workflow-lifecycle-policy.cml")).copy(identity = "LifecycleWorkflow")
        val safety = _workflow(Files.readString(_fixture_path))
        val failureonly = safety.copy(identity = "FailureOnlyWorkflow", executionSafetyPolicy = None)
        val notrequired = safety.copy(identity = "NotRequiredWorkflow", failurePolicies = Vector.empty, invocationPolicies = Vector.empty,
          lifecyclePolicy = None, waits = Vector.empty, executionSafetyPolicy = safety.executionSafetyPolicy.map(_.copy(duplicateProtection = WorkflowDuplicateProtection.NotRequired)))
        val edge = _edge_workflow(safety)
        val workflows = Vector(legacy, policy, lifecycle, safety, failureonly, notrequired, edge)
        val names = Vector("LegacyWorkflow", "PolicyWorkflow", "LifecycleWorkflow", "SafetyWorkflow", "FailureOnlyWorkflow", "NotRequiredWorkflow", "SafetyEdgeWorkflow")
        val objects = names.zipWithIndex.map { case (name, index) => s"${name}StateMachineWorkflow${index + 1}" }
        val memberpaths = objects.map(name => s"${_root}/$name.scala")
        val paths = Vector(_abi_path, _bootstrap_path) ++ memberpaths
        val out = _base.resolve("target/test-generated/workflow-execution-safety-compile")
        val normalout = _base.resolve("target/test-generated/workflow-execution-safety-normal")
        val valueout = _base.resolve("target/test-generated/workflow-execution-safety-value")

        When("the ordered collection is generated twice and its actual products, sidecar and consumer are saved")
        val first = StateMachineWorkflowAbiGenerator.generate(workflows)
        val second = StateMachineWorkflowAbiGenerator.generate(workflows)
        val json = StateMachineWorkflowAbiGenerator.canonicalJson(workflows)
        val repeatedjson = StateMachineWorkflowAbiGenerator.canonicalJson(workflows)
        val records = (Json.parse(json) \ "workflows").as[Vector[JsObject]]
        paths.foreach(path => _write(out.resolve(path), _realm_string(first, path)))
        _write(out.resolve(StateMachineWorkflowAbiGenerator.metadataPath), json)
        _write(out.resolve(s"${_root}/WorkflowExecutionSafetyAbiConsumerProbe.scala"), _consumer_probe(objects.last, objects))
        _run("modeler-scala", _fixture_path, normalout)
        _run("modeler-scala-value", _fixture_path, valueout)
        Vector(normalout, valueout).foreach(routeout =>
          _write(routeout.resolve(s"${_root}/WorkflowExecutionSafetyAbiConsumerProbe.scala"),
            _consumer_probe("SafetyWorkflowStateMachineWorkflow1", Vector("SafetyWorkflowStateMachineWorkflow1"))))

        Then("one complete v4 ABI and bootstrap preserve ordered old data and explicit empty or null new metadata")
        json shouldBe repeatedjson
        paths.foreach(path => _realm_string(first, path) shouldBe _realm_string(second, path))
        _realm_string(first, _abi_path) shouldBe Files.readString(normalout.resolve(_abi_path))
        _realm_string(first, _abi_path) shouldBe Files.readString(valueout.resolve(_abi_path))
        _expect_shared(_realm_string(first, _abi_path))
        _realm_string(first, _bootstrap_path) shouldBe _expected_bootstrap(objects)
        (Json.parse(json) \ "schemaVersion").as[String] shouldBe _schema
        records.map(x => (x \ "identity").as[String]) shouldBe names
        records.zip(workflows).zip(memberpaths).foreach { case ((record, workflow), path) =>
          _expect_projection(record, workflow, _realm_string(first, path))
        }
        records.take(3).foreach { record =>
          (record \ "failurePolicies").get shouldBe Json.arr()
          (record \ "executionSafetyPolicy").get shouldBe JsNull
        }
        (records.head \ "invocationPolicies").get shouldBe Json.arr()
        records.take(2).foreach { record =>
          (record \ "lifecyclePolicy").get shouldBe JsNull
          (record \ "waits").get shouldBe Json.arr()
        }
        (records(4) \ "executionSafetyPolicy").get shouldBe JsNull
        (records(5) \ "failurePolicies").get shouldBe Json.arr()
        (records(5) \ "executionSafetyPolicy" \ "duplicateProtection").get shouldBe Json.obj("kind" -> "NOT_REQUIRED", "idempotencyKeyRef" -> JsNull)
      }
    }
  }

  private def _expect_projection(record: JsObject, workflow: WorkflowDefinition, descriptor: String): Unit = {
    record.fields.map(_._1).toVector shouldBe Vector("identity", "version", "source", "states", "actions", "requiredSpi", "invocationPolicies", "lifecyclePolicy", "waits", "failurePolicies", "executionSafetyPolicy")
    record shouldBe _expected_record(workflow)
    descriptor should include(s"schemaVersion = ${_literal(_schema)}")
    descriptor should include(s"identity = ${_literal(workflow.identity)}")
    descriptor should include(s"version = ${_literal(workflow.version)}")
    descriptor should include(s"source = StateMachineWorkflowAbi.WorkflowSourceCorrelation(${_source_literal(workflow.source.root)}, ${_source_literal(workflow.source.definition)})")
    descriptor should include("StateMachineWorkflowAbi.ComponentFactoryMetadata(workflow)")
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
    (record \ "source").as[JsObject].fields.map(_._1).toVector shouldBe Vector("root", "definition")
    (record \ "states").as[Vector[JsObject]].foreach(_.fields.map(_._1).toVector shouldBe Vector("name", "source"))
    (record \ "actions").as[Vector[JsObject]].foreach { action =>
      action.fields.map(_._1).toVector shouldBe Vector("identity", "kind", "operation", "inputBinding", "source")
      (action \ "operation").as[JsObject].fields.map(_._1).toVector shouldBe Vector("service", "name", "inputType", "resultType", "source")
    }
    (record \ "requiredSpi").as[Vector[JsObject]].foreach { required =>
      required.fields.map(_._1).toVector shouldBe Vector("capability", "actionIdentity", "operation", "source")
      (required \ "operation").as[JsObject].fields.map(_._1).toVector shouldBe Vector("service", "name", "inputType", "resultType", "source")
    }
    (record \ "invocationPolicies").as[Vector[JsObject]].foreach { policy =>
      policy.fields.map(_._1).toVector shouldBe Vector("identity", "target", "actionIdentity", "retry", "executionTimeoutMillis", "source")
      (policy \ "target").as[JsObject].fields.map(_._1).toVector shouldBe Vector("kind", "identity")
      (policy \ "retry").get match {
        case retry: JsObject => retry.fields.map(_._1).toVector shouldBe Vector("maximumAttempts", "fixedRetryDelayMillis")
        case value => value shouldBe JsNull
      }
    }
    (record \ "lifecyclePolicy").get match {
      case policy: JsObject => policy.fields.map(_._1).toVector shouldBe Vector("deadlineMillis", "cancellation", "source")
      case value => value shouldBe JsNull
    }
    (record \ "waits").as[Vector[JsObject]].foreach { wait =>
      wait.fields.map(_._1).toVector shouldBe Vector("identity", "state", "stateIdentity", "trigger", "source")
      val trigger = (wait \ "trigger").as[JsObject]
      trigger.fields.map(_._1).toVector shouldBe (if ((trigger \ "kind").as[String] == "TIMER") Vector("kind", "delayMillis") else Vector("kind", "identity"))
    }
    val labels = Vector("requiredOperations =", "invocationPolicies =", "lifecyclePolicy =", "waits =", "failurePolicies =", "executionSafetyPolicy =")
    val positions = labels.map(descriptor.indexOf(_))
    positions.foreach(_ should be >= 0)
    positions shouldBe positions.sorted
    workflow.invocationPolicies.foreach { policy =>
      val retry = policy.retry.map(x => s"Some(StateMachineWorkflowAbi.RetryPolicy(${x.maximumAttempts}, ${x.fixedRetryDelayMillis}L))").getOrElse("None")
      val timeout = policy.executionTimeoutMillis.map(x => s"Some(${x}L)").getOrElse("None")
      descriptor should include(s"StateMachineWorkflowAbi.InvocationPolicy(${_literal(policy.identity)}, ${_target_literal(policy.target)}, ${_literal(policy.actionIdentity)}, $retry, $timeout, ${_source_literal(policy.source)})")
    }
    workflow.lifecyclePolicy match {
      case Some(policy) =>
        val deadline = policy.deadlineMillis.map(x => s"Some(${x}L)").getOrElse("None")
        val cancellation = policy.cancellation.map(_ => "Some(StateMachineWorkflowAbi.CancellationMode.Cooperative)").getOrElse("None")
        descriptor should include(s"lifecyclePolicy = Some(StateMachineWorkflowAbi.LifecyclePolicy($deadline, $cancellation, ${_source_literal(policy.source)}))")
      case None => descriptor should include("lifecyclePolicy = None")
    }
    workflow.waits.foreach { wait =>
      val trigger = wait.trigger match {
        case WorkflowWaitTrigger.Timer(delay) => s"StateMachineWorkflowAbi.WaitTrigger.Timer(${delay}L)"
        case WorkflowWaitTrigger.Signal(identity) => s"StateMachineWorkflowAbi.WaitTrigger.Signal(${_literal(identity)})"
      }
      descriptor should include(s"StateMachineWorkflowAbi.WaitDeclaration(${_literal(wait.identity)}, ${_literal(wait.state)}, ${_literal(wait.stateIdentity)}, $trigger, ${_source_literal(wait.source)})")
    }
    val policies = (record \ "failurePolicies").as[Vector[JsObject]]
    val policypositions = workflow.failurePolicies.map(policy => descriptor.indexOf(s"StateMachineWorkflowAbi.FailurePolicy(${_literal(policy.identity)},"))
    policypositions.foreach(_ should be >= 0)
    policypositions shouldBe policypositions.sorted
    policies.zip(workflow.failurePolicies).foreach { case (projected, policy) =>
      projected.fields.map(_._1).toVector shouldBe Vector("identity", "target", "actionIdentity", "defaultClassification", "rules", "source")
      (projected \ "target").as[JsObject].fields.map(_._1).toVector shouldBe Vector("kind", "identity")
      val rules = (projected \ "rules").as[Vector[JsObject]]
      rules.foreach(_.fields.map(_._1).toVector shouldBe Vector("identity", "classification", "source"))
      val ruleliterals = policy.rules.map(rule => s"StateMachineWorkflowAbi.FailureRule(${_literal(rule.identity)}, ${_classification_literal(rule.classification)}, ${_source_literal(rule.source)})")
      val rulepositions = ruleliterals.map(descriptor.indexOf(_))
      rulepositions.foreach(_ should be >= 0)
      rulepositions shouldBe rulepositions.sorted
      descriptor should include(s"StateMachineWorkflowAbi.FailurePolicy(${_literal(policy.identity)}, ${_target_literal(policy.target)}, ${_literal(policy.actionIdentity)}, ${_classification_literal(policy.defaultClassification)}, ${_vector_literal(ruleliterals)}, ${_source_literal(policy.source)})")
    }
    workflow.executionSafetyPolicy match {
      case Some(policy) =>
        val projected = (record \ "executionSafetyPolicy").as[JsObject]
        projected.fields.map(_._1).toVector shouldBe Vector("logicalExecutionIdentityRef", "duplicateProtection", "source")
        (projected \ "duplicateProtection").as[JsObject].fields.map(_._1).toVector shouldBe Vector("kind", "idempotencyKeyRef")
        val protection = policy.duplicateProtection match {
          case WorkflowDuplicateProtection.NotRequired => "StateMachineWorkflowAbi.DuplicateProtection.NotRequired"
          case WorkflowDuplicateProtection.Required(key) => s"StateMachineWorkflowAbi.DuplicateProtection.Required(${_literal(key)})"
        }
        descriptor should include(s"executionSafetyPolicy = Some(StateMachineWorkflowAbi.ExecutionSafetyPolicy(${_literal(policy.logicalExecutionIdentityRef)}, $protection, ${_source_literal(policy.source)}))")
      case None => descriptor should include("executionSafetyPolicy = None")
    }
    if (workflow.failurePolicies.isEmpty) descriptor should include("failurePolicies = Vector.empty")
    if (workflow.invocationPolicies.isEmpty) descriptor should include("invocationPolicies = Vector.empty")
    if (workflow.waits.isEmpty) descriptor should include("waits = Vector.empty")
  }

  private def _expected_record(workflow: WorkflowDefinition): JsObject = Json.obj(
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

  private def _target_json(target: WorkflowInvocationTarget): JsObject = Json.obj("kind" -> (target match {
    case _: WorkflowInvocationTarget.Action => "ACTION"
    case _: WorkflowInvocationTarget.Participant => "PARTICIPANT"
  }), "identity" -> target.identity)

  private def _target_literal(target: WorkflowInvocationTarget): String = target match {
    case WorkflowInvocationTarget.Action(identity) => s"StateMachineWorkflowAbi.InvocationTarget.Action(${_literal(identity)})"
    case WorkflowInvocationTarget.Participant(identity) => s"StateMachineWorkflowAbi.InvocationTarget.Participant(${_literal(identity)})"
  }

  private def _classification_literal(classification: WorkflowFailureClassification): String = classification match {
    case WorkflowFailureClassification.Retryable => "StateMachineWorkflowAbi.FailureClassification.Retryable"
    case WorkflowFailureClassification.Permanent => "StateMachineWorkflowAbi.FailureClassification.Permanent"
  }

  private def _expect_shared(source: String): Unit = {
    source should include(s"val VERSION: String = ${_literal(_schema)}")
    Vector("InvocationTarget", "RetryPolicy", "InvocationPolicy", "CancellationMode", "LifecyclePolicy", "WaitTrigger", "WaitDeclaration",
      "FailureClassification", "FailureRule", "FailurePolicy", "DuplicateProtection", "ExecutionSafetyPolicy").foreach(name => source should include(name))
    source should include("waits: Vector[WaitDeclaration] = Vector.empty, failurePolicies: Vector[FailurePolicy] = Vector.empty, executionSafetyPolicy: Option[ExecutionSafetyPolicy] = None")
  }

  private def _edge_workflow(original: WorkflowDefinition): WorkflowDefinition = {
    val absent = WorkflowSourceIdentity(None)
    val invocation = original.invocationPolicies.head
    original.copy(identity = "SafetyEdgeWorkflow", source = WorkflowSourceCorrelation(absent, absent),
      invocationPolicies = Vector(
        invocation.copy(identity = _escaped + "invocation-max", target = WorkflowInvocationTarget.Action(_escaped + "action"), actionIdentity = _escaped + "canonical",
          retry = Some(WorkflowRetryPolicy(3, Long.MaxValue)), executionTimeoutMillis = Some(Long.MaxValue), source = absent),
        invocation.copy(identity = _escaped + "invocation-zero", retry = Some(WorkflowRetryPolicy(3, 0L)), executionTimeoutMillis = Some(Long.MaxValue), source = absent)),
      lifecyclePolicy = Some(WorkflowLifecyclePolicy(Some(Long.MaxValue), Some(WorkflowCancellationMode.Cooperative), absent)),
      waits = Vector(original.waits.head.copy(identity = _escaped + "timer", state = _escaped + "state", stateIdentity = _escaped + "canonical-state",
        trigger = WorkflowWaitTrigger.Timer(Long.MaxValue), source = absent),
        original.waits.last.copy(identity = _escaped + "signal", trigger = WorkflowWaitTrigger.Signal(_escaped + "declared-signal"), source = absent)),
      failurePolicies = Vector(original.failurePolicies.head.copy(identity = _escaped + "policy", target = WorkflowInvocationTarget.Participant(_escaped + "target"),
        actionIdentity = _escaped + "canonical-action", source = absent,
        rules = Vector(WorkflowFailureRule(_escaped + "rule", WorkflowFailureClassification.Retryable, absent),
          WorkflowFailureRule(_escaped + "Rule", WorkflowFailureClassification.Permanent, absent)))),
      executionSafetyPolicy = Some(WorkflowExecutionSafetyPolicy(_escaped + "logical", WorkflowDuplicateProtection.Required(_escaped + "key"), absent)))
  }

  private def _source(metadata: String, older: Boolean = true): String = {
    val source = Files.readString(_fixture_path)
    val start = source.indexOf(if (older) "### FAILURE-POLICY" else "### INVOCATION-POLICY")
    val end = source.indexOf("# STATE-MACHINE", start)
    source.substring(0, start) + metadata + "\n" + source.substring(end)
  }

  private def _resource(name: String): String = Files.readString(_base.resolve(s"src/test/resources/modeler/$name"))

  private def _workflow(source: String): WorkflowDefinition = {
    val model = KaleidoxModel.parse(KaleidoxConfig.default, source)
    if (model.errors.nonEmpty) throw new IllegalArgumentException(s"CML fixture must parse without errors: ${model.errors.mkString(" | ")}")
    Modeler.ModelBuilder(model)
    CompositeStateMachineCml.workflowDefinitions(model).head
  }

  private def _record(workflow: WorkflowDefinition): JsObject =
    (Json.parse(StateMachineWorkflowAbiGenerator.canonicalJson(Vector(workflow))) \ "workflows").as[Vector[JsObject]].head

  private def _paths(objectname: String): Vector[String] = Vector(_abi_path, _bootstrap_path, s"${_root}/$objectname.scala", StateMachineWorkflowAbiGenerator.metadataPath)
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
  private def _line_json(line: Option[Int]): JsObject = Json.obj("line" -> line.map[JsValue](x => JsNumber(BigDecimal(x))).getOrElse[JsValue](JsNull))
  private def _source_json(source: WorkflowSourceIdentity): JsObject = _line_json(source.line)
  private def _source_literal(source: WorkflowSourceIdentity): String = s"StateMachineWorkflowAbi.SourceIdentity(${source.line.map(x => s"Some($x)").getOrElse("None")})"
  private def _string_json(value: Option[String]): JsValue = value.map[JsValue](Json.toJson(_)).getOrElse(JsNull)
  private def _long_json(value: Option[Long]): JsValue = value.map[JsValue](x => JsNumber(BigDecimal(x))).getOrElse(JsNull)
  private def _vector_literal(values: Vector[String]): String = if (values.isEmpty) "Vector.empty" else values.mkString("Vector(", ", ", ")")
  private def _literal(value: String): String = {
    val encoded = Json.stringify(Json.toJson(value))
    encoded.flatMap(character => if (Character.isISOControl(character)) f"\\u${character.toInt}%04x" else character.toString)
  }
  private def _expected_bootstrap(objects: Vector[String]): String =
    "package domain.statemachine.workflow\n\nobject StateMachineWorkflowComponentFactoryBootstrap {\n  val schemaVersion: String = " + _literal(_bootstrap_schema) +
      "\n  val componentFactoryMetadata: Vector[StateMachineWorkflowAbi.ComponentFactoryMetadata] = " +
      objects.map(name => s"$name.componentFactoryMetadata").mkString("Vector(", ", ", ")") + "\n}\n"

  private def _consumer_probe(member: String, objects: Vector[String]): String = s"""package domain.statemachine.workflow
    |
    |import StateMachineWorkflowAbi._
    |
    |object WorkflowExecutionSafetyAbiConsumerProbe {
    |  val source: WorkflowSourceCorrelation = WorkflowSourceCorrelation(root = SourceIdentity(line = None), definition = SourceIdentity(line = Some(1)))
    |  val legacy: WorkflowDescriptor = WorkflowDescriptor("v1", "legacy", "v1", source, Vector.empty, Vector.empty, Vector.empty)
    |  val invocation: WorkflowDescriptor = WorkflowDescriptor("v2", "invocation", "v2", source, Vector.empty, Vector.empty, Vector.empty, Vector.empty)
    |  val lifecycle: WorkflowDescriptor = WorkflowDescriptor("v3", "lifecycle", "v3", source, Vector.empty, Vector.empty, Vector.empty, Vector.empty, None, Vector.empty)
    |  val oldFailures: Vector[Vector[FailurePolicy]] = Vector(legacy.failurePolicies, invocation.failurePolicies, lifecycle.failurePolicies)
    |  val oldSafety: Vector[Option[ExecutionSafetyPolicy]] = Vector(legacy.executionSafetyPolicy, invocation.executionSafetyPolicy, lifecycle.executionSafetyPolicy)
    |  val rule: FailureRule = FailureRule(identity = "opaque", classification = FailureClassification.Retryable, source = SourceIdentity(None))
    |  val policy: FailurePolicy = FailurePolicy(identity = "declared", target = InvocationTarget.Action("declared-action"), actionIdentity = "canonical-action", defaultClassification = FailureClassification.Permanent, rules = Vector(rule), source = SourceIdentity(None))
    |  val participant: InvocationTarget = InvocationTarget.Participant("declared-capability")
    |  val required: ExecutionSafetyPolicy = ExecutionSafetyPolicy(logicalExecutionIdentityRef = "logical-ref", duplicateProtection = DuplicateProtection.Required(idempotencyKeyRef = "key-ref"), source = SourceIdentity(None))
    |  val notRequired: ExecutionSafetyPolicy = ExecutionSafetyPolicy("logical-ref", DuplicateProtection.NotRequired, SourceIdentity(None))
    |  val retry: RetryPolicy = RetryPolicy(maximumAttempts = 3, fixedRetryDelayMillis = Long.MaxValue)
    |  val invocationPolicy: InvocationPolicy = InvocationPolicy("invoke", participant, "canonical-action", Some(retry), Some(Long.MaxValue), SourceIdentity(None))
    |  val lifecyclePolicy: LifecyclePolicy = LifecyclePolicy(deadlineMillis = Some(Long.MaxValue), cancellation = Some(CancellationMode.Cooperative), source = SourceIdentity(None))
    |  val timer: WaitDeclaration = WaitDeclaration("timer", "declared-state", "canonical-state", WaitTrigger.Timer(Long.MaxValue), SourceIdentity(None))
    |  val signal: WaitDeclaration = WaitDeclaration("signal", "declared-state", "canonical-state", WaitTrigger.Signal("declared-signal"), SourceIdentity(None))
    |  val actual: WorkflowDescriptor = $member.workflow
    |  val ordered: Vector[WorkflowDescriptor] = ${objects.map(name => s"$name.workflow").mkString("Vector(", ", ", ")")}
    |  val actualPolicies: Vector[FailurePolicy] = actual.failurePolicies
    |  val actualSafety: Option[ExecutionSafetyPolicy] = actual.executionSafetyPolicy
    |  val actualLogicalRefs: Option[String] = actualSafety.map(_.logicalExecutionIdentityRef)
    |  val actualKeyRefs: Option[Option[String]] = actualSafety.map(value => protectionData(value.duplicateProtection))
    |  val actualSources: Vector[Option[Int]] = actualPolicies.map(_.source.line)
    |  val actualDefaults: Vector[FailureClassification] = actualPolicies.map(_.defaultClassification)
    |  val actualRules: Vector[Vector[FailureRule]] = actualPolicies.map(_.rules)
    |  val actualDeadline: Option[Long] = actual.lifecyclePolicy.flatMap(_.deadlineMillis)
    |  val actualWaits: Vector[Either[Long, String]] = actual.waits.map(value => waitData(value.trigger))
    |  val actualRetryDelays: Vector[Long] = actual.invocationPolicies.flatMap(_.retry).map(_.fixedRetryDelayMillis)
    |  val actualTimeouts: Vector[Long] = actual.invocationPolicies.flatMap(_.executionTimeoutMillis)
    |  def classificationData(classification: FailureClassification): String = classification match {
    |    case FailureClassification.Retryable => "RETRYABLE"
    |    case FailureClassification.Permanent => "PERMANENT"
    |  }
    |  def protectionData(protection: DuplicateProtection): Option[String] = protection match {
    |    case DuplicateProtection.Required(key) => Some(key)
    |    case DuplicateProtection.NotRequired => None
    |  }
    |  def targetData(target: InvocationTarget): (String, String) = target match {
    |    case InvocationTarget.Action(identity) => ("ACTION", identity)
    |    case InvocationTarget.Participant(identity) => ("PARTICIPANT", identity)
    |  }
    |  def waitData(trigger: WaitTrigger): Either[Long, String] = trigger match {
    |    case WaitTrigger.Timer(delay) => Left(delay)
    |    case WaitTrigger.Signal(identity) => Right(identity)
    |  }
    |  def cancellationData(mode: CancellationMode): String = mode match {
    |    case CancellationMode.Cooperative => "COOPERATIVE"
    |  }
    |  val actualClassifications: Vector[String] = actualPolicies.flatMap(value => classificationData(value.defaultClassification) +: value.rules.map(rule => classificationData(rule.classification)))
    |  val actualTargets: Vector[(String, String)] = actualPolicies.map(value => targetData(value.target))
    |  val actualCancellation: Option[String] = actual.lifecyclePolicy.flatMap(_.cancellation).map(cancellationData)
    |}
    |""".stripMargin
}
