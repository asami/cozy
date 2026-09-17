package cozy.modeler

import java.nio.file.Files
import java.nio.file.Paths
import org.goldenport.kaleidox.{Config => KaleidoxConfig, Model => KaleidoxModel}
import org.goldenport.realm.Realm
import org.scalacheck.{Gen, Prop, Test}
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Sep. 17, 2026
 * @version Sep. 17, 2026
 * @author  ASAMI, Tomoharu
 */
final class SkillDrivenWorkflowProducerFixtureSpec extends AnyWordSpec with Matchers with GivenWhenThen with ModelerSpecSupport {
  "The checked-in Workflow producer fixture" should {
    "render the frozen StateMachine API/SPI boundary" which {
      "produce byte-identical ABI evidence through two isolated modeler-scala generations" in {
        Given("the checked-in structural WORKFLOW fixture with four ordered actions and one typed Required SPI operation")
        val base = Paths.get(sys.props("user.dir")).toAbsolutePath.normalize()
        val input = base.resolve("src/test/resources/modeler/skill-driven-workflow-producer.cml")
        val firstout = base.resolve("target/test-generated/skill-driven-workflow-producer-first")
        val secondout = base.resolve("target/test-generated/skill-driven-workflow-producer-second")
        delete_recursively(firstout)
        delete_recursively(secondout)
        val model = _model(Files.readString(input))
        val workflow = CompositeStateMachineCml.workflowDefinitions(model).head
        workflow.compositeStateMachine.actions.map(_.identity) shouldBe Vector(
          "BuildProject",
          "RunTests",
          "ReviewChange",
          "CommitChanges"
        )
        workflow.requiredOperations.map(_.action.identity) shouldBe Vector("ReviewChange")

        When("the real modeler-scala route renders the fixture into each isolated target root")
        run_modeler_scala(input, firstout)
        run_modeler_scala(input, secondout)

        Then("the complete generated trees and canonical sidecars are byte-identical")
        val firstsnapshot = tree_snapshot(firstout)
        val secondsnapshot = tree_snapshot(secondout)
        firstsnapshot should not be empty
        firstsnapshot shouldBe secondsnapshot
        val firstjson = firstout.resolve(StateMachineWorkflowAbiGenerator.metadataPath)
        val secondjson = secondout.resolve(StateMachineWorkflowAbiGenerator.metadataPath)
        Files.readAllBytes(firstjson).toVector shouldBe Files.readAllBytes(secondjson).toVector

        And("the generated ABI and bootstrap retain stable schema versions")
        val root = "target/scala-3.3.8/src_managed/main/scala/domain/statemachine/workflow"
        val abi = Files.readString(firstout.resolve(root).resolve("StateMachineWorkflowAbi.scala"))
        val bootstrap = Files.readString(firstout.resolve(root).resolve("StateMachineWorkflowComponentFactoryBootstrap.scala"))
        abi should include("val VERSION: String = \"cozy.cml.statemachine-workflow-abi.v1\"")
        bootstrap should include("val schemaVersion: String = \"cozy.cml.statemachine-workflow-bootstrap.v1\"")

        And("the generated Workflow descriptor retains ReviewChange identity, typed input/result, and source provenance")
        val requiredoperation = _api(workflow).requiredOperations.head
        requiredoperation.identity shouldBe StateMachineRequiredOperationIdentity("review-change-capability")
        requiredoperation.actionIdentity shouldBe "ReviewChange"
        requiredoperation.operation shouldBe StateMachineOperationIdentity("WorkflowService", "reviewChange")
        requiredoperation.inputType shouldBe Some(StateMachineInputTypeReference("ReviewContext"))
        requiredoperation.resultType shouldBe Some(StateMachineResultTypeReference("ReviewResult"))
        val locatedworkflow = CompositeStateMachineCml.workflowDefinitions(_model_with_location(Files.readString(input))).head
        locatedworkflow.source.root.line should not be empty
        locatedworkflow.source.definition.line should not be empty
        locatedworkflow.requiredOperations.head.source.line should not be empty
        locatedworkflow.requiredOperations.head.action.source.line should not be empty
        val workflowcontent = Files.readString(firstout.resolve(root).resolve("WorkflowProducerStateMachineWorkflow1.scala"))
        workflowcontent should include("StateMachineWorkflowAbi.Action(\"ReviewChange\", \"OPERATION\"")
        workflowcontent should include("StateMachineWorkflowAbi.Operation(\"WorkflowService\", \"reviewChange\", Some(\"ReviewContext\"), Some(\"ReviewResult\")")
        workflowcontent should include("StateMachineWorkflowAbi.WorkflowRequiredOperationDescriptor(StateMachineWorkflowAbi.StateMachineRequiredOperationIdentity(\"review-change-capability\"), \"ReviewChange\", StateMachineWorkflowAbi.StateMachineOperationIdentity(\"WorkflowService\", \"reviewChange\"), Some(StateMachineWorkflowAbi.StateMachineInputTypeReference(\"ReviewContext\")), Some(StateMachineWorkflowAbi.StateMachineResultTypeReference(\"ReviewResult\")),")
        workflowcontent should include("WorkflowSourceCorrelation(StateMachineWorkflowAbi.SourceIdentity(Some(")
        Files.readString(firstjson) should include("\"capability\":\"review-change-capability\"")
        Files.readString(firstjson) should include("\"actionIdentity\":\"ReviewChange\"")
        Files.readString(firstjson) should include("\"inputType\":\"ReviewContext\"")
        Files.readString(firstjson) should include("\"resultType\":\"ReviewResult\"")

        And("the generated boundary contains only the frozen provider and SPI vocabulary")
        val generated = abi + bootstrap + workflowcontent + Files.readString(firstjson)
        generated should not include "InvocationBinding"
        generated should not include "ORCHESTRATION"
        generated should not include "RUNTIME"
        generated should not include "REST"
        generated should not include "transport"
        generated should not include "Skill"

        And("repeated pure ABI and sidecar generation remains deterministic under ScalaCheck")
        val normalizedworkflow = CompositeStateMachineCml.workflowDefinitions(_model(Files.readString(input))).head
        val repetitions = Gen.chooseNum(1, 12)
        val property = Prop.forAll(repetitions) { count =>
          val sources = Vector.fill(count)(StateMachineWorkflowAbiGenerator.generate(Vector(normalizedworkflow)))
            .map(_realm_string(_, s"$root/WorkflowProducerStateMachineWorkflow1.scala"))
          val sidecars = Vector.fill(count)(StateMachineWorkflowAbiGenerator.canonicalJson(Vector(normalizedworkflow)))
          sources.distinct.size == 1 && sidecars.distinct.size == 1
        }
        Test.check(Test.Parameters.default.withMinSuccessfulTests(24), property).passed shouldBe true
      }
    }

    "drive provider outcomes through the Required SPI" which {
      "suspend ReviewChange and resume matching typed evidence into the same terminal state" in {
        Given("the normalized Workflow, its sole ReviewChange Required SPI descriptor, and an external suspending Provider")
        val workflow = _workflow()
        val api = _api(workflow)
        val requiredoperation = api.requiredOperations.head
        val provider = _suspending_provider
        val providerfor = _provider_selection(requiredoperation.identity, provider)

        When("the fixture runner selects the Provider only by Required SPI identity")
        val suspended = _run(workflow, api, providerfor)

        Then("BuildProject and RunTests complete locally while ReviewChange remains durably suspended")
        suspended.completedactions shouldBe Vector("BuildProject", "RunTests")
        suspended.localexecutions shouldBe Vector(
          "BuildProject" -> ActionExecution.Completed(_build_result),
          "RunTests" -> ActionExecution.Completed(_run_tests_result)
        )
        suspended.suspendedcontinuation should not be empty
        suspended.terminated shouldBe false

        And("a matching typed ContinuationResult validates and commits the final action")
        val continuation = suspended.suspendedcontinuation.get
        val result = _continuation_result(continuation)
        val resumed = _resume(suspended, result)
        resumed._2 shouldBe ContinuationResumeValidation.Accepted(result)
        resumed._1 shouldBe FixtureRunState(
          Vector("BuildProject", "RunTests", "ReviewChange", "CommitChanges"),
          Vector(
            "BuildProject" -> ActionExecution.Completed(_build_result),
            "RunTests" -> ActionExecution.Completed(_run_tests_result),
            "CommitChanges" -> ActionExecution.Completed(_commit_result)
          ),
          None,
          true
        )
      }

      "reject stale revision or context snapshot evidence without advancing the suspended run" in {
        Given("a suspended ReviewChange continuation and a matching typed completion result")
        val workflow = _workflow()
        val api = _api(workflow)
        val requiredoperation = api.requiredOperations.head
        val suspended = _run(workflow, api, _provider_selection(requiredoperation.identity, _suspending_provider))
        val continuation = suspended.suspendedcontinuation.get
        val result = _continuation_result(continuation)

        When("the suspended run receives one stale revision and one stale ContextSnapshot result")
        val stale_revision = _resume(suspended, result.copy(expectedRevision = StateMachineRevision("stale-revision")))
        val stale_snapshot = _resume(suspended, result.copy(
          contextSnapshot = result.contextSnapshot.copy(workflowRevision = "stale-workflow")
        ))

        Then("both mismatches fail closed and leave the runner suspended")
        stale_revision._2 shouldBe ContinuationResumeValidation.Rejected(
          ContinuationResumeRejection.ExpectedRevisionMismatch(
            StateMachineRevision("1"),
            StateMachineRevision("stale-revision")
          )
        )
        stale_snapshot._2 shouldBe ContinuationResumeValidation.Rejected(
          ContinuationResumeRejection.ContextSnapshotMismatch(
            _context_snapshot,
            _context_snapshot.copy(workflowRevision = "stale-workflow")
          )
        )
        stale_revision._1 shouldBe suspended
        stale_snapshot._1 shouldBe suspended
      }

      "complete ReviewChange directly when the same Required SPI selects a deterministic Provider" in {
        Given("the normalized Workflow and a deterministic Provider bound to the same ReviewChange Required SPI identity")
        val workflow = _workflow()
        val api = _api(workflow)
        val requiredoperation = api.requiredOperations.head
        val provider = _deterministic_provider

        When("the fixture runner executes the four declared actions with the deterministic Provider")
        val completed = _run(workflow, api, _provider_selection(requiredoperation.identity, provider))

        Then("ReviewChange completes directly and reaches the same terminal action state as resumed execution")
        completed shouldBe FixtureRunState(
          Vector("BuildProject", "RunTests", "ReviewChange", "CommitChanges"),
          Vector(
            "BuildProject" -> ActionExecution.Completed(_build_result),
            "RunTests" -> ActionExecution.Completed(_run_tests_result),
            "CommitChanges" -> ActionExecution.Completed(_commit_result)
          ),
          None,
          true
        )
      }
    }
  }

