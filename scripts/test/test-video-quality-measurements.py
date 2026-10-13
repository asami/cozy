#!/usr/bin/env -S uv run --managed-python --no-python-downloads --script
# /// script
# requires-python = ">=3.12"
# dependencies = []
# ///
"""Executable behavior of the private P718-03 measurement protocol.

These synthetic tempfile observations specify helpers, never actual media,
native speed, hearing, visual usefulness or Step/Phase acceptance.
@since/@version Oct. 12, 2026; @author ASAMI, Tomoharu
"""

import array
from contextlib import contextmanager
import copy
import importlib.util
import json
import math
from pathlib import Path
import sys
import tempfile
import unittest
from unittest.mock import patch
import wave

sys.dont_write_bytecode = True
SPEC = importlib.util.spec_from_file_location('quality_measurements', Path(__file__).with_name('measure-video-quality-profiles.py'))
QUALITY = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(QUALITY)

DESCRIPTOR = '''name: specimen
output: build/movie.mp4
credits:
  profile: retained
visual-effects:
  final-page: end-card
assets:
  final-page:
    path: assets/final-page.svg
voice: {speakerName: retained}
renderer:
  engine: remotion
  fps: 18
  width: 1280
  height: 720
parts:
  - id: dialogue
    type: storyboard
    storyboard: storyboard.md
    audioDir: build/audio/dialogue
    output: build/parts/dialogue.mp4
    renderer:
      strategy: slide
      width: 1280
      height: 720
'''


def put(path, value):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value), encoding='utf-8')
    return path


@contextmanager
def fixture():
    work = QUALITY.REPOSITORY / 'target/quality-measurement-spec/work'
    work.mkdir(parents=True, exist_ok=True)
    with tempfile.TemporaryDirectory(dir=work) as directory:
        repository = Path(directory).resolve()
        target = repository / 'target/phase718/quality-media01'
        source = repository / 'fixture'
        with patch.multiple(QUALITY, REPOSITORY=repository, TARGET=target, SOURCE=source):
            for relative in QUALITY.FILES:
                path = source / relative
                path.parent.mkdir(parents=True, exist_ok=True)
                path.write_bytes(b'retained fixture')
            (source / 'video.yaml').write_text(DESCRIPTOR, encoding='utf-8')
            put(source / 'build/audio/dialogue/manifest.json', [
                {'sceneId': scene, 'file': f'{index:02d}-{scene}.wav'}
                for index, scene in enumerate(QUALITY.SCENES, 1)])
            for name in ('quality-profile-node-observed.mjs', 'quality-profile-ffmpeg-observed.py'):
                path = repository / 'scripts/test' / name
                path.parent.mkdir(parents=True, exist_ok=True)
                path.write_text('synthetic unexecuted observer', encoding='utf-8')
            yield repository


def props(case, run):
    selected = QUALITY.settings(case)
    return {**{key: selected[key] for key in QUALITY.FIELDS}, 'encodingOrigins': selected['origins'],
            'partId': 'dialogue', 'rendererTemplate': None, 'outputPath': QUALITY.outputs(run)['dialogue'],
            'timing': {'totalFrames': 150, 'openingFrames': 12, 'finalPageStartFrame': 144,
                       'finalPageHoldFrames': 6, 'summaryStartFrame': 130, 'summaryFrames': 6},
            'scenes': [{'id': scene, 'audioPath': f'audio/{index:02d}-{scene}.wav', 'startFrame': (index - 1) * 12,
                        'leadInFrames': 3, 'durationFrames': 12, 'audioDurationFrames': 6}
                       for index, scene in enumerate(QUALITY.SCENES, 1)]}


def direct(project, case, run):
    path = project / QUALITY.outputs(run)['dialogue']
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_bytes(b'actual fixture direct bytes')
    put(project / 'target/cozy-video/remotion/dialogue/props.json', props(case, run))


def producer_records(project, case, run):
    selected = QUALITY.settings(case)
    final = {'schema': 'cozy.video.final.v2', 'status': 'validated', 'mode': 'final',
             'encoding': selected, 'output': {'path': QUALITY.outputs(run)['final']},
             'buildSettings': {'parts': []}, 'partArtifacts': []}
    for partid in ('dialogue', 'dialogue-repeat'):
        relative = f'target/cozy-video/final/part-artifacts/{partid}.json'
        final['buildSettings']['parts'].append({'partId': partid, 'outputPath': QUALITY.outputs(run)[partid]})
        final['partArtifacts'].append({'partId': partid, 'output': {'path': QUALITY.outputs(run)[partid]},
                                       'manifest': {'path': relative}})
        put(project / relative, {'schema': 'cozy.video.storyboard-part-artifact.v2', 'status': 'validated',
                                'mode': 'final', 'partId': partid, 'output': {'path': QUALITY.outputs(run)[partid]}})
    put(project / f'build/quality/{run}/dialogue.manifest.json', {'encoding': selected})
    put(project / 'target/cozy-video/final/manifest.json', final)
    (project / QUALITY.outputs(run)['final']).write_bytes(b'synthetic final bytes')
    return final


