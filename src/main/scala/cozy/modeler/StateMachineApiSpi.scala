package cozy.modeler

/*
 * @since   Sep. 17, 2026
 * @version Sep. 17, 2026
 * @author  ASAMI, Tomoharu
 */
/** Stable source-level identity for one StateMachine operation. */
final case class StateMachineOperationIdentity(service: String, operation: String) {
  def canonicalValue: String = s"$service.$operation"
}

/** Source-level reference to the declared input type of an operation. */
final case class StateMachineInputTypeReference(value: String)

/** Source-level reference to the declared result type of an operation. */
final case class StateMachineResultTypeReference(value: String)

/** Explicit StateMachine Provided API descriptor supplied by the caller. */
final case class StateMachineProvidedOperation(
  identity: StateMachineOperationIdentity,
  inputType: Option[StateMachineInputTypeReference],
  resultType: Option[StateMachineResultTypeReference]
)

/** Stable identity for one StateMachine Required SPI capability. */
final case class StateMachineRequiredOperationIdentity(capability: String)

/** Generic context requirements that an issued Required SPI operation preserves. */
final case class ContextContract(
  identity: String,
  requiredFacts: Vector[ContextReference],
  requiredReferences: Vector[ContextReference]
)

/** One constraint attached to a Required SPI operation. */
final case class StateMachineConstraint(identity: String, value: String)

/** Generic metadata owned by a Required SPI operation. */
final case class StateMachineRequiredOperationMetadata(
  contextContract: ContextContract,
  completionContract: CompletionContract,
  evidenceContract: EvidenceContract,
  constraints: Vector[StateMachineConstraint]
)

/** StateMachine Required SPI descriptor projected from an existing logical Action. */
final case class StateMachineRequiredOperation(
  identity: StateMachineRequiredOperationIdentity,
  actionIdentity: String,
  operation: StateMachineOperationIdentity,
  inputType: Option[StateMachineInputTypeReference],
  resultType: Option[StateMachineResultTypeReference],
  metadata: StateMachineRequiredOperationMetadata
)

/** One reusable StateMachine Provided API / Required SPI source contract. */
final case class StateMachineApiSpi(
  providedOperations: Vector[StateMachineProvidedOperation],
  requiredOperations: Vector[StateMachineRequiredOperation]
)

object StateMachineApiSpi {
  /**
   * Projects only the existing Workflow Required SPI mappings. Provided API
   * descriptors remain explicit caller input and no Workflow-specific API
   * model is introduced.
   */
  def fromWorkflow(
    workflow: WorkflowDefinition,
    providedOperations: Vector[StateMachineProvidedOperation],
    requiredOperationMetadata: WorkflowRequiredOperation => StateMachineRequiredOperationMetadata
  ): StateMachineApiSpi =
    StateMachineApiSpi(
      providedOperations,
      workflow.requiredOperations.map(_required_operation(_, requiredOperationMetadata))
    )

  private def _required_operation(
    required: WorkflowRequiredOperation,
    requiredoperationmetadata: WorkflowRequiredOperation => StateMachineRequiredOperationMetadata
  ): StateMachineRequiredOperation = {
    val operation = required.action.operation
    StateMachineRequiredOperation(
      identity = StateMachineRequiredOperationIdentity(required.capability),
      actionIdentity = required.action.identity,
      operation = StateMachineOperationIdentity(operation.service, operation.name),
      inputType = operation.inputType.map(StateMachineInputTypeReference),
      resultType = operation.outputType.map(StateMachineResultTypeReference),
      metadata = requiredoperationmetadata(required)
    )
  }
}

/** Identity of a provider selected to satisfy a Required SPI operation. */
final case class ProviderIdentity(value: String)

/** Binding is deliberately limited to Required SPI identity -> Provider identity. */
final case class StateMachineProviderBinding(
  requiredOperation: StateMachineRequiredOperationIdentity,
  provider: ProviderIdentity
)

/** A versioned reference to context held outside an invocation payload. */
final case class ContextReference(identity: String, revision: String)

/** Immutable context revision evidence used to reject stale continuation results. */
final case class ContextSnapshot(
  workflowRevision: String,
  modelRevision: Option[String] = None,
  workspaceRevision: Option[String] = None,
  evidenceRevision: Option[String] = None
)

/** Minimal context carrier for an operation invocation or durable continuation. */
final case class ContextBundle(
  summary: String,
  requiredFacts: Vector[ContextReference],
  references: Vector[ContextReference],
  snapshot: ContextSnapshot
)

/** Completion criteria carried independently of provider placement. */
final case class CompletionContract(identity: String, requiredFacts: Vector[ContextReference])

/** Evidence criteria carried independently of provider placement. */
final case class EvidenceContract(identity: String, requiredEvidence: Vector[ContextReference])

/** Typed operation input represented through a durable context reference. */
final case class StateMachineOperationInput(
  typeReference: StateMachineInputTypeReference,
  contextReference: ContextReference
)

