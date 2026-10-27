package cozy.modeler

import play.api.libs.json._
import CmlModelEditModel._

/*
 * @since   Oct. 27, 2026
 * @version Oct. 27, 2026
 */
object CmlModelEditJson {
  private def _closed[A](format: OFormat[A], names: String*): OFormat[A] =
    OFormat(Reads[A] {
      case obj: JsObject if obj.keys.subsetOf(names.toSet) => format.reads(obj)
      case _ => JsError("Unknown fields or non-object model-edit record.")
    }, format)
  private def _enum[A](values: Vector[A], name: A => String): Format[A] = Format(
    Reads[A] { value => value.validate[String].flatMap { spelling =>
      values.find(x => name(x) == spelling).map(JsSuccess(_)).getOrElse(JsError("Unsupported model-edit enum."))
    } }, Writes[A](value => JsString(name(value))))

  implicit val elementKindFormat: Format[ElementKind] = _enum(elementKinds, (x: ElementKind) => x.name)
  implicit val relationKindFormat: Format[RelationKind] = _enum(relationKinds, (x: RelationKind) => x.name)
  implicit val evidenceStateFormat: Format[EvidenceState] = _enum(evidenceStates, (x: EvidenceState) => x.name)
  implicit val refFormat: OFormat[RecordRef] = _closed(Json.format[RecordRef], "owner", "identity", "revision")
  implicit val sourceFormat: OFormat[Source] = _closed(Json.format[Source], "owner", "identity", "revision")
  implicit val producerFormat: OFormat[Producer] = _closed(Json.format[Producer], "name", "version", "revision", "modified")
  implicit val fieldFormat: OFormat[Field] = _closed(Json.format[Field], "name", "datatype", "multiplicity")
  implicit val elementFormat: OFormat[Element] = _closed(Json.format[Element], "ref", "kind", "title", "intent", "fields", "scope", "acceptance", "lifecycle", "source")
  implicit val specificationFormat: OFormat[Specification] = _closed(Json.format[Specification], "owner", "identity", "sourceRevision")
  implicit val observationFormat: OFormat[TicketObservation] = _closed(Json.format[TicketObservation], "state", "revision")
  implicit val ticketFormat: OFormat[Ticket] = _closed(Json.format[Ticket], "provider", "owner", "externalIdentity", "observation")
  implicit val endpointFormat: Format[Endpoint] = Format(Reads[Endpoint] { value =>
    (value \ "kind").validate[String].flatMap {
      case "local" if value.as[JsObject].keys == Set("kind", "ref") => (value \ "ref").validate[RecordRef].map(LocalEndpoint)
      case "external" if value.as[JsObject].keys == Set("kind", "ref") => (value \ "ref").validate[RecordRef].map(ExternalEndpoint)
      case "specification" if value.as[JsObject].keys == Set("kind", "specification") => (value \ "specification").validate[Specification].map(SpecificationEndpoint)
      case "ticket" if value.as[JsObject].keys == Set("kind", "ticket") => (value \ "ticket").validate[Ticket].map(TicketEndpoint)
      case _ => JsError("Unsupported or malformed model-edit endpoint.")
    }
  }, Writes[Endpoint] {
    case LocalEndpoint(ref) => Json.obj("kind" -> "local", "ref" -> Json.toJson(ref))
    case ExternalEndpoint(ref) => Json.obj("kind" -> "external", "ref" -> Json.toJson(ref))
    case SpecificationEndpoint(specification) => Json.obj("kind" -> "specification", "specification" -> Json.toJson(specification))
    case TicketEndpoint(ticket) => Json.obj("kind" -> "ticket", "ticket" -> Json.toJson(ticket))
  })
  implicit val relationshipFormat: OFormat[Relationship] = _closed(Json.format[Relationship], "ref", "kind", "from", "to", "source")
  implicit val coverageFormat: OFormat[Coverage] = _closed(Json.format[Coverage], "family", "status", "detail")
  implicit val documentFormat: OFormat[Document] = _closed(Json.format[Document], "schemaVersion", "coverage", "elements", "relationships")
  implicit val limitationFormat: OFormat[Limitation] = _closed(Json.format[Limitation], "code", "detail")
  implicit val publicationFormat: OFormat[Publication] = _closed(Json.format[Publication], "schemaVersion", "source", "producer", "document", "limitations")
  implicit val diagnosticFormat: OFormat[Diagnostic] = _closed(Json.format[Diagnostic], "code", "path", "detail")
  implicit val elementReplacementFormat: OFormat[ElementReplacement] = _closed(Json.format[ElementReplacement], "expected", "replacement")
  implicit val relationshipReplacementFormat: OFormat[RelationshipReplacement] = _closed(Json.format[RelationshipReplacement], "expected", "replacement")
  implicit val candidateFormat: OFormat[Candidate] = _closed(Json.format[Candidate], "schemaVersion", "source", "elements", "relationships", "removedRelationships")
  implicit val candidateV2Format: OFormat[CandidateV2] = _closed(Json.format[CandidateV2], "schemaVersion", "source", "elements", "relationships", "removedRelationships", "addedElements", "addedRelationships", "coverage")
  implicit val renderingFormat: OFormat[Rendering] = _closed(Json.format[Rendering], "schemaVersion", "source", "producer", "candidateSource", "text", "limitations")
}
