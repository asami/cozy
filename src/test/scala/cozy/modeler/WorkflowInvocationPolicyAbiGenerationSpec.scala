package cozy.modeler

import java.nio.file.{Files, Path, Paths}
import java.security.MessageDigest
import org.goldenport.kaleidox.{Config => KaleidoxConfig, Model => KaleidoxModel}
import org.goldenport.realm.Realm
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import play.api.libs.json.{JsNull, JsNumber, JsObject, JsValue, Json}

/*
 * @since   Oct.  2, 2026
 * @version Oct.  2, 2026
 */
final class WorkflowInvocationPolicyAbiGenerationSpec extends AnyWordSpec with Matchers with GivenWhenThen with ModelerSpecSupport {
  private val _base = Paths.get(sys.props("user.dir")).toAbsolutePath.normalize()
  private val _root = "target/scala-3.3.8/src_managed/main/scala/domain/statemachine/workflow"
  private val _abi_path = s"${_root}/StateMachineWorkflowAbi.scala"
  private val _bootstrap_path = s"${_root}/StateMachineWorkflowComponentFactoryBootstrap.scala"
  private val _fixture_path = _base.resolve("src/test/resources/modeler/workflow-invocation-policy.cml")
  private val _legacy_path = _base.resolve("src/test/resources/modeler/workflow-invocation-policy-legacy.cml")
  // Independent S66.1 generated-product pins, frozen before the v2 producer edit.
  private val _expected_legacy_sidecar_sha256 = "c1ec6d672e76f060b1047880e50db38627ea54f80b8833e1f7a590c06ea79626"
  private val _expected_legacy_workflow_sha256 = "6d9c0fdebe1c4b262e15d610337faf854001fb2591a54f1306bfbf8a293bd847"
  private val _expected_legacy_abi_sha256 = "8e3f80129d3c547825bfce7e786806dfd7dc128419158cb409ddb2ab9080b623"
  private val _expected_legacy_bootstrap_sha256 = "94b2ac0dc5aaa47a5f4d920b4dc4038c727d6eb87182284cb71311ccf81d065c"

