#!/usr/bin/env -S uv run --managed-python --no-python-downloads --script
# /// script
# requires-python = ">=3.12"
# dependencies = []
# ///
"""Append real host FFmpeg observations; preserve argv, cwd, env and termination.

@since/@version Oct. 12, 2026; @author ASAMI, Tomoharu
"""

import json
import os
import signal
import subprocess
import sys
import time
import uuid
from pathlib import Path

FFMPEG = '/opt/homebrew/bin/ffmpeg'
RUNS = ('cold', 'warm01', 'warm02', 'warm03')
CASES = ('mixed-standard', 'mixed-lightweight', 'mixed-override')


def require(condition, message):
    if not condition:
        raise ValueError(message)


def classify(args, project, run):
    checks = ('-version', '--version', '-h', '-help', '-encoders', '-formats', '-codecs')
    if any(item in args for item in checks):
        return 'tool-check'
    if args:
        output = Path(args[-1])
        output = output if output.is_absolute() else Path.cwd() / output
        expected = project / f'build/quality/{run}/dialogue.mp4'
        codecs = [args[index + 1] for index, item in enumerate(args[:-1]) if item in ('-c:v', '-vcodec')]
        if output.resolve() == expected.resolve() and codecs:
            return 'dialogue-encode'
    return 'assembly-subcall'


def main():
    project = Path.cwd().resolve()
    run = json.loads((project / 'quality-tools/run.json').read_text(encoding='utf-8'))
    require(run.get('schema') == 'cozy.quality-run.v1' and run.get('case') in CASES
            and run.get('run') in RUNS and run.get('projectRoot') == str(project)
            and Path(__file__).resolve().parent == project / 'quality-tools'
            and str(project).endswith(f'/target/phase718/quality-media03/work/{run["case"]}'),
            'Installed host FFmpeg observer requires matching literal run/project metadata')
    args = sys.argv[1:]
    original = [str(Path(__file__).resolve()), *args]
    real = [FFMPEG, *args]
    invocation = uuid.uuid4().hex
    observation = project / 'quality-observations' / run['run'] / 'ffmpeg-events.jsonl'
    kind = classify(args, project, run['run'])

    def emit(event, **values):
        row = {'schema': 'cozy.quality-ffmpeg-event.v1', 'case': run['case'], 'run': run['run'],
               'part': 'dialogue' if kind == 'dialogue-encode' else 'assembly', 'cwd': str(project),
               'invocation': invocation, 'pid': os.getpid(), 'stage': kind, 'event': event,
               'monotonic_ns': str(time.monotonic_ns()), 'wall_ms': time.time_ns() // 1_000_000,
               'original_argv': original, 'real_argv': real, **values}
        with observation.open('a', encoding='utf-8') as output:
            output.write(json.dumps(row, allow_nan=False) + '\n')

    start = time.monotonic_ns()
    emit('begin', start_ns=str(start))
    try:
        # Default cwd/env and inherited streams are deliberate. There is no retry.
        completed = subprocess.run(real, shell=False)
    except OSError as error:
        emit('end', duration_ns=str(time.monotonic_ns() - start), success=False,
             exit_code=None, signal=None, error={'name': type(error).__name__, 'message': str(error)})
        print(f'FFmpeg startup failed: {error}', file=sys.stderr)
        return 127
    except KeyboardInterrupt:
        emit('end', duration_ns=str(time.monotonic_ns() - start), success=False,
             exit_code=None, signal='SIGINT', error={'name': 'KeyboardInterrupt',
             'message': 'Observer interrupted; subprocess.run performed its interrupt cleanup'})
        signal.signal(signal.SIGINT, signal.SIG_DFL)
        os.kill(os.getpid(), signal.SIGINT)
        return 130
    code = completed.returncode
    termination = signal.Signals(-code).name if code < 0 else None
    end = time.monotonic_ns()
    emit('end', end_ns=str(end), duration_ns=str(end - start), success=code == 0,
         exit_code=code if code >= 0 else None, signal=termination, error=None)
    if code < 0:
        if -code not in (signal.SIGKILL, signal.SIGSTOP):
            signal.signal(-code, signal.SIG_DFL)
        os.kill(os.getpid(), -code)
        return 128 - code
    return code


if __name__ == '__main__':
    try:
        raise SystemExit(main())
    except (OSError, ValueError, TypeError, KeyError) as error:
        print(f'FFmpeg observation failed: {error}', file=sys.stderr)
        raise SystemExit(1)
