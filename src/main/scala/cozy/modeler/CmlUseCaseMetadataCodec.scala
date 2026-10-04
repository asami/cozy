package cozy.modeler

import play.api.libs.json.{JsArray, JsObject, JsString, JsValue, Json}

import CmlSemanticFoundation._
import CmlDynamicMetadata.{DeclaredExpression, ModelReference}
import CmlUseCaseMetadata._

/*
 * @since   Oct.  4, 2026
 * @version Oct.  4, 2026
 * @author  ASAMI, Tomoharu
 */
private[cozy] object CmlUseCaseMetadataCodec {
  private[modeler] type Result[A] = Either[UseCaseDiagnostic, A]
  private val _path = "extensions.cozy.cml.use-case"

  def useCaseJson(envelope: CmlSemanticMetadata.Envelope, actors: Vector[ActorProjection], useCases: Vector[UseCaseProjection]): JsObject = {
    val records = _record_pairs(envelope).map { case (json, record) => record -> json }.toMap
    Json.obj("schemaVersion" -> schemaVersion,
      "actors" -> JsArray(actors.map(value => CmlUseCaseProjectionCodec.actorJson(records, value))),
      "useCases" -> JsArray(useCases.map(value => CmlUseCaseProjectionCodec.useCaseJson(records, value))))
  }

  def read(value: JsValue): Either[Vector[UseCaseDiagnostic], Graph] = {
    CmlSemanticMetadata.read(value) match {
      case Left(diagnostics) => Left(diagnostics.map(_publication_failure))
      case Right(envelope) => envelope.extensions.value.get(namespace) match {
        case Some(child: JsObject) => _read_child(envelope, child) match {
          case Left(diagnostic) => Left(Vector(diagnostic))
          case Right((actors, usecases)) => CmlUseCaseMetadata.build(envelope.catalog, actors, usecases, envelope.extensions - namespace)
        }
        case _ => Left(Vector(_invalid_shape(_path)))
      }
    }
  }

  private def _read_child(envelope: CmlSemanticMetadata.Envelope, value: JsObject): Result[(Vector[ActorProjection], Vector[UseCaseProjection])] = {
    val records = _record_pairs(envelope).toMap
    for {
      version <- _field(value, "schemaVersion", _path + ".schemaVersion")
      _ <- version match {
        case JsString(text) if text == schemaVersion => Right(())
        case JsString(_) => Left(_diagnostic(UnsupportedSchemaVersion, _path + ".schemaVersion", "The Use Case schema version is not supported."))
        case _ => Left(_invalid_shape(_path + ".schemaVersion"))
      }
      _ <- _exact(value, Vector("schemaVersion", "actors", "useCases"), _path)
      actors <- _array_field(value, "actors", _path, CmlUseCaseProjectionCodec.actor(records, _, _))
      usecases <- _array_field(value, "useCases", _path, CmlUseCaseProjectionCodec.useCase(records, _, _))
    } yield (actors, usecases)
  }

  private def _record_pairs(envelope: CmlSemanticMetadata.Envelope): Vector[(JsValue, ElementRecord)] = {
    (CmlSemanticMetadata.toJson(envelope) \ "elements").as[Vector[JsValue]].zip(envelope.catalog.elements)
  }

  private[modeler] def _projection(value: JsValue, fields: Vector[String], path: String): Result[JsObject] = {
    value match {
      case objectvalue: JsObject => _exact(objectvalue, fields, path).map(_ => objectvalue)
      case _ => Left(_invalid_shape(path))
    }
  }

  private[modeler] def _field(value: JsObject, name: String, path: String): Result[JsValue] = {
    value.value.get(name).toRight(_invalid_shape(path))
  }

  private def _exact(value: JsObject, fields: Vector[String], path: String): Result[Unit] = {
    if (value.keys.exists(key => !fields.contains(key))) Left(_invalid_shape(path)) else {
      fields.find(name => !value.value.contains(name)) match {
        case Some(name) => Left(_invalid_shape(path + "." + name))
        case None => Right(())
      }
    }
  }

  private[modeler] def _record_field(records: Map[JsValue, ElementRecord], value: JsObject, path: String): Result[ElementRecord] = {
    _field(value, "element", path + ".element").flatMap { json =>
      records.get(json).toRight(_diagnostic(InvalidCoreBinding, path + ".element",
        "The embedded Use Case record does not exactly match admitted core JSON."))
    }
  }

  private[modeler] def _reference(value: JsValue, path: String): Result[ModelReference] = {
    for {
      objectvalue <- _projection(value, Vector("identity", "boundary"), path)
      identityvalue <- _field(objectvalue, "identity", path + ".identity")
      identity <- _projection(identityvalue, Vector("modelId", "elementId"), path + ".identity")
      model <- _field(identity, "modelId", path + ".identity.modelId").flatMap(_string(_, path + ".identity.modelId"))
      element <- _field(identity, "elementId", path + ".identity.elementId").flatMap(_string(_, path + ".identity.elementId"))
      boundaryvalue <- _field(objectvalue, "boundary", path + ".boundary")
      boundary <- boundaryvalue match {
        case JsString("local") => Right(Local)
        case JsString("external") => Right(External)
        case _ => Left(_invalid_shape(path + ".boundary"))
      }
    } yield ModelReference(ModelElementId(model, element), boundary)
  }

  private[modeler] def _references(value: JsValue, path: String): Result[Vector[ModelReference]] = {
    _array(value, path, _reference)
  }

  private[modeler] def _expression(value: JsValue, path: String): Result[DeclaredExpression] = {
    for {
      objectvalue <- _projection(value, Vector("text", "references"), path)
      text <- _field(objectvalue, "text", path + ".text").flatMap(_string(_, path + ".text"))
      references <- _presence_field(objectvalue, "references", path, _references)
    } yield DeclaredExpression(text, references)
  }

  private[modeler] def _expressions(value: JsValue, path: String): Result[Vector[DeclaredExpression]] = {
    _array(value, path, _expression)
  }

  private[modeler] def _presence_field[A](value: JsObject, name: String, path: String, decode: (JsValue, String) => Result[A]): Result[Presence[A]] = {
    val coordinate = path + "." + name
    _field(value, name, coordinate).flatMap {
      case objectvalue: JsObject => _field(objectvalue, "status", coordinate + ".status").flatMap {
        case JsString("present") => _exact(objectvalue, Vector("status", "value"), coordinate).flatMap { _ =>
          _field(objectvalue, "value", coordinate + ".value").flatMap(decode(_, coordinate + ".value")).map(Present(_))
        }
        case JsString("absent") =>
          for {
            _ <- _exact(objectvalue, Vector("status", "reason", "detail"), coordinate)
            reasonvalue <- _field(objectvalue, "reason", coordinate + ".reason")
            reason <- reasonvalue match {
              case JsString("not-declared") => Right(NotDeclared)
              case JsString("not-represented") => Right(NotRepresented)
              case JsString("unsupported") => Right(Unsupported)
              case JsString("not-applicable") => Right(NotApplicable)
              case _ => Left(_invalid_shape(coordinate + ".reason"))
            }
            detail <- _field(objectvalue, "detail", coordinate + ".detail").flatMap(_string(_, coordinate + ".detail"))
          } yield Absent(reason, detail)
        case _ => Left(_invalid_shape(coordinate + ".status"))
      }
      case _ => Left(_invalid_shape(coordinate))
    }
  }

  private[modeler] def _array_field[A](value: JsObject, name: String, path: String, decode: (JsValue, String) => Result[A]): Result[Vector[A]] = {
    _field(value, name, path + "." + name).flatMap(_array(_, path + "." + name, decode))
  }

  private[modeler] def _array[A](value: JsValue, path: String, decode: (JsValue, String) => Result[A]): Result[Vector[A]] = {
    value match {
      case JsArray(values) => values.zipWithIndex.foldLeft[Result[Vector[A]]](Right(Vector.empty)) {
        case (Right(accumulator), (item, index)) => decode(item, s"$path[$index]").map(accumulator :+ _)
        case (left @ Left(_), _) => left
      }
      case _ => Left(_invalid_shape(path))
    }
  }

  private[modeler] def _string(value: JsValue, path: String): Result[String] = {
    value match {
      case JsString(text) => Right(text)
      case _ => Left(_invalid_shape(path))
    }
  }

  private[modeler] def _presence_json[A](value: Presence[A], render: A => JsValue): JsObject = {
    value match {
      case Present(present) => Json.obj("status" -> "present", "value" -> render(present))
      case Absent(reason, detail) =>
        val tag = reason match {
          case NotDeclared => "not-declared"
          case NotRepresented => "not-represented"
          case Unsupported => "unsupported"
          case NotApplicable => "not-applicable"
        }
        Json.obj("status" -> "absent", "reason" -> tag, "detail" -> detail)
    }
  }

  private[modeler] def _reference_json(value: ModelReference): JsObject = {
    Json.obj("identity" -> Json.obj("modelId" -> value.identity.modelId, "elementId" -> value.identity.elementId),
      "boundary" -> (value.boundary match { case Local => "local"; case External => "external" }))
  }

  private[modeler] def _references_json(value: Vector[ModelReference]): JsArray = {
    JsArray(value.map(_reference_json))
  }

  private[modeler] def _expression_json(value: DeclaredExpression): JsObject = {
    Json.obj("text" -> value.text, "references" -> _presence_json(value.references, _references_json))
  }

  private[modeler] def _expressions_json(value: Vector[DeclaredExpression]): JsArray = {
    JsArray(value.map(_expression_json))
  }

  private[modeler] def _invalid_shape(path: String): UseCaseDiagnostic = {
    _diagnostic(InvalidShape, path, "The Use Case value does not match the required v1 shape.")
  }
}
