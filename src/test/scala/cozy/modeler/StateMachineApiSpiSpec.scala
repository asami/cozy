package cozy.modeler

import org.scalacheck.{Gen, Prop, Test}
import org.scalatest.GivenWhenThen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/*
 * @since   Sep. 17, 2026
 * @version Sep. 17, 2026
 * @author  ASAMI, Tomoharu
 */
final class StateMachineApiSpiSpec extends AnyWordSpec with Matchers with GivenWhenThen {
  "StateMachine API/SPI source contract" should {
    "project the existing Workflow Required SPI without creating a Workflow-specific interface model" which {
      "retains the complete generic Required SPI metadata" in {
      Given("a Workflow Required Operation with an existing typed Operation Action and an explicit Provided API descriptor")
      val workflow = _workflow()
      val providedoperations = Vector(
        StateMachineProvidedOperation(
          StateMachineOperationIdentity("OrderService", "queryPayment"),
          Some(StateMachineInputTypeReference("PaymentQuery")),
          Some(StateMachineResultTypeReference("PaymentView"))
        )
      )

      When("the reusable StateMachine API/SPI contract projects the Workflow")
      val projection = StateMachineApiSpi.fromWorkflow(workflow, providedoperations, _required_operation_metadata)
      val requiredoperation = projection.requiredOperations.head

      Then("the explicit Provided API and every generic Required SPI descriptor field are retained")
      projection.providedOperations shouldBe providedoperations
      requiredoperation.identity shouldBe StateMachineRequiredOperationIdentity("capture-payment-capability")
      requiredoperation.actionIdentity shouldBe "capture-payment"
      requiredoperation.operation shouldBe StateMachineOperationIdentity("OrderService", "capturePayment")
      requiredoperation.operation.canonicalValue shouldBe "OrderService.capturePayment"
      requiredoperation.inputType shouldBe Some(StateMachineInputTypeReference("PaymentCommand"))
      requiredoperation.resultType shouldBe Some(StateMachineResultTypeReference("PaymentResult"))
      requiredoperation.metadata shouldBe _required_operation_metadata_value
    }
    }

    "bind only a Required SPI identity to a Provider identity" which {
      "keeps completion and evidence contracts owned by the Required SPI descriptor" in {
      Given("one projected Required SPI operation and a Provider execution request")
      val requiredoperation = StateMachineApiSpi.fromWorkflow(_workflow(), Vector.empty, _required_operation_metadata).requiredOperations.head
      val binding = StateMachineProviderBinding(requiredoperation.identity, ProviderIdentity("payment-provider"))
      val request = ProviderExecutionRequest(
        StateMachineRunIdentity("run-1"),
        requiredoperation,
        Some(StateMachineOperationInput(
          StateMachineInputTypeReference("PaymentCommand"),
          ContextReference("payment-command", "1")
        )),
        _context_bundle
      )

      When("the binding and request are represented by the source ABI")
      val bindingfields = binding.productIterator.toVector

      Then("the binding contains exactly the Required SPI and Provider identities, while the request carries the typed invocation context without an execution mode")
      binding shouldBe StateMachineProviderBinding(requiredoperation.identity, ProviderIdentity("payment-provider"))
      bindingfields shouldBe Vector(requiredoperation.identity, ProviderIdentity("payment-provider"))
      request.requiredOperation shouldBe requiredoperation
      request.input.map(_.typeReference) shouldBe Some(StateMachineInputTypeReference("PaymentCommand"))
      request.requiredOperation.metadata.completionContract shouldBe _completion_contract
      request.requiredOperation.metadata.evidenceContract shouldBe _evidence_contract
    }
    }

    "keep Provider outcomes closed as Completed, Suspended, or Failed" which {
      "classifies exactly the three Provider outcomes" in {
      Given("one typed completion, one durable continuation, and one typed Provider failure")
      val completed = ActionExecution.Completed(_operation_result)
      val suspended = ActionExecution.Suspended(_continuation)
      val failed = ActionExecution.Failed(StateMachineOperationFailure(
        "payment-declined",
        "The payment provider declined the command.",
        Vector(ContextReference("payment-decline", "1"))
      ))

      When("a Provider outcome is classified by the closed ActionExecution algebra")
      val outcomes = Vector[ActionExecution](completed, suspended, failed).map {
        case ActionExecution.Completed(_) => "completed"
        case ActionExecution.Suspended(_) => "suspended"
        case ActionExecution.Failed(_) => "failed"
      }

      Then("the only source-contract outcomes are completion, durable suspension, and typed failure")
      outcomes shouldBe Vector("completed", "suspended", "failed")
    }
    }

    "accept only current continuation completion evidence" which {
      "rejects every stale, descriptor, contract, context, and evidence mismatch" in {
      Given("an issued continuation and a completion result with matching run, continuation, revision, and context snapshot")
      val continuation = _continuation
      val result = _continuation_result

      When("the pure resume validator receives the matching result and one mismatch for each protected boundary")
      val accepted = ContinuationResumeValidator.validate(continuation, result)
      val runmismatch = ContinuationResumeValidator.validate(continuation, result.copy(
        runId = StateMachineRunIdentity("run-2")
      ))
      val continuationmismatch = ContinuationResumeValidator.validate(continuation, result.copy(
        continuationId = ContinuationIdentity("continuation-2")
      ))
      val revisionmismatch = ContinuationResumeValidator.validate(continuation, result.copy(
        expectedRevision = StateMachineRevision("2")
      ))
      val snapshotmismatch = ContinuationResumeValidator.validate(continuation, result.copy(
        contextSnapshot = _context_snapshot.copy(workflowRevision = "workflow-2")
      ))
      val requiredoperationmismatch = ContinuationResumeValidator.validate(continuation, result.copy(
        requiredOperation = StateMachineRequiredOperationIdentity("other-capability")
      ))
      val operationmismatch = ContinuationResumeValidator.validate(continuation, result.copy(
        operation = StateMachineOperationIdentity("OrderService", "refundPayment")
      ))
      val resulttypemismatch = ContinuationResumeValidator.validate(continuation, result.copy(
        completion = _operation_result.copy(typeReference = StateMachineResultTypeReference("RefundResult"))
      ))
      val contextcontractmismatch = ContinuationResumeValidator.validate(continuation, result.copy(
        requiredOperationMetadata = _required_operation_metadata_value.copy(
          contextContract = _context_contract.copy(identity = "other-context")
        )
      ))
      val constraintsmismatch = ContinuationResumeValidator.validate(continuation, result.copy(
        requiredOperationMetadata = _required_operation_metadata_value.copy(
          constraints = Vector(StateMachineConstraint("payment-region", "US"))
        )
      ))
      val contextfactsmismatch = ContinuationResumeValidator.validate(continuation.copy(
        context = _context_bundle.copy(requiredFacts = Vector.empty)
      ), result)
      val contextreferencesmismatch = ContinuationResumeValidator.validate(continuation.copy(
        context = _context_bundle.copy(references = Vector.empty)
      ), result)
      val completioncontractmismatch = ContinuationResumeValidator.validate(continuation, result.copy(
        requiredOperationMetadata = _required_operation_metadata_value.copy(
          completionContract = _completion_contract.copy(identity = "other-completion")
        )
      ))
      val completionfactsmismatch = ContinuationResumeValidator.validate(continuation, result.copy(
        completionFacts = Vector.empty
      ))
      val evidencecontractmismatch = ContinuationResumeValidator.validate(continuation, result.copy(
        requiredOperationMetadata = _required_operation_metadata_value.copy(
          evidenceContract = _evidence_contract.copy(identity = "other-evidence")
        )
      ))
      val evidencemismatch = ContinuationResumeValidator.validate(continuation, result.copy(
        evidence = Vector.empty
      ))

      Then("only matching typed evidence is accepted and every protected mismatch is rejected by its closed reason")
      accepted shouldBe ContinuationResumeValidation.Accepted(result)
      runmismatch shouldBe ContinuationResumeValidation.Rejected(
        ContinuationResumeRejection.RunIdentityMismatch(StateMachineRunIdentity("run-1"), StateMachineRunIdentity("run-2"))
      )
      continuationmismatch shouldBe ContinuationResumeValidation.Rejected(
        ContinuationResumeRejection.ContinuationIdentityMismatch(ContinuationIdentity("continuation-1"), ContinuationIdentity("continuation-2"))
      )
      revisionmismatch shouldBe ContinuationResumeValidation.Rejected(
        ContinuationResumeRejection.ExpectedRevisionMismatch(StateMachineRevision("1"), StateMachineRevision("2"))
      )
      snapshotmismatch shouldBe ContinuationResumeValidation.Rejected(
        ContinuationResumeRejection.ContextSnapshotMismatch(
          _context_snapshot,
          _context_snapshot.copy(workflowRevision = "workflow-2")
        )
      )
      requiredoperationmismatch shouldBe ContinuationResumeValidation.Rejected(
        ContinuationResumeRejection.RequiredOperationIdentityMismatch(
          StateMachineRequiredOperationIdentity("capture-payment-capability"),
          StateMachineRequiredOperationIdentity("other-capability")
        )
      )
      operationmismatch shouldBe ContinuationResumeValidation.Rejected(
        ContinuationResumeRejection.OperationIdentityMismatch(
          StateMachineOperationIdentity("OrderService", "capturePayment"),
          StateMachineOperationIdentity("OrderService", "refundPayment")
        )
      )
      resulttypemismatch shouldBe ContinuationResumeValidation.Rejected(
        ContinuationResumeRejection.DeclaredResultTypeMismatch(
          Some(StateMachineResultTypeReference("PaymentResult")),
          StateMachineResultTypeReference("RefundResult")
        )
      )
      contextcontractmismatch shouldBe ContinuationResumeValidation.Rejected(
        ContinuationResumeRejection.ContextContractMismatch(
          _context_contract,
          _context_contract.copy(identity = "other-context")
        )
      )
      constraintsmismatch shouldBe ContinuationResumeValidation.Rejected(
        ContinuationResumeRejection.StateMachineConstraintsMismatch(
          _required_operation_metadata_value.constraints,
          Vector(StateMachineConstraint("payment-region", "US"))
        )
      )
      contextfactsmismatch shouldBe ContinuationResumeValidation.Rejected(
        ContinuationResumeRejection.RequiredContextFactsMissing(
          _context_contract.requiredFacts,
          Vector.empty
        )
      )
      contextreferencesmismatch shouldBe ContinuationResumeValidation.Rejected(
        ContinuationResumeRejection.RequiredContextReferencesMissing(
          _context_contract.requiredReferences,
          Vector.empty
        )
      )
      completioncontractmismatch shouldBe ContinuationResumeValidation.Rejected(
        ContinuationResumeRejection.CompletionContractMismatch(
          _completion_contract,
          _completion_contract.copy(identity = "other-completion")
        )
      )
      completionfactsmismatch shouldBe ContinuationResumeValidation.Rejected(
        ContinuationResumeRejection.CompletionFactsMissing(
          _completion_contract.requiredFacts,
          Vector.empty
        )
      )
      evidencecontractmismatch shouldBe ContinuationResumeValidation.Rejected(
        ContinuationResumeRejection.EvidenceContractMismatch(
          _evidence_contract,
          _evidence_contract.copy(identity = "other-evidence")
        )
      )
      evidencemismatch shouldBe ContinuationResumeValidation.Rejected(
        ContinuationResumeRejection.EvidenceMissing(
          _evidence_contract.requiredEvidence,
          Vector.empty
        )
      )
      }

      "rejects every generated divergence from the issued durable boundary" in {
      Given("an issued continuation and ScalaCheck generators for each protected identity, context, metadata, completion, and evidence field")
      val suffixes = Gen.chooseNum(2, 64)
      val divergences: Gen[(Continuation, ContinuationResult)] = Gen.oneOf(
        suffixes.map(suffix => _continuation -> _continuation_result.copy(
          runId = StateMachineRunIdentity(s"run-$suffix")
        )),
        suffixes.map(suffix => _continuation -> _continuation_result.copy(
          continuationId = ContinuationIdentity(s"continuation-$suffix")
        )),
        suffixes.map(suffix => _continuation -> _continuation_result.copy(
          expectedRevision = StateMachineRevision(s"$suffix")
        )),
        suffixes.map(suffix => _continuation -> _continuation_result.copy(
          contextSnapshot = _context_snapshot.copy(workflowRevision = s"workflow-$suffix")
        )),
        suffixes.map(suffix => _continuation -> _continuation_result.copy(
          requiredOperation = StateMachineRequiredOperationIdentity(s"other-capability-$suffix")
        )),
        suffixes.map(suffix => _continuation -> _continuation_result.copy(
          operation = StateMachineOperationIdentity("OrderService", s"refundPayment$suffix")
        )),
        suffixes.map(suffix => _continuation -> _continuation_result.copy(
          completion = _operation_result.copy(typeReference = StateMachineResultTypeReference(s"RefundResult$suffix"))
        )),
        suffixes.map(suffix => _continuation -> _continuation_result.copy(
          requiredOperationMetadata = _required_operation_metadata_value.copy(
            contextContract = _context_contract.copy(identity = s"other-context-$suffix")
          )
        )),
        suffixes.map(suffix => _continuation -> _continuation_result.copy(
          requiredOperationMetadata = _required_operation_metadata_value.copy(
            constraints = Vector(StateMachineConstraint("payment-region", s"region-$suffix"))
          )
        )),
        suffixes.map(suffix => _continuation.copy(
          context = _context_bundle.copy(requiredFacts = Vector(ContextReference(s"order-$suffix", "1")))
        ) -> _continuation_result),
        suffixes.map(suffix => _continuation.copy(
          context = _context_bundle.copy(references = Vector(ContextReference(s"payment-command-$suffix", "1")))
        ) -> _continuation_result),
        suffixes.map(suffix => _continuation -> _continuation_result.copy(
          requiredOperationMetadata = _required_operation_metadata_value.copy(
            completionContract = _completion_contract.copy(identity = s"other-completion-$suffix")
          )
        )),
        suffixes.map(suffix => _continuation -> _continuation_result.copy(
          completionFacts = Vector(ContextReference(s"payment-receipt-$suffix", "1"))
        )),
        suffixes.map(suffix => _continuation -> _continuation_result.copy(
          requiredOperationMetadata = _required_operation_metadata_value.copy(
            evidenceContract = _evidence_contract.copy(identity = s"other-evidence-$suffix")
          )
        )),
        suffixes.map(suffix => _continuation -> _continuation_result.copy(
          evidence = Vector(ContextReference(s"payment-evidence-$suffix", "1"))
        ))
      )

      When("ScalaCheck submits generated divergences at the durable resume boundary")
      val property = Prop.forAll(divergences) { case (continuation, result) =>
        ContinuationResumeValidator.validate(continuation, result) match {
          case ContinuationResumeValidation.Rejected(_) => true
          case ContinuationResumeValidation.Accepted(_) => false
        }
      }
      val checked = Test.check(Test.Parameters.default.withMinSuccessfulTests(60), property)

      Then("every generated divergence is rejected without advancing the continuation")
      checked.passed shouldBe true
      }
    }
  }

