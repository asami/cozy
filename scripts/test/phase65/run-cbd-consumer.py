#!/usr/bin/env -S uv run --script
# /// script
# requires-python = ">=3.12"
# dependencies = []
# ///
"""Observe two real CBD calls through generated Dart contracts in one owned run."""

import argparse
from contextlib import contextmanager
from datetime import datetime, timezone
import functools
from http.server import SimpleHTTPRequestHandler, ThreadingHTTPServer
import json
import os
from pathlib import Path
import re
import shutil
import signal
import socket
import subprocess
import sys
import tempfile
import threading
import time
import urllib.error
import urllib.request
import zipfile


TOOL = "org.simplemodeling.textus.CbdSupport.CbdRetrieval.status"
MAIN = "org.goldenport.cncf.CncfMain"
PROTOCOL = "2025-11-25"
UNSET = ("TEXTUS_CBD_SIE_BOK_ROUTES", "TEXTUS_CBD_SIE_ALLOWED_ORIGINS",
         "TEXTUS_CBD_BOK_SITES", "TEXTUS_CBD_BOK_ALLOWED_ORIGINS")
MAX_BYTES = 16 * 1024 * 1024
ACQUIRING_RESOURCE = False
PENDING_SIGNAL = None


def require(condition, message):
    if not condition:
        raise ValueError(message)


def now():
    return datetime.now(timezone.utc).isoformat()


