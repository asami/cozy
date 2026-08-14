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
 *  version Mar. 31, 2026
 *  version May. 24, 2026
 *  version Jul. 31, 2026
 * @version Aug. 14, 2026
 * @author  ASAMI, Tomoharu
 */
class Modeler(
  predefinedResultCatalog: PredefinedResultCatalog = PredefinedResultCatalog.empty,
  componentStyleCatalog: ComponentStyleCatalog = ComponentStyleCatalog.EMPTY
) extends org.goldenport.kaleidox.extension.modeler.Modeler {
  import Modeler._

  def explain(model: SimpleModel): Vector[ExplainEntry] =
    Explain.from(model)

  def help(model: KaleidoxModel): Vector[HelpModel] =
    Help.from(model)

  def linkageDiagnostics(model: KaleidoxModel): Vector[LinkageEntry] =
    Linkage.from(model)

  def generateStateMachineDiagram(
    c: Context,
    name: String,
    resourceId: Option[String]
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
    def _init_state_ = MState.initState(sm)
    // def historyState = MState.historyState(sm)

    def _state_(p: StateClass): MState = {
      val s = MState.create(sm, p.name, Right(p.value))
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

    def _build_transitions_(statemap: StateHanger) = {
      def _build_state_(s: StateClass): Unit = {
        def _transition_(t: Transition): Option[MTransition] = {
          val g = _guard_(t.guard)
          val event = t.getEventName.map(x => MEvent(x)) // TODO share
          val action = None // MAction(sm, "???")

          def _name_transition_(p: NameTransitionTo) =
            (statemap.get(s.name), statemap.get(p.name)) match {
              case (Some(from), Some(to)) => MTransition(sm, event, g, from, to, action)
              case (Some(from), None) => RAISE.noReachDefect
              case (None, Some(to)) => MTransition(sm, event, g, _init_state_, to, action)
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
              case (None, Some(to)) => MTransition(sm, event, g, _init_state_, to, action)
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

        smr.states.foreach(_build_state_)
        smr.statemachines.foreach(_build_statemachine_)
        val ts = smr.transitions.call.flatMap(_transition_) ++ smr.transitions.global.flatMap(_transition_)
        statemap.get(smr.name getOrElse "").foreach(_.transitions = ts.toList)
      }

      p.statemachines.foreach(_build_statemachine_)
      p.states.foreach(_build_state_)
    }

    def _guard_(g: SmGuard): Option[MGuard] =
      _guard_mark_(g).map(mark => MGuard(sm, Description.name("guard"), mark))

    def _guard_mark_(g: SmGuard): Option[String] =
      g match {
        case AllGuard => None
        case EventNameGuard(_) => None
        case CmlExpressionGuard(expression) =>
          Option(expression).map(_.trim).filter(_.nonEmpty)
        case ResourceIdGuard(resourceId) =>
          Some(s"""event.targetId.exists(_.value == "$resourceId")""")
        case ToStateGuard(name, value) =>
          value match {
            case Some(v) => Some(s"event.name == '${name}' || event.name == '${v.toString}'")
            case None => Some(s"event.name == '${name}'")
          }
        case AndGuard(exprs) =>
          _join_guard_mark_(exprs, "&&")
        case OrGuard(exprs) =>
          _join_guard_mark_(exprs, "||")
      }

    def _join_guard_mark_(exprs: Vector[SmGuard], delimiter: String): Option[String] = {
      val a = exprs.flatMap(_guard_mark_)
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
    _build_transitions_(states)
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

  def buildValueModel(model: KaleidoxModel): SimpleModel =
    _make_model_value(model)

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

  private def _make_model_value(p: IModel): SimpleModel = p match {
    case m: KaleidoxModel => _make_model_value(m)
    case m => RAISE.noReachDefect
  }

  private def _make_model(p: KaleidoxModel): SimpleModel = {
    ModelBuilder(p, predefinedResultCatalog, componentStyleCatalog).build()
  }

  private def _make_model_value(p: KaleidoxModel): SimpleModel = {
    ModelBuilder(p, predefinedResultCatalog, componentStyleCatalog).buildValue()
  }

  def generateScala(
    c: Context,
    model: SModel
  ): SExpr = {
    _strip_build_sbt(_make_scala(c, model, "domain"))
  }

  def generateScalaValue(
    c: Context,
    model: SModel
  ): SExpr = {
    _strip_build_sbt(_make_scala_value(c, model, "domain"))
  }

  private def _strip_build_sbt(expr: SExpr): SExpr = expr match {
    case m: STree =>
      m.tree.backend.remove("build.sbt")
      m
    case m =>
      m
  }

  private def _make_scala(c: Context, smodel: SModel, pkg: String): SExpr = {
    val env = c.executionContext.environment
    val model = _make_model(smodel.model)
    val g = new ScalaGenerator(env, model)
    val targetpkg = _resolve_generate_package(model, pkg)
    model.getPackage(targetpkg).orElse(Some(model.root)) match {
      case Some(s) => g.generate(s)
      case None => SError.notFound("Unkown package", targetpkg)
    }
  }

  private def _make_scala_value(c: Context, smodel: SModel, pkg: String): SExpr = {
    val env = c.executionContext.environment
    val model = _make_model_value(smodel.model)
    val g = new ScalaGenerator(env, model)
    val targetpkg = _resolve_generate_package(model, pkg)
    model.getPackage(targetpkg).orElse(Some(model.root)) match {
      case Some(s) => g.generate(s)
      case None => SError.notFound("Unkown package", targetpkg)
    }
  }

  private def _resolve_generate_package(
    model: SimpleModel,
    requested: String
  ): String = {
    if (model.getPackage(requested).isDefined)
      requested
    else
      model.elements.collectFirst {
        case m: MComponent => m.packageName
      }.orElse {
        model.elements.collectFirst {
          case m: MObject => m.packageName
        }
      }.getOrElse(requested)
  }
}

object Modeler {
  final case class HelpModel(
    `type`: String,
    name: String,
    summary: String,
    children: Vector[String] = Vector.empty,
    details: Map[String, Vector[String]] = Map.empty,
    usage: Vector[String] = Vector.empty
  )

  case class ExplainEntry(
    sectionPath: String,
    classifiedRole: String,
    normalizedTarget: String
  )

  case class LinkageEntry(
    sectionPath: String,
    target: String,
    resolved: Boolean,
    facet: String
  )

  object Explain {
    def from(model: SimpleModel): Vector[ExplainEntry] = {
      model.elements.toVector.flatMap {
        case m: MDomainValue => _value(m)
        case m: MDomainResource => _entity(m)
        case _ => Vector.empty
      }
    }

    private def _value(p: MDomainValue): Vector[ExplainEntry] =
      Vector(
        ExplainEntry(
          sectionPath = s"VALUE/${p.name}",
          classifiedRole = "structural",
          normalizedTarget = s"${p.packageName}.${p.name}"
        )
      ) ++ p.attributes.toVector.map { a =>
        ExplainEntry(
          sectionPath = s"VALUE/${p.name}/ATTRIBUTE/${a.name}",
          classifiedRole = "structural",
          normalizedTarget = s"${p.packageName}.${p.name}.${a.name}"
        )
      }

    private def _entity(p: MDomainResource): Vector[ExplainEntry] =
      Vector(
        ExplainEntry(
          sectionPath = s"ENTITY/${p.name}",
          classifiedRole = "structural",
          normalizedTarget = s"${p.packageName}.${p.name}"
        )
      ) ++ p.attributes.toVector.map { a =>
        ExplainEntry(
          sectionPath = s"ENTITY/${p.name}/ATTRIBUTE/${a.name}",
          classifiedRole = "structural",
          normalizedTarget = s"${p.packageName}.${p.name}.${a.name}"
        )
      }
  }

  object Help {
    private val _narrative_keys = Set(
      "headline",
      "brief",
      "summary",
      "description",
      "lead",
      "content",
      "abstract",
      "remarks",
      "tooltip"
    )

    def from(model: KaleidoxModel): Vector[HelpModel] = {
      val valuemodel = model.getValueModel
      model.divisions.collect {
        case d: org.goldenport.kaleidox.Model.ValueDivision =>
          val root = d.section
          root.blocks.sections.filterNot(x => _narrative_keys.contains(x.keyForModel.toLowerCase)).toVector.map { v =>
            _value_help_model(valuemodel.flatMap(_.get(v.nameForModel)), v)
          }
      }.flatten.toVector
    }

    private def _value_help_model(
      valueclass: Option[org.goldenport.kaleidox.model.ValueModel.ValueClass],
      p: org.goldenport.parser.LogicalSection
    ): HelpModel = {
      val summary = _child_text(p, "summary").
        orElse(_child_text(p, "description")).
        orElse(_free_narrative(p)).
        getOrElse(s"Value: ${p.nameForModel}")
      val description = _child_text(p, "description").orElse(_free_narrative(p))
      val attributes = valueclass.map(_.schema.columns.map(_.name).toVector).getOrElse(Vector.empty)
      val details = Vector.newBuilder[(String, Vector[String])]
      if (attributes.nonEmpty)
        details += "attributes" -> attributes
      description.foreach(x => details += "description" -> Vector(x))
      details += "sectionPath" -> Vector(s"VALUE/${p.nameForModel}")
      HelpModel(
        `type` = "value",
        name = p.nameForModel,
        summary = summary,
        children = attributes,
        details = details.result().toMap,
        usage = Vector.empty
      )
    }

    private def _child_text(p: org.goldenport.parser.LogicalSection, name: String): Option[String] =
      p.blocks.sections.find(_.keyForModel.equalsIgnoreCase(name)).flatMap { s =>
        val t = s.blocks.text.trim
        if (t.isEmpty) None else Some(t)
      }

    private def _free_narrative(p: org.goldenport.parser.LogicalSection): Option[String] = {
      val s = p.blocks.prologue.text.trim
      if (s.isEmpty) None else Some(s)
    }
  }

  object Linkage {
    def from(model: KaleidoxModel): Vector[LinkageEntry] = {
      val eventmodel = model.eventModel
      val eventnames = eventmodel.receptionDefinitions.map(_.name).toSet
      val actionnames = eventmodel.receptionDefinitions.flatMap(_.actionName).toSet
      eventmodel.subscriptionDefinitions.toVector.flatMap { s =>
        val eventlinks = s.eventName.toVector.map { eventname =>
          LinkageEntry(
            sectionPath = s"SUBSCRIPTION/${s.name}/eventName",
            target = eventname,
            resolved = eventnames.contains(eventname),
            facet = "event"
          )
        }
        val actionlinks = s.actionName.toVector.map { actionname =>
          LinkageEntry(
            sectionPath = s"SUBSCRIPTION/${s.name}/actionName",
            target = actionname,
            resolved = actionnames.contains(actionname),
            facet = "action"
          )
        }
        eventlinks ++ actionlinks
      }
    }
  }
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

  final case class OperationRelationshipBinding(
    childEntityBindings: Vector[MComponent.OperationChildEntityBinding] = Vector.empty,
    associationBinding: Option[MComponent.OperationAssociationBinding] = None
  )

  final case class ServiceOperationLeafContract(
    kind: Option[OperationModel.OperationKind] = None,
    inputType: Option[String] = None,
    outputType: Option[String] = None
  ) {
    def nonEmpty: Boolean =
      kind.nonEmpty || inputType.exists(_.trim.nonEmpty) || outputType.exists(_.trim.nonEmpty)
  }

  case class ModelBuilder(
    schema: SchemaModel,
    entity: EntityModel,
    datatype: DataTypeModel,
    value: ValueModel,
    powertype: PowertypeModel,
    stateMachine: StateMachineModel,
    actor: ActorModel,
    componentSubsystem: ComponentSubsystemModel,
    service: ServiceModel,
    event: EventModel,
    operation: OperationModel,
    predefinedResultCatalog: PredefinedResultCatalog = PredefinedResultCatalog.empty,
    componentStyleCatalog: ComponentStyleCatalog = ComponentStyleCatalog.EMPTY,
    cmlDeclaredTypeNames: Set[String] = Set.empty,
    relationships: Vector[MComponent.RelationshipDefinition] = Vector.empty,
    operationRelationshipBindings: Map[String, OperationRelationshipBinding] = Map.empty,
    serviceOperationLeafContracts: Map[String, ServiceOperationLeafContract] = Map.empty
  ) extends ModelBuildContext {
    private lazy val _type_projector = new ModelTypeProjector(this)
    private lazy val _state_machine_projector = new ModelStateMachineProjector(this)
    private lazy val _service_operation_projector = new ModelServiceOperationProjector(this, _type_projector)
    private lazy val _runtime_definition_projector = new ModelRuntimeDefinitionProjector(this, _type_projector, _state_machine_projector, _service_operation_projector)
    private lazy val _component_projector = new ModelComponentProjector(this, _state_machine_projector, _service_operation_projector, _runtime_definition_projector)

    def build(): SimpleModel = {
      _build(includecomponents = true)
    }

    def buildValue(): SimpleModel = {
      _build(includecomponents = false)
    }

    private def _build(includecomponents: Boolean): SimpleModel = {
      _validate_component_service_operation_boundary()
      _validate_component_style_selection()
      val entities = entity.classes.values.filterNot(c => _type_projector.isSimpleEntity(c.name)).map(_type_projector.projectEntity)
      val values = value.classes.values.map(_type_projector.projectValue) ++ _type_projector.serviceInlineValues.map(_type_projector.projectValue)
      val datatypes = datatype.classes.values.map(_type_projector.projectDatatype)
      val powertypes = powertype.classes.values.map(_type_projector.projectPowertype)
      val statemachines = stateMachine.classes.values.map(_state_machine_projector.projectStateMachine)
      val xs = entities ++ values ++ datatypes ++ powertypes ++ statemachines
      val a = SimpleModel(xs.toVector)
      if (includecomponents) {
        val comps = _component_projector.complementComponents(a)
        a.add(comps)
      } else {
        a
      }
    }

    private def _validate_component_service_operation_boundary(): Unit = {
      if (operation.operations.nonEmpty)
        RAISE.syntaxErrorFault("Top-level OPERATION is not supported; define operations under SERVICE.")
      if (service.classes.nonEmpty && componentSubsystem.components.isEmpty)
        RAISE.syntaxErrorFault("SERVICE requires COMPONENT; services are owned by a component.")
    }

    private def _validate_component_style_selection(): Unit =
      componentSubsystem.components.foreach { component =>
        component.componentStyle.foreach(componentStyleCatalog.requireSelection)
      }
  }
  object ModelBuilder {
    def apply(p: KaleidoxModel): ModelBuilder =
      apply(p, PredefinedResultCatalog.empty, ComponentStyleCatalog.EMPTY)

    def apply(
      p: KaleidoxModel,
      predefinedResultCatalog: PredefinedResultCatalog
    ): ModelBuilder =
      apply(p, predefinedResultCatalog, ComponentStyleCatalog.EMPTY)

    def apply(
      p: KaleidoxModel,
      predefinedResultCatalog: PredefinedResultCatalog,
      componentStyleCatalog: ComponentStyleCatalog
    ): ModelBuilder = {
      val relationships = ModelerRelationshipCml.relationshipDefinitions(p)
      val operationbindings = ModelerRelationshipCml.operationBindings(p, relationships)
      val leafcontracts = ModelerRelationshipCml.serviceOperationLeafContracts(p)
      apply(
        p.takeSchemaModel,
        p.takeEntityModel,
        p.takeDataTypeModel,
        p.getValueModel.getOrElse(ValueModel.empty),
        p.takePowertypeModel,
        p.takeStateMachineModel,
        p.takeActorModel,
        p.takeComponentSubsystemModel,
        p.getServiceModel.getOrElse(ServiceModel.empty),
        p.eventModel,
        p.takeOperationModel,
        predefinedResultCatalog,
        componentStyleCatalog,
        ModelerRelationshipCml.cmlDeclaredTypeNames(p),
        relationships,
        operationbindings,
        leafcontracts
      )
    }


  }
}
