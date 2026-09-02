package cozy.ui


/*
 * @since   Sep. 1, 2026
 * @version Sep. 2, 2026
 * @author  ASAMI, Tomoharu
 */
private[cozy] object CozyLogicalUi {
  sealed trait UseCaseLayer {
    def id: String
  }

  case object Business extends UseCaseLayer {
    val id = "business"
  }

  case object System extends UseCaseLayer {
    val id = "system"
  }

  case object Ui extends UseCaseLayer {
    val id = "ui"
  }

  final case class LogicalUiError(code: String, path: String, reason: String)

  final case class ComponentCoordinate(namespace: String, id: String, version: String) {
    def qualifiedName: String = s"$namespace.$id"
    def canonicalIdentity: String = s"$qualifiedName@$version"
  }

  final case class ComponentSurface(component: ComponentCoordinate, exportIds: Vector[String])
  final case class ComponentBinding(component: ComponentCoordinate, exportId: String)
  final case class UseCaseReference(layer: UseCaseLayer, id: String)
  final case class UseCaseRealization(source: UseCaseReference, target: UseCaseReference)
  final case class UseCaseLayers(
    business: UseCaseReference,
    system: UseCaseReference,
    ui: UseCaseReference,
    realizations: Vector[UseCaseRealization]
  )
  final case class CandidateInput(
    componentSurfaces: Vector[ComponentSurface],
    componentBindings: Vector[ComponentBinding],
    useCases: UseCaseLayers
  )

  sealed trait UiUseCasePath {
    def id: String
  }

  case object NormalPath extends UiUseCasePath {
    val id = "normal"
  }

  case object AlternativePath extends UiUseCasePath {
    val id = "alternative"
  }

  case object ExceptionPath extends UiUseCasePath {
    val id = "exception"
  }

  case object SystemOnlyPath extends UiUseCasePath {
    val id = "system-only"
  }

  sealed trait InteractionKind {
    def id: String
  }

  case object EntryInteraction extends InteractionKind {
    val id = "entry"
  }

  case object InputInteraction extends InteractionKind {
    val id = "input"
  }

  case object QueryInteraction extends InteractionKind {
    val id = "query"
  }

  case object SelectionInteraction extends InteractionKind {
    val id = "selection"
  }

  case object InvocationInteraction extends InteractionKind {
    val id = "invocation"
  }

  case object ObservationInteraction extends InteractionKind {
    val id = "observation"
  }

  case object FeedbackInteraction extends InteractionKind {
    val id = "feedback"
  }

  case object NavigationInteraction extends InteractionKind {
    val id = "navigation"
  }

  sealed trait ComponentRole {
    def id: String
  }

  case object EntityRole extends ComponentRole {
    val id = "Entity"
  }

  case object AggregateRole extends ComponentRole {
    val id = "Aggregate"
  }

  case object ServiceRole extends ComponentRole {
    val id = "Service"
  }

  case object OperationRole extends ComponentRole {
    val id = "Operation"
  }

  case object ValueRole extends ComponentRole {
    val id = "Value"
  }

  case object DatatypeRole extends ComponentRole {
    val id = "Datatype"
  }

  case object ViewRole extends ComponentRole {
    val id = "View"
  }

  case object WorkflowRole extends ComponentRole {
    val id = "Workflow"
  }

  case object PowertypeRole extends ComponentRole {
    val id = "Powertype"
  }

  case object StateMachineRole extends ComponentRole {
    val id = "StateMachine"
  }

  sealed trait FeedbackState {
    def id: String
  }

  case object NormalFeedback extends FeedbackState {
    val id = "normal"
  }

  case object LoadingFeedback extends FeedbackState {
    val id = "loading"
  }

  case object EmptyFeedback extends FeedbackState {
    val id = "empty"
  }

  case object UnavailableFeedback extends FeedbackState {
    val id = "unavailable"
  }

  case object ValidationFailedFeedback extends FeedbackState {
    val id = "validation-failed"
  }

  case object ConflictFeedback extends FeedbackState {
    val id = "conflict"
  }

  case object OperationFailedFeedback extends FeedbackState {
    val id = "operation-failed"
  }

  final case class UiUseCaseStep(
    uiUseCase: UseCaseReference,
    stepId: String,
    path: UiUseCasePath
  )

  final case class ScreenInteractionMapping(
    uiUseCase: UseCaseReference,
    stepId: String,
    screenId: String,
    interactionId: String
  )

  final case class ScreenSubject(role: ComponentRole, binding: ComponentBinding)

  final case class ComponentUsage(role: ComponentRole, binding: ComponentBinding)

  final case class SemanticRegion(
    id: String,
    parentId: Option[String],
    order: Int
  )

  final case class NavigationEndpoint(endpointId: String, targetScreenId: String)

  final case class MutationAction(target: ComponentBinding, operation: ComponentBinding)

  final case class ScreenInteraction(
    id: String,
    kind: InteractionKind,
    componentUsages: Vector[ComponentUsage],
    mutation: Option[MutationAction],
    navigation: Option[NavigationEndpoint]
  )

  final case class LogicalScreen(
    id: String,
    primaryPurpose: String,
    secondaryPurposes: Vector[String],
    subject: ScreenSubject,
    regions: Vector[SemanticRegion],
    interactions: Vector[ScreenInteraction],
    feedbackStates: Vector[FeedbackState]
  )

  final case class AggregateBoundary(
    aggregate: ComponentBinding,
    root: ComponentBinding,
    members: Vector[ComponentBinding],
    publicOperations: Vector[ComponentBinding]
  )

  final case class UseCaseScreenProjection(
    candidateIdentity: String,
    catalog: Vector[UseCaseLayers],
    steps: Vector[UiUseCaseStep],
    screens: Vector[LogicalScreen],
    mappings: Vector[ScreenInteractionMapping],
    aggregateBoundaries: Vector[AggregateBoundary]
  )

  final case class AcceptanceDecision(decisionId: String, candidateIdentity: String) {
    def decisionIdentity: String = CozyLogicalUiProjection.decisionIdentity(this)
  }

  final class LogicalUiCandidate private[CozyLogicalUi] (
    val identity: String,
    val inputIdentity: String,
    val input: CandidateInput
  ) {
    def canonicalContent: String = CozyLogicalUiProjection.canonicalLogicalContent(input)
    def canonicalJson: String = CozyLogicalUiProjection.canonicalCandidateDocument(this)
  }

  final class AcceptedLogicalUi private[CozyLogicalUi] (
    val identity: String,
    val candidate: LogicalUiCandidate,
    val decision: AcceptanceDecision
  )

  final class LogicalUiProjection private[CozyLogicalUi] (
    val identity: String,
    val candidate: LogicalUiCandidate,
    val input: UseCaseScreenProjection
  ) {
    def canonicalContent: String = CozyLogicalUiProjection.canonicalProjectionContent(candidate.identity, input)
  }

  private val _schema = "cozy.logical-ui.v1"
  private val _version = 1
  private val _candidate_kind = "candidate"
  private val _consumed_input_kind = "consumed-input"
  private val _acceptance_decision_kind = "acceptance-decision"
  private val _accepted_kind = "accepted"
  private val _projection_schema = "cozy.usecase-screen-projection.v1"
  private val _projection_version = 1
  private val _component_segment_pattern = "[A-Za-z][A-Za-z0-9_-]*".r

  def schema: String = _schema
  def version: Int = _version

  def candidate(input: CandidateInput): Either[LogicalUiError, LogicalUiCandidate] =
    _normalize_input(input) match {
      case Left(error) => Left(error)
      case Right(normalized) =>
        val content = CozyLogicalUiProjection.canonicalLogicalContent(normalized)
        Right(new LogicalUiCandidate(CozyLogicalUiProjection.identity(content), CozyLogicalUiProjection.inputIdentity(content), normalized))
    }

  def project(
    candidate: LogicalUiCandidate,
    input: UseCaseScreenProjection
  ): Either[LogicalUiError, LogicalUiProjection] = {
    _normalize_projection(candidate, input) match {
      case Left(error) => Left(error)
      case Right(normalized) =>
        val content = CozyLogicalUiProjection.canonicalProjectionContent(candidate.identity, normalized)
        Right(new LogicalUiProjection(CozyLogicalUiProjection.identity(content), candidate, normalized))
    }
  }

  def accept(
    candidate: LogicalUiCandidate,
    decision: AcceptanceDecision
  ): Either[LogicalUiError, AcceptedLogicalUi] = {
    if (candidate == null)
      Left(LogicalUiError("LUI43_ACCEPTANCE_CANDIDATE_MISSING", "candidate", "candidate is required"))
    else
      _acceptance_error(candidate, decision) match {
        case Some(error) => Left(error)
        case None =>
          val identity = CozyLogicalUiProjection.acceptedIdentity(candidate, decision)
          if (identity == candidate.identity)
            Left(LogicalUiError("LUI43_ACCEPTANCE_IDENTITY_INVALID", "acceptance", "accepted identity must differ from candidate identity"))
          else
            Right(new AcceptedLogicalUi(identity, candidate, decision))
      }
  }

  private def _normalize_projection(
    candidate: LogicalUiCandidate,
    input: UseCaseScreenProjection
  ): Either[LogicalUiError, UseCaseScreenProjection] = {
    if (candidate == null)
      Left(LogicalUiError("LUI43_PROJECTION_CANDIDATE_MISMATCH", "candidate", "a LogicalUiCandidate is required"))
    else if (input == null)
      Left(LogicalUiError("LUI43_PROJECTION_INPUT_MISSING", "projection", "a typed UseCase-to-Screen projection is required"))
    else if (_required_text_error(input.candidateIdentity, "projection.candidateIdentity").isDefined)
      Left(LogicalUiError("LUI43_PROJECTION_CANDIDATE_MISMATCH", "projection.candidateIdentity", "candidate identity must be nonempty and trimmed"))
    else if (input.candidateIdentity != candidate.identity)
      Left(LogicalUiError("LUI43_PROJECTION_CANDIDATE_MISMATCH", "projection.candidateIdentity", "projection must bind the exact LogicalUiCandidate identity"))
    else
      _normalize_projection_catalog(input.catalog, candidate.input.useCases) match {
        case Left(error) => Left(error)
        case Right(catalog) =>
          _normalize_projection_steps(input.steps, catalog) match {
            case Left(error) => Left(error)
            case Right(steps) =>
              _normalize_projection_screens(input.screens) match {
                case Left(error) => Left(error)
                case Right(screens) =>
                  _projection_navigation_error(screens) match {
                    case Some(error) => Left(error)
                    case None =>
                      _normalize_projection_mappings(input.mappings, steps, screens) match {
                        case Left(error) => Left(error)
                        case Right(mappings) =>
                          _normalize_projection_boundaries(input.aggregateBoundaries) match {
                            case Left(error) => Left(error)
                            case Right(boundaries) =>
                              val normalized = input.copy(
                                catalog = catalog,
                                steps = steps,
                                screens = screens,
                                mappings = mappings,
                                aggregateBoundaries = boundaries
                              )
                              CozyLogicalUiProjection.projectionComponentError(candidate, normalized) match {
                                case Some(error) => Left(error)
                                case None =>
                                  _projection_aggregate_error(normalized) match {
                                    case Some(error) => Left(error)
                                    case None => Right(normalized)
                                  }
                              }
                          }
                      }
                  }
              }
          }
      }
  }

  private def _normalize_projection_catalog(
    values: Vector[UseCaseLayers],
    candidateusecases: UseCaseLayers
  ): Either[LogicalUiError, Vector[UseCaseLayers]] = {
    val catalog = Option(values).getOrElse(Vector.empty)
    if (catalog.isEmpty)
      Left(LogicalUiError("LUI43_PROJECTION_CATALOG_CLOSED", "projection.catalog", "at least one opaque three-layer UseCase identity is required"))
    else {
      val normalized = catalog.zipWithIndex.map { case (value, index) =>
        _normalize_use_cases(value) match {
          case Left(error) => Left(error.copy(code = "LUI43_PROJECTION_CATALOG_CLOSED", path = s"projection.catalog[$index].${error.path}", reason = "catalog UseCaseLayers must be a valid opaque three-layer identity"))
          case Right(item) => Right(item)
        }
      }
      normalized.collectFirst { case Left(error) => error } match {
        case Some(error) => Left(error)
        case None =>
          val items = normalized.collect { case Right(value) => value }
          val identities = items.map(_use_case_layers_key)
          if (identities.distinct.size != identities.size)
            Left(LogicalUiError("LUI43_PROJECTION_CATALOG_CLOSED", "projection.catalog", "catalog UseCaseLayers identities must be unique"))
          else if (!items.contains(candidateusecases))
            Left(LogicalUiError("LUI43_PROJECTION_CATALOG_CLOSED", "projection.catalog", "catalog must contain the candidate's exact UseCaseLayers identity"))
          else
            Right(items.sortBy(_use_case_layers_key))
      }
    }
  }

  private def _normalize_projection_steps(
    values: Vector[UiUseCaseStep],
    catalog: Vector[UseCaseLayers]
  ): Either[LogicalUiError, Vector[UiUseCaseStep]] = {
    val steps = Option(values).getOrElse(Vector.empty)
    if (steps.isEmpty)
      Left(LogicalUiError("LUI43_PROJECTION_STEP_COVERAGE", "projection.steps", "every catalog UI UseCase must declare at least one step"))
    else {
      val uiidentities = catalog.map(_.ui).toSet
      val errors = steps.zipWithIndex.flatMap { case (step, index) =>
        _projection_step_error(step, uiidentities, s"projection.steps[$index]")
      }
      errors.headOption match {
        case Some(error) => Left(error)
        case None =>
          val keys = steps.map(_step_key)
          if (keys.distinct.size != keys.size)
            Left(LogicalUiError("LUI43_PROJECTION_STEP_COVERAGE", "projection.steps", "step identity must be unique within a catalog UI UseCase"))
          else {
            val declared = steps.map(_.uiUseCase).toSet
            catalog.map(_.ui).find(reference => !declared.contains(reference)) match {
              case Some(reference) => Left(LogicalUiError("LUI43_PROJECTION_STEP_COVERAGE", "projection.steps", s"catalog UI UseCase ${reference.id} must declare at least one step"))
              case None => Right(steps.sortBy(_step_key))
            }
          }
      }
    }
  }

  private def _projection_step_error(
    value: UiUseCaseStep,
    uiidentities: Set[UseCaseReference],
    path: String
  ): Option[LogicalUiError] = {
    if (value == null)
      Some(LogicalUiError("LUI43_PROJECTION_STEP_COVERAGE", path, "UI UseCase step is required"))
    else if (value.uiUseCase == null || value.uiUseCase.layer != Ui || !uiidentities.contains(value.uiUseCase))
      Some(LogicalUiError("LUI43_PROJECTION_CATALOG_CLOSED", s"$path.uiUseCase", "step must reference an exact catalog UI UseCase identity"))
    else if (_required_text_error(value.stepId, s"$path.stepId").isDefined)
      Some(LogicalUiError("LUI43_PROJECTION_STEP_COVERAGE", s"$path.stepId", "step ID must be nonempty and trimmed"))
    else if (value.path == null)
      Some(LogicalUiError("LUI43_PROJECTION_PATH_INVALID", s"$path.path", "step path must be normal, alternative, exception, or system-only"))
    else
      None
  }

  private def _normalize_projection_screens(
    values: Vector[LogicalScreen]
  ): Either[LogicalUiError, Vector[LogicalScreen]] = {
    val screens = Option(values).getOrElse(Vector.empty)
    if (screens.isEmpty)
      Left(LogicalUiError("LUI43_PROJECTION_SCREEN_UNJUSTIFIED", "projection.screens", "at least one mapped Logical Screen is required"))
    else {
      val errors = screens.zipWithIndex.flatMap { case (screen, index) =>
        _projection_screen_error(screen, s"projection.screens[$index]")
      }
      errors.headOption match {
        case Some(error) => Left(error)
        case None =>
          val ids = screens.map(_.id)
          if (ids.distinct.size != ids.size)
            Left(LogicalUiError("LUI43_PROJECTION_SCREEN_UNJUSTIFIED", "projection.screens", "screen identity must be globally unique"))
          else
            Right(screens.map(_normalize_screen).sortBy(_.id))
      }
    }
  }

  private def _projection_screen_error(value: LogicalScreen, path: String): Option[LogicalUiError] = {
    if (value == null)
      Some(LogicalUiError("LUI43_PROJECTION_SCREEN_INVALID", path, "Logical Screen is required"))
    else if (_required_text_error(value.id, s"$path.id").isDefined)
      Some(LogicalUiError("LUI43_PROJECTION_SCREEN_INVALID", s"$path.id", "screen ID must be nonempty and trimmed"))
    else if (_required_text_error(value.primaryPurpose, s"$path.primaryPurpose").isDefined)
      Some(LogicalUiError("LUI43_PROJECTION_SCREEN_INVALID", s"$path.primaryPurpose", "primary purpose must be nonempty and trimmed"))
    else {
      val purposes = Option(value.secondaryPurposes).getOrElse(Vector.empty)
      if (purposes.exists(item => _required_text_error(item, s"$path.secondaryPurposes").isDefined))
        Some(LogicalUiError("LUI43_PROJECTION_SCREEN_INVALID", s"$path.secondaryPurposes", "secondary purposes must be nonempty and trimmed"))
      else if (purposes.distinct.size != purposes.size)
        Some(LogicalUiError("LUI43_PROJECTION_SCREEN_INVALID", s"$path.secondaryPurposes", "secondary purposes must be unique"))
      else if (value.subject == null || value.subject.role == null)
        Some(LogicalUiError("LUI43_PROJECTION_COMPONENT_ROLE_INVALID", s"$path.subject", "screen subject must be Entity, Aggregate, View, or Workflow"))
      else if (!Set[ComponentRole](EntityRole, AggregateRole, ViewRole, WorkflowRole).contains(value.subject.role))
        Some(LogicalUiError("LUI43_PROJECTION_COMPONENT_ROLE_INVALID", s"$path.subject.role", "screen subject must be Entity, Aggregate, View, or Workflow"))
      else if (value.subject.binding == null)
        Some(LogicalUiError("LUI43_PROJECTION_COMPONENT_CLOSED", s"$path.subject.binding", "screen subject must use an exact public Component binding"))
      else
        _projection_region_error(value.regions, s"$path.regions").orElse {
          val interactions = Option(value.interactions).getOrElse(Vector.empty)
          if (interactions.isEmpty)
            Some(LogicalUiError("LUI43_PROJECTION_INTERACTION_UNJUSTIFIED", s"$path.interactions", "a Logical Screen must declare at least one interaction"))
          else {
            val errors = interactions.zipWithIndex.flatMap { case (interaction, index) =>
              _projection_interaction_error(interaction, s"$path.interactions[$index]")
            }
            errors.headOption.orElse {
              val ids = interactions.map(_.id)
              if (ids.distinct.size != ids.size)
                Some(LogicalUiError("LUI43_PROJECTION_INTERACTION_UNJUSTIFIED", s"$path.interactions", "interaction identity must be unique within a screen"))
              else
                _projection_feedback_error(value.feedbackStates, s"$path.feedbackStates")
            }
          }
        }
    }
  }

  private def _projection_region_error(values: Vector[SemanticRegion], path: String): Option[LogicalUiError] = {
    val regions = Option(values).getOrElse(Vector.empty)
    if (regions.isEmpty)
      Some(LogicalUiError("LUI43_PROJECTION_REGION_INVALID", path, "a screen must contain a nonempty semantic region tree"))
    else if (regions.exists(_ == null))
      Some(LogicalUiError("LUI43_PROJECTION_REGION_INVALID", path, "semantic region is required"))
    else {
      val ids = regions.map(_.id)
      if (ids.exists(value => _required_text_error(value, s"$path.id").isDefined))
        Some(LogicalUiError("LUI43_PROJECTION_REGION_INVALID", path, "region ID must be nonempty and trimmed"))
      else if (ids.distinct.size != ids.size)
        Some(LogicalUiError("LUI43_PROJECTION_REGION_INVALID", path, "region ID must be unique within a screen"))
      else if (regions.exists(_.parentId == null))
        Some(LogicalUiError("LUI43_PROJECTION_REGION_INVALID", path, "region parent must be explicitly empty or reference a parent"))
      else if (regions.exists(region => region.parentId.exists(parent => _required_text_error(parent, path).isDefined)))
        Some(LogicalUiError("LUI43_PROJECTION_REGION_INVALID", path, "region parent ID must be nonempty and trimmed"))
      else if (regions.exists(_.order < 0))
        Some(LogicalUiError("LUI43_PROJECTION_REGION_INVALID", path, "region sibling order must be nonnegative"))
      else if (regions.count(_.parentId.isEmpty) != 1)
        Some(LogicalUiError("LUI43_PROJECTION_REGION_INVALID", path, "semantic region tree must contain exactly one root"))
      else if (regions.exists(region => region.parentId.exists(parent => !ids.contains(parent))))
        Some(LogicalUiError("LUI43_PROJECTION_REGION_INVALID", path, "every region parent must exist"))
      else if (regions.groupBy(_.parentId).exists { case (_, siblings) => siblings.map(_.order).distinct.size != siblings.size })
        Some(LogicalUiError("LUI43_PROJECTION_REGION_INVALID", path, "sibling region order must be unique"))
      else {
        val root = regions.find(_.parentId.isEmpty).get.id
        var visited = Set.empty[String]
        var frontier = Vector(root)
        while (frontier.nonEmpty) {
          val current = frontier.head
          frontier = frontier.tail
          if (!visited.contains(current)) {
            visited += current
            frontier = frontier ++ regions.filter(_.parentId.contains(current)).map(_.id)
          }
        }
        if (visited.size != ids.size)
          Some(LogicalUiError("LUI43_PROJECTION_REGION_INVALID", path, "semantic regions must form one rooted tree without cycles"))
        else
          None
      }
    }
  }

  private def _projection_interaction_error(value: ScreenInteraction, path: String): Option[LogicalUiError] = {
    if (value == null)
      Some(LogicalUiError("LUI43_PROJECTION_INTERACTION_UNJUSTIFIED", path, "screen interaction is required"))
    else if (_required_text_error(value.id, s"$path.id").isDefined)
      Some(LogicalUiError("LUI43_PROJECTION_INTERACTION_UNJUSTIFIED", s"$path.id", "interaction ID must be nonempty and trimmed"))
    else if (value.kind == null)
      Some(LogicalUiError("LUI43_PROJECTION_INTERACTION_KIND_INVALID", s"$path.kind", "interaction kind must be one of the closed v1 kinds"))
    else if (value.componentUsages == null)
      Some(LogicalUiError("LUI43_PROJECTION_COMPONENT_CLOSED", s"$path.componentUsages", "component usages must be explicit"))
    else if (value.componentUsages.exists(usage => usage == null || usage.role == null))
      Some(LogicalUiError("LUI43_PROJECTION_COMPONENT_ROLE_INVALID", s"$path.componentUsages", "every component usage must have a closed role"))
    else if (value.componentUsages.exists(_.binding == null))
      Some(LogicalUiError("LUI43_PROJECTION_COMPONENT_CLOSED", s"$path.componentUsages", "every component usage must use an exact public Component binding"))
    else if (value.componentUsages.map(usage => (usage.role.id, usage.binding)).distinct.size != value.componentUsages.size)
      Some(LogicalUiError("LUI43_PROJECTION_COMPONENT_CLOSED", s"$path.componentUsages", "component usage must not be duplicated"))
    else if (value.mutation == null || value.navigation == null)
      Some(LogicalUiError("LUI43_PROJECTION_INTERACTION_KIND_INVALID", path, "optional mutation and navigation values must be explicit Options"))
    else if (value.mutation.exists(action => action == null || action.target == null || action.operation == null))
      Some(LogicalUiError("LUI43_PROJECTION_OPERATION_MISSING", s"$path.mutation", "a mutation action must name both target and public Operation"))
    else if (value.navigation.exists(endpoint => endpoint == null || _required_text_error(endpoint.endpointId, s"$path.navigation.endpointId").isDefined || _required_text_error(endpoint.targetScreenId, s"$path.navigation.targetScreenId").isDefined))
      Some(LogicalUiError("LUI43_PROJECTION_NAVIGATION_INVALID", s"$path.navigation", "navigation endpoint must have nonempty endpoint and target screen IDs"))
    else if (value.kind == NavigationInteraction && value.navigation.isEmpty)
      Some(LogicalUiError("LUI43_PROJECTION_NAVIGATION_INVALID", s"$path.navigation", "navigation interaction must declare an endpoint"))
    else if (value.kind != NavigationInteraction && value.navigation.nonEmpty)
      Some(LogicalUiError("LUI43_PROJECTION_NAVIGATION_INVALID", s"$path.navigation", "only a navigation interaction may declare an endpoint"))
    else if (value.mutation.nonEmpty && value.kind != InvocationInteraction)
      Some(LogicalUiError("LUI43_PROJECTION_OPERATION_MISSING", s"$path.mutation", "a mutation action must belong to an invocation interaction"))
    else
      None
  }

  private def _projection_feedback_error(values: Vector[FeedbackState], path: String): Option[LogicalUiError] = {
    val states = Option(values).getOrElse(Vector.empty)
    if (states.isEmpty)
      Some(LogicalUiError("LUI43_PROJECTION_FEEDBACK_INVALID", path, "every screen must include a normal feedback state"))
    else if (states.exists(_ == null))
      Some(LogicalUiError("LUI43_PROJECTION_FEEDBACK_INVALID", path, "feedback state must be one of the closed v1 states"))
    else if (states.distinct.size != states.size)
      Some(LogicalUiError("LUI43_PROJECTION_FEEDBACK_INVALID", path, "feedback state must not be duplicated"))
    else if (!states.contains(NormalFeedback))
      Some(LogicalUiError("LUI43_PROJECTION_FEEDBACK_INVALID", path, "every screen must include normal feedback"))
    else
      None
  }

  private def _projection_navigation_error(screens: Vector[LogicalScreen]): Option[LogicalUiError] = {
    val screenids = screens.map(_.id).toSet
    screens.iterator.flatMap { screen =>
      val endpoints = screen.interactions.flatMap(_.navigation.toVector)
      if (endpoints.map(_.endpointId).distinct.size != endpoints.size)
        Iterator.single(LogicalUiError("LUI43_PROJECTION_NAVIGATION_INVALID", s"projection.screens.${screen.id}.interactions", "navigation endpoint identity must be unique within a screen"))
      else
        endpoints.iterator.filter(endpoint => !screenids.contains(endpoint.targetScreenId)).map { endpoint =>
          LogicalUiError("LUI43_PROJECTION_NAVIGATION_INVALID", s"projection.screens.${screen.id}.navigation.${endpoint.endpointId}", "navigation target screen must exist in the projection")
        }
    }.toStream.headOption
  }

  private def _normalize_screen(value: LogicalScreen): LogicalScreen =
    value.copy(
      secondaryPurposes = Option(value.secondaryPurposes).getOrElse(Vector.empty).sorted,
      regions = value.regions.sortBy(_.id),
      interactions = value.interactions.map { interaction =>
        interaction.copy(
          componentUsages = interaction.componentUsages.sortBy(usage => (usage.role.id, _binding_key(usage.binding)))
        )
      }.sortBy(_.id),
      feedbackStates = value.feedbackStates.sortBy(_.id)
    )

  private def _normalize_projection_mappings(
    values: Vector[ScreenInteractionMapping],
    steps: Vector[UiUseCaseStep],
    screens: Vector[LogicalScreen]
  ): Either[LogicalUiError, Vector[ScreenInteractionMapping]] = {
    val mappings = Option(values).getOrElse(Vector.empty)
    val stepbykey = steps.map(step => _step_key(step) -> step).toMap
    val screenbyid = screens.map(screen => screen.id -> screen).toMap
    val errors = mappings.zipWithIndex.flatMap { case (mapping, index) =>
      _projection_mapping_error(mapping, stepbykey, screenbyid, s"projection.mappings[$index]")
    }
    errors.headOption match {
      case Some(error) => Left(error)
      case None =>
        val keys = mappings.map(_mapping_key)
        if (keys.distinct.size != keys.size)
          Left(LogicalUiError("LUI43_PROJECTION_STEP_COVERAGE", "projection.mappings", "duplicate step-to-screen interaction mapping is not admitted"))
        else {
          val mappedsteps = mappings.map(mapping => (mapping.uiUseCase, mapping.stepId)).toSet
          steps.find(step => step.path == SystemOnlyPath && mappedsteps.contains((step.uiUseCase, step.stepId))) match {
            case Some(step) => Left(LogicalUiError("LUI43_PROJECTION_SYSTEM_STEP_MAPPING", "projection.mappings", s"system-only step ${step.stepId} must not map to a screen interaction"))
            case None =>
              steps.find(step => step.path != SystemOnlyPath && !mappedsteps.contains((step.uiUseCase, step.stepId))) match {
                case Some(step) => Left(LogicalUiError("LUI43_PROJECTION_STEP_COVERAGE", "projection.mappings", s"non-system step ${step.stepId} must map to at least one interaction"))
                case None =>
                  val screenkeys = mappings.map(mapping => (mapping.screenId, mapping.interactionId)).toSet
                  val missingscreen = screens.find(screen => !mappings.exists(_.screenId == screen.id))
                  missingscreen match {
                    case Some(screen) => Left(LogicalUiError("LUI43_PROJECTION_SCREEN_UNJUSTIFIED", s"projection.screens.${screen.id}", "every screen must be justified by at least one step mapping"))
                    case None =>
                      screens.iterator.flatMap(screen => screen.interactions.map(interaction => (screen.id, interaction.id))).find(key => !screenkeys.contains(key)) match {
                        case Some(_) =>
                          val missing = screens.iterator.flatMap(screen => screen.interactions.map(interaction => (screen.id, interaction.id))).find(key => !screenkeys.contains(key)).get
                          Left(LogicalUiError("LUI43_PROJECTION_INTERACTION_UNJUSTIFIED", s"projection.screens.${missing._1}.interactions.${missing._2}", "every interaction must be justified by at least one step mapping"))
                        case None => Right(mappings.sortBy(_mapping_key))
                      }
                  }
              }
          }
        }
    }
  }

  private def _projection_mapping_error(
    value: ScreenInteractionMapping,
    stepbykey: Map[(String, String, String), UiUseCaseStep],
    screenbyid: Map[String, LogicalScreen],
    path: String
  ): Option[LogicalUiError] = {
    if (value == null)
      Some(LogicalUiError("LUI43_PROJECTION_STEP_COVERAGE", path, "screen interaction mapping is required"))
    else if (value.uiUseCase == null || value.uiUseCase.layer != Ui || _required_text_error(value.uiUseCase.id, s"$path.uiUseCase.id").isDefined || _required_text_error(value.stepId, s"$path.stepId").isDefined)
      Some(LogicalUiError("LUI43_PROJECTION_STEP_COVERAGE", path, "mapping must name a nonempty UI UseCase step"))
    else if (!stepbykey.contains(_step_reference_key(value.uiUseCase, value.stepId)))
      Some(LogicalUiError("LUI43_PROJECTION_CATALOG_CLOSED", s"$path.step", "mapping must reference an exact declared step"))
    else if (_required_text_error(value.screenId, s"$path.screenId").isDefined || !screenbyid.contains(value.screenId))
      Some(LogicalUiError("LUI43_PROJECTION_SCREEN_UNJUSTIFIED", s"$path.screenId", "mapping must reference an exact declared screen"))
    else if (_required_text_error(value.interactionId, s"$path.interactionId").isDefined || !screenbyid(value.screenId).interactions.exists(_.id == value.interactionId))
      Some(LogicalUiError("LUI43_PROJECTION_INTERACTION_UNJUSTIFIED", s"$path.interactionId", "mapping must reference an exact declared screen interaction"))
    else
      None
  }

  private def _normalize_projection_boundaries(
    values: Vector[AggregateBoundary]
  ): Either[LogicalUiError, Vector[AggregateBoundary]] = {
    val boundaries = Option(values).getOrElse(Vector.empty)
    val errors = boundaries.zipWithIndex.flatMap { case (boundary, index) =>
      _projection_boundary_error(boundary, s"projection.aggregateBoundaries[$index]")
    }
    errors.headOption match {
      case Some(error) => Left(error)
      case None =>
        val identities = boundaries.map(boundary => _binding_key(boundary.aggregate))
        if (identities.distinct.size != identities.size)
          Left(LogicalUiError("LUI43_PROJECTION_COMPONENT_CLOSED", "projection.aggregateBoundaries", "Aggregate boundary identity must be unique"))
        else
          Right(boundaries.map(_normalize_boundary).sortBy(boundary => _binding_key(boundary.aggregate)))
    }
  }

  private def _projection_boundary_error(value: AggregateBoundary, path: String): Option[LogicalUiError] = {
    if (value == null)
      Some(LogicalUiError("LUI43_PROJECTION_COMPONENT_CLOSED", path, "Aggregate boundary is required"))
    else if (value.aggregate == null || value.root == null)
      Some(LogicalUiError("LUI43_PROJECTION_COMPONENT_CLOSED", path, "Aggregate boundary must name an Aggregate and root binding"))
    else if (value.members == null || value.members.isEmpty)
      Some(LogicalUiError("LUI43_PROJECTION_COMPONENT_CLOSED", s"$path.members", "Aggregate boundary must declare members"))
    else if (value.publicOperations == null || value.publicOperations.isEmpty)
      Some(LogicalUiError("LUI43_PROJECTION_OPERATION_MISSING", s"$path.publicOperations", "Aggregate boundary must declare public Operation bindings"))
    else if (!value.members.contains(value.root))
      Some(LogicalUiError("LUI43_PROJECTION_COMPONENT_CLOSED", s"$path.members", "Aggregate boundary members must contain the root binding"))
    else if (value.members.exists(_ == null) || value.publicOperations.exists(_ == null))
      Some(LogicalUiError("LUI43_PROJECTION_COMPONENT_CLOSED", path, "Aggregate boundary references must be explicit bindings"))
    else if (value.members.map(_binding_key).distinct.size != value.members.size || value.publicOperations.map(_binding_key).distinct.size != value.publicOperations.size)
      Some(LogicalUiError("LUI43_PROJECTION_COMPONENT_CLOSED", path, "Aggregate members and public Operations must be unique"))
    else
      None
  }

  private def _normalize_boundary(value: AggregateBoundary): AggregateBoundary =
    value.copy(
      members = value.members.sortBy(_binding_key),
      publicOperations = value.publicOperations.sortBy(_binding_key)
    )

  private def _use_case_layers_key(value: UseCaseLayers): ((String, String), (String, String), (String, String), ((String, String, String, String), (String, String, String, String))) = {
    val realizations = value.realizations.map(_realization_key).sorted
    (
      (value.business.layer.id, value.business.id),
      (value.system.layer.id, value.system.id),
      (value.ui.layer.id, value.ui.id),
      (realizations(0), realizations(1))
    )
  }

  private def _step_key(value: UiUseCaseStep): (String, String, String) =
    _step_reference_key(value.uiUseCase, value.stepId)

  private def _step_reference_key(reference: UseCaseReference, stepid: String): (String, String, String) =
    (reference.layer.id, reference.id, stepid)

  private def _mapping_key(value: ScreenInteractionMapping): (String, String, String, String, String) =
    (value.uiUseCase.layer.id, value.uiUseCase.id, value.stepId, value.screenId, value.interactionId)

  private def _projection_aggregate_error(input: UseCaseScreenProjection): Option[LogicalUiError] = {
    val boundaries = input.aggregateBoundaries
    val overlap = boundaries.indices.toStream.flatMap { index =>
      val left = (Vector(boundaries(index).aggregate, boundaries(index).root) ++ boundaries(index).members ++ boundaries(index).publicOperations).map(_binding_key).toSet
      boundaries.drop(index + 1).find { right =>
        val rightreferences = (Vector(right.aggregate, right.root) ++ right.members ++ right.publicOperations).map(_binding_key).toSet
        left.intersect(rightreferences).nonEmpty
      }.map(right => (boundaries(index), right))
    }.headOption
    overlap.map { pair =>
      LogicalUiError("LUI43_PROJECTION_COMPONENT_CLOSED", "projection.aggregateBoundaries", s"Aggregate boundaries ${pair._1.aggregate.exportId} and ${pair._2.aggregate.exportId} overlap")
    }.orElse {
      val aggregateusages = input.screens.flatMap { screen =>
        val subjectusage = if (screen.subject.role == AggregateRole) Vector(screen.subject.binding) else Vector.empty
        val interactionusages = screen.interactions.flatMap(_.componentUsages).collect {
          case usage if usage.role == AggregateRole => usage.binding
        }
        subjectusage ++ interactionusages
      }.toSet
      boundaries.find(boundary => !aggregateusages.contains(boundary.aggregate)) match {
        case Some(boundary) =>
          Some(LogicalUiError("LUI43_PROJECTION_COMPONENT_CLOSED", s"projection.aggregateBoundaries.${boundary.aggregate.exportId}", "an Aggregate boundary must have an explicit Aggregate role usage"))
        case None =>
          input.screens.iterator.flatMap(_.interactions).flatMap { interaction =>
            interaction.mutation.map(action => (interaction, action))
          }.flatMap { pair =>
            val interaction = pair._1
            val action = pair._2
            val operationusage = interaction.componentUsages.exists(usage => usage.role == OperationRole && usage.binding == action.operation)
            boundaries.find(boundary => boundary.root == action.target || boundary.members.contains(action.target)) match {
              case None =>
                Some(LogicalUiError("LUI43_PROJECTION_AGGREGATE_MUTATION_BYPASS", s"projection.interactions.${interaction.id}.mutation.target", "a mutation target must belong to a declared Aggregate boundary"))
              case Some(boundary) if boundary.root != action.target =>
                Some(LogicalUiError("LUI43_PROJECTION_AGGREGATE_MUTATION_BYPASS", s"projection.interactions.${interaction.id}.mutation.target", "an Aggregate child cannot be a mutation target"))
              case Some(_) if !operationusage =>
                Some(LogicalUiError("LUI43_PROJECTION_OPERATION_MISSING", s"projection.interactions.${interaction.id}.mutation.operation", "a mutating invocation must explicitly use the same Operation binding"))
              case Some(boundary) if !boundary.publicOperations.contains(action.operation) =>
                Some(LogicalUiError("LUI43_PROJECTION_AGGREGATE_MUTATION_BYPASS", s"projection.interactions.${interaction.id}.mutation.operation", "a root mutation must use a declared public Aggregate Operation"))
              case Some(_) => None
            }
          }.toStream.headOption
      }
    }
  }

  private def _normalize_input(input: CandidateInput): Either[LogicalUiError, CandidateInput] = {
    if (input == null)
      Left(LogicalUiError("LUI43_INPUT_MISSING", "input", "candidate input is required"))
    else
      _normalize_surfaces(input.componentSurfaces) match {
        case Left(error) => Left(error)
        case Right(surfaces) =>
          _normalize_bindings(input.componentBindings, surfaces) match {
            case Left(error) => Left(error)
            case Right(bindings) =>
              _normalize_use_cases(input.useCases) match {
                case Left(error) => Left(error)
                case Right(usecases) => Right(CandidateInput(surfaces, bindings, usecases))
              }
          }
      }
  }

  private def _normalize_surfaces(values: Vector[ComponentSurface]): Either[LogicalUiError, Vector[ComponentSurface]] = {
    val surfaces = Option(values).getOrElse(Vector.empty)
    if (surfaces.isEmpty)
      Left(LogicalUiError("LUI43_COMPONENT_SURFACE_MISSING", "componentSurfaces", "at least one public Component surface is required"))
    else {
      val error = surfaces.zipWithIndex.flatMap { case (surface, index) =>
        _surface_error(surface, s"componentSurfaces[$index]")
      }.headOption
      error match {
        case Some(value) => Left(value)
        case None =>
          val identities = surfaces.map(_.component.canonicalIdentity)
          if (identities.distinct.size != identities.size)
            Left(LogicalUiError("LUI43_COMPONENT_SURFACE_DUPLICATE", "componentSurfaces", "Component surface identity must be unique"))
          else {
            val normalized = surfaces.map { surface =>
              surface.copy(exportIds = surface.exportIds.sorted)
            }.sortBy(_.component.canonicalIdentity)
            Right(normalized)
          }
      }
    }
  }

  private def _normalize_bindings(
    values: Vector[ComponentBinding],
    surfaces: Vector[ComponentSurface]
  ): Either[LogicalUiError, Vector[ComponentBinding]] = {
    val bindings = Option(values).getOrElse(Vector.empty)
    if (bindings.isEmpty)
      Left(LogicalUiError("LUI43_COMPONENT_BINDING_MISSING", "componentBindings", "at least one direct public export binding is required"))
    else {
      val surfacebyidentity = surfaces.map(surface => surface.component.canonicalIdentity -> surface).toMap
      val error = bindings.zipWithIndex.flatMap { case (binding, index) =>
        _binding_error(binding, surfacebyidentity, s"componentBindings[$index]")
      }.headOption
      error match {
        case Some(value) => Left(value)
        case None =>
          val keys = bindings.map(_binding_key)
          if (keys.distinct.size != keys.size)
            Left(LogicalUiError("LUI43_COMPONENT_BINDING_DUPLICATE", "componentBindings", "Component binding must be unique"))
          else
            Right(bindings.sortBy(_binding_key))
      }
    }
  }

  private def _normalize_use_cases(value: UseCaseLayers): Either[LogicalUiError, UseCaseLayers] = {
    if (value == null)
      Left(LogicalUiError("LUI43_USE_CASES_MISSING", "useCases", "Business, System, and UI UseCase identities are required"))
    else {
      val referenceerrors = Vector(
        _reference_error(value.business, Business, "useCases.business"),
        _reference_error(value.system, System, "useCases.system"),
        _reference_error(value.ui, Ui, "useCases.ui")
      ).flatten
      referenceerrors.headOption match {
        case Some(error) => Left(error)
        case None =>
          val references = Vector(value.business, value.system, value.ui)
          if (references.map(_.id).distinct.size != references.size)
            Left(LogicalUiError("LUI43_USE_CASE_IDENTITY_DUPLICATE", "useCases", "Business, System, and UI identities must be distinct"))
          else {
            val realizations = Option(value.realizations).getOrElse(Vector.empty)
            if (realizations.exists(_ == null))
              Left(LogicalUiError("LUI43_USE_CASE_REALIZATION_INVALID", "useCases.realizations", "realization must be an explicit relation"))
            else {
              val expected = Vector(
                UseCaseRealization(value.business, value.system),
                UseCaseRealization(value.system, value.ui)
              )
              if (realizations.size != expected.size || realizations.toSet != expected.toSet)
                Left(LogicalUiError("LUI43_USE_CASE_REALIZATION_INVALID", "useCases.realizations", "v1 requires exactly Business -> System and System -> UI realizations"))
              else
                Right(value.copy(realizations = realizations.sortBy(_realization_key)))
            }
          }
      }
    }
  }

  private def _surface_error(surface: ComponentSurface, path: String): Option[LogicalUiError] = {
    if (surface == null)
      Some(LogicalUiError("LUI43_COMPONENT_SURFACE_INVALID", path, "Component surface is required"))
    else
      _coordinate_error(surface.component, s"$path.component").orElse {
        val exports = Option(surface.exportIds).getOrElse(Vector.empty)
        if (exports.isEmpty)
          Some(LogicalUiError("LUI43_COMPONENT_EXPORT_MISSING", s"$path.exportIds", "at least one explicit public export ID is required"))
        else if (exports.exists(value => _required_text_error(value, s"$path.exportIds").isDefined))
          Some(LogicalUiError("LUI43_COMPONENT_EXPORT_INVALID", s"$path.exportIds", "export ID must be nonempty and trimmed"))
        else if (exports.distinct.size != exports.size)
          Some(LogicalUiError("LUI43_COMPONENT_EXPORT_DUPLICATE", s"$path.exportIds", "export ID must be unique within its Component surface"))
        else
          None
      }
  }

  private def _binding_error(
    binding: ComponentBinding,
    surfaces: Map[String, ComponentSurface],
    path: String
  ): Option[LogicalUiError] = {
    if (binding == null)
      Some(LogicalUiError("LUI43_COMPONENT_BINDING_INVALID", path, "Component binding is required"))
    else
      _coordinate_error(binding.component, s"$path.component").orElse {
        _required_text_error(binding.exportId, s"$path.exportId")
      }.orElse {
        surfaces.get(binding.component.canonicalIdentity) match {
          case None => Some(LogicalUiError("LUI43_COMPONENT_SURFACE_CLOSED", s"$path.component", "Component binding must use an exact declared public surface identity"))
          case Some(surface) if !surface.exportIds.contains(binding.exportId) =>
            Some(LogicalUiError("LUI43_COMPONENT_EXPORT_CLOSED", s"$path.exportId", "export ID is not declared by the exact public Component surface"))
          case Some(_) => None
        }
      }
  }

  private def _reference_error(
    reference: UseCaseReference,
    expected: UseCaseLayer,
    path: String
  ): Option[LogicalUiError] = {
    if (reference == null)
      Some(LogicalUiError("LUI43_USE_CASE_IDENTITY_MISSING", path, "UseCase identity is required"))
    else if (reference.layer != expected)
      Some(LogicalUiError("LUI43_USE_CASE_LAYER_INVALID", s"$path.layer", s"expected ${expected.id} layer"))
    else
      _required_text_error(reference.id, s"$path.id")
  }

  private def _coordinate_error(value: ComponentCoordinate, path: String): Option[LogicalUiError] = {
    if (value == null)
      Some(LogicalUiError("LUI43_COMPONENT_COORDINATE_INVALID", path, "Component coordinate is required"))
    else
      _required_text_error(value.namespace, s"$path.namespace").orElse {
        _required_text_error(value.id, s"$path.id")
      }.orElse {
        _required_text_error(value.version, s"$path.version")
      }.orElse {
        if (value.namespace.split("\\.", -1).exists(segment => !_component_segment_pattern.pattern.matcher(segment).matches))
          Some(LogicalUiError("LUI43_COMPONENT_COORDINATE_INVALID", s"$path.namespace", "namespace must be dot-separated canonical segments"))
        else if (!_component_segment_pattern.pattern.matcher(value.id).matches)
          Some(LogicalUiError("LUI43_COMPONENT_COORDINATE_INVALID", s"$path.id", "id must be one canonical Component segment"))
        else
          None
      }
  }

  private def _required_text_error(value: String, path: String): Option[LogicalUiError] =
    if (value == null || value.isEmpty || value != value.trim)
      Some(LogicalUiError("LUI43_INPUT_TEXT_INVALID", path, "value must be nonempty and trimmed"))
    else
      None

  private def _acceptance_error(candidate: LogicalUiCandidate, decision: AcceptanceDecision): Option[LogicalUiError] = {
    if (decision == null)
      Some(LogicalUiError("LUI43_ACCEPTANCE_DECISION_MISSING", "decision", "an explicit acceptance decision is required"))
    else
      _required_text_error(decision.decisionId, "decision.decisionId").orElse {
        _required_text_error(decision.candidateIdentity, "decision.candidateIdentity")
      }.orElse {
        if (decision.candidateIdentity != candidate.identity)
          Some(LogicalUiError("LUI43_ACCEPTANCE_CANDIDATE_MISMATCH", "decision.candidateIdentity", "acceptance decision must bind exactly the supplied candidate identity"))
        else
          None
      }
  }

  private def _binding_key(value: ComponentBinding): (String, String, String, String) =
    (value.component.namespace, value.component.id, value.component.version, value.exportId)

  private def _realization_key(value: UseCaseRealization): (String, String, String, String) =
    (value.source.layer.id, value.source.id, value.target.layer.id, value.target.id)

}
