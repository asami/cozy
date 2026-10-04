package cozy.modeler

import cozy.ui.CozyLogicalUiRuntime
import cozy.ui.CozyLogicalUiRuntime.{PresentationRole, ContentRole, TitleRole, SubtitleRole, DescriptionRole, StatusRole}
import org.goldenport.kaleidox.{Model => KaleidoxModel}
import LogicalUiProducer._

/*
 * @since   Oct.  4, 2026
 * @version Oct.  4, 2026
 * @author  ASAMI, Tomoharu
 */
/** Internal rendering data for the fixed public constructor shape, without a Scala 3 binary dependency. */
private[modeler] object LogicalUiCncfAbiProjection {
  // Projected constructor labels preserve the frozen consumer schema for the later renderer.
  final case class ProjectedComponent(owner: ComponentOwner, definitions: Vector[ProjectedDefinition])
  final case class ProjectedDefinition(
    abiIdentity: String, abiVersion: Int, semanticContract: ProjectedSemanticContract,
    provenance: ProjectedSourceProvenance, screenId: String, definitionRevision: String,
    viewBindingId: String, screen: ProjectedScreen
  )
  final case class ProjectedSemanticContract(
    schema: String, version: Int, semanticAuthoritySchema: String,
    assurance: String, driver: ProjectedDriverReference
  )
  final case class ProjectedDriverReference(repository: String, revision: String, path: String, scenarioId: String)
  final case class ProjectedSourceProvenance(producerId: String, sourceId: String, revision: String)
  sealed abstract class ProjectedScreen
  final case class ProjectedListDefinition(
    title: String, itemFields: Vector[ProjectedFieldDefinition], detailTarget: ProjectedDetailTarget,
    actions: Vector[ProjectedActionDefinition], itemActions: Vector[ProjectedActionDefinition]
  ) extends ProjectedScreen
  final case class ProjectedDetailDefinition(
    title: String, sections: Vector[ProjectedSectionDefinition], actions: Vector[ProjectedActionDefinition]
  ) extends ProjectedScreen
  final case class ProjectedDetailTarget(screenId: String)
  final case class ProjectedSectionDefinition(id: String, title: Option[String], fields: Vector[ProjectedFieldDefinition])
  final case class ProjectedFieldDefinition(
    id: String, label: String, role: ProjectedPresentationRole, value: ProjectedValueDefinition
  )
  final case class ProjectedValueDefinition(bindingId: String, allowedKinds: Vector[ProjectedValueKind])
  final case class ProjectedActionDefinition(id: String, label: String, bindingId: String)

  sealed abstract class ProjectedPresentationRole(val constructorReference: String)
  object ProjectedPresentationRole {
    case object Content extends ProjectedPresentationRole("GeneratedLogicalUiAbi.PresentationRole.Content")
    case object Title extends ProjectedPresentationRole("GeneratedLogicalUiAbi.PresentationRole.Title")
    case object Subtitle extends ProjectedPresentationRole("GeneratedLogicalUiAbi.PresentationRole.Subtitle")
    case object Description extends ProjectedPresentationRole("GeneratedLogicalUiAbi.PresentationRole.Description")
    case object Status extends ProjectedPresentationRole("GeneratedLogicalUiAbi.PresentationRole.Status")
  }
  sealed abstract class ProjectedValueKind(val constructorReference: String)
  object ProjectedValueKind {
    case object Text extends ProjectedValueKind("GeneratedLogicalUiAbi.ValueKind.Text")
    case object Number extends ProjectedValueKind("GeneratedLogicalUiAbi.ValueKind.Number")
    case object Boolean extends ProjectedValueKind("GeneratedLogicalUiAbi.ValueKind.Boolean")
    case object Timestamp extends ProjectedValueKind("GeneratedLogicalUiAbi.ValueKind.Timestamp")
    case object Missing extends ProjectedValueKind("GeneratedLogicalUiAbi.ValueKind.Missing")
  }

  def admit(component: SourceComponent): Either[Diagnostic, ProjectedComponent] =
    LogicalUiProducerValidation.validate(component).map(_ => _project(component))

  def admitAll(components: Vector[SourceComponent]): Either[Diagnostic, Vector[ProjectedComponent]] = {
    if (components == null)
      Left(Diagnostic(DiagnosticCode.ElementInvalid, "logicalUi", "null component vector"))
    else {
      val problem = components.iterator.zipWithIndex.flatMap { case (component, index) =>
        LogicalUiProducerValidation.validate(component).left.toOption.map { diagnostic =>
          diagnostic.copy(path = s"logicalUi[$index]" + diagnostic.path.stripPrefix("logicalUi"))
        }
      }.toStream.headOption.orElse {
        components.indices.find(index => components.take(index).exists(
          _.owner.componentName == components(index).owner.componentName)).map { index =>
          Diagnostic(DiagnosticCode.ComponentInvalid, s"logicalUi[$index].owner.componentName",
            "one Logical UI root per explicit component")
        }
      }
      problem match {
        case Some(diagnostic) => Left(diagnostic)
        case None => Right(components.map(_project))
      }
    }
  }

  def fromModel(model: KaleidoxModel): Either[Diagnostic, Vector[ProjectedComponent]] =
    LogicalUiProducerCml.definitions(model).flatMap(admitAll)

  private def _project(component: SourceComponent): ProjectedComponent = {
    val contract = CozyLogicalUiRuntime.minimumContract
    val driver = contract.driver
    val descriptor = ProjectedSemanticContract(contract.schema, contract.version,
      contract.semanticAuthoritySchema, contract.assurance,
      ProjectedDriverReference(driver.repository, driver.revision, driver.path, driver.scenarioId))
    val source = component.provenance
    val provenance = ProjectedSourceProvenance(source.producerId, source.sourceId, source.revision)
    ProjectedComponent(component.owner, component.definitions.map { definition =>
      ProjectedDefinition("cncf.logical-ui-generated.v1", 1, descriptor, provenance,
        definition.screenId, definition.definitionRevision, definition.viewBindingId, _screen(definition.screen))
    })
  }

  private def _screen(screen: Screen): ProjectedScreen = screen match {
    case list: ListScreen => ProjectedListDefinition(list.title, list.itemFields.map(_field),
      ProjectedDetailTarget(list.detailTarget.screenId), list.actions.map(_action), list.itemActions.map(_action))
    case detail: DetailScreen => ProjectedDetailDefinition(detail.title, detail.sections.map { section =>
      ProjectedSectionDefinition(section.id, section.title, section.fields.map(_field))
    }, detail.actions.map(_action))
  }

  private def _field(field: Field): ProjectedFieldDefinition =
    ProjectedFieldDefinition(field.id, field.label, _role(field.role),
      ProjectedValueDefinition(field.value.bindingId, field.value.allowedKinds.map(_kind)))

  private def _action(action: Action): ProjectedActionDefinition =
    ProjectedActionDefinition(action.id, action.label, action.bindingId)

  private def _role(role: PresentationRole): ProjectedPresentationRole = role match {
    case ContentRole => ProjectedPresentationRole.Content
    case TitleRole => ProjectedPresentationRole.Title
    case SubtitleRole => ProjectedPresentationRole.Subtitle
    case DescriptionRole => ProjectedPresentationRole.Description
    case StatusRole => ProjectedPresentationRole.Status
  }

  private def _kind(kind: StaticValueKind): ProjectedValueKind = kind match {
    case StaticValueKind.Text => ProjectedValueKind.Text
    case StaticValueKind.Number => ProjectedValueKind.Number
    case StaticValueKind.Boolean => ProjectedValueKind.Boolean
    case StaticValueKind.Timestamp => ProjectedValueKind.Timestamp
    case StaticValueKind.Missing => ProjectedValueKind.Missing
  }
}
