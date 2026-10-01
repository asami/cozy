package cozy.modeler

import org.goldenport.RAISE
import org.goldenport.parser.LogicalSection

/*
 * @since   Oct.  2, 2026
 * @version Oct.  2, 2026
 */
/** Normalizes the Workflow-owned invocation declaration without expanding the CSM. */
private[modeler] object WorkflowInvocationPolicyCml {
  private val _allowed_fields = Set(
    "action", "participant", "maximumattempts", "fixedretrydelaymillis", "executiontimeoutmillis"
  )

  def normalize(
    section: Option[LogicalSection],
    workflowIdentity: String,
    actions: Vector[CompositeStateMachineLogicalAction],
    requiredOperations: Vector[WorkflowRequiredOperation],
    directFields: LogicalSection => Vector[(String, String)]
  ): Vector[WorkflowInvocationPolicy] = {
    val context = s"WORKFLOW '$workflowIdentity' INVOCATION-POLICY"
    val entries = section.toVector.flatMap { policysection =>
      if (directFields(policysection).nonEmpty)
        RAISE.syntaxErrorFault(s"$context requires fields on policy entries, not on the section.")
      val children = policysection.blocks.sections.toVector
      if (children.isEmpty)
        RAISE.syntaxErrorFault(s"$context requires at least one policy entry.")
      children
    }.map { entry =>
      val identity = _nonempty(entry.nameForModel, s"$context identity")
      val entrycontext = s"$context '$identity'"
      if (entry.blocks.sections.nonEmpty)
        RAISE.syntaxErrorFault(s"$entrycontext does not admit nested structural content.")
      val fields = directFields(entry).map { case (name, value) => _normalize_key(name) -> value }
      fields.find { case (name, _) => !_allowed_fields.contains(name) }.foreach { case (name, _) =>
        RAISE.syntaxErrorFault(s"$entrycontext does not admit field '$name'.")
      }
      fields.groupBy(_._1).collectFirst { case (name, values) if values.size > 1 => name }.foreach { name =>
        RAISE.syntaxErrorFault(s"$entrycontext field '$name' must be unique.")
      }
      val values = fields.toMap
      val targets = Vector("action", "participant").filter(values.contains)
      if (targets.size != 1)
        RAISE.syntaxErrorFault(s"$entrycontext requires exactly one direct action or participant target.")
      val targetname = _nonempty(values(targets.head), s"$entrycontext target")
      val (target, candidates) = if (targets.head == "action") {
        WorkflowInvocationTarget.Action(targetname) -> actions.filter(x => _same_key(x.identity, targetname))
      } else {
        val mappings = requiredOperations.filter(x => _same_key(x.capability, targetname))
        if (mappings.size != 1)
          RAISE.syntaxErrorFault(s"$entrycontext participant '$targetname' must resolve to one REQUIRED-OPERATION capability.")
        WorkflowInvocationTarget.Participant(targetname) -> mappings.map(_.action)
      }
      if (candidates.size != 1)
        RAISE.syntaxErrorFault(s"$entrycontext action '$targetname' must resolve to one declared Action.")
      val action = candidates.head
      if (!_same_key(action.kind, "OPERATION") && !_same_key(action.kind, "JUDGMENT"))
        RAISE.syntaxErrorFault(s"$entrycontext target must be OPERATION or JUDGMENT; ADMISSION is deterministic local semantics.")
      val attempts = values.get("maximumattempts")
      val delay = values.get("fixedretrydelaymillis")
      if (attempts.nonEmpty != delay.nonEmpty)
        RAISE.syntaxErrorFault(s"$entrycontext maximum-attempts and fixed-retry-delay-millis must be declared together.")
      val retry = attempts.map { value =>
        WorkflowRetryPolicy(
          _unsigned_integer(value, 1, BigInt(Int.MaxValue), s"$entrycontext maximum-attempts").toInt,
          _unsigned_integer(delay.get, 0, BigInt(Long.MaxValue), s"$entrycontext fixed-retry-delay-millis").toLong
        )
      }
      val timeout = values.get("executiontimeoutmillis").map { value =>
        _unsigned_integer(value, 1, BigInt(Long.MaxValue), s"$entrycontext execution-timeout-millis").toLong
      }
      if (retry.isEmpty && timeout.isEmpty)
        RAISE.syntaxErrorFault(s"$entrycontext requires retry or execution timeout or both.")
      WorkflowInvocationPolicy(
        identity, target, action.identity, retry, timeout, WorkflowSourceIdentity(entry.location.flatMap(_.line))
      )
    }
    _unique(entries.map(_.identity), s"$context identity")
    _unique(entries.map(_.actionIdentity), s"$context resolved Action")
    entries
  }

  private def _unsigned_integer(value: String, minimum: Int, maximum: BigInt, context: String): BigInt = {
    val text = value.trim
    if (!text.matches("[0-9]+"))
      RAISE.syntaxErrorFault(s"$context requires an unsigned base-10 integer.")
    val number = BigInt(text)
    if (number < minimum || number > maximum)
      RAISE.syntaxErrorFault(s"$context must be in [$minimum, $maximum].")
    number
  }

  private def _unique(values: Vector[String], context: String): Unit =
    values.groupBy(_normalize_key).collectFirst { case (_, duplicates) if duplicates.size > 1 => duplicates.head }.foreach { value =>
      RAISE.syntaxErrorFault(s"$context '$value' must be unique.")
    }

  private def _nonempty(value: String, context: String): String =
    Option(value).map(_.trim).filter(_.nonEmpty).getOrElse(RAISE.syntaxErrorFault(s"$context is required."))

  private def _same_key(lhs: String, rhs: String): Boolean = _normalize_key(lhs) == _normalize_key(rhs)

  private def _normalize_key(value: String): String = value.toLowerCase.filter(_.isLetterOrDigit)
}
