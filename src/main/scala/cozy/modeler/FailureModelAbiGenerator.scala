package cozy.modeler

import org.goldenport.realm.Realm

/*
 * @since   Oct.  3, 2026
 * @version Oct.  3, 2026
 * @author  ASAMI, Tomoharu
 */
/** Lossless development ABI projection; runtime resolution belongs to CNCF. */
private[modeler] object FailureModelAbiGenerator {
  val schemaVersion: String = "cozy.failure-model.v1"
  val metadataPath: String = "target/cozy/failure-model.json"
  private val _scala_path = "target/scala-3.3.8/src_managed/main/scala/cozy/generated/failuremodel/FailureModelMetadata.scala"

  def generate(definitions: Vector[ComponentFailureModel]): Realm = {
    val builder = Realm.Builder()
    if (definitions.nonEmpty)
      builder.set(_scala_path, _declarations +
        s"  val schemaVersion: String = ${_quoted(schemaVersion)}\n" +
        s"  val components: Vector[ComponentFailureModel] = ${_scala(Items(definitions.map(_component)))}\n}\n")
    builder.build()
  }

  def canonicalJson(definitions: Vector[ComponentFailureModel]): String =
    _json(Record("", Vector("schemaVersion" -> Text(schemaVersion), "components" -> Items(definitions.map(_component)))))

  // One ordered projection tree feeds both encodings. Record names are fixed ABI
  // constructors, never authored identifiers; optional values retain their shape.
  private sealed trait Value
  private final case class Text(value: String) extends Value
  private final case class Number(value: Int) extends Value
  private final case class Scope(value: FailureScope) extends Value
  private final case class Optional(value: Option[Value]) extends Value
  private final case class Items(values: Vector[Value]) extends Value
  private final case class Record(name: String, fields: Vector[(String, Value)]) extends Value

  private def _source(p: FailureModelSourceIdentity): Value = Record("FailureModelSourceIdentity", Vector(
    "declarationKind" -> Text(p.declarationKind), "sectionPath" -> Text(p.sectionPath), "line" -> Optional(p.line.map(Number))))
  private def _entry(p: FailureModelEntry): Value = Record("FailureModelEntry", Vector(
    "identity" -> Text(p.identity), "scope" -> Scope(p.scope), "source" -> _source(p.source)))
  private def _execution(p: ExecutionFailureModel): Value = Record("ExecutionFailureModel", Vector(
    "identity" -> Text(p.identity), "source" -> _source(p.source), "defaults" -> Items(p.defaults.map(_entry))))
  private def _delta(p: FailureModelDelta): Value = Record("FailureModelDelta", Vector(
    "ownerKind" -> Text(p.ownerKind), "ownerIdentity" -> Text(p.ownerIdentity),
    "source" -> Optional(p.source.map(_source)), "entries" -> Items(p.entries.map(_entry))))
  private def _effective_entry(p: EffectiveFailureEntry): Value = Record("EffectiveFailureEntry", Vector(
    "identity" -> Text(p.identity), "scope" -> Scope(p.scope), "origins" -> Items(p.origins.map(_entry)),
    "winningOrigin" -> _source(p.winningOrigin)))
  private def _effective(p: EffectiveFailureContract): Value = Record("EffectiveFailureContract", Vector(
    "ownerKind" -> Text(p.ownerKind), "ownerIdentity" -> Text(p.ownerIdentity), "source" -> _source(p.source),
    "entries" -> Items(p.entries.map(_effective_entry))))
  private def _operation(p: OperationFailureModel): Value = Record("OperationFailureModel", Vector(
    "identity" -> Text(p.identity), "source" -> _source(p.source), "delta" -> _delta(p.delta), "effective" -> _effective(p.effective)))
  private def _service(p: ServiceFailureModel): Value = Record("ServiceFailureModel", Vector(
    "identity" -> Text(p.identity), "source" -> _source(p.source), "delta" -> _delta(p.delta),
    "effective" -> _effective(p.effective), "operations" -> Items(p.operations.map(_operation))))
  private def _component(p: ComponentFailureModel): Value = Record("ComponentFailureModel", Vector(
    "identity" -> Text(p.identity), "source" -> _source(p.source), "executionSelectionSource" -> _source(p.executionSelectionSource),
    "executionModel" -> _execution(p.executionModel), "delta" -> _delta(p.delta), "effective" -> _effective(p.effective),
    "services" -> Items(p.services.map(_service))))

  private def _json(p: Value): String = p match {
    case Text(value) => _quoted(value)
    case Number(value) => value.toString
    case Scope(value) => _quoted(value.name)
    case Optional(value) => value.map(_json).getOrElse("null")
    case Items(values) => values.map(_json).mkString("[", ",", "]")
    case Record(_, fields) => fields.map { case (key, value) => _quoted(key) + ":" + _json(value) }.mkString("{", ",", "}")
  }

  private def _scala(p: Value): String = p match {
    case Text(value) => _quoted(value)
    case Number(value) => value.toString
    case Scope(FailureScope.InScope) => "FailureScope.InScope"
    case Scope(FailureScope.OutOfScope) => "FailureScope.OutOfScope"
    case Optional(value) => value.map(x => s"Some(${_scala(x)})").getOrElse("None")
    case Items(values) => values.map(_scala).mkString("Vector(", ", ", ")")
    case Record(name, fields) => fields.map(x => _scala(x._2)).mkString(name + "(", ", ", ")")
  }

  /** JSON escapes are also valid Scala quoted literals. Preserve Unicode code units. */
  private def _quoted(value: String): String = "\"" + value.flatMap {
    case '"' => "\\\""
    case '\\' => "\\\\"
    case '\b' => "\\b"
    case '\f' => "\\f"
    case '\n' => "\\n"
    case '\r' => "\\r"
    case '\t' => "\\t"
    case c if Character.isISOControl(c) || Character.isSurrogate(c) || c == '\u2028' || c == '\u2029' => f"\\u${c.toInt}%04x"
    case c => c.toString
  } + "\""

  private val _declarations = """package cozy.generated.failuremodel
    |
    |object FailureModelMetadata {
    |  sealed trait FailureScope { def name: String }
    |  object FailureScope {
    |    case object InScope extends FailureScope { val name = "IN_SCOPE" }
    |    case object OutOfScope extends FailureScope { val name = "OUT_OF_SCOPE" }
    |  }
    |  final case class FailureModelSourceIdentity(declarationKind: String, sectionPath: String, line: Option[Int])
    |  final case class FailureModelEntry(identity: String, scope: FailureScope, source: FailureModelSourceIdentity)
    |  final case class ExecutionFailureModel(identity: String, source: FailureModelSourceIdentity, defaults: Vector[FailureModelEntry])
    |  final case class FailureModelDelta(ownerKind: String, ownerIdentity: String, source: Option[FailureModelSourceIdentity], entries: Vector[FailureModelEntry])
    |  final case class EffectiveFailureEntry(identity: String, scope: FailureScope, origins: Vector[FailureModelEntry], winningOrigin: FailureModelSourceIdentity)
    |  final case class EffectiveFailureContract(ownerKind: String, ownerIdentity: String, source: FailureModelSourceIdentity, entries: Vector[EffectiveFailureEntry])
    |  final case class OperationFailureModel(identity: String, source: FailureModelSourceIdentity, delta: FailureModelDelta, effective: EffectiveFailureContract)
    |  final case class ServiceFailureModel(identity: String, source: FailureModelSourceIdentity, delta: FailureModelDelta, effective: EffectiveFailureContract, operations: Vector[OperationFailureModel])
    |  final case class ComponentFailureModel(identity: String, source: FailureModelSourceIdentity, executionSelectionSource: FailureModelSourceIdentity, executionModel: ExecutionFailureModel, delta: FailureModelDelta, effective: EffectiveFailureContract, services: Vector[ServiceFailureModel])
    |""".stripMargin
}
