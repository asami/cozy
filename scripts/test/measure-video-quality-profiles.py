#!/usr/bin/env -S uv run --managed-python --no-python-downloads --script
# /// script
# requires-python = ">=3.12"
# dependencies = []
# ///
"""P718-03 private fixture preparation and factual media measurement.

Run through the parent-selected runtime runner. Only inspect starts subprocesses,
and those are explicit ffprobe/FFmpeg argv. This helper never invokes Cozy.
@since/@version Oct. 12, 2026; @author ASAMI, Tomoharu
"""

import argparse
import array
from fractions import Fraction
import html
import json
import math
from pathlib import Path
import re
import shutil
import statistics
import subprocess
import sys
import time
import wave

REPOSITORY = Path(__file__).resolve().parents[2]
TARGET = REPOSITORY / 'target/phase718/quality-media03'
SOURCE = REPOSITORY / 'target/phase717-integration/work/simplemodeling-org/src/main/media/development-process/ai-development-harness/video/ja'
RUNS = ('cold', 'warm01', 'warm02', 'warm03')
CASES = ('mixed-standard', 'mixed-lightweight', 'docker-standard', 'docker-lightweight', 'mixed-override')
SCENES = ('opening', 'speed', 'business-wall', 'convergence', 'harness-definition',
          'broad-concept', 'responsibility-boundary', 'technology-context', 'series-roadmap')
FILES = ('video.yaml', 'storyboard.md', 'assets/opening.svg', 'assets/section-start.svg',
         'assets/summary.svg', 'assets/final-page.svg', '.cozy/video/credit-profiles/simplemodeling-org.yaml',
         'build/audio/dialogue/manifest.json', *(f'build/audio/dialogue/{i:02d}-{scene}.wav' for i, scene in enumerate(SCENES, 1)))
IMAGE = 'ghcr.io/asami/textus-toolchain:0.2.1-SNAPSHOT'
FIELDS = ('qualityProfile', 'fps', 'width', 'height', 'crf', 'x264Preset', 'audioBitrate')
CACHE = {'cold': 'First actual render per active project; unmeasured warmup for acceptance.',
         'warm': 'Three paired subsequent renders in that same project/caches; no cleaning.',
         'process': 'Separate Cozy/Node/browser commands; no process-hot or system-cold claim.'}
LIMITS = ['Native commandExecution duration includes executor/lifecycle bookkeeping; excludes agent creation/queue.',
          'Observer and I/O overhead included; no subtraction across clock domains.',
          'Docker progress/encode windows overlap; no pure encode or additive-window claim.',
          'Two identical actual assembly inputs; the second is copied, not independently rendered.',
          'Pixel/decode/waveform checks cannot establish human hearing, layout usability or Step acceptance.']


def require(condition, message):
    if not condition:
        raise ValueError(message)


def owned(path):
    path = Path(path).absolute()
    require(path.is_relative_to(TARGET) and path.resolve() == path and path != TARGET,
            f'Expected canonical private target path: {path}')
    return path


def file(path):
    path = Path(path)
    require(path.is_file() and path.stat().st_size > 0, f'Missing nonempty file: {path}')
    return path


def read(path):
    return json.loads(file(path).read_text(encoding='utf-8'))


def write(path, value):
    path = owned(path)
    path.parent.mkdir(parents=True, exist_ok=True)
    with path.open('x', encoding='utf-8') as output:
        output.write(json.dumps(value, indent=2, ensure_ascii=False, allow_nan=False) + '\n')


def settings(case):
    standard = case.endswith('standard')
    values = dict(zip(FIELDS, ('standard', 18, 1280, 720, 32, None, '128k') if standard else
                      ('lightweight', 12, 854, 480, 36, 'superfast', '96k')))
    if case == 'mixed-override':
        values.update(fps=15, width=960, height=540, crf=28, x264Preset='veryfast', audioBitrate='192k')
    values['origins'] = {key: 'explicit' if case == 'mixed-override' and key != 'qualityProfile'
                       else 'quality-profile' for key in FIELDS}
    return values


def identity(case, run):
    require(case in CASES and run in RUNS and (case != 'mixed-override' or run == 'cold'), 'Frozen case/run required')
    return owned(TARGET / 'work' / case)


def outputs(run):
    return {kind: f'build/quality/{run}/{kind}.mp4' for kind in ('dialogue', 'dialogue-repeat', 'final')}


def logical_argv(project, kind):
    base = ['--runtime-dev-dir', str(REPOSITORY), 'video', 'render' if kind == 'render' else 'build', str(project / 'video.yaml')]
    return base + (['--renderer', 'remotion', '--part', 'dialogue'] if kind == 'render' else ['--mode', 'final']) + ['--check-tools']


def descriptor(original, case, run):
    # The selected fixture has one root renderer and one part, with no YAML folding
    # in these blocks. Refuse an unfamiliar descriptor instead of guessing YAML.
    match = re.search(r'^renderer:\n((?:[ ]{2}[^\n]*\n)+)(?=parts:\n)', original, re.M)
    require(match is not None and len(re.findall(r'^renderer:', original, re.M)) == 1, 'Unique fixture root renderer required')
    block = re.sub(r'^  (?:fps|width|height|policy|qualityProfile):[^\n]*\n', '', match.group(1), flags=re.M)
    block += f'  qualityProfile: {settings(case)["qualityProfile"]}\n'
    if case == 'mixed-override':
        block += ''.join(f'  {key}: {settings(case)[key]}\n' for key in FIELDS if key != 'qualityProfile')
    text = original[:match.start(1)] + block + original[match.end(1):]
    require(text.count('\noutput: ') == 1 and text.count('  - id: dialogue\n') == 1
            and text.count('    output: build/parts/dialogue.mp4\n') == 1, 'Selected fixture output/part anchors differ')
    text = re.sub(r'^output: .*$', f'output: {outputs(run)["final"]}', text, flags=re.M)
    text = text.replace('    output: build/parts/dialogue.mp4\n', f'    output: {outputs(run)["dialogue"]}\n')
    require(re.findall(r'^  - id: (.+)$', text, re.M) == ['dialogue'], 'Exactly one direct fixture part required')
    return text


