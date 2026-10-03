package cozy.modeler

/*
 * @since   Oct.  3, 2026
 * @version Oct.  3, 2026
 * @author  ASAMI, Tomoharu
 */
sealed trait FailureScope { def name: String }
object FailureScope {
  case object InScope extends FailureScope { val name = "IN_SCOPE" }
  case object OutOfScope extends FailureScope { val name = "OUT_OF_SCOPE" }
}

final case class FailureModelSourceIdentity(declarationKind: String, sectionPath: String, line: Option[Int])
final case class FailureModelIssue(code: String, message: String, source: FailureModelSourceIdentity)
final case class FailureModelEntry(identity: String, scope: FailureScope, source: FailureModelSourceIdentity)
final case class ExecutionFailureModel(identity: String, source: FailureModelSourceIdentity, defaults: Vector[FailureModelEntry])
final case class FailureModelDelta(ownerKind: String, ownerIdentity: String, source: Option[FailureModelSourceIdentity], entries: Vector[FailureModelEntry])
final case class EffectiveFailureEntry(identity: String, scope: FailureScope, origins: Vector[FailureModelEntry], winningOrigin: FailureModelSourceIdentity)
final case class EffectiveFailureContract(ownerKind: String, ownerIdentity: String, source: FailureModelSourceIdentity, entries: Vector[EffectiveFailureEntry])
final case class OperationFailureModel(identity: String, source: FailureModelSourceIdentity, delta: FailureModelDelta, effective: EffectiveFailureContract)
final case class ServiceFailureModel(identity: String, source: FailureModelSourceIdentity, delta: FailureModelDelta, effective: EffectiveFailureContract, operations: Vector[OperationFailureModel])
final case class ComponentFailureModel(identity: String, source: FailureModelSourceIdentity, executionSelectionSource: FailureModelSourceIdentity, executionModel: ExecutionFailureModel, delta: FailureModelDelta, effective: EffectiveFailureContract, services: Vector[ServiceFailureModel])

object FailureModel {
  /** Pure development projection; CNCF owns runtime resolution. */
  def effective(
    ownerKind: String,
    ownerIdentity: String,
    source: FailureModelSourceIdentity,
    defaults: Vector[FailureModelEntry],
    deltas: Vector[FailureModelDelta]
  ): EffectiveFailureContract = {
    require(defaults.map(_.identity).distinct.size == defaults.size, "Defaults must have unique identities")
    require(deltas.forall(_.entries.forall(x => defaults.exists(_.identity == x.identity))), "Deltas must reference defaults")
    val entries = defaults.map { entry =>
      val origins = Vector(entry) ++ deltas.flatMap(_.entries.filter(_.identity == entry.identity))
      EffectiveFailureEntry(entry.identity, origins.last.scope, origins, origins.last.source)
    }
    EffectiveFailureContract(ownerKind, ownerIdentity, source, entries)
  }
}
