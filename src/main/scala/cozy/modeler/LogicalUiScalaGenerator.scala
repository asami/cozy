package cozy.modeler

import org.goldenport.context.Conclusion
import org.goldenport.kaleidox.{Model => KaleidoxModel}
import org.goldenport.realm.Realm
import LogicalUiProducer.{Diagnostic, SourceComponent}
import LogicalUiCncfAbiProjection._

/*
 * @since   Oct.  4, 2026
 * @version Oct.  4, 2026
 * @author  ASAMI, Tomoharu
 */
/** Renders only completely admitted static definitions into the public CNCF source ABI. */
private[modeler] object LogicalUiScalaGenerator {
  def generate(components: Vector[SourceComponent]): Either[Diagnostic, Realm] =
    LogicalUiCncfAbiProjection.admitAll(components).map(_render)

  def fromModel(model: KaleidoxModel): Either[Diagnostic, Option[Realm]] =
    LogicalUiCncfAbiProjection.fromModel(model).map { components =>
      if (components.isEmpty) None else Some(_render(components))
    }

  def conclusion(diagnostic: Diagnostic): Conclusion =
    Conclusion.invalidArgumentFault(
      s"${diagnostic.code.code}: path=${diagnostic.path}; reason=${diagnostic.reason}"
    ).withData(diagnostic)

  private def _render(components: Vector[ProjectedComponent]): Realm = {
    val builder = Realm.Builder()
    components.foreach { component =>
      val stem = component.owner.componentName
      val namespace = component.owner.packageName + ".logicalui"
      val root = "target/scala-3.3.8/src_managed/main/scala/" + namespace.replace('.', '/')
      builder.set(s"$root/${stem}LogicalUiDefinitions.scala", _definitions_source(component, namespace, stem))
      builder.set(s"$root/${stem}LogicalUiMetadataProvider.scala", _provider_source(namespace, stem))
    }
    builder.build()
  }

  private def _definitions_source(component: ProjectedComponent, namespace: String, stem: String): String = {
    val definitions = component.definitions.map(_definition).mkString(",\n")
    s"""package $namespace
       |
       |import org.goldenport.cncf.display.GeneratedLogicalUiAbi
       |
       |object ${stem}LogicalUiDefinitions {
       |  val definitions: Vector[GeneratedLogicalUiAbi.Definition] = Vector(
       |$definitions
       |  )
       |}
       |""".stripMargin
  }

  private def _provider_source(namespace: String, stem: String): String =
    s"""package $namespace
       |
       |import org.goldenport.cncf.display.GeneratedLogicalUiAbi
       |
       |trait ${stem}LogicalUiMetadataProvider extends org.goldenport.cncf.display.GeneratedLogicalUiMetadataProvider {
       |  override def generatedLogicalUiDefinitions: Vector[GeneratedLogicalUiAbi.Definition] =
       |    ${stem}LogicalUiDefinitions.definitions
       |}
       |""".stripMargin

  private def _definition(value: ProjectedDefinition): String =
    s"""    GeneratedLogicalUiAbi.Definition(
       |      ${_quote(value.abiIdentity)}, ${value.abiVersion},
       |      ${_contract(value.semanticContract)},
       |      ${_provenance(value.provenance)},
       |      ${_quote(value.screenId)}, ${_quote(value.definitionRevision)}, ${_quote(value.viewBindingId)},
       |      ${_screen(value.screen)}
       |    )""".stripMargin

  private def _contract(value: ProjectedSemanticContract): String = {
    val driver = value.driver
    s"GeneratedLogicalUiAbi.SemanticContract(${_quote(value.schema)}, ${value.version}, " +
      s"${_quote(value.semanticAuthoritySchema)}, ${_quote(value.assurance)}, " +
      s"GeneratedLogicalUiAbi.DriverReference(${_quote(driver.repository)}, ${_quote(driver.revision)}, " +
      s"${_quote(driver.path)}, ${_quote(driver.scenarioId)}))"
  }

  private def _provenance(value: ProjectedSourceProvenance): String =
    s"GeneratedLogicalUiAbi.SourceProvenance(${_quote(value.producerId)}, ${_quote(value.sourceId)}, ${_quote(value.revision)})"

  private def _screen(value: ProjectedScreen): String = value match {
    case list: ProjectedListDefinition =>
      s"GeneratedLogicalUiAbi.ListDefinition(${_quote(list.title)}, ${_vector(list.itemFields.map(_field))}, " +
        s"GeneratedLogicalUiAbi.DetailTarget(${_quote(list.detailTarget.screenId)}), " +
        s"${_vector(list.actions.map(_action))}, ${_vector(list.itemActions.map(_action))})"
    case detail: ProjectedDetailDefinition =>
      s"GeneratedLogicalUiAbi.DetailDefinition(${_quote(detail.title)}, " +
        s"${_vector(detail.sections.map(_section))}, ${_vector(detail.actions.map(_action))})"
  }

  private def _section(value: ProjectedSectionDefinition): String = {
    val title = value.title.map(text => s"Some(${_quote(text)})").getOrElse("None")
    s"GeneratedLogicalUiAbi.SectionDefinition(${_quote(value.id)}, $title, ${_vector(value.fields.map(_field))})"
  }

  private def _field(value: ProjectedFieldDefinition): String =
    s"GeneratedLogicalUiAbi.FieldDefinition(${_quote(value.id)}, ${_quote(value.label)}, " +
      s"${value.role.constructorReference}, GeneratedLogicalUiAbi.ValueDefinition(${_quote(value.value.bindingId)}, " +
      s"${_vector(value.value.allowedKinds.map(_.constructorReference))}))"

  private def _action(value: ProjectedActionDefinition): String =
    s"GeneratedLogicalUiAbi.ActionDefinition(${_quote(value.id)}, ${_quote(value.label)}, ${_quote(value.bindingId)})"

  private def _vector(values: Vector[String]): String = values.mkString("Vector(", ", ", ")")

  private def _quote(value: String): String = {
    val escaped = value.flatMap {
      case '"' => "\\\""
      case '\\' => "\\\\"
      case '\n' => "\\n"
      case '\r' => "\\r"
      case '\t' => "\\t"
      case '\b' => "\\b"
      case '\f' => "\\f"
      case character if Character.isISOControl(character) =>
        val hex = Integer.toHexString(character.toInt)
        "\\u" + ("0" * (4 - hex.length)) + hex
      case character => character.toString
    }
    "\"" + escaped + "\""
  }
}