def interval(stage, invocation='render', start=1000000000, seconds=1):
    end = start + int(seconds * 1000000000)
    return [{'stage': stage, 'event': 'begin', 'invocation': invocation, 'monotonic_ns': str(start), 'start_ns': str(start)},
            {'stage': stage, 'event': 'end', 'invocation': invocation, 'monotonic_ns': str(end), 'end_ns': str(end),
             'duration_ns': str(end - start), 'success': True, 'exit_code': 0, 'signal': None}]


def traces(project, case, run):
    docker = case.startswith('docker-')
    node = interval('node-invocation', seconds=8)
    node += [{'stage': 'props', 'event': 'observed', 'invocation': 'render', 'monotonic_ns': '1100000000',
              'settings': QUALITY.settings(case), 'timing': props(case, run)['timing'],
              'outputPath': QUALITY.outputs(run)['dialogue']}]
    stages = ('bundle', 'select-composition', 'render-encode-overlap', 'media-write') if docker else (
        'bundle', 'select-composition', 'render-frames', 'audio-mix')
    for stage in stages:
        node += interval(stage, seconds=4 if stage == 'render-encode-overlap' else 1)
    if docker:
        for name, stamp in (('full-rendered', 4000000000), ('first-positive-encoded', 2000000000), ('full-encoded', 4500000000)):
            node.append({'stage': 'render-encode-overlap', 'event': 'milestone', 'milestone': name,
                         'invocation': 'render', 'monotonic_ns': str(stamp)})
        for kind in ('pre-stitcher', 'stitcher'):
            node.append({'stage': 'ffmpeg-override', 'event': 'output', 'type': kind, 'invocation': 'render',
                         'monotonic_ns': '4600000000', 'args': ['-b:a', QUALITY.settings(case)['audioBitrate']]})
    calls = [['ffmpeg', '-i', str(project / QUALITY.outputs(run)[partid]), '-c:a', 'pcm_s16le', f'{partid}.wav']
             for partid in ('dialogue', 'dialogue-repeat')]
    calls.append(['ffmpeg', '-c:a', 'aac', '-b:a', QUALITY.settings(case)['audioBitrate'], '-ar', '48000',
                  '-ac', '2', '-cutoff', '18000', str(project / QUALITY.outputs(run)['final'])])
    ffmpeg = []
    for index, argv in enumerate(calls):
        rows = interval('assembly-subcall', invocation=f'assembly{index}')
        rows[0]['real_argv'] = argv
        ffmpeg += rows
    if not docker:
        rows = interval('dialogue-encode', invocation='host')
        rows[0]['real_argv'] = ['ffmpeg', '-c:v', 'libx264', '-crf', str(QUALITY.settings(case)['crf']),
                                '-b:a', QUALITY.settings(case)['audioBitrate']]
        if QUALITY.settings(case)['x264Preset']:
            rows[0]['real_argv'] += ['-preset', QUALITY.settings(case)['x264Preset']]
        ffmpeg += rows
    directory = project / f'quality-observations/{run}'
    for rows, name, schema in ((node, 'remotion', 'cozy.quality-remotion-event.v1'),
                               (ffmpeg, 'ffmpeg', 'cozy.quality-ffmpeg-event.v1')):
        (directory / f'{name}-events.jsonl').write_text(''.join(json.dumps({**row, 'schema': schema, 'case': case,
            'run': run, 'part': 'dialogue' if name == 'remotion' else 'assembly'}) + '\n' for row in rows), encoding='utf-8')


def native_entry(project, case, run, kind, seconds):
    directory = project / f'quality-observations/{run}'
    argv = QUALITY.logical_argv(project, kind)
    identity = f'{case}-{run}-{kind}'
    request = {'schema': 'cozy.exact-cli-command.v5', 'execution_identity': identity, 'executable': '/synthetic/cozy',
               'argv': ['/synthetic/cozy', *argv], 'working_directory': str(project)}
    result = {**request, 'schema': 'cozy.exact-cli-command-result.v6', 'exit_code': 0, 'timed_out': False,
              'command_completion': 'terminal', 'process_group_completion': 'group-gone'}
    return {'case': case, 'run': run, 'kind': kind, 'logical_argv': argv,
            'request_locator': str(put(directory / f'{kind}-request.json', request)),
            'result_locator': str(put(directory / f'{kind}-result.json', result)), 'owned_group_gone': True,
            'commandExecution': {'id': identity, 'command': identity, 'durationMs': seconds * 1000},
            'accepted_native_command': identity, 'native_execution_identity': identity}


def collection(case='mixed-lightweight'):
    run = 'cold'
    project = Path(QUALITY.prepare(case, run)['project'])
    direct(project, case, run)
    QUALITY.prepare_final(case, run)
    producer_records(project, case, run)
    traces(project, case, run)
    index = {'case': case, 'run': run, 'entries': [native_entry(project, case, run, kind, seconds)
              for kind, seconds in (('render', 10), ('final-build', 2))]}
    return put(project / f'quality-observations/{run}/index.json', index)


def paired_rows(base, lightweight=8):
    rows = []
    for route in ('mixed', 'docker'):
        for tier in ('standard', 'lightweight'):
            for run in QUALITY.RUNS:
                row = copy.deepcopy(base)
                row.update(case=f'{route}-{tier}', run=run, route=route, settings=QUALITY.settings(f'{route}-{tier}'),
                           encoder='libx264' if route == 'mixed' else 'remotion-renderMedia-h264',
                           project=str(QUALITY.identity(f'{route}-{tier}', run)))
                seconds = lightweight if tier == 'lightweight' else 10
                for kind, duration in (('render', seconds), ('final-build', 2)):
                    row['commands'][kind]['native_wall_seconds'] = duration
                    row['commands'][kind]['commandExecution']['durationMs'] = duration * 1000
                row['native_total_seconds'] = seconds + 2
                rows.append(row)
    return rows


