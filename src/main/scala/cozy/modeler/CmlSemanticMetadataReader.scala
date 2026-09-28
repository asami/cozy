package cozy.modeler

import play.api.libs.json.{JsArray, JsNull, JsNumber, JsObject, JsString, JsValue}

import CmlSemanticFoundation._
import CmlSemanticMetadata._

/*
 * @since   Sep. 28, 2026
 * @version Sep. 28, 2026
 * @author  ASAMI, Tomoharu
 */
private[cozy] object CmlSemanticMetadataReader {
  private type Result[A] = Either[PublicationDiagnostic, A]

  def read(value: JsValue): Either[Vector[PublicationDiagnostic], Envelope] =
    _root(value) match {
      case Right((elements, terms, extensions)) =>
        CmlSemanticFoundation.build(elements, terms) match {
          case Right(catalog) => CmlSemanticMetadata.build(catalog, extensions)
          case Left(diagnostics) => Left(diagnostics.map(diagnostic => PublicationDiagnostic(
            InvalidSemanticCatalog,
            diagnostic.path,
            "The decoded catalog violates an admitted semantic invariant.",
            Some(diagnostic)
          )))
        }
      case Left(diagnostic) => Left(Vector(diagnostic))
    }

  private def _root(value: JsValue): Result[(Vector[ElementRecord], Vector[TermRecord], JsObject)] =
    _object(value, "root").flatMap { objectvalue =>
      _schema_version(objectvalue).flatMap { _ =>
        _exact_fields(objectvalue, Vector("schemaVersion", "elements", "terms", "extensions"), "root").flatMap { _ =>
          for {
            elementvalue <- _field(objectvalue, "elements", "elements")
            elements <- _array(elementvalue, "elements", _element)
            termvalue <- _field(objectvalue, "terms", "terms")
            terms <- _array(termvalue, "terms", _term)
            extensionvalue <- _field(objectvalue, "extensions", "extensions")
            extensions <- _object(extensionvalue, "extensions")
          } yield (elements, terms, extensions)
        }
      }
    }

  private def _schema_version(value: JsObject): Result[Unit] =
    _field(value, "schemaVersion", "schemaVersion").flatMap {
      case JsString(schema) if schema == CmlSemanticMetadata.schemaVersion => Right(())
      case JsString(_) => Left(PublicationDiagnostic(
        UnsupportedSchemaVersion,
        "schemaVersion",
        "The schema version is not supported by this reader.",
        None
      ))
      case _ => Left(_invalid_shape("schemaVersion"))
    }

  private def _element(value: JsValue, path: String): Result[ElementRecord] =
    _object(value, path).flatMap { objectvalue =>
      _exact_fields(objectvalue, Vector("identity", "kind", "name", "source", "references"), path).flatMap { _ =>
        for {
          identityvalue <- _field(objectvalue, "identity", s"$path.identity")
          identity <- _presence(identityvalue, s"$path.identity", _model_element_id)
          kindvalue <- _field(objectvalue, "kind", s"$path.kind")
          kind <- _string(kindvalue, s"$path.kind")
          namevalue <- _field(objectvalue, "name", s"$path.name")
          name <- _string(namevalue, s"$path.name")
          sourcevalue <- _field(objectvalue, "source", s"$path.source")
          source <- _source(sourcevalue, s"$path.source")
          referencesvalue <- _field(objectvalue, "references", s"$path.references")
          references <- _presence(referencesvalue, s"$path.references", _references)
        } yield ElementRecord(identity, kind, name, source, references)
      }
    }

  private def _term(value: JsValue, path: String): Result[TermRecord] =
    _object(value, path).flatMap { objectvalue =>
      _exact_fields(objectvalue, Vector("identity", "source"), path).flatMap { _ =>
        for {
          identityvalue <- _field(objectvalue, "identity", s"$path.identity")
          identity <- _term_id(identityvalue, s"$path.identity")
          sourcevalue <- _field(objectvalue, "source", s"$path.source")
          source <- _source(sourcevalue, s"$path.source")
        } yield TermRecord(identity, source)
      }
    }

  private def _references(value: JsValue, path: String): Result[Vector[SemanticReference]] =
    _array(value, path, _reference)

  private def _reference(value: JsValue, path: String): Result[SemanticReference] =
    _object(value, path).flatMap { objectvalue =>
      _exact_fields(objectvalue, Vector("target", "boundary", "relation", "profile", "context", "preferredLabel", "source", "origin"), path).flatMap { _ =>
        for {
          targetvalue <- _field(objectvalue, "target", s"$path.target")
          target <- _target(targetvalue, s"$path.target")
          boundaryvalue <- _field(objectvalue, "boundary", s"$path.boundary")
          boundary <- _boundary(boundaryvalue, s"$path.boundary")
          relationvalue <- _field(objectvalue, "relation", s"$path.relation")
          relation <- _presence(relationvalue, s"$path.relation", _relation_id)
          profilevalue <- _field(objectvalue, "profile", s"$path.profile")
          profile <- _presence(profilevalue, s"$path.profile", _profile_id)
          contextvalue <- _field(objectvalue, "context", s"$path.context")
          context <- _presence(contextvalue, s"$path.context", _string)
          labelvalue <- _field(objectvalue, "preferredLabel", s"$path.preferredLabel")
          label <- _presence(labelvalue, s"$path.preferredLabel", _label)
          sourcevalue <- _field(objectvalue, "source", s"$path.source")
          source <- _source(sourcevalue, s"$path.source")
          originvalue <- _field(objectvalue, "origin", s"$path.origin")
          origin <- _origin(originvalue, s"$path.origin")
        } yield SemanticReference(target, boundary, relation, profile, context, label, source, origin)
      }
    }

  private def _target(value: JsValue, path: String): Result[SemanticTarget] =
    _object(value, path).flatMap { objectvalue =>
      _exact_fields(objectvalue, Vector("kind", "identity"), path).flatMap { _ =>
        _field(objectvalue, "kind", s"$path.kind").flatMap {
          case JsString("model-element") => _field(objectvalue, "identity", s"$path.identity").flatMap(_model_element_id(_, s"$path.identity")).map(ModelTarget)
          case JsString("term") => _field(objectvalue, "identity", s"$path.identity").flatMap(_term_id(_, s"$path.identity")).map(TermTarget)
          case _ => Left(_invalid_shape(s"$path.kind"))
        }
      }
    }

  private def _boundary(value: JsValue, path: String): Result[ReferenceBoundary] =
    value match {
      case JsString("local") => Right(Local)
      case JsString("external") => Right(External)
      case _ => Left(_invalid_shape(path))
    }

  private def _origin(value: JsValue, path: String): Result[ReferenceOrigin] =
    _object(value, path).flatMap { objectvalue =>
      _no_extra_fields(objectvalue, Vector("kind", "ruleId", "sources"), path).flatMap { _ =>
        _field(objectvalue, "kind", s"$path.kind").flatMap {
          case JsString("declared") => _exact_fields(objectvalue, Vector("kind"), path).map(_ => Declared)
          case JsString("derived") => _exact_fields(objectvalue, Vector("kind", "ruleId", "sources"), path).flatMap { _ =>
            for {
              rulevalue <- _field(objectvalue, "ruleId", s"$path.ruleId")
              ruleid <- _string(rulevalue, s"$path.ruleId")
              sourcesvalue <- _field(objectvalue, "sources", s"$path.sources")
              sources <- _array(sourcesvalue, s"$path.sources", _source)
            } yield Derived(ruleid, sources)
          }
          case _ => Left(_invalid_shape(s"$path.kind"))
        }
      }
    }

  private def _presence[A](
    value: JsValue,
    path: String,
    decode: (JsValue, String) => Result[A]
  ): Result[Presence[A]] =
    _object(value, path).flatMap { objectvalue =>
      _no_extra_fields(objectvalue, Vector("status", "value", "reason", "detail"), path).flatMap { _ =>
        _field(objectvalue, "status", s"$path.status").flatMap {
          case JsString("present") => _exact_fields(objectvalue, Vector("status", "value"), path).flatMap { _ =>
            _field(objectvalue, "value", s"$path.value").flatMap(decode(_, s"$path.value")).map(Present(_))
          }
          case JsString("absent") => _exact_fields(objectvalue, Vector("status", "reason", "detail"), path).flatMap { _ =>
            for {
              reasonvalue <- _field(objectvalue, "reason", s"$path.reason")
              reason <- _absence_reason(reasonvalue, s"$path.reason")
              detailvalue <- _field(objectvalue, "detail", s"$path.detail")
              detail <- _string(detailvalue, s"$path.detail")
            } yield Absent(reason, detail)
          }
          case _ => Left(_invalid_shape(s"$path.status"))
        }
      }
    }

  private def _model_element_id(value: JsValue, path: String): Result[ModelElementId] =
    _identity(value, path, "modelId", "elementId").map { case (modelid, elementid) => ModelElementId(modelid, elementid) }

  private def _term_id(value: JsValue, path: String): Result[TermId] =
    _identity(value, path, "vocabularyId", "termId").map { case (vocabularyid, termid) => TermId(vocabularyid, termid) }

  private def _relation_id(value: JsValue, path: String): Result[RelationId] =
    _identity(value, path, "vocabularyId", "relationId").map { case (vocabularyid, relationid) => RelationId(vocabularyid, relationid) }

  private def _profile_id(value: JsValue, path: String): Result[ProfileId] =
    _identity(value, path, "vocabularyId", "profileId").map { case (vocabularyid, profileid) => ProfileId(vocabularyid, profileid) }

  private def _identity(value: JsValue, path: String, first: String, second: String): Result[(String, String)] =
    _object(value, path).flatMap { objectvalue =>
      _exact_fields(objectvalue, Vector(first, second), path).flatMap { _ =>
        for {
          firstvalue <- _field(objectvalue, first, s"$path.$first")
          firsttext <- _string(firstvalue, s"$path.$first")
          secondvalue <- _field(objectvalue, second, s"$path.$second")
          secondtext <- _string(secondvalue, s"$path.$second")
        } yield (firsttext, secondtext)
      }
    }

  private def _source(value: JsValue, path: String): Result[SourceAttribution] =
    _object(value, path).flatMap { objectvalue =>
      _exact_fields(objectvalue, Vector("authorityId", "path", "sha256", "line"), path).flatMap { _ =>
        for {
          authorityvalue <- _field(objectvalue, "authorityId", s"$path.authorityId")
          authorityid <- _string(authorityvalue, s"$path.authorityId")
          pathvalue <- _field(objectvalue, "path", s"$path.path")
          sourcename <- _string(pathvalue, s"$path.path")
          digestvalue <- _field(objectvalue, "sha256", s"$path.sha256")
          digest <- _string(digestvalue, s"$path.sha256")
          linevalue <- _field(objectvalue, "line", s"$path.line")
          line <- _line(linevalue, s"$path.line")
        } yield SourceAttribution(authorityid, sourcename, digest, line)
      }
    }

  private def _label(value: JsValue, path: String): Result[LocalizedLabel] =
    _object(value, path).flatMap { objectvalue =>
      _exact_fields(objectvalue, Vector("text", "language"), path).flatMap { _ =>
        for {
          textvalue <- _field(objectvalue, "text", s"$path.text")
          text <- _string(textvalue, s"$path.text")
          languagevalue <- _field(objectvalue, "language", s"$path.language")
          language <- _optional_string(languagevalue, s"$path.language")
        } yield LocalizedLabel(text, language)
      }
    }

  private def _line(value: JsValue, path: String): Result[Option[Int]] =
    value match {
      case JsNull => Right(None)
      case JsNumber(number) if number.isWhole && number.isValidInt => Right(Some(number.toInt))
      case _ => Left(_invalid_shape(path))
    }

  private def _optional_string(value: JsValue, path: String): Result[Option[String]] =
    value match {
      case JsNull => Right(None)
      case JsString(text) => Right(Some(text))
      case _ => Left(_invalid_shape(path))
    }

  private def _string(value: JsValue, path: String): Result[String] =
    value match {
      case JsString(text) => Right(text)
      case _ => Left(_invalid_shape(path))
    }

  private def _array[A](value: JsValue, path: String, decode: (JsValue, String) => Result[A]): Result[Vector[A]] =
    value match {
      case JsArray(values) => values.zipWithIndex.foldLeft[Result[Vector[A]]](Right(Vector.empty)) {
        case (Right(accumulator), (item, index)) => decode(item, s"$path[$index]").map(accumulator :+ _)
        case (left @ Left(_), _) => left
      }
      case _ => Left(_invalid_shape(path))
    }

  private def _object(value: JsValue, path: String): Result[JsObject] =
    value match {
      case objectvalue: JsObject => Right(objectvalue)
      case _ => Left(_invalid_shape(path))
    }

  private def _field(value: JsObject, name: String, path: String): Result[JsValue] =
    value.value.get(name) match {
      case Some(field) => Right(field)
      case None => Left(_invalid_shape(path))
    }

  private def _exact_fields(value: JsObject, expected: Vector[String], path: String): Result[Unit] =
    _no_extra_fields(value, expected, path).flatMap { _ =>
      expected.find(name => !value.value.contains(name)) match {
        case Some(name) => Left(_invalid_shape(s"$path.$name"))
        case None => Right(())
      }
    }

  private def _no_extra_fields(value: JsObject, allowed: Vector[String], path: String): Result[Unit] =
    if (value.value.keys.exists(key => !allowed.contains(key)))
      Left(_invalid_shape(path))
    else
      Right(())

  private def _absence_reason(value: JsValue, path: String): Result[AbsenceReason] =
    value match {
      case JsString("not-declared") => Right(NotDeclared)
      case JsString("not-represented") => Right(NotRepresented)
      case JsString("unsupported") => Right(Unsupported)
      case JsString("not-applicable") => Right(NotApplicable)
      case _ => Left(_invalid_shape(path))
    }

  private def _invalid_shape(path: String): PublicationDiagnostic =
    PublicationDiagnostic(
      InvalidShape,
      path,
      "The semantic-metadata value does not match the required v1 shape.",
      None
    )
}
