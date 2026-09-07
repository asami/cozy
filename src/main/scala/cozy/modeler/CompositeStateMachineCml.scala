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
/** Normalizes and validates the CSM-06 CML surface; it deliberately retains no IR. */
private[modeler] object CompositeStateMachineCml {
  private final case class Constituent(role: String, machine: StateMachineClass, states: Vector[String], subject: Option[String], subjecttype: Option[String])
  private final case class Derivation(name: String, state: String, when: Map[String, String])
  private final case class OperationContract(service: String, name: String, inputtype: Option[String])

  def validate(model: KaleidoxModel): Unit = {
    val roots = model.divisions.toVector.flatMap(_logical_section)
    if (roots.exists(_same_section_key(_, "WORKFLOW")))
      RAISE.syntaxErrorFault("WORKFLOW is not admitted by the Phase 47 CML grammar.")
    roots.filter(_same_section_key(_, "COMPOSITE-STATEMACHINE")).foreach(_validate_definition_root(_, model))
  }

  private def _validate_definition_root(root: LogicalSection, model: KaleidoxModel): Unit =
    root.blocks.sections.toVector.foreach(_validate_definition(_, model, _operation_contracts(model)))

  private def _validate_definition(definition: LogicalSection, model: KaleidoxModel, operations: Vector[OperationContract]): Unit = {
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
    val initial = _children(definition, "INITIAL").headOption.map(_mapping(_, s"COMPOSITE-STATEMACHINE '$name' INITIAL", constituents))
    initial.foreach(_validate_complete_mapping(_, s"COMPOSITE-STATEMACHINE '$name' INITIAL", constituents))
    _validate_derivation_analysis(name, constituents, derivations, initial)
    val actions = _actions(_children(definition, "ACTION").headOption, name, constituents, operations)
    _constituent_actions(_children(definition, "CONSTITUENT-ACTION").headOption, name, constituents, actions)
    _derived_actions(_children(definition, "DERIVED-ACTION").headOption, name, states, actions)
  }

  private def _constituents(section: LogicalSection, model: KaleidoxModel, context: String): Vector[Constituent] = {
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
      Constituent(role, machine, states, subject, subjecttype)
    }
    _unique(entries.map(_.role), s"COMPOSITE-STATEMACHINE '$context' CONSTITUENT role")
    entries
  }

  private def _states(section: LogicalSection, context: String): Vector[String] = {
    val states = section.blocks.sections.toVector.map(x => _nonempty(x.nameForModel, s"COMPOSITE-STATEMACHINE '$context' STATE"))
    if (states.isEmpty)
      RAISE.syntaxErrorFault(s"COMPOSITE-STATEMACHINE '$context' STATE requires at least one declared composite state.")
    _unique(states, s"COMPOSITE-STATEMACHINE '$context' STATE")
    states
  }

  private def _derivations(section: LogicalSection, context: String, constituents: Vector[Constituent], states: Vector[String]): Vector[Derivation] = {
    val derivations = section.blocks.sections.toVector.map { entry =>
      val name = _nonempty(entry.nameForModel, s"COMPOSITE-STATEMACHINE '$context' DERIVATION identity")
      val state = _required_field(entry, "STATE", s"DERIVATION '$name'")
      _require_member(state, states, s"DERIVATION '$name' STATE")
      val when = _mapping_field(entry, "WHEN", s"DERIVATION '$name' WHEN", constituents)
      _validate_complete_mapping(when, s"DERIVATION '$name' WHEN", constituents)
      Derivation(name, state, when)
    }
    if (derivations.isEmpty)
      RAISE.syntaxErrorFault(s"COMPOSITE-STATEMACHINE '$context' DERIVATION requires at least one stable rule.")
    _unique(derivations.map(_.name), s"COMPOSITE-STATEMACHINE '$context' DERIVATION identity")
    derivations
  }

  private def _validate_derivation_analysis(context: String, constituents: Vector[Constituent], derivations: Vector[Derivation], initial: Option[Map[String, String]]): Unit =
    initial.foreach { start =>
      _reachable_configurations(constituents, start).foreach { configuration =>
        val matches = derivations.filter(_.when == configuration)
        if (matches.isEmpty)
          RAISE.syntaxErrorFault(s"COMPOSITE-STATEMACHINE '$context' has uncovered reachable derivation configuration ${_show_mapping(configuration)}.")
        if (matches.size > 1)
          RAISE.syntaxErrorFault(s"COMPOSITE-STATEMACHINE '$context' has ambiguous reachable derivation configuration ${_show_mapping(configuration)}.")
      }
    }

  private def _reachable_configurations(constituents: Vector[Constituent], initial: Map[String, String]): Set[Map[String, String]] = {
    val seen = mutable.Set(initial)
    val queue = mutable.Queue(initial)
    while (queue.nonEmpty) {
      val current = queue.dequeue()
      constituents.foreach { constituent =>
        val source = current(constituent.role)
        _next_states(constituent.machine, source, constituent.states).foreach { target =>
          val next = current.updated(constituent.role, target)
          if (!seen(next)) {
            seen += next
            queue.enqueue(next)
          }
        }
      }
    }
    seen.toSet
  }

  private def _next_states(machine: StateMachineClass, source: String, universe: Vector[String]): Vector[String] =
    machine.rule.states.find(x => _same_key(x.name, source)).toVector.flatMap { state =>
      (state.transitions.call ++ state.transitions.global).collect {
        case transition if transition.to.isInstanceOf[NameTransitionTo] => transition.to.asInstanceOf[NameTransitionTo].name
      }.filter(target => universe.exists(_same_key(_, target)))
    }

  private def _actions(section: Option[LogicalSection], context: String, constituents: Vector[Constituent], operations: Vector[OperationContract]): Map[String, OperationContract] = {
    val entries = section.toVector.flatMap(_.blocks.sections.toVector).map { entry =>
      val name = _nonempty(entry.nameForModel, s"COMPOSITE-STATEMACHINE '$context' ACTION identity")
      val kind = _required_field(entry, "KIND", s"ACTION '$name'")
      if (!_same_key(kind, "OPERATION"))
        RAISE.syntaxErrorFault(s"ACTION '$name' KIND must be OPERATION; raw expression, script, provider, transaction, retry, and compensation syntax is not admitted.")
      val operationname = _required_field(entry, "OPERATION", s"ACTION '$name'")
      val candidates = operations.filter(x => _same_key(x.name, operationname) || _same_key(s"${x.service}.${x.name}", operationname))
      if (candidates.size != 1)
        RAISE.syntaxErrorFault(s"ACTION '$name' OPERATION '$operationname' must resolve to one normalized CML Operation.")
      val operation = candidates.head
      val input = _field(entry, "INPUT")
      (input, operation.inputtype) match {
        case (None, None) =>
        case (None, Some(_)) => RAISE.syntaxErrorFault(s"ACTION '$name' requires INPUT because OPERATION '$operationname' declares an input.")
        case (Some(_), None) => RAISE.syntaxErrorFault(s"ACTION '$name' INPUT is invalid because OPERATION '$operationname' declares no input.")
        case (Some(binding), Some(inputtype)) => _validate_input_binding(name, binding, inputtype, constituents)
      }
      name -> operation
    }
    _unique(entries.map(_._1), s"COMPOSITE-STATEMACHINE '$context' ACTION identity")
    entries.toMap
  }

  private def _validate_input_binding(actionname: String, binding: String, inputtype: String, constituents: Vector[Constituent]): Unit = {
    val parts = binding.trim.split("\\.", 2).toVector
    if (parts.size != 2 || !_same_key(parts(1), "subject"))
      RAISE.syntaxErrorFault(s"ACTION '$actionname' INPUT must be <role>.subject.")
    val constituent = constituents.find(x => _same_key(x.role, parts.head)).getOrElse(
      RAISE.syntaxErrorFault(s"ACTION '$actionname' INPUT references unknown role '${parts.head}'.")
    )
    if (constituent.subject.isEmpty || constituent.subjecttype.isEmpty)
      RAISE.syntaxErrorFault(s"ACTION '$actionname' INPUT references role '${constituent.role}' without a typed SUBJECT.")
    if (!_same_key(constituent.subjecttype.get, inputtype))
      RAISE.syntaxErrorFault(s"ACTION '$actionname' INPUT type '${constituent.subjecttype.get}' must match OPERATION input '$inputtype'.")
  }

  private def _constituent_actions(section: Option[LogicalSection], context: String, constituents: Vector[Constituent], actions: Map[String, OperationContract]): Unit = {
    val identities = section.toVector.flatMap(_.blocks.sections.toVector).map { entry =>
      val name = _nonempty(entry.nameForModel, s"COMPOSITE-STATEMACHINE '$context' CONSTITUENT-ACTION identity")
      val role = _required_field(entry, "ROLE", s"CONSTITUENT-ACTION '$name'")
      val constituent = constituents.find(x => _same_key(x.role, role)).getOrElse(
        RAISE.syntaxErrorFault(s"CONSTITUENT-ACTION '$name' references unknown ROLE '$role'.")
      )
      val from = _required_field(entry, "FROM", s"CONSTITUENT-ACTION '$name'")
      val to = _required_field(entry, "TO", s"CONSTITUENT-ACTION '$name'")
      val on = _required_field(entry, "ON", s"CONSTITUENT-ACTION '$name'")
      _require_member(from, constituent.states, s"CONSTITUENT-ACTION '$name' FROM")
      _require_member(to, constituent.states, s"CONSTITUENT-ACTION '$name' TO")
      if (!_has_transition(constituent.machine, from, to, on))
        RAISE.syntaxErrorFault(s"CONSTITUENT-ACTION '$name' does not resolve constituent transition '$role:$from-$on->$to'.")
      val placement = _required_field(entry, "PLACEMENT", s"CONSTITUENT-ACTION '$name'").toLowerCase
      if (!Set("exit", "transition", "entry").contains(placement))
        RAISE.syntaxErrorFault(s"CONSTITUENT-ACTION '$name' PLACEMENT must be exit, transition, or entry.")
      _action_reference(entry, name, actions)
      name
    }
    _unique(identities, s"COMPOSITE-STATEMACHINE '$context' CONSTITUENT-ACTION identity")
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

  private def _derived_actions(section: Option[LogicalSection], context: String, states: Vector[String], actions: Map[String, OperationContract]): Unit = {
    val identities = section.toVector.flatMap(_.blocks.sections.toVector).map { entry =>
      val name = _nonempty(entry.nameForModel, s"COMPOSITE-STATEMACHINE '$context' DERIVED-ACTION identity")
      val from = _required_field(entry, "FROM", s"DERIVED-ACTION '$name'")
      val to = _required_field(entry, "TO", s"DERIVED-ACTION '$name'")
      _require_member(from, states, s"DERIVED-ACTION '$name' FROM")
      _require_member(to, states, s"DERIVED-ACTION '$name' TO")
      if (_same_key(from, to))
        RAISE.syntaxErrorFault(s"DERIVED-ACTION '$name' requires distinct FROM and TO states.")
      _action_reference(entry, name, actions)
      name
    }
    _unique(identities, s"COMPOSITE-STATEMACHINE '$context' DERIVED-ACTION identity")
  }

  private def _action_reference(entry: LogicalSection, context: String, actions: Map[String, OperationContract]): Unit = {
    val action = _required_field(entry, "ACTION", context)
    if (!actions.keys.exists(_same_key(_, action)))
      RAISE.syntaxErrorFault(s"$context references unknown ACTION '$action'.")
  }

  private def _mapping_field(section: LogicalSection, field: String, context: String, constituents: Vector[Constituent]): Map[String, String] =
    _field(section, field).map(_mapping_text(_, context, constituents)).getOrElse(
      RAISE.syntaxErrorFault(s"$context is required.")
    )

  private def _mapping(section: LogicalSection, context: String, constituents: Vector[Constituent]): Map[String, String] = {
    val text = _section_text(section).orElse(_field(section, "WHEN")).getOrElse(
      RAISE.syntaxErrorFault(s"$context is required.")
    )
    _mapping_text(text, context, constituents)
  }

  private def _mapping_text(text: String, context: String, constituents: Vector[Constituent]): Map[String, String] = {
    val entries = text.split("[,\\n]").toVector.map(_.trim).filter(_.nonEmpty).map { entry =>
      val parts = entry.split("\\.", 2).toVector
      if (parts.size != 2 || parts.exists(_.trim.isEmpty))
        RAISE.syntaxErrorFault(s"$context mapping '$entry' must be <role>.<state>.")
      parts.head.trim -> parts(1).trim
    }
    _unique(entries.map(_._1), s"$context role")
    val canonical = entries.map { case (role, state) =>
      constituents.find(x => _same_key(x.role, role)).map { constituent =>
        _require_member(state, constituent.states, s"$context state for role '$role'")
        constituent.role -> constituent.states.find(_same_key(_, state)).get
      }.getOrElse(RAISE.syntaxErrorFault(s"$context references unknown role '$role'."))
    }
    canonical.toMap
  }

  private def _validate_complete_mapping(mapping: Map[String, String], context: String, constituents: Vector[Constituent]): Unit = {
    val missing = constituents.map(_.role).filterNot(role => mapping.keys.exists(_same_key(_, role)))
    if (missing.nonEmpty)
      RAISE.syntaxErrorFault(s"$context is incomplete; missing roles ${missing.mkString(", ")}.")
  }

  private def _operation_contracts(model: KaleidoxModel): Vector[OperationContract] =
    _root_sections(model, "SERVICE").flatMap { service =>
      service.blocks.sections.toVector.flatMap { serviceclass =>
        _children(serviceclass, "OPERATION").flatMap(_.blocks.sections.toVector).map { operation =>
          val input = _field(operation, "INPUT").orElse {
            _children(operation, "INPUT").headOption.flatMap(_field(_, "TYPE"))
          }
          OperationContract(serviceclass.nameForModel, operation.nameForModel, input)
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

  private def _show_mapping(mapping: Map[String, String]): String =
    mapping.toVector.sortBy { case (role, _) => _normalize_key(role) }.map { case (role, state) => s"$role.$state" }.mkString(", ")

  private def _same_section_key(section: LogicalSection, name: String): Boolean =
    _same_key(section.keyForModel, name) || _same_key(section.nameForModel, name)

  private def _same_key(lhs: String, rhs: String): Boolean = _normalize_key(lhs) == _normalize_key(rhs)

  private def _normalize_key(value: String): String = value.toLowerCase.filter(_.isLetterOrDigit)
}
