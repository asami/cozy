package cozy.ui

import java.io.IOException
import java.nio.charset.StandardCharsets
import java.nio.file.{Files, LinkOption, Path}
import cozy.ui.CozyLogicalUi._
import cozy.ui.CozyLogicalUiReadiness._
import cozy.ui.CozyLogicalUiReadinessReview._
import cozy.ui.CozyLogicalUiReview.ReviewDiagnostic
import play.api.libs.json._

/*
 * @since   Oct. 8, 2026
 * @version Oct. 13, 2026
 * @author  ASAMI, Tomoharu
 */
private[cozy] object CozyLogicalUiReadinessWeb {
  final case class WebReceipt(
    schema: String, version: Int, rendererProfile: String, reviewReceipt: ReviewReceipt,
    simulated: Boolean, outputs: Vector[String])

  final class ReadinessWebPreview private[CozyLogicalUiReadinessWeb] (
    val html: String, val reviewHtml: String, val compareHtml: String, val receipt: WebReceipt,
    val receiptJson: String, val reviewReceiptJson: String, val diagnostics: Vector[ReviewDiagnostic],
    private[CozyLogicalUiReadinessWeb] val _candidate: TargetReadyCandidate,
    private[CozyLogicalUiReadinessWeb] val _accepted: Option[AcceptedTargetReadyLogicalUi],
    private[CozyLogicalUiReadinessWeb] val _scenario: ReadinessScenario
  ) {
    def htmlBytes: Vector[Byte] = html.getBytes(StandardCharsets.UTF_8).toVector
    def reviewHtmlBytes: Vector[Byte] = reviewHtml.getBytes(StandardCharsets.UTF_8).toVector
    def compareHtmlBytes: Vector[Byte] = compareHtml.getBytes(StandardCharsets.UTF_8).toVector
    def receiptJsonBytes: Vector[Byte] = receiptJson.getBytes(StandardCharsets.UTF_8).toVector
    def reviewReceiptJsonBytes: Vector[Byte] = reviewReceiptJson.getBytes(StandardCharsets.UTF_8).toVector
    def copy(
      html: String = this.html, reviewHtml: String = this.reviewHtml, compareHtml: String = this.compareHtml,
      receipt: WebReceipt = this.receipt, receiptJson: String = this.receiptJson,
      reviewReceiptJson: String = this.reviewReceiptJson, diagnostics: Vector[ReviewDiagnostic] = this.diagnostics
    ): ReadinessWebPreview = new ReadinessWebPreview(html, reviewHtml, compareHtml, receipt, receiptJson,
      reviewReceiptJson, diagnostics, _candidate, _accepted, _scenario)
  }

  def renderCandidate(
    completeCandidate: TargetReadyCandidate, scenario: ReadinessScenario
  ): Either[LogicalUiError, ReadinessWebPreview] =
    _render(CozyLogicalUiReadinessReview.renderCandidate(completeCandidate, scenario), completeCandidate, None, scenario)

  def renderAccepted(
    accepted: AcceptedTargetReadyLogicalUi, scenario: ReadinessScenario
  ): Either[LogicalUiError, ReadinessWebPreview] =
    _render(CozyLogicalUiReadinessReview.renderAccepted(accepted, scenario),
      if (accepted == null) null else accepted.candidate, Option(accepted), scenario)

  def write(preview: ReadinessWebPreview, siteParent: Path, receiptParent: Path): Either[LogicalUiError, Unit] = {
    if (preview == null) Left(_error("preview", "Web preview required", "CONTRACT"))
    else for {
      _ <- _preflight(siteParent, receiptParent)
      _ <- _install(Vector(siteParent.resolve("index.html") -> preview.html,
        siteParent.resolve("review.html") -> preview.reviewHtml, siteParent.resolve("compare.html") -> preview.compareHtml,
        receiptParent.resolve("readiness-review-receipt.json") -> preview.reviewReceiptJson,
        receiptParent.resolve("readiness-web-receipt.json") -> preview.receiptJson))
    } yield ()
  }

  private def _render(
    result: Either[LogicalUiError, ReadinessReviewPreview], candidate: TargetReadyCandidate,
    accepted: Option[AcceptedTargetReadyLogicalUi], scenario: ReadinessScenario
  ): Either[LogicalUiError, ReadinessWebPreview] = result.left.map(error => _error(error.path, error.reason)).map { review =>
    val receipt = WebReceipt("cozy.logical-ui-target-ready-web.v1", 1,
      "cozy.logical-ui-target-ready-web.renderer.v1", review.receipt, true,
      Vector("index.html", "review.html", "compare.html", "readiness-review-receipt.json", "readiness-web-receipt.json"))
    val json = Json.stringify(Json.obj("schema" -> receipt.schema, "version" -> receipt.version,
      "rendererProfile" -> receipt.rendererProfile, "reviewReceipt" -> Json.parse(review.receiptJson),
      "simulated" -> receipt.simulated, "outputs" -> receipt.outputs))
    new ReadinessWebPreview(_app_html(candidate, review.receipt, json), review.html,
      _compare_html(review.receipt.binding), receipt, json, review.receiptJson, review.diagnostics, candidate, accepted, scenario)
  }

  private def _app_html(candidate: TargetReadyCandidate, receipt: ReviewReceipt, json: String): String = {
    val input = candidate.input
    val flow = receipt.scenario.normal.flow
    def _control_(role: ControlRole): ControlSemantics = input.controls.find(_.role == role).get
    def _text_(id: String): String = input.textSources.find(_.textId == id).get.defaultText
    def _label_(role: ControlRole): String = _escape(_text_(_control_(role).labelTextId))
    def _attributes_(role: ControlRole): String = {
      val control = _control_(role)
      val ref = control.control
      val steps = candidate.projection.input.mappings.filter(mapping => mapping.screenId == ref.screenId &&
        mapping.interactionId == ref.interactionId).map(mapping => Json.obj("uiUseCaseId" -> mapping.uiUseCase.id,
          "stepId" -> mapping.stepId))
      " data-source-control=\"" + _escape(ref.interactionId) + "\" data-screen-id=\"" + _escape(ref.screenId) +
        "\" data-interaction-id=\"" + _escape(ref.interactionId) + "\" data-control-role=\"" + role.id +
        "\" data-label-text-id=\"" + _escape(control.labelTextId) + "\" data-text-source=\"" +
        _escape(input.textSources.find(_.textId == control.labelTextId).get.sourceIdentity) +
        "\" data-source-steps=\"" + _escape(Json.stringify(JsArray(steps))) + "\"" +
        control.focusOrder.map(rank => " data-focus-order=\"" + rank + "\"").getOrElse("")
    }
    def _region_(screenid: String, role: RegionRole): String = {
      val region = input.profiles.head.regions.find(value => value.region.screenId == screenid && value.role == role).get.region
      " data-screen-id=\"" + _escape(region.screenId) + "\" data-region-id=\"" + _escape(region.regionId) + "\""
    }
    val field = input.fields.head
    val fieldjson = Json.parse(candidate.canonicalContent) \ "readiness" \ "fields"
    val binding = receipt.binding
    "<!doctype html><html lang=\"en\"><head><meta charset=\"utf-8\"><meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">" +
      "<title>Complete Logical UI simulation</title><style>" + _css + "</style></head><body>" +
      "<main id=\"readiness-preview\">" +
      "<h1>SalesOrder Logical UI simulation</h1><p id=\"assurance\">" +
      (if (binding.decision.isDefined) "Explicitly accepted complete input" else "Complete candidate; acceptance pending") +
      "; simulated review fixture</p><p><a href=\"review.html\">Structural review</a> · <a href=\"compare.html\">Compare window sizes</a></p>" +
      "<p>Window intent: <output id=\"window-profile\"></output></p><nav id=\"source-navigation\" aria-label=\"Source navigation\">" +
      "<button id=\"open-detail\" type=\"button\"" + _attributes_(NavigationControl) + ">" + _label_(NavigationControl) + "</button></nav>" +
      "<div id=\"source-screens\"><section id=\"order-list-screen\"" + _region_(flow.listScreenId, PrimaryRegion) + ">" +
      "<div id=\"list-entry\"" + _attributes_(EntryControl) + "><h2>" + _label_(EntryControl) + "</h2>" +
      "<p id=\"order-record\"><span id=\"order-label\"></span> · <span id=\"order-amount\"></span></p>" +
      "<p id=\"entry-empty\" hidden></p></div></section>" +
      "<section id=\"order-detail-screen\"" + _region_(flow.detailScreenId, PrimaryRegion) + "><h2>Order detail</h2>" +
      "<div id=\"detail-body\"" + _region_(flow.detailScreenId, SecondaryRegion) + ">" +
      "<label for=\"order-reference\">" + _escape(_text_(field.labelTextId)) + "</label>" +
      "<input id=\"order-reference\" type=\"text\" inputmode=\"" + field.inputIntent.id +
      "\" aria-required=\"" + field.required.toString + "\" aria-invalid=\"false\" aria-describedby=\"input-error\"" +
      _attributes_(InputControl) + " data-field-id=\"" + _escape(field.fieldId) +
      "\" data-value-binding=\"" + _escape(_binding_text(field.valueBinding)) +
      "\" data-datatype-binding=\"" + _escape(_binding_text(field.datatypeBinding)) +
      "\" data-field-declaration=\"" + _escape(Json.stringify(fieldjson.get)) + "\">" +
      "<p id=\"input-error\" role=\"alert\" hidden></p>" +
      "<label for=\"confirmation-option\">" + _label_(SelectionControl) + "</label><select id=\"confirmation-option\"" +
      _attributes_(SelectionControl) + " data-powertype-binding=\"" + _escape(_binding_text(receipt.sourceFlow.powertypeBinding)) +
      "\" data-powertype-variant-id=\"" + _escape(receipt.sourceFlow.powertypeVariantId) + "\"></select>" +
      "<button id=\"confirm-order\" type=\"button\"" + _attributes_(CommandControl) +
      " data-operation-binding=\"" + _escape(_binding_text(receipt.sourceFlow.operationBinding)) +
      "\" data-state-machine-binding=\"" + _escape(_binding_text(receipt.sourceFlow.stateMachineBinding)) + "\">" + _label_(CommandControl) + "</button>" +
      "<div id=\"observation\"" + _attributes_(ObservationControl) + "><h3>" + _label_(ObservationControl) + "</h3>" +
      "<dl><dt>Domain state</dt><dd id=\"domain-state\"></dd><dt>UI state</dt><dd id=\"ui-state\"></dd>" +
      "<dt>Workflow source evidence</dt><dd id=\"workflow-evidence\"></dd></dl><p id=\"observed-result\" hidden></p></div>" +
      "</div></section></div><section id=\"feedback-panel\" aria-live=\"polite\" aria-busy=\"false\"><h2>" + _label_(FeedbackControl) +
      "</h2><p id=\"feedback\"" + _attributes_(FeedbackControl) + " hidden></p><div id=\"recovery-controls\"></div></section>" +
      "<section id=\"fixture-controls\" data-review-fixture=\"true\"><h2>Review fixture controls</h2>" +
      "<p>These actions inspect declared simulated outcomes. They do not execute source operations or accept input.</p>" +
      "<label for=\"fixture-outcome\">Next declared simulated outcome</label><select id=\"fixture-outcome\"></select>" +
      "<button id=\"complete-confirmation\" type=\"button\">Complete simulated confirmation</button>" +
      "<button id=\"reset-fixture\" type=\"button\">Reset review fixture</button>" +
      "<button id=\"replay-fixture\" type=\"button\">Replay loading and normal</button></section>" +
      "<details><summary>Complete source provenance</summary><pre id=\"source-provenance\"></pre></details>" +
      "<details><summary>Actual runtime diagnostics</summary><pre id=\"preview-diagnostics\" aria-live=\"polite\"></pre></details></main>" +
      "<script id=\"readiness-data\" type=\"application/json\">" + _embedded_json(json) + "</script><script>" + _script + "</script></body></html>"
  }

  private val _css = """
*{box-sizing:border-box}body{margin:0;font-family:system-ui,sans-serif;line-height:1.5;color:#162434;background:#f4f6f8}
main{max-width:100%;padding:1rem;margin:auto}h1{font-size:1.4rem}section,nav,details{min-width:0;margin:.7rem 0;padding:.8rem;background:white;border:1px solid #b8c4d0;border-radius:.4rem}
#source-screens{display:grid;grid-template-columns:minmax(0,1fr);gap:1rem}#readiness-preview[data-window-class="expanded"] #source-screens{grid-template-columns:minmax(0,1fr) minmax(0,2fr)}
button,input,select{font:inherit;max-width:100%;min-width:0;margin:.3rem 0;padding:.5rem}button{white-space:normal;overflow-wrap:anywhere;cursor:pointer}label{display:block;font-weight:600}
input,select{display:block;width:100%}button:focus-visible,input:focus-visible,select:focus-visible,a:focus-visible{outline:3px solid #075eaa;outline-offset:3px}
p,dd,pre,output,span{overflow-wrap:anywhere;white-space:pre-wrap}pre{font-size:.85rem}dd{margin-left:1rem}#input-error{color:#962127}[hidden]{display:none!important}button:disabled{cursor:default;opacity:.65}
"""

  // Fixed interpreter: no supplied source text is executable interpolation.
  private val _script = """
'use strict';
(() => {
  const payload = JSON.parse(document.getElementById('readiness-data').textContent);
  const review = payload.reviewReceipt, normal = review.scenario.normal, flow = normal.flow;
  const ready = review.sourceInputs.readinessContent.readiness;
  const field = ready.fields[0], sourceflow = review.sourceFlow;
  const byId = id => document.getElementById(id);
  const root = byId('readiness-preview'), input = byId('order-reference'), choice = byId('confirmation-option');
  const menu = byId('fixture-outcome'), command = byId('confirm-order'), feedback = byId('feedback');
  const text = id => ready.textSources.find(item => item.textId === id).defaultText;
  const category = id => ready.outcomes.find(item => item.feedbackState === id);
  const control = role => ready.controls.find(item => item.role === role);
  const variant = sourceflow.powertypeVariantId;
  let selected = false, detail = false, active = null, pending = false, completed = false, error = null;
  let renderedRecoveryOutcome = null;
  let domain = normal.initialOrder.domainState, ui = 'idle', profile;
  const option = (select, value, label) => {
    const node = document.createElement('option'); node.value = value; node.textContent = label; select.appendChild(node);
  };
  option(choice, '', 'Choose the declared option'); option(choice, variant, variant);
  ready.outcomes.forEach(item => option(menu, item.outcomeId, item.outcomeId + ': ' + text(item.messageTextId)));
  byId('source-provenance').textContent = JSON.stringify(review, null, 2);
  const bind = (node, ref, step) => {
    node.dataset.screenId = ref.screenId; node.dataset.interactionId = ref.interactionId;
    if (step) { node.dataset.stepId = step.stepId; node.dataset.uiUseCaseId = step.uiUseCase.id; node.dataset.path = step.path; }
  };
  const validate = () => {
    const rules = field.localRules.slice().sort((a,b) => (a.ruleKind === 'required-text' ? 0 : 1) - (b.ruleKind === 'required-text' ? 0 : 1));
    return rules.find(rule => rule.ruleKind === 'required-text' ? input.value.trim().length === 0 : !/^[A-Z0-9-]+$/.test(input.value)) || null;
  };
  const showError = rule => {
    error = rule; input.setAttribute('aria-invalid', String(Boolean(rule)));
    const node = byId('input-error'); node.hidden = !rule; node.textContent = rule ? text(rule.messageTextId) : '';
    ['constraintId','detailCode','feedbackId','messageTextId'].forEach(key => delete node.dataset[key]);
    if (rule) { node.dataset.constraintId = rule.constraint.constraintId; node.dataset.detailCode = rule.constraint.detailCode;
      node.dataset.feedbackId = rule.feedbackId; node.dataset.messageTextId = rule.messageTextId; }
  };
  const visible = node => !node.hidden && node.getClientRects().length > 0 && getComputedStyle(node).visibility !== 'hidden';
  const diagnostics = () => {
    const findings = [], add = (code,path,reason) => findings.push({code:'LUI501_WEB_' + code,path,reason});
    const width = document.documentElement.clientWidth;
    if (document.documentElement.scrollWidth > width + 1 || root.scrollWidth > root.clientWidth + 1)
      add('OVERFLOW','viewport','actual content exceeds the viewport width');
    const reachable = node => { const rect = node.getBoundingClientRect(); return visible(node) && rect.width > 0 && rect.height > 0 && rect.left >= -1 && rect.right <= width + 1; };
    const nav = byId('open-detail');
    if ((profile.persistentNavigation || !detail) && (!visible(nav) || nav.disabled))
      add('NAVIGATION_MISSING','controls.navigation','declared navigation is unreachable');
    const interactive = Array.from(root.querySelectorAll('[data-focus-order]'));
    const roles = ['navigation','input','selection','command'];
    const ranks = interactive.map(node => Number(node.dataset.focusOrder));
    if (interactive.length !== 4 || ranks.some((rank,index) => rank !== index + 1 || interactive[index].dataset.controlRole !== roles[index]) ||
      interactive.some(node => node.tabIndex > 0))
      add('FOCUS_ORDER_INCONSISTENT','controls.focusOrder','natural source focus order differs from declared ranks');
    ready.controls.forEach(item => {
      const node = root.querySelector('[data-control-role="' + item.role + '"]');
      if (!node) add('ACTION_MISSING','controls.' + item.role,'declared source control is absent');
      else if (visible(node) && !reachable(node)) add('REQUIRED_CONTENT_UNREACHABLE','controls.' + item.role,'actual source control has no usable visible width');
    });
    if (selected && detail && !pending && !completed && choice.value === variant && command.disabled)
      add('ACTION_MISSING','controls.command','ready source invocation is disabled');
    if (active && (!reachable(feedback) || feedback.textContent !== text(active.messageTextId)))
      add('FEEDBACK_MISSING','outcomes.' + active.outcomeId,'active declared feedback is unreachable or mismatched');
    if (error && !reachable(byId('input-error')))
      add('REQUIRED_CONTENT_UNREACHABLE','field.localRules','active local error is unreachable');
    profile.regions.forEach(item => {
      const node = Array.from(root.querySelectorAll('[data-region-id]')).find(region => region.dataset.screenId === item.region.screenId && region.dataset.regionId === item.region.regionId);
      if (!node || (item.persistent && !visible(node))) add('REQUIRED_CONTENT_UNREACHABLE','profiles.' + profile.windowClass + '.regions','required source region is unavailable');
    });
    const expected = window.innerWidth < 600 ? 'compact' : window.innerWidth < 1024 ? 'medium' : 'expanded';
    const expanded = expected === 'expanded', placement = expanded ? 'update-detail-region' : 'navigate-to-detail';
    if (profile.windowClass !== expected || profile.detailPlacement !== placement || profile.persistentNavigation !== (expected !== 'compact') ||
      visible(byId('order-list-screen')) !== (expanded || !detail) || visible(byId('order-detail-screen')) !== (expanded || detail) ||
      visible(byId('source-navigation')) !== (expected !== 'compact' || !detail))
      add('RESPONSIVE_INTENT_INCONSISTENT','profiles.' + expected,'actual target layout differs from admitted intent');
    findings.sort((a,b) => a.code.localeCompare(b.code) || a.path.localeCompare(b.path) || a.reason.localeCompare(b.reason));
    byId('preview-diagnostics').textContent = JSON.stringify(findings, null, 2);
  };
  const render = () => {
    const name = window.innerWidth < 600 ? 'compact' : window.innerWidth < 1024 ? 'medium' : 'expanded';
    profile = ready.profiles.find(item => item.windowClass === name);
    const focused = document.activeElement;
    root.dataset.windowClass = name; root.dataset.detailPlacement = profile.detailPlacement;
    root.dataset.persistentNavigation = String(profile.persistentNavigation);
    root.dataset.activeScreen = detail ? flow.detailScreenId : flow.listScreenId;
    root.dataset.selectedOrderId = selected ? normal.initialOrder.orderId : '';
    root.dataset.selectedResourceId = selected ? normal.initialOrder.orderId : '';
    root.dataset.outcomeId = active ? active.outcomeId : ''; root.dataset.pending = String(pending);
    byId('window-profile').textContent = name;
    byId('source-navigation').hidden = name === 'compact' && detail;
    byId('order-list-screen').hidden = name !== 'expanded' && detail;
    byId('order-detail-screen').hidden = name !== 'expanded' && !detail;
    profile.regions.forEach(item => {
      const node = Array.from(root.querySelectorAll('[data-region-id]')).find(region => region.dataset.screenId === item.region.screenId && region.dataset.regionId === item.region.regionId);
      node.dataset.regionPersistent = String(item.persistent); node.dataset.regionRole = item.role;
    });
    const empty = active && active.feedbackState === 'empty';
    byId('order-record').hidden = Boolean(empty); byId('entry-empty').hidden = !empty;
    byId('entry-empty').textContent = empty ? text(active.messageTextId) : '';
    byId('order-label').textContent = normal.initialOrder.label; byId('order-amount').textContent = normal.initialOrder.amount;
    byId('list-entry').dataset.orderId = normal.initialOrder.orderId;
    input.disabled = !selected || pending || completed; choice.disabled = !selected || pending || completed;
    command.disabled = !selected || choice.value !== variant || pending || completed;
    byId('complete-confirmation').disabled = !pending || menu.value === category('loading').outcomeId;
    byId('domain-state').textContent = domain.stateId; byId('ui-state').textContent = ui;
    byId('workflow-evidence').textContent = active && active.workflowState ? active.workflowState.workflowId + ': ' + active.workflowState.stateId : 'No active Workflow evidence';
    byId('observed-result').hidden = !completed;
    byId('observed-result').textContent = completed ? normal.successfulOrder.label + ' · ' + normal.successfulOrder.amount + ' · Simulated success' : '';
    feedback.hidden = !active; feedback.textContent = active ? text(active.messageTextId) : '';
    byId('feedback-panel').setAttribute('aria-busy', String(pending));
    if (active) { bind(feedback, control('feedback').control, active.step); feedback.dataset.outcomeId = active.outcomeId;
      feedback.dataset.feedbackState = active.feedbackState; feedback.dataset.messageTextId = active.messageTextId;
      feedback.dataset.outcomeFeedbackScreenId = active.feedbackControl.screenId;
      feedback.dataset.outcomeFeedbackInteractionId = active.feedbackControl.interactionId;
      if (empty) bind(byId('entry-empty'), active.feedbackControl, active.step); }
    else { bind(feedback, control('feedback').control); ['stepId','uiUseCaseId','path','outcomeId','feedbackState','messageTextId',
      'outcomeFeedbackScreenId','outcomeFeedbackInteractionId'].forEach(key => delete feedback.dataset[key]); }
    if (active !== renderedRecoveryOutcome) {
      const recoveries = byId('recovery-controls'); recoveries.replaceChildren();
      if (active) active.recoveries.forEach(recovery => {
        const button = document.createElement('button'); button.type = 'button'; button.textContent = recovery.kind;
        button.dataset.recoveryKind = recovery.kind; bind(button, recovery.target, recovery.step);
        button.addEventListener('click', () => recover(recovery)); recoveries.appendChild(button);
      });
      renderedRecoveryOutcome = active;
    }
    showError(error);
    if (focused && focused.dataset && focused.dataset.focusOrder && (!visible(focused) || focused.disabled)) {
      const counterpart = !input.disabled && visible(input) ? input : byId('open-detail');
      if (visible(counterpart) && !counterpart.disabled) counterpart.focus();
    }
    diagnostics();
  };
  const applyOutcome = outcome => {
    active = outcome; domain = outcome.domainState; ui = outcome.uiState;
    pending = outcome.feedbackState === 'loading'; completed = outcome.feedbackState === 'normal';
    if (outcome.feedbackState === 'empty') { detail = false; selected = false; }
    else { detail = true; selected = true; }
    render();
  };
  const begin = () => {
    if (!selected || choice.value !== variant || pending || completed) return;
    const invalid = validate(); showError(invalid);
    if (invalid) { applyOutcome(category('validation-failed')); return; }
    applyOutcome(category('loading'));
  };
  const complete = () => {
    if (!pending) return;
    const outcome = ready.outcomes.find(item => item.outcomeId === menu.value);
    if (outcome.feedbackState !== 'loading') applyOutcome(outcome);
  };
  const reset = () => {
    selected = false; detail = false; active = null; pending = false; completed = false;
    domain = normal.initialOrder.domainState; ui = 'idle'; input.value = field.initialValue;
    choice.value = ''; menu.value = category('normal').outcomeId; showError(null);
    const nav = byId('open-detail'); ['stepId','uiUseCaseId','path','selectedOrderId'].forEach(key => delete nav.dataset[key]);
    render();
  };
  const open = () => {
    selected = true; detail = true;
    const nav = byId('open-detail'); bind(nav, control('navigation').control, flow.step);
    nav.dataset.selectedOrderId = normal.initialOrder.orderId;
    if (active && active.feedbackState === 'empty') { active = null; ui = 'idle'; }
    render(); if (!input.disabled) input.focus();
  };
  const recover = recovery => {
    if (recovery.kind === 'edit-input') {
      active = null; pending = false; completed = false; ui = 'idle'; detail = true;
      menu.value = category('normal').outcomeId; showError(null); render(); if (!input.disabled) input.focus();
    } else if (recovery.kind === 'return-to-entry') {
      detail = false; active = null; pending = false; completed = false; ui = 'idle'; showError(null); render(); byId('open-detail').focus();
    } else if (recovery.target.screenId === flow.listScreenId && recovery.target.interactionId === flow.listEntryInteractionId) {
      reset(); byId('open-detail').focus();
    } else {
      active = null; pending = false; completed = false; ui = 'idle'; menu.value = category('normal').outcomeId; begin();
    }
  };
  input.addEventListener('blur', () => { if (!input.disabled) { showError(validate()); render(); } });
  input.addEventListener('input', () => { if (error) showError(validate()); render(); });
  choice.addEventListener('change', render); byId('open-detail').addEventListener('click', open);
  command.addEventListener('click', begin); byId('complete-confirmation').addEventListener('click', complete);
  byId('reset-fixture').addEventListener('click', reset);
  byId('replay-fixture').addEventListener('click', () => { reset(); open(); choice.value = variant; begin(); if (!error) complete(); });
  menu.addEventListener('change', () => {
    const outcome = ready.outcomes.find(item => item.outcomeId === menu.value);
    if (['empty','loading','validation-failed'].includes(outcome.feedbackState)) { showError(null); applyOutcome(outcome); }
    else if (!pending) { active = null; completed = false; ui = 'idle'; domain = normal.initialOrder.domainState; showError(null); render(); }
    else render();
  });
  window.addEventListener('resize', render);
  reset();
})();
"""

  private def _compare_html(binding: CompleteBinding): String = {
    val frames = Vector(("Compact", 390, 844), ("Medium", 834, 1112), ("Expanded", 1440, 900)).map { case (name, width, height) =>
      "<section><h2>" + name + " · " + width + " × " + height + "</h2><iframe title=\"" + name +
        " complete Logical UI simulation\" src=\"index.html\" width=\"" + width + "\" height=\"" + height + "\"></iframe></section>"
    }.mkString
    val provenance = Vector("Decision ID" -> binding.decision.map(_.decisionId).getOrElse(""),
      "Reviewer" -> binding.decision.map(_.reviewerId).getOrElse(""))
      .map { case (name, value) => _escape(name) + ":" + (if (value.isEmpty) "" else " " + _escape(value)) }.mkString("\n")
    "<!doctype html><html lang=\"en\"><head><meta charset=\"utf-8\"><meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">" +
      "<title>Compare one complete Logical UI</title><style>body{font-family:system-ui,sans-serif;margin:1rem}#frames{display:flex;gap:1rem;overflow-x:auto}section{flex:none}iframe{border:1px solid #b8c4d0}pre{white-space:pre-wrap;overflow-wrap:anywhere}</style></head><body>" +
      "<h1>One app at three viewport sizes</h1><p>" +
      (if (binding.decision.isDefined) "Explicit accepted complete input" else "Complete candidate; acceptance pending") +
      "; all frames use the same declared list fixture.</p><a href=\"review.html\">Structural review</a>" +
      "<details><summary>Exact complete provenance</summary><pre>" + provenance +
      "</pre></details><div id=\"frames\">" + frames + "</div></body></html>"
  }

  private def _preflight(siteparent: Path, receiptparent: Path): Either[LogicalUiError, Unit] = {
    try {
      Vector(siteparent -> "output.siteParent", receiptparent -> "output.receiptParent").foreach { case (parent, path) =>
        if (parent == null || !parent.isAbsolute || parent != parent.normalize())
          return Left(_error(path, "existing canonical absolute directory required", "OUTPUT"))
        val components = parent.iterator()
        var current = parent.getRoot
        while (components.hasNext) {
          val component = components.next()
          if (component.toString == "." || component.toString == "..") return Left(_error(path, "traversal forbidden", "OUTPUT"))
          current = current.resolve(component)
          if (Files.isSymbolicLink(current)) return Left(_error(path, "symbolic link component forbidden", "OUTPUT"))
        }
        if (!Files.isDirectory(parent, LinkOption.NOFOLLOW_LINKS) || parent.toRealPath() != parent)
          return Left(_error(path, "existing canonical nonsymlink directory required", "OUTPUT"))
      }
      if (siteparent == receiptparent) return Left(_error("output.receiptParent", "distinct parents required", "OUTPUT"))
      val targets = Vector(siteparent.resolve("index.html"), siteparent.resolve("review.html"), siteparent.resolve("compare.html"),
        receiptparent.resolve("readiness-review-receipt.json"), receiptparent.resolve("readiness-web-receipt.json"))
      targets.foreach { target =>
        if (Files.isSymbolicLink(target) || (Files.exists(target, LinkOption.NOFOLLOW_LINKS) && !Files.isRegularFile(target, LinkOption.NOFOLLOW_LINKS)))
          return Left(_error("output." + target.getFileName.toString, "target must be absent or regular nonsymlink file", "OUTPUT"))
      }
      Right(())
    } catch {
      case error: IOException => Left(_error("output", "preflight failed: " + error.getMessage, "OUTPUT"))
      case _: SecurityException => Left(_error("output", "preflight denied", "OUTPUT"))
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

  private def _binding_text(binding: ComponentBinding): String = binding.component.canonicalIdentity + "#" + binding.exportId
  private def _error(path: String, reason: String, category: String = "CONTRACT"): LogicalUiError =
    LogicalUiError("LUI501_WEB_" + category + "_INVALID", path, reason)
  private def _embedded_json(value: String): String = value.flatMap {
    case '<' => "\\u003c"
    case '>' => "\\u003e"
    case '&' => "\\u0026"
    case '\u2028' => "\\u2028"
    case '\u2029' => "\\u2029"
    case character => character.toString
  }
  private def _escape(value: String): String = value.flatMap {
    case '&' => "&amp;"
    case '<' => "&lt;"
    case '>' => "&gt;"
    case '"' => "&quot;"
    case '\'' => "&#39;"
    case character => character.toString
  }
}
