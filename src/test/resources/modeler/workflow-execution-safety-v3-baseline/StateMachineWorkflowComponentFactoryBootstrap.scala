package domain.statemachine.workflow

object StateMachineWorkflowComponentFactoryBootstrap {
  val schemaVersion: String = "cozy.cml.statemachine-workflow-bootstrap.v3"
  val componentFactoryMetadata: Vector[StateMachineWorkflowAbi.ComponentFactoryMetadata] = Vector(LifecycleWorkflowStateMachineWorkflow1.componentFactoryMetadata)
}