/** Typed operation result represented through a durable context reference. */
final case class StateMachineOperationResult(
  typeReference: StateMachineResultTypeReference,
  contextReference: ContextReference
)

/** Typed failure descriptor returned by a Provider. */
final case class StateMachineOperationFailure(
  code: String,
  message: String,
  evidence: Vector[ContextReference]
)

/** Provider execution request with no execution-mode selection. */
final case class ProviderExecutionRequest(
  runId: StateMachineRunIdentity,
  requiredOperation: StateMachineRequiredOperation,
  input: Option[StateMachineOperationInput],
  context: ContextBundle
)

/** A Provider selects one closed ActionExecution outcome for each request. */
trait StateMachineProvider {
  def identity: ProviderIdentity
  def execute(request: ProviderExecutionRequest): ActionExecution
}

final case class StateMachineRunIdentity(value: String)

final case class ContinuationIdentity(value: String)

final case class StateMachineRevision(value: String)

/** Durable resume boundary returned when a Provider suspends an Action. */
final case class Continuation(
  runId: StateMachineRunIdentity,
  continuationId: ContinuationIdentity,
  expectedRevision: StateMachineRevision,
  requiredOperation: StateMachineRequiredOperation,
  context: ContextBundle
)

/** Completion and evidence submitted when resuming a durable continuation. */
final case class ContinuationResult(
  runId: StateMachineRunIdentity,
  continuationId: ContinuationIdentity,
  expectedRevision: StateMachineRevision,
  operation: StateMachineOperationIdentity,
  requiredOperation: StateMachineRequiredOperationIdentity,
  contextSnapshot: ContextSnapshot,
  requiredOperationMetadata: StateMachineRequiredOperationMetadata,
  completion: StateMachineOperationResult,
  completionFacts: Vector[ContextReference],
  evidence: Vector[ContextReference]
)

/** Closed Provider result algebra. */
sealed trait ActionExecution

object ActionExecution {
  final case class Completed(result: StateMachineOperationResult) extends ActionExecution
  final case class Suspended(continuation: Continuation) extends ActionExecution
  final case class Failed(failure: StateMachineOperationFailure) extends ActionExecution
}

/** Closed fail-closed reason algebra for continuation resume rejection. */
sealed trait ContinuationResumeRejection

object ContinuationResumeRejection {
  final case class RunIdentityMismatch(
    expected: StateMachineRunIdentity,
    actual: StateMachineRunIdentity
  ) extends ContinuationResumeRejection

  final case class ContinuationIdentityMismatch(
    expected: ContinuationIdentity,
    actual: ContinuationIdentity
  ) extends ContinuationResumeRejection

  final case class ExpectedRevisionMismatch(
    expected: StateMachineRevision,
    actual: StateMachineRevision
  ) extends ContinuationResumeRejection

  final case class ContextSnapshotMismatch(
    expected: ContextSnapshot,
    actual: ContextSnapshot
  ) extends ContinuationResumeRejection

  final case class RequiredOperationIdentityMismatch(
    expected: StateMachineRequiredOperationIdentity,
    actual: StateMachineRequiredOperationIdentity
  ) extends ContinuationResumeRejection

  final case class OperationIdentityMismatch(
    expected: StateMachineOperationIdentity,
    actual: StateMachineOperationIdentity
  ) extends ContinuationResumeRejection

  final case class DeclaredResultTypeMismatch(
    expected: Option[StateMachineResultTypeReference],
    actual: StateMachineResultTypeReference
  ) extends ContinuationResumeRejection

  final case class ContextContractMismatch(
    expected: ContextContract,
    actual: ContextContract
  ) extends ContinuationResumeRejection

  final case class StateMachineConstraintsMismatch(
    expected: Vector[StateMachineConstraint],
    actual: Vector[StateMachineConstraint]
  ) extends ContinuationResumeRejection

  final case class RequiredContextFactsMissing(
    expected: Vector[ContextReference],
    actual: Vector[ContextReference]
  ) extends ContinuationResumeRejection

  final case class RequiredContextReferencesMissing(
    expected: Vector[ContextReference],
    actual: Vector[ContextReference]
  ) extends ContinuationResumeRejection

  final case class CompletionContractMismatch(
    expected: CompletionContract,
    actual: CompletionContract
  ) extends ContinuationResumeRejection

  final case class CompletionFactsMissing(
    expected: Vector[ContextReference],
    actual: Vector[ContextReference]
  ) extends ContinuationResumeRejection

  final case class EvidenceContractMismatch(
    expected: EvidenceContract,
    actual: EvidenceContract
  ) extends ContinuationResumeRejection

  final case class EvidenceMissing(
    expected: Vector[ContextReference],
    actual: Vector[ContextReference]
  ) extends ContinuationResumeRejection
}

/** Result of applying a ContinuationResult to one durable Continuation boundary. */
sealed trait ContinuationResumeValidation

object ContinuationResumeValidation {
  final case class Accepted(result: ContinuationResult) extends ContinuationResumeValidation
  final case class Rejected(reason: ContinuationResumeRejection) extends ContinuationResumeValidation
}

