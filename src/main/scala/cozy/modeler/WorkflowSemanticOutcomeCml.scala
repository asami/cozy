package cozy.modeler

import org.goldenport.RAISE
import org.goldenport.parser.LogicalSection
import org.goldenport.sm.{NameTransitionTo, StateMachineClass}

/*
 * @since   Oct.  3, 2026
 * @version Oct.  3, 2026
 * @author  ASAMI, Tomoharu
 */
/** Closed source normalization using the constituent evidence already admitted by CSM. */
private[modeler] object WorkflowSemanticOutcomeCml {
  private val _fields = Set("action", "participant", "outcome", "role", "from", "to", "on")

  def normalize(
    section: Option[LogicalSection],
    workflowIdentity: String,
    compositeStateMachine: CompositeStateMachineDefinition,
    requiredOperations: Vector[WorkflowRequiredOperation],
    resolvedConstituents: Vector[(CompositeStateMachineConstituent, StateMachineClass, String)],
    directFields: LogicalSection => Vector[(String, String)]
  ): Vector[WorkflowSemanticOutcomeDeclaration] = {
    val context = s"WORKFLOW '$workflowIdentity' SEMANTIC-OUTCOME"
    val entries = section.toVector.flatMap { container =>
      if (directFields(container).nonEmpty) {
        RAISE.syntaxErrorFault(s"$context requires fields on entries, not on the section.")
      }
      val children = container.blocks.sections.toVector
      if (children.isEmpty) {
        RAISE.syntaxErrorFault(s"$context requires at least one outcome entry.")
      }
      children
    }.map { entry =>
      val identity = _nonempty(entry.nameForModel, s"$context identity")
      val entrycontext = s"$context '$identity'"
      if (entry.blocks.sections.nonEmpty) {
        RAISE.syntaxErrorFault(s"$entrycontext does not admit nested structural content.")
      }
      val values = _closed_fields(entry, entrycontext, directFields)
      val targets = Vector("action", "participant").filter(values.contains)
      if (targets.size != 1) {
        RAISE.syntaxErrorFault(s"$entrycontext requires exactly one direct action or participant target.")
      }
      val targetname = values(targets.head)
      val (target, candidates) = if (targets.head == "action") {
        WorkflowInvocationTarget.Action(targetname) -> compositeStateMachine.actions.filter(x => _same_key(x.identity, targetname))
      } else {
        val mappings = requiredOperations.filter(x => _same_key(x.capability, targetname))
        if (mappings.size != 1) {
          RAISE.syntaxErrorFault(s"$entrycontext participant '$targetname' must resolve to one REQUIRED-OPERATION capability.")
        }
        WorkflowInvocationTarget.Participant(targetname) -> mappings.map(_.action)
      }
      if (candidates.size != 1) {
        RAISE.syntaxErrorFault(s"$entrycontext action '$targetname' must resolve to one declared Action.")
      }
      val action = candidates.head
      if (!_same_key(action.kind, "OPERATION") && !_same_key(action.kind, "JUDGMENT")) {
        RAISE.syntaxErrorFault(s"$entrycontext target must be OPERATION or JUDGMENT; ADMISSION is deterministic local semantics.")
      }
      val outcome = _required_value(values, "outcome", entrycontext) match {
        case "NEEDS_INPUT" => WorkflowSemanticOutcome.NeedsInput
        case "NEEDS_REVISION" => WorkflowSemanticOutcome.NeedsRevision
        case "REJECTED" => WorkflowSemanticOutcome.Rejected
        case _ => RAISE.syntaxErrorFault(s"$entrycontext outcome must be NEEDS_INPUT, NEEDS_REVISION or REJECTED.")
      }
      val role = _required_value(values, "role", entrycontext)
      val constituents = resolvedConstituents.filter(x => _same_key(x._1.role, role))
      if (constituents.size != 1) {
        RAISE.syntaxErrorFault(s"$entrycontext role '$role' must resolve to one CSM constituent.")
      }
      val (constituent, machine, reference) = constituents.head
      val from = _state(_required_value(values, "from", entrycontext), constituent.states, entrycontext, "from")
      val to = _state(_required_value(values, "to", entrycontext), constituent.states, entrycontext, "to")
      val on = _required_value(values, "on", entrycontext)
      val events = machine.rule.states.find(x => _same_key(x.name, from)).toVector.flatMap { state =>
        (state.transitions.call ++ state.transitions.global).flatMap { transition =>
          transition.to match {
            case NameTransitionTo(name) if _same_key(name, to) => transition.getEventName.filter(_same_key(_, on))
            case _ => None
          }
        }
      }
      if (events.size != 1) {
        RAISE.syntaxErrorFault(s"$entrycontext requires exactly one declared constituent transition '$role:$from-$on->$to'.")
      }
      WorkflowSemanticOutcomeDeclaration(
        identity, target, action.identity, outcome,
        WorkflowSemanticOutcomeTransition(constituent.role, reference, from, to, events.head),
        WorkflowSourceIdentity(entry.location.flatMap(_.line))
      )
    }
    entries.groupBy(x => _normalize_key(x.identity)).collectFirst {
      case (_, duplicates) if duplicates.size > 1 => duplicates.head.identity
    }.foreach { identity =>
      RAISE.syntaxErrorFault(s"$context identity '$identity' must be unique.")
    }
    entries.groupBy(x => (_normalize_key(x.actionIdentity), x.outcome)).collectFirst {
      case (_, duplicates) if duplicates.size > 1 => duplicates.head
    }.foreach { declaration =>
      RAISE.syntaxErrorFault(s"$context resolved Action '${declaration.actionIdentity}' outcome '${declaration.outcome.canonicalValue}' must be unique.")
    }
    entries
  }

  private def _closed_fields(section: LogicalSection, context: String, directfields: LogicalSection => Vector[(String, String)]): Map[String, String] = {
    val fields = directfields(section).map { case (name, value) => _normalize_key(name) -> value.trim }
    fields.find { case (name, _) => !_fields.contains(name) }.foreach { case (name, _) =>
      RAISE.syntaxErrorFault(s"$context does not admit field '$name'.")
    }
    fields.groupBy(_._1).collectFirst { case (name, values) if values.size > 1 => name }.foreach { name =>
      RAISE.syntaxErrorFault(s"$context field '$name' must be unique.")
    }
    fields.map { case (name, value) => name -> _nonempty(value, s"$context $name") }.toMap
  }

  private def _required_value(values: Map[String, String], name: String, context: String): String =
    values.getOrElse(name, RAISE.syntaxErrorFault(s"$context requires exactly one direct $name value."))

  private def _state(value: String, states: Vector[String], context: String, field: String): String =
    states.find(_same_key(_, value)).getOrElse(RAISE.syntaxErrorFault(s"$context $field references unknown state '$value'."))

  private def _nonempty(value: String, context: String): String =
    Option(value).map(_.trim).filter(_.nonEmpty).getOrElse(RAISE.syntaxErrorFault(s"$context is required."))

  private def _same_key(lhs: String, rhs: String): Boolean = _normalize_key(lhs) == _normalize_key(rhs)

  private def _normalize_key(value: String): String = value.toLowerCase.filter(_.isLetterOrDigit)
}
