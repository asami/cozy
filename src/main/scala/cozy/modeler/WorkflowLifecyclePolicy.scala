package cozy.modeler

/*
 * @since   Oct.  2, 2026
 * @version Oct.  2, 2026
 * @author  ASAMI, Tomoharu
 */
/** Closed declaration of cancellation semantics; enforcement belongs to runtime. */
sealed trait WorkflowCancellationMode {
  def canonicalValue: String
}

object WorkflowCancellationMode {
  case object Cooperative extends WorkflowCancellationMode {
    val canonicalValue: String = "COOPERATIVE"
  }
}

/** Deadline is relative to Workflow start; omitted values remain undeclared. */
final case class WorkflowLifecyclePolicy(
  deadlineMillis: Option[Long],
  cancellation: Option[WorkflowCancellationMode],
  source: WorkflowSourceIdentity
)

/** Timer delay is relative to each State entry; Signal carries an opaque logical name. */
sealed trait WorkflowWaitTrigger

object WorkflowWaitTrigger {
  final case class Timer(delayMillis: Long) extends WorkflowWaitTrigger
  final case class Signal(identity: String) extends WorkflowWaitTrigger
}

/** Static wait identity and source correlation, independent of durable runtime instances. */
final case class WorkflowWaitDeclaration(
  identity: String,
  state: String,
  stateIdentity: String,
  trigger: WorkflowWaitTrigger,
  source: WorkflowSourceIdentity
)