  private final case class FixtureRunState(
    completedactions: Vector[String],
    localexecutions: Vector[(String, ActionExecution.Completed)],
    suspendedcontinuation: Option[Continuation],
    terminated: Boolean
  )

  private def _workflow(): WorkflowDefinition = {
    val base = Paths.get(sys.props("user.dir")).toAbsolutePath.normalize()
    val input = base.resolve("src/test/resources/modeler/skill-driven-workflow-producer.cml")
    CompositeStateMachineCml.workflowDefinitions(_model(Files.readString(input))).head
  }

  private def _api(workflow: WorkflowDefinition): StateMachineApiSpi =
    StateMachineApiSpi.fromWorkflow(workflow, Vector.empty, _required_operation_metadata)

  private def _required_operation_metadata(
    required: WorkflowRequiredOperation
  ): StateMachineRequiredOperationMetadata =
    _required_operation_metadata_value

  private val _context_snapshot = ContextSnapshot(
    workflowRevision = "workflow-producer-v1",
    modelRevision = Some("model-1"),
    workspaceRevision = Some("workspace-1"),
    evidenceRevision = Some("evidence-1")
  )

  private val _review_context_reference = ContextReference("review-context", "1")

  private val _context_bundle = ContextBundle(
    summary = "Review the current change before committing it.",
    requiredFacts = Vector(ContextReference("change", "1")),
    references = Vector(_review_context_reference),
    snapshot = _context_snapshot
  )

