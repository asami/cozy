package cozy.modeler

import org.simplemodeling.model._
import org.simplemodeling.model.domain._
import org.simplemodeling.SimpleModeler.generator.scala.Generator.{State => GState, _}
import org.smartdox.{Description, Dox}
import org.goldenport.RAISE
import org.goldenport.collection.VectorMap
import org.goldenport.values.Designation
import org.goldenport.values.PathName
import org.goldenport.record.v2.Column
import org.goldenport.sm._
import org.goldenport.sm.{ExecutionContext => StateMachineContext}
import org.goldenport.sm.StateMachineClass
import org.goldenport.sexpr._
import org.goldenport.util.StringUtils
import org.goldenport.kaleidox.{Model => KaleidoxModel}
import org.goldenport.kaleidox.lisp.Context
import org.goldenport.kaleidox.model.{SchemaModel, EntityModel, DataTypeModel}
import org.goldenport.kaleidox.model.{PowertypeModel, StateMachineModel, EventModel}
import org.goldenport.kaleidox.model.ComponentSubsystemModel
import org.goldenport.kaleidox.model.OperationModel
import org.goldenport.kaleidox.model.ServiceModel
import org.goldenport.kaleidox.model.ValueModel
import org.goldenport.kaleidox.model.actor.ActorModel
import org.goldenport.kaleidox.model.CmlExpressionGuard
import org.goldenport.kaleidox.CmlSectionFormat
import org.goldenport.kaleidox.model.SchemaModel.SchemaClass
import org.goldenport.kaleidox.model.EntityModel.EntityClass
import org.goldenport.kaleidox.model.DataTypeModel.DataTypeClass
import org.goldenport.kaleidox.model.PowertypeModel.PowertypeClass
import org.goldenport.kaleidox.model.ValueModel.ValueClass
import org.goldenport.parser.LogicalSection
import scala.collection.mutable


/*
 * @since Aug. 14, 2026
 * @version Aug. 14, 2026
 * @author ASAMI, Tomoharu
 */
