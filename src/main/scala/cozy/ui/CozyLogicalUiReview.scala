package cozy.ui

import java.io.IOException
import java.nio.charset.StandardCharsets
import java.nio.file.{Files, LinkOption, Path, StandardOpenOption}

import cozy.ui.CozyLogicalUi._
import cozy.ui.CozyLogicalUiSemantics._

/*
 * @since   Sep. 1, 2026
 * @version Oct. 13, 2026
 * @author  ASAMI, Tomoharu
 */
private[cozy] object CozyLogicalUiReview {
  final case class ReviewDiagnostic(code: String, path: String, reason: String)

  final class LogicalUiReview private[CozyLogicalUiReview] (
    val html: String,
    val diagnostics: Vector[ReviewDiagnostic]
  ) {
    def htmlBytes: Vector[Byte] = html.getBytes(StandardCharsets.UTF_8).toVector
  }

  final case class CandidateReviewReceipt(
    schema: String,
    version: Int,
    rendererProfile: String
  )

  final class LogicalUiCandidateReview private[CozyLogicalUiReview] (
    val html: String,
    val receipt: CandidateReviewReceipt,
    val diagnostics: Vector[ReviewDiagnostic],
    private[CozyLogicalUiReview] val _candidate: LogicalUiCandidate,
    private[CozyLogicalUiReview] val _projection: LogicalUiProjection,
    private[CozyLogicalUiReview] val _semantics: LogicalUiSemanticProjection
  ) {
    def htmlBytes: Vector[Byte] = html.getBytes(StandardCharsets.UTF_8).toVector
    def copy(
      html: String = this.html,
      receipt: CandidateReviewReceipt = this.receipt,
      diagnostics: Vector[ReviewDiagnostic] = this.diagnostics
    ): LogicalUiCandidateReview = new LogicalUiCandidateReview(html, receipt, diagnostics, _candidate, _projection, _semantics)
  }

  private val _schema = "cozy.logical-ui-review.v1"
  private val _version = 1
  private val _renderer_profile = "cozy.logical-ui-review.renderer.v1"
  private val _candidate_schema = "cozy.logical-ui-candidate-review.v1"
  private val _candidate_renderer_profile = "cozy.logical-ui-candidate-review.renderer.v1"

  def schema: String = _schema
  def version: Int = _version
  def rendererProfile: String = _renderer_profile

  def render(
    accepted: AcceptedLogicalUi,
    projection: LogicalUiProjection,
    semantics: LogicalUiSemanticProjection
  ): Either[LogicalUiError, LogicalUiReview] = {
    _input_error(accepted, projection, semantics) match {
      case Some(error) => Left(error)
      case None =>
        val diagnostics = _diagnostics(projection, semantics)
        val html = _html(accepted.candidate, projection, semantics, diagnostics, _overview_html(accepted))
        Right(new LogicalUiReview(html, diagnostics))
    }
  }

  def renderCandidate(
    candidate: LogicalUiCandidate,
    projection: LogicalUiProjection,
    semantics: LogicalUiSemanticProjection
  ): Either[LogicalUiError, LogicalUiCandidateReview] = {
    _candidate_input_error(candidate, projection, semantics) match {
      case Some(error) => Left(error)
      case None =>
        val diagnostics = _diagnostics(projection, semantics)
        val html = _html(candidate, projection, semantics, diagnostics, _candidate_overview_html(candidate, projection, semantics))
        val receipt = CandidateReviewReceipt(_candidate_schema, _version, _candidate_renderer_profile)
        Right(new LogicalUiCandidateReview(html, receipt, diagnostics, candidate, projection, semantics))
    }
  }

  def writeCandidate(
    reviewValue: LogicalUiCandidateReview,
    parent: Path,
    target: Path
  ): Either[LogicalUiError, Unit] = {
    if (reviewValue == null)
      Left(_candidate_input_error_value("review", "a LogicalUiCandidateReview is required"))
    else
      _write_html(reviewValue.html, parent, target)
  }

  def review(
    accepted: AcceptedLogicalUi,
    projection: LogicalUiProjection,
    semantics: LogicalUiSemanticProjection
  ): Either[LogicalUiError, LogicalUiReview] = render(accepted, projection, semantics)

  def write(
    reviewValue: LogicalUiReview,
    parent: Path,
    target: Path
  ): Either[LogicalUiError, Unit] = {
    if (reviewValue == null)
      Left(_input_error_value("review", "a LogicalUiReview is required"))
    else
      _write_html(reviewValue.html, parent, target)
  }

  def write(
    accepted: AcceptedLogicalUi,
    projection: LogicalUiProjection,
    semantics: LogicalUiSemanticProjection,
    parent: Path,
    target: Path
  ): Either[LogicalUiError, Unit] = {
    render(accepted, projection, semantics) match {
      case Left(error) => Left(error)
      case Right(reviewvalue) => write(reviewvalue, parent, target)
    }
  }

  private def _write_html(
    html: String,
    parent: Path,
    target: Path
  ): Either[LogicalUiError, Unit] = {
    _output_error(parent, target) match {
      case Some(error) => Left(error)
      case None =>
        try {
          Files.write(
            target,
            html.getBytes(StandardCharsets.UTF_8),
            StandardOpenOption.CREATE,
            StandardOpenOption.WRITE,
            StandardOpenOption.TRUNCATE_EXISTING
          )
          Right(())
        } catch {
          case error: IOException =>
            Left(_output_error_value("output", s"HTML write failed: ${error.getMessage}"))
          case _: SecurityException =>
            Left(_output_error_value("output", "HTML write was denied"))
          case error: RuntimeException =>
            Left(_output_error_value("output", s"HTML write failed: ${error.getMessage}"))
        }
    }
  }

  private def _output_error(parent: Path, target: Path): Option[LogicalUiError] = {
    if (parent == null || target == null)
      Some(_output_error_value("output", "an explicit parent and target are required"))
    else if (_has_traversal_component(parent))
      Some(_output_error_value("output.parent", "output parent must not contain . or .. path components"))
    else if (_has_symbolic_link_component(parent))
      Some(_output_error_value("output.parent", "output parent must not cross a symbolic link"))
    else if (Files.isSymbolicLink(parent) || !Files.isDirectory(parent, LinkOption.NOFOLLOW_LINKS))
      Some(_output_error_value("output.parent", "output parent must be an existing direct regular directory"))
    else if (target.getParent == null || target.getParent != parent)
      Some(_output_error_value("output.target", "output target must be direct child of the explicit parent"))
    else if (!target.getFileName.toString.endsWith(".html"))
      Some(_output_error_value("output.target", "output target must have an .html suffix"))
    else if (Files.isSymbolicLink(target))
      Some(_output_error_value("output.target", "output target must not be a symbolic link"))
    else if (Files.exists(target, LinkOption.NOFOLLOW_LINKS) && !Files.isRegularFile(target, LinkOption.NOFOLLOW_LINKS))
      Some(_output_error_value("output.target", "existing output target must be a regular file"))
    else None
  }

  private def _has_traversal_component(path: Path): Boolean = {
    val components = path.iterator()
    while (components.hasNext) {
      val component = components.next()
      if (component.toString == "." || component.toString == "..")
        return true
    }
    false
  }

  private def _has_symbolic_link_component(path: Path): Boolean = {
    val absolute = path.toAbsolutePath
    val components = absolute.iterator()
    var current = absolute.getRoot
    var leading = true
    while (components.hasNext) {
      val component = components.next()
      current = if (current == null) component else current.resolve(component)
      if (!leading && Files.isSymbolicLink(current))
        return true
      leading = false
    }
    false
  }

  private def _input_error(
    accepted: AcceptedLogicalUi,
    projection: LogicalUiProjection,
    semantics: LogicalUiSemanticProjection
  ): Option[LogicalUiError] = {
    if (accepted == null)
      Some(_input_error_value("accepted", "accepted Logical UI is required"))
    else if (projection == null)
      Some(_input_error_value("projection", "Logical UI screen projection is required"))
    else if (semantics == null)
      Some(_input_error_value("semantics", "Logical UI semantic projection is required"))
    else if (accepted.candidate == null)
      Some(_input_error_value("accepted.candidate", "accepted candidate is required"))
    else if (projection.candidate == null)
      Some(_input_error_value("projection.candidate", "projected candidate is required"))
    else if (accepted.decision == null)
      Some(_input_error_value("accepted.decision", "explicit acceptance decision is required"))
    else None
  }

  private def _candidate_input_error(
    candidate: LogicalUiCandidate,
    projection: LogicalUiProjection,
    semantics: LogicalUiSemanticProjection
  ): Option[LogicalUiError] = {
    if (candidate == null)
      Some(_candidate_input_error_value("candidate", "a Logical UI candidate is required"))
    else if (projection == null)
      Some(_candidate_input_error_value("projection", "Logical UI screen projection is required"))
    else if (semantics == null)
      Some(_candidate_input_error_value("semantics", "Logical UI semantic projection is required"))
    else if (projection.candidate == null)
      Some(_candidate_input_error_value("projection.candidate", "a projected candidate is required"))
    else None
  }

  private def _diagnostics(
    projection: LogicalUiProjection,
    semantics: LogicalUiSemanticProjection
  ): Vector[ReviewDiagnostic] = {
    val projected = projection.input
    val semanticinput = semantics.input
    val screens = projected.screens
    val semanticscreens = semanticinput.screens.map(value => value.screenId -> value).toMap
    val componentsemantics = semanticinput.componentBindings
    val diagnostics = Vector.newBuilder[ReviewDiagnostic]

    screens.foreach { screen =>
      if (!semanticscreens.contains(screen.id))
        diagnostics += ReviewDiagnostic("LUI43_REVIEW_COVERAGE", s"screens.${screen.id}", "screen has no semantic review binding")
      screen.interactions.foreach { interaction =>
        val semantic = semanticscreens.get(screen.id)
        val mapped = projected.mappings.exists(value => value.screenId == screen.id && value.interactionId == interaction.id)
        if (!mapped)
          diagnostics += ReviewDiagnostic("LUI43_REVIEW_COVERAGE", s"screens.${screen.id}.interactions.${interaction.id}", "interaction has no UseCase step mapping")
        interaction.componentUsages.filter(_.role == PowertypeRole).filter { usage =>
          !componentsemantics.exists(value => value.role == PowertypeRole && value.binding == usage.binding && value.powertypeVariantId.exists(item => !_empty(item)))
        }.foreach { _ =>
          diagnostics += ReviewDiagnostic("LUI43_REVIEW_POWERTYPE_VARIANT_MISSING", s"screens.${screen.id}.interactions.${interaction.id}", "Powertype interaction usage has no explicit semantic variant")
        }
        if (interaction.componentUsages.exists(_.role == StateMachineRole) &&
          !semanticinput.transitionActions.exists(value => value.screenId == screen.id && value.interactionId == interaction.id))
          diagnostics += ReviewDiagnostic("LUI43_REVIEW_TRANSITION_ACTION_MISSING", s"screens.${screen.id}.interactions.${interaction.id}", "StateMachine usage has no admitted transition action")
        if ((interaction.kind == InputInteraction || interaction.kind == InvocationInteraction) &&
          !screen.feedbackStates.exists(value => Set[FeedbackState](ValidationFailedFeedback, ConflictFeedback, OperationFailedFeedback).contains(value)))
          diagnostics += ReviewDiagnostic("LUI43_REVIEW_FAILURE_FEEDBACK_MISSING", s"screens.${screen.id}.feedbackStates", "input or invocation interaction has no expected failure feedback")
        interaction.mutation.foreach { mutation =>
          val boundary = projected.aggregateBoundaries.find(value => value.root == mutation.target || value.members.contains(mutation.target))
          if (boundary.isEmpty || !boundary.get.publicOperations.contains(mutation.operation) || boundary.get.root != mutation.target)
            diagnostics += ReviewDiagnostic("LUI43_REVIEW_AGGREGATE_BOUNDARY_DISCREPANCY", s"screens.${screen.id}.interactions.${interaction.id}.mutation", "mutation is not authorized by its Aggregate root boundary")
        }
      }
    }

    val reached = _reachable(screens)
    screens.filter(screen => !reached.contains(screen.id)).foreach { screen =>
      diagnostics += ReviewDiagnostic("LUI43_REVIEW_NAVIGATION_UNREACHABLE", s"screens.${screen.id}", "screen is not reachable from an Entry interaction")
    }

    val ordered = diagnostics.result().distinct.sortBy(value => (value.code, value.path, value.reason))
    ordered
  }

  private def _reachable(screens: Vector[LogicalScreen]): Set[String] = {
    val edges = screens.flatMap { screen =>
      screen.interactions.flatMap(_.navigation.map(endpoint => screen.id -> endpoint.targetScreenId))
    }
    val entries = screens.filter(_.interactions.exists(_.kind == EntryInteraction)).map(_.id)
    var reached = Set.empty[String]
    var frontier = entries
    while (frontier.nonEmpty) {
      val current = frontier.head
      frontier = frontier.tail
      if (!reached.contains(current)) {
        reached += current
        frontier = frontier ++ edges.collect { case (`current`, target) if !reached.contains(target) => target }
      }
    }
    reached
  }

  private def _html(
    candidate: LogicalUiCandidate,
    projection: LogicalUiProjection,
    semantics: LogicalUiSemanticProjection,
    diagnostics: Vector[ReviewDiagnostic],
    overview: String
  ): String = {
    val usecases = candidate.input.useCases
    val catalog = projection.input.catalog
    val semanticinput = semantics.input
    val semanticbyid = semanticinput.screens.map(value => value.screenId -> value).toMap
    val navigation = projection.input.screens.flatMap { screen =>
      screen.interactions.flatMap(_.navigation.map(endpoint => (screen.id, endpoint.endpointId, endpoint.targetScreenId)))
    }
    val reachable = _reachable(projection.input.screens)
    val parts = Vector(
      "<!doctype html>",
      "<html lang=\"en\"><head><meta charset=\"utf-8\"><meta name=\"viewport\" content=\"width=device-width, initial-scale=1\"><title>Logical UI Review</title>",
      "<style>body{font-family:system-ui,sans-serif;line-height:1.45;margin:2rem;color:#17202a}main{max-width:72rem;margin:auto}section,article{border:1px solid #b8c2cc;border-radius:.4rem;padding:1rem;margin:1rem 0}table{border-collapse:collapse;width:100%;margin:.5rem 0}th,td{border:1px solid #b8c2cc;padding:.35rem;text-align:left;vertical-align:top}th{background:#eef2f5}code{overflow-wrap:anywhere}dt{font-weight:700}dd{margin:0 0 .35rem 1rem}.diagnostic{border-left:.35rem solid #bd3d3d;padding-left:.7rem}</style></head>",
      "<body><main><h1>Logical UI Review</h1>",
      overview,
      _usecase_html(usecases, catalog, projection),
      _navigation_html(navigation, reachable),
      _screens_html(projection, semanticbyid),
      _components_html(projection, semanticinput),
      _constraints_html(semanticinput),
      _transitions_html(semanticinput),
      _diagnostics_html(diagnostics),
      "</main></body></html>"
    )
    parts.mkString
  }

  private def _overview_html(accepted: AcceptedLogicalUi): String =
    s"""<section aria-labelledby="overview"><h2 id="overview">Overview</h2><dl>""" +
      _definition("Schema", _schema) +
      _definition("Renderer profile", _renderer_profile) +
      _definition("Acceptance decision ID", _escape(accepted.decision.decisionId)) +
      "</dl></section>"

  private def _candidate_overview_html(
    candidate: LogicalUiCandidate,
    projection: LogicalUiProjection,
    semantics: LogicalUiSemanticProjection
  ): String = {
    "<section aria-labelledby=\"overview\"><h2 id=\"overview\">Candidate preview</h2><dl>" +
      _definition("Status", _escape("candidate (not accepted)")) +
      _definition("Schema", _escape(_candidate_schema)) +
      _definition("Renderer profile", _escape(_candidate_renderer_profile)) +
      "</dl></section>"
  }

  private def _usecase_html(
    usecases: UseCaseLayers,
    catalog: Vector[UseCaseLayers],
    projection: LogicalUiProjection
  ): String = {
    val realizationrows = usecases.realizations.map(value => s"<tr><td>${_reference_html(value.source)}</td><td>${_reference_html(value.target)}</td></tr>").mkString
    val catalogrows = catalog.map(value => s"<tr><td>${_reference_html(value.business)}</td><td>${_reference_html(value.system)}</td><td>${_reference_html(value.ui)}</td></tr>").mkString
    val steprows = projection.input.steps.map { step =>
      val mappings = projection.input.mappings.filter(value => value.uiUseCase == step.uiUseCase && value.stepId == step.stepId)
      val mappingtext = if (mappings.isEmpty) "system-only (no screen mapping)" else mappings.map(value => s"${_escape(value.screenId)} / ${_escape(value.interactionId)}").mkString(", ")
      s"<tr><td>${_reference_html(step.uiUseCase)}</td><td>${_escape(step.stepId)}</td><td>${_escape(step.path.id)}</td><td>$mappingtext</td></tr>"
    }.mkString
    "<section aria-labelledby=\"usecases\"><h2 id=\"usecases\">UseCase realization, catalog, and coverage</h2>" +
      "<h3>Business -&gt; System -&gt; UI</h3><table><thead><tr><th>Source</th><th>Target</th></tr></thead><tbody>" + realizationrows +
      "</tbody></table><h3>Catalog membership</h3><table><thead><tr><th>Business</th><th>System</th><th>UI</th></tr></thead><tbody>" + catalogrows +
      "</tbody></table><h3>Step-to-screen interaction coverage</h3><table><thead><tr><th>UI UseCase</th><th>Step</th><th>Path</th><th>Screen interaction</th></tr></thead><tbody>" + steprows +
      "</tbody></table></section>"
  }

  private def _navigation_html(values: Vector[(String, String, String)], reachable: Set[String]): String = {
    val rows = values.map { case (source, endpoint, target) => s"<tr><td>${_escape(source)}</td><td>${_escape(endpoint)}</td><td>${_escape(target)}</td></tr>" }.mkString
    val reachabletext = reachable.toVector.sorted.map(_escape).mkString(", ")
    "<section aria-labelledby=\"navigation\"><h2 id=\"navigation\">Navigation and reachability</h2><p>Reachable screens: " + reachabletext + "</p><table><thead><tr><th>Source screen</th><th>Endpoint</th><th>Target screen</th></tr></thead><tbody>" + rows + "</tbody></table></section>"
  }

  private def _screens_html(
    projection: LogicalUiProjection,
    semanticbyid: Map[String, ScreenSemantics]
  ): String = {
    val articles = projection.input.screens.map { screen =>
      val semantic = semanticbyid.get(screen.id)
      val regions = screen.regions.map { region =>
        val display = semantic.flatMap(_.regions.find(_.regionId == region.id)).map(_.display.id).getOrElse("unbound")
        s"<tr><td>${_escape(region.id)}</td><td>${_escape(region.parentId.getOrElse(""))}</td><td>${region.order}</td><td>${_escape(display)}</td></tr>"
      }.mkString
      val interactions = screen.interactions.map { interaction =>
        val pattern = semantic.flatMap(_.interactions.find(_.interactionId == interaction.id)).map(_.pattern.id).getOrElse("unbound")
        val state = semantic.flatMap(_.interactionStates.find(_.interactionId == interaction.id)).map(_.state.id).getOrElse("unbound")
        val usages = interaction.componentUsages.map(usage => s"${_escape(usage.role.id)}: ${_binding_html(usage.binding)}").mkString(", ")
        val mutation = interaction.mutation.map(value => s"target ${_binding_html(value.target)} via ${_binding_html(value.operation)}").getOrElse("")
        val endpoint = interaction.navigation.map(value => s"${_escape(value.endpointId)} -&gt; ${_escape(value.targetScreenId)}").getOrElse("")
        s"<tr><td>${_escape(interaction.id)}</td><td>${_escape(interaction.kind.id)}</td><td>${_escape(pattern)}</td><td>${_escape(state)}</td><td>$usages</td><td>$mutation</td><td>$endpoint</td></tr>"
      }.mkString
      val feedback = screen.feedbackStates.map(value => _escape(value.id)).mkString(", ")
      val primarypurpose = _escape(screen.primaryPurpose)
      val secondarypurposes = screen.secondaryPurposes.map(_escape).mkString(", ")
      val semanticpurpose = semantic.map(value => _escape(value.purpose.id)).getOrElse("unbound")
      s"""<article data-screen-id="${_escape_attribute(screen.id)}"><h3>${_escape(screen.id)}</h3><dl>""" +
        _definition("Logical primary purpose", primarypurpose) + _definition("Logical secondary purposes", secondarypurposes) +
        _definition("Semantic Purpose", semanticpurpose) + _definition("Subject", s"${_escape(screen.subject.role.id)}: ${_binding_html(screen.subject.binding)}") +
        _definition("Feedback states", feedback) + "</dl><h4>Regions and Display mappings</h4><table><thead><tr><th>Region</th><th>Parent</th><th>Order</th><th>Display</th></tr></thead><tbody>" + regions +
        "</tbody></table><h4>Interactions, Patterns, UI lifecycle, and navigation</h4><table><thead><tr><th>Interaction</th><th>Kind</th><th>Pattern</th><th>UI state</th><th>Component usages</th><th>Mutation/action</th><th>Navigation</th></tr></thead><tbody>" + interactions + "</tbody></table></article>"
    }.mkString
    "<section aria-labelledby=\"screens\"><h2 id=\"screens\">Logical Screens</h2>" + articles + "</section>"
  }

  private def _components_html(
    projection: LogicalUiProjection,
    semanticinput: LogicalUiSemanticsInput
  ): String = {
    val rows = semanticinput.componentBindings.map { value =>
      val multiplicity = value.multiplicity.map(_.id).getOrElse("")
      val variant = value.powertypeVariantId.getOrElse("")
      val states = value.domainStates.map(state => _escape(state.stateId)).mkString(", ")
      s"<tr><td>${_escape(value.role.id)}</td><td>${_binding_html(value.binding)}</td><td>${_escape(multiplicity)}</td><td>${_escape(variant)}</td><td>$states</td></tr>"
    }.mkString
    "<section aria-labelledby=\"components\"><h2 id=\"components\">Component semantic bindings</h2><table><thead><tr><th>Role</th><th>Exact public binding</th><th>Multiplicity</th><th>Powertype variant</th><th>Domain StateMachine states</th></tr></thead><tbody>" + rows + "</tbody></table></section>"
  }

  private def _constraints_html(semanticinput: LogicalUiSemanticsInput): String = {
    val rows = semanticinput.constraints.map { value =>
      val feedback = semanticinput.feedbackAssociations.filter(_.constraint == value.identity).map(_.feedbackId).mkString(", ")
      s"<tr><td>${_escape(value.identity.constraintId)}</td><td>${_escape(value.identity.detailCode)}</td><td>${_escape(value.kind.id)}</td><td>${_binding_html(value.binding)}</td><td>${_escape(value.validation.id)}</td><td>${_escape(feedback)}</td></tr>"
    }.mkString
    "<section aria-labelledby=\"constraints\"><h2 id=\"constraints\">Constraints, validation authority, and feedback associations</h2><table><thead><tr><th>constraintId</th><th>detailCode</th><th>Kind</th><th>Binding</th><th>Validation authority</th><th>Feedback</th></tr></thead><tbody>" + rows + "</tbody></table></section>"
  }

  private def _transitions_html(semanticinput: LogicalUiSemanticsInput): String = {
    val rows = semanticinput.transitionActions.map { value =>
      val workflowstate = value.workflowState.map(state => s"workflowId=${_escape(state.workflowId)}; stateId=${_escape(state.stateId)}").getOrElse("missing")
      s"<tr><td>${_escape(value.step.stepId)}</td><td>${_escape(value.screenId)}</td><td>${_escape(value.interactionId)}</td><td>${_binding_html(value.operation)}</td><td>${_binding_html(value.stateMachine)}</td><td>${_escape(value.from.stateId)} -&gt; ${_escape(value.to.stateId)}</td><td>$workflowstate</td><td>${_escape(value.interactionState.id)}</td></tr>"
    }.mkString
    "<section aria-labelledby=\"transitions\"><h2 id=\"transitions\">StateMachine transition admission evidence</h2><table><thead><tr><th>Step</th><th>Screen</th><th>Interaction</th><th>Operation</th><th>StateMachine</th><th>Domain state</th><th>Workflow state</th><th>UI state</th></tr></thead><tbody>" + rows + "</tbody></table></section>"
  }

  private def _diagnostics_html(values: Vector[ReviewDiagnostic]): String = {
    val content = if (values.isEmpty) "<p>No review diagnostics.</p>" else values.map(value => s"""<p class="diagnostic"><code>${_escape(value.code)}</code> <strong>${_escape(value.path)}</strong>: ${_escape(value.reason)}</p>""").mkString
    "<section aria-labelledby=\"diagnostics\"><h2 id=\"diagnostics\">Review diagnostics</h2>" + content + "</section>"
  }

  private def _reference_html(value: UseCaseReference): String = s"<code>${_escape(value.layer.id)}:${_escape(value.id)}</code>"
  private def _binding_html(value: ComponentBinding): String = s"<code>${_escape(value.component.canonicalIdentity)}#${_escape(value.exportId)}</code>"
  private def _definition(name: String, value: String): String = s"<dt>${_escape(name)}</dt><dd>$value</dd>"

  private def _input_error_value(path: String, reason: String): LogicalUiError = _output_error_value(path, reason, "LUI43_REVIEW_INPUT_INVALID")
  private def _candidate_input_error_value(path: String, reason: String): LogicalUiError = _output_error_value(path, reason, "LUI50_CANDIDATE_REVIEW_INPUT_INVALID")
  private def _output_error_value(path: String, reason: String, code: String = "LUI43_REVIEW_OUTPUT_INVALID"): LogicalUiError = LogicalUiError(code, path, reason)

  private def _empty(value: String): Boolean = value == null || value.isEmpty || value != value.trim

  private def _escape_attribute(value: String): String = _escape(value)

  private def _escape(value: String): String = Option(value).getOrElse("").flatMap {
    case '&' => "&amp;"
    case '<' => "&lt;"
    case '>' => "&gt;"
    case '"' => "&quot;"
    case '\'' => "&#39;"
    case character if character < ' ' => f"&#x${character.toInt}%02x;"
    case character => character.toString
  }

}
