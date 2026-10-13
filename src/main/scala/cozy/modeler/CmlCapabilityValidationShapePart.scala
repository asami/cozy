package cozy.modeler

import org.goldenport.cncf.component.identity.{ComponentId, ComponentNamespace, ComponentLocalId}
import CmlSemanticFoundation.{Presence, Present, Absent, NotDeclared, NotRepresented, NotApplicable, Local, External, ModelElementId, TermId, RelationId, ProfileId, ModelTarget, TermTarget, LocalizedLabel, ReferenceBoundary}
import CmlCapabilityModel._
import CmlCapabilitySource._
import CmlCapabilityValidationScope.{Diagnostic, DiagnosticKind}
import CmlCapabilityValidationSourcePart._

/*
 * @since   Oct. 5, 2026
 * @version Oct. 5, 2026
 * @author  ASAMI, Tomoharu
 */
private[modeler] object CmlCapabilityValidationShapePart {
  private[modeler] def _required(value: AnyRef, path: String, context: Context): Vector[Diagnostic] =
    if (value == null) Vector(context._issue(DiagnosticKind.Malformed, path, "An explicit typed value is required."))
    else Vector.empty

  private[modeler] def _model_id(value: ModelElementId, path: String, context: Context): Vector[Diagnostic] =
    if (value == null) _required(value, path, context)
    else _text(value.modelId, s"$path.modelId", context) ++ _text(value.elementId, s"$path.elementId", context)

  private[modeler] def _term_id(value: TermId, path: String, context: Context): Vector[Diagnostic] =
    if (value == null) _required(value, path, context)
    else _text(value.vocabularyId, s"$path.vocabularyId", context) ++ _text(value.termId, s"$path.termId", context)

  private[modeler] def _relation_id(value: RelationId, path: String, context: Context): Vector[Diagnostic] =
    if (value == null) _required(value, path, context)
    else _text(value.vocabularyId, s"$path.vocabularyId", context) ++ _text(value.relationId, s"$path.relationId", context)

  private[modeler] def _profile_id(value: ProfileId, path: String, context: Context): Vector[Diagnostic] =
    if (value == null) _required(value, path, context)
    else _text(value.vocabularyId, s"$path.vocabularyId", context) ++ _text(value.profileId, s"$path.profileId", context)

  private[modeler] def _component(value: ComponentId, path: String, context: Context): Vector[Diagnostic] = {
    if (value == null) _required(value, path, context)
    else {
      val namespace = ComponentNamespace.parse(value.namespace().value())
      val localid = ComponentLocalId.parse(value.localId().value())
      if (namespace.isSuccess() && localid.isSuccess() &&
        namespace.value().get() == value.namespace() && localid.value().get() == value.localId()) Vector.empty
      else Vector(context._issue(DiagnosticKind.Malformed, path, "Invalid canonical Component identity."))
    }
  }

  private[modeler] def _presence[A](value: Presence[A], path: String, context: Context)
    (check: (A, String, Context) => Vector[Diagnostic]): Vector[Diagnostic] = value match {
    case Present(item) => check(item, s"$path.value", context)
    case Absent(reason, detail) =>
      _text(detail, s"$path.detail", context) ++ (reason match {
        case NotDeclared | NotRepresented | CmlSemanticFoundation.Unsupported | NotApplicable => Vector.empty
        case null => _required(reason, s"$path.reason", context)
      })
    case null => _required(value, path, context)
  }

  private[modeler] def _items[A](value: Presence[Vector[A]]): Vector[A] = value match {
    case Present(items) => items
    case _: Absent => Vector.empty
  }

  private[modeler] def _vector[A](value: Presence[Vector[A]], path: String, context: Context)
    (check: (A, String, Context) => Vector[Diagnostic]): Vector[Diagnostic] =
    _presence(value, path, context) { (items, location, ctx) =>
      items.zipWithIndex.flatMap { case (item, index) => check(item, s"$location[$index]", ctx) }
    }

  private def _boundary(value: ReferenceBoundary, path: String, context: Context): Vector[Diagnostic] = value match {
    case Local | External => Vector.empty
    case null => _required(value, path, context)
  }

  private def _reference(value: ModelReference, path: String, context: Context): Vector[Diagnostic] =
    if (value == null) _required(value, path, context)
    else _model_id(value.identity, s"$path.identity", context) ++ _boundary(value.boundary, s"$path.boundary", context)

  private[modeler] def _component_reference(value: VersionedComponentCapabilityReference, path: String,
    context: Context): Vector[Diagnostic] =
    if (value == null) _required(value, path, context)
    else _model_id(value.identity, s"$path.identity", context) ++ _text(value.contractVersion, s"$path.contractVersion", context)

  private def _application_reference(value: VersionedApplicationCapabilityReference, path: String,
    context: Context): Vector[Diagnostic] =
    if (value == null) _required(value, path, context)
    else _model_id(value.identity, s"$path.identity", context) ++ _text(value.contractVersion, s"$path.contractVersion", context)

  private def _link(value: ModelLink, path: String, context: Context): Vector[Diagnostic] = {
    val ctx = context.copy(source = Option(value.source))
    _required(value.role, s"$path.role", ctx) ++ _reference(value.target, s"$path.target", ctx) ++
      _direct(value.source, s"$path.source", ctx) ++ _origin(value.origin, s"$path.origin", ctx)
  }

  private def _trace(value: TraceabilityReference, path: String, context: Context): Vector[Diagnostic] = {
    val ctx = context.copy(source = Option(value.source))
    _required(value.role, s"$path.role", ctx) ++ _reference(value.target, s"$path.target", ctx) ++
      _direct(value.source, s"$path.source", ctx) ++ _origin(value.origin, s"$path.origin", ctx)
  }

  private def _label(value: LocalizedLabel, path: String, context: Context): Vector[Diagnostic] =
    if (value == null) _required(value, path, context)
    else _text(value.text, s"$path.text", context) ++
      (if (value.language == null) _required(value.language, s"$path.language", context)
       else value.language.toVector.flatMap(x => _text(x, s"$path.language", context)))

  private def _grounding(value: GroundingReference, path: String, context: Context): Vector[Diagnostic] = {
    val ctx = context.copy(source = Option(value.source))
    val target = value.target match {
      case ModelTarget(identity) => _model_id(identity, s"$path.target.identity", ctx)
      case TermTarget(identity) => _term_id(identity, s"$path.target.identity", ctx)
      case null => _required(value.target, s"$path.target", ctx)
    }
    target ++ _boundary(value.boundary, s"$path.boundary", ctx) ++
      _presence(value.relation, s"$path.relation", ctx)(_relation_id) ++
      _presence(value.profile, s"$path.profile", ctx)(_profile_id) ++
      _presence(value.context, s"$path.context", ctx)(_text) ++
      _presence(value.preferredLabel, s"$path.preferredLabel", ctx)(_label) ++
      _direct(value.source, s"$path.source", ctx) ++ _origin(value.origin, s"$path.origin", ctx)
  }

  private def _mapping(value: ApplicationComponentMapping, path: String, context: Context): Vector[Diagnostic] = {
    val ctx = context.copy(source = Option(value.source))
    _component_reference(value.target, s"$path.target", ctx) ++ _boundary(value.boundary, s"$path.boundary", ctx) ++
      _direct(value.source, s"$path.source", ctx) ++ _origin(value.origin, s"$path.origin", ctx)
  }

  private def _dependency(value: ApplicationDependency, path: String, context: Context): Vector[Diagnostic] = {
    val ctx = context.copy(source = Option(value.source))
    _application_reference(value.target, s"$path.target", ctx) ++ _boundary(value.boundary, s"$path.boundary", ctx) ++
      _direct(value.source, s"$path.source", ctx) ++ _origin(value.origin, s"$path.origin", ctx)
  }

  private def _key(value: DeclarationKey, path: String, context: Context): Vector[Diagnostic] =
    if (value == null) _required(value, path, context)
    else _component(value.component, s"$path.component", context) ++
      _required(value.direction, s"$path.direction", context) ++
      _component_reference(value.capability, s"$path.capability", context)

  private def _realization(value: RealizationEdge, path: String, context: Context): Vector[Diagnostic] = {
    val ctx = context.copy(source = Option(value.source))
    _key(value.declarationKey, s"$path.declarationKey", ctx) ++
      _required(value.targetKind, s"$path.targetKind", ctx) ++ _reference(value.target, s"$path.target", ctx) ++
      _component(value.targetComponent, s"$path.targetComponent", ctx) ++
      _text(value.targetContractVersion, s"$path.targetContractVersion", ctx) ++
      _text(value.generatedAbiVersion, s"$path.generatedAbiVersion", ctx) ++
      _direct(value.source, s"$path.source", ctx) ++ _origin(value.origin, s"$path.origin", ctx)
  }

  private def _compatibility(value: CompatibilityEdge, path: String, context: Context): Vector[Diagnostic] = {
    val ctx = context.copy(source = Option(value.source))
    _model_id(value.identity, s"$path.identity", ctx) ++
      _text(value.requiredVersion, s"$path.requiredVersion", ctx) ++
      _text(value.providedVersion, s"$path.providedVersion", ctx) ++
      _direct(value.source, s"$path.source", ctx) ++ _origin(value.origin, s"$path.origin", ctx)
  }

  private def _definition(identity: ModelElementId, version: String, name: String,
    summary: Presence[String], description: Presence[String], source: CapabilitySource,
    path: String, context: Context): Vector[Diagnostic] =
    _model_id(identity, s"$path.identity", context) ++ _text(version, s"$path.contractVersion", context) ++
      _text(name, s"$path.name", context) ++ _presence(summary, s"$path.summary", context)(_text) ++
      _presence(description, s"$path.description", context)(_text) ++ _direct(source, s"$path.source", context) ++
      (if (identity == null) Vector.empty else _authority(source, identity.modelId, s"$path.source.modelAuthority", context))

  private[modeler] def _document_shape(document: CapabilityDocument, index: Int): Vector[Diagnostic] = {
    val model = document.model
    val path = s"documents[$index].model"
    val applications = model.applicationCapabilities.zipWithIndex.flatMap { case (value, offset) =>
      val location = s"$path.applicationCapabilities[$offset]"
      val context = _document(document.scope, value.source)
      _definition(value.identity, value.contractVersion, value.name, value.summary, value.description,
        value.source, location, context) ++
        _vector(value.demandReferences, s"$location.demandReferences", context) { (edge, p, ctx) =>
          _link(edge, p, ctx) ++ (edge.role match {
            case Goal | UseCase | Scenario | UseCaseSlice | null => Vector.empty
            case _ => Vector(ctx.copy(source = Option(edge.source))._issue(DiagnosticKind.WrongKind, s"$p.role", "Demand permits only Goal, UseCase, Scenario or UseCaseSlice."))
          })
        } ++
        _vector(value.discoveryReferences, s"$location.discoveryReferences", context)(_link) ++
        _vector(value.componentMappings, s"$location.componentMappings", context)(_mapping) ++
        _vector(value.dependencies, s"$location.dependencies", context)(_dependency) ++
        _vector(value.grounding, s"$location.grounding", context)(_grounding) ++
        _vector(value.traceability, s"$location.traceability", context)(_trace)
    }
    val components = model.componentCapabilities.zipWithIndex.flatMap { case (value, offset) =>
      val location = s"$path.componentCapabilities[$offset]"
      val context = _document(document.scope, value.source)
      _definition(value.identity, value.contractVersion, value.name, value.summary, value.description,
        value.source, location, context) ++
        _vector(value.discoveryReferences, s"$location.discoveryReferences", context)(_link) ++
        _vector(value.grounding, s"$location.grounding", context)(_grounding) ++
        _vector(value.traceability, s"$location.traceability", context)(_trace)
    }
    val declarations = model.componentDeclarations.zipWithIndex.flatMap { case (value, offset) =>
      val location = s"$path.componentDeclarations[$offset]"
      val context = _document(document.scope, value.source)
      _component(value.component, s"$location.component", context) ++
        _required(value.direction, s"$location.direction", context) ++
        _component_reference(value.capability, s"$location.capability", context) ++
        _direct(value.source, s"$location.source", context) ++
        _vector(value.realizations, s"$location.realizations", context)(_realization) ++
        _vector(value.traceability, s"$location.traceability", context)(_trace)
    }
    applications ++ components ++ declarations ++
      _vector(model.compatibility, s"$path.compatibility", Context(Some(document.scope), None))(_compatibility)
  }
}
