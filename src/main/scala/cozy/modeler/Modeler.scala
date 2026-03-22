package cozy.modeler

import org.simplemodeling.model._
import org.simplemodeling.model.domain._
import org.simplemodeling.SimpleModeler.generator.scala.Generator.{State => GState, _}
import org.smartdox.Description
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
import org.goldenport.kaleidox.model.CmlExpressionGuard
import org.goldenport.kaleidox.model.SchemaModel.SchemaClass
import org.goldenport.kaleidox.model.EntityModel.EntityClass
import org.goldenport.kaleidox.model.DataTypeModel.DataTypeClass
import org.goldenport.kaleidox.model.PowertypeModel.PowertypeClass

/*
 * @since   May.  5, 2021
 *  version Jun. 30, 2021
 *  version Jul. 11, 2021
 *  version Aug.  2, 2021
 *  version Sep. 26, 2021
 *  version Oct. 31, 2021
 *  version Nov. 29, 2021
 *  version Dec. 18, 2021
 *  version Jan. 23, 2022
 *  version Aug.  4, 2023
 *  version Sep. 25, 2023
 *  version Oct. 29, 2023
 *  version Nov.  2, 2024
 *  version May. 13, 2025
 *  version Feb. 27, 2026
 * @version Mar. 22, 2026
 * @author  ASAMI, Tomoharu
 */
class Modeler() extends org.goldenport.kaleidox.extension.modeler.Modeler {
  import Modeler._

  def generateStateMachineDiagram(
    c: Context,
    name: String,
    resourceid: Option[String]
  ): SExpr = {
    _make_sm(c, name).
      map(_make_diagram(c, _)).
      getOrElse(SError.notFound("statemachine", name))
  }

  private def _make_sm(c: Context, name: String) =
    c.universe.model.stateMachineModel.getClass(name).orElse {
      c.universe.model.getEntityModel.flatMap(_.get(name)).flatMap { em =>
        em.stateMachines.headOption // TODO
      }
    }.map { x =>
      val sm = MDomainStateMachine.create(name)
      val states = _states(sm, x)
      // val sms = VectorMap.empty[String, MDomainStateMachine]
      sm.setStates(states)
      sm
    }

