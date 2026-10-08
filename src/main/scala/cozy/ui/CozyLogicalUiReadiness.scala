package cozy.ui

import cozy.ui.CozyLogicalUi._
import cozy.ui.CozyLogicalUiSelection._
import cozy.ui.CozyLogicalUiSemantics._
import play.api.libs.json._

/*
 * @since   Oct. 8, 2026
 * @version Oct. 8, 2026
 * @author  ASAMI, Tomoharu
 */
private[cozy] object CozyLogicalUiReadiness {
  sealed abstract class RegionRole { def id: String }
  case object PrimaryRegion extends RegionRole { val id = "primary" }
  case object SecondaryRegion extends RegionRole { val id = "secondary" }

  sealed abstract class ControlRole { def id: String }
  case object EntryControl extends ControlRole { val id = "entry" }
  case object NavigationControl extends ControlRole { val id = "navigation" }
  case object InputControl extends ControlRole { val id = "input" }
  case object SelectionControl extends ControlRole { val id = "selection" }
  case object CommandControl extends ControlRole { val id = "command" }
  case object ObservationControl extends ControlRole { val id = "observation" }
  case object FeedbackControl extends ControlRole { val id = "feedback" }

  sealed abstract class InputIntent { def id: String }
  case object TextInputIntent extends InputIntent { val id = "text" }
  case object DecimalInputIntent extends InputIntent { val id = "decimal" }

  sealed abstract class LocalRuleKind { def id: String }
  case object RequiredTextRule extends LocalRuleKind { val id = "required-text" }
  case object AsciiIdentifierRule extends LocalRuleKind { val id = "ascii-identifier" }

  sealed abstract class RecoveryKind { def id: String }
  case object RetryInteraction extends RecoveryKind { val id = "retry-interaction" }
  case object EditInput extends RecoveryKind { val id = "edit-input" }
  case object ReturnToEntry extends RecoveryKind { val id = "return-to-entry" }

  final case class InteractionRef(screenId: String, interactionId: String)
  final case class RegionRef(screenId: String, regionId: String)
  final case class RegionIntent(region: RegionRef, role: RegionRole, persistent: Boolean)
  final case class ProfileIntent(
    windowClass: AdaptiveIntent, detailPlacement: DetailPlacement,
    persistentNavigation: Boolean, regions: Vector[RegionIntent])
  final case class TextSource(textId: String, sourceIdentity: String, defaultText: String)
  final case class ControlSemantics(
    control: InteractionRef, role: ControlRole, labelTextId: String, focusOrder: Option[Int])
  final case class LocalRule(
    constraint: ConstraintIdentity, ruleKind: LocalRuleKind, feedbackId: String, messageTextId: String)
  final case class InputField(
    fieldId: String, control: InteractionRef, valueBinding: ComponentBinding,
    datatypeBinding: ComponentBinding, labelTextId: String, inputIntent: InputIntent,
    required: Boolean, initialValue: String, localRules: Vector[LocalRule])
  final case class RecoveryBinding(kind: RecoveryKind, step: UiUseCaseStep, target: InteractionRef)
  final case class OutcomeBinding(
    outcomeId: String, step: UiUseCaseStep, trigger: InteractionRef,
    feedbackControl: InteractionRef, feedbackState: FeedbackState,
    uiState: UiInteractionState, domainState: DomainStateReference,
    workflowState: Option[WorkflowStateReference], recoveries: Vector[RecoveryBinding],
    messageTextId: String)
  final case class ReadinessInput(
    semanticProjectionIdentity: String, profiles: Vector[ProfileIntent],
    controls: Vector[ControlSemantics], fields: Vector[InputField],
    textSources: Vector[TextSource], outcomes: Vector[OutcomeBinding])

  final class TargetReadyCandidate private[CozyLogicalUiReadiness] (
    val identity: String, val inputIdentity: String,
    val kernelCandidate: LogicalUiCandidate, val projection: LogicalUiProjection,
    val semantics: LogicalUiSemanticProjection, val input: ReadinessInput,
    val canonicalContent: String)

  final case class TargetReadyAcceptanceDecision(
    decisionId: String, candidateIdentity: String, reviewerId: String) {
    def decisionIdentity: String = CozyLogicalUiProjection.identity(Json.stringify(_object(Vector(
      "schema" -> JsString(schema), "version" -> JsNumber(version),
      "kind" -> JsString("acceptance-decision"), "decisionId" -> JsString(decisionId),
      "candidateIdentity" -> JsString(candidateIdentity), "reviewerId" -> JsString(reviewerId)))))
  }
  final class AcceptedTargetReadyLogicalUi private[CozyLogicalUiReadiness] (
    val identity: String, val candidate: TargetReadyCandidate, val decision: TargetReadyAcceptanceDecision)

  def schema: String = "cozy.logical-ui-target-ready.v1"
  def version: Int = 1

  def candidate(
    kernelCandidate: LogicalUiCandidate, projection: LogicalUiProjection,
    semantics: LogicalUiSemanticProjection, input: ReadinessInput
  ): Either[LogicalUiError, TargetReadyCandidate] = for {
    _ <- _check(kernelCandidate != null, "CHAIN_INVALID", "kernelCandidate", "kernel candidate required")
    _ <- _check(projection != null, "CHAIN_INVALID", "projection", "screen projection required")
    _ <- _check(semantics != null, "CHAIN_INVALID", "semantics", "semantic projection required")
    _ <- _check(input != null, "INPUT_INVALID", "input", "readiness input required")
    _ <- _check(projection.candidate.identity == kernelCandidate.identity &&
      projection.candidate.inputIdentity == kernelCandidate.inputIdentity &&
      projection.candidate.canonicalContent == kernelCandidate.canonicalContent,
      "CHAIN_INVALID", "projection.candidate", "exact kernel candidate required")
    _ <- _check(semantics.projectionIdentity == projection.identity,
      "CHAIN_INVALID", "semantics.projectionIdentity", "exact screen projection required")
    _ <- _check(_id_valid(input.semanticProjectionIdentity) && input.semanticProjectionIdentity == semantics.identity,
      "CHAIN_INVALID", "input.semanticProjectionIdentity", "exact semantic projection required")
    rebuilt <- CozyLogicalUi.candidate(kernelCandidate.input)
    rebuiltprojection <- CozyLogicalUi.project(rebuilt, projection.input)
    rebuiltsemantics <- CozyLogicalUiSemantics.project(rebuiltprojection, semantics.input)
    _ <- _check(rebuilt.identity == kernelCandidate.identity && rebuilt.inputIdentity == kernelCandidate.inputIdentity &&
      rebuiltprojection.identity == projection.identity && rebuiltsemantics.identity == semantics.identity,
      "CHAIN_INVALID", "kernelCandidate", "factory-produced source chain required")
    normalized <- _normalize(input, projection, semantics)
  } yield {
    val content = Json.stringify(_object(Vector(
      "schema" -> JsString(schema), "version" -> JsNumber(version),
      "kernelCandidateIdentity" -> JsString(kernelCandidate.identity),
      "kernelInputIdentity" -> JsString(kernelCandidate.inputIdentity),
      "projectionIdentity" -> JsString(projection.identity),
      "semanticProjectionIdentity" -> JsString(semantics.identity),
      "readiness" -> _input_json(normalized))))
    val inputidentity = _role_identity("consumed-input", content)
    new TargetReadyCandidate(_role_identity("candidate", content), inputidentity,
      kernelCandidate, projection, semantics, normalized, content)
  }

  def accept(
    completeCandidate: TargetReadyCandidate, decision: TargetReadyAcceptanceDecision
  ): Either[LogicalUiError, AcceptedTargetReadyLogicalUi] = for {
    _ <- _check(completeCandidate != null, "ACCEPTANCE_INVALID", "candidate", "complete candidate required")
    _ <- _check(decision != null, "ACCEPTANCE_INVALID", "decision", "attributed decision required")
    _ <- _check(_id_valid(decision.decisionId), "ACCEPTANCE_INVALID", "decision.decisionId", "trimmed decision ID required")
    _ <- _check(_id_valid(decision.candidateIdentity) && decision.candidateIdentity == completeCandidate.identity,
      "ACCEPTANCE_INVALID", "decision.candidateIdentity", "exact complete candidate required")
    _ <- _check(_id_valid(decision.reviewerId), "ACCEPTANCE_INVALID", "decision.reviewerId", "trimmed reviewer ID required")
  } yield {
    val content = Json.stringify(_object(Vector(
      "schema" -> JsString(schema), "version" -> JsNumber(version), "kind" -> JsString("accepted"),
      "candidateIdentity" -> JsString(completeCandidate.identity),
      "inputIdentity" -> JsString(completeCandidate.inputIdentity),
      "decisionIdentity" -> JsString(decision.decisionIdentity))))
    new AcceptedTargetReadyLogicalUi(CozyLogicalUiProjection.identity(content), completeCandidate, decision)
  }

  def verifyCurrent(
    value: TargetReadyCandidate, kernelCandidate: LogicalUiCandidate,
    projection: LogicalUiProjection, semantics: LogicalUiSemanticProjection, input: ReadinessInput
  ): Either[LogicalUiError, Unit] = for {
    _ <- _check(value != null, "CURRENTNESS_INVALID", "value", "complete candidate required")
    rebuilt <- candidate(kernelCandidate, projection, semantics, input)
    _ <- _check(value.identity == rebuilt.identity && value.inputIdentity == rebuilt.inputIdentity &&
      value.canonicalContent == rebuilt.canonicalContent,
      "CURRENTNESS_INVALID", "value", "complete authoritative input changed")
  } yield ()

  private def _normalize(
    input: ReadinessInput, projection: LogicalUiProjection, semantics: LogicalUiSemanticProjection
  ): Either[LogicalUiError, ReadinessInput] = {
    val interactions = projection.input.screens.flatMap(screen => screen.interactions.map(interaction =>
      InteractionRef(screen.id, interaction.id) -> interaction)).toMap
    val regions = projection.input.screens.flatMap(screen => screen.regions.map(region => RegionRef(screen.id, region.id))).toSet
    for {
      _ <- _vector(input.profiles, "input.profiles")
      _ <- _vector(input.controls, "input.controls")
      _ <- _vector(input.fields, "input.fields")
      _ <- _vector(input.textSources, "input.textSources")
      _ <- _vector(input.outcomes, "input.outcomes")
      _ <- _each(input.textSources, "input.textSources") { (text, path) => for {
        _ <- _check(_id_valid(text.textId), "TEXT_INVALID", path + ".textId", "trimmed text ID required")
        _ <- _check(_id_valid(text.sourceIdentity), "TEXT_INVALID", path + ".sourceIdentity", "trimmed source identity required")
        _ <- _check(text.defaultText != null && text.defaultText.trim.nonEmpty,
          "TEXT_INVALID", path + ".defaultText", "nonblank display text required")
      } yield () }
      _ <- _unique(input.textSources.map(_.textId), "input.textSources")
      _ <- _each(input.profiles, "input.profiles") { (profile, path) => for {
        _ <- _check(Set[AdaptiveIntent](CompactIntent, MediumIntent, ExpandedIntent).contains(profile.windowClass),
          "PROFILE_INVALID", path + ".windowClass", "closed window intent required")
        _ <- _check(profile.detailPlacement == (if (profile.windowClass == ExpandedIntent) UpdateDetailRegion else NavigateToDetail),
          "PROFILE_INVALID", path + ".detailPlacement", "placement must match shared selection intent")
        _ <- _vector(profile.regions, path + ".regions")
        _ <- _each(profile.regions, path + ".regions") { (region, regionpath) => for {
          _ <- _check(regions.contains(region.region), "REGION_INVALID", regionpath + ".region", "exact projected region required")
          _ <- _check(region.role != null, "REGION_INVALID", regionpath + ".role", "closed region role required")
        } yield () }
        _ <- _unique(profile.regions.map(_.region), path + ".regions")
        _ <- _check(profile.regions.map(_.region).toSet == regions, "REGION_INVALID", path + ".regions", "every region required")
        _ <- _check(projection.input.screens.forall(screen => profile.regions.exists(region =>
          region.region.screenId == screen.id && region.role == PrimaryRegion)),
          "REGION_INVALID", path + ".regions", "each screen requires a primary region")
      } yield () }
      _ <- _unique(input.profiles.map(_.windowClass), "input.profiles")
      _ <- _check(input.profiles.map(_.windowClass).toSet == Set[AdaptiveIntent](CompactIntent, MediumIntent, ExpandedIntent),
        "PROFILE_INVALID", "input.profiles", "compact, medium and expanded each required")
      _ <- _each(input.controls, "input.controls") { (control, path) => for {
        _ <- _check(interactions.contains(control.control), "CONTROL_INVALID", path + ".control", "exact interaction required")
        _ <- _check(control.role == _control_role(interactions(control.control).kind),
          "CONTROL_INVALID", path + ".role", "role must match source interaction kind")
        _ <- _text_ref(control.labelTextId, input, path + ".labelTextId")
        _ <- _check(control.focusOrder != null, "FOCUS_INVALID", path + ".focusOrder", "Option required")
        _ <- _check(if (_focusable(control.role)) control.focusOrder.exists(_ > 0) else control.focusOrder.isEmpty,
          "FOCUS_INVALID", path + ".focusOrder", "positive focus rank required only for interactive controls")
      } yield () }
      _ <- _unique(input.controls.map(_.control), "input.controls")
      _ <- _check(input.controls.map(_.control).toSet == interactions.keySet,
        "CONTROL_INVALID", "input.controls", "every interaction requires control semantics")
      _ <- _unique(input.controls.flatMap(_.focusOrder), "input.controls.focusOrder")
      _ <- _each(input.fields, "input.fields") { (field, path) => for {
        _ <- _check(_id_valid(field.fieldId), "FIELD_INVALID", path + ".fieldId", "trimmed field ID required")
        _ <- _check(interactions.get(field.control).exists(_.kind == InputInteraction),
          "FIELD_INVALID", path + ".control", "source input interaction required")
        _ <- _check(_field_binding(field.valueBinding, ValueRole, interactions(field.control), semantics),
          "FIELD_INVALID", path + ".valueBinding", "exact source Value binding required")
        _ <- _check(_field_binding(field.datatypeBinding, DatatypeRole, interactions(field.control), semantics),
          "FIELD_INVALID", path + ".datatypeBinding", "exact source Datatype binding required")
        _ <- _text_ref(field.labelTextId, input, path + ".labelTextId")
        _ <- _check(field.inputIntent != null, "FIELD_INVALID", path + ".inputIntent", "closed input intent required")
        _ <- _check(field.initialValue != null, "FIELD_INVALID", path + ".initialValue", "opaque nonnull initial value required")
        _ <- _vector(field.localRules, path + ".localRules")
        _ <- _each(field.localRules, path + ".localRules") { (rule, rulepath) => for {
          _ <- _check(rule.ruleKind != null, "RULE_INVALID", rulepath + ".ruleKind", "closed local rule kind required")
          _ <- _check(rule.constraint != null && semantics.input.constraints.exists(declaration =>
            declaration.identity == rule.constraint && declaration.validation == LocalDeterministicValidation &&
              ((declaration.kind == ValueConstraint && declaration.binding == field.valueBinding) ||
                (declaration.kind == DatatypeConstraint && declaration.binding == field.datatypeBinding))),
            "RULE_INVALID", rulepath + ".constraint", "exact local Value or Datatype constraint required")
          _ <- _check(_id_valid(rule.feedbackId) && projection.input.screens.find(_.id == field.control.screenId).exists(
            _.feedbackStates.exists(_.id == rule.feedbackId)) && semantics.input.feedbackAssociations.contains(
              FeedbackConstraintAssociation(rule.feedbackId, rule.constraint)),
            "RULE_INVALID", rulepath + ".feedbackId", "declared exact feedback association required")
          _ <- _text_ref(rule.messageTextId, input, rulepath + ".messageTextId")
        } yield () }
        _ <- _unique(field.localRules.map(rule => (rule.constraint, rule.ruleKind)), path + ".localRules")
        _ <- _check(!field.required || field.localRules.exists(_.ruleKind == RequiredTextRule),
          "RULE_INVALID", path + ".localRules", "required field needs RequiredTextRule")
      } yield () }
      _ <- _unique(input.fields.map(_.fieldId), "input.fields")
      _ <- _unique(input.fields.map(_.control), "input.fields.control")
      _ <- _check(input.fields.map(_.control).toSet == interactions.collect {
        case (ref, interaction) if interaction.kind == InputInteraction => ref
      }.toSet, "FIELD_INVALID", "input.fields", "every input control requires one field")
      _ <- _each(input.outcomes, "input.outcomes") { (outcome, path) => for {
        _ <- _check(_id_valid(outcome.outcomeId), "OUTCOME_INVALID", path + ".outcomeId", "trimmed outcome ID required")
        _ <- _mapped(outcome.step, outcome.trigger, projection, path + ".trigger")
        _ <- _mapped(outcome.step, outcome.feedbackControl, projection, path + ".feedbackControl")
        _ <- _check(projection.input.screens.find(_.id == outcome.feedbackControl.screenId).exists(
          _.feedbackStates.contains(outcome.feedbackState)),
          "OUTCOME_INVALID", path + ".feedbackState", "feedback must be declared on its screen")
        _ <- _check(outcome.uiState != null && outcome.uiState == _outcome_state(outcome.feedbackState),
          "OUTCOME_INVALID", path + ".uiState", "UI lifecycle must match outcome feedback")
        _ <- _check(outcome.domainState != null && semantics.input.componentBindings.exists(binding =>
          binding.role == StateMachineRole && binding.domainStates.contains(outcome.domainState)),
          "OUTCOME_INVALID", path + ".domainState", "admitted domain state required")
        _ <- _check(outcome.workflowState != null && outcome.workflowState.forall(workflow => workflow != null &&
          semantics.input.transitionActions.exists(action => action.screenId == outcome.trigger.screenId &&
            action.interactionId == outcome.trigger.interactionId && action.workflowState.contains(workflow))),
          "OUTCOME_INVALID", path + ".workflowState", "exact source Workflow transition evidence required")
        _ <- _text_ref(outcome.messageTextId, input, path + ".messageTextId")
        _ <- _vector(outcome.recoveries, path + ".recoveries")
        _ <- _each(outcome.recoveries, path + ".recoveries") { (recovery, recoverypath) => for {
          _ <- _mapped(recovery.step, recovery.target, projection, recoverypath + ".target")
          _ <- _check(recovery.kind match {
            case RetryInteraction => recovery.target == outcome.trigger &&
              Set[InteractionKind](EntryInteraction, InvocationInteraction).contains(interactions(recovery.target).kind)
            case EditInput => interactions(recovery.target).kind == InputInteraction
            case ReturnToEntry => interactions(recovery.target).kind == EntryInteraction
            case _ => false
          }, "RECOVERY_INVALID", recoverypath + ".kind", "recovery kind must match mapped target")
        } yield () }
        _ <- _unique(outcome.recoveries, path + ".recoveries")
      } yield () }
      _ <- _unique(input.outcomes.map(_.outcomeId), "input.outcomes")
    } yield input.copy(
      profiles = input.profiles.map(profile => profile.copy(regions = profile.regions.sortBy(region =>
        (region.region.screenId, region.region.regionId)))).sortBy(profile => _window_id(profile.windowClass)),
      controls = input.controls.sortBy(control => (control.control.screenId, control.control.interactionId)),
      fields = input.fields.map(field => field.copy(localRules = field.localRules.sortBy(rule =>
        (rule.constraint.constraintId, rule.constraint.detailCode, rule.ruleKind.id)))).sortBy(_.fieldId),
      textSources = input.textSources.sortBy(_.textId),
      outcomes = input.outcomes.map(outcome => outcome.copy(recoveries = outcome.recoveries.sortBy(recovery =>
        (recovery.kind.id, recovery.step.uiUseCase.layer.id, recovery.step.uiUseCase.id,
          recovery.step.stepId, recovery.step.path.id, recovery.target.screenId, recovery.target.interactionId)))).sortBy(_.outcomeId))
  }

  private def _mapped(step: UiUseCaseStep, ref: InteractionRef, projection: LogicalUiProjection, path: String): Either[LogicalUiError, Unit] =
    _check(step != null && ref != null && projection.input.steps.contains(step) && projection.input.mappings.contains(
      ScreenInteractionMapping(step.uiUseCase, step.stepId, ref.screenId, ref.interactionId)),
      "SOURCE_INVALID", path, "exact declared step and interaction mapping required")

  private def _field_binding(binding: ComponentBinding, role: ComponentRole, interaction: ScreenInteraction, semantics: LogicalUiSemanticProjection): Boolean =
    binding != null && interaction.componentUsages.contains(ComponentUsage(role, binding)) &&
      semantics.input.componentBindings.exists(value => value.role == role && value.binding == binding)

  private def _control_role(kind: InteractionKind): ControlRole = kind match {
    case EntryInteraction => EntryControl
    case NavigationInteraction => NavigationControl
    case InputInteraction => InputControl
    case SelectionInteraction => SelectionControl
    case InvocationInteraction => CommandControl
    case ObservationInteraction | QueryInteraction => ObservationControl
    case FeedbackInteraction => FeedbackControl
  }
  private def _focusable(role: ControlRole): Boolean =
    Set[ControlRole](NavigationControl, InputControl, SelectionControl, CommandControl).contains(role)
  private def _outcome_state(feedback: FeedbackState): UiInteractionState = feedback match {
    case NormalFeedback => SucceededInteractionState
    case LoadingFeedback => PendingInteractionState
    case EmptyFeedback => IdleInteractionState
    case _ => FailedInteractionState
  }
  private def _window_id(intent: AdaptiveIntent): String = intent match {
    case CompactIntent => "compact"
    case MediumIntent => "medium"
    case ExpandedIntent => "expanded"
  }
  private def _id_valid(value: String): Boolean = value != null && value.nonEmpty && value == value.trim
  private def _check(valid: Boolean, code: String, path: String, reason: String): Either[LogicalUiError, Unit] =
    if (valid) Right(()) else Left(LogicalUiError("LUI501_" + code, path, reason))
  private def _vector[A](values: Vector[A], path: String): Either[LogicalUiError, Unit] =
    _check(values != null, "ELEMENT_INVALID", path, "nonnull declaration vector required")
  private def _each[A](values: Vector[A], path: String)(f: (A, String) => Either[LogicalUiError, Unit]): Either[LogicalUiError, Unit] =
    values.zipWithIndex.foldLeft[Either[LogicalUiError, Unit]](Right(())) { case (result, (value, index)) =>
      val itempath = s"$path[$index]"
      result.flatMap(_ => _check(value != null, "ELEMENT_INVALID", itempath, "nonnull declaration required")).flatMap(_ => f(value, itempath))
    }
  private def _unique[A](values: Vector[A], path: String): Either[LogicalUiError, Unit] = {
    val duplicate = values.indices.find(index => values.take(index).contains(values(index)))
    _check(duplicate.isEmpty, "DUPLICATE_INVALID", duplicate.map(index => s"$path[$index]").getOrElse(path), "declaration identity must be unique")
  }
  private def _text_ref(id: String, input: ReadinessInput, path: String): Either[LogicalUiError, Unit] =
    _check(_id_valid(id) && input.textSources.exists(_.textId == id), "TEXT_REFERENCE_INVALID", path, "declared text source required")

  private def _object(fields: Vector[(String, JsValue)]): JsObject = JsObject(fields)
  private def _array[A](values: Vector[A])(f: A => JsValue): JsArray = JsArray(values.map(f))
  private def _role_identity(kind: String, content: String): String = CozyLogicalUiProjection.identity(Json.stringify(_object(Vector(
    "schema" -> JsString(schema), "version" -> JsNumber(version), "kind" -> JsString(kind), "logicalContent" -> JsString(content)))))
  private def _ref_json(value: InteractionRef): JsValue = _object(Vector(
    "screenId" -> JsString(value.screenId), "interactionId" -> JsString(value.interactionId)))
  private def _binding_json(value: ComponentBinding): JsValue = _object(Vector(
    "component" -> _object(Vector("namespace" -> JsString(value.component.namespace),
      "id" -> JsString(value.component.id), "version" -> JsString(value.component.version))),
    "exportId" -> JsString(value.exportId)))
  private def _step_json(value: UiUseCaseStep): JsValue = _object(Vector(
    "uiUseCase" -> _object(Vector("layer" -> JsString(value.uiUseCase.layer.id), "id" -> JsString(value.uiUseCase.id))),
    "stepId" -> JsString(value.stepId), "path" -> JsString(value.path.id)))
  private def _input_json(input: ReadinessInput): JsValue = _object(Vector(
    "semanticProjectionIdentity" -> JsString(input.semanticProjectionIdentity),
    "profiles" -> _array(input.profiles)(profile => _object(Vector(
      "windowClass" -> JsString(_window_id(profile.windowClass)),
      "detailPlacement" -> JsString(if (profile.detailPlacement == NavigateToDetail) "navigate-to-detail" else "update-detail-region"),
      "persistentNavigation" -> JsBoolean(profile.persistentNavigation),
      "regions" -> _array(profile.regions)(region => _object(Vector(
        "region" -> _object(Vector("screenId" -> JsString(region.region.screenId), "regionId" -> JsString(region.region.regionId))),
        "role" -> JsString(region.role.id), "persistent" -> JsBoolean(region.persistent))))))),
    "controls" -> _array(input.controls)(control => _object(Vector(
      "control" -> _ref_json(control.control), "role" -> JsString(control.role.id),
      "labelTextId" -> JsString(control.labelTextId), "focusOrder" -> control.focusOrder.map(rank => JsNumber(rank)).getOrElse(JsNull)))),
    "fields" -> _array(input.fields)(field => _object(Vector(
      "fieldId" -> JsString(field.fieldId), "control" -> _ref_json(field.control),
      "valueBinding" -> _binding_json(field.valueBinding), "datatypeBinding" -> _binding_json(field.datatypeBinding),
      "labelTextId" -> JsString(field.labelTextId), "inputIntent" -> JsString(field.inputIntent.id),
      "required" -> JsBoolean(field.required), "initialValue" -> JsString(field.initialValue),
      "localRules" -> _array(field.localRules)(rule => _object(Vector(
        "constraint" -> _object(Vector("constraintId" -> JsString(rule.constraint.constraintId), "detailCode" -> JsString(rule.constraint.detailCode))),
        "ruleKind" -> JsString(rule.ruleKind.id), "feedbackId" -> JsString(rule.feedbackId), "messageTextId" -> JsString(rule.messageTextId))))))),
    "textSources" -> _array(input.textSources)(text => _object(Vector(
      "textId" -> JsString(text.textId), "sourceIdentity" -> JsString(text.sourceIdentity), "defaultText" -> JsString(text.defaultText)))),
    "outcomes" -> _array(input.outcomes)(outcome => _object(Vector(
      "outcomeId" -> JsString(outcome.outcomeId), "step" -> _step_json(outcome.step),
      "trigger" -> _ref_json(outcome.trigger), "feedbackControl" -> _ref_json(outcome.feedbackControl),
      "feedbackState" -> JsString(outcome.feedbackState.id), "uiState" -> JsString(outcome.uiState.id),
      "domainState" -> _object(Vector("stateMachineBinding" -> _binding_json(outcome.domainState.stateMachineBinding), "stateId" -> JsString(outcome.domainState.stateId))),
      "workflowState" -> outcome.workflowState.map(workflow => _object(Vector(
        "workflowId" -> JsString(workflow.workflowId), "stateId" -> JsString(workflow.stateId)))).getOrElse(JsNull),
      "recoveries" -> _array(outcome.recoveries)(recovery => _object(Vector(
        "kind" -> JsString(recovery.kind.id), "step" -> _step_json(recovery.step), "target" -> _ref_json(recovery.target)))),
      "messageTextId" -> JsString(outcome.messageTextId))))
  ))
}
