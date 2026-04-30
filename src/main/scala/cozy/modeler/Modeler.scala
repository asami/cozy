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
import org.goldenport.kaleidox.model.CmlExpressionGuard
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
 * @version Apr. 30, 2026
 * @author  ASAMI, Tomoharu
 */
class Modeler() extends org.goldenport.kaleidox.extension.modeler.Modeler {
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
    ModelBuilder(p).build()
  }

  private def _make_model_value(p: KaleidoxModel): SimpleModel = {
    ModelBuilder(p).buildValue()
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
    private val _narrativeKeys = Set(
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
      val valueModel = model.getValueModel
      model.divisions.collect {
        case d: org.goldenport.kaleidox.Model.ValueDivision =>
          val root = d.section
          root.blocks.sections.filterNot(x => _narrativeKeys.contains(x.keyForModel.toLowerCase)).toVector.map { v =>
            _value_help_model(valueModel.flatMap(_.get(v.nameForModel)), v)
          }
      }.flatten.toVector
    }

    private def _value_help_model(
      valueClass: Option[org.goldenport.kaleidox.model.ValueModel.ValueClass],
      p: org.goldenport.parser.LogicalSection
    ): HelpModel = {
      val summary = _child_text(p, "summary").
        orElse(_child_text(p, "description")).
        orElse(_free_narrative(p)).
        getOrElse(s"Value: ${p.nameForModel}")
      val description = _child_text(p, "description").orElse(_free_narrative(p))
      val attributes = valueClass.map(_.schema.columns.map(_.name).toVector).getOrElse(Vector.empty)
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
      val eventModel = model.eventModel
      val eventNames = eventModel.receptionDefinitions.map(_.name).toSet
      val actionNames = eventModel.receptionDefinitions.flatMap(_.actionName).toSet
      eventModel.subscriptionDefinitions.toVector.flatMap { s =>
        val eventLinks = s.eventName.toVector.map { eventName =>
          LinkageEntry(
            sectionPath = s"SUBSCRIPTION/${s.name}/eventName",
            target = eventName,
            resolved = eventNames.contains(eventName),
            facet = "event"
          )
        }
        val actionLinks = s.actionName.toVector.map { actionName =>
          LinkageEntry(
            sectionPath = s"SUBSCRIPTION/${s.name}/actionName",
            target = actionName,
            resolved = actionNames.contains(actionName),
            facet = "action"
          )
        }
        eventLinks ++ actionLinks
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

  private object RelationshipCml {
    private val KindAssociation = "association"
    private val KindAggregation = "aggregation"
    private val KindComposition = "composition"
    private val StorageAssociationRecord = "association-record"
    private val StorageChildParentIdField = "child-parent-id-field"
    private val StorageEmbeddedValueObject = "embedded-value-object"

    def relationshipDefinitions(model: KaleidoxModel): Vector[MComponent.RelationshipDefinition] = {
      val valueNames = model.getValueModel.map(_.classes.keys.toSet).getOrElse(Set.empty[String])
      val entities = model.takeEntityModel.classes
      _division_sections(model, "RELATIONSHIP").flatMap { root =>
        _validate_blank_after_heading(root, "RELATIONSHIP")
        root.blocks.sections.toVector.map(_relationship_definition(_, valueNames, entities))
      }
    }

    def operationBindings(
      model: KaleidoxModel,
      relationships: Vector[MComponent.RelationshipDefinition]
    ): Map[String, OperationRelationshipBinding] = {
      val relmap = relationships.map(x => x.name -> x).toMap
      val pairs = _operation_sections(model).flatMap { op =>
        val childbindings = _children(op, "CHILD ENTITY BINDING").map(_child_entity_binding(_, relmap))
        val associations = _children(op, "ASSOCIATION BINDING").map(_association_binding(_, relmap))
        if (associations.size > 1)
          RAISE.syntaxErrorFault(s"Operation '${op.nameForModel}' accepts at most one ASSOCIATION BINDING.")
        val binding = OperationRelationshipBinding(
          childEntityBindings = childbindings,
          associationBinding = associations.headOption
        )
        if (binding.childEntityBindings.isEmpty && binding.associationBinding.isEmpty)
          None
        else
          Some(op.nameForModel -> binding)
      }
      pairs.toMap
    }

    private def _relationship_definition(
      section: LogicalSection,
      valueNames: Set[String],
      entities: VectorMap[String, EntityClass]
    ): MComponent.RelationshipDefinition = {
      val name = section.nameForModel
      _validate_blank_after_heading(section, s"RELATIONSHIP '$name'")
      section.blocks.sections.foreach(s => _validate_blank_after_heading(s, s"RELATIONSHIP '$name' field ${s.nameForModel}"))
      val kind = _required(section, "KIND", name).toLowerCase
      val source = _required(section, "SOURCE", name)
      val target = _required(section, "TARGET", name)
      val storage = _child_text(section, "STORAGE").map(_.toLowerCase).getOrElse {
        kind match {
          case KindComposition => StorageChildParentIdField
          case _ => StorageAssociationRecord
        }
      }
      _validate_enum(name, "KIND", kind, Set(KindAssociation, KindAggregation, KindComposition))
      _validate_enum(name, "STORAGE", storage, Set(StorageAssociationRecord, StorageChildParentIdField, StorageEmbeddedValueObject))
      val parentIdField = _child_text(section, "PARENT ID FIELD")
      val valueField = _child_text(section, "VALUE FIELD")
      if (kind == KindComposition && storage == StorageChildParentIdField && parentIdField.isEmpty)
        RAISE.syntaxErrorFault(s"RELATIONSHIP '$name' composition with child-parent-id-field requires PARENT ID FIELD.")
      if (storage == StorageEmbeddedValueObject && kind != KindComposition)
        RAISE.syntaxErrorFault(s"RELATIONSHIP '$name' embedded-value-object storage requires composition kind.")
      if (storage == StorageEmbeddedValueObject && valueField.isEmpty)
        RAISE.syntaxErrorFault(s"RELATIONSHIP '$name' composition with embedded-value-object requires VALUE FIELD.")
      if (storage == StorageEmbeddedValueObject && parentIdField.nonEmpty)
        RAISE.syntaxErrorFault(s"RELATIONSHIP '$name' embedded-value-object storage does not accept PARENT ID FIELD.")
      if (storage == StorageEmbeddedValueObject && !valueNames.contains(target))
        RAISE.syntaxErrorFault(s"RELATIONSHIP '$name' embedded-value-object TARGET '$target' must reference a VALUE.")
      if (storage == StorageEmbeddedValueObject)
        _validate_embedded_value_field(name, source, target, valueField.get, entities)
      MComponent.RelationshipDefinition(
        name = name,
        kind = kind,
        sourceEntityName = source,
        targetEntityName = target,
        targetModelKind = if (storage == StorageEmbeddedValueObject) "value" else "entity",
        multiplicity = _child_text(section, "MULTIPLICITY"),
        storageMode = storage,
        parentIdField = parentIdField,
        valueField = valueField,
        sortOrderField = _child_text(section, "SORT ORDER FIELD"),
        associationDomain = _child_text(section, "ASSOCIATION DOMAIN"),
        targetKind = _child_text(section, "TARGET KIND"),
        lifecyclePolicy = _child_text(section, "LIFECYCLE")
      )
    }

    private def _validate_embedded_value_field(
      relationshipName: String,
      sourceEntityName: String,
      targetValueName: String,
      valueField: String,
      entities: VectorMap[String, EntityClass]
    ): Unit = {
      val entity = entities.getOrElse(sourceEntityName,
        RAISE.syntaxErrorFault(s"RELATIONSHIP '$relationshipName' embedded-value-object SOURCE '$sourceEntityName' must reference an ENTITY.")
      )
      val attribute = entity.schemaClass.slots.collectFirst {
        case p: SchemaModel.Attribute if _same_key(p.name, valueField) => p
      }.getOrElse(
        RAISE.syntaxErrorFault(s"RELATIONSHIP '$relationshipName' VALUE FIELD '$valueField' is not an ATTRIBUTE of SOURCE '$sourceEntityName'.")
      )
      val actual = attribute.rawTypeName.map(_.split("\\.").last).getOrElse("")
      if (!_same_key(actual, targetValueName))
        RAISE.syntaxErrorFault(s"RELATIONSHIP '$relationshipName' VALUE FIELD '$valueField' must have VALUE type '$targetValueName'.")
    }

    private def _child_entity_binding(
      section: LogicalSection,
      relationships: Map[String, MComponent.RelationshipDefinition]
    ): MComponent.OperationChildEntityBinding = {
      _validate_blank_after_heading(section, s"CHILD ENTITY BINDING '${section.nameForModel}'")
      section.blocks.sections.foreach(s => _validate_blank_after_heading(s, s"CHILD ENTITY BINDING field ${s.nameForModel}"))
      val relationshipName = _required(section, "RELATIONSHIP", section.nameForModel)
      val relationship = relationships.getOrElse(relationshipName,
        RAISE.syntaxErrorFault(s"CHILD ENTITY BINDING references unknown RELATIONSHIP '$relationshipName'.")
      )
      if (relationship.storageMode != StorageChildParentIdField)
        RAISE.syntaxErrorFault(s"CHILD ENTITY BINDING '$relationshipName' requires child-parent-id-field storage.")
      val parentIdField = relationship.parentIdField.getOrElse(
        RAISE.syntaxErrorFault(s"RELATIONSHIP '$relationshipName' requires PARENT ID FIELD for CHILD ENTITY BINDING.")
      )
      MComponent.OperationChildEntityBinding(
        name = relationshipName,
        entityName = relationship.targetEntityName,
        inputParameter = _required(section, "INPUT", relationshipName),
        parentIdField = parentIdField,
        relationshipName = Some(relationshipName),
        sourceEntityIdMode = _child_text(section, "SOURCE ENTITY ID").getOrElse("entity-create-result"),
        sourceEntityIdParameters = _child_vector(section, "SOURCE ENTITY ID PARAMETERS"),
        sourceEntityIdResultFields = _child_vector(section, "SOURCE ENTITY ID RESULT FIELDS", Vector("entity_id", "entityId", "id")),
        childIdField = _child_text(section, "CHILD ID FIELD").orElse(Some("id")),
        sortOrderField = _child_text(section, "SORT ORDER FIELD").orElse(relationship.sortOrderField),
        createsEntity = true,
        failurePolicy = _child_text(section, "FAILURE POLICY").getOrElse("compensate-parent-on-create")
      )
    }

    private def _association_binding(
      section: LogicalSection,
      relationships: Map[String, MComponent.RelationshipDefinition]
    ): MComponent.OperationAssociationBinding = {
      _validate_blank_after_heading(section, s"ASSOCIATION BINDING '${section.nameForModel}'")
      section.blocks.sections.foreach(s => _validate_blank_after_heading(s, s"ASSOCIATION BINDING field ${s.nameForModel}"))
      val relationshipName = _required(section, "RELATIONSHIP", section.nameForModel)
      val relationship = relationships.getOrElse(relationshipName,
        RAISE.syntaxErrorFault(s"ASSOCIATION BINDING references unknown RELATIONSHIP '$relationshipName'.")
      )
      if (relationship.storageMode != StorageAssociationRecord)
        RAISE.syntaxErrorFault(s"ASSOCIATION BINDING '$relationshipName' requires association-record storage.")
      MComponent.OperationAssociationBinding(
        domain = relationship.associationDomain.getOrElse(relationship.name),
        targetKind = relationship.targetKind.getOrElse(relationship.targetEntityName),
        createsAssociation = true,
        detachesAssociation = false,
        roles = _child_vector(section, "ROLES"),
        parameters = _child_vector(section, "PARAMETERS"),
        sourceEntityIdMode = _child_text(section, "SOURCE ENTITY ID").getOrElse("entity-create-result"),
        sourceEntityIdParameters = _child_vector(section, "SOURCE ENTITY ID PARAMETERS"),
        sourceEntityIdResultFields = _child_vector(section, "SOURCE ENTITY ID RESULT FIELDS", Vector("entity_id", "entityId", "id")),
        targetIdParameters = _child_vector(section, "TARGET ID PARAMETERS"),
        sortOrderParameters = _child_vector(section, "SORT ORDER PARAMETERS")
      )
    }

    private def _operation_sections(model: KaleidoxModel): Vector[LogicalSection] =
      _division_sections(model, "SERVICE").flatMap { root =>
        root.blocks.sections.toVector.flatMap { service =>
          _children(service, "OPERATION").flatMap(_.blocks.sections.toVector)
        }
      }

    private def _division_sections(model: KaleidoxModel, name: String): Vector[LogicalSection] =
      model.divisions.toVector.flatMap { d =>
        _section(d).filter { s =>
          _same_key(d.name, name) || _same_key(s.keyForModel, name) || _same_key(s.nameForModel, name)
        }
      }

    private def _section(d: org.goldenport.kaleidox.Model.Division): Option[LogicalSection] =
      d match {
        case p: Product =>
          p.productIterator.collectFirst {
            case s: LogicalSection => s
          }
        case _ => None
      }

    private def _children(section: LogicalSection, name: String): Vector[LogicalSection] =
      section.blocks.sections.toVector.filter(x => _same_key(x.keyForModel, name) || _same_key(x.nameForModel, name))

    private def _required(section: LogicalSection, name: String, context: String): String =
      _child_text(section, name).getOrElse(
        RAISE.syntaxErrorFault(s"$context requires $name.")
      )

    private def _child_text(section: LogicalSection, name: String): Option[String] =
      section.blocks.sections.find(x => _same_key(x.keyForModel, name) || _same_key(x.nameForModel, name)).flatMap { s =>
        val text = s.blocks.text.trim
        if (text.isEmpty) None else Some(text)
      }

    private def _child_vector(section: LogicalSection, name: String): Vector[String] =
      _child_vector(section, name, Vector.empty)

    private def _child_vector(section: LogicalSection, name: String, default: Vector[String]): Vector[String] =
      _child_text(section, name).map(_split_values).filter(_.nonEmpty).getOrElse(default)

    private def _split_values(text: String): Vector[String] =
      text.split("[,\\n]").toVector.map(_.trim).filter(_.nonEmpty)

    private def _validate_enum(context: String, field: String, value: String, allowed: Set[String]): Unit =
      if (!allowed.contains(value))
        RAISE.syntaxErrorFault(s"$context $field must be one of ${allowed.toVector.sorted.mkString(", ")}: $value")

    private def _validate_blank_after_heading(section: LogicalSection, context: String): Unit =
      for {
        header <- _line(section)
        first <- _first_block_line(section)
        if first <= header + 1
      } RAISE.syntaxErrorFault(s"$context requires a blank line after the section heading.")

    private def _first_block_line(section: LogicalSection): Option[Int] =
      section.blocks.blocks.headOption.flatMap {
        case s: LogicalSection => _line(s)
        case p: org.goldenport.parser.LogicalParagraph =>
          p.lines.lines.headOption.flatMap(x => x.location.flatMap(_line))
        case _ => None
      }

    private def _line(section: LogicalSection): Option[Int] =
      section.location.flatMap(_line)

    private def _line(location: org.goldenport.parser.ParseLocation): Option[Int] =
      location.line

    private def _same_key(lhs: String, rhs: String): Boolean =
      _normalize_key(lhs) == _normalize_key(rhs)

    private def _normalize_key(s: String): String =
      s.toLowerCase.filter(_.isLetterOrDigit)
  }

  case class ModelBuilder(
    schema: SchemaModel,
    entity: EntityModel,
    datatype: DataTypeModel,
    value: ValueModel,
    powertype: PowertypeModel,
    stateMachine: StateMachineModel,
    componentSubsystem: ComponentSubsystemModel,
    service: ServiceModel,
    event: EventModel,
    operation: OperationModel,
    relationships: Vector[MComponent.RelationshipDefinition] = Vector.empty,
    operationRelationshipBindings: Map[String, OperationRelationshipBinding] = Map.empty
  ) {
    def build(): SimpleModel = {
      _build(includeComponents = true)
    }

    def buildValue(): SimpleModel = {
      _build(includeComponents = false)
    }

    private def _build(includeComponents: Boolean): SimpleModel = {
      _validate_component_service_operation_boundary()
      val entities = entity.classes.values.filterNot(c => _is_simple_entity(c.name)).map(_entity)
      val values = value.classes.values.map(_value) ++ _service_inline_values.map(_value)
      val datatypes = datatype.classes.values.map(_datatype)
      val powertypes = powertype.classes.values.map(_powertype)
      val statemachines = stateMachine.classes.values.map(_statemachine)
      val xs = entities ++ values ++ datatypes ++ powertypes ++ statemachines
      val a = SimpleModel(xs.toVector)
      if (includeComponents) {
        val comps = _complement_components(a)
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

    private def _entity(p: EntityClass): MEntity = {
      val packagename = _entity_package_name(p.packageName)
      val desc = Description.name(p.name)
      val affiliation = MPackageRef(packagename)
      val stereotypes = Nil
      val base = p.parents.headOption.map(_object_ref).orElse {
        p.schemaClass.features.parentsName.headOption.map(_object_ref)
      }
      val traits = _delegate_traits(p)
      val powertypes = _powertypes(affiliation, p.schemaClass)
      val attributes = _merge_attributes(
        _simple_entity_attributes(affiliation, p),
        _delegate_attributes(affiliation, p) ++ _attributes(affiliation, p.schemaClass)
      )
      val associations = _merge_associations(
        _associations(affiliation, p.schemaClass),
        _aggregate_member_associations(affiliation, p.schemaClass.aggregate)
      )
      val operations = _aggregate_methods(p)
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
        statemachines,
        usageKind = p.usageKind,
        operationKind = p.operationKind,
        applicationDomain = p.applicationDomain
      )
    }

    private def _aggregate_methods(
      p: EntityClass
    ): List[MOperation] =
      p.schemaClass.aggregate.toList.flatMap { aggregate =>
        val creates = aggregate.creates.map { create =>
          MOperation.command(create.name, MParameter.record("input"))
        }
        val commands = aggregate.commands.map { command =>
          MOperation.command(command.name, MParameter.record("input"))
        }
        creates ++ commands
      }

    private lazy val _simple_entity_template: Option[EntityClass] =
      entity.classes.values.find(c => _is_simple_entity(c.name))

    private def _simple_entity_attributes(
      pkg: MPackageRef,
      p: EntityClass
    ): List[MAttribute] = {
      val inheritsSimpleEntity = {
        val byParentRef = p.parents.exists {
          case m: EntityClass.ParentRef.Name => _is_simple_entity(m.name)
          case EntityClass.ParentRef.EntityKlass(c) => _is_simple_entity(c.name)
        }
        val byFeatures = p.schemaClass.features.parentsName.exists(_is_simple_entity)
        byParentRef || byFeatures
      }
      if (inheritsSimpleEntity)
        _simple_entity_template.toList.flatMap(t => _attributes(pkg, t.schemaClass))
      else
        Nil
    }

    private def _delegate_attributes(
      pkg: MPackageRef,
      p: EntityClass
    ): List[MAttribute] = {
      p.schemaClass.features.delegates.toList.flatMap { d =>
        val name = Option(d.name).map(_.trim).getOrElse("")
        if (name.isEmpty) Nil
        else {
          val isOptional = Option(d.multiplicity).map(_.trim).contains("?")
          _delegate_value_attribute(pkg, name, isOptional).toList
        }
      }
    }

    private def _delegate_traits(
      p: EntityClass
    ): List[MTraitRef] =
      p.schemaClass.features.delegates.toList.flatMap { d =>
        val simple = Option(d.name).map(_.trim).getOrElse("")
        if (simple.isEmpty || value.classes.get(simple).isDefined)
          None
        else
          Some(MTraitRef(MPackageRef("org.simplemodeling.model.value"), s"${simple}Holder"))
      }

    private def _delegate_value_attribute(
      pkg: MPackageRef,
      delegateName: String,
      isOptional: Boolean
    ): Option[MAttribute] = {
      val attrname = delegateName.head.toLower + delegateName.drop(1)
      val multiplicity = if (isOptional) MZeroOne else MOne
      _resolve_object_attribute_type(pkg, delegateName).map { atype =>
        MAttribute(
          Designation.nameLabel(attrname, None),
          atype,
          multiplicity,
          Nil,
          None,
          readonly = false,
          derived = None,
          description = Description.empty
        )
      }
    }

    private def _merge_attributes(
      inherited: List[MAttribute],
      own: List[MAttribute]
    ): List[MAttribute] = {
      val z = mutable.LinkedHashMap.empty[String, MAttribute]
      inherited.foreach(x => z.update(x.name, x))
      own.foreach(x => z.update(x.name, x))
      z.values.toList
    }

    private def _merge_associations(
      base: List[MAssociation],
      own: List[MAssociation]
    ): List[MAssociation] = {
      val z = mutable.LinkedHashMap.empty[String, MAssociation]
      base.foreach(x => z.update(x.designation.name, x))
      own.foreach(x => z.update(x.designation.name, x))
      z.values.toList
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
        MObjectRef.create("org.simplemodeling.model.SimpleEntity")
      else
        MObjectRef.create(p)
    }

    private def _object_ref(packagename: String, name: String): MObjectRef = {
      val isSimpleEntity = _is_simple_entity(name)
      if (isSimpleEntity)
        MObjectRef.create("org.simplemodeling.model.SimpleEntity")
      else
        MEntityRef.create(_entity_package_name(packagename), name)
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
        case m: SchemaModel.Attribute => Some(_attribute(pkg, m))
        case m: SchemaModel.Association => None
        case m: SchemaModel.PowertypeRelationship => None
        case m: SchemaModel.StateMachineRelationship => Some(_attribute(m.toColumn)) // TODO
        case m: SchemaModel.StateMachine => Some(_attribute(m.toColumn))
      }

    private def _attribute(pkg: MPackageRef, p: SchemaModel.Attribute): MAttribute = {
      val column = p.toColumn
      val designation = Designation.nameLabel(column.name, column.i18nLabel)
      val atype = _resolve_attribute_type(pkg, p)
      val multiplicity = MMultiplicity(column.multiplicity)
      val web = _web(p.web)
      val constraints: List[MConstraint] = column.constraints.map(RConstraint) ++ web.validationConstraints
      val readonly = false
      val description = Description.empty
      MAttribute(designation, atype, multiplicity, constraints, Some(column), readonly, p.derived, web = web, description = description)
    }

    private def _web(p: SchemaModel.Attribute.Web): MAttribute.Web =
      MAttribute.Web(
        label = p.label,
        controlType = p.controlType,
        placeholder = p.placeholder,
        help = p.help,
        required = p.required,
        hidden = p.hidden,
        readonly = p.readonly,
        minLength = p.minLength,
        maxLength = p.maxLength,
        min = p.min,
        max = p.max,
        step = p.step,
        pattern = p.pattern
      )

    private def _attribute(p: Column): MAttribute = {
      val designation = Designation.nameLabel(p.name, p.i18nLabel)
      val atype = MDataType(p.datatype)
      val multiplicity = MMultiplicity(p.multiplicity)
      val constraints: List[MConstraint] = p.constraints.map(RConstraint)
      val readonly = false
      val description = Description.empty // p.desc
      MAttribute(designation, atype, multiplicity, constraints, Some(p), readonly, None, description = description)
    }

    private def _resolve_attribute_type(
      pkg: MPackageRef,
      p: SchemaModel.Attribute
    ): MAttributeType =
      if (p.domain.datatype == org.goldenport.record.v2.XString) {
        p.rawTypeName match {
          case Some(raw) =>
            _builtin_attribute_type(pkg, raw).
              orElse(if (_is_string_format_raw_type(raw)) Some(MDataType(p.domain.datatype)) else None).
              orElse(_resolve_object_attribute_type(pkg, raw)).
              getOrElse(RAISE.syntaxErrorFault(s"Unknown CML attribute type: $raw"))
          case None => MDataType(p.domain.datatype)
        }
      } else
        MDataType(p.domain.datatype)

    private def _builtin_attribute_type(
      pkg: MPackageRef,
      p: String
    ): Option[MAttributeType] = {
      val normalized = p.trim.toLowerCase(java.util.Locale.ROOT)
      val datatype: Option[org.goldenport.record.v2.DataType] = normalized match {
        case "entityid" => Some(org.goldenport.record.v2.XEntityId)
        case "year" => Some(org.goldenport.record.v2.XYear)
        case "yearmonth" => Some(org.goldenport.record.v2.XYearMonth)
        case "month" => Some(org.goldenport.record.v2.XMonth)
        case "monthday" => Some(org.goldenport.record.v2.XMonthDay)
        case "day" => Some(org.goldenport.record.v2.XDay)
        case "duration" => Some(org.goldenport.record.v2.XDuration)
        case "date-time" => None
        case _ => org.goldenport.record.v2.DataType.get(normalized)
      }
      datatype.map(MDataType(_)).orElse {
        normalized match {
          case "name" => Some(MObjectAttributeType(MObjectRef.create("org.goldenport.datatype.Name")))
          case "identifier" => Some(MObjectAttributeType(MObjectRef.create("org.goldenport.datatype.Identifier")))
          case "text" => Some(MObjectAttributeType(MObjectRef.create("org.goldenport.datatype.Text")))
          case "token" => Some(MObjectAttributeType(MObjectRef.create("org.goldenport.datatype.Token")))
          case "url" => Some(MObjectAttributeType(MObjectRef.create("java.net.URL")))
          case "uri" => Some(MObjectAttributeType(MObjectRef.create("java.net.URI")))
          case "urn" => Some(MObjectAttributeType(MObjectRef.create("org.goldenport.datatype.Urn")))
          case "blob" => Some(MObjectAttributeType(MObjectRef.create("org.goldenport.bag.BinaryBag")))
          case "clob" => Some(MObjectAttributeType(MObjectRef.create("org.goldenport.bag.TextBag")))
          case "datetime" | "date_time" => Some(MObjectAttributeType(MObjectRef.create("java.time.ZonedDateTime")))
          case "instant" => Some(MObjectAttributeType(MObjectRef.create("java.time.Instant")))
          case "localdate" => Some(MObjectAttributeType(MObjectRef.create("java.time.LocalDate")))
          case "localtime" => Some(MObjectAttributeType(MObjectRef.create("java.time.LocalTime")))
          case "localdatetime" => Some(MObjectAttributeType(MObjectRef.create("java.time.LocalDateTime")))
          case "locale" => Some(MObjectAttributeType(MObjectRef.create("java.util.Locale")))
          case "timezone" => Some(MObjectAttributeType(MObjectRef.create("java.util.TimeZone")))
          case _ => None
        }
      }
    }

    private def _resolve_object_attribute_type(
      pkg: MPackageRef,
      p: String
    ): Option[MObjectAttributeType] = {
      val raw = p.trim
      if (raw.isEmpty)
        None
      else if (!_is_valid_type_name(raw))
        None
      else {
        val simple = raw.split("\\.").last
        _resolve_local_object_attribute_type(pkg, simple).
          orElse(Some(MObjectAttributeType(MObjectRef.create(raw))))
      }
    }

    private def _is_valid_type_name(p: String): Boolean =
      p.split("\\.").forall { segment =>
        segment.nonEmpty &&
          segment.headOption.exists(c => c.isLetter || c == '_') &&
          segment.forall(c => c.isLetterOrDigit || c == '_')
      }

    private def _resolve_local_object_attribute_type(
      pkg: MPackageRef,
      simple: String
    ): Option[MObjectAttributeType] =
      value.classes.get(simple).
        map(_ => MObjectAttributeType(MObjectRef(MPackageRef(_value_package_name()), simple))).
        orElse(datatype.classes.get(simple).map(_ => MObjectAttributeType(MObjectRef(MPackageRef(_datatype_package_name("domain")), simple)))).
        orElse(entity.classes.get(simple).map(_ => MObjectAttributeType(_object_ref(pkg.packageName, simple))))

    private def _is_builtin_raw_type(
      p: String
    ): Boolean = {
      val normalized = p.trim.toLowerCase(java.util.Locale.ROOT)
      _builtin_attribute_type(MPackageRef.default, normalized).isDefined ||
        org.goldenport.record.v2.DataType.get(normalized).isDefined ||
        _is_string_format_raw_type(normalized)
    }

    private def _is_string_format_raw_type(p: String): Boolean = {
      val normalized = p.trim.toLowerCase(java.util.Locale.ROOT)
      Set(
        "email",
        "uuid",
        "phone",
        "tel",
        "e164"
      ).contains(normalized)
    }

    private def _associations(pkg: MPackageRef, p: SchemaClass): List[MAssociation] =
      p.slots.flatMap(_get_association(pkg, _)).toList

    private def _aggregate_member_associations(
      pkg: MPackageRef,
      aggregate: Option[SchemaModel.AggregateDefinition]
    ): List[MAssociation] =
      aggregate.toList.flatMap(_.members).map(_aggregate_member_association(pkg, _))

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

    private def _aggregate_member_association(
      pkg: MPackageRef,
      p: SchemaModel.AggregateMemberDefinition
    ): MAssociation = {
      val designation = Designation(p.name)
      val description = Description.empty
      val objectref = MObjectRef(pkg, p.entity)
      val kind = p.kind.toLowerCase match {
        case "composition" => MAssociation.Composition
        case "aggregation" => MAssociation.Aggregation
        case _ => MAssociation.Association
      }
      val multiplicity = p.multiplicity.map(x => MMultiplicity.create(x.toString)).getOrElse(MOne)
      val collaborations = Nil
      MAssociation(designation, description, Some(pkg), objectref, kind, multiplicity, collaborations)
    }

    private def _datatype(p: DataTypeClass): MDataType = p match {
      case m: DataTypeClass.Plain =>
        val pkg = MPackageRef(_datatype_package_name(m.packageName))
        val desc = m.description
        val datatype = m.datatype
        MDataType(desc.designation, datatype, pkg, desc)
      case m: DataTypeClass.Complex => ???
    }

    private def _value(p: ValueClass): MValue = {
      val desc = Description.name(p.name)
      val pkg = MPackageRef(_value_package_name())
      val stereotypes = Nil
      val base = None
      val traits = Nil
      val powertypes = _powertypes(pkg, p.schemaClass)
      val attributes = _attributes(pkg, p.schemaClass)
      val operations = Nil
      MDomainValue(
        desc,
        pkg,
        stereotypes,
        base,
        traits,
        powertypes,
        attributes,
        operations
      )
    }

    private def _service_inline_values: Vector[ValueClass] =
      service.classes.values.toVector.flatMap { svc =>
        svc.operations.operations.values.toVector.flatMap { op =>
          Vector(op.input.value, op.output.value).flatten
        }
      }

    private def _powertype(p: PowertypeClass): MPowertype = {
      val desc = p.description
      val pkg = MPackageRef(p.packageName)
      val kinds = p.kinds.toList.map { x =>
        MPowertypeKind(x.name, x.value.map(_.toString), x.label)
      }
      val stereotypes = Nil
      MPowertype(desc, pkg, kinds, stereotypes)
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
        val isRootPackage = pkg == sm.root
        if (entities.nonEmpty || (isRootPackage && service.classes.nonEmpty)) {
          val comp = _make_component(pkg, entities)
          Vector(comp)
        } else {
          Vector.empty
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
      val compname = _component_name(pkg)
      val desc = _description(compname, _component_description_for(pkg))
      val componentpackagename = _component_package_name(pkg).getOrElse(pkg.name)
      val servicepkg = _service_package(pkg, componentpackagename)
      val definedservices = _defined_services(servicepkg, entities)
      val entityservice: MService = _make_entity_service(servicepkg, entities)
      val aggregateservice: MService = _make_aggregate_service(servicepkg, entities)
      val viewservice: MService = _make_view_service(servicepkg, entities)
      val core = MObject.Core(
        affiliation = MPackageRef(componentpackagename),
        services = definedservices.toList ++ List(aggregateservice, viewservice, entityservice)
      )
      val transitionrules = _state_machine_transition_rules(entities)
      val statemachinedefs = _state_machine_definitions(entities)
      val eventdefs = _event_reception_definitions(entities)
      val eventroutes = _event_routing_definitions()
      val eventsubs = _event_subscription_definitions()
      val aggregates = _aggregate_definitions(entities)
      val views = _view_definitions(entities)
      val operations = _operation_definitions()
      val components = _component_definitions(pkg)
      val subsystems = _subsystem_definitions(pkg)
      val entitydescs = _entity_runtime_descriptors(entities)
      val ccore = MComponent.Core(
        entities = entities,
        entityRuntimeDescriptors = entitydescs,
        stateMachineTransitionRules = transitionrules,
        stateMachineDefinitions = statemachinedefs,
        eventReceptionDefinitions = eventdefs,
        eventRoutingDefinitions = eventroutes,
        eventSubscriptionDefinitions = eventsubs,
        aggregateDefinitions = aggregates,
        viewDefinitions = views,
        relationshipDefinitions = relationships,
        operationDefinitions = operations,
        componentDefinitions = components,
        subsystemDefinitions = subsystems
      )
      MDomainComponent(desc, core, ccore)
    }

    private def _entity_runtime_descriptors(
      entities: Vector[MEntity]
    ): Vector[MComponent.EntityRuntimeDescriptor] =
      entities.map { entity =>
        MComponent.EntityRuntimeDescriptor(
          entityName = StringUtils.makeTitle(entity.name),
          packageName = _entity_runtime_package_name(entity),
          usageKind = entity.usageKind,
          operationKind = entity.operationKind,
          applicationDomain = entity.applicationDomain,
          viewNames = _view_names(entity)
        )
      }

    private def _entity_runtime_package_name(entity: MEntity): String =
      entity.packageName match {
        case "" => "entity"
        case x if x.endsWith(".entity") => x
        case x => s"${x}.entity"
      }

    private def _service_package(
      pkg: MPackage,
      packageName: String
    ): MPackage = pkg.copy(
      designation = Designation(packageName),
      affiliation = MPackageRef.default
    )

    private def _component_package_name(
      pkg: MPackage
    ): Option[String] =
      _component_definition_for(pkg).flatMap(_.packageName)

    private def _component_name(
      pkg: MPackage
    ): String =
      _component_definition_for(pkg).map(_.name).getOrElse(pkg.name)

    private def _component_definition_for(
      pkg: MPackage
    ): Option[ComponentSubsystemModel.ComponentDefinition] =
      componentSubsystem.components.find(_.name == pkg.name).orElse {
        if (componentSubsystem.components.lengthCompare(1) == 0)
          componentSubsystem.components.headOption
        else
          None
      }

    private def _description(
      name: String,
      text: Option[String]
    ): Description =
      text.map(_.trim).filter(_.nonEmpty).
        map(s => Description.name(name, Dox.text(s))).
        getOrElse(Description.name(name))

    private def _component_description_for(
      pkg: MPackage
    ): Option[String] =
      _component_definition_for(pkg).flatMap { p =>
        _append_use_cases(p.description, p.useCases.map(_component_use_case_text))
      }

    private def _entity_package_name(
      packagename: String
    ): String =
      _component_package_override(packagename).getOrElse(packagename)

    private def _value_package_name(): String =
      _component_package_override("domain").map(_ + ".value").getOrElse("domain.value")

    private def _datatype_package_name(
      packagename: String
    ): String =
      if (packagename == "domain")
        _component_package_override(packagename).map(_ + ".datatype").getOrElse("domain.datatype")
      else
        _component_package_override(packagename).getOrElse(packagename)

    private def _component_package_override(
      componentname: String
    ): Option[String] =
      componentSubsystem.components.find(_.name == componentname).flatMap(_.packageName).orElse {
        if (componentSubsystem.components.lengthCompare(1) == 0)
          componentSubsystem.components.headOption.flatMap(_.packageName)
        else
          None
      }

    private def _defined_services(
      pkg: MPackage,
      entities: Vector[MEntity]
    ): Vector[MService] =
      service.classes.values.toVector.map(_defined_service(pkg, entities, _))

    private def _defined_service(
      pkg: MPackage,
      entities: Vector[MEntity],
      p: ServiceModel.ServiceClass
    ): MService = {
      val ops = p.operations.operations.values.toVector.map(_defined_service_operation(entities, p.name, p, _))
      MService(
        pkg,
        p.name,
        ops,
        _description(p.name, _service_description(p)),
        p.useCases.map(_component_use_case_definition_for_service)
      )
    }

    private def _service_description(
      p: ServiceModel.ServiceClass
    ): Option[String] =
      _append_use_cases(p.description, p.useCases.map(_service_use_case_text))

    private def _append_use_cases(
      base: Option[String],
      usecases: Vector[String]
    ): Option[String] = {
      val xs = usecases.map(_.trim).filter(_.nonEmpty)
      val usecaseText =
        if (xs.isEmpty)
          None
        else
          Some(xs.mkString("Use cases:\n", "\n", ""))
      (base.map(_.trim).filter(_.nonEmpty), usecaseText) match {
        case (Some(a), Some(b)) => Some(s"$a\n\n$b")
        case (Some(a), None) => Some(a)
        case (None, Some(b)) => Some(b)
        case _ => None
      }
    }

    private def _component_use_case_text(
      p: ComponentSubsystemModel.UseCaseDefinition
    ): String = {
      val summary = p.summary.orElse(p.description).getOrElse("")
      val actor = _use_case_actor_text(p.actor, p.primaryActor, p.secondaryActor, p.supportingActor, p.stakeholder)
      val base = if (summary.nonEmpty) s"- ${p.name}: ${summary}" else s"- ${p.name}"
      actor.map(x => s"${base} (${x})").getOrElse(base)
    }

    private def _service_use_case_text(
      p: ServiceModel.ServiceClass.UseCaseDefinition
    ): String = {
      val summary = p.summary.orElse(p.description).getOrElse("")
      val actor = _use_case_actor_text(p.actor, p.primaryActor, p.secondaryActor, p.supportingActor, p.stakeholder)
      val base = if (summary.nonEmpty) s"- ${p.name}: ${summary}" else s"- ${p.name}"
      actor.map(x => s"${base} (${x})").getOrElse(base)
    }

    private def _use_case_actor_text(
      actor: Option[String],
      primaryActor: Option[String],
      secondaryActor: Option[String],
      supportingActor: Option[String],
      stakeholder: Option[String]
    ): Option[String] = {
      val xs = Vector(
        actor.map(x => s"actor=${x.trim}"),
        primaryActor.map(x => s"primary=${x.trim}"),
        secondaryActor.map(x => s"secondary=${x.trim}"),
        supportingActor.map(x => s"supporting=${x.trim}"),
        stakeholder.map(x => s"stakeholder=${x.trim}")
      ).flatten.filter(_.nonEmpty)
      if (xs.isEmpty) None else Some(xs.mkString(", "))
    }

    private lazy val _operation_input_value_map: Map[String, OperationModel.InputValueDefinition] =
      operation.values.map(x => x.name -> x).toMap

    private lazy val _value_input_field_map: Map[String, Vector[OperationModel.FieldDefinition]] =
      (value.classes.values.toVector ++ _service_inline_values).map(x => x.name -> _operation_fields(x)).toMap

    private def _operation_fields(p: ValueClass): Vector[OperationModel.FieldDefinition] =
      p.schemaClass.attributes.map(_operation_field)

    private def _operation_field(p: SchemaModel.Attribute): OperationModel.FieldDefinition =
      OperationModel.FieldDefinition(
        name = p.name,
        datatype = p.rawTypeName.getOrElse(p.domain.datatype.name),
        multiplicity = p.domain.multiplicity.mark,
        label = p.web.label,
        controlType = p.web.controlType,
        placeholder = p.web.placeholder,
        help = p.web.help,
        required = p.web.required
      )

    private lazy val _normalized_operation_map: Map[String, OperationModel.NormalizedOperationDefinition] =
      operation.normalizedOperations.map(_with_input_value_parameters).map(x => x.name -> x).toMap

    private def _with_input_value_parameters(
      p: OperationModel.NormalizedOperationDefinition
    ): OperationModel.NormalizedOperationDefinition =
      if (p.parameters.nonEmpty)
        p
      else
        _operation_input_value_map.get(p.inputType).
          map(v => p.copy(parameters = v.fields)).
          getOrElse(p)

    private def _defined_service_operation(
      entities: Vector[MEntity],
      servicename: String,
      serviceclass: ServiceModel.ServiceClass,
      p: ServiceModel.ServiceClass.Operation
    ): MOperation = {
      val opname = p.name
      val opdef = _normalized_service_operation(p).map(_merge_service_operation_metadata(_, serviceclass, p))
      val desc = _description(
        opname,
        _operation_description(
          p.description.orElse(opdef.flatMap(_.description)),
          p.precondition.orElse(opdef.flatMap(_.precondition)),
          p.postcondition.orElse(opdef.flatMap(_.postcondition)),
          if (p.rules.nonEmpty) p.rules else opdef.map(_.rules).getOrElse(Vector.empty)
        )
      )
      val implementation = opdef.flatMap(_.implementation).map(_.trim.toLowerCase)
      val entity = _entity_for_service(entities, servicename)
      val access = opdef.flatMap(_.access).map(a =>
        MComponent.OperationAccess(
          policy = a.policy,
          resource = a.resource,
          target = a.target,
          mode = a.mode,
          relation = a.relation,
          operationModel = a.operationModel,
          entityUsage = a.entityUsage,
          entityOperationKind = a.entityOperationKind,
          entityApplicationDomain = a.entityApplicationDomain,
          condition = a.condition
        )
      )
      opdef.map(_.kind) match {
        case Some(OperationModel.OperationKind.Query) =>
          _defined_query_service_operation(opname, desc, implementation, entity, access)
        case _ =>
          _defined_command_service_operation(opname, desc, implementation, entity, opdef.flatMap(_.execution), access)
      }
    }

    private def _merge_service_operation_metadata(
      lhs: OperationModel.NormalizedOperationDefinition,
      serviceclass: ServiceModel.ServiceClass,
      rhs: ServiceModel.ServiceClass.Operation
    ): OperationModel.NormalizedOperationDefinition =
      lhs.copy(
        summary = rhs.summary.orElse(lhs.summary),
        entityName = rhs.entityName.orElse(serviceclass.entityName).orElse(lhs.entityName),
        entityNames =
          if (rhs.entityNames.nonEmpty) rhs.entityNames
          else if (serviceclass.entityNames.nonEmpty) serviceclass.entityNames
          else lhs.entityNames,
        description = rhs.description.orElse(lhs.description),
        precondition = rhs.precondition.orElse(lhs.precondition),
        postcondition = rhs.postcondition.orElse(lhs.postcondition),
        execution = rhs.execution.orElse(lhs.execution),
        implementation = rhs.implementation.orElse(lhs.implementation),
        access = rhs.access.orElse(serviceclass.access).orElse(lhs.access),
        authorization = rhs.authorization.orElse(lhs.authorization),
        rules = if (rhs.rules.nonEmpty) rhs.rules else lhs.rules,
        parameters = if (rhs.parameters.nonEmpty) rhs.parameters else lhs.parameters
      )

    private def _normalized_service_operation(
      p: ServiceModel.ServiceClass.Operation
    ): Option[OperationModel.NormalizedOperationDefinition] =
      if (!_has_service_operation_contract(p)) {
        None
      } else {
        val kind = p.kind.getOrElse(
          RAISE.syntaxErrorFault(s"Operation '${p.name}' requires TYPE (COMMAND|QUERY).")
        )
        val inputType = p.input.tpe.map(_.trim).filterNot(_.isEmpty).getOrElse(
          RAISE.syntaxErrorFault(s"Operation '${p.name}' requires INPUT TYPE.")
        )
        val outputType = p.output.tpe.map(_.trim).filterNot(_.isEmpty).getOrElse(
          RAISE.syntaxErrorFault(s"Operation '${p.name}' requires OUTPUT TYPE.")
        )
        val inputValue = _operation_input_value_map.get(inputType)
        val inputValueKind = inputValue.map(_.kind).getOrElse {
          kind match {
            case OperationModel.OperationKind.Command => OperationModel.InputValueKind.CommandValue
            case OperationModel.OperationKind.Query => OperationModel.InputValueKind.QueryValue
          }
        }
        val parameters =
          if (p.parameters.nonEmpty)
            p.parameters
          else
            inputValue.map(_.fields).orElse(_value_input_field_map.get(inputType)).getOrElse(Vector.empty)
        _validate_service_operation_input_kind(p.name, kind, inputValueKind)
        Some(OperationModel.NormalizedOperationDefinition(
          name = p.name,
          kind = kind,
          summary = p.summary,
          execution = p.execution,
          implementation = p.implementation,
          entityName = p.entityName,
          entityNames = p.entityNames,
          inputType = inputType,
          inputSummary = p.input.summary,
          inputDescription = p.input.description,
          outputType = outputType,
          outputSummary = p.output.summary,
          outputDescription = p.output.description,
          inputValueKind = inputValueKind,
          description = p.description,
          precondition = p.precondition,
          postcondition = p.postcondition,
          access = p.access,
          authorization = p.authorization,
          rules = p.rules,
          parameters = parameters
        ))
      }

    private def _has_service_operation_contract(
      p: ServiceModel.ServiceClass.Operation
    ): Boolean =
      p.kind.nonEmpty ||
        p.input.tpe.exists(_.trim.nonEmpty) ||
        p.output.tpe.exists(_.trim.nonEmpty) ||
        p.execution.nonEmpty ||
        p.implementation.nonEmpty ||
        p.entityName.nonEmpty ||
        p.entityNames.nonEmpty ||
        p.access.nonEmpty ||
        p.authorization.nonEmpty ||
        p.parameters.nonEmpty

    private def _validate_service_operation_input_kind(
      opname: String,
      opkind: OperationModel.OperationKind,
      valuekind: OperationModel.InputValueKind
    ): Unit =
      (opkind, valuekind) match {
        case (OperationModel.OperationKind.Command, OperationModel.InputValueKind.QueryValue) =>
          RAISE.syntaxErrorFault(s"Operation '$opname' TYPE=COMMAND cannot use query-value input.")
        case (OperationModel.OperationKind.Query, OperationModel.InputValueKind.CommandValue) =>
          RAISE.syntaxErrorFault(s"Operation '$opname' TYPE=QUERY cannot use command-value input.")
        case _ =>
          ()
      }

    private def _operation_description(
      base: Option[String],
      precondition: Option[String],
      postcondition: Option[String],
      rules: Vector[String]
    ): Option[String] = {
      val normalizedRules = rules.map(_.trim).filter(_.nonEmpty)
      val chunks = Vector(
        base.map(_.trim).filter(_.nonEmpty),
        precondition.map(x => s"Precondition: ${x.trim}").filter(_.nonEmpty),
        postcondition.map(x => s"Postcondition: ${x.trim}").filter(_.nonEmpty),
        if (normalizedRules.nonEmpty) Some(normalizedRules.mkString("Rules:\n- ", "\n- ", "")) else None
      ).flatten
      if (chunks.isEmpty) None else Some(chunks.mkString("\n\n"))
    }

    private def _defined_command_service_operation(
      opname: String,
      desc: Description,
      implementation: Option[String],
      entity: Option[MEntity],
      execution: Option[String],
      access: Option[MComponent.OperationAccess]
    ): MOperation =
      implementation match {
        case Some("echo-record") =>
          _echo_record_command_operation(opname, desc, access)
        case Some("blocking-task") =>
          _blocking_task_command_operation(opname, desc, access)
        case Some("entity-create") =>
          entity.map(_entity_create_command_operation(opname, desc, _)).getOrElse(_not_implemented_command_operation(opname, desc, access))
        case Some("event-emit") | Some("event-effect-record") =>
          MOperation.commandBody(opname, List(MParameter.record), MResult.unit, desc, access) {
            blockFor(
              "_ <- uowmNotImplemented[org.goldenport.cncf.unitofwork.UnitOfWorkOp, Unit]"
            )(
              "OperationResponse.void"
            )
          }
        case _ =>
          if (execution.exists(_.trim.equalsIgnoreCase("sync")))
            _echo_record_command_operation(opname, desc, access)
          else
            _not_implemented_command_operation(opname, desc, access)
      }

    private def _defined_query_service_operation(
      opname: String,
      desc: Description,
      implementation: Option[String],
      entity: Option[MEntity],
      access: Option[MComponent.OperationAccess]
    ): MOperation =
      implementation match {
        case Some("entity-load") =>
          entity.map(_entity_load_query_operation(opname, desc, _)).getOrElse(_not_implemented_query_operation(opname, desc, access))
        case Some("entity-search") =>
          entity.map(_entity_search_query_operation(opname, desc, _)).getOrElse(_not_implemented_query_operation(opname, desc, access))
        case Some("aggregate-load") =>
          entity.map(_aggregate_load_query_operation(opname, desc, _)).getOrElse(_not_implemented_query_operation(opname, desc, access))
        case Some("aggregate-search") =>
          entity.map(_aggregate_search_query_operation(opname, desc, _)).getOrElse(_not_implemented_query_operation(opname, desc, access))
        case Some("view-load") =>
          entity.map(_view_load_query_operation(opname, desc, _)).getOrElse(_not_implemented_query_operation(opname, desc, access))
        case Some("view-search") =>
          entity.map(_view_search_query_operation(opname, desc, _)).getOrElse(_not_implemented_query_operation(opname, desc, access))
        case Some("event-effect-load") =>
          MOperation.queryBody(opname, List(MParameter.record), MResult.unit, desc, access) {
            blockFor(
              "_ <- uowmNotImplemented[org.goldenport.cncf.unitofwork.UnitOfWorkOp, Unit]"
            )(
              "OperationResponse.void"
            )
          }
        case _ =>
          _not_implemented_query_operation(opname, desc, access)
      }

    private def _not_implemented_command_operation(
      opname: String,
      desc: Description,
      access: Option[MComponent.OperationAccess]
    ): MOperation =
      MOperation.commandBody(opname, List(MParameter.record), MResult.unit, desc, access) {
        blockFor(
          "_ <- uowmNotImplemented[org.goldenport.cncf.unitofwork.UnitOfWorkOp, Unit]"
        )(
          "OperationResponse.void"
        )
      }

    private def _not_implemented_query_operation(
      opname: String,
      desc: Description,
      access: Option[MComponent.OperationAccess]
    ): MOperation =
      MOperation.queryBody(opname, List(MParameter.record), MResult.unit, desc, access) {
        blockFor(
          "_ <- uowmNotImplemented[org.goldenport.cncf.unitofwork.UnitOfWorkOp, Unit]"
        )(
          "OperationResponse.void"
        )
      }

    private def _echo_record_command_operation(
      opname: String,
      desc: Description,
      access: Option[MComponent.OperationAccess]
    ): MOperation =
      MOperation.commandBody(opname, List(MParameter.record), MResult.unit, desc, access) {
        blockFor(
          "r <- ConsequenceT.fromConsequence[[X] =>> org.goldenport.cncf.Program[org.goldenport.cncf.unitofwork.UnitOfWorkOp, X], org.goldenport.record.Record](Consequence.success(action.request.toRecord))"
        )(
          "OperationResponse.create(r)"
        )
      }

    private def _blocking_task_command_operation(
      opname: String,
      desc: Description,
      access: Option[MComponent.OperationAccess]
    ): MOperation =
      MOperation.commandBody(opname, List(MParameter.record), MResult.unit, desc, access) {
        blockFor(
          "r <- exec_pure({ Thread.sleep(250L); action.request.toRecord })"
        )(
          "OperationResponse.create(r)"
        )
      }

    private def _entity_for_service(
      entities: Vector[MEntity],
      servicename: String
    ): Option[MEntity] =
      entities.find(_.name == servicename).orElse {
        entities.find(x => StringUtils.makeTitle(x.name) == StringUtils.makeTitle(servicename))
      }

    private def _entity_classes(
      entity: MEntity
    ): (String, String, String, String, String) = {
      val title = StringUtils.makeTitle(entity.name)
      val pkgname = entity.packageName
      def _qualify(s: String) =
        if (pkgname.isEmpty) s else s"$pkgname.$s"
      val wholeclass = _qualify(s"${_entity_package}.$title")
      val createclass = _qualify(s"${_entity_create_package}.$title")
      val queryclass = _qualify(s"${_entity_query_package}.$title")
      val aggregateclass = _qualify(s"${_aggregate_package(_aggregate_name(entity))}.$title")
      val viewclass = _qualify(s"${_view_package(_view_name(entity))}.$title")
      (wholeclass, createclass, queryclass, aggregateclass, viewclass)
    }

    private def _entity_create_command_operation(
      opname: String,
      desc: Description,
      entity: MEntity
    ): MOperation = {
      val (_, createclass, _, _, _) = _entity_classes(entity)
      MOperation.commandBody(opname, List(MParameter.record), MResult.unit, desc) {
        blockFor(
          s"entity <- exec_pure($createclass.create(action.request.toRecord))",
          "r <- entity_create(entity)"
        )(
          "OperationResponse(r.toRecord)"
        )
      }
    }

    private def _entity_load_query_operation(
      opname: String,
      desc: Description,
      entity: MEntity
    ): MOperation = {
      val (wholeclass, _, _, _, _) = _entity_classes(entity)
      MOperation.queryBody(opname, MParameter.record, MResult.unit, desc) {
        blockFor(
          """id <- exec_pure(Consequence.successOrRecordNotFound[org.simplemodeling.model.datatype.EntityId]("id", action.request.toRecord).TAKE)""",
          s"r <- entity_load[$wholeclass](id)"
        )(
          "OperationResponse(r.toRecord())"
        )
      }
    }

    private def _entity_search_query_operation(
      opname: String,
      desc: Description,
      entity: MEntity
    ): MOperation = {
      val (wholeclass, _, queryclass, _, _) = _entity_classes(entity)
      MOperation.queryBody(opname, MParameter.record, MResult.unit, desc) {
        blockFor(
          s"""fields <- exec_pure(org.goldenport.cncf.entity.runtime.EntityQueryFieldResolver(core.component, ${_scala_string_literal(entity.name)}))""",
          s"r <- entity_search[$wholeclass]($queryclass.collectionId, fields.rewrite(Query.fromRecord(action.request.toRecord)))"
        )(
          "OperationResponse.create(r)"
        )
      }
    }

    private def _aggregate_load_query_operation(
      opname: String,
      desc: Description,
      entity: MEntity
    ): MOperation = {
      val (_, _, _, aggregateclass, _) = _entity_classes(entity)
      MOperation.queryBody(opname, MParameter.record, MResult.unit, desc) {
        blockFor(
          """id <- exec_pure(Consequence.successOrRecordNotFound[org.simplemodeling.model.datatype.EntityId]("id", action.request.toRecord).TAKE)""",
          s"r <- aggregate_load_option[$aggregateclass](id)"
        )(
          "OperationResponse.create(r.map(_.toRecord()))"
        )
      }
    }

    private def _aggregate_search_query_operation(
      opname: String,
      desc: Description,
      entity: MEntity
    ): MOperation = {
      val (_, _, queryclass, aggregateclass, _) = _entity_classes(entity)
      MOperation.queryBody(opname, MParameter.record, MResult.unit, desc) {
        blockFor(
          s"r <- aggregate_search[$aggregateclass]($queryclass.collectionId.name, Query.fromRecord(action.request.toRecord))"
        )(
          "OperationResponse.create(r)"
        )
      }
    }

    private def _view_load_query_operation(
      opname: String,
      desc: Description,
      entity: MEntity
    ): MOperation = {
      val (_, _, queryclass, _, viewclass) = _entity_classes(entity)
      MOperation.queryBody(opname, MParameter.record, MResult.unit, desc) {
        blockFor(
          """id <- exec_pure(Consequence.successOrRecordNotFound[org.simplemodeling.model.datatype.EntityId]("id", action.request.toRecord).TAKE)""",
          s"r <- view_load[$viewclass]($queryclass.collectionId.name, id)"
        )(
          "OperationResponse(r.toRecord())"
        )
      }
    }

    private def _view_search_query_operation(
      opname: String,
      desc: Description,
      entity: MEntity
    ): MOperation = {
      val (_, _, queryclass, _, viewclass) = _entity_classes(entity)
      MOperation.queryBody(opname, MParameter.record, MResult.unit, desc) {
        blockFor(
          s"""fields <- exec_pure(org.goldenport.cncf.entity.runtime.EntityQueryFieldResolver(core.component, ${_scala_string_literal(entity.name)}))""",
          s"""r <- action_property_string("view").fold(view_search[$viewclass]($queryclass.collectionId.name, fields.rewrite(Query.fromRecord(action.request.toRecord))))(viewname => view_search[$viewclass]($queryclass.collectionId.name, viewname, fields.rewrite(Query.fromRecord(action.request.toRecord))))"""
        )(
          "OperationResponse.create(r)"
        )
      }
    }

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
        extensionBindings = p.extensionBindings,
        domainVisions = Vector.empty,
        domainCapabilities = Vector.empty,
        domainQualities = Vector.empty,
        domainConstraints = Vector.empty,
        domainUseCases = Vector.empty,
        useCases = p.useCases.map(_component_use_case_definition)
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
        extensionBindings = Map.empty,
        domainVisions = Vector.empty,
        domainCapabilities = Vector.empty,
        domainQualities = Vector.empty,
        domainConstraints = Vector.empty,
        domainUseCases = Vector.empty,
        useCases = Vector.empty
      )
    }

    private def _component_capability_definition(
      p: ComponentSubsystemModel.CapabilityDefinition
    ): MComponent.CapabilityDefinition =
      MComponent.CapabilityDefinition(
        name = p.name,
        summary = p.summary,
        description = p.description,
        actor = p.actor,
        primaryActor = p.primaryActor,
        secondaryActor = p.secondaryActor,
        supportingActor = p.supportingActor,
        stakeholder = p.stakeholder,
        goal = p.goal,
        precondition = p.precondition,
        postcondition = p.postcondition
      )

  private def _component_vision_definition(
    p: ComponentSubsystemModel.VisionDefinition
  ): MComponent.VisionDefinition =
      MComponent.VisionDefinition(
        name = p.name,
        summary = p.summary,
        description = p.description,
        goal = p.goal,
        precondition = p.precondition,
        postcondition = p.postcondition
      )

    private def _component_context_definition(
      p: ComponentSubsystemModel.ContextDefinition
    ): MComponent.ContextDefinition =
      MComponent.ContextDefinition(
        name = p.name,
        summary = p.summary,
        description = p.description
      )

    private def _component_system_context_definition(
      p: ComponentSubsystemModel.SystemContextDefinition
    ): MComponent.SystemContextDefinition =
      MComponent.SystemContextDefinition(
        name = p.name,
        summary = p.summary,
        description = p.description
      )

    private def _component_context_map_definition(
      p: ComponentSubsystemModel.ContextMapDefinition
    ): MComponent.ContextMapDefinition =
      MComponent.ContextMapDefinition(
        name = p.name,
        summary = p.summary,
        description = p.description
      )

    private def _component_quality_definition(
      p: ComponentSubsystemModel.QualityDefinition
    ): MComponent.QualityDefinition =
      MComponent.QualityDefinition(
        name = p.name,
        summary = p.summary,
        description = p.description,
        goal = p.goal,
        precondition = p.precondition,
        postcondition = p.postcondition
      )

    private def _component_constraint_definition(
      p: ComponentSubsystemModel.ConstraintDefinition
    ): MComponent.ConstraintDefinition =
      MComponent.ConstraintDefinition(
        name = p.name,
        summary = p.summary,
        description = p.description,
        goal = p.goal,
        precondition = p.precondition,
        postcondition = p.postcondition
      )

    private def _component_use_case_definition_for_service(
      p: ServiceModel.ServiceClass.UseCaseDefinition
    ): MComponent.UseCaseDefinition =
      MComponent.UseCaseDefinition(
        name = p.name,
        summary = p.summary,
        description = p.description,
        actor = p.actor,
        primaryActor = p.primaryActor,
        secondaryActor = p.secondaryActor,
        supportingActor = p.supportingActor,
        stakeholder = p.stakeholder,
        goal = p.goal,
        precondition = p.precondition,
        postcondition = p.postcondition,
        scenarios = p.scenarios.map { s =>
          MComponent.UseCaseScenario(
            name = s.name,
            summary = s.summary,
            description = s.description,
            steps = s.steps,
            alternates = s.alternates,
            exceptions = s.exceptions
          )
        }
      )

    private def _component_use_case_definition(
      p: ComponentSubsystemModel.UseCaseDefinition
    ): MComponent.UseCaseDefinition =
      MComponent.UseCaseDefinition(
        name = p.name,
        summary = p.summary,
        description = p.description,
        actor = p.actor,
        primaryActor = p.primaryActor,
        secondaryActor = p.secondaryActor,
        supportingActor = p.supportingActor,
        stakeholder = p.stakeholder,
        goal = p.goal,
        precondition = p.precondition,
        postcondition = p.postcondition,
        scenarios = p.scenarios.map { s =>
          MComponent.UseCaseScenario(
            name = s.name,
            summary = s.summary,
            description = s.description,
            steps = s.steps,
            alternates = s.alternates,
            exceptions = s.exceptions
          )
        }
      )

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
      pkg: MPackage
    ): Vector[MComponent.SubsystemDefinition] = {
      val explicit = componentSubsystem.subsystems.sortBy(_.name).map { p =>
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
      val defaultName = _default_subsystem_name(pkg)
      val topLevelRequirements =
        if (_has_top_level_requirement_model)
          Some(
            MComponent.SubsystemDefinition(
              name = defaultName,
              domainVisions = componentSubsystem.visions.map(_component_vision_definition),
              domainContexts = componentSubsystem.contexts.map(_component_context_definition),
              domainSystemContexts = componentSubsystem.systemContexts.map(_component_system_context_definition),
              domainContextMaps = componentSubsystem.contextMaps.map(_component_context_map_definition),
              domainCapabilities = componentSubsystem.capabilities.map(_component_capability_definition),
              domainQualities = componentSubsystem.qualities.map(_component_quality_definition),
              domainConstraints = componentSubsystem.constraints.map(_component_constraint_definition),
              domainUseCases = componentSubsystem.useCases.map(_component_use_case_definition)
            )
          )
        else
          None
      topLevelRequirements.map { req =>
        explicit.indexWhere(_.name == req.name) match {
          case -1 => explicit :+ req
          case i =>
            explicit.updated(
              i,
              explicit(i).copy(
                domainVisions = req.domainVisions,
                domainContexts = req.domainContexts,
                domainSystemContexts = req.domainSystemContexts,
                domainContextMaps = req.domainContextMaps,
                domainCapabilities = req.domainCapabilities,
                domainQualities = req.domainQualities,
                domainConstraints = req.domainConstraints,
                domainUseCases = req.domainUseCases
              )
            )
        }
      }.getOrElse(explicit)
    }

    private def _default_subsystem_name(
      pkg: MPackage
    ): String =
      pkg.name

    private def _has_top_level_requirement_model: Boolean =
      componentSubsystem.visions.nonEmpty ||
        componentSubsystem.contexts.nonEmpty ||
        componentSubsystem.systemContexts.nonEmpty ||
        componentSubsystem.contextMaps.nonEmpty ||
        componentSubsystem.capabilities.nonEmpty ||
        componentSubsystem.qualities.nonEmpty ||
        componentSubsystem.constraints.nonEmpty ||
        componentSubsystem.useCases.nonEmpty

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
        val aggregate = this.entity.classes.get(entity.name).flatMap(_.schemaClass.aggregate)
        val members = _aggregate_member_definitions(entity, entities, aggregate)
        MComponent.AggregateDefinition(
          name = name,
          entityName = entityname,
          members = members,
          creates = aggregate.toVector.flatMap(_.creates).map { c =>
            MComponent.AggregateCreateDefinition(
              name = c.name,
              input = c.input,
              validations = c.validations,
              events = c.events,
              initialState = c.initialState,
              implementation = c.implementation
            )
          },
          commands = aggregate.toVector.flatMap(_.commands).map { c =>
            MComponent.AggregateCommandDefinition(
              name = c.name,
              input = c.input,
              validations = c.validations,
              events = c.events,
              newState = c.newState,
              implementation = c.implementation
            )
          },
          state = aggregate.toVector.flatMap(_.state).map { s =>
            MComponent.AggregateStateDefinition(
              name = s.name,
              datatype = s.datatype,
              multiplicity = s.multiplicity
            )
          },
          invariants = aggregate.toVector.flatMap(_.invariants).map { i =>
            MComponent.AggregateInvariantDefinition(
              name = i.name,
              expression = i.expression
            )
          }
        )
      }

    private def _aggregate_member_definitions(
      root: MEntity,
      entities: Vector[MEntity],
      aggregate: Option[SchemaModel.AggregateDefinition]
    ): Vector[MComponent.AggregateMemberDefinition] = {
      val explicit = aggregate.toVector.flatMap(_.members).flatMap { member =>
        val entityname = _package_token(member.entity)
        if (entityname.nonEmpty)
          Some(MComponent.AggregateMemberDefinition(
            name = _package_token(member.name),
            entityName = entityname,
            kind = Some(member.kind),
            boundary = Some(member.boundary),
            join = member.join,
            joinFieldName = member.joinField,
            multiplicity = member.multiplicity
          ))
        else
          None
      }
      if (explicit.nonEmpty)
        explicit
      else {
        val joinFieldName = s"${root.name.head.toLower}${root.name.drop(1)}Id"
        entities.filterNot(_ == root).flatMap { entity =>
          entity.attributes.find(_.name.equalsIgnoreCase(joinFieldName)).map { attr =>
            MComponent.AggregateMemberDefinition(
              name = _package_token(entity.name),
              entityName = _package_token(entity.name),
              kind = Some("composition"),
              boundary = Some("internal"),
              join = Some("reverse"),
              joinFieldName = Some(attr.name),
              multiplicity = Some("*")
            )
          }
        }
      }
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
          entityName = entityname,
          viewNames = _view_names(entity),
          viewFields = _view_fields(entity),
          queries = _view_queries(entity),
          sourceEvents = _view_source_events(entity),
          rebuildable = _view_rebuildable(entity)
        )
      }

    private def _operation_definitions(
    ): Vector[MComponent.OperationDefinition] =
      _normalized_service_operation_definitions.
        sortBy(_.name).
        map { x =>
        MComponent.OperationDefinition(
          name = x.name,
          kind = x.kind.toString.toUpperCase,
          summary = x.summary,
          execution = x.execution,
          implementation = x.implementation,
          entityName = x.entityName,
          entityNames = x.entityNames,
          inputType = x.inputType,
          inputSummary = x.inputSummary,
          inputDescription = x.inputDescription,
          outputType = x.outputType,
          outputSummary = x.outputSummary,
          outputDescription = x.outputDescription,
          inputValueKind = x.inputValueKind match {
            case OperationModel.InputValueKind.CommandValue => "COMMAND_VALUE"
            case OperationModel.InputValueKind.QueryValue => "QUERY_VALUE"
          },
          access = x.access.map(a =>
            MComponent.OperationAccess(
              policy = a.policy,
              resource = a.resource,
              target = a.target,
              mode = a.mode,
              relation = a.relation,
              operationModel = a.operationModel,
              entityUsage = a.entityUsage,
              entityOperationKind = a.entityOperationKind,
              entityApplicationDomain = a.entityApplicationDomain,
              condition = a.condition
            )
          ),
          operationAuthorization = x.authorization.map(a =>
            MComponent.OperationAuthorization(
              operationModes = a.operationModes,
              allowAnonymous = a.allowAnonymous,
              anonymousOperationModes = a.anonymousOperationModes
            )
          ),
          childEntityBindings = operationRelationshipBindings.get(x.name).map(_.childEntityBindings).getOrElse(Vector.empty),
          associationBinding = operationRelationshipBindings.get(x.name).flatMap(_.associationBinding),
          parameters = x.parameters.map { p =>
            MComponent.OperationField(
              name = p.name,
              datatype = p.datatype,
              multiplicity = p.multiplicity,
              label = p.label.orElse(Some(_humanize_field_name(p.name))),
              controlType = p.controlType.orElse(_operation_control_type(p.name, p.datatype)),
              placeholder = p.placeholder,
              help = p.help,
              required = p.required
            )
          }
        )
      }

    private def _normalized_service_operation_definitions: Vector[OperationModel.NormalizedOperationDefinition] =
      service.classes.values.toVector.
        flatMap { svc =>
          svc.operations.operations.values.toVector.flatMap { p =>
          _normalized_service_operation(p).
            map(_merge_service_operation_metadata(_, svc, p))
          }
        }

    private def _merge_normalized_operation_definition(
      lhs: OperationModel.NormalizedOperationDefinition,
      rhs: OperationModel.NormalizedOperationDefinition
    ): OperationModel.NormalizedOperationDefinition =
      lhs.copy(
        summary = rhs.summary.orElse(lhs.summary),
        execution = rhs.execution.orElse(lhs.execution),
        implementation = rhs.implementation.orElse(lhs.implementation),
        entityName = rhs.entityName.orElse(lhs.entityName),
        entityNames = if (rhs.entityNames.nonEmpty) rhs.entityNames else lhs.entityNames,
        inputType = Option(rhs.inputType).filterNot(_.isEmpty).getOrElse(lhs.inputType),
        inputSummary = rhs.inputSummary.orElse(lhs.inputSummary),
        inputDescription = rhs.inputDescription.orElse(lhs.inputDescription),
        outputType = Option(rhs.outputType).filterNot(_.isEmpty).getOrElse(lhs.outputType),
        outputSummary = rhs.outputSummary.orElse(lhs.outputSummary),
        outputDescription = rhs.outputDescription.orElse(lhs.outputDescription),
        description = rhs.description.orElse(lhs.description),
        precondition = rhs.precondition.orElse(lhs.precondition),
        postcondition = rhs.postcondition.orElse(lhs.postcondition),
        access = rhs.access.orElse(lhs.access),
        authorization = rhs.authorization.orElse(lhs.authorization),
        rules = if (rhs.rules.nonEmpty) rhs.rules else lhs.rules,
        parameters = if (rhs.parameters.nonEmpty) rhs.parameters else lhs.parameters
      )

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

    // NOTE: View DSL package scoping is not available yet. Keep default package for now.
    // Named views are exposed through generated viewDefinitions.viewNames.
    private def _view_name(entity: MEntity): Option[String] = None

    private def _view_names(mentity: MEntity): Vector[String] = {
      val source = _source_entity_class(mentity)
      val declared = source.flatMap(_.view).toVector.flatMap(_.viewNames).map(_package_token(_)).filterNot(_.isEmpty)
      val standard =
        if (source.exists(_inherits_simple_entity))
          Vector("summary", "detail")
        else
          Vector.empty
      (standard ++ declared).distinct
    }

    private def _humanize_field_name(
      name: String
    ): String = {
      val spaced = name.replace('_', ' ').replace('-', ' ').
        replaceAll("([a-z0-9])([A-Z])", "$1 $2").
        trim
      if (spaced.isEmpty)
        name
      else
        spaced.split("\\s+").map(_.capitalize).mkString(" ")
    }

    private def _operation_control_type(
      name: String,
      datatype: String
    ): Option[String] = {
      val n = name.toLowerCase(java.util.Locale.ROOT)
      val t = Option(datatype).map(_.trim.toLowerCase(java.util.Locale.ROOT)).getOrElse("")
      if (t == "text" || Vector("body", "content", "description", "comment", "message").exists(n.contains))
        Some("textarea")
      else
        None
    }

    private def _view_fields(mentity: MEntity): Map[String, Vector[String]] = {
      val source = _source_entity_class(mentity)
      val names =
        if (source.exists(_inherits_simple_entity))
          (_view_names(mentity) :+ "create").distinct
        else
          _view_names(mentity)
      val fields = _entity_value_display_fields(mentity)
      if (names.isEmpty || fields.isEmpty)
        Map.empty
      else
        names.map(name => name -> _entity_value_display_fields(name, fields)).toMap
    }

    private def _entity_value_display_fields(
      view: String,
      fields: Vector[String]
    ): Vector[String] =
      view match {
        case "create" => fields.filterNot(_ == "id")
        case _ => fields
      }

    private def _entity_value_display_fields(
      mentity: MEntity
    ): Vector[String] = {
      val attrs = mentity.attributes.toVector.map(_.name).filterNot(_.isEmpty)
      if (attrs.contains("id"))
        attrs
      else
        "id" +: attrs
    }

    private def _inherits_simple_entity(p: EntityClass): Boolean = {
      val byParentRef = p.parents.exists {
        case m: EntityClass.ParentRef.Name => _is_simple_entity(m.name)
        case EntityClass.ParentRef.EntityKlass(c) => _is_simple_entity(c.name)
      }
      val byFeatures = p.schemaClass.features.parentsName.exists(_is_simple_entity)
      byParentRef || byFeatures
    }

    private def _view_queries(mentity: MEntity): Vector[MComponent.ViewQueryDefinition] =
      _source_entity_class(mentity).flatMap(_.view).toVector.flatMap(_.queries).map { q =>
        MComponent.ViewQueryDefinition(
          name = _package_token(q.name),
          expression = q.expression
        )
      }

    private def _view_source_events(mentity: MEntity): Vector[String] =
      _source_entity_class(mentity).flatMap(_.view).toVector.flatMap(_.sourceEvents).map(_package_token(_)).filterNot(_.isEmpty).distinct

    private def _view_rebuildable(mentity: MEntity): Option[Boolean] =
      _source_entity_class(mentity).flatMap(_.view).flatMap(_.rebuildable)

    private def _source_entity_class(mentity: MEntity): Option[EntityClass] =
      entity.classes.values.find { c =>
        c.name == mentity.name || _package_token(c.name) == _package_token(mentity.name)
      }

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

    private def _scala_string_literal(p: String): String =
      "\"" + p.flatMap {
        case '\\' => "\\\\"
        case '"' => "\\\""
        case '\n' => "\\n"
        case '\r' => "\\r"
        case '\t' => "\\t"
        case c => c.toString
      } + "\""

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
            events = _state_machine_events(sm)
          )
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
      val createRecordParam = MParameter("record", MEntityValue.create(entity))
      val loadresult = MResult.option(MEntityValue.whole(entity))
      val searchresult = MResult.search(MEntityValue.whole(entity))
      val create = MOperation.commandBody(s"create$title", entityparam) {
        blockFor(
          "r <- entity_create(action.entity)"
        )(
          "OperationResponse(r.toRecord)"
        )
      }
      val createrec = MOperation.commandBody(s"create${title}Record", createRecordParam) {
        blockFor(
          "r <- entity_create(action.record)"
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
          s"""fields <- exec_pure(org.goldenport.cncf.entity.runtime.EntityQueryFieldResolver(core.component, ${_scala_string_literal(entity.name)}))""",
          s"r <- entity_search[$wholeclass]($queryclass.collectionId, fields.rewrite(Query.withControls(Query(action.q), action.request.toRecord)))"
        )(
          "OperationResponse.create(r)"
        )
      }
      val searchrec = MOperation.queryBody(s"search${title}Record", queryrecparam, searchresult) {
        blockFor(
          s"""fields <- exec_pure(org.goldenport.cncf.entity.runtime.EntityQueryFieldResolver(core.component, ${_scala_string_literal(entity.name)}))""",
          s"r <- entity_search[$wholeclass]($queryclass.collectionId, fields.rewrite(Query.withControls(action.q, action.request.toRecord)))"
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
      val wholeclass = _qualify(s"${_entity_package}.$title")
      val queryclass = _qualify(s"${_entity_query_package}.$title")
      val entityname = _package_token(entity.name)
      val createparam = MParameter("entity", MEntityValue.aggregate(entity))
      val saveparam = MParameter("entity", MEntityValue.aggregate(entity))
      val updateparam = MParameter("entity", MEntityValue.aggregate(entity))
      val searchparam = MParameter.query("q", MEntityValue.query(entity))
      val idparam = MParameter.entityId
      val loadresult = MResult.option(MEntityValue.aggregate(entity))
      val searchresult = MResult.search(MEntityValue.aggregate(entity))
      val aggregate = this.entity.classes.get(entity.name).flatMap(_.schemaClass.aggregate)
      val createMethod = s"create$title"
      val updateMethod = s"update$title"
      val hasCreateMethod = aggregate.exists(_.creates.exists(_.name == createMethod))
      val hasUpdateMethod = aggregate.exists(_.commands.exists(_.name == updateMethod))
      val create = MOperation.commandBody(s"create$title", createparam) {
        if (hasCreateMethod)
          blockFor(
            s"r <- aggregate_create(${_scala_string_literal(entityname)}, ${_scala_string_literal(createMethod)}, $aggregateclass.$createMethod(action.entity.toRecord())(using executionContext))"
          )(
            "OperationResponse.create(r.toRecord())"
          )
        else
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
        if (hasUpdateMethod)
          blockFor(
            s"current <- aggregate_load[$aggregateclass](action.entity.id)",
            s"r <- aggregate_update(${_scala_string_literal(entityname)}, action.entity.id, ${_scala_string_literal(updateMethod)}, current.$updateMethod(action.entity.toRecord())(using executionContext))"
          )(
            "OperationResponse.create(r.toRecord())"
          )
        else
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
          s"r <- aggregate_search[$aggregateclass]($queryclass.collectionId.name, Query.withControls(Query(action.q), action.request.toRecord))"
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
          "OperationResponse(r.toViewRecord(using core.executionContext))"
        )
      }
      val loadbyview = MOperation.queryBody(s"load${title}ByView", idparam, loadresult) {
        blockFor(
          s"""r <- view_load[$viewclass]($queryclass.collectionId.name, action_required_property_string("view").TAKE, action.id)"""
        )(
          "OperationResponse(r.toViewRecord(using core.executionContext))"
        )
      }
      val search = MOperation.queryBody(s"search$title", List(searchparam, viewparam), searchresult) {
        blockFor(
          s"""fields <- exec_pure(org.goldenport.cncf.entity.runtime.EntityQueryFieldResolver(core.component, ${_scala_string_literal(entity.name)}))""",
          s"r <- action.view.fold(view_search[$viewclass]($queryclass.collectionId.name, fields.rewrite(Query.withControls(Query(action.q), action.request.toRecord))))(viewname => view_search[$viewclass]($queryclass.collectionId.name, viewname, fields.rewrite(Query.withControls(Query(action.q), action.request.toRecord))))"
        )(
          "OperationResponse.create(org.goldenport.cncf.directive.SearchResult(query = r.query, data = r.data.map(_.toViewRecord(using core.executionContext)), totalCount = r.totalCount, offset = r.offset, limit = r.limit, fetchedCount = r.fetchedCount))"
        )
      }
      val searchrec = MOperation.queryBody(s"search${title}Record", searchrecparam, searchresult) {
        blockFor(
          s"""fields <- exec_pure(org.goldenport.cncf.entity.runtime.EntityQueryFieldResolver(core.component, ${_scala_string_literal(entity.name)}))""",
          s"""r <- action_property_string("view").fold(view_search[$viewclass]($queryclass.collectionId.name, fields.rewrite(Query.withControls(action.q, action.request.toRecord))))(viewname => view_search[$viewclass]($queryclass.collectionId.name, viewname, fields.rewrite(Query.withControls(action.q, action.request.toRecord))))"""
        )(
          "OperationResponse.create(org.goldenport.cncf.directive.SearchResult(query = r.query, data = r.data.map(_.toViewRecord(using core.executionContext)), totalCount = r.totalCount, offset = r.offset, limit = r.limit, fetchedCount = r.fetchedCount))"
        )
      }
      val named = _view_names(entity).flatMap { viewname =>
        _token_opt(viewname).toVector.flatMap { token =>
          val projectionTitle = StringUtils.makeTitle(token)
          val projectionValue = MEntityValue.projection(entity, Some(viewname))
          val projectionClass = _qualify(s"${_view_package(Some(viewname))}.$title")
          val projectionLoadResult = MResult.option(projectionValue)
          val projectionSearchResult = MResult.search(projectionValue)
          val loadProjection = MOperation.queryBody(s"load${title}${projectionTitle}", idparam, projectionLoadResult) {
            blockFor(
              s"""r <- view_load[$projectionClass]($queryclass.collectionId.name, "${viewname}", action.id)"""
            )(
              "OperationResponse(r.toViewRecord(using core.executionContext))"
            )
          }
          val searchProjection = MOperation.queryBody(s"search${title}${projectionTitle}", searchrecparam, projectionSearchResult) {
            blockFor(
              s"""fields <- exec_pure(org.goldenport.cncf.entity.runtime.EntityQueryFieldResolver(core.component, ${_scala_string_literal(entity.name)}))""",
              s"""r <- view_search[$projectionClass]($queryclass.collectionId.name, "${viewname}", fields.rewrite(Query.withControls(action.q, action.request.toRecord)))"""
            )(
              "OperationResponse.create(org.goldenport.cncf.directive.SearchResult(query = r.query, data = r.data.map(_.toViewRecord(using core.executionContext)), totalCount = r.totalCount, offset = r.offset, limit = r.limit, fetchedCount = r.fetchedCount))"
            )
          }
          val searchProjectionRecord = MOperation.queryBody(s"search${title}${projectionTitle}Record", searchrecparam, projectionSearchResult) {
            blockFor(
              s"""fields <- exec_pure(org.goldenport.cncf.entity.runtime.EntityQueryFieldResolver(core.component, ${_scala_string_literal(entity.name)}))""",
              s"""r <- view_search[$projectionClass]($queryclass.collectionId.name, "${viewname}", fields.rewrite(Query.withControls(action.q, action.request.toRecord)))"""
            )(
              "OperationResponse.create(org.goldenport.cncf.directive.SearchResult(query = r.query, data = r.data.map(_.toViewRecord(using core.executionContext)), totalCount = r.totalCount, offset = r.offset, limit = r.limit, fetchedCount = r.fetchedCount))"
            )
          }
          Vector(
            loadProjection,
            searchProjection,
            searchProjectionRecord
          )
        }
      }
      Vector(
        load,
        loadbyview,
        search,
        searchrec
      ) ++ named
    }
  }
  object ModelBuilder {
    def apply(p: KaleidoxModel): ModelBuilder = {
      val relationships = RelationshipCml.relationshipDefinitions(p)
      val operationbindings = RelationshipCml.operationBindings(p, relationships)
      apply(
        p.takeSchemaModel,
        p.takeEntityModel,
        p.takeDataTypeModel,
        p.getValueModel.getOrElse(ValueModel.empty),
        p.takePowertypeModel,
        p.takeStateMachineModel,
        p.takeComponentSubsystemModel,
        p.getServiceModel.getOrElse(ServiceModel.empty),
        p.eventModel,
        p.takeOperationModel,
        relationships,
        operationbindings
      )
    }
  }
}
