package cozy.modeler

import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.{Files, Path, Paths}
import org.goldenport.kaleidox.{Config => KaleidoxConfig, Model => KaleidoxModel}
import org.goldenport.realm.Realm
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import play.api.libs.json.{JsNull, JsNumber, JsObject, JsValue, Json}

/*
 * @since   Oct.  2, 2026
 * @version Oct.  2, 2026
 * @author  ASAMI, Tomoharu
 */
final class WorkflowLifecycleAbiGenerationSpec extends AnyWordSpec with Matchers with GivenWhenThen with ModelerSpecSupport {
  private val _base = Paths.get(sys.props("user.dir")).toAbsolutePath.normalize()
  private val _root = "target/scala-3.3.8/src_managed/main/scala/domain/statemachine/workflow"
  private val _abi_path = s"${_root}/StateMachineWorkflowAbi.scala"
  private val _bootstrap_path = s"${_root}/StateMachineWorkflowComponentFactoryBootstrap.scala"
  private val _fixture_path = _base.resolve("src/test/resources/modeler/workflow-lifecycle-policy.cml")
  private val _policy_path = _base.resolve("src/test/resources/modeler/workflow-invocation-policy.cml")
  private val _legacy_path = _base.resolve("src/test/resources/modeler/workflow-invocation-policy-legacy.cml")
  private val _baseline = _base.resolve("src/test/resources/modeler/workflow-lifecycle-v2-baseline")
  private val _escaped = "quote\"slash\\line\nreturn\rtab\tcontrol\u0001"
  private val _declared_metadata = """### LIFECYCLE
    |
    |deadline-millis = 60000
    |cancellation = COOPERATIVE
    |
    |### WAIT
    |
    |#### payment-timer
    |
    |state = Pending
    |delay-millis = 250
    |
    |#### completion-signal
    |
    |state = Complete
    |signal = payment.completed
    |
    |""".stripMargin
  private val _invocation_metadata = """### INVOCATION-POLICY
    |
    |#### capture-control
    |
    |action = capture-payment
    |execution-timeout-millis = 30000
    |
    |""".stripMargin

