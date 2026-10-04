package cozy.modeler

import play.api.libs.json.{JsObject, JsValue}
import CmlSemanticFoundation._

/*
 * @since   Oct.  4, 2026
 * @version Oct.  4, 2026
 */
private[cozy] object CmlTerminologyMetadata {
  val namespace: String = "cozy.cml.terminology"
  val schemaVersion: String = "cozy.cml.terminology.v1"

  sealed abstract class BokKind
  case object Mono extends BokKind
  case object Koto extends BokKind

  final case class ElementProjection(
    element: ElementRecord,
    termReferences: Presence[Vector[SemanticReference]],
    conceptualGroups: Presence[Vector[SemanticReference]]
  )

  final case class TermProjection(
    term: TermRecord,
    termType: Presence[String],
    bokKind: Presence[BokKind],
    labels: Presence[Vector[LocalizedLabel]],
    preferredLabel: Presence[LocalizedLabel]
  )

  sealed abstract class TerminologyDiagnosticKind
  case object InvalidShape extends TerminologyDiagnosticKind
  case object UnsupportedSchemaVersion extends TerminologyDiagnosticKind
  case object InvalidCoreBinding extends TerminologyDiagnosticKind
  case object DuplicateProjection extends TerminologyDiagnosticKind
  case object MissingProjection extends TerminologyDiagnosticKind
  case object InvalidTerminologySemantics extends TerminologyDiagnosticKind
  case object PublicationFailure extends TerminologyDiagnosticKind
  case object ExtensionConflict extends TerminologyDiagnosticKind
  case object UnknownElement extends TerminologyDiagnosticKind
  case object UnknownTerm extends TerminologyDiagnosticKind
  case object UnknownLink extends TerminologyDiagnosticKind

  final case class TerminologyDiagnostic(
    kind: TerminologyDiagnosticKind,
    path: String,
    detail: String,
    publication: Option[CmlSemanticMetadata.PublicationDiagnostic]
  )

  final class Graph private[CmlTerminologyMetadata] (
    val catalog: Catalog,
    val envelope: CmlSemanticMetadata.Envelope,
    val elements: Vector[ElementProjection],
    val terms: Vector[TermProjection]
  )

  def build(
    catalog: Catalog,
    elements: Vector[ElementProjection],
    terms: Vector[TermProjection],
    extensions: JsObject
  ): Either[Vector[TerminologyDiagnostic], Graph] = {
    _build(catalog, elements, terms, extensions, None)
  }

  // Reader and publisher share the factory; the reader reuses its admitted correspondence.
  private[modeler] def _build(
    catalog: Catalog,
    elements: Vector[ElementProjection],
    terms: Vector[TermProjection],
    extensions: JsObject,
    correspondence: Option[CmlTerminologyProjectionCodec.Correspondence]
  ): Either[Vector[TerminologyDiagnostic], Graph] = {
    CmlSemanticMetadata.build(catalog, extensions) match {
      case Left(diagnostics) => Left(diagnostics.map(_publication_failure))
      case Right(_) if extensions.value.contains(namespace) =>
        Left(Vector(_diagnostic(ExtensionConflict, "extensions",
          "The Terminology extension namespace is already supplied by the caller.")))
      case Right(base) =>
        CmlTerminologyMetadataValidation.validate(catalog, elements, terms).flatMap { _ =>
          val pairs = correspondence.getOrElse(CmlTerminologyProjectionCodec.correspondence(base))
          val child = CmlTerminologyMetadataCodec.terminologyJson(pairs, elements, terms)
          CmlSemanticMetadata.build(catalog, extensions + (namespace -> child)) match {
            case Left(diagnostics) => Left(diagnostics.map(_publication_failure))
            case Right(envelope) => Right(new Graph(catalog, envelope, elements, terms))
          }
        }
    }
  }

  def read(value: JsValue): Either[Vector[TerminologyDiagnostic], Graph] = {
    CmlTerminologyMetadataCodec.read(value)
  }

  def toJson(graph: Graph): JsObject = {
    require(graph != null, "An admitted Terminology graph is required.")
    CmlSemanticMetadata.toJson(graph.envelope)
  }

  def canonicalJson(graph: Graph): String = {
    require(graph != null, "An admitted Terminology graph is required.")
    CmlSemanticMetadata.canonicalJson(graph.envelope)
  }

  private[modeler] def _diagnostic(
    kind: TerminologyDiagnosticKind, path: String, detail: String
  ): TerminologyDiagnostic = {
    TerminologyDiagnostic(kind, path, detail, None)
  }

  private[modeler] def _publication_failure(
    value: CmlSemanticMetadata.PublicationDiagnostic
  ): TerminologyDiagnostic = {
    TerminologyDiagnostic(PublicationFailure, value.path,
      "The foundation metadata publication is invalid.", Some(value))
  }
}
