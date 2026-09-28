package cozy.modeler

import play.api.libs.json.{JsObject, JsValue}

import CmlSemanticFoundation._

/*
 * @since   Sep. 28, 2026
 * @version Sep. 28, 2026
 * @author  ASAMI, Tomoharu
 */
private[cozy] object CmlStructureMetadata {
  val namespace: String = "cozy.cml.structure"
  val schemaVersion: String = "cozy.cml.structure.v1"

  sealed abstract class ElementKind
  case object Entity extends ElementKind
  case object Value extends ElementKind
  case object Aggregate extends ElementKind

  sealed abstract class RelationKind
  case object Composition extends RelationKind
  case object Aggregation extends RelationKind
  case object Association extends RelationKind

  final case class ModelReference(identity: ModelElementId, boundary: ReferenceBoundary)
  final case class Cardinality(lower: Int, upper: Option[Int])
  final case class AggregateBoundary(aggregate: Presence[ModelReference], membership: Presence[String])
  final case class Endpoint(
    target: Presence[ModelReference],
    role: Presence[String],
    cardinality: Presence[Cardinality],
    navigable: Presence[Boolean]
  )
  final case class RelationSemantics(
    ownership: Presence[String],
    independentExistence: Presence[Boolean],
    createPolicy: Presence[String],
    deletePolicy: Presence[String],
    reassignment: Presence[Boolean],
    reparenting: Presence[Boolean],
    lifecyclePropagation: Presence[Vector[String]],
    aggregateBoundary: Presence[AggregateBoundary]
  )
  final case class ElementProjection(
    element: ElementRecord,
    kind: ElementKind,
    aggregateBoundary: Presence[AggregateBoundary]
  )
  final case class RelationProjection(
    element: ElementRecord,
    kind: RelationKind,
    sourceEndpoint: Endpoint,
    targetEndpoint: Endpoint,
    semantics: RelationSemantics
  )

  sealed abstract class StructureDiagnosticKind
  case object InvalidShape extends StructureDiagnosticKind
  case object UnsupportedSchemaVersion extends StructureDiagnosticKind
  case object InvalidCoreBinding extends StructureDiagnosticKind
  case object DuplicateProjection extends StructureDiagnosticKind
  case object MissingProjection extends StructureDiagnosticKind
  case object InvalidRelationSemantics extends StructureDiagnosticKind
  case object PublicationFailure extends StructureDiagnosticKind
  case object ExtensionConflict extends StructureDiagnosticKind

  final case class StructureDiagnostic(
    kind: StructureDiagnosticKind,
    path: String,
    detail: String,
    publication: Option[CmlSemanticMetadata.PublicationDiagnostic]
  )

  final class Graph private[CmlStructureMetadata] (
    val catalog: Catalog,
    val envelope: CmlSemanticMetadata.Envelope,
    val elements: Vector[ElementProjection],
    val relations: Vector[RelationProjection]
  )

  def build(
    catalog: Catalog,
    elements: Vector[ElementProjection],
    relations: Vector[RelationProjection],
    extensions: JsObject
  ): Either[Vector[StructureDiagnostic], Graph] =
    _build(catalog, elements, relations, extensions, true)

  def toJson(graph: Graph): JsObject =
    CmlSemanticMetadata.toJson(graph.envelope)

  def canonicalJson(graph: Graph): String =
    CmlSemanticMetadata.canonicalJson(graph.envelope)

  def read(value: JsValue): Either[Vector[StructureDiagnostic], Graph] =
    CmlStructureMetadataCodec.read(value)

  private[modeler] def _build(
    catalog: Catalog,
    elements: Vector[ElementProjection],
    relations: Vector[RelationProjection],
    extensions: JsObject,
    rejectnamespace: Boolean
  ): Either[Vector[StructureDiagnostic], Graph] = {
    val publication = CmlSemanticMetadata.build(catalog, extensions)
    publication match {
      case Left(diagnostics) => Left(diagnostics.map(_publication_failure))
      case Right(_) if rejectnamespace && extensions.value.contains(namespace) => Left(Vector(_diagnostic(
        ExtensionConflict, "extensions", "The Structure extension namespace is already supplied by the caller."
      )))
      case Right(_) => CmlStructureMetadataValidation.validate(catalog, elements, relations) match {
        case Left(diagnostics) => Left(diagnostics)
        case Right(_) =>
          val structure = CmlStructureMetadataCodec.structureJson(catalog, elements, relations)
          CmlSemanticMetadata.build(catalog, extensions + (namespace -> structure)) match {
            case Left(diagnostics) => Left(diagnostics.map(_publication_failure))
            case Right(envelope) => Right(new Graph(catalog, envelope, elements, relations))
          }
      }
    }
  }

  private[modeler] def _diagnostic(kind: StructureDiagnosticKind, path: String, detail: String): StructureDiagnostic =
    StructureDiagnostic(kind, path, detail, None)

  private[modeler] def _publication_failure(value: CmlSemanticMetadata.PublicationDiagnostic): StructureDiagnostic =
    StructureDiagnostic(PublicationFailure, value.path, "The foundation metadata publication is invalid.", Some(value))
}
