#!/usr/bin/env node
/*
 * @since   Oct. 8, 2026
 * @version Oct. 8, 2026
 * @author  ASAMI, Tomoharu
 */
import assert from 'node:assert/strict';
import fs from 'node:fs/promises';
import path from 'node:path';
import os from 'node:os';
import http from 'node:http';
import { createRequire } from 'node:module';

const flags = ['--site', '--receipt', '--evidence', '--playwright', '--browser'];
const viewports = { compact: { width: 390, height: 844 }, medium: { width: 834, height: 1112 },
  expanded: { width: 1440, height: 900 } };
const outcomeIds = ['conflict', 'empty', 'loading', 'normal', 'operation-failed', 'unavailable', 'validation-failed'];
const screenshots = Object.keys(viewports).flatMap(name => outcomeIds.map(id => `${name}-${id}.png`))
  .concat(['structural-review.png', 'responsive-comparison.png']);
const outputNames = [...screenshots, 'browser-observations.json', 'server-process.json', 'browser-result.json'];
const startedAt = new Date().toISOString();
const errors = [], requests = [], cases = [], observations = [], capturedScreenshots = [], writtenOutputs = [];
let options, source, review, ready, normal, flow, field, sourceFlow, server, context, browser, profile;
let baseUrl, browserVersion, playwrightVersion, startupPromise, cleanupPromise;
let evidenceReady = false, readiness = false, clientSuccess = false, cleanupSuccess = false, interrupted = false;
let actionContext = 'input admission';

function parseArgs(argv) {
  assert.equal(argv.length, 10, 'Exactly five flag/value pairs required');
  const result = {};
  for (let index = 0; index < argv.length; index += 2) {
    const flag = argv[index], value = argv[index + 1];
    assert(flags.includes(flag) && !Object.hasOwn(result, flag), `Unknown or duplicate flag: ${flag}`);
    assert(value && !value.startsWith('--') && path.isAbsolute(value), `Absolute value required: ${flag}`);
    assert(!value.split(path.sep).some(part => part === '.' || part === '..'), `Traversal forbidden: ${flag}`);
    assert.equal(path.normalize(value), value, `Canonical path required: ${flag}`);
    result[flag] = value;
  }
  flags.forEach(flag => assert(Object.hasOwn(result, flag), `Missing flag: ${flag}`));
  return result;
}

async function checkComponents(target, allowMissing = false) {
  let current = path.parse(target).root;
  for (const component of target.slice(current.length).split(path.sep).filter(Boolean)) {
    current = path.join(current, component);
    try { assert(!(await fs.lstat(current)).isSymbolicLink(), `Symlink forbidden: ${current}`); }
    catch (error) { if (!allowMissing || error.code !== 'ENOENT') throw error; }
  }
}

async function regularFile(target) {
  await checkComponents(target);
  assert((await fs.lstat(target)).isFile(), `Regular file required: ${target}`);
  assert.equal(await fs.realpath(target), target, 'Canonical file required');
}

async function directory(target) {
  await checkComponents(target);
  assert((await fs.lstat(target)).isDirectory(), `Existing directory required: ${target}`);
  assert.equal(await fs.realpath(target), target, 'Canonical directory required');
}

function within(parent, target) {
  const relative = path.relative(parent, target);
  return relative === '' || (!relative.startsWith(`..${path.sep}`) && relative !== '..' && !path.isAbsolute(relative));
}

const bindingText = binding => `${binding.component.canonicalIdentity ||
  `${binding.component.namespace}.${binding.component.id}@${binding.component.version}`}#${binding.exportId}`;
const text = id => { const item = ready.textSources.find(value => value.textId === id); assert(item, id); return item.defaultText; };
const outcome = id => { const item = ready.outcomes.find(value => value.outcomeId === id); assert(item, id); return item; };
const controlIds = { entry: 'list-entry', navigation: 'open-detail', input: 'order-reference', selection: 'confirmation-option',
  command: 'confirm-order', observation: 'observation', feedback: 'feedback' };

function admitReceipt() {
  assert.equal(source.schema, 'cozy.logical-ui-target-ready-web.v1');
  assert.equal(source.version, 1);
  assert.equal(source.rendererProfile, 'cozy.logical-ui-target-ready-web.renderer.v1');
  assert.equal(source.simulated, true);
  assert.deepEqual(source.outputs, ['index.html', 'review.html', 'compare.html', 'readiness-review-receipt.json', 'readiness-web-receipt.json']);
  review = source.reviewReceipt;
  assert.equal(review.schema, 'cozy.logical-ui-target-ready-review.v1');
  assert.equal(review.version, 1);
  assert.equal(review.rendererProfile, 'cozy.logical-ui-target-ready-review.renderer.v1');
  const expectedBinding = {
    candidateIdentity: 'sha256:dcb64b218d5921c65be0f1be7b1483565446dfd9fc3dbefd215c202e83f813b1',
    consumedInputIdentity: 'sha256:dd1f3b249795e3857bdcb19a72abc2cc205edee8e4d736f8f2db42a2dd08c0a1',
    kernelCandidateIdentity: 'sha256:3e5a37efbcdf29e7e950118bbf5763f4e2bf6b76c8d80011a5896877af3d7c47',
    kernelInputIdentity: 'sha256:9199b0a67e6581c3a2f24a8ce24675499226f3699a21a9fc772156fc01a121f0',
    projectionIdentity: 'sha256:2a47d6264607e6514687e0f7a15abb7a2b9385101c019b09458b8ce26c2a9d10',
    semanticProjectionIdentity: 'sha256:99cfa256b7dd2402907e3859605dc17f25a8ef00870e2319de04f7971642dcc0',
    acceptedIdentity: null, decisionIdentity: null, decision: null
  };
  assert.deepEqual(review.binding, expectedBinding, 'Exact frozen complete candidate binding');
  const schemas = { kernelContent: 'cozy.logical-ui.v1', projectionContent: 'cozy.usecase-screen-projection.v1',
    semanticContent: 'cozy.logical-ui-semantics.v1', readinessContent: 'cozy.logical-ui-target-ready.v1' };
  assert.deepEqual(Object.keys(review.sourceInputs).sort(), Object.keys(schemas).sort());
  for (const [key, schema] of Object.entries(schemas)) {
    assert.equal(review.sourceInputs[key].schema, schema);
    assert.equal(review.sourceInputs[key].version, 1);
    assert(Object.keys(review.sourceInputs[key]).length > 2, `Full source input required: ${key}`);
  }
  const inputs = review.sourceInputs;
  assert.equal(inputs.projectionContent.candidateIdentity, review.binding.kernelCandidateIdentity);
  assert.equal(inputs.semanticContent.projectionIdentity, review.binding.projectionIdentity);
  for (const key of ['kernelCandidateIdentity', 'kernelInputIdentity', 'projectionIdentity', 'semanticProjectionIdentity'])
    assert.equal(inputs.readinessContent[key], review.binding[key]);
  ready = inputs.readinessContent.readiness;
  assert.equal(ready.semanticProjectionIdentity, review.binding.semanticProjectionIdentity);
  assert.deepEqual(ready.profiles.map(item => item.windowClass).sort(), Object.keys(viewports).sort());
  assert.deepEqual(ready.outcomes.map(item => item.outcomeId).sort(), outcomeIds);
  assert.equal(ready.fields.length, 1);
  field = ready.fields[0];
  assert.equal(field.fieldId, 'order-reference');
  assert.deepEqual(field.localRules.map(rule => rule.ruleKind).sort(), ['ascii-identifier', 'required-text']);
  assert.equal(ready.controls.length, 7);
  normal = review.scenario.normal; flow = normal.flow; sourceFlow = review.sourceFlow;
  assert.equal(normal.fixtureId, 'sales-order-target-ready-v1');
  assert.equal(normal.coverageId, 'P501-COMPACT-NORMAL-001');
  assert.equal(flow.step.path, 'normal');
  assert.deepEqual(normal.initialOrder.domainState, sourceFlow.from);
  assert.deepEqual(normal.successfulOrder.domainState, sourceFlow.to);
  assert.equal(normal.successUiState, sourceFlow.successUiState);
  assert.deepEqual(review.diagnostics, []);
  const expectedCases = Object.keys(viewports).flatMap(windowClass => outcomeIds.map(outcomeId => ({
    coverageId: `P501-${windowClass.toUpperCase()}-${outcomeId === 'validation-failed' ? 'VALIDATION' : outcomeId.toUpperCase()}-001`,
    windowClass, outcomeId
  }))).sort((a, b) => a.coverageId.localeCompare(b.coverageId));
  assert.deepEqual([...review.scenario.coverage].sort((a, b) => a.coverageId.localeCompare(b.coverageId)), expectedCases);
  for (const item of ready.outcomes) {
    assert(inputs.projectionContent.mappings.some(mapping => mapping.screenId === item.trigger.screenId &&
      mapping.interactionId === item.trigger.interactionId && mapping.stepId === item.step.stepId &&
      mapping.uiUseCase.id === item.step.uiUseCase.id), `Outcome source mapping: ${item.outcomeId}`);
    for (const recovery of item.recoveries) {
      assert(['edit-input', 'retry-interaction', 'return-to-entry'].includes(recovery.kind));
      assert(inputs.projectionContent.mappings.some(mapping => mapping.screenId === recovery.target.screenId &&
        mapping.interactionId === recovery.target.interactionId && mapping.stepId === recovery.step.stepId &&
        mapping.uiUseCase.id === recovery.step.uiUseCase.id), `Recovery mapping: ${item.outcomeId}/${recovery.kind}`);
    }
  }
}

