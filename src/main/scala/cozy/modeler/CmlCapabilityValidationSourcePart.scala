package cozy.modeler

import CmlCapabilitySource._
import CmlCapabilityValidationScope._
import CmlCapabilityValidationScope.DiagnosticKind._

/*
 * @since   Oct. 5, 2026
 * @version Oct. 15, 2026
 * @author  ASAMI, Tomoharu
 */
private[modeler] object CmlCapabilityValidationSourcePart {
  final case class Context(scope: Option[SourceScope], source: Option[CapabilitySource]) {
    private[modeler] def _issue(kind: DiagnosticKind, path: String, detail: String,
      related: Vector[CapabilitySource] = Vector.empty): Diagnostic =
      Diagnostic(kind, path, scope, source, related, detail)
  }

  private[modeler] def _document(scope: SourceScope, source: CapabilitySource): Context =
    Context(Some(scope), Option(source))

  private[modeler] def _text(value: String, path: String, context: Context): Vector[Diagnostic] =
    if (CmlCapabilitySource._valid_text(value)) Vector.empty
    else Vector(context._issue(Malformed, path, "Required literal text is null, empty, padded or contains ISO controls."))

  private[modeler] def _source(value: CapabilitySource, path: String, context: Context): Vector[Diagnostic] = {
    if (value == null) Vector(context._issue(Malformed, path, "An explicit source is required."))
    else {
      _text(value.modelAuthority, s"$path.modelAuthority", context) ++
        (if (CmlCapabilitySource._valid_path(value.path)) Vector.empty
         else Vector(context._issue(Malformed, s"$path.path", "Source path must be canonical project-relative POSIX."))) ++
        _text(value.sourceRevision, s"$path.sourceRevision", context) ++
        (if (value.line == null || value.line.exists(_ <= 0))
           Vector(context._issue(Malformed, s"$path.line", "Source line must be explicit None or a positive line."))
         else Vector.empty)
    }
  }

  private[modeler] def _authority(value: CapabilitySource, authority: String, path: String,
    context: Context): Vector[Diagnostic] =
    if (value != null && value.modelAuthority != authority)
      Vector(context._issue(Malformed, path, "Source authority differs from the qualified identity authority."))
    else Vector.empty

  private[modeler] def _direct(value: CapabilitySource, path: String, context: Context): Vector[Diagnostic] =
    _source(value, path, context) ++ context.scope.toVector.flatMap { scope =>
      if (value != null && (value.modelAuthority != scope.modelAuthority || value.path != scope.path))
        Vector(context._issue(Malformed, path, "Direct source authority/path differs from its original document scope."))
      else Vector.empty
    }

  private[modeler] def _origin(value: CapabilityOrigin, path: String, context: Context): Vector[Diagnostic] =
    value match {
      case Declared => Vector.empty
      case Derived(ruleid, inputs) =>
        _text(ruleid, s"$path.ruleId", context) ++
          (if (inputs.isEmpty) Vector(context._issue(Malformed, s"$path.inputs", "Derived origin requires ordered source evidence."))
           else Vector.empty) ++
          inputs.zipWithIndex.flatMap { case (source, index) => _source(source, s"$path.inputs[$index]", context) }
      case null => Vector(context._issue(Malformed, path, "An explicit origin is required."))
    }

  private[modeler] def _scopes(documents: Vector[CapabilityDocument]): Vector[Diagnostic] =
    documents.zipWithIndex.flatMap { case (document, index) =>
      val context = Context(Some(document.scope), None)
      val path = s"documents[$index].scope"
      _text(document.scope.modelAuthority, s"$path.modelAuthority", context) ++
        (if (CmlCapabilitySource._valid_path(document.scope.path)) Vector.empty
         else Vector(context._issue(Malformed, s"$path.path", "Invalid original document path."))) ++
        documents.take(index).find(_.scope.path == document.scope.path).toVector.map { _ =>
          context._issue(DuplicateDefinition, s"$path.path", "Repeated original document path.")
        }
    }
}