def prepare(case, run):
    project = identity(case, run)
    runpath = owned(project / 'quality-tools/run.json')
    if not project.exists():
        require(run == 'cold', 'Cold preparation must precede warm runs')
        audio = read(SOURCE / 'build/audio/dialogue/manifest.json')
        require([item['sceneId'] for item in audio] == list(SCENES), 'Selected nine-scene WAV manifest differs')
        for relative in FILES:
            destination = owned(project / relative)
            destination.parent.mkdir(parents=True, exist_ok=True)
            shutil.copyfile(file(SOURCE / relative), destination)
        for source, name in (('quality-profile-node-observed.mjs', 'node-observed.mjs'),
                             ('quality-profile-node-observed.mjs', 'ffmpeg-observed.mjs'),
                             ('quality-profile-ffmpeg-observed.py', 'ffmpeg-observed.py')):
            destination = owned(project / 'quality-tools' / name)
            destination.parent.mkdir(parents=True, exist_ok=True)
            shutil.copyfile(file(REPOSITORY / 'scripts/test' / source), destination)
            destination.chmod(0o755)
    else:
        previous = read(runpath)
        require(previous['case'] == case and RUNS.index(run) == RUNS.index(previous['run']) + 1,
                'Advance one run in the same project; no repeated cold or cache reset')
        require(file(project / outputs(previous['run'])['final']) is not None, 'Actual preceding final output required')
    for relative in outputs(run).values():
        require(not owned(project / relative).exists(), 'Selected output already exists; never overwrite an actual run')
    owned(project / 'video.yaml').write_text(descriptor(file(SOURCE / 'video.yaml').read_text(), case, run), encoding='utf-8')
    ffmpeg = {'mode': 'docker'} if case.startswith('docker-') else {
        'mode': 'host', 'executable': str(project / 'quality-tools/ffmpeg-observed.py')}
    config = {'video': {'tools': {'remotion': {'mode': 'docker', 'executable': '/workspace/quality-tools/node-observed.mjs', 'docker-image': IMAGE},
                                 'ffmpeg': ffmpeg, 'ffprobe': {'mode': 'host', 'executable': '/opt/homebrew/bin/ffprobe'}},
                        'docker-image': IMAGE}}
    if not case.startswith('docker-'):
        config['video']['encoder'] = {'name': 'libx264'}
    configpath = owned(project / '.cozy/config.json')
    configpath.write_text(json.dumps(config, indent=2) + '\n', encoding='utf-8')
    metadata = {'schema': 'cozy.quality-run.v1', 'case': case, 'run': run, 'part': 'dialogue',
                'projectRoot': str(project), 'settings': settings(case), 'outputs': outputs(run),
                'sceneIds': list(SCENES), 'fixtureFiles': list(FILES), 'image': IMAGE, 'cachePolicy': CACHE}
    runpath.write_text(json.dumps(metadata, indent=2) + '\n', encoding='utf-8')
    owned(project / f'quality-observations/{run}').mkdir(parents=True, exist_ok=False)
    write(TARGET / 'selections' / f'{case}-{run}.json', metadata)
    commands = [{'case': case, 'run': run, 'kind': kind, 'working_directory': str(project),
                 'logical_argv': logical_argv(project, kind)} for kind in ('render', 'final-build')]
    write(TARGET / 'commands' / f'{case}-{run}.json', {'commands': commands,
          'between_commands': f'prepare-final --case {case} --run {run}', 'producer_execution': 'pending'})
    return {'project': str(project), 'commands': commands, 'producer_execution': 'pending'}


def prepare_final(case, run):
    project = identity(case, run)
    metadata = read(project / 'quality-tools/run.json')
    require(metadata['case'] == case and metadata['run'] == run and metadata['projectRoot'] == str(project),
            'Actual current run metadata differs')
    source, destination = (owned(project / outputs(run)[kind]) for kind in ('dialogue', 'dialogue-repeat'))
    file(source)
    props = read(owned(project / 'target/cozy-video/remotion/dialogue/props.json'))
    validate_settings(encoding(props), case)
    require(props['partId'] == 'dialogue' and props['outputPath'] == outputs(run)['dialogue']
            and [scene['id'] for scene in props['scenes']] == list(SCENES)
            and type(props['timing']['finalPageHoldFrames']) is int and props['timing']['finalPageHoldFrames'] > 0,
            'Current complete direct props required before final preparation')
    descriptorpath = owned(project / 'video.yaml')
    text = file(descriptorpath).read_text(encoding='utf-8')
    require(re.findall(r'^  - id: (.+)$', text, re.M) == ['dialogue']
            and text.count(f'    output: {outputs(run)["dialogue"]}\n') == 1
            and re.findall(r'^output: (.+)$', text, re.M) == [outputs(run)['final']], 'Current single-part descriptor required')
    part = text[text.index('  - id: dialogue\n'):]
    repeated = part.replace('  - id: dialogue\n', '  - id: dialogue-repeat\n', 1).replace(
        f'    output: {outputs(run)["dialogue"]}\n', f'    output: {outputs(run)["dialogue-repeat"]}\n', 1)
    configpath = owned(project / '.cozy/config.json')
    config = read(configpath)
    require(not destination.exists(), 'Repeated input already exists')
    shutil.copyfile(source, destination)
    descriptorpath.write_text(text + repeated, encoding='utf-8')
    if case.startswith('docker-'):
        config['video']['tools']['ffmpeg'] = {'mode': 'docker', 'executable': '/workspace/quality-tools/ffmpeg-observed.mjs'}
        configpath.write_text(json.dumps(config, indent=2) + '\n', encoding='utf-8')
    return {'copied_actual_input': str(source), 'repeated_input': str(destination), 'independent_second_render': False}


