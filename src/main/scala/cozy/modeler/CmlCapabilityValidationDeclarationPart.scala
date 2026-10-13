package cozy.modeler

import CmlCapabilityModel._
import CmlCapabilitySource._
import CmlCapabilityValidationScope._
import CmlCapabilityValidationScope.DiagnosticKind._
import CmlCapabilityValidationSourcePart._
import CmlCapabilityValidationShapePart._
import CmlCapabilityValidationScopePart._
import CmlCapabilityValidationReferencePart._

/*
 * @since   Oct. 5, 2026
 * @version Oct. 5, 2026
 * @author  ASAMI, Tomoharu
 */
private[modeler] object CmlCapabilityValidationDeclarationPart {
  private[modeler] def _key(value: ComponentDeclaration): DeclarationKey =
    DeclarationKey(value.component, value.direction, value.capability)

  private[modeler] def _duplicates(documents: Vector[CapabilityDocument]): Vector[Diagnostic] = {
    val declarations = documents.zipWithIndex.flatMap { case (document, index) =>
      document.model.componentDeclarations.zipWithIndex.map { case (value, offset) =>
        (value, _document(document.scope, value.source), s"documents[$index].model.componentDeclarations[$offset]")
      }
    }
    declarations.zipWithIndex.flatMap { case ((value, context, path), index) =>
      declarations.take(index).find(x => _key(x._1) == _key(value)).toVector.map { previous =>
        context._issue(DuplicateDeclaration, path, "Repeated complete declaration key.", Vector(previous._1.source))
      }
    }
  }

  private def _target(edge: RealizationEdge, scope: Scope, path: String,
    context: Context): Either[Diagnostic, TargetContract] = {
    val identities = scope.targetcontracts.filter(x => x.component == edge.targetComponent && x.identity == edge.target.identity)
    val kinds = identities.filter(_.kind == edge.targetKind)
    val versions = kinds.filter(_.contractversion == edge.targetContractVersion)
    val exact = versions.filter(_.generatedabiversion == edge.generatedAbiVersion)
    if (identities.isEmpty) Left(context._issue(DanglingReference, s"$path.target", "No target has this exact owning Component and qualified identity."))
    else if (kinds.isEmpty) Left(context._issue(WrongKind, s"$path.targetKind", "Executable target kind differs.", identities.map(_.source)))
    else if (versions.isEmpty) Left(context._issue(Incompatible, s"$path.targetContractVersion", "Executable target contract version differs.", kinds.map(_.source)))
    else if (exact.isEmpty) Left(context._issue(Incompatible, s"$path.generatedAbiVersion", "Executable target ABI differs from the supplied contract.", versions.map(_.source)))
    else if (exact.size != 1) Left(context._issue(Ambiguous, s"$path.target", "Multiple exact executable target contracts.", exact.map(_.source)))
    else Right(exact.head)
  }

  private[modeler] def _document_declarations(document: CapabilityDocument, index: Int, scope: Scope,
    contracts: Vector[Contract]): (Vector[Diagnostic], Vector[(RealizationEdge, TargetContract)]) = {
    val results = document.model.componentDeclarations.zipWithIndex.map { case (value, offset) =>
      val path = s"documents[$index].model.componentDeclarations[$offset]"
      val context = _document(document.scope, value.source)
      val references = _resolve(value.capability.identity, value.capability.contractVersion, ComponentKind,
        contracts, s"$path.capability", context)
      val trace = _traceability(value.traceability, scope, contracts, s"$path.traceability", document.scope)
      val targets = _items(value.realizations).zipWithIndex.map { case (edge, edgeindex) =>
        val location = s"$path.realizations.value[$edgeindex]"
        val ctx = _document(document.scope, edge.source)
        if (edge.declarationKey != _key(value))
          Left(ctx._issue(Malformed, s"$location.declarationKey", "Every repeated declaration key field must equal its enclosing complete key."))
        else _target(edge, scope, location, ctx).map(target => edge -> target)
      }
      (references ++ targets.flatMap(_.left.toOption) ++ trace, targets.flatMap(_.right.toOption))
    }
    (results.flatMap(_._1), results.flatMap(_._2))
  }
}
