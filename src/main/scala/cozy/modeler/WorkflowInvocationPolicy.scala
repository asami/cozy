package cozy.modeler

/*
 * @since   Oct.  2, 2026
 * @version Oct.  2, 2026
 */
/** Declared source target, independent of provider selection or runtime placement. */
sealed trait WorkflowInvocationTarget {
  def identity: String
}

object WorkflowInvocationTarget {
  final case class Action(identity: String) extends WorkflowInvocationTarget
  final case class Participant(identity: String) extends WorkflowInvocationTarget
}

/** Maximum attempts includes the initial attempt; delay precedes each later attempt. */
final case class WorkflowRetryPolicy(maximumAttempts: Int, fixedRetryDelayMillis: Long)

/** Immutable source metadata for one canonical Action; it introduces no execution. */
final case class WorkflowInvocationPolicy(
  identity: String,
  target: WorkflowInvocationTarget,
  actionIdentity: String,
  retry: Option[WorkflowRetryPolicy],
  executionTimeoutMillis: Option[Long],
  source: WorkflowSourceIdentity
)
