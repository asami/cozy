package cozy.modeler

import java.util.Locale
import org.goldenport.realm.Realm

/*
 * @since   Sep.  8, 2026
 * @version Sep.  8, 2026
 * @author  ASAMI, Tomoharu
 */
/** Emits the additive logical-action-to-UnitOfWork producer ABI from normalized Composite StateMachine definitions. */
private[modeler] object CompositeStateMachineActionProgram {
  val schemaVersion: String = "cozy.cml.logical-action-program.v1"
  val metadataPath: String = "target/cozy/cml-logical-action-program.json"

  private val _root = "target/scala-3.3.8/src_managed/main/scala/domain/composite/statemachine/actionprogram"

  private final case class ActionOccurrenceDraft(
    action: CompositeStateMachineLogicalAction,
    origin: String,
    causaltransition: String,
    placement: String,
    role: Option[String],
    from: String,
    to: String,
    on: Option[String],
    source: CompositeStateMachineSourceIdentity
  )

  def generate(definitions: Vector[CompositeStateMachineDefinition]): Realm = {
    val builder = Realm.Builder()
    builder.set(s"${_root}/CompositeStateMachineActionProgramAbi.scala", _abi_source)
    builder.set(s"${_root}/LogicalActionCompiler.scala", _compiler_source)
    builder.set(s"${_root}/CompositeStateMachineActionProgramBootstrap.scala", _bootstrap_source(definitions))
    definitions.zipWithIndex.foreach { case (definition, index) =>
      val objectname = _object_name(definition, index)
      builder.set(s"${_root}/$objectname.scala", _definition_source(definition, objectname))
    }
    builder.build()
  }

  def canonicalJson(definitions: Vector[CompositeStateMachineDefinition]): String =
    _json_object(Vector(
      "schemaVersion" -> _json_string(schemaVersion),
      "definitions" -> _json_array(definitions.map(_definition_json))
    ))

  private def _abi_source: String =
    s"""package domain.composite.statemachine.actionprogram
       |
       |object CompositeStateMachineActionProgramAbi {
       |  val VERSION: String = ${_quote(schemaVersion)}
       |
       |  final case class SourceIdentity(line: Option[Int])
       |  final case class Operation(service: String, name: String, inputType: Option[String])
       |  final case class Idempotency(required: Boolean, keyRef: Option[String])
       |  final case class ActionMetadata(effectClass: String, transactionRequirement: String, idempotency: Idempotency, compensationHandlerRef: Option[String])
       |  final case class LogicalActionDescriptor(actionId: String, kind: String, operation: Operation, inputBinding: Option[String], actionSource: SourceIdentity, metadata: Option[ActionMetadata])
       |  final case class Occurrence(definitionIdentity: String, occurrenceId: String, ordinal: Int, actionId: String, kind: String, operation: Operation, inputBinding: Option[String], actionSource: SourceIdentity, occurrenceSource: SourceIdentity, origin: String, causalTransition: String, placement: String, role: Option[String], from: String, to: String, on: Option[String], metadata: Option[ActionMetadata])
       |  final case class CompiledOccurrence(definitionIdentity: String, occurrenceId: String, ordinal: Int, actionId: String, kind: String, operation: Operation, inputBinding: Option[String], actionSource: SourceIdentity, occurrenceSource: SourceIdentity, origin: String, causalTransition: String, placement: String, role: Option[String], from: String, to: String, on: Option[String], metadata: ActionMetadata)
       |  final case class Definition(schemaVersion: String, definitionIdentity: String, actions: Vector[LogicalActionDescriptor], occurrences: Vector[Occurrence])
       |}
       |""".stripMargin

  private def _compiler_source: String =
    """package domain.composite.statemachine.actionprogram
       |
       |import cats.free.Free
       |
       |object LogicalActionCompiler {
       |  type ExecProgram[A] = org.goldenport.cncf.unitofwork.ExecProgram[A]
       |
       |  final case class Binding(actionId: String, operation: CompositeStateMachineActionProgramAbi.Operation, inputBinding: Option[String], program: ExecProgram[Unit])
       |  sealed trait ValidationError
       |  final case class UnknownBinding(actionId: String) extends ValidationError
       |  final case class DuplicateBinding(actionId: String) extends ValidationError
       |  final case class MissingBinding(actionId: String) extends ValidationError
       |  final case class IncompatibleBinding(actionId: String, expectedOperation: CompositeStateMachineActionProgramAbi.Operation, suppliedOperation: CompositeStateMachineActionProgramAbi.Operation, expectedInputBinding: Option[String], suppliedInputBinding: Option[String]) extends ValidationError
       |  final case class UnknownOccurrenceSelection(occurrenceId: String) extends ValidationError
       |  final case class DuplicateOccurrenceSelection(occurrenceId: String) extends ValidationError
       |  final case class MissingMetadata(occurrenceId: String, actionId: String) extends ValidationError
       |  final case class CompiledProgram(occurrences: Vector[CompositeStateMachineActionProgramAbi.CompiledOccurrence], program: ExecProgram[Unit])
       |
       |  def compileAll(definition: CompositeStateMachineActionProgramAbi.Definition, bindings: Vector[Binding]): Either[Vector[ValidationError], CompiledProgram] =
       |    compile(definition, bindings, definition.occurrences.map(_.occurrenceId))
       |
       |  def compile(definition: CompositeStateMachineActionProgramAbi.Definition, bindings: Vector[Binding], requestedOccurrenceIds: Vector[String]): Either[Vector[ValidationError], CompiledProgram] = {
       |    val selected = _selected_occurrences(definition, requestedOccurrenceIds)
       |    val errors = _binding_errors(definition, bindings, selected) ++ _selection_errors(definition, requestedOccurrenceIds) ++ _metadata_errors(selected)
       |    if (errors.nonEmpty) {
       |      Left(errors)
       |    } else {
       |      val compiled = selected.map(_compiled_occurrence)
       |      val programs = compiled.map { occurrence =>
       |        bindings.find(_.actionId == occurrence.actionId).get.program
       |      }
       |      Right(CompiledProgram(compiled, _compose(programs)))
       |    }
       |  }
       |
       |  private def _selected_occurrences(definition: CompositeStateMachineActionProgramAbi.Definition, requestedOccurrenceIds: Vector[String]): Vector[CompositeStateMachineActionProgramAbi.Occurrence] =
       |    definition.occurrences.filter(occurrence => requestedOccurrenceIds.contains(occurrence.occurrenceId))
       |
       |  private def _binding_errors(definition: CompositeStateMachineActionProgramAbi.Definition, bindings: Vector[Binding], selected: Vector[CompositeStateMachineActionProgramAbi.Occurrence]): Vector[ValidationError] = {
       |    val unknown = bindings.collect {
       |      case binding if !definition.actions.exists(_.actionId == binding.actionId) => UnknownBinding(binding.actionId)
       |    }
       |    val duplicates = bindings.zipWithIndex.collect {
       |      case (binding, index) if bindings.take(index).exists(_.actionId == binding.actionId) => DuplicateBinding(binding.actionId)
       |    }
       |    val incompatible = bindings.flatMap { binding =>
       |      definition.actions.find(_.actionId == binding.actionId).flatMap { expected =>
       |        if (expected.operation != binding.operation || expected.inputBinding != binding.inputBinding)
       |          Some(IncompatibleBinding(binding.actionId, expected.operation, binding.operation, expected.inputBinding, binding.inputBinding))
       |        else
       |          None
       |      }
       |    }
       |    val requiredActionIds = selected.foldLeft(Vector.empty[String]) { (acc, occurrence) =>
       |      if (acc.contains(occurrence.actionId)) acc else acc :+ occurrence.actionId
       |    }
       |    val missing = requiredActionIds.collect {
       |      case actionId if !bindings.exists(_.actionId == actionId) => MissingBinding(actionId)
       |    }
       |    unknown ++ duplicates ++ incompatible ++ missing
       |  }
       |
       |  private def _selection_errors(definition: CompositeStateMachineActionProgramAbi.Definition, requestedOccurrenceIds: Vector[String]): Vector[ValidationError] = {
       |    val unknown = requestedOccurrenceIds.collect {
       |      case occurrenceId if !definition.occurrences.exists(_.occurrenceId == occurrenceId) => UnknownOccurrenceSelection(occurrenceId)
       |    }
       |    val duplicates = requestedOccurrenceIds.zipWithIndex.collect {
       |      case (occurrenceId, index) if requestedOccurrenceIds.take(index).contains(occurrenceId) => DuplicateOccurrenceSelection(occurrenceId)
       |    }
       |    unknown ++ duplicates
       |  }
       |
       |  private def _metadata_errors(selected: Vector[CompositeStateMachineActionProgramAbi.Occurrence]): Vector[ValidationError] =
       |    selected.collect {
       |      case occurrence if occurrence.metadata.isEmpty => MissingMetadata(occurrence.occurrenceId, occurrence.actionId)
       |    }
       |
       |  private def _compiled_occurrence(occurrence: CompositeStateMachineActionProgramAbi.Occurrence): CompositeStateMachineActionProgramAbi.CompiledOccurrence =
       |    CompositeStateMachineActionProgramAbi.CompiledOccurrence(
       |      definitionIdentity = occurrence.definitionIdentity,
       |      occurrenceId = occurrence.occurrenceId,
       |      ordinal = occurrence.ordinal,
       |      actionId = occurrence.actionId,
       |      kind = occurrence.kind,
       |      operation = occurrence.operation,
       |      inputBinding = occurrence.inputBinding,
       |      actionSource = occurrence.actionSource,
       |      occurrenceSource = occurrence.occurrenceSource,
       |      origin = occurrence.origin,
       |      causalTransition = occurrence.causalTransition,
       |      placement = occurrence.placement,
       |      role = occurrence.role,
       |      from = occurrence.from,
       |      to = occurrence.to,
       |      on = occurrence.on,
       |      metadata = occurrence.metadata.get
       |    )
       |
       |  private def _compose(programs: Vector[ExecProgram[Unit]]): ExecProgram[Unit] =
       |    programs.foldLeft(Free.pure[org.goldenport.cncf.unitofwork.UnitOfWorkOp, Unit](())) { (acc, program) =>
       |      acc.flatMap(_ => program)
       |    }
       |}
       |""".stripMargin

  private def _bootstrap_source(definitions: Vector[CompositeStateMachineDefinition]): String = {
    val references = definitions.zipWithIndex.map { case (definition, index) =>
      s"${_object_name(definition, index)}.definition"
    }
    val definitionvector = if (references.nonEmpty) {
      references.mkString("Vector(", ", ", ")")
    } else {
      "Vector.empty"
    }
    s"""package domain.composite.statemachine.actionprogram
       |
       |object CompositeStateMachineActionProgramBootstrap {
       |  val schemaVersion: String = ${_quote(schemaVersion)}
       |  val definitions: Vector[CompositeStateMachineActionProgramAbi.Definition] = $definitionvector
       |}
       |""".stripMargin
  }

  private def _definition_source(
    definition: CompositeStateMachineDefinition,
    objectname: String
  ): String =
    s"""package domain.composite.statemachine.actionprogram
       |
       |object $objectname {
       |  val definition: CompositeStateMachineActionProgramAbi.Definition = ${_definition(definition)}
       |}
       |""".stripMargin

  private def _definition(value: CompositeStateMachineDefinition): String =
    s"""CompositeStateMachineActionProgramAbi.Definition(
       |  schemaVersion = ${_quote(schemaVersion)},
       |  definitionIdentity = ${_quote(value.identity)},
       |  actions = ${_vector(value.actions.map(_action_source))},
       |  occurrences = ${_vector(_occurrence_drafts(value).zipWithIndex.map { case (draft, index) => _occurrence_source(value, draft, index + 1) })}
       |)""".stripMargin

  private def _definition_json(value: CompositeStateMachineDefinition): String =
    _json_object(Vector(
      "definitionIdentity" -> _json_string(value.identity),
      "actions" -> _json_array(value.actions.map(_action_json)),
      "occurrences" -> _json_array(_occurrence_drafts(value).zipWithIndex.map { case (draft, index) => _occurrence_json(value, draft, index + 1) })
    ))

  private def _action_source(value: CompositeStateMachineLogicalAction): String =
    s"""CompositeStateMachineActionProgramAbi.LogicalActionDescriptor(
       |  actionId = ${_quote(value.identity)},
       |  kind = ${_quote(value.kind)},
       |  operation = ${_operation_source(value.operation)},
       |  inputBinding = ${_option(value.inputBinding.map(_quote))},
       |  actionSource = ${_source(value.source)},
       |  metadata = ${_option(value.metadata.map(_metadata_source))}
       |)""".stripMargin

  private def _action_json(value: CompositeStateMachineLogicalAction): String =
    _json_object(Vector(
      "actionId" -> _json_string(value.identity),
      "kind" -> _json_string(value.kind),
      "operation" -> _operation_json(value.operation),
      "inputBinding" -> _json_option(value.inputBinding.map(_json_string)),
      "actionSource" -> _source_json(value.source),
      "metadata" -> _json_option(value.metadata.map(_metadata_json))
    ))

  private def _occurrence_drafts(value: CompositeStateMachineDefinition): Vector[ActionOccurrenceDraft] = {
    val constituents = _ordered_constituent_actions(value).map { occurrence =>
      ActionOccurrenceDraft(
        action = occurrence.action,
        origin = "constituent",
        causaltransition = occurrence.identity,
        placement = occurrence.placement,
        role = Some(occurrence.role),
        from = occurrence.from,
        to = occurrence.to,
        on = Some(occurrence.on),
        source = occurrence.source
      )
    }
    val derived = value.derivedActions.map { occurrence =>
      ActionOccurrenceDraft(
        action = occurrence.action,
        origin = "derived",
        causaltransition = occurrence.derivedTransition,
        placement = "derived-transition",
        role = None,
        from = occurrence.from,
        to = occurrence.to,
        on = None,
        source = occurrence.source
      )
    }
    constituents ++ derived
  }

  private def _ordered_constituent_actions(value: CompositeStateMachineDefinition): Vector[CompositeStateMachineConstituentAction] = {
    val causalkeys = value.constituentActions.map(_constituent_causal_key).distinct
    causalkeys.flatMap { causalkey =>
      value.constituentActions.zipWithIndex.collect {
        case (occurrence, index) if _constituent_causal_key(occurrence) == causalkey => occurrence -> index
      }.sortBy { case (occurrence, index) =>
        (_placement_rank(occurrence.placement), index)
      }.map(_._1)
    }
  }

  private def _constituent_causal_key(value: CompositeStateMachineConstituentAction): String =
    Vector(value.role, value.from, value.on, value.to).mkString("\u0000")

  private def _placement_rank(value: String): Int = value match {
    case "exit" => 0
    case "transition" => 1
    case "entry" => 2
    case _ => 3
  }

  private def _occurrence_source(
    definition: CompositeStateMachineDefinition,
    draft: ActionOccurrenceDraft,
    ordinal: Int
  ): String = {
    val action = draft.action
    s"""CompositeStateMachineActionProgramAbi.Occurrence(
       |  definitionIdentity = ${_quote(definition.identity)},
       |  occurrenceId = ${_quote(_occurrence_id(draft, ordinal))},
       |  ordinal = $ordinal,
       |  actionId = ${_quote(action.identity)},
       |  kind = ${_quote(action.kind)},
       |  operation = ${_operation_source(action.operation)},
       |  inputBinding = ${_option(action.inputBinding.map(_quote))},
       |  actionSource = ${_source(action.source)},
       |  occurrenceSource = ${_source(draft.source)},
       |  origin = ${_quote(draft.origin)},
       |  causalTransition = ${_quote(draft.causaltransition)},
       |  placement = ${_quote(draft.placement)},
       |  role = ${_option(draft.role.map(_quote))},
       |  from = ${_quote(draft.from)},
       |  to = ${_quote(draft.to)},
       |  on = ${_option(draft.on.map(_quote))},
       |  metadata = ${_option(action.metadata.map(_metadata_source))}
       |)""".stripMargin
  }

  private def _occurrence_json(
    definition: CompositeStateMachineDefinition,
    draft: ActionOccurrenceDraft,
    ordinal: Int
  ): String = {
    val action = draft.action
    _json_object(Vector(
      "definitionIdentity" -> _json_string(definition.identity),
      "occurrenceId" -> _json_string(_occurrence_id(draft, ordinal)),
      "ordinal" -> ordinal.toString,
      "actionId" -> _json_string(action.identity),
      "kind" -> _json_string(action.kind),
      "operation" -> _operation_json(action.operation),
      "inputBinding" -> _json_option(action.inputBinding.map(_json_string)),
      "actionSource" -> _source_json(action.source),
      "occurrenceSource" -> _source_json(draft.source),
      "origin" -> _json_string(draft.origin),
      "causalTransition" -> _json_string(draft.causaltransition),
      "placement" -> _json_string(draft.placement),
      "role" -> _json_option(draft.role.map(_json_string)),
      "from" -> _json_string(draft.from),
      "to" -> _json_string(draft.to),
      "on" -> _json_option(draft.on.map(_json_string)),
      "metadata" -> _json_option(action.metadata.map(_metadata_json))
    ))
  }

  private def _occurrence_id(value: ActionOccurrenceDraft, ordinal: Int): String =
    s"${value.origin}:${value.causaltransition}:$ordinal"

  private def _operation_source(value: CompositeStateMachineOperation): String =
    s"CompositeStateMachineActionProgramAbi.Operation(${_quote(value.service)}, ${_quote(value.name)}, ${_option(value.inputType.map(_quote))})"

  private def _operation_json(value: CompositeStateMachineOperation): String =
    _json_object(Vector(
      "service" -> _json_string(value.service),
      "name" -> _json_string(value.name),
      "inputType" -> _json_option(value.inputType.map(_json_string))
    ))

  private def _metadata_source(value: CompositeStateMachineActionMetadata): String =
    s"""CompositeStateMachineActionProgramAbi.ActionMetadata(
       |  effectClass = ${_quote(value.effectClass.canonicalValue)},
       |  transactionRequirement = ${_quote(value.transactionRequirement.canonicalValue)},
       |  idempotency = ${_idempotency_source(value.idempotency)},
       |  compensationHandlerRef = ${_option(value.compensationHandlerRef.map(_quote))}
       |)""".stripMargin

  private def _metadata_json(value: CompositeStateMachineActionMetadata): String =
    _json_object(Vector(
      "effectClass" -> _json_string(value.effectClass.canonicalValue),
      "transactionRequirement" -> _json_string(value.transactionRequirement.canonicalValue),
      "idempotency" -> _idempotency_json(value.idempotency),
      "compensationHandlerRef" -> _json_option(value.compensationHandlerRef.map(_json_string))
    ))

  private def _idempotency_source(value: CompositeStateMachineIdempotency): String = value match {
    case CompositeStateMachineIdempotency.NotRequired =>
      "CompositeStateMachineActionProgramAbi.Idempotency(required = false, keyRef = None)"
    case CompositeStateMachineIdempotency.Required(keyref) =>
      s"CompositeStateMachineActionProgramAbi.Idempotency(required = true, keyRef = Some(${_quote(keyref)}))"
  }

  private def _idempotency_json(value: CompositeStateMachineIdempotency): String = value match {
    case CompositeStateMachineIdempotency.NotRequired =>
      _json_object(Vector(
        "required" -> "false",
        "keyRef" -> "null"
      ))
    case CompositeStateMachineIdempotency.Required(keyref) =>
      _json_object(Vector(
        "required" -> "true",
        "keyRef" -> _json_string(keyref)
      ))
  }

  private def _source(value: CompositeStateMachineSourceIdentity): String =
    s"CompositeStateMachineActionProgramAbi.SourceIdentity(${_option(value.line.map(_.toString))})"

  private def _source_json(value: CompositeStateMachineSourceIdentity): String =
    _json_object(Vector("line" -> _json_option(value.line.map(_.toString))))

  private def _object_name(value: CompositeStateMachineDefinition, index: Int): String = {
    val words = value.identity.split("[^A-Za-z0-9]+").toVector.filter(_.nonEmpty)
    val stem = words.map(word => word.take(1).toUpperCase(Locale.ROOT) + word.drop(1)).mkString
    val normalized = if (stem.isEmpty) "CompositeStateMachine" else stem
    val prefixed = if (normalized.head.isDigit) s"Composite$normalized" else normalized
    s"${prefixed}ActionProgram${index + 1}"
  }

  private def _vector(values: Vector[String]): String = values.mkString("Vector(", ", ", ")")

  private def _option(value: Option[String]): String = value.map(x => s"Some($x)").getOrElse("None")

  private def _json_object(fields: Vector[(String, String)]): String =
    fields.map { case (name, value) => s"${_json_string(name)}:$value" }.mkString("{", ",", "}")

  private def _json_array(values: Vector[String]): String = values.mkString("[", ",", "]")

  private def _json_option(value: Option[String]): String = value.getOrElse("null")

  private def _json_string(value: String): String = _quote(value)

  private def _quote(value: String): String = {
    val escaped = value.flatMap {
      case '\\' => "\\\\"
      case '"' => "\\\""
      case '\n' => "\\n"
      case '\r' => "\\r"
      case '\t' => "\\t"
      case character if Character.isISOControl(character) => f"\\u${character.toInt}%04x"
      case character => character.toString
    }
    "\"" + escaped + "\""
  }
}