async function prepareInputs() {
  await directory(options['--site']); await regularFile(options['--receipt']);
  await regularFile(options['--browser']); await directory(options['--playwright']);
  await regularFile(path.join(options['--playwright'], 'package.json'));
  await directory(options['--evidence']);
  const supervisorFiles = ['start-selection.json', 'stderr.log', 'stdout.log'];
  assert.deepEqual((await fs.readdir(options['--evidence'])).sort(), supervisorFiles,
    'Only selected supervisor start/log files may precede child execution; no replay or overwrite');
  for (const name of supervisorFiles) await regularFile(path.join(options['--evidence'], name));
  for (const input of [options['--site'], options['--receipt'], options['--playwright'], options['--browser']]) {
    assert(!within(input, options['--evidence']) && !within(options['--evidence'], input), 'Source/output overlap forbidden');
  }
  assert(!within(options['--site'], options['--receipt']), 'Receipt outside served site required');
  for (const name of outputNames) {
    const target = path.join(options['--evidence'], name);
    assert.equal(path.dirname(target), options['--evidence']);
    await checkComponents(target, true);
    await assert.rejects(fs.lstat(target), { code: 'ENOENT' });
  }
  evidenceReady = true;
  source = JSON.parse(await fs.readFile(options['--receipt'], 'utf8'));
  admitReceipt();
  const reviewPath = path.join(path.dirname(options['--receipt']), 'readiness-review-receipt.json');
  await regularFile(reviewPath);
  assert.deepEqual(JSON.parse(await fs.readFile(reviewPath, 'utf8')), review);
  const files = {};
  for (const name of ['index.html', 'review.html', 'compare.html']) {
    const target = path.join(options['--site'], name);
    await regularFile(target); files[`/${name}`] = await fs.readFile(target);
  }
  playwrightVersion = JSON.parse(await fs.readFile(path.join(options['--playwright'], 'package.json'), 'utf8')).version;
  return files;
}

async function writeJson(name, value) {
  assert(outputNames.includes(name), 'Undeclared output forbidden');
  const target = path.join(options['--evidence'], name);
  await checkComponents(target, true);
  await fs.writeFile(target, JSON.stringify(value, null, 2) + '\n', { flag: 'wx' });
  writtenOutputs.push(name);
}

async function startServer(files) {
  server = http.createServer((request, response) => {
    if (request.method !== 'GET') { response.writeHead(405, { Allow: 'GET' }); response.end(); }
    else if (Object.hasOwn(files, request.url)) {
      response.writeHead(200, { 'Content-Type': 'text/html; charset=utf-8', 'Cache-Control': 'no-store' });
      response.end(files[request.url]);
    } else { response.writeHead(404); response.end(); }
  });
  await new Promise((resolve, reject) => { server.once('error', reject); server.listen(0, '127.0.0.1', resolve); });
  baseUrl = `http://127.0.0.1:${server.address().port}`;
  await writeJson('server-process.json', { schema: 'cozy.logical-ui-target-ready-server.v1', pid: process.pid,
    startedAt, baseUrl, runDirectory: options['--evidence'], paths: Object.keys(files) });
  for (const name of Object.keys(files)) {
    const response = await fetch(baseUrl + name, { signal: AbortSignal.timeout(10000) });
    assert.equal(response.status, 200);
    const actual = await response.text();
    assert.equal(actual, files[name].toString('utf8'), `Actual HTTP source: ${name}`);
    assert(actual.includes(review.binding.candidateIdentity) && actual.includes(review.binding.consumedInputIdentity));
    if (name !== '/compare.html') assert(actual.includes(normal.fixtureId));
    else assert(actual.includes('all frames use the same declared list fixture') &&
      (actual.match(/src="index.html"/g) || []).length === 3);
  }
  readiness = true;
}

async function launchBrowser() {
  const require = createRequire(path.join(options['--playwright'], 'package.json'));
  const { chromium } = require(options['--playwright']);
  const temporaryRoot = await fs.realpath(os.tmpdir());
  await directory(temporaryRoot);
  profile = await fs.mkdtemp(path.join(temporaryRoot, 'cozy-readiness-chrome-'));
  context = await chromium.launchPersistentContext(profile, { executablePath: options['--browser'], headless: true,
    viewport: viewports.compact, timeout: 20000, serviceWorkers: 'block' });
  browser = context.browser(); assert(browser, 'Invocation-owned Chrome required'); browserVersion = browser.version();
  context.setDefaultTimeout(10000); context.setDefaultNavigationTimeout(15000);
  await context.route('**/*', async route => {
    const request = route.request(), url = new URL(request.url());
    const allowed = url.origin === baseUrl && request.method() === 'GET' &&
      ['/index.html', '/review.html', '/compare.html'].includes(url.pathname) && !url.search;
    requests.push({ origin: url.origin === baseUrl ? 'owned-loopback' : 'external', path: url.pathname,
      method: request.method(), allowed });
    if (!allowed) { errors.push({ type: 'network', context: actionContext, message: 'Undeclared or mutating browser request rejected' }); await route.abort(); }
    else await route.continue();
  });
  await context.routeWebSocket('**/*', async socket => {
    // Do not connect the routed socket to a server: this fixture admits HTML GETs only.
    errors.push({ type: 'network', context: actionContext, message: 'WebSocket request rejected before server connection' });
    await socket.close();
  });
  context.on('page', page => {
    page.on('pageerror', error => errors.push({ type: 'pageerror', context: actionContext, message: error.message }));
    page.on('console', message => { if (message.type() === 'error') errors.push({ type: 'console', context: actionContext, message: message.text() }); });
  });
  return context.newPage();
}