class PreparationSpec(unittest.TestCase):
    def test_direct_final_and_each_warm_restore_the_selected_route_and_complete_part(self):
        for case in ('docker-standard', 'docker-lightweight', 'mixed-lightweight', 'mixed-override'):
            with self.subTest(case=case), fixture():
                # Given a retained one-part fixture and an admitted profile.
                for run in (('cold',) if case == 'mixed-override' else QUALITY.RUNS):
                    # When the next direct run is prepared.
                    project = Path(QUALITY.prepare(case, run)['project'])
                    text = (project / 'video.yaml').read_text()
                    config = QUALITY.read(project / '.cozy/config.json')
                    # Then only dialogue is rendered and Docker uses the default FFmpeg selection.
                    self.assertEqual(text.count('  - id: '), 1)
                    self.assertIn(f'  qualityProfile: {QUALITY.settings(case)["qualityProfile"]}', text)
                    if case == 'mixed-override':
                        for field in QUALITY.FIELDS[1:]:
                            self.assertIn(f'  {field}: {QUALITY.settings(case)[field]}\n', text)
                    ffmpeg = config['video']['tools']['ffmpeg']
                    if case.startswith('docker-'):
                        self.assertEqual(ffmpeg, {'mode': 'docker'})
                        self.assertNotIn('encoder', config['video'])
                    else:
                        self.assertEqual(ffmpeg, {'mode': 'host', 'executable': str(project / 'quality-tools/ffmpeg-observed.py')})
                        self.assertEqual(config['video']['encoder'], {'name': 'libx264'})
                    self.assertEqual(config['video']['docker-image'], QUALITY.IMAGE)
                    direct(project, case, run)
                    # When final preparation consumes the current complete direct output and props.
                    result = QUALITY.prepare_final(case, run)
                    finaltext = (project / 'video.yaml').read_text()
                    # Then the ordered second input repeats current bytes with all authored settings retained.
                    self.assertFalse(result['independent_second_render'])
                    self.assertEqual(finaltext.split('  - id: dialogue-repeat\n')[0], text)
                    self.assertEqual(finaltext.count('  - id: '), 2)
                    self.assertEqual((project / QUALITY.outputs(run)['dialogue-repeat']).read_bytes(),
                                     (project / QUALITY.outputs(run)['dialogue']).read_bytes())
                    selected = QUALITY.read(project / '.cozy/config.json')['video']['tools']['ffmpeg']
                    self.assertEqual(selected, {'mode': 'docker', 'executable': '/workspace/quality-tools/ffmpeg-observed.mjs'}
                                     if case.startswith('docker-') else ffmpeg)
                    (project / QUALITY.outputs(run)['final']).write_bytes(b'preceding final fixture')

    def test_final_requires_current_direct_media_props_and_complete_card(self):
        for defect in ('missing-media', 'missing-props', 'foreign-output', 'empty-card'):
            with self.subTest(defect=defect), fixture():
                # Given a direct-stage project with an absent or invalid producer prerequisite.
                project = Path(QUALITY.prepare('docker-lightweight', 'cold')['project'])
                if defect != 'missing-media':
                    direct(project, 'docker-lightweight', 'cold')
                    path = project / 'target/cozy-video/remotion/dialogue/props.json'
                    if defect == 'missing-props':
                        path.unlink()
                    else:
                        value = QUALITY.read(path)
                        if defect == 'foreign-output':
                            value['outputPath'] = 'build/quality/old/dialogue.mp4'
                        else:
                            value['timing']['finalPageHoldFrames'] = 0
                        put(path, value)
                # When final preparation is requested, then it refuses before installing a repeated input.
                with self.assertRaises(ValueError):
                    QUALITY.prepare_final('docker-lightweight', 'cold')
                self.assertFalse((project / QUALITY.outputs('cold')['dialogue-repeat']).exists())
                self.assertEqual((project / 'video.yaml').read_text().count('  - id: '), 1)