/** Pure validator that accepts only evidence matching the issued continuation. */
object ContinuationResumeValidator {
  def validate(
    continuation: Continuation,
    result: ContinuationResult
  ): ContinuationResumeValidation =
    if (continuation.runId != result.runId)
      ContinuationResumeValidation.Rejected(
        ContinuationResumeRejection.RunIdentityMismatch(continuation.runId, result.runId)
      )
    else if (continuation.continuationId != result.continuationId)
      ContinuationResumeValidation.Rejected(
        ContinuationResumeRejection.ContinuationIdentityMismatch(continuation.continuationId, result.continuationId)
      )
    else if (continuation.expectedRevision != result.expectedRevision)
      ContinuationResumeValidation.Rejected(
        ContinuationResumeRejection.ExpectedRevisionMismatch(continuation.expectedRevision, result.expectedRevision)
      )
    else if (continuation.context.snapshot != result.contextSnapshot)
      ContinuationResumeValidation.Rejected(
        ContinuationResumeRejection.ContextSnapshotMismatch(continuation.context.snapshot, result.contextSnapshot)
      )
    else if (continuation.requiredOperation.identity != result.requiredOperation)
      ContinuationResumeValidation.Rejected(
        ContinuationResumeRejection.RequiredOperationIdentityMismatch(
          continuation.requiredOperation.identity,
          result.requiredOperation
        )
      )
    else if (continuation.requiredOperation.operation != result.operation)
      ContinuationResumeValidation.Rejected(
        ContinuationResumeRejection.OperationIdentityMismatch(
          continuation.requiredOperation.operation,
          result.operation
        )
      )
    else if (continuation.requiredOperation.resultType != Some(result.completion.typeReference))
      ContinuationResumeValidation.Rejected(
        ContinuationResumeRejection.DeclaredResultTypeMismatch(
          continuation.requiredOperation.resultType,
          result.completion.typeReference
        )
      )
    else if (continuation.requiredOperation.metadata.contextContract != result.requiredOperationMetadata.contextContract)
      ContinuationResumeValidation.Rejected(
        ContinuationResumeRejection.ContextContractMismatch(
          continuation.requiredOperation.metadata.contextContract,
          result.requiredOperationMetadata.contextContract
        )
      )
    else if (continuation.requiredOperation.metadata.constraints != result.requiredOperationMetadata.constraints)
      ContinuationResumeValidation.Rejected(
        ContinuationResumeRejection.StateMachineConstraintsMismatch(
          continuation.requiredOperation.metadata.constraints,
          result.requiredOperationMetadata.constraints
        )
      )
    else if (!_contains_required_references(
      continuation.context.requiredFacts,
      continuation.requiredOperation.metadata.contextContract.requiredFacts
    ))
      ContinuationResumeValidation.Rejected(
        ContinuationResumeRejection.RequiredContextFactsMissing(
          continuation.requiredOperation.metadata.contextContract.requiredFacts,
          continuation.context.requiredFacts
        )
      )
    else if (!_contains_required_references(
      continuation.context.references,
      continuation.requiredOperation.metadata.contextContract.requiredReferences
    ))
      ContinuationResumeValidation.Rejected(
        ContinuationResumeRejection.RequiredContextReferencesMissing(
          continuation.requiredOperation.metadata.contextContract.requiredReferences,
          continuation.context.references
        )
      )
    else if (continuation.requiredOperation.metadata.completionContract != result.requiredOperationMetadata.completionContract)
      ContinuationResumeValidation.Rejected(
        ContinuationResumeRejection.CompletionContractMismatch(
          continuation.requiredOperation.metadata.completionContract,
          result.requiredOperationMetadata.completionContract
        )
      )
    else if (!_contains_required_references(
      result.completionFacts,
      continuation.requiredOperation.metadata.completionContract.requiredFacts
    ))
      ContinuationResumeValidation.Rejected(
        ContinuationResumeRejection.CompletionFactsMissing(
          continuation.requiredOperation.metadata.completionContract.requiredFacts,
          result.completionFacts
        )
      )
    else if (continuation.requiredOperation.metadata.evidenceContract != result.requiredOperationMetadata.evidenceContract)
      ContinuationResumeValidation.Rejected(
        ContinuationResumeRejection.EvidenceContractMismatch(
          continuation.requiredOperation.metadata.evidenceContract,
          result.requiredOperationMetadata.evidenceContract
        )
      )
    else if (!_contains_required_references(
      result.evidence,
      continuation.requiredOperation.metadata.evidenceContract.requiredEvidence
    ))
      ContinuationResumeValidation.Rejected(
        ContinuationResumeRejection.EvidenceMissing(
          continuation.requiredOperation.metadata.evidenceContract.requiredEvidence,
          result.evidence
        )
      )
    else
      ContinuationResumeValidation.Accepted(result)

  private def _contains_required_references(
    actual: Vector[ContextReference],
    required: Vector[ContextReference]
  ): Boolean =
    required.forall(actual.contains)
}