  private def _workflow(): WorkflowDefinition = {
    val operation = CompositeStateMachineOperation(
      "OrderService",
      "capturePayment",
      Some("PaymentCommand"),
      Some("PaymentResult")
    )
    val action = CompositeStateMachineLogicalAction(
      "capture-payment",
      "OPERATION",
      operation,
      Some("payment.subject"),
      CompositeStateMachineSourceIdentity(None)
    )
    val definition = CompositeStateMachineDefinition(
      "OrderProgress",
      "OrderProgress",
      CompositeStateMachineSourceIdentity(None),
      Vector.empty,
      Vector.empty,
      Vector.empty,
      None,
      Vector(action),
      Vector.empty,
      Vector.empty
    )
    WorkflowDefinition(
      "OrderProgress",
      "workflow-v1",
      WorkflowSourceCorrelation(WorkflowSourceIdentity(None), WorkflowSourceIdentity(None)),
      definition,
      Vector(WorkflowRequiredOperation(
        "capture-payment-capability",
        action,
        WorkflowSourceIdentity(None)
      ))
    )
  }

  private val _context_snapshot = ContextSnapshot(
    workflowRevision = "workflow-1",
    modelRevision = Some("model-1"),
    workspaceRevision = Some("workspace-1"),
    evidenceRevision = Some("evidence-1")
  )

