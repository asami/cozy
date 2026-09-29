package cozy.ui

import java.time.Instant

import cozy.ui.CozyLogicalUiVocabulary._

/*
 * @since   Sep. 29, 2026
 * @version Sep. 29, 2026
 * @author  ASAMI, Tomoharu
 */
object CozyLogicalUiRuntime {
  final case class DriverReference(
    repository: String,
    revision: String,
    path: String,
    scenarioId: String
  )

  final case class ContractDescriptor(
    schema: String,
    version: Int,
    semanticAuthoritySchema: String,
    assurance: String,
    driver: DriverReference
  )

  val minimumContract = ContractDescriptor(
    schema = "cozy.logical-ui-runtime.v1",
    version = 1,
    semanticAuthoritySchema = CozyLogicalUiSemantics.schema,
    assurance = "provisional-scenario",
    driver = DriverReference(
      repository = "KnowledgeHubProject/nict-editing-studio-app",
      revision = "48eb43b8eb839137e17ef4c944b3db4a73d0cec3",
      path = "docs/phase/phase-2.md",
      scenarioId = "resource-list-detail"
    )
  )

  final case class SourceProvenance(producerId: String, sourceId: String, revision: String)

  final case class Metadata(contract: ContractDescriptor, provenance: SourceProvenance)

  sealed abstract class PresentationRole {
    def id: String
  }

  case object ContentRole extends PresentationRole {
    val id = "content"
  }

  case object TitleRole extends PresentationRole {
    val id = "title"
  }

  case object SubtitleRole extends PresentationRole {
    val id = "subtitle"
  }

  case object DescriptionRole extends PresentationRole {
    val id = "description"
  }

  case object StatusRole extends PresentationRole {
    val id = "status"
  }

  sealed abstract class DisplayValue

  final case class TextValue(text: String) extends DisplayValue
  final case class NumberValue(number: BigDecimal) extends DisplayValue
  final case class BooleanValue(boolean: Boolean) extends DisplayValue
  final case class TimestampValue(instant: Instant) extends DisplayValue
  case object MissingValue extends DisplayValue

  final case class Field(id: String, label: String, role: PresentationRole, value: DisplayValue)

  final case class Section(id: String, title: Option[String], fields: Vector[Field])

  final case class Action(id: String, label: String, enabled: Boolean) {
    def pattern: InteractionPattern = CommandPattern
  }

  final case class ListItem(id: String, fields: Vector[Field], actions: Vector[Action])

  final case class DetailTarget(screenId: String)

  sealed abstract class Screen {
    def id: String
    def purpose: Purpose
    def display: Display
    def actions: Vector[Action]
  }

  final case class ResourceList(
    id: String,
    title: String,
    items: Vector[ListItem],
    detailTarget: DetailTarget,
    actions: Vector[Action]
  ) extends Screen {
    val purpose = BrowsePurpose
    val display = CollectionDisplay
  }

  final case class ResourceDetail(
    id: String,
    resourceId: String,
    title: String,
    sections: Vector[Section],
    actions: Vector[Action]
  ) extends Screen {
    val purpose = InspectPurpose
    val display = DetailDisplay
  }

  final case class Model(metadata: Metadata, screen: Screen)

  final case class RuntimeError(code: String, path: String, reason: String)
}