  private def _states(
    sm: MDomainStateMachine,
    p: StateMachineClass
  ): VectorMap[String, MState] = {
    def initState = MState.initState(sm)
    // def historyState = MState.historyState(sm)

    def _state_(p: StateClass): MState = {
      val s = MState.create(sm, p.name)
      s
    }

    def _statemachine_state_(p: StateMachineRule): MState = {
      val s = MState.create(sm, p.name.get) // TODO
      s.subStateMap = _sub_states_map_(p)
      s
    }

    def _sub_states_map_(p: StateMachineRule): VectorMap[String, MState] = {
      val a1 = p.states.map(_state_)
      val a2 = p.statemachines.map(_statemachine_state_)
      val a = (a1 ++ a2).map(x => x.name -> x)
      VectorMap(a)
    }

    def _build_transitions(statemap: StateHanger) = {
      def _build_state(s: StateClass): Unit = {
        def _transition_(t: Transition): Option[MTransition] = {
          val g = _guard_(t.guard)
          val event = t.getEventName.map(x => MEvent(x)) // TODO share
          val action = None // MAction(sm, "???")

          def _name_transition_(p: NameTransitionTo) =
            (statemap.get(s.name), statemap.get(p.name)) match {
              case (Some(from), Some(to)) => MTransition(sm, event, g, from, to, action)
              case (Some(from), None) => RAISE.noReachDefect
              case (None, Some(to)) => MTransition(sm, event, g, initState, to, action)
              case (None, None) => RAISE.noReachDefect
            }

          def _history_transition_(p: HistoryTransitionTo) = {
            val historystate = statemap.historyStates(s.name).head // TODO
            MTransition(sm, event, g, statemap.get(s.name).get, historystate, action)
          }

          t.to match {
            case NoneTransitionTo => None
            case FinalTransitionTo => None
            case m: HistoryTransitionTo => Some(_history_transition_(m))
            case m: NameTransitionTo =>
              if (m.name.equalsIgnoreCase(PROP_STATE_INIT))
                None
              else
                Some(_name_transition_(m))
          }
        }

        val ts = s.transitions.call.flatMap(_transition_) ++ s.transitions.global.flatMap(_transition_)
        statemap.get(s.name).foreach(_.transitions = ts.toList)
      }

      def _build_statemachine_(smr: StateMachineRule): Unit = {
        def _transition_(t: Transition): Option[MTransition] = { // TODO unify
          val g = _guard_(t.guard)
          val event = t.getEventName.map(x => MEvent(x)) // TODO share
          val action = None // MAction(sm, "???")

          def _name_transition_(p: NameTransitionTo) =
            (statemap.get(smr.name getOrElse ""), statemap.get(p.name)) match {
              case (Some(from), Some(to)) => MTransition(sm, event, g, from, to, action)
              case (Some(from), None) => RAISE.noReachDefect
              case (None, Some(to)) => MTransition(sm, event, g, initState, to, action)
              case (None, None) => RAISE.noReachDefect
            }

          def _history_transition_(p: HistoryTransitionTo) = {
            RAISE.notImplementedYetDefect
          }

          t.to match {
            case NoneTransitionTo => None
            case FinalTransitionTo => None
            case m: HistoryTransitionTo => Some(_history_transition_(m))
            case m: NameTransitionTo =>
              if (m.name.equalsIgnoreCase(PROP_STATE_INIT))
                None
              else
                Some(_name_transition_(m))
          }
        }

        smr.states.foreach(_build_state)
        smr.statemachines.foreach(_build_statemachine_)
        val ts = smr.transitions.call.flatMap(_transition_) ++ smr.transitions.global.flatMap(_transition_)
        statemap.get(smr.name getOrElse "").foreach(_.transitions = ts.toList)
      }

      p.statemachines.foreach(_build_statemachine_)
      p.states.foreach(_build_state)
    }

    def _guard_(g: SmGuard): Option[MGuard] =
      _guard_mark(g).map(mark => MGuard(sm, Description.name("guard"), mark))

    def _guard_mark(g: SmGuard): Option[String] =
      g match {
        case AllGuard => None
        case EventNameGuard(_) => None
        case CmlExpressionGuard(expression) =>
          Option(expression).map(_.trim).filter(_.nonEmpty)
        case ResourceIdGuard(resourceid) =>
          Some(s"""event.targetId.exists(_.value == "$resourceid")""")
        case ToStateGuard(name, value) =>
          value match {
            case Some(v) => Some(s"event.name == '${name}' || event.name == '${v.toString}'")
            case None => Some(s"event.name == '${name}'")
          }
        case AndGuard(exprs) =>
          _join_guard_mark(exprs, "&&")
        case OrGuard(exprs) =>
          _join_guard_mark(exprs, "||")
      }

    def _join_guard_mark(exprs: Vector[SmGuard], delimiter: String): Option[String] = {
      val a = exprs.flatMap(_guard_mark)
      if (a.isEmpty)
        None
      else
        Some(a.map(x => s"($x)").mkString(s" $delimiter "))
    }

    val (a0, initstatename) = _normalize_init(p.states)
    val a1 = a0.map(_state_)
    val a2 = p.statemachines.map(_statemachine_state_)
    //        val c = Vector(MState.initState(sm), MState.finalState(sm)).map(x => x.name -> x)
    //        val a3 = Vector()
    //        val a4 = _resolve_init(a1 ++ a2 ++ a3)
    //        val a4 = _normalize(a1 ++ a2 ++ a3)
    val a4 = _normalize_init(initstatename, a1, a2)
    val a = a4.map(x => x.name -> x)
    val states = StateHanger.create(a)
    _build_transitions(states)
    states.states
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

  private def _normalize_init(initstatename: Option[String], states: Seq[MState], sms: Seq[MState]): Seq[MState] = {
    val ss = states ++ sms
    initstatename.map { name =>
      val (ls, rs) = ss.span(_.name != name)
      rs.headOption.map(x => x +: (ls ++ rs.tail)).getOrElse(ss)
    }.getOrElse(ss)
  }

  // private def _normalize(ps: Seq[StateClass]) = {
  //   case class Z(
  //     ss: Vector[StateClass] = Vector.empty,
  //     initStateName: Option[String] = None
  //   ) {
  //     def r = initStateName.
  //       map(_explicit_init).
  //       getOrElse(ss)

  //     private def _explicit_init(name: String) = {
  //       val (ls, rs) = ss.span(_.name != name)
  //       rs.headOption.map(x => x +: (ls ++ rs.tail)).getOrElse(ss)
  //     }

  //     def +(rhs: StateClass) = {
  //       if (rhs.name.equalsIgnoreCase(PROP_STATE_INIT))
  //         copy(initStateName = _init_state_name(rhs))
  //       else
  //         copy(ss = ss :+ rhs)
  //     }

  //     private def _init_state_name(p: StateClass) =
  //       p.transitions.transitions.map(_.to).collect {
  //         case NameTransitionTo(to) => to
  //       }.headOption
  //   }
  //   ps./:(Z())(_+_).r
  // }

  // private def _init_state_name(ps: Seq[StateClass]) =
  //   ps.find(_.name.equalsIgnoreCase(PROP_STATE_INIT)).
  //     flatMap(_.transitions.transitions.map(_.to).collect {
  //       case NameTransitionTo(to) => to
  //     }.headOption)

  private def _normalize(
    ps: Seq[StateClass],
    initstatename: Option[String]
  ) = initstatename.map { name =>
    val (ls, rs) = ps.span(_.name != name)
    rs.headOption.map(x => x +: (ls ++ rs.tail)).getOrElse(ps)
  }.getOrElse(ps)

  // unused
  // private def _resolve_init(ps: Seq[MState]): Seq[MState] = {
  //   case class Z(
  //     ss: Vector[MState] = Vector.empty,
  //     initStateName: Option[String] = None
  //   ) {
  //     def r = initStateName.
  //       map(_explicit_init).
  //       getOrElse(ss)

  //     private def _explicit_init(name: String) = {
  //       val (ls, rs) = ss.span(_.name != name)
  //       rs.headOption.map(x => x +: (ls ++ rs.tail)).getOrElse(ss)
  //     }

  //     def +(rhs: MState) = {
  //       if (rhs.name.equalsIgnoreCase(PROP_STATE_INIT))
  //         copy(initStateName = _init_state_name(rhs))
  //       else
  //         copy(ss = ss :+ rhs)
  //     }

  //     private def _init_state_name(p: MState) =
  //       p.transitions.headOption.map(_.postState.name)
  //   }
  //   ps./:(Z())(_+_).r
  // }

  private def _make_diagram(c: Context, p: MStateMachine): SExpr = {
    val env = c.executionContext.environment
    val model = _make_model(p)
    val g = new StateMachineDiagramGenerator(env, model)
    g.generate(p)
  }

  private def _make_model(sm: MStateMachine): SimpleModel = {
    SimpleModel(Vector(sm))
  }

  def generateDiagram(
    c: Context,
    model: SModel
  ): SExpr = {
    val pkg = "" // TODO
    _make_diagram(c, model, pkg)
  }

  private def _make_diagram(c: Context, smodel: SModel, pkg: String): SExpr = {
    val env = c.executionContext.environment
    val model = _make_model(smodel.model)
    val g = new ClassDiagramGenerator(env, model)
    model.getPackage(pkg) match {
      case Some(s) => g.generate(s)
      case None => SError.notFound("Unkown package", pkg)
    }
  }

  private def _make_model(p: IModel): SimpleModel = p match {
    case m: KaleidoxModel => _make_model(m)
    case m => RAISE.noReachDefect
  }

  private def _make_model(p: KaleidoxModel): SimpleModel = {
    ModelBuilder(p).build()
  }

  def generateScala(
    c: Context,
    model: SModel
  ): SExpr = {
    val pkg = "domain" // TODO
    _make_scala(c, model, pkg)
  }

  private def _make_scala(c: Context, smodel: SModel, pkg: String): SExpr = {
    val env = c.executionContext.environment
    val model = _make_model(smodel.model)
    val g = new ScalaGenerator(env, model)
    model.getPackage(pkg) match {
      case Some(s) => g.generate(s)
      case None => SError.notFound("Unkown package", pkg)
    }
  }
}

object Modeler {
  class StateHanger(val states: VectorMap[String, MState]) {
    def get(name: String): Option[MState] = states.get(name) orElse _get_substate(name)

