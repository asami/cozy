package cozy.modeler

import play.api.libs.json.{JsObject, Json}
import CmlCapabilityModel._
import CmlCapabilitySource._
import CmlCapabilityValidationScope.ScopeInput
import CmlCapabilityGenerationContext._
import CmlSemanticFoundation.Presence

/*
 * @since   Oct. 14, 2026
 * @version Oct. 14, 2026
 * @author  ASAMI, Tomoharu
 */
private[cozy] object CmlCapabilityGeneration {
  final class Projection private[CmlCapabilityGeneration] (
    val context: GenerationContext,
    val capabilities: Vector[ComponentCapability],
    val declarations: Vector[ComponentDeclaration],
    val compatibility: Presence[Vector[CompatibilityEdge]],
    val json: JsObject
  ) {
    def snapshotKey: SnapshotKey = context.snapshotKey
    def projectionJson: String = Json.stringify(json)
  }
  final class Generated private[CmlCapabilityGeneration] (
    val validated: CmlCapabilityValidation.Validated,
    val projections: Vector[Projection]
  )

  def generate(inputs: Vector[SourceInput], scopeInput: ScopeInput,
    contexts: Vector[GenerationContext]): Either[Failure, Generated] =
    CmlCapabilityCml.normalize(inputs) match {
      case Left(issues) => Left(SyntaxFailure(issues))
      case Right(documents) => CmlCapabilityValidation.validate(documents, scopeInput) match {
        case Left(diagnostics) => Left(SemanticFailure(diagnostics))
        case Right(validated) => project(validated, contexts).left.map(GenerationFailure)
      }
    }

  def project(validated: CmlCapabilityValidation.Validated,
    contexts: Vector[GenerationContext]): Either[Vector[Issue], Generated] = {
    val issues = CmlCapabilityGenerationValidationPart._validate(validated, contexts)
    if (issues.nonEmpty) Left(issues)
    else {
      val projections = contexts.sortBy(context => (context.source.path, context.source.modelAuthority,
        context.component.component.namespace().value(), context.component.component.localId().value())).map { context =>
        val document = validated.documents.find(_.scope == context.snapshotKey.scope).get
        val capabilities = document.model.componentCapabilities
        val declarations = document.model.componentDeclarations.filter(_.component == context.component.component)
        val compatibility = document.model.compatibility
        new Projection(context, capabilities, declarations, compatibility,
          CmlCapabilityProjectionJsonPart._encode(context, capabilities, declarations, compatibility))
      }
      Right(new Generated(validated, projections))
    }
  }
}
