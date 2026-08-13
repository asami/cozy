package cozy.modeler

/*
 * @since   Aug. 7, 2026
 * @version Aug. 14, 2026
 * @author  ASAMI, Tomoharu
 */

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
  ): ProjectIdentityContractScenarioReport = request match {
    case ProjectIdentityContractScenarioRequest.ScopedCollision(scenarioid, identities) =>
      ProjectIdentityAdapter.validateNoScopedCollisions(identities) match {
        case Right(_) => ProjectIdentityContractScenarioReport.Admitted(scenarioid)
        case Left(error) =>
          ProjectIdentityContractScenarioReport.Rejected(scenarioid, _scenario_code(error.code()))
      }
  }

  private def _scenario_code(code: String): String =
    if (code == "component.identity.projection.collision")
      "component.identity.projection-collision"
    else
      code
}