    private def _get_substate(name: String) =
      states.values.toStream.flatMap(_.getSubStateRecursive(name)).headOption
    
    def historyStates(name: String): Vector[MState] =
      states.values.flatMap(s =>
        if (s.transitions.exists(t => t.postState.name == name))
          Some(s.createHistoryState)
        else
          None
      ).toVector
  }
  object StateHanger {
    def create(ps: Seq[(String, MState)]): StateHanger = new StateHanger(VectorMap(ps))
  }

  case class ModelBuilder(
    schema: SchemaModel,
    entity: EntityModel,
    datatype: DataTypeModel,
    powertype: PowertypeModel,
    stateMachine: StateMachineModel,
    componentSubsystem: ComponentSubsystemModel,
    event: EventModel,
    operation: OperationModel
  ) {
    def build(): SimpleModel = {
      val entities = entity.classes.values.map(_entity)
      val datatypes = datatype.classes.values.map(_datatype)
      val powertypes = powertype.classes.values.map(_powertype)
      val statemachines = stateMachine.classes.values.map(_statemachine)
      val xs = entities ++ datatypes ++ powertypes ++ statemachines
      val a = SimpleModel(xs.toVector)
      val comps = _complement_components(a)
      a.add(comps)
    }

    private def _entity(p: EntityClass): MEntity = {
      val packagename = p.packageName
      val desc = Description.name(p.name)
      val affiliation = MPackageRef(packagename)
      val stereotypes = Nil
      val base = p.parents.headOption.map(_object_ref).orElse {
        p.schemaClass.features.parentsName.headOption.map(_object_ref)
      }
      val traits = Nil // TODO
      val powertypes = _powertypes(affiliation, p.schemaClass)
      val attributes = _attributes(affiliation, p.schemaClass)
      val associations = _associations(affiliation, p.schemaClass)
      val operations = Nil // TODO
      val statemachines = _state_machines(affiliation, p)
      MDomainResource(
        desc,
        affiliation,
        stereotypes,
        base,
        traits,
        powertypes,
        attributes,
        associations,
        operations,
        statemachines
      )
    }

    private def _state_machines(
      pkg: MPackageRef,
      p: EntityClass
    ): List[MStateMachineRef] =
      p.stateMachines.toList.map(_state_machine_ref(pkg, _))

    private def _state_machine_ref(
      pkg: MPackageRef,
      p: StateMachineClass
    ): MStateMachineRef =
      MStateMachineRef(pkg, p.name)

    private def _object_ref(p: EntityClass.ParentRef): MObjectRef =
      p match {
        case m: EntityClass.ParentRef.Name => _object_ref(m.name)
        case EntityClass.ParentRef.EntityKlass(c) => _object_ref(c.packageName, c.name)
      }

    private def _object_ref(p: String): MObjectRef = {
      val isSimpleEntity = _is_simple_entity(p)
      if (isSimpleEntity)
        MObjectRef.create("org.goldenport.model.SimpleEntity")
      else
        MObjectRef.create(p)
    }

    private def _object_ref(packagename: String, name: String): MObjectRef = {
      val isSimpleEntity = _is_simple_entity(name)
      if (isSimpleEntity)
        MObjectRef.create("org.goldenport.model.SimpleEntity")
      else
        MEntityRef.create(packagename, name)
    }

    private def _is_simple_entity(p: String): Boolean =
      p.split("\\.").lastOption.map(_.trim).exists { name =>
        name.equalsIgnoreCase("SimpleEntity") || name.equalsIgnoreCase("simple_entity")
      }

    private def _powertypes(pkg: MPackageRef, p: SchemaClass): List[MPowertypeRef] =
      p.slots.flatMap(_get_powertype(pkg, _)).toList

    private def _get_powertype(pkg: MPackageRef, p: SchemaModel.Slot): Option[MPowertypeRef] =
      p match {
        case m: SchemaModel.Id => None
        case m: SchemaModel.Attribute => None
        case m: SchemaModel.Association => None
        case m: SchemaModel.PowertypeRelationship => Some(_powertype(pkg, m))
        case m: SchemaModel.StateMachineRelationship => None
        case m: SchemaModel.StateMachine => None
      }

    private def _powertype(pkg: MPackageRef, p: SchemaModel.PowertypeRelationship): MPowertypeRef = {
      val name = p.name
      val multiplicity = MMultiplicity(p.multiplicity)
      MPowertypeRef(pkg, name, multiplicity)
    }

    private def _attributes(pkg: MPackageRef, p: SchemaClass): List[MAttribute] =
      p.slots.flatMap(_get_attribute(pkg, _)).toList

    private def _get_attribute(pkg: MPackageRef, p: SchemaModel.Slot): Option[MAttribute] =
      p match {
        case m: SchemaModel.Id => Some(_attribute(m.toColumn))
        case m: SchemaModel.Attribute => Some(_attribute(m.toColumn))
        case m: SchemaModel.Association => None
        case m: SchemaModel.PowertypeRelationship => None
        case m: SchemaModel.StateMachineRelationship => Some(_attribute(m.toColumn)) // TODO
        case m: SchemaModel.StateMachine => Some(_attribute(m.toColumn))
      }

    private def _attribute(p: Column): MAttribute = {
      val designation = Designation(p.name)
      val atype = MDataType(p.datatype)
      val multiplicity = MMultiplicity(p.multiplicity)
      val constraints = Nil // TODO
      val readonly = false
      val description = Description.empty // p.desc
      MAttribute(designation, atype, multiplicity, constraints, Some(p), readonly, description)
    }

    private def _associations(pkg: MPackageRef, p: SchemaClass): List[MAssociation] =
      p.slots.flatMap(_get_association(pkg, _)).toList

    private def _get_association(pkg: MPackageRef, p: SchemaModel.Slot): Option[MAssociation] =
      p match {
        case m: SchemaModel.Id => None
        case m: SchemaModel.Attribute => None
        case m: SchemaModel.Association => Some(_association(pkg, m))
        case m: SchemaModel.PowertypeRelationship => None
        case m: SchemaModel.StateMachineRelationship => None
        case m: SchemaModel.StateMachine => None
      }

    private def _association(pkg: MPackageRef, p: SchemaModel.Association): MAssociation = {
      val designation = Designation(p.name)
      val description = Description.empty // p.desc
      // val pkg = None
      val objectref: MObjectRef = {
        val pathname = p.objectRef.pathname
        if (pathname.tailOption.isEmpty)
          MObjectRef(pkg, pathname.head)
        else
          MObjectRef.create(pathname.v)
      }
      val kind = p match {
        case p: SchemaModel.Composition => MAssociation.Association
        case p: SchemaModel.Aggregation => MAssociation.Aggregation
        case p: SchemaModel.Association => MAssociation.Composition
      }
      val multiplicity = MMultiplicity(p.multiplicity)
      val collaborations = Nil
      MAssociation(designation, description, Some(pkg), objectref, kind, multiplicity, collaborations)
    }

    private def _datatype(p: DataTypeClass): MDataType = p match {
      case m: DataTypeClass.Plain =>
        val pkg = MPackageRef(m.packageName)
        val desc = m.description
        val datatype = m.datatype
        MDataType(desc.designation, datatype, pkg, desc)
      case m: DataTypeClass.Complex => ???
    }

    private def _powertype(p: PowertypeClass): MPowertype = {
      val desc = p.description
      val pkg = MPackageRef(p.packageName)
      val kinds = Nil
      val stereotypes = Nil
      MPowertype(desc, pkg, kinds, stereotypes)
    }

    private def _statemachine(p: StateMachineClass): MStateMachine = {
      _validate_state_machine(p)
      val sm = MDomainStateMachine.create(p.name)
      val statemap = _build_state_map(sm, p.rule)
      _build_state_machine_transitions(sm, p, statemap)
      sm.setStates(statemap.states)
    }

    private def _build_state_map(
      sm: MDomainStateMachine,
      rule: StateMachineRule
    ): StateHanger = {
      def _state_(p: StateClass): MState =
        MState.create(sm, p.name)

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

      def _build_transition(
        sourceStateName: Option[String],
        ownerRule: StateMachineRule,
        t: Transition
      ): Option[MTransition] = {
        val g = _guard_(t)
        val event = _event_(t)
        val action = None // TODO MAction mapping
        val from = sourceStateName.flatMap(statemap.get).orElse(ownerRule.name.flatMap(statemap.get))

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

        def _history_transition_(): MTransition = {
          val source = sourceStateName.flatMap(statemap.get).getOrElse(MState.initState(sm))
          val history = statemap.historyStates(source.name).headOption.getOrElse {
            RAISE.syntaxErrorFault(s"StateMachine '${smc.name}' transition history source ${source.name} is not defined.")
          }
          MTransition(sm, event, g, source, history, action)
        }

        t.to match {
          case NoneTransitionTo => None
          case FinalTransitionTo => None
          case _: HistoryTransitionTo => Some(_history_transition_())
          case NameTransitionTo(name) =>
            if (name.equalsIgnoreCase(PROP_STATE_INIT))
              None
            else
              Some(_name_transition_(name))
        }
      }

      def _build_rule(rule: StateMachineRule): Unit = {
        rule.states.foreach { s =>
          val ts =
            s.transitions.call.flatMap(_build_transition(Some(s.name), rule, _)) ++
              s.transitions.global.flatMap(_build_transition(Some(s.name), rule, _))
          statemap.get(s.name).foreach(_.transitions = ts.toList)
        }
        rule.statemachines.foreach(_build_rule)
        val ts =
          rule.transitions.call.flatMap(_build_transition(None, rule, _)) ++
            rule.transitions.global.flatMap(_build_transition(None, rule, _))
        rule.name.flatMap(statemap.get).foreach(_.transitions = ts.toList)
      }

      _build_rule(smc.rule)
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

    private def _complement_components(p: SimpleModel): Vector[MComponent] =
      _complement_components_package(p, p.root)

    private def _complement_components_package(
      sm: SimpleModel,
      pkg: MPackage
    ): Vector[MComponent] = {
      val a = if (pkg.components.isEmpty) {
        val entities = pkg.entities
        if (entities.isEmpty) {
          Vector.empty
        } else {
          val comp = _make_component(pkg, entities)
          Vector(comp)
        }
      } else {
        Vector.empty
      }
      val subpackages = pkg.subpackages
      val xs = subpackages.flatMap(_complement_components_package(sm, _))
      a ++ xs
    }

    private def _make_component(
      pkg: MPackage,
      entities: Vector[MEntity]
    ): MComponent = {
      // val compclassname = s"${StringUtils.makeTitle(pkg.name)}Component"
      val compname = pkg.name
      val desc = Description.name(compname)
      val entityservice: MService = _make_entity_service(pkg, entities)
      val aggregateservice: MService = _make_aggregate_service(pkg, entities)
      val viewservice: MService = _make_view_service(pkg, entities)
      val componentpackagename = _component_package_name(pkg).getOrElse(pkg.name)
      val core = MObject.Core(
        affiliation = MPackageRef(componentpackagename),
        services = List(aggregateservice, viewservice, entityservice)
      )
      val transitionrules = _state_machine_transition_rules(entities)
      val eventdefs = _event_reception_definitions(entities)
      val eventroutes = _event_routing_definitions()
      val eventsubs = _event_subscription_definitions()
      val aggregates = _aggregate_definitions(entities)
      val views = _view_definitions(entities)
      val operations = _operation_definitions()
      val components = _component_definitions(pkg)
      val subsystems = _subsystem_definitions()
      val ccore = MComponent.Core(
        entities = entities,
        stateMachineTransitionRules = transitionrules,
        eventReceptionDefinitions = eventdefs,
        eventRoutingDefinitions = eventroutes,
        eventSubscriptionDefinitions = eventsubs,
        aggregateDefinitions = aggregates,
        viewDefinitions = views,
        operationDefinitions = operations,
        componentDefinitions = components,
        subsystemDefinitions = subsystems
      )
      MDomainComponent(desc, core, ccore)
    }

    private def _component_package_name(
      pkg: MPackage
    ): Option[String] =
      componentSubsystem.components.find(_.name == pkg.name).flatMap(_.packageName)

    private def _component_definitions(
      pkg: MPackage
    ): Vector[MComponent.ComponentDefinition] = {
      val all = componentSubsystem.components.sortBy(_.name)
      val selected = {
        val matched = all.filter(_.name == pkg.name)
        if (matched.nonEmpty)
          matched
        else
          all
      }
      if (selected.nonEmpty)
        selected.map(_component_definition)
      else
        Vector(_default_component_definition(pkg))
    }

    private def _component_definition(
      p: ComponentSubsystemModel.ComponentDefinition
    ): MComponent.ComponentDefinition = {
      MComponent.ComponentDefinition(
        name = p.name,
        coordinates = p.coordinates.map { c =>
          MComponent.ComponentCoordinate(
            group = c.group,
            artifact = c.artifact,
            version = c.version
          )
        },
        componentlets = _componentlet_names_for_component(p),
        extensionPoints = _extension_point_names_for_component(p),
        extensionBindings = p.extensionBindings
      )
    }

    private def _default_component_definition(
      pkg: MPackage
    ): MComponent.ComponentDefinition = {
      MComponent.ComponentDefinition(
        name = pkg.name,
        coordinates = Vector.empty,
        componentlets = _unbound_componentlet_names(),
        extensionPoints = _unbound_extension_point_names(),
        extensionBindings = Map.empty
      )
    }

    private def _componentlet_names_for_component(
      p: ComponentSubsystemModel.ComponentDefinition
    ): Vector[String] =
      _distinct_stable(
        p.componentlets ++ componentSubsystem.componentlets.collect {
          case x if x.component.contains(p.name) => x.name
        }
      )

    private def _extension_point_names_for_component(
      p: ComponentSubsystemModel.ComponentDefinition
    ): Vector[String] =
      _distinct_stable(
        p.extensionPoints ++ componentSubsystem.extensionPoints.collect {
          case x if x.component.contains(p.name) => x.name
        }
      )

    private def _unbound_componentlet_names(
    ): Vector[String] =
      _distinct_stable(
        componentSubsystem.componentlets.collect {
          case x if x.component.isEmpty => x.name
        }
      )

    private def _unbound_extension_point_names(
    ): Vector[String] =
      _distinct_stable(
        componentSubsystem.extensionPoints.collect {
          case x if x.component.isEmpty => x.name
        }
      )

    private def _subsystem_definitions(
    ): Vector[MComponent.SubsystemDefinition] =
      componentSubsystem.subsystems.sortBy(_.name).map { p =>
        MComponent.SubsystemDefinition(
          name = p.name,
          components = p.components.map { c =>
            MComponent.ComponentCoordinate(
              group = c.group,
              artifact = c.artifact,
              version = c.version
            )
          },
          extensionBindings = p.extensionBindings,
          config = p.config
        )
      }

    private def _distinct_stable(
      p: Vector[String]
    ): Vector[String] =
      p.foldLeft(Vector.empty[String]) { (z, x) =>
        val s = Option(x).map(_.trim).getOrElse("")
        if (s.isEmpty || z.contains(s))
          z
        else
          z :+ s
      }

    private def _event_reception_definitions(
      entities: Vector[MEntity]
    ): Vector[MComponent.EventReceptionDefinition] = {
      val a = entities.flatMap(_entity_event_reception_definitions) ++ _global_event_reception_definitions()
      a.foldLeft(Vector.empty[MComponent.EventReceptionDefinition]) { (z, x) =>
        if (z.contains(x)) z else z :+ x
      }
    }

    private def _global_event_reception_definitions(
    ): Vector[MComponent.EventReceptionDefinition] =
      event.receptionDefinitions.map { e =>
        MComponent.EventReceptionDefinition(
          name = e.name,
          category = e.category,
          kind = e.kind,
          selectors = e.selectors,
          actionName = e.actionName,
          priority = e.priority
        )
      }

    private def _entity_event_reception_definitions(
      p: MEntity
    ): Vector[MComponent.EventReceptionDefinition] =
      entity.get(p.name).toVector.flatMap { klass =>
        klass.schemaClass.events.map { e =>
          MComponent.EventReceptionDefinition(
            name = e.name,
            category = e.category,
            kind = e.kind,
            selectors = e.selectors,
            actionName = e.actionName,
            priority = e.priority
          )
        }
      }

    private def _event_routing_definitions(
    ): Vector[MComponent.EventRoutingDefinition] =
      event.routingDefinitions.map { r =>
        MComponent.EventRoutingDefinition(
          name = r.name,
          when = r.when,
          topic = r.topic,
          service = r.service,
          partition = r.partition
        )
      }

    private def _event_subscription_definitions(
    ): Vector[MComponent.EventSubscriptionDefinition] =
      event.subscriptionDefinitions.flatMap { s =>
        for {
          eventname <- s.eventName
          actionname <- s.actionName
        } yield
          MComponent.EventSubscriptionDefinition(
            name = s.name,
            eventName = eventname,
            route = s.route.getOrElse("Unicast"),
            entityName = s.entityName,
            target = s.target,
            targets = s.targets,
            selector = s.selector,
            actionName = actionname,
            declaredTargetUpperBound = s.declaredTargetUpperBound.getOrElse(1),
            activation = s.activation
          )
      }

    private def _aggregate_definitions(
      entities: Vector[MEntity]
    ): Vector[MComponent.AggregateDefinition] =
      entities.map { entity =>
        val entityname = _package_token(entity.name)
        val name = _aggregate_name(entity).flatMap(_token_opt) match {
          case Some(x) => s"${x}_$entityname"
          case None => entityname
        }
        MComponent.AggregateDefinition(
          name = name,
          entityName = entityname
        )
      }

    private def _view_definitions(
      entities: Vector[MEntity]
    ): Vector[MComponent.ViewDefinition] =
      entities.map { entity =>
        val entityname = _package_token(entity.name)
        val name = _view_name(entity).flatMap(_token_opt) match {
          case Some(x) => s"${x}_$entityname"
          case None => entityname
        }
        MComponent.ViewDefinition(
          name = name,
          entityName = entityname
        )
      }

    private def _operation_definitions(
    ): Vector[MComponent.OperationDefinition] =
      operation.normalizedOperations.map { x =>
        MComponent.OperationDefinition(
          name = x.name,
          kind = x.kind.toString.toUpperCase,
          inputType = x.inputType,
          outputType = x.outputType,
          inputValueKind = x.inputValueKind match {
            case OperationModel.InputValueKind.CommandValue => "COMMAND_VALUE"
            case OperationModel.InputValueKind.QueryValue => "QUERY_VALUE"
          },
          parameters = x.parameters.map { p =>
            MComponent.OperationField(
              name = p.name,
              datatype = p.datatype,
              multiplicity = p.multiplicity
            )
          }
        )
      }

    private val _entity_package = "entity"
    private val _entity_create_package = s"${_entity_package}.create"
    private val _entity_query_package = s"${_entity_package}.query"

    private def _aggregate_package(name: Option[String]): String =
      name.flatMap(_token_opt).fold(s"${_entity_package}.aggregate")(x => s"${_entity_package}.aggregate.$x")

    private def _view_package(name: Option[String]): String =
      name.flatMap(_token_opt).fold(s"${_entity_package}.view")(x => s"${_entity_package}.view.$x")

    // NOTE: Aggregate DSL is not available yet. Keep default package for now.
    // Future: return Some(aggregateName) from model metadata.
    private def _aggregate_name(entity: MEntity): Option[String] = None

    // NOTE: View DSL is not available yet. Keep default package for now.
    // Future: return Some(viewName) from model metadata.
    private def _view_name(entity: MEntity): Option[String] = None

    private def _token_opt(name: String): Option[String] =
      Option(name).map(_.trim).filter(_.nonEmpty).map(_package_token)

    private def _package_token(name: String): String = {
      val b = new StringBuilder
      name.zipWithIndex.foreach { case (c, i) =>
        if (
          c.isUpper && i > 0 &&
          (name.charAt(i - 1).isLower || (i + 1 < name.length && name.charAt(i + 1).isLower))
        ) {
          b.append('_')
        }
        b.append(c.toLower)
      }
      b.toString
    }

    private case class _TransitionDef(
      machineName: String,
      sourceStateName: Option[String],
      sourceState: Option[StateClass],
      transition: Transition,
      isCallTransition: Boolean
    )

    private def _validate_state_machine(sm: StateMachineClass): Unit = {
      val states = _all_states(sm.rule).map(_.name).toSet
      val transitions = _all_transitions(sm.rule)
      val events = _declared_events(sm.rule)
      transitions.foreach(x => _validate_transition(sm.name, x, states, events))
    }

    private def _validate_transition(
      machineName: String,
      transition: _TransitionDef,
      stateNames: Set[String],
      events: Set[String]
    ): Unit = {
      transition.transition.to match {
        case NameTransitionTo(name) =>
          if (!name.equalsIgnoreCase(PROP_STATE_INIT) && !stateNames.contains(name))
            RAISE.syntaxErrorFault(s"StateMachine '$machineName' transition target $name is not defined.")
        case _ =>
      }
      val eventName = _event_name_from_guard(transition.transition.guard).orElse(transition.transition.getEventName).getOrElse {
        RAISE.syntaxErrorFault(s"StateMachine '$machineName' transition requires on.")
      }
      if (events.nonEmpty && !events.contains(eventName))
        RAISE.syntaxErrorFault(s"StateMachine '$machineName' transition references undeclared event $eventName.")
    }

    private def _declared_events(rule: StateMachineRule): Set[String] = {
      val self = rule.events.map(_.name).toSet
      self ++ rule.statemachines.toSet.flatMap(_declared_events)
    }

    private def _state_machine_transition_rules(
      entities: Vector[MEntity]
    ): Vector[MComponent.StateMachineTransitionRule] = {
      val a = entities.flatMap(_entity_state_machine_transition_rules)
      a.zipWithIndex.map {
        case (x, i) => x.copy(declarationOrder = i)
      }
    }

    private def _entity_state_machine_transition_rules(
      p: MEntity
    ): Vector[MComponent.StateMachineTransitionRule] =
      entity.get(p.name).toVector.flatMap { klass =>
        klass.stateMachines.toVector.flatMap(_transition_rules(p, _))
      }

    private def _transition_rules(
      entity: MEntity,
      sm: StateMachineClass
    ): Vector[MComponent.StateMachineTransitionRule] = {
      _validate_state_machine(sm)
      val collectionname = StringUtils.camelToUnderscore(entity.name)
      val statemap = _state_map(sm.rule)
      val transitions = _all_transitions(sm.rule)
      transitions.map { x =>
        val eventname = _event_name(sm.name, x)
        val trigger = _transition_trigger(eventname, x.isCallTransition)
        val guard = _transition_guard(x.transition.guard)
        val plan = _transition_plan(x, statemap)
        MComponent.StateMachineTransitionRule(
          collectionName = collectionname,
          trigger = trigger,
          eventName = eventname,
          priority = 0,
          declarationOrder = 0,
          guard = guard,
          plan = plan
        )
      }
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
    ): Vector[_TransitionDef] = {
      val machinename = rule.name.getOrElse("")
      val fromstates = rule.states.toVector.flatMap { s =>
        s.transitions.call.map(t => _TransitionDef(machinename, Some(s.name), Some(s), t, isCallTransition = true)).toVector ++
          s.transitions.global.map(t => _TransitionDef(machinename, Some(s.name), Some(s), t, isCallTransition = false)).toVector
      }
      val fromrule =
        rule.transitions.call.map(t => _TransitionDef(machinename, None, None, t, isCallTransition = true)).toVector ++
          rule.transitions.global.map(t => _TransitionDef(machinename, None, None, t, isCallTransition = false)).toVector
      fromstates ++ fromrule ++ rule.statemachines.toVector.flatMap(_all_transitions)
    }

    private def _event_name(
      machineName: String,
      transition: _TransitionDef
    ): String =
      _event_name_from_guard(transition.transition.guard).orElse(transition.transition.getEventName).getOrElse {
        RAISE.syntaxErrorFault(s"StateMachine '$machineName' transition requires on.")
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
      def go(i: Int, depth: Int): Boolean =
        if (i >= p.length)
          depth == 0
        else
          p.charAt(i) match {
            case '(' => go(i + 1, depth + 1)
            case ')' =>
              if (depth <= 0)
                false
              else
                go(i + 1, depth - 1)
            case _ => go(i + 1, depth)
          }
      go(0, 0)
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
        case ResourceIdGuard(resourceid) =>
          Some(s"""event.targetId.exists(_.value == "${_escape_string(resourceid)}")""")
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
      transition: _TransitionDef,
      statemap: Map[String, StateClass]
    ): MComponent.RulePlan = {
      val exit = transition.sourceState.toVector.flatMap(x => _activity_scripts(x.exitActivity))
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

    private def _make_entity_service(
      pkg: MPackage,
      entities: Vector[MEntity]
    ): MService = {
      val ops = entities.flatMap(_make_entity_operations)
      MService(pkg, "entity", ops)
    }

    private def _make_entity_operations(entity: MEntity): Vector[MOperation] = {
      val title = StringUtils.makeTitle(entity.name)
      val pkgname = entity.packageName
      def _qualify(s: String) =
        if (pkgname.isEmpty) s else s"$pkgname.$s"
      val wholeclass = _qualify(s"${_entity_package}.$title")
      val createclass = _qualify(s"${_entity_create_package}.$title")
      val queryclass = _qualify(s"${_entity_query_package}.$title")
      val entityparam = MParameter("entity", MEntityValue.create(entity))
      val updateparam = MParameter("entity", MEntityValue.update(entity))
      val queryparam = MParameter.query("q", MEntityValue.query(entity))
      val queryrecparam = MParameter.query("q", MObjectRef.record)
      val idparam = MParameter.entityId
      val recordparam = MParameter.record
      val loadresult = MResult.option(MEntityValue.whole(entity))
      val searchresult = MResult.search(MEntityValue.whole(entity))
      val create = MOperation.commandBody(s"create$title", entityparam) {
        blockFor(
          "r <- entity_create(action.entity)"
        )(
          "OperationResponse(r.toRecord)"
        )
      }
      val createrec = MOperation.commandBody(s"create${title}Record", recordparam) {
        blockFor(
          s"entity <- exec_pure($createclass.create(action.record))",
          "r <- entity_create(entity)"
        )(
          "OperationResponse(r.toRecord)"
        )
      }
      val load = MOperation.queryBody(s"load$title", idparam, loadresult) {
        blockFor(
          s"r <- entity_load[$wholeclass](action.id)"
        )(
          "OperationResponse(r.toRecord())"
        )
      }
      val loadrec = MOperation.queryBody(s"load${title}Record", idparam, loadresult) {
        blockFor(
          s"r <- entity_load[$wholeclass](action.id)"
        )(
          "OperationResponse(r.toRecord())"
        )
      }
      val save = MOperation.commandBody(s"save$title", entityparam) {
        blockFor(
          s"entity <- exec_pure($wholeclass.create(action.entity.toRecord()))",
          "_ <- entity_save(entity)"
        )(
          "OperationResponse.void"
        )
      }
      val saverec = MOperation.commandBody(s"save${title}Record", entityparam) {
        blockFor(
          s"entity <- exec_pure($wholeclass.create(action.entity.toRecord()))",
          "_ <- entity_save(entity)"
        )(
          "OperationResponse.void"
        )
      }
      val update = MOperation.commandBody(s"update$title", updateparam) {
        blockFor(
          """id <- exec_pure(Consequence.successOrRecordNotFound[EntityId]("id", action.request.toRecord).TAKE)""",
          "_ <- entity_update(id, action.entity)"
        )(
          "OperationResponse.void"
        )
      }
      val updaterec = MOperation.commandBody(s"update${title}Record", updateparam) {
        blockFor(
          """id <- exec_pure(Consequence.successOrRecordNotFound[EntityId]("id", action.request.toRecord).TAKE)""",
          "_ <- entity_update(id, action.entity)"
        )(
          "OperationResponse.void"
        )
      }
      val delete = MOperation.commandBody(s"delete$title", idparam) {
        blockFor(
          "_ <- entity_delete(action.id)"
        )(
          "OperationResponse.void"
        )
      }
      val deletehard = MOperation.commandBody(s"delete${title}Hard", idparam) {
        blockFor(
          "_ <- entity_delete_hard(action.id)"
        )(
          "OperationResponse.void"
        )
      }
      val search = MOperation.queryBody(s"search$title", queryparam, searchresult) {
        blockFor(
          s"r <- entity_search[$wholeclass]($queryclass.collectionId, Query(action.q))"
        )(
          "OperationResponse.create(r)"
        )
      }
      val searchrec = MOperation.queryBody(s"search${title}Record", queryrecparam, searchresult) {
        blockFor(
          s"r <- entity_search[$wholeclass]($queryclass.collectionId, action.q)"
        )(
          "OperationResponse.create(r)"
        )
      }
      Vector(
        create,
        createrec,
        load,
        loadrec,
        save,
        saverec,
        update,
        updaterec,
        delete,
        deletehard,
        search,
        searchrec
      )
    }

    private def _make_aggregate_service(
      pkg: MPackage,
      entities: Vector[MEntity]
    ): MService = {
      val ops = entities.flatMap(_make_aggregate_operations)
      MService(pkg, "aggregate", ops)
    }

    private def _make_aggregate_operations(entity: MEntity): Vector[MOperation] = {
      val title = StringUtils.makeTitle(entity.name)
      val pkgname = entity.packageName
      def _qualify(s: String) =
        if (pkgname.isEmpty) s else s"$pkgname.$s"
      // NOTE: Aggregate-specific DSL/model is not available yet.
      // Default is aggregate.<Entity>. Non-default is aggregate.<aggregate-name>.<Entity>.
      val aggregateclass = _qualify(s"${_aggregate_package(_aggregate_name(entity))}.$title")
      val queryclass = _qualify(s"${_entity_query_package}.$title")
      val createparam = MParameter("entity", MEntityValue.aggregate(entity))
      val saveparam = MParameter("entity", MEntityValue.aggregate(entity))
      val updateparam = MParameter("entity", MEntityValue.aggregate(entity))
      val searchparam = MParameter.query("q", MEntityValue.query(entity))
      val idparam = MParameter.entityId
      val loadresult = MResult.option(MEntityValue.aggregate(entity))
      val searchresult = MResult.search(MEntityValue.aggregate(entity))
      val create = MOperation.commandBody(s"create$title", createparam) {
        blockFor(
          "_ <- uowmNotImplemented[org.goldenport.cncf.unitofwork.UnitOfWorkOp, Unit]"
        )(
          "OperationResponse.void"
        )
      }
      val load = MOperation.queryBody(s"load$title", idparam, loadresult) {
        blockFor(
          s"r <- aggregate_load_option[$aggregateclass](action.id)"
        )(
          "OperationResponse.create(r.map(_.toRecord()))"
        )
      }
      val save = MOperation.commandBody(s"save$title", saveparam) {
        blockFor(
          "_ <- uowmNotImplemented[org.goldenport.cncf.unitofwork.UnitOfWorkOp, Unit]"
        )(
          "OperationResponse.void"
        )
      }
      val update = MOperation.commandBody(s"update$title", updateparam) {
        blockFor(
          "_ <- uowmNotImplemented[org.goldenport.cncf.unitofwork.UnitOfWorkOp, Unit]"
        )(
          "OperationResponse.void"
        )
      }
      val delete = MOperation.commandBody(s"delete$title", idparam) {
        blockFor(
          "_ <- uowmNotImplemented[org.goldenport.cncf.unitofwork.UnitOfWorkOp, Unit]"
        )(
          "OperationResponse.void"
        )
      }
      val search = MOperation.queryBody(s"search$title", searchparam, searchresult) {
        blockFor(
          s"r <- aggregate_search[$aggregateclass]($queryclass.collectionId.name, Query(action.q))"
        )(
          "OperationResponse.create(r)"
        )
      }
      Vector(
        create,
        load,
        save,
        update,
        delete,
        search
      )
    }

    private def _make_view_service(
      pkg: MPackage,
      entities: Vector[MEntity]
    ): MService = {
      val ops = entities.flatMap(_make_view_operations)
      MService(pkg, "view", ops)
    }

    private def _make_view_operations(entity: MEntity): Vector[MOperation] = {
      val title = StringUtils.makeTitle(entity.name)
      val pkgname = entity.packageName
      def _qualify(s: String) =
        if (pkgname.isEmpty) s else s"$pkgname.$s"
      val queryclass = _qualify(s"${_entity_query_package}.$title")
      val searchparam = MParameter.query("q", MEntityValue.query(entity))
      val viewparam = MParameter(
        Description.name("view"),
        MParameter.MDataTypeParameterType(MDataType.string),
        MZeroOne
      )
      val searchrecparam = MParameter.query("q", MObjectRef.record)
      val idparam = MParameter.entityId
      // NOTE: View-specific DSL/model is not available yet.
      // Default is view.<Entity>. Non-default is view.<view-name>.<Entity>.
      val viewvalue = MEntityValue.view(entity)
      val viewclass = _qualify(s"${_view_package(_view_name(entity))}.$title")
      val loadresult = MResult.option(viewvalue)
      val searchresult = MResult.search(viewvalue)
      val load = MOperation.queryBody(s"load$title", idparam, loadresult) {
        blockFor(
          s"r <- view_load[$viewclass]($queryclass.collectionId.name, action.id)"
        )(
          "OperationResponse(r.toRecord())"
        )
      }
      val loadbyview = MOperation.queryBody(s"load${title}ByView", idparam, loadresult) {
        blockFor(
          s"""r <- view_load[$viewclass]($queryclass.collectionId.name, action_required_property_string("view").TAKE, action.id)"""
        )(
          "OperationResponse(r.toRecord())"
        )
      }
      val search = MOperation.queryBody(s"search$title", List(searchparam, viewparam), searchresult) {
        blockFor(
          s"r <- action.view.fold(view_search[$viewclass]($queryclass.collectionId.name, Query(action.q)))(viewname => view_search[$viewclass]($queryclass.collectionId.name, viewname, Query(action.q)))"
        )(
          "OperationResponse.create(r)"
        )
      }
      val searchrec = MOperation.queryBody(s"search${title}Record", searchrecparam, searchresult) {
        blockFor(
          s"""r <- action_property_string("view").fold(view_search[$viewclass]($queryclass.collectionId.name, action.q))(viewname => view_search[$viewclass]($queryclass.collectionId.name, viewname, action.q))"""
        )(
          "OperationResponse.create(r)"
        )
      }
      Vector(
        load,
        loadbyview,
        search,
        searchrec
      )
    }
  }
  object ModelBuilder {
    def apply(p: KaleidoxModel): ModelBuilder = apply(
      p.takeSchemaModel,
      p.takeEntityModel,
      p.takeDataTypeModel,
      p.takePowertypeModel,
      p.takeStateMachineModel,
      p.takeComponentSubsystemModel,
      p.eventModel,
      p.takeOperationModel
    )
  }
}