private[modeler] final class ModelStateMachineProjector(val context: ModelBuildContext) {
  import context._
  import Modeler._
  private[modeler] def projectStateMachine(p: StateMachineClass): MStateMachine = _statemachine(p)
  private[modeler] def transitionRules(entities: Vector[MEntity]): Vector[MComponent.StateMachineTransitionRule] = _state_machine_transition_rules(entities)
  private[modeler] def definitions(entities: Vector[MEntity]): Vector[MComponent.StateMachineDefinition] = _state_machine_definitions(entities)

    private def _distinct_stable(
      p: Vector[String]
    ): Vector[String] =
      p.foldLeft(Vector.empty[String]) { (z, x) =>
        val s = Option(x).map(_.trim).getOrElse("")
        if (s.isEmpty || z.contains(s)) z else z :+ s
      }

    private def _statemachine(p: StateMachineClass): MStateMachine = {
      _validate_state_machine(p)
      val sm = new MDomainStateMachine(
        Description.name(p.name),
        MPackageRef("domain.statemachine"),
        Nil
      )
      val statemap = _build_state_map(sm, p.rule)
      _build_state_machine_transitions(sm, p, statemap)
      sm.setStates(statemap.states)
    }

    private def _build_state_map(
      sm: MDomainStateMachine,
      rule: StateMachineRule
    ): StateHanger = {
      def _state_(p: StateClass): MState =
        MState.create(sm, p.name, Right(p.value))

      def _statemachine_state_(p: StateMachineRule): MState = {
        val s = MState.create(sm, p.name.getOrElse("statemachine"))
        s.subStateMap = _sub_states_map_(p)
        s
      }

      def _sub_states_map_(p: StateMachineRule): VectorMap[String, MState] = {
        val a1 = p.states.map(_state_)
        val a2 = p.statemachines.map(_statemachine_state_)
        val a = (a1 ++ a2).map(x => x.name -> x)
        VectorMap(a)
      }

      val (a0, initstatename) = _normalize_init(rule.states)
      val a1 = a0.map(_state_)
      val a2 = rule.statemachines.map(_statemachine_state_)
      val a4 = _normalize_init(initstatename, a1, a2)
      StateHanger.create(a4.map(x => x.name -> x))
    }

    private def _build_state_machine_transitions(
      sm: MDomainStateMachine,
      smc: StateMachineClass,
      statemap: StateHanger
    ): Unit = {
      def _event_(t: Transition): Option[MObject] =
        _event_name_from_guard(t.guard).orElse(t.getEventName).map(MEvent.apply)

      def _guard_(t: Transition): Option[MGuard] =
        _guard_for_rule(t.guard).map {
          case MComponent.RuleGuard.Ref(name) =>
            MGuard(sm, Description.name(name), s"ref:$name")
          case MComponent.RuleGuard.Expression(expr) =>
            MGuard(sm, Description.name("guard"), expr)
        }

      def _build_transition_(
        sourcestatename: Option[String],
        ownerrule: StateMachineRule,
        t: Transition
      ): Option[MTransition] = {
        val g = _guard_(t)
        val event = _event_(t)
        val action = None // TODO MAction mapping
        val from = sourcestatename.flatMap(statemap.get).orElse(ownerrule.name.flatMap(statemap.get))

        def _name_transition_(name: String): MTransition =
          (from, statemap.get(name)) match {
            case (Some(pre), Some(post)) => MTransition(sm, event, g, pre, post, action)
            case (Some(_), None) =>
              RAISE.syntaxErrorFault(s"StateMachine '${smc.name}' transition target $name is not defined.")
            case (None, Some(post)) =>
              MTransition(sm, event, g, MState.initState(sm), post, action)
            case (None, None) =>
              RAISE.syntaxErrorFault(s"StateMachine '${smc.name}' transition target $name is not defined.")
          }

        def _history_transition_(p: NamedHistoryTransitionTo): MTransition = {
          val source = sourcestatename.flatMap(statemap.get).getOrElse(MState.initState(sm))
          val history = statemap.get(p.compositeName).filter(_.isComposite).map { composite =>
            composite.historyState.getOrElse(composite.createHistoryState)
          }.getOrElse {
            RAISE.syntaxErrorFault(s"StateMachine '${smc.name}' history target must name a declared composite state.")
          }
          MTransition(sm, event, g, source, history, action)
        }

        t.to match {
          case NoneTransitionTo => None
          case FinalTransitionTo => None
          case p: NamedHistoryTransitionTo => Some(_history_transition_(p))
          case HistoryTransitionTo() =>
            RAISE.syntaxErrorFault(s"StateMachine '${smc.name}' history target must name a declared composite state.")
          case NameTransitionTo(name) =>
            if (name.equalsIgnoreCase(PROP_STATE_INIT))
              None
            else
              Some(_name_transition_(name))
        }
      }

      def _build_rule_(rule: StateMachineRule): Unit = {
        rule.states.foreach { s =>
          val ts =
            s.transitions.call.flatMap(_build_transition_(Some(s.name), rule, _)) ++
              s.transitions.global.flatMap(_build_transition_(Some(s.name), rule, _))
          statemap.get(s.name).foreach(_.transitions = ts.toList)
        }
        rule.statemachines.foreach(_build_rule_)
        val ts =
          rule.transitions.call.flatMap(_build_transition_(None, rule, _)) ++
            rule.transitions.global.flatMap(_build_transition_(None, rule, _))
        rule.name.flatMap(statemap.get).foreach(_.transitions = ts.toList)
      }

      _build_rule_(smc.rule)
    }

    private def _normalize_init(ps: Seq[StateClass]): (Vector[StateClass], Option[String]) = {
      case class Z(
        ss: Vector[StateClass] = Vector.empty,
        initStateName: Option[String] = None
      ) {
        def r = initStateName.
          map(_explicit_init).
          getOrElse((ss, None))

        private def _explicit_init(name: String): (Vector[StateClass], Option[String]) = {
          val (ls, rs) = ss.span(_.name != name)
          rs.headOption.map { x =>
            (x +: (ls ++ rs.tail), None)
          }.getOrElse((ss, initStateName))
        }

        def +(rhs: StateClass) = {
          if (rhs.name.equalsIgnoreCase(PROP_STATE_INIT))
            copy(initStateName = _init_state_name(rhs))
          else
            copy(ss = ss :+ rhs)
        }

        private def _init_state_name(p: StateClass) =
          (p.transitions.call.map(_.to) ++ p.transitions.global.map(_.to)).collect {
            case NameTransitionTo(to) => to
          }.headOption
      }
      ps.foldLeft(Z())(_+_).r
    }

    private def _normalize_init(
      initstatename: Option[String],
      states: Seq[MState],
      sms: Seq[MState]
    ): Seq[MState] = {
      val ss = states ++ sms
      initstatename.map { name =>
        val (ls, rs) = ss.span(_.name != name)
        rs.headOption.map(x => x +: (ls ++ rs.tail)).getOrElse(ss)
      }.getOrElse(ss)
    }
    private case class TransitionDefinition(
      machinename: String,
      sourcestatename: Option[String],
      sourcestate: Option[StateClass],
      transition: Transition,
      iscalltransition: Boolean
    )

    private def _validate_state_machine(sm: StateMachineClass): Unit = {
      val states = _all_states(sm.rule).map(_.name).toSet
      val composites = _history_composites(sm.rule)
      val transitions = _all_transitions(sm.rule)
      val events = _declared_events(sm.rule)
      transitions.foreach(x => _validate_transition(sm.name, x, states, composites, events))
    }

    private def _validate_transition(
      machinename: String,
      transition: TransitionDefinition,
      statenames: Set[String],
      composites: Vector[MComponent.StateMachineHistoryComposite],
      events: Set[String]
    ): Unit = {
      transition.transition.to match {
        case NameTransitionTo(name) =>
          if (!name.equalsIgnoreCase(PROP_STATE_INIT) && !statenames.contains(name))
            RAISE.syntaxErrorFault(s"StateMachine '$machinename' transition target $name is not defined.")
        case NamedHistoryTransitionTo(name) =>
          if (!composites.exists(_.name == name))
            RAISE.syntaxErrorFault(s"StateMachine '$machinename' history composite '$name' is not defined.")
        case HistoryTransitionTo() =>
          RAISE.syntaxErrorFault(s"StateMachine '$machinename' history target must name a composite.")
        case _ =>
      }
      val eventname = _event_name_from_guard(transition.transition.guard).orElse(transition.transition.getEventName).getOrElse {
        RAISE.syntaxErrorFault(s"StateMachine '$machinename' transition requires on.")
      }
      if (events.nonEmpty && !events.contains(eventname))
        RAISE.syntaxErrorFault(s"StateMachine '$machinename' transition references undeclared event $eventname.")
    }

    private def _declared_events(rule: StateMachineRule): Set[String] = {
      val self = rule.events.map(_.name).toSet
      self ++ rule.statemachines.toSet.flatMap(_declared_events)
    }

    private def _declared_event_names(rule: StateMachineRule): Vector[String] = {
      val self = rule.events.map(_.name).toVector
      self ++ rule.statemachines.toVector.flatMap(_declared_event_names)
    }

    private def _state_machine_events(sm: StateMachineClass): Vector[String] = {
      val declared = _declared_event_names(sm.rule)
      val referenced = _all_transitions(sm.rule).flatMap { x =>
        _event_name_from_guard(x.transition.guard).orElse(x.transition.getEventName)
      }
      _distinct_stable(declared ++ referenced)
    }

    private def _state_machine_transition_rules(
      entities: Vector[MEntity]
    ): Vector[MComponent.StateMachineTransitionRule] = {
      val a = entities.flatMap(_entity_state_machine_transition_rules)
      a.zipWithIndex.map {
        case (x, i) => x.copy(declarationOrder = i)
      }
    }

    private def _state_machine_definitions(
      entities: Vector[MEntity]
    ): Vector[MComponent.StateMachineDefinition] =
      entities.flatMap(_entity_state_machine_definitions)

    private def _entity_state_machine_definitions(
      p: MEntity
    ): Vector[MComponent.StateMachineDefinition] =
      entity.get(p.name).toVector.flatMap { klass =>
        klass.stateMachines.toVector.map { sm =>
          MComponent.StateMachineDefinition(
            name = sm.name,
            states = _distinct_stable(_all_states(sm.rule).map(_.name)),
            events = _state_machine_events(sm),
            historyFieldName = sm.rule.historyFieldName,
            historyComposites = _history_composites(sm.rule)
          )
        }
      }

    private def _entity_state_machine_transition_rules(
      p: MEntity
    ): Vector[MComponent.StateMachineTransitionRule] =
      entity.get(p.name).toVector.flatMap { klass =>
        klass.stateMachines.toVector.flatMap(_transition_rules(p, klass, _))
      }

    private def _transition_rules(
      entity: MEntity,
      entityclass: EntityClass,
      sm: StateMachineClass
    ): Vector[MComponent.StateMachineTransitionRule] = {
      _validate_state_machine(sm)
      val collectionname = StringUtils.camelToUnderscore(entity.name)
      val statemap = _state_map(sm.rule)
      val statefieldname = _state_field_name(entityclass, sm)
      val historyfieldname = _history_field_name(entityclass, sm)
      val historycomposites = _history_composites(sm.rule)
      val transitions = _all_transitions(sm.rule)
      val namedhistory = transitions.exists(_.transition.to.isInstanceOf[NamedHistoryTransitionTo])
      if (namedhistory && historyfieldname.isEmpty)
        RAISE.syntaxErrorFault(s"StateMachine '${sm.name}' has named history transitions and requires HISTORY-FIELD.")
      transitions.map { x =>
        val eventname = _event_name(sm.name, x)
        val trigger = _transition_trigger(eventname, x.iscalltransition)
        val guard = _transition_guard(x.transition.guard)
        val plan = _transition_plan(x, statemap)
        val targetstate = _target_state(x.transition.to, statemap, historycomposites)
        val historycomposite = _history_transition_composite(x.transition.to, historycomposites)
        MComponent.StateMachineTransitionRule(
          collectionName = collectionname,
          trigger = trigger,
          eventName = eventname,
          machineName = Some(sm.name),
          stateFieldName = statefieldname,
          fromState = x.sourcestatename,
          fromStateValue = x.sourcestate.map(_.value),
          toState = targetstate.map(_.name),
          toStateValue = targetstate.map(_.value),
          priority = 0,
          declarationOrder = 0,
          guard = guard,
          plan = plan,
          historyCompositeName = historycomposite.map(_.name),
          historyFieldName = historyfieldname,
          historyDirectLeaves = historycomposite.map(_.directLeaves).getOrElse(Vector.empty),
          historyFallbackLeaf = historycomposite.flatMap(_.fallbackLeaf),
          expectedHistoryRecordWrites = _expected_history_record_writes(
            x.sourcestate,
            targetstate,
            x.transition.to,
            historycomposites,
            historyfieldname
          )
        )
      }
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

    private def _history_composites(
      rule: StateMachineRule
    ): Vector[MComponent.StateMachineHistoryComposite] =
      rule.statemachines.toVector.map { composite =>
        val leaves = composite.states.toVector.map(_.name)
        MComponent.StateMachineHistoryComposite(
          name = composite.name.getOrElse(""),
          directLeaves = leaves,
          fallbackLeaf = leaves.headOption
        )
      }

    private def _history_transition_composite(
      to: TransitionTo,
      composites: Vector[MComponent.StateMachineHistoryComposite]
    ): Option[MComponent.StateMachineHistoryComposite] =
      to match {
        case NamedHistoryTransitionTo(name) =>
          composites.find(_.name == name).orElse {
            RAISE.syntaxErrorFault(s"StateMachine history composite '$name' is not defined.")
          }
        case HistoryTransitionTo() =>
          RAISE.syntaxErrorFault("StateMachine history target must name a composite.")
        case _ => None
      }

    private def _expected_history_record_writes(
      source: Option[StateClass],
      target: Option[StateClass],
      to: TransitionTo,
      composites: Vector[MComponent.StateMachineHistoryComposite],
      historyfieldname: Option[String]
    ): Vector[MComponent.StateMachineHistoryRecordWrite] =
      to match {
        case _: NamedHistoryTransitionTo => Vector.empty
        case HistoryTransitionTo() => Vector.empty
        case _ =>
          historyfieldname.toVector.flatMap { _ => composites.flatMap { composite =>
            val sourceleaf = source.filter(s => composite.directLeaves.contains(s.name)).map(_.name)
            val targetleaf = target.filter(s => composite.directLeaves.contains(s.name)).map(_.name)
            targetleaf.orElse(sourceleaf).map { leaf =>
              MComponent.StateMachineHistoryRecordWrite(composite.name, leaf)
            }
          }}
      }

    private def _state_field_name(
      entityclass: EntityClass,
      statemachine: StateMachineClass
    ): Option[String] = {
      val statenames = _all_states(statemachine.rule).map(_.name).toSet
      val candidates = entityclass.schemaClass.slots.collect {
        case attribute: SchemaModel.Attribute =>
          attribute.rawTypeName.flatMap { rawtypename =>
            val typename = rawtypename.split("\\.").last
            powertype.classes.get(typename).filter { powertypeclass =>
              powertypeclass.kinds.map(_.name).toSet == statenames
            }.map(_ => attribute.name)
          }
      }.flatten.toVector.distinct
      candidates match {
        case Vector() => None
        case Vector(name) => Some(name)
        case xs =>
          RAISE.syntaxErrorFault(
            s"StateMachine '${statemachine.name}' has multiple state attributes: ${xs.mkString(", ")}."
          )
      }
    }

    private def _target_state(
      to: TransitionTo,
      statemap: Map[String, StateClass],
      composites: Vector[MComponent.StateMachineHistoryComposite]
    ): Option[StateClass] =
      to match {
        case NameTransitionTo(name) => statemap.get(name)
        case NamedHistoryTransitionTo(name) =>
          composites.find(_.name == name).flatMap(_.fallbackLeaf).flatMap(statemap.get)
        case HistoryTransitionTo() =>
          RAISE.syntaxErrorFault("StateMachine history target must name a composite.")
        case _ => None
      }

    private def _state_map(
      rule: StateMachineRule
    ): Map[String, StateClass] =
      _all_states(rule).foldLeft(Map.empty[String, StateClass]) { (z, x) =>
        if (z.contains(x.name))
          z
        else
          z + (x.name -> x)
      }

    private def _all_states(
      rule: StateMachineRule
    ): Vector[StateClass] =
      rule.states.toVector ++ rule.statemachines.toVector.flatMap(_all_states)

    private def _all_transitions(
      rule: StateMachineRule
    ): Vector[TransitionDefinition] = {
      val machinename = rule.name.getOrElse("")
      val fromstates = rule.states.toVector.flatMap { s =>
        s.transitions.call.map(t => TransitionDefinition(machinename, Some(s.name), Some(s), t, iscalltransition = true)).toVector ++
          s.transitions.global.map(t => TransitionDefinition(machinename, Some(s.name), Some(s), t, iscalltransition = false)).toVector
      }
      val fromrule =
        rule.transitions.call.map(t => TransitionDefinition(machinename, None, None, t, iscalltransition = true)).toVector ++
          rule.transitions.global.map(t => TransitionDefinition(machinename, None, None, t, iscalltransition = false)).toVector
      fromstates ++ fromrule ++ rule.statemachines.toVector.flatMap(_all_transitions)
    }

    private def _event_name(
      machinename: String,
      transition: TransitionDefinition
    ): String =
      _event_name_from_guard(transition.transition.guard).orElse(transition.transition.getEventName).getOrElse {
        RAISE.syntaxErrorFault(s"StateMachine '$machinename' transition requires on.")
      }

    private def _event_name_from_guard(
      guard: SmGuard
    ): Option[String] =
      guard match {
        case EventNameGuard(name) => Some(name)
        case AndGuard(exprs) => exprs.toStream.flatMap(_event_name_from_guard).headOption
        case OrGuard(exprs) => exprs.toStream.flatMap(_event_name_from_guard).headOption
        case _ => None
      }

    private def _transition_trigger(
      eventname: String,
      iscall: Boolean
    ): MComponent.TransitionTrigger = {
      val n = eventname.toLowerCase
      if (n == "save" || n.startsWith("save_") || n == "create")
        MComponent.TransitionTrigger.Save
      else if (n == "update" || n.startsWith("update_"))
        MComponent.TransitionTrigger.Update
      else if (iscall)
        MComponent.TransitionTrigger.Update
      else
        MComponent.TransitionTrigger.Save
    }

    private def _transition_guard(
      guard: SmGuard
    ): Option[MComponent.RuleGuard] =
      _guard_for_rule(guard)

    private def _guard_for_rule(
      guard: SmGuard
    ): Option[MComponent.RuleGuard] =
      guard match {
        case AllGuard => None
        case EventNameGuard(_) => None
        case CmlExpressionGuard(expression) =>
          _guard_text_to_rule_guard(expression)
        case AndGuard(exprs) =>
          _guard_for_composite(exprs, "&&")
        case OrGuard(exprs) =>
          _guard_for_composite(exprs, "||")
        case m =>
          _guard_expression(m).map(MComponent.RuleGuard.Expression)
      }

    private def _guard_for_composite(
      exprs: Vector[SmGuard],
      delimiter: String
    ): Option[MComponent.RuleGuard] = {
      val a = exprs.filterNot(_.isInstanceOf[EventNameGuard]).flatMap(_guard_expression_for_rule)
      if (a.isEmpty)
        None
      else if (a.lengthCompare(1) == 0)
        _guard_text_to_rule_guard(a.head)
      else
        Some(MComponent.RuleGuard.Expression(a.map(x => s"($x)").mkString(s" $delimiter ")))
    }

    private def _guard_text_to_rule_guard(
      p: String
    ): Option[MComponent.RuleGuard] = {
      val s = _normalize_guard_text(p)
      if (s.isEmpty)
        None
      else if (_is_guard_ref_name(s))
        Some(MComponent.RuleGuard.Ref(s))
      else
        Some(MComponent.RuleGuard.Expression(s))
    }

    private def _normalize_guard_text(p: String): String = {
      val s = p.trim
      _strip_wrapping_paren(s)
    }

    private def _strip_wrapping_paren(p: String): String =
      if (_is_wrapped_by_paren(p))
        p.substring(1, p.length - 1).trim
      else
        p

    private def _is_wrapped_by_paren(p: String): Boolean =
      p.length >= 2 && p.head == '(' && p.last == ')' && _is_balanced_paren(p.substring(1, p.length - 1))

    private def _is_balanced_paren(p: String): Boolean = {
      @annotation.tailrec
      def _go_(i: Int, depth: Int): Boolean =
        if (i >= p.length)
          depth == 0
        else
          p.charAt(i) match {
            case '(' => _go_(i + 1, depth + 1)
            case ')' =>
              if (depth <= 0)
                false
              else
                _go_(i + 1, depth - 1)
            case _ => _go_(i + 1, depth)
          }
      _go_(0, 0)
    }

    private def _is_guard_ref_name(p: String): Boolean = {
      p.matches("^[A-Za-z_][A-Za-z0-9_\\.]*$")
    }

    private def _guard_expression_for_rule(
      guard: SmGuard
    ): Option[String] =
      guard match {
        case AllGuard => None
        case EventNameGuard(_) => None
        case CmlExpressionGuard(expression) => Some(expression)
        case AndGuard(exprs) =>
          _join_guard_expression(exprs.filterNot(_.isInstanceOf[EventNameGuard]), "&&")
        case OrGuard(exprs) =>
          _join_guard_expression(exprs.filterNot(_.isInstanceOf[EventNameGuard]), "||")
        case m =>
          _guard_expression(m)
      }

    private def _guard_expression(
      guard: SmGuard
    ): Option[String] =
      guard match {
        case AllGuard => None
        case EventNameGuard(name) =>
          Some(s"event.name == '${_escape_string(name)}'")
        case CmlExpressionGuard(expression) =>
          Some(expression)
        case ResourceIdGuard(resourceId) =>
          Some(s"""event.targetId.exists(_.value == "${_escape_string(resourceId)}")""")
        case ToStateGuard(name, value) =>
          value match {
            case Some(v) =>
              Some(s"event.name == '${_escape_string(name)}' || event.name == '${v.toString}'")
            case None =>
              Some(s"event.name == '${_escape_string(name)}'")
          }
        case AndGuard(exprs) =>
          _join_guard_expression(exprs, "&&")
        case OrGuard(exprs) =>
          _join_guard_expression(exprs, "||")
      }

    private def _join_guard_expression(
      exprs: Vector[SmGuard],
      delimiter: String
    ): Option[String] = {
      val a = exprs.flatMap(_guard_expression)
      if (a.isEmpty)
        None
      else
        Some(a.map(x => s"($x)").mkString(s" $delimiter "))
    }

    private def _transition_plan(
      transition: TransitionDefinition,
      statemap: Map[String, StateClass]
    ): MComponent.RulePlan = {
      val exit = transition.sourcestate.toVector.flatMap(x => _activity_scripts(x.exitActivity))
      val trans = _activity_script(transition.transition.effect)
      val entry = _entry_scripts(transition.transition.to, statemap)
      MComponent.RulePlan(
        exit = exit.map(MComponent.RuleAction.apply),
        transition = trans.map(MComponent.RuleAction.apply),
        entry = entry.map(MComponent.RuleAction.apply)
      )
    }

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

    private def _activity_script(p: Activity): Option[String] =
      _activity_scripts(p).headOption

    private def _activity_scripts(p: Activity): Vector[String] =
      p match {
        case Activity.Empty => Vector.empty
        case Activity.Opaque(script) => _script_vector(script)
        case m => _script_vector(m.toString)
      }

    private def _script_vector(p: String): Vector[String] =
      Option(p).map(_.trim).filter(_.nonEmpty).toVector

    private def _escape_string(p: String): String =
      Option(p).getOrElse("").flatMap {
        case '\\' => "\\\\"
        case '"' => "\\\""
        case '\n' => "\\n"
        case '\r' => "\\r"
        case '\t' => "\\t"
        case c => c.toString
      }

}
