#!/usr/bin/env node
// Test-only unchanged-option observer. @since/@version Oct. 12, 2026; ASAMI, Tomoharu.
import fs from 'node:fs';
import path from 'node:path';
import {spawnSync} from 'node:child_process';

const args = process.argv.slice(2);
const runs = ['cold', 'warm01', 'warm02', 'warm03'];
const cases = ['mixed-standard', 'mixed-lightweight', 'docker-standard', 'docker-lightweight', 'mixed-override'];

function forward(result) {
  if (result.error) throw result.error;
  if (result.signal) {
    process.kill(process.pid, result.signal);
    // If a signal handler intercepts termination, do not accidentally return success.
    process.exit(1);
  }
  if (!Number.isInteger(result.status)) throw new Error('Real Node returned no exit or signal');
  process.exit(result.status);
}

function replaceOnce(source, before, after) {
  if (source.split(before).length !== 2) {
    throw new Error(`Missing or ambiguous current producer anchor: ${before}`);
  }
  return source.replace(before, after);
}

function metadata(root) {
  const run = JSON.parse(fs.readFileSync(path.join(root, 'quality-tools/run.json'), 'utf8'));
  if (run.schema !== 'cozy.quality-run.v1' || !runs.includes(run.run) || !cases.includes(run.case) ||
      run.part !== 'dialogue' || (root !== '/workspace' && fs.realpathSync(run.projectRoot) !== root) ||
      !run.projectRoot.endsWith(`/target/phase718/quality-media03/work/${run.case}`)) {
    throw new Error('Invalid literal benchmark case/run');
  }
  // projectRoot is host metadata; Docker resolves the same file through /workspace.
  if (typeof run.projectRoot !== 'string' || !path.isAbsolute(run.projectRoot)) {
    throw new Error('Missing absolute host projectRoot metadata');
  }
  return run;
}

