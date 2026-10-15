package cozy.modeler

import CmlCapabilityModel._
import CmlCapabilitySource._
import CmlCapabilityValidationScope._
import CmlCapabilityValidationShapePart._
import CmlCapabilityValidationScopePart._

/*
 * @since   Oct. 5, 2026
 * @version Oct. 15, 2026
 * @author  ASAMI, Tomoharu
 */
private[cozy] object CmlCapabilityValidation {
  final class Validated private[CmlCapabilityValidation] (
    val documents: Vector[CapabilityDocument],
    val scope: Scope,
    val bindings: Vector[(RealizationEdge, TargetContract)],
    private val _contracts: Vector[Contract]
  ) {
    def isCompatible(required: VersionedComponentCapabilityReference,
      provided: VersionedComponentCapabilityReference): Boolean =
      CmlCapabilityValidationGraphPart._is_compatible(required, provided, _contracts,
        documents.flatMap(document => _items(document.model.compatibility)))
  }

  def validate(documents: Vector[CapabilityDocument], input: ScopeInput): Either[Vector[Diagnostic], Validated] =
    input match {
      case ScopeInput.Rejected(diagnostics) => Left(diagnostics)
      case ScopeInput.Ready(scope) =>
        val scopediagnostics = CmlCapabilityValidationScopePart._scope(scope)
        val documentdiagnostics = CmlCapabilityValidationSourcePart._scopes(documents) ++
          documents.zipWithIndex.flatMap { case (document, index) => _document_shape(document, index) }
        val initial = scopediagnostics ++ documentdiagnostics
        // Malformed carriers cannot be consumed. Valid duplicate candidates remain
        // available for source-correlated reference failures, never winner selection.
        if (initial.exists(_.kind == DiagnosticKind.Malformed)) Left(initial)
        else {
          val current = _current(documents)
          val catalog = _catalog(scope)
          val contracts = current ++ catalog
          val snapshot = _snapshot(current, catalog) ++ CmlCapabilityValidationDeclarationPart._duplicates(documents)
          val resolved = documents.zipWithIndex.map { case (document, index) =>
            val declarations = CmlCapabilityValidationDeclarationPart._document_declarations(document, index, scope, contracts)
            (CmlCapabilityValidationReferencePart._document_references(document, index, scope, contracts) ++ declarations._1 ++
              CmlCapabilityValidationGraphPart._compatibility(document, index, contracts), declarations._2)
          }
          val diagnostics = scopediagnostics ++ snapshot.filter(_.scope.isEmpty) ++ documentdiagnostics ++
            snapshot.filter(_.scope.nonEmpty) ++ resolved.flatMap(_._1) ++ CmlCapabilityValidationGraphPart._cycles(documents)
          if (diagnostics.nonEmpty) Left(diagnostics)
          else Right(new Validated(documents, scope, resolved.flatMap(_._2), contracts))
        }
    }
}
