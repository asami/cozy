package cozyruntime

import cozy.ui.CozyLogicalUiRuntime._

/*
 * @since   Sep. 29, 2026
 * @version Sep. 29, 2026
 * @author  ASAMI, Tomoharu
 */
object CozyLogicalUiRuntimeFixtures {
  final case class FakeResource(
    id: String,
    productName: String,
    opaqueSourceContent: String,
    price: BigDecimal,
    active: Boolean
  )

  val resources = Vector(
    FakeResource("resource-a", "Amber Notebook", "source:amber", BigDecimal("12.50"), active = true),
    FakeResource("resource-b", "Blue Pencil", "source:blue", BigDecimal("2.00"), active = false)
  )

  val metadata = Metadata(
    contract = minimumContract,
    provenance = SourceProvenance(
      producerId = "cozy-phase-74-fixture",
      sourceId = "editing-studio-planned-scenario",
      revision = "48eb43b8eb839137e17ef4c944b3db4a73d0cec3"
    )
  )

  def listModel: Model = listFor(resources)

  def listFor(values: Vector[FakeResource]): Model =
    Model(
      metadata = metadata,
      screen = ResourceList(
        id = "resource-list",
        title = "Resources",
        items = values.map(_list_item),
        detailTarget = DetailTarget("resource-detail"),
        actions = Vector(Action("refresh-resources", "Refresh", enabled = true))
      )
    )

  def detailFor(resourceId: String): Model =
    configuredDetail(resourceId, defaultDetailFields(resourceId))

  def configuredDetail(resourceId: String, fields: Vector[Field]): Model = {
    val resource = _resource_for(resourceId)
    Model(
      metadata = metadata,
      screen = ResourceDetail(
        id = "resource-detail",
        resourceId = resource.id,
        title = resource.productName,
        sections = Vector(Section("resource-data", Some("Resource data"), fields)),
        actions = Vector(Action("archive-resource", "Archive", enabled = resource.active))
      )
    )
  }

  def defaultDetailFields(resourceId: String): Vector[Field] = {
    val resource = _resource_for(resourceId)
    Vector(
      Field("product-name", "Product name", TitleRole, TextValue(resource.productName)),
      Field("source-content", "Source content", DescriptionRole, TextValue(resource.opaqueSourceContent)),
      Field("price", "Price", ContentRole, NumberValue(resource.price)),
      Field("active", "Active", ContentRole, BooleanValue(resource.active))
    )
  }

  private def _list_item(resource: FakeResource): ListItem =
    ListItem(
      id = resource.id,
      fields = Vector(
        Field("product-name", "Product name", TitleRole, TextValue(resource.productName)),
        Field("source-content", "Source content", DescriptionRole, TextValue(resource.opaqueSourceContent))
      ),
      actions = Vector(Action("edit-resource", "Edit", enabled = resource.active))
    )

  private def _resource_for(resourceid: String): FakeResource =
    resources.find(_.id == resourceid).getOrElse(
      throw new IllegalArgumentException("unknown fixture resource: " + resourceid)
    )
}
