package cozy.ui

import java.nio.charset.StandardCharsets
import java.security.MessageDigest

import cozy.ui.CozyLogicalUi._

/*
 * @since   Sep. 1, 2026
 * @version Sep. 1, 2026
 * @author  ASAMI, Tomoharu
 */
private[cozy] object CozyLogicalUiSemantics {
  sealed trait Purpose {
    def id: String
  }

  case object BrowsePurpose extends Purpose {
    val id = "browse"
  }

  case object InspectPurpose extends Purpose {
    val id = "inspect"
  }

  case object EditPurpose extends Purpose {
    val id = "edit"
  }

  case object ConfirmPurpose extends Purpose {
    val id = "confirm"
  }

  sealed trait Display {
    def id: String
  }

  case object CollectionDisplay extends Display {
    val id = "collection"
  }

  case object DetailDisplay extends Display {
    val id = "detail"
  }

  case object FormDisplay extends Display {
    val id = "form"
  }

  case object StatusDisplay extends Display {
    val id = "status"
  }

  sealed trait InteractionPattern {
    def id: String
  }

  case object NavigatePattern extends InteractionPattern {
    val id = "navigate"
  }

  case object SelectPattern extends InteractionPattern {
    val id = "select"
  }

  case object InputPattern extends InteractionPattern {
    val id = "input"
  }

  case object CommandPattern extends InteractionPattern {
    val id = "command"
  }

  case object ObservePattern extends InteractionPattern {
    val id = "observe"
  }

  sealed trait ValidationAuthority {
    def id: String
  }

  case object LocalDeterministicValidation extends ValidationAuthority {
    val id = "local-deterministic"
  }

  case object ContextDependentValidation extends ValidationAuthority {
    val id = "context-dependent"
  }

  case object ServerAuthoritativeValidation extends ValidationAuthority {
    val id = "server-authoritative"
  }

  sealed trait UiInteractionState {
    def id: String
  }

  case object IdleInteractionState extends UiInteractionState {
    val id = "idle"
  }

  case object PendingInteractionState extends UiInteractionState {
    val id = "pending"
  }

  case object SucceededInteractionState extends UiInteractionState {
    val id = "succeeded"
  }

  case object FailedInteractionState extends UiInteractionState {
    val id = "failed"
  }

  sealed trait Multiplicity {
    def id: String
  }

  case object ExactlyOneMultiplicity extends Multiplicity {
    val id = "exactly-one"
  }

  case object ZeroOrOneMultiplicity extends Multiplicity {
    val id = "zero-or-one"
  }

  case object OneOrMoreMultiplicity extends Multiplicity {
    val id = "one-or-more"
  }

  case object ZeroOrMoreMultiplicity extends Multiplicity {
    val id = "zero-or-more"
  }

  sealed trait ConstraintKind {
    def id: String
  }

  case object DatatypeConstraint extends ConstraintKind {
    val id = "datatype"
  }

  case object ValueConstraint extends ConstraintKind {
    val id = "value"
  }

  case object AggregateInvariant extends ConstraintKind {
    val id = "aggregate-invariant"
  }

  case object OperationPrecondition extends ConstraintKind {
    val id = "operation-precondition"
  }

  case object OperationPostcondition extends ConstraintKind {
    val id = "operation-postcondition"
  }

  /** Domain StateMachine state; it is not a Workflow or UI lifecycle value. */
  final case class DomainStateReference(stateMachineBinding: ComponentBinding, stateId: String)

  /** Opaque CNCF Workflow state; it is not a domain or UI state. */
  final case class WorkflowStateReference(workflowId: String, stateId: String)

  final case class RegionDisplayBinding(regionId: String, display: Display)

  final case class InteractionPatternBinding(interactionId: String, pattern: InteractionPattern)

  final case class InteractionStateBinding(interactionId: String, state: UiInteractionState)

  final case class ScreenSemantics(
    screenId: String,
    purpose: Purpose,
    regions: Vector[RegionDisplayBinding],
    interactions: Vector[InteractionPatternBinding],
    interactionStates: Vector[InteractionStateBinding]
  )

  final case class ComponentSemanticBinding(
    role: ComponentRole,
    binding: ComponentBinding,
    multiplicity: Option[Multiplicity] = None,
    powertypeVariantId: Option[String] = None,
    domainStates: Vector[DomainStateReference] = Vector.empty
  )

  final case class ConstraintIdentity(constraintId: String, detailCode: String)

  final case class ConstraintDeclaration(
    identity: ConstraintIdentity,
    kind: ConstraintKind,
    binding: ComponentBinding,
    validation: ValidationAuthority
  )

  final case class FeedbackConstraintAssociation(feedbackId: String, constraint: ConstraintIdentity)

  final case class StateMachineTransitionAction(
    step: UiUseCaseStep,
    screenId: String,
    interactionId: String,
    operation: ComponentBinding,
    stateMachine: ComponentBinding,
    from: DomainStateReference,
    to: DomainStateReference,
    workflowState: Option[WorkflowStateReference],
    interactionState: UiInteractionState
  )

  final case class LogicalUiSemanticsInput(
    projectionIdentity: String,
    screens: Vector[ScreenSemantics],
    componentBindings: Vector[ComponentSemanticBinding],
    constraints: Vector[ConstraintDeclaration],
    feedbackAssociations: Vector[FeedbackConstraintAssociation],
    transitionActions: Vector[StateMachineTransitionAction]
  )

  final class LogicalUiSemanticProjection private[CozyLogicalUiSemantics] (
    val identity: String,
    val projectionIdentity: String,
    val input: LogicalUiSemanticsInput
  ) {
    def canonicalContent: String = _canonical_content(projectionIdentity, input)
  }

  private val _schema = "cozy.logical-ui-semantics.v1"
  private val _version = 1

  def schema: String = _schema

  def version: Int = _version

  def project(
    projection: LogicalUiProjection,
    input: LogicalUiSemanticsInput
  ): Either[LogicalUiError, LogicalUiSemanticProjection] = {
    if (projection == null)
      Left(LogicalUiError("LUI43_SEMANTICS_PROJECTION_IDENTITY_INVALID", "projection", "an exact LogicalUiProjection is required"))
    else if (input == null)
      Left(LogicalUiError("LUI43_SEMANTICS_INPUT_INVALID", "semantics", "typed semantic input is required"))
    else if (_text_error(input.projectionIdentity).isDefined || input.projectionIdentity != projection.identity)
      Left(LogicalUiError("LUI43_SEMANTICS_PROJECTION_IDENTITY_INVALID", "semantics.projectionIdentity", "semantic input must bind the exact supplied LogicalUiProjection identity"))
    else {
      _normalize_input(projection, input) match {
        case Left(error) => Left(error)
        case Right(normalized) =>
          val content = _canonical_content(projection.identity, normalized)
          Right(new LogicalUiSemanticProjection(_identity(content), projection.identity, normalized))
      }
    }
  }

  private def _normalize_input(
    projection: LogicalUiProjection,
    input: LogicalUiSemanticsInput
  ): Either[LogicalUiError, LogicalUiSemanticsInput] = {
    _normalize_screens(projection, input.screens) match {
      case Left(error) => Left(error)
      case Right(screens) =>
        _normalize_component_bindings(projection, input.componentBindings) match {
          case Left(error) => Left(error)
          case Right(bindings) =>
            _normalize_constraints(projection, bindings, input.constraints) match {
              case Left(error) => Left(error)
              case Right(constraints) =>
                _normalize_feedback(constraints, input.feedbackAssociations) match {
                  case Left(error) => Left(error)
                  case Right(feedback) =>
                    _normalize_transitions(projection, bindings, screens, input.transitionActions) match {
                      case Left(error) => Left(error)
                      case Right(transitions) =>
                        Right(input.copy(
                          projectionIdentity = projection.identity,
                          screens = screens,
                          componentBindings = bindings,
                          constraints = constraints,
                          feedbackAssociations = feedback,
                          transitionActions = transitions
                        ))
                    }
                }
            }
        }
    }
  }

  private def _normalize_screens(
    projection: LogicalUiProjection,
    values: Vector[ScreenSemantics]
  ): Either[LogicalUiError, Vector[ScreenSemantics]] = {
    val semantics = Option(values).getOrElse(Vector.empty)
    val projected = projection.input.screens
    if (semantics.isEmpty)
      Left(LogicalUiError("LUI43_SEMANTICS_SCREEN_COVERAGE", "semantics.screens", "every projected LogicalScreen needs one semantic binding"))
    else if (semantics.exists(_ == null))
      Left(LogicalUiError("LUI43_SEMANTICS_SCREEN_COVERAGE", "semantics.screens", "screen semantic identity must be nonempty and trimmed"))
    else {
      val ids = semantics.map(_.screenId)
      if (ids.exists(value => _text_error(value).isDefined))
        Left(LogicalUiError("LUI43_SEMANTICS_SCREEN_COVERAGE", "semantics.screens", "screen semantic identity must be nonempty and trimmed"))
      else if (ids.distinct.size != ids.size)
        Left(LogicalUiError("LUI43_SEMANTICS_SCREEN_COVERAGE", "semantics.screens", "screen semantic identity must be unique"))
      else if (ids.toSet != projected.map(_.id).toSet)
        Left(LogicalUiError("LUI43_SEMANTICS_SCREEN_COVERAGE", "semantics.screens", "screen semantic bindings must cover the projection exactly"))
      else {
        val errors = semantics.flatMap { value =>
          _screen_semantic_error(value, projected.find(_.id == value.screenId).get)
        }
        errors.headOption match {
          case Some(error) => Left(error)
          case None => Right(semantics.map(_normalize_screen).sortBy(_.screenId))
        }
      }
    }
  }

  private def _screen_semantic_error(
    value: ScreenSemantics,
    projected: LogicalScreen
  ): Option[LogicalUiError] = {
    if (value.purpose == null)
      Some(LogicalUiError("LUI43_SEMANTICS_PURPOSE_INVALID", s"semantics.screens.${value.screenId}.purpose", "purpose must use the closed v1 catalog"))
    else {
      val regions = Option(value.regions).getOrElse(Vector.empty)
      if (regions.exists(_ == null))
        Some(LogicalUiError("LUI43_SEMANTICS_REGION_COVERAGE", s"semantics.screens.${value.screenId}.regions", "region display binding identity must be nonempty and trimmed"))
      else {
      val regionids = regions.map(_.regionId)
      if (regionids.exists(value => _text_error(value).isDefined))
        Some(LogicalUiError("LUI43_SEMANTICS_REGION_COVERAGE", s"semantics.screens.${value.screenId}.regions", "region display binding identity must be nonempty and trimmed"))
      else if (regionids.distinct.size != regionids.size || regionids.toSet != projected.regions.map(_.id).toSet)
        Some(LogicalUiError("LUI43_SEMANTICS_REGION_COVERAGE", s"semantics.screens.${value.screenId}.regions", "region display bindings must cover every projected region exactly once"))
      else if (regions.exists(_.display == null))
        Some(LogicalUiError("LUI43_SEMANTICS_DISPLAY_INVALID", s"semantics.screens.${value.screenId}.regions", "display must use the closed v1 catalog"))
      else {
        val interactions = Option(value.interactions).getOrElse(Vector.empty)
        if (interactions.exists(_ == null))
          Some(LogicalUiError("LUI43_SEMANTICS_INTERACTION_PATTERN_INVALID", s"semantics.screens.${value.screenId}.interactions", "interaction pattern binding identity must be nonempty and trimmed"))
        else {
        val interactionids = interactions.map(_.interactionId)
        if (interactionids.exists(value => _text_error(value).isDefined))
          Some(LogicalUiError("LUI43_SEMANTICS_INTERACTION_PATTERN_INVALID", s"semantics.screens.${value.screenId}.interactions", "interaction pattern binding identity must be nonempty and trimmed"))
        else if (interactionids.distinct.size != interactionids.size || interactionids.toSet != projected.interactions.map(_.id).toSet)
          Some(LogicalUiError("LUI43_SEMANTICS_INTERACTION_PATTERN_INVALID", s"semantics.screens.${value.screenId}.interactions", "interaction pattern bindings must cover every projected interaction exactly once"))
        else if (interactions.exists(_.pattern == null))
          Some(LogicalUiError("LUI43_SEMANTICS_INTERACTION_PATTERN_INVALID", s"semantics.screens.${value.screenId}.interactions", "interaction pattern must use the closed v1 catalog"))
        else {
          val incompatible = interactions.flatMap { item =>
            projected.interactions.find(_.id == item.interactionId).flatMap { interaction =>
              if (_pattern_compatible(item.pattern, interaction.kind)) None else Some(item)
            }
          }
          if (incompatible.nonEmpty)
            Some(LogicalUiError("LUI43_SEMANTICS_INTERACTION_PATTERN_INVALID", s"semantics.screens.${value.screenId}.interactions.${incompatible.head.interactionId}", "interaction pattern is incompatible with the projected interaction kind"))
          else {
            val states = Option(value.interactionStates).getOrElse(Vector.empty)
            if (states.exists(_ == null))
              Some(LogicalUiError("LUI43_SEMANTICS_INTERACTION_STATE_INVALID", s"semantics.screens.${value.screenId}.interactionStates", "interaction state binding identity must be nonempty and trimmed"))
            else {
            val stateids = states.map(_.interactionId)
            if (stateids.exists(value => _text_error(value).isDefined))
              Some(LogicalUiError("LUI43_SEMANTICS_INTERACTION_STATE_INVALID", s"semantics.screens.${value.screenId}.interactionStates", "interaction state binding identity must be nonempty and trimmed"))
            else if (stateids.distinct.size != stateids.size || stateids.toSet != projected.interactions.map(_.id).toSet)
              Some(LogicalUiError("LUI43_SEMANTICS_INTERACTION_STATE_INVALID", s"semantics.screens.${value.screenId}.interactionStates", "UI interaction states must cover every projected interaction exactly once"))
            else if (states.exists(_.state == null))
              Some(LogicalUiError("LUI43_SEMANTICS_INTERACTION_STATE_INVALID", s"semantics.screens.${value.screenId}.interactionStates", "UI interaction state must use the closed v1 catalog"))
            else None
            }
          }
        }
      }
      }
    }
  }
  }

  private def _normalize_screen(value: ScreenSemantics): ScreenSemantics =
    value.copy(
      regions = value.regions.sortBy(_.regionId),
      interactions = value.interactions.sortBy(_.interactionId),
      interactionStates = value.interactionStates.sortBy(_.interactionId)
    )

  private def _normalize_component_bindings(
    projection: LogicalUiProjection,
    values: Vector[ComponentSemanticBinding]
  ): Either[LogicalUiError, Vector[ComponentSemanticBinding]] = {
    val bindings = Option(values).getOrElse(Vector.empty)
    if (bindings.isEmpty)
      Left(LogicalUiError("LUI43_SEMANTICS_COMPONENT_BINDING_INVALID", "semantics.componentBindings", "at least one typed Component semantic binding is required"))
    else {
      val errors = bindings.zipWithIndex.flatMap { case (value, index) =>
        _component_semantic_error(projection, bindings, value, s"semantics.componentBindings[$index]")
      }
      errors.headOption match {
        case Some(error) => Left(error)
        case None =>
          val keys = bindings.map(value => (value.role, _binding_key(value.binding)))
          if (keys.distinct.size != keys.size)
            Left(LogicalUiError("LUI43_SEMANTICS_COMPONENT_BINDING_INVALID", "semantics.componentBindings", "role and binding identity must be unique"))
          else {
            val normalized = bindings.map { value =>
              value.copy(domainStates = value.domainStates.sortBy(reference => _domain_state_key(reference)))
            }
            Right(normalized.sortBy(value => (value.role.id, _binding_key(value.binding))))
          }
      }
    }
  }

  private def _component_semantic_error(
    projection: LogicalUiProjection,
    values: Vector[ComponentSemanticBinding],
    value: ComponentSemanticBinding,
    path: String
  ): Option[LogicalUiError] = {
    if (value == null || value.role == null || value.binding == null)
      Some(LogicalUiError("LUI43_SEMANTICS_COMPONENT_BINDING_INVALID", path, "role and exact Component binding are required"))
    else if (!_projection_admits_role(projection, value.role, value.binding))
      Some(LogicalUiError("LUI43_SEMANTICS_COMPONENT_BINDING_INVALID", path, "role and binding must be admitted by the LogicalUiProjection"))
    else if (value.multiplicity == null || value.powertypeVariantId == null || value.domainStates == null)
      Some(LogicalUiError("LUI43_SEMANTICS_COMPONENT_BINDING_INVALID", path, "semantic options and state references must be explicit"))
    else if (value.multiplicity.exists(item => item == null))
      Some(LogicalUiError("LUI43_SEMANTICS_MULTIPLICITY_INVALID", s"$path.multiplicity", "multiplicity must contain one closed value"))
    else if (value.role == ValueRole || value.role == DatatypeRole) {
      if (value.multiplicity.isEmpty)
        Some(LogicalUiError("LUI43_SEMANTICS_MULTIPLICITY_INVALID", s"$path.multiplicity", "Value and Datatype semantics require one closed multiplicity"))
      else if (value.powertypeVariantId.nonEmpty || value.domainStates.nonEmpty)
        Some(LogicalUiError("LUI43_SEMANTICS_COMPONENT_BINDING_INVALID", path, "Powertype and StateMachine metadata cannot be attached to Value or Datatype"))
      else None
    } else if (value.multiplicity.nonEmpty)
      Some(LogicalUiError("LUI43_SEMANTICS_MULTIPLICITY_INVALID", s"$path.multiplicity", "multiplicity is admitted only for Value or Datatype"))
    else if (value.role == PowertypeRole) {
      if (value.powertypeVariantId.isEmpty || _text_error(value.powertypeVariantId.get).isDefined)
        Some(LogicalUiError("LUI43_SEMANTICS_COMPONENT_BINDING_INVALID", s"$path.powertypeVariantId", "Powertype semantics require a nonempty opaque variant ID"))
      else if (value.domainStates.nonEmpty)
        Some(LogicalUiError("LUI43_SEMANTICS_COMPONENT_BINDING_INVALID", path, "StateMachine metadata cannot be attached to Powertype"))
      else None
    } else if (value.powertypeVariantId.nonEmpty)
      Some(LogicalUiError("LUI43_SEMANTICS_COMPONENT_BINDING_INVALID", s"$path.powertypeVariantId", "Powertype variant IDs are admitted only for Powertype"))
    else if (value.role == StateMachineRole) {
      if (value.domainStates.isEmpty)
        Some(LogicalUiError("LUI43_SEMANTICS_STATE_INVALID", s"$path.domainStates", "StateMachine semantics require explicit domain state references"))
      else if (value.domainStates.exists(reference => reference == null || reference.stateMachineBinding != value.binding || _text_error(reference.stateId).isDefined))
        Some(LogicalUiError("LUI43_SEMANTICS_STATE_INVALID", s"$path.domainStates", "domain state references must bind the exact StateMachine and nonempty state IDs"))
      else if (value.domainStates.map(reference => (reference.stateMachineBinding, reference.stateId)).distinct.size != value.domainStates.size)
        Some(LogicalUiError("LUI43_SEMANTICS_STATE_INVALID", s"$path.domainStates", "domain state reference identity must be unique"))
      else None
    } else if (value.domainStates.nonEmpty)
      Some(LogicalUiError("LUI43_SEMANTICS_STATE_INVALID", s"$path.domainStates", "domain state references are admitted only for StateMachine"))
    else None
  }

  private def _projection_admits_role(
    projection: LogicalUiProjection,
    role: ComponentRole,
    binding: ComponentBinding
  ): Boolean = {
    val screenroles = projection.input.screens.flatMap { screen =>
      Vector((screen.subject.role, screen.subject.binding)) ++ screen.interactions.flatMap(_.componentUsages.map(usage => (usage.role, usage.binding)))
    }
    val boundaryroles = projection.input.aggregateBoundaries.flatMap { boundary =>
      Vector((AggregateRole, boundary.aggregate)) ++ boundary.publicOperations.map(binding => (OperationRole, binding))
    }
    (screenroles ++ boundaryroles).contains((role, binding))
  }

  private def _normalize_constraints(
    projection: LogicalUiProjection,
    bindings: Vector[ComponentSemanticBinding],
    values: Vector[ConstraintDeclaration]
  ): Either[LogicalUiError, Vector[ConstraintDeclaration]] = {
    val constraints = Option(values).getOrElse(Vector.empty)
    if (constraints.isEmpty)
      Left(LogicalUiError("LUI43_SEMANTICS_CONSTRAINT_INVALID", "semantics.constraints", "explicit typed constraints are required"))
    else {
      val errors = constraints.zipWithIndex.flatMap { case (value, index) =>
        _constraint_error(projection, bindings, value, s"semantics.constraints[$index]")
      }
      errors.headOption match {
        case Some(error) => Left(error)
        case None =>
          val ids = constraints.map(_.identity)
          if (ids.distinct.size != ids.size)
            Left(LogicalUiError("LUI43_SEMANTICS_CONSTRAINT_INVALID", "semantics.constraints", "constraint identity must be the unique exact pair of constraintId and detailCode"))
          else {
            val operationbindings = bindings.collect { case value if value.role == OperationRole => value.binding }.distinct
            val missingdbc = operationbindings.find(binding =>
              !constraints.exists(value => value.binding == binding && value.kind == OperationPrecondition) ||
                !constraints.exists(value => value.binding == binding && value.kind == OperationPostcondition)
            )
            missingdbc match {
              case Some(binding) => Left(LogicalUiError("LUI43_SEMANTICS_CONSTRAINT_INVALID", s"semantics.constraints.${binding.exportId}", "each Operation semantic binding requires explicit precondition and postcondition identities"))
              case None => Right(constraints.sortBy(value => (value.identity.constraintId, value.identity.detailCode)))
            }
          }
      }
    }
  }

  private def _constraint_error(
    projection: LogicalUiProjection,
    bindings: Vector[ComponentSemanticBinding],
    value: ConstraintDeclaration,
    path: String
  ): Option[LogicalUiError] = {
    if (value == null || value.identity == null || value.kind == null || value.binding == null || value.validation == null)
      Some(LogicalUiError("LUI43_SEMANTICS_CONSTRAINT_INVALID", path, "constraint identity, category, binding, and validation authority are required"))
    else if (_text_error(value.identity.constraintId).isDefined || _text_error(value.identity.detailCode).isDefined)
      Some(LogicalUiError("LUI43_SEMANTICS_CONSTRAINT_INVALID", s"$path.identity", "constraintId and detailCode must be nonempty opaque text"))
    else if (!_projection_admits_binding(projection, value.binding))
      Some(LogicalUiError("LUI43_SEMANTICS_CONSTRAINT_INVALID", s"$path.binding", "constraint binding must be an exact public projection binding"))
    else if (!_binding_has_kind(bindings, value.binding, value.kind))
      Some(LogicalUiError("LUI43_SEMANTICS_CONSTRAINT_INVALID", s"$path.kind", "constraint category is incompatible with its Component role"))
    else None
  }

  private def _binding_has_kind(
    bindings: Vector[ComponentSemanticBinding],
    binding: ComponentBinding,
    kind: ConstraintKind
  ): Boolean = {
    val role = kind match {
      case DatatypeConstraint => DatatypeRole
      case ValueConstraint => ValueRole
      case AggregateInvariant => AggregateRole
      case OperationPrecondition => OperationRole
      case OperationPostcondition => OperationRole
      case _ => null
    }
    role != null && bindings.exists(value => value.role == role && value.binding == binding)
  }

  private def _projection_admits_binding(projection: LogicalUiProjection, binding: ComponentBinding): Boolean =
    projection.candidate.input.componentBindings.contains(binding)

  private def _normalize_feedback(
    constraints: Vector[ConstraintDeclaration],
    values: Vector[FeedbackConstraintAssociation]
  ): Either[LogicalUiError, Vector[FeedbackConstraintAssociation]] = {
    val feedback = Option(values).getOrElse(Vector.empty)
    val declarations = constraints.map(_.identity).toSet
    val errors = feedback.zipWithIndex.flatMap { case (value, index) =>
      if (value == null || _text_error(value.feedbackId).isDefined || value.constraint == null)
        Vector(LogicalUiError("LUI43_SEMANTICS_FEEDBACK_CONSTRAINT_INVALID", s"semantics.feedbackAssociations[$index]", "feedback association must have an explicit ID and whole constraint identity"))
      else if (!declarations.contains(value.constraint))
        Vector(LogicalUiError("LUI43_SEMANTICS_FEEDBACK_CONSTRAINT_INVALID", s"semantics.feedbackAssociations[$index].constraint", "feedback must reference the exact declared constraintId/detailCode pair"))
      else Vector.empty
    }
    errors.headOption match {
      case Some(error) => Left(error)
      case None =>
        val keys = feedback.map(value => (value.feedbackId, value.constraint))
        if (keys.distinct.size != keys.size)
          Left(LogicalUiError("LUI43_SEMANTICS_FEEDBACK_CONSTRAINT_INVALID", "semantics.feedbackAssociations", "feedback constraint associations must be unique"))
        else Right(feedback.sortBy(value => (value.feedbackId, value.constraint.constraintId, value.constraint.detailCode)))
    }
  }

  private def _normalize_transitions(
    projection: LogicalUiProjection,
    bindings: Vector[ComponentSemanticBinding],
    screens: Vector[ScreenSemantics],
    values: Vector[StateMachineTransitionAction]
  ): Either[LogicalUiError, Vector[StateMachineTransitionAction]] = {
    val actions = Option(values).getOrElse(Vector.empty)
    val errors = actions.zipWithIndex.flatMap { case (value, index) =>
      _transition_error(projection, bindings, screens, value, s"semantics.transitionActions[$index]")
    }
    errors.headOption match {
      case Some(error) => Left(error)
      case None =>
        val keys = actions.map(_transition_key)
        if (keys.distinct.size != keys.size)
          Left(LogicalUiError("LUI43_SEMANTICS_TRANSITION_ADMISSION_INVALID", "semantics.transitionActions", "transition action identity must be unique"))
        else Right(actions.sortBy(_transition_key))
    }
  }

  private def _transition_error(
    projection: LogicalUiProjection,
    bindings: Vector[ComponentSemanticBinding],
    screens: Vector[ScreenSemantics],
    value: StateMachineTransitionAction,
    path: String
  ): Option[LogicalUiError] = {
    if (value == null || value.step == null || value.from == null || value.to == null || value.workflowState == null || value.interactionState == null)
      Some(LogicalUiError("LUI43_SEMANTICS_TRANSITION_ADMISSION_INVALID", path, "transition action requires explicit step, states, and UI interaction state"))
    else if (value.step.path == SystemOnlyPath)
      Some(LogicalUiError("LUI43_SEMANTICS_TRANSITION_ADMISSION_INVALID", s"$path.step", "system-only UI UseCase steps cannot expose a StateMachine action"))
    else if (!_projection_step_matches(projection, value.step))
      Some(LogicalUiError("LUI43_SEMANTICS_TRANSITION_ADMISSION_INVALID", s"$path.step", "transition action must name an exact declared UI UseCase step"))
    else if (!_text_error(value.screenId).isDefined && !_text_error(value.interactionId).isDefined) {
      projection.input.mappings.find(mapping =>
        mapping.uiUseCase == value.step.uiUseCase && mapping.stepId == value.step.stepId &&
          mapping.screenId == value.screenId && mapping.interactionId == value.interactionId
      ) match {
        case None => Some(LogicalUiError("LUI43_SEMANTICS_TRANSITION_ADMISSION_INVALID", s"$path.mapping", "transition action must name an exact mapped ScreenInteraction"))
        case Some(_) =>
          screens.find(_.screenId == value.screenId).flatMap(_.interactions.find(_.interactionId == value.interactionId)) match {
            case None => Some(LogicalUiError("LUI43_SEMANTICS_TRANSITION_ADMISSION_INVALID", s"$path.interaction", "transition action interaction must be projected and semantically bound"))
            case Some(interaction) =>
              val projectedscreen = projection.input.screens.find(_.id == value.screenId).get
              val projectedinteraction = projectedscreen.interactions.find(_.id == value.interactionId).get
              val operationused = projectedinteraction.componentUsages.exists(usage => usage.role == OperationRole && usage.binding == value.operation)
              val statemachineused = projectedinteraction.componentUsages.exists(usage => usage.role == StateMachineRole && usage.binding == value.stateMachine)
              val mutationmatches = projectedinteraction.mutation.forall(_.operation == value.operation)
              if (value.operation == null || !operationused)
                Some(LogicalUiError("LUI43_SEMANTICS_TRANSITION_ADMISSION_INVALID", s"$path.operation", "transition action requires the exact public Operation role usage"))
              else if (value.stateMachine == null || !statemachineused)
                Some(LogicalUiError("LUI43_SEMANTICS_TRANSITION_ADMISSION_INVALID", s"$path.stateMachine", "transition action requires the exact StateMachine role usage"))
              else if (!mutationmatches)
                Some(LogicalUiError("LUI43_SEMANTICS_TRANSITION_ADMISSION_INVALID", s"$path.operation", "transition operation must equal the mapped interaction mutation operation"))
              else if (!_binding_has_role(bindings, value.operation, OperationRole) || !_binding_has_role(bindings, value.stateMachine, StateMachineRole))
                Some(LogicalUiError("LUI43_SEMANTICS_TRANSITION_ADMISSION_INVALID", path, "transition roles must be explicitly admitted semantic bindings"))
              else if (value.from.stateMachineBinding != value.stateMachine || value.to.stateMachineBinding != value.stateMachine || _text_error(value.from.stateId).isDefined || _text_error(value.to.stateId).isDefined)
                Some(LogicalUiError("LUI43_SEMANTICS_STATE_INVALID", s"$path.states", "domain transition states must bind the exact StateMachine and nonempty state IDs"))
              else if (value.workflowState.exists(state => state == null || _text_error(state.workflowId).isDefined || _text_error(state.stateId).isDefined))
                Some(LogicalUiError("LUI43_SEMANTICS_STATE_INVALID", s"$path.workflowState", "Workflow state is opaque and must remain a separate nonempty typed value"))
              else None
          }
      }
    } else Some(LogicalUiError("LUI43_SEMANTICS_TRANSITION_ADMISSION_INVALID", path, "transition screen and interaction IDs must be nonempty and trimmed"))
  }

  private def _projection_step_matches(projection: LogicalUiProjection, value: UiUseCaseStep): Boolean =
    projection.input.steps.exists(step => step == value)

  private def _binding_has_role(
    bindings: Vector[ComponentSemanticBinding],
    binding: ComponentBinding,
    role: ComponentRole
  ): Boolean =
    binding != null && bindings.exists(value => value.role == role && value.binding == binding)

  private def _pattern_compatible(pattern: InteractionPattern, kind: InteractionKind): Boolean = pattern match {
    case NavigatePattern => kind == NavigationInteraction
    case SelectPattern => kind == SelectionInteraction
    case InputPattern => kind == InputInteraction
    case CommandPattern => kind == InvocationInteraction
    case ObservePattern => Set[InteractionKind](EntryInteraction, QueryInteraction, ObservationInteraction, FeedbackInteraction).contains(kind)
    case _ => false
  }

  private def _text_error(value: String): Option[String] =
    if (value == null || value.isEmpty || value != value.trim) Some(value) else None

  private def _binding_key(value: ComponentBinding): (String, String, String, String) =
    (value.component.namespace, value.component.id, value.component.version, value.exportId)

  private def _domain_state_key(value: DomainStateReference): ((String, String, String, String), String) =
    (_binding_key(value.stateMachineBinding), value.stateId)

  private def _step_key(value: UiUseCaseStep): (String, String, String) =
    (value.uiUseCase.layer.id, value.uiUseCase.id, value.stepId)

  private def _transition_key(value: StateMachineTransitionAction): ((String, String, String), String, String, (String, String, String, String), (String, String, String, String), ((String, String, String, String), String), ((String, String, String, String), String), String) =
    (
      (value.step.uiUseCase.layer.id, value.step.uiUseCase.id, value.step.stepId),
      value.screenId,
      value.interactionId,
      _binding_key(value.operation),
      _binding_key(value.stateMachine),
      (_binding_key(value.from.stateMachineBinding), value.from.stateId),
      (_binding_key(value.to.stateMachineBinding), value.to.stateId),
      value.interactionState.id
    )

  private def _canonical_content(
    projectionidentity: String,
    input: LogicalUiSemanticsInput
  ): String = _json_object(Vector(
    "schema" -> _json_string(_schema),
    "version" -> _version.toString,
    "projectionIdentity" -> _json_string(projectionidentity),
    "screens" -> _json_array(input.screens.map(_screen_json)),
    "componentBindings" -> _json_array(input.componentBindings.map(_component_semantic_json)),
    "constraints" -> _json_array(input.constraints.map(_constraint_json)),
    "feedbackAssociations" -> _json_array(input.feedbackAssociations.map(_feedback_json)),
    "transitionActions" -> _json_array(input.transitionActions.map(_transition_json))
  ))

  private def _screen_json(value: ScreenSemantics): String = _json_object(Vector(
    "screenId" -> _json_string(value.screenId),
    "purpose" -> _json_string(value.purpose.id),
    "regions" -> _json_array(value.regions.map(region => _json_object(Vector(
      "regionId" -> _json_string(region.regionId),
      "display" -> _json_string(region.display.id)
    )))),
    "interactions" -> _json_array(value.interactions.map(interaction => _json_object(Vector(
      "interactionId" -> _json_string(interaction.interactionId),
      "pattern" -> _json_string(interaction.pattern.id)
    )))),
    "interactionStates" -> _json_array(value.interactionStates.map(state => _json_object(Vector(
      "interactionId" -> _json_string(state.interactionId),
      "state" -> _json_string(state.state.id)
    ))))
  ))

  private def _component_semantic_json(value: ComponentSemanticBinding): String = _json_object(Vector(
    "role" -> _json_string(value.role.id),
    "binding" -> _component_binding_json(value.binding),
    "multiplicity" -> value.multiplicity.map(item => _json_string(item.id)).getOrElse("null"),
    "powertypeVariantId" -> value.powertypeVariantId.map(_json_string).getOrElse("null"),
    "domainStates" -> _json_array(value.domainStates.map(state => _json_object(Vector(
      "stateMachineBinding" -> _component_binding_json(state.stateMachineBinding),
      "stateId" -> _json_string(state.stateId)
    ))))
  ))

  private def _constraint_json(value: ConstraintDeclaration): String = _json_object(Vector(
    "constraintId" -> _json_string(value.identity.constraintId),
    "detailCode" -> _json_string(value.identity.detailCode),
    "kind" -> _json_string(value.kind.id),
    "binding" -> _component_binding_json(value.binding),
    "validation" -> _json_string(value.validation.id)
  ))

  private def _feedback_json(value: FeedbackConstraintAssociation): String = _json_object(Vector(
    "feedbackId" -> _json_string(value.feedbackId),
    "constraintId" -> _json_string(value.constraint.constraintId),
    "detailCode" -> _json_string(value.constraint.detailCode)
  ))

  private def _transition_json(value: StateMachineTransitionAction): String = _json_object(Vector(
    "step" -> _json_object(Vector(
      "layer" -> _json_string(value.step.uiUseCase.layer.id),
      "uiUseCaseId" -> _json_string(value.step.uiUseCase.id),
      "stepId" -> _json_string(value.step.stepId),
      "path" -> _json_string(value.step.path.id)
    )),
    "screenId" -> _json_string(value.screenId),
    "interactionId" -> _json_string(value.interactionId),
    "operation" -> _component_binding_json(value.operation),
    "stateMachine" -> _component_binding_json(value.stateMachine),
    "from" -> _domain_state_json(value.from),
    "to" -> _domain_state_json(value.to),
    "workflowState" -> value.workflowState.map(_workflow_state_json).getOrElse("null"),
    "interactionState" -> _json_string(value.interactionState.id)
  ))

  private def _domain_state_json(value: DomainStateReference): String = _json_object(Vector(
    "stateMachineBinding" -> _component_binding_json(value.stateMachineBinding),
    "stateId" -> _json_string(value.stateId)
  ))

  private def _workflow_state_json(value: WorkflowStateReference): String = _json_object(Vector(
    "workflowId" -> _json_string(value.workflowId),
    "stateId" -> _json_string(value.stateId)
  ))

  private def _component_binding_json(value: ComponentBinding): String = _json_object(Vector(
    "component" -> _json_object(Vector(
      "namespace" -> _json_string(value.component.namespace),
      "id" -> _json_string(value.component.id),
      "version" -> _json_string(value.component.version)
    )),
    "exportId" -> _json_string(value.exportId)
  ))

  private def _json_object(fields: Vector[(String, String)]): String =
    fields.map { case (name, value) => s"${_json_string(name)}:$value" }.mkString("{", ",", "}")

  private def _json_array(values: Vector[String]): String = values.mkString("[", ",", "]")

  private def _json_string(value: String): String = {
    val escaped = value.flatMap {
      case '"' => "\\\""
      case '\\' => "\\\\"
      case '\b' => "\\b"
      case '\f' => "\\f"
      case '\n' => "\\n"
      case '\r' => "\\r"
      case '\t' => "\\t"
      case character if character < ' ' => f"\\u${character.toInt}%04x"
      case character => character.toString
    }
    "\"" + escaped + "\""
  }

  private def _identity(value: String): String =
    "sha256:" + MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)).map(byte => f"${byte & 0xff}%02x").mkString
}