function assertActive() {
  assert(!interrupted, 'Run interrupted');
  assert.equal(errors.length, 0, 'Browser errors or rejected requests fail the run');
}

// Evaluations only read public DOM observables. Source interpreter functions are never invoked.
async function snapshot(target, name) {
  assertActive();
  return target.evaluate(stateName => {
    const byId = id => document.getElementById(id);
    const visible = node => Boolean(node && node.getClientRects().length && getComputedStyle(node).visibility !== 'hidden');
    const inspect = node => {
      const rect = node.getBoundingClientRect(), style = getComputedStyle(node);
      return { id: node.id, tag: node.tagName, text: node.textContent, value: 'value' in node ? node.value : null,
        visible: visible(node), disabled: Boolean(node.disabled), tabIndex: node.tabIndex,
        focused: document.activeElement === node, connected: node.isConnected,
        data: { ...node.dataset }, attributes: Object.fromEntries([...node.attributes].map(item => [item.name, item.value])),
        labels: node.labels ? [...node.labels].map(label => label.textContent) : [],
        rectangle: { x: rect.x, y: rect.y, width: rect.width, height: rect.height, right: rect.right, bottom: rect.bottom },
        scrollWidth: node.scrollWidth, clientWidth: node.clientWidth,
        focusVisible: node.matches(':focus-visible'), outlineStyle: style.outlineStyle, outlineWidth: style.outlineWidth };
    };
    const ids = ['readiness-preview', 'source-navigation', 'source-screens', 'order-list-screen', 'order-detail-screen',
      'detail-body', 'list-entry', 'order-record', 'order-label', 'order-amount', 'entry-empty', 'open-detail',
      'order-reference', 'input-error', 'confirmation-option', 'confirm-order', 'observation', 'domain-state', 'ui-state',
      'workflow-evidence', 'observed-result', 'feedback-panel', 'feedback', 'fixture-outcome', 'complete-confirmation'];
    const nodes = Object.fromEntries(ids.map(id => [id, inspect(byId(id))]));
    const sourceFocus = [...document.querySelectorAll('[data-focus-order]')].map(inspect);
    return { name: stateName, viewport: { width: innerWidth, height: innerHeight }, nodes,
      regions: [...document.querySelectorAll('[data-region-id]')].map(inspect),
      sourceControls: [...document.querySelectorAll('[data-control-role]')].map(inspect),
      sourceFocus, reachableSourceFocus: sourceFocus.filter(node => node.visible && !node.disabled && node.tabIndex >= 0).map(node => node.id),
      recoveries: [...byId('recovery-controls').querySelectorAll('button')].map(inspect),
      activeElement: document.activeElement.id, diagnostics: JSON.parse(byId('preview-diagnostics').textContent),
      documentWidth: document.documentElement.clientWidth, documentScrollWidth: document.documentElement.scrollWidth,
      provenance: JSON.parse(byId('source-provenance').textContent) };
  }, name);
}

function assertRef(data, ref, step) {
  assert.equal(data.screenId, ref.screenId); assert.equal(data.interactionId, ref.interactionId);
  if (step) { assert.equal(data.stepId, step.stepId); assert.equal(data.uiUseCaseId, step.uiUseCase.id); assert.equal(data.path, step.path); }
}

function assertUsable(node, actual) {
  assert(node.visible && node.rectangle.width > 0 && node.rectangle.height > 0, `${actual.name}: usable ${node.id}`);
  assert(node.rectangle.x >= -1 && node.rectangle.right <= actual.documentWidth + 1, `${actual.name}: horizontal reachability ${node.id}`);
  assert(node.scrollWidth <= node.clientWidth + 1, `${actual.name}: content overflow ${node.id}`);
}

function assertSource(actual) {
  const nodes = actual.nodes, root = nodes['readiness-preview'].data;
  for (const [key, value] of Object.entries(review.binding)) {
    if (key === 'decision') continue;
    assert.equal(root[key], value ?? '', `DOM complete binding: ${key}`);
  }
  assert.deepEqual(actual.provenance, review);
  const name = actual.viewport.width < 600 ? 'compact' : actual.viewport.width < 1024 ? 'medium' : 'expanded';
  const intent = ready.profiles.find(item => item.windowClass === name);
  assert.equal(root.windowClass, name); assert.equal(root.detailPlacement, intent.detailPlacement);
  assert.equal(root.persistentNavigation, String(intent.persistentNavigation));
  const detail = root.activeScreen === flow.detailScreenId;
  assert([flow.listScreenId, flow.detailScreenId].includes(root.activeScreen));
  assert.equal(nodes['source-navigation'].visible, intent.persistentNavigation || !detail);
  assert.equal(nodes['order-list-screen'].visible, name === 'expanded' || !detail);
  assert.equal(nodes['order-detail-screen'].visible, name === 'expanded' || detail);
  assert.equal(root.selectedOrderId, root.selectedResourceId);
  assert(['', normal.initialOrder.orderId].includes(root.selectedOrderId));
  assert.equal(nodes['order-label'].text, normal.initialOrder.label);
  assert.equal(nodes['order-amount'].text, normal.initialOrder.amount);
  assert.equal(nodes['list-entry'].data.orderId, normal.initialOrder.orderId);
  for (const item of ready.controls) {
    const node = nodes[controlIds[item.role]];
    assert(node, `Declared control ${item.role}`); assertRef(node.data, item.control);
    assert.equal(node.data.controlRole, item.role); assert.equal(node.data.sourceControl, item.control.interactionId);
    assert.equal(node.data.labelTextId, item.labelTextId);
    assert.equal(node.data.textSource, ready.textSources.find(value => value.textId === item.labelTextId).sourceIdentity);
    const expectedSteps = review.sourceInputs.projectionContent.mappings.filter(mapping =>
      mapping.screenId === item.control.screenId && mapping.interactionId === item.control.interactionId)
      .map(mapping => ({ uiUseCaseId: mapping.uiUseCase.id, stepId: mapping.stepId }));
    assert.deepEqual(JSON.parse(node.data.sourceSteps), expectedSteps);
    if (item.focusOrder !== null) assert.equal(node.data.focusOrder, String(item.focusOrder));
    if (['input', 'selection'].includes(item.role)) assert.deepEqual(node.labels, [text(item.labelTextId)]);
    else if (['navigation', 'command'].includes(item.role)) assert.equal(node.text, text(item.labelTextId));
    else if (['entry', 'observation'].includes(item.role)) assert(node.text.includes(text(item.labelTextId)));
    else assert(nodes['feedback-panel'].text.includes(text(item.labelTextId)));
    if (node.visible) assertUsable(node, actual);
  }
  assert.deepEqual(actual.sourceFocus.map(node => Number(node.data.focusOrder)), [1, 2, 3, 4]);
  assert(actual.sourceFocus.every(node => node.tabIndex <= 0), 'No positive tabindex');
  const input = nodes['order-reference'];
  assert.equal(input.attributes.type, 'text'); assert.equal(input.attributes.inputmode, field.inputIntent);
  assert.equal(input.attributes['aria-required'], String(field.required));
  assert.equal(input.attributes['aria-describedby'], 'input-error');
  assert.equal(input.data.fieldId, field.fieldId);
  assert.equal(input.data.valueBinding, bindingText(field.valueBinding));
  assert.equal(input.data.datatypeBinding, bindingText(field.datatypeBinding));
  assert.deepEqual(JSON.parse(input.data.fieldDeclaration), ready.fields);
  assert.equal(nodes['input-error'].attributes.role, 'alert');
  assert.equal(nodes['feedback-panel'].attributes['aria-live'], 'polite');
  assert.equal(nodes['confirmation-option'].data.powertypeBinding, bindingText(sourceFlow.powertypeBinding));
  assert.equal(nodes['confirmation-option'].data.powertypeVariantId, sourceFlow.powertypeVariantId);
  assert.equal(nodes['confirm-order'].data.operationBinding, bindingText(sourceFlow.operationBinding));
  assert.equal(nodes['confirm-order'].data.stateMachineBinding, bindingText(sourceFlow.stateMachineBinding));
  for (const item of intent.regions) {
    const region = actual.regions.find(node => node.data.screenId === item.region.screenId && node.data.regionId === item.region.regionId);
    assert(region, 'Required region exists'); assert.equal(region.data.regionPersistent, String(item.persistent));
    assert.equal(region.data.regionRole, item.role);
    const expectedVisible = item.region.screenId === flow.listScreenId ? name === 'expanded' || !detail : name === 'expanded' || detail;
    assert.equal(region.visible, expectedVisible); if (region.visible) { assertUsable(region, actual); assert(region.text.trim()); }
  }
  assert(actual.documentScrollWidth <= actual.documentWidth + 1, `${actual.name}: document overflow`);
  assertUsable(nodes['readiness-preview'], actual);
  if (actual.diagnostics.length) {
    for (const diagnostic of actual.diagnostics) {
      assert(/^LUI501_WEB_[A-Z_]+$/.test(diagnostic.code));
      assert(typeof diagnostic.path === 'string' && diagnostic.path && typeof diagnostic.reason === 'string' && diagnostic.reason);
    }
  }
  assert.deepEqual(actual.diagnostics, [], `${actual.name}: actual preview diagnostics ${JSON.stringify(actual.diagnostics)}`);
}

