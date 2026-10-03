package cozy.modeler

import play.api.libs.json.{JsObject, JsValue}

import CmlSemanticFoundation._

/*
 * @since   Oct. 3, 2026
 * @version Oct.  3, 2026
 * @author  ASAMI, Tomoharu
 */
private[cozy] object CmlClassificationMetadata {
  val namespace: String = "cozy.cml.classification"
  val schemaVersion: String = "cozy.cml.classification.v1"

  final case class ModelReference(identity: ModelElementId, boundary: ReferenceBoundary)
  final case class TraitProjection(element: ElementRecord)
  final case class PowertypeProjection(element: ElementRecord, members: Presence[Vector[ModelReference]])
  final case class GeneralizationProjection(
    element: ElementRecord,
    subtype: Presence[ModelReference],
    supertype: Presence[ModelReference]
  )
  final case class TraitRealizationProjection(
    element: ElementRecord,
    subject: Presence[ModelReference],
    traitTarget: Presence[ModelReference]
  )
  final case class PowertypeClassificationProjection(
    element: ElementRecord,
    subject: Presence[ModelReference],
    powertype: Presence[ModelReference]
  )

  sealed abstract class ClassificationDiagnosticKind
  case object InvalidShape extends ClassificationDiagnosticKind
  case object UnsupportedSchemaVersion extends ClassificationDiagnosticKind
  case object InvalidCoreBinding extends ClassificationDiagnosticKind
  case object DuplicateProjection extends ClassificationDiagnosticKind
  case object MissingProjection extends ClassificationDiagnosticKind
  case object InvalidClassificationSemantics extends ClassificationDiagnosticKind
  case object PublicationFailure extends ClassificationDiagnosticKind
  case object ExtensionConflict extends ClassificationDiagnosticKind
  case object UnknownNode extends ClassificationDiagnosticKind
  case object UnknownDimension extends ClassificationDiagnosticKind

  final case class ClassificationDiagnostic(
    kind: ClassificationDiagnosticKind,
    path: String,
    detail: String,
    publication: Option[CmlSemanticMetadata.PublicationDiagnostic]
  )

  final class Graph private[CmlClassificationMetadata] (
    val catalog: Catalog,
    val envelope: CmlSemanticMetadata.Envelope,
    val traits: Vector[TraitProjection],
    val powertypes: Vector[PowertypeProjection],
    val generalizations: Vector[GeneralizationProjection],
    val traitRealizations: Vector[TraitRealizationProjection],
    val powertypeClassifications: Vector[PowertypeClassificationProjection]
  )

  def build(
    catalog: Catalog,
    traits: Vector[TraitProjection],
    powertypes: Vector[PowertypeProjection],
    generalizations: Vector[GeneralizationProjection],
    traitRealizations: Vector[TraitRealizationProjection],
    powertypeClassifications: Vector[PowertypeClassificationProjection],
    extensions: JsObject
  ): Either[Vector[ClassificationDiagnostic], Graph] =
    CmlSemanticMetadata.build(catalog, extensions) match {
      case Left(diagnostics) => Left(diagnostics.map(_publication_failure))
      case Right(_) if extensions.value.contains(namespace) => Left(Vector(_diagnostic(
        ExtensionConflict, "extensions", "The Classification extension namespace is already supplied by the caller."
      )))
      case Right(base) =>
        CmlClassificationMetadataValidation.validate(catalog, traits, powertypes, generalizations, traitRealizations, powertypeClassifications).flatMap { _ =>
          val child = CmlClassificationMetadataCodec.classificationJson(base, traits, powertypes, generalizations, traitRealizations, powertypeClassifications)
          CmlSemanticMetadata.build(catalog, extensions + (namespace -> child)) match {
            case Left(diagnostics) => Left(diagnostics.map(_publication_failure))
            case Right(envelope) => Right(new Graph(catalog, envelope, traits, powertypes, generalizations, traitRealizations, powertypeClassifications))
          }
        }
    }

  def toJson(graph: Graph): JsObject = {
    require(graph != null, "An admitted Classification graph is required.")
    CmlSemanticMetadata.toJson(graph.envelope)
  }

  def canonicalJson(graph: Graph): String = {
    require(graph != null, "An admitted Classification graph is required.")
    CmlSemanticMetadata.canonicalJson(graph.envelope)
  }

  def read(value: JsValue): Either[Vector[ClassificationDiagnostic], Graph] =
    CmlClassificationMetadataCodec.read(value)

  private[modeler] def _diagnostic(kind: ClassificationDiagnosticKind, path: String, detail: String): ClassificationDiagnostic =
    ClassificationDiagnostic(kind, path, detail, None)

  private[modeler] def _publication_failure(value: CmlSemanticMetadata.PublicationDiagnostic): ClassificationDiagnostic =
    ClassificationDiagnostic(PublicationFailure, value.path, "The foundation metadata publication is invalid.", Some(value))
}