  private val _context_contract = ContextContract(
    "review-context",
    Vector(ContextReference("change", "1")),
    Vector(_review_context_reference)
  )

  private val _completion_contract = CompletionContract(
    "review-completed",
    Vector(ContextReference("review-result", "1"))
  )

  private val _evidence_contract = EvidenceContract(
    "review-evidence",
    Vector(ContextReference("review-result", "1"))
  )

  private val _required_operation_metadata_value = StateMachineRequiredOperationMetadata(
    _context_contract,
    _completion_contract,
    _evidence_contract,
    Vector(StateMachineConstraint("review-policy", "required"))
  )

  private val _review_result = StateMachineOperationResult(
    StateMachineResultTypeReference("ReviewResult"),
    ContextReference("review-result", "1")
  )

  private val _build_result = StateMachineOperationResult(
    StateMachineResultTypeReference("BuildResult"),
    ContextReference("build-result", "1")
  )

  private val _run_tests_result = StateMachineOperationResult(
    StateMachineResultTypeReference("RunTestsResult"),
    ContextReference("run-tests-result", "1")
  )

  private val _commit_result = StateMachineOperationResult(
    StateMachineResultTypeReference("CommitResult"),
    ContextReference("commit-result", "1")
  )

  private val _run_identity = StateMachineRunIdentity("workflow-run-1")

  private def _suspending_provider: StateMachineProvider =
    new StateMachineProvider {
      override val identity: ProviderIdentity = ProviderIdentity("external-review")

      override def execute(request: ProviderExecutionRequest): ActionExecution =
        ActionExecution.Suspended(Continuation(
          request.runId,
          ContinuationIdentity("review-continuation-1"),
          StateMachineRevision("1"),
          request.requiredOperation,
          request.context
        ))
    }