class CollectionSpec(unittest.TestCase):
    def test_actual_final_v2_and_declared_artifacts_are_preserved_for_the_run(self):
        for case in ('mixed-lightweight', 'docker-lightweight', 'mixed-override'):
            with self.subTest(case=case), fixture():
                # Given exact current result, native timing, producer records and observation files.
                index = collection(case)
                # When their current measurement is collected.
                row = QUALITY.collect(index)
                # Then final.v2 and both declared artifacts remain independently locatable before warm replacement.
                self.assertEqual(QUALITY.read(row['final_manifest_locator'])['schema'], 'cozy.video.final.v2')
                self.assertEqual([item['partId'] for item in row['part_artifact_locators']], ['dialogue', 'dialogue-repeat'])
                for item in row['part_artifact_locators']:
                    self.assertEqual(QUALITY.read(item['archived_locator'])['partId'], item['partId'])
                    put(Path(item['producer_locator']), {'later_run': True})
                    self.assertEqual(QUALITY.read(item['archived_locator'])['partId'], item['partId'])
                self.assertEqual(QUALITY.read(row['props_locator'])['partId'], 'dialogue')
                self.assertEqual(QUALITY.read(row['part_manifest_locator'])['encoding'], row['settings'])
                self.assertEqual(row['native_total_seconds'], 12)
                self.assertEqual(len(row['assembly_subcalls']), 3)
                self.assertEqual(row['acceptance'], 'parent-pending')
                if case.startswith('docker-'):
                    self.assertTrue(row['progress_windows']['overlap'])
                    self.assertIsNone(row['progress_windows']['disjoint_sum_seconds'])
                    self.assertIsNone(row['progress_windows']['pure_encode_seconds'])
                    self.assertEqual(row['host_encode'], [])
                else:
                    self.assertIsNone(row['progress_windows'])
                    self.assertEqual(len(row['host_encode']), 1)

    def test_foreign_generic_unordered_missing_and_unsafe_producer_records_are_refused(self):
        for defect in ('generic', 'status', 'mode', 'final-path', 'part-order', 'extra-part', 'output-path',
                       'missing-artifact', 'artifact-schema', 'artifact-id', 'artifact-status', 'artifact-mode',
                       'artifact-output', 'absolute-manifest', 'outside-manifest', 'traversal', 'symlink'):
            with self.subTest(defect=defect), fixture():
                # Given real current artifact files with one producer-contract defect.
                index = collection()
                project = QUALITY.identity('mixed-lightweight', 'cold')
                path = project / 'target/cozy-video/final/manifest.json'
                final = QUALITY.read(path)
                artifactpath = project / final['partArtifacts'][0]['manifest']['path']
                if defect == 'generic':
                    final = {'status': 'validated', 'outputPath': str(project / QUALITY.outputs('cold')['final']), 'partOutputs': []}
                elif defect in ('status', 'mode'):
                    final[defect] = 'foreign'
                elif defect == 'final-path':
                    final['output']['path'] = 'build/quality/warm01/final.mp4'
                elif defect == 'part-order':
                    final['partArtifacts'].reverse()
                elif defect == 'extra-part':
                    final['buildSettings']['parts'].append(copy.deepcopy(final['buildSettings']['parts'][0]))
                elif defect == 'output-path':
                    final['buildSettings']['parts'][0]['outputPath'] = str(project / QUALITY.outputs('cold')['dialogue'])
                elif defect == 'missing-artifact':
                    artifactpath.unlink()
                elif defect.startswith('artifact-'):
                    record = QUALITY.read(artifactpath)
                    field = {'artifact-schema': 'schema', 'artifact-id': 'partId', 'artifact-status': 'status',
                             'artifact-mode': 'mode'}.get(defect)
                    if field:
                        record[field] = 'foreign'
                    else:
                        record['output']['path'] = QUALITY.outputs('warm01')['dialogue']
                    put(artifactpath, record)
                elif defect == 'symlink':
                    outside = put(project / 'outside.json', QUALITY.read(artifactpath))
                    artifactpath.unlink()
                    artifactpath.symlink_to(outside)
                else:
                    final['partArtifacts'][0]['manifest']['path'] = {
                        'absolute-manifest': str(artifactpath), 'outside-manifest': 'outside.json',
                        'traversal': 'target/cozy-video/final/part-artifacts/../part-artifacts/dialogue.json'}[defect]
                put(path, final)
                # When the producer contract is consumed, then the invalid record cannot become measured evidence.
                with self.assertRaises((ValueError, KeyError)):
                    QUALITY.collect(index)

    def test_failed_missing_native_and_foreign_trace_inputs_are_refused(self):
        for defect in ('failed-command', 'incomplete-command', 'absent-duration', 'foreign-command', 'foreign-run',
                       'failed-stage', 'missing-stage', 'wrong-profile', 'wrong-encoder'):
            with self.subTest(defect=defect), fixture():
                # Given exact tempfile command/results and traces with one failure or mismatched observation.
                indexpath = collection()
                index = QUALITY.read(indexpath)
                entry = index['entries'][0]
                if defect in ('failed-command', 'incomplete-command'):
                    result = QUALITY.read(entry['result_locator'])
                    result['exit_code' if defect == 'failed-command' else 'command_completion'] = 1 if defect == 'failed-command' else 'running'
                    put(Path(entry['result_locator']), result)
                elif defect == 'absent-duration':
                    del entry['commandExecution']['durationMs']
                elif defect == 'foreign-command':
                    entry['native_execution_identity'] = 'foreign'
                elif defect == 'wrong-profile':
                    project = QUALITY.identity('mixed-lightweight', 'cold')
                    path = project / 'target/cozy-video/remotion/dialogue/props.json'
                    value = QUALITY.read(path)
                    value['encodingOrigins']['fps'] = 'explicit'
                    put(path, value)
                else:
                    project = QUALITY.identity('mixed-lightweight', 'cold')
                    path = project / 'quality-observations/cold' / ('ffmpeg-events.jsonl' if defect == 'wrong-encoder' else 'remotion-events.jsonl')
                    rows = [json.loads(line) for line in path.read_text().splitlines()]
                    if defect == 'foreign-run':
                        rows[0]['run'] = 'warm01'
                    elif defect == 'failed-stage':
                        next(row for row in rows if row['event'] == 'end')['success'] = False
                    elif defect == 'missing-stage':
                        rows = [row for row in rows if row['stage'] != 'audio-mix']
                    else:
                        next(row for row in rows if row['stage'] == 'dialogue-encode' and row['event'] == 'begin')['real_argv'][2] = 'h264_videotoolbox'
                    path.write_text(''.join(json.dumps(row) + '\n' for row in rows), encoding='utf-8')
                put(indexpath, index)
                # When collection runs, then unsuccessful or incomparable facts are refused.
                with self.assertRaises((ValueError, KeyError)):
                    QUALITY.collect(indexpath)


