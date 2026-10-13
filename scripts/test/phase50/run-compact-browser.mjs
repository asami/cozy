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
const screenshots = ['01-list.png', '02-selected.png', '03-detail.png', '04-confirm-ready.png',
  '05-simulated-success.png', '06-structural-review.png'];
const startedAt = new Date().toISOString();
const errors = [];
const requests = [];
const states = [];
const capturedScreenshots = [];
let options;
let source;
let server;
let context;
let browser;
let profile;
let baseUrl;
let browserVersion;
let readiness = false;
let clientSuccess = false;
let cleanupSuccess = false;
let cleanupPromise;
let startupPromise;
let interrupted = false;
let evidenceReady = false;

function parseArgs(argv) {
  assert.equal(argv.length, 10, 'Exactly five flag/value pairs are required');
  const result = {};
  for (let index = 0; index < argv.length; index += 2) {
    const flag = argv[index];
    assert(flags.includes(flag), `Unknown flag: ${flag}`);
    assert(!(flag in result), `Duplicate flag: ${flag}`);
    const value = argv[index + 1];
    assert(value && !value.startsWith('--'), `Missing value: ${flag}`);
    assert(path.isAbsolute(value), `Absolute path required: ${flag}`);
    assert(!value.split(path.sep).some(part => part === '.' || part === '..'), `Traversal forbidden: ${flag}`);
    assert.equal(path.normalize(value), value, `Canonical path required: ${flag}`);
    result[flag] = value;
  }
  flags.forEach(flag => assert(flag in result, `Missing flag: ${flag}`));
  return result;
}

async function checkComponents(target, allowMissing = false) {
  let current = path.parse(target).root;
  for (const component of target.slice(current.length).split(path.sep).filter(Boolean)) {
    current = path.join(current, component);
    try {
      const stat = await fs.lstat(current);
      assert(!stat.isSymbolicLink(), 'Symlink redirection forbidden');
    } catch (error) {
      if (allowMissing && error.code === 'ENOENT') continue;
      throw error;
    }
  }
}

async function regularFile(target) {
  await checkComponents(target);
  assert((await fs.lstat(target)).isFile(), 'A regular file is required');
  assert.equal(await fs.realpath(target), target, 'Canonical regular file required');
}

function within(parent, target) {
  const relative = path.relative(parent, target);
  return relative === '' || (!relative.startsWith(`..${path.sep}`) && relative !== '..' && !path.isAbsolute(relative));
}

async function prepareInputs() {
  await checkComponents(options['--site']);
  assert((await fs.lstat(options['--site'])).isDirectory(), 'Site directory required');
  assert.equal(await fs.realpath(options['--site']), options['--site']);
  await regularFile(options['--receipt']);
  await regularFile(options['--browser']);
  await checkComponents(options['--playwright']);
  assert((await fs.lstat(options['--playwright'])).isDirectory(), 'Installed Playwright directory required');
  await regularFile(path.join(options['--playwright'], 'package.json'));
  assert(!within(options['--site'], options['--receipt']), 'Receipt must be outside the served site');
  assert(!within(options['--site'], options['--evidence']) && !within(options['--evidence'], options['--site']),
    'Evidence and served site must be separate');
  assert(!within(options['--evidence'], options['--receipt']), 'Source receipt must be outside the owned run');
  await checkComponents(options['--evidence'], true);
  await fs.mkdir(options['--evidence'], { recursive: true });
  assert.equal(await fs.realpath(options['--evidence']), options['--evidence']);
  for (const filename of [...screenshots, 'browser-observations.json', 'server-process.json', 'browser-result.json']) {
    const target = path.join(options['--evidence'], filename);
    await checkComponents(target, true);
    try {
      assert((await fs.lstat(target)).isFile(), 'Evidence destination must be absent or a regular file');
    } catch (error) {
      if (error.code !== 'ENOENT') throw error;
    }
  }
  evidenceReady = true;
  source = JSON.parse(await fs.readFile(options['--receipt'], 'utf8'));
  assert.equal(source.schema, 'cozy.logical-ui-compact-web.v1');
  assert.equal(source.version, 1);
  assert.equal(source.rendererProfile, 'cozy.logical-ui-compact-web.renderer.v1');
  assert.equal(source.profile, 'compact');
  assert.equal(source.detailPlacement, 'navigate-to-detail');
  assert.equal(source.simulated, true);
  assert.deepEqual(source.outputs, ['index.html', 'review.html', 'compact-preview-receipt.json']);
  assert(source.scenario && source.candidateReview, 'Complete generated receipt required');
  const files = {};
  for (const filename of ['index.html', 'review.html']) {
    const target = path.join(options['--site'], filename);
    await regularFile(target);
    files[`/${filename}`] = await fs.readFile(target);
  }
  return files;
}

