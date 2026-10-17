package cozy.ui

import java.nio.charset.StandardCharsets
import cozy.ui.CozyLogicalUi._
import cozy.ui.CozyLogicalUiReadiness._
import cozy.ui.CozyLogicalUiSelection._
import cozy.ui.CozyLogicalUiSemantics._
import cozy.ui.CozyLogicalUiReview.ReviewDiagnostic
import play.api.libs.json._

/*
 * @since   Oct. 8, 2026
 * @version Oct. 13, 2026
 * @author  ASAMI, Tomoharu
 */
private[cozy] object CozyLogicalUiReadinessReview {
  final case class CoverageCase(coverageId: String, windowClass: AdaptiveIntent, outcomeId: String)
  final case class ReadinessScenario(normal: CozyLogicalUiCompactWeb.CompactScenario, coverage: Vector[CoverageCase])
  final case class SourceInputs(
    kernelContent: String, projectionContent: String, semanticContent: String, readinessContent: String)
  final case class CompleteBinding(decision: Option[TargetReadyAcceptanceDecision])
  final case class SourceFlow(
    operationBinding: ComponentBinding, stateMachineBinding: ComponentBinding,
    powertypeBinding: ComponentBinding, powertypeVariantId: String,
    from: DomainStateReference, to: DomainStateReference, successUiState: UiInteractionState,
    workflowState: Option[WorkflowStateReference])
  final case class ReviewReceipt(
    schema: String, version: Int, rendererProfile: String, binding: CompleteBinding,
    sourceInputs: SourceInputs, scenario: ReadinessScenario, sourceFlow: SourceFlow,
    diagnostics: Vector[ReviewDiagnostic], outputs: Vector[String])

  final class ReadinessReviewPreview private[CozyLogicalUiReadinessReview] (
    val html: String, val receipt: ReviewReceipt, val receiptJson: String,
    val diagnostics: Vector[ReviewDiagnostic],
    private val _candidate: TargetReadyCandidate,
    private val _accepted: Option[AcceptedTargetReadyLogicalUi], private val _scenario: ReadinessScenario
  ) {
    def htmlBytes: Vector[Byte] = html.getBytes(StandardCharsets.UTF_8).toVector
    def copy(
      html: String = this.html, receipt: ReviewReceipt = this.receipt,
      receiptJson: String = this.receiptJson, diagnostics: Vector[ReviewDiagnostic] = this.diagnostics
    ): ReadinessReviewPreview = new ReadinessReviewPreview(html, receipt, receiptJson, diagnostics,
      _candidate, _accepted, _scenario)
  }

  def renderCandidate(
    completeCandidate: TargetReadyCandidate, scenario: ReadinessScenario
  ): Either[LogicalUiError, ReadinessReviewPreview] = _render(completeCandidate, None, scenario)

  def renderAccepted(
    accepted: AcceptedTargetReadyLogicalUi, scenario: ReadinessScenario
  ): Either[LogicalUiError, ReadinessReviewPreview] = {
    if (accepted == null) Left(_error("accepted", "complete accepted value required"))
    else _render(accepted.candidate, Some(accepted), scenario)
  }

  private def _render(
    candidate: TargetReadyCandidate, accepted: Option[AcceptedTargetReadyLogicalUi], scenario: ReadinessScenario
  ): Either[LogicalUiError, ReadinessReviewPreview] = {
    if (candidate == null) return Left(_error("completeCandidate", "complete candidate required"))
    val current = candidate
    for {
      _ <- accepted match {
        case None => Right(())
        case Some(value) => CozyLogicalUiReadiness.accept(current, value.decision).left.map(error =>
          _error(error.path, error.reason)).map(_ => ())
      }
      _ <- _check(scenario != null, "scenario", "complete scenario required")
      compact <- CozyLogicalUiCompactWeb.render(current.kernelCandidate, current.projection,
        current.semantics, scenario.normal).left.map(error =>
          _error(if (error.path == "scenario") "scenario.normal" else error.path.replace("scenario.", "scenario.normal."), error.reason))
      normalized <- _scenario(current, scenario)
      legacy <- CozyLogicalUiReview.renderCandidate(current.kernelCandidate, current.projection, current.semantics)
        .left.map(error => _error(error.path, error.reason))
    } yield {
      val action = current.semantics.input.transitionActions.find(value => value.step == normalized.normal.flow.step &&
        value.screenId == normalized.normal.flow.detailScreenId &&
        value.interactionId == normalized.normal.flow.commandInteractionId).get
      val binding = CompleteBinding(accepted.map(_.decision))
      val sources = SourceInputs(current.kernelCandidate.canonicalContent, current.projection.canonicalContent,
        current.semantics.canonicalContent, current.canonicalContent)
      val flow = SourceFlow(compact.receipt.operationBinding, compact.receipt.stateMachineBinding,
        compact.receipt.powertypeBinding, compact.receipt.powertypeVariantId,
        action.from, action.to, action.interactionState, action.workflowState)
      val diagnostics = legacy.diagnostics.sortBy(value => (value.code, value.path, value.reason))
      val receipt = ReviewReceipt("cozy.logical-ui-target-ready-review.v1", 1,
        "cozy.logical-ui-target-ready-review.renderer.v1", binding, sources, normalized, flow,
        diagnostics, Vector("review.html", "readiness-review-receipt.json"))
      val json = _receipt_json(receipt, Json.parse(compact.receiptJson) \ "scenario")
      val html = legacy.html.replace("<h1>Logical UI Review</h1>",
        "<h1>Complete target-ready Logical UI review</h1>" + _sections(receipt, json) +
          "<h2>Kernel candidate evidence</h2>")
        .replace("</style>", "td,dd,pre{white-space:pre-wrap;overflow-wrap:anywhere}section{min-width:0}</style>")
      new ReadinessReviewPreview(html, receipt, json, diagnostics, candidate, accepted, scenario)
    }
  }

  private def _scenario(candidate: TargetReadyCandidate, scenario: ReadinessScenario): Either[LogicalUiError, ReadinessScenario] = {
    val input = candidate.input
    val flow = scenario.normal.flow
    val command = InteractionRef(flow.detailScreenId, flow.commandInteractionId)
    val entry = InteractionRef(flow.listScreenId, flow.listEntryInteractionId)
    val categories = Set[FeedbackState](NormalFeedback, LoadingFeedback, EmptyFeedback, ValidationFailedFeedback,
      ConflictFeedback, UnavailableFeedback, OperationFailedFeedback)
    if (scenario.normal.fixtureId != "sales-order-target-ready-v1")
      return Left(_error("scenario.normal.fixtureId", "the shared complete SalesOrder fixture is required"))
    if (input.outcomes.size != 7 || input.outcomes.map(_.feedbackState).toSet != categories)
      return Left(_error("input.outcomes", "exactly one outcome per seven feedback categories required"))
    val roles = Vector(EntryControl, NavigationControl, InputControl, SelectionControl, CommandControl, ObservationControl, FeedbackControl)
    if (input.controls.size != 7 || roles.exists(role => input.controls.count(_.role == role) != 1))
      return Left(_error("input.controls", "exactly one control per supported role required"))
    if (input.fields.size != 1) return Left(_error("input.fields", "one input field required"))
    val expected = Vector((NavigationControl, 1), (InputControl, 2), (SelectionControl, 3), (CommandControl, 4))
    expected.foreach { case (role, rank) =>
      if (input.controls.find(_.role == role).get.focusOrder != Some(rank))
        return Left(_error("input.controls.focusOrder", "source navigation/input/selection/command rank must match natural DOM order"))
    }
    val refs = Vector(EntryControl -> entry,
      NavigationControl -> InteractionRef(flow.listScreenId, flow.navigationInteractionId),
      SelectionControl -> InteractionRef(flow.detailScreenId, flow.selectionInteractionId), CommandControl -> command,
      ObservationControl -> InteractionRef(flow.detailScreenId, flow.observationInteractionId))
    refs.foreach { case (role, ref) =>
      if (input.controls.find(_.role == role).get.control != ref)
        return Left(_error("input.controls", "control must join the declared normal flow"))
    }
    if (input.fields.head.control.screenId != flow.detailScreenId ||
      input.controls.find(_.role == FeedbackControl).get.control.screenId != flow.detailScreenId)
      return Left(_error("input.controls", "field and feedback must belong to the declared detail"))
    if (input.fields.head.labelTextId != input.controls.find(_.role == InputControl).get.labelTextId)
      return Left(_error("input.fields[0].labelTextId", "one visible field/control source label required"))
    input.profiles.foreach { profile =>
      if (profile.persistentNavigation != (profile.windowClass != CompactIntent) ||
        profile.regions.count(_.region.screenId == flow.listScreenId) != 1 ||
        profile.regions.count(_.region.screenId == flow.detailScreenId) != 2 ||
        profile.regions.count(value => value.region.screenId == flow.listScreenId && value.role == PrimaryRegion) != 1 ||
        profile.regions.count(value => value.region.screenId == flow.detailScreenId && value.role == PrimaryRegion) != 1 ||
        profile.regions.count(value => value.region.screenId == flow.detailScreenId && value.role == SecondaryRegion) != 1 ||
        profile.regions.map(value => (value.region, value.role)).toSet != input.profiles.head.regions.map(value => (value.region, value.role)).toSet ||
        profile.regions.exists(_.persistent != (profile.windowClass == ExpandedIntent)))
        return Left(_error("input.profiles", "supported list/detail region and navigation persistence required"))
    }
    input.outcomes.zipWithIndex.foreach { case (outcome, index) =>
      val path = "input.outcomes[" + index + "]"
      val expectedtrigger = outcome.feedbackState match {
        case EmptyFeedback => entry
        case ValidationFailedFeedback => input.fields.head.control
        case _ => command
      }
      if (outcome.trigger != expectedtrigger) return Left(_error(path + ".trigger", "exact source trigger required"))
      if ((outcome.feedbackState == NormalFeedback || outcome.feedbackState == LoadingFeedback) && outcome.step != flow.step)
        return Left(_error(path + ".step", "normal and loading must join normal source command step"))
      val domain = if (outcome.feedbackState == NormalFeedback) scenario.normal.successfulOrder.domainState
        else scenario.normal.initialOrder.domainState
      if (outcome.domainState != domain) return Left(_error(path + ".domainState", "exact scenario source state required"))
      if (outcome.feedbackState == NormalFeedback && outcome.uiState != scenario.normal.successUiState)
        return Left(_error(path + ".uiState", "exact scenario success UI state required"))
      val feedbackref = if (outcome.feedbackState == EmptyFeedback) entry
        else input.controls.find(_.role == FeedbackControl).get.control
      if (outcome.feedbackControl != feedbackref)
        return Left(_error(path + ".feedbackControl", "exact source feedback control required"))
    }
    if (scenario.coverage == null) return Left(_error("scenario.coverage", "coverage vector required"))
    scenario.coverage.zipWithIndex.foreach { case (value, index) =>
      val path = "scenario.coverage[" + index + "]"
      if (value == null) return Left(_error(path, "coverage case required"))
      if (!_id_valid(value.coverageId)) return Left(_error(path + ".coverageId", "trimmed nonempty coverage ID required"))
      if (!input.profiles.exists(_.windowClass == value.windowClass))
        return Left(_error(path + ".windowClass", "admitted profile required"))
      if (!input.outcomes.exists(_.outcomeId == value.outcomeId))
        return Left(_error(path + ".outcomeId", "admitted outcome required"))
      if (scenario.coverage.take(index).exists(_.coverageId == value.coverageId))
        return Left(_error(path + ".coverageId", "unique coverage ID required"))
      if (scenario.coverage.take(index).exists(other => other.windowClass == value.windowClass && other.outcomeId == value.outcomeId))
        return Left(_error(path, "unique profile/outcome pair required"))
    }
    if (scenario.coverage.map(value => (value.windowClass, value.outcomeId)).toSet !=
      input.profiles.flatMap(profile => input.outcomes.map(outcome => (profile.windowClass, outcome.outcomeId))).toSet)
      return Left(_error("scenario.coverage", "every admitted profile/outcome pair required"))
    if (!scenario.coverage.exists(value => value.windowClass == CompactIntent &&
      value.outcomeId == input.outcomes.find(_.feedbackState == NormalFeedback).get.outcomeId &&
      value.coverageId == scenario.normal.coverageId))
      return Left(_error("scenario.normal.coverageId", "normal compact coverage ID must name its declared case"))
    Right(scenario.copy(coverage = scenario.coverage.sortBy(value => (_window_id(value.windowClass), value.outcomeId))))
  }

  private def _receipt_json(value: ReviewReceipt, normal: JsLookupResult): String = {
    val binding = value.binding
    val sources = value.sourceInputs
    val flow = value.sourceFlow
    Json.stringify(Json.obj(
      "schema" -> value.schema, "version" -> value.version, "rendererProfile" -> value.rendererProfile,
      "binding" -> Json.obj("decision" -> binding.decision.map(decision => Json.obj(
        "decisionId" -> decision.decisionId, "reviewerId" -> decision.reviewerId))),
      "sourceInputs" -> Json.obj("kernelContent" -> Json.parse(sources.kernelContent),
        "projectionContent" -> Json.parse(sources.projectionContent), "semanticContent" -> Json.parse(sources.semanticContent),
        "readinessContent" -> Json.parse(sources.readinessContent)),
      "scenario" -> Json.obj("normal" -> normal.get, "coverage" -> JsArray(value.scenario.coverage.map(coverage =>
        Json.obj("coverageId" -> coverage.coverageId, "windowClass" -> _window_id(coverage.windowClass), "outcomeId" -> coverage.outcomeId)))),
      "sourceFlow" -> Json.obj("operationBinding" -> _binding_json(flow.operationBinding),
        "stateMachineBinding" -> _binding_json(flow.stateMachineBinding), "powertypeBinding" -> _binding_json(flow.powertypeBinding),
        "powertypeVariantId" -> flow.powertypeVariantId, "from" -> _domain_json(flow.from), "to" -> _domain_json(flow.to),
        "successUiState" -> flow.successUiState.id, "workflowState" -> flow.workflowState.map(workflow =>
          Json.obj("workflowId" -> workflow.workflowId, "stateId" -> workflow.stateId))),
      "diagnostics" -> JsArray(value.diagnostics.map(diagnostic => Json.obj("code" -> diagnostic.code,
        "path" -> diagnostic.path, "reason" -> diagnostic.reason))), "outputs" -> value.outputs))
  }

  private def _sections(receipt: ReviewReceipt, json: String): String = {
    val value = Json.parse(json)
    val readiness = Json.parse(receipt.sourceInputs.readinessContent) \ "readiness"
    val overview = "<section><h2>Complete authority and provenance</h2><p>" +
      (if (receipt.binding.decision.isDefined) "Accepted complete input; explicit attributed decision" else "Complete candidate preview; acceptance pending") +
      "</p>" + _table("Complete binding", Vector((value \ "binding").as[JsObject])) + "</section>"
    val declarations = Vector("profiles" -> "Window placement, navigation and region persistence",
      "controls" -> "Source control roles and global focus", "fields" -> "Input intent, Value/Datatype and local constraint feedback",
      "textSources" -> "Text identities, source attribution and preserved default values",
      "outcomes" -> "Outcomes: separate UI, domain, Workflow evidence and mapped recoveries")
      .map { case (key, title) => _table(title, (readiness \ key).as[Vector[JsObject]]) }.mkString
    val scenario = _table("Scenario normal records and flow", Vector((value \ "scenario" \ "normal").as[JsObject])) +
      _table("Profile and outcome coverage", (value \ "scenario" \ "coverage").as[Vector[JsObject]]) +
      _table("Exact Operation, StateMachine and Powertype source flow", Vector((value \ "sourceFlow").as[JsObject]))
    val sources = (value \ "sourceInputs").as[JsObject].fields.map { case (name, content) =>
      "<details><summary>Full canonical " + _escape(name) + "</summary><pre>" + _escape(Json.prettyPrint(content)) + "</pre></details>"
    }.mkString
    overview + declarations + scenario + sources
  }

  private def _table(title: String, rows: Vector[JsObject]): String = {
    val columns = rows.headOption.map(_.keys.toVector).getOrElse(Vector.empty).sorted
    "<section><h2>" + _escape(title) + "</h2><table><thead><tr>" +
      columns.map(name => "<th scope=\"col\">" + _escape(name) + "</th>").mkString + "</tr></thead><tbody>" +
      rows.map(row => "<tr>" + columns.map(name => "<td>" + _escape((row \ name).get match {
        case JsString(text) => text
        case other => Json.prettyPrint(other)
      }) + "</td>").mkString + "</tr>").mkString + "</tbody></table></section>"
  }

  private def _binding_json(binding: ComponentBinding): JsObject = Json.obj("component" -> Json.obj(
    "namespace" -> binding.component.namespace, "id" -> binding.component.id, "version" -> binding.component.version,
    "canonicalIdentity" -> binding.component.canonicalIdentity), "exportId" -> binding.exportId)
  private def _domain_json(value: DomainStateReference): JsObject = Json.obj(
    "stateMachineBinding" -> _binding_json(value.stateMachineBinding), "stateId" -> value.stateId)
  private def _window_id(intent: AdaptiveIntent): String = intent match {
    case CompactIntent => "compact"
    case MediumIntent => "medium"
    case ExpandedIntent => "expanded"
  }
  private def _id_valid(value: String): Boolean = value != null && value.nonEmpty && value == value.trim
  private def _check(valid: Boolean, path: String, reason: String): Either[LogicalUiError, Unit] =
    if (valid) Right(()) else Left(_error(path, reason))
  private def _error(path: String, reason: String, category: String = "CONTRACT"): LogicalUiError =
    LogicalUiError("LUI501_REVIEW_" + category + "_INVALID", path, reason)
  private def _escape(value: String): String = value.flatMap {
    case '&' => "&amp;"
    case '<' => "&lt;"
    case '>' => "&gt;"
    case '"' => "&quot;"
    case '\'' => "&#39;"
    case character => character.toString
  }
}
