package cozy.modeler

import scala.util.control.NonFatal
import play.api.libs.json.{JsArray, JsNull, JsObject, JsString, JsValue, Json}
import CmlSemanticFoundation._
import CmlCausalMetadata._
import CmlCausalMetadataProjection.{Facts, Peers}

/*
 * @since   Oct.  5, 2026
 * @version Oct.  5, 2026
 */
private[cozy] object CmlCausalMetadataCodec {
  private val _path = "extensions.cozy.cml.causal"

  def read(value: JsValue): Either[Vector[CausalDiagnostic], Graph] = {
    try {
      _foundation(value).flatMap { envelope =>
        _child(value).flatMap { child =>
          child.value.get("schemaVersion") match {
            case Some(JsString(version)) if version == schemaVersion =>
              CmlSemanticMetadata.build(envelope.catalog, envelope.extensions - namespace)
                .left.map(_ => Vector(_publication_failure)).flatMap(CmlCausalMetadata.build).flatMap { graph =>
                  if (graph.envelope.extensions.value(namespace) == child) Right(graph)
                  else Left(Vector(CausalDiagnostic(InvalidProjection, _path,
                    "The Causal projection does not match its admitted semantic evidence.")))
                }
            case Some(JsString(_)) => Left(Vector(CausalDiagnostic(UnsupportedSchemaVersion,
              _path + ".schemaVersion", "The Causal schema version is unsupported.")))
            case _ => Left(Vector(_invalid_shape(_path + ".schemaVersion")))
          }
        }
      }
    } catch {
      case NonFatal(_) => Left(Vector(_publication_failure))
    }
  }

  def causalJson(envelope: CmlSemanticMetadata.Envelope, peers: Peers, facts: Facts): JsObject = {
    val root = CmlSemanticMetadata.toJson(envelope)
    val core = (root \ "elements").as[Vector[JsValue]].zip(envelope.catalog.elements)
    val links = peers.dynamic.map { graph =>
      _ordered_pairs(envelope.catalog, graph.causalLinks, _array(envelope, CmlDynamicMetadata.namespace, "causalLinks"),
        (value: CmlDynamicMetadata.CausalLinkProjection) => value.element).map(_._2)
    }
    val actors = peers.usecase.map { graph =>
      _ordered_pairs(envelope.catalog, graph.actors, _array(envelope, CmlUseCaseMetadata.namespace, "actors"),
        (value: CmlUseCaseMetadata.ActorProjection) => value.element).map { case (_, json) => _select(json, "operations") }
    }
    val aggregates = peers.structure.map { graph =>
      _ordered_pairs(envelope.catalog, graph.elements, _array(envelope, CmlStructureMetadata.namespace, "elements"),
        (value: CmlStructureMetadata.ElementProjection) => value.element).map { case (_, json) => _select(json, "aggregateBoundary") }
    }
    val rules = peers.dynamic.map { graph =>
      val pairs = graph.workflows.map(_.element).zip(_array(envelope, CmlDynamicMetadata.namespace, "workflows")) ++
        graph.activities.map(_.element).zip(_array(envelope, CmlDynamicMetadata.namespace, "activities"))
      CmlCausalMetadataProjection.ordered(envelope.catalog, pairs, (value: (ElementRecord, JsValue)) => value._1)
        .map { case (_, json) => _select(json, "rules") }
    }
    val collaborators = peers.usecase.map { graph =>
      _ordered_pairs(envelope.catalog, graph.useCases, _array(envelope, CmlUseCaseMetadata.namespace, "useCases"),
        (value: CmlUseCaseMetadata.UseCaseProjection) => value.element).map { case (_, json) => _select(json, "collaborators") }
    }
    val references = core.map { case (json, record) =>
      val filtered = record.references match {
        case Present(values) =>
          val supplied = (json \ "references" \ "value").as[Vector[JsValue]]
          _present(JsArray(values.zip(supplied).collect { case (reference, wire) if reference.target.isInstanceOf[ModelTarget] => wire }))
        case absence: Absent => _absence(absence)
      }
      Json.obj("element" -> json, "references" -> filtered)
    }
    val gaps = facts.capabilitygaps.map { gap =>
      val owner = gap.owner.fold[JsValue](JsNull)(record => core.find(_._2 == record).get._1)
      Json.obj("owner" -> owner, "field" -> JsString(gap.field), "absence" -> _absence(gap.absence))
    }
    Json.obj(
      "schemaVersion" -> schemaVersion,
      "causalLinks" -> _family(facts.causallinks, links),
      "actorOperations" -> _family(facts.actoroperations, actors),
      "aggregateBoundaries" -> _family(facts.aggregateboundaries, aggregates),
      "ruleContexts" -> _family(facts.rulecontexts, rules),
      "collaboratorContexts" -> _family(facts.collaboratorcontexts, collaborators),
      "modelReferences" -> JsArray(references),
      "capabilityGaps" -> JsArray(gaps))
  }

  private def _foundation(value: JsValue): Either[Vector[CausalDiagnostic], CmlSemanticMetadata.Envelope] = {
    if (_contains_null(value)) return Left(Vector(_publication_failure))
    CmlSemanticMetadata.read(value) match {
      case Right(envelope) => Right(envelope)
      case Left(_) =>
        // Foundation requires object extensions. Admit the remainder first so a nonobject
        // owned child gets its fixed child-shape diagnostic without hiding a bad core.
        value match {
          case root: JsObject => root.value.get("extensions") match {
            case Some(extensions: JsObject) if extensions.value.get(namespace).exists(value => !value.isInstanceOf[JsObject]) =>
              CmlSemanticMetadata.read(root + ("extensions" -> (extensions - namespace)))
                .left.map(_ => Vector(_publication_failure))
            case _ => Left(Vector(_publication_failure))
          }
          case _ => Left(Vector(_publication_failure))
        }
    }
  }

  private def _child(value: JsValue): Either[Vector[CausalDiagnostic], JsObject] = {
    (value \ "extensions" \ namespace).toOption match {
      case Some(child: JsObject) => Right(child)
      case _ => Left(Vector(_invalid_shape(_path)))
    }
  }

  private def _ordered_pairs[A](catalog: Catalog, values: Vector[A], json: Vector[JsValue],
    element: A => ElementRecord): Vector[(A, JsValue)] = {
    CmlCausalMetadataProjection.ordered(catalog, values.zip(json), (value: (A, JsValue)) => element(value._1))
  }

  private def _array(envelope: CmlSemanticMetadata.Envelope, namespace: String, field: String): Vector[JsValue] = {
    (envelope.extensions.value(namespace) \ field).as[Vector[JsValue]]
  }

  private def _select(value: JsValue, field: String): JsObject = {
    Json.obj("element" -> (value \ "element").get, field -> (value \ field).get)
  }

  private def _family(value: Presence[_], json: Option[Vector[JsValue]]): JsObject = value match {
    case Present(_) => _present(JsArray(json.get))
    case absence: Absent => _absence(absence)
  }

  private def _present(value: JsValue): JsObject = Json.obj("status" -> "present", "value" -> value)

  private def _absence(value: Absent): JsObject = {
    val reason = value.reason match {
      case NotDeclared => "not-declared"
      case NotRepresented => "not-represented"
      case Unsupported => "unsupported"
      case NotApplicable => "not-applicable"
    }
    Json.obj("status" -> "absent", "reason" -> reason, "detail" -> value.detail)
  }

  private def _invalid_shape(path: String): CausalDiagnostic = CausalDiagnostic(InvalidShape, path,
    "The Causal value does not match the required v1 shape.")
}
