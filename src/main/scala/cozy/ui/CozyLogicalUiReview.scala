package cozy.ui

import java.io.IOException
import java.nio.charset.StandardCharsets
import java.nio.file.{Files, LinkOption, Path, StandardCopyOption, StandardOpenOption}
import java.security.MessageDigest

import cozy.ui.CozyLogicalUi._
import cozy.ui.CozyLogicalUiSemantics._

/*
 * @since   Sep. 1, 2026
 * @version Sep. 1, 2026
 * @author  ASAMI, Tomoharu
 */
private[cozy] object CozyLogicalUiReview {
  final case class ReviewDiagnostic(code: String, path: String, reason: String)

  final case class ReviewReceipt(
    schema: String,
    version: Int,
    rendererProfile: String,
    acceptedIdentity: String,
    candidateIdentity: String,
    consumedInputIdentity: String,
    useCaseIdentity: String,
    catalogIdentity: String,
    projectionIdentity: String,
    semanticProjectionIdentity: String,
    htmlIdentity: String,
    identity: String
  ) {
    def receiptIdentity: String = identity
    def htmlSha256: String = htmlIdentity
  }

  final class LogicalUiReview private[CozyLogicalUiReview] (
    val html: String,
    val receipt: ReviewReceipt,
    val diagnostics: Vector[ReviewDiagnostic],
    private[CozyLogicalUiReview] val _accepted: AcceptedLogicalUi,
    private[CozyLogicalUiReview] val _projection: LogicalUiProjection,
    private[CozyLogicalUiReview] val _semantics: LogicalUiSemanticProjection
  ) {
    def htmlBytes: Vector[Byte] = html.getBytes(StandardCharsets.UTF_8).toVector
    def htmlIdentity: String = receipt.htmlIdentity
    def receiptIdentity: String = receipt.identity
    def copy(
      html: String = this.html,
      receipt: ReviewReceipt = this.receipt,
      diagnostics: Vector[ReviewDiagnostic] = this.diagnostics
    ): LogicalUiReview = new LogicalUiReview(html, receipt, diagnostics, _accepted, _projection, _semantics)
  }

  private val _schema = "cozy.logical-ui-review.v1"
  private val _version = 1
  private val _renderer_profile = "cozy.logical-ui-review.renderer.v1"

  def schema: String = _schema
  def version: Int = _version
  def rendererProfile: String = _renderer_profile

  def render(
    accepted: AcceptedLogicalUi,
    projection: LogicalUiProjection,
    semantics: LogicalUiSemanticProjection
  ): Either[LogicalUiError, LogicalUiReview] = {
    _identity_error(accepted, projection, semantics) match {
      case Some(error) => Left(error)
      case None =>
        val diagnostics = _diagnostics(projection, semantics)
        val html = _html(accepted, projection, semantics, diagnostics)
        val receipt = _receipt(accepted, projection, semantics, _sha256_bytes(html))
        Right(new LogicalUiReview(html, receipt, diagnostics, accepted, projection, semantics))
    }
  }

  def review(
    accepted: AcceptedLogicalUi,
    projection: LogicalUiProjection,
    semantics: LogicalUiSemanticProjection
  ): Either[LogicalUiError, LogicalUiReview] = render(accepted, projection, semantics)

  def verifyCurrent(
    accepted: AcceptedLogicalUi,
    projection: LogicalUiProjection,
    semantics: LogicalUiSemanticProjection,
    reviewValue: LogicalUiReview
  ): Either[LogicalUiError, Unit] = {
    if (reviewValue == null)
      Left(_currentness_error("review", "a LogicalUiReview is required"))
    else
      render(accepted, projection, semantics) match {
        case Left(_) => Left(_currentness_error("review.inputs", "review input identities are no longer valid"))
        case Right(current) =>
          if (current.html != reviewValue.html)
            Left(_currentness_error("review.html", "review HTML bytes are stale"))
          else if (current.receipt != reviewValue.receipt)
            Left(_currentness_error("review.receipt", "review receipt fields or identity are stale"))
          else if (current.diagnostics != reviewValue.diagnostics)
            Left(_currentness_error("review.diagnostics", "review diagnostics are stale"))
          else Right(())
      }
  }

  def verifyCurrent(
    reviewValue: LogicalUiReview,
    accepted: AcceptedLogicalUi,
    projection: LogicalUiProjection,
    semantics: LogicalUiSemanticProjection
  ): Either[LogicalUiError, Unit] = verifyCurrent(accepted, projection, semantics, reviewValue)

  def write(
    reviewValue: LogicalUiReview,
    parent: Path,
    target: Path
  ): Either[LogicalUiError, Unit] = {
    if (reviewValue == null)
      Left(_currentness_error("review", "a LogicalUiReview is required"))
    else {
      verifyCurrent(reviewValue._accepted, reviewValue._projection, reviewValue._semantics, reviewValue) match {
        case Left(error) => Left(error)
        case Right(_) => _write_current(reviewValue, parent, target)
      }
    }
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

  private def _write_current(
    reviewvalue: LogicalUiReview,
    parent: Path,
    target: Path
  ): Either[LogicalUiError, Unit] = {
    _output_error(parent, target) match {
      case Some(error) => Left(error)
      case None =>
        var temporary: Option[Path] = None
        try {
          val created = Files.createTempFile(parent, ".cozy-logical-ui-review-", ".tmp")
          temporary = Some(created)
          Files.write(
            created,
            reviewvalue.html.getBytes(StandardCharsets.UTF_8),
            StandardOpenOption.WRITE,
            StandardOpenOption.TRUNCATE_EXISTING
          )
          Files.move(created, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
          temporary = None
          Right(())
        } catch {
          case error: IOException =>
            temporary.foreach(_delete_temporary)
            Left(_output_error_value("output", s"atomic HTML write failed: ${error.getMessage}"))
          case error: SecurityException =>
            temporary.foreach(_delete_temporary)
            Left(_output_error_value("output", "atomic HTML write was denied"))
          case error: RuntimeException =>
            temporary.foreach(_delete_temporary)
            Left(_output_error_value("output", s"atomic HTML write failed: ${error.getMessage}"))
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

  private def _delete_temporary(path: Path): Unit = {
    try Files.deleteIfExists(path) catch {
      case _: IOException => ()
      case _: SecurityException => ()
    }
  }

  private def _identity_error(
    accepted: AcceptedLogicalUi,
    projection: LogicalUiProjection,
    semantics: LogicalUiSemanticProjection
  ): Option[LogicalUiError] = {
    if (accepted == null)
      Some(_identity_error_value("accepted", "accepted Logical UI authority is required"))
    else if (projection == null)
      Some(_identity_error_value("projection", "Logical UI screen projection is required"))
    else if (semantics == null)
      Some(_identity_error_value("semantics", "Logical UI semantic projection is required"))
    else if (accepted.candidate == null || projection.candidate == null)
      Some(_identity_error_value("candidate", "accepted and projected candidates are required"))
    else if (accepted.candidate.identity != projection.candidate.identity ||
      accepted.candidate.inputIdentity != projection.candidate.inputIdentity ||
      accepted.candidate.canonicalContent != projection.candidate.canonicalContent)
      Some(_identity_error_value("candidate", "accepted candidate and projection candidate identities must be exact"))
    else if (accepted.decision == null || accepted.decision.candidateIdentity != accepted.candidate.identity)
      Some(_identity_error_value("accepted.decision", "acceptance decision must bind the exact candidate identity"))
    else if (_empty(accepted.identity) || _empty(accepted.candidate.identity) || _empty(accepted.candidate.inputIdentity))
      Some(_identity_error_value("accepted.identity", "accepted, candidate, and consumed-input identities must be present"))
    else if (semantics.projectionIdentity != projection.identity)
      Some(_identity_error_value("semantics.projectionIdentity", "semantic projection must bind the exact screen projection identity"))
    else if (_empty(projection.identity) || _empty(semantics.identity))
      Some(_identity_error_value("projection.identity", "projection and semantic identities must be present"))
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
    accepted: AcceptedLogicalUi,
    projection: LogicalUiProjection,
    semantics: LogicalUiSemanticProjection,
    diagnostics: Vector[ReviewDiagnostic]
  ): String = {
    val candidate = accepted.candidate
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
      _overview_html(accepted, projection, semantics),
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

  private def _overview_html(
    accepted: AcceptedLogicalUi,
    projection: LogicalUiProjection,
    semantics: LogicalUiSemanticProjection
  ): String = {
    val candidate = accepted.candidate
    val usecaseidentity = _use_case_identity(candidate.input.useCases)
    val catalogidentity = _catalog_identity(projection.input.catalog)
    s"""<section aria-labelledby="overview"><h2 id="overview">Overview</h2><dl>""" +
      _definition("Schema", _schema) +
      _definition("Renderer profile", _renderer_profile) +
      _definition("Accepted identity", _escape(accepted.identity)) +
      _definition("Candidate identity", _escape(candidate.identity)) +
      _definition("Consumed input identity", _escape(candidate.inputIdentity)) +
      _definition("UseCase identity", _escape(usecaseidentity)) +
      _definition("Catalog identity", _escape(catalogidentity)) +
      _definition("Projection identity", _escape(projection.identity)) +
      _definition("Semantic projection identity", _escape(semantics.identity)) +
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

  private def _receipt(
    accepted: AcceptedLogicalUi,
    projection: LogicalUiProjection,
    semantics: LogicalUiSemanticProjection,
    htmlidentity: String
  ): ReviewReceipt = {
    val candidate = accepted.candidate
    val usecaseidentity = _use_case_identity(candidate.input.useCases)
    val catalogidentity = _catalog_identity(projection.input.catalog)
    val values = ReviewReceipt(_schema, _version, _renderer_profile, accepted.identity, candidate.identity, candidate.inputIdentity, usecaseidentity, catalogidentity, projection.identity, semantics.identity, htmlidentity, "")
    values.copy(identity = _sha256(_receipt_content(values)))
  }

  private def _receipt_content(value: ReviewReceipt): String =
    Vector(
      "schema=" + value.schema,
      "version=" + value.version,
      "rendererProfile=" + value.rendererProfile,
      "acceptedIdentity=" + value.acceptedIdentity,
      "candidateIdentity=" + value.candidateIdentity,
      "consumedInputIdentity=" + value.consumedInputIdentity,
      "useCaseIdentity=" + value.useCaseIdentity,
      "catalogIdentity=" + value.catalogIdentity,
      "projectionIdentity=" + value.projectionIdentity,
      "semanticProjectionIdentity=" + value.semanticProjectionIdentity,
      "htmlIdentity=" + value.htmlIdentity
    ).mkString("{", ",", "}")

  private def _use_case_identity(value: UseCaseLayers): String = _sha256(_use_case_json(value))

  private def _catalog_identity(values: Vector[UseCaseLayers]): String = _sha256(_json_array(values.sortBy(_use_case_key).map(_use_case_json)))

  private def _use_case_key(value: UseCaseLayers): (String, String, String) = (value.business.id, value.system.id, value.ui.id)

  private def _use_case_json(value: UseCaseLayers): String = {
    val realizations = value.realizations.sortBy(item => (item.source.layer.id, item.source.id, item.target.layer.id, item.target.id)).map { item =>
      _json_object(Vector(
        "source" -> _reference_json(item.source),
        "target" -> _reference_json(item.target)
      ))
    }
    _json_object(Vector(
      "business" -> _reference_json(value.business),
      "system" -> _reference_json(value.system),
      "ui" -> _reference_json(value.ui),
      "realizations" -> _json_array(realizations)
    ))
  }

  private def _reference_html(value: UseCaseReference): String = s"<code>${_escape(value.layer.id)}:${_escape(value.id)}</code>"
  private def _reference_json(value: UseCaseReference): String = _json_object(Vector(
    "layer" -> _json_string(value.layer.id),
    "id" -> _json_string(value.id)
  ))
  private def _json_object(fields: Vector[(String, String)]): String = fields.map { case (name, value) => s"${_json_string(name)}:$value" }.mkString("{", ",", "}")
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
  private def _binding_html(value: ComponentBinding): String = s"<code>${_escape(value.component.canonicalIdentity)}#${_escape(value.exportId)}</code>"
  private def _definition(name: String, value: String): String = s"<dt>${_escape(name)}</dt><dd>$value</dd>"

  private def _currentness_error(path: String, reason: String): LogicalUiError = _output_error_value(path, reason, "LUI43_REVIEW_CURRENTNESS_INVALID")
  private def _identity_error_value(path: String, reason: String): LogicalUiError = _output_error_value(path, reason, "LUI43_REVIEW_IDENTITY_MISMATCH")
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

  private def _sha256(value: String): String = _sha256_bytes(value).toString

  private def _sha256_bytes(value: String): String =
    "sha256:" + MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)).map(byte => f"${byte & 0xff}%02x").mkString
}
