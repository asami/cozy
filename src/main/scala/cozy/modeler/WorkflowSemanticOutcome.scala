package cozy.modeler

/*
 * @since   Oct.  3, 2026
 * @version Oct.  3, 2026
 * @author  ASAMI, Tomoharu
 */
/** Closed semantic results; these declarations do not execute an ordinary graph edge. */
sealed abstract class WorkflowSemanticOutcome {
  def canonicalValue: String
}

object WorkflowSemanticOutcome {
  case object NeedsInput extends WorkflowSemanticOutcome {
    val canonicalValue: String = "NEEDS_INPUT"
  }

  case object NeedsRevision extends WorkflowSemanticOutcome {
    val canonicalValue: String = "NEEDS_REVISION"
  }

  case object Rejected extends WorkflowSemanticOutcome {
    val canonicalValue: String = "REJECTED"
  }
}

/** Actual ordinary constituent edge, retaining the resolved source machine reference. */
final case class WorkflowSemanticOutcomeTransition(
  role: String,
  stateMachine: String,
  from: String,
  to: String,
  on: String
)

final case class WorkflowSemanticOutcomeDeclaration(
  identity: String,
  target: WorkflowInvocationTarget,
  actionIdentity: String,
  outcome: WorkflowSemanticOutcome,
  transition: WorkflowSemanticOutcomeTransition,
  source: WorkflowSourceIdentity
)
