package domain.statemachine.workflow

object StateMachineWorkflowComponentFactoryBootstrap {
  val schemaVersion: String = "cozy.cml.statemachine-workflow-bootstrap.v4"
  val componentFactoryMetadata: Vector[StateMachineWorkflowAbi.ComponentFactoryMetadata] = Vector(SafetyWorkflowStateMachineWorkflow1.componentFactoryMetadata)
}