  "Workflow scheduling and lifecycle generated ABI" should {
    "project actual tracked source through both public Scala routes" which {
      "retain all four deterministic v3 products and complete located declarations" in {
        Given("the accepted lifecycle CML with a deadline, cooperative cancellation and ordered Timer and Signal waits")
        val source = Files.readString(_fixture_path)
        val workflow = _workflow(source)
        val paths = _paths("LifecycleWorkflowStateMachineWorkflow1")
        val normalout = _base.resolve("target/test-generated/workflow-lifecycle-normal")
        val valueout = _base.resolve("target/test-generated/workflow-lifecycle-value")

        When("both public routes generate the tracked source twice and the canonical generator projects the same model")
        _run("modeler-scala", _fixture_path, normalout)
        val normalfirst = _products(normalout, paths)
        _run("modeler-scala", _fixture_path, normalout)
        val normalsecond = _products(normalout, paths)
        _run("modeler-scala-value", _fixture_path, valueout)
        val valuefirst = _products(valueout, paths)
        _run("modeler-scala-value", _fixture_path, valueout)
        val valuesecond = _products(valueout, paths)
        val generated = StateMachineWorkflowAbiGenerator.generate(Vector(workflow))
        val canonicaljson = StateMachineWorkflowAbiGenerator.canonicalJson(Vector(workflow))
        val json = Json.parse(canonicaljson).as[JsObject]
        val record = (json \ "workflows").as[Vector[JsObject]].head

        Then("every public product equals its repeated, other-route and canonical projection")
        normalfirst shouldBe normalsecond
        normalfirst shouldBe valuefirst
        valuefirst shouldBe valuesecond
        paths.filterNot(_ == StateMachineWorkflowAbiGenerator.metadataPath).foreach { path =>
          normalfirst(path) shouldBe _realm_string(generated, path)
        }
        normalfirst(StateMachineWorkflowAbiGenerator.metadataPath) shouldBe canonicaljson
        json.fields.map(_._1).toVector shouldBe Vector("schemaVersion", "workflows")
        (json \ "schemaVersion").as[String] shouldBe "cozy.cml.statemachine-workflow-abi.v3"
        normalfirst(_abi_path) should include("val VERSION: String = \"cozy.cml.statemachine-workflow-abi.v3\"")
        normalfirst(_bootstrap_path) should include("cozy.cml.statemachine-workflow-bootstrap.v3")
        normalfirst(_bootstrap_path) should include("Vector(LifecycleWorkflowStateMachineWorkflow1.componentFactoryMetadata)")
        _expect_projection(record, workflow, normalfirst(paths(2)))

        And("policy, waits and Workflow version retain their actual static source correlations")
        workflow.version shouldBe "workflow-lifecycle-v3"
        workflow.source.root.line shouldBe Some(_line(source, "# WORKFLOW"))
        workflow.source.definition.line shouldBe Some(_line(source, "## LifecycleWorkflow"))
        workflow.lifecyclePolicy shouldBe Some(WorkflowLifecyclePolicy(Some(60000L), Some(WorkflowCancellationMode.Cooperative),
          WorkflowSourceIdentity(Some(_line(source, "### LIFECYCLE")))))
        workflow.waits shouldBe Vector(
          WorkflowWaitDeclaration("payment-timer", "Pending", "Pending", WorkflowWaitTrigger.Timer(250L), WorkflowSourceIdentity(Some(_line(source, "#### payment-timer")))),
          WorkflowWaitDeclaration("completion-signal", "Complete", "Complete", WorkflowWaitTrigger.Signal("payment.completed"), WorkflowSourceIdentity(Some(_line(source, "#### completion-signal")))))
        workflow.invocationPolicies.head.source.line shouldBe Some(_line(source, "#### capture-control"))
      }

      "retain the independent v2 four-product baseline byte for byte on both repeated routes" in {
        Given("the tracked invocation-policy CML and four original v2 product byte fixtures")
        val paths = _paths("InvocationControlledWorkflowStateMachineWorkflow1")
        val expected = paths.map { path => path -> Files.readAllBytes(_baseline.resolve(Paths.get(path).getFileName)).toVector }.toMap
        val outputs = Vector("modeler-scala", "modeler-scala-value").map { command =>
          command -> _base.resolve(s"target/test-generated/workflow-lifecycle-v2-$command")
        }

        When("each public route generates the original v2 source twice")
        val products = outputs.flatMap { case (command, out) =>
          _run(command, _policy_path, out)
          val first = _product_bytes(out, paths)
          _run(command, _policy_path, out)
          Vector(first, _product_bytes(out, paths))
        }

        Then("all four outputs retain the independently recorded bytes across routes and repetitions")
        products.foreach(_ shouldBe expected)
        products.distinct.size shouldBe 1
      }
    }

    "preserve optional declarations and lossless scalar meanings" which {
      Vector(
        ("deadline only", "### LIFECYCLE\n\ndeadline-millis = 1\n\n", Some(1L), None, Vector.empty[WorkflowWaitTrigger]),
        ("cancellation only", "### LIFECYCLE\n\ncancellation = COOPERATIVE\n\n", None, Some(WorkflowCancellationMode.Cooperative), Vector.empty[WorkflowWaitTrigger]),
        ("lifecycle only with maximum deadline", s"### LIFECYCLE\n\ndeadline-millis = ${Long.MaxValue}\ncancellation = COOPERATIVE\n\n", Some(Long.MaxValue), Some(WorkflowCancellationMode.Cooperative), Vector.empty[WorkflowWaitTrigger]),
        ("wait only with zero Timer and Signal", "### WAIT\n\n#### timer\n\nstate = Pending\ndelay-millis = 0\n\n#### signal\n\nstate = Complete\nsignal = Payment Completed / external:v1\n\n", None, None, Vector(WorkflowWaitTrigger.Timer(0L), WorkflowWaitTrigger.Signal("Payment Completed / external:v1"))),
        ("wait only with maximum Timer", s"### WAIT\n\n#### timer\n\nstate = Pending\ndelay-millis = ${Long.MaxValue}\n\n", None, None, Vector(WorkflowWaitTrigger.Timer(Long.MaxValue)))
      ).foreach { case (description, metadata, deadline, cancellation, triggers) =>
        s"project $description without invocation policy or manufactured defaults" in {
          Given(s"accepted real CML with $description and no invocation-policy section")
          val source = Files.readString(_fixture_path).replace(_declared_metadata, metadata).replace(_invocation_metadata, "")
          val workflow = _workflow(source)

          When("the normalized declarations are projected twice to canonical JSON and Scala")
          val json = StateMachineWorkflowAbiGenerator.canonicalJson(Vector(workflow))
          val repeatedjson = StateMachineWorkflowAbiGenerator.canonicalJson(Vector(workflow))
          val realm = StateMachineWorkflowAbiGenerator.generate(Vector(workflow))
          val repeated = StateMachineWorkflowAbiGenerator.generate(Vector(workflow))
          val descriptor = _realm_string(realm, s"${_root}/LifecycleWorkflowStateMachineWorkflow1.scala")
          val record = (Json.parse(json) \ "workflows").as[Vector[JsObject]].head

          Then("v3 carries explicit null or empty absence and exact Long values")
          json shouldBe repeatedjson
          _paths("LifecycleWorkflowStateMachineWorkflow1").filterNot(_ == StateMachineWorkflowAbiGenerator.metadataPath).foreach { path =>
            _realm_string(realm, path) shouldBe _realm_string(repeated, path)
          }
          (Json.parse(json) \ "schemaVersion").as[String] shouldBe "cozy.cml.statemachine-workflow-abi.v3"
          workflow.invocationPolicies shouldBe Vector.empty
          workflow.lifecyclePolicy.flatMap(_.deadlineMillis) shouldBe deadline
          workflow.lifecyclePolicy.flatMap(_.cancellation) shouldBe cancellation
          workflow.waits.map(_.trigger) shouldBe triggers
          _expect_projection(record, workflow, descriptor)
          descriptor should include("invocationPolicies = Vector.empty")
          if (triggers.isEmpty) descriptor should include("waits = Vector.empty")
          if (workflow.lifecyclePolicy.isEmpty) descriptor should include("lifecyclePolicy = None")
          if (deadline.contains(Long.MaxValue) || triggers.contains(WorkflowWaitTrigger.Timer(Long.MaxValue))) {
            json should include("9223372036854775807")
            descriptor should include("9223372036854775807L")
          }
          if (triggers.contains(WorkflowWaitTrigger.Timer(0L))) descriptor should include("WaitTrigger.Timer(0L)")
        }
      }

      "retain declared State alias spelling separately from the resolved canonical State" in {
        Given("accepted lifecycle CML whose Timer uses a case and separator State alias")
        val workflow = _workflow(Files.readString(_fixture_path).replace("state = Pending\ndelay-millis", "state = PEND_ing\ndelay-millis"))

        When("the declared alias and canonical State are emitted to both representations")
        val record = (Json.parse(StateMachineWorkflowAbiGenerator.canonicalJson(Vector(workflow))) \ "workflows").as[Vector[JsObject]].head
        val descriptor = _realm_string(StateMachineWorkflowAbiGenerator.generate(Vector(workflow)), s"${_root}/LifecycleWorkflowStateMachineWorkflow1.scala")

        Then("the declaration keeps PEND_ing and correlates it with Pending without substituting spelling")
        workflow.waits.head.state shouldBe "PEND_ing"
        workflow.waits.head.stateIdentity shouldBe "Pending"
        _expect_projection(record, workflow, descriptor)
      }

      "preserve escaped strings and absent source locations in programmatic declarations" in {
        Given("a parsed lifecycle model copied with escaped wait strings and nullable policy, wait and Workflow sources")
        val original = _workflow(Files.readString(_fixture_path))
        val workflow = _edge_workflow(original)

        When("the immutable source-free edge model is serialized to actual Scala and JSON products")
        val json = StateMachineWorkflowAbiGenerator.canonicalJson(Vector(workflow))
        val record = (Json.parse(json) \ "workflows").as[Vector[JsObject]].head
        val descriptor = _realm_string(StateMachineWorkflowAbiGenerator.generate(Vector(workflow)), s"${_root}/LifecycleEdgeWorkflowStateMachineWorkflow1.scala")

        Then("decoded strings, Long maxima and null source lines survive exactly")
        _expect_projection(record, workflow, descriptor)
        (record \ "waits").as[Vector[JsObject]].map(x => (x \ "identity").as[String]) shouldBe Vector(_escaped + "timer", _escaped + "signal")
        (record \ "waits").as[Vector[JsObject]].last.value("trigger") shouldBe Json.obj("kind" -> "SIGNAL", "identity" -> _escaped)
        (record \ "lifecyclePolicy" \ "source" \ "line").get shouldBe JsNull
        (record \ "source" \ "root" \ "line").get shouldBe JsNull
        (record \ "source" \ "definition" \ "line").get shouldBe JsNull
        descriptor should include("\"quote\\\"slash\\\\line\\nreturn\\rtab\\tcontrol\\u0001signal\"")
        descriptor should include("StateMachineWorkflowAbi.SourceIdentity(None)")
        descriptor should include("9223372036854775807L")
      }
    }

    "select one compatible schema for the complete ordered collection" which {
      "project mixed v1, v2 and v3 members completely and emit typed consumer compilation inputs" in {
        Given("actual parsed legacy, invocation-policy and lifecycle fixtures with six stable mixed-member identities")
        val legacy = _workflow(Files.readString(_legacy_path)).copy(identity = "LegacyWorkflow")
        val policy = _workflow(Files.readString(_policy_path)).copy(identity = "PolicyWorkflow")
        val lifecycle = _workflow(Files.readString(_fixture_path))
        val edge = _edge_workflow(lifecycle)
        val waitonly = lifecycle.copy(identity = "WaitOnlyWorkflow", invocationPolicies = Vector.empty, lifecyclePolicy = None)
        val deadlineonly = lifecycle.copy(identity = "DeadlineOnlyWorkflow", invocationPolicies = Vector.empty, waits = Vector.empty,
          lifecyclePolicy = lifecycle.lifecyclePolicy.map(_.copy(cancellation = None)))
        val cancellationonly = lifecycle.copy(identity = "CancellationOnlyWorkflow", invocationPolicies = Vector.empty, waits = Vector.empty,
          lifecyclePolicy = lifecycle.lifecyclePolicy.map(_.copy(deadlineMillis = None)))
        val workflows = Vector(legacy, policy, edge, waitonly, deadlineonly, cancellationonly)
        val names = Vector("LegacyWorkflow", "PolicyWorkflow", "LifecycleEdgeWorkflow", "WaitOnlyWorkflow", "DeadlineOnlyWorkflow", "CancellationOnlyWorkflow")
        val memberpaths = names.zipWithIndex.map { case (name, index) => s"${_root}/${name}StateMachineWorkflow${index + 1}.scala" }
        val paths = Vector(_abi_path, _bootstrap_path) ++ memberpaths
        val out = _base.resolve("target/test-generated/workflow-lifecycle-compile")

        When("the mixed collection is generated twice and its actual Realm products, sidecar and typed consumer are saved")
        val first = StateMachineWorkflowAbiGenerator.generate(workflows)
        val second = StateMachineWorkflowAbiGenerator.generate(workflows)
        val json = StateMachineWorkflowAbiGenerator.canonicalJson(workflows)
        val repeatedjson = StateMachineWorkflowAbiGenerator.canonicalJson(workflows)
        val records = (Json.parse(json) \ "workflows").as[Vector[JsObject]]
        paths.foreach { path => _write(out.resolve(path), _realm_string(first, path)) }
        _write(out.resolve(StateMachineWorkflowAbiGenerator.metadataPath), json)
        _write(out.resolve(s"${_root}/WorkflowLifecycleAbiConsumerProbe.scala"), _consumer_probe)
        val normalout = _base.resolve("target/test-generated/workflow-lifecycle-normal")
        _run("modeler-scala", _fixture_path, normalout)
        val normalabi = Files.readString(normalout.resolve(_abi_path))

        Then("one v3 ABI dominates all ordered members while every old field and optional declaration remains exact")
        json shouldBe repeatedjson
        paths.foreach { path =>
          _realm_string(first, path) shouldBe _realm_string(second, path)
          Files.readString(out.resolve(path)) shouldBe _realm_string(first, path)
        }
        Files.readString(out.resolve(StateMachineWorkflowAbiGenerator.metadataPath)) shouldBe json
        _realm_string(first, _abi_path) shouldBe normalabi
        (Json.parse(json) \ "schemaVersion").as[String] shouldBe "cozy.cml.statemachine-workflow-abi.v3"
        records.map(x => (x \ "identity").as[String]) shouldBe names
        records.zip(workflows).zip(memberpaths).foreach { case ((record, workflow), path) =>
          _expect_projection(record, workflow, _realm_string(first, path))
        }
        val references = names.zipWithIndex.map { case (name, index) => s"${name}StateMachineWorkflow${index + 1}.componentFactoryMetadata" }
        _realm_string(first, _bootstrap_path) shouldBe
          "package domain.statemachine.workflow\n\nobject StateMachineWorkflowComponentFactoryBootstrap {\n  val schemaVersion: String = \"cozy.cml.statemachine-workflow-bootstrap.v3\"\n  val componentFactoryMetadata: Vector[StateMachineWorkflowAbi.ComponentFactoryMetadata] = " + references.mkString("Vector(", ", ", ")") + "\n}\n"
        (records.head \ "invocationPolicies").as[Vector[JsValue]] shouldBe Vector.empty
        (records.head \ "lifecyclePolicy").get shouldBe JsNull
        (records.head \ "waits").as[Vector[JsValue]] shouldBe Vector.empty
        (records(1) \ "lifecyclePolicy").get shouldBe JsNull
        (records(1) \ "waits").as[Vector[JsValue]] shouldBe Vector.empty
        (records(3) \ "lifecyclePolicy").get shouldBe JsNull
        (records(4) \ "lifecyclePolicy" \ "cancellation").get shouldBe JsNull
        (records(5) \ "lifecyclePolicy" \ "deadlineMillis").get shouldBe JsNull
      }

      Vector("metadata-free" -> "cozy.cml.statemachine-workflow-abi.v1", "invocation-policy" -> "cozy.cml.statemachine-workflow-abi.v2").foreach { case (description, version) =>
        s"omit lifecycle declarations and fields for a $description collection" in {
          Given(s"an actual parsed $description fixture without lifecycle or waits")
          val workflow = _workflow(Files.readString(if (description == "metadata-free") _legacy_path else _policy_path))

          When("the old collection is projected to shared ABI, bootstrap, member and sidecar")
          val realm = StateMachineWorkflowAbiGenerator.generate(Vector(workflow))
          val json = Json.parse(StateMachineWorkflowAbiGenerator.canonicalJson(Vector(workflow)))
          val record = (json \ "workflows").as[Vector[JsObject]].head
          val descriptor = _realm_string(realm, s"${_root}/${workflow.identity}StateMachineWorkflow1.scala")

          Then("the original schema and field sequence contain no lifecycle extension")
          (json \ "schemaVersion").as[String] shouldBe version
          record.fields.map(_._1).toVector shouldBe Vector("identity", "version", "source", "states", "actions", "requiredSpi") ++
            (if (workflow.invocationPolicies.nonEmpty) Vector("invocationPolicies") else Vector.empty)
          _realm_string(realm, _abi_path) should not include "LifecyclePolicy"
          _realm_string(realm, _abi_path) should not include "WaitTrigger"
          _realm_string(realm, _abi_path) should not include "CancellationMode"
          descriptor should not include "lifecyclePolicy"
          descriptor should not include "waits ="
          descriptor should include(s"""schemaVersion = "$version"""")
          _realm_string(realm, _bootstrap_path) should include(version.replace("-abi.", "-bootstrap."))
        }
      }

      "retain exact v1 empty input and undeclared lifecycle data" in {
        Given("an empty normalized Workflow collection")
        val workflows = Vector.empty[WorkflowDefinition]
        val legacy = _workflow(Files.readString(_legacy_path))

        When("the empty collection and legacy collection emit their shared Scala ABI")
        val realm = StateMachineWorkflowAbiGenerator.generate(workflows)
        val legacyrealm = StateMachineWorkflowAbiGenerator.generate(Vector(legacy))
        val json = StateMachineWorkflowAbiGenerator.canonicalJson(workflows)

        Then("empty input retains the exact v1 sidecar, bootstrap and legacy shared ABI")
        json shouldBe "{\"schemaVersion\":\"cozy.cml.statemachine-workflow-abi.v1\",\"workflows\":[]}"
        _realm_string(realm, _abi_path) shouldBe _realm_string(legacyrealm, _abi_path)
        _realm_string(realm, _bootstrap_path) shouldBe "package domain.statemachine.workflow\n\nobject StateMachineWorkflowComponentFactoryBootstrap {\n  val schemaVersion: String = \"cozy.cml.statemachine-workflow-bootstrap.v1\"\n  val componentFactoryMetadata: Vector[StateMachineWorkflowAbi.ComponentFactoryMetadata] = Vector.empty\n}\n"
        StateMachineWorkflowAbiGenerator.schemaVersion shouldBe "cozy.cml.statemachine-workflow-abi.v1"
        StateMachineWorkflowAbiGenerator.bootstrapSchemaVersion shouldBe "cozy.cml.statemachine-workflow-bootstrap.v1"
        StateMachineWorkflowAbiGenerator.lifecycleSchemaVersion shouldBe "cozy.cml.statemachine-workflow-abi.v3"
        StateMachineWorkflowAbiGenerator.lifecycleBootstrapSchemaVersion shouldBe "cozy.cml.statemachine-workflow-bootstrap.v3"
      }
    }
  }

  private def _expect_projection(record: JsObject, workflow: WorkflowDefinition, descriptor: String): Unit = {
    record.fields.map(_._1).toVector shouldBe Vector("identity", "version", "source", "states", "actions", "requiredSpi", "invocationPolicies", "lifecyclePolicy", "waits")
    val previous = workflow.copy(lifecyclePolicy = None, waits = Vector.empty)
    val previousrecord = (Json.parse(StateMachineWorkflowAbiGenerator.canonicalJson(Vector(previous))) \ "workflows").as[Vector[JsObject]].head
    (record - "invocationPolicies" - "lifecyclePolicy" - "waits") shouldBe (previousrecord - "invocationPolicies")
    (record \ "invocationPolicies").get shouldBe previousrecord.value.getOrElse("invocationPolicies", Json.arr())
    val previousdescriptor = _realm_string(StateMachineWorkflowAbiGenerator.generate(Vector(previous)), s"${_root}/${workflow.identity}StateMachineWorkflow1.scala")
    val previousend = if (previous.invocationPolicies.nonEmpty) previousdescriptor.indexOf(",\n  invocationPolicies =") else previousdescriptor.indexOf("\n)")
    descriptor.substring(descriptor.indexOf("StateMachineWorkflowAbi.WorkflowDescriptor("), descriptor.indexOf(",\n  invocationPolicies ="))
      .replace("abi.v3", if (previous.invocationPolicies.nonEmpty) "abi.v2" else "abi.v1") shouldBe
      previousdescriptor.substring(previousdescriptor.indexOf("StateMachineWorkflowAbi.WorkflowDescriptor("), previousend)
    descriptor should include("schemaVersion = \"cozy.cml.statemachine-workflow-abi.v3\"")
    workflow.lifecyclePolicy match {
      case Some(policy) =>
        val projected = (record \ "lifecyclePolicy").as[JsObject]
        projected.fields.map(_._1).toVector shouldBe Vector("deadlineMillis", "cancellation", "source")
        projected shouldBe Json.obj("deadlineMillis" -> _long_json(policy.deadlineMillis),
          "cancellation" -> policy.cancellation.map[JsValue](x => Json.toJson(x.canonicalValue)).getOrElse[JsValue](JsNull),
          "source" -> _source_json(policy.source))
        val deadline = policy.deadlineMillis.map(x => s"Some(${x}L)").getOrElse("None")
        val cancellation = policy.cancellation.map(_ => "Some(StateMachineWorkflowAbi.CancellationMode.Cooperative)").getOrElse("None")
        descriptor should include(s"lifecyclePolicy = Some(StateMachineWorkflowAbi.LifecyclePolicy($deadline, $cancellation, ${_source_literal(policy.source)}))")
      case None =>
        (record \ "lifecyclePolicy").get shouldBe JsNull
        descriptor should include("lifecyclePolicy = None")
    }
    val waits = (record \ "waits").as[Vector[JsObject]]
    waits.size shouldBe workflow.waits.size
    val positions = workflow.waits.map { wait => descriptor.indexOf(s"StateMachineWorkflowAbi.WaitDeclaration(${_literal(wait.identity)},") }
    positions.foreach(_ should be >= 0)
    positions shouldBe positions.sorted
    waits.zip(workflow.waits).foreach { case (projected, wait) =>
      projected.fields.map(_._1).toVector shouldBe Vector("identity", "state", "stateIdentity", "trigger", "source")
      val (trigger, literal) = wait.trigger match {
        case WorkflowWaitTrigger.Timer(delay) =>
          Json.obj("kind" -> "TIMER", "delayMillis" -> JsNumber(BigDecimal(delay))) -> s"StateMachineWorkflowAbi.WaitTrigger.Timer(${delay}L)"
        case WorkflowWaitTrigger.Signal(identity) =>
          Json.obj("kind" -> "SIGNAL", "identity" -> identity) -> s"StateMachineWorkflowAbi.WaitTrigger.Signal(${_literal(identity)})"
      }
      (projected \ "trigger").as[JsObject].fields.map(_._1).toVector shouldBe trigger.fields.map(_._1).toVector
      projected shouldBe Json.obj("identity" -> wait.identity, "state" -> wait.state, "stateIdentity" -> wait.stateIdentity,
        "trigger" -> trigger, "source" -> _source_json(wait.source))
      descriptor should include(s"StateMachineWorkflowAbi.WaitDeclaration(${_literal(wait.identity)}, ${_literal(wait.state)}, ${_literal(wait.stateIdentity)}, $literal, ${_source_literal(wait.source)})")
    }
    if (workflow.waits.isEmpty) descriptor should include("waits = Vector.empty")
  }

  private def _edge_workflow(original: WorkflowDefinition): WorkflowDefinition = original.copy(
    identity = "LifecycleEdgeWorkflow", source = WorkflowSourceCorrelation(WorkflowSourceIdentity(None), WorkflowSourceIdentity(None)),
    lifecyclePolicy = Some(WorkflowLifecyclePolicy(Some(Long.MaxValue), Some(WorkflowCancellationMode.Cooperative), WorkflowSourceIdentity(None))),
    waits = Vector(
      original.waits.head.copy(identity = _escaped + "timer", state = _escaped + "declared", stateIdentity = _escaped + "canonical",
        trigger = WorkflowWaitTrigger.Timer(Long.MaxValue), source = WorkflowSourceIdentity(None)),
      original.waits.last.copy(identity = _escaped + "signal", trigger = WorkflowWaitTrigger.Signal(_escaped), source = WorkflowSourceIdentity(None))))

  private def _workflow(source: String): WorkflowDefinition = {
    val model = KaleidoxModel.parse(KaleidoxConfig.default, source)
    if (model.errors.nonEmpty) throw new IllegalArgumentException(s"CML fixture must parse without errors: ${model.errors.mkString(" | ")}")
    Modeler.ModelBuilder(model)
    CompositeStateMachineCml.workflowDefinitions(model).head
  }

  private def _paths(objectname: String): Vector[String] = Vector(_abi_path, _bootstrap_path,
    s"${_root}/$objectname.scala", StateMachineWorkflowAbiGenerator.metadataPath)

  private def _run(command: String, input: Path, out: Path): Unit =
    cozy.Cozy.main(Array(command, input.toString, "--save", out.toString))

  private def _products(out: Path, paths: Vector[String]): Map[String, String] =
    paths.map(path => path -> Files.readString(out.resolve(path))).toMap

  private def _product_bytes(out: Path, paths: Vector[String]): Map[String, Vector[Byte]] =
    paths.map(path => path -> Files.readAllBytes(out.resolve(path)).toVector).toMap

  private def _realm_string(realm: Realm, path: String): String =
    realm.get(path).collect { case value: Realm.StringData => value.string }.getOrElse(fail(s"Missing generated artifact: $path"))

  private def _write(path: Path, value: String): Unit = {
    Files.createDirectories(path.getParent)
    Files.writeString(path, value, UTF_8)
    ()
  }

  private def _line(source: String, heading: String): Int = source.linesIterator.toVector.indexOf(heading) + 1

  private def _literal(value: String): String = Json.stringify(Json.toJson(value))

  private def _long_json(value: Option[Long]): JsValue = value.map(x => JsNumber(BigDecimal(x))).getOrElse(JsNull)

  private def _source_json(value: WorkflowSourceIdentity): JsObject =
    Json.obj("line" -> value.line.map[JsValue](x => JsNumber(BigDecimal(x))).getOrElse[JsValue](JsNull))

  private def _source_literal(value: WorkflowSourceIdentity): String =
    s"StateMachineWorkflowAbi.SourceIdentity(${value.line.map(x => s"Some($x)").getOrElse("None")})"

  private val _consumer_probe = """package domain.statemachine.workflow
    |
    |import StateMachineWorkflowAbi._
    |
    |object WorkflowLifecycleAbiConsumerProbe {
    |  val source: WorkflowSourceCorrelation = WorkflowSourceCorrelation(SourceIdentity(None), SourceIdentity(None))
    |  val legacy: WorkflowDescriptor = WorkflowDescriptor("cozy.cml.statemachine-workflow-abi.v1", "legacy", "v1", source, Vector.empty, Vector.empty, Vector.empty)
    |  val policy: WorkflowDescriptor = WorkflowDescriptor("cozy.cml.statemachine-workflow-abi.v2", "policy", "v2", source, Vector.empty, Vector.empty, Vector.empty, Vector.empty)
    |  val legacyLifecycle: Option[LifecyclePolicy] = legacy.lifecyclePolicy
    |  val legacyWaits: Vector[WaitDeclaration] = legacy.waits
    |  val legacyInvocationPolicies: Vector[InvocationPolicy] = legacy.invocationPolicies
    |  val policyLifecycle: Option[LifecyclePolicy] = policy.lifecyclePolicy
    |  val policyWaits: Vector[WaitDeclaration] = policy.waits
    |  val policyInvocationPolicies: Vector[InvocationPolicy] = policy.invocationPolicies
    |  val edge: WorkflowDescriptor = LifecycleEdgeWorkflowStateMachineWorkflow3.workflow
    |  val deadline: Option[Long] = edge.lifecyclePolicy.flatMap(_.deadlineMillis)
    |  val timer: WaitTrigger = WaitTrigger.Timer(Long.MaxValue)
    |  val signal: WaitTrigger = WaitTrigger.Signal("payment.completed")
    |  val cancellation: CancellationMode = CancellationMode.Cooperative
    |  def waitData(trigger: WaitTrigger): Either[Long, String] = trigger match {
    |    case WaitTrigger.Timer(delay) => Left(delay)
    |    case WaitTrigger.Signal(identity) => Right(identity)
    |  }
    |  def cancellationData(mode: CancellationMode): String = mode match {
    |    case CancellationMode.Cooperative => CancellationMode.Cooperative.canonicalValue
    |  }
    |  val actualWaitData: Vector[Either[Long, String]] = edge.waits.map(wait => waitData(wait.trigger))
    |  val actualCancellationData: Option[String] = edge.lifecyclePolicy.flatMap(_.cancellation).map(cancellationData)
    |}
    |""".stripMargin
}
