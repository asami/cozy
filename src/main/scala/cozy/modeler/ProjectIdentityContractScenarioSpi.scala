package cozy.modeler

private[cozy] final case class ProjectIdentityInput(
  namespace: String,
  localid: String
)

private[cozy] sealed trait ProjectIdentityContractScenarioRequest {
  def scenarioId: String
}

private[cozy] object ProjectIdentityContractScenarioRequest {
  final case class ScopedCollision(
    scenarioId: String,
    identities: Vector[ProjectIdentityInput]
  ) extends ProjectIdentityContractScenarioRequest
}

private[cozy] sealed trait ProjectIdentityContractScenarioReport {
  def scenarioId: String
}

private[cozy] object ProjectIdentityContractScenarioReport {
  final case class NotImplemented(scenarioId: String) extends ProjectIdentityContractScenarioReport
  final case class Admitted(scenarioId: String) extends ProjectIdentityContractScenarioReport
  final case class Rejected(
    scenarioId: String,
    code: String
  ) extends ProjectIdentityContractScenarioReport
}

private[cozy] object ProjectIdentityContractScenarioSpi {
  def evaluate(
    request: ProjectIdentityContractScenarioRequest
  ): ProjectIdentityContractScenarioReport =
    ProjectIdentityContractScenarioReport.NotImplemented(request.scenarioId)
}
