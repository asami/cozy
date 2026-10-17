package cozy.modeler

import CmlSemanticFoundation.Local
import CmlCapabilityModel._
import CmlCapabilitySource._
import CmlCapabilityValidationScope._
import CmlCapabilityValidationScope.DiagnosticKind._
import CmlCapabilityValidationSourcePart._
import CmlCapabilityValidationShapePart._
import CmlCapabilityValidationScopePart._

/*
 * @since   Oct. 5, 2026
 * @version Oct. 15, 2026
 * @author  ASAMI, Tomoharu
 */
private[modeler] object CmlCapabilityValidationGraphPart {
  private[modeler] def _cycles(documents: Vector[CapabilityDocument]): Vector[Diagnostic] = {
    val edges = documents.zipWithIndex.flatMap { case (document, index) =>
      document.model.componentUsageCapabilities.zipWithIndex.flatMap { case (value, offset) =>
        _items(value.dependencies).zipWithIndex.collect {
          case (edge, edgeindex) if edge.boundary == Local =>
            (value.identity, edge.target.identity, edge, _document(document.scope, edge.source),
              s"documents[$index].model.componentUsageCapabilities[$offset].dependencies.value[$edgeindex]")
        }
      }
    }
    val adjacency = edges.groupBy(_._1).map { case (identity, items) => identity -> items.map(_._2) }
    def _reachable_(start: CmlSemanticFoundation.ModelElementId, goal: CmlSemanticFoundation.ModelElementId): Boolean = {
      @annotation.tailrec
      def _visit_(pending: List[CmlSemanticFoundation.ModelElementId], visited: Set[CmlSemanticFoundation.ModelElementId]): Boolean =
        pending match {
          case Nil => false
          case head :: _ if head == goal => true
          case head :: tail if visited(head) => _visit_(tail, visited)
          case head :: tail => _visit_(adjacency.getOrElse(head, Vector.empty).toList ::: tail, visited + head)
        }
      _visit_(List(start), Set.empty)
    }
    edges.collect { case (from, to, _, context, path) if _reachable_(to, from) =>
      context._issue(CyclicDependency, path, "This authored Local Usage dependency participates in a directed cycle.")
    }
  }

  private[modeler] def _compatibility(document: CapabilityDocument, index: Int,
    contracts: Vector[Contract]): Vector[Diagnostic] =
    _items(document.model.compatibility).zipWithIndex.flatMap { case (edge, offset) =>
      val path = s"documents[$index].model.compatibility.value[$offset]"
      val context = _document(document.scope, edge.source)
      _resolve(edge.identity, edge.requiredVersion, ComponentKind, contracts, s"$path.required", context) ++
        _resolve(edge.identity, edge.providedVersion, ComponentKind, contracts, s"$path.provided", context)
    }

  private[modeler] def _is_compatible(required: VersionedComponentCapabilityReference,
    provided: VersionedComponentCapabilityReference, contracts: Vector[Contract],
    edges: Vector[CompatibilityEdge]): Boolean = {
    val context = Context(None, None)
    _component_reference(required, "required", context).isEmpty &&
      _component_reference(provided, "provided", context).isEmpty &&
      _resolve(required.identity, required.contractVersion, ComponentKind, contracts, "required", context).isEmpty &&
      _resolve(provided.identity, provided.contractVersion, ComponentKind, contracts, "provided", context).isEmpty &&
      required.identity == provided.identity &&
      (required.contractVersion == provided.contractVersion || edges.exists(edge =>
        edge.identity == required.identity && edge.requiredVersion == required.contractVersion && edge.providedVersion == provided.contractVersion))
  }
}