function assertOutcome(actual, item) {
  const nodes = actual.nodes, root = nodes['readiness-preview'].data, feedback = nodes.feedback;
  assert.equal(root.outcomeId, item.outcomeId); assert.equal(root.pending, String(item.feedbackState === 'loading'));
  assert.equal(nodes['domain-state'].text, item.domainState.stateId); assert.equal(nodes['ui-state'].text, item.uiState);
  assert.equal(nodes['workflow-evidence'].text, item.workflowState ?
    `${item.workflowState.workflowId}: ${item.workflowState.stateId}` : 'No active Workflow evidence');
  assertUsable(feedback, actual); assert.equal(feedback.text, text(item.messageTextId));
  assertRef(feedback.data, ready.controls.find(value => value.role === 'feedback').control, item.step);
  assert.equal(feedback.data.outcomeId, item.outcomeId); assert.equal(feedback.data.feedbackState, item.feedbackState);
  assert.equal(feedback.data.messageTextId, item.messageTextId);
  assert.equal(feedback.data.outcomeFeedbackScreenId, item.feedbackControl.screenId);
  assert.equal(feedback.data.outcomeFeedbackInteractionId, item.feedbackControl.interactionId);
  assert.equal(nodes['feedback-panel'].attributes['aria-busy'], String(item.feedbackState === 'loading'));
  assert.equal(actual.recoveries.length, item.recoveries.length);
  item.recoveries.forEach((recovery, index) => {
    const button = actual.recoveries[index]; assertUsable(button, actual); assert(!button.disabled);
    assert.equal(button.data.recoveryKind, recovery.kind); assert.equal(button.text, recovery.kind);
    assertRef(button.data, recovery.target, recovery.step);
  });
  if (item.feedbackState === 'empty') {
    assert.equal(root.activeScreen, flow.listScreenId); assert.equal(root.selectedOrderId, '');
    assert(!nodes['order-record'].visible && nodes['entry-empty'].visible);
    assert.equal(nodes['entry-empty'].text, text(item.messageTextId)); assertRef(nodes['entry-empty'].data, item.feedbackControl, item.step);
  } else assert.equal(root.activeScreen, flow.detailScreenId);
  if (['loading', 'normal'].includes(item.feedbackState))
    for (const id of ['order-reference', 'confirmation-option', 'confirm-order']) assert(nodes[id].disabled, `${item.outcomeId}: ${id} disabled`);
  assert.equal(nodes['observed-result'].visible, item.feedbackState === 'normal');
  if (item.feedbackState === 'normal') assert.equal(nodes['observed-result'].text,
    `${normal.successfulOrder.label} · ${normal.successfulOrder.amount} · Simulated success`);
}

async function observe(target, name, verify = () => {}) {
  actionContext = name;
  const actual = await snapshot(target, name);
  observations.push(actual); // Retain real failing observations before an assertion throws.
  assertSource(actual); verify(actual); return actual;
}

async function capture(page, name, actual) {
  assert(screenshots.includes(name) && !capturedScreenshots.includes(name));
  await page.screenshot({ path: path.join(options['--evidence'], name), fullPage: true });
  capturedScreenshots.push(name); writtenOutputs.push(name);
  if (actual) {
    const coverage = review.scenario.coverage.find(item => `${item.windowClass}-${item.outcomeId}.png` === name);
    assert(coverage, 'Screenshot must have exact source coverage');
    cases.push({ ...coverage, fixtureId: normal.fixtureId, sourceStep: outcome(coverage.outcomeId).step,
      sourceOutcome: outcome(coverage.outcomeId), profileIntent: ready.profiles.find(item => item.windowClass === coverage.windowClass),
      observation: actual, screenshot: name });
  }
}

function semanticState(actual) {
  const nodes = actual.nodes, root = nodes['readiness-preview'].data;
  return { activeScreen: root.activeScreen, selectedOrderId: root.selectedOrderId, selectedResourceId: root.selectedResourceId,
    outcomeId: root.outcomeId, pending: root.pending, input: nodes['order-reference'].value,
    variant: nodes['confirmation-option'].value, domain: nodes['domain-state'].text, ui: nodes['ui-state'].text,
    workflow: nodes['workflow-evidence'].text, feedback: nodes.feedback.text, feedbackData: nodes.feedback.data,
    error: nodes['input-error'].text, errorData: nodes['input-error'].data, invalid: nodes['order-reference'].attributes['aria-invalid'],
    result: nodes['observed-result'].text, resultVisible: nodes['observed-result'].visible,
    fixtureOutcome: nodes['fixture-outcome'].value, recoveries: actual.recoveries.map(node => node.data),
    navigation: nodes['open-detail'].data };
}

function assertReset(actual, viewport) {
  const nodes = actual.nodes, root = nodes['readiness-preview'].data;
  assert.deepEqual(actual.viewport, viewport); assert.equal(root.activeScreen, flow.listScreenId);
  assert.equal(root.selectedOrderId, ''); assert.equal(root.selectedResourceId, ''); assert.equal(root.outcomeId, '');
  assert.equal(root.pending, 'false'); assert.equal(nodes['order-reference'].value, field.initialValue);
  assert.equal(nodes['confirmation-option'].value, ''); assert.equal(nodes['fixture-outcome'].value, outcome('normal').outcomeId);
  assert.equal(nodes['domain-state'].text, normal.initialOrder.domainState.stateId); assert.equal(nodes['ui-state'].text, 'idle');
  assert.equal(nodes['workflow-evidence'].text, 'No active Workflow evidence');
  for (const id of ['feedback', 'input-error', 'observed-result']) { assert(!nodes[id].visible); assert.equal(nodes[id].text, ''); }
  assert.equal(nodes['order-reference'].attributes['aria-invalid'], 'false'); assert.deepEqual(nodes['input-error'].data, {});
  assert.equal(actual.recoveries.length, 0); assert(nodes['complete-confirmation'].disabled);
  for (const id of ['order-reference', 'confirmation-option', 'confirm-order']) assert(nodes[id].disabled);
  for (const key of ['stepId', 'uiUseCaseId', 'path', 'selectedOrderId']) assert(!Object.hasOwn(nodes['open-detail'].data, key));
  for (const key of ['stepId', 'uiUseCaseId', 'path', 'outcomeId', 'feedbackState', 'messageTextId',
    'outcomeFeedbackScreenId', 'outcomeFeedbackInteractionId']) assert(!Object.hasOwn(nodes.feedback.data, key));
}

