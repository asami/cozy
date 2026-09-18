package cozy.modeler

import org.simplemodeling.model._
import org.goldenport.RAISE
import org.goldenport.sm._
import org.goldenport.sm.StateMachineClass
import org.goldenport.kaleidox.model.SchemaModel
import org.goldenport.kaleidox.model.EntityModel.EntityClass
import org.goldenport.kaleidox.model.CmlExpressionGuard
import scala.util.control.NonFatal

/*
 * @since Sep. 18, 2026
 * @author ASAMI, Tomoharu
 */
private[modeler] final class StateMachineNormalizationProjector(val context: ModelBuildContext) {
  import context._
  import Modeler._

  private case class NormalizationTransitionDefinition(
    source: Option[MComponent.StateMachineStateIdentity],
    sourcestate: Option[StateClass],
    transition: Transition,
    iscalltransition: Boolean,
    declarationpath: Vector[String]
  )

  private final case class NormalizationFailure(
    code: String,
    message: String,
    location: MComponent.StateMachineSourceLocation,
    transition: Option[MComponent.StateMachineTransitionIdentity] = None
  ) extends RuntimeException(message)

  private[modeler] def normalize(
    p: StateMachineClass,
    entityClass: Option[EntityClass] = None
  ): MComponent.StateMachineNormalization =
    _normalization_result(p, entityClass)

  private def _normalization_result(
    sm: StateMachineClass,
    entityclass: Option[EntityClass] = None
  ): MComponent.StateMachineNormalization =
    try {
      MComponent.StateMachineNormalization.Accepted(_normalized_state_machine(sm, entityclass))
    } catch {
      case failure: NormalizationFailure =>
        MComponent.StateMachineNormalization.Rejected(
          Vector(
            MComponent.StateMachineNormalizationDiagnostic(
              code = failure.code,
              message = failure.message,
              machine = MComponent.StateMachineIdentity(sm.name),
              sourceLocation = failure.location,
              transition = failure.transition
            )
          )
        )
      case NonFatal(error) =>
        MComponent.StateMachineNormalization.Rejected(
          Vector(
            MComponent.StateMachineNormalizationDiagnostic(
              code = _normalization_diagnostic_code(error),
              message = _normalization_diagnostic_message(error),
              machine = MComponent.StateMachineIdentity(sm.name),
              sourceLocation = _machine_source_location(sm.name)
            )
          )
        )
    }

  private def _machine_source_location(
    machinename: String
  ): MComponent.StateMachineSourceLocation =
    MComponent.StateMachineSourceLocation(Vector("StateMachine", machinename))

  private def _transition_source_location(
    machine: MComponent.StateMachineIdentity,
    declarationpath: Vector[String]
  ): MComponent.StateMachineSourceLocation =
    MComponent.StateMachineSourceLocation(
      Vector("StateMachine", machine.qualifiedName) ++ declarationpath
    )

  private def _normalization_failure(
    code: String,
    message: String,
    sourcelocation: MComponent.StateMachineSourceLocation,
    transition: Option[MComponent.StateMachineTransitionIdentity] = None
  ): Nothing =
    throw NormalizationFailure(code, message, sourcelocation, transition)

  private def _normalization_diagnostic_code(error: Throwable): String = {
    val message = Option(error.getMessage).getOrElse("")
    if (message.contains("unadmitted legacy raw expression"))
      "legacy-raw-expression-not-admitted"
    else if (message.contains("not an admitted named local action"))
      "legacy-raw-action-not-admitted"
    else if (message.contains("ambiguous StateMachine state name"))
      "ambiguous-state-name"
    else if (message.contains("transition source"))
      "invalid-source-state"
    else if (message.contains("transition target"))
      "invalid-target-state"
    else if (message.contains("history composite"))
      "invalid-history-target"
    else if (message.contains("HISTORY-FIELD"))
      "invalid-history-field"
    else
      "state-machine-normalization-rejected"
  }

  private def _normalization_diagnostic_message(error: Throwable): String =
    _normalization_diagnostic_code(error) match {
      case "legacy-raw-expression-not-admitted" =>
        "A legacy raw expression is not admitted to the typed StateMachine predicate contract."
      case "legacy-raw-action-not-admitted" =>
        "A legacy raw action is not admitted to the named local-action contract."
      case "ambiguous-state-name" =>
        "The StateMachine declaration has an ambiguous state name."
      case "invalid-source-state" =>
        "The StateMachine declaration references an invalid source state."
      case "invalid-target-state" =>
        "The StateMachine declaration references an invalid target state."
      case "invalid-history-target" =>
        "The StateMachine declaration references an invalid named shallow-history target."
      case "invalid-history-field" =>
        "The StateMachine declaration references an invalid history field."
      case _ =>
        "The StateMachine declaration cannot be normalized to the typed contract."
    }

  /**
   * Produces the Phase-63 closed declaration model.  This deliberately
   * consumes the parsed non-Workflow StateMachine AST only; Workflow CML is
   * normalized by CompositeStateMachineCml on its own workstream.
   *
   * The existing StateMachineTransitionRule remains the legacy generation
   * carrier.  Phase 63.1 is responsible for carrying this normalized model
   * into generated ABI and CNCF execution.
   */
  private def _normalized_state_machine(
    sm: StateMachineClass,
    entityclass: Option[EntityClass]
  ): MComponent.NormalizedStateMachine = {
    val machine = MComponent.StateMachineIdentity(sm.name)
    _validate_normalizable_topology(sm.name, sm.rule)
    val historyfieldname = entityclass.map(_history_field_name(_, sm)).getOrElse(sm.rule.historyFieldName)
    val states = _normalized_state_identities(machine, sm.rule)
    val statesbyname = _unique_normalized_states(sm.name, states)
    val composites = _normalized_composite_identities(machine, sm.rule)
    val compositesbyname = _unique_normalized_states(sm.name, composites.map(_._1))
    val fallbacks = composites.map { case (composite, leaves) =>
      composite -> leaves.headOption
    }.toMap
    val legacystatemap = _state_map(sm.rule)
    val declaredeventnames = _declared_event_names(sm.rule)
    val transitions = _normalized_transition_definitions(machine, sm.rule).zipWithIndex.map {
      case (transition, declarationorder) =>
        _normalized_transition(
          sm = sm,
          machine = machine,
          transition = transition,
          declarationorder = declarationorder,
          statesbyname = statesbyname,
          compositesbyname = compositesbyname,
          fallbacks = fallbacks,
          legacystatemap = legacystatemap,
          historyfieldname = historyfieldname,
          declaredeventnames = declaredeventnames,
          topology = _normalized_topology(composites)
        )
    }

    MComponent.NormalizedStateMachine(
      identity = machine,
      initialState = Some(_normalized_initial_state(sm.rule, statesbyname, _machine_source_location(sm.name))),
      states = states,
      transitions = transitions,
      historyFieldName = historyfieldname,
      topology = _normalized_topology(composites, transitions)
    )
  }

  private def _validate_normalizable_topology(
    machinename: String,
    rule: StateMachineRule,
    declarationpath: Vector[String] = Vector("root")
  ): Unit =
    rule.statemachines.toVector.zipWithIndex.foreach {
      case (composite, compositeindex) =>
        val name = _require_composite_state_name(composite)
        val path = declarationpath ++ Vector("composite", name, compositeindex.toString)
        if (composite.statemachines.nonEmpty)
          _normalization_failure(
            code = "unsupported-nested-composite",
            message = s"StateMachine '$machinename' uses nested composite state not admitted by the one-level contract.",
            sourcelocation = _transition_source_location(MComponent.StateMachineIdentity(machinename), path)
          )
        _validate_normalizable_topology(machinename, composite, path)
    }

  private def _normalized_topology(
    composites: Vector[(MComponent.StateMachineStateIdentity, Vector[MComponent.StateMachineStateIdentity])],
    transitions: Vector[MComponent.NormalizedStateMachineTransition] = Vector.empty
  ): MComponent.StateMachineTopology =
    MComponent.StateMachineTopology(
      composites = composites.map { case (identity, leaves) =>
        MComponent.StateMachineCompositeTopology(identity, leaves)
      },
      terminalTransitions = transitions.collect {
        case transition if transition.target == MComponent.StateMachineTransitionTarget.Final => transition.identity
      }
    )

  private def _normalized_state_identities(
    machine: MComponent.StateMachineIdentity,
    rule: StateMachineRule,
    parent: Vector[String] = Vector.empty
  ): Vector[MComponent.StateMachineStateIdentity] = {
    val leaves = rule.states.toVector.filterNot(_.name.equalsIgnoreCase(PROP_STATE_INIT)).map { state =>
      MComponent.StateMachineStateIdentity(machine, parent :+ state.name)
    }
    val composites = rule.statemachines.toVector.flatMap { composite =>
      val name = _require_composite_state_name(composite)
      val path = parent :+ name
      MComponent.StateMachineStateIdentity(machine, path) +:
        _normalized_state_identities(machine, composite, path)
    }
    leaves ++ composites
  }

  private def _normalized_composite_identities(
    machine: MComponent.StateMachineIdentity,
    rule: StateMachineRule,
    parent: Vector[String] = Vector.empty
  ): Vector[(MComponent.StateMachineStateIdentity, Vector[MComponent.StateMachineStateIdentity])] =
    rule.statemachines.toVector.flatMap { composite =>
      val name = _require_composite_state_name(composite)
      val path = parent :+ name
      val identity = MComponent.StateMachineStateIdentity(machine, path)
      val leaves = composite.states.toVector.map { state =>
        MComponent.StateMachineStateIdentity(machine, path :+ state.name)
      }
      (identity -> leaves) +: _normalized_composite_identities(machine, composite, path)
    }

  private def _unique_normalized_states(
    machinename: String,
    states: Vector[MComponent.StateMachineStateIdentity]
  ): Map[String, MComponent.StateMachineStateIdentity] =
    states.groupBy(_.path.last).map {
      case (name, Vector(state)) => name -> state
      case (name, _) =>
        RAISE.syntaxErrorFault(
          s"StateMachine '$machinename' has an ambiguous StateMachine state name '$name'."
        )
    }.toMap

  private def _normalized_initial_state(
    rule: StateMachineRule,
    statesbyname: Map[String, MComponent.StateMachineStateIdentity],
    sourcelocation: MComponent.StateMachineSourceLocation
  ): MComponent.StateMachineStateIdentity = {
    val explicit = rule.states.find(_.name.equalsIgnoreCase(PROP_STATE_INIT)).flatMap { init =>
      (init.transitions.call.map(_.to) ++ init.transitions.global.map(_.to)).collectFirst {
        case NameTransitionTo(name) => name
      }
    }
    explicit match {
      case Some(name) => statesbyname.get(name).getOrElse {
        _normalization_failure(
          code = "invalid-initial-state",
          message = "The StateMachine declaration references an invalid initial state.",
          sourcelocation = sourcelocation
        )
      }
      case None =>
        rule.states.toVector.find(x => !x.name.equalsIgnoreCase(PROP_STATE_INIT)).flatMap { state =>
          statesbyname.get(state.name)
        }.getOrElse {
          _normalization_failure(
            code = "missing-initial-state",
            message = "The StateMachine declaration requires an explicit normalized initial state.",
            sourcelocation = sourcelocation
          )
        }
    }
  }

  private def _normalized_transition_definitions(
    machine: MComponent.StateMachineIdentity,
    rule: StateMachineRule,
    parent: Vector[String] = Vector.empty,
    compositesource: Option[MComponent.StateMachineStateIdentity] = None,
    declarationprefix: Vector[String] = Vector("root")
  ): Vector[NormalizationTransitionDefinition] = {
    val statetransitions = rule.states.toVector.filterNot(_.name.equalsIgnoreCase(PROP_STATE_INIT)).zipWithIndex.flatMap {
      case (state, stateindex) =>
        val source = MComponent.StateMachineStateIdentity(machine, parent :+ state.name)
        val stateprefix = declarationprefix ++ Vector("state", state.name, stateindex.toString)
        state.transitions.call.zipWithIndex.map {
          case (transition, transitionindex) =>
            NormalizationTransitionDefinition(
              Some(source),
              Some(state),
              transition,
              iscalltransition = true,
              stateprefix ++ Vector("call", transitionindex.toString)
            )
        }.toVector ++ state.transitions.global.zipWithIndex.map {
          case (transition, transitionindex) =>
            NormalizationTransitionDefinition(
              Some(source),
              Some(state),
              transition,
              iscalltransition = false,
              stateprefix ++ Vector("global", transitionindex.toString)
            )
        }.toVector
    }
    val ruletransitions =
      rule.transitions.call.zipWithIndex.map {
        case (transition, transitionindex) =>
          NormalizationTransitionDefinition(
            compositesource,
            None,
            transition,
            iscalltransition = true,
            declarationprefix ++ Vector("rule", "call", transitionindex.toString)
          )
      }.toVector ++ rule.transitions.global.zipWithIndex.map {
        case (transition, transitionindex) =>
          NormalizationTransitionDefinition(
            compositesource,
            None,
            transition,
            iscalltransition = false,
            declarationprefix ++ Vector("rule", "global", transitionindex.toString)
          )
      }.toVector
    val nested = rule.statemachines.toVector.zipWithIndex.flatMap {
      case (composite, compositeindex) =>
        val name = _require_composite_state_name(composite)
        val source = MComponent.StateMachineStateIdentity(machine, parent :+ name)
        _normalized_transition_definitions(
          machine = machine,
          rule = composite,
          parent = parent :+ name,
          compositesource = Some(source),
          declarationprefix = declarationprefix ++ Vector("composite", name, compositeindex.toString)
        )
    }
    statetransitions ++ ruletransitions ++ nested
  }

  private def _normalized_transition(
    sm: StateMachineClass,
    machine: MComponent.StateMachineIdentity,
    transition: NormalizationTransitionDefinition,
    declarationorder: Int,
    statesbyname: Map[String, MComponent.StateMachineStateIdentity],
    compositesbyname: Map[String, MComponent.StateMachineStateIdentity],
    fallbacks: Map[MComponent.StateMachineStateIdentity, Option[MComponent.StateMachineStateIdentity]],
    legacystatemap: Map[String, StateClass],
    historyfieldname: Option[String],
    declaredeventnames: Set[String],
    topology: MComponent.StateMachineTopology
  ): MComponent.NormalizedStateMachineTransition = {
    val identity = MComponent.StateMachineTransitionIdentity(machine, declarationorder)
    val sourcelocation = _transition_source_location(machine, transition.declarationpath)
    val expliciteventnames = _normalized_event_names(transition.transition.guard)
    if (expliciteventnames.size > 1)
      _normalization_failure(
        code = "ambiguous-trigger",
        message = "A normalized StateMachine transition must declare exactly one event trigger.",
        sourcelocation = sourcelocation,
        transition = Some(identity)
      )
    val eventname = expliciteventnames.headOption.orElse(transition.transition.getEventName.map(_.trim).filter(_.nonEmpty)).getOrElse {
      _normalization_failure(
        code = "missing-trigger",
        message = s"StateMachine '${sm.name}' transition requires an event trigger.",
        sourcelocation = sourcelocation,
        transition = Some(identity)
      )
    }
    if (declaredeventnames.nonEmpty && !declaredeventnames.contains(eventname))
      _normalization_failure(
        code = "undeclared-event",
        message = s"StateMachine '${sm.name}' transition references undeclared event $eventname.",
        sourcelocation = sourcelocation,
        transition = Some(identity)
      )
    val target = transition.transition.to match {
      case NameTransitionTo(name) =>
        statesbyname.get(name).map(MComponent.StateMachineTransitionTarget.State).getOrElse {
          _normalization_failure(
            code = "invalid-target-state",
            message = "The StateMachine declaration references an invalid target state.",
            sourcelocation = sourcelocation,
            transition = Some(identity)
          )
        }
      case NamedHistoryTransitionTo(name) =>
        if (historyfieldname.isEmpty)
          _normalization_failure(
            code = "missing-history-field",
            message = s"StateMachine '${sm.name}' named history transition requires HISTORY-FIELD.",
            sourcelocation = sourcelocation,
            transition = Some(identity)
          )
        compositesbyname.get(name).map { composite =>
          MComponent.StateMachineTransitionTarget.ShallowHistory(composite, fallbacks.getOrElse(composite, None))
        }.getOrElse {
          _normalization_failure(
            code = "invalid-history-target",
            message = "The StateMachine declaration references an invalid named shallow-history target.",
            sourcelocation = sourcelocation,
            transition = Some(identity)
          )
        }
      case FinalTransitionTo => MComponent.StateMachineTransitionTarget.Final
      case HistoryTransitionTo() =>
        _normalization_failure(
          code = "invalid-history-target",
          message = "The StateMachine declaration references an invalid named shallow-history target.",
          sourcelocation = sourcelocation,
          transition = Some(identity)
        )
      case NoneTransitionTo =>
        _normalization_failure(
          code = "invalid-target-state",
          message = "The StateMachine declaration references an invalid target state.",
          sourcelocation = sourcelocation,
          transition = Some(identity)
        )
    }
    val plan = _normalized_rule_plan(sm.name, transition, legacystatemap)
    val guard = _normalized_guard(sm.name, identity, transition.transition.guard, sourcelocation)
    MComponent.StateMachineGuardProgram.validate(guard) match {
      case Left(error) =>
        _normalization_failure(
          code = error.code,
          message = error.message,
          sourcelocation = sourcelocation,
          transition = Some(identity)
        )
      case Right(_) => ()
    }

    MComponent.NormalizedStateMachineTransition(
      identity = identity,
      source = transition.source,
      target = target,
      trigger = MComponent.StateMachineTriggerIdentity(machine, eventname),
      priority = MComponent.StateMachineTransitionPriority.default,
      guard = guard,
      actions = _normalized_actions(identity, plan, sourcelocation),
      sourceLocation = sourcelocation,
      historyWrites = _normalized_history_writes(
        source = transition.source,
        target = target,
        historyfieldname = historyfieldname,
        topology = topology
      )
    )
  }

  private def _normalized_history_writes(
    source: Option[MComponent.StateMachineStateIdentity],
    target: MComponent.StateMachineTransitionTarget,
    historyfieldname: Option[String],
    topology: MComponent.StateMachineTopology
  ): Vector[MComponent.StateMachineHistoryWrite] =
    if (historyfieldname.isEmpty || target.isInstanceOf[MComponent.StateMachineTransitionTarget.ShallowHistory])
      Vector.empty
    else {
      val targetstate = target match {
        case MComponent.StateMachineTransitionTarget.State(identity) => Some(identity)
        case _ => None
      }
      topology.composites.flatMap { composite =>
        targetstate.filter(composite.directLeaves.contains).orElse(source.filter(composite.directLeaves.contains)).map { leaf =>
          MComponent.StateMachineHistoryWrite(composite.identity, leaf)
        }
      }
    }

  private def _normalized_rule_plan(
    machinename: String,
    transition: NormalizationTransitionDefinition,
    statemap: Map[String, StateClass]
  ): MComponent.RulePlan = {
    val exit = transition.sourcestate.toVector.flatMap(x => _activity_scripts(x.exitActivity))
    val trans = _normalize_transition_action(transition.transition.effect, machinename)
    val entry = _entry_scripts(transition.transition.to, statemap)
    MComponent.RulePlan(
      exit = exit.map(MComponent.RuleAction.apply),
      transition = trans.map(MComponent.RuleAction.apply),
      entry = entry.map(MComponent.RuleAction.apply)
    )
  }

  private def _normalized_guard(
    machinename: String,
    identity: MComponent.StateMachineTransitionIdentity,
    guard: SmGuard,
    sourcelocation: MComponent.StateMachineSourceLocation
  ): MComponent.StateMachineGuardProgram = {
    import MComponent.StateMachineGuardProgram

    def _named_binding_name_(value: SmGuard): Option[String] =
      value match {
        case CmlExpressionGuard(expression) =>
          val name = _normalize_guard_text(expression)
          if (_is_guard_ref_name(name)) Some(name) else None
        case AndGuard(terms) =>
          terms.filterNot(_.isInstanceOf[EventNameGuard]) match {
            case Vector(single) => _named_binding_name_(single)
            case _ => None
          }
        case _ => None
      }

    _named_binding_name_(guard) match {
      case Some(name) => StateMachineGuardProgram.Named(MComponent.StateMachineGuardIdentity(identity, name))
      case None =>
        StateMachineGuardProgram.Predicate(
          MComponent.PredicateProgram(expression = _normalized_predicate(machinename, identity, guard, sourcelocation))
        )
    }
  }

  private def _normalized_predicate(
    machinename: String,
    identity: MComponent.StateMachineTransitionIdentity,
    guard: SmGuard,
    sourcelocation: MComponent.StateMachineSourceLocation
  ): MComponent.StateMachinePredicate = {
    import MComponent.StateMachinePredicate
    import MComponent.StateMachinePredicateField
    import MComponent.StateMachinePredicateValue

    def _combine_(
      terms: Vector[SmGuard],
      constructor: Vector[MComponent.StateMachinePredicate] => MComponent.StateMachinePredicate
    ): MComponent.StateMachinePredicate = {
      val normalized = terms.filterNot(_.isInstanceOf[EventNameGuard]).map {
        _normalized_predicate(machinename, identity, _, sourcelocation)
      }
      normalized match {
        case Vector() => StateMachinePredicate.Always
        case Vector(single) => single
        case xs => constructor(xs)
      }
    }

    guard match {
      case AllGuard => StateMachinePredicate.Always
      case EventNameGuard(_) => StateMachinePredicate.Always
      case ResourceIdGuard(resourceid) =>
        StateMachinePredicate.Equals(
          StateMachinePredicateField.TargetIdentifier,
          StateMachinePredicateValue.Text(resourceid)
        )
      case ToStateGuard(name, value) =>
        val names = Vector(Some(name), value.map(_.toString)).flatten.distinct
        names match {
          case Vector(single) =>
            StateMachinePredicate.Equals(
              StateMachinePredicateField.EventName,
              StateMachinePredicateValue.Text(single)
            )
          case xs =>
            StateMachinePredicate.Any(xs.map { name =>
              StateMachinePredicate.Equals(
                StateMachinePredicateField.EventName,
                StateMachinePredicateValue.Text(name)
              )
            })
        }
      case CmlExpressionGuard(expression) =>
        val name = _normalize_guard_text(expression)
        if (_is_guard_ref_name(name))
          _normalization_failure(
            code = "invalid-named-guard-composition",
            message = "A named guard must be the complete guard declaration.",
            sourcelocation = sourcelocation,
            transition = Some(identity)
          )
        else
          _normalization_failure(
            code = "legacy-raw-expression-not-admitted",
            message = "A legacy raw expression is not admitted to the typed StateMachine predicate contract.",
            sourcelocation = sourcelocation,
            transition = Some(identity)
          )
      case AndGuard(terms) => _combine_(terms, StateMachinePredicate.All)
      case OrGuard(terms) if terms.exists(_.isInstanceOf[EventNameGuard]) =>
        _normalization_failure(
          code = "ambiguous-trigger",
          message = "An event trigger cannot be combined with a predicate through disjunction.",
          sourcelocation = sourcelocation,
          transition = Some(identity)
        )
      case OrGuard(terms) => _combine_(terms, StateMachinePredicate.Any)
    }
  }

  private def _normalized_event_names(
    guard: SmGuard
  ): Vector[String] = {
    def _go_(value: SmGuard): Vector[String] =
      value match {
        case EventNameGuard(name) => Vector(name.trim).filter(_.nonEmpty)
        case AndGuard(terms) => terms.flatMap(_go_)
        case OrGuard(terms) => terms.flatMap(_go_)
        case _ => Vector.empty
      }

    _go_(guard).distinct
  }

  private def _normalized_actions(
    transition: MComponent.StateMachineTransitionIdentity,
    plan: MComponent.RulePlan,
    sourcelocation: MComponent.StateMachineSourceLocation
  ): MComponent.NormalizedStateMachineActionPlan = {
    def _actions_(
      phase: MComponent.StateMachineActionPhase,
      values: Vector[MComponent.RuleAction]
    ): Vector[MComponent.NormalizedStateMachineAction] =
      values.zipWithIndex.map {
        case (action, order) =>
          val reference = action.script.trim
          if (!_is_guard_ref_name(reference))
            _normalization_failure(
              code = "legacy-raw-action-not-admitted",
              message = "A legacy raw action is not admitted to the named local-action contract.",
              sourcelocation = sourcelocation,
              transition = Some(transition)
            )
          MComponent.NormalizedStateMachineAction(
            MComponent.StateMachineActionIdentity(transition, phase, order),
            reference
          )
      }

    MComponent.NormalizedStateMachineActionPlan(
      exit = _actions_(MComponent.StateMachineActionPhase.Exit, plan.exit),
      transition = _actions_(MComponent.StateMachineActionPhase.Transition, plan.transition.toVector),
      entry = _actions_(MComponent.StateMachineActionPhase.Entry, plan.entry)
    )
  }

  private def _history_field_name(
    entityclass: EntityClass,
    statemachine: StateMachineClass
  ): Option[String] =
    statemachine.rule.historyFieldName.map { requested =>
      entityclass.schemaClass.slots.collectFirst {
        case attribute: SchemaModel.Attribute if attribute.name == requested => attribute.name
      }.getOrElse {
        RAISE.syntaxErrorFault(
          s"StateMachine '${statemachine.name}' HISTORY-FIELD '$requested' is not an attribute of entity '${entityclass.name}'."
        )
      }
    }

  private def _declared_event_names(
    rule: StateMachineRule
  ): Set[String] =
    rule.events.toVector.map(_.name).toSet ++ rule.statemachines.toVector.flatMap(_declared_event_names)

  private def _state_map(
    rule: StateMachineRule
  ): Map[String, StateClass] =
    _all_states(rule).foldLeft(Map.empty[String, StateClass]) { (z, x) =>
      if (z.contains(x.name)) z else z + (x.name -> x)
    }

  private def _all_states(
    rule: StateMachineRule
  ): Vector[StateClass] =
    rule.states.toVector ++ rule.statemachines.toVector.flatMap(_all_states)

  private def _entry_scripts(
    to: TransitionTo,
    statemap: Map[String, StateClass]
  ): Vector[String] =
    to match {
      case NameTransitionTo(name) =>
        statemap.get(name).toVector.flatMap(x => _activity_scripts(x.entryActivity))
      case _ =>
        Vector.empty
    }

  private def _activity_scripts(
    activity: Activity
  ): Vector[String] =
    activity match {
      case Activity.Empty => Vector.empty
      case Activity.Opaque(script) => _script_vector(script)
      case m => _script_vector(m.toString)
    }

  private def _script_vector(
    value: String
  ): Vector[String] =
    Option(value).map(_.trim).filter(_.nonEmpty).toVector

  private def _normalize_guard_text(
    value: String
  ): String =
    _strip_wrapping_paren(value.trim)

  private def _strip_wrapping_paren(
    value: String
  ): String =
    if (_is_wrapped_by_paren(value)) value.substring(1, value.length - 1).trim else value

  private def _is_wrapped_by_paren(
    value: String
  ): Boolean =
    value.length >= 2 && value.head == '(' && value.last == ')' && _is_balanced_paren(value.substring(1, value.length - 1))

  private def _is_balanced_paren(
    value: String
  ): Boolean = {
    @annotation.tailrec
    def _go_(index: Int, depth: Int): Boolean =
      if (index >= value.length)
        depth == 0
      else
        value.charAt(index) match {
          case '(' => _go_(index + 1, depth + 1)
          case ')' =>
            if (depth <= 0) false else _go_(index + 1, depth - 1)
          case _ => _go_(index + 1, depth)
        }
    _go_(0, 0)
  }

  private def _is_guard_ref_name(
    value: String
  ): Boolean =
    value.matches("^[A-Za-z_][A-Za-z0-9_\\.]*$")
}
