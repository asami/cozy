package cozy.modeler

import scala.collection.immutable.TreeMap
import play.api.libs.json.{JsArray, JsNull, JsNumber, JsObject, JsString, JsValue, Json}

import CmlSemanticFoundation._

/*
 * @since   Sep. 28, 2026
 * @version Sep. 28, 2026
 * @author  ASAMI, Tomoharu
 */
private[cozy] object CmlSemanticMetadata {
  val schemaVersion: String = "cozy.cml.semantic-metadata.v1"
  val metadataPath: String = "target/cozy/cml-semantic-metadata.json"

  sealed abstract class PublicationDiagnosticKind
  case object InvalidShape extends PublicationDiagnosticKind
  case object UnsupportedSchemaVersion extends PublicationDiagnosticKind
  case object InvalidExtension extends PublicationDiagnosticKind
  case object InvalidSemanticCatalog extends PublicationDiagnosticKind

  final case class PublicationDiagnostic(
    kind: PublicationDiagnosticKind,
    path: String,
    detail: String,
    foundation: Option[Diagnostic]
  )

  final class Envelope private[CmlSemanticMetadata] (
    val catalog: Catalog,
    val extensions: JsObject
  )

  def build(
    catalog: Catalog,
    extensions: JsObject
  ): Either[Vector[PublicationDiagnostic], Envelope] = {
    val descriptivediagnostics = _descriptive_diagnostics(catalog)
    val extensiondiagnostics = _extension_diagnostics(extensions)
    val diagnostics = descriptivediagnostics ++ extensiondiagnostics
    if (diagnostics.nonEmpty)
      Left(diagnostics)
    else
      Right(new Envelope(catalog, _sorted_object(extensions.fields.toVector)))
  }

  def toJson(envelope: Envelope): JsObject =
    _sorted_object(Vector(
      "schemaVersion" -> JsString(schemaVersion),
      "elements" -> JsArray(envelope.catalog.elements.map(_element_json)),
      "terms" -> JsArray(envelope.catalog.terms.map(_term_json)),
      "extensions" -> _sort_json(envelope.extensions)
    ))

  def canonicalJson(envelope: Envelope): String =
    Json.stringify(toJson(envelope))

  def read(value: JsValue): Either[Vector[PublicationDiagnostic], Envelope] =
    CmlSemanticMetadataReader.read(value)

  private def _descriptive_diagnostics(catalog: Catalog): Vector[PublicationDiagnostic] =
    if (catalog == null)
      Vector(_invalid_shape("catalog"))
    else
      catalog.elements.zipWithIndex.flatMap { case (record, index) =>
        Vector(
          Option(record.kind).fold(Vector(_invalid_shape(s"elements[$index].kind")))(_ => Vector.empty),
          Option(record.name).fold(Vector(_invalid_shape(s"elements[$index].name")))(_ => Vector.empty)
        ).flatten
      }

  private def _extension_diagnostics(extensions: JsObject): Vector[PublicationDiagnostic] =
    if (extensions == null)
      Vector(PublicationDiagnostic(
        InvalidExtension,
        "extensions",
        "The extension namespace object is invalid.",
        None
      ))
    else if (extensions.value.keys.exists(_ == null))
      Vector(PublicationDiagnostic(
        InvalidExtension,
        "extensions[0]",
        "The extension namespace is invalid.",
        None
      ))
    else
      extensions.value.keys.toVector.sorted.zipWithIndex.flatMap { case (key, index) =>
        extensions.value.get(key) match {
          case Some(_: JsObject) if _valid_extension_key(key) => Vector.empty
          case _ => Vector(PublicationDiagnostic(
            InvalidExtension,
            s"extensions[$index]",
            "The extension namespace is invalid.",
            None
          ))
        }
      }

  private def _valid_extension_key(key: String): Boolean =
    key != null && key.contains(".") && key.matches("[a-z][a-z0-9]*(?:[.-][a-z0-9]+)*")

  private def _element_json(record: ElementRecord): JsObject =
    _sorted_object(Vector(
      "identity" -> _presence_json(record.identity, _model_element_id_json),
      "kind" -> JsString(record.kind),
      "name" -> JsString(record.name),
      "source" -> _source_json(record.source),
      "references" -> _presence_json(record.references, (references: Vector[SemanticReference]) => JsArray(references.map(_reference_json)))
    ))

  private def _term_json(record: TermRecord): JsObject =
    _sorted_object(Vector(
      "identity" -> _term_id_json(record.identity),
      "source" -> _source_json(record.source)
    ))

  private def _reference_json(reference: SemanticReference): JsObject =
    _sorted_object(Vector(
      "target" -> _target_json(reference.target),
      "boundary" -> JsString(_boundary_name(reference.boundary)),
      "relation" -> _presence_json(reference.relation, _relation_id_json),
      "profile" -> _presence_json(reference.profile, _profile_id_json),
      "context" -> _presence_json(reference.context, JsString),
      "preferredLabel" -> _presence_json(reference.preferredLabel, _label_json),
      "source" -> _source_json(reference.source),
      "origin" -> _origin_json(reference.origin)
    ))

  private def _model_element_id_json(value: ModelElementId): JsObject =
    _sorted_object(Vector("modelId" -> JsString(value.modelId), "elementId" -> JsString(value.elementId)))

  private def _term_id_json(value: TermId): JsObject =
    _sorted_object(Vector("vocabularyId" -> JsString(value.vocabularyId), "termId" -> JsString(value.termId)))

  private def _relation_id_json(value: RelationId): JsObject =
    _sorted_object(Vector("vocabularyId" -> JsString(value.vocabularyId), "relationId" -> JsString(value.relationId)))

  private def _profile_id_json(value: ProfileId): JsObject =
    _sorted_object(Vector("vocabularyId" -> JsString(value.vocabularyId), "profileId" -> JsString(value.profileId)))

  private def _source_json(value: SourceAttribution): JsObject =
    _sorted_object(Vector(
      "authorityId" -> JsString(value.authorityId),
      "path" -> JsString(value.path),
      "sha256" -> JsString(value.sha256),
      "line" -> value.line.fold[JsValue](JsNull)(line => JsNumber(BigDecimal(line)))
    ))

  private def _label_json(value: LocalizedLabel): JsObject =
    _sorted_object(Vector(
      "text" -> JsString(value.text),
      "language" -> value.language.fold[JsValue](JsNull)(JsString)
    ))

  private def _presence_json[A](value: Presence[A], render: A => JsValue): JsObject =
    value match {
      case Present(present) => _sorted_object(Vector("status" -> JsString("present"), "value" -> render(present)))
      case Absent(reason, detail) => _sorted_object(Vector(
        "status" -> JsString("absent"),
        "reason" -> JsString(_absence_reason_name(reason)),
        "detail" -> JsString(detail)
      ))
    }

  private def _target_json(value: SemanticTarget): JsObject =
    value match {
      case ModelTarget(identity) => _sorted_object(Vector("kind" -> JsString("model-element"), "identity" -> _model_element_id_json(identity)))
      case TermTarget(identity) => _sorted_object(Vector("kind" -> JsString("term"), "identity" -> _term_id_json(identity)))
    }

  private def _origin_json(value: ReferenceOrigin): JsObject =
    value match {
      case Declared => _sorted_object(Vector("kind" -> JsString("declared")))
      case Derived(ruleid, sources) => _sorted_object(Vector(
        "kind" -> JsString("derived"),
        "ruleId" -> JsString(ruleid),
        "sources" -> JsArray(sources.map(_source_json))
      ))
    }

  private def _boundary_name(value: ReferenceBoundary): String =
    value match {
      case Local => "local"
      case External => "external"
    }

  private def _absence_reason_name(value: AbsenceReason): String =
    value match {
      case NotDeclared => "not-declared"
      case NotRepresented => "not-represented"
      case Unsupported => "unsupported"
      case NotApplicable => "not-applicable"
    }

  private def _sorted_object(fields: Vector[(String, JsValue)]): JsObject =
    JsObject(TreeMap(fields.map { case (key, value) => key -> _sort_json(value) }: _*))

  private def _sort_json(value: JsValue): JsValue =
    value match {
      case objectvalue: JsObject => _sorted_object(objectvalue.fields.toVector)
      case JsArray(values) => JsArray(values.map(_sort_json))
      case scalar => scalar
    }

  private def _invalid_shape(path: String): PublicationDiagnostic =
    PublicationDiagnostic(
      InvalidShape,
      path,
      "The semantic-metadata value does not match the required v1 shape.",
      None
    )
}
