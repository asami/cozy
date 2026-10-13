package cozy.modeler

import java.nio.file.Path
import org.goldenport.cncf.component.identity.ComponentId
import CmlCapabilitySource.{CapabilitySource, SourceScope, SyntaxIssue}
import CmlCapabilityValidationScope.Diagnostic

/*
 * @since   Oct. 14, 2026
 * @version Oct. 14, 2026
 * @author  ASAMI, Tomoharu
 */
private[cozy] object CmlCapabilityGenerationContext {
  final case class Producer(id: String, version: String)
  final case class ComponentCoordinate(component: ComponentId, version: String)
  final case class PackageCoordinate(coordinate: String, version: String)
  final case class SnapshotKey(scope: SourceScope, component: ComponentId)
  final case class GenerationContext(
    projectiongeneration: Int,
    schemaversion: Int,
    generatedabiversion: String,
    producer: Producer,
    component: ComponentCoordinate,
    packagecoordinate: PackageCoordinate,
    source: CapabilitySource
  ) {
    def snapshotKey: SnapshotKey = SnapshotKey(SourceScope(source.modelAuthority, source.path), component.component)
  }
  final case class ScalaOutput(key: SnapshotKey, path: String, packagename: Vector[String], objectname: String)

  sealed abstract class IssueKind
  object IssueKind {
    case object Malformed extends IssueKind
    case object Unsupported extends IssueKind
    case object ProducerMismatch extends IssueKind
    case object UnknownSnapshot extends IssueKind
    case object DuplicateSnapshot extends IssueKind
    case object MissingCoverage extends IssueKind
    case object OutputConflict extends IssueKind
    case object UnsafeOutput extends IssueKind
    case object IoFailure extends IssueKind
  }
  final case class Issue(kind: IssueKind, fieldpath: String, source: Option[CapabilitySource],
    relatedsources: Vector[CapabilitySource], detail: String)
  sealed abstract class Failure
  final case class SyntaxFailure(issues: Vector[SyntaxIssue]) extends Failure
  final case class SemanticFailure(diagnostics: Vector[Diagnostic]) extends Failure
  final case class GenerationFailure(issues: Vector[Issue]) extends Failure
  final case class PublicationFailure(issues: Vector[Issue], writtenpaths: Vector[Path])
}
