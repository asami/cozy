package cozy.ui

import java.io.{IOException, StringWriter}
import java.nio.charset.StandardCharsets
import java.nio.file.{Files, LinkOption, Path}
import com.fasterxml.jackson.core.JsonFactory
import cozy.ui.CozyLogicalUi._
import cozy.ui.CozyLogicalUiReview.{CandidateReviewReceipt, ReviewDiagnostic}
import cozy.ui.CozyLogicalUiSemantics._

/*
 * @since   Oct. 8, 2026
 * @version Oct. 13, 2026
 * @author  ASAMI, Tomoharu
 */
private[cozy] object CozyLogicalUiCompactWeb {
  final case class OrderRecord(
    orderId: String, label: String, amount: String, domainState: DomainStateReference)

  final case class CompactFlow(
    step: UiUseCaseStep, listScreenId: String, listEntryInteractionId: String,
    navigationInteractionId: String, detailScreenId: String, selectionInteractionId: String,
    commandInteractionId: String, observationInteractionId: String)

  final case class CompactScenario(
    fixtureId: String, coverageId: String, flow: CompactFlow,
    initialOrder: OrderRecord, successfulOrder: OrderRecord, successUiState: UiInteractionState)

  final case class CompactWebReceipt(
    schema: String, version: Int, rendererProfile: String,
    candidateReview: CandidateReviewReceipt, scenario: CompactScenario,
    componentBindings: Vector[ComponentBinding], operationBinding: ComponentBinding,
    stateMachineBinding: ComponentBinding, powertypeBinding: ComponentBinding,
    powertypeVariantId: String, profile: String, detailPlacement: String,
    simulated: Boolean, outputs: Vector[String])

  final class CompactWebPreview private[CozyLogicalUiCompactWeb] (
    val html: String, val reviewHtml: String, val receipt: CompactWebReceipt,
    val receiptJson: String, val diagnostics: Vector[ReviewDiagnostic],
    private[CozyLogicalUiCompactWeb] val _candidate: LogicalUiCandidate,
    private[CozyLogicalUiCompactWeb] val _projection: LogicalUiProjection,
    private[CozyLogicalUiCompactWeb] val _semantics: LogicalUiSemanticProjection,
    private[CozyLogicalUiCompactWeb] val _scenario: CompactScenario
  ) {
    def htmlBytes: Vector[Byte] = html.getBytes(StandardCharsets.UTF_8).toVector
    def reviewHtmlBytes: Vector[Byte] = reviewHtml.getBytes(StandardCharsets.UTF_8).toVector
    def copy(
      html: String = this.html, reviewHtml: String = this.reviewHtml,
      receipt: CompactWebReceipt = this.receipt, receiptJson: String = this.receiptJson,
      diagnostics: Vector[ReviewDiagnostic] = this.diagnostics
    ): CompactWebPreview = new CompactWebPreview(
      html, reviewHtml, receipt, receiptJson, diagnostics, _candidate, _projection, _semantics, _scenario)
  }

  def render(
    candidate: LogicalUiCandidate, projection: LogicalUiProjection,
    semantics: LogicalUiSemanticProjection, scenario: CompactScenario
  ): Either[LogicalUiError, CompactWebPreview] = {
    for {
      review <- CozyLogicalUiReview.renderCandidate(candidate, projection, semantics)
      bindings <- _contract(candidate, projection, semantics, scenario)
    } yield {
      val receipt = CompactWebReceipt(
        "cozy.logical-ui-compact-web.v1", 1, "cozy.logical-ui-compact-web.renderer.v1",
        review.receipt, scenario, candidate.input.componentBindings,
        bindings._1.operation, bindings._1.stateMachine, bindings._2, bindings._3,
        "compact", "navigate-to-detail", true,
        Vector("index.html", "review.html", "compact-preview-receipt.json"))
      val json = _receipt_json(receipt)
      val reviewhtml = review.html.replace("</main>", _scenario_html(receipt) + "</main>")
      new CompactWebPreview(_app_html(receipt, json), reviewhtml, receipt, json,
        review.diagnostics, candidate, projection, semantics, scenario)
    }
  }

  def write(preview: CompactWebPreview, siteParent: Path, receiptParent: Path): Either[LogicalUiError, Unit] = {
    if (preview == null) Left(_error("preview", "a CompactWebPreview is required", "CONTRACT"))
    else for {
      _ <- _preflight(siteParent, receiptParent)
      _ <- _install(Vector(
        siteParent.resolve("index.html") -> preview.html,
        siteParent.resolve("review.html") -> preview.reviewHtml,
        receiptParent.resolve("compact-preview-receipt.json") -> preview.receiptJson))
    } yield ()
  }

  private def _contract(
    candidate: LogicalUiCandidate, projection: LogicalUiProjection,
    semantics: LogicalUiSemanticProjection, scenario: CompactScenario
  ): Either[LogicalUiError, (StateMachineTransitionAction, ComponentBinding, String)] = {
    _scenario_error(scenario) match {
      case Some(error) => Left(error)
      case None =>
        val flow = scenario.flow
        val step = flow.step
        if (step.uiUseCase != candidate.input.useCases.ui || step.path != NormalPath || !projection.input.steps.contains(step))
          return Left(_error("scenario.flow.step", "the candidate's existing normal UI step is required"))
        if (flow.listScreenId == flow.detailScreenId)
          return Left(_error("scenario.flow.detailScreenId", "list and detail screens must be distinct"))
        val screens = projection.input.screens.map(value => value.id -> value).toMap
        val interactions = Vector(
          (flow.listScreenId, flow.listEntryInteractionId, EntryInteraction, "listEntryInteractionId"),
          (flow.listScreenId, flow.navigationInteractionId, NavigationInteraction, "navigationInteractionId"),
          (flow.detailScreenId, flow.selectionInteractionId, SelectionInteraction, "selectionInteractionId"),
          (flow.detailScreenId, flow.commandInteractionId, InvocationInteraction, "commandInteractionId"),
          (flow.detailScreenId, flow.observationInteractionId, ObservationInteraction, "observationInteractionId"))
        Vector(flow.listScreenId -> "listScreenId", flow.detailScreenId -> "detailScreenId").foreach { case (id, path) =>
          if (!screens.contains(id)) return Left(_error("scenario.flow." + path, "screen must exist in the source projection"))
        }
        interactions.foreach { case (screenid, id, kind, path) =>
          val interaction = screens(screenid).interactions.find(_.id == id)
          if (!interaction.exists(_.kind == kind))
            return Left(_error("scenario.flow." + path, "interaction must exist with the required source kind"))
          if (!projection.input.mappings.contains(ScreenInteractionMapping(step.uiUseCase, step.stepId, screenid, id)))
            return Left(_error("scenario.flow." + path, "interaction must map to the exact declared step"))
        }
        val navigation = screens(flow.listScreenId).interactions.find(_.id == flow.navigationInteractionId).get
        if (!navigation.navigation.exists(_.targetScreenId == flow.detailScreenId))
          return Left(_error("scenario.flow.navigationInteractionId", "navigation must target the declared detail screen"))
        val semanticbyid = semantics.input.screens.map(value => value.screenId -> value).toMap
        Vector((flow.listScreenId, BrowsePurpose, CollectionDisplay, "listScreenId"),
          (flow.detailScreenId, ConfirmPurpose, DetailDisplay, "detailScreenId")).foreach { case (id, purpose, display, path) =>
          if (!semanticbyid.get(id).exists(value => value.purpose == purpose && value.regions.exists(_.display == display)))
            return Left(_error("scenario.flow." + path, "screen requires the declared typed semantic Purpose and Display"))
        }
        val selection = screens(flow.detailScreenId).interactions.find(_.id == flow.selectionInteractionId).get
        val powertypes = selection.componentUsages.filter(_.role == PowertypeRole)
        if (powertypes.size != 1)
          return Left(_error("scenario.flow.selectionInteractionId", "selection requires exactly one source Powertype usage"))
        val powertype = powertypes.head.binding
        val variant = semantics.input.componentBindings.find(value => value.role == PowertypeRole && value.binding == powertype)
          .flatMap(_.powertypeVariantId).filter(value => !_empty(value))
        if (variant.isEmpty)
          return Left(_error("scenario.flow.selectionInteractionId", "selection requires an explicit source semantic Powertype variant"))
        val command = screens(flow.detailScreenId).interactions.find(_.id == flow.commandInteractionId).get
        val actions = semantics.input.transitionActions.filter(value =>
          value.step == step && value.screenId == flow.detailScreenId && value.interactionId == flow.commandInteractionId)
        if (actions.size != 1)
          return Left(_error("scenario.flow.commandInteractionId", "command requires exactly one matching source transition action"))
        val action = actions.head
        if (!command.mutation.exists(_.operation == action.operation) ||
          !command.componentUsages.contains(ComponentUsage(OperationRole, action.operation)) ||
          !command.componentUsages.contains(ComponentUsage(StateMachineRole, action.stateMachine)))
          return Left(_error("scenario.flow.commandInteractionId", "command action must use its exact public Operation and StateMachine"))
        if (scenario.initialOrder.domainState != action.from)
          return Left(_error("scenario.initialOrder.domainState", "initial domain state must equal the source action's from reference"))
        if (scenario.successfulOrder.domainState != action.to)
          return Left(_error("scenario.successfulOrder.domainState", "result domain state must equal the source action's to reference"))
        if (scenario.successUiState != action.interactionState || scenario.successUiState != SucceededInteractionState)
          return Left(_error("scenario.successUiState", "success must equal the source action UI state and succeeded"))
        Right((action, powertype, variant.get))
    }
  }

  private def _scenario_error(scenario: CompactScenario): Option[LogicalUiError] = {
    if (scenario == null) return Some(_error("scenario", "a complete declared scenario is required"))
    if (scenario.flow == null) return Some(_error("scenario.flow", "a declared flow is required"))
    val flow = scenario.flow
    if (flow.step == null) return Some(_error("scenario.flow.step", "a declared UI step is required"))
    if (flow.step.uiUseCase == null) return Some(_error("scenario.flow.step.uiUseCase", "a UI use case is required"))
    if (flow.step.uiUseCase.layer == null) return Some(_error("scenario.flow.step.uiUseCase.layer", "a UI layer is required"))
    if (flow.step.path == null) return Some(_error("scenario.flow.step.path", "a normal UI path is required"))
    val identifiers = Vector(
      scenario.fixtureId -> "fixtureId", scenario.coverageId -> "coverageId",
      flow.step.stepId -> "flow.step.stepId", flow.step.uiUseCase.id -> "flow.step.uiUseCase.id",
      flow.listScreenId -> "flow.listScreenId", flow.detailScreenId -> "flow.detailScreenId",
      flow.listEntryInteractionId -> "flow.listEntryInteractionId", flow.navigationInteractionId -> "flow.navigationInteractionId",
      flow.selectionInteractionId -> "flow.selectionInteractionId", flow.commandInteractionId -> "flow.commandInteractionId",
      flow.observationInteractionId -> "flow.observationInteractionId")
    identifiers.foreach { case (text, path) =>
      if (_empty(text)) return Some(_error("scenario." + path, "identifier must be nonempty and trimmed"))
    }
    Vector(scenario.initialOrder -> "initialOrder", scenario.successfulOrder -> "successfulOrder").foreach { case (order, path) =>
      if (order == null) return Some(_error("scenario." + path, "an order record is required"))
      if (_empty(order.orderId)) return Some(_error("scenario." + path + ".orderId", "order identifier must be nonempty and trimmed"))
      Vector(order.label -> "label", order.amount -> "amount").foreach { case (text, field) =>
        if (text == null || text.isEmpty) return Some(_error("scenario." + path + "." + field, "opaque presentation text must be nonempty"))
      }
      if (order.domainState == null) return Some(_error("scenario." + path + ".domainState", "a domain reference is required"))
    }
    if (scenario.successUiState == null) return Some(_error("scenario.successUiState", "a distinct UI state is required"))
    val initial = scenario.initialOrder
    val successful = scenario.successfulOrder
    Vector((initial.orderId, successful.orderId, "orderId"), (initial.label, successful.label, "label"),
      (initial.amount, successful.amount, "amount")).foreach { case (from, to, field) =>
      if (from != to) return Some(_error("scenario.successfulOrder." + field, "the declared records must retain the same presentation value"))
    }
    None
  }

  private def _receipt_json(value: CompactWebReceipt): String = {
    val review = value.candidateReview
    _object(Vector(
      "schema" -> _string(value.schema), "version" -> value.version.toString,
      "rendererProfile" -> _string(value.rendererProfile),
      "candidateReview" -> _object(Vector("schema" -> _string(review.schema), "version" -> review.version.toString,
        "rendererProfile" -> _string(review.rendererProfile))),
      "scenario" -> _scenario_json(value.scenario),
      "componentBindings" -> value.componentBindings.map(_binding_json).mkString("[", ",", "]"),
      "operationBinding" -> _binding_json(value.operationBinding), "stateMachineBinding" -> _binding_json(value.stateMachineBinding),
      "powertypeBinding" -> _binding_json(value.powertypeBinding), "powertypeVariantId" -> _string(value.powertypeVariantId),
      "profile" -> _string(value.profile), "detailPlacement" -> _string(value.detailPlacement),
      "simulated" -> value.simulated.toString, "outputs" -> value.outputs.map(_string).mkString("[", ",", "]")))
  }

  private def _scenario_json(value: CompactScenario): String = {
    val flow = value.flow
    _object(Vector("fixtureId" -> _string(value.fixtureId), "coverageId" -> _string(value.coverageId),
      "flow" -> _object(Vector(
        "step" -> _object(Vector("uiUseCase" -> _object(Vector("layer" -> _string(flow.step.uiUseCase.layer.id),
          "id" -> _string(flow.step.uiUseCase.id))), "stepId" -> _string(flow.step.stepId), "path" -> _string(flow.step.path.id))),
        "listScreenId" -> _string(flow.listScreenId), "listEntryInteractionId" -> _string(flow.listEntryInteractionId),
        "navigationInteractionId" -> _string(flow.navigationInteractionId), "detailScreenId" -> _string(flow.detailScreenId),
        "selectionInteractionId" -> _string(flow.selectionInteractionId), "commandInteractionId" -> _string(flow.commandInteractionId),
        "observationInteractionId" -> _string(flow.observationInteractionId))),
      "initialOrder" -> _order_json(value.initialOrder), "successfulOrder" -> _order_json(value.successfulOrder),
      "successUiState" -> _string(value.successUiState.id)))
  }

  private def _order_json(value: OrderRecord): String = _object(Vector(
    "orderId" -> _string(value.orderId), "label" -> _string(value.label), "amount" -> _string(value.amount),
    "domainState" -> _object(Vector("stateMachineBinding" -> _binding_json(value.domainState.stateMachineBinding),
      "stateId" -> _string(value.domainState.stateId)))))

  private def _binding_json(value: ComponentBinding): String = _object(Vector(
    "component" -> _object(Vector("namespace" -> _string(value.component.namespace), "id" -> _string(value.component.id),
      "version" -> _string(value.component.version), "canonicalIdentity" -> _string(value.component.canonicalIdentity))),
    "exportId" -> _string(value.exportId)))

  private def _object(fields: Vector[(String, String)]): String =
    fields.map { case (name, value) => _string(name) + ":" + value }.mkString("{", ",", "}")

  private def _string(value: String): String = {
    val writer = new StringWriter()
    val generator = new JsonFactory().createGenerator(writer)
    try generator.writeString(value)
    finally generator.close()
    writer.toString
  }

  private def _embedded_json(value: String): String = value.flatMap {
    case '<' => "\\u003c"
    case '>' => "\\u003e"
    case '&' => "\\u0026"
    case '\u2028' => "\\u2028"
    case '\u2029' => "\\u2029"
    case character => character.toString
  }

  private def _binding_text(value: ComponentBinding): String = value.component.canonicalIdentity + "#" + value.exportId

  private def _source_attributes(interaction: String, step: UiUseCaseStep): String =
    s"""data-interaction-id="${_escape(interaction)}" data-ui-step-id="${_escape(step.stepId)}" """

  private def _app_html(value: CompactWebReceipt, json: String): String = {
    val scenario = value.scenario
    val flow = scenario.flow
    val initial = scenario.initialOrder
    val review = value.candidateReview
    s"""<!doctype html><html lang="en"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width, initial-scale=1"><link rel="icon" href="data:,"><title>Sales order candidate preview</title>
<style>*{box-sizing:border-box}body{margin:0;background:#f3f5f7;color:#172b3a;font:16px/1.5 system-ui,sans-serif}main{max-width:32rem;margin:auto;padding:1rem;overflow-wrap:anywhere}section{background:white;border:1px solid #c4ced6;border-radius:.75rem;padding:1rem;margin:1rem 0}h1{font-size:1.5rem}h2{font-size:1.25rem}button,select{display:block;width:100%;min-width:0;font:inherit;padding:.65rem;margin:.75rem 0;border:1px solid #718595;border-radius:.4rem}button{background:#183e58;color:white}button:disabled{background:#dce3e8;color:#4e616e}dt{font-weight:650}dd{margin:0 0 .6rem}output{display:block}[hidden]{display:none!important}.assurance{font-size:.9rem;color:#435966}a{color:#164e78}</style></head><body>
<main id="compact-preview" data-fixture-id="${_escape(scenario.fixtureId)}" data-coverage-id="${_escape(scenario.coverageId)}" data-profile="compact" data-detail-placement="navigate-to-detail" data-active-screen="${_escape(flow.listScreenId)}" data-selected-order="">
<h1>Sales order</h1><p class="assurance">Candidate preview · not accepted. Confirmation displays a declared simulated outcome.</p>
<section id="order-list-screen" data-screen-id="${_escape(flow.listScreenId)}" ${_source_attributes(flow.listEntryInteractionId, flow.step)} aria-labelledby="list-heading"><h2 id="list-heading">Orders</h2>
<dl><dt>Order</dt><dd id="list-order-id">${_escape(initial.orderId)}</dd><dt>Label</dt><dd id="list-order-label">${_escape(initial.label)}</dd><dt>Amount</dt><dd id="list-order-amount">${_escape(initial.amount)}</dd><dt>Domain state</dt><dd id="list-domain-state" data-domain-state="${_escape(initial.domainState.stateId)}">${_escape(initial.domainState.stateId)}</dd></dl>
<button id="select-order" type="button">Select order</button><output id="selected-order" aria-live="polite">No order selected</output><button id="open-detail" type="button" ${_source_attributes(flow.navigationInteractionId, flow.step)} disabled>Open detail</button></section>
<section id="order-detail-screen" data-screen-id="${_escape(flow.detailScreenId)}" aria-labelledby="detail-heading" hidden><h2 id="detail-heading">Confirm order</h2>
<dl><dt>Order</dt><dd id="detail-order-id">${_escape(initial.orderId)}</dd><dt>Label</dt><dd id="detail-order-label">${_escape(initial.label)}</dd><dt>Amount</dt><dd id="detail-order-amount">${_escape(initial.amount)}</dd><dt>Domain state</dt><dd><output id="domain-state" data-domain-state="${_escape(initial.domainState.stateId)}">${_escape(initial.domainState.stateId)}</output></dd><dt>UI state</dt><dd><output id="ui-state" data-ui-state="idle">idle</output></dd></dl>
<label for="confirmation-option">Confirmation option</label><select id="confirmation-option" ${_source_attributes(flow.selectionInteractionId, flow.step)} data-powertype-binding="${_escape(_binding_text(value.powertypeBinding))}"><option value="">Choose confirmation option</option><option value="${_escape(value.powertypeVariantId)}">${_escape(value.powertypeVariantId)}</option></select>
<button id="confirm-order" type="button" ${_source_attributes(flow.commandInteractionId, flow.step)} data-operation-binding="${_escape(_binding_text(value.operationBinding))}" data-state-machine-binding="${_escape(_binding_text(value.stateMachineBinding))}" disabled>Confirm order</button>
<output id="simulated-result" ${_source_attributes(flow.observationInteractionId, flow.step)} aria-live="polite" hidden>Simulated success</output><p class="assurance">This preview displays the supplied result. No order is submitted.</p></section><a href="review.html">Read-only structural review</a></main>
<script id="compact-data" type="application/json">${_embedded_json(json)}</script><script>${_client_script}</script></body></html>"""
  }

  private val _client_script = """'use strict';
(() => {
  const receipt = JSON.parse(document.getElementById('compact-data').textContent);
  const scenario = receipt.scenario;
  const root = document.getElementById('compact-preview');
  const get = id => document.getElementById(id);
  let selected = false;
  let completed = false;
  get('select-order').addEventListener('click', () => {
    selected = true;
    root.dataset.selectedOrder = scenario.initialOrder.orderId;
    get('selected-order').textContent = scenario.initialOrder.orderId + ' · ' + scenario.initialOrder.label + ' · ' + scenario.initialOrder.amount;
    get('open-detail').disabled = false;
  });
  get('open-detail').addEventListener('click', () => {
    if (!selected) return;
    get('order-list-screen').hidden = true;
    get('order-detail-screen').hidden = false;
    root.dataset.activeScreen = scenario.flow.detailScreenId;
    location.hash = encodeURIComponent(scenario.flow.detailScreenId);
  });
  get('confirmation-option').addEventListener('change', event => {
    get('confirm-order').disabled = completed || event.target.value !== receipt.powertypeVariantId;
  });
  get('confirm-order').addEventListener('click', () => {
    if (completed || get('confirmation-option').value !== receipt.powertypeVariantId) return;
    completed = true;
    const order = scenario.successfulOrder;
    get('detail-order-id').textContent = order.orderId;
    get('detail-order-label').textContent = order.label;
    get('detail-order-amount').textContent = order.amount;
    get('domain-state').textContent = order.domainState.stateId;
    get('domain-state').dataset.domainState = order.domainState.stateId;
    get('ui-state').textContent = scenario.successUiState;
    get('ui-state').dataset.uiState = scenario.successUiState;
    get('simulated-result').hidden = false;
    get('confirm-order').disabled = true;
  });
})();"""

  private def _scenario_html(value: CompactWebReceipt): String = {
    val scenario = value.scenario
    val flow = scenario.flow
    val fields = Vector(
      "fixture-id" -> scenario.fixtureId, "coverage-id" -> scenario.coverageId, "profile" -> value.profile,
      "detail-placement" -> value.detailPlacement, "ui-use-case" -> (flow.step.uiUseCase.layer.id + ":" + flow.step.uiUseCase.id),
      "ui-step" -> flow.step.stepId, "ui-path" -> flow.step.path.id,
      "list-screen" -> flow.listScreenId, "list-entry" -> flow.listEntryInteractionId,
      "navigation" -> flow.navigationInteractionId, "detail-screen" -> flow.detailScreenId,
      "selection" -> flow.selectionInteractionId, "command" -> flow.commandInteractionId,
      "observation" -> flow.observationInteractionId,
      "operation-binding" -> _binding_text(value.operationBinding),
      "state-machine-binding" -> _binding_text(value.stateMachineBinding),
      "powertype-binding" -> _binding_text(value.powertypeBinding), "powertype-variant" -> value.powertypeVariantId,
      "initial-order-id" -> scenario.initialOrder.orderId, "initial-label" -> scenario.initialOrder.label,
      "initial-amount" -> scenario.initialOrder.amount, "initial-domain-state" -> scenario.initialOrder.domainState.stateId,
      "result-order-id" -> scenario.successfulOrder.orderId, "result-label" -> scenario.successfulOrder.label,
      "result-amount" -> scenario.successfulOrder.amount, "result-domain-state" -> scenario.successfulOrder.domainState.stateId,
      "success-ui-state" -> scenario.successUiState.id)
    "<section id=\"compact-scenario\" aria-labelledby=\"compact-scenario-heading\"><h2 id=\"compact-scenario-heading\">Compact simulated scenario</h2>" +
      "<p>Declared simulated success; no Operation, StateMachine or Workflow is executed.</p><dl>" +
      fields.map { case (name, text) => s"""<dt>${_escape(name)}</dt><dd data-scenario-field="${_escape(name)}">${_escape(text)}</dd>""" }.mkString +
      "</dl></section>"
  }

  private def _preflight(siteparent: Path, receiptparent: Path): Either[LogicalUiError, Unit] = {
    try {
      Vector(siteparent -> "output.siteParent", receiptparent -> "output.receiptParent").foreach { case (parent, path) =>
        if (parent == null || !parent.isAbsolute || parent != parent.normalize())
          return Left(_error(path, "an existing canonical absolute directory is required", "OUTPUT"))
        val components = parent.iterator()
        var current = parent.getRoot
        while (components.hasNext) {
          val component = components.next()
          if (component.toString == "." || component.toString == "..")
            return Left(_error(path, "traversal components are forbidden", "OUTPUT"))
          current = current.resolve(component)
          if (Files.isSymbolicLink(current)) return Left(_error(path, "symbolic link components are forbidden", "OUTPUT"))
        }
        if (!Files.isDirectory(parent, LinkOption.NOFOLLOW_LINKS) || parent.toRealPath() != parent)
          return Left(_error(path, "an existing canonical directory is required", "OUTPUT"))
      }
      if (siteparent == receiptparent)
        return Left(_error("output.receiptParent", "site and receipt parents must be distinct", "OUTPUT"))
      Vector(siteparent.resolve("index.html"), siteparent.resolve("review.html"),
        receiptparent.resolve("compact-preview-receipt.json")).foreach { target =>
        if (Files.isSymbolicLink(target) ||
          (Files.exists(target, LinkOption.NOFOLLOW_LINKS) && !Files.isRegularFile(target, LinkOption.NOFOLLOW_LINKS)))
          return Left(_error("output." + target.getFileName.toString, "target must be absent or a regular file", "OUTPUT"))
      }
      Right(())
    } catch {
      case error: IOException => Left(_error("output", "destination preflight failed: " + error.getMessage, "OUTPUT"))
      case _: SecurityException => Left(_error("output", "destination preflight was denied", "OUTPUT"))
    }
  }

  private def _install(values: Vector[(Path, String)]): Either[LogicalUiError, Unit] = {
    try {
      values.foreach { case (target, text) => Files.write(target, text.getBytes(StandardCharsets.UTF_8)) }
      Right(())
    } catch {
      case error: IOException => Left(_error("output", "output write failed: " + error.getMessage, "OUTPUT"))
      case _: SecurityException => Left(_error("output", "output write denied", "OUTPUT"))
    }
  }

  private def _error(path: String, reason: String, category: String = "CONTRACT"): LogicalUiError =
    LogicalUiError("LUI50_WEB_" + category + "_INVALID", path, reason)

  private def _empty(value: String): Boolean = value == null || value.isEmpty || value != value.trim

  private def _escape(value: String): String = value.flatMap {
    case '&' => "&amp;"
    case '<' => "&lt;"
    case '>' => "&gt;"
    case '"' => "&quot;"
    case '\'' => "&#39;"
    case character if character < ' ' => f"&#x${character.toInt}%02x;"
    case character => character.toString
  }
}
