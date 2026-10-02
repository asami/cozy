package cozy.modeler

import org.goldenport.RAISE
import org.goldenport.parser.LogicalSection

/*
 * @since   Oct.  2, 2026
 * @version Oct.  3, 2026
 * @author  ASAMI, Tomoharu
 */
/** Closed normalization and structural ownership for Workflow execution metadata. */
private[modeler] object WorkflowExecutionSafetyPolicyCml {
  private val _lifecycle_sections = Set("LIFECYCLE", "WAIT")
  private val _lifecycle_fields = Set("deadlinemillis", "cancellation", "delaymillis")
  private val _safety_sections = Set("FAILURE-POLICY", "EXECUTION-SAFETY")
  private val _safety_fields = Set("defaultclassification", "logicalexecutionidentityref", "duplicateprotection", "idempotencykeyref")
  private val _execution_safety_fields = Set("logicalexecutionidentityref", "duplicateprotection", "idempotencykeyref")
  private val _composite_identity_sections = Set("CONSTITUENT", "STATE", "DERIVATION", "INITIAL", "ACTION", "CONSTITUENT-ACTION", "DERIVED-ACTION")
  private val _workflow_identity_sections = Set("OPERATION", "REQUIRED-OPERATION", "INVOCATION-POLICY", "SEMANTIC-OUTCOME")

  def normalizeFailurePolicies(
    section: Option[LogicalSection],
    workflowIdentity: String,
    actions: Vector[CompositeStateMachineLogicalAction],
    requiredOperations: Vector[WorkflowRequiredOperation],
    directFields: LogicalSection => Vector[(String, String)]
  ): Vector[WorkflowFailurePolicy] = {
    val context = s"WORKFLOW '$workflowIdentity' FAILURE-POLICY"
    val entries = section.toVector.flatMap { policysection =>
      if (directFields(policysection).nonEmpty) {
        RAISE.syntaxErrorFault(s"$context requires fields on policy entries, not on the section.")
      }
      val children = policysection.blocks.sections.toVector
      if (children.isEmpty) {
        RAISE.syntaxErrorFault(s"$context requires at least one policy entry.")
      }
      children
    }.map { entry =>
      val identity = _nonempty(entry.nameForModel, s"$context identity")
      val entrycontext = s"$context '$identity'"
      val values = _closed_fields(entry, Set("action", "participant", "defaultclassification"), entrycontext, directFields)
      val targets = Vector("action", "participant").filter(values.contains)
      if (targets.size != 1) {
        RAISE.syntaxErrorFault(s"$entrycontext requires exactly one direct action or participant target.")
      }
      val targetname = values(targets.head)
      val (target, candidates) = if (targets.head == "action") {
        WorkflowInvocationTarget.Action(targetname) -> actions.filter(x => _same_key(x.identity, targetname))
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
      val defaultclassification = _classification(_required_value(values, "defaultclassification", entrycontext), entrycontext)
      val rules = entry.blocks.sections.toVector.map { rule =>
        val ruleidentity = _nonempty(rule.nameForModel, s"$entrycontext failure-rule identity")
        val rulecontext = s"$entrycontext failure-rule '$ruleidentity'"
        if (rule.blocks.sections.nonEmpty) {
          RAISE.syntaxErrorFault(s"$rulecontext does not admit nested structural content.")
        }
        val rulevalues = _closed_fields(rule, Set("classification"), rulecontext, directFields)
        WorkflowFailureRule(ruleidentity, _classification(_required_value(rulevalues, "classification", rulecontext), rulecontext), _source(rule))
      }
      rules.groupBy(_.identity).collectFirst { case (name, duplicates) if duplicates.size > 1 => name }.foreach { name =>
        RAISE.syntaxErrorFault(s"$entrycontext failure-rule '$name' must be unique by exact identity.")
      }
      WorkflowFailurePolicy(identity, target, action.identity, defaultclassification, rules, _source(entry))
    }
    _unique(entries.map(_.identity), s"$context identity")
    _unique(entries.map(_.actionIdentity), s"$context resolved Action")
    entries
  }

  def normalizeExecutionSafety(
    section: Option[LogicalSection],
    workflowIdentity: String,
    directFields: LogicalSection => Vector[(String, String)]
  ): Option[WorkflowExecutionSafetyPolicy] = {
    section.map { safety =>
      val context = s"WORKFLOW '$workflowIdentity' EXECUTION-SAFETY"
      if (safety.blocks.sections.nonEmpty) {
        RAISE.syntaxErrorFault(s"$context does not admit nested structural content.")
      }
      val values = _closed_fields(safety, _execution_safety_fields, context, directFields)
      val identityref = _required_value(values, "logicalexecutionidentityref", context)
      val protection = _required_value(values, "duplicateprotection", context) match {
        case "REQUIRED" =>
          WorkflowDuplicateProtection.Required(_required_value(values, "idempotencykeyref", context))
        case "NOT_REQUIRED" =>
          if (values.contains("idempotencykeyref")) {
            RAISE.syntaxErrorFault(s"$context idempotency-key-ref is prohibited when duplicate-protection is NOT_REQUIRED.")
          }
          WorkflowDuplicateProtection.NotRequired
        case _ => RAISE.syntaxErrorFault(s"$context duplicate-protection must be REQUIRED or NOT_REQUIRED.")
      }
      WorkflowExecutionSafetyPolicy(identityref, protection, _source(safety))
    }
  }

  /** Both public CML extraction routes share the complete legacy ownership guard. */
  def validateOwnership(roots: Vector[LogicalSection], directFields: LogicalSection => Vector[(String, String)]): Unit = {
    roots.foreach { root =>
      if ((_lifecycle_sections ++ _safety_sections).exists(_same_section_key(root, _))) {
        RAISE.syntaxErrorFault(s"${root.nameForModel} must be a direct Workflow definition section.")
      } else if (_same_section_key(root, "WORKFLOW")) {
        _reject_fields(root, Set.empty, true, directFields)
        root.blocks.sections.foreach { definition =>
          val definitionshape = _children(definition, "COMPOSITE-STATEMACHINE").nonEmpty ||
            directFields(definition).exists { case (fieldname, _) => _same_key(fieldname, "VERSION") }
          if ((_lifecycle_sections ++ _safety_sections).exists(_same_section_key(definition, _)) && !definitionshape) {
            RAISE.syntaxErrorFault("LIFECYCLE, WAIT, FAILURE-POLICY and EXECUTION-SAFETY must be direct Workflow definition sections, not Workflow root sections.")
          }
          _reject_fields(definition, Set.empty, true, directFields)
          definition.blocks.sections.foreach { section =>
            if (_same_section_key(section, "COMPOSITE-STATEMACHINE")) {
              _validate_composite_ownership(section, directFields)
            } else if (_workflow_identity_sections.exists(_same_section_key(section, _))) {
              _validate_identity_entries(section, Set.empty, directFields)
            } else if (_same_section_key(section, "FAILURE-POLICY")) {
              _reject_fields(section, Set.empty, true, directFields)
              section.blocks.sections.foreach { entry =>
                _reject_fields(entry, Set("defaultclassification"), true, directFields)
                entry.blocks.sections.foreach { rule =>
                  _reject_fields(rule, Set("classification"), true, directFields)
                  rule.blocks.sections.foreach(_reject_misplaced_metadata(_, true, directFields))
                }
              }
            } else if (_same_section_key(section, "EXECUTION-SAFETY")) {
              _reject_fields(section, _execution_safety_fields, true, directFields)
              section.blocks.sections.foreach(_reject_misplaced_metadata(_, true, directFields))
            } else if (_same_section_key(section, "WAIT")) {
              _validate_identity_entries(section, _lifecycle_fields, directFields)
            } else if (_same_section_key(section, "LIFECYCLE")) {
              _reject_fields(section, _lifecycle_fields, true, directFields)
              section.blocks.sections.foreach(_reject_misplaced_metadata(_, true, directFields))
            } else {
              _reject_misplaced_metadata(section, true, directFields)
            }
          }
        }
      } else if (_same_section_key(root, "COMPOSITE-STATEMACHINE")) {
        _reject_fields(root, Set.empty, true, directFields)
        root.blocks.sections.foreach(_validate_composite_ownership(_, directFields))
      } else {
        _reject_fields(root, Set.empty, false, directFields)
        root.blocks.sections.foreach { definition =>
          _reject_fields(definition, Set.empty, false, directFields)
          definition.blocks.sections.foreach { section =>
            if (Set("OPERATION", "STATE").exists(_same_section_key(section, _))) {
              _validate_unrelated_identity_entries(section, directFields)
            } else {
              _reject_misplaced_metadata(section, false, directFields, lifecycle = false)
            }
          }
        }
      }
    }
  }

  private def _validate_composite_ownership(definition: LogicalSection, directfields: LogicalSection => Vector[(String, String)]): Unit = {
    _reject_fields(definition, Set.empty, true, directfields)
    definition.blocks.sections.foreach { section =>
      if (_composite_identity_sections.exists(_same_section_key(section, _))) {
        _validate_identity_entries(section, Set.empty, directfields)
      } else {
        _reject_misplaced_metadata(section, true, directfields)
      }
    }
  }

  private def _validate_identity_entries(section: LogicalSection, allowed: Set[String], directfields: LogicalSection => Vector[(String, String)]): Unit = {
    _reject_fields(section, allowed, true, directfields)
    section.blocks.sections.foreach { entry =>
      _reject_fields(entry, allowed, true, directfields)
      entry.blocks.sections.foreach(_reject_misplaced_metadata(_, true, directfields))
    }
  }

  private def _validate_unrelated_identity_entries(section: LogicalSection, directfields: LogicalSection => Vector[(String, String)]): Unit = {
    _reject_fields(section, Set.empty, false, directfields)
    section.blocks.sections.foreach { entry =>
      _reject_fields(entry, Set.empty, false, directfields)
      entry.blocks.sections.foreach(_reject_misplaced_metadata(_, false, directfields, lifecycle = false))
    }
  }

  private def _reject_misplaced_metadata(
    section: LogicalSection,
    classification: Boolean,
    directfields: LogicalSection => Vector[(String, String)],
    lifecycle: Boolean = true
  ): Unit = {
    val forbidden = if (lifecycle) _lifecycle_sections ++ _safety_sections else _safety_sections
    if (forbidden.exists(_same_section_key(section, _))) {
      RAISE.syntaxErrorFault(s"${section.nameForModel} must be a direct Workflow definition section.")
    }
    _reject_fields(section, Set.empty, classification, directfields)
    section.blocks.sections.foreach(_reject_misplaced_metadata(_, classification, directfields, lifecycle))
  }

  private def _reject_fields(
    section: LogicalSection,
    allowed: Set[String],
    classification: Boolean,
    directfields: LogicalSection => Vector[(String, String)]
  ): Unit = {
    val reserved = _lifecycle_fields ++ _safety_fields ++ (if (classification) Set("classification") else Set.empty[String])
    directfields(section).collectFirst {
      case (fieldname, _) if reserved.contains(_normalize_key(fieldname)) && !allowed.contains(_normalize_key(fieldname)) => fieldname
    }.foreach { fieldname =>
      RAISE.syntaxErrorFault(s"Field '$fieldname' belongs in its Workflow LIFECYCLE, WAIT, FAILURE-POLICY or EXECUTION-SAFETY owned position.")
    }
  }

  private def _closed_fields(
    section: LogicalSection,
    allowed: Set[String],
    context: String,
    directfields: LogicalSection => Vector[(String, String)]
  ): Map[String, String] = {
    val fields = directfields(section).map { case (name, value) => _normalize_key(name) -> value.trim }
    fields.find { case (name, _) => !allowed.contains(name) }.foreach { case (name, _) =>
      RAISE.syntaxErrorFault(s"$context does not admit field '$name'.")
    }
    fields.groupBy(_._1).collectFirst { case (name, values) if values.size > 1 => name }.foreach { name =>
      RAISE.syntaxErrorFault(s"$context field '$name' must be unique.")
    }
    fields.map { case (name, value) => name -> _nonempty(value, s"$context $name") }.toMap
  }

  private def _required_value(values: Map[String, String], name: String, context: String): String = {
    values.getOrElse(name, RAISE.syntaxErrorFault(s"$context requires exactly one direct $name value."))
  }

  private def _classification(value: String, context: String): WorkflowFailureClassification = {
    value match {
      case "RETRYABLE" => WorkflowFailureClassification.Retryable
      case "PERMANENT" => WorkflowFailureClassification.Permanent
      case _ => RAISE.syntaxErrorFault(s"$context classification must be RETRYABLE or PERMANENT.")
    }
  }

  private def _unique(values: Vector[String], context: String): Unit = {
    values.groupBy(_normalize_key).collectFirst { case (_, duplicates) if duplicates.size > 1 => duplicates.head }.foreach { value =>
      RAISE.syntaxErrorFault(s"$context '$value' must be unique.")
    }
  }

  private def _nonempty(value: String, context: String): String = {
    Option(value).map(_.trim).filter(_.nonEmpty).getOrElse(RAISE.syntaxErrorFault(s"$context is required."))
  }

  private def _source(section: LogicalSection): WorkflowSourceIdentity = WorkflowSourceIdentity(section.location.flatMap(_.line))

  private def _children(section: LogicalSection, name: String): Vector[LogicalSection] = {
    section.blocks.sections.toVector.filter(_same_section_key(_, name))
  }

  private def _same_section_key(section: LogicalSection, name: String): Boolean = {
    _same_key(section.keyForModel, name) || _same_key(section.nameForModel, name)
  }

  private def _same_key(lhs: String, rhs: String): Boolean = _normalize_key(lhs) == _normalize_key(rhs)

  private def _normalize_key(value: String): String = value.toLowerCase.filter(_.isLetterOrDigit)
}