def native_command(entry, project, kind):
    require(entry['logical_argv'] == logical_argv(project, kind), 'Exact admitted logical argv differs')
    request, result = read(entry['request_locator']), read(entry['result_locator'])
    require(request.get('schema') == 'cozy.exact-cli-command.v5' and result.get('schema') == 'cozy.exact-cli-command-result.v6', 'Current native CLI schemas required')
    execution = request.get('execution_identity')
    require(execution and result.get('execution_identity') == execution and
            request.get('argv') == result.get('argv') == [request['executable'], *entry['logical_argv']] and
            request.get('working_directory') == result.get('working_directory') == str(project), 'Native request/result identity/argv/cwd differs')
    require(result.get('exit_code') == 0 and result.get('timed_out') is False and result.get('command_completion') == 'terminal'
            and result.get('process_group_completion') in ('group-gone', 'not-checked') and entry.get('owned_group_gone') is True,
            'Actual successful terminal command/group admission required')
    history = entry.get('commandExecution', {})
    require(isinstance(history.get('id'), str) and history['id'] and isinstance(history.get('command'), str)
            and history['command'] and history['command'] == entry.get('accepted_native_command')
            and entry.get('native_execution_identity') == execution, 'Parent matched current native commandExecution required')
    duration = history.get('durationMs')
    require(type(duration) in (float, int) and math.isfinite(duration) and duration > 0, 'Required native durationMs absent/invalid; mtime is not a substitute')
    return {'execution_identity': execution, 'logical_argv': entry['logical_argv'], 'commandExecution': history,
            'native_wall_seconds': duration / 1000, 'request_locator': entry['request_locator'], 'result_locator': entry['result_locator']}


def trace(path, case, run, schema):
    rows = [json.loads(line) for line in file(path).read_text().splitlines()]
    require(rows and all(row.get('schema') == schema and row.get('case') == case and row.get('run') == run
                        and row.get('invocation') and int(row['monotonic_ns']) > 0 for row in rows), 'Trace case/run/schema mismatch')
    return rows


def pair(rows, stage):
    starts = [row for row in rows if row.get('stage') == stage and row.get('event') == 'begin']
    ends = [row for row in rows if row.get('stage') == stage and row.get('event') == 'end']
    require(len(starts) == len(ends) == 1, f'Unique actual {stage} interval required')
    begin, end = starts[0], ends[0]
    require(begin['invocation'] == end['invocation'] and end.get('success') is True and not end.get('signal')
            and end.get('exit_code', 0) == 0, f'Failed/incomplete {stage}')
    duration = int(end['end_ns']) - int(begin['start_ns'])
    require(duration > 0 and duration == int(end['duration_ns']), f'Invalid monotonic {stage} interval')
    return {'stage': stage, 'duration_seconds': duration / 1e9, 'invocation': begin['invocation'],
            'begin': begin, 'end': end}


def validate_settings(actual, case):
    expected = settings(case)
    require({key: actual.get(key) for key in expected} == expected, 'Current resolved profile/fields/seven origins differ')
    return expected


def encoding(record):
    return {**{key: record.get(key) for key in FIELDS}, 'origins': record.get('origins', record.get('encodingOrigins'))}


def option(argv, key):
    values = [argv[index + 1] for index, value in enumerate(argv[:-1]) if value == key]
    require(len(values) == 1, f'Exactly one actual {key} required')
    return values[0]


def final_artifacts(project, run, final):
    require(final.get('schema') == 'cozy.video.final.v2' and final.get('status') == 'validated'
            and final.get('mode') == 'final' and final['output']['path'] == outputs(run)['final'],
            'Actual current final.v2 output required')
    parts, artifacts = final['buildSettings']['parts'], final['partArtifacts']
    ids = ['dialogue', 'dialogue-repeat']
    require([part['partId'] for part in parts] == [artifact['partId'] for artifact in artifacts] == ids,
            'Exactly ordered current final parts/artifacts required')
    paths = []
    artifactroot = owned(project / 'target/cozy-video/final/part-artifacts')
    for partid, part, artifact in zip(ids, parts, artifacts):
        require(part['outputPath'] == artifact['output']['path'] == outputs(run)[partid], 'Current relative part output differs')
        relative = Path(artifact['manifest']['path'])
        require(not relative.is_absolute() and '..' not in relative.parts, 'Safe relative artifact manifest required')
        path = owned(project / relative)
        require(path.is_relative_to(artifactroot) and path != artifactroot and path not in paths,
                'Unique declared artifact manifest under final/part-artifacts required')
        record = read(path)
        require(record.get('schema') == 'cozy.video.storyboard-part-artifact.v2' and record.get('status') == 'validated'
                and record.get('mode') == 'final' and record.get('partId') == partid
                and record['output']['path'] == outputs(run)[partid], 'Declared current part artifact differs')
        paths.append(path)
    return paths


