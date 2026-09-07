package cozy.modeler

import org.goldenport.RAISE
import org.goldenport.kaleidox.{Model => KaleidoxModel}
import org.goldenport.kaleidox.CmlSectionFormat
import org.goldenport.parser.LogicalSection
import org.goldenport.sm.{NameTransitionTo, StateMachineClass}

import scala.collection.mutable

/*
 * @since   Sep.  7, 2026
 * @version Sep.  7, 2026
 * @author  ASAMI, Tomoharu
 */
/** Normalizes and validates the CSM-06 CML surface into the CSM-07 typed IR. */
private[modeler] object CompositeStateMachineCml {
  private final case class Constituent(
    machine: StateMachineClass,
    value: CompositeStateMachineConstituent
  )

  def validate(model: KaleidoxModel): Unit = {
    definitions(model)
    ()
  }

  def definitions(model: KaleidoxModel): Vector[CompositeStateMachineDefinition] = {
    val roots = model.divisions.toVector.flatMap(_logical_section)
    if (roots.exists(_same_section_key(_, "WORKFLOW")))
      RAISE.syntaxErrorFault("WORKFLOW is not admitted by the Phase 47 CML grammar.")
    val operations = _operation_contracts(model)
    roots.filter(_same_section_key(_, "COMPOSITE-STATEMACHINE")).flatMap(_normalize_definition_root(_, model, operations))
  }

  private def _normalize_definition_root(
    root: LogicalSection,
    model: KaleidoxModel,
    operations: Vector[CompositeStateMachineOperation]
  ): Vector[CompositeStateMachineDefinition] =
    root.blocks.sections.toVector.map(_normalize_definition(_, model, operations))

  private def _normalize_definition(
    definition: LogicalSection,
    model: KaleidoxModel,
    operations: Vector[CompositeStateMachineOperation]
  ): CompositeStateMachineDefinition = {
    val name = definition.nameForModel
    val allowed = Set("CONSTITUENT", "STATE", "DERIVATION", "INITIAL", "ACTION", "CONSTITUENT-ACTION", "DERIVED-ACTION")
    definition.blocks.sections.foreach { section =>
      if (!allowed.exists(_same_section_key(section, _)))
        RAISE.syntaxErrorFault(s"COMPOSITE-STATEMACHINE '$name' does not admit '${section.nameForModel}'.")
    }
    Vector("CONSTITUENT", "STATE", "DERIVATION").foreach { field =>
      if (_children(definition, field).size != 1)
        RAISE.syntaxErrorFault(s"COMPOSITE-STATEMACHINE '$name' requires exactly one $field section.")
    }
    Vector("INITIAL", "ACTION", "CONSTITUENT-ACTION", "DERIVED-ACTION").foreach { field =>
      if (_children(definition, field).size > 1)
        RAISE.syntaxErrorFault(s"COMPOSITE-STATEMACHINE '$name' accepts at most one $field section.")
    }
    val constituents = _constituents(_children(definition, "CONSTITUENT").head, model, name)
    val states = _states(_children(definition, "STATE").head, name)
    val derivations = _derivations(_children(definition, "DERIVATION").head, name, constituents, states)
    val initial = _children(definition, "INITIAL").headOption.map { section =>
      val configuration = _mapping(section, s"COMPOSITE-STATEMACHINE '$name' INITIAL", constituents)
      _validate_complete_mapping(configuration.bindings, s"COMPOSITE-STATEMACHINE '$name' INITIAL", constituents)
      configuration
    }
    _validate_derivation_analysis(name, constituents, derivations, initial)
    val actions = _actions(_children(definition, "ACTION").headOption, name, constituents, operations)
    val constituentactions = _constituent_actions(
      _children(definition, "CONSTITUENT-ACTION").headOption,
      name,
      constituents,
      actions
    )
    val derivedactions = _derived_actions(
      _children(definition, "DERIVED-ACTION").headOption,
      name,
      states,
      actions
    )
    CompositeStateMachineDefinition(
      identity = name,
      name = name,
      source = _source(definition),
      constituents = constituents.map(_.value),
      states = states,
      derivations = derivations,
      initialConfiguration = initial,
      actions = actions,
      constituentActions = constituentactions,
      derivedActions = derivedactions
    )
  }

  private def _constituents(
    section: LogicalSection,
    model: KaleidoxModel,
    context: String
  ): Vector[Constituent] = {
    val entries = section.blocks.sections.toVector.map { entry =>
      val role = _nonempty(entry.nameForModel, s"COMPOSITE-STATEMACHINE '$context' CONSTITUENT role")
      val machinename = _required_field(entry, "STATE-MACHINE", s"CONSTITUENT '$role'")
      val machine = Modeler._select_state_machine(model, machinename).getOrElse(
        RAISE.syntaxErrorFault(s"CONSTITUENT '$role' references unknown STATE-MACHINE '$machinename'.")
      )
      if (machine.rule.statemachines.nonEmpty)
        RAISE.syntaxErrorFault(s"CONSTITUENT '$role' STATE-MACHINE '$machinename' has hierarchy; CSM-03 hierarchy lowering remains deferred.")
      val states = machine.rule.states.map(_.name).filterNot(_.equalsIgnoreCase("INIT")).toVector
      if (states.isEmpty)
        RAISE.syntaxErrorFault(s"CONSTITUENT '$role' STATE-MACHINE '$machinename' requires a nonempty finite flat direct state universe.")
      _unique(states, s"CONSTITUENT '$role' STATE-MACHINE '$machinename' direct state")
      val subject = _field(entry, "SUBJECT")
      val subjecttype = _field(entry, "SUBJECT-TYPE")
      if (subject.nonEmpty != subjecttype.nonEmpty)
        RAISE.syntaxErrorFault(s"CONSTITUENT '$role' SUBJECT requires SUBJECT-TYPE.")
      Constituent(
        machine,
        CompositeStateMachineConstituent(
          role = role,
          stateMachine = CompositeStateMachineReference(machine.name),
          states = states,
          subject = subject.flatMap(name => subjecttype.map(kind => CompositeStateMachineSubject(name, kind))),
          source = _source(entry)
        )
      )
    }
    _unique(entries.map(_.value.role), s"COMPOSITE-STATEMACHINE '$context' CONSTITUENT role")
    entries
  }

  private def _states(section: LogicalSection, context: String): Vector[CompositeStateMachineState] = {
    val states = section.blocks.sections.toVector.map { entry =>
      CompositeStateMachineState(
        _nonempty(entry.nameForModel, s"COMPOSITE-STATEMACHINE '$context' STATE"),
        _source(entry)
      )
    }
    if (states.isEmpty)
      RAISE.syntaxErrorFault(s"COMPOSITE-STATEMACHINE '$context' STATE requires at least one declared composite state.")
    _unique(states.map(_.name), s"COMPOSITE-STATEMACHINE '$context' STATE")
    states
  }

  private def _derivations(
    section: LogicalSection,
    context: String,
    constituents: Vector[Constituent],
    states: Vector[CompositeStateMachineState]
  ): Vector[CompositeStateMachineDerivation] = {
    val derivations = section.blocks.sections.toVector.map { entry =>
      val identity = _nonempty(entry.nameForModel, s"COMPOSITE-STATEMACHINE '$context' DERIVATION identity")
      val statename = _required_field(entry, "STATE", s"DERIVATION '$identity'")
      _require_member(statename, states.map(_.name), s"DERIVATION '$identity' STATE")
      val state = states.find(x => _same_key(x.name, statename)).get.name
      val configuration = _mapping_field(entry, "WHEN", s"DERIVATION '$identity' WHEN", constituents)
      _validate_complete_mapping(configuration.bindings, s"DERIVATION '$identity' WHEN", constituents)
      CompositeStateMachineDerivation(identity, state, configuration, _source(entry))
    }
    if (derivations.isEmpty)
      RAISE.syntaxErrorFault(s"COMPOSITE-STATEMACHINE '$context' DERIVATION requires at least one stable rule.")
    _unique(derivations.map(_.identity), s"COMPOSITE-STATEMACHINE '$context' DERIVATION identity")
    derivations
  }

  private def _validate_derivation_analysis(
    context: String,
    constituents: Vector[Constituent],
    derivations: Vector[CompositeStateMachineDerivation],
    initial: Option[CompositeStateMachineConfiguration]
  ): Unit =
    initial.foreach { start =>
      _reachable_configurations(constituents, _as_mapping(start.bindings)).foreach { configuration =>
        val matches = derivations.filter(x => _as_mapping(x.configuration.bindings) == configuration)
        if (matches.isEmpty)
          RAISE.syntaxErrorFault(s"COMPOSITE-STATEMACHINE '$context' has uncovered reachable derivation configuration ${_show_mapping(configuration)}.")
        if (matches.size > 1)
          RAISE.syntaxErrorFault(s"COMPOSITE-STATEMACHINE '$context' has ambiguous reachable derivation configuration ${_show_mapping(configuration)}.")
      }
    }

  private def _reachable_configurations(
    constituents: Vector[Constituent],
    initial: Map[String, String]
  ): Set[Map[String, String]] = {
    val seen = mutable.Set(initial)
    val queue = mutable.Queue(initial)
    while (queue.nonEmpty) {
      val current = queue.dequeue()
      constituents.foreach { constituent =>
        val source = current(constituent.value.role)
        _next_states(constituent.machine, source, constituent.value.states).foreach { target =>
          val next = current.updated(constituent.value.role, target)
          if (!seen(next)) {
            seen += next
            queue.enqueue(next)
          }
        }
      }
    }
    seen.toSet
  }

  private def _next_states(
    machine: StateMachineClass,
    source: String,
    universe: Vector[String]
  ): Vector[String] =
    machine.rule.states.find(x => _same_key(x.name, source)).toVector.flatMap { state =>
      (state.transitions.call ++ state.transitions.global).collect {
        case transition if transition.to.isInstanceOf[NameTransitionTo] => transition.to.asInstanceOf[NameTransitionTo].name
      }.filter(target => universe.exists(_same_key(_, target)))
    }

  private def _actions(
    section: Option[LogicalSection],
    context: String,
    constituents: Vector[Constituent],
    operations: Vector[CompositeStateMachineOperation]
  ): Vector[CompositeStateMachineLogicalAction] = {
    val entries = section.toVector.flatMap(_.blocks.sections.toVector).map { entry =>
      val identity = _nonempty(entry.nameForModel, s"COMPOSITE-STATEMACHINE '$context' ACTION identity")
      val kind = _required_field(entry, "KIND", s"ACTION '$identity'")
      if (!_same_key(kind, "OPERATION"))
        RAISE.syntaxErrorFault(s"ACTION '$identity' KIND must be OPERATION; raw expression, script, provider, transaction, retry, and compensation syntax is not admitted.")
      val operationname = _required_field(entry, "OPERATION", s"ACTION '$identity'")
      val candidates = operations.filter(x => _same_key(x.name, operationname) || _same_key(s"${x.service}.${x.name}", operationname))
      if (candidates.size != 1)
        RAISE.syntaxErrorFault(s"ACTION '$identity' OPERATION '$operationname' must resolve to one normalized CML Operation.")
      val operation = candidates.head
      val input = _field(entry, "INPUT")
      (input, operation.inputType) match {
        case (None, None) =>
        case (None, Some(_)) => RAISE.syntaxErrorFault(s"ACTION '$identity' requires INPUT because OPERATION '$operationname' declares an input.")
        case (Some(_), None) => RAISE.syntaxErrorFault(s"ACTION '$identity' INPUT is invalid because OPERATION '$operationname' declares no input.")
        case (Some(binding), Some(inputtype)) => _validate_input_binding(identity, binding, inputtype, constituents)
      }
      CompositeStateMachineLogicalAction(
        identity = identity,
        kind = "OPERATION",
        operation = operation,
        inputBinding = input,
        source = _source(entry),
        metadata = _action_metadata(entry, identity)
      )
    }
    _unique(entries.map(_.identity), s"COMPOSITE-STATEMACHINE '$context' ACTION identity")
    entries
  }

  private val _action_metadata_fields = Vector(
    "EFFECT",
    "TRANSACTION",
    "IDEMPOTENCY",
    "IDEMPOTENCY-KEY",
    "COMPENSATION-HANDLER"
  )

  private val _direct_action_field_pattern = """^\s*(?:-\s*)?([A-Za-z][A-Za-z0-9_-]*)\s*(?:::|=)\s*(.*)$""".r

  private def _action_metadata(
    section: LogicalSection,
    identityname: String
  ): Option[CompositeStateMachineActionMetadata] = {
    _action_metadata_fields.foreach { fieldname =>
      if (_children(section, fieldname).nonEmpty)
        RAISE.syntaxErrorFault(s"ACTION '$identityname' metadata field '$fieldname' must be a direct field.")
    }
    val fields = _direct_action_field_entries(section).flatMap { case (fieldname, value) =>
      _action_metadata_fields.find(_same_key(_, fieldname)).map(_ -> value)
    }
    val duplicates = fields.groupBy(_._1).collect {
      case (fieldname, values) if values.size > 1 => fieldname
    }.toVector.sorted
    duplicates.headOption.foreach { fieldname =>
      RAISE.syntaxErrorFault(s"ACTION '$identityname' metadata field '$fieldname' must be unique.")
    }
    if (fields.isEmpty)
      None
    else {
      val values = fields.toMap
      val effectvalue = _required_action_metadata_value(values, "EFFECT", identityname)
      val transactionvalue = _required_action_metadata_value(values, "TRANSACTION", identityname)
      val idempotencyvalue = _required_action_metadata_value(values, "IDEMPOTENCY", identityname)
      val effectclass = effectvalue match {
        case "LOCAL" => CompositeStateMachineEffectClass.Local
        case "EXTERNAL" => CompositeStateMachineEffectClass.External
        case _ => RAISE.syntaxErrorFault(s"ACTION '$identityname' EFFECT must be LOCAL or EXTERNAL.")
      }
      val transactionrequirement = transactionvalue match {
        case "REQUIRED" => CompositeStateMachineTransactionRequirement.Required
        case "OUTSIDE_UNIT_OF_WORK" => CompositeStateMachineTransactionRequirement.OutsideUnitOfWork
        case _ => RAISE.syntaxErrorFault(s"ACTION '$identityname' TRANSACTION must be REQUIRED or OUTSIDE_UNIT_OF_WORK.")
      }
      val keyref = values.get("IDEMPOTENCY-KEY").map(_.trim)
      val idempotency = idempotencyvalue match {
        case "NOT_REQUIRED" =>
          if (keyref.nonEmpty)
            RAISE.syntaxErrorFault(s"ACTION '$identityname' IDEMPOTENCY-KEY is prohibited when IDEMPOTENCY is NOT_REQUIRED.")
          CompositeStateMachineIdempotency.NotRequired
        case "REQUIRED" =>
          CompositeStateMachineIdempotency.Required(keyref.filter(_.nonEmpty).getOrElse(
            RAISE.syntaxErrorFault(s"ACTION '$identityname' IDEMPOTENCY-KEY is required and must be nonempty when IDEMPOTENCY is REQUIRED.")
          ))
        case _ => RAISE.syntaxErrorFault(s"ACTION '$identityname' IDEMPOTENCY must be NOT_REQUIRED or REQUIRED.")
      }
      val compensationhandler = values.get("COMPENSATION-HANDLER").map { value =>
        _nonempty(value, s"ACTION '$identityname' COMPENSATION-HANDLER")
      }
      if (compensationhandler.nonEmpty &&
        (effectclass != CompositeStateMachineEffectClass.External ||
          transactionrequirement != CompositeStateMachineTransactionRequirement.OutsideUnitOfWork))
        RAISE.syntaxErrorFault(
          s"ACTION '$identityname' COMPENSATION-HANDLER requires EFFECT=EXTERNAL and TRANSACTION=OUTSIDE_UNIT_OF_WORK."
        )
      Some(CompositeStateMachineActionMetadata(
        effectClass = effectclass,
        transactionRequirement = transactionrequirement,
        idempotency = idempotency,
        compensationHandlerRef = compensationhandler
      ))
    }
  }

  private def _direct_action_field_entries(section: LogicalSection): Vector[(String, String)] = {
    val raw = section.blocks.blocks.toVector.flatMap {
      case _: LogicalSection => Vector.empty
      case block => block.getText.toVector.flatMap { text =>
        text.split("\\r?\\n").toVector.collect {
          case _direct_action_field_pattern(fieldname, value) => fieldname -> value.trim
        }
      }
    }
    val direct = CmlSectionFormat.directKeyValues(section).toVector
    raw ++ direct.filterNot { case (fieldname, value) =>
      raw.exists { case (rawfieldname, rawvalue) =>
        _same_key(fieldname, rawfieldname) && value.trim == rawvalue.trim
      }
    }
  }

  private def _required_action_metadata_value(
    values: Map[String, String],
    fieldname: String,
    identityname: String
  ): String =
    values.get(fieldname).map(_.trim).filter(_.nonEmpty).getOrElse(
      RAISE.syntaxErrorFault(s"ACTION '$identityname' metadata requires nonempty $fieldname.")
    )

  private def _validate_input_binding(
    actionname: String,
    binding: String,
    inputtype: String,
    constituents: Vector[Constituent]
  ): Unit = {
    val parts = binding.trim.split("\\.", 2).toVector
    if (parts.size != 2 || !_same_key(parts(1), "subject"))
      RAISE.syntaxErrorFault(s"ACTION '$actionname' INPUT must be <role>.subject.")
    val constituent = constituents.find(x => _same_key(x.value.role, parts.head)).getOrElse(
      RAISE.syntaxErrorFault(s"ACTION '$actionname' INPUT references unknown role '${parts.head}'.")
    )
    if (constituent.value.subject.isEmpty)
      RAISE.syntaxErrorFault(s"ACTION '$actionname' INPUT references role '${constituent.value.role}' without a typed SUBJECT.")
    if (!_same_key(constituent.value.subject.get.`type`, inputtype))
      RAISE.syntaxErrorFault(s"ACTION '$actionname' INPUT type '${constituent.value.subject.get.`type`}' must match OPERATION input '$inputtype'.")
  }

  private def _constituent_actions(
    section: Option[LogicalSection],
    context: String,
    constituents: Vector[Constituent],
    actions: Vector[CompositeStateMachineLogicalAction]
  ): Vector[CompositeStateMachineConstituentAction] = {
    val entries = section.toVector.flatMap(_.blocks.sections.toVector).map { entry =>
      val identity = _nonempty(entry.nameForModel, s"COMPOSITE-STATEMACHINE '$context' CONSTITUENT-ACTION identity")
      val role = _required_field(entry, "ROLE", s"CONSTITUENT-ACTION '$identity'")
      val constituent = constituents.find(x => _same_key(x.value.role, role)).getOrElse(
        RAISE.syntaxErrorFault(s"CONSTITUENT-ACTION '$identity' references unknown ROLE '$role'.")
      )
      val from = _required_field(entry, "FROM", s"CONSTITUENT-ACTION '$identity'")
      val to = _required_field(entry, "TO", s"CONSTITUENT-ACTION '$identity'")
      val on = _required_field(entry, "ON", s"CONSTITUENT-ACTION '$identity'")
      _require_member(from, constituent.value.states, s"CONSTITUENT-ACTION '$identity' FROM")
      _require_member(to, constituent.value.states, s"CONSTITUENT-ACTION '$identity' TO")
      if (!_has_transition(constituent.machine, from, to, on))
        RAISE.syntaxErrorFault(s"CONSTITUENT-ACTION '$identity' does not resolve constituent transition '$role:$from-$on->$to'.")
      val placement = _required_field(entry, "PLACEMENT", s"CONSTITUENT-ACTION '$identity'").toLowerCase
      if (!Set("exit", "transition", "entry").contains(placement))
        RAISE.syntaxErrorFault(s"CONSTITUENT-ACTION '$identity' PLACEMENT must be exit, transition, or entry.")
      val action = _action_reference(entry, identity, actions)
      CompositeStateMachineConstituentAction(
        identity,
        constituent.value.role,
        constituent.value.states.find(_same_key(_, from)).get,
        constituent.value.states.find(_same_key(_, to)).get,
        on,
        placement,
        action,
        _source(entry)
      )
    }
    _unique(entries.map(_.identity), s"COMPOSITE-STATEMACHINE '$context' CONSTITUENT-ACTION identity")
    entries
  }

  private def _has_transition(machine: StateMachineClass, from: String, to: String, on: String): Boolean =
    machine.rule.states.find(x => _same_key(x.name, from)).exists { source =>
      (source.transitions.call ++ source.transitions.global).exists { transition =>
        val target = transition.to match {
          case NameTransitionTo(name) => Some(name)
          case _ => None
        }
        target.exists(_same_key(_, to)) && transition.getEventName.exists(_same_key(_, on))
      }
    }

  private def _derived_actions(
    section: Option[LogicalSection],
    context: String,
    states: Vector[CompositeStateMachineState],
    actions: Vector[CompositeStateMachineLogicalAction]
  ): Vector[CompositeStateMachineDerivedAction] = {
    val entries = section.toVector.flatMap(_.blocks.sections.toVector).map { entry =>
      val identity = _nonempty(entry.nameForModel, s"COMPOSITE-STATEMACHINE '$context' DERIVED-ACTION identity")
      val from = _required_field(entry, "FROM", s"DERIVED-ACTION '$identity'")
      val to = _required_field(entry, "TO", s"DERIVED-ACTION '$identity'")
      _require_member(from, states.map(_.name), s"DERIVED-ACTION '$identity' FROM")
      _require_member(to, states.map(_.name), s"DERIVED-ACTION '$identity' TO")
      if (_same_key(from, to))
        RAISE.syntaxErrorFault(s"DERIVED-ACTION '$identity' requires distinct FROM and TO states.")
      val action = _action_reference(entry, identity, actions)
      CompositeStateMachineDerivedAction(
        identity,
        states.find(x => _same_key(x.name, from)).get.name,
        states.find(x => _same_key(x.name, to)).get.name,
        action,
        _source(entry)
      )
    }
    _unique(entries.map(_.derivedTransition), s"COMPOSITE-STATEMACHINE '$context' DERIVED-ACTION identity")
    entries
  }

  private def _action_reference(
    entry: LogicalSection,
    context: String,
    actions: Vector[CompositeStateMachineLogicalAction]
  ): CompositeStateMachineLogicalAction = {
    val action = _required_field(entry, "ACTION", context)
    actions.find(x => _same_key(x.identity, action)).getOrElse(
      RAISE.syntaxErrorFault(s"$context references unknown ACTION '$action'.")
    )
  }

  private def _mapping_field(
    section: LogicalSection,
    field: String,
    context: String,
    constituents: Vector[Constituent]
  ): CompositeStateMachineConfiguration =
    _field(section, field).map(_mapping_text(_, context, constituents, _source(section))).getOrElse(
      RAISE.syntaxErrorFault(s"$context is required.")
    )

  private def _mapping(
    section: LogicalSection,
    context: String,
    constituents: Vector[Constituent]
  ): CompositeStateMachineConfiguration = {
    val text = _section_text(section).orElse(_field(section, "WHEN")).getOrElse(
      RAISE.syntaxErrorFault(s"$context is required.")
    )
    _mapping_text(text, context, constituents, _source(section))
  }

  private def _mapping_text(
    text: String,
    context: String,
    constituents: Vector[Constituent],
    source: CompositeStateMachineSourceIdentity
  ): CompositeStateMachineConfiguration = {
    val entries = text.split("[,\\n]").toVector.map(_.trim).filter(_.nonEmpty).map { entry =>
      val parts = entry.split("\\.", 2).toVector
      if (parts.size != 2 || parts.exists(_.trim.isEmpty))
        RAISE.syntaxErrorFault(s"$context mapping '$entry' must be <role>.<state>.")
      parts.head.trim -> parts(1).trim
    }
    _unique(entries.map(_._1), s"$context role")
    val resolved = entries.map { case (role, state) =>
      constituents.find(x => _same_key(x.value.role, role)).map { constituent =>
        _require_member(state, constituent.value.states, s"$context state for role '$role'")
        constituent.value.role -> constituent.value.states.find(_same_key(_, state)).get
      }.getOrElse(RAISE.syntaxErrorFault(s"$context references unknown role '$role'."))
    }
    val bindings = constituents.flatMap { constituent =>
      resolved.find { case (role, _) => _same_key(constituent.value.role, role) }.map { case (_, state) =>
        CompositeStateMachineConfigurationBinding(
          constituent.value.role,
          state
        )
      }
    }
    CompositeStateMachineConfiguration(bindings, source)
  }

  private def _validate_complete_mapping(
    mapping: Vector[CompositeStateMachineConfigurationBinding],
    context: String,
    constituents: Vector[Constituent]
  ): Unit = {
    val missing = constituents.map(_.value.role).filterNot(role => mapping.exists(x => _same_key(x.role, role)))
    if (missing.nonEmpty)
      RAISE.syntaxErrorFault(s"$context is incomplete; missing roles ${missing.mkString(", ")}.")
  }

  private def _operation_contracts(model: KaleidoxModel): Vector[CompositeStateMachineOperation] =
    _root_sections(model, "SERVICE").flatMap { service =>
      service.blocks.sections.toVector.flatMap { serviceclass =>
        _children(serviceclass, "OPERATION").flatMap(_.blocks.sections.toVector).map { operation =>
          val input = _field(operation, "INPUT").orElse {
            _children(operation, "INPUT").headOption.flatMap(_field(_, "TYPE"))
          }
          CompositeStateMachineOperation(serviceclass.nameForModel, operation.nameForModel, input)
        }
      }
    }

  private def _root_sections(model: KaleidoxModel, name: String): Vector[LogicalSection] =
    model.divisions.toVector.flatMap { division =>
      _logical_section(division).filter { section =>
        _same_key(division.name, name) || _same_section_key(section, name)
      }
    }

  private def _logical_section(division: org.goldenport.kaleidox.Model.Division): Option[LogicalSection] =
    division match {
      case product: Product => product.productIterator.collectFirst { case section: LogicalSection => section }
      case _ => None
    }

  private def _children(section: LogicalSection, name: String): Vector[LogicalSection] =
    section.blocks.sections.toVector.filter(_same_section_key(_, name))

  private def _field(section: LogicalSection, name: String): Option[String] = {
    val direct = CmlSectionFormat.directKeyValues(section).collectFirst {
      case (key, value) if _same_key(key, name) && value.trim.nonEmpty => value.trim
    }
    direct.orElse(_children(section, name).headOption.flatMap(_section_text))
  }

  private def _section_text(section: LogicalSection): Option[String] =
    Option(section.blocks.text).map(_.trim).filter(_.nonEmpty)

  private def _required_field(section: LogicalSection, name: String, context: String): String =
    _field(section, name).getOrElse(RAISE.syntaxErrorFault(s"$context requires $name."))

  private def _require_member(value: String, members: Vector[String], context: String): Unit =
    if (!members.exists(_same_key(_, value)))
      RAISE.syntaxErrorFault(s"$context references unknown state '$value'.")

  private def _unique(values: Vector[String], context: String): Unit =
    values.groupBy(_normalize_key).collectFirst { case (_, duplicates) if duplicates.size > 1 => duplicates.head }.foreach { value =>
      RAISE.syntaxErrorFault(s"$context '$value' must be unique.")
    }

  private def _nonempty(value: String, context: String): String =
    Option(value).map(_.trim).filter(_.nonEmpty).getOrElse(RAISE.syntaxErrorFault(s"$context is required."))

  private def _as_mapping(bindings: Vector[CompositeStateMachineConfigurationBinding]): Map[String, String] =
    bindings.map(x => x.role -> x.state).toMap

  private def _show_mapping(mapping: Map[String, String]): String =
    mapping.toVector.sortBy { case (role, _) => _normalize_key(role) }.map { case (role, state) => s"$role.$state" }.mkString(", ")

  private def _source(section: LogicalSection): CompositeStateMachineSourceIdentity =
    CompositeStateMachineSourceIdentity(section.location.flatMap(_.line))

  private def _same_section_key(section: LogicalSection, name: String): Boolean =
    _same_key(section.keyForModel, name) || _same_key(section.nameForModel, name)

  private def _same_key(lhs: String, rhs: String): Boolean = _normalize_key(lhs) == _normalize_key(rhs)

  private def _normalize_key(value: String): String = value.toLowerCase.filter(_.isLetterOrDigit)
}