function transform(source, token) {
  const helpers = `
const benchmarkRun = JSON.parse(fs.readFileSync(path.join(projectRoot, 'quality-tools/run.json'), 'utf8'));
if (benchmarkRun.schema !== 'cozy.quality-run.v1' ||
    !['mixed-standard', 'mixed-lightweight', 'docker-standard', 'docker-lightweight', 'mixed-override'].includes(benchmarkRun.case) ||
    !['cold', 'warm01', 'warm02', 'warm03'].includes(benchmarkRun.run)) throw new Error('Invalid benchmark run');
const benchmarkInvocation = ${JSON.stringify(token)};
const benchmarkFile = path.join(projectRoot, 'quality-observations', benchmarkRun.run, 'remotion-events.jsonl');
const benchmarkMilestones = new Set();
let benchmarkProgressCallbacks = 0;
const benchmarkObserve = (stage, event, values = {}) => {
  fs.appendFileSync(benchmarkFile, JSON.stringify({schema: 'cozy.quality-remotion-event.v1',
    case: benchmarkRun.case, run: benchmarkRun.run, part: 'dialogue', invocation: benchmarkInvocation,
    stage, event, monotonic_ns: process.hrtime.bigint().toString(), wall_ms: Date.now(),
    pid: process.pid, ...values}) + '\\n');
};
const benchmarkMeasure = async (stage, action) => {
  const start = process.hrtime.bigint();
  benchmarkObserve(stage, 'begin', {start_ns: start.toString()});
  try {
    const value = await action();
    const end = process.hrtime.bigint();
    const actual = {};
    if (Number.isSafeInteger(value?.frameCount)) actual.frameCount = value.frameCount;
    if (Number.isSafeInteger(value?.durationInFrames)) actual.durationInFrames = value.durationInFrames;
    if (Buffer.isBuffer(value?.buffer)) actual.buffer_bytes = value.buffer.length;
    benchmarkObserve(stage, 'end', {end_ns: end.toString(), duration_ns: (end - start).toString(), success: true,
      progress_callbacks: benchmarkProgressCallbacks, ...actual});
    return value;
  } catch (error) {
    const end = process.hrtime.bigint();
    benchmarkObserve(stage, 'end', {end_ns: end.toString(), duration_ns: (end - start).toString(),
      success: false, error: {name: error?.name, message: error?.message}});
    throw error;
  }
};
const benchmarkMeasureSync = (stage, action) => {
  const start = process.hrtime.bigint();
  benchmarkObserve(stage, 'begin', {start_ns: start.toString()});
  try {
    const value = action();
    const end = process.hrtime.bigint();
    benchmarkObserve(stage, 'end', {end_ns: end.toString(), duration_ns: (end - start).toString(), success: true});
    return value;
  } catch (error) {
    const end = process.hrtime.bigint();
    benchmarkObserve(stage, 'end', {end_ns: end.toString(), duration_ns: (end - start).toString(),
      success: false, error: {name: error?.name, message: error?.message}});
    throw error;
  }
};
const benchmarkProgress = (renderedFrames, encodedFrames, target) => {
  benchmarkProgressCallbacks++;
  const conditions = [['first-positive-rendered', renderedFrames > 0],
    ['first-positive-encoded', encodedFrames > 0], ['full-rendered', renderedFrames >= target],
    ['full-encoded', encodedFrames >= target]];
  for (const [milestone, reached] of conditions) {
    if (reached && !benchmarkMilestones.has(milestone)) {
      benchmarkMilestones.add(milestone);
      benchmarkObserve('render-encode-overlap', 'milestone', {milestone, renderedFrames, encodedFrames,
        frame_target: target, callback_count: benchmarkProgressCallbacks});
    }
  }
};
`;
  source = replaceOnce(source, 'const projectRoot = process.env.COZY_PROJECT_ROOT || process.cwd();',
    'const projectRoot = process.env.COZY_PROJECT_ROOT || process.cwd();' + helpers);
  source = replaceOnce(source, "const props = JSON.parse(fs.readFileSync(propsPath, 'utf8'));",
    "const props = JSON.parse(fs.readFileSync(propsPath, 'utf8'));\nbenchmarkObserve('props', 'observed', {propsPath, settings: {qualityProfile: props.qualityProfile, fps: props.fps, width: props.width, height: props.height, crf: props.crf, x264Preset: props.x264Preset, audioBitrate: props.audioBitrate, origins: props.encodingOrigins}, timing: props.timing, outputPath: props.outputPath});");
  const bundle = `const serveUrl = await bundle({
  entryPoint: entry,
  publicDir,
  webpackOverride: (config) => ({...config, resolve: {...config.resolve,
    modules: [...(config.resolve?.modules || []), ...(cliRequire.resolve.paths('remotion') || [])]}})
});`;
  source = replaceOnce(source, bundle, bundle.replace('await bundle(', "await benchmarkMeasure('bundle', async () => bundle(")
    .replace('\n});', '\n}));'));
  source = replaceOnce(source,
    "const composition = await selectComposition({serveUrl, id: 'CozyVideo', inputProps: props});",
    "const composition = await benchmarkMeasure('select-composition', async () => selectComposition({serveUrl, id: 'CozyVideo', inputProps: props}));");
  const renderFrames = `  const rendered = await renderFrames({composition, serveUrl, inputProps: props, outputDir: frameDir,
    imageFormat: 'png', imageSequencePattern: 'frame-[frame].png', frameRange: [0, frames - 1],
    onFrameUpdate: (renderedFrames) => console.log(\`Rendered \${renderedFrames}/\${frames}\`)});`;
  source = replaceOnce(source, renderFrames,
    renderFrames.replace('await renderFrames(', "await benchmarkMeasure('render-frames', async () => renderFrames(")
      .replace(')});', ')}));'));
  const audioMix = `  await renderMedia({composition, serveUrl, inputProps: props, codec: 'wav', audioCodec: 'pcm-16',
    imageFormat: 'none', enforceAudioTrack: true, outputLocation: audioPath});`;
  source = replaceOnce(source, audioMix,
    audioMix.replace('await renderMedia(', "await benchmarkMeasure('audio-mix', async () => renderMedia(")
      .replace('audioPath});', 'audioPath}));'));
  const media = `const result = await renderMedia({
  composition,
  serveUrl,
  codec: 'h264',
  audioCodec: 'pcm-16',
  inputProps: props,
  crf: props.crf,
  ...(props.x264Preset ? {x264Preset: props.x264Preset} : {}),
  outputLocation: null,
  ffmpegOverride,
  onProgress: ({renderedFrames, encodedFrames}) => console.log(\`Rendered \${renderedFrames}/\${frames}, encoded \${encodedFrames}/\${frames}\`)
});`;
  const progress = "onProgress: ({renderedFrames, encodedFrames}) => console.log(`Rendered ${renderedFrames}/${frames}, encoded ${encodedFrames}/${frames}`)";
  const observedProgress = "onProgress: ({renderedFrames, encodedFrames}) => { benchmarkProgress(renderedFrames, encodedFrames, frames); console.log(`Rendered ${renderedFrames}/${frames}, encoded ${encodedFrames}/${frames}`); }";
  source = replaceOnce(source, media,
    media.replace('await renderMedia(', "await benchmarkMeasure('render-encode-overlap', async () => renderMedia(")
      .replace(progress, observedProgress).replace('\n});', '\n}));'));
  source = replaceOnce(source, 'fs.mkdirSync(path.dirname(output), {recursive: true});\nfs.writeFileSync(output, result.buffer);',
    "benchmarkMeasureSync('media-write', () => {\n  fs.mkdirSync(path.dirname(output), {recursive: true});\n  fs.writeFileSync(output, result.buffer);\n});");
  source = replaceOnce(source,
    "const ffmpegOverride = ({type, args}) => {\n  if (type !== 'pre-stitcher' && type !== 'stitcher') return args;",
    "const ffmpegOverride = ({type, args}) => {\n  benchmarkObserve('ffmpeg-override', 'input', {type, args});\n  if (type !== 'pre-stitcher' && type !== 'stitcher') {\n    benchmarkObserve('ffmpeg-override', 'output', {type, args});\n    return args;\n  }");
  source = replaceOnce(source,
    "    result.splice(result.length - 1, 0, '-f', 'mp4');\n    return result;",
    "    result.splice(result.length - 1, 0, '-f', 'mp4');\n    benchmarkObserve('ffmpeg-override', 'output', {type, args: result});\n    return result;");
  source = replaceOnce(source,
    "  result.splice(result.length - 1, 0, '-movie_timescale', '48000', '-f', 'mp4');\n  return result;",
    "  result.splice(result.length - 1, 0, '-movie_timescale', '48000', '-f', 'mp4');\n  benchmarkObserve('ffmpeg-override', 'output', {type, args: result});\n  return result;");
  return source;
}

