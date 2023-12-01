package cozy.modeler

import org.simplemodeling.model._
import org.simplemodeling.model.domain._
import org.smartdox.Description
import org.goldenport.RAISE
import org.goldenport.collection.VectorMap
import org.goldenport.values.Designation
import org.goldenport.values.PathName
import org.goldenport.record.v2.Column
import org.goldenport.statemachine._
import org.goldenport.statemachine.{ExecutionContext => StateMachineContext}
import org.goldenport.statemachine.StateMachineClass
import org.goldenport.sexpr._
import org.goldenport.kaleidox.{Model => KaleidoxModel}
import org.goldenport.kaleidox.lisp.Context
import org.goldenport.kaleidox.model.{SchemaModel, EntityModel, DataTypeModel}
import org.goldenport.kaleidox.model.{PowertypeModel, StateMachineModel}
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
 * @version Oct. 29, 2023
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

    def _guard_(g: SmGuard): Option[MGuard] = None // MGuard(sm)

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
    ps./:(Z())(_+_).r
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
    val pkg = "domain" // TODO
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
    stateMachine: StateMachineModel
  ) {
    def build(): SimpleModel = {
      val entities = entity.classes.values.map(_entity)
      val datatypes = datatype.classes.values.map(_datatype)
      val powertypes = powertype.classes.values.map(_powertype)
      val statemachines = stateMachine.classes.values.map(_statemachine)
      val xs = entities ++ datatypes ++ powertypes ++ statemachines
      SimpleModel(xs.toVector)
    }

    private def _entity(p: EntityClass): MEntity = {
      val packagename = p.packageName
      val desc = Description.name(p.name)
      val affiliation = MPackageRef(packagename)
      val stereotypes = Nil
      val base = p.parents.headOption.map(_object_ref)
      val traits = Nil // TODO
      val powertypes = _powertypes(affiliation, p.schemaClass)
      val attributes = _attributes(affiliation, p.schemaClass)
      val associations = _associations(affiliation, p.schemaClass)
      val operations = Nil // TODO
      val statemachines = Nil // TODO
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

    private def _object_ref(p: EntityClass.ParentRef): MObjectRef =
      p match {
        case m: EntityClass.ParentRef.Name => RAISE.noReachDefect
        case EntityClass.ParentRef.EntityKlass(c) => MEntityRef.create(c.packageName, c.name)
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
      }

    private def _attribute(p: Column): MAttribute = {
      val designation = Designation(p.name)
      val atype = MDatatype(p.datatype)
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

    private def _datatype(p: DataTypeClass): MDatatype = p match {
      case m: DataTypeClass.Plain =>
        val pkg = MPackageRef(m.packageName)
        val desc = m.description
        val datatype = m.datatype
        MDatatype(desc.designation, datatype, pkg, desc)
      case m: DataTypeClass.Complex => ???
    }

    private def _powertype(p: PowertypeClass): MPowertype = {
      val desc = p.description
      val pkg = MPackageRef(p.packageName)
      val kinds = Nil
      val stereotypes = Nil
      MPowertype(desc, pkg, kinds, stereotypes)
    }

    private def _statemachine(p: StateMachineClass): MStateMachine = ???
  }
  object ModelBuilder {
    def apply(p: KaleidoxModel): ModelBuilder = apply(
      p.takeSchemaModel,
      p.takeEntityModel,
      p.takeDataTypeModel,
      p.takePowertypeModel,
      p.takeStateMachineModel
    )
  }
}
