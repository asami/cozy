package cozy.modeler

import org.goldenport.cncf.component.identity.ComponentId
import CmlSemanticFoundation.{ModelElementId, Presence, SemanticTarget, ReferenceBoundary, RelationId, ProfileId, LocalizedLabel}
import CmlCapabilitySource.{CapabilitySource, CapabilityOrigin}

/*
 * @since   Oct. 5, 2026
 * @version Oct. 5, 2026
 * @author  ASAMI, Tomoharu
 */
private[cozy] object CmlCapabilityModel {
  sealed abstract class CapabilityKind
  case object ApplicationKind extends CapabilityKind
  case object ComponentKind extends CapabilityKind
  sealed abstract class Direction
  case object Provided extends Direction
  case object Required extends Direction
  sealed abstract class TargetKind
  case object Operation extends TargetKind
  case object Workflow extends TargetKind
  case object StateMachine extends TargetKind
  sealed abstract class ModelRole
  case object Goal extends ModelRole
  case object UseCase extends ModelRole
  case object Scenario extends ModelRole
  case object UseCaseSlice extends ModelRole
  case object Component extends ModelRole
  case object Specification extends ModelRole
  case object Evidence extends ModelRole
  sealed abstract class TraceabilityRole
  case object SpecificationTrace extends TraceabilityRole
  case object EvidenceTrace extends TraceabilityRole

  final case class ModelReference(identity: ModelElementId, boundary: ReferenceBoundary)
  final case class VersionedApplicationCapabilityReference(identity: ModelElementId, contractVersion: String) {
    val kind: CapabilityKind = ApplicationKind
  }
  final case class VersionedComponentCapabilityReference(identity: ModelElementId, contractVersion: String) {
    val kind: CapabilityKind = ComponentKind
  }
  final case class ModelLink(role: ModelRole, target: ModelReference, source: CapabilitySource, origin: CapabilityOrigin)
  final case class TraceabilityReference(role: TraceabilityRole, target: ModelReference, source: CapabilitySource, origin: CapabilityOrigin)
  final case class GroundingReference(
    target: SemanticTarget,
    boundary: ReferenceBoundary,
    relation: Presence[RelationId],
    profile: Presence[ProfileId],
    context: Presence[String],
    preferredLabel: Presence[LocalizedLabel],
    source: CapabilitySource,
    origin: CapabilityOrigin
  )
  final case class ApplicationComponentMapping(target: VersionedComponentCapabilityReference, boundary: ReferenceBoundary, source: CapabilitySource, origin: CapabilityOrigin)
  final case class ApplicationDependency(target: VersionedApplicationCapabilityReference, boundary: ReferenceBoundary, source: CapabilitySource, origin: CapabilityOrigin)
  final case class ApplicationCapability(
    identity: ModelElementId,
    contractVersion: String,
    name: String,
    summary: Presence[String],
    description: Presence[String],
    source: CapabilitySource,
    demandReferences: Presence[Vector[ModelLink]],
    discoveryReferences: Presence[Vector[ModelLink]],
    componentMappings: Presence[Vector[ApplicationComponentMapping]],
    dependencies: Presence[Vector[ApplicationDependency]],
    grounding: Presence[Vector[GroundingReference]],
    traceability: Presence[Vector[TraceabilityReference]]
  ) {
    val kind: CapabilityKind = ApplicationKind
  }
  final case class ComponentCapability(
    identity: ModelElementId,
    contractVersion: String,
    name: String,
    summary: Presence[String],
    description: Presence[String],
    source: CapabilitySource,
    discoveryReferences: Presence[Vector[ModelLink]],
    grounding: Presence[Vector[GroundingReference]],
    traceability: Presence[Vector[TraceabilityReference]]
  ) {
    val kind: CapabilityKind = ComponentKind
  }
  final case class DeclarationKey(component: ComponentId, direction: Direction, capability: VersionedComponentCapabilityReference)
  final case class ComponentDeclaration(
    component: ComponentId,
    direction: Direction,
    capability: VersionedComponentCapabilityReference,
    source: CapabilitySource,
    realizations: Presence[Vector[RealizationEdge]],
    traceability: Presence[Vector[TraceabilityReference]]
  )
  final case class RealizationEdge(
    declarationKey: DeclarationKey,
    targetKind: TargetKind,
    target: ModelReference,
    targetComponent: ComponentId,
    targetContractVersion: String,
    generatedAbiVersion: String,
    source: CapabilitySource,
    origin: CapabilityOrigin
  )
  final case class CompatibilityEdge(identity: ModelElementId, requiredVersion: String, providedVersion: String, source: CapabilitySource, origin: CapabilityOrigin)
  final case class CapabilityModel(
    applicationCapabilities: Vector[ApplicationCapability],
    componentCapabilities: Vector[ComponentCapability],
    componentDeclarations: Vector[ComponentDeclaration],
    compatibility: Presence[Vector[CompatibilityEdge]]
  )
}