def collect(indexpath):
    index = read(indexpath)
    case, run = index['case'], index['run']
    project = identity(case, run)
    metadata = read(TARGET / 'selections' / f'{case}-{run}.json')
    require(metadata['projectRoot'] == str(project), 'Current selection project mismatch')
    require([entry['kind'] for entry in index['entries']] == ['render', 'final-build'] and
            all(entry.get('case') == case and entry.get('run') == run for entry in index['entries']), 'One exact current render/final pair required')
    commands = {entry['kind']: native_command(entry, project, entry['kind']) for entry in index['entries']}
    propspath = owned(project / 'target/cozy-video/remotion/dialogue/props.json')
    partpath = owned(project / f'build/quality/{run}/dialogue.manifest.json')
    finalpath = owned(project / 'target/cozy-video/final/manifest.json')
    props, part, final = read(propspath), read(partpath), read(finalpath)
    selected = validate_settings(encoding(props), case)
    for record in (part, final):
        validate_settings(encoding(record['encoding']), case)
    require(props['outputPath'] == outputs(run)['dialogue'] and props['partId'] == 'dialogue'
            and [scene['id'] for scene in props['scenes']] == list(SCENES), 'Current props output/part/input scene mismatch')
    artifactpaths = final_artifacts(project, run, final)
    directory = owned(project / f'quality-observations/{run}')
    node = trace(directory / 'remotion-events.jsonl', case, run, 'cozy.quality-remotion-event.v1')
    nodeinterval = pair(node, 'node-invocation')
    require(all(row['invocation'] == nodeinterval['invocation'] and row.get('part') == 'dialogue' for row in node), 'Ambiguous/foreign render trace')
    observed = [row for row in node if row['stage'] == 'props' and row['event'] == 'observed']
    require(len(observed) == 1 and observed[0]['settings'] == selected and observed[0]['timing'] == props['timing']
            and observed[0]['outputPath'] == outputs(run)['dialogue'], 'Invocation actual props mismatch')
    docker = case.startswith('docker-')
    names = ('bundle', 'select-composition', 'render-encode-overlap', 'media-write') if docker else (
        'bundle', 'select-composition', 'render-frames', 'audio-mix')
    stages = [pair(node, name) for name in names]
    ffmpeg = trace(directory / 'ffmpeg-events.jsonl', case, run, 'cozy.quality-ffmpeg-event.v1')
    groups = {}
    for row in ffmpeg:
        groups.setdefault(row['invocation'], []).append(row)
    calls = [pair(rows, rows[0]['stage']) for rows in groups.values()]
    calls = [call for call in calls if call['stage'] != 'tool-check']
    assembly = [call for call in calls if call['stage'] == 'assembly-subcall']
    argv = [call['begin']['real_argv'] for call in assembly]
    pcm = [args for args in argv if '-c:a' in args and option(args, '-c:a') == 'pcm_s16le']
    finalaac = [args for args in argv if '-c:a' in args and option(args, '-c:a') == 'aac']
    require(len(pcm) == 2 and len(finalaac) == 1 and option(finalaac[0], '-b:a') == selected['audioBitrate']
            and option(finalaac[0], '-ar') == '48000' and option(finalaac[0], '-ac') == '2'
            and option(finalaac[0], '-cutoff') == '18000', 'Actual independent PCM decode and one selected final AAC required')
    hostencode = [call for call in calls if call['stage'] == 'dialogue-encode']
    callbackargs = [row for row in node if row['stage'] == 'ffmpeg-override' and row['event'] == 'output']
    if docker:
        require(not hostencode and any(row.get('type') == 'pre-stitcher' for row in callbackargs), 'Ordinary Docker route differs')
        stitcher = [row['args'] for row in callbackargs if row.get('type') == 'stitcher']
        require(stitcher and all(option(args, '-b:a') == selected['audioBitrate'] for args in stitcher), 'Actual selected Docker stitcher bitrate required')
    else:
        require(len(hostencode) == 1, 'One actual host direct encode required')
        args = hostencode[0]['begin']['real_argv']
        require(option(args, '-c:v') == 'libx264' and option(args, '-crf') == str(selected['crf'])
                and option(args, '-b:a') == selected['audioBitrate'], 'Comparable software encoder settings differ')
        if selected['x264Preset']:
            require(option(args, '-preset') == selected['x264Preset'], 'Current explicit preset missing')
    movies = {kind: {'path': str(file(owned(project / outputs(run)[kind]))),
                     'bytes': (project / outputs(run)[kind]).stat().st_size} for kind in ('dialogue', 'final')}
    # Preserve current producer files before next warm preparation can replace them.
    archive = owned(TARGET / 'observations' / f'{case}-{run}')
    archive.mkdir(parents=True, exist_ok=False)
    for path in (propspath, partpath, finalpath):
        shutil.copyfile(path, owned(archive / path.name))
    artifactlocators = []
    for partid, path in zip(('dialogue', 'dialogue-repeat'), artifactpaths):
        destination = owned(archive / 'part-artifacts' / f'{partid}.json')
        destination.parent.mkdir(parents=True, exist_ok=True)
        shutil.copyfile(path, destination)
        artifactlocators.append({'partId': partid, 'producer_locator': str(path), 'archived_locator': str(destination)})
    row = {'schema': 'cozy.quality-measurement.v1', 'case': case, 'run': run, 'route': 'docker' if docker else 'mixed',
           'encoder': 'remotion-renderMedia-h264' if docker else 'libx264', 'image': IMAGE,
           'input': {'fixtureFiles': list(FILES), 'sceneIds': list(SCENES), 'source': str(SOURCE)},
           'project': str(project), 'cachePolicy': CACHE, 'settings': selected, 'commands': commands,
           'stages': stages, 'host_encode': hostencode, 'assembly_subcalls': assembly,
           'docker_callback_args': callbackargs, 'progress_windows': progress_windows(node) if docker else None,
           'props_locator': str(archive / propspath.name), 'part_manifest_locator': str(archive / partpath.name),
           'final_manifest_locator': str(archive / finalpath.name), 'part_artifact_locators': artifactlocators, 'outputs': movies,
           'native_total_seconds': sum(command['native_wall_seconds'] for command in commands.values()),
           'technical_status': 'inspection-pending', 'acceptance': 'parent-pending', 'limitations': LIMITS}
    write(TARGET / 'measurements' / f'{case}-{run}.json', row)
    return row