  "Workflow invocation policy generated ABI" should {
    "describe real source through both public Scala routes" which {
      "retain deterministic v2 products and complete located source metadata" in {
        Given("the tracked two-Action CML with Action and Participant invocation policies")
        val source = Files.readString(_fixture_path)
        val workflow = _workflow(source)
        val normalout = _base.resolve("target/test-generated/workflow-invocation-policy-normal")
        val valueout = _base.resolve("target/test-generated/workflow-invocation-policy-value")
        val paths = Vector(_abi_path, _bootstrap_path,
          s"${_root}/InvocationControlledWorkflowStateMachineWorkflow1.scala", StateMachineWorkflowAbiGenerator.metadataPath)

        When("each public route generates the tracked source twice")
        _run("modeler-scala", _fixture_path, normalout)
        val normalfirst = _products(normalout, paths)
        _run("modeler-scala", _fixture_path, normalout)
        val normalsecond = _products(normalout, paths)
        _run("modeler-scala-value", _fixture_path, valueout)
        val valuefirst = _products(valueout, paths)
        _run("modeler-scala-value", _fixture_path, valueout)
        val valuesecond = _products(valueout, paths)
        val generated = StateMachineWorkflowAbiGenerator.generate(Vector(workflow))
        val json = Json.parse(normalfirst(StateMachineWorkflowAbiGenerator.metadataPath))
        val record = (json \ "workflows").as[Vector[JsObject]].head
        val descriptor = normalfirst(paths(2))

        Then("all four products agree between routes and repeated generation")
        normalfirst shouldBe normalsecond
        normalfirst shouldBe valuefirst
        valuefirst shouldBe valuesecond
        paths.filterNot(_ == StateMachineWorkflowAbiGenerator.metadataPath).foreach { path =>
          normalfirst(path) shouldBe _realm_string(generated, path)
        }
        normalfirst(StateMachineWorkflowAbiGenerator.metadataPath) shouldBe StateMachineWorkflowAbiGenerator.canonicalJson(Vector(workflow))
        (json \ "schemaVersion").as[String] shouldBe StateMachineWorkflowAbiGenerator.policySchemaVersion
        normalfirst(_abi_path) should include("val VERSION: String = \"cozy.cml.statemachine-workflow-abi.v2\"")
        normalfirst(_abi_path) should include("sealed trait InvocationTarget { def identity: String }")
        normalfirst(_abi_path) should include("final case class Action(identity: String) extends InvocationTarget")
        normalfirst(_abi_path) should include("final case class Participant(identity: String) extends InvocationTarget")
        normalfirst(_abi_path) should include("final case class RetryPolicy(maximumAttempts: Int, fixedRetryDelayMillis: Long)")
        normalfirst(_abi_path) should include("actionIdentity: String, retry: Option[RetryPolicy],")
        normalfirst(_abi_path) should include("executionTimeoutMillis: Option[Long], source: SourceIdentity)")
        normalfirst(_abi_path) should include("invocationPolicies: Vector[InvocationPolicy] = Vector.empty")
        normalfirst(_bootstrap_path) should include("cozy.cml.statemachine-workflow-bootstrap.v2")
        normalfirst(_bootstrap_path) should include("Vector(InvocationControlledWorkflowStateMachineWorkflow1.componentFactoryMetadata)")
        descriptor should include("schemaVersion = \"cozy.cml.statemachine-workflow-abi.v2\"")
        record.fields.map(_._1).toVector shouldBe Vector("identity", "version", "source", "states", "actions", "requiredSpi", "invocationPolicies")
        _expect_policies(record, workflow, descriptor)
        workflow.invocationPolicies.map(_.identity) shouldBe Vector("capture-control", "check-control")
        workflow.invocationPolicies.foreach { policy =>
          policy.source.line shouldBe Some(source.linesIterator.toVector.indexOf(s"#### ${policy.identity}") + 1)
        }

        And("adding policy metadata preserves every established descriptor projection")
        val legacyworkflow = workflow.copy(invocationPolicies = Vector.empty)
        val oldrecord = (Json.parse(StateMachineWorkflowAbiGenerator.canonicalJson(Vector(legacyworkflow))) \ "workflows").as[Vector[JsObject]].head
        (record - "invocationPolicies") shouldBe oldrecord
        val olddescriptor = _realm_string(StateMachineWorkflowAbiGenerator.generate(Vector(legacyworkflow)), paths(2))
        descriptor.take(descriptor.indexOf(",\n  invocationPolicies =")).replace("abi.v2", "abi.v1") shouldBe
          olddescriptor.take(olddescriptor.indexOf("\n)"))
      }
    }

    "project optional numeric declarations without defaults" which {
      Vector(
        ("retry only", "maximum-attempts = 3\nfixed-retry-delay-millis = 250", Some(WorkflowRetryPolicy(3, 250L)), None),
        ("timeout only at one millisecond", "execution-timeout-millis = 1", None, Some(1L)),
        ("both declarations", "maximum-attempts = 3\nfixed-retry-delay-millis = 250\nexecution-timeout-millis = 30000", Some(WorkflowRetryPolicy(3, 250L)), Some(30000L)),
        ("one attempt and zero delay", "maximum-attempts = 1\nfixed-retry-delay-millis = 0", Some(WorkflowRetryPolicy(1, 0L)), None),
        ("maximum Int and Long values", "maximum-attempts = 2147483647\nfixed-retry-delay-millis = 9223372036854775807\nexecution-timeout-millis = 9223372036854775807", Some(WorkflowRetryPolicy(Int.MaxValue, Long.MaxValue)), Some(Long.MaxValue)),
        ("timeout only at Long maximum", "execution-timeout-millis = 9223372036854775807", None, Some(Long.MaxValue))
      ).foreach { case (description, fields, retry, timeout) =>
        s"retain $description as exact integers and Scala expressions" in {
          Given(s"a real legacy CML variant with $description")
          val source = _with_policy(Files.readString(_legacy_path), "capture-control", "action = capture-payment", fields)
          val workflow = _workflow(source)

          When("the normalized source is projected twice")
          val first = StateMachineWorkflowAbiGenerator.canonicalJson(Vector(workflow))
          val second = StateMachineWorkflowAbiGenerator.canonicalJson(Vector(workflow))
          val scala = _realm_string(StateMachineWorkflowAbiGenerator.generate(Vector(workflow)), s"${_root}/OrderProgressStateMachineWorkflow1.scala")
          val record = (Json.parse(first) \ "workflows").as[Vector[JsObject]].head

          Then("presence, absence and boundary values survive without numeric conversion")
          first shouldBe second
          workflow.invocationPolicies.head.retry shouldBe retry
          workflow.invocationPolicies.head.executionTimeoutMillis shouldBe timeout
          _expect_policies(record, workflow, scala)
          if (timeout.contains(Long.MaxValue) || retry.exists(_.fixedRetryDelayMillis == Long.MaxValue)) {
            first should include("9223372036854775807")
            scala should include("9223372036854775807L")
          }
        }
      }
    }

    "retain declared targets independently of resolved Actions" which {
      Vector("action = CAPTURE_payment" -> "ACTION", "participant = CAPTURE_payment_CAPABILITY" -> "PARTICIPANT").foreach { case (target, kind) =>
        s"preserve the $kind alias spelling" in {
          Given("valid located CML with a case and separator alias of an invocation target")
          val workflow = _workflow(_with_policy(Files.readString(_legacy_path), "capture-control", target, "execution-timeout-millis = 1"))

          When("the source model is rendered into Scala and canonical JSON")
          val record = (Json.parse(StateMachineWorkflowAbiGenerator.canonicalJson(Vector(workflow))) \ "workflows").as[Vector[JsObject]].head
          val scala = _realm_string(StateMachineWorkflowAbiGenerator.generate(Vector(workflow)), s"${_root}/OrderProgressStateMachineWorkflow1.scala")

          Then("the target kind and declared alias remain separate from capture-payment")
          (record \ "invocationPolicies").as[Vector[JsObject]].head.value("target") shouldBe
            Json.obj("kind" -> kind, "identity" -> target.split(" = ")(1))
          workflow.invocationPolicies.head.actionIdentity shouldBe "capture-payment"
          _expect_policies(record, workflow, scala)
        }
      }

      "project Judgment metadata while retaining Candidate and local Admission semantics" in {
        Given("the established Candidate-Admission CML with a policy on its Judgment")
        val source = Files.readString(_base.resolve("src/test/resources/modeler/candidate-admission-workflow.cml"))
        val workflow = _workflow(_with_policy(source, "judgment-control", "participant = capture-payment-capability", "maximum-attempts = 1\nfixed-retry-delay-millis = 0"))

        When("the Judgment invocation policy is projected")
        val record = (Json.parse(StateMachineWorkflowAbiGenerator.canonicalJson(Vector(workflow))) \ "workflows").as[Vector[JsObject]].head
        val scala = _realm_string(StateMachineWorkflowAbiGenerator.generate(Vector(workflow)), s"${_root}/OrderProgressStateMachineWorkflow1.scala")

        Then("only the Judgment has policy metadata and the original Actions retain their kinds")
        workflow.invocationPolicies.map(_.actionIdentity) shouldBe Vector("judge-payment")
        (record \ "actions").as[Vector[JsObject]].map(x => (x \ "kind").as[String]) shouldBe Vector("JUDGMENT", "ADMISSION")
        workflow.compositeStateMachine shouldBe _workflow(source).compositeStateMachine
        _expect_policies(record, workflow, scala)
      }

      "escape policy and declared target strings with optional source correlation" in {
        Given("a located valid model copied to represent strings outside the CML heading grammar")
        val original = _workflow(Files.readString(_fixture_path))
        val escaped = "quote\"slash\\line\nreturn\rtab\tcontrol\u0001"
        val action = original.compositeStateMachine.actions.head.copy(identity = escaped)
        val policy = original.invocationPolicies.head.copy(identity = escaped,
          target = WorkflowInvocationTarget.Action(escaped), actionIdentity = escaped,
          source = WorkflowSourceIdentity(None))
        val workflow = original.copy(
          compositeStateMachine = original.compositeStateMachine.copy(actions = Vector(action)),
          requiredOperations = Vector(original.requiredOperations.head.copy(action = action)),
          invocationPolicies = Vector(policy))

        When("the normalized model copy is rendered to generated products")
        val json = StateMachineWorkflowAbiGenerator.canonicalJson(Vector(workflow))
        val record = (Json.parse(json) \ "workflows").as[Vector[JsObject]].head
        val scala = _realm_string(StateMachineWorkflowAbiGenerator.generate(Vector(workflow)), s"${_root}/InvocationControlledWorkflowStateMachineWorkflow1.scala")

        Then("JSON preserves the exact strings and Scala uses escaped string literals")
        val projected = (record \ "invocationPolicies").as[Vector[JsObject]].head
        (projected \ "identity").as[String] shouldBe escaped
        (projected \ "target" \ "identity").as[String] shouldBe escaped
        (projected \ "source" \ "line").get shouldBe JsNull
        scala should include("\"quote\\\"slash\\\\line\\nreturn\\rtab\\tcontrol\\u0001\"")
        scala should include("StateMachineWorkflowAbi.SourceIdentity(None)")
        _expect_policies(record, workflow, scala)
      }
    }

    "select one schema for a complete input collection" which {
      "emit one shared v2 ABI for mixed policy and metadata-free members" in {
        Given("a policy Workflow followed by a distinct metadata-free Workflow")
        val controlled = _workflow(Files.readString(_fixture_path))
        val legacy = _workflow(Files.readString(_legacy_path)).copy(identity = "LegacyWorkflow")
        val workflows = Vector(controlled, legacy)

        When("the mixed collection is projected twice")
        val first = StateMachineWorkflowAbiGenerator.generate(workflows)
        val second = StateMachineWorkflowAbiGenerator.generate(workflows)
        val json = StateMachineWorkflowAbiGenerator.canonicalJson(workflows)
        val records = (Json.parse(json) \ "workflows").as[Vector[JsObject]]
        val controlledscala = _realm_string(first, s"${_root}/InvocationControlledWorkflowStateMachineWorkflow1.scala")
        val legacyscala = _realm_string(first, s"${_root}/LegacyWorkflowStateMachineWorkflow2.scala")

        Then("all descriptors share v2 and empty policies add no control defaults")
        (Json.parse(json) \ "schemaVersion").as[String] shouldBe StateMachineWorkflowAbiGenerator.policySchemaVersion
        count_token(_realm_string(first, _abi_path), "object StateMachineWorkflowAbi {") shouldBe 1
        _realm_string(first, _abi_path) shouldBe _realm_string(second, _abi_path)
        _realm_string(first, _bootstrap_path) shouldBe _realm_string(second, _bootstrap_path)
        _realm_string(first, _bootstrap_path) should include("Vector(InvocationControlledWorkflowStateMachineWorkflow1.componentFactoryMetadata, LegacyWorkflowStateMachineWorkflow2.componentFactoryMetadata)")
        _realm_string(first, _bootstrap_path) should include(StateMachineWorkflowAbiGenerator.policyBootstrapSchemaVersion)
        Vector(controlledscala, legacyscala).foreach(_ should include("schemaVersion = \"cozy.cml.statemachine-workflow-abi.v2\""))
        legacyscala should include("invocationPolicies = Vector.empty")
        (records(1) \ "invocationPolicies").as[Vector[JsValue]] shouldBe Vector.empty
        _expect_policies(records.head, controlled, controlledscala)
        json shouldBe StateMachineWorkflowAbiGenerator.canonicalJson(workflows)
        controlledscala shouldBe _realm_string(second, s"${_root}/InvocationControlledWorkflowStateMachineWorkflow1.scala")
        legacyscala shouldBe _realm_string(second, s"${_root}/LegacyWorkflowStateMachineWorkflow2.scala")
      }

      "retain four independently frozen v1 product hashes through both public routes" in {
        Given("the tracked exact legacy CML and named pre-change generated-product hashes")
        val expected = Map(StateMachineWorkflowAbiGenerator.metadataPath -> _expected_legacy_sidecar_sha256,
          s"${_root}/OrderProgressStateMachineWorkflow1.scala" -> _expected_legacy_workflow_sha256,
          _abi_path -> _expected_legacy_abi_sha256, _bootstrap_path -> _expected_legacy_bootstrap_sha256)
        val outputs = Vector("modeler-scala", "modeler-scala-value").map { command =>
          command -> _base.resolve(s"target/test-generated/workflow-invocation-policy-legacy-$command")
        }

        When("both public routes generate the metadata-free source")
        outputs.foreach { case (command, out) => _run(command, _legacy_path, out) }
        val products = outputs.map { case (_, out) => _products(out, expected.keys.toVector) }

        Then("every product has its independent original UTF-8 SHA-256 and no new declarations or keys")
        products.head shouldBe products(1)
        outputs.foreach { case (_, out) =>
          expected.foreach { case (path, hash) => _sha256(Files.readAllBytes(out.resolve(path))) shouldBe hash }
        }
        products.head(_abi_path) should not include "InvocationPolicy"
        products.head(_abi_path) should not include "InvocationTarget"
        products.head(_abi_path) should not include "RetryPolicy"
        products.head(StateMachineWorkflowAbiGenerator.metadataPath) should not include "invocationPolicies"
      }

      "retain the v1 schema and exact empty bootstrap for empty normalized input" in {
        Given("an empty normalized Workflow collection")
        val workflows = Vector.empty[WorkflowDefinition]

        When("empty Scala artifacts and the canonical JSON sidecar are generated")
        val realm = StateMachineWorkflowAbiGenerator.generate(workflows)
        val json = StateMachineWorkflowAbiGenerator.canonicalJson(workflows)
        val abi = _realm_string(realm, _abi_path)
        val bootstrap = _realm_string(realm, _bootstrap_path)

        Then("empty input uses the original ABI bytes, JSON shape and bootstrap bytes")
        _sha256(abi.getBytes(java.nio.charset.StandardCharsets.UTF_8)) shouldBe _expected_legacy_abi_sha256
        json shouldBe "{\"schemaVersion\":\"cozy.cml.statemachine-workflow-abi.v1\",\"workflows\":[]}"
        bootstrap shouldBe "package domain.statemachine.workflow\n\nobject StateMachineWorkflowComponentFactoryBootstrap {\n  val schemaVersion: String = \"cozy.cml.statemachine-workflow-bootstrap.v1\"\n  val componentFactoryMetadata: Vector[StateMachineWorkflowAbi.ComponentFactoryMetadata] = Vector.empty\n}\n"
        abi should not include "invocationPolicies"
        StateMachineWorkflowAbiGenerator.schemaVersion shouldBe "cozy.cml.statemachine-workflow-abi.v1"
        StateMachineWorkflowAbiGenerator.bootstrapSchemaVersion shouldBe "cozy.cml.statemachine-workflow-bootstrap.v1"
      }
    }
  }

