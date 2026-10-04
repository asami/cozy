package cozy.modeler

import CmlSemanticFoundation.{Catalog, ElementRecord, External, Local, ModelElementId, Present}
import CmlDynamicMetadata.ModelReference
import CmlUseCaseMetadata._

/*
 * @since   Oct.  4, 2026
 * @version Oct.  4, 2026
 * @author  ASAMI, Tomoharu
 */
private[cozy] object CmlUseCaseNavigation {
  sealed trait ReferenceTarget
  final case class LocalTarget(element: ElementRecord) extends ReferenceTarget
  final case class ExternalTarget(reference: ModelReference) extends ReferenceTarget

  final class Navigation private[CmlUseCaseNavigation] (
    val graph: Graph,
    private val _actors_by_id: Map[ModelElementId, ActorProjection],
    private val _use_cases_by_id: Map[ModelElementId, UseCaseProjection]
  ) {
    val catalog: Catalog = graph.catalog

    def actor(id: ModelElementId): Either[UseCaseDiagnostic, ActorProjection] = {
      _query_diagnostic(id, "actor.identity") match {
        case Some(diagnostic) => Left(diagnostic)
        case None => _actors_by_id.get(id) match {
          case Some(projection) => Right(projection)
          case None => Left(_unknown_projection("actor.identity"))
        }
      }
    }

    def useCase(id: ModelElementId): Either[UseCaseDiagnostic, UseCaseProjection] = {
      _query_diagnostic(id, "useCase.identity") match {
        case Some(diagnostic) => Left(diagnostic)
        case None => _use_cases_by_id.get(id) match {
          case Some(projection) => Right(projection)
          case None => Left(_unknown_projection("useCase.identity"))
        }
      }
    }

    def element(id: ModelElementId): Either[UseCaseDiagnostic, ElementRecord] = {
      _query_diagnostic(id, "element.identity") match {
        case Some(diagnostic) => Left(diagnostic)
        case None => catalog.element(id) match {
          case Right(record) => Right(record)
          case Left(_) => Left(_diagnostic(
            UnknownElement,
            "element.identity",
            "No admitted model element has the requested qualified identity."
          ))
        }
      }
    }

    def resolve(reference: ModelReference): Either[UseCaseDiagnostic, ReferenceTarget] = {
      CmlUseCaseMetadataValidation._reference_diagnostic(catalog, reference, "resolve.reference") match {
        case Some(diagnostic) => Left(diagnostic)
        case None => reference.boundary match {
          case External => Right(ExternalTarget(reference))
          case Local => catalog.element(reference.identity) match {
            case Right(record) => Right(LocalTarget(record))
            case Left(_) => Left(_invalid_semantics("resolve.reference"))
          }
          // Admission above rejects any other boundary before interpretation.
          case _ => Left(_diagnostic(
            InvalidShape,
            "resolve.reference.boundary",
            "The Use Case value does not match the required v1 shape."
          ))
        }
      }
    }
  }

  def build(graph: Graph): Either[Vector[UseCaseDiagnostic], Navigation] = {
    if (graph == null) {
      Left(Vector(_diagnostic(InvalidShape, "graph", "An admitted Use Case graph is required.")))
    } else {
      val actors = graph.actors.flatMap { projection =>
        projection.element.identity match {
          case Present(identity) => Vector(identity -> projection)
          case _ => Vector.empty
        }
      }.toMap
      val usecases = graph.useCases.flatMap { projection =>
        projection.element.identity match {
          case Present(identity) => Vector(identity -> projection)
          case _ => Vector.empty
        }
      }.toMap
      Right(new Navigation(graph, actors, usecases))
    }
  }

  private def _query_diagnostic(id: ModelElementId, path: String): Option[UseCaseDiagnostic] = {
    if (id == null || !_valid_component(id.modelId) || !_valid_component(id.elementId)) {
      Some(_invalid_semantics(path))
    } else {
      None
    }
  }

  private def _valid_component(value: String): Boolean = {
    value != null && value.nonEmpty && !_whitespace(value.codePointAt(0)) &&
      !_whitespace(value.codePointBefore(value.length)) && !_contains_control(value)
  }

  private def _whitespace(codepoint: Int): Boolean = {
    Character.isWhitespace(codepoint) || Character.isSpaceChar(codepoint)
  }

  private def _contains_control(value: String): Boolean = {
    var offset = 0
    var result = false
    while (offset < value.length && !result) {
      val codepoint = value.codePointAt(offset)
      result = Character.isISOControl(codepoint)
      offset += Character.charCount(codepoint)
    }
    result
  }

  private def _unknown_projection(path: String): UseCaseDiagnostic = {
    _diagnostic(UnknownProjection, path, "No admitted Use Case projection has the requested qualified identity.")
  }

  private def _invalid_semantics(path: String): UseCaseDiagnostic = {
    _diagnostic(InvalidUseCaseSemantics, path, "The declared Use Case reference or absence is invalid.")
  }

  private def _diagnostic(kind: UseCaseDiagnosticKind, path: String, detail: String): UseCaseDiagnostic = {
    UseCaseDiagnostic(kind, path, detail, None)
  }
}