async function reset(target, name) {
  const viewport = await target.evaluate(() => ({ width: innerWidth, height: innerHeight }));
  await target.locator('#reset-fixture').click();
  return observe(target, name, actual => assertReset(actual, viewport));
}

async function assertKeyboardFocus(target, id) {
  await target.locator(`#${id}`).scrollIntoViewIfNeeded();
  const focus = await target.locator(`#${id}`).evaluate(node => {
    const rect = node.getBoundingClientRect(), style = getComputedStyle(node);
    return { active: document.activeElement === node, visible: node.matches(':focus-visible'),
      outline: style.outlineStyle, width: parseFloat(style.outlineWidth), rect: { left: rect.left, right: rect.right, top: rect.top, bottom: rect.bottom },
      viewport: { width: innerWidth, height: innerHeight } };
  });
  observations.push(await snapshot(target, `${actionContext}/keyboard-state/${id}`));
  observations.push({ name: `${actionContext}/keyboard-focus/${id}`, ...focus });
  assert(focus.active && focus.visible && focus.outline !== 'none' && focus.width > 0, `Native visible keyboard focus: ${id}`);
  assert(focus.rect.left >= 0 && focus.rect.right <= focus.viewport.width + 1 && focus.rect.top >= 0 && focus.rect.bottom <= focus.viewport.height + 1);
}

async function openReady(target, name, inputValue = 'SO-EDIT-01', key = 'Enter') {
  actionContext = name;
  await target.locator('#open-detail').focus(); await target.locator('#open-detail').press(key);
  await observe(target, `${name}/navigation`, actual => {
    assert.equal(actual.activeElement, 'order-reference');
    assert.equal(actual.nodes['readiness-preview'].data.selectedOrderId, normal.initialOrder.orderId);
    assertRef(actual.nodes['open-detail'].data, ready.controls.find(item => item.role === 'navigation').control, flow.step);
    assert.equal(actual.nodes['open-detail'].data.selectedOrderId, normal.initialOrder.orderId);
  });
  await target.locator('#order-reference').fill(inputValue);
  await target.locator('#order-reference').press('Tab'); await assertKeyboardFocus(target, 'confirmation-option');
  await target.locator('#confirmation-option').press(sourceFlow.powertypeVariantId[0]);
  assert.equal(await target.locator('#confirmation-option').inputValue(), sourceFlow.powertypeVariantId,
    `${name}: native keyboard selected declared variant before Tab`);
  await target.locator('#confirmation-option').press('Tab'); await assertKeyboardFocus(target, 'confirm-order');
  const actual = await observe(target, `${name}/ready`, value => {
    assert.equal(value.nodes['order-reference'].value, inputValue);
    assert.equal(value.nodes['confirmation-option'].value, sourceFlow.powertypeVariantId);
    assert(!value.nodes['confirm-order'].disabled);
    assert.equal(value.nodes['domain-state'].text, normal.initialOrder.domainState.stateId);
  });
  // Verify the native source subsequence, starting with navigation only when the profile retains it.
  const expected = actual.nodes['readiness-preview'].data.persistentNavigation === 'true' ?
    ['open-detail', 'order-reference', 'confirmation-option', 'confirm-order'] : ['order-reference', 'confirmation-option', 'confirm-order'];
  assert.deepEqual(actual.reachableSourceFocus, expected);
  await target.locator(`#${expected[0]}`).focus();
  for (let index = 1; index < expected.length; index++) {
    await target.locator(`#${expected[index - 1]}`).press('Tab'); await assertKeyboardFocus(target, expected[index]);
  }
  return actual;
}

async function beginNormal(target, name, key = 'Space') {
  await target.locator('#confirm-order').press(key);
  return observe(target, `${name}/loading`, actual => assertOutcome(actual, outcome('loading')));
}

async function completeNormal(target, name) {
  assert.equal(await target.locator('#fixture-outcome').inputValue(), outcome('normal').outcomeId);
  await target.locator('#complete-confirmation').click();
  return observe(target, `${name}/normal`, actual => assertOutcome(actual, outcome('normal')));
}

async function prepareOutcome(target, id, name) {
  await reset(target, `${name}/reset`);
  if (id === 'empty') await target.locator('#fixture-outcome').selectOption(id);
  else {
    await openReady(target, `${name}/prepare`);
    if (id === 'validation-failed') {
      await target.locator('#order-reference').fill('   ');
      await target.locator('#order-reference').press('Tab');
      await assertLocalRule(target, 'required-text', `${name}/required-blur`);
      await target.locator('#confirm-order').click();
    } else {
      if (id !== 'normal' && id !== 'loading') await target.locator('#fixture-outcome').selectOption(id);
      await beginNormal(target, name, 'Enter');
      if (id !== 'loading') await target.locator('#complete-confirmation').click();
    }
  }
  return observe(target, `${name}/${id}`, actual => assertOutcome(actual, outcome(id)));
}

async function assertLocalRule(target, kind, name) {
  const rule = field.localRules.find(item => item.ruleKind === kind); assert(rule);
  return observe(target, name, actual => {
    const nodes = actual.nodes, error = nodes['input-error']; assertUsable(error, actual);
    assert.equal(nodes['order-reference'].attributes['aria-invalid'], 'true');
    assert.equal(error.text, text(rule.messageTextId));
    assert.deepEqual(error.data, { constraintId: rule.constraint.constraintId, detailCode: rule.constraint.detailCode,
      feedbackId: rule.feedbackId, messageTextId: rule.messageTextId });
    assert.equal(nodes['domain-state'].text, normal.initialOrder.domainState.stateId);
    assert.equal(nodes['confirmation-option'].value, sourceFlow.powertypeVariantId);
  });
}