  private def _expect_policies(record: JsObject, workflow: WorkflowDefinition, scala: String): Unit = {
    val policies = (record \ "invocationPolicies").as[Vector[JsObject]]
    policies.size shouldBe workflow.invocationPolicies.size
    val positions = workflow.invocationPolicies.map { policy =>
      scala.indexOf(s"StateMachineWorkflowAbi.InvocationPolicy(${Json.stringify(Json.toJson(policy.identity))},")
    }
    positions.foreach(_ should be >= 0)
    positions shouldBe positions.sorted
    policies.zip(workflow.invocationPolicies).foreach { case (json, policy) =>
      json.fields.map(_._1).toVector shouldBe Vector("identity", "target", "actionIdentity", "retry", "executionTimeoutMillis", "source")
      (json \ "identity").as[String] shouldBe policy.identity
      (json \ "actionIdentity").as[String] shouldBe policy.actionIdentity
      val (kind, constructor) = policy.target match {
        case _: WorkflowInvocationTarget.Action => "ACTION" -> "Action"
        case _: WorkflowInvocationTarget.Participant => "PARTICIPANT" -> "Participant"
      }
      val target = (json \ "target").as[JsObject]
      target.fields.map(_._1).toVector shouldBe Vector("kind", "identity")
      target shouldBe Json.obj("kind" -> kind, "identity" -> policy.target.identity)
      scala should include(s"StateMachineWorkflowAbi.InvocationTarget.$constructor(${Json.stringify(Json.toJson(policy.target.identity))})")
      policy.retry match {
        case Some(retry) =>
          val retryjson = (json \ "retry").as[JsObject]
          retryjson.fields.map(_._1).toVector shouldBe Vector("maximumAttempts", "fixedRetryDelayMillis")
          (retryjson \ "maximumAttempts").get shouldBe JsNumber(BigDecimal(retry.maximumAttempts))
          (retryjson \ "fixedRetryDelayMillis").get shouldBe JsNumber(BigDecimal(retry.fixedRetryDelayMillis))
          scala should include(s"Some(StateMachineWorkflowAbi.RetryPolicy(${retry.maximumAttempts}, ${retry.fixedRetryDelayMillis}L))")
        case None =>
          (json \ "retry").get shouldBe JsNull
      }
      (json \ "executionTimeoutMillis").get shouldBe policy.executionTimeoutMillis.map(x => JsNumber(BigDecimal(x))).getOrElse(JsNull)
      (json \ "source" \ "line").get shouldBe policy.source.line.map(x => JsNumber(BigDecimal(x))).getOrElse(JsNull)
      val timeout = policy.executionTimeoutMillis.map(x => s"Some(${x}L)").getOrElse("None")
      val line = policy.source.line.map(x => s"Some($x)").getOrElse("None")
      scala should include(s", $timeout, StateMachineWorkflowAbi.SourceIdentity($line))")
      if (policy.retry.isEmpty) scala should include(s", None, $timeout, StateMachineWorkflowAbi.SourceIdentity($line))")
    }
  }

  private def _workflow(source: String): WorkflowDefinition = {
    val model = KaleidoxModel.parse(KaleidoxConfig.default, source)
    model.errors shouldBe empty
    Modeler.ModelBuilder(model)
    CompositeStateMachineCml.workflowDefinitions(model).head
  }

  private def _with_policy(source: String, identity: String, target: String, fields: String): String =
    source.replace("# STATE-MACHINE", s"### INVOCATION-POLICY\n\n#### $identity\n\n$target\n$fields\n\n# STATE-MACHINE")

  private def _run(command: String, input: Path, out: Path): Unit =
    cozy.Cozy.main(Array(command, input.toString, "--save", out.toString))

  private def _products(out: Path, paths: Vector[String]): Map[String, String] =
    paths.map(path => path -> Files.readString(out.resolve(path))).toMap

  private def _realm_string(realm: Realm, path: String): String =
    realm.get(path).collect { case value: Realm.StringData => value.string }.getOrElse(fail(s"Missing generated artifact: $path"))

  private def _sha256(bytes: Array[Byte]): String =
    MessageDigest.getInstance("SHA-256").digest(bytes).map(byte => f"${byte & 0xff}%02x").mkString
}