def write_json(path, value):
    path.write_text(json.dumps(value, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


def read_json(path):
    return json.loads(path.read_text(encoding="utf-8"))


def safe_path(raw):
    path = Path(os.path.abspath(raw))
    for parent in (path, *path.parents):
        require(not parent.is_symlink(), f"Symlink path is not admitted: {parent}")
    return path.resolve()


def regular(path):
    require(path.is_file() and not path.is_symlink() and path.stat().st_size > 0,
            f"Nonempty regular file required: {path}")


def prepare_consumer(cozy, workspace):
    """Refresh only the two current authored consumer files in owned scratch."""
    cozy, workspace = safe_path(cozy), safe_path(workspace)
    require(workspace == cozy / 'target/phase65/s654', 'Exact task-owned consumer workspace required')
    authored = cozy / 'examples/dart-cbd-consumer/consumer'
    consumer = safe_path(workspace / 'consumer')
    binary = safe_path(consumer / 'bin')
    files = []
    for name in ('pubspec.yaml', 'bin/contract.dart'):
        source = safe_path(authored / name)
        destination = safe_path(consumer / name)
        regular(source)
        if destination.exists():
            regular(destination)
        files.append((source, destination))
    for directory in (consumer, binary):
        require(not directory.exists() or directory.is_dir(), f'Consumer directory required: {directory}')
        directory.mkdir(exist_ok=True)
    for source, destination in files:
        shutil.copyfile(source, destination)
    return {'files': [{'source': str(source), 'destination': str(destination)}
                      for source, destination in files]}


def prepare_fixture_catalog(source, run):
    """Copy catalog inputs privately and supply an absent zero-profile SAR index."""
    source, run = safe_path(source), safe_path(run)
    source_fixtures = safe_path(source / 'examples/cbd-sie-sar/fixtures')
    for path in source_fixtures.rglob('*'):
        require(not path.is_symlink(), f'Symlink fixture prohibited: {path}')
    for name in ('catalog/metadata/repository/car/index.json',
                 'catalog/repository/catalog/car/textus-runtime.model-metadata.json'):
        regular(source_fixtures / name)
    served_fixtures = safe_path(run / 'fixture-input')
    require(not served_fixtures.exists(),
            f'Fixture catalog destination already exists: {served_fixtures}')
    sar_relative = 'catalog/metadata/repository/sar/index.json'
    source_sar = source_fixtures / sar_relative
    if source_sar.exists():
        regular(source_sar)
    shutil.copytree(source_fixtures, served_fixtures)
    sar_index = served_fixtures / sar_relative
    generated = not sar_index.exists()
    if generated:
        sar_index.parent.mkdir(parents=True, exist_ok=True)
        write_json(sar_index, {'entries': [], 'diagnostics': []})
    else:
        regular(sar_index)
    return {'sourceFixtures': str(source_fixtures), 'servedFixtures': str(served_fixtures),
            'sarIndex': str(sar_index), 'sarIndexGenerated': generated}


def owned_group_exists(group, observations):
    """Observe the exact native owned group using the selected macOS ps contract."""
    require(type(group) is int and group > 0,
            'Owned process group observation failed: positive integer group required')
    argv = ['/bin/ps', '-g', str(group), '-o', 'pid=,pgid=']
    observation = {'argv': argv, 'startedAt': now()}
    env = os.environ.copy()
    env['LC_ALL'] = 'C'
    try:
        result = subprocess.run(argv, capture_output=True, text=True, timeout=2, env=env)
        observation.update(exitCode=result.returncode, stdout=result.stdout, stderr=result.stderr)
    except subprocess.TimeoutExpired as error:
        observation.update(timedOut=True, error=str(error),
                           stdout=error.stdout.decode('utf-8', errors='replace') if isinstance(error.stdout, bytes) else error.stdout,
                           stderr=error.stderr.decode('utf-8', errors='replace') if isinstance(error.stderr, bytes) else error.stderr)
        raise ValueError(f'Owned process group observation failed: timeout: {error}') from error
    except OSError as error:
        observation['error'] = str(error)
        raise ValueError(f'Owned process group observation failed: {type(error).__name__}: {error}') from error
    finally:
        observation['finishedAt'] = now()
        observations.append(observation)
    if result.returncode == 1 and result.stdout == '' and result.stderr == '':
        return False
    require(result.returncode == 0 and result.stderr == '' and bool(result.stdout.strip()),
            'Owned process group observation failed: unexpected exit, stderr or empty rows')
    for row in result.stdout.splitlines():
        fields = row.split()
        require(len(fields) == 2 and all(re.fullmatch(r'[0-9]+', field) and int(field) > 0 for field in fields),
                'Owned process group observation failed: malformed row')
        require(int(fields[1]) == group, 'Owned process group observation failed: mismatched group')
    return True


def cbd_deployment_descriptor(car):
    """Admit the actual CAR identity before any runtime resource acquisition."""
    car = safe_path(car)
    regular(car)
    try:
        with zipfile.ZipFile(car) as archive:
            source = json.loads(archive.read('component-descriptor.json').decode('utf-8'))
    except (zipfile.BadZipFile, KeyError, UnicodeDecodeError, json.JSONDecodeError,
            RuntimeError, NotImplementedError) as error:
        raise ValueError(f'CBD deployment admission failed: component-descriptor.json in {car}: {error}') from error
    require(isinstance(source, dict), 'CBD deployment admission failed: descriptor object required')
    require(type(source.get('schemaVersion')) is int and source['schemaVersion'] == 3,
            'CBD deployment admission failed: schemaVersion must be integer 3')
    identity = source.get('component')
    require(isinstance(identity, dict), 'CBD deployment admission failed: component object required')
    expected = {'namespace': 'org.simplemodeling.textus', 'id': 'CbdSupport',
                'version': '0.1.0-SNAPSHOT'}
    for field, value in expected.items():
        require(isinstance(identity.get(field), str) and identity[field] == value,
                f'CBD deployment admission failed: component.{field} must be {value!r}')
    return {
        'subsystem': 'textus-cbd-standalone', 'version': '0.1.0-SNAPSHOT',
        'components': [{field: identity[field] for field in expected}],
        'security': {'authentication': {
            'convention': 'disabled', 'fallback_privilege': 'disabled',
            'local_subject': {
                'id': 'cbd-standalone-review', 'roles': ['reviewer'], 'security_level': 'user',
                'attributes': {'installation': 'cbd-standalone'},
            },
        }},
    }


def write_deployment_sar(car, original_descriptor, run):
    """Derive a private canonical SAR; the copied descriptor is provenance only."""
    car = safe_path(car)
    descriptor = cbd_deployment_descriptor(car)
    original_descriptor = safe_path(original_descriptor)
    regular(original_descriptor)
    with original_descriptor.open('rb') as provenance:
        provenance.read(1)
    run = safe_path(run)
    generated = safe_path(run / 'subsystem-descriptor.json')
    component = safe_path(run / 'component.d')
    component.mkdir(exist_ok=True)
    sar = safe_path(component / 'textus-cbd-standalone.sar')
    write_json(generated, descriptor)
    with zipfile.ZipFile(sar, 'x', zipfile.ZIP_DEFLATED) as archive:
        archive.write(generated, 'subsystem-descriptor.json')
    return {'sourceCar': str(car), 'sourceDescriptor': str(original_descriptor),
            'componentIdentity': descriptor['components'][0],
            'generatedDescriptor': str(generated), 'generatedArchive': str(sar),
            'userMode': 'standalone', 'authenticationPolicy': descriptor['security']['authentication']}


def bounded(raw):
    value = int(raw)
    if not 1 <= value <= 600:
        raise argparse.ArgumentTypeError("Timeout must be an integer in 1..600 seconds")
    return value


@contextmanager
def acquiring_owned_resource():
    """Deliver termination signals after a new child/server has an owned handle."""
    global ACQUIRING_RESOURCE, PENDING_SIGNAL
    ACQUIRING_RESOURCE = True
    try:
        yield
    finally:
        ACQUIRING_RESOURCE = False
        if PENDING_SIGNAL is not None:
            pending = PENDING_SIGNAL
            PENDING_SIGNAL = None
            signal.raise_signal(pending)


class NoRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, req, fp, code, msg, headers, newurl):
        raise ValueError(f"Redirect prohibited for owned loopback endpoint: {newurl}")


class FixtureServer(ThreadingHTTPServer):
    daemon_threads = True


class FixtureHandler(SimpleHTTPRequestHandler):
    def log_message(self, fmt, *args):
        with self.server.log_lock:
            self.server.log_stream.write(f"{now()} {self.client_address[0]} {fmt % args}\n")
            self.server.log_stream.flush()


class LiveRun:
    """Own receipts, bounded child commands, live requests, and Java lifecycle."""

    def __init__(self, args, workspace, run):
        self.args = args
        self.workspace = workspace
        self.run = run
        self.java = None
        self.java_group = None
        self.java_log = None
        self.fixture = None
        self.fixture_thread = None
        self.fixture_log = None
        self.process_receipt = None
        self.command_count = 0
        self.http_count = 0
        self.opener = urllib.request.build_opener(urllib.request.ProxyHandler({}), NoRedirect())
        self.result = {"schema": "phase65.cbd.live.v1", "runDirectory": str(run),
                       "startedAt": now(), "processCompletion": "not_started",
                       "liveProof": False, "cleanup": {"success": False},
                       "commands": [], "scenarios": [], "observations": []}

    def command(self, label, argv, cwd):
        self.command_count += 1
        prefix = self.run / f"command-{self.command_count:02d}-{label}"
        receipt = {"label": label, "argv": argv, "cwd": str(cwd),
                   "startedAt": now(), "timeoutSeconds": self.args.command_timeout_seconds,
                   "stdout": str(prefix.with_suffix('.stdout')),
                   "stderr": str(prefix.with_suffix('.stderr')), "timedOut": False}
        child = None
        try:
            with prefix.with_suffix('.stdout').open('wb') as stdout, prefix.with_suffix('.stderr').open('wb') as stderr:
                try:
                    with acquiring_owned_resource():
                        child = subprocess.Popen(argv, cwd=cwd, stdout=stdout, stderr=stderr,
                                                 start_new_session=True)
                        receipt.update(pid=child.pid, processGroup=child.pid)
                    receipt['exitCode'] = child.wait(timeout=self.args.command_timeout_seconds)
                except subprocess.TimeoutExpired:
                    receipt['timedOut'] = True
                    raise ValueError(f"Command timed out: {label}")
                finally:
                    if child is not None and child.poll() is None:
                        receipt['termination'] = self.stop_group(child, child.pid)
                        receipt['exitCode'] = child.poll()
            require(receipt['exitCode'] == 0, f"Command failed: {label}")
            return prefix.with_suffix('.stdout').read_text(encoding='utf-8')
        finally:
            receipt['finishedAt'] = now()
            write_json(prefix.with_suffix('.json'), receipt)
            self.result['commands'].append(str(prefix.with_suffix('.json')))

    def stop_group(self, child, group):
        """Only groups created with start_new_session in this invocation are owned."""
        receipt = {"pid": child.pid, "processGroup": group, "startedAt": now(),
                   "termSent": False, "killSent": False, "errors": [],
                   "groupObservations": [], "groupGone": False, "childExited": False}
        def failed(stage, error):
            receipt['errors'].append({'stage': stage, 'error': str(error)})
        def send(stage, signum, field):
            try:
                os.killpg(group, signum)
                receipt[field] = True
            except ProcessLookupError:
                receipt.setdefault('alreadyAbsentSignalTargets', []).append(stage)
            except (OSError, ValueError, subprocess.SubprocessError) as error:
                failed(stage, error)
        absent = False
        send('term-signal', signal.SIGTERM, 'termSent')
        deadline = time.monotonic() + 15
        while time.monotonic() < deadline:
            try:
                child.poll()
            except (OSError, ValueError, subprocess.SubprocessError) as error:
                failed('term-poll', error)
                break
            try:
                absent = not owned_group_exists(group, receipt['groupObservations'])
            except (OSError, ValueError, subprocess.SubprocessError) as error:
                failed('term-observation', error)
                break
            if absent:
                break
            time.sleep(0.1)
        if not absent:
            send('kill-signal', signal.SIGKILL, 'killSent')
        try:
            receipt['exitCode'] = child.wait(timeout=10)
            receipt['childExited'] = True
        except (OSError, ValueError, subprocess.SubprocessError) as error:
            failed('child-wait', error)
        deadline = time.monotonic() + 5
        while time.monotonic() < deadline:
            try:
                receipt['groupGone'] = not owned_group_exists(group, receipt['groupObservations'])
            except (OSError, ValueError, subprocess.SubprocessError) as error:
                failed('final-observation', error)
                break
            if receipt['groupGone']:
                break
            time.sleep(0.1)
        else:
            failed('final-absence', 'Owned process group remained present at deadline')
        receipt['success'] = receipt['childExited'] and receipt['groupGone'] and not receipt['errors']
        receipt['finishedAt'] = now()
        return receipt

    def http(self, label, url, envelope=None, timeout=10):
        self.http_count += 1
        prefix = self.run / f"http-{self.http_count:03d}-{label}"
        headers = {'Accept': 'application/json', 'Content-Type': 'application/json',
                   'MCP-Protocol-Version': PROTOCOL}
        data = None if envelope is None else json.dumps(envelope).encode('utf-8')
        if envelope is not None:
            write_json(prefix.with_suffix('.sent.json'), envelope)
        receipt = {'url': url, 'method': 'GET' if data is None else 'POST',
                   'requestHeaders': headers, 'startedAt': now(), 'timeoutSeconds': timeout}
        try:
            request = urllib.request.Request(url, data=data, headers=headers)
            try:
                response = self.opener.open(request, timeout=timeout)
            except urllib.error.HTTPError as error:
                response = error
            with response:
                raw = response.read(MAX_BYTES + 1)
                prefix.with_suffix('.response').write_bytes(raw)
                receipt.update(status=response.status, headers=dict(response.headers),
                               responseBytes=str(prefix.with_suffix('.response')))
                require(len(raw) <= MAX_BYTES, 'HTTP response exceeds bounded payload size')
                require(200 <= response.status < 300, f"HTTP failure {response.status}")
                require(response.headers.get_content_type() == 'application/json',
                        'Expected JSON HTTP response')
            decoded = json.loads(raw)
            require(isinstance(decoded, dict), 'HTTP JSON object required')
            if envelope is not None:
                require(decoded.get('jsonrpc') == '2.0' and decoded.get('id') == envelope['id'],
                        'JSON-RPC response identity mismatch')
                require('error' not in decoded, 'JSON-RPC error returned')
                require(isinstance(decoded.get('result'), dict), 'JSON-RPC result missing')
                require(decoded['result'].get('isError', False) is False, 'MCP tool error returned')
            return decoded
        except (OSError, ValueError) as error:
            receipt['error'] = str(error)
            raise
        finally:
            receipt['finishedAt'] = now()
            write_json(prefix.with_suffix('.json'), receipt)

    def fixture_ready(self, fixture_base):
        for kind in ('car', 'sar'):
            self.http(f'fixture-{kind}-readiness',
                      fixture_base + f'/catalog/metadata/repository/{kind}/index.json')

    def ready(self, base):
        deadline = time.monotonic() + self.args.startup_timeout_seconds
        while time.monotonic() < deadline:
            require(self.java.poll() is None, 'Owned Java child exited before readiness')
            bound_line = f"HTTP server started at 127.0.0.1:{self.process_receipt['baseUrl'].rsplit(':', 1)[1]}."
            if bound_line not in (self.run / 'server.log').read_text(encoding='utf-8', errors='replace'):
                time.sleep(min(0.25, max(0, deadline - time.monotonic())))
                continue
            remaining = deadline - time.monotonic()
            try:
                self.http('openapi-readiness', base + '/openapi.json', timeout=min(5, remaining))
                listed = self.http('tools-list', base + '/mcp',
                                   {'jsonrpc': '2.0', 'id': 'phase65-tools',
                                    'method': 'tools/list', 'params': {}},
                                   timeout=min(5, max(0.01, deadline - time.monotonic())))
            except (OSError, ValueError) as error:
                self.result['lastReadinessError'] = str(error)
                time.sleep(min(0.25, max(0, deadline - time.monotonic())))
                continue
            tools = listed['result'].get('tools')
            require(isinstance(tools, list) and
                    sum(isinstance(tool, dict) and tool.get('name') == TOOL for tool in tools) == 1,
                    'Exact status tool missing or ambiguous')
            require(self.java.poll() is None, 'Owned Java child exited at readiness')
            self.result['readiness'] = {'observedAt': now(), 'openapi': base + '/openapi.json',
                                        'statusTool': TOOL, 'ownedPid': self.java.pid,
                                        'ownedBindObservation': bound_line,
                                        'ownedBindSource': 'CNCF Http4sHttpServer after successful Ember bind; child-only server.log'}
            return
        raise ValueError('Owned CBD server startup deadline exceeded')

    def scenario(self, label, detail, base, dart, consumer):
        # Given real running CBD, the CNCF profile SDK and generated omission encoding.
        evidence = {'id': label, 'given': {'ownedPid': self.java.pid,
                    'generatedSdk': str(self.workspace / 'sdk-cncf-snake-omit'),
                    'jsonFieldStyle': 'cncf-snake-case',
                    'requestEncoding': 'toJsonOmittingNullFields', 'detailArgument': detail},
                    'when': {}, 'then': {}}
        self.result['scenarios'].append(evidence)
        detail_args = [] if detail is None else [detail]
        request = json.loads(self.command(label + '-request',
                            [str(dart), 'run', 'bin/contract.dart', 'request', *detail_args], consumer))
        require(isinstance(request, dict), 'Generated request must be an object')
        # When generated output is compared with the exact provided or absent argument.
        expected_request = {} if detail is None else {'detail': detail}
        evidence['when']['generatedRequest'] = request
        # Then present-null and additional keys fail before any HTTP call.
        require(request == expected_request,
                'Generated request-shape admission failed: exact provided detail or absent key required')
        envelope = {'jsonrpc': '2.0', 'id': label, 'method': 'tools/call',
                    'params': {'name': TOOL, 'arguments': request}}
        # When these exact generated arguments travel through the real MCP route.
        live = self.http(label, base + '/mcp', envelope,
                         timeout=self.args.command_timeout_seconds)
        content = live['result'].get('content')
        require(isinstance(content, list) and len(content) == 1 and
                isinstance(content[0], dict) and content[0].get('type') == 'text' and
                isinstance(content[0].get('text'), str), 'Single successful JSON text payload required')
        payload = json.loads(content[0]['text'])
        require(isinstance(payload, dict), 'Live CBD payload must be an object')
        payload_file = self.run / (label + '-live-payload.json')
        write_json(payload_file, payload)
        evidence['when']['livePayload'] = str(payload_file)
        report = json.loads(self.command(label + '-response', [str(dart), 'run',
                            'bin/contract.dart', 'response', str(payload_file), *detail_args], consumer))
        # Then typed generated API/codecs must preserve the actual complete payload.
        require(isinstance(report, dict), 'Typed consumer report must be an object')
        require(report.get('encodedRequest') == request, 'Injected request differs from sent arguments')
        require(report.get('decodedResponse') == payload, 'Generated response roundtrip differs from live payload')
        require(isinstance(payload.get('overall'), str) and isinstance(payload.get('detail'), str),
                'Live named wrappers must have scalar string values')
        for field in ('overall', 'detail'):
            require(report.get(field) == payload.get(field), f'Typed field differs: {field}')
        for field, wire in (('sourceCount', 'source_count'), ('readySourceCount', 'ready_source_count'),
                            ('componentCount', 'component_count')):
            require(report.get(field) == payload.get(wire), f'Typed field differs: {field} / {wire}')
            require(type(payload.get(wire)) is int and type(report.get(field)) is int and payload[wire] >= 0,
                    f'Typed nonnegative integer required: {field} / {wire}')
        require(0 < payload['ready_source_count'] <= payload['source_count'] and payload['component_count'] > 0,
                'Actual fixture-fed source/component readiness required')
        if detail is not None:
            require(payload['detail'] == detail, 'Explicit detail marker was not echoed')
        else:
            require(bool(payload['detail']) and 'fixture-catalog=ready' in payload['detail'],
                    'Omitted detail must expose the actual ready fixture source summary')
        evidence['then'] = {'requestMatchesSent': True, 'responseMatchesLive': True,
                            'typedReport': report, 'fixtureFedReadiness': True,
                            'detailObservation': 'echo' if detail is not None else 'runtime source summary'}
        write_json(self.run / (label + '-observation.json'), evidence)

    def execute(self, preparation, source, dart, java):
        sdk = self.workspace / 'sdk-cncf-snake-omit'
        consumer = self.workspace / 'consumer'
        cozy = self.args.cozy_root
        for path in (sdk / 'pubspec.yaml', sdk / 'lib/phase65_cbd_sdk.dart',
                     sdk / 'lib/src/models.dart', sdk / 'lib/src/api.dart',
                     sdk / 'lib/src/json.dart', sdk / 'README.md',
                     cozy / 'examples/dart-cbd-consumer/consumer/pubspec.yaml',
                     cozy / 'examples/dart-cbd-consumer/consumer/bin/contract.dart',
                     source / 'target/textus-cbd-support-0.1.0-SNAPSHOT.car',
                     source / 'examples/cbd-standalone-sar/subsystem-descriptor.yaml'):
            regular(safe_path(path))
        car = source / 'target/textus-cbd-support-0.1.0-SNAPSHOT.car'
        descriptor = source / 'examples/cbd-standalone-sar/subsystem-descriptor.yaml'
        derivation = write_deployment_sar(car, descriptor, self.run)
        self.result['deploymentDerivation'] = derivation
        self.result['subsystemUserMode'] = 'standalone'
        consumer_preparation = prepare_consumer(cozy, self.workspace)
        self.result['consumerPreparation'] = consumer_preparation
        classpath_file = self.workspace / 'cncf-runtime-classpath.txt'
        regular(classpath_file)
        entries = classpath_file.read_text(encoding='utf-8').strip().split(os.pathsep)
        require(entries and all(entries), 'Nonempty exported dependency classpath required')
        jars = [safe_path(entry) for entry in entries]
        for jar in jars:
            regular(jar)
            require(jar.suffix == '.jar' and not jar.is_relative_to(source),
                    'Only resolved dependency JARs outside copied CBD root admitted')
        cncf = [jar for jar in jars if jar.name.startswith('goldenport-cncf_3')]
        require(len(cncf) == 1 and '0.5.3-SNAPSHOT' in str(cncf[0]),
                'Exactly declared CNCF 0.5.3-SNAPSHOT artifact required')
        classpath = os.pathsep.join(map(str, jars))
        version = self.command('cncf-version', [str(java), '-cp', classpath, MAIN, '--version'], self.run)
        require(re.search(r'(?<![\w.])0\.5\.3-SNAPSHOT(?![\w.-])', version),
                'Actual CNCF version command did not report 0.5.3-SNAPSHOT')
        fixture_preparation = prepare_fixture_catalog(source, self.run)
        self.result['fixturePreparation'] = fixture_preparation
        fixture_root = Path(fixture_preparation['servedFixtures'])
        component = self.run / 'component.d'
        for name in ('local/repository/car', 'cache/car', 'development',
                     'dependency-cache', 'runtime-home'):
            (self.run / name).mkdir(parents=True)
        shutil.copyfile(car, component / car.name)
        self.fixture_log = (self.run / 'fixture.log').open('w', encoding='utf-8')
        with acquiring_owned_resource():
            self.fixture = FixtureServer(('127.0.0.1', 0), functools.partial(FixtureHandler, directory=str(fixture_root)))
            self.fixture.log_stream = self.fixture_log
            self.fixture.log_lock = threading.Lock()
            self.fixture_thread = threading.Thread(target=self.fixture.serve_forever, daemon=True)
            self.fixture_thread.start()
        fixture_base = f'http://127.0.0.1:{self.fixture.server_port}'
        self.fixture_ready(fixture_base)
        # Reserve a free loopback port; release immediately before starting our child.
        with socket.socket(socket.AF_INET, socket.SOCK_STREAM) as reservation:
            reservation.bind(('127.0.0.1', 0))
            port = reservation.getsockname()[1]
            base = f'http://127.0.0.1:{port}'
            config = {
                'textus.resource.tree.file-roots': f'empty={self.run}/development,local-car={self.run}/local,cache-car={self.run}/cache',
                'textus.cbd.catalog.allowed-origins': fixture_base,
                'textus.cbd.catalogs': f'fixture-catalog={fixture_base}/catalog/',
                'textus.cbd.development.trees': 'empty=empty',
                'textus.cbd.local-car.tree': 'local-car', 'textus.cbd.cache-car.tree': 'cache-car',
                'textus.subsystem': 'textus-cbd-standalone',
                'textus.subsystem.user-mode': 'standalone',
                'textus.server.port-assignment-file': str(self.run / 'server-port-assignments.json'),
                'textus.component.dependencies.cache.dir': str(self.run / 'dependency-cache'),
                'textus.test.home.mode': 'isolated', 'textus.test.home.path': str(self.run / 'runtime-home'),
                'textus.test.home.inherit.runtime': 'true',
                'textus.test.home.inherit.repositories': 'false',
                'textus.test.home.inherit.credentials': 'false',
                'textus.test.home.inherit.local-data': 'false',
            }
            argv = [str(java), '-Dtextus.server.host=127.0.0.1', f'-Dtextus.server.port={port}',
                    f'-Dcncf.http.baseurl={base}', '-cp', classpath, MAIN,
                    *[f'--{key}={value}' for key, value in config.items()],
                    'server', '--no-project-classpath', '--component-dir', str(component)]
            env = os.environ.copy()
            for key in UNSET:
                env.pop(key, None)
            env.update(CNCF_SERVER_PORT=str(port), CNCF_HTTP_BASEURL=base)
            self.process_receipt = {'argv': argv, 'cwd': str(self.run), 'startedAt': now(),
                                    'environmentUnset': list(UNSET),
                                    'environmentOverrides': {'CNCF_SERVER_PORT': str(port), 'CNCF_HTTP_BASEURL': base},
                                    'baseUrl': base, 'fixtureBaseUrl': fixture_base,
                                    'sourceCopy': str(source), 'car': str(car),
                                    'descriptor': str(descriptor), 'classpath': list(map(str, jars)),
                                    'deploymentDerivation': derivation,
                                    'fixturePreparation': fixture_preparation,
                                    'subsystemUserMode': config['textus.subsystem.user-mode']}
            write_json(self.run / 'port-selection.json', {'serverHost': '127.0.0.1',
                       'serverPort': port, 'fixturePort': self.fixture.server_port,
                       'selection': 'Fresh loopback socket bind; child bound observation required before HTTP',
                       'assignmentFile': config['textus.server.port-assignment-file']})
        # A bind race must fail; readiness always polls this child before HTTP.
        self.java_log = (self.run / 'server.log').open('wb')
        with acquiring_owned_resource():
            self.java = subprocess.Popen(argv, cwd=self.run, env=env, stdout=self.java_log,
                                         stderr=subprocess.STDOUT, start_new_session=True)
            self.java_group = self.java.pid
        self.process_receipt.update(pid=self.java.pid, processGroup=self.java_group,
                                    observedRunning=self.java.poll() is None)
        write_json(self.run / 'server-process.json', self.process_receipt)
        self.result['processCompletion'] = 'running_owned_child'
        # Given actual selected source provenance, built CAR/SAR and running runtime.
        self.result['observations'].append({'given': {'preparation': str(preparation),
                                             'sourceHead': read_json(preparation)['sourceHead'],
                                             'runtimeVersion': version.strip(), 'processReceipt': 'server-process.json',
                                             'deploymentDerivation': derivation,
                                             'consumerPreparation': consumer_preparation,
                                             'fixturePreparation': fixture_preparation,
                                             'generatedSdk': str(sdk), 'jsonFieldStyle': 'cncf-snake-case',
                                             'requestEncoding': 'toJsonOmittingNullFields',
                                             'subsystemUserMode': config['textus.subsystem.user-mode']}})
        self.ready(base)
        for label, cwd in (('sdk', sdk), ('consumer', consumer)):
            self.command(label + '-pub-get', [str(dart), 'pub', 'get', '--offline'], cwd)
            self.command(label + '-analyze', [str(dart), 'analyze', '--fatal-infos', '--fatal-warnings'], cwd)
        marker = 'phase65-' + self.run.name
        self.scenario('explicit-detail', marker, base, dart, consumer)
        self.scenario('omitted-detail', None, base, dart, consumer)
        require(self.java.poll() is None, 'Owned runtime exited during live proof')
        self.result['liveProof'] = True

    def cleanup(self):
        cleanup = {'startedAt': now(), 'success': True}
        if self.java is not None:
            cleanup['java'] = self.stop_group(self.java, self.java_group)
            cleanup['success'] = cleanup['java']['success']
            self.result['processCompletion'] = 'terminal' if cleanup['java']['childExited'] else 'termination_failed'
        else:
            self.result['processCompletion'] = 'not_started'
        if self.fixture is not None:
            try:
                if self.fixture_thread is not None and self.fixture_thread.is_alive():
                    self.fixture.shutdown()
                self.fixture.server_close()
                if self.fixture_thread is not None and self.fixture_thread.ident is not None:
                    self.fixture_thread.join(timeout=10)
                cleanup['fixtureThreadJoined'] = self.fixture_thread is None or not self.fixture_thread.is_alive()
                cleanup['success'] = cleanup['success'] and cleanup['fixtureThreadJoined']
            except OSError as error:
                cleanup['fixtureError'] = str(error)
                cleanup['success'] = False
        for stream in (self.java_log, self.fixture_log):
            if stream is not None:
                stream.close()
        cleanup['finishedAt'] = now()
        self.result['cleanup'] = cleanup


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    for flag in ('cozy-root', 'workspace', 'cbd-source-copy', 'dart-sdk', 'java'):
        parser.add_argument('--' + flag, required=True)
    parser.add_argument('--startup-timeout-seconds', type=bounded, default=120)
    parser.add_argument('--command-timeout-seconds', type=bounded, default=180)
    args = parser.parse_args()
    live = None
    previous = {}
    try:
        cozy, workspace, source, dart, java = map(safe_path,
            (args.cozy_root, args.workspace, args.cbd_source_copy, args.dart_sdk, args.java))
        args.cozy_root = cozy
        require(workspace == cozy / 'target/phase65/s654' and
                source == cozy / 'target/phase65/cbd-runtime-source', 'Exact task-owned paths required')
        preparation = workspace / 'preparation.json'
        regular(preparation)
        prepared = read_json(preparation)
        require(prepared.get('cozyRoot') == str(cozy) and prepared.get('workspace') == str(workspace)
                and prepared.get('sourceCopy') == str(source), 'Preparation locators mismatch')
        for executable in (dart, java):
            regular(executable)
            require(os.access(executable, os.X_OK), f'Executable required: {executable}')
        runs = safe_path(workspace / 'runs')
        runs.mkdir(exist_ok=True)
        run = Path(tempfile.mkdtemp(prefix='live-', dir=runs))
        live = LiveRun(args, workspace, run)
        write_json(workspace / 'run-result.json', {'runDirectory': str(run), 'status': 'running',
                                                 'result': str(run / 'result.json'), 'startedAt': now()})
        def interrupted(signum, frame):
            global PENDING_SIGNAL
            if ACQUIRING_RESOURCE:
                PENDING_SIGNAL = signum
                return
            raise InterruptedError(f'Received signal {signum}')
        try:
            previous_mask = signal.pthread_sigmask(signal.SIG_BLOCK, {signal.SIGINT, signal.SIGTERM})
            try:
                for signum in (signal.SIGINT, signal.SIGTERM):
                    previous[signum] = signal.signal(signum, interrupted)
            finally:
                signal.pthread_sigmask(signal.SIG_SETMASK, previous_mask)
            live.execute(preparation, source, dart, java)
        except (OSError, ValueError, subprocess.SubprocessError) as error:
            live.result['error'] = str(error)
        finally:
            # Repeated signals must not interrupt the same mandatory cleanup path.
            for signum in previous:
                signal.signal(signum, signal.SIG_IGN)
            live.cleanup()
            live.result['finishedAt'] = now()
            live.result['success'] = (live.result['liveProof'] and
                                      live.result['cleanup']['success'] and 'error' not in live.result)
            write_json(run / 'result.json', live.result)
            pointer = {'runDirectory': str(run), 'result': str(run / 'result.json'),
                       'status': 'completed', 'success': live.result['success'],
                       'processCompletion': live.result['processCompletion'],
                       'liveProof': live.result['liveProof'], 'cleanup': live.result['cleanup']['success']}
            write_json(workspace / 'run-result.json', pointer)
            print(json.dumps(pointer))
        return 0 if live.result['success'] else 1
    except (OSError, ValueError) as error:
        print(f'CBD live acceptance failed: {error}', file=sys.stderr)
        return 1
    finally:
        for signum, handler in previous.items():
            signal.signal(signum, handler)


if __name__ == '__main__':
    raise SystemExit(main())