async function writeJson(filename, value) {
  const target = path.join(options['--evidence'], filename);
  await checkComponents(target, true);
  await fs.writeFile(target, JSON.stringify(value, null, 2) + '\n', { flag: 'w' });
}

async function startServer(files) {
  server = http.createServer((request, response) => {
    if (request.method !== 'GET') {
      response.writeHead(405, { Allow: 'GET' });
      response.end();
    } else if (Object.hasOwn(files, request.url)) {
      response.writeHead(200, { 'Content-Type': 'text/html; charset=utf-8', 'Cache-Control': 'no-store' });
      response.end(files[request.url]);
    } else {
      response.writeHead(404);
      response.end();
    }
  });
  await new Promise((resolve, reject) => {
    server.once('error', reject);
    server.listen(0, '127.0.0.1', resolve);
  });
  baseUrl = `http://127.0.0.1:${server.address().port}`;
  await writeJson('server-process.json', { schema: 'cozy.logical-ui-compact-server.v1',
    pid: process.pid, startedAt, baseUrl });
  const response = await fetch(`${baseUrl}/index.html`, { signal: AbortSignal.timeout(10000) });
  assert.equal(response.status, 200);
  const actual = await response.text();
  assert.equal(actual, files['/index.html'].toString('utf8'));
  assert(actual.includes(source.scenario.fixtureId));
  readiness = true;
}

async function cleanup() {
  if (cleanupPromise) return cleanupPromise;
  cleanupPromise = (async () => {
    // Admission/startup owns any in-flight acquisition. Close resources only after it settles.
    if (startupPromise) await startupPromise.catch(() => {});
    let success = true;
    for (const close of [async () => { if (context) await context.close(); },
      async () => { if (browser) await browser.close(); }, async () => {
        if (server?.listening) {
          await new Promise((resolve, reject) => {
            server.close(error => error ? reject(error) : resolve());
            server.closeAllConnections();
          });
        }
      }, async () => { if (profile) await fs.rm(profile, { recursive: true, force: true }); }]) {
      try { await close(); }
      catch (error) { success = false; errors.push({ type: 'cleanup', message: error.message }); }
    }
    cleanupSuccess = success;
  })();
  return cleanupPromise;
}

function interrupt(reason) {
  interrupted = true;
  errors.push({ type: 'interruption', message: reason });
  void cleanup();
}

const bindingText = binding => `${binding.component.canonicalIdentity}#${binding.exportId}`;

async function snapshot(page, name) {
  return page.evaluate(stateName => {
    const element = id => document.getElementById(id);
    const text = id => element(id).textContent;
    const attrs = id => Object.fromEntries([...element(id).attributes]
      .filter(attribute => attribute.name.startsWith('data-')).map(attribute => [attribute.name, attribute.value]));
    const record = prefix => ({ orderId: text(`${prefix}-order-id`), label: text(`${prefix}-order-label`),
      amount: text(`${prefix}-order-amount`) });
    return { name: stateName, root: attrs('compact-preview'), hash: location.hash,
      listVisible: !element('order-list-screen').hidden, detailVisible: !element('order-detail-screen').hidden,
      list: record('list'), detail: record('detail'), listDomain: text('list-domain-state'),
      domain: text('domain-state'), domainAttributes: attrs('domain-state'), ui: text('ui-state'), uiAttributes: attrs('ui-state'),
      selected: text('selected-order'), openDetailDisabled: element('open-detail').disabled,
      confirmationOption: element('confirmation-option').value, confirmDisabled: element('confirm-order').disabled,
      simulatedVisible: !element('simulated-result').hidden, simulatedText: text('simulated-result'),
      listAttributes: attrs('order-list-screen'), detailAttributes: attrs('order-detail-screen'),
      navigation: attrs('open-detail'), selection: attrs('confirmation-option'), command: attrs('confirm-order'),
      observation: attrs('simulated-result'),
      overflow: document.documentElement.scrollWidth > innerWidth };
  }, name);
}

