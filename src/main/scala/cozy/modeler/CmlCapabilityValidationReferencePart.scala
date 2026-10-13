package cozy.modeler

import CmlSemanticFoundation.{Presence, Present, Absent, Local, External, ModelElementId, ModelTarget, TermTarget}
import CmlCapabilityModel._
import CmlCapabilitySource._
import CmlCapabilityValidationScope._
import CmlCapabilityValidationScope.DiagnosticKind._
import CmlCapabilityValidationSourcePart._
import CmlCapabilityValidationShapePart._
import CmlCapabilityValidationScopePart._

/*
 * @since   Oct. 5, 2026
 * @version Oct. 5, 2026
 * @author  ASAMI, Tomoharu
 */
private[modeler] object CmlCapabilityValidationReferencePart {
  private def _role(value: ModelRole): ModelKind = value match {
    case Goal => ModelKind.Goal
    case UseCase => ModelKind.UseCase
    case Scenario => ModelKind.Scenario
    case UseCaseSlice => ModelKind.UseCaseSlice
    case Component => ModelKind.Component
    case Specification => ModelKind.Specification
    case Evidence => ModelKind.Evidence
  }

  private def _unique[A](values: Vector[A], path: String, context: Context)
    (source: A => Vector[CapabilitySource]): Vector[Diagnostic] =
    if (values.isEmpty) Vector(context._issue(DanglingReference, path, "No exact Local reference candidate."))
    else if (values.size != 1) Vector(context._issue(Ambiguous, path, "Multiple exact Local reference candidates.", values.flatMap(source)))
    else Vector.empty

  private def _model(identity: ModelElementId, expected: Option[ModelKind], scope: Scope,
    contracts: Vector[Contract], path: String, context: Context): Vector[Diagnostic] = {
    val candidates = _model_candidates(identity, scope, contracts)
    val unique = _unique(candidates, path, context)(_._2)
    unique ++ (if (unique.isEmpty && expected.exists(_ != candidates.head._1))
      Vector(context._issue(WrongKind, path, "Local model kind differs from the full reference role.", candidates.flatMap(_._2)))
      else Vector.empty)
  }

  private def _links(value: Presence[Vector[ModelLink]], scope: Scope, contracts: Vector[Contract],
    path: String, sourcescope: SourceScope): Vector[Diagnostic] =
    _items(value).zipWithIndex.flatMap { case (edge, index) =>
      if (edge.target.boundary == External) Vector.empty
      else _model(edge.target.identity, Some(_role(edge.role)), scope, contracts,
        s"$path.value[$index].target.identity", _document(sourcescope, edge.source))
    }

  private[modeler] def _traceability(value: Presence[Vector[TraceabilityReference]], scope: Scope,
    contracts: Vector[Contract], path: String, sourcescope: SourceScope): Vector[Diagnostic] =
    _items(value).zipWithIndex.flatMap { case (edge, index) =>
      if (edge.target.boundary == External) Vector.empty
      else {
        val kind = edge.role match {
          case SpecificationTrace => ModelKind.Specification
          case EvidenceTrace => ModelKind.Evidence
        }
        _model(edge.target.identity, Some(kind), scope, contracts, s"$path.value[$index].target.identity",
          _document(sourcescope, edge.source))
      }
    }

  private def _present[A](value: Presence[A])(check: A => Vector[Diagnostic]): Vector[Diagnostic] = value match {
    case Present(item) => check(item)
    case _: Absent => Vector.empty
  }

  private def _grounding(value: Presence[Vector[GroundingReference]], scope: Scope,
    contracts: Vector[Contract], path: String, sourcescope: SourceScope): Vector[Diagnostic] =
    _items(value).zipWithIndex.flatMap { case (edge, index) =>
      if (edge.boundary == External) Vector.empty
      else {
        val location = s"$path.value[$index]"
        val context = _document(sourcescope, edge.source)
        val target = edge.target match {
          case ModelTarget(identity) => _model(identity, None, scope, contracts, s"$location.target.identity", context)
          case TermTarget(identity) => _unique(scope.termfacts.filter(_.identity == identity), s"$location.target.identity", context)(x => Vector(x.source))
        }
        target ++ _present(edge.relation) { identity =>
          _unique(scope.relationfacts.filter(_.identity == identity), s"$location.relation.value", context)(x => Vector(x.source))
        } ++ _present(edge.profile) { identity =>
          _unique(scope.profilefacts.filter(_.identity == identity), s"$location.profile.value", context)(x => Vector(x.source))
        }
      }
    }

  private[modeler] def _document_references(document: CapabilityDocument, index: Int, scope: Scope,
    contracts: Vector[Contract]): Vector[Diagnostic] = {
    val path = s"documents[$index].model"
    val applications = document.model.applicationCapabilities.zipWithIndex.flatMap { case (value, offset) =>
      val location = s"$path.applicationCapabilities[$offset]"
      _links(value.demandReferences, scope, contracts, s"$location.demandReferences", document.scope) ++
        _links(value.discoveryReferences, scope, contracts, s"$location.discoveryReferences", document.scope) ++
        _items(value.componentMappings).zipWithIndex.flatMap { case (edge, edgeindex) =>
          if (edge.boundary == External) Vector.empty
          else _resolve(edge.target.identity, edge.target.contractVersion, ComponentKind, contracts,
            s"$location.componentMappings.value[$edgeindex].target", _document(document.scope, edge.source))
        } ++ _items(value.dependencies).zipWithIndex.flatMap { case (edge, edgeindex) =>
          if (edge.boundary == External) Vector.empty
          else _resolve(edge.target.identity, edge.target.contractVersion, ApplicationKind, contracts,
            s"$location.dependencies.value[$edgeindex].target", _document(document.scope, edge.source))
        } ++ _grounding(value.grounding, scope, contracts, s"$location.grounding", document.scope) ++
        _traceability(value.traceability, scope, contracts, s"$location.traceability", document.scope)
    }
    val components = document.model.componentCapabilities.zipWithIndex.flatMap { case (value, offset) =>
      val location = s"$path.componentCapabilities[$offset]"
      _links(value.discoveryReferences, scope, contracts, s"$location.discoveryReferences", document.scope) ++
        _grounding(value.grounding, scope, contracts, s"$location.grounding", document.scope) ++
        _traceability(value.traceability, scope, contracts, s"$location.traceability", document.scope)
    }
    applications ++ components
  }
}
