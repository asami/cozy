package cozy.modeler

import org.goldenport.RAISE
import org.goldenport.parser.LogicalSection

/*
 * @since   Oct.  2, 2026
 * @version Oct.  2, 2026
 * @author  ASAMI, Tomoharu
 */
/** Normalizes Workflow-owned lifecycle declarations after existing CSM validation. */
private[modeler] object WorkflowLifecyclePolicyCml {
  def normalizeLifecycle(
    section: Option[LogicalSection],
    workflowIdentity: String,
    directFields: LogicalSection => Vector[(String, String)]
  ): Option[WorkflowLifecyclePolicy] = section.map { lifecycle =>
    val context = s"WORKFLOW '$workflowIdentity' LIFECYCLE"
    val values = _closed_fields(lifecycle, Set("deadlinemillis", "cancellation"), context, directFields)
    if (values.isEmpty)
      RAISE.syntaxErrorFault(s"$context requires deadline-millis or cancellation or both.")
    val deadline = values.get("deadlinemillis").map(_unsigned_long(_, 1, s"$context deadline-millis"))
    val cancellation = values.get("cancellation").map { value =>
      if (value != "COOPERATIVE")
        RAISE.syntaxErrorFault(s"$context cancellation must be COOPERATIVE.")
      WorkflowCancellationMode.Cooperative
    }
    WorkflowLifecyclePolicy(deadline, cancellation, WorkflowSourceIdentity(lifecycle.location.flatMap(_.line)))
  }

  def normalizeWaits(
    section: Option[LogicalSection],
    workflowIdentity: String,
    states: Vector[CompositeStateMachineState],
    directFields: LogicalSection => Vector[(String, String)]
  ): Vector[WorkflowWaitDeclaration] = {
    val context = s"WORKFLOW '$workflowIdentity' WAIT"
    val entries = section.toVector.flatMap { waitsection =>
      if (directFields(waitsection).nonEmpty)
        RAISE.syntaxErrorFault(s"$context requires fields on named entries, not on the section.")
      val children = waitsection.blocks.sections.toVector
      if (children.isEmpty)
        RAISE.syntaxErrorFault(s"$context requires at least one named entry.")
      children
    }.map { entry =>
      val identity = _nonempty(entry.nameForModel, s"$context identity")
      val entrycontext = s"$context '$identity'"
      val values = _closed_fields(entry, Set("state", "delaymillis", "signal"), entrycontext, directFields)
      val state = values.get("state").getOrElse(RAISE.syntaxErrorFault(s"$entrycontext requires one direct state."))
      val candidates = states.filter(x => _normalize_key(x.name) == _normalize_key(state))
      if (candidates.size != 1)
        RAISE.syntaxErrorFault(s"$entrycontext state '$state' must resolve to exactly one CSM State.")
      val triggers = Vector("delaymillis", "signal").filter(values.contains)
      if (triggers.size != 1)
        RAISE.syntaxErrorFault(s"$entrycontext requires exactly one delay-millis or signal trigger.")
      val trigger = if (triggers.head == "delaymillis")
        WorkflowWaitTrigger.Timer(_unsigned_long(values("delaymillis"), 0, s"$entrycontext delay-millis"))
      else
        WorkflowWaitTrigger.Signal(values("signal"))
      WorkflowWaitDeclaration(identity, state, candidates.head.name, trigger, WorkflowSourceIdentity(entry.location.flatMap(_.line)))
    }
    _unique(entries.map(_.identity), s"$context identity")
    _unique(entries.map(_.stateIdentity), s"$context canonical State")
    entries
  }

  private def _closed_fields(
    section: LogicalSection,
    allowed: Set[String],
    context: String,
    directfields: LogicalSection => Vector[(String, String)]
  ): Map[String, String] = {
    if (section.blocks.sections.nonEmpty)
      RAISE.syntaxErrorFault(s"$context does not admit nested structural content.")
    val fields = directfields(section).map { case (name, value) => _normalize_key(name) -> value.trim }
    fields.find { case (name, _) => !allowed.contains(name) }.foreach { case (name, _) =>
      RAISE.syntaxErrorFault(s"$context does not admit field '$name'.")
    }
    fields.groupBy(_._1).collectFirst { case (name, values) if values.size > 1 => name }.foreach { name =>
      RAISE.syntaxErrorFault(s"$context field '$name' must be unique.")
    }
    fields.map { case (name, value) => name -> _nonempty(value, s"$context $name") }.toMap
  }

  private def _unsigned_long(value: String, minimum: Int, context: String): Long = {
    val text = value.trim
    if (!text.matches("[0-9]+"))
      RAISE.syntaxErrorFault(s"$context requires an unsigned base-10 integer.")
    val number = BigInt(text)
    if (number < minimum || number > BigInt(Long.MaxValue))
      RAISE.syntaxErrorFault(s"$context must be in [$minimum, ${Long.MaxValue}].")
    number.toLong
  }

  private def _unique(values: Vector[String], context: String): Unit =
    values.groupBy(_normalize_key).collectFirst { case (_, duplicates) if duplicates.size > 1 => duplicates.head }.foreach { value =>
      RAISE.syntaxErrorFault(s"$context '$value' must be unique.")
    }

  private def _nonempty(value: String, context: String): String =
    Option(value).map(_.trim).filter(_.nonEmpty).getOrElse(RAISE.syntaxErrorFault(s"$context is required."))

  private def _normalize_key(value: String): String = value.toLowerCase.filter(_.isLetterOrDigit)
}