function assertSource(actual) {
  const tuple = source.candidateReview;
  const scenario = source.scenario;
  const flow = scenario.flow;
  for (const [attribute, expected] of Object.entries({ 'data-fixture-id': scenario.fixtureId,
    'data-coverage-id': scenario.coverageId, 'data-profile': source.profile, 'data-detail-placement': source.detailPlacement })) {
    assert.equal(actual.root[attribute], expected, attribute);
  }
  assert.equal(actual.listAttributes['data-screen-id'], flow.listScreenId);
  assert.equal(actual.detailAttributes['data-screen-id'], flow.detailScreenId);
  for (const [control, id] of [[actual.listAttributes, flow.listEntryInteractionId], [actual.navigation, flow.navigationInteractionId],
    [actual.selection, flow.selectionInteractionId], [actual.command, flow.commandInteractionId], [actual.observation, flow.observationInteractionId]]) {
    assert.equal(control['data-interaction-id'], id);
    assert.equal(control['data-ui-step-id'], flow.step.stepId);
  }
  assert.equal(actual.selection['data-powertype-binding'], bindingText(source.powertypeBinding));
  assert.equal(actual.command['data-operation-binding'], bindingText(source.operationBinding));
  assert.equal(actual.command['data-state-machine-binding'], bindingText(source.stateMachineBinding));
  assert.equal(actual.overflow, false, 'Compact viewport must not overflow horizontally');
}

function recordText(order) {
  return { orderId: order.orderId, label: order.label, amount: order.amount };
}

async function observe(page, name, index, verify) {
  const actual = await snapshot(page, name);
  assertSource(actual);
  verify(actual);
  states.push(actual);
  await page.screenshot({ path: path.join(options['--evidence'], screenshots[index]), fullPage: true });
  capturedScreenshots.push(screenshots[index]);
}

async function launchBrowser() {
  const require = createRequire(path.join(options['--playwright'], 'package.json'));
  const { chromium } = require(options['--playwright']);
  profile = await fs.mkdtemp(path.join(await fs.realpath(os.tmpdir()), 'cozy-compact-chrome-'));
  context = await chromium.launchPersistentContext(profile, { executablePath: options['--browser'], headless: true,
    viewport: { width: 390, height: 844 }, timeout: 20000 });
  browser = context.browser();
  assert(browser, 'The owned Chromium process is required');
  browserVersion = browser.version();
  context.setDefaultTimeout(10000);
  context.setDefaultNavigationTimeout(15000);
  await context.route('**/*', async route => {
    const request = route.request();
    const url = new URL(request.url());
    const allowed = url.origin === baseUrl && request.method() === 'GET';
    requests.push({ origin: url.origin === baseUrl ? 'owned-loopback' : 'external',
      path: url.pathname, method: request.method(), allowed });
    if (!allowed) {
      errors.push({ type: 'network', message: 'External or mutating request rejected' });
      await route.abort();
    } else await route.continue();
  });
  context.on('page', page => {
    page.on('pageerror', error => errors.push({ type: 'pageerror', message: error.message }));
    page.on('console', message => { if (message.type() === 'error') errors.push({ type: 'console', message: message.text() }); });
  });
  // Persistent Chromium starts with a blank page; create the observed page after installing event handlers.
  const page = await context.newPage();
  return page;
}