def progress_windows(rows):
    interval = pair(rows, 'render-encode-overlap')
    milestones = {row['milestone']: row for row in rows if row.get('event') == 'milestone'}
    windows = {'overlap': True, 'disjoint_sum_seconds': None, 'pure_encode_seconds': None}
    for name, begin, end in (('frame_render', interval['begin'], milestones.get('full-rendered')),
                             ('encoding', milestones.get('first-positive-encoded'), milestones.get('full-encoded'))):
        windows[name] = None if begin is None or end is None else {
            'begin_ns': begin['monotonic_ns'], 'end_ns': end['monotonic_ns'],
            'seconds': (int(end['monotonic_ns']) - int(begin['monotonic_ns'])) / 1e9}
    windows['completion_upper_bound'] = None if 'first-positive-encoded' not in milestones else {
        'seconds': (int(interval['end']['monotonic_ns']) - int(milestones['first-positive-encoded']['monotonic_ns'])) / 1e9,
        'label': 'First positive encoding callback to renderMedia completion; not pure encode.'}
    return windows


def process(argv, directory, label, commands):
    stdout, stderr = owned(directory / f'{label}.stdout'), owned(directory / f'{label}.stderr')
    record = {'argv': argv, 'stdout': str(stdout), 'stderr': str(stderr), 'exit_code': None, 'timeout': False}
    commands.append(record)
    start = time.monotonic_ns()
    try:
        with stdout.open('xb') as out, stderr.open('xb') as err:
            completed = subprocess.run(argv, shell=False, stdout=out, stderr=err, timeout=600)
        record['exit_code'] = completed.returncode
    except subprocess.TimeoutExpired:
        record['timeout'] = True
    except OSError as error:
        record['error'] = str(error)
    record['observer_duration_seconds'] = (time.monotonic_ns() - start) / 1e9
    record['stdout_bytes'], record['stderr_bytes'] = stdout.stat().st_size, stderr.stat().st_size
    require(record['exit_code'] == 0 and not record['timeout'], f'Failed native inspection {label}; logs retained')
    return stdout


def pcm(raw):
    require(len(raw) % 2 == 0, 'Truncated signed16 PCM')
    result = array.array('h')
    result.frombytes(raw)
    if sys.byteorder != 'little':
        result.byteswap()
    return result


def correspondence(source, decoded, offset, begin=0, end=None):
    end = len(source) if end is None else end
    require(0 <= begin < end <= len(source) and offset + begin >= 24 and offset + end + 24 <= len(decoded), 'Source window underrun')
    indices = range(begin, end, 48)
    sourcepower = sum(source[index] ** 2 for index in indices)
    require(sourcepower > 0, 'Source window has no energy')
    scores = []
    for lag in range(-24, 25):
        power = sum(decoded[offset + lag + index] ** 2 for index in indices)
        product = sum(source[index] * decoded[offset + lag + index] for index in indices)
        scores.append({'lag_samples': lag, 'score': product / math.sqrt(sourcepower * power) if power else 0})
    return {**max(scores, key=lambda item: item['score']), 'source_begin_sample': begin, 'source_end_sample': end,
            'output_offset_sample': offset, 'stride_samples': 48, 'perceptual_quality': None}