  private def _deterministic_provider: StateMachineProvider =
    new StateMachineProvider {
      override val identity: ProviderIdentity = ProviderIdentity("deterministic-review")

      override def execute(request: ProviderExecutionRequest): ActionExecution =
        ActionExecution.Completed(_review_result)
    }

  private def _provider_selection(
    requiredidentity: StateMachineRequiredOperationIdentity,
    provider: StateMachineProvider
  ): StateMachineRequiredOperationIdentity => StateMachineProvider =
    identity =>
      if (identity == requiredidentity)
        provider
      else
        fail(s"Unexpected Required SPI identity: ${identity.capability}")

  private def _run(
    workflow: WorkflowDefinition,
    api: StateMachineApiSpi,
    providerfor: StateMachineRequiredOperationIdentity => StateMachineProvider
  ): FixtureRunState = {
    val actionidentities = workflow.compositeStateMachine.actions.map(_.identity)
    val localactions = actionidentities.take(2)
    val reviewaction = actionidentities(2)
    val commitaction = actionidentities(3)
    val localexecutions = localactions.map(actionidentity => actionidentity -> _execute_local_action(actionidentity))
    val requiredoperation = api.requiredOperations.find(_.actionIdentity == reviewaction).get
    val request = ProviderExecutionRequest(
      _run_identity,
      requiredoperation,
      requiredoperation.inputType.map(typereference => StateMachineOperationInput(typereference, _review_context_reference)),
      _context_bundle
    )
    providerfor(requiredoperation.identity).execute(request) match {
      case ActionExecution.Completed(result) =>
        result.typeReference shouldBe StateMachineResultTypeReference("ReviewResult")
        val commitexecution = _execute_local_action(commitaction)
        FixtureRunState(
          localactions ++ Vector(reviewaction, commitaction),
          localexecutions ++ Vector(commitaction -> commitexecution),
          None,
          true
        )
      case ActionExecution.Suspended(continuation) =>
        FixtureRunState(localactions, localexecutions, Some(continuation), false)
      case ActionExecution.Failed(failure) =>
        fail(s"Fixture Provider failed: ${failure.code}")
    }
  }

  private def _execute_local_action(actionidentity: String): ActionExecution.Completed =
    actionidentity match {
      case "BuildProject" => ActionExecution.Completed(_build_result)
      case "RunTests" => ActionExecution.Completed(_run_tests_result)
      case "CommitChanges" => ActionExecution.Completed(_commit_result)
      case unexpected => fail(s"Fixture local action is not declared: $unexpected")
    }

  private def _resume(
    state: FixtureRunState,
    result: ContinuationResult
  ): (FixtureRunState, ContinuationResumeValidation) =
    state.suspendedcontinuation match {
      case Some(continuation) =>
        val validation = ContinuationResumeValidator.validate(continuation, result)
        validation match {
          case ContinuationResumeValidation.Accepted(_) =>
            val commitexecution = _execute_local_action("CommitChanges")
            (
              FixtureRunState(
                state.completedactions ++ Vector("ReviewChange", "CommitChanges"),
                state.localexecutions ++ Vector("CommitChanges" -> commitexecution),
                None,
                true
              ),
              validation
            )
          case ContinuationResumeValidation.Rejected(_) => state -> validation
        }
      case None => fail("Fixture runner cannot resume without a suspended continuation.")
    }

  private def _continuation_result(continuation: Continuation): ContinuationResult =
    ContinuationResult(
      continuation.runId,
      continuation.continuationId,
      continuation.expectedRevision,
      continuation.requiredOperation.operation,
      continuation.requiredOperation.identity,
      continuation.context.snapshot,
      continuation.requiredOperation.metadata,
      _review_result,
      _completion_contract.requiredFacts,
      _evidence_contract.requiredEvidence
    )

  private def _model(source: String): KaleidoxModel = {
    val model = KaleidoxModel.parseWitoutLocation(KaleidoxConfig.default, source)
    if (model.errors.nonEmpty)
      throw new IllegalArgumentException(s"CML fixture must parse without errors: ${model.errors.mkString(" | ")}")
    model
  }

  private def _model_with_location(source: String): KaleidoxModel = {
    val model = KaleidoxModel.parse(KaleidoxConfig.default, source)
    if (model.errors.nonEmpty)
      throw new IllegalArgumentException(s"CML fixture must parse without errors: ${model.errors.mkString(" | ")}")
    model
  }

  private def _realm_string(realm: Realm, path: String): String =
    realm.get(path).collect { case value: Realm.StringData => value.string }.getOrElse(fail(s"Missing generated artifact: $path"))
}
