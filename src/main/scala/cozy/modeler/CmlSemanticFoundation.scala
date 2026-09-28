package cozy.modeler

/*
 * @since   Sep. 28, 2026
 * @version Sep. 28, 2026
 * @author  ASAMI, Tomoharu
 */
private[cozy] object CmlSemanticFoundation {
  final case class ModelElementId(modelId: String, elementId: String)
  final case class TermId(vocabularyId: String, termId: String)
  final case class RelationId(vocabularyId: String, relationId: String)
  final case class ProfileId(vocabularyId: String, profileId: String)

  final case class SourceAttribution(
    authorityId: String,
    path: String,
    sha256: String,
    line: Option[Int]
  )

  sealed abstract class Presence[+A]
  final case class Present[A](value: A) extends Presence[A]
  final case class Absent(reason: AbsenceReason, detail: String) extends Presence[Nothing]

  sealed abstract class AbsenceReason
  case object NotDeclared extends AbsenceReason
  case object NotRepresented extends AbsenceReason
  case object Unsupported extends AbsenceReason
  case object NotApplicable extends AbsenceReason

  final case class ElementRecord(
    identity: Presence[ModelElementId],
    kind: String,
    name: String,
    source: SourceAttribution,
    references: Presence[Vector[SemanticReference]]
  )

  final case class TermRecord(identity: TermId, source: SourceAttribution)

  final case class SemanticReference(
    target: SemanticTarget,
    boundary: ReferenceBoundary,
    relation: Presence[RelationId],
    profile: Presence[ProfileId],
    context: Presence[String],
    preferredLabel: Presence[LocalizedLabel],
    source: SourceAttribution,
    origin: ReferenceOrigin
  )

  final case class LocalizedLabel(text: String, language: Option[String])

  sealed abstract class SemanticTarget
  final case class ModelTarget(identity: ModelElementId) extends SemanticTarget
  final case class TermTarget(identity: TermId) extends SemanticTarget

  sealed abstract class ReferenceBoundary
  case object Local extends ReferenceBoundary
  case object External extends ReferenceBoundary

  sealed abstract class ReferenceOrigin
  case object Declared extends ReferenceOrigin
  final case class Derived(ruleId: String, sources: Vector[SourceAttribution]) extends ReferenceOrigin

  sealed abstract class DiagnosticKind
  case object InvalidIdentity extends DiagnosticKind
  case object InvalidProvenance extends DiagnosticKind
  case object InvalidReference extends DiagnosticKind
  case object DuplicateIdentity extends DiagnosticKind
  case object DanglingReference extends DiagnosticKind

  final case class Diagnostic(kind: DiagnosticKind, path: String, detail: String)

  final class Catalog private[CmlSemanticFoundation] (
    val elements: Vector[ElementRecord],
    val terms: Vector[TermRecord],
    private val _elements_by_id: Map[ModelElementId, ElementRecord],
    private val _terms_by_id: Map[TermId, TermRecord]
  ) {
    def element(id: ModelElementId): Either[Diagnostic, ElementRecord] =
      _elements_by_id.get(id) match {
        case Some(record) => Right(record)
        case None => Left(Diagnostic(
          DanglingReference,
          "element",
          "No admitted model element has the requested qualified identity."
        ))
      }

    def term(id: TermId): Either[Diagnostic, TermRecord] =
      _terms_by_id.get(id) match {
        case Some(record) => Right(record)
        case None => Left(Diagnostic(
          DanglingReference,
          "term",
          "No admitted Term has the requested qualified identity."
        ))
      }
  }

  object Catalog {
    val empty: Catalog = new Catalog(Vector.empty, Vector.empty, Map.empty, Map.empty)

    def apply(): Catalog = empty
  }

  def build(
    elements: Vector[ElementRecord],
    terms: Vector[TermRecord]
  ): Either[Vector[Diagnostic], Catalog] = {
    val elementdiagnostics = elements.zipWithIndex.flatMap { case (record, index) =>
      _element_diagnostics(record, s"elements[$index]")
    }
    val termdiagnostics = terms.zipWithIndex.flatMap { case (record, index) =>
      _term_diagnostics(record, s"terms[$index]")
    }
    val elementidentities = _element_identities(elements)
    val termidentities = _term_identities(terms)
    val duplicatediagnostics =
      _duplicate_diagnostics(elementidentities, "elements") ++
        _duplicate_diagnostics(termidentities, "terms")
    val danglingdiagnostics = _dangling_local_diagnostics(
      elements,
      elementidentities.map(_._1).toSet,
      termidentities.map(_._1).toSet
    )
    val diagnostics = elementdiagnostics ++ termdiagnostics ++ duplicatediagnostics ++ danglingdiagnostics
    if (diagnostics.nonEmpty)
      Left(diagnostics)
    else
      Right(new Catalog(
        elements,
        terms,
        elementidentities.map { case (identity, index) => identity -> elements(index) }.toMap,
        termidentities.map { case (identity, index) => identity -> terms(index) }.toMap
      ))
  }

  private def _element_diagnostics(record: ElementRecord, path: String): Vector[Diagnostic] = {
    val identitydiagnostics = record.identity match {
      case Present(identity) => _model_element_id_diagnostics(identity, s"$path.identity")
      case absence: Absent => _absence_diagnostics(absence, s"$path.identity", InvalidIdentity)
    }
    val authoritydiagnostics = record.identity match {
      case Present(identity) if identity.modelId != record.source.authorityId => Vector(Diagnostic(
        InvalidProvenance,
        s"$path.source.authorityId",
        "The element source authority must equal its model identity authority."
      ))
      case _ => Vector.empty
    }
    val referencediagnostics = record.references match {
      case Present(references) => references.zipWithIndex.flatMap { case (reference, index) =>
        _reference_diagnostics(reference, record.source.authorityId, s"$path.references[$index]")
      }
      case absence: Absent => _absence_diagnostics(absence, s"$path.references", InvalidReference)
    }
    identitydiagnostics ++ _source_diagnostics(record.source, s"$path.source") ++
      authoritydiagnostics ++ referencediagnostics
  }

  private def _term_diagnostics(record: TermRecord, path: String): Vector[Diagnostic] = {
    val authoritydiagnostics = if (record.identity.vocabularyId == record.source.authorityId)
      Vector.empty
    else
      Vector(Diagnostic(
        InvalidProvenance,
        s"$path.source.authorityId",
        "The Term source authority must equal its vocabulary identity authority."
      ))
    _term_id_diagnostics(record.identity, s"$path.identity") ++
      _source_diagnostics(record.source, s"$path.source") ++ authoritydiagnostics
  }

  private def _reference_diagnostics(
    reference: SemanticReference,
    owningauthority: String,
    path: String
  ): Vector[Diagnostic] = {
    val targetdiagnostics = reference.target match {
      case ModelTarget(identity) => _model_element_id_diagnostics(identity, s"$path.target.identity")
      case TermTarget(identity) => _term_id_diagnostics(identity, s"$path.target.identity")
    }
    val authoritydiagnostics = if (reference.source.authorityId == owningauthority)
      Vector.empty
    else
      Vector(Diagnostic(
        InvalidProvenance,
        s"$path.source.authorityId",
        "The primary reference source authority must equal the owning element authority."
      ))
    targetdiagnostics ++
      _relation_diagnostics(reference.relation, s"$path.relation") ++
      _profile_diagnostics(reference.profile, s"$path.profile") ++
      _context_diagnostics(reference.context, s"$path.context") ++
      _label_diagnostics(reference.preferredLabel, s"$path.preferredLabel") ++
      _source_diagnostics(reference.source, s"$path.source") ++ authoritydiagnostics ++
      _origin_diagnostics(reference.origin, s"$path.origin")
  }

  private def _relation_diagnostics(value: Presence[RelationId], path: String): Vector[Diagnostic] =
    value match {
      case Present(identity) => _relation_id_diagnostics(identity, path)
      case absence: Absent => _absence_diagnostics(absence, path, InvalidReference)
    }

  private def _profile_diagnostics(value: Presence[ProfileId], path: String): Vector[Diagnostic] =
    value match {
      case Present(identity) => _profile_id_diagnostics(identity, path)
      case absence: Absent => _absence_diagnostics(absence, path, InvalidReference)
    }

  private def _context_diagnostics(value: Presence[String], path: String): Vector[Diagnostic] =
    value match {
      case Present(context) => _text_diagnostics(context, path, InvalidReference, "Context")
      case absence: Absent => _absence_diagnostics(absence, path, InvalidReference)
    }

  private def _label_diagnostics(value: Presence[LocalizedLabel], path: String): Vector[Diagnostic] =
    value match {
      case Present(label) =>
        _text_diagnostics(label.text, s"$path.text", InvalidReference, "Localized label text") ++
          label.language.toVector.flatMap(language =>
            _text_diagnostics(language, s"$path.language", InvalidReference, "Localized label language")
          )
      case absence: Absent => _absence_diagnostics(absence, path, InvalidReference)
    }

  private def _origin_diagnostics(value: ReferenceOrigin, path: String): Vector[Diagnostic] =
    value match {
      case Declared => Vector.empty
      case Derived(ruleid, sources) =>
        _text_diagnostics(ruleid, s"$path.ruleId", InvalidReference, "Derived reference rule") ++
          (if (sources.nonEmpty) Vector.empty else Vector(Diagnostic(
            InvalidReference,
            s"$path.sources",
            "A derived reference requires at least one admitted input source."
          ))) ++
          sources.zipWithIndex.flatMap { case (source, index) =>
            _source_diagnostics(source, s"$path.sources[$index]")
          }
    }

  private def _source_diagnostics(value: SourceAttribution, path: String): Vector[Diagnostic] = {
    val authoritydiagnostics = _text_diagnostics(
      value.authorityId,
      s"$path.authorityId",
      InvalidProvenance,
      "Source authority"
    )
    val pathdiagnostics = if (_valid_path(value.path)) Vector.empty else Vector(Diagnostic(
      InvalidProvenance,
      s"$path.path",
      "Source path must be a canonical project-relative POSIX path."
    ))
    val digestdiagnostics = if (_valid_digest(value.sha256)) Vector.empty else Vector(Diagnostic(
      InvalidProvenance,
      s"$path.sha256",
      "Source digest must be exactly 64 lowercase hexadecimal characters."
    ))
    val linediagnostics = value.line match {
      case Some(line) if line > 0 => Vector.empty
      case Some(_) => Vector(Diagnostic(
        InvalidProvenance,
        s"$path.line",
        "A supplied source line must be positive."
      ))
      case None => Vector.empty
    }
    authoritydiagnostics ++ pathdiagnostics ++ digestdiagnostics ++ linediagnostics
  }

  private def _model_element_id_diagnostics(value: ModelElementId, path: String): Vector[Diagnostic] =
    _identifier_diagnostics(value.modelId, s"$path.modelId") ++
      _identifier_diagnostics(value.elementId, s"$path.elementId")

  private def _term_id_diagnostics(value: TermId, path: String): Vector[Diagnostic] =
    _identifier_diagnostics(value.vocabularyId, s"$path.vocabularyId") ++
      _identifier_diagnostics(value.termId, s"$path.termId")

  private def _relation_id_diagnostics(value: RelationId, path: String): Vector[Diagnostic] =
    _identifier_diagnostics(value.vocabularyId, s"$path.vocabularyId") ++
      _identifier_diagnostics(value.relationId, s"$path.relationId")

  private def _profile_id_diagnostics(value: ProfileId, path: String): Vector[Diagnostic] =
    _identifier_diagnostics(value.vocabularyId, s"$path.vocabularyId") ++
      _identifier_diagnostics(value.profileId, s"$path.profileId")

  private def _identifier_diagnostics(value: String, path: String): Vector[Diagnostic] =
    _text_diagnostics(value, path, InvalidIdentity, "Identifier component")

  private def _absence_diagnostics(
    value: Absent,
    path: String,
    kind: DiagnosticKind
  ): Vector[Diagnostic] =
    _text_diagnostics(value.detail, s"$path.detail", kind, "Absence detail")

  private def _text_diagnostics(
    value: String,
    path: String,
    kind: DiagnosticKind,
    subject: String
  ): Vector[Diagnostic] =
    if (_valid_text(value)) Vector.empty
    else Vector(Diagnostic(kind, path, s"$subject must be nonempty, unpadded, and free of ISO control characters."))

  private def _element_identities(elements: Vector[ElementRecord]): Vector[(ModelElementId, Int)] =
    elements.zipWithIndex.flatMap { case (record, index) =>
      record.identity match {
        case Present(identity) if _model_element_id_diagnostics(identity, "identity").isEmpty =>
          Vector(identity -> index)
        case _ => Vector.empty
      }
    }

  private def _term_identities(terms: Vector[TermRecord]): Vector[(TermId, Int)] =
    terms.zipWithIndex.flatMap { case (record, index) =>
      if (_term_id_diagnostics(record.identity, "identity").isEmpty)
        Vector(record.identity -> index)
      else
        Vector.empty
    }

  private def _duplicate_diagnostics[A](identities: Vector[(A, Int)], root: String): Vector[Diagnostic] = {
    val seen = scala.collection.mutable.Set.empty[A]
    identities.flatMap { case (identity, index) =>
      if (seen(identity))
        Vector(Diagnostic(
          DuplicateIdentity,
          s"$root[$index].identity",
          "An admitted qualified identity must occur exactly once."
        ))
      else {
        seen += identity
        Vector.empty
      }
    }
  }

  private def _dangling_local_diagnostics(
    elements: Vector[ElementRecord],
    modelelementids: Set[ModelElementId],
    termids: Set[TermId]
  ): Vector[Diagnostic] =
    elements.zipWithIndex.flatMap { case (record, elementindex) =>
      record.references match {
        case Present(references) => references.zipWithIndex.flatMap { case (reference, referenceindex) =>
          if (reference.boundary == Local)
            reference.target match {
              case ModelTarget(identity) if _model_element_id_diagnostics(identity, "target").isEmpty && !modelelementids(identity) =>
                Vector(Diagnostic(DanglingReference, s"elements[$elementindex].references[$referenceindex].target", "The local model target is not admitted."))
              case TermTarget(identity) if _term_id_diagnostics(identity, "target").isEmpty && !termids(identity) =>
                Vector(Diagnostic(DanglingReference, s"elements[$elementindex].references[$referenceindex].target", "The local Term target is not admitted."))
              case _ => Vector.empty
            }
          else
            Vector.empty
        }
        case _ => Vector.empty
      }
    }

  private def _valid_text(value: String): Boolean =
    value != null && value.nonEmpty && !_edge_whitespace(value) && !_contains_iso_control(value)

  private def _valid_path(value: String): Boolean =
    value != null && value.nonEmpty && !_contains_iso_control(value) &&
      !value.startsWith("/") && !value.contains("\\") &&
      !value.matches("^[A-Za-z][A-Za-z0-9+.-]*:.*") &&
      value.split("/", -1).forall(segment => segment.nonEmpty && segment != "." && segment != "..")

  private def _valid_digest(value: String): Boolean =
    value != null && value.matches("[0-9a-f]{64}")

  private def _edge_whitespace(value: String): Boolean =
    _is_whitespace(value.codePointAt(0)) || _is_whitespace(value.codePointBefore(value.length))

  private def _is_whitespace(codepoint: Int): Boolean =
    Character.isWhitespace(codepoint) || Character.isSpaceChar(codepoint)

  private def _contains_iso_control(value: String): Boolean = {
    var offset = 0
    var result = false
    while (offset < value.length && !result) {
      val codepoint = value.codePointAt(offset)
      result = Character.isISOControl(codepoint)
      offset += Character.charCount(codepoint)
    }
    result
  }
}
