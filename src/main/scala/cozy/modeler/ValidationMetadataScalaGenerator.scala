package cozy.modeler

import org.goldenport.context.Conclusion
import org.goldenport.kaleidox.{Model => KaleidoxModel}
import org.goldenport.realm.Realm
import ValidationMetadataCml._

/*
 * @since   Oct. 18, 2026
 * @version Oct. 18, 2026
 * @author  ASAMI, Tomoharu
 */
/** Emits the normalized CNCF source ABI into the consumer's executable Test artifact. */
private[modeler] object ValidationMetadataScalaGenerator {
  def fromModel(model: KaleidoxModel): Either[Diagnostic, Option[Realm]] =
    ValidationMetadataCml.fromModel(model).map { declarations =>
      if (declarations.isEmpty) None else Some(_render(declarations))
    }

  def conclusion(diagnostic: Diagnostic): Conclusion =
    Conclusion.invalidArgumentFault(
      s"${diagnostic.code}: path=${diagnostic.path}; reason=${diagnostic.reason}"
    ).withData(diagnostic)

  def libraryConclusion: Conclusion = conclusion(Diagnostic(
    "LIBRARY_GENERATION_UNSUPPORTED_RUNTIME", "validationMetadata",
    "category=validation-metadata is a CNCF runtime feature. Use --generation-target cncf."
  ))

  def requireInput(metadata: Option[Realm]): Unit =
    if (metadata == null || metadata.contains(null))
      conclusion(Diagnostic("VALIDATION_METADATA_ELEMENT_INVALID", "validationMetadata",
        "expected non-null Option containing a non-null Realm")).RAISEC

  def requireLibrary(metadata: Option[Realm]): Unit = {
    requireInput(metadata)
    if (metadata.nonEmpty) libraryConclusion.RAISEC
  }

  private def _render(declarations: Vector[SourceDeclaration]): Realm = {
    val builder = Realm.Builder()
    declarations.foreach { declaration =>
      val provider = declaration.provider
      val path = "target/scala-3.3.8/src_managed/test/scala/" +
        provider.packageName.replace('.', '/') + "/" + provider.className + ".scala"
      builder.set(path, _source(declaration))
    }
    builder.build()
  }

  private def _source(declaration: SourceDeclaration): String = {
    val provider = declaration.provider
    s"""package ${provider.packageName}
       |
       |import org.goldenport.cncf.validation.{ValidationCmlMappingV1, ValidationDeclarationProvider, ValidationMetadataV1}
       |import org.goldenport.cncf.workflow.WorkflowProtocolV1
       |
       |class ${provider.className} extends ValidationDeclarationProvider {
       |  override def declaration: ValidationMetadataV1.Declaration =
       |    ${_properties(declaration.properties)}.declarationC.take
       |}
       |""".stripMargin
  }

  private def _properties(value: Properties): String =
    s"""ValidationCmlMappingV1.Properties(
       |      abiVersion = ${value.abiVersion},
       |      purposes = ${_option(value.purposes)(_strings)},
       |      features = ${value.features.toVector.sortBy(feature => (feature.scope, feature.identity)).map(_feature).mkString("Set(", ", ", ")")},
       |      expectedDuration = ${_option(value.expectedDuration)(_duration)},
       |      durationClass = ${_option(value.durationClass)(_quote)},
       |      executionRequirement = ${_option(value.executionRequirement)(_requirement)},
       |      requiredSemantics = ${_strings(value.requiredSemantics)}
       |    )""".stripMargin

  private def _feature(value: FeatureReference): String =
    s"ValidationMetadataV1.FeatureReference(${_quote(value.scope)}, ${_quote(value.identity)})"

  private def _duration(value: DurationValue): String =
    s"ValidationCmlMappingV1.DurationValue(${value.amount}L, ValidationCmlMappingV1.DurationUnit.${value.unit.label})"

  private def _requirement(value: ExecutionRequirement): String = {
    val capabilities = value.capabilities.map(text => s"WorkflowProtocolV1.CapabilityRequirement(${_quote(text)})")
      .mkString("Vector(", ", ", ")")
    s"WorkflowProtocolV1.ExecutionRequirement(capabilities = $capabilities, " +
      s"risk = WorkflowProtocolV1.RiskLevel(${_quote(value.risk)}), " +
      s"reasoning = WorkflowProtocolV1.ReasoningLevel.${value.reasoning.constructor}, " +
      s"reviewRequired = ${value.reviewRequired}, disposition = ${_option(value.disposition)(_disposition)}, " +
      s"reasoningClass = ${_option(value.reasoningClass)(text => s"WorkflowProtocolV1.ReasoningClassReference(${_quote(text)})")})"
  }

  private def _disposition(value: ExecutionDisposition): String =
    s"WorkflowProtocolV1.ExecutionDisposition(WorkflowProtocolV1.ExecutionPlacement.${value.placement.constructor}, " +
      s"WorkflowProtocolV1.ExecutionIndependence.${value.independence.constructor}, ${_quote(value.originContextIdentity)})"

  private def _option[A](value: Option[A])(render: A => String): String =
    value.map(member => "Some(" + render(member) + ")").getOrElse("None")

  private def _strings(values: Set[String]): String = values.toVector.sorted.map(_quote).mkString("Set(", ", ", ")")

  private def _quote(value: String): String = {
    val escaped = value.flatMap {
      case '"' => "\\\""
      case '\\' => "\\\\"
      case '\n' => "\\n"
      case '\r' => "\\r"
      case '\t' => "\\t"
      case '\b' => "\\b"
      case '\f' => "\\f"
      case character if Character.isISOControl(character) || character == '\u2028' || character == '\u2029' =>
        val hex = Integer.toHexString(character.toInt)
        "\\u" + ("0" * (4 - hex.length)) + hex
      case character => character.toString
    }
    "\"" + escaped + "\""
  }
}