// Docker final assembly uses this same source with a basename-selected proxy.
if (path.basename(process.argv[1]) === 'ffmpeg-observed.mjs') {
  const root = fs.realpathSync(process.cwd());
  if (root !== '/workspace') throw new Error('Docker FFmpeg observer requires /workspace cwd');
  const run = metadata(root);
  const token = `${process.pid}-${Date.now()}-${process.hrtime.bigint()}`;
  const output = path.join(root, 'quality-observations', run.run, 'ffmpeg-events.jsonl');
  const start = process.hrtime.bigint();
  const emit = (event, values) => fs.appendFileSync(output, JSON.stringify({
    schema: 'cozy.quality-ffmpeg-event.v1', case: run.case, run: run.run, part: 'assembly',
    invocation: token, stage: 'assembly-subcall', event, pid: process.pid,
    monotonic_ns: process.hrtime.bigint().toString(), real_argv: ['ffmpeg', ...args], cwd: root, ...values}) + '\n');
  emit('begin', {start_ns: start.toString()});
  const result = spawnSync('ffmpeg', args, {stdio: 'inherit'});
  const end = process.hrtime.bigint();
  emit('end', {end_ns: end.toString(), duration_ns: (end - start).toString(),
    exit_code: result.status, signal: result.signal, success: !result.error && result.status === 0 && !result.signal,
    error: result.error ? {name: result.error.name, message: result.error.message} : null});
  forward(result);
}

// Version checks forward without producer instrumentation.
if (!args[0] || path.basename(args[0]) !== 'render.mjs') {
  forward(spawnSync('node', args, {stdio: 'inherit'}));
}

const root = fs.realpathSync(process.cwd());
const script = fs.realpathSync(path.resolve(root, args[0]));
const directory = fs.realpathSync(path.join(root, 'target/cozy-video/remotion/dialogue/src'));
if (path.dirname(script) !== directory || path.basename(script) !== 'render.mjs') {
  throw new Error('Producer render path escapes the current dialogue workspace');
}
const run = metadata(root);
const token = `${process.pid}-${Date.now()}-${process.hrtime.bigint()}`;
const observation = path.join(root, 'quality-observations', run.run, 'remotion-events.jsonl');
const emit = (event, values = {}) => fs.appendFileSync(observation, JSON.stringify({
  schema: 'cozy.quality-remotion-event.v1', case: run.case, run: run.run, part: 'dialogue', invocation: token,
  stage: 'node-invocation', event, monotonic_ns: process.hrtime.bigint().toString(),
  wall_ms: Date.now(), pid: process.pid, ...values}) + '\n');
const sibling = path.join(directory, 'render-observed.mjs');
const start = process.hrtime.bigint();
emit('begin', {start_ns: start.toString(), original_argv: args, real_argv: ['node', sibling, ...args.slice(1)],
  original_script: script, observed_script: sibling, cwd: root});
let child;
try {
  fs.writeFileSync(sibling, transform(fs.readFileSync(script, 'utf8'), token), 'utf8');
  child = spawnSync('node', [sibling, ...args.slice(1)], {stdio: 'inherit'});
  const end = process.hrtime.bigint();
  emit('end', {end_ns: end.toString(), duration_ns: (end - start).toString(), exit_code: child.status, signal: child.signal,
    success: !child.error && child.status === 0 && !child.signal,
    error: child.error ? {name: child.error.name, message: child.error.message} : null});
} catch (error) {
  emit('end', {success: false, error: {name: error.name, message: error.message}});
  throw error;
}
forward(child);