  private val _context_bundle = ContextBundle(
    summary = "Capture payment for the current order.",
    requiredFacts = Vector(ContextReference("order", "1")),
    references = Vector(ContextReference("payment-command", "1")),
    snapshot = _context_snapshot
  )

  private val _context_contract = ContextContract(
    "payment-context",
    Vector(ContextReference("order", "1")),
    Vector(ContextReference("payment-command", "1"))
  )

  private val _completion_contract = CompletionContract(
    "payment-captured",
    Vector(ContextReference("payment-receipt", "1"))
  )

  private val _evidence_contract = EvidenceContract(
    "payment-evidence",
    Vector(ContextReference("payment-receipt", "1"))
  )

  private val _required_operation_metadata_value = StateMachineRequiredOperationMetadata(
    _context_contract,
    _completion_contract,
    _evidence_contract,
    Vector(StateMachineConstraint("payment-region", "JP"))
  )

  private def _required_operation_metadata(
    required: WorkflowRequiredOperation
  ): StateMachineRequiredOperationMetadata =
    _required_operation_metadata_value

  private val _operation_result = StateMachineOperationResult(
    StateMachineResultTypeReference("PaymentResult"),
    ContextReference("payment-result", "1")
  )

  private val _continuation = Continuation(
    StateMachineRunIdentity("run-1"),
    ContinuationIdentity("continuation-1"),
    StateMachineRevision("1"),
    StateMachineApiSpi.fromWorkflow(_workflow(), Vector.empty, _required_operation_metadata).requiredOperations.head,
    _context_bundle
  )

  private val _continuation_result = ContinuationResult(
    StateMachineRunIdentity("run-1"),
    ContinuationIdentity("continuation-1"),
    StateMachineRevision("1"),
    StateMachineOperationIdentity("OrderService", "capturePayment"),
    StateMachineRequiredOperationIdentity("capture-payment-capability"),
    _context_snapshot,
    _required_operation_metadata_value,
    _operation_result,
    Vector(ContextReference("payment-receipt", "1")),
    Vector(ContextReference("payment-receipt", "1"))
  )
}