async function exerciseRecoveries(target, item, name) {
  for (let index = 0; index < item.recoveries.length; index++) {
    const recovery = item.recoveries[index];
    const modes = ['conflict', 'validation-failed', 'unavailable', 'operation-failed'].includes(item.feedbackState)
      ? ['keyboard', 'pointer'] : ['keyboard'];
    for (const mode of modes) {
      const activationName = `${name}/${index}/${mode}`;
      // Given a separately prepared outcome and its exact declared recovery.
      const before = await prepareOutcome(target, item.outcomeId, `${activationName}/before`);
      const button = target.locator('#recovery-controls button').nth(index);
      if (mode === 'pointer') {
        await target.locator('#order-reference').focus();
        await observe(target, `${activationName}/focused-input`, actual => {
          const input = actual.nodes['order-reference'];
          assert(input.focused && !input.disabled); assertUsable(input, actual);
          assertOutcome(actual, item); assertRef(actual.recoveries[index].data, recovery.target, recovery.step);
        });
        // When the normal pointer activation blurs the editable input.
        await button.click();
      } else {
        await button.focus();
        await observe(target, `${activationName}/focused-recovery`, actual => {
          assert(actual.recoveries[index].focused); assertOutcome(actual, item);
          assertRef(actual.recoveries[index].data, recovery.target, recovery.step);
        });
        // When the declared recovery receives native keyboard activation.
        await button.press(index % 2 ? 'Space' : 'Enter');
      }
      // Then the source-mapped action preserves the declared state and input semantics.
      if (recovery.kind === 'edit-input') {
        await observe(target, `${activationName}/edited`, actual => {
          assert.equal(actual.activeElement, 'order-reference'); assert(!actual.nodes['order-reference'].disabled);
          assert.equal(actual.nodes['order-reference'].attributes['aria-invalid'], 'false');
          assert(!actual.nodes['input-error'].visible && !actual.nodes.feedback.visible);
          assert.equal(actual.nodes['readiness-preview'].data.outcomeId, '');
          assert.equal(actual.nodes['order-reference'].value, before.nodes['order-reference'].value);
          assert.equal(actual.nodes['confirmation-option'].value, before.nodes['confirmation-option'].value);
        });
        await target.locator('#order-reference').fill('SO-RECOVER-01');
        await target.locator('#confirm-order').click();
        await observe(target, `${activationName}/loading`, actual => assertOutcome(actual, outcome('loading')));
        await completeNormal(target, activationName);
      } else if (recovery.kind === 'return-to-entry') {
        await observe(target, `${activationName}/entry`, actual => {
          assert.equal(actual.activeElement, 'open-detail'); assert.equal(actual.nodes['readiness-preview'].data.activeScreen, flow.listScreenId);
          assert.equal(actual.nodes['domain-state'].text, before.nodes['domain-state'].text);
          assert.equal(actual.nodes['readiness-preview'].data.outcomeId, ''); assert(!actual.nodes.feedback.visible);
        });
      } else if (recovery.target.screenId === flow.listScreenId && recovery.target.interactionId === flow.listEntryInteractionId) {
        await observe(target, `${activationName}/entry-reset`, actual => { assertReset(actual, before.viewport); assert.equal(actual.activeElement, 'open-detail'); });
      } else {
        await observe(target, `${activationName}/retry-loading`, actual => {
          assertOutcome(actual, outcome('loading'));
          assert.equal(actual.nodes['order-reference'].value, before.nodes['order-reference'].value);
          assert.equal(actual.nodes['confirmation-option'].value, before.nodes['confirmation-option'].value);
        });
        await completeNormal(target, activationName);
      }
    }
  }
}

async function exerciseProfile(page, name) {
  actionContext = `${name}/entry`; await page.setViewportSize(viewports[name]);
  await page.goto(`${baseUrl}/index.html`, { waitUntil: 'load' });
  assert.deepEqual(JSON.parse(await page.locator('#readiness-data').textContent()), source, 'Full embedded production receipt');
  await observe(page, `${name}/entry`, actual => assertReset(actual, viewports[name]));
  await openReady(page, `${name}/normal-flow`, 'SO-EDIT-01', name === 'medium' ? 'Space' : 'Enter');
  const loading = await beginNormal(page, `${name}/normal-flow`, name === 'expanded' ? 'Enter' : 'Space');
  await capture(page, `${name}-loading.png`, loading);
  const rectangle = await page.locator('#confirm-order').boundingBox(); assert(rectangle);
  await page.mouse.click(rectangle.x + rectangle.width / 2, rectangle.y + rectangle.height / 2);
  const duplicate = await observe(page, `${name}/duplicate-disabled-invocation`, actual => assertOutcome(actual, outcome('loading')));
  assert.deepEqual(semanticState(duplicate), semanticState(loading), 'Duplicate pending invocation retains state');
  const successful = await completeNormal(page, `${name}/normal-flow`);
  await capture(page, `${name}-normal.png`, successful);
  for (const id of outcomeIds.filter(value => !['normal', 'loading'].includes(value))) {
    const actual = await prepareOutcome(page, id, `${name}/case`);
    if (id === 'validation-failed') await assertLocalRule(page, 'required-text', `${name}/required-invoke`);
    await capture(page, `${name}-${id}.png`, actual);
    await exerciseRecoveries(page, outcome(id), `${name}/recovery/${id}`);
  }
  for (const invalid of ['lowercase', '注文-01']) {
    await reset(page, `${name}/format/${invalid}/reset`);
    await openReady(page, `${name}/format/${invalid}/prepare`);
    await page.locator('#order-reference').fill(invalid); await page.locator('#order-reference').press('Tab');
    await assertLocalRule(page, 'ascii-identifier', `${name}/format/${invalid}/blur`);
    await page.locator('#confirm-order').click();
    await observe(page, `${name}/format/${invalid}/invoke`, actual => assertOutcome(actual, outcome('validation-failed')));
    await assertLocalRule(page, 'ascii-identifier', `${name}/format/${invalid}/retained-rule`);
  }
  await reset(page, `${name}/manual-validation/reset`);
  await openReady(page, `${name}/manual-validation/prepare`);
  await page.locator('#fixture-outcome').selectOption('validation-failed');
  await observe(page, `${name}/manual-validation/additional-provenance`, actual => {
    assertOutcome(actual, outcome('validation-failed'));
    assert.equal(actual.nodes['order-reference'].attributes['aria-invalid'], 'false'); assert(!actual.nodes['input-error'].visible);
  });
  const baseline = await reset(page, `${name}/replay/reset`);
  const replays = [];
  for (let index = 0; index < 2; index++) {
    await page.locator('#replay-fixture').click();
    const replay = await observe(page, `${name}/replay/${index + 1}`, actual => {
      assertOutcome(actual, outcome('normal')); assert.equal(actual.nodes['order-reference'].value, field.initialValue);
      assert.equal(actual.nodes['confirmation-option'].value, sourceFlow.powertypeVariantId);
    });
    replays.push(semanticState(replay));
  }
  assert.deepEqual(replays[0], replays[1], 'Two deterministic semantic replays');
  const resetAgain = await reset(page, `${name}/reset-after-replay`);
  assert.deepEqual(semanticState(resetAgain), semanticState(baseline), 'Exact baseline restored after replay');
}

