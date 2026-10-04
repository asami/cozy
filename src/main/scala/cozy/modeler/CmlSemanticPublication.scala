package cozy.modeler

import scala.util.control.NonFatal
import play.api.libs.json.{JsArray, JsObject, JsValue}
import CmlSemanticFoundation._

/*
 * @since   Oct.  5, 2026
 * @version Oct.  5, 2026
 */
private[cozy] object CmlSemanticPublication {
  val schemaVersion: String = CmlSemanticMetadata.schemaVersion
  val contractVersion: String = "cozy.cml.semantic-strength.v1"
  val registeredNamespaces: Vector[String] = Vector(
    "cozy.cml.structure",
    "cozy.cml.classification",
    "cozy.cml.dynamic",
    "cozy.cml.use-case",
    "cozy.cml.terminology",
    "cozy.cml.causal"
  )

  sealed abstract class PublicationDiagnosticKind
  case object InvalidShape extends PublicationDiagnosticKind
  case object PublicationFailure extends PublicationDiagnosticKind
  case object InvalidSupplier extends PublicationDiagnosticKind

  final case class PublicationDiagnostic(
    kind: PublicationDiagnosticKind,
    path: String,
    detail: String
  )

  final class Publication private[CmlSemanticPublication] (
    val envelope: CmlSemanticMetadata.Envelope,
    val structure: Presence[CmlStructureMetadata.Graph],
    val classification: Presence[CmlClassificationMetadata.Graph],
    val dynamic: Presence[CmlDynamicMetadata.Graph],
    val useCase: Presence[CmlUseCaseMetadata.Graph],
    val terminology: Presence[CmlTerminologyMetadata.Graph],
    val causal: Presence[CmlCausalMetadata.Graph]
  ) {
    val catalog: Catalog = envelope.catalog
  }

  def build(envelope: CmlSemanticMetadata.Envelope): Either[Vector[PublicationDiagnostic], Publication] = {
    if (envelope == null)
      Left(Vector(PublicationDiagnostic(InvalidShape, "envelope",
        "An admitted semantic metadata envelope is required.")))
    else
      _build_root(envelope).flatMap(root => _admit_families(envelope, root))
  }

  def read(value: JsValue): Either[Vector[PublicationDiagnostic], Publication] =
    _read_envelope(value).flatMap(envelope => _admit_families(envelope, value))

  def toJson(publication: Publication): JsObject = {
    require(publication != null, "An admitted semantic publication is required.")
    CmlSemanticMetadata.toJson(publication.envelope)
  }

  def canonicalJson(publication: Publication): String = {
    require(publication != null, "An admitted semantic publication is required.")
    CmlSemanticMetadata.canonicalJson(publication.envelope)
  }

  private def _build_root(envelope: CmlSemanticMetadata.Envelope): Either[Vector[PublicationDiagnostic], JsObject] = {
    try {
      if (_contains_null(envelope.extensions))
        Left(Vector(_publication_failure))
      else
        CmlSemanticMetadata.build(envelope.catalog, envelope.extensions) match {
          case Left(_) => Left(Vector(_publication_failure))
          case Right(base) =>
            val root = CmlSemanticMetadata.toJson(base)
            _read_envelope(root).map(_ => root)
        }
    } catch {
      case NonFatal(_) => Left(Vector(_publication_failure))
    }
  }

  private def _read_envelope(value: JsValue): Either[Vector[PublicationDiagnostic], CmlSemanticMetadata.Envelope] = {
    try {
      if (_contains_null(value))
        Left(Vector(_publication_failure))
      else
        CmlSemanticMetadata.read(value).left.map(_ => Vector(_publication_failure))
    } catch {
      case NonFatal(_) => Left(Vector(_publication_failure))
    }
  }

  private def _admit_families(
    envelope: CmlSemanticMetadata.Envelope,
    root: JsValue
  ): Either[Vector[PublicationDiagnostic], Publication] = {
    for {
      structure <- _family(envelope, root, registeredNamespaces(0), "Structure", CmlStructureMetadata.read)
      classification <- _family(envelope, root, registeredNamespaces(1), "Classification", CmlClassificationMetadata.read)
      dynamic <- _family(envelope, root, registeredNamespaces(2), "Dynamic", CmlDynamicMetadata.read)
      usecase <- _family(envelope, root, registeredNamespaces(3), "Use Case", CmlUseCaseMetadata.read)
      terminology <- _family(envelope, root, registeredNamespaces(4), "Terminology", CmlTerminologyMetadata.read)
      causal <- _family(envelope, root, registeredNamespaces(5), "Causal", CmlCausalMetadata.read)
    } yield new Publication(envelope, structure, classification, dynamic, usecase, terminology, causal)
  }

  private def _family[A, B](
    envelope: CmlSemanticMetadata.Envelope,
    root: JsValue,
    namespace: String,
    label: String,
    reader: JsValue => Either[A, B]
  ): Either[Vector[PublicationDiagnostic], Presence[B]] = {
    if (!envelope.extensions.value.contains(namespace))
      Right(Absent(NotDeclared, s"No admitted $label metadata is supplied."))
    else {
      val failure = Vector(PublicationDiagnostic(InvalidSupplier, "extensions." + namespace,
        s"The supplied $label metadata is not admitted."))
      try {
        reader(root).map(graph => Present(graph): Presence[B]).left.map(_ => failure)
      } catch {
        case NonFatal(_) => Left(failure)
      }
    }
  }

  private def _contains_null(value: JsValue): Boolean = value match {
    case null => true
    case objectvalue: JsObject =>
      objectvalue.fields.exists { case (key, child) => key == null || _contains_null(child) }
    case JsArray(values) => values.exists(_contains_null)
    case _ => false
  }

  private def _publication_failure: PublicationDiagnostic = PublicationDiagnostic(
    PublicationFailure, "publication", "The semantic metadata publication is invalid.")
}
