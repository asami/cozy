package cozy.modeler

import play.api.libs.json.{JsArray, JsObject, JsString, JsValue, Json}

import CmlSemanticFoundation._
import CmlClassificationMetadata._

/*
 * @since   Oct. 3, 2026
 * @version Oct.  3, 2026
 * @author  ASAMI, Tomoharu
 */
private[cozy] object CmlClassificationMetadataCodec {
  private type Result[A] = Either[ClassificationDiagnostic, A]
  private val _path = "extensions.cozy.cml.classification"

  def classificationJson(
    envelope: CmlSemanticMetadata.Envelope,
    traits: Vector[TraitProjection],
    powertypes: Vector[PowertypeProjection],
    generalizations: Vector[GeneralizationProjection],
    traitRealizations: Vector[TraitRealizationProjection],
    powertypeClassifications: Vector[PowertypeClassificationProjection]
  ): JsObject = {
    val records = _record_pairs(envelope).map { case (json, record) => record -> json }.toMap
    Json.obj(
      "schemaVersion" -> schemaVersion,
      "traits" -> JsArray(traits.map(value => Json.obj("element" -> records(value.element)))),
      "powertypes" -> JsArray(powertypes.map(value => Json.obj(
        "element" -> records(value.element), "members" -> _presence_json(value.members, _members_json)
      ))),
      "generalizations" -> JsArray(generalizations.map(value => Json.obj(
        "element" -> records(value.element), "subtype" -> _presence_json(value.subtype, _reference_json),
        "supertype" -> _presence_json(value.supertype, _reference_json)
      ))),
      "traitRealizations" -> JsArray(traitRealizations.map(value => Json.obj(
        "element" -> records(value.element), "subject" -> _presence_json(value.subject, _reference_json),
        "traitTarget" -> _presence_json(value.traitTarget, _reference_json)
      ))),
      "powertypeClassifications" -> JsArray(powertypeClassifications.map(value => Json.obj(
        "element" -> records(value.element), "subject" -> _presence_json(value.subject, _reference_json),
        "powertype" -> _presence_json(value.powertype, _reference_json)
      )))
    )
  }

  def read(value: JsValue): Either[Vector[ClassificationDiagnostic], Graph] =
    CmlSemanticMetadata.read(value) match {
      case Left(diagnostics) => Left(diagnostics.map(_publication_failure))
      case Right(envelope) => envelope.extensions.value.get(namespace) match {
        case Some(child: JsObject) =>
          _read_child(envelope, child) match {
            case Left(diagnostic) => Left(Vector(diagnostic))
            case Right((traits, powertypes, generalizations, realizations, classifications)) =>
              CmlClassificationMetadata.build(envelope.catalog, traits, powertypes, generalizations,
                realizations, classifications, envelope.extensions - namespace)
          }
        case _ => Left(Vector(_invalid_shape(_path)))
      }
    }

  private def _read_child(
    envelope: CmlSemanticMetadata.Envelope,
    value: JsObject
  ): Result[(Vector[TraitProjection], Vector[PowertypeProjection], Vector[GeneralizationProjection], Vector[TraitRealizationProjection], Vector[PowertypeClassificationProjection])] = {
    val records = _record_pairs(envelope).toMap
    for {
      _ <- _schema(value)
      _ <- _exact(value, Vector("schemaVersion", "traits", "powertypes", "generalizations", "traitRealizations", "powertypeClassifications"), _path)
      traits <- _array_field(value, "traits", _path, _trait(records, _, _))
      powertypes <- _array_field(value, "powertypes", _path, _powertype(records, _, _))
      generalizations <- _array_field(value, "generalizations", _path, _generalization(records, _, _))
      realizations <- _array_field(value, "traitRealizations", _path, _realization(records, _, _))
      classifications <- _array_field(value, "powertypeClassifications", _path, _classification(records, _, _))
    } yield (traits, powertypes, generalizations, realizations, classifications)
  }

  private def _record_pairs(envelope: CmlSemanticMetadata.Envelope): Vector[(JsValue, ElementRecord)] =
    (CmlSemanticMetadata.toJson(envelope) \ "elements").as[Vector[JsValue]].zip(envelope.catalog.elements)

  private def _schema(value: JsObject): Result[Unit] =
    _field(value, "schemaVersion", _path + ".schemaVersion").flatMap {
      case JsString(version) if version == schemaVersion => Right(())
      case JsString(_) => Left(_diagnostic(UnsupportedSchemaVersion, _path + ".schemaVersion", "The Classification schema version is not supported."))
      case _ => Left(_invalid_shape(_path + ".schemaVersion"))
    }

  private def _trait(records: Map[JsValue, ElementRecord], value: JsValue, path: String): Result[TraitProjection] =
    _projection(value, Vector("element"), path).flatMap(objectvalue =>
      _record_field(records, objectvalue, path).map(TraitProjection))

  private def _powertype(records: Map[JsValue, ElementRecord], value: JsValue, path: String): Result[PowertypeProjection] =
    _projection(value, Vector("element", "members"), path).flatMap { objectvalue =>
      for {
        record <- _record_field(records, objectvalue, path)
        members <- _presence_field(objectvalue, "members", path, _members)
      } yield PowertypeProjection(record, members)
    }

  private def _generalization(records: Map[JsValue, ElementRecord], value: JsValue, path: String): Result[GeneralizationProjection] =
    _projection(value, Vector("element", "subtype", "supertype"), path).flatMap { objectvalue =>
      for {
        record <- _record_field(records, objectvalue, path)
        subtype <- _presence_field(objectvalue, "subtype", path, _reference)
        supertype <- _presence_field(objectvalue, "supertype", path, _reference)
      } yield GeneralizationProjection(record, subtype, supertype)
    }

  private def _realization(records: Map[JsValue, ElementRecord], value: JsValue, path: String): Result[TraitRealizationProjection] =
    _projection(value, Vector("element", "subject", "traitTarget"), path).flatMap { objectvalue =>
      for {
        record <- _record_field(records, objectvalue, path)
        subject <- _presence_field(objectvalue, "subject", path, _reference)
        target <- _presence_field(objectvalue, "traitTarget", path, _reference)
      } yield TraitRealizationProjection(record, subject, target)
    }

  private def _classification(records: Map[JsValue, ElementRecord], value: JsValue, path: String): Result[PowertypeClassificationProjection] =
    _projection(value, Vector("element", "subject", "powertype"), path).flatMap { objectvalue =>
      for {
        record <- _record_field(records, objectvalue, path)
        subject <- _presence_field(objectvalue, "subject", path, _reference)
        powertype <- _presence_field(objectvalue, "powertype", path, _reference)
      } yield PowertypeClassificationProjection(record, subject, powertype)
    }

  private def _projection(value: JsValue, fields: Vector[String], path: String): Result[JsObject] =
    _object(value, path).flatMap(objectvalue => _exact(objectvalue, fields, path).map(_ => objectvalue))

  private def _record_field(records: Map[JsValue, ElementRecord], value: JsObject, path: String): Result[ElementRecord] =
    _field(value, "element", path + ".element").flatMap { json =>
      records.get(json) match {
        case Some(record) => Right(record)
        case None => Left(_diagnostic(InvalidCoreBinding, path + ".element", "The embedded Classification record does not exactly match admitted core JSON."))
      }
    }

  private def _reference(value: JsValue, path: String): Result[ModelReference] =
    _projection(value, Vector("identity", "boundary"), path).flatMap { objectvalue =>
      for {
        identityvalue <- _field(objectvalue, "identity", path + ".identity")
        identity <- _identity(identityvalue, path + ".identity")
        boundaryvalue <- _field(objectvalue, "boundary", path + ".boundary")
        boundary <- _boundary(boundaryvalue, path + ".boundary")
      } yield ModelReference(identity, boundary)
    }

  private def _identity(value: JsValue, path: String): Result[ModelElementId] =
    _projection(value, Vector("modelId", "elementId"), path).flatMap { objectvalue =>
      for {
        model <- _field(objectvalue, "modelId", path + ".modelId").flatMap(_string(_, path + ".modelId"))
        element <- _field(objectvalue, "elementId", path + ".elementId").flatMap(_string(_, path + ".elementId"))
      } yield ModelElementId(model, element)
    }

  private def _members(value: JsValue, path: String): Result[Vector[ModelReference]] =
    _array(value, path, _reference)

  private def _presence_field[A](value: JsObject, name: String, path: String, decode: (JsValue, String) => Result[A]): Result[Presence[A]] =
    _field(value, name, path + "." + name).flatMap(_presence(_, path + "." + name, decode))

  private def _presence[A](value: JsValue, path: String, decode: (JsValue, String) => Result[A]): Result[Presence[A]] =
    _object(value, path).flatMap { objectvalue =>
      _field(objectvalue, "status", path + ".status").flatMap {
        case JsString("present") =>
          _exact(objectvalue, Vector("status", "value"), path).flatMap(_ =>
            _field(objectvalue, "value", path + ".value").flatMap(decode(_, path + ".value")).map(Present(_)))
        case JsString("absent") =>
          _exact(objectvalue, Vector("status", "reason", "detail"), path).flatMap { _ =>
            for {
              reasonvalue <- _field(objectvalue, "reason", path + ".reason")
              reason <- _absence(reasonvalue, path + ".reason")
              detail <- _field(objectvalue, "detail", path + ".detail").flatMap(_string(_, path + ".detail"))
            } yield Absent(reason, detail)
          }
        case _ => Left(_invalid_shape(path + ".status"))
      }
    }

  private def _array_field[A](value: JsObject, name: String, path: String, decode: (JsValue, String) => Result[A]): Result[Vector[A]] =
    _field(value, name, path + "." + name).flatMap(_array(_, path + "." + name, decode))

  private def _array[A](value: JsValue, path: String, decode: (JsValue, String) => Result[A]): Result[Vector[A]] = value match {
    case JsArray(values) => values.zipWithIndex.foldLeft[Result[Vector[A]]](Right(Vector.empty)) {
      case (Right(accumulator), (item, index)) => decode(item, s"$path[$index]").map(accumulator :+ _)
      case (left @ Left(_), _) => left
    }
    case _ => Left(_invalid_shape(path))
  }

  private def _boundary(value: JsValue, path: String): Result[ReferenceBoundary] = value match {
    case JsString("local") => Right(Local)
    case JsString("external") => Right(External)
    case _ => Left(_invalid_shape(path))
  }

  private def _absence(value: JsValue, path: String): Result[AbsenceReason] = value match {
    case JsString("not-declared") => Right(NotDeclared)
    case JsString("not-represented") => Right(NotRepresented)
    case JsString("unsupported") => Right(Unsupported)
    case JsString("not-applicable") => Right(NotApplicable)
    case _ => Left(_invalid_shape(path))
  }

  private def _string(value: JsValue, path: String): Result[String] = value match {
    case JsString(text) => Right(text)
    case _ => Left(_invalid_shape(path))
  }

  private def _object(value: JsValue, path: String): Result[JsObject] = value match {
    case objectvalue: JsObject => Right(objectvalue)
    case _ => Left(_invalid_shape(path))
  }

  private def _field(value: JsObject, name: String, path: String): Result[JsValue] =
    value.value.get(name).toRight(_invalid_shape(path))

  private def _exact(value: JsObject, expected: Vector[String], path: String): Result[Unit] =
    if (value.keys.exists(key => !expected.contains(key))) Left(_invalid_shape(path)) else
      expected.find(name => !value.value.contains(name)) match {
        case Some(name) => Left(_invalid_shape(path + "." + name))
        case None => Right(())
      }

  private def _reference_json(value: ModelReference): JsObject = Json.obj(
    "identity" -> Json.obj("modelId" -> value.identity.modelId, "elementId" -> value.identity.elementId),
    "boundary" -> (value.boundary match { case Local => "local"; case External => "external" })
  )

  private def _members_json(value: Vector[ModelReference]): JsArray = JsArray(value.map(_reference_json))

  private def _presence_json[A](value: Presence[A], render: A => JsValue): JsObject = value match {
    case Present(present) => Json.obj("status" -> "present", "value" -> render(present))
    case Absent(reason, detail) => Json.obj("status" -> "absent", "reason" -> _absence_name(reason), "detail" -> detail)
  }

  private def _absence_name(value: AbsenceReason): String = value match {
    case NotDeclared => "not-declared"
    case NotRepresented => "not-represented"
    case Unsupported => "unsupported"
    case NotApplicable => "not-applicable"
  }

  private def _invalid_shape(path: String): ClassificationDiagnostic =
    _diagnostic(InvalidShape, path, "The Classification value does not match the required v1 shape.")
}
