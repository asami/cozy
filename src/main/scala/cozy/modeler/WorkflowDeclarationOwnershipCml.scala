package cozy.modeler

import org.goldenport.RAISE
import org.goldenport.parser.LogicalSection

/*
 * @since   Oct.  3, 2026
 * @version Oct.  3, 2026
 * @author  ASAMI, Tomoharu
 */
/** Structural outcome ownership and the complete existing invocation ownership group. */
private[modeler] object WorkflowDeclarationOwnershipCml {
  private val _invocation_policy_fields = Set(
    "PARTICIPANT", "MAXIMUM-ATTEMPTS", "FIXED-RETRY-DELAY-MILLIS", "EXECUTION-TIMEOUT-MILLIS"
  )
  private val _composite_identity_sections = Set("CONSTITUENT", "STATE", "DERIVATION", "INITIAL", "ACTION", "CONSTITUENT-ACTION", "DERIVED-ACTION")
  private val _workflow_identity_sections = Set("OPERATION", "REQUIRED-OPERATION", "INVOCATION-POLICY", "WAIT")

  def rejectInvocationPolicyFields(
    section: LogicalSection,
    context: String,
    includeTarget: Boolean,
    directFields: LogicalSection => Vector[(String, String)]
  ): Unit = {
    val forbidden = if (includeTarget) _invocation_policy_fields + "ACTION" else _invocation_policy_fields
    directFields(section).collectFirst {
      case (fieldname, _) if forbidden.exists(_same_key(fieldname, _)) => fieldname
    }.foreach { fieldname =>
      RAISE.syntaxErrorFault(s"$context field '$fieldname' belongs in a Workflow INVOCATION-POLICY entry.")
    }
  }

  def rejectMisplacedInvocationPolicy(
    section: LogicalSection,
    context: String,
    directFields: LogicalSection => Vector[(String, String)]
  ): Unit = {
    if (_same_section_key(section, "INVOCATION-POLICY"))
      RAISE.syntaxErrorFault(s"$context INVOCATION-POLICY must be a direct Workflow definition section.")
    rejectInvocationPolicyFields(section, context, includeTarget = false, directFields = directFields)
    section.blocks.sections.foreach(rejectMisplacedInvocationPolicy(_, context, directFields))
  }

  def validateOwnership(roots: Vector[LogicalSection], directFields: LogicalSection => Vector[(String, String)]): Unit = {
    roots.foreach { root =>
      _reject_section(root)
      if (_same_section_key(root, "WORKFLOW")) {
        _reject_outcome_field(root, directFields)
        root.blocks.sections.foreach { definition =>
          val definitionshape = definition.blocks.sections.exists(_same_section_key(_, "COMPOSITE-STATEMACHINE")) ||
            directFields(definition).exists { case (name, _) => _same_key(name, "VERSION") }
          if (!definitionshape) {
            _reject_section(definition)
          }
          _reject_outcome_field(definition, directFields)
          definition.blocks.sections.foreach { section =>
            if (_same_section_key(section, "SEMANTIC-OUTCOME")) {
              _reject_outcome_field(section, directFields)
              section.blocks.sections.foreach { entry =>
                entry.blocks.sections.foreach(_reject_nested(_, true, directFields))
              }
            } else if (_same_section_key(section, "COMPOSITE-STATEMACHINE")) {
              _validate_composite(section, directFields)
            } else if (_workflow_identity_sections.exists(_same_section_key(section, _))) {
              _validate_entries(section, true, directFields)
            } else if (_same_section_key(section, "FAILURE-POLICY")) {
              _reject_outcome_field(section, directFields)
              section.blocks.sections.foreach { entry =>
                _reject_outcome_field(entry, directFields)
                entry.blocks.sections.foreach { rule =>
                  _reject_outcome_field(rule, directFields)
                  rule.blocks.sections.foreach(_reject_nested(_, true, directFields))
                }
              }
            } else {
              _reject_nested(section, true, directFields)
            }
          }
        }
      } else if (_same_section_key(root, "COMPOSITE-STATEMACHINE")) {
        _reject_outcome_field(root, directFields)
        root.blocks.sections.foreach(_validate_composite(_, directFields))
      } else {
        root.blocks.sections.foreach { definition =>
          definition.blocks.sections.foreach { section =>
            if (Set("STATE", "OPERATION").exists(_same_section_key(section, _))) {
              _validate_entries(section, false, directFields)
            } else if (_same_section_key(section, "STATE-MACHINE")) {
              section.blocks.sections.foreach { machine =>
                machine.blocks.sections.foreach { child =>
                  if (_same_section_key(child, "STATE")) _validate_entries(child, false, directFields)
                  else _reject_nested(child, false, directFields)
                }
              }
            } else {
              _reject_nested(section, false, directFields)
            }
          }
        }
      }
    }
  }

  private def _validate_composite(definition: LogicalSection, directfields: LogicalSection => Vector[(String, String)]): Unit = {
    _reject_outcome_field(definition, directfields)
    definition.blocks.sections.foreach { section =>
      if (_composite_identity_sections.exists(_same_section_key(section, _))) {
        _validate_entries(section, true, directfields)
      } else {
        _reject_nested(section, true, directfields)
      }
    }
  }

  private def _validate_entries(section: LogicalSection, contextual: Boolean, directfields: LogicalSection => Vector[(String, String)]): Unit = {
    _reject_section(section)
    if (contextual) _reject_outcome_field(section, directfields)
    section.blocks.sections.foreach { entry =>
      if (contextual) _reject_outcome_field(entry, directfields)
      entry.blocks.sections.foreach(_reject_nested(_, contextual, directfields))
    }
  }

  private def _reject_nested(section: LogicalSection, contextual: Boolean, directfields: LogicalSection => Vector[(String, String)]): Unit = {
    _reject_section(section)
    if (contextual) _reject_outcome_field(section, directfields)
    section.blocks.sections.foreach(_reject_nested(_, contextual, directfields))
  }

  private def _reject_section(section: LogicalSection): Unit = {
    if (_same_section_key(section, "SEMANTIC-OUTCOME")) {
      RAISE.syntaxErrorFault("SEMANTIC-OUTCOME must be a direct Workflow definition section.")
    }
  }

  private def _reject_outcome_field(section: LogicalSection, directfields: LogicalSection => Vector[(String, String)]): Unit = {
    directfields(section).collectFirst { case (name, _) if _same_key(name, "outcome") => name }.foreach { name =>
      RAISE.syntaxErrorFault(s"Field '$name' belongs in a Workflow SEMANTIC-OUTCOME entry.")
    }
  }

  private def _same_section_key(section: LogicalSection, name: String): Boolean =
    _same_key(section.keyForModel, name) || _same_key(section.nameForModel, name)

  private def _same_key(lhs: String, rhs: String): Boolean = _normalize_key(lhs) == _normalize_key(rhs)

  private def _normalize_key(value: String): String = value.toLowerCase.filter(_.isLetterOrDigit)
}