class ComparisonSpec(unittest.TestCase):
    def test_explicit_report_selection_retains_failures_and_rejects_duplicate_cases_and_paths(self):
        with fixture():
            # Given all seventeen report files and a retained failure beside a later successful attempt.
            QUALITY.collect(collection())
            paths = []
            for case in QUALITY.CASES:
                for run in (('cold',) if case == 'mixed-override' else QUALITY.RUNS):
                    report = {'case': case, 'run': run, 'technical_complete': True,
                              'movies': [{'full_decode_exit': 0, 'within_one_48k_sample': True,
                                          'narration': [{'fixture': True}], 'card_frames': [{'nonblack_fraction': 0.5}]}
                                         for _ in range(2)]}
                    paths.append(put(QUALITY.TARGET / 'inspection' / f'{case}-{run}' / 'inspection.json', report))
            successful = QUALITY.read(paths[0])
            failed = {**successful, 'technical_complete': False, 'error': 'Retained first attempt failure'}
            failedpath = put(QUALITY.TARGET / 'inspection/first-failed/inspection.json', failed)
            laterpath = put(QUALITY.TARGET / 'inspection/later-success/inspection.json', successful)
            # When exact selected reports are summarized, then full coverage and selected failure both remain factual.
            self.assertEqual(QUALITY.summarize([], QUALITY.selected_inspections(paths))['technical_status'], 'observed')
            self.assertEqual(QUALITY.summarize([], QUALITY.selected_inspections([failedpath, *paths[1:]]))['technical_status'],
                             'pending-or-failed')
            self.assertEqual(QUALITY.summarize([], QUALITY.selected_inspections(paths[1:]))['technical_status'], 'pending-or-failed')
            self.assertEqual(QUALITY.selected_inspections([failedpath])[0]['error'], failed['error'])
            # When the CLI receives no inspection selection despite existing reports, then none are implicitly discovered.
            output = QUALITY.TARGET / 'explicit-empty-summary.json'
            with patch.object(sys, 'argv', ['measure', 'summarize', '--output', str(output)]), patch('builtins.print'):
                self.assertEqual(QUALITY.main(), 0)
            self.assertEqual(QUALITY.read(output)['technical_status'], 'pending-or-failed')
            # When repeated CLI inputs explicitly select coverage, then the supplied reports determine the result.
            output = QUALITY.TARGET / 'explicit-selected-summary.json'
            argv = ['measure', 'summarize', '--output', str(output)]
            for path in paths:
                argv += ['--inspection', str(path)]
            with patch.object(sys, 'argv', argv), patch('builtins.print'):
                self.assertEqual(QUALITY.main(), 0)
            self.assertEqual(QUALITY.read(output)['technical_status'], 'observed')
            # When selection repeats a path or case/run, then no last-record-wins policy is allowed.
            with self.assertRaisesRegex(ValueError, 'Duplicate selected inspection path'):
                QUALITY.selected_inspections([paths[0], paths[0]])
            for selection in ([paths[0], laterpath], [failedpath, laterpath]):
                with self.assertRaisesRegex(ValueError, 'Duplicate selected inspection case/run'):
                    QUALITY.summarize([], QUALITY.selected_inspections(selection))
            outside = put(QUALITY.TARGET / 'outside-inspection.json', successful)
            with self.assertRaises(ValueError):
                QUALITY.selected_inspections([outside])

    def test_three_paired_warm_medians_remain_facts_with_human_acceptance_pending(self):
        with fixture():
            # Given current comparable helper observations for both full paired route matrices.
            rows = paired_rows(QUALITY.collect(collection()))
            # When independent route medians are summarized.
            summary = QUALITY.summarize(rows, [])
            # Then all three samples contribute and human and workflow acceptance remain pending.
            self.assertEqual(summary['speed_status'], 'observed-faster-both-routes')
            for route in ('mixed', 'docker'):
                self.assertEqual(len(summary['routes'][route]['pairs']), 3)
                self.assertEqual(summary['routes'][route]['warm_medians']['lightweight']['total_seconds'], 10)
            for field in ('visual_acceptance', 'hearing_acceptance', 'step_acceptance', 'phase_acceptance'):
                self.assertEqual(summary[field], 'parent-pending')
            self.assertEqual(summary['technical_status'], 'pending-or-failed')

    def test_smaller_bytes_partial_one_sided_and_slower_sets_do_not_establish_speed(self):
        with fixture():
            # Given smaller lightweight files with equal or slower native totals and incomplete variants.
            base = QUALITY.collect(collection())
            for seconds, expected in ((10, 'observed-not-faster'), (12, 'observed-not-faster')):
                rows = paired_rows(base, seconds)
                for row in rows:
                    if row['case'].endswith('lightweight'):
                        for output in row['outputs'].values():
                            output['bytes'] = 1
                # When size and timing facts are summarized, then size cannot make an equal/slower run faster.
                self.assertEqual(QUALITY.summarize(rows, [])['speed_status'], expected)
            complete = paired_rows(base)
            for selected in ([row for row in complete if row['run'] in ('cold', 'warm01')],
                             [row for row in complete if row['case'].endswith('standard')],
                             [row for row in complete if row['route'] == 'mixed']):
                # When a required paired route/run is missing, then speed remains pending.
                self.assertEqual(QUALITY.summarize(selected, [])['speed_status'], 'pending')

    def test_input_route_encoder_profile_run_and_native_clock_mismatches_are_refused(self):
        for defect in ('input', 'route', 'encoder', 'profile', 'run', 'duration', 'total', 'project'):
            with self.subTest(defect=defect), fixture():
                # Given a complete paired matrix with one incomparable field or missing native clock.
                rows = paired_rows(QUALITY.collect(collection()))
                row = rows[1]
                if defect == 'input':
                    row['input']['sceneIds'].reverse()
                elif defect == 'profile':
                    row['settings']['origins']['fps'] = 'explicit'
                elif defect == 'duration':
                    row['commands']['render']['commandExecution'].pop('durationMs')
                elif defect == 'total':
                    row['native_total_seconds'] += 1
                else:
                    row[defect] = 'foreign'
                # When comparison is requested, then these fields cannot silently form a speed claim.
                with self.assertRaises(ValueError):
                    QUALITY.summarize(rows, [])