async function resizeRetainingState(page, widths, name) {
  let before = await observe(page, `${name}/before`);
  for (const width of widths) {
    const previousFocus = before.activeElement;
    const recoveryFocus = before.recoveries.find(node => node.focused);
    // When a viewport change renders the unchanged active outcome.
    await page.setViewportSize({ width, height: 1112 });
    await page.waitForFunction(expected => document.getElementById('readiness-preview').dataset.windowClass === expected,
      width < 600 ? 'compact' : width < 1024 ? 'medium' : 'expanded');
    let after = await observe(page, `${name}/width-${width}`);
    assert.deepEqual(semanticState(after), semanticState(before), 'Resize retains full semantic state and source provenance');
    // Then an available source-bound recovery retains its actual DOM focus.
    if (recoveryFocus) {
      const retained = after.recoveries.find(node => node.focused);
      assert(retained, 'Focused recovery retained after resize');
      assert(retained.connected && !retained.disabled, 'Focused recovery remains connected and enabled');
      assertUsable(retained, after);
      assert.deepEqual(retained.data, recoveryFocus.data, 'Exact source recovery provenance retained');
      assert.equal(retained.text, recoveryFocus.text, 'Recovery action text retained');
    }
    const oldControl = after.nodes[previousFocus];
    if (oldControl && oldControl.data.focusOrder) {
      if (after.reachableSourceFocus.includes(previousFocus)) assert.equal(after.activeElement, previousFocus, 'Reachable source focus retained');
      else {
        const counterpart = ['order-reference', 'open-detail'].find(id => after.reachableSourceFocus.includes(id))
          ?? after.reachableSourceFocus[0];
        if (counterpart) {
          assert.equal(after.activeElement, counterpart, 'Hidden source focus transferred to admitted counterpart');
          assert(after.nodes[counterpart].visible && !after.nodes[counterpart].disabled);
        } else {
          assert.equal(after.nodes['readiness-preview'].data.pending, 'true', 'No reachable source counterpart requires actual pending state');
          assert.deepEqual(after.reachableSourceFocus, [], 'Pending waiting fixture applies only without reachable source controls');
          const bodyFocused = await page.evaluate(() => document.activeElement === document.body);
          observations.push({ name: `${name}/width-${width}/pending-body-focus`, bodyFocused });
          assert(bodyFocused, 'Unavailable source focus leaves actual document body focused');
          assertUsable(after.nodes['complete-confirmation'], after);
          assert(!after.nodes['complete-confirmation'].disabled, 'Pending completion fixture is enabled');
          const waitControls = ['fixture-outcome', 'complete-confirmation'];
          for (let index = 1; index <= 8; index++) {
            await page.keyboard.press('Tab');
            after = await observe(page, `${name}/width-${width}/pending-native-tab-${index}`);
            observations.push({ name: `${name}/width-${width}/pending-native-tab-hop`, index, activeElement: after.activeElement });
            assert.deepEqual(semanticState(after), semanticState(before), 'Native waiting-fixture traversal retains full semantic state and source provenance');
            if (waitControls.includes(after.activeElement)) break;
          }
          assert(waitControls.includes(after.activeElement), 'Pending waiting fixture receives focus within eight native Tab hops');
          await assertKeyboardFocus(page, after.activeElement);
          after = await observe(page, `${name}/width-${width}/pending-wait-focus`);
          assert(waitControls.includes(after.activeElement), 'Actual waiting-fixture focus retained after visibility assertion');
          assertUsable(after.nodes[after.activeElement], after);
          assert(!after.nodes[after.activeElement].disabled, 'Focused pending waiting fixture is enabled');
          assert.deepEqual(semanticState(after), semanticState(before), 'Focused waiting fixture retains full semantic state and source provenance');
        }
      }
    }
    before = after;
  }
}

async function exerciseResize(page) {
  const transitions = [390, 834, 1440, 834, 390, 1440];
  await page.setViewportSize(viewports.expanded); await reset(page, 'resize/idle/reset');
  await page.locator('#open-detail').focus(); await resizeRetainingState(page, transitions, 'resize/idle');
  await openReady(page, 'resize/edited');
  await page.locator('#open-detail').focus(); await resizeRetainingState(page, transitions, 'resize/edited');
  await page.locator('#order-reference').focus(); await resizeRetainingState(page, [599, 600, 1023, 1024], 'resize/boundaries');
  await page.locator('#confirm-order').click();
  await observe(page, 'resize/pending/loading', actual => assertOutcome(actual, outcome('loading')));
  await resizeRetainingState(page, transitions, 'resize/pending'); await completeNormal(page, 'resize/pending');
  for (const item of ready.outcomes.filter(value => value.recoveries.length)) {
    await prepareOutcome(page, item.outcomeId, `resize/${item.outcomeId}`);
    if (await page.locator('#order-reference').isEnabled() && await page.locator('#order-reference').isVisible())
      await page.locator('#order-reference').focus();
    else await page.locator('#open-detail').focus();
    await resizeRetainingState(page, transitions, `resize/${item.outcomeId}`);
    for (let index = 0; index < item.recoveries.length; index++) {
      const recovery = item.recoveries[index];
      const name = `resize/${item.outcomeId}/recovery/${index}/${recovery.kind}`;
      // Given an available recovery with actual focus and exact source provenance.
      await page.locator('#recovery-controls button').nth(index).focus();
      await observe(page, `${name}/focused`, actual => {
        const focused = actual.recoveries[index];
        assert(focused.focused && focused.connected && !focused.disabled); assertUsable(focused, actual);
        assertOutcome(actual, item); assertRef(focused.data, recovery.target, recovery.step);
      });
      await resizeRetainingState(page, [...transitions, 599, 600, 1023, 1024], name);
    }
  }
}

async function inspectStructuralReview(page) {
  actionContext = 'structural-review'; await page.setViewportSize(viewports.expanded);
  await page.goto(`${baseUrl}/review.html`, { waitUntil: 'load' });
  const actual = await page.evaluate(() => ({ title: document.querySelector('h1').textContent,
    headings: [...document.querySelectorAll('h2,h3')].map(node => node.textContent),
    tables: [...document.querySelectorAll('section')].filter(node => node.querySelector('table')).map(node => ({
      heading: node.querySelector('h2').textContent,
      rows: [...node.querySelectorAll('tbody tr')].map(row => [...row.querySelectorAll('td')].map(cell => cell.textContent)),
      columns: [...node.querySelectorAll('thead th')].map(cell => cell.textContent) })),
    sources: [...document.querySelectorAll('details')].filter(node => node.querySelector('summary')?.textContent.startsWith('Full canonical '))
      .map(node => ({ name: node.querySelector('summary').textContent.replace('Full canonical ', ''), content: JSON.parse(node.querySelector('pre').textContent) })),
    text: document.body.textContent, scripts: document.querySelectorAll('script').length,
    forms: document.querySelectorAll('form,input,select,button').length,
    networkAssets: document.querySelectorAll('img,iframe,script[src],link[href],audio,video,source,object,embed').length }));
  observations.push({ name: 'structural-review', ...actual });
  assert.equal(actual.title, 'Complete target-ready Logical UI review');
  for (const heading of ['Complete authority and provenance', 'Complete binding',
    'Window placement, navigation and region persistence', 'Source control roles and global focus',
    'Input intent, Value/Datatype and local constraint feedback', 'Text identities, source attribution and preserved default values',
    'Outcomes: separate UI, domain, Workflow evidence and mapped recoveries', 'Scenario normal records and flow',
    'Profile and outcome coverage', 'Exact Operation, StateMachine and Powertype source flow', 'Kernel candidate evidence'])
    assert(actual.headings.includes(heading), heading);
  for (const identity of Object.values(review.binding).filter(value => typeof value === 'string')) assert(actual.text.includes(identity));
  assert(actual.text.includes(normal.fixtureId) && actual.text.includes('acceptance pending'));
  assert.equal(actual.scripts, 0); assert.equal(actual.forms, 0); assert.equal(actual.networkAssets, 0);
  assert.deepEqual(Object.fromEntries(actual.sources.map(item => [item.name, item.content])), review.sourceInputs);
  const coverage = actual.tables.find(item => item.heading === 'Profile and outcome coverage');
  assert(coverage); assert.equal(coverage.rows.length, 21);
  const rows = coverage.rows.map(row => Object.fromEntries(coverage.columns.map((key, index) => [key, row[index]])));
  assert.deepEqual(rows, review.scenario.coverage);
  await capture(page, 'structural-review.png');
}

