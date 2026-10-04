package cozy.modeler

import CmlSemanticFoundation._
import CmlTerminologyMetadata._

/*
 * @since   Oct.  4, 2026
 * @version Oct.  4, 2026
 */
private[cozy] object CmlTerminologyMetadataValidation {
  def validate(
    catalog: Catalog,
    elements: Vector[ElementProjection],
    terms: Vector[TermProjection]
  ): Either[Vector[TerminologyDiagnostic], Unit] = {
    val elementdiagnostics = if (elements == null) Vector(_shape("elements")) else {
      val remaining = scala.collection.mutable.Map(_counts(catalog.elements).toSeq: _*)
      val declared = elements.zipWithIndex.flatMap { case (projection, index) =>
        val path = s"elements[$index]"
        if (projection == null) Vector(_shape(path)) else {
          val binding = _binding(projection.element, catalog.elements, remaining, path + ".element")
          val references = _references(projection.termReferences, path + ".termReferences")
          val groups = _references(projection.conceptualGroups, path + ".conceptualGroups")
          val semantics = if (projection.element == null || !catalog.elements.contains(projection.element)) {
            Vector.empty
          } else {
            val expected: Presence[Vector[SemanticReference]] = projection.element.references match {
              case Present(values) => Present(values.filter(_.target.isInstanceOf[TermTarget]))
              case absence: Absent => absence
            }
            val filter = if (references.isEmpty && projection.termReferences != expected) {
              Vector(_semantics(path + ".termReferences"))
            } else Vector.empty
            val subset = projection.conceptualGroups match {
              case Present(values) if groups.isEmpty =>
                val admitted = projection.element.references match {
                  case Present(refs) => _counts(refs)
                  case _: Absent => Map.empty[SemanticReference, Int]
                }
                if (_counts(values).exists { case (ref, count) => count > admitted.getOrElse(ref, 0) }) {
                  Vector(_semantics(path + ".conceptualGroups"))
                } else Vector.empty
              case _ => Vector.empty
            }
            filter ++ subset
          }
          // Declaration fields retain their diagnostic order even when binding fails.
          binding ++ references ++ semantics.filter(_.path.endsWith(".termReferences")) ++
            groups ++ semantics.filter(_.path.endsWith(".conceptualGroups"))
        }
      }
      declared ++ _missing(catalog.elements, remaining, "elements")
    }
    val termdiagnostics = if (terms == null) Vector(_shape("terms")) else {
      val remaining = scala.collection.mutable.Map(_counts(catalog.terms).toSeq: _*)
      val declared = terms.zipWithIndex.flatMap { case (projection, index) =>
        val path = s"terms[$index]"
        if (projection == null) Vector(_shape(path)) else {
          _binding(projection.term, catalog.terms, remaining, path + ".term") ++
            _presence(projection.termType, path + ".termType", _text) ++
            _presence(projection.bokKind, path + ".bokKind", _kind) ++
            _presence(projection.labels, path + ".labels", _labels) ++
            _presence(projection.preferredLabel, path + ".preferredLabel", _label)
        }
      }
      declared ++ _missing(catalog.terms, remaining, "terms")
    }
    val diagnostics = elementdiagnostics ++ termdiagnostics
    if (diagnostics.isEmpty) Right(()) else Left(diagnostics)
  }

  private def _counts[A](values: Vector[A]): Map[A, Int] = {
    values.groupBy(identity).map { case (value, occurrences) => value -> occurrences.size }
  }

  private def _binding[A](
    value: A, admitted: Vector[A], remaining: scala.collection.mutable.Map[A, Int], path: String
  ): Vector[TerminologyDiagnostic] = {
    if (value == null) Vector(_shape(path)) else if (!admitted.contains(value)) {
      Vector(_diagnostic(InvalidCoreBinding, path,
        "The Terminology projection does not bind exactly to an admitted core record."))
    } else if (remaining(value) == 0) {
      Vector(_diagnostic(DuplicateProjection, path,
        "A core record has excess Terminology projection occurrences."))
    } else {
      remaining.update(value, remaining(value) - 1)
      Vector.empty
    }
  }

  private def _missing[A](
    admitted: Vector[A], remaining: scala.collection.mutable.Map[A, Int], path: String
  ): Vector[TerminologyDiagnostic] = {
    admitted.distinct.flatMap { value =>
      Vector.fill(remaining(value))(_diagnostic(MissingProjection, path,
        "An admitted core record lacks its required Terminology projection occurrence."))
    }
  }

  private def _presence[A](
    value: Presence[A], path: String, validate: (A, String) => Vector[TerminologyDiagnostic]
  ): Vector[TerminologyDiagnostic] = {
    value match {
      case Present(payload) => validate(payload, path)
      case Absent(reason, detail) =>
        val reasondiagnostics = reason match {
          case NotDeclared | NotRepresented | Unsupported | NotApplicable => Vector.empty
          case _ => Vector(_semantics(path + ".reason"))
        }
        reasondiagnostics ++ _text(detail, path + ".detail")
      case _ => Vector(_shape(path))
    }
  }

  private def _references(
    value: Presence[Vector[SemanticReference]], path: String
  ): Vector[TerminologyDiagnostic] = {
    _presence(value, path, (refs: Vector[SemanticReference], coordinate: String) => {
      if (refs == null) Vector(_shape(coordinate)) else {
        refs.zipWithIndex.flatMap { case (ref, index) =>
          if (ref == null) Vector(_semantics(s"$coordinate[$index]")) else Vector.empty
        }
      }
    })
  }

  private def _kind(value: BokKind, path: String): Vector[TerminologyDiagnostic] = {
    value match {
      case Mono | Koto => Vector.empty
      case _ => Vector(_semantics(path))
    }
  }

  private def _labels(value: Vector[LocalizedLabel], path: String): Vector[TerminologyDiagnostic] = {
    if (value == null) Vector(_shape(path)) else {
      value.zipWithIndex.flatMap { case (label, index) => _label(label, s"$path[$index]") }
    }
  }

  private def _label(value: LocalizedLabel, path: String): Vector[TerminologyDiagnostic] = {
    if (value == null) Vector(_shape(path)) else {
      _text(value.text, path + ".text") ++ (value.language match {
        case None => Vector.empty
        case Some(language) => _text(language, path + ".language")
        case _ => Vector(_shape(path + ".language"))
      })
    }
  }

  private def _text(value: String, path: String): Vector[TerminologyDiagnostic] = {
    if (value != null && value.nonEmpty &&
      !_space(value.codePointAt(0)) && !_space(value.codePointBefore(value.length)) &&
      !value.codePoints().anyMatch(new java.util.function.IntPredicate {
        override def test(codePoint: Int): Boolean = { Character.isISOControl(codePoint) }
      })) Vector.empty else Vector(_semantics(path))
  }

  private def _space(value: Int): Boolean = {
    Character.isWhitespace(value) || Character.isSpaceChar(value)
  }

  private def _shape(path: String): TerminologyDiagnostic = {
    _diagnostic(InvalidShape, path, "The Terminology value does not match the required v1 shape.")
  }

  private def _semantics(path: String): TerminologyDiagnostic = {
    _diagnostic(InvalidTerminologySemantics, path,
      "The declared Terminology reference, label, classification or absence is invalid.")
  }
}
