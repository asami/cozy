package cozy.modeler

import play.api.libs.json.{JsArray, JsBoolean, JsNull, JsNumber, JsObject, JsString, JsValue, Json}

import CmlSemanticFoundation._
import CmlStructureMetadata._

/*
 * @since   Sep. 28, 2026
 * @version Sep. 28, 2026
 * @author  ASAMI, Tomoharu
 */
private[cozy] object CmlStructureMetadataCodec {
  private type Result[A] = Either[StructureDiagnostic, A]

  def structureJson(catalog: Catalog, elements: Vector[ElementProjection], relations: Vector[RelationProjection]): JsObject =
    Json.obj(
      "schemaVersion" -> JsString(schemaVersion),
      "elements" -> JsArray(elements.map(_element_json(catalog, _))),
      "relations" -> JsArray(relations.map(_relation_json(catalog, _)))
    )

  def read(value: JsValue): Either[Vector[StructureDiagnostic], Graph] =
    CmlSemanticMetadata.read(value) match {
      case Left(diagnostics) => Left(diagnostics.map(_publication_failure))
      case Right(envelope) => envelope.extensions.value.get(namespace) match {
        case Some(structure: JsObject) => _read_structure(envelope.catalog, structure).flatMap { case (elements, relations) =>
          CmlStructureMetadata._build(envelope.catalog, elements, relations, envelope.extensions - namespace, false)
        } match {
          case Right(graph) => Right(graph)
          case Left(diagnostics) => Left(diagnostics)
        }
        case _ => Left(Vector(_invalid_shape("extensions.cozy.cml.structure")))
      }
    }

  private def _read_structure(catalog: Catalog, value: JsObject): Either[Vector[StructureDiagnostic], (Vector[ElementProjection], Vector[RelationProjection])] =
    _schema(value).flatMap { _ =>
      _exact(value, Vector("schemaVersion", "elements", "relations"), "extensions.cozy.cml.structure").flatMap { _ =>
        for {
          elementvalue <- _field(value, "elements", "extensions.cozy.cml.structure.elements")
          elements <- _array(elementvalue, "extensions.cozy.cml.structure.elements", _element(catalog, _, _))
          relationvalue <- _field(value, "relations", "extensions.cozy.cml.structure.relations")
          relations <- _array(relationvalue, "extensions.cozy.cml.structure.relations", _relation(catalog, _, _))
        } yield (elements, relations)
      }
    } match {
      case Right(result) => Right(result)
      case Left(diagnostic) => Left(Vector(diagnostic))
    }

  private def _schema(value: JsObject): Result[Unit] =
    _field(value, "schemaVersion", "extensions.cozy.cml.structure.schemaVersion").flatMap {
      case JsString(version) if version == schemaVersion => Right(())
      case JsString(_) => Left(_diagnostic(UnsupportedSchemaVersion, "extensions.cozy.cml.structure.schemaVersion", "The Structure schema version is not supported."))
      case _ => Left(_invalid_shape("extensions.cozy.cml.structure.schemaVersion"))
    }

  private def _element(catalog: Catalog, value: JsValue, path: String): Result[ElementProjection] =
    _object(value, path).flatMap { objectvalue =>
      _exact(objectvalue, Vector("element", "kind", "aggregateBoundary"), path).flatMap { _ =>
        for {
          recordvalue <- _field(objectvalue, "element", path + ".element")
          record <- _record(catalog, recordvalue, path + ".element")
          kindvalue <- _field(objectvalue, "kind", path + ".kind")
          kind <- _element_kind(kindvalue, path + ".kind")
          boundaryvalue <- _field(objectvalue, "aggregateBoundary", path + ".aggregateBoundary")
          boundary <- _presence(boundaryvalue, path + ".aggregateBoundary", _aggregate_boundary)
        } yield ElementProjection(record, kind, boundary)
      }
    }

  private def _relation(catalog: Catalog, value: JsValue, path: String): Result[RelationProjection] =
    _object(value, path).flatMap { objectvalue =>
      _exact(objectvalue, Vector("element", "kind", "sourceEndpoint", "targetEndpoint", "semantics"), path).flatMap { _ =>
        for {
          recordvalue <- _field(objectvalue, "element", path + ".element")
          record <- _record(catalog, recordvalue, path + ".element")
          kindvalue <- _field(objectvalue, "kind", path + ".kind")
          kind <- _relation_kind(kindvalue, path + ".kind")
          sourcevalue <- _field(objectvalue, "sourceEndpoint", path + ".sourceEndpoint")
          source <- _endpoint(sourcevalue, path + ".sourceEndpoint")
          targetvalue <- _field(objectvalue, "targetEndpoint", path + ".targetEndpoint")
          target <- _endpoint(targetvalue, path + ".targetEndpoint")
          semanticsvalue <- _field(objectvalue, "semantics", path + ".semantics")
          semantics <- _semantics(semanticsvalue, path + ".semantics")
        } yield RelationProjection(record, kind, source, target, semantics)
      }
    }

  private def _record(catalog: Catalog, value: JsValue, path: String): Result[ElementRecord] = {
    val core = CmlSemanticMetadata.build(catalog, Json.obj()) match {
      case Right(envelope) => CmlSemanticMetadata.toJson(envelope).value.get("elements") match {
        case Some(JsArray(records)) => records.zip(catalog.elements)
        case _ => Vector.empty
      }
      case Left(_) => Vector.empty
    }
    core.find { case (json, _) => json == value } match {
      case Some((_, record)) => Right(record)
      case None => Left(_diagnostic(InvalidCoreBinding, path, "The embedded Structure record does not exactly match admitted core JSON."))
    }
  }

  private def _endpoint(value: JsValue, path: String): Result[Endpoint] =
    _object(value, path).flatMap { objectvalue =>
      _exact(objectvalue, Vector("target", "role", "cardinality", "navigable"), path).flatMap { _ =>
        for {
          targetvalue <- _field(objectvalue, "target", path + ".target")
          target <- _presence(targetvalue, path + ".target", _model_reference)
          rolevalue <- _field(objectvalue, "role", path + ".role")
          role <- _presence(rolevalue, path + ".role", _string)
          cardinalityvalue <- _field(objectvalue, "cardinality", path + ".cardinality")
          cardinality <- _presence(cardinalityvalue, path + ".cardinality", _cardinality)
          navigablevalue <- _field(objectvalue, "navigable", path + ".navigable")
          navigable <- _presence(navigablevalue, path + ".navigable", _boolean)
        } yield Endpoint(target, role, cardinality, navigable)
      }
    }

  private def _semantics(value: JsValue, path: String): Result[RelationSemantics] =
    _object(value, path).flatMap { objectvalue =>
      _exact(objectvalue, Vector("ownership", "independentExistence", "createPolicy", "deletePolicy", "reassignment", "reparenting", "lifecyclePropagation", "aggregateBoundary"), path).flatMap { _ =>
        for {
          ownership <- _presence_field(objectvalue, "ownership", path, _string)
          independent <- _presence_field(objectvalue, "independentExistence", path, _boolean)
          create <- _presence_field(objectvalue, "createPolicy", path, _string)
          delete <- _presence_field(objectvalue, "deletePolicy", path, _string)
          reassignment <- _presence_field(objectvalue, "reassignment", path, _boolean)
          reparenting <- _presence_field(objectvalue, "reparenting", path, _boolean)
          lifecycle <- _presence_field(objectvalue, "lifecyclePropagation", path, _strings)
          boundary <- _presence_field(objectvalue, "aggregateBoundary", path, _aggregate_boundary)
        } yield RelationSemantics(ownership, independent, create, delete, reassignment, reparenting, lifecycle, boundary)
      }
    }

  private def _presence_field[A](value: JsObject, name: String, path: String, decode: (JsValue, String) => Result[A]): Result[Presence[A]] =
    _field(value, name, path + "." + name).flatMap(_presence(_, path + "." + name, decode))

  private def _aggregate_boundary(value: JsValue, path: String): Result[AggregateBoundary] =
    _object(value, path).flatMap { objectvalue =>
      _exact(objectvalue, Vector("aggregate", "membership"), path).flatMap { _ =>
        for {
          aggregatevalue <- _field(objectvalue, "aggregate", path + ".aggregate")
          aggregate <- _presence(aggregatevalue, path + ".aggregate", _model_reference)
          membershipvalue <- _field(objectvalue, "membership", path + ".membership")
          membership <- _presence(membershipvalue, path + ".membership", _string)
        } yield AggregateBoundary(aggregate, membership)
      }
    }

  private def _model_reference(value: JsValue, path: String): Result[ModelReference] =
    _object(value, path).flatMap { objectvalue =>
      _exact(objectvalue, Vector("identity", "boundary"), path).flatMap { _ =>
        for {
          identityvalue <- _field(objectvalue, "identity", path + ".identity")
          identity <- _model_id(identityvalue, path + ".identity")
          boundaryvalue <- _field(objectvalue, "boundary", path + ".boundary")
          boundary <- _boundary(boundaryvalue, path + ".boundary")
        } yield ModelReference(identity, boundary)
      }
    }

  private def _cardinality(value: JsValue, path: String): Result[Cardinality] =
    _object(value, path).flatMap { objectvalue =>
      _exact(objectvalue, Vector("lower", "upper"), path).flatMap { _ =>
        for {
          lowervalue <- _field(objectvalue, "lower", path + ".lower")
          lower <- _integer(lowervalue, path + ".lower")
          uppervalue <- _field(objectvalue, "upper", path + ".upper")
          upper <- _optional_integer(uppervalue, path + ".upper")
        } yield Cardinality(lower, upper)
      }
    }

  private def _presence[A](value: JsValue, path: String, decode: (JsValue, String) => Result[A]): Result[Presence[A]] =
    _object(value, path).flatMap { objectvalue =>
      _field(objectvalue, "status", path + ".status").flatMap {
        case JsString("present") => _exact(objectvalue, Vector("status", "value"), path).flatMap(_ => _field(objectvalue, "value", path + ".value").flatMap(decode(_, path + ".value")).map(Present(_)))
        case JsString("absent") => _exact(objectvalue, Vector("status", "reason", "detail"), path).flatMap { _ =>
          for {
            reasonvalue <- _field(objectvalue, "reason", path + ".reason")
            reason <- _absence(reasonvalue, path + ".reason")
            detailvalue <- _field(objectvalue, "detail", path + ".detail")
            detail <- _string(detailvalue, path + ".detail")
          } yield Absent(reason, detail)
        }
        case _ => Left(_invalid_shape(path + ".status"))
      }
    }

  private def _element_kind(value: JsValue, path: String): Result[ElementKind] = value match { case JsString("entity") => Right(Entity); case JsString("value") => Right(Value); case JsString("aggregate") => Right(Aggregate); case _ => Left(_invalid_shape(path)) }
  private def _relation_kind(value: JsValue, path: String): Result[RelationKind] = value match { case JsString("composition") => Right(Composition); case JsString("aggregation") => Right(Aggregation); case JsString("association") => Right(Association); case _ => Left(_invalid_shape(path)) }
  private def _boundary(value: JsValue, path: String): Result[ReferenceBoundary] = value match { case JsString("local") => Right(Local); case JsString("external") => Right(External); case _ => Left(_invalid_shape(path)) }
  private def _absence(value: JsValue, path: String): Result[AbsenceReason] = value match { case JsString("not-declared") => Right(NotDeclared); case JsString("not-represented") => Right(NotRepresented); case JsString("unsupported") => Right(Unsupported); case JsString("not-applicable") => Right(NotApplicable); case _ => Left(_invalid_shape(path)) }
  private def _model_id(value: JsValue, path: String): Result[ModelElementId] = _object(value, path).flatMap(objectvalue => _exact(objectvalue, Vector("modelId", "elementId"), path).flatMap(_ => for { model <- _field(objectvalue, "modelId", path + ".modelId").flatMap(_string(_, path + ".modelId")); element <- _field(objectvalue, "elementId", path + ".elementId").flatMap(_string(_, path + ".elementId")) } yield ModelElementId(model, element)))
  private def _strings(value: JsValue, path: String): Result[Vector[String]] = _array(value, path, _string)
  private def _string(value: JsValue, path: String): Result[String] = value match { case JsString(text) => Right(text); case _ => Left(_invalid_shape(path)) }
  private def _boolean(value: JsValue, path: String): Result[Boolean] = value match { case JsBoolean(boolean) => Right(boolean); case _ => Left(_invalid_shape(path)) }
  private def _integer(value: JsValue, path: String): Result[Int] = value match { case JsNumber(number) if number.isWhole && number.isValidInt => Right(number.toInt); case _ => Left(_invalid_shape(path)) }
  private def _optional_integer(value: JsValue, path: String): Result[Option[Int]] = value match { case JsNull => Right(None); case _ => _integer(value, path).map(Some(_)) }
  private def _array[A](value: JsValue, path: String, decode: (JsValue, String) => Result[A]): Result[Vector[A]] = value match { case JsArray(values) => values.zipWithIndex.foldLeft[Result[Vector[A]]](Right(Vector.empty)) { case (Right(accumulator), (item, index)) => decode(item, path + "[" + index + "]").map(accumulator :+ _); case (left @ Left(_), _) => left }; case _ => Left(_invalid_shape(path)) }
  private def _object(value: JsValue, path: String): Result[JsObject] = value match { case objectvalue: JsObject => Right(objectvalue); case _ => Left(_invalid_shape(path)) }
  private def _field(value: JsObject, name: String, path: String): Result[JsValue] = value.value.get(name) match { case Some(field) => Right(field); case None => Left(_invalid_shape(path)) }
  private def _exact(value: JsObject, expected: Vector[String], path: String): Result[Unit] = if (value.value.keys.exists(key => !expected.contains(key))) Left(_invalid_shape(path)) else expected.find(name => !value.value.contains(name)) match { case Some(name) => Left(_invalid_shape(path + "." + name)); case None => Right(()) }

  private def _element_json(catalog: Catalog, value: ElementProjection): JsObject = Json.obj("element" -> _record_json(catalog, value.element), "kind" -> JsString(_element_kind_name(value.kind)), "aggregateBoundary" -> _presence_json(value.aggregateBoundary, _aggregate_boundary_json))
  private def _relation_json(catalog: Catalog, value: RelationProjection): JsObject = Json.obj("element" -> _record_json(catalog, value.element), "kind" -> JsString(_relation_kind_name(value.kind)), "sourceEndpoint" -> _endpoint_json(value.sourceEndpoint), "targetEndpoint" -> _endpoint_json(value.targetEndpoint), "semantics" -> _semantics_json(value.semantics))
  private def _record_json(catalog: Catalog, value: ElementRecord): JsObject = CmlSemanticMetadata.build(catalog, Json.obj()) match { case Right(envelope) => CmlSemanticMetadata.toJson(envelope).value.get("elements") match { case Some(JsArray(records)) => records.zip(catalog.elements).find { case (_, record) => record == value } match { case Some((json: JsObject, _)) => json; case _ => Json.obj() }; case _ => Json.obj() }; case Left(_) => Json.obj() }
  private def _endpoint_json(value: Endpoint): JsObject = Json.obj("target" -> _presence_json(value.target, _model_reference_json), "role" -> _presence_json(value.role, JsString), "cardinality" -> _presence_json(value.cardinality, _cardinality_json), "navigable" -> _presence_json(value.navigable, JsBoolean))
  private def _semantics_json(value: RelationSemantics): JsObject = Json.obj("ownership" -> _presence_json(value.ownership, JsString), "independentExistence" -> _presence_json(value.independentExistence, JsBoolean), "createPolicy" -> _presence_json(value.createPolicy, JsString), "deletePolicy" -> _presence_json(value.deletePolicy, JsString), "reassignment" -> _presence_json(value.reassignment, JsBoolean), "reparenting" -> _presence_json(value.reparenting, JsBoolean), "lifecyclePropagation" -> _presence_json(value.lifecyclePropagation, (values: Vector[String]) => JsArray(values.map(JsString))), "aggregateBoundary" -> _presence_json(value.aggregateBoundary, _aggregate_boundary_json))
  private def _aggregate_boundary_json(value: AggregateBoundary): JsObject = Json.obj("aggregate" -> _presence_json(value.aggregate, _model_reference_json), "membership" -> _presence_json(value.membership, JsString))
  private def _model_reference_json(value: ModelReference): JsObject = Json.obj("identity" -> Json.obj("modelId" -> JsString(value.identity.modelId), "elementId" -> JsString(value.identity.elementId)), "boundary" -> JsString(value.boundary match { case Local => "local"; case External => "external" }))
  private def _cardinality_json(value: Cardinality): JsObject = {
    val upper: JsValue = value.upper.map[JsValue](number => JsNumber(number)).getOrElse(JsNull)
    Json.obj("lower" -> JsNumber(value.lower), "upper" -> upper)
  }
  private def _presence_json[A](value: Presence[A], render: A => JsValue): JsObject = value match { case Present(present) => Json.obj("status" -> JsString("present"), "value" -> render(present)); case Absent(reason, detail) => Json.obj("status" -> JsString("absent"), "reason" -> JsString(_absence_name(reason)), "detail" -> JsString(detail)) }
  private def _element_kind_name(value: ElementKind): String = value match { case Entity => "entity"; case Value => "value"; case Aggregate => "aggregate" }
  private def _relation_kind_name(value: RelationKind): String = value match { case Composition => "composition"; case Aggregation => "aggregation"; case Association => "association" }
  private def _absence_name(value: AbsenceReason): String = value match { case NotDeclared => "not-declared"; case NotRepresented => "not-represented"; case Unsupported => "unsupported"; case NotApplicable => "not-applicable" }
  private def _invalid_shape(path: String): StructureDiagnostic = _diagnostic(InvalidShape, path, "The Structure value does not match the required v1 shape.")
}