def narration(project, props, decoded, copies):
    require('rendererTemplate' in props, 'Explicit current rendererTemplate required')
    template = props['rendererTemplate']
    require(template is None or template in ('cozy-character-dialogue-v1', 'cozy-character-web-demo-v1'),
            'Unknown current rendererTemplate')
    applylead = template is not None
    policy = 'scene-start-plus-lead-in' if applylead else 'generic-scene-start'
    accumulated = 0
    for scene in props['scenes']:
        require(type(scene['startFrame']) is int and scene['startFrame'] == accumulated
                and type(scene['durationFrames']) is int and scene['durationFrames'] > 0,
                'Current scene allocations must be positive and contiguous')
        accumulated += scene['durationFrames']
    manifest = read(project / 'build/audio/dialogue/manifest.json')
    require([entry['sceneId'] for entry in manifest] == [scene['id'] for scene in props['scenes']] == list(SCENES), 'Current narration order mismatch')
    observations = []
    for copy in range(copies):
        for entry, scene in zip(manifest, props['scenes']):
            require(Path(entry['file']).name == entry['file'] and scene['audioPath'] == f'audio/{entry["file"]}', 'Narration file mapping differs')
            sourcepath = file(project / 'build/audio/dialogue' / entry['file'])
            with wave.open(str(sourcepath), 'rb') as audio:
                require(audio.getframerate() == 24000 and audio.getnchannels() == 1 and audio.getsampwidth() == 2
                        and audio.getcomptype() == 'NONE', 'Retained lossless mono24k PCM16 WAV required')
                source = pcm(audio.readframes(audio.getnframes()))
            duration = Fraction(len(source), 24000)
            leadin = scene['leadInFrames']
            require(type(leadin) is int and leadin >= 0, 'Current nonnegative scene lead-in required')
            start = props['timing']['openingFrames'] + scene['startFrame'] + (leadin if applylead else 0)
            offsetframes = copy * props['timing']['totalFrames'] + start
            require(leadin + math.ceil(duration * props['fps']) <= scene['durationFrames']
                    and duration <= Fraction(scene['audioDurationFrames'], props['fps']), 'Actual WAV truncated by scene allocation')
            require(Fraction(start, props['fps']) + duration <=
                    Fraction(props['timing']['finalPageStartFrame'], props['fps']), 'Narration crosses final-card boundary')
            offset = round(Fraction(offsetframes * 24000, props['fps']))
            full = correspondence(source, decoded, offset)
            energetic = None
            for end in range(len(source), 5999, -6000):
                if sum(value * value for value in source[end - 6000:end]) / 6000 >= (0.005 * 32768) ** 2:
                    energetic = (end - 6000, end)
                    break
            require(energetic is not None, 'No energetic narration tail window')
            tail = correspondence(source, decoded, offset, *energetic)
            require(full['score'] >= 0.80 and tail['score'] >= 0.60, 'Current source/energetic-tail correspondence unresolved')
            observations.append({'copy': copy + 1, 'scene': scene['id'], 'source_wav': str(sourcepath),
                                 'source_samples': len(source), 'allocated_frames': scene['durationFrames'],
                                 'audio_offset_frame': offsetframes, 'renderer_template': template, 'scheduling_policy': policy,
                                 'lead_in_frames': leadin, 'lead_in_applied_to_audio': applylead,
                                 'full_source': full, 'last_energetic_source_block': tail})
    return observations


def inspection_path(path):
    path = owned(path)
    root = TARGET / 'inspection'
    require(path.is_relative_to(root) and path != root, 'Expected proper descendant of private inspection directory')
    return path


def selected_inspections(paths):
    selected = [inspection_path(path) for path in paths]
    require(len(set(selected)) == len(selected), 'Duplicate selected inspection path')
    return [read(path) for path in selected]


