package cozy.modeler

import CmlSemanticFoundation._
import CmlDynamicMetadata._

/*
 * @since   Oct.  4, 2026
 * @version Oct.  4, 2026
 * @author  ASAMI, Tomoharu
 */
private[cozy] object CmlDynamicMetadataValidation {
  private val _affected_kinds = Set("entity", "value", "aggregate", "trait", "powertype", "powertype-kind")
  private val _participant_kinds = Set("actor", "entity", "value", "aggregate", "component", "service", "external-system")
  private val _effect_kinds = Set("state", "transition", "state-machine")
  private val _expression_kinds = Set("operation", "command", "event", "rule", "action", "workflow",
    "workflow-activity", "state-machine", "state", "transition")

  def validate(
    catalog: Catalog,
    workflows: Vector[WorkflowProjection],
    activities: Vector[ActivityProjection],
    flows: Vector[FlowProjection],
    stateMachines: Vector[StateMachineProjection],
    states: Vector[StateProjection],
    transitions: Vector[TransitionProjection],
    causalLinks: Vector[CausalLinkProjection]
  ): Either[Vector[DynamicDiagnostic], Unit] = {
    val diagnostics = if (catalog == null) Vector(_invalid_shape("catalog")) else {
      _projections(catalog, workflows, "workflows", "workflow", (value: WorkflowProjection) => value.element,
        (value: WorkflowProjection, path: String) => _workflow(catalog, value, path)) ++
      _projections(catalog, activities, "activities", "workflow-activity", (value: ActivityProjection) => value.element,
        (value: ActivityProjection, path: String) => _activity(catalog, value, path)) ++
      _projections(catalog, flows, "flows", "workflow-flow", (value: FlowProjection) => value.element,
        (value: FlowProjection, path: String) => _flow(catalog, value, path)) ++
      _projections(catalog, stateMachines, "stateMachines", "state-machine", (value: StateMachineProjection) => value.element,
        (value: StateMachineProjection, path: String) => _state_machine(catalog, value, path)) ++
      _projections(catalog, states, "states", "state", (value: StateProjection) => value.element,
        (value: StateProjection, path: String) => _state(catalog, value, path)) ++
      _projections(catalog, transitions, "transitions", "transition", (value: TransitionProjection) => value.element,
        (value: TransitionProjection, path: String) => _transition(catalog, value, path)) ++
      _projections(catalog, causalLinks, "causalLinks", "causal-link", (value: CausalLinkProjection) => value.element,
        (value: CausalLinkProjection, path: String) => _causal_link(catalog, value, path))
    }
    if (diagnostics.nonEmpty) Left(diagnostics) else Right(())
  }

  private def _workflow(catalog: Catalog, value: WorkflowProjection, path: String): Vector[DynamicDiagnostic] = {
    _presence(value.purpose, path + ".purpose", _literal) ++
      _references(catalog, value.activities, path + ".activities", Set("workflow-activity")) ++
      _references(catalog, value.flows, path + ".flows", Set("workflow-flow")) ++
      _references(catalog, value.participants, path + ".participants", _participant_kinds) ++
      _references(catalog, value.affectedElements, path + ".affectedElements", _affected_kinds) ++
      _references(catalog, value.stateMachines, path + ".stateMachines", Set("state-machine")) ++
      _references(catalog, value.operations, path + ".operations", Set("operation", "command")) ++
      _references(catalog, value.events, path + ".events", Set("event")) ++
      _references(catalog, value.rules, path + ".rules", Set("rule")) ++
      _references(catalog, value.stateEffects, path + ".stateEffects", _effect_kinds)
  }

  private def _activity(catalog: Catalog, value: ActivityProjection, path: String): Vector[DynamicDiagnostic] = {
    _reference_presence(catalog, value.workflow, path + ".workflow", Set("workflow")) ++
      _presence(value.activityKind, path + ".activityKind", _activity_kind) ++
      _references(catalog, value.participants, path + ".participants", _participant_kinds) ++
      _references(catalog, value.affectedElements, path + ".affectedElements", _affected_kinds) ++
      _references(catalog, value.operations, path + ".operations", Set("operation", "command")) ++
      _references(catalog, value.events, path + ".events", Set("event")) ++
      _references(catalog, value.rules, path + ".rules", Set("rule")) ++
      _references(catalog, value.stateEffects, path + ".stateEffects", _effect_kinds)
  }

  private def _flow(catalog: Catalog, value: FlowProjection, path: String): Vector[DynamicDiagnostic] = {
    _reference_presence(catalog, value.workflow, path + ".workflow", Set("workflow")) ++
      _reference_presence(catalog, value.source, path + ".source", Set("workflow-activity")) ++
      _reference_presence(catalog, value.target, path + ".target", Set("workflow-activity")) ++
      _presence(value.guard, path + ".guard", (expression: DeclaredExpression, coordinate: String) =>
        _expression(catalog, expression, coordinate))
  }

  private def _state_machine(catalog: Catalog, value: StateMachineProjection, path: String): Vector[DynamicDiagnostic] = {
    _reference_presence(catalog, value.owner, path + ".owner", Set("entity", "value", "aggregate")) ++
      _references(catalog, value.affectedElements, path + ".affectedElements", _affected_kinds) ++
      _references(catalog, value.states, path + ".states", Set("state")) ++
      _references(catalog, value.transitions, path + ".transitions", Set("transition")) ++
      _references(catalog, value.workflows, path + ".workflows", Set("workflow"))
  }

  private def _state(catalog: Catalog, value: StateProjection, path: String): Vector[DynamicDiagnostic] = {
    _reference_presence(catalog, value.stateMachine, path + ".stateMachine", Set("state-machine")) ++
      _expressions(catalog, value.entryActions, path + ".entryActions") ++
      _expressions(catalog, value.exitActions, path + ".exitActions")
  }

  private def _transition(catalog: Catalog, value: TransitionProjection, path: String): Vector[DynamicDiagnostic] = {
    _reference_presence(catalog, value.stateMachine, path + ".stateMachine", Set("state-machine")) ++
      _reference_presence(catalog, value.fromState, path + ".fromState", Set("state")) ++
      _reference_presence(catalog, value.toState, path + ".toState", Set("state")) ++
      _expressions(catalog, value.triggers, path + ".triggers") ++
      _presence(value.guard, path + ".guard", (expression: DeclaredExpression, coordinate: String) =>
        _expression(catalog, expression, coordinate)) ++
      _expressions(catalog, value.actions, path + ".actions") ++
      _references(catalog, value.activities, path + ".activities", Set("workflow-activity")) ++
      _references(catalog, value.workflows, path + ".workflows", Set("workflow")) ++
      _references(catalog, value.affectedElements, path + ".affectedElements", _affected_kinds) ++
      _references(catalog, value.stateEffects, path + ".stateEffects", _effect_kinds)
  }

  private def _causal_link(catalog: Catalog, value: CausalLinkProjection, path: String): Vector[DynamicDiagnostic] = {
    (if (value.kind == Cause || value.kind == Reaction) Vector.empty else Vector(_invalid_shape(path + ".kind"))) ++
      _reference_presence(catalog, value.source, path + ".source", _expression_kinds) ++
      _reference_presence(catalog, value.target, path + ".target", _expression_kinds) ++
      _reference_presence(catalog, value.rule, path + ".rule", Set("rule")) ++
      _references(catalog, value.affectedElements, path + ".affectedElements", _affected_kinds)
  }

  private def _projections[A](
    catalog: Catalog,
    values: Vector[A],
    path: String,
    kind: String,
    record: A => ElementRecord,
    payload: (A, String) => Vector[DynamicDiagnostic]
  ): Vector[DynamicDiagnostic] = {
    if (values == null) Vector(_invalid_shape(path)) else {
      val bindings = values.zipWithIndex.flatMap { case (value, index) =>
        val itempath = s"$path[$index]"
        if (value == null) Vector(_invalid_shape(itempath)) else {
          _core_binding(catalog, record(value), kind, itempath + ".element") ++ payload(value, itempath)
        }
      }
      val admitted = catalog.elements.filter(_.kind == kind)
      val projected = values.filter(_ != null).map(record)
      val corecounts = admitted.groupBy(identity).map { case (value, occurrences) => value -> occurrences.size }
      val projectioncounts = projected.groupBy(identity).map { case (value, occurrences) => value -> occurrences.size }
      val missing = admitted.distinct.zipWithIndex.flatMap { case (value, index) =>
        if (projectioncounts.getOrElse(value, 0) < corecounts(value)) Vector(_diagnostic(
          MissingProjection, s"$path[$index]", "An admitted Dynamic record lacks its required projection occurrence."
        )) else Vector.empty
      }
      val duplicate = projected.distinct.zipWithIndex.flatMap { case (value, index) =>
        if (corecounts.contains(value) && projectioncounts(value) > corecounts(value)) Vector(_diagnostic(
          DuplicateProjection, s"$path[$index]", "A Dynamic record has excess projection occurrences."
        )) else Vector.empty
      }
      bindings ++ missing ++ duplicate
    }
  }

  private def _core_binding(catalog: Catalog, record: ElementRecord, kind: String, path: String): Vector[DynamicDiagnostic] = {
    if (record == null) Vector(_invalid_shape(path))
    else if (record.kind != kind || !catalog.elements.contains(record)) Vector(_invalid_core(path))
    else record.identity match {
      case Present(identity: ModelElementId) => catalog.element(identity) match {
        case Right(admitted) if admitted == record => Vector.empty
        case _ => Vector(_invalid_core(path))
      }
      case _: Absent => Vector.empty
      case _ => Vector(_invalid_core(path))
    }
  }

  private def _presence[A](value: Presence[A], path: String, payload: (A, String) => Vector[DynamicDiagnostic]): Vector[DynamicDiagnostic] = {
    value match {
      case Present(present) => payload(present, path + ".value")
      case absence: Absent => _absence(absence, path)
      case _ => Vector(_invalid_shape(path))
    }
  }

  private def _references(catalog: Catalog, value: Presence[Vector[ModelReference]], path: String, kinds: Set[String]): Vector[DynamicDiagnostic] = {
    _presence(value, path, (references: Vector[ModelReference], coordinate: String) => {
      if (references == null) Vector(_invalid_shape(coordinate)) else {
        references.zipWithIndex.flatMap { case (reference, index) =>
          _reference(catalog, reference, s"$coordinate[$index]", kinds)
        }
      }
    })
  }

  private def _reference_presence(catalog: Catalog, value: Presence[ModelReference], path: String, kinds: Set[String]): Vector[DynamicDiagnostic] = {
    _presence(value, path, (reference: ModelReference, coordinate: String) => _reference(catalog, reference, coordinate, kinds))
  }

  private def _reference(catalog: Catalog, value: ModelReference, path: String, kinds: Set[String]): Vector[DynamicDiagnostic] = {
    if (value == null || value.identity == null) Vector(_invalid_shape(path))
    else if (!_valid_text(value.identity.modelId) || !_valid_text(value.identity.elementId)) Vector(_invalid_semantics(path + ".identity"))
    else value.boundary match {
      case External => Vector.empty
      case Local => catalog.element(value.identity) match {
        case Right(record) if kinds(record.kind) => Vector.empty
        case _ => Vector(_invalid_semantics(path))
      }
      case _ => Vector(_invalid_shape(path + ".boundary"))
    }
  }

  private def _expressions(catalog: Catalog, value: Presence[Vector[DeclaredExpression]], path: String): Vector[DynamicDiagnostic] = {
    _presence(value, path, (expressions: Vector[DeclaredExpression], coordinate: String) => {
      if (expressions == null) Vector(_invalid_shape(coordinate)) else {
        expressions.zipWithIndex.flatMap { case (expression, index) => _expression(catalog, expression, s"$coordinate[$index]") }
      }
    })
  }

  private def _expression(catalog: Catalog, value: DeclaredExpression, path: String): Vector[DynamicDiagnostic] = {
    if (value == null) Vector(_invalid_shape(path)) else {
      _literal(value.text, path + ".text") ++ _references(catalog, value.references, path + ".references", _expression_kinds)
    }
  }

  private def _literal(value: String, path: String): Vector[DynamicDiagnostic] = {
    if (value == null) Vector(_invalid_shape(path)) else Vector.empty
  }

  private def _activity_kind(value: ActivityKind, path: String): Vector[DynamicDiagnostic] = {
    if (value == Action || value == Branch || value == Merge || value == Fork || value == Join || value == Start || value == End) {
      Vector.empty
    } else Vector(_invalid_shape(path))
  }

  private def _absence(value: Absent, path: String): Vector[DynamicDiagnostic] = {
    if (value.reason != NotDeclared && value.reason != NotRepresented && value.reason != Unsupported && value.reason != NotApplicable) {
      Vector(_invalid_shape(path + ".reason"))
    } else if (!_valid_text(value.detail)) Vector(_invalid_semantics(path + ".detail")) else Vector.empty
  }

  private def _valid_text(value: String): Boolean = {
    value != null && value.nonEmpty && !_whitespace(value.codePointAt(0)) &&
      !_whitespace(value.codePointBefore(value.length)) && !_contains_control(value)
  }

  private def _whitespace(codepoint: Int): Boolean = {
    Character.isWhitespace(codepoint) || Character.isSpaceChar(codepoint)
  }

  private def _contains_control(value: String): Boolean = {
    var offset = 0
    var result = false
    while (offset < value.length && !result) {
      val codepoint = value.codePointAt(offset)
      result = Character.isISOControl(codepoint)
      offset += Character.charCount(codepoint)
    }
    result
  }

  private def _invalid_shape(path: String): DynamicDiagnostic = {
    _diagnostic(InvalidShape, path, "The Dynamic value does not match the required v1 shape.")
  }

  private def _invalid_core(path: String): DynamicDiagnostic = {
    _diagnostic(InvalidCoreBinding, path, "The Dynamic projection does not bind exactly to an admitted core record.")
  }

  private def _invalid_semantics(path: String): DynamicDiagnostic = {
    _diagnostic(InvalidDynamicSemantics, path, "The declared Dynamic reference or absence is invalid.")
  }
}