async function walkBrowser(page) {
  await page.goto(`${baseUrl}/index.html`, { waitUntil: 'load' });
  const embedded = await page.locator('#compact-data').textContent();
  assert.deepEqual(JSON.parse(embedded), source, 'App declaration must equal the full production receipt');
  const scenario = source.scenario;
  const flow = scenario.flow;
  await observe(page, 'list', 0, actual => {
    assert(actual.listVisible && !actual.detailVisible && actual.openDetailDisabled);
    assert.deepEqual(actual.list, recordText(scenario.initialOrder));
    assert.equal(actual.listDomain, scenario.initialOrder.domainState.stateId);
    assert.equal(actual.root['data-active-screen'], flow.listScreenId);
    assert.equal(actual.root['data-selected-order'], '');
    assert.equal(actual.ui, 'idle');
    assert(!actual.simulatedVisible);
  });
  await page.locator('#select-order').click();
  await observe(page, 'selected', 1, actual => {
    assert(actual.listVisible && !actual.detailVisible && !actual.openDetailDisabled);
    assert.equal(actual.selected, `${scenario.initialOrder.orderId} · ${scenario.initialOrder.label} · ${scenario.initialOrder.amount}`);
    assert.equal(actual.root['data-selected-order'], scenario.initialOrder.orderId);
    assert.equal(actual.root['data-active-screen'], flow.listScreenId);
    assert.deepEqual(actual.list, recordText(scenario.initialOrder));
    assert.equal(actual.domain, scenario.initialOrder.domainState.stateId);
  });
  await page.locator('#open-detail').click();
  await observe(page, 'detail', 2, actual => {
    assert(!actual.listVisible && actual.detailVisible && actual.confirmDisabled);
    assert.equal(actual.hash, `#${encodeURIComponent(flow.detailScreenId)}`);
    assert.equal(actual.root['data-active-screen'], flow.detailScreenId);
    assert.equal(actual.root['data-selected-order'], scenario.initialOrder.orderId);
    assert.deepEqual(actual.detail, recordText(scenario.initialOrder));
    assert.equal(actual.domain, scenario.initialOrder.domainState.stateId);
    assert.equal(actual.domainAttributes['data-domain-state'], scenario.initialOrder.domainState.stateId);
    assert.equal(actual.ui, 'idle');
    assert.equal(actual.confirmationOption, '');
  });
  await page.locator('#confirmation-option').selectOption(source.powertypeVariantId);
  await observe(page, 'confirmation-ready', 3, actual => {
    assert(actual.detailVisible && !actual.confirmDisabled);
    assert.equal(actual.confirmationOption, source.powertypeVariantId);
    assert.equal(actual.domain, scenario.initialOrder.domainState.stateId);
    assert.equal(actual.uiAttributes['data-ui-state'], 'idle');
  });
  await page.locator('#confirm-order').click();
  await observe(page, 'simulated-success', 4, actual => {
    assert(actual.detailVisible && actual.confirmDisabled && actual.simulatedVisible);
    assert.equal(actual.simulatedText, 'Simulated success');
    assert.deepEqual(actual.detail, recordText(scenario.successfulOrder));
    assert.equal(actual.domain, scenario.successfulOrder.domainState.stateId);
    assert.equal(actual.domainAttributes['data-domain-state'], scenario.successfulOrder.domainState.stateId);
    assert.equal(actual.ui, scenario.successUiState);
    assert.equal(actual.uiAttributes['data-ui-state'], scenario.successUiState);
  });
  await page.locator('a[href="review.html"]').click();
  await page.waitForURL(`${baseUrl}/review.html`);
  const actualReview = await page.evaluate(() => ({
    title: document.querySelector('h1').textContent,
    headings: [...document.querySelectorAll('h2,h3')].map(element => element.textContent),
    overview: document.querySelector('section[aria-labelledby="overview"]').textContent,
    scenario: Object.fromEntries([...document.querySelectorAll('[data-scenario-field]')]
      .map(element => [element.dataset.scenarioField, element.textContent])),
    scripts: document.querySelectorAll('script').length,
    forms: document.querySelectorAll('form').length
  }));
  assert(actualReview.overview.includes('Candidate preview') && actualReview.overview.includes('candidate (not accepted)'));
  for (const heading of ['Navigation and reachability', 'Logical Screens', 'Component semantic bindings',
    'Constraints, validation authority, and feedback associations', 'StateMachine transition admission evidence',
    'Review diagnostics', 'Compact simulated scenario']) assert(actualReview.headings.includes(heading), heading);
  assert.deepEqual(actualReview.scenario, {
    'fixture-id': scenario.fixtureId, 'coverage-id': scenario.coverageId, profile: source.profile,
    'detail-placement': source.detailPlacement, 'ui-use-case': `${flow.step.uiUseCase.layer}:${flow.step.uiUseCase.id}`,
    'ui-step': flow.step.stepId, 'ui-path': flow.step.path, 'list-screen': flow.listScreenId,
    'list-entry': flow.listEntryInteractionId, navigation: flow.navigationInteractionId, 'detail-screen': flow.detailScreenId,
    selection: flow.selectionInteractionId, command: flow.commandInteractionId, observation: flow.observationInteractionId,
    'operation-binding': bindingText(source.operationBinding), 'state-machine-binding': bindingText(source.stateMachineBinding),
    'powertype-binding': bindingText(source.powertypeBinding), 'powertype-variant': source.powertypeVariantId,
    'initial-order-id': scenario.initialOrder.orderId, 'initial-label': scenario.initialOrder.label,
    'initial-amount': scenario.initialOrder.amount, 'initial-domain-state': scenario.initialOrder.domainState.stateId,
    'result-order-id': scenario.successfulOrder.orderId, 'result-label': scenario.successfulOrder.label,
    'result-amount': scenario.successfulOrder.amount, 'result-domain-state': scenario.successfulOrder.domainState.stateId,
    'success-ui-state': scenario.successUiState
  });
  assert.equal(actualReview.scripts, 0);
  assert.equal(actualReview.forms, 0);
  states.push({ name: 'structural-review', ...actualReview });
  await page.screenshot({ path: path.join(options['--evidence'], screenshots[5]), fullPage: true });
  capturedScreenshots.push(screenshots[5]);
  assert.equal(errors.length, 0, 'Browser errors or rejected requests fail the run');
  clientSuccess = true;
}

