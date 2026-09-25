package cozy.modeler

import java.util.Locale
import org.goldenport.realm.Realm

/** Additive inbound Provided API producer ABI; the closed Workflow ABI v1 is unchanged. */
private[modeler] object StateMachineProvidedApiAbiGenerator {
  val schemaVersion = "cozy.cml.statemachine-provided-api-abi.v1"
  val bootstrapSchemaVersion = "cozy.cml.statemachine-provided-api-bootstrap.v1"
  val generatorIdentity = "cozy.modeler.StateMachineProvidedApiAbiGenerator"
  val metadataPath = "target/cozy/statemachine-provided-api-abi.json"

  private val _root = "target/scala-3.3.8/src_managed/main/scala/domain/statemachine/providedapi"

  def hasProvidedOperations(workflows: Vector[WorkflowDefinition]): Boolean =
    workflows.exists(_.providedOperations.nonEmpty)

  def generate(workflows: Vector[WorkflowDefinition]): Realm = {
    val selected = workflows.filter(_.providedOperations.nonEmpty)
    val builder = Realm.Builder()
    builder.set(s"${_root}/StateMachineProvidedApiAbi.scala", _abi_source)
    builder.set(s"${_root}/StateMachineProvidedApiComponentFactoryBootstrap.scala", _bootstrap_source(selected))
    selected.zipWithIndex.foreach { case (workflow, index) =>
      builder.set(s"${_root}/${_object_name(workflow, index)}.scala", _workflow_source(workflow, index))
    }
    builder.build()
  }

  def canonicalJson(workflows: Vector[WorkflowDefinition]): String =
    _json_object(Vector(
      "schemaVersion" -> _quote(schemaVersion),
      "generator" -> _quote(generatorIdentity),
      "workflows" -> _array(workflows.filter(_.providedOperations.nonEmpty).map(_workflow_json))
    ))

  private def _abi_source: String =
    s"""package domain.statemachine.providedapi
       |
       |object StateMachineProvidedApiAbi {
       |  val VERSION: String = ${_quote(schemaVersion)}
       |  val GENERATOR: String = ${_quote(generatorIdentity)}
       |  final case class SourceIdentity(line: Option[Int])
       |  final case class WorkflowSourceCorrelation(root: SourceIdentity, definition: SourceIdentity)
       |  final case class Operation(service: String, name: String, inputType: Option[String], resultType: Option[String], source: SourceIdentity)
       |  final case class WorkflowDescriptor(schemaVersion: String, generator: String, identity: String, version: String, source: WorkflowSourceCorrelation, providedOperations: Vector[Operation])
       |  final case class ComponentFactoryMetadata(workflow: WorkflowDescriptor)
       |}
       |""".stripMargin

  private def _bootstrap_source(workflows: Vector[WorkflowDefinition]): String =
    s"""package domain.statemachine.providedapi
       |
       |object StateMachineProvidedApiComponentFactoryBootstrap {
       |  val schemaVersion: String = ${_quote(bootstrapSchemaVersion)}
       |  val componentFactoryMetadata: Vector[StateMachineProvidedApiAbi.ComponentFactoryMetadata] = ${_vector(workflows.zipWithIndex.map { case (workflow, index) => s"${_object_name(workflow, index)}.componentFactoryMetadata" })}
       |}
       |""".stripMargin

  private def _workflow_source(workflow: WorkflowDefinition, index: Int): String =
    s"""package domain.statemachine.providedapi
       |
       |object ${_object_name(workflow, index)} {
       |  val workflow: StateMachineProvidedApiAbi.WorkflowDescriptor = StateMachineProvidedApiAbi.WorkflowDescriptor(
       |    schemaVersion = ${_quote(schemaVersion)},
       |    generator = ${_quote(generatorIdentity)},
       |    identity = ${_quote(workflow.identity)},
       |    version = ${_quote(workflow.version)},
       |    source = StateMachineProvidedApiAbi.WorkflowSourceCorrelation(${_source(workflow.source.root)}, ${_source(workflow.source.definition)}),
       |    providedOperations = ${_vector(workflow.providedOperations.map(_operation))}
       |  )
       |  val componentFactoryMetadata: StateMachineProvidedApiAbi.ComponentFactoryMetadata = StateMachineProvidedApiAbi.ComponentFactoryMetadata(workflow)
       |}
       |""".stripMargin

  private def _operation(value: WorkflowProvidedOperation): String =
    s"StateMachineProvidedApiAbi.Operation(${_quote(value.operation.service)}, ${_quote(value.operation.name)}, ${_option(value.operation.inputType.map(_quote))}, ${_option(value.operation.outputType.map(_quote))}, ${_source(value.source)})"

  private def _workflow_json(value: WorkflowDefinition): String =
    _json_object(Vector(
      "identity" -> _quote(value.identity),
      "version" -> _quote(value.version),
      "source" -> _json_object(Vector(
        "root" -> _source_json(value.source.root),
        "definition" -> _source_json(value.source.definition)
      )),
      "providedOperations" -> _array(value.providedOperations.map { provided =>
        _json_object(Vector(
          "service" -> _quote(provided.operation.service),
          "operation" -> _quote(provided.operation.name),
          "inputType" -> _option(provided.operation.inputType.map(_quote), "null"),
          "resultType" -> _option(provided.operation.outputType.map(_quote), "null"),
          "source" -> _source_json(provided.source)
        ))
      })
    ))

  private def _source(value: WorkflowSourceIdentity): String =
    s"StateMachineProvidedApiAbi.SourceIdentity(${_option(value.line.map(_.toString))})"

  private def _source_json(value: WorkflowSourceIdentity): String =
    _json_object(Vector("line" -> _option(value.line.map(_.toString), "null")))

  private def _object_name(value: WorkflowDefinition, index: Int): String = {
    val words = value.identity.split("[^A-Za-z0-9]+").toVector.filter(_.nonEmpty)
    val stem = words.map(word => word.take(1).toUpperCase(Locale.ROOT) + word.drop(1)).mkString
    val normalized = if (stem.isEmpty) "Workflow" else stem
    val prefixed = if (normalized.head.isDigit) s"Workflow$normalized" else normalized
    s"${prefixed}StateMachineProvidedApi${index + 1}"
  }

  private def _vector(values: Vector[String]): String =
    if (values.isEmpty) "Vector.empty" else values.mkString("Vector(", ", ", ")")

  private def _array(values: Vector[String]): String = values.mkString("[", ",", "]")

  private def _json_object(fields: Vector[(String, String)]): String =
    fields.map { case (name, value) => s"${_quote(name)}:$value" }.mkString("{", ",", "}")

  private def _option(value: Option[String], empty: String = "None"): String =
    value.map(x => if (empty == "None") s"Some($x)" else x).getOrElse(empty)

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
