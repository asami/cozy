package cozy.modeler

import play.api.libs.json.{JsArray, JsObject, JsValue, Json}
import CmlSemanticFoundation._
import CmlTerminologyMetadata._
import CmlTerminologyMetadataCodec._

/*
 * @since   Oct.  4, 2026
 * @version Oct.  4, 2026
 */
private[cozy] object CmlTerminologyProjectionCodec {
  // This internal correspondence is made exclusively from an admitted base envelope.
  final class Correspondence private[CmlTerminologyProjectionCodec] (
    val elements: Vector[(JsValue, ElementRecord)],
    val terms: Vector[(JsValue, TermRecord)],
    val references: Map[ElementRecord, Vector[(JsValue, SemanticReference)]]
  )

  def correspondence(envelope: CmlSemanticMetadata.Envelope): Correspondence = {
    val basejson = CmlSemanticMetadata.toJson(envelope)
    val elements = (basejson \ "elements").as[Vector[JsValue]].zip(envelope.catalog.elements)
    val terms = (basejson \ "terms").as[Vector[JsValue]].zip(envelope.catalog.terms)
    val references = elements.map { case (json, record) =>
      val pairs = record.references match {
        case Present(refs) => (json \ "references" \ "value").as[Vector[JsValue]].zip(refs)
        case _: Absent => Vector.empty[(JsValue, SemanticReference)]
      }
      record -> pairs
    }.toMap
    new Correspondence(elements, terms, references)
  }

  def elementJson(correspondence: Correspondence, projection: ElementProjection): JsObject = {
    val elementjson = correspondence.elements.find(_._2 == projection.element).get._1
    val refs = correspondence.references(projection.element).map { case (json, ref) => ref -> json }.toMap
    def _references_json_(values: Vector[SemanticReference]): JsValue = { JsArray(values.map(refs)) }
    Json.obj("element" -> elementjson,
      "termReferences" -> _presence_json(projection.termReferences, _references_json_),
      "conceptualGroups" -> _presence_json(projection.conceptualGroups, _references_json_))
  }

  def termJson(correspondence: Correspondence, projection: TermProjection): JsObject = {
    Json.obj("term" -> correspondence.terms.find(_._2 == projection.term).get._1,
      "termType" -> _presence_json(projection.termType, play.api.libs.json.JsString),
      "bokKind" -> _presence_json(projection.bokKind, _kind_json),
      "labels" -> _presence_json(projection.labels, (values: Vector[LocalizedLabel]) => JsArray(values.map(_label_json))),
      "preferredLabel" -> _presence_json(projection.preferredLabel, _label_json))
  }

  def element(correspondence: Correspondence, value: JsValue, path: String): Result[ElementProjection] = {
    for {
      objectvalue <- _object(value, Vector("element", "termReferences", "conceptualGroups"), path)
      elementjson <- _field(objectvalue, "element", path + ".element")
      record <- correspondence.elements.find(_._1 == elementjson).map(_._2).toRight(_core_binding(path + ".element"))
      references <- _presence_field(objectvalue, "termReferences", path,
        _references(correspondence.references(record), _, _))
      groups <- _presence_field(objectvalue, "conceptualGroups", path,
        _references(correspondence.references(record), _, _))
    } yield ElementProjection(record, references, groups)
  }

  def term(correspondence: Correspondence, value: JsValue, path: String): Result[TermProjection] = {
    for {
      objectvalue <- _object(value, Vector("term", "termType", "bokKind", "labels", "preferredLabel"), path)
      termjson <- _field(objectvalue, "term", path + ".term")
      record <- correspondence.terms.find(_._1 == termjson).map(_._2).toRight(_core_binding(path + ".term"))
      termtype <- _presence_field(objectvalue, "termType", path, _string)
      kind <- _presence_field(objectvalue, "bokKind", path, _kind)
      labels <- _presence_field(objectvalue, "labels", path, (json: JsValue, coordinate: String) => _array(json, coordinate, _label))
      preferred <- _presence_field(objectvalue, "preferredLabel", path, _label)
    } yield TermProjection(record, termtype, kind, labels, preferred)
  }

  private def _references(
    admitted: Vector[(JsValue, SemanticReference)], value: JsValue, path: String
  ): Result[Vector[SemanticReference]] = {
    _array(value, path, (json: JsValue, coordinate: String) => {
      admitted.find(_._1 == json).map(_._2).toRight(_diagnostic(InvalidCoreBinding, coordinate,
        "The Terminology reference does not exactly match an admitted core reference."))
    })
  }

  private def _core_binding(path: String): TerminologyDiagnostic = {
    _diagnostic(InvalidCoreBinding, path,
      "The embedded Terminology record does not exactly match admitted core JSON.")
  }
}
