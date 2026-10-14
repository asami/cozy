package cozy.modeler

import CmlCapabilitySource.CapabilitySource
import CmlCapabilityValidationScope.ScopeInput
import CmlCapabilityGenerationContext._

/*
 * @since   Oct. 14, 2026
 * @version Oct. 14, 2026
 * @author  ASAMI, Tomoharu
 */
private[cozy] object CmlCapabilityProductionRequest {
  final case class ResourceOutput(key: SnapshotKey, filename: String)
  final case class PackageOutput(component: ComponentCoordinate, packagecoordinate: PackageCoordinate,
    path: String, resources: Vector[ResourceOutput])
  final case class ProductionRequest(producer: Producer, sources: Vector[CapabilitySource], scope: ScopeInput,
    contexts: Vector[GenerationContext], packages: Vector[PackageOutput], scalaoutputs: Vector[ScalaOutput],
    applicationoutput: String)
  val REQUEST_LIMIT = 1048576
  val SOURCE_LIMIT = 16777216
  val SOURCE_COUNT = 256
  val TOTAL_LIMIT = 67108864L
  val METADATA_PREFIX = "meta/component-capability/"
  def validFilename(value: String): Boolean = value != null && value != "index.json" &&
    value.matches("[A-Za-z0-9][A-Za-z0-9._-]*\\.json")
}
