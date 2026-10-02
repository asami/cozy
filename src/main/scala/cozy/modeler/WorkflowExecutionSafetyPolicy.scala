package cozy.modeler

/*
 * @since   Oct.  2, 2026
 * @version Oct.  2, 2026
 * @author  ASAMI, Tomoharu
 */
/** Classification constrains an independently declared retry budget; it creates none. */
sealed trait WorkflowFailureClassification {
  def canonicalValue: String
}

object WorkflowFailureClassification {
  case object Retryable extends WorkflowFailureClassification {
    val canonicalValue: String = "RETRYABLE"
  }

  case object Permanent extends WorkflowFailureClassification {
    val canonicalValue: String = "PERMANENT"
  }
}

/** Identity is opaque, provider-neutral and case-sensitive, not an exception reference. */
final case class WorkflowFailureRule(
  identity: String,
  classification: WorkflowFailureClassification,
  source: WorkflowSourceIdentity
)

final case class WorkflowFailurePolicy(
  identity: String,
  target: WorkflowInvocationTarget,
  actionIdentity: String,
  defaultClassification: WorkflowFailureClassification,
  rules: Vector[WorkflowFailureRule],
  source: WorkflowSourceIdentity
)

/** Workflow admission protection is independent of Action idempotency metadata. */
sealed trait WorkflowDuplicateProtection {
  def canonicalValue: String
}

object WorkflowDuplicateProtection {
  case object NotRequired extends WorkflowDuplicateProtection {
    val canonicalValue: String = "NOT_REQUIRED"
  }

  final case class Required(idempotencyKeyRef: String) extends WorkflowDuplicateProtection {
    val canonicalValue: String = "REQUIRED"
  }
}

/** References denote runtime-supplied values, never computed keys or concrete run IDs. */
final case class WorkflowExecutionSafetyPolicy(
  logicalExecutionIdentityRef: String,
  duplicateProtection: WorkflowDuplicateProtection,
  source: WorkflowSourceIdentity
)
