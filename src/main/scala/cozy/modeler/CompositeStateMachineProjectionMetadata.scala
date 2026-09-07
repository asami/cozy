package cozy.modeler

/*
 * @since   Sep.  7, 2026
 * @version Sep.  7, 2026
 * @author  ASAMI, Tomoharu
 */
/** Renders the deterministic Cozy-side JSON projection for normalized Composite StateMachines. */
private[modeler] object CompositeStateMachineProjectionMetadata {
  val schemaVersion: String = "cozy.cml.composite-statemachine-projection.v1"
  val metadataPath: String = "target/cozy/composite-statemachine-projection.json"

  def canonicalJson(definitions: Vector[CompositeStateMachineDefinition]): String =
    _json_object(Vector(
      "schemaVersion" -> _json_string(schemaVersion),
      "definitions" -> _json_array(definitions.map(_definition_json))
    ))

  private def _definition_json(value: CompositeStateMachineDefinition): String =
    _json_object(Vector(
      "identity" -> _json_string(value.identity),
      "name" -> _json_string(value.name),
      "source" -> _source_json(value.source),
      "constituents" -> _json_array(value.constituents.map(_constituent_json)),
      "states" -> _json_array(value.states.map(_state_json)),
      "derivations" -> _json_array(value.derivations.map(_derivation_json)),
      "initialConfiguration" -> _json_option(value.initialConfiguration.map(_configuration_json)),
      "actions" -> _json_array(value.actions.map(_action_json)),
      "constituentActions" -> _json_array(value.constituentActions.map(_constituent_action_json)),
      "derivedActions" -> _json_array(value.derivedActions.map(_derived_action_json))
    ))

  private def _source_json(value: CompositeStateMachineSourceIdentity): String =
    _json_object(Vector(
      "line" -> value.line.map(_.toString).getOrElse("null")
    ))

  private def _constituent_json(value: CompositeStateMachineConstituent): String =
    _json_object(Vector(
      "role" -> _json_string(value.role),
      "stateMachine" -> _reference_json(value.stateMachine),
      "states" -> _json_array(value.states.map(_json_string)),
      "subject" -> _json_option(value.subject.map(_subject_json)),
      "source" -> _source_json(value.source)
    ))

  private def _reference_json(value: CompositeStateMachineReference): String =
    _json_object(Vector(
      "name" -> _json_string(value.name)
    ))

  private def _subject_json(value: CompositeStateMachineSubject): String =
    _json_object(Vector(
      "name" -> _json_string(value.name),
      "type" -> _json_string(value.`type`)
    ))

  private def _state_json(value: CompositeStateMachineState): String =
    _json_object(Vector(
      "name" -> _json_string(value.name),
      "source" -> _source_json(value.source)
    ))

  private def _derivation_json(value: CompositeStateMachineDerivation): String =
    _json_object(Vector(
      "identity" -> _json_string(value.identity),
      "state" -> _json_string(value.state),
      "configuration" -> _configuration_json(value.configuration),
      "source" -> _source_json(value.source)
    ))

  private def _configuration_json(value: CompositeStateMachineConfiguration): String =
    _json_object(Vector(
      "bindings" -> _json_array(value.bindings.map(_configuration_binding_json)),
      "source" -> _source_json(value.source)
    ))

  private def _configuration_binding_json(value: CompositeStateMachineConfigurationBinding): String =
    _json_object(Vector(
      "role" -> _json_string(value.role),
      "state" -> _json_string(value.state)
    ))

  private def _action_json(value: CompositeStateMachineLogicalAction): String =
    _json_object(Vector(
      "identity" -> _json_string(value.identity),
      "kind" -> _json_string(value.kind),
      "operation" -> _operation_json(value.operation),
      "inputBinding" -> _json_option(value.inputBinding.map(_json_string)),
      "source" -> _source_json(value.source)
    ))

  private def _operation_json(value: CompositeStateMachineOperation): String =
    _json_object(Vector(
      "service" -> _json_string(value.service),
      "name" -> _json_string(value.name),
      "inputType" -> _json_option(value.inputType.map(_json_string))
    ))

  private def _constituent_action_json(value: CompositeStateMachineConstituentAction): String =
    _json_object(Vector(
      "identity" -> _json_string(value.identity),
      "role" -> _json_string(value.role),
      "from" -> _json_string(value.from),
      "to" -> _json_string(value.to),
      "on" -> _json_string(value.on),
      "placement" -> _json_string(value.placement),
      "action" -> _action_json(value.action),
      "source" -> _source_json(value.source)
    ))

  private def _derived_action_json(value: CompositeStateMachineDerivedAction): String =
    _json_object(Vector(
      "derivedTransition" -> _json_string(value.derivedTransition),
      "from" -> _json_string(value.from),
      "to" -> _json_string(value.to),
      "action" -> _action_json(value.action),
      "source" -> _source_json(value.source)
    ))

  private def _json_object(fields: Vector[(String, String)]): String =
    fields.map { case (name, value) => s"${_json_string(name)}:$value" }.mkString("{", ",", "}")

  private def _json_array(values: Vector[String]): String = values.mkString("[", ",", "]")

  private def _json_option(value: Option[String]): String = value.getOrElse("null")

  private def _json_string(value: String): String = {
    val escaped = value.flatMap {
      case '"' => "\\\""
      case '\\' => "\\\\"
      case '\b' => "\\b"
      case '\f' => "\\f"
      case '\n' => "\\n"
      case '\r' => "\\r"
      case '\t' => "\\t"
      case character if Character.isISOControl(character) => f"\\u${character.toInt}%04x"
      case character => character.toString
    }
    "\"" + escaped + "\""
  }
}