def inspect(rowpath, ffprobe, ffmpeg, output_directory=None):
    for executable in (ffprobe, ffmpeg):
        require(executable.is_absolute() and executable.is_file(), 'Select explicit absolute ffprobe/FFmpeg files')
    row = read(rowpath)
    case, run = row['case'], row['run']
    project = identity(case, run)
    props = read(owned(row['props_locator']))
    validate_settings(encoding(props), case)
    directory = inspection_path(TARGET / 'inspection' / f'{case}-{run}' if output_directory is None else output_directory)
    directory.mkdir(parents=True, exist_ok=False)
    report = {'schema': 'cozy.quality-inspection.v1', 'case': case, 'run': run,
              'measurement_locator': str(rowpath), 'inspection_directory': str(directory),
              'movies': [], 'commands': [], 'technical_complete': False,
              'visual_acceptance': 'parent-pending', 'hearing_acceptance': 'parent-pending', 'limitations': LIMITS}
    try:
        for kind in ('dialogue', 'final'):
            movie = file(owned(row['outputs'][kind]['path']))
            copies = 1 if kind == 'dialogue' else 2
            frames = props['timing']['totalFrames'] * copies
            probe = read(process([str(ffprobe), '-v', 'error', '-count_frames', '-show_streams', '-show_format',
                                  '-show_packets', '-of', 'json', str(movie)], directory, kind + '-probe', report['commands']))
            video = [stream for stream in probe['streams'] if stream['codec_type'] == 'video']
            audio = [stream for stream in probe['streams'] if stream['codec_type'] == 'audio']
            require(len(video) == len(audio) == 1 and len(probe['streams']) == 2, 'One H264/AAC pair required')
            video, audio = video[0], audio[0]
            require(video['codec_name'] == 'h264' and video['width'] == props['width'] and video['height'] == props['height']
                    and Fraction(video['avg_frame_rate']) == props['fps'] and int(video['nb_read_frames']) == frames
                    and audio['codec_name'] == 'aac' and int(audio['sample_rate']) == 48000 and audio['channels'] == 2, 'Decoded media fields differ')
            endpoints = []
            for stream in (video, audio):
                packets = [packet for packet in probe['packets'] if packet['stream_index'] == stream['index']]
                base = Fraction(stream['time_base'])
                endpoint = (int(stream.get('start_pts', 0)) + int(stream['duration_ts'])) * base
                require(packets and max((int(packet['pts']) + int(packet['duration'])) * base for packet in packets) == endpoint,
                        'Rational packet/stream presentation ending differs')
                endpoints.append(endpoint)
            require(endpoints[0] == Fraction(frames, props['fps']) and abs(endpoints[0] - endpoints[1]) <= Fraction(1, 48000), 'AV ending differs by more than one48k sample')
            process([str(ffmpeg), '-nostdin', '-v', 'error', '-xerror', '-i', str(movie), '-map', '0:v:0',
                     '-map', '0:a:0', '-f', 'null', '-'], directory, kind + '-decode', report['commands'])
            decodedpath = owned(directory / f'{kind}-audio.pcm')
            process([str(ffmpeg), '-nostdin', '-v', 'error', '-i', str(movie), '-vn', '-map', '0:a:0',
                     '-ac', '1', '-ar', '24000', '-c:a', 'pcm_s16le', '-f', 's16le', str(decodedpath)],
                    directory, kind + '-audio', report['commands'])
            item = {'kind': kind, 'path': str(movie), 'bytes': movie.stat().st_size, 'frame_count': frames,
                    'dimensions': [video['width'], video['height']], 'fps': props['fps'], 'full_decode_exit': 0,
                    'within_one_48k_sample': True, 'presented_ends': [str(value) for value in endpoints],
                    'configured_aac_target': row['settings']['audioBitrate'], 'measured_average_aac_bps': audio.get('bit_rate'),
                    'decoded_pcm': str(decodedpath), 'narration': narration(project, props, pcm(decodedpath.read_bytes()), copies),
                    'card_frames': [], 'snapshots': []}
            report['movies'].append(item)
            hold = props['timing']['finalPageHoldFrames']
            require(type(hold) is int and hold > 0, 'Actual complete final-card hold required')
            for copy in range(copies):
                start = copy * props['timing']['totalFrames'] + props['timing']['finalPageStartFrame']
                card = owned(directory / f'{kind}-card{copy}.rgb')
                process([str(ffmpeg), '-nostdin', '-v', 'error', '-i', str(movie), '-an', '-vf',
                         f'select=between(n\\,{start}\\,{start + hold - 1}),scale=320:180', '-fps_mode', 'passthrough',
                         '-frames:v', str(hold), '-pix_fmt', 'rgb24', '-f', 'rawvideo', str(card)], directory,
                        f'{kind}-card{copy}', report['commands'])
                raw, size = card.read_bytes(), 320 * 180 * 3
                require(len(raw) == hold * size, 'Incomplete final-card decode')
                for number in range(hold):
                    pixels = raw[number * size:(number + 1) * size]
                    fraction = sum(max(pixels[i:i + 3]) > 32 for i in range(0, size, 3)) / (320 * 180)
                    item['card_frames'].append({'frame': start + number, 'nonblack_fraction': fraction})
                    require(fraction > 0.01, 'Black frame inside actual full final-card hold')
            selected = [('opening-display', props['timing']['openingFrames'] // 2)] + [
                (scene['id'], props['timing']['openingFrames'] + scene['startFrame'] + scene['durationFrames'] // 2)
                for scene in props['scenes']] + [('summary-display', props['timing']['summaryStartFrame'] + props['timing']['summaryFrames'] // 2),
                                               ('last-end-card', frames - 1)]
            for role, frame in selected:
                png = owned(directory / f'{kind}-{role}.png')
                process([str(ffmpeg), '-nostdin', '-v', 'error', '-i', str(movie), '-an', '-vf', f'select=eq(n\\,{frame})',
                         '-frames:v', '1', str(png)], directory, f'{kind}-{role}', report['commands'])
                item['snapshots'].append({'role': role, 'frame': frame, 'png': str(file(png))})
        report['technical_complete'] = True
    except (OSError, ValueError, KeyError, TypeError, wave.Error, EOFError) as error:
        report['error'] = str(error)
    write(directory / 'inspection.json', report)
    page = '<!doctype html><meta charset="utf-8"><title>Current media observations</title><p>Parent visual review pending; waveform checks are technical evidence.</p>'
    page += ''.join(f'<figure><img style="max-width:90vw" src="{html.escape(Path(image["png"]).name)}"><figcaption>{html.escape(movie["kind"] + ": " + image["role"])}</figcaption></figure>'
                    for movie in report['movies'] for image in movie['snapshots'])
    owned(directory / 'comparison.html').write_text(page, encoding='utf-8')
    return report


def technical(inspection):
    movies = inspection.get('movies', [])
    return inspection.get('technical_complete') is True and len(movies) == 2 and all(
        movie.get('full_decode_exit') == 0 and movie.get('within_one_48k_sample') is True
        and movie.get('narration') and movie.get('card_frames') and all(frame['nonblack_fraction'] > 0.01 for frame in movie['card_frames'])
        for movie in movies)


def summarize(rows, inspections):
    result = {'schema': 'cozy.quality-summary.v1', 'routes': {}, 'technical_status': 'pending',
              'speed_status': 'pending', 'visual_acceptance': 'parent-pending', 'hearing_acceptance': 'parent-pending',
              'step_acceptance': 'parent-pending', 'phase_acceptance': 'parent-pending', 'limitations': LIMITS}
    require(len({(row['case'], row['run']) for row in rows}) == len(rows), 'Duplicate current sample')
    require(len({(inspection['case'], inspection['run']) for inspection in inspections}) == len(inspections),
            'Duplicate selected inspection case/run')
    for inspection in inspections:
        identity(inspection['case'], inspection['run'])
    for row in rows:
        identity(row['case'], row['run'])
        validate_settings(row['settings'], row['case'])
        require(row['route'] == ('docker' if row['case'].startswith('docker-') else 'mixed')
                and row['encoder'] == ('remotion-renderMedia-h264' if row['route'] == 'docker' else 'libx264')
                and row['image'] == IMAGE and row['cachePolicy'] == CACHE, 'Route/encoder/image/cache mismatch')
        for command in row['commands'].values():
            duration = command['commandExecution'].get('durationMs')
            require(type(duration) in (float, int) and math.isfinite(duration) and duration > 0
                    and command['native_wall_seconds'] == duration / 1000, 'Actual native timing required')
        require(row['native_total_seconds'] == sum(value['native_wall_seconds'] for value in row['commands'].values()), 'Native total must use only native command clock')
    for route in ('mixed', 'docker'):
        selected = {(row['case'], row['run']): row for row in rows if row['route'] == route and row['case'] != 'mixed-override'}
        missing = [(f'{route}-{tier}', run) for run in RUNS for tier in ('standard', 'lightweight') if (f'{route}-{tier}', run) not in selected]
        record = {'status': 'pending', 'missing': missing, 'pairs': [], 'warm_medians': None}
        result['routes'][route] = record
        if missing:
            continue
        reference = selected[(f'{route}-standard', 'cold')]
        require(all(row['input'] == reference['input'] for row in selected.values()), 'Comparable input mismatch')
        for tier in ('standard', 'lightweight'):
            projects = {selected[(f'{route}-{tier}', run)]['project'] for run in RUNS}
            require(len(projects) == 1, 'Warm samples require the same active project')
        medians = {}
        for run in RUNS[1:]:
            standard, lightweight = (selected[(f'{route}-{tier}', run)] for tier in ('standard', 'lightweight'))
            record['pairs'].append({'run': run, 'standard_seconds': standard['native_total_seconds'],
                                    'lightweight_seconds': lightweight['native_total_seconds'],
                                    'standard_over_lightweight_ratio': standard['native_total_seconds'] / lightweight['native_total_seconds'],
                                    'output_bytes': {tier: row['outputs'] for tier, row in (('standard', standard), ('lightweight', lightweight))}})
        for tier in ('standard', 'lightweight'):
            warm = [selected[(f'{route}-{tier}', run)] for run in RUNS[1:]]
            medians[tier] = {'total_seconds': statistics.median(row['native_total_seconds'] for row in warm),
                             'render_seconds': statistics.median(row['commands']['render']['native_wall_seconds'] for row in warm),
                             'final_seconds': statistics.median(row['commands']['final-build']['native_wall_seconds'] for row in warm),
                             'stages_seconds': {name: statistics.median(next(stage['duration_seconds'] for stage in row['stages'] if stage['stage'] == name)
                                                                        for row in warm) for name in [stage['stage'] for stage in warm[0]['stages']]},
                             'host_encode_seconds': None if route == 'docker' else statistics.median(row['host_encode'][0]['duration_seconds'] for row in warm),
                             'overlapping_windows': [row['progress_windows'] for row in warm] if route == 'docker' else None}
        record['warm_medians'] = medians
        record['status'] = 'observed-faster' if medians['lightweight']['total_seconds'] < medians['standard']['total_seconds'] else 'observed-not-faster'
    statuses = [record['status'] for record in result['routes'].values()]
    result['speed_status'] = 'observed-faster-both-routes' if all(status == 'observed-faster' for status in statuses) else (
        'observed-not-faster' if 'observed-not-faster' in statuses else 'pending')
    lookup = {(inspection['case'], inspection['run']): inspection for inspection in inspections}
    expected = {(case, run) for case in CASES for run in (('cold',) if case == 'mixed-override' else RUNS)}
    result['technical_status'] = 'observed' if expected.issubset(lookup) and all(technical(lookup[key]) for key in expected) else 'pending-or-failed'
    result['override'] = {'measurement': next((row for row in rows if row['case'] == 'mixed-override'), None),
                          'inspection': lookup.get(('mixed-override', 'cold')), 'acceptance': 'parent-pending'}
    return result


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    commands = parser.add_subparsers(dest='action', required=True)
    for name in ('prepare', 'prepare-final'):
        command = commands.add_parser(name)
        command.add_argument('--case', choices=CASES, required=True)
        command.add_argument('--run', choices=RUNS, required=True)
    command = commands.add_parser('collect')
    command.add_argument('--index', type=Path, required=True)
    command = commands.add_parser('inspect')
    command.add_argument('--measurement', type=Path, required=True)
    command.add_argument('--ffprobe', type=Path, required=True)
    command.add_argument('--ffmpeg', type=Path, required=True)
    command.add_argument('--output-directory', type=Path)
    command = commands.add_parser('summarize')
    command.add_argument('--output', type=Path, required=True)
    command.add_argument('--inspection', type=Path, action='append', default=[])
    args = parser.parse_args()
    if args.action == 'prepare':
        result = prepare(args.case, args.run)
    elif args.action == 'prepare-final':
        result = prepare_final(args.case, args.run)
    elif args.action == 'collect':
        result = collect(owned(args.index))
    elif args.action == 'inspect':
        result = inspect(owned(args.measurement), args.ffprobe, args.ffmpeg, args.output_directory)
    else:
        result = summarize([read(path) for path in sorted((TARGET / 'measurements').glob('*.json'))],
                           selected_inspections(args.inspection))
        write(owned(args.output), result)
    print(json.dumps(result, ensure_ascii=False, allow_nan=False))
    return 1 if args.action == 'inspect' and not result['technical_complete'] else 0


if __name__ == '__main__':
    try:
        raise SystemExit(main())
    except (OSError, ValueError, KeyError, TypeError, IndexError) as error:
        print(f'Quality observation refused: {error}', file=sys.stderr)
        raise SystemExit(1)
