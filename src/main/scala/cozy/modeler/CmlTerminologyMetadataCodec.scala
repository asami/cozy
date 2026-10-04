package cozy.modeler

import play.api.libs.json.{JsArray, JsNull, JsObject, JsString, JsValue, Json}
import CmlSemanticFoundation._
import CmlTerminologyMetadata._
import CmlTerminologyProjectionCodec.Correspondence

/*
 * @since   Oct.  4, 2026
 * @version Oct.  4, 2026
 */
private[cozy] object CmlTerminologyMetadataCodec {
  private[modeler] type Result[A] = Either[TerminologyDiagnostic, A]

  def terminologyJson(
    correspondence: Correspondence, elements: Vector[ElementProjection], terms: Vector[TermProjection]
  ): JsObject = {
    Json.obj("schemaVersion" -> schemaVersion,
      "elements" -> JsArray(elements.map(CmlTerminologyProjectionCodec.elementJson(correspondence, _))),
      "terms" -> JsArray(terms.map(CmlTerminologyProjectionCodec.termJson(correspondence, _))))
  }

  def read(value: JsValue): Either[Vector[TerminologyDiagnostic], Graph] = {
    CmlSemanticMetadata.read(value) match {
      case Left(diagnostics) => Left(diagnostics.map(_publication_failure))
      case Right(envelope) => envelope.extensions.value.get(namespace) match {
        case Some(child: JsObject) => _read_child(envelope, child) match {
          case Left(diagnostic) => Left(Vector(diagnostic))
          case Right((elements, terms, correspondence)) =>
            CmlTerminologyMetadata._build(envelope.catalog, elements, terms,
              envelope.extensions - namespace, Some(correspondence))
        }
        case _ => Left(Vector(_invalid_shape("extensions.cozy.cml.terminology")))
      }
    }
  }

  private def _read_child(
    envelope: CmlSemanticMetadata.Envelope, value: JsObject
  ): Result[(Vector[ElementProjection], Vector[TermProjection], Correspondence)] = {
    // Correspondence is constructed only after the child version has been admitted.
    for {
      version <- _field(value, "schemaVersion", "schemaVersion")
      _ <- version match {
        case JsString(text) if text == schemaVersion => Right(())
        case JsString(_) => Left(_diagnostic(UnsupportedSchemaVersion, "schemaVersion",
          "The Terminology schema version is not supported."))
        case _ => Left(_invalid_shape("schemaVersion"))
      }
      _ <- _exact(value, Vector("schemaVersion", "elements", "terms"), "extensions.cozy.cml.terminology")
      correspondence = CmlTerminologyProjectionCodec.correspondence(envelope)
      elements <- _array_field(value, "elements", "elements",
        CmlTerminologyProjectionCodec.element(correspondence, _, _))
      terms <- _array_field(value, "terms", "terms",
        CmlTerminologyProjectionCodec.term(correspondence, _, _))
    } yield (elements, terms, correspondence)
  }

  private[modeler] def _object(value: JsValue, fields: Vector[String], path: String): Result[JsObject] = {
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
        case Some(name) => Left(_invalid_shape(if (path == "extensions.cozy.cml.terminology") name else path + "." + name))
        case None => Right(())
      }
    }
  }

  private[modeler] def _presence_field[A](
    value: JsObject, name: String, path: String, decode: (JsValue, String) => Result[A]
  ): Result[Presence[A]] = {
    val coordinate = path + "." + name
    _field(value, name, coordinate).flatMap {
      case objectvalue: JsObject => _field(objectvalue, "status", coordinate + ".status").flatMap {
        case JsString("present") =>
          for {
            _ <- _exact(objectvalue, Vector("status", "value"), coordinate)
            payload <- _field(objectvalue, "value", coordinate + ".value")
            decoded <- decode(payload, coordinate)
          } yield Present(decoded)
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

  private[modeler] def _array_field[A](
    value: JsObject, name: String, path: String, decode: (JsValue, String) => Result[A]
  ): Result[Vector[A]] = {
    _field(value, name, path).flatMap(_array(_, path, decode))
  }

  private[modeler] def _array[A](
    value: JsValue, path: String, decode: (JsValue, String) => Result[A]
  ): Result[Vector[A]] = {
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

  private[modeler] def _kind(value: JsValue, path: String): Result[BokKind] = {
    value match {
      case JsString("mono") => Right(Mono)
      case JsString("koto") => Right(Koto)
      case _ => Left(_invalid_shape(path))
    }
  }

  private[modeler] def _label(value: JsValue, path: String): Result[LocalizedLabel] = {
    for {
      objectvalue <- _object(value, Vector("text", "language"), path)
      text <- _field(objectvalue, "text", path + ".text").flatMap(_string(_, path + ".text"))
      languagevalue <- _field(objectvalue, "language", path + ".language")
      language <- languagevalue match {
        case JsNull => Right(None)
        case JsString(text) => Right(Some(text))
        case _ => Left(_invalid_shape(path + ".language"))
      }
    } yield LocalizedLabel(text, language)
  }

  private[modeler] def _presence_json[A](value: Presence[A], render: A => JsValue): JsObject = {
    value match {
      case Present(payload) => Json.obj("status" -> "present", "value" -> render(payload))
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

  private[modeler] def _kind_json(value: BokKind): JsString = {
    JsString(value match { case Mono => "mono"; case Koto => "koto" })
  }

  private[modeler] def _label_json(value: LocalizedLabel): JsObject = {
    Json.obj("text" -> value.text, "language" -> value.language.fold[JsValue](JsNull)(JsString))
  }

  private[modeler] def _invalid_shape(path: String): TerminologyDiagnostic = {
    _diagnostic(InvalidShape, path, "The Terminology value does not match the required v1 shape.")
  }
}