async function inspectComparison(page) {
  actionContext = 'responsive-comparison'; await page.goto(`${baseUrl}/compare.html`, { waitUntil: 'load' });
  const frames = page.locator('iframe'); assert.equal(await frames.count(), 3);
  const names = ['compact', 'medium', 'expanded'];
  for (let index = 0; index < names.length; index++) {
    const name = names[index], element = frames.nth(index);
    assert.equal(await element.getAttribute('src'), 'index.html');
    assert.equal(await element.getAttribute('title'), `${name[0].toUpperCase() + name.slice(1)} complete Logical UI simulation`);
    const frame = await (await element.elementHandle()).contentFrame(); assert(frame);
    assert.deepEqual(JSON.parse(await frame.locator('#readiness-data').textContent()), source);
    await observe(frame, `comparison/${name}/entry`, actual => assertReset(actual, viewports[name]));
    await openReady(frame, `comparison/${name}/flow`);
    await beginNormal(frame, `comparison/${name}/flow`); await completeNormal(frame, `comparison/${name}/flow`);
    // Every frame retains its own selection and result while the other independent frames run.
    await observe(frame, `comparison/${name}/completed`, actual => {
      assert.deepEqual(actual.viewport, viewports[name]); assertOutcome(actual, outcome('normal'));
    });
  }
  for (let index = 0; index < names.length; index++) {
    const frame = await (await frames.nth(index).elementHandle()).contentFrame();
    await observe(frame, `comparison/${names[index]}/independent-retained`, actual => assertOutcome(actual, outcome('normal')));
  }
  const shell = await page.evaluate(() => ({ text: document.body.textContent,
    horizontalScroll: document.getElementById('frames').scrollWidth > document.getElementById('frames').clientWidth }));
  for (const identity of Object.values(review.binding).filter(value => typeof value === 'string')) assert(shell.text.includes(identity));
  assert(shell.horizontalScroll, 'Comparison shell intentionally scrolls horizontally');
  observations.push({ name: 'responsive-comparison-shell', ...shell });
  // Frame the completed profiles from the top without changing their declared viewports.
  const framingViewport = {
    width: names.reduce((sum, name) => sum + viewports[name].width, 0) + 128,
    height: Math.max(...names.map(name => viewports[name].height)) + 300
  };
  await page.setViewportSize(framingViewport);
  await page.evaluate(() => {
    window.scrollTo(0, 0);
    document.getElementById('frames').scrollTo(0, 0);
  });
  const framingFrames = [];
  for (let index = 0; index < names.length; index++) {
    const frame = await (await frames.nth(index).elementHandle()).contentFrame();
    await frame.evaluate(() => window.scrollTo(0, 0));
    framingFrames.push({ name: names[index], ...await frame.evaluate(() => ({
      viewport: { width: window.innerWidth, height: window.innerHeight },
      scroll: { x: window.scrollX, y: window.scrollY }
    })) });
  }
  const framing = await page.evaluate(() => ({
    viewport: { width: window.innerWidth, height: window.innerHeight },
    scroll: { x: window.scrollX, y: window.scrollY },
    shell: { scrollLeft: document.getElementById('frames').scrollLeft,
      scrollTop: document.getElementById('frames').scrollTop }
  }));
  observations.push({ name: 'responsive-comparison-framing', ...framing, frames: framingFrames });
  assert.deepEqual(framing.viewport, framingViewport, 'Comparison screenshot framing viewport');
  assert.deepEqual(framing.scroll, { x: 0, y: 0 }, 'Comparison outer page starts at the top');
  assert.deepEqual(framing.shell, { scrollLeft: 0, scrollTop: 0 }, 'Comparison shell starts at the top left');
  for (const actual of framingFrames) {
    assert.deepEqual(actual.viewport, viewports[actual.name], `${actual.name} retains its declared viewport`);
    assert.deepEqual(actual.scroll, { x: 0, y: 0 }, `${actual.name} comparison frame starts at the top`);
  }
  await capture(page, 'responsive-comparison.png');
}

async function walkBrowser(page) {
  for (const name of Object.keys(viewports)) await exerciseProfile(page, name);
  assert.equal(cases.length, 21);
  assert.deepEqual(cases.map(item => item.coverageId).sort(), review.scenario.coverage.map(item => item.coverageId).sort());
  await exerciseResize(page); await inspectStructuralReview(page); await inspectComparison(page);
  assert.deepEqual([...capturedScreenshots].sort(), [...screenshots].sort()); assertActive(); clientSuccess = true;
}

async function cleanup() {
  if (cleanupPromise) return cleanupPromise;
  cleanupPromise = (async () => {
    // Startup retains ownership until every acquisition settles, including interruption during launch.
    if (startupPromise) await startupPromise.catch(() => {});
    let success = true;
    for (const close of [async () => { if (context) await context.close(); },
      async () => { if (browser) await browser.close(); }, async () => {
        if (server?.listening) await new Promise((resolve, reject) => {
          server.close(error => error ? reject(error) : resolve()); server.closeAllConnections();
        });
      }, async () => { if (profile) await fs.rm(profile, { recursive: true, force: true }); }]) {
      try { await close(); }
      catch (error) { success = false; errors.push({ type: 'cleanup', context: actionContext, message: error.message }); }
    }
    cleanupSuccess = success;
  })();
  return cleanupPromise;
}

function interrupt(reason) {
  interrupted = true; errors.push({ type: 'interruption', context: actionContext, message: reason }); void cleanup();
}

const onSigint = () => interrupt('SIGINT'), onSigterm = () => interrupt('SIGTERM');
process.once('SIGINT', onSigint); process.once('SIGTERM', onSigterm);
// Leave the supervisor's 480-second bound room for awaited cleanup and terminal evidence.
const overallTimer = setTimeout(() => interrupt('Overall 450 second timeout'), 450000);
try {
  options = parseArgs(process.argv.slice(2));
  startupPromise = (async () => {
    const files = await prepareInputs(); assertActive(); await startServer(files); assertActive();
    const page = await launchBrowser(); assertActive(); return page;
  })();
  await walkBrowser(await startupPromise);
} catch (error) {
  errors.push({ type: 'assertion-or-action', context: actionContext, message: error.message, stack: error.stack });
} finally {
  await cleanup(); clearTimeout(overallTimer);
  process.removeListener('SIGINT', onSigint); process.removeListener('SIGTERM', onSigterm);
  let success = readiness && clientSuccess && cleanupSuccess && !interrupted && errors.length === 0;
  if (evidenceReady) {
    try {
      const portableErrors = errors.map(error => ({ type: error.type, context: error.context,
        message: error.message.replace(/https?:\/\/[^\s)]+/g, '[request URL]')
          .replace(/\/(?:Users|Applications|private|var|tmp)\/[^\s)]+/g, '[local path]') }));
      await writeJson('browser-observations.json', { schema: 'cozy.logical-ui-target-ready-browser.v1',
        binding: review?.binding, rendererProfile: source?.rendererProfile, rendererVersion: source?.version,
        browserVersion, playwrightVersion, fixtureId: normal?.fixtureId, sourceReceipt: source,
        cases, observations, screenshots: capturedScreenshots, network: { requests,
          externalRequests: requests.filter(item => item.origin === 'external').length,
          mutatingRequests: requests.filter(item => item.method !== 'GET').length },
        errors: portableErrors, result: success ? 'PASS' : 'FAIL' });
      await writeJson('browser-result.json', { schema: 'cozy.logical-ui-target-ready-browser-result.v1',
        runDirectory: options['--evidence'], readiness, clientSuccess, cleanupSuccess, processCompletion: 'terminal',
        pid: process.pid, startedAt, completedAt: new Date().toISOString(), baseUrl,
        requiredOutputs: outputNames, actualOutputs: [...writtenOutputs, 'browser-result.json'],
        errors, result: success ? 'PASS' : 'FAIL' });
    } catch (error) { success = false; process.stderr.write(`Evidence write failed: ${error.message}\n`); }
  }
  process.stdout.write(JSON.stringify({ readiness, clientSuccess, cleanupSuccess, processCompletion: 'terminal',
    result: success ? 'PASS' : 'FAIL' }) + '\n');
  if (!success) {
    process.stderr.write(errors.map(error => `${error.type} (${error.context}): ${error.message}`).join('\n') + '\n');
    process.exitCode = 1;
  }
}
