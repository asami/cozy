package cozy.modeler

import java.util.Locale
import org.goldenport.realm.Realm

/*
 * @since   Sep.  8, 2026
 * @version Sep.  8, 2026
 * @author  ASAMI, Tomoharu
 */
/** Emits the additive, versioned producer metadata surface for normalized Composite StateMachine Actions. */
private[modeler] object CompositeStateMachineActionProducerMetadata {
  val schemaVersion: String = "cozy.cml.action-producer-metadata.v1"
  val metadataPath: String = "target/cozy/cml-action-producer-metadata.json"

  private val _root = "target/scala-3.3.8/src_managed/main/scala/domain/composite/statemachine/actionproducer"

  def generate(definitions: Vector[CompositeStateMachineDefinition]): Realm = {
    val builder = Realm.Builder()
    builder.set(s"${_root}/CompositeStateMachineActionProducerMetadataAbi.scala", _abi_source)
    builder.set(s"${_root}/CompositeStateMachineActionProducerMetadataBootstrap.scala", _bootstrap_source(definitions))
    definitions.zipWithIndex.foreach { case (definition, index) =>
      val actions = _metadata_actions(definition)
      if (actions.nonEmpty) {
        val objectname = _object_name(definition, index)
        builder.set(s"${_root}/$objectname.scala", _definition_source(definition, actions, objectname))
      }
    }
    builder.build()
  }

  def canonicalJson(definitions: Vector[CompositeStateMachineDefinition]): String =
    _json_object(Vector(
      "schemaVersion" -> _json_string(schemaVersion),
      "definitions" -> _json_array(definitions.flatMap { definition =>
        val actions = _metadata_actions(definition)
        if (actions.nonEmpty) Vector(_definition_json(definition, actions)) else Vector.empty
      })
    ))

  private def _abi_source: String =
    s"""package domain.composite.statemachine.actionproducer
       |
       |object CompositeStateMachineActionProducerMetadataAbi {
       |  val VERSION: String = ${_quote(schemaVersion)}
       |
       |  final case class Idempotency(required: Boolean, keyRef: Option[String])
       |  final case class ActionMetadata(definitionIdentity: String, actionId: String, effectClass: String, transactionRequirement: String, idempotency: Idempotency, compensationHandlerRef: Option[String])
       |}
       |""".stripMargin

  private def _bootstrap_source(definitions: Vector[CompositeStateMachineDefinition]): String = {
    val references = definitions.zipWithIndex.collect {
      case (definition, index) if _metadata_actions(definition).nonEmpty =>
        s"${_object_name(definition, index)}.metadata"
    }
    val metadatavector = if (references.nonEmpty) {
      references.mkString("Vector(", ", ", ").flatten")
    } else {
      "Vector.empty"
    }
    s"""package domain.composite.statemachine.actionproducer
       |
       |object CompositeStateMachineActionProducerMetadataBootstrap {
       |  val schemaVersion: String = ${_quote(schemaVersion)}
       |  val metadata: Vector[CompositeStateMachineActionProducerMetadataAbi.ActionMetadata] = $metadatavector
       |}
       |""".stripMargin
  }

  private def _definition_source(
    definition: CompositeStateMachineDefinition,
    actions: Vector[CompositeStateMachineLogicalAction],
    objectname: String
  ): String =
    s"""package domain.composite.statemachine.actionproducer
       |
       |object $objectname {
       |  val metadata: Vector[CompositeStateMachineActionProducerMetadataAbi.ActionMetadata] = ${_vector(actions.map(_action_metadata_source(definition, _)))}
       |}
       |""".stripMargin

  private def _metadata_actions(value: CompositeStateMachineDefinition): Vector[CompositeStateMachineLogicalAction] =
    value.actions.filter(_.metadata.nonEmpty)

  private def _action_metadata_source(
    definition: CompositeStateMachineDefinition,
    action: CompositeStateMachineLogicalAction
  ): String = {
    val metadata = action.metadata.getOrElse(
      throw new IllegalArgumentException(s"Action '${action.identity}' must have metadata before producer generation.")
    )
    s"""CompositeStateMachineActionProducerMetadataAbi.ActionMetadata(
       |  definitionIdentity = ${_quote(definition.identity)},
       |  actionId = ${_quote(action.identity)},
       |  effectClass = ${_quote(metadata.effectClass.canonicalValue)},
       |  transactionRequirement = ${_quote(metadata.transactionRequirement.canonicalValue)},
       |  idempotency = ${_idempotency_source(metadata.idempotency)},
       |  compensationHandlerRef = ${_option(metadata.compensationHandlerRef.map(_quote))}
       |)""".stripMargin
  }

  private def _idempotency_source(value: CompositeStateMachineIdempotency): String = value match {
    case CompositeStateMachineIdempotency.NotRequired =>
      "CompositeStateMachineActionProducerMetadataAbi.Idempotency(required = false, keyRef = None)"
    case CompositeStateMachineIdempotency.Required(keyref) =>
      s"CompositeStateMachineActionProducerMetadataAbi.Idempotency(required = true, keyRef = Some(${_quote(keyref)}))"
  }

  private def _definition_json(
    definition: CompositeStateMachineDefinition,
    actions: Vector[CompositeStateMachineLogicalAction]
  ): String =
    _json_object(Vector(
      "definitionIdentity" -> _json_string(definition.identity),
      "actions" -> _json_array(actions.map(_action_metadata_json))
    ))

  private def _action_metadata_json(value: CompositeStateMachineLogicalAction): String = {
    val metadata = value.metadata.getOrElse(
      throw new IllegalArgumentException(s"Action '${value.identity}' must have metadata before producer generation.")
    )
    _json_object(Vector(
      "actionId" -> _json_string(value.identity),
      "effectClass" -> _json_string(metadata.effectClass.canonicalValue),
      "transactionRequirement" -> _json_string(metadata.transactionRequirement.canonicalValue),
      "idempotency" -> _idempotency_json(metadata.idempotency),
      "compensationHandlerRef" -> _json_option(metadata.compensationHandlerRef.map(_json_string))
    ))
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

  private def _object_name(value: CompositeStateMachineDefinition, index: Int): String = {
    val words = value.identity.split("[^A-Za-z0-9]+").toVector.filter(_.nonEmpty)
    val stem = words.map(word => word.take(1).toUpperCase(Locale.ROOT) + word.drop(1)).mkString
    val normalized = if (stem.isEmpty) "CompositeStateMachine" else stem
    val prefixed = if (normalized.head.isDigit) s"Composite$normalized" else normalized
    s"${prefixed}ActionProducerMetadata${index + 1}"
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
