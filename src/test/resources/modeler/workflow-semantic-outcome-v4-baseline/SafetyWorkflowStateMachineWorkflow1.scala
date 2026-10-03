package domain.statemachine.workflow

object SafetyWorkflowStateMachineWorkflow1 {
  val workflow: StateMachineWorkflowAbi.WorkflowDescriptor = StateMachineWorkflowAbi.WorkflowDescriptor(
  schemaVersion = "cozy.cml.statemachine-workflow-abi.v4",
  identity = "SafetyWorkflow",
  version = "workflow-safety-v4",
  source = StateMachineWorkflowAbi.WorkflowSourceCorrelation(StateMachineWorkflowAbi.SourceIdentity(Some(25)), StateMachineWorkflowAbi.SourceIdentity(Some(27))),
  states = Vector(StateMachineWorkflowAbi.State("Pending", StateMachineWorkflowAbi.SourceIdentity(Some(43))), StateMachineWorkflowAbi.State("Complete", StateMachineWorkflowAbi.SourceIdentity(Some(45)))),
  actions = Vector(StateMachineWorkflowAbi.Action("capture-payment", "OPERATION", StateMachineWorkflowAbi.Operation("OrderService", "capturePayment", Some("PaymentCommand"), Some("PaymentResult"), StateMachineWorkflowAbi.SourceIdentity(Some(61))), Some("payment.subject"), StateMachineWorkflowAbi.SourceIdentity(Some(61)))),
  requiredOperations = Vector(StateMachineWorkflowAbi.WorkflowRequiredOperationDescriptor(StateMachineWorkflowAbi.StateMachineRequiredOperationIdentity("capture-payment-capability"), "capture-payment", StateMachineWorkflowAbi.StateMachineOperationIdentity("OrderService", "capturePayment"), Some(StateMachineWorkflowAbi.StateMachineInputTypeReference("PaymentCommand")), Some(StateMachineWorkflowAbi.StateMachineResultTypeReference("PaymentResult")), StateMachineWorkflowAbi.SourceIdentity(Some(74)), StateMachineWorkflowAbi.SourceIdentity(Some(61)))),
  invocationPolicies = Vector(StateMachineWorkflowAbi.InvocationPolicy("capture-control", StateMachineWorkflowAbi.InvocationTarget.Action("capture-payment"), "capture-payment", Some(StateMachineWorkflowAbi.RetryPolicy(3, 250L)), Some(30000L), StateMachineWorkflowAbi.SourceIdentity(Some(86)))),
  lifecyclePolicy = Some(StateMachineWorkflowAbi.LifecyclePolicy(Some(60000L), Some(StateMachineWorkflowAbi.CancellationMode.Cooperative), StateMachineWorkflowAbi.SourceIdentity(Some(93)))),
  waits = Vector(StateMachineWorkflowAbi.WaitDeclaration("payment-timer", "Pending", "Pending", StateMachineWorkflowAbi.WaitTrigger.Timer(250L), StateMachineWorkflowAbi.SourceIdentity(Some(100))), StateMachineWorkflowAbi.WaitDeclaration("completion-signal", "Complete", "Complete", StateMachineWorkflowAbi.WaitTrigger.Signal("payment.completed"), StateMachineWorkflowAbi.SourceIdentity(Some(105)))),
  failurePolicies = Vector(StateMachineWorkflowAbi.FailurePolicy("capture-failure", StateMachineWorkflowAbi.InvocationTarget.Participant("capture-payment-capability"), "capture-payment", StateMachineWorkflowAbi.FailureClassification.Permanent, Vector(StateMachineWorkflowAbi.FailureRule("transient.payment", StateMachineWorkflowAbi.FailureClassification.Retryable, StateMachineWorkflowAbi.SourceIdentity(Some(117))), StateMachineWorkflowAbi.FailureRule("payment.rejected", StateMachineWorkflowAbi.FailureClassification.Permanent, StateMachineWorkflowAbi.SourceIdentity(Some(121)))), StateMachineWorkflowAbi.SourceIdentity(Some(112)))),
  executionSafetyPolicy = Some(StateMachineWorkflowAbi.ExecutionSafetyPolicy("order-run", StateMachineWorkflowAbi.DuplicateProtection.Required("order-start"), StateMachineWorkflowAbi.SourceIdentity(Some(125))))
)
  val componentFactoryMetadata: StateMachineWorkflowAbi.ComponentFactoryMetadata = StateMachineWorkflowAbi.ComponentFactoryMetadata(workflow)
}