const onSigint = () => interrupt('SIGINT');
const onSigterm = () => interrupt('SIGTERM');
process.once('SIGINT', onSigint);
process.once('SIGTERM', onSigterm);
const overallTimer = setTimeout(() => interrupt('Overall 180 second timeout'), 180000);
try {
  options = parseArgs(process.argv.slice(2));
  startupPromise = (async () => {
    const files = await prepareInputs();
    if (interrupted) throw new Error('Interrupted before server startup');
    await startServer(files);
    if (interrupted) throw new Error('Interrupted before browser startup');
    const page = await launchBrowser();
    if (interrupted) throw new Error('Interrupted during browser startup');
    return page;
  })();
  const page = await startupPromise;
  await walkBrowser(page);
} catch (error) {
  errors.push({ type: 'action', message: error.message });
} finally {
  await cleanup();
  clearTimeout(overallTimer);
  process.removeListener('SIGINT', onSigint);
  process.removeListener('SIGTERM', onSigterm);
  let success = readiness && clientSuccess && cleanupSuccess && !interrupted && errors.length === 0;
  if (evidenceReady) {
    try {
      // URL-bearing error text belongs only in the private result, never in portable observations.
      const portableErrors = errors.map(error => ({ type: error.type, message: error.message
        .replace(/https?:\/\/[^\s)]+/g, '[request URL]').replace(/\/(?:Users|Applications|private|var|tmp)\/[^\s)]+/g, '[local path]') }));
      await writeJson('browser-observations.json', { schema: 'cozy.logical-ui-compact-browser.v1',
        fixtureId: source?.scenario.fixtureId, coverageId: source?.scenario.coverageId,
        sourceReceipt: source, browserVersion, viewport: { width: 390, height: 844 },
        observedStates: states, screenshots: capturedScreenshots,
        network: { requests, externalRequests: requests.filter(request => request.origin === 'external').length,
          mutatingRequests: requests.filter(request => request.method !== 'GET').length },
        errors: portableErrors, result: success ? 'PASS' : 'FAIL' });
      await writeJson('browser-result.json', { schema: 'cozy.logical-ui-compact-browser-result.v1',
        runDirectory: options['--evidence'], readiness, clientSuccess, cleanupSuccess,
        processCompletion: 'terminal', pid: process.pid, startedAt, completedAt: new Date().toISOString(), baseUrl,
        artifacts: ['server-process.json', 'browser-observations.json', ...capturedScreenshots],
        errors, result: success ? 'PASS' : 'FAIL' });
    } catch (error) {
      success = false;
      process.stderr.write(`Evidence write failed: ${error.message}\n`);
    }
  }
  process.stdout.write(JSON.stringify({ readiness, clientSuccess, cleanupSuccess,
    processCompletion: 'terminal', result: success ? 'PASS' : 'FAIL' }) + '\n');
  if (!success) {
    process.stderr.write(errors.map(error => `${error.type}: ${error.message}`).join('\n') + '\n');
    process.exitCode = 1;
  }
}
