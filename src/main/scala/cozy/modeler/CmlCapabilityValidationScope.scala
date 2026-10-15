package cozy.modeler

import org.goldenport.cncf.component.identity.ComponentId
import CmlSemanticFoundation.{ModelElementId, TermId, RelationId, ProfileId}
import CmlCapabilitySource.{CapabilitySource, SourceScope}

/*
 * @since   Oct. 5, 2026
 * @version Oct. 15, 2026
 * @author  ASAMI, Tomoharu
 */
private[cozy] object CmlCapabilityValidationScope {
  sealed abstract class ModelKind(val tag: String)
  object ModelKind {
    case object DomainConcept extends ModelKind("DOMAIN_CONCEPT")
    case object Goal extends ModelKind("GOAL")
    case object UseCase extends ModelKind("USE_CASE")
    case object Scenario extends ModelKind("SCENARIO")
    case object UseCaseSlice extends ModelKind("USE_CASE_SLICE")
    case object Component extends ModelKind("COMPONENT")
    case object ComponentCapability extends ModelKind("COMPONENT_CAPABILITY")
    case object ComponentUsageCapability extends ModelKind("COMPONENT_USAGE_CAPABILITY")
    case object Specification extends ModelKind("SPECIFICATION")
    case object Evidence extends ModelKind("EVIDENCE")
    case object Operation extends ModelKind("OPERATION")
    case object Workflow extends ModelKind("WORKFLOW")
    case object StateMachine extends ModelKind("STATE_MACHINE")
  }

  sealed abstract class DiagnosticKind
  object DiagnosticKind {
    case object Malformed extends DiagnosticKind
    case object Unsupported extends DiagnosticKind
    case object Unavailable extends DiagnosticKind
    case object Unauthorized extends DiagnosticKind
    case object Stale extends DiagnosticKind
    case object DuplicateDefinition extends DiagnosticKind
    case object DuplicateDeclaration extends DiagnosticKind
    case object DanglingReference extends DiagnosticKind
    case object Ambiguous extends DiagnosticKind
    case object WrongKind extends DiagnosticKind
    case object Incompatible extends DiagnosticKind
    case object CyclicDependency extends DiagnosticKind
  }
  final case class Diagnostic(
    kind: DiagnosticKind,
    fieldpath: String,
    scope: Option[SourceScope],
    source: Option[CapabilitySource],
    relatedsources: Vector[CapabilitySource],
    detail: String
  )

  sealed abstract class ScopeInput
  object ScopeInput {
    final case class Ready(scope: Scope) extends ScopeInput
    final case class Rejected(diagnostics: Vector[Diagnostic]) extends ScopeInput {
      require(diagnostics.nonEmpty, "Rejected scope requires its acquisition diagnostics.")
    }
  }
  final case class Scope(
    scopeid: String,
    source: CapabilitySource,
    modelfacts: Vector[ModelFact],
    termfacts: Vector[TermFact],
    relationfacts: Vector[RelationFact],
    profilefacts: Vector[ProfileFact],
    usagecontracts: Vector[ComponentUsageContract],
    componentcontracts: Vector[ComponentContract],
    targetcontracts: Vector[TargetContract]
  )
  final case class ModelFact(identity: ModelElementId, kind: ModelKind, source: CapabilitySource)
  final case class TermFact(identity: TermId, source: CapabilitySource)
  final case class RelationFact(identity: RelationId, source: CapabilitySource)
  final case class ProfileFact(identity: ProfileId, source: CapabilitySource)
  final case class ComponentUsageContract(identity: ModelElementId, contractversion: String, source: CapabilitySource)
  final case class ComponentContract(identity: ModelElementId, contractversion: String, source: CapabilitySource)
  final case class TargetContract(
    kind: CmlCapabilityModel.TargetKind,
    identity: ModelElementId,
    component: ComponentId,
    contractversion: String,
    generatedabiversion: String,
    source: CapabilitySource
  )
}