class NarrationSpec(unittest.TestCase):
    def test_actual_positive_lead_in_maps_each_original_wav_in_both_copies(self):
        with fixture():
            # Given retained mono24k PCM WAVs and decoded samples at the actual producer lead-in offsets.
            project = Path(QUALITY.prepare('mixed-lightweight', 'cold')['project'])
            value = props('mixed-lightweight', 'cold')
            source = array.array('h', [int(10000 * math.sin(index * 0.037)) for index in range(6000)])
            decoded = array.array('h', [0]) * 600000
            for copyindex in range(2):
                for index, scene in enumerate(value['scenes'], 1):
                    path = project / f'build/audio/dialogue/{index:02d}-{scene["id"]}.wav'
                    with wave.open(str(path), 'wb') as audio:
                        audio.setparams((1, 2, 24000, 0, 'NONE', 'not compressed'))
                        samples = array.array('h', source)
                        if sys.byteorder != 'little':
                            samples.byteswap()
                        audio.writeframes(samples.tobytes())
                    offset = (copyindex * 150 + 12 + scene['startFrame'] + 3) * 2000
                    decoded[offset:offset + len(source)] = source
            for template in ('cozy-character-dialogue-v1', 'cozy-character-web-demo-v1'):
                value['rendererTemplate'] = template
                # When narration correspondence consumes the selected character template and original WAVs.
                observations = QUALITY.narration(project, value, decoded, 2)
                # Then both named templates apply lead-in to both copies without claiming hearing acceptance.
                self.assertEqual(len(observations), 18)
                for index, observation in enumerate(observations):
                    self.assertEqual(observation['renderer_template'], template)
                    self.assertEqual(observation['scheduling_policy'], 'scene-start-plus-lead-in')
                    self.assertTrue(observation['lead_in_applied_to_audio'])
                    self.assertEqual(observation['lead_in_frames'], 3)
                    self.assertEqual(observation['audio_offset_frame'], (index // 9) * 150 + 15 + (index % 9) * 12)
                    self.assertAlmostEqual(observation['full_source']['score'], 1)
                    self.assertIsNone(observation['full_source']['perceptual_quality'])
            for defect in ('negative-lead', 'scene-short', 'audio-short', 'card-crossing'):
                broken = copy.deepcopy(value)
                if defect == 'card-crossing':
                    broken['timing']['finalPageStartFrame'] = 17
                else:
                    broken['scenes'][0][{'negative-lead': 'leadInFrames', 'scene-short': 'durationFrames',
                                         'audio-short': 'audioDurationFrames'}[defect]] = {'negative-lead': -1, 'scene-short': 5, 'audio-short': 2}[defect]
                # When allocation would truncate narration or cross the card, then the observation is refused.
                with self.subTest(defect=defect), self.assertRaises(ValueError):
                    QUALITY.narration(project, broken, decoded, 2)

    def test_generic_positive_metadata_uses_scene_start_in_both_copies_and_refuses_wrong_placement(self):
        with fixture():
            # Given explicit generic props with positive authored lead and retained original WAVs.
            project = Path(QUALITY.prepare('mixed-lightweight', 'cold')['project'])
            value = props('mixed-lightweight', 'cold')
            source = array.array('h', [int(10000 * math.sin(index * 0.037)) for index in range(6000)])
            for index, scene in enumerate(value['scenes'], 1):
                with wave.open(str(project / f'build/audio/dialogue/{index:02d}-{scene["id"]}.wav'), 'wb') as audio:
                    audio.setparams((1, 2, 24000, 0, 'NONE', 'not compressed'))
                    samples = array.array('h', source)
                    if sys.byteorder != 'little':
                        samples.byteswap()
                    audio.writeframes(samples.tobytes())
            for shift in (0, 3):
                decoded = array.array('h', [0]) * 600000
                for copyindex in range(2):
                    for scene in value['scenes']:
                        offset = (copyindex * 150 + 12 + scene['startFrame'] + shift) * 2000
                        decoded[offset:offset + len(source)] = source
                # When correspondence interprets the explicit generic schedule.
                if shift:
                    # Then placing audio at the metadata lead instead is refused, without origin search.
                    with self.assertRaises(ValueError):
                        QUALITY.narration(project, value, decoded, 2)
                else:
                    observations = QUALITY.narration(project, value, decoded, 2)
                    # Then all nine WAVs in both copies start at their scenes and retain authored lead facts.
                    self.assertEqual(len(observations), 18)
                    for index, observation in enumerate(observations):
                        self.assertIsNone(observation['renderer_template'])
                        self.assertEqual(observation['scheduling_policy'], 'generic-scene-start')
                        self.assertFalse(observation['lead_in_applied_to_audio'])
                        self.assertEqual(observation['lead_in_frames'], 3)
                        self.assertEqual(observation['audio_offset_frame'], (index // 9) * 150 + 12 + (index % 9) * 12)
                        self.assertAlmostEqual(observation['full_source']['score'], 1)
                        self.assertAlmostEqual(observation['last_energetic_source_block']['score'], 1)
                    for field, invalid in (('startFrame', 13), ('durationFrames', 0)):
                        broken = copy.deepcopy(value)
                        broken['scenes'][1][field] = invalid
                        # When allocation is nonadjacent or nonpositive, then correspondence is refused.
                        with self.subTest(field=field), self.assertRaises(ValueError):
                            QUALITY.narration(project, broken, decoded, 2)

    def test_missing_and_unknown_templates_cannot_infer_an_audio_origin(self):
        with fixture():
            # Given current props with missing or unknown scheduling identity.
            project = Path(QUALITY.prepare('mixed-lightweight', 'cold')['project'])
            for template in ('missing', 'foreign-template'):
                value = props('mixed-lightweight', 'cold')
                if template == 'missing':
                    del value['rendererTemplate']
                else:
                    value['rendererTemplate'] = template
                # When narration is interpreted, then it refuses before seeking any source waveform.
                with self.subTest(template=template), self.assertRaisesRegex(ValueError, 'rendererTemplate'):
                    QUALITY.narration(project, value, array.array('h'), 2)


class InspectionSpec(unittest.TestCase):
    def test_failed_attempt_is_retained_and_only_a_new_safe_explicit_destination_starts_tools(self):
        with fixture() as repository:
            # Given a current measurement and explicitly selected mocked native executables.
            rowpath = put(QUALITY.TARGET / 'inspection-input.json', QUALITY.collect(collection()))
            executables = [repository / name for name in ('ffprobe', 'ffmpeg')]
            for executable in executables:
                executable.write_bytes(b'explicit mocked native tool')
            # When the deterministic first inspection fails, then its report remains in its original directory.
            with patch.object(QUALITY, 'process', side_effect=ValueError('Retained native failure')) as native:
                failed = QUALITY.inspect(rowpath, *executables)
                self.assertEqual(native.call_count, 1)
            original = QUALITY.TARGET / 'inspection/mixed-lightweight-cold'
            originalbytes = (original / 'inspection.json').read_bytes()
            self.assertFalse(failed['technical_complete'])
            self.assertEqual(failed['inspection_directory'], str(original))
            # When a separate explicit new destination is selected, then the prior attempt is preserved.
            newdirectory = QUALITY.TARGET / 'inspection/mixed-lightweight-cold-recheck02'
            with patch.object(QUALITY, 'process', side_effect=ValueError('New separately retained failure')) as native:
                new = QUALITY.inspect(rowpath, *executables, output_directory=newdirectory)
                self.assertEqual(native.call_count, 1)
            self.assertEqual(new['inspection_directory'], str(newdirectory))
            self.assertTrue((newdirectory / 'inspection.json').is_file())
            self.assertEqual((original / 'inspection.json').read_bytes(), originalbytes)
            outside = QUALITY.TARGET / 'not-inspection'
            link = QUALITY.TARGET / 'inspection/linked'
            link.symlink_to(original, target_is_directory=True)
            traversal = QUALITY.TARGET / 'inspection/new/../traversed'
            # When an existing, root, outside, traversal or symlink destination is requested, then tools never start.
            for destination in (None, original, newdirectory, QUALITY.TARGET / 'inspection', outside, traversal, link):
                with self.subTest(destination=destination), patch.object(QUALITY, 'process') as native:
                    with self.assertRaises((ValueError, FileExistsError)):
                        QUALITY.inspect(rowpath, *executables, output_directory=destination)
                    native.assert_not_called()
                self.assertEqual((original / 'inspection.json').read_bytes(), originalbytes)

    def test_current_media_bytes_do_not_refuse_valid_media_but_decode_and_ending_contracts_remain(self):
        for defect in (None, 'codec', 'ending', 'decode', 'black-card', 'empty-card'):
            with self.subTest(defect=defect), fixture() as repository:
                # Given a selected current movie whose bytes differ from earlier collection stage facts.
                row = QUALITY.collect(collection())
                for output in row['outputs'].values():
                    Path(output['path']).write_bytes(b'new current selected media fixture bytes')
                rowpath = put(QUALITY.TARGET / 'inspection-input.json', row)
                current = QUALITY.read(row['props_locator'])
                # Both built-in display names also occur as authored scene IDs.
                current['scenes'][-1]['id'] = 'summary'
                if defect == 'empty-card':
                    current['timing']['finalPageHoldFrames'] = 0
                put(Path(row['props_locator']), current)
                executables = [repository / name for name in ('ffprobe', 'ffmpeg')]
                for executable in executables:
                    executable.write_bytes(b'explicit mocked native tool')

                def observed(argv, directory, label, commands):
                    path = directory / f'{label}.stdout'
                    stderr = directory / f'{label}.stderr'
                    commands.append({'argv': argv, 'stdout': str(path), 'stderr': str(stderr), 'exit_code': 0})
                    # Exclusive logs and outputs expose collisions instead of silently replacing artifacts.
                    with path.open('xb'), stderr.open('xb'):
                        pass
                    if label.endswith('-probe'):
                        frames = current['timing']['totalFrames'] * (2 if label.startswith('final') else 1)
                        ending = frames * 48000 // current['fps'] + (2 if defect == 'ending' else 0)
                        streams = [{'index': 0, 'codec_type': 'video', 'codec_name': 'vp9' if defect == 'codec' else 'h264',
                                    'width': current['width'], 'height': current['height'], 'avg_frame_rate': str(current['fps']),
                                    'nb_read_frames': str(frames), 'time_base': f'1/{current["fps"]}', 'duration_ts': frames},
                                   {'index': 1, 'codec_type': 'audio', 'codec_name': 'aac', 'sample_rate': '48000', 'channels': 2,
                                    'time_base': '1/48000', 'duration_ts': ending, 'bit_rate': '92345'}]
                        path.write_text(json.dumps({'streams': streams, 'packets': [{'stream_index': stream['index'], 'pts': 0,
                                                                   'duration': stream['duration_ts']} for stream in streams]}), encoding='utf-8')
                    elif label.endswith('-decode') and defect == 'decode':
                        raise ValueError('Mocked native full decode failure')
                    elif label.endswith('-audio'):
                        with Path(argv[-1]).open('xb') as output:
                            output.write(b'\0\0' * 600000)
                    elif label.rsplit('-', 1)[-1].startswith('card'):
                        with Path(argv[-1]).open('xb') as output:
                            output.write(bytes([0 if defect == 'black-card' else 64]) * 320 * 180 * 3 * current['timing']['finalPageHoldFrames'])
                    elif argv[-1].endswith('.png'):
                        with Path(argv[-1]).open('xb') as output:
                            output.write(b'synthetic mocked snapshot')
                    return path

                # When bounded native inspection results are consumed (tools are mocked, never launched here).
                with patch.object(QUALITY, 'process', side_effect=observed), patch.object(QUALITY, 'narration', return_value=[{'technical_fixture': True}]):
                    report = QUALITY.inspect(rowpath, *executables)
                # Then earlier bytes are not an immutability certificate and invalid current media is still refused.
                self.assertEqual(report['technical_complete'], defect is None)
                if defect is None:
                    self.assertTrue(QUALITY.technical(report))
                    self.assertNotEqual(report['movies'][0]['bytes'], row['outputs']['dialogue']['bytes'])
                    self.assertEqual([len(movie['card_frames']) for movie in report['movies']], [6, 12])
                    self.assertEqual(report['movies'][0]['measured_average_aac_bps'], '92345')
                    self.assertEqual(report['movies'][0]['configured_aac_target'], '96k')
                    # Then both movies keep every display and scene snapshot in order at its original frame.
                    expected = [('opening-display', 6), ('opening', 18), ('speed', 30),
                                ('business-wall', 42), ('convergence', 54), ('harness-definition', 66),
                                ('broad-concept', 78), ('responsibility-boundary', 90),
                                ('technology-context', 102), ('summary', 114), ('summary-display', 133)]
                    artifacts = []
                    for movie, ending in zip(report['movies'], (149, 299)):
                        snapshots = movie['snapshots']
                        self.assertEqual([(image['role'], image['frame']) for image in snapshots],
                                         expected + [('last-end-card', ending)])
                        for image in snapshots:
                            png = Path(image['png'])
                            self.assertEqual(png.name, f'{movie["kind"]}-{image["role"]}.png')
                            command = next(command for command in report['commands'] if command['argv'][-1] == str(png))
                            self.assertEqual(command['argv'][command['argv'].index('-vf') + 1],
                                             f'select=eq(n\\,{image["frame"]})')
                            artifacts.append(png)
                        artifacts.append(Path(movie['decoded_pcm']))
                    for command in report['commands']:
                        artifacts.extend(Path(command[field]) for field in ('stdout', 'stderr'))
                        if command['argv'][-1].endswith('.rgb'):
                            artifacts.append(Path(command['argv'][-1]))
                    self.assertEqual(len(artifacts), len(set(artifacts)))
                    for artifact in artifacts:
                        self.assertEqual(artifact.parent, Path(report['inspection_directory']))
                        self.assertTrue(artifact.is_file())
                else:
                    self.assertIn('error', report)
                    self.assertFalse(QUALITY.technical(report))


if __name__ == '__main__':
    unittest.main()
