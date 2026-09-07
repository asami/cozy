package cozy.modeler

import java.util.Locale
import org.goldenport.realm.Realm

/*
 * @since   Sep.  7, 2026
 * @version Sep.  7, 2026
 * @author  ASAMI, Tomoharu
 */
/** Emits the standalone, versioned Scala ABI for normalized Composite StateMachine definitions. */
private[modeler] object CompositeStateMachineScalaGenerator {
  private val _root = "target/scala-3.3.8/src_managed/main/scala/domain/composite/statemachine"

  def generate(definitions: Vector[CompositeStateMachineDefinition]): Realm = {
    val builder = Realm.Builder()
    builder.set(s"${_root}/CompositeStateMachineAbi.scala", _abi_source)
    builder.set(s"${_root}/CompositeStateMachineBootstrap.scala", _bootstrap_source(definitions))
    definitions.zipWithIndex.foreach { case (definition, index) =>
      val objectname = _object_name(definition, index)
      builder.set(s"${_root}/$objectname.scala", _definition_source(definition, objectname))
    }
    builder.build()
  }

  private def _abi_source: String =
    s"""package domain.composite.statemachine
       |
       |object CompositeStateMachineAbi {
       |  val Version: String = ${_quote(CompositeStateMachineDefinition.ABI_VERSION)}
       |
       |  final case class SourceIdentity(line: Option[Int])
       |  final case class StateMachineReference(name: String)
       |  final case class Subject(name: String, `type`: String)
       |  final case class ConstituentBinding(role: String, stateMachine: StateMachineReference, states: Vector[String], subject: Option[Subject], source: SourceIdentity)
       |  final case class State(name: String, source: SourceIdentity)
       |  final case class ConfigurationBinding(role: String, state: String)
       |  final case class Configuration(bindings: Vector[ConfigurationBinding], source: SourceIdentity)
       |  final case class Derivation(identity: String, state: String, configuration: Configuration, source: SourceIdentity)
       |  final case class Operation(service: String, name: String, inputType: Option[String])
       |  final case class LogicalAction(identity: String, kind: String, operation: Operation, inputBinding: Option[String], source: SourceIdentity)
       |  final case class ConstituentAction(identity: String, role: String, from: String, to: String, on: String, placement: String, action: LogicalAction, source: SourceIdentity)
       |  final case class DerivedAction(derivedTransition: String, from: String, to: String, action: LogicalAction, source: SourceIdentity)
       |  final case class Definition(abiVersion: String, identity: String, name: String, source: SourceIdentity, constituents: Vector[ConstituentBinding], states: Vector[State], derivations: Vector[Derivation], initialConfiguration: Option[Configuration], actions: Vector[LogicalAction], constituentActions: Vector[ConstituentAction], derivedActions: Vector[DerivedAction])
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
    s"""package domain.composite.statemachine
       |
       |object CompositeStateMachineBootstrap {
       |  val bootstrapAbiVersion: String = ${_quote(CompositeStateMachineDefinition.bootstrapAbiVersion)}
       |  val definitions: Vector[CompositeStateMachineAbi.Definition] = $definitionvector
       |}
       |""".stripMargin
  }

  private def _definition_source(
    definition: CompositeStateMachineDefinition,
    objectname: String
  ): String =
    s"""package domain.composite.statemachine
       |
       |object $objectname {
       |  val definition: CompositeStateMachineAbi.Definition = ${_definition(definition)}
       |}
       |""".stripMargin

  private def _definition(value: CompositeStateMachineDefinition): String =
    s"""CompositeStateMachineAbi.Definition(
       |  abiVersion = ${_quote(CompositeStateMachineDefinition.ABI_VERSION)},
       |  identity = ${_quote(value.identity)},
       |  name = ${_quote(value.name)},
       |  source = ${_source(value.source)},
       |  constituents = ${_vector(value.constituents.map(_constituent))},
       |  states = ${_vector(value.states.map(_state))},
       |  derivations = ${_vector(value.derivations.map(_derivation))},
       |  initialConfiguration = ${_option(value.initialConfiguration.map(_configuration))},
       |  actions = ${_vector(value.actions.map(_action))},
       |  constituentActions = ${_vector(value.constituentActions.map(_constituent_action))},
       |  derivedActions = ${_vector(value.derivedActions.map(_derived_action))}
       |)""".stripMargin

  private def _constituent(value: CompositeStateMachineConstituent): String =
    s"CompositeStateMachineAbi.ConstituentBinding(${_quote(value.role)}, ${_reference(value.stateMachine)}, ${_vector(value.states.map(_quote))}, ${_option(value.subject.map(_subject))}, ${_source(value.source)})"

  private def _reference(value: CompositeStateMachineReference): String =
    s"CompositeStateMachineAbi.StateMachineReference(${_quote(value.name)})"

  private def _subject(value: CompositeStateMachineSubject): String =
    s"CompositeStateMachineAbi.Subject(${_quote(value.name)}, ${_quote(value.`type`)})"

  private def _state(value: CompositeStateMachineState): String =
    s"CompositeStateMachineAbi.State(${_quote(value.name)}, ${_source(value.source)})"

  private def _configuration(value: CompositeStateMachineConfiguration): String =
    s"CompositeStateMachineAbi.Configuration(${_vector(value.bindings.map(_binding))}, ${_source(value.source)})"

  private def _binding(value: CompositeStateMachineConfigurationBinding): String =
    s"CompositeStateMachineAbi.ConfigurationBinding(${_quote(value.role)}, ${_quote(value.state)})"

  private def _derivation(value: CompositeStateMachineDerivation): String =
    s"CompositeStateMachineAbi.Derivation(${_quote(value.identity)}, ${_quote(value.state)}, ${_configuration(value.configuration)}, ${_source(value.source)})"

  private def _action(value: CompositeStateMachineLogicalAction): String =
    s"CompositeStateMachineAbi.LogicalAction(${_quote(value.identity)}, ${_quote(value.kind)}, ${_operation(value.operation)}, ${_option(value.inputBinding.map(_quote))}, ${_source(value.source)})"

  private def _operation(value: CompositeStateMachineOperation): String =
    s"CompositeStateMachineAbi.Operation(${_quote(value.service)}, ${_quote(value.name)}, ${_option(value.inputType.map(_quote))})"

  private def _constituent_action(value: CompositeStateMachineConstituentAction): String =
    s"CompositeStateMachineAbi.ConstituentAction(${_quote(value.identity)}, ${_quote(value.role)}, ${_quote(value.from)}, ${_quote(value.to)}, ${_quote(value.on)}, ${_quote(value.placement)}, ${_action(value.action)}, ${_source(value.source)})"

  private def _derived_action(value: CompositeStateMachineDerivedAction): String =
    s"CompositeStateMachineAbi.DerivedAction(${_quote(value.derivedTransition)}, ${_quote(value.from)}, ${_quote(value.to)}, ${_action(value.action)}, ${_source(value.source)})"

  private def _source(value: CompositeStateMachineSourceIdentity): String =
    s"CompositeStateMachineAbi.SourceIdentity(${_option(value.line.map(_.toString))})"

  private def _vector(values: Vector[String]): String = values.mkString("Vector(", ", ", ")")

  private def _option(value: Option[String]): String = value.map(x => s"Some($x)").getOrElse("None")

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

  private def _object_name(value: CompositeStateMachineDefinition, index: Int): String = {
    val words = value.identity.split("[^A-Za-z0-9]+").toVector.filter(_.nonEmpty)
    val stem = words.map(word => word.take(1).toUpperCase(Locale.ROOT) + word.drop(1)).mkString
    val normalized = if (stem.isEmpty) "CompositeStateMachine" else stem
    val prefixed = if (normalized.head.isDigit) s"Composite$normalized" else normalized
    s"${prefixed}CompositeStateMachine${index + 1}"
  }
}
