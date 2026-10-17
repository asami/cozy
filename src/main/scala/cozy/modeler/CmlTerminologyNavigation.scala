package cozy.modeler

import CmlSemanticFoundation._
import CmlTerminologyMetadata._

/*
 * @since   Oct.  4, 2026
 * @version Oct. 13, 2026
 */
private[cozy] object CmlTerminologyNavigation {
  final case class TermLink(element: ElementRecord, reference: SemanticReference)

  sealed abstract class ReferenceTarget
  final case class LocalTarget(link: TermLink, term: TermProjection) extends ReferenceTarget
  final case class ExternalTarget(link: TermLink) extends ReferenceTarget

  final class Navigation private[CmlTerminologyNavigation] (
    val graph: Graph,
    val allLinks: Vector[TermLink],
    private val _elements_by_id: Map[ModelElementId, ElementProjection],
    private val _terms_by_id: Map[TermId, TermProjection]
  ) {
    val catalog: Catalog = graph.catalog
    private val _link_membership: Set[TermLink] = allLinks.toSet

    def element(id: ModelElementId): Either[TerminologyDiagnostic, ElementProjection] = {
      _element(id, "element.identity")
    }

    def term(id: TermId): Either[TerminologyDiagnostic, TermProjection] = {
      _term(id, "term.identity")
    }

    def links(id: ModelElementId): Either[TerminologyDiagnostic, Presence[Vector[TermLink]]] = {
      _element(id, "links.identity").map { projection =>
        projection.termReferences match {
          case Present(references) => Present(references.map(TermLink(projection.element, _)))
          case absence: Absent => absence
        }
      }
    }

    def linksForTerm(id: TermId): Either[TerminologyDiagnostic, Vector[TermLink]] = {
      _term(id, "linksForTerm.identity").map { _ =>
        allLinks.filter { link =>
          link.reference.boundary == Local && link.reference.target == TermTarget(id)
        }
      }
    }

    def resolve(link: TermLink): Either[TerminologyDiagnostic, ReferenceTarget] = {
      if (!_valid_link(link)) {
        Left(_diagnostic(InvalidShape, "resolve.link",
          "The Terminology value does not match the required v1 shape."))
      } else if (!_link_membership(link)) {
        Left(_diagnostic(UnknownLink, "resolve.link",
          "The Terminology link is not admitted by this graph."))
      } else {
        (link.reference.boundary, link.reference.target) match {
          case (External, _) => Right(ExternalTarget(link))
          case (Local, TermTarget(identity)) => Right(LocalTarget(link, _terms_by_id(identity)))
          // Shape validation and admitted membership make this branch unreachable.
          case _ => Left(_diagnostic(InvalidShape, "resolve.link",
            "The Terminology value does not match the required v1 shape."))
        }
      }
    }

    private def _element(id: ModelElementId, path: String): Either[TerminologyDiagnostic, ElementProjection] = {
      if (!_valid_model_id(id)) {
        Left(_invalid_semantics(path))
      } else {
        _elements_by_id.get(id).toRight(_diagnostic(UnknownElement, path,
          "No admitted Terminology element has the requested qualified identity."))
      }
    }

    private def _term(id: TermId, path: String): Either[TerminologyDiagnostic, TermProjection] = {
      if (!_valid_term_id(id)) {
        Left(_invalid_semantics(path))
      } else {
        _terms_by_id.get(id).toRight(_diagnostic(UnknownTerm, path,
          "No admitted Terminology Term has the requested qualified identity."))
      }
    }
  }

  def build(graph: Graph): Either[Vector[TerminologyDiagnostic], Navigation] = {
    if (graph == null) {
      Left(Vector(_diagnostic(InvalidShape, "graph", "An admitted Terminology graph is required.")))
    } else {
      val elements = graph.elements.flatMap { projection =>
        projection.element.identity match {
          case Present(identity) => Vector(identity -> projection)
          case _: Absent => Vector.empty
        }
      }.toMap
      val terms = graph.terms.map(projection => projection.term.identity -> projection).toMap
      val links = graph.catalog.elements.flatMap { record =>
        record.references match {
          case Present(references) => references.collect {
            case reference if reference.target.isInstanceOf[TermTarget] => TermLink(record, reference)
          }
          case _: Absent => Vector.empty
        }
      }
      Right(new Navigation(graph, links, elements, terms))
    }
  }

  // These predicates describe shape only. Admission and cross-field residency
  // remain the Graph's responsibility; complete membership decides foreign links.
  private def _valid_link(link: TermLink): Boolean = {
    link != null && _valid_element(link.element) && _valid_reference(link.reference) &&
      (link.reference.target match {
        case TermTarget(identity) => _valid_term_id(identity)
        case _ => false
      })
  }

  private def _valid_element(record: ElementRecord): Boolean = {
    record != null && record.kind != null && record.name != null &&
      _valid_presence(record.identity, _valid_model_id) && _valid_source(record.source) &&
      _valid_presence(record.references, (refs: Vector[SemanticReference]) => {
        refs != null && refs.forall(_valid_reference)
      })
  }

  private def _valid_reference(reference: SemanticReference): Boolean = {
    reference != null && _valid_target(reference.target) &&
      (reference.boundary == Local || reference.boundary == External) &&
      _valid_presence(reference.relation, _valid_relation_id) &&
      _valid_presence(reference.profile, _valid_profile_id) &&
      _valid_presence(reference.context, _valid_text) &&
      _valid_presence(reference.preferredLabel, _valid_label) &&
      _valid_source(reference.source) && _valid_origin(reference.origin)
  }

  private def _valid_target(target: SemanticTarget): Boolean = {
    target match {
      case ModelTarget(identity) => _valid_model_id(identity)
      case TermTarget(identity) => _valid_term_id(identity)
      case _ => false
    }
  }

  private def _valid_presence[A](presence: Presence[A], valid: A => Boolean): Boolean = {
    presence match {
      case Present(value) => valid(value)
      case Absent(reason, detail) =>
        (reason == NotDeclared || reason == NotRepresented || reason == Unsupported || reason == NotApplicable) &&
          _valid_text(detail)
      case _ => false
    }
  }

  private def _valid_model_id(id: ModelElementId): Boolean = {
    id != null && _valid_text(id.modelId) && _valid_text(id.elementId)
  }

  private def _valid_term_id(id: TermId): Boolean = {
    id != null && _valid_text(id.vocabularyId) && _valid_text(id.termId)
  }

  private def _valid_relation_id(id: RelationId): Boolean = {
    id != null && _valid_text(id.vocabularyId) && _valid_text(id.relationId)
  }

  private def _valid_profile_id(id: ProfileId): Boolean = {
    id != null && _valid_text(id.vocabularyId) && _valid_text(id.profileId)
  }

  private def _valid_label(label: LocalizedLabel): Boolean = {
    label != null && _valid_text(label.text) && (label.language match {
      case None => true
      case Some(language) => _valid_text(language)
      case _ => false
    })
  }

  private def _valid_source(source: SourceAttribution): Boolean = {
    source != null && _valid_text(source.authorityId) && _valid_path(source.path) &&
      (source.line match {
        case None => true
        case Some(line) => line > 0
        case _ => false
      })
  }

  private def _valid_origin(origin: ReferenceOrigin): Boolean = {
    origin match {
      case Declared => true
      case Derived(ruleid, sources) =>
        _valid_text(ruleid) && sources != null && sources.nonEmpty && sources.forall(_valid_source)
      case _ => false
    }
  }

  private def _valid_path(path: String): Boolean = {
    path != null && path.nonEmpty && !_contains_control(path) &&
      !path.startsWith("/") && !path.contains("\\") &&
      !path.matches("^[A-Za-z][A-Za-z0-9+.-]*:.*") &&
      path.split("/", -1).forall(segment => segment.nonEmpty && segment != "." && segment != "..")
  }

  private def _valid_text(text: String): Boolean = {
    text != null && text.nonEmpty && !_whitespace(text.codePointAt(0)) &&
      !_whitespace(text.codePointBefore(text.length)) && !_contains_control(text)
  }

  private def _whitespace(codepoint: Int): Boolean = {
    Character.isWhitespace(codepoint) || Character.isSpaceChar(codepoint)
  }

  private def _contains_control(text: String): Boolean = {
    var offset = 0
    var found = false
    while (offset < text.length && !found) {
      val codepoint = text.codePointAt(offset)
      found = Character.isISOControl(codepoint)
      offset += Character.charCount(codepoint)
    }
    found
  }

  private def _invalid_semantics(path: String): TerminologyDiagnostic = {
    _diagnostic(InvalidTerminologySemantics, path,
      "The declared Terminology reference, label, classification or absence is invalid.")
  }

  private def _diagnostic(kind: TerminologyDiagnosticKind, path: String, detail: String): TerminologyDiagnostic = {
    TerminologyDiagnostic(kind, path, detail, None)
  }
}
